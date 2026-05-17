# 🎯 Topic 56: Multi-Window Stream Aggregation

> **System Design Interview — Deep Dive**
> A comprehensive guide covering multi-window stream aggregation patterns using Apache Flink — producing minute, hour, and day aggregates from a single pipeline, tiered storage (Redis → ClickHouse SSD → ClickHouse HDD), partial flush strategies, query routing across storage tiers, late event handling, exactly-once semantics, real-world applications (ad analytics, metrics dashboards, billing), and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Multi-Window Aggregation Is Hard](#why-multi-window-aggregation-is-hard)
3. [The Three-Window Architecture](#the-three-window-architecture)
4. [Apache Flink Window Mechanics](#apache-flink-window-mechanics)
5. [Partial Flush Strategy — The 10-Second Heartbeat](#partial-flush-strategy--the-10-second-heartbeat)
6. [Tiered Storage Architecture](#tiered-storage-architecture)
7. [Redis Tier — Hot Minute Aggregates](#redis-tier--hot-minute-aggregates)
8. [ClickHouse Tier — Warm and Cold Aggregates](#clickhouse-tier--warm-and-cold-aggregates)
9. [Query Router — Merging Across Tiers](#query-router--merging-across-tiers)
10. [Late Events and Watermarks](#late-events-and-watermarks)
11. [Exactly-Once Semantics Across Tiers](#exactly-once-semantics-across-tiers)
12. [State Management and Checkpointing](#state-management-and-checkpointing)
13. [Keyed Aggregation — Dimensions and Cardinality](#keyed-aggregation--dimensions-and-cardinality)
14. [Rollup Hierarchies — Minute → Hour → Day](#rollup-hierarchies--minute--hour--day)
15. [Backpressure and Flow Control](#backpressure-and-flow-control)
16. [Schema Design for Multi-Window Aggregates](#schema-design-for-multi-window-aggregates)
17. [Performance Tuning & Benchmarks](#performance-tuning--benchmarks)
18. [Real-World Production Patterns](#real-world-production-patterns)
19. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
20. [Common Interview Mistakes](#common-interview-mistakes)
21. [Multi-Window Aggregation by System Design Problem](#multi-window-aggregation-by-system-design-problem)
22. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Multi-window stream aggregation** processes a continuous event stream through multiple time windows simultaneously (e.g., 1-minute, 1-hour, 1-day) in a single pipeline, flushing each window's results to a different storage tier optimized for that granularity's access pattern.

```
The fundamental pattern:

  Input: Continuous stream of events (ad clicks, page views, API calls)
         1M events/sec

  Processing: Apache Flink with 3 concurrent windows
    Window 1: 1-minute tumbling → counts, sums, averages per minute
    Window 2: 1-hour tumbling   → counts, sums, averages per hour
    Window 3: 1-day tumbling    → counts, sums, averages per day

  Output: Each window flushes to the optimal storage tier
    Minute → Redis          (in-memory, sub-ms reads, 24-hour retention)
    Hour   → ClickHouse SSD (fast analytics, 90-day retention)
    Day    → ClickHouse HDD (cheap storage, multi-year retention)

  Query: A unified query router merges results across tiers
    "Show me ad impressions for campaign X"
      Last hour:  → Redis (minute granularity)
      Last week:  → ClickHouse SSD (hour granularity)
      Last year:  → ClickHouse HDD (day granularity)
```

### Why This Matters

```
Without multi-window aggregation:
  Option A: Store raw events and aggregate on read
    1M events/sec × 86,400 sec/day = 86.4 BILLION events/day
    "Give me hourly clicks for campaign X last month" → scan 2.6 TRILLION events
    Latency: Minutes. Cost: $$$$$. Doesn't scale.

  Option B: Run 3 separate pipelines (one per window)
    3 Flink jobs consuming the same Kafka topic
    3× compute cost, 3× Kafka consumer bandwidth
    Possible consistency issues between pipelines

With multi-window aggregation:
  One Flink pipeline, 3 windows running concurrently
    Pre-aggregated: minute=1440 rows/day, hour=24 rows/day, day=1 row/day
    "Give me hourly clicks last month" → scan 720 rows (not trillions)
    Latency: Milliseconds. Cost: $. Scales beautifully.
```

---

## Why Multi-Window Aggregation Is Hard

### Challenge 1: Different Flush Cadences

```
A single Flink pipeline must flush at 3 different rates:

  Minute window: Fires every 60 seconds
  Hour window:   Fires every 3,600 seconds
  Day window:    Fires every 86,400 seconds

  But users expect real-time dashboards — they can't wait 60 seconds
  for the first data point. Solution: Partial flushes every 10 seconds.

  This means the pipeline has 5 different output cadences:
    10s  → partial minute aggregate → Redis (in-progress)
    60s  → final minute aggregate   → Redis (complete)
    60s  → partial hour aggregate   → Redis (in-progress)
    3600s → final hour aggregate    → ClickHouse SSD (complete)
    86400s → final day aggregate    → ClickHouse HDD (complete)
```

### Challenge 2: Different Storage Tiers

```
Each window granularity has different access patterns:

  Minute aggregates:
    Access pattern: Read-heavy, low-latency, recent data only
    Best storage: Redis (in-memory, sub-ms, auto-expire old data)
    Retention: 24-48 hours

  Hour aggregates:
    Access pattern: Moderate reads, analytical queries, medium history
    Best storage: ClickHouse on SSD (fast OLAP scans)
    Retention: 30-90 days

  Day aggregates:
    Access pattern: Infrequent reads, long-term trends, dashboards
    Best storage: ClickHouse on HDD (cheap, high capacity)
    Retention: 1-5 years

  Writing to 3 different storage systems atomically = hard.
```

### Challenge 3: Late Events

```
Event time: When the event actually happened (user clicked at 12:00:00)
Processing time: When the event arrives at Flink (received at 12:00:05)

  Events can arrive LATE:
    User on a subway clicks ad at 12:00:00
    Phone goes offline for 3 minutes
    Event arrives at Flink at 12:03:00

  The 12:00 minute window already fired!
  The event should be in the 12:00 window, not the 12:03 window.

  Solution: Watermarks + allowed lateness + side outputs
  See Section 10.
```

### Challenge 4: Cross-Tier Query Merging

```
"Show me impressions for the last 2 hours"

  Current partial hour: Redis (minute-level, in-progress)
  Previous complete hour: ClickHouse SSD (hour-level, complete)

  The query router must:
    1. Determine which tiers to query
    2. Fetch data from each tier
    3. Merge results (sum the partial + complete)
    4. Return a unified response

  Edge case: The current hour's partial is overwritten every 10 seconds.
  The query must read a consistent snapshot.
```

### Challenge 5: Exactly-Once Across Tiers

```
Flink's checkpoint fires. It must write to Redis AND ClickHouse.
If Redis write succeeds but ClickHouse write fails → inconsistency.

  Solution: Idempotent writes with version/checkpoint IDs.
  See Section 11.
```

---

## The Three-Window Architecture

### High-Level Architecture

```
┌──────────┐         ┌───────────────────────────────────────┐
│  Events   │         │            Apache Flink                │
│  (Kafka)  │────────→│                                       │
│  1M/sec   │         │  ┌────────────────────────────────┐   │
└──────────┘         │  │  KeyBy(campaign_id, ad_id)     │   │
                     │  └───────────┬────────────────────┘   │
                     │              │                         │
                     │    ┌─────────▼──────────┐             │
                     │    │  3 Concurrent       │             │
                     │    │  Tumbling Windows   │             │
                     │    │                     │             │
                     │    │  ┌──────────────┐   │             │
                     │    │  │ 1-Min Window │───┼───→ Redis (minute)
                     │    │  └──────────────┘   │             │
                     │    │  ┌──────────────┐   │             │
                     │    │  │ 1-Hour Window│───┼───→ ClickHouse SSD (hour)
                     │    │  └──────────────┘   │             │
                     │    │  ┌──────────────┐   │             │
                     │    │  │ 1-Day Window │───┼───→ ClickHouse HDD (day)
                     │    │  └──────────────┘   │             │
                     │    └────────────────────┘             │
                     │                                       │
                     │  10-sec trigger → partial to Redis    │
                     │                                       │
                     └───────────────────────────────────────┘
                                     │
                     ┌───────────────▼───────────────────┐
                     │          Query Router              │
                     │                                    │
                     │  "Last 2 hours of impressions"     │
                     │  → Redis (current partial hour)    │
                     │  → ClickHouse SSD (previous hour)  │
                     │  → Merge and return                │
                     └────────────────────────────────────┘
```

### Data Flow Timeline

```
T=0s     Event arrives in Kafka
T=0.01s  Flink consumes event, routes by key (campaign_id)
T=0.01s  Event added to all 3 window accumulators simultaneously

T=10s    10-second trigger fires:
           Partial minute aggregate → Redis (overwrites previous partial)
           "campaign:42:minute:12:00 → {impressions: 4521, clicks: 87}" (in-progress)

T=20s    10-second trigger fires again:
           "campaign:42:minute:12:00 → {impressions: 9102, clicks: 174}" (updated)

T=60s    Minute window closes:
           FINAL minute aggregate → Redis
           "campaign:42:minute:12:00 → {impressions: 27305, clicks: 521}" (complete)
           Mark as final: SET campaign:42:minute:12:00:status "FINAL"

T=60s    Partial hour aggregate → Redis (optional, for real-time dashboards)
           "campaign:42:hour:12:00 → {impressions: 27305, clicks: 521}" (1 min of 60)

T=3600s  Hour window closes:
           FINAL hour aggregate → ClickHouse SSD
           "campaign:42 | 2024-03-15 12:00 | 1,638,300 impressions | 31,260 clicks"
           Clean up hour partials from Redis

T=86400s Day window closes:
           FINAL day aggregate → ClickHouse HDD
           "campaign:42 | 2024-03-15 | 39,319,200 impressions | 750,240 clicks"
```

---

## Apache Flink Window Mechanics

### Tumbling Windows (Used for Aggregation)

```
A tumbling window divides the stream into non-overlapping, fixed-size chunks.

  Minute tumbling windows:
  ─────┬───────────┬───────────┬───────────┬─────
       │ 12:00-    │ 12:01-    │ 12:02-    │
       │ 12:01     │ 12:02     │ 12:03     │
       │ Window 1  │ Window 2  │ Window 3  │
  ─────┴───────────┴───────────┴───────────┴─────

  Each event belongs to exactly ONE window.
  No overlap. No gaps. Clean aggregation boundaries.
```

### Three Windows on One Stream

```java
// Flink Java: Three concurrent tumbling windows on the same keyed stream

DataStream<AdEvent> events = env
    .addSource(new FlinkKafkaConsumer<>("ad-events", ...))
    .assignTimestampsAndWatermarks(
        WatermarkStrategy
            .<AdEvent>forBoundedOutOfOrderness(Duration.ofSeconds(30))
            .withTimestampAssigner((event, ts) -> event.getEventTime())
    );

KeyedStream<AdEvent, String> keyed = events
    .keyBy(event -> event.getCampaignId() + ":" + event.getAdId());

// Window 1: Minute aggregation
keyed
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .trigger(ContinuousEventTimeTrigger.of(Time.seconds(10)))  // Partial every 10s
    .aggregate(new AdMetricsAggregator(), new MinuteWindowFunction())
    .addSink(new RedisSink(...));

// Window 2: Hour aggregation
keyed
    .window(TumblingEventTimeWindows.of(Time.hours(1)))
    .trigger(ContinuousEventTimeTrigger.of(Time.minutes(1)))   // Partial every 1 min
    .aggregate(new AdMetricsAggregator(), new HourWindowFunction())
    .addSink(new ClickHouseSSDSink(...));

// Window 3: Day aggregation
keyed
    .window(TumblingEventTimeWindows.of(Time.days(1)))
    .trigger(ContinuousEventTimeTrigger.of(Time.hours(1)))     // Partial every 1 hour
    .aggregate(new AdMetricsAggregator(), new DayWindowFunction())
    .addSink(new ClickHouseHDDSink(...));
```

### The ContinuousEventTimeTrigger — The Key Enabler

```
Without continuous trigger:
  Minute window fires at T=60s. For the first 59 seconds, the dashboard shows nothing.
  User experience: "The dashboard updates once per minute." → too slow.

With continuous trigger (every 10 seconds):
  T=10s: Partial result emitted (in-progress aggregate so far)
  T=20s: Updated partial result emitted
  T=30s: Updated partial result emitted
  ...
  T=60s: FINAL result emitted (window closes)
  
  User experience: "Dashboard updates every 10 seconds with partial data,
                    confirmed every 60 seconds with final data." → real-time feel.

How it works:
  ContinuousEventTimeTrigger.of(Time.seconds(10))
  ← registers a timer that fires every 10 seconds within the window
  ← each fire emits the CURRENT accumulator state (partial)
  ← when the window closes, emits the FINAL accumulator state
  
  The sink can distinguish partial from final via a flag in the WindowFunction output.
```

### Aggregate Function — Incremental Computation

```java
public class AdMetricsAggregator implements AggregateFunction<
        AdEvent,           // Input
        AdMetrics,         // Accumulator
        AdMetrics> {       // Output

    @Override
    public AdMetrics createAccumulator() {
        return new AdMetrics(0L, 0L, 0L, 0.0);
        //                   impressions, clicks, conversions, spend
    }

    @Override
    public AdMetrics add(AdEvent event, AdMetrics acc) {
        return new AdMetrics(
            acc.impressions + (event.isImpression() ? 1 : 0),
            acc.clicks      + (event.isClick() ? 1 : 0),
            acc.conversions  + (event.isConversion() ? 1 : 0),
            acc.spend        + event.getCost()
        );
    }

    @Override
    public AdMetrics getResult(AdMetrics acc) { return acc; }

    @Override
    public AdMetrics merge(AdMetrics a, AdMetrics b) {
        return new AdMetrics(
            a.impressions + b.impressions,
            a.clicks + b.clicks,
            a.conversions + b.conversions,
            a.spend + b.spend
        );
    }
}
```

```
Why AggregateFunction (not ProcessWindowFunction):
  ProcessWindowFunction: Buffers ALL events in state, processes on window fire.
    1-minute window × 1M events/sec = 60M events in memory per key. OOM!
  
  AggregateFunction: Maintains a SINGLE accumulator. Each event updates it.
    O(1) memory regardless of event count. Incrementally computes the aggregate.
    
  For multi-window: This is CRITICAL. Three windows × O(1) each = 3 accumulators per key.
```

---

## Partial Flush Strategy — The 10-Second Heartbeat

### Why Partial Flushes

```
Problem: Users want real-time dashboards. A 1-minute window means no data for 59 seconds.

Solution: Partial flushes every 10 seconds within each window.

  T=0-10s:  500K events processed → partial: {impressions: 500000}
  T=10-20s: 500K more events    → partial: {impressions: 1000000} (cumulative)
  T=20-30s: 500K more events    → partial: {impressions: 1500000}
  ...
  T=50-60s: 500K more events    → FINAL:   {impressions: 3000000}

  The dashboard shows:
    T=10s: "~500K impressions this minute (updating...)"
    T=20s: "~1M impressions this minute (updating...)"
    ...
    T=60s: "3M impressions this minute ✓"
```

### Partial vs Final — Tagging

```
Each emitted aggregate is tagged as PARTIAL or FINAL:

  {
    "key": "campaign:42:ad:789",
    "window_start": "2024-03-15T12:00:00Z",
    "window_end": "2024-03-15T12:01:00Z",
    "granularity": "MINUTE",
    "status": "PARTIAL",           ← or "FINAL"
    "emitted_at": "2024-03-15T12:00:10Z",
    "metrics": {
      "impressions": 500000,
      "clicks": 9500,
      "conversions": 380,
      "spend": 4750.00,
      "ctr": 0.019                 ← clicks / impressions
    }
  }

Why tag matters:
  - PARTIAL: Overwrite previous partial (upsert in Redis)
  - FINAL: Write to final storage, mark as complete
  - Dashboard shows PARTIAL with "~" prefix and "updating..." indicator
  - API distinguishes: "This data is preliminary" vs "This data is final"
```

### Flush Cadence per Window

```
┌─────────────┬──────────────┬────────────────┬───────────────────┐
│ Window      │ Partial Freq │ Final Freq     │ Destination       │
├─────────────┼──────────────┼────────────────┼───────────────────┤
│ 1-Minute    │ Every 10s    │ Every 60s      │ Redis             │
│ 1-Hour      │ Every 60s    │ Every 3600s    │ ClickHouse SSD    │
│ 1-Day       │ Every 3600s  │ Every 86400s   │ ClickHouse HDD    │
└─────────────┴──────────────┴────────────────┴───────────────────┘

Partial = "in-progress aggregate so far"
Final   = "window closed, this is the complete result"

Partials are overwritten on each flush (upsert semantics).
Finals are written once and never overwritten (append-only).
```

---

## Tiered Storage Architecture

### The Storage Pyramid

```
                ┌─────────────┐
                │    Redis     │  ← HOT tier
                │  (in-memory) │  ← Minute granularity
                │  Sub-ms reads│  ← 24-48 hour retention
                │  ~50 GB      │  ← $$$$ per GB
                └──────┬──────┘
                       │
              ┌────────▼────────┐
              │  ClickHouse SSD  │  ← WARM tier
              │  (columnar OLAP) │  ← Hour granularity
              │  1-10ms reads    │  ← 30-90 day retention
              │  ~2 TB           │  ← $$ per GB
              └────────┬────────┘
                       │
            ┌──────────▼──────────┐
            │   ClickHouse HDD    │  ← COLD tier
            │   (columnar OLAP)   │  ← Day granularity
            │   10-100ms reads    │  ← 1-5 year retention
            │   ~50 TB            │  ← $ per GB
            └─────────────────────┘
```

### Why This Specific Tier Mapping

```
Minute aggregates → Redis:
  - Dashboard auto-refreshes every 10 seconds
  - Needs sub-millisecond reads for real-time UX
  - Only recent data (last 24h) → small dataset
  - TTL handles auto-cleanup: keys expire after 48 hours
  - Redis sorted sets for time-range queries

Hour aggregates → ClickHouse SSD:
  - Analytical queries: "Hourly clicks for campaign X last 30 days"
  - 720 rows/month/campaign → small scans
  - ClickHouse columnar format: scans only needed columns
  - SSD for consistent low-latency (1-10ms)
  - MergeTree engine with partitioning by date

Day aggregates → ClickHouse HDD:
  - Historical trends: "Daily spend for campaign X last 2 years"
  - 730 rows/2years/campaign → tiny scan
  - Accessed infrequently → HDD cost savings (10x cheaper than SSD)
  - Same ClickHouse SQL interface
  - TTLs auto-move data from SSD to HDD after 90 days
```

### Cost Comparison

```
Storing 1 year of ad metrics for 100K campaigns:

  Raw events (no pre-aggregation):
    1M events/sec × 86,400 sec × 365 days × 500 bytes/event
    = 15.8 PB → ~$300K/month in S3 alone
    Query: Scan petabytes → minutes per query

  With multi-window aggregation:
    Minute: 100K campaigns × 525,600 min/year × 100 bytes = 5.3 TB
    Hour:   100K campaigns × 8,760 hours/year × 100 bytes = 88 GB
    Day:    100K campaigns × 365 days/year × 100 bytes = 3.7 GB

    Redis (last 48h of minutes): ~500 MB → $50/month
    ClickHouse SSD (90d of hours): ~22 GB → $100/month
    ClickHouse HDD (1yr of days + older hours): ~5.3 TB → $200/month

    Total: ~$350/month (vs $300K/month for raw events)
    Query: Scan megabytes → milliseconds per query
    
    857x cost reduction. 1000x query speed improvement.
```

---

## Redis Tier — Hot Minute Aggregates

### Data Model

```
Key pattern: metrics:{granularity}:{key}:{window_start}

  metrics:minute:campaign:42:ad:789:2024-03-15T12:00
  → Hash: { impressions: 27305, clicks: 521, spend: 6538.25, status: FINAL }
  
  metrics:minute:campaign:42:ad:789:2024-03-15T12:01
  → Hash: { impressions: 15210, clicks: 304, spend: 3802.50, status: PARTIAL }

  metrics:hour_partial:campaign:42:ad:789:2024-03-15T12:00
  → Hash: { impressions: 42515, clicks: 825, spend: 10340.75, status: PARTIAL }

TTL:
  Minute keys: EXPIRE 172800 (48 hours)
  Hour partial keys: EXPIRE 7200 (2 hours — replaced by ClickHouse final)
```

### Writing Partials (Upsert)

```python
# Called every 10 seconds from Flink's minute window trigger

def flush_minute_partial(redis, campaign_id, ad_id, window_start, metrics, is_final):
    key = f"metrics:minute:{campaign_id}:{ad_id}:{window_start}"
    
    pipe = redis.pipeline()
    pipe.hmset(key, {
        'impressions': metrics.impressions,
        'clicks': metrics.clicks,
        'conversions': metrics.conversions,
        'spend': str(metrics.spend),
        'ctr': str(metrics.clicks / max(1, metrics.impressions)),
        'status': 'FINAL' if is_final else 'PARTIAL',
        'updated_at': time.time()
    })
    pipe.expire(key, 172800)  # 48 hours
    pipe.execute()
```

### Reading Minute Aggregates

```python
# "Give me minute-by-minute clicks for campaign:42 in the last hour"

def get_minute_aggregates(redis, campaign_id, ad_id, start_time, end_time):
    results = []
    current = start_time
    
    pipe = redis.pipeline()
    keys = []
    while current < end_time:
        key = f"metrics:minute:{campaign_id}:{ad_id}:{current.isoformat()}"
        pipe.hgetall(key)
        keys.append(current)
        current += timedelta(minutes=1)
    
    raw_results = pipe.execute()
    
    for i, data in enumerate(raw_results):
        if data:
            results.append({
                'timestamp': keys[i],
                'impressions': int(data.get(b'impressions', 0)),
                'clicks': int(data.get(b'clicks', 0)),
                'status': data.get(b'status', b'').decode(),
            })
    
    return results
```

### Memory Sizing for Redis

```
Per minute aggregate key:
  Key: ~60 bytes (metrics:minute:campaign:42:ad:789:2024-03-15T12:00)
  Hash: 6 fields × ~20 bytes each = ~120 bytes
  Overhead: ~100 bytes
  Total: ~280 bytes per key

100K campaigns × 10 ads/campaign × 1440 minutes/day = 1.44B keys/day
But we only retain 48 hours: 2.88B keys × 280 bytes = 806 GB

That's too much for Redis! Solution: Only keep high-cardinality dimensions for recent data.

Optimization:
  Last 1 hour: per-campaign × per-ad (minute granularity) → 60M keys × 280B = 16.8 GB
  Last 24 hours: per-campaign only (minute granularity)   → 144M keys × 280B = 40.3 GB
  Hour partials: per-campaign × per-ad                    → 1M keys × 280B = 280 MB

  Total: ~57 GB → fits in a 64 GB Redis Cluster (8 shards × 8 GB).
```

---

## ClickHouse Tier — Warm and Cold Aggregates

### Table Schema

```sql
-- Hour aggregates (SSD storage)
CREATE TABLE ad_metrics_hourly
(
    campaign_id    UInt64,
    ad_id          UInt64,
    window_start   DateTime,
    impressions    UInt64,
    clicks         UInt64,
    conversions    UInt64,
    spend          Decimal(18, 4),
    ctr            Float32,
    created_at     DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(window_start)     -- Partition by month
ORDER BY (campaign_id, ad_id, window_start)
TTL window_start + INTERVAL 90 DAY     -- Auto-delete after 90 days
SETTINGS storage_policy = 'ssd_policy';

-- Day aggregates (HDD storage)
CREATE TABLE ad_metrics_daily
(
    campaign_id    UInt64,
    ad_id          UInt64,
    window_start   Date,
    impressions    UInt64,
    clicks         UInt64,
    conversions    UInt64,
    spend          Decimal(18, 4),
    ctr            Float32,
    created_at     DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYear(window_start)       -- Partition by year
ORDER BY (campaign_id, ad_id, window_start)
TTL window_start + INTERVAL 5 YEAR     -- Auto-delete after 5 years
SETTINGS storage_policy = 'hdd_policy';
```

### ClickHouse Storage Policies (SSD → HDD Tiering)

```xml
<!-- ClickHouse storage policy config -->
<storage_configuration>
    <disks>
        <ssd>
            <path>/mnt/ssd/clickhouse/</path>
            <keep_free_space_bytes>10737418240</keep_free_space_bytes>  <!-- 10 GB -->
        </ssd>
        <hdd>
            <path>/mnt/hdd/clickhouse/</path>
            <keep_free_space_bytes>107374182400</keep_free_space_bytes>  <!-- 100 GB -->
        </hdd>
    </disks>
    <policies>
        <ssd_policy>
            <volumes>
                <ssd_volume>
                    <disk>ssd</disk>
                </ssd_volume>
            </volumes>
        </ssd_policy>
        <hdd_policy>
            <volumes>
                <hdd_volume>
                    <disk>hdd</disk>
                </hdd_volume>
            </volumes>
        </hdd_policy>
        <tiered_policy>
            <volumes>
                <hot>
                    <disk>ssd</disk>
                </hot>
                <cold>
                    <disk>hdd</disk>
                </cold>
            </volumes>
            <move_factor>0.1</move_factor>
        </tiered_policy>
    </policies>
</storage_configuration>
```

### Writing Hour/Day Aggregates from Flink

```java
// Flink ClickHouse Sink (batched inserts for efficiency)

public class ClickHouseSink extends RichSinkFunction<AggregatedMetrics> {
    private ClickHouseConnection connection;
    private List<AggregatedMetrics> buffer = new ArrayList<>();
    private static final int BATCH_SIZE = 10000;

    @Override
    public void invoke(AggregatedMetrics value, Context context) throws Exception {
        buffer.add(value);
        if (buffer.size() >= BATCH_SIZE) {
            flush();
        }
    }

    private void flush() throws Exception {
        if (buffer.isEmpty()) return;
        
        String sql = "INSERT INTO ad_metrics_hourly VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (AggregatedMetrics m : buffer) {
                stmt.setLong(1, m.campaignId);
                stmt.setLong(2, m.adId);
                stmt.setTimestamp(3, m.windowStart);
                stmt.setLong(4, m.impressions);
                stmt.setLong(5, m.clicks);
                stmt.setLong(6, m.conversions);
                stmt.setBigDecimal(7, m.spend);
                stmt.setFloat(8, m.ctr);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        buffer.clear();
    }
}
```

---

## Query Router — Merging Across Tiers

### How the Query Router Works

```
User query: "Impressions for campaign:42 from 2 days ago to now"

  Time range: 2024-03-13 12:00 → 2024-03-15 14:35

  Query Router decision:
    1. 2024-03-13 12:00 → 2024-03-14 23:59 (2 full days)
       → ClickHouse HDD (daily aggregates, if available)
       → OR ClickHouse SSD (hourly aggregates)
       
    2. 2024-03-15 00:00 → 2024-03-15 13:59 (14 complete hours today)
       → ClickHouse SSD (hourly aggregates)
    
    3. 2024-03-15 14:00 → 2024-03-15 14:35 (current partial hour)
       → Redis (minute aggregates for the last 35 minutes)
    
  Merge: SUM(daily) + SUM(hourly) + SUM(minutes) = total impressions
```

### Query Router Implementation

```python
class QueryRouter:
    """Routes time-range queries to the optimal storage tier."""
    
    def __init__(self, redis_client, clickhouse_client):
        self.redis = redis_client
        self.ch = clickhouse_client
    
    def query(self, campaign_id, start_time, end_time, metric='impressions'):
        now = datetime.utcnow()
        results = []
        
        # Tier 1: Days — for full completed days in range
        full_days = self._get_full_days(start_time, end_time)
        if full_days:
            day_start, day_end = full_days
            ch_daily = self.ch.execute(
                """SELECT sum(impressions), sum(clicks), sum(spend)
                   FROM ad_metrics_daily
                   WHERE campaign_id = %(cid)s
                     AND window_start >= %(start)s
                     AND window_start < %(end)s""",
                {'cid': campaign_id, 'start': day_start, 'end': day_end}
            )
            results.append(('daily', ch_daily))
        
        # Tier 2: Hours — for complete hours not covered by days
        remaining_hours = self._get_remaining_hours(start_time, end_time, full_days)
        if remaining_hours:
            h_start, h_end = remaining_hours
            ch_hourly = self.ch.execute(
                """SELECT sum(impressions), sum(clicks), sum(spend)
                   FROM ad_metrics_hourly
                   WHERE campaign_id = %(cid)s
                     AND window_start >= %(start)s
                     AND window_start < %(end)s""",
                {'cid': campaign_id, 'start': h_start, 'end': h_end}
            )
            results.append(('hourly', ch_hourly))
        
        # Tier 3: Minutes — for the current incomplete hour
        if end_time >= now.replace(minute=0, second=0):
            minute_results = self._query_redis_minutes(
                campaign_id,
                max(start_time, now.replace(minute=0, second=0)),
                end_time
            )
            results.append(('minute', minute_results))
        
        # Merge all tiers
        return self._merge_results(results)
    
    def _merge_results(self, tier_results):
        """Sum metrics across all tiers."""
        total = {'impressions': 0, 'clicks': 0, 'spend': 0.0}
        for tier_name, data in tier_results:
            total['impressions'] += data['impressions']
            total['clicks'] += data['clicks']
            total['spend'] += data['spend']
        total['tiers_queried'] = [t[0] for t in tier_results]
        return total
```

### Handling Tier Overlap and Gaps

```
Edge case 1: Hour aggregate exists in ClickHouse, but also in Redis (not yet cleaned up)
  Solution: ClickHouse data wins for complete hours. Redis used only for incomplete current hour.
  Query router checks window completeness (status = FINAL in ClickHouse).

Edge case 2: Hour aggregate not yet written to ClickHouse (pipeline lag)
  Solution: Fall back to summing minute aggregates from Redis for that hour.
  "If ClickHouse doesn't have hour X, sum Redis minutes for hour X."

Edge case 3: Day aggregate not yet written (it's still mid-day)
  Solution: Sum hour aggregates from ClickHouse + current hour partials from Redis.
  Day aggregates only used for fully completed days.

Priority waterfall:
  Day (complete) > Hour (complete) > Minute (partial or complete)
  Never double-count the same time range from two tiers.
```

---

## Late Events and Watermarks

### Watermark Strategy

```
Watermarks tell Flink: "All events with timestamp ≤ W have arrived."

  Configuration:
    WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(30))
    
  Meaning: Events can arrive up to 30 seconds late.
    If current event time = 12:01:00, watermark = 12:00:30.
    The 12:00 minute window closes when watermark passes 12:01:00.
    Events arriving before 12:01:30 (actual clock) with timestamp in [12:00, 12:01)
    are still included in the 12:00 window.

  Tradeoff:
    More lateness allowed → more accurate → higher latency (windows close later)
    Less lateness allowed → lower latency → more events dropped as "too late"
    
    30 seconds is a good default for ad analytics.
    5 minutes for mobile events (users go offline on subways).
```

### Handling Late Events That Miss the Window

```
Event arrives at Flink with timestamp 12:00:30, but the 12:00 minute window
already fired at 12:01:30 (30-second lateness allowed).
Event arrives at 12:02:00 → 90 seconds late → missed the window!

Three strategies:

Strategy 1: Allowed lateness + late side output
  .window(TumblingEventTimeWindows.of(Time.minutes(1)))
  .allowedLateness(Time.minutes(5))    // Keep window open 5 extra minutes
  .sideOutputLateData(lateOutputTag)   // Events beyond 5 min → side output
  
  The window re-fires when late events arrive (updated result).
  Events more than 5 minutes late go to a "late events" side output.
  
Strategy 2: Corrections table
  Late events → written to a "corrections" table in ClickHouse.
  Query router adds corrections to the main aggregate.
  
  SELECT base.impressions + COALESCE(corrections.impressions, 0)
  FROM ad_metrics_hourly base
  LEFT JOIN ad_corrections corrections
    ON base.campaign_id = corrections.campaign_id
    AND base.window_start = corrections.window_start

Strategy 3: Accept inaccuracy (pragmatic)
  For ad analytics: 0.01% of events arriving >30 seconds late
  → losing 0.01% accuracy is acceptable
  → simpler pipeline, no corrections logic
```

---

## Exactly-Once Semantics Across Tiers

### The Problem

```
Flink checkpoint fires. The pipeline must write to:
  1. Redis (minute partial)
  2. ClickHouse (hour aggregate)

What if Redis write succeeds but ClickHouse write fails?
  → Redis has data that ClickHouse doesn't → inconsistency.

What if a checkpoint fails and Flink restarts?
  → Flink replays from the last successful checkpoint.
  → Writes to Redis/ClickHouse are re-executed → duplicates!
```

### Solution: Idempotent Writes

```
Redis: Naturally idempotent for HMSET (upsert).
  Writing the same minute aggregate twice = same result.
  The key is the same: metrics:minute:{cid}:{aid}:{window_start}
  The value is the same (deterministic aggregate).
  Duplicate write has no effect.

ClickHouse: Use ReplacingMergeTree for idempotent upserts.
  CREATE TABLE ad_metrics_hourly (...)
  ENGINE = ReplacingMergeTree(created_at)   -- Dedup by latest created_at
  ORDER BY (campaign_id, ad_id, window_start);
  
  If the same (campaign_id, ad_id, window_start) is written twice,
  ClickHouse keeps the latest version (by created_at).
  
  OPTIMIZE TABLE ad_metrics_hourly FINAL;  -- Force deduplication merge

Alternative: Include checkpoint_id in the write.
  Each write tagged with Flink checkpoint ID.
  Sink checks: "Did I already write checkpoint 42?" → skip if yes.
  Requires a small metadata table: flink_checkpoints(sink, checkpoint_id, completed_at).
```

### Flink Checkpointing Configuration

```java
env.enableCheckpointing(30_000);  // Checkpoint every 30 seconds
env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(10_000);
env.getCheckpointConfig().setCheckpointTimeout(120_000);
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
env.getCheckpointConfig().setExternalizedCheckpointCleanup(
    ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
);

// State backend: RocksDB for large state (millions of keys)
env.setStateBackend(new EmbeddedRocksDBStateBackend());
env.getCheckpointConfig().setCheckpointStorage("s3://checkpoints/ad-metrics/");
```

---

## State Management and Checkpointing

### Per-Key State Footprint

```
Each key (campaign:42:ad:789) maintains 3 window accumulators:

  Minute accumulator: 40 bytes (4 longs + 1 double)
  Hour accumulator:   40 bytes
  Day accumulator:    40 bytes
  
  Per-key state: ~120 bytes (plus overhead ~200 bytes)
  Total per key: ~320 bytes

Scale:
  100K campaigns × 10 ads = 1M keys
  1M keys × 320 bytes = 320 MB state
  
  Fits comfortably in RocksDB (local SSD).
  Checkpointed to S3 every 30 seconds (~320 MB snapshot).
```

### Checkpoint Size and Duration

```
1M keys × 320 bytes = 320 MB per checkpoint

  Incremental checkpointing (RocksDB):
    Only changed state since last checkpoint is uploaded.
    Typical: 5-20% of state changes per 30-second interval.
    Actual upload: 16-64 MB per checkpoint.
    
  Upload to S3: 64 MB at 100 MB/s = 0.64 seconds.
  Total checkpoint time: ~1-2 seconds (including barriers).
  
  This is well within the 30-second checkpoint interval.
```

---

## Keyed Aggregation — Dimensions and Cardinality

### Choosing Aggregation Keys

```
For ad analytics, common aggregation dimensions:

  Level 1: Campaign (campaign_id)
    Cardinality: 100K campaigns
    Use: Campaign-level dashboards, budget monitoring
    
  Level 2: Ad (campaign_id + ad_id)
    Cardinality: 1M (100K campaigns × 10 ads each)
    Use: Ad performance comparison, A/B testing
    
  Level 3: Ad + Placement (campaign_id + ad_id + placement_id)
    Cardinality: 10M (1M ads × 10 placements each)
    Use: Placement optimization, granular attribution

  Level 4: Ad + Placement + Geo (+ country/region)
    Cardinality: 100M+ → TOO HIGH for Flink state
    
  Rule of thumb: Keep keyed state cardinality under 10M keys per Flink TaskManager.
  Above that: Pre-aggregate at a higher level in Flink, store raw events for drill-down.
```

### Pre-Aggregation + Drill-Down Pattern

```
Flink pipeline:
  KeyBy(campaign_id, ad_id) → aggregate minute/hour/day → Redis + ClickHouse
  
  This handles Level 2 (campaign + ad) in real-time.

For Level 3+ drill-downs:
  Store raw events in ClickHouse (separate table, high cardinality):
    INSERT INTO ad_events_raw (campaign_id, ad_id, placement_id, country, event_time, ...)
    
  Query on demand:
    SELECT placement_id, count(*) as clicks
    FROM ad_events_raw
    WHERE campaign_id = 42 AND event_time BETWEEN ... AND ...
    GROUP BY placement_id
    
  Latency: 100ms-1s (ClickHouse scans efficiently with partition pruning).
  Cost: Acceptable for on-demand drill-downs (not real-time dashboards).
```

---

## Rollup Hierarchies — Minute → Hour → Day

### Should Hour Aggregate From Minutes or From Raw Events?

```
Approach A: Aggregate from raw events (3 independent windows)
  Minute window: Processes all events → writes minute aggregate
  Hour window:   Processes all events → writes hour aggregate
  Day window:    Processes all events → writes day aggregate
  
  ✅ Each window is independent — no cascading errors
  ✅ Each window can have different lateness tolerance
  ❌ 3× processing cost (each event processed by all 3 windows)

Approach B: Rollup (minute → hour → day)
  Minute window: Processes all events → writes minute aggregate
  Hour window:   Reads minute aggregates → sums 60 minutes → writes hour
  Day window:    Reads hour aggregates → sums 24 hours → writes day
  
  ✅ 1× processing cost for raw events (only minute window)
  ✅ Higher-level aggregates are simple sums of lower-level
  ❌ Cascading dependency — if minute is wrong, hour and day are wrong
  ❌ Late events that update a minute must propagate to hour and day

RECOMMENDATION for production:
  Use Approach A (3 independent windows) for simplicity and correctness.
  The "3× cost" is misleading — AggregateFunction is O(1) per event.
  Adding an event to 3 accumulators is 3 additions — trivial compute.
  
  Use Approach B only when you need derived metrics that span windows
  (e.g., "running total of the day so far" derived from completed minutes).
```

---

## Backpressure and Flow Control

### Handling Sink Slowdowns

```
Scenario: ClickHouse is slow (disk I/O spike). Flink's ClickHouse sink backs up.

Without backpressure handling:
  Flink buffers pile up → memory exhaustion → OOM → crash → data loss.

With Flink's built-in backpressure:
  ClickHouse sink slows down → output buffer fills → processing slows → 
  Kafka consumer slows → Kafka retains events → no data loss.
  
  Flink's backpressure propagates automatically through the DAG.
  
  Monitor: Flink Web UI shows backpressure per operator (red = high).
```

### Sink-Specific Strategies

```
Redis sink backpressure:
  Redis rarely slows down (in-memory, sub-ms).
  If it does: Pipeline the writes (batch 100 commands per round-trip).
  Buffer: In-memory batch of 1000 records, flush when full or every 1 second.
  
ClickHouse sink backpressure:
  ClickHouse INSERT is batch-friendly. Buffer 10K-100K rows per insert.
  Async inserts: ClickHouse's async_insert mode buffers server-side.
  Flush interval: every 10 seconds or when buffer > 50K rows.
  
  If ClickHouse is truly overloaded:
    Flink checkpoint will extend (sink can't flush).
    Alert: checkpoint_duration > 60 seconds → investigate ClickHouse.
```

---

## Schema Design for Multi-Window Aggregates

### Aggregate Metrics Schema

```
Common metrics for ad analytics:

  Counters (summable):
    impressions    UINT64     # Total times ad was shown
    clicks         UINT64     # Total times ad was clicked
    conversions    UINT64     # Total purchase/signup events
    unique_users   HLL        # Approximate unique users (HyperLogLog)
    
  Financial (summable):
    spend          DECIMAL    # Total ad spend ($)
    revenue        DECIMAL    # Total attributed revenue
    
  Derived (computed from counters):
    ctr            FLOAT      # Click-Through Rate = clicks / impressions
    cvr            FLOAT      # Conversion Rate = conversions / clicks
    cpc            FLOAT      # Cost Per Click = spend / clicks
    roas           FLOAT      # Return on Ad Spend = revenue / spend
    
  Derived metrics are NOT stored in Flink state.
  They're computed at write time or query time:
    ctr = clicks / NULLIF(impressions, 0)
    
  Why not store derived metrics?
    If a late event adds 1 click, we'd need to recompute ctr.
    Storing only counters makes corrections trivial (just add the delta).
```

### HyperLogLog for Unique Counts

```
Problem: Count unique users per campaign per minute.
  Can't just SUM unique counts across windows:
    Minute 1: Users {A, B, C} → unique = 3
    Minute 2: Users {B, C, D} → unique = 3
    Hour total: unique ≠ 3 + 3 = 6 (user B and C counted twice!)
    Correct: unique = 4 (A, B, C, D)

Solution: HyperLogLog (probabilistic data structure).
  Each window accumulator maintains an HLL sketch (~12 KB).
  HLL sketches are MERGEABLE:
    hour_unique = MERGE(minute1_hll, minute2_hll, ..., minute60_hll)
    → Correct unique count (within 0.81% error)
  
  Flink AggregateFunction stores HLL sketch as accumulator state.
  On merge: HLL union operation.
  
  ClickHouse: Native HyperLogLog support.
    CREATE TABLE ... (unique_users AggregateFunction(uniq, UInt64))
    INSERT ... VALUES (uniqState(user_id))
    SELECT uniqMerge(unique_users) FROM ... GROUP BY campaign_id
```

---

## Performance Tuning & Benchmarks

### Flink Pipeline Performance

```
Benchmark: Ad analytics pipeline, 3 windows, 1M events/sec

  Hardware: 4 TaskManagers, 8 cores each, 32 GB RAM, NVMe SSD
  Parallelism: 32 (4 TMs × 8 slots)
  State backend: RocksDB (incremental checkpoints to S3)
  
  Results:
    Throughput: 1.2M events/sec sustained
    Event-to-Redis latency: 200ms (10-second partial flush interval)
    Event-to-ClickHouse latency: 65 seconds (minute window + batch flush)
    Checkpoint size: 320 MB (1M keys)
    Checkpoint duration: 1.5 seconds (incremental)
    State size per TM: 80 MB (1M keys / 4 TMs × 320 bytes)

  Resource utilization:
    CPU: 60% average per TM
    Memory: 12 GB per TM (4 GB Flink, 4 GB RocksDB, 4 GB buffer)
    Network: 500 MB/sec (Kafka ingestion)
    Disk: 200 MB/sec (RocksDB compaction + checkpoint upload)
```

### Scaling Strategies

```
Vertical:
  More cores per TM → more task slots → more parallelism per machine.
  Limit: Single TM can handle ~2M events/sec with 16 cores.

Horizontal:
  More TaskManagers → more parallelism → linear throughput scaling.
  4 TMs → 1.2M events/sec
  8 TMs → 2.4M events/sec
  16 TMs → 4.8M events/sec

  Kafka partitions must match: 32 partitions for 32 parallel slots.
  
Key skew mitigation:
  If campaign:42 gets 50% of all events → one slot is overloaded.
  Solution: Two-phase aggregation:
    Phase 1: Random partitioning → local pre-aggregation per slot.
    Phase 2: KeyBy(campaign_id) → merge pre-aggregated results.
    Eliminates hot keys at the cost of one extra shuffle.
```

---

## Real-World Production Patterns

### Pattern 1: Ad Analytics (Facebook/Google Ads Style)

```
Event: Ad impression, click, or conversion
Volume: 10M events/sec
Dimensions: campaign_id, ad_group_id, ad_id, placement, country

Pipeline:
  Kafka → Flink (3 windows) → Redis (minute) + ClickHouse (hour/day)
  
Dashboard:
  Real-time: "Campaign X: 1.2M impressions in the last hour, CTR 2.1%"
  Updated every 10 seconds from Redis minute partials.
  
Reporting:
  "Daily spend by campaign for Q1 2024"
  → ClickHouse HDD: 90 rows per campaign × 100K campaigns = 9M rows
  → Scans in 200ms
```

### Pattern 2: Metrics/Monitoring Dashboard (Datadog/Grafana Style)

```
Event: System metric (CPU, memory, request latency, error count)
Volume: 100K metrics/sec (from 10K hosts)
Dimensions: host_id, service, metric_name

Pipeline:
  StatsD/OpenTelemetry → Kafka → Flink (3 windows) → Redis + ClickHouse

Dashboard:
  Last 1 hour: Minute-level CPU graph → Redis
  Last 24 hours: Hour-level CPU graph → ClickHouse SSD
  Last 30 days: Day-level CPU graph → ClickHouse HDD
  
  Aggregation: p50, p95, p99 (requires t-digest or DDSketch, not just sum/count)
  
Special: Percentile sketches
  Can't SUM percentiles across time windows.
  Use t-digest or DDSketch sketches as accumulators.
  Sketches are mergeable (like HyperLogLog for unique counts).
```

### Pattern 3: E-Commerce Order Analytics

```
Event: Order placed, order shipped, order delivered, return initiated
Volume: 50K events/sec
Dimensions: merchant_id, category, region

Pipeline:
  Order events → Kafka → Flink → Redis (minute orders/revenue) + ClickHouse

Dashboard:
  Real-time: "Orders per minute: 2,341 | Revenue: $187K | Avg order: $80"
  Daily report: "Merchant X: $4.2M revenue, 52K orders, 3.1% return rate"

Special: Revenue requires Decimal precision (not float!)
  Use DECIMAL(18,4) in ClickHouse, BigDecimal in Java.
```

### Pattern 4: Real-Time Billing/Usage Metering

```
Event: API call with resource consumption (compute-seconds, storage-bytes, requests)
Volume: 500K events/sec
Dimensions: tenant_id, resource_type, region

Pipeline:
  Usage events → Kafka → Flink → Redis (minute usage) + ClickHouse (hourly billing)

Special considerations:
  - EXACTLY-ONCE is critical (over-billing or under-billing = legal issue)
  - Idempotent writes with checkpoint IDs
  - Reconciliation job: Compare Flink output vs raw events daily
  - Alert if discrepancy > 0.01%
```

---

## Interview Talking Points & Scripts

### Script 1: Core Architecture

> *"A single Flink pipeline produces minute, hour, and day aggregates simultaneously. I'd use three concurrent tumbling windows on the same keyed stream — each event updates all three window accumulators via an incremental AggregateFunction (O(1) memory per key). Every 10 seconds, a ContinuousEventTimeTrigger flushes partial minute aggregates to Redis for real-time dashboards. When the minute window closes, the final aggregate overwrites the partial. Hour finals go to ClickHouse on SSD, and day finals go to ClickHouse on HDD. A query router transparently merges across all three tiers."*

### Script 2: Why Not Query Raw Events?

> *"At 1M events per second, storing raw events is 86 billion per day. Querying 'hourly clicks for last month' would scan 2.6 trillion events — minutes per query, terabytes scanned. With pre-aggregation, that same query scans 720 rows from ClickHouse in milliseconds. The multi-window approach gives us 857x cost reduction and 1000x query speed improvement while maintaining 10-second real-time granularity for live dashboards."*

### Script 3: Tiered Storage

> *"Each window tier maps to the optimal storage for its access pattern. Minutes go to Redis — sub-millisecond reads for dashboards refreshing every 10 seconds, with auto-expiring TTLs for cleanup. Hours go to ClickHouse on SSD — fast columnar scans for 30-90 day analytics. Days go to ClickHouse on HDD — cheap storage for multi-year trends. The query router decides which tier to query based on the requested time range, and merges results transparently."*

### Script 4: Partial Flushes

> *"Users expect real-time dashboards, but a 1-minute window means no data for 59 seconds. I solve this with a ContinuousEventTimeTrigger that fires every 10 seconds within each window, emitting a partial aggregate. The partial is written to Redis as an upsert — each flush overwrites the previous partial. The dashboard shows these partials with an 'updating...' indicator, confirmed when the final aggregate arrives. This gives 10-second freshness with 1-minute accuracy."*

### Script 5: Late Events

> *"For late events, I'd configure a 30-second watermark delay — events arriving up to 30 seconds late are included in the correct window. Beyond that, I'd use Flink's allowedLateness to keep windows open for 5 extra minutes and re-fire with updated results. Events arriving after even that go to a side output, which writes to a corrections table in ClickHouse. The query router joins the corrections table at query time. In practice, for ad analytics, only 0.01% of events are >30 seconds late, so corrections are rare."*

### Script 6: Exactly-Once

> *"For exactly-once across tiers, I rely on idempotent writes. Redis HMSET is naturally idempotent — writing the same minute aggregate twice produces the same result. For ClickHouse, I use ReplacingMergeTree which deduplicates rows with the same primary key, keeping the latest version. On checkpoint recovery, Flink replays from the last checkpoint, re-executing writes — but since they're idempotent, there's no duplication. For billing use cases, I'd add an explicit checkpoint ID to each write and a reconciliation job."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Query raw events on demand"

**Bad**: "We'll store all events and aggregate at query time."
**Fix**: Pre-aggregate in Flink. 1M events/sec = 86B/day. Querying trillions of events doesn't scale.

### ❌ Mistake 2: Running 3 separate pipelines

**Bad**: "One Flink job for minutes, one for hours, one for days."
**Fix**: One pipeline with 3 windows. Saves 3× compute and ensures consistency.

### ❌ Mistake 3: Using ProcessWindowFunction instead of AggregateFunction

**Bad**: Buffer all events in window state, process on fire.
**Fix**: AggregateFunction — O(1) accumulator per key. 60M events in a minute window would OOM with ProcessWindowFunction.

### ❌ Mistake 4: No partial flushes

**Bad**: "Dashboard updates every minute when the window fires."
**Fix**: ContinuousEventTimeTrigger every 10 seconds → partial aggregates to Redis.

### ❌ Mistake 5: Storing all granularities in the same storage

**Bad**: "Everything goes to ClickHouse."
**Fix**: Minute → Redis (sub-ms, real-time). Hour → ClickHouse SSD. Day → ClickHouse HDD. Each tier optimized for its access pattern.

### ❌ Mistake 6: Ignoring late events

**Bad**: "Events always arrive on time."
**Fix**: Watermarks with bounded out-of-orderness + allowedLateness + corrections table.

### ❌ Mistake 7: Forgetting the query router

**Bad**: "Just query Redis for recent data and ClickHouse for old data."
**Fix**: A unified query router that determines the optimal tier per time range and merges results without double-counting.

### ❌ Mistake 8: Summing unique counts across windows

**Bad**: "Unique users per hour = sum of unique users per minute."
**Fix**: HyperLogLog sketches — mergeable probabilistic data structure for approximate unique counts.

---

## Multi-Window Aggregation by System Design Problem

| Problem | Event Volume | Windows | Key Dimensions | Special Considerations |
|---|---|---|---|---|
| **Ad Analytics** | 1-10M/sec | Min/Hr/Day | campaign, ad, placement | CTR as derived metric, HLL for unique users |
| **Metrics Dashboard** | 100K/sec | Min/Hr/Day | host, service, metric | p50/p99 requires t-digest sketches |
| **E-Commerce Analytics** | 50K/sec | Min/Hr/Day | merchant, category | Revenue = Decimal precision, not float |
| **Billing/Usage Metering** | 500K/sec | Hr/Day/Month | tenant, resource | Exactly-once critical, reconciliation required |
| **IoT Sensor Data** | 1M/sec | Sec/Min/Hr | device, sensor_type | High cardinality, pre-aggregate per device |
| **Gaming Leaderboards** | 100K/sec | Min/Hr/Day | game, region | Sorted sets for real-time ranking |
| **Streaming Analytics** | 500K/sec | Min/Hr/Day | content, region | Watch-time = sum, concurrent = max (not summable) |
| **API Rate Monitoring** | 200K/sec | Min/Hr | service, endpoint, client | Error rates as derived metrics |
| **Financial Trading** | 1M/sec | Sec/Min/Hr | ticker, exchange | VWAP = weighted average (not simple sum) |
| **CDN Analytics** | 5M/sec | Min/Hr/Day | region, content_type | Bandwidth = sum of bytes, cache hit ratio = derived |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│         MULTI-WINDOW STREAM AGGREGATION — CHEAT SHEET                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  PATTERN: One Flink pipeline → 3 concurrent tumbling windows         │
│    Minute → Redis (hot)                                              │
│    Hour   → ClickHouse SSD (warm)                                    │
│    Day    → ClickHouse HDD (cold)                                    │
│                                                                      │
│  PARTIAL FLUSHES:                                                    │
│    ContinuousEventTimeTrigger every 10 seconds                       │
│    Partial → Redis (upsert, overwrites previous)                     │
│    Final → destination tier (append-only)                            │
│    Dashboard: 10-second freshness, 1-minute accuracy                 │
│                                                                      │
│  AGGREGATE FUNCTION (not ProcessWindowFunction):                     │
│    O(1) memory per key. Incremental. No event buffering.             │
│    3 windows × 1 accumulator each = 3 additions per event.          │
│                                                                      │
│  QUERY ROUTER:                                                       │
│    Routes time range to optimal tier(s).                             │
│    Merges results. No double-counting.                               │
│    Falls back: Day → Hour → Minute if higher tier unavailable.       │
│                                                                      │
│  LATE EVENTS:                                                        │
│    Watermark: 30-second bounded out-of-orderness.                    │
│    allowedLateness: 5 minutes (re-fires window with update).         │
│    Beyond that: Side output → corrections table → merged at query.   │
│                                                                      │
│  EXACTLY-ONCE:                                                       │
│    Redis: HMSET is idempotent (same key, same value).                │
│    ClickHouse: ReplacingMergeTree deduplicates by primary key.       │
│    Billing: Explicit checkpoint ID + reconciliation job.             │
│                                                                      │
│  UNIQUE COUNTS:                                                      │
│    HyperLogLog sketches (mergeable, ~0.81% error, ~12 KB each).     │
│    NOT sum of per-window uniques (overcounts!).                      │
│                                                                      │
│  COST COMPARISON:                                                    │
│    Raw events at 1M/sec: ~$300K/month, minutes per query.            │
│    Pre-aggregated: ~$350/month, milliseconds per query.              │
│    857× cost reduction. 1000× query speed improvement.               │
│                                                                      │
│  STATE: ~320 bytes per key × 1M keys = 320 MB.                      │
│    RocksDB state backend. Incremental checkpoints to S3.             │
│                                                                      │
│  PERFORMANCE: 1.2M events/sec on 4 TaskManagers (32 cores).         │
│    Linear horizontal scaling with more TMs.                          │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 23: Batch vs Stream Processing** — When to use batch vs streaming
- **Topic 39: Kafka Deep Dive** — Source for event ingestion
- **Topic 40: Redis Deep Dive** — Hot tier storage for minute aggregates
- **Topic 41: Trending and Top-K** — Streaming top-K patterns
- **Topic 45: Observability** — Metrics dashboards as a consumer
- **Topic 48: Flink Deep Dive** — Flink architecture, watermarks, checkpointing
- **Topic 49: DynamoDB Deep Dive** — Alternative to ClickHouse for warm tier

---

*This document is part of the System Design Interview Deep Dive series.*
