# Multi-Step Processes & Sagas - Complete HLD Guide

## Table of Contents
1. [Introduction](#introduction)
2. [The Problem](#the-problem)
3. [Solution Approaches](#solution-approaches)
4. [Saga Pattern Deep Dive](#saga-pattern-deep-dive)
5. [Workflow Engines](#workflow-engines)
6. [Real-World Architecture Examples](#real-world-architecture-examples)
7. [Failure Handling & Compensation](#failure-handling--compensation)
8. [Decision Framework](#decision-framework)
9. [Interview Guide](#interview-guide)

---

## Introduction

**Multi-Step Processes** (also known as **Sagas**) are business workflows that require coordinating multiple services, databases, or external systems to complete a single user request. Unlike simple CRUD operations, these processes:

- Span multiple services/databases
- Take seconds to hours to complete
- Require coordination and state management
- Must handle partial failures gracefully
- Need compensating actions for rollback

**Common Examples:**
- **E-commerce**: Order → Payment → Inventory → Shipping → Notification
- **Travel booking**: Reserve flight → Reserve hotel → Charge payment → Send confirmation
- **Ride-hailing**: Match driver → Accept ride → Navigate → Complete trip → Process payment
- **Food delivery**: Accept order → Notify restaurant → Assign driver → Track delivery → Complete
- **Banking**: Transfer → Debit source → Credit destination → Notify both parties

---

## The Problem

### Why Distributed Transactions Are Hard

```
┌──────────────────────────────────────────────────────────────────┐
│              E-COMMERCE ORDER FULFILLMENT NIGHTMARE               │
└──────────────────────────────────────────────────────────────────┘

Ideal Flow (Everything Works):
1. Charge payment          ✓
2. Reserve inventory       ✓
3. Create shipping label   ✓
4. Notify warehouse        ✓
5. Send confirmation email ✓
6. Update analytics        ✓

Reality (Things Fail):

Attempt 1:
1. Charge payment          ✓
2. Reserve inventory       ✓
3. Create shipping label   ✗ TIMEOUT
   → Payment charged but no shipment!
   → Need to refund payment
   → Need to release inventory
   → How to rollback?

Attempt 2:
1. Charge payment          ✓
2. Reserve inventory       ✓
3. Create shipping label   ✓
4. Notify warehouse        ✓
5. Send confirmation email ✗ FAILS
   → Order is valid, but user not notified
   → Should we retry email?
   → Or mark order as needing manual notification?

Attempt 3:
1. Charge payment          ✓
2. Server CRASHES during inventory check
   → Payment charged
   → No record of inventory reservation
   → No way to rollback automatically
   → Money lost!

Problems:
├─ No atomic commit across services
├─ Partial failures leave inconsistent state
├─ Compensating actions needed
├─ Retry logic complex
├─ State management difficult
├─ Visibility into process status poor
└─ Debugging failures nightmare
```

### Traditional Distributed Transactions Don't Scale

```
┌──────────────────────────────────────────────────────────────────┐
│              TWO-PHASE COMMIT (2PC) PROBLEMS                      │
└──────────────────────────────────────────────────────────────────┘

2PC Protocol:

Phase 1: PREPARE
Coordinator: "Can everyone commit?"
   ├─> Service A: "Yes" (locks held)
   ├─> Service B: "Yes" (locks held)
   └─> Service C: "Yes" (locks held)

Phase 2: COMMIT
Coordinator: "Everyone commit now!"
   ├─> Service A: COMMIT
   ├─> Service B: COMMIT
   └─> Service C: COMMIT

Problems with 2PC:

1. BLOCKING PROTOCOL
   • All participants wait for coordinator
   • Locks held during entire process
   • Performance killer

2. COORDINATOR FAILURE
   • If coordinator crashes, all participants blocked
   • Need coordinator failover
   • Complex recovery

3. SLOW PARTICIPANTS
   • Slowest service delays everyone
   • No way to bypass
   • Cascading delays

4. DOESN'T WORK WITH EXTERNAL SERVICES
   • Payment gateways don't support 2PC
   • HTTP APIs can't participate
   • Most real services incompatible

Result: 2PC impractical for microservices!
```

---

## Solution Approaches

### Comparison of Patterns

```
┌──────────────────────────────────────────────────────────────────┐
│              MULTI-STEP PROCESS SOLUTIONS                         │
└──────────────────────────────────────────────────────────────────┘

1. CHOREOGRAPHY (Event-Driven)
   Services react to events independently
   
   Order Created Event
      ↓
   ├─> Payment Service (charges, publishes PaymentCompleted)
   ├─> Inventory Service (listens, reserves, publishes InventoryReserved)
   └─> Shipping Service (listens, creates label)
   
   Pros: Loose coupling, no central coordinator
   Cons: Hard to understand flow, complex debugging

2. ORCHESTRATION (Central Coordinator)
   One service orchestrates all steps
   
   Order Service
      ↓
   1. Call Payment API
   2. Call Inventory API
   3. Call Shipping API
   4. Send notification
   
   Pros: Clear flow, easier debugging
   Cons: Coordinator becomes bottleneck

3. SAGA PATTERN (Compensating Transactions)
   Each step has a compensating action for rollback
   
   Success: Step 1 → Step 2 → Step 3 ✓
   Failure: Step 1 ✓ → Step 2 ✗ → Undo Step 1
   
   Pros: No distributed locks, scales well
   Cons: Eventual consistency, complex compensation

4. WORKFLOW ENGINES (Temporal, Airflow)
   Specialized systems for orchestration
   
   Define workflow as code
   Engine handles: retries, state, timeouts
   
   Pros: Built-in reliability, easier development
   Cons: New system to learn/operate
```

---

## Saga Pattern Deep Dive

### Saga Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    SAGA PATTERN OVERVIEW                          │
└──────────────────────────────────────────────────────────────────┘

Definition: Sequence of local transactions, each with a compensating transaction

Example: Order Fulfillment Saga

Forward Flow (Happy Path):
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Charge    │────▶│  Reserve    │────▶│   Create    │
│  Payment    │     │  Inventory  │     │  Shipping   │
└─────────────┘     └─────────────┘     └─────────────┘
      T1                  T2                  T3

Compensation Flow (Failure):
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Refund    │◀────│  Release    │◀────│   Cancel    │
│  Payment    │     │  Inventory  │     │  Shipping   │
└─────────────┘     └─────────────┘     └─────────────┘
      C1                  C2                  C3

Execution Scenarios:

Scenario 1: All Steps Succeed
T1 (Charge) ✓ → T2 (Reserve) ✓ → T3 (Ship) ✓
Result: Order complete!

Scenario 2: Step 2 Fails
T1 (Charge) ✓ → T2 (Reserve) ✗
Execute: C1 (Refund) ✓
Result: Payment refunded, no order

Scenario 3: Step 3 Fails
T1 (Charge) ✓ → T2 (Reserve) ✓ → T3 (Ship) ✗
Execute: C2 (Release) ✓ → C1 (Refund) ✓
Result: Inventory released, payment refunded

Guarantees:
• Either all steps complete OR all compensated
• Eventually consistent
• No distributed locks needed
```

### Choreography vs Orchestration Sagas

```
┌──────────────────────────────────────────────────────────────────┐
│              CHOREOGRAPHY (Event-Driven) SAGA                     │
└──────────────────────────────────────────────────────────────────┘

No central coordinator, services react to events

Order Service publishes: OrderCreated
   ↓
Payment Service:
   Listens to OrderCreated
   Charges card
   Publishes: PaymentCompleted or PaymentFailed
   ↓
Inventory Service:
   Listens to PaymentCompleted
   Reserves items
   Publishes: InventoryReserved or InventoryFailed
   ↓
Shipping Service:
   Listens to InventoryReserved
   Creates label
   Publishes: ShippingLabelCreated
   ↓
Notification Service:
   Listens to ShippingLabelCreated
   Sends confirmation email

If InventoryFailed:
   Payment Service listens
   Executes refund compensation
   Publishes: PaymentRefunded

Pros:
✓ No single point of failure
✓ Services decoupled
✓ Each service autonomous

Cons:
✗ Hard to understand overall flow
✗ Difficult to debug
✗ No visibility into saga state
✗ Cyclic dependencies possible

┌──────────────────────────────────────────────────────────────────┐
│              ORCHESTRATION (Coordinator) SAGA                     │
└──────────────────────────────────────────────────────────────────┘

Central coordinator manages the flow

Order Coordinator:
   ↓
Step 1: Call Payment Service
   If success → Step 2
   If fail → End with error
   ↓
Step 2: Call Inventory Service
   If success → Step 3
   If fail → Compensate: Refund payment
   ↓
Step 3: Call Shipping Service
   If success → Step 4
   If fail → Compensate: Release inventory + Refund
   ↓
Step 4: Send Notification
   If fail → Log for manual retry (non-critical)

Pros:
✓ Clear flow (easy to understand)
✓ Centralized monitoring
✓ Easier debugging
✓ Simple compensation logic

Cons:
✗ Coordinator is bottleneck
✗ Coordinator is single point of failure
✗ Coordinator must be highly available

Recommendation: Use Orchestration for most cases
• Easier to reason about
• Better tooling support
• Clearer error handling
```

---

## Workflow Engines

### What Are Workflow Engines?

```
┌──────────────────────────────────────────────────────────────────┐
│                    WORKFLOW ENGINE OVERVIEW                       │
└──────────────────────────────────────────────────────────────────┘

Workflow Engine = Orchestration + State Management + Reliability

Key Features:
├─ Define workflows as code (or config)
├─ Automatic retry logic
├─ State persistence
├─ Timeout handling
├─ Compensation management
├─ Progress visualization
└─ Historical execution logs

Popular Engines:

TEMPORAL (Modern, Developer-Friendly)
• Define workflows in your language (Go, Python, Java)
• Automatic state persistence
• Built-in retry/timeout
• Strong consistency
• Use: New projects, complex workflows

APACHE AIRFLOW (Data Engineering)
• DAG-based workflows
• Python-based
• Rich operator library
• Scheduling built-in
• Use: ETL pipelines, data processing

AWS STEP FUNCTIONS (Managed)
• JSON-based workflow definition
• Fully managed (no servers)
• Integrates with AWS services
• Pay per execution
• Use: AWS-native, simple workflows

NETFLIX CONDUCTOR (Battle-tested)
• JSON-based workflows
• Polyglot workers
• UI for monitoring
• Open source
• Use: Complex microservice orchestration
```

### Workflow Engine Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│              TYPICAL WORKFLOW ENGINE ARCHITECTURE                 │
└──────────────────────────────────────────────────────────────────┘

                    ┌────────────────┐
                    │  Workflow      │
                    │  Definition    │
                    │  (Code/Config) │
                    └────────┬───────┘
                             │
                             ▼
                    ┌────────────────┐
                    │  Workflow      │
                    │  Engine        │
                    │  (Temporal/    │
                    │   Airflow)     │
                    └────────┬───────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
    ┌────────┐         ┌────────┐         ┌────────┐
    │ State  │         │ Task   │         │Workers │
    │ Store  │         │ Queue  │         │        │
    │        │         │        │         │Execute │
    │Persist │         │Pending │         │tasks   │
    │workflow│         │tasks   │         │        │
    │state   │         │        │         │        │
    └────────┘         └────────┘         └────────┘

Flow:
1. Submit workflow → Engine
2. Engine persists state
3. Engine enqueues first task
4. Worker picks up task
5. Worker executes and returns result
6. Engine updates state
7. Engine enqueues next task
8. Repeat until complete

If worker crashes:
• State is persisted
• Engine detects timeout
• Engine retries task
• Workflow continues

If engine crashes:
• State in database
• Engine restarts
• Resumes from last state
• No data loss
```

---

## Real-World Architecture Examples

### Example 1: Uber - Ride Completion Saga

```
┌──────────────────────────────────────────────────────────────────┐
│                  UBER RIDE WORKFLOW ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 20M+ trips/day globally

Challenge: Coordinate driver, rider, payment, rating over 30+ minutes

Workflow Steps:

Step 1: MATCH DRIVER (30 seconds)
   ↓
   Find nearby drivers
   Send requests (multiple)
   Wait for acceptance
   Update ride status: "matched"

Step 2: DRIVER NAVIGATION (Variable: 5-30 minutes)
   ↓
   Track driver location
   Update rider with ETA
   Send notifications
   Status: "driver_arriving"

Step 3: PICKUP (2-5 minutes)
   ↓
   Driver arrives
   Wait for rider
   Confirm pickup
   Status: "in_progress"

Step 4: RIDE IN PROGRESS (Variable: 10-60 minutes)
   ↓
   Track GPS location
   Update route
   Calculate fare
   Status: "ongoing"

Step 5: DROP-OFF (2 minutes)
   ↓
   Arrive at destination
   Confirm drop-off
   Calculate final fare
   Status: "completed"

Step 6: PAYMENT PROCESSING (10 seconds)
   ↓
   Charge rider's card
   Handle payment failures
   Retry if needed
   Status: "payment_pending" → "payment_complete"

Step 7: DRIVER PAYOUT (Async)
   ↓
   Calculate driver earnings
   Account for fees, bonuses
   Schedule payout
   Status: "driver_paid"

Step 8: RATINGS (Async)
   ↓
   Prompt both parties for ratings
   Wait up to 24 hours
   Update driver/rider scores

Architecture:

┌────────────────────────┐
│   Uber Cadence         │
│   (Workflow Engine)    │
│   • Manages state      │
│   • Handles retries    │
│   • Tracks progress    │
└────────┬───────────────┘
         │
         ├─────────────────────┬─────────────────┬─────────────────┐
         ▼                     ▼                 ▼                 ▼
   ┌──────────┐        ┌──────────┐      ┌──────────┐      ┌──────────┐
   │ Match    │        │ Location │      │ Payment  │      │ Rating   │
   │ Service  │        │ Service  │      │ Service  │      │ Service  │
   └──────────┘        └──────────┘      └──────────┘      └──────────┘

Failure Scenarios & Compensation:

Scenario 1: Driver Cancels After Match
   Compensation:
   • Find new driver (restart from Step 1)
   • Don't charge cancellation fee (grace period)
   • Notify rider

Scenario 2: Payment Fails
   Compensation:
   • Mark ride as "payment_failed"
   • Send notification to rider
   • Retry payment (multiple methods)
   • If all fail: Manual follow-up
   • Driver still paid (Uber covers)

Scenario 3: Ride Interrupted (Crash, Emergency)
   Compensation:
   • Calculate partial fare
   • Charge proportional amount
   • Mark ride as "interrupted"
   • Allow completion later

State Management:

Workflow State (Persisted):
{
  ride_id: "ride_abc",
  status: "in_progress",
  current_step: "navigation",
  driver_id: "driver_123",
  rider_id: "rider_456",
  pickup: {lat, lng},
  dropoff: {lat, lng},
  fare_estimate: 15.50,
  payment_method: "card_***1234",
  started_at: "2025-01-23T10:00:00Z",
  metadata: {...}
}

Benefits of Workflow Engine:

• Automatic state persistence (survive crashes)
• Built-in retry logic (transient failures)
• Timeout handling (stuck steps)
• Visualization (see ride progress)
• Historical analysis (debugging)

Performance:
• Workflow creation: <100ms
• State updates: <50ms
• Average ride duration: 20 minutes
• Completion rate: 99.5%
```

---

### Example 2: Stripe - Payment Processing Saga

```
┌──────────────────────────────────────────────────────────────────┐
│              STRIPE PAYMENT WORKFLOW ARCHITECTURE                 │
└──────────────────────────────────────────────────────────────────┘

Scale: Billions of payments/year

Challenge: Coordinate multiple steps with strong consistency

Payment Workflow:

Step 1: VALIDATE (100ms)
   ↓
   Validate card details
   Check fraud rules
   Verify merchant account
   Create payment intent

Step 2: AUTHORIZATION (500ms)
   ↓
   Contact card network (Visa/Mastercard)
   Request authorization hold
   Receive auth code
   Status: "requires_capture"

Step 3: CAPTURE (200ms)
   ↓
   Capture authorized funds
   Transfer from customer to Stripe
   Update balances
   Status: "succeeded"

Step 4: SETTLEMENT (Async, T+2 days)
   ↓
   Transfer to merchant account
   Account for fees
   Generate payout
   Status: "paid"

Step 5: RECONCILIATION (Daily)
   ↓
   Match with bank statements
   Detect discrepancies
   Generate reports

Architecture:

┌────────────────────────┐
│   Stripe API           │
│   • Idempotency        │
│   • Webhook events     │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Payment State Machine│
│   • PostgreSQL-backed  │
│   • Event sourcing     │
│   • Audit log          │
└────────┬───────────────┘
         │
         ├─────────────────────┬─────────────────┐
         ▼                     ▼                 ▼
   ┌──────────┐        ┌──────────┐      ┌──────────┐
   │  Card    │        │ Fraud    │      │ Ledger   │
   │ Network  │        │ Detection│      │ Service  │
   │ Gateway  │        │          │      │          │
   └──────────┘        └──────────┘      └──────────┘

Failure Handling:

Scenario 1: Authorization Fails (Insufficient Funds)
   Compensation:
   • Don't proceed to capture
   • Update status: "failed"
   • Send webhook to merchant
   • No rollback needed (nothing committed)

Scenario 2: Capture Timeout
   Compensation:
   • Retry capture (idempotent)
   • If repeated failures: Manual review
   • Don't double-charge (idempotency key)
   • Eventually succeed or expire

Scenario 3: Settlement Fails
   Compensation:
   • Retry settlement daily
   • Alert operations team
   • Manual reconciliation if needed
   • Customer already charged (must resolve)

Idempotency Strategy:

Every request has idempotency key:
   POST /payments
   Idempotency-Key: unique_key_123
   
   First request: Processes payment
   Duplicate request: Returns same result
   
   Prevents: Double charging on retry

Event Sourcing:

All state changes stored as events:
1. PaymentIntentCreated
2. AuthorizationRequested
3. AuthorizationSucceeded
4. CaptureRequested
5. CaptureSucceeded
6. SettlementScheduled

Benefits:
• Complete audit trail
• Can reconstruct any state
• Regulatory compliance
• Debugging easier

Performance:
• Payment authorization: 500-1000ms
• Capture: 200-500ms
• Success rate: 99.95%
• Zero tolerance for data loss
```

---

### Example 3: Airbnb - Booking Confirmation Workflow

```
┌──────────────────────────────────────────────────────────────────┐
│              AIRBNB BOOKING WORKFLOW ARCHITECTURE                 │
└──────────────────────────────────────────────────────────────────┘

Scale: 100M+ bookings/year

Challenge: Coordinate guest, host, payment, calendar across days

Booking Workflow (Instant Book):

Step 1: INITIATE BOOKING (100ms)
   ↓
   Validate dates available
   Calculate total price
   Check guest eligibility
   Create booking record (status: "pending")

Step 2: HOLD DATES (500ms)
   ↓
   Distributed lock on listing
   Block calendar dates
   Prevent double booking
   Status: "dates_held"

Step 3: AUTHORIZE PAYMENT (1-2 seconds)
   ↓
   Pre-authorize full amount
   Verify sufficient funds
   Don't capture yet
   Status: "payment_authorized"

Step 4: NOTIFY HOST (Immediate)
   ↓
   Send notification
   If host must confirm: Wait up to 24 hours
   If instant book: Auto-confirm
   Status: "awaiting_host" or "confirmed"

Step 5: CAPTURE PAYMENT (If confirmed)
   ↓
   Charge guest's card
   Transfer to Airbnb
   Schedule host payout
   Status: "payment_captured"

Step 6: SEND CONFIRMATIONS (100ms)
   ↓
   Email to guest
   Email to host
   Push notifications
   Status: "confirmed"

Step 7: PRE-CHECKIN REMINDERS (Days later)
   ↓
   Send check-in instructions (1 day before)
   Provide host contact info
   House rules reminder

Step 8: POST-CHECKOUT (After stay)
   ↓
   Request reviews
   Process host payout
   Close booking
   Status: "completed"

Architecture:

┌────────────────────────┐
│   Temporal Workflow    │
│   • Durable execution  │
│   • Survives crashes   │
│   • State in Cassandra │
└────────┬───────────────┘
         │
         ├─────────────────────┬─────────────────┬─────────────────┐
         ▼                     ▼                 ▼                 ▼
   ┌──────────┐        ┌──────────┐      ┌──────────┐      ┌──────────┐
   │Calendar  │        │ Payment  │      │Notification│    │ Payout   │
   │ Service  │        │ Service  │      │  Service   │      │ Service  │
   └──────────┘        └──────────┘      └──────────┘      └──────────┘

Compensation Scenarios:

Scenario 1: Payment Authorization Fails
   Rollback:
   • Release calendar dates
   • Notify guest (payment failed)
   • Delete booking record
   Time: Immediate

Scenario 2: Host Declines (Within 24h)
   Rollback:
   • Release payment authorization
   • Release calendar dates
   • Refund any fees
   • Help guest find alternative
   Time: Up to 24 hours

Scenario 3: Guest Cancels
   Compensation depends on cancellation policy:
   • Flexible: Full refund if >24h before
   • Moderate: 50% refund if >5 days before
   • Strict: No refund
   
   Steps:
   • Calculate refund amount
   • Process refund
   • Release calendar dates
   • Notify host
   • Apply cancellation fee if applicable

Long-Running Aspects:

Workflow spans days:
• Booking created: Today
• Check-in: 30 days later
• Check-out: 35 days later
• Payout: 37 days later (T+2)

Workflow must:
• Persist state for weeks
• Wake up at scheduled times
• Send reminders
• Handle cancellations anytime

Temporal Features Used:

• Timers (sleep until check-in date)
• Signals (handle cancellations)
• Queries (check booking status)
• Compensation (rollback on failure)

Performance:
• Booking creation: 2-3 seconds
• Host confirmation: 0-24 hours
• Payment capture: Instant (if confirmed)
• Workflow completion: 30-40 days
• Success rate: 98%
```

---

### Example 4: DoorDash - Order Fulfillment Saga

```
┌──────────────────────────────────────────────────────────────────┐
│              DOORDASH ORDER WORKFLOW ARCHITECTURE                 │
└──────────────────────────────────────────────────────────────────┘

Scale: 1M+ orders/day

Challenge: Coordinate customer, restaurant, dasher in real-time

Order Workflow:

Step 1: ORDER PLACEMENT (1 second)
   ↓
   Validate order
   Check restaurant open
   Calculate estimated time
   Create order (status: "pending")

Step 2: PAYMENT HOLD (2 seconds)
   ↓
   Authorize payment
   Hold funds
   Don't capture yet
   Status: "payment_authorized"

Step 3: SEND TO RESTAURANT (Immediate)
   ↓
   Notify restaurant (tablet/app)
   Restaurant confirms
   Wait for confirmation (timeout: 5 minutes)
   Status: "confirmed" or "rejected"

Step 4: DASHER ASSIGNMENT (30-90 seconds)
   ↓
   Find available dashers
   Calculate optimal assignment
   Send offer to dasher
   Wait for acceptance
   Status: "dasher_assigned"

Step 5: DASHER TO RESTAURANT (5-15 minutes)
   ↓
   Track dasher location
   Update customer with ETA
   Dasher arrives at restaurant
   Status: "dasher_at_restaurant"

Step 6: FOOD PREPARATION (10-30 minutes)
   ↓
   Wait for restaurant
   Dasher picks up food
   Mark picked up
   Status: "picked_up"

Step 7: DELIVERY IN PROGRESS (10-30 minutes)
   ↓
   Track dasher to customer
   Real-time location updates
   ETA updates
   Status: "on_the_way"

Step 8: DELIVERY COMPLETE (1 minute)
   ↓
   Dasher marks delivered
   Customer confirms
   Status: "delivered"

Step 9: PAYMENT CAPTURE (5 seconds)
   ↓
   Capture payment from customer
   Calculate dasher earnings
   Calculate restaurant payout
   Status: "payment_captured"

Step 10: RATINGS & REVIEW (Async)
   ↓
   Request customer rating
   Request dasher rating
   Wait up to 24 hours
   Status: "completed"

Architecture:

┌────────────────────────┐
│   Order Orchestrator   │
│   • Tracks order state │
│   • Coordinates steps  │
│   • Handles timeouts   │
└────────┬───────────────┘
         │
         ├─────────────────────┬─────────────────┬─────────────────┐
         ▼                     ▼                 ▼                 ▼
   ┌──────────┐        ┌──────────┐      ┌──────────┐      ┌──────────┐
   │Restaurant│        │ Dasher   │      │ Payment  │      │ Location │
   │ Service  │        │ Service  │      │ Service  │      │ Service  │
   └──────────┘        └──────────┘      └──────────┘      └──────────┘

Compensation Scenarios:

Scenario 1: Restaurant Rejects Order
   Immediate compensation:
   • Release payment authorization
   • Notify customer
   • Suggest alternatives
   • Apply coupon for inconvenience

Scenario 2: No Dasher Available (15 min timeout)
   Compensation:
   • Cancel order at restaurant
   • Release payment
   • Refund any fees
   • Apologize to customer

Scenario 3: Dasher Cancels Mid-Delivery
   Compensation:
   • Find replacement dasher immediately
   • Track food location
   • Update customer with new ETA
   • Apply credit for delay

Scenario 4: Customer Not Available at Delivery
   Handling:
   • Dasher waits 5 minutes
   • Attempt contact (call/text)
   • If no response: Leave at door or cancel
   • Still charge customer (food was prepared)

Real-Time Updates:

Customer sees:
{
  order_id: "order_abc",
  status: "dasher_on_the_way",
  dasher_name: "John",
  dasher_location: {lat, lng},
  eta_minutes: 12,
  steps_completed: ["confirmed", "preparing", "picked_up"],
  steps_pending: ["delivering", "complete"]
}

Performance:
• Order placement: 1-2 seconds
• Average delivery time: 30-45 minutes
• Workflow duration: 40-90 minutes (including ratings)
• Completion rate: 96% (some cancellations expected)
• Compensation rate: 4%
```

---

### Example 5: AWS - EC2 Instance Launch Workflow

```
┌──────────────────────────────────────────────────────────────────┐
│              AWS EC2 LAUNCH WORKFLOW ARCHITECTURE                 │
└──────────────────────────────────────────────────────────────────┘

Challenge: Coordinate 20+ steps to launch VM

Workflow Steps:

Step 1: VALIDATE REQUEST (200ms)
Step 2: ALLOCATE CAPACITY (500ms)
Step 3: CREATE NETWORK INTERFACE (1 sec)
Step 4: ATTACH STORAGE (2 sec)
Step 5: CONFIGURE SECURITY GROUPS (500ms)
Step 6: ASSIGN IP ADDRESS (500ms)
Step 7: BOOT INSTANCE (30-60 sec)
Step 8: RUN HEALTH CHECKS (10 sec)
Step 9: UPDATE DNS (if applicable) (5 sec)
Step 10: NOTIFY USER (100ms)

Total: 45-75 seconds

State Machine Implementation:
• AWS Step Functions
• Each step is Lambda or API call
• Automatic retries on failure
• Compensation for partial launches

Compensation Example:

If Step 7 (Boot) fails:
• Deallocate capacity
• Release network interface
• Detach storage
• Free IP address
• Mark instance as "failed"
• Partial charges may apply
```

---

### Example 6: Shopify - Order Fulfillment Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│              SHOPIFY ORDER FULFILLMENT ARCHITECTURE               │
└──────────────────────────────────────────────────────────────────┘

Scale: 2M+ merchants, handling millions of orders

Multi-Merchant Complexity:

Order Workflow:

Step 1: ORDER CREATION
Step 2: PAYMENT PROCESSING
Step 3: INVENTORY DEDUCTION
Step 4: NOTIFICATION TO MERCHANT
Step 5: MERCHANT FULFILLMENT (Hours to Days)
Step 6: SHIPPING LABEL GENERATION
Step 7: CARRIER PICKUP
Step 8: TRACKING UPDATES
Step 9: DELIVERY CONFIRMATION
Step 10: MERCHANT PAYOUT (T+7 days)

Long-Running Nature:
• Order to delivery: 2-7 days
• Payout: Additional 7 days
• Total workflow: Up to 14 days

Workflow engine handles:
• Merchant timeout (48 hours to fulfill)
• Carrier integration failures
• Payment capture timing
• Payout scheduling
```

---

## Failure Handling & Compensation

```
┌──────────────────────────────────────────────────────────────────┐
│              COMPENSATION STRATEGIES                              │
└──────────────────────────────────────────────────────────────────┘

1. BACKWARD RECOVERY (Undo)
   Execute compensating transactions in reverse order
   
   T1 ✓ → T2 ✓ → T3 ✗
   Execute: C2 → C1
   
   Use: When state can be reversed

2. FORWARD RECOVERY (Continue)
   Retry failed step until success
   
   T1 ✓ → T2 (retry 3 times) → T3
   
   Use: When operation must complete

3. TIMEOUT & RETRY
   Set timeout for each step
   Retry with exponential backoff
   
   Try 1: Immediate
   Try 2: 1 second
   Try 3: 5 seconds
   Try 4: 30 seconds
   
   Use: Transient failures

4. HUMAN INTERVENTION
   Some failures need manual resolution
   
   Alert operations team
   Provide context and tools
   Resume workflow after fix
   
   Use: Complex business logic, external dependencies

5. PARTIAL COMPLETION
   Some steps are best-effort
   
   Critical: Payment, inventory
   Non-critical: Email notifications
   
   Allow workflow to complete even if non-critical steps fail
```

---

## Decision Framework

```
┌──────────────────────────────────────────────────────────────────┐
│              WHEN TO USE EACH PATTERN                             │
└──────────────────────────────────────────────────────────────────┘

USE SIMPLE ORCHESTRATION:
├─ < 5 steps
├─ All steps synchronous
├─ Fast (<5 seconds total)
└─ Simple compensations

USE SAGA PATTERN:
├─ 5-10 steps
├─ Distributed across services
├─ Need compensations
├─ Medium duration (<5 minutes)
└─ Moderate complexity

USE WORKFLOW ENGINE:
├─ >10 steps
├─ Long-running (>5 minutes)
├─ Complex compensations
├─ Need durability (survive crashes)
├─ Want visualization
└─ Complex business logic

USE EVENT-DRIVEN CHOREOGRAPHY:
├─ Loosely coupled services
├─ No clear "owner"
├─ Eventual consistency OK
└─ Simple per-service logic

AVOID DISTRIBUTED TRANSACTIONS (2PC):
✗ External services involved
✗ Need high availability
✗ Long-running operations
✗ Microservices architecture
```

---

## Interview Guide

```
┌──────────────────────────────────────────────────────────────────┐
│              INTERVIEW FRAMEWORK                                  │
└──────────────────────────────────────────────────────────────────┘

Common Question: "Design an e-commerce order system"

STEP 1: IDENTIFY MULTI-STEP NATURE (2 min)
"This requires coordinating multiple services:
• Payment processing
• Inventory management
• Shipping label creation
• Email notifications

Each can fail independently, so we need a saga pattern."

STEP 2: CHOOSE APPROACH (3 min)
"I'll use Orchestration-based saga because:
• Clear flow (easier to explain)
• Centralized error handling
• Better for interviews

Alternative is Choreography (event-driven) but harder to visualize."

STEP 3: DESIGN SAGA (15 min)

Draw the flow:
Order Service (Orchestrator)
   ↓
1. Charge payment (Payment Service)
   If fail → End
2. Reserve inventory (Inventory Service)
   If fail → Refund payment
3. Create shipping label (Shipping Service)
   If fail → Release inventory + Refund
4. Send confirmation (Notification Service)
   If fail → Log (non-critical)

STEP 4: DISCUSS COMPENSATION (10 min)

"Each step has a compensation:
• Charge → Refund
• Reserve → Release
• Create label → Cancel label

Compensations execute in reverse order on failure."

STEP 5: HANDLE EDGE CASES (10 min)

Q: "What if payment service is down?"
A: "Retry with exponential backoff (3 attempts), then fail gracefully"

Q: "What if server crashes mid-saga?"
A: "Use workflow engine (Temporal) for state persistence,
   or implement idempotency with database-backed state"

Q: "How do you prevent duplicate orders?"
A: "Idempotency keys, check for existing orders before creating"
```

---

## Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                  KEY TAKEAWAYS                                    │
└──────────────────────────────────────────────────────────────────┘

CORE CONCEPTS:
✓ Sagas = Sequence of local transactions + compensations
✓ Orchestration = Central coordinator (recommended)
✓ Choreography = Event-driven (more complex)
✓ Workflow engines = Production-grade orchestration

CRITICAL FEATURES:
✓ Idempotency (safe retries)
✓ State persistence (survive crashes)
✓ Compensation logic (rollback)
✓ Timeout handling
✓ Progress tracking

BEST PRACTICES:
✓ Start with orchestration (simpler)
✓ Use workflow engine for complex flows
✓ Design compensations upfront
✓ Make operations idempotent
✓ Monitor saga progress
✓ Alert on stuck sagas
✓ Log everything for debugging

REAL-WORLD USAGE:
• Uber: Ride workflows (Cadence)
• Airbnb: Booking workflows (Temporal)  
• Netflix: Content processing (Conductor)
• Stripe: Payment processing (Custom)
• DoorDash: Order fulfillment (Custom)

FOR INTERVIEWS:
• Mention saga pattern for multi-step processes
• Discuss compensation strategies
• Consider workflow engines for complex cases
• Always address failure scenarios
```

---

**Document Version**: 1.0  
**Created**: January 2025  
**Type**: High-Level Design Guide  
**Examples**: 6 real-world architectures (Uber, Stripe, Airbnb, DoorDash, AWS, Shopify)  
**Focus**: Saga patterns & workflow orchestration
