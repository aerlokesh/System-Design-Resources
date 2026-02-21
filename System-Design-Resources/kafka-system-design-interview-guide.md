# Apache Kafka — System Design Interview Guide

> **Purpose**: Everything you need to know about Kafka for system design interviews. No code — just concepts, architecture, tradeoffs, and talking points you can use across any HLD question.

---

## Table of Contents

1. [What Is Kafka & When to Mention It](#1-what-is-kafka--when-to-mention-it)
2. [Core Architecture](#2-core-architecture)
3. [Key Concepts for Interviews](#3-key-concepts-for-interviews)
4. [Kafka Internals That Matter](#4-kafka-internals-that-matter)
5. [Delivery Guarantees](#5-delivery-guarantees)
6. [Partitioning Strategy](#6-partitioning-strategy)
7. [Consumer Groups & Scaling](#7-consumer-groups--scaling)
8. [Replication & Fault Tolerance](#8-replication--fault-tolerance)
9. [Kafka vs Alternatives](#9-kafka-vs-alternatives)
10. [Common Kafka Patterns in HLD](#10-common-kafka-patterns-in-hld)
11. [Sizing & Back-of-Envelope Numbers](#11-sizing--back-of-envelope-numbers)
12. [Failure Scenarios & How Kafka Handles Them](#12-failure-scenarios--how-kafka-handles-them)
13. [Kafka Anti-Patterns](#13-kafka-anti-patterns)
14. [Interview Talking Points by Problem](#14-interview-talking-points-by-problem)
15. [Quick Reference Card](#15-quick-reference-card)

---

## 1. What Is Kafka & When to Mention It

### One-Liner Definition
> Kafka is a **distributed, durable, high-throughput event streaming platform** that decouples producers from consumers and guarantees ordering within partitions.

### When to Introduce Kafka in an Interview

| Situation | Why Kafka? |
|-----------|-----------|
| Multiple services need the same event | Kafka supports multiple independent consumer groups reading from the same topic |
| Write path needs to be fast, processing can be async | Kafka decouples the write (publish) from the read (consume) |
| Need event replay / audit trail | Kafka retains events for days/weeks — consumers can re-read from any offset |
| High write throughput (>10K events/sec) | Kafka handles millions of events/sec per cluster |
| Need ordering guarantees per entity | Kafka guarantees ordering within a partition — partition by entity ID |
| Event-driven microservices architecture | Kafka is the backbone for pub/sub and event sourcing |
| Need a buffer between a fast producer and slow consumer | Kafka absorbs traffic spikes — consumers process at their own pace |

### When NOT to Use Kafka

| Situation | Use Instead |
|-----------|-------------|
| Simple task queue (send job, one worker processes it) | SQS / RabbitMQ |
| Request-response pattern | REST / gRPC |
| Low-volume, simple pub/sub (< 1K msgs/sec) | Redis Pub/Sub or SNS |
| Need strong transactional guarantees across multiple entities | Database + outbox pattern |
| Real-time bidirectional communication | WebSocket |

### Interview Signal
> Mentioning Kafka shows you understand **async processing, event-driven architecture, and decoupling**. The interviewer is looking for you to explain **why** Kafka and not just "I'll add a queue."

---

## 2. Core Architecture

### The Big Picture

```
┌─────────────────────────────────────────────────────────────┐
│                        KAFKA CLUSTER                         │
│                                                              │
│   ┌──────────┐   ┌──────────┐   ┌──────────┐               │
│   │ Broker 1 │   │ Broker 2 │   │ Broker 3 │  ... Broker N │
│   └──────────┘   └──────────┘   └──────────┘               │
│                                                              │
│   Topic "tweets" ────────────────────────────               │
│     Partition 0: [msg0, msg3, msg6, msg9 ...]  → Broker 1  │
│     Partition 1: [msg1, msg4, msg7, msg10...]  → Broker 2  │
│     Partition 2: [msg2, msg5, msg8, msg11...]  → Broker 3  │
│                                                              │
│   Topic "notifications" ─────────────────────               │
│     Partition 0: [...]                         → Broker 2  │
│     Partition 1: [...]                         → Broker 3  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
        ▲                                        │
        │                                        ▼
   ┌─────────┐                          ┌────────────────┐
   │Producers│                          │Consumer Groups │
   │(Services│                          │  Group A: 3    │
   │ publish │                          │  Group B: 2    │
   │ events) │                          │  Group C: 1    │
   └─────────┘                          └────────────────┘
```

### Components in Interview Language

| Component | What It Is | Interview Phrasing |
|-----------|-----------|-------------------|
| **Broker** | A Kafka server that stores data on disk | "The cluster has N brokers — each stores a subset of partitions" |
| **Topic** | A named category of events (like a table) | "I'd create a topic per event type — `tweets`, `likes`, `notifications`" |
| **Partition** | A topic is split into ordered, append-only logs | "Partitions give us parallelism — more partitions = more throughput" |
| **Producer** | Client that writes events to a topic | "The Tweet Service publishes to the `tweets` topic" |
| **Consumer** | Client that reads events from partitions | "The Fanout Worker consumes from the `tweets` topic" |
| **Consumer Group** | A set of consumers that share the work | "Each consumer group independently reads all events — fanout, search, and analytics are separate groups" |
| **Offset** | The position of a consumer in a partition | "The consumer commits its offset after processing — on restart, it resumes from the last committed offset" |
| **Replication Factor** | Number of copies of each partition | "RF=3 means each partition exists on 3 brokers — tolerates 1 broker failure" |

---

## 3. Key Concepts for Interviews

### 3.1 Topics and Partitions

**Analogy**: A topic is like a database table. Partitions are like shards of that table.

**Key Points**:
- Messages within a partition are **strictly ordered** (append-only log)
- Messages across partitions have **no ordering guarantee**
- Each partition is an independent, ordered, immutable sequence of records
- Partitions are the unit of parallelism — more partitions = more consumers can work in parallel

**Partition Count**: Choose based on throughput needs. A good starting point is 3-10× the number of consumers you expect.

**Example in Interview — Twitter**:
> "The `tweets` topic has 50 partitions. Each partition is an ordered log of tweet events. Partition key = author's user_id, so all tweets by one user go to the same partition — this gives us per-user ordering. With 50 partitions, we can have up to 50 parallel consumers in each consumer group."

### 3.2 Message Retention

| Mode | Description | Use Case |
|------|-------------|----------|
| **Time-based** | Delete after N days (default: 7 days) | Most common — event processing |
| **Size-based** | Delete when partition exceeds N GB | Constrained storage environments |
| **Compacted** | Keep latest value per key forever | State snapshots, changelogs |
| **Infinite** | Never delete | Event sourcing, compliance/audit |

**Interview Insight**: "Kafka retains messages even after they're consumed — unlike SQS where messages are deleted. This enables replay, which is critical for debugging, reprocessing after a bug fix, or bootstrapping a new consumer."

**Example — Retention by System**:
| System | Topic | Retention | Mode | Why |
|--------|-------|-----------|------|-----|
| Twitter | `tweets` | 7 days | Time-based | Enough for replay/debug; tweets stored in Cassandra permanently |
| Ad Aggregator | `ad-events` | 7 days | Time-based | Replay for Flink recovery; raw events archived in S3 |
| Banking | `transactions` | Infinite | Compacted | Event sourcing — full audit trail required by law |
| Chat (WhatsApp) | `messages.created` | 7 days | Time-based | Messages stored in Cassandra; Kafka is just the event bus |
| Notification | `notifications` | 3 days | Time-based | Short processing window; DLQ handles failures |

### 3.3 How Producers Write

1. Producer serializes the message (key + value + headers)
2. If a **key** is provided: `hash(key) % num_partitions` → deterministic partition assignment
3. If no key: round-robin across partitions
4. Message sent to the **partition leader** broker
5. Broker writes to disk (append-only log)
6. Broker replicates to followers (if RF > 1)
7. Broker sends acknowledgment based on `acks` setting

### 3.4 How Consumers Read

1. Consumer joins a **consumer group**
2. Kafka assigns partitions to consumers in the group (1 partition → max 1 consumer in a group)
3. Consumer polls for new messages (pull-based, not push)
4. Consumer processes messages
5. Consumer commits offset (auto or manual)
6. On failure/restart: consumer resumes from last committed offset

**Example in Interview — WhatsApp**:
> "The Delivery Worker consumer group polls from the `messages.created` topic. When it receives a message event, it checks if the recipient is online (Redis lookup). If online → push via WebSocket. If offline → add to undelivered queue + send push notification. After successful processing, it commits the offset. If the worker crashes mid-processing, it restarts from the last committed offset — the message is redelivered (at-least-once), and the delivery logic is idempotent."

---

## 4. Kafka Internals That Matter

### 4.1 The Append-Only Log

Each partition is a file on disk that's only ever appended to. This is why Kafka is fast:
- **Sequential I/O**: Appending to a file is the fastest disk operation. Kafka writes at disk bandwidth speed.
- **Zero-copy**: Kafka uses the OS `sendfile()` syscall to transfer data directly from disk → network socket, bypassing user space.
- **Page cache**: Recent data is served from the OS page cache (memory) — no explicit cache needed.

**Interview Insight**: "Kafka achieves high throughput because it treats the disk as an append-only log. Sequential writes are fast — even HDDs can sustain 600 MB/s sequential writes. Combined with zero-copy transfer and OS page cache, Kafka can serve recent data from memory while still being durable."

### 4.2 Offsets

```
Partition 0:  [0] [1] [2] [3] [4] [5] [6] [7] [8] [9]
                                    ▲              ▲
                              Consumer A      Latest offset
                              (committed=5)   (log-end=9)

Consumer lag = 9 - 5 = 4 messages behind
```

- Each message in a partition has a monotonically increasing **offset** (like an auto-increment ID)
- Offsets are per-partition (not global)
- Consumers track their position via offsets
- **Consumer lag** = latest offset - committed offset = how far behind a consumer is

**Consumer lag is the #1 metric to monitor** — if lag grows, the consumer can't keep up.

**Example in Interview — Ad Aggregator**:
> "We monitor consumer lag per consumer group. If the Flink aggregation group's lag exceeds 500K events, we auto-scale TaskManagers. If lag exceeds 1M, we start sampling impression events at 10% (clicks always processed — they're revenue). The alert threshold is 5M events — at that point, the 30-second freshness SLA is breached."

### 4.3 ISR (In-Sync Replicas)

For a partition with RF=3:
- 1 **leader** handles all reads/writes
- 2 **followers** replicate from the leader
- Followers that are up-to-date form the **ISR** (In-Sync Replica set)
- If a follower falls behind: removed from ISR
- If the leader dies: one of the ISR members becomes the new leader

**`min.insync.replicas`**: The minimum number of replicas that must acknowledge a write before it's considered committed. With `acks=all` + `min.insync.replicas=2` + RF=3: a write succeeds only if 2 of 3 replicas have it. This is the gold standard for durability.

### 4.4 Controller & Coordination (KRaft / ZooKeeper)

- **Controller broker**: One broker is elected as the cluster controller. It manages partition leadership, broker health, and topic creation.
- **ZooKeeper** (legacy): External coordination service. Being replaced by KRaft.
- **KRaft** (modern): Built-in consensus using Raft protocol. No external dependency.

**Interview Insight**: "Historically Kafka depended on ZooKeeper for coordination. Since Kafka 3.3+, KRaft mode removes this dependency — the controller is built into Kafka itself using Raft consensus. This simplifies operations."

**Example in Interview**:
> "When Broker 2 crashes and it was the leader for partitions 3, 7, and 12, the controller detects the failure via heartbeat timeout, selects new leaders from the ISR for each partition, and updates metadata. Producers and consumers redirect within seconds. With RF=3, no data is lost."

---

## 5. Delivery Guarantees

### The Three Guarantees

| Guarantee | How | When to Use |
|-----------|-----|-------------|
| **At-Most-Once** | `acks=0` (producer doesn't wait for ACK) + auto-commit offsets | Metrics, logging where loss is acceptable |
| **At-Least-Once** | `acks=all` + manual offset commit after processing | Most use cases — notifications, fanout, search indexing |
| **Exactly-Once** | Idempotent producer + Kafka transactions + consumer-side dedup | Financial/billing, ad click counting |

### At-Least-Once (Most Common)

```
1. Consumer polls messages
2. Consumer processes message
3. Consumer commits offset
4. If consumer crashes between 2 and 3:
   → Message is reprocessed on restart (duplicate)
   → Application must handle duplicates (idempotency)
```

**This is the default and recommended approach for most systems.** The application handles duplicates via idempotent operations (e.g., upsert instead of insert, or dedup by message ID).

### Exactly-Once Semantics (EOS)

Kafka achieves exactly-once via three mechanisms working together:

1. **Idempotent Producer** (`enable.idempotence=true`): Kafka assigns each producer a PID and sequence number per partition. Duplicate sends with the same sequence are silently ignored.

2. **Kafka Transactions**: Producer can atomically write to multiple partitions in a single transaction. If any write fails, all are rolled back.

3. **Consumer reads committed**: Consumer configured to `read_committed` only sees messages from completed transactions.

**When EOS Matters**: "For ad click counting, each click represents billing revenue. A duplicate click = overbilling = lawsuit. We'd use idempotent producer + Kafka transactions + a dedup layer for belt-and-suspenders exactly-once."

**When EOS Is Overkill**: "For timeline fanout, if a tweet appears twice in a follower's feed, the UI deduplicates by tweet_id. At-least-once with client-side dedup is sufficient."

**Example — Delivery Guarantee by System**:
| System | Guarantee | Why | How |
|--------|-----------|-----|-----|
| Twitter fanout | At-least-once | Duplicate tweet in feed → UI deduplicates by tweet_id | Manual offset commit after Redis write |
| Ad click counting | Exactly-once | Duplicate click = overbilling = lawsuit | Idempotent producer + Flink checkpoints + Bloom filter dedup |
| WhatsApp delivery | At-least-once | Duplicate message → client deduplicates by message_id | Manual offset commit after delivery ACK |
| Notification system | At-least-once | Duplicate notification is annoying but tolerable | Manual commit + DLQ for failures |
| Payment processing | Exactly-once | Double charge = refund + reputation damage | Kafka transactions + idempotency key + DB unique constraint |

---

## 6. Partitioning Strategy

### Why Partition Key Matters

The partition key determines which partition a message goes to. This has two major implications:

1. **Ordering**: Messages with the same key always go to the same partition → guaranteed ordering per key
2. **Locality**: All data for one entity is on one partition → one consumer processes it

### Common Partition Key Choices

| System | Partition Key | Why |
|--------|--------------|-----|
| Twitter | `user_id` (of the author) | All tweets by one user go to one partition → ordered per user |
| WhatsApp | `conversation_id` | All messages in a conversation go to one partition → ordered per conversation |
| Ad Aggregator | `campaign_id` | All events for one campaign go to one partition → aggregation stays on one consumer |
| E-commerce | `order_id` | All events for one order go to one partition → ordered lifecycle per order |
| Notifications | `user_id` (of recipient) | Notifications for one user are ordered → prevents out-of-order delivery |

### The Hot Partition Problem

If one key generates disproportionate traffic (e.g., celebrity's `user_id`), that partition becomes a bottleneck.

**Solutions**:
1. **Key salting**: Append random suffix → `campaign_123_salt_7` → spreads across partitions. Downstream merge step aggregates partial results.
2. **Separate topic**: Route hot keys to a dedicated high-throughput topic.
3. **Compound key**: `user_id + date` → distributes a user's events across partitions by day (loses strict ordering).

**Talking Point**: "I'd partition by campaign_id for ordering, but monitor for hot partitions. If a Super Bowl campaign generates 50% of traffic, I'd salt the key with a random 0-19 suffix to spread it across 20 partitions, then merge the partial aggregates downstream."

**Example — Partition Key by System**:
| System | Topic | Partition Key | # Partitions | Why This Key |
|--------|-------|--------------|-------------|--------------|
| Twitter | `tweets` | author's user_id | 50 | All tweets by one user ordered |
| WhatsApp | `messages.created` | conversation_id | 100 | All messages in a conversation ordered |
| Ad Aggregator | `ad-events` | campaign_id (salted for hot) | 500 | All events for one campaign on one partition |
| Ticket Booking | `reservation-events` | event_id | 20 | All reservations for one event together |
| Notification | `notifications-pending` | recipient user_id | 20 | Notifications for one user ordered |
| Web Crawler | `urls-to-crawl` | domain hash | 50 | One consumer per domain (politeness) |

---

## 7. Consumer Groups & Scaling

### How Consumer Groups Work

```
Topic "tweets" (6 partitions)

Consumer Group "fanout-workers" (3 consumers):
  Consumer A → Partition 0, Partition 1
  Consumer B → Partition 2, Partition 3
  Consumer C → Partition 4, Partition 5

Consumer Group "search-indexer" (2 consumers):
  Consumer D → Partition 0, Partition 1, Partition 2
  Consumer E → Partition 3, Partition 4, Partition 5

Consumer Group "analytics" (1 consumer):
  Consumer F → Partition 0, 1, 2, 3, 4, 5
```

**Key Rules**:
- Each partition is assigned to **exactly one consumer** within a group
- One consumer can handle **multiple partitions**
- **Max parallelism** = number of partitions (adding more consumers than partitions = idle consumers)
- **Different consumer groups** read the same data independently — each group has its own offsets

### Scaling Consumers

| Situation | Action |
|-----------|--------|
| Consumer lag growing | Add more consumers to the group (up to partition count) |
| Need more than N consumers | Increase partition count (careful — this rebalances) |
| One consumer group is slow but others are fine | Scale only that group independently |
| All consumer groups lag | The brokers are the bottleneck — add brokers + partitions |

### Rebalancing

When a consumer joins/leaves a group, Kafka reassigns partitions. During rebalancing:
- All consumers in the group briefly stop processing
- Partitions are redistributed
- Consumers resume from their last committed offsets

**Rebalancing is disruptive.** Strategies to minimize impact:
- **Sticky assignor**: Minimizes partition movement during rebalance
- **Static group membership**: Consumer gets a persistent ID, avoids rebalance on restart
- **Incremental cooperative rebalancing**: Only reassigns the partitions that need to move

**Talking Point**: "With 6 partitions and 3 consumers, each consumer handles 2 partitions. If I add a 4th consumer, Kafka rebalances — two consumers get 2 partitions, two get 1. Max parallelism is capped at 6 (the partition count). If I need more throughput, I'd increase partitions to 12."

**Example — Consumer Groups in Twitter Design**:
```
Topic "tweets" (50 partitions)

Consumer Group "fanout-workers" (10 consumers):
  → Each consumer handles 5 partitions
  → Pushes tweet_ids to followers' Redis timelines
  → Can scale up to 50 consumers

Consumer Group "search-indexer" (5 consumers):
  → Each consumer handles 10 partitions
  → Indexes tweets in Elasticsearch

Consumer Group "notification-workers" (5 consumers):
  → Sends push notifications to mentioned users

Consumer Group "trending-workers" (3 consumers):
  → Updates hashtag counters for trending

Consumer Group "analytics" (2 consumers):
  → Writes to data warehouse (Redshift/BigQuery)

All 5 groups independently read ALL tweets.
Fanout group can be scaled separately from search.
If search indexer is slow, it doesn't affect fanout.
```

---

## 8. Replication & Fault Tolerance

### Replication Architecture

```
Topic "orders", Partition 0, RF=3:

  Broker 1: [Leader]   ← All reads and writes go here
  Broker 2: [Follower] ← Replicates from leader
  Broker 3: [Follower] ← Replicates from leader

ISR = {Broker 1, Broker 2, Broker 3}  (all in sync)
```

### What Happens When a Broker Dies?

**Scenario**: Broker 1 (leader of Partition 0) crashes.

1. Controller detects Broker 1 is dead (via heartbeat timeout)
2. Controller selects a new leader from ISR (e.g., Broker 2)
3. Broker 2 becomes the new leader for Partition 0
4. Producers and consumers redirect to Broker 2
5. **Recovery time**: ~seconds (automatic)
6. **Data loss**: Zero (if `acks=all` + `min.insync.replicas=2`)

### Durability Configuration Matrix

| Setting | Value | Meaning |
|---------|-------|---------|
| `replication.factor` | 3 | Each partition stored on 3 brokers |
| `min.insync.replicas` | 2 | At least 2 replicas must ACK a write |
| `acks` | all | Producer waits for all ISR replicas to ACK |
| `unclean.leader.election` | false | Never elect an out-of-sync replica as leader |

**This combination guarantees zero data loss** — even if one broker dies mid-write. The tradeoff is slightly higher write latency (must wait for 2 replicas).

**Talking Point**: "With RF=3, acks=all, and min.insync.replicas=2, every message is confirmed by at least 2 brokers before the producer gets an ACK. If any single broker dies, no data is lost. The tradeoff is ~5ms extra write latency for the replication round-trip."

**Example — Durability Settings by System**:
| System | acks | RF | min.insync.replicas | Why |
|--------|------|-----|-------------------|-----|
| Ad click counting | all | 3 | 2 | Every click is revenue — zero data loss |
| Twitter tweets | all | 3 | 2 | Tweets are important user content |
| Analytics/logging | 1 | 2 | 1 | Some loss acceptable — lower latency |
| Metrics pipeline | 1 | 2 | 1 | Approximate counts are fine |
| Payment events | all | 3 | 2 | Financial data — zero loss mandatory |

---

## 9. Kafka vs Alternatives

### Kafka vs SQS/RabbitMQ (Message Queues)

| Dimension | Kafka | SQS / RabbitMQ |
|-----------|-------|----------------|
| **Model** | Log-based (retain after consume) | Queue-based (delete after consume) |
| **Replay** | ✅ Consumers can re-read from any offset | ❌ Message gone after consumed |
| **Multiple consumers** | ✅ Consumer groups read independently | ❌ One consumer per message (fan-out needs SNS/exchange) |
| **Ordering** | ✅ Per partition | ✅ Per FIFO queue (SQS) |
| **Throughput** | Millions/sec | Thousands/sec (SQS), tens of thousands (Rabbit) |
| **Latency** | 5-10ms typical | 10-50ms |
| **Complexity** | Higher (cluster management) | Lower (managed service) |
| **Best for** | Event streaming, multiple consumers, replay | Simple task queue, job distribution |

**When to pick Kafka**: "We need the fanout worker, search indexer, notification service, and analytics pipeline to all independently consume the same events. Kafka's consumer group model lets each service read at its own pace without blocking others."

**When to pick SQS**: "This is a simple job queue — a user uploads a video, we need one worker to transcode it. SQS is simpler, fully managed, and handles the one-consumer-per-message pattern perfectly."

### Kafka vs Redis Pub/Sub

| Dimension | Kafka | Redis Pub/Sub |
|-----------|-------|---------------|
| **Durability** | ✅ Persisted to disk | ❌ Fire-and-forget (message lost if no subscriber listening) |
| **Replay** | ✅ | ❌ |
| **Throughput** | Higher at scale | High but limited by single-threaded model |
| **Best for** | Durable event streaming | Ephemeral real-time notifications (typing indicators, presence) |

### Kafka vs Redis Streams

| Dimension | Kafka | Redis Streams |
|-----------|-------|---------------|
| **Persistence** | Disk (durable, huge capacity) | Memory (limited by RAM) |
| **Consumer groups** | ✅ | ✅ (similar model) |
| **Throughput** | Higher at scale (cluster) | Lower (single instance, sharding via Redis Cluster) |
| **Best for** | High-volume, durable streaming | Low-volume streams where Redis is already in the stack |

### Kafka vs Kinesis

| Dimension | Kafka | Kinesis |
|-----------|-------|---------|
| **Management** | Self-managed or MSK | Fully managed |
| **Throughput per unit** | 10-100 MB/s per partition | 1 MB/s per shard |
| **Max retention** | Unlimited | 365 days |
| **Exactly-once** | ✅ Native | ❌ Application-level |
| **Latency** | 5-10ms | 200-500ms |
| **Cost at scale** | Cheaper (>200 MB/s) | Cheaper (<100 MB/s) |
| **Best for** | High throughput, long retention, exactly-once | AWS-native, low ops, moderate scale |

---

## 10. Common Kafka Patterns in HLD

### Pattern 1: Async Processing / Decoupling

```
User posts tweet → Tweet Service writes to DB → Publishes to Kafka "tweets" topic
                                                           │
                          ┌────────────────────────────────┤
                          ▼                ▼               ▼
                    Fanout Worker   Search Indexer   Analytics Pipeline
                    (push to feeds) (index in ES)   (write to warehouse)
```

**When**: Any time you have a write path that triggers multiple side effects. The user doesn't need to wait for all of them.

**Talking Point**: "The tweet write returns to the user in < 100ms. Kafka decouples the side effects — fanout, search indexing, analytics, and notifications all happen asynchronously. If the search indexer is slow, it doesn't affect the user's experience."

### Pattern 2: Event Sourcing

```
All state changes stored as events in Kafka:
  AccountCreated → MoneyDeposited → MoneyWithdrawn → MoneyDeposited

Current state can be rebuilt by replaying all events.
Kafka topic retention: infinite (or compacted).
```

**When**: Financial systems, audit trails, any system that needs to reconstruct state at any point in time.

### Pattern 3: CQRS (Command Query Responsibility Segregation)

```
Write path: Commands → Kafka → Write DB (normalized)
                         │
                         └→ Read DB (denormalized, optimized for queries)
```

**When**: The write model is different from the read model. E.g., write normalized to MySQL, read from denormalized Redis/Elasticsearch.

### Pattern 4: Stream Processing Pipeline

```
Raw events → Kafka → Flink/Spark Streaming → Kafka → Serving store

Example: Ad clicks → Kafka → Flink (aggregate by campaign, minute windows) → Kafka → Redis (real-time) + ClickHouse (historical)
```

**When**: Real-time aggregation, transformation, enrichment, or windowed computation.

### Pattern 5: Change Data Capture (CDC)

```
Database → CDC (Debezium) → Kafka → Downstream consumers

Every INSERT/UPDATE/DELETE in the DB becomes a Kafka event.
Consumers can build materialized views, sync caches, update search indices.
```

**When**: Need to propagate database changes to other services without coupling them to the DB directly.

### Pattern 6: Dead Letter Queue (DLQ)

```
Main topic → Consumer → Process
                 │ (failure)
                 └→ DLQ topic → Retry consumer → Process
                                    │ (still failing)
                                    └→ Alert + manual review
```

**When**: Need graceful handling of messages that fail processing. Don't block the main consumer.

---

## 11. Sizing & Back-of-Envelope Numbers

### Throughput Estimates

| Metric | Typical Value |
|--------|-------------|
| Single partition throughput | 10-100 MB/s (depends on message size, disk, network) |
| Single broker throughput | 200-500 MB/s (10-20 partitions, good hardware) |
| Cluster throughput | Scales linearly with brokers |
| Messages per second per partition | 100K-1M (small messages) |
| End-to-end latency | 5-10ms (producer → consumer) at p50 |

### Partition Count Formula

```
Desired throughput: T MB/s
Per-partition throughput: P MB/s (conservative: 10 MB/s)
Partition count: T / P

Example:
  Need 200 MB/s → 200 / 10 = 20 partitions
  With 20 consumers max parallelism

Also consider: expected consumer count (partitions ≥ consumers)
```

### Storage Estimation

```
Messages per day: M
Average message size: S bytes
Retention period: R days
Replication factor: RF

Storage = M × S × R × RF

Example (Twitter):
  500M tweets/day × 500 bytes × 7 days × 3 RF
  = 500M × 500 × 7 × 3
  = 5.25 TB total across cluster
  ≈ 1.75 TB per broker (3 brokers)
```

### Broker Count Formula

```
Total storage needed / storage per broker
Total throughput needed / throughput per broker
Take the max of both → minimum broker count
Add 1-2 for headroom

Example:
  Storage: 5.25 TB / 2 TB per broker = 3 brokers
  Throughput: 200 MB/s / 300 MB/s per broker = 1 broker
  → Min 3 brokers (storage-bound)
  → Use 4-5 for fault tolerance
```

### Interview Quick Numbers

| Scale | Brokers | Partitions | Throughput |
|-------|---------|-----------|------------|
| Small (startup) | 3 | 10-30 | 10-50 MB/s |
| Medium (scale-up) | 5-10 | 50-100 | 100-500 MB/s |
| Large (FAANG) | 20-100+ | 500+ | 1-10+ GB/s |

---

## 12. Failure Scenarios & How Kafka Handles Them

### Scenario 1: Broker Dies

**Impact**: Partitions led by that broker are temporarily unavailable.  
**Recovery**: Controller elects new leaders from ISR. Consumers redirect. Automatic, takes seconds.  
**Data loss**: None if RF ≥ 2 and acks=all.

### Scenario 2: Consumer Crashes

**Impact**: Partitions assigned to that consumer stop being processed.  
**Recovery**: Rebalance triggers. Other consumers in the group pick up orphaned partitions. Consumer resumes from last committed offset.  
**Data loss**: None. Messages may be reprocessed (at-least-once).

### Scenario 3: Producer Retries Cause Duplicates

**Impact**: Same message written multiple times.  
**Recovery**: Idempotent producer (enable.idempotence=true) deduplicates using PID + sequence number. Or application-level dedup.

### Scenario 4: Slow Consumer (Growing Lag)

**Impact**: Consumer falls behind. If lag exceeds retention period, messages are lost.  
**Recovery**: Scale consumer group (add instances). Or increase retention. Or enable auto-scaling.  
**Monitoring**: Alert on consumer lag metric.

### Scenario 5: Network Partition Between Brokers

**Impact**: Followers can't replicate from leader. ISR shrinks.  
**Recovery**: When network heals, followers catch up. If `min.insync.replicas` not met, writes are rejected (safety).

### Scenario 6: Disk Full on a Broker

**Impact**: Broker stops accepting writes.  
**Recovery**: Log retention/compaction frees space. Or add disk. Or add broker and rebalance partitions.

**Interview Insight**: "Kafka's ISR mechanism is the key to fault tolerance. As long as min.insync.replicas brokers are alive and in sync, no data is lost. If fewer replicas are in sync, Kafka rejects writes rather than risk data loss — it chooses consistency over availability for the write path."

---

## 13. Kafka Anti-Patterns

### ❌ Using Kafka as a Database
**Problem**: Kafka is optimized for sequential reads, not random lookups. Querying by arbitrary fields is not supported.  
**Instead**: Use Kafka as the event log → consume into a database optimized for your query pattern.

### ❌ Very Large Messages (> 1 MB)
**Problem**: Large messages increase latency, memory pressure, and rebalance time.  
**Instead**: Store the payload in S3/object storage, publish only the reference (URL/key) to Kafka.

### ❌ Too Many Topics (Thousands)
**Problem**: Each topic/partition has metadata overhead. Thousands of topics can strain the controller.  
**Instead**: Use a smaller number of topics with message type as a field in the payload. Or use headers.

### ❌ Using Kafka for Request-Response
**Problem**: Kafka is async. Building request-response on top adds complexity (reply topics, correlation IDs).  
**Instead**: Use gRPC or REST for synchronous communication.

### ❌ Unbounded Consumer Groups
**Problem**: Adding consumers beyond the partition count means idle consumers wasting resources.  
**Instead**: Partition count ≥ max expected consumers.

### ❌ Ignoring Consumer Lag
**Problem**: If lag grows and exceeds retention, messages are permanently lost.  
**Instead**: Alert on consumer lag. Auto-scale consumers. Increase retention as a safety net.

---

## 14. Interview Talking Points by Problem

### Twitter / Instagram (Social Feed)

> "When a user tweets, the Tweet Service writes to Cassandra and publishes a `tweet.created` event to Kafka. Five independent consumer groups consume from this topic: the Fanout Worker pushes to followers' Redis timelines, the Search Indexer writes to Elasticsearch, the Notification Worker sends push notifications, the Trending Worker updates hashtag counters, and the Analytics Worker logs to the data warehouse. Each group reads independently at its own pace. Kafka partitioned by user_id — all tweets by one user go to the same partition for ordering."

**Key Kafka details**: Topic `tweets` with 50 partitions, RF=3, 7-day retention. Partition key = author's user_id. 5 consumer groups. ~6K messages/sec sustained, 60K peak.

---

### WhatsApp / Chat Messaging

> "When Alice sends a message to Bob, the Message Service writes to Cassandra and publishes to Kafka topic `messages.created`. The Delivery Worker consumes the event, checks if Bob is online (Redis), and either pushes via WebSocket or queues for offline delivery. A separate Notification Worker consumer group sends FCM/APNS push notifications for offline users. Kafka is partitioned by conversation_id to ensure message ordering within each conversation."

**Key Kafka details**: Topic `messages.created` with 100 partitions, RF=3, 7-day retention. Partition key = conversation_id. ~1.15M messages/sec.

---

### Ad Click Aggregator

> "Ad servers publish click and impression events directly to Kafka topic `ad-events` with 500+ partitions. Apache Flink consumes from this topic and performs multi-window aggregation — minute, hour, and day windows simultaneously. Flink checkpoints every 30 seconds for exactly-once semantics. Partial minute aggregates are flushed to Redis every 10 seconds for real-time dashboards. Completed hour/day aggregates go to ClickHouse. We use idempotent producers and a Bloom filter dedup layer to guarantee no duplicate clicks — because each click is a billing event."

**Key Kafka details**: Topic `ad-events` with 500 partitions, RF=3, 7-day retention. Partition key = campaign_id (with salting for hot campaigns). ~2.3M events/sec. Exactly-once via Flink checkpointing + Kafka transactions.

---

### Ticket Booking

> "When a user reserves seats, the Reservation Service writes to PostgreSQL and publishes a `reservation.created` event to Kafka. Three consumer groups process this: the Notification Worker sends confirmation emails, the Analytics Worker logs for reporting, and the Hold Expiration Worker schedules a timer. If the hold expires without payment, a `hold.expired` event is published, and the Seat Release Worker makes those seats available again."

**Key Kafka details**: Topic `reservation-events`, RF=3, 30-day retention. Partition key = event_id (all reservations for one event stay together). Lower volume (~200 TPS) but critical — acks=all for durability.

---

### Notification System

> "All services (Order, Shipping, Marketing) publish notification requests to Kafka topic `notifications-pending`, partitioned by user_id to preserve per-user ordering. Three consumer groups — Email, SMS, and Push — each process the same events independently. Each consumer checks user preferences before sending. Failed deliveries go to a DLQ topic for retry. Kafka gives us natural fan-out (three channels from one event), ordering (per user), and retry via replay."

**Key Kafka details**: Topic `notifications-pending` with 20 partitions, RF=3. Partition key = recipient user_id. 3 consumer groups (email, SMS, push). DLQ topic for failed deliveries.

---

### Job Scheduler

> "When the Scheduler Leader determines a job is due, it publishes a `job.ready` event to Kafka. Worker instances form a consumer group — each worker gets assigned a subset of partitions and processes jobs from them. If a worker dies, Kafka rebalances its partitions to surviving workers. The job is retried (at-least-once). Workers must be idempotent — processing the same job twice produces the same result."

**Key Kafka details**: Topic `jobs-ready` with partitions ≥ max worker count. Partition key = job_id. Manual offset commit after job completion.

---

### Web Crawler

> "The URL Frontier publishes URLs to crawl to Kafka topic `urls-to-crawl`, partitioned by domain hash to ensure politeness (one consumer per domain). Crawler workers consume URLs, fetch pages, and publish discovered URLs back to the `urls-discovered` topic. A separate dedup consumer filters already-seen URLs using a Bloom filter before re-publishing to `urls-to-crawl`. Kafka's retention enables replaying the crawl if we need to re-process pages."

**Key Kafka details**: Two topics: `urls-to-crawl` and `urls-discovered`. Partition key = domain hash (politeness). Retention = 7 days for replay.

---

## 15. Quick Reference Card

### Kafka in One Diagram

```
Producer → [Topic (N Partitions)] → Consumer Group A (independent)
                                  → Consumer Group B (independent)
                                  → Consumer Group C (independent)

Within each partition: strict ordering, append-only, offset-tracked
Across partitions: no ordering guarantee, parallel processing
```

### Numbers to Memorize

| Metric | Value |
|--------|-------|
| End-to-end latency | 5-10ms |
| Per-partition throughput | 10-100 MB/s |
| Per-broker throughput | 200-500 MB/s |
| Default retention | 7 days |
| Recommended replication factor | 3 |
| Recommended min.insync.replicas | 2 |
| Max consumers per group | = partition count |
| Message size limit (default) | 1 MB |

### Configuration Cheat Sheet

| Goal | Settings |
|------|----------|
| **Maximum durability** | `acks=all`, `RF=3`, `min.insync.replicas=2`, `unclean.leader.election=false` |
| **Maximum throughput** | `acks=1`, compression=lz4, large batch size, high linger.ms |
| **Exactly-once** | `enable.idempotence=true`, Kafka transactions, `read_committed` |
| **Low latency** | `acks=1`, `linger.ms=0`, small batch size |
| **Event sourcing** | `retention.ms=-1` (infinite) or `cleanup.policy=compact` |

### When to Say "Kafka" in an Interview

✅ Say Kafka when:
- "Multiple services need the same event"
- "We need to decouple the write path from processing"
- "We need ordering per entity"
- "We need event replay for debugging or reprocessing"
- "We're building a stream processing pipeline"
- "We need high throughput async processing"

❌ Don't say Kafka when:
- "We just need a simple job queue" → SQS
- "We need request-response" → REST/gRPC
- "We need real-time bidirectional communication" → WebSocket
- "The volume is < 1K msgs/sec and we need simplicity" → Redis Pub/Sub

### The 30-Second Kafka Elevator Pitch

> "I'd introduce Kafka here as the message bus between services. When [event happens], the [source service] publishes to a Kafka topic partitioned by [entity_id] for ordering. Multiple consumer groups — [service A], [service B], [service C] — independently consume from this topic at their own pace. Kafka gives us decoupling (services don't know about each other), durability (messages are persisted for [N] days), replay (we can reprocess if something goes wrong), and ordering (all events for one [entity] go to the same partition). With [N] partitions, we can scale to [N] parallel consumers per group."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Focus**: System design interviews — no code, all concepts  
**Status**: Complete & Interview-Ready ✅
