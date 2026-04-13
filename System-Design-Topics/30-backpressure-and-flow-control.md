# 🎯 Topic 30: Backpressure & Flow Control

> **System Design Interview — Deep Dive**
> Managing overload — priority-based load shedding, sampling under backpressure, circuit breakers, auto-scaling, and queue-based buffering for traffic spikes.

---

## Table of Contents

- [🎯 Topic 30: Backpressure \& Flow Control](#-topic-30-backpressure--flow-control)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [What Causes Backpressure](#what-causes-backpressure)
  - [Priority-Based Load Shedding](#priority-based-load-shedding)
    - [The Strategy](#the-strategy)
    - [Interview Script](#interview-script)
  - [Sampling Under Backpressure](#sampling-under-backpressure)
    - [How It Works](#how-it-works)
    - [When Sampling Is Acceptable](#when-sampling-is-acceptable)
  - [Queue-Based Buffering](#queue-based-buffering)
    - [Kafka as a Buffer](#kafka-as-a-buffer)
    - [Queue Depth as Scaling Signal](#queue-depth-as-scaling-signal)
  - [Auto-Scaling as Backpressure Response](#auto-scaling-as-backpressure-response)
    - [Scaling Based on Queue Depth](#scaling-based-on-queue-depth)
    - [Scaling Based on CPU/Latency](#scaling-based-on-cpulatency)
  - [Circuit Breaker (Service-Level Backpressure)](#circuit-breaker-service-level-backpressure)
    - [How It Applies to Backpressure](#how-it-applies-to-backpressure)
  - [Rate Limiting (Client-Level Backpressure)](#rate-limiting-client-level-backpressure)
    - [Producer-Side Throttling](#producer-side-throttling)
  - [Kafka Consumer Lag Management](#kafka-consumer-lag-management)
    - [Monitoring](#monitoring)
    - [Recovery Strategies](#recovery-strategies)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [Priority Load Shedding](#priority-load-shedding)
    - [Queue Buffering](#queue-buffering)
  - [Common Interview Mistakes](#common-interview-mistakes)
    - [❌ No backpressure strategy (system crashes under spike)](#-no-backpressure-strategy-system-crashes-under-spike)
    - [❌ Treating all events equally (critical and non-critical get same priority)](#-treating-all-events-equally-critical-and-non-critical-get-same-priority)
    - [❌ Only relying on auto-scaling (takes minutes; need immediate shedding)](#-only-relying-on-auto-scaling-takes-minutes-need-immediate-shedding)
    - [❌ Not monitoring consumer lag as a health signal](#-not-monitoring-consumer-lag-as-a-health-signal)
    - [❌ No circuit breaker for sync service dependencies](#-no-circuit-breaker-for-sync-service-dependencies)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Backpressure** occurs when a downstream system can't keep up with the upstream rate. Without handling, it causes cascading failures — queues overflow, memory exhausts, services crash.

```
Normal:    Producer (100K/sec) → Consumer (100K/sec) → Balanced ✓
Overload:  Producer (500K/sec) → Consumer (100K/sec) → Queue grows → OOM crash!

Solutions:
  1. Buffer: Queue absorbs the burst (temporary)
  2. Shed:   Drop low-priority work (preserve critical)
  3. Sample: Process 10% of non-critical data (estimate rest)
  4. Scale:  Add more consumers (takes minutes)
  5. Limit:  Slow down producers (rate limiting)
```

---

## What Causes Backpressure

| Cause | Example |
|---|---|
| **Traffic spike** | Super Bowl ads → 5x normal traffic |
| **Dependency slowdown** | Database latency spike → processing slows |
| **Consumer crash** | 3 of 10 consumers crash → remaining 7 can't keep up |
| **Burst events** | Celebrity tweet → millions of fanout events |
| **Batch backfill** | Replaying historical data + live traffic simultaneously |

---

## Priority-Based Load Shedding

### The Strategy
When overwhelmed, **drop low-priority work** to ensure high-priority work completes.

```
Priority levels:
  P0 (Critical): Payment transactions, click billing → ALWAYS process
  P1 (Important): User-facing notifications → process if capacity allows
  P2 (Normal):    Search indexing, feed updates → shed under pressure
  P3 (Low):       Analytics events, logging → first to shed

Under backpressure (consumer lag > 2M):
  P0: Process 100% (revenue-critical)
  P1: Process 100% (user experience)
  P2: Process 50%  (eventual consistency is fine)
  P3: Sample 10% with 10x correction factor (approximate)
```

### Interview Script
> *"Under backpressure during a Super Bowl traffic spike (5x normal), I'd use priority-based load shedding with three levels. Level 1 (lag < 500K): process everything normally. Level 2 (lag 500K-2M): auto-scale consumers and sample impression events at 50%. Level 3 (lag > 2M): sample impressions at 10% with correction factor; clicks are always processed at 100% because they represent revenue. This preserves billing accuracy while preventing the pipeline from falling hours behind."*

---

## Sampling Under Backpressure

### How It Works
```
Normal: Process 100% of events
Backpressure: Process 10% of non-critical events, multiply by 10

Example:
  Normal: 1,000,000 impression events → count = 1,000,000
  Sampling: Process 100,000 events → count = 100,000 × 10 = 1,000,000 (estimated)
  
  Error: ±1-3% (statistically valid for large numbers)
  Throughput: 10x reduction → consumer catches up
```

### When Sampling Is Acceptable
```
✅ Impression counting (approximate is fine for display)
✅ Analytics aggregations (±2% is invisible on dashboards)
✅ Trend detection (signal is preserved at 10% sample)

❌ Click counting for billing (must be exact — revenue)
❌ Payment processing (every transaction must complete)
❌ Message delivery (every message must be delivered)
```

---

## Queue-Based Buffering

### Kafka as a Buffer
```
Producer burst: 500K events/sec (5x normal)
Consumer capacity: 100K events/sec

Without Kafka:
  Events dropped or service crashes.

With Kafka:
  Kafka absorbs 400K/sec excess → stored on disk
  Consumer lag grows: 400K × 60 sec = 24M events behind after 1 minute
  
  Auto-scaling kicks in: 100K/sec → 500K/sec (5x consumers)
  Lag decreases as consumers catch up
  
  After spike ends: Consumers catch up, lag returns to 0
```

### Queue Depth as Scaling Signal
```
CloudWatch alarm on Kafka consumer lag:
  Lag < 100K:    Normal (2 consumers)
  Lag 100K-1M:   Warning → scale to 10 consumers
  Lag 1M-10M:    Critical → scale to 50 consumers + sample P3 events
  Lag > 10M:     Emergency → max consumers + sample P2 + shed P3
```

---

## Auto-Scaling as Backpressure Response

### Scaling Based on Queue Depth
```
SQS + Auto Scaling:
  Queue depth < 1000:    2 workers
  Queue depth 1K-10K:    10 workers
  Queue depth 10K-100K:  50 workers
  Queue depth > 100K:    100 workers (max)
  
  Scale-up:   Immediate (within 1-2 minutes)
  Scale-down: Delayed by 5 minutes (avoid flapping)
```

### Scaling Based on CPU/Latency
```
CPU > 70% for 2 minutes → add 20% more instances
P99 latency > 500ms for 3 minutes → add 50% more instances
CPU < 30% for 10 minutes → remove 20% instances
```

---

## Circuit Breaker (Service-Level Backpressure)

### How It Applies to Backpressure
```
Service A calls Service B (sync):
  B is slow (backpressured) → A's threads block → A slows down → A's callers slow down
  → Cascading failure across all services

Circuit breaker stops the cascade:
  After 5 timeouts → circuit OPEN → A returns fallback immediately
  A is no longer blocked → A's callers are unaffected
  B gets time to recover (reduced load)
```

---

## Rate Limiting (Client-Level Backpressure)

### Producer-Side Throttling
```
If Kafka producer can't send fast enough (broker backpressure):
  Option 1: Block (wait until space available) — simple but risky
  Option 2: Drop (best-effort events like analytics)
  Option 3: Buffer locally (in-memory queue, flush when space available)
  Option 4: Return 429 to client (HTTP rate limiting)
```

---

## Kafka Consumer Lag Management

### Monitoring
```
Consumer lag = latest offset - consumer's committed offset

Metrics to monitor:
  lag_per_partition:  How far behind each partition's consumer is
  lag_growth_rate:    Is lag increasing or decreasing?
  processing_rate:    Events processed per second per consumer
  
  Healthy:    Lag stable, < 1000 events
  Warning:    Lag growing, 1K-100K events
  Critical:   Lag growing fast, > 100K events
  Emergency:  Lag > 1M, hours behind real-time
```

### Recovery Strategies
```
1. Auto-scale consumers (add more parallel processing)
2. Increase consumer batch size (process 100-1000 per poll)
3. Skip non-critical events (sample or drop P3)
4. Temporarily reduce processing complexity (skip enrichment)
5. Add Kafka partitions (more parallelism — but one-time action)
```

---

## Interview Talking Points & Scripts

### Priority Load Shedding
> *"Under 5x traffic spike: clicks always at 100% (revenue). Impressions sampled at 10% with correction factor. Analytics shed entirely. Preserves billing accuracy while preventing pipeline collapse."*

### Queue Buffering
> *"Kafka absorbs traffic spikes on disk. Consumer lag grows during spike. Auto-scaling adds consumers to catch up. After spike, lag returns to zero. No data loss."*

---

## Common Interview Mistakes

### ❌ No backpressure strategy (system crashes under spike)
### ❌ Treating all events equally (critical and non-critical get same priority)
### ❌ Only relying on auto-scaling (takes minutes; need immediate shedding)
### ❌ Not monitoring consumer lag as a health signal
### ❌ No circuit breaker for sync service dependencies

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          BACKPRESSURE & FLOW CONTROL CHEAT SHEET             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  STRATEGIES (layered):                                       │
│  1. BUFFER: Kafka absorbs burst on disk                      │
│  2. SCALE: Auto-scale consumers based on lag                 │
│  3. SHED: Drop low-priority events (P3 first)                │
│  4. SAMPLE: 10% of non-critical + correction factor          │
│  5. LIMIT: Rate limit producers (429)                        │
│  6. CIRCUIT BREAK: Stop calling slow dependencies            │
│                                                              │
│  PRIORITY:                                                   │
│    P0: Always process (payments, billing clicks)             │
│    P1: Process if possible (notifications)                   │
│    P2: Shed under pressure (search indexing)                 │
│    P3: First to shed/sample (analytics, logging)             │
│                                                              │
│  KAFKA LAG MONITORING:                                       │
│    < 1K: Healthy                                             │
│    1K-100K: Scale consumers                                  │
│    > 100K: Scale + sample + shed                             │
│                                                              │
│  RULE: Protect critical data paths (revenue).                │
│  Shed non-critical gracefully. Never crash the pipeline.     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
