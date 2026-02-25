# Redis Data Structures — Comprehensive Interview Guide

## 🔹 STRING
```
SET key value [EX seconds] [NX|XX]    → O(1)
GET key                                 → O(1)
INCR key / DECR key                    → O(1) atomic increment/decrement
INCRBY key amount                      → O(1)
SETNX key value                        → O(1) set-if-not-exists (for locks)
SETEX key seconds value                → O(1) set + expire atomically
MSET k1 v1 k2 v2                      → O(N) set multiple keys
MGET k1 k2 k3                         → O(N) get multiple keys
APPEND key value                       → O(1) append to existing string
STRLEN key                             → O(1)
GETSET key newvalue                    → O(1) set new, return old (atomic swap)
TTL key                                → O(1) check remaining TTL
EXPIRE key seconds                     → O(1) set expiration
```

### When to Use STRING
- Counters (page views, rate limiters, stock counts)
- Caching (serialized JSON, query results)
- Distributed locks (SETNX + EXPIRE)
- Session tokens, OTPs, temporary auth codes
- Simple key-value lookups

### Real-World Examples
| Use Case | Key Pattern | Command |
|----------|------------|---------|
| Rate limiter | `rate:user:123` | INCR + EXPIRE |
| Session store | `session:abc123` | SETEX token 3600 |
| Page views | `views:page:/home` | INCR |
| Distributed lock | `lock:order:456` | SET lock:order:456 owner NX PX 30000 |
| Cache DB query | `cache:user:123` | SET + EX 300 |

---

## 🔹 HASH
```
HSET key field value                   → O(1)
HGET key field                         → O(1)
HMSET key f1 v1 f2 v2                 → O(N)
HMGET key f1 f2 f3                    → O(N)
HGETALL key                            → O(N) get all fields+values
HDEL key field                         → O(1)
HEXISTS key field                      → O(1)
HINCRBY key field amount               → O(1)
HLEN key                               → O(1) count fields
HKEYS key                              → O(N)
HVALS key                              → O(N)
HSETNX key field value                 → O(1) set field if not exists
```

### When to Use HASH
- Object storage (user profiles, product details)
- Shopping carts (hash per user, field per item)
- Per-entity counters (login count per user)
- Configuration stores (per-tenant settings)
- Feature flags per user/tenant

### Real-World Examples
| Use Case | Key Pattern | Fields |
|----------|------------|--------|
| User profile | `user:123` | name, email, age, plan |
| Shopping cart | `cart:user:123` | item:sku → quantity |
| Feature flags | `flags:user:123` | dark_mode → 1, beta → 0 |
| Tenant config | `config:tenant:acme` | max_users → 100 |
| Product inventory | `inventory:warehouse:1` | sku:A → 50 |

### Why HASH over multiple STRING keys?
```
❌ SET user:123:name "Alice"    → 3 separate keys = more memory overhead
❌ SET user:123:email "a@b.com"
❌ SET user:123:age "30"

✅ HSET user:123 name "Alice" email "a@b.com" age "30"   → 1 key, 3 fields
                                                           → ~10x less memory for small hashes
```
Redis internally uses **ziplist** encoding for small hashes (< 128 fields, < 64 bytes each), which is extremely memory-efficient.

---

## 🔹 LIST
```
LPUSH key value [value ...]            → O(1) per element, push to head
RPUSH key value [value ...]            → O(1) per element, push to tail
LPOP key                               → O(1) remove from head
RPOP key                               → O(1) remove from tail
LRANGE key start stop                  → O(S+N) get range (0-based, -1 = last)
LLEN key                               → O(1) count elements
LINDEX key index                       → O(N) get element at index
LREM key count value                   → O(N) remove N occurrences of value
BLPOP key [key ...] timeout            → O(1) blocking pop (for queues!)
BRPOP key [key ...] timeout            → O(1) blocking pop from tail
LTRIM key start stop                   → O(N) trim list to range
LINSERT key BEFORE|AFTER pivot value   → O(N)
```

### When to Use LIST
- Message queues (LPUSH + RPOP = FIFO)
- Activity feeds / timelines
- Recent items (last N with LTRIM)
- Job queues (BLPOP for workers)
- Chat message history

### Queue Patterns
```
# Simple queue (FIFO)
LPUSH queue:emails "email1"    → Producer pushes left
RPOP queue:emails              → Consumer pops right

# Blocking queue (worker waits for jobs)
BLPOP queue:jobs 30            → Block up to 30s until job arrives

# Priority queue (multiple lists)
RPOP queue:high                → Try high priority first
RPOP queue:medium              → Then medium
RPOP queue:low                 → Then low

# Bounded list (keep last 100)
LPUSH recent:user:123 "action"
LTRIM recent:user:123 0 99    → Trim to 100 elements
```

---

## 🔹 SET
```
SADD key member [member ...]           → O(1) per member
SREM key member                        → O(1)
SISMEMBER key member                   → O(1) membership check
SMEMBERS key                           → O(N) get all members
SCARD key                              → O(1) count members
SPOP key [count]                       → O(1) random remove (for lottery!)
SRANDMEMBER key [count]                → O(1) random get (no remove)
SINTER key1 key2                       → O(N*M) intersection
SUNION key1 key2                       → O(N) union
SDIFF key1 key2                        → O(N) difference (in key1 but not key2)
SINTERSTORE dest key1 key2             → O(N*M) store intersection
SUNIONSTORE dest key1 key2             → O(N) store union
SDIFFSTORE dest key1 key2              → O(N) store difference
```

### When to Use SET
- Unique tracking (visitors, IPs, active users)
- Tagging / categorization
- Social features (friends, followers, mutual friends)
- A/B testing cohorts
- Feature rollout groups
- Lottery / random selection

### Set Operation Examples
```
# Mutual friends
SADD friends:alice bob charlie dave
SADD friends:bob alice charlie eve
SINTER friends:alice friends:bob        → {"charlie"} (mutual friends)

# Users who signed up but didn't buy
SADD signups alice bob charlie
SADD purchases bob
SDIFF signups purchases                 → {"alice", "charlie"}

# Unique visitors today
SADD visitors:2024-01-15 user:123 user:456 user:123
SCARD visitors:2024-01-15              → 2 (deduped)

# Random winner
SPOP contest:entries                    → removes and returns random entry
```

---

## 🔹 SORTED SET (ZSET)
```
ZADD key score member                  → O(log N)
ZREM key member                        → O(log N)
ZSCORE key member                      → O(1)
ZRANK key member                       → O(log N) rank (0-based, lowest first)
ZREVRANK key member                    → O(log N) rank (highest first)
ZRANGE key start stop [WITHSCORES]     → O(log N + M) by rank ascending
ZREVRANGE key start stop [WITHSCORES]  → O(log N + M) by rank descending
ZRANGEBYSCORE key min max              → O(log N + M) by score range
ZCOUNT key min max                     → O(log N) count in score range
ZINCRBY key amount member              → O(log N) increment score
ZUNIONSTORE dest numkeys key1 key2     → O(N*log N) merge sorted sets
ZINTERSTORE dest numkeys key1 key2     → O(N*M*log M) intersect sorted sets
ZCARD key                              → O(1) total members
ZREMRANGEBYSCORE key min max           → O(log N + M)
ZREMRANGEBYRANK key start stop         → O(log N + M)
```

### When to Use SORTED SET
- Leaderboards / rankings
- Time-series data (score = timestamp)
- Priority queues (score = priority)
- Rate limiting (sliding window)
- Delayed job scheduling
- Trending content scoring
- Geofencing (before GEO type existed)

### Common Patterns
```
# Leaderboard
ZADD leaderboard 1500 "alice" 2000 "bob" 1800 "charlie"
ZREVRANGE leaderboard 0 2 WITHSCORES   → Top 3 with scores
ZREVRANK leaderboard "alice"            → Alice's rank (0-based)

# Delayed job scheduler
ZADD delayed:jobs 1706000000 "job:123"  → score = unix timestamp
ZRANGEBYSCORE delayed:jobs -inf <now>   → Get jobs ready to execute

# Sliding window rate limiter
ZADD rate:user:123 <timestamp> <request-uuid>
ZREMRANGEBYSCORE rate:user:123 0 <now - window>
ZCARD rate:user:123                     → Count requests in window

# Trending topics (decay over time)
ZINCRBY trending 1 "redis"              → Boost score
ZREVRANGE trending 0 9                  → Top 10 trending
```

---

## 🔹 PUB/SUB
```
PUBLISH channel message                → O(N+M) publish to channel
SUBSCRIBE channel [channel ...]        → subscribe to channel(s)
UNSUBSCRIBE channel                    → unsubscribe
PSUBSCRIBE pattern                     → pattern subscribe (e.g., news.*)
```

### Characteristics
- **Fire-and-forget**: messages are NOT persisted
- **No history**: late subscribers miss old messages
- **Fan-out**: all subscribers get every message
- Use **Streams** if you need persistence or consumer groups

---

## 🔹 STREAMS (Redis 5.0+)
```
XADD stream * field value              → O(1) add entry (* = auto ID)
XREAD COUNT n STREAMS stream id        → read entries after id
XRANGE stream start end [COUNT n]      → O(N) range query
XLEN stream                            → O(1) stream length
XGROUP CREATE stream group id          → create consumer group
XREADGROUP GROUP group consumer COUNT n STREAMS stream >  → read as consumer
XACK stream group id                   → acknowledge processing
XPENDING stream group                  → list pending (unacked) entries
XCLAIM stream group consumer min-idle-time id  → claim stuck messages
XTRIM stream MAXLEN n                  → trim stream to N entries
```

### Streams vs PUB/SUB
| Feature | PUB/SUB | Streams |
|---------|---------|---------|
| Persistence | ❌ | ✅ |
| Consumer Groups | ❌ | ✅ |
| Message History | ❌ | ✅ |
| Acknowledgment | ❌ | ✅ |
| At-least-once | ❌ | ✅ |
| Fan-out | ✅ | ✅ (via groups) |

---

## 🔹 GEO
```
GEOADD key longitude latitude member   → O(log N) add location
GEOPOS key member                      → O(1) get coordinates
GEODIST key member1 member2 [unit]     → O(1) distance between
GEORADIUS key lon lat radius unit      → O(N+log N) find within radius
GEORADIUSBYMEMBER key member radius    → O(N+log N) find near member
GEOSEARCH key FROMLONLAT lon lat BYRADIUS r unit  → (Redis 6.2+)
```

### Under the Hood
GEO commands use **Sorted Sets** internally. The score is a **geohash** encoding of the coordinates. This means all ZSET commands also work on GEO keys.

---

## 🔹 HYPERLOGLOG
```
PFADD key element [element ...]        → O(1) add element
PFCOUNT key [key ...]                  → O(1) approximate count
PFMERGE destkey sourcekey [sourcekey]  → O(N) merge counts
```

### Characteristics
- **Probabilistic**: ~0.81% standard error
- **Fixed memory**: 12KB per HyperLogLog regardless of cardinality
- **Perfect for**: counting unique visitors, unique IPs, unique events
- **Cannot**: list the actual elements, only approximate count

### When HyperLogLog vs SET
| | SET | HyperLogLog |
|---|-----|-------------|
| Memory (1M unique) | ~50MB | 12KB |
| Exact count | ✅ | ❌ (~0.81% error) |
| List members | ✅ | ❌ |
| Membership check | ✅ | ❌ |

---

## 🔹 BITMAP (String-based)
```
SETBIT key offset value                → O(1) set bit at offset
GETBIT key offset                      → O(1) get bit at offset
BITCOUNT key [start end]               → O(N) count set bits
BITOP AND|OR|XOR|NOT destkey key [key] → O(N) bitwise operations
```

### Use Cases
- Daily active users (1 bit per user ID)
- Feature flags for millions of users
- Bloom filter implementation
- Attendance tracking

---

## 🔹 TRANSACTIONS & LUA
```
# MULTI/EXEC (optimistic transactions)
MULTI                                  → start transaction
SET key1 value1
INCR key2
EXEC                                   → execute all atomically

# WATCH (optimistic locking)
WATCH key                              → watch for changes
MULTI
... commands ...
EXEC                                   → fails if key changed since WATCH

# Lua Script (TRUE atomicity)
EVAL "return redis.call('SET', KEYS[1], ARGV[1])" 1 mykey myvalue
EVALSHA sha1 1 mykey myvalue           → cached script execution
```

### MULTI/EXEC vs Lua Scripts
| | MULTI/EXEC | Lua Scripts |
|---|------------|-------------|
| Atomic execution | ✅ | ✅ |
| Conditional logic | ❌ | ✅ |
| Read-then-write | ❌ (use WATCH) | ✅ |
| Performance | Good | Better (1 round trip) |
| Complexity | Simple | More complex |

---

## 🔹 KEY MANAGEMENT
```
DEL key [key ...]                      → O(1) per string key
UNLINK key [key ...]                   → O(1) async delete (non-blocking)
EXISTS key                             → O(1)
TYPE key                               → O(1)
RENAME key newkey                      → O(1)
KEYS pattern                           → O(N) ⚠️ NEVER in production!
SCAN cursor [MATCH pattern] [COUNT n]  → O(1) per call, safe iteration
EXPIRE key seconds                     → O(1)
PERSIST key                            → O(1) remove expiration
TTL key                                → O(1) remaining seconds
PTTL key                               → O(1) remaining milliseconds
```

### ⚠️ KEYS vs SCAN
```
KEYS user:*     → ❌ Blocks Redis, scans ALL keys. NEVER use in production!
SCAN 0 MATCH user:* COUNT 100  → ✅ Iterates incrementally, non-blocking