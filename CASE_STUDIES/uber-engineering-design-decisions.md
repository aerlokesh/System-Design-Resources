# Uber Engineering — Design Decisions & Tradeoffs from Their Blog

> **Purpose**: A system design interview reference documenting real-world architectural decisions Uber made, sourced from their engineering blog. Each entry covers: **the problem, what they chose, why, the tradeoff, and how to use it in an interview.**
>
> **Blog**: eng.uber.com (Uber Engineering Blog)

---

## Table of Contents

1. [Ringpop — Consistent Hashing for Service Discovery](#1-ringpop)
2. [Schemaless — Uber's Custom Datastore (MySQL + Append-Only)](#2-schemaless)
3. [Cherami — Durable Task Queue](#3-cherami)
4. [Kafka → Apache Hudi — Real-Time Data Lake](#4-kafka-to-hudi)
5. [H3 — Hexagonal Hierarchical Geospatial Index](#5-h3-geospatial-index)
6. [MVCC + Google S2 — Trip Matching (Dispatch)](#6-trip-matching-dispatch)
7. [Cadence — Microservice Workflow Orchestration](#7-cadence-workflow-orchestration)
8. [Cassandra → DocStore — Moving Off Cassandra](#8-moving-off-cassandra)
9. [MySQL + Schemaless → CockroachDB — NewSQL Migration](#9-cockroachdb-migration)
10. [Peloton — Unified Resource Scheduler](#10-peloton-resource-scheduler)
11. [AresDB — GPU-Powered Real-Time Analytics](#11-aresdb-real-time-analytics)
12. [Uber's Microservice Architecture — Domain-Oriented Design](#12-domain-oriented-microservices)
13. [Rate Limiting — Distributed Rate Limiter at Scale](#13-distributed-rate-limiting)
14. [Observability — M3 Metrics Platform](#14-m3-metrics-platform)
15. [Real-Time ETA Prediction — ML at Scale](#15-real-time-eta)
16. [Payment System — Double-Entry Bookkeeping](#16-payment-system)
17. [Geofence Service — Point-in-Polygon at Scale](#17-geofence-service)
18. [Push Notifications — Delivering Billions](#18-push-notification-platform)
19. [Uber Eats — Order Management & Dispatch](#19-uber-eats-order-management)
20. [Database Reliability — Disaster Recovery & Multi-Region](#20-database-reliability)

---

## 1. Ringpop

**Blog Post**: "Ringpop: Scalable, Fault-tolerant Application-Layer Sharding"

### Problem
Uber needed application-level sharding without a centralized coordinator. Traditional load balancers distribute requests randomly — but some operations (like all trip data for a rider) must go to the same server.

### What They Built
**Ringpop** — a library that implements consistent hashing with a SWIM gossip protocol for membership. Each service instance joins a hash ring. Requests are routed to the node that owns the hash range for the given key.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Membership protocol** | SWIM gossip | ZooKeeper centralized | Gossip is decentralized — no single point of failure, O(log N) convergence |
| **Hash ring** | Consistent hashing with virtual nodes | Modular hashing | Adding/removing nodes only remaps ~1/N of keys |
| **Coordination** | No coordinator (peer-to-peer) | ZooKeeper / etcd | Eliminated dependency on external coordination service |
| **Failure detection** | SWIM protocol (ping, ping-req, suspect) | Heartbeat to central server | Distributed detection, tolerates partial network failure |

### Tradeoff
- ✅ No single point of failure, scales horizontally
- ✅ Sub-second failure detection and rebalancing
- ❌ Eventual consistency in membership view (nodes may briefly disagree on ring state)
- ❌ Virtual node count tuning needed for balanced distribution

### Interview Use
> "For stateful routing (all requests for one user go to the same server), I'd use consistent hashing with a gossip-based membership protocol — similar to Uber's Ringpop. Each server joins a hash ring, and request routing is determined by hashing the key. No centralized coordinator needed."

---

## 2. Schemaless

**Blog Post**: "Designing Schemaless, Uber Engineering's Scalable Datastore Using MySQL"

### Problem
Uber's trip data was growing exponentially. PostgreSQL (their original choice) couldn't scale horizontally. They needed a scalable datastore that could handle append-heavy workloads with flexible schema.

### What They Built
**Schemaless** — an append-only, immutable datastore built on top of MySQL. Data is stored as JSON blobs in MySQL with a thin indexing layer. Think of it as "Cassandra's write model on top of MySQL's storage."

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage engine** | MySQL (InnoDB) | Cassandra, custom storage | MySQL is battle-tested, understood by all engineers, great tooling |
| **Data model** | Append-only (immutable cells) | Mutable rows | Append-only simplifies replication, conflict resolution, and debugging |
| **Schema** | Schemaless (JSON blobs) | Fixed schema (SQL DDL) | Schema changes at Uber's scale are expensive; JSON allows evolution |
| **Indexing** | Secondary indexes via separate MySQL tables | Elasticsearch | Keep everything in MySQL for operational simplicity |
| **Sharding** | Application-level sharding by entity key | MySQL Vitess, CockroachDB | Full control over shard placement, no external dependency |
| **Consistency** | Eventual (async replication) | Strong (synchronous) | Cross-datacenter strong consistency too expensive at their scale |

### Tradeoff
- ✅ Built on proven MySQL — understood by every engineer
- ✅ Append-only simplifies debugging and audit trails
- ✅ Horizontal scaling via application-level sharding
- ❌ No native JOINs or complex queries (key-based lookup only)
- ❌ Application must handle sharding logic
- ❌ Append-only increases storage (no in-place updates)

### Interview Use
> "For a write-heavy system that needs horizontal scaling, I'd consider an append-only data model on top of MySQL — similar to Uber's Schemaless. Data is stored as immutable JSON cells, sharded by entity key. This gives us MySQL's reliability with Cassandra-like write scalability."

---

## 3. Cherami

**Blog Post**: "Cherami: Uber Engineering's Durable Distributed Task Queue"

### Problem
Uber needed a durable task queue for asynchronous processing (send notification, process payment, generate receipt). SQS wasn't available in all their deployment environments. Kafka is a log, not a queue (messages aren't deleted after consumption).

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Build vs Buy** | Built Cherami | Needed cross-datacenter support, Kafka didn't fit queue semantics |
| **Delivery model** | Competing consumers (like SQS) | Each message consumed by exactly one worker |
| **Durability** | Write-ahead log + replication | Zero message loss for payment/notification processing |
| **Ordering** | No strict ordering | Ordering not required for task queues; enables higher throughput |
| **Dead-letter queue** | Built-in DLQ | Failed messages retried N times, then moved to DLQ |

### Tradeoff
- ✅ Durable task queue with exactly-once-delivery semantics
- ✅ Cross-datacenter replication
- ❌ Eventually deprecated in favor of Kafka (operational cost of maintaining a custom system)
- ❌ Building a distributed queue is hard — took significant engineering investment

### Lesson
> "Uber built Cherami because Kafka's log model didn't fit their queue needs. But maintaining a custom distributed system is expensive — they later migrated many workloads to Kafka with consumer-side queue semantics. **In an interview, favor Kafka or SQS unless you have a very specific reason to build custom.**"

---

## 4. Kafka to Hudi

**Blog Post**: "Uber's Big Data Platform: 100+ Petabytes with Minute Latency"

### Problem
Uber generates 100+ PB of data. They needed to get real-time data from their operational databases into their analytics data lake (HDFS/S3) with minute-level freshness.

### What They Built
An ingestion pipeline: **MySQL → Debezium (CDC) → Kafka → Apache Hudi → HDFS/S3 → Presto/Spark**.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **CDC tool** | Debezium (MySQL binlog) | Periodic batch dumps | Real-time: minute-level freshness vs hour-level |
| **Event bus** | Kafka | Direct DB → HDFS copy | Kafka buffers, handles backpressure, supports multiple consumers |
| **Lake format** | Apache Hudi (built at Uber) | Delta Lake, Iceberg, raw Parquet | Hudi supports upserts in data lake (ACID on HDFS) — critical for CDC |
| **Query engine** | Presto (interactive) + Spark (batch) | Single engine | Presto for fast queries, Spark for heavy ETL |
| **Storage** | HDFS → migrating to S3 | Pure S3 | HDFS for performance, S3 for cost/durability |

### Tradeoff
- ✅ Minute-level freshness (CDC + Kafka + Hudi)
- ✅ Upserts in data lake (Hudi) — not just append
- ✅ Decoupled: many consumers read from Kafka independently
- ❌ Complex pipeline with many components to operate
- ❌ Hudi compaction overhead (merge-on-read vs copy-on-write tradeoff)

### Interview Use
> "For a real-time data pipeline, I'd use CDC (Debezium) to capture database changes, publish to Kafka, and sink to a data lake format like Hudi that supports upserts. This gives minute-level freshness for analytics dashboards — similar to how Uber ingests 100+ PB."

---

## 5. H3 Geospatial Index

**Blog Post**: "H3: Uber's Hexagonal Hierarchical Spatial Index"

### Problem
Uber needs to answer geospatial questions fast: "Which drivers are near this rider?", "What is the surge pricing for this area?", "How many trips started in this zone?" Traditional lat/lng queries are slow.

### What They Built
**H3** — a hexagonal grid system that divides the Earth into hierarchical hexagons at multiple resolutions. Open-sourced by Uber.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Grid shape** | Hexagons | Squares (S2), triangles | Hexagons have uniform adjacency (6 equidistant neighbors) — better for distance calculations |
| **Hierarchy** | 16 resolution levels (0-15) | Fixed resolution | Multi-resolution: coarse for city-level, fine for block-level |
| **vs Google S2** | H3 for proximity queries | S2 for general geospatial | H3's hexagons have equal area and uniform distances — better for "nearest driver" |
| **Indexing** | H3 cell ID as partition/shard key | Lat/lng with geospatial index | O(1) lookup: hash lat/lng to H3 cell → query that cell + neighbors |

### Tradeoff
- ✅ O(1) proximity queries (hash to cell, check neighbors)
- ✅ Uniform distance between neighbors (unlike square grids)
- ✅ Multi-resolution for different use cases
- ❌ More complex than simple geohashing
- ❌ Pentagon cells at icosahedron vertices (12 out of billions — edge case)

### Interview Use
> "For proximity queries ('find nearby drivers'), I'd use a hierarchical spatial index like Uber's H3 or Google's S2. The key insight is converting lat/lng to a cell ID, then querying that cell and its neighbors. This turns a 2D spatial query into a simple key lookup — O(1) instead of scanning all entities."

---

## 6. Trip Matching (Dispatch)

**Blog Post**: "How Uber Matches Riders with Drivers"

### Problem
When a rider requests a trip, Uber must find the best available driver within seconds. At peak, this means matching millions of riders to millions of drivers with constraints (ETA, driver rating, vehicle type, surge pricing).

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Driver location tracking** | Drivers send GPS every 4 seconds → stored in memory-based geospatial index (H3) | Real-time positions needed; DB too slow |
| **Matching algorithm** | Supply-demand optimization (batch matching every few seconds) | Better than greedy first-match: considers global optimum |
| **Geospatial filter** | H3 ring expansion: check cell → 1st ring → 2nd ring until enough candidates | Efficient proximity search without scanning all drivers |
| **ETA computation** | ML model (routing graph + real-time traffic) | Historical averages too inaccurate during peak |
| **Consistency** | Optimistic (driver can receive multiple offers, first ACK wins) | Pessimistic locking too slow for real-time matching |

### Tradeoff
- ✅ Batch matching finds globally better assignments (vs greedy)
- ✅ H3 ring expansion is efficient for proximity
- ❌ Batch introduces small latency (1-2 seconds) vs instant greedy match
- ❌ Race condition possible (two riders matched to same driver) — resolved by first-ACK-wins

### Interview Use
> "For real-time matching (drivers to riders), I'd use an in-memory geospatial index with hexagonal cells. When a ride is requested, expand outward ring-by-ring from the rider's cell until enough candidate drivers are found. Then run a batch optimization every 2 seconds to find the globally optimal matching — better than greedy first-match."

---

## 7. Cadence (Temporal)

**Blog Post**: "Cadence: The Workflow Engine behind Uber"

### Problem
Uber has complex multi-step workflows: trip lifecycle (request → match → pickup → ride → dropoff → payment → receipt), food delivery, driver onboarding. Each step can fail and needs retry, timeout, and compensation.

### What They Built
**Cadence** (later evolved into **Temporal** after the creators left Uber) — a distributed workflow engine.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Orchestration model** | Code-based workflows (write workflow in Go/Java) | BPMN/state machines, saga pattern | Developers already know how to code; code is testable, debuggable |
| **State management** | Event-sourced (every workflow step is an event) | Database state snapshots | Enables replay, debugging, audit trail |
| **Execution guarantees** | Activities execute at-least-once + idempotency | Exactly-once | At-least-once is simpler to implement; activities handle idempotency |
| **Timer/scheduling** | Durable timers (survive process restart) | Cron jobs, scheduled tasks | Timers are part of the workflow — no external scheduler needed |
| **Scalability** | Shard by workflow ID | Single scheduler | Each shard independently processes its workflows |

### Tradeoff
- ✅ Complex workflows expressed as simple code (not state machines)
- ✅ Durable: workflow state survives any crash — replays from event history
- ✅ Built-in retry, timeout, compensation
- ❌ Learning curve (workflow-as-code paradigm)
- ❌ Operational complexity (need to run Cadence/Temporal cluster)

### Interview Use
> "For multi-step workflows (trip lifecycle, order processing), I'd use a workflow orchestration engine like Cadence/Temporal. Each workflow is written as code with durable state — if a step fails, the engine retries it. The entire workflow history is event-sourced, so we can replay for debugging. This replaces fragile state machines and scattered retry logic."

---

## 8. Moving Off Cassandra

**Blog Post**: "Uber's Migration from Cassandra to Uber's Docstore"

### Problem
Uber was a heavy Cassandra user but ran into operational challenges: compaction storms (CPU spikes during background merging), read amplification (reading from multiple SSTables), and operational complexity.

### What They Did
Built **Docstore** — a document-oriented store on top of MySQL (similar evolution to Schemaless).

### Design Decisions

| Decision | Chose (Docstore) | Left Behind (Cassandra) | Why |
|----------|-----------------|------------------------|-----|
| **Storage engine** | MySQL (InnoDB B-tree) | Cassandra (LSM tree) | B-tree better for read-heavy workloads; no compaction storms |
| **Consistency** | Tunable (strong available) | Eventual (by default) | Some workloads needed strong consistency |
| **Query flexibility** | Document queries + secondary indexes | Primary key lookups only | Applications needed more query patterns |
| **Operations** | MySQL expertise abundant | Cassandra expertise scarce | MySQL is well-understood; easier to hire/train |

### Tradeoff
- ✅ No compaction storms (B-tree vs LSM)
- ✅ Better read performance (single file lookup vs multi-SSTable scan)
- ✅ Operational familiarity (MySQL)
- ❌ Write throughput lower than Cassandra (B-tree writes slower than LSM appends)
- ❌ Sharding must be handled at application layer

### Interview Use
> "The choice between LSM-tree (Cassandra, RocksDB) and B-tree (MySQL, PostgreSQL) databases depends on the workload. LSM trees excel at writes but suffer from compaction overhead and read amplification. B-trees have consistent read performance but slower writes. Uber moved from Cassandra to MySQL-based DocStore because their workloads became more read-heavy as they scaled."

---

## 9. CockroachDB Migration

**Blog Post**: "How Uber Migrated to CockroachDB for Global Scale"

### Problem
Uber needed a globally distributed, strongly consistent database for their financial systems (payments, billing). MySQL sharding required manual shard management and couldn't provide cross-shard transactions.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Database** | CockroachDB | Spanner, TiDB, Vitess | CockroachDB: Spanner-compatible, open-source, runs anywhere (not locked to GCP) |
| **Consistency** | Serializable isolation | Eventual consistency | Financial data requires strong consistency — no double-charges |
| **Distribution** | Multi-region active-active | Single-region with failover | Uber operates globally; need writes in every region |
| **Migration** | Dual-write + shadow reads | Big-bang cutover | Risk-free migration: write to both, compare results, switch reads |

### Tradeoff
- ✅ Distributed transactions across regions (no more manual sharding)
- ✅ Serializable isolation (financial correctness)
- ✅ Automatic rebalancing (no manual shard management)
- ❌ Higher write latency (consensus across regions: ~100-200ms)
- ❌ Newer technology with smaller community than MySQL
- ❌ Cost (consensus overhead uses more resources)

### Interview Use
> "For financial systems requiring global strong consistency, I'd consider a NewSQL database like CockroachDB or Spanner. They provide distributed transactions with serializable isolation — eliminating the complexity of manual sharding and two-phase commits. The tradeoff is higher write latency (~100ms for cross-region consensus) vs MySQL's ~5ms local writes."

---

## 10. Peloton Resource Scheduler

**Blog Post**: "Peloton: Uber's Unified Resource Scheduler"

### Problem
Uber runs millions of containers across their fleet. They needed a scheduler that handles both stateless services (microservices) and batch jobs (ML training, data pipelines) on the same cluster without resource waste.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Unified scheduler** | One scheduler for all workloads | Separate schedulers (Kubernetes + YARN) | Better resource utilization — stateless uses resources at night, batch during off-peak |
| **Scheduling** | Hierarchical: resource manager → placement engine | Flat scheduler | Resource manager handles admission; placement engine handles bin-packing |
| **Preemption** | Batch jobs can be preempted by stateless services | No preemption | Stateless services have strict SLAs; batch can be restarted |
| **Job types** | Stateless, stateful, batch, daemon | Separate systems | Single platform reduces operational overhead |

### Interview Use
> "The key tradeoff in resource scheduling is unified vs separate schedulers. Uber's Peloton unified all workloads on one scheduler — batch jobs can use spare capacity from stateless services at night, improving utilization from ~50% to ~80%. The tradeoff is complexity in the scheduler's preemption and priority logic."

---

## 11. AresDB — GPU Real-Time Analytics

**Blog Post**: "AresDB: Uber's GPU-Powered Open Source, Real-Time Analytics Engine"

### Problem
Uber needed sub-second analytical queries on real-time data (last-hour trip metrics, surge pricing computation). Traditional columnar stores (ClickHouse, Druid) run on CPU — too slow for their latency requirements at scale.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Computation** | GPU (CUDA) | CPU (ClickHouse, Druid) | GPU parallelism: 10,000+ cores for aggregation — 10-100× faster |
| **Storage** | Columnar in GPU memory | Disk-based columnar | Sub-millisecond data access for hot data |
| **Ingestion** | Real-time (Kafka → AresDB) | Batch loading | Minute-level freshness needed for surge pricing |
| **Query model** | SQL-like aggregation queries | Full SQL | Focused on aggregation — not general-purpose SQL |

### Tradeoff
- ✅ Sub-second queries on real-time data (10-100× faster than CPU)
- ✅ Handles Uber's aggregation workloads efficiently
- ❌ GPU memory is expensive and limited (can't store years of data)
- ❌ Only suitable for aggregation queries (not JOINs, not general SQL)
- ❌ Niche technology — harder to hire, smaller ecosystem

### Interview Use
> "For sub-second analytical queries on real-time data, GPU-accelerated analytics (like Uber's AresDB) can provide 10-100× speedup over CPU-based engines. However, GPU memory is limited and expensive — this is only viable for hot data aggregation, not general-purpose queries."

---

## 12. Domain-Oriented Microservices

**Blog Post**: "Introducing Domain-Oriented Microservice Architecture (DOMA)"

### Problem
Uber's microservice count exploded from a monolith to 4000+ services. The result: dependency hell, cascading failures, unclear ownership, and extremely complex debugging.

### What They Did
Introduced **DOMA** — grouping microservices into "domains" (like mini-monoliths) with clear interfaces between domains.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Organization** | Domain-oriented (group services into domains) | Pure microservices (each service independent) | Reduces inter-service calls; clear ownership boundaries |
| **Communication** | Within domain: direct calls. Between domains: gateway/API | All direct calls | Reduces cross-domain coupling |
| **Data** | Domain owns its data (no shared databases) | Shared database | Prevents coupling; each domain can evolve independently |
| **Platform layer** | Shared infrastructure services (gateway, observability, deployment) | Each domain builds its own | Avoid reinventing infrastructure; focus on business logic |

### Tradeoff
- ✅ Reduced complexity (from 4000 random services to ~50 domains with clear boundaries)
- ✅ Clear ownership (domain team owns all services in their domain)
- ✅ Reduced cascading failures (domain gateways act as circuit breakers)
- ❌ Added latency for cross-domain calls (extra gateway hop)
- ❌ Migration from flat microservices to DOMA is a multi-year effort

### Interview Use
> "For a large microservice architecture, I'd organize services into domains — each domain has a clear boundary, owns its data, and exposes a gateway to other domains. This is Uber's DOMA pattern: instead of 4000 random services calling each other, you have ~50 domains with well-defined interfaces. It reduces coupling and makes the system easier to reason about."

---

## 13. Distributed Rate Limiting

**Blog Post**: "Rate Limiter at Scale: How Uber Manages Billions of Requests"

### Problem
Uber processes billions of API requests daily. Need to protect services from abuse and cascading failures with per-service, per-user, and per-endpoint rate limits.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Algorithm** | Token bucket (per-key) | Sliding window, leaky bucket | Token bucket allows bursts (natural for human behavior) while enforcing average rate |
| **Storage** | Local counters + periodic sync to Redis | Pure Redis | Reduces Redis round-trips; local counter handles 99% of requests |
| **Enforcement** | Application-side library (sidecar) | Centralized rate limiter service | Lower latency (no network hop); more resilient (works if central service is down) |
| **Configuration** | Dynamic (hot-reload from config service) | Static config files | Can adjust limits in real-time during incidents |
| **Fallback** | Allow requests when rate limiter is unavailable | Deny | Prefer availability — better to allow some extra requests than to deny all |

### Tradeoff
- ✅ Low latency (local decision, no network hop for 99% of requests)
- ✅ Resilient (works even if Redis is down — falls back to local counters)
- ❌ Approximate (local counters may slightly exceed global limit during sync intervals)
- ❌ Distributed counters can drift — acceptable for rate limiting (not billing)

### Interview Use
> "I'd implement rate limiting with a local-first approach: each server maintains a local token bucket per key, periodically syncing with Redis. This handles 99% of requests with zero network latency. The tradeoff is slight over-counting during sync intervals — acceptable for rate limiting where approximate enforcement is fine."

---

## 14. M3 Metrics Platform

**Blog Post**: "M3: Uber's Open Source, Large-Scale Metrics Platform"

### Problem
Uber generates billions of metrics data points per second (service latency, error rates, business metrics). Prometheus didn't scale to their volume. Commercial solutions were too expensive.

### What They Built
**M3** — a distributed time-series database and query engine. Open-sourced.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Custom time-series storage (M3DB) | Prometheus TSDB, InfluxDB | Need billions of metrics/sec; Prometheus doesn't scale horizontally |
| **Query** | M3Query (PromQL-compatible) | Grafana direct to storage | Distributed query engine for fan-out across shards |
| **Aggregation** | Pre-aggregation at ingestion + runtime aggregation | Runtime-only | Pre-aggregation reduces storage 10× and speeds up queries |
| **Compression** | Gorilla compression (Facebook's algorithm) | General compression (LZ4) | 12× compression for time-series data specifically |
| **Retention** | Tiered: 48h hot (memory) → 30d warm (SSD) → 1y cold (HDD) | Single tier | Balance cost and query speed |

### Tradeoff
- ✅ Handles billions of metrics/sec (horizontally scalable)
- ✅ 12× compression with Gorilla encoding
- ✅ PromQL-compatible (familiar query language)
- ❌ Complex to operate (distributed system with custom storage engine)
- ❌ Eventually deprecated some workloads to managed solutions

### Interview Use
> "For a metrics/monitoring system at scale, I'd use tiered storage with pre-aggregation — raw metrics kept for 48 hours, then rolled up to minute/hour granularity. Uber's M3 uses Gorilla compression for 12× compression of time-series data. The key decision is pre-aggregate at ingestion time vs compute at query time — pre-aggregation makes dashboards fast at the cost of losing raw granularity for old data."

---

## 15. Real-Time ETA Prediction

**Blog Post**: "How Uber Predicts ETAs Using Machine Learning"

### Problem
Accurate ETA is critical: used for matching, pricing, driver earnings, and rider experience. Simple routing (shortest path × speed limit) is inaccurate — doesn't account for traffic, time of day, weather, road conditions.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Model** | Gradient Boosted Trees (XGBoost) → later Deep Learning | Simple routing algorithm | ML model considers 100+ features — traffic, time, weather, historical patterns |
| **Features** | Real-time traffic (GPS pings from drivers), historical patterns, time-of-day, weather, events | Static speed limits | Real-time GPS from millions of drivers = best traffic data in the world |
| **Serving** | Pre-computed routing graph + ML model overlay | Pure ML end-to-end | Routing graph handles path finding; ML adjusts for real-time conditions |
| **Latency** | < 100ms per ETA prediction | Batch pre-computation | Must be real-time for dispatch matching |
| **Training** | Offline batch (Spark) + online model updates (hourly) | Daily retraining | Hourly updates capture traffic pattern shifts |

### Tradeoff
- ✅ Much more accurate than simple routing (reduces ETA error by 50%+)
- ✅ Real-time GPS data from drivers is a unique competitive advantage
- ❌ ML model complexity — harder to debug than simple rules
- ❌ Model needs constant retraining to stay accurate (traffic patterns change)
- ❌ Edge cases: new roads, construction, special events may not have training data

### Interview Use
> "For ETA prediction, I'd combine a routing graph (Dijkstra/A* for path) with an ML model that adjusts segment travel times based on real-time traffic, time-of-day, and weather. The routing graph handles 'which path', the ML model handles 'how long each segment takes.' This is how Uber reduced ETA errors by 50% compared to simple routing."

---

## 16. Payment System

**Blog Post**: "Payments at Uber: Double-Entry Bookkeeping"

### Problem
Uber processes millions of payments daily across 70+ countries with different currencies. Need to track money flow accurately: rider pays → Uber takes commission → driver gets paid. Every cent must be accounted for.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Accounting model** | Double-entry bookkeeping | Simple credit/debit | Every transaction has a debit and credit entry — always balanced |
| **Consistency** | Strong (ACID transactions) | Eventual consistency | Financial data must be exactly correct — no approximations |
| **Idempotency** | Idempotency keys on every payment operation | Retry and hope for the best | Network retries must not cause double-charges |
| **Currency handling** | Store amounts in smallest unit (cents) as integers | Floating-point | Avoid floating-point rounding errors in financial calculations |
| **Reconciliation** | Daily batch reconciliation against payment processors | Trust the system | Catches discrepancies between Uber's records and Stripe/PayPal |

### Tradeoff
- ✅ Every penny accounted for (double-entry always balances)
- ✅ Idempotency prevents double-charges on retry
- ✅ Daily reconciliation catches any drift
- ❌ Complex system (double-entry adds overhead to every transaction)
- ❌ Strong consistency limits throughput compared to eventual consistency
- ❌ Multi-currency adds significant complexity

### Interview Use
> "For a payment system, I'd use double-entry bookkeeping — every money movement has a debit entry and a credit entry that must balance. Amounts stored as integers in the smallest currency unit (cents) to avoid floating-point errors. Every operation uses an idempotency key to prevent double-charges on retry. Daily reconciliation against payment processors catches any drift."

---

## 17. Geofence Service

**Blog Post**: "Geofence: Uber's Service for Defining Geographical Boundaries"

### Problem
Uber needs to know which city/zone/airport a rider or driver is in — for pricing, regulations, driver availability rules. Thousands of geofences (polygons on a map), millions of point-in-polygon checks per second.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Index** | H3 cells → pre-computed lookup table | Brute-force point-in-polygon for every request | O(1) lookup instead of O(N×V) polygon intersection |
| **Pre-computation** | For each H3 cell, pre-compute which geofences it overlaps | Runtime computation | Moves expensive computation to build time; runtime is just a hash lookup |
| **Storage** | In-memory (geofences rarely change) | Database query per request | Millions of lookups/sec need in-memory speed |
| **Updates** | Rebuild index on geofence change (minutes) | Real-time incremental updates | Geofences change rarely (city boundaries, airport zones); minutes-level staleness OK |

### Tradeoff
- ✅ O(1) per lookup — handles millions of requests/sec
- ✅ Simple: hash lat/lng → H3 cell → lookup table → list of matching geofences
- ❌ Index rebuild takes minutes (acceptable for slowly-changing geofences)
- ❌ Cell boundary approximation (some edge-case inaccuracy at geofence borders)

### Interview Use
> "For a geofence service ('is this point inside this polygon?'), I'd pre-compute the answer for every H3 cell — build a lookup table mapping cell ID → list of overlapping geofences. At runtime, hash the lat/lng to H3 cell ID and look up the table. O(1) per query. Rebuild the table when geofences change (which is rare). This handles millions of lookups per second."

---

## 18. Push Notification Platform

**Blog Post**: "Scaling Uber's Real-Time Push Platform"

### Problem
Uber sends billions of push notifications daily — trip updates, driver arrivals, promotions, Uber Eats delivery status. Each notification must reach the right user on the right device within seconds.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Event-driven (Kafka) → Notification Service → FCM/APNS | Synchronous calls from each service | Decouples business logic from notification delivery |
| **Deduplication** | Per-user notification dedup (don't send same notification twice within 5 min) | No dedup | Prevent spamming users on retries |
| **Batching** | Batch notifications to FCM/APNS (100 per API call) | Individual API calls | 100× fewer API calls to Google/Apple |
| **Priority** | Priority queues (trip updates > promotions) | Single queue | Trip updates must arrive immediately; promotions can wait |
| **Delivery tracking** | Track sent/delivered/opened per notification | Fire and forget | Measure effectiveness, debug delivery issues |
| **Multi-device** | Send to all user's registered devices | Only primary device | User should see notification regardless of which device they're using |

### Tradeoff
- ✅ Event-driven → any service can trigger notifications without coupling
- ✅ Priority queues ensure critical notifications aren't delayed by promotions
- ✅ Batching reduces API costs and rate limit risk with FCM/APNS
- ❌ Async means slight delay (1-3 seconds) vs synchronous
- ❌ FCM/APNS are unreliable — no guarantee of delivery
- ❌ Multi-device increases notification volume and potential for annoyance

### Interview Use
> "For push notifications, I'd use an event-driven architecture: services publish events to Kafka, a Notification Worker consumes them, applies user preferences, deduplicates, and sends to FCM/APNS in batches. Priority queues ensure trip updates arrive before promotional messages. Track delivery for monitoring and debugging."

---

## 19. Uber Eats — Order Management

**Blog Post**: "Uber Eats: Scaling the Food Ordering System"

### Problem
Food delivery adds complexity beyond ride-sharing: multi-party coordination (customer, restaurant, delivery partner), order state machine with many states, parallel preparation and delivery, real-time ETA for food preparation + delivery.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Order state machine** | Cadence/Temporal workflow (event-sourced) | Database state column | Complex state transitions with retries, timeouts, compensations |
| **Dispatch** | Two-phase: (1) find nearest delivery partner when food is almost ready, not when order is placed | Assign delivery partner immediately | Reduces driver wait time; food preparation takes 15-30 min |
| **ETA** | Composite: restaurant prep ETA (ML) + delivery ETA (routing + traffic) | Single ETA model | Two separate problems: "when is food ready?" + "how long to deliver?" |
| **Restaurant integration** | Tablet app with order queue | API integration | Most restaurants don't have technical staff; tablet is plug-and-play |
| **Surge pricing** | Dynamic pricing based on demand/supply in area | Fixed pricing | Incentivizes delivery partners to work in high-demand areas |

### Tradeoff
- ✅ Two-phase dispatch reduces driver idle time (don't assign 25 min before food is ready)
- ✅ Workflow engine handles the complex order lifecycle reliably
- ❌ Two-phase dispatch risks no available driver when food IS ready (mitigated by pre-matching)
- ❌ Restaurant prep ETA is hard to predict (varies by dish, kitchen load)

### Interview Use
> "For food delivery, I'd use a two-phase dispatch: when an order is placed, send it to the restaurant but DON'T assign a driver yet. When the restaurant marks the order as 'almost ready' (or our ML model predicts it), THEN find the nearest driver. This prevents the driver from waiting 20 minutes at the restaurant. The tradeoff is risk of no driver being available — mitigated by pre-matching a likely candidate."

---

## 20. Database Reliability — Multi-Region & Disaster Recovery

**Blog Post**: "How Uber Achieves Cross-Datacenter Database Reliability"

### Problem
Uber operates across multiple datacenters. A datacenter failure should not cause data loss or prolonged outage. Need automated failover with minimal human intervention.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Replication** | Asynchronous cross-DC replication | Synchronous (Paxos/Raft) | Sync replication adds 50-100ms latency per write (unacceptable for real-time matching) |
| **Failover** | Automated with manual approval for writes | Fully automated | Prevent false-positive failovers that could cause data inconsistency |
| **Data loss tolerance** | RPO ≈ seconds (async replication lag) | RPO = 0 (synchronous) | Seconds of data loss during DC failure is acceptable vs 100ms latency penalty on every write |
| **Active-Active** | Active-passive for writes, active-active for reads | Full active-active writes | Simpler conflict resolution; writes go to one DC |
| **Backup** | Continuous backups to object storage + periodic snapshots | Nightly backup only | Point-in-time recovery within minutes, not just to last night's backup |

### Tradeoff
- ✅ Low write latency (no cross-DC consensus per write)
- ✅ Automated failover reduces recovery time to minutes
- ✅ Continuous backup enables point-in-time recovery
- ❌ Async replication means up to ~seconds of data loss during DC failure
- ❌ Active-passive for writes means all writes go to one DC (single region write bottleneck)
- ❌ Failover requires brief downtime (seconds to minutes)

### Interview Use
> "For multi-region database reliability, I'd use asynchronous replication across datacenters — writes go to the primary DC and async replicate to secondary. This keeps write latency low (~5ms) vs synchronous cross-DC consensus (~100ms). The tradeoff is potential data loss of a few seconds during DC failure (RPO ≈ seconds). For financial data requiring zero loss, I'd use a separate strongly-consistent store (CockroachDB) while keeping the main datastore async for performance."

---

## 🎯 Quick Reference: Uber's Key Decisions by Category

### Database Decisions
| System | Choice | Why |
|--------|--------|-----|
| Trip data | Schemaless (MySQL, append-only) | Write-heavy, flexible schema, MySQL familiarity |
| Financial data | CockroachDB | Global strong consistency, distributed transactions |
| General storage | DocStore (MySQL-based) | Replaced Cassandra — better reads, no compaction storms |
| Analytics | Hudi on HDFS/S3 | Upserts in data lake, minute-level freshness |
| Metrics | M3DB (custom time-series) | Billions of metrics/sec, Gorilla compression |

### Communication / Messaging
| System | Choice | Why |
|--------|--------|-----|
| Event bus | Kafka | High throughput, replay, multiple consumers |
| Task queue | Cherami → Kafka | Durable task queue; later migrated to Kafka |
| Notifications | Kafka → Priority Queue → FCM/APNS | Event-driven, prioritized, batched |

### Geospatial
| System | Choice | Why |
|--------|--------|-----|
| Spatial index | H3 (hexagonal) | Uniform neighbors, multi-resolution, O(1) proximity |
| Geofence | H3 pre-computed lookup table | O(1) point-in-polygon at millions of QPS |
| ETA | ML model + routing graph | Real-time traffic data + historical patterns |

### Architecture Patterns
| Pattern | Implementation | Why |
|---------|---------------|-----|
| Service organization | DOMA (domains) | 4000 services → ~50 domains with clear boundaries |
| Workflow orchestration | Cadence/Temporal | Code-based durable workflows for trip lifecycle |
| Rate limiting | Local-first token bucket + Redis sync | Low latency, resilient to Redis failure |
| Matching | Batch optimization every 2s | Globally optimal vs greedy first-match |

---

## 🗣️ How to Use Uber Examples in Interviews

### The Pattern
1. **Name the problem**: "This is similar to how Uber handles X..."
2. **State the decision**: "They chose Y because..."
3. **Acknowledge the tradeoff**: "The downside is Z, which they accept because..."
4. **Apply to your design**: "In our system, I'd take a similar approach / modify it because..."

### Example Sentences
- "Uber uses H3 hexagonal indexing for proximity queries — I'd use the same approach for our 'find nearby' feature."
- "Similar to Uber's DOMA, I'd organize these microservices into domains with clear boundaries and gateway interfaces."
- "Uber stores payments with double-entry bookkeeping and integer cents — we should do the same for our billing system."
- "Like Uber's Cadence, I'd use a workflow engine for this multi-step process instead of scattered state machines."
- "Uber moved from Cassandra to MySQL-based DocStore because their workloads became read-heavy — the LSM vs B-tree tradeoff is relevant here."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 20 engineering blog posts, categorized by database, communication, geospatial, and architecture  
**Status**: Complete & Interview-Ready ✅
