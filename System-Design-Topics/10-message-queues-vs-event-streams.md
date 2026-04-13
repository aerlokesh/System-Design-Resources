# 🎯 Topic 10: Message Queues vs Event Streams

> **System Design Interview — Deep Dive**
> A comprehensive guide covering message queues (SQS, RabbitMQ) vs event streams (Kafka), when to use each, delivery semantics, consumer groups, replay capability, and how to articulate these choices in interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Message Queues — Task Distribution](#message-queues--task-distribution)
3. [Event Streams — Event Broadcasting](#event-streams--event-broadcasting)
4. [Kafka Deep Dive](#kafka-deep-dive)
5. [SQS Deep Dive](#sqs-deep-dive)
6. [RabbitMQ Deep Dive](#rabbitmq-deep-dive)
7. [Head-to-Head Comparison](#head-to-head-comparison)
8. [When to Use Kafka vs SQS](#when-to-use-kafka-vs-sqs)
9. [Consumer Groups & Partition Assignment](#consumer-groups--partition-assignment)
10. [Ordering Guarantees](#ordering-guarantees)
11. [Replay & Reprocessing](#replay--reprocessing)
12. [Delivery Semantics](#delivery-semantics)
13. [Backpressure & Consumer Lag](#backpressure--consumer-lag)
14. [Dead Letter Queues](#dead-letter-queues)
15. [Event-Driven Architecture Patterns](#event-driven-architecture-patterns)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Queue/Stream Selection by System Design Problem](#queuestream-selection-by-system-design-problem)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Message Queue**: Point-to-point delivery. A message is consumed by **one** consumer and then deleted. Like a task list — each task is done once.

**Event Stream**: Pub/sub with persistence. An event can be consumed by **multiple** independent consumers. Like a log — everyone can read it, and it's retained for replay.

```
Message Queue (SQS/RabbitMQ):
  Producer → [msg1, msg2, msg3] → Consumer A picks msg1 (deleted)
                                → Consumer B picks msg2 (deleted)
                                → Consumer C picks msg3 (deleted)
  Each message processed ONCE by ONE consumer.

Event Stream (Kafka):
  Producer → [evt1, evt2, evt3] → Consumer Group A reads ALL events
                                → Consumer Group B reads ALL events
                                → Consumer Group C reads ALL events
  Each consumer group independently reads ALL events.
  Events are RETAINED (not deleted after consumption).
```

---

## Message Queues — Task Distribution

### Core Model
- One message → one consumer → message deleted.
- Load balancing: N consumers share the work.
- No ordering guarantee across consumers.
- Message is "done" once consumed.

### Use Cases
| Use Case | Why Queue? |
|---|---|
| Send email | One email per message, processed once |
| Resize image | One worker processes each image |
| Generate PDF | Each report generated once |
| Process payment | Each charge executed once |
| Background job | Task scheduling and distribution |

### Key Characteristics
```
Delivery: Each message consumed by exactly one consumer
Ordering: FIFO within a queue (SQS FIFO) or best-effort (standard)
Retention: Messages deleted after consumption
Replay: NOT possible (message is gone)
Scaling: Add more consumers for higher throughput
Cost: Pay per message ($0.40/million for SQS)
```

---

## Event Streams — Event Broadcasting

### Core Model
- One event → multiple consumer groups → event retained.
- Each consumer group independently reads all events.
- Events are immutable and ordered within a partition.
- Events are retained for configurable duration (hours to forever).

### Use Cases
| Use Case | Why Stream? |
|---|---|
| Tweet created → fanout + search + analytics + notifications | Multiple independent consumers |
| Order placed → inventory + shipping + billing + analytics | Event drives multiple workflows |
| Click tracking → real-time dashboard + batch reconciliation | Stream + batch processing |
| User activity → recommendations + fraud detection + analytics | Same event, different purposes |
| Database CDC → populate search index + cache + data lake | Multiple derived views |

### Key Characteristics
```
Delivery: Each consumer group reads all events independently
Ordering: Guaranteed within a partition (by partition key)
Retention: Configurable (hours, days, weeks, forever)
Replay: YES — reset consumer offset to reprocess
Scaling: Add partitions for higher throughput
Cost: Pay for cluster ($500+/month for 3-broker Kafka)
```

---

## Kafka Deep Dive

### Architecture
```
┌──────────────────────────────────────────────┐
│                Kafka Cluster                  │
├──────────────────────────────────────────────┤
│                                              │
│  Topic: "tweet-events"                       │
│  ┌────────────┐ ┌────────────┐ ┌───────────┐│
│  │ Partition 0 │ │ Partition 1 │ │Partition 2││
│  │ [e0,e3,e6] │ │ [e1,e4,e7] │ │[e2,e5,e8]││
│  └────────────┘ └────────────┘ └───────────┘│
│                                              │
│  Consumer Group A (Fanout Service):          │
│    Consumer A1 → Partition 0                 │
│    Consumer A2 → Partition 1                 │
│    Consumer A3 → Partition 2                 │
│                                              │
│  Consumer Group B (Search Indexer):          │
│    Consumer B1 → Partition 0, 1             │
│    Consumer B2 → Partition 2                 │
│                                              │
│  Consumer Group C (Analytics):               │
│    Consumer C1 → Partition 0, 1, 2          │
│                                              │
└──────────────────────────────────────────────┘
```

### Key Concepts

**Topics**: Named categories for events (e.g., "tweet-events", "order-events").

**Partitions**: A topic is split into N partitions for parallelism. Events within a partition are strictly ordered.

**Partition Key**: Determines which partition an event goes to. `hash(key) % num_partitions`. Events with the same key always go to the same partition → ordering guarantee.

**Consumer Groups**: Independent consumers. Each group gets all events. Within a group, partitions are distributed among consumers.

**Offsets**: Each consumer tracks how far it's read in each partition. Can be reset for replay.

**Retention**: Events are retained for configurable time (default 7 days) regardless of consumption.

### Performance
```
Write throughput: ~100K messages/sec per partition
                  ~1M+ messages/sec per cluster (10+ partitions)
Read throughput:  ~100K messages/sec per consumer
Latency:          2-10ms (producer ACK to consumer delivery)
Storage:          Scales to petabytes on disk
```

### Interview Script
> *"I'd use Kafka here because we need multiple independent consumers processing the same event. When a user tweets, the same `TweetCreated` event needs to reach: (1) the fanout service to update timelines, (2) the notification service to alert mentioned users, (3) the search indexer to update Elasticsearch, (4) the analytics pipeline to count impressions, and (5) the content moderation service to check for policy violations. With Kafka consumer groups, each service reads from the same topic independently — no fan-out infrastructure needed."*

---

## SQS Deep Dive

### Architecture
```
┌──────────────────────────────────┐
│         SQS Queue                │
│                                  │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │msg 1│ │msg 2│ │msg 3│ ...   │
│  └─────┘ └─────┘ └─────┘       │
│                                  │
│  Worker 1 picks msg 1 → processes → deletes │
│  Worker 2 picks msg 2 → processes → deletes │
│  Worker 3 picks msg 3 → processes → deletes │
│                                  │
└──────────────────────────────────┘
```

### Standard vs FIFO

| Feature | Standard | FIFO |
|---|---|---|
| **Throughput** | Unlimited | 3,000 msg/sec (with batching) |
| **Ordering** | Best-effort | Strict FIFO |
| **Duplicates** | Possible (at-least-once) | Exactly-once |
| **Cost** | $0.40/million | $0.50/million |
| **Use case** | Email, image processing | Order processing, payments |

### Key Features
- **Managed**: Zero operational overhead (no brokers to manage).
- **Auto-scaling**: Unlimited throughput (standard queues).
- **Visibility timeout**: Message hidden during processing; reappears if not deleted.
- **DLQ**: Built-in dead letter queue for failed messages.
- **Long polling**: Efficient — reduces empty responses.

### Interview Script
> *"For simple task distribution — send this email, resize this image, generate this PDF — SQS is the right tool. Each message is consumed exactly once by one worker, then deleted. No need for Kafka's log retention, consumer group management, partition rebalancing, or Zookeeper coordination. SQS is serverless, auto-scales, and costs $0.40 per million messages. For a notification queue processing 10 million emails per day, that's $4/day. Kafka would cost $500+/month for a 3-broker cluster to do the same job."*

---

## RabbitMQ Deep Dive

### When to Choose RabbitMQ
- **Complex routing**: Direct, topic, fanout, and header-based exchange routing.
- **Priority queues**: Messages processed by priority level.
- **Request-reply pattern**: RPC-style communication.
- **Protocol support**: AMQP, MQTT, STOMP.
- **On-premise**: When you can't use AWS SQS.

### Exchange Types
```
Direct Exchange:   Route by exact routing key match
Topic Exchange:    Route by pattern matching (e.g., "order.*.created")
Fanout Exchange:   Broadcast to all bound queues
Headers Exchange:  Route by message header attributes
```

### RabbitMQ vs Kafka
| Aspect | RabbitMQ | Kafka |
|---|---|---|
| **Model** | Message queue (push) | Event log (pull) |
| **Routing** | Complex (exchanges) | Simple (topics + partitions) |
| **Ordering** | Per-queue FIFO | Per-partition FIFO |
| **Retention** | Messages deleted after ACK | Retained for configured duration |
| **Replay** | No | Yes |
| **Throughput** | ~50K msg/sec | ~1M msg/sec |
| **Use case** | Task queues, RPC | Event streaming, CDC |

---

## Head-to-Head Comparison

| Feature | Kafka | SQS | RabbitMQ |
|---|---|---|---|
| **Model** | Event stream (log) | Message queue | Message queue |
| **Consumers** | Multiple groups (pub/sub) | Single consumer per message | Single or fan-out |
| **Ordering** | Per-partition | FIFO (optional) | Per-queue |
| **Retention** | Configurable (days/weeks) | 14 days max | Until consumed |
| **Replay** | ✅ (reset offset) | ❌ | ❌ |
| **Throughput** | ~1M msg/sec | Unlimited (standard) | ~50K msg/sec |
| **Operational** | Self-managed or managed | Fully managed | Self-managed or managed |
| **Cost (10M msg/day)** | $500+/month (cluster) | $4/day | $200+/month |
| **Best for** | Event streaming, CDC, multi-consumer | Simple task queues | Complex routing, RPC |

---

## When to Use Kafka vs SQS

### Use Kafka When:
1. **Multiple consumers need the same event**: Fanout, search, analytics, notifications all read from the same topic.
2. **Replay is needed**: Reprocess events after a bug fix.
3. **Ordering by entity**: All events for user_123 must be processed in order.
4. **High throughput**: > 100K events/sec.
5. **Event sourcing**: Immutable event log as source of truth.
6. **Stream processing**: Real-time aggregation with Flink/Spark Streaming.

### Use SQS When:
1. **Simple task distribution**: Each task processed once by one worker.
2. **No replay needed**: Process and forget.
3. **Low operational overhead**: Fully managed, no cluster to maintain.
4. **Cost-sensitive**: Pay per message, no minimum infrastructure cost.
5. **Spiky workloads**: Auto-scales from 0 to millions.
6. **AWS-native**: Integrates with Lambda, Step Functions, etc.

### Decision Framework
```
Need multiple consumers for same event?     → Kafka
Need replay/reprocessing?                   → Kafka
Need ordering by entity key?               → Kafka (partition key)
Simple task queue (process once, delete)?   → SQS
Want zero operational overhead?             → SQS
Processing < 50K messages/day?              → SQS (cheaper)
Complex message routing?                    → RabbitMQ
```

---

## Consumer Groups & Partition Assignment

### Kafka Consumer Groups
```
Topic "orders" has 6 partitions: P0, P1, P2, P3, P4, P5

Consumer Group "order-processor" with 3 consumers:
  Consumer 1 → P0, P1
  Consumer 2 → P2, P3
  Consumer 3 → P4, P5

Consumer Group "analytics" with 2 consumers:
  Consumer A → P0, P1, P2
  Consumer B → P3, P4, P5

Key rule: Max consumers in a group = number of partitions.
Adding a 7th consumer to a 6-partition topic → 1 consumer sits idle.
```

### Rebalancing
When consumers join or leave a group, partitions are redistributed:
```
Consumer 3 crashes → Rebalance:
  Consumer 1 → P0, P1, P4
  Consumer 2 → P2, P3, P5
  (Consumer 3's partitions redistributed to survivors)
  
Brief pause during rebalancing (~seconds)
```

---

## Ordering Guarantees

### Kafka: Ordering Within a Partition
```
Partition key = user_id

All events for user_123 → same partition → processed in order:
  event1: OrderCreated(user_123)
  event2: PaymentProcessed(user_123)
  event3: OrderShipped(user_123)

Events for DIFFERENT users → different partitions → NO ordering guarantee:
  user_123 events and user_456 events may be processed in any relative order
  (but each user's events are internally ordered)
```

### Interview Script
> *"For ordering guarantees, I'd use Kafka with user_id as the message key. All events for user_123 hash to the same partition, which means they're consumed in the exact order they were produced. This is critical for maintaining state machines — we can't process 'payment_completed' before 'order_placed'. Kafka guarantees ordering within a partition, and the key-based routing ensures related events share a partition."*

### SQS: FIFO Queues
```
SQS FIFO with MessageGroupId = user_id:
  All messages for user_123 processed in order
  Messages for different users can be processed in parallel
  Throughput: 300 msg/sec per group (3000 with batching)
```

---

## Replay & Reprocessing

### Kafka's Killer Feature
Events in Kafka are **not deleted after consumption**. They're retained for a configurable period. Any consumer can **reset its offset** to reprocess events.

```
Normal operation:
  Consumer offset for partition 0: 1,000,000 (current)

Bug discovered in click deduplication logic (5 days ago):
  1. Fix the bug in consumer code
  2. Reset consumer offset to 5 days ago: offset 500,000
  3. Consumer reprocesses events 500,000 → 1,000,000
  4. Correct counts are now computed

Without replay (SQS): Events are gone after consumption. 
  You'd need to recompute from a separate data store (S3, DB).
```

### Interview Script
> *"Kafka's replay capability is mission-critical for our ad click pipeline. Last month, we discovered a bug in our click deduplication logic that had been undercounting clicks by 0.3% for 5 days. Because Kafka retains events for 30 days, we fixed the bug, reset the consumer group offset to 5 days ago, and replayed 12 billion events through the corrected pipeline. With SQS, those events would have been deleted after consumption — the data would have been gone."*

---

## Delivery Semantics

### At-Most-Once
```
Send message → don't wait for ACK → might be lost
Fast but unreliable. Use for: metrics, logging (best-effort)
```

### At-Least-Once (Most Common)
```
Send message → wait for ACK → retry if no ACK → might send duplicate
Reliable but may duplicate. Use for: most operations (with idempotent consumers)
```

### Exactly-Once (Expensive)
```
Kafka transactions + Flink checkpointing + dedup layer
No duplicates, no losses. Use for: billing, ad click counting, financial
```

### Practical Approach
> *"True exactly-once delivery is expensive — it requires Kafka transactions, Flink checkpointing, and a dedup layer. For most operations, at-least-once with idempotent handlers is sufficient and dramatically simpler. If the notification service sends 'Alice liked your photo' twice, the user sees a duplicate notification — mildly annoying but not harmful. We save the engineering cost of exactly-once infrastructure for the operations where duplicates cause real damage: billing, ad click counting, and payment processing."*

---

## Backpressure & Consumer Lag

### Consumer Lag
The difference between the latest produced offset and the consumer's current offset:
```
Latest offset: 1,000,000
Consumer offset: 950,000
Lag: 50,000 events

Lag increasing → consumer is slower than producer → data freshness degrading
Lag stable → consumer keeping up
Lag decreasing → consumer catching up
```

### Mitigation Strategies
1. **Auto-scale consumers**: More consumers = more parallel processing.
2. **Add partitions**: More partitions = more parallelism (but partition increase is irreversible in Kafka).
3. **Batch processing**: Process messages in batches of 100-1000.
4. **Sampling**: For non-critical data, process 10% and extrapolate.
5. **Priority shedding**: Process critical events fully, sample non-critical ones.

---

## Dead Letter Queues

### Pattern
```
Main Queue/Topic → Consumer → Success → Commit offset
                            → Fail → Retry (3 times)
                                   → Fail → Send to DLQ topic/queue

DLQ → Alert → Investigate → Fix bug → Replay from DLQ
```

### Kafka DLQ Pattern
```java
try {
    processEvent(event);
    consumer.commitOffset(event.offset());
} catch (RetryableException e) {
    retryQueue.send(event);  // Back to main topic for retry
} catch (PermanentException e) {
    dlqProducer.send("tweet-events-dlq", event);  // To DLQ
    consumer.commitOffset(event.offset());  // Don't block main processing
}
```

---

## Event-Driven Architecture Patterns

### Pattern 1: Event Notification
```
OrderService publishes: OrderCreated { order_id: 123 }
InventoryService consumes → decrements stock
ShippingService consumes → creates shipment
BillingService consumes → generates invoice
```

### Pattern 2: Event-Carried State Transfer
```
UserService publishes: UserUpdated { user_id: 123, name: "Alice", avatar: "..." }
TimelineService consumes → updates cached author info in timeline entries
SearchService consumes → updates search index
```

### Pattern 3: Event Sourcing
```
All state changes stored as events:
  AccountCreated { balance: 0 }
  Deposited { amount: 100 }
  Withdrawn { amount: 30 }
  Deposited { amount: 50 }

Current state = replay all events: balance = 0 + 100 - 30 + 50 = 120
```

### Pattern 4: CQRS (Command Query Responsibility Segregation)
```
Commands (writes) → PostgreSQL (normalized, ACID)
                  → CDC → Kafka → Denormalized read views

Queries (reads) → DynamoDB (denormalized, fast)
                → Elasticsearch (search)
                → Redis (cache)
```

---

## Interview Talking Points & Scripts

### Kafka for Multi-Consumer
> *"Kafka is ideal when multiple services need the same event. One `TweetCreated` event feeds fanout, search, analytics, notifications, and moderation — each as an independent consumer group reading from the same topic."*

### SQS for Simple Tasks
> *"For simple task distribution — send this email, resize this image — SQS is the right tool. Fully managed, auto-scales, $0.40 per million messages. Kafka would cost $500+/month for a 3-broker cluster to do the same job."*

### Replay Justification
> *"Kafka's 30-day retention means we can fix a bug and replay 5 days of events to correct the data. With SQS, those events are gone after consumption."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using Kafka for simple task queues
**Problem**: Over-engineering. SQS is 10x simpler and cheaper for process-once-delete tasks.

### ❌ Mistake 2: Using SQS when multiple consumers need the same event
**Problem**: SQS deletes messages after consumption. You'd need SNS fan-out → multiple SQS queues, adding complexity.

### ❌ Mistake 3: Not mentioning partition key for ordering
**Problem**: Saying "Kafka guarantees ordering" without specifying "within a partition, using user_id as the partition key."

### ❌ Mistake 4: Ignoring consumer lag
**Problem**: Not discussing what happens when consumers fall behind — must mention auto-scaling, sampling, or priority shedding.

### ❌ Mistake 5: Not specifying retention period
**Problem**: Kafka retention is configurable. Mention "30-day retention for replay capability" to show awareness.

---

## Queue/Stream Selection by System Design Problem

| Problem | Technology | Why |
|---|---|---|
| **Tweet fanout** | Kafka | Multiple consumers (fanout, search, analytics) |
| **Email sending** | SQS | Simple task, process once |
| **Ad click counting** | Kafka | Replay for reconciliation, exactly-once billing |
| **Image processing** | SQS | Task distribution, auto-scale workers |
| **Order processing** | Kafka | Multi-step workflow, ordering by order_id |
| **Chat message routing** | Kafka | Ordering by conversation_id |
| **Video transcoding** | SQS | Heavy tasks, long processing time |
| **CDC (Change Data Capture)** | Kafka | Event log, multiple derived views |
| **Metrics collection** | Kafka | High throughput, stream processing |
| **Background jobs** | SQS | Simple scheduling, visibility timeout |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│        MESSAGE QUEUES vs EVENT STREAMS CHEAT SHEET           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  MESSAGE QUEUE (SQS/RabbitMQ):                               │
│    Model: One message → one consumer → deleted               │
│    Use: Task distribution (email, image resize, PDF)         │
│    Replay: ❌ (messages deleted after consumption)            │
│    Cost: $0.40/million (SQS — cheap, managed)                │
│                                                              │
│  EVENT STREAM (Kafka):                                       │
│    Model: One event → multiple consumer groups → retained    │
│    Use: Event broadcasting (fanout + search + analytics)     │
│    Replay: ✅ (reset offset, reprocess)                      │
│    Cost: $500+/month (cluster — more expensive)              │
│                                                              │
│  DECISION:                                                   │
│    Multiple consumers? → Kafka                               │
│    Need replay? → Kafka                                      │
│    Need ordering by key? → Kafka (partition key)             │
│    Simple task queue? → SQS                                  │
│    Zero ops overhead? → SQS                                  │
│    Complex routing? → RabbitMQ                               │
│                                                              │
│  ORDERING: Kafka guarantees order WITHIN a partition         │
│    Use partition key (user_id, order_id) for entity ordering │
│                                                              │
│  DELIVERY: At-least-once + idempotent consumers (DEFAULT)    │
│    Exactly-once: Only for billing/payments (expensive)       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 8: Sync vs Async** — Queues/streams enable async processing
- **Topic 9: Communication Protocols** — Kafka as async communication
- **Topic 22: Delivery Guarantees** — At-least-once, exactly-once semantics
- **Topic 23: Batch vs Stream Processing** — Stream processing with Kafka + Flink
- **Topic 30: Backpressure** — Managing consumer lag

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
