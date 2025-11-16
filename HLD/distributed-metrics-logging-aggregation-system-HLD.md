# Distributed Metrics Logging and Aggregation System - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [API Design](#api-design)
9. [Data Flow](#data-flow)
10. [Deep Dives](#deep-dives)
11. [Scalability & Reliability](#scalability--reliability)
12. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a unified distributed system that combines logging and metrics capabilities to:
- Collect logs from thousands of microservices and applications
- Collect performance metrics from infrastructure and applications
- Aggregate and analyze both logs and metrics in real-time
- Provide unified search across logs and metrics
- Enable correlation between logs and metrics
- Support real-time alerting on both logs and metrics
- Store petabytes of log data with efficient compression
- Process billions of log lines and metrics per day
- Provide sub-second query response for dashboards

### Scale Requirements
- **5,000 microservices** generating logs and metrics
- **10,000 servers/containers** running these services
- **100 billion log lines per day** (average 1KB per line)
- **10 billion metrics per day** (100 metrics/sec per service)
- **Storage**: 100TB logs/day, 10TB metrics/day
- **Query latency**: < 2 seconds for log search, < 1 second for metrics
- **Real-time alerting**: < 1 minute from log/metric generation
- **Retention**: Logs 30 days, metrics 90 days, aggregates 1 year

---

## Functional Requirements

### Must Have (P0)

1. **Log Collection**
   - Application logs (stdout, stderr, log files)
   - System logs (syslog, journald)
   - Container logs (Docker, Kubernetes)
   - Multiple log formats (JSON, plain text, structured)
   - Log levels (DEBUG, INFO, WARN, ERROR, FATAL)
   - Log enrichment (add metadata, tags)

2. **Metrics Collection**
   - System metrics (CPU, memory, disk, network)
   - Application metrics (latency, throughput, errors)
   - Custom metrics (business KPIs)
   - Metric types (gauge, counter, histogram, summary)
   - Metric tags and dimensions

3. **Data Processing**
   - Real-time log parsing and normalization
   - Metric aggregation and rollups
   - Log filtering and sampling
   - Data enrichment (geo, user info)
   - Schema extraction from logs

4. **Storage & Indexing**
   - Full-text search on logs
   - Time-series storage for metrics
   - Efficient compression
   - Automatic data retention
   - Hot/warm/cold storage tiers

5. **Querying & Visualization**
   - Unified query interface (logs + metrics)
   - Full-text log search
   - Metric aggregation queries
   - Log pattern analysis
   - Correlation between logs and metrics
   - Grafana and custom dashboards

6. **Alerting**
   - Log-based alerts (error rate, specific patterns)
   - Metric-based alerts (thresholds, anomalies)
   - Alert correlation (logs + metrics together)
   - Multiple notification channels
   - Alert rules configuration

7. **Administration**
   - Multi-tenancy support
   - Access control (RBAC)
   - Data retention policies
   - Cost allocation and tracking
   - Audit logging

### Nice to Have (P1)
- Machine learning for anomaly detection
- Automatic log clustering
- Predictive alerting
- Root cause analysis
- Distributed tracing integration
- Log replay capabilities
- Cost optimization recommendations
- Compliance reporting (SOC2, GDPR)

---

## Non-Functional Requirements

### Performance
- **Log ingestion**: < 200ms latency (p95)
- **Metrics ingestion**: < 100ms latency (p95)
- **Log search**: < 2 seconds for 1-day search
- **Metrics query**: < 1 second for dashboards
- **Indexing delay**: < 5 seconds (log searchable after ingestion)

### Scalability
- Handle 5,000+ services
- Process 100B log lines per day (1.15M logs/sec)
- Process 10B metrics per day (116K metrics/sec)
- Store petabytes of data
- Support 10K+ concurrent queries

### Availability
- **99.9% uptime** for data collection
- **99.95% uptime** for querying
- Multi-region deployment
- No data loss during failures
- Automatic failover (< 30 seconds)

### Consistency
- **Eventual consistency** for logs and metrics (acceptable)
- **At-least-once delivery** guarantee
- Idempotent processing

### Data Retention
- **Logs**: 30 days (configurable per tenant)
- **Metrics raw**: 7 days
- **Metrics 1-min**: 90 days
- **Metrics 1-hour**: 1 year
- **Archive**: S3/Glacier (optional, 7 years)

### Security
- TLS encryption for all data in transit
- Data encryption at rest
- API key and OAuth authentication
- RBAC for multi-tenancy
- Audit logging
- PII masking and redaction

---

## Capacity Estimation

### Traffic Estimates

```
Logs:
- Services: 5,000
- Logs per service: 20,000 per day
- Total logs: 100 billion per day
- Logs per second: 100B / 86,400 = 1.15 million logs/sec
- Peak (3x): 3.45 million logs/sec

Metrics:
- Services: 5,000
- Metrics per service: 100
- Collection interval: 10 seconds
- Metrics per second: (5,000 × 100) / 10 = 50,000 metrics/sec
- Peak (3x): 150,000 metrics/sec

Combined peak: 3.6M events/sec

Query Load:
- Active users: 5,000 concurrent
- Queries per user per minute: 2
- Total QPS: (5,000 × 2) / 60 = 167 QPS
```

### Storage Estimates

```
Logs:
- Average log size: 1 KB (includes message, timestamp, metadata, tags)
- Daily: 100B logs × 1 KB = 100 TB/day
- Monthly: 100 TB × 30 = 3 PB/month
- With 30-day retention: 3 PB

Logs after compression (5:1 ratio):
- Daily: 20 TB
- 30-day retention: 600 TB

Metrics:
- Average metric: 124 bytes (timestamp, name, value, tags)
- Daily: 10B metrics × 124 bytes = 1.24 TB/day
- 90-day retention (raw): 111.6 TB

Metrics aggregated (1-minute rollups):
- Daily: 10B / 60 = 167M rollups
- Storage: 167M × 200 bytes = 33.4 GB/day
- 1-year retention: 12.2 TB

Total Storage:
- Logs (compressed, 30 days): 600 TB
- Metrics (raw, 90 days): 112 TB
- Metrics (rollups, 1 year): 12 TB
- Indexes: 150 TB
- Total: ~874 TB

With replication (3x): 2.6 PB
```

### Processing Requirements

```
Log Processing:
- Logs per second: 3.45M (peak)
- Processing per log: 5ms (parsing, indexing, enriching)
- CPU cores: 3.45M × 0.005 = 17,250 cores

Metrics Processing:
- Metrics per second: 150K (peak)
- Processing per metric: 1ms
- CPU cores: 150K × 0.001 = 150 cores

Query Processing:
- Queries per second: 167
- Average query time: 2 seconds
- Concurrent capacity: 334 queries
- Query nodes: 50

Total compute: ~18,000 cores
```

### Network Bandwidth

```
Ingestion:
- Logs: 3.45M/sec × 1KB = 3.45 GB/s = 27.6 Gbps
- Metrics: 150K/sec × 124 bytes = 18.6 MB/s = 149 Mbps
- Total ingestion: ~28 Gbps

Inter-node replication:
- Replication factor: 3
- Replication bandwidth: 28 Gbps × 2 = 56 Gbps

Query traffic:
- QPS: 167
- Response size: 500 KB average
- Bandwidth: 167 × 500KB = 83.5 MB/s = 668 Mbps

Total: ~85 Gbps
```

### Cost Estimation

```
Monthly Costs (approximate):

Compute:
- Log processing: 500 instances × $200 = $100K
- Metric processing: 50 instances × $150 = $7.5K
- Query servers: 50 instances × $300 = $15K

Storage:
- SSD (hot - 7 days): 200 TB × $200/TB = $40K
- HDD (warm - 30 days): 1 PB × $30/TB = $30K
- S3 (cold - 1 year): 2 PB × $20/TB = $40K

Network:
- Data transfer: 100 TB/day × 30 × $0.05/GB = $150K

Total: ~$382K per month
Cost per service: $382K / 5,000 = $76.4/service/month
```

---

## High-Level Architecture

```
                [5,000 Microservices + 10,000 Servers]
                              |
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
  [Log Agents]        [Metric Agents]      [App Libraries]
  (Fluentd/Vector)    (Telegraf)           (Log4j, Winston)
        |                     |                     |
        └─────────────────────┼─────────────────────┘
                              ↓
                    [Load Balancer / CDN]
                    (Global, multi-region)
                              |
            ┌─────────────────┼─────────────────┐
            ↓                 ↓                 ↓
    [Log Collector]   [Metric Collector]  [Unified API]
    (Ingest logs)     (Ingest metrics)    (REST/gRPC)
            |                 |                 |
            └─────────────────┼─────────────────┘
                              ↓
                    [Message Queue - Kafka]
                    (Buffer & decouple)
                              |
            ┌─────────────────┼─────────────────────────┐
            ↓                 ↓                         ↓
    [Log Processing]  [Metric Processing]    [Correlation Engine]
    (Parse, enrich)   (Aggregate, rollup)    (Link logs to metrics)
    (Flink/Spark)     (Flink)                (Real-time analysis)
            |                 |                         |
            └─────────────────┼─────────────────────────┘
                              ↓
                    [Data Router]
                    (Route to storage tiers)
                              |
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
  [Log Storage]       [Metric Storage]      [Metadata DB]
  (Elasticsearch)     (InfluxDB/Prometheus) (PostgreSQL)
  (Full-text search)  (Time-series)         (Config, rules)
        |                     |                     |
        └─────────────────────┼─────────────────────┘
                              ↓
                    [Query Layer]
                              |
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
  [Log Query]         [Metric Query]        [Unified Query]
  (Lucene DSL)        (PromQL)              (SQL-like)
        |                     |                     |
        └─────────────────────┼─────────────────────┘
                              ↓
                    [Cache Layer - Redis]
                    (Query results, metadata)
                              ↓
                    [API Gateway]
                              |
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
  [Dashboards]         [Alert Manager]       [API Clients]
  (Grafana, Kibana)    (Alert routing)       (CLI, SDK)
        |                     |                     |
        └─────────────────────┼─────────────────────┘
                              ↓
                    [Notification System]
                    (Email, Slack, PagerDuty)
```

### Key Architectural Decisions

1. **Unified Collection Pipeline**
   - Single ingestion layer for logs and metrics
   - Common message queue (Kafka)
   - Shared processing infrastructure
   - Correlated storage

2. **Elasticsearch for Logs**
   - Full-text search capability
   - Flexible schema (JSON documents)
   - Horizontal scalability
   - Rich query DSL

3. **InfluxDB for Metrics**
   - Time-series optimized
   - High write throughput
   - Efficient compression
   - Fast aggregations

4. **Kafka for Decoupling**
   - Buffer between ingestion and processing
   - Handle traffic spikes
   - Enable replay and reprocessing
   - Support multiple consumers

5. **Multi-Tier Storage**
   - Hot: Last 7 days (SSD, fast queries)
   - Warm: 30 days (HDD, moderate speed)
   - Cold: 1+ year (S3/Glacier, archival)

---

## Core Components

### 1. Log Collection Layer

**Purpose**: Collect logs from all services and infrastructure

**Collection Methods**:

**A. Log Agents** (Deployed on each server):
```
Popular Agents:
- Fluentd: Ruby-based, rich plugins
- Fluent Bit: Lightweight C-based
- Logstash: Java-based, ELK stack
- Vector: Rust-based, high performance
- Filebeat: Lightweight, Elastic ecosystem

Agent responsibilities:
- Tail log files
- Parse log formats
- Add metadata (host, service, environment)
- Buffer locally during network issues
- Compress before sending
- Handle backpressure
```

**B. Application Libraries**:
```
Direct integration in application code:
- Log4j (Java)
- Winston (Node.js)
- Logrus (Go)
- Python logging

Benefits:
- Structured logging (JSON by default)
- Automatic context (trace ID, user ID)
- Performance optimized
- No file I/O overhead
```

**C. Sidecar Pattern** (Kubernetes):
```
Each pod has sidecar container:
- Main container: Application
- Sidecar container: Log agent

Benefits:
- Isolation from main app
- Independent scaling
- Centralized logging config
- No app code changes needed
```

**Log Format Standardization**:
```json
{
  "timestamp": "2025-01-16T03:00:00.123Z",
  "level": "ERROR",
  "service": "api-gateway",
  "host": "web-01.prod.us-west",
  "trace_id": "abc123def456",
  "user_id": "user_789",
  "message": "Database connection timeout",
  "error": {
    "type": "TimeoutException",
    "message": "Connection timeout after 5000ms",
    "stack_trace": "..."
  },
  "context": {
    "endpoint": "/api/v1/users",
    "method": "GET",
    "response_time_ms": 5123
  },
  "tags": {
    "environment": "production",
    "version": "2.3.1"
  }
}
```

### 2. Metrics Collection Layer

**Purpose**: Collect performance metrics from services and infrastructure

**Collection Methods**:

**A. Agent-Based** (Push model):
```
Agents deployed on servers:
- Telegraf
- Datadog agent
- Custom agents

Collect:
- System metrics (CPU, memory, disk, network)
- Application metrics (via StatsD, HTTP endpoints)
- Container metrics (Docker API, cAdvisor)

Push to collectors every 10 seconds
```

**B. Pull-Based** (Prometheus-style):
```
Scrapers pull from service endpoints:
- Services expose /metrics endpoint
- Prometheus format (OpenMetrics)
- Scrape interval: 15-30 seconds

Benefits:
- Service discovery integration
- Targets don't need agent
- Easy debugging
```

**C. SDK Integration**:
```
Application directly reports metrics:
- OpenTelemetry SDK
- Micrometer (Java)
- statsd-client

Benefits:
- Low overhead
- Custom metrics easy
- Business KPIs tracked
```

**Metric Types**:
```
1. Gauge: Current value (CPU%, memory used)
2. Counter: Cumulative count (requests, errors)
3. Histogram: Distribution (request duration buckets)
4. Summary: Statistical distribution (p50, p95, p99)
```

### 3. Unified Ingestion Pipeline

**Purpose**: Single entry point for logs and metrics

**Architecture**:
```
[Load Balancer]
    ↓
[API Gateway]
    ├─ /logs/ingest (log endpoint)
    ├─ /metrics/ingest (metric endpoint)
    └─ /events/ingest (unified endpoint)
    ↓
[Ingestion Service]
    ├─ Authentication
    ├─ Validation
    ├─ Rate limiting
    ├─ Enrichment
    └─ Routing
    ↓
[Kafka Topics]
    ├─ raw-logs (logs stream)
    └─ raw-metrics (metrics stream)
```

**Processing Steps**:

**For Logs**:
```
1. Receive log batch (up to 1000 logs)
2. Validate JSON format
3. Extract timestamp (or add if missing)
4. Add server-side metadata:
   - Receipt timestamp
   - Ingestion server ID
   - Source IP (for geo-lookup)
5. Normalize log level (INFO, WARN, ERROR)
6. Check for PII (mask if found)
7. Compress batch
8. Publish to Kafka: raw-logs topic
9. Return 202 Accepted
```

**For Metrics**:
```
1. Receive metric batch (up to 1000 metrics)
2. Validate metric name and tags
3. Check timestamp (reject if too old)
4. Normalize metric name (lowercase, dots)
5. Add metadata tags
6. Compress batch
7. Publish to Kafka: raw-metrics topic
8. Return 202 Accepted
```

**Rate Limiting**:
```
Per service:
- Logs: 100K logs/minute
- Metrics: 100K metrics/minute

Per tenant:
- Logs: 10M logs/minute
- Metrics: 1M metrics/minute

Backpressure:
- Return 429 if limits exceeded
- Or queue with degraded SLA
```

### 4. Stream Processing Pipeline

**Purpose**: Real-time processing of logs and metrics

**Architecture**:
```
[Kafka Streams]
    ↓
[Apache Flink / Spark Streaming]
    ↓
┌───────────────┴───────────────┐
↓                               ↓
[Log Processing]        [Metric Processing]
    ├─ Parse formats           ├─ Aggregate
    ├─ Extract fields          ├─ Calculate rollups
    ├─ Enrich with geo         ├─ Detect anomalies
    ├─ PII masking             └─ Update counters
    ├─ Pattern detection
    └─ Error classification
    ↓                               ↓
[Indexed Logs]          [Aggregated Metrics]
    ↓                               ↓
[Elasticsearch]         [InfluxDB / Cortex]
```

**Log Processing Jobs**:

**1. Log Parsing**:
```
Input: Raw log string
Process:
- Detect format (JSON, syslog, common, custom)
- Parse into structured fields
- Extract timestamp
- Identify log level
- Extract error information

Output: Structured log document
```

**2. Field Extraction**:
```
Extract common fields:
- User ID
- Request ID / Trace ID
- Endpoint / URL
- Response time
- Status code
- Error messages

Use regex patterns or grok patterns
```

**3. Enrichment**:
```
Add contextual information:
- Geo-location from IP
- Service metadata from registry
- User information from cache
- Request context from distributed tracing
```

**4. PII Masking**:
```
Detect and mask:
- Email addresses
- Phone numbers
- Credit card numbers
- SSNs, API keys

Replace with: [REDACTED]
Store original in secure vault (if needed)
```

**5. Log Pattern Clustering**:
```
Group similar logs:
"User 123 logged in" → Pattern: "User {user_id} logged in"
"User 456 logged in" → Same pattern

Benefits:
- Reduce unique log count
- Identify common patterns
- Better compression
```

**Metric Processing Jobs**:

**1. Metric Aggregation**:
```
Tumbling windows:
- 1 minute: Calculate avg, min, max, count, p50, p95, p99
- 1 hour: Aggregate from 1-minute
- 1 day: Aggregate from 1-hour

Group by: metric_name + tags
```

**2. Anomaly Detection**:
```
Compare current value to baseline:
- Historical average ± 3 std deviations
- Seasonal patterns (hourly, daily, weekly)
- ML models for complex patterns

Flag anomalies for investigation
```

**3. Derived Metrics**:
```
Calculate from raw metrics:
- Error rate: errors / total_requests
- Apdex score: (satisfied + tolerating/2) / total
- Availability: uptime / (uptime + downtime)
```

### 5. Log Storage (Elasticsearch)

**Purpose**: Store logs with full-text search capability

**Why Elasticsearch**:
- Full-text search (inverted indexes)
- JSON document storage (flexible schema)
- Horizontal scalability
- Lucene-based (battle-tested)
- Rich aggregation capabilities

**Index Strategy**:
```
Index pattern: logs-{service}-{date}
Example: logs-api-gateway-2025-01-16

Benefits:
- Time-based partitioning
- Easy deletion (drop old indices)
- Per-service isolation
- Efficient queries within service
```

**Document Structure**:
```json
{
  "@timestamp": "2025-01-16T03:00:00.123Z",
  "level": "ERROR",
  "service": "api-gateway",
  "host": "web-01",
  "environment": "production",
  "datacenter": "us-west-2",
  "message": "Database connection timeout",
  "error": {
    "type": "TimeoutException",
    "stack_trace": "..."
  },
  "context": {
    "trace_id": "abc123",
    "user_id": "user_789",
    "endpoint": "/api/v1/users",
    "method": "GET",
    "duration_ms": 5123
  },
  "tags": ["timeout", "database"]
}
```

**Index Settings**:
```
Shards: 10 per index (based on data size)
Replicas: 1 (2 total copies)
Refresh interval: 5 seconds (searchable delay)
Compression: Best compression (saves 50% storage)
Merge policy: Tiered (optimize for search)
```

**Index Lifecycle Management (ILM)**:
```
Phase 1 - Hot (0-7 days):
- Store on SSD
- Full search capability
- No deletion

Phase 2 - Warm (7-30 days):
- Move to HDD
- Merge segments
- Shrink shards
- Read-only

Phase 3 - Delete (> 30 days):
- Delete index
- Or snapshot to S3 for archival
```

### 6. Metric Storage (Time-Series DB)

**Purpose**: Store metrics efficiently with fast aggregation

**Data Model**:
```
Metric: http_requests_total
Tags: {
  service: api-gateway,
  method: GET,
  status: 200,
  environment: production
}
Value: 123456 (counter)
Timestamp: 1705363200
```

**Storage Tiers**:
```
Hot (Last 7 days):
- In-memory + SSD
- Full resolution (10s intervals)
- Fast queries (< 100ms)

Warm (7-90 days):
- SSD storage
- 1-minute rollups
- Fast queries (< 500ms)

Cold (90+ days):
- HDD/S3 storage
- 1-hour rollups
- Slower queries (1-2 seconds)
```

**Retention Policies**:
```
Auto-downsample and delete:
- Raw (10s): Keep 7 days, then downsample
- 1-minute: Keep 90 days, then downsample
- 1-hour: Keep 1 year, then downsample
- 1-day: Keep 5 years, then delete
```

### 7. Unified Query Engine

**Purpose**: Query both logs and metrics through single interface

**Query Types**:

**1. Log Search** (Elasticsearch DSL):
```
Search for errors in last hour:
{
  "query": {
    "bool": {
      "must": [
        {"match": {"level": "ERROR"}},
        {"match": {"service": "api-gateway"}}
      ],
      "filter": [
        {"range": {"@timestamp": {"gte": "now-1h"}}}
      ]
    }
  },
  "sort": [{"@timestamp": "desc"}],
  "size": 100
}
```

**2. Metric Query** (PromQL):
```
Average CPU usage last 5 minutes:
avg(cpu_usage_percent{service="api"}) [5m]

Request rate:
rate(http_requests_total[5m])

Error rate:
sum(rate(http_errors_total[5m])) / sum(rate(http_requests_total[5m]))
```

**3. Unified Query** (SQL-like):
```
Correlate logs and metrics:

SELECT 
  m.timestamp,
  m.value as cpu_usage,
  COUNT(l.*) as error_count
FROM metrics m
LEFT JOIN logs l 
  ON m.host = l.host 
  AND l.level = 'ERROR'
  AND l.timestamp BETWEEN m.timestamp - 1m AND m.timestamp + 1m
WHERE m.metric_name = 'cpu.usage.percent'
  AND m.timestamp > NOW() - INTERVAL '1 hour'
GROUP BY m.timestamp, m.value
ORDER BY error_count DESC;
```

**Query Router**:
```
Based on query type, route to appropriate storage:
- Log search → Elasticsearch
- Metric query → InfluxDB
- Unified query → Federated query across both
- Cache check → Redis

Caching strategy:
- Cache query results
- TTL based on time range (recent = shorter TTL)
- Invalidate on data updates
```

### 8. Alert Engine

**Purpose**: Monitor logs and metrics for alert conditions

**Alert Types**:

**1. Log-Based Alerts**:
```
Alert on log patterns:
- Error rate > 10 errors/minute
- Specific error message appears
- Log volume spike (> 2x normal)
- Critical log level appears

Example:
{
  "name": "HighErrorRate",
  "type": "log",
  "query": "level:ERROR AND service:api",
  "window": "5m",
  "threshold": "count > 100",
  "severity": "high"
}
```

**2. Metric-Based Alerts**:
```
Alert on metric thresholds:
- CPU > 90%
- Disk space < 10%
- Request latency p95 > 1000ms
- Error rate > 5%

Example:
{
  "name": "HighCPU",
  "type": "metric",
  "query": "avg(cpu_usage{service='api'})",
  "threshold": "> 90",
  "for": "5m",
  "severity": "critical"
}
```

**3. Correlated Alerts**:
```
Alert when logs AND metrics indicate problem:
- High CPU AND increasing error logs
- Disk full AND database write errors
- High latency AND timeout logs

Example:
{
  "name": "ServiceDegradation",
  "type": "correlated",
  "conditions": [
    {"metric": "latency_p95", "threshold": "> 1000"},
    {"log": "level:ERROR", "count": "> 50"}
  ],
  "window": "5m"
}
```

**Alert Evaluation**:
```
Scheduled execution (every 1 minute):
1. Fetch all enabled alert rules
2. Execute queries (logs and metrics)
3. Evaluate conditions
4. Track alert states:
   OK → PENDING → FIRING → RESOLVED
5. Group similar alerts
6. Send notifications
7. Record in alert history
```

**Alert Grouping**:
```
Group by: service, severity, alert_name
Suppress duplicates within 5-minute window

Example:
- 10 hosts have high CPU
- Group into 1 alert
- List all affected hosts
- Reduce alert fatigue
```

---

## Database Design

### 1. Log Storage (Elasticsearch)

**Index Template**:
```
Index name: logs-{service}-{date}
Mapping:
- @timestamp: date (sortable)
- level: keyword (not analyzed)
- service: keyword
- host: keyword
- message: text (full-text search)
- error.type: keyword
- error.stack_trace: text
- context.*: object (dynamic)
- tags: keyword array

Shards: 10 (adjustable based on size)
Replicas: 1
Refresh interval: 5s
```

**Search Optimization**:
```
Inverted index on:
- message (full-text)
- level, service, host (exact match)
- tags (array of keywords)

Doc values for:
- Sorting and aggregations
- Reduce memory usage

Source storage:
- Compress with DEFLATE (50% savings)
```

**Query Performance**:
```
Search patterns:
1. Recent logs (last hour): < 1 second
2. Term search (exact match): < 500ms
3. Full-text search: 1-3 seconds
4. Aggregations (count by field): 2-5 seconds

Optimization:
- Use filters over queries (cacheable)
- Limit result size (pagination)
- Use scroll API for large results
- Avoid wildcard at start of term
```

### 2. Metric Storage (InfluxDB/Cortex)

**Schema**:
```
Measurement (table): http_requests_total

Tag set (indexed):
- service: VARCHAR
- method: VARCHAR
- status_code: VARCHAR
- environment: VARCHAR

Field set (not indexed):
- value: FLOAT64
- count: INT64

Timestamp: TIMESTAMP (primary sort key)

Series cardinality:
services (10) × methods (5) × status (10) × envs (3) = 1,500 series
```

**Storage Format**:
```
TSM (Time-Structured Merge) files:
- Immutable files on disk
- Compressed time-series blocks
- Index for fast lookups
- Compaction to merge old blocks

Compression:
- Timestamps: Delta-of-delta encoding
- Values: XOR floating point compression
- Tags: Dictionary encoding

Ratio: 10:1 typical
```

**Retention & Compaction**:
```
Continuous queries:
CREATE CONTINUOUS QUERY "1min_avg" ON "metrics"
BEGIN
  SELECT mean(value) AS avg_value
  INTO "metrics"."1min"."avg_metrics"
  FROM "metrics"."raw"."all_metrics"
  GROUP BY time(1m), *
END

Auto-delete old data:
ALTER RETENTION POLICY "raw" ON "metrics" 
  DURATION 7d 
  REPLICATION 2 
  DEFAULT
```

### 3. Metadata Database (PostgreSQL)

**Services Registry**:
```
Table: services

Columns:
- service_id: UUID PRIMARY KEY
- name: VARCHAR(255) UNIQUE
- description: TEXT
- owner_team: VARCHAR(100)
- repository_url: VARCHAR(500)
- endpoints: JSONB
- log_format: VARCHAR(50)
- metric_prefix: VARCHAR(100)
- tags: JSONB
- created_at: TIMESTAMP
- updated_at: TIMESTAMP
```

**Alert Rules**:
```
Table: alert_rules

Columns:
- rule_id: UUID PRIMARY KEY
- name: VARCHAR(255)
- type: ENUM('log', 'metric', 'correlated')
- query: TEXT
- threshold: VARCHAR(100)
- window: INTERVAL
- severity: ENUM('info', 'warning', 'critical')
- notification_channels: JSONB
- enabled: BOOLEAN
- created_at: TIMESTAMP
```

**Dashboards**:
```
Table: dashboards

Columns:
- dashboard_id: UUID PRIMARY KEY
- name: VARCHAR(255)
- description: TEXT
- panels: JSONB
- variables: JSONB
- refresh_interval: INTEGER
- is_public: BOOLEAN
- created_by: VARCHAR(100)
- created_at: TIMESTAMP
```

---

## API Design

### Log Ingestion API

```
POST /api/v1/logs/ingest

Request:
{
  "service": "api-gateway",
  "logs": [
    {
      "timestamp": "2025-01-16T03:00:00.123Z",
      "level": "ERROR",
      "message": "Database timeout",
      "context": {"endpoint": "/api/users"}
    }
  ]
}

Response:
{
  "status": "accepted",
  "received": 100,
  "rejected": 0,
  "batch_id": "batch_abc123"
}
```

### Metric Ingestion API

```
POST /api/v1/metrics/ingest

Request:
{
  "service": "api-gateway",
  "host": "web-01",
  "metrics": [
    {
      "name": "cpu.usage.percent",
      "value": 75.3,
      "timestamp": 1705363200,
      "tags": {"core": "0"}
    }
  ]
}

Response:
{
  "status": "accepted",
  "received": 100,
  "metrics_per_second": 10000
}
```

### Query APIs

**Log Search**:
```
POST /api/v1/logs/search

Request:
{
  "query": "level:ERROR AND service:api",
  "time_range": {
    "start": "2025-01-16T00:00:00Z",
    "end": "2025-01-16T03:00:00Z"
  },
  "size": 100,
  "sort": [{"@timestamp": "desc"}]
}

Response:
{
  "hits": {
    "total": 1234,
    "logs": [...]
  },
  "took_ms": 234
}
```

**Metric Query**:
```
GET /api/v1/metrics/query?q=avg(cpu{service='api'}[5m])&start=now-1h&end=now

Response:
{
  "result": [
    {"timestamp": 1705363200, "value": 75.3},
    {"timestamp": 1705363210, "value": 76.1},
    ...
  ],
  "query_time_ms": 123
}
```

**Unified Query**:
```
POST /api/v1/query/correlate

Request:
{
  "log_query": "level:ERROR",
  "metric_query": "cpu_usage{service='api'}",
  "time_range": "last_1h",
  "correlation": "by_host"
}

Response:
{
  "correlations": [
    {
      "host": "web-01",
      "error_count": 50,
      "avg_cpu": 92.3,
      "correlation_score": 0.85
    }
  ]
}
```

---

## Data Flow

### End-to-End Flow: Error Log with High CPU

```
T=0ms: Application encounters error
    ↓
[Application logs error]
Log: "Database connection timeout"
    ↓
[Log library (Log4j)]
- Format as JSON
- Add trace_id, user_id
    ↓
[Log agent (Fluentd)]
- Read from stdout
- Add host, service metadata
    ↓
T=50ms: Send to log collector
POST /api/v1/logs/ingest
    ↓
[Ingestion Service]
- Validate format
- Mask PII if found
- Publish to Kafka: raw-logs
    ↓
T=100ms: Return 202 Accepted
─────────────────────────────────
ASYNC PROCESSING
    ↓
[Flink Log Processor]
T=200ms:
- Parse log
- Extract error type
- Enrich with geo data
- Calculate error rate
    ↓
[Write to Elasticsearch]
T=500ms: Log indexed and searchable
    ↓
[Alert Evaluator]
T=60s: Check error rate alert
- Query: Count errors in last 5 min
- Threshold: > 100 errors
- Current: 50 errors → OK (no alert)
─────────────────────────────────
MEANWHILE (Metrics)
    ↓
T=0s: Metric agent collects CPU
- Read /proc/stat
- Calculate: 95.3% CPU usage
    ↓
T=10ms: Send to metric collector
POST /api/v1/metrics/ingest
    ↓
[Metric Processor]
- Aggregate over 1 minute
- Store in InfluxDB
    ↓
T=60s: Alert evaluator checks CPU alert
- Query: avg(cpu) over 5 min
- Threshold: > 90%
- Current: 95.3% → ALERT!
    ↓
[Alert triggered: HighCPU on web-01]
    ↓
[Check correlated logs]
- Query logs for same host, same time
- Found 50 error logs
- Correlation score: HIGH
    ↓
[Send notification]
"⚠️ High CPU (95%) on web-01 + 50 errors in last 5min"
    ↓
T=90s: Engineer receives alert
```

---

## Deep Dives

### 1. Log Correlation with Distributed Tracing

**Challenge**: Connect logs across distributed services

**Solution**: Trace ID propagation

**Implementation**:
```
Request flow:
User → API Gateway → Auth Service → Database
  (trace_id: abc123 propagated through all services)

Logs at each service:
API Gateway: "Request received" trace_id=abc123
Auth Service: "Validating token" trace_id=abc123
Database: "Query executed" trace_id=abc123

Query logs by trace_id:
- Get complete request journey
- Identify where failure occurred
- Calculate latency breakdown
```

**Trace Context**:
```
W3C Trace Context standard:
traceparent: 00-{trace-id}-{span-id}-{trace-flags}

Example:
traceparent: 00-abc123def456-789ghi-01

Propagated via HTTP headers:
- Incoming request: Extract traceparent
- Outgoing request: Add traceparent
- Logs: Include trace_id from context
```

**Benefits**:
```
1. End-to-end visibility
2. Root cause analysis
3. Latency attribution
4. Error source identification
5. Service dependency mapping
```

### 2. Log Sampling Strategy

**Problem**: Too many logs overwhelm system

**Sampling Types**:

**1. Head Sampling** (At collection):
```
Sample before sending:
- DEBUG logs: Sample 10%
- INFO logs: Sample 50%
- WARN logs: Sample 90%
- ERROR logs: Sample 100% (never sample errors)

Benefits:
+ Reduce ingestion load
+ Lower costs

Cons:
- May miss important logs
- Sampling decision made early
```

**2. Tail Sampling** (After collection):
```
Sample after seeing full trace:
- If trace has error: Keep all logs
- If trace slow (> 1s): Keep all logs
- If trace normal: Sample 10%

Benefits:
+ Keep important logs
+ Context-aware sampling

Cons:
- Higher ingestion cost
- More complex
```

**3. Adaptive Sampling**:
```
Adjust rate based on volume:
- Normal traffic: 100% sampling
- High traffic (> 2x): 50% sampling
- Very high (> 5x): 10% sampling

Dynamic adjustment every minute
```

**Implementation**:
```
Agent configuration:
sampling:
  strategy: adaptive
  base_rate: 1.0 (100%)
  rate_by_level:
    DEBUG: 0.1
    INFO: 0.5
    WARN: 0.9
    ERROR: 1.0
  intelligent:
    enabled: true
    keep_traces_with_errors: true
    keep_slow_traces: true
    slow_threshold_ms: 1000
```

### 3. Cost Optimization Strategies

**Challenge**: Petabytes of data = expensive

**Optimization Techniques**:

**1. Hot/Warm/Cold Tiering**:
```
Hot (SSD): Last 7 days
- Fast queries
- Expensive: $200/TB/month

Warm (HDD): 7-30 days
- Moderate speed
- Moderate cost: $30/TB/month

Cold (S3): 30+ days
- Slow queries
- Cheap: $20/TB/month

Savings: 70% compared to all-SSD
```

**2. Compression**:
```
Logs:
- Raw: 100 TB/day
- Compressed (5:1): 20 TB/day
- Savings: 80%

Metrics:
- Raw: 10 TB/day
- Compressed (10:1): 1 TB/day
- Savings: 90%
```

**3. Sampling**:
```
Sample non-critical logs:
- DEBUG: Sample 90% (only 10% stored)
- INFO: Sample 50%
- Keep all ERROR/WARN

Savings: 40% on log volume
```

**4. Retention Tuning**:
```
Not all data needs long retention:
- Critical services: 90 days
- Non-critical: 30 days
- Development: 7 days

Per-service retention policies
Savings: 50% on storage
```

**5. Index Optimization**:
```
Don't index everything:
- message: Full-text index (expensive)
- stack_trace: Store only, don't index
- Large fields: Disable indexing

Savings: 30% on index size
```

**Total Cost Reduction**: 60-70% with all optimizations

### 4. Real-Time Log Pattern Detection

**Challenge**: Identify new error patterns automatically

**Approach**: Log clustering

**Algorithm**:
```
1. Extract log message
2. Tokenize: "User 123 failed login" → ["User", "{ID}", "failed", "login"]
3. Replace variables with {VAR}
4. Hash pattern
5. Group logs by pattern hash
6. Track pattern frequency
```

**Pattern Examples**:
```
Original logs:
- "User 123 logged in from 192.168.1.1"
- "User 456 logged in from 10.0.0.1"
- "User 789 logged in from 172.16.0.1"

Detected pattern:
"User {user_id} logged in from {ip_address}"

Count: 3 occurrences
```

**Alert on New Patterns**:
```
New error pattern detected:
- Pattern: "Database {db_name} connection pool exhausted"
- First seen: 2025-01-16 03:00:00
- Frequency: 50 times in 5 minutes
- Alert: NEW_ERROR_PATTERN
```

**Benefits**:
```
- Automatic issue detection
- No need to pre-define patterns
- Discover unknown unknowns
- Reduce alert rule complexity
```

### 5. Metric Cardinality Management

**Problem**: Unbounded tag combinations

**Example of Bad Practice**:
```
Metric: api_request_latency
Tags: {
  endpoint: "/api/users/{user_id}",  ← user_id is unique!
  user_id: "123456"                   ← millions of users
}

Result: Million of unique series
Index explodes, queries slow
```

**Best Practices**:
```
Good tags (bounded):
- service: 10 values
- environment: 3 values
- method: 5 values
- status_code: 10 values
Total combinations: 1,500 series ✓

Bad tags (unbounded):
- user_id: millions ❌
- request_id: infinite ❌
- timestamp: infinite ❌
```

**Solutions**:

**1. Tag Whitelisting**:
```
Allowed tags:
- service, environment, datacenter
- method, status_code
- version

Reject unknown tags
Prevents accidental high-cardinality
```

**2. Cardinality Limits**:
```
Per metric name:
- Max 10,000 unique series
- Alert if approaching limit
- Reject new series if exceeded

Monitoring:
SELECT metric_name, COUNT(DISTINCT series_hash)
FROM metrics
GROUP BY metric_name
HAVING COUNT(*) > 5000
```

**3. Alternative Storage for High-Cardinality**:
```
Use logs for high-cardinality data:
- Store user-level data in logs
- Aggregate in logs (Elasticsearch aggregation)
- Don't create per-user metrics
```

### 6. Cross-Region Aggregation

**Challenge**: Services deployed globally, need unified view

**Architecture**:
```
[US Region]          [EU Region]          [APAC Region]
    ↓                    ↓                    ↓
[Local Processing]  [Local Processing]  [Local Processing]
    ↓                    ↓                    ↓
[Local Storage]     [Local Storage]     [Local Storage]
    ↓                    ↓                    ↓
[Regional Dashboards] [Regional Dashboards] [Regional Dashboards]
    └────────────────────┼────────────────────┘
                         ↓
            [Cross-Region Aggregator]
            (Federated queries)
                         ↓
            [Global Dashboard]
            (Worldwide view)
```

**Implementation**:
```
Regional storage:
- Each region stores its data locally
- Low latency for regional queries
- Compliance (data residency)

Global aggregation:
- Batch job aggregates across regions
- Hourly rollups synchronized
- Federated queries for real-time global view

Query routing:
- Regional query → Local storage
- Global query → Federate across regions
- Cache global results
```

**Data Synchronization**:
```
Metadata replicated globally:
- Alert rules
- Dashboard definitions
- User preferences

Metrics/logs stay regional:
- Too large to replicate
- Compliance requirements
- Query across regions when needed
```

---

## Scalability & Reliability

### Horizontal Scaling

**Ingestion Layer**:
```
Stateless services behind load balancer

Current: 50 instances, 3.6M events/sec
Scale up: Add 50 instances → 7.2M events/sec

Auto-scaling triggers:
- CPU > 70%: Scale up
- Queue depth > 1M: Scale up
- CPU < 30%: Scale down
```

**Elasticsearch Cluster**:
```
Scale by adding data nodes:

Current: 50 data nodes, 600TB storage
Each node: 12TB storage, 10K writes/sec

Add 25 nodes:
- New capacity: 900TB storage, 750K writes/sec
- Rebalancing: 2-4 hours
- No downtime
```

**Time-Series DB**:
```
Horizontal scaling:

InfluxDB Enterprise / Cortex:
- Add new nodes to cluster
- Rebalance shards automatically
- Linear scaling

Current: 20 nodes, 112TB
Add 10 nodes: 168TB capacity
```

**Query Layer**:
```
Add query nodes:
- Each node can query full dataset
- Load balancer distributes queries
- Cache shared via Redis

Current: 20 query nodes, 167 QPS
Add 20 nodes: 334 QPS capacity
```

### High Availability

**Multi-Region Deployment**:
```
3 Regions: US-East, EU-West, AP-South

Each region:
- Complete stack (ingestion, processing, storage)
- Independent operation
- Regional data

Benefits:
- Low latency (serve from nearest region)
- Disaster recovery
- Compliance (GDPR, data residency)
```

**Component Redundancy**:

**Ingestion**:
```
- Multiple instances (N+2 redundancy)
- Health checks every 10 seconds
- Auto-replace failed instances
- Zero downtime deployments
```

**Kafka**:
```
- 5+ brokers per cluster
- Replication factor: 3
- Min in-sync replicas: 2
- Automatic leader election
```

**Elasticsearch**:
```
- Master nodes: 3 (quorum)
- Data nodes: 50+ (distributed)
- Replicas: 1 per shard
- Automatic shard reallocation
```

**Time-Series DB**:
```
- Replication: 3x
- Automatic failover
- Read from any replica
- Write to quorum
```

### Data Durability

**No Data Loss Guarantee**:
```
1. Agent local buffering:
   - Disk-backed queue (1-hour capacity)
   - Retry on failure

2. Kafka durability:
   - Disk-backed partitions
   - Replication factor: 3
   - fsync on write

3. Database replication:
   - 3 replicas minimum
   - Synchronous replication
   - Quorum writes

Result: Data loss extremely rare
```

**Disaster Recovery**:
```
Backups:
- Elasticsearch: Daily snapshots to S3
- InfluxDB: Daily backups to S3
- PostgreSQL: Continuous WAL archiving

Retention: 30 days

Recovery procedures:
- Elasticsearch: Restore from snapshot (2-4 hours)
- InfluxDB: Restore from backup (1-2 hours)
- PostgreSQL: Point-in-time recovery (30 minutes)

RTO: 4 hours
RPO: 15 minutes (last backup + Kafka retention)
```

### Monitoring & Alerting

**System Health Metrics**:
```
Ingestion:
- logs.ingested_per_sec
- metrics.ingested_per_sec
- ingestion.lag_seconds
- ingestion.error_rate

Processing:
- kafka.consumer_lag
- flink.processing_rate
- flink.checkpoint_duration

Storage:
- elasticsearch.index_rate
- elasticsearch.query_latency
- influxdb.write_latency
- disk.usage_percent

Query:
- query.requests_per_sec
- query.latency_p95
- cache.hit_ratio
```

**Alerts on System**:
```
P0 (Critical):
- Ingestion lag > 5 minutes
- Disk space > 95%
- Elasticsearch cluster red
- Data loss detected

P1 (High):
- Query latency > 5 seconds
- Cache hit ratio < 50%
- Kafka consumer lag > 1M
- CPU > 90%

P2 (Medium):
- Storage growing faster than expected
- Slow queries detected
- High cardinality metrics
```

---

## Trade-offs & Alternatives

### 1. Elasticsearch vs ClickHouse for Logs

**Elasticsearch** (Chosen):
```
Pros:
+ Full-text search (inverted index)
+ Flexible schema (JSON)
+ Rich ecosystem (Kibana, Beats)
+ Mature and battle-tested

Cons:
- Resource intensive (RAM)
- Can be expensive at scale
- Requires tuning

Use when: Need full-text search, flexible schema
```

**ClickHouse**:
```
Pros:
+ Faster for structured logs
+ Better compression
+ Lower cost
+ SQL interface

Cons:
- No full-text search
- Limited schema flexibility
- Smaller ecosystem

Use when: Structured logs, cost priority
```

### 2. Kafka vs Alternatives

**Kafka** (Chosen):
```
Pros:
+ High throughput (1M+ msg/sec)
+ Durable (disk-backed)
+ Replay capability
+ Multiple consumers

Cons:
- Complex operations
- Higher latency
- Resource intensive

Use when: High volume, need durability
```

**Amazon Kinesis**:
```
Pros:
+ Fully managed
+ Auto-scaling
+ AWS integration

Cons:
- More expensive
- 1MB/sec shard limit
- Vendor lock-in

Use when: AWS-only, want managed
```

**Apache Pulsar**:
```
Pros:
+ Geo-replication built-in
+ Tiered storage (hot/cold)
+ Lower latency

Cons:
- Less mature
- Smaller ecosystem
- More complex

Use when: Need geo-replication
```

### 3. Push vs Pull Collection

**Hybrid** (Chosen):
```
Push for logs:
+ Agents control collection
+ Works behind firewalls
+ Real-time delivery

Pull for metrics:
+ Simple service endpoints
+ Service discovery
+ Easier debugging

Best of both worlds
```

### 4. Centralized vs Federated Storage

**Centralized** (Chosen):
```
Pros:
+ Simpler queries
+ Single source of truth
+ Easier operations

Cons:
- Single point of failure
- Higher latency for distant regions
- Compliance challenges

Use when: Single region, moderate scale
```

**Federated**:
```
Pros:
+ Lower latency (regional storage)
+ Better compliance
+ Fault isolation

Cons:
- Complex global queries
- Data consistency challenges
- Higher ops overhead

Use when: Global deployment, large scale
```

### 5. Structured vs Unstructured Logs

**Structured (JSON)** (Preferred):
```
Pros:
+ Easy to parse
+ Queryable fields
+ Machine-readable
+ Better compression

Cons:
- Larger log size
- Requires code changes

Use when: New services, control over code
```

**Unstructured (Plain Text)**:
```
Pros:
+ Simple
+ Human-readable
+ No code changes

Cons:
- Hard to parse
- Limited queryability
- Requires grok patterns

Use when: Legacy apps, cannot change code
```

---

## Conclusion

This Distributed Metrics Logging and Aggregation System processes **3.6M events per second** (logs + metrics) with:

**Key Features**:
- Unified collection pipeline for logs and metrics
- Real-time processing (< 5 second indexing)
- Petabyte-scale storage with tiering
- Sub-second queries for dashboards
- Correlated alerting (logs + metrics)
- 99.9% uptime with multi-region deployment

**Core Design Principles**:
1. **Separation of concerns**: Different storage for logs vs metrics
2. **Kafka buffering**: Decouples ingestion from processing
3. **Stream processing**: Real-time analysis and enrichment
4. **Multi-tier storage**: Optimize cost vs performance
5. **Horizontal scaling**: Linear scaling by adding nodes
6. **Correlation**: Link logs and metrics for RCA

**Technology Stack**:
- **Log Agent**: Fluentd / Fluent Bit / Vector
- **Metric Agent**: Telegraf / Prometheus exporters
- **Message Queue**: Apache Kafka
- **Stream Processing**: Apache Flink
- **Log Storage**: Elasticsearch
- **Metric Storage**: InfluxDB / Cortex / M3DB
- **Metadata**: PostgreSQL
- **Cache**: Redis
- **Dashboards**: Grafana + Kibana
- **Alerting**: Prometheus Alert Manager

The system scales to **100,000+ servers** and **1TB logs/day** by adding more Kafka partitions, Elasticsearch nodes, and processing workers.

**Cost Efficiency**:
- Infrastructure: $382K/month
- Cost per service: $76/month
- With optimizations: $150K/month (60% savings)

---

**Document Version**: 1.0  
**Last Updated**: November 16, 2025  
**Status**: Complete & Interview-Ready ✅
