# Design a URL Shortener (aka.ms) — Hello Interview Framework

> **Question**: Design a URL shortener service (like aka.ms, bit.ly) that creates short URLs, redirects users, tracks analytics, supports custom aliases, and handles link expiration — at massive scale.
>
> **Asked at**: Microsoft, Amazon, Google, Meta, Stripe
>
> **Difficulty**: Medium | **Level**: Mid/Senior

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
1. **Shorten URL**: Given a long URL, generate a short URL (e.g., `aka.ms/abc123`). Support custom aliases (e.g., `aka.ms/windows11`).
2. **Redirect**: When a user visits the short URL, redirect (301/302) to the original long URL.
3. **Analytics**: Track click count, referrer, geographic location, device type, timestamp per short URL. Dashboard for URL owners.
4. **Expiration**: URLs can have optional expiration date. Expired URLs return 410 Gone.
5. **Custom Domains**: Support custom short domains (e.g., `aka.ms`, `go.microsoft.com`, `1drv.ms`).

#### Nice to Have (P1)
- Link-in-bio pages (aggregate multiple links)
- QR code generation for each short URL
- A/B testing (rotate destination URLs with traffic split)
- Password-protected links
- Preview page (show destination before redirecting)
- Bulk URL creation via API
- Webhook on click events
- Link editing (change destination after creation)

#### Below the Line (Out of Scope)
- Full marketing campaign management
- Email integration
- Phishing detection of destination URLs

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **URLs created/day** | 100 million | Enterprise + consumer usage |
| **Redirects/day** | 10 billion | Read-heavy workload (100:1 read:write) |
| **Redirect latency** | < 10ms p99 | Must be imperceptible |
| **Short URL length** | 6–8 characters | Short enough to share, long enough for uniqueness |
| **Availability** | 99.999% | Broken links = broken trust |
| **URL space** | 3.5 trillion (62^7) | Sufficient for decades |
| **Total URLs stored** | 100 billion over lifetime | Growing dataset |

### Capacity Estimation

```
Write (URL creation):
  100M/day → 1,157/sec avg, ~5K/sec peak

Read (redirects):
  10B/day → 115K/sec avg, ~500K/sec peak
  Read:Write ratio = 100:1 → heavily read-optimized

Storage:
  Per URL: ~500 bytes (shortCode, longURL, metadata)
  100B URLs × 500 bytes = 50 TB total
  Daily growth: 100M × 500 bytes = 50 GB/day

Bandwidth:
  Redirects: 500K/sec × 500 bytes (response header) = 250 MB/s
  Minimal — redirect is just a header, no body

Cache:
  Hot URLs (20% of URLs serve 80% of traffic):
  20B hot URLs × 500 bytes = 10 TB → too large for single cache
  Top 1% (1B URLs) in cache: 500 GB → fits in Redis cluster
  Cache hit rate target: 95%+
```

---

## 2️⃣ Core Entities

```
┌───────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  ShortURL          │     │  ClickEvent       │     │  User/Owner        │
│                    │     │                   │     │                    │
│ shortCode (PK)     │     │ clickId           │     │ userId             │
│ longURL            │     │ shortCode         │     │ email              │
│ customAlias        │     │ timestamp         │     │ apiKey             │
│ domain             │     │ referrer          │     │ urlCount           │
│ ownerId            │     │ userAgent         │     │ plan (free/pro)    │
│ createdAt          │     │ ipAddress         │     │ customDomains[]    │
│ expiresAt          │     │ country           │     └───────────────────┘
│ clickCount         │     │ city              │
│ isActive           │     │ device (mobile/   │
│ password (opt)     │     │  desktop/tablet)  │
│ metadata{}         │     │ os               │
└───────────────────┘     │ browser           │
                           └──────────────────┘
```

---

## 3️⃣ API Design

### Create Short URL
```
POST /api/v1/urls
Authorization: Bearer <token>

Request:
{
  "longUrl": "https://docs.microsoft.com/en-us/windows/release-health/status-windows-11",
  "customAlias": "windows11-status",       // optional
  "domain": "aka.ms",                       // optional, default
  "expiresAt": "2025-12-31T23:59:59Z",     // optional
  "metadata": { "campaign": "launch", "team": "windows" }
}

Response: 201 Created
{
  "shortCode": "windows11-status",
  "shortUrl": "https://aka.ms/windows11-status",
  "longUrl": "https://docs.microsoft.com/...",
  "createdAt": "2025-01-15T10:00:00Z",
  "expiresAt": "2025-12-31T23:59:59Z",
  "qrCodeUrl": "https://aka.ms/qr/windows11-status"
}
```

### Redirect (GET short URL)
```
GET https://aka.ms/windows11-status

Response: 301 Moved Permanently (or 302 Found)
Location: https://docs.microsoft.com/en-us/windows/release-health/status-windows-11
Cache-Control: private, max-age=0
```

### Get Analytics
```
GET /api/v1/urls/windows11-status/analytics?period=7d

Response: 200 OK
{
  "shortCode": "windows11-status",
  "totalClicks": 142857,
  "clicksByDay": [
    { "date": "2025-01-15", "clicks": 25000 },
    { "date": "2025-01-14", "clicks": 22000 }
  ],
  "topCountries": [{ "country": "US", "clicks": 80000 }, ...],
  "topReferrers": [{ "referrer": "twitter.com", "clicks": 35000 }, ...],
  "deviceBreakdown": { "mobile": 55, "desktop": 40, "tablet": 5 }
}
```

---

## 4️⃣ Data Flow

### URL Creation

```
Client → POST /api/v1/urls → API Gateway
        │
        ▼
   URL Service:
     1. Authenticate user (API key / Azure AD)
     2. Validate long URL (format, not blacklisted)
     3. Generate short code:
        Custom alias? → check uniqueness in DB
        Auto-generate? → use ID generator (see deep dive)
     4. Write to URL DB (Cosmos DB):
        { shortCode: "abc123", longUrl: "https://...", ... }
     5. Invalidate cache (if updating existing URL)
     6. Return short URL to client
```

### URL Redirect

```
User visits https://aka.ms/abc123
        │
        ▼
   DNS: aka.ms → CDN / Load Balancer
        │
        ▼
   Redirect Service (stateless):
     1. Extract short code: "abc123"
     2. Cache lookup (Redis):
        HIT → get longUrl (0.5ms)
        MISS → query URL DB (5ms) → populate cache
     3. Check expiration: expired? → return 410 Gone
     4. Return 301 redirect with Location header
     5. Async: publish click event to Kafka for analytics
        { shortCode, timestamp, userAgent, IP, referrer }
```

---

## 5️⃣ High-Level Design

```
┌────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │ Browser  │  │ API      │  │ Mobile   │  │ CLI      │         │
│  │ (user    │  │ (service │  │ App      │  │ Tool     │         │
│  │  clicks) │  │  creates)│  │          │  │          │         │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘         │
└───────┴──────────────┴────────────┴──────────────┴───────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────────────┐
│  Azure Front Door (Global LB + CDN edge for redirect caching)     │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
            ┌──────────────┼──────────────────┐
            ▼              ▼                  ▼
┌──────────────────┐ ┌──────────────┐ ┌──────────────────┐
│ Redirect Service │ │ URL Service  │ │ Analytics Service│
│ (Read path)      │ │ (Write path) │ │                  │
│                  │ │              │ │ Click aggregation│
│ Lookup short→long│ │ Create URL   │ │ Geo, device,     │
│ Return 301       │ │ Custom alias │ │ referrer stats   │
│ Log click event  │ │ Validate     │ │ Dashboard API    │
│                  │ │ Generate code│ │                  │
│ ~500K RPS        │ │ ~5K RPS      │ │                  │
└────────┬─────────┘ └──────┬───────┘ └──────┬───────────┘
         │                  │                │
         ▼                  ▼                ▼
┌────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐    │
│  │ Redis Cache  │  │ Cosmos DB    │  │ ClickHouse /         │    │
│  │              │  │              │  │ Azure Data Explorer  │    │
│  │ shortCode →  │  │ URL mappings │  │                      │    │
│  │ longUrl      │  │ (partition:  │  │ Click analytics      │    │
│  │              │  │  shortCode)  │  │ Time-series data     │    │
│  │ Hot URLs     │  │              │  │ Aggregations         │    │
│  │ 95% hit rate │  │ User data    │  │                      │    │
│  └──────────────┘  └──────────────┘  └──────────────────────┘    │
│                                                                    │
│  ┌──────────────┐  ┌──────────────┐                               │
│  │ Kafka        │  │ ID Generator │                               │
│  │              │  │ Service      │                               │
│  │ Click events │  │              │                               │
│  │ stream       │  │ Snowflake /  │                               │
│  │              │  │ Counter-based│                               │
│  └──────────────┘  └──────────────┘                               │
└────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Short Code Generation — Uniqueness at Scale

**Problem**: Generate 100M unique short codes per day. Codes must be short (7 chars), unpredictable, and collision-free.

**Solution: Base62 Encoding of Unique IDs**

```
Approach: Counter-Based ID → Base62 Encode

ID Generator Service:
  Distributed counter using:
    Option A: Snowflake-like IDs (timestamp + machine + sequence)
    Option B: Range-based counters (each server claims range from DB)
    Option C: Redis INCR (atomic, fast)

  Using Range-Based Counters:
    Server 1 claims range: [1, 1,000,000]
    Server 2 claims range: [1,000,001, 2,000,000]
    Each server increments locally (no coordination)
    When range exhausted → claim next range from Cosmos DB
    
    Coordination: INCR on a counter document with atomic operations
    Failure: if server crashes with unused IDs → small waste, acceptable

Base62 Encoding:
  Characters: [a-z, A-Z, 0-9] = 62 characters
  7 characters: 62^7 = 3.52 trillion possible codes
  At 100M/day: lasts ~96 years ✓
  
  encode(12345678) → "dnh6a" (5 chars)
  encode(999999999999) → "fXGh7Bz" (7 chars)

  Implementation:
    BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    
    function encode(id):
      code = ""
      while id > 0:
        code = BASE62[id % 62] + code
        id = id / 62
      return code.padStart(7, '0')  // pad to 7 chars

Custom Aliases:
  User provides alias: "windows11-status"
  Check uniqueness: GET from DB → if exists, return 409 Conflict
  No collision possible (user chooses, uniqueness enforced by DB)
  Reserved words filter: block "admin", "api", "login", etc.

Why not random hash?
  MD5/SHA → collision risk, need to check DB every time
  Sequential counter → guaranteed unique, no collisions, O(1)
  Obfuscation: shuffle the counter mapping to avoid predictable patterns
```

### Deep Dive 2: Read Optimization — Serving 500K Redirects/sec

**Problem**: 500K redirects/sec. Each redirect must resolve in <10ms. How to serve this at scale?

**Solution: Multi-Layer Caching + Stateless Redirect Servers**

```
Caching Strategy (3 layers):

Layer 1 — CDN Edge Cache (Azure Front Door):
  For extremely popular URLs (top 0.1%):
    Cache the 301 redirect response at CDN edge
    Cache-Control: public, max-age=3600 (for stable URLs)
    TTL: 1 hour (adjustable)
    Hit rate: 30-40% of all requests
  
  Note: analytics may be under-counted (CDN doesn't report every redirect)
  Solution: use 302 (no cache) for URLs where analytics matter

Layer 2 — Application Redis Cache:
  All recently/frequently accessed URLs
  Key: shortCode → { longUrl, expiresAt, isActive }
  Cache size: 500 GB → ~1 billion URLs cached
  Hit rate: 60% of CDN-miss requests
  
  Cache population: on first redirect (cache-aside)
  Cache eviction: LRU with TTL 24 hours
  Cache invalidation: on URL update/delete → DEL shortCode

Layer 3 — Database (Cosmos DB):
  For cache misses (cold URLs, first access)
  Partition key: shortCode (excellent distribution)
  Point read: < 5ms with SSD
  
  Hit rate: remaining 5-10% of requests

Combined:
  Request flow:
    CDN edge hit: 35% → 1ms
    Redis hit: 60% → 2ms  
    Cosmos DB hit: 5% → 8ms
    
  Weighted average latency: 0.35×1 + 0.60×2 + 0.05×8 = 1.95ms ✓

Redirect Server (stateless):
  No session, no state → horizontal scaling
  Auto-scale from 50 to 500 instances based on RPS
  Each instance: ~10K RPS capacity
  Total: 500 instances × 10K = 5M RPS capacity (10x headroom)
```

### Deep Dive 3: Click Analytics Pipeline

**Problem**: 10 billion clicks/day. We need per-URL analytics (clicks by day, country, device, referrer) with <5s freshness. Storing each click individually would be 10B rows/day.

**Solution: Streaming Aggregation with Pre-Computed Rollups**

```
Analytics Pipeline:

Click happens → Redirect Service → publish to Kafka
  { shortCode, timestamp, ip, userAgent, referrer }
        │
        ▼
  Kafka topic: "click-events" (partitioned by shortCode)
        │
        ├──▶ Stream Processor (Flink / Azure Stream Analytics):
        │     Real-time aggregation:
        │     
        │     1. Per-URL per-minute counter:
        │        key: (shortCode, minute_bucket)
        │        INCR click count
        │     
        │     2. Per-URL per-country counter:
        │        GeoIP lookup on IP → country
        │        key: (shortCode, country, day)
        │        INCR
        │     
        │     3. Per-URL per-device counter:
        │        Parse User-Agent → device type
        │        key: (shortCode, device, day)
        │        INCR
        │     
        │     Write pre-aggregated counters to ClickHouse / ADX
        │
        └──▶ Cold Storage (Azure Blob):
              Raw click events → Parquet files
              For ad-hoc analysis, debugging
              Retained 90 days

ClickHouse Tables:
  clicks_by_day:    (shortCode, date, clickCount)
  clicks_by_country: (shortCode, date, country, clickCount)
  clicks_by_device:  (shortCode, date, device, clickCount)
  clicks_by_referrer: (shortCode, date, referrer, clickCount)

Query: "clicks for aka.ms/windows11 in last 7 days by country"
  SELECT date, country, SUM(clickCount)
  FROM clicks_by_country
  WHERE shortCode = 'windows11' AND date >= today() - 7
  GROUP BY date, country

Real-time click count (for display on dashboard):
  Redis: INCR clicks:{shortCode} (total)
  Updated by stream processor every second
  Atomic, fast, eventually consistent with analytics DB

Storage savings:
  Raw: 10B clicks × 200 bytes = 2 TB/day
  Aggregated: 100M URLs × 5 dimensions × 50 bytes = 25 GB/day
  80x compression via pre-aggregation!
```
