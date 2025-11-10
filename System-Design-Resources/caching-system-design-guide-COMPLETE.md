# Caching in System Design - Complete HLD Interview Guide

[Previous content from sections 1-9 remains the same, continuing from section 9.4]

### 4. Design Amazon E-commerce

**Cache Opportunities:**
```
✅ Product catalog
✅ Product images  
✅ Search results
✅ User shopping cart
✅ Reviews and ratings
✅ Recommendations
```

**What to Tell Interviewer:**
```
"E-commerce is extremely cache-friendly:

1. Product Catalog (Redis, 1 hour TTL):
   - Millions of products, but power law applies
   - Top 1000 products: 40% of traffic
   - Product details rarely change
   - Cache hit rate: 90%+

2. Images (CDN, 1 year TTL):
   - Billions of images
   - Static and immutable
   - 95% served from edge
   - Massive bandwidth savings

3. Search Results (15 min TTL):
   - Common searches repeated constantly
   - "iPhone 15" searched 100K times/day
   - Pre-compute and cache
   - Personalization: Cache per user segment

4. Shopping Cart (Redis, session TTL):
   - High read frequency during checkout
   - Session-based TTL
   - Write-through caching (consistency)
   - Backup in DB every 5 min

5. Performance Benefits:
   - Product page: 500ms → 50ms (10x faster)
   - Search: 2s → 200ms (10x faster)
   - Database load: 90% reduction
   - Infrastructure cost: 60% savings"
```

### 5. Design Uber/Lyft

**Cache Opportunities:**
```
✅ Driver locations (geo-spatial)
✅ Pricing estimates
✅ Map tiles
✅ User profiles
✅ Frequently visited places
```

**What to Tell Interviewer:**
```
"Real-time systems have unique caching needs:

1. Driver Locations (Redis Geo, 30 sec TTL):
   - Constantly changing (challenge!)
   - Use Redis geo-spatial indexes
   - TTL matches GPS update frequency
   - Trade-off: Slight inaccuracy acceptable
   
2. Map Tiles (CDN, permanent):
   - Static map images
   - Heavily accessed (every app open)
   - Perfect CDN use case
   - 99% hit rate

3. Pricing Estimates (Redis, 5 min TTL):
   - Based on demand/supply
   - Cache by (lat, lon, time) tuple
   - Refresh during surge pricing events

4. User Profiles (10 min TTL):
   - Name, payment methods
   - Read on every ride request
   - Infrequent updates

Key Insight:
'Even with frequently changing data like driver locations,
we can cache with short TTL (30s). 30-second staleness
is acceptable for UX, and we still get 90% cache hit rate
with massive latency improvement: 50ms → 2ms.'"
```

### 6. Design Facebook Messenger

**Cache Opportunities:**
```
✅ User online status
✅ Recent conversations list
✅ Message history (recent)
✅ User profiles
✅ Media files (CDN)
```

**What to Tell Interviewer:**
```
"Messaging requires careful caching:

1. Recent Messages (Redis, 7 days retention):
   - Last 100 messages per conversation
   - 95% of reads are recent messages
   - Older messages: Fetch from DB on-demand
   - Cache structure: Sorted Set (by timestamp)

2. Online Status (Redis, 30 sec TTL):
   - Presence information
   - Frequent updates but cached with short TTL
   - Acceptable latency: User won't notice 30s lag

3. Conversation List (5 min TTL):
   - List of chats with last message preview
   - Changes less frequently than messages
   - Invalidate on new message

4. Media (CDN, permanent):
   - Images, videos, voice notes
   - Immutable once sent
   - Edge caching for global access

Critical Decision:
'We use write-through for messages to ensure strong 
consistency - no user should miss a message due to 
cache issues. But online status uses shorter TTL 
with eventual consistency - acceptable trade-off.'"
```

### 7. Design Airbnb/Hotel Booking

**Cache Opportunities:**
```
✅ Listing details
✅ Search results
✅ Photos (CDN)
✅ Availability calendar
✅ Reviews
✅ Pricing
```

**What to Tell Interviewer:**
```
"Booking platforms have mixed consistency needs:

1. Listing Details (1 hour TTL):
   - Property info rarely changes
   - High read volume
   - Perfect for aggressive caching

2. Availability Calendar (5 min TTL):
   - Changes when bookings made
   - Must be reasonably current
   - Event-based invalidation on booking
   - Trade-off: Potential double-booking handled at transaction level

3. Search Results (15 min TTL):
   - Expensive query (geo + filters + sorting)
   - Cache by search parameters hash
   - Most users search popular locations/dates
   - Cache hit rate: 70%

4. Photos (CDN, 1 year):
   - Largest bandwidth consumer
   - Static content
   - 95% edge serving

5. Pricing (Cache-aside, 1 hour):
   - Dynamic pricing algorithm
   - Expensive to compute
   - Invalidate on demand changes

Consistency Strategy:
'For availability, we use optimistic locking at booking time.
Cache may be slightly stale (5 min), but final booking check
queries DB directly with row-level locking to prevent
double-booking. This gives us speed without sacrificing
correctness.'"
```

### 8. Design Payment System

**Cache Opportunities:**
```
⚠️ Limited - Strong consistency required
✅ Merchant details
✅ Exchange rates (5 min)
✅ User payment methods
✅ Fraud rules cache
❌ Transaction data (NO caching)
```

**What to Tell Interviewer:**
```
"Payment systems require extreme caution with caching:

1. What NOT to Cache:
   ❌ Account balances (must be real-time)
   ❌ Transaction history (audit requirements)
   ❌ Any financial data requiring ACID

2. What CAN be Cached:

   Merchant Details (1 hour TTL):
   - Business name, logo, address
   - Read-heavy, changes rarely
   - Safe to cache

   Exchange Rates (5 min TTL):
   - Updates every few minutes
   - Non-critical if slightly stale
   - Refresh-ahead strategy

   Fraud Rules (Redis, 10 min):
   - ML model parameters
   - Blacklisted IPs/cards
   - Real-time updates via pub/sub

Critical Insight:
'In payment systems, correctness > performance. We use
caching sparingly and only for non-critical paths. All
financial operations query database directly with proper
transaction isolation to ensure ACID properties.'"
```

### 9. Design News Feed (Facebook/LinkedIn)

**Cache Opportunities:**
```
✅ User feed posts (pre-computed)
✅ Post details
✅ User profiles
✅ Engagement counts (likes, comments)
✅ Trending topics
```

**What to Tell Interviewer:**
```
"News feed is the ultimate caching use case:

1. Feed Generation (Redis, 30 min TTL):
   - Pre-compute top 100 posts per user
   - Store as sorted list (by relevance score)
   - Fanout-on-write for regular users
   - Fanout-on-read for celebrities

2. Post Content (15 min TTL):
   - Viral posts read millions of times
   - Cache post + metadata
   - Lazy loading: More details on expand

3. Engagement Counts (Write-back, 30 sec):
   - Likes/comments change constantly
   - Eventually consistent acceptable
   - Batch update to DB every 30 seconds
   - High write performance

4. Trending Topics (5 min TTL):
   - Computed via streaming analytics
   - Global data, same for all users
   - Perfect for caching

Architecture:
┌─────────────────────────────────────────┐
│ Feed Service                            │
├─────────────────────────────────────────┤
│ 1. Check Redis for user feed            │
│ 2. If HIT: Return cached posts          │
│ 3. If MISS: Generate feed                │
│    - Query social graph                  │
│    - Score and rank posts                │
│    - Cache result                        │
│ 4. Async refresh in background          │
└─────────────────────────────────────────┘

Performance Impact:
- Feed load: 2s → 50ms (40x faster)
- DB queries: 50 per feed → 0 (cached)
- Infrastructure: Serve 100M users with 10% of DB capacity"
```

### 10. Design Rate Limiter

**Cache Opportunities:**
```
✅ Rate limit counters (Redis)
✅ User quotas
✅ Blacklisted IPs
✅ Allow lists
```

**What to Tell Interviewer:**
```
"Rate limiters are built on caching:

1. Redis as Primary Store:
   - NOT just a cache - it's the rate limit database
   - Atomic operations (INCR, EXPIRE)
   - Sub-millisecond performance
   - No SQL database needed

2. Counter Storage:
   Key: user:123:minute:1641234567
   Value: Request count
   TTL: 60 seconds

3. Algorithm (Sliding Window):
   ```
   current_count = INCR(key)
   IF current_count == 1:
       EXPIRE(key, 60)
   IF current_count > limit:
       REJECT
   ELSE:
       ALLOW
   ```

4. Performance:
   - Check rate limit: 1ms
   - Can handle 100K+ checks/second per instance
   - Distributed: Redis Cluster for millions/second

5. Blacklists (TTL varies):
   - Temporary blocks: 15 min - 24 hours
   - Permanent bans: No TTL
   - Fast lookup in Redis

This demonstrates Redis as more than cache - it's
a high-performance database for specific use cases."
```

---

## Common Caching Patterns

### 1. Cache Warming

**Problem:**
```
Cold cache after deployment/restart
All requests miss cache → Database overwhelmed
System slow for first minutes
```

**Solution:**
```
Pre-populate cache before traffic

Methods:
1. Lazy warmup: Let traffic naturally fill cache
2. Active warmup: Script loads critical data
3. Replay traffic: Use production logs

Example - Product Catalog:
  1. Identify top 1000 products (by traffic)
  2. Before deployment: Fetch and cache all 1000
  3. Result: 80% hit rate from minute 1
```

**Implementation:**
```python
def warm_cache():
    top_products = get_top_products(limit=1000)
    for product_id in top_products:
        product = db.get_product(product_id)
        cache.set(f"product:{product_id}", product, ttl=3600)
    print(f"Warmed cache with {len(top_products)} products")

# Run before accepting traffic
warm_cache()
```

### 2. Lazy Loading with Error Handling

**Pattern:**
```python
def get_user_profile(user_id):
    # Try cache first
    cache_key = f"user:{user_id}"
    
    try:
        cached = cache.get(cache_key)
        if cached:
            return cached
    except CacheException:
        # Cache down? Fall back to DB
        log.warn("Cache unavailable, querying DB")
    
    # Cache miss or cache error
    user = db.query(f"SELECT * FROM users WHERE id = {user_id}")
    
    try:
        cache.set(cache_key, user, ttl=600)
    except CacheException:
        # Failed to cache? Continue anyway
        log.warn("Failed to cache user profile")
    
    return user
```

**Key Points:**
```
✓ Cache failure doesn't break system
✓ Always have DB fallback
✓ Log cache issues for monitoring
✓ Graceful degradation
```

### 3. Cache-Aside with Stampede Prevention

**Pattern:**
```python
import threading

locks = {}

def get_product(product_id):
    cache_key = f"product:{product_id}"
    
    # Try cache
    product = cache.get(cache_key)
    if product:
        return product
    
    # Cache miss - acquire lock
    lock_key = f"lock:{cache_key}"
    lock = locks.setdefault(lock_key, threading.Lock())
    
    with lock:
        # Double-check cache (another thread may have filled it)
        product = cache.get(cache_key)
        if product:
            return product
        
        # Still not cached - query database
        product = db.get_product(product_id)
        cache.set(cache_key, product, ttl=3600)
        
    return product
```

**Benefits:**
```
✓ Only 1 database query per cache miss
✓ Other threads wait, then read from cache
✓ Prevents stampede on popular items
✓ Minimal additional latency (~10ms wait)
```

### 4. Write-Through with Async Background Refresh

**Pattern:**
```python
def update_user_profile(user_id, new_data):
    # Update database first
    db.update_user(user_id, new_data)
    
    # Update cache synchronously
    cache_key = f"user:{user_id}"
    cache.set(cache_key, new_data, ttl=600)
    
    # Trigger async refresh of derived data
    queue.publish("user.updated", {
        "user_id": user_id,
        "fields_changed": new_data.keys()
    })
    
    return {"status": "success"}

# Background worker
def refresh_user_feed(message):
    user_id = message["user_id"]
    # Regenerate and cache user's feed
    feed = generate_feed(user_id)
    cache.set(f"feed:{user_id}", feed, ttl=1800)
```

**Use Case:**
```
User updates profile → Immediately cached
Feed regeneration → Async, doesn't block user
Result: Fast user experience + eventual consistency
```

### 5. Two-Level Caching

**Pattern:**
```
┌──────────────────┐
│ Application      │
│ ┌──────────────┐ │  L1: In-memory (fast, small)
│ │ Local Cache  │ │  - 10MB, 1000 items
│ │ (Caffeine)   │ │  - 0.1ms access
│ └──────────────┘ │  - LRU eviction
└────────┬─────────┘
         │ (miss)
         ↓
┌──────────────────┐
│ Redis Cluster    │  L2: Distributed (slower, large)
│ (100GB)          │  - 1ms access
└──────────────────┘  - Shared across servers
         │ (miss)
         ↓
┌──────────────────┐
│ Database         │  L3: Persistent
└──────────────────┘  - 50ms access
```

**Implementation:**
```python
def get_product(product_id):
    cache_key = f"product:{product_id}"
    
    # L1: Check local cache
    product = local_cache.get(cache_key)
    if product:
        metrics.incr("l1_cache_hit")
        return product
    
    # L2: Check Redis
    product = redis.get(cache_key)
    if product:
        metrics.incr("l2_cache_hit")
        local_cache.set(cache_key, product, ttl=60)  # Promote to L1
        return product
    
    # L3: Query database
    metrics.incr("cache_miss")
    product = db.get_product(product_id)
    
    # Populate both caches
    redis.set(cache_key, product, ttl=3600)  # L2: 1 hour
    local_cache.set(cache_key, product, ttl=60)  # L1: 1 min
    
    return product
```

**Benefits:**
```
✓ Best performance (0.1ms for L1 hits)
✓ Reduced Redis load (L1 absorbs repeated requests)
✓ Shared cache across servers (L2)
✓ Automatic promotion of hot data to L1

Typical Hit Rates:
- L1: 30-40%
- L2: 50-60%
- L3 (DB): 10-20%
```

---

## Cache Technologies Comparison

### Redis vs Memcached

```
┌─────────────────┬──────────────┬──────────────┐
│ Feature         │ Redis        │ Memcached    │
├─────────────────┼──────────────┼──────────────┤
│ Data Structures │ Many (*)     │ Key-Value    │
│ Persistence     │ Yes (RDB/AOF)│ No           │
│ Replication     │ Yes (Master) │ No (client)  │
│ Clustering      │ Built-in     │ Client-side  │
│ Pub/Sub         │ Yes          │ No           │
│ Transactions    │ Yes          │ No           │
│ Lua Scripts     │ Yes          │ No           │
│ Max Value Size  │ 512MB        │ 1MB          │
│ Threading       │ Single       │ Multi        │
│ Performance     │ Fast         │ Faster (**)  │
│ Use Cases       │ General      │ Simple cache │
└─────────────────┴──────────────┴──────────────┘

(*) Redis: String, Hash, List, Set, Sorted Set, Bitmaps, HyperLogLog, Streams
(**) Memcached marginally faster for simple get/set at massive scale

Recommendation: 
Use Redis unless you need multi-threaded performance for
simple caching at extreme scale (1M+ QPS single instance)
```

### Redis Data Structures Use Cases

```
1. String (Basic Cache):
   user:123 → {"name":"John","age":30}
   
2. Hash (Structured):
   user:123 → {name: "John", age: 30, city: "NYC"}
   Operations: HGET user:123 name
   
3. List (Queues, Timeline):
   notifications:user123 → [notif1, notif2, notif3]
   Operations: LPUSH, RPOP (queue), LRANGE (pagination)
   
4. Set (Unique Items):
   online_users → {user123, user456, user789}
   Operations: SADD, SISMEMBER, SINTER (common friends)
   
5. Sorted Set (Rankings, Leaderboards):
   leaderboard → {user123: 1500, user456: 1200}
   Operations: ZADD, ZRANK, ZRANGE (top 10)
   
6. Geo (Location):
   drivers:location → {driver1: (lat, lon), driver2: (lat, lon)}
   Operations: GEOADD, GEORADIUS (nearby drivers)
```

### Cache Size Estimation

```
Example: E-commerce Product Cache

Data per product:
├── Product ID: 8 bytes (BIGINT)
├── Title: 100 bytes
├── Price: 8 bytes (DECIMAL)
├── Description: 500 bytes
├── Images URLs: 200 bytes
├── Category: 50 bytes
├── Metadata: 100 bytes
└── Overhead: 34 bytes (Redis)
───────────────────────────────
Total: ~1000 bytes = 1KB per product

Cache Requirements:
├── Total products: 10M
├── Want to cache: Top 100K (1%)
├── Memory needed: 100K × 1KB = 100MB
├── Redis instances: 1 (with room to grow)
└── Monthly cost: ~$50 (AWS ElastiCache)

ROI:
├── Queries saved: 90% of 10M QPS = 9M QPS
├── Database savings: $5000/month
├── Net savings: $4950/month (99:1 ROI!)
```

---

## Design Trade-offs

### 1. Consistency vs Performance

```
Strong Consistency (Slow):
  Every read queries database
  Latency: 50-100ms
  Use: Financial data, inventory

Eventual Consistency (Fast):
  Reads from cache (may be stale)
  Latency: 1-5ms
  Use: Profiles, posts, recommendations

Trade-off Decision Framework:
  Q: What's impact of stale data?
  Q: How long is acceptable staleness?
  Q: Can we detect and handle stale data?
```

### 2. Memory vs Hit Rate

```
Scenario: 1M products, 1GB available

Option A: Cache all 1M products
├── Memory: 1GB (full capacity)
├── Hit rate: 100%
└── Risk: No room for growth, evictions frequent

Option B: Cache top 100K products
├── Memory: 100MB (10% capacity)
├── Hit rate: 90% (if 80/20 holds)
├── Benefit: 900MB for other data
└── Outcome: Better overall system performance

Recommendation: Cache selectively, not exhaustively
```

### 3. TTL Selection Trade-offs

```
Short TTL (< 5 min):
✓ Fresher data
✓ Lower memory usage
✗ More cache misses
✗ Higher database load

Long TTL (> 1 hour):
✓ Higher hit rate
✓ Lower database load
✗ Staler data
✗ Higher memory usage

Dynamic TTL Strategy:
  Hot data (>1000 hits/hour): 1 hour TTL
  Warm data (100-1000 hits/hour): 15 min TTL
  Cold data (<100 hits/hour): 5 min TTL or don't cache
```

### 4. Cache Size vs Cost

```
Redis Memory Pricing (AWS ElastiCache):

1GB instance:   $50/month
5GB instance:   $200/month
20GB instance:  $700/month
100GB instance: $3000/month

Cost/GB decreases with larger instances, but:
- Start small (1-5GB)
- Monitor hit rates
- Scale based on ROI
- Consider eviction policies

Better to have 5GB cache with 90% hit rate
than 50GB cache with 92% hit rate if the
cost difference is $2500/month
```

### 5. Write Strategy Trade-offs

```
Cache-Aside (Lazy):
✓ Only caches needed data
✓ Cache failure doesn't break writes
✗ Three round trips on miss
✗ Potential stampede

Write-Through:
✓ Strong consistency
✓ Simpler read path
✗ Higher write latency
✗ Caches all data (may not be read)

Write-Back:
✓ Lowest write latency
✓ Batched database writes
✗ Data loss risk
✗ Complex error handling

Choose based on:
- Read:write ratio
- Consistency requirements
- Acceptable write latency
- Data loss tolerance
```

---

## Real-World Examples

### Case Study 1: Twitter Timeline Cache

**Problem:**
```
- 330M active users
- Average 100 follows per user
- Need to generate timeline in <200ms
```

**Solution:**
```
Hybrid Fanout Strategy:

Regular Users (<2000 followers):
├── Fanout-on-write (push model)
├── When user tweets: Push to all follower timelines
├── Timeline pre-computed in Redis
├── Read time: 2ms (just cache read)
└── Tradeoff: Write amplification acceptable

Celebrities (>2000 followers):
├── Fanout-on-read (pull model)
├── Timeline generated on-demand
├── Merge cached posts + celebrity posts
├── Read time: 50ms (but rare to follow many celebrities)
└── Tradeoff: Slightly slower, but saves massive writes

Cache Structure (Redis Sorted Set):
  timeline:user123 → [(tweet1, score1), (tweet2, score2), ...]
  Score = timestamp (for recency sorting)
  Keep top 1000 tweets per user
  
Operations:
  ZADD timeline:user123 timestamp1 tweet1  (add)
  ZREVRANGE timeline:user123 0 49          (get top 50)
  ZREMRANGEBYRANK timeline:user123 0 -1001 (trim to 1000)
```

**Results:**
```
✓ 99.9% of timelines served from cache
✓ Average latency: 5ms (40x improvement)
✓ Database load: 95% reduction
✓ Can handle 10K tweets/second
```

### Case Study 2: Netflix CDN Strategy

**Problem:**
```
- 230M subscribers globally
- Streaming 165M hours/day
- 15 petabytes bandwidth/day
- Must be <100ms latency globally
```

**Solution:**
```
Three-Tier CDN Architecture:

Tier 1: Open Connect Appliances (Edge)
├── Physical servers in ISP data centers
├── <10ms from most users
├── 95% of streams served here
├── 100TB storage per appliance
└── Cache hit rate: 95%+

Tier 2: Regional POPs
├── Fallback for edge misses
├── 50-100ms latency
├── Serves 4% of streams
└── Pre-populated with popular content

Tier 3: Origin (AWS)
├── Source of truth
├── <1% of streams
├── New releases before CDN propagation
└── Metadata and recommendations only

Caching Strategy:
  Popular Shows (Top 10%):
  ├── Permanently cached at edge
  ├── Multiple quality levels (480p to 4K)
  └── Cache hit rate: 99%

  Long Tail Content:
  ├── Cached on first request
  ├── LRU eviction after 30 days
  └── Cache hit rate: 70%

  Brand New Releases:
  ├── Pre-positioned to regional POPs
  ├── Gradually pushed to edge based on demand
  └── Hottest shows pushed within hours
```

**Results:**
```
✓ 15 PB/day served from cache (vs origin)
✓ Bandwidth cost savings: ~$1B/year
✓ Latency: <50ms for 99% of streams
✓ Handles launch day surges (millions concurrent)
```

### Case Study 3: Pinterest Image Serving

**Problem:**
```
- 450M monthly users
- 240B pins saved
- High-resolution images
- Billions of page views/month
```

**Solution:**
```
Multi-Layer Caching:

Layer 1: Browser Cache
├── Pins cached locally with ETags
├── Hit rate: 40% (repeat visitors)
└── Latency: 0ms (local)

Layer 2: CloudFront CDN
├── 200+ edge locations
├── Cache original + 5 resized versions
├── Hit rate: 85%
└── Latency: 10-50ms

Layer 3: Intermediate Origin (S3)
├── Caches transformed images
├── Handles CDN misses
├── Hit rate: 95% (of CDN misses)
└── Latency: 100ms

Layer 4: Image Processing Service
├── Generates new sizes on-demand
├── Caches to S3
└── <5% of requests

Image Variants Cached:
  236x → Mobile thumbnail
  564x → Grid view
  736x → Desktop medium
  1200x → Full screen
  Original → Download

TTL Strategy:
  - Pins: 1 year (content-addressed URLs)
  - User profiles: 1 day
  - Board covers: 1 hour
```

**Results:**
```
✓ 95% of images served from edge (CloudFront)
✓ 3% of requests hit origin
✓ Average image load: 30ms globally
✓ Bandwidth savings: ~80% ($10M+/year)
```

### Case Study 4: Amazon Product Catalog

**Problem:**
```
- 350M+ products
- Billions of product views/day
- Frequent price/inventory updates
- Need <200ms page load
```

**Solution:**
```
Tiered Caching Strategy:

Hot Products (Top 100K = 0.03%):
├── Cached in Redis (in-memory)
├── TTL: 5 minutes
├── Serves 60% of traffic
├── Latency: 2ms
└── Cost: 5GB Redis = $200/month

Warm Products (Next 900K = 0.27%):
├── Cached in Redis with longer TTL
├── TTL: 30 minutes
├── Serves 30% of traffic
├── Latency: 2ms
└── Additional cost: 20GB = $600/month

Cold Products (Rest = 99.7%):
├── NOT cached (direct database query)
├── Serves 10% of traffic
├── Latency: 50ms
└── Cost: $0 (existing DB)

Cache Key Design:
  product:{id}:us → Full product for US
  product:{id}:uk → Full product for UK (different price)
  product:{id}:basic → Basic info (no price)

Invalidation:
  Price Change:
  ├── Delete product:{id}:{all_regions}
  ├── Publish event to services
  └── Let cache naturally repopulate

  Inventory Update:
  ├── Update only if crossing threshold (0, <10, >10)
  ├── Avoid invalidation on every sale
  └── Eventually consistent acceptable
```

**Results:**
```
✓ 90% of requests served from cache
✓ Average page load: 80ms (was 800ms)
✓ Database queries reduced by 90%
✓ Infrastructure cost: 70% lower
✓ Can handle Prime Day traffic (10x normal)
```

### Case Study 5: Uber Driver Location

**Problem:**
```
- 5M active drivers
- Location updates every 4 seconds
- Need to find drivers within 5km in <100ms
- 100M+ location queries/day
```

**Solution:**
```
Redis Geospatial Indexes:

Data Structure:
  drivers:city:san_francisco → {
    driver1: (37.7749, -122.4194),
    driver2: (37.7849, -122.4094),
    ...
  }

Operations:
  # Update driver location
  GEOADD drivers:city:sf -122.4194 37.7749 driver123

  # Find drivers within 5km
  GEORADIUS drivers:city:sf -122.4194 37.7749 5 km WITHDIST
  
TTL Strategy:
  ├── Driver location: 30 second TTL
  ├── Auto-expires if driver offline
  ├── Prevents stale data
  └── Driver reconnects on timeout

Sharding by City:
  ├── Separate key per city
  ├── Most queries local to city
  ├── Scales horizontally
  └── No global lock contention

Optimization:
  ├── Cache result of GEORADIUS for 5 seconds
  ├── Many riders search simultaneously
  ├── Same results for nearby locations
  └── Reduces load by 80%
```

**Results:**
```
✓ Find nearby drivers: <10ms average
✓ Handle 100K+ concurrent location updates
✓ 30-second acceptable staleness
✓ No database needed for location data
✓ Scales to millions of drivers globally
```

---

## Interview Cheat Sheet

### When Interviewer Asks: "Where would you use caching?"

**Step-by-Step Response:**

1
