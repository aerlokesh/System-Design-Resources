# 🎯 Topic 40: Redis Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Redis data structures, key design patterns, distributed locks (Redlock), Pub/Sub, persistence (RDB vs AOF), Redis Cluster, eviction policies, Lua scripting, rate limiting, session management, leaderboards, and how to articulate Redis design decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Redis Architecture — Single-Threaded Model](#redis-architecture--single-threaded-model)
3. [Data Structures and Use Cases](#data-structures-and-use-cases)
4. [Key Design Patterns](#key-design-patterns)
5. [Distributed Locks with Redis](#distributed-locks-with-redis)
6. [Redis Pub/Sub](#redis-pubsub)
7. [Persistence — RDB vs AOF](#persistence--rdb-vs-aof)
8. [Redis Cluster and Sharding](#redis-cluster-and-sharding)
9. [Replication and High Availability](#replication-and-high-availability)
10. [Eviction Policies](#eviction-policies)
11. [Lua Scripting for Atomic Operations](#lua-scripting-for-atomic-operations)
12. [Common Redis Patterns in System Design](#common-redis-patterns-in-system-design)
13. [Performance Characteristics and Numbers](#performance-characteristics-and-numbers)
14. [Redis vs Memcached vs DynamoDB DAX](#redis-vs-memcached-vs-dynamodb-dax)
15. [Real-World System Examples](#real-world-system-examples)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Redis** (Remote Dictionary Server) is an in-memory data structure store used as a cache, database, message broker, and streaming engine. Its sub-millisecond latency and rich data structures make it the most commonly used component in system design interviews — appearing in nearly every design for caching, rate limiting, leaderboards, sessions, presence, and distributed coordination.

```
Why Redis appears in almost every system design:

  Database query: SELECT * FROM users WHERE id = 123 → 2-10ms
  Redis lookup:   GET user:123 → 0.1-0.5ms (10-100x faster)

  PostgreSQL: 10-50K reads/sec per instance
  Redis: 100K+ reads/sec per instance (10x more)

  Redis cost: $200/month for 50GB
  Equivalent DB capacity: $3,000/month in read replicas

  Result: Redis is the highest-ROI component in most architectures.
```

---

## Redis Architecture — Single-Threaded Model

### Why Single-Threaded?

```
Redis processes commands in a SINGLE thread:

  Client A: SET key1 "value1"   ← processed
  Client B: GET key2            ← waits
  Client C: INCR counter        ← waits
  
  Only ONE command executes at a time.
  No locks needed. No race conditions. Perfect consistency per operation.

Why this is FAST (not slow):
  1. All data in memory (no disk I/O on reads)
  2. Simple data structures (O(1) for most operations)
  3. No context switching (single thread = no overhead)
  4. Epoll-based I/O multiplexing (handles 10K+ connections efficiently)
  5. Each operation completes in microseconds (not milliseconds)
  
  At 1 microsecond per operation: 1,000,000 operations/sec (theoretical max)
  Practical: ~100K-300K operations/sec per Redis instance.

Redis 6.0+ threading:
  I/O threading: Multiple threads handle network I/O (reading/writing sockets)
  Command execution: Still single-threaded (atomic, no races)
  Result: ~2x throughput improvement for I/O-heavy workloads.
```

### Memory Architecture

```
Redis stores ALL data in RAM:
  Pros: Sub-millisecond latency, predictable performance
  Cons: Limited by available RAM, expensive per GB

Memory usage per data type:
  String (key + value): ~90 bytes overhead + actual data
  Hash (small, < 128 fields): ~200 bytes + entries (ziplist encoding)
  Hash (large, > 128 fields): ~800 bytes + entries (hashtable encoding)
  Sorted Set (small): ~200 bytes + entries (ziplist)
  Sorted Set (large): ~800 bytes + entries (skiplist)
  
  Rule of thumb: 
    1 million keys with 100-byte values ≈ 200 MB
    10 million keys with 100-byte values ≈ 2 GB
    100 million keys with 100-byte values ≈ 20 GB

Memory optimization:
  Use hashes for small objects (Redis optimizes with ziplist encoding)
  Set maxmemory-policy for eviction when full
  Use TTL aggressively (auto-delete expired data)
  Compress values before storing (snappy/gzip for large values)
```

---

## Data Structures and Use Cases

### String — The Swiss Army Knife

```
GET / SET / INCR / DECR / EXPIRE

Use cases:
  Caching:      SET user:123 '{"name":"Alice","avatar":"..."}' EX 3600
  Counters:     INCR page_views:homepage:2025-03-15
  Rate limiting: INCR api_calls:user_123:minute_14 → if > 100 → reject
  Flags:        SET feature:dark_mode "enabled"
  Locks:        SET lock:order_123 "worker_7" NX EX 30

Operations: O(1) for all basic operations
Memory: ~90 bytes overhead per key
Throughput: ~100K operations/sec
```

### Hash — Object Storage

```
HSET / HGET / HMSET / HMGET / HINCRBY / HGETALL

Store structured objects without serialization/deserialization:
  HSET user:123 name "Alice" age 30 city "NYC" avatar_url "cdn.com/alice.jpg"
  HGET user:123 name → "Alice"
  HMGET user:123 name avatar_url → ["Alice", "cdn.com/alice.jpg"]
  HINCRBY user:123 age 1 → 31 (atomic increment)

vs String approach:
  SET user:123 '{"name":"Alice","age":30,"city":"NYC"}'
  GET user:123 → deserialize → change age → serialize → SET user:123 ...
  
  Hash advantage: Update individual fields without read-modify-write.
  String advantage: Simpler for read-heavy workloads (one GET returns all).

Use cases:
  User profiles: HSET user:{id} field value
  Shopping carts: HSET cart:{user_id} product_id quantity
  Pre-computed aggregates: HINCRBY campaign:123:2025-03-15 clicks 1
  Configuration: HSET config:{service} feature value

Operations: O(1) per field, O(N) for HGETALL
Memory: Extremely efficient for < 128 fields (ziplist encoding)
```

### List — Queues and Recent Items

```
LPUSH / RPUSH / LPOP / RPOP / LRANGE / BRPOP / LTRIM

Use cases:
  Message queue:     LPUSH queue:emails '{"to":"alice","subject":"..."}'
                     BRPOP queue:emails 30  (blocking pop, waits up to 30s)
  
  Recent activity:   LPUSH recent:user_123 '{"action":"viewed_product","id":456}'
                     LTRIM recent:user_123 0 99  (keep only last 100)
                     LRANGE recent:user_123 0 19  (get last 20)
  
  Timeline (simple): LPUSH timeline:user_123 tweet_id
                     LRANGE timeline:user_123 0 49  (latest 50 tweets)

Operations: O(1) for push/pop, O(N) for LRANGE with large ranges
Pattern: LPUSH + LTRIM = bounded queue (never exceeds N items)
```

### Set — Unique Collections

```
SADD / SREM / SISMEMBER / SMEMBERS / SINTER / SUNION / SDIFF / SCARD

Use cases:
  Tags:             SADD post:123:tags "funny" "viral" "trending"
                    SISMEMBER post:123:tags "funny" → true
  
  Unique tracking:  SADD visitors:2025-03-15 user_id (one SADD per visit)
                    SCARD visitors:2025-03-15 → 1,234,567 unique visitors
  
  Mutual friends:   SINTER friends:alice friends:bob 
                    → {"charlie", "dave"} (O(min(N,M)))
  
  Online users:     SADD online_users user_id
                    SREM online_users user_id (on logout)
                    SCARD online_users → 42,000 online

Operations: O(1) for add/remove/member-check, O(N) for SMEMBERS
Pattern: SINTER for "mutual friends" — intersection of two sets
```

### Sorted Set — Leaderboards and Priority Queues

```
ZADD / ZREM / ZSCORE / ZRANK / ZREVRANK / ZRANGE / ZREVRANGE / ZRANGEBYSCORE

THE most important Redis data structure for system design interviews.

Leaderboard:
  ZADD leaderboard 5000 "alice"    (score: 5000, member: alice)
  ZADD leaderboard 7500 "bob"
  ZADD leaderboard 3200 "charlie"
  
  Top 10:       ZREVRANGE leaderboard 0 9 WITHSCORES → Bob(7500), Alice(5000), ...
  Alice's rank: ZREVRANK leaderboard "alice" → 1 (0-indexed, so 2nd place)
  Alice's score: ZSCORE leaderboard "alice" → 5000
  
  All operations: O(log N) — for 100M users, ~27 operations internally.

Time-sorted feed:
  ZADD timeline:user_123 {timestamp} {tweet_id}
  ZREVRANGEBYSCORE timeline:user_123 +inf {min_timestamp} LIMIT 0 50
  → Latest 50 tweets since min_timestamp

Rate limiting (sliding window):
  ZADD rate:user_123 {current_timestamp} {request_id}
  ZREMRANGEBYSCORE rate:user_123 0 {current_timestamp - 60}  (remove old entries)
  ZCARD rate:user_123  → count of requests in last 60 seconds
  If count > 100 → reject (rate limit exceeded)

Delayed job queue:
  ZADD delayed_jobs {execute_at_timestamp} {job_payload}
  ZRANGEBYSCORE delayed_jobs 0 {now} LIMIT 0 10  → jobs ready to execute
  ZREM delayed_jobs {job_payload}  → remove after processing

Operations: O(log N) for most operations, O(log N + M) for range queries
Memory: ~100 bytes per member (ziplist) to ~200 bytes (skiplist)
```

### HyperLogLog — Approximate Unique Counting

```
PFADD / PFCOUNT / PFMERGE

Count unique items using only 12 KB of memory (regardless of cardinality):

  PFADD visitors:homepage "user_1"
  PFADD visitors:homepage "user_2"
  PFADD visitors:homepage "user_1"  (duplicate, no change)
  PFCOUNT visitors:homepage → ~2 (±0.81% error)

  PFMERGE total visitors:homepage visitors:products visitors:checkout
  PFCOUNT total → unique visitors across all pages

Memory: 12 KB per HyperLogLog (constant!)
Error: ±0.81%
Use: Unique visitor counting, unique search query counting
```

### Bloom Filter (Redis Module)

```
BF.ADD / BF.EXISTS / BF.RESERVE

  BF.RESERVE crawled_urls 0.0001 10000000000  (0.01% FP, 10B capacity)
  BF.ADD crawled_urls "https://example.com/page1"
  BF.EXISTS crawled_urls "https://example.com/page1" → 1 (probably exists)
  BF.EXISTS crawled_urls "https://example.com/page2" → 0 (definitely NOT)

Memory: ~1.5 GB for 10B items at 0.01% FP rate
Use: URL dedup, click dedup, cache miss optimization
```

---

## Key Design Patterns

### Naming Conventions

```
Best practice: Use colons as separators, hierarchical structure.

Pattern: {entity}:{id}:{field_or_subresource}

Examples:
  user:123                    → User profile (string or hash)
  user:123:friends            → Set of friend IDs
  user:123:timeline           → Sorted set of tweet IDs
  session:abc-def-ghi         → Session data
  cache:product:456           → Cached product data
  rate:api:user_123:minute    → Rate limit counter
  lock:order:789              → Distributed lock
  online:user_123             → Presence indicator
  leaderboard:global          → Global leaderboard sorted set
  counter:campaign:123:clicks → Click counter
  queue:emails                → Email processing queue

Anti-patterns:
  ❌ Very long keys: "this:is:a:very:long:key:that:wastes:memory"
  ❌ Spaces in keys: "user 123" (confusing, hard to debug)
  ❌ No structure: "abc123" (what is this?)
```

### TTL Strategies

```
Always set TTL on cache entries:

  SET cache:product:456 '{"name":"Widget",...}' EX 3600  (1 hour)
  SETEX session:abc 1800 '{"user_id":123,...}'           (30 minutes)
  
  HSET user:123 name "Alice"
  EXPIRE user:123 86400  (1 day — entire hash expires as unit)

TTL guidelines by data type:
  Session data:       30 minutes (refresh on activity)
  API cache:          60 seconds (frequently changing data)
  Product catalog:    1 hour (relatively stable)
  User profile cache: 10 minutes (moderate change frequency)
  CDN config cache:   5 minutes
  Feature flags:      30 seconds (near-real-time updates)
  Presence (online):  70 seconds (refreshed by heartbeat every 30s)
  Rate limit window:  60 seconds (sliding window)

No TTL = memory leak:
  Without TTL, data stays forever → Redis fills up → eviction or OOM.
  ALWAYS set TTL unless you have a specific reason not to.
```

### Key Expiration and Lazy Deletion

```
How Redis handles expired keys:

Passive expiration:
  Key is accessed → Redis checks TTL → if expired → delete + return nil
  Keys not accessed stay in memory even after TTL!

Active expiration (background):
  Every 100ms: Redis randomly samples 20 expired keys
  If > 25% are expired → repeat immediately (more keys to clean)
  If < 25% → wait for next cycle
  
  Result: Expired keys are cleaned up within a few seconds of expiry.
  But not instantly — brief window where expired data is in memory.

UNLINK vs DEL:
  DEL key: Synchronous deletion (blocks Redis for large keys)
  UNLINK key: Asynchronous deletion (key removed, memory freed in background)
  
  For large keys (sets with 1M members): Always use UNLINK.
```

---

## Distributed Locks with Redis

### Basic SETNX Lock

```
Acquire lock:
  SET lock:order_123 "worker_7" NX EX 30
  NX = Only set if key doesn't exist (atomic check-and-set)
  EX 30 = Auto-expire after 30 seconds (prevents deadlock if worker crashes)

  If SET returns OK → lock acquired ✅
  If SET returns nil → lock held by someone else ❌ → retry or back off

Release lock (safe release):
  -- Only release if I still own it (Lua script for atomicity)
  if redis.call("GET", "lock:order_123") == "worker_7" then
      return redis.call("DEL", "lock:order_123")
  end
  
  Why Lua? GET + DEL must be atomic.
  Without Lua: GET returns "worker_7" → another worker acquires lock → DEL removes THEIR lock!
```

### Lock Safety Issues

```
Problem 1: Lock expiry during processing
  Worker acquires lock (TTL=30s) → processing takes 45 seconds
  Lock expires at 30s → another worker acquires same lock
  Two workers processing same order simultaneously!
  
  Solution: Lease renewal
  Background thread renews TTL every 10 seconds while processing.
  If worker crashes → no renewal → lock expires naturally → safe.

Problem 2: Redis failover loses the lock
  Worker writes lock to Redis master
  Master crashes before replicating to replica
  Replica promoted to master → lock doesn't exist!
  Another worker acquires "same" lock → two workers!
  
  Solution: Fencing tokens (see Topic 32)
  Storage layer rejects writes from stale lock holders.

Problem 3: Clock skew
  Worker's clock is 5 seconds ahead of Redis
  Worker thinks lock still has 25 seconds remaining
  Redis expires lock after 30 seconds (its time)
  Worker continues working for 5 seconds without lock
  
  Solution: Use lock TTL conservatively. Stop work well before expected expiry.
```

### Redlock (Multi-Instance Distributed Lock)

```
Redlock algorithm (by Redis author, Salvatore Sanfilippo):

  5 independent Redis instances (NOT replicas — independent masters)
  
  Acquire:
    1. Get current time T1
    2. Try to acquire lock on ALL 5 instances (with short timeout each)
    3. Lock acquired if: ≥ 3 of 5 succeeded AND total time < lock TTL
    4. Effective TTL = original TTL - time spent acquiring
    
  Release:
    Send DEL to ALL 5 instances (even those where acquisition failed)

  Why 5 independent instances?
    If 2 crash → still have 3 → majority → lock is valid
    No single point of failure

Controversy (Martin Kleppmann):
  Kleppmann argues Redlock is still unsafe due to:
  - Clock skew between instances
  - GC pauses causing lock to expire during use
  - Recommends fencing tokens for true safety
  
  Practical recommendation:
    Non-critical: Simple SETNX on single Redis (simple, good enough)
    Critical: ZooKeeper/etcd (consensus-based, provably safe)
    Redlock: More complex than SETNX but still not as safe as ZK/etcd
```

### When to Use Redis Locks

```
✅ Good use cases:
  Prevent duplicate processing of the same event
  Coordinate cache rebuild (only one server rebuilds)
  Rate limit expensive operations (one at a time)
  Prevent concurrent modifications to shared state

❌ Bad use cases (use ZooKeeper/etcd instead):
  Leader election for critical coordination
  Financial transaction locking
  Database master designation
  
  Rule: If lock failure means DATA LOSS or FINANCIAL HARM → don't use Redis.
        If lock failure means DUPLICATE WORK → Redis is fine.
```

---

## Redis Pub/Sub

### How It Works

```
Publisher:   PUBLISH channel:chat_room_123 '{"user":"alice","msg":"Hello!"}'
Subscriber:  SUBSCRIBE channel:chat_room_123

Publisher sends message → Redis delivers to ALL subscribers of that channel.
Subscribers receive messages in real-time (< 1ms latency within Redis).

Pattern subscription:
  PSUBSCRIBE channel:chat_room_*
  → Receives messages from ALL chat rooms
```

### Pub/Sub Limitations

```
Fire-and-forget:
  If subscriber is disconnected when message is published → message LOST.
  No persistence. No replay. No message queue semantics.
  
  Not suitable for: Guaranteed message delivery
  Suitable for: Real-time notifications where occasional loss is OK.

No consumer groups:
  Every subscriber gets every message (broadcast, not load-balanced).
  Can't have 3 workers cooperating to process messages (like Kafka).

Alternative for guaranteed delivery:
  Redis Streams (XADD / XREAD / XREADGROUP) — persistent, consumer groups
  Kafka — full-featured event streaming
```

### Redis Streams (Alternative to Pub/Sub)

```
Redis Streams (5.0+) provide Kafka-like features:

  XADD stream:orders * order_id 123 status "created"
  → Appends entry to stream (like Kafka produce)

  XREADGROUP GROUP analytics consumer_1 COUNT 10 STREAMS stream:orders >
  → Reads entries from consumer group (like Kafka consumer group)

  XACK stream:orders analytics entry_id
  → Acknowledges processing (like Kafka offset commit)

Benefits over Pub/Sub:
  ✅ Persistent (entries stay in stream)
  ✅ Consumer groups (load-balanced processing)
  ✅ Acknowledgment (at-least-once delivery)
  ✅ Replay (read from any position)

Limitations vs Kafka:
  ❌ No built-in replication across nodes (Redis Cluster handles this)
  ❌ Not designed for >100K messages/sec sustained (Kafka is better)
  ❌ No compaction
```

---

## Persistence — RDB vs AOF

### RDB (Redis Database Snapshots)

```
RDB creates point-in-time snapshots of the entire dataset:

  save 900 1      (snapshot if 1+ key changed in 900 seconds)
  save 300 10     (snapshot if 10+ keys changed in 300 seconds)
  save 60 10000   (snapshot if 10K+ keys changed in 60 seconds)

How it works:
  Redis forks a child process → child writes entire dataset to .rdb file
  Parent continues serving requests (copy-on-write, minimal impact)
  
Pros:
  ✅ Compact single file (easy to backup/transfer)
  ✅ Fast restarts (load .rdb into memory)
  ✅ Minimal performance impact (child process does the work)
  
Cons:
  ❌ Data loss between snapshots (if crash between saves, lose recent writes)
  ❌ Fork can be slow for large datasets (>10GB → fork takes seconds)
  
Best for: Backup, disaster recovery, cache (where some data loss is OK)
```

### AOF (Append-Only File)

```
AOF logs every write operation:

  appendonly yes
  appendfsync everysec  (fsync every second — recommended)
  
  Every SET, INCR, ZADD → appended to AOF file
  On restart: Redis replays the entire AOF to rebuild state.

appendfsync options:
  always:   fsync after EVERY write (safest, slowest — ~10K ops/sec)
  everysec: fsync every second (good balance — ~100K ops/sec, max 1s data loss)
  no:       Let OS decide when to fsync (fastest, risk of 30s data loss)

Pros:
  ✅ Minimal data loss (at most 1 second with everysec)
  ✅ Human-readable file (can inspect/edit commands)
  ✅ AOF rewrite compacts the file (removes redundant operations)
  
Cons:
  ❌ Larger file than RDB (every operation logged)
  ❌ Slower restart (must replay all operations)
  ❌ Write amplification (every command written to disk)

Best for: Data that must survive restart (sessions, queues, locks)
```

### Recommended: Both RDB + AOF

```
appendonly yes        (AOF for durability — max 1s data loss)
save 900 1            (RDB for fast backup)

On restart: Redis loads from AOF (more complete) if available.
RDB serves as backup if AOF is corrupted.

For pure caching (no persistence needed):
  appendonly no
  save ""              (disable RDB)
  Fastest performance. Data lost on restart → rebuilt from source of truth.
```

---

## Redis Cluster and Sharding

### Redis Cluster Architecture

```
Redis Cluster: Automatic sharding across multiple Redis nodes.

  16,384 hash slots divided among master nodes:
  
  Master 1: Slots 0-5460       (+ Replica 1A)
  Master 2: Slots 5461-10922   (+ Replica 2A)
  Master 3: Slots 10923-16383  (+ Replica 3A)

  Key → slot mapping: CRC16(key) % 16384 = slot number
  
  SET user:123 → CRC16("user:123") % 16384 = 7291 → Master 2
  GET user:456 → CRC16("user:456") % 16384 = 2100 → Master 1

Client routing:
  Smart client: Knows the slot mapping → sends to correct master directly
  Redirect: If client sends to wrong node → MOVED redirect to correct node
```

### Hash Tags for Co-Location

```
Problem: MGET user:123:name user:123:age
  If user:123:name → slot 7291 (Master 2)
  And user:123:age → slot 4500 (Master 1)
  → Cross-slot operation! Redis Cluster rejects this.

Solution: Hash tags — force keys to the same slot:
  {user:123}:name → CRC16("user:123") → same slot
  {user:123}:age  → CRC16("user:123") → same slot!
  
  Redis hashes only the part inside {} for slot assignment.
  All keys with {user:123} → guaranteed same slot → same node.

Use cases:
  Multi-field operations on same entity
  Lua scripts that touch multiple keys (must be on same node)
  Transactions (MULTI/EXEC) across multiple keys
```

### Scaling Redis Cluster

```
Adding a node:
  1. Add Master 4 to cluster
  2. Redis Cluster migrates some slots from Masters 1-3 to Master 4
  3. During migration: Clients are redirected (ASK redirect)
  4. After migration: Normal operation with 4 masters

Removing a node:
  1. Migrate all slots from Master 4 to remaining masters
  2. Remove Master 4 from cluster
  3. Data redistributed without downtime

Capacity planning:
  Each master: ~25 GB recommended maximum (for fast failover, backup)
  100 GB total: 4 masters × 25 GB + 4 replicas = 8 nodes
  1 TB total: 40 masters + 40 replicas = 80 nodes
```

---

## Replication and High Availability

### Master-Replica Replication

```
Master: Accepts reads and writes
Replica: Read-only copy, replicates from master asynchronously

  Master writes: SET user:123 "Alice"
  → Replication stream to Replica
  → Replica applies: SET user:123 "Alice" (lag: 0.1-10ms)

Read scaling:
  3 replicas → 4x read throughput
  Route read-heavy queries to replicas
  Route writes to master only

Replication lag:
  Typical: < 1ms (same datacenter)
  Under load: 1-10ms
  Network issues: Can grow to seconds
  
  Stale reads from replica are possible during lag window.
  For strong consistency: Read from master only.
```

### Redis Sentinel (HA without Cluster)

```
Sentinel monitors masters and performs automatic failover:

  3-5 Sentinel instances monitoring one master + replicas
  
  Master fails → Sentinels detect (heartbeat timeout)
  Majority vote: "Is master really down?" (prevents false positives)
  Promote one replica to master
  Reconfigure other replicas to follow new master
  Notify clients of new master address

Failover timeline:
  T+0s:    Master crashes
  T+5-10s: Sentinels detect (heartbeat miss)
  T+10-15s: Majority vote confirms failure
  T+15-20s: Replica promoted to master
  T+20-25s: Clients reconnected to new master
  
  Total: 15-25 seconds of downtime for writes
  Reads from replicas: Unaffected (still serving during failover)
```

---

## Eviction Policies

### When Memory Is Full

```
maxmemory 50gb
maxmemory-policy allkeys-lru  (recommended for caching)

Policies:

  noeviction: Return error on new writes when memory is full
    Use: When data loss is unacceptable (sessions, locks)

  allkeys-lru: Evict least recently used key from ALL keys
    Use: General caching (most common)

  allkeys-lfu: Evict least frequently used key from ALL keys
    Use: When access patterns are stable (hot items accessed often)

  volatile-lru: Evict least recently used key that has TTL set
    Use: Mix of persistent and cache data

  volatile-ttl: Evict key with shortest remaining TTL
    Use: When you want TTL to control eviction priority

  allkeys-random: Evict random key
    Use: When all keys are equally important (rare)

Recommendation:
  Pure cache: allkeys-lru (simple, effective)
  Mixed use: volatile-lru (only evict cache data, preserve persistent data)
  Frequency-based: allkeys-lfu (better hit rate for skewed access patterns)
```

---

## Lua Scripting for Atomic Operations

### Why Lua?

```
Problem: Redis operations are atomic individually, but NOT across operations.

  STEP 1: GET counter → 99
  STEP 2: IF 99 < 100 THEN INCR counter
  
  Between step 1 and 2, another client might INCR counter to 100!
  Race condition: Counter goes to 101 (exceeds limit).

Solution: Lua script executes atomically (single-threaded, no interleaving):

  EVAL "
    local current = tonumber(redis.call('GET', KEYS[1]) or '0')
    if current < tonumber(ARGV[1]) then
      return redis.call('INCR', KEYS[1])
    else
      return -1  -- limit reached
    end
  " 1 counter 100

  This runs as ONE atomic operation. No race conditions.
```

### Common Lua Patterns

```
Rate limiting (sliding window, atomic):
  EVAL "
    local key = KEYS[1]
    local limit = tonumber(ARGV[1])
    local window = tonumber(ARGV[2])
    local now = tonumber(ARGV[3])
    
    redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
    local count = redis.call('ZCARD', key)
    
    if count < limit then
      redis.call('ZADD', key, now, now .. ':' .. math.random())
      redis.call('EXPIRE', key, window)
      return 1  -- allowed
    else
      return 0  -- rejected
    end
  " 1 rate:user_123 100 60 {current_timestamp}

Safe lock release (atomic check-and-delete):
  EVAL "
    if redis.call('GET', KEYS[1]) == ARGV[1] then
      return redis.call('DEL', KEYS[1])
    else
      return 0
    end
  " 1 lock:order_123 worker_7
```

---

## Common Redis Patterns in System Design

### Pattern 1: Cache-Aside (Most Common)

```
Read:
  1. Check Redis: GET cache:product:456
  2. If HIT → return cached data
  3. If MISS → query database → SET cache:product:456 '...' EX 3600 → return

Write:
  1. Update database
  2. DELETE cache:product:456 (invalidate, not update)
  3. Next read triggers cache rebuild

Why DELETE not SET on write?
  Race condition: Two concurrent writes might cache stale data.
  DELETE is always safe: next read rebuilds from source of truth.
```

### Pattern 2: Session Storage

```
Login:
  session_id = generate_uuid()
  SETEX session:{session_id} 1800 '{"user_id":123,"role":"admin",...}'
  Set-Cookie: session_id={session_id}; HttpOnly; Secure

Request:
  session_id = request.cookies["session_id"]
  session = GET session:{session_id}
  If nil → redirect to login (expired or invalid)
  EXPIRE session:{session_id} 1800  (refresh TTL on each request)

Logout:
  DEL session:{session_id}

Why Redis for sessions?
  Sub-ms lookups (every request checks session)
  Auto-expiration via TTL (no cleanup jobs needed)
  Shared across all API servers (stateless servers)
```

### Pattern 3: Rate Limiting (Sliding Window)

```
Sliding window with sorted sets:

  Each request: ZADD rate:user_123 {timestamp} {request_id}
  Remove old: ZREMRANGEBYSCORE rate:user_123 0 {timestamp - 60}
  Count: ZCARD rate:user_123
  If count > 100: Reject (429 Too Many Requests)
  Set TTL: EXPIRE rate:user_123 60

Advantages over simple counter:
  No boundary issues (counter resets at minute boundary → burst at boundary)
  Smooth rate limiting across any 60-second window
```

### Pattern 4: Leaderboard

```
Update score:
  ZADD leaderboard 5000 "alice"
  ZADD leaderboard 7500 "bob"
  ZINCRBY leaderboard 100 "alice"  → alice now 5100

Top 10:
  ZREVRANGE leaderboard 0 9 WITHSCORES
  → [("bob", 7500), ("alice", 5100), ...]

User's rank:
  ZREVRANK leaderboard "alice" → 1 (0-indexed)

Around-me (nearby ranks):
  rank = ZREVRANK leaderboard "alice"  → 1
  ZREVRANGE leaderboard (rank-5) (rank+5) WITHSCORES
  → Players ranked 4th to 6th place around alice

All operations: O(log N) — scales to 100M+ users.
```

### Pattern 5: Presence (Online/Offline)

```
Heartbeat (every 30 seconds):
  SET online:user_123 1 EX 70  (70s TTL, 2 missed heartbeats = offline)

Check one user:
  GET online:user_123 → "1" (online) or nil (offline)

Check many users (batch):
  MGET online:user_1 online:user_2 ... online:user_500
  → [1, nil, 1, 1, nil, ...] in < 2ms

Last seen:
  SET last_seen:user_123 {timestamp}  (no TTL)
  On disconnect: Update last_seen, let online key expire
```

### Pattern 6: Pub/Sub for Real-Time

```
Cross-gateway messaging (WebSocket architecture):
  Gateway 7 has Alice connected
  Gateway 12 has Bob connected
  
  Alice sends message to Bob:
  Gateway 7 → PUBLISH gateway:12 '{"to":"bob","msg":"Hello!"}'
  Gateway 12 subscribes to "gateway:12" → receives → pushes to Bob

  Latency: ~1-5ms (Redis Pub/Sub is very fast)
```

---

## Performance Characteristics and Numbers

| Operation | Complexity | Latency | Throughput |
|---|---|---|---|
| GET/SET | O(1) | 0.1-0.5ms | 100K+/sec |
| HGET/HSET | O(1) | 0.1-0.5ms | 100K+/sec |
| LPUSH/RPOP | O(1) | 0.1-0.5ms | 100K+/sec |
| SADD/SISMEMBER | O(1) | 0.1-0.5ms | 100K+/sec |
| ZADD | O(log N) | 0.1-1ms | 50K+/sec |
| ZRANGE (100 items) | O(log N + 100) | 0.2-1ms | 30K+/sec |
| ZRANK | O(log N) | 0.1-1ms | 50K+/sec |
| MGET (100 keys) | O(100) | 0.5-2ms | 10K+/sec |
| Lua script | Depends | 0.1-5ms | Varies |
| PFADD/PFCOUNT | O(1) | 0.1-0.5ms | 100K+/sec |

### Pipeline for Bulk Operations

```
Without pipeline: 100 commands × 0.5ms RTT = 50ms total
With pipeline: 100 commands batched in 1 RTT = 0.5ms + processing

  pipe = redis.pipeline()
  for i in range(100):
      pipe.get(f"key:{i}")
  results = pipe.execute()  // One network round trip for 100 commands

Throughput improvement: 100x for bulk operations
Use: Batch reads, bulk cache warming, mass updates
```

---

## Redis vs Memcached vs DynamoDB DAX

| Feature | Redis | Memcached | DynamoDB DAX |
|---|---|---|---|
| **Data structures** | Strings, hashes, lists, sets, sorted sets, streams | Strings only | DynamoDB items |
| **Persistence** | RDB + AOF | None | N/A (cache for DynamoDB) |
| **Replication** | Master-replica | None (client-side) | Built-in |
| **Clustering** | Redis Cluster (auto-sharding) | Client-side consistent hashing | Managed |
| **Pub/Sub** | Yes | No | No |
| **Lua scripting** | Yes | No | No |
| **Max memory** | Unlimited (cluster) | 64-bit addressing | Managed |
| **Threading** | Single (6.0+ I/O threads) | Multi-threaded | Managed |
| **Best for** | General caching + data structures | Simple key-value caching | DynamoDB acceleration |

```
Choose Redis when:
  ✅ Need data structures (sorted sets, hashes, lists)
  ✅ Need Pub/Sub or Streams
  ✅ Need persistence (survive restarts)
  ✅ Need Lua scripting for atomic operations
  ✅ Need distributed locks

Choose Memcached when:
  ✅ Simple key-value caching only
  ✅ Multi-threaded performance is critical
  ✅ Don't need persistence or data structures
  ✅ Already using Memcached (migration cost not worth it)
```

---

## Real-World System Examples

### Twitter — Timeline Cache

```
Pre-computed timelines stored in Redis sorted sets:
  ZADD timeline:user_123 {timestamp} {tweet_id}
  
Feed load:
  ZREVRANGE timeline:user_123 0 49 → Latest 50 tweets in < 1ms
  
Scale: Hundreds of millions of timelines in Redis cluster
```

### Instagram — Session + Caching

```
Redis for: Session storage, activity feed, like counts, caching
Scale: Multiple Redis clusters, terabytes of data in memory
Pattern: Cache-aside for frequently accessed data
```

### Discord — Presence + Messaging

```
Redis for: Online presence (who's in a voice channel)
           Real-time message routing (Pub/Sub)
           Permission caching (channel access checks)
Scale: Millions of concurrent users, sub-ms latency required
```

### Uber — Geospatial + Rate Limiting

```
Redis for: Driver location caching (GEOADD/GEOPOS)
           Rate limiting (sliding window per rider/driver)
           Trip state caching
Scale: Millions of location updates per second
```

---

## Interview Talking Points & Scripts

### Why Redis

> *"I'd use Redis as the caching layer because it gives us sub-millisecond reads — 0.1ms vs 5ms from PostgreSQL. At 200K reads/sec, a 3-node Redis cluster at $600/month replaces 20 PostgreSQL read replicas at $30,000/month. Beyond simple caching, Redis's sorted sets give us O(log N) leaderboard operations, and its atomic INCR gives us rate limiting without race conditions."*

### Distributed Lock

> *"For preventing duplicate order processing, I'd use a Redis distributed lock: SETNX with a 30-second TTL. The worker acquires the lock, processes the order, and releases it with a Lua script that checks ownership before deleting. If the worker crashes, the TTL ensures the lock auto-releases after 30 seconds. I'd use fencing tokens on the downstream database to prevent stale lock holders from corrupting data."*

### Data Structure Choice

> *"For the leaderboard, I'd use a Redis sorted set — ZADD for score updates in O(log N), ZREVRANGE for top-100 in O(log N + 100), and ZREVRANK for any user's rank in O(log N). For 100 million users, that's about 27 internal operations per query — all in-memory, sub-millisecond. No other data structure gives you sorted insert, rank lookup, and range query all in logarithmic time."*

### Persistence Choice

> *"I'd configure Redis with both RDB and AOF: AOF with everysec sync for minimal data loss (at most 1 second), and RDB snapshots for fast backup. For pure caching where data can be rebuilt from the source of truth, I'd disable persistence entirely for maximum performance."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd store everything in Redis"
**Why it's wrong**: Redis stores data in RAM ($25/GB/month). Storing 1TB in Redis costs $25,000/month vs $23 in S3.
**Better**: Redis for hot data only. Cold data in database/S3.

### ❌ Mistake 2: Not mentioning TTL on cache entries
**Why it's wrong**: Without TTL, cache grows indefinitely → OOM or eviction of important data.
**Better**: Always set TTL. Mention specific values based on data freshness requirements.

### ❌ Mistake 3: Using Redis locks for critical financial operations
**Why it's wrong**: Redis's async replication can cause split-lock during failover.
**Better**: Use ZooKeeper/etcd for critical locks. Redis for non-critical coordination.

### ❌ Mistake 4: Not mentioning eviction policy
**Why it's wrong**: When Redis fills up, the eviction policy determines what gets deleted.
**Better**: "allkeys-lru for pure caching, volatile-lru for mixed persistent+cache data."

### ❌ Mistake 5: Ignoring the single-threaded model
**Why it's wrong**: Long-running Lua scripts or large KEYS commands block ALL other operations.
**Better**: Keep Lua scripts short. Use SCAN instead of KEYS. Use UNLINK instead of DEL for large keys.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                  REDIS DEEP DIVE CHEAT SHEET                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ARCHITECTURE: In-memory, single-threaded, sub-ms latency    │
│  THROUGHPUT: ~100K ops/sec per instance                      │
│  LATENCY: 0.1-0.5ms per operation                            │
│                                                              │
│  DATA STRUCTURES:                                            │
│    String: Caching, counters, flags, locks                   │
│    Hash: User profiles, configs, aggregates                  │
│    List: Queues, recent items, activity feeds                │
│    Set: Tags, unique tracking, mutual friends (SINTER)       │
│    Sorted Set: Leaderboards, timelines, rate limiting         │
│    HyperLogLog: Unique counting (12 KB, ±0.81%)              │
│    Bloom Filter: Membership test (module)                    │
│    Stream: Kafka-like persistent messaging                   │
│                                                              │
│  KEY DESIGN: {entity}:{id}:{field} — user:123:profile        │
│  TTL: ALWAYS set on cache entries. 1s-24h depending on data. │
│                                                              │
│  DISTRIBUTED LOCKS:                                          │
│    SETNX + EX (TTL) for basic locking                        │
│    Lua script for safe release (check ownership + delete)    │
│    Fencing tokens for write safety                           │
│    Non-critical: Redis. Critical: ZooKeeper/etcd.            │
│                                                              │
│  PERSISTENCE:                                                │
│    RDB: Snapshots (backup, fast restart)                     │
│    AOF: Every write logged (max 1s data loss with everysec)  │
│    Both: Recommended for production                          │
│    Neither: Pure caching (maximum performance)               │
│                                                              │
│  CLUSTER: 16,384 hash slots across masters                   │
│    Hash tags {entity:id} for key co-location                 │
│    Max ~25 GB per master recommended                         │
│                                                              │
│  EVICTION: allkeys-lru (caching), volatile-lru (mixed)       │
│                                                              │
│  PATTERNS: Cache-aside, sessions, rate limiting,             │
│    leaderboards, presence, Pub/Sub, distributed locks        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 5: Caching Strategies** — Cache-aside, write-through patterns with Redis
- **Topic 6: Cache Invalidation** — TTL, event-driven invalidation in Redis
- **Topic 15: Rate Limiting** — Redis-based rate limiting algorithms
- **Topic 31: Hot Partitions & Hot Keys** — Redis hot key mitigation
- **Topic 32: Leader Election** — Redis vs ZooKeeper for locks
- **Topic 37: WebSocket & Real-Time** — Redis Pub/Sub for cross-gateway messaging
- **Topic 39: Kafka Deep Dive** — Kafka vs Redis Streams comparison

---

*This document is part of the System Design Interview Deep Dive series.*
