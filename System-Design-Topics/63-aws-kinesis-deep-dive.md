# 🎯 Topic 63: AWS Kinesis Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Amazon Kinesis for real-time data streaming — Kinesis Data Streams architecture (shards, partition keys, sequence numbers), producer and consumer patterns, Enhanced Fan-Out, scaling and resharding, ordering guarantees, exactly-once processing with deduplication, Kinesis vs Kafka comparison, Kinesis Data Firehose for delivery, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Kinesis Data Streams Architecture](#kinesis-data-streams-architecture)
3. [Shards — The Unit of Parallelism](#shards--the-unit-of-parallelism)
4. [Partition Keys and Data Distribution](#partition-keys-and-data-distribution)
5. [Producers — Writing to Kinesis](#producers--writing-to-kinesis)
6. [Consumers — Reading from Kinesis](#consumers--reading-from-kinesis)
7. [Enhanced Fan-Out (EFO)](#enhanced-fan-out-efo)
8. [Ordering Guarantees](#ordering-guarantees)
9. [Scaling and Resharding](#scaling-and-resharding)
10. [Retention and Replay](#retention-and-replay)
11. [Exactly-Once Processing](#exactly-once-processing)
12. [Kinesis Data Firehose](#kinesis-data-firehose)
13. [Kinesis Data Analytics](#kinesis-data-analytics)
14. [Kinesis vs Kafka — When to Use Which](#kinesis-vs-kafka--when-to-use-which)
15. [Error Handling and Dead Letter Queues](#error-handling-and-dead-letter-queues)
16. [Cost Optimization](#cost-optimization)
17. [Performance Benchmarks](#performance-benchmarks)
18. [Real-World Production Patterns](#real-world-production-patterns)
19. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
20. [Common Interview Mistakes](#common-interview-mistakes)
21. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Amazon Kinesis Data Streams** is a fully managed, serverless real-time data streaming service. It ingests hundreds of thousands of records per second from producers, retains them for 24 hours to 365 days, and allows multiple consumers to process records independently — enabling real-time analytics, ETL, log aggregation, and event-driven architectures.

```
The mental model:

  Kinesis Data Stream = a PIPE with N parallel lanes (shards).
  
  Producers (many) → push records into the pipe → partitioned across shards
  Consumers (many) → read from the pipe independently → at their own pace

  Key properties:
    1. Records are ORDERED within a shard (by sequence number)
    2. Records are RETAINED (24h default, up to 365 days)
    3. Multiple consumers read the SAME data independently
    4. Throughput scales by adding shards (each shard: 1 MB/s in, 2 MB/s out)
    5. Fully managed — no servers, no ZooKeeper, no brokers to manage

  ┌──────────┐     ┌─────────────────────────────────────┐     ┌──────────┐
  │ Producer │────→│        Kinesis Data Stream           │────→│ Consumer │
  │          │     │  Shard 1: [rec1, rec2, rec3, ...]    │     │ App A    │
  │ Producer │────→│  Shard 2: [rec4, rec5, rec6, ...]    │────→│          │
  │          │     │  Shard 3: [rec7, rec8, rec9, ...]    │     │ Consumer │
  │ Producer │────→│  Shard 4: [rec10, rec11, rec12, ...] │────→│ App B    │
  └──────────┘     └─────────────────────────────────────┘     └──────────┘
```

### When to Use Kinesis

```
USE Kinesis when:
  ✅ Real-time event streaming (< 1 second latency)
  ✅ Multiple consumers need the same data (fan-out)
  ✅ Ordering matters (per partition key)
  ✅ You need replay capability (re-read from a point in time)
  ✅ AWS-native, fully managed, no ops overhead desired
  ✅ Moderate throughput (up to ~1M records/sec with 1000 shards)

DON'T USE Kinesis when:
  ❌ Simple queue semantics (use SQS instead — simpler, cheaper)
  ❌ Extreme throughput > 1M records/sec (Kafka on MSK handles better)
  ❌ Multi-region replication needed (Kafka MirrorMaker, or Kinesis cross-region is limited)
  ❌ Long-term storage (Kinesis max 365 days; use S3/Glacier for archival)
  ❌ Request-response messaging (use SQS or API Gateway)
```

---

## Kinesis Data Streams Architecture

### Components

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Kinesis Data Stream                               │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ Shard 1                                                        │  │
│  │  [Seq:1001, Seq:1002, Seq:1003, ...] → 1 MB/s write, 2 MB/s read│
│  └───────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ Shard 2                                                        │  │
│  │  [Seq:2001, Seq:2002, Seq:2003, ...]                          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ Shard 3                                                        │  │
│  │  [Seq:3001, Seq:3002, Seq:3003, ...]                          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Each shard: 1 MB/s ingress, 2 MB/s egress, 1000 records/s write   │
│  Stream capacity = N_shards × per_shard_capacity                    │
└─────────────────────────────────────────────────────────────────────┘

Record structure:
  {
    "PartitionKey": "user:42",           // Determines which shard
    "Data": "<base64-encoded payload>",   // Up to 1 MB
    "SequenceNumber": "495834...",        // Assigned by Kinesis (monotonic per shard)
    "ApproximateArrivalTimestamp": "..."  // When Kinesis received it
  }
```

### How Records are Routed to Shards

```
Producer sends: PutRecord(StreamName, PartitionKey, Data)

Kinesis hashes the partition key: MD5(PartitionKey) → 128-bit hash
Each shard owns a RANGE of the hash space (like consistent hashing):
  Shard 1: hash range [0, 85070591730234615865843651857942052863]
  Shard 2: hash range [85070591730234615865843651857942052864, 170141183460469231731687303715884105727]
  Shard 3: hash range [170141183460469231731687303715884105728, 340282366920938463463374607431768211455]

Record's hash falls into one shard's range → record goes to that shard.

Key insight: ALL records with the same PartitionKey go to the SAME shard.
  → Guarantees ordering for that partition key.
  → But also means a hot partition key = hot shard!
```

---

## Shards — The Unit of Parallelism

### Shard Capacity Limits

```
Per shard (hard limits):
  Write:  1 MB/sec OR 1,000 records/sec (whichever is hit first)
  Read:   2 MB/sec (shared across all consumers, unless Enhanced Fan-Out)
  
  With Enhanced Fan-Out:
  Read:   2 MB/sec PER consumer (dedicated throughput per consumer)

Examples:
  4 shards: 4 MB/s write, 8 MB/s read, 4000 records/s
  100 shards: 100 MB/s write, 200 MB/s read, 100K records/s
  1000 shards: 1 GB/s write, 2 GB/s read, 1M records/s (practical max)
```

### Shard Count Calculation

```
Formula:
  shards_needed = max(
    incoming_data_MB_per_sec / 1,         // Write throughput limit
    incoming_records_per_sec / 1000,       // Write record limit
    read_data_MB_per_sec / 2              // Read throughput limit (shared mode)
  )

Example: Ad click stream
  100K events/sec, average 500 bytes/event = 50 MB/s

  By records: 100,000 / 1,000 = 100 shards
  By data:    50 MB/s / 1 MB/s = 50 shards
  
  Need: 100 shards (records limit is the bottleneck)
```

---

## Partition Keys and Data Distribution

### Good vs Bad Partition Keys

```
GOOD partition keys (uniform distribution):
  ✅ user_id — millions of users → records spread evenly across shards
  ✅ device_id — IoT devices → uniform distribution
  ✅ transaction_id — unique per transaction → perfect distribution
  ✅ session_id — unique per session

BAD partition keys (hot shards):
  ❌ country_code — "US" gets 60% of traffic → one shard overwhelmed
  ❌ date — all records same date → all go to one shard
  ❌ constant — same key for all records → single shard bottleneck
  ❌ popular_user_id — celebrity's events flood one shard

FIX for hot keys:
  Add random suffix: "US_0", "US_1", ..., "US_9"
  Spreads US traffic across 10 shards.
  Tradeoff: Lose ordering within US (ordering only within each sub-key).
  Usually acceptable — ordering per-user matters, not per-country.
```

### Explicit Hash Keys

```
Instead of letting Kinesis hash the partition key, you can specify ExplicitHashKey:
  PutRecord(PartitionKey="user:42", ExplicitHashKey="12345678901234567890")

This gives EXACT control over which shard receives the record.
Use case: Load balancing across shards manually when partition keys are skewed.
```

---

## Producers — Writing to Kinesis

### PutRecord (Single Record)

```python
import boto3

kinesis = boto3.client('kinesis')

response = kinesis.put_record(
    StreamName='ad-clicks',
    Data=json.dumps({'user_id': 'u42', 'ad_id': 'a789', 'timestamp': '...'}).encode(),
    PartitionKey='user:42'
)
# Returns: ShardId, SequenceNumber
```

### PutRecords (Batch — Up to 500 Records)

```python
records = [
    {'Data': json.dumps(event).encode(), 'PartitionKey': event['user_id']}
    for event in batch_of_events
]

response = kinesis.put_records(
    StreamName='ad-clicks',
    Records=records[:500]  # Max 500 records per batch, max 5 MB total
)

# Check for partial failures:
if response['FailedRecordCount'] > 0:
    for i, record in enumerate(response['Records']):
        if 'ErrorCode' in record:
            # Retry this record (ProvisionedThroughputExceededException)
            retry_queue.append(records[i])
```

### Kinesis Producer Library (KPL) — High-Throughput

```
KPL optimizations:
  1. Aggregation: Combines multiple small records into one Kinesis record
     (up to 1 MB). Reduces PutRecords API calls.
  2. Collection: Batches records before sending. Configurable buffer time.
  3. Retry with backoff: Automatic retry on ProvisionedThroughputExceededException.
  4. Async: Non-blocking writes. Callbacks on success/failure.
  
  Without KPL: 1000 records/sec → 1000 API calls/sec
  With KPL:    1000 records/sec → ~10 API calls/sec (100 records aggregated per call)
  
  KPL adds ~100-200ms latency (buffering time) in exchange for 100x fewer API calls.
```

---

## Consumers — Reading from Kinesis

### Shared Throughput Consumer (GetRecords)

```
Default mode: All consumers SHARE the 2 MB/s per shard read limit.

Consumer A polls: GetRecords(ShardIterator) → gets records → processes
Consumer B polls: GetRecords(ShardIterator) → gets records → processes

Total: A + B share 2 MB/s per shard.
If 5 consumers: each gets ~400 KB/s per shard (2 MB / 5).

Polling frequency: Max 5 GetRecords calls/sec per shard per consumer.
  Each call returns up to 10,000 records or 10 MB.
  
Latency: 200ms-1s (polling interval dependent).
```

### Kinesis Client Library (KCL) — Managed Consumer

```
KCL handles:
  1. Shard assignment: Distributes shards across consumer instances.
  2. Checkpointing: Tracks last processed sequence number (in DynamoDB).
  3. Load balancing: If a consumer instance dies, reassigns its shards.
  4. Resharding: Automatically adapts when shards split/merge.

Architecture:
  KCL Consumer App (3 instances)
    Instance 1: Processes Shard 1, Shard 2
    Instance 2: Processes Shard 3, Shard 4
    Instance 3: Processes Shard 5, Shard 6
    
  Each instance runs a "record processor" that you implement:
    def process_records(records, checkpointer):
        for record in records:
            process(record)
        checkpointer.checkpoint()  # Save progress to DynamoDB

Max parallelism = number of shards.
  6 shards → max 6 consumer instances (each gets 1 shard).
  If you have 10 instances with 6 shards → 4 instances are idle.
```

---

## Enhanced Fan-Out (EFO)

### The Problem with Shared Throughput

```
Shared mode: 2 MB/s per shard shared across ALL consumers.
  3 consumers on a 10-shard stream:
  Each consumer gets: 2 MB/s × 10 shards / 3 = 6.67 MB/s total
  
  Problem: Adding more consumers REDUCES each one's throughput.
  5 consumers: each gets only 4 MB/s. Latency increases.
```

### Enhanced Fan-Out Solution

```
EFO: Each consumer gets a DEDICATED 2 MB/s per shard (not shared).

  3 consumers on a 10-shard stream:
  Each consumer gets: 2 MB/s × 10 shards = 20 MB/s (full throughput)
  
  Total stream egress: 3 × 20 MB/s = 60 MB/s (vs 20 MB/s shared mode)

How it works:
  Instead of polling (GetRecords), EFO uses HTTP/2 PUSH:
  Consumer subscribes → Kinesis pushes records to the consumer.
  
  Latency improvement:
    Shared (polling): 200ms-1s
    EFO (push):       ~70ms (average)

Cost:
  EFO: $0.015/shard-hour/consumer + $0.013/GB retrieved
  Shared: No additional charge (included in shard cost)
  
  Use EFO when: Multiple consumers, latency-sensitive, or high throughput needed.
  Use Shared when: Single consumer, cost-sensitive, latency < 1s acceptable.
```

---

## Ordering Guarantees

### Per-Shard Ordering

```
WITHIN a shard: Records are STRICTLY ORDERED by sequence number.
  PutRecord with PartitionKey="user:42" → Shard 3
  Next PutRecord with PartitionKey="user:42" → Shard 3 (same shard, higher sequence)
  
  Consumer reads from Shard 3: gets records in EXACT order they were written.
  
ACROSS shards: NO ordering guarantee.
  Records in Shard 1 and Shard 2 have independent sequence numbers.
  You cannot determine global order across shards.

Implication:
  If you need ordering for user_42's events → use "user:42" as partition key.
  All events for user_42 go to the same shard → read in order.
  
  If you need GLOBAL ordering of ALL events → use ONE shard (but 1 MB/s max).
  This is rarely needed — per-entity ordering is usually sufficient.
```

---

## Scaling and Resharding

### Splitting a Shard (Scale Up)

```
Shard 3 is hot (approaching 1 MB/s limit):
  Split Shard 3 → Shard 3a + Shard 3b
  
  Shard 3's hash range is split in half:
    Shard 3a: [hash_start, hash_mid]
    Shard 3b: [hash_mid+1, hash_end]
  
  New records for Shard 3's partition keys now go to 3a or 3b.
  Shard 3 remains for reads (already-written records) until retention expires.
  
  Consumers (KCL) automatically detect the split and create new record processors.

API:
  kinesis.split_shard(
      StreamName='ad-clicks',
      ShardToSplit='shardId-000000000003',
      NewStartingHashKey='170141183460469231731687303715884105728'
  )
```

### Merging Shards (Scale Down)

```
Two adjacent shards are underutilized:
  Merge Shard 5 + Shard 6 → Shard 7
  
  Combined capacity: still 1 MB/s write, 2 MB/s read for the merged shard.
  Saves cost (charged per shard-hour).

API:
  kinesis.merge_shards(
      StreamName='ad-clicks',
      ShardToMerge='shardId-000000000005',
      AdjacentShardToMerge='shardId-000000000006'
  )
```

### On-Demand Mode (Auto-Scaling)

```
Kinesis On-Demand mode:
  No shard management needed. AWS auto-scales shards based on traffic.
  
  Capacity: Scales up to 200 MB/s write and 400 MB/s read (default).
  Can request higher limits.
  
  Cost: More expensive per GB than provisioned mode.
    On-Demand: $0.080/GB ingested + $0.04/GB retrieved
    Provisioned: $0.015/shard-hour (~$10.80/shard/month)
  
  Use On-Demand when:
    ✅ Traffic is unpredictable / spiky
    ✅ Don't want to manage shard count
    ✅ Willing to pay more for convenience
  
  Use Provisioned when:
    ✅ Traffic is predictable
    ✅ Cost optimization matters
    ✅ You want explicit control over parallelism
```

---

## Retention and Replay

### Retention Period

```
Default: 24 hours (free)
Extended: Up to 365 days (additional cost: $0.023/shard-hour for 7 days retention)

Use cases for extended retention:
  - Replay events from 3 days ago (bug fix, reprocess with new logic)
  - Multiple consumers processing at different speeds
  - Disaster recovery: re-read from a specific timestamp
```

### Iterator Types for Replay

```
TRIM_HORIZON:    Start from the oldest record in the shard.
LATEST:          Start from the newest record (real-time only).
AT_TIMESTAMP:    Start from records at or after a specific timestamp.
AT_SEQUENCE_NUMBER:    Start from a specific sequence number.
AFTER_SEQUENCE_NUMBER: Start after a specific sequence number.

Example (replay from 2 hours ago):
  iterator = kinesis.get_shard_iterator(
      StreamName='ad-clicks',
      ShardId='shardId-000000000001',
      ShardIteratorType='AT_TIMESTAMP',
      Timestamp=datetime.utcnow() - timedelta(hours=2)
  )
  # Now read from 2 hours ago forward
```

---

## Exactly-Once Processing

### The Challenge

```
Kinesis guarantees at-least-once delivery to consumers:
  - If a consumer crashes after processing but before checkpointing → re-reads records.
  - If a GetRecords call times out → consumer retries → may get duplicates.

For exactly-once:
  Consumer must be IDEMPOTENT or use deduplication.
```

### Deduplication Strategies

```
Strategy 1: Idempotent writes (recommended)
  Each record has a unique ID (e.g., event_id in the payload).
  Consumer: INSERT ... ON CONFLICT (event_id) DO NOTHING
  Or: Upsert based on event_id → duplicate writes are no-ops.

Strategy 2: Sequence number tracking
  Before processing: Check if sequence_number already processed.
  SETNX processed:{shard}:{seq_num} 1 EX 86400
  If exists → skip (already processed).
  If new → process + checkpoint.

Strategy 3: Transactional checkpoint
  Process record + update checkpoint in the SAME database transaction.
  If transaction succeeds → both happen. If it fails → neither happens → retry is safe.
```

---

## Kinesis Data Firehose

### What It Is

```
Kinesis Data Firehose: Fully managed DELIVERY service.
  Takes data from Kinesis Data Streams (or direct producers) 
  and delivers to destinations (S3, Redshift, Elasticsearch, Splunk).

Key difference from Data Streams:
  Data Streams: YOU manage consumers, checkpointing, processing logic.
  Firehose: AWS manages delivery. You configure destination + transformation.
  
  Data Streams: Real-time consumer apps with custom logic.
  Firehose: Managed ETL pipeline to storage/analytics destinations.
```

### Architecture

```
Producers → Kinesis Data Stream → Firehose → S3 / Redshift / OpenSearch

Or direct to Firehose:
Producers → Kinesis Firehose → S3 (buffered, batched, optionally transformed)

Buffering:
  Buffer size: 1-128 MB (deliver when buffer is full)
  Buffer interval: 60-900 seconds (deliver when interval passes)
  Whichever condition is met first triggers delivery.

Transformation:
  AWS Lambda function invoked per batch.
  Transform records before delivery (e.g., JSON → Parquet, add fields, filter).
```

---

## Kinesis Data Analytics

```
Kinesis Data Analytics: Run SQL or Apache Flink on streaming data.

Use cases:
  - Real-time aggregations: "Clicks per minute per campaign"
  - Anomaly detection: "Alert if error rate > 5% in last 5 minutes"
  - Enrichment: Join streaming data with reference data
  
Architecture:
  Kinesis Data Stream → Kinesis Data Analytics (Flink) → Output stream / S3 / Redshift
  
  Think of it as "managed Flink on Kinesis."
```

---

## Kinesis vs Kafka — When to Use Which

| Aspect | Kinesis | Kafka (MSK) |
|---|---|---|
| **Management** | Fully managed (serverless) | Semi-managed (MSK), or self-hosted |
| **Max throughput** | ~1 GB/s (1000 shards) | 10+ GB/s (more brokers) |
| **Latency** | 70ms (EFO) to 200ms (shared) | 5-50ms |
| **Retention** | 24h to 365 days | Unlimited (disk-based) |
| **Scaling** | Add shards (minutes) | Add brokers + rebalance (hours) |
| **Consumers** | KCL, Lambda, EFO | Consumer groups, Kafka Streams |
| **Ordering** | Per shard (partition key) | Per partition (key) |
| **Ecosystem** | AWS-native (Lambda, Firehose) | Open-source ecosystem |
| **Multi-region** | Limited | MirrorMaker, Confluent Cluster Linking |
| **Cost (low volume)** | Cheaper (per shard-hour) | More expensive (always-on brokers) |
| **Cost (high volume)** | More expensive | Cheaper per GB |
| **Ops overhead** | Zero | Medium (MSK) to High (self-hosted) |

### Interview Decision Script

> *"For an AWS-native real-time pipeline with moderate throughput (< 1 GB/s) and zero ops overhead, I'd choose Kinesis Data Streams. It's fully managed — no brokers, no ZooKeeper, no rebalancing. For higher throughput (> 1 GB/s), lower latency (< 50ms), multi-region replication, or when the team already has Kafka expertise, I'd use MSK (managed Kafka). The key tradeoff: Kinesis trades throughput and flexibility for operational simplicity. If I need both real-time processing AND managed delivery to S3/Redshift, Kinesis + Firehose is a single-service solution that Kafka would need additional connectors for."*

---

## Error Handling and Dead Letter Queues

### Lambda Consumer Failures

```
Kinesis + Lambda integration:
  Lambda polls Kinesis shards → invokes your function with a batch of records.
  
  If Lambda fails (exception, timeout):
    Retries indefinitely by default → shard is BLOCKED until batch succeeds.
    
  Solutions:
    1. MaximumRetryAttempts: 3 → after 3 retries, skip batch
    2. BisectBatchOnFunctionError: true → split batch in half, retry smaller batches
       (isolates the "poison pill" record)
    3. DestinationConfig.OnFailure: Send failed records to SQS DLQ or SNS
    
  Configuration:
    EventSourceMapping:
      MaximumRetryAttempts: 3
      BisectBatchOnFunctionError: true
      DestinationConfig:
        OnFailure:
          Destination: arn:aws:sqs:...:kinesis-dlq
```

---

## Cost Optimization

```
Kinesis cost components:
  1. Shard-hour: $0.015/shard/hour = $10.80/shard/month
  2. PUT payload units: $0.014 per million (25 KB = 1 unit)
  3. Extended retention: $0.023/shard/hour (beyond 24h)
  4. Enhanced Fan-Out: $0.015/shard-hour/consumer + $0.013/GB

Cost optimization strategies:
  1. Right-size shards: Monitor CloudWatch (IncomingBytes, IncomingRecords).
     If utilization < 30% → merge shards.
  2. Use KPL aggregation: Combine multiple small records into 1 KB units.
     Reduces PUT units (cost per million 25 KB units).
  3. Limit retention: 24h is free. Only extend if replay is needed.
  4. Use Shared consumers when EFO isn't needed (saves $0.015/shard-hour/consumer).
  5. On-Demand vs Provisioned: Provisioned is cheaper for steady workloads.

Example monthly cost (100 shards, 50 MB/s, 1 consumer):
  Shard-hours: 100 × $10.80 = $1,080
  PUT units: 50 MB/s × 86400 / 25 KB × $0.014/M = ~$2,400
  Total: ~$3,500/month for 100 shards (4.3 TB/day ingested)
```

---

## Performance Benchmarks

```
Single shard:
  Write: 1 MB/s or 1000 records/s
  Read (shared): 2 MB/s, 5 GetRecords calls/s
  Read (EFO): 2 MB/s per consumer, push-based (~70ms latency)

100-shard stream:
  Write: 100 MB/s, 100K records/s
  Read: 200 MB/s (shared) or 200 MB/s per consumer (EFO)
  Latency (producer → consumer): 200ms (shared), 70ms (EFO)

End-to-end pipeline latency:
  Producer → Kinesis → Lambda → DynamoDB: ~300-500ms
  Producer → Kinesis → KCL consumer → PostgreSQL: ~200-400ms
  Producer → Kinesis → Firehose → S3: 60-900 seconds (buffering)
```

---

## Real-World Production Patterns

### Pattern 1: Real-Time Analytics Pipeline

```
Clickstream events → Kinesis → Lambda (enrich) → Kinesis → Firehose → S3 + Redshift

  Web app: Sends click events to Kinesis (100K events/sec)
  Lambda: Enriches with user profile data (from DynamoDB)
  Second Kinesis stream: Enriched events
  Firehose: Batches and writes to S3 (Parquet format) every 5 minutes
  Redshift: COPY from S3 for SQL analytics
  
  Latency: Real-time (enrichment in <1s), batch to warehouse (5 min)
```

### Pattern 2: IoT Sensor Ingestion

```
10K IoT devices × 1 reading/sec = 10K records/sec

  Devices → IoT Core → Kinesis (10 shards) → Lambda → DynamoDB (latest) + S3 (history)
  
  DynamoDB: Stores latest reading per device (hot path for dashboards)
  S3: Stores all readings (cold path for ML training)
```

### Pattern 3: Log Aggregation

```
500 EC2 instances × CloudWatch Logs → Kinesis → OpenSearch

  CloudWatch Logs subscription filter → Kinesis Data Stream
  Kinesis → Firehose → OpenSearch (for search/dashboards)
  Kinesis → Firehose → S3 (for long-term archival)
  
  Cost-effective alternative to direct CloudWatch Logs → OpenSearch.
```

---

## Interview Talking Points & Scripts

### Script 1: Core Architecture

> *"Kinesis Data Streams is a fully managed real-time streaming service. Data is organized into shards — each shard supports 1 MB/s write and 2 MB/s read. Records are partitioned by a partition key (MD5 hash determines shard assignment), and ordering is guaranteed within a shard. For a 100K events/sec ad click stream, I'd provision 100 shards (1000 records/shard/sec limit). Consumers use KCL for automatic shard assignment and DynamoDB-based checkpointing. Multiple consumer apps read independently from the same stream without interfering."*

### Script 2: Scaling

> *"Scaling Kinesis is straightforward: split hot shards or switch to On-Demand mode. On-Demand auto-scales based on traffic — good for unpredictable workloads. For provisioned mode, I'd monitor CloudWatch's IncomingBytes and IteratorAgeMilliseconds. If iterator age increases → consumers are falling behind → add shards or optimize consumer code. Splitting doubles the shards in that hash range; KCL consumers automatically detect and redistribute. The key limitation: max 1 reshard operation per second per stream, and each split/merge takes a few seconds to propagate."*

### Script 3: Kinesis vs Kafka

> *"I'd choose Kinesis over Kafka when I want zero operational overhead — no brokers, no ZooKeeper, no version upgrades. Kinesis scales by adding shards with a simple API call. The tradeoff: lower maximum throughput (~1 GB/s vs Kafka's 10+ GB/s), higher latency (70-200ms vs Kafka's 5-50ms), and less ecosystem flexibility. For an AWS-native stack where we already use Lambda, DynamoDB, and S3, Kinesis integrates natively with Firehose, Lambda triggers, and Data Analytics. If we need extreme throughput or multi-region replication, I'd use MSK."*

### Script 4: Ordering and Partition Keys

> *"Kinesis guarantees ordering within a shard. To maintain order for a specific entity — say, all events for user:42 — I use user_id as the partition key. All user:42 events go to the same shard and are read in sequence. The risk: if one partition key is extremely hot (celebrity with millions of followers), it overwhelms a single shard. Solution: add a random suffix to the partition key ('user:42_0' through 'user:42_9'), spreading load across 10 shards — but losing per-user ordering across those sub-keys. For most use cases, per-user ordering isn't needed beyond a few seconds of events."*

### Script 5: Lambda Integration

> *"Kinesis + Lambda is the simplest real-time processing pattern: Lambda automatically polls shards, invokes your function with a batch of records (up to 10K or 6 MB), and checkpoints on success. For error handling, I'd configure BisectBatchOnFunctionError (splits the batch to isolate poison pills), MaximumRetryAttempts: 3, and an SQS dead-letter queue for failed records. Key tuning: batch size (larger = fewer invocations, higher latency), parallelization factor (process multiple batches per shard concurrently), and tumbling window (aggregate records before processing)."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Confusing Kinesis with SQS

**Bad**: "I'd use Kinesis as a message queue between services."
**Fix**: Kinesis is a STREAM (multiple consumers, replay, ordering). SQS is a QUEUE (single consumer, message deleted after processing, no ordering guarantee).

### ❌ Mistake 2: Ignoring shard limits

**Bad**: "I'll just put all events on one shard."
**Fix**: One shard = 1 MB/s write, 1000 records/s. Calculate shards needed based on throughput.

### ❌ Mistake 3: Using a bad partition key

**Bad**: "I'll use the timestamp as partition key."
**Fix**: Timestamp causes all records to go to ONE shard (same hash). Use entity IDs (user_id, device_id) for uniform distribution.

### ❌ Mistake 4: Not mentioning Enhanced Fan-Out

**Bad**: "Multiple consumers share the 2 MB/s read limit per shard."
**Fix**: Enhanced Fan-Out gives each consumer dedicated 2 MB/s per shard with push-based delivery (~70ms latency).

### ❌ Mistake 5: Assuming exactly-once delivery

**Bad**: "Kinesis guarantees exactly-once."
**Fix**: Kinesis provides at-least-once. Consumers must be idempotent or deduplicate (using record IDs or sequence numbers).

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│                AWS KINESIS DEEP DIVE — CHEAT SHEET                    │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  WHAT: Fully managed real-time data streaming service.               │
│  UNIT: Shard (1 MB/s write, 2 MB/s read, 1000 records/s).          │
│  ORDERING: Guaranteed per shard (same partition key = same shard).   │
│  RETENTION: 24h (free) to 365 days.                                  │
│                                                                      │
│  PRODUCERS: PutRecord, PutRecords (batch 500), KPL (aggregation).   │
│  CONSUMERS: GetRecords (polling), KCL (managed), Lambda (serverless).│
│  FAN-OUT: Enhanced Fan-Out = dedicated 2 MB/s per consumer (push).   │
│                                                                      │
│  PARTITION KEY: Determines shard (MD5 hash). Use entity IDs.         │
│  HOT KEY FIX: Random suffix ("user:42_0" ... "user:42_9").          │
│                                                                      │
│  SCALING:                                                            │
│    Provisioned: SplitShard / MergeShards manually.                  │
│    On-Demand: Auto-scales, pay per GB (no shard management).        │
│                                                                      │
│  DELIVERY: At-least-once. Consumer must be idempotent.              │
│  CHECKPOINT: DynamoDB (KCL) or manual (GetRecords).                  │
│                                                                      │
│  FIREHOSE: Managed delivery to S3/Redshift/OpenSearch (buffered).    │
│  ANALYTICS: Managed Flink/SQL on streaming data.                     │
│                                                                      │
│  vs SQS: Kinesis = stream (multi-consumer, replay, ordering).        │
│           SQS = queue (single consumer, delete-after-read).          │
│  vs Kafka: Kinesis = simpler, lower throughput, zero ops.            │
│            Kafka = more flexible, higher throughput, more ops.        │
│                                                                      │
│  COST: ~$10.80/shard/month + $0.014/million PUT units.              │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 10: Message Queues vs Event Streams** — Kinesis vs SQS vs Kafka
- **Topic 39: Kafka Deep Dive** — Detailed Kafka comparison
- **Topic 48: Flink Deep Dive** — Kinesis Data Analytics uses Flink
- **Topic 52: Architecting on AWS** — Service mapping

---

*This document is part of the System Design Interview Deep Dive series.*
