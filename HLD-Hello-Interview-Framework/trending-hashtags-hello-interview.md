# Trending Hashtags System Design - Hello Interview Framework

> **Question**: Design a highly scalable system that tracks and computes the top K trending hashtags (e.g., top 30-50) across different time windows (last 5/15/30/60 minutes or custom intervals) for billions of users, with support for filtering by geography (local/global) and categories (food, sports, politics).
>
> **Asked at**: Meta, Atlassian, Google, LinkedIn
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
1. **Track Hashtag Usage**: Ingest hashtag events from posts, comments, stories across the platform in real-time.
2. **Compute Top-K**: Return the top 30-50 trending hashtags over configurable sliding time windows (5 min, 15 min, 30 min, 1 hour).
3. **Geographic Filtering**: Support trending by country, region, city, or global.
4. **Category Filtering**: Support trending by category (sports, food, politics, entertainment).
5. **Low-Latency Serving**: Return trending results in < 50ms.

#### Nice to Have (P1)
- Custom time windows (user picks "last 2 hours").
- Trending velocity (hashtags rising fastest, not just highest count).
- Personalized trending (based on user interests/follows).
- Historical trending data (what was trending yesterday/last week).
- Trend detection alerts (notify editors when a new topic emerges).

#### Below the Line (Out of Scope)
- Hashtag content moderation / filtering offensive hashtags.
- Full-text search of posts containing a hashtag.
- Hashtag recommendation ("you might also like #topic").

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Ingestion Throughput** | 500K-1M hashtag events/sec | Billions of users, many posts per second |
| **Query Latency** | < 50ms (p99) for top-K query | Real-time UX, shown on every app open |
| **Freshness** | Top-K updated every 5-10 seconds | Users expect "live" trending |
| **Accuracy** | Approximate OK (within 5% of exact) | Exact counts not needed; approximate algorithms save 100x resources |
| **Availability** | 99.99% | Trending is a high-visibility feature |
| **Scalability** | 1B+ unique hashtags, 200+ countries | Global social platform |
| **Fault Tolerance** | Degrade gracefully; stale trends better than no trends | Serve cached results if pipeline is down |

### Capacity Estimation

```
Ingestion:
  Active users: 2B
  Posts per user per day: 2-5
  Hashtags per post: 2 (average)
  Total hashtag events/day: 2B × 3 × 2 = 12B events/day
  Sustained: 12B / 86400 = ~140K events/sec
  Peak (3x): ~420K events/sec
  
  Event size: ~100 bytes (hashtag, user_id, country, category, timestamp)
  Daily ingestion: 12B × 100 bytes = 1.2 TB/day

Unique Hashtags:
  Active in any 1-hour window: ~10M unique hashtags
  All-time: 1B+ unique hashtags

Serving:
  Trending queries: ~100K QPS (every app open fetches trending)
  Response size: top 50 × 200 bytes = 10 KB per response
  Bandwidth: 100K × 10 KB = 1 GB/sec (easily CDN-cacheable)

Storage (sliding windows):
  Per-hashtag per-window counter: 16 bytes (hashtag_id + count)
  10M hashtags × 4 windows × 200 countries = 8B entries × 16 bytes = 128 GB
  Fits in memory across a Redis cluster
```

---

## 2️⃣ Core Entities

### Entity 1: HashtagEvent (Ingestion)
```java
public class HashtagEvent {
    private final String hashtag;          // e.g., "#WorldCup"
    private final String userId;
    private final String country;          // ISO 3166-1: "US", "BR", "JP"
    private final String category;         // SPORTS, FOOD, POLITICS, ENTERTAINMENT, GENERAL
    private final Instant timestamp;       // Event time (when post was created)
    private final String sourceType;       // POST, COMMENT, STORY, REEL
}
```

### Entity 2: HashtagCount (Aggregated Counter)
```java
public class HashtagCount {
    private final String hashtag;
    private final String country;          // "US", "GLOBAL"
    private final String category;         // "SPORTS", "ALL"
    private final String windowKey;        // "5m", "15m", "30m", "60m"
    private final long count;              // Number of usages in this window
    private final Instant windowStart;
    private final Instant windowEnd;
}
```

### Entity 3: TrendingResult (Served to Clients)
```java
public class TrendingResult {
    private final String window;           // "5m", "15m", "30m", "60m"
    private final String country;
    private final String category;
    private final List<TrendingHashtag> hashtags;  // Top K, sorted by count desc
    private final Instant computedAt;
    private final boolean approximate;     // true if using Count-Min Sketch
}

public class TrendingHashtag {
    private final int rank;                // 1-50
    private final String hashtag;
    private final long count;              // Approximate usage count
    private final double velocityScore;    // How fast it's rising (optional)
    private final String category;
}
```

---

## 3️⃣ API Design

### 1. Get Trending Hashtags
```
GET /api/v1/trending?window=15m&country=US&category=SPORTS&limit=30

Response (200 OK):
{
  "window": "15m",
  "country": "US",
  "category": "SPORTS",
  "computed_at": "2025-01-10T19:30:05Z",
  "freshness_seconds": 5,
  "hashtags": [
    { "rank": 1, "hashtag": "#SuperBowl", "count": 2345678, "velocity": 12.5 },
    { "rank": 2, "hashtag": "#NFLPlayoffs", "count": 1876543, "velocity": 8.2 },
    { "rank": 3, "hashtag": "#Halftime", "count": 1234567, "velocity": 15.1 }
  ]
}
```

### 2. Ingest Hashtag Event (Internal)
```
POST /api/v1/events (internal, from post service)

Request:
{
  "hashtag": "#SuperBowl",
  "user_id": "user_123",
  "country": "US",
  "category": "SPORTS",
  "timestamp": "2025-01-10T19:30:00Z",
  "source_type": "POST"
}

Response (202 Accepted)
```

> In practice, events are published to Kafka by the post service, not via HTTP API.

---

## 4️⃣ Data Flow

### Flow 1: Hashtag Event Ingestion
```
1. User creates a post with #SuperBowl
   ↓
2. Post Service extracts hashtags from post text
   ↓
3. Publish HashtagEvent to Kafka topic "hashtag-events"
   Partition key = hashtag (all events for same hashtag go to same partition)
   ↓
4. Stream Processor (Flink/Kafka Streams) consumes events:
   a. Update Count-Min Sketch for each time window
   b. Update per-country, per-category sketches
   c. Every 5-10 seconds: compute top-K from sketch → write to Redis
   ↓
5. Redis stores pre-computed trending results:
   Key: trending:{window}:{country}:{category}
   Value: sorted list of top 50 hashtags with counts
   TTL: 30 seconds (auto-expire stale results)
```

### Flow 2: Serving Trending Query
```
1. Client requests: GET /api/v1/trending?window=15m&country=US&category=SPORTS
   ↓
2. API Gateway → Trending Service
   ↓
3. Trending Service reads from Redis:
   Key: "trending:15m:US:SPORTS"
   ↓
4. If Redis has fresh data (< 10 seconds old):
   → Return immediately (< 5ms)
   ↓
5. If Redis is stale or missing:
   → Return last known good result (graceful degradation)
   → Background: trigger recomputation
   ↓
6. Return top-K list to client
   Total latency: < 50ms
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────┐
│                         POST SERVICE                               │
│  User creates post → extract hashtags → publish to Kafka           │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                      KAFKA CLUSTER                                  │
│  Topic: "hashtag-events" (100+ partitions)                         │
│  Partition key: hashtag (co-locate same hashtag)                   │
│  Throughput: 500K events/sec                                       │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│               STREAM PROCESSOR (Apache Flink)                      │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │  Per-Partition Processing:                                │      │
│  │  1. Parse event                                          │      │
│  │  2. Update Count-Min Sketch (per window, country, cat)   │      │
│  │  3. Maintain min-heap of top-K per dimension             │      │
│  │  4. Every 5s: flush top-K to Redis                       │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                     │
│  Windows: 5m, 15m, 30m, 60m (tumbling + sliding)                  │
│  Parallelism: 100+ tasks (one per Kafka partition)                 │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                      REDIS CLUSTER                                  │
│                                                                     │
│  Pre-computed trending results:                                    │
│  Key: trending:{window}:{country}:{category}                      │
│  Value: JSON list of top 50 hashtags + counts                     │
│                                                                     │
│  Total keys: 4 windows × 200 countries × 10 categories = 8,000   │
│  Memory: 8,000 × 10 KB = 80 MB (trivial)                         │
│  TTL: 30 seconds per key                                          │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                    TRENDING SERVICE (API)                           │
│  • Read from Redis (< 5ms)                                        │
│  • Fallback: stale cache if Redis unavailable                     │
│  • CDN-cacheable (5-10 second TTL)                                │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                     │
│  Mobile App / Web → "Trending" tab / Explore page                  │
└────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Post Service** | Extract hashtags, publish events | Existing platform service | N/A |
| **Kafka** | Durable event stream, partitioned by hashtag | Apache Kafka | 100+ partitions |
| **Flink** | Real-time stream processing, Count-Min Sketch, top-K | Apache Flink | 100+ parallel tasks |
| **Redis** | Pre-computed trending results, < 5ms reads | Redis Cluster | 3-6 nodes |
| **Trending Service** | Serve API, read from Redis | Java/Spring Boot | Stateless, horizontal |
| **CDN** | Cache trending responses at edge | CloudFront/Akamai | Global PoPs |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Count-Min Sketch — Approximate Counting at Scale

**The Problem**: Tracking exact counts for 10M+ unique hashtags across multiple windows, countries, and categories requires too much memory with naive hash maps.

**Count-Min Sketch**: A probabilistic data structure that uses sub-linear memory to estimate frequencies with bounded error.

```java
public class CountMinSketch {
    private final int depth;   // Number of hash functions (d)
    private final int width;   // Number of counters per row (w)
    private final long[][] table;
    private final int[] seeds;

    /**
     * @param epsilon Acceptable error rate (e.g., 0.001 = 0.1%)
     * @param delta   Probability of exceeding error (e.g., 0.01 = 1%)
     */
    public CountMinSketch(double epsilon, double delta) {
        this.width = (int) Math.ceil(Math.E / epsilon);      // w = e/ε
        this.depth = (int) Math.ceil(Math.log(1.0 / delta)); // d = ln(1/δ)
        this.table = new long[depth][width];
        this.seeds = IntStream.range(0, depth).toArray();
    }

    /** Increment count for a hashtag */
    public void increment(String hashtag) {
        for (int i = 0; i < depth; i++) {
            int index = hash(hashtag, seeds[i]) % width;
            table[i][Math.abs(index)]++;
        }
    }

    /** Estimate count for a hashtag (never underestimates, may overestimate) */
    public long estimate(String hashtag) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            int index = hash(hashtag, seeds[i]) % width;
            min = Math.min(min, table[i][Math.abs(index)]);
        }
        return min;
    }

    private int hash(String key, int seed) {
        return Hashing.murmur3_32_fixed(seed)
            .hashString(key, StandardCharsets.UTF_8).asInt();
    }
}

// Sizing:
// ε = 0.001 (0.1% error), δ = 0.01 (99% confidence)
// width = 2719, depth = 5
// Memory: 5 × 2719 × 8 bytes = ~109 KB per sketch
// 
// For 4 windows × 200 countries × 10 categories = 8,000 sketches
// Total: 8,000 × 109 KB = ~850 MB (fits in Flink's memory)
```

**Why CMS Over Exact Counts?**
```
Exact HashMap:
  10M hashtags × 8 bytes count × 8,000 dimensions = 640 GB
  → Doesn't fit in memory, requires distributed counting

Count-Min Sketch:
  8,000 sketches × 109 KB = 850 MB
  → Fits in a single Flink task manager
  → 0.1% error is acceptable for "trending" (nobody cares if #SuperBowl
    has 2,345,678 vs 2,348,000 uses)
```

---

### Deep Dive 2: Sliding Window with Tumbling Micro-Batches

**The Problem**: "Top trending in the last 15 minutes" is a sliding window. Maintaining a true sliding window is expensive — every event needs to be both added and removed at the right time.

**Solution: Approximate Sliding Window using Tumbling Micro-Batches**

```java
public class SlidingWindowTopK {
    // 15-minute sliding window = 15 × 1-minute tumbling buckets
    // Each bucket has its own Count-Min Sketch
    private static final int BUCKET_COUNT = 15;
    private static final Duration BUCKET_SIZE = Duration.ofMinutes(1);
    
    private final CountMinSketch[] buckets = new CountMinSketch[BUCKET_COUNT];
    private final MinHeap<HashtagCount> topKHeap = new MinHeap<>(50);
    private int currentBucketIndex = 0;

    /** Called for every hashtag event */
    public void addEvent(String hashtag, Instant eventTime) {
        int bucketIdx = getBucketIndex(eventTime);
        buckets[bucketIdx].increment(hashtag);
    }

    /** Rotate: clear the oldest bucket every minute */
    @Scheduled(fixedRate = 60_000)
    public void rotateBucket() {
        currentBucketIndex = (currentBucketIndex + 1) % BUCKET_COUNT;
        buckets[currentBucketIndex] = new CountMinSketch(0.001, 0.01); // Reset oldest
    }

    /** Compute top-K by summing across all active buckets */
    public List<HashtagCount> getTopK(int k) {
        // Sum estimates across all 15 buckets for candidate hashtags
        // Candidates come from a "heavy hitter" set maintained separately
        Map<String, Long> totals = new HashMap<>();
        for (String candidate : heavyHitterCandidates) {
            long total = 0;
            for (int i = 0; i < BUCKET_COUNT; i++) {
                total += buckets[i].estimate(candidate);
            }
            totals.put(candidate, total);
        }
        
        // Return top K
        return totals.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(k)
            .map(e -> new HashtagCount(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }
}
```

```
Window: "Last 15 minutes" at time T=15:30
  
  Bucket layout (1-min tumbling buckets):
  [15:16] [15:17] [15:18] ... [15:29] [15:30]
   old ←────────────────────────────── new
  
  At T=15:31: clear bucket [15:16], reuse for [15:31]
  
  Accuracy: ±1 minute granularity (good enough for trending)
  Memory: 15 buckets × 109 KB = 1.6 MB per window dimension
```

---

### Deep Dive 3: Heavy Hitter Detection (Space-Saving Algorithm)

**The Problem**: Count-Min Sketch can estimate any hashtag's count, but we need to know WHICH hashtags to check. We need a "candidate set" of potential top-K hashtags.

```java
public class SpaceSavingTopK {
    private final int maxSize;
    private final Map<String, Long> counts = new HashMap<>();
    private final TreeMap<Long, Set<String>> countToKeys = new TreeMap<>();

    public SpaceSavingTopK(int maxSize) {
        this.maxSize = maxSize; // Track top ~200 candidates (more than K=50 for safety margin)
    }

    /** Process a hashtag event */
    public void add(String hashtag) {
        if (counts.containsKey(hashtag)) {
            // Already tracked — increment
            long oldCount = counts.get(hashtag);
            long newCount = oldCount + 1;
            counts.put(hashtag, newCount);
            countToKeys.get(oldCount).remove(hashtag);
            countToKeys.computeIfAbsent(newCount, k -> new HashSet<>()).add(hashtag);
        } else if (counts.size() < maxSize) {
            // Space available — add new entry
            counts.put(hashtag, 1L);
            countToKeys.computeIfAbsent(1L, k -> new HashSet<>()).add(hashtag);
        } else {
            // Full — evict the minimum count entry, replace with new
            Map.Entry<Long, Set<String>> minEntry = countToKeys.firstEntry();
            long minCount = minEntry.getKey();
            String evicted = minEntry.getValue().iterator().next();
            minEntry.getValue().remove(evicted);
            if (minEntry.getValue().isEmpty()) countToKeys.remove(minCount);
            counts.remove(evicted);
            
            // New entry starts with minCount + 1 (overestimate, but bounded)
            long newCount = minCount + 1;
            counts.put(hashtag, newCount);
            countToKeys.computeIfAbsent(newCount, k -> new HashSet<>()).add(hashtag);
        }
    }

    /** Get current top K candidates */
    public List<Map.Entry<String, Long>> getTopK(int k) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(k)
            .collect(Collectors.toList());
    }
}

// Memory: tracking 200 candidates × ~100 bytes = 20 KB per dimension
// Very compact, O(1) per event, guaranteed to contain true top-K if K < maxSize/2
```

---

### Deep Dive 4: Hot Hashtag Partitioning (Avoiding Skew)

**The Problem**: During the Super Bowl, `#SuperBowl` gets 100x more events than average. If we partition Kafka by hashtag, one partition gets 100x the load.

```java
public class SkewAwarePartitioner implements Partitioner {
    private final Set<String> hotHashtags = ConcurrentHashMap.newKeySet();

    @Override
    public int partition(String topic, String key, byte[] value, int numPartitions) {
        String hashtag = key;
        
        if (hotHashtags.contains(hashtag)) {
            // HOT hashtag: spread across random partitions
            // Multiple Flink tasks will count independently, then merge
            int randomSuffix = ThreadLocalRandom.current().nextInt(10); // 10 sub-partitions
            return Math.abs((hashtag + ":" + randomSuffix).hashCode()) % numPartitions;
        } else {
            // Normal hashtag: deterministic partition
            return Math.abs(hashtag.hashCode()) % numPartitions;
        }
    }
}

// For hot hashtags distributed across 10 sub-partitions:
// Each Flink task counts independently
// A downstream "merge" step sums the 10 partial counts
// This prevents any single task from being a bottleneck
```

---

### Deep Dive 5: Multi-Dimensional Trending (Country × Category × Window)

**The Problem**: We need trending for every combination of (country, category, window). That's 200 countries × 10 categories × 4 windows = 8,000 dimensions.

```java
public class MultiDimensionalAggregator {
    // One sketch per (window, country, category) combination
    private final Map<String, CountMinSketch> sketches = new ConcurrentHashMap<>();
    private final Map<String, SpaceSavingTopK> candidates = new ConcurrentHashMap<>();

    public void processEvent(HashtagEvent event) {
        String hashtag = event.getHashtag();
        String country = event.getCountry();
        String category = event.getCategory();

        // Update sketches for all applicable dimensions
        for (String window : List.of("5m", "15m", "30m", "60m")) {
            // Country-specific + category-specific
            updateDimension(window, country, category, hashtag);
            // Country-specific + all categories
            updateDimension(window, country, "ALL", hashtag);
            // Global + category-specific
            updateDimension(window, "GLOBAL", category, hashtag);
            // Global + all categories
            updateDimension(window, "GLOBAL", "ALL", hashtag);
        }
    }

    private void updateDimension(String window, String country, String category, String hashtag) {
        String key = window + ":" + country + ":" + category;
        sketches.computeIfAbsent(key, k -> new CountMinSketch(0.001, 0.01)).increment(hashtag);
        candidates.computeIfAbsent(key, k -> new SpaceSavingTopK(200)).add(hashtag);
    }

    /** Compute and flush top-K for all dimensions to Redis */
    @Scheduled(fixedRate = 5_000) // Every 5 seconds
    public void flushTopKToRedis() {
        for (var entry : candidates.entrySet()) {
            String dimensionKey = entry.getKey();
            SpaceSavingTopK topK = entry.getValue();
            CountMinSketch sketch = sketches.get(dimensionKey);

            List<TrendingHashtag> trending = topK.getTopK(50).stream()
                .map(e -> new TrendingHashtag(e.getKey(), sketch.estimate(e.getKey())))
                .sorted(Comparator.comparingLong(TrendingHashtag::getCount).reversed())
                .collect(Collectors.toList());

            redis.setex("trending:" + dimensionKey, 30, 
                        objectMapper.writeValueAsString(trending));
        }
    }
}

// Fan-out per event: 4 windows × (1 country + 1 global) × (1 category + 1 all) = 4 × 2 × 2 = 16 sketch updates
// At 500K events/sec: 8M sketch updates/sec — easily handled by Flink at ~100 tasks
```

---

### Deep Dive 6: Serving Layer — Redis + CDN

```java
public class TrendingService {
    private final JedisCluster redis;
    private final LoadingCache<String, TrendingResult> localCache;

    public TrendingResult getTrending(String window, String country, String category, int limit) {
        String key = "trending:" + window + ":" + country + ":" + category;

        // Layer 1: Local in-process cache (< 0.1ms)
        TrendingResult cached = localCache.getIfPresent(key);
        if (cached != null) return cached;

        // Layer 2: Redis (< 5ms)
        String json = redis.get(key);
        if (json != null) {
            TrendingResult result = objectMapper.readValue(json, TrendingResult.class);
            localCache.put(key, result); // Cache locally for 5 seconds
            return result;
        }

        // Layer 3: Fallback — return global trending as default
        String fallback = redis.get("trending:" + window + ":GLOBAL:ALL");
        return fallback != null ? objectMapper.readValue(fallback, TrendingResult.class)
                                : TrendingResult.empty();
    }
}
```

**Caching Strategy**:
```
CDN (CloudFront): TTL = 10 seconds
  → 100K QPS reduced to ~100 origin QPS per edge location
  Cache key: /trending?window=15m&country=US&category=ALL

API Local Cache (Caffeine): TTL = 5 seconds
  → Avoid Redis round-trip for hot keys

Redis: TTL = 30 seconds
  → Updated every 5 seconds by Flink
  → Stale reads acceptable (trending is not real-time-critical)
```

---

### Deep Dive 7: Velocity Scoring — What's Rising Fastest

**The Problem**: Raw count alone isn't enough. "#goodmorning" has high count every day but isn't trending. We want hashtags that are rising UNUSUALLY fast.

```java
public class VelocityScorer {
    /**
     * Compute velocity: how much faster is this hashtag growing
     * compared to its baseline rate.
     * 
     * velocity = (count_last_5m / count_last_60m) × recency_boost
     */
    public double computeVelocity(String hashtag, Map<String, CountMinSketch> sketches) {
        long count5m = sketches.get("5m").estimate(hashtag);
        long count60m = sketches.get("60m").estimate(hashtag);

        if (count60m == 0) return count5m > 10 ? 100.0 : 0.0; // Brand new hashtag

        // What fraction of the hour's volume happened in the last 5 minutes?
        // If evenly distributed: 5/60 = 8.3%
        // If trending: much higher than 8.3%
        double expectedFraction = 5.0 / 60.0; // 8.3%
        double actualFraction = (double) count5m / count60m;
        double velocity = actualFraction / expectedFraction; // > 1 means trending up

        // Apply minimum volume threshold (ignore hashtags with < 100 uses)
        if (count60m < 100) velocity = 0.0;

        return velocity;
    }

    /**
     * Rank trending by combined score: volume × velocity
     * This surfaces hashtags that are both popular AND rising fast
     */
    public double combinedScore(String hashtag, long count, double velocity) {
        return count * Math.log1p(velocity); // Log dampens extreme velocity
    }
}

// Examples:
// #goodmorning:  count_5m=10000, count_60m=120000 → velocity=1.0 (flat, not trending)
// #BreakingNews: count_5m=50000, count_60m=60000  → velocity=10.0 (TRENDING!)
// #NewHashtag:   count_5m=5000,  count_60m=5100   → velocity=11.8 (very new, rising fast)
```

---

### Deep Dive 8: Fault Tolerance & Graceful Degradation

```java
public class ResilientTrendingService {
    private volatile TrendingResult lastKnownGoodResult;

    public TrendingResult getTrendingWithFallback(String window, String country, String category) {
        try {
            TrendingResult result = trendingService.getTrending(window, country, category, 50);
            lastKnownGoodResult = result;
            return result;
        } catch (Exception e) {
            log.warn("Trending service failed, serving stale result: {}", e.getMessage());
            metrics.counter("trending.fallback.stale").increment();
            return lastKnownGoodResult != null ? lastKnownGoodResult : TrendingResult.empty();
        }
    }
}
```

**Failure Modes**:
```
Kafka down → Flink has no new events → Redis keeps stale results (TTL 30s)
  → Serve stale trending (still useful, just not updated)
  
Flink down → No new top-K computed → Redis TTL expires
  → Serve last known good result from local cache
  
Redis down → API can't read trending
  → Serve from local in-process cache (Caffeine, 5s TTL)
  → If local cache also expired, return hardcoded "editorial picks"
  
All down → Return empty result with "trending unavailable" message
```

---

### Deep Dive 9: Observability & Metrics

```java
public class TrendingMetrics {
    // Ingestion
    Counter eventsIngested     = Counter.builder("trending.events.ingested").register(registry);
    Counter eventsDropped      = Counter.builder("trending.events.dropped").register(registry);
    Gauge   kafkaLag           = Gauge.builder("trending.kafka.consumer_lag", ...).register(registry);
    
    // Processing
    Timer   sketchUpdateTime   = Timer.builder("trending.sketch.update_ms").register(registry);
    Timer   topKComputeTime    = Timer.builder("trending.topk.compute_ms").register(registry);
    Gauge   uniqueHashtags     = Gauge.builder("trending.unique_hashtags", ...).register(registry);
    Counter redisFlushes       = Counter.builder("trending.redis.flushes").register(registry);
    
    // Serving
    Timer   queryLatency       = Timer.builder("trending.query.latency_ms").register(registry);
    Counter queriesTotal       = Counter.builder("trending.queries.total").register(registry);
    Counter cacheHits          = Counter.builder("trending.cache.hits").register(registry);
    Counter fallbacksServed    = Counter.builder("trending.fallback.served").register(registry);
    
    // Data quality
    Gauge   freshnessSeconds   = Gauge.builder("trending.freshness_seconds", ...).register(registry);
    Gauge   topHashtagCount    = Gauge.builder("trending.top1.count", ...).register(registry);
}
```

**Key Alerts**:
```yaml
alerts:
  - name: TrendingStaleness
    condition: trending.freshness_seconds > 60
    severity: CRITICAL
    message: "Trending data >60s stale. Flink pipeline may be down."
    
  - name: HighKafkaLag
    condition: trending.kafka.consumer_lag > 1000000
    severity: WARNING
    message: "Kafka consumer lag >1M events. Trending may be delayed."
    
  - name: HighFallbackRate
    condition: rate(trending.fallback.served, 5m) / rate(trending.queries.total, 5m) > 0.1
    severity: WARNING
    message: ">10% of queries served from fallback. Redis may be unhealthy."
```

---

### Deep Dive 10: Comparison of Approximate Algorithms

| Algorithm | Memory | Update | Query | Supports Delete? | Use Case |
|-----------|--------|--------|-------|------------------|----------|
| **Count-Min Sketch** | O(1/ε × ln(1/δ)) | O(d) | O(d) | No | Frequency estimation |
| **Space-Saving** | O(K) | O(1) amortized | O(K log K) | Yes | Top-K candidates |
| **HyperLogLog** | 12 KB fixed | O(1) | O(1) | No | Count distinct |
| **Bloom Filter** | O(n) | O(k) | O(k) | No | Membership test |

**Our Combination**:
```
Count-Min Sketch: "How many times was #SuperBowl used?" → ~2,345,000
Space-Saving:     "What are the candidate top-50 hashtags?" → [#SuperBowl, #NFL, ...]
Together:         CMS provides counts, Space-Saving provides candidate list
                  → Top-K with bounded error and bounded memory
```

---

## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Counting** | Count-Min Sketch (approximate) | 850 MB vs 640 GB exact; 0.1% error is fine for trending |
| **Top-K candidates** | Space-Saving algorithm | O(1) per event, guaranteed top-K coverage |
| **Windows** | Tumbling micro-batches (1-min buckets) | Simpler than true sliding window; ±1 min accuracy |
| **Processing** | Apache Flink (stream) | Native windowing, checkpointing, exactly-once |
| **Serving** | Redis + CDN + local cache | < 5ms reads, 3-layer caching, graceful degradation |
| **Partitioning** | Hashtag-based + hot-key spreading | Avoids skew on viral hashtags |
| **Multi-dimensional** | Fan-out to 16 sketches per event | 8,000 dimensions × 109 KB = 850 MB total |
| **Ranking** | Volume × velocity | Surfaces rising topics, not just always-popular ones |

## 🎯 Interview Talking Points

1. **"Count-Min Sketch is the core insight"** — 850 MB vs 640 GB. Approximate counting makes this problem tractable. 0.1% error is invisible to users.

2. **"Tumbling micro-batches approximate sliding windows"** — 15 × 1-min buckets simulate a 15-min sliding window with ±1 min accuracy. True sliding windows are 100x more expensive.

3. **"Pre-compute everything, serve nothing"** — Flink writes top-K to Redis every 5 seconds. The serving layer is just a Redis GET — < 5ms, infinitely scalable with CDN.

4. **"Velocity scoring separates trending from popular"** — `#goodmorning` is always popular but never trending. Velocity = recent fraction / expected fraction. Score > 1 means it's rising.

5. **"Hot hashtag spreading prevents skew"** — `#SuperBowl` would overload one Kafka partition. Spread across 10 random partitions, merge counts downstream.

6. **"Three layers of degradation"** — Redis stale data → local cache → editorial picks. Trending should never be "unavailable."

---

**References**:
- Count-Min Sketch (Cormode & Muthukrishnan, 2005)
- Space-Saving Algorithm (Metwally et al., 2005)
- Twitter Trends Architecture
- Apache Flink Windowing Documentation
- Redis as a Real-Time Serving Layer

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Deep Dives**: 10 topics
