# Distributed Logging & Monitoring System Design - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [Data Flow](#data-flow)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a scalable, distributed logging and monitoring system similar to Google's Borgmon/Monarch that enables:
- Centralized log collection from thousands of services
- Real-time metrics collection and aggregation
- Time-series data storage and querying
- Alerting on anomalies and threshold breaches
- Visualization dashboards
- Distributed tracing across microservices
- Log search and analysis
- Performance monitoring and SLA tracking

### Scale Requirements
- **10,000+ services** generating logs and metrics
- **1 billion log events per day**
- **100 million metrics per minute**
- **100K+ servers** being monitored
- **Query latency**: < 1 second for recent data
- **Data retention**: 30 days hot, 1 year warm, 7 years cold
- **99.9% availability** for monitoring system itself
- **Sub-second alerting** on critical issues

---

## Functional Requirements

### Must Have (P0)

#### 1. **Log Management**
- Collect logs from multiple sources (applications, servers, containers)
- Support structured and unstructured logs
- Parse and extract fields from logs
- Store logs with timestamps and metadata
- Search logs by keywords, time range, service
- Filter and aggregate logs
- Tail logs in real-time (live streaming)

#### 2. **Metrics Collection**
- Collect system metrics (CPU, memory, disk, network)
- Collect application metrics (requests/sec, latency, errors)
- Support custom metrics from applications
- Time-series data with timestamps
- Tags/labels for dimensional metrics
- Counter, gauge, histogram metric types
- Pull-based (scraping) and push-based collection

#### 3. **Alerting**
- Define alert rules on metrics/logs
- Multiple condition types (threshold, anomaly, rate of change)
- Alert severity levels (critical, high, medium, low)
- Alert routing to different channels (email, Slack, PagerDuty)
- Alert deduplication and grouping
- Silence/snooze alerts temporarily
- Alert escalation policies

#### 4. **Visualization**
- Create custom dashboards
- Real-time graphs and charts
- Multiple visualization types (line, bar, heatmap, table)
- Time-range selection and zoom
- Dashboard templates
- Share dashboards via links
- Export dashboard data

#### 5. **Query Language**
- SQL-like query language for logs
- PromQL-like query language for metrics
- Aggregation functions (sum, avg, max, min, percentile)
- Filtering and grouping
- Time-series operations (rate, delta, increase)
- Join operations across metrics

#### 6. **Distributed Tracing**
- Trace requests across microservices
- Visualize service dependencies
- Identify bottlenecks and latencies
- Span details (service, operation, duration)
- Trace sampling for high-volume systems

### Nice to Have (P1)
- Anomaly detection using ML
- Log pattern recognition
- Automatic dashboard generation
- Cost attribution and tracking
- Capacity planning recommendations
- Service mesh integration
- Log replay for debugging
- Multi-tenancy support
- Compliance and audit logs
- SLA reporting

---

## Non-Functional Requirements

### Performance
- **Log ingestion**: 1M events/second per cluster
- **Metrics ingestion**: 10M data points/second
- **Query latency**: < 1s for recent data (last 24h)
- **Query latency**: < 5s for historical data (last 30 days)
- **Alert evaluation**: < 10 seconds from metric arrival
- **Dashboard refresh**: < 2 seconds

### Scalability
- Support 10K+ services
- Handle 100M metrics/minute
- Store petabytes of log data
- Support 10K+ concurrent queries
- Scale horizontally (add nodes for capacity)

### Availability
- **99.9% uptime** (< 9 hours downtime/year)
- No single point of failure
- Multi-region deployment
- Graceful degradation (query recent data even if historical unavailable)

### Reliability
- **No data loss** for critical logs/metrics
- At-least-once delivery guarantee
- Data durability (11 nines for cold storage)
- Automatic failover
- Data replication (3x)

### Data Retention
- **Hot storage**: 30 days (fast queries)
- **Warm storage**: 1 year (slower queries)
- **Cold storage**: 7 years (archive, compliance)
- Configurable per service/metric

### Security
- Encryption at rest and in transit
- Role-based access control (RBAC)
- Audit logging of all access
- Data masking for sensitive fields
- API authentication (API keys, OAuth)

---

## Capacity Estimation

### Traffic Estimates
```
Services: 10,000
Servers: 100,000
Containers: 1,000,000

Logs:
- Log events/service/second: 100
- Total log events/second: 10K × 100 = 1M events/sec
- Daily: 1M × 86,400 = 86.4 billion events/day

Metrics:
- Metrics/server: 100 (CPU, memory, disk, network, etc.)
- Collection interval: 10 seconds
- Metrics/server/minute: 100 × 6 = 600
- Total metrics/minute: 100K × 600 = 60M metrics/minute
- Daily: 60M × 1,440 = 86.4 billion metrics/day
```

### Storage Estimates

**Log Storage**:
```
Average log size: 500 bytes (timestamp + level + message + metadata)
Daily log data: 86.4B × 500 bytes = 43.2 TB/day

30 days (hot): 43.2 TB × 30 = 1.3 PB
1 year (warm): 43.2 TB × 365 = 15.8 PB
With compression (5:1): 3.2 PB for 1 year

With 3x replication: 9.6 PB for 1 year
```

**Metrics Storage**:
```
Time-series data point: 16 bytes (timestamp: 8 bytes, value: 8 bytes)
Plus metadata: ~50 bytes per unique metric series

Daily metrics: 86.4B × 16 bytes = 1.4 TB/day
With metadata overhead: ~2 TB/day

30 days: 60 TB
1 year: 730 TB
With compression (10:1): 73 TB
With 3x replication: 219 TB
```

**Total Storage**:
```
Logs (1 year, compressed, replicated): 9.6 PB
Metrics (1 year, compressed, replicated): 219 TB = 0.2 PB
────────────────────────────────────────────
Total: ~10 PB
```

### Bandwidth Estimates
```
Log Ingestion:
- Data rate: 1M events/sec × 500 bytes = 500 MB/sec = 4 Gbps

Metrics Ingestion:
- Data rate: 1M metrics/sec × 16 bytes = 16 MB/sec = 128 Mbps

Query Traffic (20% of ingestion):
- Egress: ~1 Gbps

Total Bandwidth: ~5 Gbps ingress, 1 Gbps egress
```

### Memory Requirements
```
Query Cache (recent data):
- Cache last 1 hour of metrics: 2 TB/day ÷ 24 = 83 GB
- Cache factor (10x for index): 830 GB
- Distributed across 20 nodes: 42 GB per node

Indexing Structures:
- Inverted index for logs: ~500 GB
- Time-series index for metrics: ~100 GB

Active Queries:
- 1000 concurrent queries × 50 MB = 50 GB

Total Memory: ~1.5 TB distributed across cluster
```

### Server Estimates
```
Log Ingest Nodes:
- Each node: 50K events/sec capacity
- Needed: 1M / 50K = 20 nodes
- With redundancy (2x): 40 nodes

Metrics Ingest Nodes:
- Each node: 50K metrics/sec capacity
- Needed: 1M / 50K = 20 nodes
- With redundancy (2x): 40 nodes

Storage Nodes (Distributed across HDFS/S3):
- Hot storage: 50 nodes (25 TB each)
- Warm storage: 200 nodes

Query Nodes:
- Handle 10K concurrent queries
- Each node: 500 queries capacity
- Needed: 10K / 500 = 20 nodes
- With redundancy: 40 nodes

Total Servers: ~370 nodes

Cost Estimation (AWS):
- Compute (370 nodes): 370 × $200/month = $74K/month
- Storage (10 PB): $230K/month
- Bandwidth: $50K/month
- Total: ~$350K/month
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    DATA SOURCES                            │
│  Applications | Servers | Containers | Databases | Load   │
│  Balancers | Message Queues | External Services          │
└────────────────┬───────────────────────────────────────────┘
                 │
                 │ (Logs + Metrics)
                 ↓
┌────────────────────────────────────────────────────────────┐
│                  COLLECTION LAYER                          │
│  ┌──────────────────┐    ┌──────────────────┐            │
│  │  Log Collectors  │    │ Metrics Scrapers │            │
│  │  (Fluentd,       │    │ (Prometheus,     │            │
│  │   Logstash,      │    │  Telegraf)       │            │
│  │   Vector)        │    │                  │            │
│  └────────┬─────────┘    └────────┬─────────┘            │
└───────────┼──────────────────────┼─────────────────────────┘
            │                      │
            │                      │
            ↓                      ↓
┌────────────────────────────────────────────────────────────┐
│                    INGESTION LAYER                         │
│  ┌──────────────────┐    ┌──────────────────┐            │
│  │  Log Ingest API  │    │ Metrics Ingest   │            │
│  │  (Load Balanced) │    │ API (gRPC)       │            │
│  └────────┬─────────┘    └────────┬─────────┘            │
└───────────┼──────────────────────┼─────────────────────────┘
            │                      │
            │                      │
            ↓                      ↓
┌────────────────────────────────────────────────────────────┐
│              MESSAGE QUEUE (Kafka Cluster)                 │
│  Topics:                                                   │
│  - logs.raw (partitioned by service)                       │
│  - metrics.raw (partitioned by metric name)                │
│  - traces (distributed tracing data)                       │
└────────────────┬───────────────────────────────────────────┘
                 │
                 │
      ┌──────────┼──────────┐
      ↓          ↓           ↓
┌───────────┐ ┌──────────┐ ┌───────────┐
│   Log     │ │ Metrics  │ │  Trace    │
│ Processor │ │ Processor│ │ Processor │
│ Workers   │ │ Workers  │ │ Workers   │
└─────┬─────┘ └────┬─────┘ └─────┬─────┘
      │            │              │
      │            │              │
      ↓            ↓              ↓
┌────────────────────────────────────────────────────────────┐
│                   STORAGE LAYER                            │
│  ┌────────────────┐  ┌────────────────┐  ┌─────────────┐ │
│  │   Log Store    │  │  Metrics Store │  │ Trace Store │ │
│  │ (Elasticsearch)│  │ (Prometheus/   │  │  (Jaeger)   │ │
│  │                │  │  VictoriaMetrics│  │             │ │
│  └────────┬───────┘  └────────┬───────┘  └──────┬──────┘ │
└───────────┼──────────────────┼─────────────────┼─────────┘
            │                  │                 │
            │                  │                 │
            ↓                  ↓                 ↓
┌────────────────────────────────────────────────────────────┐
│              LONG-TERM STORAGE (Object Storage)            │
│  S3 / GCS / HDFS - Compressed, Partitioned by Time        │
└────────────────────────────────────────────────────────────┘
            │
            │
            ↓
┌────────────────────────────────────────────────────────────┐
│                    QUERY LAYER                             │
│  ┌──────────────────┐    ┌──────────────────┐            │
│  │  Log Query API   │    │ Metrics Query API│            │
│  │  (REST/GraphQL)  │    │ (PromQL/SQL)     │            │
│  └────────┬─────────┘    └────────┬─────────┘            │
└───────────┼──────────────────────┼─────────────────────────┘
            │                      │
            └──────────┬───────────┘
                       │
                       ↓
┌────────────────────────────────────────────────────────────┐
│                 VISUALIZATION LAYER                        │
│  ┌──────────────────┐    ┌──────────────────┐            │
│  │    Dashboards    │    │  Alert Manager   │            │
│  │  (Grafana)       │    │  (Prometheus AM) │            │
│  └──────────────────┘    └────────┬─────────┘            │
└─────────────────────────────────────┼─────────────────────┘
                                      │
                                      ↓
                        ┌─────────────────────────┐
                        │  Notification Services  │
                        │  Email | Slack | PagerDuty │
                        └─────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                CONFIGURATION & METADATA                     │
│  Service Registry | Alert Rules | Dashboard Configs        │
│  (etcd / Consul / ZooKeeper)                              │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              MONITORING & OBSERVABILITY                    │
│  Self-monitoring with same system (dogfooding)            │
│  Prometheus | Grafana | PagerDuty                         │
└────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **Kafka as Message Queue**
   - Decouples ingestion from processing
   - Handles bursts and backpressure
   - Replay capability for reprocessing
   - Partitioning for parallelism

2. **Elasticsearch for Logs**
   - Full-text search capabilities
   - Fast indexing and retrieval
   - Horizontal scalability
   - Rich query DSL

3. **Time-Series DB for Metrics**
   - Optimized for time-series data
   - Efficient compression (10:1)
   - Fast range queries
   - Downsampling and rollups

4. **Object Storage for Archives**
   - Cost-effective for cold data
   - Virtually unlimited capacity
   - Durability (11 nines)
   - Lifecycle policies

5. **Separation of Hot/Warm/Cold Data**
   - Fast SSD for recent data
   - HDD for warm data
   - Object storage for cold data
   - Query router determines source

---

## Core Components

### 1. Log Collectors (Fluentd/Logstash/Vector)

**Purpose**: Collect logs from applications and infrastructure

**Responsibilities**:
- Tail log files from disk
- Receive logs via syslog/HTTP
- Parse structured logs (JSON, CSV)
- Extract fields from unstructured logs
- Enrich logs with metadata
- Buffer logs during outages
- Forward logs to ingestion API

**Deployment Model**:
```
Sidecar Pattern (Kubernetes):
┌──────────────────────┐
│   Application Pod    │
│  ┌────────────────┐  │
│  │ App Container  │  │
│  │ (writes logs)  │  │
│  └───────┬────────┘  │
│          │ (shared   │
│          │  volume)  │
│  ┌───────▼────────┐  │
│  │ Log Collector  │  │
│  │ (reads & ships)│  │
│  └────────────────┘  │
└──────────────────────┘

Agent Pattern (VMs):
One agent per server collects from all applications
```

**Configuration Example**:
```yaml
# Fluentd config
<source>
  @type tail
  path /var/log/app/*.log
  pos_file /var/log/fluentd/app.log.pos
  tag app.logs
  <parse>
    @type json
    time_key timestamp
    time_format %Y-%m-%dT%H:%M:%S.%NZ
  </parse>
</source>

<filter app.logs>
  @type record_transformer
  <record>
    hostname "#{Socket.gethostname}"
    environment "production"
    service_name "user-service"
  </record>
</filter>

<match app.logs>
  @type http
  endpoint http://log-ingest-api.internal/logs
  <buffer>
    @type file
    path /var/log/fluentd/buffer
    flush_interval 5s
    retry_max_times 3
  </buffer>
</match>
```

**Key Features**:
- **Buffering**: Handle downstream failures gracefully
- **Batching**: Send logs in batches (reduce overhead)
- **Filtering**: Drop unnecessary logs
- **Multi-line parsing**: Stack traces, JSON payloads
- **Retry logic**: Exponential backoff

---

### 2. Metrics Collectors (Prometheus Exporters/Telegraf)

**Purpose**: Collect metrics from services and infrastructure

**Collection Models**:

**Pull-Based (Scraping)**:
```
Prometheus scrapes metrics from exporters:

1. Service exposes /metrics endpoint
2. Prometheus scrapes every 10-30 seconds
3. Metrics stored in Prometheus TSDB

Pros:
+ Service discovery built-in
+ Centralized scrape config
+ Easy to detect targets down

Cons:
- Firewall issues (Prometheus needs access)
- Not suitable for short-lived jobs
```

**Push-Based**:
```
Agents push metrics to collector:

1. Agent collects metrics locally
2. Agent pushes to Pushgateway/Ingest API
3. Metrics stored in TSDB

Pros:
+ Works behind firewalls
+ Good for batch jobs/lambdas
+ More flexible

Cons:
- Need to track which agents are alive
- More complex configuration
```

**Metrics Exposed**:
```
# System Metrics (node_exporter)
node_cpu_seconds_total{cpu="0",mode="idle"} 12345.67
node_memory_MemAvailable_bytes 8589934592
node_disk_io_time_seconds_total{device="sda"} 789.12
node_network_receive_bytes_total{device="eth0"} 1234567890

# Application Metrics (custom)
http_requests_total{method="GET",endpoint="/api/users",status="200"} 10234
http_request_duration_seconds{method="GET",endpoint="/api/users",quantile="0.95"} 0.25
http_requests_in_flight{method="GET",endpoint="/api/users"} 5
```

**Metric Types**:
```
1. Counter: Cumulative value (requests_total, errors_total)
   - Only increases
   - Rate() function for per-second rate

2. Gauge: Point-in-time value (memory_usage, queue_depth)
   - Can go up or down
   - Current snapshot

3. Histogram: Distribution of values (request_duration)
   - Multiple buckets
   - Calculate percentiles

4. Summary: Similar to histogram but client-side percentiles
   - Pre-calculated quantiles
   - Lower query cost
```

---

### 3. Ingestion API

**Purpose**: Receive logs and metrics from collectors

**Log Ingestion API**:
```http
POST /api/v1/logs/ingest
Content-Type: application/json
Authorization: Bearer <api_key>

Body:
{
  "logs": [
    {
      "timestamp": "2024-01-08T10:00:00Z",
      "level": "ERROR",
      "message": "Failed to connect to database",
      "service": "user-service",
      "host": "server-123",
      "trace_id": "abc123",
      "error": {
        "type": "ConnectionError",
        "stack_trace": "..."
      },
      "metadata": {
        "user_id": "user_456",
        "request_id": "req_789"
      }
    }
  ]
}

Response: 202 Accepted
{
  "accepted": 1,
  "rejected": 0,
  "message": "Logs queued for processing"
}
```

**Metrics Ingestion API (Prometheus Remote Write)**:
```http
POST /api/v1/metrics/write
Content-Type: application/x-protobuf
Content-Encoding: snappy
Authorization: Bearer <api_key>

Body: (Protobuf format - Prometheus remote write protocol)
[
  {
    labels: [
      {name: "__name__", value: "http_requests_total"},
      {name: "method", value: "GET"},
      {name: "status", value: "200"}
    ],
    samples: [
      {value: 12345, timestamp: 1704708000000}
    ]
  }
]

Response: 204 No Content (success)
```

**Processing Flow**:
```
1. Receive HTTP request
2. Authenticate & validate API key
3. Check rate limits (per service/API key)
4. Validate payload format
5. Publish to Kafka topic
6. Return 202 Accepted immediately
7. Async processing by workers

Total latency: < 50ms
```

**Rate Limiting**:
```
Per API Key:
- Logs: 100K events/minute
- Metrics: 1M data points/minute

Implementation: Token bucket in Redis
Key: "rate_limit:api_key:{key_id}"
```

---

### 4. Processing Workers

**Log Processing Worker**:

**Responsibilities**:
- Parse and structure logs
- Extract fields (regex, grok patterns)
- Enrich with metadata (geo-location, user info)
- Detect patterns and anomalies
- Index in Elasticsearch
- Archive to S3

**Processing Pipeline**:
```
1. Consume from Kafka logs.raw topic
2. Parse log message:
   - Detect format (JSON, plain text, syslog)
   - Extract timestamp, level, message, fields
3. Enrich:
   - Add service metadata from registry
   - Resolve IP to geo-location
   - Look up user details (if user_id present)
4. Transform:
   - Normalize field names
   - Convert data types
   - Hash sensitive fields (PII)
5. Index:
   - Bulk insert to Elasticsearch (1000 docs/batch)
6. Archive (async):
   - Write to S3 (partitioned by date and service)
7. Commit Kafka offset

Processing time: ~50ms per batch (1000 logs)
```

**Metrics Processing Worker**:

**Responsibilities**:
- Aggregate metrics (sum, count, avg)
- Downsample high-cardinality metrics
- Calculate rollups (5min, 1hour, 1day)
- Write to time-series database
- Trigger alert evaluation

**Processing Pipeline**:
```
1. Consume from Kafka metrics.raw topic
2. Group by metric name and labels
3. Aggregate within time window (10 seconds):
   - Sum counters
   - Average gauges
   - Merge histograms
4. Downsample if high cardinality:
   - Drop low-importance labels
   - Sample (keep 1/N data points)
5. Write to TSDB:
   - Batch write (10K data points)
   - Compressed write
6. Evaluate alert rules:
   - Check thresholds
   - Trigger alerts if needed
7. Generate rollups:
   - 5min avg, 1hour avg, 1day avg
   - Store for faster long-range queries
8. Commit Kafka offset

Processing time: ~10ms per batch (10K metrics)
```

---

### 5. Storage Layer

**Elasticsearch (Log Storage)**:

**Index Strategy**:
```
Index per day per service:
logs-user-service-2024.01.08
logs-payment-service-2024.01.08
logs-order-service-2024.01.08

Benefits:
- Fast writes (hot index)
- Easy to delete old data (drop index)
- Parallel queries across indices

Index Template:
{
  "index_patterns": ["logs-*"],
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "refresh_interval": "5s"
  },
  "mappings": {
    "properties": {
      "timestamp": {"type": "date"},
      "level": {"type": "keyword"},
      "message": {"type": "text"},
      "service": {"type": "keyword"},
      "host": {"type": "keyword"},
      "trace_id": {"type": "keyword"},
      "metadata": {"type": "object"}
    }
  }
}
```

**Query Example**:
```json
POST /logs-*-2024.01.08/_search
{
  "query": {
    "bool": {
      "must": [
        {"term": {"service": "user-service"}},
        {"term": {"level": "ERROR"}},
        {"range": {"timestamp": {
          "gte": "2024-01-08T10:00:00Z",
          "lte": "2024-01-08T11:00:00Z"
        }}}
      ]
    }
  },
  "sort": [{"timestamp": "desc"}],
  "size": 100
}
```

**Data Lifecycle**:
```
Day 1-7: Hot nodes (SSD, 3 shards)
Day 8-30: Warm nodes (HDD, 1 shard, force merge)
Day 31+: Snapshot to S3, delete from Elasticsearch

ILM Policy:
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_size": "50GB",
            "max_age": "1d"
          }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "shrink": {"number_of_shards": 1},
          "forcemerge": {"max_num_segments": 1}
        }
      },
      "delete": {
        "min_age": "30d",
        "actions": {"delete": {}}
      }
    }
  }
}
```

**Time-Series Database (Metrics Storage)**:

**Choice**: Prometheus + VictoriaMetrics (long-term storage)

**Why**:
- Prometheus: Industry standard, PromQL, pull model
- VictoriaMetrics: Better compression, faster queries, lower cost

**Architecture**:
```
Prometheus (Recent data - last 2 hours):
- Fast writes
- Low latency queries
- Limited retention

↓ (Remote Write)

VictoriaMetrics (Long-term - 30 days):
- Better compression (10:1)
- Unlimited cardinality
- Fast range queries
- Horizontal scaling

↓ (Archive)

S3 (Cold storage - 1 year+):
- Compressed chunks
- Cheap storage
- Rare access
```

**Data Model**:
```
Metric:
http_requests_total{
  method="GET",
  endpoint="/api/users",
  status="200",
  instance="server-123",
  job="user-service"
} 12345 @1704708000

Components:
- Metric name: http_requests_total
- Labels: method, endpoint, status, instance, job
- Value: 12345
- Timestamp: 1704708000 (Unix epoch)
```

**Storage Format**:
```
Chunk-based storage:
- 2 hours of data per chunk
- Compressed using Gorilla/DoubleDelta algorithm
- Indexed by metric name + labels hash

On-disk structure:
/data/
  /chunks/
    chunk_001.db (2024-01-08 00:00 - 02:00)
    chunk_002.db (2024-01-08 02:00 - 04:00)
  /index/
    labels.idx (inverted index: label -> chunk IDs)
    time.idx (time range -> chunk IDs)
```

**Query Optimization**:
```
1. Query: http_requests_total{service="user-service"}[5m]
2. Index lookup:
   - Find chunks with service="user-service" label
   - Filter by time range (last 5 minutes)
   - Result: chunk_145, chunk_146
3. Load chunks from disk (or cache)
4. Decompress
5. Apply filters and aggregations
6. Return results

Query time: ~100ms for recent data
```

---

### 6. Query Engine

**Purpose**: Execute queries on logs and metrics

**Log Query API**:

```http
POST /api/v1/logs/query
{
  "query": "level:ERROR AND service:user-service",
  "timeRange": {
    "start": "2024-01-08T10:00:00Z",
    "end": "2024-01-08T11:00:00Z"
  },
  "limit": 100,
  "sort": "timestamp:desc"
}

Response:
{
  "total": 1523,
  "logs": [
    {
      "timestamp": "2024-01-08T10:45:23Z",
      "level": "ERROR",
      "message": "Database connection failed",
      "service": "user-service",
      "host": "server-123",
      "trace_id": "abc123"
    }
  ],
  "took_ms": 145
}
```

**Metrics Query API (
