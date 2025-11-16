# Facebook Live Comments System - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Capacity Estimation](#capacity-estimation)
4. [High-Level Architecture](#high-level-architecture)
5. [Core Components](#core-components)
6. [Database Design](#database-design)
7. [API Design](#api-design)
8. [Deep Dives](#deep-dives)
9. [Technology Stack Justification](#technology-stack-justification)
10. [Scalability & Performance](#scalability--performance)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)
12. [Interview Questions & Answers](#interview-questions--answers)

---

## Problem Statement

Design a scalable, real-time commenting system for Facebook Live that enables:
- Millions of concurrent viewers to post and view comments
- Sub-second latency for comment delivery
- Temporal ordering of comments
- Moderation at scale
- Engagement tracking (likes, reactions)
- Graceful degradation under extreme load

### Key Challenges
- **Massive fanout**: 1 comment → deliver to 1M+ viewers
- **Write amplification**: Avoiding 1M database writes per comment
- **Ordering guarantees**: Comments appear in chronological order
- **Hot partitions**: Popular streams concentrate load
- **Real-time delivery**: WebSocket connections at scale
- **Moderation latency**: Filter inappropriate content without blocking
- **Load spikes**: Viral streams can attract millions instantly

### Scale Requirements
- **100M daily active users (DAU)**
- **10M+ concurrent viewers** for popular streams
- **100K+ comments per second** during peak
- **P99 latency < 200ms** for comment delivery
- **99.9% availability**
- **No comment loss** (at-least-once delivery)
- **Support 100K+ concurrent live streams**

---

## Requirements

### Functional Requirements

#### Must Have (P0)

1. **Comment Creation**
   - Post text comments (max 500 characters)
   - Support Unicode and emojis
   - @mention other users
   - Reply to specific comments (threading)
   - Rate limiting (10 comments/minute per user)
   - Idempotency (duplicate prevention)

2. **Comment Consumption**
   - Real-time streaming of new comments to all viewers
   - Paginated historical comments (load previous)
   - Comment count display (live updating)
   - Active viewer count (approximate)
   - Sort by time (newest first by default)

3. **Reactions & Engagement**
   - Like/unlike comments
   - Emoji reactions (❤️, 😂, 😮, 😢, 😡)
   - Reaction counts (real-time)
   - Track who reacted
   - Top comments (most engaged)

4. **Moderation**
   - Profanity filter (pre-send)
   - Spam detection
   - Broadcaster can:
     * Delete comments
     * Block/mute users
     * Pin comments
     * Turn comments on/off
   - Automated content moderation (ML-based)
   - User reporting

5. **Broadcaster Features**
   - Pinned comments (sticky at top)
   - Highlighted comments
   - Slow mode (rate limiting)
   - Followers-only mode
   - Comment analytics (velocity, sentiment)

#### Nice to Have (P1)
- Comment search within stream
- GIF support
- Sticker reactions
- Super chat (paid highlights)
- Comment translation
- Sentiment analysis
- Notification when mentioned
- Comment export for analytics

### Non-Functional Requirements

#### Performance
- **Comment posting latency**: < 100ms (P95)
- **Comment delivery latency**: < 200ms (P99)
- **API response time**: < 50ms (P50)
- **WebSocket connection overhead**: < 10KB memory per connection
- **Database write latency**: < 10ms (P95)

#### Scalability
- Support 10M concurrent viewers per stream
- Handle 1M comments per minute per popular stream
- Scale to 100K concurrent streams
- Support 50K new WebSocket connections per second
- Linear scaling (10x users = 10x servers)

#### Availability
- 99.9% uptime (43 minutes downtime/month)
- Multi-region deployment (US, EU, Asia)
- No single point of failure
- Graceful degradation (quality reduction over failure)
- Automatic failover (< 30 seconds)

#### Consistency
- **Ordering**: Best-effort temporal ordering (1-2 second windows)
- **Comment counts**: Eventual consistency (acceptable lag: 5-10 seconds)
- **Reactions**: Eventual consistency (acceptable lag: 2-3 seconds)
- **Moderation actions**: Strongly consistent (immediate effect)

#### Reliability
- At-least-once delivery guarantee
- Durable storage (no data loss)
- Automatic retry on transient failures
- Circuit breakers on provider failures
- Message queue persistence

#### Security
- Authentication (user tokens)
- Rate limiting (prevent spam)
- Input sanitization (XSS prevention)
- Encryption in transit (TLS 1.3)
- DDoS protection (CDN + rate limiting)

---

## Capacity Estimation

### Assumptions
```
Daily Active Users (DAU): 100M
Live stream engagement rate: 10% = 10M users watching live
Average concurrent streams: 100K
Average viewers per stream: 100-1000
Popular streams: 1M-10M concurrent viewers

Comment rate:
- Average user posts 5 comments per live session
- 30% of viewers comment (active commenters)
- Average session: 30 minutes

Active commenters: 10M × 30% = 3M users
Comments per day: 3M × 5 = 15M comments/day
Comments per second (avg): 15M / 86400 = 174 comments/sec
Peak load (10x): 1,740 comments/sec
```

### Traffic Estimates

**Write Operations (Comment Creation)**:
```
Average: 174 comments/sec
Peak: 1,740 comments/sec
Popular stream burst: 100K comments/min = 1,667 comments/sec

Per stream averages:
- Small stream (100 viewers): 1 comment/sec
- Medium stream (10K viewers): 10 comments/sec
- Large stream (1M viewers): 1,000 comments/sec
```

**Read Operations (Comment Delivery)**:
```
Fanout calculation:
Average viewers per stream: 5,000
Comments/sec: 174
Fanout reads: 174 × 5,000 = 870,000 reads/sec

Popular stream (1M viewers):
Comments/sec: 1,000
Fanout: 1,000 × 1,000,000 = 1 billion reads/sec (!)

This is why fanout-on-read architecture is critical.
With fanout-on-write, we'd need:
- 1B database writes per second
- $50M+/month in database costs
- Impossible to scale
```

**WebSocket Connections**:
```
Concurrent viewers: 10M
WebSocket connections: 10M (1 per viewer)
Memory per connection: 10KB
Total memory: 10M × 10KB = 100 GB

With 50K connections per server:
Servers needed: 10M / 50K = 200 WebSocket servers

Bandwidth per connection:
- Heartbeat: 100 bytes / 30 sec = 3 bytes/sec
- Comment: Average 200 bytes, 1 per 5 sec = 40 bytes/sec
- Total: ~50 bytes/sec per connection

Total bandwidth: 10M × 50 bytes/sec = 500 MB/sec = 4 Gbps
```

### Storage Estimates

**Comment Storage (Hot Data - Active Streams)**:
```
Per comment:
{
  stream_id: 16 bytes (UUID)
  comment_id: 16 bytes (UUID)
  user_id: 16 bytes
  username: 50 bytes
  text: 500 bytes (max)
  timestamp: 8 bytes
  parent_id: 16 bytes (nullable)
  likes: 4 bytes
  flags: 10 bytes
}
Total per comment: ~650 bytes

Active streams: 100K
Comments per stream (24 hours): 5,000
Total active comments: 100K × 5,000 = 500M comments
Storage: 500M × 650 bytes = 325 GB

With indexes and metadata: ~1 TB hot data
```

**Comment Storage (Cold Data - Archived)**:
```
Daily new comments: 15M
Monthly: 15M × 30 = 450M comments
Storage per month: 450M × 650 bytes = 292 GB
Annual: 3.5 TB

With 2-year retention: 7 TB cold storage
With replication (3x): 21 TB
```

**Stream Metadata**:
```
Active streams: 100K
Per stream metadata: 2 KB
Total: 100K × 2 KB = 200 MB (negligible)
```

**User Engagement (Redis Cache)**:
```
Active viewers: 10M
Per viewer session data:
- stream_id: 16 bytes
- user_id: 16 bytes
- last_seen: 8 bytes
- comment_ids_seen: 1 KB (dedupe)
Total: ~1 KB per viewer

Total cache: 10M × 1 KB = 10 GB

Additional caches:
- Recent comments per stream: 5 GB
- Top comments per stream: 2 GB
- Viewer counts: 100 MB
- Blocked users: 1 GB

Total Redis: ~20 GB
Distributed across 10 nodes: 2 GB per node
```

### Bandwidth Estimates

**Ingress (Comment Writing)**:
```
Average comment size: 200 bytes (text + metadata)
Comments/sec: 174
Ingress: 174 × 200 bytes = 34.8 KB/sec
Peak: 1,740 × 200 bytes = 348 KB/sec

Negligible compared to egress
```

**Egress (Comment Reading)**:
```
Per comment delivery: 300 bytes (includes metadata, username, timestamp)
Fanout reads: 870K/sec (average)
Egress: 870K × 300 bytes = 261 MB/sec = 2 Gbps

Peak (popular stream):
1B reads/sec × 300 bytes = 300 GB/sec
This would cost $23M/month in bandwidth!

Solution: WebSocket push (not HTTP polling)
- One message sent to many connections
- Server-side fanout
- ~500 MB/sec total (200 servers × 2.5 MB/sec each)
```

### Infrastructure Estimates

**API Servers (Comment Write)**:
```
Comment write requests: 1,740 QPS (peak)
Per server capacity: 1,000 QPS
Servers needed: 2 servers
With redundancy (3x): 6 servers
CPU: 4 cores per server
Memory: 8 GB per server
```

**WebSocket Servers (Real-time Delivery)**:
```
Concurrent connections: 10M
Per server capacity: 50K connections
Servers needed: 10M / 50K = 200 servers
With redundancy (1.5x): 300 servers

Per server specs:
- CPU: 8 cores (WebSocket handling is I/O bound)
- Memory: 16 GB (10KB × 50K connections = 500MB + overhead)
- Network: 10 Gbps (250K connections × 400 bytes/sec)
```

**Kafka Brokers**:
```
Message throughput: 1,740 msg/sec
Message size: 500 bytes
Bandwidth: 870 KB/sec
Retention: 24 hours
Storage per broker: 75 GB (24 hour × 870 KB/sec × 3 replicas)

Brokers needed:
- Production cluster: 9 brokers (3 per AZ)
- Replication factor: 3
- Partitions: 100 (for parallelism)
```

**Cassandra Nodes**:
```
Write throughput: 1,740 writes/sec
Per node capacity: 5,000 writes/sec
Nodes needed: 1 node
With redundancy + read load: 30 nodes

Per node specs:
- CPU: 8 cores
- Memory: 32 GB
- Storage: 2 TB SSD
- Network: 10 Gbps

Total cluster: 60 TB storage (30 nodes × 2 TB)
```

**Redis Cluster**:
```
Memory: 20 GB
Per node: 2 GB
Nodes needed: 10 nodes
With replication: 20 nodes

Operations:
- Reads: 100K ops/sec
- Writes: 10K ops/sec
- Per node: 10K ops/sec capacity
```

### Cost Estimation (AWS)

```
API Servers (6 × c5.xlarge): $1,200/month
WebSocket Servers (300 × c5.2xlarge): $60,000/month
Kafka Cluster (9 × m5.2xlarge): $9,000/month
Cassandra (30 × i3.2xlarge): $45,000/month
Redis (20 × r5.large): $6,000/month
Load Balancers: $500/month
Data Transfer (egress): $15,000/month
CloudFront CDN: $5,000/month
────────────────────────────────────────
Subtotal: $141,700/month

+ Monitoring, logging, backups: $5,000/month
+ Regional redundancy (×3): $440,000/month
────────────────────────────────────────
Total: ~$450K/month for global deployment
```

**Cost Optimization**:
- Reserved instances (1-year): 30% savings = $315K/month
- Spot instances for non-critical workers: 10% savings = $283K/month
- Aggressive caching: Reduce database by 20% = $265K/month

**Final estimated cost: ~$250-300K/month at scale**

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                           CLIENT LAYER                                │
│  [Mobile Apps - iOS/Android]  [Web - React/Next.js]  [Smart TVs]    │
└────────────────────────┬─────────────────────────────────────────────┘
                         │
                         │ HTTPS / WebSocket (WSS)
                         │ TLS 1.3
                         ↓
┌──────────────────────────────────────────────────────────────────────┐
│                      EDGE LAYER (MULTI-REGION)                       │
│  ┌──────────────────┐  ┌────────────────────┐  ┌─────────────────┐ │
│  │   CloudFlare     │  │   AWS CloudFront   │  │   Route 53      │ │
│  │   - DDoS Protect │  │   - SSL/TLS Term   │  │   - DNS Routing │ │
│  │   - Rate Limit   │  │   - Edge Cache     │  │   - Geo-routing │ │
│  └──────────────────┘  └────────────────────┘  └─────────────────┘ │
└────────────────────────┬─────────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │               │
          ↓              ↓               ↓
   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
   │   Region:    │ │   Region:    │ │   Region:    │
   │   US-EAST    │ │   EU-WEST    │ │   AP-SOUTH   │
   └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
          │                │                 │
          └────────────────┼─────────────────┘
                           ↓
┌──────────────────────────────────────────────────────────────────────┐
│                     LOAD BALANCER LAYER                              │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Application Load Balancer (ALB)                               │ │
│  │  - Path-based routing: /api/v1/comments → API                 │ │
│  │                       /ws/streams/{id} → WebSocket            │ │
│  │  - Health checks: HTTP 200 from /health                       │ │
│  │  - Connection draining: 30s graceful shutdown                 │ │
│  │  - Sticky sessions: For WebSocket (same server per stream)    │ │
│  └────────────────────────────────────────────────────────────────┘ │
└────────────────────────┬─────────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │               │
          ↓              ↓               ↓
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  API Gateway    │ │  API Gateway    │ │  API Gateway    │
│  (Kong/Nginx)   │ │  (Kong/Nginx)   │ │  (Kong/Nginx)   │
│  ─────────────  │ │  ─────────────  │ │  ─────────────  │
│  - JWT Auth     │ │  - JWT Auth     │ │  - JWT Auth     │
│  - Rate Limit   │ │  - Rate Limit   │ │  - Rate Limit   │
│  - Validation   │ │  - Validation   │ │  - Validation   │
│  - Metrics      │ │  - Metrics      │ │  - Metrics      │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                   │
    Write Path          Read Path         Stream Management
          │                  │                   │
          ↓                  ↓                   ↓
┌───────────────────┐ ┌───────────────────┐ ┌────────────────┐
│ Comment Write     │ │  Real-time        │ │  Stream        │
│ Service           │ │  Delivery         │ │  Service       │
│ ───────────────── │ │  Service          │ │  ─────────────│
│ - Validate input  │ │  ───────────────  │ │ - Metadata     │
│ - Check rate limit│ │ - WebSocket mgmt  │ │ - Permissions  │
│ - Generate ID     │ │ - Subscribe Kafka │ │ - Analytics    │
│ - Call moderation │ │ - Fan-out msgs    │ │ - Status       │
│ - Publish Kafka   │ │ - Backpressure    │ └────────┬───────┘
│ - Return 201      │ │ - Heartbeat       │          │
└─────────┬─────────┘ └─────────┬─────────┘          │
          │                     │                     │
          │ Publish             │ Subscribe           │
          ↓                     ↓                     ↓
┌──────────────────────────────────────────────────────────────────────┐
│             MESSAGE STREAMING PLATFORM (Apache Kafka)                │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │ Topics (partitioned by stream_id):                             │ │
│  │  • comments-{region}     : 100 partitions, RF=3, 24h retention│ │
│  │  • engagement-{region}   : 50 partitions, RF=3, 7d retention  │ │
│  │  • moderation-{region}   : 10 partitions, RF=3, 7d retention  │ │
│  │                                                                 │ │
│  │ Consumer Groups:                                                │ │
│  │  • comment-processor-group : Writes to Cassandra               │ │
│  │  • realtime-delivery-group : Pushes to WebSocket clients       │ │
│  │  • analytics-group         : Aggregates metrics                │ │
│  │  • moderation-group        : Content filtering                 │ │
│  └────────────────────────────────────────────────────────────────┘ │
└────────────────────────┬─────────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │               │
    Consume (Async)      │         Consume (Async)
          │              │               │
          ↓              ↓               ↓
┌──────────────────┐ ┌──────────────┐ ┌──────────────────┐
│ Comment          │ │  Moderation  │ │   Analytics      │
│ Processor        │ │  Service     │ │   Service        │
│ ──────────────── │ │  ─────────── │ │   ─────────────  │
│ - Consume Kafka  │ │ - Profanity  │ │ - Metrics        │
│ - Batch writes   │ │ - ML toxicity│ │ - Sentiment      │
│ - Update cache   │ │ - Spam detect│ │ - Trending       │
│ - Cassandra      │ │ - Auto-action│ │ - Velocity       │
│ - Idempotency    │ │ - Report queue│ │ - Flink/Storm   │
└──────────────────┘ └──────────────┘ └──────────────────┘
          │                   │                   │
          └───────────────────┼───────────────────┘
                              │
                              ↓
┌──────────────────────────────────────────────────────────────────────┐
│                         CACHE LAYER (Redis Cluster)                  │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │ Cluster: 10 nodes × 2 GB = 20 GB total memory                  │ │
│  │ ─────────────────────────────────────────────────────────────  │ │
│  │ Keys:                                                           │ │
│  │  • stream:{stream_id}:comments:recent → LIST (last 100)        │ │
│  │  • stream:{stream_id}:viewers → SET (active user_ids)          │ │
│  │  • stream:{stream_id}:comment_count → INTEGER                  │ │
│  │  • stream:{stream_id}:top_comments → SORTED SET (by score)    │ │
│  │  • user:{user_id}:rate_limit → SORTED SET (sliding window)    │ │
│  │  • stream:{stream_id}:blocked_users → SET                      │ │
│  │  • comment:{comment_id}:likes → HYPERLOGLOG (approx count)    │ │
│  └────────────────────────────────────────────────────────────────┘ │
└────────────────────────┬─────────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │               │
          ↓              ↓               ↓
┌──────────────────────────────────────────────────────────────────────┐
│                        STORAGE LAYER                                 │
│  ┌───────────────────┐  ┌──────────────────┐  ┌──────────────────┐ │
│  │ Cassandra Cluster │  │  PostgreSQL      │  │  S3 / Object     │ │
│  │ ───────────────── │  │  ──────────────  │  │  Storage         │ │
│  │ 30 nodes, 60TB    │  │ Read replicas    │  │  ──────────────  │ │
│  │                   │  │                  │  │                  │ │
│  │ Tables:           │  │ Tables:          │  │ Storage:         │ │
│  │ • comments        │  │ • streams        │  │ • Archived       │ │
│  │ • reactions       │  │ • users          │  │   comments       │ │
│  │ • user_actions    │  │ • permissions    │  │ • Media files    │ │
│  │                   │  │ • moderation     │  │ • Compliance     │ │
│  │ Partitioned by:   │  │                  │  │   exports        │ │
│  │ stream_id         │  │ Sharded by:      │  │                  │ │
│  │ + timestamp       │  │ user_id hash     │  │ Lifecycle:       │ │
│  │                   │  │                  │  │ Hot → Glacier    │ │
│  │ RF=3, QUORUM      │  │ Primary + 5      │  │ (90 days)        │ │
│  │ consistency       │  │ read replicas    │  │                  │ │
│  └───────────────────┘  └──────────────────┘  └──────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌──────────────────────────────────────────────────────────────────────┐
│                   OBSERVABILITY & MONITORING                         │
│  ┌───────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │
│  │  Prometheus   │  │  ELK Stack   │  │  Jaeger/Zipkin           │ │
│  │  + Grafana    │  │  ────────── │  │  ────────────────────    │ │
│  │  ───────────  │  │ - Logs       │  │ - Distributed tracing    │ │
│  │ - Metrics     │  │ - Search     │  │ - Request tracking       │ │
│  │ - Alerts      │  │ - Kibana     │  │ - Latency analysis       │ │
│  │ - Dashboards  │  │ - Alerts     │  │ - Bottleneck detection   │ │
│  └───────────────┘  └──────────────┘  └──────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### Architecture Decisions & Rationale

**1. Why Multi-Region Deployment?**
```
Problem: Global audience, latency matters

Single region (US-East):
- US users: 20ms
- EU users: 100ms
- Asia users: 200ms
- Result: Poor experience for 70% of users

Multi-region (US, EU, Asia):
- All users: < 50ms to nearest region
- Benefit: 4x better latency
- Cost: 3x infrastructure (acceptable)

Implementation:
- Active-active in all regions
- Users routed to nearest (GeoDNS)
- Data replicated (Cassandra multi-DC)
- Kafka per region (no cross-region)
```

**2. Why Kafka Over RabbitMQ/SQS?**
```
Requirements:
- High throughput: 100K+ msg/sec
- Durability: No message loss
- Replay: Reprocess failed messages
- Ordering: Within stream_id
- Consumer groups: Parallel processing

Kafka:
✓ Throughput: 1M+ msg/sec
✓ Disk-backed: Durable
✓ Consumer offset: Replay any time
✓ Partition ordering: Guaranteed
✓ Consumer groups: Built-in

RabbitMQ:
✗ Throughput: 100K msg/sec (10x less)
✗ Memory-first: Less durable
✗ No replay: Ack = deleted
✓ Routing: More flexible
✓ Consumer groups: Supported

SQS:
✗ Ordering: FIFO limited (3K msg/sec)
✗ Replay: Limited visibility timeout
✗ Cost: $0.40 per 1M = $3.5M/month
✓ Managed: No ops
✓ Scalability: Unlimited

Winner: Kafka (throughput + durability + replay)
```

**3. Why WebSocket Over HTTP Polling/SSE?**
```
HTTP Long Polling:
- Client: GET /comments?stream_id=X&after=timestamp
- Server: Hold request until new comment (30s timeout)
- Problem:
  * 10M concurrent connections = 10M open HTTP requests
  * Each request: 2KB memory = 20GB
  * Timeout: Reconnect overhead
  * Not scalable

Server-Sent Events (SSE):
- One-way: Server → Client
- Browser support: Good
- Problem:
  * HTTP/1.1: Max 6 connections per domain
  * No binary data support
  * Reconnection complex
  * Not bidirectional

WebSocket:
✓ Bidirectional: Client ↔ Server
✓ Low overhead: 10KB per connection
✓ Persistent: No reconnection
✓ Binary support: Efficient
✓ HTTP/2 compatible: Multiplexing
✓ Wide support: All modern browsers

Winner: WebSocket (efficiency + real-time + bidirectional)
```

**4. Why Cassandra Over MongoDB/DynamoDB?**
```
Requirements:
- Write-heavy: 1,740 writes/sec
- Time-series: Comments ordered by time
- Scalability: Linear with nodes
- High availability: Multi-DC

Cassandra:
✓ Write-optimized: LSM tree, append-only
✓ Time-series: Natural fit with clustering
✓ Linear scale: Add nodes = more capacity
✓ Multi-DC: Built-in replication
✓ No single master: All nodes equal
✓ Tunable consistency: QUORUM, ONE, ALL

MongoDB:
✗ Single master: Write bottleneck
✗ Sharding: Complex, not automatic
✓ Query language: Familiar (JSON)
✓ Transactions: ACID
✗ Time-series: Need TTL indexes

DynamoDB:
✓ Fully managed: No ops
✓ Scalability: Unlimited
✗ Cost: $0.25 per million writes = $38K/month
