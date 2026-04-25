# 📨 Kafka Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how Kafka is used in production at companies like LinkedIn, Uber, Netflix, Stripe, Airbnb, and more. Every topic design, partition key choice, consumer group pattern, and configuration decision is explained with the **WHY** behind it.

---

## 📋 Table of Contents

- [1. LinkedIn — Activity Feed & Event Sourcing](#1-linkedin--activity-feed--event-sourcing)
- [2. Uber — Real-Time Ride Event Pipeline](#2-uber--real-time-ride-event-pipeline)
- [3. Netflix — Viewing Activity & Recommendations Pipeline](#3-netflix--viewing-activity--recommendations-pipeline)
- [4. Stripe — Payment Event Processing & Webhooks](#4-stripe--payment-event-processing--webhooks)
- [5. Twitter — Tweet Ingestion & Fan-Out Trigger](#5-twitter--tweet-ingestion--fan-out-trigger)
- [6. Airbnb — Search Indexing Pipeline (DB → Elasticsearch)](#6-airbnb--search-indexing-pipeline-db--elasticsearch)
- [7. DoorDash — Order State Machine & Event-Driven Workflow](#7-doordash--order-state-machine--event-driven-workflow)
- [8. Spotify — Play Count Aggregation & Royalty Calculation](#8-spotify--play-count-aggregation--royalty-calculation)
- [9. Amazon — Click & Impression Tracking for Ads](#9-amazon--click--impression-tracking-for-ads)
- [10. Slack — Message Delivery & Notification Fanout](#10-slack--message-delivery--notification-fanout)
- [11. Walmart — Inventory Sync Across Warehouses](#11-walmart--inventory-sync-across-warehouses)
- [12. Datadog — Log Ingestion Pipeline (Millions/Sec)](#12-datadog--log-ingestion-pipeline-millionssec)
- [13. WhatsApp — Message Queue with Delivery Guarantees](#13-whatsapp--message-queue-with-delivery-guarantees)
- [14. Pinterest — Image Processing Pipeline](#14-pinterest--image-processing-pipeline)
- [15. Robinhood — Stock Price Streaming](#15-robinhood--stock-price-streaming)
- [16–25: Additional Real-World Applications (Summary)](#1625-additional-real-world-applications-summary)
- [🏆 Cheat Sheet: Kafka Topic & Partition Key Design Guide](#-cheat-sheet-kafka-topic--partition-key-design-guide)
- [🎯 Interview Summary: "When Would You Use Kafka?"](#-interview-summary-when-would-you-use-kafka)

---

## 1. LinkedIn — Activity Feed & Event Sourcing

### 🏗️ Architecture Context

LinkedIn was where Kafka was born. Every user action — profile view, connection request, post, like, share, message — is published as an event to Kafka. Dozens of downstream consumers process these events independently: feed generation, notification service, analytics, search indexing, and recommendations.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `user-activity` | `user_id` | 256 | 7 days | All user actions (posts, likes, shares, views) |
| `connections` | `user_id` | 64 | 30 days | Connection requests, accepts, removes |
| `notifications` | `recipient_user_id` | 128 | 3 days | Notification events to be delivered |
| `feed-updates` | `target_user_id` | 256 | 24 hours | Pre-computed feed entries per user |
| `profile-updates` | `user_id` | 32 | 14 days | Profile edits for search index + recommendations |

### 💡 Why This Partition Key Design?

- **`user_id` for user-activity**: All events for a single user land in the same partition → **ordering guaranteed per user**. Consumer can build a complete user activity timeline in-order.
- **`recipient_user_id` for notifications**: All notifications for one user are ordered → no race conditions (read notification before it's created).
- **`target_user_id` for feed-updates**: Each feed consumer owns a subset of users → easy horizontal scaling.

### 🔧 Producer & Consumer — Step by Step

```
# =============================================
# STEP 1: User "alice" likes a post
# =============================================

Producer publishes to topic: user-activity
  Key: "user_alice_123"
  Value: {
    "event_type": "LIKE",
    "user_id": "alice_123",
    "target_id": "post_789",
    "target_owner_id": "bob_456",
    "timestamp": "2026-04-25T10:30:00Z",
    "metadata": {"source": "mobile_app", "session_id": "sess_abc"}
  }
  
  Partition assignment: hash("user_alice_123") % 256 = partition 42
  All of Alice's events go to partition 42 → in-order processing

# =============================================
# STEP 2: Multiple consumer groups process independently
# =============================================

Consumer Group 1: "feed-generator"
  - 256 consumers (one per partition)
  - Reads event → pushes "post_789 liked by alice" into bob's feed
  - Writes to topic: feed-updates (key: "bob_456")

Consumer Group 2: "notification-service"
  - 64 consumers
  - Reads event → sends push notification to bob: "alice liked your post"
  - Writes to topic: notifications (key: "bob_456")

Consumer Group 3: "analytics-pipeline"
  - 32 consumers
  - Reads event → aggregates into "likes per post per hour" in data warehouse
  - Writes to HDFS / S3 via Kafka Connect

Consumer Group 4: "search-indexer"
  - 16 consumers
  - Reads event → updates engagement signals in Elasticsearch
  - post_789 now has +1 like → search ranking updated

# KEY INSIGHT: Same event, processed 4 different ways.
# Each consumer group reads independently at its own pace.
# If analytics is slow, it doesn't slow down notifications.

# =============================================
# STEP 3: Backfill / Replay scenario
# =============================================

# Bug found in analytics pipeline for the last 3 days.
# Fix deployed. Now replay:

kafka-consumer-groups.sh --reset-offsets \
  --group analytics-pipeline \
  --topic user-activity \
  --to-datetime 2026-04-22T00:00:00.000 \
  --execute

# Analytics consumers re-read 3 days of events and reprocess.
# No other consumer groups are affected!
# This is IMPOSSIBLE with RabbitMQ/SQS (messages already deleted).
```

### 🎯 Key Interview Insights

```
Q: Why Kafka and not RabbitMQ for LinkedIn's activity feed?
A: Three reasons:
   1. MULTIPLE CONSUMERS: Same event consumed by 10+ services.
      RabbitMQ: need 10 queues with copies. Kafka: 10 consumer groups, one copy.
   2. REPLAY: Can re-read events for debugging, backfill, new consumers.
      RabbitMQ: messages deleted after consumption.
   3. ORDERING: Partition key = user_id guarantees per-user ordering.
      RabbitMQ: ordering only within a single queue, no key-based routing.

Q: Why 256 partitions for user-activity?
A: We need to support 256 parallel consumers for throughput.
   Rule of thumb: partitions ≥ max expected consumers.
   More partitions = more parallelism but more overhead.
   LinkedIn's actual Kafka deployment: 100K+ partitions across cluster.
```

---

## 2. Uber — Real-Time Ride Event Pipeline

### 🏗️ Architecture Context

Every Uber ride generates hundreds of events: driver location updates (every 4 seconds), ride state changes (requested → matched → en_route → arrived → in_trip → completed), payment events, surge pricing signals, and ETA calculations. Kafka is the central nervous system connecting 4,000+ microservices.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `driver-locations` | `driver_id` | 512 | 1 hour | GPS pings every 4 seconds (high throughput!) |
| `ride-events` | `ride_id` | 256 | 7 days | Ride state machine transitions |
| `payment-events` | `ride_id` | 128 | 30 days | Charge, refund, tip events |
| `surge-pricing` | `geo_cell_id` | 64 | 1 hour | Supply/demand signals per geo area |
| `eta-requests` | `rider_id` | 128 | 1 hour | ETA calculation requests/responses |

### 💡 Why This Partition Key Design?

- **`driver_id` for locations**: All pings from one driver go to same partition → consumer can maintain an in-memory location buffer per driver, calculate speed/heading. Out-of-order pings would break ETA.
- **`ride_id` for ride-events**: A ride's lifecycle (requested → completed) must be processed in order. Partition by ride_id guarantees this.
- **`geo_cell_id` for surge**: All supply/demand signals for one area are processed together → accurate local surge calculation. Uses S2/H3 geo cell as key.

### 🔧 Producer & Consumer — Step by Step

```
# =============================================
# STEP 1: Driver location updates (HIGHEST THROUGHPUT)
# =============================================

# 5M active drivers × 1 ping/4sec = 1.25M messages/sec!
# This is Kafka's sweet spot — no other system handles this.

Producer (driver app): publishes every 4 seconds
  Topic: driver-locations
  Key: "driver_d789"
  Value: {
    "driver_id": "d789",
    "lat": 37.7749,
    "lng": -122.4194,
    "heading": 245,
    "speed_mph": 32,
    "timestamp": "2026-04-25T10:30:04Z",
    "trip_id": "ride_123"   // null if not on a trip
  }

  # Producer config for LOCATION DATA (high throughput, some loss OK):
  acks: 1              # Leader ACK only (not waiting for replicas)
  linger.ms: 50        # Batch for 50ms before sending
  batch.size: 64KB     # Large batches for throughput
  compression: lz4     # Fast compression, 4× less network
  
  # WHY these settings?
  # - Location data is ephemeral. If we lose 1 ping out of 1000, nobody notices.
  # - Throughput > durability for this use case.
  # - Contrast with payment-events where we'd use acks=all.

# =============================================
# STEP 2: Ride state machine — strict ordering
# =============================================

Producer (ride service): publishes on state transition
  Topic: ride-events
  Key: "ride_123"
  Value: {
    "ride_id": "ride_123",
    "event_type": "DRIVER_ARRIVED",
    "driver_id": "d789",
    "rider_id": "r456",
    "timestamp": "2026-04-25T10:31:00Z",
    "previous_state": "EN_ROUTE",
    "new_state": "ARRIVED",
    "location": {"lat": 37.7750, "lng": -122.4180}
  }

  # Producer config for RIDE EVENTS (strict ordering, no loss):
  acks: all                          # Wait for all replicas
  enable.idempotence: true           # Prevent duplicates on retry
  max.in.flight.requests: 1          # Strict ordering (no reordering on retry)
  
  # WHY? A ride going COMPLETED before ARRIVED would break billing.

# =============================================
# STEP 3: Consumer — Surge pricing calculator
# =============================================

Consumer Group: "surge-calculator"
  Consumes from: driver-locations + ride-events

  For each geo_cell (S2 cell level 12, ~3km²):
    supply = count(available_drivers in cell in last 5 min)
    demand = count(ride_requests in cell in last 5 min)
    surge_multiplier = max(1.0, demand / supply)
    
    Publish to: surge-pricing
    Key: "geo_cell_9q8yyk"
    Value: {"cell_id": "9q8yyk", "surge": 2.3, "supply": 5, "demand": 12}

# =============================================
# STEP 4: Consumer — Real-time trip tracking
# =============================================

Consumer Group: "trip-tracker"
  Consumes from: driver-locations
  
  For each location ping where trip_id is not null:
    1. Update rider's live map (via WebSocket push)
    2. Recalculate ETA to destination
    3. Check for detour/safety anomalies
    4. Update trip distance meter (for fare calculation)
```

### 🎯 Key Interview Insights

```
Q: Why different acks settings for locations vs rides?
A: TRADE-OFF between throughput and durability:
   - Locations: acks=1 because losing 1 GPS ping is fine (1.25M/sec throughput)
   - Rides: acks=all because losing a state transition breaks billing (100K/sec is enough)
   
   This is a GREAT interview talking point:
   "I'd configure Kafka differently per topic based on durability needs.
    High-throughput ephemeral data gets acks=1 + lz4 compression.
    Critical financial events get acks=all + idempotent producer."

Q: How does Uber handle exactly-once for payments?
A: Kafka's idempotent producer (enable.idempotence=true) + 
   transactional consumer (read_committed) + 
   idempotency key in payment service (ride_id as dedup key in DB).
   Belt AND suspenders approach.
```

---

## 3. Netflix — Viewing Activity & Recommendations Pipeline

### 🏗️ Architecture Context

Every play, pause, seek, rating, and browse action is an event. Netflix processes ~500B events/day through Kafka. These feed the recommendation engine, A/B testing platform, content quality metrics, and CDN cache warming.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `viewing-activity` | `user_id` | 512 | 30 days | Play, pause, stop, seek events |
| `user-interactions` | `user_id` | 256 | 14 days | Browse, search, rate, add-to-list |
| `content-quality` | `content_id` | 128 | 7 days | Buffering, bitrate switches, errors |
| `ab-test-events` | `user_id` | 64 | 90 days | A/B test assignments and outcomes |
| `cdn-cache-hints` | `region_id` | 32 | 1 hour | Content popularity signals for CDN pre-warming |

### 🔧 Producer & Consumer — Step by Step

```
# =============================================
# STEP 1: User starts watching a show
# =============================================

Producer (player app):
  Topic: viewing-activity
  Key: "user_u123"
  Value: {
    "event": "PLAY_START",
    "user_id": "u123",
    "content_id": "stranger_things_s5e1",
    "profile_id": "profile_2",
    "device": "smart_tv",
    "timestamp": "2026-04-25T20:00:00Z",
    "playback_position_sec": 0,
    "audio_language": "en",
    "subtitle_language": "es",
    "video_quality": "4K_HDR",
    "ab_tests": {"new_ui_v2": "treatment", "autoplay_delay": "5s"}
  }

# =============================================
# STEP 2: Periodic heartbeat every 30 seconds during viewing
# =============================================

  Topic: viewing-activity
  Key: "user_u123"
  Value: {
    "event": "HEARTBEAT",
    "user_id": "u123",
    "content_id": "stranger_things_s5e1",
    "playback_position_sec": 1830,   // 30.5 minutes in
    "buffer_health_sec": 45,
    "current_bitrate_kbps": 15000,
    "timestamp": "2026-04-25T20:30:30Z"
  }

# =============================================
# STEP 3: Multiple consumer groups process viewing data
# =============================================

Consumer Group 1: "recommendation-engine"
  - Reads viewing-activity
  - Builds user taste profile: 
    "u123 watched 92% of Stranger Things → likes sci-fi, thriller"
  - Updates ML feature store for real-time recommendations
  - If user watches > 70% → "positive signal"
  - If user stops at 10% → "negative signal"

Consumer Group 2: "content-analytics"
  - Reads viewing-activity
  - Aggregates: "Stranger Things S5E1 has 5.2M views in first 24h"
  - Completion rate: 78% watched to end
  - Drop-off analysis: 15% stopped at the 20-minute mark (boring scene?)
  - Feeds dashboards for content team

Consumer Group 3: "cdn-optimizer"
  - Reads viewing-activity  
  - Detects trending content in specific regions
  - Publishes to cdn-cache-hints: "Stranger Things S5E1 is hot in EU-west → pre-cache"
  - CDN servers consume hints and pre-fetch content from origin

Consumer Group 4: "ab-test-analyzer"
  - Reads viewing-activity + ab-test-events
  - Joins: did "new_ui_v2:treatment" group watch longer than "control"?
  - Produces experiment results with statistical significance

# =============================================
# KEY INSIGHT: Kafka as the "single source of truth" for events
# =============================================

# Netflix calls this "Keystone Pipeline":
#   - Every event enters Kafka ONCE
#   - N consumer groups each get their own view
#   - If a new team needs viewing data? → Add a new consumer group
#   - Zero changes to producers, zero impact on existing consumers
#   - This is the core power of Kafka over point-to-point messaging
```

### 🎯 Key Interview Insights

```
Q: Why not just write events directly to a database?
A: Kafka decouples producers from consumers:
   1. Producer doesn't need to know who reads the data
   2. Adding a new consumer = zero producer changes
   3. Consumer can be slow/down without affecting writes
   4. Replay capability for debugging and backfill
   
   Netflix has 100+ consumer groups on viewing-activity.
   Writing to 100 databases directly? Impossible.

Q: How does Netflix handle 500B events/day?
A: Multi-cluster Kafka deployment:
   - 3 regional clusters (US, EU, APAC)
   - MirrorMaker 2 for cross-region replication (for global analytics)
   - 10,000+ partitions per cluster
   - Custom consumer framework with auto-scaling
```

---

## 4. Stripe — Payment Event Processing & Webhooks

### 🏗️ Architecture Context

When a payment is processed, Stripe must: charge the card, update the balance, notify the merchant via webhook, update fraud models, trigger accounting entries, and send receipt emails. Each step is an event in Kafka — the financial backbone must guarantee exactly-once processing.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `payment-intents` | `payment_id` | 128 | 90 days | Payment lifecycle: created → confirmed → captured → settled |
| `webhook-deliveries` | `merchant_id` | 64 | 7 days | Webhook events to deliver to merchants |
| `fraud-signals` | `card_fingerprint` | 32 | 30 days | Fraud detection signals per card |
| `ledger-entries` | `account_id` | 64 | Infinite (compacted) | Double-entry accounting (compacted log!) |
| `payout-events` | `merchant_id` | 32 | 90 days | Merchant payouts |

### 💡 Why This Partition Key Design?

- **`payment_id` for payment-intents**: A payment's lifecycle MUST be in order (can't settle before capturing). Partition by payment_id guarantees this.
- **`merchant_id` for webhooks**: All webhooks for one merchant are delivered in order → merchant sees events chronologically.
- **`card_fingerprint` for fraud**: All transactions from the same card are processed by the same consumer → pattern detection (rapid successive charges from different merchants = stolen card).

### 🔧 Producer & Consumer — Step by Step

```
# =============================================
# STEP 1: Payment intent created
# =============================================

Producer (API server):
  Topic: payment-intents
  Key: "pi_3O7abc123"
  Value: {
    "event": "PAYMENT_INTENT_CREATED",
    "payment_id": "pi_3O7abc123",
    "merchant_id": "acct_stripe_shop",
    "amount_cents": 4999,
    "currency": "usd",
    "card_fingerprint": "fp_abc789",
    "idempotency_key": "order_12345_charge",   // CRITICAL for exactly-once
    "timestamp": "2026-04-25T10:30:00Z"
  }

  # Producer config for FINANCIAL DATA:
  acks: all
  enable.idempotence: true
  retries: Integer.MAX_VALUE        # Retry forever (bounded by timeout)
  delivery.timeout.ms: 120000      # 2 min timeout
  max.in.flight.requests: 5        # With idempotence, safe for ordering
  
  # WHY? Losing a payment event = lost money. Period.
  # acks=all + idempotent producer = exactly-once publish guarantee.

# =============================================
# STEP 2: Consumer — Webhook delivery service
# =============================================

Consumer Group: "webhook-deliverer"
  Consumes from: payment-intents
  
  For each event:
    1. Look up merchant's webhook URL from config DB
    2. POST event to merchant's endpoint:
       POST https://shop.com/webhooks/stripe
       Body: {"type": "payment_intent.succeeded", "data": {...}}
    3. If 2xx → commit offset → done
    4. If 5xx or timeout → DON'T commit offset → retry with exponential backoff
       Retry schedule: 5s, 30s, 5min, 1hr, 6hr, 24hr, 3days
    5. After all retries exhausted → publish to dead-letter topic

  # EXACTLY-ONCE guarantee:
  # Merchant webhook handler must be IDEMPOTENT (use idempotency_key).
  # We might deliver the same webhook twice (network retry), 
  # but merchant deduplicates using idempotency_key.

# =============================================
# STEP 3: Consumer — Fraud detection
# =============================================

Consumer Group: "fraud-detector"
  Consumes from: payment-intents
  
  Maintains in-memory state per card_fingerprint (partition key):
    - Transaction count in last 1 minute
    - Transaction count in last 1 hour  
    - Unique merchants in last 1 hour
    - Average transaction amount (last 30 days)
    
  Rules:
    if tx_count_1min > 5 → FRAUD (rapid successive charges)
    if unique_merchants_1hr > 10 → FRAUD (card used everywhere)
    if amount > 10 × avg_amount → SUSPICIOUS (unusual amount)
    
  If fraud detected:
    Publish to: fraud-signals
    Trigger: block card, reverse charge, alert merchant

# =============================================
# STEP 4: Consumer — Ledger (compacted topic!)
# =============================================

Consumer Group: "accounting-ledger"
  Consumes from: ledger-entries (LOG COMPACTED topic)
  
  Topic config:
    cleanup.policy: compact     # Keep LATEST value per key forever
    min.cleanable.dirty.ratio: 0.5
    
  Key: "acct_stripe_shop:balance:usd"
  Value: {"balance_cents": 125499, "pending_cents": 4999, "updated_at": "..."}
  
  # Log compaction means:
  # - Old values for the same key are deleted
  # - LATEST balance is always available
  # - Acts like a KEY-VALUE STORE with changelog
  # - If consumer restarts, it reads compacted log to rebuild state
  
  # This is Kafka as a DATABASE, not just a queue!
```

### 🎯 Key Interview Insights

```
Q: How do you guarantee exactly-once payment processing?
A: Three layers:
   1. Idempotent producer (enable.idempotence=true): no duplicate publishes
   2. Transactional consumer (isolation.level=read_committed): atomic read-process-write
   3. Application-level idempotency (idempotency_key in DB): dedup at business logic

   "I'd use Kafka's transactional API for internal services,
    and idempotency keys for external webhook delivery."

Q: Why log compaction for the ledger?
A: It gives us BOTH an event log AND a state store:
   - Full history: replay all events to rebuild any point in time
   - Compacted view: latest balance per account without scanning history
   - This is the basis of Kafka Streams' KTable abstraction.
```

---

## 5. Twitter — Tweet Ingestion & Fan-Out Trigger

### 🏗️ Architecture Context

When a user posts a tweet, it's first written to Kafka. Multiple downstream consumers handle: storing in DB, indexing for search, fan-out to followers' timelines (Redis), sending notifications, updating trending topics, and content moderation.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `tweets` | `tweet_id` | 256 | 7 days | Raw tweet events (the source of truth) |
| `fanout-tasks` | `author_id` | 128 | 24 hours | Fan-out work items per author |
| `search-index-updates` | `tweet_id` | 64 | 24 hours | Tweets to be indexed in search |
| `trending-signals` | `hashtag` | 32 | 1 hour | Hashtag counts for trending detection |
| `moderation-queue` | `tweet_id` | 16 | 72 hours | Tweets flagged for content moderation |

### 🔧 Producer & Consumer — Step by Step

```
# =============================================
# STEP 1: User posts a tweet
# =============================================

API Server → Kafka:
  Topic: tweets
  Key: "tweet_1792345678"
  Value: {
    "tweet_id": "1792345678",
    "author_id": "user_alice",
    "text": "Kafka is the backbone of real-time systems! #tech #kafka",
    "hashtags": ["tech", "kafka"],
    "media_urls": [],
    "created_at": "2026-04-25T10:30:00Z",
    "follower_count": 45000
  }

# WHY write to Kafka first (not DB)?
# 1. Kafka is faster for writes (append-only log) → lower latency for user
# 2. DB write can be async (a consumer writes to DB)
# 3. If DB is temporarily down, tweets are buffered in Kafka
# 4. Multiple consumers process the tweet in parallel

# =============================================
# STEP 2: Consumer — DB writer
# =============================================

Consumer Group: "tweet-db-writer"
  - Reads from: tweets
  - Writes to: MySQL/Manhattan (persistent store)
  - acks=all on producer, auto.offset.reset=earliest
  - If DB write fails → don't commit offset → retry

# =============================================
# STEP 3: Consumer — Fan-out orchestrator
# =============================================

Consumer Group: "fanout-orchestrator"
  - Reads from: tweets
  - For regular users (< 500K followers):
    → Publish fan-out task to: fanout-tasks
    → Fan-out workers push tweet_id to each follower's Redis timeline
  - For celebrities (> 500K followers):
    → Skip fan-out (pull model instead)
    → Flag tweet as "celebrity tweet" in a lookup

Consumer Group: "fanout-workers"
  - Reads from: fanout-tasks
  - Partition key = author_id → all of one author's followers handled by one worker
  - For each follower of the author:
    → LPUSH timeline:{follower_id} tweet_id (Redis)
    → LTRIM timeline:{follower_id} 0 799 (bound timeline size)

# =============================================
# STEP 4: Consumer — Search indexer
# =============================================

Consumer Group: "search-indexer"
  - Reads from: tweets
  - Tokenizes tweet text
  - Updates inverted index (Earlybird / Elasticsearch)
  - Tweet is searchable within 5-10 seconds of posting!

# =============================================
# STEP 5: Consumer — Trending detector
# =============================================

Consumer Group: "trending-detector"
  - Reads from: tweets
  - Extracts hashtags from each tweet
  - Publishes to: trending-signals
  - Downstream Kafka Streams app:
    Windowed aggregation: count per hashtag per 5-minute window
    Compare to baseline → if velocity > 5× → TRENDING!
```

### 🎯 Key Interview Insights

```
Q: Why Kafka before the database?
A: "Write-ahead to Kafka" pattern:
   1. User gets fast acknowledgment (Kafka write is fast)
   2. DB write happens async (consumer writes to DB)
   3. If DB is down → Kafka buffers events → no data loss
   4. Multiple consumers process simultaneously vs. sequential DB writes
   
   This is the EVENT SOURCING pattern:
   Kafka is the source of truth. DB is a materialized view.

Q: Why partition fanout-tasks by author_id?
A: All followers of one author are processed by one worker.
   This prevents race conditions:
   - Author posts 3 tweets rapidly
   - All 3 go to same partition → same worker → delivered in order
   - If split across workers, follower might see tweet #3 before #1
```

---

## 6. Airbnb — Search Indexing Pipeline (DB → Elasticsearch)

### 🏗️ Architecture Context

Airbnb's search uses Elasticsearch. When a host updates a listing (price, photos, availability), the change must propagate from MySQL (source of truth) to Elasticsearch (search index). Kafka + CDC (Change Data Capture) bridges this gap.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `listing-changes` | `listing_id` | 128 | 7 days | CDC events from MySQL binlog |
| `listing-enriched` | `listing_id` | 64 | 3 days | Enriched listing data ready for indexing |
| `search-index-commands` | `listing_id` | 64 | 24 hours | Index/update/delete commands for ES |

### 🔧 Pipeline — Step by Step

```
# =============================================
# THE CDC (Change Data Capture) PIPELINE
# =============================================

# Step 1: Host updates price in MySQL
MySQL: UPDATE listings SET price = 150 WHERE listing_id = 'list_abc'

# Step 2: Debezium (CDC connector) reads MySQL binlog
Debezium → Kafka:
  Topic: listing-changes
  Key: "list_abc"
  Value: {
    "before": {"listing_id": "list_abc", "price": 200, "title": "Cozy Studio"},
    "after": {"listing_id": "list_abc", "price": 150, "title": "Cozy Studio"},
    "op": "u",     // u=update, c=create, d=delete
    "ts_ms": 1714060200000,
    "source": {"table": "listings", "db": "airbnb_main"}
  }

# Step 3: Enrichment consumer adds computed fields
Consumer Group: "listing-enricher"
  Reads from: listing-changes
  For each change:
    1. Fetch host details (join with hosts table)
    2. Compute average review score
    3. Geocode address → lat/lng
    4. Compute calendar availability for next 90 days
    5. Publish enriched document to: listing-enriched

# Step 4: ES indexer consumer updates search index
Consumer Group: "es-indexer"
  Reads from: listing-enriched
  Batch 500 documents → ES _bulk API:
    POST /_bulk
    {"index": {"_id": "list_abc"}}
    {"title": "Cozy Studio", "price": 150, "location": {...}, ...}

  # BATCHING IS KEY:
  # Individual ES writes: 1ms each, 1000 QPS = 1 second
  # Batch 500 writes: 50ms for batch, 1000 QPS = 0.1 seconds
  # 10× throughput improvement from batching!

# Step 5: Verification (optional)
Consumer Group: "index-verifier"
  Reads from: listing-changes
  After 1-minute delay: queries ES for the listing
  Compares ES document with source event
  If mismatch → alert + re-index

# =============================================
# WHY KAFKA IN THE MIDDLE (not direct MySQL → ES)?
# =============================================

# Without Kafka:
#   MySQL → Application writes to ES synchronously
#   Problems:
#     - If ES is down → MySQL write fails? Or data lost?
#     - Tight coupling: app must know about ES
#     - Can't replay: if ES corrupts, how to rebuild?
#     - Can't add new consumers without changing app

# With Kafka:
#   MySQL → Debezium → Kafka → ES Indexer
#   Benefits:
#     - ES down? Kafka buffers. No data loss.
#     - Decoupled: app doesn't know about ES
#     - Replay: reindex entire ES from Kafka in hours
#     - Add consumers: new team wants listing data? Add consumer group.
```

### 🎯 Key Interview Insights

```
Q: How do you keep search index in sync with the database?
A: "CDC + Kafka pipeline:
    Debezium reads MySQL binlog → publishes to Kafka → 
    enrichment consumer adds computed fields → 
    ES indexer consumer bulk-writes to Elasticsearch.
    
    End-to-end latency: < 5 seconds from DB write to searchable.
    If ES goes down, Kafka buffers events. When ES recovers, 
    consumer catches up automatically."

Q: Why not use MySQL triggers or dual-write?
A: Dual-write (app writes to both MySQL and ES):
   - Not atomic: MySQL succeeds, ES fails = inconsistency
   - Performance: doubles write latency
   
   CDC via Kafka:
   - Single write to MySQL (source of truth)
   - Async, decoupled propagation to ES
   - Exactly-once with idempotent consumer (use listing_id as ES _id)
```

---

## 7. DoorDash — Order State Machine & Event-Driven Workflow

### 🏗️ Architecture Context

A DoorDash order goes through: placed → accepted → preparing → ready → picked_up → delivering → delivered. Each transition triggers downstream actions: notify customer, update Dasher app, charge card, calculate pay, update ETA.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `order-events` | `order_id` | 256 | 30 days | Order state transitions |
| `dasher-assignments` | `dasher_id` | 64 | 7 days | Dasher-to-order matching |
| `payment-commands` | `order_id` | 64 | 30 days | Payment actions triggered by order events |
| `notification-commands` | `user_id` | 128 | 3 days | Push notifications to send |
| `eta-updates` | `order_id` | 64 | 1 hour | Real-time ETA recalculations |

### 🔧 State Machine via Kafka

```
# =============================================
# ORDER STATE MACHINE VIA KAFKA
# =============================================

# State: PLACED → ACCEPTED
Producer (merchant app):
  Topic: order-events
  Key: "order_123"
  Value: {
    "order_id": "order_123",
    "event": "ORDER_ACCEPTED",
    "merchant_id": "restaurant_789",
    "estimated_prep_time_min": 15,
    "timestamp": "2026-04-25T12:02:00Z"
  }

# Consumers react to this event:

Consumer: "customer-notifier"
  → Push notification: "Your order has been accepted! Estimated 15 min prep time."

Consumer: "dasher-dispatcher"  
  → Trigger matching algorithm to find a Dasher
  → When matched, publish to: dasher-assignments

Consumer: "eta-calculator"
  → Calculate delivery ETA = prep_time + travel_time
  → Publish to: eta-updates

Consumer: "payment-processor"
  → Pre-authorize the customer's card (don't charge yet)

# State: PICKED_UP → DELIVERING
Producer (Dasher app):
  Topic: order-events
  Key: "order_123"
  Value: {"event": "ORDER_PICKED_UP", "dasher_id": "dasher_456", ...}

Consumer: "customer-notifier"
  → "Your order is on its way! Dasher is 10 min away."

Consumer: "payment-processor"
  → Capture the pre-authorized charge
  → Record Dasher earnings

Consumer: "live-tracker"
  → Start streaming Dasher location to customer's app

# =============================================
# WHY EVENT-DRIVEN (not orchestration)?
# =============================================

# Orchestration pattern:
#   Order Service calls Notification Service
#   Then calls Payment Service
#   Then calls ETA Service
#   Problem: tight coupling, failure cascades, performance bottleneck

# Event-driven (choreography) pattern:
#   Order Service publishes event to Kafka
#   Each service subscribes and reacts independently
#   Benefits:
#     - Services are decoupled (can be deployed independently)
#     - Failure isolation (notification failure doesn't block payment)
#     - Easy to add new reactions (add new consumer group)
#     - Full event audit trail (every state transition recorded)
```

---

## 8. Spotify — Play Count Aggregation & Royalty Calculation

### 🏗️ Architecture Context

Every song play generates a play event. These must be aggregated for: artist dashboards (real-time play counts), royalty calculations (pay artists per stream), trending/charts, and personalized recommendations.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `play-events` | `user_id` | 512 | 30 days | Every play/skip/complete event |
| `play-counts` | `track_id` | 128 | Compacted | Aggregated play counts (compacted) |
| `royalty-events` | `rights_holder_id` | 64 | 90 days | Royalty-eligible plays for payment |
| `chart-signals` | `country_code` | 32 | 7 days | Signals for chart calculations |

### 🔧 Kafka Streams Aggregation

```
# =============================================
# STEP 1: Play event published
# =============================================

  Topic: play-events
  Key: "user_u789"
  Value: {
    "user_id": "u789",
    "track_id": "track_abc",
    "artist_id": "artist_xyz",
    "duration_played_sec": 187,    // Song is 210 sec
    "completed": false,            // Didn't finish
    "skip": false,
    "context": "playlist",
    "country": "US",
    "subscription_type": "premium",
    "timestamp": "2026-04-25T14:30:00Z"
  }

# =============================================
# STEP 2: Kafka Streams — Real-time aggregation
# =============================================

# Kafka Streams application (stateful stream processing):

KStream<String, PlayEvent> plays = builder.stream("play-events");

// Filter: only count plays > 30 seconds (Spotify's royalty rule)
KStream<String, PlayEvent> qualifiedPlays = plays
    .filter((key, event) -> event.durationPlayedSec >= 30);

// Aggregate: count per track (using Kafka Streams KTable)
KTable<String, Long> trackCounts = qualifiedPlays
    .map((key, event) -> KeyValue.pair(event.trackId, 1L))
    .groupByKey()
    .count(Materialized.as("track-play-counts"));

// Output: write aggregated counts back to Kafka (compacted topic)
trackCounts.toStream().to("play-counts", 
    Produced.with(Serdes.String(), Serdes.Long()));

// Result in play-counts topic (compacted):
//   Key: "track_abc" → Value: 45234892
//   Key: "track_def" → Value: 12045678

# WHY Kafka Streams (not Flink)?
# - Simpler deployment: just a Java library, no cluster to manage
# - Uses Kafka topics as both input AND state store
# - Exactly-once via Kafka transactions
# - Perfect for single-topic aggregation

# WHY compacted topic for play-counts?
# - Only latest count per track matters
# - Old counts deleted by compaction
# - Consumer can rebuild state by reading compacted topic from beginning
# - Acts like a distributed key-value store

# =============================================
# STEP 3: Consumer — Royalty calculator (batch job)
# =============================================

Consumer Group: "royalty-calculator"
  Reads from: play-events (filtered to qualified plays)
  Runs: hourly batch aggregation
  
  For each rights_holder:
    total_streams = count of qualified plays
    royalty = total_streams × $0.004 per stream (average)
    → Publish to: royalty-events
    → Monthly payout via payment system
```

---

## 9. Amazon — Click & Impression Tracking for Ads

### 🏗️ Architecture Context

When a user sees a sponsored product (impression) or clicks on one, these events flow through Kafka for: billing (charge advertiser per click), attribution (did the click lead to a purchase?), fraud detection, and campaign optimization.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `ad-impressions` | `campaign_id` | 256 | 7 days | Every ad shown (high volume!) |
| `ad-clicks` | `campaign_id` | 128 | 30 days | Ad clicks (medium volume) |
| `ad-conversions` | `campaign_id` | 64 | 90 days | Purchases attributed to ads |
| `budget-updates` | `campaign_id` | 64 | Compacted | Real-time campaign spend tracking |
| `fraud-alerts` | `advertiser_id` | 16 | 30 days | Detected fraud events |

### 🔧 Pipeline — Step by Step

```
# =============================================
# STEP 1: Ad impression (fire-and-forget, high throughput)
# =============================================

Producer (search API):
  Topic: ad-impressions
  Key: "campaign_c456"
  Value: {
    "impression_id": "imp_uuid_123",
    "campaign_id": "c456",
    "product_id": "B09XS7JWHH",
    "query": "wireless headphones",
    "position": 2,
    "user_id": "u789",
    "timestamp": "2026-04-25T10:30:00Z"
  }
  
  # Config: acks=1, compression=lz4 (high throughput, some loss OK)
  # 900M impressions/day = 10K/sec — need fast writes

# =============================================  
# STEP 2: Ad click (must not lose)
# =============================================

Producer (click tracker):
  Topic: ad-clicks
  Key: "campaign_c456"
  Value: {
    "click_id": "clk_uuid_456",
    "impression_id": "imp_uuid_123",   // Links to impression
    "campaign_id": "c456",
    "bid_cents": 150,
    "charged_cents": 121,              // GSP: second-price
    "user_id": "u789",
    "timestamp": "2026-04-25T10:30:05Z"
  }
  
  # Config: acks=all, idempotent=true (billing event — cannot lose)

# =============================================
# STEP 3: Consumer — Real-time budget tracking
# =============================================

Consumer Group: "budget-tracker"
  Reads from: ad-clicks
  For each click:
    1. INCRBY campaign_spend:{c456}:{today} 121 (Redis)
    2. If spend > 95% of daily budget → publish throttle signal
    3. Publish to: budget-updates (compacted)
       Key: "c456"  
       Value: {"daily_budget": 50000, "spent_today": 47521, "throttled": false}
    
  # Ad auction service reads budget-updates to check budget before auction

# =============================================
# STEP 4: Consumer — Click fraud detection
# =============================================

Consumer Group: "fraud-detector"
  Uses: Kafka Streams with windowed aggregation
  
  // Count clicks per user per campaign per hour
  KTable<Windowed<String>, Long> clickCounts = clicks
      .groupBy((key, click) -> click.userId + ":" + click.campaignId)
      .windowedBy(TimeWindows.of(Duration.ofHours(1)))
      .count();
  
  // Alert if > 5 clicks from same user on same campaign
  clickCounts.toStream()
      .filter((windowedKey, count) -> count > 5)
      .to("fraud-alerts");

  // Fraudulent clicks are marked as invalid → not billed
```

---

## 10. Slack — Message Delivery & Notification Fanout

### 🏗️ Architecture Context

When a user sends a message in a Slack channel, it must be: persisted to DB, delivered to all online channel members (real-time via WebSocket), queued for offline members (push notification + unread badge), indexed for search, and logged for compliance.

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `channel-messages` | `channel_id` | 256 | 30 days | All messages (source of truth) |
| `user-notifications` | `user_id` | 128 | 3 days | Notifications per user |
| `search-updates` | `channel_id` | 64 | 7 days | Messages to index for search |
| `compliance-log` | `workspace_id` | 32 | Infinite (compacted) | Legal/compliance retention |

### 🔧 Producer & Consumer — Step by Step

```
# =============================================
# Message sent in #engineering channel (5000 members)
# =============================================

Producer (message API):
  Topic: channel-messages
  Key: "channel_engineering"        // All messages in one channel → same partition → ordered!
  Value: {
    "message_id": "msg_uuid_789",
    "channel_id": "channel_engineering",
    "author_id": "user_alice",
    "text": "Deploying v2.4 to production now 🚀",
    "thread_ts": null,
    "timestamp": "2026-04-25T15:30:00Z"
  }

# =============================================
# Consumer Group: "realtime-delivery"
# =============================================

# For each message:
  1. Look up channel membership: 5000 users
  2. For ONLINE users (connected via WebSocket):
     → Push message directly via WebSocket connection
     → ~3000 users online → 3000 WebSocket pushes
  3. For OFFLINE users:
     → Increment unread counter: HINCRBY unread:{user_id} channel_engineering 1
     → If user has push notifications enabled:
       → Publish to: user-notifications (key: user_id)
     → Push notification sent after 30-second delay
       (in case user comes online and reads the message)

# WHY 30-second delay for push notifications?
# User might be opening the app right now.
# Sending a push notification for a message they're about to see = annoying.
# Delay + check: if unread_count for this channel is still > 0 after 30s → send push.

# WHY partition by channel_id?
# Messages in one channel MUST be delivered in order.
# Alice's message at 15:30:00 must appear before Bob's at 15:30:01.
# Same partition → same consumer → ordered delivery.
```

---

## 11. Walmart — Inventory Sync Across Warehouses

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `inventory-updates` | `sku_id` | 256 | 7 days | Stock level changes across warehouses |
| `inventory-snapshots` | `sku_id` | 128 | Compacted | Latest stock level per SKU per warehouse |
| `reorder-signals` | `sku_id` | 32 | 3 days | Trigger replenishment when stock is low |

### 🔧 Pipeline

```
# Warehouse POS system sells item:
  Topic: inventory-updates
  Key: "sku_HDTV_55_samsung"
  Value: {
    "sku_id": "HDTV_55_samsung",
    "warehouse_id": "warehouse_dallas",
    "change": -1,
    "reason": "SALE",
    "new_quantity": 23,
    "timestamp": "2026-04-25T14:30:00Z"
  }

# Consumer Group: "inventory-aggregator"
  Maintains: total stock per SKU across all warehouses
  sku_HDTV_55_samsung: dallas=23, chicago=45, la=12 → total=80
  
  If total < reorder_threshold (50):
    → Publish to reorder-signals
    → Purchasing system places order with supplier

# Consumer Group: "website-stock-updater"
  Reads inventory-updates
  Updates website: "In Stock" / "Low Stock" / "Out of Stock"
  Updates search index (ES) in_stock field

# WHY partition by sku_id?
# All stock changes for one product go to one consumer
# Consumer maintains accurate count per SKU
# No race condition: two warehouses selling same SKU simultaneously
# → both events go to same partition → processed sequentially
```

---

## 12. Datadog — Log Ingestion Pipeline (Millions/Sec)

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `raw-logs` | `service_name` | 1024 | 24 hours | Raw log events from all services |
| `parsed-logs` | `service_name` | 512 | 7 days | Parsed, enriched, structured logs |
| `alert-triggers` | `rule_id` | 64 | 1 hour | Logs matching alert rules |

### 🔧 Pipeline

```
# 10M logs/sec ingestion pipeline:

# Step 1: Agents ship logs
  Topic: raw-logs
  Key: "payment-service"
  Value: raw log line (gzip compressed)
  
  # 1024 partitions for massive parallelism
  # Producer config: linger.ms=100, batch.size=256KB, compression=lz4
  # WHY? Maximize throughput. 100ms batching + large batches = fewer requests.

# Step 2: Log parser consumers
Consumer Group: "log-parser" (512 consumers)
  Reads from: raw-logs
  For each log:
    1. Detect format (JSON, syslog, grok pattern)
    2. Parse into structured fields
    3. Enrich: add datacenter, team, environment from service registry
    4. Publish to: parsed-logs

# Step 3: Indexer consumers  
Consumer Group: "es-indexer" (256 consumers)
  Reads from: parsed-logs
  Batch 5000 logs → ES _bulk API
  
  # KEY: batch size is the #1 throughput lever
  # 5000 logs × 500 bytes = 2.5 MB per bulk request
  # ES handles large bulk requests much better than individual writes

# Step 4: Real-time alerting (Kafka Streams)
  Reads from: parsed-logs
  Pattern: count ERROR logs per service per 1-minute window
  If count > threshold → publish to: alert-triggers → PagerDuty
```

---

## 13. WhatsApp — Message Queue with Delivery Guarantees

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `messages` | `chat_id` | 512 | 30 days | All messages (ordered per chat!) |
| `message-status` | `message_id` | 128 | 7 days | Delivery receipts (sent/delivered/read) |
| `presence-events` | `user_id` | 64 | 1 hour | Online/offline/typing status |

### 🔧 Delivery Guarantees

```
# WHY partition by chat_id (not user_id)?
# Messages in a conversation MUST be in order.
# Alice sends "Hi" then "How are you?" → Bob must see them in order.
# chat_id = hash(sorted(alice_id, bob_id)) → all messages in one chat → one partition → ordered.

# Delivery flow:
  1. Alice sends message → Kafka (messages topic, key=chat_id)
  2. Consumer checks: is Bob online?
     YES → Push via WebSocket → Bob receives → ack back
           → Publish to message-status: {status: "DELIVERED"}
           → Two blue ticks ✓✓
     NO  → Store in "offline queue" (DynamoDB)
           → When Bob comes online → drain offline queue → deliver
           → Publish to message-status: {status: "DELIVERED"}
  3. Bob reads the message → client publishes:
     message-status: {status: "READ"} → Two blue ticks turn blue

# EXACTLY-ONCE delivery:
# Message has a unique message_id
# Consumer uses message_id as idempotency key
# If delivered twice (retry), Bob's client deduplicates by message_id
```

---

## 14. Pinterest — Image Processing Pipeline

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `pin-uploads` | `pin_id` | 128 | 7 days | New pin image uploads |
| `image-processing-tasks` | `pin_id` | 64 | 3 days | Resize, crop, compress tasks |
| `pin-enrichment` | `pin_id` | 32 | 3 days | Visual search features, tags, OCR |
| `search-index-updates` | `pin_id` | 32 | 24 hours | Pin metadata for search |

### 🔧 Pipeline

```
# User uploads a pin with image:
  Topic: pin-uploads
  Key: "pin_12345"
  Value: {
    "pin_id": "pin_12345",
    "user_id": "user_abc",
    "image_url": "s3://uploads/raw/pin_12345.jpg",
    "description": "Modern kitchen renovation ideas",
    "board_id": "board_home_decor"
  }

# Consumer: "image-processor"
  For each upload:
    1. Download raw image from S3
    2. Generate thumbnails: 150×150, 236×, 564×, original
    3. Compress with WebP format
    4. Upload all sizes to S3 CDN path
    5. Publish to: image-processing-tasks (DONE)

# Consumer: "visual-feature-extractor"
  For each processed image:
    1. Run CNN model to extract visual features (embedding vector)
    2. Run object detection: ["kitchen", "marble", "island", "modern"]
    3. Run OCR if text detected in image
    4. Publish to: pin-enrichment

# Consumer: "search-indexer"
  For each enriched pin:
    1. Combine text description + visual tags + OCR text
    2. Generate search embedding
    3. Index in Elasticsearch (text) + FAISS (visual similarity)

# WHY Kafka for image pipeline (not just a task queue)?
# 1. Retry: if image processing fails, message stays in Kafka
# 2. Multiple consumers: visual features + search index + recommendations all process same upload
# 3. Backfill: re-extract visual features when model improves (replay from Kafka)
```

---

## 15. Robinhood — Stock Price Streaming

### 📐 Topic Design

| Topic | Partition Key | Partitions | Retention | Purpose |
|---|---|---|---|---|
| `market-data` | `ticker` | 128 | 1 hour | Real-time stock quotes from exchanges |
| `user-watchlists` | `user_id` | 64 | Compacted | User's watched stocks |
| `price-alerts` | `user_id` | 32 | 3 days | Price alert triggers |
| `portfolio-valuations` | `user_id` | 64 | 7 days | Real-time portfolio value updates |

### 🔧 Streaming Pipeline

```
# Exchange sends AAPL price update:
  Topic: market-data
  Key: "AAPL"
  Value: {
    "ticker": "AAPL",
    "price": 189.50,
    "bid": 189.49,
    "ask": 189.51,
    "volume": 45234892,
    "timestamp": "2026-04-25T14:30:00.123Z"
  }
  
  # ~50K price updates/sec across all tickers
  # Partition by ticker → all AAPL updates in order in one partition

# Consumer Group: "price-alert-checker" (Kafka Streams)
  Reads: market-data
  For each price update:
    1. Look up alerts for this ticker (from user-watchlists KTable)
    2. Check: user_abc wants alert if AAPL > $190
    3. Current price $189.50 < $190 → no alert
    4. Next update: AAPL = $190.25 → TRIGGER!
    5. Publish to: price-alerts (key: user_abc)
    6. Push notification: "AAPL crossed $190!"

# Consumer Group: "portfolio-valuator" (Kafka Streams)
  Reads: market-data + user-watchlists (KTable join)
  For each price update:
    1. Find all users holding this stock
    2. Recalculate portfolio value
    3. Publish to: portfolio-valuations
    4. Push updated portfolio value to user's app via WebSocket

# WHY Kafka Streams (not regular consumers)?
# KTable join: market-data (stream) JOIN user-watchlists (table)
#   → For every price update, instantly know which users care about it
#   → Stateful processing with local RocksDB state store
#   → Exactly-once with Kafka transactions
```

---

## 16–25: Additional Real-World Applications (Summary)

### 16. Shopify — Order Processing Pipeline
```
Topic: order-events (key: order_id)
Flow: Order placed → payment → inventory reserve → shipping label → tracking
Each step = new event consumed by independent services
WHY Kafka: Saga pattern for distributed transactions without 2PC
```

### 17. Twitch — Chat Message Delivery
```
Topic: chat-messages (key: channel_id)  
Flow: Viewer sends message → moderation filter → broadcast to all viewers
100K+ messages/sec during popular streams. Kafka handles burst.
WHY Kafka: Back-pressure handling when chat goes viral
```

### 18. GitHub — Webhook Delivery
```
Topic: webhook-events (key: repo_id)
Flow: Push to repo → build events → CI/CD webhooks to 10+ integrations
WHY Kafka: Reliable delivery with retry + dead-letter for failed webhooks
```

### 19. Coinbase — Crypto Trade Matching
```
Topic: trade-orders (key: trading_pair, e.g., "BTC-USD")
Flow: Order placed → matching engine → execution → settlement
WHY Kafka: Strict ordering per trading pair + exactly-once for financial data
```

### 20. Instagram — Story View Counting
```
Topic: story-views (key: story_id)
Flow: User views story → deduplicate → aggregate count → display to author
WHY Kafka: Millions of views/sec, Kafka Streams for dedup + count
```

### 21. TikTok — Content Moderation Pipeline
```
Topic: uploaded-videos (key: video_id)
Flow: Upload → moderation (AI + human) → approved → publish → index
WHY Kafka: Async moderation doesn't block upload UX, retry on failure
```

### 22. Lyft — Dynamic Pricing Signals
```
Topic: ride-requests (key: geo_cell_id)
Flow: Request → aggregate demand per area → calculate surge → update pricing
WHY Kafka Streams: Windowed aggregation for real-time supply/demand
```

### 23. Discord — Server Activity Events
```
Topic: server-events (key: guild_id)
Flow: Join/leave/message/react → activity feed → mod tools → analytics
WHY Kafka: 19M concurrent users, massive event fan-out
```

### 24. Notion — Collaborative Editing Events
```
Topic: document-operations (key: page_id)
Flow: OT/CRDT operations → ordered per document → applied to all editors
WHY Kafka: Per-document ordering guarantees for collaborative editing
```

### 25. Datadog — Metric Aggregation
```
Topic: raw-metrics (key: metric_name + host)
Flow: Agent → Kafka → aggregate (1min/5min/1hr) → store in time-series DB
WHY Kafka: Buffer billions of data points, batch aggregation for efficiency
```

---

## 🏆 Cheat Sheet: Kafka Topic & Partition Key Design Guide

| Use Case | Partition Key | Why | # Partitions |
|---|---|---|---|
| **User activity / events** | `user_id` | Per-user ordering (feed, notifications) | 128-512 |
| **Order lifecycle** | `order_id` | Order state machine must be sequential | 64-256 |
| **Chat messages** | `channel_id` / `chat_id` | Messages in a conversation must be ordered | 128-512 |
| **Financial transactions** | `payment_id` / `account_id` | Can't process settlement before charge | 64-128 |
| **IoT / location data** | `device_id` / `driver_id` | Per-device event ordering | 256-1024 |
| **Geo-based aggregation** | `geo_cell_id` | Aggregate events per geographic area | 32-128 |
| **Search index updates** | `document_id` | Latest version wins per document | 64-256 |
| **Inventory updates** | `sku_id` | Count per item must be sequential | 64-256 |
| **Metrics / logs** | `service_name` | Group by origin for processing | 256-1024 |
| **Webhook delivery** | `merchant_id` | Per-merchant ordering | 32-128 |

### Partition Count Rules of Thumb

```
1. Partitions ≥ max expected consumers (for full parallelism)
2. Start with 2× expected consumers (room to grow)
3. Each partition handles ~10 MB/sec write throughput
4. More partitions = more file handles, more memory, more rebalance time
5. Sweet spot: 32 - 512 per topic (most systems)
6. LinkedIn extreme: 100K+ partitions across cluster
```

---

## 🎯 Interview Summary: "When Would You Use Kafka?"

### ✅ Use Kafka When:

| Scenario | Why Kafka | Example |
|---|---|---|
| **Multiple consumers need same data** | Consumer groups read independently | LinkedIn: 10+ services process user events |
| **Event ordering matters** | Partition key guarantees per-key ordering | Uber: ride state machine must be sequential |
| **Need replay / reprocessing** | Retention + offset reset | Netflix: replay events to fix analytics bug |
| **Decouple producers from consumers** | Producers don't know who reads | Stripe: payment event → N downstream handlers |
| **Handle traffic spikes** | Kafka absorbs burst, consumers process at own pace | Twitter: tweet fan-out buffered during viral events |
| **CDC (Database → Search/Cache)** | Debezium → Kafka → ES/Redis | Airbnb: MySQL → Kafka → Elasticsearch |
| **Stream processing** | Kafka Streams / Flink on Kafka topics | Spotify: real-time play count aggregation |
| **Event sourcing / audit log** | Immutable append-only log | Stripe: complete payment audit trail |
| **Financial / exactly-once** | Idempotent producer + transactional consumer | Coinbase: trade execution |

### ❌ Don't Use Kafka When:

| Scenario | Use Instead | Why |
|---|---|---|
| **Simple task queue (one consumer)** | SQS / RabbitMQ | Kafka is overkill for fire-and-forget tasks |
| **Request-reply / RPC** | gRPC / REST | Kafka is async — bad for synchronous req-reply |
| **Small scale (< 1K msg/sec)** | SQS / Redis Streams | Kafka's operational overhead not justified |
| **Message routing / filtering** | RabbitMQ (topic exchange) | Kafka lacks built-in message filtering |
| **Exactly-once with non-Kafka sinks** | Kafka + idempotent consumer | Kafka EOS only works for Kafka-to-Kafka |
| **Priority queues** | RabbitMQ / SQS | Kafka has no native priority support |
| **Temporary / ephemeral messaging** | Redis Pub/Sub | If you don't need durability or replay |

### 🗣️ Interview Power Phrases

```
"I'd use Kafka here because we need multiple consumers to process the same event 
independently — the feed service, notification service, and analytics all consume 
from the same topic via separate consumer groups."

"I'd partition by order_id to guarantee per-order event ordering — 
the state machine (created → paid → shipped) must be processed sequentially."

"For this CDC pipeline, I'd use Debezium to capture MySQL binlog changes into Kafka, 
then a consumer writes to Elasticsearch. If ES goes down, Kafka buffers. 
When ES recovers, the consumer catches up from where it left off."

"The beauty of Kafka here is replayability — if we discover a bug in the analytics 
pipeline, we can reset consumer offsets to 3 days ago and reprocess. 
This is impossible with SQS where messages are deleted after consumption."

"I'd configure acks differently per topic: acks=all for payment events (can't lose), 
acks=1 for location pings (ephemeral, throughput matters more than durability)."

"I'd use Kafka Streams for this aggregation because it gives us exactly-once 
processing with local state in RocksDB — no external database needed for the 
windowed count."
```
