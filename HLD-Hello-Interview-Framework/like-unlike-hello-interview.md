# Design a Like/Unlike Feature - Hello Interview Framework

> **Question**: Design a system that allows users to favorite/unfavorite items, check their favorite status, view favorite counts, and see their list of favorited items. The system should handle high read traffic (1M QPS for status checks and counts) and significant write traffic (100K QPS for fav/unfav operations) while maintaining data consistency.
>
> **Asked at**: Roblox, Instagram, YouTube, Twitter, TikTok
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
1. **Like/Unlike (Toggle)**: Users can like or unlike an item (post, video, comment). The operation is idempotent — liking twice = still liked.
2. **Check Like Status**: Given a user and item, return whether the user has liked it. Must be fast (shown on every item render).
3. **Get Like Count**: Return the total number of likes for an item. Displayed on every post/video.
4. **Get User's Liked Items**: Return the list of items a user has liked (paginated, reverse chronological).

#### Nice to Have (P1)
- Get list of users who liked a specific item (paginated).
- Like count by time range (likes in the last 24 hours).
- Batch status check (check like status for multiple items at once — e.g., rendering a feed of 20 posts).
- Like analytics (which items are getting the most likes per hour).
- "Like" reactions (heart, thumbs up, fire, etc.) — multiple reaction types.

#### Below the Line (Out of Scope)
- Notification on like ("X liked your post") — assume separate notification service.
- Recommendation based on likes — assume separate ML pipeline.
- Content moderation of liked items.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Read QPS** | 1M QPS (status checks + counts) | Every item render = 1 status check + 1 count read |
| **Write QPS** | 100K QPS (like/unlike) | High engagement rate on popular platform |
| **Read Latency** | < 10ms (p99) for status check and count | Inline with feed rendering; must not block UI |
| **Write Latency** | < 100ms (p99) for like/unlike | User expects instant feedback |
| **Consistency** | Strong for status (user sees their own action immediately); eventual for counts (±few seconds) | User must see toggle immediately; count can lag |
| **Idempotency** | Like/unlike must be idempotent | Network retries, double-taps must not corrupt state |
| **Availability** | 99.99% | Core social feature; downtime = broken UX |
| **Durability** | No likes lost | Financial-grade durability for user actions |
| **Scalability** | 500M users, 5B items, 500B total likes | Instagram/YouTube scale |

### Capacity Estimation

```
Users & Items:
  Registered users: 500M
  Daily active users: 200M
  Total items (posts/videos): 5B
  New items per day: 50M

Likes:
  Likes per user per day: 20
  Total likes per day: 200M × 20 = 4B likes/day
  Write QPS: 4B / 86400 = ~46K QPS (sustained), peak 3x = ~140K QPS
  
  Total historical likes: 500B (all time)
  Average likes per item: 100 (power-law: most items have <10, viral items have millions)

Reads:
  Feed renders per user per day: 50 (each shows ~20 items)
  Status checks per render: 20 (one per item)
  Total status checks/day: 200M × 50 × 20 = 200B/day
  Read QPS: 200B / 86400 = ~2.3M QPS (sustained)
  
  Count reads: ~1M QPS (every item render needs count)

Storage:
  Per like record: user_id (8B) + item_id (8B) + timestamp (8B) + flags (4B) = ~28 bytes
  500B likes × 28 bytes = 14 TB (just the like records)
  
  Per item count: item_id (8B) + count (8B) = 16 bytes
  5B items × 16 bytes = 80 GB (fits in Redis)

  User liked-items index: 200B active likes × 16 bytes = 3.2 TB
```

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
│     User     │ *─* │   Like (Edge)    │ *─1 │     Item     │
├──────────────┤     ├──────────────────┤     ├──────────────┤
│ user_id (PK) │     │ user_id (FK)     │     │ item_id (PK) │
│ username     │     │ item_id (FK)     │     │ like_count   │
│ ...          │     │ created_at       │     │ ...          │
└──────────────┘     │ is_active        │     └──────────────┘
                     │ PK(user_id,      │
                     │    item_id)       │
                     └──────────────────┘
```

### Entity 1: Like (the core edge)
```sql
-- Primary table: one row per (user, item) pair
CREATE TABLE likes (
    user_id     BIGINT NOT NULL,
    item_id     BIGINT NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,  -- false = unliked (soft delete)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (user_id, item_id)
);

-- Index for "get all users who liked item X" (paginated)
CREATE INDEX idx_likes_item ON likes (item_id, created_at DESC) WHERE is_active = true;

-- Index for "get all items user X liked" (paginated)
CREATE INDEX idx_likes_user ON likes (user_id, created_at DESC) WHERE is_active = true;
```

### Entity 2: Like Count (denormalized counter)
```sql
-- Denormalized count per item (source of truth for display)
CREATE TABLE like_counts (
    item_id     BIGINT PRIMARY KEY,
    count       BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Entity 3: Like Event (for async processing)
```java
public class LikeEvent {
    private final String eventId;        // UUID, for idempotency
    private final long userId;
    private final long itemId;
    private final LikeAction action;     // LIKE or UNLIKE
    private final Instant timestamp;
    private final String clientRequestId; // Client-provided idempotency key
}

public enum LikeAction {
    LIKE, UNLIKE
}
```

---

## 3️⃣ API Design

### 1. Like an Item
```
POST /api/v1/items/{item_id}/like

Headers:
  Authorization: Bearer <JWT>
  X-Idempotency-Key: "client-uuid-123" (optional, for retry safety)

Response (200 OK):
{
  "item_id": "item_456",
  "liked": true,
  "like_count": 12346,
  "updated_at": "2025-01-10T19:30:00Z"
}

Response (409 Conflict — already liked):
{
  "item_id": "item_456",
  "liked": true,
  "like_count": 12346,
  "message": "Already liked"
}
```

### 2. Unlike an Item
```
DELETE /api/v1/items/{item_id}/like

Headers:
  Authorization: Bearer <JWT>

Response (200 OK):
{
  "item_id": "item_456",
  "liked": false,
  "like_count": 12345,
  "updated_at": "2025-01-10T19:30:01Z"
}
```

### 3. Check Like Status (Single)
```
GET /api/v1/items/{item_id}/like/status

Response (200 OK):
{
  "item_id": "item_456",
  "liked": true,
  "liked_at": "2025-01-10T18:00:00Z"
}
```

### 4. Batch Check Like Status (Feed Rendering)
```
POST /api/v1/likes/batch-status

Request:
{
  "item_ids": ["item_1", "item_2", "item_3", ..., "item_20"]
}

Response (200 OK):
{
  "statuses": {
    "item_1": { "liked": true, "liked_at": "2025-01-10T18:00:00Z" },
    "item_2": { "liked": false },
    "item_3": { "liked": true, "liked_at": "2025-01-09T12:00:00Z" },
    ...
  }
}
```

### 5. Get Like Count
```
GET /api/v1/items/{item_id}/like/count

Response (200 OK):
{
  "item_id": "item_456",
  "like_count": 12345,
  "approximate": false
}
```

### 6. Get User's Liked Items (Paginated)
```
GET /api/v1/users/me/likes?limit=20&cursor=eyJ0cyI6...

Response (200 OK):
{
  "items": [
    { "item_id": "item_789", "liked_at": "2025-01-10T19:30:00Z" },
    { "item_id": "item_456", "liked_at": "2025-01-10T18:00:00Z" },
    ...
  ],
  "next_cursor": "eyJ0cyI6MTcwNTE...",
  "has_more": true
}
```

---

## 4️⃣ Data Flow

### Flow 1: Like an Item
```
1. User taps ❤️ on item_456
   ↓
2. Client sends: POST /api/v1/items/item_456/like
   ↓
3. Like Service:
   a. Auth: extract user_id from JWT
   b. Idempotency check: SETNX Redis key "like:{user_id}:{item_id}" → if exists, return 200 (already liked)
   c. Write to DB: INSERT INTO likes (user_id, item_id, is_active) VALUES (..., ..., true)
                   ON CONFLICT (user_id, item_id) DO UPDATE SET is_active = true, updated_at = NOW()
   d. Update Redis cache:
      - SET like_status:{user_id}:{item_id} = "1" (TTL 24h)
      - INCR like_count:{item_id}
   e. Publish LikeEvent to Kafka topic "like-events"
   f. Return 200 OK with updated count
   ↓
4. Async Consumers (via Kafka):
   a. Counter Service → increment like_counts table (batch, every few seconds)
   b. Feed Service → update feed cache with new like count
   c. Analytics Service → track engagement metrics
   d. Notification Service → "X liked your post" (separate system)
   ↓
5. Total write latency: < 100ms (DB write + Redis update)
```

### Flow 2: Check Like Status (Feed Render)
```
1. User opens feed → client receives 20 item IDs
   ↓
2. Client sends: POST /api/v1/likes/batch-status { item_ids: [...20 items] }
   ↓
3. Like Service:
   a. For each item_id, check Redis: GET like_status:{user_id}:{item_id}
   b. Cache hit → return immediately
   c. Cache miss → query DB: SELECT item_id, is_active FROM likes 
                              WHERE user_id = ? AND item_id IN (?, ?, ...) AND is_active = true
   d. Populate cache for misses
   e. Return batch response
   ↓
4. Total read latency: < 10ms (Redis MGET for 20 keys)
```

### Flow 3: Get Like Count
```
1. Client requests count for item_456
   ↓
2. Like Service:
   a. Check Redis: GET like_count:{item_id}
   b. Cache hit → return count (< 1ms)
   c. Cache miss → query like_counts table → populate cache
   ↓
3. Count freshness: within 5 seconds of real-time (async counter updates)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                   │
│   [Mobile App]        [Web App]        [API Consumers]                │
│   POST /like   GET /batch-status   GET /count   GET /likes            │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │    API Gateway / LB      │
              │  • Auth, Rate Limiting   │
              │  • Request routing       │
              └────────────┬────────────┘
                           │
         ┌─────────────────┼──────────────────────┐
         ▼                 ▼                       ▼
┌─────────────────┐ ┌────────────────┐   ┌─────────────────┐
│  Like Service    │ │  Count Service │   │  List Service    │
│  (Write Path)   │ │  (Read Path)   │   │  (Read Path)     │
│  • Like/Unlike  │ │  • Get count   │   │  • User's likes  │
│  • Status check │ │  • Batch count │   │  • Paginated     │
│  • Batch status │ │                │   │  • Cursor-based  │
└───────┬─────────┘ └───────┬────────┘   └────────┬────────┘
        │                   │                      │
        ▼                   ▼                      ▼
┌──────────────────────────────────────────────────────────┐
│                    REDIS CLUSTER                          │
│                                                           │
│  like_status:{user}:{item} → "1"/"0"   (TTL 24h)        │
│  like_count:{item} → count              (TTL 1h)         │
│  user_likes:{user} → sorted set         (hot users)      │
│                                                           │
│  Memory: ~500 GB across 10-20 nodes                      │
│  QPS: handles 1M+ reads/sec                              │
└──────────────────────────┬───────────────────────────────┘
                           │ cache miss
                           ▼
┌──────────────────────────────────────────────────────────┐
│               POSTGRESQL (Sharded)                        │
│                                                           │
│  Shard key: user_id (for likes table)                    │
│  16-64 shards, each with read replicas                   │
│                                                           │
│  likes table: PK(user_id, item_id)                       │
│  like_counts table: PK(item_id) — separate shard cluster │
│                                                           │
│  Storage: ~14 TB for likes, 80 GB for counts             │
└──────────────────────────┬───────────────────────────────┘
                           │
        ┌──────────────────┘
        ▼
┌──────────────────────┐
│  Kafka               │
│  "like-events" topic │
│  • Counter updates   │
│  • Analytics         │
│  • Notifications     │
└──────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Like Service** | Write path: like/unlike + status checks | Java/Spring Boot | Stateless, 50+ pods |
| **Count Service** | Read path: serve like counts | Java/Spring Boot | Stateless, 30+ pods |
| **List Service** | Read path: user's liked items, paginated | Java/Spring Boot | Stateless, 20+ pods |
| **Redis Cluster** | Cache: status, counts, hot user lists | Redis 7+ Cluster | 10-20 nodes, 500 GB |
| **PostgreSQL** | Source of truth: likes + counts | PostgreSQL 16 | 16-64 shards |
| **Kafka** | Event stream for async processing | Apache Kafka | 50+ partitions |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Like/Unlike Write Path — Idempotency & Correctness

**The Problem**: A user taps ❤️, but the network is flaky. The client retries. We must ensure: (1) the like is recorded exactly once, (2) the count is incremented exactly once, (3) concurrent likes on the same item don't corrupt the count.

```java
public class LikeService {

    /**
     * Core like/unlike operation.
     * Idempotent: calling like() when already liked returns success (no-op).
     * Uses DB upsert + Redis cache update in a single flow.
     */
    
    private final JdbcTemplate jdbc;
    private final JedisCluster redis;
    private final KafkaTemplate<String, LikeEvent> kafka;
    
    @Transactional
    public LikeResponse like(long userId, long itemId, String idempotencyKey) {
        // Step 1: Idempotency check (fast path)
        String idempotencyRedisKey = "idem:like:" + idempotencyKey;
        if (idempotencyKey != null && redis.exists(idempotencyRedisKey)) {
            log.info("Duplicate like request detected: user={}, item={}", userId, itemId);
            return getCurrentStatus(userId, itemId);
        }
        
        // Step 2: Upsert to DB (atomic — handles concurrent requests)
        int rowsAffected = jdbc.update("""
            INSERT INTO likes (user_id, item_id, is_active, created_at, updated_at)
            VALUES (?, ?, true, NOW(), NOW())
            ON CONFLICT (user_id, item_id) DO UPDATE 
            SET is_active = true, updated_at = NOW()
            WHERE likes.is_active = false
            """, userId, itemId);
        
        // rowsAffected = 0 means already liked (no change) — idempotent
        boolean wasNewLike = (rowsAffected > 0);
        
        // Step 3: Update Redis cache
        String statusKey = "like_status:" + userId + ":" + itemId;
        redis.setex(statusKey, 86400, "1"); // TTL 24h
        
        if (wasNewLike) {
            // Increment count in Redis (approximate, will be corrected by async job)
            redis.incr("like_count:" + itemId);
            
            // Add to user's liked items sorted set (score = timestamp)
            redis.zadd("user_likes:" + userId, 
                        Instant.now().getEpochSecond(), String.valueOf(itemId));
        }
        
        // Step 4: Set idempotency key (5 min TTL — covers retries)
        if (idempotencyKey != null) {
            redis.setex(idempotencyRedisKey, 300, "1");
        }
        
        // Step 5: Publish event for async processing
        kafka.send(new ProducerRecord<>("like-events", String.valueOf(itemId),
            LikeEvent.builder()
                .userId(userId).itemId(itemId)
                .action(LikeAction.LIKE)
                .timestamp(Instant.now())
                .wasNewLike(wasNewLike)
                .build()));
        
        long currentCount = getCount(itemId);
        
        return LikeResponse.builder()
            .itemId(itemId).liked(true)
            .likeCount(currentCount)
            .build();
    }
    
    @Transactional
    public LikeResponse unlike(long userId, long itemId) {
        // Soft delete: set is_active = false (don't actually DELETE)
        int rowsAffected = jdbc.update("""
            UPDATE likes SET is_active = false, updated_at = NOW()
            WHERE user_id = ? AND item_id = ? AND is_active = true
            """, userId, itemId);
        
        boolean wasUnliked = (rowsAffected > 0);
        
        // Update Redis
        String statusKey = "like_status:" + userId + ":" + itemId;
        redis.setex(statusKey, 86400, "0");
        
        if (wasUnliked) {
            redis.decr("like_count:" + itemId);
            redis.zrem("user_likes:" + userId, String.valueOf(itemId));
        }
        
        kafka.send(new ProducerRecord<>("like-events", String.valueOf(itemId),
            LikeEvent.builder()
                .userId(userId).itemId(itemId)
                .action(LikeAction.UNLIKE)
                .timestamp(Instant.now())
                .wasNewUnlike(wasUnliked)
                .build()));
        
        return LikeResponse.builder()
            .itemId(itemId).liked(false)
            .likeCount(getCount(itemId))
            .build();
    }
}
```

**Why Soft Delete?**
```
Hard DELETE:
  ✗ Loses history (when did they unlike?)
  ✗ Tombstone overhead in PostgreSQL (dead tuples, vacuum)
  ✗ Re-liking requires INSERT (vs UPDATE)

Soft Delete (is_active = false):
  ✓ Preserves full history
  ✓ Re-liking is a simple UPDATE (upsert)
  ✓ Partial indexes (WHERE is_active = true) keep queries fast
  ✓ Analytics can track like/unlike patterns
```

---

### Deep Dive 2: Like Count — Sharded Counters for Hot Items

**The Problem**: A viral post by Taylor Swift gets 10M likes in an hour. If all 10M writes try to `UPDATE like_counts SET count = count + 1 WHERE item_id = ?`, that's a massive write-hot-spot — every write locks the same row.

**Solution**: Sharded counters. Split the count into N shards, each shard incremented independently. Read = SUM of all shards.

```java
public class ShardedCounterService {

    /**
     * Sharded counter for like counts.
     * Each item's count is split across N shards (default 16).
     * 
     * Write: randomly pick a shard, INCREMENT it
     * Read: SUM all shards (cached in Redis)
     * 
     * This spreads write contention across 16 rows instead of 1.
     */
    
    private static final int DEFAULT_SHARD_COUNT = 16;
    private static final int HOT_ITEM_SHARD_COUNT = 256; // For viral items
    
    // Schema:
    // CREATE TABLE like_count_shards (
    //     item_id     BIGINT NOT NULL,
    //     shard_id    SMALLINT NOT NULL,
    //     count       BIGINT NOT NULL DEFAULT 0,
    //     PRIMARY KEY (item_id, shard_id)
    // );
    
    /** Increment the count for an item (write path) */
    public void incrementCount(long itemId) {
        int shardCount = isHotItem(itemId) ? HOT_ITEM_SHARD_COUNT : DEFAULT_SHARD_COUNT;
        int shardId = ThreadLocalRandom.current().nextInt(shardCount);
        
        jdbc.update("""
            INSERT INTO like_count_shards (item_id, shard_id, count)
            VALUES (?, ?, 1)
            ON CONFLICT (item_id, shard_id)
            DO UPDATE SET count = like_count_shards.count + 1
            """, itemId, shardId);
    }
    
    /** Decrement the count for an item (unlike path) */
    public void decrementCount(long itemId) {
        int shardCount = isHotItem(itemId) ? HOT_ITEM_SHARD_COUNT : DEFAULT_SHARD_COUNT;
        int shardId = ThreadLocalRandom.current().nextInt(shardCount);
        
        jdbc.update("""
            UPDATE like_count_shards 
            SET count = GREATEST(count - 1, 0)
            WHERE item_id = ? AND shard_id = ?
            """, itemId, shardId);
    }
    
    /** Get the total count (read path — usually served from cache) */
    public long getCount(long itemId) {
        // Layer 1: Redis cache
        String cachedCount = redis.get("like_count:" + itemId);
        if (cachedCount != null) {
            return Long.parseLong(cachedCount);
        }
        
        // Layer 2: Sum all shards from DB
        Long totalCount = jdbc.queryForObject("""
            SELECT COALESCE(SUM(count), 0) FROM like_count_shards WHERE item_id = ?
            """, Long.class, itemId);
        
        // Cache the result (TTL 60 seconds)
        redis.setex("like_count:" + itemId, 60, String.valueOf(totalCount));
        
        return totalCount;
    }
    
    /**
     * Periodic reconciliation: recount from source of truth (likes table).
     * Handles any drift between sharded counters and actual like count.
     * Runs as a background job for items with recent activity.
     */
    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void reconcileCounts() {
        // Find items with recent like activity
        List<Long> recentItems = jdbc.queryForList("""
            SELECT DISTINCT item_id FROM likes
            WHERE updated_at > NOW() - INTERVAL '10 minutes'
            LIMIT 10000
            """, Long.class);
        
        for (Long itemId : recentItems) {
            Long exactCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM likes WHERE item_id = ? AND is_active = true
                """, Long.class, itemId);
            
            Long shardedCount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(count), 0) FROM like_count_shards WHERE item_id = ?
                """, Long.class, itemId);
            
            if (!exactCount.equals(shardedCount)) {
                log.warn("Count drift for item {}: sharded={}, exact={}", 
                    itemId, shardedCount, exactCount);
                
                // Reset shards to correct value
                jdbc.update("DELETE FROM like_count_shards WHERE item_id = ?", itemId);
                jdbc.update("""
                    INSERT INTO like_count_shards (item_id, shard_id, count)
                    VALUES (?, 0, ?)
                    """, itemId, exactCount);
                
                // Update Redis cache
                redis.setex("like_count:" + itemId, 60, String.valueOf(exactCount));
                
                metrics.counter("like.count.reconciled").increment();
            }
        }
    }
    
    /** Hot item detection: items with > 1000 likes/min get more shards */
    private boolean isHotItem(long itemId) {
        String rateKey = "like_rate:" + itemId;
        Long recentLikes = redis.incr(rateKey);
        if (recentLikes == 1) redis.expire(rateKey, 60);
        return recentLikes > 1000;
    }
}
```

**Sharded Counter Visualization**:
```
Normal item (item_123): 16 shards
  shard_0: 42    shard_1: 38    shard_2: 41    ...    shard_15: 39
  Total = SUM = 628

Hot item (taylor_post): 256 shards  
  shard_0: 4012  shard_1: 3998  shard_2: 4105  ...    shard_255: 3987
  Total = SUM = 1,023,456

Without sharding:
  Single row: UPDATE count = count + 1  → row lock contention → 100 writes/sec max
  
With 256 shards:
  256 rows: each gets ~40 writes/sec → no contention → 10,000 writes/sec
```

---

### Deep Dive 3: Batch Status Check — Feed Rendering Optimization

**The Problem**: When rendering a feed of 20 items, we need to check "did this user like each of these 20 items?" Making 20 sequential Redis calls or 20 DB queries is too slow.

```java
public class BatchLikeStatusService {

    /**
     * Batch check like status for multiple items at once.
     * Optimized for feed rendering: check 20 items in < 10ms.
     * 
     * Strategy:
     *   1. Redis MGET for all items (single round-trip)
     *   2. For cache misses, batch DB query
     *   3. Populate cache for misses
     */
    
    public Map<Long, LikeStatus> batchCheckStatus(long userId, List<Long> itemIds) {
        Map<Long, LikeStatus> results = new HashMap<>();
        
        // Step 1: Build Redis keys
        List<String> redisKeys = itemIds.stream()
            .map(id -> "like_status:" + userId + ":" + id)
            .toList();
        
        // Step 2: Single MGET call (1 round-trip instead of N)
        List<String> cachedValues = redis.mget(redisKeys.toArray(new String[0]));
        
        List<Long> cacheMisses = new ArrayList<>();
        
        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            String cached = cachedValues.get(i);
            
            if (cached != null) {
                results.put(itemId, new LikeStatus(itemId, "1".equals(cached)));
            } else {
                cacheMisses.add(itemId);
            }
        }
        
        // Step 3: Batch DB query for cache misses
        if (!cacheMisses.isEmpty()) {
            // Single query with IN clause (instead of N queries)
            String placeholders            String placeholders = cacheMisses.stream()
                .map(id -> "?").collect(Collectors.joining(","));
            
            List<Long> likedItemIds = jdbc.queryForList(
                "SELECT item_id FROM likes WHERE user_id = ? AND item_id IN (" 
                + placeholders + ") AND is_active = true",
                Long.class,
                Stream.concat(Stream.of(userId), cacheMisses.stream()).toArray());
            
            Set<Long> likedSet = new HashSet<>(likedItemIds);
            
            for (Long missedItemId : cacheMisses) {
                boolean liked = likedSet.contains(missedItemId);
                results.put(missedItemId, new LikeStatus(missedItemId, liked));
                
                // Populate cache for future requests
                String key = "like_status:" + userId + ":" + missedItemId;
                redis.setex(key, 86400, liked ? "1" : "0");
            }
        }
        
        return results;
    }
    
    /**
     * Batch count: get like counts for multiple items.
     * Used alongside batch status for feed rendering.
     */
    public Map<Long, Long> batchGetCounts(List<Long> itemIds) {
        Map<Long, Long> results = new HashMap<>();
        
        // Redis MGET for counts
        List<String> countKeys = itemIds.stream()
            .map(id -> "like_count:" + id).toList();
        List<String> cachedCounts = redis.mget(countKeys.toArray(new String[0]));
        
        List<Long> misses = new ArrayList<>();
        
        for (int i = 0; i < itemIds.size(); i++) {
            if (cachedCounts.get(i) != null) {
                results.put(itemIds.get(i), Long.parseLong(cachedCounts.get(i)));
            } else {
                misses.add(itemIds.get(i));
            }
        }
        
        // DB fallback for misses
        if (!misses.isEmpty()) {
            String placeholders = misses.stream().map(id -> "?").collect(Collectors.joining(","));
            jdbc.query(
                "SELECT item_id, COALESCE(SUM(count),0) as total FROM like_count_shards WHERE item_id IN ("
                + placeholders + ") GROUP BY item_id",
                misses.toArray(),
                (rs) -> {
                    long itemId = rs.getLong("item_id");
                    long total = rs.getLong("total");
                    results.put(itemId, total);
                    redis.setex("like_count:" + itemId, 60, String.valueOf(total));
                });
        }
        
        return results;
    }
}
```

**Performance: 20-Item Feed Render**:
```
Without batch optimization:
  20 × Redis GET (status)  = 20 round-trips × 0.5ms = 10ms
  20 × Redis GET (count)   = 20 round-trips × 0.5ms = 10ms
  Total: ~20ms

With batch optimization:
  1 × Redis MGET (20 status keys) = 1 round-trip × 1ms = 1ms
  1 × Redis MGET (20 count keys)  = 1 round-trip × 1ms = 1ms
  Total: ~2ms (10x faster)

With pipelining (alternative):
  Redis pipeline: 40 GET commands in 1 round-trip = 1-2ms
```

---

### Deep Dive 4: Database Sharding Strategy

**The Problem**: 500B like records × 28 bytes = 14 TB. A single PostgreSQL instance can't handle this volume or the write throughput (100K+ QPS).

```java
public class LikeShardRouter {

    /**
     * Shard routing for the likes table.
     * 
     * Shard key: user_id
     * Why user_id? Because the two most common queries are:
     *   1. "Did user X like item Y?" → needs user_id in shard key
     *   2. "Get all items user X liked" → scan single shard
     * 
     * Trade-off: "Get all users who liked item Y" requires scatter-gather
     * across all shards. This is acceptable because it's a P1 feature
     * (paginated, less frequent).
     */
    
    private static final int SHARD_COUNT = 64;
    
    /** Route a query to the correct shard */
    public int getShardId(long userId) {
        // Consistent hashing based on user_id
        return (int) (Math.abs(userId) % SHARD_COUNT);
    }
    
    /** Get the DataSource for a given shard */
    public DataSource getDataSource(long userId) {
        int shardId = getShardId(userId);
        return shardDataSources.get(shardId);
    }
    
    /**
     * Cross-shard query: "who liked item X?"
     * Scatter-gather across all shards, merge results.
     */
    public List<LikeRecord> getUsersWhoLiked(long itemId, int limit, Instant cursor) {
        List<CompletableFuture<List<LikeRecord>>> futures = new ArrayList<>();
        
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            DataSource ds = shardDataSources.get(shard);
            futures.add(CompletableFuture.supplyAsync(() -> 
                queryShardForItemLikes(ds, itemId, limit, cursor)));
        }
        
        // Wait for all shards, merge by created_at desc, take top N
        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .sorted(Comparator.comparing(LikeRecord::getCreatedAt).reversed())
            .limit(limit)
            .toList();
    }
}
```

**Sharding Layout**:
```
┌─────────────────────────────────────────────┐
│ likes table: sharded by user_id (64 shards) │
├─────────┬───────────────────────────────────┤
│ Shard 0 │ user_ids where userId % 64 == 0   │
│ Shard 1 │ user_ids where userId % 64 == 1   │
│ ...     │ ...                                │
│ Shard 63│ user_ids where userId % 64 == 63   │
├─────────┼───────────────────────────────────┤
│ Each    │ ~220 GB data + indexes             │
│ shard   │ ~1,600 QPS write, ~16K QPS read    │
│         │ 1 primary + 2 read replicas        │
└─────────┴───────────────────────────────────┘

like_count_shards: sharded by item_id (separate cluster)
  → Different access pattern: write-heavy, item-centric
  → 16 shards sufficient (80 GB total, 5 GB/shard)
```

---

### Deep Dive 5: Cache Consistency — Read-Your-Own-Writes

**The Problem**: User likes an item. The DB write succeeds, but the Redis cache update fails (network blip). Now the user refreshes and sees the item as "not liked" — their own action appears lost.

```java
public class ReadYourWritesService {

    /**
     * Guarantee: a user always sees their own most recent like/unlike action,
     * even if the Redis cache is stale.
     * 
     * Strategy: Write-through cache with session-level override.
     * 
     * On write:
     *   1. Write to DB (source of truth)
     *   2. Write to Redis (best-effort cache)
     *   3. Write to user's session store (guaranteed visible to this user)
     * 
     * On read:
     *   1. Check session store first (user's own recent actions)
     *   2. Check Redis cache
     *   3. Check DB
     */
    
    /** Write path: ensure user sees their own action */
    public void setUserAction(long userId, long itemId, boolean liked) {
        // Primary: DB (durable)
        // Already done in LikeService.like() / unlike()
        
        // Secondary: Redis cache (may fail, that's OK)
        try {
            String statusKey = "like_status:" + userId + ":" + itemId;
            redis.setex(statusKey, 86400, liked ? "1" : "0");
        } catch (Exception e) {
            log.warn("Redis cache update failed for user {} item {}", userId, itemId);
            // Don't fail the request — DB write already succeeded
        }
        
        // Tertiary: User session override (short-lived, high priority)
        String sessionKey = "session_override:" + userId + ":" + itemId;
        redis.setex(sessionKey, 60, liked ? "1" : "0"); // 60 second TTL
    }
    
    /** Read path: check session override first */
    public LikeStatus checkStatusWithRYOW(long userId, long itemId) {
        // Step 1: Check session override (user's own recent actions — highest priority)
        String sessionKey = "session_override:" + userId + ":" + itemId;
        String sessionOverride = redis.get(sessionKey);
        if (sessionOverride != null) {
            return new LikeStatus(itemId, "1".equals(sessionOverride));
        }
        
        // Step 2: Check general cache
        String statusKey = "like_status:" + userId + ":" + itemId;
        String cached = redis.get(statusKey);
        if (cached != null) {
            return new LikeStatus(itemId, "1".equals(cached));
        }
        
        // Step 3: DB fallback
        Boolean isActive = jdbc.queryForObject("""
            SELECT is_active FROM likes WHERE user_id = ? AND item_id = ?
            """, Boolean.class, userId, itemId);
        
        boolean liked = Boolean.TRUE.equals(isActive);
        redis.setex(statusKey, 86400, liked ? "1" : "0");
        
        return new LikeStatus(itemId, liked);
    }
}
```

**Cache Consistency Model**:
```
Write Flow:
  DB write ──→ Redis cache update ──→ Session override (60s TTL)
     ✓              may fail              ✓ (always set)

Read Flow:
  Session override? ──→ Redis cache? ──→ DB query
      (60s window)         (24h TTL)      (source of truth)

Guarantee: Within 60 seconds of a write, the user ALWAYS sees their own action
(even if Redis main cache is stale or failed to update).
After 60 seconds, the main cache should be correct (or will be refreshed on next miss).
```

---

### Deep Dive 6: Rate Limiting for Likes — Abuse Prevention

**The Problem**: Bots mass-liking content, users rapidly liking/unliking to game algorithms, or automated tools sending 10K likes/sec to inflate counts.

```java
public class LikeRateLimiter {

    /**
     * Multi-layer rate limiting for like operations.
     * 
     * Layer 1: Per-user global rate (max 100 likes/min)
     * Layer 2: Per-user per-item rate (max 5 toggles/min — prevent rapid like/unlike)
     * Layer 3: Per-item global rate (max 50K likes/min — detect coordinated attacks)
     */
    
    private static final int USER_LIMIT_PER_MINUTE = 100;
    private static final int USER_ITEM_TOGGLE_LIMIT = 5;
    private static final int ITEM_GLOBAL_LIMIT_PER_MINUTE = 50_000;
    
    public RateLimitResult checkAllowed(long userId, long itemId) {
        String minuteWindow = String.valueOf(Instant.now().getEpochSecond() / 60);
        
        // Layer 1: Per-user global
        String userKey = "rl:like:user:" + userId + ":" + minuteWindow;
        long userCount = redis.incr(userKey);
        if (userCount == 1) redis.expire(userKey, 120);
        
        if (userCount > USER_LIMIT_PER_MINUTE) {
            return RateLimitResult.rejected("Too many likes. Max " + USER_LIMIT_PER_MINUTE + "/min.");
        }
        
        // Layer 2: Per-user per-item toggle (prevent rapid like/unlike oscillation)
        String toggleKey = "rl:like:toggle:" + userId + ":" + itemId + ":" + minuteWindow;
        long toggleCount = redis.incr(toggleKey);
        if (toggleCount == 1) redis.expire(toggleKey, 120);
        
        if (toggleCount > USER_ITEM_TOGGLE_LIMIT) {
            return RateLimitResult.rejected("Too many toggles on this item.");
        }
        
        // Layer 3: Per-item global (detect coordinated botnet attacks)
        String itemKey = "rl:like:item:" + itemId + ":" + minuteWindow;
        long itemCount = redis.incr(itemKey);
        if (itemCount == 1) redis.expire(itemKey, 120);
        
        if (itemCount > ITEM_GLOBAL_LIMIT_PER_MINUTE) {
            metrics.counter("like.ratelimit.item_global").increment();
            return RateLimitResult.rejected("This item is receiving too many likes.");
        }
        
        return RateLimitResult.allowed();
    }
}
```

---

### Deep Dive 7: User's Liked Items List — Pagination at Scale

**The Problem**: "Show me all items I've liked" needs cursor-based pagination over potentially millions of likes per user, sorted by most recent first.

```java
public class UserLikedItemsService {

    /**
     * Get user's liked items with cursor-based pagination.
     * 
     * Two strategies based on user's like volume:
     *   1. Light users (< 10K likes): Redis sorted set
     *   2. Heavy users (> 10K likes): DB query with cursor
     */
    
    private static final int REDIS_LIST_THRESHOLD = 10_000;
    
    public PaginatedResult<LikedItem> getUserLikes(long userId, int limit, String cursor) {
        // Try Redis first (for active users with cached sorted set)
        Long redisSize = redis.zcard("user_likes:" + userId);
        
        if (redisSize != null && redisSize > 0 && redisSize < REDIS_LIST_THRESHOLD) {
            return getUserLikesFromRedis(userId, limit, cursor);
        }
        
        // Fall back to DB for heavy users or cache misses
        return getUserLikesFromDB(userId, limit, cursor);
    }
    
    private PaginatedResult<LikedItem> getUserLikesFromRedis(long userId, int limit, String cursor) {
        double maxScore = cursor != null ? decodeCursor(cursor) : Double.MAX_VALUE;
        
        // ZREVRANGEBYSCORE: get items with score < cursor, limit N
        Set<Tuple> items = redis.zrevrangeByScoreWithScores(
            "user_likes:" + userId,
            "-inf", String.valueOf(maxScore - 0.001),
            0, limit);
        
        List<LikedItem> results = items.stream()
            .map(t -> new LikedItem(Long.parseLong(t.getElement()), 
                                     Instant.ofEpochSecond((long) t.getScore())))
            .toList();
        
        String nextCursor = results.isEmpty() ? null :
            encodeCursor(results.get(results.size() - 1).getLikedAt().getEpochSecond());
        
        return new PaginatedResult<>(results, nextCursor, results.size() == limit);
    }
    
    private PaginatedResult<LikedItem> getUserLikesFromDB(long userId, int limit, String cursor) {
        Instant cursorTime = cursor != null ? decodeCursorTimestamp(cursor) : Instant.now();
        
        // Efficient cursor-based query using partial index
        List<LikedItem> items = jdbc.query("""
            SELECT item_id, created_at FROM likes
            WHERE user_id = ? AND is_active = true AND created_at < ?
            ORDER BY created_at DESC
            LIMIT ?
            """,
            new Object[]{userId, Timestamp.from(cursorTime), limit + 1},
            (rs, rowNum) -> new LikedItem(
                rs.getLong("item_id"),
                rs.getTimestamp("created_at").toInstant()));
        
        boolean hasMore = items.size() > limit;
        if (hasMore) items = items.subList(0, limit);
        
        String nextCursor = items.isEmpty() ? null :
            encodeCursorTimestamp(items.get(items.size() - 1).getLikedAt());
        
        return new PaginatedResult<>(items, nextCursor, hasMore);
    }
}
```

---

### Deep Dive 8: Cache Warming & Eviction Strategy

**The Problem**: With 500B total likes, we can't cache all like statuses. We need a smart eviction policy that keeps hot data in Redis while allowing cold data to be fetched from DB.

```java
public class LikeCacheManager {

    /**
     * Cache management for like data.
     * 
     * What to cache:
     *   1. Like status: like_status:{user}:{item} → "1"/"0" (TTL 24h)
     *      Only for recently active users × recently viewed items
     *   2. Like count: like_count:{item} → count (TTL 60s)
     *      For items shown in feeds (top 100M items)
     *   3. User likes list: user_likes:{user} → sorted set
     *      Only for active users, capped at 10K entries
     *   
     * Memory budget:
     *   Status: 200M active users × 200 items each × 50 bytes = 2 TB → too much!
     *   → Only cache for current session items (reduce to ~200 GB)
     *   Count: 100M items × 20 bytes = 2 GB → fits easily
     *   Lists: 50M active users × 1 KB avg = 50 GB → fits with eviction
     */
    
    /** Warm cache for a user's feed items */
    public void warmCacheForFeed(long userId, List<Long> itemIds) {
        // Batch check DB and populate Redis for all items in the feed
        List<Long> uncachedItems = itemIds.stream()
            .filter(id -> redis.get("like_status:" + userId + ":" + id) == null)
            .toList();
        
        if (uncachedItems.isEmpty()) return;
        
        // Single DB query for all uncached items
        Set<Long> likedItems = new HashSet<>(jdbc.queryForList("""
            SELECT item_id FROM likes 
            WHERE user_id = ? AND item_id IN (?) AND is_active = true
            """, Long.class, userId, uncachedItems));
        
        // Populate Redis with pipeline (single round-trip)
        Pipeline pipeline = redis.pipelined();
        for (Long itemId : uncachedItems) {
            String key = "like_status:" + userId + ":" + itemId;
            pipeline.setex(key, 86400, likedItems.contains(itemId) ? "1" : "0");
        }
        pipeline.sync();
    }
    
    /**
     * Proactive count cache warming: pre-load counts for trending items.
     * Runs periodically to ensure popular items always have cached counts.
     */
    @Scheduled(fixedRate = 30_000)
    public void warmPopularItemCounts() {
        // Get top 10K items by recent activity
        List<Long> popularItems = jdbc.queryForList("""
            SELECT item_id FROM likes 
            WHERE created_at > NOW() - INTERVAL '1 hour' AND is_active = true
            GROUP BY item_id ORDER BY COUNT(*) DESC LIMIT 10000
            """, Long.class);
        
        for (Long itemId : popularItems) {
            String key = "like_count:" + itemId;
            if (redis.ttl(key) < 10) { // About to expire
                Long count = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(count), 0) FROM like_count_shards WHERE item_id = ?
                    """, Long.class, itemId);
                redis.setex(key, 60, String.valueOf(count));
            }
        }
    }
}
```

**Cache Memory Breakdown**:
```
┌─────────────────────────┬──────────┬─────────────────────────────┐
│ Cache Type              │ Size     │ Policy                      │
├─────────────────────────┼──────────┼─────────────────────────────┤
│ Like status (per user×  │ ~200 GB  │ TTL 24h, warmed on feed    │
│   item)                 │          │ render                      │
│ Like count (per item)   │ ~2 GB    │ TTL 60s, warmed for popular│
│ User likes sorted set   │ ~50 GB   │ TTL 48h, capped at 10K     │
│ Session overrides       │ ~5 GB    │ TTL 60s                     │
│ Rate limit counters     │ ~10 GB   │ TTL 120s                    │
├─────────────────────────┼──────────┼─────────────────────────────┤
│ TOTAL                   │ ~270 GB  │ 10-20 Redis nodes           │
└─────────────────────────┴──────────┴─────────────────────────────┘
```

---

### Deep Dive 9: Async Counter Aggregation via Kafka

**The Problem**: Directly incrementing the DB counter on every like creates write amplification and lock contention. Instead, we batch counter updates via Kafka consumers.

```java
public class LikeCountAggregator {

    /**
     * Kafka consumer that batches like/unlike events and applies counter
     * updates in bulk. Instead of 100K individual DB updates/sec,
     * we aggregate into ~1K batch updates/sec.
     * 
     * Key insight: we don't need to update the count on every like.
     * Batch N events, compute net delta, apply once.
     */
    
    private static final int BATCH_SIZE = 100;
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(5);
    
    // Accumulator: item_id → net delta (likes - unlikes)
    private final ConcurrentMap<Long, AtomicLong> pendingDeltas = new ConcurrentHashMap<>();
    
    /** Kafka consumer: accumulate events */
    @KafkaListener(topics = "like-events", groupId = "counter-aggregator")
    public void onLikeEvent(LikeEvent event) {
        if (!event.isWasNewLike() && !event.isWasNewUnlike()) {
            return; // Idempotent no-op, skip
        }
        
        long delta = event.getAction() == LikeAction.LIKE ? 1 : -1;
        pendingDeltas.computeIfAbsent(event.getItemId(), k -> new AtomicLong())
            .addAndGet(delta);
    }
    
    /** Flush accumulated deltas to DB every 5 seconds */
    @Scheduled(fixedRate = 5_000)
    public void flushDeltas() {
        if (pendingDeltas.isEmpty()) return;
        
        // Snapshot and clear pending deltas
        Map<Long, Long> snapshot = new HashMap<>();
        pendingDeltas.forEach((itemId, delta) -> {
            long value = delta.getAndSet(0);
            if (value != 0) snapshot.put(itemId, value);
        });
        // Remove zeroed entries
        pendingDeltas.entrySet().removeIf(e -> e.getValue().get() == 0);
        
        if (snapshot.isEmpty()) return;
        
        // Batch update DB (single transaction)
        jdbc.batchUpdate("""
            INSERT INTO like_count_shards (item_id, shard_id, count)
            VALUES (?, 0, ?)
            ON CONFLICT (item_id, shard_id)
            DO UPDATE SET count = GREATEST(like_count_shards.count + ?, 0)
            """,
            snapshot.entrySet().stream()
                .map(e -> new Object[]{e.getKey(), Math.max(e.getValue(), 0), e.getValue()})
                .toList());
        
        // Update Redis cache for each item
        for (var entry : snapshot.entrySet()) {
            String key = "like_count:" + entry.getKey();
            if (entry.getValue() > 0) {
                redis.incrBy(key, entry.getValue());
            } else {
                redis.decrBy(key, Math.abs(entry.getValue()));
            }
        }
        
        metrics.counter("like.counter.flush.items").increment(snapshot.size());
        log.debug("Flushed counter deltas for {} items", snapshot.size());
    }
}
```

**Aggregation Benefits**:
```
Without aggregation (direct DB update per like):
  100K likes/sec → 100K DB writes/sec → row lock contention on hot items
  
With Kafka + 5-second batching:
  100K likes/sec → accumulated in memory
  Every 5 seconds: ~50K unique items × 1 batch update = 10K DB writes/sec
  10x reduction in DB write load
  
For a single viral item (10K likes/sec):
  Without: 10K competing writes to same row
  With: 1 batch write every 5 seconds with delta=50,000
```

---

### Deep Dive 10: Multi-Reaction Support (Heart, Thumbs Up, Fire)

**The Problem**: Beyond simple like/unlike, modern platforms support multiple reaction types (❤️, 👍, 🔥, 😂, 😮, 😢). Each user can have at most one reaction per item. Changing from ❤️ to 🔥 should decrement the heart count and increment the fire count.

```java
public class ReactionService {

    /**
     * Extended like system with multiple reaction types.
     * 
     * Schema changes:
     *   - likes table gets a "reaction_type" column
     *   - like_count_shards becomes reaction_count_shards (per type)
     *   - Cache keys include reaction type
     * 
     * Rule: A user can have AT MOST one reaction per item.
     * Changing reaction = atomic swap (decrement old, increment new).
     */
    
    public enum ReactionType {
        HEART, THUMBS_UP, FIRE, LAUGH, WOW, SAD
    }
    
    // Schema:
    // CREATE TABLE reactions (
    //     user_id         BIGINT NOT NULL,
    //     item_id         BIGINT NOT NULL,
    //     reaction_type   VARCHAR(20),          -- NULL = unreacted
    //     is_active       BOOLEAN DEFAULT true,
    //     created_at      TIMESTAMPTZ,
    //     updated_at      TIMESTAMPTZ,
    //     PRIMARY KEY (user_id, item_id)
    // );
    //
    // CREATE TABLE reaction_count_shards (
    //     item_id         BIGINT NOT NULL,
    //     reaction_type   VARCHAR(20) NOT NULL,
    //     shard_id        SMALLINT NOT NULL,
    //     count           BIGINT DEFAULT 0,
    //     PRIMARY KEY (item_id, reaction_type, shard_id)
    // );
    
    @Transactional
    public ReactionResponse react(long userId, long itemId, ReactionType newReaction) {
        // Step 1: Get current reaction (if any)
        ReactionRecord current = jdbc.queryForObject("""
            SELECT reaction_type, is_active FROM reactions
            WHERE user_id = ? AND item_id = ?
            """, new Object[]{userId, itemId}, reactionRowMapper);
        
        ReactionType oldReaction = (current != null && current.isActive()) 
            ? current.getReactionType() : null;
        
        // Step 2: Upsert new reaction
        jdbc.update("""
            INSERT INTO reactions (user_id, item_id, reaction_type, is_active, created_at, updated_at)
            VALUES (?, ?, ?, true, NOW(), NOW())
            ON CONFLICT (user_id, item_id) DO UPDATE
            SET reaction_type = ?, is_active = true, updated_at = NOW()
            """, userId, itemId, newReaction.name(), newReaction.name());
        
        // Step 3: Update counts (atomic swap)
        if (oldReaction != null && oldReaction != newReaction) {
            // Decrement old reaction count
            decrementReactionCount(itemId, oldReaction);
        }
        if (oldReaction != newReaction) {
            // Increment new reaction count
            incrementReactionCount(itemId, newReaction);
        }
        
        // Step 4: Update Redis cache
        String statusKey = "reaction:" + userId + ":" + itemId;
        redis.setex(statusKey, 86400, newReaction.name());
        
        // Invalidate count caches for affected types
        if (oldReaction != null) redis.del("reaction_count:" + itemId + ":" + oldReaction.name());
        redis.del("reaction_count:" + itemId + ":" + newReaction.name());
        
        // Step 5: Publish event
        kafka.send(new ProducerRecord<>("reaction-events", String.valueOf(itemId),
            ReactionEvent.builder()
                .userId(userId).itemId(itemId)
                .oldReaction(oldReaction).newReaction(newReaction)
                .build()));
        
        return getReactionSummary(itemId);
    }
    
    /** Get reaction summary: count per type */
    public ReactionSummary getReactionSummary(long itemId) {
        Map<ReactionType, Long> counts = new EnumMap<>(ReactionType.class);
        
        for (ReactionType type : ReactionType.values()) {
            String key = "reaction_count:" + itemId + ":" + type.name();
            String cached = redis.get(key);
            if (cached != null) {
                counts.put(type, Long.parseLong(cached));
            } else {
                Long count = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(count), 0) FROM reaction_count_shards
                    WHERE item_id = ? AND reaction_type = ?
                    """, Long.class, itemId, type.name());
                counts.put(type, count);
                redis.setex(key, 60, String.valueOf(count));
            }
        }
        
        long totalCount = counts.values().stream().mapToLong(Long::longValue).sum();
        
        return ReactionSummary.builder()
            .itemId(itemId)
            .totalCount(totalCount)
            .countsByType(counts)
            .build();
    }
}
```

**API Response for Multi-Reaction**:
```json
{
  "item_id": "item_456",
  "user_reaction": "FIRE",
  "total_count": 12345,
  "counts_by_type": {
    "HEART": 8500,
    "THUMBS_UP": 2100,
    "FIRE": 1200,
    "LAUGH": 400,
    "WOW": 100,
    "SAD": 45
  }
}
```

---

### Deep Dive 11: Observability & Operational Metrics

```java
public class LikeMetrics {
    // Write path
    Counter likesTotal       = Counter.builder("likes.total").tag("action", "like").register(registry);
    Counter unlikesTotal     = Counter.builder("likes.total").tag("action", "unlike").register(registry);
    Counter idempotentSkips  = Counter.builder("likes.idempotent_skip").register(registry);
    Timer   writeLatency     = Timer.builder("likes.write_latency_ms").register(registry);
    
    // Read path
    Timer   statusCheckLatency  = Timer.builder("likes.status_check_ms").register(registry);
    Timer   batchStatusLatency  = Timer.builder("likes.batch_status_ms").register(registry);
    Timer   countReadLatency    = Timer.builder("likes.count_read_ms").register(registry);
    Counter cacheHitStatus      = Counter.builder("likes.cache.hit").tag("type", "status").register(registry);
    Counter cacheMissStatus     = Counter.builder("likes.cache.miss").tag("type", "status").register(registry);
    Counter cacheHitCount       = Counter.builder("likes.cache.hit").tag("type", "count").register(registry);
    
    // Counter aggregation
    Gauge   pendingDeltaSize = Gauge.builder("likes.counter.pending_deltas", ...).register(registry);
    Counter counterFlushes   = Counter.builder("likes.counter.flushes").register(registry);
    Counter countReconciled  = Counter.builder("likes.counter.reconciled").register(registry);
    
    // Rate limiting
    Counter rateLimitRejected = Counter.builder("likes.ratelimit.rejected").register(registry);
    
    // Infrastructure
    Gauge   redisMemoryUsed  = Gauge.builder("likes.redis.memory_bytes", ...).register(registry);
    Gauge   dbConnectionPool = Gauge.builder("likes.db.connections.active", ...).register(registry);
    Gauge   kafkaLag         = Gauge.builder("likes.kafka.consumer_lag", ...).register(registry);
}
```

**Key Alerts**:
```yaml
alerts:
  - name: HighWriteLatency
    condition: likes.write_latency_ms.p99 > 200
    severity: WARNING
    message: "Like write latency p99 > 200ms"
    
  - name: CountDrift
    condition: rate(likes.counter.reconciled, 5m) > 100
    severity: WARNING
    message: ">100 counter reconciliations in 5 min — possible drift issue"
    
  - name: HighCacheMissRate
    condition: likes.cache.miss / (likes.cache.hit + likes.cache.miss) > 0.3
    severity: WARNING
    message: ">30% cache miss rate for like status"
    
  - name: KafkaConsumerLag
    condition: likes.kafka.consumer_lag > 500000
    severity: CRITICAL
    message: "Like counter consumer lag > 500K — counts may be stale"
```

---

### Deep Dive 12: Disaster Recovery & Data Migration

**The Problem**: With 500B like records across 64 DB shards, migrating data (re-sharding, region failover, or schema changes) is a massive operation. We need a strategy for zero-downtime migrations.

```java
public class LikeDataMigration {

    /**
     * Double-write migration strategy for re-sharding or schema changes.
     * 
     * Phase 1 (Shadow Write): Write to both old and new shards
     * Phase 2 (Backfill): Copy historical data from old to new
     * Phase 3 (Shadow Read): Read from new, verify against old
     * Phase 4 (Cutover): Switch reads to new, stop writing to old
     * Phase 5 (Cleanup): Decommission old shards
     */
    
    public enum MigrationPhase {
        SHADOW_WRITE,  // Write to both, read from old
        BACKFILL,      // Copy historical data
        SHADOW_READ,   // Read from new, compare with old (dark reads)
        CUTOVER,       // Read from new, write to both (can rollback)
        COMPLETE       // New only (old decommissioned)
    }
    
    private volatile MigrationPhase currentPhase = MigrationPhase.SHADOW_WRITE;
    
    /** Write path: dual-write during migration */
    public void writeLike(long userId, long itemId, boolean isActive) {
        // Always write to old shard (current source of truth)
        DataSource oldDs = oldShardRouter.getDataSource(userId);
        writeTo(oldDs, userId, itemId, isActive);
        
        if (currentPhase == MigrationPhase.SHADOW_WRITE || 
            currentPhase == MigrationPhase.CUTOVER) {
            // Also write to new shard
            try {
                DataSource newDs = newShardRouter.getDataSource(userId);
                writeTo(newDs, userId, itemId, isActive);
            } catch (Exception e) {
                log.error("Shadow write to new shard failed: user={}, item={}", userId, itemId, e);
                metrics.counter("migration.shadow_write.failed").increment();
                // Don't fail the request — old shard is still source of truth
            }
        }
    }
    
    /** Read path: gradually shift reads to new shards */
    public LikeStatus readLikeStatus(long userId, long itemId) {
        if (currentPhase == MigrationPhase.SHADOW_READ ||
            currentPhase == MigrationPhase.CUTOVER ||
            currentPhase == MigrationPhase.COMPLETE) {
            
            // Read from new shard
            LikeStatus newResult = readFrom(newShardRouter.getDataSource(userId), userId, itemId);
            
            if (currentPhase == MigrationPhase.SHADOW_READ) {
                // Verify against old shard (async, don't block)
                CompletableFuture.runAsync(() -> {
                    LikeStatus oldResult = readFrom(oldShardRouter.getDataSource(userId), userId, itemId);
                    if (!oldResult.equals(newResult)) {
                        log.warn("Migration read mismatch: user={}, item={}, old={}, new={}",
                            userId, itemId, oldResult.isLiked(), newResult.isLiked());
                        metrics.counter("migration.read_mismatch").increment();
                    }
                });
            }
            
            return newResult;
        }
        
        // Default: read from old shard
        return readFrom(oldShardRouter.getDataSource(userId), userId, itemId);
    }
    
    /**
     * Backfill: copy all historical data from old shards to new shards.
     * Uses a streaming approach to avoid OOM on large tables.
     */
    @Async
    public void runBackfill() {
        for (int oldShard = 0; oldShard < OLD_SHARD_COUNT; oldShard++) {
            DataSource oldDs = oldShardRouter.getDataSourceByShard(oldShard);
            
            long lastUserId = 0;
            int batchSize = 10_000;
            
            while (true) {
                List<LikeRecord> batch = jdbc(oldDs).query("""
                    SELECT user_id, item_id, is_active, created_at, updated_at
                    FROM likes WHERE user_id > ? ORDER BY user_id LIMIT ?
                    """, new Object[]{lastUserId, batchSize}, likeRowMapper);
                
                if (batch.isEmpty()) break;
                
                // Route each record to new shard and insert
                Map<Integer, List<LikeRecord>> byNewShard = batch.stream()
                    .collect(Collectors.groupingBy(r -> newShardRouter.getShardId(r.getUserId())));
                
                for (var entry : byNewShard.entrySet()) {
                    DataSource newDs = newShardRouter.getDataSourceByShard(entry.getKey());
                    batchInsert(newDs, entry.getValue());
                }
                
                lastUserId = batch.get(batch.size() - 1).getUserId();
                metrics.counter("migration.backfill.records").increment(batch.size());
                
                // Throttle to avoid overloading the DB
                Thread.sleep(100);
            }
            
            log.info("Backfill complete for old shard {}", oldShard);
        }
    }
}
```

**Migration Timeline**:
```
Week 1: Deploy shadow-write code. All writes go to old + new.
Week 2: Run backfill job (copy 500B records). Takes ~3-5 days with throttling.
Week 3: Enable shadow-read. Compare old vs new results. Fix any mismatches.
Week 4: Cutover. Read from new shards. Monitor for 48 hours.
Week 5: Stop writing to old shards. Decommission.

Total: ~5 weeks for zero-downtime re-sharding of 14 TB across 64 shards.
```

---

## 📊 Summary: Key Trade-offs

| Decision | Options Considered | Chosen | Why |
|----------|-------------------|--------|-----|
| **Write idempotency** | Client dedup / DB unique constraint / Redis SETNX | DB UPSERT + Redis idempotency key | Handles retries at both DB and cache level |
| **Count storage** | Single counter / Sharded counters / Redis only | Sharded counters (16-256 shards) | Eliminates hot-spot contention on viral items |
| **Status caching** | No cache / TTL cache / Write-through | Write-through + session override | User always sees own action; cache may lag |
| **DB sharding** | No shard / User-based / Item-based | User-based (64 shards) | Optimizes for "did I like?" and "my likes" queries |
| **Count updates** | Sync per-like / Async batched / CQRS | Kafka + 5s batch aggregation | 10x less DB write load; eventual consistency OK for counts |
| **Pagination** | Offset / Cursor (timestamp) / Cursor (ID) | Cursor-based (timestamp) | O(1) per page, no skipped items, works across shards |
| **Soft vs hard delete** | Hard DELETE / Soft delete (is_active) | Soft delete | Preserves history; re-like is UPDATE not INSERT |
| **Cache eviction** | LRU / TTL / Proactive warming | TTL + proactive warming for hot items | Balances memory usage with hit rate |
| **Multi-reaction** | Separate tables / Column per type / Single enum | Single table + enum column | Atomic swap, simple schema, same caching pattern |
| **Rate limiting** | API gateway only / Per-user / Multi-layer | 3-layer (user, toggle, item) | Prevents bots, oscillation, and coordinated attacks |
| **Migration** | Stop-the-world / Dual-write / CDC | Dual-write with phased cutover | Zero downtime; 5-week migration for 14 TB |

## 🎯 Interview Talking Points

1. **"Idempotent upsert is the core write primitive"** — `INSERT ... ON CONFLICT DO UPDATE WHERE is_active = false`. Handles retries, double-taps, and race conditions atomically.

2. **"Sharded counters eliminate hot-spot contention"** — A viral post with 10K likes/sec would destroy a single counter row. 256 shards = 256 independent rows = zero contention.

3. **"Batch status with MGET, not N GETs"** — Feed rendering needs 20 status checks. Single Redis MGET = 1ms. 20 individual GETs = 10ms. 10x improvement.

4. **"Read-your-own-writes via session override"** — User likes, cache fails, user refreshes → still sees "liked" because session override (60s TTL) has priority over main cache.

5. **"User-sharded DB, item-sharded counts"** — Likes table sharded by user_id (optimizes "my likes" query). Count table sharded by item_id (optimizes count reads). Different access patterns, different shard keys.

6. **"Kafka batches 100K likes into 10K writes"** — 5-second aggregation window. Accumulate deltas per item, flush in batch. 10x reduction in DB write load.

7. **"Soft delete preserves history"** — `is_active = false` instead of DELETE. Re-liking is an UPDATE (not INSERT). Partial index on `is_active = true` keeps queries fast.

8. **"Three-layer rate limiting"** — Per-user (100/min), per-toggle (5/min), per-item (50K/min). Stops bots, oscillation spam, and coordinated attacks.

9. **"Cache warming on feed render"** — When user opens feed, pre-warm status cache for all 20 items in a single batch DB query + pipeline Redis set. Cache hit rate > 95%.

10. **"Periodic reconciliation catches count drift"** — Every 5 minutes, compare sharded count sum vs actual COUNT(*) for recently active items. Auto-correct any drift.

11. **"Multi-reaction is an atomic swap"** — Changing ❤️ to 🔥 = single transaction: UPDATE reaction_type + decrement old count + increment new count. No double-counting.

12. **"Zero-downtime migration via dual-write"** — Shadow write → backfill → shadow read (verify) → cutover → decommission. 5-week process for re-sharding 14 TB.

---

**References**:
- Instagram Like/Unlike Architecture
- Redis MGET for Batch Operations
- Sharded Counters (Google Cloud Datastore pattern)
- Cursor-Based Pagination (Slack Engineering)
- Dual-Write Migration (Stripe Engineering)

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 12 topics (choose 2-3 based on interviewer interest)
