# Design a Search Service for Enterprise Data (Office 365 Search) — Hello Interview Framework

> **Question**: Design a search engine for Office 365 content (emails, files, SharePoint, Teams messages) that indexes data and returns relevant results quickly with security trimming — showing results only to authorized users.
>
> **Asked at**: Microsoft, Google, Amazon, Elastic
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
1. **Unified Search**: Single search box queries across emails, files (OneDrive/SharePoint), Teams messages, calendar events, contacts. Federated results from multiple content sources.
2. **Full-Text Search**: Search body, subject, file content, metadata. Support phrase search, boolean operators, wildcards. Relevance-ranked results.
3. **Security Trimming**: Users only see results they have access to. Respect per-item permissions (file sharing, mailbox access, channel membership). Zero unauthorized data leaks.
4. **Faceted Filtering**: Filter by content type, date range, author, file type, site. Facet counts update with filters.
5. **Search Suggestions**: Typeahead / autocomplete as user types. Suggest people, files, sites based on user's activity.
6. **Ranking**: Relevance scoring combining text match, recency, user interaction signals (opened, shared, edited). Personalized ranking per user.

#### Nice to Have (P1)
- Extracted entities (people, dates, projects) from content
- Knowledge cards (show person/file details inline)
- Search analytics (what are people searching for, zero-result queries)
- Bookmarks (admin-curated promoted results)
- Acronym definitions
- Custom connectors (index external data sources — Salesforce, Jira)
- Natural language queries ("emails from Alice last week about Q1")

#### Below the Line (Out of Scope)
- Web search (Bing)
- Content creation/editing
- Compliance search (eDiscovery — separate system)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Indexed items** | 50+ billion documents | All M365 content |
| **Search QPS** | 100K queries/sec peak | Enterprise + consumer |
| **Search latency** | < 300ms p95 | Responsive search experience |
| **Index freshness** | < 5 minutes for new content | Near real-time |
| **Security trimming accuracy** | 100% (zero false positives) | Compliance requirement |
| **Availability** | 99.99% | Search is core experience |
| **Tenants** | 10M+ organizations | Multi-tenant |

### Capacity Estimation

```
Index:
  Total documents: 50B
  Average document size (indexed): 2 KB (extracted text + metadata)
  Total index size: 50B × 2 KB = 100 PB
  
  Per-tenant average: 5000 documents
  Large tenants: 100M+ documents
  
Queries:
  QPS avg: 50K, peak: 100K
  Average query terms: 3 words
  Average results returned: 20
  Filters applied: 60% of queries
  
Indexing:
  New/updated documents/day: 5B
  Indexing throughput: 58K docs/sec
  Index update latency: < 5 minutes
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  SearchIndex     │     │  IndexedDocument  │     │  AccessControlList│
│  (per tenant)    │     │                   │     │                    │
│ indexId          │     │ docId             │     │ docId              │
│ tenantId         │     │ tenantId          │     │ allowedUsers[]     │
│ contentSources[] │     │ sourceType (email/│     │ allowedGroups[]    │
│ documentCount    │     │  file/message/    │     │ deniedUsers[]      │
│ lastUpdated      │     │  event)           │     │ isPublic           │
│ sizeBytes        │     │ title             │     │ inheritedFrom      │
└─────────────────┘     │ body (extracted)  │     └───────────────────┘
                         │ author            │
┌─────────────────┐     │ createdAt         │     ┌───────────────────┐
│  SearchQuery     │     │ modifiedAt        │     │  RankingSignal     │
│                  │     │ url               │     │                    │
│ queryId          │     │ contentType       │     │ docId              │
│ userId           │     │ fileType          │     │ userId             │
│ tenantId         │     │ site/container    │     │ clickCount         │
│ queryText        │     │ acl (embedded)    │     │ viewCount          │
│ filters{}        │     │ entities[]        │     │ editCount          │
│ resultCount      │     │ rankingScore      │     │ shareCount         │
│ latencyMs        │     └──────────────────┘     │ recency            │
│ clickedResults[] │                                │ personalScore      │
└─────────────────┘                                └───────────────────┘
```

---

## 3️⃣ API Design

### Search Query
```
POST /api/v1/search/query
Authorization: Bearer <token>

Request:
{
  "queryText": "Q1 report",
  "contentSources": ["mailMessages", "driveItems", "chatMessages"],
  "filters": {
    "dateRange": { "from": "2025-01-01", "to": "2025-03-31" },
    "fileType": ["pdf", "pptx"],
    "author": "alice@contoso.com"
  },
  "from": 0,
  "size": 20,
  "aggregations": ["contentSource", "fileType", "author"]
}

Response: 200 OK
{
  "totalHits": 342,
  "results": [
    {
      "id": "drive_item_123",
      "title": "Q1 Report - Final.pptx",
      "snippet": "...revenue grew 15% in <mark>Q1</mark>...",
      "source": "driveItem",
      "author": "Alice Smith",
      "modifiedAt": "2025-03-15T10:00:00Z",
      "url": "https://contoso.sharepoint.com/sites/.../Q1Report.pptx",
      "score": 0.95
    }
  ],
  "aggregations": {
    "contentSource": { "driveItems": 200, "mailMessages": 120, "chatMessages": 22 },
    "fileType": { "pptx": 85, "pdf": 60, "docx": 55 },
    "author": { "alice@contoso.com": 45, "bob@contoso.com": 30 }
  }
}
```

### Search Suggestions (Typeahead)
```
GET /api/v1/search/suggest?q=Q1%20re&sources=all

Response: 200 OK
{
  "suggestions": [
    { "text": "Q1 Report", "type": "query" },
    { "text": "Q1 Revenue Analysis", "type": "document", "url": "..." },
    { "text": "Q1 Review Meeting", "type": "event" }
  ],
  "people": [
    { "name": "Alice (Q1 Report author)", "email": "alice@contoso.com" }
  ]
}
```

---

## 4️⃣ Data Flow

### Indexing Pipeline

```
Content Sources:
  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
  │ Exchange │ │OneDrive/│ │  Teams  │ │Calendar │
  │ (Email)  │ │SharePt  │ │ (Chat)  │ │(Events) │
  └────┬─────┘ └────┬────┘ └────┬────┘ └────┬────┘
       │             │           │           │
       └─────────────┴───────────┴───────────┘
                     │
                     ▼
         Change Notification / Change Feed
         (Graph subscriptions / webhooks)
                     │
                     ▼
┌──────────────────────────────────────────────────────┐
│              INDEXING PIPELINE                         │
│                                                      │
│  1. Content Crawler:                                 │
│     Fetch full content via Graph API                 │
│     (email body, file content, message text)         │
│                                                      │
│  2. Content Processor:                               │
│     ├── Extract text from files (PDF, DOCX, PPTX)   │
│     ├── Strip HTML from emails                       │
│     ├── Extract metadata (author, dates, file type)  │
│     ├── Entity extraction (people, orgs, dates)      │
│     └── Language detection                           │
│                                                      │
│  3. ACL Resolver:                                    │
│     ├── Resolve file permissions → list of user/group IDs │
│     ├── Resolve email access → mailbox owner         │
│     ├── Resolve Teams message → channel members      │
│     └── Embed ACL in document for query-time trimming│
│                                                      │
│  4. Indexer:                                         │
│     ├── Tokenize, stem, lowercase                    │
│     ├── Build inverted index                         │
│     ├── Update Elasticsearch index                   │
│     └── Near real-time: available within 5 minutes   │
└──────────────────────────────────────────────────────┘
```

---

## 5️⃣ High-Level Design

```
┌───────────────────────────────────────────────────────────────────┐
│                      SEARCH PLATFORM                               │
│                                                                    │
│  ┌───────────────┐                                                │
│  │ Search API    │ ← User queries                                 │
│  │ Gateway       │                                                │
│  └───────┬───────┘                                                │
│          │                                                         │
│  ┌───────┴────────────────────────────────────────────────┐      │
│  │           QUERY PROCESSING                              │      │
│  │                                                         │      │
│  │  1. Parse query → extract terms, filters, operators    │      │
│  │  2. Expand query (synonyms, spell check)               │      │
│  │  3. Fan-out to content-specific indexes                │      │
│  │  4. Security trim results (filter by user's ACLs)      │      │
│  │  5. Rank: text relevance + personalization signals      │      │
│  │  6. Merge results across sources → unified ranking     │      │
│  │  7. Generate snippets with highlights                   │      │
│  │  8. Compute facet aggregations                         │      │
│  └───────┬────────────────────────────────────────────────┘      │
│          │                                                         │
│  ┌───────┴────────────────────────────────────────────────┐      │
│  │           SEARCH INDEX CLUSTER (Elasticsearch)          │      │
│  │                                                         │      │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐    │      │
│  │  │ Emails  │ │ Files   │ │Messages │ │ Events  │    │      │
│  │  │ Index   │ │ Index   │ │ Index   │ │ Index   │    │      │
│  │  │         │ │         │ │         │ │         │    │      │
│  │  │Per-tenant│ │Per-tenant│ │Per-tenant│ │Per-tenant│    │      │
│  │  │sharding │ │sharding │ │sharding │ │sharding │    │      │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘    │      │
│  └────────────────────────────────────────────────────────┘      │
│                                                                    │
│  ┌──────────────────────────────────────────────────────┐        │
│  │         INDEXING PIPELINE                              │        │
│  │  Change Feed → Crawler → Processor → ACL → Indexer   │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                    │
│  ┌──────────────────────────────────────────────────────┐        │
│  │         RANKING & PERSONALIZATION                      │        │
│  │  Click signals, recency, user graph, file popularity  │        │
│  └──────────────────────────────────────────────────────┘        │
└───────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Security Trimming — Zero Unauthorized Results

**Problem**: A user searches "Q1 report" — there might be 1,000 matching documents, but the user only has access to 50 of them. We MUST show exactly those 50, with zero leakage. But checking permissions on 1,000 documents per query is slow.

**Solution: Pre-Computed ACLs Embedded in Index + Query-Time Filtering**

```
Two approaches (hybrid):

APPROACH 1 — Early Binding (ACL in index):
  At index time: resolve document permissions → embed in index document
  
  Index document:
  {
    "docId": "file_123",
    "title": "Q1 Report",
    "body": "Revenue grew 15%...",
    "acl_users": ["user_alice", "user_bob", "user_carol"],
    "acl_groups": ["group_finance", "group_executives"],
    "acl_everyone": false
  }
  
  At query time: add filter for current user's access:
  {
    "query": { "match": { "body": "Q1 report" } },
    "filter": {
      "bool": {
        "should": [
          { "term": { "acl_users": "user_alice" } },
          { "terms": { "acl_groups": ["group_finance", "group_hr", ...] } },
          { "term": { "acl_everyone": true } }
        ]
      }
    }
  }
  
  Pro: fast query (filter is part of ES query, uses bitsets)
  Con: ACL changes require re-indexing all affected documents
       Group membership changes → massive re-index

APPROACH 2 — Late Binding (post-query check):
  Query without ACL filter → get top 200 results
  For each result: check live permissions via Graph API
  Filter down to authorized results → return top 20
  
  Pro: always fresh permissions
  Con: slow (200 permission checks), may not fill page (need over-fetch)

HYBRID (best of both):
  1. Use early binding for MOST cases (embedded ACL in index)
  2. Background job: ACL sync pipeline
     - Listen for permission changes (file shared, group member added)
     - Update affected documents' ACL fields in index
     - Target: ACL freshness < 15 minutes
  3. For security-critical tenants: late binding fallback
     - Post-query permission check on top results
     - Catches edge cases where ACL is stale

Group Expansion (at query time):
  User Alice is member of: [finance, executives, project-alpha]
  Expand all nested groups → flat list of group IDs
  Cache in Redis: groups:alice → ["finance", "executives", "project-alpha", "all-employees"]
  TTL: 5 minutes (invalidate on group change event)
```

### Deep Dive 2: Relevance Ranking & Personalization

**Problem**: A generic text match returns thousands of results. How to rank them so the most useful result appears first — personalized to the searching user?

**Solution: Multi-Signal Scoring with Learning to Rank**

```
Ranking Signals:

1. TEXT RELEVANCE (BM25):
   Standard information retrieval scoring
   Considers: term frequency, inverse document frequency, field length
   Higher score for exact phrase match in title vs body

2. RECENCY:
   More recent documents score higher
   Decay function: recency_score = 1 / (1 + days_since_modified / 30)
   
3. PERSONALIZATION:
   Based on user's interaction graph:
   - Documents user recently opened → boost
   - Documents from user's frequent collaborators → boost
   - Documents in user's frequent sites/folders → boost
   - User's manager's documents → slight boost
   
   Signal: personalScore = f(interaction_history, org_proximity)

4. POPULARITY:
   Documents accessed by many people in the tenant → boost
   Shared widely → boost
   Many edits → actively maintained → boost

5. AUTHORITY:
   Author is a known expert on the topic → boost
   Document is in an official site (vs personal OneDrive) → boost

Combined Score:
  finalScore = w1 × BM25 + w2 × recency + w3 × personalScore 
             + w4 × popularity + w5 × authority

  Weights learned via Learning to Rank (LTR):
    - Training data: click-through logs (which results users click)
    - Model: LambdaMART (gradient boosted decision trees)
    - Re-trained weekly with fresh click data
    - A/B tested: compare new model vs current model on live traffic
```

### Deep Dive 3: Multi-Source Federated Search

**Problem**: Search spans emails, files, messages, and events — each stored in different backend systems. How to query all at once and merge results into a unified ranked list?

**Solution: Scatter-Gather with Unified Ranking**

```
Federated Search Architecture:

User query: "Q1 report"
        │
        ▼
  Query Coordinator:
    1. Parse query → query plan
    2. Fan-out to content-specific search services:
       ├── Email Search Service: search mailbox index
       ├── File Search Service: search OneDrive/SharePoint index
       ├── Chat Search Service: search Teams messages index
       └── Calendar Search Service: search events index
    3. Each returns top 50 results with scores + snippets
    4. Coordinator merges:
       - Normalize scores across sources (different scales)
       - Apply cross-source ranking:
         Score normalization: score_normalized = (score - min) / (max - min)
       - Interleave results: don't show 20 emails then 20 files
         → Show most relevant regardless of source
         → But ensure diversity: at least 1 result from each source in top 5
    5. Apply security trimming (if not already done per-source)
    6. Generate unified response with facet counts

Performance Optimization:
  - Parallel fan-out: all 4 sources queried simultaneously
  - Timeout: 500ms per source, coordinator returns partial results
  - Short-circuit: if Email returns 0 results in 50ms, don't wait for full timeout
  - Caching: cache common queries per user (LRU, TTL 60s)
  
  Timeline:
    T+0ms:   Fan-out to all 4 sources
    T+100ms: Email results return (fastest, indexed locally)
    T+150ms: File results return
    T+200ms: Chat results return  
    T+220ms: Calendar results return
    T+230ms: Merge + rank + return to client
    Total: < 300ms ✓
```
