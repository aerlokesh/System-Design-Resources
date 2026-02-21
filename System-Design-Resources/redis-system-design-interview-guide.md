# Redis — System Design Interview Guide

> **Purpose**: Everything you need to know about Redis for system design interviews. No code — concepts, data structures, patterns, and real examples from HLD problems.

---

## Table of Contents

1. [What Is Redis & When to Mention It](#1-what-is-redis--when-to-mention-it)
2. [Core Data Structures & Interview Use Cases](#2-core-data-structures--interview-use-cases)
3. [Redis as a Cache](#3-redis-as-a-cache)
4. [Redis as a Primary Data Store](#4-redis-as-a-primary-data-store)
5. [Redis Persistence & Durability](#5-redis-persistence--durability)
6. [Redis Cluster & Scaling](#6-redis-cluster--scaling)
7. [Redis Pub/Sub & Streams](#7-redis-pubsub--streams)
8. [Common Redis Patterns in HLD](#8-common-redis-patterns-in-hld)
9. [Redis vs Alternatives](#9-redis-vs-alternatives)
10. [Sizing & Back-of-Envelope Numbers](#10-sizing--back-of-envelope-numbers)
11. [Failure Scenarios](#11-failure-scenarios)
12. [Redis Anti-Patterns](#12-redis-anti-patterns)
13. [Interview Examples by Problem](#13-interview-examples-by-problem)
14. [Quick Reference Card](#14-quick-reference-card)

---

## 1. What Is Redis & When to Mention It

### One-Liner Definition
> Redis is an **in-memory key-value data store** with sub-millisecond latency, rich data structures (strings, hashes, sorted sets, lists), and support for caching, pub/sub, rate limiting, and real-time counters.

### When to Introduce Redis in an Interview

| Situation | Why Redis? | Example |
|-----------|-----------|---------|
| Need sub-millisecond reads | Redis serves from memory | "User profile cache — avoid hitting DB on every page load" |
| Need a cache layer | Classic use case | "Cache tweet data, user profiles, timeline entries" |
| Need atomic counters | `INCR` is atomic and O(1) | "Like count, view count, rate limiter token count" |
| Need sorted/ranked data | Sorted Sets (ZSET) | "Leaderboard — `ZADD` to update, `ZREVRANGE` for top-100" |
| Need temporary holds with auto-expiry | Keys with TTL | "Seat reservation hold — 10 min TTL, auto-releases" |
| Need distributed locks | `SETNX` with TTL | "Only one worker processes a job at a time" |
| Need session storage | Fast read/write with TTL | "User sessions — 1 hour TTL, auto-logout" |
| Need pub/sub for real-time | Built-in Pub/Sub | "Route WebSocket messages between gateway servers" |
| Need a rate limiter | Atomic operations + TTL | "Token bucket — `HINCRBY` for token count" |

### When NOT to Use Redis

| Situation | Use Instead | Why |
|-----------|-------------|-----|
| Data must survive restarts (primary store) | PostgreSQL / DynamoDB | Redis is memory-first — persistence is secondary |
| Data > available RAM | Cassandra / DynamoDB | Redis is limited by memory |
| Need complex queries (JOINs, GROUP BY) | SQL database | Redis is key-based lookup only |
| Need durable message queue | Kafka / SQS | Redis Pub/Sub is fire-and-forget (messages lost if no subscriber) |
| Need full-text search | Elasticsearch | Redis doesn't support inverted indexes natively |

---

## 2. Core Data Structures & Interview Use Cases

### 2.1 String

**What**: Simple key → value. Supports `SET`, `GET`, `INCR`, `DECR`, `SETNX`, `SETEX`.

**Interview Uses**:

| Use Case | Command | Example |
|----------|---------|---------|
| **Cache a value** | `SET user:123 "{json}"` + `EXPIRE 3600` | Cache user profile for 1 hour |
| **Atomic counter** | `INCR tweet:456:likes` | Increment like count — atomic, no race condition |
| **Distributed lock** | `SET lock:order:789 "worker1" NX EX 30` | Acquire lock for 30 seconds. `NX` = only if not exists |
| **Rate limiter (fixed window)** | `INCR ratelimit:user:123:min:1430` + `EXPIRE 60` | Count requests this minute. Key auto-expires. |
| **Idempotency check** | `SET idem:payment:abc 1 NX EX 3600` | If SET succeeds → new request. If fails → duplicate. |
| **OTP storage** | `SETEX otp:+1234567890 300 "123456"` | Store OTP for 5 minutes, auto-deletes |

**Example in Interview — Rate Limiter**:
> "For rate limiting, I'd use Redis strings with atomic `INCR`. The key is `ratelimit:{user_id}:{minute}`, and I increment on each request. If the count exceeds 100, reject with 429. The key auto-expires after 60 seconds via TTL. This is the Fixed Window approach — simple and effective for most APIs."

---

### 2.2 Hash

**What**: Key → field/value map. Like a mini document. Supports `HSET`, `HGET`, `HGETALL`, `HINCRBY`.

**Interview Uses**:

| Use Case | Command | Example |
|----------|---------|---------|
| **Cache structured data** | `HSET user:123 name "Alice" email "a@b.com" age 30` | Cache user profile as hash fields |
| **Token bucket rate limiter** | `HSET ratelimit:user:123 tokens 100 last_refill 1704672000` | Store bucket state per user |
| **Session storage** | `HSET session:abc user_id 123 role "admin" created_at 170467` | Store session fields with one key |
| **Tweet metrics** | `HINCRBY tweet:456:metrics likes 1` | Atomically increment a specific metric field |
| **Feature flags** | `HSET features:user:123 dark_mode 1 beta_search 0` | Per-user feature toggles |

**Example in Interview — Token Bucket**:
> "Each user's rate limit state is a Redis hash: `ratelimit:{user_id}` with fields `tokens` and `last_refill`. On each request, I calculate how many tokens to add since `last_refill`, cap at max, then try to consume one. `HINCRBY` gives me atomic field-level increments. The whole check-and-update runs as a Lua script for atomicity."

---

### 2.3 List

**What**: Ordered collection of strings. Supports `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`, `LLEN`.

**Interview Uses**:

| Use Case | Command | Example |
|----------|---------|---------|
| **Message queue (simple)** | `LPUSH queue:jobs "job123"` / `RPOP queue:jobs` | FIFO job queue |
| **Undelivered messages** | `LPUSH undelivered:bob "msg_abc"` | Queue messages for offline users |
| **Recent activity feed** | `LPUSH activity:user:123 "liked tweet 456"` + `LTRIM 0 99` | Keep last 100 activities |
| **Notification inbox** | `LPUSH notifications:user:123 "{...}"` | Push notifications, pop when read |

**Example in Interview — Offline Message Queue (WhatsApp)**:
> "When Bob is offline, I push message IDs to `undelivered:{bob_id}` using `LPUSH`. When Bob reconnects, I fetch all with `LRANGE undelivered:{bob_id} 0 -1`, deliver them in order, then delete with `DEL`. This is a simple FIFO queue per user — no need for Kafka here since it's per-user temporary storage."

---

### 2.4 Set

**What**: Unordered collection of unique strings. Supports `SADD`, `SISMEMBER`, `SMEMBERS`, `SINTER`, `SUNION`, `SCARD`.

**Interview Uses**:

| Use Case | Command | Example |
|----------|---------|---------|
| **Track unique visitors** | `SADD visitors:page:home:2025-01-10 "user_123"` | Count unique visitors per day |
| **Group members (cached)** | `SADD group:family:members "alice" "bob" "charlie"` | Cache group membership for fast lookup |
| **User's liked tweets** | `SADD user:123:liked_tweets "tweet_456" "tweet_789"` | Check if user liked a tweet: `SISMEMBER` O(1) |
| **Online users** | `SADD online_users "user_123"` | Track who's online, `SCARD` for count |
| **Mutual friends** | `SINTER user:123:friends user:456:friends` | Find common friends in one command |
| **Dedup check** | `SADD seen_events "evt_abc"` | Returns 0 if already exists (duplicate) |

**Example in Interview — "Has User Liked This Tweet?"**:
> "To check if a user has liked a tweet, I maintain a Redis set `user:{user_id}:liked_tweets`. When they like, `SADD` adds the tweet_id. When rendering a tweet, `SISMEMBER` checks in O(1). This avoids a database query on every tweet render — critical when displaying a timeline of 50 tweets."

---

### 2.5 Sorted Set (ZSET) ⭐ Most Important for Interviews

**What**: Like a Set, but each member has a **score**. Sorted by score. Supports `ZADD`, `ZREVRANGE`, `ZRANGEBYSCORE`, `ZRANK`, `ZINCRBY`, `ZCARD`, `ZPOPMIN`.

**Interview Uses**:

| Use Case | Command | Example |
|----------|---------|---------|
| **Leaderboard** | `ZADD leaderboard 1500 "alice"` / `ZREVRANGE leaderboard 0 9` | Top-10 players by score |
| **Timeline** | `ZADD timeline:user:123 1704672000 "tweet_456"` | Timeline sorted by timestamp |
| **Trending topics** | `ZADD trending:global 95.3 "#WorldCup"` | Trending hashtags sorted by trend score |
| **Rate limiter (sliding window)** | `ZADD ratelimit:user:123 1704672000.123 "req_uuid"` | Each request is a member with timestamp as score |
| **Virtual queue (flash sale)** | `ZADD queue:event:123 1704672000123 "user_456"` | FIFO queue by join timestamp |
| **Delayed job queue** | `ZADD jobs:delayed 1704675600 "job_789"` | Jobs sorted by scheduled execution time |
| **Scheduled hold expiration** | `ZADD holds:expiring 1704672600 "reservation_abc"` | Reservations sorted by expiry time |

**Example in Interview — Leaderboard**:
> "I'd use a Redis Sorted Set `leaderboard:{game_id}`. When a player scores, `ZINCRBY leaderboard:game1 50 'alice'` atomically adds 50 to Alice's score. To get the top 100: `ZREVRANGE leaderboard:game1 0 99 WITHSCORES`. To get Alice's rank: `ZREVRANK leaderboard:game1 'alice'`. All O(log N) operations. For 10M players, each operation takes < 1ms."

**Example in Interview — Twitter Timeline**:
> "Each user's timeline is a Sorted Set `timeline:{user_id}`. Score = tweet timestamp, member = tweet_id. When a followed user tweets, the Fanout Worker does `ZADD timeline:{follower_id} {timestamp} {tweet_id}` for each follower. To read the timeline: `ZREVRANGE timeline:{user_id} 0 49` returns the 50 most recent tweet IDs. Then we hydrate with tweet details from cache/DB."

**Example in Interview — Virtual Queue**:
> "For the Ticketmaster flash sale queue, I'd use `ZADD queue:{event_id} {timestamp_ms} {user_id}`. Score = join time, so it's naturally FIFO. Every 10 seconds, the admission worker does `ZPOPMIN queue:{event_id} 1000` to admit the next 1000 users. A user checks their position with `ZRANK queue:{event_id} {user_id}`."

---

### 2.6 HyperLogLog

**What**: Probabilistic cardinality estimation. Counts unique items using ~12 KB regardless of cardinality. ~0.81% error rate.

**Interview Uses**:

| Use Case | Command | Example |
|----------|---------|---------|
| **Unique page views** | `PFADD pageviews:homepage:2025-01-10 "user_123"` | Count unique visitors (~12 KB per page per day) |
| **Unique ad impressions** | `PFADD ad:impressions:camp_456 "user_789"` | Approximate unique impression count |
| **Count distinct events** | `PFADD events:type:click "evt_abc"` | How many unique click events? |

**Example in Interview — Unique View Count**:
> "For counting unique viewers of a video, I'd use HyperLogLog — `PFADD views:video:123 {user_id}` for each view, `PFCOUNT views:video:123` for the count. It uses only 12 KB per video regardless of whether 100 or 100 million users viewed it. The tradeoff is ~0.81% error — 'approximately 1.2M views' is fine for a UI counter."

---

### 2.7 Bitmap

**What**: Bit array operations. Supports `SETBIT`, `GETBIT`, `BITCOUNT`.

**Interview Uses**:

| Use Case | Command | Example |
|----------|---------|---------|
| **Daily active users** | `SETBIT dau:2025-01-10 {user_id_as_int} 1` | Bit per user — `BITCOUNT` for total DAU |
| **Feature rollout tracking** | `SETBIT feature:dark_mode {user_id} 1` | Track which users have feature enabled |
| **User activity calendar** | `SETBIT activity:user:123:2025-01 {day} 1` | Which days in January was user active? |

**Example in Interview — Daily Active Users**:
> "To count daily active users, I set a bit at position `user_id` in `dau:{date}`. With 100M users, this bitmap is only ~12.5 MB. `BITCOUNT dau:2025-01-10` returns the exact DAU. To find weekly active users: `BITOP OR wau dau:2025-01-10 dau:2025-01-09 ... dau:2025-01-04` then `BITCOUNT wau`."

---

## 3. Redis as a Cache

### Cache-Aside Pattern (Most Common)

```
Request → Check Redis → HIT? Return cached data
                      → MISS? Query DB → Store in Redis (with TTL) → Return
```

**When to use**: Read-heavy workloads where stale data for a few seconds/minutes is acceptable.

### What to Cache (with TTL examples)

| Data | TTL | Why |
|------|-----|-----|
| User profiles | 1 hour | Rarely changes, read on every page |
| Tweet data | 5 minutes | Metrics (likes) change frequently |
| Timeline (pre-computed) | 2 days | Rebuilt on new tweets anyway |
| Search results | 30 seconds | Quickly stale, but reduces ES load |
| Session tokens | 1 hour | Security — auto-logout |
| Rate limit state | Matches window | 60 seconds for per-minute limits |
| Seat availability | 2 seconds | Must be fresh for booking, but 250K QPS is too much for DB |

### Cache Invalidation Approaches

| Approach | How | Example |
|----------|-----|---------|
| **TTL only** | Set expiry, accept staleness | `SETEX user:123 3600 "{json}"` — stale for up to 1 hour |
| **TTL + event** | TTL as safety net, Kafka event for immediate invalidation | On profile update → Kafka event → consumer calls `DEL user:123` |
| **Write-through** | Update cache on every write | On tweet like: `HINCRBY tweet:456:metrics likes 1` (cache) + DB write |
| **Write-invalidate** | Delete cache on write, next read repopulates | On profile update: `DEL user:123` → next GET triggers cache-aside |

**Example in Interview**:
> "I'd use cache-aside with a 5-minute TTL for tweet data. On every read, check Redis first. On cache miss, query Cassandra and populate Redis. For invalidation, I'd combine TTL (safety net) with Kafka event-driven invalidation — when a tweet's like count changes, a Kafka consumer deletes the cached entry so the next read gets fresh data."

---

## 4. Redis as a Primary Data Store

Redis can be the **source of truth** for certain ephemeral/real-time data that doesn't need long-term persistence:

| Data | Why Redis is Primary | Persistence Needed? |
|------|---------------------|-------------------|
| **Online presence** | `online:{user_id}` with 70s TTL, refreshed by heartbeats | No — ephemeral by nature |
| **Typing indicators** | `typing:{conv_id}:{user_id}` with 5s TTL | No — auto-expires |
| **Active WebSocket sessions** | `session:{user_id}:{device_id}` → gateway server | No — reconnect recreates |
| **Rate limit counters** | `ratelimit:{user_id}:{window}` | No — resets each window |
| **Pre-computed timelines** | `timeline:{user_id}` sorted set | No — rebuilt from DB on cold start |
| **Real-time counters** (likes, views) | `tweet:{id}:metrics` hash | Partially — flush to DB periodically |

**Example in Interview — Presence (WhatsApp)**:
> "Online status is stored directly in Redis: `SET online:{user_id} 1 EX 70`. The client sends a heartbeat every 30 seconds, which refreshes the TTL. If no heartbeat for 70 seconds, the key expires → user is offline. To check if Bob is online: `EXISTS online:{bob_id}`. No database involved — presence is inherently ephemeral."

---

## 5. Redis Persistence & Durability

### Persistence Options

| Mode | How | Data Loss Risk | Performance Impact |
|------|-----|---------------|-------------------|
| **No persistence** | Memory only | Lose everything on restart | Fastest |
| **RDB (snapshots)** | Periodic full dump to disk (e.g., every 5 min) | Lose last 5 min of data | Low (fork + background write) |
| **AOF (append-only file)** | Log every write command | Minimal (depends on fsync policy) | Moderate (disk writes) |
| **RDB + AOF** | Both — RDB for fast restart, AOF for minimal data loss | Very low | Moderate |

### When Persistence Matters

| Use Case | Persistence? | Why |
|----------|-------------|-----|
| Cache | No / RDB | Cache can be rebuilt from DB. RDB speeds up warm restart. |
| Session store | RDB | Losing sessions = users re-login. Acceptable. |
| Rate limiter | No | Counters reset naturally. |
| Leaderboard (source of truth) | AOF | Can't rebuild from elsewhere — need durability. |
| Pre-computed timelines | No | Rebuilt from Cassandra on cold start. |

**Talking Point**: "For caching, I wouldn't enable persistence — on restart, the cache warms up naturally from DB reads. For the leaderboard where Redis is the source of truth, I'd use AOF with `everysec` fsync to limit data loss to ~1 second."

---

## 6. Redis Cluster & Scaling

### Single Instance Limits

| Resource | Typical Limit |
|----------|--------------|
| Memory | 25-64 GB per instance |
| QPS | ~100K-300K operations/sec (depends on operation) |
| Connections | ~10K concurrent |

### Redis Cluster

**How it works**: Data is split into **16,384 hash slots**. Each slot is assigned to a master node. `hash(key) % 16384` → determines which node owns the key.

```
Redis Cluster (6 nodes: 3 masters + 3 replicas)

Master 1: slots 0-5460       ← Replica 1 (failover backup)
Master 2: slots 5461-10922   ← Replica 2
Master 3: slots 10923-16383  ← Replica 3

Key "user:123" → hash = 7892 → Master 2
Key "user:456" → hash = 3201 → Master 1
```

### Scaling Strategies

| Need | Strategy | Example |
|------|----------|---------|
| More memory | Add nodes to cluster | 50 GB → add a 4th master, redistribute slots |
| More read throughput | Add replicas | Each master gets 2 replicas → 3× read throughput |
| More write throughput | Add masters | More masters = more slots = more write parallelism |
| Geographic distribution | Cross-region replicas | Read locally, write to primary region |

**Example in Interview**:
> "I'd deploy a Redis Cluster with 10 master nodes (each 32 GB = 320 GB total) and 2 replicas per master for fault tolerance. That gives us ~1M ops/sec read capacity and 300 GB usable memory. Keys are automatically distributed across nodes via hash slots — no application-level sharding needed."

---

## 7. Redis Pub/Sub & Streams

### Pub/Sub

**What**: Fire-and-forget message broadcasting. Publishers send to a channel, all subscribers receive.

**Key limitation**: **No durability** — if no subscriber is listening, the message is lost.

| Use Case | Why Pub/Sub | Example |
|----------|-------------|---------|
| **WebSocket message routing** | Route messages between gateway servers | Gateway A publishes → Gateway B (where recipient is connected) receives |
| **Real-time seat map updates** | Broadcast seat status changes to all viewers | `PUBLISH seatmap:{event_id} "{seat_id: 'A1', status: 'HELD'}"` |
| **Cache invalidation** | Notify all app servers to evict a cache entry | `PUBLISH cache-invalidate "user:123"` |
| **Typing indicators** | Broadcast "Alice is typing" to conversation participants | `PUBLISH typing:{conv_id} "alice"` |

**NOT for**: Durable messaging, event sourcing, guaranteed delivery → use Kafka.

**Example in Interview — WebSocket Routing**:
> "When Alice sends a message to Bob, and Bob is connected to Gateway Server 7, I need to route from the Message Service to Gateway 7. I look up Bob's gateway in Redis (`session:{bob_id}` → `gateway-7`), then `PUBLISH messages:gateway-7 '{msg}'`. Gateway 7 subscribes to its own channel and pushes to Bob's WebSocket. This decouples the Message Service from knowing which gateway Bob is on."

### Redis Streams (Durable Alternative)

**What**: Append-only log (similar to Kafka topics but in Redis). Supports consumer groups, acknowledgment, and replay.

**When to use over Pub/Sub**: When you need durability, replay, or consumer groups — but volume is low enough for Redis.

**When to use Kafka instead**: High volume (>100K msgs/sec), long retention, disk-based durability.

---

## 8. Common Redis Patterns in HLD

### Pattern 1: Cache Layer (Most Common)

```
Client → API Server → Redis (cache check)
                         │ HIT → return
                         │ MISS → DB → store in Redis → return
```

**Used in**: Every single HLD problem. Twitter (tweet cache), Instagram (profile cache), WhatsApp (user cache), etc.

---

### Pattern 2: Pre-Computed Timelines

```
Tweet created → Fanout Worker → ZADD timeline:{follower} {timestamp} {tweet_id}
                                 for each follower

Timeline read → ZREVRANGE timeline:{user} 0 49 → hydrate tweet details
```

**Used in**: Twitter, Instagram, any social feed

**Example**: "Alice follows 500 users. When Bob tweets, the Fanout Worker adds the tweet_id to Alice's Redis sorted set. When Alice opens the app, `ZREVRANGE` returns the 50 most recent tweet IDs in < 1ms."

---

### Pattern 3: Distributed Locking

```
Acquire: SET lock:{resource} {owner} NX EX 30
         → NX = only if not exists
         → EX 30 = auto-release after 30 seconds

Release: DEL lock:{resource} (only if owner matches)
```

**Used in**: Ticket booking (prevent double-sell), job scheduling (one worker per job), distributed cron

**Example**: "Before reserving seat A1, I acquire `SET lock:event:123 worker1 NX EX 5`. If acquired → proceed with DB transaction. If not → another worker is handling it. The 5-second TTL ensures the lock is released even if the worker crashes."

---

### Pattern 4: Rate Limiting

**Fixed Window** (simplest):
```
Key: ratelimit:{user}:{minute}
INCR → if > 100 → reject
EXPIRE 60 (set on first request)
```

**Sliding Window Log** (most accurate):
```
Key: ratelimit:{user}  (Sorted Set)
ZADD with timestamp as score
ZREMRANGEBYSCORE to remove old entries
ZCARD to count current window
```

**Token Bucket** (best balance):
```
Key: ratelimit:{user}  (Hash)
Fields: tokens, last_refill
Lua script: calculate refill → check tokens → decrement
```

**Used in**: API gateway, per-user limits, per-action limits (tweets/hour, likes/day)

---

### Pattern 5: Session Storage

```
Login: HSET session:{token} user_id 123 role "admin" created_at 170467
       EXPIRE session:{token} 3600

Auth check: HGETALL session:{token}
            → if exists → authenticated
            → if expired/missing → redirect to login

Logout: DEL session:{token}
```

**Used in**: Any system with user authentication

---

### Pattern 6: Presence / Online Status

```
Online:  SET online:{user_id} 1 EX 70  (refreshed every 30s by heartbeat)
Offline: Key expires after 70s → user is offline
Check:   EXISTS online:{user_id}  → 1=online, 0=offline
Last seen: On expiry → SET last_seen:{user_id} {timestamp}
```

**Used in**: WhatsApp, Slack, any chat system

---

### Pattern 7: Counter with Periodic Flush

```
Real-time: HINCRBY tweet:456:metrics likes 1  (every like)
           HINCRBY tweet:456:metrics views 1   (every view)

Periodic:  Every 5 minutes, background worker reads counters and writes to DB
           Then optionally resets or keeps as cache
```

**Used in**: Like counts, view counts, ad impression counts — where real-time display matters but DB writes can be batched.

---

### Pattern 8: Bloom Filter (via RedisBloom module)

```
BF.ADD crawled_urls "https://example.com/page1"
BF.EXISTS crawled_urls "https://example.com/page1"  → 1 (probably seen)
BF.EXISTS crawled_urls "https://example.com/new"    → 0 (definitely not seen)
```

**Used in**: Web crawler URL dedup, ad click dedup

---

## 9. Redis vs Alternatives

### Redis vs Memcached

| Dimension | Redis | Memcached |
|-----------|-------|-----------|
| Data structures | Rich (strings, hashes, lists, sets, sorted sets) | Strings only |
| Persistence | Optional (RDB/AOF) | None |
| Pub/Sub | ✅ | ❌ |
| Clustering | Redis Cluster (built-in) | Client-side consistent hashing |
| Lua scripting | ✅ | ❌ |
| Memory efficiency | Slightly more overhead | More memory-efficient for simple caching |
| **Best for** | Complex use cases (leaderboards, rate limiting, queues) | Simple key-value caching |

**When to pick Memcached**: "If all we need is simple string caching with no data structure needs, Memcached is simpler and slightly more memory-efficient."

**When to pick Redis**: "Almost always — the rich data structures (sorted sets for leaderboards, hashes for token buckets, sets for dedup) make Redis far more versatile."

### Redis vs DynamoDB (for cache/counter use cases)

| Dimension | Redis | DynamoDB |
|-----------|-------|----------|
| Latency | < 1ms | 1-5ms |
| Data model | Rich structures | Key-value / document |
| Scaling | Manual (cluster) | Auto-scaling |
| Cost | Memory-bound (expensive per GB) | Storage-bound (cheaper per GB) |
| Durability | Optional | Built-in (replicated) |
| **Best for** | Hot data, real-time counters, sub-ms reads | Durable key-value, serverless, larger datasets |

---

## 10. Sizing & Back-of-Envelope Numbers

### Performance Numbers

| Metric | Value |
|--------|-------|
| GET/SET latency | < 1ms (typically 0.1-0.5ms) |
| Operations per second (single instance) | 100K-300K |
| Operations per second (cluster, 10 masters) | 1M-3M |
| Max memory per instance | 25-64 GB (practical) |
| Max keys | 2^32 (~4 billion) per instance |
| Max sorted set members | 2^32 per key |

### Memory Estimation

| Data | Size Per Item | Example |
|------|--------------|---------|
| String (short) | ~100 bytes (key + value + overhead) | `session:{token}` → 100 bytes |
| Hash (5 fields) | ~200 bytes | `user:{id}` with name, email, etc. |
| Sorted Set member | ~80 bytes per member | `timeline:{user}` with tweet_id as member |
| Set member | ~80 bytes per member | `user:{id}:liked_tweets` |
| HyperLogLog | ~12 KB fixed | One per unique counter |

### Common Sizing Examples

**Twitter Timeline Cache (80M active users)**:
```
80M users × 50 tweets × 80 bytes per ZSET member
= 80M × 4,000 bytes = 320 GB
Deployed: 15 masters × 25 GB each = 375 GB (with headroom)
```

**WhatsApp Session Store (500M concurrent)**:
```
500M sessions × 200 bytes per hash
= 100 GB
Deployed: 5 masters × 25 GB each = 125 GB
```

**Rate Limiter (100M users)**:
```
100M users × 100 bytes per rate limit key
= 10 GB
Deployed: 1 master (with replicas)
```

---

## 11. Failure Scenarios

### Scenario 1: Redis Master Dies

**Impact**: Keys on that master are unavailable.  
**Recovery**: Sentinel/Cluster promotes replica to master (~seconds).  
**Data loss**: Up to last snapshot (RDB) or last second (AOF everysec).  
**Application impact**: Brief errors, then automatic recovery.

### Scenario 2: Cache Stampede (Thundering Herd)

**Problem**: A popular key expires → thousands of requests simultaneously hit DB.  
**Solutions**:
- **Staggered TTL**: Add random jitter to TTL (e.g., 3600 ± 300 seconds)
- **Lock on miss**: First request acquires a lock, fetches from DB, populates cache. Others wait or get stale data.
- **Refresh-ahead**: Proactively refresh before expiry.

### Scenario 3: Hot Key

**Problem**: One key gets extreme traffic (celebrity profile, viral tweet).  
**Solutions**:
- **Local cache**: Cache the hot key in application memory (1-5 second TTL)
- **Read replicas**: Multiple replicas serve the same key
- **Key splitting**: Split value across multiple keys, aggregate on read

### Scenario 4: Memory Full (OOM)

**Problem**: Redis runs out of memory.  
**Solutions**:
- **Eviction policy**: Configure `maxmemory-policy` (LRU, LFU, random, volatile-ttl)
- **Add nodes**: Scale cluster horizontally
- **Reduce TTLs**: Expire data faster
- **Move cold data**: Archive to DB, keep only hot data in Redis

### Scenario 5: Network Partition (Split Brain)

**Problem**: Replicas can't reach master → both accept writes → data conflict.  
**Solution**: `min-replicas-to-write` = 1 — master rejects writes if no replica is reachable. Prevents split-brain at the cost of availability.

**Talking Point**: "For Redis as cache, I'd use `allkeys-lru` eviction — when memory is full, evict the least recently used key. For Redis as primary store (leaderboard), I'd use `noeviction` and alert when memory reaches 80% so we can scale before hitting limits."

---

## 12. Redis Anti-Patterns

### ❌ Storing Large Values (> 100 KB)
**Problem**: Large values block the single-threaded event loop, increasing latency for all clients.  
**Instead**: Store large blobs in S3, keep only the reference URL in Redis.

### ❌ Using KEYS * in Production
**Problem**: `KEYS *` scans all keys — blocks Redis for seconds/minutes on large datasets.  
**Instead**: Use `SCAN` for incremental iteration. Or maintain an index set.

### ❌ Unbounded Collections
**Problem**: A list/set/sorted set that grows forever eventually fills memory.  
**Instead**: Use `LTRIM`, `ZREMRANGEBYRANK`, or set max size. E.g., `LTRIM activity:user:123 0 99` keeps only the last 100.

### ❌ Using Redis as a Durable Database
**Problem**: Redis can lose data on restart (even with AOF). Not designed for ACID transactions.  
**Instead**: Use Redis as cache + PostgreSQL/DynamoDB as source of truth. Or accept the data loss risk.

### ❌ Ignoring Serialization Overhead
**Problem**: Storing entire JSON objects as strings wastes memory and requires full re-serialization on every update.  
**Instead**: Use hashes for structured data — update individual fields with `HSET`.

### ❌ No TTL on Cache Keys
**Problem**: Keys accumulate forever → memory fills → evictions start → unpredictable behavior.  
**Instead**: Always set TTL on cache entries. Even 24 hours is better than no TTL.

---

## 13. Interview Examples by Problem

### Twitter
| Redis Usage | Data Structure | Key Pattern |
|-------------|---------------|-------------|
| Timeline cache | Sorted Set | `timeline:{user_id}` — score=timestamp, member=tweet_id |
| Tweet cache | Hash | `tweet:{tweet_id}` — fields: content, author, created_at |
| User profile cache | Hash | `user:{user_id}` — fields: name, bio, avatar_url |
| Like count | Hash field | `tweet:{id}:metrics` — field: likes (via `HINCRBY`) |
| "Did I like this?" | Set | `user:{id}:liked_tweets` — members: tweet_ids |
| Rate limiting | String | `ratelimit:{user_id}:tweet:{minute}` — counter with TTL |
| Trending topics | Sorted Set | `trending:global` — score=trend_score, member=hashtag |

**Talking Point**: "Redis appears in 6 places in the Twitter design. The timeline is a sorted set per user. Tweet metrics use `HINCRBY` for atomic counter updates. The 'has user liked this tweet?' check is `SISMEMBER` on a set — O(1). Trending topics are a sorted set updated every 5 minutes."

---

### WhatsApp
| Redis Usage | Data Structure | Key Pattern |
|-------------|---------------|-------------|
| Online presence | String with TTL | `online:{user_id}` — 70s TTL, refreshed by heartbeat |
| Last seen | String | `last_seen:{user_id}` — timestamp |
| Session routing | Hash | `session:{user_id}:{device_id}` — gateway_server, connection_id |
| Undelivered messages | List | `undelivered:{user_id}` — list of message_ids |
| Group members (cached) | Set | `group:{group_id}:members` — set of user_ids |
| Typing indicator | String with TTL | `typing:{conv_id}:{user_id}` — 5s TTL |
| WebSocket routing | Pub/Sub | `PUBLISH messages:{gateway_server}` |

**Talking Point**: "Redis is the real-time backbone of WhatsApp — presence uses key TTL (70s, refreshed by heartbeats), session routing tells us which gateway server each user is on, and Pub/Sub routes messages between gateway servers. Undelivered messages queue in Redis lists until the user reconnects."

---

### Ticket Booking
| Redis Usage | Data Structure | Key Pattern |
|-------------|---------------|-------------|
| Distributed lock | String (SETNX) | `lock:event:{event_id}` — 5s TTL |
| Virtual queue | Sorted Set | `queue:{event_id}` — score=timestamp, member=user_id |
| Queue token | String with TTL | `queue_token:{event_id}:{user_id}` — 5 min TTL |
| Seat availability cache | String with TTL | `availability:{event_id}` — 2s TTL |
| Hold expiration | String with TTL | `hold:{reservation_id}` — 10 min TTL → keyspace notification |
| GA inventory counter | String (DECR) | `ga_inventory:{event_id}:{section_id}` — atomic decrement |

**Talking Point**: "Redis handles the flash sale: the virtual queue is a sorted set (FIFO by timestamp), distributed locks prevent double-selling, and seat holds auto-expire via key TTL with keyspace notifications. For general admission, a Lua script atomically checks-and-decrements the inventory counter."

---

### Ad Click Aggregator
| Redis Usage | Data Structure | Key Pattern |
|-------------|---------------|-------------|
| Minute-level aggregates | Hash | `agg:{dim_key}:{minute_ts}` — fields: impressions, clicks, spend_cents |
| Daily rollup | Hash | `rollup:{campaign_id}:today:{date}` — fields: impressions, clicks |
| Dedup (tier 2) | String (SETNX) | `dedup:{event_id}` — 1 hour TTL |
| Click journal (attribution) | Sorted Set | `click_journal:{user_id}` — score=click_timestamp, member=click_data |
| Real-time campaign metrics | Hash | `conv:{campaign_id}:today` — fields: conversions, revenue |

**Talking Point**: "Flink flushes partial aggregates to Redis every 10 seconds using `HINCRBY` for atomic counter updates. Dashboard queries hit Redis for minute-level data — `HGETALL` on 60 keys (pipelined) returns the last hour in < 5ms. The dedup layer uses `SETNX` for the 0.01% of events that pass the Bloom filter."

---

### Leaderboard
| Redis Usage | Data Structure | Key Pattern |
|-------------|---------------|-------------|
| Global leaderboard | Sorted Set | `leaderboard:{game_id}` — score=points, member=user_id |
| User rank | `ZREVRANK` | O(log N) — instant rank lookup |
| Top-100 | `ZREVRANGE 0 99` | O(log N + 100) — fast top-K query |
| Score update | `ZINCRBY` | Atomic score increment |
| Daily leaderboard | Sorted Set + TTL | `leaderboard:{game_id}:daily:{date}` — TTL 48 hours |

---

## 14. Quick Reference Card

### Redis in One Sentence by Use Case

| Use Case | One-Liner |
|----------|-----------|
| **Cache** | `SET key value EX ttl` — cache-aside, read-through |
| **Counter** | `INCR key` — atomic, O(1), perfect for likes/views |
| **Leaderboard** | Sorted Set — `ZADD`, `ZREVRANGE`, `ZRANK` |
| **Timeline** | Sorted Set — score=timestamp, member=tweet_id |
| **Rate Limiter** | String (`INCR`) for fixed window, Hash for token bucket, ZSET for sliding window |
| **Session** | Hash with TTL — `HSET session:{token} ... EX 3600` |
| **Lock** | `SET lock NX EX 30` — acquire if not exists, auto-release |
| **Presence** | String with TTL — `SET online:{user} 1 EX 70` |
| **Queue** | List — `LPUSH` / `RPOP` for FIFO |
| **Pub/Sub** | WebSocket routing, cache invalidation, typing indicators |
| **Unique count** | HyperLogLog — 12 KB for billions of uniques |
| **DAU** | Bitmap — 12.5 MB for 100M users |
| **Dedup** | `SETNX` — returns 0 if already exists |
| **Bloom filter** | RedisBloom — `BF.ADD`, `BF.EXISTS` |

### Numbers to Memorize

| Metric | Value |
|--------|-------|
| Latency | < 1ms (typically 0.1-0.5ms) |
| QPS per instance | 100K-300K |
| Memory per instance | 25-64 GB |
| Sorted Set operations | O(log N) |
| Hash operations | O(1) per field |
| HyperLogLog memory | ~12 KB fixed |
| Bitmap for 100M users | ~12.5 MB |

### The 10-Second Redis Elevator Pitch

> "I'd add Redis as the caching and real-time data layer. For [use case], I'd use a [data structure] with key pattern `[pattern]`. This gives us [latency] reads and [capability]. The data has a [TTL] TTL — if Redis restarts, it rebuilds from [source of truth]. For the cluster, I'd use [N] masters with [M] replicas, giving us [total memory] and [QPS] capacity."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Focus**: System design interviews — concepts, data structures, patterns, examples  
**Status**: Complete & Interview-Ready ✅
