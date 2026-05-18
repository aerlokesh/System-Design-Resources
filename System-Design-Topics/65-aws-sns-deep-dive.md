# 🎯 Topic 65: AWS SNS Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Amazon Simple Notification Service (SNS) — pub/sub messaging, topic-based fan-out, push delivery to multiple subscribers (SQS, Lambda, HTTP, email, SMS), message filtering, FIFO topics, dead-letter queues, SNS vs SQS vs EventBridge comparison, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [SNS Architecture — Pub/Sub Fan-Out](#sns-architecture--pubsub-fan-out)
3. [Topics and Subscriptions](#topics-and-subscriptions)
4. [Subscriber Types](#subscriber-types)
5. [Message Filtering](#message-filtering)
6. [SNS FIFO Topics](#sns-fifo-topics)
7. [Fan-Out Pattern: SNS + SQS](#fan-out-pattern-sns--sqs)
8. [Delivery Policies and Retries](#delivery-policies-and-retries)
9. [Dead Letter Queues](#dead-letter-queues)
10. [Message Attributes and Payload](#message-attributes-and-payload)
11. [SNS vs SQS vs EventBridge](#sns-vs-sqs-vs-eventbridge)
12. [Security and Access Control](#security-and-access-control)
13. [Performance and Limits](#performance-and-limits)
14. [Cost Optimization](#cost-optimization)
15. [Real-World Production Patterns](#real-world-production-patterns)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Amazon SNS** is a fully managed pub/sub messaging service. A publisher sends ONE message to a TOPIC, and SNS delivers it to ALL subscribers of that topic — simultaneously, in parallel. Subscribers can be SQS queues, Lambda functions, HTTP endpoints, email addresses, or SMS numbers.

```
The mental model:

  Publisher → SNS Topic → Fan-out to N subscribers

  ONE publish = N deliveries (simultaneously)

  ┌──────────┐         ┌──────────────┐         ┌──────────────┐
  │ Publisher │───────→│  SNS Topic    │────────→│ SQS Queue A   │
  │ (Service) │         │ "order-placed"│────────→│ Lambda Func B │
  └──────────┘         │              │────────→│ HTTP Endpoint C│
                       │              │────────→│ Email address D│
                       └──────────────┘         └──────────────┘

  Use case: "Order placed" → notify inventory, billing, shipping, email simultaneously.
  Without SNS: Service must know ALL downstream consumers + call them sequentially.
  With SNS: Service publishes ONCE. SNS handles fan-out. Fully decoupled.
```

### When to Use SNS

```
USE SNS when:
  ✅ One event needs to reach multiple consumers (fan-out)
  ✅ You need push-based delivery (not polling)
  ✅ Subscribers are heterogeneous (SQS + Lambda + HTTP + email)
  ✅ Decoupling producers from consumers
  ✅ Event notification (not request-response)

DON'T USE SNS when:
  ❌ Point-to-point messaging (use SQS directly)
  ❌ Need message retention/replay (use Kinesis or SQS)
  ❌ Complex event routing rules (use EventBridge)
  ❌ Streaming data (use Kinesis)
  ❌ Need request-response (use API Gateway/HTTP)
```

---

## SNS Architecture — Pub/Sub Fan-Out

### How It Works

```
1. Publisher calls: sns.publish(TopicArn, Message)
2. SNS receives the message.
3. SNS looks up ALL active subscriptions for that topic.
4. SNS delivers the message to EACH subscriber in PARALLEL.
5. Each delivery is independent — failure of one doesn't affect others.

Key properties:
  - At-least-once delivery (message may be delivered more than once)
  - Best-effort ordering (FIFO topics available for strict ordering)
  - No message retention (message is delivered and gone — not stored)
  - Push model (SNS pushes to subscribers; subscribers don't poll)
```

### Throughput

```
Standard topics:
  Publish: Nearly unlimited (soft limit: 30K publishes/sec per topic, can be increased)
  Deliveries: Millions per second (parallel to all subscribers)
  
FIFO topics:
  Publish: 300 publishes/sec (or 10 MB/sec)
  Higher with batching: 3000 messages/sec (batch of 10)
  
Message size: Up to 256 KB per message
  For larger payloads: Store in S3, put S3 URL in SNS message.
```

---

## Topics and Subscriptions

### Topic Types

```
Standard Topic:
  - Best-effort ordering (messages may arrive out of order)
  - At-least-once delivery (possible duplicates)
  - Nearly unlimited throughput
  - Subscribers: SQS, Lambda, HTTP/S, Email, SMS, Mobile Push

FIFO Topic:
  - Strict ordering (per message group)
  - Exactly-once delivery (with SQS FIFO subscriber)
  - 300 messages/sec (3000 with batching)
  - Subscribers: SQS FIFO queues ONLY
  - Topic name must end with ".fifo"
```

### Subscription Lifecycle

```
Create subscription:
  sns.subscribe(TopicArn, Protocol, Endpoint)
  
  Protocol: "sqs", "lambda", "https", "email", "sms"
  Endpoint: Queue ARN, Lambda ARN, URL, email address, phone number

Confirmation:
  HTTP/Email/SMS: Subscriber must CONFIRM (click link or respond to message)
  SQS/Lambda: Auto-confirmed (AWS manages trust)

Subscription filter (optional):
  Only deliver messages matching certain attributes.
  Reduces noise — subscriber only gets relevant messages.
```

---

## Subscriber Types

### SQS (Most Common)

```
SNS → SQS: The "fan-out" pattern.
  One message published → delivered to N SQS queues.
  Each queue processes independently at its own pace.
  
  Use: Decouple services. Each service has its own queue.
  
  Example:
    "order-placed" topic → 3 SQS queues:
      - inventory-updates-queue (Inventory Service polls)
      - billing-queue (Billing Service polls)
      - shipping-queue (Shipping Service polls)
```

### Lambda (Serverless Processing)

```
SNS → Lambda: Direct invocation.
  Message arrives → Lambda function invoked with the message as payload.
  No queue in between (unless you want buffering → use SNS → SQS → Lambda).
  
  Retry: SNS retries Lambda invocation 3 times on failure.
  Async: Lambda processes asynchronously.
  
  Use: Lightweight event handlers, transformations, notifications.
```

### HTTP/HTTPS (Webhooks)

```
SNS → HTTP endpoint: POST request to your URL.
  Message delivered as POST body.
  Endpoint must return 2xx to acknowledge.
  If non-2xx: SNS retries with exponential backoff.
  
  Use: Deliver events to external services, partner integrations.
  
  Security: SNS signs messages. Subscriber can verify signature.
```

### Email / SMS / Mobile Push

```
SNS → Email: Sends an email with the message body.
SNS → SMS: Sends a text message.
SNS → Mobile Push: APNs (iOS), FCM (Android), ADM (Kindle).

Use: Human notifications. Alert on-call engineers, notify customers.
Not for system-to-system communication (use SQS/Lambda for that).
```

---

## Message Filtering

### Subscription Filter Policies

```
Without filtering: Every subscriber gets EVERY message.
  order-placed topic: 100K messages/day
  Shipping service only cares about "physical" orders (not digital).
  Without filter: Shipping receives 100K messages, discards 60% (digital orders).

With filtering: Subscriber declares what it wants.
  Subscription filter policy:
  {
    "order_type": ["physical"],
    "country": ["US", "CA"]
  }
  
  Only messages with order_type="physical" AND country in ["US","CA"] are delivered.
  Digital orders → not sent to Shipping queue. Saves cost + processing.

Filter operates on MESSAGE ATTRIBUTES (not the body).
  Message must include: MessageAttributes = {"order_type": {"DataType": "String", "StringValue": "physical"}}
```

### Filter Policy Operators

```
Exact match:     {"status": ["shipped", "delivered"]}
Prefix match:    {"region": [{"prefix": "us-"}]}
Numeric match:   {"price": [{"numeric": [">", 100]}]}
Exists:          {"priority": [{"exists": true}]}
Negation:        {"status": [{"anything-but": "cancelled"}]}

Complex example:
{
  "event_type": ["order_placed"],
  "amount": [{"numeric": [">=", 100, "<=", 1000]}],
  "region": [{"prefix": "eu-"}]
}
→ Only delivers order_placed events between $100-$1000 in EU regions.
```

---

## SNS FIFO Topics

### When to Use FIFO

```
Standard: Messages may arrive out of order. Duplicates possible.
FIFO: Strict ordering + exactly-once (with FIFO SQS subscriber).

Use FIFO when:
  ✅ Order of events matters (state machine transitions)
  ✅ Duplicate processing causes issues (financial transactions)
  ✅ Events within an entity must be processed sequentially

Example:
  Order lifecycle: placed → confirmed → shipped → delivered
  MUST be processed in order. If "delivered" arrives before "placed" → error.
  
  Message group ID: "order:12345"
  All events for order 12345 are delivered in order.
  Different orders (different group IDs) are processed in parallel.
```

### FIFO Limitations

```
Subscribers: SQS FIFO queues ONLY (no Lambda, no HTTP, no email)
Throughput: 300 messages/sec (3000 with batching)
Deduplication: 5-minute window (by message dedup ID or content hash)

If you need FIFO + Lambda:
  SNS FIFO → SQS FIFO → Lambda (event source mapping with FIFO)
```

---

## Fan-Out Pattern: SNS + SQS

### The Canonical Pattern

```
┌──────────┐      ┌───────────┐      ┌──────────┐      ┌──────────────┐
│ Order     │─────→│ SNS Topic │─────→│ SQS Q1   │─────→│ Inventory Svc│
│ Service   │      │ "orders"  │─────→│ SQS Q2   │─────→│ Billing Svc  │
└──────────┘      │           │─────→│ SQS Q3   │─────→│ Shipping Svc │
                  └───────────┘      └──────────┘      └──────────────┘

Benefits:
  1. Order Service publishes ONCE (doesn't know about downstream services)
  2. Each service has its OWN queue (processes at its own pace)
  3. Adding a new consumer = add a new SQS subscription (no code change in publisher)
  4. If Shipping is slow → messages buffer in its queue (doesn't affect Billing)
  5. Each queue has independent retry/DLQ policies

This is the #1 most common SNS pattern in production AWS architectures.
```

### Why Not Publish Directly to Each SQS Queue?

```
Without SNS (publisher knows all consumers):
  order_service.publish_to_queue("inventory-queue", message)
  order_service.publish_to_queue("billing-queue", message)
  order_service.publish_to_queue("shipping-queue", message)
  
  Problems:
    - Publisher COUPLED to consumers (must update code to add new consumer)
    - If one publish fails → partial delivery
    - Hard to add cross-account subscribers
    - N API calls (not parallel from publisher's perspective)

With SNS:
  order_service.publish_to_topic("orders", message)  # ONE call
  
  SNS handles fan-out to ALL subscribers in parallel.
  Adding a new subscriber = one AWS CLI command (no code change).
  Publisher is completely decoupled from consumers.
```

---

## Delivery Policies and Retries

### HTTP/HTTPS Retry Policy

```
Default retry policy for HTTP endpoints:
  Immediate retries: 3 (within seconds)
  Pre-backoff retries: 2 (10 seconds apart)
  Backoff retries: 10 (exponential: 10s, 20s, 40s, ..., up to 3600s)
  Post-backoff retries: 38 (3600s apart)
  
  Total attempts: 53 over ~23 minutes to ~1 hour
  
  Customizable: Set min/max delay, number of retries, backoff function.
```

### SQS/Lambda Retry

```
SNS → SQS: If SQS returns error, SNS retries up to 100,015 times over 23 days.
  In practice: SQS is highly available, almost never rejects.

SNS → Lambda: 3 retries (synchronous invocation).
  If all 3 fail → message is lost (unless you configure a DLQ on the subscription).
```

---

## Dead Letter Queues

### Per-Subscription DLQ

```
Each subscription can have its own SQS DLQ:
  If delivery fails after all retries → message goes to the DLQ.
  
Configuration:
  sns.subscribe(
    TopicArn='arn:aws:sns:...:orders',
    Protocol='https',
    Endpoint='https://my-service.com/webhook',
    Attributes={
      'RedrivePolicy': '{"deadLetterTargetArn": "arn:aws:sqs:...:orders-dlq"}'
    }
  )

DLQ inspection:
  Monitor DLQ message count → alert if > 0.
  Messages in DLQ contain: original message + error details + subscription ARN.
  Ops team investigates and re-drives (replays) messages manually or via automation.
```

---

## Message Attributes and Payload

### Message Structure

```python
import boto3
sns = boto3.client('sns')

sns.publish(
    TopicArn='arn:aws:sns:us-east-1:123456789012:order-events',
    Message=json.dumps({
        'order_id': 'ord-12345',
        'customer_id': 'cust-42',
        'items': [{'sku': 'ABC', 'qty': 2}],
        'total': 99.99
    }),
    MessageAttributes={
        'event_type': {'DataType': 'String', 'StringValue': 'order_placed'},
        'order_type': {'DataType': 'String', 'StringValue': 'physical'},
        'amount': {'DataType': 'Number', 'StringValue': '99.99'},
        'region': {'DataType': 'String', 'StringValue': 'us-east-1'}
    }
)

# MessageAttributes used for:
#   1. Subscription filtering (subscribers only get relevant messages)
#   2. Metadata without parsing the body
#   3. Routing decisions in consumers
```

### Raw vs JSON Delivery

```
Raw delivery (raw message delivery = true):
  Subscriber receives ONLY the Message body.
  Use: When subscriber doesn't need SNS metadata.

JSON delivery (default):
  Subscriber receives SNS envelope:
  {
    "Type": "Notification",
    "MessageId": "...",
    "TopicArn": "...",
    "Message": "<your message body>",
    "Timestamp": "...",
    "MessageAttributes": {...}
  }
  
  SQS subscribers: Enable "Raw Message Delivery" to avoid double-encoding.
```

---

## SNS vs SQS vs EventBridge

| Aspect | SNS | SQS | EventBridge |
|---|---|---|---|
| **Pattern** | Pub/Sub (fan-out) | Point-to-point queue | Event bus (rules-based routing) |
| **Delivery** | Push to subscribers | Pull (consumer polls) | Push to targets |
| **Retention** | None (deliver and forget) | Up to 14 days | None (deliver and forget) |
| **Ordering** | Best-effort (FIFO available) | FIFO available | Best-effort |
| **Filtering** | Attribute-based (simple) | None (all messages received) | Content-based (complex, JSON path) |
| **Throughput** | Nearly unlimited | 3000/sec (FIFO) or unlimited (standard) | 2400 events/sec (per region, soft limit) |
| **Subscribers/Targets** | SQS, Lambda, HTTP, Email, SMS | One consumer (or consumer group) | 300+ AWS service integrations |
| **Best for** | Simple fan-out to multiple consumers | Buffering, decoupling, work queues | Complex routing, AWS service integration |

### Interview Decision Script

> *"I'd use SNS when I need to fan out one event to multiple independent consumers — like 'order placed' going to inventory, billing, and shipping services simultaneously. Each gets its own SQS queue behind SNS. I'd use SQS alone for point-to-point buffering where one producer feeds one consumer. I'd use EventBridge when I need complex content-based routing rules (JSON path filtering), or when integrating with many AWS services as targets (Step Functions, API Gateway, ECS tasks) — EventBridge has 300+ native targets. The common pattern is SNS for simple fan-out, EventBridge for complex routing."*

---

## Security and Access Control

```
Topic policies (resource-based):
  Control WHO can publish to and subscribe to a topic.
  Cross-account publishing: Allow another AWS account to publish.

Encryption:
  SSE-KMS: Encrypt messages at rest using KMS key.
  Messages are decrypted transparently when delivered to subscribers.
  
  In-transit: HTTPS endpoints. TLS for SQS/Lambda delivery.

VPC endpoints:
  PrivateLink: Publish to SNS from within VPC without internet access.
  Interface VPC endpoint for SNS.
```

---

## Performance and Limits

```
Standard topics:
  Publish: Soft limit 30K/sec/topic (can request increase to 100K+)
  Subscriptions per topic: 12.5 million
  Topics per account: 100K (soft limit)
  Message size: 256 KB

FIFO topics:
  Publish: 300/sec (3000 with batching)
  Subscriptions: SQS FIFO only
  Dedup window: 5 minutes

Delivery latency:
  SNS → SQS: <100ms typically
  SNS → Lambda: <100ms typically
  SNS → HTTP: Depends on endpoint response time
  SNS → Email: Seconds to minutes (email delivery delay)
  SNS → SMS: 1-30 seconds (carrier dependent)
```

---

## Cost Optimization

```
SNS pricing:
  Publishes: $0.50 per million requests
  Deliveries to SQS: Free
  Deliveries to Lambda: Free
  Deliveries to HTTP: $0.60 per million
  Deliveries to Email: $2.00 per 100K
  Deliveries to SMS: $0.00645+ per message (varies by country)
  
  Data transfer: First 1 GB free, then standard rates.

Optimization:
  1. Use message filtering → fewer deliveries to uninterested subscribers
  2. Batch publishes (FIFO: up to 10 per API call)
  3. Use SQS/Lambda subscribers (free delivery) over HTTP/Email
  4. Large messages: Store body in S3, send reference in SNS (stay under 256 KB)
```

---

## Real-World Production Patterns

### Pattern 1: Microservice Event Fan-Out

```
Order Service → SNS "order-events" → 
  SQS (Inventory) + SQS (Billing) + SQS (Shipping) + SQS (Analytics) + Lambda (Email)

  5 subscribers. One publish call. Fully decoupled.
  Adding a 6th subscriber: one CLI command, zero code changes.
```

### Pattern 2: Alert Distribution

```
CloudWatch Alarm → SNS "critical-alerts" →
  SMS (On-call engineer) + Email (Team) + Lambda (PagerDuty webhook) + SQS (Audit log)

  Multi-channel alerting from a single alarm.
```

### Pattern 3: Cross-Account Event Sharing

```
Account A (Producer) → SNS Topic (with cross-account policy) →
  Account B: SQS subscription
  Account C: Lambda subscription
  
  Enterprise: Central event bus that multiple teams/accounts subscribe to.
```

---

## Interview Talking Points & Scripts

### Script 1: Core Fan-Out Pattern

> *"SNS is a pub/sub service for fan-out. I'd use it when one event needs to reach multiple independent consumers. The canonical pattern: Order Service publishes 'order placed' to an SNS topic. Behind the topic: SQS queues for Inventory, Billing, Shipping, and Analytics — each processing independently. The publisher doesn't know or care about subscribers. Adding a new consumer is one AWS CLI command — zero code changes to the publisher. This is the foundation of event-driven microservice architectures on AWS."*

### Script 2: SNS + SQS vs Direct SQS

> *"Without SNS, the publisher must know ALL consumers and send to each queue individually — tight coupling, partial failure risk, and code changes for every new consumer. With SNS → SQS fan-out, the publisher calls one API (sns.publish). SNS delivers to all SQS subscribers in parallel. Each queue is independent — if Shipping is slow, messages buffer in its queue without affecting Billing. It's the difference between 'Publisher Service knows about 5 downstream services' and 'Publisher Service knows about one topic.'"*

### Script 3: Message Filtering

> *"SNS message filtering lets subscribers declare what they want. Instead of every subscriber receiving every message and discarding irrelevant ones, the filter is applied at SNS level. Shipping subscribes with filter: {order_type: ['physical']}. Digital-only orders are never sent to Shipping's queue — saving SQS costs and processing time. Filters operate on message attributes (key-value metadata), not the body. Supports exact match, prefix, numeric ranges, and negation."*

### Script 4: FIFO for Ordering

> *"For events that must be processed in order — like order lifecycle events (placed → confirmed → shipped → delivered) — I'd use an SNS FIFO topic with an SQS FIFO subscriber. The message group ID is the order_id, so all events for one order arrive in sequence. Different orders (different group IDs) process in parallel. FIFO also provides exactly-once delivery via deduplication IDs. The tradeoff: 300 msgs/sec (vs unlimited for standard), and only SQS FIFO as subscribers."*

### Script 5: SNS vs EventBridge

> *"SNS is for simple fan-out: one message → N subscribers, attribute-based filtering. EventBridge is for complex routing: content-based rules with JSON path matching, 300+ AWS service targets (Step Functions, ECS, CodePipeline), and schema registry. I'd use SNS when I have 3-5 SQS subscribers behind a topic. I'd switch to EventBridge when I need routing rules like 'if order.total > $1000 AND order.region starts with eu- → route to fraud-detection Lambda AND compliance-queue.' EventBridge is also better for cross-service orchestration (native Step Functions integration)."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Confusing SNS with SQS

**Bad**: "SNS is a message queue."
**Fix**: SNS is pub/sub (fan-out). SQS is a queue (point-to-point). SNS pushes, SQS is polled. They're often used TOGETHER: SNS → SQS.

### ❌ Mistake 2: "SNS retains messages"

**Bad**: "If the subscriber is down, SNS holds the message until it recovers."
**Fix**: SNS doesn't retain messages. If delivery fails after retries → DLQ (if configured) or message is lost. For retention, put SQS behind SNS.

### ❌ Mistake 3: Not mentioning the SNS + SQS pattern

**Bad**: "I'd have each service subscribe directly to SNS."
**Fix**: SNS → SQS → Service. The SQS queue buffers messages, provides retry, and decouples consumption rate from publishing rate.

### ❌ Mistake 4: Using SNS for streaming data

**Bad**: "I'd use SNS for real-time clickstream processing."
**Fix**: SNS is for events/notifications, not streaming. Use Kinesis for high-throughput ordered streams.

### ❌ Mistake 5: Ignoring message filtering

**Bad**: "Every subscriber gets every message from the topic."
**Fix**: Use subscription filter policies. Shipping only gets physical orders. Analytics only gets high-value orders. Reduces cost and processing.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│                   AWS SNS DEEP DIVE — CHEAT SHEET                    │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  WHAT: Pub/Sub fan-out service. One publish → N deliveries.         │
│  PATTERN: Publisher → SNS Topic → Multiple Subscribers (parallel).  │
│                                                                      │
│  SUBSCRIBERS: SQS, Lambda, HTTP/S, Email, SMS, Mobile Push.         │
│  CANONICAL: SNS → SQS (each service gets its own queue).           │
│                                                                      │
│  FILTERING: Attribute-based. Subscribers only get relevant messages. │
│  FIFO: Strict ordering + exactly-once. 300 msgs/sec. SQS FIFO only.│
│  RETENTION: NONE. Message delivered and gone. Use SQS for buffering.│
│                                                                      │
│  DELIVERY: At-least-once (Standard). Exactly-once (FIFO + SQS FIFO)│
│  RETRIES: HTTP = 53 attempts over ~1 hour. SQS/Lambda = 3.         │
│  DLQ: Per-subscription. Failed messages go to SQS DLQ.             │
│                                                                      │
│  THROUGHPUT: 30K+ publishes/sec (Standard). 300/sec (FIFO).        │
│  MESSAGE SIZE: 256 KB max. Larger → S3 reference.                   │
│                                                                      │
│  vs SQS: SNS = fan-out (1→N). SQS = queue (1→1).                  │
│  vs EventBridge: SNS = simple filter. EB = complex routing rules.   │
│  Together: SNS + SQS = the standard decoupled microservice pattern. │
│                                                                      │
│  COST: $0.50/M publishes. SQS/Lambda delivery = free.              │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 10: Message Queues vs Event Streams** — SNS/SQS/Kinesis comparison
- **Topic 43: Notification Systems** — SNS as notification infrastructure
- **Topic 52: Architecting on AWS** — SNS in event-driven architectures
- **Topic 63: Kinesis Deep Dive** — Streaming alternative to SNS

---

*This document is part of the System Design Interview Deep Dive series.*
