# Instagram Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Instagram's engineering blog and Meta's infrastructure talks. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Instagram Engineering Blog, Meta Engineering Blog, conference talks (QCon, Strange Loop, @Scale)

---

## Table of Contents

1. [Scaling Django — Staying on Python at Billions of Requests](#1-scaling-django)
2. [Cassandra for Direct Messages](#2-cassandra-for-dms)
3. [Feed Ranking — From Chronological to ML-Ranked](#3-feed-ranking)
4. [Explore/Discover — Embedding-Based Recommendations](#4-explore-recommendations)
5. [TAO — Social Graph Store (Facebook/Meta)](#5-tao-social-graph)
6. [Memcache at Scale — The Caching Layer](#6-memcache-at-scale)
7. [Snowflake-Style ID Generation](#7-id-generation)
8. [Celery + RabbitMQ → Async Task Processing](#8-async-task-processing)
9. [Image Storage — From NFS to Haystack to F4](#9-image-storage)
10. [Reels Ranking & Video Processing Pipeline](#10-reels-video-pipeline)
11. [Sharding PostgreSQL](#11-sharding-postgresql)
12. [Real-Time Notifications & Push](#12-real-time-notifications)
13. [Stories — Ephemeral Content Architecture](#13-stories-architecture)
14. [Anti-Spam & Integrity — Fighting Bad Content](#14-anti-spam-integrity)
15. [Search — From Elasticsearch to Custom](#15-search)
16. [CDN & Image Delivery Optimization](#16-cdn-image-delivery)
17. [Monitoring & Observability at Instagram Scale](#17-monitoring-observability)
18. [Scaling to 2 Billion Users — Key Lessons](#18-scaling-lessons)

---

## 1. Scaling Django

**Blog Post**: "Dismissing Python Garbage Collection at Instagram" + "Web Service Efficiency at Instagram"

### Problem
Instagram was built on Django/Python. As they grew to hundreds of millions of users, conventional wisdom said "rewrite in Java/C++." They chose not to.

### What They Did
Kept Python/Django but optimized the runtime aggressively — disabling garbage collection, optimizing memory layout, using Cython for hot paths.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Stay on Python/Django | Rewrite in Java, Go, or C++ | Developer productivity > raw performance. Rewrite risk too high for a fast-growing product |
| **GC optimization** | Disabled Python GC for web workers (use only ref counting) | Default CPython GC | Eliminating GC pauses saved ~10% CPU across the fleet |
| **Memory sharing** | Copy-on-write friendly memory layout (pre-fork model) | Thread-based concurrency | Shared memory between forked worker processes reduces total memory by ~60% |
| **Hot paths** | Cython compilation for critical code paths | Pure Python | 10-100× speedup for serialization, URL routing |
| **Async** | uWSGI + async I/O for external calls | Synchronous blocking calls | Don't block on DB/cache calls; handle more requests per worker |

### Tradeoff
- ✅ Developer velocity — Python is fast to write, easy to hire
- ✅ No risky multi-year rewrite (Instagram shipped features while competitors rewrote)
- ✅ GC optimization alone saved hundreds of servers
- ❌ Python is inherently slower than Java/Go/C++ for CPU-bound work
- ❌ GIL limits true multi-threading (mitigated by multi-process model)
- ❌ Requires deep CPython expertise for optimizations

### Interview Use
> "Instagram stayed on Python/Django and optimized the runtime — disabling GC saved 10% CPU fleet-wide. The lesson is that language choice matters less than architecture. You can scale Python to billions of requests with the right optimizations. Rewriting is risky and expensive — optimize first."

---

## 2. Cassandra for Direct Messages

**Blog Post**: "How Instagram Stores Millions of Direct Messages"

### Problem
Instagram DMs need: real-time delivery, message history per conversation, high write throughput (billions of messages/day), and strong ordering within a conversation.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Database** | Cassandra | MySQL, PostgreSQL, DynamoDB | Write-heavy (billions/day), time-series access pattern, linear horizontal scaling |
| **Partition key** | conversation_id | user_id | All messages in one conversation on one partition → ordered, efficient range queries |
| **Clustering key** | message_id (timeuuid DESC) | Timestamp | Timeuuid gives time-ordering + uniqueness. DESC for "newest first" queries |
| **Consistency** | QUORUM reads/writes | ONE / ALL | Balance between availability and consistency — no lost messages |
| **Denormalization** | Separate inbox table per user (user_id → conversations sorted by last_message_at) | JOINs at query time | Cassandra doesn't support JOINs — pre-compute the conversation list per user |

### Tradeoff
- ✅ Handles billions of messages/day with linear scaling
- ✅ All messages for a conversation on one partition — efficient reads
- ✅ Timeuuid clustering gives natural chronological ordering
- ❌ No ACID transactions (can't atomically update sender + receiver state)
- ❌ Denormalization means data is stored multiple times (inbox table + messages table)
- ❌ Schema is query-driven — adding new query patterns requires new tables

### Interview Use
> "For a messaging system, I'd use Cassandra partitioned by conversation_id with timeuuid clustering — gives us ordered messages per conversation with O(1) partition lookup. I'd also maintain a denormalized inbox table per user (partition by user_id, sorted by last_message_at) for the conversation list. This is similar to how Instagram stores DMs."

---

## 3. Feed Ranking

**Blog Post**: "Powered by AI: Instagram's Explore Recommender System" + various @Scale talks

### Problem
Instagram's chronological feed missed important posts (your best friend posted 3 hours ago, buried under 50 newer posts from accounts you don't care about). Users missed ~70% of posts from people they follow.

### What They Did
Replaced chronological feed with ML-ranked feed.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Ranking model** | ML model (predicts engagement probability) | Chronological (reverse time) | Users missed 70% of content; ML surfaces what matters |
| **Features** | User affinity, post engagement signals, content type, recency, author info | Recency only | Multi-signal ranking is dramatically more engaging |
| **Training** | Offline (daily batch on Spark) + online updates | Offline only | Online updates capture trending content faster |
| **Serving** | Pre-ranked candidate generation → final ranking (two-stage) | Single-stage ranking | Two-stage is much cheaper: filter 500 candidates → rank top 50 |
| **Candidate generation** | From followed accounts + injected recommendations | Only followed accounts | Recommendations increase time-on-app and content discovery |

### Tradeoff
- ✅ Users see content that matters to them (engagement increased significantly)
- ✅ Smaller creators get surfaced to their true audience
- ❌ "Filter bubble" — users see more of what they already engage with
- ❌ Reduced transparency — users don't understand why they see what they see
- ❌ Chronological option requested by many users (Instagram eventually added it back as an option)

### Interview Use
> "For a social feed, I'd use a two-stage ranking pipeline: first, candidate generation (pull posts from followed accounts + recommended content), then ML ranking (predict engagement probability using features like user affinity, post engagement, recency). This is more engaging than chronological but adds complexity and 'filter bubble' concerns. I'd offer a chronological option as well."

---

## 4. Explore & Recommendations

**Blog Post**: "Powered by AI: Instagram's Explore Recommender System"

### Problem
The Explore tab shows personalized content from accounts the user doesn't follow. Need to recommend from billions of posts to millions of users in real-time.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Approach** | Two-tower embedding model (user embedding + post embedding) | Collaborative filtering | Embeddings scale better to billions of items; handle cold-start |
| **Candidate generation** | Approximate Nearest Neighbor (ANN) search in embedding space | Exact search | ANN with FAISS handles billions of candidates in milliseconds |
| **Architecture** | Funnel: 500B eligible → 10K candidates (ANN) → 500 ranked (lightweight model) → 25 displayed (heavy model) | Rank everything | Funnel reduces computation by 20,000× |
| **Real-time signals** | Trending posts, viral content, breaking events | Pre-computed only | Real-time signals keep recommendations fresh |
| **Diversity** | Inject diversity rules (don't show 5 food posts in a row) | Pure relevance ranking | Diversity improves user satisfaction even if individual posts are less "relevant" |

### Tradeoff
- ✅ Personalized discovery of new content → increases time-on-app
- ✅ Funnel architecture makes real-time ranking feasible at scale
- ❌ Filter bubble risk — users see echo chambers
- ❌ Cold-start problem for new users (mitigated by popularity + demographics)
- ❌ Embedding models are expensive to train and serve

### Interview Use
> "For a recommendation system at scale, I'd use a funnel architecture: ANN search to find 10K candidates from billions of items, a lightweight model to filter to 500, then a heavy model to rank the final 25. This reduces computation by 20,000× while maintaining quality. Instagram uses two-tower embeddings for their Explore tab — user embedding dot-product with post embedding gives a relevance score."

---

## 5. TAO — Social Graph Store

**Blog Post**: "TAO: Facebook's Distributed Data Store for the Social Graph" (Meta/Facebook, used by Instagram)

### Problem
Social graph queries ("who are Alice's friends?", "did Bob like this post?", "who commented on this photo?") are extremely frequent and latency-sensitive. Traditional databases can't handle the access pattern efficiently.

### What They Built
**TAO** — a distributed, read-optimized cache over MySQL for graph-structured data. Objects (users, posts) and associations (follows, likes) with consistent caching.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Data model** | Objects (nodes) + Associations (edges) | Relational tables | Graph model maps naturally to social data; simplifies queries |
| **Caching** | Distributed write-through cache over MySQL | Cache-aside (application-managed) | Consistent caching — TAO handles invalidation; app doesn't think about cache |
| **Consistency** | Write-through with async replication across regions | Strong consistency everywhere | Cross-region strong consistency too slow; eventual for reads is OK |
| **Read:write ratio** | 500:1 → heavily optimized for reads | Balanced read/write | Social graphs are overwhelmingly read — write once, read millions of times |
| **Source of truth** | MySQL (sharded) | Custom storage engine | MySQL is proven, well-understood; TAO is the caching + API layer |

### Tradeoff
- ✅ Sub-millisecond reads for social graph queries (cache hit rate ~96%)
- ✅ Consistent cache — developers don't manage cache invalidation
- ✅ MySQL as source of truth — proven, reliable
- ❌ Cache layer adds complexity (another system to operate)
- ❌ Cross-region reads may be stale (async replication)
- ❌ Not suitable for ad-hoc queries (optimized for known access patterns)

### Interview Use
> "For the social graph (followers, likes, comments), I'd use a graph-oriented caching layer over MySQL — similar to Meta's TAO. Objects (users, posts) and associations (follows, likes) are cached with write-through consistency. With a 500:1 read:write ratio and 96% cache hit rate, this handles millions of graph queries per second. MySQL remains the source of truth."

---

## 6. Memcache at Scale

**Blog Post**: "Scaling Memcache at Facebook" (used by Instagram post-acquisition)

### Problem
At Meta's scale, memcache is not just a cache — it's the primary read path. Billions of GET operations per second. A single cache miss cascades into a thundering herd on the database.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Consistency** | Lease-based (prevents thundering herd + stale sets) | Simple TTL | When a cache miss occurs, the server gets a "lease" (token). Only the lease holder can populate the cache. Others wait. Prevents thundering herd. |
| **Invalidation** | Database triggers → invalidation daemon → memcache delete | TTL only | Sub-second invalidation after DB write (vs waiting for TTL) |
| **Regional replication** | Async replication from primary region | Sync replication | Cross-region consistency not worth the latency cost |
| **Failure handling** | "Gutter" pool (small pool of backup servers for failed hosts) | No redundancy | When a memcache server dies, route to gutter pool instead of hitting DB directly |
| **Hot keys** | Client-side caching for extremely hot keys | Only server-side cache | Celebrity profiles, viral posts — cache locally in the web server |

### Tradeoff
- ✅ Lease-based approach eliminates thundering herd (millions of concurrent requests for same key)
- ✅ Invalidation daemon provides sub-second freshness
- ✅ Gutter pool prevents cascading failures on server death
- ❌ Complex system (leases, invalidation daemon, gutter pool)
- ❌ Cross-region reads may be stale
- ❌ Very large operational footprint (thousands of memcache servers)

### Interview Use
> "At scale, the biggest caching challenge is the thundering herd — when a hot key expires, thousands of requests simultaneously hit the database. Meta's solution is lease-based caching: on a cache miss, one server gets a 'lease' to populate the cache, others wait for the result. This prevents N concurrent DB queries for the same key. Combined with a DB-triggered invalidation daemon, cache freshness is sub-second."

---

## 7. Snowflake-Style ID Generation

**Blog Post**: "Sharding & IDs at Instagram"

### Problem
Instagram needed globally unique, time-sortable IDs for posts, comments, and likes. Auto-increment doesn't work across sharded databases. UUIDs aren't sortable and are 128 bits (wasteful).

### What They Built
A **Snowflake-inspired ID** generated in PostgreSQL using a PL/pgSQL function. 64-bit ID = timestamp (41 bits) + shard_id (13 bits) + auto-increment (10 bits).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **ID structure** | 64-bit: 41 bits timestamp + 13 bits shard + 10 bits sequence | UUID, auto-increment, separate ID service | 64-bit is compact; timestamp prefix gives sortability; shard ID embeds the location |
| **Generation** | Inside PostgreSQL (PL/pgSQL function) | Separate ID generation service (Twitter's Snowflake) | No additional service to operate; ID generated atomically with the INSERT |
| **Epoch** | Custom epoch (Jan 1, 2011) | Unix epoch | Custom epoch extends the usable range of 41 bits |
| **Shard encoding** | Shard ID embedded in the ID itself | Separate shard mapping | Can determine which shard holds a record just by looking at the ID |

### Tradeoff
- ✅ No external ID service to maintain (generated in DB)
- ✅ Shard ID embedded — can route reads without a lookup
- ✅ Time-sortable — chronological queries by ID order
- ❌ 10 bits sequence = 1024 IDs per shard per millisecond (enough for Instagram but not for extreme throughput)
- ❌ Coupled to PostgreSQL (ID generation tied to DB)
- ❌ Clock skew between DB servers could cause non-monotonic IDs across shards

### Interview Use
> "Instagram generates IDs in PostgreSQL itself — a 64-bit Snowflake-style ID with timestamp (41 bits) + shard_id (13 bits) + sequence (10 bits). The shard ID is embedded in the ID, so given any post_id, you immediately know which database shard holds it. No separate ID service needed — it's generated atomically with the INSERT."

---

## 8. Async Task Processing

**Blog Post**: Various Instagram engineering talks on their async infrastructure

### Problem
Many operations after a user action don't need to happen synchronously: send push notification, update counters, sync to search index, trigger recommendation updates, process uploaded media.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Task queue** | Celery + RabbitMQ → later Kafka for some workloads | SQS, custom queue | Celery is Python-native (Instagram is Django); RabbitMQ for simple task semantics |
| **Prioritization** | Priority queues (image processing > analytics) | Single FIFO queue | User-facing tasks (notifications) must not be delayed by background analytics |
| **Retry** | Exponential backoff with max retries → DLQ | No retry | Transient failures (network, service restart) are common; retry handles most |
| **Idempotency** | Tasks must be idempotent (at-least-once delivery) | Exactly-once | RabbitMQ/Celery provides at-least-once; app handles duplicates |
| **Migration** | Gradually moving heavy workloads to Kafka | All on Celery | Kafka better for high-throughput event streaming (feed updates, analytics) |

### Tradeoff
- ✅ Celery + Python = same language as the rest of the app (developer velocity)
- ✅ Priority queues ensure user-facing tasks aren't blocked
- ❌ Celery/RabbitMQ doesn't scale as well as Kafka for very high throughput
- ❌ At-least-once means all tasks must handle duplicate execution

### Interview Use
> "For async processing, I'd use a task queue with priority lanes — high priority for user-facing tasks (notifications, media processing) and low priority for background work (analytics, search indexing). Instagram uses Celery/RabbitMQ for Python-native task processing, with Kafka handling the highest-throughput workloads like feed updates."

---

## 9. Image Storage — Haystack to F4

**Blog Post**: "Finding a Needle in Haystack: Facebook's Photo Storage" + "f4: Facebook's Warm BLOB Storage"

### Problem
Instagram stores billions of photos. Each photo has multiple sizes (thumbnail, small, medium, large, original). Traditional filesystems (POSIX) are too slow — too many metadata lookups per read.

### What They Built
**Haystack** (hot storage) → **F4** (warm storage). Haystack eliminates filesystem metadata overhead by packing many photos into a single large file.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Hot storage** | Haystack — multiple images packed into one large file | NFS / filesystem per image | One file per image = too many inode lookups. Haystack: one open() + seek() per read |
| **Warm storage** | F4 — Reed-Solomon erasure coding | Triple replication (RF=3) | Warm data rarely accessed. Erasure coding gives same durability at 1.4× storage vs 3× |
| **Metadata** | In-memory index: photo_id → (volume_id, offset, size) | Database lookup per image | O(1) memory lookup to find any photo's location on disk |
| **Lifecycle** | Hot (Haystack, frequent access) → Warm (F4, infrequent) → Cold (never deleted at Instagram) | Single tier | Warm storage is 50% cheaper. Most photos accessed in first 24 hours, rarely after |
| **CDN integration** | CDN → Haystack/F4 on cache miss | CDN → separate origin server | Direct integration eliminates extra hop |

### Tradeoff
- ✅ Haystack serves a photo with 1 disk read (vs 3+ for filesystem)
- ✅ F4 with erasure coding: 1.4× overhead vs 3× for triple replication (50%+ storage savings)
- ✅ In-memory index gives O(1) photo lookup
- ❌ Custom storage system — significant engineering investment
- ❌ Erasure coding slower to reconstruct on failure (acceptable for warm data)
- ❌ Can't use standard filesystem tools for debugging

### Interview Use
> "For image storage at scale, I'd use a Haystack-like approach: pack multiple images into large volume files, maintain an in-memory index mapping photo_id → (volume, offset, size). This eliminates filesystem metadata overhead — one disk seek per photo vs multiple inode lookups. For warm data (older photos), switch to erasure coding (1.4× overhead vs 3× replication) to cut storage costs 50%."

---

## 10. Reels & Video Processing

**Blog Post**: Various Meta engineering talks on video infrastructure

### Problem
Short-form video (Reels) requires: fast upload, transcoding to multiple resolutions/codecs, thumbnail extraction, content safety screening, ML-based ranking, and global delivery via CDN.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Upload** | Chunked upload + presigned URLs to object storage | Upload through API server | Don't bottleneck the API server with large files; parallel chunk upload |
| **Transcoding** | Async pipeline: multiple resolutions + codecs (H.264, H.265, AV1) | Single format | Different devices/networks need different quality levels |
| **Adaptive bitrate** | HLS/DASH with multiple quality levels | Fixed quality | Client switches quality based on network conditions |
| **Content safety** | ML models scan before publishing (blocking) | Post-publish moderation | Must catch harmful content before it reaches any user |
| **Ranking** | Engagement prediction (watch time, likes, shares, completion rate) | Chronological / follower count | Engagement-based ranking surfaces quality content |
| **CDN** | Pre-warm CDN for trending content; pull-through for long-tail | All pull-through | Trending Reels need instant playback (no buffering on first view in a region) |

### Tradeoff
- ✅ Adaptive bitrate → smooth playback on any network
- ✅ Pre-publish content safety prevents harmful content from spreading
- ❌ Pre-publish screening adds latency (seconds to minutes before video is visible)
- ❌ Transcoding is expensive (GPU/CPU intensive)
- ❌ Multiple resolutions × codecs = significant storage multiplication

### Interview Use
> "For a short-video platform, I'd use chunked upload → object storage → async transcoding pipeline. The pipeline generates multiple resolutions (360p, 720p, 1080p) and codecs (H.264, H.265), plus thumbnail extraction and content safety ML screening. Delivery via CDN with adaptive bitrate (HLS). Trending content is pre-warmed at CDN edge locations for instant playback."

---

## 11. Sharding PostgreSQL

**Blog Post**: "Sharding & IDs at Instagram"

### Problem
Instagram started with a single PostgreSQL instance. As data grew, they needed to shard — but PostgreSQL doesn't natively support horizontal sharding.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Sharding** | Application-level sharding (logical shards mapped to physical DB servers) | Vitess, Citus, CockroachDB | Full control; no dependency on external sharding proxy |
| **Shard key** | Entity ID (user_id for user data, media_id for posts) | Random | Co-locate related data; shard deterministically from the ID |
| **Logical shards** | Thousands of logical shards → mapped to physical servers | 1:1 shard:server | Logical shards can be moved between servers without resharding |
| **Schema** | Same schema on all shards | Different schemas per shard | Simplifies operations, migrations apply uniformly |
| **Cross-shard queries** | Avoided by design (denormalize if needed) | Distributed query engine | Cross-shard queries are expensive; design data model to avoid them |

### Tradeoff
- ✅ Logical shards decouple data placement from physical hardware (easy to rebalance)
- ✅ ID encodes shard → can route any request without a lookup table
- ✅ PostgreSQL is mature, well-understood, and the team has expertise
- ❌ Application must handle sharding logic (routing, migrations)
- ❌ No cross-shard JOINs or transactions
- ❌ Schema migrations must be coordinated across all shards

### Interview Use
> "I'd shard PostgreSQL at the application level with logical shards — thousands of logical shards mapped to a smaller number of physical database servers. The entity ID embeds the shard number, so routing is O(1) — no lookup table needed. Logical shards can be migrated between physical servers for load balancing without changing the application. This is how Instagram scaled PostgreSQL."

---

## 12. Real-Time Notifications & Push

### Problem
Instagram sends billions of notifications daily — likes, comments, follows, DM messages, Stories mentions, live video starts. Must be real-time for engagement.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Event-driven: service → Kafka → Notification Service → FCM/APNS | Direct push from each service | Decoupled; any service can trigger notifications without knowing the delivery mechanism |
| **Aggregation** | Aggregate similar notifications ("Alice and 5 others liked your photo") | Individual notifications | Reduces notification fatigue; improves user experience |
| **Dedup window** | Don't re-notify for same action within 5 minutes | No dedup | Prevents spam on retries |
| **Priority** | DMs > Comments/Likes > Follows > Marketing | Single queue | DMs are time-sensitive; marketing can wait |
| **Preferences** | Per-notification-type settings (can mute likes but keep DMs) | Global on/off | Users want granular control |
| **Delivery** | Push (FCM/APNS) + in-app badge + email (digest) | Push only | Multi-channel ensures users see important notifications |

### Interview Use
> "For notifications, I'd use Kafka as the event bus — any service publishes a notification event. The Notification Service consumes, aggregates (5 likes → one notification), respects user preferences, and delivers via FCM/APNS + in-app. Priority queues ensure DMs arrive instantly while marketing notifications can batch."

---

## 13. Stories Architecture

### Problem
Stories are ephemeral (24-hour TTL), viewed sequentially (tray at top of feed), and must load instantly. Different from permanent posts — different storage and delivery strategy needed.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Separate from main feed storage; TTL-based automatic deletion | Same storage as posts | Stories expire — don't pollute permanent storage; TTL handles cleanup |
| **Tray ordering** | ML-ranked (who you interact with most) | Chronological | Surfaces stories you care about; increases completion rate |
| **Prefetching** | Prefetch first 3-5 stories when tray loads | Load on tap | Instant playback when user taps — critical for engagement |
| **Media format** | Optimized for vertical full-screen (9:16) | Same processing as feed posts | Different dimensions, different encoding quality targets |
| **TTL** | 24 hours from creation, handled by storage layer | Application-level cleanup | Storage layer (Cassandra TTL or similar) handles expiration automatically |
| **Viewers list** | Stored separately; sorted by recency | Part of story entity | Viewers list can grow large; separate storage avoids bloating the story record |

### Tradeoff
- ✅ TTL-based storage auto-cleans expired stories (no garbage collection)
- ✅ Prefetching ensures instant playback
- ❌ Separate storage system from feed adds operational complexity
- ❌ Prefetching wastes bandwidth if user doesn't watch (mitigated by only prefetching top stories)

### Interview Use
> "For ephemeral content (Stories), I'd use a TTL-based storage strategy — Cassandra with 24-hour TTL handles automatic expiration. The story tray is ML-ranked by user affinity. When the tray loads, prefetch the first 3-5 stories so playback is instant when the user taps. This is Instagram's approach — different storage and delivery strategy from permanent posts."

---

## 14. Anti-Spam & Content Integrity

### Problem
Instagram fights spam (fake accounts, bot comments), harmful content (violence, nudity, hate speech), and coordinated inauthentic behavior. Must process billions of pieces of content.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Pre-publish screening** | ML classifiers run before content is visible | Post-publish only | Catch 95% of harmful content before any user sees it |
| **Architecture** | Streaming pipeline: content created → Kafka → ML classifiers → verdict | Batch daily scan | Real-time detection prevents viral spread of harmful content |
| **ML models** | Ensemble: image classifier + text classifier + behavior signals | Single model | Different content types need different models; ensemble catches more |
| **Human review** | ML flags → human reviewer confirms borderline cases | Fully automated | ML isn't 100% accurate; humans handle edge cases |
| **Account signals** | Account age, posting velocity, follower graph patterns | Content-only analysis | Spam accounts have behavioral patterns (new account, rapid posting, bot-like graph) |
| **False positive handling** | Appeal process → human re-review | No appeal | Legitimate content sometimes caught; users need a way to contest |

### Tradeoff
- ✅ Pre-publish catches harmful content before it spreads
- ✅ ML + behavioral signals catch sophisticated spam that content-only analysis misses
- ❌ Pre-publish adds latency to posting (seconds of screening delay)
- ❌ False positives frustrate legitimate users
- ❌ Adversarial: spammers evolve to evade ML models (requires constant retraining)

### Interview Use
> "For content integrity, I'd use a streaming ML pipeline: when content is created, push to Kafka → ML classifiers (image, text, behavioral) produce a verdict before the content is visible. This pre-publish approach prevents harmful content from going viral. Borderline cases go to human review with an appeal process. The tradeoff is a few seconds of posting latency."

---

## 15. Search

### Problem
Users search for accounts, hashtags, and locations on Instagram. With 2B+ users and billions of posts, search must be fast, relevant, and personalized.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Account search** | Custom inverted index (prefix + fuzzy match) | Elasticsearch | Optimized for username/display name search patterns; lower latency |
| **Hashtag search** | Pre-computed hashtag index with popularity scores | Full-text on post content | Hashtags are explicit labels — no NLP needed |
| **Ranking** | Personalized: your search history, who you follow, geographic proximity | Global popularity | "John" should show your friend John first, not the most popular John |
| **Autocomplete** | Trie-based prefix matching with popularity weighting | Full search on every keystroke | Prefix match is fast; populate results as user types |
| **Indexing** | Near real-time (seconds after account creation / post) | Batch (hourly) | New accounts should be findable immediately |

### Tradeoff
- ✅ Personalized ranking surfaces relevant results (your friend John, not celebrity John)
- ✅ Trie-based autocomplete is very fast (< 50ms)
- ❌ Custom search infrastructure is expensive to maintain vs managed Elasticsearch
- ❌ Personalization requires storing and querying user interaction history

### Interview Use
> "For search, I'd use a personalized ranking approach: the same query 'John' returns different results for different users based on who they follow and interact with. Autocomplete uses trie-based prefix matching with personalized re-ranking — fast and relevant."

---

## 16. CDN & Image Delivery

### Problem
Instagram serves billions of images daily across the globe. Images must load fast — slow images = users leave. Multiple sizes per image (150px thumbnail, 320px, 640px, 1080px).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **CDN** | Multi-CDN (Akamai, Fastly, Meta's own edge) | Single CDN | Redundancy + geographic optimization; route to fastest CDN per region |
| **Image sizes** | Generate multiple sizes on upload (not on-the-fly) | On-the-fly resizing | Pre-generated is faster to serve; on-the-fly adds per-request compute |
| **Format** | WebP for Android/Chrome, JPEG for iOS/Safari → later AVIF | JPEG everywhere | WebP is 30% smaller than JPEG at same quality; AVIF even better |
| **Progressive loading** | Low-quality placeholder → full image (blur-up) | Wait for full image | Perceived performance is better — user sees something immediately |
| **Cache key** | Content-addressable: `{hash_of_content}.jpg` | `media_id.jpg` | Content-based URLs never need cache invalidation — change content = change URL |

### Tradeoff
- ✅ Content-addressable URLs = infinite CDN TTL, never stale
- ✅ WebP/AVIF saves 30-50% bandwidth vs JPEG
- ✅ Progressive loading improves perceived performance
- ❌ Multiple sizes × multiple formats = storage multiplication (6-10× per image)
- ❌ Multi-CDN adds routing complexity
- ❌ Format negotiation requires device/browser detection

### Interview Use
> "For image delivery, I'd pre-generate multiple sizes on upload (thumbnail, medium, large) in modern formats (WebP, AVIF). Use content-addressable URLs (`image_{hash}.webp`) for infinite CDN caching — the URL changes when the content changes, so no invalidation needed. Progressive loading (blur-up) gives users instant visual feedback."

---

## 17. Monitoring & Observability

### Problem
With thousands of services, billions of requests, and deployments multiple times per day, Instagram needs to detect issues within seconds — before users notice.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Metrics** | Time-series DB (Meta's internal, similar to M3/Gorilla) | Prometheus | Prometheus doesn't scale to Meta's volume (billions of data points/sec) |
| **Distributed tracing** | Trace ID propagated through all service calls | No tracing | Pinpoint which service in a chain of 20 is causing latency |
| **Alerting** | Anomaly detection (automatic) + static thresholds | Manual thresholds only | Anomaly detection catches issues without manually setting thresholds for every metric |
| **Dashboards** | Pre-computed dashboards (not ad-hoc queries on raw data) | Query raw metrics on every dashboard load | Pre-computed dashboards load in < 1 second vs minutes for raw queries |
| **Canary deployments** | Deploy to 1% of traffic → compare metrics → full rollout | Deploy to 100% at once | Catch regressions before they affect all users |
| **Incident response** | Automated runbooks + on-call rotation | Manual firefighting | Reduce MTTR; automate common fixes (restart service, roll back deploy) |

### Interview Use
> "For observability at scale, I'd use distributed tracing (trace ID through all services), pre-aggregated metrics dashboards, anomaly-based alerting, and canary deployments (1% → 10% → 100%). Instagram deploys code multiple times daily — canaries catch regressions before they reach all users."

---

## 18. Scaling to 2 Billion Users — Key Lessons

### Lesson 1: "Don't Rewrite — Optimize"
Instagram stayed on Python/Django. Instead of rewriting in Java, they optimized the runtime (disable GC, Cython hot paths, memory sharing). Saved hundreds of servers and years of rewrite risk.

### Lesson 2: "Shard Early, Shard by Entity"
Instagram sharded PostgreSQL early with application-level sharding. Entity ID embeds the shard → O(1) routing. Logical shards decouple data from physical servers.

### Lesson 3: "Cache Aggressively, Invalidate Carefully"
TAO provides consistent caching for the social graph. Lease-based caching prevents thundering herds. DB-triggered invalidation provides sub-second freshness.

### Lesson 4: "Denormalize for Read Performance"
Cassandra for DMs uses separate inbox tables. Feed ranking pre-computes candidate lists. Search pre-computes personalized indexes. Denormalization trades storage for speed.

### Lesson 5: "Two-Stage ML Pipelines"
Both feed ranking and Explore use funnel architectures: cheap candidate generation → expensive ranking on a small set. Reduces computation 1000×+ while maintaining quality.

### Lesson 6: "Separate Hot from Cold"
Haystack for hot images, F4 with erasure coding for warm. Stories use TTL-based auto-expiration. Metrics use tiered retention (raw → hourly → daily).

### Lesson 7: "Event-Driven for Decoupling"
Kafka connects services: posting → fan-out, notifications, search indexing, analytics. Services don't know about each other. Each consumer group scales independently.

### Lesson 8: "Content Safety Before Publishing"
ML classifiers screen content before it's visible to any user. Pre-publish adds seconds of latency but prevents viral spread of harmful content.

---

## 🎯 Quick Reference: Instagram's Key Decisions

### Database
| Data | Choice | Why |
|------|--------|-----|
| User/channel metadata | PostgreSQL (sharded) | Relational, transactional, team expertise |
| Social graph | TAO (cache over MySQL) | Read-optimized, consistent cache, 96% hit rate |
| Direct messages | Cassandra | Write-heavy, time-series, horizontal scaling |
| Feed/Explore candidates | Pre-computed in cache | Two-stage ML needs fast candidate access |
| Search | Custom inverted index | Optimized for username prefix matching |

### Media
| Media Type | Storage | Delivery |
|-----------|---------|----------|
| Photos (hot) | Haystack (packed files) | CDN + content-addressable URLs |
| Photos (warm) | F4 (erasure coding, 1.4× overhead) | CDN on cache miss |
| Reels video | Object storage → transcoded to multiple formats | CDN + adaptive bitrate (HLS) |
| Stories | TTL-based storage (24h auto-delete) | Prefetched on tray load |

### ML & Ranking
| System | Approach | Key Insight |
|--------|----------|-------------|
| Feed | Two-stage: candidates → ML ranking | 70% of content was being missed with chronological |
| Explore | Funnel: 500B → 10K → 500 → 25 | Two-tower embeddings + ANN (FAISS) |
| Content safety | Pre-publish ML ensemble | Screen before visible; prevents viral spread |
| Search | Personalized ranking | Your friend John > celebrity John |

---

## 🗣️ How to Use Instagram Examples in Interviews

### Example Sentences
- "Instagram generates IDs inside PostgreSQL with embedded shard IDs — I'd take the same approach for our sharded system."
- "Like Instagram's Haystack, I'd pack small objects into large volume files to avoid filesystem metadata overhead."
- "Instagram uses lease-based caching (via Meta's memcache) to prevent thundering herds — I'd use the same pattern for our hot cache keys."
- "For the recommendation system, I'd use a funnel architecture like Instagram's Explore: ANN candidate generation → lightweight filter → heavy ranking model."
- "Instagram stayed on Python/Django and optimized instead of rewriting — the lesson is that language choice matters less than architecture."
- "For DMs, I'd use Cassandra partitioned by conversation_id like Instagram — all messages for one conversation on one partition, timeuuid for ordering."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 18 design decisions across database, media, ML, infrastructure  
**Status**: Complete & Interview-Ready ✅
