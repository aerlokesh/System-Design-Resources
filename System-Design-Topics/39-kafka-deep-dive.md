# 🎯 Topic 39: Kafka Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Apache Kafka's architecture, topics, partitions, consumer groups, replication, delivery guarantees, exactly-once semantics, producer configuration, consumer lag, Kafka Streams, Kafka Connect, and how to articulate Kafka design decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Kafka Architecture Overview](#kafka-architecture-overview)
3. [Topics and Partitions](#topics-and-partitions)
4. [Partition Key Design — The Most Critical Decision](#partition-key-design--the-most-critical-decision)
5. [Producers — Writing to Kafka](#producers--writing-to-kafka)
6. [Consumers and Consumer Groups](#consumers-and-consumer-groups)
7. [Replication and Fault Tolerance](#replication-and-fault-tolerance)
8. [Delivery Guarantees](#delivery-guarantees)
9. [Exactly-Once Semantics (EOS)](#exactly-once-semantics-eos)
10. [Consumer Lag and Monitoring](#consumer-lag-and-monitoring)
11. [Retention and Compaction](#retention-and-compaction)
12. [Kafka vs Other Messaging Systems](#kafka-vs-other-messaging-systems)
13. [Kafka Streams and Stream Processing](#kafka-streams-and-stream-processing)
14. [Common Kafka Patterns in System Design](#common-kafka-patterns-in-system-design)
15. [Performance Tuning and Numbers](#performance-tuning-and-numbers)
16. [Real-World System Examples](#real-world-system-examples)
17. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
18. [Common Interview Mistakes](#common-interview-mistakes)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Apache Kafka** is a distributed event streaming platform that acts as a durable, high-throughput, fault-tolerant message bus. It's the backbone of modern event-driven architectures, decoupling producers from consumers while guaranteeing message ordering within partitions and configurable delivery guarantees.

```
Traditional Message Queue (RabbitMQ, SQS):
  Producer → Queue → ONE Consumer processes → message deleted
  Fire-and-forget: Once consumed, message is gone.

Kafka (Distributed Log):
  Producer → Topic (partitioned log) → MULTIPLE Consumer Groups read independently
  Durable: Messages retained for days/weeks (configurable).
  Replayable: Any consumer can re-read from any offset.
  
  Key difference: Kafka is a DISTRIBUTED LOG, not a queue.
  Messages are appended, not dequeued. Multiple consumers read the same data.
```

---

## Kafka Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                    KAFKA CLUSTER                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Broker 1              Broker 2              Broker 3         │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐ │
│  │ Topic-A P0   │     │ Topic-A P1   │     │ Topic-A P2   │ │
│  │ (leader)     │     │ (leader)     │     │ (leader)     │ │
│  │              │     │              │     │              │ │
│  │ Topic-A P1   │     │ Topic-A P2   │     │ Topic-A P0   │ │
│  │ (replica)    │     │ (replica)    │     │ (replica)    │ │
│  │              │     │              │     │              │ │
│  │ Topic-B P0   │     │ Topic-B P1   │     │ Topic-B P0   │ │
│  │ (leader)     │     │ (leader)     │     │ (replica)    │ │
│  └──────────────┘     └──────────────┘     └──────────────┘ │
│                                                              │
│  ZooKeeper / KRaft: Cluster metadata, leader election        │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Key components:
  Broker: A Kafka server that stores data and serves clients
  Topic: A named stream of records (like a database table)
  Partition: A topic is split into partitions for parallelism
  Replica: Each partition is replicated across brokers for fault tolerance
  Producer: Writes records to topics
  Consumer: Reads records from topics
  Consumer Group: A set of consumers that cooperate to consume a topic
  ZooKeeper/KRaft: Manages cluster metadata and leader election
```

---

## Topics and Partitions

### Topics

```
A topic is a named category or feed of records:
  "user-clicks"     — all click events
  "order-events"    — all order lifecycle events
  "payment-results" — payment success/failure events

Topics are:
  ✅ Append-only (records are never modified after writing)
  ✅ Immutable (records can only be appended, not updated or deleted)
  ✅ Ordered within partitions (not across partitions)
  ✅ Retained for a configurable duration (7 days default, up to infinite)
```

### Partitions — The Unit of Parallelism

```
A topic is divided into N partitions:

  Topic "clicks" with 6 partitions:
  
  Partition 0: [msg0, msg6, msg12, msg18, ...]
  Partition 1: [msg1, msg7, msg13, msg19, ...]
  Partition 2: [msg2, msg8, msg14, msg20, ...]
  Partition 3: [msg3, msg9, msg15, msg21, ...]
  Partition 4: [msg4, msg10, msg16, msg22, ...]
  Partition 5: [msg5, msg11, msg17, msg23, ...]

Each partition:
  - Is an ordered, immutable sequence of records (a log)
  - Lives on ONE broker (leader) with replicas on other brokers
  - Is consumed by ONE consumer within a consumer group
  - Has its own offset counter (position in the log)

Why partitions matter:
  - Parallelism: 6 partitions → 6 consumers can process in parallel
  - Throughput: Each partition can handle ~10MB/sec writes
  - Ordering: Records with the same key always go to the same partition
    → Guaranteed ordering per key
```

### How Many Partitions?

```
Guidelines:
  Target throughput: Total throughput / throughput per partition
  
  Single partition: ~10 MB/sec write, ~30 MB/sec read
  
  If you need 100 MB/sec write throughput:
    100 / 10 = 10 partitions minimum
    
  If you need 50 consumers for parallel processing:
    50 partitions minimum (1 partition per consumer)

  Rule of thumb: Start with max(throughput_needs, consumer_count)
  
  Over-provisioning is OK: Having 50 partitions when you need 10 is fine.
  Under-provisioning hurts: Can add partitions later, but existing data
    won't be redistributed (only new data uses new partitions).
    
  Warning: Don't create 10,000 partitions "just in case."
    Each partition uses memory on every broker (file handles, buffers).
    Typical: 10-100 partitions per topic.
    LinkedIn: ~100K partitions across the cluster (not per topic).
```

---

## Partition Key Design — The Most Critical Decision

### How Partition Key Works

```
Producer sends record with a key:
  produce("clicks", key="user_123", value={click_event})

Kafka hashes the key to determine the partition:
  partition = hash(key) % num_partitions
  
  hash("user_123") % 6 = 2  → Partition 2
  hash("user_456") % 6 = 5  → Partition 5
  hash("user_123") % 6 = 2  → Partition 2 (same user, same partition!)

Guarantee: All records with the same key go to the same partition.
  → All events for user_123 are in partition 2, in order.
  → Consumer of partition 2 sees user_123's events in exact order.
```

### Choosing the Right Partition Key

| Partition Key | Ordering Guarantee | Risk | Use Case |
|---|---|---|---|
| `user_id` | All events for one user in order | Hot users create hot partitions | User activity streams |
| `order_id` | All events for one order in order | Even distribution (random IDs) | Order lifecycle events |
| `conversation_id` | All messages in one chat in order | Large groups → hot partition | Chat messaging |
| `campaign_id` | All clicks for one campaign in order | Popular campaigns → hot partition | Ad click tracking |
| `null` (no key) | Round-robin, NO ordering guarantee | Even distribution | When ordering doesn't matter |
| `device_id` | All events from one device in order | IoT: millions of devices → even | IoT telemetry |

### Hot Partition Problem

```
If key = "celebrity_user_id":
  All 10M engagement events for this user → ONE partition → ONE consumer
  Consumer capacity: 10K events/sec
  Actual load: 100K events/sec → consumer can't keep up

Solution: Key salting (see Topic 31)
  key = user_id + "_" + random(0, 19)
  Spreads across 20 partitions
  Requires downstream aggregation
```

### Null Key (Round-Robin)

```
When to use null key (no ordering guarantee):
  produce("metrics", key=null, value={cpu_usage: 72%})
  
  Kafka distributes round-robin across partitions.
  Maximum parallelism and even distribution.
  No ordering guarantee between records.
  
  Use when:
    ✅ Each record is independent (metrics, logs)
    ✅ Order doesn't matter (aggregate later anyway)
    ❌ Never for events that need causal ordering
```

---

## Producers — Writing to Kafka

### Producer Configuration

```
Key producer settings:

acks (acknowledgment level):
  acks=0:  Fire-and-forget. Producer doesn't wait for any acknowledgment.
           Fastest. Risk: Data loss if broker crashes.
  acks=1:  Leader acknowledges. Producer waits for leader to write to log.
           Moderate. Risk: Data loss if leader crashes before replicating.
  acks=all: ALL in-sync replicas acknowledge. 
           Slowest. No data loss (unless all replicas fail simultaneously).

  For billing/payments: acks=all (can't lose a payment event)
  For analytics/metrics: acks=1 (fast, acceptable tiny loss risk)
  For debug logging: acks=0 (maximum throughput, loss is OK)

batch.size and linger.ms:
  Kafka batches multiple records into one network request.
  batch.size=16384 (16KB): Send when batch reaches this size.
  linger.ms=5: Wait up to 5ms to fill the batch before sending.
  
  Higher values: Better throughput (fewer network calls), more latency.
  Lower values: Lower latency, more network overhead.
  
  Sweet spot: linger.ms=5, batch.size=64KB for most use cases.

compression.type:
  none: No compression (fastest, most bandwidth)
  gzip: Best compression ratio (70-80%), CPU intensive
  snappy: Good compression (50-60%), fast (recommended default)
  lz4: Good compression (50-60%), fastest decompression
  zstd: Best overall (compression + speed balance)
  
  Recommendation: snappy or lz4 for real-time pipelines.
```

### Idempotent Producer

```
Problem: Network timeout → producer retries → duplicate record!

  Producer sends record → network timeout (broker received it, ACK lost)
  Producer retries → broker writes it again → DUPLICATE!

Solution: enable.idempotence=true (default since Kafka 3.0)
  Each producer gets a producer ID (PID).
  Each record gets a sequence number (per partition).
  Broker deduplicates: If it sees PID + sequence already → skip.
  
  Result: Exactly-once delivery from producer to broker.
  No application-level dedup needed on the producer side.
```

### Producer Partitioner

```
Default partitioner behavior:
  If key != null: hash(key) % num_partitions → deterministic partition
  If key == null: Round-robin (or sticky partitioning in newer versions)

Sticky partitioning (Kafka 2.4+):
  For null keys, instead of round-robin per record:
  Batch records to the SAME partition until batch is full.
  Then switch to another partition for the next batch.
  
  Why? Better batching → fewer requests → higher throughput.
  Old round-robin: Each batch has 1 record → many tiny network calls.
  Sticky: Each batch has many records → fewer large network calls.

Custom partitioner:
  class GeoPartitioner implements Partitioner {
      int partition(String topic, Object key, ...) {
          String region = extractRegion(key);
          return regionToPartition(region);
      }
  }
  Use case: Route US traffic to partitions 0-4, EU to 5-9.
```

---

## Consumers and Consumer Groups

### Consumer Group Model

```
Consumer Group: A set of consumers that cooperate to consume a topic.

Topic "clicks" with 6 partitions:
Consumer Group "analytics-service":
  Consumer A: reads Partition 0, 1
  Consumer B: reads Partition 2, 3
  Consumer C: reads Partition 4, 5

Consumer Group "billing-service" (independent):
  Consumer X: reads Partition 0, 1, 2
  Consumer Y: reads Partition 3, 4, 5

Key rules:
  1. Each partition is consumed by EXACTLY ONE consumer in a group
  2. One consumer can read multiple partitions
  3. Different consumer groups read INDEPENDENTLY
     (analytics and billing both read all clicks, at their own pace)
  4. Max useful consumers per group = number of partitions
     (7 consumers for 6 partitions → 1 consumer is idle)
```

### Rebalancing

```
When does rebalancing happen?
  - Consumer joins the group (new instance started)
  - Consumer leaves the group (crashes, shutdown)
  - Partition count changes (admin adds partitions)

What happens during rebalance:
  1. All consumers stop reading (pause processing)
  2. Group coordinator reassigns partitions
  3. Consumers resume reading from last committed offset
  
  Duration: 5-30 seconds (depending on group size)
  Impact: No processing during rebalance → temporary lag spike

Cooperative rebalancing (Kafka 2.4+):
  Instead of stopping ALL consumers:
  Only the affected partitions are reassigned.
  Other consumers continue reading unaffected partitions.
  Much less disruption than "stop-the-world" rebalancing.

Static group membership:
  group.instance.id = "consumer-1" (fixed ID per consumer)
  If consumer restarts quickly (< session.timeout):
    Gets the SAME partitions back → no rebalance triggered.
  Reduces unnecessary rebalances from rolling deployments.
```

### Offset Management

```
Each consumer tracks its position in each partition:

  Partition 0: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, ...]
                                       ^
                               committed offset = 7
                               (consumer has processed records 0-6)

Offset commit strategies:

Auto-commit (enable.auto.commit=true):
  Consumer auto-commits every 5 seconds (auto.commit.interval.ms).
  Risk: If consumer crashes between commit and processing → data loss.
  Risk: If consumer processes but hasn't committed → reprocessing on restart.

Manual commit (recommended):
  Consumer explicitly commits after processing:
  
  records = consumer.poll(Duration.ofMillis(100));
  for (record : records) {
      process(record);  // Process first
  }
  consumer.commitSync();  // Then commit
  
  If crash after process but before commit → reprocessing (at-least-once).
  Application must be idempotent to handle reprocessing.
```

---

## Replication and Fault Tolerance

### Replication Model

```
Each partition has:
  - 1 Leader replica (handles ALL reads and writes)
  - N-1 Follower replicas (replicate from leader, serve as backup)

Replication factor (RF) = 3 (typical):
  Partition 0: Leader on Broker 1, Followers on Broker 2 and Broker 3
  
  Write flow:
    Producer → Leader (Broker 1) → replicate to Follower (Broker 2, 3)
    With acks=all: Producer gets ACK only after all ISR replicas acknowledge
  
  Failure:
    Broker 1 crashes → Broker 2 (a follower) is elected new leader
    No data loss (Broker 2 has all committed records)
    Producer/consumer automatically reconnect to new leader

ISR (In-Sync Replicas):
  The set of replicas that are "caught up" with the leader.
  A follower is in ISR if its lag < replica.lag.time.max.ms (default 10s).
  
  min.insync.replicas=2:
    With RF=3, need at least 2 replicas (including leader) in ISR.
    If only 1 replica is alive → producer gets error (not enough replicas).
    Prevents writing to a single copy (which could be lost).
    
  RF=3 + min.insync.replicas=2 + acks=all:
    Can tolerate 1 broker failure with zero data loss.
    The gold standard for production Kafka.
```

### Unclean Leader Election

```
unclean.leader.election.enable=false (recommended):
  If ALL ISR replicas are down, Kafka waits for one to recover.
  No data loss, but partition is unavailable until recovery.
  
unclean.leader.election.enable=true:
  If ALL ISR replicas are down, a non-ISR (out-of-sync) replica becomes leader.
  Partition remains available, but DATA LOSS for records not replicated to this replica.
  
  For payment events: false (never lose data)
  For analytics events: true might be acceptable (availability over durability)
```

---

## Delivery Guarantees

### Three Levels

```
At-Most-Once:
  acks=0, no retries
  Message may be lost, never duplicated
  Use: Debug logs, non-critical metrics

At-Least-Once (default):
  acks=1 or acks=all, retries enabled
  Message never lost, may be duplicated
  Consumer must be idempotent (handle duplicates)
  Use: Most event processing, analytics, notifications

Exactly-Once:
  Idempotent producer + transactional consumer
  Message never lost, never duplicated
  Highest overhead, most complex
  Use: Financial transactions, billing, inventory updates
```

---

## Exactly-Once Semantics (EOS)

### How It Works

```
Exactly-once in Kafka requires:

1. Idempotent Producer (producer → Kafka):
   enable.idempotence=true
   Producer assigns sequence numbers per partition.
   Broker deduplicates by PID + sequence.
   Guarantees: Each record written exactly once to Kafka.

2. Transactional Producer (cross-partition atomicity):
   producer.initTransactions();
   producer.beginTransaction();
   producer.send(topic_A, record_1);
   producer.send(topic_B, record_2);
   producer.commitTransaction();
   
   Both writes succeed or both are rolled back.
   Consumers with isolation.level=read_committed see only committed records.

3. Consumer + Producer Transaction (read-process-write):
   Read from input topic → process → write to output topic
   Commit input offset AND output records atomically.
   
   If consumer crashes mid-transaction:
     Transaction not committed → both input offset and output records rolled back.
     On restart: Re-reads input → reprocesses → writes output → commits.
     Net result: Each input record produces exactly one output record.
```

### When to Use EOS

```
✅ Use EOS when:
  Financial processing (each payment processed exactly once)
  Inventory updates (decrement exactly once per order)
  Stream processing with side effects (Kafka Streams exactly-once)
  Billing event counting (each click counted exactly once)

❌ Don't use EOS when:
  Simple log aggregation (duplicates are cheap to filter)
  Metrics collection (approximate is fine)
  Non-critical notifications (double notification is OK)
  
  EOS has ~10-20% throughput overhead due to transaction coordination.
  Use at-least-once + idempotent consumers for better performance when possible.
```

---

## Consumer Lag and Monitoring

### What Is Consumer Lag?

```
Consumer lag = Latest offset (newest message) - Consumer's committed offset

Topic "clicks", Partition 3:
  Latest offset: 1,000,000
  Consumer committed: 998,500
  Lag: 1,500 records

Lag interpretation:
  Lag = 0: Consumer is fully caught up (real-time)
  Lag = 1,000: 1-2 seconds behind (normal)
  Lag = 100,000: Minutes behind (backpressure)
  Lag = 10,000,000: Hours behind (critical issue)
  Lag growing: Consumer can't keep up (input > processing rate)
  Lag stable: Consumer is keeping up but behind (processing = input rate)
  Lag shrinking: Consumer is catching up (processing > input rate)
```

### Monitoring Tools

```
Kafka built-in:
  kafka-consumer-groups.sh --describe --group my-group
  → Shows lag per partition per consumer

Burrow (LinkedIn):
  Dedicated consumer lag monitoring service
  Evaluates lag status: OK, WARNING, ERROR
  Considers lag trend (growing, stable, shrinking)

JMX metrics:
  records-lag-max: Maximum lag across all partitions for this consumer
  records-consumed-rate: Records consumed per second
  commit-rate: Offset commits per second

Alerting thresholds:
  Lag > 10K for 5 minutes → Warning alert
  Lag > 100K for 5 minutes → Critical alert (page on-call)
  Lag growing for 15 minutes → Capacity alert (need more consumers)
```

---

## Retention and Compaction

### Time-Based Retention

```
retention.ms=604800000 (7 days, default)
  Records older than 7 days are deleted.
  
  retention.ms=-1: Infinite retention (never delete)
  retention.ms=86400000: 1 day retention (aggressive cleanup)

retention.bytes=1073741824 (1 GB per partition)
  If partition exceeds 1 GB, oldest records deleted regardless of age.
  
  Use both: Delete when EITHER time or size limit is reached.
```

### Log Compaction

```
cleanup.policy=compact:
  Instead of deleting old records, keep ONLY the latest record per key.
  
  Before compaction:
    offset 0: key=A, value=1
    offset 1: key=B, value=2
    offset 2: key=A, value=3   ← newer value for key A
    offset 3: key=C, value=4
    offset 4: key=B, value=5   ← newer value for key B
    
  After compaction:
    offset 2: key=A, value=3   ← latest for A
    offset 3: key=C, value=4   ← latest for C (only value)
    offset 4: key=B, value=5   ← latest for B

Use cases:
  - Database changelog (CDC): Keep latest state of each row
  - Configuration: Keep latest config value per key
  - User profiles: Keep latest profile per user_id
  
  Tombstone: key=A, value=null → after compaction, key A is deleted entirely.
```

---

## Kafka vs Other Messaging Systems

| Feature | Kafka | RabbitMQ | SQS | Pulsar |
|---|---|---|---|---|
| **Model** | Distributed log | Message queue | Managed queue | Distributed log |
| **Ordering** | Per partition | Per queue | Best-effort (FIFO option) | Per partition |
| **Retention** | Days/weeks/infinite | Until consumed | 14 days max | Tiered (infinite) |
| **Replay** | Yes (any offset) | No (consumed = gone) | No | Yes |
| **Multi-consumer** | Yes (consumer groups) | Yes (exchanges) | No (1 consumer per message) | Yes |
| **Throughput** | 1M+ msg/sec | 50K msg/sec | 3K msg/sec (std) | 1M+ msg/sec |
| **Latency** | 5-50ms | 1-5ms | 20-100ms | 5-50ms |
| **Operations** | Complex (manage cluster) | Moderate | None (managed) | Complex |
| **Best for** | Event streaming, CDC | Task queues, RPC | Simple decoupling | Multi-tenancy |

---

## Kafka Streams and Stream Processing

### Kafka Streams (Library, Not a Cluster)

```
Kafka Streams is a Java library for stream processing ON TOP of Kafka.
No separate cluster needed (unlike Flink or Spark Streaming).
Runs as part of your application (just a JAR dependency).

Example: Count clicks per campaign in real-time

StreamsBuilder builder = new StreamsBuilder();
builder.stream("clicks")
    .groupByKey()
    .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
    .count()
    .toStream()
    .to("click-counts");

KafkaStreams streams = new KafkaStreams(builder.build(), config);
streams.start();

This reads from "clicks" topic, counts per key per minute,
and writes results to "click-counts" topic.
Scales by running multiple instances (each gets subset of partitions).
```

### When Kafka Streams vs Flink

```
Kafka Streams:
  ✅ Simple transformations (filter, map, aggregate)
  ✅ Don't want to manage a separate cluster
  ✅ Java/Kotlin applications
  ✅ Input and output are both Kafka
  ❌ No Python/Go support
  ❌ Limited windowing capabilities vs Flink

Apache Flink:
  ✅ Complex event processing (CEP)
  ✅ Multi-language (Java, Python, SQL)
  ✅ Advanced windowing (session windows, custom triggers)
  ✅ Input from Kafka + databases + files + APIs
  ❌ Requires separate Flink cluster
  ❌ More operational complexity
```

---

## Common Kafka Patterns in System Design

### Pattern 1: Event Sourcing

```
Store every state change as an immutable event:

Topic "order-events":
  {order_id: 123, event: "created", items: [...]}
  {order_id: 123, event: "paid", payment_id: "pay_456"}
  {order_id: 123, event: "shipped", tracking: "UPS123"}
  {order_id: 123, event: "delivered", signed_by: "Alice"}

Benefits:
  Complete audit trail (every state change recorded)
  Rebuild state by replaying events (from any point in time)
  Multiple consumers derive different views from same events
```

### Pattern 2: CDC (Change Data Capture)

```
Capture database changes and stream to Kafka:

PostgreSQL → Debezium (reads WAL) → Kafka topic "db-changes"

Consumers:
  - Elasticsearch consumer: Updates search index
  - Redis consumer: Updates cache
  - Analytics consumer: Updates ClickHouse aggregates

Benefits: No dual-write problem. Single source of truth (database).
  Kafka topic is a faithful mirror of every database change.
```

### Pattern 3: Transactional Outbox

```
Write business data + event in ONE database transaction:

BEGIN;
  INSERT INTO orders (id, ...) VALUES (123, ...);
  INSERT INTO outbox (event_type, payload) VALUES ('order_created', {...});
COMMIT;

Debezium reads outbox table → publishes to Kafka.
Guarantees: If order exists in DB, event exists in Kafka.
```

### Pattern 4: Dead Letter Queue

```
Consumer can't process a record (poison message, invalid data):

  try:
      process(record)
  except PermanentError:
      producer.send("clicks-dlq", record)  # Move to DLQ
      consumer.commitOffset(record)          # Skip in main topic

DLQ topic: "clicks-dlq"
  Monitored separately. Investigated by engineering.
  Can be reprocessed after bug fix.
```

### Pattern 5: Fan-Out (One Event → Multiple Consumers)

```
One event published to "order-created" topic:

Consumer Group 1 (Billing): Generates invoice
Consumer Group 2 (Shipping): Initiates fulfillment
Consumer Group 3 (Analytics): Updates dashboards
Consumer Group 4 (Notification): Sends confirmation email

Each consumer group reads ALL records independently.
No coordination between groups. Each processes at its own pace.
```

---

## Performance Tuning and Numbers

### Throughput Numbers

```
Single partition:
  Write: ~10 MB/sec (configurable, limited by disk I/O)
  Read: ~30 MB/sec (sequential reads from page cache)

Single broker (commodity hardware):
  Write: ~200 MB/sec (20 partitions × 10 MB/sec)
  Read: ~600 MB/sec (20 partitions × 30 MB/sec)
  
  In messages: ~500K messages/sec (at 400 bytes average)

Cluster (10 brokers):
  Write: ~2 GB/sec aggregate
  Read: ~6 GB/sec aggregate
  Messages: ~5M messages/sec

LinkedIn production:
  7+ trillion messages/day
  ~100 Kafka clusters
  ~4,000+ brokers total
```

### Latency Optimization

```
End-to-end latency (producer → consumer):

  Batch mode (default): 5-50ms
    linger.ms=5 + network + consumer poll interval
    
  Low-latency mode: 2-10ms
    linger.ms=0 (send immediately, lower throughput)
    fetch.min.bytes=1 (consumer fetches with minimal wait)
    
  Tradeoff: Lower latency = lower throughput (smaller batches)
```

---

## Real-World System Examples

### LinkedIn — Kafka's Origin

```
Activity stream processing:
  Profile views, searches, connections → Kafka → Analytics
  7+ trillion messages/day
  Kafka invented at LinkedIn (2011), open-sourced to Apache
```

### Uber — Real-Time Ride Matching

```
Driver location updates → Kafka → Location Service
Trip events → Kafka → Analytics, Billing, ETAs
Kafka handles: ~1M events/sec for location tracking alone
```

### Netflix — Event Pipeline

```
Playback events, UI interactions → Kafka → Analytics, Recommendations
200 billion events/day processed through Kafka pipelines
Used for: A/B testing, content recommendations, billing
```

---

## Interview Talking Points & Scripts

### Why Kafka

> *"I'd use Kafka as the event bus between services because it gives us three critical properties: durable message retention (30-day retention means we can replay events for bug fixes), ordered processing within partitions (all events for order_123 processed in sequence), and independent consumer groups (billing, analytics, and notifications each read at their own pace without blocking each other)."*

### Partition Key Design

> *"I'd partition by order_id for the order-events topic. This guarantees all lifecycle events for a single order — created, paid, shipped, delivered — land on the same partition and are processed in order by a single consumer. If I used a null key, a 'shipped' event might be processed before the 'created' event, breaking my state machine."*

### Delivery Guarantees

> *"For billing events, I'd use acks=all with min.insync.replicas=2 and RF=3. This means a billing event is acknowledged only after it's written to at least 2 of 3 replicas. Combined with an idempotent producer, we get exactly-once delivery to Kafka. On the consumer side, I'd use manual offset commits after processing — if the consumer crashes, it re-reads and reprocesses, but our processing is idempotent so duplicates are harmless."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Kafka guarantees message ordering"
**Correction**: Kafka guarantees ordering WITHIN a partition, not across partitions. If you need ordering for a specific entity, all events for that entity must share the same partition key.

### ❌ Mistake 2: Using Kafka as a database
**Correction**: Kafka is a log, not a database. It's great for streaming events but terrible for random key lookups. Use Kafka for transport, databases for storage.

### ❌ Mistake 3: Not mentioning acks configuration
**Correction**: The acks setting is the single most important durability control. Always state it when discussing Kafka in a design.

### ❌ Mistake 4: Ignoring consumer lag
**Correction**: Consumer lag is the #1 health indicator. Always mention monitoring lag and the response strategy (scale consumers, shed low-priority events).

### ❌ Mistake 5: Not mentioning replication factor
**Correction**: Production Kafka should always have RF=3, min.insync.replicas=2. Saying "I'd use Kafka" without mentioning replication shows incomplete understanding.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                  KAFKA DEEP DIVE CHEAT SHEET                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ARCHITECTURE: Brokers + Topics + Partitions + Consumer Groups│
│  PARTITIONS: Unit of parallelism. 1 partition = 1 consumer.  │
│  ORDERING: Guaranteed WITHIN partition, NOT across partitions.│
│  KEY: hash(key) % partitions → deterministic partition.       │
│                                                              │
│  PRODUCER SETTINGS:                                          │
│    acks=all: No data loss (wait for all ISR replicas)         │
│    acks=1: Leader only (fast, small loss risk)                │
│    enable.idempotence=true: Exactly-once producer→broker      │
│    linger.ms=5: Batch for 5ms before sending                 │
│                                                              │
│  REPLICATION:                                                │
│    RF=3 + min.insync.replicas=2 + acks=all                   │
│    = Tolerates 1 broker failure with zero data loss           │
│                                                              │
│  CONSUMER GROUPS:                                            │
│    Each partition → exactly 1 consumer per group              │
│    Multiple groups → independent consumption                  │
│    Max consumers = number of partitions                       │
│                                                              │
│  DELIVERY:                                                   │
│    At-most-once: acks=0 (may lose)                           │
│    At-least-once: acks=all + retries (may duplicate)         │
│    Exactly-once: Idempotent + transactions (highest cost)    │
│                                                              │
│  RETENTION: 7 days default. Compaction keeps latest per key. │
│  THROUGHPUT: ~500K msg/sec per broker, ~5M per 10-broker     │
│  LATENCY: 5-50ms (batch), 2-10ms (low-latency mode)         │
│                                                              │
│  PATTERNS: Event sourcing, CDC, Outbox, DLQ, Fan-out         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 10: Message Queues vs Event Streams** — Kafka vs RabbitMQ vs SQS
- **Topic 22: Delivery Guarantees** — At-least-once, exactly-once semantics
- **Topic 30: Backpressure & Flow Control** — Consumer lag management
- **Topic 33: Reconciliation** — Kafka replay for corrections
- **Topic 36: Distributed Transactions & Sagas** — Transactional outbox with Kafka

---

*This document is part of the System Design Interview Deep Dive series.*
