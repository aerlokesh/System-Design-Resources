# Dealing with Contention - Complete HLD Guide

## Table of Contents
1. [Introduction](#introduction)
2. [The Problem](#the-problem)
3. [Single-Node Solutions](#single-node-solutions)
4. [Distributed Solutions](#distributed-solutions)
5. [Real-World Architecture Examples](#real-world-architecture-examples)
6. [Optimization Strategies](#optimization-strategies)
7. [Decision Framework](#decision-framework)
8. [Interview Guide](#interview-guide)

---

## Introduction

**Contention** occurs when multiple processes compete for the same resource simultaneously, leading to race conditions, double-bookings, and inconsistent state. Common scenarios:

- **Ticket booking**: Last concert seat, flight booking
- **Inventory management**: Last product in stock
- **Auction bidding**: Highest bid tracking
- **Hotel reservations**: Room double-booking
- **Banking**: Concurrent withdrawals, transfer races
- **Ride-hailing**: Driver assignment conflicts
- **Gaming**: Shared resource acquisition
- **URL shortening**: Collision prevention

**The Challenge**: Ensure consistency while maintaining performance and availability.

---

## The Problem

### Race Condition Example

```
┌──────────────────────────────────────────────────────────────────┐
│              RACE CONDITION: CONCERT TICKET BOOKING               │
└──────────────────────────────────────────────────────────────────┘

Scenario: 1 ticket left, Alice and Bob both want it

TIME    ALICE                           BOB
────────────────────────────────────────────────────────────────
t=0     Click "Buy Now"                Click "Buy Now"

t=1     Read: seats_available = 1      Read: seats_available = 1
        ✓ (sees 1 available)           ✓ (sees 1 available)

t=2     Check: 1 >= 1? YES             Check: 1 >= 1? YES
        Proceed to payment              Proceed to payment

t=3     Payment: $500 charged           Payment: $500 charged
        Write: seats_available = 0      Write: seats_available = -1

t=4     Confirmation: Seat 12           Confirmation: Seat 12
        ✓ Success!                      ✓ Success!

Result: BOTH get same seat! Double-booking disaster!

Problems:
├─ Race condition (read-check-write not atomic)
├─ Oversold tickets (negative inventory)
├─ Angry customers (one gets kicked out)
├─ Refunds required (operational cost)
├─ Reputation damage
└─ Legal issues (fraud, breach of contract)
```

### Why Contention is Hard

```
┌──────────────────────────────────────────────────────────────────┐
│              CONTENTION CHALLENGES                                │
└──────────────────────────────────────────────────────────────────┘

1. PERFORMANCE vs CORRECTNESS
   • Locks ensure correctness
   • But locks reduce throughput
   • Must balance both

2. DEADLOCKS
   Transaction A: Locks resource X, wants Y
   Transaction B: Locks resource Y, wants X
   Result: Both stuck forever

3. DISTRIBUTED SYSTEMS
   • No shared memory
   • Network delays
   • Partial failures
   • Clock skew

4. SCALABILITY
   • More servers = more coordination
   • Central lock = bottleneck
   • Distributed locks = complex

5. HIGH CONTENTION
   • Popular items (iPhone launch)
   • Flash sales (limited quantity)
   • Auctions (last second bidding)
   • Locks become bottleneck
```

---

## Single-Node Solutions

### Solution 1: Database Transactions (ACID)

```
┌──────────────────────────────────────────────────────────────────┐
│              DATABASE TRANSACTION SOLUTION                        │
└──────────────────────────────────────────────────────────────────┘

Use SERIALIZABLE isolation level

Transaction Flow:

BEGIN TRANSACTION;

1. SELECT seats_available FROM tickets 
   WHERE event_id = 123 
   FOR UPDATE;  ← Lock row
   
   Result: 1 seat

2. IF seats_available >= 1:
     UPDATE tickets 
     SET seats_available = seats_available - 1
     WHERE event_id = 123;
     
     INSERT INTO bookings VALUES (...);

COMMIT;  ← Release lock

Concurrent Execution:

Alice's Transaction:
t=0: BEGIN
t=1: SELECT FOR UPDATE (acquires lock)
t=2: Check: 1 >= 1? YES
t=3: UPDATE seats to 0
t=4: COMMIT (releases lock)

Bob's Transaction:
t=1: BEGIN
t=2: SELECT FOR UPDATE (WAITS for Alice's lock)
t=5: Lock acquired (after Alice commits)
t=6: Check: 0 >= 1? NO
t=7: ROLLBACK (no ticket available)

Result: Only Alice gets ticket! ✓ Correct!

Benefits:
✓ Guaranteed consistency
✓ Built into database
✓ No extra infrastructure

Drawbacks:
✗ Performance impact (locks)
✗ Only works within single database
✗ Doesn't scale to distributed systems
✗ Deadlock risk
```

### Solution 2: Optimistic Concurrency Control

```
┌──────────────────────────────────────────────────────────────────┐
│              OPTIMISTIC LOCKING PATTERN                           │
└──────────────────────────────────────────────────────────────────┘

Assume no conflicts, detect and retry if they occur

Table Schema:
tickets(event_id, seats_available, version)

Flow:

1. READ (No lock)
   SELECT seats_available, version 
   FROM tickets WHERE event_id = 123;
   
   Result: seats=1, version=5

2. CHECK & PREPARE UPDATE
   IF seats >= 1:
     new_seats = seats - 1

3. CONDITIONAL UPDATE (Atomic)
   UPDATE tickets
   SET seats_available = new_seats,
       version = version + 1
   WHERE event_id = 123 
     AND version = 5;  ← Only update if version unchanged
   
   Rows affected: 1 (success) or 0 (conflict)

4. IF CONFLICT (rows affected = 0):
   Retry from step 1

Concurrent Execution:

Alice:
t=0: Read: seats=1, version=5
t=1: Prepare: new_seats=0
t=2: Update WHERE version=5 ✓ (success)
t=3: New state: seats=0, version=6

Bob:
t=0: Read: seats=1, version=5 (same as Alice)
t=1: Prepare: new_seats=0
t=2: Update WHERE version=5 ✗ (fails, version now 6)
t=3: Retry: Read seats=0, version=6
t=4: Check fails: 0 >= 1? NO
t=5: Return "Sold out"

Result: Alice gets ticket, Bob told it's sold out ✓

Benefits:
✓ No locks held (better performance)
✓ Higher throughput (no waiting)
✓ Suitable for low contention
✓ Simpler than pessimistic locking

Drawbacks:
✗ Retry overhead if high contention
✗ Wasted work on conflicts
✗ Complex retry logic needed
✗ May starve under very high contention

When to Use:
• Low to medium contention
• Read-heavy workloads
• Performance critical
• Can tolerate retries
```

### Solution 3: Pessimistic Locking

```
┌──────────────────────────────────────────────────────────────────┐
│              PESSIMISTIC LOCKING PATTERN                          │
└──────────────────────────────────────────────────────────────────┘

Lock resource before reading

Flow:

BEGIN TRANSACTION;

1. ACQUIRE LOCK
   SELECT * FROM tickets 
   WHERE event_id = 123 
   FOR UPDATE;  ← Exclusive lock
   
   Other transactions WAIT here

2. READ & UPDATE
   seats = row.seats_available
   IF seats >= 1:
     UPDATE tickets 
     SET seats_available = seats - 1
     WHERE event_id = 123;

3. COMMIT
   Release lock

Benefits:
✓ Prevents conflicts (guaranteed consistency)
✓ No retry logic needed
✓ Clear semantics

Drawbacks:
✗ Lower throughput (serialized access)
✗ Lock contention under load
✗ Deadlock risk
✗ Lock timeout issues

Deadlock Example:

Transaction A:
  Lock ticket A
  Want to lock ticket B

Transaction B:
  Lock ticket B
  Want to lock ticket A

Both wait forever! (deadlock)

Deadlock Prevention:
• Always acquire locks in same order
• Set lock timeout
• Deadlock detection and abort
```

---

## Distributed Solutions

### Solution 1: Distributed Locks (Redis, Zookeeper)

```
┌──────────────────────────────────────────────────────────────────┐
│              DISTRIBUTED LOCK WITH REDIS                          │
└──────────────────────────────────────────────────────────────────┘

Architecture:

Multiple API Servers
   ↓
┌────────────────────────┐
│   Redis (Redlock)      │
│   • Distributed lock   │
│   • 5 Redis instances  │
│   • Quorum-based       │
└────────────────────────┘

Flow:

1. ACQUIRE LOCK
   SET lock:event_123 "alice_request_id" 
   NX    ← Only if not exists
   PX 10000  ← Expires in 10 seconds
   
   Response: OK (lock acquired) or NIL (already locked)

2. IF LOCK ACQUIRED:
   Proceed with booking
   Check seats available
   Update database
   Create booking

3. RELEASE LOCK
   DEL lock:event_123
   (or let it expire after 10 seconds)

Concurrent Execution:

Alice (t=0):
• Acquire lock:event_123 ✓ (success)
• Process booking (8 seconds)
• Release lock

Bob (t=1):
• Acquire lock:event_123 ✗ (fails, Alice has it)
• Wait and retry
• Acquire lock:event_123 ✓ (success at t=9)
• Check seats: sold out
• Release lock

Benefits:
✓ Works across multiple servers
✓ Simple protocol
✓ Fast (in-memory)

Drawbacks:
✗ Single point of failure (Redis)
✗ Clock skew issues
✗ Network partitions
✗ Lock expiration timing tricky

Redlock Algorithm (More Reliable):
• Use 5 independent Redis instances
• Acquire lock on majority (3 of 5)
• If can't get majority, abort
• Release on all instances

When to Use:
• Distributed system (multiple servers)
• Short critical sections (<10 seconds)
• Can tolerate occasional failures
```

### Solution 2: Database Constraints

```
┌──────────────────────────────────────────────────────────────────┐
│              UNIQUE CONSTRAINT PATTERN                            │
└──────────────────────────────────────────────────────────────────┘

Use database constraints to prevent conflicts

Schema:
bookings(
  id PRIMARY KEY,
  user_id,
  event_id,
  seat_number,
  UNIQUE(event_id, seat_number)  ← Prevents duplicates
)

Flow:

Alice and Bob both try to book Seat 12:

Alice:
INSERT INTO bookings VALUES 
  (1, 'alice', 123, 12);
Result: Success ✓

Bob:
INSERT INTO bookings VALUES 
  (2, 'bob', 123, 12);
Result: UNIQUE CONSTRAINT VIOLATION ✗

Application handles:
• Catch constraint violation
• Return "Seat already taken"
• Suggest alternative seats

Benefits:
✓ Simple and reliable
✓ Database enforces correctness
✓ No explicit locking needed
✓ Works across transactions

Drawbacks:
✗ Requires careful schema design
✗ Error handling needed
✗ May expose database errors to users

Use Cases:
• Unique usernames
• Seat booking
• URL shortening
• Resource allocation
```

### Solution 3: Optimistic Concurrency with Versioning

```
┌──────────────────────────────────────────────────────────────────┐
│              VERSION-BASED OPTIMISTIC LOCKING                     │
└──────────────────────────────────────────────────────────────────┘

Add version column to detect concurrent modifications

Table: tickets(event_id, seats_available, version)
Initial: (123, 1, 5)

Alice's Request:
1. Read: seats=1, version=5
2. Calculate: new_seats=0, new_version=6
3. Update:
   UPDATE tickets 
   SET seats_available=0, version=6
   WHERE event_id=123 AND version=5;
   
   Rows affected: 1 ✓ (success)

Bob's Request (slightly after Alice):
1. Read: seats=1, version=5 (before Alice's update)
2. Calculate: new_seats=0, new_version=6
3. Update:
   UPDATE tickets 
   SET seats_available=0, version=6
   WHERE event_id=123 AND version=5;
   
   Rows affected: 0 ✗ (conflict! version is now 6)
   
4. Retry:
   Read: seats=0, version=6
   Check: 0 >= 1? NO
   Return: "Sold out"

Benefits:
✓ No locks (better performance)
✓ Detects conflicts reliably
✓ Scales better than pessimistic

Drawbacks:
✗ Retry logic required
✗ Wasted work on conflicts
✗ Application complexity

When to Use:
• Low to medium contention
• Performance matters
• Can handle retries
```

---

## Real-World Architecture Examples

### Example 1: Ticketmaster - Concert Ticket Sales

```
┌──────────────────────────────────────────────────────────────────┐
│              TICKETMASTER HIGH-CONTENTION ARCHITECTURE            │
└──────────────────────────────────────────────────────────────────┘

Scale: Millions of users, Seconds to sell out popular concerts

Challenge: 100K users trying to buy 20K tickets in 60 seconds

Architecture:

User clicks "Buy"
   ↓
┌────────────────────────┐
│   API Gateway          │
│   • Rate limiting      │
│   • Queue position     │
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Waiting Room         │
│   • Virtual queue      │
│   • Position: 1,523    │
│   • ETA: 2 minutes     │
└────────┬───────────────┘
         │
         ▼ When your turn
┌────────────────────────┐
│   Seat Selection API   │
│   • Show available     │
│   • Reserve with lock  │
│   Time: 200ms          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Redis (Distributed   │
│   Lock)                │
│   • Lock seat          │
│   • TTL: 5 minutes     │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   PostgreSQL (Tickets) │
│   • Optimistic locking │
│   • Version column     │
│   • Seat reservation   │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Payment Processing   │
│   • Stripe/PayPal      │
│   • 5-min timeout      │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Confirm & Release    │
│   • Book seat          │
│   • Release lock       │
│   • Send confirmation  │
└────────────────────────┘

Multi-Layer Contention Management:

Layer 1: WAITING ROOM (Queue)
• 100K users → Virtual queue
• Let through 100 at a time
• Prevents server overload
• Reduces database contention

Layer 2: REDIS LOCK (Seat Hold)
• User selects seat
• Lock in Redis (5 min)
• Prevents others from seeing/selecting
• Auto-release if payment times out

Layer 3: DATABASE VERSION (Final Booking)
• Optimistic locking with version
• Final check before confirmation
• Prevents double-booking
• Retry if conflict

Contention Scenarios:

Scenario 1: Two users select same seat
User A: Acquires Redis lock ✓
User B: Lock attempt fails ✗
Result: User B shown "Seat unavailable"

Scenario 2: User A abandons checkout
User A: Locks seat, starts payment
User A: Closes browser
Redis: Lock expires after 5 minutes
User B: Can now select that seat

Scenario 3: Flash sale (Taylor Swift concert)
• 1M users for 50K tickets
• Waiting room: 20:1 ratio
• Only 50K get through
• Rest told "Sold out" early
• Prevents wasted database load

Performance:
• Seats sold: 20K in 60 seconds
• Peak load: 100K concurrent users
• Success rate: 20% (by design - limited tickets)
• Database load: Protected by queue
• Lock contention: Minimal (seat-level)
```

---

### Example 2: Airbnb - Double Booking Prevention

```
┌──────────────────────────────────────────────────────────────────┐
│              AIRBNB BOOKING CONTENTION ARCHITECTURE               │
└──────────────────────────────────────────────────────────────────┘

Scale: 7M listings, Concurrent booking attempts

Challenge: Prevent double-booking same dates

Architecture:

User books listing
   ↓
┌────────────────────────┐
│   Booking API          │
│   • Validate dates     │
│   • Calculate price    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Redis Distributed    │
│   Lock                 │
│   • Lock: listing_id   │
│   • TTL: 30 seconds    │
│   Time: 5ms            │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   MySQL Transaction    │
│   BEGIN                │
│   1. SELECT dates      │
│   2. Check available   │
│   3. INSERT booking    │
│   4. BLOCK dates       │
│   COMMIT               │
│   Time: 100ms          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Release Lock         │
│   • Redis DEL lock     │
└────────────────────────┘

Calendar Contention Pattern:

Database Schema:
calendar(
  listing_id,
  date,
  status: 'available' | 'booked' | 'blocked',
  booking_id,
  UNIQUE(listing_id, date)
)

Booking Flow:

1. Acquire distributed lock (listing_id)
   Prevents concurrent modifications

2. Check date availability (within transaction)
   SELECT status FROM calendar
   WHERE listing_id = 123
   AND date BETWEEN '2025-02-01' AND '2025-02-05'
   FOR UPDATE;

3. If all dates available:
   INSERT INTO bookings (...);
   UPDATE calendar SET status='booked', booking_id=X
   WHERE listing_id=123 AND date IN (...);

4. Release lock

Conflict Scenarios:

Scenario 1: Exact same dates
User A: Feb 1-5
User B: Feb 1-5
Result: A acquires lock first, B waits, B sees "unavailable"

Scenario 2: Overlapping dates
User A: Feb 1-5
User B: Feb 3-7 (overlaps Feb 3-5)
Result: A books first, B sees partial availability

Scenario 3: Adjacent bookings
User A: Feb 1-5
User B: Feb 6-10 (no overlap)
Result: Both succeed (different date ranges)

Optimization: Lock Granularity

Naive: Lock entire listing
• Only 1 booking at a time per listing
• Low throughput

Better: Lock specific date range
• Multiple non-overlapping bookings concurrent
• Higher throughput
• More complex lock management

Performance:
• Booking creation: 100-150ms
• Lock hold time: <200ms
• Conflict rate: <1%
• Double-booking: 0% (prevented)
```

---

### Example 3: Shopify - Inventory Management

```
┌──────────────────────────────────────────────────────────────────┐
│              SHOPIFY INVENTORY CONTENTION ARCHITECTURE            │
└──────────────────────────────────────────────────────────────────┘

Scale: 2M merchants, Flash sales with high contention

Challenge: Prevent overselling limited inventory

Architecture:

Customer places order
   ↓
┌────────────────────────┐
│   Checkout API         │
│   • Validate cart      │
│   • Calculate total    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Inventory Service    │
│   • Redis lock per SKU │
│   • Atomic decrement   │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   MySQL (Orders)       │
│   • Create order       │
│   • Sharded by shop    │
│   Time: 80ms           │
└────────────────────────┘

Redis Inventory Pattern:

Key: inventory:product_123
Value: {"available": 100, "reserved": 0}

Atomic Operations:

Reserve Item:
WATCH inventory:product_123
available = GET inventory:product_123.available
IF available > 0:
  MULTI
  HINCRBY inventory:product_123 available -1
  HINCRBY inventory:product_123 reserved +1
  EXEC
  Result: Success
ELSE:
  Return "Out of stock"

Confirm Purchase:
HINCRBY inventory:product_123 reserved -1
(Item sold, don't return to available)

Cancel/Timeout:
HINCRBY inventory:product_123 available +1
HINCRBY inventory:product_123 reserved -1
(Return to available pool)

Flash Sale Strategy:

Black Friday: 1000 units, 10K buyers

Approach 1: First-Come-First-Served
• All 10K users compete
• Redis atomic operations
• First 1000 succeed
• Rest see "Sold out"

Approach 2: Lottery System
• 10K users enter drawing
• Select 1000 winners randomly
• Winners get purchase window
• Reduces contention

Approach 3: Pre-Queue
• Users join queue before sale
• Assigned queue position
• Let through in order
• Rate-limited access

Chosen: Approach 3 (best UX)

Overallocation Strategy:

Intentional oversell (5%):
• Sell 1050 units (have 1000)
• Some orders will cancel
• Better than underselling
• If all fulfill: Cancel 50 oldest
• Provide discount/apology

Trade-off:
• Maximize sales
• Small cancellation rate acceptable
• Better revenue vs leaving inventory

Performance:
• Inventory check: <10ms (Redis)
• Order creation: 100-150ms
• Flash sale: 1000 units in 60 seconds
• Oversell rate: <0.1% (with queue)
```

---

### Example 4: StubHub - Live Auction Bidding

```
┌──────────────────────────────────────────────────────────────────┐
│              AUCTION BIDDING CONTENTION ARCHITECTURE              │
└──────────────────────────────────────────────────────────────────┘

Scale: Millions of listings, High-frequency bidding

Challenge: Last-second bid wars (multiple bids in milliseconds)

Architecture:

User places bid
   ↓
┌────────────────────────┐
│   Bidding API          │
│   • Validate amount    │
│   • Check user funds   │
│   Time: 20ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Redis Lock           │
│   • Lock: listing_id   │
│   • TTL: 1 second      │
│   Time: 2ms            │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Redis (Current Bid)  │
│   • WATCH listing_123  │
│   • GET current_bid    │
│   • SET if higher      │
│   • Atomic compare-set │
│   Time: 3ms            │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   PostgreSQL (History) │
│   • Log all bids       │
│   • Async write        │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   WebSocket Broadcast  │
│   • Notify all watchers│
│   • New highest bid    │
└────────────────────────┘

Bid Conflict Resolution:

Scenario: Three bids submitted simultaneously

t=0ms:  Current bid: $100
t=1ms:  Alice bids $105
t=1ms:  Bob bids $105 (same time!)
t=1ms:  Carol bids $110

Processing (serialized by Redis):

Alice (arrives first):
• Lock acquired
• Check: $105 > $100? YES
• SET current_bid = $105
• Lock released
• Result: Success ✓

Bob (arrives second):
• Lock acquired (waits 0.5ms)
• Check: $105 > $105? NO
• Bid rejected
• Lock released
• Result: "Bid too low" ✗

Carol (arrives third):
• Lock acquired (waits 1ms)
• Check: $110 > $105? YES
• SET current_bid = $110
• Lock released
• Result: Success ✓

Final state: Carol is highest bidder at $110

Auction Sniping (Last-Second Bids):

Problem: Everyone bids in last 5 seconds
• 1000 bids in 5 seconds
• Extreme contention
• Redis can handle (100K ops/sec)

Solution: Auto-Extend
• If bid in last 5 seconds → Extend by 5 seconds
• Prevents pure sniping
• Gives others chance to respond

Performance:
• Bid processing: 10-30ms
• Conflict rate: 5-10% (high contention)
• Retry success: 90%
• Lock wait time: <5ms average
```

---

### Example 5: Robinhood - Stock Trading Orders

```
┌──────────────────────────────────────────────────────────────────┐
│              STOCK TRADING CONTENTION ARCHITECTURE                │
└──────────────────────────────────────────────────────────────────┘

Scale: Millions of trades/day, Microsecond precision needed

Challenge: Match buy/sell orders correctly under high load

Architecture:

User places order
   ↓
┌────────────────────────┐
│   Trading API          │
│   • Validate order     │
│   • Check funds        │
│   Time: 30ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Wallet Lock (Redis)  │
│   • Lock user funds    │
│   • Prevent overdraft  │
│   Time: 5ms            │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Order Matching Engine│
│   • In-memory book     │
│   • Redis Sorted Set   │
│   • Price-time priority│
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   PostgreSQL (Orders)  │
│   • Persist order      │
│   • Async replication  │
└────────────────────────┘

Order Book Structure (Redis):

Buy Orders (Sorted Set):
ZADD buy_orders:AAPL
  101.50 "order_1"  ← Highest bid
  101.00 "order_2"
  100.50 "order_3"

Sell Orders (Sorted Set):
ZADD sell_orders:AAPL
  102.00 "order_4"  ← Lowest ask
  102.50 "order_5"
  103.00 "order_6"

Matching Algorithm:

New buy order: $102.50
   ↓
Check lowest sell: $102.00
   ↓
Match! $102.50 >= $102.00
   ↓
Execute trade at $102.00
   ↓
Remove sell order from book
   ↓
Partial fill buy order (if quantity > 1)

Atomic Execution (Lua Script in Redis):
• Read order book
• Match orders
• Update book
• All atomic (no race conditions)

High Contention Handling:

Market Open (9:30 AM):
• 10K orders/second for popular stocks
• All hitting same order book
• Solution: Single-threaded Redis (naturally serialized)
• Redis handles 100K ops/sec easily

Partitioning:
• Separate order book per stock symbol
• AAPL orders don't contend with GOOGL
• Scales linearly

Optimistic Execution:
• Assume orders will match
• Validate after execution
• Rollback if invalid (rare)

Performance:
• Order placement: 50ms
• Order matching: <10ms
• Market open throughput: 10K orders/sec
• Match accuracy: 100%
```

---

### Example 6: OpenTable - Restaurant Reservations

```
┌──────────────────────────────────────────────────────────────────┐
│              RESTAURANT RESERVATION ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 60K+ restaurants, Millions of reservations

Challenge: Time-slot contention (dinner rush 7-8 PM)

Architecture:

User books table
   ↓
┌────────────────────────┐
│   Reservation API      │
│   • Check availability │
│   • Validate party size│
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Distributed Lock     │
│   • restaurant_id +    │
│     time_slot          │
│   • TTL: 10 minutes    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   PostgreSQL           │
│   • Table availability │
│   • Optimistic locking │
│   • Version check      │
│   Time: 100ms          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Release Lock         │
│   • Confirm booking    │
└────────────────────────┘

Table Availability Management:

Schema:
tables(
  restaurant_id,
  table_id,
  time_slot,
  seats,
  status,
  version
)

Concurrent Reservations:

Prime time (7:30 PM):
• 10 users want same slot
• Restaurant has 5 tables (different sizes)
• Solution: Show available tables
• Each table: Separate contention

User A: Books 4-person table
User B: Books 2-person table
User C: Books 4-person table (same as A wants)

Processing:
• A and B don't conflict (different tables)
• A and C conflict (same table)
• First to complete wins

Performance:
• Reservation: 150-200ms
• Conflict rate: 5-10% (prime time)
• Double-booking: 0%
```

---

## Optimization Strategies

```
┌──────────────────────────────────────────────────────────────────┐
│              CONTENTION REDUCTION TECHNIQUES                      │
└──────────────────────────────────────────────────────────────────┘

1. QUEUE/RATE LIMITING
   Reduce simultaneous access
   • Virtual waiting room
   • Let through gradually
   • Prevents thundering herd

2. OPTIMISTIC FIRST, PESSIMISTIC FALLBACK
   Try optimistic (no lock)
   If high conflicts: Switch to pessimistic
   Adaptive based on contention

3. FINE-GRAINED LOCKING
   Lock smaller units
   • Row-level not table-level
   • Seat-level not event-level
   • Reduces contention surface

4. SHARDING
   Partition hot resources
   • Multiple order books
   • Geographic partitioning
   • Reduces contention per shard

5. EVENTUAL CONSISTENCY
   Accept overselling temporarily
   • Reconcile later
   • Refund if needed
   • Better UX (no blocking)

6. RESERVATION SYSTEM
   Two-phase booking
   • Reserve (soft lock, timeout)
   • Confirm (hard lock, permanent)
   • Others can't see reserved items

7. TIMEOUT STRATEGY
   Locks expire automatically
   • Prevent indefinite holds
   • Abandoned carts release
   • TTL-based cleanup

8. RETRY WITH BACKOFF
   Exponential backoff on conflict
   • Prevents busy-waiting
   • Reduces load during contention
   • Better distributed fairness
```

---

## Decision Framework

```
┌──────────────────────────────────────────────────────────────────┐
│              CONTENTION HANDLING DECISION TREE                    │
└──────────────────────────────────────────────────────────────────┘

START: Resource contention expected?
   │
   ▼
What's the contention level?
   │
   ├─> LOW (< 10 concurrent accesses)
   │   └─> Use: Optimistic locking
   │       • Best performance
   │       • Rare conflicts
   │       • Simple retry
   │
   ├─> MEDIUM (10-100 concurrent)
   │   └─> Use: Database transactions
   │       • SELECT FOR UPDATE
   │       • SERIALIZABLE isolation
   │       • Guaranteed correctness
   │
   └─> HIGH (> 100 concurrent)
       └─> Use: Multi-layer approach
           • Virtual queue (waiting room)
           • Distributed locks (Redis)
           • Database constraints
           • Rate limiting

Is it a distributed system?
   │
   ├─> NO (Single database)
   │   └─> Use: Database transactions
   │       • Simple and reliable
   │       • Built-in support
   │
   └─> YES (Multiple servers)
       └─> Use: Distributed locks
           • Redis Redlock
           • ZooKeeper
           • Etcd

How long is the critical section?
   │
   ├─> SHORT (< 100ms)
   │   └─> Pessimistic locking OK
   │       • Lock overhead acceptable
   │
   └─> LONG (> 100ms)
       └─> Optimistic locking better
           • Don't hold locks long
           • Retry on conflict

Can you accept occasional overselling?
   │
   ├─> YES
   │   └─> Use: Eventual consistency
   │       • Accept 1-5% oversell
   │       • Reconcile/refund later
   │       • Better performance
   │
   └─> NO (Zero tolerance)
       └─> Use: Strong consistency
           • Pessimistic locks
           • Database constraints
           • Slower but correct
```

---

## Interview Guide

```
┌──────────────────────────────────────────────────────────────────┐
│              INTERVIEW FRAMEWORK                                  │
└──────────────────────────────────────────────────────────────────┘

Question: "Design a ticket booking system"

STEP 1: IDENTIFY CONTENTION (2 min)
"This is a classic contention problem:
• Multiple users want last ticket
• Need to prevent double-booking
• Race conditions possible"

STEP 2: CHOOSE SOLUTION (5 min)

For single database:
"Use database transactions with SELECT FOR UPDATE
• Guarantees correctness
• Pessimistic locking
• Only one transaction proceeds"

For distributed system:
"Use distributed locks (Redis Redlock)
• Coordinate across servers
• Lock before checking inventory
• Release after booking"

STEP 3: HANDLE HIGH CONTENTION (10 min)

"For popular events (Taylor Swift concert):
• Add virtual waiting room
• Queue 100K users
• Let through 100 at a time
• Prevents server overload
• Better user experience"

STEP 4: DISCUSS TRADE-OFFS (8 min)

Pessimistic Locking:
+ Guaranteed correctness
- Lower throughput
- Serialized access

Optimistic Locking:
+ Better performance
+ Higher throughput
- Retry overhead
- Complex logic

Virtual Queue:
+ Protects backend
+ Clear wait time
- Additional complexity

STEP 5: EDGE CASES (5 min)

Q: "What if user abandons checkout?"
A: "Lock with TTL (5 minutes), auto-release"

Q: "What about deadlocks?"
A: "Acquire locks in consistent order, set timeouts"

Q: "How to handle flash sales?"
A: "Pre-queue, lottery system, or rate limiting"
```

---

## Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                  KEY TAKEAWAYS                                    │
└──────────────────────────────────────────────────────────────────┘

CORE SOLUTIONS:
├─ Database Transactions (single node)
├─ Optimistic Locking (low contention)
├─ Pessimistic Locking (high contention)
├─ Distributed Locks (multi-server)
└─ Unique Constraints (simple prevention)

BEST PRACTICES:
✓ Identify contention points early
✓ Choose locking strategy based on contention level
✓ Use virtual queues for extreme contention
✓ Implement timeouts on all locks
✓ Add retry logic with exponential backoff
✓ Monitor conflict rates
✓ Use fine-grained locking when possible

REAL-WORLD PATTERNS:
• Ticketmaster: Virtual queue + distributed locks
• Airbnb: Redis locks + database transactions
• Shopify: Redis atomic operations + overallocation
• StubHub: Redis locks + atomic compare-and-set
• Robinhood: In-memory matching + Lua scripts

FOR INTERVIEWS:
• Always mention potential contention
• Discuss locking strategy
• Explain trade-offs
• Address failure scenarios
• Consider high-load cases
```

---

**Document Version**: 1.0  
**Created**: January 2025  
**Type**: High-Level Design Guide  
**Examples**: 6 real-world architectures (Ticketmaster, Airbnb, Shopify, StubHub, Robinhood, OpenTable)  
**Focus**: Concurrency control, locking strategies, race condition prevention
