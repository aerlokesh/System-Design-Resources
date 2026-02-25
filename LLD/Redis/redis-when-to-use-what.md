# Redis — When to Use What (Decision Guide)

## 🎯 Quick Decision Matrix

```
Need to store a simple value/counter?           → STRING
Need to store an object with fields?             → HASH
Need ordered insertion / queue?                  → LIST
Need uniqueness / set operations?                → SET
Need ranking / scoring / time-ordering?          → SORTED SET (ZSET)
Need real-time broadcast (fire & forget)?        → PUB/SUB
Need persistent event log with consumers?        → STREAMS
Need geographic proximity search?                → GEO
Need approximate unique counting (billions)?     → HYPERLOGLOG
Need per-bit flags for millions of entities?     → BITMAP
Need multi-step atomicity with logic?            → LUA SCRIPT
Need simple atomic batch?                        → MULTI/EXEC
```

---

## 🔹 STRING vs HASH

| Scenario | Use STRING | Use HASH |
|----------|-----------|----------|
| Simple counter | ✅ `INCR views:page:123` | ❌ overkill |
| Cache serialized JSON | ✅ `SET cache:user:123 "{...}"` | ❌ can't query fields |
| Store object with queryable fields | ❌ need multiple keys | ✅ `HSET user:123 name Alice age 30` |
| Update single field of object | ❌ must rewrite entire JSON | ✅ `HSET user:123 age 31` |
| Atomic field increment | ❌ parse JSON, increment, write back | ✅ `HINCRBY user:123 login_count 1` |
| TTL on entire object | ✅ `SET key val EX 300` | ✅ `EXPIRE user:123 300` (whole hash only) |
| TTL on individual fields | ❌ | ❌ (not supported — use separate keys) |

**Rule of Thumb**: If you need to read/write individual fields → HASH. If it's a blob → STRING.

---

## 🔹 LIST vs SET vs SORTED SET

| Scenario | LIST | SET | SORTED SET |
|----------|------|-----|------------|
| Preserve insertion order | ✅ | ❌ | ❌ (score order) |
| Allow duplicates | ✅ | ❌ | ❌ |
| O(1) membership check | ❌ O(N) | ✅ SISMEMBER | ✅ ZSCORE |
| Random access by index | ✅ LINDEX O(N) | ❌ | ❌ |
| Ranking / top-N | ❌ | ❌ | ✅ ZREVRANGE |
| Set operations (intersect/union) | ❌ | ✅ | ✅ |
| Blocking pop (queue) | ✅ BLPOP | ❌ | ❌ |
| Count unique items | ❌ | ✅ SCARD | ✅ ZCARD |
| Score-based range query | ❌ | ❌ | ✅ ZRANGEBYSCORE |

### Common Confusions

**Q: I need a queue. LIST or STREAM?**
```
Simple FIFO queue, single consumer?        → LIST (LPUSH/RPOP)
Blocking worker queue?                      → LIST (BLPOP)
Multiple consumer groups, at-least-once?    → STREAMS
Need message replay / history?              → STREAMS
Need acknowledgment?                        → STREAMS
```

**Q: I need to track unique users. SET or HYPERLOGLOG?**
```
Need exact count + list members?            → SET
Need membership check (is X in set)?        → SET
Only need approximate count?                → HYPERLOGLOG (12KB vs 50MB+)
Billions of unique items?                   → HYPERLOGLOG (always 12KB)
```

**Q: I need a leaderboard. How?**
```
Simple ranking by score?                    → SORTED SET
Need user metadata (name, avatar)?          → SORTED SET (rank) + HASH (metadata)
Multi-game combined leaderboard?            → ZUNIONSTORE
Find mutual top players across games?       → ZINTERSTORE
```

---

## 🔹 PUB/SUB vs STREAMS

| Feature | PUB/SUB | Streams |
|---------|---------|---------|
| **Delivery** | Fire-and-forget | Persistent |
| **Late joiners** | Miss messages | Can read history |
| **Consumer groups** | ❌ | ✅ |
| **Acknowledgment** | ❌ | ✅ (XACK) |
| **Replay** | ❌ | ✅ |
| **Backpressure** | ❌ | ✅ (consumer controls pace) |
| **Memory** | Minimal | Grows with messages |
| **Use case** | Live notifications, chat | Event sourcing, job queues |

**Rule of Thumb**: PUB/SUB for ephemeral notifications. Streams for reliable event processing.

---

## 🔹 Distributed Lock Approaches

| Approach | Pros | Cons |
|----------|------|------|
| `SETNX + EXPIRE` (separate) | Simple | Race condition between SETNX and EXPIRE |
| `SET key val NX PX 30000` (atomic) | ✅ Correct, atomic | Single instance |
| Redlock (multi-instance) | Fault tolerant | Complex, controversial |
| Lua script lock | Full control | Must handle edge cases |

**Best Practice**:
```
SET lock:resource <unique-owner-id> NX PX 30000

# Release only if you own it (Lua):
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end
```

---

## 🔹 Caching Patterns

### Cache-Aside (Lazy Loading)
```
1. App checks Redis: GET cache:user:123
2. Cache MISS → query DB → SET cache:user:123 <data> EX 300
3. Cache HIT → return cached data
```
**Pros**: Only caches what's needed. **Cons**: First request always slow.

### Write-Through
```
1. App writes to DB
2. App writes to Redis: SET cache:user:123 <data> EX 300
3. Reads always hit cache
```
**Pros**: Cache always fresh. **Cons**: Write latency, caches unused data.

### Write-Behind (Write-Back)
```
1. App writes to Redis only
2. Background worker async writes to DB
```
**Pros**: Fast writes. **Cons**: Data loss risk if Redis crashes.

### Cache Stampede Prevention
```
# Problem: Key expires → 1000 requests hit DB simultaneously
# Solution 1: Distributed lock
SET lock:cache:user:123 owner NX PX 5000
# Only lock winner queries DB, others wait/retry

# Solution 2: Probabilistic early expiration
# Refresh cache slightly before TTL expires (jitter)

# Solution 3: Never expire + background refresh
# Cache never expires, background job refreshes periodically
```

---

## 🔹 Rate Limiting Patterns

### Fixed Window (STRING + INCR)
```
INCR rate:user:123:1706000000    → increment counter for current window
EXPIRE rate:user:123:1706000000 60
# If count > limit → reject
```
**Pros**: Simple, O(1). **Cons**: Burst at window boundary.

### Sliding Window (SORTED SET)
```
ZADD rate:user:123 <timestamp> <request-uuid>
ZREMRANGEBYSCORE rate:user:123 0 <now - window_size>
ZCARD rate:user:123
# If count > limit → reject
```
**Pros**: Smooth, no boundary burst. **Cons**: More memory per request.

### Token Bucket (Lua Script)
```lua
local tokens = tonumber(redis.call("GET", KEYS[1]) or ARGV[2])
local last_refill = tonumber(redis.call("GET", KEYS[2]) or ARGV[3])
local now = tonumber(ARGV[3])
local rate = tonumber(ARGV[4])
local refill = math.min(tokens + (now - last_refill) * rate, tonumber(ARGV[2]))
if refill >= 1 then
    redis.call("SET", KEYS[1], refill - 1)
    redis.call("SET", KEYS[2], now)
    return 1  -- allowed
else
    return 0  -- rejected
end
```

---

## 🔹 Key Naming Conventions

```
# Pattern: entity:id:attribute
user:123                    → user hash
user:123:sessions           → set of session IDs
user:123:feed               → list of feed items
user:123:friends            → set of friend IDs

# Pattern: action:entity:id:window
rate:api:user:123:minute    → rate limit counter
views:page:/home:2024-01-15 → daily page view counter

# Pattern: purpose:scope
lock:order:456              → distributed lock
cache:query:sha256hash      → cached query result
queue:emails:high           → priority email queue

# Anti-patterns to AVOID
❌ Very long keys (wastes memory)
❌ Spaces in keys
❌ No structure (hard to SCAN/debug)
```

---

## 🔹 Memory Optimization Cheat Sheet

| Technique | Savings | How |
|-----------|---------|-----|
| Use HASH over multiple STRINGs | ~10x for small objects | Ziplist encoding for < 128 fields |
| Short key names | Significant at scale | `u:123` vs `user_profile:123` |
| UNLINK instead of DEL | Latency (not memory) | Async delete for large keys |
| Set maxmemory-policy | Prevents OOM | `allkeys-lru` or `volatile-lru` |
| Use HyperLogLog for counting | 12KB vs GBs | When approximate count is OK |
| Compress values | 50-90% | gzip/snappy before SET |
| EXPIRE unused keys | Reclaims memory | TTL on everything possible |
| Use BITMAP for flags | 1 bit per flag | vs. 1 key per flag |

---

## 🔹 Redis Cluster Considerations

```
✅ Single-key commands work normally
✅ Multi-key commands work IF keys are in same slot
❌ Multi-key commands across slots FAIL

# Force keys to same slot using hash tags:
SET {user:123}:profile "..."     → slot = hash("user:123")
SET {user:123}:settings "..."    → same slot!
MGET {user:123}:profile {user:123}:settings  → works in cluster!

# Lua scripts must use keys in same slot
EVAL "..." 2 {user:123}:balance {user:123}:history  → OK
EVAL "..." 2 user:123:balance user:456:balance       → CROSSSLOT ERROR
```

---

## 🔹 Interview Decision Flowchart

```
START: What's the access pattern?
│
├─ Simple get/set/increment? → STRING
│
├─ Object with multiple fields?
│   ├─ Need individual field access? → HASH
│   └─ Always read/write as blob? → STRING (JSON)
│
├─ Ordered collection?
│   ├─ Need queue behavior (push/pop)? → LIST
│   ├─ Need ranking by score? → SORTED SET
│   └─ Need time-ordered events? → STREAMS or SORTED SET
│
├─ Unique collection?
│   ├─ Need set operations (intersect/union)? → SET
│   ├─ Need ranking? → SORTED SET
│   └─ Only need count (not members)? → HYPERLOGLOG
│
├─ Real-time messaging?
│   ├─ Ephemeral, fire-and-forget? → PUB/SUB
│   └─ Need persistence/ack/groups? → STREAMS
│
├─ Geographic data?
│   └─ Need proximity search? → GEO
│
├─ Need atomicity across multiple keys?
│   ├─ Simple batch? → MULTI/EXEC
│   └─ Conditional logic? → LUA SCRIPT
│
└─ Need distributed coordination?
    ├─ Mutual exclusion? → SET NX PX (distributed lock)
    ├─ Limited concurrency? → Sorted Set semaphore
    └─ Leader election? → SET NX + TTL + Lua