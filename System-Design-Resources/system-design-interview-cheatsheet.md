# System Design Interview Cheatsheet
## Practical Decision-Making Guide for Interviews

A practical, actionable guide for making smart technology choices during system design interviews. Use this to confidently justify your design decisions.

---

## Table of Contents
1. [Database Selection Guide](#database-selection-guide)
2. [Caching Strategy Guide](#caching-strategy-guide)
3. [Load Balancing Strategies](#load-balancing-strategies)
4. [Communication Protocols](#communication-protocols)
5. [Scaling Strategies](#scaling-strategies)
6. [Message Queue Selection](#message-queue-selection)
7. [Storage Solutions](#storage-solutions)
8. [API Design Choices](#api-design-choices)
9. [Consistency Models](#consistency-models)
10. [Common Architecture Patterns](#common-architecture-patterns)
11. [Interview Response Templates](#interview-response-templates)

---

## Database Selection Guide

### Decision Tree

```
Is data relational with complex queries?
├─ YES → Consider SQL
│  ├─ Need ACID transactions? → PostgreSQL (best features) or MySQL (simplicity)
│  ├─ Analytics workload? → Redshift, BigQuery
│  └─ Need global distribution? → CockroachDB, Spanner
│
└─ NO → Consider NoSQL
   ├─ Key-Value lookups? 
   │  ├─ In-memory speed needed? → Redis (cache + persistence)
   │  └─ Persistent store? → DynamoDB, Riak
   │
   ├─ Document storage?
   │  ├─ Flexible schema? → MongoDB (most popular)
   │  └─ Mobile/offline sync? → Couchbase
   │
   ├─ Time-series data?
   │  ├─ High write throughput? → Cassandra (Twitter, Netflix use)
   │  └─ IoT/metrics? → InfluxDB, TimescaleDB
   │
   ├─ Graph relationships?
   │  ├─ Social network? → Neo4j (best for traversal)
   │  └─ Fraud detection? → Amazon Neptune
   │
   └─ Search/full-text?
       └─ → Elasticsearch (de facto standard)
```

### Quick Recommendations by Use Case

| Use Case | Best Choice | Why | Interview Talking Point |
|----------|-------------|-----|------------------------|
| **User profiles, accounts** | PostgreSQL | ACID, relations, mature | "Need strong consistency for user data, PostgreSQL gives us ACID with great performance" |
| **Shopping cart** | Redis | Fast, session data | "Redis for session storage - in-memory speed with persistence backup" |
| **Social feed/timeline** | Cassandra | Write-heavy, time-series | "Cassandra for timeline - handles high write throughput, used by Twitter and Instagram" |
| **Product catalog** | MongoDB | Flexible schema, documents | "MongoDB for products - flexible schema for varying product attributes" |
| **Analytics/reporting** | Redshift/BigQuery | OLAP optimized | "Redshift for analytics - columnar storage optimized for aggregations" |
| **Social graph** | Neo4j | Graph traversal | "Neo4j for friend relationships - native graph DB, O(1) traversals" |
| **Search functionality** | Elasticsearch | Full-text search | "Elasticsearch for search - inverted index, relevance ranking out of box" |
| **Chat messages** | Cassandra/MongoDB | High writes, documents | "Cassandra for messages - time-series data, linear scalability" |
| **Gaming leaderboard** | Redis Sorted Sets | Real-time ranking | "Redis sorted sets - O(log N) updates, perfect for leaderboards" |
| **Financial transactions** | PostgreSQL | ACID critical | "PostgreSQL with strict ACID - can't compromise on transaction consistency" |

### Interview Power Phrases

**When choosing SQL:**
> "I'm going with PostgreSQL because we need ACID guarantees for [user accounts/payments]. The complex JOINs for [feature] also benefit from relational structure. We can add read replicas for scaling reads and partition by [key] if needed."

**When choosing NoSQL:**
> "I'm choosing Cassandra because [writes/timeline] is write-heavy at [scale]. It gives us linear scalability and no single point of failure. Instagram and Netflix use it for similar use cases. We sacrifice strong consistency but eventual consistency works here because [reason]."

**When choosing both (Polyglot Persistence):**
> "I'm using PostgreSQL for transactional data and Cassandra for activity feeds. This polyglot approach lets us optimize each data access pattern. We'll use CDC (Change Data Capture) or event streaming to keep them in sync."

---

## Caching Strategy Guide

### When to Cache

```
Cache if:
✅ Read-heavy workload (read:write ratio > 10:1)
✅ Data queried frequently (hot data)
✅ Acceptable to serve stale data for short period
✅ Expensive computation or query
✅ External API calls

Don't cache if:
❌ Write-heavy workload
❌ Real-time strong consistency required
❌ Data changes very frequently
❌ Query is already fast (< 10ms)
```

### Cache Patterns

| Pattern | When to Use | Pros | Cons | Interview Use |
|---------|------------|------|------|---------------|
| **Cache-Aside (Lazy Loading)** | Most common, read-heavy | Simple, only cache what's needed | Cache miss penalty, stale data risk | "Most flexible pattern for [feature]" |
| **Write-Through** | Data must always be fresh | No stale data | Higher write latency | "For [critical data] that must be consistent" |
| **Write-Behind** | Write-heavy, eventual consistency OK | Fast writes, batch DB writes | Data loss risk, complexity | "For [logs/metrics] where speed > consistency" |
| **Refresh-Ahead** | Predictable access pattern | Always warm cache | Wasted resources if prediction wrong | "For [homepage/trending] with known access" |

### Cache Eviction Policies

| Policy | Best For | Interview Justification |
|--------|----------|------------------------|
| **LRU (Least Recently Used)** | General purpose, most common | "LRU is the safe default - removes cold data automatically" |
| **LFU (Least Frequently Used)** | Known popular items | "LFU for trending content - keeps popular items longer" |
| **TTL (Time to Live)** | Time-sensitive data | "TTL for session data - automatic expiration after timeout" |
| **FIFO** | Simple caching | "FIFO is simpler but less effective than LRU" |

### Multi-Level Caching Strategy

```
Interview Response:
"I'll implement a three-tier caching strategy:

L1 - Browser Cache (5 min TTL)
└─ Static assets, user preferences
   Benefits: Zero latency, reduces server load

L2 - CDN Edge Cache (24 hours TTL)  
└─ Images, videos, static content
   Benefits: Geographic distribution, ~50ms latency

L3 - Application Cache - Redis (1 hour TTL)
└─ API responses, database query results  
   Benefits: Shared cache, ~5ms latency, 90% hit rate target

This gives us 95%+ cache hit ratio and < 100ms p99 latency."
```

### Cache Sizing Formula

```
Interview Calculation:
"Let me calculate cache size:

100M DAU × 20% active (hot users) = 20M users
Average cached data per user = 50KB (timeline, profile)
Total: 20M × 50KB = 1TB

I'd provision 1.5TB Redis cluster (50% buffer) distributed across 10 nodes
= 150GB per node, well within RAM limits."
```

### Redis vs Memcached Decision

| Factor | Redis | Memcached | Interview Tip |
|--------|-------|-----------|---------------|
| **Data structures** | Strings, Lists, Sets, Sorted Sets, Hashes | Only strings | "Redis for sorted sets (leaderboards)" |
| **Persistence** | Yes (RDB + AOF) | No | "Redis if we need cache persistence across restarts" |
| **Replication** | Master-slave | No | "Redis for high availability with replicas" |
| **Performance** | Slightly slower | Faster for simple K-V | "Memcached if pure cache, no persistence needed" |
| **Memory** | More features, more memory | More efficient | "Memcached for simple session storage at scale" |

**Interview Power Phrase:**
> "I'm choosing Redis because we need sorted sets for leaderboards and persistence for session recovery. The small performance trade-off vs Memcached is worth the features. We'll use Redis Cluster for horizontal scaling."

---

## Load Balancing Strategies

### Algorithm Selection

| Algorithm | Best For | Interview Justification |
|-----------|----------|------------------------|
| **Round Robin** | Equal server capacity | "Simplest, fair distribution for homogeneous servers" |
| **Weighted Round Robin** | Unequal server capacity | "New servers get 20% traffic, old get 80% during transition" |
| **Least Connections** | Long-lived connections (WebSocket) | "For chat servers - routes to server with fewest active connections" |
| **IP Hash** | Session affinity needed | "Sticky sessions for stateful apps - same user → same server" |
| **Random** | Stateless, large fleet | "Random with large fleet distributes well, simpler than round-robin" |
| **Geolocation** | Global users | "Route US users to us-east, EU to eu-west for low latency" |

### Load Balancer Layers

**Layer 4 (TCP/UDP) vs Layer 7 (HTTP/HTTPS)**

| Feature | Layer 4 | Layer 7 | Interview Tip |
|---------|---------|---------|---------------|
| **Speed** | Faster (no payload inspection) | Slower | "L4 for raw throughput (10M+ QPS)" |
| **Features** | Basic routing | Content-based routing, SSL termination | "L7 for microservices - route /api/users to user service" |
| **Cost** | Cheaper | More expensive | "Start with L7 (ALB), move to L4 if needed" |

**Interview Power Phrase:**
> "I'll use AWS ALB (Layer 7) for intelligent routing - /api/users → User Service, /api/posts → Post Service. It handles SSL termination and health checks. If we hit 1M+ QPS, we can add NLB (Layer 4) in front for TLS passthrough."

---

## Communication Protocols

### Protocol Decision Tree

```
Real-time bi-directional communication needed?
├─ YES → WebSocket
│  └─ "For chat, live feed, collaborative editing"
│
└─ NO → 
   ├─ Server needs to push updates?
   │  ├─ Browser only? → Server-Sent Events (SSE)
   │  └─ Mobile too? → Long Polling or Push Notifications
   │
   ├─ Microservice-to-microservice?
   │  ├─ High performance, low latency? → gRPC
   │  └─ Simple, human-readable? → REST
   │
   └─ External API (third-party)?
       └─ → REST (most compatible)
```

### Quick Comparison

| Protocol | Latency | Use Case | Interview Line |
|----------|---------|----------|----------------|
| **REST** | ~50-100ms | Standard CRUD APIs | "REST for public API - widely understood, easy to debug" |
| **gRPC** | ~10-20ms | Microservices | "gRPC between services - 5x faster than REST, bi-directional streaming" |
| **WebSocket** | ~5-10ms | Real-time updates | "WebSocket for chat - persistent connection, instant delivery" |
| **GraphQL** | ~50-100ms | Flexible querying | "GraphQL for mobile - one request gets exact data needed" |
| **Server-Sent Events** | ~10-20ms | Server push (one-way) | "SSE for notifications - simpler than WebSocket, auto-reconnect" |

**Interview Power Phrases:**

**For REST:**
> "REST for external API - industry standard, great tooling. We'll version it (v1, v2) and use standard HTTP methods. Rate limiting with 429 responses."

**For gRPC:**
> "gRPC between microservices - Protocol Buffers are compact, HTTP/2 multiplexing handles high throughput. Google, Netflix use it internally."

**For WebSocket:**
> "WebSocket for real-time chat - maintains persistent connection, sub-10ms latency. We'll use Socket.io for automatic fallback to polling."

---

## Scaling Strategies

### Horizontal vs Vertical Scaling

| Approach | When to Use | Limits | Cost | Interview Tip |
|----------|-------------|--------|------|---------------|
| **Vertical (Scale Up)** | Quick fix, monolith | Single machine limits (~96 cores, 768GB) | Expensive (non-linear) | "Start here for MVP, cheaper initially" |
| **Horizontal (Scale Out)** | True scalability | No limit | Linear cost | "Production choice - add servers as needed" |

### Scalability Decision Framework

**Always Start With:**
1. **Identify bottleneck** - Profile first, optimize later
2. **Database optimization** - Indexes, query optimization (often 10x gain)
3. **Caching** - Redis/CDN (often 100x read reduction)
4. **Then scale** - Horizontal scaling after optimization

### Database Scaling Ladder

```
Interview Response:
"Here's my scaling progression:

Level 1: Single Database (0-10K QPS)
└─ Add indexes, optimize queries
   "Handles most apps"

Level 2: Add Read Replicas (10K-50K QPS)
├─ Route reads to replicas (5x capacity)
└─ Master for writes only
   "Instagram started here"

Level 3: Caching Layer (50K-500K QPS)
├─ Redis for 90% cache hit rate
└─ DB only for cache misses
   "Reduces DB load 10x"

Level 4: Sharding (500K+ QPS)
├─ Partition by user_id or region
└─ Multiple database clusters
   "Horizontal scaling, no limit"

Level 5: Multi-Region (Global Scale)
└─ Replicate across continents
   "Sub-100ms latency worldwide"
```

### Sharding Strategy

| Sharding Key | Pros | Cons | Best For |
|--------------|------|------|----------|
| **User ID** | Even distribution | Cross-user queries hard | Social media, user data |
| **Geographic** | Latency benefits | Uneven load | Global apps (Netflix) |
| **Timestamp** | Easy archival | Recent shard hot | Logs, analytics |
| **Hash** | Perfect distribution | Can't do range queries | Key-value workloads |

**Interview Power Phrase:**
> "I'll shard by user_id using consistent hashing. This distributes evenly and keeps user data co-located. We'll use 1024 virtual shards across 10 physical databases, making it easy to add nodes without massive rebalancing."

---

## Message Queue Selection

### When to Use Message Queues

```
Use message queue if:
✅ Async processing acceptable
✅ Need to decouple services
✅ Traffic spikes (load leveling)
✅ Retry/dead letter queue needed
✅ Fan-out pattern (1 → many)

Don't use if:
❌ Need immediate response (< 100ms)
❌ Simple synchronous flow
❌ Adding unnecessary complexity
```

### Queue Comparison

| Queue | Best For | Throughput | Ordering | Interview Use |
|-------|----------|------------|----------|---------------|
| **Kafka** | Event streaming, high throughput | 1M+ msg/sec | Partition-level | "For activity feed, analytics pipeline - persistent log" |
| **RabbitMQ** | Traditional messaging | 50K msg/sec | Queue-level | "For background jobs - battle-tested, easy to use" |
| **SQS** | AWS ecosystem, managed | 100K msg/sec | No strict order | "For AWS stack - fully managed, no ops overhead" |
| **Redis Streams** | Lightweight, already use Redis | 100K msg/sec | Stream-level | "If already using Redis - dual purpose" |

**Interview Decision Framework:**

```
"Let me choose the right queue:

Need event log replay? → Kafka
  "Activity feed needs 7-day replay - Kafka perfect"

Need strict message ordering? → RabbitMQ
  "Order processing needs FIFO - RabbitMQ with single consumer"

Using AWS, want managed? → SQS
  "AWS-native, no infrastructure to manage"

Already using Redis? → Redis Streams
  "Leverage existing infra, good enough for [use case]"
```

**Interview Power Phrase:**
> "I'll use Kafka for event streaming - it's the industry standard for real-time data pipelines. LinkedIn, Uber, Netflix all use it. We get durability, replay capability, and 1M+ messages/sec throughput. Events published to topics, microservices consume independently."

---

## Storage Solutions

### Object Storage vs Block Storage vs File Storage

| Type | Use Case | Example | Cost | Interview Tip |
|------|----------|---------|------|---------------|
| **Object Storage** | Static files, media, backups | S3, GCS | Cheapest | "For images/videos - infinite scale, $0.023/GB" |
| **Block Storage** | Database volumes, VM disks | EBS, SSD | Expensive | "For database - high IOPS, low latency" |
| **File Storage** | Shared file system | EFS, NFS | Medium | "For shared configs - multiple servers mount" |

### S3 Storage Classes (Interview Cheat Sheet)

| Class | Use Case | Cost | Retrieval | Interview Line |
|-------|----------|------|-----------|----------------|
| **S3 Standard** | Hot data, frequent access | $0.023/GB | Instant | "For active user uploads - accessed daily" |
| **S3 Intelligent-Tiering** | Unknown access patterns | $0.021/GB + fee | Instant | "Let AWS optimize automatically" |
| **S3 Glacier** | Archival, compliance | $0.004/GB | Minutes-hours | "For old data - 80% cheaper, rarely accessed" |
| **S3 Deep Archive** | Long-term archival | $0.00099/GB | 12+ hours | "7-year retention, accessed once/year" |

**Interview Power Phrase:**
> "I'll use S3 Standard for active uploads with lifecycle policy: after 30 days → Intelligent-Tiering, after 1 year → Glacier. This automatically cuts storage costs 70% without affecting user experience."

### CDN Strategy

```
Interview Response:
"CDN strategy for static assets:

Static Content (images, JS, CSS):
├─ Push to CloudFront immediately after upload
├─ Cache for 30 days (immutable content)
└─ 95% cache hit ratio expected

Dynamic Content (API responses):
├─ Cache at edge for 5 minutes
├─ Use cache keys with query parameters
└─ Reduce origin load by 80%

Benefits:
- Latency: 200ms → 50ms (4x improvement)
- Cost: $0.085/GB → $0.02/GB (CloudFront cheaper than EC2 bandwidth)
- Scale: Handles traffic spikes automatically
```

---

## API Design Choices

### REST vs GraphQL vs gRPC

| Aspect | REST | GraphQL | gRPC | Interview Pick |
|--------|------|---------|------|----------------|
| **Learning curve** | Easy | Medium | Hard | REST for public API |
| **Over-fetching** | Common | Solved | N/A | GraphQL for mobile |
| **Performance** | Good | Good | Excellent | gRPC for microservices |
| **Caching** | Easy (HTTP) | Complex | Custom | REST if caching critical |
| **Tooling** | Excellent | Good | Growing | REST for external |

**Interview Decision:**

```
Public API? → REST
  "Industry standard, every developer knows it"

Mobile app with many screens? → GraphQL
  "One request gets exact data, reduces bandwidth"

Internal microservices? → gRPC
  "5x faster than REST, type-safe"
```

### API Versioning

| Strategy | Example | Pros | Cons | Interview Use |
|----------|---------|------|------|---------------|
| **URI** | /v1/users, /v2/users | Clear, easy to route | URL pollution | "Most common, easy to understand" |
| **Header** | API-Version: 2 | Clean URLs | Less visible | "For internal APIs" |
| **Query param** | /users?version=2 | Flexible | Less standard | "Avoid - not RESTful" |

**Interview Power Phrase:**
> "I'll version via URI (/v1, /v2) - it's explicit and makes routing easy. We'll maintain old versions for 6 months with deprecation warnings, then sunset gracefully."

---

## Consistency Models

### Quick Decision Guide

| Need | Choose | Interview Justification |
|------|--------|------------------------|
| **Bank transactions** | Strong Consistency | "Can't have race conditions on balance - PostgreSQL with ACID" |
| **Social media likes** | Eventual Consistency | "OK if count shows 99 then 100 - Cassandra for scale" |
| **Shopping cart** | Session Consistency | "User sees own updates immediately - sticky sessions" |
| **Collaborative doc** | Causal Consistency | "See your edits instantly, others eventually - CRDT" |
| **DNS updates** | Eventual Consistency | "Can tolerate propagation delay - standard DNS" |

### CAP Theorem in Practice

```
Interview Response:
"Given CAP theorem, I can only pick 2 of 3:

Consistency + Availability (CA):
└─ Not possible in distributed system with partitions
   "Single datacenter only, not realistic"

Consistency + Partition Tolerance (CP):
├─ Examples: MongoDB, HBase, Zookeeper
└─ "Choose for financial data - availability suffers during partition"

Availability + Partition Tolerance (AP):
├─ Examples: Cassandra, DynamoDB, Riak
└─ "Choose for social media - eventual consistency acceptable"

For this system, I pick AP because [availability/scale] matters more than 
immediate consistency for [feature]."
```

---

## Common Architecture Patterns

### Pattern Selection Guide

| Pattern | Use Case | Interview Line |
|---------|----------|----------------|
| **API Gateway** | Microservices entry point | "Single entry point for auth, rate limiting, routing" |
| **Circuit Breaker** | Prevent cascading failures | "If payment service down, fail fast instead of timeout" |
| **CQRS** | Different read/write needs | "Write to PostgreSQL, read from Elasticsearch - optimize each" |
| **Event Sourcing** | Audit trail needed | "Store all events, rebuild state - used by banks" |
| **Saga** | Distributed transactions | "Multi-service transaction with compensating actions" |
| **Bulkhead** | Isolate failures | "Separate thread pools - search failure doesn't affect checkout" |
| **Rate Limiting** | Prevent abuse | "Token bucket - 1000 req/hour per user" |
| **Cache-Aside** | Lazy load cache | "Most common caching pattern - check cache, then DB" |

---

## Interview Response Templates

### Template 1: Database Choice

```
"For [feature], I'm choosing [database] because:

1. Access Pattern: [describe read/write pattern]
   └─ [Database] is optimized for [read-heavy/write-heavy/mixed]

2. Consistency Requirements: [strong/eventual]
   └─ [Database] provides [ACID/BASE]

3. Scale: [expected QPS, data volume]
   └─ [Database] scales [vertically/horizontally] via [method]

4. Real-world use: [Company] uses it for [similar use case]

Trade-offs:
- Pros: [list 2-3]
- Cons: [list 1-2 and mitigation]
"
```

**Example:**
> "For user timelines, I'm choosing Cassandra because:
> 1. Write-heavy: 10K writes/sec, Cassandra excels at writes
> 2. Eventual consistency acceptable - users can wait 1-2 sec for new posts
> 3. Scales linearly - Twitter handles 500M tweets/day with it
> 4. Time-series data model perfect for chronological feeds
> 
> Trade-off: No complex queries, but we don't need JOINs for timelines."

### Template 2: Caching Strategy

```
"I'll implement caching at [layers]:

1. [Layer 1] - Browser (Client-side)
   └─ Cache: [what], TTL: [time], Benefit: [why]

2. [Layer 2] - CDN (Edge)
   └─ Cache: [what], TTL: [time], Benefit: [why]

3. [Layer 3] - Application (Redis)
   └─ Cache: [what], TTL: [time], Benefit: [why]

Invalidation strategy: [method]
Expected cache hit ratio: [X]%
Latency improvement: [Xms → Yms]
"
```

### Template 3: Scaling Approach

```
"Here's my scaling strategy:

Phase 1 (0-100K users):
└─ [Single DB, optimize queries]

Phase 2 (100K-1M users):
└─ [Add read replicas + caching]

Phase 3 (1M-10M users):
└─ [Shard database, CDN, microservices]

Phase 4 (10M+ users):
└─ [Multi-region, advanced optimizations]

For current requirement of [X users], we're in Phase [Y], so I'll implement [specific solution]."
```

### Template 4: Technology Justification

```
"I'm choosing [technology] over [alternative] because:

Strengths:
✅ [Key advantage 1]
✅ [Key advantage 2]
✅ Used by [company] for [similar use case]

Trade-offs:
⚠️ [Limitation] - but acceptable because [reason]
⚠️ [Complexity] - mitigated by [solution]

Alternative [X] would be better if [condition], but given [requirement], [chosen tech] is optimal."
```

---

## Quick Interview Wins

### Impressive Things to Say

1. **Show you understand scale:**
   > "At 100M DAU with 50 requests/day, we're at ~60K QPS average, ~300K peak. We'll need..."

2. **Mention real companies:**
   > "Netflix uses Cassandra for this exact use case. Instagram uses Redis sorted sets for feed ranking."

3. **Explain trade-offs:**
   > "Cassandra gives us horizontal scalability but sacrifices strong consistency. For social feed, that's acceptable."

4. **Use specific numbers:**
   > "Redis gives us ~1ms latency vs 50ms for database. With 90% cache hit rate, we reduce DB load by 10x."

5. **Show you've built systems:**
   > "I'd start with PostgreSQL and Redis. If we hit 100K QPS, we'll add read replicas. Beyond 500K QPS, we'll shard by user_id."

### Red Flags to Avoid

❌ "We'll use microservices" (without justification)
❌ "We'll use blockchain" (almost never needed)
❌ "We'll handle infinite scale" (be realistic)
❌ "NoSQL is better than SQL" (depends on use case)
❌ Choosing tech you don't understand well

### Back-of-Envelope Calculations to Know

```
Storage:
- Text (tweet): 200 bytes
- Image (thumbnail): 50 KB
- Image (full): 500 KB
- Video (1 min): 50 MB

Users:
- 1M DAU = 12 QPS average
- Peak traffic = 5-10x average
- Active users = 10-20% of DAU

Costs (AWS rough):
- EC2 t3.medium: $30/month
- RDS db.r5.large: $200/month
- S3 storage: $0.023/GB
- Data transfer: $0.09/GB
- CloudFront: $0.085/GB
```

---

## Final Interview Tips

### Structure Your Answers

1. **Restate requirement:** "So we need to handle X requests..."
2. **State choice:** "I'll use [technology]..."
3. **Justify:** "Because [reason 1], [reason 2], [example]..."
4. **Acknowledge trade-offs:** "The downside is [X], but we can mitigate by [Y]"
5. **Show growth path:** "If we need to scale further, we can [next step]"

### When Stuck

- **Buy time:** "Let me think about the access patterns here..."
- **Ask clarifying questions:** "Is strong consistency required?"
- **Start simple:** "I'd start with X, then optimize to Y"
- **Use examples:** "How does [Twitter/Netflix] do this?"

### Show Depth Not Just Breadth

Instead of: "We'll use Redis for caching"

Say: "We'll use Redis with LRU eviction and write-through pattern. Cache user sessions (30 min TTL) and API responses (5 min TTL). With 100M users, assuming 20% active, we need 20M × 50KB = 1TB cache distributed across 10 Redis nodes. Expected 90% hit rate reduces DB queries from 100K QPS to 10K QPS."

---

## Document Information

**Version**: 1.0  
**Purpose**: Practical decision-making guide for system design interviews  
**Best Used**: During interview prep and as quick reference  

**Remember**: The best answer explains **why** you made each choice, not just **what** you chose.

---

**Pro Tip**: Before your interview, practice these templates with the specific problem. Replace placeholders with actual numbers and real justifications. Confidence comes from preparation!
