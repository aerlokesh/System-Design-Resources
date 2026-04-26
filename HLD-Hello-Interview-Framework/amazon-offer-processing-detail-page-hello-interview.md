# Design Amazon Offer Processing Pipeline — Vendor/Seller Offers to Detail Page — Hello Interview Framework

> **Question**: Design the system that processes product offers from millions of third-party sellers and first-party vendors (Amazon Retail) and determines what appears on the Amazon product detail page — including the Buy Box winner, all offer listings, pricing, shipping options, and availability. This is the core commerce pipeline that connects catalog data, pricing, inventory, and seller quality into the customer-facing detail page.
>
> **Asked at**: Amazon (very common!), Walmart, eBay, Flipkart, Alibaba
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

### Understanding the Domain — Amazon's Offer Model

```
KEY CONCEPT: On Amazon, a "Product" (ASIN) is NOT the same as an "Offer."

ASIN (Amazon Standard Identification Number):
  = A unique product identity (e.g., "iPhone 15 Pro Max 256GB Black")
  = Has ONE detail page
  = Has shared attributes: title, images, description, reviews

Offer:
  = A specific seller's listing for that ASIN
  = Each ASIN can have 1 to 1000+ offers from different sellers
  = Each offer has its own: price, shipping, condition (new/used), fulfillment method

Example: ASIN B0CMZ4S8MV (iPhone 15 Pro Max)
  Offer 1: Amazon.com (1P vendor) — $1,199, Prime 1-day, New
  Offer 2: TechDeals LLC (3P seller, FBA) — $1,149, Prime 2-day, New  
  Offer 3: PhoneWorld (3P seller, FBM) — $1,099, 5-day shipping, New
  Offer 4: RefurbKing (3P seller, FBM) — $899, 3-day shipping, Refurbished
  
Buy Box: The prominent "Add to Cart" button shows ONE winning offer.
  → Buy Box winner for this ASIN: TechDeals LLC ($1,149, Prime)
  → Other offers shown in "Other Sellers on Amazon" section

The Buy Box winner gets ~82% of all sales for that ASIN!
```

### Functional Requirements

#### Core Requirements (P0)
1. **Offer Ingestion**: Accept product offers from 2M+ third-party sellers (via Seller Central / MWS API) and 100K+ first-party vendors (via Vendor Central / EDI feeds). Each offer specifies: ASIN, price, quantity, condition, fulfillment method, shipping options.
2. **Offer Validation**: Validate each offer: seller is active/not suspended, ASIN exists in catalog, price is within acceptable range (not too high, not predatory low), condition matches listing restrictions, hazmat/restricted product checks.
3. **Buy Box Computation**: For each ASIN, determine the Buy Box winner from all active offers. Algorithm considers: price (landed price = item + shipping), seller performance metrics, fulfillment method (FBA > FBM), shipping speed, inventory depth.
4. **Detail Page Assembly**: When a customer visits a product detail page, assemble: product metadata (title, images, description, bullets), Buy Box offer (price, shipping, seller), all other offers sorted, reviews/ratings, related products. All within < 100ms.
5. **Real-Time Price & Inventory Updates**: When a seller changes price or inventory changes (sale, restock, return), the detail page must reflect the update within 1-5 minutes. Buy Box may switch to a different seller.
6. **Offer Listing Page**: "New & Used from $X" page showing ALL offers for an ASIN, sorted by landed price, with seller ratings and shipping details.

#### Nice to Have (P1)
- Dynamic pricing suggestions for sellers (repricing tools)
- MAP (Minimum Advertised Price) enforcement for brands
- Counterfeit detection (too-cheap offers for luxury brands)
- Subscribe & Save offer aggregation
- International offer routing (show different offers by country)
- "Frequently Bought Together" and "Customers Also Bought"

#### Below the Line (Out of Scope)
- Product catalog management (ASIN creation, matching, merging)
- Review/rating system
- Search and discovery
- Cart, checkout, payments
- Shipping/logistics/fulfillment execution
- Seller onboarding and account management

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Detail Page Latency** | P50 < 50ms, P95 < 100ms, P99 < 200ms | Every 100ms costs 1% revenue |
| **Offer Update Latency** | < 2 minutes from seller submission to live on detail page | Sellers expect near-real-time pricing |
| **Buy Box Recomputation** | < 30 seconds after any offer change | Buy Box must reflect current best offer |
| **Throughput** | 100K detail page views/sec; 10M offer updates/day | Peak traffic on Prime Day |
| **Offer Volume** | 2B active offers across 600M ASINs | Massive catalog |
| **Availability** | 99.99% for detail page reads; 99.9% for offer writes | Detail page is the revenue page |
| **Consistency** | Eventual for offer updates (minutes OK); strong for inventory decrements | Showing stale price: OK briefly. Overselling: not OK. |
| **Scale** | 2M sellers, 100K vendors, 600M ASINs, 2B offers | Growing 20% YoY |

### Capacity Estimation

```
Offers:
  Active offers: 2B (across 600M ASINs)
  Average offers per ASIN: 3.3 (many ASINs have 1, popular ones have 100+)
  New/updated offers per day: 10M (price changes, inventory updates, new listings)
  Offer size: ~500 bytes (price, seller, fulfillment, shipping, condition)

Detail Page Views:
  Total: 5B page views/day
  QPS: 5B / 86400 ≈ 58K avg → 300K peak (Prime Day)
  Each page view needs: product metadata + Buy Box + offer count + reviews

Offer Updates:
  10M offer updates/day = 116 updates/sec avg → 1,000/sec peak
  Buy Box recomputations triggered: ~5M/day (not every offer change flips Buy Box)

Storage:
  Offer store: 2B × 500B = 1 TB
  Product metadata: 600M × 5KB = 3 TB
  Buy Box cache: 600M × 200B = 120 GB (easily fits in Redis)
  Seller profiles: 2M × 2KB = 4 GB
  Historical offers (for analytics): 100 TB (S3)

Infrastructure:
  Offer DB (DynamoDB): 50 partitions, 100K RCU, 10K WCU
  Buy Box cache (Redis): 20 nodes, 120 GB total
  Detail page API: 60 servers (5K QPS each)
  Offer processing pipeline: 30 workers
  Kafka: 10 brokers
```

---

## 2️⃣ Core Entities

### Entity 1: Offer (The Core Entity)
```java
public class Offer {
    String offerId;              // Unique: "{sellerId}:{asin}:{condition}"
    String asin;                 // "B0CMZ4S8MV"
    String sellerId;             // "A3PJQF5Z7Z3Z1Q" (seller/vendor ID)
    SellerType sellerType;       // FIRST_PARTY (1P/Amazon Retail) or THIRD_PARTY (3P)
    
    // Pricing
    int listPriceCents;          // 119900 ($1,199.00) — before any discounts
    int offerPriceCents;         // 114900 ($1,149.00) — actual selling price
    int shippingCents;           // 0 (free shipping for Prime)
    int landedPriceCents;        // 114900 (offer + shipping) — used for Buy Box!
    String currency;             // "USD"
    
    // Fulfillment
    FulfillmentChannel channel;  // FBA (Fulfilled by Amazon), FBM (Fulfilled by Merchant), AFN (Amazon Fresh)
    int maxDeliveryDays;         // 2 (Prime 2-day)
    int minDeliveryDays;         // 1
    boolean primeEligible;       // true (FBA or Seller Fulfilled Prime)
    
    // Inventory
    int quantityAvailable;       // 342
    boolean inStock;             // true
    InventoryCondition condition;// NEW, USED_LIKE_NEW, USED_VERY_GOOD, USED_GOOD, USED_ACCEPTABLE, REFURBISHED
    
    // Seller Quality (precomputed)
    float sellerRating;          // 4.8 (out of 5)
    float orderDefectRate;       // 0.3% (ODR — critical metric!)
    float lateShipmentRate;      // 0.8%
    float cancellationRate;      // 0.5%
    boolean isFeaturedMerchant;  // true (eligible for Buy Box consideration)
    
    // Metadata
    OfferStatus status;          // ACTIVE, SUPPRESSED, INACTIVE, BLOCKED
    Instant createdAt;
    Instant lastUpdatedAt;
    Instant priceLastChangedAt;
    String suppressionReason;    // null or "PRICE_TOO_HIGH" / "SELLER_SUSPENDED" / "HAZMAT_REVIEW"
}

public enum FulfillmentChannel { FBA, FBM, SFP, AFN, PRIME_NOW }
public enum SellerType { FIRST_PARTY, THIRD_PARTY }
public enum InventoryCondition { NEW, USED_LIKE_NEW, USED_VERY_GOOD, USED_GOOD, USED_ACCEPTABLE, REFURBISHED, COLLECTIBLE }
public enum OfferStatus { ACTIVE, SUPPRESSED, INACTIVE, BLOCKED, PENDING_REVIEW }
```

### Entity 2: BuyBoxResult (Precomputed)
```java
public class BuyBoxResult {
    String asin;                 // "B0CMZ4S8MV"
    
    // Winning offer
    String winningOfferId;       // "A3PJQF5Z:B0CMZ4S8MV:NEW"
    String winningSellerId;      // "A3PJQF5Z7Z3Z1Q"
    String winnerSellerName;     // "TechDeals LLC"
    int winnerPriceCents;        // 114900
    int winnerShippingCents;     // 0
    FulfillmentChannel winnerChannel; // FBA
    boolean winnerPrimeEligible; // true
    int winnerDeliveryDays;      // 2
    
    // Other offers summary
    int totalOfferCount;         // 4
    int newOfferCount;           // 3
    int usedOfferCount;          // 1
    int lowestNewPriceCents;     // 109900 (PhoneWorld's $1,099 — but not Buy Box winner!)
    int lowestUsedPriceCents;    // 89900
    
    // Metadata
    float buyBoxConfidence;      // 0.92 (how stable is this Buy Box — will it flip soon?)
    Instant computedAt;          // When Buy Box was last computed
    Instant expiresAt;           // Recompute after this time (max 5 min staleness)
}
```

### Entity 3: ProductDetailPage (Assembled View)
```java
public class ProductDetailPage {
    // Product metadata (from Catalog Service)
    String asin;
    String title;                // "Apple iPhone 15 Pro Max, 256GB, Black Titanium"
    List<String> bulletPoints;   // Feature bullets
    String description;          // Full product description
    List<String> imageUrls;      // Product images
    List<String> categoryPath;   // ["Electronics", "Cell Phones", "Smartphones"]
    
    // Buy Box (from Buy Box Service)
    BuyBoxResult buyBox;
    
    // Reviews (from Review Service)
    float averageRating;         // 4.6
    int totalReviews;            // 12847
    Map<Integer, Integer> ratingDistribution; // {5: 8234, 4: 2341, 3: 1234, 2: 567, 1: 471}
    
    // Offers summary
    int totalSellers;            // 4
    String otherSellersSnippet;  // "3 new from $1,099.00, 1 used from $899.00"
    
    // Variations
    List<Variation> variations;  // Color: [Black, White, Blue], Storage: [128GB, 256GB, 512GB]
    
    // Delivery (personalized to viewer's address)
    DeliveryEstimate delivery;   // "FREE delivery Thursday, April 27"
}
```

### Entity 4: SellerProfile
```java
public class SellerProfile {
    String sellerId;
    String sellerName;           // "TechDeals LLC"
    SellerType type;             // THIRD_PARTY
    float rating;                // 4.8
    int totalRatings;            // 23456
    float orderDefectRate;       // 0.3%
    float lateShipmentRate;      // 0.8%
    float cancellationRate;      // 0.5%
    boolean isFeaturedMerchant;  // Buy Box eligible (must meet quality thresholds)
    Instant memberSince;         // "2018-03-15"
    boolean isProfessionalSeller;// true (vs. Individual seller plan)
    List<String> authorizedBrands; // Brands this seller is authorized to sell
    SellerAccountStatus status;  // ACTIVE, SUSPENDED, UNDER_REVIEW
}
```

---

## 3️⃣ API Design

### 1. Detail Page API (Customer-Facing)
```
GET /api/v1/detail-page/{asin}?customer_id={id}&zip_code=10001&marketplace=US

Headers:
  Authorization: Bearer {jwt}
  Accept-Language: en-US

Response (200 OK):
{
  "asin": "B0CMZ4S8MV",
  "title": "Apple iPhone 15 Pro Max, 256GB, Black Titanium - Unlocked",
  "images": ["https://m.media-amazon.com/images/I/81xxx.jpg", ...],
  "bullet_points": [
    "FORGED IN TITANIUM — iPhone 15 Pro Max has a strong and light aerospace-grade titanium design",
    "ADVANCED DISPLAY — The 6.7\" Super Retina XDR display with ProMotion"
  ],
  
  "buy_box": {
    "seller_id": "A3PJQF5Z7Z3Z1Q",
    "seller_name": "TechDeals LLC",
    "price_cents": 114900,
    "shipping_cents": 0,
    "landed_price_cents": 114900,
    "prime_eligible": true,
    "fulfillment": "FBA",
    "condition": "NEW",
    "delivery_estimate": "FREE delivery Thursday, April 27",
    "delivery_date": "2026-04-27",
    "in_stock": true,
    "quantity_available": 342,
    "add_to_cart_enabled": true
  },
  
  "other_offers_summary": {
    "total_new": 3,
    "total_used": 1,
    "lowest_new_cents": 109900,
    "lowest_used_cents": 89900,
    "link": "/gp/offer-listing/B0CMZ4S8MV"
  },
  
  "reviews": {
    "average_rating": 4.6,
    "total_count": 12847,
    "distribution": {"5": 8234, "4": 2341, "3": 1234, "2": 567, "1": 471}
  },
  
  "variations": [
    {"dimension": "Color", "values": [
      {"value": "Black Titanium", "asin": "B0CMZ4S8MV", "selected": true},
      {"value": "White Titanium", "asin": "B0CMZ4TKMW"},
      {"value": "Blue Titanium", "asin": "B0CMZ53K3G"}
    ]},
    {"dimension": "Storage", "values": [
      {"value": "256GB", "asin": "B0CMZ4S8MV", "selected": true},
      {"value": "512GB", "asin": "B0CMZ5R77H"},
      {"value": "1TB", "asin": "B0CMZ6B8X2"}
    ]}
  ],
  
  "metadata": {
    "category_path": ["Electronics", "Cell Phones & Accessories", "Cell Phones", "Smartphones"],
    "brand": "Apple",
    "model": "iPhone 15 Pro Max",
    "first_available": "2025-09-22"
  }
}
```

### 2. Offer Listing Page ("Other Sellers")
```
GET /api/v1/offers/{asin}?condition=new&sort=price_asc&page=1

Response (200 OK):
{
  "asin": "B0CMZ4S8MV",
  "offers": [
    {
      "offer_id": "off_1",
      "seller_name": "PhoneWorld",
      "seller_rating": 4.2,
      "seller_rating_count": 5678,
      "price_cents": 109900,
      "shipping_cents": 599,
      "landed_price_cents": 110499,
      "condition": "NEW",
      "fulfillment": "FBM",
      "prime_eligible": false,
      "delivery_estimate": "May 1 - May 5",
      "in_stock": true
    },
    {
      "offer_id": "off_2",
      "seller_name": "TechDeals LLC",
      "seller_rating": 4.8,
      "seller_rating_count": 23456,
      "price_cents": 114900,
      "shipping_cents": 0,
      "landed_price_cents": 114900,
      "condition": "NEW",
      "fulfillment": "FBA",
      "prime_eligible": true,
      "delivery_estimate": "Thursday, April 27",
      "buy_box_winner": true,
      "in_stock": true
    },
    {
      "offer_id": "off_3",
      "seller_name": "Amazon.com",
      "seller_rating": null,
      "price_cents": 119900,
      "shipping_cents": 0,
      "landed_price_cents": 119900,
      "condition": "NEW",
      "fulfillment": "AFN",
      "prime_eligible": true,
      "delivery_estimate": "Tomorrow, April 26",
      "in_stock": true,
      "is_amazon_retail": true
    }
  ],
  "total_offers": 4,
  "page": 1,
  "total_pages": 1
}
```

### 3. Offer Submission (Seller-Facing)
```
POST /api/v1/sellers/{seller_id}/offers
{
  "asin": "B0CMZ4S8MV",
  "price_cents": 114900,
  "quantity": 342,
  "condition": "NEW",
  "fulfillment_channel": "FBA",
  "handling_time_days": 0,
  "shipping_template_id": "tmpl_prime_2day"
}

Response (202 Accepted):
{
  "offer_id": "off_A3PJQF5Z_B0CMZ4S8MV_NEW",
  "status": "PROCESSING",
  "estimated_live_time": "2026-04-25T10:32:00Z",
  "message": "Offer submitted. Will be live within 2 minutes."
}
```

---

## 4️⃣ Data Flow

### Offer Ingestion & Processing Flow
```
Seller submits offer (Seller Central / API / Feed file)
  → Offer Ingestion Service (validate, normalize, enrich)
  → Kafka: "offer-updates" topic (key: asin)
  → Multiple consumers in parallel:
  
  Consumer 1: "offer-validator"
    → Check: seller active? ASIN valid? Price within bounds? Restricted product?
    → If invalid → mark SUPPRESSED with reason
    → If valid → mark ACTIVE

  Consumer 2: "offer-store-writer"
    → Write to DynamoDB (Offer Store): key = {asin, sellerId, condition}
    → This is the source of truth for all offers

  Consumer 3: "buy-box-trigger"  
    → For each offer change on an ASIN → trigger Buy Box recomputation
    → Buy Box Service recalculates winner for that ASIN
    → Write result to Redis (Buy Box Cache): key = "buybox:{asin}"

  Consumer 4: "search-index-updater"
    → Update Elasticsearch: new price, new availability, new offer count
    → Search results show updated "from $X" price

  Consumer 5: "detail-page-cache-invalidator"
    → Invalidate CDN/edge cache for this ASIN's detail page
    → Next customer visit fetches fresh data
```

### Detail Page Assembly Flow (Read Path)
```
Customer visits amazon.com/dp/B0CMZ4S8MV
  → CDN/Edge Cache check → MISS (or stale)
  → Load Balancer → Detail Page Service

Detail Page Service orchestrates IN PARALLEL:
  ┌─── Call 1: Catalog Service ──────────────┐
  │  Input: ASIN B0CMZ4S8MV                   │
  │  Returns: title, images, bullets, brand    │
  │  Latency: ~10ms (cached in Redis)          │
  └────────────────────────────────────────────┘
  
  ┌─── Call 2: Buy Box Service ──────────────┐
  │  Input: ASIN + customer zip + Prime status│
  │  Returns: winning offer + price + delivery │
  │  Latency: ~5ms (Redis lookup)              │
  └────────────────────────────────────────────┘
  
  ┌─── Call 3: Offer Summary Service ────────┐
  │  Input: ASIN                               │
  │  Returns: offer count, lowest prices       │
  │  Latency: ~10ms (DynamoDB query)           │
  └────────────────────────────────────────────┘
  
  ┌─── Call 4: Review Service ───────────────┐
  │  Input: ASIN                               │
  │  Returns: rating, count, distribution      │
  │  Latency: ~8ms (Redis cached)              │
  └────────────────────────────────────────────┘
  
  ┌─── Call 5: Delivery Estimation ──────────┐
  │  Input: ASIN + winning offer + zip code    │
  │  Returns: "Thursday, April 27"             │
  │  Latency: ~15ms (complex calculation)      │
  └────────────────────────────────────────────┘
  
  → Assemble all responses → return to customer
  → Total: max(10, 5, 10, 8, 15) + assembly = ~25ms (parallel!)
  → Cache in CDN for 30 seconds (personalized parts excluded)
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────────┐
│                   OFFER INGESTION (WRITE PATH)                    │
│                                                                    │
│  Sellers ──→ Seller Central API ──→ Offer Ingestion Service       │
│  Vendors ──→ Vendor Central/EDI ──→          │                    │
│                                               ▼                   │
│                                        ┌──────────┐              │
│                                        │  Kafka    │              │
│                                        │  "offer-  │              │
│                                        │  updates" │              │
│                                        └────┬─────┘              │
│                                 ┌───────────┼───────────┐        │
│                                 ▼           ▼           ▼        │
│                          ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│                          │ Offer    │ │ Buy Box  │ │ Search   │  │
│                          │ Validator│ │ Trigger  │ │ Index    │  │
│                          │          │ │          │ │ Updater  │  │
│                          └────┬─────┘ └────┬─────┘ └────┬─────┘ │
│                               ▼            ▼            ▼        │
│                          ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│                          │ DynamoDB │ │  Redis   │ │  Elastic │  │
│                          │ (Offer   │ │ (Buy Box │ │ search   │  │
│                          │  Store)  │ │  Cache)  │ │ (Index)  │  │
│                          └──────────┘ └──────────┘ └──────────┘ │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                   DETAIL PAGE (READ PATH)                         │
│                                                                    │
│  Customer → CDN → LB → Detail Page Service                       │
│                              │                                     │
│              ┌───────────────┼───────────────────┐                │
│              ▼               ▼                   ▼                │
│        ┌──────────┐   ┌──────────┐        ┌──────────┐          │
│        │ Catalog   │   │ Buy Box  │        │ Review   │          │
│        │ Service   │   │ Service  │        │ Service  │          │
│        │ (Redis)   │   │ (Redis)  │        │ (Redis)  │          │
│        └──────────┘   └──────────┘        └──────────┘          │
│              │               │                   │                │
│              └───────────────┼───────────────────┘                │
│                              ▼                                     │
│                   ┌──────────────────┐                            │
│                   │ Delivery         │                            │
│                   │ Estimation       │                            │
│                   │ (zip + inventory │                            │
│                   │  + logistics)    │                            │
│                   └────────┬─────────┘                            │
│                            ▼                                       │
│                   Page Assembly → Response                         │
└──────────────────────────────────────────────────────────────────┘
```

### Key Technology Choices

| Component | Technology | Why |
|-----------|-----------|-----|
| **Offer Store** | DynamoDB | Key-value access by (asin, sellerId), high throughput, auto-scaling |
| **Buy Box Cache** | Redis | Sub-ms reads for 600M ASINs, 120 GB fits in cluster |
| **Event Bus** | Apache Kafka | Decouple offer ingestion from processing, replay capability |
| **Search Index** | Elasticsearch | Full-text search + offer counts + price ranges |
| **Catalog Store** | DynamoDB + Redis | Product metadata rarely changes, heavily cached |
| **CDN** | CloudFront | Cache detail page fragments, static assets |
| **Delivery Estimation** | Custom service | Complex logic: inventory location + carrier capacity + date math |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Buy Box Algorithm — Who Wins the "Add to Cart"?

**The Problem**: ASIN has 15 offers from different sellers. One seller gets the Buy Box (the "Add to Cart" button). This seller gets 82% of sales. How does Amazon choose?

**Buy Box Eligibility (Must Pass All)**:
```
Before even competing for Buy Box, seller must be eligible:

1. Professional seller account (not Individual)
2. Featured Merchant status (earned via performance metrics)
3. Order Defect Rate (ODR) < 1%
4. Late Shipment Rate < 4%
5. Pre-fulfillment Cancel Rate < 2.5%
6. Account in good standing (no suspensions)
7. Sufficient account history (> 90 days, > 25 orders)
8. Product is in "New" condition (used items have separate Buy Box)

If seller fails ANY of these → NOT eligible → never wins Buy Box.
Amazon Retail (1P) is always eligible.
```

**Buy Box Scoring Algorithm**:
```
For each eligible offer on the ASIN:

buy_box_score(offer) = 
    w1 × landed_price_score(offer)         // 35% — price competitiveness
  + w2 × fulfillment_score(offer)          // 25% — FBA > SFP > FBM
  + w3 × shipping_speed_score(offer)       // 15% — faster = better
  + w4 × seller_metrics_score(offer)       // 15% — ODR, late shipment, etc.
  + w5 × inventory_depth_score(offer)      // 5%  — more stock = more reliable
  + w6 × account_health_score(offer)       // 5%  — account age, feedback count

Detailed scoring:

LANDED PRICE (item + shipping):
  landed_price = offer_price + shipping_cost
  
  normalized_score = 1 - (landed_price - min_landed_price) / price_range
  
  Example:
    Offer A: $114.99 + $0 = $114.99 (landed)
    Offer B: $109.99 + $5.99 = $115.98 (landed)
    Offer C: $119.99 + $0 = $119.99 (landed)
    
    Offer A wins on landed price! (B is cheaper listed but more expensive landed)

FULFILLMENT SCORE:
  FBA (Fulfilled by Amazon): 1.0
    → Amazon handles storage, packing, shipping, returns
    → Most reliable → highest score
    
  SFP (Seller Fulfilled Prime): 0.8
    → Seller ships but meets Prime standards
    
  FBM (Fulfilled by Merchant): 0.4 - 0.6
    → Seller handles everything
    → Score depends on seller's shipping track record

SHIPPING SPEED:
  Same-day: 1.0
  1-day Prime: 0.9
  2-day Prime: 0.8
  3-5 day standard: 0.4
  7-14 day: 0.1

SELLER METRICS:
  score = (1 - ODR/0.01) × 0.4 +           // ODR weight
          (1 - late_ship_rate/0.04) × 0.3 + // Late shipment weight
          (1 - cancel_rate/0.025) × 0.2 +   // Cancellation weight
          seller_rating/5.0 × 0.1           // Overall rating weight
```

**Buy Box Rotation (The Surprising Truth)**:
```
IMPORTANT: Buy Box is NOT always awarded to the single highest scorer!

Amazon ROTATES the Buy Box among top-scoring offers:
  - If Offer A scores 0.92 and Offer B scores 0.89:
    → Offer A wins Buy Box ~60% of the time
    → Offer B wins ~30% of the time
    → Others share ~10%
    
  - Rotation uses weighted random selection based on scores
  - This incentivizes competition (multiple sellers stay competitive)
  - Prevents monopoly (one seller dominating all Buy Box time)
  
Implementation:
  buy_box_winner = weighted_random_choice(
    offers_sorted_by_score,
    weights = [softmax(score_i / temperature)]  // temperature controls rotation
  )
  
  Temperature:
    Low (0.1): winner-take-all (highest score always wins)
    Medium (0.5): moderate rotation (Amazon's typical setting)
    High (1.0): nearly equal distribution (rare, used during seller disputes)
```

**Buy Box Recomputation Triggers**:
```
Recompute Buy Box for ASIN when:
  1. Any offer price changes → immediate recompute
  2. Any offer inventory changes (in-stock ↔ out-of-stock) → immediate
  3. Seller metrics update (weekly batch) → batch recompute all affected ASINs
  4. New offer added or existing offer removed → immediate
  5. Time-based refresh: every 5 minutes regardless → prevent staleness
  
Implementation:
  Kafka consumer reads from "offer-updates" topic
  For each ASIN with a change:
    1. Fetch all active offers for this ASIN from DynamoDB
    2. Filter to eligible offers only
    3. Compute buy_box_score for each
    4. Select winner (with rotation)
    5. Write BuyBoxResult to Redis: key "buybox:{asin}"
    6. If winner changed → invalidate CDN cache for detail page
    
  Latency: offer change → Buy Box update → Redis write → ~10 seconds
```

**Interview Talking Points**:
- "Buy Box uses landed price (item + shipping), not just item price — this is why free-shipping offers often win."
- "FBA gets a massive scoring advantage because Amazon can guarantee delivery quality."
- "Buy Box rotates among top scorers, not winner-take-all — incentivizes competition."
- "Recomputation triggered by any offer change via Kafka, result cached in Redis for sub-ms detail page reads."

---

### Deep Dive 2: Offer Validation Pipeline — Preventing Bad Listings

**The Problem**: 10M offer updates/day. Some are invalid: suspended sellers, predatory pricing, restricted products, counterfeit risk. We must catch these before they go live.

**Validation Pipeline (Sequential Checks)**:
```
Offer submitted → Validation Pipeline:

Check 1: SELLER VALIDATION (5ms)
  - Is seller account active? → DynamoDB lookup
  - Is seller suspended/under review? → reject with reason
  - Is seller authorized for this brand? → brand registry check
  - If 1P vendor: is purchase order active? → vendor system check
  
Check 2: ASIN VALIDATION (5ms)
  - Does ASIN exist in catalog? → Catalog Service lookup
  - Is ASIN active/not suppressed? → check catalog status
  - Is ASIN restricted (hazmat, age-restricted, gated category)? → restriction DB
  - For gated categories: does seller have approval? → authorization check
  
Check 3: PRICE VALIDATION (10ms)
  - Is price > $0? → basic sanity
  - Is price within category norms? 
    → Compare to median price for this ASIN
    → If offer > 3× median → flag "PRICE_TOO_HIGH" → suppress
    → If offer < 0.3× median → flag "SUSPICIOUSLY_LOW" → fraud review
  - MAP (Minimum Advertised Price) check:
    → If brand has MAP agreement, offer must be ≥ MAP → suppress if below
  - Is price change > 50% in last hour? → velocity check → human review
  
Check 4: INVENTORY VALIDATION (5ms)
  - Quantity ≥ 0?
  - For FBA: verify Amazon warehouse has this inventory (FC inventory check)
  - For FBM: trust seller's declared quantity (verified by order fulfillment)
  
Check 5: COMPLIANCE CHECKS (15ms)
  - Product recalls: is this ASIN on a recall list? → block all offers
  - Import restrictions: can this product be sold in this marketplace?
  - Intellectual property: any IP complaints on this ASIN? → restrict new sellers
  - Safety certifications: does product category require certifications?

Total validation: ~40ms per offer (checks run in pipeline, some parallelized)

Outcomes:
  PASS → status = ACTIVE → proceed to Buy Box computation
  FAIL_SOFT → status = SUPPRESSED → seller notified, can fix and resubmit
  FAIL_HARD → status = BLOCKED → seller flagged, account review triggered
```

**Batch Validation (Daily)**:
```
Daily batch job re-validates ALL 2B active offers:
  - Seller accounts may have been suspended since last check
  - ASINs may have been recalled
  - Brand authorization may have been revoked
  - Price norms may have shifted
  
Process: Spark job reads all offers from DynamoDB → validates → 
  suppresses newly-invalid offers → publishes changes to Kafka
  
This catches offers that were valid at submission but became invalid later.
```

**Interview Talking Points**:
- "Sequential validation pipeline with fail-fast: cheapest checks first (seller active? 5ms), expensive checks last (compliance 15ms)."
- "Price velocity check: > 50% change in 1 hour triggers human review — prevents both pricing errors and price gouging."
- "Daily batch re-validation catches offers that became invalid after submission (seller suspended, product recalled)."

---

### Deep Dive 3: 1P (Vendor/Amazon Retail) vs 3P (Seller) Offer Pipeline Differences

**The Problem**: Amazon has TWO fundamentally different offer sources. 1P offers come from Amazon buying wholesale from brands/vendors. 3P offers come from marketplace sellers. The pipelines are different but converge at the Buy Box.

**1P (First-Party / Amazon Retail) Pipeline**:
```
Vendor (e.g., Apple) ──→ Vendor Central ──→ Amazon Retail Pipeline

1. Vendor sends catalog data + wholesale cost via EDI/API
2. Amazon Retail BUYS inventory at wholesale price
3. Amazon Retail SETS the customer price:
   retail_price = wholesale_cost × markup_factor + margin_target
   
   Pricing is done by AUTOMATED PRICING ALGORITHMS:
   - Competitive matching: match other retailers (Best Buy, Walmart)
   - Demand-based: higher price during peak, lower during slow periods
   - Cost-plus: ensure minimum margin per unit
   - MAP compliance: respect brand's minimum advertised price
   
4. Amazon Retail OWNS the inventory (in Amazon FCs)
5. Fulfillment: always Amazon-fulfilled (AFN)
6. Returns: handled by Amazon
7. Customer sees: "Ships from and sold by Amazon.com"

Offer creation:
  - Automatic: when Amazon receives inventory from vendor → offer goes live
  - Price: set by Amazon's pricing algorithms (vendor has NO control!)
  - Vendor can only suggest a "list price" (MSRP) — Amazon decides actual selling price

Key difference: Vendor does NOT set the price. Amazon does.
This causes vendor frustration: "Amazon is selling my product below MAP!"
```

**3P (Third-Party Seller) Pipeline**:
```
Seller ──→ Seller Central ──→ Marketplace Pipeline

1. Seller lists product on existing ASIN (or creates new ASIN)
2. Seller SETS their own price
3. Seller OWNS the inventory (either in their warehouse or shipped to Amazon FBA)
4. Fulfillment options:
   FBA: Seller ships inventory to Amazon FC → Amazon fulfills → Prime eligible
   FBM: Seller fulfills from own warehouse → handles shipping → not Prime (usually)
   SFP: Seller fulfills but meets Prime standards → Prime eligible
5. Returns: FBA = Amazon handles; FBM = seller handles
6. Customer sees: "Ships from Amazon / Sold by TechDeals LLC" (FBA)
   or "Ships from and sold by TechDeals LLC" (FBM)

Offer creation:
  - Seller submits offer via API/UI with their chosen price
  - Offer goes through validation pipeline
  - Goes live within 2 minutes
  
Key difference: Seller controls the price.
Many sellers use REPRICING TOOLS that automatically adjust prices every 15 minutes
to compete for the Buy Box.
```

**Convergence at Buy Box**:
```
Both 1P and 3P offers compete for the same Buy Box:

ASIN B0CMZ4S8MV:
  1P Offer: Amazon.com, $1,199.00, Prime 1-day
  3P Offer: TechDeals (FBA), $1,149.00, Prime 2-day
  3P Offer: PhoneWorld (FBM), $1,099.00, 5-day shipping

Buy Box algorithm treats them equally (with some nuances):
  - 1P gets slight "home advantage" in tiebreaker scenarios
  - But 3P with better price + FBA often wins (as in this example)
  - Amazon Retail has perfect seller metrics → always Buy Box eligible
  
CONTROVERSY: There are allegations Amazon Retail uses competitor pricing data
from 3P sellers to undercut them. Regulatory scrutiny ongoing.

For interviews, focus on:
  "Both 1P and 3P offers enter the same Buy Box competition.
   The algorithm is fair on paper: landed price + fulfillment + seller quality.
   FBA sellers compete effectively against Amazon Retail because FBA 
   provides the same fulfillment quality."
```

**Interview Talking Points**:
- "1P vendors don't set the price — Amazon's automated pricing algorithms do. 3P sellers set their own prices."
- "Both 1P and 3P compete equally in the Buy Box — a 3P FBA seller regularly beats Amazon Retail."
- "FBA is the great equalizer: 3P sellers using FBA get the same fulfillment quality (and Buy Box weight) as 1P."

---

### Deep Dive 4: Real-Time Price & Inventory Propagation

**The Problem**: A seller changes their price at 10:30:00 AM. A customer views the detail page at 10:30:45 AM. They should see the new price. But the offer pipeline has many steps — how do we keep it under 2 minutes?

**End-to-End Latency Breakdown**:
```
T+0s:    Seller clicks "Update Price" in Seller Central
T+0.5s:  API writes new price to staging DB
T+1s:    Offer Ingestion Service validates new price
T+1.5s:  Publish to Kafka "offer-updates" topic
T+2s:    Consumer 1 (Offer Store Writer) updates DynamoDB
T+3s:    Consumer 2 (Buy Box Trigger) recomputes Buy Box
T+4s:    Buy Box result written to Redis
T+5s:    Consumer 3 (Search Index Updater) updates Elasticsearch
T+6s:    Consumer 4 (Cache Invalidator) invalidates CDN for this ASIN
T+6.5s:  Next customer request hits origin → gets fresh data

TOTAL: ~7 seconds from seller action to customer-visible change
TARGET: < 2 minutes (we're well within!)
```

**Inventory Propagation (Harder — Higher Volume)**:
```
Inventory changes happen MUCH more frequently than price changes:
  - Every purchase decrements inventory
  - Returns increment inventory
  - FBA inventory moves between warehouses
  - Vendor shipments arrive at FCs
  
Volume: 50M+ inventory changes/day vs 10M price changes/day

Strategy — Tiered update frequency:
  
  CRITICAL (sync, immediate):
    - Inventory goes from >0 to 0 (OUT OF STOCK)
    - Must immediately suppress from Buy Box + detail page
    - Why? Showing "Add to Cart" for out-of-stock = customer frustration + canceled orders
    
  HIGH PRIORITY (async, < 30 seconds):  
    - Inventory goes from 0 to >0 (BACK IN STOCK)
    - Re-add to Buy Box competition
    
  LOW PRIORITY (async, < 5 minutes):
    - Inventory changes from 342 to 341 (sold 1 unit)
    - Doesn't affect Buy Box or customer-visible display
    - Only matters when displaying "Only 3 left in stock!" threshold
    
  Implementation:
    OUT_OF_STOCK events → high-priority Kafka partition → processed immediately
    BACK_IN_STOCK events → high-priority Kafka partition → processed in 30s
    QUANTITY_CHANGE events → normal Kafka partition → processed in 5 min batch
```

**Race Condition: Overselling**:
```
Problem: 
  Inventory = 1 unit left.
  Customer A and Customer B both see "In Stock" and click "Add to Cart" simultaneously.
  Both orders succeed → but only 1 unit exists → OVERSELL!

Solution — Inventory Reservation:
  1. "Add to Cart" does NOT decrement inventory (cart is non-binding)
  2. "Place Order" attempts CONDITIONAL WRITE to inventory:
     UpdateItem: SET quantity = quantity - 1 WHERE quantity > 0
     (DynamoDB conditional expression)
  3. If condition fails (quantity already 0) → order fails → "Sorry, this item is no longer available"
  4. Successful order → inventory decremented atomically

  This is a CONDITIONAL DECREMENT — the core pattern for preventing overselling.
  
  Additional safety: 
    - Reserve inventory for 30 minutes during checkout
    - Release if customer doesn't complete payment
    - DynamoDB conditional writes handle the atomicity
```

**Interview Talking Points**:
- "End-to-end offer update latency is ~7 seconds via Kafka pipeline — well within the 2-minute target."
- "Inventory updates are tiered: out-of-stock is IMMEDIATE (critical UX), quantity change is BATCHED (low priority)."
- "Overselling prevention: DynamoDB conditional decrement — `SET qty = qty - 1 WHERE qty > 0` — atomic."

---

### Deep Dive 5: Detail Page Assembly — Parallel Service Orchestration

**The Problem**: The detail page needs data from 5+ microservices. If called sequentially: 10+5+10+8+15 = 48ms. But at P99 with retries, it could be 200ms+. We need parallel orchestration with graceful degradation.

**Parallel Fan-Out Architecture**:
```
Detail Page Service receives request for ASIN B0CMZ4S8MV

Orchestrator kicks off 5 parallel async calls:

  CompletableFuture<CatalogData> catalogFuture = 
      catalogService.getProduct(asin);                    // 10ms P50
  
  CompletableFuture<BuyBoxResult> buyBoxFuture = 
      buyBoxService.getBuyBox(asin, customerId, zipCode); // 5ms P50
  
  CompletableFuture<OfferSummary> offersFuture = 
      offerService.getOfferSummary(asin);                 // 10ms P50
  
  CompletableFuture<ReviewSummary> reviewsFuture = 
      reviewService.getReviewSummary(asin);               // 8ms P50
  
  CompletableFuture<DeliveryEstimate> deliveryFuture = 
      deliveryService.estimate(asin, zipCode);            // 15ms P50

  // Wait for all with TIMEOUT
  CompletableFuture.allOf(catalogFuture, buyBoxFuture, 
      offersFuture, reviewsFuture, deliveryFuture)
      .get(80, TimeUnit.MILLISECONDS);  // 80ms total timeout
  
  // Assemble page from results
  DetailPage page = assemble(
      catalogFuture.getNow(fallbackCatalog),
      buyBoxFuture.getNow(fallbackBuyBox),
      offersFuture.getNow(fallbackOffers),
      reviewsFuture.getNow(fallbackReviews),
      deliveryFuture.getNow(fallbackDelivery)
  );
```

**Graceful Degradation — What If a Service Is Down?**:
```
Each call has a fallback:

Catalog Service down?
  → Use stale cached data (Redis, last known good, TTL 1 hour)
  → Page shows product info from cache
  → Customer barely notices
  → SEVERITY: Low (data changes rarely)

Buy Box Service down?
  → Use last cached Buy Box winner (Redis, TTL 5 min)
  → Worst case: Buy Box winner might be slightly stale
  → Still better than no Buy Box!
  → SEVERITY: Medium (price might be wrong)

Offer Summary Service down?
  → Show "See all offers" link without count
  → Hide "3 new from $X" line
  → SEVERITY: Low (supplementary info)

Review Service down?
  → Show stars/count from cached data
  → Or hide review section entirely
  → SEVERITY: Low (reviews don't change often)

Delivery Estimation Service down?
  → Show generic "Check delivery date at checkout"
  → Don't show specific date
  → SEVERITY: Medium (customers want to know delivery date)

CRITICAL RULE: 
  The detail page MUST ALWAYS RENDER, even if 4 of 5 services are down.
  Only truly critical data: ASIN exists + at least one offer exists.
  Everything else has a degraded fallback.

Circuit Breaker:
  If a service fails 50% of requests in 30 seconds → OPEN circuit
  → Stop calling that service for 60 seconds
  → Use fallback directly (save the failing service from more load)
  → After 60 seconds → HALF_OPEN → try a few requests
  → If they succeed → CLOSE circuit (service recovered)
```

**Caching Strategy for Detail Page**:
```
Layer 1: CDN (CloudFront) — 30 second TTL
  Cache key: ASIN + marketplace + language
  Hit rate: ~40% (popular ASINs)
  Excludes: personalized data (delivery date, Prime badge)
  
  Personalized parts loaded via JavaScript after page load
  (client-side calls to delivery + buy box APIs)

Layer 2: Application Cache (Redis) — per-service
  Catalog data: 1 hour TTL (changes rarely)
  Buy Box result: 1-5 min TTL (changes on offer updates)
  Reviews: 1 hour TTL (changes slowly)
  
  Combined hit rate: ~80%
  
Layer 3: Source of Truth
  Catalog: DynamoDB
  Offers: DynamoDB
  Buy Box: computed on-the-fly from offers
  Reviews: Review Service DB
  
Invalidation:
  On offer change → invalidate Buy Box cache + CDN
  On catalog change → invalidate catalog cache + CDN
  On review change → invalidate review cache (lazy, not urgent)
```

**Interview Talking Points**:
- "Parallel fan-out with CompletableFuture.allOf() — total latency = max of individual calls, not sum."
- "Graceful degradation: every service call has a fallback. Page MUST render even if 4/5 services are down."
- "Circuit breaker pattern: if service fails 50% in 30 seconds, stop calling it for 60 seconds."
- "Three-layer caching: CDN (30s) → Redis (1-60 min) → DynamoDB (source of truth)."

---

### Deep Dive 6: Offer Data Model in DynamoDB

**The Problem**: 2B offers, 600M ASINs, accessed by multiple patterns: "all offers for an ASIN" (detail page), "all offers by a seller" (seller dashboard), "specific offer" (update).

**DynamoDB Table Design**:
```
Table: Offers

Primary Key:
  Partition Key (PK): ASIN     // "B0CMZ4S8MV"
  Sort Key (SK): OFFER_ID      // "{seller_id}#{condition}" = "A3PJQF5Z#NEW"

This supports the primary access pattern:
  "Get all offers for an ASIN" → Query(PK = "B0CMZ4S8MV")
  Returns all offers sorted by seller_id → filter in application

Attributes:
  PK: "B0CMZ4S8MV"
  SK: "A3PJQF5Z#NEW"
  seller_id: "A3PJQF5Z7Z3Z1Q"
  seller_name: "TechDeals LLC"
  seller_type: "3P"
  price_cents: 114900
  shipping_cents: 0
  landed_price_cents: 114900
  fulfillment: "FBA"
  condition: "NEW"
  quantity: 342
  in_stock: true
  prime_eligible: true
  seller_rating: 4.8
  odr: 0.003
  status: "ACTIVE"
  created_at: "2026-01-15T..."
  updated_at: "2026-04-25T10:30:00Z"
  
  GSI-1-PK: "A3PJQF5Z7Z3Z1Q"  (seller_id)
  GSI-1-SK: "B0CMZ4S8MV#NEW"   (asin#condition)

GSI-1 (Seller Index):
  PK: seller_id
  SK: asin#condition
  Supports: "Get all offers by seller" (Seller Central dashboard)
  Query(GSI-1-PK = "A3PJQF5Z7Z3Z1Q") → all of seller's offers

GSI-2 (Status Index):
  PK: status     // "ACTIVE", "SUPPRESSED"
  SK: updated_at
  Supports: "Get recently suppressed offers" (compliance dashboard)
  
Capacity:
  On-demand mode (auto-scaling)
  Estimated: 100K RCU (reads for detail pages + seller dashboards)
  Estimated: 10K WCU (offer updates)
  
  DynamoDB handles: 2B items, ~1 TB data, auto-partitions across 1000+ partitions
```

**Why DynamoDB (Not PostgreSQL)?**:
```
DynamoDB:
  ✅ 2B rows, unlimited scale (auto-partitioning)
  ✅ Single-digit-ms reads/writes at any scale
  ✅ No connection pool management
  ✅ Conditional writes for inventory (atomic decrement)
  ✅ DynamoDB Streams → Kafka (built-in CDC)
  ✅ Global tables for multi-region
  ❌ No complex JOINs
  ❌ Limited aggregation

PostgreSQL:
  ✅ Rich queries, JOINs, aggregations
  ❌ Scaling to 2B rows requires heavy sharding
  ❌ Connection pool limits at high concurrency
  ❌ Vertical scaling ceiling
  
Decision: DynamoDB for offers (simple access patterns, massive scale)
           PostgreSQL for analytics (complex queries, smaller dataset)
```

**Interview Talking Points**:
- "DynamoDB with composite sort key: PK=ASIN, SK=seller_id#condition → all offers for an ASIN in one query."
- "GSI on seller_id for the seller dashboard — access pattern drives schema design."
- "DynamoDB conditional writes for inventory: `SET qty = qty - 1 WHERE qty > 0` prevents overselling atomically."

---

### Deep Dive 7: Handling the "Offer Explosion" — Hot ASINs

**The Problem**: Most ASINs have 1-5 offers. But popular ASINs (iPhone, AirPods, PS5) can have 500+ offers. Processing and displaying these efficiently requires special handling.

**Hot ASIN Detection and Handling**:
```
Problem: ASIN "B0CMZ4S8MV" (iPhone) has 500 offers.
  - Fetching all 500 offers for Buy Box computation → expensive
  - Displaying all 500 on the offer listing page → slow page load
  - 500 sellers all repricing simultaneously → Kafka consumer lag

Solutions:

1. BUY BOX: Only consider top-50 eligible offers (pre-sorted by landed price)
   - Fetch: Query DynamoDB for ASIN, limit 100, filter to ACTIVE + eligible
   - Score: Only compute buy_box_score for top-50 by price
   - Result: Buy Box winner from a relevant subset (not all 500)
   
   Why this works: The Buy Box winner is almost always in the lowest-30 priced offers.
   Checking all 500 wastes computation on $2000 offers that will never win.

2. OFFER LISTING PAGE: Server-side pagination
   - Page 1: show top-20 offers sorted by landed price
   - Lazy load: "Show more" loads next 20
   - Pre-sort at write time: maintain a sorted index in DynamoDB/Redis
   
3. REPRICING STORMS: Rate-limit offer updates per ASIN
   - If ASIN gets > 100 offer updates/minute → batch them
   - Buy Box recomputes at most every 30 seconds per ASIN (debounce)
   - Prevents one hot ASIN from consuming all processing resources

4. CACHING SPECIFICALLY FOR HOT ASINS:
   - Pre-compute and cache Buy Box for top-10K ASINs every 30 seconds
   - Don't wait for event-driven recomputation
   - These ASINs cover 80% of traffic (power law distribution)
```

**Interview Talking Points**:
- "Power law: 10K ASINs cover 80% of detail page views — pre-compute Buy Box for these."
- "For hot ASINs with 500+ offers, only score top-50 by landed price for Buy Box — cheaper offers always win."
- "Debounce repricing storms: max 1 Buy Box recomputation per 30 seconds per ASIN."

---

### Deep Dive 8: Offer Suppression & Seller Accountability

**The Problem**: Bad offers hurt customers. Amazon must suppress offers that violate policies, and hold sellers accountable.

**Suppression Reasons and Actions**:
```
PRICE_TOO_HIGH:
  Trigger: offer price > 3× fair market value
  Action: Suppress offer, notify seller
  Example: $5,000 for a $100 headphone → price gouging
  
PRICE_TOO_LOW (suspected counterfeit):
  Trigger: offer price < 30% of average for brand product
  Action: Suppress + trigger IP team review
  Example: $50 for a genuine iPhone (too good to be true)
  
SELLER_METRICS_FAILED:
  Trigger: ODR > 1%, late shipment > 4%, etc.
  Action: Lose Buy Box eligibility (offer stays but never wins Buy Box)
  
SELLER_SUSPENDED:
  Trigger: Account suspension (fraud, IP violations, policy violations)
  Action: ALL seller's offers go INACTIVE immediately
  
PRODUCT_RECALL:
  Trigger: Safety recall issued for this ASIN
  Action: ALL offers for this ASIN blocked across ALL sellers
  
OUT_OF_STOCK_TOO_LONG:
  Trigger: Offer listed but 0 inventory for > 30 days
  Action: Auto-inactivate offer (clean up stale listings)
  
LISTING_QUALITY:
  Trigger: Missing required attributes, wrong category, misleading title
  Action: Suppress until seller fixes the listing

Suppression Pipeline:
  Suppression event → Kafka → 
    1. Update offer status in DynamoDB
    2. Remove from Buy Box competition (Redis)
    3. Remove from search index (Elasticsearch)
    4. Send notification to seller (email + Seller Central alert)
    5. Log for compliance audit
  
  Total time to suppress: < 30 seconds (critical for customer safety)
```

**Interview Talking Points**:
- "Offer suppression is immediate for safety issues (recalls, fraud) — < 30 seconds from detection to removal."
- "Price guardrails: > 3× fair market value → suppress. < 30% → fraud review. Protects both buyers and legitimate sellers."
- "Seller metrics cascading: ODR > 1% doesn't remove offers but removes Buy Box eligibility — significant revenue impact motivates seller improvement."

---

## What is Expected at Each Level?

### Mid-Level
- Explain the ASIN/Offer distinction (product vs seller listing)
- Design basic offer ingestion → store → display pipeline
- Understand Buy Box concept at high level (price + seller quality)
- Use DynamoDB for offer storage with ASIN as partition key
- Basic caching strategy for detail page

### Senior
- Buy Box algorithm with multiple scoring factors (price, fulfillment, metrics)
- Buy Box rotation (not winner-take-all)
- 1P vs 3P pipeline differences and convergence at Buy Box
- Real-time propagation via Kafka with tiered priority (out-of-stock = immediate)
- Parallel detail page assembly with graceful degradation
- DynamoDB data model with GSIs for multiple access patterns
- Inventory overselling prevention with conditional decrements
- Offer validation pipeline with sequential checks

### Staff
- Full Buy Box scoring formula with weight analysis
- Repricing storm handling and debouncing for hot ASINs
- Three-layer caching with invalidation strategy
- Circuit breaker pattern for service dependencies
- Offer suppression pipeline with compliance considerations
- 1P automated pricing vs 3P seller-controlled pricing dynamics
- End-to-end latency analysis (7s from seller change to customer-visible)
- Capacity planning for 2B offers, 600M ASINs
- Discuss trade-offs: Buy Box rotation vs winner-take-all, stale cache vs freshness, seller autonomy vs customer protection
