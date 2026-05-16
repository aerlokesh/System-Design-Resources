# 📨 SQS, SNS & Kinesis Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how AWS messaging services are used in production. Every queue configuration, topic subscription, stream shard design, and integration pattern is explained with the **WHY** behind it. Uses **AWS-native services** throughout.

---

## 📋 Table of Contents

1. [SQS vs SNS vs Kinesis — When to Use What](#1-sqs-vs-sns-vs-kinesis--when-to-use-what)
2. [Amazon SQS Deep Dive](#2-amazon-sqs-deep-dive)
3. [Amazon SNS Deep Dive](#3-amazon-sns-deep-dive)
4. [Amazon Kinesis Deep Dive](#4-amazon-kinesis-deep-dive)
5. [Pattern: Fan-Out (SNS → SQS)](#5-pattern-fan-out-sns--sqs)
6. [Real-World: E-Commerce Order Pipeline](#6-real-world-e-commerce-order-pipeline)
7. [Real-World: Notification System](#7-real-world-notification-system)
8. [Real-World: Real-Time Analytics Pipeline](#8-real-world-real-time-analytics-pipeline)
9. [Real-World: Image/Video Processing Pipeline](#9-real-world-imagevideo-processing-pipeline)
10. [Real-World: Microservice Decoupling](#10-real-world-microservice-decoupling)
11. [Real-World: IoT Data Ingestion](#11-real-world-iot-data-ingestion)
12. [Real-World: Payment Processing](#12-real-world-payment-processing)
13. [Real-World: Email/SMS Delivery](#13-real-world-emailsms-delivery)
14. [Real-World: Event Sourcing & CQRS](#14-real-world-event-sourcing--cqrs)
15. [Real-World: Log Aggregation Pipeline](#15-real-world-log-aggregation-pipeline)
16. [EventBridge — The Modern Alternative](#16-eventbridge--the-modern-alternative)
17. [Error Handling & Dead Letter Queues](#17-error-handling--dead-letter-queues)
18. [🏆 Cheat Sheet](#-cheat-sheet-sqs-vs-sns-vs-kinesis-vs-eventbridge)
19. [🎯 Interview Summary](#-interview-summary)

---

## 1. SQS vs SNS vs Kinesis — When to Use What

```
┌────────────────────────────────────────────────────────────────────────┐
│         SQS vs SNS vs KINESIS — DECISION MATRIX                       │
├──────────────────┬──────────────────┬──────────────────┬──────────────┤
│                  │ SQS              │ SNS              │ Kinesis      │
├──────────────────┼──────────────────┼──────────────────┼──────────────┤
│ Pattern          │ Queue (1:1)      │ Pub/Sub (1:N)    │ Stream (N:N) │
│ Delivery         │ Pull (consumer   │ Push (to subs)   │ Pull (consmr │
│                  │ polls)           │                  │ polls shards)│
│ Consumers        │ One consumer     │ Many subscribers │ Many consumer│
│                  │ group            │ (fan-out)        │ groups       │
│ Message replay   │ ❌ No            │ ❌ No            │ ✅ Yes       │
│ Ordering         │ FIFO queues only │ FIFO topics only │ Per-shard    │
│ Retention        │ 4 days (max 14)  │ None (instant)   │ 1-365 days  │
│ Throughput       │ Unlimited (std)  │ Unlimited        │ Per-shard    │
│                  │ 300 TPS (FIFO)   │                  │ (1MB/s write)│
│ Pricing          │ Per request      │ Per publish +    │ Per shard/hr │
│                  │ ($0.40/M)        │ per delivery     │ ($0.015/hr)  │
│ Serverless       │ ✅ Fully         │ ✅ Fully         │ ✅ On-demand │
│ Lambda trigger   │ ✅               │ ✅               │ ✅           │
├──────────────────┼──────────────────┼──────────────────┼──────────────┤
│ USE FOR:         │ • Async tasks    │ • Fan-out to     │ • Event      │
│                  │ • Decouple       │   multiple       │   streaming  │
│                  │   services       │   services       │ • Real-time  │
│                  │ • Buffer spikes  │ • Notifications  │   analytics  │
│                  │ • Retry w/ DLQ   │ • Alerts         │ • Replay     │
│                  │ • Job queues     │ • Email/SMS/Push │ • Ordering   │
│                  │                  │ • Webhooks       │ • Aggregation│
└──────────────────┴──────────────────┴──────────────────┴──────────────┘
```

### One-Line Decision

```
"I need to decouple service A from B"         → SQS
"I need to notify multiple services at once"   → SNS (or SNS → SQS fan-out)
"I need to stream events for real-time analytics" → Kinesis
"I need to route events by content/rules"      → EventBridge
```

---

## 2. Amazon SQS Deep Dive

### Standard Queue vs FIFO Queue

| Feature | Standard Queue | FIFO Queue |
|---------|---------------|------------|
| Throughput | **Unlimited** | 300 msg/s (3000 with batching) |
| Ordering | **Best-effort** (may reorder) | **Strict FIFO** (per message group) |
| Delivery | **At-least-once** (may duplicate) | **Exactly-once** |
| Deduplication | ❌ | ✅ (5-min window) |
| Name | `my-queue` | `my-queue.fifo` (must end in .fifo) |
| Use case | High throughput, order doesn't matter | Financial txns, ordering matters |

### SQS Configuration (Production)

```python
import boto3

sqs = boto3.client('sqs')

# Create queue with production settings
queue = sqs.create_queue(
    QueueName='order-processing',
    Attributes={
        'VisibilityTimeout': '300',        # 5 min — time to process before retry
        'MessageRetentionPeriod': '1209600', # 14 days max retention
        'ReceiveMessageWaitTimeSeconds': '20', # Long polling (reduces empty receives!)
        'RedrivePolicy': json.dumps({
            'deadLetterTargetArn': dlq_arn,   # DLQ for failed messages
            'maxReceiveCount': '3'             # Move to DLQ after 3 failures
        })
    }
)

# Send message
sqs.send_message(
    QueueUrl=queue_url,
    MessageBody=json.dumps({
        'orderId': 'ord_123',
        'action': 'process_payment',
        'amount': 4999
    }),
    MessageAttributes={
        'Priority': {'DataType': 'String', 'StringValue': 'high'}
    }
)

# Receive + process + delete
response = sqs.receive_message(
    QueueUrl=queue_url,
    MaxNumberOfMessages=10,        # Batch up to 10
    WaitTimeSeconds=20,            # Long polling
    VisibilityTimeout=300
)
for msg in response.get('Messages', []):
    try:
        process_order(json.loads(msg['Body']))
        sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=msg['ReceiptHandle'])
    except Exception:
        pass  # Visibility timeout expires → message becomes visible again → retry
```

### SQS + Lambda (Event Source Mapping)

```
# SQS triggers Lambda automatically — no polling code needed!

# Configuration:
# - Batch size: 10 (Lambda receives up to 10 messages at once)
# - Batch window: 5 seconds (wait to fill batch)
# - Max concurrency: 50 (limit concurrent Lambda invocations)
# - On failure: DLQ or Lambda destination
# - Bisect on error: Split batch and retry (find the bad message)

# Lambda handler:
def handler(event, context):
    for record in event['Records']:
        body = json.loads(record['body'])
        process_order(body)
    # If function returns successfully → SQS deletes all messages
    # If function throws → SQS retries (visibility timeout reset)
```

### Key SQS Patterns

```
# 1. Visibility Timeout = Processing Time
# Set to: max_processing_time + buffer
# Too short: message reprocessed while still being worked on (duplicates!)
# Too long: failed messages take too long to retry

# 2. Long Polling (ALWAYS use!)
# WaitTimeSeconds=20 → reduces empty receives by 90%
# Without: $0.40/M requests × constant polling = expensive
# With: Only charged when messages available

# 3. Dead Letter Queue (DLQ)
# Message fails N times → moved to DLQ → investigate manually
# ALWAYS configure DLQ — otherwise failed messages retry forever

# 4. Message Deduplication (Standard Queue)
# Standard queues may deliver duplicates → make consumers IDEMPOTENT
# Use idempotency key: check if orderId already processed before processing
```

---

## 3. Amazon SNS Deep Dive

### SNS Concepts

```
┌─────────────────────────────────────────────────────────────┐
│                    SNS ARCHITECTURE                          │
│                                                              │
│  Publisher ──→ SNS Topic ──→ Subscriber 1 (SQS queue)       │
│                           ──→ Subscriber 2 (Lambda)          │
│                           ──→ Subscriber 3 (HTTP endpoint)   │
│                           ──→ Subscriber 4 (Email)           │
│                           ──→ Subscriber 5 (SMS)             │
│                           ──→ Subscriber 6 (Kinesis Firehose)│
│                                                              │
│  One publish → delivered to ALL subscribers simultaneously   │
└─────────────────────────────────────────────────────────────┘
```

### SNS Message Filtering

```python
# Without filtering: Every subscriber gets EVERY message
# With filtering: Subscribers only get messages they care about

# Publisher sends:
sns.publish(
    TopicArn=topic_arn,
    Message=json.dumps({'orderId': 'ord_123', 'total': 4999}),
    MessageAttributes={
        'event_type': {'DataType': 'String', 'StringValue': 'order_placed'},
        'region': {'DataType': 'String', 'StringValue': 'us-east'},
        'amount': {'DataType': 'Number', 'StringValue': '4999'}
    }
)

# Subscriber A: Only wants order_placed events
# Filter policy: {"event_type": ["order_placed"]}

# Subscriber B: Only wants high-value orders (> $100)
# Filter policy: {"amount": [{"numeric": [">=", 10000]}]}

# Subscriber C: Only wants us-east region
# Filter policy: {"region": ["us-east"]}

# → Each subscriber ONLY receives matching messages
# → Reduces processing cost and complexity
```

### SNS + SQS Fan-Out (Most Common Pattern)

```
# Problem: Order placed → need to:
# 1. Process payment
# 2. Update inventory
# 3. Send confirmation email
# 4. Update analytics
# 5. Notify warehouse

# Solution: SNS → multiple SQS queues

                    ┌───→ SQS: payment-queue ───→ Lambda: ProcessPayment
                    │
  Order Service ──→ SNS: order-events ──→ SQS: inventory-queue ──→ Lambda: UpdateInventory
                    │
                    ├───→ SQS: email-queue ───→ Lambda: SendEmail
                    │
                    ├───→ SQS: analytics-queue ───→ Lambda: UpdateAnalytics
                    │
                    └───→ SQS: warehouse-queue ───→ Lambda: NotifyWarehouse

# WHY SNS → SQS (not SNS → Lambda directly)?
# 1. SQS provides retry with DLQ (SNS → Lambda has limited retries)
# 2. SQS buffers during Lambda throttling (SNS drops if Lambda can't keep up)
# 3. SQS allows batch processing (10 messages at once)
# 4. Each queue can have different visibility timeout for its workload
```

---

## 4. Amazon Kinesis Deep Dive

### Kinesis Components

```
┌─────────────────────────────────────────────────────────────┐
│                    KINESIS ECOSYSTEM                         │
│                                                              │
│  Kinesis Data Streams → Real-time event streaming            │
│  ├── Producers write records to shards                       │
│  ├── Consumers read from shards (multiple consumer groups)   │
│  └── Records retained for 1-365 days (replay!)               │
│                                                              │
│  Kinesis Data Firehose → Delivery to destinations            │
│  ├── Auto-batches and delivers to S3, Redshift, OpenSearch  │
│  ├── Can transform with Lambda inline                        │
│  └── Near real-time (60-sec buffer minimum)                  │
│                                                              │
│  Kinesis Data Analytics → SQL/Flink on streams               │
│  ├── Real-time aggregation, windowing                        │
│  └── Managed Apache Flink                                    │
└─────────────────────────────────────────────────────────────┘
```

### Kinesis Data Streams — When & Why

```python
# Create stream
kinesis = boto3.client('kinesis')
kinesis.create_stream(
    StreamName='clickstream-events',
    StreamModeDetails={'StreamMode': 'ON_DEMAND'}  # Auto-scales shards
    # OR: ShardCount=10 for PROVISIONED mode
)

# Produce records
kinesis.put_record(
    StreamName='clickstream-events',
    Data=json.dumps({
        'userId': 'user_123',
        'event': 'page_view',
        'page': '/products/shoe-abc',
        'timestamp': '2024-01-15T14:30:00Z'
    }),
    PartitionKey='user_123'  # All events for same user → same shard → ordered!
)

# Consume with Lambda (Event Source Mapping)
# Lambda polls shards automatically
def handler(event, context):
    for record in event['Records']:
        data = json.loads(base64.b64decode(record['kinesis']['data']))
        process_clickstream(data)
```

### Kinesis vs SQS — Key Differences

| Feature | SQS | Kinesis |
|---------|-----|---------|
| **Replay** | ❌ Message deleted after processing | ✅ Re-read from any point in time |
| **Ordering** | No (Standard) / Yes (FIFO per group) | ✅ Yes (within shard) |
| **Multiple consumers** | ❌ One consumer group | ✅ Multiple consumer groups |
| **Throughput per shard** | Unlimited (Standard) | 1MB/s write, 2MB/s read per shard |
| **Retention** | 4 days (max 14) | 1 day (max 365) |
| **Cost model** | Per request | Per shard-hour + per PUT |
| **Best for** | Task queues, decoupling | Event streaming, analytics, replay |

### Kinesis Partition Key Strategy

```
# Partition key determines which shard gets the record
# CRITICAL: Choose carefully to avoid hot shards!

# ❌ BAD: Single partition key for everything
PartitionKey = "all"  → Everything on one shard → 1MB/s limit!

# ❌ BAD: Too few unique keys
PartitionKey = "us-east"  → Most traffic → hot shard

# ✅ GOOD: High cardinality keys
PartitionKey = user_id     → Distributes evenly, all events for same user ordered
PartitionKey = device_id   → IoT: each device's events stay ordered
PartitionKey = order_id    → All events for same order stay ordered

# ✅ GOOD: Random key (when ordering doesn't matter)
PartitionKey = uuid()      → Maximum distribution across shards
```

---

## 5. Pattern: Fan-Out (SNS → SQS)

```
# THE most common AWS messaging pattern

# Step 1: Create SNS topic
topic_arn = sns.create_topic(Name='order-events')['TopicArn']

# Step 2: Create SQS queues for each consumer
payment_queue = sqs.create_queue(QueueName='payment-processing')
inventory_queue = sqs.create_queue(QueueName='inventory-update')
email_queue = sqs.create_queue(QueueName='email-notification')

# Step 3: Subscribe queues to topic
sns.subscribe(TopicArn=topic_arn, Protocol='sqs', Endpoint=payment_queue_arn)
sns.subscribe(TopicArn=topic_arn, Protocol='sqs', Endpoint=inventory_queue_arn)
sns.subscribe(TopicArn=topic_arn, Protocol='sqs', Endpoint=email_queue_arn,
    Attributes={'FilterPolicy': json.dumps({'event_type': ['order_placed']})})

# Step 4: Publish once → delivered to all queues
sns.publish(TopicArn=topic_arn, Message=json.dumps(order_data),
    MessageAttributes={'event_type': {'DataType': 'String', 'StringValue': 'order_placed'}})

# Result: Each service processes independently, at its own pace
# Payment service slow? → messages queue up in payment-queue
# Email service down? → messages queue in email-queue → DLQ if persistent failure
# No service affects any other service → full decoupling!
```

---

## 6. Real-World: E-Commerce Order Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│           E-COMMERCE ORDER PIPELINE                          │
│                                                              │
│  API Gateway → Lambda: CreateOrder                           │
│       │                                                      │
│       ▼                                                      │
│  DynamoDB: Orders (write order, status=PENDING)              │
│       │                                                      │
│       ▼                                                      │
│  SNS: order-events (publish OrderCreated)                    │
│       │                                                      │
│       ├──→ SQS: payment-queue ──→ Lambda: ProcessPayment     │
│       │         DLQ: payment-dlq       │                     │
│       │                                ▼                     │
│       │                         SNS: payment-events          │
│       │                                │                     │
│       │         ┌──────────────────────┤                     │
│       │         ▼                      ▼                     │
│       │    SQS: inventory-queue   SQS: shipping-queue        │
│       │    → Lambda: Reserve      → Lambda: CreateLabel      │
│       │                                                      │
│       ├──→ SQS: email-queue ──→ Lambda → SES (send email)   │
│       │                                                      │
│       └──→ Kinesis Firehose ──→ S3 (analytics data lake)    │
│                                                              │
└─────────────────────────────────────────────────────────────┘

# Key decisions:
# SNS for fan-out: One order event → 4 downstream services
# SQS for each: Independent processing, buffering, retry, DLQ
# Kinesis Firehose for analytics: Auto-batch to S3 every 60s
# DLQ on payment queue: Failed payments need manual investigation
```

---

## 7. Real-World: Notification System

```
# User triggers: like, comment, follow, mention
# Need to deliver via: push, email, SMS, in-app

SNS Topic: user-notifications
├── Filter: {"channel": ["push"]}   → SQS → Lambda → APNs/FCM
├── Filter: {"channel": ["email"]}  → SQS → Lambda → SES
├── Filter: {"channel": ["sms"]}    → SQS → Lambda → SNS SMS
└── Filter: {"channel": ["in_app"]} → SQS → Lambda → DynamoDB + WebSocket

# Publisher:
sns.publish(
    TopicArn=topic_arn,
    Message=json.dumps({
        'userId': 'user_123',
        'type': 'new_follower',
        'title': 'Alice started following you',
        'body': 'You now have 1,234 followers'
    }),
    MessageAttributes={
        'channel': {'DataType': 'String', 'StringValue': 'push'},
        'priority': {'DataType': 'String', 'StringValue': 'normal'}
    }
)

# Each channel processes at its own rate
# Push: instant (< 1 sec)
# Email: batched every 5 minutes (digest)
# SMS: rate-limited (1/sec per number)
# In-app: stored in DynamoDB, pushed via WebSocket if online
```

---

## 8. Real-World: Real-Time Analytics Pipeline

```
# Track every click, page view, purchase on an e-commerce site

Clickstream → API Gateway → Kinesis Data Streams (high throughput)
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            Lambda (enrich)   Kinesis Analytics   Kinesis Firehose
            → DynamoDB        (Flink SQL)         → S3 (data lake)
            (real-time        → Real-time          → Athena (ad-hoc)
             dashboard)        aggregation          → Redshift (BI)
                              → Anomaly detect

# Why Kinesis (not SQS)?
# 1. Replay: Re-process last 24h if analytics bug found
# 2. Multiple consumers: Dashboard + Analytics + S3 all read same stream
# 3. Ordering: Events for same user arrive in order (partition by userId)
# 4. High throughput: 1MB/s per shard, scale to hundreds of shards

# Kinesis Analytics SQL example:
# Real-time: "Page views per product per minute"
CREATE OR REPLACE STREAM output_stream (
    product_id VARCHAR(50),
    view_count INTEGER,
    window_end TIMESTAMP
);

INSERT INTO output_stream
SELECT product_id, COUNT(*) as view_count, STEP(event_time BY INTERVAL '1' MINUTE)
FROM clickstream_input
WHERE event_type = 'page_view'
GROUP BY product_id, STEP(event_time BY INTERVAL '1' MINUTE);
```

---

## 9. Real-World: Image/Video Processing Pipeline

```
# User uploads image → resize to 6 sizes + generate thumbnail

S3 Event → SNS: media-uploaded
              │
              ├──→ SQS: thumbnail-queue → Lambda: GenerateThumbnail (128x128)
              ├──→ SQS: medium-queue → Lambda: ResizeMedium (640x480)
              ├──→ SQS: large-queue → Lambda: ResizeLarge (1920x1080)
              ├──→ SQS: webp-queue → Lambda: ConvertToWebP
              ├──→ SQS: moderation-queue → Lambda: ContentModeration (Rekognition)
              └──→ SQS: metadata-queue → Lambda: ExtractMetadata (EXIF)

# Each size processed independently in parallel
# If thumbnail fails → only thumbnail retries (others unaffected)
# Moderation can take 30s → separate queue with longer visibility timeout
# DLQ on each queue → no image processing silently fails
```

---

## 10. Real-World: Microservice Decoupling

```
# Problem: Order Service directly calls Inventory Service
# If Inventory is down → Order fails → customer can't checkout!

# ❌ BEFORE: Tight coupling
OrderService → HTTP → InventoryService
                      (if down → 500 error → lost sale!)

# ✅ AFTER: Decoupled with SQS
OrderService → SQS: inventory-updates → InventoryService
               (if Inventory down → messages queue up → processed when back)

# Benefits:
# 1. Inventory service down 5 minutes → no orders lost (queued)
# 2. Inventory service slow → messages buffer (no backpressure on orders)
# 3. Inventory service deploys → zero impact on order service
# 4. Scale independently: 10 order lambdas, 3 inventory lambdas
```

---

## 11. Real-World: IoT Data Ingestion

```
# 100,000 IoT devices sending temperature readings every 5 seconds
# = 20,000 messages/second

IoT Devices → IoT Core MQTT → Kinesis Data Streams (20 shards @ 1MB/s each)
                                        │
                        ┌───────────────┼───────────────┐
                        ▼               ▼               ▼
                  Lambda (alert    Kinesis Firehose  Lambda (aggregate)
                  if temp > 100)   → S3 (raw data)  → DynamoDB (latest
                  → SNS → SMS                         reading per device)

# Why Kinesis (not SQS)?
# 1. 20K msg/sec sustained → Kinesis handles with 20 shards
# 2. Order per device: PartitionKey = deviceId → all readings for same device ordered
# 3. Multiple consumers: Alerts + Storage + Dashboard all read same stream
# 4. Replay: If alerting Lambda had bug, re-process last 24h
```

---

## 12. Real-World: Payment Processing

```
# Payment requires EXACTLY-ONCE processing (no double charges!)

API Gateway → Lambda: InitiatePayment
                  │
                  ▼
SQS FIFO Queue: payment-processing.fifo
    MessageGroupId = orderId           ← Same order = same group = ordered
    MessageDeduplicationId = paymentId ← Prevents duplicate charges!
                  │
                  ▼
Lambda: ProcessPayment
    1. Call Stripe API
    2. Update DynamoDB (payment status)
    3. Publish to SNS: payment-completed
                  │
           ┌──────┴──────┐
           ▼              ▼
    SQS: fulfillment  SQS: email
    → Ship product    → Send receipt

# Why FIFO Queue for payments?
# 1. EXACTLY-ONCE delivery → no duplicate charges
# 2. MessageGroupId = orderId → payments for same order processed sequentially
# 3. Deduplication: Retry sends same paymentId → SQS rejects duplicate
# 4. DLQ: Failed payments → investigate, never silently lost
```

---

## 13. Real-World: Email/SMS Delivery

```
# Send transactional emails (order confirm, password reset)

SNS Topic: email-requests
    │
    └──→ SQS: email-queue (with rate limiting)
              VisibilityTimeout: 60
              DLQ: email-dlq (maxReceiveCount: 3)
              │
              ▼
         Lambda: SendEmail
              │
              ├── SES (email)     → Rate: 14/sec (can request increase)
              ├── SNS SMS         → Rate: 1/sec per number
              └── Pinpoint (push) → APNs/FCM

# Rate limiting pattern:
# SQS maxConcurrency on Lambda = 5
# Each Lambda sends 1 email → max 5 emails/second
# Burst: SQS buffers → processes at steady 5/sec
# This prevents hitting SES rate limits!

# Bounce handling:
# SES → SNS Topic: ses-bounces → Lambda → DynamoDB (mark email as bounced)
# Future sends: Check bounce list BEFORE sending → skip bounced addresses
```

---

## 14. Real-World: Event Sourcing & CQRS

```
# Event Sourcing: Store every state change as an event

Order Events (Kinesis Data Streams):
├── {type: "OrderCreated", orderId: "123", items: [...], timestamp: T1}
├── {type: "PaymentProcessed", orderId: "123", amount: 4999, timestamp: T2}
├── {type: "OrderShipped", orderId: "123", trackingId: "XYZ", timestamp: T3}
└── {type: "OrderDelivered", orderId: "123", timestamp: T4}

# Consumer 1: Write Model (Kinesis → Lambda → DynamoDB)
# Maintains current order state by applying events in order

# Consumer 2: Read Model (Kinesis → Lambda → OpenSearch)
# Maintains searchable order index for customer support

# Consumer 3: Analytics (Kinesis → Firehose → S3 → Athena)
# Full event history for ad-hoc analysis

# Why Kinesis for Event Sourcing?
# 1. Events are IMMUTABLE (append-only)
# 2. Replay: Rebuild any read model by re-reading from start
# 3. Multiple consumers: Different projections from same events
# 4. Ordering: Events for same orderId arrive in order (partition key)
# 5. Retention: Up to 365 days of event history
```

---

## 15. Real-World: Log Aggregation Pipeline

```
# Centralized logging for 50 microservices

Applications → CloudWatch Logs
                    │
           Subscription Filter (pattern match)
                    │
                    ▼
           Kinesis Data Firehose
                    │
            ┌───────┼───────┐
            ▼               ▼
      S3 (archive)    OpenSearch (search/dashboards)
      (cheap, query    (real-time search,
       with Athena)     Kibana dashboards)

# Why Kinesis Firehose (not direct to OpenSearch)?
# 1. Auto-batches: Firehose buffers 1-15 min → efficient bulk inserts
# 2. Auto-retries: If OpenSearch is slow/down → Firehose retries
# 3. Backup to S3: Always have a copy in cheap S3 storage
# 4. Transform: Lambda inline can filter/enrich before delivery
# 5. Auto-scales: No shard management needed

# Firehose configuration:
# Buffer interval: 60 seconds
# Buffer size: 5 MB
# Compression: GZIP (for S3)
# Transform: Lambda (filter out DEBUG logs, enrich with service metadata)
# Backup: S3 bucket (all raw logs)
# Destination: OpenSearch (indexed for search)
```

---

## 16. EventBridge — The Modern Alternative

```
# EventBridge = SNS + content-based routing + schema registry + 30+ targets

# When to use EventBridge over SNS:
# 1. Content-based filtering (filter on ANY field in the message body)
# 2. Schema discovery and registry
# 3. Cross-account event routing
# 4. Scheduled events (cron/rate)
# 5. SaaS integrations (Shopify, Stripe, Auth0 → EventBridge)

# EventBridge Rule:
{
  "source": ["order-service"],
  "detail-type": ["OrderPlaced"],
  "detail": {
    "amount": [{"numeric": [">=", 10000]}],
    "region": ["us-east-1", "us-west-2"]
  }
}
# → Only high-value orders from US regions trigger this rule

# EventBridge vs SNS:
# SNS: Filter on message attributes only (not body)
# EventBridge: Filter on ANY field in message body (more powerful)
# SNS: Push to SQS, Lambda, HTTP, Email, SMS
# EventBridge: Push to 30+ targets including Step Functions, API Gateway, etc.
```

---

## 17. Error Handling & Dead Letter Queues

```
# DLQ Pattern (CRITICAL for production!)

Main Queue ──→ Lambda Consumer ──→ Success ✅
     │              │
     │              └── Failure (exception thrown)
     │                     │
     │              VisibilityTimeout expires
     │                     │
     │              Message becomes visible again → retry
     │                     │
     │              After maxReceiveCount retries (e.g., 3)
     │                     │
     ▼                     ▼
Dead Letter Queue (DLQ)
     │
     ▼
CloudWatch Alarm: "Messages in DLQ > 0"
     │
     ▼
SNS → PagerDuty/Slack (alert on-call engineer)

# DLQ Investigation Lambda:
# Reads from DLQ → logs message details → sends to Slack
# Engineer investigates → fixes bug → redrive messages

# SQS DLQ Redrive (re-process failed messages):
sqs.start_message_move_task(
    SourceArn=dlq_arn,
    DestinationArn=main_queue_arn
)
# Moves all DLQ messages back to main queue for reprocessing
```

---

## 🏆 Cheat Sheet: SQS vs SNS vs Kinesis vs EventBridge

```
┌────────────────────────────────────────────────────────────────────────┐
│     AWS MESSAGING CHEAT SHEET                                          │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│ SQS Standard:  Async decouple, unlimited throughput, at-least-once    │
│ SQS FIFO:      Ordered processing, exactly-once, 300 TPS             │
│ SNS:           Fan-out to multiple subscribers, push delivery         │
│ SNS + SQS:     Fan-out + buffering + retry (MOST COMMON pattern)     │
│ Kinesis:       Event streaming, replay, multiple consumers, ordered   │
│ Firehose:      Auto-delivery to S3/Redshift/OpenSearch (near RT)     │
│ EventBridge:   Content-based routing, cross-account, 30+ targets     │
│                                                                        │
│ PATTERNS:                                                              │
│ • Decouple A → B            → SQS                                    │
│ • Fan-out A → B, C, D       → SNS → SQS (per consumer)              │
│ • Real-time analytics        → Kinesis Data Streams                   │
│ • Log aggregation            → Kinesis Firehose → S3/OpenSearch      │
│ • Event sourcing / CQRS      → Kinesis (replay + multi-consumer)     │
│ • Notifications (multi-ch)   → SNS with filter policies              │
│ • EXACTLY-once processing    → SQS FIFO with deduplication           │
│ • Content-based routing      → EventBridge rules                     │
│ • Cron/scheduled tasks       → EventBridge Scheduler → Lambda        │
│                                                                        │
│ ALWAYS:                                                                │
│ • DLQ on every SQS queue (maxReceiveCount: 3-5)                      │
│ • Long polling (WaitTimeSeconds: 20)                                   │
│ • Idempotent consumers (messages CAN be delivered twice)              │
│ • CloudWatch alarm on DLQ message count > 0                           │
│ • Batch operations (send/receive 10 at a time)                        │
│                                                                        │
│ PRICING (approximate):                                                 │
│ • SQS: $0.40/million requests (first 1M free/month)                  │
│ • SNS: $0.50/million publishes + per-delivery cost                   │
│ • Kinesis: $0.015/shard-hour (or on-demand: $0.08/GB)               │
│ • EventBridge: $1.00/million events                                   │
│ • Firehose: $0.029/GB ingested                                       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary

### "How would you choose between SQS, SNS, and Kinesis?"

> *"I choose based on the messaging pattern. **SQS** for point-to-point async decoupling — like a job queue where one consumer processes each message, with DLQ for failures. **SNS** for fan-out — when one event needs to reach multiple services, I use SNS → SQS pattern so each consumer has its own queue with independent retry. **Kinesis** for event streaming — when I need multiple consumer groups reading the same data, replay capability, or strict ordering. For example, a clickstream analytics pipeline where I need the dashboard, the data lake, and the anomaly detector all reading the same stream independently. For content-based routing with complex rules, I'd use **EventBridge**. In practice, most architectures combine them: SNS for fan-out, SQS per consumer for buffering and retry, Kinesis for the analytics pipeline."*

---

> **Related Topics**: [Message Queues vs Event Streams →](./10-message-queues-vs-event-streams.md) | [Kafka Deep Dive →](./39-kafka-deep-dive.md) | [Kafka Real-World →](./39b-kafka-real-world-applications.md) | [AWS Architecture Mapping →](./52-architecting-on-aws-service-mapping.md)
