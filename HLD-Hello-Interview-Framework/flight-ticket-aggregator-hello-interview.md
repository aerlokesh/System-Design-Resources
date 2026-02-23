# Design a Flight Ticket Aggregator Service - Hello Interview Framework

> **Question**: Design a flight ticket aggregator service like Google Flights, Skyscanner, or Kayak that aggregates flight data from multiple airlines and OTAs, allows users to search for flights with low latency, compare prices, and book tickets.
>
> **Asked at**: Google, Amazon, Expedia, Booking.com, Uber
>
> **Difficulty**: Hard | **Level**: Senior

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
1. **Search Flights**: Users search by origin, destination, date(s), passengers, cabin class. Return sorted results with price, duration, stops, airline.
2. **View Flight Details**: Detailed itinerary — legs, layovers, baggage policy, fare rules.
3. **Filter & Sort**: Filter by stops, airline, departure time, price range, duration. Sort by price, duration, departure time, "best" score.
4. **Price Alerts**: Subscribe to alerts for route + date. Notify when price drops below threshold.
5. **Redirect to Book**: On selection, redirect to airline/OTA booking page with deep link (aggregator model).

#### Nice to Have (P1)
- Price calendar (cheapest fares across date range).
- Multi-city / complex itinerary search.
- "Explore" feature (cheapest destinations from origin).
- Price prediction ("prices likely to increase — book now").
- Nearby airport suggestions.

#### Below the Line (Out of Scope)
- Actual booking and payment (we redirect to airline/OTA).
- Seat selection and boarding passes.
- Hotel/car bundling, loyalty programs.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Latency** | < 500ms P50, < 2s P99 | Users expect instant results |
| **Throughput** | 50K search QPS sustained, 200K peak | Millions of daily users |
| **Freshness** | Prices within 15-30 minutes | Stale prices = user frustration |
| **Availability** | 99.99% search, 99.9% alerts | Search = revenue (ads/affiliate) |
| **Scalability** | 500+ airlines, 10K+ routes, 100M+ fares | Global coverage |
| **Data Volume** | ~5B fare updates/day | Airlines change prices frequently |
| **Consistency** | Eventual | Fares inherently volatile |

### Capacity Estimation

```
Data: 500 airlines, 200K daily flights, 90-day lookahead → 3.6B active fares
Traffic: 20M DAU × 4 searches = 80M/day → ~930 QPS sustained, 50K peak
Storage: 3.6B × 500 bytes = 1.8 TB fares, 3 TB ES index, 50 TB historical
Ingestion: 5B updates/day = ~60K updates/second
```

---

## 2️⃣ Core Entities

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│   Airline    │ 1─* │    Flight    │ 1─* │   FlightLeg      │
├──────────────┤     ├──────────────┤     ├──────────────────┤
│ airline_id   │     │ flight_id    │     │ leg_id           │
│ name         │     │ airline_id   │     │ flight_id        │
│ iata_code    │     │ flight_number│     │ departure_airport│
│ logo_url     │     │ origin       │     │ arrival_airport  │
│ booking_url  │     │ destination  │     │ departure_time   │
└──────────────┘     │ departure_dt │     │ arrival_time     │
                     │ arrival_dt   │     │ aircraft_type    │
                     │ stops        │     │ duration_min     │
                     │ duration_min │     └──────────────────┘
                     └──────┬───────┘
                          1─*
                     ┌──────────────┐     ┌──────────────────┐
                     │     Fare     │     │   PriceAlert     │
                     ├──────────────┤     ├──────────────────┤
                     │ fare_id      │     │ alert_id         │
                     │ flight_id    │     │ user_id          │
                     │ source       │     │ origin           │
                     │ cabin_class  │     │ destination      │
                     │ price_cents  │     │ departure_date   │
                     │ currency     │     │ threshold_cents  │
                     │ booking_url  │     │ cabin_class      │
                     │ seats_left   │     │ status           │
                     │ baggage_info │     │ last_notified_at │
                     │ fetched_at   │     └──────────────────┘
                     │ expires_at   │
                     └──────────────┘
```

### Flight Table
```sql
CREATE TABLE flights (
    flight_id       UUID PRIMARY KEY,
    airline_id      UUID NOT NULL REFERENCES airlines(airline_id),
    flight_number   VARCHAR(10) NOT NULL,
    origin_airport  CHAR(3) NOT NULL,
    dest_airport    CHAR(3) NOT NULL,
    departure_time  TIMESTAMPTZ NOT NULL,
    arrival_time    TIMESTAMPTZ NOT NULL,
    duration_minutes INT NOT NULL,
    stops           INT NOT NULL DEFAULT 0,
    aircraft_type   VARCHAR(20),
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_flights_route ON flights (origin_airport, dest_airport, departure_time);
```

### Fare Table
```sql
CREATE TABLE fares (
    fare_id           UUID PRIMARY KEY,
    flight_id         UUID NOT NULL REFERENCES flights(flight_id),
    source_id         UUID NOT NULL REFERENCES sources(source_id),
    cabin_class       VARCHAR(20) NOT NULL,
    price_cents       INT NOT NULL,
    currency          CHAR(3) DEFAULT 'USD',
    booking_deep_link TEXT NOT NULL,
    seats_remaining   INT,
    baggage_included  BOOLEAN DEFAULT false,
    refundable        BOOLEAN DEFAULT false,
    fare_class        CHAR(1),
    fetched_at        TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    is_active         BOOLEAN DEFAULT true
);
CREATE INDEX idx_fares_flight ON fares (flight_id, cabin_class, price_cents);
CREATE INDEX idx_fares_expiry ON fares (expires_at) WHERE is_active = true;
```

### PriceAlert Table
```sql
CREATE TABLE price_alerts (
    alert_id       UUID PRIMARY KEY,
    user_id        UUID NOT NULL,
    origin_airport CHAR(3) NOT NULL,
    dest_airport   CHAR(3) NOT NULL,
    departure_date DATE NOT NULL,
    return_date    DATE,
    cabin_class    VARCHAR(20) DEFAULT 'ECONOMY',
    threshold_cents INT NOT NULL,
    status         VARCHAR(20) DEFAULT 'ACTIVE',
    last_notified_at TIMESTAMPTZ,
    last_checked_price INT,
    expires_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_alerts_route ON price_alerts (origin_airport, dest_airport, departure_date) WHERE status = 'ACTIVE';
```

### Elasticsearch Document (Denormalized)
```json
{
  "flight_id": "uuid-123",
  "airline": { "id": "AA", "name": "American Airlines" },
  "flight_number": "AA1234",
  "origin": { "airport": "JFK", "city": "New York" },
  "destination": { "airport": "LAX", "city": "Los Angeles" },
  "departure_time": "2025-06-15T08:30:00-04:00",
  "arrival_time": "2025-06-15T11:45:00-07:00",
  "duration_minutes": 375,
  "stops": 1,
  "legs": [
    { "from": "JFK", "to": "ORD", "flight_number": "AA1234", "duration_minutes": 165 },
    { "from": "ORD", "to": "LAX", "flight_number": "AA5678", "duration_minutes": 255 }
  ],
  "fares": [
    { "source": "American Airlines", "cabin_class": "ECONOMY", "price_cents": 25900, "booking_url": "..." },
    { "source": "Expedia", "cabin_class": "ECONOMY", "price_cents": 24500, "booking_url": "..." }
  ],
  "min_price_cents": 24500,
  "best_score": 87.5,
  "tags": ["cheapest", "one_stop"]
}
```

---

## 3️⃣ API Design

### 1. Search Flights
```
GET /api/v1/flights/search?origin=JFK&destination=LAX&departure_date=2025-06-15
    &return_date=2025-06-20&passengers=2&cabin_class=ECONOMY
    &sort=price&stops=0,1&airlines=AA,UA,DL&max_price=50000&page=1&page_size=20

Response 200:
{
  "search_id": "srch_abc",
  "results_count": 142,
  "flights": [{
    "flight_id": "flt_001",
    "outbound": {
      "airline": { "code": "UA", "name": "United Airlines" },
      "departure": { "airport": "JFK", "time": "2025-06-15T07:00:00-04:00" },
      "arrival": { "airport": "LAX", "time": "2025-06-15T10:15:00-07:00" },
      "duration_minutes": 375, "stops": 0
    },
    "price_per_person_cents": 24500,
    "total_price_cents": 49000,
    "sources": [
      { "name": "United Airlines", "price_cents": 25900, "booking_url": "..." },
      { "name": "Expedia", "price_cents": 24500, "booking_url": "..." }
    ],
    "tags": ["cheapest", "nonstop", "best_value"],
    "score": 92.5
  }],
  "available_filters": {
    "airlines": [{ "code": "UA", "min_price_cents": 24500, "count": 15 }],
    "stops": [{ "value": 0, "label": "Nonstop", "min_price_cents": 24500, "count": 8 }],
    "price_range": { "min_cents": 17500, "max_cents": 85000 }
  },
  "price_insights": { "trend": "STABLE", "median_price_cents": 32000 },
  "pagination": { "page": 1, "page_size": 20, "total_pages": 8 }
}
```

### 2. Get Flight Details
```
GET /api/v1/flights/{flight_id}?departure_date=2025-06-15&passengers=2
→ Returns legs, fare_options (per source with baggage/flexibility), price_history
```

### 3. Create Price Alert
```
POST /api/v1/alerts  (Auth required)
{ "origin": "JFK", "destination": "LAX", "departure_date": "2025-06-15",
  "cabin_class": "ECONOMY", "threshold_cents": 22000 }
→ 201: { "alert_id": "...", "current_lowest_price_cents": 24500 }
```

### 4. Price Calendar
```
GET /api/v1/flights/calendar?origin=JFK&destination=LAX&month=2025-06
→ Array of { date, min_price_cents, tag } per day
```

### 5. Track Booking Click
```
POST /api/v1/flights/{flight_id}/click
{ "search_id": "...", "source": "Expedia", "price_cents": 24500 }
→ { "redirect_url": "https://expedia.com/book?...", "tracking_id": "trk_abc" }
```

---

## 4️⃣ Data Flow

### Flow 1: Fare Ingestion (Background)
```
Airlines/GDS/OTA → Ingestion Gateway (validate, dedupe, normalize)
  → Kafka "raw-fares" → Flink Workers (enrich, anomaly detect)
  → PostgreSQL (source of truth)
  → CDC (Debezium) → Kafka "fare-changes"
  → ES Indexer (upsert denormalized docs) + Cache Invalidation (Redis)
End-to-end: 30s-2min from source to searchable
```

### Flow 2: User Search
```
User → API Gateway (rate limit) → Search Service
  → Redis cache check (key = hash of query params, TTL 60-120s)
  → Cache MISS: Elasticsearch query (filter + sort + aggregation)
  → Post-process: expire stale fares, compute scores, tag results
  → Cache in Redis → Return to user
Latency: cache hit 10-50ms, miss 100-400ms
```

### Flow 3: Price Alert
```
Kafka "fare-changes" → Alert Worker
  → Redis lookup: "alerts:{origin}:{dest}:{date}" sorted set
  → For each alert where new_price < threshold (with 4hr cooldown)
  → Kafka "alert-notifications" → Notification Service (email + push)
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENTS                                   │
│   [Web App]     [Mobile App]     [API Partners]                 │
└──────────────────────┬──────────────────────────────────────────┘
                       ▼
          ┌────────────────────────┐
          │   CDN + API Gateway    │  Rate limit, auth, routing
          └────────────┬───────────┘
                       │
     ┌─────────────────┼──────────────────┐
     ▼                 ▼                   ▼
┌──────────┐  ┌──────────────┐  ┌──────────────┐
│ Search   │  │Alert Service │  │  Analytics   │
│ Service  │  │• CRUD alerts │  │• Click track │
│• Query ES│  │• Evaluate    │  │• Search logs │
│• Cache   │  │  triggers    │  │• Revenue     │
│• Rank    │  └──────┬───────┘  └──────┬───────┘
└────┬─────┘         │                 │
     ▼               ▼                 ▼
┌──────────────────────────────────────────────┐
│              REDIS CLUSTER                    │
│  Search cache, Alert index, Rate limits      │
└──────────────────┬───────────────────────────┘
     ┌─────────────┼─────────────┐
     ▼                           ▼
┌──────────────┐      ┌───────────────────┐
│ELASTICSEARCH │      │   POSTGRESQL      │
│ 3TB index    │←CDC──│ Source of truth    │
│ Denormalized │      │ Flights, Fares    │
│ Filter/Sort  │      │ Alerts, Sources   │
└──────────────┘      └─────────┬─────────┘
                                │ CDC
                                ▼
┌──────────────────────────────────────────────┐
│                 KAFKA                         │
│ raw-fares → fare-changes → alert-notif       │
│ click-events → search-logs                   │
└──────────────────┬───────────────────────────┘
     ┌─────────────┼─────────────┐
     ▼             ▼             ▼
┌──────────┐ ┌──────────┐ ┌───────────┐
│Ingestion │ │  Flink   │ │Notification│
│Gateway   │ │ Workers  │ │ Service   │
│Airline/  │ │Validate  │ │Email/Push │
│GDS/OTA   │ │Enrich    │ │SMS        │
└──────────┘ └──────────┘ └───────────┘
```

| Component | Tech | Scaling |
|-----------|------|---------|
| Search Service | Java/Spring | 30+ stateless pods |
| Alert Service | Java/Spring | 10+ pods |
| Ingestion Gateway | Go | 20+ pods |
| Fare Processing | Apache Flink | 50+ task slots |
| Elasticsearch | ES 8.x, 15 nodes | 3 TB, sharded by route |
| PostgreSQL | PG 16, sharded | By route_hash |
| Redis | Cluster, 10 nodes | 200 GB |
| Kafka | 100+ partitions | 5 brokers |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Elasticsearch + CDC — The Read Path

**Problem**: 3.6B fares, 50K search QPS, complex filters + sorting + aggregations. PostgreSQL can't handle this read pattern.

**Solution**: Elasticsearch as read-optimized index, synced via CDC (Debezium).

```java
// CDC Consumer: PostgreSQL → Debezium → Kafka → Elasticsearch
@KafkaListener(topics = "fare-changes", groupId = "es-indexer")
public void onFareChange(FareChangeEvent event) {
    String flightId = event.getFlightId();
    
    // Scripted upsert: replace fare from this source, recalculate min_price
    UpdateRequest req = new UpdateRequest("flights", flightId)
        .script(new Script(ScriptType.INLINE, "painless",
            "ctx._source.fares.removeIf(f -> f.source == params.source);" +
            "ctx._source.fares.add(params.fare);" +
            "ctx._source.min_price_cents = ctx._source.fares.stream()" +
            "  .mapToInt(f -> f.price_cents).min().orElse(Integer.MAX_VALUE);" +
            "ctx._source.updated_at = params.now;",
            Map.of("source", event.getSourceName(), "fare", fareDoc, "now", Instant.now())))
        .upsert(buildFullFlightDoc(event)); // If doc doesn't exist, create it
    
    esClient.update(req);
}
```

**Why CDC over dual-writes?**
- DB is single source of truth → no inconsistency
- No distributed transaction between PG + ES
- Debezium captures all changes including batch operations
- Can replay Kafka topic to re-index ES from scratch
- Trade-off: 30s-2min lag (acceptable — fares are inherently approximate)

**ES Index Design**:
```json
{
  "settings": {
    "number_of_shards": 30,
    "number_of_replicas": 1,
    "routing.allocation.total_shards_per_node": 3
  },
  "mappings": {
    "properties": {
      "origin.airport": { "type": "keyword" },
      "destination.airport": { "type": "keyword" },
      "departure_time": { "type": "date" },
      "duration_minutes": { "type": "integer" },
      "stops": { "type": "integer" },
      "min_price_cents": { "type": "integer" },
      "airline.id": { "type": "keyword" },
      "fares": { "type": "nested" },
      "best_score": { "type": "float" }
    }
  }
}
```

**Shard routing**: Route queries by `origin_airport + dest_airport` → most searches hit 1-2 shards instead of all 30.

---

### Deep Dive 2: Fare Ingestion Pipeline — 60K Updates/Second

**Problem**: 500 airlines + 50 GDS/OTA partners pushing ~5B fare updates daily. Each has different format, different API, different reliability.

```java
public class FareIngestionGateway {

    // Source Adapters: normalize heterogeneous feeds into common format
    interface SourceAdapter {
        Stream<RawFare> fetchFares(SourceConfig config);
    }
    
    // Adapter per source type
    class AmadeusAdapter implements SourceAdapter { /* GDS XML → RawFare */ }
    class SabreAdapter implements SourceAdapter { /* GDS SOAP → RawFare */ }
    class NDCAdapter implements SourceAdapter { /* Airline NDC JSON → RawFare */ }
    class ScraperAdapter implements SourceAdapter { /* Headless browser → RawFare */ }
    
    // Ingestion pipeline
    public void ingest(RawFare raw) {
        // 1. Validate
        if (!validator.isValid(raw)) { metrics.invalid++; return; }
        
        // 2. Deduplicate (Bloom filter + Redis)
        String dedupeKey = hash(raw.flightNumber, raw.date, raw.source, raw.cabin);
        if (bloomFilter.mightContain(dedupeKey)) {
            String existing = redis.get("fare:" + dedupeKey);
            if (existing != null && existing.equals(raw.priceHash())) return; // No change
        }
        bloomFilter.put(dedupeKey);
        redis.setex("fare:" + dedupeKey, 1800, raw.priceHash()); // 30 min TTL
        
        // 3. Normalize
        NormalizedFare fare = normalizer.normalize(raw);
        // Convert currency to USD, standardize cabin (Y→ECONOMY), timezone to UTC
        
        // 4. Anomaly detection
        if (anomalyDetector.isAnomaly(fare)) {
            kafka.send("fare-anomalies", fare); // Route to human review queue
            return;
        }
        
        // 5. Publish to processing pipeline
        kafka.send("raw-fares", fare.routeKey(), fare);
    }
}
```

**Anomaly Detection**:
```
Rule-based (fast):
  - Price < $10 for international flight → likely error
  - Price > 10x historical median → likely error
  - Duration < route distance / 600mph → physically impossible

ML-based (periodic):
  - Train on historical price distributions per route
  - Flag fares > 3σ from mean for that route + season + advance purchase
```

**Ingestion Prioritization**: Not all routes are equal. Popular routes (JFK→LAX) need fresher data than rare ones (ABQ→BZN).

```java
// Priority-based refresh scheduling
public Duration getRefreshInterval(Route route) {
    int searchesPerDay = routePopularity.get(route);
    if (searchesPerDay > 10000) return Duration.ofMinutes(5);   // Hot route
    if (searchesPerDay > 1000)  return Duration.ofMinutes(15);  // Warm route
    if (searchesPerDay > 100)   return Duration.ofMinutes(60);  // Cool route
    return Duration.ofHours(6);                                  // Cold route
}
```

---

### Deep Dive 3: Search Ranking — The "Best" Score

**Problem**: Users don't just want cheapest. "Best" flight balances price, duration, stops, departure time, and data freshness. How do we compute a composite ranking score?

```java
public class FlightRankingService {

    /**
     * Composite scoring: weighted combination of normalized factors.
     * Weights learned from click-through data (A/B tested).
     */
    
    static final double W_PRICE = 0.35;
    static final double W_DURATION = 0.25;
    static final double W_STOPS = 0.20;
    static final double W_DEPARTURE = 0.10;
    static final double W_FRESHNESS = 0.10;
    
    public double computeScore(FlightResult flight, SearchContext ctx) {
        // Normalize each factor to 0-100 (higher = better)
        
        // Price: cheapest = 100, most expensive = 0
        double priceScore = 100.0 * (1.0 - 
            (double)(flight.getMinPrice() - ctx.getMinPrice()) / 
            (ctx.getMaxPrice() - ctx.getMinPrice() + 1));
        
        // Duration: shortest = 100
        double durationScore = 100.0 * (1.0 - 
            (double)(flight.getDuration() - ctx.getMinDuration()) / 
            (ctx.getMaxDuration() - ctx.getMinDuration() + 1));
        
        // Stops: nonstop = 100, 1 stop = 60, 2+ = 20
        double stopsScore = switch (flight.getStops()) {
            case 0 -> 100.0;
            case 1 -> 60.0;
            default -> 20.0;
        };
        
        // Departure time preference (user's preferred window scores higher)
        double departureScore = computeDepartureScore(
            flight.getDepartureTime(), ctx.getPreferredDepartureWindow());
        
        // Freshness: how recently was this fare verified?
        long minutesSinceFetch = Duration.between(
            flight.getFetchedAt(), Instant.now()).toMinutes();
        double freshnessScore = Math.max(0, 100.0 - minutesSinceFetch * 2);
        
        return W_PRICE * priceScore + W_DURATION * durationScore +
               W_STOPS * stopsScore + W_DEPARTURE * departureScore +
               W_FRESHNESS * freshnessScore;
    }
    
    // Tags based on score breakdown
    public List<String> computeTags(FlightResult flight, List<FlightResult> allResults) {
        List<String> tags = new ArrayList<>();
        if (flight == cheapest(allResults)) tags.add("cheapest");
        if (flight == fastest(allResults)) tags.add("fastest");
        if (flight.getStops() == 0) tags.add("nonstop");
        if (flight.getScore() == highest(allResults)) tags.add("best_value");
        return tags;
    }
}
```

**Learning weights from user behavior**: Run A/B tests with different weight configurations. Measure click-through rate on top-3 results. Gradient descent to optimize weights that maximize CTR.

---

### Deep Dive 4: Multi-Layer Caching Strategy

**Problem**: 50K search QPS. Even Elasticsearch can't absorb this without caching. But fares change every 15-30 minutes — aggressive caching serves stale data.

```java
public class SearchCacheService {

    /**
     * 3-layer cache:
     *   L1: CDN edge cache (10s TTL) — absorbs 60% of reads
     *   L2: Redis (60-120s TTL) — absorbs 30% of reads
     *   L3: Elasticsearch — handles 10% (cache misses)
     * 
     * Cache key = hash(origin, dest, date, cabin, sort, filters)
     * Invalidation: on fare change → delete Redis keys for that route
     */
    
    public SearchResult search(SearchRequest req) {
        String cacheKey = computeCacheKey(req);
        
        // L2: Redis
        String cached = redis.get(cacheKey);
        if (cached != null) {
            metrics.cacheHit("redis");
            return deserialize(cached);
        }
        
        // L3: Elasticsearch
        metrics.cacheMiss();
        SearchResult result = elasticsearchQuery(req);
        
        // Determine TTL based on route popularity
        int ttlSeconds = getAdaptiveTTL(req.getOrigin(), req.getDest());
        redis.setex(cacheKey, ttlSeconds, serialize(result));
        
        return result;
    }
    
    /**
     * Adaptive TTL: popular routes get shorter TTL (fresher data)
     * because stale prices on popular routes = more frustrated users.
     */
    int getAdaptiveTTL(String origin, String dest) {
        int searchesPerHour = routePopularity.getSearchRate(origin, dest);
        if (searchesPerHour > 1000) return 30;   // Hot: 30s TTL
        if (searchesPerHour > 100)  return 60;   // Warm: 60s
        return 120;                                // Cold: 120s
    }
    
    /**
     * Proactive cache invalidation: when a fare changes,
     * delete all cached search results for that route.
     */
    @KafkaListener(topics = "fare-changes")
    public void onFareChange(FareChangeEvent event) {
        String routePattern = "search:" + event.getOrigin() + ":" + event.getDest() + ":*";
        // Use Redis SCAN + DELETE (not KEYS — production safe)
        redis.scanAndDelete(routePattern);
    }
}
```

**Cache hit rate in practice**: ~85-90% overall
```
L1 (CDN): 60% hit rate (identical searches within 10s window)
L2 (Redis): 25% hit rate (same route+date within 60-120s)
L3 (ES): 15% of total traffic reaches ES
Effective ES QPS: 50K × 0.15 = 7,500 QPS (manageable for 15-node cluster)
```

---

### Deep Dive 5: Price Alert System — Streaming Evaluation

**Problem**: Millions of active alerts. Fare changes stream in at 60K/s. For each fare change, we need to check matching alerts and trigger notifications — without scanning a huge alerts table.

```java
public class PriceAlertEvaluator {

    /**
     * Alert index in Redis: sorted sets keyed by route+date.
     * Members = alert_ids, score = threshold price.
     * 
     * On fare change → ZRANGEBYSCORE to find all alerts with threshold > new_price
     * O(log N + M) where M = matching alerts. Very fast even for millions.
     */
    
    @KafkaListener(topics = "fare-changes", groupId = "alert-evaluator")
    public void onFareChange(FareChangeEvent event) {
        String origin = event.getOrigin();
        String dest = event.getDest();
        int newPrice = event.getMinPriceCents();
        
        // Check all departure dates in the next 90 days for this route
        for (LocalDate date : event.getAffectedDates()) {
            String alertKey = "alerts:" + origin + ":" + dest + ":" + date;
            
            // Find alerts where threshold >= newPrice (i.e., price dropped enough)
            Set<String> matchingAlerts = redis.zrangeByScore(
                alertKey, newPrice, Double.POSITIVE_INFINITY);
            
            for (String alertId : matchingAlerts) {
                // Check cooldown: don't spam user
                String cooldownKey = "alert_cooldown:" + alertId;
                if (redis.exists(cooldownKey)) continue;
                
                // Set cooldown (4 hours)
                redis.setex(cooldownKey, 14400, "1");
                
                // Publish notification
                kafka.send("alert-notifications", AlertNotification.builder()
                    .alertId(alertId)
                    .route(origin + "→" + dest)
                    .date(date)
                    .newPrice(newPrice)
                    .build());
            }
        }
    }
    
    // When user creates alert, add to Redis sorted set
    public void registerAlert(PriceAlert alert) {
        String key = "alerts:" + alert.getOrigin() + ":" + alert.getDest() 
                     + ":" + alert.getDepartureDate();
        redis.zadd(key, alert.getThresholdCents(), alert.getAlertId().toString());
    }
}
```

**Why Redis sorted sets?**
```
Without index: for each fare change, scan all alerts in DB → O(N) per change
  60K changes/s × scan = impossible

With Redis sorted set:
  Key: "alerts:JFK:LAX:2025-06-15"
  Members: alert IDs, Score: threshold price in cents
  
  ZRANGEBYSCORE "alerts:JFK:LAX:2025-06-15" 24500 +inf
  → Returns only alerts whose threshold ≥ current price (i.e., triggered)
  → O(log N + M) where N = alerts for this route+date, M = matches
  → Typically M < 10, entire operation < 1ms
```

---

### Deep Dive 6: Data Freshness vs. Performance Trade-off

**Problem**: Airlines change prices constantly. If we cache aggressively, users see stale prices. If we don't cache, we can't handle 50K QPS. The fundamental tension: freshness vs. performance.

**Solution: Tiered freshness model** — different freshness guarantees for different contexts.

```
Tier 1: Search Results (browsing)
  Freshness: 1-2 minutes (Redis cache TTL 60-120s)
  Rationale: user is browsing, not yet committed. Approximate prices OK.
  Impact of staleness: mild annoyance (price slightly different on click-through)

Tier 2: Flight Detail Page (pre-booking)
  Freshness: 15-30 seconds (shorter cache TTL or direct ES query)
  Rationale: user is about to click "Book". Price should be accurate.
  Impact of staleness: user clicks through, sees different price → loses trust

Tier 3: Booking Redirect (critical)
  Freshness: real-time verification
  Rationale: at click time, optionally verify price with source
  Approach: async check — redirect immediately but show "verifying price..."
```

**Live Price Verification on Click**:
```java
public class BookingRedirectService {
    
    public RedirectResult redirect(String flightId, String source, int expectedPrice) {
        // Immediate redirect (don't block user)
        String redirectUrl = buildRedirectUrl(flightId, source);
        
        // Async: verify price hasn't changed significantly
        CompletableFuture.runAsync(() -> {
            int currentPrice = sourceAdapter.verifyPrice(flightId, source);
            if (Math.abs(currentPrice - expectedPrice) > expectedPrice * 0.05) {
                // Price changed > 5% — log for analytics, possibly notify user
                analytics.logPriceDiscrepancy(flightId, source, expectedPrice, currentPrice);
            }
        });
        
        return new RedirectResult(redirectUrl, "trk_" + UUID.randomUUID());
    }
}
```

---

### Deep Dive 7: Price Prediction — "Should I Book Now?"

**Problem**: Users want to know if prices will go up or down. This is a competitive differentiator (Google Flights does this).

```java
public class PricePredictionService {

    /**
     * ML model: predicts price direction for a route+date.
     * 
     * Features:
     *   - Days until departure (advance purchase)
     *   - Day of week (departure and booking)
     *   - Season/holiday proximity
     *   - Historical price trend (last 7/14/30 days)
     *   - Current price vs. historical median for this route+advance_days
     *   - Seats remaining (if available from source)
     *   - Competitor pricing signals
     * 
     * Output: { direction: UP/DOWN/STABLE, confidence: 0-1, explanation: "..." }
     * 
     * Model: Gradient Boosted Trees (XGBoost) trained on 1 year of historical data
     * Retraining: weekly on last 90 days of data
     * Serving: precomputed daily for top 10K routes, on-demand for long tail
     */
    
    public PricePrediction predict(String origin, String dest, LocalDate date) {
        // Check precomputed cache first
        String cacheKey = "prediction:" + origin + ":" + dest + ":" + date;
        PricePrediction cached = redis.get(cacheKey, PricePrediction.class);
        if (cached != null) return cached;
        
        // Compute features
        PredictionFeatures features = PredictionFeatures.builder()
            .daysUntilDeparture(ChronoUnit.DAYS.between(LocalDate.now(), date))
            .dayOfWeek(date.getDayOfWeek().getValue())
            .isHoliday(holidayService.isNearHoliday(date))
            .currentPrice(getCurrentMinPrice(origin, dest, date))
            .historicalMedian(getHistoricalMedian(origin, dest, date))
            .priceSlope7d(getPriceSlope(origin, dest, date, 7))
            .priceSlope30d(getPriceSlope(origin, dest, date, 30))
            .build();
        
        // Inference
        double[] prediction = model.predict(features.toArray());
        // prediction[0] = P(price_up), prediction[1] = P(stable), prediction[2] = P(price_down)
        
        PricePrediction result;
        if (prediction[0] > 0.6) {
            result = new PricePrediction("UP", prediction[0],
                "Prices are likely to increase. We recommend booking soon.");
        } else if (prediction[2] > 0.6) {
            result = new PricePrediction("DOWN", prediction[2],
                "Prices may drop. Consider waiting a few days.");
        } else {
            result = new PricePrediction("STABLE", prediction[1],
                "Prices are typical for this route. No significant change expected.");
        }
        
        redis.setex(cacheKey, 86400, result); // Cache for 24 hours
        return result;
    }
}
```

---

### Deep Dive 8: Handling Source Failures Gracefully

**Problem**: Airline APIs go down. GDS feeds have outages. OTA scraping gets blocked. If a source fails, we shouldn't show stale data or empty results.

```java
public class SourceHealthMonitor {

    /**
     * Circuit breaker per source with graceful degradation.
     * 
     * States: CLOSED (healthy) → OPEN (failing) → HALF_OPEN (testing)
     * 
     * When source is OPEN:
     *   - Stop fetching from it (save resources)
     *   - Mark existing fares from this source as "unverified"
     *   - Still show them in search results with staleness indicator
     *   - Expire them after 2x normal TTL (e.g., 1 hour instead of 30 min)
     */
    
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    
    public void recordSuccess(String sourceId) {
        breakers.computeIfAbsent(sourceId, this::createBreaker).recordSuccess();
    }
    
    public void recordFailure(String sourceId) {
        CircuitBreaker cb = breakers.computeIfAbsent(sourceId, this::createBreaker);
        cb.recordFailure();
        
        if (cb.getState() == State.OPEN) {
            log.warn("Source {} circuit OPEN — marking fares as unverified", sourceId);
            markFaresUnverified(sourceId);
        }
    }
    
    private void markFaresUnverified(String sourceId) {
        // Extend expiry of existing fares (don't delete — stale data > no data)
        jdbc.update("""
            UPDATE fares SET expires_at = expires_at + INTERVAL '30 minutes'
            WHERE source_id = ? AND is_active = true
            """, sourceId);
    }
}
```

**Staleness indicators in search results**:
```json
{
  "source": "Expedia",
  "price_cents": 24500,
  "freshness": "VERIFIED",      // fetched < 30 min ago
  "fetched_at": "2025-06-10T12:00:00Z"
}
{
  "source": "Kayak",
  "price_cents": 25100,
  "freshness": "UNVERIFIED",    // source is down, data is 45 min old
  "fetched_at": "2025-06-10T11:15:00Z",
  "warning": "Price may have changed. Last verified 45 minutes ago."
}
```

---

### Deep Dive 9: Revenue Model — Click Tracking & Attribution

**Problem**: We're an aggregator. Revenue comes from CPC (cost-per-click) when users click through to airlines/OTAs, plus display ads. We need accurate click tracking and attribution.

```java
public class ClickTrackingService {

    /**
     * Revenue attribution flow:
     *   1. User searches → log search_id
     *   2. User clicks "Book on Expedia" → log click with search_id
     *   3. User lands on Expedia → Expedia confirms via webhook (postback)
     *   4. If user books → Expedia reports conversion → we earn commission
     * 
     * Click = revenue event. Must be:
     *   - Accurate (no missed clicks)
     *   - Fraud-resistant (no fake clicks)
     *   - Fast (don't slow down redirect)
     */
    
    public RedirectResult trackAndRedirect(ClickRequest request) {
        // 1. Generate tracking ID
        String trackingId = "trk_" + UUID.randomUUID();
        
        // 2. Log click event (async — don't block redirect)
        ClickEvent event = ClickEvent.builder()
            .trackingId(trackingId)
            .searchId(request.getSearchId())
            .userId(request.getUserId())
            .flightId(request.getFlightId())
            .source(request.getSource())
            .displayedPrice(request.getPriceCents())
            .position(request.getResultPosition())
            .timestamp(Instant.now())
            .userAgent(request.getUserAgent())
            .ipAddress(request.getIpAddress())
            .build();
        
        kafka.send("click-events", event);
        
        // 3. Build redirect URL with affiliate tracking params
        String redirectUrl = buildAffiliateUrl(
            request.getBookingUrl(),
            trackingId,
            request.getSource());
        
        return new RedirectResult(redirectUrl, trackingId);
    }
}
```

**Revenue metrics**:
```
Key metrics tracked:
  - CTR (click-through rate) per search = clicks / searches
  - CPC (cost per click) per source = revenue / clicks
  - Conversion rate = bookings / clicks
  - Revenue per search = total revenue / total searches
  - Price accuracy = % clicks where displayed_price ≈ actual_price (±5%)

Typical values:
  - CTR: 15-25% (users click on 1 in 4-7 searches)
  - CPC: $0.50 - $3.00 depending on route value
  - Conversion: 5-15% of clicks result in booking
  - Revenue per search: $0.10 - $0.50
```

---

### Deep Dive 10: Observability & Metrics

```java
public class AggregatorMetrics {
    // Search path
    Counter searchRequests    = counter("search.requests");
    Timer   searchLatency     = timer("search.latency_ms");
    Counter cacheHits         = counter("search.cache.hits");
    Counter cacheMisses       = counter("search.cache.misses");
    Counter esQueries         = counter("search.es.queries");
    Timer   esLatency         = timer("search.es.latency_ms");
    
    // Ingestion path
    Counter faresIngested     = counter("ingestion.fares.total");
    Counter faresDeduplicated = counter("ingestion.fares.deduped");
    Counter faresAnomaly      = counter("ingestion.fares.anomaly");
    Gauge   ingestionLag      = gauge("ingestion.lag_seconds");
    
    // Freshness
    Gauge   avgFareAge        = gauge("freshness.avg_fare_age_minutes");
    Gauge   staleFarePercent  = gauge("freshness.stale_fare_percent");
    
    // Alerts
    Counter alertsCreated     = counter("alerts.created");
    Counter alertsTriggered   = counter("alerts.triggered");
    Timer   alertEvalLatency  = timer("alerts.eval.latency_ms");
    
    // Revenue
    Counter clicks            = counter("revenue.clicks");
    Counter redirects         = counter("revenue.redirects");
    Gauge   revenuePerSearch  = gauge("revenue.per_search_cents");
    Counter priceDiscrepancy  = counter("revenue.price_discrepancy");
}
```

**Key alarms**:
```
CRITICAL: search P99 latency > 3s for 5 minutes
CRITICAL: ingestion lag > 10 minutes (fares getting stale)
HIGH:     cache hit rate drops below 70% (ES overload risk)
HIGH:     source circuit breaker OPEN for > 30 minutes
MEDIUM:   stale fare percentage > 20%
MEDIUM:   price discrepancy rate > 10% (users seeing wrong prices)
```

---

## 🏗️ Infrastructure & Cost Estimation

```
┌─────────────────────────┬──────────────────┬──────────────────┐
│ Component               │ Configuration    │ Monthly Cost     │
├─────────────────────────┼──────────────────┼──────────────────┤
│ CDN (CloudFront)        │ Global, 50K RPS  │ $8,000           │
│ API Gateway + LB        │ Multi-AZ         │ $3,000           │
│ Search Service          │ 30 pods (K8s)    │ $12,000          │
│ Alert Service           │ 10 pods          │ $4,000           │
│ Analytics Service       │ 10 pods          │ $4,000           │
│ Ingestion Gateway       │ 20 pods (Go)     │ $6,000           │
│ Flink Cluster           │ 50+ task slots   │ $15,000          │
│ Elasticsearch           │ 15 nodes, 3 TB   │ $20,000          │
│ PostgreSQL (sharded)    │ 8 shards × 3     │ $18,000          │
│ Redis Cluster           │ 10 nodes, 200 GB │ $10,000          │
│ Kafka                   │ 5 brokers        │ $8,000           │
│ Notification Service    │ 15 pods          │ $4,000           │
│ Monitoring              │ Datadog/CW       │ $5,000           │
├─────────────────────────┼──────────────────┼──────────────────┤
│ TOTAL                   │                  │ ~$117,000/month  │
└─────────────────────────┴──────────────────┴──────────────────┘

Revenue: 80M searches/day × $0.25 avg revenue/search = $20M/day = $600M/year
Infra cost: ~$1.4M/year = 0.23% of revenue
```

---

## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Search engine** | Elasticsearch + CDC from PostgreSQL | Decouples write-heavy ingestion from read-heavy search |
| **Data sync** | CDC (Debezium) over dual-writes | Single source of truth; no distributed txn; replayable |
| **Caching** | 3-layer (CDN → Redis → ES) with adaptive TTL | 85-90% hit rate; popular routes get fresher data |
| **Ingestion** | Kafka + Flink streaming | Handles 60K updates/s; backpressure; exactly-once |
| **Alert evaluation** | Redis sorted sets (streaming) | O(log N + M) per fare change; no table scans |
| **Ranking** | Weighted composite score (A/B tested) | Balances price, duration, stops; CTR-optimized |
| **Consistency** | Eventual (30s-2min lag) | Fares inherently volatile; perfect consistency impossible |
| **Source failures** | Circuit breaker + graceful degradation | Stale data > no data; extend TTL when source is down |
| **Price prediction** | XGBoost on historical data | Competitive differentiator; precomputed for top routes |

---

## 🎯 Interview Talking Points

1. **"ES + CDC is the strongest call"** — Decouples 60K/s write ingestion from 50K QPS search reads. CDC ensures single source of truth; ES is a derived, denormalized index.
2. **"3-layer cache with adaptive TTL"** — CDN (10s) → Redis (30-120s, route-dependent) → ES. Hot routes get shorter TTLs for fresher prices. 85-90% cache hit rate.
3. **"Tiered freshness model"** — Browse = 2 min stale OK, detail page = 30s, booking redirect = real-time verification. Matches user intent with cost.
4. **"Alert evaluation via Redis sorted sets"** — Score = threshold price. ZRANGEBYSCORE finds matching alerts in O(log N + M). No table scans for 60K fare changes/second.
5. **"Ingestion priority by route popularity"** — JFK→LAX refreshed every 5 min, ABQ→BZN every 6 hours. Search logs drive priority.
6. **"Ranking score is A/B tested"** — Weighted combo of price (35%), duration (25%), stops (20%), departure (10%), freshness (10%). Weights optimized via CTR.
7. **"Circuit breaker per source"** — When airline API goes down, extend fare TTL. Stale data > no data. Show "unverified" badge.
8. **"Revenue = CPC on redirects"** — Click tracking is critical path. Async logging, affiliate deep links, postback conversion tracking.
9. **"Bloom filter + Redis for deduplication"** — At 60K/s, most updates are no-ops (price unchanged). Bloom filter + price hash eliminates 70%+ redundant writes.
10. **"Anomaly detection gates ingestion"** — Rule-based (fast) + ML (periodic). Prevents erroneous $10 NYC→London fares from reaching users.

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 10 topics (choose 2-3 based on interviewer interest)
