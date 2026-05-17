# 🎯 Topic 57: Distributed Counter at Scale

> **System Design Interview — Deep Dive**
> A comprehensive guide covering distributed counter patterns for like counts, view counts, and inventory — sharded counters in Redis, write-behind buffering, local accumulation + periodic flush, CRDTs for multi-region counters, eventual vs strong consistency tradeoffs, hot key mitigation, counter rollup to databases, exact vs approximate counting, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Single Counters Don't Scale](#why-single-counters-dont-scale)
3. [Sharded Counter Pattern](#sharded-counter-pattern)
4. [Local Accumulation + Periodic Flush](#local-accumulation--periodic-flush)
5. [Write-Behind Buffering](#write-behind-buffering)
6. [Redis Counter Implementation](#redis-counter-implementation)
7. [Reading Distributed Counters](#reading-distributed-counters)
8. [Counter Rollup to Database](#counter-rollup-to-database)
9. [Exact vs Approximate Counting](#exact-vs-approximate-counting)
10. [Inventory Counters — Stronger Consistency](#inventory-counters--stronger-consistency)
11. [CRDTs for Multi-Region Counters](#crdts-for-multi-region-counters)
12. [Idempotency and Deduplication](#idempotency-and-deduplication)
13. [Hot Key Detection and Mitigation](#hot-key-detection-and-mitigation)
14. [Counter Overflow and Precision](#counter-overflow-and-precision)
15. [Negative Counters — Unlikes and Decrements](#negative-counters--unlikes-and-decrements)
16. [Observability and Monitoring](#observability-and-monitoring)
17. [Performance Tuning & Benchmarks](#performance-tuning--benchmarks)
18. [Real-World Production Patterns](#real-world-production-patterns)
19. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
20. [Common Interview Mistakes](#common-interview-mistakes)
21. [Distributed Counter by System Design Problem](#distributed-counter-by-system-design-problem)
22. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **distributed counter** tracks a numeric value (likes, views, inventory) that is incremented or decremented by many servers concurrently. The core challenge is that a single counter key becomes a **hot key** — a bottleneck when thousands of servers write to the exact same key simultaneously.

```
The fundamental problem:

  Taylor Swift posts a new Instagram photo.
  10M users like it in the first 5 minutes.
  = 33,333 likes/second hitting one counter.

  Single Redis key: "likes:post:12345"
    INCR likes:post:12345   ← 33K times per second
    All 33K operations serialize on ONE Redis key on ONE shard.
    Redis single-threaded: can handle ~300K ops/sec total,
    but ONE key can only sustain ~100K ops/sec before queuing.
    
  Result: At 33K/sec it works. At 100K+/sec → hot key bottleneck.
  With viral global events (World Cup goal) → 500K+ increments/sec → problem.

  Solution: SHARD the counter into N sub-counters.
    likes:post:12345:shard_0  → INCR
    likes:post:12345:shard_1  → INCR
    likes:post:12345:shard_2  → INCR
    ...
    likes:post:12345:shard_7  → INCR

    Total likes = SUM of all shards.
    Each shard handles 1/N of the traffic. No hot key.
```

### Counter Categories

| Category | Example | Consistency Needed | Tolerance for Error |
|---|---|---|---|
| **Vanity metrics** | Like count, view count, follower count | Eventual (seconds) | ±0.1% acceptable |
| **Business metrics** | Ad impression count, click count | Eventual (seconds) | ±0.01% (billing) |
| **Inventory** | Stock quantity, seat availability | Strong (immediate) | ZERO tolerance |
| **Rate limiting** | Request count per window | Near-real-time | ±5% acceptable |
| **Analytics** | Page views, DAU | Eventual (minutes) | ±1% acceptable |

---

## Why Single Counters Don't Scale

### The Hot Key Problem

```
A single counter key (likes:post:12345) lives on ONE Redis shard.

  Redis Cluster: CRC16("likes:post:12345") mod 16384 → slot 7823 → Shard 3.
  ALL increments for this post go to Shard 3.
  Other shards are idle. Load is completely imbalanced.

  ┌─────────┐
  │ Shard 1  │  idle (0 ops/sec)
  ├─────────┤
  │ Shard 2  │  idle (0 ops/sec)
  ├─────────┤
  │ Shard 3  │  ████████████████ 100K ops/sec ← ALL traffic here
  ├─────────┤
  │ Shard 4  │  idle (0 ops/sec)
  └─────────┘

  Single Redis instance: ~300K ops/sec total capacity.
  Single KEY throughput: limited by single-thread command execution.
  At 100K+ INCR/sec to one key: queue builds, latency spikes.
```

### The Database Problem

```
Storing counters directly in the database:

  UPDATE posts SET like_count = like_count + 1 WHERE post_id = 12345;

  33K UPDATE/sec on ONE row:
  - Row-level lock contention → UPDATE serializes
  - PostgreSQL: ~5K UPDATE/sec on a single hot row (with lock contention)
  - MySQL InnoDB: Similar constraint
  - Transaction log write amplification

  Even with connection pooling and tuning: 5-10K/sec per row is the ceiling.
  At 33K/sec → lock wait timeouts, deadlocks, degraded performance.
```

### Why INCR Alone Isn't Enough

```
Redis INCR is atomic and fast (O(1), ~0.1ms).
At 100K INCR/sec to a single key: works (Redis handles it).
At 500K INCR/sec: starts queueing.
At 1M INCR/sec: timeout errors.

Even within Redis's capability, putting ALL write traffic on ONE shard
is a waste of the cluster. 5 other shards sit idle.

The solution is the same in all cases: SHARD the writes.
```

---

## Sharded Counter Pattern

### How It Works

```
Instead of one counter key, create N sub-counter keys (shards):

  Original: likes:post:12345                    → 1 key, 1 shard
  Sharded:  likes:post:12345:shard_{0..N-1}    → N keys, spread across shards

  Write path:
    1. Pick a random shard: shard_id = random(0, N-1)
       OR hash the writer: shard_id = hash(user_id) % N
    2. INCR likes:post:12345:shard_{shard_id}
    
  Read path:
    Total likes = SUM of all N shards
    MGET likes:post:12345:shard_0 ... likes:post:12345:shard_{N-1}
    Sum them up.

  Example (N=8):
    likes:post:12345:shard_0 = 4,127
    likes:post:12345:shard_1 = 4,089
    likes:post:12345:shard_2 = 4,203
    likes:post:12345:shard_3 = 4,156
    likes:post:12345:shard_4 = 4,098
    likes:post:12345:shard_5 = 4,178
    likes:post:12345:shard_6 = 4,145
    likes:post:12345:shard_7 = 4,112
    ─────────────────────────
    Total: 33,108 likes
```

### Choosing the Shard Count (N)

```
Rule of thumb: N = expected_peak_writes_per_sec / writes_per_shard_comfort

  Single Redis shard comfort zone: ~50K INCR/sec per key (conservative)
  
  Example scenarios:
    Normal post (1K likes/sec):  N = 1 (no sharding needed)
    Popular post (50K likes/sec): N = 2-4
    Viral post (500K likes/sec):  N = 8-16
    Global event (1M+ likes/sec): N = 32+

Dynamic sharding:
  Start with N=1. If hot key detected (>50K ops/sec), auto-shard to N=8.
  When traffic drops, merge shards back to reduce read overhead.
  
  Most posts: N=1 (no overhead).
  Viral posts: N=8-16 (auto-scaled).
```

### Implementation

```python
class ShardedCounter:
    """Distributed sharded counter in Redis."""
    
    def __init__(self, redis, key, num_shards=8):
        self.redis = redis
        self.key = key
        self.num_shards = num_shards
    
    def increment(self, amount=1):
        """Increment a random shard."""
        shard = random.randint(0, self.num_shards - 1)
        shard_key = f"{self.key}:shard:{shard}"
        return self.redis.incrby(shard_key, amount)
    
    def decrement(self, amount=1):
        """Decrement a random shard."""
        shard = random.randint(0, self.num_shards - 1)
        shard_key = f"{self.key}:shard:{shard}"
        return self.redis.decrby(shard_key, amount)
    
    def get_count(self):
        """Read total by summing all shards."""
        pipe = self.redis.pipeline()
        for i in range(self.num_shards):
            pipe.get(f"{self.key}:shard:{i}")
        results = pipe.execute()
        return sum(int(v or 0) for v in results)

# Usage
counter = ShardedCounter(redis_client, "likes:post:12345", num_shards=8)
counter.increment()         # Like
counter.decrement()         # Unlike
total = counter.get_count() # Read total
```

### Write Throughput Improvement

```
Without sharding (N=1):
  Max throughput: ~100K INCR/sec (single key)
  
With sharding (N=8):
  Max throughput: 8 × 100K = ~800K INCR/sec
  (If shards land on different Redis shards — use different hash slots)

With sharding (N=8) + Redis Cluster (6 shards):
  Spread across multiple Redis nodes.
  Max throughput: 800K+ INCR/sec (nearly unlimited with more shards)

Read overhead:
  N=1: 1 GET → 0.1ms
  N=8: 8 GETs (pipelined) → 0.3ms
  N=32: 32 GETs (pipelined) → 0.5ms
  
  Read cost increases linearly with N, but pipelining keeps it low.
```

---

## Local Accumulation + Periodic Flush

### The Pattern

```
Instead of sending every increment to Redis immediately,
accumulate counts locally on each app server and flush periodically.

  App Server 1: local_buffer["likes:post:12345"] = 0
    Like event → local_buffer += 1 (in-memory, no network)
    Like event → local_buffer += 1
    Like event → local_buffer += 1
    ... (accumulate for 1 second)
    
    Every 1 second: INCRBY likes:post:12345 47  (batch flush)
    Reset local_buffer to 0.

  Instead of 47 Redis calls → 1 Redis call.
  47x reduction in Redis operations.

  With 50 app servers, each flushing once per second:
    50 INCRBY/sec to Redis (instead of 33K INCR/sec).
    Massive reduction. Redis barely notices.
```

### Implementation

```python
class BufferedCounter:
    """Local accumulation with periodic flush to Redis."""
    
    def __init__(self, redis, flush_interval=1.0):
        self.redis = redis
        self.flush_interval = flush_interval
        self.local_counts = defaultdict(int)   # key → accumulated delta
        self.lock = threading.Lock()
        self._start_flush_thread()
    
    def increment(self, key, amount=1):
        """Instant local increment. No network call."""
        with self.lock:
            self.local_counts[key] += amount
    
    def decrement(self, key, amount=1):
        with self.lock:
            self.local_counts[key] -= amount
    
    def _flush(self):
        """Periodic flush to Redis."""
        while True:
            time.sleep(self.flush_interval)
            with self.lock:
                to_flush = dict(self.local_counts)
                self.local_counts.clear()
            
            if to_flush:
                pipe = self.redis.pipeline()
                for key, delta in to_flush.items():
                    if delta != 0:
                        pipe.incrby(key, delta)
                pipe.execute()
    
    def _start_flush_thread(self):
        t = threading.Thread(target=self._flush, daemon=True)
        t.start()

# Usage
counter = BufferedCounter(redis_client, flush_interval=1.0)
counter.increment("likes:post:12345")  # Instant, no network
counter.increment("likes:post:12345")  # Instant, no network
# ... 1 second later, batch INCRBY to Redis
```

### Tradeoffs

```
✅ Pros:
  - Massive write amplification reduction (1000 events → 1 Redis call)
  - Sub-microsecond local increment (vs 0.1ms Redis call)
  - Redis load drops by 1000x
  - Works with or without counter sharding

❌ Cons:
  - Data loss on app server crash (buffered counts lost)
    Mitigation: 1-second flush interval → max 1 second of data loss
    For like counts: Losing 1 second of likes is acceptable
    For inventory: NOT acceptable (use strong consistency instead)
    
  - Stale reads: Counter in Redis is up to flush_interval seconds behind
    User likes a post → reads the counter → their like isn't reflected yet
    Mitigation: "Read your own writes" — add local delta to Redis read
    
  - Memory usage: One counter per active key per server
    1M active posts × 16 bytes = 16 MB per server → negligible
```

### Read-Your-Own-Writes Consistency

```python
class ConsistentBufferedCounter:
    """Buffered counter with read-your-own-writes guarantee."""
    
    def __init__(self, redis, flush_interval=1.0):
        self.redis = redis
        self.buffer = BufferedCounter(redis, flush_interval)
        self.local_counts = defaultdict(int)  # Track this server's buffered delta
    
    def increment(self, key, amount=1):
        self.buffer.increment(key, amount)
        self.local_counts[key] += amount
    
    def get_count(self, key):
        # Redis count + unflushed local delta
        redis_count = int(self.redis.get(key) or 0)
        local_delta = self.local_counts.get(key, 0)
        return redis_count + local_delta
    
    def on_flush(self, key):
        # Called after successful flush to Redis
        self.local_counts[key] = 0
```

---

## Write-Behind Buffering

### Pattern: Redis as Write Buffer → Database as Source of Truth

```
Architecture:

  App Servers → Redis (fast writes) → Async Worker → Database (durable)

  Write path:
    1. User likes post → INCR likes:post:12345 in Redis (0.1ms)
    2. Return "liked!" to user immediately
    
  Async rollup:
    3. Every 30 seconds, a rollup worker reads Redis counters
    4. Writes to database: UPDATE posts SET like_count = {redis_count} WHERE id = 12345
    5. Database is always slightly behind Redis (up to 30 seconds)

  Read path:
    6. Dashboard/API reads from Redis (fast, recent)
    7. Analytics/reports read from database (durable, queryable)

  ┌──────────┐     INCR      ┌─────────┐   Rollup   ┌──────────┐
  │ App Server│──────────────→│  Redis   │───────────→│ Database │
  └──────────┘  (0.1ms)      └─────────┘  (30 sec)  └──────────┘
                                  ↑                       ↑
                            Fast reads              Analytical queries
                            (API, UI)               (reports, backup)
```

### Rollup Worker Implementation

```python
class CounterRollupWorker:
    """Periodically syncs Redis counters to the database."""
    
    def __init__(self, redis, db, interval=30):
        self.redis = redis
        self.db = db
        self.interval = interval
    
    def run(self):
        while True:
            time.sleep(self.interval)
            self._rollup()
    
    def _rollup(self):
        # Option A: Read counter from Redis, write absolute value to DB
        # Simple but overwrites any DB-side corrections
        
        # Option B: Read-and-reset with atomic Lua (GETSET pattern)
        # Get current delta since last rollup, add to DB
        
        keys = self.redis.keys("likes:post:*")  # Or scan with pattern
        
        pipe = self.redis.pipeline()
        for key in keys:
            # Atomically read and reset the delta counter
            pipe.getset(f"{key}:delta", 0)
        deltas = pipe.execute()
        
        # Batch update database
        updates = []
        for key, delta in zip(keys, deltas):
            if delta and int(delta) != 0:
                post_id = key.split(":")[2]
                updates.append((int(delta), post_id))
        
        if updates:
            self.db.executemany(
                "UPDATE posts SET like_count = like_count + %s WHERE id = %s",
                updates
            )
```

### Delta Counter vs Absolute Counter

```
Absolute counter:
  Redis stores the TOTAL count: likes:post:12345 = 33108
  Rollup: UPDATE posts SET like_count = 33108 WHERE id = 12345
  Problem: If DB had a manual correction (admin added 5 likes), it's overwritten.

Delta counter:
  Redis stores the CHANGE since last rollup: likes:post:12345:delta = 47
  Rollup: UPDATE posts SET like_count = like_count + 47 WHERE id = 12345
  Then: GETSET likes:post:12345:delta 0 (reset delta to 0)
  Benefit: DB corrections are preserved. Only the delta is applied.

RECOMMENDATION: Use BOTH.
  likes:post:12345         → absolute total (for fast reads)
  likes:post:12345:delta   → delta since last rollup (for DB sync)
  
  On increment: INCR both keys.
  On rollup: GETSET delta to 0, INCRBY into DB.
```

---

## Redis Counter Implementation

### Simple Atomic Counter

```
# Single key, no sharding
SET likes:post:12345 0
INCR likes:post:12345          → 1
INCR likes:post:12345          → 2
INCRBY likes:post:12345 10     → 12
GET likes:post:12345           → "12"
DECR likes:post:12345          → 11

Why INCR is safe:
  INCR is ATOMIC — read + increment + write in one operation.
  No race conditions even with concurrent writers.
  Redis single-threaded: commands execute sequentially.
```

### Sharded Counter with Lua

```lua
-- Atomic sharded increment with total tracking
-- KEYS[1] = base key (e.g., "likes:post:12345")
-- ARGV[1] = num_shards
-- ARGV[2] = amount (positive = increment, negative = decrement)
-- ARGV[3] = shard_id (pre-computed by caller)

local base_key = KEYS[1]
local num_shards = tonumber(ARGV[1])
local amount = tonumber(ARGV[2])
local shard_id = tonumber(ARGV[3])

-- Increment the specific shard
local shard_key = base_key .. ":shard:" .. shard_id
local new_shard_value = redis.call('INCRBY', shard_key, amount)

-- Also maintain a cached total for fast reads (approximate)
-- Updated lazily — exact total requires summing all shards
redis.call('INCRBY', base_key .. ":total_approx", amount)

-- Set TTL on shard (for counter cleanup)
redis.call('EXPIRE', shard_key, 86400 * 30)  -- 30 days

return new_shard_value
```

### Counter with TTL (Auto-Expiring Counters)

```
Use case: "Views in the last 24 hours" or "Requests in the last minute"

Approach: Per-window counter with TTL

  # View count per hour (auto-expiring)
  INCR views:post:12345:hour:2024031512    # 2024-03-15 12:00 hour
  EXPIRE views:post:12345:hour:2024031512 90000  # 25-hour TTL
  
  # Sum last 24 hours:
  MGET views:post:12345:hour:2024031512 
       views:post:12345:hour:2024031511
       ... (24 keys)
  SUM = total views in last 24 hours

  Old keys auto-expire. No cleanup needed.
```

### Redis Hash for Multi-Metric Counters

```
# Store multiple counters for one entity in a single hash
HINCRBY post:12345:counters likes 1
HINCRBY post:12345:counters views 1
HINCRBY post:12345:counters comments 1
HINCRBY post:12345:counters shares 1

HGETALL post:12345:counters
→ { likes: "33108", views: "1250000", comments: "4521", shares: "892" }

Benefits:
  ✅ One key for all counters → one lookup, one shard
  ✅ HINCRBY is atomic per field
  ✅ Reduces key count (1 hash vs 4 strings)
  
Tradeoff:
  ❌ All counters on one shard → if post is viral, all counters are hot
  ❌ Can't shard individual counters within the hash
  
Recommendation: Use hash for normal posts. Shard for viral posts.
```

---

## Reading Distributed Counters

### Read Strategies

```
Strategy 1: Sum all shards (exact, slower)
  MGET likes:post:12345:shard:0 ... :shard:7
  Total = sum(results)
  Latency: 0.2-0.5ms (pipelined)
  Use when: Need exact count, low-frequency reads.

Strategy 2: Read cached total (approximate, faster)
  GET likes:post:12345:total_approx
  Latency: 0.1ms
  Accuracy: Within flush interval of exact count.
  Use when: High-frequency reads (every page load), ±0.1% error OK.

Strategy 3: Read from database (durable, analytical)
  SELECT like_count FROM posts WHERE id = 12345
  Latency: 1-5ms
  Accuracy: Within rollup interval (30 seconds behind).
  Use when: Reports, analytics, backup reads.

Strategy 4: Hybrid — cached total + local delta
  total = GET likes:post:12345:total_approx
  local_delta = this_server.buffered_counts.get("likes:post:12345", 0)
  display = total + local_delta
  Latency: 0.1ms + O(1) memory lookup
  Use when: Need read-your-own-writes consistency.
```

### Caching the Total

```
Problem: Summing 8 shards on every read is 8x more expensive than reading 1 key.
  At 1M reads/sec: 8M Redis operations → expensive.

Solution: Maintain a cached total alongside the shards.

  On every INCR to a shard: also INCR the total key.
    INCR likes:post:12345:shard:3        → shard counter
    INCR likes:post:12345:total_approx   → cached total (approximate)
  
  Read: GET likes:post:12345:total_approx → single O(1) read.
  
  The "approx" is because in rare crash scenarios, the shard and total
  might be slightly out of sync. Periodic reconciliation fixes this:
    Every 5 minutes: total = SUM(shards) → SET total_approx

Why approximate is fine:
  For "33,108 likes" → showing "33,107" is imperceptible.
  Social media likes are vanity metrics — ±10 is meaningless.
  If EXACT count matters (inventory), use a different strategy (Section 10).
```

### Display Rounding

```
Real count:    33,108
Displayed:     "33.1K likes"

Real count:    1,482,937
Displayed:     "1.5M likes"

Why? Because exact numbers change every second for viral posts.
  Showing "33,108" vs "33,109" causes visual flickering.
  Showing "33.1K" is stable for ~100 likes.
  
  Less precision = better UX for rapidly changing counters.
  
Implementation:
  if count >= 1_000_000: display = f"{count / 1_000_000:.1f}M"
  elif count >= 1_000:   display = f"{count / 1_000:.1f}K"
  else:                  display = str(count)
```

---

## Counter Rollup to Database

### Why Roll Up?

```
Redis is the fast layer. Database is the durable layer.

  Redis:    Fast writes, fast reads, but volatile (data loss on crash).
  Database: Slow writes, queryable, durable, supports analytics.

  Roll up: Periodically sync Redis → Database.
  
  Why?
    1. Durability: If Redis loses data, DB has a recent backup.
    2. Analytics: SQL queries on counters (GROUP BY, aggregate, trends).
    3. Indexing: "Top 100 most-liked posts" → DB index on like_count.
    4. API fallback: If Redis is down, serve from DB (slightly stale).
```

### Batch Rollup with Delta Pattern

```python
class BatchRollupWorker:
    """Efficiently syncs Redis counter deltas to PostgreSQL."""
    
    ROLLUP_LUA = """
    -- Atomically read and reset delta
    local key = KEYS[1]
    local delta = redis.call('GET', key)
    if delta then
        redis.call('SET', key, 0)
        return delta
    end
    return 0
    """
    
    def __init__(self, redis, db, batch_size=1000):
        self.redis = redis
        self.db = db
        self.batch_size = batch_size
        self.script = redis.register_script(self.ROLLUP_LUA)
    
    def rollup(self):
        """Scan all delta keys and batch-update the database."""
        cursor = 0
        updates = []
        
        while True:
            cursor, keys = self.redis.scan(
                cursor, match="likes:*:delta", count=self.batch_size
            )
            
            for key in keys:
                # Atomically get-and-reset delta
                delta = int(self.script(keys=[key]) or 0)
                if delta != 0:
                    post_id = key.decode().split(":")[1]
                    updates.append((delta, post_id))
            
            # Batch update DB when buffer is full
            if len(updates) >= self.batch_size:
                self._flush_to_db(updates)
                updates = []
            
            if cursor == 0:
                break
        
        if updates:
            self._flush_to_db(updates)
    
    def _flush_to_db(self, updates):
        """Batch UPDATE with unnest for PostgreSQL efficiency."""
        self.db.execute("""
            UPDATE posts p
            SET like_count = p.like_count + u.delta
            FROM (SELECT unnest(%s::int[]) as delta, 
                         unnest(%s::bigint[]) as post_id) u
            WHERE p.id = u.post_id
        """, (
            [u[0] for u in updates],
            [u[1] for u in updates]
        ))
```

### Rollup Failure Handling

```
Scenario: Rollup reads delta=47, writes to DB, but DB write fails.
  Delta was already reset to 0 in Redis. 47 likes are lost!

Solution: Two-phase rollup

  Phase 1: Read deltas (don't reset yet)
    delta = GET likes:post:12345:delta  → 47
    
  Phase 2: Write to DB
    UPDATE posts SET like_count = like_count + 47 WHERE id = 12345
    
  Phase 3: Reset delta only after DB confirms
    If DB write succeeded: SET likes:post:12345:delta 0
    If DB write failed: Don't reset → retry next cycle (delta will be higher)
    
  Risk: Double-counting if DB write succeeds but reset fails.
  Mitigation: Idempotent updates with rollup_version:
    UPDATE posts SET like_count = like_count + 47, rollup_version = 42
    WHERE id = 12345 AND rollup_version < 42
```

---

## Exact vs Approximate Counting

### When Exact Counts Matter

```
Approximate OK:
  - Like count: "33.1K" vs "33,108" → nobody notices
  - View count: Delayed by seconds → acceptable
  - Follower count: Eventually consistent → fine

Exact required:
  - Inventory: "2 items left" → must be EXACTLY 2
  - Billing: "1,000 API calls" → undercounting = lost revenue
  - Rate limiting: "100 requests per minute" → overcounting = false rejects
  - Voting: "52% vs 48%" → accuracy matters
```

### Approximate Counting Techniques

```
Technique 1: Counter sharding with periodic reconciliation
  Write: INCR random shard → fast
  Read: SUM shards → exact but 8 ops
  Cached read: GET total_approx → approximate, 1 op
  Reconcile every 5 minutes: total_approx = SUM(shards)

Technique 2: Probabilistic counting (Morris counter)
  Instead of incrementing by 1 every time:
    Increment by 1 with probability 1/2^count_approximation
  Stores log2(count) instead of count.
  Memory: O(log log N) instead of O(log N).
  Use: When you have BILLIONS of counters and memory is critical.
  Error: ~30% relative error. Fine for "1.5B views" → "1.1-1.9B views".

Technique 3: HyperLogLog (unique counting)
  Not a counter — counts UNIQUE items (distinct users who liked).
  PFADD likes:post:12345 user:42 user:99 user:7
  PFCOUNT likes:post:12345 → 3 (approx unique likers)
  Memory: 12 KB per counter regardless of cardinality.
  Error: 0.81%.
  Use: "How many unique users viewed this page?"
```

---

## Inventory Counters — Stronger Consistency

### Why Inventory Is Different

```
Like counts: If we show "33,108" but the real count is "33,107", nobody cares.
Inventory: If we show "2 items left" but the real count is "0", we oversell.

  Overselling consequences:
    - Customer orders item → "Sorry, out of stock" → refund → angry customer
    - Financial loss: Already charged → refund processing → customer lost
    - Legal issues for some products (e.g., concert tickets)

  Inventory requires STRONG consistency. Eventual consistency is not acceptable.
```

### Inventory Counter Pattern

```
Two-phase: Redis for speed + Database for truth.

  Write path (reserve inventory):
    1. Lua script in Redis:
       local stock = redis.call('GET', 'inventory:product:789')
       if tonumber(stock) > 0 then
           redis.call('DECR', 'inventory:product:789')
           return 1  -- RESERVED
       else
           return 0  -- OUT OF STOCK
       end
       
    2. If Redis says RESERVED:
       INSERT INTO reservations (product_id, user_id, expires_at)
       VALUES (789, 42, NOW() + INTERVAL '10 minutes')
       
    3. On checkout completion:
       UPDATE inventory SET quantity = quantity - 1 WHERE product_id = 789
       DELETE FROM reservations WHERE product_id = 789 AND user_id = 42
       
    4. On reservation timeout:
       DELETE FROM reservations WHERE expires_at < NOW()
       INCR inventory:product:789  (return stock to Redis)

  Why Lua script?
    Redis DECR can go negative! We need check + decrement atomically.
    Lua ensures: "Only decrement if > 0."
```

### Inventory Lua Script (Production Grade)

```lua
-- Atomic inventory reservation
-- Returns: 1 = reserved, 0 = out of stock, -1 = already reserved by this user

local inventory_key = KEYS[1]            -- inventory:product:789
local reservation_key = KEYS[2]          -- reservations:product:789
local user_id = ARGV[1]
local quantity = tonumber(ARGV[2]) or 1
local ttl = tonumber(ARGV[3]) or 600     -- 10 minute reservation

-- Check if user already has a reservation
local existing = redis.call('SISMEMBER', reservation_key, user_id)
if existing == 1 then
    return -1  -- Already reserved
end

-- Check and decrement inventory
local stock = tonumber(redis.call('GET', inventory_key) or 0)
if stock >= quantity then
    redis.call('DECRBY', inventory_key, quantity)
    redis.call('SADD', reservation_key, user_id)
    -- Set user's reservation expiry
    redis.call('SET', 'reservation:' .. user_id .. ':' .. KEYS[1], quantity, 'EX', ttl)
    return 1  -- RESERVED
else
    return 0  -- OUT OF STOCK
end
```

### Inventory Reconciliation

```
Redis inventory can drift from the database (reservations expire, bugs, etc.)

Reconciliation job (every 5 minutes):
  1. DB_count = SELECT quantity FROM inventory WHERE product_id = 789
  2. Active_reservations = SELECT COUNT(*) FROM reservations 
                           WHERE product_id = 789 AND expires_at > NOW()
  3. Correct_redis = DB_count - Active_reservations
  4. SET inventory:product:789 {Correct_redis}
  
This ensures Redis always reflects the database truth.
```

---

## CRDTs for Multi-Region Counters

### The Multi-Region Problem

```
Users in US-East and EU-West both like the same post simultaneously.

  US-East Redis: INCR likes:post:12345 → 33108
  EU-West Redis: INCR likes:post:12345 → 33109

  Which is correct? Both! They're independent events.
  Total should be 33108 + 33109 - 33107 (base) = 33110

  Cross-region replication of INCR operations:
    Option A: Single-leader (all writes go to one region) → high latency for remote users
    Option B: Multi-leader (each region writes independently) → conflicts!
    Option C: CRDT (conflict-free merge) → eventual consistency, no conflicts
```

### G-Counter (Grow-Only Counter)

```
A CRDT counter where each node maintains its own count.
Total = sum of all node counts. Merge = max of each node's count.

  State:
    node_counts = { "us-east": 15000, "eu-west": 12000, "ap-south": 6110 }
    total = 15000 + 12000 + 6110 = 33110

  Increment at us-east:
    node_counts["us-east"] += 1 → 15001
    total = 15001 + 12000 + 6110 = 33111

  Merge (on replication):
    merged["us-east"] = max(local["us-east"], remote["us-east"])
    merged["eu-west"] = max(local["eu-west"], remote["eu-west"])
    merged["ap-south"] = max(local["ap-south"], remote["ap-south"])
    
    Max-based merge is IDEMPOTENT and COMMUTATIVE.
    No conflicts. No coordination needed.
    
  Total = sum of all max values.
```

### PN-Counter (Supports Decrements)

```
A G-Counter only grows. For unlike/decrement, use a PN-Counter:
  P = positive counter (increments)
  N = negative counter (decrements)
  Value = P - N

  State:
    P = { "us-east": 15000, "eu-west": 12000 }  (likes)
    N = { "us-east": 500, "eu-west": 300 }       (unlikes)
    
    Total likes = (15000 + 12000) - (500 + 300) = 26200

  Like at us-east:  P["us-east"] += 1 → 15001
  Unlike at eu-west: N["eu-west"] += 1 → 301
  
  Merge: max() on each node's P and N independently.
  Result: Correct total, no conflicts, works across regions.
```

### Implementation with Redis

```python
class CRDTCounter:
    """PN-Counter CRDT using Redis hashes."""
    
    def __init__(self, redis, key, node_id):
        self.redis = redis
        self.key = key
        self.node_id = node_id
    
    def increment(self, amount=1):
        self.redis.hincrby(f"{self.key}:P", self.node_id, amount)
    
    def decrement(self, amount=1):
        self.redis.hincrby(f"{self.key}:N", self.node_id, amount)
    
    def get_value(self):
        p_counts = self.redis.hgetall(f"{self.key}:P")
        n_counts = self.redis.hgetall(f"{self.key}:N")
        
        total_p = sum(int(v) for v in p_counts.values())
        total_n = sum(int(v) for v in n_counts.values())
        return total_p - total_n
    
    def merge(self, remote_p, remote_n):
        """Merge remote state (from another region's replication)."""
        pipe = self.redis.pipeline()
        for node, count in remote_p.items():
            local = int(self.redis.hget(f"{self.key}:P", node) or 0)
            if count > local:
                pipe.hset(f"{self.key}:P", node, count)
        for node, count in remote_n.items():
            local = int(self.redis.hget(f"{self.key}:N", node) or 0)
            if count > local:
                pipe.hset(f"{self.key}:N", node, count)
        pipe.execute()
```

---

## Idempotency and Deduplication

### The Double-Count Problem

```
User clicks "Like" → request hits app server → INCR counter.
Network timeout → user retries → INCR counter again.
Result: 2 increments for 1 like. Double-counted!

At scale:
  1% of requests retry due to timeouts.
  33K likes/sec × 1% = 330 phantom likes/sec.
  Over 24 hours: 28.5M phantom likes. Significant!
```

### Deduplication with User Sets

```
Before incrementing, check if the user already liked:

  SISMEMBER likers:post:12345 user:42
  If YES → already liked, ignore.
  If NO → SADD likers:post:12345 user:42 + INCR likes:post:12345

  Lua script (atomic check + increment):
    local is_member = redis.call('SISMEMBER', KEYS[1], ARGV[1])  -- likers set
    if is_member == 0 then
        redis.call('SADD', KEYS[1], ARGV[1])
        redis.call('INCR', KEYS[2])  -- counter
        return 1  -- Liked
    end
    return 0  -- Already liked (deduplicated)

Tradeoff:
  Memory: SET stores every user_id who liked.
    10M likes × 8 bytes per user_id = 80 MB per post.
    For a viral post with 100M likes: 800 MB → expensive!

Optimization for high-cardinality:
  Use a Bloom Filter instead of a Set:
    BF.ADD likers:post:12345 user:42
    BF.EXISTS likers:post:12345 user:42
    Memory: ~1.2 bytes per element (vs 8 bytes for Set).
    False positive rate: 1% → 1% of duplicate likes still counted.
    100M likes → 120 MB (vs 800 MB for Set).
```

### Request-Level Idempotency

```
For critical operations (inventory), use idempotency keys:

  POST /api/like
  Headers: Idempotency-Key: "req-abc123xyz"
  
  Server:
    1. SETNX idempotency:req-abc123xyz "processing" EX 60
    2. If SET (key didn't exist) → process the like
    3. If NOT SET (key exists) → duplicate request → return cached result
    
  Idempotency keys auto-expire after 60 seconds.
  Prevents any duplicate within the 60-second window.
```

---

## Hot Key Detection and Mitigation

### Detecting Hot Keys

```
Metric: Operations per second per key.

  Monitor with Redis MONITOR or redis-cli --hotkeys (Redis 4.0+).
  Alert threshold: Key with > 50K ops/sec → hot key.

  Automated detection:
    Track INCR frequency per key (sampled).
    If key exceeds threshold → auto-shard.

  ┌──────────────────────────────────────────────┐
  │  Hot Key Detection Pipeline                   │
  │                                               │
  │  INCR event → sample 1% → count per key      │
  │  key_ops_sec > 50K → trigger auto-sharding    │
  │  key_ops_sec < 10K for 5 min → merge shards   │
  └──────────────────────────────────────────────┘
```

### Auto-Sharding on Detection

```python
class AdaptiveShardedCounter:
    """Automatically shards hot counters."""
    
    DEFAULT_SHARDS = 1
    HOT_SHARDS = 8
    COOL_DOWN_SECONDS = 300
    
    def __init__(self, redis):
        self.redis = redis
        self.shard_counts = {}  # key → current shard count
    
    def increment(self, key, amount=1):
        num_shards = self._get_shard_count(key)
        
        if num_shards == 1:
            return self.redis.incrby(key, amount)
        else:
            shard = random.randint(0, num_shards - 1)
            return self.redis.incrby(f"{key}:shard:{shard}", amount)
    
    def promote_to_sharded(self, key):
        """Called when hot key detected. Migrates to sharded counter."""
        current_value = int(self.redis.get(key) or 0)
        
        # Distribute current value across shards
        per_shard = current_value // self.HOT_SHARDS
        remainder = current_value % self.HOT_SHARDS
        
        pipe = self.redis.pipeline()
        for i in range(self.HOT_SHARDS):
            value = per_shard + (1 if i < remainder else 0)
            pipe.set(f"{key}:shard:{i}", value)
        pipe.delete(key)  # Remove original key
        pipe.execute()
        
        self.shard_counts[key] = self.HOT_SHARDS
    
    def demote_to_single(self, key):
        """Called when traffic drops. Merges shards back to single key."""
        total = 0
        pipe = self.redis.pipeline()
        for i in range(self.HOT_SHARDS):
            pipe.get(f"{key}:shard:{i}")
        results = pipe.execute()
        total = sum(int(v or 0) for v in results)
        
        pipe = self.redis.pipeline()
        pipe.set(key, total)
        for i in range(self.HOT_SHARDS):
            pipe.delete(f"{key}:shard:{i}")
        pipe.execute()
        
        self.shard_counts[key] = 1
```

---

## Negative Counters — Unlikes and Decrements

### The Unlike Problem

```
User likes → INCR → count goes up.
User unlikes → DECR → count goes down.

Edge cases:
  1. Count goes negative (more unlikes than likes):
     Shouldn't happen, but bugs or race conditions can cause it.
     Defense: max(0, count) on read.
     
  2. Unlike without prior like (client bug):
     User sends "unlike" without ever liking.
     Defense: Check membership before decrement (dedup set).
     
  3. Concurrent like + unlike:
     User taps like/unlike rapidly.
     Both arrive at server simultaneously.
     Defense: Lua script with check → idempotent.
```

### Atomic Like/Unlike Lua Script

```lua
-- Like or unlike with deduplication
-- KEYS[1] = likers set (likers:post:12345)
-- KEYS[2] = counter key (likes:post:12345)
-- ARGV[1] = user_id
-- ARGV[2] = action ("like" or "unlike")

local likers_key = KEYS[1]
local counter_key = KEYS[2]
local user_id = ARGV[1]
local action = ARGV[2]

if action == "like" then
    local added = redis.call('SADD', likers_key, user_id)
    if added == 1 then
        -- New like (not already in set)
        redis.call('INCR', counter_key)
        return 1  -- liked
    end
    return 0  -- already liked (no-op)
    
elseif action == "unlike" then
    local removed = redis.call('SREM', likers_key, user_id)
    if removed == 1 then
        -- Was in set, remove and decrement
        local count = tonumber(redis.call('GET', counter_key) or 0)
        if count > 0 then
            redis.call('DECR', counter_key)
        end
        return 1  -- unliked
    end
    return 0  -- wasn't liked (no-op)
end

return -1  -- invalid action
```

---

## Observability and Monitoring

### Key Metrics

```
Counter Operations:
  counter.increment_rate{key_pattern}    = counter  # Increments per second
  counter.read_rate{key_pattern}         = counter  # Reads per second
  counter.hot_key_count                  = gauge    # Keys above threshold

Redis Health:
  redis.ops_per_sec{shard}              = gauge
  redis.memory_used_bytes{shard}         = gauge
  redis.key_count{shard}                 = gauge

Rollup Health:
  rollup.lag_seconds                     = gauge    # Time since last successful rollup
  rollup.delta_sum                       = gauge    # Total unrolled deltas
  rollup.failure_count                   = counter

Consistency:
  counter.redis_vs_db_drift{entity}      = gauge    # Difference between Redis and DB
  counter.reconciliation_corrections     = counter  # Fixes applied per reconciliation cycle
```

### Alerting Rules

```
CRITICAL:
  rollup.lag_seconds > 300          → Rollup worker is stuck
  redis.ops_per_sec > 250K/shard    → Shard approaching capacity
  counter.redis_vs_db_drift > 1000  → Significant drift

WARNING:
  counter.hot_key_count > 0         → Hot keys detected, verify sharding
  rollup.failure_count > 0/5min     → DB write issues
  redis.memory_used > 80%           → Memory pressure
```

---

## Performance Tuning & Benchmarks

### Redis Counter Performance

```
Single key INCR:
  Throughput: ~300K ops/sec per Redis instance
  Latency: p50 = 0.08ms, p99 = 0.3ms

Sharded counter (N=8):
  Write throughput: 8 × 300K = ~2.4M ops/sec (across 8 shards)
  Read throughput: 300K pipeline ops/sec (8 GETs in one pipeline)
  Read latency: p50 = 0.2ms (pipeline), p99 = 0.5ms

Local buffering + periodic flush (1s interval):
  App-level throughput: Unlimited (memory-bound)
  Redis write load: Reduced by 1000-10000x
  Redis effective throughput: ~50 INCRBY/sec per server × 50 servers = 2.5K/sec
  
Combined (buffering + sharding):
  Handles ANY write volume. Bottleneck shifts to memory/CPU, not Redis.
```

### Database Rollup Performance

```
PostgreSQL batch UPDATE:
  1000 rows per batch: ~500 batches/sec = 500K counter updates/sec
  Using UNNEST: 10x faster than individual UPDATEs
  
  Rollup interval: 30 seconds
  Updates per cycle: 50 servers × 100K active counters = 5M deltas → 5K batches
  Rollup duration: ~10 seconds (within 30-second window)
```

---

## Real-World Production Patterns

### Pattern 1: Instagram Like Count

```
Scale: 2B+ likes/day, 100M+ posts with active like counts

Architecture:
  Like event → App server → Memcached buffer (2s) → Redis INCR → Cassandra rollup
  
  Read: Memcached (cached total) → Redis → Cassandra (fallback)
  
  Sharding: Per-post counter, no sharding for most posts.
  Hot posts (celebrities): Auto-shard to 16 sub-counters.
  
  Display: Rounded ("1.5M likes") for posts > 10K likes.
  Exact: Only shown for posts < 10K likes.
```

### Pattern 2: YouTube View Count

```
Scale: 500M+ hours watched/day, billions of view increments

Architecture:
  View event → Kafka → Flink aggregation → Spanner (source of truth)
  
  Real-time count: Pre-aggregated in-memory cache, refreshed every 5 seconds.
  Deduplication: Per-user view deduplicated within 30-minute window.
  Anti-fraud: Views from bots detected and subtracted in batch.
  
  Special: View count is frozen during verification for new videos.
  "301 views" bug (now fixed): Counter paused at 301 for fraud verification.
```

### Pattern 3: Twitter Retweet/Like Count

```
Scale: 500K tweets/min, each with like + retweet + reply counters

Architecture:
  Redis cluster for real-time counters.
  Manhattan (Twitter's custom KV store) for persistence.
  
  Fan-out: When tweet is retweeted, engagement counters update on the original tweet.
  Hot tweets: During events (Super Bowl), auto-shard counters.
  
  Consistency: Eventual — counters may differ by a few hundred for seconds.
```

### Pattern 4: E-Commerce Inventory (Amazon)

```
Scale: 500M+ products, 100K+ concurrent checkouts

Architecture:
  Inventory: DynamoDB with atomic conditional updates.
    UpdateItem: SET quantity = quantity - 1 IF quantity > 0
    Native atomic conditional decrement — no Lua needed.
    
  Redis cache: Read-through cache for product page display.
  Cache invalidation: On successful purchase, invalidate Redis key.
  
  Consistency: STRONG. No overselling. DynamoDB conditional write is the gate.
  
  Sharding: Not needed — DynamoDB handles single-item throughput up to 40K WCU
  (with on-demand scaling).
```

---

## Interview Talking Points & Scripts

### Script 1: Sharded Counter

> *"A single counter key is a hot key bottleneck — one Redis shard handles all writes. I'd shard the counter into N sub-keys: `likes:post:{id}:shard:{0..N-1}`. Each write picks a random shard, spreading load across N keys. The total is the sum of all shards. I'd start with N=1 for normal posts, auto-scale to N=8 when hot key detection triggers at >50K ops/sec, and merge back when traffic drops. Read cost is N pipelined GETs — at N=8, that's 0.3ms pipelined, imperceptible."*

### Script 2: Local Buffering

> *"Instead of sending every like directly to Redis, I'd buffer locally on each app server and flush once per second. With 50 servers each accumulating likes, that's 50 INCRBY/sec instead of 33K INCR/sec — a 660x reduction in Redis operations. The tradeoff is up to 1 second of staleness, which is invisible for social media like counts. For read-your-own-writes, I add the local buffer delta to the Redis read."*

### Script 3: Write-Behind to DB

> *"Redis is the fast write layer, but the database is the durable truth. A rollup worker runs every 30 seconds: it atomically reads and resets each counter's delta from Redis (using a Lua GETSET), then batch-updates the database with `UPDATE posts SET like_count = like_count + delta`. If the DB write fails, the delta isn't reset — it accumulates until the next successful rollup. The DB always catches up."*

### Script 4: Inventory (Strong Consistency)

> *"Inventory counters are fundamentally different from vanity metrics — overselling is unacceptable. I'd use a Redis Lua script for atomic check-and-decrement: read stock, verify > 0, decrement, all in one atomic operation. The database is the source of truth — Redis is a fast gate. A reservation TTL of 10 minutes prevents abandoned carts from permanently consuming inventory. A reconciliation job runs every 5 minutes to sync Redis back to the database."*

### Script 5: Multi-Region with CRDTs

> *"For a globally distributed counter, I'd use a PN-Counter CRDT. Each region maintains its own increment count and decrement count. The total value is the sum of all increments minus the sum of all decrements across all regions. Merging is conflict-free: for each region's counter, take the max. This is commutative and idempotent, so replicas converge without coordination. The tradeoff is eventual consistency — a like in US-East takes a few hundred milliseconds to be visible in EU-West."*

### Script 6: Deduplication

> *"To prevent double-counting from retries, I'd use a Redis Set alongside the counter: SADD checks if the user already liked, and only if SADD returns 1 (new member), do I INCR the counter — all in an atomic Lua script. For viral posts with 100M+ likes, the Set costs 800 MB, so I'd switch to a Bloom Filter at 1.2 bytes per element (120 MB) with a 1% false positive rate — meaning 1% of duplicate likes might still count, which is acceptable for vanity metrics."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Just use Redis INCR"

**Bad**: Single key for a viral post → hot key bottleneck at 100K+ ops/sec.
**Fix**: Shard the counter into N sub-keys. Auto-scale N based on traffic.

### ❌ Mistake 2: "Store counters only in the database"

**Bad**: `UPDATE posts SET like_count = like_count + 1` at 33K/sec → row lock contention.
**Fix**: Redis for writes, database as durable backup via periodic rollup.

### ❌ Mistake 3: Treating all counters the same

**Bad**: Same eventual-consistency pattern for likes AND inventory.
**Fix**: Like counts → eventual consistency (sharded, buffered). Inventory → strong consistency (Lua check-and-decrement, DB as truth).

### ❌ Mistake 4: No deduplication

**Bad**: Network retry causes double increment.
**Fix**: Lua script: SADD to check membership + INCR only on new member. Or idempotency keys.

### ❌ Mistake 5: Reading sharded counter by summing on every request

**Bad**: 8 GET operations on every page load → 8x read amplification.
**Fix**: Maintain a cached total (approximate). Periodically reconcile with shard sum.

### ❌ Mistake 6: No rollup to database

**Bad**: Counters only in Redis → Redis crash = all counts lost.
**Fix**: Periodic rollup to database (every 30 seconds). DB is the durable source of truth.

### ❌ Mistake 7: Ignoring multi-region

**Bad**: Single-leader counter → all writes go to one region → high latency for remote users.
**Fix**: PN-Counter CRDT for multi-leader writes. Eventual convergence, no conflicts.

### ❌ Mistake 8: Not mentioning display rounding

**Bad**: Showing exact "33,108" for a rapidly changing counter → visual flickering.
**Fix**: Round to "33.1K" — stable display, negligible inaccuracy.

---

## Distributed Counter by System Design Problem

| Problem | Counter Type | Consistency | Pattern | Special Considerations |
|---|---|---|---|---|
| **Instagram likes** | Vanity | Eventual | Sharded + buffered + rollup | Dedup with set/bloom, round display |
| **YouTube views** | Vanity | Eventual | Kafka → Flink → Spanner | Anti-fraud verification, dedup by user+window |
| **Twitter retweets** | Vanity | Eventual | Sharded Redis + rollup | Hot tweets auto-shard |
| **E-commerce inventory** | Inventory | Strong | Lua check-and-decrement + DB | Reservation TTL, reconciliation |
| **Concert ticket seats** | Inventory | Strong | Pessimistic lock in DB | Zero overselling tolerance |
| **API rate limiting** | Rate | Near-real-time | Redis INCR + TTL window | ±5% acceptable, per-window keys |
| **Ad impression count** | Billing | Eventual (seconds) | Flink aggregation + ClickHouse | Exactly-once, reconciliation |
| **Follower count** | Vanity | Eventual | Buffered counter + DB | Bidirectional (follow/unfollow) |
| **Real-time poll votes** | Voting | Eventual → Exact | Sharded + periodic exact sum | Display partial → then final count |
| **Game score/leaderboard** | Metric | Near-real-time | Redis sorted set (not counter) | ZADD for ranked leaderboard |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│            DISTRIBUTED COUNTER AT SCALE — CHEAT SHEET                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  PROBLEM: Single counter key = hot key bottleneck at 100K+ ops/sec   │
│                                                                      │
│  PATTERN 1 — SHARDED COUNTER:                                        │
│    Split into N sub-keys: key:shard:{0..N-1}                         │
│    Write: INCR random shard → spreads load N ways                    │
│    Read: SUM all shards (pipelined) or cached total_approx           │
│    Auto-scale N: 1 for normal, 8-16 for viral, merge when cool      │
│                                                                      │
│  PATTERN 2 — LOCAL BUFFERING:                                        │
│    Accumulate in-memory per app server, flush every 1 second.        │
│    50 servers → 50 INCRBY/sec instead of 33K INCR/sec.              │
│    Tradeoff: 1-second staleness. Fine for vanity metrics.            │
│                                                                      │
│  PATTERN 3 — WRITE-BEHIND TO DB:                                     │
│    Redis = fast writes. DB = durable truth.                          │
│    Rollup every 30 seconds: GETSET delta → batch UPDATE DB.          │
│    Fallback: Read from DB if Redis is down.                          │
│                                                                      │
│  INVENTORY (STRONG CONSISTENCY):                                     │
│    Lua script: Check stock > 0, THEN decrement. Atomic.              │
│    DB = source of truth. Redis = fast gate.                          │
│    Reservation TTL (10 min). Reconciliation every 5 min.             │
│                                                                      │
│  DEDUPLICATION:                                                      │
│    Set: SADD likers_set user_id → INCR only if new member.          │
│    Bloom Filter: 1.2 bytes/element, 1% FP, for 100M+ cardinality.   │
│    Idempotency keys: SETNX req_id EX 60 → prevent duplicate ops.    │
│                                                                      │
│  MULTI-REGION:                                                       │
│    PN-Counter CRDT: P(increments) - N(decrements) per region.        │
│    Merge: max() per region. Conflict-free. Eventually consistent.    │
│                                                                      │
│  READ STRATEGIES:                                                    │
│    Exact: SUM all shards (pipelined) → 0.3ms for N=8.               │
│    Fast: GET total_approx → 0.1ms, reconciled every 5 min.          │
│    Display: Round to "33.1K" for rapidly changing counters.          │
│                                                                      │
│  PERFORMANCE:                                                        │
│    Single INCR: ~300K ops/sec per Redis instance.                    │
│    Sharded (N=8): ~2.4M ops/sec.                                    │
│    Buffered: Unlimited app throughput, 50 Redis ops/sec/server.      │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 12: Sharding** — Counter sharding is a specific application of key sharding
- **Topic 15: Rate Limiting** — Rate limit counters are a special case of distributed counters
- **Topic 21: Concurrency Control** — Inventory counters need pessimistic or CAS-based control
- **Topic 31: Hot Partitions** — Hot counter keys are the canonical hot key problem
- **Topic 38: Bloom Filters** — Space-efficient deduplication for high-cardinality like sets
- **Topic 40: Redis Deep Dive** — INCR, Lua scripting, pipelining, Cluster
- **Topic 54: Distributed Rate Limiting** — Token Bucket uses Redis counters internally

---

*This document is part of the System Design Interview Deep Dive series.*
