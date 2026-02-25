# Design a System Metrics Monitoring and Alerting Platform - Hello Interview Framework

> **Question**: Design a metrics monitoring platform that collects performance data (CPU, memory, throughput) from hundreds of thousands of servers, allows users to set threshold-based alerts, and provides dashboards for visualization and notifications when alerts are triggered. The core workflow is collect → store → query → alert → notify, at very high scale and in near real time.
>
> **Asked at**: LinkedIn, TikTok, Amazon, Meta
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
1. **Metrics Ingestion**: Collect time-series metrics (CPU, memory, disk, network, custom app metrics) from 500K+ hosts at 10-second intervals. Agents push metrics via a high-throughput pipeline.
2. **Time-Series Storage**: Store metrics efficiently with configurable retention (raw: 15 days, 1-min rollup: 90 days, 1-hour rollup: 2 years). Support downsampling/rollups for long-term queries.
3. **Querying & Dashboards**: Query metrics by metric name, host, tags, time range. Support aggregation (avg, sum, max, P99, rate). Power Grafana-style dashboards with sub-second refresh.
4. **Alerting Rules**: Users define threshold-based alert rules (e.g., "alert if avg(cpu.usage) > 90% for 5 minutes on any host in cluster=prod"). Evaluate rules continuously.
5. **Alert Notifications**: When alerts fire, notify via email, Slack, PagerDuty, webhook. Support escalation (P1 → page oncall, P3 → Slack only). Deduplication and grouping.
6. **Multi-Tenancy**: Multiple teams/services share the platform. Per-tenant isolation, dashboards, and alert routing. Per-tenant cardinality limits.

#### Nice to Have (P1)
- Anomaly detection (ML-based: detect deviations from baseline without explicit thresholds).
- Alert silencing/maintenance windows (suppress during deploys).
- Composite alerts (alert if metric A > X AND metric B < Y).
- SLO/SLI tracking (error budget burn rate alerts).
- Metrics metadata/discovery (browse available metrics, auto-complete).
- Custom metric labels/tags with cardinality control.

#### Below the Line (Out of Scope)
- Log collection and analysis (separate system: ELK/Loki).
- Distributed tracing (separate system: Jaeger/Zipkin).
- The monitoring agents themselves (assume they exist and push metrics).
- Dashboard UI rendering (we design the backend; Grafana is the frontend).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Ingestion Throughput** | 10M data points/sec | 500K hosts × 20 metrics × 1 sample/10s |
| **Ingestion Latency** | < 5 seconds from emission to queryable | Near-real-time dashboards |
| **Query Latency** | < 500ms for dashboard queries (last 1 hour) | Smooth dashboard refresh |
| **Alert Evaluation Latency** | < 30 seconds from metric → alert fired | Timely incident detection |
| **Storage Efficiency** | < 2 bytes/data point (compressed) | Cost control at 10M pts/sec |
| **Retention** | Raw: 15 days, 1-min: 90 days, 1-hour: 2 years | Balance detail vs cost |
| **Availability** | 99.9% ingestion, 99.99% alerting | Alerting is the safety net; must not fail silently |
| **Cardinality** | 100M unique time series | High-cardinality tags (host_id, container_id, etc.) |

### Capacity Estimation

```
Metrics:
  Hosts: 500K
  Metrics per host: 20 (CPU, memory, disk, network, app-specific)
  Sample interval: 10 seconds
  Data points per second: 500K × 20 / 10 = 1M dps/sec (sustained)
  Peak: 10M dps/sec (burst, deploy storms, autoscaling)
  
  Data point size: metric_name + tags + timestamp + value = ~100 bytes (raw)
  Compressed: ~1.5 bytes/point (time-series compression: gorilla, delta-of-delta)

Storage:
  Raw (15 days): 1M dps/sec × 86400 × 15 × 1.5 bytes = ~1.9 TB
  1-min rollup (90 days): 1M / 6 × 86400 × 90 × 10 bytes = ~12.5 TB
  1-hour rollup (2 years): 1M / 360 × 86400 × 730 × 10 bytes = ~1.5 TB
  Total: ~16 TB active storage

Queries:
  Dashboard queries: 10K/sec (500 dashboards × 20 panels × 1 refresh/sec)
  Alert rule evaluations: 50K rules × 1 eval/30s = ~1,700 evals/sec
  Ad-hoc queries: ~500/sec

Alerts:
  Active alert rules: 50K
  Alert evaluations/sec: ~1,700
  Alerts fired/day: ~5K (0.1% of evaluations trigger)
  Notifications/day: ~10K (alerts × notification channels)

Unique time series: 500K hosts × 20 metrics × 10 tag combos = ~100M
```

---

## 2️⃣ Core Entities

### Entity 1: Metric Data Point
```java
public class DataPoint {
    private final String metricName;        // "cpu.usage", "http.request.latency_p99"
    private final Map<String, String> tags; // { "host": "web-01", "cluster": "prod", "region": "us-east" }
    private final long timestamp;           // Unix epoch milliseconds
    private final double value;             // 87.5 (CPU %), 234 (ms), 1542 (count)
}
```

### Entity 2: Time Series
```java
public class TimeSeries {
    private final String seriesId;          // Hash of (metric_name + sorted tags)
    private final String metricName;
    private final Map<String, String> tags;
    private final String tenantId;          // Multi-tenant isolation
    private final Instant firstSeen;
    private final Instant lastSeen;
}
```

### Entity 3: Alert Rule
```java
public class AlertRule {
    private final String ruleId;            // UUID
    private final String tenantId;
    private final String name;              // "High CPU on prod cluster"
    private final String query;             // "avg(cpu.usage{cluster=prod}) by (host)"
    private final AlertCondition condition; // ABOVE 90 FOR 5m
    private final AlertSeverity severity;   // P1, P2, P3, P4
    private final List<NotificationChannel> channels; // Slack, PagerDuty, email
    private final boolean enabled;
    private final Map<String, String> labels; // Additional context for routing
    private final Instant createdAt;
}

public record AlertCondition(Operator operator, double threshold, Duration duration) {}
public enum Operator { ABOVE, BELOW, EQUALS, ABSENT }
public enum AlertSeverity { P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW }
```

### Entity 4: Alert Instance (Firing Alert)
```java
public class AlertInstance {
    private final String alertId;           // UUID
    private final String ruleId;
    private final String tenantId;
    private final AlertState state;         // PENDING, FIRING, RESOLVED
    private final double currentValue;      // 94.2 (current metric value)
    private final Instant startedAt;        // When condition first met
    private final Instant firedAt;          // When duration threshold exceeded → alert fired
    private final Instant resolvedAt;       // When condition no longer met
    private final Map<String, String> labels; // { "host": "web-01" }
}

public enum AlertState { PENDING, FIRING, RESOLVED }
```

### Entity 5: Notification Channel
```java
public class NotificationChannel {
    private final String channelId;
    private final String tenantId;
    private final ChannelType type;         // SLACK, PAGERDUTY, EMAIL, WEBHOOK
    private final Map<String, String> config; // { "webhook_url": "...", "channel": "#alerts" }
    private final boolean enabled;
}

public enum ChannelType { SLACK, PAGERDUTY, EMAIL, WEBHOOK, OPSGENIE }
```

### Entity 6: Dashboard / Panel Query
```java
public class DashboardQuery {
    private final String queryId;
    private final String tenantId;
    private final String query;             // "rate(http.requests.total{service=api}[5m])"
    private final Instant startTime;
    private final Instant endTime;
    private final Duration step;            // 15s, 1m, 5m (query resolution)
}
```

---

## 3️⃣ API Design

### 1. Ingest Metrics (Push — Hot Path)
```
POST /api/v1/metrics/write
Headers: X-Tenant-Id: tenant_abc

Request (Prometheus remote_write format or custom):
{
  "timeseries": [
    {
      "metric": "cpu.usage",
      "tags": { "host": "web-01", "cluster": "prod", "region": "us-east" },
      "samples": [
        { "timestamp": 1736503200000, "value": 87.5 },
        { "timestamp": 1736503210000, "value": 89.2 }
      ]
    },
    {
      "metric": "memory.used_bytes",
      "tags": { "host": "web-01", "cluster": "prod" },
      "samples": [ { "timestamp": 1736503200000, "value": 8589934592 } ]
    }
  ]
}

Response (200 OK): { "accepted": 3, "rejected": 0 }
```

### 2. Query Metrics (Dashboard Reads)
```
POST /api/v1/metrics/query

Request:
{
  "query": "avg(cpu.usage{cluster='prod'}) by (host)",
  "start": "2025-01-10T09:00:00Z",
  "end": "2025-01-10T10:00:00Z",
  "step": "15s"
}

Response (200 OK):
{
  "result_type": "matrix",
  "result": [
    {
      "tags": { "host": "web-01" },
      "values": [
        [1736503200, 87.5], [1736503215, 89.2], [1736503230, 85.1], ...
      ]
    },
    {
      "tags": { "host": "web-02" },
      "values": [ [1736503200, 62.3], [1736503215, 64.1], ... ]
    }
  ]
}
```

### 3. Create Alert Rule
```
POST /api/v1/alerts/rules

Request:
{
  "name": "High CPU on prod",
  "query": "avg(cpu.usage{cluster='prod'}) by (host)",
  "condition": { "operator": "ABOVE", "threshold": 90, "for": "5m" },
  "severity": "P2_HIGH",
  "channels": ["slack_ops", "pagerduty_infra"],
  "labels": { "team": "infra", "service": "web" }
}

Response (201): { "rule_id": "rule_123", "status": "ENABLED" }
```

### 4. Get Active Alerts
```
GET /api/v1/alerts?state=FIRING&severity=P1_CRITICAL,P2_HIGH

Response (200):
{
  "alerts": [
    {
      "alert_id": "alert_001",
      "rule_name": "High CPU on prod",
      "state": "FIRING",
      "current_value": 94.2,
      "threshold": 90,
      "labels": { "host": "web-01", "cluster": "prod" },
      "started_at": "2025-01-10T09:50:00Z",
      "fired_at": "2025-01-10T09:55:00Z",
      "duration": "10m"
    }
  ]
}
```

### 5. Silence Alert
```
POST /api/v1/alerts/silences

Request:
{
  "matchers": [{ "label": "cluster", "value": "staging" }],
  "starts_at": "2025-01-10T10:00:00Z",
  "ends_at": "2025-01-10T12:00:00Z",
  "comment": "Scheduled maintenance window"
}
```

### 6. List Available Metrics (Discovery)
```
GET /api/v1/metrics/labels?metric=cpu.usage

Response (200):
{
  "metric": "cpu.usage",
  "labels": ["host", "cluster", "region", "instance_type"],
  "label_values": {
    "cluster": ["prod", "staging", "dev"],
    "region": ["us-east", "us-west", "eu-west"]
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Metrics Ingestion Pipeline (Hot Path)
```
1. Agent on host emits metrics every 10 seconds
   ↓
2. Agent batches + compresses → POST /api/v1/metrics/write
   ↓
3. Ingestion Gateway (stateless):
   a. Authenticate (tenant API key)
   b. Rate limit per tenant (cardinality + throughput)
   c. Validate: metric names, tag keys, value types
   d. Produce to Kafka topic "raw-metrics" (partitioned by tenant + metric hash)
   ↓
4. Kafka: durable buffer, 10M dps/sec throughput
   ↓
5. Metrics Writer (Kafka consumer):
   a. Consume batch of data points
   b. Write to Time-Series DB (TSDB):
      - In-memory buffer → WAL (write-ahead log) → compressed chunks on disk
   c. Update series index (new series? register metric + tags)
   ↓
6. Data queryable within ~5 seconds of emission
```

### Flow 2: Dashboard Query (Read Path)
```
1. Grafana dashboard panel refreshes (every 15-60 seconds)
   ↓
2. Grafana calls POST /api/v1/metrics/query
   ↓
3. Query Service:
   a. Parse PromQL-style query
   b. Determine time range → select data tier (raw, 1-min rollup, 1-hour rollup)
   c. Identify relevant time series (by metric + tag filters)
   d. Fetch data from TSDB:
      - Recent (< 2 hours): from in-memory cache / memtable
      - Older: from compressed on-disk chunks
   e. Apply aggregation functions (avg, sum, P99, rate)
   f. Return time series result
   ↓
4. Response: < 500ms for 1-hour range, < 2s for 24-hour range
```

### Flow 3: Alert Evaluation & Firing
```
1. Alert Evaluator runs every 15-30 seconds per rule
   ↓
2. For each active alert rule:
   a. Execute the rule's query against TSDB
   b. Compare result against threshold:
      - value > threshold? → mark as PENDING
      - PENDING for > duration? → transition to FIRING
      - value < threshold? → transition to RESOLVED
   ↓
3. On state change (PENDING → FIRING, FIRING → RESOLVED):
   a. Create/update AlertInstance in Alert Store
   b. Publish to Kafka "alert-notifications" topic
   ↓
4. Notification Service:
   a. Consume alert events
   b. Group: alerts with same labels → single notification
   c. Deduplicate: don't re-notify for same alert within cooldown
   d. Route to channels (Slack, PagerDuty, email, webhook)
   e. Check silences: if matching silence active → suppress
   ↓
5. Total: metric emitted → alert fired → notification sent: < 60 seconds
```

### Flow 4: Rollup / Downsampling
```
1. Background job runs every 5 minutes
   ↓
2. Rollup Service:
   a. For each time series, compute 1-minute aggregates:
      - avg, min, max, count, sum over 1-minute windows
   b. Write rollup data points to separate rollup storage tier
   ↓
3. After 15 days: raw data TTL expires, deleted
   After 90 days: 1-minute rollup TTL expires, deleted
   After 2 years: 1-hour rollup TTL expires, deleted
   ↓
4. Query engine automatically selects appropriate tier:
   - Last 1 hour → raw data (10s resolution)
   - Last 7 days → 1-minute rollup
   - Last 90 days → 1-hour rollup
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          METRIC SOURCES                                      │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐                  │
│  │ Host Agent │ │ App SDK   │ │ K8s cAdvisor│ │ Cloud APIs│                  │
│  │ (node_exp.)│ │ (StatsD)  │ │           │ │ (CloudWatch)                  │
│  └─────┬──────┘ └─────┬─────┘ └─────┬──────┘ └─────┬─────┘                  │
└────────┼───────────────┼─────────────┼──────────────┼────────────────────────┘
         └───────────────┼─────────────┼──────────────┘
                         │ POST /metrics/write
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    INGESTION GATEWAY (Stateless, 50-100 pods)                 │
│  • Auth (API key per tenant)  • Rate limiting (per-tenant cardinality limit) │
│  • Validation (metric names, tags) • Produce to Kafka                       │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA CLUSTER                                        │
│  Topic: "raw-metrics"  (500 partitions, 10M dps/sec)                        │
│  Topic: "alert-notifications" (alert state changes)                          │
│  Retention: 6 hours (buffer for consumer lag)                               │
└──────────┬───────────────────────────────┬───────────────────────────────────┘
           │                               │
     ┌─────▼──────────────┐         ┌─────▼──────────────┐
     │ METRICS WRITER      │         │ ALERT EVALUATOR     │
     │ (Kafka consumer)    │         │                     │
     │                     │         │ • 50K rules / 30s   │
     │ • Consume batches   │         │ • Query TSDB        │
     │ • Write to TSDB     │         │ • Compare threshold │
     │ • Update series     │         │ • State machine:    │
     │   index             │         │   OK→PENDING→FIRING │
     │                     │         │   →RESOLVED         │
     │ Pods: 50-200        │         │                     │
     │ (scale with ingest) │         │ Pods: 20-50         │
     └──────────┬──────────┘         └──────────┬──────────┘
                │                               │
                ▼                               │
┌──────────────────────────────┐               │
│      TIME-SERIES DATABASE     │               │
│                               │               │
│  ┌──────────────────────┐    │               │
│  │ In-Memory (hot, <2h)  │    │               │
│  │ • memtable + WAL      │    │               │
│  │ • Fastest queries      │    │               │
│  ├──────────────────────┤    │               │
│  │ SSD (warm, 15 days)   │    │◄──────────────┘
│  │ • Compressed chunks    │    │   (alert queries)
│  │ • Gorilla compression  │    │
│  ├──────────────────────┤    │
│  │ S3/GCS (cold, 2 years)│    │
│  │ • 1-min & 1-hour       │    │
│  │   rollups              │    │
│  │ • Parquet files        │    │
│  └──────────────────────┘    │
│                               │
│  ~16 TB total                │
│  Nodes: 20-50 (sharded)     │
└──────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                      QUERY & VISUALIZATION LAYER                             │
│                                                                               │
│  ┌───────────────┐  ┌───────────────┐  ┌──────────────────┐                │
│  │ QUERY SERVICE  │  │ GRAFANA       │  │ ALERT STORE       │                │
│  │               │  │ (Dashboard UI)│  │                  │                │
│  │ • Parse PromQL│  │               │  │ PostgreSQL       │                │
│  │ • Select tier │  │ • Dashboards  │  │ • Alert rules    │                │
│  │ • Aggregate   │  │ • Panels      │  │ • Alert instances│                │
│  │ • Return JSON │  │ • Alerts view │  │ • Silences       │                │
│  │               │  │               │  │ • Notification   │                │
│  │ Pods: 30-50   │  │               │  │   channels       │                │
│  └───────────────┘  └───────────────┘  └──────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                      NOTIFICATION SERVICE                                    │
│                                                                               │
│  • Consume from "alert-notifications" Kafka topic                           │
│  • Grouping: same alert labels → one notification                          │
│  • Deduplication: don't re-notify within cooldown (e.g., 1 hour)          │
│  • Routing: severity → channel (P1→PagerDuty, P3→Slack)                   │
│  • Silences: check active silences before sending                          │
│  • Channels: Slack, PagerDuty, OpsGenie, email, webhook                   │
│  Pods: 10-20                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Ingestion Gateway** | Auth, validate, rate limit, produce to Kafka | Go on K8s | 50-100 pods (stateless) |
| **Kafka** | Durable buffer for metrics + alert events | Apache Kafka | 30+ brokers, 500 partitions |
| **Metrics Writer** | Consume from Kafka, write to TSDB | Go on K8s | 50-200 pods (scale with ingest) |
| **TSDB** | Time-series storage with compression | VictoriaMetrics / Thanos / M3DB | 20-50 nodes, ~16 TB |
| **Query Service** | Parse PromQL, query TSDB, aggregate | Go on K8s | 30-50 pods (read-heavy) |
| **Alert Evaluator** | Evaluate 50K rules every 30s against TSDB | Go on K8s | 20-50 pods |
| **Alert Store** | Alert rules, instances, silences, channels | PostgreSQL | Replicated, ~10 GB |
| **Notification Service** | Route alerts to channels, dedup, group, silence | Go on K8s | 10-20 pods |
| **Rollup Service** | Downsample raw → 1-min → 1-hour aggregates | Batch job (K8s CronJob) | 5-10 pods |
| **Grafana** | Dashboard UI, visualization | Grafana OSS | 5-10 pods |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Time-Series Storage — Efficient Compression at 10M dps/sec

**The Problem**: 10M data points/sec × 100 bytes each = 1 GB/sec raw. Must compress to ~1.5 bytes/point for cost-effective storage. Time-series data has unique compression properties: timestamps are nearly sequential, values change slowly.

```
Compression techniques (Gorilla / Facebook time-series compression):

1. TIMESTAMP COMPRESSION (delta-of-delta):
   Raw timestamps: 1736503200, 1736503210, 1736503220, 1736503230
   Deltas: 10, 10, 10
   Delta-of-deltas: 0, 0, 0
   → Encode: 0 bits for repeated delta! (1 bit = "same as last")
   → 10-second intervals compress to ~1 bit per timestamp

2. VALUE COMPRESSION (XOR encoding):
   CPU values: 87.5, 87.6, 87.4, 87.5
   XOR with previous: small change → most bits are zero
   → Encode only the differing bits (leading zeros + trailing zeros + meaningful bits)
   → Slowly changing values: ~1-4 bits per value

3. CHUNK ORGANIZATION:
   - Data grouped into 2-hour chunks per time series
   - Each chunk: compressed timestamps + compressed values
   - Chunk size: ~100-500 KB (depending on cardinality)
   - In-memory for recent 2 hours → flush to disk when full

Result: 100 bytes/point raw → ~1.5 bytes/point compressed (66× compression)
  16 TB storage vs 1 PB uncompressed
```

---

### Deep Dive 2: Cardinality Control — Managing 100M Unique Time Series

**The Problem**: Each unique combination of (metric_name, tag_key=tag_value, ...) creates a new time series. High-cardinality tags (e.g., `request_id`, `user_id`) can explode to billions of series, crashing the TSDB.

```
Cardinality explosion example:
  metric: http.latency
  tags: { host, endpoint, status_code, user_id }
  
  Without user_id: 500 hosts × 100 endpoints × 5 status codes = 250K series ✓
  With user_id: 250K × 100M users = 25 TRILLION series ✗ (catastrophic)

Controls:
1. PER-TENANT SERIES LIMIT: max 10M active series per tenant
   → Enforced at Ingestion Gateway: reject new series if limit exceeded

2. TAG VALUE CARDINALITY LIMIT: max 10K unique values per tag key
   → reject metrics with tag values exceeding limit (e.g., user_id)

3. RELABELING: drop or aggregate high-cardinality tags before storage
   → Ingestion pipeline rule: "drop tag user_id from http.latency"

4. SERIES STALENESS: series not seen for 5 minutes → mark stale
   → Don't count toward active series limit
   → Garbage collect stale series index entries after 1 hour

5. CARDINALITY DASHBOARD: show each tenant their series count + top tag cardinalities
   → Alert if approaching limit (80% threshold warning)
```

---

### Deep Dive 3: Alert Evaluation Engine — 50K Rules Every 30 Seconds

**The Problem**: 50K alert rules, each evaluated every 30 seconds. Each evaluation executes a PromQL query against the TSDB. That's ~1,700 queries/sec just for alerting. Must be reliable (missed alert = missed incident).

```
Alert evaluation architecture:

1. RULE DISTRIBUTION: 50K rules divided across 50 evaluator pods
   → 1,000 rules per pod
   → Each pod evaluates its rules every 30 seconds
   → Consistent hashing: rule_id → pod (stable assignment for state tracking)

2. EVALUATION LOOP (per pod, every 30 seconds):
   for each rule in my_rules:
     result = query_tsdb(rule.query)        // ~10ms per query
     for each series in result:
       current_value = series.last_value
       
       if meets_condition(current_value, rule.condition):
         if state == OK:
           state = PENDING; pending_since = now
         elif state == PENDING and (now - pending_since) > rule.duration:
           state = FIRING; fire_alert()
       else:
         if state == FIRING:
           state = RESOLVED; resolve_alert()
         elif state == PENDING:
           state = OK  // condition cleared before duration
   
   Total per pod: 1000 rules × 10ms = 10 seconds per cycle (fits in 30s window)

3. STATE PERSISTENCE:
   - Alert state (OK/PENDING/FIRING/RESOLVED) stored in-memory per pod
   - Periodically checkpointed to PostgreSQL (every 60s)
   - On pod restart: load state from PostgreSQL, resume evaluation

4. FAULT TOLERANCE:
   - If pod crashes: another pod picks up its rules (re-assignment via leader election)
   - State loaded from last checkpoint → may re-evaluate a few rules (idempotent)
   - Alert notifications are idempotent (dedup by alert_id in Notification Service)
```

---

### Deep Dive 4: Notification Pipeline — Grouping, Deduplication, Routing

**The Problem**: When a deploy goes bad, 100 hosts may fire CPU alerts simultaneously. Sending 100 separate PagerDuty pages is unhelpful. Must group related alerts, deduplicate, route by severity, and respect silences.

```
Notification pipeline:

1. GROUPING: alerts with same (rule_name, cluster, severity) → one notification
   Example: 50 hosts fire "High CPU on prod" → 1 notification:
   "🔥 High CPU on prod: 50 hosts affected (web-01, web-02, ...)"

2. DEDUPLICATION: don't re-notify for same alert within cooldown
   - Cooldown per alert_id: 1 hour (configurable per rule)
   - Redis SET: notified:{alert_id} with TTL = cooldown duration
   - If key exists → skip notification; if not → notify + set key

3. ROUTING by severity:
   P1_CRITICAL → PagerDuty (page oncall) + Slack #incidents + email
   P2_HIGH → Slack #alerts + email
   P3_MEDIUM → Slack #alerts
   P4_LOW → Slack #low-priority (daily digest)

4. SILENCES: before sending, check if any active silence matches alert labels
   - Silence: { matchers: [{label: "cluster", value: "staging"}], ends_at: ... }
   - If match → suppress notification (alert still FIRING, just not notified)

5. ESCALATION: if FIRING for > 30 minutes and no ACK → escalate
   - P2 → escalate to P1 (page manager)
   - Requires human acknowledgment to stop escalation
```

---

### Deep Dive 5: Multi-Tier Storage — Raw, Rollup, and Cold Archive

**The Problem**: Raw 10-second data is valuable for debugging recent issues but too expensive to keep forever. We downsample into rollups for long-term queries and archive to object storage for cost efficiency.

```
Storage tiers:

HOT (in-memory + SSD, 0-2 hours):
  - In-memory memtable + WAL for writes
  - Fastest queries (< 50ms)
  - ~50 GB total (2 hours × 1M dps/sec × 1.5 bytes)

WARM (SSD, 2 hours - 15 days):
  - Compressed chunks on SSD (Gorilla encoding)
  - ~1.9 TB total
  - Query latency: < 500ms

COLD - 1min rollup (S3/GCS, 15 days - 90 days):
  - 1-minute aggregates: avg, min, max, count, sum
  - Stored as Parquet files on object storage
  - ~12.5 TB total
  - Query latency: 1-3 seconds

COLD - 1hr rollup (S3/GCS, 90 days - 2 years):
  - 1-hour aggregates
  - ~1.5 TB total
  - Query latency: 2-5 seconds

Query auto-routing:
  time_range < 2 hours → HOT tier (10s resolution)
  time_range < 15 days → WARM tier (10s resolution)
  time_range < 90 days → 1-min rollup
  time_range < 2 years → 1-hour rollup

Rollup computation:
  - Background CronJob every 5 minutes
  - For each series: read raw data → compute aggregates → write to rollup tier
  - Rollups are immutable (write once, never update)
```

---

### Deep Dive 6: PromQL-Style Query Engine

**The Problem**: Users write queries like `avg(cpu.usage{cluster="prod"}) by (host)` and expect results in < 500ms. The query engine must parse the expression, identify relevant series, fetch data from the appropriate tier, and compute aggregations.

```
Query execution pipeline:

1. PARSE: PromQL string → AST
   "avg(cpu.usage{cluster='prod'}) by (host)"
   → Aggregate(AVG, Selector("cpu.usage", {cluster: "prod"}), GroupBy("host"))

2. PLAN: determine data source
   - Time range → select tier (hot/warm/cold)
   - Tag filters → series index lookup (which series match {cluster="prod"}?)
   - Estimated data volume → parallel vs serial fetch

3. FETCH: retrieve raw data points
   - Series index: inverted index → metric_name + tags → series_ids
   - For each matching series: fetch data points in time range
   - Parallel fetch across TSDB shards

4. AGGREGATE: compute result
   - avg(cpu.usage) by (host): group by host tag, compute average per group
   - rate(): compute per-second rate from counter increments
   - P99: compute 99th percentile (requires all values, not just aggregates)

5. RETURN: time series result (timestamp, value pairs per group)

Optimization:
  - Pre-aggregated queries (avg by host) can use rollup data directly
  - Percentile queries (P99) MUST use raw data (can't compute from rollups)
  - Query result cache: same query within 15s → return cached result
  - Parallel shard queries: scatter-gather across TSDB nodes
```

---

### Deep Dive 7: Ingestion Backpressure — Handling 10M dps/sec Spikes

**The Problem**: A deploy triggers 500K hosts to restart simultaneously, causing a burst of 10M dps/sec (10× normal). Kafka absorbs the burst, but downstream TSDB may not keep up. We need backpressure without dropping metrics.

```
Backpressure strategy:

1. KAFKA AS BUFFER: 6 hours of retention
   - Normal throughput: 1M dps/sec → TSDB keeps up
   - Burst: 10M dps/sec → Kafka absorbs, consumer lag grows
   - Consumer processes at max throughput (e.g., 5M dps/sec)
   - After burst: catches up over time (consumer lag decreases)

2. ADAPTIVE BATCHING in Metrics Writer:
   - Normal: write batches of 1000 points (low latency)
   - Under pressure (lag > 10 min): increase batch size to 10K (higher throughput)
   - Trade latency for throughput during bursts

3. PER-TENANT RATE LIMITING at Ingestion Gateway:
   - Each tenant has a quota: max dps/sec
   - If exceeded: return 429, agent retries with backoff
   - Prevents one tenant's burst from impacting others

4. LOAD SHEDDING (last resort):
   - If Kafka lag > 30 minutes: drop lowest-priority metrics
   - Priority: P1 (infra metrics like CPU/memory) > P2 (app metrics) > P3 (debug metrics)
   - Never drop metrics that alert rules depend on

5. HORIZONTAL SCALING:
   - Auto-scale Metrics Writer pods based on consumer lag
   - If lag > 5 minutes → add more consumer pods
   - Max consumers = number of Kafka partitions (500)
```

---

### Deep Dive 8: Multi-Tenancy — Isolation Without Dedicated Infrastructure

**The Problem**: Multiple teams share one monitoring platform. Team A's noisy metrics (10M series) shouldn't slow down Team B's queries. Each team should only see their own metrics and alerts.

```
Multi-tenancy layers:

1. DATA ISOLATION:
   - Every data point tagged with tenant_id
   - TSDB partitioned by tenant: separate storage per tenant (or tenant prefix in keys)
   - Every query includes tenant_id filter (enforced at Query Service, not user input)
   - Series index per tenant: tenant_A's series don't appear in tenant_B's queries

2. QUOTA ENFORCEMENT:
   - Per-tenant limits: max series (10M), max dps/sec (1M), max alert rules (5K)
   - Enforced at Ingestion Gateway (series) and Alert Evaluator (rules)
   - Quota usage dashboard per tenant

3. QUERY ISOLATION:
   - Per-tenant query concurrency limit (max 50 concurrent queries)
   - Query timeout: 30 seconds (prevent runaway queries from consuming resources)
   - Resource pools: dedicated TSDB read replicas for large tenants (optional)

4. ALERT ISOLATION:
   - Alert rules scoped to tenant
   - Notification channels scoped to tenant
   - Alert Evaluator distributes rules across pods by tenant for fair scheduling
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Ingestion buffer** | Kafka (500 partitions, 6h retention) | Absorbs 10× bursts; decouples ingestion from storage; durable |
| **Storage** | TSDB with 3 tiers (hot/warm/cold) | Raw data for debugging; rollups for long-term; cost-optimized by age |
| **Compression** | Gorilla (delta-of-delta + XOR) | 66× compression ratio; ~1.5 bytes/point; industry standard for TSDB |
| **Cardinality control** | Per-tenant series limits + tag cardinality limits | Prevents series explosion; protects TSDB from OOM; enforced at ingestion |
| **Alert evaluation** | Distributed across pods, consistent hashing by rule_id | 50K rules in 30s; stateful (PENDING→FIRING); checkpoint to PostgreSQL |
| **Notifications** | Grouping + dedup + silences + escalation | No alert storms; actionable notifications; maintenance windows |
| **Rollups** | 1-min and 1-hour aggregates, auto-selected by query range | Fast long-range queries; raw data for recent (detail preserved) |
| **Query engine** | PromQL-compatible with auto-tier selection | Familiar syntax; automatic resolution by time range; result caching |
| **Multi-tenancy** | Shared infra + per-tenant quotas + data isolation | Cost-efficient; tenant_id in every data path; noisy-neighbor protection |
| **Backpressure** | Kafka buffer + adaptive batching + load shedding | Graceful degradation; never lose high-priority metrics |

## Interview Talking Points

1. **"Kafka as ingestion buffer absorbing 10× burst traffic"** — 500 partitions, 6-hour retention. Normal: 1M dps/sec. Burst: 10M dps/sec absorbed by Kafka while TSDB consumers catch up. Adaptive batching trades latency for throughput under pressure.

2. **"Gorilla compression: 66× ratio, ~1.5 bytes/data point"** — Delta-of-delta for timestamps (1 bit for regular intervals). XOR for values (1-4 bits for slowly changing metrics). 16 TB instead of 1 PB uncompressed.

3. **"3-tier storage: hot (memory) → warm (SSD) → cold (S3 rollups)"** — Last 2 hours in memory (< 50ms queries). 15 days on SSD. Rollups in S3 (1-min for 90 days, 1-hour for 2 years). Query engine auto-selects tier.

4. **"Cardinality control: per-tenant series limit + tag cardinality limit"** — Prevents series explosion (user_id tag → trillions of series). Max 10M series/tenant, 10K unique values/tag. Enforced at ingestion. Dashboard shows usage.

5. **"Alert evaluation: 50K rules across 50 pods, consistent hashing, 30s cycle"** — Each pod owns 1000 rules. State machine: OK → PENDING → FIRING → RESOLVED. Checkpointed to PostgreSQL. Idempotent notifications for fault tolerance.

6. **"Notification pipeline: group + dedup + silence + escalate"** — 100 host alerts → 1 grouped notification. Cooldown dedup via Redis. Check silences before sending. Auto-escalate if unacknowledged after 30 minutes.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Prometheus / Datadog / New Relic** | Identical problem | We design the internals; these are production implementations |
| **Log Aggregation (ELK)** | Same collection pipeline | Logs are unstructured text; metrics are structured numeric time-series |
| **Distributed Tracing (Jaeger)** | Same telemetry domain | Tracing captures request flows; metrics capture system health |
| **Event-Driven Architecture** | Same Kafka pipeline | Metrics are periodic samples; events are discrete occurrences |
| **Notification System** | Same delivery pattern | Our notifications are alert-triggered; general notifications are user-triggered |
| **Leaderboard** | Same time-series aggregation | Leaderboard ranks users; metrics ranks/aggregates system measurements |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **TSDB** | VictoriaMetrics | Prometheus / Thanos / M3DB / InfluxDB | Prometheus: simpler, single-node; Thanos: multi-cluster Prometheus; InfluxDB: SQL-like queries |
| **Ingestion buffer** | Kafka | NATS / Pulsar / Kinesis | NATS: lighter weight; Pulsar: built-in tiered storage; Kinesis: serverless |
| **Query language** | PromQL | InfluxQL / SQL (TimescaleDB) / MetricsQL | SQL: familiar for analytics; MetricsQL: VictoriaMetrics-extended PromQL |
| **Dashboard** | Grafana | Kibana / Custom / Datadog UI | Kibana: if using ELK stack; Custom: full control; Datadog: managed |
| **Notification** | Custom + Kafka | Alertmanager (Prometheus) / PagerDuty API | Alertmanager: Prometheus-native; PagerDuty: managed escalation |
| **Cold storage** | S3 + Parquet | GCS / Azure Blob / ClickHouse | ClickHouse: fast analytical queries on cold data |
| **Compression** | Gorilla | Snappy / LZ4 / Zstd | Zstd: general purpose; Gorilla is time-series specific (better ratio) |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Facebook Gorilla: In-Memory Time Series Database (VLDB 2015)
- Prometheus Architecture & PromQL
- VictoriaMetrics: High-Performance TSDB
- Thanos: Highly Available Prometheus Setup
- Grafana Dashboard Architecture
- Alertmanager Notification Pipeline
- Time-Series Data Compression Techniques
- Cardinality Management in Monitoring Systems
