# 🎯 Topic 30: Backpressure & Flow Control

> **System Design Interview — Deep Dive**
> A comprehensive guide covering managing overload in distributed systems — priority-based load shedding, sampling under backpressure, circuit breakers, auto-scaling, queue-based buffering, consumer lag management, and how to articulate backpressure strategies with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [What Causes Backpressure](#what-causes-backpressure)
3. [The Five Backpressure Strategies](#the-five-backpressure-strategies)
4. [Priority-Based Load Shedding](#priority-based-load-shedding)
5. [Sampling Under Backpressure](#sampling-under-backpressure)
6. [Queue-Based Buffering](#queue-based-buffering)
7. [Auto-Scaling as Backpressure Response](#auto-scaling-as-backpressure-response)
8. [Circuit Breaker Pattern](#circuit-breaker-pattern)
9. [Rate Limiting as Upstream Backpressure](#rate-limiting-as-upstream-backpressure)
10. [Kafka Consumer Lag Management](#kafka-consumer-lag-management)
11. [Layered Backpressure Strategy](#layered-backpressure-strategy)
12. [Real-World System Examples](#real-world-system-examples)
13. [Deep Dive: Applying Backpressure to Popular Problems](#deep-dive-applying-backpressure-to-popular-problems)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Backpressure** occurs when a downstream system can't keep up with the upstream rate. Without handling, it causes cascading failures — queues overflow, memory exhausts, latency spikes, and services crash.

```
Normal:    Producer (100K/sec) → Consumer (100K/sec) → Balanced ✓
Overload:  Producer (500K/sec) → Consumer (100K/sec) → Queue grows → OOM crash!

The fundamental question:
  When input rate exceeds processing capacity, what do you do?

Answer: A layered strategy, applied in order:
  1. Buffer: Absorb the burst temporarily (queue/Kafka)
  2. Scale:  Add more consumers (auto-scaling)
  3. Shed:   Drop low-priority work (preserve critical)
  4. Sample: Process a fraction of non-critical data (estimate the rest)
  5. Limit:  Slow down producers (rate limiting / 429 responses)
  6. Break:  Stop calling slow dependencies (circuit breaker)
```

---

## What Causes Backpressure

### Common Causes

| Cause | Example | Duration | Impact |
|---|---|---|---|
| **Traffic spike** | Super Bowl ad → 10x normal traffic | Minutes to hours | All consumers overloaded |
| **Dependency slowdown** | Database latency spike (10ms → 500ms) | Minutes to hours | Processing rate drops 50x |
| **Consumer crash** | 3 of 10 consumers crash | Minutes | Remaining 7 handle 10x load |
| **Burst events** | Celebrity tweet → millions of fanout events | Minutes | One partition overwhelmed |
| **Batch backfill** | Replaying historical data + live traffic | Hours | Double the input rate |
| **Cascading failure** | Service A slows → Service B queues → Service C times out | Minutes | Entire pipeline degrades |
| **Memory pressure** | Large messages exhausting consumer heap | Gradual | OOM kills, consumer restarts |
| **Network congestion** | Cross-AZ traffic during peak | Minutes | Increased latency, timeouts |

### How Backpressure Manifests

```
Kafka pipeline:
  Producer → Kafka → Consumer → Database

Symptoms of backpressure:
  1. Consumer lag grows (events piling up in Kafka)
  2. End-to-end latency increases (events processed minutes after creation)
  3. Consumer CPU/memory spikes (processing more than capacity)
  4. Database connections exhausted (consumer pushing too many writes)
  5. Eventually: OOM, consumer crashes, topic falls hours behind

HTTP service:
  Client → Load Balancer → API Server → Database

Symptoms of backpressure:
  1. Request queue grows (more incoming than can be processed)
  2. P99 latency spikes (requests waiting in queue)
  3. Thread pool exhaustion (all threads busy, new requests rejected)
  4. Upstream timeouts (clients give up waiting)
  5. Eventually: Health check failures, cascading to other services
```

---

## The Five Backpressure Strategies

### Strategy Overview

```
┌────────────────────────────────────────────────────────────┐
│              BACKPRESSURE RESPONSE LAYERS                    │
│                                                            │
│  Layer 1: BUFFER (absorb burst)                            │
│    Kafka/SQS absorbs excess on disk                        │
│    Buys time: minutes to hours                             │
│    No data loss, no degradation                            │
│                                                            │
│  Layer 2: SCALE (increase capacity)                        │
│    Add consumers/workers (auto-scaling)                    │
│    Response time: 1-5 minutes                              │
│    No data loss, temporary lag                             │
│                                                            │
│  Layer 3: SHED (drop low-priority work)                    │
│    Stop processing P3 events entirely                      │
│    Immediate effect                                        │
│    Controlled data loss (non-critical only)                │
│                                                            │
│  Layer 4: SAMPLE (process fraction)                        │
│    Process 10% of P2 events, multiply by 10               │
│    Immediate throughput reduction                          │
│    Approximate results (±1-3% error)                       │
│                                                            │
│  Layer 5: LIMIT (slow producers)                           │
│    Return 429 to clients / throttle producers              │
│    Immediate effect                                        │
│    User-visible degradation (last resort)                  │
│                                                            │
│  Applied in order: 1 → 2 → 3 → 4 → 5                     │
│  Never jump to Layer 5 before exhausting Layers 1-4        │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## Priority-Based Load Shedding

### The Strategy

When overwhelmed, **drop low-priority work** to ensure high-priority work completes. This is the single most important backpressure technique.

### Priority Classification

```
Priority levels:

P0 (Critical — ALWAYS process):
  ✅ Payment transactions (revenue)
  ✅ Click billing events (advertiser charges)
  ✅ Order confirmations (customer commitment)
  ✅ Authentication requests (security)
  ✅ Leader election heartbeats (system coordination)

P1 (Important — process if capacity allows):
  ✅ User-facing notifications (push, email)
  ✅ Message delivery (chat, inbox)
  ✅ Search index updates (user experience)
  ✅ Inventory updates (moderate freshness needed)

P2 (Normal — shed under moderate pressure):
  ✅ Feed ranking updates (eventual consistency fine)
  ✅ Recommendation recalculation
  ✅ Non-critical search index updates
  ✅ Profile view counters

P3 (Low — first to shed):
  ✅ Analytics events (can reconstruct from raw logs)
  ✅ Debug logging (non-essential)
  ✅ A/B test impression tracking
  ✅ Historical data backfills
```

### Implementation

```
Consumer processing logic:

def process_event(event):
    current_lag = get_consumer_lag()
    
    if current_lag < 500_000:       # LEVEL 1: Normal
        process(event)               # All priorities processed
        
    elif current_lag < 2_000_000:   # LEVEL 2: Elevated
        if event.priority <= P2:
            process(event)           # P0, P1, P2 processed
        else:
            drop(event)              # P3 shed
            
    elif current_lag < 10_000_000:  # LEVEL 3: Critical
        if event.priority <= P1:
            process(event)           # P0, P1 processed
        elif event.priority == P2:
            sample(event, rate=0.5)  # P2 sampled at 50%
        else:
            drop(event)              # P3 shed
            
    else:                           # LEVEL 4: Emergency
        if event.priority == P0:
            process(event)           # P0 always processed
        elif event.priority == P1:
            sample(event, rate=0.5)  # P1 sampled at 50%
        elif event.priority == P2:
            sample(event, rate=0.1)  # P2 sampled at 10%
        else:
            drop(event)              # P3 shed entirely
```

### Priority Tagging

```
How events get their priority:

Option 1: Producer tags at creation
  event.priority = P0  // Set by the billing service
  
Option 2: Topic-based priority (Kafka)
  Topic "clicks-billing" → P0 consumer group (always processed)
  Topic "clicks-analytics" → P3 consumer group (first to shed)
  
Option 3: Header-based priority (HTTP)
  X-Priority: P0
  Load balancer routes P0 to dedicated server pool
  P3 requests rejected first under load
```

### Interview Script

> *"Under backpressure during a Super Bowl traffic spike (10x normal), I'd use priority-based load shedding with four levels. Level 1 (lag < 500K): process everything. Level 2 (lag 500K-2M): auto-scale consumers, shed P3 analytics events. Level 3 (lag 2M-10M): sample P2 impression events at 50% with correction factor. Level 4 (lag > 10M): sample P1 at 50%, P2 at 10%, shed P3 entirely. P0 click-billing events are ALWAYS processed at 100% because they represent revenue. This preserves billing accuracy while preventing the pipeline from falling hours behind."*

---

## Sampling Under Backpressure

### How Sampling Works

```
Normal operation: Process 100% of events
Under backpressure: Process 10% of non-critical events, multiply by 10

Example:
  Normal:   1,000,000 impression events → count = 1,000,000
  Sampling: Process 100,000 events → count = 100,000 × 10 = 1,000,000 (estimated)
  
  Error: ±1-3% (statistically valid for large sample sizes)
  Throughput: 10x reduction in processing → consumer catches up quickly

Implementation:
  def should_sample(event, rate):
      return hash(event.id) % 100 < (rate * 100)
  
  # Deterministic: Same event always sampled or always dropped
  # Ensures no partial counting (event counted 0 or 1 times, never twice)
```

### Statistical Validity

```
Central Limit Theorem guarantees:
  For large N (> 10,000), a random 10% sample estimates the true count
  within ±1-3% with 95% confidence.

Example at 10% sampling:
  True count:     1,000,000
  Sampled count:  100,247
  Estimated:      100,247 × 10 = 1,002,470
  Error:          0.25% (well within acceptable range)

For dashboards showing "~1M impressions" — this is perfect.
For billing showing "$1,002,470 in charges" — NOT acceptable.
```

### When Sampling Is Acceptable

```
✅ Acceptable (approximate is fine):
  Impression counting (display on dashboard, labeled "~")
  Analytics aggregations (±2% invisible on charts)
  Trend detection (signal preserved at 10% sample)
  A/B test metrics (statistical significance from sample)
  System monitoring (CPU%, latency percentiles)

❌ Not acceptable (must be exact):
  Click counting for billing (each click = revenue)
  Payment processing (every transaction must complete)
  Message delivery (every message must be delivered)
  Order processing (every order must be fulfilled)
  Security events (every alert must be evaluated)
```

### Correction Factor

```
When sampling at rate R:
  Sampled count × (1/R) = Estimated total

  Rate = 10%:  100,000 sampled × 10 = 1,000,000 estimated
  Rate = 50%:  500,000 sampled × 2  = 1,000,000 estimated
  Rate = 1%:   10,000 sampled × 100 = 1,000,000 estimated

Correction is applied at the aggregation layer (Flink/Spark):
  aggregate.clicks += event.is_sampled ? (1 / event.sample_rate) : 1
  
Dashboard labels:
  "~1,000,000 impressions (estimated)" during backpressure
  "1,000,000 impressions (exact)" during normal operation
```

---

## Queue-Based Buffering

### Kafka as a Buffer

```
Producer burst: 500K events/sec (5x normal)
Consumer capacity: 100K events/sec

Without Kafka:
  Events dropped or service crashes.
  Data loss. Unrecoverable.

With Kafka:
  Kafka absorbs 400K/sec excess → stored on disk (not in memory!)
  Consumer lag grows: 400K/sec × 60 sec = 24M events behind after 1 minute
  
  Kafka disk capacity: 10 TB → can buffer ~hours of excess events
  Events are NOT lost — they're queued for later processing.
  
  Auto-scaling kicks in: 100K/sec → 500K/sec (5x consumers)
  Lag decreases as consumers catch up
  
  After spike ends: Consumers process backlog + new events
  Eventually lag returns to 0. Zero data loss.
```

### Queue Depth as Health Signal

```
Kafka consumer lag is the single best indicator of pipeline health:

Lag < 100K:    ✅ Healthy — processing keeps up with production
Lag 100K-1M:   ⚠️ Warning — minor backpressure
                Action: Monitor closely, prepare to scale
Lag 1M-10M:    🔴 Critical — falling behind significantly
                Action: Auto-scale consumers, begin shedding P3
Lag > 10M:     🔥 Emergency — hours behind real-time
                Action: Max consumers + sample P2 + shed P3 + alert on-call
Lag growing:   📈 Getting worse — input > capacity
                Action: Must either increase capacity or reduce input
Lag shrinking: 📉 Recovering — capacity > input
                Action: Maintain current capacity until lag = 0
```

### SQS + Dead Letter Queue

```
For HTTP-based services (not Kafka):

SQS message flow:
  Producer → SQS Queue → Consumer

Backpressure handling:
  Queue depth grows → CloudWatch alarm → auto-scale consumers
  
  If message fails processing 3 times:
    Move to Dead Letter Queue (DLQ)
    Don't block the main queue
    Investigate DLQ messages separately
    
  DLQ acts as a safety net:
    Messages that can't be processed don't clog the pipeline
    Can be reprocessed after bug fix
    Alerted on DLQ depth > 0
```

---

## Auto-Scaling as Backpressure Response

### Scaling Based on Queue Depth

```
Kafka consumer auto-scaling:

CloudWatch metric: Kafka consumer lag per consumer group

Scaling policy:
  Lag < 100K:       2 consumers   (baseline)
  Lag 100K-500K:    5 consumers   (minor scaling)
  Lag 500K-2M:      10 consumers  (moderate scaling)
  Lag 2M-10M:       20 consumers  (aggressive scaling)
  Lag > 10M:        50 consumers  (maximum, with shedding)

Scale-up:   Immediate — new consumers start within 1-2 minutes
Scale-down: Delayed by 10 minutes — avoid flapping
  (Don't scale down immediately when lag drops —
   the burst might return)

Important: Number of consumers ≤ number of Kafka partitions
  If topic has 20 partitions → max useful consumers = 20
  Beyond 20 → idle consumers (no partitions to consume)
  Solution: Pre-provision enough partitions for peak scaling
```

### Scaling Based on CPU/Latency (HTTP Services)

```
Auto-scaling for API servers:

Target tracking policies:
  CPU > 70% for 2 minutes → add 20% more instances
  P99 latency > 500ms for 3 minutes → add 50% more instances
  Active connections > 1000/instance for 2 minutes → add instances
  
Cool-down:
  Scale-up cool-down: 30 seconds (react quickly)
  Scale-down cool-down: 5 minutes (avoid flapping)
  
Predictive scaling:
  Known traffic patterns (daily peak at 12 PM, Super Bowl at 6 PM)
  Pre-scale 30 minutes before expected spike
  More effective than reactive scaling (avoids the 1-2 minute gap)
```

### Auto-Scaling Limitations

```
Auto-scaling is NOT instantaneous:
  EC2 launch: 1-3 minutes
  Container (ECS/K8s): 30-60 seconds
  Lambda: Near-instant (but cold start: 100-500ms)

During the scaling gap (1-3 minutes):
  System is still overloaded
  Need OTHER strategies during this window:
    Load shedding (immediate)
    Circuit breakers (immediate)
    Queue buffering (immediate absorption)
  
  Auto-scaling SUPPLEMENTS these strategies, doesn't replace them.
```

---

## Circuit Breaker Pattern

### How Circuit Breakers Apply to Backpressure

```
Without circuit breaker:
  Service A → Service B (slow, backpressured)
  A's threads block waiting for B → A slows down
  Service C → Service A (now slow) → C slows down
  → Cascading failure across entire system!

  Service A: 100 threads, each waiting 5s for B
  = All 100 threads blocked = A is completely unresponsive
  = Everything that depends on A also fails

With circuit breaker:
  After 5 timeouts in 30 seconds → circuit OPENS
  A stops calling B → returns fallback immediately (< 1ms)
  A's threads are free → A serves other requests normally
  B gets reduced load → time to recover
  
  After 30 seconds → circuit goes HALF-OPEN
  A sends ONE test request to B
  If B responds → circuit CLOSES → normal operation
  If B fails → circuit stays OPEN → wait another 30 seconds
```

### Circuit Breaker States

```
┌────────────────────────────────────────────┐
│                                            │
│  CLOSED (normal operation)                 │
│    All requests pass through               │
│    Track failure count                     │
│    If failures > threshold → OPEN          │
│                                            │
│  ──→ failure threshold exceeded ──→        │
│                                            │
│  OPEN (circuit tripped)                    │
│    All requests fail fast (no call to B)   │
│    Return fallback/cached response         │
│    Wait timeout period (30 seconds)        │
│                                            │
│  ──→ timeout expires ──→                   │
│                                            │
│  HALF-OPEN (testing recovery)              │
│    Allow ONE request through               │
│    If success → CLOSED                     │
│    If failure → OPEN (reset timeout)       │
│                                            │
└────────────────────────────────────────────┘
```

### Fallback Strategies

```
When circuit is OPEN, what to return?

Strategy 1: Cached response
  Return the last known good response from cache
  Stale but functional
  Best for: Product catalog, user profiles, search results

Strategy 2: Degraded response
  Return a simplified version (no recommendations, no personalization)
  Best for: Homepage, feed (show generic content)

Strategy 3: Default value
  Return a hardcoded default
  Best for: Feature flags (default to "off"), config (default values)

Strategy 4: Error with retry guidance
  Return 503 with Retry-After header
  Best for: APIs where clients can retry (payment, booking)

Strategy 5: Queue for later
  Accept the request, queue it, process when B recovers
  Best for: Non-time-sensitive operations (email, notification)
```

---

## Rate Limiting as Upstream Backpressure

### Pushing Back on Producers

```
When consumers are overwhelmed, signal producers to slow down:

HTTP API (Rate Limiting):
  Response: HTTP 429 Too Many Requests
  Header: Retry-After: 30
  
  Client receives 429 → backs off → retries after 30 seconds
  Reduces inbound traffic immediately

Kafka Producer Throttling:
  If broker is backpressured (disk full, replication lag):
    Broker slows down acknowledgments to producer
    Producer's buffer fills up
    Producer blocks or drops events (configurable)
    
  Producer config:
    max.block.ms = 60000  (wait up to 60s for space)
    buffer.memory = 32MB  (local buffer before blocking)

gRPC Flow Control:
  Built-in windowing mechanism
  Server signals client to slow down (WINDOW_UPDATE frames)
  Client respects window size → won't overwhelm server
```

### Per-Client Rate Limiting

```
Not all clients are equal. Under backpressure:

Tier 1 clients (paying customers):
  Rate limit: 10,000 req/sec (generous)
  Never shed unless system is dying

Tier 2 clients (free users):
  Rate limit: 1,000 req/sec (moderate)
  Shed to 500 req/sec under pressure

Tier 3 clients (bots, scrapers):
  Rate limit: 100 req/sec (tight)
  Shed to 10 req/sec under pressure

Implementation: Token bucket per client tier
  Bucket size and refill rate adjusted based on system health
```

---

## Kafka Consumer Lag Management

### Monitoring

```
Consumer lag = latest offset - consumer's committed offset

Key metrics to monitor:
  lag_per_partition:    How far behind each partition's consumer is
  lag_growth_rate:      Is lag increasing or decreasing?
  processing_rate:      Events processed per second per consumer
  commit_rate:          Offsets committed per second
  rebalance_count:      Consumer group rebalances (cause lag spikes)
  
Health thresholds:
  Healthy:    Lag stable, < 1,000 events, growth rate ≤ 0
  Warning:    Lag growing, 1K-100K events
  Critical:   Lag growing fast, > 100K events
  Emergency:  Lag > 1M events, hours behind real-time
```

### Recovery Strategies (In Order)

```
1. Auto-scale consumers
   Add more parallel processing (up to partition count)
   Most common and least disruptive

2. Increase consumer batch size
   process 500-1000 per poll instead of 100
   Reduces per-event overhead (fewer commits, fewer network calls)

3. Optimize consumer processing
   Profile for bottlenecks (DB writes, API calls, serialization)
   Batch DB writes (insert 100 rows in one statement vs 100 statements)

4. Skip non-critical processing
   Temporarily skip enrichment, validation, or secondary writes
   Process the minimum required for each event

5. Shed low-priority events
   Drop P3/P2 events based on priority headers
   Immediate throughput improvement

6. Sample non-critical events
   Process 10% of impressions with 10x correction factor
   Maintains approximate accuracy with 10x throughput

7. Add Kafka partitions
   More partitions → more parallel consumers possible
   One-time action (partitions can't be reduced)
   Requires careful planning (partition key distribution changes)
```

---

## Layered Backpressure Strategy

### Putting It All Together

```
Complete backpressure response for an ad click pipeline:

NORMAL (lag < 100K):
  Process all events (P0, P1, P2, P3)
  2 consumers
  No special handling

ELEVATED (lag 100K-500K):
  Process all events
  Scale to 5 consumers
  Alert: #pipeline-alerts (informational)

WARNING (lag 500K-2M):
  Scale to 10 consumers
  Shed P3 events (analytics, debug logs)
  Alert: #pipeline-alerts (warning)

CRITICAL (lag 2M-10M):
  Scale to 20 consumers
  Shed P3 entirely
  Sample P2 at 50% (impression counters with correction)
  Increase consumer batch size to 500
  Alert: On-call engineer paged

EMERGENCY (lag > 10M):
  Scale to 50 consumers (maximum)
  Shed P3 entirely
  Sample P2 at 10% (10x correction)
  Sample P1 at 50% (2x correction)
  P0 (click billing) always 100%
  Skip enrichment (process raw events only)
  Alert: Senior on-call + manager notified

Recovery:
  As lag decreases, reverse the escalation levels
  Don't de-escalate too quickly (wait for lag to stabilize)
  Scale-down delayed by 10 minutes
```

---

## Real-World System Examples

### Twitter — Tweet Fanout Pipeline

```
When a celebrity tweets (10M+ followers):
  Fanout generates 10M+ events for timeline updates
  
Normal users (< 1K followers): Process all fanout events immediately
Celebrity users (> 100K followers): Queue fanout, process in background
  
Under backpressure:
  1. Kafka buffers excess fanout events (disk-based, can absorb hours)
  2. Scale fanout consumers (up to partition count)
  3. Prioritize: Active users' timelines first, inactive users last
  4. Shed: Users who haven't logged in for 30 days → skip fanout
  5. Their timelines rebuilt on next login (merge at read time)
```

### Netflix — Logging and Analytics Pipeline

```
200 billion events/day from streaming devices:

Normal: All events processed by Flink → ClickHouse
Backpressure:
  1. Kafka absorbs burst (7-day retention)
  2. Scale Flink job (parallelism 1 → 20)
  3. Sample non-critical events at 10% (playback analytics)
  4. Always process: Error events, billing events, quality metrics
  5. Backfill sampled data from Kafka during off-peak hours
```

### Stripe — Payment Processing

```
Payment events NEVER shed or sampled (P0):
  Every payment must be processed exactly once

Backpressure strategy:
  1. Internal queue (SQS) absorbs burst
  2. Auto-scale payment workers
  3. Circuit breaker on downstream bank APIs
     If bank API is slow → fail fast → return to user → user retries
  4. Rate limit per merchant (protect system from one merchant's spike)
  5. Never drop, sample, or delay payment events
```

---

## Deep Dive: Applying Backpressure to Popular Problems

### Ad Click Aggregation System

```
10M clicks/hour normal, 50M clicks/hour during Super Bowl

Architecture:
  Clicks → Kafka (50 partitions) → Flink Consumers → Redis (pre-computed aggregates)

Normal: 10 Flink consumers, each handling 1M clicks/hour
Spike:  Need 50M/hour capacity

Response:
  T+0: Kafka absorbs burst (500K events/minute excess)
  T+1min: Auto-scale Flink to 50 consumers (1 per partition)
  T+3min: Consumers caught up to burst rate
  
  If 50 consumers aren't enough:
  T+5min: Sample P2 impression events at 50%
  T+10min: Sample P2 at 10% + shed P3 entirely
  T+10min: P0 click billing always 100%
  
Post-spike:
  Scale down to 10 consumers over 30 minutes
  Reconciliation job corrects any sampling approximations
```

### Chat Message Delivery

```
1M messages/minute normal, 5M during New Year's Eve

Backpressure strategy:
  Messages are P0 (never shed or sample)
  
  1. Kafka buffers excess (messages queued, not lost)
  2. Scale delivery consumers
  3. Degrade non-critical features:
     - Disable typing indicators (saves 40% of traffic)
     - Batch presence updates (every 30s instead of real-time)
     - Disable read receipts temporarily
  4. Rate limit message sends (max 10 messages/sec per user)
  5. If still overwhelmed: Queue messages, deliver when caught up
     User sees "Message sending..." for a few seconds instead of instant delivery
```

---

## Interview Talking Points & Scripts

### The Layered Strategy

> *"I'd implement a layered backpressure strategy. Layer 1: Kafka absorbs the burst on disk — it can buffer hours of excess events. Layer 2: Auto-scale consumers based on lag (100K lag → 5 consumers, 2M lag → 20 consumers). Layer 3: If scaling isn't enough, shed P3 events (analytics, debug logs). Layer 4: Sample P2 events at 10% with a 10x correction factor — the dashboard shows '~1M impressions (estimated).' P0 click billing events are always processed at 100% because they directly impact revenue."*

### Priority Load Shedding

> *"Under 10x traffic spike, I'd use priority-based load shedding. P0 (revenue-critical clicks): always 100%. P1 (user notifications): 100% until lag > 10M, then 50%. P2 (impression counters): shed to 10% sampling during crisis. P3 (analytics): shed entirely. This preserves billing accuracy — the most important business metric — while gracefully degrading non-critical features."*

### Circuit Breaker

> *"If our downstream database becomes slow under load, I'd use a circuit breaker. After 5 timeouts in 30 seconds, the circuit opens and we return cached responses instead of blocking threads waiting for the slow database. This prevents cascading failures — without the circuit breaker, our API servers' thread pools would exhaust, making us unresponsive to ALL requests, not just the ones needing the slow database."*

### Queue Buffering

> *"Kafka is the first line of defense — it absorbs traffic spikes on disk with zero data loss. During a burst, consumer lag grows, but events are safely queued. Auto-scaling adds consumers to catch up. After the spike, consumers process the backlog and lag returns to zero. The key insight: Kafka turns a 'spike that crashes the system' into 'lag that increases and then recovers' — a much safer failure mode."*

---

## Common Interview Mistakes

### ❌ Mistake 1: No backpressure strategy (system crashes under spike)
**Why it's wrong**: Every system will face traffic spikes. Without a plan, the result is cascading failures.
**Better**: Design layered backpressure from the start.

### ❌ Mistake 2: Treating all events equally under pressure
**Why it's wrong**: Shedding payment events and analytics events equally means losing revenue AND accuracy.
**Better**: Priority-based shedding — protect revenue-critical paths, shed non-critical first.

### ❌ Mistake 3: Only relying on auto-scaling
**Why it's wrong**: Auto-scaling takes 1-3 minutes. During that gap, the system needs immediate relief.
**Better**: Queue buffering (immediate) + load shedding (immediate) + auto-scaling (1-3 min) — layered.

### ❌ Mistake 4: Not monitoring consumer lag as a health signal
**Why it's wrong**: Consumer lag is the earliest indicator of pipeline health. By the time CPU spikes, you're already in trouble.
**Better**: Alert on lag growth rate, not just absolute lag.

### ❌ Mistake 5: No circuit breaker for synchronous dependencies
**Why it's wrong**: A slow dependency causes thread pool exhaustion in the caller, leading to cascading failure.
**Better**: Circuit breaker with fallback (cached response, degraded mode).

### ❌ Mistake 6: Shedding events without mentioning reconciliation
**Why it's wrong**: Events dropped during backpressure need to be accounted for later.
**Better**: Sampled events use correction factors. Shed events are reconciled from raw logs in nightly batch.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          BACKPRESSURE & FLOW CONTROL CHEAT SHEET             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  LAYERED STRATEGIES (apply in order):                        │
│  1. BUFFER: Kafka absorbs burst on disk (no data loss)       │
│  2. SCALE: Auto-scale consumers based on lag                 │
│  3. SHED: Drop low-priority events (P3 → P2 → P1)           │
│  4. SAMPLE: 10% of non-critical + 10x correction factor     │
│  5. LIMIT: Rate limit producers (429, throttle)              │
│  6. CIRCUIT BREAK: Stop calling slow dependencies            │
│                                                              │
│  PRIORITY LEVELS:                                            │
│    P0: ALWAYS process (payments, billing clicks)             │
│    P1: Process if possible (notifications, messages)         │
│    P2: Shed under moderate pressure (feed updates, counters) │
│    P3: First to shed (analytics, debug logging)              │
│                                                              │
│  KAFKA LAG THRESHOLDS:                                       │
│    < 100K: Healthy ✅                                        │
│    100K-1M: Scale consumers ⚠️                               │
│    1M-10M: Scale + shed P3 + sample P2 🔴                    │
│    > 10M: Max scale + sample + shed + alert 🔥               │
│                                                              │
│  CIRCUIT BREAKER:                                            │
│    5 failures in 30s → OPEN (fail fast, return fallback)     │
│    30s timeout → HALF-OPEN (test one request)                │
│    Success → CLOSED (normal). Failure → OPEN (wait again)    │
│                                                              │
│  GOLDEN RULES:                                               │
│    Never crash the pipeline — degrade gracefully             │
│    Protect critical paths (revenue, security)                │
│    Auto-scaling supplements, doesn't replace, other layers   │
│    Monitor lag growth rate, not just absolute lag             │
│    Reconcile shed/sampled data in nightly batch              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 15: Rate Limiting** — Upstream backpressure via rate limits
- **Topic 10: Message Queues vs Event Streams** — Kafka as a buffering layer
- **Topic 23: Batch vs Stream Processing** — Stream processing under backpressure
- **Topic 29: Pre-Computation vs On-Demand** — Reducing read load to prevent backpressure
- **Topic 33: Reconciliation** — Correcting sampled/shed data after backpressure

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
