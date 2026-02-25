# Design a Global VM Monitoring System - Hello Interview Framework

> **Question**: Design a system that allows users to view and monitor their virtual machines across multiple global regions, displaying key information such as node name, region, and health status. Users open a single console, filter by region or tag, and get near real-time status, alerts, and drill-down details. Think AWS CloudWatch, Azure Monitor, or Datadog's infrastructure list.
>
> **Asked at**: Google
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

## 1️⃣ Requirements

### Functional (P0)
1. **VM Inventory View**: Unified dashboard listing all VMs across all regions. Show: name, region, status (healthy/degraded/down), IP, instance type, tags. Filterable and searchable.
2. **Health Status Computation**: Aggregate heartbeats + metrics (CPU, memory, disk, network) into a health status per VM. Healthy = heartbeat received within 60s AND metrics within thresholds.
3. **Heartbeat Ingestion**: Each VM agent sends heartbeat every 30 seconds. If missed for 2 consecutive intervals (60s) → mark as DEGRADED. If missed for 3 (90s) → mark as DOWN.
4. **Multi-Region**: VMs run in 10+ global regions. Data collected per-region, aggregated centrally. Dashboard shows cross-region view.
5. **Drill-Down Details**: Click a VM → see CPU/memory/disk charts, recent events, config, running processes. Per-VM detail page.
6. **Alerting**: Alert when VM goes DOWN or metrics exceed thresholds. Notify via email/Slack/PagerDuty.

### Functional (P1)
- Tag-based filtering and grouping (env=prod, team=infra)
- Custom dashboards with widgets (top-N CPU, disk usage by region)
- Auto-scaling recommendations based on utilization
- Maintenance windows (suppress alerts during planned downtime)
- Audit log: who started/stopped/modified a VM

### Non-Functional Requirements
| Attribute | Target |
|-----------|--------|
| **Dashboard Load** | < 500ms for full VM list (10K VMs) |
| **Health Freshness** | < 60 seconds from heartbeat to dashboard update |
| **Heartbeat Ingestion** | 10M heartbeats/min (5M VMs × 2/min) |
| **Availability** | 99.99% for dashboard reads, 99.9% for alerting |
| **Multi-Region** | 10+ regions, central dashboard |
| **Scale** | 5M VMs, 100K tenants |

### Capacity Estimation
```
VMs: 5M total across 10 regions (500K per region avg)
Heartbeats: 5M × 2/min = 10M heartbeats/min = ~167K/sec
  Each heartbeat: { vm_id, region, timestamp, cpu, mem, disk, net } = ~200 bytes
  Ingestion: 167K × 200 bytes = 33 MB/sec

VM inventory: 5M × 1 KB metadata = 5 GB
Health state: 5M × 100 bytes = 500 MB (all in memory)
Metrics history: 5M VMs × 6 samples/min × 1440 min/day × 30 bytes = ~1.3 TB/day

Dashboard queries: 100K tenants × 5 loads/day = 500K queries/day, peak ~20/sec
  Each query: list VMs + health → response ~50-200 KB (depends on VM count)
```

---

## 2️⃣ Core Entities

```java
public class VirtualMachine {
    String vmId;                // "vm-us-east-1-abc123"
    String tenantId;
    String name;                // "web-server-01"
    String region;              // "us-east-1", "eu-west-1"
    String availabilityZone;
    String instanceType;        // "m5.xlarge"
    String ipAddress;
    Map<String, String> tags;   // { "env": "prod", "team": "infra" }
    VmStatus status;            // RUNNING, STOPPED, TERMINATED
    HealthStatus health;        // HEALTHY, DEGRADED, DOWN, UNKNOWN
    Instant lastHeartbeatAt;
    Instant createdAt;
}
public enum VmStatus { RUNNING, STOPPED, TERMINATED }
public enum HealthStatus { HEALTHY, DEGRADED, DOWN, UNKNOWN }

public class Heartbeat {
    String vmId;
    String region;
    Instant timestamp;
    double cpuPercent;
    double memoryPercent;
    double diskPercent;
    double networkBytesIn;
    double networkBytesOut;
    String agentVersion;
}

public class HealthState {
    String vmId;
    HealthStatus status;
    Instant lastHeartbeatAt;
    int missedHeartbeats;       // 0 = healthy, 1-2 = degraded, 3+ = down
    double lastCpu, lastMem, lastDisk;
    Instant stateChangedAt;     // When status last changed
}

public class Alert {
    String alertId;
    String vmId;
    String tenantId;
    AlertType type;             // VM_DOWN, HIGH_CPU, HIGH_MEMORY, HIGH_DISK
    AlertSeverity severity;
    String message;
    Instant firedAt;
    Instant resolvedAt;
    boolean notified;
}

public class Tenant {
    String tenantId;
    String name;
    int vmCount;
    List<String> regions;       // Regions with VMs
    Map<String, String> alertChannels; // "email": "admin@...", "slack": "#ops"
}

public class MetricDataPoint {
    String vmId;
    String metricName;          // "cpu.usage", "memory.usage"
    double value;
    Instant timestamp;
}
```

---

## 3️⃣ API Design

### 1. List VMs (Dashboard — Hot Path)
```
GET /api/v1/vms?region=us-east-1&status=RUNNING&health=DOWN&tag=env:prod&limit=100&cursor=xxx
Response (200):
{
  "vms": [
    { "vm_id": "vm-001", "name": "web-server-01", "region": "us-east-1", "instance_type": "m5.xlarge",
      "health": "HEALTHY", "cpu": 45.2, "memory": 72.1, "last_heartbeat": "2025-01-10T10:00:30Z",
      "tags": { "env": "prod", "team": "infra" } },
    { "vm_id": "vm-002", "name": "db-primary", "region": "us-east-1",
      "health": "DOWN", "cpu": null, "memory": null, "last_heartbeat": "2025-01-10T09:58:00Z", ... }
  ],
  "summary": { "total": 1234, "healthy": 1200, "degraded": 20, "down": 14 },
  "next_cursor": "..."
}
```

### 2. Get VM Details (Drill-Down)
```
GET /api/v1/vms/{vm_id}
Response: { "vm_id": "vm-001", ..., "metrics": { "cpu_history": [...], "memory_history": [...] }, "recent_events": [...] }
```

### 3. Ingest Heartbeat (Agent → Backend)
```
POST /api/v1/heartbeats
Request: { "vm_id": "vm-001", "region": "us-east-1", "timestamp": "...", "cpu": 45.2, "memory": 72.1, "disk": 55.0 }
Response (202): { "accepted": true }
```

### 4. Get Region Summary
```
GET /api/v1/regions/summary
Response: {
  "regions": [
    { "region": "us-east-1", "total_vms": 150000, "healthy": 149500, "degraded": 300, "down": 200 },
    { "region": "eu-west-1", "total_vms": 120000, ... }
  ]
}
```

### 5. Get Active Alerts
```
GET /api/v1/alerts?severity=CRITICAL&state=FIRING
Response: { "alerts": [{ "alert_id": "alert_001", "vm_id": "vm-002", "type": "VM_DOWN", ... }] }
```

### 6. Update VM Tags
```
PATCH /api/v1/vms/{vm_id}/tags
Request: { "tags": { "env": "prod", "team": "platform" } }
```

---

## 4️⃣ Data Flow

### Heartbeat → Health State → Dashboard
```
1. VM Agent sends heartbeat every 30 seconds
   → POST /api/v1/heartbeats (to regional ingestion endpoint)
   ↓
2. Regional Ingestion Service:
   a. Validate heartbeat (vm_id exists, tenant matches)
   b. Write to regional Kafka "heartbeats" topic
   c. Return 202 immediately
   ↓
3. Regional Health Processor (Kafka consumer):
   a. Update HealthState in regional Redis:
      - Reset missedHeartbeats to 0
      - Update lastHeartbeatAt, lastCpu, lastMem
      - If was DEGRADED/DOWN → transition to HEALTHY
   b. Write metric data point to regional TSDB
   c. If state changed → publish to "health-changes" topic
   ↓
4. Health State Aggregator (cross-region):
   a. Each region publishes health changes to central Kafka
   b. Central aggregator updates global Redis cache
   c. Global cache: vm:{vm_id} → { health, region, metrics }
   ↓
5. Dashboard queries global Redis cache → returns VM list with live health
   Latency: heartbeat → dashboard: < 60 seconds
```

### Missing Heartbeat Detection
```
Background job per region (every 30 seconds):
  1. Scan regional Redis for VMs where lastHeartbeatAt > 60s ago
  2. Increment missedHeartbeats:
     missedHeartbeats == 1 → status stays HEALTHY (one miss is OK)
     missedHeartbeats == 2 → status = DEGRADED
     missedHeartbeats >= 3 → status = DOWN
  3. On transition to DOWN → fire alert → notify via channels
  4. Publish state change to cross-region aggregator
```

### Alert Pipeline
```
1. Health change: HEALTHY → DOWN
   ↓
2. Alert Service:
   a. Create Alert record in DB
   b. Check: is there an active maintenance window for this VM? → suppress
   c. Check: was alert already fired for this VM in last 10 min? → dedup
   d. Route notification: severity → channel mapping
   e. Send: email/Slack/PagerDuty
   ↓
3. When VM recovers (DOWN → HEALTHY):
   a. Auto-resolve alert
   b. Send recovery notification
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                    VM AGENTS (5M VMs across 10 regions)              │
│  Each agent sends heartbeat every 30 seconds to regional endpoint  │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│              PER-REGION STACK (replicated in each of 10 regions)     │
│                                                                      │
│  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │ Regional         │  │ Regional Health   │  │ Regional          │  │
│  │ Ingestion        │  │ Processor         │  │ Redis Cache       │  │
│  │ (20 pods)        │  │ (Kafka consumer)  │  │                   │  │
│  │                  │  │                   │  │ HealthState per   │  │
│  │ Validate +       │  │ Update health     │  │ VM: 500K entries  │  │
│  │ produce to       │  │ state per VM      │  │ ~50 MB per region │  │
│  │ Kafka            │  │ Detect missed     │  │                   │  │
│  │                  │  │ heartbeats        │  │                   │  │
│  └─────────────────┘  └──────────────────┘  └───────────────────┘  │
│                                                                      │
│  ┌─────────────────┐  ┌──────────────────┐                         │
│  │ Regional Kafka   │  │ Regional TSDB    │                         │
│  │ "heartbeats"     │  │ (Metrics history)│                         │
│  │ "health-changes" │  │ VictoriaMetrics  │                         │
│  └────────┬─────────┘  └──────────────────┘                         │
│           │ Replicate health changes to central                     │
└───────────┼──────────────────────────────────────────────────────────┘
            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CENTRAL / GLOBAL LAYER                            │
│                                                                      │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ Global Health     │  │ Global Redis     │  │ Dashboard         │  │
│  │ Aggregator        │  │ Cache            │  │ API Service       │  │
│  │                   │  │                  │  │                   │  │
│  │ Consume health    │  │ All 5M VMs:      │  │ List VMs + filter │  │
│  │ changes from      │  │ health + metadata│  │ Region summary    │  │
│  │ all 10 regions    │  │ ~500 MB total    │  │ Drill-down detail │  │
│  │                   │  │                  │  │                   │  │
│  │ Pods: 5-10        │  │ Redis Cluster    │  │ Pods: 20-50      │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                      │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ VM Inventory DB   │  │ Alert Service    │  │ Notification      │  │
│  │ (PostgreSQL)      │  │                  │  │ Service           │  │
│  │                   │  │ Fire/resolve     │  │                   │  │
│  │ VM metadata,      │  │ alerts on health │  │ Email, Slack,     │  │
│  │ tenants, tags     │  │ state changes    │  │ PagerDuty         │  │
│  │ ~5 GB             │  │                  │  │                   │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Regional Ingestion** | Accept heartbeats, validate, produce to Kafka | Go on K8s | 20 pods/region |
| **Regional Health Processor** | Update per-VM health state, detect missed heartbeats | Go on K8s | 10 pods/region |
| **Regional Redis** | Per-region health state cache | Redis | ~50 MB/region |
| **Regional Kafka** | Heartbeat and health-change event streaming | Kafka | 5 brokers/region |
| **Regional TSDB** | Metrics history (CPU, memory, disk) per VM | VictoriaMetrics | ~130 GB/day/region |
| **Global Aggregator** | Merge health changes from all regions | Go on K8s | 5-10 pods |
| **Global Redis** | Cross-region VM health cache (all 5M VMs) | Redis Cluster | ~500 MB |
| **Dashboard API** | Serve VM list, filters, drill-down to dashboard UI | Go on K8s | 20-50 pods |
| **VM Inventory DB** | VM metadata, tenants, tags | PostgreSQL | ~5 GB |
| **Alert Service** | Fire/resolve alerts on health changes, dedup | Go on K8s | 5-10 pods |
| **Notification Service** | Send email/Slack/PagerDuty alerts | Go on K8s | 5-10 pods |

---

## 6️⃣ Deep Dives

### DD1: Multi-Region Architecture — Regional Fan-In

**Problem**: VMs in 10 regions. Heartbeats must be processed locally (low latency). Dashboard is global (cross-region view). How to federate?

```
Architecture: REGIONAL COLLECTION → CENTRAL AGGREGATION

Per region:
  - Ingestion + Health Processor + Redis + Kafka + TSDB
  - Process heartbeats locally (< 1s from agent to health update)
  - Only HEALTH STATE CHANGES replicated to central (not raw heartbeats)
  - Reduces cross-region traffic: 167K heartbeats/sec → ~100 health changes/sec

Central:
  - Global Aggregator merges health changes from all 10 regions
  - Global Redis cache holds current state of all 5M VMs
  - Dashboard API reads from Global Redis (< 10ms)

Cross-region replication: Kafka MirrorMaker or dedicated replication
  - "health-changes" topic replicated from each region to central
  - Latency: ~100-500ms cross-region (acceptable for < 60s SLA)

Failure isolation:
  - If one region's Kafka is down → that region's health data stale
  - Other regions unaffected
  - Dashboard shows: "us-east-1: data may be stale (last updated 5 min ago)"
```

### DD2: Health State Machine — Heartbeat Timeout Detection

**Problem**: 5M VMs sending heartbeats every 30s. Must detect missing heartbeats within 60s. Can't scan all 5M VMs every 30s.

```
Approach: per-VM expiry timer (NOT full scan)

Option A: Redis key expiry (chosen)
  On heartbeat: SET heartbeat:{vm_id} 1 EX 90 (90-second TTL)
  Background checker (every 30s): scan for VMs where key expired
  Problem: Redis doesn't notify on key expiry reliably

Option B: Sorted set by last heartbeat time (chosen)
  ZADD heartbeat_times <timestamp> <vm_id>
  Every 30 seconds: ZRANGEBYSCORE heartbeat_times 0 <now - 60s>
  → Returns all VMs that haven't heartbeated in 60+ seconds
  Update their health state accordingly

  Performance: 500K VMs per region in sorted set
    ZRANGEBYSCORE with 1000 results → < 1ms
    Scan runs every 30s → process ~1000 stale VMs → < 100ms total

State transitions:
  HEALTHY → (miss 2 heartbeats, 60s) → DEGRADED → (miss 3, 90s) → DOWN
  DOWN → (receive heartbeat) → HEALTHY (immediate recovery)
  DEGRADED → (receive heartbeat) → HEALTHY
```

### DD3: Dashboard Query Performance — 10K VMs in < 500ms

**Problem**: Tenant has 10K VMs. Dashboard must load full list with health + metrics in < 500ms. Must support filtering by region, tag, health status.

```
Global Redis cache structure:
  Hash per VM: vm:{vm_id} → { name, region, health, cpu, mem, disk, tags, ... }
  Index sets:
    tenant_vms:{tenant_id} → SET of vm_ids
    region_vms:{region} → SET of vm_ids
    tag_vms:{tag_key}:{tag_value} → SET of vm_ids
    health_vms:{health_status} → SET of vm_ids

Query: "VMs in us-east-1 with tag env=prod and health=DOWN"
  1. SINTER region_vms:us-east-1 tag_vms:env:prod health_vms:DOWN → matching vm_ids
  2. For each vm_id: HGETALL vm:{vm_id} → full VM data
  3. Pagination via cursor (SSCAN)

Performance:
  SINTER with 10K-100K sets: < 5ms
  HGETALL for 100 VMs (paginated): < 10ms (pipelined)
  Total: < 50ms for filtered, paginated response

Cache updates:
  On health change → update vm:{vm_id}.health + move between health_vms sets
  On tag change → update tag index sets
  All atomic via Redis pipeline
```

### DD4: Metrics Storage — Per-VM CPU/Memory/Disk History

**Problem**: 5M VMs × 6 data points/min × 30 bytes = 1.3 TB/day. Users view per-VM charts (last 1h, 24h, 7d).

```
Regional TSDB (VictoriaMetrics per region):
  Metric: vm.cpu.usage { vm_id="vm-001", region="us-east-1", tenant="t-001" }
  Sample rate: every 10 seconds (from heartbeat)
  Retention: 30 days raw, 1 year at 5-min rollup

Query path (drill-down):
  User clicks VM → Dashboard API queries regional TSDB directly
  "Give me CPU for vm-001, last 1 hour, 10-second resolution"
  → Fast: single VM query on regional TSDB (< 50ms)

Cross-region metrics:
  NOT replicated centrally (too much data)
  Dashboard API routes to correct region based on VM's region
  "VM vm-001 is in us-east-1" → query us-east-1 TSDB directly
```

### DD5: Alerting — VM Down Detection & Notification

**Problem**: When a VM goes DOWN, alert the tenant within 2 minutes. Dedup: don't alert again if VM stays DOWN.

```
Alert pipeline:
  1. Health Processor detects: VM status → DOWN (missed 3 heartbeats)
  2. Publish to "health-changes" topic: { vm_id, old: HEALTHY, new: DOWN }
  3. Alert Service:
     a. Check: active alert for this VM? → dedup (don't re-alert)
     b. Check: maintenance window? → suppress
     c. Create Alert record: { vm_id, type: VM_DOWN, severity: CRITICAL }
     d. Route to notification channels (tenant's config)
  4. Notification Service sends email/Slack/PagerDuty

Recovery:
  When VM comes back (heartbeat received):
  → Health → HEALTHY
  → Alert auto-resolved
  → Send recovery notification: "VM web-server-01 is back HEALTHY"

Dedup key: alert:{vm_id}:{alert_type} in Redis with TTL 1 hour
```

### DD6: Tag-Based Filtering & Grouping

**Problem**: Users want: "show me all prod VMs in us-east-1 sorted by CPU usage" or "group by team tag."

```
Tag index in Redis:
  On VM tag update:
    SADD tag_vms:env:prod vm-001
    SADD tag_vms:team:infra vm-001
  
  On query "env=prod AND team=infra":
    SINTER tag_vms:env:prod tag_vms:team:infra → [vm-001, vm-003, ...]
  
  Grouping:
    "Group by team" → for each unique team tag value:
      SMEMBERS tag_vms:team:{value} → count per group
    Result: { "infra": 500, "platform": 300, "data": 200 }

  Sorting by CPU:
    After filtering: for each matching vm_id → get cpu from vm:{vm_id}
    Sort in application layer (pagination: top 100 by CPU)
```

### DD7: Failure Isolation — Region Down

**Problem**: If us-east-1 goes completely down (including our monitoring stack there), global dashboard must still work for other regions. Must clearly indicate the failed region.

```
Failure modes:

1. REGIONAL KAFKA DOWN:
   → Health Processor can't consume → health data stale for that region
   → Global Aggregator stops receiving changes from that region
   → Dashboard shows: "us-east-1: last updated 5 minutes ago" (stale indicator)
   → Other regions: unaffected

2. REGIONAL INGESTION DOWN:
   → Agents can't send heartbeats → all VMs in region appear DOWN
   → But: don't alert (mass DOWN is likely monitoring failure, not VM failure)
   → Suppress alerts when > 50% of region goes DOWN simultaneously
   → Show: "us-east-1: monitoring service degraded"

3. CENTRAL LAYER DOWN:
   → Dashboard unavailable
   → Regional stacks continue processing (heartbeats, health, local alerts)
   → Recovery: Global Aggregator catches up from regional Kafka on restart

4. GLOBAL REDIS DOWN:
   → Dashboard falls back to querying regional Redis instances directly
   → Slower (cross-region latency) but functional
   → Cache warm-up on Redis recovery from regional sources
```

### DD8: Consistency vs Freshness Trade-off

**Problem**: Dashboard shows VM health. How fresh must it be? What if different regions have different lag?

```
Freshness SLA: < 60 seconds from heartbeat to dashboard

Consistency model:
  - Regional: strongly consistent (single Redis, single Kafka consumer group)
  - Cross-region: eventually consistent (replication lag 100-500ms)
  - Dashboard: may show briefly inconsistent data across regions
    → Acceptable: "us-east-1 shows HEALTHY, central shows DEGRADED" for ~1 second

Staleness indicators:
  Each VM record includes: last_heartbeat_at, state_updated_at
  Dashboard UI:
    - If state_updated_at > 2 minutes ago → show "stale" badge
    - If state_updated_at > 5 minutes ago → show "data unavailable" for that region

Region summary freshness:
  Pre-computed every 5 seconds: { total, healthy, degraded, down } per region
  Cached in Global Redis: region_summary:{region} → counts
  Dashboard loads summary from cache (< 1ms)

Trade-off: we prioritize freshness over strong consistency
  → Users see near-real-time data, may briefly see stale data during failures
  → Explicitly show "last updated" timestamps so users know
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Architecture** | Regional collection + central aggregation | Low-latency heartbeat processing locally; cross-region view via replication |
| **Health detection** | Redis sorted set + 30s scan | Efficient missed-heartbeat detection without scanning all 5M VMs |
| **Dashboard cache** | Global Redis with index sets | < 50ms for filtered queries; tag/region/health indexes for fast intersection |
| **Metrics** | Regional TSDB (VictoriaMetrics), NOT replicated centrally | Too much data to replicate; drill-down queries routed to correct region |
| **Cross-region replication** | Only health STATE CHANGES, not raw heartbeats | Reduces bandwidth: 167K heartbeats/sec → ~100 changes/sec |
| **Alerting** | Event-driven on health state change + dedup | Alert once per DOWN event; auto-resolve on recovery; suppress during mass failures |
| **Failure isolation** | Regional stacks independent; central is optional | One region down doesn't affect others; central failure → fallback to regional queries |
| **Consistency** | Eventually consistent with staleness indicators | < 60s freshness SLA; explicitly show "last updated" timestamps |

## Interview Talking Points

1. **"Regional fan-in: process heartbeats locally, replicate only health changes centrally"** — 167K heartbeats/sec processed per-region. Only ~100 state changes/sec replicated to central. Reduces cross-region bandwidth by 1000×.

2. **"Redis sorted set for heartbeat timeout detection"** — ZADD on heartbeat, ZRANGEBYSCORE every 30s to find stale VMs. O(K + log N) for K stale VMs. State machine: HEALTHY → DEGRADED → DOWN.

3. **"Global Redis with tag/region/health index sets for < 50ms dashboard queries"** — SINTER for multi-filter. Per-VM hash for details. Paginated SSCAN. 5M VMs in ~500 MB.

4. **"Per-region TSDB for metrics, NOT replicated centrally"** — 1.3 TB/day too expensive to replicate. Drill-down queries routed to VM's region. Cross-region latency acceptable for detail view.

5. **"Failure isolation: regional stacks independent, central is optional"** — Region down → stale indicator on dashboard. Mass DOWN → suppress alerts (monitoring failure, not VM failure). Central down → fall back to regional queries.

6. **"Eventually consistent with explicit staleness indicators"** — < 60s freshness. Dashboard shows "last updated" timestamps. Stale badge if > 2 min. "Data unavailable" if > 5 min.

---

## 🔗 Related Problems
| Problem | Key Difference |
|---------|---------------|
| **AWS CloudWatch / Datadog** | Our exact design |
| **Metrics Monitoring (Prometheus)** | Prometheus is pull-based; our agents push heartbeats |
| **Health Check Service** | We do health + metrics + inventory; health checks are simpler |
| **Service Discovery (Consul)** | Consul discovers services; we monitor VMs with rich metrics |

## 🔧 Technology Alternatives
| Component | Chosen | Alternative |
|-----------|--------|------------|
| **Health state** | Redis (sorted set + hashes) | Hazelcast / Apache Ignite |
| **TSDB** | VictoriaMetrics | Prometheus / Thanos / M3DB |
| **Cross-region replication** | Kafka MirrorMaker | Custom gRPC replication / AWS Kinesis |
| **Dashboard cache** | Redis Cluster | Elasticsearch (for complex queries) |
| **Alerting** | Custom + Kafka | Alertmanager (Prometheus) |

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Time**: 45-60 min
**References**: AWS CloudWatch Architecture, Datadog Infrastructure Monitoring, Multi-Region Event Processing, Redis for Real-Time Dashboards