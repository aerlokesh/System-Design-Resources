# Design Nearby/Geo Search (Yelp/Google Maps) — Hello Interview Framework

> **Question**: Design a geo-spatial search system like Yelp or Google Maps that lets users find nearby businesses, restaurants, and points of interest. Support proximity search ("restaurants within 2 miles"), text + location queries ("sushi near me"), filtering by attributes, and ranking by relevance/distance/rating.
>
> **Asked at**: Google, Uber, Yelp, DoorDash, Airbnb, Meta, Amazon
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
1. **Proximity Search**: Given a user's location (lat/lng), find businesses within a radius (e.g., "restaurants within 2 miles"). Return results sorted by distance.
2. **Text + Location Search**: "sushi restaurants near Times Square" — combine keyword relevance with geo-proximity.
3. **Filtering**: Category (restaurant, gas station, hotel), price level ($ - $$$$), rating (4+ stars), currently open, accepts credit cards, delivery available, etc.
4. **Ranking**: Blend of distance, rating, review count, relevance to query, business popularity, and recency of reviews.
5. **Map Viewport Search**: "Show all coffee shops visible on the current map viewport" (bounding box query). Re-query as user pans/zooms.
6. **Business Detail Pages**: Full business info — hours, photos, reviews, menu, directions.

#### Nice to Have (P1)
- Auto-complete for business names and categories
- "Popular times" (crowd density by hour)
- Personalized results (user's past visits, cuisine preferences)
- Real-time updates (business opens/closes, changes hours)
- Photo search (find businesses with specific ambiance)
- Driving directions integration

#### Below the Line (Out of Scope)
- Review system CRUD (assume separate service)
- Business owner dashboard / claim management
- Ad bidding / sponsored listings
- Navigation / routing engine
- Menu ordering / reservations

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Search Latency** | P50 < 100ms, P95 < 300ms, P99 < 500ms | Mobile users expect fast response |
| **Throughput** | 50K QPS (peak 100K during mealtimes) | 200M DAU × 2 searches/day |
| **Availability** | 99.99% | Search is the core product |
| **Data Size** | 200M businesses globally | Yelp has ~6M, Google has 200M+ |
| **Freshness** | Business info updates within 1 hour | Hours, pricing, menu changes |
| **Geo Coverage** | Global (195 countries) | Multi-region deployment |
| **Consistency** | Eventual (minutes) | Slightly stale business info is fine |
| **Location Accuracy** | Within 10 meters | GPS precision level |

### Capacity Estimation

```
Businesses:
  Total: 200M businesses globally
  Active (claimed, has reviews): 50M
  Average business record: 2KB (name, address, hours, attributes)
  Total data: 200M × 2KB = 400 GB (fits on single machine, replicated for throughput)

Traffic:
  DAU: 200M users
  Searches/day: 400M
  QPS: 400M / 86400 ≈ 4,600 avg → 50K peak (mealtimes)
  Map viewport queries: 2B/day (as users pan/zoom) = 23K QPS

Per Request:
  Inbound: ~200 bytes (lat, lng, radius, query, filters)
  Outbound: ~20KB (20 results × 1KB each with distance, rating, snippet)

Storage:
  Business data (Postgres): 400 GB (with indices: 800 GB)
  Geo index (in-memory): ~10 GB (QuadTree / Geohash)
  Search index (Elasticsearch): 200M × 1KB = 200 GB
  Photos: 200M businesses × 10 photos × 200KB = 400 TB (CDN)
  Reviews: 2B reviews × 500B = 1 TB

Infrastructure:
  API servers: 20 (2,500 QPS each)
  Elasticsearch nodes: 10 (data) + 3 (master)
  Postgres: primary + 5 read replicas per region
  Redis: 10 nodes (cache cluster)
  3 regions globally
```

---

## 2️⃣ Core Entities

### Entity 1: Business (Place)
```java
public class Business {
    String businessId;           // UUID
    String name;                 // "Joe's Pizza"
    String description;          // "Best New York style pizza since 1975"
    GeoPoint location;           // {lat: 40.7308, lng: -73.9975}
    Address address;             // Structured address
    String phone;
    String website;
    List<String> categories;     // ["Pizza", "Italian", "Restaurant"]
    int priceLevel;              // 1-4 ($-$$$$)
    float rating;                // 4.3
    int reviewCount;             // 2847
    BusinessHours hours;         // Per-day open/close times
    Map<String, Boolean> attrs;  // {"delivery": true, "outdoor_seating": true}
    List<String> photoUrls;
    boolean isClaimed;           // Business owner has claimed this listing
    boolean isTemporarilyClosed;
    Instant updatedAt;
}

public class GeoPoint {
    double latitude;     // -90 to 90
    double longitude;    // -180 to 180
}
```

### Entity 2: SearchQuery
```java
public class SearchQuery {
    String queryText;            // "sushi" (nullable for pure proximity search)
    GeoPoint center;             // User's location or map center
    double radiusMeters;         // 3218 (2 miles)
    BoundingBox viewport;        // For map searches
    List<String> categories;     // ["Restaurant", "Sushi"]
    Integer minRating;           // 4
    Integer priceLevel;          // 2
    boolean openNow;             // true
    String sortBy;               // "distance" | "rating" | "relevance" | "review_count"
    int limit;                   // 20
    String cursor;               // Pagination
}

public class BoundingBox {
    GeoPoint topLeft;            // NW corner
    GeoPoint bottomRight;        // SE corner
}
```

### Entity 3: SearchResult
```java
public class SearchResult {
    List<BusinessHit> businesses;
    long totalHits;
    Map<String, List<Facet>> facets;  // {"category": [...], "price_level": [...]}
    String nextCursor;
    GeoPoint searchCenter;            // Where the search was centered
}

public class BusinessHit {
    String businessId;
    String name;
    float rating;
    int reviewCount;
    double distanceMeters;       // Distance from search center
    String address;
    boolean isOpenNow;
    String photoUrl;             // Primary thumbnail
    String snippet;              // "Known for their hand-tossed..."
    float relevanceScore;        // Internal ranking score
}
```

---

## 3️⃣ API Design

### 1. Nearby Search
```
GET /api/v1/search/nearby?lat=40.7308&lng=-73.9975&radius=3218&q=sushi&min_rating=4&open_now=true&sort=relevance&limit=20

Response (200 OK):
{
  "businesses": [
    {
      "business_id": "biz_abc123",
      "name": "Sushi Nakazawa",
      "rating": 4.8,
      "review_count": 1847,
      "distance_meters": 450,
      "address": "23 Commerce St, New York, NY",
      "is_open_now": true,
      "price_level": 4,
      "categories": ["Sushi", "Japanese"],
      "photo_url": "https://cdn.yelp.com/photos/biz_abc123/primary.webp",
      "snippet": "Omakase experience with fresh fish flown in daily from Tsukiji..."
    }
    // ... more results
  ],
  "total_hits": 47,
  "facets": {
    "price_level": [
      {"value": "1", "count": 12},
      {"value": "2", "count": 18},
      {"value": "3", "count": 11},
      {"value": "4", "count": 6}
    ]
  },
  "search_center": {"lat": 40.7308, "lng": -73.9975}
}
```

### 2. Map Viewport Search
```
GET /api/v1/search/viewport?ne_lat=40.76&ne_lng=-73.97&sw_lat=40.72&sw_lng=-74.01&q=coffee&limit=50

Response (200 OK):
{
  "businesses": [
    {
      "business_id": "biz_xyz789",
      "name": "Blue Bottle Coffee",
      "lat": 40.7425,
      "lng": -73.9892,
      "rating": 4.5,
      "price_level": 2,
      "is_open_now": true
    }
    // ... clustered markers for the map
  ],
  "cluster_markers": [
    {"lat": 40.75, "lng": -73.98, "count": 23, "label": "23 coffee shops"}
  ],
  "total_in_viewport": 127
}
```

---

## 4️⃣ Data Flow

### Search Flow
```
User taps "Sushi near me" → Mobile App determines location (GPS)
  → CDN/Edge → Load Balancer → Search API
  → Query Parser (extract text query, geo center, filters)
  → Geo Filter (find businesses within radius using spatial index)
  → Text Search (if query text provided, search within geo-filtered set)
  → Apply Filters (rating, price, open_now)
  → Ranking (blend distance + relevance + rating + popularity)
  → Fetch business details for top-K results
  → Return to user
```

### Business Update Flow
```
Business owner updates hours → Business Service (CRUD)
  → Write to Postgres (source of truth)
  → CDC (Change Data Capture) event → Kafka
  → Search Indexer consumes event → updates Elasticsearch
  → Geo Index Updater updates spatial index (if location changed)
  → Cache invalidation (Redis)
```

---

## 5️⃣ High-Level Design

```
┌───────────────────────────────────────────────────────────────┐
│                        SEARCH PATH                             │
│                                                                 │
│  User → CDN → LB → Search API Service                         │
│                        │                                        │
│              ┌─────────┼──────────┐                            │
│              ▼         ▼          ▼                             │
│         ┌─────────┐ ┌─────────┐ ┌──────────┐                  │
│         │ Geo     │ │ Text    │ │ Business │                   │
│         │ Index   │ │ Search  │ │ Details  │                   │
│         │(spatial)│ │ (ES)    │ │ Cache    │                   │
│         └────┬────┘ └────┬────┘ └────┬─────┘                  │
│              └───────────┼───────────┘                         │
│                          ▼                                      │
│                    ┌───────────┐                                │
│                    │  Ranker   │                                │
│                    │ (blend    │                                │
│                    │  distance │                                │
│                    │  + score) │                                │
│                    └─────┬─────┘                                │
│                          ▼                                      │
│                      Response                                   │
└─────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│                    DATA / UPDATE PATH                           │
│                                                                 │
│  Business Service → Postgres (source of truth)                 │
│        │                                                        │
│        ▼                                                        │
│     Kafka (CDC events)                                         │
│        │                                                        │
│   ┌────┼─────────────┐                                         │
│   ▼    ▼             ▼                                          │
│  ES   Geo Index   Redis Cache                                  │
│  Index Updater    Invalidation                                  │
└───────────────────────────────────────────────────────────────┘
```

### Key Technology Choices

| Component | Technology | Why |
|-----------|-----------|-----|
| **Geo Index** | Geohash + QuadTree / Elasticsearch geo queries | Efficient spatial lookup |
| **Text Search** | Elasticsearch | Full-text + geo queries in one system |
| **Primary DB** | PostgreSQL + PostGIS | Relational + spatial extension |
| **Cache** | Redis (with geo commands) | Fast proximity lookups for hot areas |
| **CDN** | CloudFront | Static assets (photos, map tiles) |
| **CDC** | Debezium → Kafka | Real-time sync from Postgres to ES |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Geo-Spatial Indexing — Geohash vs QuadTree vs R-Tree

**The Problem**: "Find all businesses within 2 miles of (40.73, -73.99)" requires checking 200M businesses. Linear scan = seconds. We need a spatial index.

**Option A: Geohash**
```
How it works:
  - Divide Earth's surface into a grid of cells
  - Each cell has a string hash: "dr5ru7" (6 chars = ~610m × 610m)
  - Longer hash = smaller cell = more precision
  
  Precision levels:
    4 chars: ~39km × 19km (city level)
    5 chars: ~5km × 5km (neighborhood)
    6 chars: ~610m × 610m (block)
    7 chars: ~76m × 76m (building)
    8 chars: ~19m × 19m (precise)

  Key property: nearby points share a common prefix!
    "dr5ru7" and "dr5ru6" are adjacent cells

Radius query with Geohash:
  1. Compute geohash of user's location: "dr5ru7"
  2. For 2-mile radius, need precision 5 → "dr5ru"
  3. Get all 8 neighbors: "dr5rt", "dr5rv", "dr5rg", ... (9 cells total)
  4. Fetch all businesses in these 9 cells from hash table
  5. Post-filter: compute exact distance, keep those within 2 miles

Storage: Inverted index — geohash_prefix → [businessId1, businessId2, ...]
  Pre-build at multiple precision levels (5, 6, 7)
```

**Option B: QuadTree**
```
How it works:
  - Recursively divide 2D space into 4 quadrants
  - Split a quadrant when it contains > K points (e.g., K=100)
  - Dense areas (Manhattan) → deep tree; sparse areas (desert) → shallow
  
  Node structure:
    QuadTreeNode {
      BoundingBox bounds;
      List<Business> businesses;   // if leaf (≤ K businesses)
      QuadTreeNode[] children;     // NE, NW, SE, SW (if internal)
    }

Radius query:
  1. Start at root
  2. If node's bounding box doesn't intersect search circle → skip (prune!)
  3. If leaf → check each business for distance ≤ radius
  4. If internal → recurse into children that intersect
  
  Typical: check ~20 leaf nodes (out of millions) for a 2-mile radius

Pros: adaptive to density (NYC gets more cells, Sahara gets fewer)
Cons: harder to distribute across machines (tree is hierarchical)
```

**Option C: S2 Geometry (Google's approach)**
```
How it works:
  - Projects Earth's sphere onto a cube, then unfolded to 6 face cells
  - Each face recursively divided into 4 → hierarchical cell IDs
  - Cell IDs are 64-bit integers → efficient storage and comparison
  - Supports covering: approximate any shape (circle, polygon) with a set of cells
  
  Region covering for "2 mile radius around (40.73, -73.99)":
    → Returns ~8-12 S2 cell ranges that cover the circle
    → Query: WHERE s2_cell_id BETWEEN range1_start AND range1_end
              OR s2_cell_id BETWEEN range2_start AND range2_end ...

Pros: handles edge cases (poles, date line), efficient for polygons
Used by: Google Maps, Uber's H3 (hexagonal variant), DynamoDB geo library
```

**My Choice: Geohash + Elasticsearch for simplicity; S2 for advanced**
```
For most interviews: Geohash is simplest to explain
  - "I'd use geohash at precision 6 (610m cells), pre-compute for each business"
  - "Proximity query: find the 9 surrounding cells, fetch businesses, filter by exact distance"
  
For staff level: mention S2 / H3
  - "Google uses S2 cells which handle spherical geometry correctly"
  - "Uber uses H3 (hexagonal) for uniform area coverage"
```

**Interview Talking Points**:
- "Geohash converts 2D proximity to 1D prefix matching — nearby points share prefixes."
- "QuadTree adapts to density: dense areas get more cells, sparse areas fewer."
- "For production at Google scale, S2 cells are preferred — handle poles, date lines, and arbitrary shapes."

---

### Deep Dive 2: Ranking — Blending Distance, Relevance, and Quality

**The Problem**: User searches "sushi near me." Do we show the closest sushi place (100m away, 3.2 stars) or a great one (800m away, 4.8 stars)? Ranking must balance multiple signals.

**Ranking Function**:
```
score(business, query) = 
    w1 × relevance_score(query, business)     // Text match quality
  + w2 × distance_score(user, business)       // Closer = higher
  + w3 × quality_score(business)              // Rating + reviews
  + w4 × popularity_score(business)           // Check-ins, clicks
  + w5 × freshness_score(business)            // Recent reviews boost
  + w6 × personalization_score(user, business) // User preferences

Where:
  distance_score = 1 / (1 + distance_km)      // Exponential decay
  quality_score = rating × log(1 + review_count)  // Rating weighted by review count
  popularity_score = log(1 + monthly_views)
```

**Distance Decay Models**:
```
Option A: Linear decay
  score = max(0, 1 - distance / max_radius)
  Problem: treats 100m and 500m very differently, but 1.5km and 1.9km the same

Option B: Gaussian decay (preferred)
  score = exp(-0.5 × (distance / sigma)²)
  sigma depends on context: walking (500m), driving (5km), cycling (2km)

Option C: Logarithmic
  score = 1 / (1 + log(1 + distance))
  Gentle decay — good for driving scenarios
  
Context-aware:
  If user is walking → sigma = 500m (strong distance preference)
  If user is driving → sigma = 5km (weaker distance preference)
  If "best sushi in city" → sigma = 20km (quality matters more than distance)
```

**Learning-to-Rank for Production**:
```
Instead of hand-tuned weights, train ML model:
  
Features:
  - Distance (meters)
  - Text BM25 score
  - Rating (stars)
  - Review count
  - Photos count
  - Is currently open
  - Price level match (if user specified)
  - Category match confidence
  - Historical click-through rate for this business
  - User's affinity for this category
  
Model: LambdaMART (GBDT for ranking)
  - Trained on click data: (query, [clicked businesses, skipped businesses])
  - Pairwise loss: clicked > skipped at same position
  
  This naturally learns the optimal blending of all signals!
```

**Interview Talking Points**:
- "Gaussian distance decay with context-aware sigma: walking queries use tight sigma (500m), driving uses loose (5km)."
- "Quality score: rating × log(review_count) — a 4.8-star restaurant with 1 review shouldn't rank above 4.5 with 2000 reviews."
- "In production, I'd use LambdaMART trained on click data rather than hand-tuned weights."

---

### Deep Dive 3: Map Viewport Search & Clustering

**The Problem**: User views a map of NYC. There are 10,000 restaurants in the viewport. We can't show 10,000 markers — the map becomes unusable. We need to cluster.

**Viewport Query**:
```
Query: "All businesses in bounding box (NE: 40.76,-73.97, SW: 40.72,-74.01)"

Execution:
  1. Convert bounding box to geohash ranges (or S2 cell ranges)
  2. Fetch all businesses in those cells
  3. Post-filter: keep only those strictly within the bounding box
  4. Apply zoom-dependent behavior:
     - Zoomed in (< 500 businesses visible) → show individual markers
     - Zoomed out (> 500 businesses) → cluster into groups
```

**Server-Side Clustering**:
```
Algorithm: Grid-based clustering (fast, deterministic)
  1. Overlay grid on viewport (e.g., 8×6 grid = 48 cells)
  2. Assign each business to a grid cell
  3. Cells with > 3 businesses → collapse into cluster marker
  4. Cells with ≤ 3 → show individual markers
  
  Cluster marker: {lat: centroid_lat, lng: centroid_lng, count: 23}

Alternative: Supercluster (hierarchical, used by Mapbox)
  - Pre-compute cluster hierarchy at index time
  - Store clusters at each zoom level (0-20)
  - For zoom level Z, return pre-computed clusters
  - O(1) lookup per zoom level (pre-built)
```

**Optimization for Map Panning**:
```
Problem: User pans the map → new viewport → new query → 200ms wait

Solutions:
  1. Over-fetch: fetch 2× the viewport area so small pans don't trigger re-query
  2. Client-side caching: cache previously fetched markers, only request new area
  3. Incremental loading: send delta (new markers to add, old markers to remove)
  4. Debounce: wait 300ms after user stops panning before querying
```

**Interview Talking Points**:
- "Grid-based clustering: divide viewport into cells, collapse dense cells into cluster markers."
- "Over-fetch 2× viewport area so small pans are handled client-side without re-querying."
- "Pre-computed cluster hierarchy (Supercluster) gives O(1) lookup per zoom level."

---

### Deep Dive 4: "Open Now" Filter — Time Zone Handling

**The Problem**: "Show open restaurants" seems simple but is surprisingly complex: time zones, DST, holidays, special hours.

**Business Hours Model**:
```java
public class BusinessHours {
    Map<DayOfWeek, List<TimeRange>> regularHours;
    // {"MONDAY": [{"open": "11:00", "close": "22:00"}],
    //  "FRIDAY": [{"open": "11:00", "close": "02:00"}]}  // crosses midnight!
    
    List<SpecialHours> specialHours;
    // {"date": "2026-12-25", "closed": true}
    // {"date": "2026-12-31", "open": "11:00", "close": "01:00"}
    
    String timeZone;  // "America/New_York" (IANA timezone)
}
```

**Query-Time Evaluation**:
```
To check if business is open:
  1. Get current time in business's timezone:
     now_local = now_utc.atZone(business.timeZone)
  2. Get today's hours (check special hours first, then regular)
  3. Check if now_local is within any open range
  4. Handle cross-midnight: if close < open, business is open across midnight
     e.g., "FRIDAY 11:00-02:00" means open until Saturday 02:00

Why not pre-compute "is_open" in the index?
  - Would need to update every minute for every business → too expensive
  - Instead: store hours in index, evaluate at query time
  
  Optimization: store nextOpen and nextClose timestamps (UTC)
  - Updated hourly via batch job
  - At query time: if now_utc < nextClose → open; else → closed
  - Handles 99% of cases; fall back to full evaluation for edge cases
```

**Interview Talking Points**:
- "Store business hours with IANA timezone, evaluate openness at query time."
- "Pre-compute nextOpen/nextClose UTC timestamps hourly as a fast-path filter."
- "Handle cross-midnight hours by checking if close time is before open time — if so, it spans to the next day."

---

### Deep Dive 5: Handling Hot Areas (Manhattan) vs Cold Areas (Rural)

**The Problem**: Manhattan has 50,000 restaurants in a 10-mile radius. Rural Kansas has 3. Our system must handle both efficiently.

**Adaptive Radius**:
```
If user searches "restaurants near me" in Manhattan:
  - Within 0.5 miles: 200+ results → show top 20, no need for wider radius
  
If user searches in rural area:
  - Within 0.5 miles: 0 results
  - Expand to 2 miles: 1 result
  - Expand to 10 miles: 8 results
  
Adaptive strategy:
  1. Start with default radius based on category:
     - Restaurant: 2 miles
     - Gas station: 5 miles
     - Hospital: 10 miles
  2. If results < min_results (5):
     - Double the radius and re-query
     - Repeat up to 3 times (max radius 50 miles)
  3. If results > max_display (100):
     - Tighten ranking, don't expand further
     - Show "Showing top results in your area"
```

**Density-Aware Sharding**:
```
Problem: If we shard geographically, Manhattan shard is 1000× larger than Kansas shard

Solutions:
  1. QuadTree-based sharding: dense areas get more, smaller shards
     - Manhattan: 50 shards (each covering a few blocks)
     - Kansas: 1 shard (covering the entire state)
  
  2. Geohash-based with variable precision:
     - Dense areas: geohash precision 7 (76m cells)
     - Sparse areas: geohash precision 4 (39km cells)
     - Ensures each shard has ~100K businesses
  
  3. Elasticsearch handles this: automatic shard balancing
     - Just use geo_distance queries, ES optimizes internally
```

**Interview Talking Points**:
- "Adaptive radius expansion: start tight, expand if too few results, up to 3 expansions."
- "QuadTree naturally handles density imbalance — Manhattan gets many small cells, rural areas get few large cells."
- "Category-aware default radius: restaurants 2mi, gas stations 5mi, hospitals 10mi."

---

### Deep Dive 6: Caching Strategy for Geo Queries

**The Problem**: Millions of users in NYC searching "restaurants near me" — many are in the same neighborhood. Can we cache?

**Challenge**: Every user has a unique (lat, lng) → unique query → cache miss!

**Solution: Geohash-Based Cache Bucketing**:
```
Instead of caching per exact (lat, lng), round to geohash cell:
  User at (40.73081, -73.99753) → geohash "dr5ru7c" (precision 7, ~76m)
  User at (40.73089, -73.99741) → geohash "dr5ru7c" (same cell!)

Cache key: "{query}:{geohash6}:{filters_hash}"
  Example: "sushi:dr5ru7:rating4_opennow"

Cache hit rate:
  NYC has ~50K precision-6 cells
  400K NYC queries/day → ~8 queries per cell per day
  With top 10K cells pre-warmed: ~60% cache hit rate

Redis implementation:
  SET "search:sushi:dr5ru7:opennow" → serialized results (TTL: 5 minutes)
  
  Warm-up: pre-compute results for top-100 queries × top-1000 geohash cells
  = 100K cache entries, ~2GB in Redis
```

**Map Tile Caching**:
```
For viewport queries: cache by map tile (z/x/y)
  At zoom level 15 (city blocks): cache business markers per tile
  Tile key: "tile:15:9648:12320" → [list of business markers]
  
  Pre-compute for all zoom levels, invalidate on business update
  CDN-cacheable: most map tiles are identical for all users
```

**Interview Talking Points**:
- "Geohash-based cache bucketing: round user's location to a cell, cache results per cell."
- "This converts millions of unique (lat, lng) queries into thousands of cacheable cell queries."
- "Map viewport queries cached as tiles — CDN-friendly, same tile for all users at same zoom."

---

## What is Expected at Each Level?

### Mid-Level
- Use Geohash or QuadTree for spatial indexing
- Design basic search flow: geo filter → text match → sort
- Handle radius queries
- Basic caching strategy

### Senior
- Compare Geohash vs QuadTree vs S2 cells with trade-offs
- Multi-signal ranking with distance decay
- Map viewport search with clustering
- "Open now" with timezone handling
- Geohash-based cache bucketing
- Adaptive radius for sparse areas

### Staff
- S2/H3 cell coverage for arbitrary shapes
- LambdaMART learning-to-rank on click data
- Density-aware sharding strategy
- Pre-computed cluster hierarchy (Supercluster)
- Over-fetch and incremental map loading
- Full capacity planning and multi-region deployment
- Discuss trade-offs: precision vs recall in geo-filtering, distance vs quality in ranking
