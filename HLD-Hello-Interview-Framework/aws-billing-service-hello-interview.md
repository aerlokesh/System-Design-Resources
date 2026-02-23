# Design a Billing Service for AWS - Hello Interview Framework

> **Question**: Design a billing/metering service for a cloud provider like AWS that ingests petabytes of usage data daily from hundreds of services, aggregates it per-account with hourly granularity, handles "hot shot" accounts with disproportionate traffic, ensures auditability and exactly-once counting, and produces accurate bills.
>
> **Asked at**: Amazon, Google Cloud, Microsoft Azure, Stripe, Snowflake
>
> **Difficulty**: Hard | **Level**: Staff/Principal

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
1. **Usage Data Ingestion**: Ingest raw usage records from 200+ AWS services (EC2, S3, Lambda, etc.) at petabyte scale daily. Each record represents a billable event (API call, compute-hour, GB stored, data transferred).
2. **Hourly Aggregation**: Aggregate raw usage records into per-account, per-service, per-resource hourly summaries (e.g., "Account X used 150 EC2-hours in us-east-1 on 2025-01-10 from 14:00-15:00").
3. **Hot Shot Account Handling**: Handle accounts with disproportionately high usage (e.g., Netflix's AWS account generating 1000x more events than typical) without creating hotspots or skewing aggregation.
4. **Auditability**: Every usage record must be traceable. The system must support full audit trails — reconstruct any bill from raw records for regulatory compliance and dispute resolution.
5. **Exactly-Once Counting**: No duplicate billing (overcharge) and no missed billing (revenue loss). Financial-grade correctness despite distributed processing and failures.

#### Nice to Have (P1)
- Real-time cost dashboards (show spend as it accrues, not just after billing cycle).
- Budget alerts (notify when account approaches spending threshold).
- Reserved Instance / Savings Plan credit application.
- Multi-currency billing and tax calculation.
- Cost allocation tags (allocate costs to teams/projects within an account).

#### Below the Line (Out of Scope)
- Payment processing (charging credit cards, invoicing).
- Pricing engine (determining price per unit — assume pricing API exists).
- AWS service instrumentation (how services emit usage records).
- Free tier calculation.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Ingestion Throughput** | 32+ PB/day (~370 GB/sec) | Hundreds of AWS services emitting billions of usage records |
| **Aggregation Latency** | Hourly batches, complete within 2 hours | Bills must be available next day; dashboards need hourly updates |
| **Data Correctness** | Exactly-once — zero duplicates, zero missed records | Financial data: overcharging = legal liability, undercharging = revenue loss |
| **Auditability** | 100% traceability from bill to raw records | Regulatory compliance (SOC2, SOX), customer dispute resolution |
| **Availability** | 99.99% for ingestion (never drop records) | Lost records = lost revenue or incorrect billing |
| **Durability** | Zero data loss | Every usage record is money |
| **Tenant Isolation** | Government accounts on dedicated shards | FedRAMP, ITAR compliance requires physical data separation |
| **Scalability** | 500M+ AWS accounts, 200+ services | Growing customer base and service portfolio |
| **Recovery** | Resume from any failure without data loss or double-counting | Multi-hour batch jobs must be resumable |

### Capacity Estimation

```
Raw Usage Data:
  Total daily volume: ~32 PB
  Per hour: 32 PB / 24 = ~1.33 PB/hour
  Per second: ~370 GB/sec ingestion rate
  
  Number of usage records per day: ~10 trillion
  Average record size: ~3.2 KB
  Peak hours (US business hours): 2x average → ~740 GB/sec

Top Accounts ("Hot Shots"):
  Top 100 accounts: 20% of all usage records
  Single largest account: ~1% of total → ~320 TB/day, ~100B records/day
  Without special handling: would create massive hotspots

Aggregated Data:
  Unique (account, service, resource, region, hour) combinations: ~50 billion per day
  Aggregated record size: ~200 bytes (counters: count, duration, bytes, cost)
  Daily aggregated size: 50B × 200 bytes = ~10 TB/day
  Monthly: ~300 TB

Storage:
  Raw data retention: 7 years (regulatory requirement)
  Raw: 32 PB/day × 365 × 7 = ~81 EB → tiered to cold storage
  Aggregated: 300 TB/month × 84 months = ~25 PB
  Hot aggregated (last 3 months): ~900 TB

Sharding:
  Write shards (hourly): target 1 TB per shard → ~1333 shards per hour
  Nodes: 10 TB per node (10 × 1 TB disks) → ~134 nodes for hourly data
  Aggregation: MapReduce with ~1M keys per reducer, ~20 counters each → ~100 MB per reducer
```

---

## 2️⃣ Core Entities

### Entity 1: Usage Record (raw billing event)
```java
public class UsageRecord {
    private final String recordId;              // UUID — for deduplication
    private final String accountId;             // "123456789012" (AWS account)
    private final String serviceCode;           // "AmazonEC2", "AmazonS3", "AWSLambda"
    private final String operationType;         // "RunInstances", "PutObject", "Invoke"
    private final String resourceId;            // "i-0abc123def", "my-bucket", "my-function"
    private final String region;                // "us-east-1"
    private final String usageType;             // "BoxUsage:m5.xlarge", "DataTransfer-Out-Bytes"
    private final double quantity;              // 1.0 (hours), 1073741824 (bytes), 1 (request)
    private final String unit;                  // "Hours", "Bytes", "Requests", "GB-Mo"
    private final Instant startTime;            // When usage began
    private final Instant endTime;              // When usage ended
    private final Instant emitTime;             // When the service emitted this record
    private final Map<String, String> tags;     // Cost allocation tags
}
```

### Entity 2: Hourly Aggregate
```java
public class HourlyAggregate {
    private final String aggregateId;           // Composite key
    private final String accountId;
    private final String serviceCode;
    private final String usageType;
    private final String region;
    private final Instant hourStart;            // Truncated to hour: 2025-01-10T14:00:00Z
    
    // Counters (accumulated from raw records)
    private long recordCount;                   // Number of raw records aggregated
    private double totalQuantity;               // Sum of quantities
    private double totalDuration;               // Sum of durations (for time-based billing)
    private long totalRequests;                 // Sum of API calls
    private long totalBytesTransferred;         // Sum of data transfer
    private BigDecimal unblendedCost;           // Computed: quantity × price_per_unit
    private BigDecimal blendedCost;             // After RI/Savings Plan credits
}
```

### Entity 3: Shard Metadata
```java
public class ShardMetadata {
    private final String shardId;               // "shard-2025-01-10-14-00042"
    private final Instant hourStart;            // Which hour this shard belongs to
    private final int shardIndex;               // Shard number within the hour (0-1332)
    private final ShardType type;               // GENERAL or DEDICATED (for gov/hot accounts)
    private final String assignedNode;          // Which storage node holds this shard
    private final long recordCount;             // Records in this shard
    private final long sizeBytes;               // Size in bytes
    private final ShardStatus status;           // WRITING, SEALED, AGGREGATING, AGGREGATED, ARCHIVED
}

public enum ShardStatus {
    WRITING,      // Actively receiving records
    SEALED,       // Hour ended, no more writes
    AGGREGATING,  // Being processed by aggregation job
    AGGREGATED,   // Aggregation complete, results stored
    ARCHIVED      // Moved to cold storage
}
```

### Entity 4: Aggregation Job
```java
public class AggregationJob {
    private final String jobId;
    private final Instant targetHour;            // Which hour to aggregate
    private final JobStatus status;              // PENDING, RUNNING, COMPLETED, FAILED, RETRYING
    private final int totalShards;               // Shards to process
    private final int shardsProcessed;           // Progress tracking
    private final Instant startedAt;
    private final Instant completedAt;
    private final List<String> failedShards;     // For retry
    private final String orchestratorId;         // Which orchestrator owns this job
}
```

---

## 3️⃣ API Design

### 1. Ingest Usage Record (Service → Billing)
```
POST /api/v1/usage/ingest

Request:
{
  "records": [
    {
      "record_id": "rec_abc123",
      "account_id": "123456789012",
      "service_code": "AmazonEC2",
      "operation_type": "RunInstances",
      "resource_id": "i-0abc123def456",
      "region": "us-east-1",
      "usage_type": "BoxUsage:m5.xlarge",
      "quantity": 1.0,
      "unit": "Hours",
      "start_time": "2025-01-10T14:00:00Z",
      "end_time": "2025-01-10T15:00:00Z",
      "tags": { "team": "backend", "project": "api-gateway" }
    }
  ]
}

Response (202 Accepted):
{
  "accepted": 1,
  "rejected": 0,
  "shard_id": "shard-2025-01-10-14-00042"
}
```

> **Batch ingestion**: services send batches of 100-1000 records per call for efficiency. Fire-and-forget with async durability guarantee.

### 2. Get Hourly Usage Summary (Dashboard)
```
GET /api/v1/usage/summary?account_id=123456789012&start=2025-01-10T00:00:00Z&end=2025-01-10T23:59:59Z&granularity=HOUR

Response (200 OK):
{
  "account_id": "123456789012",
  "period": "2025-01-10",
  "granularity": "HOUR",
  "summaries": [
    {
      "hour": "2025-01-10T14:00:00Z",
      "service_code": "AmazonEC2",
      "usage_type": "BoxUsage:m5.xlarge",
      "region": "us-east-1",
      "quantity": 24.0,
      "unit": "Hours",
      "unblended_cost": 4.608,
      "currency": "USD"
    },
    {
      "hour": "2025-01-10T14:00:00Z",
      "service_code": "AmazonS3",
      "usage_type": "Requests-Tier1",
      "region": "us-east-1",
      "quantity": 1500000,
      "unit": "Requests",
      "unblended_cost": 7.50,
      "currency": "USD"
    }
  ],
  "total_cost": 12.108
}
```

### 3. Get Aggregation Job Status (Internal)
```
GET /api/v1/internal/aggregation/jobs/{job_id}

Response (200 OK):
{
  "job_id": "agg_2025-01-10-14",
  "target_hour": "2025-01-10T14:00:00Z",
  "status": "RUNNING",
  "total_shards": 1333,
  "shards_processed": 890,
  "percent_complete": 66.8,
  "started_at": "2025-01-10T16:05:00Z",
  "estimated_completion": "2025-01-10T17:30:00Z"
}
```

### 4. Audit Trail Query
```
GET /api/v1/audit/trace?account_id=123456789012&service_code=AmazonEC2&hour=2025-01-10T14:00:00Z

Response (200 OK):
{
  "aggregate": {
    "quantity": 24.0,
    "cost": 4.608,
    "record_count": 24
  },
  "source_shards": ["shard-2025-01-10-14-00042", "shard-2025-01-10-14-01205"],
  "sample_records": [
    { "record_id": "rec_abc123", "resource_id": "i-0abc123def456", "quantity": 1.0, "start_time": "..." },
    { "record_id": "rec_def456", "resource_id": "i-0abc123def456", "quantity": 1.0, "start_time": "..." }
  ],
  "total_records_available": 24,
  "full_audit_download_url": "https://billing-audit.s3.amazonaws.com/audit/123456789012/2025-01-10-14.csv"
}
```

---

## 4️⃣ Data Flow

### Flow 1: Usage Data Ingestion (Write Path)
```
1. AWS Service (e.g., EC2) generates usage record: "instance i-xyz ran for 1 hour"
   ↓
2. Service publishes record to Billing Ingestion Pipeline (Kinesis / Kafka)
   ↓
3. Ingestion Service:
   a. Validate record format and required fields
   b. Deduplicate: check record_id against dedup store (idempotency)
   c. Determine shard assignment:
      - Shard key = hash(account_id + request_time_hour + random_suffix)
      - Special accounts (gov/hot): dedicated shards by account_id
   d. Write record to assigned shard on storage node
   e. Acknowledge receipt to service
   ↓
4. Storage Node:
   - Each node has 10 × 1 TB disks
   - Each shard is a ~1 TB file for one hour
   - Sequential append (write-optimized)
   - Replicated to 3 nodes for durability
   ↓
5. At hour boundary: shard sealed → status = SEALED → ready for aggregation
```

### Flow 2: Hourly Aggregation (MapReduce-style)
```
1. Orchestrator detects: hour 14:00 shards are sealed → trigger aggregation job
   ↓
2. MAP PHASE: Read shards in parallel
   - 1333 shards × ~1 TB each = ~1.33 PB of raw data for this hour
   - Each mapper reads one shard (~1 TB)
   - With 10 parallel disks per node: read 1 TB in ~4 hours
   - With 134 nodes reading in parallel: total read time = ~4 hours
   - Each mapper emits: (account_id, service, usage_type, region) → (counters)
   ↓
3. SHUFFLE: Route intermediate results to reducers
   - Partition by account_id hash → each reducer gets all data for a subset of accounts
   ↓
4. REDUCE PHASE: Aggregate per-account metrics
   - Each reducer handles ~1M unique keys
   - 20 counters per key × 100 bytes = ~100 MB per reducer (fits in memory!)
   - Sum quantities, counts, durations, bytes for each key
   ↓
5. OUTPUT: Write aggregated results
   - Hourly aggregates → Aggregated Data Store (DynamoDB / Bigtable)
   - ~10 TB of aggregated data per day (vs 32 PB raw)
   ↓
6. Orchestrator marks job COMPLETED
   - All shards processed, results stored
   - Raw shards marked for archival (→ S3 Glacier after 90 days)
```

### Flow 3: Audit & Reconciliation
```
1. Daily reconciliation job:
   a. For each hour's aggregation, verify:
      - Every sealed shard was processed (no missed shards)
      - Sum of raw record counts == sum of aggregated record_count fields
      - No shard processed twice (check orchestrator event log)
   ↓
2. If discrepancy found:
   a. Re-read raw shards for the affected hour
   b. Recompute aggregation
   c. Compare with stored aggregates
   d. Log discrepancy and corrective action
   ↓
3. Audit trail:
   - Aggregated data links back to source shards
   - Source shards link to individual raw records
   - Full chain: bill → daily total → hourly aggregate → shards → raw records
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     AWS SERVICES (200+)                                      │
│                                                                              │
│  EC2    S3     Lambda   RDS    DynamoDB   CloudFront   ...                  │
│   │      │       │       │        │           │                             │
│   └──────┴───────┴───────┴────────┴───────────┘                             │
│          │ Usage records (billions/day)                                      │
└──────────┼──────────────────────────────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   INGESTION PIPELINE                                        │
│                   (Kinesis Data Streams / Kafka)                            │
│                                                                              │
│  • 1000+ shards, partitioned by shard key                                  │
│  • Throughput: ~370 GB/sec                                                  │
│  • Deduplication on record_id                                              │
│  • Retention: 7 days (for replay)                                          │
└──────────┬──────────────────────────────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   SHARD WRITER SERVICE                                       │
│                   (Writes records to hourly shards)                         │
│                                                                              │
│  Shard key: hash(account_id + hour) + random_suffix                        │
│  Special accounts: dedicated shards by account_id                          │
│                                                                              │
│  Output: ~1333 shards per hour, ~1 TB each                                │
└──────────┬──────────────────────────────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   RAW DATA STORAGE CLUSTER                                   │
│                   (134+ nodes, 10 × 1 TB disks each)                       │
│                                                                              │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐        ┌─────────┐              │
│  │ Node 1   │  │ Node 2   │  │ Node 3   │  ...  │ Node 134 │              │
│  │ 10 TB    │  │ 10 TB    │  │ 10 TB    │        │ 10 TB    │              │
│  │ ~10 shards│ │ ~10 shards│ │ ~10 shards│       │ ~10 shards│             │
│  └─────────┘  └─────────┘  └─────────┘        └─────────┘              │
│                                                                              │
│  Replication factor: 3 (each shard on 3 nodes)                             │
│  Total capacity: 134 × 10 TB = 1.34 PB (one hour of data)                │
└──────────┬──────────────────────────────────────────────────────────────────┘
           │ Sealed shards (hour boundary)
           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   AGGREGATION ORCHESTRATOR                                   │
│                   (Coordinates MapReduce jobs)                              │
│                                                                              │
│  • Detects sealed shards → triggers aggregation                            │
│  • Tracks per-shard processing status                                      │
│  • Ensures all shards processed (no gaps)                                  │
│  • Handles failures: retry failed shards                                   │
│  • Event log: full audit trail of all processing                          │
└──────────┬──────────────────────────────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   AGGREGATION CLUSTER (MapReduce)                           │
│                                                                              │
│  MAP (1333 mappers):                     REDUCE (~50K reducers):           │
│  ┌─────────┐  ┌─────────┐              ┌──────────┐  ┌──────────┐       │
│  │ Mapper 1 │  │ Mapper 2 │  ...        │ Reducer 1│  │ Reducer 2│  ...  │
│  │ Read     │  │ Read     │             │ ~1M keys │  │ ~1M keys │       │
│  │ shard 1  │  │ shard 2  │             │ ~100 MB  │  │ ~100 MB  │       │
│  │ (1 TB)   │  │ (1 TB)   │             │ in memory│  │ in memory│       │
│  └────┬─────┘  └────┬─────┘             └────┬─────┘  └────┬─────┘       │
│       └─────────────┴───── SHUFFLE ──────────┴──────────────┘             │
└──────────┬──────────────────────────────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   AGGREGATED DATA STORE                                      │
│                   (DynamoDB / Bigtable / Redshift)                          │
│                                                                              │
│  • Hourly aggregates: ~10 TB/day                                           │
│  • Key: (account_id, service, usage_type, region, hour)                   │
│  • Values: counters (quantity, cost, requests, bytes)                      │
│  • Serves dashboard queries and bill generation                            │
│  • Hot data (3 months): ~900 TB                                            │
│  • Cold data (7 years): → S3 + Athena                                     │
└────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│                   COLD STORAGE (S3 / Glacier)                               │
│                                                                              │
│  • Raw data archived after 90 days                                          │
│  • Parquet format for efficient querying                                    │
│  • 7-year retention (regulatory)                                            │
│  • Queryable via Athena for audits                                         │
│  • Total: ~81 EB over 7 years                                              │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Ingestion Pipeline** | Buffer and distribute usage records | Kinesis / Kafka | 1000+ shards, ~370 GB/sec |
| **Shard Writer** | Write records to hourly shards with correct partitioning | Custom service on K8s | 200+ pods |
| **Raw Data Storage** | Store hourly shards for aggregation | HDFS / S3 / custom distributed FS | 134+ nodes, 10 TB each |
| **Aggregation Orchestrator** | Coordinate MapReduce, track progress, ensure completeness | Custom service (3-node HA) | Leader-elected |
| **Aggregation Cluster** | MapReduce: read, shuffle, reduce | EMR / Spark / custom MR | 1333 mappers + 50K reducers |
| **Aggregated Data Store** | Serve hourly aggregates for dashboards and billing | DynamoDB / Bigtable / Redshift | Sharded by account_id |
| **Cold Storage** | Long-term raw data retention | S3 + Glacier | Tiered, 7-year retention |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Sharding Strategy — Preventing Hot Spots

**The Problem**: If we shard by account_id alone, the top 100 accounts (e.g., Netflix, Uber) generate 20% of all records — their shards become massively overloaded while other shards are nearly empty.

```java
public class ShardAssigner {

    /**
     * Two-level sharding strategy:
     * 
     * Level 1: Temporal sharding — data partitioned by hour
     *   Each hour gets its own set of shards (~1333 shards for ~1.33 PB)
     * 
     * Level 2: Within each hour, distribute records across shards
     *   General accounts: hash(account_id + record_id) mod N → random distribution
     *   Special accounts (gov, hot shots): dedicated shards by account_id
     * 
     * Why random for general accounts:
     *   - Even distribution → no hotspots
     *   - Any shard can receive records from any account
     *   - Aggregation handles reassembly (reducer groups by account_id)
     * 
     * Why dedicated for special accounts:
     *   - Government: data isolation required (FedRAMP, ITAR)
     *   - Hot shots: predictable load → can pre-provision shard capacity
     *   - Easier audit trail for high-value accounts
     */
    
    private final Set<String> governmentAccounts;   // Require physical isolation
    private final Set<String> hotShotAccounts;      // Top 100 by volume
    
    public String assignShard(UsageRecord record, Instant hour) {
        String accountId = record.getAccountId();
        String hourKey = formatHour(hour); // "2025-01-10-14"
        
        if (governmentAccounts.contains(accountId)) {
            // Dedicated shard per government account per hour
            return "shard-" + hourKey + "-gov-" + accountId;
        }
        
        if (hotShotAccounts.contains(accountId)) {
            // Spread across N dedicated shards for this hot account
            int subShard = hash(record.getRecordId()) % 10; // 10 shards per hot account
            return "shard-" + hourKey + "-hot-" + accountId + "-" + subShard;
        }
        
        // General: random distribution
        int shardIndex = hash(accountId + record.getRecordId()) % GENERAL_SHARD_COUNT;
        return "shard-" + hourKey + "-gen-" + shardIndex;
    }
}
```

**Shard distribution per hour**:
```
General shards: 1300 × ~1 TB = 1.3 PB
  Records from all general accounts, randomly distributed
  
Hot shot shards: 100 accounts × 10 shards each = 1000 shards
  Top 100 accounts get 10 dedicated shards each
  e.g., Netflix: 10 shards × ~32 TB = ~320 TB/day
  
Government shards: 50 accounts × 1 shard each = 50 shards
  Each gov account fully isolated
  
Total: ~2350 shards per hour
```

---

### Deep Dive 2: Aggregation — MapReduce at Petabyte Scale

**The Problem**: Each hour produces ~1.33 PB of raw data across ~2350 shards. We must aggregate this into ~10 TB of per-account hourly summaries within 2 hours.

```java
public class HourlyAggregationJob {

    /**
     * MapReduce pipeline:
     * 
     * MAP: Read each shard, extract key-value pairs
     *   Key: (account_id, service_code, usage_type, region)
     *   Value: (quantity, duration, request_count, bytes_transferred)
     * 
     * SHUFFLE: Partition by hash(account_id) → route to correct reducer
     * 
     * REDUCE: Sum all values for each key
     *   Each reducer handles ~1M keys with ~20 counters each
     *   Memory: 1M × 20 × 8 bytes = ~160 MB (fits in memory!)
     * 
     * Reading 1 TB shard:
     *   10 parallel disks × ~100 MB/sec each = 1 GB/sec per node
     *   1 TB / 1 GB/sec = ~17 minutes per shard
     *   With all 134 nodes reading in parallel: all shards read in ~17 min
     *   (Some shards are replicas; actual time ~30-60 min with scheduling)
     */
    
    // Mapper: processes one shard
    public void map(Shard shard, MapOutputCollector collector) {
        try (ShardReader reader = shard.openReader()) {
            while (reader.hasNext()) {
                UsageRecord record = reader.next();
                
                AggregationKey key = new AggregationKey(
                    record.getAccountId(),
                    record.getServiceCode(),
                    record.getUsageType(),
                    record.getRegion()
                );
                
                AggregationValue value = new AggregationValue(
                    record.getQuantity(),
                    Duration.between(record.getStartTime(), record.getEndTime()).getSeconds(),
                    1L, // One record
                    record.getUsageType().contains("Bytes") ? (long) record.getQuantity() : 0L
                );
                
                collector.emit(key, value);
            }
        }
    }
    
    // Reducer: aggregates all values for the same key
    public HourlyAggregate reduce(AggregationKey key, Iterable<AggregationValue> values) {
        double totalQuantity = 0;
        long totalDuration = 0;
        long totalRecords = 0;
        long totalBytes = 0;
        
        for (AggregationValue v : values) {
            totalQuantity += v.getQuantity();
            totalDuration += v.getDuration();
            totalRecords += v.getRecordCount();
            totalBytes += v.getBytes();
        }
        
        // Look up price per unit
        BigDecimal pricePerUnit = pricingService.getPrice(
            key.getServiceCode(), key.getUsageType(), key.getRegion());
        BigDecimal cost = pricePerUnit.multiply(BigDecimal.valueOf(totalQuantity));
        
        return HourlyAggregate.builder()
            .accountId(key.getAccountId())
            .serviceCode(key.getServiceCode())
            .usageType(key.getUsageType())
            .region(key.getRegion())
            .recordCount(totalRecords)
            .totalQuantity(totalQuantity)
            .totalDuration(totalDuration)
            .totalBytesTransferred(totalBytes)
            .unblendedCost(cost)
            .build();
    }
}
```

---

### Deep Dive 3: Orchestrator — Ensuring Completeness and Exactly-Once Processing

**The Problem**: The orchestrator must guarantee that every sealed shard is processed exactly once. If the orchestrator crashes mid-job, it must resume without re-processing already-aggregated shards or missing any.

```java
public class AggregationOrchestrator {

    /**
     * Orchestrator responsibilities:
     * 1. Detect when all shards for an hour are SEALED
     * 2. Create aggregation job with list of all shards
     * 3. Assign shards to mapper nodes
     * 4. Track completion of each shard (per-shard checkpoint)
     * 5. Handle mapper/reducer failures (retry individual shards)
     * 6. Mark job COMPLETED only when ALL shards processed
     * 7. Log every event for auditability
     * 
     * Event log ensures exactly-once:
     * - Each shard processing logged: "shard X read by node Y at time T"
     * - On restart: replay log → skip already-processed shards
     * - Idempotent reduces: re-reducing produces same result
     */
    
    public void runAggregationForHour(Instant hour) {
        // Step 1: Get all sealed shards for this hour
        List<ShardMetadata> shards = shardStore.getSealedShards(hour);
        
        // Step 2: Create job
        AggregationJob job = createJob(hour, shards);
        eventLog.append(new JobCreatedEvent(job.getJobId(), hour, shards.size()));
        
        // Step 3: Check for previously processed shards (resume from crash)
        Set<String> alreadyProcessed = eventLog.getProcessedShards(job.getJobId());
        List<ShardMetadata> remaining = shards.stream()
            .filter(s -> !alreadyProcessed.contains(s.getShardId()))
            .toList();
        
        log.info("Hour {}: {} total shards, {} already processed, {} remaining",
            hour, shards.size(), alreadyProcessed.size(), remaining.size());
        
        // Step 4: Process remaining shards
        for (ShardMetadata shard : remaining) {
            try {
                String assignedNode = nodeManager.assignMapper(shard);
                mapperService.processShard(assignedNode, shard, job.getJobId());
                
                // Log successful processing
                eventLog.append(new ShardProcessedEvent(
                    job.getJobId(), shard.getShardId(), assignedNode, Instant.now()));
                job.incrementShardsProcessed();
                
            } catch (Exception e) {
                // Retry failed shard on different node
                log.error("Shard {} failed on node, retrying...", shard.getShardId());
                job.addFailedShard(shard.getShardId());
                retryQueue.add(shard);
            }
        }
        
        // Step 5: Verify completeness
        Set<String> processedShards = eventLog.getProcessedShards(job.getJobId());
        Set<String> expectedShards = shards.stream().map(ShardMetadata::getShardId).collect(toSet());
        
        if (processedShards.equals(expectedShards)) {
            job.setStatus(JobStatus.COMPLETED);
            eventLog.append(new JobCompletedEvent(job.getJobId(), Instant.now()));
            log.info("Hour {} aggregation COMPLETE: {} shards processed", hour, shards.size());
        } else {
            Set<String> missing = new HashSet<>(expectedShards);
            missing.removeAll(processedShards);
            log.error("Hour {} aggregation INCOMPLETE: {} shards missing: {}", 
                hour, missing.size(), missing);
            alertService.sendAlert("AGGREGATION_INCOMPLETE", hour, missing);
        }
    }
}
```

---

### Deep Dive 4: Exactly-Once Billing — Deduplication and Idempotency

**The Problem**: In a distributed system processing 10 trillion records daily, duplicates are inevitable (network retries, service restarts). Duplicates mean overbilling. Missed records mean revenue loss.

```java
public class ExactlyOnceBillingGuarantee {

    /**
     * Three layers of dedup:
     * 
     * Layer 1: Ingestion dedup (record_id)
     *   - Each usage record has a unique record_id (UUID)
     *   - Ingestion service checks Bloom filter + Redis SET
     *   - Duplicate record_id → reject silently
     *   - Handles: service-side retries, network duplicates
     * 
     * Layer 2: Shard-level idempotency
     *   - Each shard has a manifest: list of record_ids it contains
     *   - If orchestrator replays a shard (crash recovery), reducer is idempotent
     *   - Idempotent reduce: SUM over same data = same result
     * 
     * Layer 3: Aggregation-level reconciliation
     *   - After aggregation: verify sum(raw record counts) == sum(aggregated counts)
     *   - If mismatch: trigger investigation
     *   - Daily reconciliation catches any drift
     */
    
    // Layer 1: Ingestion dedup
    public boolean isDuplicate(String recordId) {
        // Fast check: Bloom filter (< 0.1ms, 0.01% false positive rate)
        if (!bloomFilter.mightContain(recordId)) {
            bloomFilter.put(recordId);
            return false; // Definitely new
        }
        
        // Slow check: Redis SET (< 1ms, 100% accurate)
        boolean isNew = redis.sadd("dedup:" + currentHour(), recordId) == 1;
        return !isNew;
    }
    
    // Layer 3: Reconciliation
    public ReconciliationResult reconcile(Instant hour) {
        // Count records in raw shards
        long rawCount = shardStore.getSealedShards(hour).stream()
            .mapToLong(ShardMetadata::getRecordCount)
            .sum();
        
        // Count records in aggregated data
        long aggCount = aggregateStore.getHourlyAggregates(hour).stream()
            .mapToLong(HourlyAggregate::getRecordCount)
            .sum();
        
        if (rawCount != aggCount) {
            return ReconciliationResult.MISMATCH(rawCount, aggCount,
                "Expected " + rawCount + " records, aggregated " + aggCount);
        }
        
        return ReconciliationResult.OK(rawCount);
    }
}
```

---

### Deep Dive 5: Auditability — Full Traceability from Bill to Raw Record

**The Problem**: A customer disputes a $50,000 EC2 charge. We must be able to trace that charge back to every individual usage record that contributed to it.

```
AUDIT TRAIL (top-down):

Monthly Bill: Account 123456789012 — January 2025 — $127,500.00
  │
  ├── Service: AmazonEC2 — $98,000.00
  │   ├── Daily: Jan 10 — $3,200.00
  │   │   ├── Hourly: Jan 10 14:00-15:00 — $192.00
  │   │   │   ├── Aggregate: BoxUsage:m5.xlarge, us-east-1 — $4.608 (24 instances × 1 hour)
  │   │   │   │   ├── Source shard: shard-2025-01-10-14-00042
  │   │   │   │   │   ├── Record: rec_abc123 — i-0abc123 — 1.0 hr — $0.192
  │   │   │   │   │   ├── Record: rec_abc124 — i-0abc124 — 1.0 hr — $0.192
  │   │   │   │   │   └── ... (22 more records)
  │   │   │   │   └── Source shard: shard-2025-01-10-14-01205
  │   │   │   │       └── Record: rec_abc145 — i-0abc145 — 1.0 hr — $0.192
  │   │   │   │
  │   │   │   └── Aggregate: DataTransfer-Out, us-east-1 — $187.392 (2 TB)
  │   │   │       └── (similar drill-down)

Every level links to the next:
  Bill → daily rollup → hourly aggregate → source shards → raw records
  
  All stored. All queryable. All immutable (append-only).
```

---

### Deep Dive 6: Failure Recovery — Resumable Aggregation

**The Problem**: A 2-hour aggregation job processing 1.33 PB might crash at the 90-minute mark. We cannot afford to restart from scratch.

```
Recovery via event log:

Normal processing:
  T=0    Job started for hour 14:00, 2350 shards
  T=5    Shard 0001 processed by node-17 ✓ (logged)
  T=5    Shard 0002 processed by node-22 ✓ (logged)
  ...
  T=90   Shard 1800 processed ✓ (logged)
  T=90   ★ ORCHESTRATOR CRASHES ★

Recovery:
  T=92   New orchestrator elected (leader election)
  T=92   Read event log: shards 0001-1800 already processed
  T=93   Resume from shard 1801 → only 550 shards remaining
  T=130  Job COMPLETED (all 2350 shards processed)

Total delay from crash: ~42 minutes (vs ~130 minutes full restart)
Data loss: ZERO (event log is durable)
Double-counting: ZERO (already-processed shards skipped)
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Sharding** | Time (hourly) + random + dedicated (gov/hot) | Prevents hot spots; gov isolation; predictable shard sizes |
| **Aggregation** | Hourly batch MapReduce (not streaming) | Financial correctness requires bounded processing; easier auditability |
| **Deduplication** | Bloom filter + Redis SET (record_id) | Two layers: fast probabilistic + exact. Catches 100% of duplicates |
| **Orchestrator** | Event-log based with per-shard checkpoints | Resumable from any failure; exactly-once processing; full audit trail |
| **Storage tiering** | Hot (3 mo) → Warm (1 yr) → Cold (7 yr Glacier) | Raw data is 32 PB/day — must tier aggressively; audits rare after 90 days |
| **Reducer memory** | ~100 MB per reducer (1M keys × 20 counters) | Fits in RAM → no disk spills; fast aggregation |
| **Reconciliation** | Daily batch: sum(raw) == sum(aggregated) | Catches drift from edge cases; financial correctness guarantee |

## Interview Talking Points

1. **"Two-level sharding: time + random/dedicated"** — Hourly partitioning bounds read scope. Random within hour prevents hot spots. Dedicated shards for gov (isolation) and hot shots (predictable capacity).
2. **"MapReduce with 1M keys per reducer fits in memory"** — 50B unique aggregation keys ÷ 50K reducers = 1M keys each. 20 counters × 8 bytes = ~160 MB. No disk spills, fast in-memory aggregation.
3. **"Orchestrator event log enables exactly-once"** — Every shard processing logged. On crash: replay log → skip done shards. Idempotent reducers ensure re-processing yields same result.
4. **"Bloom filter + Redis for ingestion dedup"** — Bloom filter catches 99.99% in < 0.1ms. Redis SET catches the rest. record_id uniqueness ensures no duplicate billing.
5. **"Full audit trail: bill → hourly → shards → raw records"** — Every aggregation links to source shards. Every shard contains record manifest. Customer can drill from $50K charge to individual API calls.
6. **"Hourly batch over streaming for financial correctness"** — Streaming aggregation is harder to audit and reconcile. Hourly batches with bounded input enable: verification of completeness, reconciliation, and reprocessing.
7. **"32 PB/day to 10 TB/day: 3200x compression via aggregation"** — Raw records average 3.2 KB each. Aggregated records average 200 bytes. Massive reduction in data stored for querying/billing.
8. **"7-year cold storage via S3 Glacier for regulatory compliance"** — Raw data tiered: hot (90 days on cluster) → S3 Standard (1 year) → Glacier (7 years). Parquet format enables Athena queries for audits.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Ad Click Aggregator** | Same streaming aggregation pattern | Ad clicks need 30s freshness; billing uses hourly batches for correctness |
| **Distributed Metrics (Datadog)** | Same high-throughput ingestion + aggregation | Metrics are approximate-OK; billing must be exact |
| **Data Warehouse (Snowflake)** | Same petabyte-scale storage and querying | Warehouse is general purpose; billing has specific aggregation + pricing logic |
| **Payment Processing (Stripe)** | Same financial correctness requirements | Payments are transactional; billing is batch aggregation of usage |
| **Log Aggregation (ELK)** | Same ingestion pipeline pattern | Logs are best-effort; billing is exactly-once |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Ingestion** | Kinesis / Kafka | SQS + Lambda | Lower throughput; simpler; serverless |
| **Storage** | Custom distributed FS | S3 + Athena | If direct S3 write throughput is sufficient |
| **Aggregation** | Custom MapReduce | Apache Spark on EMR | Leverage existing Spark expertise; less custom code |
| **Aggregated store** | DynamoDB | Redshift / BigQuery | If complex analytical queries needed (joins, window functions) |
| **Orchestrator** | Custom (event-log based) | Apache Airflow | DAG-based scheduling; existing Airflow deployment |
| **Dedup** | Bloom + Redis | DynamoDB conditional writes | Stronger consistency; simpler; more expensive at scale |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (choose 2-3 based on interviewer interest)

---

**References**:
- AWS Cost and Usage Report (CUR) Architecture
- MapReduce: Simplified Data Processing on Large Clusters (Dean & Ghemawat, 2004)
- Apache Spark at Petabyte Scale
- Exactly-Once Semantics in Distributed Systems
- SOX Compliance for Financial Data Systems
- AWS S3 Glacier Storage Tiers and Lifecycle Policies
