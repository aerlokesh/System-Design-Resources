# System Design Tradeoffs & Deep Dives — Universal Cheat Sheet

> **Purpose**: A reusable reference of tradeoffs and dive-deep topics you can pull from during ANY HLD interview. No code — just decisions, reasoning, and talking points.
>
> **How to Use**: When the interviewer asks "what are the tradeoffs?" or "let's dive deeper into X", find the relevant category below and articulate the **Chose / Why / Alternative / When Alternative Wins** pattern.

---

## Table of Contents

1. [Database Selection](#1-database-selection)
2. [SQL vs NoSQL](#2-sql-vs-nosql)
3. [Consistency vs Availability (CAP)](#3-consistency-vs-availability-cap)
4. [Consistency Models](#4-consistency-models)
5. [Caching Strategies](#5-caching-strategies)
6. [Cache Invalidation](#6-cache-invalidation)
7. [Push vs Pull (Fanout)](#7-push-vs-pull-fanout)
8. [Synchronous vs Asynchronous Processing](#8-synchronous-vs-asynchronous-processing)
9. [Communication Protocols](#9-communication-protocols)
10. [Message Queues & Event Streaming](#10-message-queues--event-streaming)
11. [ID Generation](#11-id-generation)
12. [Sharding / Partitioning Strategies](#12-sharding--partitioning-strategies)
13. [Replication Strategies](#13-replication-strategies)
14. [Load Balancing](#14-load-balancing)
15. [Rate Limiting](#15-rate-limiting)
16. [Search & Indexing](#16-search--indexing)
17. [Storage Tiering & Data Lifecycle](#17-storage-tiering--data-lifecycle)
18. [Blob / Media Storage](#18-blob--media-storage)
19. [CDN & Edge Caching](#19-cdn--edge-caching)
20. [Deduplication](#20-deduplication)
21. [Contention & Concurrency Control](#21-contention--concurrency-control)
22. [Exactly-Once vs At-Least-Once vs At-Most-Once](#22-exactly-once-vs-at-least-once-vs-at-most-once)
23. [Batch vs Stream Processing](#23-batch-vs-stream-processing)
24. [Monolith vs Microservices](#24-monolith-vs-microservices)
25. [API Design Tradeoffs](#25-api-design-tradeoffs)
26. [Pagination Strategies](#26-pagination-strategies)
27. [Encryption & Security](#27-encryption--security)
28. [Availability & Disaster Recovery](#28-availability--disaster-recovery)
29. [Pre-Computation vs On-Demand Computation](#29-pre-computation-vs-on-demand-computation)
30. [Backpressure & Flow Control](#30-backpressure--flow-control)
31. [Hot Partition / Hot Key Handling](#31-hot-partition--hot-key-handling)
32. [Leader Election & Coordination](#32-leader-election--coordination)
33. [Reconciliation (Streaming vs Batch)](#33-reconciliation-streaming-vs-batch)
34. [Multi-Region / Geo-Distribution](#34-multi-region--geo-distribution)
35. [Cost vs Performance](#35-cost-vs-performance)

---

## 1. Database Selection

### Relational (MySQL / PostgreSQL)
| Pros | Cons |
|------|------|
| ACID transactions | Hard to scale writes horizontally |
| Rich query support (JOINs, aggregation) | Schema migrations can be painful |
| Strong consistency | Single-node write bottleneck without sharding |
| Mature tooling & ecosystem | Sharding adds application complexity |

**Best for**: User accounts, financial data, relational data with complex queries, anything requiring strong consistency + transactions.

### Wide-Column (Cassandra / HBase)
| Pros | Cons |
|------|------|
| Linear horizontal scalability | No ACID transactions (per-row only in Cassandra) |
| Designed for write-heavy workloads | Limited query flexibility (primary key lookups) |
| No single point of failure | Eventual consistency by default |
| Multi-datacenter replication built-in | Data modeling is query-driven (denormalization required) |

**Best for**: Messages, time-series, activity feeds, IoT data, any write-heavy workload at scale.

### Document (MongoDB / DynamoDB)
| Pros | Cons |
|------|------|
| Flexible schema (JSON documents) | Poor for highly relational data |
| Good for hierarchical data | Joins are expensive / unsupported |
| Easy to start, natural mapping to objects | Consistency guarantees vary |
| DynamoDB: managed, auto-scaling | DynamoDB: hot partition issues, cost at scale |

**Best for**: Product catalogs, user profiles, CMS, any domain with varied/evolving schemas.

### Graph (Neo4j / Amazon Neptune)
| Pros | Cons |
|------|------|
| Optimized for relationship traversal | Not suited for bulk analytics |
| Multi-hop queries are fast | Harder to scale horizontally |
| Natural for social graphs, recommendations | Smaller ecosystem |

**Best for**: Social graphs, recommendation engines, fraud detection, knowledge graphs.

### Key-Value (Redis / Memcached)
| Pros | Cons |
|------|------|
| Sub-millisecond latency | Limited query capability (key-based only) |
| Simple data model | Data size limited by memory |
| Excellent for caching, sessions, counters | Persistence is secondary concern |

**Best for**: Caching, sessions, rate limiters, leaderboards, real-time counters, pub/sub.

---

## 2. SQL vs NoSQL

| Dimension | SQL | NoSQL |
|-----------|-----|-------|
| **Schema** | Fixed, predefined | Flexible, schemaless |
| **Consistency** | Strong (ACID) | Eventual (BASE) typically |
| **Scaling** | Vertical (+ complex sharding) | Horizontal (native) |
| **Queries** | Rich (JOINs, GROUP BY, subqueries) | Limited (key/range lookups) |
| **Write throughput** | Limited by single master | Distributed writes |
| **Use when** | Data is relational, consistency matters | Scale > consistency, write-heavy |

### Decision Framework
- **Need transactions across entities?** → SQL
- **Need JOINs at query time?** → SQL
- **Write-heavy (>10K writes/sec)?** → NoSQL (Cassandra, DynamoDB)
- **Schema changes frequently?** → NoSQL (MongoDB)
- **Read-heavy with simple lookups?** → NoSQL or SQL + cache
- **Financial / billing data?** → SQL (ACID guarantees)

---

## 3. Consistency vs Availability (CAP)

### CAP Theorem Summary
> In a network partition, you must choose between **Consistency** (every read gets the latest write) or **Availability** (every request gets a response, even if stale).

| Choose CP (Consistency + Partition Tolerance) | Choose AP (Availability + Partition Tolerance) |
|---|----|
| Banking, payments, inventory with limited stock | Social media feeds, likes/views counters |
| Booking systems (no double-booking) | Presence/last seen indicators |
| Leader election, distributed locks | Search results, recommendation feeds |
| User authentication state | Analytics dashboards, metrics |

### Talking Points
- "For user-facing reads like timelines, I'd choose AP — a slightly stale feed is better than an error."
- "For the booking/payment path, I'd choose CP — a brief unavailability is better than double-selling."
- "Most systems are a **mix**: CP for the write path of critical data, AP for the read path of non-critical data."

---

## 4. Consistency Models

| Model | Guarantee | Latency | Use Case |
|-------|-----------|---------|----------|
| **Strong Consistency** | Read always returns latest write | Highest (synchronous replication) | Financial transactions, booking systems |
| **Eventual Consistency** | Reads may be stale, converge eventually | Lowest | Social feeds, likes, view counts |
| **Causal Consistency** | Respects "happens-before" ordering | Medium | Chat messages, comment threads |
| **Read-Your-Writes** | User sees their own updates immediately | Medium | User profile edits, post creation |
| **Monotonic Reads** | Once you read a value, you never see an older value | Medium | Any user session that shouldn't "go backward" |

### Tradeoff
- **Stronger consistency** → higher latency, lower throughput, more complex coordination
- **Weaker consistency** → lower latency, higher throughput, simpler architecture

### Interview Pattern
> "For messages in a conversation, I'd use **causal consistency** — we need replies to appear after the messages they reply to, but we don't need global ordering across all conversations."

---

## 5. Caching Strategies

### Cache-Aside (Lazy Loading)
- **How**: App checks cache → miss → fetch from DB → write to cache → return
- **Pros**: Only caches what's actually read; cache failures don't break the system
- **Cons**: First request always misses; stale data until TTL expires
- **Best for**: General purpose, read-heavy workloads

### Write-Through
- **How**: App writes to cache AND DB simultaneously
- **Pros**: Cache is always up-to-date; no stale reads
- **Cons**: Higher write latency; caches data that may never be read
- **Best for**: Data that is read frequently right after writing

### Write-Behind (Write-Back)
- **How**: App writes to cache → cache asynchronously flushes to DB
- **Pros**: Lowest write latency; batches DB writes
- **Cons**: Risk of data loss if cache crashes before flush; complexity
- **Best for**: High write throughput (metrics, counters, analytics)

### Read-Through
- **How**: Cache itself fetches from DB on miss (cache is the access layer)
- **Pros**: Simplifies app logic; cache manages population
- **Cons**: Cold start problem; first read is slow
- **Best for**: Simplifying application code with a caching proxy

### Refresh-Ahead
- **How**: Cache proactively refreshes entries before they expire
- **Pros**: No cache miss latency for hot keys
- **Cons**: Wastes resources refreshing data that may not be needed
- **Best for**: Hot data with predictable access patterns (e.g., homepage content)

### Decision Table

| Question | Strategy |
|----------|----------|
| Read-heavy, tolerant of staleness? | Cache-Aside + TTL |
| Must always be fresh after writes? | Write-Through |
| Write-heavy, can tolerate brief staleness? | Write-Behind |
| Hot data, predictable access? | Refresh-Ahead |

---

## 6. Cache Invalidation

| Strategy | Description | Tradeoff |
|----------|-------------|----------|
| **TTL (Time-To-Live)** | Cache entries expire after fixed duration | Simple but stale until expiry |
| **Event-Driven Invalidation** | Publish event on write → consumers invalidate cache | Fresh but adds messaging complexity |
| **Version-Based** | Store version number; client checks if stale | Works for client caches; adds a check per read |
| **Write-Invalidate** | Delete cache entry on write; next read repopulates | Simple; brief miss after write |
| **Write-Update** | Update cache entry directly on write | No miss but risk of inconsistency if update fails |

### Interview Talking Point
> "I'd use TTL + event-driven invalidation together — TTL as a safety net (e.g., 5 min), with Kafka events for immediate invalidation on writes. This handles both the common case (event arrives, cache invalidated immediately) and the edge case (event lost, TTL still cleans up)."

---

## 7. Push vs Pull (Fanout)

### Fan-Out on Write (Push Model)
- **How**: When a user creates content, push it to all followers' feeds immediately
- **Pros**: Read is instant (pre-computed); great for read-heavy systems
- **Cons**: Write amplification for users with millions of followers; wasted work if followers don't read
- **Best for**: Users with < 1M followers; systems where read latency matters most

### Fan-Out on Read (Pull Model)
- **How**: When a user opens their feed, fetch and merge latest content from all followed users
- **Pros**: No write amplification; no wasted computation
- **Cons**: Read is slow (N queries to merge); high read-time compute
- **Best for**: Celebrity accounts; infrequently accessed feeds

### Hybrid Approach
- **How**: Push for normal users (< threshold followers); Pull for celebrities (> threshold)
- **Pros**: Best of both worlds; handles the "celebrity problem"
- **Cons**: Two code paths; threshold tuning needed; merge logic for celebrity tweets
- **Best for**: Twitter, Instagram, any social feed at scale

### Decision Table

| Scenario | Strategy |
|----------|----------|
| Most users have < 10K followers | Fan-out on write |
| Some users have millions of followers | Hybrid |
| Feed accessed infrequently | Fan-out on read |
| Read latency SLA < 200ms | Fan-out on write (or hybrid) |

### Talking Point
> "I'd use a hybrid approach with a threshold of ~1M followers. For 99% of users, we push to their followers' timelines on write. For celebrities, we skip the fanout and merge their tweets at read time. This prevents the 10-million-write storm when a celebrity tweets."

---

## 8. Synchronous vs Asynchronous Processing

### Synchronous
- **How**: Request → Process → Response (caller waits)
- **Pros**: Simple; immediate feedback; easy error handling
- **Cons**: Caller blocked; cascading failures; latency-sensitive to slowest dependency
- **Best for**: User-facing operations where immediate response is needed (auth, read queries)

### Asynchronous (Message Queue / Event-Driven)
- **How**: Request → Queue → ACK → Background workers process
- **Pros**: Decouples services; handles traffic spikes; retry on failure
- **Cons**: Eventual consistency; harder to debug; no immediate result
- **Best for**: Notifications, email, fanout, analytics, media processing, any "fire and forget"

### Tradeoff Matrix

| Operation | Sync or Async? | Why? |
|-----------|---------------|------|
| Post a tweet | Sync write to DB + Async fanout | User sees "posted" immediately; fanout happens in background |
| Send a message | Sync ACK to sender + Async delivery | Sender gets single-tick; delivery is async |
| Upload media | Sync presigned URL + Async processing | User gets upload URL fast; transcoding/thumbnails happen later |
| Like a tweet | Sync increment counter + Async notifications | Instant UI update; notification can be batched |
| Search indexing | Async | Search lag of seconds is acceptable |
| Payment processing | **Sync** | User must know if payment succeeded before proceeding |

---

## 9. Communication Protocols

### HTTP REST
- **Pros**: Universal, stateless, cacheable, simple
- **Cons**: Overhead per request (headers), no server push, request-response only
- **Best for**: CRUD APIs, public APIs, service-to-service calls

### WebSocket
- **Pros**: Full-duplex, persistent connection, low latency push
- **Cons**: Stateful (harder to scale), connection management, no built-in retry
- **Best for**: Chat, real-time notifications, typing indicators, live dashboards, gaming

### Server-Sent Events (SSE)
- **Pros**: Simple server → client push, works over HTTP, auto-reconnect
- **Cons**: Unidirectional (server → client only), limited browser connections
- **Best for**: Live feeds, stock tickers, notification streams (where client doesn't send data)

### gRPC
- **Pros**: Binary protocol (fast), streaming, strong typing (protobuf), HTTP/2
- **Cons**: Not browser-native (needs proxy), harder to debug, less human-readable
- **Best for**: Internal microservice-to-microservice communication, low-latency RPC

### Long Polling
- **Pros**: Works everywhere (HTTP), simulates push, simpler than WebSocket
- **Cons**: Higher latency than WebSocket, more server resources (holding connections)
- **Best for**: Fallback when WebSocket isn't available, simpler real-time needs

### Decision Table

| Need | Protocol |
|------|----------|
| Real-time bidirectional (chat, gaming) | WebSocket |
| Real-time server → client only (feed updates) | SSE |
| Standard CRUD API | REST |
| Internal service calls (high performance) | gRPC |
| Simple real-time, broad compatibility | Long Polling |

---

## 10. Message Queues & Event Streaming

### Message Queue (RabbitMQ, SQS)
- **How**: Producer → Queue → Consumer. Message deleted after processing.
- **Pros**: Simple, exactly-once delivery (with ACK), dead-letter queues
- **Cons**: No replay; messages consumed once; limited throughput
- **Best for**: Task queues, job scheduling, email/notification sending, decoupling services

### Event Stream (Kafka, Kinesis)
- **How**: Producer → Topic → Consumer Groups. Messages retained for days/weeks.
- **Pros**: Replay capability, high throughput (millions/sec), multiple consumer groups
- **Cons**: More complex, ordering only within partition, higher operational cost
- **Best for**: Event sourcing, real-time analytics, audit logs, fanout to multiple consumers

### Decision Table

| Question | Queue (SQS/RabbitMQ) | Stream (Kafka/Kinesis) |
|----------|---------------------|----------------------|
| Need to replay events? | ❌ | ✅ |
| Multiple consumers for same event? | ❌ (fan-out pattern needed) | ✅ (consumer groups) |
| Throughput > 100K msgs/sec? | ❌ (limited) | ✅ |
| Simple task distribution? | ✅ | ❌ (overkill) |
| Need strict ordering? | ✅ (FIFO queues) | ✅ (per partition) |
| Event sourcing / audit trail? | ❌ | ✅ |

### Talking Point
> "I'd use Kafka here because we need multiple consumers processing the same event — the fanout worker, notification worker, search indexer, and analytics pipeline all consume from the same topic independently."

---

## 11. ID Generation

| Strategy | Format | Sortable? | Coordination? | Throughput | Use Case |
|----------|--------|-----------|---------------|------------|----------|
| **Auto-Increment (DB)** | Sequential integer | ✅ | Single DB bottleneck | Low | Small scale, simple apps |
| **UUID v4** | 128-bit random | ❌ | None needed | High | When ordering doesn't matter |
| **Snowflake** | 64-bit (timestamp + machine + sequence) | ✅ (time) | Machine ID assignment | High (4K/ms/machine) | Tweets, messages, distributed systems |
| **ULID** | 128-bit (timestamp + random) | ✅ (time) | None needed | High | When you want sortable + no coordination |
| **Database Ticket Server** | Sequential, multi-DC | ✅ | Ticket server availability | Medium | Flickr-style (2 ticket servers, odd/even) |
| **MongoDB ObjectID** | 96-bit (timestamp + machine + counter) | ✅ (time) | None needed | High | MongoDB-native systems |

### Decision Framework
- **Need time-sortable IDs?** → Snowflake or ULID
- **Need compact (64-bit)?** → Snowflake
- **Need zero coordination?** → UUID or ULID
- **Need globally unique + sortable + high throughput?** → Snowflake
- **Simple app, single DB?** → Auto-increment

### Talking Point
> "I'd use Snowflake IDs because we need time-sortable IDs for chronological ordering of messages, they fit in a 64-bit integer (efficient storage and indexing), and each machine can generate 4096 IDs per millisecond without coordination."

---

## 12. Sharding / Partitioning Strategies

### Hash-Based Sharding
- **How**: `shard = hash(key) % num_shards`
- **Pros**: Even distribution; simple
- **Cons**: Resharding is painful (all data moves); range queries impossible
- **Best for**: User data sharded by user_id, general-purpose distribution

### Consistent Hashing
- **How**: Hash ring; keys map to nearest node clockwise
- **Pros**: Adding/removing nodes only moves ~1/N of keys; minimal disruption
- **Cons**: Can be uneven without virtual nodes; more complex
- **Best for**: Caches (Memcached, Redis), any system with frequent node changes

### Range-Based Sharding
- **How**: Shard by key range (e.g., A-F on shard 1, G-M on shard 2)
- **Pros**: Range queries efficient; logical grouping
- **Cons**: Hot spots (popular ranges); uneven distribution
- **Best for**: Time-series data (shard by date), alphabetical data

### Geographic Sharding
- **How**: Shard by user region/country
- **Pros**: Data locality (low latency for regional users); compliance (data sovereignty)
- **Cons**: Uneven sizes (US vs Iceland); cross-region queries are hard
- **Best for**: Global apps with data residency requirements

### Composite / Hierarchical Sharding
- **How**: Shard by one key, then sub-shard by another (e.g., user_id → conversation_id)
- **Pros**: Locality for related data; efficient range queries within a shard
- **Cons**: Complex; partition key choice is critical
- **Best for**: Chat messages (partition by conversation), time-series per tenant

### Decision Table

| Data Type | Shard Key | Strategy | Why |
|-----------|-----------|----------|-----|
| Users | user_id | Hash | Even distribution |
| Messages | conversation_id | Hash | Co-locate conversation messages |
| Tweets | tweet_id (Snowflake) | Range (time-based) | Recent tweets accessed more |
| Analytics | campaign_id | Hash | Even distribution across aggregation nodes |
| Geo data | Region/country | Geographic | Data locality, compliance |

---

## 13. Replication Strategies

### Single-Leader (Master-Slave)
- **How**: One master handles writes; replicas handle reads
- **Pros**: Simple; strong consistency on master; scales reads
- **Cons**: Write bottleneck; failover is complex; replication lag
- **Best for**: Traditional web apps with read-heavy workloads

### Multi-Leader
- **How**: Multiple masters accept writes; sync with each other
- **Pros**: Writes in multiple regions; higher write throughput
- **Cons**: Conflict resolution needed; complex
- **Best for**: Multi-region deployments requiring local writes (collaborative editing)

### Leaderless (Quorum-Based)
- **How**: Write to W nodes, read from R nodes, where W + R > N
- **Pros**: No single point of failure; high availability
- **Cons**: Eventual consistency; conflict resolution; slower reads/writes
- **Best for**: Cassandra, DynamoDB; highly available systems

### Replication Factor Tradeoff
| RF | Durability | Read Throughput | Write Latency | Cost |
|----|-----------|-----------------|---------------|------|
| 1 | Low | 1x | Low | Low |
| 3 | High | 3x | Medium | 3x |
| 5 | Very High | 5x | High | 5x |

> **Common choice**: RF=3 with quorum reads/writes (W=2, R=2). Tolerates 1 node failure for both reads and writes.

---

## 14. Load Balancing

### Algorithms

| Algorithm | Description | Best For |
|-----------|-------------|----------|
| **Round Robin** | Distribute sequentially | Homogeneous servers, stateless requests |
| **Weighted Round Robin** | Servers with different capacities get proportional traffic | Mixed server sizes |
| **Least Connections** | Route to server with fewest active connections | Long-lived connections, varied request times |
| **IP Hash** | Hash client IP to determine server | Session affinity without cookies |
| **Consistent Hashing** | Route by content/key hash | Cache distribution, stateful routing |
| **Random** | Pick a random server | Simple, surprisingly effective at scale |

### Layer 4 vs Layer 7

| | Layer 4 (TCP/UDP) | Layer 7 (HTTP/Application) |
|---|---|---|
| **Speed** | Faster (no payload inspection) | Slower (inspects headers/content) |
| **Intelligence** | Limited (IP + port) | Rich (URL path, headers, cookies) |
| **SSL** | Pass-through | Terminates SSL (offloads from backends) |
| **Use case** | Raw TCP performance | Content-based routing, A/B testing |

---

## 15. Rate Limiting

### Algorithms

| Algorithm | How It Works | Pros | Cons |
|-----------|-------------|------|------|
| **Token Bucket** | Tokens added at fixed rate; request consumes a token | Allows bursts; smooth rate control | Slightly complex state |
| **Leaky Bucket** | Requests queued; processed at fixed rate | Smooth output; no bursts | Doesn't handle legitimate bursts well |
| **Fixed Window** | Count requests per fixed time window (e.g., per minute) | Simple | Burst at window boundaries (2x rate) |
| **Sliding Window Log** | Track timestamp of each request in a sorted set | Accurate; no boundary issues | Memory-intensive (stores every timestamp) |
| **Sliding Window Counter** | Weighted combination of current + previous window counts | Balance of accuracy and efficiency | Approximate |

### Where to Rate Limit

| Layer | What to Limit | Example |
|-------|--------------|---------|
| **API Gateway** | Per-user, per-IP, per-API-key | 100 requests/minute per user |
| **Application** | Per-action (tweets/hour, messages/day) | 300 tweets/3 hours |
| **Infrastructure** | Per-service, circuit breaker | Service A → Service B: 1000 RPS max |

### Talking Point
> "I'd use Token Bucket implemented in Redis — it allows legitimate bursts (user rapidly scrolling), the state is a single hash per user, and Redis HINCRBY gives us atomic updates. For distributed rate limiting, each API server checks the same Redis key."

---

## 16. Search & Indexing

### Full-Text Search Engine (Elasticsearch / Solr)
- **Pros**: Fast full-text search, relevance scoring, fuzzy matching, faceted search
- **Cons**: Eventual consistency with source DB, operational complexity, storage overhead
- **Best for**: Tweet search, product search, log search

### Inverted Index (built into search engines)
- **How**: Maps each word → list of document IDs containing that word
- **Pros**: O(1) lookup per term; Boolean queries efficient
- **Cons**: Index build time; storage for large vocabularies

### Database Full-Text Index (PostgreSQL, MySQL)
- **Pros**: No separate system; consistent with data
- **Cons**: Limited relevance tuning; poor at scale
- **Best for**: Simple search on small-medium datasets

### Search Pipeline Tradeoffs

| Decision | Option A | Option B |
|----------|----------|----------|
| **Indexing latency** | Sync indexing (consistent but slow writes) | Async indexing via Kafka (fast writes, slight lag) |
| **Index scope** | Index everything (complete but expensive) | Index hot data only (cheaper but incomplete) |
| **Relevance ranking** | Recency-weighted (simpler) | ML-based personalized ranking (better but complex) |

### Talking Point
> "I'd use Elasticsearch with async indexing via Kafka — tweets are written to Cassandra first, then a Kafka consumer indexes them in Elasticsearch. This means search results may lag by ~1 second, but it decouples the write path from the search indexing path."

---

## 17. Storage Tiering & Data Lifecycle

### Tiered Storage Pattern

| Tier | Storage | Data Age | Latency | Cost |
|------|---------|----------|---------|------|
| **Hot** | Redis / In-memory | 0-24 hours | < 10ms | $$$ |
| **Warm** | SSD-backed DB (ClickHouse, Cassandra) | 1-30 days | < 500ms | $$ |
| **Cold** | HDD-backed DB | 30 days - 2 years | < 5s | $ |
| **Archive** | Object storage (S3 Glacier) | 2+ years | Minutes-hours | ¢ |

### Rollup Strategy
- **Minute → Hour**: Aggregate 60 minute-level rows into 1 hourly row
- **Hour → Day**: Aggregate 24 hourly rows into 1 daily row
- **Benefit**: Dramatically reduces storage for old data while keeping it queryable
- **Cost**: Lose granularity for older data (acceptable for most analytics)

### Talking Point
> "I'd use a 4-tier storage strategy: Redis for the last 24 hours at minute granularity, ClickHouse on SSD for the last 30 days at hourly granularity, ClickHouse on HDD for 2 years at daily granularity, and S3 Glacier for raw event archive. A nightly rollup job aggregates minute → hour and hour → day."

---

## 18. Blob / Media Storage

### Storage Options

| Option | Pros | Cons | Best For |
|--------|------|------|----------|
| **Object Storage (S3/GCS)** | Unlimited scale, cheap, durable (11 nines) | Higher latency than local; egress costs | Images, videos, documents, backups |
| **CDN + Object Storage** | Low latency globally; caches at edge | Cache invalidation complexity; cost | Frequently accessed media |
| **Block Storage (EBS)** | Low latency, good for databases | Limited scale, tied to single region | Database volumes |
| **Local/Attached Storage** | Lowest latency | Not durable, not shared | Ephemeral cache, temp files |

### Media Processing Pipeline
1. Client requests **presigned upload URL** from backend
2. Client uploads directly to S3 (no backend bottleneck)
3. S3 triggers async processing (Lambda / worker)
4. Generate thumbnails, transcode video, compress
5. Store processed versions back to S3
6. Serve via CDN

### Tradeoff: Store Media Reference vs Inline
- **Reference (URL in DB, blob in S3)**: Scalable, cheap, CDN-friendly → **almost always the right choice**
- **Inline (blob in DB)**: Simple for tiny files (< 1KB), but doesn't scale

---

## 19. CDN & Edge Caching

### When to Use CDN
- Static assets (images, videos, CSS, JS)
- Content consumed globally
- High read:write ratio

### CDN Tradeoffs

| Decision | Option A | Option B |
|----------|----------|----------|
| **Push vs Pull CDN** | Push: pre-upload content to CDN (predictable, good for known content) | Pull: CDN fetches from origin on first request (simpler, lazy) |
| **TTL length** | Short TTL (fresher content, more origin hits) | Long TTL (fewer origin hits, staler content) |
| **Cache granularity** | Cache full pages (simple, high hit ratio) | Cache fragments/API responses (more flexible, lower hit ratio) |

### Cache Invalidation at CDN
- **TTL-based**: Set `Cache-Control: max-age=3600`. Simple but stale for up to 1 hour.
- **Purge API**: Actively purge specific URLs when content changes. Fresh but adds complexity.
- **Versioned URLs**: `image_v2.jpg` or `image.jpg?v=abc123`. Never stale; old URLs cached forever (good).

### Talking Point
> "I'd serve all media through a CDN with pull-through caching. When a user uploads an image, it goes to S3. The first read pulls from S3 through the CDN and caches it at the edge. Subsequent reads from the same region hit the edge cache. With a 95% cache hit ratio, this reduces origin load by 20x."

---

## 20. Deduplication

### When You Need It
- Message delivery (at-least-once guarantees cause duplicates)
- Event ingestion (producer retries)
- Payment/billing systems (double-charge prevention)
- Ad click counting (inflated metrics = overbilling)

### Strategies

| Strategy | How | Memory | Accuracy | Best For |
|----------|-----|--------|----------|----------|
| **Bloom Filter** | Probabilistic set membership (false positives, no false negatives) | Very low (~1GB for billions of items) | ~99.99% (tunable) | First-pass filter for high-volume streams |
| **Redis SETNX** | Set-if-not-exists with TTL | Medium | 100% (exact) | Moderate-volume exact dedup |
| **Database Unique Constraint** | Primary key / unique index rejects duplicates | Disk-based | 100% | Low-volume, durable dedup |
| **Two-Tier (Bloom + Redis)** | Bloom for fast path → Redis for Bloom positives only | Low + medium | 100% | High-volume streams (ad clicks, events) |
| **Idempotency Key** | Client sends unique key; server checks before processing | Medium | 100% | Payment APIs, form submissions |

### Talking Point
> "I'd use a two-tier dedup: Bloom filter in-memory handles 99.99% of events in < 1 microsecond. The 0.01% that the Bloom says 'maybe duplicate' go to Redis SETNX for exact verification. This gives us 100% accuracy at very low cost."

---

## 21. Contention & Concurrency Control

### The Problem
Multiple users/processes trying to modify the same resource simultaneously (e.g., booking the last seat, buying the last item, bidding on an auction).

### Strategies

| Strategy | How | Pros | Cons | Best For |
|----------|-----|------|------|----------|
| **Optimistic Locking** | Read version → modify → write if version unchanged | No lock waiting; high throughput | Retries on conflict; wasted work | Low contention (profile updates, most writes) |
| **Pessimistic Locking** | Acquire lock → read → modify → write → release | No conflicts; guaranteed consistency | Lock waiting; deadlock risk; lower throughput | High contention (ticket booking, inventory) |
| **Distributed Lock (Redis/Zookeeper)** | Acquire distributed lock across services | Works across services | Single point of failure if lock service down; complexity | Cross-service coordination |
| **Compare-and-Swap (CAS)** | Atomic read-modify-write at DB level | Simple; no external locks | Only works for single-row operations | Counters, simple state machines |
| **Queue-Based Serialization** | Route all writes for a key through a single queue | Eliminates contention entirely | Added latency; queue is bottleneck | Auction bids, sequential processing |
| **Database-Level (SELECT FOR UPDATE)** | Row-level lock in transaction | Built into RDBMS; simple | Holds connection; limits throughput | Booking systems within single DB |

### Decision Framework
- **Low contention (< 1% conflicts)?** → Optimistic locking
- **High contention (many users, one resource)?** → Pessimistic locking or queue
- **Cross-service?** → Distributed lock (Redis Redlock)
- **Atomic counter?** → Redis INCR or DB CAS
- **Must be strictly ordered?** → Queue-based serialization

### Talking Point
> "For ticket booking with limited inventory, I'd use pessimistic locking with `SELECT FOR UPDATE` — when a user starts checkout, we lock that seat row in the database. If they don't complete within 10 minutes, the lock is released. This prevents double-booking at the cost of reduced concurrency."

---

## 22. Exactly-Once vs At-Least-Once vs At-Most-Once

| Guarantee | Description | Implementation Cost | Use Case |
|-----------|-------------|-------------------|----------|
| **At-Most-Once** | Fire and forget; message may be lost | Lowest | Logging, metrics (loss is tolerable) |
| **At-Least-Once** | Retry until ACK; may deliver duplicates | Medium | Notifications, feed updates (duplicates are tolerable) |
| **Exactly-Once** | Each message processed exactly once | Highest (dedup + transactions) | Payments, billing, ad click counting |

### How to Achieve Exactly-Once
1. **Idempotent operations**: Make processing the same event twice produce the same result
2. **Transactional outbox**: Write event + state change in same DB transaction
3. **Kafka transactions + Flink checkpoints**: End-to-end exactly-once in streaming
4. **Deduplication layer**: Track processed event IDs

### Talking Point
> "True exactly-once is expensive. For most operations (notifications, feed fanout), at-least-once with client-side dedup is sufficient. For financial operations (billing, ad click counting), we invest in exactly-once via Kafka transactions + Flink checkpointing + a dedup layer."

---

## 23. Batch vs Stream Processing

| Dimension | Batch (Spark, MapReduce) | Stream (Flink, Kafka Streams) |
|-----------|------------------------|------------------------------|
| **Latency** | Minutes to hours | Milliseconds to seconds |
| **Throughput** | Very high (processes all data at once) | High (per-event) |
| **Complexity** | Simpler (read all → process → write) | Higher (state management, windowing, watermarks) |
| **Accuracy** | Exact (complete data) | May need corrections (late events) |
| **Cost** | Lower (run periodically) | Higher (always running) |
| **Use case** | Daily reports, ML training, reconciliation | Real-time dashboards, alerts, fraud detection |

### Lambda Architecture (Batch + Stream)
- **Speed layer**: Stream processing for approximate real-time results
- **Batch layer**: Periodic batch jobs for exact/corrected results
- **Serving layer**: Merge both for queries
- **Tradeoff**: Two codebases to maintain; eventual correctness

### Kappa Architecture (Stream Only)
- **Everything is a stream**: Replay events for reprocessing instead of batch
- **Simpler**: One codebase
- **Tradeoff**: Reprocessing large datasets via replay is slow

### Talking Point
> "I'd use Flink for real-time aggregation with < 30s freshness, plus a daily Spark batch job that recomputes exact counts from raw events in S3. The batch job catches any drift from late events or edge cases. This is the Lambda architecture pattern — stream for speed, batch for correctness."

---

## 24. Monolith vs Microservices

| Dimension | Monolith | Microservices |
|-----------|----------|---------------|
| **Complexity** | Simple deployment, complex codebase | Simple services, complex orchestration |
| **Scaling** | Scale entire app (even if only one part needs it) | Scale each service independently |
| **Development speed** | Fast initially, slows as it grows | Slower initially, scales with team size |
| **Data consistency** | Easy (single DB, transactions) | Hard (distributed transactions, eventual consistency) |
| **Deployment** | All or nothing | Independent deployments |
| **Team autonomy** | Low (shared codebase) | High (service ownership) |
| **Debugging** | Easier (single process) | Harder (distributed tracing needed) |

### When to Use Each
- **Monolith**: Early-stage startup, small team (< 10 engineers), simple domain
- **Microservices**: Large team, complex domain, independent scaling needs, different tech stacks per service

### In System Design Interviews
> Almost always design as microservices — the interviewer wants to see service decomposition. But mention: "In practice, I'd start with a modular monolith and extract services as the team and product grow."

---

## 25. API Design Tradeoffs

### REST vs GraphQL vs gRPC

| Dimension | REST | GraphQL | gRPC |
|-----------|------|---------|------|
| **Flexibility** | Fixed endpoints, over/under-fetching | Client specifies exact fields | Fixed service definitions (protobuf) |
| **Performance** | HTTP/1.1, text-based | Single endpoint, reduces round trips | HTTP/2, binary, streaming |
| **Caching** | Easy (HTTP caching, CDN) | Hard (POST requests, custom caching) | Hard (binary protocol) |
| **Learning curve** | Low | Medium | Medium-High |
| **Best for** | Public APIs, simple CRUD | Mobile apps (bandwidth-sensitive), complex UIs | Internal service-to-service |

### API Versioning Strategies

| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| **URL path** | `/api/v1/users` | Clear, easy to route | URL pollution |
| **Header** | `Accept: application/vnd.api+json;version=1` | Clean URLs | Hidden, harder to test |
| **Query param** | `/api/users?version=1` | Easy to switch | Can be omitted |

### Talking Point
> "I'd use REST for the public API (simple, cacheable, well-understood) and gRPC for internal service-to-service calls (binary protocol, streaming, strong typing). For mobile clients that need flexible data fetching, GraphQL could reduce over-fetching on slow networks."

---

## 26. Pagination Strategies

| Strategy | How | Pros | Cons | Best For |
|----------|-----|------|------|----------|
| **Offset-Based** | `?page=3&limit=20` (OFFSET 40, LIMIT 20) | Simple, jump to any page | Slow for large offsets; inconsistent with real-time inserts | Admin dashboards, small datasets |
| **Cursor-Based** | `?cursor=abc123&limit=20` (WHERE id > cursor) | Consistent with inserts; O(1) regardless of position | Can't jump to arbitrary page; opaque cursor | Infinite scroll feeds, timelines |
| **Keyset (Seek)** | `?after_id=123&limit=20` (WHERE id > 123 ORDER BY id) | Fast; no offset scan | Requires sortable unique key | Time-ordered data (messages, events) |
| **Time-Based** | `?before=2025-01-10T00:00:00Z&limit=20` | Natural for time-series | Clock skew; not unique within same timestamp | Logs, analytics, message history |

### Decision Framework
- **User scrolling an infinite feed?** → Cursor-based
- **Admin viewing page 47 of a table?** → Offset-based
- **Fetching messages in a conversation?** → Keyset (by message_id)
- **Querying time-series data?** → Time-based

### Talking Point
> "I'd use cursor-based pagination for the timeline — it's O(1) performance regardless of how far the user scrolls, and it handles new tweets being inserted in real-time without duplicating or skipping items. The cursor is an opaque token encoding the last tweet_id."

---

## 27. Encryption & Security

### Encryption at Rest vs In Transit

| Type | What | Technology | When |
|------|------|-----------|------|
| **In Transit** | Data moving over the network | TLS 1.3, HTTPS, WSS | Always |
| **At Rest** | Data stored on disk/S3 | AES-256, server-side encryption | Sensitive data (PII, financial) |
| **End-to-End (E2EE)** | Only sender and recipient can read | Signal Protocol, Diffie-Hellman key exchange | Messaging (WhatsApp, Signal) |

### E2EE Tradeoffs
- ✅ Server cannot read messages (privacy guarantee)
- ✅ Even if server compromised, past messages are safe
- ❌ Server cannot search/index message content
- ❌ Server cannot moderate content
- ❌ Multi-device sync is more complex (key distribution)
- ❌ Message backup/recovery is harder

### Authentication Tradeoffs

| Method | Pros | Cons | Best For |
|--------|------|------|----------|
| **JWT (stateless)** | No server-side session; scales horizontally | Can't revoke until expiry; token size | Microservices, APIs |
| **Session-based (stateful)** | Easy revocation; small cookie | Requires session store (Redis); sticky sessions | Traditional web apps |
| **OAuth 2.0** | Delegated auth; third-party integration | Complex flow; token management | "Login with Google/Facebook" |
| **API Keys** | Simple; per-client identification | No user context; hard to rotate | Service-to-service, public APIs |

### Talking Point
> "For a messaging system, I'd implement E2EE using the Signal Protocol — the server only sees encrypted blobs. The tradeoff is that we can't do server-side search or content moderation, but the privacy guarantee is non-negotiable for a messaging product."

---

## 28. Availability & Disaster Recovery

### Availability Targets

| Nines | Downtime/Year | Downtime/Month | Typical System |
|-------|--------------|----------------|----------------|
| 99% (2 nines) | 3.65 days | 7.3 hours | Internal tools |
| 99.9% (3 nines) | 8.77 hours | 43.8 minutes | Standard SaaS |
| 99.99% (4 nines) | 52.6 minutes | 4.38 minutes | Critical services (messaging, payments) |
| 99.999% (5 nines) | 5.26 minutes | 26.3 seconds | Infrastructure (DNS, CDN) |

### Failover Patterns

| Pattern | Description | Recovery Time | Data Loss |
|---------|-------------|---------------|-----------|
| **Active-Passive** | Standby takes over on failure | Minutes | Possible (replication lag) |
| **Active-Active** | Both regions serve traffic; failover is instant | Seconds | Minimal (both regions have data) |
| **Pilot Light** | Minimal infra in standby; scale up on failure | 10-30 minutes | Possible |
| **Multi-Region Active-Active** | All regions serve traffic independently | Near-zero | Near-zero (multi-region replication) |

### Graceful Degradation Strategies
- **Cache fallback**: If DB is down, serve stale data from cache
- **Feature flags**: Disable non-critical features under load
- **Circuit breaker**: Stop calling a failing dependency; return cached/default response
- **Read-only mode**: Allow reads but reject writes during partial outage
- **Queue writes**: Accept writes to a queue; process when service recovers

### Talking Point
> "I'd design for 99.99% availability using active-active multi-region deployment. Each region can independently serve reads. Writes go to the nearest region and asynchronously replicate. If a region fails, DNS routes traffic to healthy regions within seconds. For the brief period during failover, we accept eventual consistency — a slightly stale feed is better than no feed."

---

## 29. Pre-Computation vs On-Demand Computation

| | Pre-Computation | On-Demand Computation |
|---|---|---|
| **Read latency** | Very low (pre-computed result) | Higher (compute at query time) |
| **Write cost** | Higher (compute + store on every write) | Lower (just store raw data) |
| **Storage** | More (store computed results) | Less (only raw data) |
| **Freshness** | May be stale until next computation | Always fresh |
| **Best for** | Hot data, dashboards, feeds, leaderboards | Ad-hoc queries, infrequent access, long-tail data |

### Examples

| System | Pre-Computed | On-Demand |
|--------|-------------|-----------|
| Twitter | Home timeline (Redis sorted sets) | Celebrity tweets merged at read time |
| Ad Analytics | Minute/hour/day rollups in Redis + ClickHouse | Custom breakdown queries on raw data |
| Leaderboard | Top-100 in Redis sorted set | Full ranking for any user on request |
| E-commerce | Product search index (Elasticsearch) | Complex filter combinations computed at query time |

### Talking Point
> "I'd pre-compute the aggregation rollups at write time — each event updates minute, hour, and day counters atomically. This means dashboard queries are just key lookups (< 10ms) instead of scanning raw events. The tradeoff is 16x write amplification per event (one update per dimension combination), but at our query SLA of < 500ms, pre-computation is necessary."

---

## 30. Backpressure & Flow Control

### The Problem
When a producer generates data faster than a consumer can process it, the system must decide what to do.

### Strategies

| Strategy | How | Tradeoff |
|----------|-----|----------|
| **Buffer (Queue)** | Store excess in a queue; process when capacity frees | Memory/disk grows; latency increases |
| **Drop** | Discard excess events | Data loss; simple |
| **Sample** | Process a random subset; extrapolate | Approximate results; preserves throughput |
| **Throttle producer** | Tell producer to slow down (backpressure signal) | Producer latency increases; no data loss |
| **Auto-scale consumer** | Add more consumer instances | Cost increases; scaling takes time |
| **Priority-based shedding** | Drop low-priority events; keep high-priority | Requires priority classification |

### Talking Point
> "Under backpressure (e.g., 5x traffic spike during Super Bowl), I'd use priority-based shedding: click events are always processed (they represent revenue), while impression events are sampled at 10% with a 10x correction factor applied to counts. This preserves billing accuracy while preventing the pipeline from falling behind."

---

## 31. Hot Partition / Hot Key Handling

### The Problem
One partition/key receives disproportionate traffic (e.g., celebrity tweet, viral video, flash sale item).

### Strategies

| Strategy | How | Tradeoff |
|----------|-----|----------|
| **Key salting / spreading** | Append random suffix to hot key → spread across partitions | Read requires aggregation across salted keys |
| **Local caching** | Cache hot key in application memory | Stale data; memory usage |
| **Dedicated partition** | Route hot key to a larger/dedicated partition | Operational overhead; manual intervention |
| **Read replicas** | Replicate hot data across multiple read nodes | Replication lag; more storage |
| **Write coalescing** | Buffer writes locally → batch flush to hot key | Brief staleness; complexity |

### Examples
- **Celebrity tweets**: Don't fan out to 10M timelines → pull at read time (hybrid fanout)
- **Flash sale item**: Cache inventory count in Redis; single atomic DECR; overflow to queue
- **Hot Kafka partition**: Salt partition key with random suffix → merge downstream
- **Hot database row**: Distribute counter across N rows → SUM at read time

### Talking Point
> "For a hot campaign generating 50% of all ad events, I'd salt the Kafka partition key — append a random number 0-19 to the campaign_id, spreading it across 20 partitions. A downstream merge step aggregates the partial counts every 10 seconds before writing to the serving layer."

---

## 32. Leader Election & Coordination

### When You Need It
- Only one instance should process a task (job scheduler, cron)
- Distributed locks (booking, inventory)
- Configuration management (cluster leader)
- Consensus on shared state

### Options

| Tool | Mechanism | Pros | Cons |
|------|-----------|------|------|
| **ZooKeeper** | ZAB consensus protocol | Battle-tested; strong guarantees | Operational complexity; JVM |
| **etcd** | Raft consensus | Simpler than ZK; Kubernetes-native | Smaller ecosystem |
| **Redis (Redlock)** | Distributed lock across N Redis instances | Simple; fast | Debated correctness (Martin Kleppmann criticism) |
| **Database (Advisory Locks)** | SELECT ... FOR UPDATE or advisory lock | No extra infrastructure | DB becomes bottleneck; not designed for this |
| **Raft/Paxos (built-in)** | Embedded consensus in application | No external dependency | Complex to implement correctly |

### Talking Point
> "For the job scheduler, I'd use ZooKeeper for leader election — only the elected leader assigns jobs to workers. If the leader dies, ZooKeeper's session timeout triggers re-election within seconds. Workers are stateless and pull jobs from a queue, so losing the leader briefly just means a short pause in job assignment."

---

## 33. Reconciliation (Streaming vs Batch)

### The Problem
Streaming systems may drift from ground truth over time due to late events, sampling under backpressure, or edge cases in exactly-once processing.

### Pattern: Daily Batch Reconciliation
1. Stream processing provides **real-time approximate** results
2. Daily batch job (Spark) recomputes **exact** results from raw events
3. Compare streaming vs batch results
4. Correct any discrepancies > threshold (e.g., > 0.1%)

### When to Use
- Financial/billing systems (ad click counting, payment reconciliation)
- Inventory management (stream updates vs periodic full count)
- Analytics dashboards (stream for real-time, batch for reports)

### Talking Point
> "I'd run a daily reconciliation job at 3 AM UTC — Spark reads all raw events from S3 for the previous day, computes exact aggregates, and compares them to the streaming aggregates in ClickHouse. Any dimension with > 0.1% drift gets corrected. This gives us real-time speed with batch-level correctness."

---

## 34. Multi-Region / Geo-Distribution

### Deployment Patterns

| Pattern | Writes | Reads | Consistency | Complexity |
|---------|--------|-------|-------------|------------|
| **Single-Region** | One region | One region | Strong | Low |
| **Primary + Read Replicas** | One region | All regions | Eventual (for replicas) | Medium |
| **Active-Passive (Failover)** | Primary region | Primary region (standby idle) | Strong in primary | Medium |
| **Active-Active (Multi-Master)** | All regions | All regions | Eventual / conflict resolution | High |

### Data Residency / Sovereignty
- Some data must stay in specific regions (GDPR, local laws)
- Shard by user region: EU users' data in EU region
- Cross-region queries become expensive

### Conflict Resolution (Multi-Master)
- **Last-writer-wins (LWW)**: Simple but lossy
- **Merge (CRDT)**: Conflict-free replicated data types; complex but no data loss
- **Application-level resolution**: Custom logic per entity type

### Talking Point
> "I'd deploy active-active in 3 regions (US, EU, APAC). Reads are always local for low latency. Writes go to the local region and asynchronously replicate. For conflict resolution, I'd use last-writer-wins for simple fields (profile name) and CRDTs for counters (like counts). User data is sharded by region for data sovereignty compliance."

---

## 35. Cost vs Performance

### Common Tradeoffs

| Spend More On | To Get | Instead Of |
|---------------|--------|------------|
| More Redis nodes | Lower read latency (< 1ms) | Slower DB reads (5-50ms) |
| More Kafka partitions | Higher write throughput | Bottlenecked ingestion |
| CDN | Global low latency | Users far from origin hit slow origin |
| Pre-computation (write amplification) | Instant read queries | Slow on-demand aggregation |
| Multi-region deployment | Low latency + high availability | Single-region with higher latency for distant users |
| SSD storage | Faster queries on warm data | HDD with higher query latency |
| More replicas | Higher read throughput + availability | Fewer replicas, more risk |

### Cost Optimization Strategies
- **Tiered storage**: Hot → Warm → Cold → Archive (see section 17)
- **Auto-scaling**: Scale up during peak, scale down at night
- **Spot/preemptible instances**: For batch processing and non-critical workers
- **Compression**: 10x savings in columnar stores (ClickHouse, Parquet)
- **Data lifecycle policies**: Auto-delete or archive data after retention period
- **Caching**: Reduce expensive DB/API calls with cheap Redis reads

### Talking Point
> "The total infrastructure cost is ~$140K/month to process 200 billion events/day — that's $0.70 per million events. Given that these events represent ~$500M/month in ad revenue, the infrastructure cost is 0.028% of revenue. The biggest cost driver is Kafka (30 brokers at $45K/month), which we could reduce by tuning retention and compression."

---

## 🎯 Quick Reference: Which Tradeoff for Which Problem?

| System Design Problem | Key Tradeoffs to Discuss |
|----------------------|-------------------------|
| **Twitter / Instagram Feed** | Push vs Pull fanout, Hybrid approach, Cache-aside, Snowflake IDs, WebSocket vs SSE |
| **WhatsApp / Chat** | WebSocket at scale, E2EE, At-least-once delivery, Presence (heartbeat + TTL), Group fanout |
| **URL Shortener** | Hash vs counter ID generation, Read-heavy caching, 301 vs 302 redirect, Base62 encoding |
| **Ticket Booking / Auction** | Pessimistic locking, Inventory contention, Distributed locks, Exactly-once payment |
| **Ad Click Aggregator** | Stream vs batch, Exactly-once counting, Bloom filter dedup, Tiered storage, Backpressure |
| **Notification System** | Async processing, Priority queues, Push vs pull, Rate limiting, Fan-out on write |
| **Search System** | Inverted index, Async indexing via Kafka, Relevance ranking, Elasticsearch vs DB full-text |
| **Rate Limiter** | Token bucket vs sliding window, Distributed Redis, Per-user vs per-IP vs per-API |
| **Leaderboard** | Redis sorted sets, Pre-computation, Hot key handling, Approximate vs exact ranking |
| **Job Scheduler** | Leader election, At-least-once execution, Idempotency, Dead letter queue, Priority scheduling |
| **Video Streaming (Netflix)** | CDN, Adaptive bitrate, Presigned URLs, Async transcoding, Storage tiering |
| **Web Crawler** | BFS vs DFS, Politeness (rate limiting per domain), URL dedup (Bloom filter), Distributed coordination |
| **Trending Topics** | Sliding window counting, Approximate top-K, Stream processing, Anomaly detection |
| **Key-Value Store** | Consistent hashing, Quorum reads/writes, Gossip protocol, Merkle trees for anti-entropy |
| **Payment System** | Strong consistency, Idempotency keys, Two-phase commit, Saga pattern, Reconciliation |

---

## 🗣️ Interview Delivery Framework

When discussing any tradeoff in an interview, use this structure:

### The 4-Part Pattern
1. **State the decision**: "For the message database, I'd choose Cassandra over MySQL."
2. **Explain why**: "Because we have 1.15M writes/sec — Cassandra's leaderless architecture handles this with linear horizontal scaling."
3. **Acknowledge the tradeoff**: "The downside is we lose ACID transactions and rich query flexibility — we can only query by partition key efficiently."
4. **Explain when the alternative wins**: "If we needed complex joins or strong transactional guarantees (like a payment system), MySQL with sharding would be the better choice."

### Power Phrases for Tradeoff Discussions
- "The tradeoff here is **latency vs consistency** — I'd choose eventual consistency for the read path because a slightly stale feed is acceptable, but strong consistency for the write path where we can't lose data."
- "This is the classic **compute at write time vs compute at read time** tradeoff — pre-computing gives us < 10ms reads at the cost of write amplification."
- "I'd optimize for the **common case** (99% of users have < 10K followers) and handle the **edge case** (celebrities) separately with a pull-based approach."
- "This follows the **single-writer principle** — by routing all updates for a given key through one partition, we eliminate contention without needing distributed locks."

---

---

## 📚 PART 2: Reusable Deep Dive Topics

> Deep dives are specific technical challenges you'll encounter across multiple HLD problems. Each one below is a **mini-essay** you can deliver when the interviewer says "let's go deeper on that."

---

### Deep Dive A: Hybrid Fan-Out (The Celebrity Problem)

**Applies to**: Twitter, Instagram, any social feed, notification system

**The Problem**: A celebrity with 10M followers tweets. Pure push = 10M Redis writes taking ~100 seconds. Pure pull = 10M users each querying the celebrity's tweets at read time, crushing the DB.

**The Solution**: Hybrid fan-out with a follower threshold (~1M).

**How It Works**:
1. On tweet creation, check the author's follower count
2. If **< 1M followers** (99% of users): **Fan-out on write** — push the tweet_id into each follower's Redis timeline (sorted set by timestamp)
3. If **≥ 1M followers** (celebrities): **Skip fanout** — do nothing at write time
4. On timeline read: fetch pre-computed timeline from Redis (normal users' tweets) + query celebrity tweets on-demand from DB + merge + sort by timestamp

**Key Details**:
- Redis sorted sets: `ZADD timeline:{user_id} {timestamp} {tweet_id}`; `ZREVRANGE` to fetch latest 50
- Trim timelines to last 500 entries to cap memory
- Celebrity tweets fetched via: `SELECT * FROM tweets WHERE user_id IN (celebrity_ids) AND created_at > NOW() - 1 day ORDER BY created_at DESC LIMIT 50`
- The merge happens at read time in the Timeline Service

**Numbers**: For a user following 5 celebrities and 500 normal users, read = 1 Redis call (normal tweets) + 1 DB call (celebrity tweets) + in-memory merge. Total < 50ms.

**Talking Point**: "This handles the 10M-follower problem while keeping reads under 200ms for 99% of users. The tradeoff is two code paths and merge logic, but it's essential at Twitter/Instagram scale."

---

### Deep Dive B: Distributed Rate Limiting (Token Bucket in Redis)

**Applies to**: Any API, rate limiter design, abuse prevention

**The Problem**: Multiple API servers need to enforce a shared rate limit (e.g., 100 tweets/hour per user). Each server can't track independently — user could spread requests across servers.

**The Solution**: Centralized rate limiting in Redis using Token Bucket algorithm.

**How It Works**:
1. Redis hash per user per action: `ratelimit:{user_id}:{action}` → `{tokens: N, last_refill: timestamp}`
2. On each request: calculate tokens to add since `last_refill` (based on refill rate), add them (capped at max), then try to consume 1 token
3. If tokens ≥ 1: allow request, decrement
4. If tokens < 1: reject with `429 Too Many Requests` and `X-Rate-Limit-Reset` header

**Why Token Bucket**: Allows bursts (user sends 10 requests quickly, then stops) while enforcing average rate. Leaky Bucket would smooth out bursts, which feels unnatural for human behavior.

**Alternative — Sliding Window Counter**: Weighted combination of current + previous window counts. Simpler, approximate, but avoids the boundary-burst problem of Fixed Window. Good enough for most cases.

**Talking Point**: "I'd implement Token Bucket in Redis with a Lua script for atomicity — the check-and-decrement must be a single atomic operation, otherwise two concurrent requests could both read tokens=1 and both succeed."

---

### Deep Dive C: Snowflake ID Generation

**Applies to**: Twitter, messaging, any system needing sortable distributed IDs

**The Problem**: Need globally unique IDs that are (a) time-sortable for chronological queries, (b) generated without central coordination, and (c) compact (64-bit, not 128-bit UUID).

**The Solution**: Snowflake — 64-bit ID = `{1 bit unused}{41 bits timestamp}{10 bits machine_id}{12 bits sequence}`

**Key Details**:
- **41 bits timestamp** (ms since custom epoch): ~69 years of IDs
- **10 bits machine ID**: up to 1024 machines (assigned via ZooKeeper or config)
- **12 bits sequence**: up to 4096 IDs per millisecond per machine
- If sequence exhausts within a millisecond → wait for next millisecond (clock spin)
- If clock moves backward (NTP correction) → throw error or use last-known timestamp

**Why Not UUID?**: UUID v4 is 128 bits (wastes index space), not sortable (random), poor B-tree locality. Snowflake is half the size, roughly chronological, and has excellent B-tree locality.

**Why Not Auto-Increment?**: Single DB bottleneck. Ticket servers (Flickr pattern) help but add availability risk. Snowflake needs zero coordination after initial machine ID assignment.

**Talking Point**: "Each machine generates 4096 IDs per millisecond independently. The timestamp prefix means IDs are roughly chronological — we can sort by ID instead of maintaining a separate timestamp index, which improves query performance."

---

### Deep Dive D: Seat Reservation & Preventing Double-Selling

**Applies to**: Ticket booking, hotel reservation, flight booking, any limited-inventory system

**The Problem**: Two users select the same seat simultaneously. Without proper concurrency control, both succeed → double-sold seat.

**The Solution**: Two-layer locking — Redis distributed lock (coarse) + DB `SELECT FOR UPDATE` (fine).

**How It Works**:
1. **Redis lock** on event_id (5s TTL): prevents thundering herd — only one reservation transaction at a time per event
2. **DB `SELECT FOR UPDATE`**: row-level pessimistic lock on the specific seat rows — prevents double-sell even if Redis fails
3. Check all seats are `AVAILABLE` → update to `HELD` with `held_until = NOW() + 10 min` → create reservation record
4. Release Redis lock
5. Schedule hold expiration (10 min timer)

**Why Both Locks?**: Redis absorbs 500K concurrent users (only one gets through per event). DB lock is the correctness guarantee — if Redis crashes, the DB still prevents double-selling. Belt and suspenders.

**Hold Expiration (Two-Tier)**:
- **Tier 1 — Redis key TTL**: Set `hold:{reservation_id}` with TTL = hold duration. On expiry, keyspace notification triggers seat release. Handles 99% of cases within 1-2 seconds.
- **Tier 2 — DB polling**: Every 30 seconds, scan `WHERE status = 'HELD' AND held_until < NOW()`. Catches the 1% that Redis missed (crash, eviction).

**Talking Point**: "Redis handles the thundering herd, DB guarantees correctness. The two-tier expiration ensures holds are always released — Redis for speed, DB polling as a safety net."

---

### Deep Dive E: Virtual Queue for Flash Sales

**Applies to**: Ticket booking, flash sales, limited drops (sneakers, GPU launches)

**The Problem**: 500K users hit "Buy" simultaneously when tickets go on sale. Without a queue, servers crash. With a simple queue, you still need fairness (FIFO) and controlled admission.

**The Solution**: Redis sorted set as a FIFO queue with batch admission.

**How It Works**:
1. Users join queue: `ZADD queue:{event_id} {timestamp_ms} {user_id}` — score = join time ensures FIFO
2. Every 10 seconds, admission worker: `ZPOPMIN queue:{event_id} 1000` — admit 1000 users
3. Each admitted user gets a **queue token** (Redis key, 5-min TTL) granting access to the booking page
4. Token validated before allowing seat selection
5. If user doesn't book within 5 min, token expires, next batch gets those slots

**Fairness**: FIFO by timestamp. Refreshing the page doesn't change position (ZADD is idempotent for same user_id). Position displayed via `ZRANK`.

**Estimated wait**: `position / batch_size * batch_interval`. Position 234,567 at 1000/10s = ~39 minutes.

**Talking Point**: "The queue decouples demand from capacity — we admit exactly as many users as the system can handle. 500K users waiting in queue, but only 1000 active at any time. No server crush, fair FIFO ordering."

---

### Deep Dive F: End-to-End Encryption (Signal Protocol)

**Applies to**: WhatsApp, Signal, any messaging system with privacy requirements

**The Problem**: Server must relay messages but should not be able to read them. Even if the server is compromised, past messages must remain secure.

**The Solution**: Signal Protocol with X3DH key exchange + Double Ratchet.

**How It Works**:
1. **Key types**: Identity key (long-term), Signed pre-key (1 week), One-time pre-keys (use once, delete)
2. **First message**: Alice fetches Bob's public keys from server → X3DH handshake derives shared secret → AES-256-GCM encrypts message → sends encrypted blob + handshake data
3. **Bob decrypts**: Uses own private keys + Alice's handshake data → derives same shared secret → decrypts
4. **Subsequent messages**: Use session key, rotate every ~1000 messages (Double Ratchet)
5. **Perfect forward secrecy**: Old session keys deleted → compromising current key doesn't expose past messages

**What Server Sees**: Encrypted blobs, sender/receiver IDs, timestamps. Cannot read content.

**Tradeoffs**:
- ✅ Privacy guarantee (server can't read messages)
- ❌ No server-side search (can't index encrypted content)
- ❌ No server-side content moderation
- ❌ Multi-device sync requires key distribution to each device
- ❌ Message backup/recovery is complex (keys stored only on device)

**Talking Point**: "The server only sees encrypted blobs — even with a court order, we can't produce message content. The tradeoff is that we can't do server-side search or content moderation. For a messaging product, the privacy guarantee is worth these limitations."

---

### Deep Dive G: WebSocket at Scale (Millions of Connections)

**Applies to**: Chat (WhatsApp), real-time notifications, live dashboards, collaborative editing

**The Problem**: Maintain 500M concurrent persistent connections. Traditional thread-per-connection doesn't scale.

**The Solution**: Event-driven I/O (epoll/kqueue) with gateway servers + Redis Pub/Sub for cross-server routing.

**Architecture**:
1. **Gateway servers** (16K servers, each handles 65K connections via event loop)
2. **Connection registry** in Redis: `session:{user_id}:{device_id}` → `{gateway_server, connection_id, last_heartbeat}` with 70s TTL
3. **Heartbeat**: Client sends PING every 30s → server refreshes Redis TTL → if no heartbeat for 70s, key expires → user marked offline
4. **Message routing**: To send to user X, lookup their gateway server in Redis → publish to that gateway's Redis Pub/Sub channel → gateway pushes to WebSocket

**Scaling Pattern**:
- Horizontal: add more gateway servers, LB distributes new connections
- Vertical: tune epoll, increase file descriptor limits, optimize event loop
- Cross-server: Redis Pub/Sub (or Kafka) connects gateways

**Presence**:
- `online:{user_id}` Redis key with 70s TTL, refreshed by heartbeats
- When key expires → user is offline → set `last_seen:{user_id}` timestamp
- Broadcast presence changes to contacts via their gateway servers

**Talking Point**: "Each gateway server handles 65K connections using non-blocking I/O. Redis tracks which user is on which server. To deliver a message, we look up the target's gateway in Redis and publish via Pub/Sub. Adding servers scales linearly — no single bottleneck."

---

### Deep Dive H: Exactly-Once Event Processing (Dedup at Scale)

**Applies to**: Ad click counting, payment processing, any financial/billing pipeline

**The Problem**: At 2.3M events/sec, even 0.1% duplication = 2,300 false clicks/second = millions in overbilling. Producer retries and network glitches cause duplicates.

**The Solution**: Two-tier deduplication — Bloom filter (fast) + Redis SETNX (exact).

**How It Works**:
1. Event arrives with unique `event_id`
2. **Bloom filter check** (< 1 microsecond, in-memory): `mightContain(event_id)`?
   - **Not in Bloom** (99.99% of events): Add to Bloom → process event → done
   - **In Bloom** (0.01%): Could be duplicate or false positive → go to tier 2
3. **Redis SETNX check** (< 1ms): `SET dedup:{event_id} 1 NX EX 3600`
   - **Set succeeded** (false positive): Process event
   - **Set failed** (true duplicate): Drop event

**Bloom Filter Details**:
- Size: ~1 GB for 10B expected elements at 0.01% false positive rate
- Rotate hourly: keep current + previous bloom (covers events near rotation boundary)
- Zero false negatives: if Bloom says "not seen", it's definitely new

**End-to-End Exactly-Once** (full pipeline):
1. Dedup layer (Bloom + Redis) prevents duplicate ingestion
2. Flink checkpointing (every 30s) snapshots state to S3
3. Kafka transactions commit offsets atomically with state
4. On crash: restore from checkpoint → replay from Kafka offset → dedup layer prevents double-counting

**Talking Point**: "Bloom filter handles 99.99% of events in < 1 microsecond with zero false negatives. The 0.01% that hit Redis are verified exactly. Total memory: ~1GB for the Bloom + ~10GB for Redis dedup keys. This gives us 100% accuracy at billions of events per day."

---

### Deep Dive I: Stream Processing with Multi-Window Aggregation

**Applies to**: Ad click aggregator, metrics/monitoring, trending topics, real-time analytics

**The Problem**: Aggregate events at multiple time granularities simultaneously (minute, hour, day) and serve each from a different storage tier.

**The Solution**: Apache Flink with multi-window aggregation, each flushing to a different sink.

**How It Works**:
1. Each event updates three in-memory windows: current minute, current hour, current day
2. **Every 10 seconds**: flush partial minute aggregates to Redis (real-time freshness)
3. **Every minute**: complete minute window → flush to Redis (final)
4. **Every hour**: complete hour window → flush to ClickHouse (warm storage)
5. **Every day**: complete day window → flush to ClickHouse (cold storage)

**Late Events**: Events arriving after window closes:
- Within 5 min: apply delta correction to the already-flushed aggregate
- Within 1 hour: flag and apply correction
- Beyond 1 hour: dead-letter for manual review

**Watermarks**: Track event-time progress across the stream. When watermark passes window end, the window is considered complete. Watermark = max(event_times) - allowed_lateness.

**Query Routing**: Query service determines which tier to read from:
- Minute granularity, last 24h → Redis
- Hour granularity, last 30 days → ClickHouse (SSD)
- Day granularity, last 2 years → ClickHouse (HDD)
- Queries spanning tier boundaries → read from both → merge

**Talking Point**: "A single Flink pipeline produces minute, hour, and day aggregates simultaneously. Each window type flushes to a different storage tier. The query router transparently merges across tiers — the advertiser just sees 'last 7 days, hourly' without knowing it hits Redis for today and ClickHouse for older data."

---

### Deep Dive J: Trending Topics / Top-K Calculation

**Applies to**: Twitter trending, hashtag ranking, popular products, leaderboards

**The Problem**: Calculate the "trending" items in real-time. Not just the most popular (that's always the same), but the items gaining popularity fastest relative to their historical baseline.

**The Solution**: Sliding window counting + velocity scoring + diversity factor.

**How It Works**:
1. **Count**: For each hashtag, maintain a sliding window counter (1-hour window) using Redis
2. **Historical baseline**: Average count per hour over the last 7 days (stored in analytics DB)
3. **Trend score**: `Score = (current_volume / historical_average) × diversity_factor × time_decay`
   - Volume ratio: how much above normal
   - Diversity: how many unique users (prevents bot manipulation)
   - Time decay: favor recent bursts
4. **Update**: Every 5 minutes, recalculate scores → `ZADD trending:global {score} {hashtag}` → keep top 50

**Approximate Top-K** alternatives:
- **Count-Min Sketch**: Probabilistic frequency counter (sub-linear memory)
- **Heavy Hitters (Misra-Gries)**: Track frequent items with bounded memory
- **Redis Sorted Set**: Exact but more memory. Best for top-50 global trends.

**Why Not Just "Most Mentioned"?**: "Weather" is always mentioned. Trending = unusual spike relative to baseline. A hashtag with 1000 mentions that normally gets 100 is more "trending" than one with 10,000 that normally gets 9,000.

**Talking Point**: "The trend score divides current volume by the 7-day average — a hashtag needs to be significantly above its baseline to trend. The diversity factor prevents bot manipulation by requiring many unique users. Updated every 5 minutes, served from Redis in < 10ms."

---

### Deep Dive K: Saga Pattern for Distributed Transactions

**Applies to**: Payment flows, booking flows, any multi-service operation

**The Problem**: A booking involves multiple services (reserve seat → charge payment → issue ticket). If payment fails after seat is reserved, we need to roll back. Traditional 2PC is slow and brittle across microservices.

**The Solution**: Saga — a sequence of local transactions with compensating actions.

**How It Works**:
1. **Step 1**: Reserve seats (compensate: release seats)
2. **Step 2**: Charge payment (compensate: refund payment)
3. **Step 3**: Confirm booking (compensate: cancel booking)
4. **Step 4**: Issue tickets (compensate: void tickets)
5. If any step fails: execute compensating actions in **reverse order** (LIFO)

**Orchestration vs Choreography**:
- **Orchestration** (central coordinator): One service drives the flow, calls each step, handles rollback. Easier to understand and debug. Used when flow is complex.
- **Choreography** (event-driven): Each service listens for events and triggers the next step. More decoupled but harder to trace.

**Idempotency**: Every step + compensating action must be idempotent. Payment charge uses Stripe's idempotency key. DB updates use optimistic version checks. Redis uses SETNX for dedup.

**Talking Point**: "I'd use an orchestrated saga — the Reservation Service drives the flow: reserve → charge → confirm → issue. If payment fails at step 2, it calls the step 1 compensation (release seats). Every step is idempotent — if the saga coordinator crashes and retries, nothing is double-charged or double-booked."

---

### Deep Dive L: Consistent Hashing for Cache Distribution

**Applies to**: Distributed cache (Redis, Memcached), any sharded system

**The Problem**: With `hash(key) % N` sharding, adding/removing a node moves ~100% of keys. During rebalancing, cache hit rate drops to near zero.

**The Solution**: Consistent hashing ring with virtual nodes.

**How It Works**:
1. Hash both keys and server IDs onto a ring (0 to 2^32)
2. Each key maps to the first server found clockwise on the ring
3. Adding a server: only keys between the new server and its predecessor move (~1/N of total)
4. Removing a server: only its keys move to the next server clockwise

**Virtual Nodes**: Each physical server gets 100-200 positions on the ring. This ensures even distribution (without virtual nodes, random placement leads to unbalanced load).

**Real-World Usage**: Redis Cluster uses hash slots (16384 slots distributed across nodes). Cassandra uses consistent hashing for partition placement. DynamoDB uses it internally.

**Talking Point**: "With consistent hashing, adding a node to a 10-node cluster only remaps ~10% of keys, instead of ~100% with modular hashing. Virtual nodes (150 per server) ensure even load distribution. This means we can scale the cache tier without a 'cold cache thundering herd' on the database."

---

### Deep Dive M: Backpressure & Load Shedding Under Spike

**Applies to**: Ad ingestion, event processing, any high-throughput pipeline

**The Problem**: During a flash event (Super Bowl, Black Friday), traffic spikes 5-10x. If consumers can't keep up, Kafka lag grows, latency degrades, workers OOM.

**The Solution**: Priority-based load shedding + auto-scaling + sampling.

**Levels**:
1. **Normal** (lag < 500K): Process everything
2. **Elevated** (lag 500K-1M): Auto-scale consumers (add Flink TaskManagers)
3. **Sampling** (lag 1M-10M): Sample low-priority events at 10%, apply 10x correction factor. High-priority events (clicks = revenue) always processed.
4. **Critical** (lag > 10M): Only process high-value events (clicks + high-bid impressions). Drop low-value impressions entirely.

**Why Prioritize Clicks Over Impressions?**: Clicks directly represent revenue (advertiser is billed per click). Impression counts can be approximate (± 10% is acceptable for dashboard display). Incorrect click counts = financial dispute.

**Auto-Scaling**: Kubernetes HPA watches Kafka consumer lag metric. Scale up when lag > 500K, stabilize for 60s before scaling again. Max 500 TaskManagers.

**Talking Point**: "Under backpressure, clicks are always processed — they represent revenue. Impressions are sampled at 10% with a correction factor. This preserves billing accuracy while preventing the pipeline from falling behind. Once traffic normalizes, we resume full processing."

---

### Deep Dive N: Database Sharding Strategy & Cross-Shard Queries

**Applies to**: Any system at scale needing horizontal DB scaling

**The Problem**: Single database can't handle the write throughput or data volume. Need to split across multiple DB instances.

**Key Decisions**:
1. **Shard key choice**: Must ensure even distribution AND keep related data together
2. **Cross-shard queries**: Must be avoided or explicitly handled

**Common Patterns**:
- **Ticket booking**: Shard by `event_id` → all seats + reservations for one event on same shard → reservation transaction never crosses shards
- **Chat**: Shard by `conversation_id` → all messages in a conversation on same shard → message ordering guaranteed
- **Social network**: Shard by `user_id` → user's profile + posts on same shard → cross-user queries (timeline) handled via denormalization (pre-computed timelines in Redis)
- **Analytics**: Shard by `campaign_id` → all metrics for one campaign on same shard → dashboard queries don't cross shards

**Cross-Shard Handling**:
- Best: design data model so common queries never cross shards
- If needed: scatter-gather pattern (query all shards in parallel, merge results)
- For joins: denormalize data or maintain a separate read-optimized view

**Resharding**: Painful with modular hashing (all data moves). Use consistent hashing or range-based sharding for easier migration. Or use a database that handles it (CockroachDB, Vitess, Citus).

**Talking Point**: "I'd shard by event_id — all seats, reservations, and tickets for one event live on the same shard. This means the entire reservation transaction (lock seats → create reservation → update status) executes within a single shard. No distributed transactions needed."

---

### Deep Dive O: CDN & Media Processing Pipeline

**Applies to**: Instagram, Netflix, any media-heavy system

**The Problem**: Billions of images/videos served globally. Can't serve from origin for every request.

**The Pipeline**:
1. Client requests **presigned upload URL** from backend (< 100ms)
2. Client uploads directly to S3 (bypasses backend — no bottleneck)
3. S3 event triggers async processing (Lambda or worker queue):
   - **Images**: generate thumbnails (small, medium, large), compress, strip EXIF metadata
   - **Videos**: transcode to multiple resolutions (360p, 720p, 1080p), generate HLS/DASH segments, extract poster frame
4. Store all processed versions back to S3
5. CDN (CloudFront) serves media from edge locations (pull-through caching)

**CDN Cache Strategy**:
- Pull-through: first request fetches from S3, caches at edge
- 95%+ cache hit ratio for popular content
- TTL: 1 year for media (content-addressable URLs — `image_{hash}.jpg`)
- Versioned URLs eliminate need for cache invalidation

**Adaptive Bitrate (Video)**: Client starts with low quality, measures bandwidth, switches to higher quality. HLS manifest file lists all available qualities. Player picks the best one per network condition.

**Talking Point**: "Users upload directly to S3 via presigned URL — our backend never touches the bytes. Async workers generate thumbnails and transcode videos. The CDN serves everything from edge locations with a 95% cache hit rate. This means even 1 billion daily image views only generate ~50 million origin fetches."

---

### Deep Dive P: Distributed Job Scheduling & At-Least-Once Execution

**Applies to**: Job scheduler, cron at scale, workflow orchestration

**The Problem**: Need to run millions of scheduled jobs reliably. Jobs must execute at least once, even if a worker crashes mid-execution. No single point of failure.

**Architecture**:
1. **Scheduler Leader** (elected via ZooKeeper/etcd): scans due jobs, assigns to workers
2. **Workers** (stateless, pull from queue): execute jobs, report completion
3. **Job Queue** (Redis sorted set or Kafka): `ZADD jobs:{partition} {execution_time} {job_id}` — score = scheduled time
4. **Heartbeat**: Worker sends heartbeat every 10s. If no heartbeat for 30s, job is reassigned to another worker.

**At-Least-Once Guarantee**:
- Worker picks up job → starts execution → sends heartbeats
- If worker crashes: heartbeat stops → scheduler reassigns job to new worker
- Job may execute twice (once on crashed worker, once on new worker) → jobs must be **idempotent**
- After completion: worker marks job as DONE with a version check (prevents stale completion from crashed worker)

**Idempotency for Jobs**: Each job execution has a unique `execution_id`. Worker writes results with `IF NOT EXISTS` or version check. Duplicate execution produces same result.

**Visibility Timeout Pattern** (SQS-style):
- Job becomes "invisible" when picked up (hidden for 5 min)
- If worker completes → delete from queue
- If worker crashes → job becomes visible again after timeout → another worker picks it up

**Talking Point**: "The scheduler leader scans for due jobs using a Redis sorted set — `ZRANGEBYSCORE jobs 0 {now}` returns all jobs ready to run. Workers pull jobs, execute with heartbeats, and report completion. If a worker dies, the heartbeat timeout triggers reassignment. Jobs must be idempotent because at-least-once means they may run twice."

---

### Deep Dive Q: Bloom Filter for URL/Event Deduplication

**Applies to**: Web crawler, ad click dedup, duplicate detection in streams

**The Problem**: Need to check "have I seen this URL/event before?" across billions of items. Can't store all items in memory. Can't afford a DB lookup per item.

**The Solution**: Bloom filter — probabilistic data structure with zero false negatives.

**Properties**:
- **No false negatives**: If Bloom says "not seen" → definitely new (100% accurate)
- **Possible false positives**: If Bloom says "maybe seen" → might be new (0.01-1% false positive rate, tunable)
- **Fixed memory**: ~1 GB for 10 billion items at 0.01% FP rate
- **O(1) operations**: Hash and check k bits. No iteration.

**Parameters**: For `n` expected items and `p` desired false positive rate:
- Bit array size `m = -n × ln(p) / (ln2)²`
- Number of hash functions `k = (m/n) × ln2`
- Example: 1B items, 0.1% FP → m ≈ 1.44 GB, k ≈ 10

**Rotation** (for streaming): Use two bloom filters — current and previous. Rotate every hour. Check both. This handles items near the rotation boundary.

**In Web Crawler**: Before fetching a URL, check Bloom filter. If "not seen" → fetch + add to Bloom. If "maybe seen" → skip (accept rare false positive = skip a URL we haven't seen).

**Talking Point**: "The Bloom filter lets us check 'have I crawled this URL before?' in < 1 microsecond with zero false negatives. At 0.1% false positive rate, we skip 1 in 1000 new URLs — acceptable for a crawler. Total memory: ~1.5 GB for 10 billion URLs."

---

### Deep Dive R: Reconciliation — Ensuring Stream Accuracy

**Applies to**: Ad click counting, financial systems, inventory, any system where accuracy matters

**The Problem**: Stream processing may drift from truth due to late events, sampling under backpressure, or edge cases. For billing/financial data, drift is unacceptable.

**The Solution**: Lambda-style batch reconciliation.

**How It Works**:
1. **Stream processing** provides real-time results (< 30s freshness)
2. **Daily batch job** (Spark) reads ALL raw events from S3, recomputes exact aggregates
3. **Compare** stream aggregates (in ClickHouse) vs batch aggregates
4. **Correct** any discrepancy > 0.1%:
   - If stream count > exact count: streaming counted duplicates or applied corrections wrong
   - If stream count < exact count: streaming missed late events
5. Overwrite ClickHouse daily table with exact counts
6. Log corrections for monitoring

**Schedule**: Run at 3 AM UTC for previous day. Expected: 99.99% of dimensions match exactly. ~0.01% need small corrections.

**Monitoring**: Alert if correction rate exceeds threshold (e.g., > 0.5% of dimensions corrected = pipeline issue).

**Talking Point**: "The stream gives us speed (< 30s freshness for dashboards), the batch gives us truth (exact counts from raw events). The reconciliation job runs nightly and catches any drift — typically < 0.01% of dimensions need correction. For advertiser billing, we use the batch-reconciled numbers."

---

### Deep Dive S: Anomaly Detection on Real-Time Metrics

**Applies to**: Ad analytics, monitoring systems, fraud detection, alerting

**The Problem**: Need to detect unusual patterns (CTR drop, spend spike, traffic surge) in real-time without manual threshold setting.

**The Solution**: Z-score based anomaly detection on rolling statistics.

**How It Works**:
1. Maintain rolling mean and standard deviation per metric per entity (e.g., CTR per campaign) using Welford's online algorithm
2. For each new data point, compute Z-score: `z = (current - mean) / stddev`
3. If `|z| > 3` (3 standard deviations): flag as anomaly
4. Different thresholds for different severities: z > 3 = WARNING, z > 5 = CRITICAL

**Types of Anomalies**:
| Anomaly | Detection | Action |
|---------|-----------|--------|
| CTR Drop | Z-score < -3 | Alert advertiser (broken ad creative?) |
| Spend Spike | Rate > 3x baseline | Alert, may auto-pause campaign |
| Zero Impressions | Current = 0, baseline > 100 | Check ad serving pipeline |
| Impression Surge + CTR Collapse | 5x impressions, 0.1x CTR | Flag for fraud team review |
| Budget Exhaustion | Spend > 90% of budget | Alert, optionally auto-pause |

**Welford's Online Algorithm**: Computes mean and standard deviation incrementally without storing all data points. Memory: O(1) per entity.

**Talking Point**: "Each minute-level aggregate is fed into a per-campaign rolling stats tracker. If the Z-score exceeds 3 standard deviations, we fire an alert. This catches CTR drops, spend spikes, and potential fraud — all in real-time with zero manual thresholds."

---

### Deep Dive T: Group Message Fanout (Chat Systems)

**Applies to**: WhatsApp groups, Slack channels, Discord servers

**The Problem**: A user sends a message to a group with 256 members. Sequential delivery (256 × 50ms = 12.8s) is too slow.

**The Solution**: Parallel fanout with online/offline classification.

**How It Works**:
1. Store message **once** in Cassandra (partition by group_id)
2. Fetch group members from Redis cache (`SMEMBERS group:{id}:members`)
3. Classify: check `online:{user_id}` keys → split into online vs offline
4. **Online** (parallel): lookup each member's WebSocket gateway → push via Redis Pub/Sub → mark DELIVERED
5. **Offline** (parallel): add to `undelivered:{user_id}` list + batch push notification (100 per FCM API call)
6. Track per-member delivery receipts in Cassandra

**Total fanout**: ~100-150ms for 256 members with parallel delivery.

**Talking Point**: "The message is stored once — fanout is just notification routing. Online members get it via WebSocket in ~30ms. Offline members get a push notification and receive the message when they reconnect. Total fanout for 256 members: ~100ms."

---

## 🎯 Deep Dive Quick Reference

| Deep Dive | Applicable Problems |
|-----------|-------------------|
| **A. Hybrid Fan-Out** | Twitter, Instagram, any social feed |
| **B. Distributed Rate Limiting** | Any API, rate limiter design |
| **C. Snowflake ID** | Twitter, chat, any distributed ID need |
| **D. Preventing Double-Selling** | Ticket booking, hotel, flight, flash sale |
| **E. Virtual Queue** | Ticket booking, sneaker drops, GPU launches |
| **F. End-to-End Encryption** | WhatsApp, Signal, secure messaging |
| **G. WebSocket at Scale** | Chat, notifications, live dashboards |
| **H. Exactly-Once Dedup** | Ad clicks, payments, billing pipelines |
| **I. Multi-Window Aggregation** | Ad analytics, metrics, monitoring |
| **J. Trending / Top-K** | Twitter trending, popular products, leaderboards |
| **K. Saga Pattern** | Payment flows, multi-service booking |
| **L. Consistent Hashing** | Distributed cache, any sharded system |
| **M. Backpressure & Load Shedding** | Event processing, real-time pipelines |
| **N. DB Sharding Strategy** | Any system needing horizontal scaling |
| **O. CDN & Media Pipeline** | Instagram, Netflix, YouTube |
| **P. Job Scheduling** | Cron at scale, workflow orchestration |
| **Q. Bloom Filter Dedup** | Web crawler, event dedup, URL tracking |
| **R. Reconciliation** | Ad billing, financial systems, inventory |
| **S. Anomaly Detection** | Ad analytics, fraud detection, monitoring |
| **T. Group Message Fanout** | WhatsApp groups, Slack, Discord |

---

**Document Version**: 2.0  
**Last Updated**: February 2026  
**Coverage**: 35 tradeoff categories + 20 reusable deep dive topics + 15 problem mappings  
**Status**: Complete & Interview-Ready ✅
