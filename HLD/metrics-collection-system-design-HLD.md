# Performance Metrics Collection System - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [API Design](#api-design)
9. [Metrics Collection Flow](#metrics-collection-flow)
10. [Deep Dives](#deep-dives)
11. [Scalability & Reliability](#scalability--reliability)
12. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a distributed system to collect, store, and analyze performance metrics from thousands of servers in real-time. The system should:
- Collect system metrics (CPU, memory, disk, network) from all servers
- Monitor application-specific metrics (response time, throughput, errors)
- Provide real-time dashboards and alerting
- Support custom metrics and dimensions
- Enable historical analysis and trending
- Scale to monitor 10,000+ servers
- Handle millions of metrics per second
- Provide sub-second query response times

### Scale Requirements
- **10,000 servers** being monitored
- **100 metrics per server** (CPU, memory, disk, network, application metrics)
- **10-second collection interval** (configurable)
- **1 million data points per second** at peak
- **90-day retention** for raw metrics
- **1-year retention** for aggregated metrics
- **Query latency**: < 1 second for dashboards
- **Alerting latency**: < 30 seconds from metric collection

---

## Functional Requirements

### Must Have (P0)

1. **Metrics Collection**
   - Agent-based collection (installed on each server)
   - Push model (agents push metrics to collectors)
   - Pull model (collectors scrape metrics from endpoints)
   - Support for standard metrics (CPU, memory, disk, network)
   - Custom application metrics
   - Metric tags and labels
   - Collection interval configuration (1s, 10s, 60s)

2. **Metrics Storage**
   - Time-series data storage
   - High write throughput (1M+ writes/sec)
   - Efficient compression
   - Automatic data rollup (1min → 1hour → 1day)
   - Retention policies (raw: 90 days, aggregated: 1 year)
   - Multi-tenant support

3. **Querying & Visualization**
   - Real-time dashboards
   - Query language (SQL-like or PromQL-like)
   - Aggregation functions (avg, sum, max, min, percentiles)
   - Group by dimensions (host, service, environment)
   - Time-range queries
   - Graph visualization

4. **Alerting**
   - Threshold-based alerts (CPU > 90%)
   - Anomaly detection
   - Alert rules configuration
   - Multiple notification channels (email, Slack, PagerDuty)
   - Alert grouping and deduplication
   - Alert history and acknowledgment

5. **Administration**
   - Agent deployment and management
   - Metric discovery
   - Dashboard templates
   - User access control
   - Audit logging

### Nice to Have (P1)
- Distributed tracing integration
- Log correlation
- Predictive analytics
- Cost optimization recommendations
- Service dependency mapping
- Capacity planning tools
- SLI/SLO tracking
- Integration with CI/CD pipelines

---

## Non-Functional Requirements

### Performance
- **Metrics ingestion**: < 100ms latency (p95)
- **Query response**: < 1 second for dashboards
- **Alert evaluation**: < 30 seconds
- **Dashboard load time**: < 2 seconds
- **Agent overhead**: < 2% CPU, < 100MB memory

### Scalability
- Support 10,000+ servers
- Handle 1M+ metrics per second
- Store 100TB+ of time-series data
- Scale linearly by adding nodes
- Support 1000+ concurrent dashboard users

### Availability
- **99.9% uptime** for data collection
- **99.95% uptime** for querying
- No single point of failure
- Multi-region deployment
- Graceful degradation

### Consistency
- **Eventual consistency** for metrics (acceptable)
- **At-least-once delivery** for alerts
- **Idempotent** metric ingestion

### Reliability
- No data loss during failures
- Automatic failover
- Self-healing agents
- Data replication (3x)

### Security
- TLS encryption for all communication
- Agent authentication (API keys, mutual TLS)
- Role-based access control (RBAC)
- Data encryption at rest
- Audit logging

---

## Capacity Estimation

### Traffic Estimates

```
Total servers: 10,000
Metrics per server: 100
Collection interval: 10 seconds

Metrics per second:
(10,000 servers × 100 metrics) / 10 seconds = 100,000 metrics/sec

Peak (3x average): 300,000 metrics/sec

Daily metrics:
100,000 metrics/sec × 86,400 seconds = 8.64 billion metrics/day

Query load:
- Active users: 1,000 concurrent
- Dashboards per user: 5
- Widgets per dashboard: 10
- Refresh interval: 30 seconds
- Queries per second: (1,000 × 5 × 10) / 30 = 1,667 QPS
```

### Storage Estimates

```
Metric data point structure:
- Timestamp: 8 bytes
- Metric ID: 8 bytes (hash of metric name + tags)
- Value: 8 bytes (float64)
- Tags: 100 bytes (average, compressed)
Total: ~124 bytes per data point

Daily storage (raw):
8.64B metrics × 124 bytes = 1.07 TB/day

Monthly storage (raw):
1.07 TB × 30 days = 32.1 TB/month

With 90-day retention:
32.1 TB × 3 months = 96.3 TB

Aggregated data (1-minute rollups):
Raw data points: 8.64B per day
1-minute rollups: 8.64B / 6 = 1.44B per day
Daily storage: 1.44B × 124 bytes = 178 GB/day
Monthly: 178 GB × 30 = 5.34 TB/month
1-year retention: 5.34 TB × 12 = 64 TB

Total storage:
- Raw metrics (90 days): 96 TB
- 1-minute rollups (1 year): 64 TB
- 1-hour rollups (1 year): 10 TB
- 1-day rollups (1 year): 2 TB
- Indexes and metadata: 20 TB
Total: ~192 TB

With replication (3x): 576 TB
```

### Processing Requirements

```
Ingestion:
- Metrics per second: 300K (peak)
- Processing per metric: 1ms (validation, tagging, routing)
- CPU cores needed: 300K × 0.001 = 300 cores

Aggregation:
- 1-minute rollups: 8.64B / 60 = 144M per minute
- Processing time: 10 seconds
- Aggregation nodes: 20

Query Processing:
- Queries per second: 1,667
- Average query time: 500ms
- Concurrent query capacity: 1,667 × 0.5 = 834 concurrent queries
- Query nodes: 50 (with buffer)
```

### Network Bandwidth

```
Ingestion:
- Metrics per second: 300K
- Metric size: 124 bytes
- Bandwidth: 300K × 124 bytes = 37.2 MB/s = 297 Mbps

Query Traffic:
- Queries per second: 1,667
- Response size: 50 KB (average)
- Bandwidth: 1,667 × 50 KB = 83.3 MB/s = 666 Mbps

Total: ~1 Gbps
```

### Cost Estimation

```
Monthly Costs (approximate):

Compute:
- Ingestion servers: 50 instances × $200 = $10K
- Aggregation servers: 20 instances × $150 = $3K
- Query servers: 50 instances × $200 = $10K

Storage:
- SSD (hot data - 30 days): 40 TB × $200/TB = $8K
- HDD (warm data - 90 days): 150 TB × $30/TB = $4.5K
- S3 (cold data - 1 year): 400 TB × $20/TB = $8K

Network:
- Data transfer: 3 TB/day × 30 days × $0.05/GB = $4.5K

Total: ~$48K per month
Cost per server: $48K / 10,000 = $4.80/server/month
```

---

## High-Level Architecture

```
                    [10,000 Monitored Servers]
                              |
            ┌─────────────────┼─────────────────┐
            ↓                 ↓                 ↓
     [Agent on Server] [Agent on Server] [Agent on Server]
     (Collect metrics) (Collect metrics) (Collect metrics)
            |                 |                 |
            └─────────────────┼─────────────────┘
                              ↓
                    [Load Balancer / API Gateway]
                    (TLS termination, auth)
                              ↓
                    ┌─────────┴─────────┐
                    ↓                   ↓
            [Ingestion Cluster]  [Pull Scrapers]
            (Receive metrics)    (Pull from endpoints)
                    |                   |
                    └─────────┬─────────┘
                              ↓
                    [Message Queue - Kafka]
                    (Buffer metrics)
                              |
            ┌─────────────────┼─────────────────┐
            ↓                 ↓                 ↓
    [Real-time         [Aggregation      [Alert
     Processing]        Pipeline]         Engine]
    (Stream metrics)   (Rollups)         (Evaluate rules)
            |                 |                 |
            └─────────────────┼─────────────────┘
                              ↓
                    [Time-Series Database]
                    (InfluxDB / Prometheus / Cortex)
                              |
        ┌─────────────────────┼─────────────────┐
        ↓                     ↓                  ↓
  [Hot Storage]        [Warm Storage]     [Cold Storage]
  (Last 7 days)        (30 days)          (1 year)
  (Redis/In-memory)    (SSD)              (S3/HDD)
        |                     |                  |
        └─────────────────────┼──────────────────┘
                              ↓
                    [Query Engine]
                    (Query coordinator, cache)
                              |
            ┌─────────────────┼─────────────────┐
            ↓                 ↓                 ↓
    [Query API]        [Dashboard]        [Alerting]
    (HTTP/gRPC)        (Grafana)          (Alert Manager)
            |                 |                 |
            └─────────────────┼─────────────────┘
                              ↓
                        [Users]
                    (Engineers, SREs, Admins)
```

### Key Architectural Decisions

1. **Time-Series Database**
   - Optimized for time-series data
   - Efficient compression (10:1 ratio)
   - Fast range queries
   - Built-in downsampling

2. **Kafka for Buffering**
   - Handles traffic spikes
   - Decouples ingestion from storage
   - Replay capability
   - Backpressure handling

3. **Multi-Tier Storage**
   - Hot tier (Redis): Last 7 days, sub-ms queries
   - Warm tier (SSD): 30 days, fast queries
   - Cold tier (S3/HDD): 1 year, slower queries
   - Optimizes cost vs performance

4. **Agent-Based Collection**
   - Installed on each server
   - Minimal overhead (< 2% CPU)
   - Local buffering (tolerates network issues)
   - Auto-discovery of services

5. **Push + Pull Hybrid**
   - Push: Agents push to collectors (proactive)
   - Pull: Scrapers pull from endpoints (Prometheus-style)
   - Flexibility for different use cases

---

## Core Components

### 1. Metrics Collection Agent

**Purpose**: Collect metrics from the host server and push to collection endpoints

**Architecture**:

```
[Agent Process]
    ├─ System Metrics Collector
    │  ├─ CPU usage
    │  ├─ Memory usage
    │  ├─ Disk I/O
    │  └─ Network traffic
    ├─ Application Metrics Collector
    │  ├─ HTTP endpoint scraping
    │  ├─ StatsD listener
    │  └─ Custom plugins
    ├─ Log Tailer (optional)
    └─ Metrics Buffer & Sender
       ├─ Local buffer (disk-based)
       ├─ Batch sending
       └─ Retry logic
```

**Key Features**:
- **Low overhead**: < 2% CPU, < 100MB memory
- **Auto-discovery**: Detects services running on host
- **Local buffering**: Queue metrics during network issues (1-hour buffer)
- **Compression**: Gzip compression before sending
- **Sampling**: Configurable sampling rates
- **Security**: TLS + API key authentication

**Metrics Collection Methods**:

1. **System Metrics** (via OS APIs):
   - CPU: /proc/stat (Linux), performance counters (Windows)
   - Memory: /proc/meminfo
   - Disk: /proc/diskstats
   - Network: /proc/net/dev

2. **Application Metrics**:
   - HTTP scraping: Pull from /metrics endpoint
   - StatsD: Listen on UDP port 8125
   - Custom: Plugin system for specific applications

**Metric Format**:
```
Metric structure:
{
  "name": "cpu.usage.percent",
  "value": 75.3,
  "timestamp": 1705363200,
  "tags": {
    "host": "web-server-01",
    "environment": "production",
    "datacenter": "us-west-2",
    "service": "api-gateway"
  }
}
```

**Configuration**:
```yaml
# agent.yaml
collection:
  interval: 10s
  batch_size: 100
  
endpoints:
  primary: https://metrics.company.com
  backup: https://metrics-backup.company.com

metrics:
  system:
    enabled: true
    cpu: true
    memory: true
    disk: true
    network: true
  
  applications:
    - type: http
      url: http://localhost:8080/metrics
      interval: 30s
    - type: statsd
      port: 8125

buffering:
  max_size: 10000
  disk_path: /var/metrics/buffer
  
auth:
  api_key: ${METRICS_API_KEY}
```

### 2. Ingestion Layer

**Purpose**: Receive metrics from agents, validate, and route to storage

**Components**:

**A. Load Balancer**:
- Distributes agent connections across ingestion servers
- Health checks on ingestion servers
- TLS termination
- DDoS protection

**B. Ingestion Servers**:
- Stateless HTTP/gRPC servers
- Validate metrics format
- Authentication (API key, mutual TLS)
- Rate limiting per agent
- Enrich with metadata
- Publish to Kafka

**Processing Flow**:
```
1. Agent → TLS connection → Load Balancer
2. Load Balancer → Ingestion Server
3. Ingestion Server:
   a. Authenticate (verify API key)
   b. Validate metric format
   c. Check rate limits
   d. Add timestamps (if missing)
   e. Normalize metric names
   f. Enrich with metadata
4. Publish to Kafka topic: raw-metrics
5. Return 200 OK to agent
```

**Validation**:
- Metric name format (alphanumeric + dots/underscores)
- Value type (numeric)
- Timestamp within acceptable range (not too old/future)
- Tag key/value format
- Payload size limits (< 1MB)

**Rate Limiting**:
- Per-host limit: 10,000 metrics/minute
- Per-tenant limit: 1M metrics/minute
- Backpressure if Kafka is lagging

### 3. Message Queue (Kafka)

**Purpose**: Buffer metrics between ingestion and storage

**Topics**:
```
raw-metrics (partitioned by host_id):
- High throughput ingestion
- Temporary buffer (24-hour retention)
- Multiple consumers

aggregated-metrics:
- Rollup results (1-min, 1-hour, 1-day)
- Longer retention (7 days)
- Downstream consumers

alerts:
- Alert events
- Notification system consumes
- 24-hour retention
```

**Benefits**:
- **Decoupling**: Ingestion independent of storage
- **Buffering**: Handles storage lag
- **Replay**: Can replay metrics for backfill
- **Fan-out**: Multiple consumers (storage, alerting, analytics)

**Configuration**:
- Partitions: 100 (based on host_id hash)
- Replication factor: 3
- Retention: 24 hours
- Compression: LZ4

### 4. Time-Series Database

**Purpose**: Store and query time-series metrics efficiently

**Why Time-Series DB**:
- Optimized for time-stamped data
- Efficient compression (10:1 ratio typical)
- Fast range queries (time-based)
- Built-in downsampling/aggregation
- High write throughput

**Popular Options**:
- **InfluxDB**: SQL-like query language, good compression
- **Prometheus**: Pull-based, excellent for Kubernetes
- **Cortex**: Horizontally scalable Prometheus
- **M3DB**: Distributed, high availability
- **TimescaleDB**: PostgreSQL extension, SQL support

**Data Model**:
```
Measurement: cpu.usage.percent
Tags: host=web-01, environment=prod, datacenter=us-west
Fields: value=75.3
Timestamp: 2025-01-16T03:00:00Z

Storage format (conceptual):
cpu.usage.percent,host=web-01,env=prod,dc=us-west value=75.3 1705363200

Index:
- Metric name index
- Tag index (inverted)
- Time index (sorted)
```

**Write Path**:
```
1. Kafka consumer reads metrics
2. Batch metrics (1000 per batch)
3. Write to WAL (write-ahead log)
4. Write to in-memory cache
5. Periodically flush to disk (TSM files)
6. Compact and compress old data
```

**Query Path**:
```
1. Parse query (PromQL or SQL-like)
2. Identify time range
3. Lookup metric names and tags in index
4. Scan relevant TSM files
5. Decompress data
6. Apply aggregation functions
7. Return results
```

**Retention & Rollups**:
```
Raw data:
- Resolution: 10 seconds
- Retention: 7 days (hot), 30 days (warm)

1-minute rollups:
- Calculate: avg, min, max, count
- Retention: 90 days

1-hour rollups:
- Calculate: avg, min, max, count
- Retention: 1 year

1-day rollups:
- Calculate: avg, min, max, count
- Retention: 5 years
```

### 5. Aggregation Pipeline

**Purpose**: Pre-compute rollups and aggregations for faster queries

**Architecture**:
```
[Kafka: raw-metrics]
         ↓
[Stream Processor (Flink)]
    ├─ 1-minute aggregation
    ├─ 1-hour aggregation
    └─ 1-day aggregation
         ↓
[Kafka: aggregated-metrics]
         ↓
[Time-Series DB]
(Store rollups)
```

**Aggregation Jobs**:

**1-Minute Rollups**:
- Tumbling window: 1 minute
- Calculate: avg, min, max, p50, p95, p99, count
- Group by: metric name + tags
- Output to aggregated-metrics topic

**1-Hour Rollups**:
- Read from 1-minute rollups
- Tumbling window: 1 hour
- Calculate: avg, min, max, p50, p95, p99, count
- Store directly in time-series DB

**1-Day Rollups**:
- Read from 1-hour rollups
- Tumbling window: 1 day
- Calculate: avg, min, max, count
- Long-term storage

**Benefits**:
- Faster dashboard queries (use rollups)
- Reduced storage (compressed aggregates)
- Historical analysis (long retention)

### 6. Query Engine

**Purpose**: Execute queries against time-series data and return results

**Query Language** (PromQL-style):
```
# CPU usage over last 5 minutes
cpu.usage.percent{host="web-01"}[5m]

# Average CPU across all web servers
avg(cpu.usage.percent{service="web"}[1h])

# 95th percentile response time
histogram_quantile(0.95, http_request_duration_seconds_bucket)

# Rate of HTTP requests per second
rate(http_requests_total[5m])

# Memory usage growth rate
deriv(memory.usage.bytes[30m])
```

**Query Optimization**:

1. **Time-based partitioning**: Only scan relevant time ranges
2. **Tag filtering**: Use indexes to filter by tags
3. **Downsampling**: Use rollups for long time ranges
4. **Caching**: Cache query results (1-5 minute TTL)
5. **Parallel execution**: Query multiple nodes in parallel

**Query Execution Plan**:
```
1. Parse query string
2. Identify time range
3. Determine storage tier (hot/warm/cold)
4. Check if rollups can be used
5. Build execution plan:
   a. Index lookup (metric + tags)
   b. Time range scan
   c. Apply functions (avg, sum, etc.)
   d. Group by dimensions
6. Execute in parallel on relevant nodes
7. Aggregate results
8. Return to client
```

**Caching Strategy**:
```
Cache key: hash(query + time_range)
TTL:
- Realtime queries (last 5 min): 30 seconds
- Recent queries (last 1 hour): 2 minutes
- Historical queries (> 1 day): 10 minutes

Cache storage: Redis
Cache size: 100GB
Hit ratio target: > 70%
```

### 7. Alert Engine

**Purpose**: Evaluate alert rules and trigger notifications

**Architecture**:
```
[Alert Rules DB]
         ↓
[Alert Evaluator]
(Scheduled checks every 1 minute)
         ↓
[Query Engine]
(Fetch metrics)
         ↓
[Alert State Manager]
(Track alert states: OK → PENDING → FIRING)
         ↓
[Notification Router]
    ├─ Email
    ├─ Slack
    ├─ PagerDuty
    └─ Webhook
```

**Alert Rule Definition**:
```yaml
alert: HighCPUUsage
expr: avg(cpu.usage.percent{service="api"}) > 90
for: 5m
labels:
  severity: critical
  team: platform
annotations:
  summary: "High CPU usage on {{ $labels.host }}"
  description: "CPU usage is {{ $value }}% on {{ $labels.host }}"
actions:
  - type: slack
    channel: "#alerts"
  - type: pagerduty
    service_key: ${PD_SERVICE_KEY}
```

**Alert States**:
```
OK: Condition not met
    ↓ (condition met)
PENDING: Condition met, waiting for duration
    ↓ (duration elapsed)
FIRING: Alert triggered, notifications sent
    ↓ (condition resolved)
OK: Alert resolved
```

**Alert Evaluation Flow**:
```
Every 1 minute:
1. Fetch all active alert rules
2. For each rule:
   a. Execute query
   b. Check if threshold exceeded
   c. Update alert state
   d. If PENDING → FIRING:
      - Send notifications
      - Record in alert history
   e. If FIRING → OK:
      - Send resolution notification
3. Apply grouping and deduplication
4. Rate limit notifications (max 1 per 5 min per alert)
```

**Alert Grouping**:
```
Problem: 100 servers have high CPU
Solution: Group into single alert

Group by: {service, severity}
Example:
- Alert: HighCPUUsage
- Service: api-gateway
- Affected hosts: [web-01, web-02, ..., web-100]
- Single notification with list of hosts
```

**Notification Channels**:

**Email**:
- SMTP integration
- HTML templates
- Batch similar alerts (5-minute window)

**Slack**:
- Webhook integration
- Rich formatting with buttons
- Thread replies for updates

**PagerDuty**:
- Incident creation
- Escalation policies
- On-call routing

**Webhook**:
- Custom HTTP POST
- Flexible integration with any system

### 8. Dashboard & Visualization

**Purpose**: Provide visual interface for metrics exploration

**Components**:

**Dashboard Builder**:
- Drag-and-drop widgets
- Template variables
- Auto-refresh (10s, 30s, 1m)
- Time range picker
- Panel types: line graph, bar chart, heatmap, table

**Popular Integrations**:
- **Grafana**: Most popular, rich ecosystem
- **Custom UI**: React-based dashboards
- **Kibana**: If using Elasticsearch

**Dashboard Examples**:

**System Overview Dashboard**:
```
Panels:
1. Active Hosts (single stat)
2. Total Metrics/sec (graph)
3. CPU Usage by Host (graph)
4. Memory Usage by Host (graph)
5. Disk I/O (graph)
6. Network Traffic (graph)
7. Top Processes by CPU (table)
```

**Application Dashboard**:
```
Panels:
1. Request Rate (graph)
2. Error Rate (graph)
3. P95 Latency (graph)
4. Active Connections (graph)
5. Top Endpoints by Traffic (table)
6. Error Breakdown by Type (pie chart)
```

**Dashboard Variables**:
```
Variables allow dynamic filtering:
- $environment: prod, staging, dev
- $datacenter: us-west, us-east, eu-west
- $service: api, web, worker
- $host: dropdown of all hosts

Query with variables:
cpu.usage.percent{
  environment="$environment",
  datacenter="$datacenter",
  service="$service"
}
```

---

## Database Design

### 1. Time-Series Database Schema

**Metrics Table** (Logical Structure):
```
Table: metrics

Columns:
- timestamp: INT64 (Unix epoch milliseconds)
- metric_name: STRING
- value: FLOAT64
- tags: MAP<STRING, STRING>

Indexes:
- Primary: (metric_name, tags_hash, timestamp)
- Secondary: (timestamp, metric_name)
- Tag index: Inverted index on tag key-value pairs

Physical Storage:
- Compressed columnar format (TSM files)
- Time-based sharding (daily/weekly)
- Replication factor: 3
```

**Aggregates Table** (Rollups):
```
Table: metrics_1min, metrics_1hour, metrics_1day

Columns:
- timestamp: INT64 (start of period)
- metric_name: STRING
- tags: MAP<STRING, STRING>
- avg: FLOAT64
- min: FLOAT64
- max: FLOAT64
- count: INT64
- p50: FLOAT64
- p95: FLOAT64
- p99: FLOAT64

Partitioning: By time (monthly partitions)
TTL: 
  - 1min: 90 days
  - 1hour: 1 year
  - 1day: 5 years
```

### 2. Metadata Database (PostgreSQL)

**Hosts Table**:
```
Table: hosts

Columns:
- host_id: UUID PRIMARY KEY
- hostname: VARCHAR(255) UNIQUE
- ip_address: VARCHAR(45)
- environment: VARCHAR(50)
- datacenter: VARCHAR(50)
- agent_version: VARCHAR(20)
- last_seen: TIMESTAMP
- status: ENUM('active', 'inactive', 'decommissioned')
- tags: JSONB
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

Indexes:
- hostname (unique)
- status, last_seen (composite)
- tags (GIN index for JSONB)
```

**Alert Rules Table**:
```
Table: alert_rules

Columns:
- rule_id: UUID PRIMARY KEY
- name: VARCHAR(255)
- expression: TEXT (query expression)
- threshold: FLOAT
- duration: INTERVAL (e.g., '5 minutes')
- severity: VARCHAR(20)
- labels: JSONB
- annotations: JSONB
- notification_channels: JSONB
- enabled: BOOLEAN
- created_by: VARCHAR(100)
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

Indexes:
- name
- enabled, created_at
```

**Alert History Table**:
```
Table: alert_history

Columns:
- alert_id: UUID PRIMARY KEY
- rule_id: UUID (FK to alert_rules)
- state: ENUM('pending', 'firing', 'resolved')
- value: FLOAT (metric value that triggered)
- labels: JSONB
- fired_at: TIMESTAMP
- resolved_at: TIMESTAMP
- acknowledged_by: VARCHAR(100)
- acknowledged_at: TIMESTAMP
- notes: TEXT

Indexes:
- rule_id, fired_at
- state, fired_at
- Partitioned by month
```

**Dashboards Table**:
```
Table: dashboards

Columns:
- dashboard_id: UUID PRIMARY KEY
- name: VARCHAR(255)
- description: TEXT
- definition: JSONB (panel configuration)
- owner: VARCHAR(100)
- is_public: BOOLEAN
- tags: ARRAY<VARCHAR>
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

Indexes:
- owner, created_at
- tags (GIN index)
```

### 3. Cache Layer (Redis)

**Query Results Cache**:
```
Key: query:{hash(query+params)}
Value: JSON result
TTL: 30-600 seconds (based on query time range)

Example:
Key: query:abc123def456
Value: {
  "results": [
    {"timestamp": 1705363200, "value": 75.3},
    {"timestamp": 1705363210, "value": 76.1},
    ...
  ]
}
TTL: 60 seconds
```

**Metric Metadata Cache**:
```
Key: metric_meta:{metric_name}
Value: {
  "name": "cpu.usage.percent",
  "type": "gauge",
  "unit": "percent",
  "description": "CPU usage percentage"
}
TTL: 3600 seconds (1 hour)
```

**Host Status Cache**:
```
Key: host_status:{host_id}
Value: {
  "hostname": "web-01",
  "status": "active",
  "last_seen": 1705363200,
  "cpu": 75.3,
  "memory": 82.5
}
TTL: 60 seconds
```

**Active Alerts Cache**:
```
Key: alerts:active
Value: Set of alert_ids currently firing
TTL: No expiration (managed by alert engine)
```

---

## API Design

### RESTful Endpoints

#### Metrics Ingestion APIs

```
POST /api/v1/metrics
- Receive metrics from agents
- Batch of up to 1000 metrics
- Returns 202 Accepted
- Rate limit: 10K metrics/min per host

Request:
{
  "host_id": "host_abc123",
  "metrics": [
    {
      "name": "cpu.usage.percent",
      "value": 75.3,
      "timestamp": 1705363200,
      "tags": {
        "core": "0"
      }
    }
  ]
}

Response:
{
  "status": "accepted",
  "received": 100,
  "rejected": 0
}
```

#### Query APIs

```
GET /api/v1/query?query={promql}&start={ts}&end={ts}&step={step}
- Execute PromQL query
- Returns time-series data
- Response time: < 1 second

POST /api/v1/query_range
- Query with time range
- Support for complex queries
- Pagination support

GET /api/v1/series?match[]={selector}&start={ts}&end={ts}
- List all series matching selector
- Returns metric names and tags
```

#### Alert APIs

```
GET /api/v1/alerts
- List all alerts
- Filter by: state, severity, labels

GET /api/v1/alerts/{alert_id}
- Get alert details
- Include history and timeline

POST /api/v1/alerts/{alert_id}/acknowledge
- Acknowledge alert
- Silence for duration

POST /api/v1/rules
- Create alert rule
- Validate expression

PUT /api/v1/rules/{rule_id}
- Update alert rule
- Version control
```

#### Configuration APIs

```
GET /api/v1/hosts
- List all monitored hosts
- Filter by status, tags

POST /api/v1/hosts/{host_id}/tags
- Add tags to host
- Update metadata

GET /api/v1/dashboards
- List all dashboards
- Search and filter

POST /api/v1/dashboards
- Create new dashboard
- Store definition
```

---

## Metrics Collection Flow

### End-to-End Flow: CPU Metric Collection

```
T=0s: Agent collection cycle starts
    ↓
[Agent reads /proc/stat]
- Calculate CPU usage: 75.3%
    ↓
[Add metadata]
{
  "name": "cpu.usage.percent",
  "value": 75.3,
  "timestamp": 1705363200,
  "tags": {
    "host": "web-01",
    "environment": "prod",
    "datacenter": "us-west-2"
  }
}
    ↓
[Batch with other metrics]
- Collect 99 other metrics
- Create batch of 100 metrics
    ↓
T=50ms: Send HTTP POST to /api/v1/metrics
    ↓
[Load Balancer]
- Route to ingestion server
    ↓
T=70ms: [Ingestion Server]
- Authenticate API key
- Validate format
- Check rate limit
    ↓
[Publish to Kafka: raw-metrics]
- Partition by host_id
    ↓
T=100ms: Return 202 Accepted
─────────────────────────────────
ASYNC PROCESSING
    ↓
[Kafka Consumer: Storage Writer]
    ↓
T=500ms: Write to Time-Series DB
- Batch write (1000 metrics)
- Write to WAL
- Update in-memory cache
    ↓
[Stream Processor: Aggregator]
    ↓
T=60s: 1-minute rollup
- Calculate avg, min, max, p95
- Write aggregated values
    ↓
[Alert Evaluator]
    ↓
T=60s: Check alert rules
- Query: avg(cpu) over 5 min
- Compare to threshold (90%)
- Current: 75.3% → OK
    ↓
[Query Engine]
    ↓
T=30s: Dashboard refresh
- Fetch last 5 minutes of CPU data
- Return to Grafana
- User sees updated graph
```

### Real-Time Dashboard Query

```
User views dashboard
    ↓
Dashboard requests data (auto-refresh every 30s)
    ↓
GET /api/v1/query?query=avg(cpu.usage.percent{service="web"})&start=now-5m&end=now
    ↓
[API Gateway]
    ↓
[Query Engine]
    ↓
[Check Cache]
- Key: query:xyz789
- Cache HIT (70% hit rate)
- Return cached result
    ↓
Response time: 50ms
─────────────────────────────────
If CACHE MISS:
    ↓
[Query Router]
- Time range: Last 5 minutes
- Route to: Hot storage (Redis + ClickHouse)
    ↓
[Execute Query]
- Fetch CPU metrics for service="web"
- Calculate average
- 30 data points (10s intervals)
    ↓
[Cache Result]
- TTL: 30 seconds
    ↓
Return response: 800ms
```

---

## Deep Dives

### 1. Handling Agent Failures

**Scenario**: Network partition, agent can't reach collectors

**Agent Behavior**:
```
1. Detect connection failure (timeout)
2. Switch to local buffering:
   - Write metrics to disk
   - Max buffer: 10,000 metrics (~1 hour)
   - Oldest metrics dropped if buffer full
3. Retry connection:
   - Exponential backoff (1s, 2s, 4s, 8s, max 60s)
   - Try backup endpoint if primary fails
4. When connection restored:
   - Flush buffer to collectors
   - Resume normal operation
   - Mark buffered metrics (metadata flag)
```

**Monitoring Agent Health**:
```
Heartbeat mechanism:
- Agent sends heartbeat every 30 seconds
- Includes: host_id, agent_version, buffer_size
- Backend tracks last_seen timestamp

Alert if no heartbeat for 5 minutes:
- Status: active → inactive
- Dashboard shows host as down
- Alert team to investigate
```

**Self-Healing**:
```
Agent watchdog process:
- Monitor agent process health
- Restart if crashed
- Log restart events
- Alert on repeated crashes
```

### 2. Time-Series Data Compression

**Challenge**: Store billions of metrics efficiently

**Compression Techniques**:

**1. Delta-of-Delta Encoding**:
```
Original timestamps:
1000, 1010, 1020, 1030, 1040

Delta (difference):
10, 10, 10, 10

Delta-of-delta:
0, 0, 0

Store: base_timestamp + deltas
Compression: 90% (10x)
```

**2. XOR Compression** (for values):
```
Values that don't change much compress well:
75.3, 75.5, 75.2, 75.4

XOR sequential values:
Only store bits that changed
Compression: 80-90%
```

**3. Dictionary Encoding** (for tags):
```
Original:
host=web-01, env=prod, dc=us-west
host=web-02, env=prod, dc=us-west
host=web-03, env=prod, dc=us-west

Dictionary:
1: web-01, 2: web-02, 3: web-03
4: prod, 5: us-west

Stored:
1,4,5
2,4,5
3,4,5

Compression: 70%
```

**Overall Compression Ratio**: 10:1 typical

**Storage Format** (TSM files):
```
TSM (Time-Structured Merge tree) File Structure:
- Header: Metadata, index offset
- Index: Metric name + tag → data block offset
- Data Blocks: Compressed time-series data
- Footer: Checksum

Block size: 1MB (compressed)
Immutable: No updates, only appends
Compaction: Merge old blocks periodically
```

### 3. Cardinality Explosion Problem

**Problem**: Too many unique tag combinations

**Example**:
```
Metric: http_requests_total
Tags: method, path, status_code, user_id

If tracking user_id in tags:
- 1M users × 10 methods × 100 paths × 10 status = 1T combinations!
- Index size explodes
- Query performance degrades
```

**Solutions**:

**1. Limit High-Cardinality Tags**:
```
Good tags (low cardinality):
- environment: prod, staging, dev (3 values)
- datacenter: us-west, us-east, eu (3 values)
- service: api, web, worker (10 values)

Bad tags (high cardinality):
- user_id: millions of users ❌
- request_id: every request is unique ❌
- email: millions of emails ❌

Use logs for high-cardinality data, not metrics
```

**2. Tag Limits**:
```
Enforce limits:
- Max 20 tags per metric
- Max 50 unique values per tag
- Reject metrics exceeding limits
- Alert on high cardinality
```

**3. Cardinality Monitoring**:
```
Track cardinality per metric:
SELECT metric_name, COUNT(DISTINCT tags_hash)
FROM metrics
GROUP BY metric_name
ORDER BY 2 DESC

Alert if cardinality > 10,000
```

### 4. Query Performance Optimization

**Challenge**: Sub-second queries over billions of data points

**Optimization Strategies**:

**1. Use Rollups for Long Time Ranges**:
```
Query: Average CPU over last 30 days

Bad: Scan raw data (10s intervals)
- Data points: 30 days × 86,400 sec/day / 10 sec = 259,200 points
- Scan time: 5-10 seconds ❌

Good: Use 1-hour rollups
- Data points: 30 days × 24 hours = 720 points
- Scan time: 100ms ✓
```

**2. Partition Pruning**:
```
Query: CPU usage on 2025-01-15

Index structure: Partitioned by day
- Only scan partition for 2025-01-15
- Skip all other days
- 100x faster
```

**3. Tag Index Optimization**:
```
Query: cpu{service="web",environment="prod"}

Inverted index:
service="web" → [host-01, host-02, host-03]
environment="prod" → [host-01, host-02, host-04]

Intersection: [host-01, host-02]

Only scan metrics for these hosts
```

**4. Query Parallelization**:
```
Query: avg(cpu) across 1000 hosts

Split into sub-queries:
- Node 1: hosts 1-250
- Node 2: hosts 251-500
- Node 3: hosts 501-750
- Node 4: hosts 751-1000

Execute in parallel
Aggregate results
4x speedup
```

**5. Approximate Algorithms**:
```
For very large datasets:
- HyperLogLog for COUNT(DISTINCT)
- T-Digest for percentiles
- Sampling for histograms

Trade accuracy for speed
Acceptable for dashboards
```

### 5. Alerting Best Practices

**Alert Fatigue Prevention**:

**1. Alert Grouping**:
```
Instead of: 100 separate alerts for 100 high-CPU servers
Use: 1 grouped alert listing all affected servers
```

**2. Alert Throttling**:
```
Same alert fired multiple times:
- First: Send immediately
- Repeat: Send at most once per 5 minutes
- After 1 hour: Send summary
```

**3. Alert Dependencies**:
```
If database is down:
- Don't alert on all dependent services
- Only alert on root cause (database)
- Suppress cascading alerts
```

**4. Severity Levels**:
```
CRITICAL: Page immediately (CPU > 95%, disk full)
WARNING: Notify during business hours (CPU > 80%)
INFO: Log only (CPU > 70%)
```

**Smart Alerting**:
```
Dynamic thresholds:
- Baseline: Average CPU = 40%
- Alert if: Current > Baseline + 2 stddev
- Adapts to normal patterns
- Reduces false positives
```

### 6. Multi-Tenancy

**Challenge**: Isolate metrics from different tenants

**Tenant Identification**:
```
Each tenant gets unique:
- API key
- Namespace prefix
- Resource quotas

Metric naming:
tenant1.cpu.usage.percent
tenant2.cpu.usage.percent

Storage isolation:
- Separate Kafka topics
- Separate time-series DB instances (hard isolation)
- OR same DB with tenant_id tag (soft isolation)
```

**Resource Quotas**:
```
Per tenant limits:
- Max 10,000 hosts
- Max 1M metrics/minute
- Max 100GB storage
- Max 1000 queries/minute

Enforcement:
- Rate limiting at ingestion
- Storage quota monitoring
- Query throttling
```

**Billing**:
```
Metering:
- Metrics ingested (per million)
- Storage used (per GB)
- Queries executed (per 1000)
- Data retention (days)

Pricing example:
- $0.10 per million metrics
- $0.50 per GB storage per month
- $0.01 per 1000 queries
```

### 7. Service Discovery Integration

**Challenge**: Automatically discover and monitor new services

**Integration with Service Mesh**:
```
Consul / Etcd / Kubernetes Service Discovery:
    ↓
[Discovery Agent]
- Watch for new service registrations
- Detect service endpoints
    ↓
[Auto-configure Monitoring]
- Deploy metric agent
- Configure scraping endpoints
- Add to monitoring
    ↓
[Update Dashboard]
- Auto-create service dashboard
- Configure baseline alerts
```

**Kubernetes Integration**:
```
Pod annotations:
prometheus.io/scrape: "true"
prometheus.io/port: "8080"
prometheus.io/path: "/metrics"

Scraper:
1. Watch Kubernetes API for pods
2. Filter by annotation
3. Scrape /metrics endpoint
4. Tag with pod metadata (namespace, labels)
```

**Service Catalog**:
```
Maintain registry:
- Service name
- Endpoints to monitor
- Alert rules
- Dashboard templates

Auto-apply when service starts
```

---

## Scalability & Reliability

### Horizontal Scaling

**Agent Scaling**:
```
Linear: 1 agent per host
No coordination needed
Each agent independent

Scale to 100,000 servers:
- Simply deploy agent on new servers
- No architectural changes needed
```

**Ingestion Layer Scaling**:
```
Current: 50 ingestion servers, 300K metrics/sec

To double capacity:
1. Add 50 more ingestion servers
2. Load balancer automatically distributes
3. No config changes needed

New capacity: 100 servers, 600K metrics/sec
```

**Time-Series DB Scaling**:

**Vertical Scaling** (Limited):
```
Upgrade instance size:
- More CPU for faster queries
- More RAM for larger in-memory cache
- More disk for storage

Limit: Can't scale infinitely
```

**Horizontal Scaling** (Preferred):
```
Sharding strategy:
- Shard by metric name hash
- Each shard handles subset of metrics
- Distributed queries across shards

Current: 10 shards, 10TB each, 100K writes/sec
Add capacity: 20 shards, 200K writes/sec
```

**Query Engine Scaling**:
```
Add query nodes behind load balancer:
- Each node has full read access
- Cache shared via Redis
- Parallel query execution

Current: 20 nodes, 1000 QPS
Add capacity: 40 nodes, 2000 QPS
```

### High Availability

**Multi-Region Deployment**:
```
3 Regions: US, EU, APAC

Each region:
- Full metrics collection stack
- Independent time-series DB
- Regional dashboards

Cross-region:
- Replicate metadata (PostgreSQL)
- Aggregate metrics for global view
- Failover to backup region
```

**Component Redundancy**:

**Ingestion Layer**:
```
- Multiple instances behind load balancer
- Health checks every 10 seconds
- Remove unhealthy instances
- Auto-scaling replaces failed instances
```

**Kafka Cluster**:
```
- 5 brokers minimum
- Replication factor: 3
- In-sync replicas: 2
- Leader election on failure (< 10 seconds)
```

**Time-Series DB**:
```
- 3 replicas per shard
- Quorum writes (2/3)
- Read from any replica
- Automatic failover
```

**Agent Resilience**:
```
- Local buffering (1-hour capacity)
- Multiple collector endpoints
- Exponential backoff retry
- Self-healing (watchdog)
```

### Data Consistency

**Eventual Consistency Model**:
```
Write path:
1. Agent sends metric
2. Acknowledged by Kafka
3. Asynchronously written to storage
4. Eventually visible in queries

Delay: 1-5 seconds typical
Acceptable for monitoring use case
```

**Handling Duplicates**:
```
Metric has unique ID:
- Combination of: metric_name + tags + timestamp

Deduplication:
- Bloom filter at ingestion (1-hour window)
- Last-write-wins at storage layer
- Idempotent writes
```

**Clock Skew Handling**:
```
Problem: Server clocks not synchronized

Solution:
1. Agent sends metrics with timestamps
2. Ingestion adds server_timestamp
3. If |agent_timestamp - server_timestamp| > 1 minute:
   - Flag as clock skew
   - Use server_timestamp
   - Alert ops team
```

### Monitoring the Monitoring System

**Meta-Monitoring**:
```
Monitor the metrics system itself:
- Ingestion rate (metrics/sec)
- Ingestion lag (time delay)
- Kafka consumer lag
- Storage write latency
- Query latency
- Alert evaluation time
- Agent health
```

**Key Metrics**:
```
System Health:
- metrics.ingested_per_sec
- metrics.dropped_per_sec
- kafka.consumer_lag_seconds
- tsdb.write_latency_ms
- tsdb.query_latency_ms
- agent.connection_errors

Resource Usage:
- ingestion.cpu_percent
- ingestion.memory_bytes
- tsdb.disk_used_percent
- cache.hit_ratio
```

**Self-Healing**:
```
Auto-remediation:
- High ingestion lag → Scale up workers
- High query latency → Add cache nodes
- Disk space low → Trigger compaction
- Agent down → Alert ops team
```

---

## Trade-offs & Alternatives

### 1. Push vs Pull Model

**Push Model** (Agent-based):
```
Pros:
+ Agents control collection frequency
+ Works behind firewalls
+ Centralized receives data
+ Explicit configuration

Cons:
- More complex agents
- Higher network overhead
- Harder to debug

Use when: Many servers, need control
```

**Pull Model** (Prometheus-style):
```
Pros:
+ Simple targets (just expose /metrics)
+ Easy service discovery
+ Targets don't need to know collector
+ Easier debugging (curl /metrics)

Cons:
- Collectors must reach all targets
- Firewall challenges
- Pull interval limits

Use when: Kubernetes, microservices
```

**Hybrid** (Both):
```
Best of both worlds:
- Push for servers behind firewalls
- Pull for Kubernetes pods
- Flexibility

Chosen for this design
```

### 2. Time-Series DB Options

**InfluxDB** (Chosen):
```
Pros:
+ SQL-like query language (easy)
+ Good compression
+ Fast writes
+ Built-in HTTP API

Cons:
- Clustering costs money (Enterprise)
- Resource intensive
- Single-node limits

Use when: SQL comfort, moderate scale
```

**Prometheus**:
```
Pros:
+ Excellent for Kubernetes
+ Pull-based model
+ Large ecosystem
+ PromQL powerful

Cons:
- Single-node limited
- Long-term storage requires Thanos/Cortex
- Pull model not always suitable

Use when: Kubernetes-focused
```

**Cortex/Thanos**:
```
Pros:
+ Horizontally scalable Prometheus
+ Multi-tenancy built-in
+ Long-term storage (S3)
+ PromQL compatible

Cons:
- More complex architecture
- Higher operational overhead
- Eventual consistency

Use when: Need Prometheus at scale
```

**M3DB**:
```
Pros:
+ Built for scale (Uber)
+ Distributed architecture
+ High availability
+ PromQL support

Cons:
- Steeper learning curve
- Smaller community
- Complex operations

Use when: Need massive scale
```

### 3. Kafka vs Alternatives

**Kafka** (Chosen):
```
Pros:
+ High throughput
+ Durable storage
+ Replay capability
+ Mature ecosystem

Cons:
- Complex operations
- Higher resource usage
- Over-engineered for simple cases

Use when: High volume, need durability
```

**RabbitMQ**:
```
Pros:
+ Easier to operate
+ Good for simple queuing
+ Lower latency

Cons:
- Lower throughput
- Less durable
- No replay

Use when: Moderate volume, need simplicity
```

**Redis Streams**:
```
Pros:
+ Simple
+ Low latency
+ Built into Redis

Cons:
- Limited throughput
- Memory-bound
- Not distributed

Use when: Small scale, already using Redis
```

### 4. Aggregation Strategy

**Pre-aggregation** (Chosen):
```
Pros:
+ Fast dashboard queries
+ Reduced storage
+ Predictable performance

Cons:
- Storage overhead (rollups)
- Complexity (aggregation pipeline)
- Query flexibility reduced

Use when: Common queries known
```

**Query-Time Aggregation**:
```
Pros:
+ Full flexibility
+ No pre-computation needed
+ Simpler architecture

Cons:
- Slower queries
- Higher compute cost
- Unpredictable performance

Use when: Ad-hoc queries, small scale
```

### 5. Storage Retention

**Tiered Retention** (Chosen):
```
Raw (10s): 7-30 days
1-minute: 90 days
1-hour: 1 year
1-day: 5 years

Pros:
+ Balance cost and granularity
+ Fast recent queries
+ Long-term trends available

Cons:
- Complex retention management
- Multiple storage tiers
```

**Single Retention**:
```
Keep raw data for 1 year

Pros:
+ Simple
+ Full granularity

Cons:
- Expensive storage cost (10x)
- Slower queries
- Unnecessary for old data
```

---

## Conclusion

This Performance Metrics Collection System handles **1 million metrics per second** from **10,000 servers** with:

**Key Features**:
- Real-time collection and dashboards (< 5 second latency)
- Efficient storage with 10:1 compression
- Sub-second query response times
- Intelligent alerting with grouping
- 99.9% uptime guarantee
- Multi-tenant support

**Core Design Principles**:
1. **Agent-based collection**: Low overhead, reliable
2. **Kafka buffering**: Decouples ingestion from storage
3. **Time-series DB**: Optimized for time-series data
4. **Multi-tier storage**: Cost optimization
5. **Pre-aggregation**: Fast common queries
6. **Horizontal scaling**: Add capacity by adding nodes

**Technology Stack**:
- **Collection Agent**: Telegraf / Custom agent
- **Message Queue**: Apache Kafka
- **Stream Processing**: Apache Flink
- **Time-Series DB**: InfluxDB / Cortex / M3DB
- **Metadata DB**: PostgreSQL
- **Cache**: Redis
- **Dashboards**: Grafana
- **Alerting**: Prometheus Alert Manager

The system scales to **100,000+ servers** by adding more ingestion nodes, Kafka partitions, and time-series DB shards.

**Cost Efficiency**:
- Infrastructure: $48K/month
- Cost per server: $4.80/month
- Competitive with commercial solutions

---

**Document Version**: 1.0  
**Last Updated**: November 16, 2025  
**Status**: Complete & Interview-Ready ✅
