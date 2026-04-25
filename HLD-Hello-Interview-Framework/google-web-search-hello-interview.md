# Design Google Web Search — Hello Interview Framework

> **Question**: Design a web-scale search engine like Google that crawls the web, indexes billions of pages, and returns relevant results in milliseconds. Support keyword search, snippet generation, spell correction, SafeSearch, and personalized ranking.
>
> **Asked at**: Google, Microsoft (Bing), Amazon, Apple, Meta
>
> **Difficulty**: Hard | **Level**: Staff/Principal

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
1. **Keyword Search**: Users submit a text query and get a ranked list of web page results with title, URL, and snippet. Support boolean operators, phrase search ("exact match"), and negative terms (-exclude).
2. **Relevance Ranking**: Results ranked by a multi-signal scoring model combining text relevance (BM25/TF-IDF), link authority (PageRank), freshness, user engagement, and semantic understanding.
3. **Snippet Generation**: Each result shows a query-relevant snippet (highlighted excerpt) from the page content. Dynamic snippets beat static meta descriptions.
4. **Spell Correction**: "googel serch" → "Did you mean: google search?" Auto-correct obvious misspellings; show suggestion for ambiguous ones.
5. **Web Crawling & Indexing**: Continuously discover, fetch, parse, and index billions of web pages. Respect robots.txt, handle deduplication (near-duplicates), and prioritize crawling by page importance.
6. **SafeSearch / Content Filtering**: Filter explicit/harmful content. Support strict, moderate, and off modes.
7. **Freshness**: Breaking news, sports scores, and rapidly changing pages indexed within minutes.

#### Nice to Have (P1)
- Knowledge Graph panels (entity cards for people, places, companies)
- Featured snippets / "position zero" answers
- Image, video, news, shopping tabs (vertical search)
- Voice search support
- Personalized results based on search history and location
- Related searches / "People also ask"
- Auto-complete / type-ahead suggestions

#### Below the Line (Out of Scope)
- Ads / sponsored results (Google Ads is separate)
- Google Maps integration
- Gmail / Drive search (separate product)
- Crawling the deep web / dark web
- Browser / Chrome implementation

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Search Latency** | P50 < 200ms, P95 < 500ms, P99 < 1s | Users abandon after 1s; Google targets < 200ms |
| **Index Size** | 100B+ web pages indexed | Entire searchable web |
| **Crawl Freshness** | Breaking news < 5 min; popular pages < 1 hour; long-tail < 30 days | Tiered freshness by importance |
| **Throughput** | 100K QPS (8.5B searches/day) | Google processes ~99K queries/sec |
| **Availability** | 99.999% (5 min downtime/year) | Search is critical infrastructure |
| **Crawl Scale** | 10B pages/day crawl capacity | Need to re-crawl the web regularly |
| **Consistency** | Eventual (minutes to hours for index updates) | Stale results acceptable briefly |
| **Relevance** | CTR > 60% for top-3; null-rate < 2% | Search quality is the product |

### Capacity Estimation

```
Users & Traffic:
  DAU: 4B users, ~2 searches/user/day = 8.5B searches/day
  QPS: 8.5B / 86400 ≈ 100K QPS average → 300K peak
  Auto-complete: ~400K QPS (multiple keystrokes per search)

Web Corpus:
  Indexed pages: 100B (conservative; Google claims 100T+ URLs known)
  Average page size: 50KB (raw HTML) → 5KB (compressed/extracted text)
  New pages discovered/day: 500M
  Pages re-crawled/day: 10B (varying frequency)

Per Search Request:
  Inbound: ~200 bytes (query + headers)
  Outbound: ~30KB (10 results × 3KB each with snippets + metadata)

Bandwidth:
  Inbound: 8.5B × 200B = 1.7 TB/day (20 MB/s avg)
  Outbound: 8.5B × 30KB = 255 TB/day (3 GB/s avg, 9 GB/s peak)

Storage:
  Raw crawled pages: 100B × 50KB = 5 PB (compressed: ~500 TB)
  Inverted index: ~100B docs × 1KB index entry = 100 TB
  PageRank / link graph: 100B nodes × 50 edges avg = 5T edges → 40 TB
  Snippet index: 100B × 500B = 50 TB
  Total hot storage: ~300 TB per serving cluster
  Cold storage (S3): ~5 PB raw pages
  
Crawling:
  10B pages/day × 50KB = 500 TB/day fetched
  Network: 500 TB / 86400 ≈ 6 GB/s sustained crawl bandwidth
  
Infrastructure (per region):
  Serving index: 10,000+ machines (sharded index)
  Crawl fleet: 1,000+ crawlers
  Index build: 5,000+ machines (MapReduce/Flume)
  Total: ~50,000 machines globally
```

---

## 2️⃣ Core Entities

### Entity 1: WebPage (Indexed Document)
```java
public class WebPage {
    String docId;                // SHA-256 of canonical URL
    String url;                  // "https://en.wikipedia.org/wiki/Search_engine"
    String canonicalUrl;         // Resolved canonical (deduped)
    String title;                // <title> tag content
    String content;              // Extracted plain text (stripped HTML)
    String metaDescription;      // <meta name="description">
    String language;             // "en", "es", "zh"
    float pageRank;              // Authority score [0, 1]
    float spamScore;             // Probability of being spam [0, 1]
    int inboundLinkCount;        // Number of pages linking here
    Instant lastCrawled;         // When we last fetched this page
    Instant lastModified;        // HTTP Last-Modified header
    int contentHash;             // SimHash for near-duplicate detection
    SafeSearchRating safeSearch; // SAFE, MODERATE, EXPLICIT
    List<String> outboundLinks;  // URLs this page links to
}
```

### Entity 2: SearchQuery
```java
public class SearchQuery {
    String queryText;            // "best restaurants in new york"
    String userId;               // For personalization (nullable)
    String locale;               // "en-US"
    GeoLocation location;        // Lat/lng for local results
    SafeSearchMode safeSearch;   // STRICT, MODERATE, OFF
    int page;                    // 0-indexed result page
    int resultsPerPage;          // Default 10
    SearchType type;             // WEB, IMAGE, VIDEO, NEWS
}
```

### Entity 3: SearchResult
```java
public class SearchResult {
    List<WebResult> results;             // Ranked organic results
    SpellSuggestion spellSuggestion;     // "Did you mean: ..."
    KnowledgePanel knowledgePanel;       // Entity card (nullable)
    List<String> relatedSearches;        // "People also search for"
    List<PeopleAlsoAsk> paa;             // Expandable questions
    long totalResults;                   // "About 1,230,000,000 results"
    float searchTimeSeconds;             // "0.47 seconds"
    String nextPageToken;
}

public class WebResult {
    String url;
    String displayUrl;                   // Breadcrumb-style URL
    String title;                        // Clickable title
    String snippet;                      // Dynamic query-relevant excerpt
    Instant lastCached;                  // "Cached" link timestamp
    List<SiteLink> siteLinks;            // Sub-pages (for authoritative sites)
    float relevanceScore;                // Internal ranking score
}
```

### Entity 4: CrawlJob
```java
public class CrawlJob {
    String url;
    CrawlPriority priority;     // REALTIME, HIGH, MEDIUM, LOW
    int depth;                   // Hops from seed URL
    Instant scheduledAt;
    Instant lastAttempt;
    int retryCount;
    CrawlStatus status;         // PENDING, FETCHING, FETCHED, FAILED, ROBOTS_BLOCKED
    String robotsTxt;            // Cached robots.txt for domain
}
```

---

## 3️⃣ API Design

### 1. Web Search
```
GET /api/v1/search?q=best+restaurants+new+york&safe=moderate&page=0&num=10&hl=en&gl=us&location=40.7,-74.0

Headers:
  Authorization: Bearer {jwt}  (optional — anonymous OK)
  Accept-Language: en-US

Response (200 OK):
{
  "results": [
    {
      "url": "https://www.timeout.com/newyork/restaurants/best-restaurants-in-nyc",
      "display_url": "timeout.com › newyork › restaurants",
      "title": "The 50 Best Restaurants in NYC Right Now - Time Out",
      "snippet": "Discover the <b>best restaurants in New York</b> City with our definitive list of the top dining spots, from fine dining to cheap eats...",
      "last_cached": "2026-04-20T14:30:00Z",
      "site_links": [
        {"title": "Fine Dining", "url": "https://..."},
        {"title": "Cheap Eats", "url": "https://..."}
      ]
    }
    // ... 9 more results
  ],
  "knowledge_panel": {
    "title": "New York City",
    "type": "City",
    "description": "New York City is the most populous city in the United States...",
    "population": "8.3 million",
    "image_url": "https://..."
  },
  "spell_suggestion": null,
  "related_searches": [
    "best restaurants in manhattan",
    "michelin star restaurants nyc",
    "best cheap restaurants nyc"
  ],
  "people_also_ask": [
    "What is the number 1 restaurant in New York?",
    "What are the best restaurants in NYC 2026?"
  ],
  "total_results": 1230000000,
  "search_time_seconds": 0.47,
  "next_page_token": "eyJwYWdlIjoxfQ=="
}
```

### 2. Spell Check
```
POST /api/v1/spell-check
Body: {"query": "googel serch", "locale": "en-US"}

Response:
{
  "original": "googel serch",
  "corrected": "google search",
  "confidence": 0.97,
  "type": "AUTO_CORRECT"   // AUTO_CORRECT | DID_YOU_MEAN | NONE
}
```

---

## 4️⃣ Data Flow

### Search Query Flow (Read Path)
```
User types query → CDN/Edge → Load Balancer → Query Service
  → Query Parser (tokenize, normalize, spell-check)
  → Query Rewriter (synonym expansion, stemming)
  → Index Serving (scatter to N shards, gather results)
  → L1 Ranking (BM25 + PageRank, top-1000 candidates)
  → L2 Ranking (ML model: BERT/LambdaMART on top-100)
  → Snippet Generation (extract query-relevant snippet from stored content)
  → Result Assembly (merge organic + knowledge panel + PAA)
  → Return to user (< 200ms target)
```

### Crawl & Index Flow (Write Path)
```
URL Frontier (priority queue of URLs to crawl)
  → Crawler Fleet fetches pages (respects robots.txt, rate limits per domain)
  → Content Extractor (strip HTML, extract text, links, metadata)
  → Deduplication (SimHash to detect near-duplicates → keep best version)
  → Link Extractor → discovered URLs fed back to URL Frontier
  → Document Store (raw page stored in blob storage)
  → Indexer (build inverted index: term → [docId, position, frequency])
  → PageRank Computation (batch on link graph, updated daily)
  → Index Serving pushes updated segments to serving nodes
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                        SEARCH SERVING PATH                       │
│                                                                   │
│  User ──→ CDN/Edge ──→ LB ──→ Query Service                     │
│                                  │                                │
│                          ┌───────┴───────┐                       │
│                          │ Query Parser   │                       │
│                          │ (tokenize,     │                       │
│                          │  spell-check,  │                       │
│                          │  rewrite)      │                       │
│                          └───────┬───────┘                       │
│                                  │                                │
│                    ┌─────────────┼─────────────┐                 │
│                    ▼             ▼             ▼                  │
│              ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│              │ Index     │ │ Index     │ │ Index     │             │
│              │ Shard 1   │ │ Shard 2   │ │ Shard N   │             │
│              │ (BM25     │ │           │ │           │             │
│              │  scoring)  │ │           │ │           │             │
│              └─────┬─────┘ └─────┬─────┘ └─────┬─────┘           │
│                    └─────────────┼─────────────┘                 │
│                                  ▼                                │
│                          ┌───────────────┐                       │
│                          │  L2 Ranker     │                       │
│                          │  (ML model:    │                       │
│                          │   BERT/GBDT)   │                       │
│                          └───────┬───────┘                       │
│                                  ▼                                │
│                          ┌───────────────┐                       │
│                          │  Snippet Gen   │                       │
│                          │  + Result      │                       │
│                          │  Assembly      │                       │
│                          └───────┬───────┘                       │
│                                  ▼                                │
│                             Response                              │
└───────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     CRAWL & INDEX PATH                            │
│                                                                   │
│  URL Frontier ──→ Crawler Fleet ──→ Content Extractor            │
│       ▲                                     │                     │
│       │                          ┌──────────┼──────────┐         │
│       │                          ▼          ▼          ▼         │
│  Link Extractor          Doc Store    Deduplicator   Link        │
│       ▲                  (Blob/GCS)   (SimHash)     Graph        │
│       │                      │                        │          │
│       │                      ▼                        ▼          │
│       │               Index Builder ◄──── PageRank               │
│       │                      │          (daily batch)            │
│       └──────────────────────┤                                   │
│                              ▼                                   │
│                     Index Serving Nodes                           │
│                     (push new segments)                           │
└──────────────────────────────────────────────────────────────────┘
```

### Key Technology Choices

| Component | Technology | Why |
|-----------|-----------|-----|
| **Inverted Index** | Custom (Google uses Caffeine; similar to Lucene) | Need sub-100ms query on 100B docs; no off-shelf handles this |
| **Crawl Store** | GCS / S3 / Colossus | Petabyte-scale blob storage for raw pages |
| **URL Frontier** | Custom priority queue (Mercator-style) | Politeness per domain + priority scheduling |
| **Link Graph / PageRank** | Custom MapReduce / Pregel | Trillions of edges; batch iterative computation |
| **L2 Ranker** | TensorFlow Serving (BERT, LambdaMART) | Real-time ML inference on top-100 candidates |
| **Snippet Store** | Bigtable / custom columnar store | Fast random access to document content |
| **Spell Correction** | Noisy channel model + BERT | Combine statistical + neural approaches |
| **Cache** | CDN + in-memory (Redis/Memcached) | ~30% of queries are repeats; cache SERP |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Inverted Index — How to Search 100B Documents in 200ms

**The Problem**: A naive linear scan of 100B documents would take hours. We need to find all documents containing the query terms and rank them in < 200ms.

**Inverted Index Structure**:
```
Term → PostingList = [(docId, termFreq, positions), ...]

Example:
  "restaurant" → [(doc_1, 5, [3,17,42,89,123]), (doc_7, 2, [1,56]), ...]
  "new"        → [(doc_1, 3, [1,44,90]), (doc_3, 1, [22]), ...]
  "york"       → [(doc_1, 3, [2,45,91]), (doc_3, 1, [23]), ...]
```

**Sharding Strategy — Document-Partitioned Index**:
- Partition the 100B documents across N shards (e.g., 10,000 shards)
- Each shard holds ~10M documents with its own local inverted index
- Query is **scattered** to all shards → each returns top-K → **gathered** and merged

```
Why document-partitioned (not term-partitioned)?
- Term-partitioned: each shard owns certain terms → every query hits ALL shards for all terms
  → terrible for multi-term queries, hot partition on common terms
- Document-partitioned: each shard is independent mini-search-engine
  → scatter-gather model, embarrassingly parallel, easy to scale
  → Google, Bing, Elasticsearch all use this approach
```

**Posting List Compression** (critical for fitting in memory):
- **PForDelta / Variable-Byte Encoding**: Compress docId gaps (delta encoding)
  - DocIds: [100, 105, 112, 200] → Gaps: [100, 5, 7, 88] → VByte encoded
  - 4× compression typical → 100 TB index → 25 TB
- **Block-based skip lists**: Skip over irrelevant segments during intersection
  - For AND queries: skip to the next block where all terms co-occur

**Query Execution — WAND Algorithm**:
```
For "best restaurants new york":
1. Look up posting lists for each term
2. Use WAND (Weighted AND) to skip unpromising documents:
   - Each term has a max possible contribution to score
   - If sum of max contributions for remaining terms < current threshold → skip
3. Only score documents that could potentially be in top-K
4. Result: evaluate BM25 on ~50K docs (out of millions with any match) → 10ms
```

**BM25 Scoring**:
```
score(q, d) = Σ IDF(qi) × (tf(qi, d) × (k1 + 1)) / (tf(qi, d) + k1 × (1 - b + b × |d|/avgdl))

Where:
  IDF(qi) = log((N - n(qi) + 0.5) / (n(qi) + 0.5))  — rarer terms score higher
  tf(qi, d) = term frequency of qi in document d       — diminishing returns
  |d| / avgdl = document length normalization           — long docs don't dominate
  k1 ≈ 1.2, b ≈ 0.75 (tunable)
```

**Interview Talking Points**:
- "Document-partitioned sharding because it makes each shard an independent search engine — embarrassingly parallel scatter-gather."
- "WAND algorithm is the key optimization — it prunes 99% of documents without scoring them."
- "Posting list compression with delta + VByte gives us 4× compression so the index fits in memory."

---

### Deep Dive 2: PageRank and Link-Based Authority

**The Problem**: BM25 alone can't distinguish between a spam site and Wikipedia — both might have high term frequency for "search engine." We need a query-independent quality signal.

**PageRank Algorithm**:
```
PR(A) = (1-d) + d × Σ PR(Ti) / C(Ti)

Where:
  PR(A) = PageRank of page A
  Ti = pages that link to A
  C(Ti) = number of outbound links from Ti
  d = damping factor (0.85) — probability of following a link vs random jump
```

**Implementation at Scale**:
```
Link Graph:
  100B pages, avg 50 outbound links = 5 trillion edges
  Storage: ~40 TB for adjacency list (compressed)

Computation:
  Iterative MapReduce / Pregel (graph-parallel):
  1. Initialize all pages PR = 1/N
  2. For each iteration:
     - Map: Each page distributes PR/outlinks to each target
     - Reduce: Each page sums incoming contributions
     - Add damping: PR(p) = (1-d)/N + d × sum
  3. Repeat until convergence (~50-100 iterations)
  
  Runtime: ~hours on 5,000 machines
  Frequency: Daily for full PageRank; real-time approximation for new pages
```

**Enhancements Beyond Basic PageRank**:
1. **Topic-Sensitive PageRank**: Bias random jump to topic-specific seed pages → different PR per topic
2. **TrustRank**: Start from manually verified trusted pages → trust propagates (spam pages get low trust)
3. **HITS (Hubs & Authorities)**: Distinguish between hubs (pages with good outlinks) and authorities (pages with good inlinks) — useful for topic-specific queries
4. **Link Spam Detection**: Detect link farms, paid links, PBNs → discount their PageRank contribution

**Interview Talking Points**:
- "PageRank is computed offline in batch via iterative MapReduce — it's too expensive to compute in real-time."
- "We use TrustRank as a spam signal — propagate trust from seed set of verified sites."
- "For new pages without link history, we use domain-level PageRank as a prior."

---

### Deep Dive 3: Two-Phase Ranking (L1 + L2)

**The Problem**: We have 100B indexed documents. Running a BERT model on all of them would take hours. We need a funnel: fast retrieval → precise ranking.

**The Ranking Funnel**:
```
100B documents (in index)
    ↓ [Inverted Index + BM25 + WAND] — 5ms per shard
~10K candidates (from scatter-gather across shards)
    ↓ [L1: Lightweight features — BM25 + PageRank + freshness]
~1K candidates
    ↓ [L2: ML Model — LambdaMART / GBDT with 200+ features] — 10ms
~100 candidates
    ↓ [L3 (optional): Neural Reranker — BERT cross-encoder] — 50ms
~10 results (displayed to user)
```

**L1 Features (Fast, Precomputed)**:
```
Query-Document Features:
  - BM25 score
  - Exact phrase match boost
  - Title match boost
  - URL match (query terms in URL)

Document Quality Features:
  - PageRank
  - Domain authority
  - Spam score
  - Content freshness (lastModified)
  - Page load speed

User Features:
  - Language match
  - Location match
  - Click history (user clicked this domain before)
```

**L2 Model — LambdaMART**:
```
LambdaMART: Learning-to-rank using gradient boosted trees
  - Trained on click data: query → [list of docs with user clicks/skips]
  - Loss function: pairwise (doc clicked > doc skipped)
  - 200+ features: text similarity, link features, user signals, freshness
  - Inference: ~0.1ms per document → 1K docs in 100ms ✓

Why not deep learning for L2?
  - GBDT is faster (no GPU needed) and interpretable
  - BERT reranking reserved for L3 on top-100 only
```

**L3 (Optional) — BERT Cross-Encoder**:
```
Input: [CLS] query [SEP] document_passage [SEP]
Output: relevance score

- Processes query-document jointly (not independently)
- Much better relevance but 100× slower than L2
- Run on top-100 candidates from L2
- Latency: ~5ms per doc on GPU → 100 docs × 5ms = 500ms (too slow!)
  → Solution: batch inference + distillation to smaller model → 50ms total
```

**Interview Talking Points**:
- "I'd use a 3-phase ranking funnel: BM25 retrieval → GBDT re-ranking → neural re-ranking on top-100."
- "Each phase trades off speed for quality — BM25 processes millions in ms, BERT processes 100 in 50ms."
- "Train L2 model on click data using LambdaMART — pairwise loss on (clicked, skipped) pairs."

---

### Deep Dive 4: Crawl Architecture — URL Frontier & Politeness

**The Problem**: We need to crawl 10B pages/day while being polite (not DDoS-ing websites), handling failures, and prioritizing important pages.

**URL Frontier Design (Mercator Architecture)**:
```
┌─────────────────────────────────────┐
│           URL Frontier               │
│                                      │
│  Priority Queues (by importance):    │
│  ┌──────┐ ┌──────┐ ┌──────┐        │
│  │ P0   │ │ P1   │ │ P2   │ ...    │
│  │ (news│ │(pop.)│ │(tail)│        │
│  └──┬───┘ └──┬───┘ └──┬───┘        │
│     └────────┼────────┘             │
│              ▼                       │
│  Politeness Queues (per domain):     │
│  ┌────────┐ ┌────────┐             │
│  │cnn.com │ │wiki.org│ ...          │
│  │ (1/s)  │ │ (5/s)  │             │
│  └────────┘ └────────┘             │
│                                      │
│  Rate: min(site crawl-delay,         │
│        our global rate limit)        │
└─────────────────────────────────────┘
```

**Crawl Priority Scoring**:
```
priority(url) = w1 × pageRank(url)          // Authority
              + w2 × freshness_need(url)     // How stale is our copy?
              + w3 × change_frequency(url)   // How often does this page change?
              + w4 × revenue_value(url)       // Business importance (news, shopping)

Tiers:
  P0 (< 5 min):   Breaking news, trending pages
  P1 (< 1 hour):  High-PageRank pages, popular sites
  P2 (< 1 day):   Medium-authority pages
  P3 (< 30 days): Long-tail pages
```

**Deduplication**:
```
Problem: 30% of the web is duplicate/near-duplicate content
  - Exact duplicates: same content, different URL (mirrors, syndication)
  - Near-duplicates: same article with slightly different boilerplate

Solution — SimHash:
  1. Extract features (word n-grams) from document
  2. Hash each feature → 64-bit fingerprint
  3. Weighted combination → 64-bit SimHash
  4. Two docs are near-duplicates if Hamming distance < 3 bits

  O(1) lookup: bucket by first 16 bits, then compare within bucket
  Detects 95%+ near-duplicates
```

**Interview Talking Points**:
- "The URL Frontier uses two-level queuing: priority queues for importance, then politeness queues per domain."
- "SimHash for near-duplicate detection — 64-bit fingerprints with Hamming distance < 3."
- "Crawl priority adapts: if a page changes frequently, we crawl it more often (adaptive scheduling)."

---

### Deep Dive 5: Snippet Generation

**The Problem**: Given a query and a stored document, extract the most relevant 1-2 sentence excerpt to show in search results. Must be fast (< 5ms per result) and query-aware.

**Approach**:
```
Offline (at index time):
  1. Store full text of each document in a compressed snippet store (Bigtable)
  2. Pre-segment into sentences
  3. Compute sentence-level features (position, length, entity density)

Online (at query time):
  For each of the top-10 results:
  1. Fetch document text from snippet store (~1ms)
  2. Find sentences containing query terms
  3. Score each sentence:
     - Query term coverage (how many unique query terms appear?)
     - Term proximity (are query terms close together?)
     - Sentence position (earlier = better for most content)
     - Sentence length (prefer 20-40 word sentences)
  4. Select best 1-2 sentences
  5. Highlight matching terms with <b> tags
  6. Truncate to 160 characters if needed
```

**Fallback Strategy**:
```
Priority order:
  1. Dynamic snippet (query-aware sentence extraction) — best relevance
  2. Meta description tag (if well-written and relevant) — decent fallback
  3. First meaningful sentence of the page — last resort
  
Caching: Cache snippets for popular queries (30% hit rate)
```

---

### Deep Dive 6: Freshness — Real-Time Indexing for Breaking News

**The Problem**: Standard crawl → index pipeline takes hours. Breaking news must be searchable in minutes.

**Two-Tier Index Architecture**:
```
┌──────────────────────────────────────────────┐
│               Query Service                    │
│                                                │
│  ┌─────────────────┐   ┌─────────────────┐   │
│  │  BASE INDEX       │   │  REALTIME INDEX  │   │
│  │  (100B docs)      │   │  (< 24hr docs)   │   │
│  │                   │   │                   │   │
│  │  Built offline    │   │  In-memory        │   │
│  │  via MapReduce    │   │  Updated via      │   │
│  │  Updated daily    │   │  streaming        │   │
│  │                   │   │  (Kafka → index)  │   │
│  │  Optimized,       │   │  Smaller,         │   │
│  │  compressed       │   │  unoptimized      │   │
│  └────────┬──────────┘   └────────┬──────────┘  │
│           └──────────┬───────────┘               │
│                      ▼                           │
│              Merge Results                       │
│              (combine + re-rank)                 │
└──────────────────────────────────────────────────┘

Flow for breaking news:
  1. News source publishes article
  2. Real-time crawler detects (via RSS, Twitter links, PubSubHubbub)
  3. Fetches page within 30 seconds
  4. Streams to Kafka → real-time indexer
  5. Available in realtime index within 2-3 minutes
  6. Eventually merged into base index in next daily build
```

**Freshness Boost in Ranking**:
```
freshness_score(doc) = {
  if doc.age < 5 minutes AND is_news_query(query):
    boost = 2.0   // Strong freshness boost for news queries
  elif doc.age < 1 hour:
    boost = 1.5
  elif doc.age < 24 hours:
    boost = 1.2
  else:
    boost = 1.0 * decay(doc.age, halflife=30days)
}

Query classification determines if freshness matters:
  "breaking news ukraine" → HIGH freshness weight
  "how to tie a tie" → LOW freshness weight (evergreen content)
```

**Interview Talking Points**:
- "I'd use a dual-index architecture: a large optimized base index rebuilt daily, and a small real-time index updated via streaming."
- "Breaking news enters via real-time crawl → Kafka → in-memory index, searchable in < 5 minutes."
- "Query classification determines freshness weight — 'breaking news' gets heavy freshness boost, 'how to tie a tie' doesn't."

---

### Deep Dive 7: Spell Correction at Scale

**The Problem**: 10-15% of queries contain typos. Need to correct "amazn prime delivry" → "amazon prime delivery" in < 10ms.

**Approach — Noisy Channel Model + Neural**:
```
P(correction | misspelling) ∝ P(misspelling | correction) × P(correction)
                                    ↑ error model            ↑ language model

Error Model:
  - Character-level edit distance (insertion, deletion, substitution, transposition)
  - Trained on search logs: common typo patterns (teh → the, resteraunt → restaurant)
  - Weight by keyboard distance (qwerty layout)

Language Model:
  - Unigram: P(word) from query frequency
  - Bigram: P(word | previous_word) for context
  - Modern: BERT-based contextual model for ambiguous cases

Combined Pipeline:
  1. Candidate generation: edit distance ≤ 2 from each word in vocabulary (100K candidates)
  2. Filter: keep candidates in dictionary + query logs
  3. Score: language_model × error_model
  4. If best_correction_score > threshold → auto-correct
  5. If marginal → show "Did you mean: ...?"
```

**Optimization**:
```
- SymSpell: Pre-compute all deletions within edit distance 2
  → O(1) lookup instead of O(|dictionary|) comparison
  → 1M word dictionary × avg 300 deletions = 300M entries in hash map
  → Fits in memory, < 1ms lookup

- Context-aware correction: "apple" → keep as-is (valid word)
  but "appel pie recipe" → "apple pie recipe" (context helps)
```

---

## What is Expected at Each Level?

### Mid-Level
- Describe inverted index and basic BM25 scoring
- Design crawl → index → serve pipeline
- Handle basic sharding of the index
- Mention PageRank at a high level

### Senior
- Design document-partitioned scatter-gather architecture
- Explain WAND algorithm for query optimization
- Two-phase ranking (retrieval + re-ranking)
- URL Frontier with politeness and priority
- Near-duplicate detection with SimHash
- Freshness with dual-index approach

### Staff
- Full three-phase ranking funnel (BM25 → GBDT → BERT)
- Training pipeline for learning-to-rank models
- Detailed PageRank computation at scale (MapReduce/Pregel)
- Posting list compression and memory optimization
- Real-time index architecture (base + realtime merge)
- Spell correction with noisy channel model
- Discuss trade-offs: precision vs recall, freshness vs quality, crawl budget allocation
- Capacity planning for 100B documents
