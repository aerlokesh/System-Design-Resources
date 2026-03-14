# Design Facebook Post Search - Hello Interview Framework

> **Question**: Design a system that allows Facebook users to search through billions of posts in real-time with support for keyword search, filters, and ranking.
>
> **Difficulty**: Medium | **Level**: Senior

## Table of Contents
- [Understanding the Problem](#understanding-the-problem)
- [Set Up](#set-up)
- [High-Level Design](#high-level-design)
- [Deep Dives](#deep-dives)
- [What is Expected at Each Level?](#what-is-expected-at-each-level)

---

## Understanding the Problem

**🔍 What is Facebook Post Search?**
Facebook Post Search allows users to search through billions of posts using keywords, with filters for author, date range, location, and more. Results are ranked by relevance and recency.

### Functional Requirements

We'''ll start by clarifying what functionality we need to build. The goal is to define the core features for searching and retrieving posts.

**Core Requirements**

1. Users should be able to search posts by keywords (e.g., "machine learning").
2. Support filters: author, date range, location, post type (text, photo, video).
3. Results should be ranked by relevance and recency.
4. Support pagination for search results.
5. Search should work across public posts and posts visible to the searcher based on privacy settings.

**Below the line (out of scope)**

- Creating or editing posts.
- Friend suggestions or graph traversal.
- Ads or sponsored content in search results.
- Image/video content search (focus on text search).

> **💡 Tip**: This problem focuses on **search infrastructure** and **relevance ranking** at scale. The key challenges are indexing billions of documents, handling high query load, and maintaining fresh results.

### Non-Functional Requirements

**Core Requirements**

1. **Low latency**: Search results should return in <200ms (p99).
2. **High availability**: 99.9% uptime for search service.
3. **Scale**: Support 3 billion users, 100 billion posts, 1M searches/second.
4. **Freshness**: New posts should be searchable within 1-5 minutes.
5. **Relevance**: Results should be ranked by relevance and recency.

**Below the line (out of scope)**

- Privacy enforcement implementation details.
- Content moderation.
- Multi-language support (assume English).

---

## Set Up

### Planning the Approach

For search problems at Facebook scale, we need:
1. A **search engine** optimized for text search (Elasticsearch)
2. An **indexing pipeline** to keep search index fresh
3. A **ranking system** to order results by relevance

We'''ll start with core entities, design the API, build a basic architecture, then optimize for scale.

### Defining the Core Entities

**Core Entities:**

- **Post**: A piece of content created by a user (text, media, metadata)
- **User**: An entity who creates posts and performs searches
- **SearchQuery**: A search request with keywords and filters
- **SearchResult**: A ranked list of posts matching the query

**Post Schema:**
```json
{
  "post_id": "post_123",
  "author_id": "user_456",
  "content": "Just learned about machine learning! Amazing stuff.",
  "created_at": "2026-03-14T10:30:00Z",
  "location": {"city": "San Francisco", "country": "US"},
  "post_type": "text",
  "privacy": "public",
  "likes_count": 42,
  "comments_count": 8,
  "shares_count": 3
}
```

### Defining the API

**API 1: Search Posts**
```
GET /api/v1/posts/search?q={query}&author_id={author}&from={date}&to={date}&location={location}&type={type}&page={page}&limit={limit}

Response:
{
  "results": [
    {
      "post_id": "post_123",
      "author_id": "user_456",
      "author_name": "John Doe",
      "content_snippet": "...machine learning! Amazing...",
      "created_at": "2026-03-14T10:30:00Z",
      "relevance_score": 0.92,
      "engagement_score": 53
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total_results": 1500000,
    "has_more": true
  },
  "search_time_ms": 145
}
```

---

## High-Level Design

### Architecture Overview

```
┌──────────┐
│  Client  │
└─────┬────┘
      │
      ▼
┌─────────────────┐
│  Search API     │
│   Service       │
└────┬───────┬────┘
     │       │
     │       ▼
     │  ┌──────────────┐      ┌─────────────┐
     │  │ Privacy      │─────▶│  User Graph │
     │  │ Filter       │      │  Service    │
     │  └──────────────┘      └─────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│     Elasticsearch Cluster           │
│  (Distributed Search Engine)        │
│                                     │
│  Indices:                           │
│  - posts_2026_03 (current month)    │
│  - posts_2026_02 (last month)       │
│  - posts_older (older posts)        │
│                                     │
│  Sharded by post_id hash            │
│  Replicated 3x for availability     │
└──────────▲──────────────────────────┘
           │
           │ Indexing Pipeline
           │
┌──────────┴──────────┐
│  Post Service       │
│  (Source of Truth)  │
└──────────▲──────────┘
           │
    ┌──────┴──────┐
    │   Kafka     │
    │ post-events │
    └──────▲──────┘
           │
    ┌──────┴──────┐
    │  Post DB    │
    │ (Postgres)  │
    └─────────────┘
```

### Core Components

1. **Search API Service**: Receives search queries, applies filters, calls Elasticsearch
2. **Elasticsearch Cluster**: Distributed search engine with inverted indices
3. **Privacy Filter**: Ensures users only see posts they have permission to view
4. **Indexing Pipeline**: Kafka + consumers to index new posts in near-real-time
5. **Post Service**: Source of truth for posts (primary database)

### Data Flow: Search Query

1. User submits search query via **Search API Service**
2. API service validates query and applies rate limiting
3. Privacy Filter determines which posts user can see (based on friendships, privacy settings)
4. Query sent to **Elasticsearch** with privacy filters
5. Elasticsearch returns ranked results
6. API service enriches results (user names, thumbnails) and returns to client

### Data Flow: Post Indexing

1. New post created in **Post Service** → written to Postgres
2. Post event published to **Kafka** topic
3. **Indexing Consumer** reads from Kafka
4. Consumer transforms post into search document
5. Document indexed in **Elasticsearch**
6. Post becomes searchable within 1-5 minutes

---

## Deep Dives

### 1) Elasticsearch Index Design and Sharding

**Problem**: With 100 billion posts, we need efficient indexing and querying.

**Solution: Time-based Index Partitioning**

```json
// Index naming convention
posts_2026_03  // Current month (most queries)
posts_2026_02  // Last month
posts_2026_01  // 2 months ago
posts_2025_*   // Older posts aggregated by quarter
posts_archive  // Posts >2 years old

// Each index is sharded by post_id hash
// Current month: 100 shards (most write traffic)
// Older indices: fewer shards (read-only)
```

**Why time-based partitioning?**
- Most searches are for recent posts (80% of queries target last 30 days)
- Older indices are read-only (no writes, better compression)
- Easy to delete old data (drop old indices)
- Query routing: search recent indices first

**Index Mapping:**
```json
{
  "mappings": {
    "properties": {
      "post_id": {"type": "keyword"},
      "author_id": {"type": "keyword"},
      "content": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {"type": "keyword"}
        }
      },
      "created_at": {"type": "date"},
      "location": {"type": "geo_point"},
      "post_type": {"type": "keyword"},
      "privacy": {"type": "keyword"},
      "engagement_score": {"type": "float"},
      "visible_to_user_ids": {"type": "keyword"}
    }
  }
}
```

### 2) Privacy Filtering at Scale

**Problem**: Users should only see posts they have permission to view. With complex privacy settings (friends, friends-of-friends, custom lists), how do we filter efficiently?

**Naive Approach (Bad):**
- Query Elasticsearch for all matching posts
- For each result, check user graph to see if searcher has permission
- **Problem**: Too slow! Checking permissions for 10k results = 10k graph lookups

**Better Approach: Pre-computed Visibility Lists**

During indexing, compute and store which users can see each post:

```java
public class PostIndexer {
    
    public SearchDocument indexPost(Post post) {
        Set<String> visibleToUserIds = new HashSet<>();
        
        switch (post.getPrivacy()) {
            case PUBLIC:
                // Everyone can see, use wildcard
                visibleToUserIds.add("*");
                break;
                
            case FRIENDS:
                // Get all friends of author
                visibleToUserIds = userGraphService
                    .getFriends(post.getAuthorId());
                break;
                
            case FRIENDS_OF_FRIENDS:
                // Get friends + friends of friends (limit to 10k)
                visibleToUserIds = userGraphService
                    .getFriendsOfFriends(post.getAuthorId(), 10000);
                break;
                
            case CUSTOM:
                // Specific user list
                visibleToUserIds = post.getCustomVisibilityList();
                break;
        }
        
        // Add author (always can see own posts)
        visibleToUserIds.add(post.getAuthorId());
        
        return SearchDocument.builder()
            .postId(post.getPostId())
            .content(post.getContent())
            .authorId(post.getAuthorId())
            .createdAt(post.getCreatedAt())
            .visibleToUserIds(visibleToUserIds)
            .build();
    }
}
```

**Query with Privacy Filter:**
```json
{
  "query": {
    "bool": {
      "must": [
        {"match": {"content": "machine learning"}}
      ],
      "filter": [
        {"terms": {"visible_to_user_ids": ["user_789", "*"]}}
      ]
    }
  }
}
```

**Trade-offs:**
- ✅ Fast queries (privacy filtered at index level)
- ✅ No post-query permission checks
- ❌ Index size increases (storing user ID lists)
- ❌ Need to reindex when friendships change (can be async)

### 3) Relevance Ranking with BM25 and Engagement

**Problem**: How do we rank results? Pure text relevance isn'''t enough - we want recent, engaging posts.

**Ranking Formula:**
```
final_score = (text_relevance * 0.6) + (recency_score * 0.2) + (engagement_score * 0.2)
```

**Components:**

1. **Text Relevance** (BM25): Elasticsearch'''s default
   - Term frequency (how often keyword appears)
   - Inverse document frequency (how rare the keyword is)
   
2. **Recency Score**: Exponential decay
   ```
   recency_score = exp(-decay_rate * days_old)
   ```

3. **Engagement Score**: Normalized social signals
   ```
   engagement_score = log(1 + likes + 2*comments + 3*shares) / max_engagement
   ```

**Elasticsearch Query with Function Score:**
```json
{
  "query": {
    "function_score": {
      "query": {
        "bool": {
          "must": [{"match": {"content": "machine learning"}}],
          "filter": [{"terms": {"visible_to_user_ids": ["user_789", "*"]}}]
        }
      },
      "functions": [
        {
          "exp": {
            "created_at": {
              "scale": "30d",
              "decay": 0.5
            }
          },
          "weight": 0.2
        },
        {
          "field_value_factor": {
            "field": "engagement_score",
            "modifier": "log1p",
            "factor": 0.2
          }
        }
      ],
      "score_mode": "sum",
      "boost_mode": "sum"
    }
  }
}
```

### 4) Handling High Query Load (1M searches/second)

**Problem**: 1M searches/second is massive load. How do we scale?

**Solution Options:**

✅ **Query-level Caching**
- Cache popular queries ("coronavirus", "election 2026")
- Use Redis with 1-5 minute TTL
- Cache key: hash(query + filters + user_privacy_group)
- Hit rate: 20-30% for popular queries

```java
public class SearchService {
    
    public SearchResults search(SearchQuery query, User user) {
        String cacheKey = buildCacheKey(query, user.getPrivacyGroup());
        
        // Try cache first
        SearchResults cached = redis.get(cacheKey);
        if (cached != null) {
            metrics.counter("search.cache.hit").increment();
            return cached;
        }
        
        // Cache miss - query Elasticsearch
        SearchResults results = elasticsearch.search(query, user);
        
        // Cache if query is likely to repeat
        if (isPopularQuery(query)) {
            redis.setex(cacheKey, 180, results);  // 3-minute TTL
        }
        
        return results;
    }
}
```

✅ **Elasticsearch Cluster Scaling**
- **Sharding**: 1000+ shards across cluster
- **Replication**: 3 replicas per shard for read scaling
- **Node specialization**: 
  - Master nodes: cluster coordination
  - Data nodes: store shards
  - Coordinating nodes: route queries
- **Auto-scaling**: Add nodes during peak hours

✅ **Index Tiering (Hot/Warm/Cold)**
- **Hot tier**: Last 7 days (SSD, most queries)
- **Warm tier**: Last 90 days (SSD, moderate queries)
- **Cold tier**: Older posts (HDD, rare queries)

### 5) Maintaining Index Freshness

**Problem**: New posts should be searchable within 1-5 minutes.

**Indexing Pipeline:**

```
Post Creation
     ↓
Post Service → Postgres (write)
     ↓
Kafka Topic "post-created"
     ↓
Indexing Consumer (100+ instances)
     ↓
Bulk Indexing to Elasticsearch (batch 1000 posts/5 seconds)
     ↓
Index Refresh (every 30 seconds)
     ↓
Post Searchable!
```

**Implementation:**
```java
public class PostIndexingConsumer {
    private final List<Post> buffer = new ArrayList<>();
    private final int BATCH_SIZE = 1000;
    
    @KafkaListener(topics = "post-created")
    public void onPostCreated(Post post) {
        synchronized (buffer) {
            buffer.add(post);
            
            if (buffer.size() >= BATCH_SIZE) {
                flushToElasticsearch();
            }
        }
    }
    
    @Scheduled(fixedDelay = 5000)
    public void flushPeriodically() {
        synchronized (buffer) {
            if (!buffer.isEmpty()) {
                flushToElasticsearch();
            }
        }
    }
    
    private void flushToElasticsearch() {
        BulkRequest bulkRequest = new BulkRequest();
        
        for (Post post : buffer) {
            SearchDocument doc = transformPostToDocument(post);
            bulkRequest.add(new IndexRequest("posts_2026_03")
                .id(post.getPostId())
                .source(doc.toJson()));
        }
        
        elasticsearch.bulk(bulkRequest);
        buffer.clear();
    }
}
```

**Freshness guarantee:**
- Write to Kafka: <10ms
- Consumer batching: up to 5 seconds
- Bulk index: 1-2 seconds
- Index refresh: 30 seconds
- **Total: ~40 seconds** (well within 1-5 minute SLA)

### 6) Handling Updates and Deletes

**Problem**: Users edit or delete posts. How do we keep the search index in sync?

**Solution: Event-Driven Updates**

```
Post Update/Delete
     ↓
Post Service → Postgres (update/delete)
     ↓
Kafka Topic "post-updated" / "post-deleted"
     ↓
Indexing Consumer
     ↓
Update/Delete from Elasticsearch
```

**Handling Eventual Consistency:**
- User deletes post → still in search for 30-60 seconds
- When user clicks result: "Post no longer available"
- Acceptable trade-off for freshness vs consistency

---

## What is Expected at Each Level?

### Mid-Level

**Breadth vs. Depth**: 80% breadth, 20% depth.

**Key Expectations**:
- Recognize this is a search problem (use Elasticsearch or similar)
- Design basic indexing pipeline (write posts → search index)
- Understand need for relevance ranking (not just return all matches)
- Basic privacy filtering concept

**The Bar**: Define search API, explain Elasticsearch indices, show basic architecture with indexing pipeline.

### Senior

**Depth of Expertise**: 60% breadth, 40% depth.

**Key Expectations**:
- Deep knowledge of Elasticsearch internals (inverted indices, sharding, replication)
- Sophisticated ranking (BM25 + recency + engagement)
- Privacy filtering optimization (pre-compute vs post-filter)
- Capacity estimation and scaling strategies
- Index tiering and lifecycle management

**The Bar**: Optimize for 1M queries/second. Discuss trade-offs between index size, query latency, and freshness. Show understanding of distributed search challenges.

### Staff+

**Emphasis on Depth**: 40% breadth, 60% depth.

**Key Expectations**:
- Expert-level Elasticsearch knowledge (routing, circuit breakers, heap management)
- Novel optimizations (query-level caching, index warming, adaptive sharding)
- Privacy at scale (graph-based pre-computation, caching strategies)
- Operational excellence (monitoring, alerting, incident response)
- Cost optimization (tiering, compression, retention policies)

**The Bar**: Dive deep into 2-3 areas. Show unique insights about search at Facebook scale. Discuss real-world experiences with distributed search. Interviewer learns something new.

---

## Summary: Key Design Decisions

| Decision | Chosen | Why |
|----------|--------|-----|
| **Search Engine** | Elasticsearch | Best-in-class text search with inverted indices |
| **Indexing** | Kafka + bulk indexing | Near-real-time with batching for efficiency |
| **Sharding Strategy** | Time-based indices + hash sharding | Recent posts get more resources, easy lifecycle |
| **Privacy** | Pre-computed visibility lists | Fast query-time filtering without graph lookups |
| **Ranking** | BM25 + recency + engagement | Balance relevance, freshness, and popularity |
| **Caching** | Redis for popular queries | 20-30% hit rate reduces ES load |
| **Freshness** | 30-second index refresh | Balance searchability with indexing overhead |
| **Tiering** | Hot/Warm/Cold (SSD/SSD/HDD) | Cost optimization based on query patterns |

## Key Interview Talking Points

1. **"Inverted index"** — Core of text search: term → list of documents
2. **"BM25 algorithm"** — Elasticsearch'''s default relevance scoring
3. **"Time-based partitioning"** — Recent indices get more shards and faster hardware
4. **"Bulk indexing"** — Batch 1000 posts every 5 seconds for efficiency
5. **"Privacy pre-compute"** — Store visible_to_user_ids array at index time
6. **"Function score query"** — Combine text relevance with recency and engagement
7. **"Index refresh interval"** — Trade-off between freshness and indexing overhead
8. **"Coordinating nodes"** — Handle query routing and result aggregation
9. **"Circuit breakers"** — Protect cluster from expensive queries
10. **"Query-level caching"** — Redis cache for popular searches (20-30% hit rate)

---

**Created**: March 2026  
**Framework**: Hello Interview (Simplified)  
**Source**: https://www.hellointerview.com/learn/system-design/problem-breakdowns/fb-post-search  
**Estimated Interview Time**: 45-60 minutes
