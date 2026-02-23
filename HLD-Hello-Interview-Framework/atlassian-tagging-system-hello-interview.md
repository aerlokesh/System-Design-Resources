# Design a Tagging System for Atlassian Products - Hello Interview Framework

> **Question**: Design a unified tagging system for Atlassian products (JIRA tickets, Confluence documents, Bitbucket PRs) that allows users to add/remove/update tags on any content, click tags to view associated items across products, and see a dashboard of the top K most popular tags.
>
> **Asked at**: Atlassian
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
1. **Add/Remove Tags**: Users can attach one or more tags (e.g., `#bug`, `#oncall`, `#q3-roadmap`) to items across Jira issues, Confluence pages, and Bitbucket pull requests.
2. **Cross-Product Tag Search**: Clicking a tag shows all associated items across ALL Atlassian products — a unified view of every Jira issue, Confluence page, and Bitbucket PR with that tag.
3. **Top-K Popular Tags Dashboard**: A real-time dashboard showing the K most popular tags globally or within a workspace, updated as tags are added/removed.
4. **Tag Autocomplete**: When typing a tag, suggest existing tags matching the prefix (to encourage reuse and prevent duplicates like `#bug` vs `#Bug` vs `#bugs`).
5. **Workspace Scoping**: Tags are scoped to an Atlassian workspace (organization). A tag `#sprint-23` in Workspace A is different from `#sprint-23` in Workspace B.

#### Nice to Have (P1)
- Tag-based subscriptions/notifications ("notify me when anything is tagged `#production-incident`").
- Tag analytics (trending tags, tag usage over time, tag co-occurrence).
- Tag hierarchies/categories (group tags: `#frontend-bug` is a child of `#bug`).
- Bulk tagging (tag 50 Jira issues at once with `#sprint-backlog`).
- Tag permissions (only admins can create certain tags, e.g., `#approved`).

#### Below the Line (Out of Scope)
- Full-text search of content (just tag-based search).
- Creating/modifying the underlying Jira issues, Confluence pages, or Bitbucket PRs.
- Labels that are product-specific (Jira labels, Confluence labels) — we design the UNIFIED tag layer.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Tag Write Latency** | < 200ms for add/remove | Users expect instant feedback when tagging |
| **Tag Search Latency** | < 500ms for cross-product search | Tag click should return results quickly |
| **Top-K Freshness** | Updated within 30 seconds of tag change | Dashboard should reflect recent activity |
| **Availability** | 99.9% | Tagging is a secondary feature; slight downtime acceptable |
| **Consistency** | Strong for writes, eventual for reads/counts | Tag addition must be durable; counts can lag briefly |
| **Scale** | 500M items, 1B tag associations, 50M unique tags | Atlassian Cloud scale across all products |
| **Autocomplete Latency** | < 100ms | Must feel instant as user types |
| **Throughput** | 10K tag writes/sec, 50K tag reads/sec | Peak usage across all Atlassian products |

### Capacity Estimation

```
Items (taggable entities):
  Jira issues: 300M
  Confluence pages: 150M
  Bitbucket PRs: 50M
  Total items: 500M

Tags:
  Unique tags: 50M (across all workspaces)
  Average tags per item: 2
  Total tag associations (item ↔ tag): 500M × 2 = 1 billion
  
  Tag association row: item_id (8B) + tag_id (8B) + metadata (16B) = ~32 bytes
  Total association storage: 1B × 32 bytes = ~32 GB

Tag metadata:
  Per tag: tag_id (8B) + name (50B) + workspace_id (8B) + count (8B) + timestamps (16B) = ~90 bytes
  Total: 50M × 90 bytes = ~4.5 GB

Write traffic:
  Tag additions per day: 50M (tags added to items)
  Tag removals per day: 5M
  Peak writes/sec: ~1K sustained, 10K peak
  
Read traffic:
  Tag searches per day: 100M (click on a tag → see items)
  Autocomplete queries per day: 200M
  Top-K dashboard views per day: 20M
  Peak reads/sec: ~5K sustained, 50K peak

Storage summary:
  Tag associations: ~32 GB
  Tag metadata: ~4.5 GB
  Search index: ~50 GB (inverted index: tag → items)
  Total: ~90 GB (fits on a single large DB instance, but we shard for throughput)
```

---

## 2️⃣ Core Entities

### Entity 1: Tag
```java
public class Tag {
    private final String tagId;              // UUID
    private final String workspaceId;        // Scoped to workspace
    private final String name;               // Normalized: lowercase, trimmed, e.g., "bug"
    private final String displayName;        // Original casing: "Bug"
    private final long usageCount;           // Number of items with this tag (denormalized)
    private final Instant createdAt;
    private final String createdBy;          // User who first used this tag
    private final Instant lastUsedAt;
}
```

### Entity 2: Tag Association (the item ↔ tag link)
```java
public class TagAssociation {
    private final String associationId;      // UUID
    private final String tagId;              // Which tag
    private final String itemId;             // Which item (Jira issue, Confluence page, etc.)
    private final ItemType itemType;         // JIRA_ISSUE, CONFLUENCE_PAGE, BITBUCKET_PR
    private final String workspaceId;        // For scoping
    private final String taggedBy;           // Who added the tag
    private final Instant taggedAt;
}

public enum ItemType {
    JIRA_ISSUE,
    CONFLUENCE_PAGE,
    BITBUCKET_PR
}
```

### Entity 3: Taggable Item Reference
```java
public class TaggableItem {
    private final String itemId;             // External ID (e.g., "PROJ-1234" for Jira)
    private final ItemType itemType;
    private final String workspaceId;
    private final String title;              // Cached title for display in search results
    private final String url;                // Deep link to the item
    private final Instant lastModifiedAt;
    private final List<String> tagIds;       // Tags attached to this item
}
```

### Entity 4: Top-K Tag Entry
```java
public class TopKTagEntry {
    private final String tagId;
    private final String tagName;
    private final long usageCount;           // Number of items with this tag
    private final long recentUsageCount;     // Usage in last 24 hours (for trending)
    private final int rank;                  // Position in top-K
}
```

---

## 3️⃣ API Design

### 1. Add Tag to Item
```
POST /api/v1/tags/associate

Request:
{
  "item_id": "PROJ-1234",
  "item_type": "JIRA_ISSUE",
  "workspace_id": "ws_abc",
  "tag_name": "bug"
}

Response (200 OK):
{
  "association_id": "assoc_xyz789",
  "tag": {
    "tag_id": "tag_456",
    "name": "bug",
    "usage_count": 12543
  },
  "item_id": "PROJ-1234",
  "tagged_at": "2025-01-10T10:00:00Z"
}
```

> **Note**: If the tag doesn't exist yet, it's auto-created (tag-on-write). No separate "create tag" step needed.

### 2. Remove Tag from Item
```
DELETE /api/v1/tags/associate

Request:
{
  "item_id": "PROJ-1234",
  "item_type": "JIRA_ISSUE",
  "workspace_id": "ws_abc",
  "tag_name": "bug"
}

Response (200 OK):
{
  "removed": true,
  "tag": { "tag_id": "tag_456", "name": "bug", "usage_count": 12542 }
}
```

### 3. Get Items by Tag (Cross-Product Search)
```
GET /api/v1/tags/{tag_name}/items?workspace_id=ws_abc&item_type=ALL&page=1&page_size=20&sort=recent

Response (200 OK):
{
  "tag": { "tag_id": "tag_456", "name": "bug", "usage_count": 12543 },
  "items": [
    {
      "item_id": "PROJ-1234",
      "item_type": "JIRA_ISSUE",
      "title": "Login button not working on mobile",
      "url": "https://mycompany.atlassian.net/browse/PROJ-1234",
      "tagged_at": "2025-01-10T10:00:00Z",
      "tagged_by": "alice"
    },
    {
      "item_id": "page_789",
      "item_type": "CONFLUENCE_PAGE",
      "title": "Bug Triage Process",
      "url": "https://mycompany.atlassian.net/wiki/spaces/ENG/pages/789",
      "tagged_at": "2025-01-09T15:30:00Z",
      "tagged_by": "bob"
    },
    {
      "item_id": "pr_321",
      "item_type": "BITBUCKET_PR",
      "title": "Fix: Handle null pointer in auth module",
      "url": "https://bitbucket.org/mycompany/backend/pull-requests/321",
      "tagged_at": "2025-01-09T14:00:00Z",
      "tagged_by": "charlie"
    }
  ],
  "pagination": { "page": 1, "page_size": 20, "total": 12543, "has_next": true }
}
```

### 4. Get Tags for an Item
```
GET /api/v1/items/{item_id}/tags?workspace_id=ws_abc

Response (200 OK):
{
  "item_id": "PROJ-1234",
  "item_type": "JIRA_ISSUE",
  "tags": [
    { "tag_id": "tag_456", "name": "bug", "usage_count": 12543 },
    { "tag_id": "tag_789", "name": "oncall", "usage_count": 3421 },
    { "tag_id": "tag_101", "name": "q3-roadmap", "usage_count": 892 }
  ]
}
```

### 5. Tag Autocomplete
```
GET /api/v1/tags/autocomplete?workspace_id=ws_abc&prefix=bu&limit=10

Response (200 OK):
{
  "prefix": "bu",
  "suggestions": [
    { "tag_id": "tag_456", "name": "bug", "usage_count": 12543 },
    { "tag_id": "tag_502", "name": "build-failure", "usage_count": 4521 },
    { "tag_id": "tag_503", "name": "business-logic", "usage_count": 1203 },
    { "tag_id": "tag_504", "name": "budget", "usage_count": 567 }
  ]
}
```

### 6. Top-K Popular Tags
```
GET /api/v1/tags/top?workspace_id=ws_abc&k=20&period=all_time

Response (200 OK):
{
  "workspace_id": "ws_abc",
  "period": "all_time",
  "top_tags": [
    { "rank": 1, "name": "bug", "usage_count": 12543 },
    { "rank": 2, "name": "feature-request", "usage_count": 9876 },
    { "rank": 3, "name": "oncall", "usage_count": 8234 },
    ...
    { "rank": 20, "name": "tech-debt", "usage_count": 1234 }
  ],
  "last_updated": "2025-01-10T10:00:15Z"
}
```

---

## 4️⃣ Data Flow

### Flow 1: Adding a Tag to an Item (Write Path)
```
1. User clicks "Add Tag" on Jira issue PROJ-1234, types "bug"
   ↓
2. Client calls POST /api/v1/tags/associate { item_id: "PROJ-1234", tag_name: "bug" }
   ↓
3. Tag Service:
   a. Normalize tag name: lowercase, trim → "bug"
   b. Lookup tag in DB: does "bug" exist in this workspace?
      - Yes → get tag_id
      - No → create new tag row, assign tag_id
   c. Create TagAssociation row: (tag_id, item_id, item_type, workspace_id, ...)
   d. If association already exists → return 409 Conflict (idempotent)
   ↓
4. Async side-effects (via Change Data Capture / event):
   a. Increment tag usage_count (counter update)
   b. Update search index: add item_id to inverted index for tag "bug"
   c. Update Top-K: notify streaming processor of count change
   d. Invalidate autocomplete cache for prefix "bu", "bug"
   ↓
5. Return success to client with tag info
```

### Flow 2: Cross-Product Tag Search (Read Path)
```
1. User clicks tag "bug" → wants to see all items with this tag
   ↓
2. Client calls GET /api/v1/tags/bug/items?workspace_id=ws_abc&page=1
   ↓
3. Tag Service:
   a. Lookup tag_id for "bug" in workspace
   b. Query search index: tag_id → list of (item_id, item_type, tagged_at)
   c. Apply filters: item_type, sort order, pagination
   d. Hydrate items: fetch title, URL from each product's API/cache
      - Jira items → Jira metadata cache
      - Confluence items → Confluence metadata cache
      - Bitbucket items → Bitbucket metadata cache
   ↓
4. Return unified list of items across all products
```

### Flow 3: Top-K Popular Tags (Streaming Path)
```
1. Every tag add/remove emits an event to Kafka: { tag_id, workspace_id, delta: +1/-1 }
   ↓
2. Stream Processor (Flink):
   a. Maintain per-workspace count: tag_id → current_count
   b. Maintain per-workspace top-K: sorted set of (tag_id, count)
   c. On each event: update count, check if tag enters/exits top-K
   ↓
3. Top-K result written to Redis: key = "topk:{workspace_id}", value = sorted set
   ↓
4. Dashboard reads from Redis: O(1) lookup, < 10ms
   ↓
5. Freshness: < 30 seconds from tag event to updated dashboard
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      ATLASSIAN PRODUCT CLIENTS                              │
│                                                                              │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐                         │
│  │   JIRA    │  │  CONFLUENCE   │  │  BITBUCKET   │                         │
│  │  Issue UI │  │   Page UI     │  │   PR UI      │                         │
│  │           │  │               │  │              │                         │
│  │ [+tag]    │  │ [+tag]        │  │ [+tag]       │                         │
│  └────┬──────┘  └──────┬────────┘  └──────┬───────┘                         │
│       │                │                   │                                 │
└───────┼────────────────┼───────────────────┼─────────────────────────────────┘
        │                │                   │
        └────────────────┼───────────────────┘
                         │  All products call unified Tag API
                         ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                         TAG SERVICE (API Layer)                              │
│                         (Stateless, horizontally scaled)                     │
│                                                                              │
│  Endpoints:                                                                  │
│  • POST /tags/associate        (add tag to item)                            │
│  • DELETE /tags/associate      (remove tag from item)                       │
│  • GET /tags/{name}/items      (cross-product search)                       │
│  • GET /items/{id}/tags        (get tags for item)                          │
│  • GET /tags/autocomplete      (prefix search)                              │
│  • GET /tags/top               (top-K dashboard)                            │
└──────────┬──────────────────────────┬──────────────────┬─────────────────────┘
           │                          │                  │
     ┌─────▼──────┐          ┌───────▼────────┐  ┌─────▼──────────┐
     │  TAG DB     │          │ SEARCH INDEX    │  │ TOP-K CACHE    │
     │  (Primary)  │          │ (Read-optimized)│  │ (Redis)        │
     │             │          │                 │  │                │
     │ PostgreSQL  │          │ Elasticsearch   │  │ Sorted sets    │
     │ or DynamoDB │          │ or custom       │  │ per workspace  │
     │             │          │ inverted index  │  │                │
     │ • tags      │          │                 │  │ topk:{ws_id}   │
     │ • assocs    │          │ tag → [items]   │  │ = [(tag, count)]│
     │ • items ref │          │ Pagination      │  │                │
     │             │          │ Filtering       │  │ Autocomplete:  │
     │             │          │ Sorting         │  │ ac:{ws}:{pfx}  │
     └──────┬──────┘          └────────────────┘  └────────────────┘
            │
            │ CDC (Change Data Capture)
            ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                            KAFKA                                            │
│                                                                              │
│  Topic: "tag-events"                                                        │
│  Events: TAG_ADDED, TAG_REMOVED                                             │
│  Payload: { tag_id, item_id, workspace_id, delta }                         │
└──────────┬──────────────────────────┬──────────────────────────────────────┘
           │                          │
     ┌─────▼──────────┐       ┌──────▼───────────────┐
     │ SEARCH INDEX    │       │ STREAM PROCESSOR     │
     │ UPDATER         │       │ (Flink / KStreams)    │
     │                 │       │                       │
     │ Consume events  │       │ • Update tag counts   │
     │ → update ES     │       │ • Maintain top-K      │
     │   inverted index│       │ • Detect trending     │
     │                 │       │ • Write to Redis      │
     └─────────────────┘       └───────────────────────┘


┌────────────────────────────────────────────────────────────────────────────┐
│                    ITEM METADATA CACHE                                       │
│                                                                              │
│  Cached titles and URLs from each product (for search result hydration)    │
│  • Jira: issue titles, project keys, URLs                                  │
│  • Confluence: page titles, space names, URLs                              │
│  • Bitbucket: PR titles, repo names, URLs                                  │
│                                                                              │
│  Source: each product publishes item metadata to a shared cache (Redis)    │
│  TTL: 1 hour (stale titles acceptable; deep links always work)            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Tag Service** | API layer: CRUD operations, routing, validation | Java/Node.js on K8s | 10-50 stateless pods |
| **Tag DB** | Source of truth for tags and associations | PostgreSQL (or DynamoDB) | Sharded by workspace_id |
| **Search Index** | Inverted index: tag → items (with filtering/pagination) | Elasticsearch | 3-node cluster, sharded by workspace |
| **Top-K Cache** | Real-time popular tags per workspace | Redis Sorted Sets | Single cluster, ~1 GB |
| **Kafka** | Event stream for async processing (CDC) | Apache Kafka | 1 topic, 10 partitions |
| **Stream Processor** | Aggregate counts, maintain top-K, trending | Flink / Kafka Streams | 3-5 nodes |
| **Item Metadata Cache** | Cached titles/URLs from Jira, Confluence, Bitbucket | Redis | Shared, ~10 GB |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Data Model — Relational Schema for Tags

**The Problem**: We need a schema that supports: add/remove tag, get tags for item, get items by tag, and maintain usage counts. The many-to-many relationship between tags and items is the core data model challenge.

```sql
-- Tags table: one row per unique tag per workspace
CREATE TABLE tags (
    tag_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL,
    name            VARCHAR(100) NOT NULL,      -- Normalized: lowercase, trimmed
    display_name    VARCHAR(100) NOT NULL,      -- Original casing
    usage_count     BIGINT DEFAULT 0,           -- Denormalized count (updated async)
    created_at      TIMESTAMP DEFAULT NOW(),
    created_by      UUID,
    last_used_at    TIMESTAMP,
    
    UNIQUE (workspace_id, name)                 -- Tag names unique per workspace
);

CREATE INDEX idx_tags_workspace_name ON tags (workspace_id, name);
CREATE INDEX idx_tags_workspace_count ON tags (workspace_id, usage_count DESC);  -- For top-K

-- Tag Associations: the many-to-many link between tags and items
CREATE TABLE tag_associations (
    association_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_id          UUID NOT NULL REFERENCES tags(tag_id),
    item_id         VARCHAR(255) NOT NULL,       -- External ID (e.g., "PROJ-1234")
    item_type       VARCHAR(50) NOT NULL,        -- "JIRA_ISSUE", "CONFLUENCE_PAGE", etc.
    workspace_id    UUID NOT NULL,
    tagged_by       UUID NOT NULL,
    tagged_at       TIMESTAMP DEFAULT NOW(),
    
    UNIQUE (tag_id, item_id)                     -- Prevent duplicate tag on same item
);

CREATE INDEX idx_assoc_tag ON tag_associations (tag_id, tagged_at DESC);   -- Items by tag
CREATE INDEX idx_assoc_item ON tag_associations (item_id);                  -- Tags for item
CREATE INDEX idx_assoc_workspace ON tag_associations (workspace_id, tagged_at DESC);

-- Item metadata cache table (populated from product webhooks)
CREATE TABLE item_metadata (
    item_id         VARCHAR(255) PRIMARY KEY,
    item_type       VARCHAR(50) NOT NULL,
    workspace_id    UUID NOT NULL,
    title           VARCHAR(500),
    url             VARCHAR(1000),
    last_modified   TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT NOW()
);
```

**Why this schema works**:
```
Query: "Get all items with tag 'bug' in workspace X" (cross-product search)
  → SELECT ta.*, im.title, im.url 
    FROM tag_associations ta 
    JOIN item_metadata im ON ta.item_id = im.item_id
    WHERE ta.tag_id = (SELECT tag_id FROM tags WHERE workspace_id = X AND name = 'bug')
    ORDER BY ta.tagged_at DESC
    LIMIT 20 OFFSET 0;
  → Uses idx_assoc_tag index → efficient

Query: "Get all tags for item PROJ-1234"
  → SELECT t.* FROM tags t 
    JOIN tag_associations ta ON t.tag_id = ta.tag_id
    WHERE ta.item_id = 'PROJ-1234';
  → Uses idx_assoc_item index → efficient

Query: "Top 20 tags in workspace X"
  → SELECT * FROM tags WHERE workspace_id = X ORDER BY usage_count DESC LIMIT 20;
  → Uses idx_tags_workspace_count index → efficient
  → For real-time top-K, read from Redis instead
```

---

### Deep Dive 2: Search Index — Cross-Product Tag Search with Elasticsearch

**The Problem**: When a user clicks a tag, we need to return items from ALL three products (Jira, Confluence, Bitbucket) in a single unified list, with filtering by product type, sorting, and pagination.

```java
public class TagSearchService {

    /**
     * Elasticsearch index design:
     * 
     * One index per workspace (for isolation and performance).
     * Each document in the index represents a tag_association (not an item or tag).
     * 
     * This allows us to:
     * - Search by tag name (exact match)
     * - Filter by item_type
     * - Sort by tagged_at (most recent first)
     * - Paginate efficiently
     * - Aggregate by item_type (show counts per product)
     */
    
    // ES document structure:
    // {
    //   "tag_name": "bug",
    //   "tag_id": "tag_456",
    //   "item_id": "PROJ-1234",
    //   "item_type": "JIRA_ISSUE",
    //   "item_title": "Login button not working",   // Denormalized for display
    //   "item_url": "https://...",
    //   "tagged_by": "alice",
    //   "tagged_at": "2025-01-10T10:00:00Z",
    //   "workspace_id": "ws_abc"
    // }
    
    public TagSearchResult searchByTag(String workspaceId, String tagName,
                                        ItemType filterType, int page, int pageSize) {
        SearchRequest request = new SearchRequest("tags_" + workspaceId);
        
        BoolQueryBuilder query = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("tag_name", tagName.toLowerCase()));
        
        if (filterType != null && filterType != ItemType.ALL) {
            query.filter(QueryBuilders.termQuery("item_type", filterType.name()));
        }
        
        request.source(new SearchSourceBuilder()
            .query(query)
            .sort("tagged_at", SortOrder.DESC)
            .from((page - 1) * pageSize)
            .size(pageSize)
            .aggregation(AggregationBuilders.terms("by_type").field("item_type")));
        
        SearchResponse response = esClient.search(request);
        
        List<TaggedItem> items = Arrays.stream(response.getHits().getHits())
            .map(hit -> mapToTaggedItem(hit.getSourceAsMap()))
            .toList();
        
        // Aggregations: how many items per product type
        Map<String, Long> typeCounts = extractTypeCounts(response.getAggregations());
        
        return new TagSearchResult(tagName, items, typeCounts,
            response.getHits().getTotalHits().value, page, pageSize);
    }
    
    /**
     * Index update: called when tag is added/removed (via Kafka consumer).
     * 
     * On TAG_ADDED: index a new document
     * On TAG_REMOVED: delete the document by (tag_id, item_id)
     */
    public void onTagEvent(TagEvent event) {
        String indexName = "tags_" + event.getWorkspaceId();
        
        if (event.getType() == TagEventType.TAG_ADDED) {
            // Fetch item metadata for denormalization
            ItemMetadata metadata = itemMetadataCache.get(event.getItemId());
            
            IndexRequest indexReq = new IndexRequest(indexName)
                .id(event.getTagId() + ":" + event.getItemId())
                .source(Map.of(
                    "tag_name", event.getTagName(),
                    "tag_id", event.getTagId(),
                    "item_id", event.getItemId(),
                    "item_type", event.getItemType(),
                    "item_title", metadata != null ? metadata.getTitle() : "",
                    "item_url", metadata != null ? metadata.getUrl() : "",
                    "tagged_by", event.getTaggedBy(),
                    "tagged_at", event.getTaggedAt(),
                    "workspace_id", event.getWorkspaceId()
                ));
            esClient.index(indexReq);
            
        } else if (event.getType() == TagEventType.TAG_REMOVED) {
            DeleteRequest deleteReq = new DeleteRequest(indexName)
                .id(event.getTagId() + ":" + event.getItemId());
            esClient.delete(deleteReq);
        }
    }
}
```

**Search performance**:
```
Index size per workspace (large company):
  500K tag associations × 300 bytes/doc = ~150 MB
  With inverted index overhead: ~500 MB

Query: "all items tagged 'bug', sorted by recency, page 1"
  → Term query on tag_name + sort by tagged_at + pagination
  → Elasticsearch: < 50ms for typical workspace
  
Aggregation: "how many Jira vs Confluence vs Bitbucket items?"
  → Terms aggregation on item_type field
  → Computed alongside the search query (same request)
```

---

### Deep Dive 3: Top-K Popular Tags — Streaming Aggregation

**The Problem**: We need a real-time dashboard showing the most popular tags. Counting via SQL `ORDER BY usage_count DESC LIMIT K` works for small scale but is expensive to run on every dashboard load at scale.

```java
public class TopKTagService {

    /**
     * Two approaches for Top-K:
     * 
     * Approach A: Redis Sorted Set (ZSET) — simple, effective for moderate scale
     *   - ZINCRBY topk:{workspace_id} 1 "bug"  (on tag add)
     *   - ZINCRBY topk:{workspace_id} -1 "bug"  (on tag remove)
     *   - ZREVRANGE topk:{workspace_id} 0 K-1 WITHSCORES  (get top K)
     *   - O(log N) for updates, O(K) for top-K query
     * 
     * Approach B: Streaming aggregation (Flink) — for high write throughput
     *   - Kafka events → Flink → maintain in-memory count map
     *   - Periodically flush top-K to Redis
     *   - Better for 10K+ writes/sec (avoids Redis hotspot on popular workspaces)
     * 
     * Our choice: HYBRID
     *   - Small/medium workspaces (< 1000 tags): Redis ZSET directly
     *   - Large workspaces (> 1000 tags): Flink → Redis
     */
    
    // Redis-based Top-K (for most workspaces)
    public void onTagAdded(String workspaceId, String tagName) {
        redis.zincrby("topk:" + workspaceId, 1, tagName);
    }
    
    public void onTagRemoved(String workspaceId, String tagName) {
        redis.zincrby("topk:" + workspaceId, -1, tagName);
        // Remove if count drops to 0
        Double score = redis.zscore("topk:" + workspaceId, tagName);
        if (score != null && score <= 0) {
            redis.zrem("topk:" + workspaceId, tagName);
        }
    }
    
    public List<TopKTagEntry> getTopK(String workspaceId, int k) {
        // ZREVRANGE returns top K by score (descending)
        Set<ZSetOperations.TypedTuple<String>> topTags = 
            redis.zrevrangeWithScores("topk:" + workspaceId, 0, k - 1);
        
        int rank = 1;
        List<TopKTagEntry> result = new ArrayList<>();
        for (var entry : topTags) {
            result.add(new TopKTagEntry(
                entry.getValue(),           // tag name
                entry.getScore().longValue(), // usage count
                rank++
            ));
        }
        return result;
    }
}
```

**Top-K performance**:
```
Redis ZINCRBY: O(log N) per update, where N = unique tags in workspace
  Typical workspace: N = 5000 tags → log(5000) ≈ 12 comparisons → < 0.1ms

Redis ZREVRANGE: O(K + log N) for top-K query
  K = 20, N = 5000 → < 0.1ms

Memory per workspace: 5000 tags × ~100 bytes = ~500 KB
Total for all workspaces: 1M workspaces × 500 KB = ~500 GB
  → Fits in Redis cluster (or shard large workspaces to Flink)

Freshness: synchronous Redis update → top-K reflects change immediately
  (vs Flink approach: 10-30 second delay)
```

---

### Deep Dive 4: Tag Normalization & Deduplication

**The Problem**: Users may type `#Bug`, `#bug`, `#BUG`, `#bugs`, or `# bug `. Without normalization, these create separate tags. We need canonical tag names while preserving display formatting.

```java
public class TagNormalizer {

    /**
     * Normalization rules:
     * 1. Trim whitespace
     * 2. Lowercase
     * 3. Replace spaces with hyphens: "frontend bug" → "frontend-bug"
     * 4. Remove special characters except hyphens and underscores
     * 5. Collapse multiple hyphens: "frontend--bug" → "frontend-bug"
     * 6. Max length: 100 characters
     * 
     * The NORMALIZED name is used for storage, lookup, and deduplication.
     * The DISPLAY name (first user's original input) is shown in the UI.
     */
    
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-z0-9\\-_]");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-{2,}");
    private static final int MAX_LENGTH = 100;
    
    public NormalizedTag normalize(String rawInput) {
        String displayName = rawInput.trim();
        
        String normalized = rawInput
            .trim()
            .toLowerCase()
            .replace(' ', '-')
            .replaceAll(SPECIAL_CHARS.pattern(), "")
            .replaceAll(MULTI_HYPHEN.pattern(), "-");
        
        if (normalized.length() > MAX_LENGTH) {
            normalized = normalized.substring(0, MAX_LENGTH);
        }
        
        // Remove leading/trailing hyphens
        normalized = normalized.replaceAll("^-+|-+$", "");
        
        if (normalized.isEmpty()) {
            throw new InvalidTagException("Tag name is empty after normalization: " + rawInput);
        }
        
        return new NormalizedTag(normalized, displayName);
    }
    
    /**
     * Tag-on-write: when a user types a tag:
     * 1. Normalize the input
     * 2. Check if normalized form already exists in this workspace
     *    - If yes: reuse existing tag (link to it)
     *    - If no: create new tag with this normalized name
     * 3. The FIRST user to create a tag sets the display_name
     */
    public Tag getOrCreateTag(String workspaceId, String rawTagName) {
        NormalizedTag normalized = normalize(rawTagName);
        
        // Atomic: find or create (INSERT ... ON CONFLICT DO NOTHING ... RETURNING)
        return tagRepository.findOrCreate(workspaceId, normalized.getName(), normalized.getDisplayName());
    }
}
```

**Normalization examples**:
```
Input               → Normalized      Display
─────               ──────────        ───────
"Bug"               → "bug"           "Bug"
"  frontend bug  "  → "frontend-bug"  "frontend bug"
"Q3-Roadmap"        → "q3-roadmap"    "Q3-Roadmap"
"#oncall"           → "oncall"        "#oncall"
"URGENT!!!"         → "urgent"        "URGENT!!!"
"  "                → ERROR (empty)
```

---

### Deep Dive 5: Tag Autocomplete — Fast Prefix Search

**The Problem**: As users type a tag, we need to suggest existing tags in < 100ms. This encourages tag reuse and prevents duplicates.

```java
public class TagAutocompleteService {

    /**
     * Autocomplete approaches:
     * 
     * Approach A: SQL LIKE query
     *   SELECT name, usage_count FROM tags 
     *   WHERE workspace_id = ? AND name LIKE 'bu%'
     *   ORDER BY usage_count DESC LIMIT 10;
     *   → Works for small workspaces, slow for large ones (LIKE with prefix is OK with index)
     * 
     * Approach B: Redis sorted sets with lexicographic ordering
     *   ZRANGEBYLEX ac:{workspace_id} "[bu" "[bu\xff" LIMIT 0 10
     *   → O(log N + K), very fast, but doesn't sort by popularity
     * 
     * Approach C: Pre-computed prefix → top-K map in Redis
     *   For each tag, store all prefixes: "b" → [...], "bu" → [...], "bug" → [...]
     *   → O(1) lookup, but high storage overhead
     * 
     * Our choice: PostgreSQL with trigram index (pg_trgm) for moderate scale
     *   + Redis cache for hot prefixes
     */
    
    // Primary: PostgreSQL with index on (workspace_id, name)
    public List<TagSuggestion> autocomplete(String workspaceId, String prefix, int limit) {
        // Check Redis cache first
        String cacheKey = "ac:" + workspaceId + ":" + prefix;
        List<TagSuggestion> cached = redis.get(cacheKey);
        if (cached != null) return cached;
        
        // Query DB
        List<TagSuggestion> suggestions = tagRepository.findByPrefix(
            workspaceId, prefix.toLowerCase(), limit);
        
        // Cache result (TTL: 5 minutes — invalidated on tag creation/deletion)
        redis.setex(cacheKey, 300, suggestions);
        
        return suggestions;
    }
    
    // Cache invalidation: when a new tag is created or deleted
    public void onTagCreatedOrDeleted(String workspaceId, String tagName) {
        // Invalidate all prefix caches for this tag's prefixes
        for (int i = 1; i <= tagName.length(); i++) {
            String prefix = tagName.substring(0, i);
            redis.del("ac:" + workspaceId + ":" + prefix);
        }
    }
}
```

---

### Deep Dive 6: Cross-Product Item Hydration — Getting Titles and URLs

**The Problem**: When displaying search results for a tag, we need the title and URL of each item. But items live in different products (Jira, Confluence, Bitbucket), each with their own API and data store.

```java
public class ItemHydrationService {

    /**
     * Challenge: a tag search might return 20 items from 3 different products.
     * We can't make 20 synchronous API calls to 3 products — too slow.
     * 
     * Solution: Item Metadata Cache
     * 
     * 1. Each product publishes item metadata events when items are created/updated:
     *    Jira: { item_id: "PROJ-1234", title: "Fix login bug", url: "...", type: "JIRA_ISSUE" }
     *    Confluence: { item_id: "page_789", title: "Bug Triage", url: "...", type: "CONFLUENCE_PAGE" }
     * 
     * 2. Metadata stored in Redis (or a lightweight DB):
     *    Key: item_meta:{item_id}
     *    Value: { title, url, item_type, workspace_id }
     *    TTL: 1 hour (stale titles are OK; deep links always work)
     * 
     * 3. On tag search: batch-fetch metadata from cache
     *    → 20 items × 1 Redis MGET = < 2ms
     *    → For cache misses: async fetch from product API (rare)
     */
    
    public Map<String, ItemMetadata> batchHydrate(List<String> itemIds) {
        // Step 1: Batch fetch from Redis
        List<String> keys = itemIds.stream()
            .map(id -> "item_meta:" + id)
            .toList();
        List<ItemMetadata> cached = redis.mget(keys);
        
        Map<String, ItemMetadata> results = new HashMap<>();
        List<String> cacheMisses = new ArrayList<>();
        
        for (int i = 0; i < itemIds.size(); i++) {
            if (cached.get(i) != null) {
                results.put(itemIds.get(i), cached.get(i));
            } else {
                cacheMisses.add(itemIds.get(i));
            }
        }
        
        // Step 2: For cache misses, fetch from product APIs (async, parallel)
        if (!cacheMisses.isEmpty()) {
            Map<String, ItemMetadata> fetched = fetchFromProducts(cacheMisses);
            results.putAll(fetched);
            
            // Backfill cache
            fetched.forEach((id, meta) -> redis.setex("item_meta:" + id, 3600, meta));
        }
        
        return results;
    }
    
    private Map<String, ItemMetadata> fetchFromProducts(List<String> itemIds) {
        // Group by product and fetch in parallel
        Map<ItemType, List<String>> byProduct = groupByProduct(itemIds);
        
        CompletableFuture<Map<String, ItemMetadata>> jiraFuture = 
            CompletableFuture.supplyAsync(() -> jiraClient.batchGetMetadata(byProduct.get(ItemType.JIRA_ISSUE)));
        CompletableFuture<Map<String, ItemMetadata>> confluenceFuture = 
            CompletableFuture.supplyAsync(() -> confluenceClient.batchGetMetadata(byProduct.get(ItemType.CONFLUENCE_PAGE)));
        CompletableFuture<Map<String, ItemMetadata>> bitbucketFuture = 
            CompletableFuture.supplyAsync(() -> bitbucketClient.batchGetMetadata(byProduct.get(ItemType.BITBUCKET_PR)));
        
        // Merge all results
        Map<String, ItemMetadata> all = new HashMap<>();
        all.putAll(jiraFuture.join());
        all.putAll(confluenceFuture.join());
        all.putAll(bitbucketFuture.join());
        return all;
    }
}
```

---

### Deep Dive 7: Consistency — Keeping Counts, Search Index, and Cache in Sync

**The Problem**: When a tag is added, we update the DB (source of truth), the search index (Elasticsearch), the usage count (Redis ZSET), and the autocomplete cache. These must stay consistent.

```java
public class TagConsistencyManager {

    /**
     * Consistency strategy: Write-Ahead + Eventual Consistency
     * 
     * 1. SYNCHRONOUS: Write to Tag DB (PostgreSQL) — source of truth
     *    → This is the commit point. If this succeeds, the tag is "added".
     * 
     * 2. ASYNCHRONOUS (via CDC/Kafka):
     *    a. Update Elasticsearch search index (eventual, ~1-5 seconds)
     *    b. Update Redis top-K sorted set (eventual, ~1 second)
     *    c. Invalidate autocomplete cache (eventual, ~1 second)
     *    d. Increment usage_count in tags table (async counter update)
     * 
     * If async updates fail:
     *    - Kafka retries automatically (at-least-once delivery)
     *    - Search index may be briefly stale → user retries and sees result
     *    - Count may be briefly wrong → corrected on next event or periodic reconciliation
     *    - Autocomplete cache → worst case: shows old suggestions for 5 min (TTL)
     * 
     * Periodic reconciliation (hourly):
     *    - Recount all tag usage from tag_associations table
     *    - Compare with cached counts in Redis
     *    - Correct any drift
     */
    
    @Transactional
    public TagAssociation addTag(String workspaceId, String itemId, 
                                  ItemType itemType, String tagName, String userId) {
        // Step 1: Normalize and find/create tag (synchronous, in DB transaction)
        Tag tag = tagNormalizer.getOrCreateTag(workspaceId, tagName);
        
        // Step 2: Create association (synchronous, same transaction)
        TagAssociation assoc = tagAssociationRepository.create(
            tag.getTagId(), itemId, itemType, workspaceId, userId);
        
        // Step 3: Publish event (async — picked up by consumers)
        // CDC on tag_associations table auto-publishes to Kafka
        // OR explicitly publish:
        kafkaProducer.send("tag-events", new TagEvent(
            TagEventType.TAG_ADDED, tag.getTagId(), tag.getName(),
            itemId, itemType, workspaceId));
        
        return assoc;
    }
    
    // Hourly reconciliation: fix any count drift
    @Scheduled(cron = "0 0 * * * *")
    public void reconcileCounts() {
        // For each workspace, recount top tags from DB and compare with Redis
        for (String workspaceId : getActiveWorkspaces()) {
            Map<String, Long> dbCounts = tagRepository.countAllTags(workspaceId);
            
            for (var entry : dbCounts.entrySet()) {
                Double rediScore = redis.zscore("topk:" + workspaceId, entry.getKey());
                long redisCount = rediScore != null ? rediScore.longValue() : 0;
                
                if (Math.abs(entry.getValue() - redisCount) > 0) {
                    // Fix drift
                    redis.zadd("topk:" + workspaceId, entry.getValue(), entry.getKey());
                }
            }
        }
    }
}
```

---

### Deep Dive 8: Workspace Scoping & Multi-Tenancy

**The Problem**: Tags are scoped per workspace. `#bug` in Workspace A is different from `#bug` in Workspace B. How do we ensure isolation while sharing infrastructure?

```java
public class WorkspaceScopingStrategy {

    /**
     * Multi-tenancy approaches:
     * 
     * Approach A: Shared tables with workspace_id column (chosen)
     *   - All workspaces in same DB tables
     *   - Every query includes WHERE workspace_id = ?
     *   - Indexes include workspace_id as leading column
     *   - Pros: simple, efficient, shared infrastructure
     *   - Cons: noisy neighbor risk, must prevent cross-workspace leaks
     * 
     * Approach B: Database-per-workspace
     *   - Each workspace gets its own DB/schema
     *   - Pros: perfect isolation
     *   - Cons: operational complexity (1M+ databases), connection pooling nightmare
     * 
     * Approach C: Shard-per-workspace
     *   - Group workspaces onto shards by consistent hashing
     *   - Each shard is a separate DB instance
     *   - Pros: balance isolation and efficiency
     *   - Cons: cross-shard queries impossible (fine for tags — always scoped)
     * 
     * Our choice: Shared tables (Approach A) for PostgreSQL + 
     *             Index-per-workspace for Elasticsearch +
     *             Key-prefix-per-workspace for Redis
     */
    
    // Every DB query includes workspace_id
    public List<Tag> getTagsByWorkspace(String workspaceId) {
        return jdbc.query(
            "SELECT * FROM tags WHERE workspace_id = ? ORDER BY usage_count DESC LIMIT 100",
            workspaceId);
    }
    
    // Elasticsearch: one index per workspace for isolation
    public String getSearchIndex(String workspaceId) {
        return "tags_" + workspaceId;
    }
    
    // Redis: key prefix per workspace
    public String getTopKKey(String workspaceId) {
        return "topk:" + workspaceId;
    }
    
    // CRITICAL: prevent cross-workspace data leaks
    // Every API request validates that the user belongs to the workspace
    public void validateWorkspaceAccess(String userId, String workspaceId) {
        boolean isMember = workspaceService.isMember(userId, workspaceId);
        if (!isMember) {
            throw new ForbiddenException("User " + userId + " is not a member of workspace " + workspaceId);
        }
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Data model** | Many-to-many with `tag_associations` junction table | Clean separation; supports arbitrary items and tags; efficient indexing both directions |
| **Search** | Elasticsearch (inverted index per workspace) | Cross-product search with filtering, sorting, pagination, aggregation; < 50ms queries |
| **Top-K** | Redis Sorted Set (ZINCRBY + ZREVRANGE) | O(log N) update, O(K) query; real-time freshness; simple implementation |
| **Tag normalization** | Lowercase + hyphen-separated + stored display name | Prevents duplicates (`Bug` vs `bug`); preserves original casing for display |
| **Autocomplete** | PostgreSQL prefix query + Redis cache (5 min TTL) | Leverages existing DB index; cache for hot prefixes; invalidated on tag creation |
| **Item hydration** | Shared Redis metadata cache (1h TTL) populated by product webhooks | Avoids cross-product API calls at query time; batch MGET for 20 items in < 2ms |
| **Consistency** | Synchronous DB write + async Kafka for search/cache | DB is source of truth; eventual consistency for read-optimized stores; hourly reconciliation |
| **Multi-tenancy** | Shared tables with workspace_id + per-workspace ES index | Simple infrastructure; workspace_id in all queries; ES index isolation |
| **Write path** | Tag-on-write (auto-create tags) | No friction; users don't need to pre-create tags; natural tag emergence |

## Interview Talking Points

1. **"Many-to-many via junction table with bidirectional indexes"** — `tag_associations` table with indexes on both (tag_id) and (item_id). Get tags for item = O(K) index scan. Get items by tag = paginated index scan.
2. **"Elasticsearch for cross-product search"** — One index per workspace. Each document = a tag association (denormalized with item title/URL). Term query + sort + pagination + aggregation in single request.
3. **"Redis ZINCRBY for real-time Top-K"** — O(log N) per tag event. ZREVRANGE for top-K in O(K). Immediate freshness. Hourly reconciliation against DB counts catches any drift.
4. **"Tag normalization prevents duplicates"** — Lowercase, hyphenate, strip specials. Stored as `name` (canonical) and `display_name` (original). Unique constraint on (workspace_id, name).
5. **"Item hydration via shared metadata cache"** — Products publish item metadata (title, URL) to Redis. Tag search does batch MGET for 20 items in < 2ms. 1-hour TTL; stale titles acceptable.
6. **"Sync write to DB, async to search/cache via Kafka"** — DB is commit point. CDC events update Elasticsearch, Redis counts, autocomplete cache. At-least-once Kafka delivery. Hourly reconciliation for safety.
7. **"Workspace scoping: shared tables + per-workspace indexes"** — workspace_id is leading column in all DB indexes. ES has separate index per workspace. Redis keys prefixed by workspace_id. All queries validated against workspace membership.
8. **"Tag-on-write: no pre-creation needed"** — First use of a tag auto-creates it. Reduces friction. INSERT ON CONFLICT for atomic find-or-create.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Hashtags (Twitter/Instagram)** | Same tagging concept | Hashtags are embedded in content; Atlassian tags are explicit metadata |
| **Labels (Gmail)** | Same labeling concept | Gmail labels are single-product; Atlassian tags span 3 products |
| **Bookmarks/Favorites** | Similar user-item association | Bookmarks are per-user; tags are shared across workspace |
| **Search Engine** | Same inverted index pattern | Search indexes full content; we index only tag associations |
| **Trending Topics** | Same Top-K pattern | Trending uses time-windowed counts; our Top-K uses all-time counts |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Tag DB** | PostgreSQL | DynamoDB | Higher write throughput; serverless; if workspace sharding maps well to partition keys |
| **Search** | Elasticsearch | PostgreSQL full-text + GIN index | Smaller scale (< 10M associations); avoid operational ES overhead |
| **Top-K** | Redis Sorted Set | Flink streaming aggregation | Very high write throughput (> 50K/sec); complex trending algorithms |
| **Autocomplete** | DB prefix query + Redis cache | Trie / prefix HashMap in memory | Very low latency (< 10ms); large tag corpus (> 1M per workspace) |
| **Event bus** | Kafka (CDC) | PostgreSQL LISTEN/NOTIFY | Simpler; single-node; no separate Kafka cluster needed |
| **Item cache** | Redis | Local in-process cache | If metadata rarely changes; avoid Redis dependency |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Atlassian Cloud Architecture (Multi-Tenancy Blog)
- Elasticsearch Inverted Index Design
- Redis Sorted Sets for Leaderboards and Top-K
- PostgreSQL Trigram Indexes (pg_trgm) for Prefix Search
- Change Data Capture Patterns (Debezium)
- Multi-Tenancy Data Isolation Strategies
