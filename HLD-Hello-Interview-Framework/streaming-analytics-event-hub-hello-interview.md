# Design a Streaming Analytics Platform (Azure Event Hub) — Hello Interview Framework

> **Question**: Design a platform to ingest, process, and analyze streams of events (e.g., telemetry from millions of devices) in real-time — akin to Azure Event Hub + Azure Stream Analytics, handling millions of events per second.
>
> **Asked at**: Microsoft, Amazon (Kinesis), Google, Confluent
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
1. **Event Ingestion**: Accept millions of events/sec from diverse producers (IoT devices, apps, services). HTTP and AMQP protocols. Batch and single-event publish.
2. **Partitioning**: Partition events by key for ordered, parallel processing. Configurable partition count.
3. **Consumer Groups**: Multiple independent consumers read from same stream at their own pace. Each consumer group has its own offset per partition.
4. **Retention**: Retain events for configurable period (1–90 days). Replay from any point in retention window.
5. **Ordering Guarantees**: Events with same partition key are ordered within a partition. No global ordering across partitions.
6. **Stream Processing**: SQL-like queries over streams: filter, aggregate, window functions (tumbling, sliding, session windows). Join streams with reference data.
7. **Output Sinks**: Write processed results to Cosmos DB, Blob Storage, SQL, Power BI, custom webhooks.

#### Nice to Have (P1)
- Schema Registry (Avro/JSON schema enforcement)
- Event capture (auto-archive to Blob/ADLS for batch processing)
- Geo-disaster recovery (paired namespaces)
- Exactly-once processing semantics
- Dynamic scaling (auto-inflate partitions)
- Dead letter queue for failed processing

#### Below the Line (Out of Scope)
- Full Kafka API compatibility
- Complex event processing (CEP) engine
- Batch analytics (separate from streaming)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Ingestion rate** | 10 million events/sec per namespace | IoT/telemetry scale |
| **Throughput** | 1 GB/s per throughput unit, auto-scale to 40 GB/s | High bandwidth |
| **Latency** | < 100ms ingestion (producer to available for consumer) | Near real-time |
| **Processing latency** | < 1 second for windowed aggregations | Real-time dashboards |
| **Availability** | 99.99% | Critical data pipeline |
| **Durability** | Zero event loss (replicated before ACK) | Data integrity |
| **Retention** | 1–90 days configurable | Replay capability |
| **Partitions** | Up to 1024 per Event Hub | Parallelism |

### Capacity Estimation

```
Ingestion:
  Events/sec: 10M
  Average event size: 1 KB
  Throughput: 10M × 1 KB = 10 GB/s
  Events/day: 864B
  
Storage:
  Daily: 864B × 1 KB = 864 TB/day
  7-day retention: ~6 PB
  90-day retention: ~78 PB
  
Partitions:
  1024 partitions per Event Hub
  Per partition: ~10K events/sec
  Per partition throughput: 10 MB/s
  
Consumer Groups:
  Up to 20 consumer groups per Event Hub
  Each maintains independent offset per partition
  
Broker Nodes:
  Each broker handles ~100 partitions
  Nodes needed: 1024 / 100 ≈ 10 brokers per Event Hub
  With replication (3x): 30 broker nodes
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  Namespace       │────▶│  EventHub         │────▶│  Partition         │
│                  │     │  (Topic)          │     │                    │
│ namespaceId      │     │ hubId             │     │ partitionId        │
│ name             │     │ namespaceId       │     │ hubId              │
│ region           │     │ name              │     │ beginSequenceNo    │
│ sku (Basic/Std/  │     │ partitionCount    │     │ endSequenceNo      │
│  Premium)        │     │ retentionDays     │     │ lastEnqueuedTime   │
│ throughputUnits  │     │ consumerGroups[]  │     │ sizeBytes          │
│ autoInflate      │     │ captureEnabled    │     │ brokerNodeId       │
└─────────────────┘     │ schemaId          │     └───────────────────┘
                         └──────────────────┘
                                                    ┌───────────────────┐
┌─────────────────┐     ┌──────────────────┐     │  Event             │
│  ConsumerGroup   │     │  Checkpoint       │     │                    │
│                  │     │                   │     │ sequenceNumber     │
│ groupId          │     │ consumerGroupId   │     │ partitionKey       │
│ hubId            │     │ partitionId       │     │ offset             │
│ name             │     │ offset            │     │ enqueuedTime       │
│ state            │     │ sequenceNumber    │     │ body               │
│                  │     │ lastModified      │     │ properties{}       │
└─────────────────┘     └──────────────────┘     │ systemProperties{} │
                                                    └───────────────────┘
┌─────────────────┐
│  StreamQuery     │
│                  │
│ queryId          │
│ sql              │
│ inputHub         │
│ outputSink       │
│ windowType       │
│ windowSize       │
│ state (running/  │
│  stopped)        │
└─────────────────┘
```

---

## 3️⃣ API Design

### Publish Events
```
POST /api/v1/{namespace}/{eventHub}/messages
Authorization: SharedAccessSignature sr={URI}&sig={signature}&se={expiry}&skn={keyName}
Content-Type: application/vnd.microsoft.servicebus.json

[
  {
    "Body": "{\"deviceId\":\"sensor-42\",\"temp\":72.5,\"timestamp\":\"2025-01-15T10:30:00Z\"}",
    "PartitionKey": "sensor-42",
    "Properties": { "source": "iot-hub", "region": "us-east" }
  },
  {
    "Body": "{\"deviceId\":\"sensor-43\",\"temp\":68.1,\"timestamp\":\"2025-01-15T10:30:01Z\"}",
    "PartitionKey": "sensor-43"
  }
]

Response: 201 Created
```

### Consume Events
```
GET /api/v1/{namespace}/{eventHub}/consumerGroups/{group}/partitions/{partitionId}/messages
  ?startingPosition=sequenceNumber&sequenceNumber=12345&maxCount=100
Authorization: SharedAccessSignature ...

Response: 200 OK
{
  "events": [
    {
      "sequenceNumber": 12345,
      "offset": "87654",
      "enqueuedTimeUtc": "2025-01-15T10:30:00.123Z",
      "partitionKey": "sensor-42",
      "body": "{\"deviceId\":\"sensor-42\",\"temp\":72.5}",
      "properties": { "source": "iot-hub" }
    }
  ]
}
```

### Create Stream Analytics Query
```
POST /api/v1/streamanalytics/jobs
Authorization: Bearer <token>

Request:
{
  "name": "high-temp-alerts",
  "input": { "type": "eventHub", "namespace": "iot-ns", "hub": "telemetry" },
  "query": "SELECT deviceId, AVG(temp) as avgTemp, System.Timestamp() as windowEnd FROM telemetry TIMESTAMP BY timestamp GROUP BY deviceId, TumblingWindow(minute, 5) HAVING AVG(temp) > 80",
  "output": { "type": "cosmosDB", "database": "alerts", "collection": "highTemp" }
}
```

---

## 4️⃣ Data Flow

```
Producers (IoT devices, services, apps)
        │
        │  HTTPS / AMQP / Kafka protocol
        ▼
┌───────────────────────────────────────────────┐
│           EVENT HUB INGESTION                  │
│                                               │
│  Gateway nodes (stateless, load balanced)     │
│  1. Authenticate (SAS token / Azure AD)       │
│  2. Validate event format                     │
│  3. Compute partition:                        │
│     hash(partitionKey) % partitionCount       │
│  4. Route to partition leader broker          │
│                                               │
│  Broker Node (partition leader):              │
│  1. Append event to partition log             │
│  2. Replicate to followers (quorum write)     │
│  3. ACK to producer                           │
│  4. Event now available for consumers         │
└───────────────────────────────────────────────┘
        │
        ├──▶ Consumer Group A (Stream Analytics):
        │     ├── Read from all 1024 partitions (parallel workers)
        │     ├── Apply SQL query (filter, aggregate, window)
        │     ├── Emit results to output sink (Cosmos DB)
        │     └── Checkpoint offset per partition
        │
        ├──▶ Consumer Group B (Real-time Dashboard):
        │     ├── Read latest events
        │     ├── Push to SignalR / WebSocket for live dashboard
        │     └── Checkpoint offset
        │
        ├──▶ Consumer Group C (Archive / Capture):
        │     ├── Read all events
        │     ├── Write to Azure Blob in Avro format
        │     ├── Partitioned by time: /{year}/{month}/{day}/{hour}/{minute}
        │     └── For batch analytics (Spark, Synapse)
        │
        └──▶ Consumer Group D (Alerting Service):
              ├── Filter for anomalies
              ├── Trigger notification via Notification Service
              └── Checkpoint offset
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                      PRODUCERS                                       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐    │
│  │IoT Hub  │ │App Logs │ │Click    │ │Metrics  │ │Service  │    │
│  │(devices)│ │         │ │Stream   │ │Agent    │ │Events   │    │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘    │
│       └────────────┴──────────┴────────────┴────────────┘          │
└────────────────────────────────┬────────────────────────────────────┘
                                 │ HTTPS / AMQP / Kafka
                                 ▼
┌────────────────────────────────────────────────────────────────────┐
│                    EVENT HUB CLUSTER                                │
│                                                                    │
│  ┌──────────────────────────────────────────────────────┐         │
│  │  GATEWAY LAYER (stateless)                            │         │
│  │  Auth → Validate → Partition Route → Forward          │         │
│  └──────────────────────┬───────────────────────────────┘         │
│                          ▼                                         │
│  ┌──────────────────────────────────────────────────────┐         │
│  │  BROKER LAYER (stateful, per-partition)                │         │
│  │                                                       │         │
│  │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐    │         │
│  │  │Broker 1│  │Broker 2│  │Broker 3│  │Broker N│    │         │
│  │  │        │  │        │  │        │  │        │    │         │
│  │  │P0(L)   │  │P0(F)   │  │P1(L)   │  │P2(L)   │    │         │
│  │  │P3(L)   │  │P1(F)   │  │P2(F)   │  │P3(F)   │    │         │
│  │  │P5(F)   │  │P4(L)   │  │P5(L)   │  │P4(F)   │    │         │
│  │  │        │  │        │  │        │  │        │    │         │
│  │  │L=Leader│  │F=Follow│  │        │  │        │    │         │
│  │  └────────┘  └────────┘  └────────┘  └────────┘    │         │
│  │                                                       │         │
│  │  Storage: append-only log per partition               │         │
│  │  Replication: quorum (2 of 3 ACK before commit)      │         │
│  └──────────────────────────────────────────────────────┘         │
│                                                                    │
│  ┌──────────────────────────────────────────────────────┐         │
│  │  CHECKPOINT STORE (Azure Blob / Table)                │         │
│  │  Per consumer group × per partition → offset          │         │
│  └──────────────────────────────────────────────────────┘         │
└────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────┐
│                  STREAM ANALYTICS ENGINE                            │
│                                                                    │
│  ┌──────────┐  ┌──────────────────────────────────┐              │
│  │Job Manager│  │  Worker Nodes (per partition)     │              │
│  │           │  │                                   │              │
│  │ Parse SQL │  │  ┌────────┐ ┌────────┐          │              │
│  │ Plan query│  │  │Worker 1│ │Worker 2│ ...      │              │
│  │ Assign    │  │  │        │ │        │          │              │
│  │ partitions│  │  │Read P0 │ │Read P1 │          │              │
│  │ to workers│  │  │Filter  │ │Filter  │          │              │
│  │           │  │  │Window  │ │Window  │          │              │
│  │           │  │  │Aggregate│ │Aggregate│          │              │
│  │           │  │  │Emit    │ │Emit    │          │              │
│  └──────────┘  │  └────────┘ └────────┘          │              │
│                  └──────────────────────────────────┘              │
└──────────────┬──────────────┬──────────────┬──────────────────────┘
               ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────────┐
        │Cosmos DB │  │Blob Store│  │Power BI /    │
        │(alerts)  │  │(archive) │  │Dashboard     │
        └──────────┘  └──────────┘  └──────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Partition Log Storage & Replication

**Problem**: Each partition receives thousands of events/sec. How to store them durably with low latency and support efficient reads (sequential scan)?

**Solution: Append-Only Segmented Log with Quorum Replication**

```
Partition Log Structure:

Partition 0:
  Segment 0: events [seq 0 – seq 999,999]        (1 GB, sealed)
  Segment 1: events [seq 1,000,000 – seq 1,999,999] (1 GB, sealed)  
  Segment 2: events [seq 2,000,000 – seq 2,345,678] (active, appending)

Each segment:
  - Data file: sequential binary log (events concatenated)
  - Index file: sparse index every 4 KB → (sequenceNo → fileOffset)
  - Time index: (timestamp → sequenceNo) for time-based seeks

Write Path:
  1. Event arrives at partition leader broker
  2. Append to active segment's data file (sequential write — fast!)
  3. Replicate to 2 follower brokers (parallel)
  4. Wait for quorum (2 of 3 ACK) → commit
  5. Update in-memory index
  6. Return ACK to producer
  
  Latency: < 10ms (sequential disk write + network replication)

Read Path (consumer):
  1. Consumer requests: "give me events starting at sequenceNo 2,000,000"
  2. Broker looks up index: segment 2, offset = 0
  3. Sequential read from segment file → stream to consumer
  4. Consumer processes events, checkpoints offset
  
  Throughput: limited only by disk sequential read speed (~500 MB/s per disk)

Segment Lifecycle:
  Active segment (appending) → reaches 1 GB → sealed (immutable)
  Sealed segments → eligible for:
    - Compaction (remove duplicates by key)
    - Archival to Blob Storage (Event Capture)
    - Deletion after retention period expires
```

### Deep Dive 2: Windowed Aggregation in Stream Processing

**Problem**: We need to compute "average temperature per device over 5-minute windows" across millions of devices in real-time. How does windowed aggregation work?

**Solution: Tumbling/Sliding Windows with In-Memory State**

```
Window Types:

1. Tumbling Window (fixed, non-overlapping):
   |---5min---|---5min---|---5min---|
   Events in each window aggregated independently.
   
   SELECT deviceId, AVG(temp), COUNT(*)
   FROM telemetry
   GROUP BY deviceId, TumblingWindow(minute, 5)

2. Sliding Window (overlapping):
   |---5min---|
      |---5min---|
         |---5min---|
   Window slides every N seconds. Each event appears in multiple windows.

3. Session Window (gap-based):
   Events grouped until gap > timeout.
   Good for user sessions (no activity for 30 min → close window).

Implementation (per worker, per partition):

  Worker maintains in-memory state:
    windowState: Map<windowKey, AggregateState>
    
    windowKey = (deviceId, windowStartTime)
    AggregateState = { sum: 725.5, count: 10, min: 68.0, max: 82.0 }

  For each event:
    1. Determine which window(s) the event belongs to
    2. Update aggregate state for that window
    3. Check: has the window closed? (current time > window end + watermark)
       Yes → emit result, cleanup state
       No  → continue accumulating

  Watermark (handling late events):
    Not all events arrive in order (network delays, device clock skew)
    Watermark = "we believe all events before this time have arrived"
    Watermark = max(event_timestamps) - allowed_lateness (e.g., 30 seconds)
    
    When watermark passes window end → window is closed, emit result
    Late events (after window closed):
      Option A: Drop (simplest)
      Option B: Update result (re-emit corrected aggregate)
      Option C: Side output to "late events" topic

State Checkpointing:
  Worker periodically snapshots in-memory state to Blob Storage
  On failure: new worker loads latest snapshot + replays from checkpoint offset
  Ensures exactly-once aggregation semantics
```

### Deep Dive 3: Consumer Group Offset Management

**Problem**: Multiple consumer groups read from the same Event Hub at different speeds. How to track each group's position (offset) per partition reliably?

**Solution: External Checkpoint Store with Lease-Based Partition Assignment**

```
Checkpoint Architecture:

Each consumer group tracks its offset per partition:
  ConsumerGroup "analytics" → { P0: seq 12345, P1: seq 67890, P2: seq 11111 }
  ConsumerGroup "archiver"  → { P0: seq 10000, P1: seq 60000, P2: seq 10000 }

Storage: Azure Blob Storage (one blob per consumer group per partition)
  Container: checkpoints
  Blob: {namespace}/{hub}/{consumerGroup}/{partitionId}
  Content: { "sequenceNumber": 12345, "offset": "87654", "timestamp": "..." }

Partition Assignment (Consumer Load Balancing):
  Consumer group has N consumer instances:
    Consumer A: assigned partitions [P0, P1, P2, P3]
    Consumer B: assigned partitions [P4, P5, P6, P7]
  
  Lease-based assignment:
    Each partition has a "lease" blob in Azure Storage
    Consumer acquires lease (30-second lease with renewal)
    If consumer dies → lease expires → another consumer takes over
    
  Rebalancing triggers:
    - Consumer joins/leaves the group
    - Lease expires (consumer crash)
    - Manual reassignment
    
  Balanced distribution: N partitions / M consumers = ~N/M partitions each

Checkpoint Strategy:
  Option A: Checkpoint after every event (safest, but slow)
  Option B: Checkpoint every N events (e.g., every 1000)
  Option C: Checkpoint every T seconds (e.g., every 10s)
  
  Tradeoff: frequent checkpoints → less re-processing on failure
            infrequent checkpoints → higher throughput
  
  Recommendation: checkpoint every 10 seconds or 5000 events (whichever first)
  On failure: re-process at most 10s of events (idempotent processing handles duplicates)
```
