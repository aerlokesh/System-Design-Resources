# ⚡ Flink Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how Apache Flink is used in production at companies like Alibaba, Uber, Netflix, Stripe, Pinterest, and more. Every windowing choice, watermark strategy, state backend decision, checkpoint configuration, and sink strategy is explained with the **WHY** behind it.

---

## 📋 Table of Contents

- [⚡ Flink Real-World Applications — Complete System Design Examples](#-flink-real-world-applications--complete-system-design-examples)
  - [📋 Table of Contents](#-table-of-contents)
  - [1. Alibaba — Real-Time Search Ranking During Singles' Day](#1-alibaba--real-time-search-ranking-during-singles-day)
    - [🏗️ Architecture Context](#️-architecture-context)
    - [📐 Pipeline Design](#-pipeline-design)
    - [⚙️ Flink Job Configuration](#️-flink-job-configuration)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step)
    - [🎯 Key Interview Insights](#-key-interview-insights)
  - [2. Uber — Real-Time Surge Pricing Engine](#2-uber--real-time-surge-pricing-engine)
    - [🏗️ Architecture Context](#️-architecture-context-1)
    - [📐 Pipeline Design](#-pipeline-design-1)
    - [⚙️ Flink Job Configuration](#️-flink-job-configuration-1)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-1)
    - [🎯 Key Interview Insights](#-key-interview-insights-1)
  - [3. Netflix — Real-Time A/B Test Metric Computation](#3-netflix--real-time-ab-test-metric-computation)
    - [🏗️ Architecture Context](#️-architecture-context-2)
    - [📐 Pipeline Design](#-pipeline-design-2)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-2)
    - [🎯 Key Interview Insights](#-key-interview-insights-2)
  - [4. Stripe — Real-Time Fraud Detection Pipeline](#4-stripe--real-time-fraud-detection-pipeline)
    - [🏗️ Architecture Context](#️-architecture-context-3)
    - [📐 Pipeline Design](#-pipeline-design-3)
    - [⚙️ Flink Job Configuration](#️-flink-job-configuration-2)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-3)
    - [🎯 Key Interview Insights](#-key-interview-insights-3)
  - [5. Pinterest — Real-Time Ad Click Aggregation \& Billing](#5-pinterest--real-time-ad-click-aggregation--billing)
    - [🏗️ Architecture Context](#️-architecture-context-4)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-4)
    - [🎯 Key Interview Insights](#-key-interview-insights-4)
  - [6. Spotify — Real-Time Royalty \& Play Count Computation](#6-spotify--real-time-royalty--play-count-computation)
    - [🏗️ Architecture Context](#️-architecture-context-5)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-5)
  - [7. DoorDash — Real-Time Delivery ETA \& SLA Monitoring](#7-doordash--real-time-delivery-eta--sla-monitoring)
    - [🏗️ Architecture Context](#️-architecture-context-6)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-6)
  - [8. LinkedIn — Real-Time Who-Viewed-Your-Profile \& Session Analytics](#8-linkedin--real-time-who-viewed-your-profile--session-analytics)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-7)
  - [9. Airbnb — Real-Time Dynamic Pricing Engine](#9-airbnb--real-time-dynamic-pricing-engine)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-8)
  - [10. Datadog — Real-Time Metrics Alerting Pipeline](#10-datadog--real-time-metrics-alerting-pipeline)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-9)
  - [11. Amazon — Real-Time Inventory Reservation \& Low-Stock Alerts](#11-amazon--real-time-inventory-reservation--low-stock-alerts)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-10)
  - [12. Twitter/X — Real-Time Trending Topics Detection](#12-twitterx--real-time-trending-topics-detection)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-11)
  - [13. Robinhood — Real-Time Portfolio Valuation \& Margin Monitoring](#13-robinhood--real-time-portfolio-valuation--margin-monitoring)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-12)
  - [14. Walmart — Real-Time Shelf Replenishment from IoT Sensors](#14-walmart--real-time-shelf-replenishment-from-iot-sensors)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-13)
  - [15. ByteDance/TikTok — Real-Time Content Moderation Pipeline](#15-bytedancetiktok--real-time-content-moderation-pipeline)
    - [🔧 Pipeline — Step by Step](#-pipeline--step-by-step-14)
  - [16–25: Additional Real-World Applications (Summary)](#1625-additional-real-world-applications-summary)
    - [16. Lyft — Real-Time Driver Incentive Optimization](#16-lyft--real-time-driver-incentive-optimization)
    - [17. Coinbase — Real-Time Crypto Transaction Monitoring (AML)](#17-coinbase--real-time-crypto-transaction-monitoring-aml)
    - [18. Shopify — Real-Time Flash Sale Inventory \& Analytics](#18-shopify--real-time-flash-sale-inventory--analytics)
    - [19. Cloudflare — Real-Time DDoS Detection](#19-cloudflare--real-time-ddos-detection)
    - [20. Grab — Real-Time Food Order Demand Forecasting](#20-grab--real-time-food-order-demand-forecasting)
    - [21. Discord — Real-Time Message Rate Limiting \& Spam Detection](#21-discord--real-time-message-rate-limiting--spam-detection)
    - [22. Expedia — Real-Time Hotel Price Comparison](#22-expedia--real-time-hotel-price-comparison)
    - [23. Tesla — Real-Time Fleet Telemetry \& Anomaly Detection](#23-tesla--real-time-fleet-telemetry--anomaly-detection)
    - [24. Zillow — Real-Time Home Price Estimate (Zestimate) Updates](#24-zillow--real-time-home-price-estimate-zestimate-updates)
    - [25. PayPal — Real-Time Cross-Border Payment Risk Scoring](#25-paypal--real-time-cross-border-payment-risk-scoring)
  - [🏆 Cheat Sheet: Flink Design Decision Guide](#-cheat-sheet-flink-design-decision-guide)
  - [🎯 Interview Summary: "When Would You Use Flink?"](#-interview-summary-when-would-you-use-flink)
    - [The 30-Second Answer](#the-30-second-answer)
    - [The 10-Second Trigger Phrases](#the-10-second-trigger-phrases)
    - [The Power Comparison](#the-power-comparison)
  - [Related Topics](#related-topics)

---

## 1. Alibaba — Real-Time Search Ranking During Singles' Day

### 🏗️ Architecture Context

Alibaba operates the world's largest Flink deployment. During Singles' Day (11.11), the platform processes **4B+ events/sec peak**. Search ranking signals must be updated in real-time — a product that just sold 10,000 units in the last minute should rank higher for "popular" queries. Flink aggregates click-through rates (CTR), conversion rates, and purchase velocity per product, feeding real-time ranking features to the search engine.

### 📐 Pipeline Design

```
Kafka "search-clicks"     ─┐
Kafka "search-impressions" ─┤─→ Flink Job ─→ Redis (ranking features)
Kafka "purchases"          ─┘                  ↓
                                          Search Ranker reads features at query time
```

### ⚙️ Flink Job Configuration

| Setting | Value | Why |
|---|---|---|
| **Parallelism** | 2,000 (across cluster) | 4B events/sec at peak requires massive parallelism |
| **Watermark** | Bounded 3s out-of-orderness | Server-to-server events, low latency |
| **Window** | Tumbling 1-minute + Sliding 10-min/1-min | Dashboard: 1-min buckets; Ranking: 10-min moving avg |
| **State Backend** | RocksDB with incremental checkpoints | Hundreds of millions of product keys → terabytes of state |
| **Checkpoint** | Every 30s, to OSS (Alibaba's S3) | Fast recovery during Singles' Day — can't afford long catchup |
| **Sink** | Redis HSET with idempotent upsert | Search ranker reads from Redis at query time |

### 🔧 Pipeline — Step by Step

```
# =============================================
# STEP 1: Three Kafka source streams
# =============================================

Source 1: "search-impressions" (key: product_id)
  {
    "product_id": "SKU_abc",
    "query": "wireless earbuds",
    "position": 3,
    "user_id": "u789",
    "timestamp": "2026-11-11T00:15:00.123Z"
  }
  # Volume: ~2B impressions/day during Singles' Day
  # Watermark: forBoundedOutOfOrderness(3 seconds)

Source 2: "search-clicks" (key: product_id)
  {
    "product_id": "SKU_abc",
    "query": "wireless earbuds",
    "impression_id": "imp_uuid",
    "user_id": "u789",
    "timestamp": "2026-11-11T00:15:02.456Z"
  }
  # Volume: ~200M clicks/day (10% CTR)

Source 3: "purchases" (key: product_id)
  {
    "product_id": "SKU_abc",
    "order_id": "order_123",
    "quantity": 1,
    "price_cents": 4999,
    "user_id": "u789",
    "timestamp": "2026-11-11T00:15:30.789Z"
  }
  # Volume: ~50M purchases/day

# =============================================
# STEP 2: Flink — Union streams and compute per-product metrics
# =============================================

# All three streams keyed by product_id
# Flink maintains per-product state:

impressions
    .union(clicks, purchases)
    .keyBy(event -> event.getProductId())
    .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.minutes(1)))
    .aggregate(new RankingFeatureAggregator())
    .addSink(redisSink)

# RankingFeatureAggregator computes per window:
#   impressions_10min: 45,000
#   clicks_10min: 4,800
#   ctr_10min: 4800/45000 = 10.67%
#   purchases_10min: 1,200
#   conversion_rate_10min: 1200/4800 = 25%
#   purchase_velocity: 1200 units / 10 min = 120 units/min
#   revenue_10min: 1200 × $49.99 = $59,988

# WHY sliding window (10min/1min)?
#   - Updated every 1 minute → near real-time ranking signal
#   - 10-minute span → smooths out noise from bursty traffic
#   - vs tumbling 10min: Would only update once every 10 min — too slow for Singles' Day

# =============================================
# STEP 3: Sink to Redis for search ranker
# =============================================

# Redis key: ranking_features:{product_id}
# HSET ranking_features:SKU_abc 
#   ctr_10min "0.1067"
#   conversion_rate_10min "0.25"
#   purchase_velocity "120"
#   revenue_10min "5998800"
#   last_updated "2026-11-11T00:16:00Z"

# Search ranker at query time:
#   1. Retrieve candidate products from inverted index
#   2. For each candidate: HMGET ranking_features:{product_id} ctr_10min purchase_velocity
#   3. Blend real-time features with static features (reviews, price) → final score
#   4. Return ranked results

# WHY idempotent Redis upsert (not append)?
#   - On Flink checkpoint recovery, window results may be recomputed
#   - Upsert by product_id → same result regardless of replays
#   - This gives us exactly-once semantics end-to-end

# =============================================
# STEP 4: Handling late events on Singles' Day
# =============================================

# Mobile app events can arrive 10-30 seconds late
# But most server-side events arrive within 3 seconds

# Strategy:
#   Watermark: 3 seconds (covers 99.5% of events)
#   allowedLateness: 5 minutes (covers mobile stragglers)
#   sideOutputLateData: Events beyond 5 min → Kafka "late-events" topic
#   Batch reconciliation: Spark job corrects nightly

# Late event flow:
#   Event with T=00:14:50 arrives at 00:20:10 (5 min 20 sec late)
#   Window [00:05-00:15] already fired at 00:15:03 (watermark passed)
#   allowedLateness = 5 min → window kept until 00:20:00
#   But event arrives at 00:20:10 → BEYOND allowed lateness
#   → Side output → "late-events" Kafka topic → batch reconciliation
```

### 🎯 Key Interview Insights

```
Q: Why Flink instead of Spark Streaming for search ranking?
A: Three reasons:
   1. TRUE EVENT-AT-A-TIME: Flink processes each event immediately (~ms latency).
      Spark micro-batch: minimum 100ms batch interval. During Singles' Day peak,
      100ms of events = 400K events per micro-batch → memory pressure.
   2. EVENT TIME with watermarks: Mobile clicks arrive late. Flink handles this
      natively. Spark's watermark support is weaker for complex multi-source joins.
   3. STATE SIZE: Hundreds of millions of products × per-product state = terabytes.
      Flink's RocksDB backend handles this. Spark keeps state in memory → OOM risk.

Q: How do you handle 4B events/sec?
A: Scale-out architecture:
   - 2,000+ task slots across hundreds of TaskManagers
   - Source parallelism = Kafka partitions (2,000 partitions for high-volume topics)
   - Key-based partitioning distributes state evenly across slots
   - Operator chaining: source → map → filter chained in same thread (no network)
   - Only keyBy triggers network shuffle
   - Incremental checkpoints: Only changed RocksDB SST files → 30s checkpoint for TB state
```

---

## 2. Uber — Real-Time Surge Pricing Engine

### 🏗️ Architecture Context

Uber's surge pricing must balance rider demand against driver supply in real-time, per geographic area. Too little surge → riders can't find rides. Too much → riders leave the platform. The system processes millions of driver GPS pings and ride requests per second, computing supply-demand ratios per H3 geo cell every 30 seconds.

### 📐 Pipeline Design

```
Kafka "driver-locations"  ─┐
                           ├─→ Flink Job ─→ Kafka "surge-signals" ─→ Pricing Service
Kafka "ride-requests"     ─┘
```

### ⚙️ Flink Job Configuration

| Setting | Value | Why |
|---|---|---|
| **Parallelism** | 500 | High throughput for location pings but moderate computation |
| **Watermark** | Bounded 5s out-of-orderness | Driver GPS pings from mobile can lag slightly |
| **Window** | Tumbling 30-second | Surge updates every 30s → smooth pricing changes |
| **State Backend** | HashMap (in-memory) | State per geo cell is small (count of drivers/riders per cell) |
| **Checkpoint** | Every 60s to S3 | Can afford brief surge recalculation gap on recovery |
| **Sink** | Kafka "surge-signals" | Pricing service consumes and applies to next ride request |

### 🔧 Pipeline — Step by Step

```
# =============================================
# STEP 1: Two source streams
# =============================================

Source 1: "driver-locations" (key: driver_id)
  {
    "driver_id": "d789",
    "lat": 37.7749, "lng": -122.4194,
    "h3_cell": "8928308280fffff",     // H3 resolution 9 (~174m hex)
    "status": "available",             // available | on_trip | offline
    "timestamp": "2026-04-25T18:30:04Z"
  }
  # Volume: 5M active drivers × 1 ping/4 sec = 1.25M events/sec
  # Producer: acks=1, lz4 compression (ephemeral data)

Source 2: "ride-requests" (key: rider_id)
  {
    "rider_id": "r456",
    "pickup_lat": 37.7750, "pickup_lng": -122.4180,
    "h3_cell": "8928308280fffff",
    "timestamp": "2026-04-25T18:30:05Z"
  }
  # Volume: ~50K ride requests/sec at peak

# =============================================
# STEP 2: Flink — Re-key by geo cell and compute supply/demand
# =============================================

# Step 2a: Extract available drivers per geo cell
DataStream<DriverPing> availableDrivers = driverLocations
    .filter(ping -> "available".equals(ping.getStatus()))
    .keyBy(ping -> ping.getH3Cell())
    .window(TumblingEventTimeWindows.of(Time.seconds(30)))
    .aggregate(new CountDistinctDrivers());
    // Output: {h3_cell: "892830...", available_drivers: 12, window_end: T}

# Step 2b: Count ride requests per geo cell
DataStream<CellDemand> demand = rideRequests
    .keyBy(req -> req.getH3Cell())
    .window(TumblingEventTimeWindows.of(Time.seconds(30)))
    .aggregate(new CountRequests());
    // Output: {h3_cell: "892830...", ride_requests: 28, window_end: T}

# Step 2c: Join supply + demand on h3_cell + window
DataStream<SurgeSignal> surgeSignals = availableDrivers
    .keyBy(s -> s.getH3Cell())
    .connect(demand.keyBy(d -> d.getH3Cell()))
    .process(new SurgeCalculator());

# SurgeCalculator logic:
#   supply = available_drivers in this cell (from driver pings)
#   demand = ride_requests in this cell (from ride requests)
#   raw_ratio = demand / max(supply, 1)
#   
#   Surge multiplier = smooth(raw_ratio):
#     ratio < 1.0  → surge = 1.0× (no surge)
#     ratio 1.0-2.0 → surge = 1.0-1.5× (mild)
#     ratio 2.0-4.0 → surge = 1.5-2.5× (moderate)
#     ratio > 4.0   → surge = 2.5-3.0× (high, capped)
#   
#   Also consider neighboring cells (spatial smoothing):
#     If cell has 0 drivers but adjacent cell has 5 → moderate surge, not extreme

# =============================================
# STEP 3: Sink surge signals to Kafka
# =============================================

surgeSignals.addSink(new FlinkKafkaProducer<>("surge-signals",
    new SurgeSignalSchema(), kafkaProps,
    FlinkKafkaProducer.Semantic.AT_LEAST_ONCE));

# Output event:
{
  "h3_cell": "8928308280fffff",
  "surge_multiplier": 1.8,
  "supply": 12,
  "demand": 28,
  "window_start": "2026-04-25T18:30:00Z",
  "window_end": "2026-04-25T18:30:30Z"
}

# Pricing Service consumes → applies surge to next ride request in this cell

# =============================================
# STEP 4: Why this design works at scale
# =============================================

# H3 resolution 9: ~174m hexagons → ~300K active cells in a city like SF
# State per cell: ~200 bytes (driver count + request count + smoothed surge)
# Total state: 300K cells × 200 bytes = ~60MB → fits easily in HashMap backend
# 
# WHY HashMap (not RocksDB)?
#   State is SMALL (geo cell aggregates, not per-user)
#   HashMap: ~0.01ms per state access
#   RocksDB: ~0.1-1ms per state access (disk + serialization)
#   For surge pricing, every microsecond matters
#
# WHY tumbling 30-second window (not sliding)?
#   Tumbling: Each event counted once. Lower compute cost.
#   Sliding (30s/5s): Each event counted in 6 windows → 6× compute.
#   Surge doesn't need sub-30s granularity. Price changes every 30s = smooth UX.
#
# WHY not just count in Redis?
#   We need to JOIN two streams (supply + demand) by geo cell in event time.
#   Redis can't do event-time windowed joins.
#   Flink provides: watermarks + window alignment + exactly-once state.
```

### 🎯 Key Interview Insights

```
Q: Why Flink over Kafka Streams for surge pricing?
A: Two reasons:
   1. MULTI-SOURCE JOIN: We join driver-locations + ride-requests by geo cell.
      Kafka Streams: Both must be Kafka topics with same partition count.
      Flink: Can join any sources, repartition independently.
   2. EVENT-TIME WINDOWING: GPS pings arrive late from mobile networks.
      Flink's watermark mechanism ensures the 30-second window includes all
      pings that actually occurred in those 30 seconds, regardless of arrival delay.

Q: How do you handle a TaskManager crash during peak hour?
A: Flink restores from the last checkpoint (saved every 60s to S3):
   1. Failed task's state loaded from S3 → ~5 seconds
   2. Kafka source resets to checkpointed offset → re-reads ~60s of events
   3. Windows recomputed → surge signals resume
   4. Net gap: ~10-15 seconds of stale surge signals
   5. Riders see last known surge → slight over/under pricing for 15s
   6. Acceptable trade-off vs. implementing a hot standby (complex)
```

---

## 3. Netflix — Real-Time A/B Test Metric Computation

### 🏗️ Architecture Context

Netflix runs thousands of A/B tests simultaneously — new UI layouts, recommendation algorithms, autoplay delays, thumbnail images. Each test needs real-time metrics: play rate, completion rate, browse-to-play conversion. Engineers want to see experiment results within minutes, not wait for the nightly Spark batch. Flink computes these metrics in real-time from the viewing activity stream.

### 📐 Pipeline Design

```
Kafka "viewing-activity" ─┐
                          ├─→ Flink Job ─→ Druid/ClickHouse (OLAP store)
Kafka "ab-assignments"   ─┘                   ↓
                                         Experiment Dashboard
```

### 🔧 Pipeline — Step by Step

```
# =============================================
# STEP 1: Two source streams
# =============================================

Source 1: "viewing-activity" (key: user_id)
  {
    "user_id": "u123",
    "event_type": "PLAY_START",     // BROWSE, PLAY_START, PLAY_COMPLETE, ABANDON
    "content_id": "stranger_things_s5e1",
    "profile_id": "profile_2",
    "duration_sec": 0,
    "timestamp": "2026-04-25T20:00:00Z"
  }
  # Volume: ~1M events/sec (200B events/day across 300M users)

Source 2: "ab-assignments" (key: user_id, compacted Kafka topic)
  {
    "user_id": "u123",
    "experiments": {
      "new_ui_v3": "treatment_b",
      "autoplay_delay": "5s",
      "thumbnail_algo": "control"
    },
    "timestamp": "2026-04-25T19:00:00Z"
  }
  # Compacted topic: latest assignment per user always available
  # Flink reads this as a BROADCAST stream for enrichment

# =============================================
# STEP 2: Flink — Enrich viewing events with experiment assignments
# =============================================

# Step 2a: Broadcast experiment assignments to all operators
BroadcastStream<ABAssignment> broadcastAssignments = 
    abAssignments.broadcast(abStateDescriptor);

# Step 2b: Connect viewing events with broadcast assignments
viewingActivity
    .keyBy(event -> event.getUserId())
    .connect(broadcastAssignments)
    .process(new EnrichWithExperiments())  // Joins user's events with their assignments
    .keyBy(event -> event.getExperimentVariant())  // Re-key by experiment+variant
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new ExperimentMetricAggregator())
    .addSink(druidSink);

# EnrichWithExperiments:
#   Maintains MapState<userId, ABAssignment> from broadcast stream
#   On viewing event: lookup user's assignments → emit enriched event
#   Output:
#   {
#     "experiment": "new_ui_v3",
#     "variant": "treatment_b",
#     "event_type": "PLAY_START",
#     "user_id": "u123",
#     "content_id": "stranger_things_s5e1",
#     "timestamp": "2026-04-25T20:00:00Z"
#   }

# =============================================
# STEP 3: Aggregate metrics per experiment per 5-minute window
# =============================================

# ExperimentMetricAggregator computes per (experiment, variant, window):
#   unique_users: HyperLogLog sketch → approximate unique count
#   browse_count: Count of BROWSE events
#   play_count: Count of PLAY_START events
#   complete_count: Count of PLAY_COMPLETE events
#   abandon_count: Count of ABANDON events
#   browse_to_play_rate: play_count / browse_count
#   completion_rate: complete_count / play_count
#   avg_duration_sec: sum(duration_sec) / play_count

# Output to Druid:
{
  "experiment": "new_ui_v3",
  "variant": "treatment_b",
  "window_start": "2026-04-25T20:00:00Z",
  "window_end": "2026-04-25T20:05:00Z",
  "unique_users": 45230,
  "browse_count": 128000,
  "play_count": 89600,
  "browse_to_play_rate": 0.70,
  "completion_rate": 0.78,
  "avg_duration_sec": 2340
}

# =============================================
# STEP 4: Why this specific design
# =============================================

# WHY broadcast stream for AB assignments (not regular join)?
#   AB assignments change infrequently (once per user per experiment).
#   Regular join: Must partition both streams by user_id → massive shuffle.
#   Broadcast: Assignments sent to ALL operators → no shuffle for enrichment.
#   Each operator has a local copy of all assignments (~2GB for 300M users).
#   Trade-off: Each TaskManager holds full assignment state → more memory,
#   but no network shuffle for every viewing event → much higher throughput.

# WHY tumbling 5-minute window (not 1-minute)?
#   Statistical significance needs enough events per window.
#   1-minute window: Noisy metrics, especially for small experiments.
#   5-minute window: Smooth metrics, still "real-time" enough for dashboards.
#   Engineers check experiments every few hours, not every minute.

# WHY Druid/ClickHouse as sink (not Redis)?
#   Experiment data is ANALYTICAL — sliced by experiment, variant, time.
#   Druid/ClickHouse: OLAP store optimized for GROUP BY, time-series rollups.
#   Redis: Great for key-value lookups, but slow for "group by experiment
#   where date between X and Y" analytical queries.

# WHY HyperLogLog for unique users (not exact count)?
#   300M users across 1000+ experiments → can't keep per-user state for each.
#   HLL: ~12KB per experiment-variant → 1000 experiments × 2 variants = ~24MB total.
#   Exact set: 300M × 8 bytes × 2000 variants = 4.8TB → impossible.
#   HLL error: ±0.81% → "45,230 ± 366 unique users" → acceptable.
```

### 🎯 Key Interview Insights

```
Q: Why Flink instead of just a Spark batch job for A/B metrics?
A: Netflix migrated from Spark to Flink specifically for experiment velocity:
   - Spark batch: Nightly → engineers wait 24h for results. 
     If experiment is broken, it runs for a full day before discovery.
   - Flink stream: Results in 5 minutes → broken experiments caught immediately.
   - Also: Same Flink job handles both real-time dashboard AND feeds the 
     batch reconciliation (Flink processes bounded datasets too).

Q: How do you handle users switching experiment groups mid-stream?
A: The broadcast stream updates assignments in real-time:
   - User u123 switches from control → treatment at 20:15
   - Events before 20:15 attributed to control (event-time semantics)
   - Events after 20:15 attributed to treatment
   - Flink's event-time processing ensures correct attribution
     even if events arrive late
```

---

## 4. Stripe — Real-Time Fraud Detection Pipeline

### 🏗️ Architecture Context

Stripe must decide if a payment is fraudulent **before** the authorization response (< 100ms). The Flink pipeline maintains per-card and per-merchant behavioral profiles, detects anomalous patterns (velocity, geography, amount), and produces a risk score that feeds the authorization decision engine.

### 📐 Pipeline Design

```
Kafka "payment-attempts" ─→ Flink Job ─→ Kafka "risk-scores"
                               │                  ↓
                               │         Authorization Service
                               │         (reads risk score, decides approve/decline)
                               ↓
                          Redis (risk features cache for low-latency reads)
```

### ⚙️ Flink Job Configuration

| Setting | Value | Why |
|---|---|---|
| **Parallelism** | 200 | Balance between latency and throughput |
| **Watermark** | Monotonously increasing | Server-generated timestamps, in-order within partition |
| **State** | RocksDB | Per-card history for millions of cards → large state |
| **Checkpoint** | Every 30s, exactly-once | Financial data — can't lose fraud signals |
| **Processing guarantee** | Exactly-once | Double-counting fraud signals → false declines |

### 🔧 Pipeline — Step by Step

```
# =============================================
# STEP 1: Source — payment attempts
# =============================================

Source: "payment-attempts" (key: card_fingerprint)
  {
    "payment_id": "pi_3O7abc",
    "card_fingerprint": "fp_xyz789",
    "merchant_id": "acct_shop_456",
    "merchant_country": "US",
    "amount_cents": 49999,
    "currency": "usd",
    "card_country": "DE",
    "ip_country": "BR",
    "timestamp": "2026-04-25T14:30:00Z"
  }
  # Volume: ~10K payment attempts/sec
  # Key: card_fingerprint → all transactions for same card → same partition

# =============================================
# STEP 2: Flink — Stateful per-card risk feature computation
# =============================================

paymentAttempts
    .keyBy(payment -> payment.getCardFingerprint())
    .process(new FraudFeatureComputer())
    .addSink(kafkaRiskScoreSink);

# FraudFeatureComputer maintains per-card keyed state:
#   ValueState<CardProfile> profile:
#     - tx_count_1min: 0
#     - tx_count_1hr: 0
#     - tx_count_24hr: 0
#     - unique_merchants_1hr: Set<String>
#     - unique_countries_1hr: Set<String>
#     - avg_amount_30d: MovingAverage
#     - last_tx_timestamp: long
#
#   ListState<RecentTransaction> recentTxns:
#     - Last 100 transactions for pattern matching

# On each payment event:
processElement(PaymentAttempt payment, Context ctx, Collector<RiskScore> out) {
    CardProfile profile = profileState.value();
    if (profile == null) profile = new CardProfile();
    
    // Update features
    profile.txCount1Min++;
    profile.txCount1Hr++;
    profile.uniqueMerchants1Hr.add(payment.merchantId);
    profile.uniqueCountries1Hr.add(payment.cardCountry);
    
    // Compute risk signals
    double velocityScore = computeVelocityRisk(profile);
    double geoScore = computeGeoRisk(payment, profile);
    double amountScore = computeAmountRisk(payment, profile);
    
    // Combined risk score (0.0 = safe, 1.0 = definitely fraud)
    double riskScore = 0.4 * velocityScore + 0.3 * geoScore + 0.3 * amountScore;
    
    // Emit risk score
    out.collect(new RiskScore(payment.paymentId, riskScore, signals));
    
    // Register timer to decay 1-minute counters
    ctx.timerService().registerEventTimeTimer(
        payment.timestamp + Duration.ofMinutes(1).toMillis());
    
    profileState.update(profile);
}

# Risk signal computation:
# 
# velocityScore:
#   tx_count_1min > 5 → HIGH (0.9): card used 5+ times in 1 minute
#   tx_count_1min > 2 → MEDIUM (0.5): unusual for most cards
#   tx_count_1min == 1 → LOW (0.1): normal
#
# geoScore:  
#   card_country ≠ ip_country → HIGH (0.8): using card from different country
#   unique_countries_1hr > 3 → VERY HIGH (0.95): physically impossible
#   card_country == ip_country → LOW (0.05): normal
#
# amountScore:
#   amount > 10× avg_30d → HIGH (0.85): suspicious amount spike
#   amount > 3× avg_30d → MEDIUM (0.4): unusual but possible
#   amount <= avg_30d → LOW (0.05): normal

# =============================================
# STEP 3: Timer-based state cleanup
# =============================================

# onTimer callback: Decay time-windowed counters
onTimer(long timestamp, ...) {
    CardProfile profile = profileState.value();
    
    // Decrement 1-minute counter (this timer fires 1 min after the tx)
    profile.txCount1Min = max(0, profile.txCount1Min - 1);
    
    // Clean up hourly sets if no activity for 2 hours
    if (timestamp - profile.lastTxTimestamp > 2_HOURS) {
        profile.uniqueMerchants1Hr.clear();
        profile.uniqueCountries1Hr.clear();
    }
    
    profileState.update(profile);
}

# WHY timer-based cleanup instead of windows?
#   Windows are rigid: "count per fixed 1-minute bucket"
#   Timer-based: "count in the LAST 1 minute from THIS event"
#   Each card has its own sliding context → more accurate fraud detection
#   Example: 3 txns at 14:30:58, 14:31:01, 14:31:02 → all within 4 seconds
#     Window [14:30-14:31] sees 1 txn. Window [14:31-14:32] sees 2 txns.
#     Timer-based: Sees 3 txns in 4 seconds → correctly flags as suspicious!

# =============================================
# STEP 4: CEP — Complex Event Processing for pattern detection
# =============================================

# Beyond simple thresholds: detect SEQUENCES of suspicious events

Pattern<PaymentAttempt, ?> accountTakeoverPattern = Pattern
    .<PaymentAttempt>begin("failed_attempts")
    .where(event -> event.getDeclined())
    .times(3)                              // 3 failed attempts
    .within(Time.minutes(5))               // within 5 minutes
    .next("success")
    .where(event -> !event.getDeclined())  // followed by a success
    .where(event -> event.getAmountCents() > 50000);  // for a large amount

# This catches: stolen card → try small amounts to verify → big purchase
# Can't be done with simple counters — needs SEQUENCE detection.

# =============================================
# STEP 5: Latency considerations
# =============================================

# Authorization decision must happen in < 100ms total:
#   Kafka produce: ~5ms
#   Flink processing: ~2-5ms (stateful lookup + computation)
#   Kafka consume (risk score): ~5ms
#   Total Flink path: ~15ms
#   Remaining for auth service logic: 85ms
#
# WHY Flink instead of in-process rules engine?
#   1. SHARED STATE: Multiple API servers need the same per-card profile.
#      In-process: Each server has partial view. Card used on server A,
#      then server B doesn't know about it.
#   2. EXACTLY-ONCE: Flink checkpoints ensure consistent state on failure.
#      In-process: Server crash loses all in-memory state.
#   3. REPLAY: New fraud rules can be tested against historical data
#      by replaying Kafka topic through updated Flink job.
```

### 🎯 Key Interview Insights

```
Q: How do you meet the <100ms latency requirement?
A: Three optimizations:
   1. KeyBy card_fingerprint: State for one card always on same TaskManager → no network
   2. HashMap state backend for hot path, RocksDB for cold state
   3. Async I/O: Risk score written to Kafka async, not blocking processing
   
   "I'd also pre-compute risk features in Flink and cache in Redis,
    so the auth service can do a sub-ms Redis GET instead of waiting
    for Flink processing on the critical path."

Q: What happens if Flink is down?
A: Fallback strategy:
   1. Short outage (<5 min): Auth service uses last known risk features from Redis
   2. Longer outage: Fall back to simpler rule-based engine (no ML features)
   3. Flink recovers from checkpoint → processes backlog → features catch up
   Never block payments because the fraud system is down.
```

---

## 5. Pinterest — Real-Time Ad Click Aggregation & Billing

### 🏗️ Architecture Context

Pinterest's ad platform must count clicks per campaign in real-time for: (1) the advertiser dashboard showing spend, (2) billing (charge per click), and (3) budget enforcement (stop showing ads when budget is exhausted). Click fraud filtering must happen before counting. The Flink pipeline deduplicates clicks, filters fraud, aggregates per campaign, and updates budget trackers.

### 🔧 Pipeline — Step by Step

```
# =============================================
# FULL PIPELINE: Click → Dedup → Fraud Filter → Aggregate → Bill
# =============================================

Kafka "raw-clicks"
    │
    ▼
[Flink: Deduplication]
    │  KeyBy: click_id
    │  State: ValueState<Boolean> seen (TTL: 1 hour)
    │  Logic: If seen → drop. Else → mark seen, emit.
    │  WHY: Users double-click, mobile retries, network duplicates
    │
    ▼
[Flink: Fraud Filter]
    │  KeyBy: user_id
    │  State: per-user click history (last 10 min)
    │  Logic: 
    │    If same user clicked same ad > 3 times in 10 min → fraud, drop
    │    If click came < 1 second after impression → bot, drop
    │    If user clicked > 50 different ads in 1 minute → bot, drop
    │  Side output: Fraudulent clicks → Kafka "fraud-clicks" for investigation
    │
    ▼
[Flink: Campaign Aggregation]
    │  KeyBy: campaign_id
    │  Window: Tumbling 1-minute event-time windows
    │  Aggregate: count clicks, sum cost, count unique users (HLL)
    │  Watermark: 5-second bounded out-of-orderness
    │  allowedLateness: 2 minutes
    │  
    │  Output per window:
    │  {
    │    "campaign_id": "camp_789",
    │    "window_start": "2026-04-25T14:30:00Z",
    │    "valid_clicks": 1247,
    │    "total_cost_cents": 186200,
    │    "unique_clickers": 1189,
    │    "avg_cpc_cents": 149
    │  }
    │
    ├─→ Redis: HINCRBY campaign:{id}:spend:2026-04-25 total_clicks 1247
    │          HINCRBY campaign:{id}:spend:2026-04-25 total_cost 186200
    │          (Dashboard reads this for real-time spend tracking)
    │
    ├─→ Kafka "billable-clicks": Individual click events for billing system
    │
    └─→ [Flink: Budget Enforcer]
           KeyBy: campaign_id
           State: ValueState<Long> cumulativeSpend
           Logic:
             cumulativeSpend += windowCost
             if cumulativeSpend >= campaign.dailyBudget:
               emit PAUSE_CAMPAIGN signal → Kafka "campaign-controls"
               Ad serving system stops showing this campaign's ads

# =============================================
# WHY this multi-stage pipeline?
# =============================================

# 1. DEDUP FIRST: Remove duplicates before counting → accurate billing
#    Without dedup: advertiser charged twice for same click → trust erosion
#    ValueState with TTL = memory-efficient dedup (1 bool per click_id for 1hr)

# 2. FRAUD BEFORE AGGREGATION: Fraudulent clicks must not inflate spend
#    Side output to "fraud-clicks" → data science team investigates patterns
#    Keeps main pipeline clean

# 3. TUMBLING WINDOWS for aggregation:
#    vs. No window (process each click independently):
#      Too many Redis writes (1 write per click vs. 1 write per minute)
#    vs. Sliding window:
#      Each click counted in multiple windows → higher compute cost
#    Tumbling 1-min: Good balance of freshness vs efficiency

# 4. EXACTLY-ONCE end-to-end:
#    Checkpoint every 30s. On failure, replay from Kafka.
#    Redis sink is idempotent (HINCRBY with deterministic amounts per window).
#    If window fires twice due to replay → HINCRBY is NOT idempotent!
#    Solution: Use HSET campaign:{id}:spend:{window_key} instead of HINCRBY
#    Or: Use campaign:{id}:spend:{window_start} as key → upsert = idempotent

# 5. BUDGET ENFORCEMENT latency:
#    Window fires every 1 minute → budget checked every 1 minute
#    Campaign could overspend by ~1 minute of clicks before pausing
#    For a $1000/day budget: ~$0.70/minute → max overspend $0.70 → acceptable
#    For tighter enforcement: Use 10-second tumbling windows (higher compute cost)
```

### 🎯 Key Interview Insights

```
Q: Why not just count clicks in Redis directly?
A: Five reasons to use Flink:
   1. DEDUPLICATION: Redis can dedup with SET NX, but Flink gives event-time dedup
      (handle late-arriving duplicates correctly).
   2. FRAUD FILTERING: Multi-field rules (user history + timing + pattern) are complex.
      Flink's keyed state makes this natural. Redis Lua scripts would be unmanageable.
   3. EXACTLY-ONCE: Flink checkpoints ensure no clicks double-counted after failure.
      Raw Redis INCR: if increment succeeds but offset commit fails → reprocessing → double-count.
   4. WINDOWED AGGREGATION: Batch writes to Redis (1 per minute vs 1 per click).
      At 10K clicks/sec: 10K Redis writes/sec vs 17 writes/sec → 600× reduction.
   5. LATE EVENTS: Flink's watermarks handle mobile clicks arriving 5+ seconds late.
      Redis counter: Late click goes into wrong minute bucket.

Q: How do you handle budget enforcement with exactly-once?
A: "I'd store cumulative spend in Flink's keyed state (per campaign).
    On checkpoint: spend amount is persisted.
    On failure recovery: spend restores to checkpointed value,
    re-read clicks from Kafka, recompute → same total.
    The PAUSE_CAMPAIGN signal is also checkpointed.
    Risk: Brief double-emission of pause signal → ad system handles idempotently."
```

---

## 6. Spotify — Real-Time Royalty & Play Count Computation

### 🏗️ Architecture Context

Every song play on Spotify must be accurately counted for two critical purposes: the artist dashboard (real-time play counts visible to artists) and royalty calculation (artists paid ~$0.004 per qualified stream). A "qualified stream" means the user listened for at least 30 seconds. Flink processes the play event stream, filters by qualification rules, deduplicates (prevent artificial inflation), and aggregates per track/artist.

### 🔧 Pipeline — Step by Step

```
# =============================================
# FULL PIPELINE: Play Events → Qualified → Dedup → Aggregate
# =============================================

Source: Kafka "play-events" (key: user_id)
  {
    "user_id": "u789",
    "track_id": "track_abc",
    "artist_id": "artist_xyz",
    "duration_played_sec": 187,
    "track_duration_sec": 210,
    "context": "playlist",          // playlist | album | search | radio
    "subscription": "premium",      // premium | free
    "country": "US",
    "timestamp": "2026-04-25T14:30:00Z"
  }
  # Volume: ~500K plays/sec globally (peak during evening hours)

# =============================================
# STEP 1: Qualify streams (>30 seconds)
# =============================================

DataStream<PlayEvent> qualifiedPlays = playEvents
    .filter(play -> play.getDurationPlayedSec() >= 30);

# Spotify royalty rule: Plays under 30 seconds don't count.
# This simple filter removes ~25% of events (skips, previews).
# Remaining: ~375K plays/sec

# =============================================
# STEP 2: Deduplicate (prevent artificial inflation)
# =============================================

DataStream<PlayEvent> dedupedPlays = qualifiedPlays
    .keyBy(play -> play.getUserId() + ":" + play.getTrackId())
    .process(new StreamingDedup(Duration.ofMinutes(30)));

# Dedup logic:
#   Key: user_id + track_id
#   State: ValueState<Long> lastPlayTimestamp
#   Rule: Same user playing same track within 30 minutes → count only once
#   This prevents: Bot plays, repeat-to-inflate, accidental restarts
#   
#   Timer: Clean up state 30 min after last play → prevent memory leak
#   State size: ~200M active user-track pairs × 8 bytes = ~1.6GB → HashMap OK

# =============================================
# STEP 3: Aggregate — Two output paths
# =============================================

# Path A: Real-time per-track counts (for artist dashboard)
dedupedPlays
    .keyBy(play -> play.getTrackId())
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new TrackPlayCounter())
    .addSink(new RedisSink<>());  // HINCRBY track_stats:{track_id} plays_today {count}

# Path B: Per-artist royalty aggregation (for payments)
dedupedPlays
    .keyBy(play -> play.getArtistId())
    .window(TumblingEventTimeWindows.of(Time.hours(1)))
    .aggregate(new RoyaltyAggregator())
    .addSink(new KafkaSink<>("royalty-events"));

# RoyaltyAggregator output:
{
  "artist_id": "artist_xyz",
  "window": "2026-04-25T14:00-15:00",
  "total_qualified_plays": 45230,
  "premium_plays": 38100,
  "free_plays": 7130,
  "estimated_royalty_cents": 18092,   // ~$0.004 per play
  "top_tracks": [
    {"track_id": "track_abc", "plays": 12400},
    {"track_id": "track_def", "plays": 8900}
  ],
  "top_countries": {"US": 18000, "UK": 8500, "DE": 6200}
}

# =============================================
# WHY Flink instead of Kafka Streams here?
# =============================================

# Spotify actually uses both:
#   Kafka Streams: Simple per-track counting (low complexity)
#   Flink: Complex dedup + royalty calculation (needs large state, CEP rules)
#
# For royalty specifically:
#   1. Dedup state: 200M user-track pairs → manageable in Flink (RocksDB if needed)
#      Kafka Streams: Same state, but harder to manage RocksDB config
#   2. CEP for fraud: "Same song played 100× by same user in 1hr" → alert
#      Flink CEP library makes pattern detection natural
#   3. Multi-output: Same pipeline feeds dashboard AND royalty AND fraud
#      Flink side outputs make branching pipelines elegant
```

---

## 7. DoorDash — Real-Time Delivery ETA & SLA Monitoring

### 🏗️ Architecture Context

DoorDash promises customers an estimated delivery time. The Flink pipeline monitors actual delivery durations against estimates in real-time, detects SLA breaches, triggers compensations (refunds/credits), and feeds the ML model that predicts future ETAs.

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Order Events → Session Window → SLA Check → Alerts
# =============================================

Source: Kafka "order-events" (key: order_id)
  Events in order lifecycle:
    ORDER_PLACED     → {estimated_delivery_min: 35, ...}
    DASHER_ASSIGNED  → {dasher_id: "d456", eta_min: 30, ...}
    ORDER_PICKED_UP  → {pickup_time: T, ...}
    ORDER_DELIVERED   → {delivery_time: T, ...}

# =============================================
# STEP 1: Session window per order (gap = 2 hours)
# =============================================

orderEvents
    .keyBy(event -> event.getOrderId())
    .window(EventTimeSessionWindows.withGap(Time.hours(2)))
    .process(new OrderLifecycleAnalyzer())

# WHY session window (not tumbling)?
#   Order lifecycle is VARIABLE LENGTH:
#     - Fast food: 20 min (placed → delivered)
#     - Restaurant: 60 min
#     - Cancelled order: 5 min (placed → cancelled)
#   
#   Session window: Collects ALL events for one order into a single window.
#   Window fires 2 hours after last event → all lifecycle events gathered.
#   
#   Alternative: Use process function with state (more control, more code).
#   Session window is simpler for this "collect all events for an entity" pattern.

# =============================================
# STEP 2: Compute delivery metrics per order
# =============================================

# OrderLifecycleAnalyzer output:
{
  "order_id": "order_123",
  "estimated_delivery_min": 35,
  "actual_delivery_min": 52,
  "delay_min": 17,
  "sla_breached": true,         // actual > estimated + 15 min buffer
  "phases": {
    "order_to_assignment_min": 3,
    "assignment_to_pickup_min": 15,
    "pickup_to_delivery_min": 34
  },
  "dasher_id": "d456",
  "restaurant_id": "rest_789",
  "region": "SF_downtown"
}

# =============================================
# STEP 3: SLA breach → trigger compensation
# =============================================

# If sla_breached == true:
#   → Emit to Kafka "sla-breaches"
#   → Compensation service: auto-issue $5 credit to customer
#   → Operations team: dashboard alert for repeated breaches

# =============================================
# STEP 4: Aggregate for ETA model training
# =============================================

orderMetrics
    .keyBy(metric -> metric.getRegion())
    .window(TumblingEventTimeWindows.of(Time.minutes(15)))
    .aggregate(new RegionETAStats())
    .addSink(featureStoreSink);

# Output per region per 15 min:
{
  "region": "SF_downtown",
  "window": "2026-04-25T18:00-18:15",
  "avg_delivery_min": 38,
  "p95_delivery_min": 55,
  "sla_breach_rate": 0.12,
  "avg_delay_min": 8,
  "orders_count": 340
}

# This feeds the ML model:
#   "In SF_downtown at 6pm on Friday, average delivery = 38 min"
#   Next time a customer orders → ETA = 38 min (not the default 30 min)
#   Better ETA → fewer SLA breaches → happier customers
```

---

## 8. LinkedIn — Real-Time Who-Viewed-Your-Profile & Session Analytics

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Profile Views → Session Windows → Viewer Analytics
# =============================================

Source: Kafka "profile-view-events" (key: viewer_user_id)
  {
    "viewer_id": "u123",
    "viewed_profile_id": "u789",
    "source": "search_result",      // search_result | connection_suggestion | direct
    "viewer_industry": "Software",
    "viewer_company": "Google",
    "timestamp": "2026-04-25T10:30:00Z"
  }

# =============================================
# Use Case 1: "Who Viewed Your Profile" — Real-time notification
# =============================================

profileViews
    .keyBy(event -> event.getViewedProfileId())  // Key by WHO WAS VIEWED
    .process(new ProfileViewNotifier())
    .addSink(notificationSink);

# ProfileViewNotifier:
#   State: MapState<viewerId, Long> recentViewers (dedup within 24h)
#   Logic:
#     If viewer not seen in last 24h:
#       → Emit notification: "Alice from Google viewed your profile"
#       → Update state: mark viewer as seen, register 24h cleanup timer
#     If viewer seen recently:
#       → Skip (don't spam with "viewed again" notifications)

# =============================================
# Use Case 2: Session analytics (who's browsing profiles heavily?)
# =============================================

profileViews
    .keyBy(event -> event.getViewerId())  // Key by WHO IS VIEWING
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
    .aggregate(new SessionAnalyzer())
    .addSink(analyticsStoreSink);

# Session window with 30-minute gap:
#   User browses profiles for 15 minutes → stops for 30 min → session ends
#   
#   Output per session:
#   {
#     "viewer_id": "u123",
#     "session_start": "2026-04-25T10:30:00Z",
#     "session_end": "2026-04-25T10:45:00Z",
#     "profiles_viewed": 12,
#     "industries_browsed": ["Software", "Finance"],
#     "companies_browsed": ["Google", "Meta", "Stripe"],
#     "source_breakdown": {"search": 8, "suggestion": 3, "direct": 1},
#     "is_recruiter_pattern": true  // >10 profiles in 15 min from same industry
#   }
#
# WHY session windows for this?
#   LinkedIn cares about BROWSING SESSIONS, not fixed time buckets.
#   "This recruiter viewed 50 profiles in one session" = meaningful signal
#   "50 profiles between 10:00-11:00" = less meaningful (crosses sessions)
#   
#   Session windows also naturally handle variable user behavior:
#     Quick browse: 3 profiles in 2 minutes → 1 short session
#     Deep research: 30 profiles over 2 hours → 1 long session
```

---

## 9. Airbnb — Real-Time Dynamic Pricing Engine

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Signals → Flink → Pricing Recommendations → Redis
# =============================================

# Three signal streams merged:

Source 1: Kafka "search-events" (key: listing_id)
  # User searched and SAW this listing (demand signal)
  # Volume: ~50K/sec

Source 2: Kafka "booking-events" (key: listing_id)
  # Listing was booked/cancelled (supply signal)
  # Volume: ~1K/sec

Source 3: Kafka "external-signals" (key: region)
  # Events, holidays, weather, concerts (demand drivers)
  # Volume: ~100/sec (infrequent but high impact)

# =============================================
# STEP 1: Compute demand score per listing
# =============================================

searchEvents
    .keyBy(event -> event.getListingId())
    .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(5)))
    .aggregate(new DemandScoreComputer())

# DemandScoreComputer:
#   searches_1hr: How many times was this listing shown in search
#   click_through_rate: searches that led to listing page view
#   booking_rate: views that led to booking inquiry
#   demand_score = normalize(searches × CTR × booking_rate)

# =============================================
# STEP 2: Join demand with external signals (broadcast)
# =============================================

demandScores
    .keyBy(d -> d.getRegion())
    .connect(externalSignals.broadcast(externalStateDesc))
    .process(new PricingRecommender())

# PricingRecommender:
#   Base price: From host's setting (stored in state, updated via Kafka CDC)
#   Demand multiplier: 1.0 (normal) to 2.5× (peak event, holiday)
#   Supply factor: Available dates vs booked dates
#   External boost: Concert nearby → +30%, rainy weather → -10%
#   
#   recommended_price = base_price × demand_multiplier × supply_factor × external_boost
#   
#   Output to Redis:
#   HSET pricing:listing_{id} 
#     recommended_price "18500"
#     demand_score "0.85"
#     external_events "Taylor_Swift_Concert_nearby"
#     updated_at "2026-04-25T14:30:00Z"

# WHY Flink for dynamic pricing (not a cron job)?
#   1. REAL-TIME: A concert just announced → prices should react in minutes, not hours
#   2. MULTI-SIGNAL JOIN: Demand + bookings + external events merged in event-time
#   3. PER-LISTING STATE: Millions of listings, each with unique demand profile
#   4. SLIDING WINDOWS: 1-hour demand score updated every 5 min → smooth pricing
#   Cron job: Recompute all listings every hour → stale prices for 59 min
```

---

## 10. Datadog — Real-Time Metrics Alerting Pipeline

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Metrics → Flink → Threshold Check → Alerts
# =============================================

Source: Kafka "metrics-ingest" (key: metric_name + tags)
  {
    "metric": "cpu.utilization",
    "host": "web-server-42",
    "service": "api-gateway",
    "value": 87.5,
    "timestamp": "2026-04-25T14:30:15Z"
  }
  # Volume: ~10M metric data points/sec (Datadog ingests billions/day)

# =============================================
# STEP 1: Broadcast alert rules (dynamic, changeable at runtime)
# =============================================

Source: Kafka "alert-rules" (compacted topic)
  {
    "rule_id": "rule_42",
    "metric": "cpu.utilization",
    "condition": "avg > 80",
    "window_minutes": 5,
    "severity": "critical",
    "notify": ["pagerduty:team-oncall", "slack:#ops-alerts"]
  }

alertRules = env.fromSource(kafkaSource("alert-rules"));
broadcastRules = alertRules.broadcast(rulesStateDescriptor);

# =============================================
# STEP 2: Evaluate metrics against rules
# =============================================

metrics
    .keyBy(m -> m.getMetric() + ":" + m.getHost())  // per metric per host
    .connect(broadcastRules)
    .process(new DynamicAlertEvaluator())

# DynamicAlertEvaluator:
#   Broadcast state: Map<ruleId, AlertRule> → all current rules
#   Keyed state per (metric, host):
#     - Circular buffer of last N data points (for avg/p99 computation)
#     - Last alert timestamp (to implement alert cooldown)
#   
#   On each metric data point:
#     1. Find matching rules (by metric name)
#     2. For each matching rule:
#        a. Compute aggregate (avg/p99/max) over rule's window
#        b. Evaluate condition (avg > 80?)
#        c. If condition met AND not in cooldown → emit alert
#        d. If condition was met but now resolved → emit recovery
#   
#   On rule update (broadcast side):
#     1. Update rules in broadcast state
#     2. New/changed rules take effect immediately (no restart!)
#     3. Deleted rules stop evaluating immediately

# =============================================
# STEP 3: Alert output
# =============================================

alerts.addSink(alertSink);  // → PagerDuty, Slack, email

# Output:
{
  "rule_id": "rule_42",
  "status": "FIRING",
  "metric": "cpu.utilization",
  "host": "web-server-42",
  "current_value": 87.5,
  "threshold": 80,
  "window_avg": 85.2,
  "triggered_at": "2026-04-25T14:30:15Z",
  "notify": ["pagerduty:team-oncall"]
}

# =============================================
# WHY Flink for alerting (not just threshold checks in the metrics store)?
# =============================================

# 1. DYNAMIC RULES: Broadcast stream allows rule changes WITHOUT restarting.
#    Users add/modify alerts via UI → Kafka → Flink picks up instantly.
#    Alternative: Hardcoded rules → redeploy for every rule change.
#
# 2. STATEFUL EVALUATION: "CPU > 80% for 5 minutes" requires keeping
#    a 5-minute window of data points PER host PER metric.
#    10M hosts × 100 metrics = 1B stateful keys → RocksDB backend essential.
#
# 3. DEDUP & COOLDOWN: Don't alert every second while CPU is high.
#    Flink state tracks "last alert time" → alert once, then suppress for 5 min.
#    Stateless system: Would fire alert on every data point → alert fatigue.
#
# 4. MULTI-CONDITION: "CPU > 80 AND memory > 90 AND error_rate > 5%"
#    Requires correlating multiple metric streams by host — natural in Flink
#    with keyBy(host) and processing multiple metrics in same operator.
```

---

## 11. Amazon — Real-Time Inventory Reservation & Low-Stock Alerts

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Purchase Events → Flink → Inventory Update → Alerts
# =============================================

Source: Kafka "purchase-events" (key: product_id)
  {
    "order_id": "order_123",
    "product_id": "B09XS7JWHH",
    "warehouse_id": "FTW1",
    "quantity": 2,
    "event_type": "RESERVED",    // RESERVED | SHIPPED | CANCELLED | RETURNED
    "timestamp": "2026-04-25T14:30:00Z"
  }
  # Volume: ~50K purchase events/sec during peak

# =============================================
# STEP 1: Maintain real-time inventory count per product per warehouse
# =============================================

purchaseEvents
    .keyBy(e -> e.getProductId() + ":" + e.getWarehouseId())
    .process(new InventoryTracker())

# InventoryTracker keyed state:
#   ValueState<InventoryState>:
#     available_count: 150
#     reserved_count: 23
#     in_transit_count: 5
#     last_replenishment: "2026-04-24T06:00:00Z"
#
#   On RESERVED event: available -= quantity, reserved += quantity
#   On SHIPPED event: reserved -= quantity
#   On CANCELLED event: reserved -= quantity, available += quantity
#   On RETURNED event: available += quantity

# =============================================
# STEP 2: Low stock alerts
# =============================================

# If available_count < threshold → emit alert
# Threshold varies by product:
#   Fast-moving product: threshold = 50 (need buffer for high demand)
#   Slow-moving product: threshold = 5

# Output to Kafka "inventory-alerts":
{
  "product_id": "B09XS7JWHH",
  "warehouse_id": "FTW1",
  "available_count": 8,
  "threshold": 50,
  "alert_type": "LOW_STOCK",
  "estimated_stockout_hours": 4.2,  // at current burn rate
  "timestamp": "2026-04-25T14:30:00Z"
}

# Supply chain team receives alert → trigger emergency replenishment

# =============================================
# STEP 3: Burn rate computation (how fast is stock depleting?)
# =============================================

# Tumbling 15-minute window:
#   Count RESERVED events per product per window
#   burn_rate = reserved_this_window / 15 minutes
#   estimated_stockout = available_count / burn_rate
#
#   If estimated_stockout < 6 hours → CRITICAL alert
#   If estimated_stockout < 24 hours → WARNING alert

# WHY Flink instead of database triggers?
#   1. SCALE: 50K events/sec → database trigger fires 50K times → DB overloaded
#   2. COMPLEX RULES: Burn rate computation needs windowed aggregation
#   3. MULTIPLE OUTPUTS: Same pipeline feeds alerts, dashboards, and ML models
#   4. EXACTLY-ONCE: Inventory count must be accurate — no double-counting
```

---

## 12. Twitter/X — Real-Time Trending Topics Detection

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Tweets → Flink → Trending Detection → Redis
# =============================================

Source: Kafka "tweets" (key: tweet_id)
  {
    "tweet_id": "tw_123",
    "text": "Just saw #TaylorSwift concert! Amazing! #ErasTour",
    "hashtags": ["TaylorSwift", "ErasTour"],
    "user_id": "u789",
    "user_followers": 342,
    "country": "US",
    "timestamp": "2026-04-25T20:30:00Z"
  }
  # Volume: ~500K tweets/sec during major events

# =============================================
# STEP 1: Extract hashtags and compute weighted mentions
# =============================================

tweets
    .flatMap(tweet -> tweet.hashtags.stream()
        .map(tag -> new HashtagEvent(tag, tweet.userFollowers, tweet.country, tweet.timestamp)))
    .keyBy(event -> event.getHashtag())
    .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.minutes(1)))
    .aggregate(new TrendingScoreAggregator())

# TrendingScoreAggregator:
#   Not just counting mentions — VELOCITY matters:
#   
#   weighted_mentions = sum(log10(user_followers + 1) × mention_weight)
#   velocity = weighted_mentions_current_window / weighted_mentions_previous_window
#   trending_score = weighted_mentions × velocity
#   
#   Why weighted by followers?
#     Celebrity tweet about #ErasTour (10M followers) = stronger signal
#     than random user (100 followers)
#   
#   Why velocity (spike detection)?
#     "#goodmorning" gets 100K mentions every day → NOT trending (it's normal)
#     "#ErasTour" normally gets 1K/day, today 50K → velocity = 50× → TRENDING!

# =============================================
# STEP 2: Top-K globally and per country
# =============================================

# Global top-K:
trendingScores
    .windowAll(TumblingEventTimeWindows.of(Time.minutes(1)))
    .process(new TopKFunction(50))  // Top 50 globally
    .addSink(redisSink);
    // ZADD trending:global score hashtag

# Per-country top-K:
trendingScores
    .keyBy(score -> score.getCountry())
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .process(new TopKFunction(30))  // Top 30 per country
    .addSink(redisSink);
    // ZADD trending:US score hashtag

# WHY Flink over simple Redis ZINCRBY?
#   1. VELOCITY detection: "Is this hashtag spiking vs its baseline?"
#      Requires comparing current window to historical average.
#      Redis: Would need to store hourly baselines + compute ratios.
#   2. WEIGHTED SCORING: Follower-weighted mentions, not raw counts.
#      Redis ZINCRBY: Only supports uniform increment.
#   3. SLIDING WINDOW: 10-min window sliding every 1-min gives smooth trends.
#      Redis: Would need 10 separate sorted sets + ZUNIONSTORE every minute.
#   4. WATERMARKS: Event-time processing handles late tweets correctly.
```

---

## 13. Robinhood — Real-Time Portfolio Valuation & Margin Monitoring

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Market Data + Positions → Flink → Portfolio Value → Margin Check
# =============================================

Source 1: Kafka "market-prices" (key: ticker)
  {
    "ticker": "AAPL",
    "price_cents": 17850,      // $178.50
    "timestamp": "2026-04-25T14:30:00.123Z"
  }
  # Volume: ~100K price updates/sec during market hours

Source 2: Kafka "position-changes" (key: user_id, compacted topic)
  {
    "user_id": "u123",
    "positions": [
      {"ticker": "AAPL", "shares": 100},
      {"ticker": "TSLA", "shares": 50},
      {"ticker": "GOOGL", "shares": 25}
    ]
  }
  # Compacted topic: latest position per user

# =============================================
# STEP 1: Join market prices with user positions
# =============================================

# Broadcast market prices (same prices apply to ALL users)
BroadcastStream<PriceUpdate> broadcastPrices = 
    marketPrices.broadcast(priceStateDescriptor);

positionChanges
    .keyBy(pos -> pos.getUserId())
    .connect(broadcastPrices)
    .process(new PortfolioValuator())

# PortfolioValuator:
#   Broadcast state: Map<ticker, priceInCents> → latest price per ticker
#   Keyed state per user: Map<ticker, shares> → user's positions
#   
#   On price update (broadcast side):
#     Update price in broadcast state
#     For EACH user with a position in this ticker:
#       Recompute portfolio value → emit if changed significantly
#   
#   On position change (keyed side):
#     Update positions in keyed state
#     Recompute portfolio value with latest prices
#   
#   Portfolio value = sum(shares × latest_price for each ticker)
#   
#   Output:
#   {
#     "user_id": "u123",
#     "portfolio_value_cents": 3456750,   // $34,567.50
#     "cash_balance_cents": 500000,
#     "margin_used_cents": 1000000,
#     "margin_ratio": 0.35,
#     "timestamp": "2026-04-25T14:30:00Z"
#   }

# =============================================
# STEP 2: Margin call detection
# =============================================

# If margin_ratio < 0.25 (maintenance margin threshold):
#   → Emit to Kafka "margin-calls"
#   → Trading system: Liquidate positions to restore margin
#   → Notification: "Your account is below margin requirement"
#
# This MUST be real-time: Stock dropping fast → margin call within seconds
# Batch process (hourly): Stock drops 30% in 1 hour → massive loss before detection
# Flink: Detects within seconds of price crossing threshold
```

---

## 14. Walmart — Real-Time Shelf Replenishment from IoT Sensors

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: Shelf Sensors → Flink → Replenishment Alerts
# =============================================

Source: Kafka "shelf-sensor-events" (key: sensor_id)
  {
    "sensor_id": "sensor_aisle3_shelf2B",
    "store_id": "store_5200",
    "product_id": "SKU_cereal_001",
    "weight_grams": 2340,         // Current weight on shelf
    "max_weight_grams": 12000,    // Full shelf weight
    "timestamp": "2026-04-25T10:30:00Z"
  }
  # Volume: ~1M sensor readings/sec across 4,700 US stores
  # Watermark: 10-second bounded out-of-orderness (IoT network latency)

# =============================================
# Processing
# =============================================

sensorEvents
    .keyBy(event -> event.getSensorId())
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new ShelfStatusAggregator())

# ShelfStatusAggregator:
#   avg_weight: Average weight over 5-minute window (smooth noise)
#   fill_percentage: avg_weight / max_weight × 100
#   depletion_rate: (weight_start - weight_end) / 5 minutes
#   estimated_empty_min: avg_weight / depletion_rate
#
# Output:
{
  "sensor_id": "sensor_aisle3_shelf2B",
  "store_id": "store_5200",
  "product_id": "SKU_cereal_001",
  "fill_percentage": 19.5,
  "depletion_rate_g_per_min": 45,
  "estimated_empty_min": 52,
  "alert": "LOW_STOCK"
}

# If fill_percentage < 20% → alert store associate's handheld device
# If estimated_empty_min < 30 → URGENT: restock before shelf is empty
#
# WHY Flink for IoT?
#   1. SCALE: 1M readings/sec → Flink handles natively
#   2. WATERMARKS: IoT sensors have variable network latency (WiFi, cellular)
#   3. WINDOWED SMOOTHING: Single sensor reading might be noisy (someone picked up item)
#      5-minute window average → smooth, reliable fill estimate
#   4. STATE: Per-sensor state tracks depletion rate → predictive alerts
```

---

## 15. ByteDance/TikTok — Real-Time Content Moderation Pipeline

### 🔧 Pipeline — Step by Step

```
# =============================================
# PIPELINE: New Content → Flink → ML Scoring → Moderation Decisions
# =============================================

Source: Kafka "new-content" (key: content_id)
  {
    "content_id": "vid_123",
    "creator_id": "u789",
    "content_type": "video",
    "thumbnail_url": "cdn.tiktok.com/thumb/vid_123.jpg",
    "audio_transcript": "...",
    "caption": "Check out this amazing trick! #fyp",
    "hashtags": ["fyp"],
    "upload_timestamp": "2026-04-25T14:30:00Z"
  }
  # Volume: ~50K new videos/sec uploaded globally

# =============================================
# STEP 1: Parallel ML model scoring (async I/O)
# =============================================

newContent
    .keyBy(content -> content.getContentId())
    .process(new AsyncMLScorer())

# AsyncMLScorer uses Flink's AsyncDataStream:
#   For each content, calls 3 ML models IN PARALLEL (async HTTP):
#     1. Image/video classifier: nudity_score, violence_score, spam_score
#     2. Text classifier: hate_speech_score, misinformation_score
#     3. Audio classifier: copyrighted_music_score
#   
#   WHY async? Each ML call takes 50-200ms.
#   Synchronous: 50K/sec × 200ms = need 10K parallel tasks → too many
#   Async: 50K/sec with 100 concurrent requests per task slot → 500 slots enough
#   Flink's AsyncDataStream: unordered mode for maximum throughput

# =============================================
# STEP 2: Decision engine
# =============================================

# After all ML scores returned:
{
  "content_id": "vid_123",
  "scores": {
    "nudity": 0.02,
    "violence": 0.85,        // HIGH!
    "spam": 0.05,
    "hate_speech": 0.12,
    "misinformation": 0.03,
    "copyrighted_music": 0.90  // HIGH!
  }
}

# Decision rules:
#   Any score > 0.9 → AUTO_REMOVE (immediate takedown)
#   Any score > 0.7 → HUMAN_REVIEW (queue for manual review)
#   All scores < 0.3 → AUTO_APPROVE (publish immediately)
#   Otherwise → LIMITED_DISTRIBUTION (show to small audience, monitor engagement)

# =============================================
# STEP 3: Creator reputation tracking
# =============================================

decisions
    .keyBy(d -> d.getCreatorId())
    .process(new CreatorReputationTracker())

# Per-creator keyed state:
#   total_uploads_30d: 150
#   removed_count_30d: 8
#   violation_rate: 8/150 = 5.3%
#   
#   If violation_rate > 10% → flag creator for closer scrutiny
#   All future content from this creator → HUMAN_REVIEW threshold lowered to 0.5
#   
#   If violation_rate > 30% → AUTO_BAN recommendation
#
# WHY Flink for content moderation?
#   1. LATENCY: Content must be scored BEFORE going viral.
#      Batch (hourly): Harmful video gets 1M views before detection.
#      Flink: Video scored within seconds of upload, before first view.
#   2. STATEFUL REPUTATION: Per-creator state tracks patterns over time.
#      One-off scoring misses: "This creator has a PATTERN of violations."
#   3. DYNAMIC RULES: Broadcast stream of moderation rules updated in real-time.
#      New trend of harmful content? Update rules → Flink applies instantly.
#   4. ASYNC I/O: ML model calls are I/O-bound (HTTP to GPU inference servers).
#      Flink's async I/O handles thousands of concurrent ML calls efficiently.
```

---

## 16–25: Additional Real-World Applications (Summary)

### 16. Lyft — Real-Time Driver Incentive Optimization
```
Driver activity → Flink (session windows per driver) → 
Compute: hours driven, trips completed, earnings/hr, areas covered →
If driver below target earnings → emit bonus incentive in real-time →
"Complete 3 more rides by 6 PM for a $20 bonus"
WHY Flink: Session window per driver tracks driving sessions, not clock time.
```

### 17. Coinbase — Real-Time Crypto Transaction Monitoring (AML)
```
Blockchain transactions → Flink (keyed by wallet address) →
Maintain per-wallet state: volume, frequency, counterparties →
Flag: $10K+ in 24h, >50 transactions to mixing services, rapid cross-chain transfers →
Side output to compliance team + auto-freeze suspicious wallets.
WHY Flink: Complex multi-window rules per wallet, exactly-once for regulatory compliance.
```

### 18. Shopify — Real-Time Flash Sale Inventory & Analytics
```
"Add to cart" + "checkout" events → Flink →
Real-time inventory countdown (remaining stock per product) →
If stock < 10 → display "Only 5 left!" urgency message →
Tumbling 10-second windows for live sale dashboard (revenue, units sold, top products).
WHY Flink: Sub-second inventory updates, event-time processing handles burst traffic.
```

### 19. Cloudflare — Real-Time DDoS Detection
```
HTTP request logs (10M+ req/sec) → Flink →
KeyBy IP/subnet → sliding 1-min window → count requests per IP →
If req/min > 10K from single IP → auto-block at edge →
Pattern detection: distributed attack (100 IPs × 100 req/sec each from same ASN).
WHY Flink: Scale (10M events/sec), CEP for distributed attack patterns, sub-second detection.
```

### 20. Grab — Real-Time Food Order Demand Forecasting
```
Order events + weather API + event calendar → Flink →
Sliding 30-min window per restaurant → predict demand for next 30 min →
If predicted demand > restaurant capacity → disable new orders temporarily →
If demand dropping → promote restaurant with discount push notification.
WHY Flink: Multi-source join (orders + external signals), real-time ML feature computation.
```

### 21. Discord — Real-Time Message Rate Limiting & Spam Detection
```
Chat messages (100M+/day) → Flink →
KeyBy user_id → count messages per 10-second window →
If >10 msg/10sec → rate limit user → If repeated → temp ban →
Content analysis: same message in 10+ servers → spam bot → global ban.
WHY Flink: Per-user stateful rate limiting at massive scale, cross-server pattern detection.
```

### 22. Expedia — Real-Time Hotel Price Comparison
```
Price feeds from 100+ hotel providers → Flink →
KeyBy hotel_id → maintain latest price from each provider →
On any price update → recompute best deal → update Redis →
"Best price" badge updates within seconds of provider price change.
WHY Flink: Multi-provider state per hotel, event-time handling for stale provider feeds.
```

### 23. Tesla — Real-Time Fleet Telemetry & Anomaly Detection
```
Vehicle sensor data (millions of data points/sec from fleet) → Flink →
KeyBy vehicle_id → sliding 5-min window → compute sensor baselines →
If battery temp > 2σ above baseline → alert for inspection →
Aggregate fleet-wide: "5% of Model Y in Arizona showing battery anomaly" → recall signal.
WHY Flink: IoT-scale ingestion, per-vehicle state, fleet-wide aggregation.
```

### 24. Zillow — Real-Time Home Price Estimate (Zestimate) Updates
```
MLS listing updates + sale records + economic indicators → Flink →
KeyBy property_id → maintain property feature vector →
On comparable sale: update neighborhood pricing model →
Trigger Zestimate recalculation for affected properties → update search index.
WHY Flink: CDC from MLS databases, stateful pricing models, cascading recalculations.
```

### 25. PayPal — Real-Time Cross-Border Payment Risk Scoring
```
Payment events → Flink → 
KeyBy sender_id → maintain sender behavioral profile →
Cross-reference: sender country ↔ recipient country ↔ amount ↔ frequency →
Risk model: sanctions check + AML rules + ML scoring (async I/O to model serving) →
Decision: approve / hold for review / block — within 200ms.
WHY Flink: Financial exactly-once, regulatory compliance, sub-second latency requirement.
```

---

## 🏆 Cheat Sheet: Flink Design Decision Guide

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     FLINK DESIGN DECISION CHEAT SHEET                        │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  WHEN TO USE FLINK (vs alternatives):                                        │
│  ✅ Multiple input sources (not just Kafka→Kafka)                            │
│  ✅ Event-time processing with watermarks needed                             │
│  ✅ State exceeds memory (TB-scale, use RocksDB)                             │
│  ✅ Complex event processing (CEP — sequence detection)                      │
│  ✅ Exactly-once semantics for financial/billing data                        │
│  ✅ Session windows or custom triggers                                       │
│  ❌ Simple Kafka→Kafka transform → use Kafka Streams (no cluster needed)     │
│  ❌ Micro-batch latency OK + Python-first → use Spark Structured Streaming   │
│                                                                              │
│  WINDOW TYPE SELECTION:                                                      │
│  ┌─────────────────┬──────────────────────────────────────────┐              │
│  │ Window           │ Use When                                 │              │
│  ├─────────────────┼──────────────────────────────────────────┤              │
│  │ Tumbling 1-min   │ Dashboard aggregates, billing buckets    │              │
│  │ Tumbling 5-min   │ A/B test metrics, trending detection     │              │
│  │ Sliding 10m/1m   │ Moving averages, ranking features        │              │
│  │ Session (30m gap) │ User sessions, order lifecycles          │              │
│  │ Global + trigger  │ Count-based alerts (N events → fire)     │              │
│  │ No window (state) │ Per-event fraud scoring, dedup           │              │
│  └─────────────────┴──────────────────────────────────────────┘              │
│                                                                              │
│  STATE BACKEND SELECTION:                                                    │
│  ┌──────────────┬────────────────────────────────────────────┐               │
│  │ Backend       │ Use When                                   │               │
│  ├──────────────┼────────────────────────────────────────────┤               │
│  │ HashMap       │ State < few GB, latency-critical            │               │
│  │               │ (surge pricing, metrics alerting)           │               │
│  │ RocksDB       │ State > 10 GB, can tolerate ~1ms overhead   │               │
│  │               │ (fraud profiles, inventory, session state)  │               │
│  └──────────────┴────────────────────────────────────────────┘               │
│                                                                              │
│  WATERMARK STRATEGY:                                                         │
│  ┌─────────────────────────────────┬────────────────────────┐                │
│  │ Source Type                      │ Allowed Lateness       │                │
│  ├─────────────────────────────────┼────────────────────────┤                │
│  │ Server-to-server                 │ 2-5 seconds            │                │
│  │ Mobile app events                │ 10-30 seconds          │                │
│  │ IoT sensors                      │ 30-60 seconds          │                │
│  │ Cross-region replication         │ 5-15 seconds           │                │
│  │ Single Kafka partition (ordered) │ Monotonous (0 seconds) │                │
│  └─────────────────────────────────┴────────────────────────┘                │
│                                                                              │
│  CHECKPOINT CONFIGURATION:                                                   │
│  ┌─────────────────────────┬───────────────────────────────────┐             │
│  │ Use Case                 │ Checkpoint Interval               │             │
│  ├─────────────────────────┼───────────────────────────────────┤             │
│  │ Financial (billing, fraud)│ Every 30s, exactly-once           │             │
│  │ Analytics (dashboards)    │ Every 60-120s, at-least-once      │             │
│  │ IoT/metrics (high volume) │ Every 60-300s, at-least-once      │             │
│  │ Large state (> 100 GB)    │ Incremental checkpoints essential │             │
│  └─────────────────────────┴───────────────────────────────────┘             │
│                                                                              │
│  SINK STRATEGY FOR EXACTLY-ONCE:                                             │
│  ┌─────────────────────┬──────────────────────────────────────┐              │
│  │ Sink Type             │ Strategy                             │              │
│  ├─────────────────────┼──────────────────────────────────────┤              │
│  │ Kafka                 │ Transactional producer (2PC)          │              │
│  │ Redis                 │ Idempotent upsert (HSET, not INCR)   │              │
│  │ Database (PG/MySQL)   │ Upsert with ON CONFLICT DO UPDATE    │              │
│  │ Elasticsearch         │ Document _id = deterministic key      │              │
│  │ S3/HDFS               │ Commit files on checkpoint only       │              │
│  └─────────────────────┴──────────────────────────────────────┘              │
│                                                                              │
│  PERFORMANCE NUMBERS:                                                        │
│  ┌──────────────────────────────────────────────────┐                        │
│  │ Single task slot: 100K-10M events/sec (op-dependent)│                      │
│  │ End-to-end latency: 10-500ms (Kafka→Flink→Sink)    │                      │
│  │ Checkpoint (1 GB state): 2-5 seconds                │                      │
│  │ Checkpoint (100 GB, incremental): 10-30 seconds     │                      │
│  │ Recovery from checkpoint: 5-30 seconds              │                      │
│  │ Alibaba peak: 4B+ events/sec across cluster         │                      │
│  └──────────────────────────────────────────────────┘                        │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary: "When Would You Use Flink?"

### The 30-Second Answer

> *"I'd use Flink when the problem requires **real-time stateful stream processing** with **event-time semantics**. Specifically: when events can arrive late (mobile, IoT), when I need large state (per-user or per-entity profiles), when the pipeline joins multiple streams, or when I need exactly-once guarantees for billing or financial accuracy. If it's a simple Kafka-to-Kafka transformation, Kafka Streams is simpler. If micro-batch latency is acceptable, Spark works. But for complex event processing, session windows, fraud detection, or real-time aggregation at scale — Flink is the right tool."*

### The 10-Second Trigger Phrases

| When Interviewer Says... | Respond With Flink Because... |
|---|---|
| "Real-time click aggregation" | Event-time windows + exactly-once + late events |
| "Fraud detection" | Stateful per-entity processing + CEP patterns |
| "Dynamic pricing / surge" | Multi-stream join + sliding windows + low latency |
| "Real-time dashboard" | Tumbling windows + watermarks + idempotent sink |
| "IoT sensor processing" | High volume + watermarks + windowed smoothing |
| "Session analytics" | Session windows (gap-based) |
| "Content moderation" | Async I/O to ML models + per-creator state |
| "SLA monitoring" | Session windows + timer-based alerts |
| "Real-time ETL" | Multi-source → Flink → multi-sink with exactly-once |
| "A/B test metrics" | Broadcast join + windowed aggregation |

### The Power Comparison

```
"For this problem, I'm choosing Flink over:
 - Kafka Streams: Because we have multiple input sources and state > memory.
 - Spark Streaming: Because we need true event-at-a-time latency, not micro-batch.
 - Custom application: Because Flink gives us checkpointing, watermarks,
   and exactly-once for free — building this ourselves would be months of work."
```

---

## Related Topics

- **Topic 39: Kafka Deep Dive** — Kafka as Flink's primary source/sink
- **Topic 39b: Kafka Real-World Applications** — Companion Kafka patterns
- **Topic 40: Redis Deep Dive** — Redis as Flink's real-time sink
- **Topic 48: Flink & Stream Processing Deep Dive** — Core Flink concepts (watermarks, state, checkpointing)
- **Topic 23: Batch vs Stream Processing** — Lambda/Kappa architecture
- **Topic 33: Reconciliation** — Flink stream vs Spark batch reconciliation
- **Topic 41: Trending & Top-K Computation** — Flink for real-time trending

---

*This document is the companion to Topic 48 (Flink & Stream Processing Deep Dive), providing complete real-world examples with full pipeline implementations.*
