# 🎯 Topic 21: Concurrency Control & Contention

> **System Design Interview — Deep Dive**
> Comprehensive coverage of pessimistic locking, optimistic locking, distributed locks, virtual queues for flash sales, the single-writer principle, and how to handle high-contention scenarios in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Pessimistic Locking (SELECT FOR UPDATE)](#pessimistic-locking-select-for-update)
3. [Optimistic Locking (Version Numbers)](#optimistic-locking-version-numbers)
4. [Distributed Locks (Redis SETNX)](#distributed-locks-redis-setnx)
5. [Two-Layer Locking Strategy](#two-layer-locking-strategy)
6. [Virtual Queue for Flash Sales](#virtual-queue-for-flash-sales)
7. [Compare-and-Swap (CAS)](#compare-and-swap-cas)
8. [Single-Writer Principle](#single-writer-principle)
9. [Database-Level Concurrency](#database-level-concurrency)
10. [Choosing Pessimistic vs Optimistic](#choosing-pessimistic-vs-optimistic)
11. [Deadlock Prevention](#deadlock-prevention)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Concurrency Strategy by System Design Problem](#concurrency-strategy-by-system-design-problem)
15. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Concurrency control** prevents multiple processes from corrupting shared data when they access it simultaneously. The key tradeoff: **stronger locking prevents more bugs but reduces throughput**.

```
Without concurrency control:
  Thread A reads seat status: AVAILABLE
  Thread B reads seat status: AVAILABLE
  Thread A books seat → status = BOOKED
  Thread B books seat → status = BOOKED (DOUBLE-BOOKED!)

With concurrency control:
  Thread A acquires lock on seat → reads AVAILABLE → books → releases lock
  Thread B waits for lock → reads BOOKED → rejects → "seat taken"
```

---

## Pessimistic Locking (SELECT FOR UPDATE)

### How It Works
```sql
BEGIN;
  -- Lock the row: no other transaction can read or write this row
  SELECT * FROM seats WHERE seat_id = 'A7' AND status = 'AVAILABLE' FOR UPDATE;
  
  -- If found: book it
  UPDATE seats SET status = 'BOOKED', user_id = 123 WHERE seat_id = 'A7';
  
  -- Create reservation
  INSERT INTO reservations (seat_id, user_id, event_id) VALUES ('A7', 123, 456);
COMMIT;
-- Lock released on COMMIT
```

### Characteristics
| Aspect | Detail |
|---|---|
| **Locks when** | At read time (before making decision) |
| **Blocks others** | Yes — other transactions wait for lock release |
| **Conflict rate** | Designed for HIGH conflict rate |
| **Throughput** | Lower (serial access to locked rows) |
| **Correctness** | Guaranteed (lock prevents all conflicts) |
| **Deadlock risk** | Yes (if multiple locks acquired in different order) |

### When to Use
- **High contention**: Many users competing for the same resource (seat booking, inventory).
- **Critical correctness**: Double-booking is unacceptable (financial, booking systems).
- **Short-lived locks**: Transaction completes in < 100ms.

### Interview Script
> *"For ticket booking with limited inventory, I'd use pessimistic locking: `SELECT * FROM seats WHERE seat_id = 'A7' AND status = 'AVAILABLE' FOR UPDATE`. This acquires a row-level lock — no other transaction can modify this seat until we commit or rollback. If the seat is available, we book it and commit. If not, we return 'seat taken'. The lock duration is < 50ms for this transaction, which is acceptable."*

---

## Optimistic Locking (Version Numbers)

### How It Works
```sql
-- Step 1: Read with version
SELECT name, version FROM profiles WHERE user_id = 123;
-- Returns: name = "Alice", version = 5

-- Step 2: Update with version check
UPDATE profiles 
SET name = 'Alice Johnson', version = 6 
WHERE user_id = 123 AND version = 5;

-- If 1 row updated → SUCCESS (nobody else changed it)
-- If 0 rows updated → CONFLICT (someone else updated version to 6)
-- On conflict: Read again, merge changes, retry
```

### Characteristics
| Aspect | Detail |
|---|---|
| **Locks when** | Never (checks at write time only) |
| **Blocks others** | No — all reads proceed concurrently |
| **Conflict rate** | Designed for LOW conflict rate |
| **Throughput** | Higher (no blocking, no waiting) |
| **Correctness** | Guaranteed via retry on conflict |
| **Deadlock risk** | None (no locks held) |

### When to Use
- **Low contention**: Conflicts are rare (< 1% of operations).
- **Read-heavy workloads**: Many readers, few writers.
- **User-level data**: Two people rarely update the same profile simultaneously.

### Interview Script
> *"For low-contention operations like profile updates, optimistic locking is better. Read the profile with its version number (version=5), make changes, then `UPDATE profiles SET name='Alice', version=6 WHERE user_id=123 AND version=5`. If someone else updated between our read and write, the WHERE clause matches 0 rows and we retry. At < 1% conflict rate, retries are vanishingly rare, and we never hold locks. Throughput is 10x higher than pessimistic locking for this access pattern."*

---

## Distributed Locks (Redis SETNX)

### How It Works
```python
def acquire_lock(resource_id, ttl_seconds=5):
    lock_key = f"lock:{resource_id}"
    lock_value = str(uuid4())  # Unique value to identify lock owner
    
    acquired = redis.set(lock_key, lock_value, nx=True, ex=ttl_seconds)
    return lock_value if acquired else None

def release_lock(resource_id, lock_value):
    # Lua script ensures atomic check-and-delete
    lua_script = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1])
    else
        return 0
    end
    """
    redis.eval(lua_script, 1, f"lock:{resource_id}", lock_value)
```

### Why Unique Lock Value?
```
Without unique value:
  Thread A acquires lock → processes slowly → lock expires (TTL)
  Thread B acquires lock (A's expired) → starts processing
  Thread A finishes → DEL lock → DELETES B's LOCK!
  Thread C acquires lock → now B and C both think they have the lock

With unique value:
  Thread A checks: is lock value == my_value? No (B changed it) → don't delete
  Only the lock owner can release the lock.
```

### TTL Considerations
```
TTL too short: Lock expires while still processing → two holders
TTL too long:  If holder crashes, lock stays for too long → delays

Rule of thumb:
  TTL = expected_processing_time × 3
  Processing takes 1 second → TTL = 3 seconds
  
For longer operations: Use lock renewal (extend TTL while still processing)
```

### Redlock (Multi-Node Redis Lock)
```
For high availability: Acquire lock on majority of N Redis nodes (N=5).
  Lock acquired on 3 of 5 nodes → lock is valid.
  If lock acquired on < 3 → lock fails → release all.
  
Tolerates: Up to 2 Redis node failures without losing lock safety.
```

---

## Two-Layer Locking Strategy

### Architecture for Ticket Booking
```
Layer 1: Redis Distributed Lock (absorbs thundering herd)
  SET lock:event:456 {uuid} NX EX 5
  Only ONE booking transaction proceeds at a time per event
  500K concurrent users → 1 gets through, 499,999 retry

Layer 2: Database Pessimistic Lock (correctness guarantee)
  SELECT * FROM seats WHERE seat_id = 'A7' FOR UPDATE
  Row-level lock is the final correctness guarantee
  
Why both?
  Redis lock: Fast, absorbs load, prevents DB from being hammered
  DB lock:    Correct, ACID, prevents double-booking even if Redis fails
  Belt and suspenders.
```

### Interview Script
> *"For ticket booking with limited inventory, I'd use a two-layer locking strategy. Layer 1: Redis distributed lock — `SET lock:event:{id} {uuid} NX EX 5` — only one booking transaction proceeds at a time per event, absorbing the thundering herd of 500K concurrent users. Layer 2: database pessimistic lock — `SELECT * FROM seats WHERE seat_id = 'A7' AND status = 'AVAILABLE' FOR UPDATE` — the row-level lock is the correctness guarantee. If Redis fails, the DB lock still prevents double-selling. Belt and suspenders."*

---

## Virtual Queue for Flash Sales

### The Problem
```
Flash sale: 1000 items, 500K users click "Buy" simultaneously.
Without queue: 500K requests hit the booking system → crashes.
```

### The Solution
```
Step 1: Users join a queue (Redis sorted set)
  ZADD queue:event:789 {timestamp} {user_id}
  500K users queued in FIFO order

Step 2: Admission worker pops users in batches
  Every 10 seconds: ZPOPMIN queue:event:789 1000
  1000 users admitted → given time-limited booking token

Step 3: Admitted users complete booking
  Token: Redis key with 5-minute TTL
  User has 5 minutes to complete payment
  If token expires → slot released for next user

Step 4: Queue position displayed to waiting users
  ZRANK queue:event:789 {user_id} → "You are #12,345 in queue"
```

### Benefits
```
✅ System processes exactly 1000 concurrent bookings (no overload)
✅ Fair ordering (FIFO — first-come-first-served)
✅ User sees queue position (transparent, reduces frustration)
✅ Expired tokens recirculate to next in queue
✅ No crashes from thundering herd
```

### Interview Script
> *"For flash sales, the virtual queue is essential. Instead of 500K users hammering the booking system simultaneously, I'd use a Redis sorted set: `ZADD queue:{event_id} {timestamp} {user_id}`. Users join the queue in FIFO order. Every 10 seconds, an admission worker pops the next 1000 users. Each admitted user gets a time-limited token granting access to the booking page. The system processes exactly 1000 concurrent bookings at a time — no overload, fair ordering, and the user sees their queue position via `ZRANK`."*

---

## Compare-and-Swap (CAS)

### Pattern
```
Atomic check-and-update without explicit locking:

Redis: WATCH + MULTI + EXEC (optimistic transactions)
  WATCH counter:likes:tweet:123
  current = GET counter:likes:tweet:123
  MULTI
  SET counter:likes:tweet:123 (current + 1)
  EXEC
  # If another client modified the watched key → EXEC returns nil → retry

Database: CAS with WHERE clause
  UPDATE inventory SET quantity = 49 WHERE product_id = 'X' AND quantity = 50;
  # If quantity changed since we read it → 0 rows updated → retry
```

### When to Use
- Counters and atomic increments.
- Lock-free concurrent data structures.
- When explicit locking is too expensive.

---

## Single-Writer Principle

### Concept
Route all updates to a given entity through a single writer (partition, actor, or leader). Eliminates concurrent access entirely.

```
All updates to user_123's balance → single partition for user_123
No concurrent writes → no locks needed → no contention

Implementation:
  Kafka partition key = user_id
  All events for user_123 → same partition → single consumer thread
  Consumer processes events sequentially for each user
  
  Per-user throughput: ~200 transactions/second (5ms each)
  Far more than any human user needs.
```

### Interview Script
> *"I follow the single-writer principle for high-contention data. All updates to a given user's balance go through a single partition — there's no concurrent access to the same balance from multiple writers. This eliminates distributed locking entirely. The tradeoff is serialized writes for that user, but individual write latency is < 5ms. For a user-level operation, serialized processing at 5ms per transaction supports 200 transactions per second per user — orders of magnitude more than any human user needs."*

---

## Database-Level Concurrency

### Isolation Levels
| Level | Dirty Reads | Non-Repeatable | Phantom | Performance |
|---|---|---|---|---|
| **Read Uncommitted** | Yes | Yes | Yes | Fastest |
| **Read Committed** | No | Yes | Yes | Fast (PostgreSQL default) |
| **Repeatable Read** | No | No | Yes | Medium (MySQL default) |
| **Serializable** | No | No | No | Slowest |

### When to Use Serializable
```
Financial transactions: Transfer $500 from A to B.
  Must prevent: Reading balance during transfer.
  Must prevent: Two concurrent transfers from same account.
  
  SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
  BEGIN;
    SELECT balance FROM accounts WHERE id = 'A';  -- $1000
    UPDATE accounts SET balance = balance - 500 WHERE id = 'A';
    UPDATE accounts SET balance = balance + 500 WHERE id = 'B';
  COMMIT;
```

### Advisory Locks (PostgreSQL)
```sql
-- Application-level locking without row locks
SELECT pg_advisory_lock(hashtext('booking:event:456'));
-- ... perform booking logic ...
SELECT pg_advisory_unlock(hashtext('booking:event:456'));

-- Lighter weight than row locks
-- Can lock on arbitrary concepts (not just rows)
```

---

## Choosing Pessimistic vs Optimistic

| Criteria | Pessimistic | Optimistic |
|---|---|---|
| **Conflict rate** | High (many contenders) | Low (rare conflicts) |
| **Throughput** | Lower (serial access) | Higher (no waiting) |
| **Latency** | Higher (wait for lock) | Lower (no waiting) |
| **Correctness** | Guaranteed by lock | Guaranteed by retry |
| **Complexity** | Deadlock handling needed | Retry logic needed |
| **Best for** | Seat booking, inventory | Profile updates, settings |

### Decision Rule
```
Conflict probability > 10%  → Pessimistic (lock upfront)
Conflict probability < 1%   → Optimistic (check at write time)
In between                  → Profile the actual workload
```

---

## Deadlock Prevention

### What Is Deadlock?
```
Thread A: Locks resource 1, waits for resource 2
Thread B: Locks resource 2, waits for resource 1
→ Both wait forever (deadlock)
```

### Prevention Strategies
1. **Lock ordering**: Always acquire locks in the same order (e.g., by resource ID).
2. **Lock timeout**: Acquire lock with timeout; if timeout expires, release all locks and retry.
3. **Single lock**: Lock a higher-level resource instead of multiple fine-grained resources.
4. **No-wait**: If lock is unavailable, immediately fail and retry.

---

## Interview Talking Points & Scripts

### Pessimistic for High Contention
> *"For limited inventory (last 5 seats), pessimistic locking with `SELECT ... FOR UPDATE`. Lock duration is < 50ms. Correctness is guaranteed."*

### Optimistic for Low Contention
> *"For profile updates (< 1% conflict), optimistic locking with version numbers. No locks held, 10x higher throughput. Retry on the rare conflict."*

### Virtual Queue for Flash Sales
> *"Redis sorted set as a virtual queue. Admit 1000 users at a time with time-limited tokens. Fair FIFO ordering, no overload, queue position visible to users."*

---

## Common Interview Mistakes

### ❌ Mistake 1: No concurrency control for shared mutable state
### ❌ Mistake 2: Using pessimistic locking for low-contention scenarios
### ❌ Mistake 3: Not mentioning lock TTL for distributed locks
### ❌ Mistake 4: Ignoring the thundering herd in flash sales
### ❌ Mistake 5: Not considering deadlocks with multiple locks

---

## Concurrency Strategy by System Design Problem

| Problem | Strategy | Why |
|---|---|---|
| **Ticket booking** | Two-layer (Redis + DB pessimistic) | High contention, correctness critical |
| **Flash sale** | Virtual queue + admission control | 500K users, prevent overload |
| **Profile update** | Optimistic locking (version) | Low contention, high throughput |
| **Bank transfer** | DB serializable isolation | ACID required, no anomalies |
| **Like counter** | Redis INCR (atomic) | Lock-free, single atomic op |
| **Inventory decrement** | Pessimistic lock (FOR UPDATE) | Must prevent overselling |
| **Leader election** | Redis SETNX + fencing token | Only one leader at a time |
| **Shopping cart** | Optimistic (per-user, low contention) | Users rarely compete on same cart |
| **Auction bidding** | Pessimistic lock on item | Highest bid must be atomic |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│        CONCURRENCY CONTROL & CONTENTION CHEAT SHEET          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PESSIMISTIC: Lock before read. Use for HIGH contention.     │
│    SELECT ... FOR UPDATE (DB row lock)                       │
│    Redis SETNX (distributed lock)                            │
│    Use: booking, inventory, auction                          │
│                                                              │
│  OPTIMISTIC: Check at write time. Use for LOW contention.    │
│    UPDATE ... WHERE version = N (version check)              │
│    Retry on conflict. No locks held.                         │
│    Use: profile updates, settings, preferences               │
│                                                              │
│  DISTRIBUTED LOCK: Redis SET key value NX EX ttl             │
│    Always set TTL (prevent infinite lock on crash)           │
│    Use unique value (prevent deleting others' locks)         │
│    Lua script for atomic release                             │
│                                                              │
│  VIRTUAL QUEUE: Redis ZADD for flash sales                   │
│    FIFO admission + time-limited tokens                      │
│    Controlled throughput, fair ordering                       │
│                                                              │
│  SINGLE-WRITER: Route all updates for entity to 1 partition  │
│    Eliminates concurrency entirely                           │
│    Use: balance updates, sequential processing               │
│                                                              │
│  DECISION: Conflict > 10% → Pessimistic                     │
│            Conflict < 1%  → Optimistic                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
