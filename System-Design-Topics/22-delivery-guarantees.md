# 🎯 Topic 22: Delivery Guarantees

> **System Design Interview — Deep Dive**
> Comprehensive coverage of at-most-once, at-least-once, and exactly-once delivery — the transactional outbox pattern, idempotent consumers, Kafka transactions, and practical approaches to message delivery in distributed systems.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [At-Most-Once Delivery](#at-most-once-delivery)
3. [At-Least-Once Delivery](#at-least-once-delivery)
4. [Exactly-Once Delivery](#exactly-once-delivery)
5. [Why Exactly-Once Is Hard](#why-exactly-once-is-hard)
6. [The Practical Approach: At-Least-Once + Idempotent Consumers](#the-practical-approach-at-least-once--idempotent-consumers)
7. [Transactional Outbox Pattern](#transactional-outbox-pattern)
8. [Kafka Exactly-Once Semantics](#kafka-exactly-once-semantics)
9. [Idempotency Patterns](#idempotency-patterns)
10. [Payment Idempotency — The Gold Standard](#payment-idempotency--the-gold-standard)
11. [Delivery Guarantees by Operation Type](#delivery-guarantees-by-operation-type)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

In distributed systems, messages can be **lost**, **duplicated**, or **delivered out of order** due to network failures, server crashes, and retries. Delivery guarantees define the contract between producers and consumers.

| Guarantee | Definition | Message Can Be | Cost |
|---|---|---|---|
| **At-most-once** | Message delivered 0 or 1 times | Lost | Cheapest |
| **At-least-once** | Message delivered 1 or more times | Duplicated | Medium |
| **Exactly-once** | Message delivered exactly 1 time | Neither | Most expensive |

---

## At-Most-Once Delivery

### How It Works
```
Producer sends message → don't wait for ACK → move on
If message is lost → it's gone forever

Fire-and-forget pattern:
  producer.send(message)  // No ACK, no retry
```

### When to Use
- **Metrics collection**: Missing one data point out of millions is fine.
- **Logging**: A lost log line won't break the system.
- **Real-time streaming**: A dropped video frame is unnoticeable.
- **Analytics events**: Approximate counts are acceptable.

### Why It's Cheapest
No retries, no ACK waiting, no dedup logic, no state management. Maximum throughput, minimum latency.

---

## At-Least-Once Delivery

### How It Works
```
Producer sends message → waits for ACK
If no ACK (timeout/error) → retry
Retry may cause duplicate delivery if original succeeded but ACK was lost

Timeline:
  1. Producer sends message → Server receives → processes → sends ACK
  2. ACK lost in network
  3. Producer: "no ACK received" → retries
  4. Server receives AGAIN → processes AGAIN → duplicate!
```

### When to Use
**Most operations** — this is the default for production systems. Combined with idempotent consumers, it provides effective exactly-once semantics at much lower cost.

### The Key Insight
> At-least-once + idempotent consumer = effectively exactly-once

If the consumer can safely process the same message multiple times (idempotent), then duplicates are harmless. This is dramatically simpler than true exactly-once.

---

## Exactly-Once Delivery

### What It Means
Each message is processed **exactly one time**. No losses, no duplicates.

### Why It's Expensive
Requires coordination across all components:
```
1. Producer → Kafka: Exactly-once via idempotent producer + transactions
2. Kafka → Consumer: Exactly-once via transactional consume + commit
3. Consumer → Database: Exactly-once via transactional outbox or dedup

All three steps must be exactly-once. If ANY step has a gap, the guarantee breaks.
```

### When Required
- **Financial transactions**: Double-charge is unacceptable.
- **Ad click billing**: Overcounting = overbilling advertisers.
- **Inventory management**: Overselling = fulfillment failure.
- **Regulatory events**: Compliance requires exactly one record per event.

---

## Why Exactly-Once Is Hard

### The Two Generals Problem
```
Two generals must agree to attack:
  General A sends message: "Attack at dawn"
  General B receives it? Unknown (messenger may be captured)
  General B sends ACK: "Confirmed"
  General A receives ACK? Unknown (return messenger may be captured)

No finite number of messages can guarantee both generals know they agree.
This is fundamentally why distributed consensus is hard.
```

### The Practical Impossibility
```
True exactly-once delivery across network boundaries is IMPOSSIBLE
because the network can always fail between "process" and "acknowledge."

What we CAN achieve:
  "Effectively exactly-once" via at-least-once + idempotent processing
  
The processing is idempotent → duplicates produce the same result → 
  from the system's perspective, it's as if the message was processed once.
```

---

## The Practical Approach: At-Least-Once + Idempotent Consumers

### Pattern
```
Producer: Send with retry (at-least-once)
Consumer: Process idempotently (handle duplicates safely)

Result: Each message's EFFECT happens exactly once,
        even if the message is delivered multiple times.
```

### Example: Notification
```python
def process_notification(event):
    notification_id = f"notif:{event.type}:{event.target_user}:{event.content_id}"
    
    # Idempotent check: have we already sent this notification?
    if redis.setnx(notification_id, "sent", ex=3600):
        # First time → send notification
        send_push_notification(event.target_user, event.message)
    else:
        # Duplicate → skip (notification already sent)
        log.info(f"Duplicate notification skipped: {notification_id}")
```

### Interview Script
> *"True exactly-once delivery is expensive — it requires Kafka transactions, Flink checkpointing, and a dedup layer. For most operations (notifications, feed updates, search indexing), at-least-once with idempotent handlers is sufficient and dramatically simpler. If the notification service sends 'Alice liked your photo' twice, the user sees a duplicate notification — mildly annoying but not harmful. We save the engineering cost of exactly-once infrastructure for the operations where duplicates cause real damage: billing, ad click counting, and payment processing."*

---

## Transactional Outbox Pattern

### The Problem
Writing to the database AND publishing to Kafka is NOT atomic:
```python
# NON-ATOMIC — can fail between steps!
def create_order(order):
    db.insert(order)          # Step 1: DB write succeeds
    kafka.publish(order_event) # Step 2: Kafka publish FAILS
    # DB has the order, but no event was published!
    # Downstream consumers never learn about the order.
```

### The Solution
Write the event to the **same database transaction** as the business data:
```python
def create_order(order):
    with db.transaction():
        db.insert("orders", order)
        db.insert("outbox", {
            "event_type": "OrderCreated",
            "payload": json.dumps(order),
            "created_at": now()
        })
    # Both inserts succeed or both fail — atomically!
```

A separate **CDC connector** (Debezium) reads the outbox table and publishes to Kafka:
```
outbox table → Debezium CDC → Kafka → Consumers

If Debezium fails → it resumes from its last position
Events are never lost (they're in the DB) and never duplicated (CDC tracks position)
```

### Interview Script
> *"The transactional outbox pattern is how I'd achieve exactly-once publishing to Kafka. Instead of writing to the database and then publishing to Kafka (which can fail between the two operations), I write both the business data and the outbox event in the same database transaction. A separate Debezium CDC connector reads the outbox table and publishes to Kafka. The event is either committed with the business data or not at all — atomically."*

---

## Kafka Exactly-Once Semantics

### Producer Idempotence
```
enable.idempotence = true

Kafka assigns each producer a Producer ID (PID).
Each message gets a sequence number.
Kafka brokers dedup: same PID + same sequence = skip.
```

### Kafka Transactions
```java
producer.initTransactions();
try {
    producer.beginTransaction();
    producer.send(new ProducerRecord("output-topic", key, value));
    producer.sendOffsetsToTransaction(offsets, consumerGroupId);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

### End-to-End Exactly-Once
```
Consumer reads → Processes → Produces output → Commits offset
All in ONE Kafka transaction:
  - Output message is written
  - Consumer offset is committed
  Both succeed or both fail → no duplicates, no losses

Cost: ~20% throughput reduction due to transaction overhead.
```

---

## Idempotency Patterns

### Pattern 1: Natural Idempotency
```
SET user:123:name "Alice"
  → Running this 1 time or 10 times produces the same result.
  → Naturally idempotent. No extra logic needed.
```

### Pattern 2: Dedup Key Check
```
Before processing:
  if redis.setnx(f"processed:{event_id}", 1, ex=86400):
      process(event)
  else:
      skip("duplicate")
```

### Pattern 3: Database Unique Constraint
```sql
INSERT INTO processed_events (event_id, result)
VALUES ('evt-123', '{"status": "success"}')
ON CONFLICT (event_id) DO NOTHING;
-- If already processed → no-op (idempotent)
```

### Pattern 4: Conditional Update
```sql
UPDATE accounts SET balance = balance - 100
WHERE account_id = 'A' AND last_transaction_id != 'txn-456';
-- Only applies if this specific transaction hasn't been applied yet
```

### Pattern 5: Version Check
```sql
UPDATE inventory SET quantity = 49, version = 6
WHERE product_id = 'X' AND version = 5;
-- Only succeeds if version matches (no concurrent modification)
```

---

## Payment Idempotency — The Gold Standard

### Flow
```
1. Client generates idempotency_key (UUID)
2. Client sends: POST /charge { amount: 100, key: "abc-123" }
3. Server checks Redis: GET idempotency:abc-123
   a. Key exists → return stored result (no double charge)
   b. Key doesn't exist → continue

4. Server acquires lock: SET lock:abc-123 1 NX EX 30
5. Server calls payment gateway: charge($100)
6. Server stores result: SET idempotency:abc-123 {result} EX 86400
7. Server releases lock: DEL lock:abc-123
8. Return result to client

If client retries (step 2 again):
  Step 3a: Key exists → return stored result → NO DOUBLE CHARGE
```

### Interview Script
> *"For the payment flow, idempotency is the foundation. The charge API call includes a client-generated idempotency key. The first call charges the card and stores the result keyed by idempotency_key. If our server crashes after charging but before responding, the client retries with the same key — our system sees the key already exists and returns the stored result. No double charge, even in the face of network failures, server crashes, or client retries."*

---

## Delivery Guarantees by Operation Type

| Operation | Guarantee Needed | Implementation | Why |
|---|---|---|---|
| **Metrics/Logging** | At-most-once | Fire and forget | Missing one datapoint is fine |
| **Notifications** | At-least-once | Retry + dedup | Duplicate notification is annoying but not harmful |
| **Feed updates** | At-least-once | Idempotent consumer | Duplicate feed entry filtered at read |
| **Search indexing** | At-least-once | Idempotent upsert | Re-indexing same doc is a no-op |
| **Email sending** | At-least-once + dedup | Redis SETNX | Duplicate email is bad but not catastrophic |
| **Payment** | Exactly-once | Idempotency key + outbox | Double charge is unacceptable |
| **Ad click billing** | Exactly-once | Kafka transactions + dedup | Overbilling is revenue fraud |
| **Inventory update** | Exactly-once | DB transaction + version | Overselling is fulfillment failure |

---

## Interview Talking Points & Scripts

### The Practical Default
> *"At-least-once with idempotent consumers is the practical default for 90% of operations. True exactly-once is reserved for billing and payments where the cost of duplicates is measured in dollars and legal liability."*

### Transactional Outbox
> *"I'd write business data and the Kafka event in the same database transaction — the transactional outbox pattern. A CDC connector publishes to Kafka. Atomic: either both succeed or neither does."*

### Idempotency as Universal Safety Net
> *"Idempotency is the single most important property in a distributed system. If every operation can be safely retried — create with dedup check, update with version check, delete that's a no-op if already deleted — then the system naturally heals from any transient failure. The universal answer to failure is 'retry.'"*

---

## Common Interview Mistakes

### ❌ Mistake 1: Claiming exactly-once is easy
### ❌ Mistake 2: Not mentioning idempotency as the practical solution
### ❌ Mistake 3: Using exactly-once for everything (overkill for notifications)
### ❌ Mistake 4: Not mentioning the transactional outbox for DB + Kafka atomicity
### ❌ Mistake 5: Confusing message delivery guarantee with processing guarantee

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│             DELIVERY GUARANTEES CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  AT-MOST-ONCE: Fire and forget. May lose messages.           │
│    Use: metrics, logging, real-time streams                  │
│                                                              │
│  AT-LEAST-ONCE: Retry on failure. May duplicate.             │
│    Use: Most operations (+ idempotent consumer)              │
│    This is the DEFAULT for production systems.               │
│                                                              │
│  EXACTLY-ONCE: No loss, no duplicates. Expensive.            │
│    Use: payments, billing, inventory                         │
│    Impl: Kafka transactions + outbox + dedup                 │
│                                                              │
│  PRACTICAL FORMULA:                                          │
│    At-least-once + idempotent consumer                       │
│    = effectively exactly-once                                │
│    = 90% of the benefit at 10% of the cost                   │
│                                                              │
│  TRANSACTIONAL OUTBOX:                                       │
│    Business data + event in SAME DB transaction              │
│    CDC connector publishes to Kafka                          │
│    Atomic: both succeed or neither does                      │
│                                                              │
│  IDEMPOTENCY: "The single most important property"           │
│    If every op can be safely retried → "retry" heals all     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
