# Design a Price Drop Tracker (CamelCamelCamel) - Hello Interview Framework

> **Question**: Design a price tracking system like CamelCamelCamel that allows users to view historical price data for e-commerce products and set up alerts to be notified when prices drop below specified thresholds. Users paste a product URL, install a browser extension to add items, and receive email or push notifications when deals appear.
>
> **Asked at**: Meta
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

## 1️⃣ Requirements

### Functional (P0)
1. **Product Tracking**: Users add products by URL (Amazon, Walmart, Best Buy). System scrapes/polls price data periodically.
2. **Price History**: Store and display historical price charts for each product. Show current, lowest, highest, and average prices.
3. **Price Alerts**: Users set target price thresholds per product. Get notified (email/push) when price drops to or below target.
4. **Product Dedup**: Multiple users tracking same product → single product entry, shared price data, individual alert thresholds.
5. **External Data Ingestion**: Poll e-commerce APIs/product pages for price updates. Handle rate limits, CAPTCHAs, page changes gracefully.
6. **Notification Dedup**: Don't re-alert for same price drop. Alert once per threshold crossing, not on every poll.

### Functional (P1)
- Browser extension for one-click "Track this product"
- Price trend predictions (ML: "price likely to drop next week")
- Wishlist / product collections
- Price comparison across retailers for same product
- Daily/weekly price digest emails

### Non-Functional Requirements
| Attribute | Target |
|-----------|--------|
| **Price Freshness** | Updated every 1-6 hours per product (based on popularity) |
| **Alert Latency** | < 30 minutes from price drop to notification |
| **Availability** | 99.9% for user-facing, 99.5% for scraping |
| **Scale** | 50M tracked products, 10M users, 100M alerts |
| **Dedup** | Zero duplicate notifications per alert threshold crossing |
| **Storage** | 5 years of price history per product |

### Capacity Estimation
```
Products: 50M tracked, 5M actively monitored (polled regularly)
Users: 10M total, 2M with active alerts
Alerts: 100M alert rules (avg 10 per active user)

Price polling: 5M products / 4 hours avg interval = ~350 polls/sec
  Each poll: scrape or API call → ~2 KB response
  Data: 350 × 2 KB = 700 KB/sec = ~60 GB/day

Price history: 50M products × 4 data points/day × 365 × 5 years × 50 bytes = ~18 TB
Product metadata: 50M × 1 KB = 50 GB
Alert rules: 100M × 200 bytes = 20 GB
Notifications: ~500K/day (0.5% of alerts trigger)
```

---

## 2️⃣ Core Entities

```java
public class Product {
    String productId;           // Internal UUID
    String externalUrl;         // "https://amazon.com/dp/B09V3KXJPB"
    String retailer;            // AMAZON, WALMART, BESTBUY
    String externalId;          // ASIN: "B09V3KXJPB"
    String title;
    String imageUrl;
    String category;
    int currentPriceCents;      // Current price (last polled)
    int lowestPriceCents;       // All-time low
    int highestPriceCents;      // All-time high
    int avgPriceCents;          // Historical average
    int trackerCount;           // Number of users tracking this
    Instant lastPolledAt;
    int pollingIntervalMin;     // Adaptive: 60-360 min
    ProductStatus status;       // ACTIVE, UNAVAILABLE, DISCONTINUED
}

public class PriceRecord {
    String productId;
    int priceCents;
    String retailer;
    String seller;              // "Amazon" vs "3rd-party seller"
    boolean inStock;
    Instant recordedAt;
}

public class PriceAlert {
    String alertId;
    String userId;
    String productId;
    int targetPriceCents;       // Notify when price <= this
    AlertType type;             // BELOW_THRESHOLD, ANY_DROP, PERCENT_DROP
    double percentDrop;         // For PERCENT_DROP: alert if drops > X%
    AlertStatus status;         // ACTIVE, TRIGGERED, PAUSED, EXPIRED
    Instant lastNotifiedAt;     // For dedup: don't re-alert within 24h
    Instant createdAt;
}
public enum AlertType { BELOW_THRESHOLD, ANY_DROP, PERCENT_DROP, ALL_TIME_LOW }

public class User {
    String userId;
    String email;
    boolean pushEnabled;
    String pushToken;
    UserTier tier;              // FREE (5 products), PREMIUM (unlimited)
    int trackedProductCount;
}

public class Notification {
    String notificationId;
    String userId;
    String alertId;
    String productId;
    int oldPriceCents;
    int newPriceCents;
    NotificationStatus status;  // PENDING, SENT, FAILED
    String channel;             // EMAIL, PUSH, BOTH
    Instant sentAt;
}

public class RetailerConfig {
    String retailerId;          // "amazon", "walmart"
    String scrapeStrategy;      // API, HTML_SCRAPE, AFFILIATE_API
    int rateLimitPerMin;
    int pollingBatchSize;
    CircuitBreakerState state;
}
```

---

## 3️⃣ API Design

### 1. Track Product
```
POST /api/v1/products/track
Request: { "url": "https://amazon.com/dp/B09V3KXJPB", "target_price": 4999 }
Response (201): {
  "product_id": "prod_001", "title": "AirPods Pro 2", "current_price": 17999,
  "lowest_price": 15999, "alert_id": "alert_001"
}
```

### 2. Get Price History
```
GET /api/v1/products/{product_id}/prices?period=90d&resolution=daily
Response: {
  "product_id": "prod_001", "title": "AirPods Pro 2",
  "current": 17999, "lowest": 15999, "highest": 24999, "average": 19500,
  "history": [{ "date": "2025-01-01", "price": 18999 }, { "date": "2025-01-02", "price": 17999 }, ...]
}
```

### 3. Create/Update Alert
```
POST /api/v1/alerts
Request: { "product_id": "prod_001", "type": "BELOW_THRESHOLD", "target_price": 14999 }

PATCH /api/v1/alerts/{alert_id}
Request: { "target_price": 15999 }
```

### 4. List My Tracked Products
```
GET /api/v1/users/me/products?sort=price_drop_pct
Response: { "products": [{ "product_id": "prod_001", "title": "AirPods Pro 2", "current": 17999, "your_target": 14999, "drop_pct": -12, ... }] }
```

### 5. Delete Alert / Stop Tracking
```
DELETE /api/v1/alerts/{alert_id}
DELETE /api/v1/products/{product_id}/track
```

---

## 4️⃣ Data Flow

### Price Polling Pipeline
```
1. Scheduler: for each product due for polling (based on interval + priority)
   → Produce to Kafka "poll-requests" topic
2. Scraper Workers consume poll requests:
   a. Check retailer circuit breaker (if OPEN → skip/delay)
   b. Fetch price: call retailer API or scrape product page
   c. Parse price, availability, seller info
   d. Publish to Kafka "price-updates" topic
3. Price Writer:
   a. Write PriceRecord to Price History DB (TimescaleDB)
   b. Update Product.currentPriceCents, lowestPriceCents in Products DB
   c. Update Redis cache: price:{product_id} = current price
4. Alert Evaluator:
   a. Consume "price-updates"
   b. For each price change: find matching alerts
   c. If price <= alert.targetPriceCents AND not recently notified → trigger
   d. Produce to "notifications" topic
5. Notification Sender:
   a. Render email/push template
   b. Send via SendGrid/Firebase
   c. Update alert.lastNotifiedAt (dedup)
```

### Alert Evaluation Logic
```
For each price update (product_id, new_price):
  1. Query alert index: alerts WHERE product_id = ? AND status = ACTIVE
  2. For each alert:
     switch(alert.type):
       BELOW_THRESHOLD: new_price <= alert.targetPriceCents?
       ANY_DROP: new_price < previous_price?
       PERCENT_DROP: (previous - new) / previous > alert.percentDrop?
       ALL_TIME_LOW: new_price < product.lowestPriceCents?
  3. Dedup check: alert.lastNotifiedAt > 24 hours ago? (don't spam)
  4. If trigger → create notification event
```

---

## 5️⃣ High-Level Design

```
┌────────────────────────────────────────────────────────────────────┐
│                        CLIENTS                                      │
│  Web App / Browser Extension / Mobile App                          │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Auth, Rate Limit)                    │
└──────────┬──────────────────────────┬───────────────────────────────┘
           │ User ops                 │ Product/Price queries
           ▼                          ▼
┌──────────────────┐     ┌──────────────────────────────────────────┐
│ PRODUCT SERVICE  │     │ PRICE SERVICE                             │
│ • Track product  │     │ • Price history queries                   │
│ • Create alert   │     │ • Current price cache (Redis)             │
│ • List tracked   │     │ • Chart data API                          │
│ Pods: 20         │     │ Pods: 20                                  │
└────────┬─────────┘     └──────────────────────────────────────────┘
         │
    ┌────┼─────────────────────────┐
    ▼    ▼                         ▼
┌────────┐ ┌──────────┐  ┌──────────────┐
│Products│ │  Alerts   │  │ Price History │
│  DB    │ │  DB       │  │  DB           │
│Postgres│ │ Postgres  │  │ TimescaleDB   │
│ 50 GB  │ │ 20 GB     │  │ ~18 TB        │
└────────┘ └──────────┘  └──────────────┘

                    ▼ Kafka
┌────────────────────────────────────────────────────────────────────┐
│  Topic: "poll-requests"   (scheduled product polls)                │
│  Topic: "price-updates"   (new prices from scrapers)               │
│  Topic: "notifications"   (alert triggers for email/push)          │
└────┬─────────────────────────┬──────────────────┬──────────────────┘
     │                         │                  │
┌────▼──────────┐    ┌────────▼──────┐   ┌──────▼───────────┐
│ SCRAPER       │    │ ALERT         │   │ NOTIFICATION     │
│ WORKERS       │    │ EVALUATOR     │   │ SENDER           │
│               │    │               │   │                  │
│ • Fetch prices│    │ • Match alerts│   │ • Email (SendGrid)│
│ • Handle rate │    │ • Dedup check │   │ • Push (Firebase) │
│   limits      │    │ • Trigger     │   │ • Record delivery │
│ • Circuit     │    │               │   │                  │
│   breaker     │    │ Pods: 10-20   │   │ Pods: 10-20     │
│ Pods: 50-100  │    └───────────────┘   └─────────────────┘
└───────────────┘

┌────────────────────────────────────────────────────────────────────┐
│ SCHEDULER: adaptive polling                                         │
│ • Hot products (>1000 trackers): poll every 1 hour                 │
│ • Warm products (100-1000): poll every 4 hours                     │
│ • Cold products (<100): poll every 6 hours                         │
│ • Distribute polls evenly (no burst)                               │
└────────────────────────────────────────────────────────────────────┘
```

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Product Service** | CRUD products, alerts, user tracking | Go on K8s | 20 pods |
| **Price Service** | Price history queries, chart data | Go on K8s | 20 pods |
| **Scraper Workers** | Fetch prices from retailers | Go on K8s | 50-100 pods (I/O bound) |
| **Alert Evaluator** | Match price updates against alert rules | Go on K8s | 10-20 pods |
| **Notification Sender** | Send email/push alerts | Go on K8s | 10-20 pods |
| **Products DB** | Product metadata, user tracking | PostgreSQL | ~50 GB |
| **Alerts DB** | Alert rules, notification history | PostgreSQL | ~20 GB |
| **Price History DB** | Time-series price data | TimescaleDB | ~18 TB (5 years) |
| **Price Cache** | Current prices for fast lookup | Redis | ~5 GB |
| **Kafka** | Event pipeline between components | Apache Kafka | 5+ brokers |

---

## 6️⃣ Deep Dives

### DD1: Product Deduplication — One Product Entry, Many Trackers

**Problem**: 1000 users paste the same Amazon URL. Must not create 1000 product entries.

```
Dedup strategy:
  1. Extract canonical product ID from URL: amazon.com/dp/B09V3KXJPB → ASIN: B09V3KXJPB
  2. Check if (retailer=AMAZON, external_id=B09V3KXJPB) exists in Products DB
  3. If exists → link user's alert to existing product; increment trackerCount
  4. If new → create product + schedule first price poll

URL normalization:
  Strip: tracking params (?tag=, &ref=), URL shorteners, locale variants
  Canonical: https://amazon.com/dp/{ASIN}
  Walmart: extract product ID from walmart.com/ip/{ID}
```

### DD2: Adaptive Polling — Balance Freshness vs Cost

**Problem**: 50M products, but scraping costs money (compute, IP rotation, API quotas). Can't poll all products every hour.

```
Adaptive intervals based on popularity + price volatility:
  Hot (>1000 trackers, volatile): every 1 hour
  Warm (100-1000 trackers): every 4 hours
  Cold (<100 trackers): every 6 hours
  Dormant (0 trackers, kept for history): every 24 hours

Smart adjustments:
  - If price changed in last poll → increase frequency (deal emerging)
  - If price stable for 7 days → decrease frequency
  - During known sale events (Black Friday, Prime Day) → all products every 1 hour

Budget: 5M active products / 4h avg = 350 polls/sec
  With 50 scraper pods × 10 requests/sec each = 500/sec capacity → headroom
```

### DD3: Scraping Resilience — Circuit Breakers, Retries, IP Rotation

**Problem**: Retailers block scrapers, change page layouts, return CAPTCHAs.

```
Per-retailer circuit breaker:
  5 consecutive failures → OPEN for 5 minutes → HALF_OPEN (try one)

Retry: 3 attempts with exponential backoff (1s, 5s, 30s)

IP rotation: pool of proxy IPs, rotate on 429/403 responses

Page change detection:
  - If price parser returns null → flag product for manual review
  - If >10% of products from retailer return null → alert on-call (likely page layout change)
  - Separate parser per retailer, versioned, A/B testable

Fallback: if scraping fails, try affiliate API (Amazon Product Advertising API)
```

### DD4: Alert Evaluation — Matching Price Updates to 100M Alerts

**Problem**: Each price update must be checked against all alerts for that product. Some products have 10K+ alerts.

```
Alert index in Redis:
  Key: alerts:{product_id} → SET of alert_ids
  On price update: SMEMBERS alerts:{product_id} → get all alert IDs
  For each: HGETALL alert:{alert_id} → check threshold

Performance: avg 20 alerts/product → 20 hash lookups = < 1ms
  Hot product (10K alerts): ~50ms with pipelining

Dedup: alert.lastNotifiedAt must be > 24 hours ago (Redis key: notified:{alert_id} TTL 24h)
```

### DD5: Price History Storage — 18 TB Efficiently Queried

**Problem**: 50M products × 5 years of daily price data. Users view 90-day and 1-year charts.

```
TimescaleDB (PostgreSQL extension for time-series):
  Hypertable: price_history, partitioned by time (monthly chunks)
  Indexes: (product_id, recorded_at DESC)

Query patterns:
  "Last 90 days for product X": single partition scan, < 50ms
  "All-time low": pre-computed on Product entity (updated on each price write)

Rollups: after 1 year, downsample to daily min/max/avg (reduce storage 4x)
Retention: raw data 1 year, daily rollups 5 years
```

### DD6: Notification Dedup & User Experience

**Problem**: Price fluctuates around threshold ($50.00 → $49.99 → $50.01 → $49.98). Don't send 3 alerts.

```
Dedup rules:
  1. After alert triggers: set cooldown of 24 hours (Redis TTL key)
  2. During cooldown: even if price drops further → don't re-alert
  3. If price goes ABOVE threshold then drops again → new trigger allowed
  4. State machine: ACTIVE → TRIGGERED (24h cooldown) → ACTIVE (if price > threshold) → TRIGGERED...

Notification content:
  Subject: "🔔 Price Drop: AirPods Pro 2 now $159.99 (was $179.99, your target: $149.99)"
  Body: price chart, buy button, current vs target, historical context
```

### DD7: Product Discovery & URL Parsing

**Problem**: Users paste URLs in many formats. Must extract product ID and retailer reliably.

```
URL parsers per retailer:
  Amazon: /dp/{ASIN}, /gp/product/{ASIN}, /exec/obidos/ASIN/{ASIN}, amzn.to/{shortcode}
  Walmart: /ip/{product_id}, /ip/{slug}/{product_id}
  Best Buy: /site/{slug}/{sku}.p?skuId={sku}

Process:
  1. Normalize URL (strip params, resolve redirects for shortlinks)
  2. Match against retailer URL patterns (regex per retailer)
  3. Extract canonical product ID
  4. Check if product exists in DB
  5. If new: fetch product metadata (title, image, current price) in first poll
```

### DD8: Free vs Premium Tiers — Quota Management

**Problem**: Free users limited to 5 tracked products. Premium unlimited. Must enforce without blocking premium experience.

```
Quota enforcement:
  On track request: check user.trackedProductCount vs tier limit
  FREE: max 5 products, email only, daily digest (no instant alerts)
  PREMIUM: unlimited products, email + push, instant alerts

Quota stored in Redis: quota:{user_id} = { tracked: 3, limit: 5, tier: "FREE" }
  Atomic increment on track, decrement on untrack
  Check before creating new tracking → 403 if exceeded

Conversion prompt: when free user hits limit, show upgrade CTA
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Product dedup** | Canonical ID extraction from URL | One product entry per physical product; shared price data |
| **Adaptive polling** | Popularity-based intervals (1-6 hours) | Balance freshness vs scraping cost; hot products polled more |
| **Scraping resilience** | Circuit breaker + IP rotation + affiliate API fallback | Graceful degradation; no single retailer failure blocks system |
| **Alert matching** | Redis inverted index (product → alert IDs) | < 1ms per price update for typical products |
| **Notification dedup** | 24h cooldown after trigger + state machine | No spam from price fluctuations around threshold |
| **Price storage** | TimescaleDB with monthly partitions + rollups | Efficient time-series queries; 5-year retention affordable |
| **Tier management** | Redis quota counters + DB enforcement | Atomic, fast quota checks; enables freemium model |

## Interview Talking Points

1. **"Product dedup: extract canonical ID from URL, share price data across all trackers"** — Amazon ASIN extraction from 5+ URL formats. One product entry, many user alerts. Reduces polling from 50M to 5M active products.

2. **"Adaptive polling: 1h for hot products, 6h for cold, with smart acceleration"** — Prioritize by tracker count and price volatility. Increase frequency when price trending down. Budget: 350 polls/sec with 500/sec capacity.

3. **"Alert evaluation: Redis inverted index, < 1ms matching per price update"** — product_id → SET of alert_ids. For each alert: check threshold + dedup. 24h cooldown prevents spam from price fluctuations.

4. **"Scraping resilience: per-retailer circuit breaker + IP rotation + affiliate API fallback"** — 5 failures → OPEN. Retry with backoff. IP rotation on 429. If scraping fails → fall back to Amazon Product Advertising API.

5. **"Notification dedup: 24h cooldown + threshold re-crossing state machine"** — After trigger: 24h cooldown (Redis TTL). If price goes above threshold then drops again → new trigger allowed. Zero duplicate notifications.

---

## 🔗 Related Problems
| Problem | Key Difference |
|---------|---------------|
| **CamelCamelCamel / Honey** | Our exact design |
| **Flight Deal Alerts** | Flights use APIs; products use scraping + APIs |
| **Stock Price Alerting** | Real-time (WebSocket); products poll periodically |
| **Web Crawler** | We scrape specific pages; crawlers discover new pages |

## 🔧 Technology Alternatives
| Component | Chosen | Alternative |
|-----------|--------|------------|
| **Price DB** | TimescaleDB | InfluxDB / ClickHouse |
| **Scraping** | Custom + proxies | ScrapingBee / Bright Data (managed) |
| **Notifications** | SendGrid + Firebase | AWS SES + SNS |
| **Alert index** | Redis inverted index | PostgreSQL with indexes |
| **Scheduling** | Kafka + custom | Temporal / Celery |

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Time**: 45-60 min
**References**: CamelCamelCamel Architecture, Web Scraping Best Practices, TimescaleDB for Price History, Adaptive Polling Strategies