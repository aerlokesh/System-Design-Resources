# 🎯 Topic 31: Hot Partitions & Hot Keys

> **System Design Interview — Deep Dive**
> A comprehensive guide covering handling viral content, celebrity users, flash sales — key salting, multi-layer caching, dedicated shards, CDN absorption, hot key detection, and how to articulate hot key mitigation strategies with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [What Causes Hot Keys](#what-causes-hot-keys)
3. [The Impact of Hot Keys](#the-impact-of-hot-keys)
4. [Key Salting (Partition Spreading)](#key-salting-partition-spreading)
5. [Multi-Layer Caching for Hot Keys](#multi-layer-caching-for-hot-keys)
6. [Dedicated Shards and Special Handling](#dedicated-shards-and-special-handling)
7. [CDN Absorption for Read-Hot Content](#cdn-absorption-for-read-hot-content)
8. [Hot Key Detection](#hot-key-detection)
9. [Read-Hot vs Write-Hot: Different Problems, Different Solutions](#read-hot-vs-write-hot-different-problems-different-solutions)
10. [Real-World System Examples](#real-world-system-examples)
11. [Deep Dive: Applying Hot Key Solutions to Popular Problems](#deep-dive-applying-hot-key-solutions-to-popular-problems)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **hot key/partition** receives disproportionately more traffic than others, overwhelming that single node while others sit idle. This is the #1 cause of unexplained performance degradation in sharded systems.

```
Normal distribution:
  Partition 0: 20%   Partition 1: 20%   Partition 2: 20%   Partition 3: 20%   Partition 4: 20%
  
Hot key distribution:
  Partition 0: 80%   Partition 1: 5%    Partition 2: 5%    Partition 3: 5%    Partition 4: 5%

Partition 0 is overloaded → high latency → timeouts → cascading failures
Partitions 1-4 are idle → wasted capacity (80% of your infrastructure is underutilized)

This is fundamentally a load distribution problem:
  Sharding assumes roughly uniform key distribution.
  Hot keys violate that assumption.
```

---

## What Causes Hot Keys

### Common Causes

| Cause | Example | Read/Write | Scale of Impact |
|---|---|---|---|
| **Viral content** | One tweet gets 50% of all engagement | Both | Millions of reads + writes per second |
| **Celebrity user** | @BarackObama's profile viewed constantly | Read-heavy | 100K+ reads/sec for one key |
| **Flash sale** | One product gets 500K simultaneous buyers | Write-heavy | Extreme write contention |
| **Trending topic** | #SuperBowl during the game | Write-heavy | Millions of writes to one counter |
| **Breaking news** | CNN.com homepage article | Read-heavy | Massive read spike on one page |
| **Popular livestream** | #1 Twitch streamer live | Read-heavy | Millions of concurrent viewers |
| **Global event** | New Year's countdown across timezones | Both | Wave of messages/posts to shared entities |
| **Bot attack** | DDoS targeting specific user/resource | Read-heavy | Artificial traffic spike |

### Why Hot Keys Are Dangerous

```
Database impact:
  DynamoDB partition: ~3,000 RCU / 1,000 WCU per partition
  Hot key on one partition: All 100K reads/sec hit ONE partition
  Result: Throttling, ProvisionedThroughputExceededException
  Other partitions: Idle capacity goes unused

Redis impact:
  Redis is single-threaded per shard
  Hot key: All operations for that key block on one CPU core
  Result: Latency spike for ALL keys on that shard
  Other shards: Idle

Kafka impact:
  Each partition consumed by ONE consumer
  Hot partition key: All events for that key go to one partition → one consumer
  Consumer can't keep up → lag grows on that partition
  Other partitions/consumers: Idle
```

---

## The Impact of Hot Keys

### Quantified Impact

```
Scenario: E-commerce product catalog, 10M products, 100 shards

Normal day:
  Each shard: ~100K products, ~10K reads/sec
  Load: Even distribution, P99 latency < 5ms

Flash sale day (one product goes viral):
  Shard 47 (hot product): 500K reads/sec (50x normal)
  Other 99 shards: 5K reads/sec (half normal — traffic shifted to hot product)
  
  Shard 47 impact:
    CPU: 100% (saturated)
    P99 latency: 500ms → 5000ms (100x degradation)
    Error rate: 30% (requests timing out)
    
  Collateral damage:
    Other products on shard 47 also degraded!
    99,999 "innocent" products affected because they share a shard with the hot product.
    Users searching for completely different products get slow responses.
```

### Cascade Pattern

```
Hot key → shard overload → timeout → client retries → MORE load on shard
→ retry storm → shard completely unresponsive → cascading failure

The retry storm amplification:
  Original load: 500K reads/sec
  Timeout rate: 30%
  Retries: 150K retries/sec (with 3x retry = 450K additional)
  Total load: 950K reads/sec (nearly double)
  Timeout rate: 60% → more retries → positive feedback loop → crash
```

---

## Key Salting (Partition Spreading)

### The Problem

```
Kafka partition key: tweet_id = "viral_tweet_123"
All events for this tweet → ONE partition → ONE consumer
Consumer capacity: 10K events/sec
Actual load: 100K events/sec → consumer can't keep up → lag grows

Every engagement (like, retweet, reply) for this tweet goes to the same place.
Scaling up the cluster doesn't help — the hot key still maps to one partition.
```

### The Solution: Append Random Salt

```
Instead of:  partition_key = tweet_id
Use:         partition_key = tweet_id + "_" + random(0, 19)

"viral_tweet_123_0"  → Partition 7
"viral_tweet_123_1"  → Partition 12
"viral_tweet_123_2"  → Partition 3
"viral_tweet_123_3"  → Partition 19
...
"viral_tweet_123_19" → Partition 15

Load spread across 20 partitions instead of 1.
Each partition handles 5K events/sec (100K / 20) — within capacity.
```

### The Cost: Aggregation Step

```
Without salting: Counter for tweet_123 is in one place. Simple HGET → instant.
With salting:    Counter is spread across 20 keys.

Read aggregation:
  Must query all 20 salted keys and SUM:
    HGET counter:viral_tweet_123_0:likes → 5,231
    HGET counter:viral_tweet_123_1:likes → 4,892
    HGET counter:viral_tweet_123_2:likes → 5,103
    ...
    Total = SUM(all 20) = 98,472

  This takes 20 Redis reads instead of 1 → ~2ms instead of ~0.1ms
  Acceptable for the hot key, but unnecessary for normal keys.

Better approach: Flink aggregation step
  Flink reads 20 partial counts → SUM → writes total to serving layer every 10 seconds
  Dashboard reads the pre-merged total → no client-side aggregation
  Dashboard staleness: 10 seconds (acceptable for like counts)
```

### Dynamic Salting

```
Don't salt everything — only salt hot keys:

if key in known_hot_keys:
    salted_key = key + "_" + random(0, SALT_RANGE - 1)
    produce(salted_key, event)
else:
    produce(key, event)  # Normal keys: no salt, no aggregation overhead

known_hot_keys: Updated in real-time by hot key detection system.
  When a key becomes hot: Add to salt list, start aggregation.
  When a key cools down: Remove from salt list, stop aggregation.

Benefits:
  Normal keys: Zero overhead (no salt, no aggregation)
  Hot keys: Spread across N partitions (prevents overload)
  Transition: Automatic, based on real-time monitoring
```

### Choosing the Salt Range

```
Salt range = N (number of sub-keys):

Too low (N = 2):
  Only 2 partitions share the load
  Each handles 50% → may still be too much

Too high (N = 100):
  100 sub-keys → 100 reads for aggregation → slow
  Most sub-keys have very few events → overhead without benefit

Guidelines:
  Estimate hot key traffic: 100K events/sec
  Consumer capacity: 10K events/sec per partition
  Needed partitions: 100K / 10K = 10 partitions
  Salt range: N = 10-20 (2x safety margin)
  
  Adjust dynamically based on observed traffic.
```

---

## Multi-Layer Caching for Hot Keys

### Architecture

```
Layer 1: CDN (edge cache, 1-second TTL)
  99% of browse requests served from CDN edge
  Origin only sees 1% of traffic
  Latency: 5-20ms (edge → user)

Layer 2: Application-level local cache (in-process, 1-second TTL)
  Each API server caches hot data in-memory
  No network hop needed
  Latency: < 1ms
  
Layer 3: Redis (distributed cache, 5-second TTL)
  Shared across API servers
  1% of requests that miss local cache → Redis hit
  Latency: 1-2ms

Layer 4: Database
  Only touched by:
    - Purchase transactions (need exact, real-time data)
    - Cache misses (< 0.01% of browse traffic)
  Latency: 5-15ms

Result for a flash sale product page:
  100K requests/sec arriving
  → 99K served by CDN (never touch our servers)
  → 990 served by local/Redis cache
  → 10 actual DB queries (purchase transactions only)

  DB load reduced by 10,000x!
```

### Local Cache (In-Process) for Hot Keys

```
Standard caching: All servers query Redis for hot key → Redis becomes hot point
  100 API servers × 10K requests/sec each = 1M Redis reads/sec for ONE key
  Redis shard for that key: Saturated!

Solution: Each API server caches the hot key locally (in-process):
  
  API Server 1: local_cache["product:hot_123"] = {...}  TTL: 1 second
  API Server 2: local_cache["product:hot_123"] = {...}  TTL: 1 second
  ...
  API Server 100: local_cache["product:hot_123"] = {...} TTL: 1 second

  Each server hits Redis once per second to refresh.
  100 servers × 1 request/sec = 100 Redis reads/sec (instead of 1M!)
  
  10,000x reduction in Redis load for hot keys.
  
  Tradeoff: Data can be up to 1 second stale.
  For product browsing: "~47 in stock" can be 1 second old — perfectly fine.
  For purchase: Skip local cache, check exact inventory from DB.
```

### Cache Stampede Protection

```
Problem: Hot key expires → all 100 servers simultaneously hit Redis/DB
  → 100 concurrent DB queries for the same key
  → DB overloaded → all get slow → users see errors

Solution 1: Lock-based (only one server refreshes)
  server_1: SETNX cache_lock:hot_123 1 EX 5
    → acquired! → query DB → update cache → release lock
  server_2-100: Lock not acquired → wait 50ms → retry cache read
    → cache is now populated → hit cache

Solution 2: Probabilistic early refresh
  Each server randomly refreshes at 80-95% of TTL
  Spread the refresh across time instead of all at expiry
  
  TTL = 60 seconds
  Each server: Refresh at random time between 48-57 seconds
  Result: Staggered refreshes, no stampede

Solution 3: Background refresh (never expire)
  Cache never expires for reads (always return cached value)
  Background thread refreshes every 5 seconds
  Reads always hit cache → never stampede
  Tradeoff: Data can be up to 5 seconds stale
```

---

## Dedicated Shards and Special Handling

### Routing Hot Keys to Dedicated Resources

```
Normal keys: Consistent hashing → distributed across N shards
Hot keys:    Route to dedicated, oversized shard

if key in known_hot_keys:
    route_to_dedicated_shard(key)  # Bigger, more resources
else:
    route_via_consistent_hashing(key)  # Normal routing

Dedicated shard characteristics:
  - Larger instance (more CPU, memory, IOPS)
  - Fewer keys (only hot keys, more capacity per key)
  - Separate monitoring and alerting
  - Independent scaling from normal shards
  
Example:
  Normal shards: r6g.large (2 vCPU, 16 GB RAM) × 100 shards
  Hot key shard: r6g.4xlarge (16 vCPU, 128 GB RAM) × 1-3 shards
  
  Hot shard handles 10-50x more traffic than a normal shard.
```

### DynamoDB Adaptive Capacity

```
DynamoDB handles hot partitions automatically:

1. Initially: Provisioned capacity split evenly across partitions
   10,000 RCU across 5 partitions = 2,000 RCU each

2. Hot partition detected: DynamoDB "borrows" capacity from cold partitions
   Hot partition: 6,000 RCU (borrowed from others)
   Cold partitions: 1,000 RCU each (lending to hot partition)

3. If still insufficient: DynamoDB splits the hot partition
   Hot partition becomes 2 partitions → load shared
   
This is AUTOMATIC — no application changes needed.
But it has limits: ~3,000 RCU / 1,000 WCU per partition per second.
Beyond that: Use application-level sharding or caching.
```

---

## CDN Absorption for Read-Hot Content

### Using CDN to Absorb Read Spikes

```
Product page during flash sale:
  Set Cache-Control: public, max-age=1 (1-second freshness)
  
  At 100K requests/sec:
    CDN has 200 edge locations worldwide
    Each edge: Caches the response for 1 second
    CDN serves 99,000 requests from edge (cached)
    Origin gets ~200 requests/sec (one per second per edge location)
  
  Origin load: Reduced 500x by CDN
  User impact: Product info at most 1 second stale
  
  Purchase button → NOT cached (bypasses CDN, hits real-time inventory)
  
  Cache-Control strategy:
    Static assets (images, CSS): max-age=31536000 (1 year, versioned URL)
    Product description: max-age=3600 (1 hour, rarely changes)
    Product price: max-age=60 (1 minute, changes occasionally)  
    Inventory count: max-age=1 (1 second, changes frequently during sale)
    Cart/checkout: no-cache, no-store (always fresh, user-specific)
```

### Invalidation for Flash Sales

```
Before flash sale starts:
  Pre-warm CDN: Push product page to all edge locations
  
During sale:
  Inventory updates: CDN naturally refreshes every 1 second
  If item sells out: Invalidate CDN cache immediately
    CDN.invalidate("/product/hot_123")
    All edge locations clear cached version within 5 seconds
    Users see "SOLD OUT" within 5 seconds of sell-out
  
Post-sale:
  Extend cache TTL back to normal (3600 seconds)
  CDN returns to standard caching mode
```

---

## Hot Key Detection

### Real-Time Monitoring

```
Metrics to monitor:

1. Kafka partition lag imbalance:
   Expected: All partitions within 2x of average lag
   Hot partition: One partition's lag is 10x+ others
   Alert: If any partition lag > 5x average → flag as hot partition

2. Redis key access frequency:
   OBJECT FREQ key (requires LFU eviction policy)
   Hot key: One key has 100x+ more accesses than average
   Redis 6.0+: CLIENT TRACKING for hot key notifications

3. Database query distribution:
   Monitor query count per primary key / partition key
   Hot key: One row/partition gets > 10x average queries
   
4. CDN cache hit ratio anomaly:
   If one URL has abnormally LOW hit ratio → uncacheable hot content
   If one URL has abnormally HIGH request rate → hot content

5. API request distribution:
   Track request count per resource ID
   Hot resource: One resource_id gets > 10x average
```

### Automated Detection Pipeline

```
Architecture:
  All request logs → Kafka → Flink (Count-Min Sketch) → Detection

Flink hot key detection:
  For each 1-minute window:
    Count-Min Sketch tracks key frequencies
    Extract keys with frequency > threshold (e.g., 10x average)
    If key exceeds threshold for 3 consecutive windows → declare HOT
    
  Emit event: {key: "product:hot_123", frequency: 150K/min, detected_at: "..."}

Automated response:
  Detection → Redis set: SADD hot_keys "product:hot_123"
  
  Application checks hot_keys set:
    If hot → enable local caching, key salting, CDN override
    If not hot → normal processing
```

### Automated Response Pipeline

```
Detection (t=0):
  Flink detects key "product:flash_sale_item" at 100x normal traffic

Response 1 (t=5s):
  Add to hot_keys set in Redis
  Application enables in-process local caching for this key
  Immediate 100x reduction in Redis/DB load

Response 2 (t=30s):
  If traffic continues growing:
  Enable key salting for Kafka partition spreading
  Start Flink aggregation for salted sub-keys

Response 3 (t=60s):
  If still insufficient:
  Route key to dedicated shard with more resources

Response 4 (t=300s):
  If persists > 5 minutes:
  Alert on-call engineer for manual review
  Potential bot/DDoS assessment

Recovery (traffic normalizes):
  Key drops below threshold for 10 minutes
  → Remove from hot_keys set
  → Disable local caching, stop salting, remove dedicated routing
  → Return to normal processing
```

---

## Read-Hot vs Write-Hot: Different Problems, Different Solutions

### Read-Hot Keys

```
Problem: One key is read millions of times per second.
Example: Celebrity profile, viral article, popular product page.

Solutions (in order of effectiveness):
  1. CDN: Cache at edge → 99% absorption → 5-20ms latency
  2. Local cache: In-process cache per API server → < 1ms
  3. Redis replicas: Read from multiple Redis replicas → spread load
  4. Dedicated shard: Bigger Redis node for hot keys
  
  Key insight: Reads can be cached. Stale data is often acceptable.
  A 1-second-old product page is perfectly fine for browsing.
```

### Write-Hot Keys

```
Problem: One key is written to millions of times per second.
Example: Viral tweet like counter, trending hashtag counter, flash sale inventory.

Solutions (in order of effectiveness):
  1. Key salting: Spread writes across N sub-keys → N partitions/consumers
  2. Write buffering: Batch writes in memory → flush periodically
  3. CRDT counters: Conflict-free distributed counters (no coordination)
  4. Dedicated write partition: Bigger instance, more write IOPS
  
  Key insight: Writes CANNOT be cached. Must actually distribute the write load.
  Unlike reads, there's no "stale write" — every write must be processed.
```

### Combined (Read + Write Hot)

```
Problem: One key is both read and written to millions of times.
Example: Flash sale item — browsed AND purchased simultaneously.

Strategy: Split the read and write paths:
  
  READ PATH (browsable data — price, description, approximate count):
    CDN (1s TTL) → Local cache → Redis → DB
    Result: 99.99% of reads never hit the database
    Staleness: ≤ 1 second (fine for browsing)
  
  WRITE PATH (purchase/inventory — must be exact):
    API → directly to database (bypass all caches)
    Pessimistic lock: SELECT ... FOR UPDATE on inventory row
    Exact inventory count at time of purchase
    
  Separate concerns:
    "~47 remaining" (browsing, cached) ≠ "Can I buy?" (checkout, real-time)
    99.9% of users are browsing → served by cache
    0.1% of users are purchasing → served by database
```

---

## Real-World System Examples

### Twitter — Hot Tweet Handling

```
@ElonMusk tweets → 100M+ impressions within minutes

Problem:
  All engagement (likes, retweets, replies) for one tweet_id
  → All hits one Kafka partition, one Redis key, one Cassandra partition

Solutions:
  1. Like counter: Key salting (tweet_id + salt) → 20 sub-keys
     Flink aggregates sub-keys every 5 seconds → merged count
  2. Feed fanout: Celebrity exception (merge-at-read, not fanout-on-write)
  3. Profile page: CDN cached with 5-second TTL
  4. Timeline reads: Redis sorted set with local cache on API servers
```

### Amazon — Flash Sale (Prime Day)

```
One product: 500K concurrent viewers, 10K purchases/minute

Solutions:
  1. Product page: CDN cached (1-second TTL)
     500K viewers → CDN serves 499,500 → origin gets 500/sec
  2. Price/description: Local cache on API servers (1-second TTL)
  3. Inventory for browsing: Redis with 1-second TTL ("~47 left")
  4. Inventory for purchase: Direct DB with SELECT FOR UPDATE
     Only purchase transactions touch the real inventory count
  5. Cart: Per-user, not shared → no hot key issue
```

### Redis — Hot Key Detection (Built-in)

```
Redis 7.0+ provides built-in hot key detection:

  redis-cli --hotkeys
  → Lists the most frequently accessed keys

  CLIENT TRACKING:
  → Subscribes to invalidation messages for tracked keys
  → Can detect when a key is accessed unusually often

  OBJECT FREQ key:
  → Returns the access frequency of a key (requires LFU policy)
  
  MEMORY USAGE key:
  → Helps identify if hot key is also memory-heavy
```

---

## Deep Dive: Applying Hot Key Solutions to Popular Problems

### Social Media — Viral Post

```
Post goes viral: 10M views/hour, 500K likes/hour

Read path (views):
  CDN (5s TTL): 9.99M views served from edge
  API local cache: 9,000 views served from in-process cache
  Redis: 990 views served from cache
  Database: 10 views (negligible)

Write path (likes):
  Key salting: like_count:post_123_[0-19] → 20 partitions
  Each partition: 25K likes/hour (manageable)
  Flink aggregation: Merge 20 sub-keys every 5 seconds
  Serving: Redis key "like_count:post_123" = 502,847
  Display: "~503K likes" (approximate, fine for display)
  
  For ad billing (if likes affect billing):
  Exact count from batch reconciliation (nightly, from raw events)
```

### E-Commerce — Product Launch

```
New iPhone launch: Product page hit by 1M users simultaneously

Architecture:
  1. Pre-warm CDN 1 hour before launch
  2. Product page: max-age=5 (5-second freshness)
  3. Inventory display: "Available" / "Limited stock" / "Sold out"
     Updated via WebSocket push (not polling)
  4. Purchase flow:
     Click "Buy" → check real-time inventory → reserve → payment → confirm
     Each step: Direct to database, no caching
  5. If sold out: CDN invalidation → all users see "Sold out" within 10 seconds
```

### Gaming — Leaderboard Hot Key

```
Global leaderboard viewed by 100M players:

Problem: ZREVRANGE leaderboard 0 99 hit 100M times/day

Solution:
  1. Pre-compute top-100 into a separate Redis key every 10 seconds
     SET leaderboard_cache:top100 [serialized list] EX 15
  2. Local cache on game servers (10-second TTL)
  3. Clients receive top-100 from local cache
  
  Redis load for top-100 query:
    Without optimization: 100M reads/day on one sorted set key
    With optimization: 1 ZREVRANGE every 10 seconds + reads from cache key
    Reduction: 1,000,000x less load on the sorted set
  
  Individual rank ("What's MY rank?"):
    ZREVRANK leaderboard {user_id} → still hits sorted set
    But this is per-user, not per-page-load → much less frequent
    Cached per-user with 30-second TTL
```

---

## Interview Talking Points & Scripts

### Flash Sale Hot Key

> *"For a flash sale item where 100K users check inventory simultaneously, I'd use multi-layered caching. Layer 1: CDN caches the product page for 1 second — 99% of requests served from edge, never touching our servers. Layer 2: In-process local cache on each API server with a 1-second TTL — even the 1% that reaches us mostly hits local cache. Layer 3: Redis for the remaining cache misses. The actual database is only touched by purchase transactions, not browse requests. The 'approximately 47 remaining' count can be 1 second stale — accuracy only matters at the moment of checkout."*

### Key Salting

> *"For a viral tweet generating 500K likes per hour, I'd salt the Kafka partition key. Instead of partitioning by tweet_id — which sends everything to one partition — I'd partition by tweet_id + random(0-19), spreading the load across 20 partitions. A downstream Flink aggregation step merges the 20 partial counts every 5 seconds. The salting adds 5 seconds of staleness but prevents a single partition from becoming a bottleneck that backs up the entire pipeline."*

### Detection and Response

> *"I'd implement automated hot key detection using a Count-Min Sketch in our Flink pipeline. Any key exceeding 10x average frequency for 3 consecutive minutes triggers automatic mitigation: first, enable in-process local caching for that key (immediate 100x load reduction). If traffic continues growing, enable key salting for write spreading. If it persists beyond 5 minutes, alert the on-call engineer. When the key cools down, automatically disable the mitigations."*

### Read-Hot vs Write-Hot

> *"The solution depends on whether the key is read-hot or write-hot. Read-hot keys — like a celebrity profile viewed millions of times — are solved with caching: CDN at the edge, local cache on API servers, Redis as shared cache. Write-hot keys — like a viral tweet's like counter — can't be cached. Instead, I'd use key salting to spread writes across 20 partitions, with a Flink aggregation step to merge partial counts. For keys that are both read-hot and write-hot, I'd split the paths: caching for the read path, salting for the write path."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not considering hot keys in sharded designs
**Why it's wrong**: Sharding assumes uniform key distribution. One viral item breaks this assumption and overloads a single shard while others are idle.
**Better**: Design for hot keys from the start with multi-layer caching and key salting.

### ❌ Mistake 2: Only proposing one solution
**Why it's wrong**: Hot key mitigation requires layered defenses. CDN alone doesn't help with writes. Key salting alone doesn't help with reads.
**Better**: CDN (reads) + local cache (reads) + key salting (writes) + dedicated shards (extreme cases).

### ❌ Mistake 3: Not mentioning the aggregation cost of key salting
**Why it's wrong**: Salting spreads writes but complicates reads. Must aggregate sub-keys for the total count.
**Better**: Mention the Flink aggregation step that merges sub-keys every N seconds.

### ❌ Mistake 4: Ignoring the difference between read-hot and write-hot keys
**Why it's wrong**: Reads can be cached (stale data acceptable). Writes cannot be cached (every write must be processed).
**Better**: Different strategies for reads (caching layers) vs writes (salting/spreading).

### ❌ Mistake 5: Not quantifying the cache absorption
**Why it's wrong**: Saying "I'd add caching" without numbers doesn't demonstrate understanding.
**Better**: "CDN absorbs 99% of reads. Local cache absorbs 99% of the remaining 1%. Database sees 0.01% of total traffic."

### ❌ Mistake 6: Not mentioning hot key detection
**Why it's wrong**: You need to DETECT hot keys before you can mitigate them. Static configuration doesn't handle unpredictable virality.
**Better**: Real-time detection via Count-Min Sketch → automatic mitigation → automatic recovery.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          HOT PARTITIONS & HOT KEYS CHEAT SHEET               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CAUSES: Viral content, celebrity, flash sale, trending      │
│                                                              │
│  READ-HOT SOLUTIONS (caching layers):                        │
│  1. CDN: Absorb 99% of reads at edge (1-5s TTL)             │
│  2. Local cache: In-process on each API server (1s TTL)      │
│  3. Redis: Distributed cache (5s TTL)                        │
│  4. Dedicated shard: Bigger instance for known hot keys      │
│  Total: DB sees < 0.01% of total read traffic                │
│                                                              │
│  WRITE-HOT SOLUTIONS (spread the load):                      │
│  1. Key salting: Append random(0-N) to partition key         │
│     Cost: Aggregation step (Flink merge every 5-10s)         │
│  2. Write buffering: Batch in memory, flush periodically     │
│  3. CRDT counters: Conflict-free distributed counting        │
│  4. Dedicated write shard: More IOPS for hot keys            │
│                                                              │
│  DETECTION:                                                  │
│  Count-Min Sketch in Flink → detect keys > 10x average      │
│  Automated: Hot → enable caching/salting → cool → disable    │
│  Metrics: Partition lag imbalance, key frequency, query dist │
│                                                              │
│  CACHE STAMPEDE PREVENTION:                                  │
│  Lock-based: Only one server refreshes, others wait          │
│  Probabilistic early refresh: Stagger refreshes randomly     │
│  Background refresh: Never expire, refresh in background     │
│                                                              │
│  KEY INSIGHT:                                                │
│  Separate browse path (cached, approximate) from             │
│  purchase path (real-time, exact)                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 5: Caching Strategies** — Multi-layer caching for hot key reads
- **Topic 12: Sharding & Partitioning** — How sharding creates hot partition vulnerability
- **Topic 15: Rate Limiting** — Rate limiting as hot key protection
- **Topic 19: CDN & Edge Caching** — CDN absorption for read-hot content
- **Topic 30: Backpressure & Flow Control** — Backpressure from hot key traffic

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
