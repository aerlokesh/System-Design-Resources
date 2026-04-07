# Design Amazon Product Search — Hello Interview Framework

> **Question**: Design a highly scalable product search system for an e-commerce platform like Amazon. Users type queries ("iphone 13 pro max"), apply filters (brand, price, rating), and get personalized, relevant results with sub-second latency. Support auto-complete, spell correction, faceted search, and real-time index updates for 300M+ products.
>
> **Asked at**: Amazon, Google, Meta, Microsoft
>
> **Difficulty**: Hard | **Level**: Senior/Staff

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
1. **Text Search**: Free-text search across product titles, descriptions, brands. Support exact match, fuzzy match, phrase match, synonym expansion ("laptop" → "notebook computer").
2. **Filtering & Faceting**: Category hierarchy (Electronics > Phones), price ranges, brand, rating (4+ stars), Prime eligible, in-stock. Dynamic facet counts that update as filters are applied.
3. **Sorting**: By relevance (default), price (low/high), customer reviews, newest, best sellers.
4. **Auto-Complete**: Type-ahead suggestions after 2+ characters — popular queries, product names, category suggestions. < 50ms latency.
5. **Spell Correction**: "iphne" → "Did you mean: iphone?" Auto-correct common misspellings (1-2 character edits).
6. **Personalized Ranking**: Search results personalized by user's search history, browsing behavior, purchase history, and demographic signals.
7. **Sponsored Products**: Ad placements within organic results, clearly marked, relevance-ranked, with click tracking and billing.

#### Nice to Have (P1)
- Visual search (search by image)
- Voice search integration
- Multi-language support (15+ languages)
- Cross-language search (English query → Spanish products)
- Related searches / "Customers also searched for"
- Real-time trending searches
- A/B testing framework for ranking models

#### Below the Line (Out of Scope)
- Product catalog CRUD (assume separate Catalog Service)
- Shopping cart / checkout / payments
- Reviews & ratings management
- Recommendation engine (separate from search)
- Seller management / marketplace operations

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Search Latency** | P50 < 50ms, P95 < 100ms, P99 < 200ms | Users abandon search if > 300ms; Google/Amazon benchmark |
| **Auto-Complete Latency** | P99 < 50ms | Must feel instant per keystroke |
| **Index Freshness** | < 1 minute for price/inventory; < 5 min for new products | Stale prices erode trust; inventory must be near real-time |
| **Throughput** | 70K QPS peak (500M searches/day, 12× spike on Prime Day) | Must handle 10× traffic during sales events |
| **Availability** | 99.99% (52 min downtime/year) | Search is the primary revenue driver — downtime = lost sales |
| **Scale** | 300M products, 200M active (in-stock) | Growing to 1B in 5 years |
| **Consistency** | Eventual for catalog (5-min lag OK); strong for inventory | Stale description is fine; overselling is not |
| **Relevance** | CTR > 50% top 3 results; null-result rate < 5% | Search quality directly drives conversion |

### Capacity Estimation

```
Users & Traffic:
  DAU: 100M users, 5 searches/user/day = 500M searches/day
  QPS: 500M / 86400 ≈ 5,800 avg → 70K peak (12× Prime Day)
  Auto-complete: ~75M/day = 868 QPS (10K peak)

Products:
  Total: 300M, Active (in-stock): 200M
  New products/day: 100K
  Price updates/day: 10M
  Inventory updates: up to 100K/sec during flash sales

Per Search Request:
  Inbound: ~550 bytes (query + headers)
  Outbound: ~102 KB (48 products × 2KB + facets + metadata)
  
Bandwidth:
  Inbound: 500M × 550B = 275 GB/day (3.2 MB/s avg, 38 MB/s peak)
  Outbound: 500M × 102KB = 51 TB/day (590 MB/s avg, 7 GB/s peak)

Storage:
  Product catalog (Postgres): 300M × 4.5KB = 1.35 TB (with replicas: 15 TB)
  Search index (Elasticsearch): 200M × 6KB = 1.2 TB (with replicas: 3.1 TB/region)
  Auto-complete index: 100M queries × 62B = 6.2 GB (negligible)
  User personalization: 100M users × 22KB = 2.2 TB
  Click logs: 1.5B events/day × 166B = 249 GB/day (7.5 TB/30-day hot)
  Total hot: ~40 TB | Cold (S3, 10-year logs): ~900 TB

Infrastructure per region:
  Elasticsearch: 30 data nodes + 3 master + 10 coordinating = 43 nodes
  API servers: 28 (5K QPS each)
  Ranking servers: 14 (10K rankings/sec each)
  Kafka: 6 brokers
  Postgres: primary + 5 read replicas
  Redis: 20 nodes (cache cluster)
  3 regions total: ~$160-180K/month optimized
```

---

## 2️⃣ Core Entities

### Entity 1: Product (Search Document)
```java
public class ProductSearchDocument {
    String productId;           // UUID
    String title;               // "Apple iPhone 13 Pro Max 256GB - Sierra Blue"
    String description;         // Full product description
    String brand;               // "Apple"
    List<String> categoryPath;  // ["Electronics", "Mobile Phones", "Smartphones"]
    int priceCents;             // 129900
    String currency;            // "USD"
    float rating;               // 4.7
    int reviewCount;            // 34521
    boolean inStock;            // true
    boolean primeEligible;      // true
    float popularityScore;      // 0.95 (normalized, based on sales velocity)
    List<String> imageUrls;     // CDN URLs for thumbnails
    Map<String, String> attrs;  // {"storage": "256GB", "color": "Sierra Blue"}
    Instant createdAt;
    Instant updatedAt;
}
```

### Entity 2: SearchQuery
```java
public class SearchQuery {
    String queryText;           // "iphone 13 pro max"
    List<String> filters;       // ["category:smartphones", "price:900-1200", "rating:4+"]
    String sortBy;              // "relevance" | "price_asc" | "price_desc" | "rating" | "newest"
    int limit;                  // 48
    String cursor;              // Opaque pagination cursor
    String userId;              // For personalization (nullable for anonymous)
    String locale;              // "en-US"
    String sessionId;           // For session-level personalization
}
```

### Entity 3: SearchResult
```java
public class SearchResult {
    List<ProductHit> products;          // Ranked product results
    Map<String, List<Facet>> facets;    // {"brand": [{"Apple": 2341}, ...], "price_range": [...]}
    long totalHits;                     // 12,847
    String nextCursor;                  // Pagination cursor
    SpellSuggestion spellSuggestion;    // "Did you mean: iphone 13 pro max?"
    List<String> relatedSearches;       // ["iphone 13 case", "iphone 14 pro max"]
    List<SponsoredProduct> sponsored;   // Ad placements
    int latencyMs;                      // 47
}
```

### Entity 4: AutoCompleteSuggestion
```java
public class AutoCompleteSuggestion {
    String text;                // "iphone 13 pro max"
    SuggestionType type;        // QUERY, PRODUCT, CATEGORY
    String productId;           // Only for PRODUCT type
    String thumbnailUrl;        // Only for PRODUCT type
    int popularity;             // For ranking suggestions
}

public enum SuggestionType { QUERY, PRODUCT, CATEGORY }
```

### Entity 5: UserSearchProfile
```java
public class UserSearchProfile {
    String userId;
    float[] userEmbedding;              // 256-dim vector (precomputed daily)
    Map<String, Float> categoryAffinity;// {"Electronics": 0.8, "Books": 0.3}
    Map<String, Float> brandAffinity;   // {"Apple": 0.9, "Samsung": 0.4}
    List<String> recentSearches;        // Last 100 queries
    List<String> recentPurchases;       // Last 50 purchased product IDs
    PriceRange pricePreference;         // Inferred from history
    Instant lastUpdated;
}
```

### Entity 6: ClickEvent (Analytics)
```java
public class ClickEvent {
    String eventId;
    String userId;
    String queryText;
    String productId;
    int positionInResults;      // 1-indexed position where shown
    String action;              // "CLICK", "ADD_TO_CART", "PURCHASE", "IMPRESSION"
    String sessionId;
    Instant timestamp;
}
```

---

## 3️⃣ API Design

### 1. Product Search
```
GET /api/v1/search?q=iphone+13+pro+max&category=electronics&price_min=900&price_max=1200&rating_min=4&brand=Apple&prime=true&sort=relevance&limit=48&cursor=eyJpZCI6MTIzNDV9

Headers:
  Authorization: Bearer {jwt}    (optional — anonymous search supported)
  Accept-Language: en-US

Response (200 OK):
{
  "products": [
    {
      "product_id": "prod_abc123",
      "title": "Apple iPhone 13 Pro Max 256GB - Sierra Blue",
      "brand": "Apple",
      "price_cents": 109900,
      "currency": "USD",
      "rating": 4.7,
      "review_count": 34521,
      "prime_eligible": true,
      "in_stock": true,
      "thumbnail_url": "https://cdn.amazon.com/images/prod_abc123/thumb.webp",
      "category_path": ["Electronics", "Mobile Phones", "Smartphones"]
    }
    // ... 47 more products
  ],
  "facets": {
    "brand": [
      {"value": "Apple", "count": 2341},
      {"value": "Samsung", "count": 1892},
      {"value": "Google", "count": 567}
    ],
    "price_range": [
      {"label": "Under $500", "count": 3421},
      {"label": "$500-$1000", "count": 5892},
      {"label": "$1000+", "count": 3534}
    ],
    "rating": [
      {"label": "4+ stars", "count": 8234},
      {"label": "3+ stars", "count": 10892}
    ]
  },
  "total_hits": 12847,
  "next_cursor": "eyJzY29yZSI6MC44NSwidGllIjoicHJvZF94eXoifQ==",
  "spell_suggestion": null,
  "related_searches": ["iphone 13 case", "iphone 14 pro max", "iphone 13 pro"],
  "latency_ms": 47
}
```

### 2. Auto-Complete
```
GET /api/v1/autocomplete?prefix=iph&limit=10

Response (200 OK):
{
  "suggestions": [
    {"text": "iphone 13 pro max", "type": "QUERY", "popularity": 98},
    {"text": "iphone 14", "type": "QUERY", "popularity": 95},
    {"text": "iphone charger", "type": "QUERY", "popularity": 87},
    {
      "text": "Apple iPhone 13 Pro Max",
      "type": "PRODUCT",
      "product_id": "prod_abc123",
      "thumbnail_url": "https://cdn.amazon.com/images/prod_abc123/thumb.webp",
      "popularity": 92
    },
    {"text": "Smartphones", "type": "CATEGORY", "popularity": 80}
  ],
  "latency_ms": 8
}
```

### 3. Spell Check (used internally by search, also exposed)
```
GET /api/v1/spellcheck?q=iphne+13+por+max

Response (200 OK):
{
  "original": "iphne 13 por max",
  "corrected": "iphone 13 pro max",
  "corrections": [
    {"original": "iphne", "corrected": "iphone", "confidence": 0.97},
    {"original": "por", "corrected": "pro", "confidence": 0.95}
  ]
}
```

### 4. Click Tracking (Fire-and-forget from client)
```
POST /api/v1/events/click

Request:
{
  "query": "iphone 13 pro max",
  "product_id": "prod_abc123",
  "position": 1,
  "action": "CLICK",
  "session_id": "sess_xyz789"
}

Response (202 Accepted):
{}
```

### 5. Trending Searches
```
GET /api/v1/trending?locale=en-US&limit=10

Response (200 OK):
{
  "trending": [
    {"query": "macbook pro m3", "trend_score": 9.8},
    {"query": "air jordan 1", "trend_score": 8.5},
    {"query": "ps5 slim", "trend_score": 7.9}
  ]
}
```

---

## 4️⃣ Data Flow

### Flow 1: Search Query → Results (Critical Path, < 50ms P50)
```
1. Client sends: GET /search?q=iphone+13+pro+max&category=electronics&rating_min=4
   ↓
2. API Gateway: Auth (JWT verify, 1ms) → Rate limit check (Redis, 1ms) → Route to Search Service
   ↓
3. Search Service (total budget: 50ms)
   a. Cache lookup (2ms):
      Key = hash(query + filters + user_id)
      → Redis GET → If HIT (40-50% for popular queries): return cached results, DONE
   
   b. Query preprocessing (3ms):
      - Tokenize: "iphone 13 pro max" → ["iphone", "13", "pro", "max"]
      - Spell check: lookup correction dictionary (Redis cache, 80% hit rate)
      - Synonym expansion: "phone" → ["phone", "smartphone", "mobile"]
      - If no tokens remain valid → return "Did you mean...?" suggestion
   
   c. Elasticsearch query (25ms):
      - Multi-match on title (3× boost), description (1×), brand (2×)
      - Filter: category=electronics, rating≥4, in_stock=true
      - Aggregations: brand counts, price ranges, rating buckets
      - Retrieve top 100 candidates (over-fetch for re-ranking)
   
   d. Ranking Service call (10ms):
      - Send 100 candidates + user context (embedding, history, session)
      - LightGBM model scores each candidate using 300+ features
      - Re-rank by predicted click+purchase probability
      - Apply diversity (MMR: don't show 48 identical phone cases)
      - Insert sponsored products at positions 1, 5, 12 (blended)
      - Return top 48
   
   e. Response formatting (3ms):
      - Attach CDN image URLs, format prices by locale
      - Build facet response from ES aggregations
      - Encode next_cursor from last result's sort values
   
   f. Cache store (2ms):
      - Redis SET with TTL=5 min for this (query, filters, user) combination
   ↓
4. Return JSON response to client

Total: 50ms P50, 100ms P95, 200ms P99
```

### Flow 2: Product Update → Search Index (Near Real-Time)
```
1. Product Service updates price in PostgreSQL
   ↓
2. Debezium CDC captures the WAL change
   → Publishes to Kafka topic "product-updates"
   → Partition key: product_id (ensures ordering per product)
   ↓
3. Search Indexer (Kafka consumer group, 20 consumers):
   a. Consume batch of 1000 events (or 1-second window, whichever first)
   b. Transform: DB row → ES document format
   c. Elasticsearch Bulk API: index 1000 docs in single request (~100ms)
   ↓
4. Elasticsearch refresh (automatic every 1 second):
   → Documents become searchable
   ↓
Total: product update → searchable in < 2 seconds (< 1 minute SLA)

For critical updates (price drops, out-of-stock):
  - Skip batching, index immediately
  - Total latency: < 500ms
```

### Flow 3: Auto-Complete (< 50ms end-to-end)
```
1. Client sends: GET /autocomplete?prefix=iph (on every keystroke after 2 chars)
   ↓
2. Cache lookup (2ms):
   Redis GET autocomplete:{prefix_hash}
   → Hit rate: 70%+ (popular prefixes cached for 1 hour)
   → If HIT: return immediately
   ↓
3. Elasticsearch autocomplete index query (8ms):
   - Edge n-gram analyzer: "iphone" indexed as ["ip", "iph", "ipho", "iphon", "iphone"]
   - Query "iph" matches all docs containing "iph" n-gram
   - Score by: 0.7 × popularity + 0.2 × personalization + 0.1 × trending
   - Return top 10
   ↓
4. Cache store (1ms): Redis SET with TTL=1 hour
   ↓
5. Return suggestions to client

Total: 10ms (cache miss), 3ms (cache hit)
```

### Flow 4: Click Event → ML Training Data Pipeline
```
1. Client sends: POST /events/click {query, product_id, position, action}
   ↓
2. API produces to Kafka topic "click-events" (fire-and-forget, async)
   → Response 202 Accepted immediately (< 5ms)
   ↓
3. Three independent consumers read from "click-events":
   
   a. Analytics Consumer → ClickHouse (real-time dashboards)
      - Insert into click_events table (partitioned by date)
      - Powers: CTR dashboards, search quality metrics
   
   b. ML Training Consumer → S3 (batch training data)
      - Join: (query, product, position, clicked/purchased) 
      - Label: clicked AND purchased = weight 10, clicked = weight 1, shown but not clicked = weight -1
      - Output: training data for weekly LightGBM model retrain
   
   c. Personalization Consumer → Redis (real-time user profile)
      - Update user's recent searches, category affinity, brand affinity
      - Immediate effect: next search uses updated profile
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                            │
│  [Web Browser]  [Mobile App]  [Alexa Voice]  [Third-Party API]                │
└──────────────────────────┬──────────────────────────────────────────────────────┘
                           │ HTTPS
                           ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                     CDN + EDGE (CloudFront)                                     │
│  • Cache: static assets, popular autocomplete responses                        │
│  • DDoS protection (AWS Shield)                                                │
│  • TLS termination, geographic routing                                         │
└──────────────────────────┬──────────────────────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │ US-EAST          │ EU-WEST          │ AP-SOUTH
        └──────────────────┼──────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY (Kong / AWS ALB)                                │
│  • JWT auth • Rate limiting (Redis) • Path routing • Request logging           │
│  • /search → Search Service   • /autocomplete → Autocomplete Service           │
│  • /events → Event Collector  • /trending → Trending Service                   │
└──────┬──────────────────────────┬──────────────────────────┬───────────────────┘
       │                          │                          │
       ▼                          ▼                          ▼
┌──────────────────┐   ┌──────────────────┐   ┌────────────────────────┐
│  SEARCH SERVICE  │   │ AUTOCOMPLETE SVC │   │   EVENT COLLECTOR      │
│  (Go, 28 pods)   │   │  (Go, 10 pods)   │   │   (Go, 10 pods)       │
│                  │   │                  │   │                        │
│ • Query parse    │   │ • Prefix search  │   │ • Click/impression     │
│ • Spell check    │   │ • Popular query  │   │ • Produce to Kafka     │
│ • ES query build │   │ • Product suggest│   │ • Fire-and-forget      │
│ • Ranking call   │   │ • Category match │   │                        │
│ • Facet assembly │   │                  │   │                        │
│ • Sponsored ads  │   │                  │   │                        │
└──────┬───────────┘   └────────┬─────────┘   └──────────┬─────────────┘
       │                        │                         │
       ▼                        │                         │
┌──────────────────────┐        │                         │
│  RANKING SERVICE     │        │                         │
│  (Python+Go, 14 pods)│        │                         │
│                      │        │                         │
│ • LightGBM inference │        │                         │
│ • 300+ features      │        │                         │
│ • Personalization    │        │                         │
│ • Diversity (MMR)    │        │                         │
│ • Sponsored blending │        │                         │
└──────┬───────────────┘        │                         │
       │                        │                         │
       ▼                        ▼                         ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                        CACHE LAYER (Redis Cluster, 20 nodes)                   │
│                                                                                │
│  search:{query_hash} → cached results (TTL: 5 min)                            │
│  autocomplete:{prefix} → suggestions (TTL: 1 hour)                            │
│  user:{user_id}:profile → personalization features (TTL: 24 hours)            │
│  spell:{word} → correction (TTL: 24 hours)                                    │
│  trending:queries → top 50 trending (TTL: 15 min)                             │
│  product:{id}:price → real-time price (TTL: 60 sec)                           │
└────────────────────────────────────────────────────────────────────────────────┘
       │                        │
       ▼                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│               ELASTICSEARCH CLUSTER (per region)                               │
│                                                                                │
│  Master Nodes (3): cluster coordination, shard allocation                     │
│  Data Nodes (30): store data, execute queries (64GB RAM, 2TB NVMe SSD each)  │
│  Coordinating Nodes (10): route requests, merge results, offload data nodes   │
│                                                                                │
│  Indices:                                                                      │
│    products-en-us    — 200M docs, 15 primary shards × 2 replicas = 45 copies │
│    autocomplete-en-us — 100M entries, 3 shards × 2 replicas                  │
│    spell-dictionary   — 1M terms, 1 shard × 2 replicas                       │
│                                                                                │
│  Capacity: 200 QPS per data node × 30 nodes = 6K QPS/region                  │
│  3 regions = 18K base → with caching (50% hit rate) → handles 70K peak QPS   │
└────────────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                         KAFKA EVENT BUS (6 brokers, RF=3)                      │
│                                                                                │
│  Topics:                                                                       │
│    product-updates (100 partitions)  — CDC from Product DB, 200 events/sec    │
│    inventory-updates (200 partitions) — real-time stock, 100K/sec peak        │
│    price-updates (100 partitions)    — price changes, 116 events/sec          │
│    click-events (300 partitions)     — user interactions, 70K events/sec peak  │
│    search-analytics (100 partitions) — query logs for ML training             │
│                                                                                │
│  Retention: 7 days (billing topics: 30 days for replay capability)            │
└──────┬───────────────────┬──────────────────────┬─────────────────────────────┘
       │                   │                      │
       ▼                   ▼                      ▼
┌──────────────┐  ┌────────────────┐  ┌──────────────────────────────────┐
│ SEARCH       │  │ ML TRAINING    │  │ PERSONALIZATION UPDATER          │
│ INDEXER      │  │ PIPELINE       │  │                                  │
│ (20 pods)    │  │ (Spark/Flink)  │  │ • Update user profiles in Redis │
│              │  │                │  │ • Real-time affinity adjustment  │
│ • Consume    │  │ • Join events  │  │ • Category/brand scores         │
│   product +  │  │ • Feature eng  │  │                                  │
│   inventory +│  │ • Weekly model │  │ Pods: 10                         │
│   price      │  │   retrain      │  └──────────────────────────────────┘
│ • Bulk index │  │ • A/B test     │
│   to ES      │  │   evaluation   │
│ • 10K docs/s │  │                │
└──────────────┘  └────────────────┘

┌────────────────────────────────────────────────────────────────────────────────┐
│                          DATABASE LAYER                                         │
│                                                                                │
│  ┌──────────────────────┐  ┌──────────────────┐  ┌────────────────────────┐   │
│  │ Product DB (Postgres) │  │ User DB (Postgres)│  │ Analytics DB           │   │
│  │ Primary + 5 replicas  │  │ Primary + 3 repl  │  │ (ClickHouse)           │   │
│  │                       │  │                   │  │                        │   │
│  │ • products (300M)     │  │ • users (500M)    │  │ • search_queries       │   │
│  │ • categories          │  │ • search_history  │  │   (500M/day)           │   │
│  │ • brands              │  │ • preferences     │  │ • click_events         │   │
│  │ • inventory           │  │ • purchase_hist   │  │   (1.5B/day)           │   │
│  │ • prices              │  │                   │  │ • impressions          │   │
│  │                       │  │ Sharded: user_id  │  │                        │   │
│  │ Sharded: product_id   │  │ ~200 GB           │  │ Partitioned by date    │   │
│  │ ~1.35 TB              │  │                   │  │ ~7.5 TB (30-day hot)   │   │
│  └──────────────────────┘  └──────────────────┘  └────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────┐
│                       MONITORING & OBSERVABILITY                               │
│  Prometheus + Grafana (metrics)  |  ELK (logs)  |  Jaeger (distributed traces)│
│  4 golden signals: latency, traffic, errors, saturation                       │
│  Key dashboards: search QPS, P99 latency, ES cluster health, cache hit ratio  │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Tech | Scale |
|-----------|---------|------|-------|
| **Search Service** | Query parse, spell check, ES query, ranking call, response assembly | Go | 28 pods/region |
| **Autocomplete Service** | Prefix search, popular queries, product/category suggestions | Go | 10 pods/region |
| **Ranking Service** | ML-based re-ranking with personalization, diversity, sponsored blending | Python + Go | 14 pods/region (optional GPU) |
| **Search Indexer** | Kafka consumer → bulk index to Elasticsearch | Go | 20 pods/region |
| **Event Collector** | Capture clicks, impressions → produce to Kafka | Go | 10 pods/region |
| **Elasticsearch** | Full-text search, filtering, faceting, aggregations | ES/OpenSearch | 43 nodes/region |
| **Redis Cache** | Query cache, autocomplete, user profiles, spell dict | Redis Cluster | 20 nodes/region |
| **Product DB** | Source of truth for catalog, inventory, pricing | PostgreSQL | Primary + 5 RR/region |
| **Analytics DB** | Click logs, search quality metrics, ML training data | ClickHouse | Distributed cluster |
| **Kafka** | CDC events, click events, decoupling all async pipelines | Kafka | 6 brokers/region |
| **ML Training** | Weekly model retrain from click/purchase logs | Spark + SageMaker | Batch (weekly) |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Elasticsearch Index Design — Sharding, Analyzers & Relevance Tuning

**The Problem**: 200M active products must be searchable with < 50ms P50 latency. Users type natural language ("wireless noise cancelling headphones under $100"), expect fuzzy matching, synonym awareness, and field-weighted relevance scoring. The index must support real-time updates (< 1 minute freshness) while handling 70K QPS peak.

**Index Configuration**:
```json
{
  "settings": {
    "number_of_shards": 15,
    "number_of_replicas": 2,
    "refresh_interval": "1s",
    "analysis": {
      "analyzer": {
        "product_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "product_synonyms", "english_stemmer"]
        },
        "autocomplete_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "edge_ngram_filter"]
        }
      },
      "filter": {
        "product_synonyms": {
          "type": "synonym",
          "synonyms": [
            "laptop, notebook, portable computer",
            "phone, mobile, smartphone, cell phone",
            "tv, television, tele",
            "headphones, earphones, earbuds"
          ]
        },
        "edge_ngram_filter": {
          "type": "edge_ngram",
          "min_gram": 2,
          "max_gram": 20
        },
        "english_stemmer": {
          "type": "stemmer",
          "language": "english"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "product_id":     { "type": "keyword" },
      "title":          { "type": "text", "analyzer": "product_analyzer",
                          "fields": { "keyword": { "type": "keyword" },
                                      "autocomplete": { "type": "text", "analyzer": "autocomplete_analyzer" } } },
      "description":    { "type": "text", "analyzer": "product_analyzer" },
      "brand":          { "type": "keyword" },
      "category_path":  { "type": "keyword" },
      "price_cents":    { "type": "integer" },
      "rating":         { "type": "half_float" },
      "review_count":   { "type": "integer" },
      "in_stock":       { "type": "boolean" },
      "prime_eligible": { "type": "boolean" },
      "popularity_score": { "type": "float" },
      "created_at":     { "type": "date" },
      "updated_at":     { "type": "date" }
    }
  }
}
```

**Why 15 shards**: Each shard processes queries in parallel. Too few (5) limits parallelism and creates large shards. Too many (100) causes overhead from shard state management and cross-shard coordination. At 200M docs, 15 shards gives ~13M docs per shard (~80GB each) — well within ES's sweet spot. With 2 replicas, we have 45 shard copies distributed across 30 data nodes.

**Search Query Construction**:
```json
{
  "query": {
    "function_score": {
      "query": {
        "bool": {
          "must": [
            { "multi_match": {
                "query": "wireless noise cancelling headphones",
                "fields": ["title^3", "brand^2", "description"],
                "type": "best_fields",
                "tie_breaker": 0.3,
                "fuzziness": "AUTO"
            }}
          ],
          "filter": [
            { "range": { "price_cents": { "lte": 10000 } } },
            { "term": { "in_stock": true } },
            { "range": { "rating": { "gte": 4.0 } } }
          ]
        }
      },
      "functions": [
        { "exp": { "updated_at": { "origin": "now", "scale": "30d", "decay": 0.5 } } },
        { "field_value_factor": { "field": "popularity_score", "modifier": "log1p", "factor": 2 } },
        { "field_value_factor": { "field": "review_count", "modifier": "log1p", "factor": 0.5 } }
      ],
      "score_mode": "sum",
      "boost_mode": "multiply"
    }
  },
  "aggs": {
    "brands":       { "terms": { "field": "brand", "size": 20 } },
    "price_ranges": { "range": { "field": "price_cents",
                       "ranges": [{"to": 5000}, {"from": 5000, "to": 10000}, {"from": 10000}] } },
    "avg_rating":   { "avg": { "field": "rating" } }
  },
  "size": 100,
  "sort": [ "_score", { "popularity_score": "desc" } ]
}
```

**Scoring formula**:
```
final_score = BM25(query, doc) × 
              (1.0 + recency_decay(updated_at, 30d)) ×
              (1.0 + log1p(popularity_score) × 2) ×
              (1.0 + log1p(review_count) × 0.5)

Where BM25 field weights:
  title: 3× (most important — exact product name match)
  brand: 2× (brand search is high-intent)
  description: 1× (supporting context)
```

**Zero-Downtime Reindexing** (Blue-Green):
```
1. Create new index: products-en-us-v2 (with updated mappings/analyzers)
2. Dual-write: all new updates go to both v1 and v2
3. Backfill v2 from v1 using _reindex API (background, 4-6 hours for 200M docs)
4. Verify: compare document counts, test sample queries
5. Atomic alias swap: products-en-us → v2 (< 1ms, zero downtime)
6. Stop dual-write, delete v1 after 7 days
```

---

### Deep Dive 2: Personalized Ranking — ML Model with 300+ Features

**The Problem**: Same query "laptop" should return gaming laptops for a gamer and ultrabooks for a business traveler. Without personalization, everyone sees the same results sorted by generic popularity. With personalization, CTR increases 20% and conversion increases 28%.

**Two-Stage Architecture**:
```
Stage 1: Candidate Generation (Elasticsearch, 25ms)
  Input: query + filters
  Output: top 100 candidates ranked by BM25 + basic signals
  Algorithm: text relevance + popularity + recency
  
Stage 2: Learning to Rank (Ranking Service, 10ms)
  Input: 100 candidates + user context + 300 features per candidate
  Output: re-ranked top 48 with diversity guarantee
  Algorithm: LightGBM (Gradient Boosted Trees)
```

**Feature Categories (300+ features)**:
```
Query Features (20):
  - Query length, word count
  - Query type: navigational ("amazon basics"), informational ("best laptop 2025"), transactional ("buy iphone")
  - Query popularity (log of historical frequency)
  - Is query a brand name? Category name?

Product Features (80):
  - BM25 score from ES (title match, description match)
  - Price (raw, relative to category median, percentile)
  - Rating (average, review count, log(review_count))
  - Sales rank (within category)
  - Days since listed
  - Number of images
  - Description completeness score
  - Prime eligible
  - In-stock probability (based on historical stock patterns)
  - Return rate

User Features (100) — precomputed daily, stored in Redis:
  - User embedding (256 dimensions — compressed to 50 via PCA at inference time)
  - Category affinity: purchase_count / visit_count per category
  - Brand affinity: purchase_count / visit_count per brand
  - Price sensitivity: avg_purchase_price / avg_viewed_price
  - Device type (mobile users prefer cheaper items, larger images)
  - Time of day (morning = commute browsing, evening = purchase intent)
  - Session depth (more pages = higher purchase intent)
  - Days since last purchase

Interaction Features (100) — precomputed batch:
  - Historical CTR for this (query, product) pair
  - Historical conversion rate for this (query, product) pair
  - Co-click score: users who clicked this also clicked what?
  - Cosine similarity: user_embedding · product_embedding
  - Category match: does user's top category match product category?
  - Brand match: does user's top brand match product brand?
```

**Model Training Pipeline**:
```
Weekly cadence:

1. Data Collection: 30 days of click logs from ClickHouse
   - Labels: purchased (weight 10), add-to-cart (weight 5), clicked (weight 1), 
             shown-not-clicked (weight -1)
   - Size: ~10B labeled examples, 500GB after feature joining

2. Feature Engineering (Spark job, 6 hours):
   - Join click events with user profiles and product data
   - Compute interaction features (CTR, conversion per query-product pair)
   - Output: Parquet files in S3

3. Model Training (SageMaker, 6 hours):
   - Algorithm: LightGBM
   - Hyperparams: num_leaves=31, learning_rate=0.1, 100 iterations
   - Objective: weighted binary cross-entropy (clicks + purchases)
   - Validation: last 7 days held out
   - Output: 100MB model artifact

4. Evaluation (automated):
   - Offline NDCG@10: must be ≥ current model
   - If regression: alert, don't deploy
   
5. Deployment: gradual rollout
   - A/B test: 10% traffic for 48 hours
   - Monitor: CTR, conversion, revenue per search
   - If positive: 10% → 50% → 100% over 1 week
```

**Online Inference (10ms budget)**:
```java
public class RankingService {
    
    // Model loaded in memory at startup (~100MB)
    private LightGBMModel model;
    
    public List<RankedProduct> rank(List<ProductCandidate> candidates,
                                    UserSearchProfile user,
                                    SearchQuery query) {
        
        // 1. Feature extraction (5ms)
        //    User features: precomputed in Redis (1 GET, < 1ms)
        //    Product features: carried from ES response
        //    Interaction features: precomputed batch, cached in Redis
        float[][] featureMatrix = featureExtractor.extract(candidates, user, query);
        
        // 2. Model inference (3ms for 100 candidates)
        //    LightGBM: ~0.03ms per candidate × 100 = 3ms
        float[] scores = model.predict(featureMatrix);
        
        // 3. Diversity enforcement (1ms)
        //    MMR: Maximal Marginal Relevance — penalize similar products
        //    Prevent: 48 identical phone cases from dominating results
        List<RankedProduct> diverse = applyMMR(candidates, scores, 
            /*lambda=*/0.7, /*targetSize=*/48);
        
        // 4. Sponsored product insertion (1ms)
        //    Blend ads at positions 1, 5, 12 if relevance score > threshold
        insertSponsoredProducts(diverse, query);
        
        return diverse;
    }
}
```

**Impact measurement**:
```
Version history:
  v1 (BM25 only):           CTR 28%, Conversion 2.1%
  v2 (+ product features):  CTR 32% (+14%), Conversion 2.5% (+19%)
  v3 (+ user features):     CTR 36% (+13%), Conversion 3.0% (+20%)
  v4 (+ interaction feats):  CTR 40% (+11%), Conversion 3.5% (+17%)
  v5 (LightGBM 300 feats):  CTR 45% (+13%), Conversion 4.2% (+20%)
  
Total improvement over baseline: +61% CTR, +100% conversion
Incremental revenue: ~$350M/year
Infrastructure cost: ~$2M/year → ROI: 175×
```

---

### Deep Dive 3: Spell Correction & Typo Handling — 3-Layer Approach

**The Problem**: 20% of search queries contain typos. "iphne 13 por max" should return iPhone results, not empty results. Must correct in < 10ms without increasing overall search latency significantly.

**Layer 1: Elasticsearch Fuzzy Matching (Free, built-in)**
```
Activated via: "fuzziness": "AUTO" in the multi_match query

AUTO rules:
  1-2 char words: exact match only (no fuzz on "tv", "ps5")
  3-5 char words: 1 edit distance ("iphne" → "iphone" ✓)
  6+ char words:  2 edit distance ("wireles" → "wireless" ✓)

Coverage: handles ~60% of typos
Latency: 0ms additional (built into the ES query)
Accuracy: good for single-character errors
Limitation: doesn't help with severely mangled queries
```

**Layer 2: Dictionary-Based Spell Checker (Redis cached)**
```
Dictionary: 1M+ correctly-spelled terms
  Sources: product titles, brand names, popular queries
  Updated: weekly batch job

Algorithm:
  1. Tokenize query: "iphne 13 por max" → ["iphne", "13", "por", "max"]
  2. For each token, check dictionary (Redis SET membership, O(1))
  3. If not in dictionary, generate candidates within edit distance 1-2
  4. Rank candidates by:
     a. Edit distance (fewer edits = more likely correction)
     b. Frequency in product titles (more common = more likely intended)
     c. Bigram probability with adjacent words (context-aware)
  5. Return best correction

Example:
  "iphne" → candidates: ["iphone" (freq: 5M, dist: 1), "phone" (freq: 2M, dist: 2)]
  Winner: "iphone" (highest frequency at minimum distance)
  
  "por" → candidates: ["pro" (freq: 3M, dist: 1), "for" (freq: 10M, dist: 1)]
  Context check: P("pro" | "iphone 13") >> P("for" | "iphone 13")
  Winner: "pro" (bigram context wins)

Final: "iphone 13 pro max"

Latency: 5ms (cache hit rate: 80%, miss: 15ms)
Coverage: handles ~35% more typos (cumulative: 95%)
Cache: Redis key "spell:{misspelling}" → correction, TTL 24 hours
```

**Layer 3: Query Rewrite (Last resort)**
```
If spell-corrected query still returns 0 results:
  1. Remove least important token (by IDF — rarest words first)
  2. Retry search
  3. If still 0: remove another token
  4. If 2 tokens remain and still 0: return "No results. Did you mean...?"

Example:
  Query: "wireles bluetoth hedphones" → spell: "wireless bluetooth headphones"
  If somehow still 0 results:
    Try: "wireless bluetooth" → 500 results ✓
    Show: results + "Showing results for 'wireless bluetooth'. Search instead for 'wireless bluetooth headphones'?"
    
Latency: +25ms per retry (rare — < 5% of queries)
Coverage: catches remaining 5%

Total correction rate: 95% of typo queries produce results
```

---

### Deep Dive 4: Handling Prime Day Traffic — 12× Spike to 70K QPS

**The Problem**: Normal traffic is 6K QPS. Prime Day hits 70K QPS within minutes. Must scale without downtime and without degraded results. If search goes down during Prime Day, Amazon loses ~$50M/hour.

**Pre-Scaling (12 hours before event)**:
```
Component         Normal    Prime Day   Scale Time
─────────────────────────────────────────────────
API Servers       28        56 (2×)     5 min (ASG warm pool)
ES Data Nodes     30        45 (1.5×)   15 min (add nodes + shard rebalance)
ES Coord Nodes    10        20 (2×)     5 min
Ranking Pods      14        28 (2×)     5 min
Redis Cluster     20        30 (1.5×)   10 min
Kafka Brokers     6         9 (1.5×)    20 min (partition rebalance)

Total cost: ~$41K for 24-hour event
Revenue impact: $100M+ in Prime Day search-driven sales
```

**Graceful Degradation Levels (if spike exceeds capacity)**:
```
Level 1: Aggressive Caching (10-12× load)
  - Search cache TTL: 5 min → 30 min
  - Autocomplete cache TTL: 1 hour → 6 hours
  - Serve slightly stale results (< 30 min old)
  - Impact: < 1% quality loss
  - Capacity gain: +50% (cache hit rate 50% → 75%)

Level 2: Reduce Quality (12-15× load)
  - Disable personalization (save 10ms, reduce ranking service load)
  - Return 24 results instead of 48 (half the ES work)
  - Simplify facets (top 3 facets only, not all 7)
  - Impact: ~5% conversion drop
  - Capacity gain: +30%

Level 3: Rate Limiting (> 15× load)
  - Priority: Prime members served first
  - Non-Prime: 10 searches/minute limit (vs unlimited normally)
  - Excess: 429 Too Many Requests with Retry-After header
  - Impact: ~10% users throttled temporarily
  - Capacity gain: shed 30% load

Historical performance:
  Prime Day 2024: peaked at 12× → Level 0 (pre-scaling handled everything)
  Black Friday 2024: peaked at 8× → Level 0
  Never reached Level 2 in production
```

**Real-time monitoring during event**:
```
Dashboard (refreshed every 10 seconds):
  • QPS: current vs capacity (target: < 80% utilization)
  • P99 latency: current vs SLA (target: < 200ms)
  • ES cluster CPU: per node (target: < 70%)
  • ES heap pressure: per node (target: < 75%)
  • Cache hit ratio: current (target: > 50%)
  • Kafka consumer lag: per topic (target: < 10K messages)
  • Error rate: 5xx percentage (target: < 0.1%)

Auto-triggers:
  CPU > 70% for 3 min → add 3 ES data nodes
  P99 > 150ms for 2 min → add 4 API servers
  Cache hit < 40% → increase TTL to 15 min
  Error rate > 0.5% → page on-call engineer
```

---

### Deep Dive 5: Faceted Search — Dynamic Filters with Sub-50ms Performance

**The Problem**: When a user searches "laptop", we show facets: Brand (Dell: 5K, HP: 4K, Apple: 3K), Price ($0-500: 10K, $500-1000: 20K), Screen Size (13": 8K, 15": 25K), RAM (8GB: 15K, 16GB: 25K). When they click "Brand: Apple", all other facets must update their counts (Screen Size for Apple laptops, not all laptops). This requires re-aggregation across the filtered result set.

**Elasticsearch Aggregation Strategy**:
```json
{
  "query": {
    "bool": {
      "must": [{ "multi_match": { "query": "laptop", "fields": ["title^3", "description"] }}],
      "filter": [
        { "term": { "in_stock": true }},
        { "term": { "brand": "Apple" }}
      ]
    }
  },
  "aggs": {
    "brands": {
      "terms": { "field": "brand", "size": 20 }
    },
    "price_ranges": {
      "range": {
        "field": "price_cents",
        "ranges": [
          { "key": "Under $500", "to": 50000 },
          { "key": "$500-$1000", "from": 50000, "to": 100000 },
          { "key": "$1000-$2000", "from": 100000, "to": 200000 },
          { "key": "Over $2000", "from": 200000 }
        ]
      }
    },
    "screen_sizes": {
      "terms": { "field": "attrs.screen_size.keyword", "size": 10 }
    },
    "ram_options": {
      "terms": { "field": "attrs.ram.keyword", "size": 10 }
    },
    "avg_price": { "avg": { "field": "price_cents" }},
    "avg_rating": { "avg": { "field": "rating" }}
  },
  "size": 100
}
```

**The "Facet Counts After Filter" Problem**:
```
Issue: When user selects Brand=Apple, the brand facet should still show ALL brands 
(so user can switch to Dell), but price/screen facets should only count Apple products.

Solution: Post-filter aggregation pattern
  - Global aggregation for the selected facet (brand): counts IGNORING the brand filter
  - Filtered aggregation for other facets: counts RESPECTING the brand filter

This is a single ES request with nested aggs — no extra round trips.
Total latency: 35-50ms for query + all facets + all aggregations.
```

**Smart Facet Selection Algorithm**:
```
Not all facets are useful for every query. "laptop" has meaningful screen size facets,
but "book" doesn't. We dynamically select which facets to show:

1. Category-based facets: each category defines relevant attributes
   Electronics → [Brand, Price, Screen Size, RAM, Storage]
   Clothing → [Brand, Price, Size, Color, Material]
   Books → [Author, Price, Format, Language]

2. Discriminative power: only show facets that help narrow results
   If 95% of results are Prime eligible → don't show Prime facet (not useful)
   If 50/50 split on "New vs Refurbished" → show condition facet (useful)
   
   Metric: entropy(facet_values) > threshold → show facet

3. Limit to top 7 facets (UI constraint) → rank by user engagement
```

**Pre-Computation for Popular Queries**:
```
Daily batch job computes facets for top 1000 queries:
  "iphone" → pre-computed facets stored in Redis
  "laptop" → pre-computed facets stored in Redis
  
At query time: 
  If query in top-1000 AND no filters applied → serve from Redis (< 1ms)
  Otherwise → compute from Elasticsearch (35-50ms)
  
Cache hit rate for this optimization: ~30% of all queries
ES load reduction: ~20%
```

---

### Deep Dive 6: Auto-Complete — Sub-50ms Suggestions with Personalization

**The Problem**: As users type each character, we need to show relevant suggestions within 50ms. At "iph" → suggest "iphone 13 pro max", "iphone 14", "iphone charger". Must blend query completions, product suggestions, and category suggestions. Must be personalized (a developer searching "py" should see "python" before "pyrex dish").

**Dedicated Auto-Complete Index**:
```
Why separate from the main product index:
  - Product index: 200M docs, optimized for full-text search + aggregations
  - Autocomplete index: 100M entries, optimized for prefix matching + frequency ranking
  - Mixing them would slow both (different access patterns)
  
Index contents (100M entries total):
  - 50M popular search queries (with frequency counts)
  - 40M product titles (top-selling products)
  - 10M categories and brand names
  
Each entry: text + type (QUERY/PRODUCT/CATEGORY) + popularity score + metadata
Size: ~6.2 GB (negligible)
```

**Edge N-Gram Tokenization**:
```
"iphone 13 pro max" is indexed as:
  ["ip", "iph", "ipho", "iphon", "iphone", "13", "pr", "pro", "ma", "max"]

Query "iph" matches the "iph" token → returns "iphone 13 pro max"
Query "pro" matches the "pro" token → returns "iphone 13 pro max" AND "projector" AND "protein powder"

Ranking: popularity × match_quality × personalization_boost
```

**Personalization**:
```
For user who recently searched/purchased gaming items:
  "key" → "mechanical keyboard" (rank 1), "keychain" (rank 5)

For user who recently searched/purchased kitchen items:  
  "key" → "key lime pie" (rank 1), "keychain" (rank 2), "keyboard" (rank 5)

Implementation:
  1. Global ranking: 0.7 × log(popularity)
  2. Personalization: 0.2 × category_affinity(user, suggestion_category)
  3. Trending: 0.1 × trend_score(suggestion, last_24h)
  
  final_score = global + personalization + trending
  
User category affinities stored in Redis: user:{id}:profile
Lookup cost: 1 HGETALL, < 1ms
```

**Caching Strategy**:
```
Layer 1: CDN edge cache for top 1000 prefixes (anonymous)
  "iph" → cached at CloudFront edge, < 5ms globally
  TTL: 1 hour
  Coverage: 20% of autocomplete requests

Layer 2: Redis cache per prefix (personalized blend)
  Key: autocomplete:{prefix}:{user_segment}
  User segments: 10 segments (by purchase category affinity)
  TTL: 1 hour
  Coverage: 50% additional

Layer 3: Elasticsearch query (fallback)
  For rare prefixes or new users
  Latency: 8-10ms
  Coverage: 30% of requests

Effective latency:
  70% cache hit → 2ms
  30% ES query → 10ms
  Blended P99: < 15ms (well under 50ms SLA)
```

---

### Deep Dive 7: Real-Time Index Updates — CDC Pipeline for Price/Inventory Freshness

**The Problem**: When a product's price drops from $999 to $899, the search results must reflect the new price within 1 minute. When inventory hits zero, the product must be flagged as out-of-stock immediately to prevent customer frustration. We process 200 normal updates/sec and up to 100K/sec during flash sales.

**Change Data Capture (CDC) Architecture**:
```
PostgreSQL → Debezium → Kafka → Search Indexer → Elasticsearch

Why Debezium CDC (not application-level events):
  - Captures ALL changes (even from batch jobs, admin tools, migrations)
  - No application code changes needed
  - Exactly-once guarantee (uses Postgres WAL)
  - Handles schema evolution gracefully
  
Why not direct ES writes from the application:
  - Coupling: application must know about ES internals
  - Reliability: if ES is down, the write fails (but DB write should succeed)
  - Ordering: concurrent updates may arrive out-of-order
  - Replay: can't reindex from scratch without the event stream
```

**Kafka Topic Design**:
```
topic: product-updates
  Partition key: product_id (ensures per-product ordering)
  Partitions: 100 (supports 20 consumers × 5 partitions each)
  Retention: 7 days
  
topic: inventory-updates  
  Partition key: product_id
  Partitions: 200 (higher throughput — flash sales can hit 100K/sec)
  Retention: 3 days (inventory changes less useful for replay)

topic: price-updates
  Partition key: product_id
  Partitions: 100
  Retention: 30 days (needed for price history analytics)
```

**Search Indexer Consumer**:
```java
public class SearchIndexerConsumer {
    
    /**
     * Kafka consumer that indexes product updates to Elasticsearch.
     * 
     * Batching strategy:
     *   Accumulate 1000 documents OR 1 second (whichever comes first)
     *   → Elasticsearch Bulk API (single HTTP request for 1000 docs)
     *   → 100ms per batch → 10K docs/sec per consumer instance
     * 
     * With 20 consumer instances: 200K docs/sec capacity
     * Normal load: 200 docs/sec (1% utilization → plenty of headroom)
     * Flash sale peak: 100K docs/sec (50% utilization → comfortable)
     */
    
    private static final int BATCH_SIZE = 1000;
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(1);
    
    public void consume(ConsumerRecord<String, ProductUpdate> record) {
        ProductUpdate update = record.value();
        
        // Build ES partial update document
        Map<String, Object> doc = new HashMap<>();
        
        switch (update.getChangeType()) {
            case PRICE_CHANGE:
                doc.put("price_cents", update.getNewPriceCents());
                doc.put("updated_at", Instant.now());
                break;
            case INVENTORY_CHANGE:
                doc.put("in_stock", update.getQuantity() > 0);
                doc.put("updated_at", Instant.now());
                break;
            case PRODUCT_UPDATE:
                // Full document update
                doc = buildFullDocument(update);
                break;
            case PRODUCT_DELETE:
                // Queue a delete operation
                batchDeleteBuffer.add(update.getProductId());
                return;
        }
        
        // Add to batch buffer
        batchBuffer.add(new UpdateRequest("products-en-us", update.getProductId())
            .doc(doc).docAsUpsert(true));
        
        // Flush if batch is full or timeout
        if (batchBuffer.size() >= BATCH_SIZE) {
            flushBatch();
        }
    }
    
    private void flushBatch() {
        if (batchBuffer.isEmpty()) return;
        
        BulkRequest bulkRequest = new BulkRequest();
        batchBuffer.forEach(bulkRequest::add);
        
        try {
            BulkResponse response = esClient.bulk(bulkRequest);
            if (response.hasFailures()) {
                // Retry failed items with exponential backoff
                retryFailed(response);
            }
            // Commit Kafka offsets after successful ES write
            consumer.commitSync();
        } catch (Exception e) {
            log.error("Bulk index failed, will retry: {}", e.getMessage());
            // Don't commit offset → Kafka will redeliver these messages
        }
        
        batchBuffer.clear();
    }
}
```

**Failure Handling**:
```
Scenario: Elasticsearch node down
  → Replicas serve queries (no search impact)
  → Kafka consumer retries bulk index (exponential backoff)
  → When node recovers: shard rebalances automatically
  → Kafka lag builds (7-day retention = 7 days to recover)

Scenario: Kafka consumer crash
  → Consumer group rebalances (remaining consumers take over partitions)
  → Reprocesses from last committed offset (at-least-once, ES upsert is idempotent)
  → No data loss

Scenario: Debezium connector crash
  → Restarts from last Kafka offset
  → Replays WAL changes from Postgres (WAL retained for 24 hours)
  → No data loss

Scenario: Full Elasticsearch cluster down
  → Search unavailable (circuit breaker returns degraded results from Redis cache)
  → Kafka buffers all updates (7-day retention)
  → When cluster recovers: consumers drain backlog in 2-4 hours
  → Full reindex from Postgres if needed (6 hours for 200M docs)
```

---

### Deep Dive 8: Multi-Language Search — 15 Languages with Locale-Specific Relevance

**The Problem**: Amazon operates in 15+ countries. Each has different languages, product names, and search behaviors. "laptop" in English, "ordinateur portable" in French, "ノートパソコン" in Japanese. Each language needs its own text analyzer (tokenization, stemming, synonyms). Some users search in English on non-English marketplaces.

**Separate Index per Locale**:
```
products-en-us (100M docs, 1TB)     — English analyzer, US pricing, US brands
products-es-es (50M docs, 500GB)    — Spanish analyzer, EUR pricing  
products-de-de (40M docs, 400GB)    — German analyzer (compound word splitting)
products-fr-fr (35M docs, 350GB)    — French analyzer (elision handling: l'ordinateur)
products-ja-jp (30M docs, 300GB)    — Japanese analyzer (kuromoji tokenizer for kanji/kana)
products-it-it (25M docs, 250GB)
... 15 total

Why per-locale (not one big index):
  1. Language-specific analyzers: German needs compound word splitting ("Handschuh" → "Hand" + "Schuh")
  2. Cultural relevance: same product ranked differently (umbrellas rank higher in London)
  3. Independent scaling: us-east needs more capacity than smaller locales
  4. Isolated failures: a bad reindex in ja-jp doesn't affect en-us
```

**Language Detection & Routing**:
```java
public SearchResult search(SearchQuery query) {
    // 1. Detect query language (95% accuracy)
    String detectedLang = languageDetector.detect(query.getQueryText());
    
    // 2. Determine target index
    String locale = query.getLocale(); // "fr-FR" from Accept-Language header
    String targetIndex = "products-" + locale.toLowerCase().replace("-", "_");
    
    // 3. Handle cross-language scenario
    if (!detectedLang.equals(localeToLang(locale))) {
        // User in France typed English query
        // Strategy: search both French and English indices, merge results
        SearchResult localResults = searchIndex(targetIndex, query);
        SearchResult englishResults = searchIndex("products-en-us", query);
        return mergeResults(localResults, englishResults, /*localWeight=*/0.7);
    }
    
    return searchIndex(targetIndex, query);
}
```

**Japanese/Chinese/Korean specifics**:
```
Japanese requires special tokenization:
  Standard tokenizer: "東京スカイツリー" → ["東京スカイツリー"] (one token — useless)
  Kuromoji tokenizer: "東京スカイツリー" → ["東京", "スカイ", "ツリー"] (meaningful tokens)

Chinese: jieba or smartcn tokenizer for word segmentation
Korean: nori tokenizer for morphological analysis

These analyzers add 5-10ms per query vs English standard analyzer.
```

---

## Summary: Key Design Decisions

| Decision | Chosen | Why | Tradeoff |
|----------|--------|-----|----------|
| **Search engine** | Elasticsearch (not Solr, not Algolia) | Best balance of features, scale, cost. Algolia would cost $1M/month at our scale | Self-managed cluster (129 nodes across 3 regions) |
| **API language** | Go | Low latency, high concurrency, small memory footprint. Java's GC pauses (10-100ms) are problematic for P50 < 50ms SLA | Smaller ecosystem than Java |
| **Ranking** | Separate service with LightGBM | Independent ML model iteration, A/B testing, doesn't require ES redeployment | +10ms latency per search (worth it for +28% conversion) |
| **Index per locale** | Yes (15 indices, not 1 giant index) | Language-specific analyzers, independent scaling, isolated failures | 15× management overhead, cross-locale search harder |
| **CDC pipeline** | Debezium → Kafka → ES | Captures ALL changes, replay capability, decoupled from application | Operational complexity of Kafka + Debezium |
| **Personalization** | User embeddings precomputed daily, real-time session signals | < 5ms feature lookup from Redis; daily recompute captures long-term preferences | Stale by up to 24 hours for long-term features |
| **Auto-complete** | Separate index with edge n-grams | 10ms vs 50ms if sharing main index; 6GB vs 1.2TB | Two indices to maintain |
| **Spell correction** | 3-layer: fuzzy + dictionary + rewrite | 95% correction rate, < 10ms total | Dictionary maintenance (weekly updates) |
| **Cache strategy** | Redis with 5-min TTL for search, 1-hour for autocomplete | 40-50% hit rate → halves ES load; different TTLs for different freshness needs | Stale results for up to 5 min (acceptable for browse) |
| **Consistency** | Eventual (< 1 min for prices, < 5 min for new products) | Decoupled write path from search; no blocking on ES indexing | Brief staleness window |
| **Scaling** | Pre-scale for events + 3-level graceful degradation | Never hit Level 2 degradation in production | Cost of pre-scaling (~$41K per Prime Day) |

## Interview Talking Points

1. **"Two-stage search: ES for recall (100 candidates in 25ms), LightGBM for ranking (re-rank to 48 in 10ms)"** — Elasticsearch is great at broad text matching but limited in personalization. A separate ranking service with 300 ML features gives us +28% conversion over ES-only ranking. The 10ms added latency pays for itself in revenue.

2. **"Hybrid scoring: BM25 text relevance × popularity decay × review quality — with field boosting (title 3×, brand 2×)"** — Title is the strongest signal for purchase intent. A user searching "Sony WH-1000XM5" wants that exact product, not a product with "sony" in the description. The 3× title boost ensures exact title matches dominate.

3. **"Separate auto-complete index with edge n-grams and personalized ranking"** — The main 200M-doc index is optimized for full-text search. A separate 100M-entry autocomplete index with edge n-gram tokenization returns suggestions in 8ms vs 50ms+ if we searched the main index. 70% cache hit rate makes most responses < 3ms.

4. **"CDC via Debezium → Kafka → ES bulk indexer: product update → searchable in < 2 seconds"** — Application-level events miss changes from batch jobs and admin tools. Debezium captures everything from the Postgres WAL. Kafka gives us 7-day replay (critical for reindexing after bugs). Bulk API (1000 docs/batch) gives 10K docs/sec per consumer.

5. **"3-layer spell correction: fuzzy matching (60% of typos, 0ms), dictionary (35%, 5ms), query rewrite (5%, 25ms)"** — Layer 1 is free. Layer 2 catches most remaining typos with context-aware bigram scoring. Layer 3 is the safety net: iteratively remove tokens until results appear. Total: 95% typo correction rate.

6. **"Graceful degradation for Prime Day: cache TTL increase → disable personalization → rate limit non-Prime"** — Pre-scaling handles 12× (actual Prime Day peak). Degradation levels are defined and tested but never needed in production. The $41K cost of pre-scaling is nothing compared to $50M/hour revenue loss if search goes down.

7. **"Per-locale indices with language-specific analyzers"** — German compound word splitting, Japanese kuromoji tokenization, French elision handling. One-size-fits-all analysis kills relevance. Cross-language queries (English in French marketplace) use multi-index search with locale-weighted merging.

8. **"Facets computed in a single ES aggregation query alongside search results — dynamic selection based on category and discriminative power"** — No extra round trips. Pre-computed facets for top 1000 queries (Redis, < 1ms). Post-filter aggregation pattern ensures the selected facet shows unfiltered counts while other facets respect the filter.

---

## Related System Design Problems

| Problem | Overlap | Key Difference |
|---------|---------|----------------|
| **Twitter Search** | Inverted index, async indexing via Kafka | Real-time tweets (< 1s freshness), no facets, no ranking ML |
| **Google Web Search** | Ranking, personalization, spell check | Web-scale crawling, PageRank, knowledge graph |
| **Netflix Search** | Personalization, ML ranking | Video metadata search, much smaller catalog (10K vs 300M) |
| **Uber Ride Search** | Geospatial, real-time | Location-first, dynamic pricing, no text search |
| **Product Catalog (Etsy/eBay)** | Same catalog, similar search | Adds listing creation + content moderation (covered separately) |
| **Ad Click Aggregator** | Click event processing, Kafka | Focuses on counting/billing, not search/ranking |

---

**Created**: April 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (pick 2-3 based on interviewer interest)
