# Design YouTube Video Search — Hello Interview Framework

> **Question**: Design a video search system like YouTube Search that allows users to find relevant videos among billions using text queries, filters, and ranking. Support search over titles, descriptions, tags, auto-generated captions, and user engagement signals.
>
> **Asked at**: Google (YouTube), Meta, Netflix, TikTok, Amazon (Prime Video), Spotify
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
1. **Text Search**: Search across video titles, descriptions, tags, channel names, and auto-generated captions/transcripts. "How to make sourdough bread" should match a video with that phrase in its title, description, or spoken in the video.
2. **Relevance Ranking**: Multi-signal ranking combining text relevance, view count, likes/dislikes ratio, watch time, upload recency, channel authority, and user personalization.
3. **Filtering**: By upload date (last hour, today, this week, this month, this year), duration (short < 4min, medium 4-20min, long > 20min), quality (HD, 4K), type (video, playlist, channel), closed captions available, live/uploaded.
4. **Auto-Suggestions**: Type-ahead suggestions as user types, showing popular queries and matching video titles.
5. **Search Within Video**: Jump to the exact moment in a video where the query term is spoken (timestamp-level search via captions).
6. **Safe Search**: Filter explicit, age-restricted, and harmful content. Support strict/moderate/off modes.

#### Nice to Have (P1)
- Visual similarity search (find videos with similar thumbnails or content)
- Multi-language search (English query → Spanish video results with captions)
- Trending searches and explore/discover
- "Did you mean" spell correction
- Search by hashtag (#)
- Channel search and playlist search
- Voice search

#### Below the Line (Out of Scope)
- Video upload/encoding pipeline
- Recommendation feed (separate from search)
- Comment search
- Monetization / ad serving
- Live streaming infrastructure

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Search Latency** | P50 < 100ms, P95 < 300ms, P99 < 500ms | Users expect instant results |
| **Throughput** | 200K QPS (peak 400K during events) | YouTube: 3B+ searches/day estimated |
| **Index Size** | 1B+ videos | YouTube has 800M+ videos as of 2024 |
| **Freshness** | New video searchable < 5 minutes after publish | Creators expect fast discoverability |
| **Availability** | 99.99% | Search is core to YouTube |
| **Caption Search** | 90% of videos have auto-generated captions | ASR (speech-to-text) coverage |
| **Consistency** | Eventual (minutes) | Slight delay for new video appearance is OK |

### Capacity Estimation

```
Videos:
  Total: 1B videos
  New uploads: 500 hours of video per minute = 720K new videos/day
  Active (not removed/private): 800M
  Average metadata per video: 3KB (title, description, tags, channel info)
  Average caption transcript: 10KB per video (1 hour video ≈ 10K words)

Traffic:
  DAU: 2B users
  Searches/day: 3B
  QPS: 3B / 86400 ≈ 35K avg → 200K peak
  Auto-complete: 600K QPS (debounced)

Storage:
  Video metadata index: 1B × 3KB = 3 TB
  Caption index: 800M × 10KB = 8 TB (compressed: 2 TB)
  Combined search index: ~5 TB per replica
  Engagement signals: 1B × 200B = 200 GB (views, likes, watch time)
  Embeddings (for semantic search): 1B × 256 floats × 4B = 1 TB

Infrastructure:
  Search API servers: 80 (2,500 QPS each)
  Elasticsearch data nodes: 50 (100 GB per node × 50 = 5 TB per replica, 3 replicas)
  ML ranking servers: 40 (GPU-equipped for BERT inference)
  Kafka brokers: 20
  Redis cache: 30 nodes
  3 global regions
```

---

## 2️⃣ Core Entities

### Entity 1: Video (Search Document)
```java
public class VideoSearchDocument {
    String videoId;              // "dQw4w9WgXcQ"
    String title;                // "How to Make Sourdough Bread"
    String description;          // Full description text (truncated to 5000 chars for indexing)
    List<String> tags;           // ["sourdough", "bread", "baking", "homemade"]
    String channelId;            // "UC..." 
    String channelName;          // "Pro Home Cooks"
    int durationSeconds;         // 1245 (20:45)
    long viewCount;              // 4582341
    long likeCount;              // 89234
    float likeRatio;             // 0.97 (likes / (likes + dislikes))
    float avgWatchPercentage;    // 0.62 (avg % of video watched)
    String language;             // "en"
    boolean hasClosedCaptions;   // true
    String captionText;          // Full transcript (for indexing)
    List<CaptionSegment> captionTimestamps; // Word-level timestamps
    Resolution maxResolution;    // HD_1080P, UHD_4K
    boolean isLive;              // false
    boolean isAgeRestricted;     // false
    String thumbnailUrl;
    float channelAuthority;      // Subscriber-based authority score
    Instant publishedAt;
    Instant indexedAt;
    float[] embedding;           // 256-dim semantic embedding
}
```

### Entity 2: CaptionSegment
```java
public class CaptionSegment {
    String text;                 // "now fold the dough gently"
    float startSeconds;          // 342.5
    float endSeconds;            // 345.2
    float confidence;            // 0.94 (ASR confidence)
}
```

### Entity 3: SearchQuery
```java
public class SearchQuery {
    String queryText;            // "how to make sourdough bread"
    String userId;               // For personalization
    UploadDateFilter uploadDate; // LAST_HOUR, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR
    DurationFilter duration;     // SHORT, MEDIUM, LONG
    QualityFilter quality;       // HD, UHD_4K
    TypeFilter type;             // VIDEO, CHANNEL, PLAYLIST
    boolean closedCaptions;      // Only show videos with CC
    SafeSearchMode safeSearch;   // STRICT, MODERATE, OFF
    String sortBy;               // "relevance" | "upload_date" | "view_count" | "rating"
    String locale;               // "en-US"
    int limit;                   // 20
    String pageToken;
}
```

---

## 3️⃣ API Design

### 1. Video Search
```
GET /api/v1/search?q=how+to+make+sourdough+bread&type=video&duration=medium&hd=true&sort=relevance&limit=20

Headers:
  Authorization: Bearer {jwt}
  Accept-Language: en-US

Response (200 OK):
{
  "results": [
    {
      "video_id": "abc123",
      "title": "The EASIEST Sourdough Bread You'll Ever Make",
      "channel_name": "Pro Home Cooks",
      "channel_id": "UCxyz",
      "duration": "20:45",
      "view_count": 4582341,
      "like_ratio": 0.97,
      "published_at": "2025-11-15T14:30:00Z",
      "thumbnail_url": "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
      "description_snippet": "In this video, I'll show you the <b>easiest sourdough bread</b> recipe that requires no kneading...",
      "caption_matches": [
        {"text": "...and that's how you <b>make sourdough bread</b> from scratch...", "timestamp": 342.5}
      ],
      "badges": ["HD", "CC"]
    }
    // ... more results
  ],
  "total_results": 834291,
  "spell_suggestion": null,
  "related_searches": [
    "sourdough bread recipe no knead",
    "sourdough starter from scratch",
    "easy bread recipes"
  ],
  "next_page_token": "CDIQAA"
}
```

### 2. Search Within Video (Caption Search)
```
GET /api/v1/videos/abc123/search?q=fold+the+dough

Response (200 OK):
{
  "video_id": "abc123",
  "matches": [
    {
      "text": "now <b>fold the dough</b> gently over itself",
      "start_seconds": 342.5,
      "end_seconds": 345.2,
      "thumbnail_url": "https://i.ytimg.com/vi/abc123/frame_342.jpg"
    },
    {
      "text": "again, <b>fold the dough</b> like I showed you",
      "start_seconds": 567.1,
      "end_seconds": 569.8,
      "thumbnail_url": "https://i.ytimg.com/vi/abc123/frame_567.jpg"
    }
  ]
}
```

---

## 4️⃣ Data Flow

### Video Indexing Flow (Write Path)
```
Creator uploads video → Video Processing Pipeline (separate system)
  → ASR (Speech-to-Text) generates captions → Caption Service
  → Metadata Service provides title, description, tags
  → Engagement Service provides views, likes, watch time
  → Embedding Service generates semantic vector (BERT on title+description)
  
All signals merge → Kafka → Search Indexer
  → Elasticsearch (inverted index on text + captions)
  → Vector DB (FAISS/ScaNN for semantic embeddings)
  → Engagement Cache (Redis for real-time engagement signals)
```

### Search Flow (Read Path)
```
User types query → Auto-Complete Service (prefix match, top-10 suggestions)
User submits search → Load Balancer → Search API
  → Query Understanding (spell check, query expansion, intent detection)
  → Multi-Field Search (scatter to ES shards: title, description, tags, captions)
  → L1 Ranking (BM25 + engagement signals) → top-500 candidates
  → L2 Ranking (ML model: GBDT with 100+ features) → top-50
  → L3 (Optional: BERT reranker on top-20) → top-20
  → Result Assembly (thumbnails, snippets, caption matches)
  → Return to user
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────┐
│                     SEARCH SERVING PATH                       │
│                                                                │
│  User → CDN → LB → Search API                                │
│                       │                                        │
│             ┌─────────┴──────────┐                            │
│             ▼                    ▼                             │
│      Query Understanding    Auto-Complete                     │
│      (spell check,          (Trie + popularity)               │
│       expansion,                                               │
│       intent)                                                  │
│             │                                                  │
│      ┌──────┼───────────────┐                                 │
│      ▼      ▼               ▼                                 │
│   [Text Search]    [Vector Search]    [Caption Search]        │
│   (ES: title,      (FAISS: semantic   (ES: transcript         │
│    desc, tags)      embeddings)        with timestamps)       │
│      │              │                  │                       │
│      └──────────────┼──────────────────┘                      │
│                     ▼                                          │
│              ┌──────────────┐                                 │
│              │ L1 Ranker    │                                 │
│              │ (BM25 +      │                                 │
│              │  engagement)  │                                 │
│              └──────┬───────┘                                 │
│                     ▼                                          │
│              ┌──────────────┐                                 │
│              │ L2 Ranker    │                                 │
│              │ (ML model:   │                                 │
│              │  GBDT/NN)    │                                 │
│              └──────┬───────┘                                 │
│                     ▼                                          │
│              Result Assembly                                   │
│              (snippets, thumbnails,                             │
│               caption timestamps)                              │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    INDEXING PATH                               │
│                                                                │
│  Video Upload → Processing Pipeline                           │
│                     │                                          │
│        ┌────────────┼────────────┐                            │
│        ▼            ▼            ▼                             │
│   ASR Engine    Metadata     Embedding                        │
│   (captions)    Service      Service                          │
│        │            │            │                             │
│        └────────────┼────────────┘                            │
│                     ▼                                          │
│                   Kafka                                        │
│                     │                                          │
│              ┌──────┼──────┐                                  │
│              ▼      ▼      ▼                                  │
│             ES    FAISS   Redis                               │
│           (text)  (vector) (engagement)                        │
└──────────────────────────────────────────────────────────────┘
```

### Key Technology Choices

| Component | Technology | Why |
|-----------|-----------|-----|
| **Text Search** | Elasticsearch | Multi-field text search with facets |
| **Vector Search** | FAISS / ScaNN | Billion-scale ANN for semantic search |
| **Caption ASR** | Whisper (OpenAI) / Google ASR | Auto-generate captions at scale |
| **Embeddings** | Sentence-BERT / YouTube's own model | Convert text to semantic vectors |
| **L2 Ranker** | LambdaMART (GBDT) | Fast, interpretable ranking model |
| **Streaming** | Apache Kafka | Decouple indexing from upload pipeline |
| **Cache** | Redis | Cache popular search results, engagement data |
| **Engagement Store** | Bigtable / DynamoDB | High-throughput read/write for view counts |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Multi-Modal Search — Text + Captions + Semantic

**The Problem**: A query "how to fix a flat tire" might match a video titled "Roadside Emergency Tips" that never mentions "flat tire" in the title but says it extensively in the video. We need to search across multiple modalities.

**Multi-Field Search with Boosting**:
```
Elasticsearch multi_match query:
{
  "multi_match": {
    "query": "how to fix a flat tire",
    "fields": [
      "title^5",          // Title match is 5× more important
      "tags^3",           // Tags are 3× 
      "channel_name^2",   // Channel match (e.g., "Flat Tire Repair Co")
      "description^1.5",  // Description
      "caption_text^1"    // Caption/transcript (full speech)
    ],
    "type": "best_fields",
    "tie_breaker": 0.3
  }
}

Why different boost weights?
  - Title is the strongest signal (creator specifically chose these words)
  - Captions are noisy (ASR errors, filler words) → lower boost
  - Description can be keyword-stuffed → moderate boost
```

**Semantic Search (Vector) for Concept Matching**:
```
Problem: BM25 won't match "fix a flat tire" to a video about "changing a punctured wheel"
  — different words, same concept

Solution: Dual retrieval — lexical + semantic
  1. BM25 retrieval from ES: matches exact/similar words → candidates_A
  2. ANN retrieval from FAISS: query embedding → nearest video embeddings → candidates_B
  3. Merge: union(candidates_A, candidates_B), re-rank with L2 model

Embedding pipeline:
  Video embedding = BERT(title + first 500 chars of description + top tags)
  Query embedding = BERT(query_text)
  Similarity = cosine(query_embedding, video_embedding)

FAISS setup:
  1B vectors × 256 dims × 4 bytes = 1 TB
  Index type: IVF-PQ (inverted file + product quantization)
    - IVF: cluster vectors into 65536 Voronoi cells
    - PQ: compress each vector from 1024B to 64B (16× compression)
    - Compressed index: ~64 GB (fits in memory!)
  Query: search top-10 cells → compare ~100K vectors → return top-100
  Latency: < 10ms for top-100 ANN
```

**Interview Talking Points**:
- "Multi-field search with title boosted 5× over captions — title is the strongest relevance signal."
- "Dual retrieval: BM25 for lexical matches, FAISS ANN for semantic matches, then merge and re-rank."
- "FAISS with IVF-PQ compresses 1TB of embeddings to 64GB — fits in memory for < 10ms ANN search."

---

### Deep Dive 2: Caption-Based Search and "Chapters"

**The Problem**: User wants to find the exact moment in a 2-hour lecture where "gradient descent" is explained. We need timestamp-level search within videos.

**Caption Index Structure**:
```
For each video, store caption segments as nested documents:

{
  "video_id": "abc123",
  "title": "Complete Machine Learning Course",
  "captions": [
    {
      "text": "let's now talk about gradient descent optimization",
      "start_sec": 2340.5,
      "end_sec": 2343.8,
      "confidence": 0.95
    },
    {
      "text": "gradient descent works by computing the partial derivative",
      "start_sec": 2343.8,
      "end_sec": 2347.2,
      "confidence": 0.93
    }
    // ... thousands of segments
  ]
}

Index structure:
  - Main index: video-level fields (title, description, tags)
  - Nested index: caption segments with positions
  - Use ES nested query to match within a segment AND get the timestamp
```

**Search Within Video**:
```
Query: "gradient descent" within video abc123

ES Query:
{
  "nested": {
    "path": "captions",
    "query": {
      "match_phrase": {
        "captions.text": "gradient descent"
      }
    },
    "inner_hits": {
      "size": 5,
      "_source": ["captions.text", "captions.start_sec", "captions.end_sec"]
    }
  }
}

Returns: segments where "gradient descent" is spoken, with timestamps
  → Frontend can create "jump to moment" links
```

**Auto-Generated Chapters**:
```
Problem: Long videos need chapter markers for navigation

Approach:
  1. ASR generates full transcript with timestamps
  2. Topic segmentation model (TextTiling or BERT-based) divides transcript into topics
  3. Each topic boundary → chapter marker
  4. Chapter titles: extractive summarization of each segment
  
  Video "Complete ML Course":
    00:00 - Introduction
    05:30 - Linear Regression
    18:45 - Gradient Descent        ← user can jump here
    32:10 - Neural Networks
    ...

  Indexed as additional metadata for search enrichment
```

**Interview Talking Points**:
- "Nested documents in ES for caption segments — each segment is independently searchable with its timestamp."
- "This enables 'jump to moment' — user searches, gets a link to 23:45 in the video where the term is spoken."
- "Auto-generated chapters via topic segmentation on ASR transcripts."

---

### Deep Dive 3: Engagement-Weighted Ranking

**The Problem**: A new video with 100 views might be more relevant than a 5-year-old video with 10M views. Raw view count is misleading. We need smarter engagement signals.

**Engagement Signals & Feature Engineering**:
```
Raw signals → Derived features:
  
View-based:
  - Total views → not great alone (old videos accumulate)
  - Views in last 7 days → recency-weighted popularity
  - View velocity → views_last_24h / views_last_7d (trending signal)
  
Watch-based:
  - Average watch percentage (avg_watch_pct) → BEST quality signal
    A 10-min video watched to 8 min (80%) is better than one watched to 1 min (10%)
  - Average watch time (seconds) → engagement depth
  - Retention curve shape: gradual decline vs cliff → cliff = clickbait

Interaction-based:
  - Like ratio: likes / (likes + dislikes) → quality signal
  - Comment rate: comments / views → engagement depth
  - Share rate: shares / views → viral potential
  - Subscribe rate after watching → channel quality

Creator-based:
  - Channel subscriber count → authority
  - Channel upload consistency → active creator
  - Channel age → established vs new
```

**Ranking Score Composition**:
```
L1 Score (fast, for top-1000 retrieval):
  score = bm25_score × (1 + 0.3 × log(1 + views_7d))
                     × (1 + 0.2 × avg_watch_pct)
                     × freshness_decay(published_at)

freshness_decay = exp(-λ × days_since_publish)
  λ depends on query intent:
    "news" queries → λ = 0.5 (strong decay)
    "how to" queries → λ = 0.01 (gentle decay, evergreen)
    "music" queries → λ = 0.001 (almost no decay)

L2 Score (ML model with 100+ features):
  Trained on implicit feedback:
    Positive: user watched > 50% of video from search results
    Negative: user clicked but bounced within 10 seconds
  
  Features include all engagement signals + query-video match features
  Model: LambdaMART with 500 trees, max_depth 8
```

**Handling Clickbait**:
```
Detection signals:
  1. Low avg_watch_pct (< 20%) despite high CTR → clickbait thumbnail/title
  2. Sudden cliff in retention curve at 10-30 seconds
  3. High dislike ratio (> 10%)
  4. Comment sentiment analysis: negative

Action:
  - Demote in ranking: multiply score × 0.3 if clickbait_score > 0.8
  - Show warning indicator in results
  - Don't feature in recommendations
```

**Interview Talking Points**:
- "Average watch percentage is the single best quality signal — it directly measures if users found the video valuable."
- "Query-dependent freshness decay: news queries decay fast (λ=0.5), how-to queries barely decay (λ=0.01)."
- "Clickbait detection: high CTR + low watch percentage + retention cliff = clickbait → demote."

---

### Deep Dive 4: Handling Freshness — Indexing 720K Videos/Day

**The Problem**: 500 hours of video uploaded per minute = 720K new videos/day. Each needs to be searchable within 5 minutes. Also, engagement signals change every second.

**Two-Tier Index Strategy**:
```
BASE INDEX (rebuilt every few hours):
  - 1B videos
  - Fully optimized segments (force-merged)
  - Engagement signals: snapshot from last rebuild
  
REALTIME INDEX (in-memory, streaming):
  - Last 24 hours of new/updated videos
  - Updated via Kafka streaming
  - Not optimized (many small segments)
  - Merged into base index every few hours

Query execution:
  1. Search both indices in parallel
  2. Merge results (real-time index results override base for same video)
  3. Re-rank merged results
```

**Engagement Signal Updates**:
```
Problem: View counts change every second. Re-indexing 1B documents for view count updates is impractical.

Solution: Separate engagement from text index
  
  Text Index (ES): title, description, tags, captions — rarely changes
  Engagement Store (Redis/DynamoDB): view_count, like_count, watch_pct — updates every minute
  
  At query time:
  1. ES returns top-500 by text relevance
  2. Lookup fresh engagement signals from Redis for those 500 videos
  3. L2 ranker combines text score + fresh engagement score
  
  This avoids re-indexing 1B documents for engagement changes!
```

**Interview Talking Points**:
- "Separate text index from engagement signals: text in ES (rarely changes), engagement in Redis (real-time)."
- "This avoids re-indexing billions of documents for view count changes — just a fast Redis lookup at query time."
- "Two-tier indexing: base index for 1B videos, real-time index for last 24 hours of uploads."

---

### Deep Dive 5: Query Understanding — Intent Detection and Expansion

**The Problem**: Query "apple" — is the user looking for Apple Inc. product reviews, apple recipes, or Apple TV shows? Query understanding is critical.

**Query Intent Classification**:
```
Intent categories:
  - INFORMATIONAL: "how to tie a tie" → educational videos
  - NAVIGATIONAL: "pewdiepie" → specific channel/video
  - ENTERTAINMENT: "funny cat videos" → entertainment content
  - MUSIC: "bohemian rhapsody" → music video
  - NEWS: "earthquake today" → recent news coverage
  - PRODUCT: "iphone 15 review" → product review
  
Classifier: Multi-label BERT model
  Input: query text + user locale + time of day
  Output: probability distribution over intents
  
Impact on ranking:
  MUSIC intent → boost music category, boost official channels
  NEWS intent → heavy freshness boost, boost news channels
  NAVIGATIONAL → boost exact channel/video name matches
```

**Query Expansion**:
```
"sourdough bread" → expanded query:
  Original: "sourdough bread"
  Synonyms: "sourdough loaf", "artisan bread"
  Related: "sourdough starter", "bread baking"
  Spell variants: (none needed)
  
Implementation:
  1. Synonym dictionary: manually curated + mined from click logs
     (if users search "sourdough bread" and "sourdough loaf" and click same videos → synonyms)
  2. Query embedding similarity: find semantically similar queries
  3. Click graph: query A → clicked same videos as query B → related queries
  
Expansion is weighted: original terms get 2× weight, expansions get 0.5×
```

**Interview Talking Points**:
- "Query intent classification determines ranking behavior: music intent boosts music, news intent boosts freshness."
- "Query expansion from click graphs: if two queries lead to the same clicked videos, they're synonyms."
- "Weight original terms 2× over expansions to avoid query drift."

---

### Deep Dive 6: Personalization in Video Search

**The Problem**: Two users searching "python" — one is a programmer, one likes the Monty Python comedy group. Results should differ.

**User Profile for Search**:
```java
public class UserSearchProfile {
    String userId;
    Map<String, Float> categoryAffinity;   
    // {"Programming": 0.8, "Comedy": 0.1, "Cooking": 0.3}
    // Built from watch history: categories of videos watched
    
    Map<String, Float> channelAffinity;
    // {"UCxyz_programming_channel": 0.9, ...}
    
    List<String> recentSearches;          // Last 50 queries
    List<String> recentWatchedVideoIds;   // Last 200 videos
    String preferredLanguage;             // "en"
    float[] userEmbedding;               // 128-dim vector (updated daily)
}
```

**Personalization Strategy**:
```
At query time:
  1. Retrieve user profile from cache (Redis, < 1ms)
  2. Add personalization features to L2 ranker:
     - category_match: user_category_affinity[video.category]
     - channel_familiarity: user_channel_affinity[video.channel_id]
     - language_match: user.preferredLanguage == video.language
     - embedding_similarity: cosine(user_embedding, video_embedding)
  
  3. Personalization weight: 15-20% of final score
     - Not too high: users should still discover new content
     - "Filter bubble" mitigation: inject 10% exploratory results

Cold-start (new users):
  - Use demographic signals: age, country, language
  - Use popularity-based ranking (no personalization)
  - Build profile quickly: 10-20 watched videos → reasonable profile
```

**Interview Talking Points**:
- "Category affinity from watch history: programmer who watches Python tutorials → 'Programming' affinity 0.8."
- "Personalization is 15-20% of ranking score — enough to help, not enough to create filter bubbles."
- "Inject 10% exploratory results to prevent echo chambers."

---

## What is Expected at Each Level?

### Mid-Level
- Design basic search flow: query → ES → ranked results
- Multi-field search (title, description, tags)
- Basic ranking with BM25 + view count
- Handle filters (duration, date, quality)

### Senior
- Multi-field search with boosting strategies
- Caption/transcript search with timestamps
- Engagement-weighted ranking with watch time
- Two-tier index (base + realtime) for freshness
- Separate engagement signals from text index
- Query understanding (intent + expansion)

### Staff
- Dual retrieval: BM25 + FAISS semantic search
- FAISS with IVF-PQ for billion-scale ANN
- Learning-to-rank with LambdaMART on implicit feedback
- Clickbait detection and demotion
- Personalization with filter bubble mitigation
- Query-dependent freshness decay
- Caption search with chapter generation
- Full capacity planning for 1B videos
