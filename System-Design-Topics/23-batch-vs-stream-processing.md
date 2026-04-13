# 🎯 Topic 23: Batch vs Stream Processing

> **System Design Interview — Deep Dive**
> Comprehensive coverage of batch processing (Spark), stream processing (Flink), the Lambda architecture, Kappa architecture, and when to use batch vs stream for real-time vs correctness tradeoffs.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Batch Processing](#batch-processing)
3. [Stream Processing](#stream-processing)
4. [Lambda Architecture — Stream for Speed, Batch for Truth](#lambda-architecture--stream-for-speed-batch-for-truth)
5. [Kappa Architecture — Stream-Only](#kappa-architecture--stream-only)
6. [Stream Processing Challenges](#stream-processing-challenges)
7. [When to Use Batch vs Stream](#when-to-use-batch-vs-stream)
8. [Real-Time Fraud Detection — Stream Example](#real-time-fraud-detection--stream-example)
9. [Daily Reconciliation — Batch Example](#daily-reconciliation--batch-example)
10. [Technologies](#technologies)
11. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
12. [Common Interview Mistakes](#common-interview-mistakes)
13. [Batch/Stream by System Design Problem](#batchstream-by-system-design-problem)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

| Aspect | Batch Processing | Stream Processing |
|---|---|---|
| **Data** | Bounded (finite dataset) | Unbounded (infinite event stream) |
| **Latency** | Minutes to hours | Milliseconds to seconds |
| **Accuracy** | Exact (processes all data) | Approximate (may miss late events) |
| **Complexity** | Lower | Higher (state, windows, late events) |
| **Use case** | Reports, reconciliation, ETL | Real-time dashboards, fraud detection |
| **Cost** | Spot instances (cheap) | Always-on (more expensive) |

---

## Batch Processing

### How It Works
```
1. Accumulate data over a period (hourly, daily)
2. Process entire dataset at once
3. Output results

Input:    S3 (raw events from last 24 hours)
Process:  Spark job reads all events → aggregates → computes exact counts
Output:   Write to ClickHouse / data warehouse
Schedule: Run nightly at 3 AM UTC
Duration: 2-4 hours for 200 billion events
```

### Advantages
- **Exact results**: Processes ALL data, including late-arriving events.
- **Simple**: No windowing, no watermarks, no state management.
- **Cost-effective**: Run on spot instances (60-80% cheaper).
- **Fault-tolerant**: If job fails, just restart it (data is still in S3).
- **Debuggable**: Run on a subset of data locally for testing.

### Disadvantages
- **High latency**: Results available hours after events occur.
- **Not real-time**: Can't detect fraud or anomalies in real-time.
- **Resource spikes**: Large compute needed for short periods, idle rest of the time.

### Spark Batch Example
```python
# Daily click aggregation
clicks_df = spark.read.parquet("s3://events/clicks/2025-03-15/")

result = clicks_df \
    .groupBy("campaign_id", "ad_id") \
    .agg(
        count("*").alias("click_count"),
        countDistinct("user_id").alias("unique_users"),
        sum("cost").alias("total_spend")
    )

result.write.mode("overwrite").parquet("s3://aggregates/daily/2025-03-15/")
```

---

## Stream Processing

### How It Works
```
1. Events arrive continuously (Kafka topic)
2. Process each event (or micro-batch) in real-time
3. Output results continuously

Input:    Kafka topic "click-events" (2.3M events/sec)
Process:  Flink reads events → windowed aggregation → increments counters
Output:   Write to Redis (real-time dashboard) and ClickHouse (historical)
Latency:  < 30 seconds from event to dashboard update
```

### Advantages
- **Low latency**: Results within seconds of event occurrence.
- **Real-time**: Detect fraud, anomalies, trending topics immediately.
- **Continuous**: No batch scheduling, always processing.
- **Event-driven**: React to individual events, not just aggregates.

### Disadvantages
- **Complex**: State management, windowing, watermarks, late events.
- **Approximate**: May miss late-arriving events outside the window.
- **Expensive**: Always-on compute (can't use spot instances).
- **Hard to debug**: Can't easily replay and inspect intermediate state.

### Flink Stream Example
```java
DataStream<ClickEvent> clicks = env
    .addSource(new FlinkKafkaConsumer<>("click-events", schema, properties));

clicks
    .keyBy(event -> event.getCampaignId())
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .aggregate(new ClickCounter())
    .addSink(new RedisSink<>(redisConfig, new ClickCountMapper()));
```

---

## Lambda Architecture — Stream for Speed, Batch for Truth

### The Idea
Run **both** stream and batch processing. Stream provides speed; batch provides correctness.

```
┌──────────────┐
│  Raw Events  │ → Kafka → ┌──────────────────────┐
│  (Kafka/S3)  │           │  Speed Layer (Flink)  │ → Real-time view (Redis)
└──────────────┘           │  Approximate, < 30s   │
        │                  └──────────────────────┘
        │
        └──────── S3 ──→ ┌──────────────────────┐
                         │  Batch Layer (Spark)   │ → Batch view (ClickHouse)
                         │  Exact, nightly        │
                         └──────────────────────┘
                                    │
                         ┌──────────▼──────────┐
                         │  Serving Layer       │ → Dashboard merges both views
                         │  Merges speed + batch│
                         └─────────────────────┘
```

### How Reconciliation Works
```
Real-time dashboard (speed layer): Shows ~2.3M clicks (approximate, streaming)
Nightly batch (batch layer):       Computes exactly 2,312,847 clicks
Reconciliation:                    
  Compare stream vs batch for each (campaign_id, ad_id, hour)
  If discrepancy > 0.1%: overwrite stream result with batch result
  Typically 99.98% of dimensions match exactly
```

### Interview Script
> *"I'd use Flink for real-time aggregation with < 30-second freshness, plus a daily Spark batch job that recomputes exact counts from raw events in S3. The stream gives us speed — the advertiser's dashboard updates every 30 seconds. The batch gives us truth — the nightly reconciliation job corrects any drift from late events, sampling under backpressure, or edge cases. For billing, we always use the batch-reconciled numbers. This is the Lambda architecture — stream for speed, batch for correctness."*

---

## Kappa Architecture — Stream-Only

### The Idea
Eliminate the batch layer entirely. Use **only** stream processing, with the ability to replay from Kafka for reprocessing.

```
Kafka (retained for 30 days) → Flink → Output

If results are wrong:
  1. Fix the bug
  2. Reset Flink consumer offset to 30 days ago
  3. Replay all events through the corrected pipeline
  4. Correct results produced
```

### Advantages over Lambda
- **Simpler**: One processing pipeline instead of two.
- **No reconciliation**: No need to merge batch and stream views.
- **Consistent logic**: Same code processes real-time and historical.

### Disadvantages
- **Kafka retention cost**: Must retain events for replay window (30 days = petabytes).
- **Reprocessing is slow**: Replaying 30 days through a stream pipeline takes days.
- **Some analytics are inherently batch**: Complex ML training, graph analysis.

### When Kappa Works
- When the stream pipeline is reliable enough to not need batch correction.
- When events are retained long enough for replay.
- When the processing logic is simple enough to not have subtle bugs.

---

## Stream Processing Challenges

### 1. Windowing
```
"Count clicks per hour" — but when does an hour start/end?

Tumbling window: [12:00-13:00], [13:00-14:00] — fixed, non-overlapping
Sliding window:  [12:00-13:00], [12:30-13:30] — overlapping, every 30 min
Session window:  Defined by inactivity gap — user-specific

Each has different memory and computation costs.
```

### 2. Late Events
```
Event happens at 12:58, arrives at 13:05 (7 minutes late).
The 12:00-13:00 window already closed and emitted results.

Options:
  a. Drop late event (simple, loses accuracy)
  b. Re-emit corrected window (complex, downstream must handle updates)
  c. Watermark: wait until 13:10 before closing the window (adds 10 min latency)
  
Tradeoff: Latency (wait longer) vs Accuracy (miss late events)
```

### 3. Watermarks
```
Watermark: "I believe all events up to time T have arrived"

If watermark = 12:55 → we can safely close windows ending at 12:55 or earlier.
Events arriving after the watermark are "late" → handled by late event policy.

Watermark heuristic: 
  watermark = max_event_time - allowed_lateness
  allowed_lateness = 5 minutes → watermark is 5 min behind real-time
```

### 4. State Management
```
Stateful stream processing:
  "Running count of clicks per campaign" → state: {campaign_123: 47,231}
  
Where is this state stored?
  In-memory (fast but lost on crash)
  RocksDB (local, checkpointed to S3 periodically)
  
Checkpointing: Flink snapshots state to S3 every 60 seconds.
On failure: Restore from latest checkpoint, replay from Kafka offset.
```

### Interview Script
> *"Stream processing adds significant complexity: state management (where is the running count stored?), windowing (how do you define 'per hour' when events arrive out of order?), watermarks (when is a window 'complete' enough to emit?), and late event handling (what if an event arrives 10 minutes after its window closed?). If the use case can tolerate 5-minute delay, a simple batch job reading from S3 every 5 minutes is 10x simpler to build, test, debug, and operate."*

---

## When to Use Batch vs Stream

### Decision Framework

| Question | If Yes → Stream | If Yes → Batch |
|---|---|---|
| Need results in < 1 minute? | ✅ | |
| Need to detect events in real-time? | ✅ | |
| Can tolerate 5-minute+ delay? | | ✅ |
| Need exact results for billing? | | ✅ (reconciliation) |
| Is the processing logic complex? | | ✅ (simpler to debug) |
| Want to minimize infrastructure cost? | | ✅ (spot instances) |
| Need both speed AND accuracy? | ✅ (Lambda: stream + batch) | |

### The Hybrid is Most Common
```
Most production systems use BOTH:
  Stream: Real-time dashboard, alerting, fraud detection
  Batch:  Billing reconciliation, exact reports, ML training

The stream is "good enough" for display.
The batch is "exact" for money and compliance.
```

---

## Real-Time Fraud Detection — Stream Example

```
Credit card used in New York at 12:00.
Same card used in London at 12:05.

Stream processor:
  Event: {card: "xxxx", location: "London", time: 12:05}
  State: {card "xxxx": last_location: "NY", last_time: 12:00}
  
  Compute: distance(NY, London) / time(5 min) = 5,570 km / 5 min
  Velocity: 66,840 km/hr (impossible for a human)
  
  Decision: BLOCK CARD → alert fraud team
  Latency: < 2 seconds from transaction to block

Batch can't do this: By the time the nightly job runs, 
  the fraudster has made 50 more charges.
```

### Interview Script
> *"For real-time fraud detection, streaming is non-negotiable. We need to flag 'this credit card was just used in New York and is now being used in London 5 minutes later' within seconds, not wait for a nightly batch job. Flink processes the transaction event, computes the velocity, and if it exceeds the threshold, triggers a hold on the card in real-time. The latency budget is < 2 seconds."*

---

## Daily Reconciliation — Batch Example

```
Ad click pipeline: Stream says campaign 123 got 2,312,000 clicks.
                   Batch recomputes from raw events: 2,312,847 clicks.

Discrepancy: 0.037% (847 clicks)
Cause: Late events + sampling during backpressure spike

For the dashboard: 2,312,000 is close enough (real-time).
For the invoice:   2,312,847 is required (exact, from batch).

The advertiser sees:
  Dashboard: "~2.31M clicks" (updates every 30 seconds)
  Invoice:   "2,312,847 clicks" (computed nightly, exact)
```

### Interview Script
> *"For advertiser billing, we never use the streaming numbers directly. The real-time dashboard shows streaming aggregates (fresh but approximate). The invoice uses batch-reconciled numbers (one day old but exact). An advertiser seeing 10,001 clicks on the dashboard but being billed for 10,003 would file a support ticket. An advertiser seeing yesterday's exact count on their invoice is perfectly acceptable."*

---

## Technologies

| Technology | Type | Best For | Throughput |
|---|---|---|---|
| **Apache Spark** | Batch (+ micro-batch streaming) | Large-scale ETL, ML, analytics | TB/hour |
| **Apache Flink** | Stream (true streaming) | Real-time aggregation, CEP | Millions/sec |
| **Kafka Streams** | Stream (lightweight) | Simple transformations | Thousands/sec |
| **Apache Beam** | Unified (batch + stream) | Portable pipelines | Varies |
| **AWS Kinesis** | Stream (managed) | AWS-native streaming | Millions/sec |
| **Google Dataflow** | Unified (managed Beam) | GCP-native pipelines | Varies |

---

## Interview Talking Points & Scripts

### Lambda Architecture
> *"Stream for speed (< 30s freshness), batch for truth (nightly exact counts). For billing, always use batch-reconciled numbers."*

### When NOT to Stream
> *"If 5-minute delay is acceptable, a batch job is 10x simpler. Stream processing adds windowing, watermarks, state management, and late event handling — all unnecessary if you don't need sub-minute latency."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using streaming for everything (over-engineering)
### ❌ Mistake 2: Using batch for fraud detection (too slow)
### ❌ Mistake 3: Not mentioning reconciliation for billing
### ❌ Mistake 4: Ignoring late events in stream processing
### ❌ Mistake 5: Not acknowledging the complexity cost of streaming

---

## Batch/Stream by System Design Problem

| Problem | Stream | Batch | Why |
|---|---|---|---|
| **Ad click dashboard** | Flink (real-time counts) | Spark (nightly reconciliation) | Lambda: speed + truth |
| **Fraud detection** | Flink (< 2s detection) | — | Real-time is essential |
| **Trending topics** | Flink (5-min windows) | — | Must be timely |
| **ML training** | — | Spark (large dataset) | Batch is natural fit |
| **ETL pipeline** | — | Spark (hourly) | Transformation + load |
| **Real-time metrics** | Flink → Redis | Spark → ClickHouse | Stream for display, batch for reports |
| **Log analysis** | Flink (alerting) | Spark (daily reports) | Alert in real-time, analyze in batch |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          BATCH vs STREAM PROCESSING CHEAT SHEET              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  BATCH: Process bounded dataset periodically                 │
│    Latency: Hours. Accuracy: Exact. Cost: Low (spot)         │
│    Use: Billing, reconciliation, ML training, ETL            │
│    Tool: Apache Spark                                        │
│                                                              │
│  STREAM: Process unbounded events continuously               │
│    Latency: Seconds. Accuracy: Approximate. Cost: Higher     │
│    Use: Dashboards, fraud detection, trending, alerting      │
│    Tool: Apache Flink                                        │
│                                                              │
│  LAMBDA: Stream + Batch together                             │
│    Stream = speed layer (approximate, real-time)             │
│    Batch = truth layer (exact, nightly)                      │
│    Reconciliation corrects stream drift for billing           │
│                                                              │
│  STREAM CHALLENGES: Windowing, late events, watermarks,      │
│    state management, checkpointing                           │
│                                                              │
│  RULE: If > 5-min delay is OK → batch (10x simpler)         │
│         If < 1-min needed → stream                           │
│         If need both → Lambda architecture                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
