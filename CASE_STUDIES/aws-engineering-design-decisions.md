# AWS Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from AWS's engineering papers, re:Invent talks, and builder blogs. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: AWS re:Invent talks, Amazon/AWS engineering papers (Dynamo, Aurora, S3, etc.), Werner Vogels' blog, AWS Architecture Blog

---

## Table of Contents

1. [DynamoDB — The Dynamo Paper](#1-dynamodb)
2. [S3 — Object Storage at Exabyte Scale](#2-s3)
3. [Aurora — Cloud-Native Relational Database](#3-aurora)
4. [Lambda — Serverless Compute](#4-lambda)
5. [SQS — The First AWS Service (Queue)](#5-sqs)
6. [Kinesis vs Kafka (MSK)](#6-kinesis-vs-kafka)
7. [ElastiCache — Managed Redis/Memcached](#7-elasticache)
8. [CloudFront CDN — Edge Delivery](#8-cloudfront)
9. [Route 53 — DNS at Global Scale](#9-route-53)
10. [ECS vs EKS vs Lambda — Compute Tradeoffs](#10-compute-tradeoffs)
11. [The Cell-Based Architecture](#11-cell-based-architecture)
12. [Shuffle Sharding — Blast Radius Reduction](#12-shuffle-sharding)
13. [Amazon's Builder's Library — Retry & Backoff](#13-retry-backoff)
14. [DynamoDB Streams + Lambda — Event-Driven](#14-dynamodb-streams)
15. [Amazon's Service-Oriented Architecture (SOA)](#15-service-oriented-architecture)
16. [S3 Consistency — From Eventual to Strong](#16-s3-consistency)
17. [AWS Nitro System — Hypervisor Innovation](#17-nitro-system)
18. [Amazon Timestream — Time-Series at Scale](#18-timestream)
19. [Step Functions — Workflow Orchestration](#19-step-functions)
20. [Multi-AZ & Multi-Region Patterns](#20-multi-az-multi-region)

---

## 1. DynamoDB — The Dynamo Paper

**Paper**: "Dynamo: Amazon's Highly Available Key-Value Store" (2007) → evolved into DynamoDB (2012)

### Problem
Amazon.com needed an always-available key-value store for the shopping cart and other critical services. Even during network partitions or server failures, customers must be able to add items to their cart. Availability > Consistency.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **CAP choice** | AP (Availability + Partition tolerance) | CP (Consistency + Partition tolerance) | "Customers should always be able to add to cart" — availability is king for e-commerce |
| **Consistency** | Eventual consistency (with conflict resolution) | Strong consistency | Strong consistency requires coordination → higher latency, lower availability |
| **Partitioning** | Consistent hashing (virtual nodes) | Modular hashing | Adding/removing nodes only moves ~1/N of data |
| **Replication** | Sloppy quorum (N=3, W=2, R=2 typical) with hinted handoff | Strict quorum | Sloppy quorum allows writes even when some replicas are temporarily down |
| **Conflict resolution** | Vector clocks (later: last-writer-wins for simplicity) | Single-master writes | Vector clocks allow concurrent writes without coordination |
| **Failure detection** | Gossip protocol | Centralized heartbeat | Decentralized — no single point of failure |
| **Anti-entropy** | Merkle trees for replica synchronization | Full data comparison | Merkle trees identify differences with O(log N) comparisons |

### Evolution to DynamoDB
The original Dynamo paper described an internal system. DynamoDB (the managed service) simplified many choices:
- **Removed vector clocks** → replaced with last-writer-wins (simpler for most applications)
- **Added strong consistency option** → `ConsistentRead=true` reads from leader
- **Added DynamoDB Streams** → change data capture for event-driven architectures
- **Added Global Tables** → multi-region active-active replication
- **Added on-demand capacity** → no need to pre-provision read/write units

### Tradeoff
- ✅ Always available — writes succeed even during partial failures
- ✅ Predictable single-digit millisecond latency at any scale
- ✅ Fully managed — no operational overhead
- ❌ No JOINs, no complex queries (key-based access only)
- ❌ Hot partition problem (one partition key getting too much traffic)
- ❌ Eventually consistent by default (strong consistency costs 2× read capacity)
- ❌ Item size limit: 400 KB

### Interview Use
> "DynamoDB's design follows the Dynamo paper — it chose availability over consistency (AP in CAP). Writes always succeed using sloppy quorum with hinted handoff. Consistent hashing with virtual nodes distributes data. For our system, I'd use DynamoDB when I need single-digit ms latency at any scale with a simple key-value access pattern. The tradeoff is no JOINs and eventual consistency by default."

---

## 2. S3 — Object Storage at Exabyte Scale

**Source**: AWS re:Invent talks, "Amazon S3: The Story So Far"

### Problem
Store virtually unlimited amounts of data durably (11 nines = 99.999999999% durability) with high availability, serving from small files to multi-terabyte objects.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Durability** | 11 nines (99.999999999%) via replication across ≥ 3 AZs | Single AZ | Designed to sustain simultaneous loss of 2 facilities |
| **Consistency** | Strong read-after-write (since Dec 2020) | Eventual consistency (original) | Customers needed consistency without workarounds; achieved without latency increase |
| **Namespace** | Flat (no hierarchical folders — "/" is just a prefix) | True filesystem hierarchy | Flat namespace is simpler to shard and scale; "folders" are a UI illusion |
| **Storage classes** | Tiered: Standard → IA → One Zone-IA → Glacier → Deep Archive | Single class | Different access patterns = different cost optimizations (hot vs cold) |
| **API** | Simple: PUT, GET, DELETE, LIST | Complex filesystem API (POSIX) | Simplicity enables massive scale; no rename/append/random write |
| **Multipart upload** | Upload in parallel chunks, assemble on server | Single upload | Handles large files (up to 5 TB), resumes on failure, parallelizes bandwidth |

### Key Architecture Insights
- **Data is replicated across ≥ 3 AZs** within a region automatically
- **Metadata and data are separated** — metadata index is itself a massive distributed system
- **No single point of failure** — designed for "everything fails, all the time"
- **Strong consistency** (since 2020) was achieved by making the metadata system strongly consistent — a major engineering feat that didn't sacrifice performance

### Tradeoff
- ✅ Virtually unlimited storage at low cost
- ✅ 11 nines durability — practically indestructible
- ✅ Strong read-after-write consistency (no more stale reads)
- ❌ Not a filesystem — no append, no rename, no random write (PUT replaces entire object)
- ❌ LIST operations are eventually consistent for very recent writes (edge case)
- ❌ Latency: 50-200ms per GET (not suitable for sub-ms access like Redis)
- ❌ Rate limits per prefix (3,500 PUT/s, 5,500 GET/s per prefix — mitigated by spreading prefixes)

### Interview Use
> "For blob storage (images, videos, documents), I'd use S3 — 11 nines durability, strong read-after-write consistency, and virtually unlimited capacity. I'd use lifecycle policies to move old data from Standard → Infrequent Access → Glacier to optimize cost. For high-throughput access, spread objects across multiple prefixes to avoid per-prefix rate limits."

---

## 3. Aurora — Cloud-Native Relational Database

**Paper**: "Amazon Aurora: Design Considerations for High Throughput Cloud-Native Relational Databases"

### Problem
Traditional MySQL/PostgreSQL on EC2 with EBS has limited IOPS, slow replication, and failover takes minutes. Customers need MySQL/PostgreSQL compatibility with cloud-native scalability and availability.

### What AWS Built
**Aurora** — decouples the storage layer from the compute layer. The storage is a distributed, self-healing, multi-AZ volume. The compute layer is a familiar MySQL/PostgreSQL engine.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Separate compute (SQL engine) from storage (distributed, log-structured) | Traditional DB (compute + storage coupled) | Storage can scale independently; replication is at storage layer, not SQL layer |
| **Replication** | 6 copies across 3 AZs (2 per AZ) | 2 copies (master + standby) | Tolerates loss of entire AZ + 1 additional node without data loss |
| **Write protocol** | Quorum: 4 of 6 copies must ACK write | Synchronous replication to all | Quorum is faster (don't wait for slowest); 4/6 tolerates 2 failures |
| **Read protocol** | Quorum: 3 of 6 for reads (only during recovery) | Read from leader only | Normal reads go to local buffer cache; quorum only during failover |
| **Failover** | ~30 seconds (vs 60-120s for RDS MySQL) | Minutes | Storage is shared — new primary just needs to replay a small log |
| **Replication lag** | < 20ms (vs seconds for MySQL replication) | Asynchronous replication (seconds of lag) | Replicas share the same storage volume — they see writes almost immediately |
| **Scaling** | Storage auto-grows up to 128 TB; up to 15 read replicas | Manual EBS volume management | No pre-provisioning; storage grows automatically |

### Tradeoff
- ✅ 5× throughput of MySQL at lower cost
- ✅ 6-way replication across 3 AZs — extremely durable
- ✅ ~30 second failover (vs minutes for RDS)
- ✅ MySQL/PostgreSQL compatible — no code changes
- ❌ More expensive than RDS for small workloads
- ❌ Single-region (Aurora Global Database adds cross-region but with latency)
- ❌ Still a single-writer model (multi-master exists but with limitations)
- ❌ Storage maximum: 128 TB (enough for most, not for massive datasets)

### Interview Use
> "For a relational database with high availability, I'd use Aurora — it decouples compute from storage, replicates 6 copies across 3 AZs, and achieves ~30 second failover. Aurora replicas share the same storage volume, so replication lag is < 20ms vs seconds for traditional MySQL replication. It's MySQL/PostgreSQL compatible — no code changes needed."

---

## 4. Lambda — Serverless Compute

**Source**: "Firecracker: Lightweight Virtualization for Serverless Applications" + re:Invent talks

### Problem
Customers want to run code without managing servers. Need fast startup, pay-per-invocation, and auto-scaling from zero to thousands of concurrent executions.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Isolation** | Firecracker microVMs (lightweight VMs) | Containers (Docker), traditional VMs | MicroVMs: VM-level security isolation with container-like startup speed (~125ms) |
| **Scaling** | Scale to zero + auto-scale on demand | Always-on instances | Pay only for actual execution time; no idle cost |
| **Cold start** | SnapStart (pre-initialized snapshot) for Java; provisioned concurrency for latency-sensitive | Accept cold starts | Cold starts are Lambda's biggest pain point; SnapStart reduces from seconds to ~200ms |
| **Execution model** | Stateless, ephemeral | Stateful, long-running | Simplifies scaling and fault tolerance; state in external services (DynamoDB, S3) |
| **Timeout** | Max 15 minutes per invocation | Long-running jobs | Forces stateless design; long jobs use Step Functions or ECS |
| **Concurrency** | 1000 concurrent executions per account (adjustable) | Unlimited | Prevents runaway costs; can be increased via support |

### Tradeoff
- ✅ Zero infrastructure management
- ✅ Pay per invocation (100ms granularity) — no idle cost
- ✅ Auto-scales from 0 to 1000+ concurrent in seconds
- ✅ Firecracker gives VM-level security with container-speed starts
- ❌ Cold starts (100ms-10s depending on runtime and VPC)
- ❌ 15 minute timeout — not for long-running jobs
- ❌ Stateless — must use external state stores
- ❌ Vendor lock-in (Lambda-specific patterns)
- ❌ Debugging and local testing are harder

### Interview Use
> "For event-driven processing (image resize on S3 upload, DynamoDB stream processing, API Gateway handlers), I'd use Lambda. It scales from zero, costs nothing when idle, and Firecracker microVMs provide VM-level isolation with ~125ms cold starts. The tradeoff is 15-minute timeout and cold start latency — for latency-sensitive APIs, I'd use provisioned concurrency or ECS."

---

## 5. SQS — Simple Queue Service

**Source**: AWS documentation + re:Invent talks

### Problem
Need a fully managed message queue that decouples producers from consumers. Must handle any throughput without pre-provisioning.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Delivery** | At-least-once (standard) / Exactly-once (FIFO) | Exactly-once everywhere | At-least-once is simpler, higher throughput; FIFO for ordering needs |
| **Ordering** | Standard: best-effort ordering. FIFO: strict per message group | Strict ordering everywhere | Strict ordering limits throughput to 300 msg/s per group |
| **Visibility timeout** | Message invisible for N seconds after read; re-appears if not deleted | Immediate deletion on read | Handles consumer crashes — unprocessed messages reappear automatically |
| **Dead letter queue** | After N failed processing attempts → move to DLQ | Infinite retry | Prevents poison messages from blocking the queue forever |
| **Long polling** | Consumer waits up to 20s for a message (vs empty response) | Short polling (immediate response) | Reduces empty responses; lowers cost and latency |
| **Throughput** | Standard: unlimited. FIFO: 3,000 msg/s with batching | Fixed throughput | Standard queues are virtually unlimited |

### SQS vs Kafka Decision

| Dimension | SQS | Kafka |
|-----------|-----|-------|
| **Model** | Queue (message deleted after processing) | Log (message retained, multiple consumers) |
| **Replay** | ❌ | ✅ |
| **Multiple consumers** | ❌ (one consumer per message) | ✅ (consumer groups) |
| **Ordering** | FIFO queues only (limited throughput) | Per partition |
| **Management** | Fully managed, zero ops | Self-managed or MSK |
| **Best for** | Simple task queue, decoupling | Event streaming, multiple consumers, replay |

### Interview Use
> "For a simple task queue (process uploaded video, send email), I'd use SQS — fully managed, unlimited throughput, dead letter queue for failed messages, and visibility timeout handles consumer crashes. I'd pick SQS over Kafka when I don't need replay, multiple consumers, or ordering. SQS is simpler and cheaper for basic queue patterns."

---

## 6. Kinesis vs Kafka (MSK)

### Design Decisions

| Decision | Kinesis | MSK (Managed Kafka) | When to Choose |
|----------|---------|-------------------|----------------|
| **Management** | Fully managed (no clusters) | Managed but you manage topics, partitions | Kinesis for zero ops; MSK for Kafka compatibility |
| **Throughput** | 1 MB/s per shard | 10-100 MB/s per partition | MSK for high throughput |
| **Retention** | Max 365 days | Unlimited | MSK for long retention / event sourcing |
| **Latency** | 200-500ms | 5-10ms | MSK for low latency |
| **Exactly-once** | Application-level | Native (Kafka transactions) | MSK for exactly-once |
| **Lambda integration** | Native | Via event source mapping | Both integrate, Kinesis is more native |
| **Cost** | Per shard-hour | Per broker-hour | Kinesis cheaper at < 100 MB/s; MSK cheaper at > 200 MB/s |
| **Ecosystem** | AWS-native | Kafka Connect, Kafka Streams, KSQL | MSK for Kafka ecosystem |

### Interview Use
> "I'd choose Kinesis for AWS-native event streaming at moderate scale (< 100 MB/s) with zero ops. I'd choose MSK (Managed Kafka) for higher throughput, exactly-once semantics, or when I need the Kafka ecosystem (Connect, Streams). The crossover point is roughly 100-200 MB/s — below that Kinesis is cheaper, above that MSK wins."

---

## 7. ElastiCache — Managed Redis/Memcached

### Design Decisions

| Decision | Redis | Memcached | When to Choose |
|----------|-------|-----------|----------------|
| **Data structures** | Rich (strings, hashes, sorted sets, lists, streams) | Strings only | Redis for leaderboards, rate limiting, sorted data |
| **Persistence** | Optional (RDB/AOF) | None | Redis if you need data to survive restart |
| **Replication** | Multi-AZ with auto-failover | None | Redis for high availability |
| **Cluster mode** | Redis Cluster (sharding + replication) | Client-side consistent hashing | Redis Cluster for large datasets |
| **Pub/Sub** | Built-in | None | Redis for real-time messaging between services |
| **Cost** | Slightly higher | Slightly lower | Memcached for simple caching at scale |

### Interview Use
> "I'd use ElastiCache Redis for caching + real-time data structures — sorted sets for leaderboards, hashes for rate limiting, pub/sub for WebSocket routing. Multi-AZ deployment with auto-failover for high availability. Memcached only if I need simple key-value caching and want maximum memory efficiency."

---

## 8. CloudFront CDN

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Edge locations** | 400+ globally | Low latency from anywhere |
| **Origin** | S3, ALB, EC2, custom HTTP | Flexible — any HTTP origin works |
| **Caching** | TTL-based + cache behaviors per path pattern | Different content has different caching needs |
| **SSL/TLS** | Free SSL via ACM at the edge | No cost for HTTPS |
| **Lambda@Edge** | Run code at CDN edge locations | Customize responses, A/B testing, auth at edge |
| **Origin Shield** | Centralized cache between edge and origin | Reduces origin load for global traffic |

### Interview Use
> "I'd put CloudFront in front of S3 for media and in front of ALB for API responses. For static content (images, videos): long TTL, content-addressable URLs. For API responses: short TTL or no cache + Origin Shield to reduce origin load. Lambda@Edge for auth token validation at the edge — saves a round trip to the origin."

---

## 9. Route 53 — DNS

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Routing policies** | Simple, Weighted, Latency-based, Failover, Geolocation, Multi-value | Different routing needs |
| **Health checks** | Active health checks on endpoints | Automatic DNS failover |
| **Availability** | 100% SLA (the only AWS service with 100% SLA) | DNS is the foundation — if DNS fails, everything fails |
| **Failover** | Automatic DNS failover based on health checks | Active-passive disaster recovery |

### Key Routing Patterns for System Design

| Pattern | Routing Policy | Use Case |
|---------|---------------|----------|
| **Active-passive failover** | Failover | Primary region down → route to DR region |
| **Multi-region latency** | Latency-based | Route user to nearest region |
| **Blue/green deployment** | Weighted (90/10 → 50/50 → 0/100) | Gradual traffic shift |
| **Geographic compliance** | Geolocation | EU users → EU region (GDPR) |
| **Load distribution** | Weighted | Split traffic across endpoints |

### Interview Use
> "For multi-region architecture, I'd use Route 53 with latency-based routing to send users to their nearest region, plus failover routing with health checks for disaster recovery. If the primary region fails health checks, Route 53 automatically routes to the secondary region within ~60 seconds."

---

## 10. Compute Tradeoffs: ECS vs EKS vs Lambda

| Dimension | Lambda | ECS (Fargate) | EKS |
|-----------|--------|---------------|-----|
| **Management** | Zero (just deploy code) | Low (AWS manages infra) | Medium (K8s complexity) |
| **Scaling** | 0 → 1000+ in seconds | Minutes (task launch) | Minutes (pod scaling) |
| **Cost model** | Per invocation (100ms) | Per vCPU/memory/second | Per EC2 instance + K8s overhead |
| **Idle cost** | Zero | Minimum 1 task running | Control plane + worker nodes |
| **Max duration** | 15 minutes | Unlimited | Unlimited |
| **State** | Stateless | Stateful possible | Stateful (persistent volumes) |
| **Ecosystem** | AWS-native | Docker/AWS | Full Kubernetes ecosystem |
| **Best for** | Event-driven, short tasks, APIs | Web services, microservices | Complex orchestration, multi-cloud |

### Decision Framework
```
Is the workload event-driven and < 15 min?
  → Lambda

Is it a long-running web service?
  → Need Kubernetes ecosystem? → EKS
  → Want simplicity? → ECS Fargate

Is it a batch job?
  → Short (< 15 min): Lambda
  → Long: ECS Fargate task or EKS Job
```

### Interview Use
> "For the API layer, I'd use ECS Fargate — long-running, stateless containers with auto-scaling. For event processing (S3 upload triggers, DynamoDB streams), Lambda is ideal — scales from zero, pay per invocation. I'd avoid EKS unless we need Kubernetes-specific features or multi-cloud portability."

---

## 11. Cell-Based Architecture

**Source**: AWS Builder's Library, re:Invent "Reducing Blast Radius" talks

### Problem
A single deployment of a service serves all customers. A bug or overload in one customer's traffic can take down the service for everyone.

### What AWS Does
Divide the service into **cells** — independent, isolated deployments. Each cell serves a subset of customers. A failure in one cell doesn't affect others.

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Isolation** | Each cell is fully independent (own DB, own cache, own instances) | Blast radius limited to one cell |
| **Cell assignment** | Hash customer ID → cell | Deterministic routing, even distribution |
| **Cell size** | Small enough that losing one cell affects few customers | Limits blast radius |
| **Cross-cell communication** | Avoided — cells are self-contained | No cascading failures between cells |
| **Deployment** | Roll out changes one cell at a time | If deployment breaks cell 1, cells 2-N are unaffected |

### Tradeoff
- ✅ Blast radius: bug/outage affects only 1/N of customers
- ✅ Deployments are safer (canary per cell)
- ✅ Each cell can scale independently
- ❌ More infrastructure to manage (N copies of everything)
- ❌ Cross-cell operations are complex (customer migration between cells)
- ❌ Higher base cost (each cell has minimum resources)

### Interview Use
> "To limit blast radius, I'd use a cell-based architecture — divide the service into N independent cells, each serving a subset of customers. A failure in one cell only affects 1/N of users. AWS uses this pattern internally — S3, DynamoDB, and other services are cell-based. Deploy changes one cell at a time as a canary."

---

## 12. Shuffle Sharding

**Source**: AWS Builder's Library — "Workload Isolation Using Shuffle Sharding"

### Problem
With simple sharding (customer → shard), if a "noisy neighbor" overloads shard 3, ALL customers on shard 3 are affected. How to reduce blast radius further?

### What AWS Does
**Shuffle sharding**: Instead of assigning each customer to 1 shard, assign each customer to a unique combination of 2+ shards. The number of unique combinations grows exponentially.

### How It Works
- 8 shards, each customer assigned to a unique pair of 2 shards
- Number of unique pairs: C(8,2) = 28 virtual shards
- If a bad customer overloads their 2 shards → only customers sharing BOTH shards are affected
- Probability of two random customers sharing both shards: 1/28 ≈ 3.6%
- vs simple sharding (1 of 8): 1/8 = 12.5% chance of being co-located

### Tradeoff
- ✅ Exponentially reduces blast radius (C(N,K) combinations vs N shards)
- ✅ Simple to implement — just pick K shards per customer deterministically
- ❌ Customer uses K× the resources (spread across K shards)
- ❌ More complex routing logic
- ❌ Monitoring per-customer resource usage is harder

### Interview Use
> "To isolate noisy neighbors, I'd use shuffle sharding — assign each customer to a unique pair of 2 (out of 8) shards. This creates C(8,2) = 28 virtual isolation groups instead of 8. A bad customer only affects the ~3.6% of customers sharing both shards, vs 12.5% with simple sharding. AWS uses this in Route 53, CloudFront, and other services."

---

## 13. Retry & Exponential Backoff

**Source**: AWS Builder's Library — "Timeouts, Retries, and Backoff with Jitter"

### Problem
When a downstream service is overloaded, aggressive retries make it worse (retry storm). Need a retry strategy that recovers from transient errors without overwhelming the system.

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Backoff** | Exponential (1s, 2s, 4s, 8s...) | Gives the failing service time to recover |
| **Jitter** | Full jitter (random between 0 and max backoff) | Prevents synchronized retry storms (thundering herd) |
| **Max retries** | 3-5 attempts | Bound the total retry time; don't retry forever |
| **Circuit breaker** | Stop retrying entirely when failure rate > threshold | Prevent wasting resources on a dead service |
| **Idempotency** | All retryable operations must be idempotent | Retrying a non-idempotent operation causes data corruption |
| **Timeout** | Set explicit timeouts on every call | Don't wait forever; fail fast |

### The Jitter Formula
```
Without jitter:   sleep = min(max_backoff, base × 2^attempt)
With full jitter:  sleep = random(0, min(max_backoff, base × 2^attempt))
With equal jitter: sleep = min(max_backoff, base × 2^attempt) / 2 + random(0, same / 2)
```

**Full jitter is recommended** by AWS — it spreads retries evenly across time, preventing synchronized storms.

### Interview Use
> "For retries, I'd use exponential backoff with full jitter — base delay × 2^attempt, but randomized between 0 and the calculated delay. This prevents a retry storm where all clients retry at the same time. Combined with a circuit breaker (stop retrying after N failures) and idempotency keys (safe to retry). AWS Builder's Library recommends this pattern for all service-to-service calls."

---

## 14. DynamoDB Streams + Lambda — Event-Driven Pattern

### Problem
Need to react to data changes in DynamoDB — update a cache, sync to Elasticsearch, trigger a workflow — without polling.

### How It Works
**DynamoDB Streams** captures every INSERT/UPDATE/DELETE as an ordered stream of events. Lambda functions consume the stream with exactly-once processing semantics.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **CDC mechanism** | DynamoDB Streams (built-in) | Polling DynamoDB, application-level publishing | Zero additional infrastructure; guaranteed to capture all changes |
| **Consumer** | Lambda (event source mapping) | EC2/ECS consumer | Serverless, auto-scales with stream volume |
| **Processing** | Per-shard, in-order | Unordered | Changes to the same item are processed in order |
| **Retention** | 24 hours | Longer | 24h is enough for near-real-time processing; use Kinesis for longer |

### Common Patterns

| Pattern | How | Example |
|---------|-----|---------|
| **Cache invalidation** | DynamoDB Stream → Lambda → delete from ElastiCache | User profile update → cache refresh |
| **Search sync** | DynamoDB Stream → Lambda → index in Elasticsearch | Product update → search index update |
| **Cross-region replication** | DynamoDB Global Tables (uses Streams internally) | Active-active multi-region |
| **Event notification** | DynamoDB Stream → Lambda → SNS/SQS | Order placed → send confirmation email |
| **Aggregation** | DynamoDB Stream → Lambda → update aggregate in another DynamoDB table | Real-time analytics counters |

### Interview Use
> "For reacting to database changes (invalidate cache, sync search index, trigger notifications), I'd use DynamoDB Streams + Lambda. Every INSERT/UPDATE/DELETE is captured as an event, and Lambda processes them in order per partition key. This is the AWS-native CDC (Change Data Capture) pattern — no polling, no missed changes."

---

## 15. Amazon's Service-Oriented Architecture

**Source**: Werner Vogels talks, "Amazon's Service-Oriented Architecture" (the original microservices before "microservices")

### Problem
Amazon.com's monolithic application (called "Obidos") became impossible to deploy and scale. A change to the product catalog could break the shopping cart.

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Architecture** | Service-oriented (independent services with APIs) | Monolith | Each team owns a service; deploy independently; scale independently |
| **Communication** | APIs only (no shared databases) | Shared databases, direct DB access | "You build it, you run it" — owning the API means owning the data |
| **Team structure** | Two-pizza teams (6-8 people per service) | Large teams on shared codebase | Small teams move faster, have clear ownership |
| **Data ownership** | Each service owns its data (no shared DB) | Centralized database | Services can evolve their schema without coordinating with others |
| **Deployment** | Independent, per-service | Coordinated releases | One service can deploy without affecting others |

### "You Build It, You Run It"
The team that writes the code also operates it in production, including being on-call. This creates accountability — if your code breaks at 3 AM, YOU get paged, not a separate ops team.

### Tradeoff
- ✅ Teams move independently — Amazon deploys thousands of times per day
- ✅ Services scale independently (search scales differently from checkout)
- ✅ "You build it, you run it" creates accountability
- ❌ Distributed transactions are hard (no cross-service DB transactions)
- ❌ Debugging across 100+ services requires distributed tracing
- ❌ Data consistency is eventual (each service owns its data)

### Interview Use
> "Amazon decomposed their monolith into services with a strict rule: no shared databases. Each service owns its data and exposes it only via APIs. The 'two-pizza team' structure — 6-8 people own one service end-to-end. This is the origin of microservices. The key lesson: data ownership per service eliminates coupling."

---

## 16. S3 Consistency — From Eventual to Strong

**Source**: AWS re:Invent 2020 — "Amazon S3 Now Delivers Strong Read-After-Write Consistency"

### Problem
For 14 years, S3 was eventually consistent for overwrites and deletes. PUT a new version → GET might return the old version for a few seconds. This caused bugs in data pipelines, ML training, and analytics.

### What AWS Did
Made S3 strongly consistent for ALL operations — without any performance or cost impact. A massive engineering achievement.

### How They Did It
- **Metadata layer**: Made the S3 metadata index strongly consistent using a custom cache coherence protocol
- **No performance impact**: Reads are still served from cache; the cache is now strongly consistent with the index
- **No API changes**: Existing applications automatically get strong consistency
- **No cost increase**: Same price as before

### Why This Matters for Interviews
- Shows that strong consistency doesn't have to mean slow
- The key insight: make the **metadata** strongly consistent, and the **data** (which is immutable in S3 — objects are replaced, not modified) is naturally consistent
- Demonstrates that "eventual consistency" is often a choice, not a fundamental limitation

### Interview Use
> "S3 achieved strong consistency without sacrificing performance by making the metadata layer strongly consistent while keeping data immutable. This is a key insight: if your data is immutable (append-only or replace-only), strong consistency is primarily a metadata problem. This is relevant for any system where we store immutable blobs with a separate metadata index."

---

## 17. AWS Nitro System

**Source**: AWS re:Invent — "The Nitro Story"

### Problem
Traditional hypervisors (Xen) consume up to 30% of a host's CPU for virtualization overhead (network, storage, management). Customers pay for resources they can't use.

### What AWS Built
**Nitro** — offload all virtualization to purpose-built hardware (custom ASICs). The hypervisor overhead drops to near zero.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Network** | Custom Nitro NIC card (hardware) | Software-defined networking (Xen) | Hardware processes network packets — zero host CPU usage for networking |
| **Storage** | Custom Nitro storage card | Software EBS driver | Hardware handles EBS I/O — zero host CPU for storage |
| **Security** | Hardware root of trust (Nitro Security Chip) | Software-based security | Hardware can't be modified by software — even AWS employees can't access customer data on a running instance |
| **Hypervisor** | Minimal KVM-based Nitro hypervisor | Full Xen hypervisor | Nitro hypervisor is tiny — almost all resources go to the customer VM |

### Tradeoff
- ✅ Near-zero virtualization overhead (customers get ~100% of the hardware)
- ✅ Better security (hardware root of trust)
- ✅ Enables bare-metal instances (no hypervisor at all)
- ✅ Enables Firecracker (Lambda/Fargate) with microsecond-level isolation
- ❌ Massive R&D investment in custom silicon
- ❌ Hardware iteration cycles are slower than software

### Interview Use
> "AWS's Nitro system offloads virtualization (networking, storage, security) to custom hardware. This means near-zero overhead — customers get almost 100% of the host's CPU/memory. More importantly, Nitro's hardware root of trust means even AWS operators can't access customer data on a running instance. This is relevant when discussing security and performance in cloud architectures."

---

## 18. Amazon Timestream — Time-Series

### Problem
Time-series data (IoT sensors, metrics, logs) has a specific access pattern: write-heavy with time-ordered reads, queries over time ranges, and data that becomes less valuable as it ages.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Tiered: in-memory (recent) → magnetic (historical) | Single tier | Recent data queried frequently (hot); historical data rarely (cold) |
| **Auto-tiering** | Automatic based on age | Manual lifecycle rules | No operational overhead; data moves to cheaper storage automatically |
| **Query** | SQL (time-series extensions) | Custom query language | Familiar to developers; BI tools work natively |
| **Ingestion** | Schemaless (auto-detects dimensions) | Pre-defined schema | IoT data evolves; schemaless handles new sensors without migrations |
| **Compression** | Time-series-specific compression | General compression | 10-20× compression for time-series data |

### Interview Use
> "For time-series data (metrics, IoT), I'd use a tiered storage strategy — recent data in memory for fast queries, older data on cheaper storage. Timestream automates this, but the pattern works with any time-series DB: keep hot data fast, archive cold data cheap. Time-series-specific compression gives 10-20× savings."

---

## 19. Step Functions — Workflow Orchestration

### Problem
Complex workflows (order processing, ETL pipelines, ML training) involve multiple steps with branching, error handling, retries, and human approval. Coding this in Lambda functions is fragile.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Model** | State machine (ASL — Amazon States Language) | Code-based workflows (Temporal/Cadence) | Visual, declarative; good for non-engineers to understand |
| **Execution** | Managed, durable state machine | Self-managed workflow engine | Zero infrastructure; state is persisted automatically |
| **Integration** | Direct AWS service integrations (200+ services) | Lambda wrappers for everything | Call DynamoDB, SQS, ECS directly — no Lambda middleman |
| **Types** | Standard (durable, up to 1 year) + Express (high-volume, 5 min) | Single type | Standard for long workflows; Express for high-throughput event processing |
| **Error handling** | Built-in retry, catch, fallback states | Application-level try/catch | Declarative error handling — easier to reason about |

### Step Functions vs Temporal/Cadence

| Dimension | Step Functions | Temporal/Cadence |
|-----------|---------------|-----------------|
| **Model** | Declarative (JSON state machine) | Imperative (code) |
| **Flexibility** | Limited to state machine constructs | Full programming language |
| **Management** | Fully managed | Self-managed cluster |
| **AWS integration** | Native (200+ services) | Via activities (you code the integration) |
| **Best for** | AWS-native workflows, visual workflows | Complex business logic, non-AWS environments |

### Interview Use
> "For a multi-step workflow (order processing: validate → charge → ship → notify), I'd use Step Functions for AWS-native orchestration or Temporal for complex business logic. Step Functions gives visual state machines with built-in retry and error handling. The tradeoff vs Temporal: less flexible (declarative JSON vs code) but zero operational overhead."

---

## 20. Multi-AZ & Multi-Region Patterns

### AZ vs Region

| Concept | What | Latency Between | Use Case |
|---------|------|-----------------|----------|
| **Availability Zone (AZ)** | Isolated data center(s) within a region | < 2ms | High availability within a region |
| **Region** | Geographic area with 2-6 AZs | 50-200ms | Disaster recovery, data residency, low global latency |

### Multi-AZ Pattern (Most Common)

```
Region us-east-1:
  AZ-a: [Primary DB] [App Server] [Redis]
  AZ-b: [Standby DB] [App Server] [Redis Replica]
  AZ-c:              [App Server] [Redis Replica]

ALB distributes across all AZs.
DB failover: Primary → Standby (~30s for Aurora).
Redis: Multi-AZ with auto-failover.
```

**When**: Standard HA. Protects against single AZ failure. Adds < 2ms latency.

### Multi-Region Pattern (Active-Passive)

```
us-east-1 (PRIMARY):        eu-west-1 (DR):
  [ALB] → [App] → [DB]       [ALB] → [App] → [DB Replica]
  
Route 53 Failover routing: 
  Healthy → us-east-1
  Unhealthy → eu-west-1

DB: Async cross-region replication (seconds of lag)
S3: Cross-region replication (CRR)
```

**When**: Disaster recovery. RPO: seconds (replication lag). RTO: minutes (DNS failover + DB promotion).

### Multi-Region Pattern (Active-Active)

```
us-east-1 (ACTIVE):          eu-west-1 (ACTIVE):
  [ALB] → [App] → [DynamoDB]   [ALB] → [App] → [DynamoDB]
  
Route 53 Latency-based routing: users → nearest region
DynamoDB Global Tables: active-active replication
```

**When**: Global low latency + high availability. Both regions serve reads AND writes. DynamoDB Global Tables or Aurora Global Database handles replication.

**Tradeoff**: Active-active is hardest — conflict resolution needed for concurrent writes to different regions.

### Interview Use
> "I'd start with Multi-AZ deployment (standard for HA — protects against AZ failure with < 2ms latency penalty). For disaster recovery, add a passive region with async replication and Route 53 failover. For global low-latency, go active-active with DynamoDB Global Tables — both regions serve reads and writes, with last-writer-wins conflict resolution."

---

## 🎯 Quick Reference: AWS Service Selection for System Design

### Database
| Need | AWS Service | Why |
|------|-------------|-----|
| Relational + HA | Aurora | 6-copy, 3-AZ, ~30s failover |
| Key-value at scale | DynamoDB | Single-digit ms, unlimited scale, fully managed |
| Cache | ElastiCache Redis | Sub-ms, rich data structures |
| Time-series | Timestream | Auto-tiering, time-series compression |
| Search | OpenSearch (Elasticsearch) | Full-text search, log analytics |
| Graph | Neptune | Social graph, fraud detection |

### Compute
| Need | AWS Service | Why |
|------|-------------|-----|
| Event-driven, short tasks | Lambda | Scale to zero, pay per invocation |
| Long-running services | ECS Fargate | Managed containers, auto-scaling |
| Kubernetes needed | EKS | Full K8s, multi-cloud portability |
| Batch processing | Lambda / ECS / EMR | Depends on duration and framework |

### Messaging / Streaming
| Need | AWS Service | Why |
|------|-------------|-----|
| Simple task queue | SQS | Fully managed, unlimited throughput, DLQ |
| Event streaming (moderate) | Kinesis | AWS-native, zero ops |
| Event streaming (high throughput) | MSK (Kafka) | Kafka ecosystem, exactly-once |
| Pub/sub notifications | SNS | Fan-out to SQS, Lambda, email |

### Storage
| Need | AWS Service | Why |
|------|-------------|-----|
| Object/blob storage | S3 | 11 nines durability, unlimited |
| CDN | CloudFront | 400+ edge locations, Lambda@Edge |
| File system | EFS | Shared filesystem for containers |

### Architecture Patterns
| Pattern | AWS Implementation |
|---------|-------------------|
| Cell-based | Independent stacks per cell, Route 53 routing |
| Circuit breaker | Application-level (Hystrix, Resilience4j) |
| Event-driven | DynamoDB Streams → Lambda, S3 Events → Lambda |
| Workflow | Step Functions (simple) or ECS + Temporal (complex) |
| Multi-region | Route 53 + Aurora Global DB or DynamoDB Global Tables |

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 20 design decisions across compute, storage, database, architecture patterns  
**Status**: Complete & Interview-Ready ✅
