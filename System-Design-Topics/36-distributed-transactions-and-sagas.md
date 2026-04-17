# 🎯 Topic 36: Distributed Transactions & Sagas

> **System Design Interview — Deep Dive**
> A comprehensive guide covering why 2PC fails in microservices, the Saga pattern (orchestration vs choreography), compensating transactions, the transactional outbox pattern, idempotency, and how to articulate distributed transaction strategies with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [The Problem: No Shared Database](#the-problem-no-shared-database)
3. [Two-Phase Commit (2PC) — Why It Fails at Scale](#two-phase-commit-2pc--why-it-fails-at-scale)
4. [The Saga Pattern — The Practical Solution](#the-saga-pattern--the-practical-solution)
5. [Orchestration vs Choreography](#orchestration-vs-choreography)
6. [Compensating Transactions (Undo Logic)](#compensating-transactions-undo-logic)
7. [The Transactional Outbox Pattern](#the-transactional-outbox-pattern)
8. [Idempotency — Making Retries Safe](#idempotency--making-retries-safe)
9. [Saga Failure Modes and Recovery](#saga-failure-modes-and-recovery)
10. [Real-World Saga Examples](#real-world-saga-examples)
11. [Deep Dive: Applying Sagas to Popular Problems](#deep-dive-applying-sagas-to-popular-problems)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

In a monolith with a single database, you get ACID transactions for free:

```sql
BEGIN;
  INSERT INTO reservations (seat_id, user_id) VALUES ('A1', 'alice');
  UPDATE seats SET status = 'HELD' WHERE seat_id = 'A1';
  INSERT INTO payments (user_id, amount) VALUES ('alice', 99.00);
COMMIT;
-- All three operations succeed or all fail. Atomicity guaranteed.
```

In microservices, each service has its own database. There's **no shared transaction boundary**:

```
Booking Service (PostgreSQL)  →  Reserve seat
Payment Service (PostgreSQL)  →  Charge card
Notification Service (DynamoDB) →  Send confirmation

Each service commits independently.
If Payment fails AFTER Booking succeeds → inconsistent state.
The seat is reserved but never paid for.
```

**The fundamental challenge**: How do you maintain data consistency across multiple services, each with its own database, without a shared transaction?

---

## The Problem: No Shared Database

### Why Microservices Can't Share a Database

| Reason | Explanation |
|---|---|
| **Independent deployment** | Service A shouldn't need to coordinate schema changes with Service B |
| **Independent scaling** | The payment DB might need 10x more IOPS than the notification DB |
| **Technology freedom** | Booking uses PostgreSQL, notifications use DynamoDB, search uses Elasticsearch |
| **Failure isolation** | Payment DB crash shouldn't take down the booking service |
| **Team autonomy** | Team A owns their data model — no cross-team schema negotiations |

### The Inconsistency Problem

```
Happy path:
  1. Booking Service: Reserve seat A1       → ✅ committed
  2. Payment Service: Charge $99.00         → ✅ committed
  3. Notification Service: Send email       → ✅ committed
  All good!

Failure scenario:
  1. Booking Service: Reserve seat A1       → ✅ committed
  2. Payment Service: Charge $99.00         → ❌ FAILS (card declined)
  3. Notification Service: Send email       → never reached
  
  Result: Seat A1 is reserved but not paid for.
  The booking is "orphaned" — inconsistent state across services.
  
  Without a compensation mechanism:
    - Seat A1 is blocked forever (no one else can book it)
    - User thinks booking failed (no confirmation)
    - Revenue lost (seat occupied but unpaid)
```

---

## Two-Phase Commit (2PC) — Why It Fails at Scale

### How 2PC Works

```
Coordinator (Transaction Manager):

Phase 1 — PREPARE:
  Coordinator → Booking Service:  "Can you reserve seat A1?"
  Coordinator → Payment Service:  "Can you charge $99?"
  Coordinator → Notification Service: "Can you send email?"
  
  Each service: Acquires locks, validates, responds YES or NO.
  
Phase 2 — COMMIT (if all YES):
  Coordinator → All services: "COMMIT"
  Each service: Commits the prepared transaction, releases locks.

Phase 2 — ABORT (if any NO):
  Coordinator → All services: "ABORT"
  Each service: Rolls back, releases locks.
```

### Why 2PC Fails in Distributed Systems

#### Problem 1: Coordinator Single Point of Failure

```
What if the coordinator crashes between Phase 1 and Phase 2?

Phase 1: All participants vote YES → locks held
Coordinator crashes → no COMMIT or ABORT message sent
All participants: Sitting with locks held, waiting for a message that never comes

Impact:
  - Database rows locked indefinitely
  - Other transactions blocked (can't book the same seat)
  - Manual intervention required to resolve

In a system with 99.99% uptime:
  0.01% downtime = ~52 minutes/year
  At 10,000 transactions/minute during peak:
  Each coordinator crash potentially freezes 10,000 in-flight transactions
```

#### Problem 2: Blocking Protocol

```
2PC is a BLOCKING protocol:
  Between Phase 1 (PREPARE) and Phase 2 (COMMIT):
    All participants hold locks on their resources.
    Duration: Network round-trip + coordinator processing = 10-200ms
    
  Under load: 10,000 concurrent 2PC transactions × 100ms lock duration
  = 10,000 rows locked at any instant
  = Massive lock contention = throughput collapse

Compare with Saga pattern:
  Each step commits immediately → no long-held locks
  Resources are available to other transactions between steps
```

#### Problem 3: Latency

```
2PC across services on different hosts:

Phase 1 (PREPARE):
  Network to Service A: 2ms
  Service A processing + lock: 5ms
  Network to Service B: 2ms  
  Service B processing + lock: 5ms
  Network to Service C: 2ms
  Service C processing + lock: 5ms
  
Phase 2 (COMMIT):
  Network to all 3 services: 2ms each
  Commit + release: 2ms each
  
Total: ~30-50ms minimum per transaction
Across regions: 200-400ms

Compare with local commit: 2-5ms
2PC adds 10-80x latency to every transaction
```

#### Problem 4: Not Supported Across Technologies

```
2PC requires XA protocol support.
  ✅ PostgreSQL (XA support)
  ✅ MySQL (XA support)
  ❌ DynamoDB (no XA)
  ❌ Redis (no XA)
  ❌ Elasticsearch (no XA)
  ❌ Cassandra (no XA)
  ❌ Kafka (no XA, different transaction model)

In a polyglot persistence architecture, 2PC is simply impossible.
```

### When 2PC IS Acceptable

```
✅ Within a single database (automatic — the DB does it for you)
✅ Between 2 databases in the same data center (low latency, rare failures)
✅ Financial systems requiring absolute atomicity (accept the latency cost)

❌ Across microservices (different networks, different technologies)
❌ Cross-region transactions (latency makes it impractical)
❌ High-throughput systems (lock contention kills throughput)
```

---

## The Saga Pattern — The Practical Solution

A **Saga** is a sequence of **local transactions**, where each step commits independently and has a **compensating action** (undo) that runs if a subsequent step fails.

### The Key Insight

Instead of one atomic transaction, break it into a sequence of steps:

```
Saga: Book Concert Ticket

Forward Steps:                    Compensating Actions (Undo):
─────────────                     ────────────────────────────
1. Reserve seat      (T1)    ←→   Release seat         (C1)
2. Charge payment    (T2)    ←→   Refund payment       (C2)
3. Confirm booking   (T3)    ←→   Cancel booking       (C3)
4. Send notification (T4)    ←→   (no compensation needed)

Happy path: T1 → T2 → T3 → T4 → Done ✅

Failure at T2 (payment fails):
  T1 ✅ → T2 ❌ → Execute compensations in reverse:
  C1 (release seat) → Done
  
  Net result: System back to original state.
  
Failure at T3 (confirm fails):
  T1 ✅ → T2 ✅ → T3 ❌ → Execute compensations in reverse:
  C2 (refund payment) → C1 (release seat) → Done
```

### Saga Properties

| Property | ACID Transaction | Saga |
|---|---|---|
| **Atomicity** | All or nothing (database guarantees) | "All or compensate" (application guarantees) |
| **Consistency** | Immediate (after COMMIT) | Eventually consistent (during saga execution) |
| **Isolation** | Full (other transactions can't see intermediate state) | None (other operations can see intermediate state) |
| **Durability** | Database guarantees | Each local transaction is durable |

### The Isolation Problem

```
Saga steps are NOT isolated from each other:

T1: Reserve seat A1 (status = "HELD")
                    ← Another user can see seat A1 as "HELD" here
T2: Charge payment
                    ← If T2 fails, seat A1 is briefly "HELD" but shouldn't be
C1: Release seat A1 (status = "AVAILABLE")

During the window between T1 and C1:
  Another user might see seat A1 as "HELD" and choose a different seat.
  This is a "dirty read" — they're seeing an intermediate saga state.

Mitigations:
  1. Semantic locks: Mark seat as "PENDING" (not "BOOKED" or "AVAILABLE")
     Other users see "PENDING" and know it might change
  2. Short saga duration: Keep the window small (< 5 seconds)
  3. Compensatable statuses: "HELD" → "CANCELLED" vs "CONFIRMED"
```

---

## Orchestration vs Choreography

### Orchestration: Central Coordinator

```
┌─────────────────────────────┐
│    Booking Orchestrator      │
│    (saga state machine)      │
└──────┬──────┬──────┬────────┘
       │      │      │
       ▼      ▼      ▼
  ┌────────┐ ┌────────┐ ┌────────────┐
  │Booking │ │Payment │ │Notification│
  │Service │ │Service │ │Service     │
  └────────┘ └────────┘ └────────────┘

Flow:
  1. Orchestrator → Booking: "Reserve seat A1"
  2. Booking → Orchestrator: "Reserved ✅"
  3. Orchestrator → Payment: "Charge $99"
  4. Payment → Orchestrator: "Charged ✅"
  5. Orchestrator → Notification: "Send confirmation"
  6. Notification → Orchestrator: "Sent ✅"
  7. Orchestrator: Saga complete ✅

On failure at step 4 (payment fails):
  4. Payment → Orchestrator: "Failed ❌"
  5. Orchestrator → Booking: "Release seat A1" (compensate)
  6. Booking → Orchestrator: "Released ✅"
  7. Orchestrator: Saga failed, compensated ✅
```

### Choreography: Event-Driven

```
No central coordinator. Each service listens for events and reacts.

  Booking Service                Payment Service              Notification Service
       │                              │                              │
  Reserve seat                        │                              │
       │                              │                              │
  Emit: SeatReserved ─────────────►  │                              │
       │                         Charge card                         │
       │                              │                              │
       │                    Emit: PaymentCharged ──────────────────► │
       │                              │                         Send email
       │                              │                              │
       │                              │               Emit: NotificationSent
       │                              │                              │

On failure (PaymentFailed event):
  Payment Service emits: PaymentFailed
  Booking Service listens for PaymentFailed → releases seat
```

### Orchestration vs Choreography Comparison

| Aspect | Orchestration | Choreography |
|---|---|---|
| **Control flow** | Explicit, centralized | Implicit, distributed |
| **Visibility** | One place to see the entire saga | Must correlate events across services |
| **Debugging** | Easy — orchestrator has full state log | Hard — trace events across 5+ services |
| **Coupling** | Orchestrator knows all services | Services don't know each other |
| **Single point of failure** | Orchestrator (mitigate with HA) | None (fully distributed) |
| **Complexity** | Simple per-saga, complex orchestrator | Simple per-service, complex interactions |
| **Adding new steps** | Change orchestrator only | Change multiple services + events |
| **Compensation** | Orchestrator drives rollback | Each service must know its compensation |
| **Best for** | Complex multi-step flows (booking, checkout) | Simple event-driven reactions (notifications) |

### When to Choose Which

```
Use ORCHESTRATION when:
  ✅ Saga has > 3 steps
  ✅ Steps have complex ordering dependencies
  ✅ Compensation logic is complex
  ✅ You need a clear audit trail of the saga lifecycle
  ✅ Example: E-commerce checkout (reserve → pay → confirm → ship → notify)

Use CHOREOGRAPHY when:
  ✅ Saga has 2-3 simple steps
  ✅ Steps are loosely coupled
  ✅ Each service independently decides its reaction
  ✅ You want minimal coupling between services
  ✅ Example: User signup (create account → send welcome email → add to newsletter)
```

---

## Compensating Transactions (Undo Logic)

### Not Every Step Has a Perfect Undo

```
Step: Reserve seat       → Compensation: Release seat          (perfect undo ✅)
Step: Charge credit card → Compensation: Refund credit card    (perfect undo ✅)
Step: Send email         → Compensation: Send "sorry" email    (imperfect undo ⚠️)
Step: Ship physical item → Compensation: Request return        (imperfect undo ⚠️)
Step: Publish tweet      → Compensation: Delete tweet          (but followers already saw it ⚠️)
```

### Designing Compensating Transactions

**Rule 1: Every step must be idempotent**
```
Reserve seat (called twice with same reservation_id):
  First call: Creates reservation → ✅
  Second call: Sees existing reservation → returns existing ✅ (no duplicate)

Refund payment (called twice with same payment_id):
  First call: Issues refund → ✅
  Second call: Sees refund already issued → returns existing ✅ (no double refund)
```

**Rule 2: Compensations must be idempotent too**
```
Release seat (called twice):
  First call: Releases seat → ✅
  Second call: Seat already released → no-op ✅ (not an error)
```

**Rule 3: Use semantic states, not hard deletes**
```
❌ Bad: DELETE FROM reservations WHERE id = 123
   (No audit trail, can't distinguish "never existed" from "cancelled")

✅ Good: UPDATE reservations SET status = 'CANCELLED' WHERE id = 123
   (Audit trail preserved, clear semantic state)

Status state machine:
  PENDING → CONFIRMED → COMPLETED
  PENDING → CANCELLED (compensated)
  CONFIRMED → CANCELLED (compensated after confirmation)
```

**Rule 4: Order compensations in reverse**
```
Forward:    T1 → T2 → T3 → T4
Compensate: C3 → C2 → C1

Why reverse? Each step may depend on the previous step's state.
Reversing ensures dependencies are unwound correctly.
```

---

## The Transactional Outbox Pattern

### The Problem: Dual-Write Inconsistency

```
Booking Service needs to:
  1. Insert reservation into its database
  2. Publish "SeatReserved" event to Kafka

Naive approach:
  db.insert(reservation)     → ✅ committed
  kafka.publish(event)       → ❌ FAILS (Kafka down, network error)
  
  Result: Reservation exists in DB, but no event published.
  Payment Service never hears about the reservation.
  System is inconsistent.

Even worse:
  kafka.publish(event)       → ✅ published
  db.insert(reservation)     → ❌ FAILS (DB constraint violation)
  
  Result: Event published, but reservation doesn't exist.
  Payment Service charges for a non-existent booking.
```

### The Solution: Transactional Outbox

```
Step 1: Write both business data AND event to the SAME database transaction:

BEGIN;
  INSERT INTO reservations (id, seat_id, user_id, status)
    VALUES ('res_123', 'A1', 'alice', 'PENDING');
  INSERT INTO outbox_events (id, aggregate_id, event_type, payload)
    VALUES ('evt_456', 'res_123', 'SeatReserved', '{"seat":"A1","user":"alice"}');
COMMIT;
-- Both succeed or both fail. Atomic. Consistent. ✅

Step 2: A separate process (CDC or poller) reads the outbox table and publishes to Kafka:

Debezium (CDC): Reads PostgreSQL WAL → publishes outbox_events to Kafka
  or
Outbox Poller: SELECT * FROM outbox_events WHERE published = false LIMIT 100
               → Publish to Kafka → UPDATE outbox_events SET published = true
```

### Outbox Architecture

```
┌──────────────────────────────────────────────────┐
│                Booking Service                     │
│                                                   │
│  ┌──────────────────────────────────────────┐     │
│  │  PostgreSQL (single transaction)          │     │
│  │                                          │     │
│  │  reservations table:                     │     │
│  │    {id: res_123, seat: A1, status: PENDING}│    │
│  │                                          │     │
│  │  outbox_events table:                    │     │
│  │    {id: evt_456, type: SeatReserved,     │     │
│  │     payload: {...}, published: false}     │     │
│  └──────────────┬───────────────────────────┘     │
│                 │                                  │
│                 │ CDC (Debezium)                    │
│                 ▼                                  │
│           ┌──────────┐                             │
│           │  Kafka    │                             │
│           │  Topic    │                             │
│           └──────────┘                             │
└──────────────────────────────────────────────────┘

Guarantees:
  ✅ If reservation is committed → event is in outbox → will be published
  ✅ If reservation fails → event is also rolled back → never published
  ✅ At-least-once delivery (Debezium replays on failure)
  ✅ Consumer idempotency handles duplicates
```

---

## Idempotency — Making Retries Safe

### Why Idempotency Is Critical for Sagas

```
Network timeout during payment:
  Orchestrator → Payment Service: "Charge $99" → TIMEOUT
  
  Did the payment succeed? We don't know.
  
  Options:
    A) Don't retry → payment might have succeeded → user never charged → revenue lost
    B) Retry → payment might have succeeded the first time → user charged TWICE
  
  Solution: Make payment idempotent with an idempotency key.
  
  POST /payments
    Idempotency-Key: saga_123_step_2
    Body: {amount: 99.00, user_id: "alice"}
  
  First call:  Creates charge, stores idempotency_key → returns {id: "pay_789"}
  Retry call:  Sees idempotency_key exists → returns same {id: "pay_789"} (no new charge)
```

### Implementing Idempotency

```sql
-- Idempotency table
CREATE TABLE idempotency_keys (
    idempotency_key TEXT PRIMARY KEY,
    response_payload JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP DEFAULT NOW() + INTERVAL '24 hours'
);

-- Payment processing with idempotency
BEGIN;
  -- Check if we've already processed this request
  SELECT response_payload FROM idempotency_keys 
    WHERE idempotency_key = 'saga_123_step_2';
  
  -- If found: Return stored response (no new charge)
  -- If not found: Process payment and store result
  INSERT INTO payments (id, user_id, amount) VALUES ('pay_789', 'alice', 99.00);
  INSERT INTO idempotency_keys (idempotency_key, response_payload)
    VALUES ('saga_123_step_2', '{"payment_id": "pay_789", "status": "charged"}');
COMMIT;
```

### Idempotency Key Strategies

| Strategy | Key Format | Example |
|---|---|---|
| **Saga-step based** | `{saga_id}_{step_number}` | `saga_123_step_2` |
| **Client-generated UUID** | `{uuid}` | `550e8400-e29b-41d4-a716-446655440000` |
| **Business key** | `{entity}_{action}_{id}` | `booking_charge_res_123` |
| **Hash-based** | `hash(request_body)` | `sha256("alice_99.00_seat_A1")` |

---

## Saga Failure Modes and Recovery

### What If Compensation Fails?

```
Forward:    T1 ✅ → T2 ❌
Compensate: C1 ❌ → ???

The compensation itself failed! Now what?

Strategy 1: Retry with exponential backoff
  C1 attempt 1: ❌ (service temporarily down)
  C1 attempt 2 (after 1s): ❌
  C1 attempt 3 (after 4s): ❌
  C1 attempt 4 (after 16s): ✅ (service recovered)

Strategy 2: Dead letter queue
  After N retries → move to DLQ → alert operations team → manual resolution

Strategy 3: Scheduled reconciliation
  Nightly job scans for sagas in "COMPENSATING" state for > 1 hour
  → Force-completes compensation or escalates to human operator
```

### Saga State Machine

```
States:
  STARTED → STEP_1_PENDING → STEP_1_COMPLETED → STEP_2_PENDING → ...
  ... → ALL_COMPLETED → DONE
  
  On failure:
  STEP_N_FAILED → COMPENSATING → COMPENSATION_COMPLETED → FAILED
  
  On compensation failure:
  COMPENSATING → COMPENSATION_FAILED → REQUIRES_MANUAL_INTERVENTION

State persistence (in orchestrator's database):
  {
    "saga_id": "saga_123",
    "type": "BOOKING",
    "state": "STEP_2_FAILED",
    "steps": [
      {"step": 1, "service": "booking", "status": "COMPLETED", "result": {...}},
      {"step": 2, "service": "payment", "status": "FAILED", "error": "card_declined"}
    ],
    "compensations": [
      {"step": 1, "service": "booking", "status": "PENDING"}
    ],
    "created_at": "2025-03-15T14:30:00Z",
    "updated_at": "2025-03-15T14:30:05Z"
  }
```

### Timeout Handling

```
Each saga step has a timeout:

Step 1 (Reserve seat):     Timeout: 5 seconds
Step 2 (Charge payment):   Timeout: 30 seconds (payment gateways are slow)
Step 3 (Confirm booking):  Timeout: 5 seconds
Step 4 (Send notification): Timeout: 10 seconds

On timeout:
  1. Retry once (idempotent, safe to retry)
  2. If retry times out → treat as failure → compensate

Saga-level timeout:
  If entire saga hasn't completed in 5 minutes → force compensate all completed steps
  Prevents "zombie sagas" that hold resources indefinitely
```

---

## Real-World Saga Examples

### Amazon Checkout

```
Saga: Place Order

T1: Validate cart and create order       → C1: Cancel order
T2: Reserve inventory                    → C2: Release inventory
T3: Calculate and verify pricing         → C3: (no-op, pricing is read-only)
T4: Authorize payment (hold, not charge) → C4: Void authorization
T5: Confirm order                        → C5: Mark order as cancelled
T6: Trigger fulfillment                  → C6: Cancel fulfillment
T7: Send confirmation email              → C7: Send cancellation email

Orchestrator: Order Service drives the flow
Idempotency: Order ID used as idempotency key for each step
Timeout: 30 seconds per step, 5 minutes total
```

### Uber Trip Assignment

```
Saga: Assign Trip to Driver

T1: Match rider with nearest driver      → C1: (no-op, just matching)
T2: Lock driver (mark as unavailable)    → C2: Unlock driver
T3: Send trip offer to driver            → C3: Retract offer
T4: Wait for driver acceptance           → C4: (timeout → try next driver)
T5: Create trip record                   → C5: Cancel trip
T6: Charge rider's payment method (hold) → C6: Void hold

Special case: Driver doesn't accept within 15 seconds
  → C3 (retract offer) → C2 (unlock driver) → retry T1 with next driver
  → Max 3 retries → saga fails → rider gets "No drivers available"
```

### Stripe Payment Processing

```
Saga: Process Payment

T1: Validate payment method              → C1: (no-op)
T2: Create payment intent                → C2: Cancel payment intent
T3: Authorize with card network          → C3: Void authorization
T4: Capture funds                        → C4: Refund
T5: Update merchant balance              → C5: Reverse balance update
T6: Send receipt                         → C6: Send correction notice

Idempotency key: Client-provided (Stripe enforces uniqueness per account)
Retry policy: Exponential backoff, max 3 retries per step
Timeout: 60 seconds per step (card networks are slow)
```

---

## Deep Dive: Applying Sagas to Popular Problems

### Ticket Booking System

```
Orchestrated Saga:

Booking Orchestrator:
  Step 1: Booking Service → Reserve seats [A1, A2]
    Compensation: Release seats [A1, A2]
    
  Step 2: Pricing Service → Calculate total ($198.00)
    Compensation: (no-op — pricing is idempotent)
    
  Step 3: Payment Service → Charge $198.00
    Compensation: Refund $198.00
    Idempotency key: booking_123_payment
    
  Step 4: Booking Service → Confirm booking (PENDING → CONFIRMED)
    Compensation: Cancel booking (CONFIRMED → CANCELLED)
    
  Step 5: Email Service → Send confirmation
    Compensation: (best-effort — send cancellation if saga fails)

Handling concurrent booking:
  Two users try to book seat A1 simultaneously:
  
  User 1: Step 1 → Reserve A1 → ✅ (row locked: SELECT ... FOR UPDATE)
  User 2: Step 1 → Reserve A1 → ❌ (seat already held)
  User 2: Gets immediate failure → "Seat unavailable"
  
  User 1 continues saga → payment → confirm → done ✅
```

### E-Commerce Order Fulfillment

```
Orchestrated Saga (complex, 7 steps):

Order Orchestrator:
  T1: Inventory Service → Reserve items
  T2: Pricing Service → Calculate final price (apply coupons, tax)
  T3: Payment Service → Authorize payment
  T4: Order Service → Create order record
  T5: Warehouse Service → Initiate picking
  T6: Shipping Service → Create shipment
  T7: Notification Service → Send order confirmation

Failure handling:
  If T5 fails (item out of stock in warehouse despite reservation):
    C4: Cancel order record
    C3: Void payment authorization
    C2: (no-op)
    C1: Release inventory reservation
    → Notify customer: "Sorry, item out of stock"
```

### User Registration with Third-Party Services

```
Choreographed Saga (simple, loosely coupled):

Events:
  UserCreated → MailService subscribes → sends welcome email
  UserCreated → AnalyticsService subscribes → creates user segment
  UserCreated → CRMService subscribes → creates CRM record
  
  Each service independently processes the event.
  If CRM fails → retry from Kafka → no compensation needed (idempotent create).
  
  Why choreography here:
    - Steps are independent (no ordering dependency)
    - Each service handles its own failures
    - No complex compensation needed
    - Adding a new subscriber doesn't require changing the orchestrator
```

---

## Interview Talking Points & Scripts

### Why Not 2PC

> *"I'd avoid 2PC across microservices for three reasons. First, it's a blocking protocol — participants hold locks between PREPARE and COMMIT, which kills throughput under load. Second, if the coordinator crashes between phases, all participants freeze with locks held until manual intervention. Third, it doesn't work across heterogeneous databases — our booking is in PostgreSQL but notifications are in DynamoDB, and DynamoDB doesn't support XA transactions."*

### The Saga Pattern

> *"I'd use the Saga pattern with orchestration. The Booking Orchestrator drives the flow: reserve seats → charge payment → confirm booking → send notification. Each step is a local transaction that commits independently. If payment fails, the orchestrator runs compensating actions in reverse: release the reserved seats. Every step is idempotent — safe to retry if there's a network timeout."*

### Transactional Outbox

> *"For the dual-write problem — we need to both update our database and publish an event to Kafka — I'd use the transactional outbox pattern. We write the business data and the event to the same database in one ACID transaction. A CDC process (Debezium) reads the outbox table and publishes to Kafka. This guarantees that if the reservation is committed, the event will be published — they can't get out of sync."*

### Compensation Design

> *"Each saga step has a compensating action: reserve seat → release seat, charge card → refund card, confirm booking → cancel booking. Compensations are idempotent — if 'release seat' is called twice, the second call is a no-op. I use semantic statuses (PENDING → CONFIRMED → CANCELLED) instead of hard deletes, so we have a full audit trail."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Proposing 2PC across microservices
**Why it's wrong**: 2PC is blocking, has a coordinator SPOF, doesn't work across heterogeneous databases, and kills throughput.
**Better**: Use the Saga pattern with orchestration for complex flows.

### ❌ Mistake 2: Not mentioning compensating transactions
**Why it's wrong**: A Saga without compensation is just a series of unrelated writes with no consistency guarantee.
**Better**: Define the compensating action for every forward step.

### ❌ Mistake 3: Forgetting idempotency
**Why it's wrong**: Network retries can cause duplicate execution. Without idempotency, "charge $99" might execute twice.
**Better**: Every saga step must be idempotent, using an idempotency key.

### ❌ Mistake 4: Not addressing the dual-write problem
**Why it's wrong**: Writing to a database AND publishing to Kafka is not atomic. One can succeed while the other fails.
**Better**: Use the transactional outbox pattern — write both in one DB transaction, CDC publishes to Kafka.

### ❌ Mistake 5: Using choreography for complex multi-step flows
**Why it's wrong**: With 5+ steps, choreographed sagas become impossible to debug. Events bounce between services with no single view of the saga state.
**Better**: Use orchestration for complex flows (> 3 steps), choreography for simple event reactions.

### ❌ Mistake 6: Not handling compensation failures
**Why it's wrong**: Compensations can fail too. Without retry logic and DLQ, the system can get stuck in an inconsistent state.
**Better**: Retry with backoff → DLQ → scheduled reconciliation → manual intervention.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│       DISTRIBUTED TRANSACTIONS & SAGAS CHEAT SHEET           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  2PC: Blocking, coordinator SPOF, cross-tech impossible.     │
│       AVOID in microservices.                                │
│                                                              │
│  SAGA: Sequence of local transactions + compensations        │
│    Forward: T1 → T2 → T3 → T4                               │
│    On T3 failure: C2 → C1 (compensate in reverse)            │
│                                                              │
│  EVERY STEP MUST BE:                                         │
│    ✓ Idempotent (safe to retry with idempotency key)         │
│    ✓ Compensatable (has a defined undo action)               │
│    ✓ Timeout-aware (bounded execution time)                  │
│                                                              │
│  ORCHESTRATION (preferred for complex flows):                │
│    Central orchestrator drives flow                          │
│    Easy to debug — one place to see saga state               │
│    Best for: checkout, booking, payment flows                │
│                                                              │
│  CHOREOGRAPHY (for simple flows):                            │
│    Event-driven, no coordinator                              │
│    Decoupled but hard to debug                               │
│    Best for: notifications, simple reactions (< 3 steps)     │
│                                                              │
│  TRANSACTIONAL OUTBOX:                                       │
│    Business data + event in SAME DB transaction              │
│    CDC (Debezium) publishes to Kafka from outbox table       │
│    Solves the dual-write problem atomically                  │
│                                                              │
│  FAILURE HANDLING:                                           │
│    Retry with backoff → DLQ → reconciliation → manual        │
│    Saga-level timeout (5 min) prevents zombie sagas          │
│                                                              │
│  SEMANTIC STATES (not hard deletes):                         │
│    PENDING → CONFIRMED → COMPLETED                           │
│    PENDING → CANCELLED (compensated)                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 1: CAP Theorem** — Consistency tradeoffs in distributed transactions
- **Topic 10: Message Queues vs Event Streams** — Kafka for saga event transport
- **Topic 22: Delivery Guarantees** — At-least-once delivery and idempotency
- **Topic 24: Monolith vs Microservices** — Why microservices need sagas
- **Topic 32: Leader Election & Coordination** — Coordination patterns

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
