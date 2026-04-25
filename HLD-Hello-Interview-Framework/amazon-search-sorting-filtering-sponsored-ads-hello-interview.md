# Design Amazon Search: Sorting, Filtering & Sponsored Ads — Hello Interview Framework

> **Question**: Design the sorting, filtering, and sponsored ads system for Amazon Product Search. When a user searches "wireless headphones," the system must apply faceted filters (brand, price, rating, Prime), sort by multiple criteria, blend sponsored products into organic results, track ad impressions/clicks for billing, and return everything in < 100ms at 70K QPS.
>
> **Asked at**: Amazon, Google, Meta, Microsoft, eBay, Walmart, Flipkart
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
1. **Faceted Filtering**: After search results load, show filter facets with live counts: Category hierarchy (Electronics > Audio > Headphones), Brand (Sony: 234, Bose: 189), Price ranges ($0-25, $25-50, $50-100, $100+), Customer rating (4+ stars), Prime eligible, In stock, Color, Deal type (Lightning Deal, Coupon). Counts update dynamically as filters are applied.
2. **Multi-Criteria Sorting**: Sort results by: Relevance (default), Price low-to-high, Price high-to-low, Avg. customer reviews, Newest arrivals, Best sellers. Sorting must work WITH active filters.
3. **Sponsored Products (Ads)**: Blend paid/sponsored product placements into organic search results. Ads are clearly labeled "Sponsored." Ad placement respects relevance (no unrelated ads). Ads compete via real-time auction (bid × relevance score). Track impressions and clicks for CPC billing.
4. **Dynamic Facet Counts**: When user selects "Brand: Sony", other facet counts update (price distribution, rating distribution recalculated for Sony-only results). Must be fast (< 50ms incremental).
5. **Price Range Slider**: Custom min/max price filtering with histogram showing price distribution.
6. **Pagination**: Cursor-based pagination supporting "load more" and page numbers. Consistent results across pages (no duplicates/skips).

#### Nice to Have (P1)
- Save filter preferences per user (auto-apply Prime filter for Prime members)
- Comparison feature (select products to compare specs side-by-side)
- "Customers who searched X also bought Y" within search results
- Filter by delivery speed ("Get it by tomorrow")
- Search within results (refine within current result set)
- A/B testing framework for ranking and ad placement strategies

#### Below the Line (Out of Scope)
- Product catalog management (CRUD)
- Full-text search relevance / query understanding (covered in the product search HLD)
- Checkout / cart / payments
- Review management system
- Seller onboarding / marketplace operations
- Ad campaign creation / management console

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Search + Filter Latency** | P50 < 50ms, P95 < 100ms, P99 < 200ms | Amazon loses 1% revenue per 100ms of latency |
| **Facet Computation** | < 30ms (included in total search latency) | Facets must load with results, not after |
| **Ad Auction Latency** | < 20ms (parallel with organic retrieval) | Ads must not slow down search results |
| **Throughput** | 70K QPS (peak 500K on Prime Day, 7× spike) | Search is #1 traffic driver |
| **Ad Fill Rate** | > 95% of search results have at least 1 sponsored product | Revenue target |
| **Ad Relevance** | CTR > 2% for sponsored products; < 5% user-reported irrelevant | Bad ads erode trust |
| **Availability** | 99.99% for search; 99.9% for ad serving (degrade gracefully) | If ads fail, show organic only |
| **Index Freshness** | Price/inventory: < 1 min; New products: < 5 min | Stale prices → overselling |
| **Consistency** | Eventual for catalog; strong for pricing/inventory | Show wrong description: meh. Show wrong price: lawsuit. |

### Capacity Estimation

```
Products:
  Total: 300M products
  Active (in-stock, buyable): 200M
  Products with active ad campaigns: 50M (25%)
  Average product record: 4.5KB (metadata + attributes)
  Average ad bid record: 200B

Traffic:
  DAU: 100M users, 5 searches/day = 500M searches/day
  QPS: 500M / 86400 ≈ 5,800 avg → 70K peak (12× Prime Day)
  Filter refinements: 2× base QPS (users click filters after initial search)
  Sort changes: 0.5× base QPS
  Total QPS including interactions: ~15K avg, ~180K peak

Ads:
  Ad eligible searches: 450M/day (90% of searches)
  Ad auctions/day: 450M × 3 ad slots = 1.35B auctions/day
  Impressions/day: ~900M (avg 2 ads shown per search)
  Clicks/day: ~18M (2% CTR)
  Revenue: ~$50M/day (avg $2.78 CPC) → $18B/year

Storage:
  Product search index (ES): 200M × 6KB = 1.2 TB per replica
  Facet index (aggregation-optimized): 200M × 500B = 100 GB
  Ad campaign index: 50M × 200B = 10 GB
  Click/impression logs: 1B events/day × 200B = 200 GB/day
  User personalization: 100M × 20KB = 2 TB

Infrastructure (per region):
  Search nodes (ES): 30 data + 10 coordinating
  Ad serving: 14 nodes (50K auctions/sec each)
  Facet cache (Redis): 20 nodes
  Click tracking (Kafka): 6 brokers
  Pricing service: 10 nodes
```

---

## 2️⃣ Core Entities

### Entity 1: Product (Search Document)
```java
public class ProductSearchDocument {
    String productId;
    String title;
    String brand;
    List<String> categoryPath;    // ["Electronics", "Audio", "Headphones", "Over-Ear"]
    int priceCents;               // 7999 ($79.99) — from real-time pricing service
    String currency;
    float rating;                 // 4.3
    int reviewCount;              // 12847
    boolean inStock;
    boolean primeEligible;
    int salesRank;                // Best seller rank in category
    String color;
    String size;
    Map<String, String> attrs;    // {"connectivity": "Bluetooth", "battery_life": "30h", "noise_cancel": "true"}
    DealType dealType;            // NONE, LIGHTNING_DEAL, COUPON, SUBSCRIBE_SAVE
    int couponPercentOff;         // 15 (for 15% off coupon)
    float popularityScore;        // Normalized [0, 1]
    Instant createdAt;
    Instant lastPriceUpdate;
    
    // Facetable fields (must be "keyword" type in ES, not analyzed)
    List<String> facetBrand;      // ["Sony"]
    List<String> facetCategory;   // ["Electronics", "Audio", "Headphones"]
    List<String> facetColor;      // ["Black", "Silver"]
    String facetPriceRange;       // "$50-$100" (pre-bucketed for fast faceting)
    int facetRatingBucket;        // 4 (for "4+ stars")
}

public enum DealType { NONE, LIGHTNING_DEAL, COUPON, SUBSCRIBE_SAVE, DEAL_OF_THE_DAY }
```

### Entity 2: SponsoredProductBid
```java
public class SponsoredProductBid {
    String campaignId;
    String adGroupId;
    String productId;
    String advertiserId;          // Seller/brand ID
    List<String> targetKeywords;  // ["wireless headphones", "bluetooth headphones"]
    List<String> targetCategories;// ["Electronics > Audio > Headphones"]
    List<String> negativeKeywords;// ["wired", "gaming"]
    int bidCents;                 // 150 ($1.50 max CPC bid)
    BidStrategy strategy;         // MANUAL, AUTO_TARGET_ACOS
    float targetAcos;             // 25% (target Advertising Cost of Sales)
    float qualityScore;           // [0, 1] — historical CTR, relevance, conversion rate
    DailyBudget dailyBudget;     // Max $500/day
    Instant campaignStart;
    Instant campaignEnd;
    boolean isActive;
    
    // Precomputed
    float effectiveBid;           // bidCents × qualityScore (used in auction)
}

public enum BidStrategy { MANUAL_CPC, AUTO_TARGET_ACOS, AUTO_TARGET_ROAS, DYNAMIC_BIDS_UP_DOWN }
```

### Entity 3: AdImpression / Click Event
```java
public class AdEvent {
    String eventId;               // UUID
    EventType type;               // IMPRESSION, CLICK, CONVERSION
    String searchQueryId;         // Links to the search session
    String queryText;             // "wireless headphones"
    String productId;
    String campaignId;
    String advertiserId;
    int bidCents;                 // What the advertiser bid
    int chargedCents;             // What they actually pay (2nd price)
    int position;                 // Ad slot position (1, 2, 3...)
    String userId;
    Instant timestamp;
    String sessionId;
    String pageType;              // SEARCH, PRODUCT_DETAIL, BROWSE
}

public enum EventType { IMPRESSION, CLICK, ADD_TO_CART, PURCHASE }
```

### Entity 4: FilterState
```java
public class FilterState {
    String queryText;             // "wireless headphones"
    Map<String, List<String>> selectedFilters;
    // {
    //   "brand": ["Sony", "Bose"],
    //   "price_range": ["$50-$100"],
    //   "rating": ["4+"],
    //   "prime": ["true"],
    //   "deal_type": ["COUPON"]
    // }
    String sortBy;                // "relevance" | "price_asc" | "price_desc" | "rating" | "newest" | "best_sellers"
    PriceRange customPriceRange;  // {min: 40, max: 120} (from slider)
    int page;
    int pageSize;                 // 48
    String cursor;                // Opaque cursor for next page
}
```

---

## 3️⃣ API Design

### 1. Search with Filters, Sort, and Ads
```
GET /api/v1/search?q=wireless+headphones&brand=Sony,Bose&price_min=50&price_max=150&rating_min=4&prime=true&sort=relevance&page_size=48&cursor=eyJzY29yZSI6MC44NX0=

Headers:
  Authorization: Bearer {jwt}
  X-Session-Id: sess_abc123
  X-Device-Type: mobile

Response (200 OK):
{
  "organic_results": [
    {
      "product_id": "B09XS7JWHH",
      "title": "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
      "brand": "Sony",
      "price_cents": 34800,
      "original_price_cents": 39999,
      "currency": "USD",
      "rating": 4.7,
      "review_count": 34521,
      "prime_eligible": true,
      "in_stock": true,
      "thumbnail_url": "https://m.media-amazon.com/images/I/51aX...",
      "deal_type": "COUPON",
      "coupon_text": "Save 15% with coupon",
      "delivery_estimate": "Tomorrow, Apr 26",
      "category_path": ["Electronics", "Headphones", "Over-Ear"],
      "badges": ["Amazon's Choice", "Best Seller"]
    }
    // ... 47 more organic results
  ],
  
  "sponsored_results": [
    {
      "product_id": "B0BSHF7Q18",
      "title": "Soundcore by Anker Space Q45 Adaptive Noise Cancelling Headphones",
      "brand": "soundcore",
      "price_cents": 9999,
      "rating": 4.5,
      "review_count": 8923,
      "prime_eligible": true,
      "ad_slot": 1,                  // Position among ads
      "insertion_position": 2,       // Placed after organic result #2
      "sponsored": true,
      "campaign_id": "camp_xyz",
      "impression_tracking_url": "https://ads.amazon.com/track?imp=abc123",
      "click_tracking_url": "https://ads.amazon.com/track?click=def456"
    },
    {
      "ad_slot": 2,
      "insertion_position": 6,       // Placed after organic result #6
      "sponsored": true,
      // ... product details
    }
  ],
  
  "facets": {
    "brand": {
      "buckets": [
        {"value": "Sony", "count": 234, "selected": true},
        {"value": "Bose", "count": 189, "selected": true},
        {"value": "JBL", "count": 167, "selected": false},
        {"value": "Apple", "count": 89, "selected": false},
        {"value": "Sennheiser", "count": 78, "selected": false}
      ],
      "total_unique": 87
    },
    "price_range": {
      "histogram": [
        {"min": 0, "max": 2500, "count": 45},
        {"min": 2500, "max": 5000, "count": 123},
        {"min": 5000, "max": 10000, "count": 234},
        {"min": 10000, "max": 20000, "count": 189},
        {"min": 20000, "max": 50000, "count": 67},
        {"min": 50000, "max": 100000, "count": 12}
      ],
      "selected_min": 5000,
      "selected_max": 15000
    },
    "rating": {
      "buckets": [
        {"label": "4★ & up", "count": 567, "selected": true},
        {"label": "3★ & up", "count": 789},
        {"label": "2★ & up", "count": 834},
        {"label": "1★ & up", "count": 847}
      ]
    },
    "prime": {
      "buckets": [
        {"label": "Prime", "count": 623, "selected": true},
        {"label": "All", "count": 847}
      ]
    },
    "deal_type": {
      "buckets": [
        {"label": "All Discounts", "count": 234},
        {"label": "Lightning Deals", "count": 12},
        {"label": "Coupons", "count": 89},
        {"label": "Subscribe & Save", "count": 45}
      ]
    },
    "color": {
      "buckets": [
        {"value": "Black", "count": 345},
        {"value": "White", "count": 189},
        {"value": "Blue", "count": 123},
        {"value": "Silver", "count": 89}
      ]
    }
  },
  
  "sort_options": [
    {"value": "relevance", "label": "Featured", "selected": true},
    {"value": "price_asc", "label": "Price: Low to High"},
    {"value": "price_desc", "label": "Price: High to Low"},
    {"value": "rating", "label": "Avg. Customer Reviews"},
    {"value": "newest", "label": "Newest Arrivals"},
    {"value": "best_sellers", "label": "Best Sellers"}
  ],
  
  "total_results": 847,
  "next_cursor": "eyJzY29yZSI6MC43MiwidGllIjoiQjA5WFM3SldISCJ9",
  "search_metadata": {
    "query_id": "qid_abc123",
    "latency_ms": 67,
    "spell_correction": null,
    "category_refinement": "Electronics > Headphones"
  }
}
```

### 2. Ad Click Tracking (Fire-and-Forget)
```
POST /api/v1/ads/click
{
  "event_id": "evt_uuid",
  "campaign_id": "camp_xyz",
  "product_id": "B0BSHF7Q18",
  "query_id": "qid_abc123",
  "position": 2,
  "timestamp": "2026-04-25T10:30:00Z"
}

Response (204 No Content)  // Fire and forget — don't block the user
```

---

## 4️⃣ Data Flow

### Search + Filter + Sort + Ads Flow
```
User searches "wireless headphones" → CDN/Edge → Load Balancer → Search API

Search API orchestrates IN PARALLEL:
  ┌─── Path A: Organic Search + Facets ───────────────────────────┐
  │  → Query Parser (tokenize, normalize, understand intent)       │
  │  → Elasticsearch Query:                                        │
  │       - Text search (BM25 + personalization)                   │
  │       - Apply filters (brand, price, rating, prime, in_stock)  │
  │       - Compute facet aggregations                             │
  │       - Sort by selected criterion                             │
  │  → L2 Ranking (ML model on top-100)                           │
  │  → Return ranked results + facets                              │
  └────────────────────────────────────────────────────────────────┘
  
  ┌─── Path B: Ad Auction (parallel) ─────────────────────────────┐
  │  → Ad Targeting (find campaigns matching query + category)     │
  │  → Relevance Filter (reject ads below relevance threshold)     │
  │  → Auction (rank by effectiveBid = bid × qualityScore)        │
  │  → Budget Check (is campaign within daily budget?)             │
  │  → Return top-3 winning ads with placement positions           │
  └────────────────────────────────────────────────────────────────┘

  → Merge: interleave sponsored results into organic results
  → Return unified response to user (< 100ms total)

Post-response (async):
  → Fire impression events to Kafka (one per ad shown)
  → Log search event for analytics
```

### Filter Refinement Flow (When User Clicks a Filter)
```
User clicks "Brand: Sony" → Client sends new request with updated filters

Optimization: Client can locally filter cached results if < 200 results were loaded.
Otherwise: Send new API request with filter state → Search API re-executes query with filter.

Key: Facet counts must be recomputed with the new filter applied!
  Before: brand facet shows counts across ALL results
  After "Brand: Sony": price/rating/color facets show counts for SONY ONLY
  But brand facet still shows counts for ALL (so user can see other brands to add)
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────────┐
│                    SEARCH + FILTER + ADS PATH                     │
│                                                                    │
│  User → CDN → LB → Search API Orchestrator                       │
│                          │                                         │
│              ┌───────────┼───────────────┐                        │
│              ▼                           ▼                         │
│     ┌──────────────────┐     ┌────────────────────┐              │
│     │ ORGANIC SEARCH    │     │ AD AUCTION SERVICE  │              │
│     │                    │     │                     │              │
│     │ Query Parser       │     │ Ad Targeting        │              │
│     │     ↓              │     │ (keyword + category │              │
│     │ Elasticsearch      │     │  matching)          │              │
│     │  - Text search     │     │     ↓               │              │
│     │  - Filters         │     │ Relevance Filter    │              │
│     │  - Facet aggs      │     │     ↓               │              │
│     │  - Sort            │     │ Auction Engine      │              │
│     │     ↓              │     │ (bid × quality)     │              │
│     │ L2 Ranker          │     │     ↓               │              │
│     │ (ML re-ranking)    │     │ Budget Validator    │              │
│     │                    │     │                     │              │
│     └────────┬───────────┘     └──────────┬─────────┘             │
│              └───────────────┬────────────┘                       │
│                              ▼                                     │
│                     ┌──────────────────┐                          │
│                     │  Result Merger    │                          │
│                     │  (blend organic + │                          │
│                     │   sponsored at    │                          │
│                     │   designated      │                          │
│                     │   positions)      │                          │
│                     └────────┬─────────┘                          │
│                              ▼                                     │
│                     ┌──────────────────┐                          │
│                     │ Pricing Service   │                          │
│                     │ (real-time price  │                          │
│                     │  + deal overlay)  │                          │
│                     └────────┬─────────┘                          │
│                              ▼                                     │
│                         Response                                   │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                        AD EVENT TRACKING                          │
│                                                                    │
│  Impression/Click → Tracking Pixel/API                            │
│         │                                                          │
│         ▼                                                          │
│       Kafka (ad-events topic)                                     │
│         │                                                          │
│    ┌────┼──────────────┐                                          │
│    ▼    ▼              ▼                                          │
│  Click  Attribution    Billing                                    │
│  Fraud  Engine         Aggregator                                 │
│  Detect (click →       (hourly                                    │
│         conversion     roll-up →                                  │
│         → sale)        invoice)                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Key Technology Choices

| Component | Technology | Why |
|-----------|-----------|-----|
| **Search Index** | Elasticsearch (Lucene) | Full-text + facets + sorting + geo in one system |
| **Facet Cache** | Redis | Pre-computed facet counts for popular queries |
| **Ad Index** | Custom in-memory (or ES) | Low-latency ad retrieval + auction |
| **Pricing** | DynamoDB / Redis | Real-time price lookups, high throughput |
| **Event Streaming** | Apache Kafka | Click/impression event pipeline |
| **Billing** | Flink + DynamoDB | Real-time spend tracking + aggregation |
| **Click Fraud** | Flink / custom ML | Real-time anomaly detection on click patterns |
| **ML Ranking** | TensorFlow Serving | L2 re-ranking model |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Faceted Filtering — Dynamic Counts at 70K QPS

**The Problem**: When user searches "wireless headphones," we show Brand: Sony (234), Bose (189), JBL (167)... When they click "Sony," ALL other facets must recompute: price ranges, ratings, colors — but for Sony products only. And this must happen in < 30ms.

**How Elasticsearch Computes Facets (Aggregations)**:
```
ES Query with facets:
{
  "query": {
    "bool": {
      "must": [{"match": {"title": "wireless headphones"}}],
      "filter": [
        {"term": {"brand": "Sony"}},       // Selected filter
        {"range": {"price_cents": {"gte": 5000, "lte": 15000}}},
        {"term": {"prime_eligible": true}},
        {"term": {"in_stock": true}}
      ]
    }
  },
  "aggs": {
    "brand_facet": {
      "terms": {"field": "brand", "size": 20}
    },
    "price_histogram": {
      "histogram": {"field": "price_cents", "interval": 2500}
    },
    "rating_facet": {
      "range": {
        "field": "rating",
        "ranges": [
          {"key": "4+", "from": 4.0},
          {"key": "3+", "from": 3.0},
          {"key": "2+", "from": 2.0}
        ]
      }
    },
    "color_facet": {
      "terms": {"field": "color", "size": 10}
    }
  },
  "size": 48,
  "sort": [{"_score": "desc"}]
}
```

**The "Sticky Facet" Problem**:
```
Problem: User selected "Brand: Sony." The brand facet should STILL show 
other brands with their counts (so user can multi-select or switch).

But the query has filter brand=Sony → only Sony products returned → 
brand facet only shows "Sony: 234" → user can't see other brands!

Solution — Global Aggregation:
  1. Main query: applies ALL filters (brand=Sony, price, rating, prime)
     → Returns filtered results for display
     
  2. Brand aggregation: applies all filters EXCEPT brand filter
     → Shows counts for ALL brands (within other filters)
     → User sees: Sony (234), Bose (189), JBL (167)...
  
  3. Price aggregation: applies all filters EXCEPT price filter
     → Shows price distribution (within brand + rating + prime)
  
  4. Rating aggregation: applies all filters EXCEPT rating filter
     → Shows rating distribution
  
  Each facet excludes its own filter but includes all others!
  This is called "disjunctive faceting" or "facet exclusion."

ES implementation — post_filter + global aggs:
{
  "query": {
    "bool": {
      "must": [{"match": {"title": "wireless headphones"}}],
      "filter": [
        {"term": {"prime_eligible": true}},
        {"term": {"in_stock": true}}
      ]
    }
  },
  "post_filter": {
    "bool": {
      "filter": [
        {"term": {"brand": "Sony"}},
        {"range": {"price_cents": {"gte": 5000, "lte": 15000}}},
        {"range": {"rating": {"gte": 4.0}}}
      ]
    }
  },
  "aggs": {
    "all_brands": {
      "filter": {  // Exclude brand filter, include price+rating
        "bool": {
          "filter": [
            {"range": {"price_cents": {"gte": 5000, "lte": 15000}}},
            {"range": {"rating": {"gte": 4.0}}}
          ]
        }
      },
      "aggs": {
        "brands": {"terms": {"field": "brand", "size": 20}}
      }
    },
    "all_prices": {
      "filter": {  // Exclude price filter, include brand+rating
        "bool": {
          "filter": [
            {"term": {"brand": "Sony"}},
            {"range": {"rating": {"gte": 4.0}}}
          ]
        }
      },
      "aggs": {
        "price_histogram": {"histogram": {"field": "price_cents", "interval": 2500}}
      }
    }
    // ... similar for each facet
  }
}
```

**Performance Optimization for Facets**:
```
Problem: Computing 6 facet aggregations on 200M products = expensive

Optimizations:
  1. Doc Values (columnar storage): ES stores facetable fields in columnar format
     → 10× faster than row-based for aggregations
     → Fields like brand, price, rating stored as doc_values
  
  2. Global ordinals: for keyword fields (brand), ES builds an ordinal mapping
     brand → ordinal: {"Apple": 0, "Bose": 1, "JBL": 2, "Sony": 3, ...}
     Aggregation on ordinals = integer comparison (fast!)
     
  3. Facet caching (Redis):
     Cache key: hash(query + all_filters_except_this_facet)
     TTL: 60 seconds (stale facet counts are acceptable briefly)
     Hit rate: ~40% for popular queries
  
  4. Approximate counts: for facets with 1000+ unique values,
     use approximate aggregation (cardinality agg with HyperLogLog)
     Exact count not critical: "About 234 results" is fine

Benchmark:
  Without optimization: 200ms for 6 facets on 200M docs
  With doc_values + ordinals + cache: < 30ms ✓
```

**Interview Talking Points**:
- "Disjunctive faceting: each facet excludes its own filter but includes all others — so users can see alternative values."
- "ES `post_filter` separates result filtering from aggregation scope — results are filtered, but aggregations see the broader set."
- "Doc values (columnar storage) + global ordinals give 10× speedup for facet computation."

---

### Deep Dive 2: Sorting — Multi-Criteria Sort at Scale

**The Problem**: User changes sort from "Relevance" to "Price: Low to High." The entire result set must be re-sorted. With 200M products matching "headphones," we can't sort all of them — we need smart strategies.

**Sort Implementation in Elasticsearch**:
```
Sort by relevance (default):
  sort: [{"_score": "desc"}, {"sales_rank": "asc"}]
  // _score = BM25 + personalization + popularity
  // Tie-breaker: higher sales rank wins

Sort by price (low to high):
  sort: [{"price_cents": "asc"}, {"_score": "desc"}]
  // Primary: price ascending
  // Tie-breaker: more relevant product wins among same-price products

Sort by rating:
  sort: [{"rating": "desc"}, {"review_count": "desc"}, {"_score": "desc"}]
  // Primary: highest rating
  // Tie-breaker 1: more reviews (4.7 with 10K reviews > 4.7 with 10 reviews)
  // Tie-breaker 2: relevance
  
Sort by newest:
  sort: [{"created_at": "desc"}, {"_score": "desc"}]

Sort by best sellers:
  sort: [{"sales_rank": "asc"}, {"_score": "desc"}]
```

**Challenge: Sort + Pagination Consistency**:
```
Problem: User is on page 3. Between page 2 and page 3:
  - A product's price changed ($49 → $79)
  - When sorting by price asc, this product jumped from position 45 to 120
  - User might see it on page 2 AND miss it on page 3, or skip it entirely

Solution — Cursor-based pagination with search_after:
  After returning page 1, the cursor encodes the sort values of the last result:
    cursor = {price_cents: 4999, _score: 0.85, product_id: "B09XS7JWHH"}
  
  Page 2 query:
    search_after: [4999, 0.85, "B09XS7JWHH"]
    → ES returns results after this point in the sort order
  
  This handles most cases, but a price change mid-session can still cause issues.
  
  Full consistency: use Point-in-Time (PIT) API
    1. Open PIT: POST /products/_pit?keep_alive=5m → pit_id
    2. All pages use the same pit_id → consistent snapshot
    3. Close PIT after session ends
    
    Trade-off: PIT holds resources on ES nodes (5 min per session)
    At 100K concurrent sessions: 100K open PIT snapshots → significant memory
    
  Pragmatic approach: search_after without PIT for most users.
  Use PIT only for API clients that need strict consistency.
```

**Sort + Filter Interaction**:
```
Tricky case: User sorts by price, then filters by "4+ stars"

Naive: re-execute query with sort=price_asc AND filter=rating≥4
  → Works, but total result count changes, and facet counts update

User expectation: "I'm looking at the same headphones, just sorted differently"
  → Adding a filter is a NEW search (result set changes)
  → Changing sort is a REORDER of the same result set

Implementation:
  1. Sort change: re-execute query with same filters, different sort order
     - Can optimize: if results are cached, just re-sort locally (for small result sets)
  2. Filter change: re-execute query with new filters → new results, new facets, new counts
```

**Interview Talking Points**:
- "Cursor-based pagination with `search_after` for sort consistency — encode last result's sort values."
- "Each sort criterion needs a tie-breaker to ensure deterministic ordering — I use product_id as final tie-breaker."
- "Point-in-Time API for strict consistency, but pragmatically, `search_after` handles 99% of cases."

---

### Deep Dive 3: Sponsored Products — Real-Time Ad Auction

**The Problem**: For every search, we run an auction among 50M ad-eligible products to select 3 winning ads, ranked by bid × quality, all within 20ms.

**Ad Auction Architecture**:
```
Search query: "wireless headphones"
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
  Ad Targeting              Organic Search
  (find eligible ads)       (in parallel)
        │
        ▼
  ┌─────────────────────────────────────────┐
  │ Ad Targeting Engine                      │
  │                                          │
  │ 1. Keyword Match:                        │
  │    Find campaigns targeting:             │
  │    "wireless headphones" (exact)         │
  │    "headphones" (broad)                  │
  │    "bluetooth audio" (phrase)            │
  │    → 5,000 eligible campaigns            │
  │                                          │
  │ 2. Category Match:                       │
  │    Find campaigns targeting:             │
  │    "Electronics > Audio > Headphones"    │
  │    → 2,000 more campaigns               │
  │                                          │
  │ 3. Negative Keyword Filter:              │
  │    Remove campaigns with negatives       │
  │    matching the query                    │
  │    → 6,500 remaining                     │
  │                                          │
  │ 4. Eligibility Check:                    │
  │    - Campaign active? (date range)       │
  │    - Budget remaining? (< daily cap)     │
  │    - Product in stock?                   │
  │    - Product not already in organic top-5│
  │    → 4,000 eligible bids                 │
  └─────────────────┬───────────────────────┘
                    ▼
  ┌─────────────────────────────────────────┐
  │ Relevance Filter                         │
  │                                          │
  │ For each of 4,000 candidates:            │
  │   relevance = cosine(query_embedding,    │
  │                       product_embedding) │
  │   if relevance < 0.3 → reject           │
  │   → 2,000 pass relevance threshold      │
  │                                          │
  │ Why? Showing a "wireless mouse" ad for   │
  │ "wireless headphones" hurts user trust   │
  └─────────────────┬───────────────────────┘
                    ▼
  ┌─────────────────────────────────────────┐
  │ Auction Engine                           │
  │                                          │
  │ For each candidate:                      │
  │   eCPM = bid × predicted_CTR × 1000     │
  │                                          │
  │ predicted_CTR = ML model:                │
  │   features: query-product relevance,     │
  │   historical CTR, product rating,        │
  │   price competitiveness, ad quality      │
  │                                          │
  │ Sort by eCPM descending                  │
  │ Select top-3                             │
  │                                          │
  │ Pricing (Generalized Second Price):      │
  │   Winner pays: (2nd_place_eCPM /         │
  │                 winner_predicted_CTR)     │
  │   + $0.01                                │
  │                                          │
  │ This incentivizes truthful bidding!       │
  └─────────────────┬───────────────────────┘
                    ▼
              3 winning ads with:
              - placement positions
              - actual charge amount
              - tracking URLs
```

**Why eCPM (not just bid)?**
```
eCPM = bid × predicted_CTR × 1000

Scenario:
  Advertiser A: bids $3.00 CPC, but predicted CTR = 0.5% → eCPM = $15.00
  Advertiser B: bids $1.00 CPC, but predicted CTR = 3% → eCPM = $30.00
  
  Winner: Advertiser B (lower bid, but higher expected revenue for Amazon!)
  
Why this is better:
  - Maximizes Amazon's revenue per impression
  - Encourages relevant ads (high CTR = high eCPM = wins auction)
  - Discourages irrelevant high-bidders from winning
  - Users see more relevant ads → better experience → more clicks → more revenue (flywheel)
```

**Generalized Second Price (GSP) Auction**:
```
Why not first-price (pay what you bid)?
  → Leads to bid shading: advertisers strategically bid below their true value
  → Results in unstable equilibria and lower revenue

Second-price:
  Winner pays: just enough to beat the second place
  
  Example (3 ad slots):
    Ad A: eCPM $30 → pays ($20/CTR_A + $0.01) to beat B
    Ad B: eCPM $20 → pays ($15/CTR_B + $0.01) to beat C  
    Ad C: eCPM $15 → pays ($10/CTR_C + $0.01) to beat D (4th place)
  
  Truthful bidding is a dominant strategy → simpler for advertisers
```

**Performance — 20ms Budget for Ad Auction**:
```
How to auction 4,000 candidates in 20ms?

1. Ad index in-memory: all active campaigns in a Redis-like structure
   Key: keyword → [campaign_ids with bids]
   Pre-sorted by bid descending
   
2. Fast targeting: inverted index on keywords and categories
   "wireless headphones" → 5,000 campaign_ids in O(1) lookup
   
3. Pre-computed quality scores:
   predicted_CTR cached per (campaign, keyword_category) pair
   Updated every hour from ML model
   No real-time ML inference during auction!
   
4. Top-K selection: partial sort (find top-3, don't sort all 4,000)
   Use a min-heap of size 3: O(N × log(3)) ≈ O(N) for N=4,000
   
5. Budget check cached:
   Redis: campaign_id → {daily_budget: 50000, spent_today: 34200}
   If spent_today > daily_budget × 0.95 → throttle (pace throughout day)

Total: ~15ms for full auction pipeline
```

**Interview Talking Points**:
- "eCPM auction (bid × predicted CTR) not just bid — maximizes Amazon's revenue while rewarding relevant ads."
- "Generalized Second Price: winner pays just above second place → incentivizes truthful bidding."
- "Relevance filter before auction: reject ads with relevance < 0.3 to protect user experience."
- "Pre-computed quality scores + in-memory ad index → full auction in 15ms."

---

### Deep Dive 4: Ad Placement — Blending Sponsored into Organic Results

**The Problem**: We have 48 organic results and 3 sponsored products. Where do we place the ads? How do we prevent ads from degrading user experience?

**Placement Strategy**:
```
Amazon's typical ad placement pattern:
  Position 1: ORGANIC (always organic first — build trust)
  Position 2: SPONSORED ← Ad slot 1 (highest bidder)
  Positions 3-5: ORGANIC
  Position 6: SPONSORED ← Ad slot 2
  Positions 7-12: ORGANIC
  Position 13: SPONSORED ← Ad slot 3
  Positions 14-48: ORGANIC (no more ads below the fold)

Rules:
  1. Never lead with an ad (position 1 is always organic)
  2. No consecutive ads (always ≥ 2 organic results between ads)
  3. Ad density: max 3 ads per 48 results = 6.25% ad density
  4. Top fold: max 1 ad in first 4 positions (visible without scrolling)
  5. Bottom half: no ads (users scrolling deep want organic results)
  
Mobile vs Desktop:
  Mobile: smaller screen → max 2 ads per page (less space, less tolerance)
  Desktop: 3 ads per page (more real estate)
```

**Ad Relevance Guard Rails**:
```
Before placing an ad, verify:
  1. Category match: ad product in same category as organic results
     "wireless headphones" query → headphone ad ✓, laptop charger ad ✗
  
  2. Price range: ad product within 50-200% of average organic price
     If organic avg is $80, ad for $400 earbuds feels out of place
  
  3. Not a duplicate: ad product not already in organic top-20
     If Sony XM5 is organic #3, don't also show it as sponsored #1
  
  4. Not a competitor block: Brand A can't bid to block Brand B
     If user searches "Sony headphones," Bose can bid for placement
     but must still be relevant to "headphones" (not just blocking)

Quality metrics tracked:
  - Ad CTR by position: if CTR drops below 0.5% → position is hurting UX
  - "Sponsored" label visibility: A/B tested to ensure users recognize ads
  - User surveys: "Was this ad helpful?" (sampled 1% of impressions)
```

**A/B Testing Ad Placement**:
```
Continuously test:
  - Number of ads per page (2 vs 3 vs 4)
  - Spacing between ads (every 4 results vs every 6)
  - Ad position (top vs bottom vs interleaved)
  - Ad format (standard vs enhanced with additional images)
  
Metrics:
  Primary: Revenue per search (ads revenue + organic conversion)
  Secondary: User engagement (time on page, pages viewed, return rate)
  Guardrail: CTR on organic results shouldn't drop > 2% 
             (ads stealing clicks from organic = bad)
```

**Interview Talking Points**:
- "Never lead with an ad, max 1 ad above the fold, no consecutive ads — protect organic search experience."
- "Ad density capped at 6.25% (3 ads per 48 results) — users came for organic results."
- "Relevance guard rails: category match, price range check, no organic duplicates in ad slots."
- "A/B test placement strategy with revenue per search as primary metric, organic CTR as guardrail."

---

### Deep Dive 5: Click/Impression Tracking & Billing Pipeline

**The Problem**: 900M ad impressions/day and 18M clicks/day need to be tracked for CPC billing. We must never double-count clicks, never miss an impression, and prevent click fraud.

**Event Pipeline Architecture**:
```
Ad shown to user → Browser fires 1x1 pixel (impression)
User clicks ad → Browser fires click event + redirect

  Impression/Click → Tracking Service (fire-and-forget)
       │
       ▼
     Kafka (ad-events topic, partitioned by campaign_id)
       │
  ┌────┼──────────────┐
  ▼    ▼              ▼
Click  Attribution    Billing
Fraud  Engine         Pipeline
       │              │
       ▼              ▼
  ┌─────────┐    ┌─────────────┐
  │ Valid   │    │ Spend       │
  │ events  │    │ Aggregator  │
  │ only    │    │ (Flink)     │
  └────┬────┘    └──────┬──────┘
       │                │
       ▼                ▼
  Attribution DB    ┌─────────────┐
  (impressions →    │ Billing DB   │
   clicks →         │ (hourly      │
   conversions)     │  invoices)   │
                    └─────────────┘
```

**Click Fraud Detection**:
```
Types of fraud:
  1. Bot clicks: automated clicking to drain competitor budgets
  2. Click farms: humans paid to click ads
  3. Publisher fraud: fake impressions on partner sites
  4. Click injection: malware intercepts organic clicks, claims as ad clicks

Detection (real-time, in Flink):
  Rule-based:
    - Same user clicks same ad > 3 times in 1 hour → fraudulent
    - Clicks from known bot IP ranges → reject
    - Click-through time < 100ms (humanly impossible) → bot
    - Click with no prior impression event → fabricated
    
  ML-based:
    - Anomalous click patterns per campaign (spike detection)
    - Device fingerprint clustering (1000 clicks from 5 fingerprints → fraud)
    - Geographic impossibility (clicks from 3 countries in 1 minute)
    
  Action:
    - Filter fraudulent clicks BEFORE billing
    - Don't charge advertiser for fraudulent clicks
    - Flag and investigate patterns
    - Amazon's "invalid click" refund policy
```

**Billing Aggregation**:
```
Real-time spend tracking (per campaign):
  Redis: campaign_id → {spent_today: 34200, budget: 50000}
  Updated on every valid click
  Used for budget pacing (throttle ads when approaching daily budget)

Billing pipeline:
  1. Flink aggregates valid clicks per campaign per hour
  2. Hourly roll-ups stored in billing DB
  3. Daily summary → advertiser dashboard
  4. Monthly invoice generated → payment processing

Deduplication:
  Each click has a unique event_id (UUID)
  Kafka exactly-once semantics (idempotent producer + transactional consumer)
  Flink deduplication window: 1 hour (reject duplicate event_ids)
```

**Attribution — Connecting Clicks to Sales**:
```
Advertiser wants to know: "Did my ad lead to a sale?"

Attribution model (Amazon's "last-click 14-day window"):
  1. User clicks sponsored ad for Product X at T=0
  2. Record: (user_id, product_id, click_time)
  3. If user purchases Product X within 14 days → attribute sale to ad
  4. Attribution stored in DynamoDB:
     Key: (user_id, product_id)
     Value: {click_time, campaign_id, purchase_time, order_value}
  
  Metrics reported to advertiser:
    - ACOS (Advertising Cost of Sales) = ad_spend / ad_attributed_sales
    - ROAS (Return on Ad Spend) = ad_attributed_sales / ad_spend
    - Conversion rate = purchases / clicks
```

**Interview Talking Points**:
- "Fire-and-forget click tracking — never block the user's click navigation."
- "Kafka exactly-once + Flink deduplication ensures no double-billing."
- "Click fraud detection: rule-based (real-time) + ML-based (batch) with multiple signals."
- "14-day last-click attribution: connect ad click to eventual purchase."

---

### Deep Dive 6: Real-Time Pricing in Search Results

**The Problem**: Prices change up to 100K times/second during flash sales. Search results must show the CURRENT price, not a stale indexed price. But re-indexing 200M products for every price change is impossible.

**Separation of Price from Search Index**:
```
Search Index (Elasticsearch):
  - Contains: title, description, brand, rating, category (rarely changes)
  - Price field: stale (used only for SORTING and FILTERING, acceptable lag: 1 min)
  - Updated via Kafka CDC from product catalog

Price Service (DynamoDB / Redis):
  - Contains: real-time price, deal info, coupon, stock status
  - Updated by pricing engine every second
  - Key: product_id → {price_cents, original_price, deal_type, coupon_pct, in_stock}

At query time:
  1. ES returns top-48 products by relevance (using potentially stale indexed price)
  2. Batch lookup fresh prices from Price Service for those 48 products
     (DynamoDB BatchGetItem: 48 keys in < 5ms)
  3. Overlay fresh prices onto results
  4. Re-sort if user sorted by price (fresh prices may change order slightly)
  5. Return to user

Trade-off: 
  - Sorting/filtering uses slightly stale ES price (99% accurate, 1-min lag)
  - Display price is always fresh from Price Service
  - If price mismatch would change sort position: best-effort (user might see slight inconsistency)
```

**Handling Flash Sales (Lightning Deals)**:
```
Problem: Lightning Deal starts at 3:00 PM. Price drops from $99 to $49.
  - 100K users searching at 3:00 PM should see $49
  - Price Service updates instantly
  - ES index updates within 1 minute
  
Window of inconsistency (3:00:00 - 3:01:00):
  - Filter "price < $60" might NOT return this product (ES still has $99)
  - But display price shows $49 (from Price Service)
  - Users who find it see the correct deal price

Mitigation:
  - For Lightning Deals: pre-index the deal price 1 minute before deal starts
  - "Deal index" separate from main index: small (1000 active deals), updated real-time
  - Merge deal results into main results → deal products always show correct price
```

**Interview Talking Points**:
- "Separate pricing from search index: ES for retrieval (1-min lag OK), DynamoDB for display price (real-time)."
- "Batch lookup 48 prices from DynamoDB in < 5ms — doesn't add meaningful latency."
- "For Lightning Deals, pre-index the deal price or use a separate real-time deal index."

---

### Deep Dive 7: Personalized Sort — "Featured" Is Not Random

**The Problem**: Amazon's default sort "Featured" (aka "Relevance") is NOT just BM25 text match. It's a personalized blend of dozens of signals. Two users searching the same query see different result orders.

**Amazon's "Featured" Ranking Signals**:
```
Text Relevance (30% weight):
  - BM25 on title, bullet points, description, backend keywords
  - Exact match boost (query = product title exactly)
  - Category match boost

Sales & Conversion (25% weight):
  - Sales velocity (units sold per day in this category)
  - Conversion rate (purchases / detail page views)
  - Sales rank within category
  - "Best Seller" badge
  
Customer Satisfaction (20% weight):
  - Average rating (stars)
  - Review count (more reviews = more trust)
  - Return rate (high returns = demote)
  - A-to-Z claims against this product
  
Personalization (15% weight):
  - User's purchase history (bought this brand before → boost)
  - User's browsing history (viewed similar products → boost)
  - User's price preference (usually buys mid-range → boost mid-range)
  - Prime membership (Prime member → boost Prime products)
  
Freshness & Availability (10% weight):
  - In-stock (out of stock → heavy demotion)
  - Prime delivery available
  - Delivery speed (1-day > 2-day > standard)
  - New product boost (first 30 days get slight boost for discoverability)

Amazon-specific business signals:
  - Amazon's own products: slightly boosted? (controversial — regulatory scrutiny)
  - FBA vs FBM: FBA (Fulfilled by Amazon) products slightly preferred (better delivery)
  - Seller rating: better sellers get slight boost
```

**Feature Engineering for L2 Model**:
```
ML model input features (200+):

Query-Product Features:
  - BM25 score on title, description, bullet_points
  - Query-title exact match (binary)
  - Query-category match (binary)
  - Token overlap ratio
  
Product Quality Features:
  - Average rating (float)
  - Review count (log-normalized)
  - Sales rank (inverse log-normalized)
  - Return rate (float, lower is better)
  - Days since listing (for new product boost)
  
User-Product Features:
  - User has purchased from this brand (binary)
  - User has viewed this product (binary)
  - User's price affinity (product_price / user_avg_purchase_price)
  - User's category affinity (float, from browsing history)
  
Business Features:
  - Prime eligible (binary)
  - FBA (binary)
  - Has active deal (binary)
  - Seller rating (float)
  - Delivery speed to user's address (hours)

Model: LambdaMART (GBDT with 500 trees, max_depth 8)
  Trained on: clicks + add-to-cart + purchases from search (implicit feedback)
  Updated: daily model refresh, A/B tested
  Inference: ~1ms per product → 100 products in 100ms
```

**Interview Talking Points**:
- "Amazon's 'Featured' sort is a 200-feature ML model — not just BM25 text match."
- "Sales velocity and conversion rate are the strongest non-text signals — Amazon promotes products that sell."
- "Personalization is ~15% of the ranking score: purchase history, browsing, price preference."
- "Daily model refresh with A/B testing — always improving the ranking formula."

---

### Deep Dive 8: Inventory-Aware Filtering — The "In Stock" Challenge

**The Problem**: Inventory changes 100K times/second. Showing out-of-stock products in search results wastes user time and erodes trust. But keeping the search index in perfect sync with inventory is nearly impossible.

**Inventory Filtering Strategy**:
```
Layer 1 — Soft filter at index time (best-effort):
  - Kafka CDC from inventory service → update ES "in_stock" field
  - Lag: 30-60 seconds (acceptable for most products)
  - 99% of products have stable inventory (not changing per second)
  
Layer 2 — Real-time check at display time:
  - For top-48 results: batch check inventory from Inventory Service
  - If any result is now out of stock: remove and backfill from position 49+
  - Adds ~5ms to response time
  
Layer 3 — Hot items during flash sales:
  - Some products sell out in seconds (e.g., PS5 restocks)
  - "Just-in-time" inventory reservation for items in cart
  - Search shows "Only 3 left in stock" or "In stock soon" for borderline items
  
Trade-off matrix:
  Approach               | Freshness | Latency Impact | Complexity
  ─────────────────────────────────────────────────────────────────
  ES index only           | 30-60s lag | 0ms           | Low
  ES + display-time check | ~real-time | +5ms          | Medium  ← Default
  Real-time inventory DB  | Real-time  | +20ms         | High    ← Flash sales only
```

**"Almost Out of Stock" UX Signals**:
```
When inventory < 10 units:
  Show: "Only 3 left in stock - order soon"
  → Creates urgency, increases conversion by ~15%
  
When inventory = 0 but restock imminent:
  Show: "Temporarily out of stock. Order now and we'll deliver when available."
  → Don't remove from search, but demote in ranking
  
When inventory = 0 with no restock:
  Remove from search results entirely (after 1-hour grace period)
```

**Interview Talking Points**:
- "Two-layer inventory filtering: soft filter in ES index (30s lag), hard check at display time for top-48."
- "Showing out-of-stock products is worse than a slightly stale price — users lose trust."
- "Scarcity UX: 'Only 3 left' increases conversion by ~15% — it's a ranking signal AND a display signal."

---

## What is Expected at Each Level?

### Mid-Level
- Design basic search with filtering using Elasticsearch
- Implement sorting by price, rating, relevance
- Understand faceted aggregations at a basic level
- Show sponsored products in a fixed position
- Basic pagination

### Senior
- Disjunctive faceting (each facet excludes its own filter)
- ES `post_filter` + global aggregations architecture
- eCPM auction (bid × CTR) for ad ranking
- GSP (second-price) auction mechanism
- Cursor-based pagination with `search_after`
- Separate pricing service from search index
- Click/impression tracking pipeline with Kafka
- Inventory-aware filtering (soft + hard check)

### Staff
- Full ad auction pipeline with relevance guards and budget pacing
- Click fraud detection (rule-based + ML)
- Attribution modeling (14-day last-click)
- 200-feature LambdaMART ranking model for "Featured" sort
- Facet caching with Redis + doc_values + global ordinals for performance
- A/B testing framework for ad placement strategies
- Real-time pricing overlay with flash sale handling
- Prime Day capacity planning (7× traffic spike)
- Revenue per search as the north star metric
- Discuss trade-offs: ad revenue vs user experience, freshness vs latency, personalization vs filter bubbles
