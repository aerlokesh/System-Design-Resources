# Design a Real-Time Traffic Monitoring System - Hello Interview Framework

> **Question**: Design a system that allows users to view real-time traffic conditions, congestion levels, and road status across a city by collecting and processing data from multiple sources like GPS devices, traffic cameras, and sensors. Users pan/zoom a map, watch color-coded roads update every few seconds, and get route ETAs that adapt to current conditions.
>
> **Asked at**: Uber, Oracle, Google
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## 1️⃣ Requirements

### Functional (P0)
1. **Real-Time Traffic Map**: Display color-coded road segments (green/yellow/red/dark-red) based on live congestion. Updates every 5-10 seconds.
2. **Data Ingestion**: Ingest GPS probe data from millions of phones/vehicles, fixed sensors (loop detectors), and traffic cameras. Handle out-of-order, duplicate, and noisy data.
3. **Road Segment Speed**: Compute average speed per road segment from GPS probes. Segment = stretch of road between two intersections (~100-500m).
4. **Congestion Classification**: Map speed to congestion level per segment: Free Flow (>80% of speed limit), Moderate (50-80%), Heavy (25-50%), Standstill (<25%).
5. **Incident Detection**: Detect accidents, road closures, hazards from sudden speed drops, camera feeds, and user reports. Show on map.
6. **Route ETA**: Given origin and destination, compute ETA using current traffic conditions on each road segment along the route.

### Functional (P1)
- Historical traffic patterns (predict congestion by time-of-day, day-of-week)
- User-reported incidents (Waze-style: accident, police, hazard)
- Alternative route suggestions based on live traffic
- Traffic alerts/notifications for saved routes
- Multi-modal: include transit, walking, cycling data

### Non-Functional Requirements
| Attribute | Target |
|-----------|--------|
| **Data Freshness** | < 30 seconds from GPS probe to map update |
| **Ingestion Throughput** | 1M GPS data points/sec (city-scale: 10M vehicles reporting every 10s) |
| **Map Query Latency** | < 200ms for viewport tile (road segment data for visible area) |
| **ETA Accuracy** | Within 10% of actual travel time |
| **Availability** | 99.9% for map reads |
| **Scale** | 10M road segments globally, 50M concurrent map viewers |
| **Geospatial** | Efficient spatial queries: "all segments in this bounding box" |

### Capacity Estimation
```
Vehicles: 10M active (city-wide fleet + consumer phones)
GPS updates: every 10 seconds → 1M data points/sec
  Each data point: { device_id, lat, lng, speed, heading, timestamp } = ~100 bytes
  Ingestion bandwidth: 1M × 100 bytes = 100 MB/sec

Road segments: 10M globally (1M per major city)
  Segment update frequency: every 5 seconds (from aggregated probes)
  Segment data: { segment_id, avg_speed, congestion_level, sample_count } = 50 bytes
  Segment updates: 1M segments × 50 bytes / 5 sec = 10 MB/sec

Map tile queries: 50M concurrent viewers × 1 tile refresh/5 sec = 10M queries/sec
  Each tile response: ~20 KB (500 segments × 40 bytes each)

Storage:
  Live segment state: 10M × 50 bytes = 500 MB (all in memory)
  Historical: 10M segments × 288 samples/day (5-min buckets) × 365 days × 20 bytes = ~21 TB/year
  GPS raw: 1M/sec × 100 bytes × 86400 = 8.6 TB/day (short retention: 24 hours)
```

---

## 2️⃣ Core Entities

```java
public class GpsProbe {
    String deviceId;
    double latitude;
    double longitude;
    double speedMps;            // Meters per second
    double heading;             // Degrees (0-360)
    Instant timestamp;
    String source;              // PHONE, FLEET, SENSOR
}

public class RoadSegment {
    String segmentId;           // Unique ID (e.g., "seg_123456")
    String roadName;            // "I-405 Northbound"
    double startLat, startLng;
    double endLat, endLng;
    int speedLimitMps;          // Posted speed limit
    int laneCount;
    RoadType type;              // HIGHWAY, ARTERIAL, LOCAL, RAMP
    String geoHash;             // For spatial indexing
}

public class SegmentTrafficState {
    String segmentId;
    double avgSpeedMps;         // Current average speed
    int sampleCount;            // Number of probes in current window
    CongestionLevel congestion; // FREE_FLOW, MODERATE, HEAVY, STANDSTILL
    Instant updatedAt;
    double travelTimeSeconds;   // Time to traverse this segment at current speed
}
public enum CongestionLevel { FREE_FLOW, MODERATE, HEAVY, STANDSTILL }

public class Incident {
    String incidentId;
    double latitude, longitude;
    IncidentType type;          // ACCIDENT, ROAD_CLOSURE, CONSTRUCTION, HAZARD
    IncidentSeverity severity;  // MINOR, MODERATE, MAJOR
    String description;
    Instant detectedAt;
    Instant resolvedAt;
    String source;              // AUTOMATED, USER_REPORT, CAMERA
}

public class MapTile {
    String tileId;              // "{zoom}:{x}:{y}" (e.g., "14:4832:6218")
    int zoomLevel;
    List<SegmentTrafficState> segments; // Road segments in this tile
    List<Incident> incidents;
    Instant generatedAt;
}

public class RouteEta {
    String routeId;
    List<String> segmentIds;    // Ordered list of segments in route
    double totalDistanceMeters;
    double totalTimeSeconds;    // Sum of segment travel times (live)
    double historicalTimeSeconds;// Expected time from historical patterns
    Instant computedAt;
}
```

---

## 3️⃣ API Design

### 1. Get Map Tile (Traffic Overlay — Hot Path)
```
GET /api/v1/traffic/tiles/{zoom}/{x}/{y}
Response (200, cached 5 seconds):
{
  "tile_id": "14:4832:6218", "generated_at": "2025-01-10T10:00:05Z",
  "segments": [
    { "segment_id": "seg_001", "congestion": "HEAVY", "speed_mph": 15, "travel_time_sec": 45 },
    { "segment_id": "seg_002", "congestion": "FREE_FLOW", "speed_mph": 55, "travel_time_sec": 12 },
    ...
  ],
  "incidents": [
    { "id": "inc_001", "type": "ACCIDENT", "lat": 34.05, "lng": -118.24, "severity": "MAJOR" }
  ]
}
```

### 2. Ingest GPS Probes (Data Push)
```
POST /api/v1/probes/batch
Request: {
  "probes": [
    { "device_id": "dev_001", "lat": 34.0522, "lng": -118.2437, "speed": 15.2, "heading": 270, "ts": 1736503200 },
    { "device_id": "dev_002", "lat": 34.0530, "lng": -118.2450, "speed": 0.5, "heading": 180, "ts": 1736503201 },
    ...
  ]
}
Response (202): { "accepted": 100, "rejected": 2 }
```

### 3. Get Route ETA
```
POST /api/v1/routes/eta
Request: { "origin": { "lat": 34.05, "lng": -118.24 }, "destination": { "lat": 34.10, "lng": -118.30 } }
Response: {
  "route": { "distance_miles": 8.2, "eta_minutes": 22, "historical_avg_minutes": 15,
             "congestion_summary": "Heavy traffic on I-405 N" },
  "segments": [{ "id": "seg_001", "name": "I-405 N", "congestion": "HEAVY", "travel_sec": 120 }, ...]
}
```

### 4. Report Incident
```
POST /api/v1/incidents
Request: { "lat": 34.05, "lng": -118.24, "type": "ACCIDENT", "description": "2-car collision blocking right lane" }
```

### 5. Get Incidents in Area
```
GET /api/v1/incidents?bbox=34.0,-118.3,34.1,-118.2
Response: { "incidents": [{ "id": "inc_001", "type": "ACCIDENT", ... }] }
```

---

## 4️⃣ Data Flow

### GPS Probe → Traffic State Update (Streaming Pipeline)
```
1. Millions of devices send GPS probes (batch every 10 seconds)
   ↓
2. Ingestion Gateway: validate, deduplicate (device_id + timestamp), rate limit
   → Produce to Kafka "raw-probes" (partitioned by geohash region)
   ↓
3. Map-Matching Service (Kafka consumer):
   a. GPS point (lat, lng) → which road segment? (snap to nearest road)
   b. Algorithm: Hidden Markov Model (HMM) map matching
      - Considers: distance to road, heading alignment, route continuity
   c. Output: { segment_id, speed, timestamp }
   → Produce to Kafka "matched-probes"
   ↓
4. Segment Aggregator (Kafka Streams / Flink):
   a. Tumbling window: 5 seconds per segment
   b. For each segment in window: compute average speed from all probes
   c. Classify congestion: speed vs speed_limit ratio
   d. Detect incidents: sudden speed drop (>50% drop in 1 minute)
   e. Update SegmentTrafficState in Redis
   → Produce to Kafka "segment-updates"
   ↓
5. Tile Generator (Kafka consumer):
   a. Consume segment updates
   b. Determine which map tiles contain this segment
   c. Regenerate tile data (pre-compute for common zoom levels)
   d. Write to Tile Cache (Redis / CDN)
   ↓
6. Client polls tile every 5 seconds → CDN/Redis cache hit → fresh traffic data
   End-to-end: GPS probe → map update: < 30 seconds
```

### Route ETA Computation
```
1. Client sends origin + destination
2. Routing Service:
   a. Compute shortest path (Dijkstra/A* on road graph)
   b. For each segment in path: look up current travel_time from Redis
   c. Sum travel times = ETA
   d. Compare with historical pattern for this route + time-of-day
3. Return ETA + segment-by-segment breakdown
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA SOURCES                                   │
│  GPS Phones ─┐  Fleet Vehicles ─┐  Loop Detectors ─┐  Cameras  │
└──────────────┼──────────────────┼──────────────────┼────────────┘
               └──────────────────┼──────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│              INGESTION GATEWAY (50-100 pods)                     │
│  • Validate GPS probes  • Deduplicate  • Produce to Kafka       │
└─────────────────────────────┬────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  KAFKA: "raw-probes" (1M events/sec, 500 partitions, geohash)  │
└────────┬────────────────────────────────────────────────────────┘
         ▼
┌──────────────────┐
│ MAP-MATCHING      │ — Snap GPS to road segment (HMM algorithm)
│ SERVICE           │ — Output: { segment_id, speed }
│ Pods: 50-100      │
└────────┬──────────┘
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  KAFKA: "matched-probes"                                         │
└────────┬────────────────────────────────────────────────────────┘
         ▼
┌──────────────────┐     ┌──────────────────┐
│ SEGMENT           │     │ INCIDENT          │
│ AGGREGATOR        │     │ DETECTOR          │
│ (Flink/KStreams)  │     │                   │
│                   │     │ • Speed drop >50% │
│ • 5-sec windows   │     │   → auto-incident │
│ • Avg speed/seg   │     │ • Camera feeds    │
│ • Congestion lvl  │     │ • User reports    │
│ • Update Redis    │     │                   │
│                   │     │ Pods: 10-20       │
│ Pods: 20-50       │     └────────┬──────────┘
└────────┬──────────┘              │
         │                         │
         ▼                         ▼
┌──────────────────────────────────────────────────────────────┐
│              SEGMENT STATE STORE (Redis Cluster)              │
│  Key: segment:{id} → { avg_speed, congestion, travel_time }  │
│  10M segments × 50 bytes = 500 MB (all in memory)            │
│  TTL: 60 seconds (stale = no recent probes → show historical)│
└──────────────────────────┬───────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
│ TILE CACHE   │ │ ROUTING      │ │ HISTORICAL       │
│ (Redis/CDN)  │ │ SERVICE      │ │ TRAFFIC DB       │
│              │ │              │ │                  │
│ Pre-computed │ │ • A* on road │ │ TimescaleDB      │
│ tiles per    │ │   graph      │ │ • 5-min buckets  │
│ zoom level   │ │ • Live ETA   │ │ • Per segment    │
│ CDN: 5-sec   │ │ • Historical │ │ • Patterns by    │
│ cache TTL    │ │   comparison │ │   day/hour       │
│              │ │              │ │ ~21 TB/year      │
│ ~50 GB       │ │ Pods: 20-50  │ └──────────────────┘
└──────────────┘ └──────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      CLIENTS (Map UI)                            │
│  • Pan/zoom map → request tiles for visible area                │
│  • Tile = road segments + congestion colors + incidents          │
│  • Auto-refresh every 5 seconds (or SSE push)                   │
│  • Route ETA: click origin + destination → real-time ETA        │
└─────────────────────────────────────────────────────────────────┘
```

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Ingestion Gateway** | Validate/dedup GPS probes | Go on K8s | 50-100 pods |
| **Map-Matching** | Snap GPS to road segments (HMM) | Go/C++ on K8s | 50-100 pods (CPU-bound) |
| **Segment Aggregator** | Compute avg speed per segment (5s windows) | Apache Flink / Kafka Streams | 20-50 pods |
| **Incident Detector** | Detect accidents from speed drops | Go on K8s | 10-20 pods |
| **Segment State** | Live traffic state per segment | Redis Cluster | ~500 MB |
| **Tile Cache** | Pre-computed map tiles per zoom level | Redis + CDN (5s TTL) | ~50 GB |
| **Routing Service** | A* shortest path with live travel times | Go on K8s | 20-50 pods |
| **Historical DB** | Traffic patterns by time-of-day | TimescaleDB | ~21 TB/year |
| **Kafka** | Streaming pipeline for probes | Apache Kafka | 20+ brokers, 500 partitions |

---

## 6️⃣ Deep Dives

### DD1: Map Matching — GPS Point to Road Segment

**Problem**: GPS coordinates are noisy (±10m accuracy). A point at (34.052, -118.244) could be on I-405, a parallel service road, or an overpass/underpass. Must correctly snap to the right road.

```
Hidden Markov Model (HMM) map matching:
  - States: candidate road segments near the GPS point
  - Emission probability: P(GPS point | road segment) based on distance
  - Transition probability: P(segment_B after segment_A) based on route connectivity
  - Viterbi algorithm: find most likely sequence of segments for a series of GPS points

Per-probe processing:
  1. Find candidate segments within 50m radius (R-tree spatial index on road network)
  2. Score each candidate: distance + heading alignment + speed compatibility
  3. If device has recent history: use HMM with previous segment as prior
  4. Output: best-match segment_id + speed on that segment

Performance: ~0.5ms per probe (with pre-built R-tree index)
  1M probes/sec → 500 CPU-seconds/sec → ~100 pods at 5 cores each
```

### DD2: Geospatial Indexing — Road Segments for Viewport Queries

**Problem**: When user views a map viewport, need to find all road segments in that bounding box. With 10M segments, linear scan is too slow.

```
Spatial indexing options:
  1. GEOHASH: each segment tagged with geohash prefix (e.g., "9q5c")
     → Range scan on geohash prefix covers bounding box
     → Redis: ZRANGEBYLEX on geohash-sorted set

  2. R-TREE (chosen for map matching): spatial index tree for point-in-polygon queries
     → Find segments within radius of GPS point
     → In-memory per map-matching pod (loaded at startup)

  3. TILE-BASED (chosen for serving): pre-compute segments per map tile
     → Key: tile:{zoom}:{x}:{y} → [segment_ids]
     → Standard slippy map tiling (zoom 10-16)
     → Pre-computed: 10M segments assigned to tiles at build time
     → On segment update: regenerate affected tiles (1-4 tiles per segment)

Client rendering: request tiles for visible viewport (like image tiles in Google Maps)
  Zoom 14 tile covers ~1.5 km² → ~500 segments per tile → ~20 KB response
```

### DD3: Stream Processing — 5-Second Windows on 1M Events/Sec

**Problem**: Compute average speed per road segment from millions of GPS probes in real-time with 5-second tumbling windows.

```
Apache Flink / Kafka Streams:
  Input: matched-probes (segment_id, speed, timestamp)
  Window: 5-second tumbling window, keyed by segment_id
  
  Aggregation per window:
    sum_speed += probe.speed
    count += 1
    avg_speed = sum_speed / count

  Output (every 5 seconds per segment):
    { segment_id, avg_speed, sample_count, congestion_level, timestamp }

Handling sparse data:
  - If segment has 0 probes in window → use last known speed (TTL 60s)
  - If no data for 60s → fall back to historical pattern for this time-of-day
  - Mark segment as "stale" → show lighter color on map

Late/out-of-order data:
  - Allow 10-second lateness (watermark)
  - Late probes update next window (not recompute old windows)
  
Parallelism: partition by segment_id → each Flink task handles subset of segments
  10M segments / 50 tasks = 200K segments per task (manageable)
```

### DD4: Congestion Classification & Incident Detection

**Problem**: Classify each segment's congestion level and automatically detect incidents from speed anomalies.

```
Congestion classification:
  ratio = avg_speed / speed_limit
  FREE_FLOW: ratio > 0.80 → green
  MODERATE: 0.50 < ratio ≤ 0.80 → yellow
  HEAVY: 0.25 < ratio ≤ 0.50 → red
  STANDSTILL: ratio ≤ 0.25 → dark red

Incident detection (automated):
  1. Speed drop: if avg_speed drops > 50% in 1 minute → flag potential incident
  2. Spatial correlation: if 3+ adjacent segments all show sudden drops → likely accident
  3. Duration filter: speed must be low for > 2 minutes (filter out traffic lights)
  4. Create incident: { type: AUTOMATED_SLOWDOWN, segments, severity based on drop % }
  5. Auto-resolve: when speed recovers to > 50% of speed limit for 5 minutes

False positive reduction:
  - Ignore speed drops during known rush hours (use historical baseline)
  - Require minimum sample count (> 5 probes in window) for detection
  - Suppress for construction zones (pre-tagged segments)
```

### DD5: Tile Caching & CDN Strategy — 10M Queries/Sec

**Problem**: 50M concurrent users refreshing tiles every 5 seconds = 10M tile requests/sec. Must serve from cache.

```
Multi-layer caching:

L1: CDN edge cache (CloudFront/Fastly)
  - Tile URL: /tiles/{zoom}/{x}/{y}?t={5sec_bucket}
  - Cache-Control: public, max-age=5
  - CDN serves 95%+ of requests (< 10ms)
  - 5-second time bucket in URL ensures freshness

L2: Redis tile cache (origin)
  - Pre-computed tiles written by Tile Generator
  - On segment update → regenerate affected tile(s)
  - Tile update takes ~10ms (fetch 500 segments, serialize)

L3: Compute on miss
  - If tile not in cache → compute from Redis segment states
  - Rare: only for unusual zoom levels or new areas

Tile generation strategy:
  - On each segment update: determine which tiles contain this segment
  - Segment → tile mapping pre-computed (stored in Redis SET)
  - Regenerate those tiles (1-4 tiles per segment typically)
  - Write to Redis + invalidate CDN (or let TTL expire in 5s)
```

### DD6: Route ETA — Live Traffic-Aware Shortest Path

**Problem**: Compute fastest route from A to B using live traffic speeds on every road segment. Must be fast (< 200ms) despite road graph having millions of nodes.

```
Road graph: 10M segments = 10M edges, ~5M intersections = 5M nodes
  Pre-loaded in memory on Routing Service pods (contraction hierarchies)

Algorithm: A* with live travel times
  1. Edge weight = segment.travelTimeSeconds (from live Redis data)
  2. Heuristic: straight-line distance / max_speed → admissible
  3. Optimization: Contraction Hierarchies (CH) pre-processing
     → Pre-compute shortcuts for highway-level routing
     → Live traffic only affects lower-level roads (local)
     → Combine: CH for long-range + Dijkstra for local

Live weight lookup:
  - Each Routing pod has local cache of segment travel times (refreshed every 5s from Redis)
  - Cache size: 10M × 8 bytes = 80 MB (easily fits in memory)

Performance: CH + A* = < 50ms for cross-city routes
  20-50 routing pods handle ~10K ETA requests/sec
```

### DD7: Handling Noisy & Sparse GPS Data

**Problem**: GPS probes are noisy (±10m), some roads have few probes, and data arrives out of order.

```
Data quality pipeline (in Map-Matching Service):
  1. Filter: reject probes with speed > 200 km/h (GPS jump) or speed < 0
  2. Smooth: exponential moving average on per-device speed
  3. Deduplicate: same device + same timestamp → keep latest
  4. Out-of-order: accept probes within 30-second lateness (Kafka watermark)

Sparse roads (few probes):
  - If < 3 probes in 5-sec window → low confidence
  - Fall back to historical average for this segment + time-of-day
  - Blend: weight = min(1.0, sample_count / 10)
    blended_speed = weight × live_speed + (1 - weight) × historical_speed
  - Show lighter color on map (low confidence indicator)

GPS tunnel/garage gaps:
  - Device enters tunnel → no GPS → gap in probes
  - On exit: snap to tunnel exit segment, interpolate speed through tunnel
  - Use historical tunnel traversal time as estimate
```

### DD8: Historical Traffic Patterns — Predictive Baseline

**Problem**: At 8 AM on a Tuesday, what's the expected speed on I-405 N? Need historical patterns for: fallback when live data is sparse, ETA comparison, anomaly detection baseline.

```
Historical model:
  For each segment: store average speed by (day_of_week, 5_minute_bucket)
  = 7 days × 288 buckets/day = 2,016 data points per segment
  10M segments × 2016 × 4 bytes = ~80 GB

Storage: TimescaleDB (append historical samples, compute rolling averages)

Computation: daily batch job
  For each segment, for each (day_of_week, time_bucket):
    avg over last 90 days of data for that slot

Serving: pre-loaded in Redis per segment
  Key: historical:{segment_id}:{day}:{bucket} → avg_speed
  Or: compact binary blob per segment (2016 × 2 bytes = 4 KB) → Redis hash

Use cases:
  1. Sparse live data → blend with historical baseline
  2. ETA: show "22 min (usually 15 min)" comparison
  3. Anomaly: if live speed is 50% below historical → flag as unusual
  4. Predictive: "traffic typically builds at 4:30 PM on this route"
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Ingestion** | Kafka (geohash-partitioned, 500 partitions) | 1M events/sec; ordered by region for spatial locality |
| **Map matching** | HMM with R-tree spatial index | Accurate road snapping; handles noise, overpasses, parallel roads |
| **Aggregation** | Flink 5-second tumbling windows | Low-latency real-time; exactly-once semantics; keyed by segment |
| **Segment state** | Redis (all 10M segments in ~500 MB) | Fits in memory; < 1ms reads; 5-second TTL for freshness |
| **Tile serving** | CDN (5s TTL) + Redis pre-computed tiles | 95% cache hit; 10M queries/sec; tile-based = standard map rendering |
| **Routing** | Contraction Hierarchies + live travel times | < 50ms route computation; pre-processed graph + live edge weights |
| **Sparse data** | Blend live + historical (confidence-weighted) | Never show blank roads; smooth degradation when data is sparse |
| **Incident detection** | Speed drop + spatial correlation + duration filter | Low false positives; automatic detection + auto-resolve |

## Interview Talking Points

1. **"GPS → Kafka → map matching (HMM) → Flink aggregation → Redis segment state → CDN tiles → client"** — End-to-end < 30 seconds. 1M probes/sec ingested, snapped to road segments, aggregated in 5-sec windows, served as pre-computed map tiles via CDN.

2. **"Map matching with HMM and R-tree spatial index"** — GPS is noisy (±10m). HMM considers distance, heading, route continuity. R-tree finds candidate segments in 50m radius. ~0.5ms per probe.

3. **"Flink 5-second tumbling windows for per-segment speed aggregation"** — Keyed by segment_id. Average speed + congestion classification. Late data handled via 10-second watermark. Sparse segments blend with historical baseline.

4. **"CDN tile cache with 5-second TTL for 10M tile requests/sec"** — Pre-computed tiles per zoom level. CDN serves 95%+ requests. Tile regenerated on segment update (~10ms). Standard slippy map tiling.

5. **"Route ETA: Contraction Hierarchies with live travel times in < 50ms"** — Road graph pre-processed. Edge weights from live Redis data (80 MB local cache). A* for short routes, CH for cross-city.

6. **"Incident detection: speed drop > 50% + spatial correlation across 3+ segments"** — Auto-detect accidents. Duration filter (> 2 min) reduces false positives. Auto-resolve when speed recovers. Suppress during known rush hours.

---

## 🔗 Related Problems
| Problem | Key Difference |
|---------|---------------|
| **Google Maps / Waze** | Our exact design |
| **Uber Surge Pricing** | Uses same GPS data; computes demand not congestion |
| **Fleet Tracking** | Tracks specific vehicles; we aggregate all vehicles anonymously |
| **IoT Sensor Platform** | Same high-throughput ingestion; we add geospatial + routing |

## 🔧 Technology Alternatives
| Component | Chosen | Alternative |
|-----------|--------|------------|
| **Stream processing** | Apache Flink | Kafka Streams / Spark Streaming |
| **Spatial index** | R-tree + geohash | H3 (Uber's hex grid) / QuadTree |
| **Routing** | Contraction Hierarchies | OSRM / GraphHopper / Valhalla |
| **Segment state** | Redis | Hazelcast / Apache Ignite (distributed cache) |
| **Map tiles** | Vector tiles (MVT) | Raster tiles (pre-rendered images) |
| **Historical DB** | TimescaleDB | ClickHouse / Apache Druid |

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Time**: 45-60 min
**References**: Google Maps Traffic Architecture, Uber H3 Hexagonal Grid, HMM Map Matching, Contraction Hierarchies, Apache Flink Windowing