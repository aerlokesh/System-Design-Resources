# Design an Auto-Complete / Type-Ahead System - Hello Interview Framework

> **Question**: Design a real-time auto-complete (type-ahead) backend that powers search suggestions as users type queries. The system must return ranked suggestions within 100ms for billions of queries daily, support personalization, and handle trending/fresh content.
>
> **Asked at**: Google, Amazon, LinkedIn, Uber, Yelp, Netflix
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
1. **Prefix-Based Suggestions**: Given a prefix typed by the user (e.g., "face"), return top-K ranked suggestions (e.g., "facebook", "facebook login", "face swap app") within 100ms.
2. **Ranking by Popularity**: Suggestions ranked by a weighted score combining search frequency, recency, and trending signals.
3. **Real-Time Updates**: Trending queries (e.g., breaking news, viral events) should appear in suggestions within 1-5 minutes of becoming popular.
4. **Multi-Language Support**: Handle queries in multiple languages and scripts (Latin, CJK, Arabic, etc.) including non-space-delimited languages.
5. **Filtering**: Remove offensive, NSFW, and legally restricted content from suggestions. Apply geo-specific filtering (e.g., GDPR right-to-be-forgotten).

#### Nice to Have (P1)
- Personalized suggestions based on user's search history and preferences.
- Query correction / "did you mean" (fuzzy matching for typos).
- Category-aware suggestions (e.g., "apple" → show both the fruit and the company with labels).
- Contextual suggestions (time of day, location, device type influence ranking).
- Zero-prefix suggestions (show trending/recent before user types anything).

#### Below the Line (Out of Scope)
- Full search results (just suggestions/completions, not search results pages).
- Ad insertion into suggestions.
- Voice/image-based autocomplete.
- Building the actual search engine behind the suggestions.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Latency (p99)** | < 100ms end-to-end | Users expect instant feedback as they type each character |
| **Throughput** | 100K+ QPS (peak 200K) | Billions of queries/day; each keystroke is a request |
| **Availability** | 99.99% | Autocomplete is on the critical path for search |
| **Freshness** | Trending queries visible within 1-5 minutes | Breaking news, viral events must surface quickly |
| **Data Size** | 100M+ unique suggestion candidates | Large vocabulary of queries/entities |
| **Suggestions per request** | Top 5-10 | Standard UX for dropdown suggestions |
| **Consistency** | Eventual (seconds-level) | Stale suggestions for a few seconds are acceptable |
| **Scalability** | Global deployment, 50+ regions | Low latency worldwide requires edge presence |

### Capacity Estimation

```
Query Volume:
  Daily search queries: 5 billion
  Average keystrokes per query: 8 characters
  Autocomplete requests per query: ~5 (not every keystroke; debounced)
  Total autocomplete QPS: 5B × 5 / 86400 = ~290K QPS average
  Peak (2x): ~580K QPS
  
  With debouncing (200ms) + client-side caching: effective server QPS ~100K

Suggestion Corpus:
  Unique query strings: ~500M (historical)
  Active suggestions (scored > threshold): ~100M
  Average suggestion length: 25 characters (25 bytes UTF-8)
  Metadata per suggestion: ~100 bytes (score, category, language, timestamps)
  Total corpus size: 100M × 125 bytes = ~12.5 GB (fits in memory!)

Trie Size (in-memory):
  100M strings, avg 25 chars, with shared prefixes
  Trie node: ~50 bytes (char, children pointers, top-K list)
  Unique trie nodes: ~2 billion (after prefix sharing)
  Estimated trie memory: ~100 GB per replica
  
  With prefix-to-topK cache: much smaller (~20 GB)

Network:
  Request size: ~50 bytes (prefix + metadata)
  Response size: ~500 bytes (10 suggestions × 50 bytes each)
  Bandwidth: 100K QPS × 550 bytes = ~55 MB/sec per cluster

Storage:
  Raw query logs: 5B/day × 50 bytes = 250 GB/day
  Aggregated frequency data: ~10 GB (updated hourly)
  Suggestion index: ~20 GB per replica
  Total: ~30 GB per serving node (RAM)
```

---

## 2️⃣ Core Entities

### Entity 1: Suggestion Candidate
```java
public class SuggestionCandidate {
    private final String text;                  // "facebook login"
    private final String normalizedText;        // "facebook login" (lowercased, trimmed)
    private final String language;              // "en"
    private final SuggestionType type;          // QUERY, ENTITY, PRODUCT, LOCATION
    private final long baseFrequency;           // Historical search count (all time)
    private final double trendingScore;         // Recent velocity (searches in last hour)
    private final double compositeScore;        // Weighted final ranking score
    private final Instant lastSearched;         // When this was last searched
    private final Instant firstSeen;            // When this first appeared
    private final String category;              // "Technology", "Sports", etc.
    private final Map<String, String> metadata; // Entity-specific: {"entity_type": "company", "image_url": "..."}
    private final boolean filtered;             // Blocked by content policy
    private final Set<String> blockedRegions;   // GDPR/legal restrictions by country
}

public enum SuggestionType {
    QUERY,      // Past search query: "how to make pasta"
    ENTITY,     // Known entity: "Barack Obama" (from knowledge graph)
    PRODUCT,    // Product: "iPhone 16 Pro" (e-commerce)
    LOCATION    // Place: "Central Park, New York"
}
```

### Entity 2: Autocomplete Request
```java
public class AutocompleteRequest {
    private final String prefix;               // What user has typed so far: "faceb"
    private final String userId;               // For personalization (optional)
    private final String language;             // "en", "ja", "ar"
    private final String country;              // For geo-filtering and local results
    private final double latitude;             // For location-based suggestions
    private final double longitude;
    private final String deviceType;           // "mobile", "desktop"
    private final int limit;                   // Number of suggestions requested (default 10)
    private final String sessionId;            // To track query refinement within a session
}
```

### Entity 3: Autocomplete Response
```java
public class AutocompleteResponse {
    private final String prefix;               // Echo back the prefix
    private final List<Suggestion> suggestions;
    private final long latencyMs;              // Server-side processing time
    private final String requestId;            // For debugging/logging
}

public class Suggestion {
    private final String text;                 // "facebook"
    private final String highlightedText;      // "<b>faceb</b>ook" (bold the typed prefix)
    private final SuggestionType type;         // QUERY, ENTITY, PRODUCT
    private final String category;             // "Social Media"
    private final String imageUrl;             // Thumbnail for entities (optional)
    private final double score;                // Ranking score (for debugging, not shown to user)
}
```

### Entity 4: Query Frequency Event
```java
public class QueryFrequencyEvent {
    private final String query;                // "facebook login"
    private final String normalizedQuery;      // Lowercased, trimmed
    private final String userId;               // Who searched
    private final String country;              // Where
    private final String language;
    private final Instant timestamp;
    private final String source;               // "web_search", "app_search", "voice"
}
```

---

## 3️⃣ API Design

### 1. Get Autocomplete Suggestions
```
GET /api/v1/autocomplete?q=faceb&lang=en&country=US&limit=10

Response (200 OK):
{
  "prefix": "faceb",
  "suggestions": [
    {
      "text": "facebook",
      "highlighted": "<b>faceb</b>ook",
      "type": "ENTITY",
      "category": "Social Media",
      "image_url": "https://cdn.example.com/icons/facebook.png"
    },
    {
      "text": "facebook login",
      "highlighted": "<b>faceb</b>ook login",
      "type": "QUERY"
    },
    {
      "text": "facebook marketplace",
      "highlighted": "<b>faceb</b>ook marketplace",
      "type": "QUERY"
    },
    {
      "text": "facebook dating",
      "highlighted": "<b>faceb</b>ook dating",
      "type": "QUERY"
    },
    {
      "text": "facebook lite",
      "highlighted": "<b>faceb</b>ook lite",
      "type": "QUERY"
    }
  ],
  "latency_ms": 12,
  "request_id": "req_abc123"
}
```

> **Design decisions**:
> - GET (not POST): cacheable at CDN/proxy, idempotent
> - Short response: ~500 bytes, optimized for mobile networks
> - Highlighted text: saves client-side computation
> - Image URL only for entities (not for plain queries)

### 2. Report Query (for frequency updates)
```
POST /api/v1/queries/report

Request:
{
  "query": "facebook login",
  "user_id": "usr_abc",
  "country": "US",
  "language": "en",
  "source": "web_search"
}

Response (202 Accepted):
{
  "status": "ACCEPTED"
}
```

> Asynchronous — fires and forgets into Kafka for batch processing.

### 3. Admin: Block/Unblock Suggestion
```
POST /api/v1/admin/suggestions/filter

Request:
{
  "text": "offensive_query_here",
  "action": "BLOCK",
  "reason": "NSFW content",
  "regions": ["*"],
  "expires_at": null
}

Response (200 OK):
{
  "status": "BLOCKED",
  "effective_in_seconds": 60
}
```

---

## 4️⃣ Data Flow

### Flow 1: Serving a Suggestion Request (Hot Path — < 100ms)
```
1. User types "faceb" in search box
   ↓
2. Client debounces (200ms), sends GET /autocomplete?q=faceb
   ↓
3. CDN / Edge cache check:
   - Cache key: "autocomplete:en:US:faceb"
   - Hit → return cached response (< 10ms) [~60% hit rate]
   - Miss → forward to nearest autocomplete server
   ↓
4. Autocomplete Server (in-memory):
   a. Normalize prefix: lowercase, trim, transliterate
   b. Look up in Trie / Prefix HashMap:
      "faceb" → top-K candidates with pre-computed scores
   c. Apply filters: remove blocked content, geo-filter
   d. Apply personalization boost (optional): 
      re-rank based on user's history
   e. Build response with highlighted text
   ↓
5. Return top 10 suggestions (< 50ms server-side)
   ↓
6. Cache response at CDN edge (TTL: 5-60 minutes)
```

### Flow 2: Updating Suggestion Scores (Warm Path — minutes)
```
1. User executes a search for "facebook login"
   ↓
2. Query event published to Kafka topic: "query-events"
   ↓
3. Stream Processor (Flink / Spark Streaming):
   a. Aggregate query frequencies per time window (1-min, 1-hour, 1-day)
   b. Compute trending score: 
      velocity = (searches_last_hour) / (searches_avg_hour)
   c. Compute composite score:
      score = w1 * log(base_frequency) + w2 * trending_score + w3 * recency
   d. Update suggestion candidate scores
   ↓
4. Score updates published to Kafka topic: "suggestion-updates"
   ↓
5. Autocomplete servers consume updates:
   a. Update in-memory trie/index with new scores
   b. Re-sort top-K lists for affected prefixes
   c. Invalidate CDN cache for affected prefixes
   ↓
6. Next request for "faceb" returns updated rankings
   Latency from search → updated suggestion: 1-5 minutes
```

### Flow 3: Full Index Rebuild (Cold Path — hourly/daily)
```
1. Batch job (hourly): aggregate all query frequencies from data warehouse
   ↓
2. Compute final scores for all 100M suggestion candidates
   ↓
3. Build new trie / prefix index snapshot
   ↓
4. Upload snapshot to S3 / distributed storage
   ↓
5. Autocomplete servers download new snapshot:
   a. Load into memory alongside current index
   b. Atomic swap: old index → new index
   c. Zero-downtime index refresh
   ↓
6. Full corpus refresh complete
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT (Browser / App)                            │
│                                                                              │
│  User types: "f" → "fa" → "fac" → "face" → "faceb"                        │
│  Debounce: 200ms → sends request for "faceb"                               │
│  Client cache: check local cache first (LRU, 100 entries)                   │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                        CDN / EDGE CACHE                                     │
│                        (CloudFront / Akamai)                                │
│                                                                              │
│  Cache key: "autocomplete:{lang}:{country}:{prefix}"                        │
│  TTL: 5 min (popular prefixes), 60 min (long-tail)                         │
│  Hit rate: ~60% (popular prefixes like "face", "amaz")                     │
│  Reduces server QPS from 100K → ~40K                                       │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │ Cache miss
                                 ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      LOAD BALANCER (L7)                                     │
│                      Route to nearest region                                │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    AUTOCOMPLETE SERVING CLUSTER                             │
│                    (per region, 10-50 nodes)                                │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────┐              │
│  │              IN-MEMORY SUGGESTION INDEX                    │              │
│  │                                                            │              │
│  │  Option A: Trie with top-K per node                       │              │
│  │    "f" → [facebook, flights, food near me, ...]           │              │
│  │    "fa" → [facebook, facebook login, fantasy, ...]        │              │
│  │    "fac" → [facebook, facebook login, face swap, ...]     │              │
│  │                                                            │              │
│  │  Option B: Prefix HashMap (prefix → top-K)               │              │
│  │    Pre-compute top-K for all prefixes of all candidates   │              │
│  │    O(1) lookup per request                                │              │
│  │                                                            │              │
│  │  Memory: ~20 GB per node                                  │              │
│  └──────────────────────────────────────────────────────────┘              │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────────┐      │
│  │ Filter Engine     │  │ Personalization   │  │ Ranking Service     │      │
│  │                   │  │ (optional)        │  │                     │      │
│  │ • Blocklist check │  │ • User history    │  │ • Score blending    │      │
│  │ • Geo filtering   │  │ • Recency boost   │  │ • Diversity rules   │      │
│  │ • NSFW filter     │  │ • Collaborative   │  │ • Freshness boost   │      │
│  └──────────────────┘  └──────────────────┘  └─────────────────────┘      │
└────────────────────────────────────────────────────────────────────────────┘

               ▲ Trie updates (near real-time)          ▲ Snapshot loads (hourly)
               │                                         │
┌──────────────┴──────────────────────────┐  ┌──────────┴──────────────────┐
│       STREAM PROCESSOR (Flink)          │  │    BATCH INDEX BUILDER       │
│                                          │  │    (Spark, hourly)           │
│  • Aggregate query frequencies           │  │                              │
│  • Compute trending scores               │  │  • Full corpus scoring       │
│  • Detect new trending queries           │  │  • Build trie snapshot       │
│  • Publish score updates to Kafka        │  │  • Upload to S3              │
└──────────────┬──────────────────────────┘  └──────────────────────────────┘
               │
               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                         KAFKA CLUSTER                                       │
│                                                                              │
│  Topic: "query-events"         (raw search events, 5B/day)                  │
│  Topic: "suggestion-updates"   (score changes, pushed to servers)           │
│  Topic: "filter-updates"       (blocklist changes, pushed to servers)       │
└──────────────┬──────────────────────────────────────────────────────────────┘
               │
               ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    DATA WAREHOUSE / ANALYTICS                               │
│                    (BigQuery / Redshift)                                     │
│                                                                              │
│  • Historical query frequency tables                                        │
│  • User search history (for personalization)                                │
│  • A/B test results for ranking algorithm                                   │
│  • Suggestion click-through rate analytics                                  │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **CDN Edge Cache** | Cache popular prefix responses | CloudFront / Akamai | Global, 200+ PoPs |
| **Autocomplete Servers** | In-memory prefix lookup, filter, rank | Java/Go on K8s | 10-50 per region, horizontal |
| **Suggestion Index** | Trie or prefix HashMap in memory | Custom data structure | ~20 GB per node |
| **Filter Engine** | Blocklist check, NSFW, geo-filter | In-memory bloom filter + set | Co-located with server |
| **Stream Processor** | Real-time frequency aggregation, trending | Apache Flink | 10-20 nodes |
| **Batch Index Builder** | Full corpus reindex, score computation | Apache Spark | Hourly job |
| **Kafka** | Event streaming for queries and updates | Apache Kafka | 3 topics, 50+ partitions |
| **Data Warehouse** | Historical analytics, ML features | BigQuery / Redshift | Managed |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Trie vs Prefix HashMap — Choosing the Right Data Structure

**The Problem**: We need to find top-K suggestions for any prefix in < 10ms (server-side). Two main approaches: a trie (tree of characters) or a pre-computed prefix-to-results HashMap.

```java
/**
 * APPROACH A: Trie with aggregated top-K at each node
 * 
 * Each node stores the top-K suggestions for its prefix.
 * Lookup is O(L) where L = prefix length. 
 * Very fast reads, but updates require propagating scores up the trie.
 */
public class TrieNode {
    private final char character;
    private final Map<Character, TrieNode> children;  // Child nodes
    private final List<ScoredSuggestion> topK;        // Pre-computed top-K for this prefix
    private boolean isEndOfWord;                       // This prefix is itself a valid suggestion
    
    // For "faceb", traverse: root → 'f' → 'a' → 'c' → 'e' → 'b'
    // topK at 'b' node = [("facebook", 9.5), ("facebook login", 8.2), ...]
}

public class TrieIndex {
    private final TrieNode root = new TrieNode('\0');
    private static final int K = 25; // Store top 25 per node (serve top 10 after filtering)
    
    /**
     * Lookup: O(prefix_length) time, O(1) for top-K retrieval.
     * No need to traverse entire subtree at query time.
     */
    public List<ScoredSuggestion> lookup(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toLowerCase().toCharArray()) {
            node = node.getChildren().get(c);
            if (node == null) return Collections.emptyList(); // No suggestions for this prefix
        }
        return node.getTopK(); // Pre-computed, O(1) retrieval
    }
    
    /**
     * Insert/Update: O(L * K * log(K)) where L = suggestion length.
     * Must update top-K at every ancestor node.
     */
    public void upsert(String suggestion, double score) {
        TrieNode node = root;
        for (char c : suggestion.toLowerCase().toCharArray()) {
            node = node.getChildren().computeIfAbsent(c, k -> new TrieNode(k));
            // Update top-K at this node
            node.updateTopK(suggestion, score, K);
        }
        node.setEndOfWord(true);
    }
}

/**
 * APPROACH B: Prefix HashMap (pre-computed, immutable)
 * 
 * For every suggestion, pre-compute all its prefixes.
 * Store prefix → top-K results in a HashMap.
 * O(1) lookup. Rebuilt periodically (hourly).
 */
public class PrefixHashMapIndex {
    // "facebook" generates prefixes: "f", "fa", "fac", "face", "faceb", "facebo", "faceboo", "facebook"
    // Each prefix maps to its global top-K suggestions
    private final Map<String, List<ScoredSuggestion>> prefixMap;
    
    /**
     * Build: iterate all suggestions, for each generate all prefixes,
     * maintain top-K per prefix using min-heap.
     */
    public static PrefixHashMapIndex build(List<SuggestionCandidate> candidates) {
        Map<String, PriorityQueue<ScoredSuggestion>> builder = new HashMap<>();
        int K = 25;
        
        for (SuggestionCandidate candidate : candidates) {
            String text = candidate.getNormalizedText();
            double score = candidate.getCompositeScore();
            
            // Generate all prefixes
            StringBuilder prefix = new StringBuilder();
            for (char c : text.toCharArray()) {
                prefix.append(c);
                String p = prefix.toString();
                
                PriorityQueue<ScoredSuggestion> heap = builder
                    .computeIfAbsent(p, k -> new PriorityQueue<>(
                        Comparator.comparingDouble(ScoredSuggestion::getScore)));
                
                heap.offer(new ScoredSuggestion(text, score));
                if (heap.size() > K) heap.poll(); // Keep only top K
            }
        }
        
        // Convert heaps to sorted lists
        Map<String, List<ScoredSuggestion>> prefixMap = new HashMap<>();
        for (var entry : builder.entrySet()) {
            List<ScoredSuggestion> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparingDouble(ScoredSuggestion::getScore).reversed());
            prefixMap.put(entry.getKey(), Collections.unmodifiableList(sorted));
        }
        
        return new PrefixHashMapIndex(prefixMap);
    }
    
    /** O(1) lookup */
    public List<ScoredSuggestion> lookup(String prefix) {
        return prefixMap.getOrDefault(prefix.toLowerCase(), Collections.emptyList());
    }
}
```

**Comparison**:
```
┌─────────────────────┬────────────────────────────┬──────────────────────────┐
│ Aspect              │ Trie + Top-K               │ Prefix HashMap           │
├─────────────────────┼────────────────────────────┼──────────────────────────┤
│ Lookup time         │ O(L) — traverse L chars    │ O(1) — direct HashMap    │
│ Memory              │ ~100 GB (node overhead)    │ ~20 GB (compact)         │
│ Update (single)     │ O(L × K log K) — fast      │ Requires full rebuild    │
│ Real-time updates   │ ✅ Yes (in-place)          │ ❌ Batch only            │
│ Build time          │ O(N × L) — incremental     │ O(N × L) — batch         │
│ Implementation      │ Complex (node management)  │ Simple (HashMap)         │
│ Cache-friendliness  │ Poor (pointer-chasing)     │ Good (flat HashMap)      │
│ CJK support         │ Harder (character-level)   │ Same as Latin            │
└─────────────────────┴────────────────────────────┴──────────────────────────┘

Our choice: HYBRID
  - Prefix HashMap as the primary serving index (rebuilt hourly)
  - Small Trie overlay for real-time trending updates (merged on read)
  - HashMap handles 99% of requests; Trie overlay handles fresh trending
```

---

### Deep Dive 2: Ranking Algorithm — Scoring Suggestions

**The Problem**: "facebook" should rank above "facetime" for prefix "face", but during an Apple event, "facetime" might temporarily rank higher. We need a scoring model that balances historical popularity, recency, and trending velocity.

```java
public class SuggestionScorer {

    /**
     * Composite score = w1 * popularity + w2 * trending + w3 * recency + w4 * quality
     * 
     * popularity: log-scaled historical search count (dampens extremely popular queries)
     * trending:   velocity ratio (recent_rate / baseline_rate)
     * recency:    exponential decay based on last-searched time
     * quality:    entity quality signals (is it a known entity? has image? etc.)
     */
    
    private static final double W_POPULARITY = 0.50;
    private static final double W_TRENDING   = 0.25;
    private static final double W_RECENCY    = 0.15;
    private static final double W_QUALITY    = 0.10;
    
    public double computeScore(SuggestionCandidate candidate) {
        double popularity = computePopularity(candidate);
        double trending = computeTrending(candidate);
        double recency = computeRecency(candidate);
        double quality = computeQuality(candidate);
        
        return W_POPULARITY * popularity
             + W_TRENDING * trending
             + W_RECENCY * recency
             + W_QUALITY * quality;
    }
    
    /**
     * Popularity: log-scaled historical frequency.
     * log() dampens the difference between "facebook" (10B searches) 
     * and "facebook dating" (10M searches) to a manageable range.
     */
    private double computePopularity(SuggestionCandidate c) {
        // Normalize to [0, 1] using log scale
        double maxLogFreq = Math.log1p(10_000_000_000L); // Max expected frequency
        return Math.log1p(c.getBaseFrequency()) / maxLogFreq;
    }
    
    /**
     * Trending: how much faster is this query growing vs its baseline?
     * 
     * velocity = searches_last_1h / avg_searches_per_hour (last 7 days)
     * 
     * Velocity > 1.0 → growing (trending up)
     * Velocity < 1.0 → declining
     * Velocity > 5.0 → strongly trending (breaking news)
     * 
     * Cap at 10.0 to prevent single viral query from dominating.
     */
    private double computeTrending(SuggestionCandidate c) {
        if (c.getBaseFrequency() < 100) return 0; // Ignore noise for rare queries
        
        double velocity = c.getTrendingScore(); // Pre-computed by Flink
        double capped = Math.min(velocity, 10.0);
        return capped / 10.0; // Normalize to [0, 1]
    }
    
    /**
     * Recency: exponential decay from last search time.
     * Queries searched recently get a boost.
     * Half-life: 24 hours (score halves every day of inactivity).
     */
    private double computeRecency(SuggestionCandidate c) {
        if (c.getLastSearched() == null) return 0;
        
        double hoursSinceLastSearch = Duration.between(c.getLastSearched(), Instant.now()).toHours();
        double halfLifeHours = 24.0;
        return Math.exp(-0.693 * hoursSinceLastSearch / halfLifeHours); // 0.693 = ln(2)
    }
    
    /**
     * Quality: bonus for rich entities (known brands, people, places).
     * These provide better UX (show images, categories).
     */
    private double computeQuality(SuggestionCandidate c) {
        double score = 0.0;
        if (c.getType() == SuggestionType.ENTITY) score += 0.5;  // Known entity
        if (c.getMetadata().containsKey("image_url")) score += 0.2; // Has image
        if (c.getCategory() != null) score += 0.2;                 // Categorized
        if (c.getText().split("\\s+").length <= 3) score += 0.1;  // Short, specific
        return Math.min(score, 1.0);
    }
}
```

**Scoring examples for prefix "face"**:
```
┌─────────────────────┬───────────┬──────────┬─────────┬─────────┬─────────┐
│ Suggestion          │ Popularity│ Trending │ Recency │ Quality │ TOTAL   │
├─────────────────────┼───────────┼──────────┼─────────┼─────────┼─────────┤
│ facebook            │ 0.95      │ 0.10     │ 0.99    │ 0.70    │ 0.696   │
│ facebook login      │ 0.80      │ 0.08     │ 0.95    │ 0.00    │ 0.563   │
│ facetime            │ 0.70      │ 0.80     │ 0.90    │ 0.70    │ 0.655   │ ← Apple event!
│ face swap app       │ 0.40      │ 0.05     │ 0.70    │ 0.00    │ 0.318   │
│ facebook marketplace│ 0.65      │ 0.12     │ 0.88    │ 0.00    │ 0.487   │
└─────────────────────┴───────────┴──────────┴─────────┴─────────┴─────────┘

Normal day:    facebook > facebook login > facetime > marketplace > face swap
Apple event:   facebook > facetime ↑ > facebook login > marketplace > face swap
```

---

### Deep Dive 3: Real-Time Trending Detection with Flink

**The Problem**: When breaking news happens (e.g., "earthquake california"), we need to detect the trend within minutes and surface it in autocomplete suggestions, even if the query has never been searched before.

```java
public class TrendingDetector {

    /**
     * Detect trending queries using a sliding window approach:
     * 
     * 1. Count searches per query in 1-minute tumbling windows
     * 2. Compare current rate vs 7-day baseline average
     * 3. If velocity > threshold → mark as trending
     * 4. Push trending updates to autocomplete servers
     * 
     * For NEW queries (never seen before):
     * - If a query gets > 100 searches in 1 minute, it's trending
     * - No baseline needed; absolute threshold triggers detection
     */
    
    // Flink: 1-minute window aggregation
    public void detectTrending(DataStream<QueryFrequencyEvent> queryStream) {
        queryStream
            .keyBy(QueryFrequencyEvent::getNormalizedQuery)
            .window(TumblingEventTimeWindows.of(Time.minutes(1)))
            .aggregate(new CountAggregator())
            .filter(this::isTrending)
            .map(this::toSuggestionUpdate)
            .addSink(new KafkaSink<>("suggestion-updates"));
    }
    
    private boolean isTrending(QueryCount count) {
        String query = count.getQuery();
        long currentMinuteCount = count.getCount();
        
        // Get baseline from Redis (pre-computed hourly)
        Double baselinePerMinute = redis.get("baseline:" + query);
        
        if (baselinePerMinute == null || baselinePerMinute < 1.0) {
            // NEW query: use absolute threshold
            return currentMinuteCount >= 100; // 100 searches in 1 minute
        }
        
        // EXISTING query: use velocity ratio
        double velocity = currentMinuteCount / baselinePerMinute;
        return velocity >= 5.0; // 5x above normal rate
    }
    
    /**
     * Anti-spam: prevent manipulation of trending queries.
     * 
     * - Deduplicate by user_id (same user searching 100 times doesn't count)
     * - Minimum unique users threshold (at least 50 unique users)
     * - Geographic diversity (not all from same IP range)
     * - Rate limit per IP
     */
    private boolean passesAntiSpam(QueryCount count) {
        return count.getUniqueUsers() >= 50 
            && count.getUniqueCountries() >= 3;
    }
}
```

**Trending Pipeline Latency**:
```
T=0:00   Event happens (earthquake in California)
T=0:01   People start searching "earthquake california"
T=0:05   100+ searches in last minute → trending detected ✓
T=0:06   Score update published to Kafka
T=0:07   Autocomplete servers receive update
T=0:07   In-memory trie overlay updated
T=0:08   CDN cache for "earth", "earthq", etc. invalidated
T=1:00   User types "earth" → sees "earthquake california" in suggestions
─────────────────────────────────────────────────
Total: ~1 minute from event to suggestion
```

---

### Deep Dive 4: CDN Caching Strategy — Balancing Freshness and Latency

**The Problem**: CDN caching is critical for latency (< 10ms for cache hits) but conflicts with freshness (trending queries should appear within minutes). We need a smart caching strategy.

```java
public class CachingStrategy {

    /**
     * Multi-tier caching:
     * 
     * Tier 1: Client-side cache (browser/app) — LRU, 100 entries, 60s TTL
     * Tier 2: CDN edge cache — varies by prefix popularity
     * Tier 3: Server-side cache (L1) — in-process LRU, 10K entries
     * 
     * CDN TTL strategy (key insight):
     *   Short prefixes (1-2 chars): HIGH traffic, many users share → long TTL (5 min)
     *   Medium prefixes (3-5 chars): moderate traffic → medium TTL (2 min)
     *   Long prefixes (6+ chars): LOW traffic, personal → short TTL (30s) or no cache
     *   
     *   Why? Short prefixes are stable (top results don't change often).
     *   Long prefixes are more personal and less cacheable.
     */
    
    public Duration computeCDNTTL(String prefix, String country) {
        int prefixLength = prefix.length();
        
        if (prefixLength <= 2) {
            return Duration.ofMinutes(5);   // "fa" → very stable, high cache hit rate
        } else if (prefixLength <= 5) {
            return Duration.ofMinutes(2);   // "faceb" → moderately stable
        } else {
            return Duration.ofSeconds(30);  // "facebook lo" → more specific, less cacheable
        }
    }
    
    /**
     * Cache invalidation for trending queries:
     * 
     * When a new trending query is detected (e.g., "earthquake california"),
     * we need to invalidate CDN cache for ALL prefixes of that query:
     *   "e", "ea", "ear", "eart", "earth", "earthq", ..., "earthquake california"
     * 
     * But invalidating 25+ CDN keys per trending query is expensive.
     * Instead: use TTL-based expiration + versioned cache keys.
     * 
     * Approach: Include a version number in the cache key.
     * When trending changes, bump the version → all old cache entries expire naturally.
     */
    public String buildCacheKey(String prefix, String language, String country) {
        long trendingVersion = trendingVersionStore.getCurrentVersion(); // Bumped every minute
        return String.format("ac:v%d:%s:%s:%s", trendingVersion, language, country, prefix);
    }
}
```

**Cache Hit Rate Analysis**:
```
Prefix length   Example        Unique prefixes   QPS per prefix   Cache hit rate
─────────────   ────────       ───────────────   ──────────────   ──────────────
1 char          "f"            26                3,800            ~95%
2 chars         "fa"           676               145              ~90%
3 chars         "fac"          17,576            5.7              ~75%
4 chars         "face"         456,976           0.22             ~50%
5 chars         "faceb"        11.8M             0.008            ~20%
6+ chars        "facebo..."    Very long tail    < 0.001          ~5%

Overall weighted cache hit rate: ~60%
Effective server QPS: 100K × 0.4 = 40K QPS (after CDN absorbs 60%)
```

---

### Deep Dive 5: Content Filtering — Blocking Offensive Suggestions

**The Problem**: Autocomplete must never suggest offensive, NSFW, or legally problematic content. This requires real-time filtering with zero false negatives (never show offensive) and minimal false positives (don't over-block).

```java
public class ContentFilter {

    /**
     * Multi-layer filtering pipeline:
     * 
     * Layer 1: Exact match blocklist (O(1) HashSet lookup)
     *   - Manually curated list of ~100K blocked terms
     *   - Updated in real-time via Kafka "filter-updates" topic
     * 
     * Layer 2: Pattern matching (regex / substring)
     *   - Catch variations: "f*ck", "fu ck", "f.u.c.k"
     *   - Aho-Corasick for multi-pattern matching
     * 
     * Layer 3: ML classifier (for borderline cases)
     *   - Pre-classify all candidates offline (batch)
     *   - Not used at query time (too slow)
     * 
     * Layer 4: Geo-specific filtering
     *   - GDPR right-to-be-forgotten (EU)
     *   - Country-specific legal restrictions
     */
    
    private final Set<String> exactBlocklist;        // ~100K terms
    private final AhoCorasick patternMatcher;         // ~10K patterns
    private final Map<String, Set<String>> geoBlocks; // country → blocked terms
    
    public List<ScoredSuggestion> filter(List<ScoredSuggestion> candidates,
                                          String country, String language) {
        return candidates.stream()
            .filter(c -> !exactBlocklist.contains(c.getText().toLowerCase()))
            .filter(c -> !patternMatcher.containsMatch(c.getText()))
            .filter(c -> !isGeoBlocked(c.getText(), country))
            .toList();
    }
    
    /**
     * Emergency block: when a new offensive suggestion is reported,
     * block it globally within 60 seconds.
     */
    public void emergencyBlock(String term) {
        // 1. Add to local blocklist immediately
        exactBlocklist.add(term.toLowerCase());
        
        // 2. Publish to Kafka for all servers
        kafka.send("filter-updates", new FilterUpdate(term, "BLOCK", "*"));
        
        // 3. Invalidate CDN cache for all prefixes of this term
        for (int i = 1; i <= term.length(); i++) {
            cdn.invalidate("ac:*:" + term.substring(0, i));
        }
        
        log.info("Emergency block applied for '{}' — effective in < 60s globally", term);
    }
}
```

---

### Deep Dive 6: Personalization — User-Specific Suggestions

**The Problem**: User A (a developer) types "py" and expects "python documentation". User B (a gamer) types "py" and expects "pyke league of legends". Personalized re-ranking provides more relevant suggestions.

```java
public class PersonalizationService {

    /**
     * Lightweight personalization applied AFTER base ranking:
     * 
     * 1. Fetch user's recent search history (last 100 queries, from Redis)
     * 2. Boost candidates that match user's historical patterns
     * 3. Must add < 5ms to total latency (personalization is optional)
     * 
     * NOT: building separate per-user tries (too expensive for 1B+ users)
     */
    
    private static final double PERSONAL_BOOST = 1.5; // 50% score boost for personal matches
    private static final int MAX_HISTORY = 100;
    
    public List<ScoredSuggestion> personalize(List<ScoredSuggestion> baseSuggestions,
                                               String userId, String prefix) {
        if (userId == null) return baseSuggestions; // Anonymous user, no personalization
        
        // Fetch user history from Redis (< 2ms)
        List<String> userHistory = redis.lrange("user_history:" + userId, 0, MAX_HISTORY);
        if (userHistory.isEmpty()) return baseSuggestions;
        
        // Build a set of user's query stems for fast matching
        Set<String> userStems = userHistory.stream()
            .map(q -> q.toLowerCase().split("\\s+")[0]) // First word as stem
            .collect(Collectors.toSet());
        
        // Boost matching suggestions
        return baseSuggestions.stream()
            .map(s -> {
                boolean matchesHistory = userHistory.stream()
                    .anyMatch(h -> h.toLowerCase().startsWith(prefix.toLowerCase()));
                boolean matchesStem = userStems.stream()
                    .anyMatch(stem -> s.getText().toLowerCase().contains(stem));
                
                if (matchesHistory) {
                    return new ScoredSuggestion(s.getText(), s.getScore() * PERSONAL_BOOST * 1.2);
                } else if (matchesStem) {
                    return new ScoredSuggestion(s.getText(), s.getScore() * PERSONAL_BOOST);
                }
                return s;
            })
            .sorted(Comparator.comparingDouble(ScoredSuggestion::getScore).reversed())
            .limit(10)
            .toList();
    }
}
```

**Personalization adds < 3ms**:
```
Step 1: Redis LRANGE user_history (< 2ms, same region)
Step 2: Set matching + score adjustment (< 0.5ms, in-memory)
Step 3: Re-sort top 10 (< 0.1ms)
Total: < 3ms added to serving path
```

---

### Deep Dive 7: Handling CJK Languages (Chinese, Japanese, Korean)

**The Problem**: CJK languages don't use spaces between words. Typing "东京" (Tokyo) should suggest "东京奥运会" (Tokyo Olympics) and "东京天气" (Tokyo weather). Character-level prefix matching works differently than Latin languages.

```java
public class CJKHandler {

    /**
     * CJK challenges:
     * 1. No word boundaries — "东京天气" is 4 characters, not 2 words
     * 2. Pinyin input (Chinese): user types "dongjing" → expects "东京"
     * 3. Romaji input (Japanese): user types "tokyo" → expects "東京"
     * 4. Mixed scripts: "iPhone 15 ケース" (Japanese + English + Katakana)
     * 
     * Strategy:
     * a. Character-level trie works natively for CJK (each char = node)
     * b. Maintain parallel Pinyin/Romaji index for phonetic input
     * c. Store suggestions in both original script AND transliterated form
     */
    
    public List<ScoredSuggestion> lookupCJK(String prefix, String language) {
        List<ScoredSuggestion> results = new ArrayList<>();
        
        // Direct character match (user typing in native script)
        results.addAll(nativeIndex.lookup(prefix));
        
        if ("zh".equals(language)) {
            // Pinyin match: "dong" → 东, 冬, 动, ...
            List<String> possibleChars = pinyinToCharacters(prefix);
            for (String candidate : possibleChars) {
                results.addAll(nativeIndex.lookup(candidate));
            }
        } else if ("ja".equals(language)) {
            // Romaji → Hiragana/Katakana conversion
            String hiragana = romajiToHiragana(prefix);  // "tokyo" → "とうきょう"
            String katakana = romajiToKatakana(prefix);
            results.addAll(nativeIndex.lookup(hiragana));
            results.addAll(nativeIndex.lookup(katakana));
        }
        
        // Deduplicate and re-rank
        return deduplicateAndRank(results);
    }
}
```

---

### Deep Dive 8: Index Deployment — Zero-Downtime Snapshot Swaps

**The Problem**: Every hour, we rebuild the full suggestion index (~20 GB). Deploying this to 50+ servers per region without downtime or inconsistency.

```java
public class IndexDeploymentManager {

    /**
     * Blue-Green index deployment:
     * 
     * Each server maintains TWO index slots:
     *   - Active index (serving traffic)
     *   - Standby index (being loaded)
     * 
     * Deployment steps:
     *   1. Batch job builds new index → uploads to S3
     *   2. Coordinator notifies all servers: "new index v42 available"
     *   3. Each server downloads v42 into standby slot (background, 2-5 min)
     *   4. Each server validates new index (spot-check queries)
     *   5. Atomic swap: standby → active (single pointer swap)
     *   6. Old index memory freed after swap
     * 
     * Result: zero downtime, zero inconsistency during swap.
     */
    
    private volatile SuggestionIndex activeIndex;
    private volatile SuggestionIndex standbyIndex;
    
    public void deployNewIndex(String s3Path) {
        log.info("Downloading new index from {}", s3Path);
        
        // Step 1: Download and load into standby slot
        standbyIndex = SuggestionIndex.loadFrom(s3Path); // ~3 minutes for 20 GB
        
        // Step 2: Validate
        boolean valid = validateIndex(standbyIndex);
        if (!valid) {
            log.error("New index validation failed! Aborting deployment.");
            standbyIndex = null;
            return;
        }
        
        // Step 3: Atomic swap
        SuggestionIndex old = activeIndex;
        activeIndex = standbyIndex;  // Single reference swap — atomic!
        standbyIndex = null;
        
        // Step 4: GC old index (will be collected when no requests reference it)
        log.info("Index swap complete. New index serving traffic.");
    }
    
    // All serving requests read from activeIndex (lock-free)
    public List<ScoredSuggestion> lookup(String prefix) {
        return activeIndex.lookup(prefix);
    }
}
```

---

### Deep Dive 9: Client-Side Optimizations — Debouncing & Prefetching

**The Problem**: Without client-side optimization, every keystroke sends a request (8 requests for "facebook"). This wastes bandwidth and server resources.

```javascript
/**
 * Client-side autocomplete optimization:
 * 
 * 1. Debounce: wait 200ms after last keystroke before sending request
 * 2. Client cache: LRU cache of last 100 prefix→suggestions
 * 3. Prefetch: when showing results for "face", preemptively fetch "faceb"
 * 4. Cancel in-flight: if user types faster, cancel previous request
 * 5. Min prefix length: don't query for 1-char prefixes (too broad)
 */

class AutocompleteClient {
    constructor() {
        this.cache = new LRUCache(100);
        this.debounceTimer = null;
        this.abortController = null;
        this.MIN_PREFIX_LENGTH = 2;
        this.DEBOUNCE_MS = 200;
    }
    
    onKeyStroke(prefix) {
        // Don't query for very short prefixes
        if (prefix.length < this.MIN_PREFIX_LENGTH) {
            this.hideDropdown();
            return;
        }
        
        // Check client cache first
        const cached = this.cache.get(prefix);
        if (cached) {
            this.showSuggestions(cached);
            return; // No server request needed!
        }
        
        // Debounce: reset timer on each keystroke
        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => this.fetchSuggestions(prefix), this.DEBOUNCE_MS);
    }
    
    async fetchSuggestions(prefix) {
        // Cancel previous in-flight request
        if (this.abortController) this.abortController.abort();
        this.abortController = new AbortController();
        
        try {
            const response = await fetch(
                `/api/v1/autocomplete?q=${encodeURIComponent(prefix)}&limit=10`,
                { signal: this.abortController.signal }
            );
            const data = await response.json();
            
            // Cache the result
            this.cache.set(prefix, data.suggestions);
            this.showSuggestions(data.suggestions);
            
            // Prefetch: cache results for prefix + common next chars
            // If user typed "face", prefetch "faceb" (most common continuation)
            
        } catch (e) {
            if (e.name !== 'AbortError') console.error(e);
        }
    }
}
```

**Request reduction**:
```
User types "facebook" (8 keystrokes):
  Without optimization: 8 HTTP requests
  With 200ms debounce: 2-3 requests ("fa", "face", "facebook")
  With debounce + cache: 1-2 requests (if "face" was cached from previous session)
  
Effective reduction: 60-80% fewer server requests
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Data structure** | Hybrid: Prefix HashMap + Trie overlay | HashMap for O(1) read speed (99%); Trie overlay for real-time trending (1%) |
| **Scoring** | Composite: popularity + trending + recency + quality | Balances stable results with fresh trending content |
| **Caching** | CDN with variable TTL by prefix length | Short prefixes (stable, high hit rate) cached longer; long prefixes (personal) shorter |
| **Freshness** | Flink stream → Trie overlay (1-5 min) | Faster than full rebuild; trending visible within minutes |
| **Full rebuild** | Hourly batch (Spark → S3 → servers) | Ensures corpus-wide score consistency; captures long-tail changes |
| **Personalization** | Lightweight re-ranking (< 3ms) | User history from Redis; score boost, not separate per-user index |
| **Filtering** | Multi-layer: blocklist + pattern + geo | Zero false negatives for offensive content; < 1ms overhead |
| **Index deployment** | Blue-green swap (atomic pointer) | Zero downtime; validates before swap; rollback by keeping old index |
| **Client optimization** | Debounce 200ms + LRU cache + cancel in-flight | 60-80% request reduction; instant UX for cached prefixes |

## Interview Talking Points

1. **"Prefix HashMap gives O(1) lookups"** — Pre-compute all prefixes for 100M candidates. 20 GB in memory. Rebuilt hourly. Handles 99% of requests.
2. **"Trie overlay for real-time trending"** — Small trie (< 1 GB) holds only trending updates. Merged with HashMap on read. New trends visible in 1-5 minutes.
3. **"Composite scoring: log(frequency) + trending velocity + recency"** — log() dampens popularity dominance. Trending velocity detects breaking news. Recency ensures freshness.
4. **"CDN caches 60% of requests"** — Short prefixes ("fa") cached 5 min (stable, shared by many users). Long prefixes ("facebook lo") cached 30s (personal, unique).
5. **"Debouncing reduces server load 60-80%"** — 200ms debounce means only 2-3 server requests per query instead of 8. Client LRU cache provides instant response for repeated prefixes.
6. **"Emergency content blocking in < 60 seconds"** — Add to local blocklist → publish via Kafka → all servers updated → CDN keys invalidated.
7. **"Personalization adds < 3ms"** — Redis lookup for user's last 100 queries. Score boost for matching patterns. No per-user index needed.
8. **"Blue-green index swap: zero downtime"** — Load new 20 GB index in background. Validate. Atomic pointer swap. Old index GC'd when no requests reference it.
9. **"CJK handled via parallel Pinyin/Romaji index"** — Phonetic input ("dongjing") → Chinese characters ("东京") → suggestions. Character-level trie works natively.
10. **"Anti-spam: unique users + geo diversity"** — Trending detection requires 50+ unique users from 3+ countries. Prevents manipulation by bots.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Search Engine** | Autocomplete feeds into search | We return suggestions, not search results |
| **Trending Hashtags** | Same real-time trend detection | Hashtags are exact match; autocomplete is prefix match |
| **Recommendation System** | Similar ranking/personalization | Recommendations use collaborative filtering; autocomplete uses prefix + frequency |
| **Rate Limiter** | Handles same high QPS | Rate limiter enforces limits; autocomplete serves content |
| **CDN Design** | Same caching patterns | CDN is generic; our caching is prefix-length-aware |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Index** | Custom Prefix HashMap | Elasticsearch prefix queries | When you need full-text + prefix (hybrid search) |
| **Index** | Custom Trie | Redis Sorted Sets (ZRANGEBYLEX) | Smaller corpus (< 1M suggestions), simpler ops |
| **Streaming** | Apache Flink | Kafka Streams | Simpler deployment, smaller scale |
| **Batch** | Apache Spark | Hadoop MapReduce | Legacy infrastructure |
| **Cache** | CloudFront CDN | Varnish / Nginx proxy cache | Self-hosted, more control |
| **Personalization** | Redis user history | ML embedding model | When deep personalization (collaborative filtering) needed |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 9 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Google Autocomplete Architecture (Jeff Dean's systems talks)
- Trie Data Structure & Prefix Trees (Sedgewick, Algorithms)
- Aho-Corasick Multi-Pattern Matching Algorithm
- Debouncing & Throttling in Frontend Engineering
- Exponential Decay Scoring for Time-Sensitive Ranking
- CJK Input Method Architecture (Pinyin, Romaji)
