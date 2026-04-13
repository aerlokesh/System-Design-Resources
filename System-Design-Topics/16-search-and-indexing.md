# 🎯 Topic 16: Search & Indexing

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Elasticsearch, inverted indexes, async indexing via Kafka, two-phase search (recall + ranking), typeahead/autocomplete, and how to articulate search architecture decisions in interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Inverted Index — The Foundation](#inverted-index--the-foundation)
3. [Elasticsearch Architecture](#elasticsearch-architecture)
4. [Async Indexing via Kafka](#async-indexing-via-kafka)
5. [Two-Phase Search: Recall + Ranking](#two-phase-search-recall--ranking)
6. [Text Analysis Pipeline](#text-analysis-pipeline)
7. [BM25 Relevance Scoring](#bm25-relevance-scoring)
8. [Typeahead / Autocomplete](#typeahead--autocomplete)
9. [Faceted Search](#faceted-search)
10. [Geospatial Search](#geospatial-search)
11. [Search as a Derived View](#search-as-a-derived-view)
12. [Indexing Strategies](#indexing-strategies)
13. [Search Performance Optimization](#search-performance-optimization)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Search Architecture by System Design Problem](#search-architecture-by-system-design-problem)
17. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Search enables users to find relevant content from billions of documents in milliseconds. The key architectural insight: **the search index is a derived view, not a source of truth**. The canonical data lives in your primary database; the search index is populated asynchronously and can be rebuilt.

```
Source of Truth:  PostgreSQL / Cassandra (ACID, durable)
     │
     │ CDC / Kafka events (async)
     ▼
Search Index:     Elasticsearch (inverted index, full-text)
     │
     │ Query
     ▼
Results:          Ranked, paginated, filtered
```

---

## Inverted Index — The Foundation

### How It Works
A regular (forward) index maps documents to words. An **inverted index** maps words to documents.

```
Forward index:
  Doc 1: "system design interview"
  Doc 2: "design patterns for microservices"
  Doc 3: "system architecture patterns"

Inverted index:
  "system"        → [Doc 1, Doc 3]
  "design"        → [Doc 1, Doc 2]
  "interview"     → [Doc 1]
  "patterns"      → [Doc 2, Doc 3]
  "microservices" → [Doc 2]
  "architecture"  → [Doc 3]

Query: "system design"
  "system" → [Doc 1, Doc 3]
  "design" → [Doc 1, Doc 2]
  Intersection: [Doc 1]  ← Most relevant (both terms match)
```

### Why Inverted Indexes Are Fast
```
Without index: Scan ALL documents for matching words → O(N × M)
  N = number of documents (billions)
  M = average document length

With inverted index: Look up each query term → O(K × L)
  K = number of query terms (2-5)
  L = number of matching documents per term (sorted, mergeable)

For "system design" across 1 billion documents:
  Without index: Scan 1B docs → minutes
  With index: Look up 2 posting lists, merge → milliseconds
```

---

## Elasticsearch Architecture

### Cluster Structure
```
┌──────────────────────────────────────────────────┐
│            Elasticsearch Cluster                  │
├──────────────────────────────────────────────────┤
│                                                  │
│  Index: "tweets" (5 primary shards, 1 replica)   │
│                                                  │
│  Node 1:         Node 2:         Node 3:         │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐    │
│  │ Shard 0P │   │ Shard 1P │   │ Shard 2P │    │
│  │ Shard 3R │   │ Shard 4R │   │ Shard 0R │    │
│  └──────────┘   └──────────┘   └──────────┘    │
│                                                  │
│  Node 4:         Node 5:                         │
│  ┌──────────┐   ┌──────────┐                    │
│  │ Shard 3P │   │ Shard 4P │                    │
│  │ Shard 1R │   │ Shard 2R │                    │
│  └──────────┘   └──────────┘                    │
│                                                  │
│  P = Primary, R = Replica                        │
└──────────────────────────────────────────────────┘
```

### Key Concepts
- **Index**: A collection of documents (like a database table).
- **Shard**: A partition of an index (horizontal scaling).
- **Replica**: A copy of a shard (high availability + read scaling).
- **Document**: A JSON object stored in the index.
- **Mapping**: Schema definition (field types, analyzers).

### Query Flow
```
1. Client sends query to any node (coordinating node)
2. Coordinating node routes query to ALL primary shards
3. Each shard executes query locally → returns top-K results
4. Coordinating node merges results from all shards
5. Returns final top-K to client

Latency: 10-100ms for most queries
```

---

## Async Indexing via Kafka

### Architecture
```
┌──────────┐   write   ┌──────────┐   CDC    ┌─────────┐
│  Tweet   │ ────────→ │ Cassandra │ ───────→ │  Kafka   │
│ Service  │           │ (source)  │          │          │
└──────────┘           └──────────┘          └────┬────┘
                                                   │
                                          ┌────────┴────────┐
                                          ▼                 ▼
                                    ┌──────────┐    ┌──────────┐
                                    │ Search   │    │ Analytics │
                                    │ Indexer  │    │ Pipeline  │
                                    └────┬─────┘    └──────────┘
                                         ▼
                                    ┌──────────┐
                                    │  Elastic  │
                                    │  search   │
                                    └──────────┘
```

### Why Async?
1. **Decoupled**: Tweet creation doesn't wait for Elasticsearch indexing.
2. **Resilient**: If Elasticsearch is slow/down, tweets still save. Kafka buffers events.
3. **Replayable**: If the index is corrupted, replay from Kafka to rebuild.
4. **Multiple consumers**: Same event feeds search, analytics, moderation.

### Indexing Lag
```
Typical: 1-2 seconds from write to searchable.
Under load: Can spike to 5-10 seconds.
Acceptable for most use cases (tweet search, product search).
NOT acceptable for: real-time systems requiring immediate searchability.
```

### Interview Script
> *"I'd use Elasticsearch with async indexing via Kafka. The write path is: user creates a tweet → write to Cassandra (source of truth) → publish `TweetCreated` event to Kafka → search indexer consumer reads from Kafka → indexes the tweet in Elasticsearch. Search results may lag by ~1-2 seconds, but the write path isn't coupled to the indexing path. If Elasticsearch is slow or down, tweets still save successfully — the Kafka consumer just builds up lag and catches up when ES recovers."*

---

## Two-Phase Search: Recall + Ranking

### Phase 1: Recall (Candidate Generation)
```
Elasticsearch returns top 1000 candidates using BM25 text relevance.
Fast, broad, term-matching.
Focus: Find all potentially relevant documents.
```

### Phase 2: Ranking (Re-ranking)
```
ML model re-ranks the 1000 candidates by business-relevant signals:
  - User purchase history (personalization)
  - Price sensitivity
  - Product rating
  - Seller quality
  - Click-through rate
  - Freshness
  - Geographic relevance

Final top 20 returned to user.
```

### Why Two Phases?
```
Phase 1 alone: Returns relevant results but not optimally ordered.
  "Running shoes" → cheapest first? Highest rated? Most popular?

Phase 2 adds: Business logic and personalization.
  For a price-sensitive user → budget options first.
  For a brand-loyal user → their preferred brands first.
  For a new user → highest-rated first.

Separates "find relevant" (search engine) from "order by value" (ML model).
```

### Interview Script
> *"For product search, I'd implement a two-phase approach. Phase 1 (recall): Elasticsearch returns the top 1000 candidates using BM25 text relevance — fast, broad, term-matching. Phase 2 (ranking): a lightweight ML model re-ranks those 1000 candidates by predicted conversion probability. The final top 20 are returned to the user. This separates the 'find relevant products' problem from the 'order by business value' problem."*

---

## Text Analysis Pipeline

### How Text Is Processed Before Indexing
```
Raw text: "The Quick Brown Fox Jumps Over the Lazy Dog!"

Step 1: Character filtering
  Remove HTML tags, special characters → "The Quick Brown Fox Jumps Over the Lazy Dog"

Step 2: Tokenization (split into words)
  → ["The", "Quick", "Brown", "Fox", "Jumps", "Over", "the", "Lazy", "Dog"]

Step 3: Lowercasing
  → ["the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog"]

Step 4: Stop-word removal (remove common words)
  → ["quick", "brown", "fox", "jumps", "lazy", "dog"]

Step 5: Stemming (reduce to root form)
  → ["quick", "brown", "fox", "jump", "lazi", "dog"]

Result stored in inverted index:
  "quick" → [doc_1]
  "brown" → [doc_1]
  "fox"   → [doc_1]
  "jump"  → [doc_1]  (matches "jumping", "jumped", "jumps")
  "lazi"  → [doc_1]  (matches "lazy", "lazily")
  "dog"   → [doc_1]
```

### Custom Analyzers
```
For product search: synonym expansion
  "laptop" → also indexes "notebook", "portable computer"
  
For multilingual: language-specific stemming
  English: "running" → "run"
  Spanish: "corriendo" → "corr"
  
For code search: camelCase splitting
  "getUserProfile" → ["get", "user", "profile"]
```

---

## BM25 Relevance Scoring

### Formula (Simplified)
```
Score(query, document) = Σ IDF(term) × TF(term, document)

IDF (Inverse Document Frequency):
  Rare terms score higher. "quantum" is more specific than "the".
  IDF = log(N / df) where N = total docs, df = docs containing term.

TF (Term Frequency):
  More occurrences in the document = higher score.
  But with diminishing returns (10 mentions isn't 10x better than 1).
  TF = (f × (k1 + 1)) / (f + k1 × (1 - b + b × dl/avgdl))

Where:
  f = term frequency in document
  k1 = saturation parameter (default 1.2)
  b = length normalization (default 0.75)
  dl = document length
  avgdl = average document length
```

### What This Means Practically
- A document mentioning "system design" 5 times scores higher than one mentioning it once.
- A short document mentioning "system design" once scores higher than a long document mentioning it once (length normalization).
- "System design" in a rare field (like a title) can be boosted higher than in the body.

---

## Typeahead / Autocomplete

### Requirements
- Results in < 100ms per keystroke.
- Show suggestions as user types.
- Personalized + global suggestions.

### Implementation: Prefix Trie in Redis
```
User types "sys":

Redis trie lookup:
  "sys" → ["system design", "system administrator", "systems engineering"]
  Sorted by frequency/popularity.

Key structure:
  ZADD autocomplete:sys 1000 "system design"
  ZADD autocomplete:sys 500  "system administrator"  
  ZADD autocomplete:sys 200  "systems engineering"

Query:
  ZREVRANGE autocomplete:sys 0 4  → top 5 suggestions
  Latency: < 2ms
```

### Personalized Autocomplete
```
Blend global suggestions with user's recent searches:

Global:   ["system design", "system administrator"]
Personal: ["system design interview", "system architecture"]

Merged:   ["system design interview", "system design", 
           "system architecture", "system administrator"]

Personal suggestions weighted higher (user's own history is more relevant).
```

### Trie Update Strategy
```
Hourly batch job:
  1. Aggregate search logs from last 24 hours
  2. Compute frequency for each prefix
  3. Update Redis sorted sets

NOT real-time: A new trending search term appears in autocomplete 
within 1 hour, which is acceptable.
```

### Interview Script
> *"For typeahead/autocomplete, I need results in < 100ms per keystroke. I'd use a prefix trie stored in Redis — each prefix maps to the sorted list of completions weighted by frequency. When the user types 'sys', Redis returns `['system design', 'system administrator', 'systems engineering']` in < 2ms. The trie is updated hourly from search log aggregations. For personalized autocomplete, I'd blend the global trie results with the user's recent search history."*

---

## Faceted Search

### What It Is
Filters alongside search results: category, price range, brand, rating, color.

```
Search: "running shoes"

Results: 2,847 items

Facets:
  Brand:    Nike (847), Adidas (623), New Balance (412), ...
  Price:    $0-50 (234), $50-100 (1,203), $100-200 (987), $200+ (423)
  Rating:   4+ stars (1,892), 3+ stars (2,456)
  Color:    Black (1,102), White (876), Blue (534), ...

User clicks "Nike" + "$50-100":
  Refined results: 312 items
  Facet counts update to reflect the filtered set.
```

### Implementation in Elasticsearch
```json
{
  "query": { "match": { "name": "running shoes" } },
  "aggs": {
    "brands": { "terms": { "field": "brand.keyword", "size": 10 } },
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          { "to": 50 }, { "from": 50, "to": 100 },
          { "from": 100, "to": 200 }, { "from": 200 }
        ]
      }
    },
    "avg_rating": { "avg": { "field": "rating" } }
  }
}
```

---

## Geospatial Search

### "Restaurants Near Me"
```json
{
  "query": {
    "bool": {
      "must": { "match": { "cuisine": "pizza" } },
      "filter": {
        "geo_distance": {
          "distance": "5km",
          "location": { "lat": 40.7128, "lon": -74.0060 }
        }
      }
    }
  },
  "sort": [
    { "_geo_distance": { "location": { "lat": 40.7128, "lon": -74.0060 }, "order": "asc" } }
  ]
}
```

### Implementation
- Elasticsearch uses geohash-based indexing.
- Efficient for radius queries, bounding box queries, and polygon queries.
- Combined with text search: "pizza restaurants within 5km, sorted by distance."

---

## Search as a Derived View

### Key Principle
```
Elasticsearch is NOT the source of truth.
It's a READ-OPTIMIZED DERIVED VIEW.

If the index is corrupted or lost:
  1. Delete the index
  2. Replay events from Kafka (or scan the source DB)
  3. Rebuild the index from scratch
  4. Zero data loss (source of truth is unchanged)

This is why async indexing via Kafka is essential —
  Kafka retains events for 30 days, enabling full index rebuilds.
```

### Consistency Model
```
Write: Cassandra (source of truth) — strong consistency
Index: Elasticsearch (derived view) — eventual consistency (1-2s lag)
Read:  Search results may be 1-2 seconds behind latest writes

This is acceptable for:
  ✅ Tweet search (user's tweet appears in search 2s after posting)
  ✅ Product search (new product searchable within seconds)
  
Not acceptable for:
  ❌ Real-time inventory check (use DB directly, not search)
  ❌ Financial data search (use DB with strong consistency)
```

---

## Indexing Strategies

### Full Reindex (Batch)
```
Periodically rebuild entire index from source DB.
Use: Initial setup, schema changes, data corruption recovery.
Duration: Hours for large datasets (billions of docs).
```

### Incremental Indexing (Streaming)
```
Process change events from Kafka in real-time.
Use: Ongoing operation (99% of the time).
Latency: 1-2 seconds from change to searchable.
```

### Blue-Green Indexing
```
Current: Index V1 (serving traffic)
New:     Index V2 (being built)

1. Build V2 from scratch (full reindex)
2. V2 catches up to real-time via Kafka
3. Switch alias: "tweets" → V2
4. Delete V1

Zero downtime. Used for mapping/schema changes.
```

---

## Search Performance Optimization

| Technique | Impact | Use Case |
|---|---|---|
| **Caching query results** | 10-100x faster for repeated queries | Popular searches |
| **Filter caching** | Precomputed bitsets for common filters | Faceted navigation |
| **Index warm-up** | Preload hot segments into memory | After node restart |
| **Routing** | Route queries to specific shards | User-specific data |
| **Scroll/Search-after** | Efficient deep pagination | Exporting large result sets |
| **Source filtering** | Return only needed fields | Reduce network payload |

---

## Interview Talking Points & Scripts

### Async Indexing
> *"The search index is a derived view populated asynchronously via Kafka. If Elasticsearch is slow or down, writes still succeed. The index can be rebuilt from the event log."*

### Two-Phase Search
> *"Phase 1: Elasticsearch returns top 1000 candidates by text relevance. Phase 2: ML model re-ranks by conversion probability. Separates 'find relevant' from 'order by value.'"*

### Typeahead
> *"Prefix trie in Redis — each prefix maps to sorted completions. 'sys' returns top 5 suggestions in < 2ms. Updated hourly from search log aggregations."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using Elasticsearch as the source of truth
**Fix**: ES is a derived view. Source of truth is your primary DB.

### ❌ Mistake 2: Synchronous indexing on the write path
**Fix**: Async via Kafka. Don't couple write latency to index latency.

### ❌ Mistake 3: Not mentioning relevance ranking
**Fix**: BM25 for recall, ML model for ranking. Two-phase search.

### ❌ Mistake 4: Ignoring index rebuild capability
**Fix**: Kafka retention enables full index rebuilds from event log.

### ❌ Mistake 5: Not addressing search latency
**Fix**: Mention < 100ms target, caching, and filter precomputation.

---

## Search Architecture by System Design Problem

| Problem | Search Technology | Indexing | Special Features |
|---|---|---|---|
| **Twitter** | Elasticsearch | Async via Kafka | Real-time tweet search, hashtags |
| **E-Commerce** | Elasticsearch | CDC from product DB | Faceted search, price filtering |
| **Google Maps** | Custom geo-index | Continuous crawl + updates | Geospatial, "near me" |
| **Stack Overflow** | Elasticsearch | Async | Code search, tag filtering |
| **GitHub** | Custom (Blackbird) | Git event stream | Code search, regex support |
| **Log Analytics** | Elasticsearch (ELK) | Direct ingest | Full-text log search |
| **Autocomplete** | Redis prefix trie | Hourly batch from search logs | < 100ms per keystroke |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              SEARCH & INDEXING CHEAT SHEET                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CORE: Inverted index maps terms → document IDs              │
│  TOOL: Elasticsearch (default for full-text search)          │
│                                                              │
│  ARCHITECTURE:                                               │
│    Source DB → Kafka → Search Indexer → Elasticsearch         │
│    (async, decoupled, replayable)                            │
│                                                              │
│  TWO-PHASE SEARCH:                                           │
│    Phase 1: Recall (BM25, top 1000 candidates)               │
│    Phase 2: Ranking (ML model, top 20 results)               │
│                                                              │
│  TYPEAHEAD: Redis prefix trie, < 2ms, hourly update          │
│  FACETS: Elasticsearch aggregations                          │
│  GEO: Elasticsearch geo_distance queries                     │
│                                                              │
│  KEY PRINCIPLE: Search index is a DERIVED VIEW               │
│    Can be rebuilt from Kafka / source DB                      │
│    1-2 second indexing lag is acceptable                      │
│                                                              │
│  LATENCY: < 100ms for search, < 100ms for typeahead          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
