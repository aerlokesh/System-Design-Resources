# 🎯 Topic 55: Virtual Queue for Flash Sales

> **System Design Interview — Deep Dive**
> A comprehensive guide covering virtual queue (waiting room) architecture for flash sales and high-demand events, Redis sorted set queues, admission control, token-based access gating, queue position tracking, fairness guarantees, anti-bot defenses, inventory reservation pipelines, graceful degradation, real-world patterns (Ticketmaster, Shopify, AWS Queue-it), and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Virtual Queues Exist](#why-virtual-queues-exist)
3. [The Thundering Herd Problem](#the-thundering-herd-problem)
4. [Virtual Queue Architecture — End to End](#virtual-queue-architecture--end-to-end)
5. [Queue Data Structures in Redis](#queue-data-structures-in-redis)
6. [Admission Control — The Gate Keeper](#admission-control--the-gate-keeper)
7. [Token-Based Access Gating](#token-based-access-gating)
8. [Queue Position & Wait Time Estimation](#queue-position--wait-time-estimation)
9. [Inventory Reservation Pipeline](#inventory-reservation-pipeline)
10. [Fairness & Ordering Guarantees](#fairness--ordering-guarantees)
11. [Anti-Bot & Fraud Prevention](#anti-bot--fraud-prevention)
12. [Scalability — Millions in the Queue](#scalability--millions-in-the-queue)
13. [Failover and Graceful Degradation](#failover-and-graceful-degradation)
14. [User Experience During the Wait](#user-experience-during-the-wait)
15. [Randomized vs FIFO Queue Entry](#randomized-vs-fifo-queue-entry)
16. [Multi-Event & Multi-Tier Queues](#multi-event--multi-tier-queues)
17. [Observability & Monitoring](#observability--monitoring)
18. [Real-World Production Patterns](#real-world-production-patterns)
19. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
20. [Common Interview Mistakes](#common-interview-mistakes)
21. [Virtual Queue by System Design Problem](#virtual-queue-by-system-design-problem)
22. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **virtual queue** (also called a **waiting room**) is an admission-control layer placed in front of a high-demand resource. Instead of letting all users access the resource simultaneously — crashing the system — users are placed in an ordered queue and admitted in controlled batches.

```
Without virtual queue (flash sale: 1,000 items, 500K users):
  T=0: 500K users click "Buy Now" simultaneously
  T=0.1s: 500K requests hit booking service
  T=0.5s: Database overwhelmed → 503 errors, timeouts, cascading failure
  T=2s: Service crashes → 0 items sold, 500K angry users

With virtual queue:
  T=0: 500K users click "Buy Now" → placed in queue
  T=0.1s: User sees "You are #42,315 in line. Estimated wait: 8 minutes."
  T=0-60s: Admission worker admits 1,000 users at a time
  T=1-10min: Each batch completes checkout. No overload.
  Result: 1,000 items sold, 0 crashes, fair ordering, happy users.
```

### The Key Insight

```
A virtual queue decouples DEMAND (how many users want to buy)
from SUPPLY (how many the system can serve concurrently).

  Demand:  500,000 users → arrives in seconds (spike)
  Supply:  1,000 concurrent checkouts → backend limit
  
  Without queue: Demand overwhelms supply → crash
  With queue:    Demand is buffered → supply processes at its own pace
  
  It's a BACKPRESSURE mechanism for user-facing traffic.
```

---

## Why Virtual Queues Exist

| Problem | Without Queue | With Queue |
|---|---|---|
| **Server overload** | 500K concurrent connections → crash | 500K queued, 1K processed at a time |
| **Database contention** | 500K concurrent `SELECT FOR UPDATE` → deadlocks | 1K concurrent queries → manageable |
| **Unfair access** | Fastest clickers / bots win | FIFO or randomized → fair |
| **User experience** | 503 errors, spinning wheels, rage | "You're #42K, ~8 min wait" → transparent |
| **Payment failures** | Timeout cascades during payment | Controlled flow → payment service is healthy |
| **Inventory integrity** | Overselling from race conditions | Controlled admission → exact inventory match |
| **CDN / Edge load** | Static pages can handle traffic but backends can't | Queue absorbs dynamic traffic load |

### When to Use a Virtual Queue

```
USE when:
  ✅ Demand can spike 100-1000x normal load (flash sale, ticket drop, product launch)
  ✅ Inventory is limited (100-100K items)
  ✅ Fairness matters to users ("I was here first!")
  ✅ Backend has a hard concurrency limit (database connections, payment provider TPS)
  ✅ The event is time-bounded (starts at a specific time)

DON'T USE when:
  ❌ Traffic is steady / predictable (just scale the backend)
  ❌ Inventory is unlimited (e-books, SaaS signups)
  ❌ Latency is critical and queuing is unacceptable (real-time trading)
  ❌ You can pre-shard demand effectively (per-region inventory pools)
```

---

## The Thundering Herd Problem

### Anatomy of a Flash Sale Crash

```
Timeline of a flash sale without a virtual queue:

  T=-5min:  Marketing email sent to 2M subscribers: "Sale starts at 12:00 PM!"
  T=-30sec: 500K users have the page loaded, fingers on "Buy" button
  T=0:      Clock hits 12:00:00 — 500K simultaneous clicks
  
  Load Balancer:
    500K connections/sec → exceeds max connections (10K) → drops requests
    
  API Servers (50 instances):
    Each receives 10K requests → thread pools exhausted → queueing at OS level
    
  Database:
    50 × 10K = 500K queries → connection pool exhausted (max 200 connections)
    500K SELECT FOR UPDATE on the same inventory rows → deadlock storm
    
  Payment Service:
    Stripe rate limit: 100 charges/sec → 499,900 requests time out
    
  Result: Cascading failure across all layers.

  ┌─────────┐    500K     ┌─────────┐    500K    ┌──────────┐
  │  Users   │───────────→│   LB    │──────────→ │ API (50) │
  └─────────┘             └─────────┘            └────┬─────┘
                                                      │ 500K
                                                 ┌────▼─────┐
                                                 │ Database  │ ← OVERWHELMED
                                                 └────┬─────┘
                                                      │ 500K
                                                 ┌────▼─────┐
                                                 │ Payments  │ ← RATE LIMITED
                                                 └──────────┘
```

### How the Virtual Queue Fixes This

```
  ┌─────────┐    500K     ┌────────────────┐   1K/batch   ┌─────────┐
  │  Users   │───────────→│  Virtual Queue  │────────────→ │ API (50)│
  └─────────┘             │  (Redis ZSET)   │              └────┬────┘
                          │                 │                   │ 1K
                          │  Admission:     │              ┌────▼────┐
                          │  1000 users     │              │Database │ ← HEALTHY
                          │  every 10 sec   │              └────┬────┘
                          └────────────────┘                    │ 1K
                                                          ┌────▼────┐
                                                          │Payments │ ← HEALTHY
                                                          └─────────┘

  The queue ABSORBS the spike. The backend processes at its own pace.
  The queue itself is Redis — trivially handles 500K writes in < 1 second.
```

---

## Virtual Queue Architecture — End to End

### Full System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          CDN / Edge Layer                             │
│  ┌─────────────┐                                                     │
│  │ Static Queue │  ← HTML/JS waiting room page served from CDN       │
│  │ Page (CDN)   │    (handles 500K connections with no backend load)  │
│  └──────┬──────┘                                                     │
│         │ Polls queue position every 5 seconds                       │
└─────────┼────────────────────────────────────────────────────────────┘
          │
┌─────────▼────────────────────────────────────────────────────────────┐
│                       Queue Service Layer                             │
│                                                                      │
│  ┌───────────────┐    ┌─────────────────┐    ┌────────────────────┐  │
│  │ Queue API      │    │ Admission Worker │    │ Redis Cluster      │  │
│  │ (Stateless)    │    │ (Scheduled)      │    │                    │  │
│  │                │    │                  │    │ ZSET: queue:{eid}  │  │
│  │ POST /enqueue  │    │ Every N seconds: │    │ HASH: token:{tid}  │  │
│  │ GET  /position │    │ Pop M users →    │    │ STRING: inventory  │  │
│  │ GET  /status   │    │ Issue tokens     │    │   :{eid}           │  │
│  └───────────────┘    └────────┬─────────┘    └────────────────────┘  │
│                                │                                      │
└────────────────────────────────┼──────────────────────────────────────┘
                                 │ Admitted users get access token
┌────────────────────────────────▼──────────────────────────────────────┐
│                      Booking Service Layer                             │
│                                                                       │
│  ┌──────────────┐    ┌─────────────┐    ┌──────────────┐              │
│  │ Booking API   │    │  Inventory   │    │  Payment     │              │
│  │               │───→│  Service     │───→│  Service     │              │
│  │ Validates     │    │  (Reserve)   │    │  (Charge)    │              │
│  │ access token  │    │              │    │              │              │
│  └──────────────┘    └─────────────┘    └──────────────┘              │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```

### Step-by-Step Flow

```
1. USER ARRIVES
   User clicks "Buy Now" → hits Queue API
   Queue API adds user to Redis sorted set:
     ZADD queue:event:789 1710500000.123 user:42
     (score = arrival timestamp, value = user ID)
   Returns: { queue_position: 42315, estimated_wait: "8 min" }

2. USER WAITS
   Browser loads static waiting room page from CDN.
   JavaScript polls GET /position every 5 seconds:
     → ZRANK queue:event:789 user:42
     → Returns: { position: 38210, estimated_wait: "6 min" }
   Progress bar updates. User sees their position dropping.

3. ADMISSION WORKER RUNS
   Every 10 seconds, the admission worker:
     a. Checks available capacity:
        remaining_inventory = GET inventory:event:789
        active_tokens = SCARD active_tokens:event:789
        available_slots = min(batch_size, remaining_inventory - active_tokens)
     
     b. Pops next batch from queue:
        users = ZPOPMIN queue:event:789 {available_slots}
        (Removes and returns the oldest N users)
     
     c. Issues access tokens:
        For each admitted user:
          token = generate_secure_token()
          SET token:{token} {user_id} EX 300  (5-minute TTL)
          SADD active_tokens:event:789 {token}
          Notify user via WebSocket or polling: "You're in! Complete checkout."

4. USER SHOPS
   Admitted user is redirected to booking page.
   Every request includes the access token.
   Booking API validates: EXISTS token:{token}
   If valid → process request.
   If expired/invalid → redirect back to queue (or "sale ended").

5. USER COMPLETES CHECKOUT
   Inventory Service: DECR inventory:event:789
   Payment Service: charge card
   Token cleanup: DEL token:{token}, SREM active_tokens:event:789
   → Slot freed for next user in queue.

6. TIMEOUT / ABANDONMENT
   If user doesn't checkout within 5 minutes:
     Token expires (Redis TTL).
     Background worker: scan active_tokens, remove expired ones.
     Admission worker sees freed capacity → admits next batch.
```

---

## Queue Data Structures in Redis

### Primary Queue — Sorted Set (ZSET)

```
Key: queue:event:{event_id}
Type: Sorted Set
Score: Arrival timestamp (Unix epoch with milliseconds)
Member: User identifier (user_id or session_id)

  ZADD queue:event:789 1710500000.123 "user:42"      → User 42 joined at T=0.123
  ZADD queue:event:789 1710500000.456 "user:99"      → User 99 joined at T=0.456
  ZADD queue:event:789 1710500000.789 "user:7"       → User 7 joined at T=0.789

Why Sorted Set?
  ✅ ZADD: O(log N) insertion — fast even with 1M members
  ✅ ZRANK: O(log N) position lookup — "You are #42,315"
  ✅ ZPOPMIN: O(log N) atomic pop — remove oldest N users
  ✅ ZCARD: O(1) total queue size — "500K users in queue"
  ✅ Natural FIFO ordering by timestamp score
  ✅ Deduplication: Re-ZADD same member just updates score (no duplicates)
  ✅ Range queries: "How many users joined in the last 10 seconds?"
```

### Access Tokens — String Keys with TTL

```
Key: token:{token_value}
Type: String
Value: user_id or JSON { user_id, event_id, admitted_at }
TTL: 300 seconds (5 minutes)

  SET token:abc123xyz "user:42" EX 300

Why String + TTL?
  ✅ O(1) existence check: EXISTS token:abc123xyz
  ✅ Auto-expiry: Redis cleans up abandoned tokens
  ✅ No background cleanup needed for expired tokens
```

### Active Token Set — Track Current Admitted Users

```
Key: active_tokens:event:{event_id}
Type: Set
Members: Token values of currently admitted users

  SADD active_tokens:event:789 "abc123xyz"
  SCARD active_tokens:event:789  → 847 (users currently shopping)

Why Track Active Tokens?
  To calculate available capacity:
    capacity = min(batch_size, remaining_inventory - active_count)
  Without this, we might over-admit users beyond inventory.
```

### Inventory Counter

```
Key: inventory:event:{event_id}
Type: String (integer)
Value: Remaining items available for sale

  SET inventory:event:789 1000        → 1000 items for sale
  DECR inventory:event:789            → 999 after one purchase
  GET inventory:event:789             → check remaining

Why Redis (not just DB)?
  ✅ Sub-ms reads: The admission worker reads this on every cycle.
  ✅ Atomic DECR: No race conditions on decrement.
  ✅ The DB is the source of truth — Redis is a fast approximation.
  ✅ Periodic sync: DB → Redis reconciliation every 30 seconds.
```

### Memory Sizing

```
Sorted Set members:
  Each member: ~50 bytes (user ID) + 8 bytes (score) + overhead ≈ 100 bytes
  500K users in queue: 500K × 100 bytes = 50 MB
  
Active tokens:
  Each token: ~40 bytes + overhead ≈ 80 bytes
  1000 active tokens: 80 KB (negligible)

Token strings:
  Each key: ~30 bytes key + ~20 bytes value + overhead ≈ 100 bytes
  1000 tokens: 100 KB (negligible)

Total for one event with 500K users: ~50 MB
Total for 100 concurrent events: ~5 GB
A single 16 GB Redis instance handles this easily.
```

---

## Admission Control — The Gate Keeper

### Batch Admission Algorithm

```python
class AdmissionWorker:
    """Controls how many users are admitted from the queue."""
    
    def __init__(self, redis, event_id, config):
        self.redis = redis
        self.event_id = event_id
        self.batch_size = config.batch_size          # e.g., 1000
        self.admission_interval = config.interval     # e.g., 10 seconds
        self.token_ttl = config.token_ttl             # e.g., 300 seconds
    
    def run_admission_cycle(self):
        """Called every admission_interval seconds."""
        
        # Step 1: Calculate available capacity
        remaining_inventory = int(self.redis.get(f"inventory:event:{self.event_id}") or 0)
        active_count = self.redis.scard(f"active_tokens:event:{self.event_id}")
        
        available = min(
            self.batch_size,
            remaining_inventory - active_count  # Don't admit more than inventory
        )
        
        if available <= 0:
            return  # No capacity — don't admit anyone
        
        # Step 2: Pop oldest users from queue
        users = self.redis.zpopmin(f"queue:event:{self.event_id}", available)
        # users = [(user_id, score), ...] sorted by arrival time (oldest first)
        
        # Step 3: Issue access tokens
        pipe = self.redis.pipeline()
        for user_id, _ in users:
            token = generate_secure_token()
            pipe.set(f"token:{token}", user_id, ex=self.token_ttl)
            pipe.sadd(f"active_tokens:event:{self.event_id}", token)
            # Store token for the user (so we can notify them)
            pipe.set(f"user_token:{user_id}:{self.event_id}", token, ex=self.token_ttl)
        pipe.execute()
        
        # Step 4: Notify admitted users (via WebSocket, push, or polling flag)
        for user_id, _ in users:
            notify_user_admitted(user_id, self.event_id)
```

### Adaptive Batch Sizing

```
Static batch size (simple):
  Always admit 1000 users per cycle.
  Problem: If checkout takes 2 minutes but tokens expire in 5 minutes,
           admitted users pile up → effective concurrency = 5 × 1000 = 5000.

Adaptive batch size (production):
  Monitor actual checkout completion rate.
  
  If average checkout time = 2 minutes, token TTL = 5 minutes:
    Max active users = batch_size × (token_ttl / admission_interval)
    To keep max active at 1000:
      batch_size = 1000 / (300/10) = 33 users per cycle
    
  Or: Track active_count in real time and fill up to target:
    target_active = 1000
    current_active = SCARD active_tokens:event:789
    batch_size = target_active - current_active
    
  This FEEDBACK LOOP is what production systems use.

  ┌──────────────────────────┐
  │                          │
  │    Target: 1000 active   │
  │           │              │
  │    ┌──────▼──────┐       │
  │    │ current=847  │       │
  │    │ admit=153    │──────→│ Pop 153 from queue
  │    └─────────────┘       │
  │           ↑              │
  │    Users complete        │
  │    checkout / expire     │
  │           │              │
  └───────────┘              │
              Feedback loop  │
  ┌──────────────────────────┘
```

### Admission Pacing — Smooth vs Burst

```
Burst admission:
  Every 10 seconds: admit 1000 users at once.
  Downstream sees 1000 simultaneous requests → mini thundering herd.

Smooth admission (staggered):
  Every 10 seconds: admit 1000 users, but stagger their "go" signal:
    Users 1-100:     "You're in!" at T=0
    Users 101-200:   "You're in!" at T=1s
    Users 201-300:   "You're in!" at T=2s
    ...
    Users 901-1000:  "You're in!" at T=9s
    
  Result: Downstream sees ~100 new requests/second (smooth).
  
  Implementation: Token becomes active at different times:
    SET token:{token} {user_id} EX 300  ← available immediately
    But the notification is staggered using a delay queue or scheduled push.
```

---

## Token-Based Access Gating

### How Access Tokens Work

```
A token is a short-lived credential that proves a user was admitted from the queue.

Properties:
  - Cryptographically random (128-bit → URL-safe base64 → 22 chars)
  - Single-use or time-limited (5-minute TTL in Redis)
  - Bound to a specific user + event
  - Validated on every request to the booking service

Token format (opaque):
  "QjF2dXhLMzR5cFpSN3dF"  → looks up in Redis: token:{value} → user_id

Token format (self-contained JWT, optional):
  {
    "sub": "user:42",
    "event": "event:789",
    "exp": 1710500300,       // expires in 5 minutes
    "iat": 1710500000,
    "jti": "abc123xyz"       // unique token ID
  }
  Signed with HMAC-SHA256. Can be validated without Redis lookup.
  But still check Redis for revocation (user completed checkout or token revoked).
```

### Token Validation Middleware

```python
class TokenGate:
    """Middleware that validates admission tokens on booking requests."""
    
    def __init__(self, redis):
        self.redis = redis
    
    def validate(self, request):
        token = request.headers.get('X-Queue-Token') or request.cookies.get('queue_token')
        
        if not token:
            return redirect_to_queue(request.event_id, reason="no_token")
        
        # Check Redis for token existence
        user_id = self.redis.get(f"token:{token}")
        
        if not user_id:
            return redirect_to_queue(request.event_id, reason="token_expired")
        
        # Verify token belongs to this user
        if user_id.decode() != request.user_id:
            return Response(status=403, body={"error": "token_mismatch"})
        
        # Extend TTL on activity (sliding expiration)
        self.redis.expire(f"token:{token}", 300)  # Reset to 5 minutes
        
        return ALLOW  # Proceed to booking service
```

### Token Lifecycle

```
┌──────────┐    ┌───────────┐    ┌──────────────┐    ┌─────────────┐
│  Issued   │───→│  Active   │───→│  Checkout    │───→│  Completed  │
│ (ZPOPMIN) │    │ (browsing)│    │  (payment)   │    │  (purchase) │
└──────────┘    └─────┬─────┘    └──────┬───────┘    └──────┬──────┘
                      │                 │                    │
                      │ Timeout         │ Payment fail       │ Success
                      ▼                 ▼                    ▼
                ┌──────────┐    ┌──────────────┐    ┌──────────────┐
                │  Expired  │    │  Revoked     │    │  Consumed    │
                │ (TTL=0)   │    │ (explicit)   │    │ (DEL token)  │
                └──────────┘    └──────────────┘    └──────────────┘
                      │                 │
                      ▼                 ▼
                Slot freed → Admission worker admits next user
```

### Token Security Considerations

```
1. Token entropy: 128-bit minimum → 2^128 possible values
   → Infeasible to brute-force (would take 10^24 years at 1B guesses/sec)

2. Token binding: Token is bound to user_id + event_id
   → User A can't use User B's token
   → Token can't be used for a different event

3. One-token-per-user: ZADD is idempotent on the same member
   → If user re-joins queue, their position updates (no duplicate entries)
   → Only one active token per user per event

4. HTTPS only: Token transmitted over TLS
   → No interception in transit

5. No token in URL: Use cookie or Authorization header
   → Prevents token leakage in browser history, referrer headers, logs
```

---

## Queue Position & Wait Time Estimation

### Position Lookup

```
ZRANK queue:event:789 "user:42"
→ Returns: 42314 (0-based index)
→ Display: "You are #42,315 in line"

Complexity: O(log N) — fast even with 1M users in queue.
```

### Wait Time Estimation

```
Method 1: Simple estimate
  wait_time = (position / admission_rate) seconds
  
  Example:
    Position: 42,315
    Admission rate: 1,000 users per 10 seconds = 100 users/sec
    Wait time: 42,315 / 100 = 423 seconds ≈ 7 minutes
    
  Display: "Estimated wait: ~7 minutes"

Method 2: Dynamic estimate (accounts for dropoff)
  Not everyone completes checkout. Some abandon.
  Effective throughput = admission_rate + abandonment_refill_rate
  
  Example:
    Admission: 100/sec
    Abandonment refill: 20/sec (20% of admitted users don't checkout)
    Effective: 120/sec
    Wait: 42,315 / 120 = 353 seconds ≈ 6 minutes

Method 3: Moving average
  Track the actual dequeue rate over the last 60 seconds.
  actual_rate = users_admitted_last_60s / 60
  wait_time = position / actual_rate
  
  Most accurate — adapts to real conditions.
```

### Queue Size Broadcast

```
Instead of 500K users all polling ZRANK every 5 seconds:
  500K × 1 poll / 5 seconds = 100K Redis calls/sec (expensive!)

Optimization: Broadcast queue metadata, individual position via ZRANK.

  Server publishes every 5 seconds:
    queue_size: 487,231
    admission_rate: 100/sec
    estimated_total_wait: 81 minutes
    
  Client calculates locally:
    "I joined 3 minutes ago. Queue was 500K, now 487K."
    "My position ≈ 487K - (time_since_join × admission_rate)"
    
  Individual ZRANK call only on:
    - Initial join (to get exact position)
    - When nearing the front (position < 5000)
    - Every 30 seconds (not every 5 seconds)
    
  Reduces Redis load from 100K/sec to ~10K/sec.
```

---

## Inventory Reservation Pipeline

### The Two-Phase Approach

```
Phase 1: Queue-level inventory check (fast, approximate)
  Redis: DECR inventory:event:789
  If result >= 0 → user is eligible, proceed to booking
  If result < 0  → INCR inventory:event:789 (undo), show "Sold Out"
  
  This is a FAST CHECK using Redis atomic DECR.
  Not the final truth — just prevents obviously-futile attempts.

Phase 2: Database-level reservation (slow, correct)
  BEGIN;
  SELECT * FROM inventory WHERE event_id = 789 AND status = 'AVAILABLE' 
    LIMIT 1 FOR UPDATE;
  UPDATE inventory SET status = 'RESERVED', user_id = 42, 
    reserved_until = NOW() + INTERVAL '5 minutes'
    WHERE item_id = {selected_item};
  COMMIT;
  
  This is the SOURCE OF TRUTH. Pessimistic lock prevents double-selling.
```

### Handling Inventory Exhaustion Mid-Queue

```
Scenario: 1000 items, 50K users in queue. After 1000 purchases, items are sold out.
  The remaining 49K users are still in the queue waiting.

Option 1: Notify immediately
  When inventory hits 0:
    - Stop admission worker
    - Broadcast to all queued users: "Event is sold out. Thank you."
    - Clear the queue: DEL queue:event:789
    
  Fast, but disappointing.

Option 2: Keep a waitlist
  When inventory hits 0:
    - Admission worker continues but marks users as "waitlisted"
    - If a purchase fails or a token expires → inventory returns → admit waitlist user
    - "You're on the waitlist. We'll notify you if spots open up."
    
  Better UX — users have hope.

Option 3: Gradual notification
  Estimate if the user has a realistic chance:
    If position > remaining_inventory × 1.5:
      "This event is likely sold out by the time you reach the front."
      Offer: "Stay in queue" or "Leave queue"
    
  Transparent and respectful of user's time.
```

---

## Fairness & Ordering Guarantees

### FIFO (First-Come-First-Served)

```
Default approach: Users served in arrival order.
  ZADD queue:event:789 {timestamp_with_ms_precision} {user_id}
  ZPOPMIN → always removes the earliest arrival

Pros:
  ✅ Intuitive fairness — "I got here first, I should be served first"
  ✅ Simple to implement
  ✅ Incentivizes early arrival (marketing benefit)

Cons:
  ❌ Favors users with faster internet connections
  ❌ Bots can arrive microseconds after the sale opens (unfair advantage)
  ❌ Clock precision issues — two users at "same" millisecond
```

### Randomized (Lottery) Entry

```
Alternative: Randomize queue order within a window.

  Step 1: Open "registration window" for 2 minutes before the sale.
    All users who arrive between 11:58-12:00 are registered.
    
  Step 2: At 12:00, randomly shuffle all registered users.
    Random score: ZADD queue:event:789 {random_float} {user_id}
    
  Step 3: Process in random order.
    "You have been randomly assigned position #42,315"

Pros:
  ✅ Eliminates bot advantage (arriving first doesn't help)
  ✅ Reduces "stampede" behavior (no need to click at T=0)
  ✅ More equitable — fast internet doesn't help

Cons:
  ❌ Less intuitive ("I was here first but got position #80K?")
  ❌ Requires a defined registration window
  ❌ Users who arrive DURING the sale (not before) still need FIFO

Used by: Ticketmaster (for some events), PS5 launch queues, Nike SNKRS app
```

### Hybrid: Registration Window + FIFO

```
Phase 1 (Registration, T-5min to T=0):
  All users who join get a RANDOM position.
  "Your spot has been randomly assigned."

Phase 2 (Post-launch, T=0+):
  Any NEW users who join after T=0 are appended AFTER all Phase 1 users.
  FIFO within Phase 2.

  Score design:
    Phase 1: ZADD queue:event:789 {random(0, 0.999)} {user_id}  
    Phase 2: ZADD queue:event:789 {1.0 + timestamp} {user_id}
    
    Phase 1 scores: 0.000 to 0.999 (random order, all before Phase 2)
    Phase 2 scores: 1.0+ (FIFO order, all after Phase 1)
    
  ZPOPMIN always processes Phase 1 users first, then Phase 2 in arrival order.
```

---

## Anti-Bot & Fraud Prevention

### The Bot Problem

```
Bots can:
  1. Join queue faster than humans (sub-millisecond requests)
  2. Create thousands of accounts to get multiple positions
  3. Solve CAPTCHAs using CAPTCHA farms
  4. Use residential proxies to bypass IP rate limiting
  5. Complete checkout faster than humans (automated card entry)
  
Result: Bots buy all inventory, resell at 10x markup.
  Real fans get nothing.
```

### Defense Layers

```
Layer 1: Pre-Queue CAPTCHA
  Before joining the queue, user must solve a CAPTCHA.
  Stops simple scripts. Doesn't stop CAPTCHA farms.
  Implementation: reCAPTCHA v3 (invisible, risk score)
    Score < 0.3 → block
    Score 0.3-0.7 → CAPTCHA challenge
    Score > 0.7 → allow

Layer 2: Device Fingerprinting
  Collect browser fingerprint (screen size, fonts, WebGL, etc.).
  Flag multiple queue entries from same fingerprint.
  One queue entry per fingerprint.

Layer 3: Account Age & Verification
  Require accounts created > 7 days ago.
  Require email verification.
  Require phone number (one number = one account).
  Verified accounts get priority in the queue.

Layer 4: Queue Entry Rate Limiting
  ZADD is idempotent for same user → no duplicate entries.
  Rate limit the /enqueue endpoint: 1 request per 10 seconds per IP.
  IP-based clustering: If 100 accounts join from the same IP → flag all.

Layer 5: Behavioral Analysis
  Monitor browsing patterns before the sale:
    - Did the user visit the event page before? (human behavior)
    - Did the user browse other events? (human behavior)
    - Did they come from a direct URL with no referrer? (bot signal)
  
  Score users and prioritize humans in the queue.

Layer 6: Checkout Velocity Check
  Normal human checkout: 30-120 seconds (fills in payment, reviews)
  Bot checkout: 2-5 seconds (automated)
  Flag and delay suspiciously fast checkouts.
```

### Queue Poisoning Protection

```
Attack: Adversary adds millions of fake users to the queue to push real users back.

Defense:
  1. Authenticated queue only — require login before ZADD.
  2. One entry per verified account — ZADD is idempotent on member.
  3. Account creation rate limit — can't create 10K accounts in an hour.
  4. Proof of Work — require a small computational challenge to join queue.
     (Like Hashcash: find a nonce where SHA256(nonce + user_id) starts with "000")
     Takes human's browser ~1 second, makes mass account creation expensive.
```

---

## Scalability — Millions in the Queue

### Scaling the Queue Service

```
500K users in queue (single event) — easily fits on one Redis instance.
5M users across 100 events — still manageable (5 GB total).
50M users across 1000 events — need to consider sharding.

Redis Sorted Set performance:
  ZADD: O(log N)  → 1M entries: ~20 operations/ms
  ZRANK: O(log N) → 1M entries: ~20 operations/ms
  ZPOPMIN: O(log N × count) → Pop 1000 from 1M: ~0.5ms
  
  Single Redis instance: ~150K queue operations/sec
  Sufficient for most flash sales.
```

### Horizontal Scaling Strategies

```
Strategy 1: Shard by Event
  Each event's queue lives on a different Redis shard.
  Key: queue:event:{event_id} → hash routes to specific shard.
  
  Event A → Redis Shard 1
  Event B → Redis Shard 2
  Event C → Redis Shard 3
  
  Perfect if events don't overlap. Each shard handles 150K ops/sec.

Strategy 2: Redis Cluster
  Auto-shards across multiple nodes.
  CRC16(queue:event:789) mod 16384 → routes to appropriate shard.
  Scales linearly with number of shards.

Strategy 3: Dedicated Queue per Event
  For mega-events (Taylor Swift: 5M users):
  - Dedicated Redis instance(s) for that event.
  - Separate admission workers.
  - Independent scaling.
```

### Scaling the Queue API (Stateless)

```
Queue API servers are stateless — they just read/write Redis.
Scale horizontally behind a load balancer.

  10 Queue API servers × 10K requests/sec each = 100K requests/sec
  With connection pooling to Redis, each server maintains 50 connections.
  Total: 500 Redis connections → well within limits.
```

### Scaling Queue Position Polling

```
Biggest scale challenge: 500K users polling every 5 seconds.
  = 100K requests/sec to the Queue API.
  
Solutions:
  1. CDN-cached queue metadata
     Publish queue stats (size, rate) every 5 seconds.
     Cache at CDN edge → 0 backend load for most polls.
     Individual ZRANK calls only near the front.
  
  2. WebSocket push (instead of polling)
     When position changes significantly, push to client.
     Reduces request volume by 10-100x.
     Tradeoff: 500K WebSocket connections → need sticky sessions or pub/sub.
  
  3. Long polling with coalesced responses
     Client opens long poll → server waits up to 10 seconds.
     If position changes → respond immediately.
     If not → respond after 10 seconds with unchanged position.
     Reduces connection churn.
  
  4. Server-Sent Events (SSE)
     One-directional push from server to client.
     Lighter than WebSocket (no framing overhead).
     Each client holds one HTTP connection.
     Server pushes position updates every 5-10 seconds.
```

---

## Failover and Graceful Degradation

### Redis Queue Failure

```
Scenario: Redis instance holding the queue goes down.

Impact:
  - Can't add new users to queue (ZADD fails)
  - Can't check position (ZRANK fails)
  - Can't admit users (ZPOPMIN fails)
  - Already-admitted users with valid tokens can still checkout
    (tokens are validated, but if token Redis also down → problem)

Recovery Strategy:

  Tier 1: Redis Sentinel / Cluster failover (automatic, 1-5 seconds)
    Data since last replication may be lost.
    Some users may lose their queue position.
    Acceptable: Users can re-join. Position might shift slightly.

  Tier 2: Fallback to "first-come-first-served without queue"
    If Redis is fully down:
      - Disable the queue entirely
      - Let the first N users through directly (rate-limited by the booking service)
      - Show "High demand — please try again" to excess users
      - Less fair, but service continues

  Tier 3: Static waiting page (CDN)
    If everything is down:
      - CDN serves static "Please wait" page
      - No dynamic queue position
      - "We're experiencing high demand. Please try again in a few minutes."
      - At least users don't see a 503 error
```

### Admission Worker Failure

```
Scenario: The admission worker process crashes.

Impact: No users are being admitted from the queue. Users wait indefinitely.

Recovery:
  1. Run multiple admission workers (active-passive with leader election)
     Only one processes ZPOPMIN at a time (via Redis SETNX lock).
     If the leader dies, another takes over within 10-30 seconds.
  
  2. Monitor "time since last admission" metric
     Alert if > 30 seconds since last ZPOPMIN.
     Auto-restart the worker.
  
  3. Stateless worker design
     Worker reads everything from Redis — no local state.
     Any instance can pick up where another left off.
```

### Partial Inventory Sync Failure

```
Scenario: Redis inventory counter drifts from database.
  Redis says 50 items left, DB says 45.

Impact: 5 users admitted who can't actually buy anything.

Prevention:
  1. Periodic reconciliation (every 30 seconds):
     DB_count = SELECT COUNT(*) FROM items WHERE status = 'AVAILABLE' AND event_id = 789
     redis.set("inventory:event:789", DB_count)
  
  2. Inventory check at booking time (Phase 2):
     Even if Redis is slightly off, the DB lock prevents overselling.
     User just gets "Sorry, this item was just sold" → back to queue or waitlist.
  
  3. Conservative Redis count:
     Redis_count = DB_count - safety_buffer (e.g., 5%)
     Ensures we never admit more users than items.
```

---

## User Experience During the Wait

### Waiting Room Page Design

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│    🎫  Taylor Swift — Eras Tour — London O2 Arena        │
│                                                          │
│    ┌──────────────────────────────────────────────────┐  │
│    │                                                  │  │
│    │           You are in the queue                   │  │
│    │                                                  │  │
│    │       Position: #42,315 of 487,231               │  │
│    │                                                  │  │
│    │   ████████████░░░░░░░░░░░░░░░░░░  8.7%           │  │
│    │                                                  │  │
│    │      Estimated wait: ~7 minutes                  │  │
│    │                                                  │  │
│    │   ⓘ Please keep this tab open.                   │  │
│    │     We'll notify you when it's your turn.        │  │
│    │                                                  │  │
│    └──────────────────────────────────────────────────┘  │
│                                                          │
│    💡 Tips while you wait:                                │
│    • Don't refresh — you'll keep your place in line      │
│    • Have your payment info ready                         │
│    • You'll have 5 minutes to complete checkout           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### UX Best Practices

```
DO:
  ✅ Show exact position and estimated wait time
  ✅ Update position in real-time (every 5-10 seconds)
  ✅ Show progress bar (visual feedback)
  ✅ Explain what happens next ("You'll have 5 min to checkout")
  ✅ Warn against refreshing ("You'll keep your place — don't refresh")
  ✅ Play a sound / show notification when it's their turn
  ✅ Show event details while waiting (build excitement)
  ✅ Mobile-friendly — users will be on phones

DON'T:
  ❌ Show a blank spinner with no information
  ❌ Let users lose their place on page refresh
  ❌ Give wildly inaccurate time estimates
  ❌ Make users solve CAPTCHAs repeatedly while waiting
  ❌ Auto-redirect without warning (user might be in another tab)
```

### Handling Page Refresh

```
Problem: User refreshes the page. Do they lose their place?

Solution: Anchor position to a persistent identifier.

  Option A: Session cookie
    On /enqueue, set cookie: queue_session=user:42:event:789
    On page load, check: ZSCORE queue:event:789 "user:42"
    If found → restore position display. If not → re-enqueue.

  Option B: URL token
    /queue?token=abc123xyz
    Token maps to user's queue entry.
    Refresh reloads same URL → same token → same position.

  Option C: Account-based (best)
    Logged-in user → position tied to user_id.
    ZADD is idempotent — re-adding same user_id doesn't change position.
    Refresh → poll ZRANK with same user_id → same position.
```

---

## Randomized vs FIFO Queue Entry

### Detailed Comparison

| Aspect | FIFO | Randomized (Lottery) |
|---|---|---|
| **Fairness model** | Time-based: earliest arrival wins | Equal chance: everyone in window has same odds |
| **Bot advantage** | High: bots arrive faster | Low: arrival time doesn't matter |
| **User behavior** | Stampede at T=0 (everyone rushes) | Calm: join anytime in window |
| **Server load pattern** | Huge spike at sale start | Spread across registration window |
| **Implementation** | ZADD with timestamp score | ZADD with random score |
| **User perception** | "Fair if I'm fast" | "Fair if I'm lucky" |
| **Best for** | General e-commerce flash sales | High-profile limited drops (concerts, sneakers) |
| **Real-world** | Amazon Lightning Deals | Ticketmaster Verified Fan, Nike SNKRS |

### When to Choose Which

```
Choose FIFO when:
  - Items are not extremely limited (1000+ available)
  - Speed of arrival is a reasonable proxy for demand
  - Users expect "first come first served"
  - Simple to implement and explain

Choose Randomized when:
  - Items are extremely limited (< 100)
  - Bot prevention is critical
  - Fairness perception is paramount
  - You want to reduce server spike at launch time
  - Event has presale/registration phase
```

---

## Multi-Event & Multi-Tier Queues

### Multiple Concurrent Events

```
Separate queue per event (simple and effective):
  queue:event:789  → Taylor Swift London
  queue:event:790  → Taylor Swift Manchester
  queue:event:791  → Beyoncé Paris

Each event has:
  - Its own ZSET
  - Its own inventory counter
  - Its own admission worker (or shared worker with per-event config)
  - Its own set of active tokens

No cross-event interference. Easy to scale per event.
```

### Priority Queues (Tiered Access)

```
Some users get priority: fan club members, presale code holders, VIPs.

Implementation: Multiple queues with priority ordering.

  queue:event:789:vip       → Processed first
  queue:event:789:presale   → Processed second
  queue:event:789:general   → Processed last

  Admission worker:
    1. Pop from :vip queue first (until empty)
    2. Then pop from :presale queue (until empty)
    3. Then pop from :general queue
    
  OR: Single queue with weighted scores:
    VIP:     ZADD queue:event:789 {0 + random(0, 0.1)} {user}     → scores 0-0.1
    Presale: ZADD queue:event:789 {0.1 + random(0.1, 0.2)} {user} → scores 0.1-0.3
    General: ZADD queue:event:789 {1.0 + timestamp} {user}         → scores 1.0+
    
    ZPOPMIN naturally processes VIP → Presale → General.

  Capacity allocation:
    First 200 items:  VIP only
    Next 300 items:   Presale (VIP overflow allowed)
    Remaining 500:    General sale
```

### Per-Tier Inventory Pools

```
Allocate inventory across tiers to prevent VIPs from buying everything:

  Event: 1000 total tickets
    VIP pool:     200 tickets (reserved for VIP)
    Presale pool: 300 tickets (reserved for presale)
    General pool: 500 tickets (open to all)
    
  If VIP pool not exhausted → overflow to general pool.
  Each pool has its own inventory counter:
    inventory:event:789:vip     = 200
    inventory:event:789:presale = 300
    inventory:event:789:general = 500
```

---

## Observability & Monitoring

### Key Metrics

```
Queue Health:
  queue.size{event_id}              = gauge    # Users currently in queue
  queue.join_rate{event_id}         = counter  # Users joining per second
  queue.admission_rate{event_id}    = counter  # Users admitted per second
  queue.drain_time{event_id}        = gauge    # Estimated time to empty queue

Token Metrics:
  tokens.active{event_id}           = gauge    # Currently admitted users
  tokens.expired{event_id}          = counter  # Tokens that timed out
  tokens.used{event_id}             = counter  # Tokens that led to purchase
  tokens.conversion_rate            = used / (used + expired)

Inventory:
  inventory.remaining{event_id}     = gauge    # Items left
  inventory.reserved{event_id}      = gauge    # Items currently reserved
  inventory.sold{event_id}          = counter  # Items purchased

System Health:
  redis.latency_ms{operation}       = histogram
  api.latency_ms{endpoint}          = histogram
  admission_worker.cycle_time_ms    = histogram
  admission_worker.last_run_ago_s   = gauge    # Alert if > 30s
```

### Alerting Rules

```
CRITICAL:
  - admission_worker.last_run_ago_s > 60 → Worker died
  - redis.latency_ms.p99 > 10ms → Redis overloaded
  - tokens.active > inventory.remaining × 1.5 → Over-admission
  - queue.size > 0 AND inventory.remaining = 0 → Sold out, drain queue

WARNING:
  - tokens.conversion_rate < 0.3 → 70% of users failing to checkout
  - queue.drain_time > 60 minutes → Very long wait (UX concern)
  - queue.join_rate > 100K/sec → Extreme demand (verify scaling)
```

---

## Real-World Production Patterns

### Pattern 1: Ticketmaster — Verified Fan Queue

```
Ticketmaster's approach for high-demand events:

  Phase 1: Registration (days before sale)
    - Fans register interest: "I want Taylor Swift London tickets"
    - Ticketmaster verifies accounts (age, history, device fingerprint)
    - Bot detection during registration period

  Phase 2: Queue Assignment (hours before sale)
    - Verified fans assigned random queue positions
    - "Your position has been randomly assigned"
    - Non-verified fans placed at back of queue

  Phase 3: Sale Opens
    - Queue processes in assigned order
    - Admitted fans get 10-minute checkout window
    - If fan doesn't checkout → spot released to next in line

  Scale: 3.5M users in queue for Taylor Swift Eras Tour
  
  Architecture:
    - CDN serves waiting room (static HTML + JS)
    - Queue-it (third-party queue service) or custom queue
    - Separate booking backend with limited concurrency
```

### Pattern 2: Shopify — Flash Sale Queue

```
Shopify merchants running flash sales:

  Shopify's Queue System (built into Shopify Plus):
    - Auto-activates when traffic exceeds threshold
    - Users see "You're in line" page
    - FIFO order with randomized entry for same-millisecond arrivals
    - Checkout throttled to 60 customers per minute per store

  Key design decisions:
    - Queue activates AUTOMATICALLY when load > threshold
      (Merchant doesn't configure it — it's transparent)
    - Queue page is on Shopify's CDN (not the merchant's server)
    - Admitted users get a cookie with expiring token
    - Cart contents preserved across queue wait
```

### Pattern 3: AWS Queue-it — Queue-as-a-Service

```
Queue-it is a dedicated waiting room SaaS:

  Architecture:
    - Customer integrates Queue-it JavaScript snippet
    - When traffic exceeds threshold → users redirected to Queue-it's servers
    - Queue-it manages the waiting room entirely
    - Admitted users redirected back to customer's site with a signed token
    - Customer validates token on their backend

  Scale: Handles 100M+ users across clients
  
  Pricing: Per user in queue (fractions of a cent)
  
  Features:
    - Customizable waiting room page
    - Analytics dashboard
    - AB testing on queue page
    - Pre-queue lobby (registration phase)
    - API integration for backend validation
```

### Pattern 4: Nike SNKRS — Lottery-Based Drop

```
Nike SNKRS approach for limited sneaker drops:

  1. Draw Entry (10 minutes before drop):
     - Users tap "Enter Draw" for the shoe they want
     - Select size
     - Confirm payment method

  2. Random Selection (at drop time):
     - All entries in the draw are shuffled randomly
     - Winners selected up to inventory count
     - NOT first-come-first-served

  3. Notification:
     - Winners: "Got 'Em!" → payment charged → shipping
     - Losers: "Didn't Get 'Em" → no charge

  Why lottery instead of queue?
    - Shoes are extremely limited (< 500 pairs)
    - Bot advantage with FIFO is extreme
    - Fairness perception: "everyone had equal chance"
    - Reduces server spike (no stampede at T=0)
```

### Pattern 5: PlayStation Direct — PS5 Queue

```
Sony's approach during PS5 shortages:

  1. Registration: Users sign up for "invite to purchase" queue
  2. Invite: Sony sends email invites to selected users
  3. Queue: Invited users join a virtual queue at scheduled time
  4. Admission: Users admitted in batches
  5. Purchase: 10-minute checkout window
  
  Key twist: INVITATION-ONLY queue
    - You can't just show up — you must be invited
    - Invitation based on PSN account history (real gamers prioritized)
    - Eliminates most bots (need a real PSN account with gaming history)
    - Dramatically reduces queue size (50K invited vs 5M wanting)
```

---

## Interview Talking Points & Scripts

### Script 1: Core Architecture

> *"For a flash sale with 500K users competing for 1,000 items, I'd implement a virtual queue using a Redis sorted set. Users join the queue via `ZADD queue:{event_id} {timestamp} {user_id}`, ordered by arrival time. An admission worker runs every 10 seconds, popping the oldest batch of users with `ZPOPMIN`. Each admitted user receives a time-limited access token (stored as a Redis key with 5-minute TTL) that grants them access to the booking page. The queue page itself is served from CDN — it handles 500K connections with zero backend load. Users poll for their position via `ZRANK`, which is O(log N). This way, the backend only ever processes 1,000 concurrent checkouts at a time — no overload, fair ordering, and transparent wait times."*

### Script 2: Why Not Just Scale the Backend?

> *"Scaling the backend alone doesn't work for flash sales. Even with 100 API servers, 500K simultaneous users means 5K per server — all trying to lock the same 1,000 inventory rows. The database becomes the bottleneck with deadlocks and connection exhaustion. A virtual queue is the right answer because the fundamental constraint is inventory, not compute. We don't need to process 500K checkouts in parallel — we need to process 1,000 checkouts without corruption, and buffer the other 499K users gracefully."*

### Script 3: Admission Control

> *"The admission worker uses a feedback loop: it checks `SCARD active_tokens:{event_id}` to see how many users are currently shopping, compares to the target concurrency (say, 1,000), and admits the difference. If 847 users are active, it admits 153 more. This adaptive approach prevents over-admission and keeps the backend at exactly the load it can handle. If average checkout time is 2 minutes and token TTL is 5 minutes, the math naturally balances."*

### Script 4: Fairness

> *"For fairness, I'd consider two approaches. For a general e-commerce flash sale, FIFO is intuitive — first come, first served via sorted set scores as timestamps. For high-profile limited drops like concert tickets, I'd use a randomized approach: open a registration window 5 minutes before the sale, collect all entries, then randomly shuffle positions. This eliminates bot advantage since arrival order doesn't matter. Ticketmaster uses this for their 'Verified Fan' system."*

### Script 5: Anti-Bot

> *"Bots are the biggest threat to flash sales. I'd layer defenses: pre-queue invisible CAPTCHA (reCAPTCHA v3 risk scoring), device fingerprinting to prevent one person entering multiple times, account age requirements (no accounts created in the last 7 days), and behavioral analysis of pre-sale browsing patterns. For the highest-value events, a registration-based lottery eliminates the bot advantage entirely since arrival time doesn't affect position."*

### Script 6: Failure Handling

> *"If the queue Redis goes down, Redis Sentinel promotes a replica within 5 seconds. Some users might lose their exact position, but they can re-join. If the admission worker dies, a standby picks up — the worker is stateless, reading everything from Redis. If inventory count in Redis drifts from the database, periodic reconciliation corrects it every 30 seconds. And the database pessimistic lock is the final guard against overselling — even if every other layer has bugs, `SELECT FOR UPDATE` prevents double-selling."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Just scale the backend"

**Bad**: "We'll add more servers to handle 500K users."
**Fix**: The bottleneck is inventory contention, not compute. A virtual queue decouples demand from supply.

### ❌ Mistake 2: Using a simple Redis LIST instead of ZSET

**Bad**: `LPUSH` / `RPOP` for the queue.
**Fix**: ZSET gives O(log N) position lookup (`ZRANK`), deduplication, and range queries. LIST has O(N) position search.

### ❌ Mistake 3: No admission control — just "serve from queue"

**Bad**: Popping from queue and letting users through with no concurrency limit.
**Fix**: Admission worker with feedback loop. Track active tokens and only admit up to target concurrency.

### ❌ Mistake 4: Forgetting token expiry

**Bad**: User gets admitted, abandons checkout, holds the slot forever.
**Fix**: Token TTL (5 minutes). When it expires, the slot is freed for the next user.

### ❌ Mistake 5: All 500K users polling Redis directly

**Bad**: `ZRANK` calls at 100K/sec → Redis overwhelmed by position checks.
**Fix**: CDN-cached queue metadata + individual ZRANK only near the front or infrequently.

### ❌ Mistake 6: Not handling sold-out gracefully

**Bad**: Users wait 30 minutes, get to the front, and see "Sold Out."
**Fix**: Monitor remaining inventory. Notify users when sold out. Offer waitlist for cancellations.

### ❌ Mistake 7: Ignoring bots

**Bad**: No anti-bot measures → bots buy everything.
**Fix**: CAPTCHA, fingerprinting, account verification, randomized entry for limited drops.

### ❌ Mistake 8: Not mentioning the CDN waiting room page

**Bad**: Queue page served by backend → backend overwhelmed by queue page traffic.
**Fix**: Static waiting room HTML/JS served from CDN. Only API calls (enqueue, position) hit the backend.

---

## Virtual Queue by System Design Problem

| Problem | Queue Type | Capacity | Key Considerations |
|---|---|---|---|
| **Concert Tickets (Ticketmaster)** | Randomized + FIFO | 1K-100K seats | Verified Fan registration, anti-bot, tiered presale |
| **Flash Sale (Amazon Lightning)** | FIFO | 100-10K items | Auto-activate queue when demand > threshold |
| **Sneaker Drop (Nike SNKRS)** | Lottery | < 500 pairs | Registration window, random selection, no FIFO |
| **Console Launch (PS5)** | Invitation + FIFO | 10K-100K units | Invite-only, account history verification |
| **Hotel/Airline Booking** | FIFO | Varies | Per-route/date queue, inventory pools per class |
| **IPO Stock Allocation** | Lottery + Priority | Fixed shares | Priority for existing customers, pro-rata allocation |
| **Restaurant Reservations** | FIFO Waitlist | 1-10 tables | Real-time cancellation recirculation |
| **Gaming Server Launch** | FIFO | Server capacity | Staggered admission to prevent login server crash |
| **Vaccine Appointment** | Priority + FIFO | Daily capacity | Priority tiers (age, risk), per-location queues |
| **Black Friday Doorbusters** | FIFO per product | Per-item limit | Separate queue per doorbuster item |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│             VIRTUAL QUEUE FOR FLASH SALES — CHEAT SHEET              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  PROBLEM: 500K users, 1K items → crash without queue                 │
│  SOLUTION: Buffer demand in Redis ZSET, admit in controlled batches  │
│                                                                      │
│  DATA STRUCTURES:                                                    │
│    Queue:     ZSET (score=timestamp or random, member=user_id)       │
│    Tokens:    STRING with TTL (5 min) → access credential            │
│    Active:    SET of current token IDs → track concurrency           │
│    Inventory: STRING (integer) → fast DECR for stock check           │
│                                                                      │
│  KEY OPERATIONS:                                                     │
│    Join:      ZADD queue:{eid} {score} {uid}          O(log N)       │
│    Position:  ZRANK queue:{eid} {uid}                 O(log N)       │
│    Admit:     ZPOPMIN queue:{eid} {batch}             O(log N × M)   │
│    Validate:  EXISTS token:{token}                    O(1)           │
│    Size:      ZCARD queue:{eid}                       O(1)           │
│                                                                      │
│  ADMISSION CONTROL:                                                  │
│    Feedback loop: target_active - current_active = batch_size        │
│    Adaptive: Monitor checkout rate, adjust admission rate            │
│    Staggered: Spread admission over interval (avoid mini-herds)      │
│                                                                      │
│  FAIRNESS:                                                           │
│    FIFO: score = timestamp (first-come-first-served)                 │
│    Random: score = random() within registration window               │
│    Hybrid: Random for Phase 1 (pre-sale), FIFO for Phase 2 (live)   │
│                                                                      │
│  TOKEN LIFECYCLE:                                                    │
│    Issued → Active → Checkout → Completed (or Expired/Revoked)       │
│    TTL = 5 minutes. Sliding expiration on activity.                  │
│    Expired tokens → slot freed → next user admitted.                 │
│                                                                      │
│  ANTI-BOT:                                                           │
│    CAPTCHA + Fingerprint + Account age + Rate limit + Behavioral     │
│    For limited drops: Lottery eliminates speed advantage              │
│                                                                      │
│  SCALING:                                                            │
│    500K ZSET = ~50 MB. Single Redis handles it easily.               │
│    Queue page on CDN (0 backend load for 500K connections).          │
│    Position polling: CDN-cached metadata + sparse ZRANK calls.       │
│                                                                      │
│  FAILOVER:                                                           │
│    Redis Sentinel: auto-failover (1-5 sec).                          │
│    Admission worker: stateless, standby picks up.                    │
│    Inventory: DB is source of truth, Redis is fast approximation.    │
│    Last resort: DB pessimistic lock prevents overselling.            │
│                                                                      │
│  UX:                                                                 │
│    Show position + estimated wait + progress bar.                    │
│    Warn: "Don't refresh." Sound/notification when admitted.          │
│    Sold out: Notify queued users, offer waitlist.                    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 15: Rate Limiting** — Rate limiting the queue API itself
- **Topic 21: Concurrency Control** — Two-layer locking for the booking backend
- **Topic 30: Backpressure** — Virtual queue as a backpressure mechanism
- **Topic 40: Redis Deep Dive** — ZSET, Lua scripting, Cluster
- **Topic 42: Job Scheduling** — Admission worker as a scheduled job
- **Topic 54: Distributed Rate Limiting** — Token Bucket for API protection alongside queues

---

*This document is part of the System Design Interview Deep Dive series.*
