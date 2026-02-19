# Design an Ad Click Aggregator - Hello Interview Framework

> **Question**: Design a real-time ad click aggregator that processes billions of ad events (clicks and impressions) daily, aggregates them with maximum 30-second latency, and supports both real-time dashboard queries and historical analytics for up to 2 years.
>
> **Asked at**: Meta, Snapchat, Stripe, Google
>
> **Difficulty**: Hard | **Level**: Staff/Manager

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
1. **Ingest Ad Events**: Accept click and impression events in real-time from ad-serving infrastructure worldwide.
2. **Aggregate Metrics**: Compute aggregated metrics (impressions, clicks, CTR, spend) by configurable dimensions: campaign, ad group, ad, device type, geo (country/region), and time granularity (minute, hour, day).
3. **Real-Time Dashboard**: Serve aggregated metrics with < 30 second freshness for live dashboards (advertisers monitoring active campaigns).
4. **Historical Queries**: Support queries over historical data (last 2 years) with reasonable latency (< 5 seconds for dashboard loads).
5. **Correctness**: Ensure exactly-once counting — no duplicates, no missed events. Advertisers are billed based on these numbers.

#### Nice to Have (P1)
- Anomaly detection (sudden CTR drop → alert advertiser).
- Conversion attribution (click → purchase within 24h).
- A/B test analytics (compare ad variants).
- Custom report generation (CSV export, scheduled emails).
- Funnel analysis (impression → click → conversion).

#### Below the Line (Out of Scope)
- Ad serving / targeting / bidding (separate system).
- Creative management (ad content storage).
- Fraud detection (assume separate fraud pipeline).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Ingestion Throughput** | 200B events/day (~2.3M events/sec) | Global ad platform at Meta/Google scale |
| **Aggregation Freshness** | < 30 seconds from event to queryable | Advertisers expect near-real-time dashboards |
| **Query Latency** | < 500ms for real-time, < 5s for historical | Dashboard responsiveness |
| **Correctness** | Exactly-once aggregation | Financial billing depends on these counts |
| **Durability** | Zero event loss | Every click = revenue |
| **Availability** | 99.99% for ingestion, 99.9% for queries | Ingestion must never drop events |
| **Data Retention** | 2 years (minute granularity for 30 days, hourly for 1 year, daily for 2 years) | Regulatory and analytics requirements |
| **Idempotency** | Duplicate events must not inflate counts | Network retries are common |
| **Scalability** | 10M+ campaigns, 200+ countries, 5+ device types | Multi-dimensional aggregation |

### Capacity Estimation

```
Ingestion:
  Total events/day: 200B (impressions + clicks)
  Impressions: ~190B/day (95%)
  Clicks: ~10B/day (5%, ~5% CTR)
  Sustained QPS: 200B / 86400 = ~2.3M events/sec
  Peak (2x): ~4.6M events/sec
  
  Event size: ~200 bytes (ad_id, campaign_id, user_id, device, geo, timestamp, event_type)
  Daily raw data: 200B × 200 bytes = 40 TB/day

Aggregated Data:
  Dimensions: campaign (10M) × ad (50M) × device (5) × country (200) × hour (24)
  Unique combinations per day: ~500M (most are sparse)
  Per aggregation row: ~100 bytes (dimensions + metrics)
  Daily aggregated: 500M × 100 bytes = 50 GB/day
  Monthly: 1.5 TB aggregated
  2 years: ~36 TB aggregated (with tiered granularity, much less)

Storage (tiered):
  Raw events: 30 days × 40 TB = 1.2 PB (then archived to cold storage)
  Minute-level aggregates: 30 days × 50 GB = 1.5 TB
  Hour-level aggregates: 1 year × 2 GB/day = 730 GB
  Day-level aggregates: 2 years × 200 MB/day = 146 GB
  Total hot storage: ~3 TB (aggregated data)

Query Load:
  Dashboard queries: ~10K QPS (advertisers checking campaigns)
  Report generation: ~1K QPS (background jobs)
```

---

## 2️⃣ Core Entities

### Entity 1: Raw Ad Event
```java
public class AdEvent {
    private final String eventId;          // UUID, for deduplication
    private final EventType eventType;     // IMPRESSION or CLICK
    private final String adId;             // Unique ad creative
    private final String adGroupId;        // Ad group (set of ads)
    private final String campaignId;       // Campaign (set of ad groups)
    private final String advertiserId;     // Advertiser account
    private final String userId;           // Viewer/clicker (hashed)
    private final DeviceType deviceType;   // MOBILE, DESKTOP, TABLET
    private final String country;          // ISO 3166-1: "US", "JP"
    private final String region;           // State/province
    private final Instant eventTime;       // When the event occurred (client timestamp)
    private final Instant serverTime;      // When the server received it
    private final BigDecimal bidAmount;    // How much the advertiser bid (for spend calculation)
}

public enum EventType {
    IMPRESSION, CLICK
}
```

### Entity 2: Aggregated Metric
```java
public class AggregatedMetric {
    // Dimension keys
    private final String campaignId;
    private final String adGroupId;        // nullable (for campaign-level rollup)
    private final String adId;             // nullable (for ad-group-level rollup)
    private final DeviceType deviceType;   // nullable (for all-devices rollup)
    private final String country;          // nullable (for global rollup)
    private final TimeGranularity granularity; // MINUTE, HOUR, DAY
    private final Instant windowStart;     // Start of aggregation window
    
    // Metrics
    private long impressions;
    private long clicks;
    private BigDecimal spend;              // Total spend in this window
    private double ctr;                    // clicks / impressions (computed)
    private double cpc;                    // spend / clicks (computed)
    private double cpm;                    // (spend / impressions) * 1000 (computed)
}
```

### Entity 3: Query Request
```java
public class MetricQueryRequest {
    private final String campaignId;       // Required
    private final String adGroupId;        // Optional filter
    private final String adId;             // Optional filter
    private final DeviceType deviceType;   // Optional filter
    private final String country;          // Optional filter
    private final Instant from;            // Start time
    private final Instant to;              // End time
    private final TimeGranularity granularity; // MINUTE, HOUR, DAY
    private final List<String> metrics;    // ["impressions", "clicks", "ctr", "spend"]
}
```

---

## 3️⃣ API Design

### 1. Ingest Ad Event (Internal — from Ad Servers)
```
POST /api/v1/events/ingest

Request:
{
  "event_id": "evt_abc123",
  "event_type": "CLICK",
  "ad_id": "ad_789",
  "campaign_id": "camp_456",
  "advertiser_id": "adv_123",
  "user_id": "usr_hash_xxx",
  "device_type": "MOBILE",
  "country": "US",
  "region": "CA",
  "event_time": "2025-01-10T19:30:00.123Z",
  "bid_amount": 0.05
}

Response (202 Accepted):
{
  "status": "ACCEPTED",
  "event_id": "evt_abc123"
}
```

> In practice, events are published directly to Kafka by ad servers (not via HTTP API).

### 2. Query Aggregated Metrics (Dashboard)
```
GET /api/v1/metrics?campaign_id=camp_456&from=2025-01-10T00:00:00Z&to=2025-01-10T23:59:59Z&granularity=HOUR&metrics=impressions,clicks,ctr,spend

Response (200 OK):
{
  "campaign_id": "camp_456",
  "granularity": "HOUR",
  "from": "2025-01-10T00:00:00Z",
  "to": "2025-01-10T23:59:59Z",
  "freshness_seconds": 12,
  "data": [
    {
      "window_start": "2025-01-10T00:00:00Z",
      "impressions": 1234567,
      "clicks": 45678,
      "ctr": 0.037,
      "spend": 2345.67
    },
    {
      "window_start": "2025-01-10T01:00:00Z",
      "impressions": 987654,
      "clicks": 34567,
      "ctr": 0.035,
      "spend": 1987.43
    }
  ]
}
```

### 3. Query with Breakdown (Drill-Down)
```
GET /api/v1/metrics?campaign_id=camp_456&from=2025-01-10T00:00:00Z&to=2025-01-10T23:59:59Z&granularity=DAY&group_by=device_type,country&metrics=impressions,clicks,ctr

Response (200 OK):
{
  "campaign_id": "camp_456",
  "group_by": ["device_type", "country"],
  "data": [
    { "device_type": "MOBILE", "country": "US", "impressions": 5000000, "clicks": 200000, "ctr": 0.04 },
    { "device_type": "DESKTOP", "country": "US", "impressions": 2000000, "clicks": 60000, "ctr": 0.03 },
    { "device_type": "MOBILE", "country": "JP", "impressions": 1500000, "clicks": 75000, "ctr": 0.05 }
  ]
}
```

### 4. Real-Time Campaign Summary (Live Dashboard)
```
GET /api/v1/campaigns/{campaign_id}/live

Response (200 OK):
{
  "campaign_id": "camp_456",
  "last_updated": "2025-01-10T19:30:15Z",
  "freshness_seconds": 8,
  "today": {
    "impressions": 45678901,
    "clicks": 1678234,
    "ctr": 0.0367,
    "spend": 89234.56,
    "cpc": 0.053,
    "cpm": 1.95
  },
  "last_hour": {
    "impressions": 1234567,
    "clicks": 45678,
    "ctr": 0.037,
    "spend": 2345.67
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Event Ingestion → Aggregation → Serving
```
1. Ad server shows ad / user clicks ad → generates AdEvent
   ↓
2. Ad server publishes event to Kafka topic "ad-events"
   Partition key: ad_id (co-locate events for same ad)
   ↓
3. Stream Processor (Apache Flink):
   a. Deduplicate: check event_id against dedup store (Bloom filter + Redis)
   b. Parse and validate event
   c. Route to appropriate aggregation windows:
      - 1-minute tumbling window (for real-time dashboard)
      - 1-hour tumbling window (for hourly rollups)
      - 1-day tumbling window (for daily rollups)
   d. Aggregate: for each (dimension_key, window):
      impressions += 1, clicks += 1, spend += bid_amount
   e. Every 10 seconds: flush partial aggregates to serving layer
   ↓
4. Serving Layer:
   a. Real-time: Redis (minute-level, last 24 hours)
   b. Mid-term: ClickHouse / Druid (hour-level, last 30 days)
   c. Long-term: ClickHouse cold storage (day-level, 2 years)
   ↓
5. Dashboard queries read from serving layer (< 500ms)
```

### Flow 2: Historical Query
```
1. Advertiser queries: "Show me daily impressions for campaign X, last 3 months"
   ↓
2. Query Service:
   a. Determine time range and granularity
   b. Route to appropriate storage tier:
      - Last 24h + minute granularity → Redis
      - Last 30 days + hour granularity → ClickHouse hot
      - Older + day granularity → ClickHouse cold
   c. Execute query, return results
   ↓
3. Total latency: < 5 seconds for 3-month daily query
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                         AD SERVERS (Global)                            │
│   User sees ad → IMPRESSION event   User clicks ad → CLICK event      │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      KAFKA CLUSTER                                     │
│  Topic: "ad-events" (500+ partitions)                                 │
│  Partition key: campaign_id (co-locate same campaign)                 │
│  Throughput: 2.3M events/sec, 40 TB/day                              │
│  Retention: 7 days (for replay/reprocessing)                         │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│               STREAM PROCESSOR (Apache Flink)                         │
│                                                                        │
│  1. Deduplicate (Bloom filter + Redis)                                │
│  2. Multi-window aggregation:                                         │
│     - 1-min tumbling windows (real-time)                              │
│     - 1-hour tumbling windows (mid-term)                              │
│     - 1-day tumbling windows (long-term)                              │
│  3. Pre-aggregate by (campaign, ad, device, country, window)          │
│  4. Flush to serving stores every 10 seconds                         │
│                                                                        │
│  Parallelism: 500+ tasks (matching Kafka partitions)                  │
│  Checkpointing: every 30 seconds (exactly-once via Kafka transactions)│
└──────────┬────────────────────────────┬───────────────────────────────┘
           │                            │
     ┌─────▼──────┐           ┌────────▼────────┐
     │ REDIS       │           │ CLICKHOUSE       │
     │ (Real-time) │           │ (Analytical)     │
     │             │           │                  │
     │ Minute-level│           │ Hour + Day level │
     │ Last 24h    │           │ Last 2 years     │
     │ < 10ms reads│           │ < 500ms queries  │
     └─────┬───────┘           └────────┬─────────┘
           │                            │
           └──────────┬─────────────────┘
                      ▼
         ┌─────────────────────────┐
         │    QUERY SERVICE        │
         │  • Route to correct tier│
         │  • Merge results        │
         │  • Compute derived      │
         │    metrics (CTR, CPC)   │
         └────────────┬────────────┘
                      │
                      ▼
         ┌─────────────────────────┐
         │    DASHBOARD / API      │
         │  Advertisers view       │
         │  campaign performance   │
         └─────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Kafka** | Durable event stream, buffering, replay | Apache Kafka | 500+ partitions, 7-day retention |
| **Flink** | Stream processing, dedup, multi-window aggregation | Apache Flink | 500+ parallel tasks |
| **Redis** | Real-time serving (minute-level, last 24h) | Redis Cluster | 20+ nodes, ~500 GB |
| **ClickHouse** | Analytical queries (hour/day level, 2 years) | ClickHouse | 10+ nodes, ~40 TB |
| **Query Service** | Route queries, merge tiers, compute derived metrics | Java/Spring Boot | Stateless, 20+ pods |
| **S3** | Raw event archive (cold storage, compliance) | AWS S3 / GCS | Managed, unlimited |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Exactly-Once Aggregation — Deduplication at Scale

**The Problem**: Ad servers retry failed publishes. Network glitches cause duplicates. At 2.3M events/sec, even 0.1% duplication = 2.3K false clicks/sec = millions in overbilling.

```java
public class EventDeduplicator {
    private volatile BloomFilter<String> currentBloom;
    private volatile BloomFilter<String> previousBloom;
    private static final long EXPECTED_EVENTS_PER_HOUR = 10_000_000_000L;
    private static final double BLOOM_FPP = 0.0001;
    
    public EventDeduplicator() {
        this.currentBloom = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8), EXPECTED_EVENTS_PER_HOUR, BLOOM_FPP);
    }
    
    public boolean isDuplicate(String eventId) {
        boolean mightExist = currentBloom.mightContain(eventId) ||
                             (previousBloom != null && previousBloom.mightContain(eventId));
        
        if (!mightExist) {
            currentBloom.put(eventId);
            return false;
        }
        
        String redisKey = "dedup:" + eventId;
        boolean isNew = redis.set(redisKey, "1", SetParams.setParams().nx().ex(3600));
        
        if (isNew) {
            metrics.counter("dedup.bloom_false_positive").increment();
            return false;
        }
        
        metrics.counter("dedup.duplicate_dropped").increment();
        return true;
    }
    
    @Scheduled(fixedRate = 3600_000)
    public void rotateBloom() {
        previousBloom = currentBloom;
        currentBloom = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8), EXPECTED_EVENTS_PER_HOUR, BLOOM_FPP);
    }
}
```

**Dedup Pipeline**:
```
Event arrives -> Bloom filter check (< 1us)
  Not in bloom -> ADD to bloom -> PROCESS (99.99% of events)
  In bloom (0.01%) -> Redis SETNX check (< 1ms)
    New (false positive) -> PROCESS
    Exists (true duplicate) -> DROP

Memory: Bloom ~1 GB + Redis ~10 GB for dedup keys
```

---

### Deep Dive 2: Multi-Window Aggregation in Flink

**The Problem**: Simultaneous aggregation at minute, hour, and day granularity, each flushed to different storage tiers.

```java
public class MultiWindowAggregator extends KeyedProcessFunction<String, AdEvent, AggregateOutput> {
    private ValueState<MinuteAggregate> minuteState;
    private ValueState<HourAggregate> hourState;
    private ValueState<DayAggregate> dayState;
    
    @Override
    public void open(Configuration parameters) {
        minuteState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("minute-agg", MinuteAggregate.class));
        hourState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("hour-agg", HourAggregate.class));
        dayState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("day-agg", DayAggregate.class));
    }
    
    @Override
    public void processElement(AdEvent event, Context ctx, Collector<AggregateOutput> out) throws Exception {
        long eventTimeMs = event.getEventTime().toEpochMilli();
        
        MinuteAggregate minute = getOrCreateMinute(eventTimeMs);
        minute.addEvent(event);
        minuteState.update(minute);
        ctx.timerService().registerEventTimeTimer(truncateToMinute(eventTimeMs) + 60_000);
        
        HourAggregate hour = getOrCreateHour(eventTimeMs);
        hour.addEvent(event);
        hourState.update(hour);
        ctx.timerService().registerEventTimeTimer(truncateToHour(eventTimeMs) + 3_600_000);
        
        DayAggregate day = getOrCreateDay(eventTimeMs);
        day.addEvent(event);
        dayState.update(day);
        ctx.timerService().registerEventTimeTimer(truncateToDay(eventTimeMs) + 86_400_000);
    }
    
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<AggregateOutput> out) throws Exception {
        MinuteAggregate minute = minuteState.value();
        if (minute != null && isMinuteWindowEnd(timestamp, minute)) {
            out.collect(new AggregateOutput("MINUTE", ctx.getCurrentKey(), minute));
            minuteState.clear();
        }
        HourAggregate hour = hourState.value();
        if (hour != null && isHourWindowEnd(timestamp, hour)) {
            out.collect(new AggregateOutput("HOUR", ctx.getCurrentKey(), hour));
            hourState.clear();
        }
        DayAggregate day = dayState.value();
        if (day != null && isDayWindowEnd(timestamp, day)) {
            out.collect(new AggregateOutput("DAY", ctx.getCurrentKey(), day));
            dayState.clear();
        }
    }
}

public class MinuteAggregate implements Serializable {
    private long windowStart;
    private long impressions;
    private long clicks;
    private BigDecimal spend = BigDecimal.ZERO;
    
    public void addEvent(AdEvent event) {
        if (event.getEventType() == EventType.IMPRESSION) impressions++;
        else if (event.getEventType() == EventType.CLICK) clicks++;
        spend = spend.add(event.getBidAmount());
    }
}
```

**Window Lifecycle**:
```
Minute window 10:00:00-10:00:59:
  Events arrive -> accumulate in minuteState
  10:01:00 -> Flush to Redis, clear state

Hour window 10:00:00-10:59:59:
  Events accumulate across 60 minutes
  11:00:00 -> Flush to ClickHouse, clear state

Partial flushes every 10s:
  In-progress minuteState -> Redis (for real-time freshness)
```

---

### Deep Dive 3: Out-of-Order Events & Watermarking

**The Problem**: Events arrive out of order. A click at 10:00:00 might arrive at 10:00:45. We need to handle late events without compromising accuracy.

```java
public class LateEventHandler {
    private static final Duration ALLOWED_LATENESS = Duration.ofMinutes(5);
    private static final Duration VERY_LATE_THRESHOLD = Duration.ofHours(1);
    
    public EventDisposition classify(AdEvent event) {
        Duration lateness = Duration.between(event.getEventTime(), Instant.now());
        
        if (lateness.compareTo(Duration.ZERO) <= 0) {
            return EventDisposition.PROCESS_WITH_SERVER_TIME;
        }
        if (lateness.compareTo(ALLOWED_LATENESS) <= 0) {
            return EventDisposition.PROCESS_NORMAL;
        }
        if (lateness.compareTo(VERY_LATE_THRESHOLD) <= 0) {
            metrics.counter("events.late", "severity", "moderate").increment();
            return EventDisposition.PROCESS_AND_FLAG;
        }
        metrics.counter("events.late", "severity", "extreme").increment();
        return EventDisposition.DEAD_LETTER;
    }
    
    public void handleLateEvent(AdEvent event) {
        String dimensionKey = buildDimensionKey(event);
        Instant windowStart = truncateToMinute(event.getEventTime());
        
        AggregationDelta delta = AggregationDelta.builder()
            .dimensionKey(dimensionKey).windowStart(windowStart)
            .impressionsDelta(event.getEventType() == EventType.IMPRESSION ? 1 : 0)
            .clicksDelta(event.getEventType() == EventType.CLICK ? 1 : 0)
            .spendDelta(event.getBidAmount()).build();
        
        String redisKey = "agg:" + dimensionKey + ":" + windowStart.getEpochSecond();
        redis.hincrBy(redisKey, "impressions", delta.getImpressionsDelta());
        redis.hincrBy(redisKey, "clicks", delta.getClicksDelta());
        
        kafka.send(new ProducerRecord<>("aggregation-corrections", dimensionKey, delta));
        metrics.counter("events.late.corrected").increment();
    }
}
```

**Late Event Distribution**:
```
99.9% on-time (< 1 min lateness) -> normal processing
0.09% moderately late (1-5 min)  -> delta correction applied
0.01% very late (> 5 min)        -> dead-lettered for manual review
```

---

### Deep Dive 4: Redis Serving Layer — Real-Time Aggregates

**The Problem**: Serve minute-level metrics with < 10ms latency for 10K QPS dashboard reads.

```java
public class RealTimeMetricStore {
    
    public void writeMinuteAggregate(String dimensionKey, Instant minuteStart,
                                      long impressions, long clicks, BigDecimal spend) {
        String key = "agg:" + dimensionKey + ":" + minuteStart.getEpochSecond();
        
        Pipeline pipe = redis.pipelined();
        pipe.hincrBy(key, "impressions", impressions);
        pipe.hincrBy(key, "clicks", clicks);
        pipe.hincrBy(key, "spend_cents", spend.multiply(BigDecimal.valueOf(100)).longValue());
        pipe.expire(key, 90000); // 25 hours
        pipe.sync();
        
        // Daily rollup counter
        String rollupKey = "rollup:" + extractCampaignId(dimensionKey) + ":today:" 
            + minuteStart.atZone(ZoneOffset.UTC).toLocalDate();
        pipe = redis.pipelined();
        pipe.hincrBy(rollupKey, "impressions", impressions);
        pipe.hincrBy(rollupKey, "clicks", clicks);
        pipe.hincrBy(rollupKey, "spend_cents", spend.multiply(BigDecimal.valueOf(100)).longValue());
        pipe.expire(rollupKey, 172800);
        pipe.sync();
    }
    
    public List<MetricPoint> queryMinuteLevel(String campaignId, Instant from, Instant to) {
        String dimensionKey = buildDimensionKey(campaignId);
        List<String> keys = new ArrayList<>();
        Instant current = truncateToMinute(from);
        while (current.isBefore(to)) {
            keys.add("agg:" + dimensionKey + ":" + current.getEpochSecond());
            current = current.plus(Duration.ofMinutes(1));
        }
        
        Pipeline pipe = redis.pipelined();
        for (String key : keys) { pipe.hgetAll(key); }
        List<Object> responses = pipe.syncAndReturnAll();
        
        List<MetricPoint> results = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            Map<String, String> hash = (Map<String, String>) responses.get(i);
            if (hash != null && !hash.isEmpty()) {
                long imp = Long.parseLong(hash.getOrDefault("impressions", "0"));
                long clk = Long.parseLong(hash.getOrDefault("clicks", "0"));
                long spd = Long.parseLong(hash.getOrDefault("spend_cents", "0"));
                results.add(new MetricPoint(extractTimestamp(keys.get(i)), imp, clk, spd));
            }
        }
        return results;
    }
}
```

**Redis Memory**: ~300 GB across 20 nodes for 1.44B minute-level keys + rollups.

---

### Deep Dive 5: ClickHouse for Historical Analytics

**The Problem**: Serve analytical queries over 2 years of data with < 5s latency.

```sql
-- ClickHouse Schema
CREATE TABLE ad_metrics_hourly (
    campaign_id     String,
    ad_group_id     String,
    ad_id           String,
    device_type     LowCardinality(String),
    country         LowCardinality(String),
    window_start    DateTime,
    impressions     UInt64,
    clicks          UInt64,
    spend_cents     UInt64,
    ctr             Float64 MATERIALIZED clicks / nullIf(impressions, 0),
    cpc_cents       Float64 MATERIALIZED spend_cents / nullIf(clicks, 0)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(window_start)
ORDER BY (campaign_id, ad_id, device_type, country, window_start)
TTL window_start + INTERVAL 1 YEAR;

CREATE TABLE ad_metrics_daily (
    -- Same schema, daily granularity
    -- TTL window_start + INTERVAL 2 YEAR
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(window_start)
ORDER BY (campaign_id, ad_id, device_type, country, window_start)
TTL window_start + INTERVAL 2 YEAR;
```

```java
public class ClickHouseMetricStore {
    
    public List<MetricPoint> queryHourly(MetricQueryRequest request) {
        StringBuilder sql = new StringBuilder("""
            SELECT window_start, sum(impressions), sum(clicks), sum(spend_cents),
                   sum(clicks) / nullIf(sum(impressions), 0) AS ctr
            FROM ad_metrics_hourly
            WHERE campaign_id = ? AND window_start >= ? AND window_start < ?
            """);
        List<Object> params = new ArrayList<>(List.of(
            request.getCampaignId(), Timestamp.from(request.getFrom()), Timestamp.from(request.getTo())));
        
        if (request.getAdId() != null) { sql.append(" AND ad_id = ?"); params.add(request.getAdId()); }
        if (request.getCountry() != null) { sql.append(" AND country = ?"); params.add(request.getCountry()); }
        
        sql.append(" GROUP BY window_start ORDER BY window_start");
        return clickhouse.query(sql.toString(), params.toArray(), metricRowMapper);
    }
}
```

**Query Performance**: 6-month daily query by country = ~36K rows, < 100ms with columnar compression.

---

### Deep Dive 6: Tiered Storage & Data Lifecycle

```java
public class DataLifecycleManager {
    
    @Scheduled(cron = "0 5 * * * *") // 5 min past each hour
    public void rollupMinuteToHour() {
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.HOURS);
        List<String> dimensionKeys = getActiveDimensionKeys(hourAgo);
        
        for (String dimKey : dimensionKeys) {
            long totalImp = 0, totalClk = 0, totalSpd = 0;
            for (int min = 0; min < 60; min++) {
                Instant minuteStart = hourAgo.plus(Duration.ofMinutes(min));
                Map<String, String> hash = redis.hgetAll("agg:" + dimKey + ":" + minuteStart.getEpochSecond());
                if (hash != null && !hash.isEmpty()) {
                    totalImp += Long.parseLong(hash.getOrDefault("impressions", "0"));
                    totalClk += Long.parseLong(hash.getOrDefault("clicks", "0"));
                    totalSpd += Long.parseLong(hash.getOrDefault("spend_cents", "0"));
                }
            }
            if (totalImp > 0 || totalClk > 0) {
                clickHouseStore.writeHourlyAggregate(dimKey, hourAgo, totalImp, totalClk, totalSpd);
            }
        }
    }
    
    @Scheduled(cron = "0 30 0 * * *") // 00:30 daily
    public void rollupHourToDay() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        clickhouse.execute("""
            INSERT INTO ad_metrics_daily
            SELECT campaign_id, ad_group_id, ad_id, device_type, country,
                   toDate(window_start), sum(impressions), sum(clicks), sum(spend_cents)
            FROM ad_metrics_hourly WHERE toDate(window_start) = ?
            GROUP BY campaign_id, ad_group_id, ad_id, device_type, country, toDate(window_start)
            """, Date.valueOf(yesterday));
    }
}
```

**Storage Tiers**:
```
Tier 1 (Hot):    Redis      | Minute | 24 hours   | < 10ms  | ~300 GB
Tier 2 (Warm):   ClickHouse | Hour   | 30 days    | < 500ms | ~1.5 TB
Tier 3 (Cold):   ClickHouse | Day    | 2 years    | < 5s    | ~146 GB
Tier 4 (Archive):S3 Parquet | Raw    | 7 years    | Minutes | ~PB---

### Deep Dive 7: Backpressure & Flow Control

**The Problem**: During flash events (Super Bowl, Black Friday), ad event volume can spike 5-10x. If Flink can't keep up, Kafka consumer lag grows, freshness degrades, and eventually OOM kills workers.

```java
public class BackpressureManager {

    /**
     * Multi-level backpressure strategy:
     * 
     * Level 1: Flink internal backpressure (automatic)
     *   - Flink's credit-based flow control slows upstream operators
     *   - Kafka consumer pauses when Flink buffers are full
     * 
     * Level 2: Dynamic sampling (shed load gracefully)
     *   - When lag > threshold, sample impression events (clicks always processed)
     *   - Apply correction factor to sampled counts
     * 
     * Level 3: Priority queues (process high-value events first)
     *   - Click events > impression events (clicks = revenue)
     *   - High-spend campaigns > low-spend campaigns
     */
    
    private static final long LAG_THRESHOLD_SAMPLING = 1_000_000; // 1M events behind
    private static final long LAG_THRESHOLD_CRITICAL = 10_000_000; // 10M events behind
    private static final double IMPRESSION_SAMPLE_RATE = 0.1; // Sample 10% of impressions
    
    public ProcessingDecision shouldProcess(AdEvent event, long currentLag) {
        // Clicks are always processed (revenue events)
        if (event.getEventType() == EventType.CLICK) {
            return ProcessingDecision.PROCESS;
        }
        
        // Normal mode: process everything
        if (currentLag < LAG_THRESHOLD_SAMPLING) {
            return ProcessingDecision.PROCESS;
        }
        
        // Sampling mode: sample impressions, apply correction factor
        if (currentLag < LAG_THRESHOLD_CRITICAL) {
            if (ThreadLocalRandom.current().nextDouble() < IMPRESSION_SAMPLE_RATE) {
                return ProcessingDecision.PROCESS_WITH_WEIGHT(1.0 / IMPRESSION_SAMPLE_RATE);
            }
            return ProcessingDecision.DROP;
        }
        
        // Critical mode: only process clicks + high-spend campaigns
        if (event.getBidAmount().compareTo(BigDecimal.valueOf(0.10)) > 0) {
            return ProcessingDecision.PROCESS; // High-value impression
        }
        
        metrics.counter("backpressure.events.dropped").increment();
        return ProcessingDecision.DROP;
    }
}
```

**Autoscaling for Flink**:
```yaml
# Kubernetes HPA for Flink TaskManagers
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    kind: Deployment
    name: flink-taskmanager
  minReplicas: 50
  maxReplicas: 500
  metrics:
    - type: External
      external:
        metric:
          name: kafka_consumer_lag
          selector:
            matchLabels:
              consumer_group: ad-aggregator
        target:
          type: Value
          value: "500000"  # Scale up when lag > 500K
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Pods
          value: 50
          periodSeconds: 60
```

---

### Deep Dive 8: Query Routing & Multi-Tier Merge

**The Problem**: A dashboard query for "last 7 days, hourly" spans two storage tiers: Redis (last 24h, minute-level) and ClickHouse (older, hour-level). The query service must route and merge transparently.

```java
public class QueryRouter {

    /**
     * Route queries to the appropriate storage tier based on time range
     * and requested granularity. Merge results from multiple tiers.
     */
    
    public MetricResponse query(MetricQueryRequest request) {
        Instant now = Instant.now();
        Instant from = request.getFrom();
        Instant to = request.getTo();
        TimeGranularity granularity = request.getGranularity();
        
        List<MetricPoint> results = new ArrayList<>();
        
        // Determine which tiers to query
        Instant redisStart = now.minus(Duration.ofHours(24));
        
        if (granularity == TimeGranularity.MINUTE) {
            // Minute-level: Redis only (last 24h)
            if (from.isBefore(redisStart)) {
                throw new BadRequestException("Minute-level data only available for last 24 hours");
            }
            results = redisStore.queryMinuteLevel(request.getCampaignId(), from, to);
            
        } else if (granularity == TimeGranularity.HOUR) {
            // Hour-level: Redis for recent + ClickHouse for older
            if (to.isAfter(redisStart)) {
                // Recent part from Redis (aggregate minutes to hours)
                Instant recentFrom = from.isAfter(redisStart) ? from : redisStart;
                List<MetricPoint> recentMinutes = redisStore.queryMinuteLevel(
                    request.getCampaignId(), recentFrom, to);
                results.addAll(aggregateMinutesToHours(recentMinutes));
            }
            if (from.isBefore(redisStart)) {
                // Older part from ClickHouse
                Instant olderTo = to.isBefore(redisStart) ? to : redisStart;
                results.addAll(clickHouseStore.queryHourly(request.withTo(olderTo)));
            }
            
        } else if (granularity == TimeGranularity.DAY) {
            // Day-level: ClickHouse only
            results = clickHouseStore.queryDaily(request);
        }
        
        // Sort by window_start and compute derived metrics
        results.sort(Comparator.comparing(MetricPoint::getWindowStart));
        
        return MetricResponse.builder()
            .data(results)
            .freshness(Duration.between(getLastUpdateTime(), now).getSeconds())
            .build();
    }
    
    /** Aggregate minute-level points into hour-level */
    private List<MetricPoint> aggregateMinutesToHours(List<MetricPoint> minutes) {
        return minutes.stream()
            .collect(Collectors.groupingBy(m -> m.getWindowStart().truncatedTo(ChronoUnit.HOURS)))
            .entrySet().stream()
            .map(e -> MetricPoint.builder()
                .windowStart(e.getKey())
                .impressions(e.getValue().stream().mapToLong(MetricPoint::getImpressions).sum())
                .clicks(e.getValue().stream().mapToLong(MetricPoint::getClicks).sum())
                .spend(e.getValue().stream().map(MetricPoint::getSpend).reduce(BigDecimal.ZERO, BigDecimal::add))
                .build())
            .sorted(Comparator.comparing(MetricPoint::getWindowStart))
            .toList();
    }
}
```

**Query Routing Decision Tree**:
```
Query: granularity=MINUTE
  → Redis only (last 24h)

Query: granularity=HOUR, range within 24h
  → Redis (aggregate minutes → hours)

Query: granularity=HOUR, range spans 24h boundary
  → Redis (recent) + ClickHouse (older) → MERGE

Query: granularity=DAY
  → ClickHouse only (daily table)

Query: granularity=HOUR, range > 30 days ago
  → ClickHouse only (hourly table)
```

---

### Deep Dive 9: Hot Campaign Sharding — Preventing Kafka Partition Skew

**The Problem**: A single large campaign (e.g., Coca-Cola Super Bowl ad) generates 50% of all events. If partitioned by campaign_id, one Kafka partition and one Flink task gets 50% of the load.

```java
public class CampaignAwarePartitioner implements Partitioner {

    private final Set<String> hotCampaigns = ConcurrentHashMap.newKeySet();
    
    @Override
    public int partition(String topic, String key, byte[] value, int numPartitions) {
        String campaignId = key; // partition key = campaign_id
        
        if (hotCampaigns.contains(campaignId)) {
            // Hot campaign: spread across 20 random partitions
            int subKey = ThreadLocalRandom.current().nextInt(20);
            return Math.abs((campaignId + ":" + subKey).hashCode()) % numPartitions;
        }
        
        // Normal: deterministic partition by campaign
        return Math.abs(campaignId.hashCode()) % numPartitions;
    }
}

/**
 * Downstream merge for hot campaigns:
 * Multiple Flink tasks produce partial aggregates for the same campaign.
 * A secondary aggregation step merges them before writing to Redis/ClickHouse.
 */
public class HotCampaignMerger {
    
    @Scheduled(fixedRate = 10_000) // Every 10 seconds
    public void mergeHotCampaignPartials() {
        for (String campaignId : hotCampaignTracker.getHotCampaigns()) {
            Map<String, AggregatePartial> partials = collectPartials(campaignId);
            
            // Merge all partials into a single aggregate
            AggregatePartial merged = partials.values().stream()
                .reduce(AggregatePartial::merge)
                .orElse(AggregatePartial.empty());
            
            // Write merged result to Redis
            redisStore.writeMinuteAggregate(campaignId, merged.getWindowStart(),
                merged.getImpressions(), merged.getClicks(), merged.getSpend());
        }
    }
    
    /** Detect hot campaigns based on event rate */
    public void detectHotCampaigns() {
        // Campaign is "hot" if > 100K events/minute
        // Typically 10-50 campaigns at any time
        // Detection via Redis counters with 1-minute TTL
    }
}
```

---

### Deep Dive 10: Flink Checkpointing & Exactly-Once Guarantees

**The Problem**: If a Flink task crashes mid-window, all in-memory aggregates are lost. We need exactly-once processing guarantees for financial correctness.

```java
public class FlinkCheckpointConfig {

    /**
     * Flink's exactly-once guarantee for ad aggregation:
     * 
     * 1. Checkpointing: every 30 seconds, Flink snapshots all state to S3
     * 2. Kafka offsets: committed as part of the checkpoint (transactional)
     * 3. On failure: Flink restores from last checkpoint, replays from Kafka offset
     * 4. Dedup layer prevents double-counting of replayed events
     * 
     * This means: even if a task crashes, no events are lost and no events
     * are double-counted after recovery.
     */
    
    public StreamExecutionEnvironment configureCheckpointing(StreamExecutionEnvironment env) {
        // Enable exactly-once checkpointing
        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        
        // Checkpoint storage: S3 (durable, shared across task managers)
        env.getCheckpointConfig().setCheckpointStorage(
            "s3://flink-checkpoints/ad-aggregator/");
        
        // Tolerate up to 3 consecutive checkpoint failures
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);
        
        // Minimum time between checkpoints (prevent thrashing)
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(10_000);
        
        // Checkpoint timeout (kill if takes too long)
        env.getCheckpointConfig().setCheckpointTimeout(120_000);
        
        // Enable unaligned checkpoints for faster completion under backpressure
        env.getCheckpointConfig().enableUnalignedCheckpoints();
        
        // Retain checkpoints on cancellation (for manual recovery)
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
            ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        
        return env;
    }
}
```

**Recovery Scenario**:
```
Timeline:
  T=0:00  Checkpoint #1 completed (all state saved to S3)
  T=0:15  Processing events...
  T=0:25  Task manager crashes! 
          → 25 seconds of in-memory state LOST
  T=0:30  Flink detects failure, starts recovery
  T=0:35  Restore state from Checkpoint #1 (T=0:00 snapshot)
  T=0:36  Resume consuming Kafka from offset at T=0:00
  T=0:36-T=0:50  Replay 25 seconds of events
          → Dedup layer prevents double-counting
  T=0:50  Fully caught up, back to normal

Data loss: ZERO (replayed from Kafka)
Double-counting: ZERO (dedup layer)
Freshness impact: ~60 seconds of extra delay during recovery
```

---

### Deep Dive 11: Observability & Operational Metrics

```java
public class AdAggregatorMetrics {
    // Ingestion
    Counter eventsReceived   = Counter.builder("ad.events.received").register(registry);
    Counter eventsDropped    = Counter.builder("ad.events.dropped").register(registry);
    Counter duplicatesFound  = Counter.builder("ad.events.duplicates").register(registry);
    Gauge   kafkaLag         = Gauge.builder("ad.kafka.consumer_lag", ...).register(registry);
    
    // Aggregation
    Timer   aggregationTime  = Timer.builder("ad.aggregation.process_ms").register(registry);
    Counter windowsFlushed   = Counter.builder("ad.windows.flushed").register(registry);
    Gauge   activeWindows    = Gauge.builder("ad.windows.active", ...).register(registry);
    Counter lateEvents       = Counter.builder("ad.events.late").register(registry);
    
    // Serving
    Timer   redisWriteTime   = Timer.builder("ad.redis.write_ms").register(registry);
    Timer   queryLatency     = Timer.builder("ad.query.latency_ms").register(registry);
    Counter queriesTotal     = Counter.builder("ad.queries.total").register(registry);
    
    // Data quality
    Gauge   freshness        = Gauge.builder("ad.freshness_seconds", ...).register(registry);
    Counter reconciliations  = Counter.builder("ad.reconciliation.corrections").register(registry);
    
    // Infrastructure
    Gauge   flinkCheckpointDuration = Gauge.builder("ad.flink.checkpoint_ms", ...).register(registry);
    Gauge   clickhouseQueryTime     = Gauge.builder("ad.clickhouse.query_ms", ...).register(registry);
}
```

**Key Alerts**:
```yaml
alerts:
  - name: FreshnessBreached
    condition: ad.freshness_seconds > 30
    severity: CRITICAL
    message: "Ad metrics freshness > 30s SLA. Flink pipeline may be degraded."
    
  - name: HighDuplicateRate
    condition: rate(ad.events.duplicates, 5m) / rate(ad.events.received, 5m) > 0.01
    severity: WARNING
    message: ">1% duplicate events. Ad servers may be retrying excessively."
    
  - name: KafkaLagCritical
    condition: ad.kafka.consumer_lag > 5000000
    severity: CRITICAL
    message: "Consumer lag > 5M events. Consider scaling Flink or enabling sampling."
```

---

### Deep Dive 12: Batch Reconciliation — Correcting Stream Drift

**The Problem**: Streaming aggregation may drift from truth over time due to late events, sampling during backpressure, or edge cases in exactly-once processing. A daily batch job provides ground-truth correction.

```java
public class DailyReconciliation {

    /**
     * Daily reconciliation job: compare streaming aggregates with batch-computed
     * exact counts from raw events. Flag and correct any discrepancies.
     * 
     * Runs at 03:00 UTC for the previous day.
     * Source of truth: raw events in S3 (Parquet).
     */
    
    @Scheduled(cron = "0 0 3 * * *")
    public void reconcile() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        
        // Step 1: Compute exact daily aggregates from raw events (Spark)
        Map<String, ExactAggregate> exactCounts = sparkJob.computeExactDailyAggregates(yesterday);
        
        // Step 2: Read streaming aggregates from ClickHouse
        Map<String, ExactAggregate> streamCounts = clickHouseStore.getDailyAggregates(yesterday);
        
        // Step 3: Compare and flag discrepancies
        int corrections = 0;
        for (var entry : exactCounts.entrySet()) {
            String dimensionKey = entry.getKey();
            ExactAggregate exact = entry.getValue();
            ExactAggregate stream = streamCounts.get(dimensionKey);
            
            if (stream == null) {
                log.warn("Missing stream aggregate for {}", dimensionKey);
                clickHouseStore.writeDailyAggregate(dimensionKey, yesterday, exact);
                corrections++;
                continue;
            }
            
            // Check if counts differ by more than 0.1%
            double impressionDrift = Math.abs(exact.getImpressions() - stream.getImpressions()) 
                / (double) Math.max(exact.getImpressions(), 1);
            double clickDrift = Math.abs(exact.getClicks() - stream.getClicks()) 
                / (double) Math.max(exact.getClicks(), 1);
            
            if (impressionDrift > 0.001 || clickDrift > 0.001) {
                log.warn("Drift detected for {}: stream_imp={}, exact_imp={}, stream_clk={}, exact_clk={}",
                    dimensionKey, stream.getImpressions(), exact.getImpressions(),
                    stream.getClicks(), exact.getClicks());
                
                // Overwrite with exact counts
                clickHouseStore.updateDailyAggregate(dimensionKey, yesterday, exact);
                corrections++;
            }
        }
        
        log.info("Reconciliation complete for {}: {} corrections out of {} dimensions",
            yesterday, corrections, exactCounts.size());
        metrics.counter("reconciliation.corrections").increment(corrections);
    }
}
```

**Reconciliation Pipeline**:
```
Daily at 03:00 UTC:
  1. Spark reads raw events from S3 (yesterday)
  2. Compute exact counts per (campaign, ad, device, country, day)
  3. Compare with ClickHouse daily table
  4. Correct any discrepancies > 0.1%
  
Expected drift:
  - 99.99% of dimensions match exactly
  - ~0.01% have small drift (< 0.1%) due to late events
  - Corrections applied in < 30 minutes
  - Advertiser-facing dashboards updated by 04:00 UTC
```

---

---

### Deep Dive 13: Conversion Attribution — Click-to-Purchase Tracking

**The Problem**: Advertisers want to know "how many users who clicked my ad actually bought something?" This requires joining click events with purchase events within a 24-hour attribution window. At scale, this means correlating 10B clicks/day with 500M purchases/day across a 24-hour window.

```java
public class ConversionAttributionService {

    /**
     * Attribution pipeline: correlate ad clicks with downstream purchase events.
     * 
     * Model: Last-click attribution (the last ad clicked before purchase gets credit).
     * Window: 24 hours (click must happen within 24h before purchase).
     * 
     * Architecture:
     *   1. Click events → Flink → write to "click_journal" (Redis sorted set per user, 24h TTL)
     *   2. Purchase events → Flink → lookup user's recent clicks → attribute conversion
     *   3. Attribution result → Kafka → update campaign metrics in ClickHouse
     */
    
    // Redis schema for click journal:
    // Key: click_journal:{user_id}
    // Value: Sorted set where score = click_timestamp, member = JSON(ad_id, campaign_id, bid_amount)
    // TTL: 25 hours (24h window + 1h buffer)
    
    /** Store a click for potential future attribution */
    public void recordClick(AdEvent clickEvent) {
        String key = "click_journal:" + clickEvent.getUserId();
        String member = objectMapper.writeValueAsString(ClickRecord.builder()
            .adId(clickEvent.getAdId())
            .campaignId(clickEvent.getCampaignId())
            .bidAmount(clickEvent.getBidAmount())
            .clickTime(clickEvent.getEventTime())
            .build());
        
        redis.zadd(key, clickEvent.getEventTime().getEpochSecond(), member);
        redis.expire(key, 90000); // 25 hours
        
        // Cap entries per user (prevent memory bloat for bot-like users)
        redis.zremrangeByRank(key, 0, -101); // Keep last 100 clicks max
    }
    
    /** When a purchase happens, find the attributable click */
    public Optional<ConversionAttribution> attributeConversion(PurchaseEvent purchase) {
        String key = "click_journal:" + purchase.getUserId();
        Instant windowStart = purchase.getPurchaseTime().minus(Duration.ofHours(24));
        
        // Get all clicks in the 24h window, most recent first
        Set<String> recentClicks = redis.zrevrangeByScore(key,
            String.valueOf(purchase.getPurchaseTime().getEpochSecond()),
            String.valueOf(windowStart.getEpochSecond()),
            0, 1); // Only need the most recent (last-click attribution)
        
        if (recentClicks.isEmpty()) {
            return Optional.empty(); // No attributable click — organic conversion
        }
        
        ClickRecord lastClick = objectMapper.readValue(recentClicks.iterator().next(), ClickRecord.class);
        
        ConversionAttribution attribution = ConversionAttribution.builder()
            .userId(purchase.getUserId())
            .purchaseId(purchase.getPurchaseId())
            .purchaseAmount(purchase.getAmount())
            .attributedAdId(lastClick.getAdId())
            .attributedCampaignId(lastClick.getCampaignId())
            .clickTime(lastClick.getClickTime())
            .purchaseTime(purchase.getPurchaseTime())
            .attributionWindowHours(24)
            .timeBetweenClickAndPurchase(
                Duration.between(lastClick.getClickTime(), purchase.getPurchaseTime()))
            .build();
        
        // Publish attribution event
        kafka.send(new ProducerRecord<>("conversion-attributions",
            lastClick.getCampaignId(), attribution));
        
        metrics.counter("conversions.attributed").increment();
        
        return Optional.of(attribution);
    }
    
    /**
     * Flink job: consume purchase events, perform attribution, aggregate conversion metrics.
     */
    public void processConversion(ConversionAttribution attribution) {
        // Update campaign conversion metrics in ClickHouse
        clickhouse.execute("""
            INSERT INTO ad_conversions 
            (campaign_id, ad_id, conversion_date, conversions, revenue, cost)
            VALUES (?, ?, ?, 1, ?, ?)
            """,
            attribution.getAttributedCampaignId(),
            attribution.getAttributedAdId(),
            Date.from(attribution.getPurchaseTime()),
            attribution.getPurchaseAmount(),
            attribution.getClickBidAmount());
        
        // Update Redis for real-time conversion dashboard
        String key = "conv:" + attribution.getAttributedCampaignId() + ":today";
        redis.hincrBy(key, "conversions", 1);
        redis.hincrBy(key, "revenue_cents", 
            attribution.getPurchaseAmount().multiply(BigDecimal.valueOf(100)).longValue());
        redis.expire(key, 172800);
    }
}
```

**Conversion Metrics Served to Advertisers**:
```json
{
  "campaign_id": "camp_456",
  "period": "today",
  "impressions": 45678901,
  "clicks": 1678234,
  "conversions": 23456,
  "conversion_rate": 0.014,
  "revenue": 1234567.89,
  "roas": 13.83,
  "cost": 89234.56,
  "cpa": 3.80
}
```

**Attribution Architecture**:
```
Click Events ──→ Flink ──→ Redis (click_journal per user, 24h TTL)
                                      │
Purchase Events ──→ Flink ──→ Lookup click_journal ──→ Attribution
                                                          │
                                                    ┌─────▼──────┐
                                                    │   Kafka     │
                                                    │ "conversions"│
                                                    └─────┬──────┘
                                                          │
                                              ┌───────────┼───────────┐
                                              ▼           ▼           ▼
                                         ClickHouse    Redis      Billing
                                         (historical)  (real-time) (advertiser)
```

---

### Deep Dive 14: Anomaly Detection — CTR Drop & Spend Spike Alerts

**The Problem**: Advertisers need to be alerted when something unusual happens: a sudden CTR drop (ad creative broken?), a spend spike (budget burning too fast), or an impression surge (potential fraud?). We need real-time anomaly detection on streaming aggregates.

```java
public class AnomalyDetectionService {

    /**
     * Real-time anomaly detection on ad metrics.
     * Runs alongside the aggregation pipeline, consuming minute-level aggregates.
     * 
     * Detection methods:
     *   1. Z-score: compare current value to rolling mean/stddev
     *   2. Rate of change: detect sudden jumps (> 3x baseline in 5 minutes)
     *   3. Absolute thresholds: budget exhaustion, zero impressions
     * 
     * Alerts: published to Kafka → consumed by notification service → email/Slack/webhook
     */
    
    private static final double Z_SCORE_THRESHOLD = 3.0; // 3 standard deviations
    private static final double RATE_CHANGE_THRESHOLD = 3.0; // 3x baseline
    private static final int BASELINE_WINDOW_MINUTES = 60; // 1-hour rolling baseline
    
    // Rolling statistics per campaign (stored in Flink state)
    private MapState<String, RollingStats> campaignStats;
    
    /**
     * Check a minute-level aggregate for anomalies.
     * Called by Flink after each minute window closes.
     */
    public List<AnomalyAlert> detectAnomalies(String campaignId, MinuteAggregate current) {
        List<AnomalyAlert> alerts = new ArrayList<>();
        
        RollingStats stats = campaignStats.get(campaignId);
        if (stats == null || stats.getSampleCount() < 10) {
            // Not enough history to detect anomalies
            campaignStats.put(campaignId, updateStats(stats, current));
            return alerts;
        }
        
        // Check 1: CTR anomaly (Z-score)
        double currentCtr = current.getClicks() > 0 
            ? (double) current.getClicks() / current.getImpressions() : 0;
        double ctrZScore = (currentCtr - stats.getCtrMean()) / Math.max(stats.getCtrStddev(), 0.001);
        
        if (Math.abs(ctrZScore) > Z_SCORE_THRESHOLD && current.getImpressions() > 1000) {
            alerts.add(AnomalyAlert.builder()
                .campaignId(campaignId)
                .type(ctrZScore < 0 ? AnomalyType.CTR_DROP : AnomalyType.CTR_SPIKE)
                .severity(Math.abs(ctrZScore) > 5 ? Severity.CRITICAL : Severity.WARNING)
                .currentValue(currentCtr)
                .expectedValue(stats.getCtrMean())
                .zScore(ctrZScore)
                .message(String.format("CTR %s: current=%.4f, expected=%.4f (%.1f sigma)",
                    ctrZScore < 0 ? "dropped" : "spiked", currentCtr, stats.getCtrMean(), ctrZScore))
                .build());
        }
        
        // Check 2: Spend rate anomaly (rate of change)
        double currentSpendRate = current.getSpend().doubleValue(); // Spend per minute
        double baselineSpendRate = stats.getSpendMean();
        
        if (baselineSpendRate > 0 && currentSpendRate > baselineSpendRate * RATE_CHANGE_THRESHOLD) {
            alerts.add(AnomalyAlert.builder()
                .campaignId(campaignId)
                .type(AnomalyType.SPEND_SPIKE)
                .severity(Severity.CRITICAL)
                .currentValue(currentSpendRate)
                .expectedValue(baselineSpendRate)
                .message(String.format("Spend spike: $%.2f/min vs baseline $%.2f/min (%.1fx)",
                    currentSpendRate, baselineSpendRate, currentSpendRate / baselineSpendRate))
                .build());
        }
        
        // Check 3: Zero impressions (ad serving issue?)
        if (current.getImpressions() == 0 && stats.getImpressionsMean() > 100) {
            alerts.add(AnomalyAlert.builder()
                .campaignId(campaignId)
                .type(AnomalyType.ZERO_IMPRESSIONS)
                .severity(Severity.CRITICAL)
                .currentValue(0)
                .expectedValue(stats.getImpressionsMean())
                .message("Zero impressions detected. Expected ~" + (long) stats.getImpressionsMean() + "/min")
                .build());
        }
        
        // Check 4: Impression surge without proportional clicks (fraud indicator)
        if (current.getImpressions() > stats.getImpressionsMean() * 5 &&
            currentCtr < stats.getCtrMean() * 0.1) {
            alerts.add(AnomalyAlert.builder()
                .campaignId(campaignId)
                .type(AnomalyType.POTENTIAL_FRAUD)
                .severity(Severity.WARNING)
                .message("Impression surge (5x) with CTR collapse (0.1x). Possible fraud.")
                .build());
        }
        
        // Update rolling statistics
        campaignStats.put(campaignId, updateStats(stats, current));
        
        // Publish alerts
        for (AnomalyAlert alert : alerts) {
            kafka.send(new ProducerRecord<>("anomaly-alerts", campaignId, alert));
            metrics.counter("anomaly.detected", "type", alert.getType().name()).increment();
        }
        
        return alerts;
    }
    
    /** Welford's online algorithm for computing rolling mean and stddev */
    private RollingStats updateStats(RollingStats stats, MinuteAggregate current) {
        if (stats == null) stats = new RollingStats();
        
        double ctr = current.getImpressions() > 0 
            ? (double) current.getClicks() / current.getImpressions() : 0;
        double spend = current.getSpend().doubleValue();
        double impressions = current.getImpressions();
        
        stats.addSample(ctr, spend, impressions);
        
        // Keep only last 60 samples (1 hour at minute granularity)
        if (stats.getSampleCount() > BASELINE_WINDOW_MINUTES) {
            stats.removeOldestSample();
        }
        
        return stats;
    }
}

/**
 * Rolling statistics using Welford's online algorithm.
 * Computes mean and standard deviation incrementally.
 */
public class RollingStats implements Serializable {
    private int sampleCount;
    private double ctrMean, ctrM2;      // For CTR
    private double spendMean, spendM2;  // For spend
    private double impressionsMean;
    
    public void addSample(double ctr, double spend, double impressions) {
        sampleCount++;
        
        // Welford's for CTR
        double ctrDelta = ctr - ctrMean;
        ctrMean += ctrDelta / sampleCount;
        double ctrDelta2 = ctr - ctrMean;
        ctrM2 += ctrDelta * ctrDelta2;
        
        // Welford's for spend
        double spendDelta = spend - spendMean;
        spendMean += spendDelta / sampleCount;
        double spendDelta2 = spend - spendMean;
        spendM2 += spendDelta * spendDelta2;
        
        // Simple moving average for impressions
        impressionsMean += (impressions - impressionsMean) / sampleCount;
    }
    
    public double getCtrStddev() {
        return sampleCount > 1 ? Math.sqrt(ctrM2 / (sampleCount - 1)) : 0;
    }
    
    public double getSpendStddev() {
        return sampleCount > 1 ? Math.sqrt(spendM2 / (sampleCount - 1)) : 0;
    }
}
```

**Alert Types**:
```
┌───────────────────┬────────────────────┬─────────────────────────────────────┐
│ Anomaly Type      │ Detection Method   │ Action                              │
├───────────────────┼────────────────────┼─────────────────────────────────────┤
│ CTR Drop          │ Z-score > 3σ       │ Alert advertiser, check ad creative │
│ CTR Spike         │ Z-score > 3σ       │ Alert (could be positive or fraud)  │
│ Spend Spike       │ 3x baseline rate   │ Alert, may auto-pause campaign      │
│ Zero Impressions  │ Absolute threshold │ Alert, check ad serving pipeline    │
│ Potential Fraud   │ Impression surge + │ Flag for fraud team review          │
│                   │ CTR collapse       │                                     │
│ Budget Exhaustion │ Spend > 90% budget │ Alert, auto-pause optional          │
└───────────────────┴────────────────────┴─────────────────────────────────────┘
```

**Anomaly Detection Pipeline**:
```
Minute-level aggregates (from Flink)
    ↓
Anomaly Detector (per-campaign rolling stats)
    ↓
┌─ No anomaly → continue monitoring
│
└─ Anomaly detected →
     ├─ Kafka "anomaly-alerts" topic
     ├─ Notification Service → Email / Slack / Webhook
     ├─ Dashboard → red banner "Alert: CTR dropped 45%"
     └─ Auto-action (optional) → pause campaign if spend > budget
---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Dedup** | Bloom filter + Redis (two-tier) | Bloom handles 99.99% in-memory; Redis for exact check on 0.01% |
| **Windows** | Multi-window Flink (min/hour/day) | Single pipeline, three granularities, different storage tiers |
| **Late events** | 5-min allowed lateness + delta corrections | Balances accuracy with processing complexity |
| **Real-time serving** | Redis (minute-level, 24h TTL) | < 10ms reads, HINCRBY for concurrent writes |
| **Historical** | ClickHouse (columnar, partitioned by month) | Sub-second analytical queries over 2 years |
| **Storage lifecycle** | 4-tier (Redis/CH-SSD/CH-HDD/S3) | Hot→warm→cold→archive with automatic rollups |
| **Backpressure** | Impression sampling + click priority | Never lose clicks (revenue); impressions are approximate-OK |
| **Exactly-once** | Flink checkpointing + Kafka transactions + dedup | Financial correctness for billing |
| **Hot campaigns** | Spread partitions + downstream merge | Prevents single-task bottleneck on viral campaigns |
| **Reconciliation** | Daily batch vs stream comparison | Catches drift from late events and edge cases |
| **Attribution** | Last-click, Redis click journal (24h TTL) | Correlate 10B clicks with 500M purchases daily |
| **Anomaly detection** | Z-score + rate-of-change on rolling stats | Real-time CTR drop, spend spike, fraud alerts |

## Interview Talking Points

1. **"Two-tier dedup: Bloom + Redis"** — Bloom handles 99.99% in < 1us. Only 0.01% reach Redis. Zero false negatives.
2. **"Multi-window Flink"** — Single pipeline produces minute/hour/day aggregates simultaneously. Different sinks per window.
3. **"Watermarks handle late events"** — 5-min allowed lateness with delta corrections. Very late events dead-lettered.
4. **"Redis for real-time, ClickHouse for history"** — Redis: minute-level, < 10ms. ClickHouse: hour/day, < 500ms. Query router merges seamlessly.
5. **"4-tier storage lifecycle"** — Redis (24h) → ClickHouse SSD (30d) → ClickHouse HDD (2y) → S3 Glacier (7y).
6. **"Impression sampling under backpressure"** — Clicks always processed (revenue). Impressions sampled at 10% with correction factor.
7. **"Exactly-once via Flink checkpoints"** — 30s checkpoint interval. On crash: restore state, replay Kafka, dedup prevents double-count.
8. **"Hot campaign spreading"** — Top 50 campaigns spread across 20 Kafka partitions. Downstream merge every 10s.
9. **"ClickHouse columnar compression"** — 10x compression. 6-month query scans 360 KB on disk. < 100ms.
10. **"Daily reconciliation catches drift"** — Spark recomputes exact counts from raw events. Corrects > 0.1% discrepancies.
11. **"Spend stored as cents (long)"** — Avoids floating-point errors in financial calculations. $23.45 = 2345 cents.
12. **"HINCRBY for concurrent writes"** — Multiple Flink tasks write to same Redis key atomically. No read-modify-write race.
13. **"Last-click attribution via Redis click journal"** — Store clicks in per-user sorted sets (24h TTL). On purchase, lookup most recent click. Join 10B clicks with 500M purchases daily.
14. **"Anomaly detection with Welford's algorithm"** — Rolling mean/stddev per campaign. Z-score > 3σ triggers alert. Catches CTR drops, spend spikes, fraud patterns in real-time.

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 14 topics (choose 2-3 based on interviewer interest)
---

## 🏗️ Infrastructure Sizing & Cost Estimation

### Kafka Cluster
```
Brokers: 30 (each with 12 TB NVMe SSD, 64 GB RAM)
Partitions: 500+ for "ad-events" topic
Throughput: 2.3M events/sec × 200 bytes = 460 MB/sec write
Replication: 3 replicas → 1.4 GB/sec total write bandwidth
Retention: 7 days × 40 TB/day = 280 TB → ~10 TB/broker (with compression ~4 TB)
Network: 25 Gbps per broker
```

### Apache Flink Cluster
```
TaskManagers: 50-500 (auto-scaled)
CPU per TM: 8 cores
Memory per TM: 32 GB (16 GB for state, 16 GB for processing)
Checkpoints: S3, 30-second interval
State backend: RocksDB (for large state) or HashMapStateBackend (for speed)
Checkpoint size: ~50 GB (all window states + dedup bloom filters)
Recovery time: < 60 seconds from checkpoint restore
```

### Redis Cluster
```
Nodes: 20 (each with 32 GB RAM, 100 GB SSD for persistence)
Total memory: 640 GB (300 GB used for ad aggregates)
Keyspace:
  - Minute-level aggregates: 1.44B keys × 200 bytes = ~290 GB
  - Daily rollup counters: 10M × 200 bytes = 2 GB
  - Dedup keys: 100M × 100 bytes = 10 GB
  - Click journals (attribution): 200M users × avg 100 bytes = 20 GB
Read QPS: ~100K (dashboard queries)
Write QPS: ~500K (Flink partial flushes every 10s)
```

### ClickHouse Cluster
```
Nodes: 10 (each with 32 cores, 128 GB RAM, 4 TB NVMe SSD)
Sharding: by campaign_id hash (10 shards)
Replication: 2 replicas per shard (20 total nodes)
Hourly table: ~1.5 TB (30 days, partitioned by month)
Daily table: ~146 GB (2 years)
Compression ratio: ~10x (columnar + LZ4)
On-disk: ~165 GB across cluster (~8 GB per node)
Query concurrency: 50 simultaneous queries per node
```

### S3 Archive
```
Raw events: 40 TB/day → Parquet compressed ~8 TB/day
30-day buffer: 240 TB
Annual archive: 2.9 PB → S3 Glacier: ~$12K/month
Parquet format: columnar, splittable, schema-on-read
Used for: reconciliation jobs, ad-hoc analysis, regulatory compliance
```

### Estimated Monthly Cost (AWS)
```
┌─────────────────────┬────────────────┬─────────────────┐
│ Component           │ Instances      │ Monthly Cost     │
├─────────────────────┼────────────────┼─────────────────┤
│ Kafka (MSK)         │ 30 brokers     │ $45,000          │
│ Flink (EMR/K8s)     │ 50-500 TMs     │ $30,000 (avg)    │
│ Redis (ElastiCache) │ 20 nodes       │ $20,000          │
│ ClickHouse (EC2)    │ 20 nodes       │ $25,000          │
│ S3 (Archive)        │ ~3 PB/year     │ $12,000          │
│ Network (inter-AZ)  │ ~10 TB/day     │ $5,000           │
│ Monitoring/Logging  │ CloudWatch+DD  │ $3,000           │
├─────────────────────┼────────────────┼─────────────────┤
│ TOTAL               │                │ ~$140,000/month  │
└─────────────────────┴────────────────┴─────────────────┘

At 200B events/day = $0.0000007 per event (~$0.70 per million events)
Revenue from those events: ~$500M/month in ad spend
Infrastructure cost = 0.028% of ad revenue → extremely cost-efficient
```

---

## 🔄 Operational Runbooks

### Runbook 1: Freshness SLA Breach (> 30 seconds)
```
Symptom: ad.freshness_seconds > 30 for > 2 minutes
Root causes (in order of likelihood):
  1. Kafka consumer lag (Flink can't keep up)
     → Check: ad.kafka.consumer_lag
     → Fix: Scale up Flink TaskManagers
  2. Flink checkpoint taking too long
     → Check: ad.flink.checkpoint_ms
     → Fix: Enable unaligned checkpoints, increase checkpoint timeout
  3. Redis write latency spike
     → Check: ad.redis.write_ms
     → Fix: Check Redis cluster health, add nodes if memory-full
  4. Network partition between Kafka and Flink
     → Check: Kafka broker logs, Flink task manager logs
     → Fix: Restart affected Flink task managers
```

### Runbook 2: Count Discrepancy Detected
```
Symptom: reconciliation.corrections > 100 in one day
Root causes:
  1. Late event volume higher than expected
     → Check: ad.events.late rate
     → Fix: Increase allowed lateness window (e.g., 5 min → 10 min)
  2. Flink checkpoint failure during window close
     → Check: checkpoint failure logs
     → Fix: Review checkpoint configuration, increase timeout
  3. Dedup false positive rate too high (rejecting valid events)
     → Check: dedup.bloom_false_positive rate
     → Fix: Increase Bloom filter capacity or reduce rotation frequency
```

### Runbook 3: Dashboard Query Latency > 5 seconds
```
Symptom: ad.query.latency_ms.p99 > 5000
Root causes:
  1. ClickHouse query hitting cold partitions (not in cache)
     → Fix: Pre-warm partitions, add more RAM to ClickHouse nodes
  2. Query spanning too many dimensions (fan-out)
     → Fix: Add query limits, require campaign_id filter
  3. Query router merging too many tiers
     → Fix: Check tier boundary logic, optimize merge
```

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Trending Hashtags** | Same streaming aggregation pattern | Trending uses approximate top-K; ad clicks need exact counts |
| **Distributed Metrics (Datadog)** | Similar multi-window aggregation | Metrics are arbitrary time series; ad clicks have fixed dimensions |
| **Real-Time Leaderboard** | Similar Redis serving pattern | Leaderboard is single-dimensional; ad metrics are multi-dimensional |
| **Fraud Detection** | Downstream consumer of same events | Fraud focuses on anomaly detection; aggregator focuses on counting |
| **Billing System** | Depends on aggregated counts | Billing needs financial-grade correctness; serves from reconciled data |

---

## 🔧 Technology Alternatives & Justifications

### Stream Processing: Why Flink Over Kafka Streams / Spark Streaming?

| Feature | Apache Flink | Kafka Streams | Spark Streaming |
|---------|-------------|---------------|-----------------|
| **Latency** | Sub-second (true streaming) | Sub-second | 1-10 seconds (micro-batch) |
| **Exactly-once** | Native (Kafka + checkpoints) | Native (Kafka transactions) | At-least-once default |
| **Windowing** | Rich (tumbling, sliding, session, custom) | Basic (tumbling, sliding) | Tumbling + sliding |
| **State management** | Built-in (RocksDB, heap) | Embedded (RocksDB) | External only |
| **Backpressure** | Credit-based flow control | Kafka-native | Receiver-based |
| **Cluster management** | Standalone / YARN / K8s | No cluster (library) | Requires Spark cluster |
| **Event-time processing** | First-class watermarks | Limited | Available |
| **Checkpoint size** | Large state OK (incremental) | Limited by Kafka | RDD lineage |
| **Operational complexity** | Medium (JobManager/TaskManager) | Low (embedded in app) | High (Spark ecosystem) |

**Our choice**: Flink — because we need:
1. Sub-second latency (30-second freshness SLA)
2. Multi-window aggregation with event-time semantics
3. Large keyed state (millions of dimension keys per window)
4. Exactly-once guarantees for financial billing data
5. Sophisticated watermark handling for late events

**When would Kafka Streams be better?**
- Simpler pipelines (single window, no complex state)
- Team already operates Kafka but not Flink
- Lower operational overhead preferred

### Analytical DB: Why ClickHouse Over Druid / Pinot / BigQuery?

| Feature | ClickHouse | Apache Druid | Apache Pinot | BigQuery |
|---------|-----------|-------------|-------------|----------|
| **Query latency** | < 100ms (hot data) | < 500ms | < 100ms | 1-10 seconds |
| **Ingestion latency** | Seconds | Seconds | Seconds | Minutes |
| **SQL support** | Full SQL | Limited SQL | Limited SQL | Full SQL |
| **Compression** | Excellent (10-20x) | Good (5-10x) | Good (5-10x) | Excellent |
| **Join support** | Good | Poor | Poor | Excellent |
| **Operational** | Self-hosted | Complex | Complex | Managed |
| **Cost** | Low (open-source) | Low (open-source) | Low (open-source) | Pay-per-query |
| **Real-time ingestion** | Kafka engine | Native Kafka | Native Kafka | Streaming insert |

**Our choice**: ClickHouse — because we need:
1. Full SQL for flexible dashboard queries
2. Sub-second queries on pre-aggregated data
3. Excellent compression (we store 2 years of data)
4. Simple operational model (MergeTree engine, auto-partitioning)
5. Cost-effective at petabyte scale

### Real-Time Store: Why Redis Over DynamoDB / Cassandra?

```
Redis advantages for our use case:
  ✓ HINCRBY: atomic hash field increment (concurrent Flink writes)
  ✓ Pipeline: batch multiple operations in single round-trip
  ✓ TTL per key: automatic expiration of old minute-level data
  ✓ < 1ms latency: critical for dashboard responsiveness
  ✓ Sorted sets: for click journal (attribution, ordered by time)

DynamoDB would work but:
  ✗ Higher latency (1-5ms vs < 1ms)
  ✗ No atomic HINCRBY equivalent (need conditional writes)
  ✗ More expensive at our write volume (500K writes/sec)
  ✗ No pipeline batching (individual DDB calls)

Cassandra would work but:
  ✗ Higher latency (2-10ms)
  ✗ Eventual consistency makes HINCRBY-style increments unreliable
  ✗ More operational complexity (compaction, repair, etc.)
```

---

## 📐 Dimension Key Design

### Composite Dimension Key Structure
```
Format: {campaign_id}:{ad_group_id}:{ad_id}:{device_type}:{country}

Examples:
  camp_456:agr_789:ad_012:MOBILE:US     — Full granularity
  camp_456:agr_789:ad_012:*:*            — Ad-level, all devices, all countries
  camp_456:agr_789:*:*:*                 — Ad-group level rollup
  camp_456:*:*:*:*                       — Campaign-level rollup

Rollup strategy:
  Each event generates 16 dimension key updates:
    1. Full: campaign:adgroup:ad:device:country
    2. No country: campaign:adgroup:ad:device:*
    3. No device: campaign:adgroup:ad:*:country
    4. No device+country: campaign:adgroup:ad:*:*
    5-8. Same pattern for adgroup level (ad=*)
    9-12. Same pattern for campaign level (adgroup=*, ad=*)
    13-16. Global level (campaign=*)

This pre-computes all possible drill-down combinations.
Queries never need to scan and aggregate at query time.
```

### Pre-Aggregation Trade-off
```
Without pre-aggregation:
  Query: "Campaign X, MOBILE, US, last hour"
  → Scan all raw minute keys for camp_456, filter MOBILE+US
  → May scan 60 × 50M ads = 3B keys → SLOW (seconds)

With pre-aggregation (our approach):
  Query: "Campaign X, MOBILE, US, last hour"
  → Direct lookup: agg:camp_456:*:*:MOBILE:US:* (60 minute keys)
  → 60 Redis HGETALL calls pipelined → < 10ms

Cost: 16x write amplification per event
  At 2.3M events/sec × 16 = 37M Redis writes/sec
  → Spread across 20 Redis nodes = 1.85M/node → well within capacity

Trade-off: compute more at write time → serve instantly at read time
This is the classic "pre-compute vs compute-at-query" trade-off.
For dashboards with < 500ms SLA, pre-computation wins.
```

---

## 📊 End-to-End Latency Breakdown

```
Event lifecycle: ad impression/click → dashboard metric visible

Step 1: Ad server generates event                        0 ms
Step 2: Event published to Kafka                         5-20 ms (batching + network)
Step 3: Flink consumer receives event                    1-5 ms (consumer poll)
Step 4: Dedup check (Bloom filter)                       < 0.001 ms
Step 5: Aggregate in Flink state                         < 0.1 ms
Step 6: Partial flush to Redis (every 10 seconds)        0-10,000 ms (wait for flush cycle)
Step 7: Redis write (HINCRBY pipeline)                   1-3 ms
Step 8: Dashboard query hits Redis                       1-5 ms
                                                    ─────────────
Total end-to-end latency:                            8-10,030 ms

Worst case: event arrives just after a flush → waits 10s for next flush → ~10s
Best case: event arrives just before a flush → flushed immediately → ~30ms
Average: ~5 seconds (half of flush interval)
p99: < 15 seconds (within 30-second SLA)

To improve to < 5 seconds: reduce flush interval to 5s (doubles Redis writes)
To improve to < 1 second: flush on every event (requires Redis batching layer)
```

---

**References**:
- Apache Flink Exactly-Once Semantics Documentation
- ClickHouse MergeTree Engine & Partitioning Guide
- Bloom Filter Theory (Burton Bloom, 1970)
- Google Ads Real-Time Reporting Architecture
- Meta Ad Analytics Infrastructure (Scuba + Presto)
- Redis HINCRBY for Concurrent Counter Updates
- Welford's Online Algorithm for Variance (1962)
