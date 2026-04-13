# 🎯 Topic 8: Synchronous vs Asynchronous

> **System Design Interview — Deep Dive**
> A comprehensive guide on when to use synchronous vs asynchronous processing — the fundamental architectural decision that determines system responsiveness, resilience, and scalability.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Synchronous Processing](#synchronous-processing)
3. [Asynchronous Processing](#asynchronous-processing)
4. [Decision Framework: Sync vs Async](#decision-framework-sync-vs-async)
5. [The Split Pattern: Sync ACK + Async Processing](#the-split-pattern-sync-ack--async-processing)
6. [Async Patterns: Message Queues & Events](#async-patterns-message-queues--events)
7. [Async Patterns: Background Workers](#async-patterns-background-workers)
8. [Async Patterns: Webhooks & Callbacks](#async-patterns-webhooks--callbacks)
9. [Error Handling: Sync vs Async](#error-handling-sync-vs-async)
10. [Retry Strategies for Async Operations](#retry-strategies-for-async-operations)
11. [Dead Letter Queues](#dead-letter-queues)
12. [Async Processing at Scale](#async-processing-at-scale)
13. [User Experience Patterns for Async Operations](#user-experience-patterns-for-async-operations)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Sync/Async Decisions for Popular System Design Problems](#syncasync-decisions-for-popular-system-design-problems)
17. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Synchronous**: The caller waits for the operation to complete before proceeding. Request → Processing → Response, all in one blocking call.

**Asynchronous**: The caller fires the request and proceeds immediately. Processing happens in the background. Results arrive later (via callback, polling, or event).

```
Synchronous:
  Client ──request──→ Server ──process (500ms)──→ response ──→ Client
  Client is BLOCKED for 500ms

Asynchronous:
  Client ──request──→ Server ──ACK (5ms)──→ Client (continues immediately)
                      Server ──process (500ms)──→ result stored/notified later
  Client is FREE after 5ms
```

**The key insight**: Make the user-facing path synchronous (fast response) and the heavy processing asynchronous (decoupled from user experience).

---

## Synchronous Processing

### When to Use Sync
- **User needs the result immediately**: Payment confirmation, login, search results.
- **Operation is fast**: < 500ms end-to-end is acceptable.
- **Failure must be reported immediately**: User needs to know it failed to take corrective action.
- **Transactional integrity**: The result of one step determines the next step.

### Examples of Synchronous Operations

| Operation | Why Sync? | Latency Budget |
|---|---|---|
| **Login/Authentication** | User can't proceed without auth token | < 200ms |
| **Payment processing** | User needs to know if payment succeeded | < 2 seconds |
| **Search query** | Results are the entire point of the request | < 500ms |
| **Read user profile** | Data is immediately displayed | < 100ms |
| **Add to cart** | User expects immediate visual feedback | < 200ms |
| **Check seat availability** | Needed before proceeding to booking | < 300ms |
| **API key validation** | Request is rejected if invalid | < 50ms |

### Synchronous Architecture
```
Client → API Gateway → Service A → Database → Service A → API Gateway → Client
         (all in one request-response cycle)
         
Total latency = sum of all component latencies
If any component fails, the entire request fails
```

### Limitations
- **Tight coupling**: Caller is blocked until callee responds.
- **Cascading failures**: If a downstream service is slow, all upstream callers slow down.
- **Scaling challenges**: Long-running requests hold connections open, consuming thread pool resources.
- **Latency accumulation**: Chaining 5 sync services, each taking 100ms = 500ms total.

---

## Asynchronous Processing

### When to Use Async
- **User doesn't need the result immediately**: Email sending, report generation, video processing.
- **Operation is slow**: Minutes to hours (transcoding, ML inference, data migration).
- **Fire-and-forget**: Logging, analytics, metrics collection.
- **Decoupling services**: The producing service shouldn't wait for the consuming service.
- **Smoothing traffic spikes**: Queue absorbs bursts; workers process at their own pace.

### Examples of Asynchronous Operations

| Operation | Why Async? | Processing Time |
|---|---|---|
| **Send email/SMS** | User doesn't wait for delivery | 1-30 seconds |
| **Video transcoding** | Takes minutes per video | 1-10 minutes |
| **Generate thumbnails** | Multiple sizes, can be parallelized | 5-30 seconds |
| **Content moderation (ML)** | Inference takes time | 2-10 seconds |
| **Timeline fanout** | Millions of writes for popular users | 1-100 seconds |
| **Search indexing** | Eventual consistency is acceptable | 1-5 seconds |
| **Report generation** | Complex aggregations over large datasets | 30 sec - 10 min |
| **Data export (CSV/PDF)** | Large dataset processing | 10 sec - 5 min |
| **Notification delivery** | Can be batched and aggregated | 1-60 seconds |
| **Audit logging** | Non-blocking, write-behind pattern | < 1 second |

### Asynchronous Architecture
```
Client → API Server → Message Queue (Kafka/SQS) → Workers → Database/Storage
         ↓ (immediate ACK)
         Client continues

Client polls for result or receives callback/notification later
```

---

## Decision Framework: Sync vs Async

### The Decision Matrix

| Question | If Yes → Sync | If Yes → Async |
|---|---|---|
| Does the user need the result to continue? | ✅ | |
| Is the operation < 500ms? | ✅ | |
| Can processing happen after the response? | | ✅ |
| Is the operation > 2 seconds? | | ✅ |
| Is failure immediately actionable by user? | ✅ | |
| Can results be delivered via notification? | | ✅ |
| Does this involve money changing hands? | ✅ | |
| Is this a fire-and-forget operation? | | ✅ |
| Does the user expect real-time confirmation? | ✅ | |
| Would queuing this smooth traffic spikes? | | ✅ |

### The Latency Rule of Thumb
```
< 200ms:  Synchronous (user perceives as instant)
200ms-2s: Synchronous with loading indicator
2s-30s:   Async with progress bar / "processing..." state
> 30s:    Async with notification when complete
```

---

## The Split Pattern: Sync ACK + Async Processing

The most powerful pattern: **return a fast synchronous acknowledgment, then process asynchronously**.

### Example: Tweet Creation
```
Sync part (< 50ms):
  1. Validate tweet content
  2. Write to Cassandra (persistent storage)
  3. Return HTTP 201 "Tweet posted successfully"
  4. User sees confirmation immediately

Async part (1-100 seconds):
  5. Publish TweetCreated event to Kafka
  6. Fanout workers push to followers' timelines
  7. Search indexer updates Elasticsearch
  8. Notification service alerts mentioned users
  9. Analytics pipeline records the event
  10. Content moderation ML checks for violations

User impact: ZERO — they got their confirmation at step 3
```

### Example: Media Upload
```
Sync part (< 100ms):
  1. Generate presigned S3 URL
  2. Return URL to client

Client uploads directly to S3 (parallel, not through our servers)

Async part (5 seconds - 5 minutes):
  3. S3 event triggers Lambda
  4. Lambda enqueues processing job
  5. Workers: generate thumbnails (150x150, 300x300, 600x600)
  6. Workers: transcode video to multiple resolutions
  7. Workers: strip EXIF metadata (privacy)
  8. Workers: run content moderation ML
  9. Workers: update post status to 'published'
  10. Send push notification: "Your video is ready"
```

### Example: "Like" Action
```
Sync part (< 5ms):
  1. INCR likes:{tweet_id} in Redis → returns new count
  2. Return new count + animate heart icon
  User sees instant feedback

Async part (< 30 seconds):
  3. Publish LikeEvent to Kafka
  4. Notification worker: "Alice liked your tweet"
  5. Analytics: record engagement event
  6. Persist to durable storage (Cassandra)
```

### Interview Scripts

**Tweet creation**:
> *"I'd make the tweet creation synchronous — write to Cassandra and return HTTP 201 to the user within 50ms. The user sees 'posted' immediately. But the fanout to followers' timelines is fully asynchronous via Kafka — a Kafka producer enqueues a `TweetCreated` event, and a fleet of fanout workers consume it and populate Redis timelines in the background. If the fanout takes 10 seconds to complete, no one notices."*

**Media upload**:
> *"For media upload: the presigned URL generation is synchronous (the client needs it immediately to start uploading), but everything after the upload is async. Once the file lands in S3, an S3 event triggers a Lambda that enqueues a processing job. Workers generate thumbnails, transcode video, strip EXIF metadata, run content moderation ML, and update the post status to 'published'. This takes 5-30 seconds for images and 1-5 minutes for video, all invisible to the user."*

**Payment processing**:
> *"Payment processing must be synchronous — the user clicks 'Pay $49.99' and needs to know within 2 seconds whether the payment succeeded before we confirm their booking. Making this async (pay → show 'processing' → email confirmation later) destroys conversion rates. Users abandon the flow if they don't see immediate success."*

**Like action**:
> *"For the 'like' action, I'd split it: the counter increment is synchronous — `INCR likes:{tweet_id}` in Redis returns in < 1ms, and the UI immediately shows the updated count. But the notification to the tweet author is asynchronous via a Kafka event. Notifications can be batched and aggregated. The user who liked gets instant feedback; the author gets a slightly delayed but richer notification."*

---

## Async Patterns: Message Queues & Events

### Pattern 1: Work Queue (SQS, RabbitMQ)
```
Producer → Queue → Consumer (one consumer processes each message)

Use: Task distribution (send email, resize image, generate report)
Guarantee: Each task processed exactly once
```

### Pattern 2: Pub/Sub (Kafka, SNS)
```
Producer → Topic → Consumer Group A (fanout)
                 → Consumer Group B (search indexing)
                 → Consumer Group C (analytics)

Use: Event broadcasting (one event, multiple consumers)
Guarantee: Each consumer group gets every message
```

### Pattern 3: Event Sourcing
```
All state changes stored as immutable events:
  OrderCreated → ItemAdded → PaymentProcessed → OrderShipped

State is reconstructed by replaying events.
Use: Audit trail, temporal queries, CQRS
```

---

## Async Patterns: Background Workers

### Worker Pool Architecture
```
┌─────────┐     ┌──────────────┐     ┌──────────┐
│ API      │────→│ Message Queue│────→│ Worker 1 │
│ Server   │     │ (SQS/Kafka)  │────→│ Worker 2 │
└─────────┘     └──────────────┘────→│ Worker 3 │
                                     │ ...      │
                                     │ Worker N │
                                     └──────────┘
```

### Auto-Scaling Workers
```
Scale based on queue depth:
  Queue depth < 1000:    2 workers (baseline)
  Queue depth 1K-10K:    10 workers
  Queue depth 10K-100K:  50 workers
  Queue depth > 100K:    100 workers (max)

Scale-down delay: 5 minutes (avoid flapping)
```

### Visibility Timeout Pattern
```
1. Worker picks message from queue → message becomes invisible
2. Worker processes message
3a. Success → Worker deletes message from queue
3b. Failure → Worker crashes → message becomes visible again after timeout
4. Another worker picks up the message and retries

Visibility timeout = expected max processing time + safety margin
Example: Processing takes 30 seconds → visibility timeout = 60 seconds
```

---

## Async Patterns: Webhooks & Callbacks

### Webhook Pattern
```
Client: "Process this payment. When done, call me back at https://my.api/callback"

Server: Accepts request → processes async → calls webhook URL with result

Webhook payload:
{
  "event": "payment.completed",
  "payment_id": "pay_123",
  "status": "success",
  "amount": 49.99,
  "timestamp": "2025-03-15T14:30:00Z"
}
```

### Polling Pattern (Alternative to Webhooks)
```
Client: POST /process-video → { "job_id": "job_456" }
Client: GET /jobs/job_456   → { "status": "processing", "progress": 45% }
Client: GET /jobs/job_456   → { "status": "processing", "progress": 78% }
Client: GET /jobs/job_456   → { "status": "complete", "result_url": "..." }
```

---

## Error Handling: Sync vs Async

### Sync Error Handling
```
Simple: Caller receives error immediately → shows error to user → user retries

try:
    result = payment_service.charge(amount=49.99)
    return {"status": "success", "transaction_id": result.id}
except PaymentDeclined:
    return {"status": "declined", "message": "Card declined"}, 402
except Timeout:
    return {"status": "error", "message": "Try again"}, 503
```

### Async Error Handling (More Complex)
```
Challenge: The user is long gone by the time the error occurs.

Options:
1. Retry automatically (exponential backoff)
2. Send to Dead Letter Queue (DLQ) after max retries
3. Notify user via email/push: "Your export failed, please try again"
4. Alert operations team for manual intervention
5. Store error state for user to check: GET /jobs/job_456 → "failed"
```

---

## Retry Strategies for Async Operations

### Exponential Backoff with Jitter
```python
def retry_with_backoff(operation, max_retries=5):
    for attempt in range(max_retries):
        try:
            return operation()
        except RetryableError:
            base_delay = min(2 ** attempt, 60)  # 1, 2, 4, 8, 16, 32, 60 seconds
            jitter = random.uniform(0, base_delay)
            delay = base_delay + jitter
            time.sleep(delay)
    
    # Max retries exceeded → send to Dead Letter Queue
    send_to_dlq(operation)
```

### Why Jitter Matters
```
Without jitter: 1000 failed requests all retry at exactly 1s, 2s, 4s, 8s
  → Synchronized retry storms that overwhelm the recovering service

With jitter: 1000 failed requests retry at random times between 0-2s, 0-4s, 0-8s
  → Spread out over time, giving the service room to recover
```

### Interview Script
> *"I'd implement retry with exponential backoff and jitter for all external service calls: `delay = min(base_delay * 2^attempt + random(0, base_delay), max_delay)`. Without jitter, all clients retry at exactly the same time, creating synchronized retry storms. The jitter randomizes retry timing, spreading the load. Without backoff, clients retry immediately and continuously, turning a brief outage into a sustained DDoS."*

---

## Dead Letter Queues

### Concept
Messages that repeatedly fail processing are moved to a **Dead Letter Queue** (DLQ) for investigation instead of blocking the main queue.

```
Main Queue → Worker processes → Success → Delete from queue
                              → Fail (1st time) → Back to main queue
                              → Fail (2nd time) → Back to main queue
                              → Fail (3rd time) → Move to DLQ

DLQ → Alert operations team
    → Manual investigation
    → Fix bug → replay messages from DLQ
```

### Why DLQs Matter
- **Prevent queue poisoning**: One bad message doesn't block all subsequent messages.
- **Preserve data**: Failed messages aren't lost — they're saved for debugging.
- **Operational visibility**: DLQ depth is a key metric — if it grows, something is wrong.
- **Replayability**: After fixing the bug, replay DLQ messages to recover.

---

## Async Processing at Scale

### Scaling Dimensions
```
1. More workers: Process messages faster (horizontal scaling)
2. More partitions: Increase parallelism (Kafka partitions)
3. Batch processing: Process multiple messages per worker invocation
4. Priority queues: Process critical messages first
```

### Backpressure
When the consumer can't keep up with the producer:
```
Queue depth increases → consumer lag grows → processing delay increases

Mitigation:
1. Auto-scale consumers based on queue depth
2. Apply backpressure to producers (reject or throttle new messages)
3. Sampling: Process 10% of non-critical messages, extrapolate
4. Priority-based shedding: Drop low-priority messages, keep critical ones
```

---

## User Experience Patterns for Async Operations

### Pattern 1: Optimistic UI
```
User clicks "Like" → UI immediately shows liked state
Background: Like event sent to server
If server fails → silently revert UI (rare, usually succeeds)
```

### Pattern 2: Progress Tracking
```
User uploads video → "Processing your video..."
                   → "Generating thumbnails... 25%"
                   → "Transcoding HD... 60%"
                   → "Almost done... 90%"
                   → "Your video is ready! 🎉"
```

### Pattern 3: Email/Push Notification
```
User requests data export → "We'll email you when it's ready"
30 minutes later → Email: "Your export is ready. Download here."
```

### Pattern 4: Status Page
```
User creates order → "Order placed. Track status: /orders/123"
/orders/123 → "Payment processing..."
/orders/123 → "Payment confirmed. Preparing shipment."
/orders/123 → "Shipped. Tracking: ABC123"
```

---

## Interview Talking Points & Scripts

### The Split Pattern
> *"I'd separate the user-facing synchronous path from the background asynchronous processing. The tweet write to Cassandra is synchronous — the user needs confirmation within 50ms. The fanout, search indexing, notifications, and analytics are all asynchronous via Kafka. The user gets instant feedback; the system gets decoupled, resilient processing."*

### Why Async for Heavy Processing
> *"Video transcoding takes 1-5 minutes. Making the user wait with a spinner for 5 minutes is terrible UX. Instead, the upload returns immediately with 'processing...', and we notify the user when it's ready. The async architecture also lets us auto-scale transcoding workers independently based on queue depth."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Making everything synchronous
**Problem**: Long-running operations block the user and waste connection resources.

### ❌ Mistake 2: Making everything asynchronous
**Problem**: Payment processing and login must be synchronous — the user needs immediate confirmation.

### ❌ Mistake 3: Not mentioning the message queue
**Problem**: Saying "we'd process this asynchronously" without explaining the mechanism (Kafka, SQS, etc.).

### ❌ Mistake 4: Ignoring failure handling for async
**Problem**: Async failures are harder to handle than sync failures. Must mention retries, DLQs, and monitoring.

### ❌ Mistake 5: Not considering user experience
**Problem**: Making an operation async without explaining how the user knows it completed (notification, polling, status page).

---

## Sync/Async Decisions for Popular System Design Problems

| System | Operation | Sync/Async | Reasoning |
|---|---|---|---|
| **Twitter** | Post tweet (persist) | Sync | User needs "posted" confirmation |
| **Twitter** | Timeline fanout | Async | Background, can take seconds |
| **Twitter** | Search indexing | Async | 1-2s lag is invisible |
| **Instagram** | Upload photo | Sync (URL gen) + Async (process) | Upload fast, process in background |
| **WhatsApp** | Send message | Sync (to server) | User needs "sent" checkmark |
| **WhatsApp** | Deliver to recipient | Async (push) | Delivery happens when recipient online |
| **E-Commerce** | Payment | Sync | Must know if payment succeeded |
| **E-Commerce** | Send confirmation email | Async | 5-second email delay is fine |
| **E-Commerce** | Update inventory | Sync (in transaction) | Must be atomic with payment |
| **YouTube** | Upload video | Sync (accept) + Async (transcode) | 1-10 min transcoding |
| **Uber** | Request ride | Sync (match) | User needs immediate match |
| **Uber** | Send receipt | Async | After ride, delay is fine |
| **Search** | Execute query | Sync | Results are the point |
| **Analytics** | Record event | Async (fire-and-forget) | Non-blocking, best-effort |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│           SYNC vs ASYNC CHEAT SHEET                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  SYNCHRONOUS: User waits for result                          │
│    Use: payments, login, search, reads, validation           │
│    Latency: < 500ms (user-perceived instant)                 │
│    Failure: Reported immediately to user                     │
│                                                              │
│  ASYNCHRONOUS: Fire and forget / process later               │
│    Use: emails, transcoding, fanout, analytics, indexing     │
│    Mechanism: Kafka, SQS, background workers                 │
│    Failure: Retry + DLQ + notification                       │
│                                                              │
│  THE SPLIT PATTERN (Most Common):                            │
│    Sync: Fast ACK to user (persist + confirm)                │
│    Async: Heavy processing in background                     │
│    Example: Tweet → sync write → async fanout                │
│                                                              │
│  RETRY: Exponential backoff + jitter                         │
│    delay = min(base * 2^attempt + random(0, base), max)      │
│    Max retries → Dead Letter Queue                           │
│                                                              │
│  UX FOR ASYNC:                                               │
│    < 2s: Show spinner (still feels sync)                     │
│    2-30s: Progress bar                                       │
│    > 30s: "We'll notify you when ready"                      │
│                                                              │
│  RULE: Make the USER PATH sync, make the SYSTEM              │
│  PATH async. Fast response + decoupled processing.           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 7: Push vs Pull** — Fanout is async processing
- **Topic 10: Message Queues vs Event Streams** — Async mechanisms
- **Topic 22: Delivery Guarantees** — Exactly-once for async processing
- **Topic 30: Backpressure & Flow Control** — Managing async overload
- **Topic 28: Availability & Disaster Recovery** — Async enables graceful degradation

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
