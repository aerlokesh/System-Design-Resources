# Design a Log Search & Analytics System (ELK/Splunk/Datadog) — Hello Interview Framework

> **Question**: Design a distributed log search and analytics platform that ingests terabytes of logs daily from thousands of services, supports full-text search with sub-second latency, enables real-time alerting, and provides dashboards for operational monitoring. Think Elasticsearch/Splunk/Datadog Logs.
>
> **Asked at**: Amazon, Google, Datadog, Splunk, Elastic, Microsoft, Meta
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Log Ingestion**: Accept structured (JSON), semi-structured (key=value), and unstructured (free-text) logs from thousands of services via agents/APIs. Support high-throughput, bursty writes.
2. **Full-Text Search**: Search logs by keyword, field values, boolean operators, regex, and time ranges. Example: `service:payment-svc AND level:ERROR AND "timeout exceeded" AND timestamp:[2026-04-25T10:00 TO 2026-04-25T11:00]`.
3. **Faceted Aggregations**: Count logs by field values (group by service, level, host), compute percentiles, histograms over time, top-K error types.
4. **Real-Time Tail**: Stream new matching logs in real-time (like `tail -f` with filters).
5. **Alerting**: Define alert rules (e.g., "if ERROR rate > 100/min for payment-svc → PagerDuty"). Evaluate continuously with < 1 min detection latency.
6. **Dashboards**: Pre-built and custom dashboards with time-series charts, log volume, error rates, latency percentiles.
7. **Retention & Tiering**: Hot (0-3 days, fast search), warm (3-30 days, slower search), cold (30-365 days, archive), frozen (1+ years, S3/Glacier).

#### Nice to Have (P1)
- Log pattern detection (auto-cluster similar log lines)
- Anomaly detection on log volume / error rates
- Trace correlation (link logs to distributed traces)
- Saved searches and scheduled reports
- Multi-tenancy with per-team quotas and RBAC
- Log-to-metrics conversion (extract numeric metrics from logs)

#### Below the Line (Out of Scope)
- APM / distributed tracing (separate system)
- Infrastructure metrics (CPU, memory — separate metrics pipeline)
- Log generation / client-side SDK
- Incident management / runbooks

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Ingestion Throughput** | 10M events/sec (peak 30M) | Thousands of services, millions of hosts |
| **Ingestion Latency** | < 10 seconds from emit to searchable | Near real-time operational use |
| **Search Latency** | P50 < 500ms, P95 < 2s, P99 < 5s | Interactive investigation |
| **Aggregation Latency** | < 3s for 1-hour window, < 10s for 24-hour | Dashboard rendering |
| **Availability** | 99.9% for search; 99.99% for ingestion | Must not lose logs; search can degrade |
| **Scale** | 50 TB/day ingestion; 1 PB hot storage | Enterprise-grade |
| **Retention** | Hot: 3 days, Warm: 30 days, Cold: 1 year | Balance cost and accessibility |
| **Consistency** | Eventual (seconds-level) | Logs appearing a few seconds late is fine |

### Capacity Estimation

```
Ingestion:
  Services: 5,000 microservices
  Hosts: 500,000 containers/VMs
  Avg log rate: 200 logs/sec/host = 100M logs/sec total
  Log sizes: avg 500 bytes (JSON), max 10KB
  Daily volume: 100M/sec × 500B × 86400 = ~4.3 PB/day (raw)
  With compression (10:1): ~430 TB/day stored
  
  Peak: 3× average during deployments/incidents = 300M logs/sec

Search:
  Concurrent users: 5,000 engineers investigating
  Search QPS: 500 (interactive searches)
  Dashboard refresh: 10,000 panels refreshing every 30s = 333 QPS
  Alert evaluation: 50,000 rules × every 30s = 1,667 QPS
  Total query QPS: ~2,500

Storage:
  Hot (3 days): 430 TB × 3 = 1.3 PB (SSD, indexed)
  Warm (30 days): 430 TB × 27 = 11.6 PB (HDD, indexed)
  Cold (1 year): 430 TB × 335 = 144 PB (object storage, not indexed)
  Index overhead: ~15% of data size
  
  Hot index: 1.3 PB × 0.15 = 195 TB (distributed across cluster)

Infrastructure:
  Ingestion nodes: 200 (500K events/sec each)
  Hot data nodes: 500 (2.6 TB SSD each, 30 shards/node)
  Warm data nodes: 300 (40 TB HDD each)
  Kafka brokers: 50 (for buffering)
  Query coordinators: 50
  Alert evaluators: 30
```

---

## 2️⃣ Core Entities

### Entity 1: LogEvent
```java
public class LogEvent {
    String eventId;              // UUID or ULID
    Instant timestamp;           // When the log was emitted (from source)
    Instant ingestTimestamp;      // When we received it
    String service;              // "payment-service"
    String host;                 // "payment-svc-abc123.us-east-1"
    String level;                // "ERROR", "WARN", "INFO", "DEBUG"
    String message;              // "Connection timeout after 30000ms to db-primary"
    Map<String, String> tags;    // {"env": "prod", "region": "us-east-1", "version": "2.4.1"}
    Map<String, Object> fields;  // {"latency_ms": 30000, "retry_count": 3, "db_host": "db-primary"}
    String traceId;              // For trace correlation (nullable)
    String spanId;               // For trace correlation (nullable)
}
```

### Entity 2: SearchQuery
```java
public class SearchQuery {
    String queryString;          // 'service:payment-svc AND level:ERROR AND "timeout"'
    Instant startTime;           // Required: time range start
    Instant endTime;             // Required: time range end
    List<String> indices;        // Which indices to search (by date/service)
    int limit;                   // Max results (default 100)
    String sortBy;               // "timestamp" desc (default)
    List<Aggregation> aggs;      // Facets/aggregations to compute
    String cursor;               // For pagination
}
```

### Entity 3: AlertRule
```java
public class AlertRule {
    String ruleId;
    String name;                 // "Payment Error Spike"
    String query;                // 'service:payment-svc AND level:ERROR'
    AlertCondition condition;    // COUNT > 100 per 1 minute
    Duration evaluationWindow;   // 5 minutes
    Duration evaluationInterval; // 30 seconds
    List<NotificationTarget> targets; // PagerDuty, Slack, email
    String owner;                // Team owning this alert
    boolean enabled;
}
```

### Entity 4: Index (Time-Based Shard)
```java
public class LogIndex {
    String indexName;            // "logs-payment-svc-2026.04.25"
    Instant startTime;          // Index covers this time range
    Instant endTime;
    IndexTier tier;             // HOT, WARM, COLD, FROZEN
    int shardCount;             // Number of primary shards
    int replicaCount;           // Number of replica shards
    long docCount;              // Documents in this index
    long sizeBytes;             // Size on disk
    IndexState state;           // OPEN, CLOSED, FROZEN
}
```

---

## 3️⃣ API Design

### 1. Ingest Logs (Bulk)
```
POST /api/v1/logs/_bulk
Content-Type: application/x-ndjson

{"service": "payment-svc", "level": "ERROR", "message": "Connection timeout after 30000ms", "timestamp": "2026-04-25T10:30:00Z", "tags": {"env": "prod"}, "fields": {"latency_ms": 30000}}
{"service": "payment-svc", "level": "INFO", "message": "Request processed successfully", "timestamp": "2026-04-25T10:30:01Z", "tags": {"env": "prod"}, "fields": {"latency_ms": 45}}

Response (202 Accepted):
{
  "accepted": 2,
  "failed": 0,
  "errors": []
}
```

### 2. Search Logs
```
POST /api/v1/logs/_search
{
  "query": "service:payment-svc AND level:ERROR AND \"timeout\"",
  "time_range": {
    "start": "2026-04-25T10:00:00Z",
    "end": "2026-04-25T11:00:00Z"
  },
  "sort": [{"timestamp": "desc"}],
  "limit": 50,
  "aggregations": {
    "error_over_time": {
      "type": "date_histogram",
      "field": "timestamp",
      "interval": "1m"
    },
    "top_hosts": {
      "type": "terms",
      "field": "host",
      "size": 10
    }
  }
}

Response (200 OK):
{
  "hits": {
    "total": 1247,
    "results": [
      {
        "event_id": "evt_abc123",
        "timestamp": "2026-04-25T10:59:58Z",
        "service": "payment-svc",
        "host": "payment-svc-xyz789",
        "level": "ERROR",
        "message": "Connection timeout after 30000ms to db-primary",
        "fields": {"latency_ms": 30000, "retry_count": 3}
      }
      // ... more results
    ]
  },
  "aggregations": {
    "error_over_time": {
      "buckets": [
        {"key": "2026-04-25T10:00:00Z", "count": 12},
        {"key": "2026-04-25T10:01:00Z", "count": 8},
        {"key": "2026-04-25T10:30:00Z", "count": 347}  // spike!
      ]
    },
    "top_hosts": {
      "buckets": [
        {"key": "payment-svc-xyz789", "count": 523},
        {"key": "payment-svc-abc456", "count": 312}
      ]
    }
  },
  "took_ms": 127
}
```

### 3. Real-Time Tail
```
WebSocket: wss://logs.internal/api/v1/logs/_tail?query=service:payment-svc+AND+level:ERROR

Server streams matching log events as they arrive:
{"event_id": "evt_new123", "timestamp": "2026-04-25T10:30:05Z", "message": "..."}
{"event_id": "evt_new124", "timestamp": "2026-04-25T10:30:06Z", "message": "..."}
```

---

## 4️⃣ Data Flow

### Ingestion Flow
```
Service emits log → Agent (Fluentd/Vector) batches + ships
  → Load Balancer → Ingestion Gateway (validate, parse, enrich)
  → Kafka (buffer, decouple ingestion from indexing)
  → Indexer Workers (consume from Kafka, bulk index)
  → Elasticsearch / Custom Index Store (inverted index on hot nodes)
  → Also: S3 for cold storage (raw logs archived)
```

### Search Flow
```
User submits query → Query Coordinator
  → Parse query (Lucene query syntax)
  → Determine which time-based indices to search
  → Scatter to relevant shards across hot/warm nodes
  → Each shard executes local search (inverted index + BM25)
  → Gather + merge results (top-K merge sort by timestamp)
  → Compute aggregations (merge partial aggregations from shards)
  → Return to user
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────┐
│                        INGESTION PATH                         │
│                                                                │
│  Services ──→ Agents ──→ LB ──→ Ingestion Gateway             │
│  (Fluentd/   (batch,          (validate, parse,               │
│   Vector)     compress)        enrich, route)                  │
│                                      │                         │
│                                      ▼                         │
│                              ┌──────────────┐                  │
│                              │    Kafka      │                  │
│                              │  (buffer)     │                  │
│                              │  partitioned  │                  │
│                              │  by service   │                  │
│                              └──────┬───────┘                  │
│                           ┌─────────┼─────────┐               │
│                           ▼         ▼         ▼               │
│                     ┌──────────┐ ┌──────┐ ┌──────┐            │
│                     │ Indexer  │ │Indexer│ │Indexer│            │
│                     │ Worker 1 │ │  2   │ │  N   │            │
│                     └────┬─────┘ └──┬───┘ └──┬───┘            │
│                          └──────────┼────────┘                 │
│                                     ▼                          │
│               ┌────────────────────────────────────┐           │
│               │   Elasticsearch / Index Store       │           │
│               │                                     │           │
│               │  HOT (SSD)    WARM (HDD)   COLD    │           │
│               │  [0-3 days]   [3-30 days]  (S3)    │           │
│               │  Fast search  Slower       Archive  │           │
│               └────────────────────────────────────┘           │
└────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                        SEARCH PATH                            │
│                                                                │
│  User ──→ LB ──→ Query Coordinator                            │
│                       │                                        │
│              ┌────────┼────────┐                               │
│              ▼        ▼        ▼                               │
│         [Shard 1] [Shard 2] [Shard N]                         │
│         (local    (local    (local                             │
│          search)   search)   search)                           │
│              │        │        │                               │
│              └────────┼────────┘                               │
│                       ▼                                        │
│              Merge + Aggregate                                 │
│                       │                                        │
│                       ▼                                        │
│                   Response                                     │
└────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                     ALERTING PATH                              │
│                                                                │
│  Alert Rules ──→ Alert Evaluator                              │
│                  (runs queries every 30s)                      │
│                       │                                        │
│                  Threshold check                               │
│                       │                                        │
│              ┌────────┼────────┐                               │
│              ▼        ▼        ▼                               │
│          PagerDuty  Slack    Email                             │
└──────────────────────────────────────────────────────────────┘
```

### Key Technology Choices

| Component | Technology | Why |
|-----------|-----------|-----|
| **Index Store** | Elasticsearch (Lucene-based) | Best-in-class full-text search with aggregations |
| **Buffer** | Apache Kafka | Handle bursty ingestion, decouple producers from indexers |
| **Cold Storage** | S3 / GCS | Cheap, durable storage for archival |
| **Agent** | Fluentd / Vector / Filebeat | Lightweight, reliable log shipping |
| **Alerting** | Custom evaluator on index | Continuous query evaluation |
| **Dashboard** | Grafana / Kibana | Rich visualization |
| **Cache** | Redis | Cache popular dashboard queries |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Ingestion Pipeline — Handling 10M Events/Sec

**The Problem**: 10M events/sec × 500 bytes = 5 GB/s sustained ingestion. Spikes during incidents can be 3× this. We cannot lose logs, and they must be searchable within 10 seconds.

**Architecture — Kafka as Central Buffer**:
```
Why Kafka?
  - Decouples ingestion rate from indexing rate
  - Handles back-pressure: if indexers are slow, Kafka absorbs the spike
  - Durable: logs don't get lost even if indexers crash
  - Replay: can re-index from Kafka if index corrupts

Kafka Sizing:
  Throughput: 10M events/sec × 500B = 5 GB/s
  Partitions: 500 (10K events/sec per partition)
  Brokers: 50 (100 MB/s per broker)
  Retention: 24 hours (buffer for re-processing)
  Replication: 3× → 15 GB/s write throughput across cluster
```

**Ingestion Gateway Design**:
```
Responsibilities:
  1. Validate: reject malformed logs, enforce schema
  2. Parse: extract structured fields from unstructured text
     - Grok patterns: "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}"
     - JSON parsing for structured logs
  3. Enrich: add metadata (datacenter, team, environment from service registry)
  4. Route: determine Kafka partition (by service name for locality)
  5. Rate limit: per-service quotas to prevent noisy neighbor

Batching (critical for throughput):
  Agent → Gateway: batch 1000 events per HTTP request (gzip compressed)
  Gateway → Kafka: batch 500 events per Kafka produce call
  Indexer → ES: bulk index 5000 events per _bulk request

  Batching gives 100× throughput improvement over individual sends
```

**Back-Pressure Handling**:
```
If Elasticsearch indexing falls behind:
  1. Kafka absorbs: up to 24 hours of buffered logs
  2. Indexer auto-scales: spin up more indexer workers
  3. Degraded mode: reduce replica count temporarily (1 → 0 replicas)
  4. Last resort: drop DEBUG/TRACE level logs (shed low-priority data)
  
  Key insight: ingestion must NEVER be blocked. Better to degrade search 
  than to lose logs.
```

**Interview Talking Points**:
- "Kafka is the critical decoupling layer — it absorbs spikes and ensures we never lose logs."
- "Bulk batching at every stage: agent → gateway → kafka → ES. This gives 100× throughput."
- "Back-pressure strategy: shed low-priority logs before blocking ingestion."

---

### Deep Dive 2: Time-Based Index Management & Storage Tiering

**The Problem**: 50 TB/day means we accumulate petabytes quickly. We need different storage tiers for different access patterns.

**Index-Per-Day Strategy**:
```
Index naming: logs-{service}-{date}
  Example: logs-payment-svc-2026.04.25

Why per-day indices?
  1. Natural time-based partitioning (95% of queries have time filter)
  2. Easy retention: delete old index = delete old data (no expensive deletes)
  3. Immutable after rollover: warm/cold indices don't need write capacity
  4. Can have different shard counts per day (more shards for busier services)
```

**Storage Tiering**:
```
┌─────────────────────────────────────────────────────────────────┐
│  HOT (0-3 days) — SSD                                          │
│  - Full indexing (inverted index + doc values + stored fields)  │
│  - 1 replica (for HA)                                          │
│  - Search latency: P95 < 500ms                                 │
│  - Storage: 1.3 PB                                              │
│  - Cost: $$$$                                                    │
├─────────────────────────────────────────────────────────────────┤
│  WARM (3-30 days) — HDD                                        │
│  - Same index, just moved to cheaper nodes                      │
│  - Merge to fewer segments (force merge to 1 segment/shard)    │
│  - 1 replica                                                    │
│  - Search latency: P95 < 2s                                     │
│  - Storage: 11.6 PB                                              │
│  - Cost: $$                                                      │
├─────────────────────────────────────────────────────────────────┤
│  COLD (30-365 days) — Object Storage (S3)                       │
│  - Searchable snapshots (Elasticsearch frozen tier)             │
│  - Index stays in S3, loaded on-demand                          │
│  - No replicas (S3 provides durability)                         │
│  - Search latency: P95 < 30s                                    │
│  - Storage: 144 PB (S3 cost)                                     │
│  - Cost: $                                                       │
├─────────────────────────────────────────────────────────────────┤
│  FROZEN (1+ years) — Glacier                                    │
│  - Not searchable directly                                      │
│  - Restore to warm on-demand (hours)                            │
│  - Compliance/audit purposes                                    │
│  - Cost: ¢                                                       │
└─────────────────────────────────────────────────────────────────┘

Lifecycle Policy (automated):
  - Day 0-3: HOT
  - Day 3: force-merge + move to WARM
  - Day 30: snapshot to S3 + delete from WARM → COLD
  - Day 365: move S3 objects to Glacier → FROZEN
  - Day 730: delete (unless compliance requires longer)
```

**Cost Optimization**:
```
Without tiering: 365 days × 430 TB/day on SSD = 157 PB SSD = ~$50M/month
With tiering:
  Hot: 1.3 PB SSD = ~$400K/month
  Warm: 11.6 PB HDD = ~$500K/month
  Cold: 144 PB S3 = ~$300K/month
  Total: ~$1.2M/month → 97% cost reduction!
```

**Interview Talking Points**:
- "Time-based indices because 95% of log queries include a time filter — we only search relevant indices."
- "Automatic lifecycle management: hot → warm → cold → frozen with ILM policies."
- "Searchable snapshots for cold tier — index lives in S3, loaded on-demand. 97% cost reduction vs. all-hot."

---

### Deep Dive 3: Search Query Execution on Time-Series Log Data

**The Problem**: A query like `service:payment-svc AND level:ERROR AND "timeout" [last 1 hour]` needs to search across dozens of shards and return in < 500ms.

**Query Execution Pipeline**:
```
1. Query Parsing:
   "service:payment-svc AND level:ERROR AND timeout"
   → BoolQuery(
       must: [
         TermQuery("service", "payment-svc"),
         TermQuery("level", "ERROR"),
         MatchQuery("message", "timeout")
       ],
       filter: [
         RangeQuery("timestamp", gte=now-1h, lte=now)
       ]
     )

2. Index Selection:
   Time range: last 1 hour → only today's hot index
   Service filter: payment-svc → route to specific shard(s) if service-partitioned
   Result: search 5 shards (not 500!)

3. Scatter Phase:
   Send query to 5 shard replicas in parallel
   Each shard:
     a. Evaluate filters first (timestamp range, service, level) → bitmap of matching docs
     b. Run full-text search on "timeout" within filtered set
     c. Return top-100 docIds + scores

4. Gather Phase:
   Coordinator merges top-100 from each shard → global top-100
   Fetch full documents for top-100
   Compute aggregations (merge partial histograms, term counts)
   Return to user

Optimization: filter → then search (filters are cheaper than full-text)
```

**Aggregation Acceleration**:
```
For dashboards that run the same aggregation repeatedly:
  - Pre-computed rollups: hourly/daily aggregates of common dimensions
    e.g., (service, level, 1-min bucket) → count
    Pre-computed at ingestion time into a separate rollup index
  
  - Column-oriented storage: for aggregation-heavy queries, store 
    "service" and "level" fields in columnar format (like Parquet)
    → 10× faster for GROUP BY operations

  - Caching: cache dashboard query results in Redis with 30s TTL
    → repeated dashboard refreshes hit cache
```

**Interview Talking Points**:
- "Time-based index selection is the #1 optimization — instead of searching 500 shards, we search 5."
- "Filter-before-search: apply cheap exact filters (service, level) before expensive full-text search."
- "Pre-computed rollups for dashboard queries — aggregate at ingestion time, not query time."

---

### Deep Dive 4: Real-Time Alerting Engine

**The Problem**: 50,000 alert rules need to be evaluated continuously. Each rule is essentially a search query with a threshold. Evaluating 50K searches every 30 seconds is expensive.

**Alerting Architecture**:
```
┌────────────────────────────────────────────────────┐
│               Alert Evaluator Service               │
│                                                      │
│  Alert Rules DB ──→ Rule Scheduler                  │
│  (50K rules)        (assign rules to evaluators)    │
│                          │                           │
│                ┌─────────┼─────────┐                │
│                ▼         ▼         ▼                │
│          [Evaluator 1] [Eval 2] [Eval N]           │
│          (1,666 rules) (1,666)  (1,666)            │
│                │                                    │
│          For each rule every 30s:                   │
│          1. Execute count query against ES           │
│          2. Compare count to threshold               │
│          3. If triggered → Notification Service      │
│                                                      │
│  Optimization: GROUP evaluation queries              │
│    - 500 rules for "service:payment-svc" →           │
│      ONE query with sub-aggregations                 │
│    - Reduces 50K queries to ~5K grouped queries     │
└────────────────────────────────────────────────────┘
```

**Streaming Alternative (for < 30s detection)**:
```
For critical alerts that need < 10s detection:
  - Tap into Kafka stream (before indexing)
  - Use Flink/Kafka Streams for real-time windowed aggregation
  - Pattern: Kafka → Flink (tumbling window 10s) → threshold check → alert

  Trade-off: streaming is faster but can't do complex queries (regex, full-text)
  Hybrid: streaming for simple count/threshold, polling for complex queries
```

**Alert De-duplication & Noise Reduction**:
```
Problem: noisy alerts fatigue on-call engineers

Solutions:
  1. Evaluation window: require threshold exceeded for 3 consecutive windows
     (not just a single spike)
  2. Grouping: group related alerts (same service, same error) into one incident
  3. Auto-resolve: if count drops below threshold for 5 min → auto-close
  4. Snooze/Mute: temporarily suppress known-noisy alerts
  5. Adaptive threshold: baseline on historical pattern, alert on deviation
```

**Interview Talking Points**:
- "Group similar alert rules into batched queries — 50K rules become 5K grouped queries."
- "Hybrid alerting: streaming (Flink on Kafka) for < 10s detection, polling for complex queries."
- "De-noise with consecutive-window requirement and auto-grouping of related alerts."

---

### Deep Dive 5: Multi-Tenancy & Noisy Neighbor Prevention

**The Problem**: One team's runaway logging (10× normal volume) shouldn't degrade search for everyone else.

**Isolation Strategy**:
```
1. Ingestion Quotas:
   - Per-service rate limit: 100K events/sec (configurable)
   - Enforcement at Ingestion Gateway
   - Over-limit → shed DEBUG/TRACE first, then sample INFO
   - Alert service owner about rate limiting

2. Index Isolation:
   - Option A: Shared indices (simpler, less isolation)
     All services in same daily index, filtered by "service" field
   - Option B: Per-service indices (better isolation, more overhead)
     Each service gets its own index: logs-{service}-{date}
     
   Hybrid: group small services into shared index, large services get dedicated
   
3. Search Quotas:
   - Per-team query rate limit: 100 QPS
   - Per-query timeout: 30 seconds max
   - Per-query scan limit: 10M documents max
   - Circuit breaker: if cluster is overloaded, reject non-critical queries

4. Resource Allocation:
   - Node affinity: team X's indices → specific hot nodes
   - CPU/memory isolation: cgroups on indexer workers
```

**Interview Talking Points**:
- "Per-service ingestion quotas with graceful degradation: shed low-severity logs first."
- "Hybrid index isolation: large services get dedicated indices, small services share."
- "Circuit breakers on search to protect the cluster during incidents."

---

### Deep Dive 6: Schema-on-Read vs Schema-on-Write

**The Problem**: Logs are messy — every service has different fields, different formats. How do we handle schema diversity?

**Schema-on-Write (Elasticsearch approach)**:
```
Pros:
  - Fast queries (fields are pre-indexed)
  - Type validation (catch errors at ingest time)
  
Cons:
  - Schema explosions (thousands of unique fields → mapping explosion)
  - Schema conflicts ("status" is integer in service A, string in service B)
  
Solution — Dynamic Templates:
  - Define templates per service: "for payment-svc, 'amount' is float, 'currency' is keyword"
  - Catch-all: unknown fields → keyword type (not analyzed)
  - Limit: max 1,000 fields per index (reject beyond that)
```

**Schema-on-Read (Splunk/ClickHouse approach)**:
```
Pros:
  - No mapping explosion
  - Ingest anything (schemaless)
  - Extract fields at query time
  
Cons:
  - Slower queries (field extraction at search time)
  - No inverted index on dynamic fields

Solution — Hybrid:
  - Schema-on-write for COMMON fields: timestamp, service, level, host, message
  - Schema-on-read for DYNAMIC fields: arbitrary key-value pairs in a JSON blob
  - Best of both: fast filtering on common fields, flexible extraction for rare fields
```

**Interview Talking Points**:
- "Hybrid schema approach: schema-on-write for common fields (timestamp, level, service), schema-on-read for dynamic fields."
- "Elasticsearch's mapping explosion is a real issue — limit to 1,000 fields per index with dynamic templates."
- "This mirrors how Datadog and Splunk handle schema diversity in practice."

---

## What is Expected at Each Level?

### Mid-Level
- Design basic ingest → index → search pipeline
- Use Elasticsearch as the search backend
- Explain inverted index basics
- Handle time-based partitioning

### Senior
- Kafka as ingestion buffer with back-pressure handling
- Time-based index lifecycle (hot/warm/cold tiering)
- Search optimization with filter-before-search
- Real-time alerting architecture
- Multi-tenancy with quotas

### Staff
- Detailed capacity planning for PB-scale ingestion
- Streaming + polling hybrid alerting
- Schema-on-read vs schema-on-write trade-offs
- Cost optimization (97% savings with tiering)
- Pre-computed rollups for dashboard acceleration
- Noisy neighbor prevention across ingestion and search
- Column-oriented storage for aggregation-heavy workloads
