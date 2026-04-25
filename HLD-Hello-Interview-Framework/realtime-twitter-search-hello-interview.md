# Design Real-Time Twitter/X Search — Hello Interview Framework

> **Question**: Design a real-time search system like Twitter Search that indexes billions of tweets and makes them searchable within seconds of posting. Support keyword search, hashtag search, user mentions, trending topics, and relevance ranking that balances recency with engagement.
>
> **Asked at**: Twitter/X, Meta, Google, Microsoft, LinkedIn, Amazon
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
1. **Full-Text Search**: Search tweets by keyword, phrase, hashtag (#), mention (@), and boolean operators. "bitcoin crash" should find tweets containing both words.
2. **Real-Time Indexing**: New tweets must be searchable within 10 seconds of posting. This is THE defining requirement — Twitter Search is about what's happening NOW.
3. **Recency-First Ranking**: Unlike Google (quality-first), Twitter defaults to "Latest" — most recent tweets first. "Top" mode blends relevance with engagement.
4. **Trending Topics**: Detect and surface trending hashtags and topics in real-time (updated every few minutes). Geo-specific trending (#NYCWeather trending only in NYC).
5. **Advanced Filters**: By date range, from specific user, minimum likes/retweets, media type (has:image, has:video), language, location radius.
6. **User Search**: Search for users by name, username, bio.
7. **Safe Search**: Filter sensitive content, spam, and banned accounts.

#### Nice to Have (P1)
- Conversation search (find replies and threads)
- "People also searched" related queries
- Auto-complete with trending suggestions
- Saved searches with notifications for new matches
- Search within a specific community / list
- Multi-language search and translation

#### Below the Line (Out of Scope)
- Tweet creation/posting
- Timeline / feed generation (separate system)
- Direct messages search
- Ad serving / promoted tweets in search
- Content moderation system

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Index Freshness** | < 10 seconds from tweet to searchable | Real-time is the core value proposition |
| **Search Latency** | P50 < 100ms, P95 < 300ms, P99 < 500ms | Interactive search experience |
| **Throughput** | 50K QPS search; 500K tweets/sec ingestion | 400M tweets/day; massive read volume |
| **Index Size** | 500B+ tweets (all-time), 50B searchable (hot) | Need to search years of history |
| **Availability** | 99.99% for search; 99.999% for ingestion | Never lose tweets; search can briefly degrade |
| **Trending Detection** | < 5 minutes to detect new trend | Breaking news drives Twitter usage |
| **Scale** | 400M tweets/day, 300M MAU | Peak during major events (Super Bowl, elections) |
| **Consistency** | Eventual (seconds) | 5-second lag for new tweets is acceptable |

### Capacity Estimation

```
Tweets:
  New tweets/day: 400M
  Average tweet size: 300 bytes (280 chars + metadata)
  Tweets/sec: 400M / 86400 ≈ 4,600 avg → 50K peak (major events, 10× spike)
  
  Searchable corpus:
    Hot (last 7 days): 2.8B tweets
    Warm (7-30 days): 12B tweets
    Cold (30 days - 2 years): 292B tweets
    Archive (2+ years): 200B+ tweets (not indexed, S3)

Traffic:
  Search QPS: 50K avg, 200K peak (during events)
  Trending queries: 100K QPS (auto-refreshing trending page)
  Total: ~150K avg QPS, 500K peak

Per Search:
  Inbound: ~150 bytes (query + filters)
  Outbound: ~10KB (20 tweets × 500B each)

Storage:
  Hot index (7 days): 2.8B × 300B = 840 GB (with inverted index: ~2 TB)
  Warm index (30 days): 12B × 300B = 3.6 TB (with index: ~8 TB)
  Full searchable (2 years): ~100 TB indexed
  Raw tweet storage (S3): 500B × 300B = 150 TB

Infrastructure:
  Ingestion workers: 50 (10K tweets/sec each)
  Hot search nodes: 40 (50 GB each, SSD)
  Warm search nodes: 100 (80 GB each, HDD)
  Kafka brokers: 30
  Redis (trending + cache): 20 nodes
  3 global regions
```

---

## 2️⃣ Core Entities

### Entity 1: Tweet (Search Document)
```java
public class TweetSearchDocument {
    long tweetId;                // Snowflake ID (encodes timestamp)
    String text;                 // "Just witnessed the most amazing sunset 🌅 #photography #NYC"
    long authorId;               // User ID
    String authorUsername;        // "@johndoe"
    String authorDisplayName;    // "John Doe"
    boolean authorVerified;      // Blue check
    int authorFollowerCount;     // 45000
    List<String> hashtags;       // ["photography", "NYC"]
    List<String> mentions;       // ["@natgeo"]
    List<String> urls;           // Expanded URLs
    boolean hasMedia;            // true
    MediaType mediaType;         // IMAGE, VIDEO, GIF, NONE
    String language;             // "en"
    GeoPoint location;           // Nullable (if geo-tagged)
    long replyToTweetId;         // Nullable (if it's a reply)
    int likeCount;               // 234
    int retweetCount;            // 89
    int replyCount;              // 45
    int quoteCount;              // 12
    float engagementScore;       // Precomputed: f(likes, retweets, replies)
    boolean isSensitive;         // Content warning flag
    Instant createdAt;           // Tweet timestamp (from Snowflake ID)
}
```

### Entity 2: TrendingTopic
```java
public class TrendingTopic {
    String topic;                // "Champions League"
    String hashtag;              // "#UCL" (if hashtag-driven)
    long tweetVolume;            // 1.2M tweets in last hour
    float velocityScore;         // Rate of acceleration (tweets/min derivative)
    String category;             // "Sports", "Politics", "Entertainment"
    GeoScope geoScope;           // GLOBAL, COUNTRY("US"), CITY("New York")
    List<String> relatedHashtags;// ["#RealMadrid", "#ChampionsLeagueFinal"]
    Instant detectedAt;
    Instant peakAt;              // When volume peaked
}
```

### Entity 3: SearchQuery
```java
public class SearchQuery {
    String queryText;            // "bitcoin crash" or "#bitcoin"
    SearchMode mode;             // TOP (relevance), LATEST (recency), PEOPLE, MEDIA
    String fromUser;             // Filter: only tweets from this user
    Instant sinceDate;           // Filter: tweets after this date
    Instant untilDate;           // Filter: tweets before this date
    Integer minLikes;            // Filter: min like count
    Integer minRetweets;         // Filter: min retweet count
    boolean hasMedia;            // Filter: only tweets with images/video
    String language;             // Filter: tweet language
    GeoCircle nearLocation;      // Filter: within radius of location
    SafeSearchMode safeSearch;   // ON, OFF
    int limit;                   // 20
    String cursor;               // Pagination (tweet_id for "latest" mode)
}
```

---

## 3️⃣ API Design

### 1. Search Tweets
```
GET /api/v1/search/tweets?q=bitcoin+crash&mode=top&min_likes=10&lang=en&limit=20

Headers:
  Authorization: Bearer {jwt}

Response (200 OK):
{
  "tweets": [
    {
      "tweet_id": "1792345678901234567",
      "text": "Bitcoin crash below $50K. Biggest drop since 2022. Markets in panic mode 📉",
      "author": {
        "user_id": "12345",
        "username": "cryptoanalyst",
        "display_name": "Crypto Analyst",
        "verified": true,
        "follower_count": 890000
      },
      "created_at": "2026-04-25T10:28:00Z",
      "like_count": 4523,
      "retweet_count": 1892,
      "reply_count": 567,
      "has_media": true,
      "media_type": "IMAGE",
      "hashtags": ["bitcoin", "crypto", "crash"],
      "language": "en"
    }
    // ... more tweets
  ],
  "search_metadata": {
    "query": "bitcoin crash",
    "mode": "top",
    "total_results_approx": 2340000,
    "next_cursor": "DAACCgACF_W...",
    "took_ms": 87
  }
}
```

### 2. Trending Topics
```
GET /api/v1/trends?geo=us&count=10

Response (200 OK):
{
  "trends": [
    {
      "topic": "Bitcoin Crash",
      "hashtag": "#BitcoinCrash",
      "tweet_volume": 1245000,
      "category": "Finance",
      "related": ["#crypto", "#BTC"]
    },
    {
      "topic": "Champions League Final",
      "hashtag": "#UCLFinal",
      "tweet_volume": 3420000,
      "category": "Sports"
    }
    // ... 8 more
  ],
  "as_of": "2026-04-25T10:30:00Z",
  "geo": "US"
}
```

### 3. Search Users
```
GET /api/v1/search/users?q=elon&limit=10

Response (200 OK):
{
  "users": [
    {
      "user_id": "44196397",
      "username": "elonmusk",
      "display_name": "Elon Musk",
      "bio": "Mars & Cars, Chips & Dips",
      "verified": true,
      "follower_count": 180000000,
      "tweet_count": 35000
    }
  ]
}
```

---

## 4️⃣ Data Flow

### Real-Time Tweet Indexing Flow
```
User posts tweet → Tweet Service (write to primary store)
  → Kafka (tweets topic, partitioned by tweet_id)
  → Real-Time Indexer (in-memory inverted index, updated per-tweet)
  → Tweet is searchable within seconds!
  
  Also in parallel:
  → Batch Indexer (periodically compacts real-time index segments)
  → Trending Detector (Kafka Streams: sliding window counts per hashtag)
  → Engagement Updater (likes/retweets update engagement score in index)
```

### Search Flow
```
User searches "bitcoin crash" → Load Balancer → Search API
  → Query Parser (tokenize, extract hashtags/mentions, parse operators)
  → Mode routing:
      LATEST mode → search sorted by created_at desc (simple)
      TOP mode → search sorted by relevance + engagement + recency blend
  → Scatter to index shards (time-partitioned):
      Hot shards (last 7 days) — always searched
      Warm shards (7-30 days) — searched if insufficient hot results
  → Gather + merge + deduplicate
  → Return results
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────┐
│                    REAL-TIME INDEXING                          │
│                                                                │
│  Tweet Post → Tweet Service → Kafka                           │
│                                  │                             │
│                     ┌────────────┼──────────────┐             │
│                     ▼            ▼              ▼             │
│              Real-Time      Batch           Trending           │
│              Indexer        Compactor        Detector           │
│              (in-memory)    (periodic)       (Kafka Streams)   │
│                  │               │              │              │
│                  ▼               ▼              ▼              │
│            ┌──────────┐   ┌──────────┐   ┌──────────┐        │
│            │ Hot Index │   │ Warm     │   │ Trending │        │
│            │ (7 days)  │   │ Index    │   │ Store    │        │
│            │ In-memory │   │ (30 days)│   │ (Redis)  │        │
│            │ + SSD     │   │ HDD      │   └──────────┘        │
│            └──────────┘   └──────────┘                        │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                      SEARCH SERVING                           │
│                                                                │
│  User → CDN → LB → Search API                                │
│                       │                                        │
│              ┌────────┴────────┐                               │
│              ▼                 ▼                                │
│        Query Parser      Mode Router                           │
│                               │                                │
│                    ┌──────────┼──────────┐                    │
│                    ▼          ▼          ▼                     │
│              [Hot Shard] [Hot Shard] [Warm Shard]             │
│              (today)     (yesterday)  (last week)             │
│                    │          │          │                     │
│                    └──────────┼──────────┘                    │
│                               ▼                                │
│                      Merge + Re-rank                           │
│                      (TOP: engagement blend)                   │
│                      (LATEST: time sort)                       │
│                               ▼                                │
│                          Response                              │
└──────────────────────────────────────────────────────────────┘
```

### Key Technology Choices

| Component | Technology | Why |
|-----------|-----------|-----|
| **Real-Time Index** | Custom (Twitter's Earlybird) / Lucene in-memory | Sub-10-second indexing latency |
| **Batch Index** | Elasticsearch / custom Lucene | Optimized segments for warm data |
| **Message Broker** | Apache Kafka | Durable, ordered, high-throughput tweet stream |
| **Trending Detection** | Kafka Streams / Flink | Real-time sliding window aggregation |
| **Trending Store** | Redis (Sorted Sets) | Fast top-K trending topics retrieval |
| **Tweet Store** | Manhattan (Twitter) / DynamoDB | High-throughput key-value for tweets |
| **Cache** | Redis / Memcached | Cache popular search results |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Real-Time Indexing — 10 Second Tweet-to-Search Latency

**The Problem**: This is the #1 differentiator from web search. A tweet posted at T=0 must be searchable by T=10. Standard Elasticsearch has a 1-second refresh interval, but the pipeline before ES adds latency.

**Twitter's Earlybird Architecture (Real-World)**:
```
Twitter built Earlybird: a custom real-time search engine optimized for tweets.

Key design decisions:
  1. In-memory inverted index (not disk-based like standard Lucene)
  2. Append-only: tweets are never updated in the index (engagement tracked separately)
  3. Time-partitioned: each index segment covers ~12 hours of tweets
  4. Optimized for recency queries (most searches want recent tweets)

Index segment lifecycle:
  [Active Segment] ← new tweets written here (in-memory, mutable)
  [Optimizing] → compacting, building skip lists
  [Sealed Segment] → read-only, compressed, can be on SSD
  [Archived] → moved to warm storage after 7 days
```

**Ingestion Pipeline — Minimizing Latency**:
```
Tweet created (T=0)
  → Write to Kafka (T+50ms) — one partition per indexer
  → Indexer consumes from Kafka (T+100ms)
  → Tokenize tweet text:
      "Bitcoin crash below $50K #bitcoin"
      → tokens: ["bitcoin", "crash", "below", "50k"]
      → hashtags: ["bitcoin"]
      → mentions: []
  → Add to in-memory inverted index (T+150ms):
      "bitcoin" → append tweet_id to posting list
      "crash" → append tweet_id to posting list
      etc.
  → Tweet is now searchable (T+200ms total!)

Why so fast?
  - No disk I/O for writes (in-memory)
  - No refresh/commit needed (unlike ES which has refresh_interval)
  - Append-only: just push tweet_id onto posting lists
  - Single writer per partition → no locking
```

**Handling Spikes (Super Bowl, Elections)**:
```
Normal: 4,600 tweets/sec
Super Bowl: 50,000+ tweets/sec (10× spike for hours)

Strategies:
  1. Pre-scale: add indexer nodes before known events
  2. Kafka absorbs burst: even if indexers lag, tweets are buffered
  3. Graceful degradation: if > 30 seconds behind, 
     start sampling (index 50% of tweets, mark results as "some tweets may be delayed")
  4. Back-pressure to write path: slow down non-essential processing (analytics)
     but NEVER slow down indexing
```

**Interview Talking Points**:
- "In-memory append-only inverted index for sub-second indexing — no disk I/O, no refresh interval."
- "Time-partitioned segments: active segment in memory, sealed segments on SSD, archived to warm storage."
- "For spikes, Kafka absorbs bursts; if indexing falls behind > 30s, sample at 50% rather than losing tweets."

---

### Deep Dive 2: Ranking — Balancing Recency vs Relevance vs Engagement

**The Problem**: Twitter has two search modes: "Latest" (pure chronological) and "Top" (quality + recency). "Top" is the hard one — how do you rank a viral tweet from 2 hours ago against a mediocre tweet from 10 seconds ago?

**"Latest" Mode (Simple)**:
```
Ranking: ORDER BY created_at DESC

Filtering:
  1. Text match on query terms (inverted index lookup)
  2. Apply filters (user, date range, language, min_likes)
  3. Sort by Snowflake ID descending (encodes timestamp)
  4. Return top-20

Optimization: since Snowflake IDs are time-ordered, 
  we can stop scanning once we have 20 results from the most recent segment.
  No need to score or rank — just filter + sort by time.
```

**"Top" Mode (Complex)**:
```
score(tweet, query) = 
    w1 × text_relevance(query, tweet)     // BM25 on tweet text
  + w2 × engagement_score(tweet)          // Likes + retweets + replies
  + w3 × author_authority(tweet.author)   // Follower count, verified status
  + w4 × recency_score(tweet)             // Time decay
  + w5 × media_boost(tweet)               // Tweets with images/video rank higher
  + w6 × conversation_depth(tweet)        // Tweets sparking discussion rank higher

Text Relevance:
  BM25 on tweet text (short documents → different k1/b tuning)
  Exact hashtag match → 3× boost
  Exact mention match → 2× boost
  Phrase match ("bitcoin crash" together) → 2× boost

Engagement Score:
  engagement = log(1 + likes) + 1.5 × log(1 + retweets) + 0.5 × log(1 + replies)
  
  Retweets weighted higher: indicates content quality
  Replies weighted lower: could be controversial (not necessarily good)
  
  Normalize by time: engagement_rate = engagement / hours_since_post
  (A tweet with 100 likes in 1 hour > a tweet with 100 likes in 24 hours)

Recency Score (Time Decay):
  recency = exp(-λ × hours_since_post)
  
  λ for Twitter is aggressive: λ ≈ 0.1 to 0.3
  → 1 hour old: 0.90 (barely penalized)
  → 6 hours old: 0.55 (moderate penalty)
  → 24 hours old: 0.09 (heavily penalized)
  → 7 days old: ~0 (effectively invisible)
  
  Compare to Google where λ ≈ 0.001 (content lives for years)!

Author Authority:
  authority = log(1 + followers) × (1 + 0.3 × is_verified)
  
  Prevents spam: random account with 3 followers vs. journalist with 500K
  Verified accounts get 30% boost
```

**Learning-to-Rank for "Top" Mode**:
```
In practice, Twitter uses ML models trained on implicit signals:

Positive examples:
  - User clicked on tweet from search results
  - User liked, retweeted, or replied to tweet from search
  - User followed the author from search results

Negative examples:
  - Tweet shown in results but user scrolled past
  - User clicked but immediately returned to search (bounce)

Model: Deep neural network (not just GBDT)
  - Input: query features, tweet features, author features, user features
  - Output: P(engagement | query, tweet)
  - Trained on billions of search impression → engagement pairs
```

**Interview Talking Points**:
- "Two modes: 'Latest' is trivial (sort by Snowflake ID), 'Top' requires multi-signal ranking."
- "Time decay for Twitter is 10-100× more aggressive than web search — λ ≈ 0.2 vs 0.001."
- "Engagement normalized by time: 100 likes in 1 hour > 100 likes in 24 hours."

---

### Deep Dive 3: Trending Topic Detection

**The Problem**: Detect that "#BitcoinCrash" is trending within minutes of the first surge. Also detect non-hashtag trends ("Champions League") and geo-specific trends.

**Approach: Sliding Window + Velocity Detection**:
```
Pipeline:
  Kafka (all tweets) → Kafka Streams / Flink
    → Extract entities: hashtags, @mentions, n-grams
    → Sliding window aggregation:
        Count each entity per 5-minute tumbling window
    → Compare to baseline:
        baseline = average count for this entity at this time of day / day of week
        velocity = current_window_count / baseline_count
    → If velocity > threshold (e.g., 5×) → TRENDING!

Example:
  "#BitcoinCrash" baseline (typical Friday afternoon): 50 tweets / 5 min
  Current window: 5,000 tweets / 5 min
  Velocity: 100× → STRONGLY TRENDING

Why velocity (not raw count)?
  "Good morning" always has high volume but isn't trending
  "#BitcoinCrash" normally has low volume, so 100× spike = trending
  Velocity captures CHANGE, not absolute popularity
```

**Non-Hashtag Trend Detection**:
```
Problem: not all trends have hashtags. "Champions League" might be discussed 
without #ChampionsLeague

Solution: Named Entity Recognition (NER) + n-gram frequency analysis
  1. NER: extract entities from tweet text (people, organizations, events)
  2. Bigram/trigram frequency: track 2-3 word phrases
  3. Apply same velocity detection on entities and n-grams
  4. Cluster related terms: "Champions League" + "#UCL" + "Real Madrid" → same trend

Clustering:
  - Co-occurrence: terms appearing in same tweets are likely about the same event
  - Temporal correlation: terms spiking at the same time → related
  - Group into "trend clusters" and pick the best label for each
```

**Geo-Specific Trending**:
```
Many trends are local:
  "#NYCWeather" — only trending in New York
  "Lakers" — trending in LA, not globally

Implementation:
  - Maintain separate sliding windows per geo region:
    - Global
    - Per country (195 countries)
    - Per major city (500 cities)
  
  - For each tweet with geo data (or user profile location):
    increment counters for the relevant geo windows
  
  - Trending in NYC: velocity > 5× in NYC window, even if global velocity is 1×
  
  Storage: Redis Sorted Sets per geo-window
    Key: "trending:US:NYC:2026-04-25T10:30"
    Members: {entity: velocity_score}
    ZREVRANGE to get top-K trending
```

**Interview Talking Points**:
- "Velocity over raw count: 'good morning' has high count but isn't trending; velocity captures CHANGE."
- "Non-hashtag trends via NER + n-gram frequency + co-occurrence clustering."
- "Geo-specific trending: separate sliding windows per country/city using Redis Sorted Sets."

---

### Deep Dive 4: Time-Partitioned Index Architecture

**The Problem**: 500B+ tweets total. Most searches want recent tweets (80% of queries target last 24 hours). Searching all 500B for every query is insane.

**Time-Based Index Partitioning**:
```
┌─────────────────────────────────────────────────────────────┐
│                 INDEX TIER ARCHITECTURE                       │
│                                                               │
│  ┌─────────────────────────────┐                             │
│  │  REALTIME TIER (< 12 hours) │  ← 200M tweets             │
│  │  In-memory index             │  ← Searched ALWAYS         │
│  │  Updated per-tweet           │  ← Fastest (< 50ms)        │
│  │  No replicas (ephemeral)     │                             │
│  └─────────────────────────────┘                             │
│                                                               │
│  ┌─────────────────────────────┐                             │
│  │  HOT TIER (12h - 7 days)    │  ← 2.8B tweets             │
│  │  SSD-backed Lucene index     │  ← Searched ALWAYS         │
│  │  Optimized segments          │  ← Fast (< 100ms)          │
│  │  2 replicas                  │                             │
│  └─────────────────────────────┘                             │
│                                                               │
│  ┌─────────────────────────────┐                             │
│  │  WARM TIER (7 - 30 days)    │  ← 12B tweets              │
│  │  HDD-backed                  │  ← Searched IF NEEDED      │
│  │  (if hot tier has < 20 results) │  ← Slower (< 500ms)    │
│  │  1 replica                   │                             │
│  └─────────────────────────────┘                             │
│                                                               │
│  ┌─────────────────────────────┐                             │
│  │  COLD TIER (30d - 2 years)  │  ← 292B tweets             │
│  │  Object storage (S3)         │  ← Searched ON DEMAND      │
│  │  Loaded into memory for query│  ← Slow (< 5s)            │
│  │  No replicas (S3 durable)    │                             │
│  └─────────────────────────────┘                             │
│                                                               │
│  ┌─────────────────────────────┐                             │
│  │  ARCHIVE (2+ years)         │  ← 200B+ tweets            │
│  │  S3 Glacier                  │  ← Not searchable          │
│  │  Restore on demand (hours)   │  ← Compliance only         │
│  └─────────────────────────────┘                             │
└─────────────────────────────────────────────────────────────┘
```

**Cascading Search Strategy**:
```
1. ALWAYS search realtime + hot tiers (cover last 7 days)
2. If results < min_threshold (20):
   → Also search warm tier (last 30 days)
3. If still < min_threshold AND user specified date range:
   → Load cold tier index for that date range → search
4. Merge results from all searched tiers, re-rank

Why not always search all tiers?
  - 80% of queries are satisfied by hot tier alone
  - Searching warm/cold adds latency and cost
  - Cascading: try fast path first, expand only if needed
```

**Shard Strategy Within Each Tier**:
```
Hot tier sharding:
  - 7 day segments × 4 shards per day = 28 shards
  - Each shard: 100M tweets, ~50 GB
  - Scatter-gather: query all 28 shards in parallel
  - But for "LATEST" mode: start with today's shards, expand backward

Document ID (Snowflake):
  Encodes timestamp in high bits → documents are naturally time-ordered
  → Range queries on time are efficient (just compare Snowflake IDs)
  → No separate timestamp field needed for time filtering
```

**Interview Talking Points**:
- "4-tier index: realtime (in-memory), hot (SSD), warm (HDD), cold (S3) — searched in cascade."
- "80% of queries satisfied by hot tier alone; cascade to warm/cold only if insufficient results."
- "Snowflake IDs encode timestamps → time range queries are just ID range comparisons."

---

### Deep Dive 5: Engagement Signal Updates Without Re-Indexing

**The Problem**: Like counts change every second. A viral tweet might go from 100 to 100K likes in an hour. But re-indexing the tweet in our search index every time someone likes it is prohibitively expensive.

**Separate Index from Engagement Store**:
```
Search Index (Lucene/ES):
  - Stores: text, hashtags, mentions, author info, media
  - Indexed ONCE when tweet is created
  - NEVER updated for engagement changes
  
Engagement Store (Redis / DynamoDB):
  - Key: tweet_id
  - Value: {likes: 45230, retweets: 12340, replies: 3421, engagement_score: 8.7}
  - Updated in real-time (every like/retweet)
  
At query time (for "Top" mode):
  1. Text search in inverted index → top-500 candidates by text relevance
  2. Batch lookup engagement signals from Redis for those 500 tweet_ids
     (Redis MGET: 500 keys in < 1ms)
  3. Re-rank combining text_score + fresh engagement_score
  4. Return top-20

Advantage: index is append-only, engagement is real-time, decoupled!
```

**Engagement Score Pre-Computation**:
```
Instead of fetching raw counts and computing score at query time:
  
Background worker (every 30 seconds):
  For each active tweet (last 48 hours, ~8M tweets):
    score = log(1 + likes) + 1.5 × log(1 + retweets) + 0.5 × log(1 + replies)
    Store in Redis: tweet_id → engagement_score
  
  Only update tweets that had engagement changes since last update
  (Kafka stream of like/retweet events → set of tweet_ids to recalculate)
  
  Result: pre-computed engagement_score available for instant lookup at query time
```

**Interview Talking Points**:
- "Index text once, store engagement in Redis — avoids re-indexing on every like/retweet."
- "Batch MGET from Redis for 500 candidate tweet IDs in < 1ms — fast engagement lookup."
- "Pre-compute engagement_score every 30 seconds for active tweets to avoid per-query computation."

---

### Deep Dive 6: Spam and Manipulation Prevention in Search

**The Problem**: Bad actors try to manipulate search by: trending manipulation (coordinated hashtagging), keyword spam (stuffing popular keywords), bot networks (fake engagement), and SEO gaming.

**Anti-Spam in Indexing**:
```
Before indexing a tweet, apply spam filters:

1. Account Quality Score:
   - Account age < 24 hours → suppress from search (new account penalty)
   - Account has < 10 followers and > 100 tweets → likely bot
   - Account creation velocity: if account was created + immediately posted → suspicious
   
2. Content Spam Detection:
   - Duplicate detection: SimHash of tweet text → if near-duplicate of recent tweet → suppress
   - Keyword stuffing: if tweet has > 10 hashtags → demote
   - Known spam patterns: "Follow me for...", "Buy cheap...", etc.
   
3. Bot Network Detection:
   - Coordinated behavior: if 1000 accounts tweet the same hashtag within 60 seconds
     from accounts created within the same week → coordinated campaign
   - Behavioral anomaly: accounts tweeting at inhuman rates (> 100 tweets/hour)
```

**Anti-Manipulation in Trending**:
```
Trending manipulation is a major problem:
  - Political actors try to make hashtags trend
  - Marketing campaigns coordinate hashtag campaigns

Defense:
  1. Source diversity: require tweets from diverse accounts (different creation dates,
     geo locations, follower counts) for a topic to trend
     → 1000 tweets from 1000 unique accounts > 1000 tweets from 50 accounts
  
  2. Account quality weighting: each tweet's contribution to trending weighted by
     account quality score. Bot accounts contribute 0, verified accounts contribute 3×
  
  3. Velocity cap: if a hashtag goes from 0 to 100K in 5 minutes, it might be
     coordinated. Apply "cooling period" of 30 minutes to verify organic growth.
  
  4. Human review pipeline: flag suspicious trends for manual review before
     displaying on trending page
```

**Interview Talking Points**:
- "Multi-layer spam defense: account quality scoring, content dedup via SimHash, coordinated behavior detection."
- "Trending requires source diversity — 1000 tweets from 1000 accounts > 1000 tweets from 50 accounts."
- "Velocity cap for trending: sudden spikes trigger a 30-minute cooling period to verify organic growth."

---

## What is Expected at Each Level?

### Mid-Level
- Design basic tweet ingestion → index → search pipeline
- Time-based index partitioning
- "Latest" mode (sort by time)
- Basic Kafka + Elasticsearch architecture
- Simple trending detection (hashtag counting)

### Senior
- Sub-10-second indexing with in-memory index
- "Top" mode ranking with engagement + recency + relevance
- Time-partitioned cascade search (hot → warm → cold)
- Separated engagement store (Redis) from text index
- Trending detection with velocity over raw counts
- Snowflake ID for time-encoded document IDs

### Staff
- Custom real-time index (Earlybird-style) vs Elasticsearch trade-offs
- Learning-to-rank on click/engagement data
- Geo-specific trending with NER and co-occurrence clustering
- Anti-spam and trending manipulation prevention
- Handling 10× traffic spikes (major events)
- Detailed capacity planning for 500B tweets
- Discuss trade-offs: recency vs quality, freshness vs indexing cost, spam prevention vs free speech
