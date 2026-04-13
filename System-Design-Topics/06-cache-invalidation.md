# 🎯 Topic 6: Cache Invalidation

> **System Design Interview — Deep Dive**
> A comprehensive guide on cache invalidation — the hardest problem in computer science — covering TTL-based, event-driven, versioned URL, write-invalidate, and hybrid strategies with real-world patterns and interview-ready scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Cache Invalidation Is Hard](#why-cache-invalidation-is-hard)
3. [TTL-Based Invalidation](#ttl-based-invalidation)
4. [Event-Driven Invalidation](#event-driven-invalidation)
5. [Hybrid: TTL + Event-Driven (The Gold Standard)](#hybrid-ttl--event-driven-the-gold-standard)
6. [Write-Invalidate vs Write-Update](#write-invalidate-vs-write-update)
7. [Versioned URLs (Content-Addressable)](#versioned-urls-content-addressable)
8. [CDN Invalidation Strategies](#cdn-invalidation-strategies)
9. [Tag-Based Invalidation](#tag-based-invalidation)
10. [Pub/Sub Invalidation](#pubsub-invalidation)
11. [Race Conditions in Cache Invalidation](#race-conditions-in-cache-invalidation)
12. [Invalidation Patterns by Architecture](#invalidation-patterns-by-architecture)
13. [Invalidation at Scale — Real-World Challenges](#invalidation-at-scale--real-world-challenges)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Invalidation Strategy by System Design Problem](#invalidation-strategy-by-system-design-problem)
17. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

> *"There are only two hard things in Computer Science: cache invalidation and naming things."* — Phil Karlton

Cache invalidation is the process of **removing or updating cached data when the source of truth changes**. The challenge: the cache doesn't know when the source data changes unless something tells it.

**The fundamental tradeoff**: 
- Invalidate too aggressively → low cache hit ratio → poor performance.
- Invalidate too lazily → stale data → incorrect behavior.
- The sweet spot depends on how tolerant each use case is of staleness.

---

## Why Cache Invalidation Is Hard

### Problem 1: Multiple Writers
```
Time T1: Server A reads product price = $10 from DB → caches $10
Time T2: Admin updates price to $15 in DB
Time T3: Server A still serves $10 from cache (STALE!)
Time T4: Server B reads from DB → caches $15 → serves $15

Two servers serving different prices for the same product.
```

### Problem 2: Race Conditions
```
Time T1: Request A updates price to $15 in DB
Time T2: Request B updates price to $20 in DB
Time T3: Request B writes $20 to cache ← correct at this moment
Time T4: Request A writes $15 to cache ← WRONG! DB has $20 but cache has $15

Out-of-order cache updates cause inconsistency.
```

### Problem 3: Distributed Systems
```
User updates profile in US-East → DB updated → cache invalidated in US-East
But EU-West cache still has old data until:
  (a) TTL expires, or
  (b) Cross-region invalidation message arrives (100-200ms lag)
```

### Problem 4: Derived Data
```
User changes their profile photo.
Cached data affected:
  - user:profile:123 (their profile)
  - timeline entries containing their posts (denormalized author_avatar)
  - search index entries (profile picture in search results)
  - recommendation cards (their face in "suggested friends")
  
How many cache keys need invalidation? Unknown without tracking dependencies.
```

---

## TTL-Based Invalidation

### How It Works
Every cached item has a **Time-To-Live** — after TTL expires, the key is automatically removed. The next read triggers a cache miss and repopulation from the source.

```
SET user:profile:123 "..." EX 600  # Expires in 600 seconds (10 min)

Time 0:00  → Cache SET, fresh data
Time 9:59  → Data may be up to 9:59 stale
Time 10:00 → Key expires automatically
Time 10:01 → Cache miss → fetch from DB → re-cache
```

### Advantages
- **Simple**: No invalidation logic needed.
- **Self-healing**: Stale data always corrects itself within TTL.
- **Bounded staleness**: Maximum staleness = TTL duration.
- **No coordination**: No events, no pub/sub, no dependencies.

### Disadvantages
- **Staleness window**: Data can be stale for up to TTL duration.
- **TTL too short**: High miss rate → high DB load → poor performance.
- **TTL too long**: Extended staleness → user-visible inconsistency.
- **Not responsive**: Doesn't react to changes immediately.

### When TTL-Only Is Sufficient
- **Analytics dashboards**: 5-minute staleness is perfectly fine.
- **Trending topics**: Recomputed every 5 minutes anyway.
- **CDN static content**: Content addressed by hash, changes via new URL.
- **Non-critical counters**: Like counts, view counts.

---

## Event-Driven Invalidation

### How It Works
When the source data changes, an **event** is published. Cache invalidation consumers listen for these events and delete the affected cache keys.

```
┌──────────┐  price_updated  ┌─────────┐  invalidate  ┌─────────┐
│ Product  │ ──────────────→ │  Kafka   │ ───────────→ │  Redis   │
│ Service  │                 │          │              │ (cache)  │
└──────────┘                 └─────────┘              └─────────┘
```

### Implementation with Kafka
```python
# Producer (in Product Service)
def update_product_price(product_id, new_price):
    database.execute("UPDATE products SET price = ? WHERE id = ?", new_price, product_id)
    kafka.produce("product-events", {
        "event": "PriceUpdated",
        "product_id": product_id,
        "new_price": new_price,
        "timestamp": now()
    })

# Consumer (Cache Invalidation Service)
def on_product_event(event):
    if event["event"] == "PriceUpdated":
        redis.delete(f"product:{event['product_id']}")
        redis.delete(f"product:{event['product_id']}:price")
        # Also invalidate any derived caches
        redis.delete(f"category:{get_category(event['product_id'])}:products")
```

### Advantages
- **Immediate**: Cache invalidated within milliseconds of the change.
- **Precise**: Only affected keys are invalidated (no TTL-based guessing).
- **Reactive**: Responds to actual changes, not time-based assumptions.

### Disadvantages
- **Complex**: Requires event infrastructure (Kafka), consumers, error handling.
- **Single point of failure risk**: If the event consumer crashes, invalidations stop.
- **Event loss**: Kafka consumer crash or network issue can cause missed invalidations.
- **Dependency tracking**: Must know which cache keys are affected by each event.

---

## Hybrid: TTL + Event-Driven (The Gold Standard)

### The Two-Layer Strategy
```
Layer 1 (PRIMARY): Event-driven invalidation
  → Immediate cache deletion on data change
  → Handles 99%+ of invalidation cases

Layer 2 (SAFETY NET): TTL-based expiration
  → Self-corrects if an event is lost
  → Bounds maximum staleness
  → No data stays stale forever
```

### Implementation
```python
# On data change:
def update_product_price(product_id, new_price):
    # 1. Update database (source of truth)
    database.execute("UPDATE products SET price = ? WHERE id = ?", new_price, product_id)
    
    # 2. Publish event for immediate invalidation
    kafka.produce("product-events", {"event": "PriceUpdated", "product_id": product_id})

# Cache invalidation consumer:
def on_product_event(event):
    redis.delete(f"product:{event['product_id']}")  # Immediate invalidation

# On cache miss (repopulation):
def get_product(product_id):
    cached = redis.get(f"product:{product_id}")
    if cached:
        return cached
    
    product = database.query(f"SELECT * FROM products WHERE id = {product_id}")
    redis.set(f"product:{product_id}", product, ex=300)  # 5-min TTL (safety net)
    return product
```

### Why Both Layers?
```
Happy path (99%+):
  Data changes → Kafka event → cache deleted immediately → next read repopulates
  Staleness: < 1 second

Failure path (< 1%):
  Data changes → Kafka event LOST (consumer crash, network issue)
  → Cache still has old data
  → TTL expires after 5 minutes → next read repopulates from DB
  Staleness: up to 5 minutes (bounded by TTL)

Without TTL: If the event is lost, the cache stays stale FOREVER.
Without events: Cache is stale for up to TTL on EVERY change.
Together: Immediate invalidation + bounded staleness guarantee.
```

### Interview Script
> *"I'd use TTL plus event-driven invalidation as a two-layer strategy. The primary mechanism is Kafka events — when a product price changes, the Product Service publishes a `PriceUpdated` event, and the cache invalidation consumer deletes the Redis key immediately. The TTL (5 minutes) is the safety net — if the Kafka event is lost due to a consumer crash or network issue, the stale price self-corrects within 5 minutes. This handles both the happy path (immediate invalidation) and the failure path (bounded staleness)."*

---

## Write-Invalidate vs Write-Update

### Write-Invalidate (DELETE on write)
```
On write: DELETE cache key
Next read: Cache miss → fetch from DB → repopulate cache
```

**Advantages**: Simple, correct (no race conditions), works for all data.
**Disadvantages**: One extra DB read on the next request (the cache miss).

### Write-Update (SET on write)
```
On write: SET cache key with new value
Next read: Cache hit → return new value immediately
```

**Advantages**: No cache miss penalty after write.
**Disadvantages**: **Race conditions!** (See below)

### Why Write-Invalidate Is Safer

The classic race condition with write-update:
```
Request A writes price=$10 to DB → starts writing $10 to cache
Request B writes price=$15 to DB → writes $15 to cache (completes first)
Request A writes $10 to cache (completes second, OVERWRITES $15!)

Result: DB has $15, cache has $10 → INCONSISTENT
```

With write-invalidate:
```
Request A writes price=$10 to DB → deletes cache key
Request B writes price=$15 to DB → deletes cache key
Next read → cache miss → reads $15 from DB → caches $15

Result: DB has $15, cache has $15 → CONSISTENT
```

### Interview Script
> *"I'd never try to update the cache and the database simultaneously in application code — the race conditions are subtle and dangerous. Imagine two concurrent requests: Request A writes price=$10 to DB, Request B writes price=$15 to DB, Request B writes $15 to cache, Request A writes $10 to cache. Now the DB has $15 but the cache has $10. Instead, I'd use write-invalidate: delete the cache on any write, and let the next read repopulate it. Simple, correct, and the cache miss cost is a one-time 5ms penalty."*

---

## Versioned URLs (Content-Addressable)

### How It Works
Instead of invalidating cached content, **change the URL when the content changes**. The old URL stays cached forever. The new URL is fetched fresh.

```
Profile photo v1: cdn.example.com/img/user123_abc456.jpg
  → Cached for 1 year
  → User uploads new photo

Profile photo v2: cdn.example.com/img/user123_def789.jpg
  → New URL → cache miss → fetched from S3 → cached for 1 year
  → Old URL (abc456) stays cached but nobody requests it anymore
```

### Implementation Patterns

**Pattern 1: Content Hash in URL**
```
URL = cdn.example.com/img/{md5(file_content)}.jpg
Content changes → hash changes → URL changes → automatic cache bust
```

**Pattern 2: Version Number in URL**
```
URL = cdn.example.com/css/styles.v42.css
Deployment changes CSS → increment version → styles.v43.css
```

**Pattern 3: Build Hash**
```
URL = cdn.example.com/js/app.3f8a2b1c.js
Build tool (Webpack) generates content hash → unique per build
```

### Advantages
- **Zero invalidation complexity**: No purge API calls.
- **Zero stale content**: Every URL points to immutable content.
- **Maximum cache hit ratio**: `max-age=31536000` (1 year) for every asset.
- **CDN-friendly**: CDN never serves stale content.

### Disadvantages
- **Requires URL management**: Application must track current version URLs.
- **Not applicable to API responses**: Only works for addressable resources (images, CSS, JS).
- **Reference updates**: HTML/API responses referencing the asset need updating.

### Interview Script
> *"For the CDN layer, I'd use versioned URLs instead of active invalidation. When a user uploads a new profile picture, the URL changes from `cdn.example.com/img/abc123_v1.jpg` to `cdn.example.com/img/abc123_v2.jpg`. The old URL stays cached forever (which is fine — nobody requests it anymore). The new URL triggers a cache miss on first request, pulls from S3, and gets cached at the edge. Zero invalidation complexity, zero stale content, and the CDN cache hit ratio stays above 95%."*

---

## CDN Invalidation Strategies

### Strategy 1: Versioned URLs (Preferred)
```
No CDN invalidation needed.
Old URLs: cached forever, never requested.
New URLs: fetched fresh, then cached.
```

### Strategy 2: Purge API
```python
# CloudFront invalidation
cloudfront.create_invalidation(
    DistributionId='EDFDVBD6EXAMPLE',
    InvalidationBatch={
        'Paths': {'Quantity': 1, 'Items': ['/images/user123/*']},
        'CallerReference': str(time.time())
    }
)
# Takes 5-15 minutes to propagate to all edge locations
# First 1,000 paths/month free; $0.005 per path after that
```

### Strategy 3: Short TTL for Dynamic Content
```
Cache-Control: max-age=60, stale-while-revalidate=300
→ CDN serves cached content for 60 seconds
→ After 60s, CDN revalidates with origin in background
→ User always gets fast response (stale or fresh)
→ Maximum staleness: 60 seconds
```

### Strategy 4: Surrogate Keys (Fastly)
```
# Tag cached responses with metadata
Response header: Surrogate-Key: product-123 category-electronics

# Purge all content tagged with a key
fastly.purge_key("product-123")
# Instantly purges all cached pages containing product 123
```

---

## Tag-Based Invalidation

### Problem
When a product is updated, many cached pages need invalidation:
- Product detail page
- Category listing page
- Search results containing this product
- Recommendation widgets
- Comparison pages

### Solution: Tag Cache Entries
```python
# When caching a category page:
redis.set("page:category:electronics", html_content, ex=300)
redis.sadd("tags:product:123", "page:category:electronics")
redis.sadd("tags:product:456", "page:category:electronics")

# When product 123 is updated:
def invalidate_product(product_id):
    tag_key = f"tags:product:{product_id}"
    affected_keys = redis.smembers(tag_key)
    if affected_keys:
        redis.delete(*affected_keys)  # Delete all tagged cache entries
    redis.delete(tag_key)             # Clean up the tag set
```

### Benefits
- **Comprehensive**: All affected caches are invalidated, not just the obvious ones.
- **Decoupled**: The cache entry doesn't need to know what invalidates it; the tag mapping handles it.

---

## Pub/Sub Invalidation

### For Multi-Instance Cache Invalidation
When each application server has a local in-memory cache (L1) in addition to the shared Redis cache (L2):

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Server A │  │ Server B │  │ Server C │
│ L1 cache │  │ L1 cache │  │ L1 cache │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │              │              │
     └──────── Redis Pub/Sub ──────┘
                    │
              ┌─────┴─────┐
              │   Redis    │
              │ L2 cache   │
              └────────────┘
```

```python
# When data changes:
def invalidate_everywhere(key):
    redis.delete(key)                    # Invalidate L2 (Redis)
    redis.publish("cache-invalidation", key)  # Notify all servers

# On each server:
def on_invalidation_message(key):
    local_cache.delete(key)              # Invalidate L1 (local)
```

---

## Race Conditions in Cache Invalidation

### Race 1: Read-Before-Write-Completes
```
T1: Thread A reads stale value from DB (before update commits)
T2: Thread B updates DB to new value
T3: Thread B deletes cache key
T4: Thread A writes stale value to cache (from its T1 read)

Result: Cache has stale value even though DB was updated
```

**Mitigation**: Add a small delay before cache repopulation, or use write-invalidate only.

### Race 2: Double-Write Race
```
T1: Request A writes value=10 to DB
T2: Request B writes value=20 to DB  
T3: Request B sets cache to 20
T4: Request A sets cache to 10 (out of order!)

Result: DB = 20, Cache = 10
```

**Mitigation**: Use write-invalidate (DELETE) instead of write-update (SET). DELETE is idempotent and order-independent.

### Race 3: Invalidation-Before-Write
```
T1: Request A invalidates cache (preemptive)
T2: Request C reads → cache miss → reads OLD value from DB (write not committed yet)
T3: Request C populates cache with OLD value
T4: Request A commits new value to DB

Result: DB = new value, Cache = old value
```

**Mitigation**: Invalidate AFTER the DB write commits, not before. Or use a delayed invalidation (invalidate again 1 second after write).

### The Double-Delete Pattern
```python
def update_and_invalidate(key, new_value):
    # 1. Delete cache key
    redis.delete(key)
    
    # 2. Update database
    database.update(key, new_value)
    
    # 3. Wait briefly for any in-flight reads to complete
    time.sleep(0.5)  # 500ms
    
    # 4. Delete cache key AGAIN (catches any stale repopulations)
    redis.delete(key)
```

This handles most race conditions by ensuring any stale value cached during the write window gets cleaned up by the second delete.

---

## Invalidation Patterns by Architecture

### Monolith
```
Simple: Application writes DB + deletes cache in same code path.
No coordination issues.
```

### Microservices with Shared Cache
```
Service A updates data → publishes event to Kafka
Cache Invalidation Service consumes event → deletes Redis keys
Other services → read from Redis (miss → repopulate from DB)
```

### Microservices with Per-Service Cache
```
Service A updates data → publishes event
Service B consumes event → invalidates its OWN cache
Service C consumes event → invalidates its OWN cache
Each service manages its own cache independently.
```

### Multi-Region
```
Region US-East: Service updates DB → invalidates local Redis
→ Publishes cross-region invalidation event via Kafka MirrorMaker
Region EU-West: Receives event → invalidates local Redis
Cross-region lag: 100-500ms
During lag: EU-West serves stale data (acceptable for most use cases)
```

---

## Invalidation at Scale — Real-World Challenges

### Challenge 1: Cascading Invalidations
When a user changes their profile photo, it affects:
- Their profile cache
- Every tweet containing their embedded author info (denormalized)
- Every timeline containing their tweets
- Search results showing their profile

**Solution**: Accept eventual consistency for derived caches. Invalidate the primary cache (profile) immediately. Derived caches (timelines, search) update on their own TTL cycle or via background fan-out.

### Challenge 2: Invalidation Storms
A bulk update (e.g., price change affecting 100,000 products) triggers 100,000 cache invalidations simultaneously.

**Solution**: 
- Rate-limit invalidation processing.
- Batch invalidations.
- Use pipeline for Redis deletes.
- Accept that cache will repopulate lazily as items are requested.

### Challenge 3: The "Delete Everything" Temptation
When something goes wrong, the temptation is to flush the entire cache (`FLUSHALL`).

**Problem**: 100% miss rate → all traffic hits DB → DB overload → cascading failure.

**Solution**: Never flush the entire cache in production. Fix the root cause. If needed, invalidate specific key patterns incrementally.

---

## Interview Talking Points & Scripts

### The Hybrid Strategy
> *"I'd use TTL plus event-driven invalidation as a two-layer strategy. Kafka events for immediate invalidation. TTL as a safety net. Together they handle both the happy path and the failure path."*

### Versioned URLs for CDN
> *"For CDN content, I'd use versioned URLs instead of active invalidation. When content changes, the URL changes. Old URLs stay cached but are never requested. New URLs are fetched fresh. Zero invalidation complexity."*

### Write-Invalidate over Write-Update
> *"I'd never simultaneously update cache and database — the race conditions are subtle and dangerous. Write-invalidate (delete the cache key) is idempotent and order-independent. The one-time cache miss cost is trivial compared to the debugging cost of a race condition."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Only using TTL
**Problem**: Data can be stale for the entire TTL duration. For a 10-minute TTL, that's 10 minutes of showing the wrong price.

### ❌ Mistake 2: Only using events
**Problem**: If the event is lost, the cache stays stale forever. Events need TTL as a safety net.

### ❌ Mistake 3: Write-update without considering races
**Problem**: Concurrent writes cause DB-cache inconsistency. Always prefer write-invalidate (DELETE).

### ❌ Mistake 4: Invalidating before the DB write commits
**Problem**: Another thread reads the old value from DB and repopulates the cache between your invalidation and your commit.

### ❌ Mistake 5: Not considering CDN invalidation
**Problem**: Even if Redis is invalidated, the CDN edge cache may still serve stale content. Versioned URLs solve this.

---

## Invalidation Strategy by System Design Problem

| Problem | Primary Strategy | Safety Net | Notes |
|---|---|---|---|
| **E-Commerce prices** | Kafka event → DELETE | TTL 5 min | Price must be accurate at checkout |
| **User profiles** | Kafka event → DELETE | TTL 10 min | Read-your-writes via master routing |
| **Social feed** | Fanout invalidation | TTL 5 min | Invalidate timeline on new tweet |
| **CDN static assets** | Versioned URLs | max-age 1 year | Never actively invalidate |
| **Search index** | Async re-index via Kafka | TTL 2 min | Search lag of 1-2s is acceptable |
| **Session store** | Write-through (no invalidation) | TTL 30 min | Session expiry = invalidation |
| **Feature flags** | Kafka event → DELETE | TTL 30s | Must be fresh; flags affect behavior |
| **Leaderboard** | Real-time update (ZADD) | N/A | Redis sorted set IS the data store |
| **Analytics counters** | Write-behind flush | N/A | Redis is the hot store; DB is archive |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│            CACHE INVALIDATION CHEAT SHEET                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  GOLD STANDARD: Event-driven (primary) + TTL (safety net)   │
│                                                              │
│  STRATEGIES:                                                 │
│  TTL-only:        Simple, bounded staleness                  │
│  Event-driven:    Immediate, requires Kafka/events           │
│  Hybrid:          Best of both (USE THIS)                    │
│  Versioned URLs:  For CDN/static assets                      │
│  Write-invalidate: DELETE on write (PREFERRED)               │
│  Write-update:    SET on write (AVOID — race conditions)     │
│                                                              │
│  RACE CONDITION PREVENTION:                                  │
│  1. Use DELETE (invalidate) not SET (update)                 │
│  2. Invalidate AFTER DB commit, not before                   │
│  3. Double-delete pattern for extra safety                   │
│  4. Accept eventual consistency for derived caches           │
│                                                              │
│  CDN INVALIDATION:                                           │
│  Preferred: Versioned URLs (zero invalidation)               │
│  Alternative: Purge API (slow, 5-15 min propagation)         │
│  Dynamic: Short TTL + stale-while-revalidate                 │
│                                                              │
│  NEVER DO:                                                   │
│  ❌ FLUSHALL in production                                   │
│  ❌ Write-update without considering race conditions          │
│  ❌ Events without TTL safety net                             │
│  ❌ Invalidate before DB write commits                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 5: Caching Strategies** — Cache-aside, write-through, write-behind patterns
- **Topic 10: Message Queues vs Event Streams** — Kafka for event-driven invalidation
- **Topic 19: CDN & Edge Caching** — CDN-specific invalidation patterns
- **Topic 22: Delivery Guarantees** — Ensuring invalidation events aren't lost
- **Topic 31: Hot Partitions** — Invalidation under hot key conditions

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
