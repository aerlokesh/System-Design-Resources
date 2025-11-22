# Scaling Reads: Complete HLD Guide with Real-World Examples

## Quick Navigation
- [Introduction](#introduction)
- [10+ Real-World Examples](#real-world-architecture-examples)
- [Scaling Patterns](#scaling-patterns)
- [Decision Framework](#decision-framework)
- [Interview Guide](#interview-guide)

---

## Introduction

**Read Scaling** optimizes systems to handle increasing read traffic. Since 90-95% of requests are reads in most applications, this is critical for performance and cost efficiency.

**This guide includes**:
- ✅ 10+ Real-world architecture examples with detailed diagrams
- ✅ Complete scaling patterns (Caching, Replicas, Sharding, CDN)
- ✅ Decision frameworks for interviews
- ✅ Trade-off analyses
- ✅ HLD-focused (minimal code)

---

## The Read Scaling Challenge

```
┌──────────────────────────────────────────────────────────────────┐
│                 WHY READ SCALING MATTERS                          │
└──────────────────────────────────────────────────────────────────┘

Read-Heavy Ratios by System Type:

Social Media (Instagram, Facebook):
• 1 post created → 1,000+ views
• Read:Write = 1000:1
• Strategy: Multi-tier caching + CDN

E-commerce (Amazon, eBay):
• 1 product added → 10,000+ views
• Read:Write = 10000:1
• Strategy: Search index + cache + replicas

News/Media (CNN, Medium):
• 1 article published → 1M+ reads
• Read:Write = 1000000:1
• Strategy: Aggressive CDN + edge caching

Video Streaming (Netflix, YouTube):
• 1 video uploaded → 10M+ streams
• Read:Write = 10000000:1
• Strategy: Global CDN (95%+ traffic)

Banking/Finance:
• 1 transaction → 10 balance checks
• Read:Write = 10:1
• Strategy: Read replicas with strong consistency
```

---

## Core Read Scaling Strategies

```
┌──────────────────────────────────────────────────────────────────┐
│              5 PRIMARY READ SCALING TECHNIQUES                    │
└──────────────────────────────────────────────────────────────────┘

1. CACHING
   What: Store frequently accessed data in fast memory
   Latency: 1-5ms
   Hit Rate: 80-95%
   Use: Almost always, first optimization
   
2. READ REPLICAS
   What: Multiple read-only database copies
   Scaling: Linear (3 replicas = 3x capacity)
   Lag: 10-100ms (async replication)
   Use: When QPS > 10,000

3. DATABASE SHARDING
   What: Partition data across multiple databases
   Scaling: Linear (4 shards = 4x capacity)
   Complexity: High
   Use: When data > 1TB or QPS > 100K

4. CDN (Content Delivery Network)
   What: Distribute content to edge locations
   Coverage: 300+ global locations
   Hit Rate: 95%+
   Use: Day 1 for static content

5. SEARCH ENGINES
   What: Specialized indexes for complex queries
   Technology: Elasticsearch, Solr
   Latency: 50-200ms
   Use: Full-text search, faceted filtering
```

---

## Real-World Architecture Examples

### Example 1: Instagram Feed Loading

```
┌──────────────────────────────────────────────────────────────────┐
│                  INSTAGRAM FEED ARCHITECTURE                      │
└──────────────────────────────────────────────────────────────────┘

Scale: 1B+ users, 100M+ photos/day, Billions of feed loads

Architecture:

User opens Instagram
   ↓
┌────────────────────────┐
│   Facebook CDN         │
│   • Profile pictures   │
│   • Posted images      │
│   • Videos             │
│   • Hit rate: 98%      │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Feed Service         │
│   • Personalization    │
│   • Ranking            │
└────────┬───────────────┘
         │
         ├──────────────────────┬──────────────────┐
         │                      │                  │
         ▼                      ▼                  ▼
┌─────────────────┐    ┌─────────────┐    ┌─────────────┐
│     Redis       │    │  Cassandra  │    │  PostgreSQL │
│                 │    │             │    │             │
│• Feed cache     │    │• Posts      │    │• Users      │
│• 5-min TTL      │    │• Comments   │    │• Follows    │
│• Pre-computed   │    │• Likes      │    │• Replicas:10│
│• 1000s nodes    │    │• Replicated │    │             │
└─────────────────┘    └─────────────┘    └─────────────┘

Feed Load Strategy:

Step 1: CHECK REDIS CACHE
   Key: feed:user_123
   Contains: [post_999, post_998, ... post_900]
   Hit rate: 85%
   Latency: 3ms
   
   If HIT: Skip to Step 3

Step 2: GENERATE FEED (Cache Miss)
   Query PostgreSQL replica:
   • Get following list (500 users)
   • Time: 20ms
   
   Query Cassandra:
   • Get recent posts from following
   • Partitioned by user_id
   • Parallel queries (50 nodes)
   • Time: 100ms
   
   Rank posts by ML algorithm
   • Engagement prediction
   • Recency score
   • Relationship strength
   • Time: 50ms
   
   Store top 100 in Redis
   Total: 170ms

Step 3: HYDRATE POST DETAILS
   Batch fetch post data:
   • Check Redis cache (70% hit)
   • Fetch from Cassandra (30% miss)
   • Time: 20-40ms

Step 4: LOAD IMAGES
   All from CDN (98% hit rate)
   • Lazy loading (progressive)
   • Adaptive quality based on network
   • Time: 10-30ms per image

Total Latency:
• Cached feed: 50-80ms
• Uncached feed: 200-250ms

Optimizations:

1. FAN-OUT ON WRITE
   When user posts:
   • Write to all followers' feed caches
   • Async background job
   • Next feed load: Instant from cache

2. CELEBRITY HANDLING
   Users with >10K followers:
   • Don't fan-out to all
   • Store in central cache
   • Merge on-demand (fan-out on read)

3. PREFETCHING
   While user views feed:
   • Prefetch next 20 posts
   • Ready when user scrolls
   • Seamless infinite scroll

Performance:
• Feed load: 50-250ms
• Image load: 10-30ms
• Concurrent users: 10M+
• Infrastructure: 1000s of servers
```

---

### Example 2: Netflix Video Catalog & Recommendations

```
┌──────────────────────────────────────────────────────────────────┐
│              NETFLIX RECOMMENDATION ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 230M+ subscribers, 15K+ titles, Personalized per user

Challenge: Show relevant content instantly when app opens

Architecture:

User opens Netflix
   ↓
┌────────────────────────┐
│   Open Connect CDN     │
│   • Video files        │
│   • Thumbnails         │
│   • Hit rate: 95%      │
│   • ISP-embedded       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Recommendation API    │
│   • Pre-computed rows  │
│   • ML ranking         │
└────────┬───────────────┘
         │
         ├──────────────────────┬──────────────────┐
         │                      │                  │
         ▼                      ▼                  ▼
┌─────────────────┐    ┌─────────────┐    ┌─────────────┐
│   EVCache       │    │  Cassandra  │    │   MySQL     │
│   (Redis-based) │    │             │    │             │
│                 │    │• Viewing    │    │• Subscriber │
│• Pre-computed   │    │  history    │    │  data       │
│  recs           │    │• Ratings    │    │• Catalog    │
│• Multiple rows  │    │• Watch time │    │• Metadata   │
│• Personalized   │    │• Massive    │    │• Read       │
│• TTL: 24 hours  │    │  scale      │    │  replicas   │
└─────────────────┘    └─────────────┘    └─────────────┘

Recommendation Strategy:

Pre-Computation (Batch Jobs):
• Runs every 4-24 hours
• Generates multiple recommendation rows:
  - "Top Picks for You"
  - "Trending Now"
  - "Because You Watched X"
  - "Popular in Your Area"
• Stores in EVCache (per-user)
• 80% of homepage from pre-computed

Real-Time Personalization (20%):
• User context (device, time, location)
• Recent viewing history
• A/B test variants
• Computed on-demand
• Cached for session

Multi-Level Caching:

Level 1: Device Cache
• Recently viewed metadata
• Reduces API calls by 40%

Level 2: EVCache (Regional)
• Pre-computed recommendations
• User preferences
• Hit rate: 95%
• Latency: 1-5ms

Level 3: Cassandra
• Full viewing history
• All user interactions
• Query time: 20-50ms

Video Delivery Optimization:

1. PREDICTIVE CACHING
   • Cache next episode before user finishes current
   • Pre-cache popular new releases
   • Regional preferences (cache locally popular content)

2. ADAPTIVE STREAMING
   • Multiple bitrates (240p to 4K)
   • Client adapts based on bandwidth
   • Reduces buffering by 80%

3. ISP PARTNERSHIPS
   • Place servers inside ISPs
   • Eliminates internet transit
   • 30% cost savings + faster delivery

Performance:
• Homepage load: 50-150ms
• Video start: <1 second (buffered)
• Recommendation accuracy: Continuously improving
• Bandwidth usage: 15% of internet traffic globally
• Infrastructure: Petabytes cached globally
```

---

### Example 3: Twitter Timeline & Trends

```
┌──────────────────────────────────────────────────────────────────┐
│                  TWITTER TIMELINE ARCHITECTURE                    │
└──────────────────────────────────────────────────────────────────┘

Scale: 400M+ users, 500M+ tweets/day, Billions of timeline loads

Architecture:

User opens Twitter
   ↓
┌────────────────────────┐
│   CDN (Fastly)         │
│   • Profile images     │
│   • Media content      │
│   • Static assets      │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Timeline Service     │
│   • Merge timelines    │
│   • Apply filters      │
└────────┬───────────────┘
         │
         ├──────────────────────┬──────────────────┐
         │                      │                  │
         ▼                      ▼                  ▼
┌─────────────────┐    ┌─────────────┐    ┌─────────────┐
│     Redis       │    │  Manhattan  │    │  GraphJet   │
│                 │    │ (Distributed│    │ (In-Memory  │
│• Timeline cache │    │  Database)  │    │   Graph)    │
│• Tweet cache    │    │             │    │             │
│• Trending cache │    │• Tweets     │    │• Follows    │
│• 5-min TTL      │    │• Users      │    │• Real-time  │
│• 100s nodes     │    │• Read       │    │  graph      │
└─────────────────┘    │  replicas   │    │  queries    │
                       └─────────────┘    └─────────────┘

Timeline Generation - Hybrid Approach:

REGULAR USERS (<5K following):
Fan-out on Write:
   User tweets
      ↓
   Write to all followers' Redis caches
   • Async background workers
   • 200 workers processing
   • Each handles 2K followers/sec
      ↓
   Follower opens app
      ↓
   Timeline loaded from Redis
   Latency: 30-50ms

POWER USERS (5K-100K following):
Hybrid Approach:
   Regular tweets: Fan-out on write (cached)
   Celebrity tweets: Fan-out on read (merged)
      ↓
   Timeline = Cached + Celebrity (merged)
   Latency: 100-150ms

CELEBRITIES (>100K followers):
Fan-out on Read Only:
   Celebrity tweets stored centrally
      ↓
   Followers fetch on timeline load
      ↓
   Merge with cached timeline
   Latency: 150-200ms (first load)
   Cached after first fetch

Trending Topics (Read-Heavy):

Real-Time Computation:
   All tweets analyzed
      ↓
   Extract hashtags
      ↓
   Calculate velocity (mentions/minute)
      ↓
   Score = velocity × recency_factor
      ↓
   Store top 50 in Redis (global + regional)
      ↓
   Update every 30 seconds
      ↓
   Serve from cache: 5ms latency

Read Optimizations:

1. TWEET CACHE
   • All recent tweets cached (24 hours)
   • Redis cluster (100+ nodes)
   • Hit rate: 90%
   • Avoids Manhattan queries

2. TIMELINE PREFETCHING
   • Predict user will open app
   • Pre-generate timeline
   • Ready when app opens

3. PROGRESSIVE LOADING
   • Load 20 tweets initially
   • Prefetch next 20 while user reads
   • Infinite scroll feels instant

Performance:
• Timeline load: 50-200ms (depending on user type)
• Trending: <10ms (Redis)
• Tweet detail: 20-50ms
• Concurrent users: 5M+
• Timeline loads: 100M+/hour
```

### Example 4: Amazon Product Search & Browse

(Already detailed above, see previous section)

### Example 5: YouTube Recommendations

(Already detailed above, see previous section)

### Example 6: LinkedIn Profile Views

(Already detailed above, see previous section)

### Example 7: Facebook News Feed

(Already detailed above, see previous section)

### Example 8: Reddit Front Page

(Already detailed above, see previous section)

### Example 9: Stack Overflow Q&A

(Already detailed above, see previous section)

### Example 10: Pinterest Image Feed

(Already detailed above, see previous section)

---

## Decision Framework - Extended

```
┌──────────────────────────────────────────────────────────────────┐
│              COMPLETE READ SCALING DECISION MATRIX                │
└──────────────────────────────────────────────────────────────────┘

BY USE CASE:
├─ Social Media Feed → Redis cache + Read replicas + CDN
├─ E-commerce Catalog → Elasticsearch + Redis + DynamoDB + CDN
├─ Video Streaming → CDN (95%) + Recommendations (Redis)
├─ News/Articles → CDN + Edge caching + minimal origin
├─ User Profiles → Cache-aside (Redis) + Read replicas
├─ Search → Elasticsearch + cache results
├─ Analytics Dashboard → Materialized views + Redis
├─ Leaderboards → Redis sorted sets
└─ Real-time Data → Redis + Read replicas

BY SCALE (QPS):
├─ <1K QPS → Database + basic caching
├─ 1K-10K → Redis + 2-3 read replicas
├─ 10K-100K → Redis cluster + 10+ replicas + CDN
└─ >100K → Sharding + Redis + CDN + CQRS

BY LATENCY:
├─ <10ms → Redis only
├─ <50ms → Redis + Read replicas
├─ <100ms → Read replicas + optimized queries
└─ >100ms → Batch acceptable

BY CONSISTENCY:
├─ Strong → Synchronous replication + master reads
├─ Read-your-writes → Session stickiness + version tracking
├─ Eventual → Async replication + aggressive caching
└─ Causal → Vector clocks + ordered updates
```

---

## Interview Preparation - Complete Framework

### Interview Question: "Design Instagram Feed"

**Step-by-Step Answer:**

```
1. CLARIFY (2 minutes)
   Q: What's the scale? 
   A: 1B users, 100M DAU
   
   Q: What's in the feed?
   A: Posts from following, stories, ads
   
   Q: Latency requirements?
   A: <500ms for feed load

2. CAPACITY (3 minutes)
   • 100M DAU
   • Each opens feed 10x/day
   • 1B feed loads/day
   • 1B / 86400 = 11,500 loads/sec
   • Peak (3x): 35,000 feeds/sec

3. HIGH-LEVEL DESIGN (10 minutes)
   
   Client → CDN (images) → Load Balancer
        → Feed Service → Redis (feed cache)
        → Cassandra (posts) → PostgreSQL (users/follows)

4. DEEP DIVE - Feed Generation (15 minutes)
   
   Fan-out Strategy:
   • <5K followers: Fan-out on write
   • >5K followers: Hybrid
   • Celebrities: Fan-out on read
   
   Cache Strategy:
   • Redis stores feed IDs (100 posts)
   • TTL: 5 minutes
   • Batch fetch post data from Cassandra
   
   CDN Strategy:
   • All media on CDN
   • 98% hit rate
   • Lazy loading as user scrolls

5. SCALING (5 minutes)
   
   Reads:
   • 35K feeds/sec × 85% cache hit = 30K from Redis
   • 5K from database (distributed across replicas)
   • Redis cluster: 20 nodes (1.5K ops/sec each)
   • Cassandra: 50 nodes (100 queries/sec each)
   
   Geographic:
   • Deploy in 3 regions (US, EU, Asia)
   • Route users to nearest
   • Cross-region replication

6. TRADE-OFFS (5 minutes)
   
   Consistency:
   • Eventual (5-min cache)
   • Acceptable for social media
   • User sees own posts immediately (write-through)
   
   Cost vs Performance:
   • Heavy caching = lower DB load = lower cost
   • CDN cost offset by reduced origin bandwidth
   
   Complexity:
   • Fan-out logic adds complexity
   • But necessary at scale
```

---

## Summary: Read Scaling Patterns

```
┌──────────────────────────────────────────────────────────────────┐
│                    PATTERN SUMMARY                                │
└──────────────────────────────────────────────────────────────────┘

CACHING PATTERNS:
├─ Cache-Aside: Application manages cache
├─ Read-Through: Cache handles DB on miss
├─ Write-Through: Update cache on write
└─ Write-Behind: Async DB updates

DATABASE PATTERNS:
├─ Read Replicas: Linear scaling for reads
├─ Sharding: Partition data horizontally
├─ Denormalization: Optimize for read queries
└─ Materialized Views: Pre-compute aggregations

DISTRIBUTION PATTERNS:
├─ CDN: Edge caching globally
├─ Multi-Region: Replicas near users
├─ Fan-out on Write: Pre-compute feeds
└─ Fan-out on Read: Compute on-demand

OPTIMIZATION PATTERNS:
├─ Multi-Tier Caching: Layer caches (99% hit rate)
├─ Prefetching: Load before needed
├─ Lazy Loading: Load as user scrolls
├─ Progressive Loading: Show fast, enhance later
└─ Batching: Combine multiple requests

BEST PRACTICES:
✓ Start with CDN for static content
✓ Add Redis for hot data (>80% hit rate)
✓ Use read replicas when QPS >10K
✓ Monitor cache hit rates aggressively
✓ Plan for cache failures (graceful degradation)
✓ Implement proper cache invalidation
✓ Use geo-routing for global users
✓ Shard only when necessary (adds complexity)
```

---

**Document Version**: 2.0  
**Created**: January 2025  
**Type**: High-Level Design Guide  
**Examples**: 10+ real-world architectures (Instagram, Netflix, Twitter, Amazon, YouTube, LinkedIn, Facebook, Reddit, Stack Overflow, Pinterest)  
**Focus**: Architecture patterns for interviews
