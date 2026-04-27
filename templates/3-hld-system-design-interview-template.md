# 🌐 HLD / System Design Interview Template — Microsoft

> **Time**: 45-60 minutes | **Framework**: Hello Interview (hellointerview.com/learn/system-design/in-a-hurry/delivery)
> **Goal**: Show you can design scalable, reliable distributed systems end-to-end
> **Key**: The framework is **Requirements → Core Entities → API → Data Flow → High-Level Design → Deep Dives**

---

## ⏱️ Timeline (45 min)

| Phase | Time | What You Do | Interviewer Signal |
|-------|------|-------------|-------------------|
| **1. Requirements** | 5 min | Clarify functional + non-functional | Can you scope a problem? |
| **2. Core Entities** | 3 min | Identify data model | Can you model data? |
| **3. API Design** | 3 min | Define REST endpoints | Can you design interfaces? |
| **4. Data Flow** | 2 min | Walk through read/write paths | Do you understand data movement? |
| **5. High-Level Design** | 12 min | Draw architecture, walk through flows | Can you decompose a system? |
| **6. Deep Dives** | 15 min | 2-3 hard problems in depth | Can you go deep? |
| **7. Wrap Up** | 5 min | Bottlenecks, monitoring, future | Production thinking? |

---

## Phase 1️⃣ — Requirements (5 min)

> **Say**: "Before I start designing, let me make sure I understand the requirements. I'll separate them into functional and non-functional."

### 🔑 Functional Requirements — Questions to Ask

| Category | Questions |
|----------|-----------|
| **Core Use Cases** | "What are the 2-3 core features?" |
| **Users** | "Who are the users? How many DAU?" |
| **Read vs Write** | "Is this read-heavy or write-heavy?" |
| **Real-time** | "Do users need real-time updates? (WebSocket vs polling)" |
| **Scale** | "What's the expected scale? (millions of users? billions of records?)" |
| **Geography** | "Single region or multi-region?" |

### 🔑 Non-Functional Requirements — Always Cover These

| NFR | Question to Ask | Why It Matters |
|-----|----------------|----------------|
| **Availability** | "What uptime? 99.9%? 99.99%?" | Determines redundancy strategy |
| **Latency** | "What's acceptable p99 latency? <100ms? <500ms?" | Determines caching, CDN needs |
| **Consistency** | "Strong consistency or eventual consistency OK?" | Database + caching strategy |
| **Durability** | "Can we lose data? What's the durability target?" | Replication strategy |
| **Scalability** | "Need to handle 10x traffic spikes?" | Auto-scaling, queue-based design |

### 📝 Requirements Output Template

```
"Let me summarize:

 FUNCTIONAL REQUIREMENTS (P0):
 1. [Feature 1] — e.g., Users can create short URLs
 2. [Feature 2] — e.g., Short URLs redirect to original URLs  
 3. [Feature 3] — e.g., Track click analytics

 NON-FUNCTIONAL REQUIREMENTS:
 - Scale: [X] DAU, [Y] QPS read, [Z] QPS write
 - Latency: p99 < [X]ms for reads, < [Y]ms for writes
 - Availability: [99.9% / 99.99%]
 - Consistency: [Strong / Eventual] — because [reason]
 - Storage: ~[X] TB over [Y] years

 OUT OF SCOPE (defer unless time permits):
 - [Feature A]
 - [Feature B]
 
 Does this sound right?"
```

---

## Phase 2️⃣ — Core Entities (3 min)

> **Say**: "Let me identify the core data entities and their relationships."

### 📊 Entity Design Template

```
CORE ENTITIES:

1. [EntityName]
   - id: UUID (partition key)
   - [field1]: type
   - [field2]: type
   - created_at: timestamp
   - updated_at: timestamp

2. [EntityName]
   - id: UUID (partition key)
   - [foreign_key]: UUID → references Entity1
   - [field1]: type
   - [field2]: type

RELATIONSHIPS:
 - User → has many → Posts (1:N)
 - Post → has many → Comments (1:N)
 - User → follows → User (M:N via follow table)

DATABASE CHOICE:
 - [Entity1]: [SQL/NoSQL] because [reason]
   - SQL: Need ACID transactions, complex queries, relationships
   - NoSQL: Need horizontal scaling, flexible schema, high write throughput
 
SHARD KEY: [field] — because [even distribution reason]
INDEXES: [field1, field2] — because [query pattern reason]
```

### 🗄️ Database Selection Cheat Sheet

| Need | Choose | Examples |
|------|--------|----------|
| ACID transactions, complex joins | **PostgreSQL / SQL Server** | Orders, payments, user accounts |
| High write throughput, flexible schema | **MongoDB / DynamoDB** | Logs, analytics, user activity |
| Key-value lookups, caching | **Redis** | Sessions, rate limiting, leaderboard |
| Full-text search | **Elasticsearch** | Search, autocomplete, log analytics |
| Time-series data | **InfluxDB / TimescaleDB** | Metrics, IoT, stock prices |
| Graph relationships | **Neo4j / Neptune** | Social graph, recommendations |
| Wide-column, massive scale | **Cassandra / HBase** | Chat messages, IoT, time-series |
| Blob/file storage | **Azure Blob / S3** | Images, videos, documents |

---

## Phase 3️⃣ — API Design (3 min)

> **Say**: "Let me define the core API endpoints."

### 🔌 API Template

```
# ===== Core APIs =====

POST   /api/v1/[resource]
  Request:  { field1, field2 }
  Response: { id, field1, field2, created_at }
  Auth: Bearer token
  Rate Limit: 100 req/min

GET    /api/v1/[resource]/{id}
  Response: { id, field1, field2, ... }
  Cache: CDN + Redis (TTL: 5 min)

GET    /api/v1/[resource]?cursor={cursor}&limit=20&sort=created_at
  Response: { items: [...], next_cursor: "abc123", has_more: true }
  Note: Cursor-based pagination (not offset — better for large datasets)

PUT    /api/v1/[resource]/{id}
  Request:  { field1, field2 }
  Response: { id, field1, field2, updated_at }

DELETE /api/v1/[resource]/{id}
  Response: { success: true }
  Note: Soft delete (set deleted_at, don't actually remove)

# ===== Real-time (if needed) =====
WebSocket  /ws/[resource]/subscribe
  Events: { type: "update", data: {...} }
```

### API Design Points to Mention

| Topic | What to Say |
|-------|-------------|
| **Pagination** | "I'll use cursor-based pagination — more efficient than offset for large datasets" |
| **Versioning** | "API versioned via URL path (/v1/) for backward compatibility" |
| **Idempotency** | "POST requests include an idempotency key to prevent duplicates" |
| **Rate Limiting** | "Rate limited per user to prevent abuse" |
| **Auth** | "JWT bearer tokens via OAuth 2.0 / Azure AD" |

---

## Phase 4️⃣ — Data Flow (2 min)

> **Say**: "Let me walk through the two main data flows — write path and read path."

### 📝 Data Flow Template

```
WRITE PATH (e.g., Create a short URL):
  1. Client → API Gateway (auth, rate limit, validation)
  2. API Gateway → [Service] (business logic)
  3. [Service] → Database (persist)
  4. [Service] → Cache (update/invalidate)
  5. [Service] → Message Queue (async: analytics, notifications)
  6. Response → Client

READ PATH (e.g., Redirect short URL):
  1. Client → CDN (cache hit? → return immediately)
  2. CDN miss → API Gateway → [Service]
  3. [Service] → Cache (Redis) (cache hit? → return)
  4. Cache miss → Database (query)
  5. Populate cache → return to client
```

---

## Phase 5️⃣ — High-Level Design (12 min)

> **Say**: "Let me draw the architecture and walk through the main flows."

### 🏗️ Architecture Drawing Template

```
                        ┌─────────┐
                        │   CDN   │ (static assets + cached responses)
                        └────┬────┘
                             │
┌──────────┐          ┌─────┴──────┐
│  Client  │────────→│ API Gateway │ (Azure API Mgmt / Nginx)
│ (Web/App)│          │  + LB      │ Auth, Rate Limit, Routing
└──────────┘          └─────┬──────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
        ┌─────┴─────┐ ┌─────┴─────┐ ┌─────┴─────┐
        │ Service A  │ │ Service B  │ │ Service C  │
        │ (Write)    │ │ (Read)     │ │ (Search)   │
        └─────┬──────┘ └─────┬──────┘ └─────┬──────┘
              │              │              │
              │         ┌────┴────┐         │
              │         │  Cache  │         │
              │         │ (Redis) │         │
              │         └────┬────┘         │
              │              │              │
        ┌─────┴──────────────┴──────────────┴─────┐
        │              Database                     │
        │    (Primary - Write)  (Replicas - Read)   │
        └──────────────┬───────────────────────────┘
                       │
                ┌──────┴──────┐
                │ Message Queue│ (Kafka / Azure Service Bus)
                │  (Async)     │
                └──────┬──────┘
                       │
              ┌────────┼────────┐
              │        │        │
        ┌─────┴──┐ ┌──┴───┐ ┌──┴──────┐
        │Analytics│ │Notify│ │ Search  │
        │ Worker  │ │Worker│ │ Indexer │
        └────────┘ └──────┘ └─────────┘
```

### 🧱 Component Checklist

| Component | Purpose | When to Include |
|-----------|---------|----------------|
| **CDN** | Cache static/frequently accessed content at edge | Read-heavy, global users |
| **API Gateway** | Auth, rate limiting, routing, SSL termination | Always |
| **Load Balancer** | Distribute traffic across service instances | Always |
| **Application Services** | Business logic (split by read/write if needed) | Always |
| **Cache (Redis)** | Reduce DB load, sub-ms reads | Read-heavy systems |
| **Primary DB** | Source of truth for writes | Always |
| **Read Replicas** | Scale reads horizontally | Read-heavy, >10K QPS reads |
| **Message Queue** | Decouple services, async processing | Write-heavy, event-driven |
| **Workers** | Async background processing | Analytics, notifications, indexing |
| **Search Engine** | Full-text search, complex queries | If search is a feature |
| **Blob Storage** | Files, images, videos | If media is involved |
| **WebSocket Server** | Real-time push to clients | If real-time updates needed |

---

## Phase 6️⃣ — Deep Dives (15 min) ⭐ Most Important Phase

> **Say**: "Now let me deep-dive into the 2-3 hardest parts of the system."

### 🎯 Pick Deep Dives Based on Problem Type

| System Type | Deep Dive Topics |
|-------------|-----------------|
| **URL Shortener** | ID generation (Base62, counter, hash), cache strategy, analytics pipeline |
| **Messaging / Chat** | Message ordering, delivery guarantees, presence, WebSocket scaling |
| **Social Feed** | Fan-out-on-write vs fan-out-on-read, ranking, caching |
| **File Storage** | Chunked upload, deduplication, sync conflict resolution |
| **Search** | Inverted index, ranking, autocomplete, distributed search |
| **Notification** | Push vs pull, priority queue, delivery guarantees, dedup |
| **Video Streaming** | Transcoding pipeline, adaptive bitrate, CDN |
| **Rate Limiter** | Token bucket vs sliding window, distributed rate limiting |
| **Real-time Analytics** | Stream processing (Kafka + Flink), pre-aggregation |

### 📝 Deep Dive Structure (for each topic)

```
"Let me deep-dive into [TOPIC]:

 PROBLEM: [What's hard about this?]
 
 OPTIONS:
  Option A: [Approach] — Pros: [X] Cons: [Y]
  Option B: [Approach] — Pros: [X] Cons: [Y]
  Option C: [Approach] — Pros: [X] Cons: [Y]

 MY CHOICE: Option [X] because [reason given our requirements]

 HOW IT WORKS:
  1. [Step 1]
  2. [Step 2]
  3. [Step 3]
  
 TRADE-OFFS:
  - We gain: [benefit]
  - We lose: [cost]
  - This is acceptable because: [reason]"
```

### 🔥 Common Deep Dive Topics & Answers

#### Scaling Reads (Caching)
```
Problem: Database can't handle 100K+ QPS reads

Strategy: Multi-layer caching
 1. CDN (edge cache) — static content, ~90% hit rate
 2. Application cache (Redis) — dynamic content, ~80% hit rate  
 3. Database read replicas — remaining ~2% of requests

Cache Invalidation: 
 - Write-through: Update cache on every write (consistent but slower writes)
 - Write-behind: Update cache async (faster writes, brief inconsistency)
 - TTL-based: Cache expires after N seconds (simplest, eventual consistency)
 
 For this system, I'll use [TTL + write-through] because [reason].
```

#### Scaling Writes
```
Problem: Single DB can't handle 50K+ QPS writes

Options:
 1. Database sharding — partition by [shard key]
 2. Write queue — buffer writes in Kafka, batch process
 3. CQRS — separate write model from read model

For this system: [choice] because [reason]
```

#### Consistency vs Availability
```
Problem: Multi-region deployment — what happens during partition?

CAP Theorem: We can't have all three (Consistency, Availability, Partition tolerance)

 - CP (Consistency + Partition): Reject writes during partition → banking, payments
 - AP (Availability + Partition): Accept writes, resolve conflicts later → social media, messaging

For this system: [AP/CP] because [reason]
Conflict resolution: [Last-write-wins / CRDT / Application-level merge]
```

#### Data Partitioning / Sharding
```
Shard Key Selection:
 - user_id: Good for user-centric queries, risk of hot shards (celebrity users)
 - [entity]_id: Good for uniform distribution
 - geographic_region: Good for data locality
 - composite (user_id + timestamp): Good for time-series + user data

For this system: Shard by [key] because:
 1. Uniform distribution across shards
 2. Queries can be routed to single shard
 3. No cross-shard joins needed for common queries
```

#### Handling Failures
```
"For fault tolerance:
 1. Retries with exponential backoff + jitter
 2. Circuit breaker pattern (trip after N failures, half-open to test recovery)
 3. Bulkhead pattern (isolate failure to one service)
 4. Dead Letter Queue (DLQ) for messages that fail N times
 5. Health checks + auto-scaling to replace unhealthy instances
 6. Multi-AZ deployment (survive entire data center failure)"
```

---

## Phase 7️⃣ — Wrap Up (5 min)

> **Say**: "Let me discuss monitoring, bottlenecks, and future improvements."

### 📊 Monitoring & Observability

```
"For production monitoring, I'd set up:
 
 METRICS (Azure Monitor / Datadog / CloudWatch):
 - p50, p99 latency per endpoint
 - Error rate (5xx / 4xx)
 - QPS per service
 - Cache hit ratio
 - Database connection pool utilization
 - Queue depth and consumer lag

 ALERTS:
 - p99 latency > [X]ms → page on-call
 - Error rate > 1% → page on-call
 - Queue depth > [N] → auto-scale consumers
 - CPU > 80% → auto-scale instances

 LOGGING:
 - Structured logging (JSON) with correlation IDs
 - Request tracing (distributed tracing with Jaeger/Zipkin)
 - Error tracking (Sentry)"
```

### 🔍 Bottleneck Identification

```
"The main bottlenecks I see:
 1. [Bottleneck 1] — Solution: [approach]
 2. [Bottleneck 2] — Solution: [approach]  
 3. [Bottleneck 3] — Solution: [approach]"
```

### 🚀 Future Improvements

```
"If we had more time, I'd add:
 1. Multi-region active-active for global low latency
 2. ML-based ranking / personalization
 3. A/B testing framework for feature rollout
 4. Cost optimization (tiered storage, reserved instances)"
```

---

## 🧮 Back-of-Envelope Estimation Cheat Sheet

### Quick Formulas

```
DAU to QPS:
  QPS = DAU × actions_per_user / 86,400
  Peak QPS = QPS × 3 (or 5 for spiky traffic)

Storage:
  Total = records_per_day × record_size × retention_days
  
Bandwidth:
  Bandwidth = QPS × average_response_size

Memory (Cache):
  Cache size = QPS × cache_duration × avg_object_size
  Rule of thumb: Cache top 20% of data to get 80% hit rate
```

### Reference Numbers

| Metric | Value |
|--------|-------|
| Seconds in a day | 86,400 (~100K) |
| Seconds in a month | 2.6 million |
| 1 million requests/day | ~12 QPS |
| 1 char (ASCII) | 1 byte |
| 1 char (UTF-8) | 1-4 bytes |
| Average URL | ~100 bytes |
| Average tweet/post | ~300 bytes |
| Average image | ~200 KB |
| HD image | ~2 MB |
| 1 min video (SD) | ~25 MB |
| 1 min video (HD) | ~150 MB |
| Average JSON API response | ~2 KB |

---

## 🎯 Microsoft HLD-Specific Tips

### What Microsoft Interviewers Look For
1. **Structured approach**: Follow a clear framework, don't jump to solutions
2. **Requirements first**: Always clarify before designing
3. **Trade-off reasoning**: Every choice should have a WHY
4. **Depth over breadth**: 2-3 deep dives >> surface-level everything
5. **Azure awareness**: Mention Azure services where relevant (not required, but nice)
6. **Production thinking**: Monitoring, alerting, failure handling, scaling

### 🏢 Azure Service Mapping (Optional — Shows Awareness)

| Generic Component | Azure Equivalent |
|-------------------|-----------------|
| CDN | Azure Front Door / Azure CDN |
| API Gateway | Azure API Management |
| Load Balancer | Azure Load Balancer / Application Gateway |
| Compute | Azure App Service / AKS / Azure Functions |
| Cache | Azure Cache for Redis |
| SQL Database | Azure SQL / Cosmos DB (SQL API) |
| NoSQL Database | Cosmos DB |
| Message Queue | Azure Service Bus / Event Hubs |
| Stream Processing | Azure Stream Analytics / Event Hubs + Functions |
| Blob Storage | Azure Blob Storage |
| Search | Azure Cognitive Search / AI Search |
| Auth | Azure Active Directory (Entra ID) |
| Monitoring | Azure Monitor / Application Insights |
| DNS | Azure DNS |
| Key Vault | Azure Key Vault |

### 🗣️ Key Phrases to Use

| Moment | Say This |
|--------|----------|
| Opening | "Let me start by clarifying the functional and non-functional requirements." |
| After requirements | "Given [X] QPS and [Y] latency requirements, I'll need [caching / sharding / etc.]." |
| Drawing architecture | "Let me walk through the write path first, then the read path." |
| Database choice | "I'll use [DB] because our access pattern is [pattern] and we need [property]." |
| Caching | "To meet the <100ms p99 target, I'll add a Redis cache with [invalidation strategy]." |
| Trade-off | "I'm choosing [Option A] over [Option B]. We trade off [X] for [Y], which is acceptable because [Z]." |
| Deep dive | "The hardest part here is [problem]. Let me explore the options..." |
| Consistency | "For this use case, eventual consistency is acceptable because [reason]. If strong consistency were required, I'd use [approach]." |
| Wrap-up | "The main bottleneck is [X]. To address it, I'd [solution]. For monitoring, I'd track [metrics]." |

---

## 📋 Quick Self-Check Before Finishing

- [ ] Did I cover **functional + non-functional** requirements?
- [ ] Did I define **core entities** with database choice + shard key?
- [ ] Did I design **clean APIs** with pagination, auth, rate limiting?
- [ ] Did I draw a **clear architecture diagram** with all key components?
- [ ] Did I walk through **read + write data flows**?
- [ ] Did I do **2-3 meaningful deep dives** with trade-off analysis?
- [ ] Did I do **back-of-envelope estimation** (QPS, storage, bandwidth)?
- [ ] Did I discuss **failure handling** (retries, circuit breaker, DLQ)?
- [ ] Did I mention **monitoring and alerting**?
- [ ] Did I explain **WHY** for every major design decision?

---

## 📚 Common Microsoft HLD Problems (Practice List)

| Problem | Key Deep Dives | Azure Focus |
|---------|---------------|-------------|
| URL Shortener | ID generation, caching, analytics | Cosmos DB, Redis, CDN |
| Microsoft Teams Chat | Message ordering, presence, WebSocket | Service Bus, SignalR |
| OneDrive / File Storage | Chunked upload, sync, dedup | Blob Storage, Cosmos DB |
| Outlook Email | Inbox fan-out, search, push notifications | Service Bus, AI Search |
| Azure AD / SSO | Token management, federation, MFA | Entra ID, Key Vault |
| News Feed | Fan-out strategy, ranking, caching | Cosmos DB, Redis, ML |
| Notification System | Delivery guarantees, priority, templating | Event Hubs, Service Bus |
| Video Streaming | Transcoding, adaptive bitrate, CDN | Media Services, CDN |
| Search Engine | Inverted index, ranking, autocomplete | AI Search, Cosmos DB |
| Rate Limiter | Token bucket, distributed counting | Redis, API Management |
| Real-time Analytics | Stream processing, pre-aggregation | Event Hubs, Stream Analytics |
| Calendar Scheduler | Conflict detection, timezone, recurrence | SQL, Redis, Service Bus |
