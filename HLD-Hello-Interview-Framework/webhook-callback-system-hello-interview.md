# Design a Webhook Callback System - Hello Interview Framework

> **Question**: Design a reliable webhook delivery system that allows services to register callback URLs and receive event notifications via HTTP POST. The system must guarantee at-least-once delivery, handle retries with exponential backoff, support HMAC signature verification, and scale to millions of webhook deliveries per day.
>
> **Asked at**: Stripe, GitHub, Twilio, Shopify, Slack
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

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
1. **Webhook Registration**: Users register callback URLs for specific event types (e.g., "payment.completed", "order.shipped"). Validate URL reachability on registration.
2. **Reliable Delivery**: When an event occurs, deliver an HTTP POST to all registered webhook URLs. Guarantee at-least-once delivery — never silently drop a webhook.
3. **Retry with Exponential Backoff**: If the recipient's server is down or returns a non-2xx response, retry with exponential backoff (1m, 5m, 30m, 2h, 8h, 24h) up to a configurable max attempts.
4. **Security (HMAC Signatures)**: Every webhook request includes an HMAC-SHA256 signature so recipients can verify the payload hasn't been tampered with and originates from us.
5. **Delivery Status & Logs**: Provide a dashboard showing delivery attempts, status (success/failed/pending), response codes, and latency for each webhook delivery.

#### Nice to Have (P1)
- Event filtering (subscribe to specific subtypes: "payment.completed" but not "payment.failed").
- Rate limiting per endpoint (don't overwhelm slow recipient servers).
- Webhook secret rotation (rotate HMAC keys without downtime).
- IP allowlisting (recipients can verify our sending IP range).
- Dead letter queue (after max retries, store failed events for manual replay).
- Batch webhooks (send multiple events in one HTTP request).

#### Below the Line (Out of Scope)
- The event generation system (assume events arrive via internal event bus).
- OAuth/API authentication for the webhook management API.
- Webhook-to-email or webhook-to-SMS conversion.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Delivery Latency** | < 5 seconds from event to first attempt | Recipients expect near-real-time notification |
| **Throughput** | 10M+ deliveries/day (peak ~500/sec) | Platform with 100K+ webhook subscriptions |
| **Delivery Guarantee** | At-least-once | Never lose an event; recipients handle dedup |
| **Availability** | 99.9% for event ingestion | Events must never be dropped |
| **Retry Duration** | Up to 72 hours | Recipient servers may have extended downtime |
| **Security** | HMAC-SHA256 per request | Prevent spoofing and tampering |
| **Observability** | Full delivery log per webhook | Debugging failed deliveries |
| **Max Payload** | 256 KB per webhook | Reasonable for event data |

### Capacity Estimation

```
Scale:
  Registered webhooks: 500K endpoints
  Unique event types: ~100
  Events per day: 5M
  Average subscriptions per event type: 50
  Webhook deliveries per day: 5M × 2 avg subscriptions = 10M deliveries/day
  
  Peak: ~500 deliveries/sec
  With retries: ~600 deliveries/sec (20% retry rate)

Payload:
  Average payload size: 2 KB
  Daily data sent: 10M × 2 KB = ~20 GB/day

Storage:
  Delivery logs: 10M/day × 500 bytes per log = 5 GB/day
  Retention: 30 days → 150 GB
  Event payloads: 5M × 2 KB × 30 days = 300 GB (for replay capability)

Connections:
  Concurrent outbound HTTP connections: ~500 (one per delivery in-flight)
  Connection timeout: 30 seconds
  Average response time from recipients: 500ms
  Worker threads needed: 500 / (1/0.5) = ~250 workers
```

---

## 2️⃣ Core Entities

### Entity 1: Webhook Subscription
```java
public class WebhookSubscription {
    private final String subscriptionId;      // UUID
    private final String userId;              // Who created this subscription
    private final String callbackUrl;         // "https://api.customer.com/webhooks"
    private final Set<String> eventTypes;     // ["payment.completed", "order.shipped"]
    private final String secret;              // HMAC signing secret (generated, shared with user)
    private final SubscriptionStatus status;  // ACTIVE, PAUSED, DISABLED (too many failures)
    private final Instant createdAt;
    private final Instant lastDeliveryAt;
    private final int consecutiveFailures;    // Auto-disable after N failures
    private final Map<String, String> headers; // Custom headers to include in webhook request
}

public enum SubscriptionStatus {
    ACTIVE,     // Receiving webhooks normally
    PAUSED,     // User paused delivery
    DISABLED    // Auto-disabled after too many failures
}
```

### Entity 2: Event
```java
public class WebhookEvent {
    private final String eventId;             // UUID — for idempotency
    private final String eventType;           // "payment.completed"
    private final Instant occurredAt;         // When the event happened
    private final Map<String, Object> payload; // Event data (JSON-serializable)
    private final String sourceService;       // "payment-service"
}
```

### Entity 3: Delivery Attempt
```java
public class DeliveryAttempt {
    private final String attemptId;           // UUID
    private final String eventId;
    private final String subscriptionId;
    private final String callbackUrl;
    private final int attemptNumber;          // 1, 2, 3, ...
    private final DeliveryStatus status;      // PENDING, SUCCESS, FAILED, RETRYING
    private final int httpStatusCode;         // 200, 500, 0 (timeout)
    private final String responseBody;        // First 1 KB of response (for debugging)
    private final Duration latency;           // How long the HTTP call took
    private final Instant attemptedAt;
    private final Instant nextRetryAt;        // When to retry (if failed)
    private final String errorMessage;        // "Connection timeout", "SSL handshake failed"
}

public enum DeliveryStatus {
    PENDING,    // Queued, not yet attempted
    SUCCESS,    // 2xx response received
    FAILED,     // Non-2xx response or error
    RETRYING,   // Failed, scheduled for retry
    EXHAUSTED   // Max retries reached, moved to dead letter queue
}
```

---

## 3️⃣ API Design

### 1. Register Webhook
```
POST /api/v1/webhooks

Request:
{
  "callback_url": "https://api.customer.com/webhooks",
  "event_types": ["payment.completed", "payment.refunded"],
  "custom_headers": { "X-Custom-Header": "my-value" }
}

Response (201 Created):
{
  "subscription_id": "wh_abc123",
  "callback_url": "https://api.customer.com/webhooks",
  "event_types": ["payment.completed", "payment.refunded"],
  "secret": "whsec_5f3a8b2c9d1e4f6a7b8c9d0e1f2a3b4c",
  "status": "ACTIVE",
  "created_at": "2025-01-10T10:00:00Z"
}
```

> **Secret** is generated server-side and returned once. User stores it to verify HMAC signatures.

### 2. List Webhook Deliveries (Logs)
```
GET /api/v1/webhooks/{subscription_id}/deliveries?limit=20&status=FAILED

Response (200 OK):
{
  "deliveries": [
    {
      "event_id": "evt_xyz789",
      "event_type": "payment.completed",
      "attempt_number": 3,
      "status": "FAILED",
      "http_status": 500,
      "latency_ms": 2500,
      "attempted_at": "2025-01-10T10:05:30Z",
      "next_retry_at": "2025-01-10T10:35:30Z",
      "error": "Server returned 500 Internal Server Error"
    }
  ]
}
```

### 3. Retry a Failed Delivery (Manual)
```
POST /api/v1/webhooks/{subscription_id}/deliveries/{event_id}/retry

Response (202 Accepted):
{ "status": "RETRYING", "next_attempt_at": "2025-01-10T10:10:00Z" }
```

### 4. Webhook Payload (what the recipient receives)
```
POST https://api.customer.com/webhooks

Headers:
  Content-Type: application/json
  X-Webhook-Id: evt_xyz789
  X-Webhook-Timestamp: 1704891600
  X-Webhook-Signature: sha256=5f3a8b2c9d1e4f6a...
  User-Agent: WebhookService/1.0

Body:
{
  "id": "evt_xyz789",
  "type": "payment.completed",
  "occurred_at": "2025-01-10T10:00:00Z",
  "data": {
    "payment_id": "pay_123",
    "amount": 99.99,
    "currency": "USD",
    "customer_id": "cust_456"
  }
}
```

Recipient verifies:
```python
import hmac, hashlib
expected = hmac.new(secret.encode(), payload.encode(), hashlib.sha256).hexdigest()
assert request.headers['X-Webhook-Signature'] == f'sha256={expected}'
```

---

## 4️⃣ Data Flow

### Flow 1: Event → Fan-Out → Deliver → Retry
```
1. Internal service emits event: { type: "payment.completed", data: {...} }
   ↓
2. Event published to Kafka topic: "webhook-events"
   ↓
3. Fan-Out Service consumes event:
   a. Look up all ACTIVE subscriptions for event type "payment.completed"
   b. For each subscription: create a DeliveryTask
   c. Publish DeliveryTasks to Kafka topic: "webhook-deliveries"
   ↓
4. Delivery Worker consumes DeliveryTask:
   a. Build HTTP request: POST to callback_url with JSON payload
   b. Compute HMAC signature: sha256(secret, timestamp + "." + body)
   c. Set headers: X-Webhook-Id, X-Webhook-Timestamp, X-Webhook-Signature
   d. Send HTTP POST with 30-second timeout
   ↓
5. Handle response:
   a. 2xx → SUCCESS → log delivery, done ✓
   b. 4xx (except 429) → FAILED (client error, no retry)
   c. 5xx or timeout → FAILED → schedule retry with backoff
   d. 429 Too Many Requests → RETRYING → respect Retry-After header
   ↓
6. Retry logic (exponential backoff):
   Attempt 1: immediate
   Attempt 2: +1 minute
   Attempt 3: +5 minutes
   Attempt 4: +30 minutes
   Attempt 5: +2 hours
   Attempt 6: +8 hours
   Attempt 7: +24 hours
   After 7 attempts: move to Dead Letter Queue → alert user
   ↓
7. Dead Letter Queue:
   Event stored permanently → user can manually retry via API/dashboard
   User notified: "Webhook to https://api.customer.com failed 7 times"
```

### Flow 2: Recipient Verifies Signature
```
1. Recipient receives POST /webhooks with:
   - Body: JSON payload
   - Header: X-Webhook-Signature: sha256=abc123...
   - Header: X-Webhook-Timestamp: 1704891600
   ↓
2. Recipient reconstructs signed payload:
   signed_payload = timestamp + "." + raw_body
   ↓
3. Recipient computes HMAC:
   expected = HMAC-SHA256(secret, signed_payload)
   ↓
4. Compare:
   If header_signature == expected → AUTHENTIC ✓
   If mismatch → REJECT (possible tampering)
   ↓
5. Check timestamp:
   If abs(now - timestamp) > 5 minutes → REJECT (replay attack)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   INTERNAL SERVICES                                         │
│  (Payment Service, Order Service, User Service, etc.)                      │
│                                                                              │
│  Emit events → Kafka topic: "webhook-events"                               │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   KAFKA CLUSTER                                             │
│                                                                              │
│  Topic: "webhook-events"    (raw events from internal services)            │
│  Topic: "webhook-deliveries" (fan-out: one message per subscription)       │
│  Topic: "webhook-retries"    (failed deliveries scheduled for retry)       │
│  Topic: "webhook-dlq"        (dead letter queue: exhausted retries)        │
└──────────┬──────────────────────────┬──────────────────────────────────────┘
           │                          │
     ┌─────▼──────────┐        ┌─────▼──────────────┐
     │ FAN-OUT SERVICE │        │ DELIVERY WORKERS    │
     │                 │        │ (50-100 pods)        │
     │ • Consume events│        │                      │
     │ • Look up subs  │        │ • Consume delivery   │
     │ • Create tasks  │        │   tasks              │
     │ • Publish to    │        │ • HTTP POST to       │
     │   delivery topic│        │   callback URL       │
     │                 │        │ • HMAC signature      │
     │                 │        │ • Handle response     │
     │                 │        │ • Schedule retries    │
     └────────────────┘        └──────────┬───────────┘
                                          │
                                ┌─────────┼─────────┐
                                ▼         ▼         ▼
                          ┌──────────┐ ┌──────┐ ┌──────────┐
                          │Recipient │ │Recip.│ │Recipient  │
                          │Server A  │ │Svr B │ │Server C   │
                          │(2xx ✓)   │ │(5xx) │ │(timeout)  │
                          └──────────┘ └──┬───┘ └────┬──────┘
                                          │          │
                                     Retry queue  Retry queue


┌────────────────────────────────────────────────────────────────────────────┐
│                   DATA STORES                                               │
│                                                                              │
│  ┌──────────────┐  ┌────────────────┐  ┌──────────────────┐              │
│  │ PostgreSQL    │  │ Redis           │  │ S3 (optional)    │              │
│  │               │  │                 │  │                  │              │
│  │ • Subscriptions│ │ • Subscription  │  │ • Event payloads │              │
│  │ • Delivery logs│ │   cache         │  │   (for replay)   │              │
│  │ • Event index │  │ • Rate limiters │  │ • Large payloads │              │
│  │               │  │ • Circuit       │  │                  │              │
│  │               │  │   breaker state │  │                  │              │
│  └──────────────┘  └────────────────┘  └──────────────────┘              │
└────────────────────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────────────────┐
│                   WEBHOOK MANAGEMENT API                                    │
│                   (REST API for users)                                      │
│                                                                              │
│  • Create/update/delete subscriptions                                      │
│  • View delivery logs and status                                           │
│  • Manual retry of failed deliveries                                       │
│  • Dashboard: success rate, latency, error breakdown                       │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Fan-Out Service** | Match events to subscriptions, create delivery tasks | Java/Go on K8s | 5-10 pods, stateless |
| **Delivery Workers** | Execute HTTP POST, handle response, schedule retries | Java/Go on K8s | 50-100 pods, auto-scaled |
| **Kafka** | Durable event queue, delivery tasks, retry scheduling | Apache Kafka | 4 topics, partitioned |
| **PostgreSQL** | Subscriptions, delivery logs, event index | PostgreSQL | Read replicas |
| **Redis** | Subscription cache, rate limiters, circuit breaker | Redis Cluster | Shared |
| **Management API** | CRUD for subscriptions, delivery logs, manual retry | Java/Spring Boot | 5+ pods |

---

## 6️⃣ Deep Dives

### Deep Dive 1: HMAC Signature Verification — Preventing Spoofing

**The Problem**: Recipients need to verify that webhook requests actually come from us, not an attacker. HMAC-SHA256 provides authentication and integrity.

```java
public class WebhookSigner {

    /**
     * HMAC signing scheme (similar to Stripe):
     * 
     * 1. Build the signed payload: timestamp + "." + raw_body
     *    - Timestamp prevents replay attacks
     *    - Raw body ensures payload integrity
     * 
     * 2. Compute HMAC-SHA256(secret, signed_payload)
     * 
     * 3. Include in header: X-Webhook-Signature: sha256={hex_digest}
     * 
     * Why include timestamp:
     *   Without it, attacker who captures a webhook can replay it forever.
     *   With it, recipient checks: if abs(now - timestamp) > 5 min → reject.
     */
    
    public Map<String, String> signPayload(String secret, String body) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + body;
        
        String signature = hmacSha256(secret, signedPayload);
        
        return Map.of(
            "X-Webhook-Timestamp", String.valueOf(timestamp),
            "X-Webhook-Signature", "sha256=" + signature
        );
    }
    
    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
    
    /**
     * Secret rotation: support multiple active secrets during rotation.
     * Sign with the NEW secret, but recipient tries both old and new.
     * 
     * Header format for multiple signatures:
     * X-Webhook-Signature: sha256=abc123,sha256=def456
     */
    public Map<String, String> signWithRotation(List<String> secrets, String body) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + body;
        
        String signatures = secrets.stream()
            .map(s -> "sha256=" + hmacSha256(s, signedPayload))
            .collect(Collectors.joining(","));
        
        return Map.of(
            "X-Webhook-Timestamp", String.valueOf(timestamp),
            "X-Webhook-Signature", signatures
        );
    }
}
```

---

### Deep Dive 2: Retry Strategy — Exponential Backoff with Jitter

**The Problem**: When a recipient server is down, naive retries at fixed intervals can overwhelm it when it recovers (thundering herd). Exponential backoff with jitter spreads retries.

```java
public class RetryScheduler {

    /**
     * Exponential backoff schedule:
     *   Attempt 1: immediate (0s)
     *   Attempt 2: 1 minute
     *   Attempt 3: 5 minutes
     *   Attempt 4: 30 minutes
     *   Attempt 5: 2 hours
     *   Attempt 6: 8 hours
     *   Attempt 7: 24 hours
     *   After 7: Dead Letter Queue (72 hours total)
     * 
     * Jitter: ±20% to prevent thundering herd when many webhooks
     * to the same recipient fail simultaneously.
     */
    
    private static final Duration[] BACKOFF_SCHEDULE = {
        Duration.ZERO,
        Duration.ofMinutes(1),
        Duration.ofMinutes(5),
        Duration.ofMinutes(30),
        Duration.ofHours(2),
        Duration.ofHours(8),
        Duration.ofHours(24)
    };
    
    private static final int MAX_ATTEMPTS = 7;
    
    public RetryDecision shouldRetry(DeliveryAttempt attempt) {
        if (attempt.getAttemptNumber() >= MAX_ATTEMPTS) {
            return RetryDecision.EXHAUSTED; // Move to DLQ
        }
        
        // Don't retry 4xx errors (except 429)
        if (attempt.getHttpStatusCode() >= 400 && attempt.getHttpStatusCode() < 500
            && attempt.getHttpStatusCode() != 429) {
            return RetryDecision.NO_RETRY; // Client error, won't fix on retry
        }
        
        Duration baseDelay = BACKOFF_SCHEDULE[attempt.getAttemptNumber()];
        
        // Respect 429 Retry-After header if present
        if (attempt.getHttpStatusCode() == 429 && attempt.getRetryAfterSeconds() != null) {
            baseDelay = Duration.ofSeconds(attempt.getRetryAfterSeconds());
        }
        
        // Add jitter: ±20%
        long jitterMs = (long) (baseDelay.toMillis() * 0.2 * (Math.random() * 2 - 1));
        Duration actualDelay = baseDelay.plusMillis(jitterMs);
        
        Instant retryAt = Instant.now().plus(actualDelay);
        return RetryDecision.retryAt(retryAt, attempt.getAttemptNumber() + 1);
    }
}
```

**Kafka-based retry scheduling**:
```
How retries work with Kafka:

1. Failed delivery → compute next retry time
2. Publish to "webhook-retries" topic with:
   - Key: subscription_id
   - Headers: { "retry_at": "2025-01-10T10:06:00Z", "attempt": 2 }
3. Retry consumer: polls topic, checks if retry_at <= now()
   - If yes → re-attempt delivery
   - If no → re-publish with same retry_at (delay loop)
   
Alternative: use Kafka delayed messages (supported in some implementations)
Or: Redis sorted set with retry_at as score, poll every second
```

---

### Deep Dive 3: Circuit Breaker — Protecting Overwhelmed Recipients

**The Problem**: If a recipient server is completely down, sending 10K retries per minute wastes resources and may DDoS them further. A circuit breaker temporarily stops delivery to unhealthy endpoints.

```java
public class WebhookCircuitBreaker {

    /**
     * Circuit breaker states:
     * 
     * CLOSED (normal): delivering webhooks normally
     *   → If failure rate > 50% in last 10 attempts → OPEN
     * 
     * OPEN (tripped): stop all deliveries to this endpoint
     *   → After 5 minutes → HALF_OPEN
     * 
     * HALF_OPEN (testing): send one test delivery
     *   → If success → CLOSED
     *   → If failure → OPEN (reset timer)
     */
    
    private static final int WINDOW_SIZE = 10;
    private static final double FAILURE_THRESHOLD = 0.5;
    private static final Duration OPEN_DURATION = Duration.ofMinutes(5);
    
    public boolean shouldDeliver(String subscriptionId) {
        CircuitState state = getState(subscriptionId);
        
        switch (state.getStatus()) {
            case CLOSED:
                return true; // Normal delivery
                
            case OPEN:
                if (Instant.now().isAfter(state.getOpenedAt().plus(OPEN_DURATION))) {
                    // Time to test → transition to HALF_OPEN
                    setState(subscriptionId, CircuitStatus.HALF_OPEN);
                    return true; // Allow one test delivery
                }
                return false; // Still open, skip delivery (queue for later)
                
            case HALF_OPEN:
                return false; // Already testing with one request, wait for result
                
            default:
                return true;
        }
    }
    
    public void recordResult(String subscriptionId, boolean success) {
        CircuitState state = getState(subscriptionId);
        
        if (state.getStatus() == CircuitStatus.HALF_OPEN) {
            if (success) {
                setState(subscriptionId, CircuitStatus.CLOSED);
                log.info("Circuit CLOSED for {}: endpoint recovered", subscriptionId);
            } else {
                setState(subscriptionId, CircuitStatus.OPEN);
                log.warn("Circuit re-OPENED for {}: still failing", subscriptionId);
            }
            return;
        }
        
        // Record in sliding window
        state.addResult(success);
        
        if (state.getFailureRate() > FAILURE_THRESHOLD) {
            setState(subscriptionId, CircuitStatus.OPEN);
            log.warn("Circuit OPENED for {}: failure rate {}%", 
                subscriptionId, state.getFailureRate() * 100);
        }
    }
}
```

---

### Deep Dive 4: Fan-Out — Event to Delivery Task Mapping

**The Problem**: A single event like "payment.completed" might have 50 subscriptions. The fan-out service must efficiently look up subscribers and create individual delivery tasks.

```java
public class WebhookFanOutService {

    /**
     * Fan-out process:
     * 1. Consume event from "webhook-events" Kafka topic
     * 2. Look up all ACTIVE subscriptions for this event type
     * 3. For each subscription: create a delivery task
     * 4. Publish tasks to "webhook-deliveries" topic
     * 
     * Optimization: cache subscriptions per event type in Redis
     *   Cache key: "subs:payment.completed"
     *   Cache value: list of subscription IDs
     *   TTL: 60 seconds (invalidate on subscription change)
     */
    
    public void processEvent(WebhookEvent event) {
        // Step 1: Find all subscriptions for this event type
        List<WebhookSubscription> subscribers = getSubscribersForEvent(event.getEventType());
        
        if (subscribers.isEmpty()) {
            log.debug("No subscribers for event type {}", event.getEventType());
            return;
        }
        
        // Step 2: Create delivery tasks
        for (WebhookSubscription sub : subscribers) {
            if (sub.getStatus() != SubscriptionStatus.ACTIVE) continue;
            
            DeliveryTask task = DeliveryTask.builder()
                .eventId(event.getEventId())
                .subscriptionId(sub.getSubscriptionId())
                .callbackUrl(sub.getCallbackUrl())
                .secret(sub.getSecret())
                .payload(serializePayload(event))
                .customHeaders(sub.getHeaders())
                .attemptNumber(1)
                .createdAt(Instant.now())
                .build();
            
            kafka.send("webhook-deliveries", sub.getSubscriptionId(), task);
        }
        
        metrics.counter("webhook.fanout.tasks_created").increment(subscribers.size());
    }
    
    private List<WebhookSubscription> getSubscribersForEvent(String eventType) {
        // Check Redis cache first
        String cacheKey = "subs:" + eventType;
        List<String> cachedIds = redis.lrange(cacheKey, 0, -1);
        
        if (!cachedIds.isEmpty()) {
            return subscriptionStore.findByIds(cachedIds);
        }
        
        // Cache miss → query DB
        List<WebhookSubscription> subs = subscriptionStore.findActiveByEventType(eventType);
        
        // Cache for 60 seconds
        List<String> ids = subs.stream().map(WebhookSubscription::getSubscriptionId).toList();
        redis.rpush(cacheKey, ids.toArray(new String[0]));
        redis.expire(cacheKey, 60);
        
        return subs;
    }
}
```

---

### Deep Dive 5: Delivery Worker — Making the HTTP Call

**The Problem**: The delivery worker must make an outbound HTTP POST to an arbitrary external URL. This is fraught with edge cases: slow responses, redirects, DNS failures, SSL errors, large response bodies.

```java
public class WebhookDeliveryWorker {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESPONSE_BODY = 1024; // Log first 1 KB only
    
    public DeliveryResult deliver(DeliveryTask task) {
        Instant startTime = Instant.now();
        
        try {
            // Build request
            String body = task.getPayload();
            Map<String, String> signatureHeaders = signer.signPayload(task.getSecret(), body);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(task.getCallbackUrl()))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "WebhookService/1.0")
                .header("X-Webhook-Id", task.getEventId())
                .header("X-Webhook-Timestamp", signatureHeaders.get("X-Webhook-Timestamp"))
                .header("X-Webhook-Signature", signatureHeaders.get("X-Webhook-Signature"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            // Add custom headers
            task.getCustomHeaders().forEach((k, v) -> request.headers().add(k, v));
            
            // Execute with timeout
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            Duration latency = Duration.between(startTime, Instant.now());
            int statusCode = response.statusCode();
            String responseBody = truncate(response.body(), MAX_RESPONSE_BODY);
            
            if (statusCode >= 200 && statusCode < 300) {
                return DeliveryResult.success(statusCode, latency, responseBody);
            } else {
                return DeliveryResult.failed(statusCode, latency, responseBody,
                    "HTTP " + statusCode);
            }
            
        } catch (ConnectException e) {
            return DeliveryResult.failed(0, elapsed(startTime), null, "Connection refused");
        } catch (SocketTimeoutException e) {
            return DeliveryResult.failed(0, elapsed(startTime), null, "Read timeout (30s)");
        } catch (SSLException e) {
            return DeliveryResult.failed(0, elapsed(startTime), null, "SSL error: " + e.getMessage());
        } catch (UnknownHostException e) {
            return DeliveryResult.failed(0, elapsed(startTime), null, "DNS resolution failed");
        } catch (Exception e) {
            return DeliveryResult.failed(0, elapsed(startTime), null, e.getMessage());
        }
    }
    
    /**
     * Security: prevent SSRF (Server-Side Request Forgery)
     * Don't allow webhooks to internal/private IP addresses.
     */
    private void validateCallbackUrl(String url) {
        InetAddress addr = InetAddress.getByName(URI.create(url).getHost());
        if (addr.isSiteLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
            throw new SecurityException("Webhook URL resolves to private/internal IP: " + addr);
        }
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Delivery guarantee** | At-least-once (not exactly-once) | Simpler; recipients handle dedup via event_id |
| **Retry strategy** | Exponential backoff with jitter (7 attempts over 72h) | Gives recipient time to recover without thundering herd |
| **Event queue** | Kafka (4 topics: events, deliveries, retries, DLQ) | Durable, ordered, replayable; natural for fan-out |
| **Security** | HMAC-SHA256 with timestamp | Prevents spoofing AND replay attacks |
| **Circuit breaker** | Per-subscription, 50% failure threshold | Protects overwhelmed recipients; auto-recovers |
| **Fan-out** | Kafka topic per concern (not per subscriber) | Scalable; delivery workers independently process tasks |
| **4xx vs 5xx retry** | Retry 5xx and timeouts; don't retry 4xx (except 429) | 4xx = client error (won't fix on retry); 5xx = server issue (may recover) |

## Interview Talking Points

1. **"At-least-once via Kafka + retry"** — Event persisted to Kafka before any delivery attempt. If worker crashes, message re-consumed. Recipients dedup using event_id idempotency key.
2. **"HMAC-SHA256 with timestamp prevents spoofing AND replay"** — Signature proves authenticity; timestamp with 5-min window prevents old captured webhooks from being replayed.
3. **"Exponential backoff: 1m → 5m → 30m → 2h → 8h → 24h"** — Gives recipient 72 hours to recover. Jitter (±20%) prevents thundering herd when many webhooks to same endpoint fail.
4. **"Circuit breaker protects overwhelmed recipients"** — If 50% of last 10 attempts fail → stop delivering for 5 min. Half-open test with one request. Prevents DDoS-ing a struggling endpoint.
5. **"Fan-out service separates event matching from delivery"** — Single event → N delivery tasks via Kafka. Workers scale independently. Each task processed exactly once by Kafka consumer group.
6. **"4xx = don't retry, 5xx = retry, 429 = respect Retry-After"** — Smart retry policy saves resources and respects recipient's rate limits.
7. **"SSRF protection: validate callback URL doesn't resolve to private IP"** — Prevents attackers from using webhooks to probe internal services.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Notification System** | Same push delivery pattern | Notifications target end-users (mobile/email); webhooks target servers (HTTP) |
| **Message Queue (Kafka)** | Core infrastructure component | Queue is generic; webhook system adds HTTP delivery + retry + signing |
| **Pub/Sub System** | Same fan-out concept | Pub/Sub is internal; webhooks deliver externally to arbitrary endpoints |
| **API Gateway** | Same HTTP concern | Gateway handles inbound; webhooks handle outbound HTTP |
| **Job Scheduler** | Same delayed execution | Scheduler is generic; webhooks are specifically timed HTTP delivery |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Queue** | Kafka | SQS + SNS | Simpler; serverless; built-in DLQ |
| **Retry scheduling** | Kafka retry topic | Redis sorted set (ZADD with timestamp) | Fine-grained timing; simpler delayed execution |
| **Delivery** | Custom workers | AWS Lambda | Serverless; auto-scaling; pay-per-invocation |
| **Signing** | HMAC-SHA256 | RSA/Ed25519 (asymmetric) | When recipient shouldn't have the secret |
| **Storage** | PostgreSQL | DynamoDB | Serverless; auto-scaling; less operational burden |
| **Circuit breaker** | Custom (Redis state) | Resilience4j / Hystrix library | Pre-built; more sophisticated patterns |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 5 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Stripe Webhook Best Practices
- GitHub Webhooks Documentation
- HMAC-SHA256 RFC 2104
- Circuit Breaker Pattern (Martin Fowler)
- Exponential Backoff and Jitter (AWS Architecture Blog)
- SSRF Prevention in Webhook Systems
