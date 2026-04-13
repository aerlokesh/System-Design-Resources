# 🎯 Topic 26: Pagination

> **System Design Interview — Deep Dive**
> Comprehensive coverage of cursor-based vs offset-based pagination, keyset pagination, performance implications at scale, and how to implement pagination for infinite-scroll feeds.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Offset-Based Pagination](#offset-based-pagination)
3. [Cursor-Based Pagination (Keyset)](#cursor-based-pagination-keyset)
4. [Why Cursor Beats Offset at Scale](#why-cursor-beats-offset-at-scale)
5. [Handling Real-Time Inserts](#handling-real-time-inserts)
6. [Cursor Encoding](#cursor-encoding)
7. [Bidirectional Pagination](#bidirectional-pagination)
8. [Pagination for Different Access Patterns](#pagination-for-different-access-patterns)
9. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
10. [Common Interview Mistakes](#common-interview-mistakes)
11. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Pagination divides large result sets into pages for efficient retrieval. The two main approaches have fundamentally different performance characteristics at scale.

| Approach | Performance | Jumping | Real-Time Safe | Best For |
|---|---|---|---|---|
| **Offset** | O(N) — degrades with depth | ✅ (page 47) | ❌ (duplicates/skips) | Admin tables |
| **Cursor** | O(1) — constant at any depth | ❌ (sequential only) | ✅ (handles inserts) | Infinite scroll |

---

## Offset-Based Pagination

### How It Works
```
Page 1: SELECT * FROM tweets ORDER BY created_at DESC LIMIT 20 OFFSET 0
Page 2: SELECT * FROM tweets ORDER BY created_at DESC LIMIT 20 OFFSET 20
Page 3: SELECT * FROM tweets ORDER BY created_at DESC LIMIT 20 OFFSET 40
Page N: SELECT * FROM tweets ORDER BY created_at DESC LIMIT 20 OFFSET (N-1)*20
```

### The Performance Problem
```
Page 1   (OFFSET 0):     Scan 20 rows → return 20     → 2ms
Page 10  (OFFSET 180):   Scan 200 rows → skip 180, return 20 → 5ms
Page 100 (OFFSET 1980):  Scan 2000 rows → skip 1980, return 20 → 20ms
Page 500 (OFFSET 9980):  Scan 10,000 rows → skip 9980, return 20 → 200ms

The database SCANS and DISCARDS offset rows every time.
At page 500: 99.8% of scanned rows are thrown away.
```

### The Consistency Problem
```
Time T1: Page 1 loaded (tweets 1-20)
Time T2: New tweet posted (becomes tweet 0, shifts everything by 1)
Time T3: Page 2 loaded (tweets 20-39... but tweet 20 was already on page 1!)
  → User sees tweet 20 TWICE (duplicate)

Or: Tweet deleted between page loads → user SKIPS a tweet (never sees it)
```

### When Offset Is Acceptable
- **Admin dashboards**: Small datasets (< 10K rows), page jumping needed.
- **Search results**: Users rarely go past page 5.
- **Reports**: Paginating through a bounded, static dataset.

---

## Cursor-Based Pagination (Keyset)

### How It Works
```
Page 1: SELECT * FROM tweets WHERE user_id = ? 
        ORDER BY tweet_id DESC LIMIT 20

Response: { data: [...20 tweets...], next_cursor: "tweet_id_12345" }

Page 2: SELECT * FROM tweets WHERE user_id = ? AND tweet_id < 12345
        ORDER BY tweet_id DESC LIMIT 20

Response: { data: [...20 tweets...], next_cursor: "tweet_id_12325" }
```

### Why It's O(1)
```
The WHERE clause (tweet_id < 12345) uses an INDEX SEEK.
The database jumps directly to tweet_id 12345 in the B-tree.
Then reads the next 20 rows sequentially.

Whether you're on "page 1" or "page 10,000":
  Same operation: index seek + 20 sequential reads
  Same performance: ~2ms

No rows are scanned and discarded. Zero waste.
```

### API Design
```
Request:
  GET /api/v1/timeline?cursor=eyJpZCI6MTIzNDV9&limit=20

Response:
{
  "data": [...20 tweets...],
  "pagination": {
    "next_cursor": "eyJpZCI6MTIzMjV9",  // Base64 encoded
    "has_more": true
  }
}

Client passes next_cursor in the next request.
Opaque to client — client doesn't know or care what's inside.
```

---

## Why Cursor Beats Offset at Scale

### Performance Comparison
```
10 million tweets, fetch page at various depths:

Offset:
  Page 1     (OFFSET 0):       2ms
  Page 100   (OFFSET 1,980):   20ms
  Page 1,000 (OFFSET 19,980):  200ms
  Page 10,000(OFFSET 199,980): 2,000ms  ← UNACCEPTABLE

Cursor (keyset):
  Page 1:       2ms
  Page 100:     2ms
  Page 1,000:   2ms
  Page 10,000:  2ms  ← CONSTANT regardless of depth
```

### Consistency with Real-Time Inserts
```
Cursor-based:
  Cursor = "last seen tweet_id = 12345"
  New tweets get IDs > 12345 → they appear at the TOP of the feed
  Next page query: tweet_id < 12345 → unaffected by new inserts
  No duplicates, no skipped tweets

Offset-based:
  Offset = 20 (skip first 20)
  New tweet inserted → everything shifts by 1
  Page 2 starts at offset 20 → the 20th tweet was already on page 1
  → DUPLICATE
```

---

## Handling Real-Time Inserts

### New Tweets While Scrolling
```
User loads page 1 (tweets 100-81)
Alice posts a new tweet (tweet 101)
User loads page 2 (cursor: tweet_id < 81)
  → Returns tweets 80-61
  → Tweet 101 appears at the TOP when user scrolls back up
  → No duplicate, no gap in the scroll

This is exactly how Twitter/Instagram infinite scroll works.
```

### Pull-to-Refresh
```
User pulls to refresh:
  GET /api/v1/timeline?limit=20 (no cursor = start from newest)
  → Returns latest 20 tweets (including tweet 101)
  → Timeline updated with new content at the top
```

---

## Cursor Encoding

### Simple: Single Field
```
Cursor = tweet_id (monotonically decreasing for reverse chronological)
Encoded: Base64(JSON({"id": 12345})) → "eyJpZCI6MTIzNDV9"
```

### Compound: Multiple Fields
```
For non-unique sort keys (e.g., sort by like_count):
  Cursor = { "like_count": 42, "tweet_id": 12345 }
  
  Query: WHERE (like_count < 42) OR (like_count = 42 AND tweet_id < 12345)
  
  Tie-breaking: tweet_id ensures unique ordering even when like_count is the same.
```

### Why Opaque Cursors?
```
Client receives: cursor = "eyJpZCI6MTIzNDV9"
Client sends:    cursor = "eyJpZCI6MTIzNDV9"

Client doesn't know it's a tweet_id.
Server can change the cursor format without breaking clients.
Server can include additional data (shard hint, cache key) in the cursor.
```

---

## Bidirectional Pagination

### Forward and Backward
```
Forward (newer → older):
  GET /timeline?cursor=abc&direction=forward&limit=20
  Query: WHERE tweet_id < cursor ORDER BY tweet_id DESC LIMIT 20

Backward (older → newer):
  GET /timeline?cursor=abc&direction=backward&limit=20
  Query: WHERE tweet_id > cursor ORDER BY tweet_id ASC LIMIT 20
```

### Use Case
- Chat messages: Scroll up for older messages, new messages appear at bottom.
- Timeline: Scroll down for older, pull-to-refresh for newer.

---

## Pagination for Different Access Patterns

| Pattern | Approach | Cursor Key |
|---|---|---|
| **Timeline (chronological)** | Cursor | tweet_id DESC |
| **Search results** | Offset (first 5 pages) | Page number |
| **Leaderboard** | Cursor | (rank, user_id) |
| **Chat messages** | Cursor | message_id ASC/DESC |
| **Admin user list** | Offset | Page number (small dataset) |
| **Product listing** | Cursor | (sort_field, product_id) |
| **Notifications** | Cursor | notification_id DESC |
| **Analytics report** | Offset | Page number (bounded result) |

---

## Interview Talking Points & Scripts

### Cursor for Infinite Scroll
> *"I'd use cursor-based pagination for the infinite-scroll timeline. The client sends `GET /timeline?cursor=eyJpZCI6MTIzNDV9&limit=20`. Internally, the cursor decodes to `{id: 12345}`, and the query is `SELECT * FROM timeline WHERE tweet_id < 12345 ORDER BY tweet_id DESC LIMIT 20`. This is O(1) regardless of position — whether the user has scrolled through 20 tweets or 20,000. It also handles real-time inserts correctly: new tweets appear at the top without shifting the cursor."*

### Offset Trap
> *"Offset-based pagination is a trap at scale. The database still scans and discards 10,000 rows to find the 20 you want — query time grows linearly with page depth. At page 500, our Postgres query takes 200ms instead of 2ms. Cursor-based avoids this entirely by using a WHERE clause on an indexed column."*

---

## Common Interview Mistakes

### ❌ Using OFFSET for infinite-scroll feeds
### ❌ Not encoding the cursor (exposing internal IDs to client)
### ❌ Not handling compound sort keys (tie-breaking)
### ❌ Not mentioning real-time insert handling as a cursor advantage
### ❌ Using cursor for admin tables that need page jumping

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                  PAGINATION CHEAT SHEET                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CURSOR-BASED (KEYSET):                                      │
│    Performance: O(1) at any depth                            │
│    Real-time safe: No duplicates/skips with new inserts      │
│    Limitation: Sequential only (no page jumping)             │
│    Use: Infinite scroll, feeds, timelines, chat              │
│    Query: WHERE id < cursor ORDER BY id DESC LIMIT 20        │
│                                                              │
│  OFFSET-BASED:                                               │
│    Performance: O(N) — degrades with depth                   │
│    Real-time: Duplicates/skips with concurrent inserts       │
│    Advantage: Page jumping (go to page 47)                   │
│    Use: Admin tables, search results, small datasets         │
│    Query: ORDER BY col LIMIT 20 OFFSET 40                    │
│                                                              │
│  CURSOR FORMAT: Base64(JSON({"id": 12345}))                  │
│    Opaque to client. Server can change format freely.        │
│                                                              │
│  COMPOUND CURSOR: {"sort_field": 42, "id": 12345}           │
│    Tie-breaking with unique ID for non-unique sort keys      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
