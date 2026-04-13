# 🎯 Topic 5: Caching Strategies

> **System Design Interview — Deep Dive**
> A comprehensive guide covering all caching strategies — cache-aside, write-through, write-behind, read-through — along with cache stampede prevention, TTL tuning, key design, eviction policies, and how to articulate caching decisions with depth and precision.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Caching Matters — The Numbers](#why-caching-matters--the-numbers)
3. [Cache-Aside (Lazy Loading)](#cache-aside-lazy-loading)
4. [Write-Through](#write-through)
5. [Write-Behind (Write-Back)](#write-behind-write-back)
6. [Read-Through](#read-through)
7. [Refresh-Ahead](#refresh-ahead)
8. [Comparing All Strategies](#comparing-all-strategies)
9. [Cache Stampede / Thundering Herd](#cache-stampede--thundering-herd)
10. [TTL Design — The Art of Expiration](#ttl-design--the-art-of-expiration)
11. [Cache Key Design](#cache-key-design)
12. [Eviction Policies](#eviction-policies)
13. [Multi-Layer Caching](#multi-layer-caching)
14. [Distributed Caching with Redis](#distributed-caching-with-redis)
15. [Cache Warming](#cache-warming)
16. [Cache Sizing and Capacity Planning](#cache-sizing-and-capacity-planning)
17. [Monitoring Cache Health](#monitoring-cache-health)
18. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
19. [Common Interview Mistakes](#common-interview-mistakes)
20. [Caching Strategy by System Design Problem](#caching-strategy-by-system-design-problem)
21. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Caching stores frequently accessed data in a faster storage layer (typically in-memory) to reduce load on the primary data store and improve response times.

**The fundamental tradeoff**: Caching trades **data freshness** (cached data may be stale) for **performance** (sub-millisecond reads) and **cost savings** (fewer database queries).

```
Without cache:  Client → App Server → Database (5ms)
With cache:     Client → App Server → Redis (0.5ms) → 95% hit
                                     → Database (5ms) → 5% miss
```

---

## Why Caching Matters — The Numbers

### Performance Impact
| Operation | Latency |
|---|---|
| Redis GET | 0.1-0.5ms |
| PostgreSQL indexed query | 2-10ms |
| PostgreSQL complex JOIN | 10-100ms |
| Elasticsearch query | 10-50ms |
| External API call | 50-500ms |

### Cost Impact
```
Scenario: 200K reads/sec

Without cache:
  PostgreSQL needs: ~15 read replicas at $1,500/month each
  Total: $22,500/month

With cache (95% hit ratio):
  Redis cluster: 3 nodes at $200/month each = $600/month
  PostgreSQL: 3 read replicas (handles 5% miss traffic) = $4,500/month
  Total: $5,100/month
  
Savings: $17,400/month = 77% cost reduction
```

### The 80/20 Rule
> *"80% of requests access 20% of the data. By caching that hot 20%, we reduce database load by 80%."*

---

## Cache-Aside (Lazy Loading)

### How It Works
```
READ:
1. Application checks cache (Redis)
2. Cache HIT → return cached data
3. Cache MISS → query database → store result in cache → return data

WRITE:
1. Application writes to database
2. Application deletes (invalidates) cache key
3. Next read will miss cache and repopulate from DB
```

### Flow Diagram
```
┌─────────┐     GET key     ┌─────────┐
│  Client  │ ──────────────→ │  Redis   │
│          │ ←── HIT ─────── │  (cache) │
│          │                 └─────────┘
│          │     MISS ↓
│          │ ──────────────→ ┌─────────┐
│          │ ←── data ────── │ Postgres │
│          │                 │  (DB)    │
│          │ ── SET key ───→ └─────────┘
│          │                 ┌─────────┐
│          │ ──────────────→ │  Redis   │
└─────────┘                  └─────────┘
```

### Advantages
- **Only caches what's actually read**: No wasted memory on data nobody requests.
- **Resilient to cache failure**: If Redis dies, reads fall through to DB (slower but functional).
- **Simple to implement**: Application controls all caching logic.

### Disadvantages
- **Cache miss penalty**: First request after miss has full DB latency.
- **Stale data**: Data can be stale until TTL expires or key is invalidated.
- **Cache stampede risk**: Multiple concurrent misses for the same key can blast the DB.

### Interview Script
> *"I'd use cache-aside here — the application checks Redis first, and on a miss, fetches from Postgres and populates the cache with a 5-minute TTL. The beauty of cache-aside is that we only cache data that's actually being read. If 80% of our 10 million user profiles are dormant, we're not wasting Redis memory caching profiles nobody requests. The downside is the first request after a cache miss always has the full DB latency — about 5ms instead of 0.5ms."*

---

## Write-Through

### How It Works
```
WRITE:
1. Application writes to cache AND database simultaneously
2. Write is considered complete only when both succeed

READ:
1. Application reads from cache
2. Cache always has the latest data (populated on write)
```

### Flow Diagram
```
WRITE:
┌─────────┐  write  ┌─────────┐  write  ┌─────────┐
│  Client  │ ──────→ │  Redis   │ ──────→ │ Postgres │
│          │ ←── ACK when both succeed ──  │          │
└─────────┘         └─────────┘          └─────────┘

READ:
┌─────────┐  read   ┌─────────┐
│  Client  │ ──────→ │  Redis   │  (always has latest data)
│          │ ←── data│          │
└─────────┘         └─────────┘
```

### Advantages
- **Cache is always consistent with DB**: No stale data (within the write-through boundary).
- **Simple read path**: Always read from cache.
- **If Redis dies**: Data is safely in DB; rebuild cache on restart.

### Disadvantages
- **Higher write latency**: Every write hits both cache and DB.
- **Caches everything that's written**: Even data that's never read (wasteful).
- **Write throughput limited**: Both cache and DB must keep up with write volume.

### Interview Script
> *"Write-through caching makes sense for the user session store — every login writes to both Redis and the database simultaneously. The additional 1-2ms write latency is negligible for a login event, and the guarantee is powerful: the cache is always consistent with the database. If Redis dies and restarts, sessions recover from the DB. If the DB dies, Redis continues serving existing sessions while we failover."*

---

## Write-Behind (Write-Back)

### How It Works
```
WRITE:
1. Application writes to cache ONLY
2. Cache asynchronously flushes to database in background
3. Writes are batched for efficiency (e.g., every 10 seconds)

READ:
1. Application reads from cache
2. Cache has the latest data (it received the write first)
```

### Flow Diagram
```
WRITE (synchronous):
┌─────────┐  write  ┌─────────┐
│  Client  │ ──────→ │  Redis   │  (ACK immediately)
│          │ ←── ACK │          │
└─────────┘         └────┬────┘
                         │ async batch flush
                         │ (every 10 seconds)
                         ▼
                    ┌─────────┐
                    │ Postgres │
                    └─────────┘
```

### Advantages
- **Fastest write path**: Client gets ACK after cache write (sub-ms).
- **Batch writes to DB**: Reduces DB write pressure by 100-1000x.
- **Absorbs write spikes**: Cache buffers bursts; DB writes are smooth.

### Disadvantages
- **Data loss risk**: If cache crashes before flushing, unflushed writes are lost.
- **Complex**: Need reliable flush mechanism, failure recovery, monitoring.
- **Eventual consistency**: DB lags behind cache by the flush interval.

### Mitigation for Data Loss
- Redis AOF (Append-Only File) with `appendfsync everysec` — persists to disk every second.
- Dual-write to two Redis nodes before ACK.
- Application-level WAL before cache write.

### Interview Script
> *"For the high-volume metrics pipeline processing 2.3 million events per second, I'd use write-behind caching — events accumulate in Redis counters, and a background flusher writes to ClickHouse every 10 seconds in batches of 50,000 rows. This reduces ClickHouse write operations from 2.3M/sec (which would crush it) to ~4,600 batch inserts/sec (which is comfortable). The risk is losing up to 10 seconds of data if Redis crashes before a flush, which we mitigate with Redis persistence (AOF every second)."*

---

## Read-Through

### How It Works
```
READ:
1. Application requests data from cache
2. Cache MISS → cache itself fetches from DB → stores → returns
3. Application doesn't interact with DB directly

WRITE:
1. Application writes to DB directly
2. Cache is populated lazily on next read (or combined with write-through)
```

### Difference from Cache-Aside
- **Cache-aside**: Application manages both cache and DB interactions.
- **Read-through**: Cache manages DB interaction; application only talks to cache.

### Implementation
Most cache-as-a-service products (like AWS ElastiCache, NCache) support read-through natively. With Redis, you'd implement it via a proxy layer.

---

## Refresh-Ahead

### How It Works
```
1. Data is cached with TTL
2. When TTL reaches threshold (e.g., 80% expired), proactively refresh
3. The refresh happens BEFORE expiration → no miss for active keys
```

### Example
```
TTL = 5 minutes
Refresh threshold = 80% (4 minutes)

Time 0:00 - Cache SET with TTL 5:00
Time 4:00 - Background thread detects key is 80% expired
           → Proactively fetches from DB and refreshes cache
Time 5:00 - Key would have expired, but it was already refreshed
           → No cache miss!
```

### Advantages
- Zero cache misses for frequently accessed data.
- Smooth performance — no periodic latency spikes from misses.

### Disadvantages
- Wasted refreshes for keys that nobody reads again.
- Complexity in managing the refresh scheduler.
- Only valuable for hot keys with predictable access patterns.

---

## Comparing All Strategies

| Strategy | Write Latency | Read Latency (miss) | Data Freshness | Complexity | Best For |
|---|---|---|---|---|---|
| **Cache-Aside** | DB latency | DB + cache write | Stale until TTL/invalidation | Low | Most read-heavy workloads |
| **Write-Through** | Cache + DB | Always hit | Always fresh | Medium | Session stores, config |
| **Write-Behind** | Cache only (fast) | Always hit | DB lags by flush interval | High | High-write metrics, counters |
| **Read-Through** | DB latency | Handled by cache | Stale until TTL | Medium | CDN-like proxy patterns |
| **Refresh-Ahead** | DB latency | Always hit (hot keys) | Proactively refreshed | High | Hot data with predictable access |

### Decision Framework
```
High write volume + can tolerate data loss risk → Write-Behind
Need cache always consistent → Write-Through
Read-heavy + tolerate first-miss penalty → Cache-Aside (DEFAULT)
Want zero misses for hot data → Refresh-Ahead
Want cache to manage DB interaction → Read-Through
```

---

## Cache Stampede / Thundering Herd

### The Problem
When a popular cache key expires, hundreds or thousands of concurrent requests all miss the cache simultaneously and blast the database:

```
Time T: Key "popular_product_123" expires (TTL reached)
Time T+1ms: 50,000 requests arrive → all miss cache → all query DB
Database: receives 50,000 identical queries simultaneously → overload
```

### Solutions

**Solution 1: Distributed Lock (Mutex)**
```python
def get_with_lock(key):
    value = redis.get(key)
    if value:
        return value
    
    # Try to acquire lock
    if redis.set(f"lock:{key}", "1", nx=True, ex=5):
        # Won the lock — repopulate cache
        value = database.query(key)
        redis.set(key, value, ex=300)  # 5-min TTL
        redis.delete(f"lock:{key}")
        return value
    else:
        # Someone else is repopulating — wait and retry
        time.sleep(0.05)  # 50ms
        return redis.get(key)  # Should be populated by now
```

**Solution 2: Probabilistic Early Expiration**
```python
def get_with_early_expiration(key):
    value, expiry = redis.get_with_ttl(key)
    
    # Probabilistically refresh before expiration
    remaining_ttl = expiry - time.now()
    if remaining_ttl < TTL * 0.2:  # Last 20% of TTL
        if random.random() < 0.1:  # 10% chance
            # Refresh in background
            background_refresh(key)
    
    return value
```

**Solution 3: Stale-While-Revalidate**
```
Cache stores: { value: "...", expires_at: T, stale_until: T+30s }

If current_time < expires_at:
    Return cached value (fresh)
If expires_at < current_time < stale_until:
    Return cached value (stale) AND trigger background refresh
If current_time > stale_until:
    Block and fetch from DB
```

### Interview Script
> *"Cache stampede is a real production issue. Imagine our most popular product page cached in Redis with a 5-minute TTL. When it expires, 50,000 concurrent requests all miss the cache simultaneously and blast the database. I'd prevent this with a distributed lock using Redis `SETNX` — the first request acquires the lock and repopulates the cache; the other 49,999 requests wait 50ms and retry, hitting the now-populated cache. Total DB load: 1 query instead of 50,000."*

---

## TTL Design — The Art of Expiration

### Principle
**TTL is a knob that trades freshness for cache hit ratio.** Shorter TTL = fresher data but more DB load. Longer TTL = staler data but higher hit ratio.

### Recommended TTLs by Data Type

| Data Type | TTL | Reasoning |
|---|---|---|
| User profiles | 10 minutes | Rarely change; high read volume |
| Tweet/post content | 1 hour | Immutable after creation |
| Like/follower counts | 30 seconds | Change frequently; slight staleness OK |
| Trending topics | 5 minutes | Recomputed periodically anyway |
| Product prices | 1 minute | Change occasionally; staleness could cause complaints |
| Session tokens | 30 minutes | Security: expired sessions should re-authenticate |
| Feature flags | 30 seconds | Must be relatively fresh; stale flags cause bugs |
| Search results | 2 minutes | Balance freshness with search infrastructure load |
| Static assets (CDN) | 1 year | Use versioned URLs; content at URL never changes |

### TTL Jitter
Add random jitter to TTLs to prevent synchronized expiration:
```python
base_ttl = 300  # 5 minutes
jitter = random.randint(-30, 30)  # ±30 seconds
actual_ttl = base_ttl + jitter  # 270-330 seconds
```

Without jitter, all keys set at the same time expire at the same time → mass cache miss → thundering herd.

### Interview Script
> *"I'd set different TTLs based on data volatility. User profiles: 10 minutes (rarely change). Tweet content: 1 hour (immutable after creation). Like counts: 30 seconds (change frequently but slight staleness is fine). Trending topics: 5 minutes (recomputed periodically anyway). The TTL is a knob that trades freshness for cache hit ratio — shorter TTL means fresher data but more DB load. I'd start conservative and tune based on the actual hit ratio we observe."*

---

## Cache Key Design

### Principles
1. **Namespaced**: Avoid key collisions between services.
2. **Predictable**: Easy to construct from known parameters.
3. **Versioned**: Allow schema changes without mass invalidation.
4. **Invalidation-friendly**: Easy to delete by prefix or pattern.

### Key Format
```
{service}:{entity}:{id}:{version}

Examples:
tweet:content:12345:v2
user:profile:67890:v3
timeline:user:12345:page:2
search:query:hash(query_string):v1
leaderboard:global:top100:v1
ratelimit:user:12345:minute:202503151430
```

### Anti-Patterns
```
❌ "12345"                     → No namespace, collision risk
❌ "user_profile_12345"        → No version, can't evolve schema
❌ "{full_sql_query}"          → Too long, unpredictable
❌ "cache_key_1"               → Not meaningful, impossible to debug
```

### Interview Script
> *"The cache key design matters more than people think. I'd use `tweet:{tweet_id}:v2` as the key — namespaced to avoid collisions, including the entity ID for direct lookup, and versioned so I can change the cache schema without invalidating everything. For complex queries like 'user 123's timeline page 2', the key would be `timeline:{user_id}:page:{page_num}` — predictable and easy to invalidate by prefix when the timeline changes."*

---

## Eviction Policies

| Policy | Algorithm | Best For |
|---|---|---|
| **LRU** (Least Recently Used) | Evict the key accessed longest ago | General-purpose (Redis default) |
| **LFU** (Least Frequently Used) | Evict the key accessed fewest times | Workloads with stable hot set |
| **TTL** | Evict based on time-to-live expiration | Data with known freshness requirements |
| **Random** | Evict a random key | Simple, surprisingly effective |
| **FIFO** (First In, First Out) | Evict the oldest inserted key | Simple caching layers |

### Redis Eviction Policies
```
allkeys-lru:        LRU across all keys (most common)
volatile-lru:       LRU only among keys with TTL set
allkeys-lfu:        LFU across all keys
volatile-lfu:       LFU only among keys with TTL
allkeys-random:     Random eviction
noeviction:         Return error when memory full (use for critical data)
```

### Recommendation
- **Default**: `allkeys-lru` — works well for most workloads.
- **Stable hot set**: `allkeys-lfu` — better when the same keys are accessed repeatedly.
- **Critical data**: `noeviction` with proper capacity planning — never lose important data.

---

## Multi-Layer Caching

```
┌────────────────────────────────────────────────────────────┐
│                  MULTI-LAYER CACHE                          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Layer 1: Browser/Client Cache                             │
│  ├── Cache-Control headers: max-age, stale-while-revalidate│
│  ├── Service Worker cache (PWAs)                           │
│  └── TTL: varies (seconds to years for static assets)      │
│                                                            │
│  Layer 2: CDN Edge Cache (CloudFront, Fastly)              │
│  ├── Caches at 200+ edge locations worldwide               │
│  ├── 95%+ hit ratio for static content                     │
│  └── TTL: hours to years (versioned URLs)                  │
│                                                            │
│  Layer 3: API Gateway / Reverse Proxy Cache                │
│  ├── Varnish, Nginx cache                                  │
│  ├── Full response caching for GET requests                │
│  └── TTL: seconds to minutes                               │
│                                                            │
│  Layer 4: Application Cache (Redis/Memcached)              │
│  ├── Business object caching                               │
│  ├── Computed results (aggregations, ML scores)            │
│  └── TTL: seconds to hours                                 │
│                                                            │
│  Layer 5: Database Query Cache / Buffer Pool               │
│  ├── PostgreSQL shared_buffers                             │
│  ├── MySQL query cache (deprecated in 8.0)                 │
│  └── Managed by database engine                            │
│                                                            │
│  Layer 6: OS Page Cache                                    │
│  ├── Filesystem cache in RAM                               │
│  └── Managed by operating system                           │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## Distributed Caching with Redis

### Single Node vs Cluster

| Aspect | Single Node | Redis Cluster |
|---|---|---|
| **Capacity** | Limited by one machine's RAM | Sharded across N machines |
| **Throughput** | ~100K ops/sec | ~100K ops/sec × N nodes |
| **Availability** | Single point of failure | Replicas per shard; auto-failover |
| **Complexity** | Simple | More complex (slot management) |
| **Use when** | Data fits in one machine | Data > 25GB or need HA |

### Redis Cluster Architecture
```
Slot 0-5460:    Master A → Replica A'
Slot 5461-10922: Master B → Replica B'
Slot 10923-16383: Master C → Replica C'

Key "user:123" → hash("user:123") % 16384 → slot 7291 → Master B
```

### Redis Sentinel (HA without Sharding)
```
Master → Replica 1, Replica 2
Sentinel monitors all nodes
If Master fails → Sentinel promotes Replica 1 → applications reconnect
```

---

## Cache Warming

### The Cold Start Problem
After a deployment, cache restart, or new region launch, the cache is empty → 100% miss rate → database overload.

### Strategies

**Strategy 1: Pre-populate on startup**
```python
# On application startup
hot_keys = database.query("SELECT user_id FROM users ORDER BY last_active LIMIT 10000")
for key in hot_keys:
    profile = database.query(f"SELECT * FROM users WHERE user_id = {key}")
    redis.set(f"user:profile:{key}", profile, ex=600)
```

**Strategy 2: Background warming job**
```
Cron job runs every hour:
1. Query DB for top 10,000 most-accessed keys (from access logs)
2. For each key, SET in Redis with standard TTL
3. Run at low priority to avoid impacting production
```

**Strategy 3: Copy from existing cache**
```
Before replacing Redis cluster:
1. DUMP keys from old Redis
2. RESTORE keys to new Redis
3. Switch traffic to new Redis
```

---

## Cache Sizing and Capacity Planning

### Formula
```
Required memory = (number_of_hot_items × avg_item_size) + overhead

Example:
  Hot user profiles: 2 million
  Avg profile size: 500 bytes
  Redis overhead per key: ~100 bytes
  
  Memory = 2M × (500 + 100) = 1.2 GB
  
  With 2x safety margin: 2.4 GB → 1 Redis node (r6g.large, 13 GB)
```

### Hit Ratio Targets
- **95%+**: Excellent — cache is well-sized and access pattern is cache-friendly.
- **85-95%**: Good — may benefit from more memory or TTL tuning.
- **< 85%**: Investigate — possible cache pollution, poor key design, or insufficient size.

---

## Monitoring Cache Health

### Key Metrics
| Metric | Healthy | Concerning | Action |
|---|---|---|---|
| **Hit ratio** | > 95% | < 85% | Increase cache size or TTL |
| **Memory usage** | < 80% | > 90% | Scale up or add nodes |
| **Evictions/sec** | Low | Increasing | Cache is undersized |
| **Latency (p99)** | < 1ms | > 5ms | Network issues or overload |
| **Connections** | Stable | Spiking | Connection pool leak |

---

## Interview Talking Points & Scripts

### The Cost-Performance Argument
> *"Caching is the single best cost-performance lever. Our 3-node Redis cluster costs $600/month and absorbs 200K reads/sec. Without it, those reads would hit our PostgreSQL cluster, which would need to scale from 3 read replicas to ~15 replicas. The $600 Redis cluster saves $18,000/month in database costs — a 30x ROI."*

### The 80/20 Argument
> *"I'd apply the 80/20 rule: 80% of requests hit 20% of the data. By identifying and caching that hot 20% in Redis, we reduce database load by 80%. Cache-aside with TTL naturally converges on caching exactly the hot set."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd cache everything"
**Problem**: Wastes memory caching cold data nobody reads. Cache-aside naturally solves this.

### ❌ Mistake 2: Not mentioning TTL
**Problem**: Without TTL, cached data becomes stale forever. Always specify TTL and explain the choice.

### ❌ Mistake 3: Ignoring cache stampede
**Problem**: Saying "we'd use cache-aside with TTL" without addressing what happens when popular keys expire simultaneously.

### ❌ Mistake 4: Not specifying the caching technology
**Problem**: "We'd add a cache" is vague. Say "Redis" and explain why (data structures, sub-ms latency, clustering).

### ❌ Mistake 5: No invalidation strategy
**Problem**: Cache without invalidation means unbounded staleness. Combine TTL with event-driven invalidation.

---

## Caching Strategy by System Design Problem

| Problem | What to Cache | Strategy | TTL |
|---|---|---|---|
| **Twitter Feed** | Pre-computed timelines | Cache-aside (Redis sorted sets) | 5 min |
| **E-Commerce** | Product pages, prices | Cache-aside + CDN | 1-5 min |
| **Chat (WhatsApp)** | Online presence | Write-through | 70s (heartbeat) |
| **URL Shortener** | URL mappings | Cache-aside (Redis) | 1 hour |
| **Leaderboard** | Top-K scores | Write-through (Redis sorted set) | Real-time |
| **Metrics Dashboard** | Aggregated counters | Write-behind | 10s flush |
| **Search** | Query results | Cache-aside | 2 min |
| **Video Streaming** | Video metadata, CDN tokens | Cache-aside + CDN | 1 hour |
| **Session Store** | User sessions | Write-through | 30 min |
| **Rate Limiter** | Request counters | Write-through (Redis INCR) | 1 min window |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              CACHING STRATEGIES CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  DEFAULT: Cache-Aside with TTL (covers 80% of cases)        │
│                                                              │
│  STRATEGIES:                                                 │
│  Cache-Aside:    App manages cache + DB independently        │
│  Write-Through:  Write to cache + DB simultaneously          │
│  Write-Behind:   Write to cache, async flush to DB           │
│  Read-Through:   Cache fetches from DB on miss               │
│  Refresh-Ahead:  Proactively refresh before TTL expires      │
│                                                              │
│  CACHE STAMPEDE PREVENTION:                                  │
│  1. Distributed lock (SETNX)                                │
│  2. Probabilistic early expiration                           │
│  3. Stale-while-revalidate                                   │
│                                                              │
│  TTL GUIDELINES:                                             │
│  Immutable data: 1 hour+                                    │
│  User profiles: 10 minutes                                   │
│  Counters: 30 seconds                                        │
│  Sessions: 30 minutes                                        │
│  Static assets (CDN): 1 year with versioned URLs             │
│                                                              │
│  KEY DESIGN: {service}:{entity}:{id}:{version}               │
│  Example: tweet:content:12345:v2                             │
│                                                              │
│  CACHE HIT RATIO TARGET: > 95%                               │
│  ROI: Typically 10-30x cost savings vs scaling DB            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 6: Cache Invalidation** — TTL + event-driven invalidation strategies
- **Topic 2: Database Selection** — Cache + DB architecture patterns
- **Topic 19: CDN & Edge Caching** — CDN as a caching layer
- **Topic 31: Hot Partitions** — Caching strategies for hot keys
- **Topic 35: Cost vs Performance** — Cache as cost optimization lever

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
