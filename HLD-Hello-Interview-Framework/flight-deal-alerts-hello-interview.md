# Design a Discounted Flight Alert System - Hello Interview Framework

> **Question**: Design a flight price monitoring system that allows users to subscribe to specific routes and receive email notifications within 10 minutes when flight prices drop significantly below historical averages. The system should integrate with external flight APIs, handle API failures gracefully, deduplicate notifications, and scale to support many users and routes.
>
> **Asked at**: Datadog
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
1. **Route Subscription**: Users subscribe to flight routes (e.g., NYC→SFO, LAX→LHR) with optional date ranges, airlines, and max price thresholds.
2. **Price Monitoring**: Continuously poll external flight APIs (Google Flights, Skyscanner, Amadeus) for price data on all subscribed routes. Detect when prices drop significantly below historical averages.
3. **Deal Detection**: Compare current prices against historical baselines. A "deal" = price dropped > 20% below 90-day rolling average for that route + date + cabin class.
4. **Alert Notifications**: Email users within 10 minutes of deal detection. Include: route, price, discount %, dates, booking link. Deduplicate: don't alert same user for same deal twice.
5. **API Integration**: Handle flaky/rate-limited external flight APIs. Retry with backoff, circuit breaker, and fallback to cached prices. Respect per-API rate limits.
6. **Historical Price Tracking**: Store price history for all monitored routes. Use for deal detection baselines and trend visualization.

#### Nice to Have (P1)
- Push notifications (mobile) and SMS in addition to email.
- Price trend graphs per route (show user if prices are trending down).
- Flexible alerts (e.g., "any destination from NYC under $200").
- Multi-leg trip support (NYC→LHR→CDG).
- Alert frequency preferences (instant, daily digest, weekly summary).
- Integration with booking platforms (deep link to buy).

#### Below the Line (Out of Scope)
- Flight booking/payment processing.
- Seat selection, baggage, and ancillary services.
- Building the flight search API itself.
- Hotel/car rental bundling.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Deal-to-Alert Latency** | < 10 minutes | Users need timely alerts; deals disappear fast |
| **Price Freshness** | Prices updated every 5-15 minutes per route | Balance API costs vs freshness |
| **Availability** | 99.9% for subscriptions, 99.5% for price polling | Price polling tolerates brief gaps |
| **API Rate Limits** | Respect per-provider limits (e.g., 1000 req/min) | Avoid being blocked by providers |
| **Notification Dedup** | Zero duplicate alerts for same deal to same user | User trust; avoid spam |
| **Scale** | 10M users, 1M active subscriptions, 50K monitored routes | Consumer-scale flight alert service |

### Capacity Estimation

```
Users: 10M total, 2M DAU
Active subscriptions: 1M (route + date range combos)
Unique monitored routes: 50K (popular origin-destination pairs)

Price polling:
  50K routes × 1 poll/10 min = 5K API calls/min = ~83/sec
  With 5 flight API providers: ~17 calls/sec per provider (well within limits)
  Each response: ~5 KB (prices for multiple dates/airlines)
  Data ingested: 50K × 5 KB × 6/hr × 24h = ~36 GB/day

Deal detection:
  50K routes evaluated every 10 minutes
  ~5% have significant price drops = 2,500 deals/evaluation cycle
  Deals per day: 2,500 × 144 cycles = ~360K deals/day

Notifications:
  Each deal matches ~20 subscribers on average
  360K deals × 20 = 7.2M notifications/day
  Peak: ~500K notifications/hour

Storage:
  Price history: 50K routes × 365 days × 10 data points/day × 200 bytes = ~36 GB/year
  Subscriptions: 1M × 500 bytes = 500 MB
  Notification log: 7.2M/day × 200 bytes = ~1.4 GB/day
```

---

## 2️⃣ Core Entities

### Entity 1: Route Subscription
```java
public class Subscription {
    private final String subscriptionId;
    private final String userId;
    private final String origin;            // "JFK", "LAX" (IATA code)
    private final String destination;       // "SFO", "LHR"
    private final LocalDate dateFrom;       // Travel date range start
    private final LocalDate dateTo;         // Travel date range end
    private final CabinClass cabinClass;    // ECONOMY, BUSINESS, FIRST
    private final Integer maxPriceUsd;      // Optional: only alert if below this price
    private final List<String> airlines;    // Optional: filter by airline
    private final AlertFrequency frequency; // INSTANT, DAILY_DIGEST, WEEKLY
    private final boolean active;
    private final Instant createdAt;
}
public enum CabinClass { ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST }
public enum AlertFrequency { INSTANT, DAILY_DIGEST, WEEKLY }
```

### Entity 2: Price Record
```java
public class PriceRecord {
    private final String priceId;
    private final String routeKey;          // "JFK-SFO" (origin-destination)
    private final LocalDate travelDate;
    private final String airline;
    private final CabinClass cabinClass;
    private final int priceUsd;             // Current price in cents
    private final String provider;          // "google_flights", "skyscanner", "amadeus"
    private final Instant fetchedAt;
}
```

### Entity 3: Route
```java
public class MonitoredRoute {
    private final String routeKey;          // "JFK-SFO"
    private final String origin;
    private final String destination;
    private final int subscriberCount;      // How many users watch this route
    private final Instant lastPolledAt;
    private final int pollingIntervalMin;   // 5-15 min based on popularity
    private final double historicalAvgPrice;// 90-day rolling average
}
```

### Entity 4: Deal
```java
public class Deal {
    private final String dealId;
    private final String routeKey;
    private final LocalDate travelDate;
    private final String airline;
    private final CabinClass cabinClass;
    private final int currentPriceUsd;
    private final int historicalAvgPriceUsd;
    private final double discountPercent;   // e.g., 35.0 (35% below average)
    private final String bookingUrl;
    private final Instant detectedAt;
    private final Instant expiresAt;        // Estimated deal expiry (TTL)
}
```

### Entity 5: Notification
```java
public class Notification {
    private final String notificationId;
    private final String userId;
    private final String dealId;
    private final String subscriptionId;
    private final NotificationStatus status;// PENDING, SENT, FAILED, DEDUPLICATED
    private final String channel;           // EMAIL, PUSH, SMS
    private final Instant createdAt;
    private final Instant sentAt;
}
public enum NotificationStatus { PENDING, SENT, FAILED, DEDUPLICATED }
```

### Entity 6: API Provider Config
```java
public class ApiProviderConfig {
    private final String providerId;        // "google_flights", "skyscanner"
    private final String apiEndpoint;
    private final int rateLimitPerMinute;
    private final int timeoutMs;
    private final CircuitBreakerState circuitState; // CLOSED, OPEN, HALF_OPEN
    private final int consecutiveFailures;
    private final Instant lastSuccessAt;
}
```

---

## 3️⃣ API Design

### 1. Create Subscription
```
POST /api/v1/subscriptions
Request:
{
  "origin": "JFK", "destination": "SFO",
  "date_from": "2025-03-01", "date_to": "2025-03-31",
  "cabin_class": "ECONOMY", "max_price_usd": 250,
  "airlines": ["UA", "DL", "AA"],
  "alert_frequency": "INSTANT"
}
Response (201):
{ "subscription_id": "sub_001", "route_key": "JFK-SFO", "status": "ACTIVE" }
```

### 2. List My Subscriptions
```
GET /api/v1/subscriptions?status=ACTIVE
Response (200):
{ "subscriptions": [{ "subscription_id": "sub_001", "origin": "JFK", "destination": "SFO", ... }] }
```

### 3. Get Price History for Route
```
GET /api/v1/routes/JFK-SFO/prices?date=2025-03-15&days=90
Response (200):
{
  "route": "JFK-SFO", "travel_date": "2025-03-15",
  "current_price": 189, "historical_avg": 310, "discount_percent": 39,
  "price_history": [{ "date": "2025-01-01", "price": 320 }, { "date": "2025-01-02", "price": 305 }, ...]
}
```

### 4. Get Active Deals (Browse)
```
GET /api/v1/deals?origin=JFK&min_discount=25
Response (200):
{
  "deals": [{
    "deal_id": "deal_001", "route": "JFK-SFO", "travel_date": "2025-03-15",
    "price": 189, "avg_price": 310, "discount": 39, "airline": "United",
    "booking_url": "https://...", "detected_at": "2025-01-10T10:00:00Z"
  }]
}
```

### 5. Delete Subscription
```
DELETE /api/v1/subscriptions/{subscription_id}
Response (204 No Content)
```

### 6. Update Notification Preferences
```
PATCH /api/v1/subscriptions/{subscription_id}
Request: { "alert_frequency": "DAILY_DIGEST", "max_price_usd": 300 }
Response (200): { "subscription_id": "sub_001", "updated": true }
```

---

## 4️⃣ Data Flow

### Flow 1: Price Polling Pipeline
```
1. Scheduler triggers every 5-15 minutes per route (based on popularity)
   ↓
2. Price Poller Service:
   a. For each route due for polling:
      - Select API provider (round-robin, prefer cheapest/fastest)
      - Check circuit breaker: if OPEN → skip provider, try next
      - Call external API: GET flights?origin=JFK&dest=SFO&date=2025-03-15
      - Retry on failure (max 2 retries, exponential backoff)
   b. Parse response: extract prices by date, airline, cabin class
   c. Publish price data to Kafka topic "raw-prices"
   ↓
3. Price Writer (Kafka consumer):
   a. Write price records to Price History DB (append-only)
   b. Update current price cache (Redis): price:{route}:{date}:{cabin} = 189
   ↓
4. Price data available for deal detection within ~1 minute
```

### Flow 2: Deal Detection
```
1. Deal Detector runs after each price update (event-driven via Kafka)
   ↓
2. For each new price record:
   a. Fetch 90-day historical average for this route + date + cabin
      (pre-computed, stored in Redis: avg_price:{route}:{date}:{cabin})
   b. Compare: discount_percent = (avg - current) / avg × 100
   c. If discount_percent > 20%:
      → Create Deal record
      → Publish to Kafka topic "deals-detected"
   ↓
3. Deal enrichment:
   a. Generate booking URL (deep link to airline/aggregator)
   b. Estimate deal expiry (based on historical deal duration)
   ↓
4. Deal available for notification matching within ~2 minutes
```

### Flow 3: Notification Fanout
```
1. Notification Matcher consumes from "deals-detected" Kafka topic
   ↓
2. For each deal:
   a. Query subscription index: "who subscribes to JFK-SFO, ECONOMY, date=March 15?"
      → Redis SET: subs:{route}:{cabin} → [sub_001, sub_002, ...]
   b. For each matching subscription:
      - Check user preferences (INSTANT vs DAILY_DIGEST)
      - Check max_price filter (price <= max_price?)
      - Check dedup: Redis SET exists? dedup:{user}:{deal_route}:{date} 
        → If exists → skip (already notified for this deal)
        → If not → create notification + set dedup key (TTL: 24h)
   c. Produce notification events to Kafka "notifications" topic
   ↓
3. Notification Sender:
   a. Consume notification events
   b. Render email template (route, price, discount, booking link)
   c. Send via email provider (SendGrid/SES)
   d. Record delivery status
   ↓
4. Total: price drop detected → email sent: < 10 minutes
```

### Flow 4: Historical Average Computation
```
1. Daily batch job (1 AM UTC):
   a. For each route + date + cabin:
      - Compute 90-day rolling average from price history
      - Write to Redis: avg_price:{route}:{date}:{cabin} = 310
   b. Also compute: median, P25, P75 (for deal quality scoring)
   ↓
2. Incremental update: on each new price, update running average
   (exponential moving average for real-time, batch for accuracy)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          EXTERNAL FLIGHT APIs                                │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                       │
│  │Google Flights │ │ Skyscanner   │ │ Amadeus GDS  │                       │
│  │ API           │ │ API          │ │ API          │                       │
│  └──────┬────────┘ └──────┬───────┘ └──────┬───────┘                       │
└─────────┼─────────────────┼────────────────┼────────────────────────────────┘
          └─────────────────┼────────────────┘
                            │ Rate-limited API calls
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PRICE POLLER SERVICE (20-50 pods)                          │
│  • Scheduled polling per route (5-15 min intervals)                         │
│  • Circuit breaker per API provider                                         │
│  • Retry with exponential backoff                                           │
│  • Rate limiter: respect per-provider limits                                │
│  • Produce raw prices to Kafka                                              │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA                                                │
│  Topic: "raw-prices"      (price updates from all providers)                │
│  Topic: "deals-detected"  (deals passing threshold)                         │
│  Topic: "notifications"   (notification events for email/push)              │
└──────┬──────────────────────────┬───────────────────┬────────────────────────┘
       │                          │                   │
 ┌─────▼──────────┐     ┌───────▼────────┐   ┌─────▼──────────────┐
 │ PRICE WRITER    │     │ DEAL DETECTOR  │   │ NOTIFICATION       │
 │                 │     │                │   │ MATCHER + SENDER   │
 │ • Write to DB   │     │ • Compare vs   │   │                    │
 │ • Update Redis  │     │   historical   │   │ • Match deal →     │
 │   price cache   │     │   average      │   │   subscriptions    │
 │                 │     │ • > 20% drop   │   │ • Dedup check      │
 │ Pods: 10       │     │   → emit deal  │   │ • Send email/push  │
 └────────┬────────┘     │                │   │ • Record delivery  │
          │              │ Pods: 10       │   │                    │
          ▼              └───────┬────────┘   │ Pods: 20-50       │
┌──────────────────┐            │             └──────────┬─────────┘
│ PRICE HISTORY DB │            ▼                        │
│                  │   ┌──────────────────┐             ▼
│ TimescaleDB /    │   │ DEAL STORE       │   ┌──────────────────┐
│ PostgreSQL       │   │ (PostgreSQL)     │   │ EMAIL PROVIDER   │
│                  │   │ + Redis cache    │   │ (SendGrid / SES) │
│ • Price records  │   │                  │   └──────────────────┘
│ • ~36 GB/year    │   │ • Active deals   │
│                  │   │ • Deal history   │
└──────────────────┘   └──────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        SUPPORTING SERVICES                                    │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐       │
│  │ SUBSCRIPTION     │  │ PRICE CACHE      │  │ DEDUP CACHE          │       │
│  │ SERVICE          │  │ (Redis)          │  │ (Redis)              │       │
│  │                  │  │                  │  │                      │       │
│  │ • CRUD subs      │  │ • Current prices │  │ • dedup:{user}:{deal}│       │
│  │ • Subscription   │  │ • Historical avg │  │   TTL: 24 hours     │       │
│  │   index (Redis)  │  │ • Route metadata │  │ • Prevents duplicate │       │
│  │                  │  │                  │  │   notifications     │       │
│  │ DB: PostgreSQL   │  │ ~1 GB            │  │ ~500 MB             │       │
│  └─────────────────┘  └──────────────────┘  └──────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Price Poller** | Call external flight APIs, handle failures/retries | Go on K8s | 20-50 pods (I/O bound) |
| **Price Writer** | Persist prices to DB + update Redis cache | Go on K8s | 10 pods |
| **Deal Detector** | Compare prices vs historical avg, emit deals | Go on K8s | 10 pods |
| **Notification Matcher** | Match deals to subscriptions, dedup, fanout | Go on K8s | 20-50 pods |
| **Notification Sender** | Render + send emails via SendGrid/SES | Go on K8s | 20-50 pods |
| **Price History DB** | Append-only price records | TimescaleDB / PostgreSQL | ~36 GB/year |
| **Price Cache** | Current prices + historical averages | Redis | ~1 GB |
| **Subscription Store** | User subscriptions + subscription index | PostgreSQL + Redis | ~500 MB |
| **Dedup Cache** | Prevent duplicate notifications | Redis (TTL keys) | ~500 MB |
| **Kafka** | Event streaming between components | Apache Kafka | 5+ brokers |

---

## 6️⃣ Deep Dives

### Deep Dive 1: External API Integration — Circuit Breaker + Rate Limiting

**The Problem**: External flight APIs are flaky (timeouts, 429s, 500s), rate-limited (1000 req/min), and expensive. Must handle failures gracefully without missing price data.

```
Per-provider circuit breaker:
  CLOSED (normal): requests flow through
    → 5 consecutive failures → OPEN
  OPEN (tripped): skip this provider, use fallback
    → After 60 seconds → HALF_OPEN (try one request)
  HALF_OPEN: if success → CLOSED; if fail → OPEN

Rate limiting per provider:
  Token bucket in Redis: ratelimit:{provider}
  Refill: 1000 tokens/min (provider's limit)
  Before each call: consume 1 token; if empty → wait or use another provider

Retry strategy:
  Attempt 1: immediate
  Attempt 2: after 1 second
  Attempt 3: after 5 seconds
  Max: 3 attempts; then → mark route as "stale" (use cached price)

Multi-provider fallback:
  Primary: Google Flights API
  Secondary: Skyscanner API
  Tertiary: Amadeus GDS
  If primary circuit open → route to secondary → tertiary
  Aggregate: use lowest price across providers (best deal for user)
```

---

### Deep Dive 2: Deal Detection Algorithm — When Is a Price a "Deal"?

**The Problem**: Not every low price is a deal. Seasonal patterns matter (Christmas flights are always expensive). We need a smart baseline that accounts for route, date, day-of-week, and seasonal trends.

```
Deal detection algorithm:

1. BASELINE: 90-day rolling average price for (route, travel_date_range, cabin)
   - Computed daily in batch job
   - Stored in Redis: avg_price:JFK-SFO:2025-03:ECONOMY = 310

2. DEAL THRESHOLD: current_price < baseline × (1 - threshold)
   - Default threshold: 20% below average
   - Premium routes (popular): 15% below average
   - Off-peak routes: 25% below average

3. DEAL QUALITY SCORING:
   score = discount_percent × recency_factor × popularity_factor
   - Higher discount → higher score
   - More recent detection → higher score (deals expire)
   - More subscribers for route → higher priority

4. SEASONAL ADJUSTMENT:
   - Compare vs same-month historical average (not just 90-day)
   - Example: Dec NYC→MIA is always $500 → $450 is NOT a deal
     But $300 IS a deal (vs $500 seasonal average)

5. DEDUP AT DEAL LEVEL:
   - Same route + date + airline + cabin + price band → one deal
   - Price band: $10 buckets (e.g., $180-190 = same deal)
   - Prevents alert spam from tiny price fluctuations
```

---

### Deep Dive 3: Notification Deduplication — Zero Spam Guarantee

**The Problem**: A deal may be detected multiple times (multiple API polls, multiple providers). A user subscribing to "JFK→SFO in March" should get ONE alert per deal, not 10.

```
Three layers of deduplication:

Layer 1: DEAL-LEVEL DEDUP (in Deal Detector)
  Key: deal:{route}:{date}:{airline}:{price_band}
  Redis SET with TTL 24 hours
  If key exists → same deal already detected → skip

Layer 2: USER-LEVEL DEDUP (in Notification Matcher)
  Key: notified:{user_id}:{route}:{travel_date}
  Redis SET with TTL 24 hours
  If key exists → user already notified for this route+date → skip
  Prevents: different airlines, same route → multiple alerts

Layer 3: NOTIFICATION-LEVEL DEDUP (in Notification Sender)
  Idempotency key: {user_id}:{deal_id}
  Before sending: check if already sent (in notification log DB)
  If sent → skip
  Prevents: Kafka reprocessing causing duplicate sends

Result: user gets exactly ONE email per deal, regardless of how many times
the deal is detected or how many subscriptions match.
```

---

### Deep Dive 4: Polling Scheduler — Efficient Route Monitoring

**The Problem**: 50K routes to monitor, but not all equally important. A route with 10K subscribers should be polled more frequently than one with 3 subscribers. Must stay within API rate limits.

```
Adaptive polling intervals:

PRIORITY TIERS:
  Hot routes (> 1000 subscribers): poll every 5 minutes
  Warm routes (100-1000 subs): poll every 10 minutes
  Cold routes (< 100 subs): poll every 15 minutes

SCHEDULING:
  - Distribute routes across time slots to avoid burst
  - 50K routes / 5-15 min = ~100 API calls/sec (spread evenly)
  - Use consistent hashing: route_key → poller pod → scheduled slots
  - Each poller pod owns ~1000 routes

SMART SCHEDULING:
  - Poll more frequently during business hours (when deals more likely)
  - Reduce polling for routes with stable prices (low variance)
  - Increase polling when price is trending down (deal emerging)

API BUDGET ALLOCATION:
  Total budget: 1000 calls/min per provider × 3 providers = 3000 calls/min
  Hot routes consume: 10K routes / 5 min = 2000 calls/5 min = 400/min
  Warm routes: 20K / 10 min = 2000/10 min = 200/min  
  Cold routes: 20K / 15 min = 1333/15 min = 89/min
  Total: ~689/min — well within 3000/min budget
```

---

### Deep Dive 5: Subscription Index — Fast Matching Deals to Subscribers

**The Problem**: When a deal is detected for JFK→SFO, we need to quickly find ALL subscriptions matching that route, date range, cabin class, and price filter. With 1M subscriptions, linear scan is too slow.

```
Subscription index in Redis:

Inverted index by route:
  subs_route:JFK-SFO → SET of subscription_ids [sub_001, sub_002, ...]
  
For each subscription_id, store details in hash:
  sub:sub_001 → { user_id, date_from, date_to, cabin, max_price, frequency }

Deal matching (per deal):
  1. Get candidates: SMEMBERS subs_route:JFK-SFO → [sub_001, sub_002, ...]
  2. For each candidate: HGETALL sub:{sub_id}
  3. Filter:
     - deal.travel_date BETWEEN sub.date_from AND sub.date_to?
     - deal.cabin_class == sub.cabin_class?
     - deal.price <= sub.max_price?
     - sub.frequency == INSTANT? (else queue for digest)
  4. Matching subs → produce notification events

Performance:
  Average route: 20 subscribers → 20 hash lookups = < 1ms
  Popular route (10K subs): ~10ms with pipelining
  Total per deal: < 10ms
```

---

### Deep Dive 6: Email Delivery — Reliable Sending at Scale

**The Problem**: 7.2M emails/day, peaking at 500K/hour. Must handle email provider failures, bounce-backs, and respect sending limits. Emails must be timely (< 10 min from deal detection).

```
Email pipeline:

1. Notification Sender consumes from Kafka "notifications" topic
2. Render email template:
   - Subject: "✈️ Deal Alert: JFK → SFO for $189 (39% off!)"
   - Body: route, dates, price comparison, trend chart, booking button
   - Personalized: user's name, subscription details
3. Send via email provider:
   Primary: SendGrid (high deliverability, 100K/hr limit)
   Failover: AWS SES (if SendGrid fails)
4. Track delivery: webhook for bounces, opens, clicks
5. Retry on failure: 3 retries, 1-min intervals
6. Daily digest: for DAILY_DIGEST subscribers, batch at 8 AM user's timezone

Sending rate management:
  - SendGrid limit: 100K emails/hour → 1,667/min
  - Peak load: 500K/hour → need multiple provider accounts or SES overflow
  - Kafka consumer parallelism: 50 pods × 34 emails/min = 1,700/min
```

---

### Deep Dive 7: Data Retention & Historical Price Storage

**The Problem**: Price history is valuable for deal detection baselines and user-facing trend graphs. Must store efficiently and query fast for "90-day average for JFK→SFO in March."

```
Price storage schema (TimescaleDB / PostgreSQL with partitioning):

CREATE TABLE price_history (
    route_key VARCHAR(10),      -- "JFK-SFO"
    travel_date DATE,
    airline VARCHAR(5),
    cabin_class VARCHAR(20),
    price_usd INT,
    provider VARCHAR(30),
    fetched_at TIMESTAMPTZ
) PARTITION BY RANGE (fetched_at);
-- Monthly partitions: auto-drop after 2 years

Indexes:
  (route_key, travel_date, cabin_class, fetched_at DESC) — for baseline computation
  (route_key, fetched_at DESC) — for price trend queries

Pre-computed aggregates (Redis, refreshed daily):
  avg_price:{route}:{month}:{cabin} = 310
  min_price:{route}:{month}:{cabin} = 180
  p25_price:{route}:{month}:{cabin} = 250

Query patterns:
  "90-day average for JFK-SFO, March, Economy" → Redis lookup (< 1ms)
  "Price history for JFK-SFO last 90 days" → TimescaleDB query (< 100ms)
```

---

### Deep Dive 8: Graceful Degradation — When APIs Fail

**The Problem**: If all flight API providers are down simultaneously, we can't poll prices. The system should degrade gracefully: no false alerts, no missed deals, clear user communication.

```
Degradation modes:

1. SINGLE PROVIDER DOWN (circuit breaker OPEN):
   → Route to other providers (no user impact)
   → Dashboard shows: "Provider X: degraded"

2. ALL PROVIDERS DOWN (rare):
   → Stop deal detection (can't detect deals without fresh prices)
   → Serve cached prices (mark as "stale: last updated X min ago")
   → Queue pending route polls; resume when providers recover
   → DO NOT fire alerts from stale data (avoid false positives)

3. PARTIAL DATA (some dates/airlines missing from response):
   → Store what we got; mark missing data as "incomplete"
   → Don't compare incomplete data against full historical average
   → Retry missing data on next poll cycle

4. USER COMMUNICATION:
   → If a subscribed route hasn't been polled in > 30 minutes:
     Mark subscription as "monitoring paused" in user dashboard
   → Email user if paused > 2 hours: "Price monitoring temporarily limited"
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Price polling** | Scheduled per-route (5-15 min adaptive) | Balance API costs vs freshness; popular routes polled more |
| **Deal detection** | 90-day rolling average + 20% threshold | Accounts for seasonal patterns; configurable per route tier |
| **Notification dedup** | 3-layer (deal-level + user-level + notification-level) | Zero duplicates guaranteed; Redis TTL for automatic cleanup |
| **API resilience** | Circuit breaker + multi-provider fallback + retry | Graceful degradation; no false alerts from stale data |
| **Subscription matching** | Redis inverted index by route | < 10ms per deal matching; scales to 1M subscriptions |
| **Email delivery** | Kafka → SendGrid (primary) + SES (failover) | Reliable; handles 500K/hour peak; retry on failure |
| **Price storage** | TimescaleDB with monthly partitions + Redis aggregates | Fast baseline queries (< 1ms from Redis); 2-year retention |
| **Event-driven pipeline** | Kafka between all stages | Decoupled; handles burst; durable; easy to add new consumers |

## Interview Talking Points

1. **"Circuit breaker + multi-provider fallback for flaky flight APIs"** — Per-provider circuit breaker (5 failures → OPEN). Automatic fallback to secondary/tertiary provider. Rate limiting via Redis token bucket. No false alerts from stale data.

2. **"3-layer notification deduplication: deal-level → user-level → notification-level"** — Deal dedup prevents detecting same deal twice. User dedup prevents alerting same user for same route+date. Notification dedup prevents Kafka reprocessing duplicates. All via Redis TTL keys.

3. **"Adaptive polling: hot routes every 5 min, cold routes every 15 min"** — Routes prioritized by subscriber count. Stays within API rate budget (689/min vs 3000/min limit). Smart scheduling increases frequency when prices trending down.

4. **"Deal detection: 90-day seasonal-adjusted average with 20% threshold"** — Not just rolling average — seasonal adjustment prevents false deals (Dec flights always expensive). Price-band bucketing ($10 bands) prevents alert spam from tiny fluctuations.

5. **"< 10 minute end-to-end SLA: price poll → Kafka → deal detect → match subs → send email"** — Each stage < 2 minutes. Kafka decouples stages. Email sent via SendGrid/SES within 1 minute of notification event.

6. **"Redis subscription index: < 10ms to match deal against 1M subscriptions"** — Inverted index by route key. SMEMBERS → filter by date/cabin/price. Average 20 subs per route = 20 hash lookups.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Google Flights / Hopper** | Same domain | We focus on alerts, not search; they focus on search + prediction |
| **Price Comparison Engine** | Same data collection | We alert on drops; comparison shows current prices side-by-side |
| **Event Subscription System** | Same pub/sub pattern | Flight deals are detected events; subscribers are notified |
| **Stock Price Alerting** | Same price monitoring pattern | Stocks update in real-time (WebSocket); flights poll periodically |
| **Notification System** | Same delivery pipeline | Our notifications are deal-triggered; general notifications are action-triggered |
| **Web Crawler** | Same external data collection | We call APIs; crawlers scrape HTML; both handle rate limits and failures |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Price DB** | TimescaleDB | InfluxDB / ClickHouse | InfluxDB: native TSDB; ClickHouse: fast analytical queries on price data |
| **Event streaming** | Kafka | AWS SQS / RabbitMQ | SQS: simpler, serverless; RabbitMQ: if lower latency needed |
| **Email provider** | SendGrid + SES | Mailgun / Postmark | Mailgun: transactional focus; Postmark: highest deliverability |
| **Circuit breaker** | Custom (Redis-backed) | Resilience4j / Hystrix | Resilience4j: Java native; Hystrix: Netflix (deprecated but proven) |
| **Subscription index** | Redis inverted index | Elasticsearch | Elasticsearch: if complex subscription filters (geo, multi-hop) |
| **Scheduling** | K8s CronJob + custom scheduler | Temporal / Apache Airflow | Temporal: durable workflows; Airflow: complex DAG scheduling |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Google Flights / Hopper Price Prediction Architecture
- Circuit Breaker Pattern (Martin Fowler)
- Notification Deduplication Strategies
- TimescaleDB for Time-Series Price Data
- Adaptive Rate Limiting for External APIs
- Event-Driven Architecture with Kafka
- Email Deliverability Best Practices (SendGrid / SES)
- Redis as Subscription Index for Pub/Sub Matching
