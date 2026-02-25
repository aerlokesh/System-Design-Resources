# Design a High-Throughput Event Ingestion & Subscription System - Hello Interview Framework

> **Question**: Design a horizontally scalable platform where producers send high-volume events (fire-and-forget, up to 1,000,000 events/second). Consumers register content-based subscription rules and webhooks to receive matching events. Events are JSON objects with a `tenant_id` and metadata (`id`, `timestamp`, `type`). The system must isolate tenants, provide durable, reliable delivery to subscribers, and support content-based filtering.
>
> **Asked at**: Google
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
1. **Event Ingestion**: Producers fire-and-forget JSON events at high throughput (up to 1M events/sec sustained, with burst tolerance). Events contain `tenant_id`, `id`, `timestamp`, `type`, and arbitrary JSON payload.
2. **Subscription Registration**: Subscribers register content-based filtering rules (e.g., `type == "order.created" AND payload.amount > 100`) and a webhook endpoint to receive matching events.
3. **Content-Based Filtering**: The system evaluates each incoming event against all active subscriptions and routes matching events to the correct subscriber webhooks.
4. **Reliable Webhook Delivery**: At-least-once delivery to subscriber webhooks with retries (exponential backoff), DLQ for persistent failures, and delivery status tracking.
5. **Multi-Tenant Isolation**: Each tenant's events are isolated — Tenant A cannot see Tenant B's events. Per-tenant rate limiting, quotas, and fair-share scheduling prevent noisy-neighbor problems.
6. **Rule Expression Language**: A well-defined filter syntax supporting equality, comparison, logical operators (`AND`, `OR`, `NOT`), and nested field access on JSON payloads.

#### Nice to Have (P1)
- Exactly-once delivery semantics (idempotency keys on webhook calls).
- Event replay (re-deliver all events from the last N hours to a subscriber).
- Subscription pause/resume without losing events.
- Event transformation/enrichment before delivery (e.g., add fields, redact PII).
- Subscriber health dashboard (delivery success rate, latency P50/P99).
- Event schema registry and validation (reject malformed events at ingestion).

#### Below the Line (Out of Scope)
- Pull-based consumption (long-polling, gRPC streams) — we focus on push (webhooks).
- Event sourcing / CQRS pattern on the consumer side.
- Building the producers themselves — we design the platform they publish to.
- Complex event processing (CEP) — no windowed aggregations or pattern detection.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Ingestion Throughput** | 1M events/sec sustained, 3M burst | Google-scale event firehose |
| **Ingestion Latency** | < 50ms P99 (ACK to producer) | Fire-and-forget must be fast |
| **End-to-End Latency** | < 5 seconds (ingest → webhook delivery) | Near-real-time for most use cases |
| **Durability** | 99.999% (no event loss after ACK) | Events must survive node failures |
| **Delivery Guarantee** | At-least-once to webhooks | Subscribers handle deduplication via event `id` |
| **Availability** | 99.99% for ingestion, 99.9% for delivery | Ingestion is the critical path; delivery can tolerate brief delays |
| **Filtering Latency** | < 10ms per event (rule evaluation) | Must not become the bottleneck at 1M events/sec |
| **Tenant Isolation** | No cross-tenant data leaks, fair-share resources | Hard isolation for data, soft isolation for compute |
| **Subscriber Scale** | 100K active subscriptions, 10K tenants | Large multi-tenant SaaS platform |

### Capacity Estimation

```
Events:
  Sustained ingestion: 1M events/sec = 86.4B events/day
  Average event size: 1 KB (JSON payload)
  Ingestion bandwidth: 1M × 1 KB = 1 GB/sec = 86 TB/day
  
  Event retention: 7 days (for replay)
  Total event storage: 86 TB/day × 7 = ~600 TB hot storage

Subscriptions:
  Active subscriptions: 100K
  Tenants: 10K
  Average subscriptions per tenant: 10
  Average rules per subscription: 3 conditions (AND/OR)

Matching & Delivery:
  Average match rate: 5% of events match at least one subscription
  Matched events/sec: 1M × 5% = 50K matched events/sec
  Average fan-out: 2 subscriptions match per matched event
  Webhook deliveries/sec: 50K × 2 = 100K webhook calls/sec
  
  With retries (10% failure rate): +10K retries/sec
  Total delivery throughput: ~110K webhook calls/sec

Webhook delivery:
  Each delivery: event payload (1 KB) + headers (200 bytes) ≈ 1.2 KB
  Delivery bandwidth: 110K × 1.2 KB = ~130 MB/sec outbound

Storage summary:
  Event log (Kafka): ~600 TB (7-day retention, 3x replication = 1.8 PB raw)
  Subscription metadata: 100K × 2 KB = ~200 MB
  Delivery status/DLQ: ~50 GB/day (failed deliveries + retry metadata)
  Filter index (compiled rules): ~500 MB in memory
```

---

## 2️⃣ Core Entities

### Entity 1: Event
```java
public class Event {
    private final String eventId;           // UUID, globally unique
    private final String tenantId;          // Tenant isolation key
    private final String type;              // e.g., "order.created", "user.signup"
    private final Instant timestamp;        // Producer-provided event time
    private final Map<String, Object> payload;  // Arbitrary JSON payload
    private final Map<String, String> metadata; // System metadata (source, version, etc.)
    private final Instant ingestedAt;       // System ingestion time
    private final String partitionKey;      // For ordering (default: tenantId)
}
```

### Entity 2: Subscription
```java
public class Subscription {
    private final String subscriptionId;    // UUID
    private final String tenantId;          // Owner tenant
    private final String name;              // Human-readable name
    private final FilterRule filterRule;     // Content-based filter expression
    private final WebhookConfig webhook;    // Delivery endpoint configuration
    private final SubscriptionStatus status; // ACTIVE, PAUSED, SUSPENDED
    private final RetryPolicy retryPolicy;  // Backoff & max retries
    private final Instant createdAt;
    private final Instant updatedAt;
}

public enum SubscriptionStatus {
    ACTIVE,     // Receiving matched events
    PAUSED,     // Temporarily stopped (events buffered)
    SUSPENDED   // Disabled by system (quota exceeded, repeated failures)
}
```

### Entity 3: Filter Rule
```java
public class FilterRule {
    private final String expression;        // Raw filter: "type == 'order.created' AND payload.amount > 100"
    private final CompiledRule compiled;     // Pre-compiled for fast evaluation
    private final List<String> indexedFields; // Fields used in rule (for indexing optimization)
}

// Supported operators:
// ==, !=, >, <, >=, <=          (comparison)
// AND, OR, NOT                   (logical)
// IN, NOT IN                     (set membership)
// EXISTS, NOT EXISTS             (field presence)
// CONTAINS                       (substring match)
// Nested field access: payload.order.items[0].sku
```

### Entity 4: Webhook Config
```java
public class WebhookConfig {
    private final String url;               // HTTPS endpoint
    private final String secret;            // HMAC signing secret for verification
    private final Map<String, String> headers; // Custom headers (e.g., Authorization)
    private final int timeoutMs;            // Per-request timeout (default: 5000ms)
    private final ContentType contentType;  // JSON or CloudEvents format
}
```

### Entity 5: Delivery Attempt
```java
public class DeliveryAttempt {
    private final String deliveryId;        // UUID
    private final String eventId;
    private final String subscriptionId;
    private final String tenantId;
    private final int attemptNumber;        // 1, 2, 3, ...
    private final DeliveryStatus status;    // PENDING, SUCCESS, FAILED, DLQ
    private final int httpStatusCode;       // Response from webhook
    private final long latencyMs;           // Delivery round-trip time
    private final Instant attemptedAt;
    private final String errorMessage;      // On failure
}

public enum DeliveryStatus {
    PENDING,    // Queued for delivery
    SUCCESS,    // 2xx response from webhook
    FAILED,     // Non-2xx or timeout — will retry
    DLQ         // Max retries exhausted — sent to dead letter queue
}
```

### Entity 6: Tenant Quota
```java
public class TenantQuota {
    private final String tenantId;
    private final long maxEventsPerSecond;       // Ingestion rate limit
    private final long maxSubscriptions;         // Max active subscriptions
    private final long maxEventSizeBytes;        // Max single event size (e.g., 256 KB)
    private final long maxWebhookTimeoutMs;      // Max allowed webhook timeout
    private final long maxRetries;               // Max retry attempts per delivery
    private final long dailyEventQuota;          // Max events per day
    private final long dailyDeliveryQuota;       // Max webhook calls per day
}
```

---

## 3️⃣ API Design

### 1. Publish Events (Ingestion)
```
POST /api/v1/events

Headers:
  X-Tenant-Id: tenant_abc
  X-Api-Key: sk_live_xxx
  Content-Type: application/json

Request:
{
  "events": [
    {
      "id": "evt_001",
      "type": "order.created",
      "timestamp": "2025-01-10T10:00:00Z",
      "payload": {
        "order_id": "ord_12345",
        "amount": 149.99,
        "currency": "USD",
        "customer_id": "cust_789"
      }
    },
    {
      "id": "evt_002",
      "type": "order.shipped",
      "timestamp": "2025-01-10T10:00:01Z",
      "payload": {
        "order_id": "ord_12345",
        "tracking_number": "1Z999AA10123456784"
      }
    }
  ]
}

Response (202 Accepted):
{
  "accepted": 2,
  "rejected": 0,
  "event_ids": ["evt_001", "evt_002"]
}
```

> **Note**: Batch ingestion (up to 100 events per request). Response is 202 (Accepted) — fire-and-forget. The system guarantees durability after ACK.

### 2. Create Subscription
```
POST /api/v1/subscriptions

Headers:
  X-Tenant-Id: tenant_abc

Request:
{
  "name": "High-value orders",
  "filter": "type == 'order.created' AND payload.amount > 100",
  "webhook": {
    "url": "https://myapp.com/webhooks/orders",
    "secret": "whsec_abc123",
    "headers": { "Authorization": "Bearer token_xyz" },
    "timeout_ms": 5000
  },
  "retry_policy": {
    "max_retries": 5,
    "backoff": "exponential",
    "initial_delay_ms": 1000,
    "max_delay_ms": 300000
  }
}

Response (201 Created):
{
  "subscription_id": "sub_456",
  "name": "High-value orders",
  "filter": "type == 'order.created' AND payload.amount > 100",
  "status": "ACTIVE",
  "webhook": { "url": "https://myapp.com/webhooks/orders" },
  "created_at": "2025-01-10T10:00:00Z"
}
```

### 3. List Subscriptions
```
GET /api/v1/subscriptions?status=ACTIVE&page=1&page_size=20

Headers:
  X-Tenant-Id: tenant_abc

Response (200 OK):
{
  "subscriptions": [
    {
      "subscription_id": "sub_456",
      "name": "High-value orders",
      "filter": "type == 'order.created' AND payload.amount > 100",
      "status": "ACTIVE",
      "webhook": { "url": "https://myapp.com/webhooks/orders" },
      "stats": {
        "events_delivered": 12543,
        "events_failed": 23,
        "last_delivered_at": "2025-01-10T09:59:50Z"
      }
    }
  ],
  "pagination": { "page": 1, "page_size": 20, "total": 8, "has_next": false }
}
```

### 4. Update Subscription (Pause/Resume/Update Filter)
```
PATCH /api/v1/subscriptions/{subscription_id}

Request:
{
  "status": "PAUSED"
}

Response (200 OK):
{
  "subscription_id": "sub_456",
  "status": "PAUSED",
  "updated_at": "2025-01-10T10:05:00Z"
}
```

### 5. Delete Subscription
```
DELETE /api/v1/subscriptions/{subscription_id}

Response (204 No Content)
```

### 6. Get Delivery Status / DLQ
```
GET /api/v1/subscriptions/{subscription_id}/deliveries?status=DLQ&page=1&page_size=50

Response (200 OK):
{
  "deliveries": [
    {
      "delivery_id": "del_789",
      "event_id": "evt_001",
      "event_type": "order.created",
      "attempts": 5,
      "last_status": "DLQ",
      "last_http_status": 503,
      "last_error": "Service Unavailable",
      "first_attempted_at": "2025-01-10T10:00:01Z",
      "last_attempted_at": "2025-01-10T10:15:30Z"
    }
  ],
  "pagination": { "page": 1, "page_size": 50, "total": 23 }
}
```

### 7. Replay Events to Subscription
```
POST /api/v1/subscriptions/{subscription_id}/replay

Request:
{
  "from": "2025-01-10T00:00:00Z",
  "to": "2025-01-10T12:00:00Z"
}

Response (202 Accepted):
{
  "replay_id": "rpl_101",
  "subscription_id": "sub_456",
  "status": "IN_PROGRESS",
  "estimated_events": 45000
}
```

### 8. Validate Filter Expression
```
POST /api/v1/filters/validate

Request:
{
  "filter": "type == 'order.created' AND payload.amount > 100",
  "test_event": {
    "type": "order.created",
    "payload": { "amount": 150 }
  }
}

Response (200 OK):
{
  "valid": true,
  "matches_test_event": true,
  "parsed_ast": {
    "operator": "AND",
    "left": { "field": "type", "op": "==", "value": "order.created" },
    "right": { "field": "payload.amount", "op": ">", "value": 100 }
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Event Ingestion (Write Path — Hot Path)
```
1. Producer sends POST /api/v1/events with batch of JSON events
   ↓
2. API Gateway:
   a. Authenticate: validate X-Api-Key for tenant
   b. Rate limit: check tenant ingestion quota (token bucket)
   c. Validate: check event schema (id, type, timestamp present; size < 256 KB)
   ↓
3. Ingestion Service:
   a. Assign partition key: hash(tenant_id) → Kafka partition
   b. Enrich: add ingestedAt timestamp, system metadata
   c. Produce to Kafka topic "raw-events" (partition by tenant_id)
   d. Wait for Kafka ACK (acks=all for durability)
   ↓
4. Return 202 Accepted to producer (< 50ms P99)
   ↓
5. Event is now DURABLY stored in Kafka (replicated across 3 brokers)
```

### Flow 2: Event Matching & Routing (Processing Path)
```
1. Filter Workers consume from Kafka topic "raw-events" (consumer group)
   ↓
2. For each event:
   a. Load tenant's active subscriptions (from in-memory subscription cache)
   b. For each subscription, evaluate filter rule against event:
      - Pre-filter by event "type" field (index lookup — O(1))
      - Full rule evaluation on matching subscriptions (< 1ms per rule)
   c. Collect all matching subscriptions
   ↓
3. For each match, produce a DeliveryTask to Kafka topic "delivery-tasks"
   {
     event_id: "evt_001",
     subscription_id: "sub_456",
     tenant_id: "tenant_abc",
     webhook_url: "https://myapp.com/webhooks/orders",
     event_payload: { ... }
   }
   ↓
4. DeliveryTask is partitioned by subscription_id (preserves ordering per subscriber)
```

### Flow 3: Webhook Delivery (Delivery Path)
```
1. Delivery Workers consume from Kafka topic "delivery-tasks"
   ↓
2. For each DeliveryTask:
   a. Sign payload with HMAC-SHA256 using subscription's webhook secret
   b. POST to webhook URL with:
      Headers: X-Event-Id, X-Subscription-Id, X-Timestamp, X-Signature
      Body: JSON event payload
   c. Wait for response (timeout: 5 seconds)
   ↓
3. On SUCCESS (2xx response):
   a. Record delivery success
   b. Commit Kafka offset
   ↓
4. On FAILURE (non-2xx, timeout, connection error):
   a. Record failure attempt
   b. Check retry count:
      - Retries remaining → schedule retry with exponential backoff
        (produce to "delivery-retry" topic with delay)
      - Max retries exhausted → move to DLQ
   ↓
5. DLQ events are stored for manual inspection/replay
```

### Flow 4: Backpressure & Slow Subscriber Handling
```
1. Delivery Worker detects subscriber is slow:
   - Consecutive failures > threshold (e.g., 10)
   - Webhook latency > SLA (e.g., P99 > 10 seconds)
   ↓
2. Circuit Breaker trips:
   a. Subscription moved to SUSPENDED status
   b. Events for this subscription are buffered in Kafka (not lost)
   c. Periodic health-check pings to webhook URL
   ↓
3. When webhook recovers (health-check succeeds):
   a. Resume delivery from buffered offset
   b. Subscription moved back to ACTIVE
   c. Gradually ramp up delivery rate (slow-start)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EVENT PRODUCERS                                 │
│                                                                               │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐               │
│  │ Service A  │  │ Service B  │  │ IoT Fleet  │  │ Mobile App │               │
│  │ (orders)   │  │ (payments) │  │ (sensors)  │  │ (clickstream)│             │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘             │
│        │               │               │               │                     │
└────────┼───────────────┼───────────────┼───────────────┼─────────────────────┘
         │               │               │               │
         └───────────────┼───────────────┼───────────────┘
                         │  POST /api/v1/events
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          API GATEWAY / LOAD BALANCER                          │
│                                                                               │
│  • TLS termination          • Rate limiting (per-tenant token bucket)        │
│  • Authentication (API key) • Request validation (schema, size)              │
│  • Geographic routing       • DDoS protection                                │
└───────────────────────────────┬───────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       INGESTION SERVICE                                       │
│                       (Stateless, horizontally scaled — 50-200 pods)          │
│                                                                               │
│  • Validate & enrich events                                                  │
│  • Partition by tenant_id → Kafka                                            │
│  • Batch produce (linger.ms=5, batch.size=64KB)                              │
│  • Return 202 Accepted after Kafka ACK                                       │
└───────────────────────────────┬───────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA CLUSTER                                        │
│                          (Event Log — Source of Truth)                         │
│                                                                               │
│  Topic: "raw-events"                                                         │
│    • Partitions: 500 (partitioned by tenant_id hash)                         │
│    • Replication factor: 3                                                   │
│    • Retention: 7 days (for replay capability)                               │
│    • Throughput: 1M events/sec = ~1 GB/sec                                   │
│                                                                               │
│  Topic: "delivery-tasks"                                                     │
│    • Partitions: 200 (partitioned by subscription_id)                        │
│    • Replication factor: 3                                                   │
│    • Per-subscriber ordering guaranteed                                      │
│                                                                               │
│  Topic: "delivery-retry" (delay topics: 1s, 5s, 30s, 5m, 30m, 5h)          │
│  Topic: "delivery-dlq"                                                       │
└──────────┬──────────────────────────────┬────────────────────────────────────┘
           │                              │
     ┌─────▼──────────────┐        ┌─────▼──────────────┐
     │ FILTER WORKERS      │        │ DELIVERY WORKERS    │
     │ (Matching Engine)   │        │ (Webhook Dispatch)  │
     │                     │        │                     │
     │ • Consume raw-events│        │ • Consume delivery  │
     │ • Load subscription │        │   tasks             │
     │   rules from cache  │        │ • HMAC-sign payload │
     │ • Evaluate filters  │        │ • HTTP POST to      │
     │   (< 1ms per rule)  │        │   webhook URL       │
     │ • Produce delivery  │        │ • Handle retries /  │
     │   tasks to Kafka    │        │   backoff / DLQ     │
     │                     │        │ • Circuit breaker   │
     │ Pods: 50-100        │        │   for slow webhooks │
     │ (one per partition  │        │                     │
     │  group)             │        │ Pods: 100-300       │
     └─────────────────────┘        └──────────┬──────────┘
                                               │
                                               ▼
                                    ┌────────────────────┐
                                    │ SUBSCRIBER WEBHOOKS │
                                    │                     │
                                    │ https://app1.com/wh │
                                    │ https://app2.com/wh │
                                    │ https://app3.com/wh │
                                    │ ...100K endpoints   │
                                    └────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        SUPPORTING SERVICES                                    │
│                                                                               │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐       │
│  │ SUBSCRIPTION     │  │ DELIVERY STATUS   │  │ TENANT / QUOTA       │       │
│  │ SERVICE          │  │ SERVICE           │  │ SERVICE              │       │
│  │                  │  │                   │  │                      │       │
│  │ • CRUD for       │  │ • Track delivery  │  │ • Tenant CRUD        │       │
│  │   subscriptions  │  │   attempts        │  │ • Rate limit configs │       │
│  │ • Compile &      │  │ • DLQ management  │  │ • Quota enforcement  │       │
│  │   validate rules │  │ • Replay trigger  │  │ • Usage tracking     │       │
│  │ • Publish to     │  │                   │  │                      │       │
│  │   subscription   │  │ DB: Cassandra     │  │ DB: PostgreSQL       │       │
│  │   cache          │  │ (high write       │  │ Cache: Redis         │       │
│  │                  │  │  throughput)       │  │ (token bucket)       │       │
│  │ DB: PostgreSQL   │  │                   │  │                      │       │
│  │ Cache: Redis     │  │                   │  │                      │       │
│  └─────────────────┘  └──────────────────┘  └──────────────────────┘       │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────┐       │
│  │ SUBSCRIPTION CACHE (Redis / In-Memory)                            │       │
│  │                                                                    │       │
│  │ • All active subscriptions loaded in filter worker memory         │       │
│  │ • Indexed by event type → [subscription_ids] for O(1) pre-filter │       │
│  │ • Updated via Redis pub/sub when subscriptions change             │       │
│  │ • Full refresh every 5 minutes as safety net                      │       │
│  └──────────────────────────────────────────────────────────────────┘       │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────┐       │
│  │ OBSERVABILITY                                                      │       │
│  │                                                                    │       │
│  │ • Metrics: ingestion rate, match rate, delivery success/failure   │       │
│  │ • Tracing: event_id across ingest → filter → deliver              │       │
│  │ • Alerting: DLQ growth, consumer lag, circuit breaker trips       │       │
│  │ • Per-tenant dashboards: usage, quota, delivery health            │       │
│  └──────────────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **API Gateway** | Auth, rate limiting, request routing | Envoy / Kong / AWS ALB | Auto-scaled, multi-region |
| **Ingestion Service** | Validate, enrich, produce to Kafka | Go/Java on K8s | 50-200 stateless pods |
| **Kafka Cluster** | Durable event log, ordering, replay | Apache Kafka | 30-50 brokers, 500 partitions |
| **Filter Workers** | Content-based matching: event × subscriptions | Java on K8s | 50-100 pods (1 per partition group) |
| **Delivery Workers** | Webhook HTTP dispatch + retry/backoff/DLQ | Go on K8s | 100-300 pods (I/O bound) |
| **Subscription Service** | CRUD for subscriptions, rule compilation | Java on K8s | 5-10 pods |
| **Subscription Cache** | In-memory subscription index for filter workers | Redis + local cache | Replicated, ~500 MB |
| **Delivery Status DB** | Delivery attempts, DLQ storage | Cassandra | 5-node cluster (high write throughput) |
| **Tenant/Quota Service** | Rate limits, quotas, tenant management | Java on K8s + Redis | 3-5 pods |
| **Observability** | Metrics, tracing, alerting | Prometheus + Grafana + Jaeger | Dedicated cluster |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Content-Based Filter Engine — Efficient Rule Evaluation at 1M Events/Sec

**The Problem**: With 100K active subscriptions and 1M events/sec, naively evaluating every event against every subscription = 100 billion evaluations/sec. This is not feasible. We need a smart filtering strategy.

```java
public class FilterEngine {

    /**
     * Three-level filtering strategy:
     * 
     * Level 1: TYPE INDEX (coarse filter)
     *   - Most subscriptions filter on event "type" (e.g., type == "order.created")
     *   - Build an inverted index: event_type → [subscription_ids]
     *   - For each event, lookup its type → get candidate subscriptions (O(1))
     *   - Eliminates 90-95% of subscriptions from evaluation
     * 
     * Level 2: TENANT SCOPING
     *   - Subscriptions belong to tenants. Events belong to tenants.
     *   - Only evaluate subscriptions belonging to the event's tenant
     *   - Further reduces candidate set by 99.99% (10K tenants → 10 subs/tenant)
     * 
     * Level 3: FULL RULE EVALUATION (fine filter)
     *   - For remaining candidates, evaluate the full compiled filter rule
     *   - Pre-compiled to bytecode/AST for fast evaluation (< 1ms per rule)
     *   - Typically 1-5 candidates per event after Level 1 + Level 2
     */
    
    // In-memory index: (tenant_id, event_type) → List<CompiledSubscription>
    private final Map<String, Map<String, List<CompiledSubscription>>> subscriptionIndex;
    
    // Wildcard subscriptions: tenant_id → List<CompiledSubscription> (no type filter)
    private final Map<String, List<CompiledSubscription>> wildcardSubscriptions;
    
    public List<MatchedSubscription> matchEvent(Event event) {
        List<MatchedSubscription> matches = new ArrayList<>();
        String tenantId = event.getTenantId();
        String eventType = event.getType();
        
        // Level 1 + 2: Get candidate subscriptions for this tenant + event type
        List<CompiledSubscription> candidates = new ArrayList<>();
        
        Map<String, List<CompiledSubscription>> tenantIndex = subscriptionIndex.get(tenantId);
        if (tenantIndex != null) {
            List<CompiledSubscription> typed = tenantIndex.get(eventType);
            if (typed != null) candidates.addAll(typed);
        }
        
        // Add wildcard subscriptions (no type filter) for this tenant
        List<CompiledSubscription> wildcards = wildcardSubscriptions.get(tenantId);
        if (wildcards != null) candidates.addAll(wildcards);
        
        // Level 3: Evaluate full filter rule for each candidate
        for (CompiledSubscription sub : candidates) {
            if (sub.getCompiledRule().evaluate(event)) {
                matches.add(new MatchedSubscription(sub.getSubscriptionId(), sub.getWebhook()));
            }
        }
        
        return matches;
    }
    
    /**
     * Rule compilation: parse filter expression into an AST at subscription creation time.
     * At evaluation time, walk the AST and evaluate against event fields.
     * 
     * Example: "type == 'order.created' AND payload.amount > 100"
     * 
     * AST:
     *   AND
     *   ├── EQ(field="type", value="order.created")
     *   └── GT(field="payload.amount", value=100)
     * 
     * Evaluation: extract field values from event JSON, compare against AST nodes.
     * Short-circuit: AND stops on first false, OR stops on first true.
     */
    public CompiledRule compileRule(String filterExpression) {
        // Tokenize → Parse → Build AST → Validate field paths → Return compiled rule
        List<Token> tokens = tokenize(filterExpression);
        ASTNode ast = parse(tokens);
        validate(ast);  // Check field names, types, operator compatibility
        return new CompiledRule(ast);
    }
}
```

**Performance analysis**:
```
Without indexing:
  1M events/sec × 100K subscriptions × 1ms per eval = 100B ms of CPU/sec
  → Impossible (need 100K CPU cores)

With 3-level indexing:
  Level 1 (type index): 100K subs → ~100 per event type (1000 unique types)
  Level 2 (tenant scope): 100 → ~10 per tenant (10 tenants share a type)
  Level 3 (rule eval): 10 subs × 0.5ms = 5ms per event
  
  1M events/sec × 5ms = 5,000 CPU-seconds/sec → 5,000 / 1 = ~5,000 cores
  With 100 filter worker pods × 64 cores each = 6,400 cores available ✓
  
  Actual: most events have 1-3 candidate subscriptions → ~1ms per event
  → 50-100 filter worker pods are sufficient
```

---

### Deep Dive 2: Webhook Delivery — Reliable At-Least-Once with Retry & DLQ

**The Problem**: Subscriber webhooks are unreliable. They may return 5xx, timeout, or be temporarily down. We must guarantee at-least-once delivery with exponential backoff, while not blocking healthy subscribers.

```java
public class WebhookDeliveryService {

    /**
     * Delivery pipeline:
     * 
     * 1. Delivery Worker consumes from "delivery-tasks" topic
     * 2. For each task: build HTTP request, sign with HMAC, POST to webhook
     * 3. On success (2xx): commit offset, record success
     * 4. On failure: schedule retry with exponential backoff
     * 5. After max retries: move to DLQ
     * 
     * Retry schedule (exponential backoff with jitter):
     *   Attempt 1: immediate
     *   Attempt 2: 1 second
     *   Attempt 3: 5 seconds
     *   Attempt 4: 30 seconds
     *   Attempt 5: 5 minutes
     *   Attempt 6: 30 minutes (max)
     *   → Total retry window: ~36 minutes
     * 
     * Implementation: Kafka delay topics (one per delay bucket)
     *   "delivery-retry-1s", "delivery-retry-5s", "delivery-retry-30s",
     *   "delivery-retry-5m", "delivery-retry-30m"
     *   
     *   Each delay topic has a consumer that waits until the message's
     *   scheduled_at timestamp before processing it.
     */
    
    private static final int MAX_RETRIES = 6;
    private static final int[] BACKOFF_SECONDS = {0, 1, 5, 30, 300, 1800};
    
    public DeliveryResult deliver(DeliveryTask task) {
        // Build HTTP request
        HttpRequest request = buildWebhookRequest(task);
        
        try {
            HttpResponse response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // SUCCESS
                recordDelivery(task, DeliveryStatus.SUCCESS, response.statusCode(), null);
                return DeliveryResult.SUCCESS;
            } else {
                // Non-2xx: retry
                return handleFailure(task, response.statusCode(), 
                    "HTTP " + response.statusCode());
            }
        } catch (HttpTimeoutException e) {
            return handleFailure(task, 0, "Timeout after " + task.getTimeoutMs() + "ms");
        } catch (IOException e) {
            return handleFailure(task, 0, "Connection error: " + e.getMessage());
        }
    }
    
    private DeliveryResult handleFailure(DeliveryTask task, int httpStatus, String error) {
        int attempt = task.getAttemptNumber();
        recordDelivery(task, DeliveryStatus.FAILED, httpStatus, error);
        
        if (attempt >= MAX_RETRIES) {
            // Move to DLQ
            kafkaProducer.send("delivery-dlq", task);
            recordDelivery(task, DeliveryStatus.DLQ, httpStatus, 
                "Max retries exhausted: " + error);
            return DeliveryResult.DLQ;
        }
        
        // Schedule retry with backoff + jitter
        int delaySeconds = BACKOFF_SECONDS[Math.min(attempt, BACKOFF_SECONDS.length - 1)];
        int jitter = ThreadLocalRandom.current().nextInt(0, delaySeconds / 4 + 1);
        
        DeliveryTask retryTask = task.withNextAttempt(
            Instant.now().plusSeconds(delaySeconds + jitter));
        
        String retryTopic = selectRetryTopic(delaySeconds);
        kafkaProducer.send(retryTopic, retryTask);
        
        return DeliveryResult.RETRY_SCHEDULED;
    }
    
    private HttpRequest buildWebhookRequest(DeliveryTask task) {
        String payload = serialize(task.getEventPayload());
        String timestamp = Instant.now().toString();
        
        // HMAC signature for webhook verification
        String signature = computeHmac(
            task.getWebhookSecret(), timestamp + "." + payload);
        
        return HttpRequest.newBuilder()
            .uri(URI.create(task.getWebhookUrl()))
            .timeout(Duration.ofMillis(task.getTimeoutMs()))
            .header("Content-Type", "application/json")
            .header("X-Event-Id", task.getEventId())
            .header("X-Subscription-Id", task.getSubscriptionId())
            .header("X-Timestamp", timestamp)
            .header("X-Signature", "sha256=" + signature)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
    }
}
```

**Retry delay implementation with Kafka**:
```
Approach A: Kafka delay topics (chosen)
  - Separate topic per delay bucket: retry-1s, retry-5s, retry-30s, retry-5m, retry-30m
  - Consumer on each topic checks: if now() < scheduled_at → sleep/wait
  - Pros: simple, durable, uses existing Kafka infrastructure
  - Cons: delay granularity limited to bucket sizes

Approach B: External scheduler (e.g., Redis ZSET with scheduled_at as score)
  - ZADD retry-queue <scheduled_at> <task_json>
  - Worker polls: ZRANGEBYSCORE retry-queue 0 <now> LIMIT 10
  - Pros: precise delay, single queue
  - Cons: additional Redis dependency, durability concerns

Approach C: Database-backed scheduler (PostgreSQL + pg_cron)
  - INSERT INTO retry_queue (task, execute_at)
  - Cron job: SELECT * FROM retry_queue WHERE execute_at <= NOW()
  - Pros: transactional, auditable
  - Cons: not designed for high-throughput scheduling (110K/sec)
```

---

### Deep Dive 3: Multi-Tenant Isolation — Data, Compute, and Rate Limiting

**The Problem**: In a multi-tenant system, one tenant publishing 500K events/sec must not starve other tenants. One tenant's slow webhook must not block another tenant's deliveries. Data must be strictly isolated.

```java
public class TenantIsolationManager {

    /**
     * Three dimensions of tenant isolation:
     * 
     * 1. DATA ISOLATION (Hard)
     *    - Events are tagged with tenant_id at ingestion
     *    - Subscriptions are scoped to tenant_id
     *    - Filter workers only evaluate subscriptions of the event's tenant
     *    - No cross-tenant data access possible (enforced in all queries)
     * 
     * 2. COMPUTE ISOLATION (Soft — Fair-Share)
     *    - Kafka partitioning by tenant_id → spread tenant data across partitions
     *    - If one tenant produces 50% of events, they occupy ~50% of partitions
     *    - Per-tenant token bucket rate limiting at ingestion (API Gateway)
     *    - Weighted fair queuing in delivery workers (prevent one tenant's
     *      retries from consuming all delivery capacity)
     * 
     * 3. RESOURCE QUOTAS (Hard Limits)
     *    - Max events/sec per tenant (rate limit)
     *    - Max event size (256 KB)
     *    - Max subscriptions per tenant
     *    - Max daily event volume
     *    - Max webhook timeout (prevent one tenant from holding connections open)
     */
    
    // Rate limiting: Token bucket per tenant (stored in Redis)
    public boolean checkRateLimit(String tenantId, int eventCount) {
        String key = "ratelimit:" + tenantId;
        TenantQuota quota = quotaService.getQuota(tenantId);
        
        // Redis token bucket: EVALSHA script
        // Returns: tokens remaining (or -1 if rate limited)
        long tokensRemaining = redis.eval(TOKEN_BUCKET_SCRIPT, 
            key, 
            String.valueOf(quota.getMaxEventsPerSecond()),  // bucket capacity
            String.valueOf(quota.getMaxEventsPerSecond()),  // refill rate
            String.valueOf(eventCount));                     // tokens requested
        
        if (tokensRemaining < 0) {
            metricsService.recordRateLimit(tenantId);
            throw new RateLimitException(
                "Tenant " + tenantId + " exceeded rate limit: " + 
                quota.getMaxEventsPerSecond() + " events/sec");
        }
        return true;
    }
    
    // Weighted fair queuing for delivery workers
    // Prevents one tenant's massive fanout from starving others
    public class FairShareDeliveryScheduler {
        
        // Each tenant gets a weighted share of delivery capacity
        // Weight = 1 / (current_queue_depth) → smaller queues get priority
        private final Map<String, PriorityQueue<DeliveryTask>> perTenantQueues;
        
        public DeliveryTask getNextTask() {
            // Round-robin across tenants with pending tasks
            // With weight: tenants with fewer pending tasks get served more often
            // This prevents a tenant with 1M pending deliveries from blocking
            // a tenant with 10 pending deliveries
            String nextTenant = selectNextTenant();
            return perTenantQueues.get(nextTenant).poll();
        }
    }
}
```

**Kafka partition strategy for tenant isolation**:
```
Problem: 10K tenants, 500 Kafka partitions
  → Multiple tenants share partitions (hash collision)
  → A "noisy" tenant (500K events/sec) can fill a partition and delay
    events from co-located tenants

Solutions:
  Option A: Dedicated partitions for large tenants (chosen)
    - Top 100 tenants (by volume) get dedicated partitions
    - Remaining tenants hash to shared partitions
    - Monitored: if a tenant's volume exceeds threshold → migrate to dedicated partition

  Option B: Tenant-per-topic (one Kafka topic per tenant)
    - Perfect isolation
    - But: 10K topics × 3 replicas = 30K partitions → Kafka metadata overhead
    - Operational nightmare at scale

  Option C: Priority lanes (separate topics for tiers)
    - "raw-events-premium" (SLA: < 2s E2E, dedicated partitions)
    - "raw-events-standard" (SLA: < 5s E2E, shared partitions)
    - "raw-events-burst" (best-effort, lowest priority)
    - Tenants assigned to lanes based on tier/plan
```

---

### Deep Dive 4: Sharding & Scalability — Handling 1M Events/Sec

**The Problem**: 1M events/sec × 1 KB = 1 GB/sec ingestion. How do we horizontally scale every component?

```java
public class ScalabilityStrategy {

    /**
     * Component-by-component scaling:
     * 
     * INGESTION (stateless — easiest to scale):
     *   - 50-200 pods behind load balancer
     *   - Each pod handles ~5K-20K events/sec
     *   - Scale trigger: CPU > 60% or latency P99 > 50ms
     *   - Kafka producer per pod, batched (linger.ms=5, batch.size=64KB)
     * 
     * KAFKA (partition-based scaling):
     *   - 500 partitions for "raw-events" topic
     *   - Each partition handles ~2K events/sec
     *   - 30-50 brokers (each broker manages ~10-15 partitions)
     *   - Scale by adding brokers + rebalancing partitions
     *   - Throughput: 1 GB/sec write + 3 GB/sec replication = ~4 GB/sec total
     *   - Disk: 600 TB / 7 days retention → ~100 TB per broker (with replication)
     * 
     * FILTER WORKERS (CPU-bound — scales with partitions):
     *   - Consumer group with 50-100 consumers
     *   - Each consumer processes ~10-20K events/sec
     *   - Scale trigger: consumer lag > 10K messages
     *   - Stateless (subscriptions loaded from cache)
     *   - Max consumers = number of partitions (500)
     * 
     * DELIVERY WORKERS (I/O-bound — scales with concurrent connections):
     *   - 100-300 pods with async HTTP client
     *   - Each pod maintains ~500 concurrent webhook connections
     *   - Total: 100K concurrent connections → 100K webhook calls/sec
     *   - Scale trigger: delivery queue depth > threshold or latency spike
     *   - Uses connection pooling per webhook domain
     */
    
    // Auto-scaling configuration
    public static final ScalingConfig INGESTION_SCALING = ScalingConfig.builder()
        .minPods(50).maxPods(200)
        .scaleUpThreshold("cpu > 60% || p99_latency > 50ms")
        .scaleDownThreshold("cpu < 30% && p99_latency < 20ms")
        .cooldownSeconds(300)
        .build();
    
    public static final ScalingConfig FILTER_SCALING = ScalingConfig.builder()
        .minPods(50).maxPods(500)   // max = number of Kafka partitions
        .scaleUpThreshold("consumer_lag > 10000 || processing_time_p99 > 100ms")
        .scaleDownThreshold("consumer_lag < 1000 && processing_time_p99 < 20ms")
        .cooldownSeconds(120)
        .build();
    
    public static final ScalingConfig DELIVERY_SCALING = ScalingConfig.builder()
        .minPods(100).maxPods(500)
        .scaleUpThreshold("pending_deliveries > 50000 || delivery_latency_p99 > 3s")
        .scaleDownThreshold("pending_deliveries < 5000 && delivery_latency_p99 < 500ms")
        .cooldownSeconds(120)
        .build();
}
```

**Kafka partition design**:
```
Topic: "raw-events" (500 partitions)
  Partition key: hash(tenant_id)
  Why tenant_id: ensures all events from same tenant go to same partition
    → preserves per-tenant ordering
    → filter worker only needs subscriptions for tenants in its partitions
    → reduces subscription cache size per worker

Topic: "delivery-tasks" (200 partitions)
  Partition key: hash(subscription_id)
  Why subscription_id: ensures per-subscriber ordering
    → events delivered in order to each webhook
    → one delivery worker handles all deliveries for a subscription
    → natural load distribution (subscriptions spread across partitions)

Topic: "delivery-retry-*" (50 partitions each)
  Partition key: hash(subscription_id) (same as delivery-tasks)
  Why: maintains per-subscriber ordering even during retries
```

---

### Deep Dive 5: Backpressure & Circuit Breaker — Handling Slow/Dead Webhooks

**The Problem**: A subscriber's webhook goes down. Without backpressure, the delivery worker retries infinitely, the retry queue grows unbounded, and healthy subscribers get starved for delivery capacity.

```java
public class WebhookCircuitBreaker {

    /**
     * Per-subscription circuit breaker with three states:
     * 
     * CLOSED (normal): deliveries flow normally
     *   → Transition to OPEN when: 10 consecutive failures OR
     *     failure rate > 50% in last 100 deliveries
     * 
     * OPEN (tripped): deliveries paused, events buffered in Kafka
     *   → Health-check probe sent every 30 seconds
     *   → Transition to HALF_OPEN when: health-check succeeds
     *   → Auto-close after 24 hours of OPEN → SUSPENDED (admin intervention)
     * 
     * HALF_OPEN (testing): send 1 event to test recovery
     *   → If success: transition to CLOSED (with slow-start ramp)
     *   → If failure: transition back to OPEN
     * 
     * Key: events are NOT lost during OPEN state.
     * They accumulate in Kafka (delivery-tasks topic, committed offset not advanced).
     * When circuit closes, delivery resumes from the buffered offset.
     */
    
    private final Map<String, CircuitState> subscriptionCircuits = new ConcurrentHashMap<>();
    
    public boolean shouldDeliver(String subscriptionId) {
        CircuitState state = subscriptionCircuits.getOrDefault(
            subscriptionId, CircuitState.CLOSED);
        
        switch (state.getStatus()) {
            case CLOSED:
                return true;
            case OPEN:
                if (state.shouldProbe()) {
                    state.transitionTo(CircuitStatus.HALF_OPEN);
                    return true;  // Allow one probe delivery
                }
                return false;  // Buffer in Kafka
            case HALF_OPEN:
                return false;  // Wait for probe result
            default:
                return false;
        }
    }
    
    public void recordResult(String subscriptionId, boolean success) {
        CircuitState state = subscriptionCircuits.get(subscriptionId);
        
        if (state.getStatus() == CircuitStatus.HALF_OPEN) {
            if (success) {
                state.transitionTo(CircuitStatus.CLOSED);
                // Slow-start: gradually ramp up delivery rate
                deliveryRateLimiter.setRate(subscriptionId, 10);  // Start at 10/sec
                // Ramp: 10 → 50 → 200 → unlimited over 5 minutes
            } else {
                state.transitionTo(CircuitStatus.OPEN);
            }
        } else if (state.getStatus() == CircuitStatus.CLOSED) {
            state.recordResult(success);
            if (state.shouldTrip()) {
                state.transitionTo(CircuitStatus.OPEN);
                alertService.sendAlert("Circuit breaker tripped for subscription " + 
                    subscriptionId + ": " + state.getFailureReason());
            }
        }
    }
    
    // Health-check: lightweight probe to see if webhook is back
    @Scheduled(fixedRate = 30_000)  // Every 30 seconds
    public void healthCheckOpenCircuits() {
        for (var entry : subscriptionCircuits.entrySet()) {
            if (entry.getValue().getStatus() == CircuitStatus.OPEN) {
                String url = getHealthCheckUrl(entry.getKey());
                boolean healthy = probeWebhook(url);
                if (healthy) {
                    entry.getValue().markProbeSuccess();
                }
            }
        }
    }
}
```

**Backpressure propagation**:
```
Subscriber slow → Delivery worker detects failure
  ↓
Circuit breaker OPEN → Delivery worker stops consuming for this subscription
  ↓
Kafka offset for this subscription's partition not advancing
  ↓
But OTHER subscriptions on the same partition ARE advancing
  ↓
Problem: Kafka offsets are per-partition, not per-subscription!

Solution: Separate delivery-tasks partitioning by subscription_id
  - Each subscription gets its own partition (or shared partition group)
  - Circuit breaker pauses consumption for affected partitions only
  - Healthy subscriptions on other partitions are unaffected

Alternative: Application-level buffering
  - Delivery worker maintains per-subscription in-memory queues
  - On circuit break: buffer tasks, stop webhook calls
  - On recovery: flush buffer
  - Risk: memory pressure if many subscriptions go down simultaneously
  - Mitigation: spill to local disk or dedicated overflow topic
```

---

### Deep Dive 6: Ordering and Delivery Semantics

**The Problem**: Some subscribers need events in order (e.g., `order.created` before `order.shipped`). Others need exactly-once (no duplicate deliveries). How do we provide ordering guarantees and support idempotent delivery?

```java
public class DeliverySemantics {

    /**
     * ORDERING GUARANTEES:
     * 
     * Per-tenant ordering (guaranteed):
     *   - Events from the same tenant go to the same Kafka partition (hash(tenant_id))
     *   - Filter worker processes events per-partition in order
     *   - Delivery tasks partitioned by subscription_id → per-subscriber ordering
     *   - Result: events for a given (tenant, subscription) pair delivered in order
     * 
     * Global ordering (NOT guaranteed):
     *   - Events from different tenants may interleave
     *   - This is acceptable: tenants don't share subscriptions
     * 
     * Causal ordering (best-effort):
     *   - If producer sends events with timestamps, we sort by timestamp
     *   - Not guaranteed across partitions (clock skew between producers)
     * 
     * AT-LEAST-ONCE DELIVERY:
     *   - Default guarantee. Delivery worker commits Kafka offset AFTER
     *     successful webhook delivery.
     *   - If worker crashes after delivery but before commit → event re-delivered
     *   - Subscribers MUST handle duplicates (idempotency)
     * 
     * EXACTLY-ONCE DELIVERY (optional, subscriber-side):
     *   - Each webhook call includes X-Event-Id and X-Delivery-Id headers
     *   - Subscriber uses X-Event-Id as idempotency key
     *   - Our side: we don't guarantee exactly-once (network partitions make it impossible)
     */
    
    // Idempotency support: include unique delivery ID in webhook call
    public HttpRequest buildIdempotentWebhookRequest(DeliveryTask task) {
        String deliveryId = task.getSubscriptionId() + ":" + task.getEventId();
        
        return HttpRequest.newBuilder()
            .uri(URI.create(task.getWebhookUrl()))
            .header("X-Event-Id", task.getEventId())
            .header("X-Delivery-Id", deliveryId)  // Unique per (subscription, event)
            .header("X-Attempt-Number", String.valueOf(task.getAttemptNumber()))
            .header("X-Timestamp", Instant.now().toString())
            .header("X-Signature", computeSignature(task))
            .POST(HttpRequest.BodyPublishers.ofString(serialize(task.getEventPayload())))
            .build();
    }
    
    /**
     * Ordering within delivery worker:
     * 
     * Per-subscription sequential delivery:
     *   - Delivery worker processes tasks for a subscription ONE AT A TIME
     *   - Wait for response before sending next event
     *   - Preserves order: event_1, event_2, event_3 → delivered in sequence
     * 
     * Trade-off: sequential delivery limits per-subscription throughput
     *   - Max: ~200 deliveries/sec per subscription (at 5ms round-trip)
     *   - For high-volume subscribers: offer "unordered" mode
     *     (parallel delivery, higher throughput, subscriber sorts by timestamp)
     */
    public enum DeliveryMode {
        ORDERED,    // Sequential per subscription (default)
        UNORDERED   // Parallel per subscription (higher throughput)
    }
}
```

---

### Deep Dive 7: Security — Authentication, Authorization, Encryption, and Webhook Verification

**The Problem**: Multi-tenant event systems handle sensitive data. We must authenticate producers, authorize subscribers, encrypt data in transit and at rest, and allow subscribers to verify webhook authenticity.

```java
public class SecurityManager {

    /**
     * Security layers:
     * 
     * 1. PRODUCER AUTHENTICATION (ingestion)
     *    - API key authentication: X-Api-Key header
     *    - Each tenant gets a unique API key (rotatable)
     *    - API key → tenant_id mapping stored in Redis (< 1ms lookup)
     *    - Keys are hashed (bcrypt) in DB, plaintext only in Redis cache
     *    - Mutual TLS (mTLS) for high-security tenants
     * 
     * 2. SUBSCRIBER AUTHORIZATION
     *    - Subscribers can only create subscriptions for their own tenant
     *    - Filter rules can only match events from their tenant
     *    - Tenant admin can manage subscriptions for all users in tenant
     *    - Role-based: ADMIN (full control), USER (read + create own subs)
     * 
     * 3. WEBHOOK VERIFICATION (subscriber-side)
     *    - Each webhook delivery is HMAC-SHA256 signed
     *    - Subscriber provides a secret at subscription creation
     *    - Signature: HMAC(secret, timestamp + "." + payload)
     *    - Subscriber verifies: recompute HMAC, compare with X-Signature header
     *    - Timestamp check: reject if > 5 minutes old (replay protection)
     * 
     * 4. DATA ENCRYPTION
     *    - In transit: TLS 1.3 for all API calls and webhook deliveries
     *    - At rest: Kafka data encrypted (AES-256), managed keys per tenant
     *    - Webhook secrets: encrypted in DB (envelope encryption with KMS)
     * 
     * 5. WEBHOOK URL VALIDATION
     *    - Only HTTPS URLs allowed (no HTTP, no localhost, no internal IPs)
     *    - DNS resolution check: ensure URL resolves to public IP
     *    - Blocklist: no AWS metadata endpoints, no internal networks
     *    - Initial verification: POST a challenge to URL, subscriber must respond
     */
    
    // Webhook signature generation
    public String signWebhookPayload(String secret, String timestamp, String payload) {
        String signedContent = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(signedContent.getBytes(UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
    
    // Webhook URL validation (prevent SSRF)
    public void validateWebhookUrl(String url) {
        URI uri = URI.create(url);
        
        // Must be HTTPS
        if (!"https".equals(uri.getScheme())) {
            throw new InvalidWebhookException("Only HTTPS URLs allowed");
        }
        
        // Resolve DNS and check IP
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || 
                addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
                throw new InvalidWebhookException(
                    "Webhook URL must not resolve to private/internal IP: " + addr);
            }
        }
        
        // Block known dangerous endpoints
        String host = uri.getHost().toLowerCase();
        if (BLOCKED_HOSTS.contains(host) || host.endsWith(".internal") || 
            host.equals("169.254.169.254")) {  // AWS metadata
            throw new InvalidWebhookException("Blocked host: " + host);
        }
    }
    
    // Webhook ownership verification (initial challenge)
    public void verifyWebhookOwnership(String url, String secret) {
        String challenge = UUID.randomUUID().toString();
        
        // POST challenge to webhook URL
        HttpResponse response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Verification-Challenge", challenge)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"type\":\"url_verification\",\"challenge\":\"" + challenge + "\"}"))
                .build());
        
        // Subscriber must echo back the challenge
        if (response.statusCode() != 200 || 
            !response.body().contains(challenge)) {
            throw new WebhookVerificationException(
                "Webhook failed ownership verification at: " + url);
        }
    }
}
```

---

### Deep Dive 8: Observability — Monitoring a 1M Events/Sec Pipeline

**The Problem**: With 1M events/sec flowing through multiple stages (ingest → filter → deliver), we need deep observability to detect issues before they cascade. A 1% drop in delivery success rate = 1,000 failed deliveries/sec.

```java
public class ObservabilityStrategy {

    /**
     * Four pillars of observability for this system:
     * 
     * 1. METRICS (Prometheus + Grafana)
     *    - Ingestion: events_ingested_total, ingestion_latency_p50/p99/p999,
     *      events_rejected_total (by reason: rate_limit, validation, auth)
     *    - Filtering: events_matched_total, filter_evaluation_time_ms,
     *      subscriptions_evaluated_per_event
     *    - Delivery: deliveries_total (by status: success, failed, dlq),
     *      delivery_latency_p50/p99, webhook_response_time_ms (by subscriber),
     *      active_circuit_breakers, retry_queue_depth
     *    - Kafka: consumer_lag (by topic/partition), produce_latency,
     *      partition_throughput
     *    - Per-tenant: events_per_tenant, deliveries_per_tenant, rate_limit_hits
     * 
     * 2. DISTRIBUTED TRACING (Jaeger / OpenTelemetry)
     *    - Each event carries a trace_id through the pipeline:
     *      Ingest → Kafka → Filter → Kafka → Deliver → Webhook
     *    - Span per stage: ingest_span, filter_span, deliver_span
     *    - Sample rate: 0.1% of events (at 1M/sec = 1K traces/sec)
     *    - 100% tracing for failed deliveries (always capture failures)
     * 
     * 3. ALERTING (PagerDuty / OpsGenie)
     *    - P1: ingestion_error_rate > 1% for 5 min
     *    - P1: consumer_lag > 100K messages for 10 min
     *    - P2: delivery_success_rate < 95% for 15 min
     *    - P2: DLQ growth > 1000/min for 10 min
     *    - P3: circuit_breaker_trips > 10 in 5 min
     *    - P3: per-tenant rate limit hits > 50% of quota
     * 
     * 4. DASHBOARDS
     *    - System health: ingestion rate, match rate, delivery rate (real-time)
     *    - Tenant dashboard: per-tenant events, deliveries, failures, quota usage
     *    - Subscriber health: per-subscription delivery success rate, latency
     *    - Capacity planning: Kafka disk usage, partition throughput, pod counts
     */
    
    // Key SLIs (Service Level Indicators)
    public static final Map<String, String> SLI_DEFINITIONS = Map.of(
        "ingestion_availability", "% of ingestion requests that return 202 within 100ms",
        "delivery_success_rate", "% of matched events delivered successfully within 5s",
        "filtering_freshness", "% of events matched within 1s of ingestion",
        "dlq_rate", "% of delivery attempts that end in DLQ (target: < 0.1%)"
    );
    
    // SLOs (Service Level Objectives)
    public static final Map<String, Double> SLO_TARGETS = Map.of(
        "ingestion_availability", 0.9999,   // 99.99%
        "delivery_success_rate", 0.999,     // 99.9%
        "filtering_freshness", 0.999,       // 99.9%
        "dlq_rate", 0.001                   // < 0.1%
    );
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Event bus** | Apache Kafka (durable log) | Durable, ordered, replayable; 1M events/sec proven; partition-based scaling |
| **Ingestion model** | Fire-and-forget (202 Accepted) | Lowest latency for producers; durability guaranteed by Kafka acks=all |
| **Filtering** | 3-level index (type → tenant → rule eval) | Reduces 100K subs to ~1-5 candidates per event; O(1) type lookup + O(1) tenant scope |
| **Filter rules** | Pre-compiled AST evaluated at runtime | < 1ms per rule; compiled once at subscription creation; supports nested JSON fields |
| **Delivery** | At-least-once with exponential backoff + DLQ | Simple, robust; exactly-once is impractical for webhooks; subscribers handle dedup |
| **Retry mechanism** | Kafka delay topics (bucketed) | Durable, uses existing infra; no external scheduler needed; trades precision for simplicity |
| **Backpressure** | Per-subscription circuit breaker (3-state) | Isolates slow subscribers; events buffered in Kafka (not lost); auto-recovery with slow-start |
| **Tenant isolation** | Data: hard (tenant_id everywhere) / Compute: fair-share (weighted queuing) | Prevents noisy neighbor; rate limits at ingestion; weighted delivery scheduling |
| **Partition strategy** | raw-events: hash(tenant_id), delivery-tasks: hash(subscription_id) | Per-tenant ordering for events; per-subscriber ordering for deliveries |
| **Delivery status** | Cassandra (high write throughput) | 110K writes/sec for delivery attempts; time-series append pattern; TTL for auto-cleanup |
| **Webhook security** | HMAC-SHA256 signature + HTTPS-only + SSRF protection | Industry standard (Stripe, GitHub style); subscriber verifies authenticity; prevents SSRF |

## Interview Talking Points

1. **"3-level filter index: type → tenant → rule"** — Pre-indexes subscriptions by event type and tenant. Reduces 100K subscriptions to 1-5 candidates per event. Full AST evaluation only on candidates. Makes 1M events/sec feasible with ~100 filter worker pods.

2. **"Kafka as durable event log with 7-day retention"** — Enables replay, guarantees durability after ACK (acks=all, replication=3). Partition by tenant_id for ordering. 500 partitions for 1M events/sec throughput.

3. **"At-least-once delivery with Kafka delay topics for retries"** — Exponential backoff (1s → 5s → 30s → 5m → 30m). Separate delay topic per bucket. DLQ after max retries. Subscribers dedup via X-Event-Id header.

4. **"Per-subscription circuit breaker (CLOSED → OPEN → HALF_OPEN)"** — Trips after 10 consecutive failures. Events buffered in Kafka (not lost). Health-check probes every 30s. Slow-start ramp on recovery. Prevents one dead webhook from starving healthy subscribers.

5. **"Multi-tenant isolation: data (hard) + compute (fair-share)"** — tenant_id in every query, every Kafka message, every subscription. Token bucket rate limiting at ingestion. Weighted fair queuing in delivery workers. Dedicated Kafka partitions for large tenants.

6. **"HMAC-SHA256 webhook signatures + SSRF protection"** — Each delivery signed with subscriber's secret. Timestamp in signature prevents replay. HTTPS-only, no internal IPs, DNS validation. Ownership verification challenge on subscription creation.

7. **"Two Kafka topics separate concerns: raw-events (by tenant) and delivery-tasks (by subscription)"** — raw-events partitioned by tenant_id for per-tenant ordering during filtering. delivery-tasks partitioned by subscription_id for per-subscriber ordered delivery. Decouples ingestion scale from delivery scale.

8. **"Observability: SLIs for ingestion availability (99.99%), delivery success (99.9%), DLQ rate (< 0.1%)"** — Distributed tracing (0.1% sample, 100% on failures). Per-tenant dashboards. Consumer lag alerts. Circuit breaker trip alerts.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Webhook Delivery System (Stripe/GitHub)** | Same delivery pattern | Stripe is single-tenant; our system is multi-tenant with content-based filtering |
| **Message Queue (Kafka/SQS)** | Same event streaming backbone | We add content-based routing on top of raw pub/sub |
| **Notification System** | Same push-based delivery | Notifications target users (mobile/email); we target webhook endpoints |
| **Pub/Sub (Google Cloud Pub/Sub)** | Same publish-subscribe model | GCP Pub/Sub uses topic-based routing; we add content-based filtering rules |
| **Event-Driven Architecture (AWS EventBridge)** | Closest analog | EventBridge has content-based rules + targets; we design the internals |
| **Rate Limiter** | Same token bucket pattern | Our rate limiter is per-tenant at ingestion; shared pattern with standalone rate limiter |
| **Circuit Breaker** | Same resilience pattern | Applied per-subscription for webhook health; same states (closed/open/half-open) |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Event bus** | Apache Kafka | AWS Kinesis / Pulsar | Kinesis: serverless, less ops; Pulsar: built-in multi-tenancy, tiered storage |
| **Filter engine** | Custom AST evaluator | CEL (Common Expression Language) / Rego (OPA) | CEL: Google standard, well-tested; Rego: policy-as-code, powerful but heavier |
| **Delivery status DB** | Cassandra | DynamoDB / ScyllaDB | DynamoDB: serverless, no ops; ScyllaDB: Cassandra-compatible, lower latency |
| **Rate limiting** | Redis token bucket | Envoy rate limiting / AWS WAF | Envoy: L7 proxy integrated; WAF: managed, less custom |
| **Retry scheduler** | Kafka delay topics | SQS delay queues / Temporal | SQS: built-in delay up to 15min; Temporal: complex workflows, durable timers |
| **Subscription cache** | Redis + local memory | etcd / Consul | etcd: strong consistency, watch API; Consul: service mesh integrated |
| **Observability** | Prometheus + Grafana + Jaeger | Datadog / New Relic | Managed: less ops overhead; better for smaller teams |
| **API Gateway** | Envoy / Kong | AWS API Gateway / Apigee | Managed: auto-scaling, built-in auth; Apigee: API management features |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Apache Kafka Architecture & Partitioning Strategies
- Content-Based Routing in Event-Driven Systems
- AWS EventBridge Architecture (content-based rules)
- Stripe Webhook Delivery & Retry Design
- Circuit Breaker Pattern (Martin Fowler)
- Multi-Tenant Isolation Patterns (SaaS Architecture)
- HMAC Webhook Signature Verification (GitHub, Stripe, Twilio)
- Kafka Consumer Groups & Offset Management
