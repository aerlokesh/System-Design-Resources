# Twitter (X) Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Twitter's engineering blog, conference talks, and open-source projects. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Twitter Engineering Blog (blog.twitter.com/engineering), @Scale/QCon/Strange Loop talks, Twitter open-source projects

---

## Table of Contents

1. [Timeline — Fan-Out on Write vs Read (The Core Decision)](#1-timeline-fanout)
2. [Snowflake — Distributed ID Generation](#2-snowflake-ids)
3. [Manhattan — Distributed Key-Value Store](#3-manhattan)
4. [Finagle — RPC Framework](#4-finagle)
5. [Earlybird — Real-Time Search](#5-earlybird-search)
6. [Cache Architecture — Twemcache + Twemproxy](#6-cache-architecture)
7. [Monolith to Microservices — "The Whale"](#7-monolith-migration)
8. [Trending Topics — Real-Time Trend Detection](#8-trending-topics)
9. [Media Storage — Blobstore](#9-media-storage)
10. [GraphQL Federation — Unified API](#10-graphql-federation)
11. [Kafka at Twitter — Unified Event Bus](#11-kafka-events)
12. [Ads Serving — Real-Time Bidding](#12-ads-serving)

---

## 1. Timeline — Fan-Out on Write (The Core Decision)

**Source**: "Timelines at Scale" (QCon/Strange Loop) — one of the most cited system design talks ever

### The Problem
When a user opens Twitter, they need to see tweets from everyone they follow, sorted by time (or relevance). Twitter has 500M+ users, some following thousands of accounts. Generating a timeline on every read (pull model) is too slow. Pre-computing it on every write (push model) is expensive for celebrities with millions of followers.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Fan-out model** | Hybrid: fan-out on write for normal users + fan-out on read for celebrities | Pure push or pure pull | Push gives instant reads; pull avoids the celebrity problem (Lady Gaga: 80M followers = 80M writes per tweet) |
| **Timeline storage** | Redis sorted sets (timeuuid as score, tweet_id as member) | MySQL, Cassandra | Redis: sub-millisecond reads, sorted sets for chronological ordering, fits in memory |
| **Celebrity threshold** | ~1M followers → skip fan-out, merge at read time | Fixed threshold for all | 99% of users have < 1M followers → push works. Top 1% merged at read time |
| **Timeline size** | Keep last 800 tweet IDs per user in Redis | Unlimited | 800 covers ~2-3 days of content; older tweets fetched from storage on demand |
| **Tweet hydration** | Timeline stores tweet_IDs only → hydrate (fetch full tweet data) at read time | Store full tweet in timeline | IDs are small (8 bytes vs ~1KB for full tweet); hydration from cache is fast |

### The Architecture
```
User A tweets "Hello World":
  1. Write tweet to Manhattan (persistent storage)
  2. For each follower of User A:
     IF follower count < 1M (normal user):
       ZADD timeline:{follower_id} {timestamp} {tweet_id}  (Redis)
     ELSE (celebrity):
       Skip fan-out (too expensive for millions of followers)
  3. Return success to User A

User B opens their timeline:
  1. ZREVRANGE timeline:{user_b} 0 49  (get 50 most recent tweet IDs from Redis)
  2. Fetch celebrity tweets: query Manhattan for tweets from celebrities User B follows
  3. Merge normal timeline + celebrity tweets, sort by timestamp
  4. Hydrate: batch-fetch full tweet data from cache/Manhattan
  5. Return timeline (< 200ms total)
```

### Numbers
- **Fan-out**: ~6K tweets/sec × average 200 followers = ~1.2M Redis writes/sec for fan-out
- **Timeline reads**: ~600K timeline reads/sec from Redis
- **Redis**: ~40TB of timeline data across thousands of Redis instances
- **Hydration**: Batch-fetch ~50 tweet objects from Twemcache in < 10ms

### Tradeoff
- ✅ Timeline read is a single Redis ZREVRANGE (< 5ms) for 99% of users
- ✅ Fan-out is async (doesn't block tweet posting)
- ✅ Celebrity tweets merged at read time avoids 80M write storm
- ❌ Two code paths (push for normal, pull for celebrity) — more complex
- ❌ 40TB of Redis for timelines is expensive
- ❌ Fan-out delay: follower sees tweet ~5 seconds after posting (async)

### Interview Use
> "This is the #1 system design pattern from Twitter: hybrid fan-out. Normal users (< 1M followers) → fan-out on write to Redis sorted sets. Celebrities (> 1M followers) → skip fan-out, merge at read time. Timeline stores only tweet_IDs (8 bytes each), hydrated at read time. This handles 500M+ users with < 200ms timeline loads."

---

## 2. Snowflake — Distributed ID Generation

**Source**: "Snowflake: Twitter's Distributed ID Generator" (open-sourced)

### The Problem
Twitter needs unique IDs for every tweet, DM, user — billions of IDs per day. Auto-increment doesn't work across sharded databases. UUIDs are 128 bits (wasteful) and not sortable.

### What They Built
**Snowflake** — a 64-bit ID = `{1 bit unused}{41 bits timestamp}{10 bits machine_id}{12 bits sequence}`

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Size** | 64 bits | 128 bits (UUID) | Fits in a long integer; half the size of UUID; better B-tree locality |
| **Sortable** | Time-sortable (timestamp prefix) | Random (UUID v4) | Can sort by ID instead of maintaining a separate timestamp index |
| **Coordination** | Machine ID assigned via ZooKeeper (one-time) | No coordination (UUID) | Machine ID assignment is one-time; after that, no coordination needed |
| **Throughput** | 4096 IDs/millisecond/machine | Centralized counter | Each machine generates independently — no bottleneck |
| **Epoch** | Custom epoch (2010-11-04) | Unix epoch | Custom epoch extends usable range of 41-bit timestamp |

### Why Snowflake Became the Industry Standard
- **K-sortable**: IDs are roughly chronological — important for timeline ordering
- **Compact**: 64 bits vs 128 bits (UUID) — half the storage, better index performance
- **Independent**: Each machine generates IDs without coordination (after initial machine_id assignment)
- **High throughput**: 4096 IDs per millisecond per machine

### Tradeoff
- ✅ 64-bit, time-sortable, no runtime coordination
- ✅ 4096 IDs/ms/machine — more than enough for any single service
- ❌ Machine ID assignment requires coordination (ZooKeeper)
- ❌ Clock skew can cause non-monotonic IDs across machines
- ❌ 41-bit timestamp = ~69 years from custom epoch

### Interview Use
> "Twitter's Snowflake is the gold standard for distributed ID generation: 64-bit = {41 bits timestamp}{10 bits machine_id}{12 bits sequence}. Time-sortable (can order by ID instead of timestamp), compact (half of UUID), and each machine generates 4K IDs/ms independently. I'd use this pattern for any system needing sortable, distributed IDs — tweets, messages, events."

---

## 3. Manhattan — Distributed Key-Value Store

**Source**: "Manhattan: Twitter's Real-Time, Multi-Tenant Distributed Database"

### Problem
Twitter needed a low-latency, high-throughput key-value store for tweets, user data, timelines (persistent), and various internal data. Cassandra didn't meet their operational requirements. MySQL couldn't scale horizontally enough.

### What They Built
**Manhattan** — a distributed, multi-tenant, real-time key-value store designed for Twitter's specific access patterns.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Data model** | Key-value with secondary indexes | Document (MongoDB), wide-column (Cassandra) | Simple key-value covers most Twitter access patterns; secondary indexes for lookups |
| **Consistency** | Per-key eventual consistency with read-repair | Strong consistency | Twitter's data (tweets, likes) tolerates brief staleness; strong consistency too expensive at scale |
| **Multi-tenancy** | Multiple logical databases on shared infrastructure | Separate cluster per use case | Reduces operational overhead; shared resources improve utilization |
| **Storage** | LSM-tree (RocksDB-based) | B-tree (MySQL/InnoDB) | LSM excels at write-heavy workloads; Twitter is extremely write-heavy |
| **Replication** | Multi-datacenter replication (async) | Single datacenter | Twitter serves globally; each datacenter has a local copy for low-latency reads |

### Tradeoff
- ✅ Handles billions of operations per day
- ✅ Multi-tenant reduces operational cost
- ✅ Multi-datacenter replication for global low-latency
- ❌ Custom system: expensive to maintain (vs managed DynamoDB, Cassandra)
- ❌ Eventual consistency means brief staleness
- ❌ Key-value model limits query flexibility

### Interview Use
> "Twitter's Manhattan is a distributed key-value store with multi-datacenter replication. LSM-tree storage for write-heavy workloads (billions of tweets/day). Multi-tenant: multiple logical databases share the same cluster. The lesson: for a write-heavy system with simple access patterns, key-value with LSM trees outperforms relational databases."

---

## 4. Finagle — RPC Framework

**Source**: "Finagle: Twitter's RPC System" (open-sourced)

### Problem
Twitter has thousands of services. Need a robust RPC framework that handles: load balancing, circuit breaking, retries, timeouts, tracing, and protocol support (Thrift, HTTP, Memcache).

### What They Built
**Finagle** — an async, composable RPC framework in Scala, built on Netty. Every service-to-service call at Twitter goes through Finagle.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Scala (JVM) | Go, C++ | Twitter's backend was Scala/Java; Finagle integrates natively |
| **I/O model** | Async non-blocking (Netty-based) | Synchronous (thread-per-request) | Handles thousands of concurrent connections without thread exhaustion |
| **Load balancing** | Client-side (power-of-two-choices) | Server-side (ELB) | Client-side: no extra hop; power-of-two-choices is simple and effective |
| **Circuit breaker** | Built-in (fail-fast when backend is unhealthy) | No protection | Prevents cascading failures; mandatory for Twitter's deep service graph |
| **Tracing** | Built-in distributed tracing (Zipkin) | Separate tracing system | Tracing integrated at the RPC layer — every call is traced automatically |
| **Retries** | Configurable retry budgets (max % of requests retried) | No limit on retries | Retry budget prevents retry storms from amplifying load on failing services |

### Open-Source Impact
- **Zipkin** (distributed tracing): Created by Twitter for Finagle, became industry standard
- **Power-of-two-choices LB**: Simple algorithm — pick 2 random backends, send to the one with fewer connections
- **Retry budgets**: Limit retries to % of total requests — prevents retry amplification

### Tradeoff
- ✅ Every service-to-service call gets load balancing, circuit breaking, tracing for free
- ✅ Async: handles thousands of concurrent connections efficiently
- ✅ Zipkin tracing integrated by default — debug any request through 100+ services
- ❌ Scala/JVM-specific (not language-agnostic like gRPC/Envoy)
- ❌ Learning curve for async programming model
- ❌ Industry has moved toward service meshes (Istio/Envoy) for cross-cutting concerns

### Interview Use
> "Twitter's Finagle embeds load balancing, circuit breaking, retries with budgets, and distributed tracing (Zipkin) into every RPC call. The power-of-two-choices load balancing algorithm is elegant: pick 2 random servers, send to the one with fewer connections. Retry budgets prevent retry storms — a key insight: unlimited retries amplify failures rather than recover from them. Modern equivalent: Envoy/Istio sidecar."

---

## 5. Earlybird — Real-Time Search

**Source**: "Earlybird: Real-Time Search at Twitter"

### Problem
Twitter search must find tweets within seconds of posting. Traditional search engines (Lucene/Elasticsearch) batch-index periodically — too slow for a real-time platform. A trending topic needs to be searchable immediately.

### What They Built
**Earlybird** — a real-time, in-memory, inverted index for tweets. Tweets are searchable within ~1 second of posting.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Indexing** | Real-time (index on write, not batch) | Batch index every N minutes | Tweets must be searchable immediately — "what's happening now" is Twitter's value proposition |
| **Storage** | In-memory inverted index (most recent tweets) + on-disk archive | All on disk (Elasticsearch) | In-memory: < 10ms query latency for recent tweets; disk for archive |
| **Partitioning** | Time-partitioned (recent tweets in hot index, older in cold) | Hash-partitioned | Most searches are for recent tweets; time-partition keeps hot data in memory |
| **Ranking** | Recency (default) + engagement signals (for "Top" tab) | Pure text relevance (BM25) | On Twitter, the most recent tweet about a topic is usually the most relevant |
| **Index structure** | Custom in-memory inverted index (not Lucene) | Lucene/Elasticsearch | Custom: optimized for Twitter's write-heavy, recency-biased access pattern; Lucene's batch model too slow |

### Tradeoff
- ✅ Tweets searchable within ~1 second of posting
- ✅ In-memory index: < 10ms query latency for recent tweets
- ✅ Time-partitioned: hot (recent) in memory, cold (old) on disk
- ❌ Custom index engine is expensive to maintain (vs Elasticsearch)
- ❌ In-memory limits index size (can't keep all tweets in memory forever)
- ❌ Real-time indexing adds write overhead to every tweet

### Interview Use
> "For real-time search (results within seconds of creation), I'd use an in-memory inverted index for recent data + on-disk for archive — like Twitter's Earlybird. Time-partition the index: hot (last 7 days) in memory for < 10ms queries, cold (older) on disk. Tweets are indexed on write, not batched. This is fundamentally different from Elasticsearch's batch indexing model — Twitter's value is 'what's happening NOW.'"

---

## 6. Cache Architecture — Twemcache + Twemproxy

**Source**: "Caching at Twitter" + open-source Twemcache/Twemproxy

### Problem
Twitter's cache layer handles billions of requests/sec: user profiles, tweet data, timeline metadata, counters. Need Memcached-compatible caching with better performance and operational characteristics.

### What They Built
- **Twemcache**: Twitter's custom Memcached fork (slab allocation improvements, better memory efficiency)
- **Twemproxy**: A proxy that sits between applications and cache clusters, handling sharding and connection pooling

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Cache** | Memcached (Twemcache fork) | Redis | Memcached: simpler, more memory-efficient for pure key-value caching; Twitter's caching needs are mostly simple GET/SET |
| **Sharding** | Twemproxy (client-side proxy) | Application-level hashing, Redis Cluster | Twemproxy: transparent to application; handles consistent hashing, connection pooling, failover |
| **Topology** | Multiple cache clusters per use case (tweets, users, counters) | Single shared cache | Isolation: a spike in tweet cache doesn't affect user profile cache |
| **Eviction** | LRU with slab rebalancing | Fixed slab classes | Slab rebalancing avoids the "slab calcification" problem where some slabs waste memory |
| **Connection pooling** | Twemproxy pools connections to cache servers | Direct connection per app instance | Without pooling: 1000 app instances × 100 cache servers = 100K connections; with Twemproxy: 1000 + 100 |

### Tradeoff
- ✅ Twemproxy reduces connection count by 100× (critical at Twitter's scale)
- ✅ Consistent hashing via proxy: application doesn't manage shard mapping
- ✅ Cache isolation per use case prevents cascading issues
- ❌ Twemproxy is an additional layer (adds ~1ms latency)
- ❌ Custom Memcached fork requires ongoing maintenance
- ❌ Industry has moved to Redis for richer data structure support

### Interview Use
> "At Twitter's scale, the cache layer handles billions of requests/sec. Twemproxy sits between app servers and Memcached clusters, handling consistent hashing and connection pooling — reducing connections from 100K to 1100 (1000 app instances + 100 cache servers). Separate cache clusters per use case (tweets, users, counters) prevent cache contention. Connection pooling is essential at scale — without it, connection count grows quadratically."

---

## 7. Monolith to Microservices — "The Whale"

**Source**: "From Monolith to Microservices at Twitter" + various talks

### Problem
Twitter's original Ruby on Rails monolith was famous for the "Fail Whale" — the error page shown when Twitter was overloaded. The monolith couldn't handle traffic spikes and was a single point of failure for everything.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Scala (JVM) for new services | Stay on Ruby, use Go/Java | Scala + JVM: type safety, performance, functional programming; Twitter's engineering team was Scala-heavy |
| **Migration** | Extract one service at a time (timeline, search, users) | Big-bang rewrite | Strangler fig: continuous feature delivery; most critical services extracted first |
| **Framework** | Finagle (custom RPC) | Standard HTTP/REST | Finagle provides circuit breaking, tracing, LB built-in — essential for a deep service graph |
| **Storage** | Per-service databases (Manhattan, MySQL, Redis) | Shared database | Data ownership per service eliminates coupling |
| **First extractions** | Timeline service, Search (Earlybird), User service | Random | Extract the biggest bottlenecks first — timeline and search were killing the monolith |

### Impact
- **Before (2010)**: Fail Whale during any traffic spike (World Cup, elections, celebrity tweets)
- **After (2013+)**: Twitter survived massive events (Super Bowl, elections) without downtime
- **Services**: From 1 monolith to hundreds of services

### Interview Use
> "Twitter's 'Fail Whale' was caused by their Ruby monolith's inability to handle traffic spikes. They migrated to Scala microservices using the strangler fig pattern — extracting timeline and search first (biggest bottlenecks). Each service uses Finagle for RPC with built-in circuit breaking. The migration eliminated the Fail Whale and allowed Twitter to survive Super Bowl traffic spikes."

---

## 8. Trending Topics — Real-Time Trend Detection

**Source**: "Trending at Twitter" + various talks

### Problem
Detect what's trending NOW — not what's always popular. "Weather" is always discussed; trending means an unusual spike. Must detect trends within minutes of emergence.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Algorithm** | Volume spike relative to historical baseline | Simple most-mentioned | "Weather" is always mentioned but rarely trending; trending = unusual spike |
| **Window** | Sliding windows (1 hour, 6 hours, 24 hours) | Fixed time buckets | Sliding windows capture trends that start mid-bucket |
| **Signals** | Hashtags + phrases + named entities | Hashtags only | Many trends don't have hashtags; NLP extracts trending phrases |
| **Anti-gaming** | Diversity factor (many unique users, not one user spamming) | No diversity check | Bots/spam create fake trends; diversity factor requires many unique users |
| **Personalization** | Global trending + personalized (by location, interests) | Global only | "Trending in India" is more relevant to Indian users than global trends |
| **Update frequency** | Every 5 minutes | Every hour | Trends emerge and die quickly; 5-minute updates are essential for real-time |

### Trend Score Formula
```
trend_score = (current_volume / historical_average) × diversity_factor × time_decay

Where:
  current_volume: mentions in last 1 hour
  historical_average: average mentions per hour over last 7 days
  diversity_factor: min(unique_users / 100, 2.0) — caps bot influence
  time_decay: favor recent velocity over sustained volume
```

### Interview Use
> "For trending topics, I'd compare current volume to historical baseline — not just raw count. 'Weather' gets 10K mentions normally; trending means it got 100K (10× baseline). Diversity factor requires many unique users (prevents bot manipulation). Update every 5 minutes. Twitter's formula: trend_score = (current / historical) × diversity × time_decay. This is the standard approach for any 'trending' feature."

---

## 9. Media Storage — Blobstore

**Source**: Twitter engineering blog on media infrastructure

### Problem
Twitter handles billions of media uploads (images, videos, GIFs) per day. Each media item needs multiple sizes (thumbnail, small, medium, large, original). Must serve globally with low latency.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Custom Blobstore (eventually moved to cloud storage) | S3 from the start | Built Blobstore when S3 wasn't performant enough for Twitter's needs (2012); later migrated |
| **Sizes** | Pre-generate multiple sizes on upload | On-the-fly resize | Pre-generated: no compute at read time; serve directly from CDN |
| **CDN** | Multi-CDN (Akamai + Fastly + internal) | Single CDN | Redundancy + geographic optimization; route to fastest CDN per region |
| **Video** | HLS adaptive streaming (multiple bitrates) | Single quality | Users on slow networks get lower quality without buffering |
| **Upload** | Async processing: upload → queue → resize/transcode | Sync processing before response | User gets immediate confirmation; media processing happens in background |

### Interview Use
> "For media at Twitter's scale, pre-generate all sizes on upload (don't resize on demand). Async processing pipeline: upload to storage → queue → resize/transcode → store variants → serve via multi-CDN. Twitter uses multiple CDN providers for redundancy and geographic optimization. Video uses HLS adaptive streaming for quality switching."

---

## 10. GraphQL Federation — Unified API

**Source**: "GraphQL at Twitter" + various talks

### Problem
Twitter's mobile clients needed data from 10+ backend services for a single screen. REST APIs required multiple round trips. Different clients (iOS, Android, web) needed different fields from the same data.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **API style** | GraphQL (unified schema) | REST per service, BFF per client | GraphQL: clients specify exact fields; one round trip; one schema for all clients |
| **Federation** | GraphQL federation (each service owns part of the schema) | Monolithic GraphQL server | Federation: services independently define their part of the schema; no bottleneck on one schema |
| **Caching** | Per-field caching (different TTLs per field) | Per-query caching | Tweet text cached longer than like count; per-field gives finer control |
| **Performance** | DataLoader pattern (batch + deduplicate backend calls) | Individual calls per field | DataLoader batches N individual user lookups into 1 batch call |

### Tradeoff
- ✅ Clients get exactly the data they need in one round trip
- ✅ Federation: services independently manage their schema
- ✅ Per-field caching: fine-grained freshness control
- ❌ GraphQL complexity (query optimization, N+1 problem)
- ❌ Harder to cache at CDN level (POST requests, dynamic queries)
- ❌ Query complexity attacks (deeply nested queries can be expensive)

### Interview Use
> "For mobile-first APIs where clients need data from 10+ services, I'd use GraphQL federation — each service owns part of the schema. Twitter adopted GraphQL so mobile clients specify exact fields needed, reducing round trips from 10+ to 1. DataLoader pattern batches backend calls (N user lookups → 1 batch call). The tradeoff: harder to cache (POST requests) but reduces bandwidth on mobile networks."

---

## 11. Kafka at Twitter — Unified Event Bus

**Source**: "Kafka at Twitter" + various talks

### Problem
Twitter produces trillions of events per day: tweet creation, likes, retweets, impressions, ad events, user actions. Multiple downstream systems need these events independently.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Event bus** | Kafka (one of the largest Kafka deployments globally) | Custom messaging (Twitter had DistributedLog before) | Kafka: industry standard, replay, multiple consumer groups, high throughput |
| **Scale** | Trillions of messages/day across multiple clusters | Single cluster | Separate clusters by use case (real-time, analytics, ads) for isolation |
| **Partition key** | user_id for user events, tweet_id for tweet events | Random | Ordering per entity; all events for one user on the same partition |
| **Consumers** | Independent groups: timeline fan-out, search indexing, analytics, ads, notifications | Shared consumers | Each system processes at its own pace |
| **Retention** | 7 days for real-time, 30 days for analytics | Shorter | Analytics needs replay; real-time needs 7 days for recovery |

### Interview Use
> "Twitter runs one of the world's largest Kafka deployments — trillions of messages/day. Separate clusters for real-time, analytics, and ads provide isolation. Every user action (tweet, like, follow) produces a Kafka event consumed by independent groups: timeline, search, analytics, ads, notifications. This decouples the write path from all downstream processing."

---

## 12. Ads Serving — Real-Time Bidding

**Source**: "Ads Serving at Twitter" + various talks

### Problem
When a user loads their timeline, Twitter must select and serve ads within the 200ms timeline response time. Must consider: user profile, engagement prediction, advertiser budget, auction dynamics, and frequency capping.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Auction** | Real-time second-price auction per ad slot | Fixed-price ads | Auction maximizes revenue; advertisers pay fair market value |
| **Ranking** | ML model: eCPM = bid × predicted_engagement_rate | Highest bid wins | eCPM balances revenue (bid) with user experience (engagement rate) |
| **Latency** | < 50ms for ad selection (within 200ms timeline budget) | Async ad loading | Ads in timeline must load with the timeline, not after |
| **Pacing** | Budget pacing: spend advertiser's daily budget evenly throughout the day | Spend as fast as possible | Even pacing ensures ads show throughout the day, not just in the morning |
| **Frequency capping** | Redis counter: max N impressions per user per ad per day | No cap | Showing the same ad 100 times is bad UX and wastes advertiser budget |

### Interview Use
> "For real-time ad serving (< 50ms budget), I'd use eCPM ranking: eCPM = bid × predicted_engagement_rate. This balances revenue with user experience — a high bid with low engagement gets ranked lower than a moderate bid with high engagement. Budget pacing ensures even spend throughout the day. Redis counters for frequency capping (max N impressions per user per ad)."

---

## 🎯 Quick Reference: Twitter's Key Decisions

### Core Architecture
| System | Choice | Why |
|--------|--------|-----|
| Timeline | Hybrid fan-out: push for normal, pull for celebrities | < 200ms reads; handles celebrity problem |
| IDs | Snowflake (64-bit: timestamp + machine + sequence) | Time-sortable, compact, no coordination |
| Storage | Manhattan (distributed KV, LSM) | Write-heavy, multi-datacenter |
| RPC | Finagle (Scala, async, Netty) | Built-in LB, circuit breaking, Zipkin tracing |
| Search | Earlybird (real-time in-memory inverted index) | Tweets searchable in ~1 second |
| Cache | Twemcache + Twemproxy | Memcached + connection pooling + consistent hashing |

### Events & Streaming
| System | Choice | Why |
|--------|--------|-----|
| Event bus | Kafka (trillions/day, multiple clusters) | Multiple consumers, replay, isolation |
| Trending | Volume spike / historical baseline × diversity | Detects emerging trends, not always-popular topics |
| Ads | Real-time auction, eCPM ranking, < 50ms | Balance revenue with user experience |

### Cultural / Historical
| Decision | Impact |
|----------|--------|
| Ruby → Scala migration | Eliminated the "Fail Whale"; survived Super Bowl |
| Open-sourcing Snowflake | Became industry standard for distributed IDs |
| Open-sourcing Zipkin | Became industry standard for distributed tracing |
| GraphQL federation | Reduced mobile round trips from 10+ to 1 |

---

## 🗣️ How to Use Twitter Examples in Interviews

### Example Sentences
- "Twitter's hybrid fan-out is the #1 system design pattern: push for normal users (Redis sorted sets), pull for celebrities (merge at read time). Timeline stores only tweet_IDs, hydrated at read time."
- "Twitter's Snowflake is the gold standard for distributed IDs: 64-bit, time-sortable, 4096 IDs/ms/machine, no runtime coordination."
- "Finagle's retry budget prevents retry storms — limit retries to a percentage of total requests. Unlimited retries amplify failures rather than recover from them."
- "Earlybird indexes tweets in ~1 second — real-time search requires an in-memory inverted index, not batch indexing like Elasticsearch."
- "Twemproxy reduces cache connections from 100K to 1100 — connection pooling is essential when you have 1000 app instances talking to 100 cache servers."
- "For trending detection, compare current volume to historical baseline — not raw counts. Diversity factor prevents bot manipulation."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 12 design decisions across timeline, infrastructure, search, and ads  
**Status**: Complete & Interview-Ready ✅