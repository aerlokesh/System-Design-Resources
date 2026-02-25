# Design a Social Media Feed Generation System - Hello Interview Framework

> **Question**: Design a social media feed system that generates and populates personalized feeds for users on platforms like Twitter, Instagram, or Facebook. The system collects posts from people and topics you follow, ranks them, and serves a personalized, paginated timeline that feels fresh and responsive. Users open the app, scroll through recent and relevant content, and see new items arrive in near real time.
>
> **Asked at**: Microsoft, Rippling, Bloomberg, Apple
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
1. **Feed Generation**: When a user opens the app, generate a personalized feed of posts from people they follow, ranked by a combination of recency and relevance.
2. **Post Publishing**: Users create posts (text, images, videos) that appear in the feeds of their followers.
3. **Paginated Timeline**: Users scroll through a paginated feed. Each page returns the next batch of ranked posts. The feed should feel infinite and seamless.
4. **Near-Real-Time Updates**: New posts from followed users should appear in the feed within seconds of being published (for active users).
5. **Follow Graph**: Users follow/unfollow other users. The follow graph determines whose posts appear in each user's feed.

#### Nice to Have (P1)
- Ranked feed using ML-based relevance scoring (not just reverse-chronological).
- Topic/interest-based feed injection (posts from accounts you don't follow but match your interests).
- "New posts available" notification banner (pull-to-refresh indicator).
- Mute/block filtering (exclude specific users from feed).
- Engagement signals feedback loop (likes, comments, shares influence future ranking).
- Multi-format support: text, images, videos, stories, reels with different ranking weights.
- Ad injection into the feed at configured intervals.

#### Below the Line (Out of Scope)
- Post creation/upload pipeline (media encoding, CDN distribution).
- Comments, likes, shares systems (we consume engagement signals but don't design them).
- Search and discovery (explore page, trending topics).
- Direct messaging.
- Content moderation / safety filtering pipeline.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Feed Read Latency** | < 200ms P99 for first page | Users expect instant feed on app open |
| **Feed Freshness** | New posts visible within 10 seconds | Near-real-time feel for active users |
| **Throughput (Reads)** | 500K feed reads/sec at peak | High read:write ratio (100:1 typical) |
| **Throughput (Writes)** | 5K posts/sec sustained, 50K burst | Celebrity posts trigger massive fanout |
| **Availability** | 99.99% for feed reads | Feed is the core product; downtime = user loss |
| **Consistency** | Eventual (seconds) | Brief delay for new posts acceptable; no stale reads beyond 30s |
| **Personalization** | Per-user ranked feed | No two users see the same feed, even if they follow the same people |
| **Scale** | 500M DAU, 1B total users, 200B follow edges | Facebook/Instagram scale |
| **Celebrity Handling** | Support users with 100M+ followers | Cannot fan out to 100M mailboxes on every post |

### Capacity Estimation

```
Users:
  Total users: 1B
  Daily active users (DAU): 500M
  Average follows per user: 200
  Total follow edges: 1B × 200 = 200B edges
  
Posts:
  New posts per day: 500M (including reposts/shares)
  Posts per second: ~5,800 (sustained), 50K (peak/burst)
  Average post size: 500 bytes (metadata) + media references
  
Feed reads:
  Feed loads per DAU per day: 10
  Total feed loads per day: 500M × 10 = 5B
  Feed loads per second: ~58K (sustained), 500K (peak)
  Posts per feed page: 20
  
Fanout:
  Average post fanout: 200 followers
  Total fanout writes per day: 500M posts × 200 = 100B fanout writes/day
  Fanout writes per second: ~1.16M/sec (average)
  
  Celebrity posts (1M+ followers): ~50K posts/day → 50B fanout writes
  → This is the HOT KEY problem (see Deep Dive)

Storage:
  Feed cache per user: 500 posts × 8 bytes (post_id) = 4 KB
  Total feed cache: 500M DAU × 4 KB = 2 TB (hot, in Redis)
  
  Post metadata: 500M posts/day × 500 bytes = 250 GB/day
  Follow graph: 200B edges × 16 bytes = 3.2 TB
  
  Feed cache (Redis): ~2 TB
  Post store (DB): ~90 TB/year (with retention)
  Follow graph: ~3.2 TB
```

---

## 2️⃣ Core Entities

### Entity 1: Post
```java
public class Post {
    private final String postId;            // Snowflake ID (timestamp-embedded for ordering)
    private final String authorId;          // User who created the post
    private final String content;           // Text content
    private final List<String> mediaUrls;   // Images, videos (CDN URLs)
    private final PostType type;            // TEXT, IMAGE, VIDEO, STORY, REEL
    private final Instant createdAt;
    private final Map<String, Object> metadata; // Hashtags, mentions, location, etc.
    private final EngagementCounts engagement;  // Likes, comments, shares (denormalized)
}

public enum PostType {
    TEXT, IMAGE, VIDEO, STORY, REEL, REPOST
}
```

### Entity 2: Follow Edge
```java
public class FollowEdge {
    private final String followerId;        // User who follows
    private final String followeeId;        // User being followed
    private final Instant followedAt;
    private final boolean notificationsOn;  // Get notified on new posts?
    private final boolean closeFriend;      // Boosted ranking weight
}
```

### Entity 3: Feed Item
```java
public class FeedItem {
    private final String postId;
    private final String authorId;
    private final double relevanceScore;    // ML-computed or heuristic score
    private final Instant rankedAt;         // When this item was scored
    private final FeedItemSource source;    // FOLLOW, TOPIC, SUGGESTED, AD
    private final boolean seen;             // Has user scrolled past this?
}

public enum FeedItemSource {
    FOLLOW,     // From someone user follows
    TOPIC,      // From topic/interest user subscribes to
    SUGGESTED,  // Algorithmic recommendation
    AD          // Sponsored content
}
```

### Entity 4: User Feed Cache
```java
public class UserFeedCache {
    private final String userId;
    private final List<String> postIds;     // Ordered list of post IDs in feed
    private final Instant lastGeneratedAt;  // When feed was last materialized
    private final String cursor;            // Pagination cursor (last seen position)
    private final int totalItems;
}
```

### Entity 5: User Profile (Feed-relevant subset)
```java
public class UserProfile {
    private final String userId;
    private final String displayName;
    private final String avatarUrl;
    private final long followerCount;       // Important for fanout strategy
    private final boolean isCelebrity;      // followerCount > CELEBRITY_THRESHOLD
    private final boolean isVerified;
    private final UserTier tier;            // NORMAL, CELEBRITY, MEGA_CELEBRITY
}

public enum UserTier {
    NORMAL,         // < 10K followers → push fanout
    CELEBRITY,      // 10K - 1M followers → hybrid fanout
    MEGA_CELEBRITY  // > 1M followers → pull-based (no fanout)
}
```

### Entity 6: Ranking Signal
```java
public class RankingSignal {
    private final String userId;            // Who we're ranking for
    private final String postId;            // Post being scored
    private final double authorAffinity;    // How much user engages with this author
    private final double recencyScore;      // Time decay: newer = higher
    private final double engagementScore;   // Post's overall engagement rate
    private final double contentRelevance;  // Topic/interest match
    private final double diversityPenalty;  // Reduce score if too many from same author
    private final double finalScore;        // Weighted combination
}
```

---

## 3️⃣ API Design

### 1. Get Feed (Primary Read Path)
```
GET /api/v1/feed?cursor={cursor}&page_size=20

Headers:
  Authorization: Bearer {token}

Response (200 OK):
{
  "items": [
    {
      "post_id": "post_123",
      "author": {
        "user_id": "user_456",
        "display_name": "Alice",
        "avatar_url": "https://cdn.example.com/avatars/456.jpg",
        "is_verified": true
      },
      "content": "Just shipped the new feed ranking algorithm! 🚀",
      "media": [],
      "type": "TEXT",
      "created_at": "2025-01-10T10:00:00Z",
      "engagement": {
        "likes": 1243,
        "comments": 89,
        "shares": 45
      },
      "source": "FOLLOW",
      "relevance_score": 0.94
    },
    {
      "post_id": "post_789",
      "author": { ... },
      "content": "...",
      "media": ["https://cdn.example.com/images/img_001.jpg"],
      "type": "IMAGE",
      "created_at": "2025-01-10T09:55:00Z",
      "engagement": { "likes": 5621, "comments": 312, "shares": 178 },
      "source": "FOLLOW",
      "relevance_score": 0.91
    }
  ],
  "cursor": "eyJwb3MiOjIwfQ==",
  "has_more": true,
  "feed_metadata": {
    "generated_at": "2025-01-10T10:00:05Z",
    "new_posts_available": 3
  }
}
```

### 2. Get New Posts Count (Polling / SSE)
```
GET /api/v1/feed/updates?since_cursor={cursor}

Response (200 OK):
{
  "new_posts_count": 3,
  "latest_post_at": "2025-01-10T10:02:00Z"
}
```

> Used by the "3 new posts" banner. Client polls every 30 seconds or uses Server-Sent Events.

### 3. Create Post
```
POST /api/v1/posts

Request:
{
  "content": "Just shipped the new feed ranking algorithm! 🚀",
  "media_ids": [],
  "type": "TEXT",
  "metadata": {
    "hashtags": ["engineering", "ml"],
    "mentions": ["@bob"]
  }
}

Response (201 Created):
{
  "post_id": "post_123",
  "created_at": "2025-01-10T10:00:00Z",
  "status": "PUBLISHED"
}
```

### 4. Follow/Unfollow User
```
POST /api/v1/follows

Request:
{
  "followee_id": "user_789",
  "action": "FOLLOW"
}

Response (200 OK):
{
  "following": true,
  "followee_id": "user_789",
  "follower_count": 1234
}
```

### 5. Refresh Feed (Force Regeneration)
```
POST /api/v1/feed/refresh

Response (200 OK):
{
  "status": "REGENERATED",
  "items_count": 500,
  "generated_at": "2025-01-10T10:05:00Z"
}
```

### 6. Mark Feed Items as Seen
```
POST /api/v1/feed/seen

Request:
{
  "post_ids": ["post_123", "post_456", "post_789"],
  "cursor": "eyJwb3MiOjIwfQ=="
}

Response (200 OK):
{
  "acknowledged": true
}
```

> Used for ranking feedback: "user saw these posts but didn't engage" = negative signal.

---

## 4️⃣ Data Flow

### Flow 1: Post Publishing → Feed Fanout (Write Path)
```
1. User creates a post → POST /api/v1/posts
   ↓
2. Post Service:
   a. Validate content, store post in Posts DB
   b. Assign Snowflake ID (embeds timestamp for ordering)
   c. Publish event to Kafka topic "post-published"
   ↓
3. Fanout Service consumes "post-published" event:
   a. Check author's follower count → determine fanout strategy:
      
      NORMAL user (< 10K followers): PUSH fanout
        → Fetch all follower IDs from Follow Graph
        → For each follower: append post_id to their feed cache (Redis)
        → 200 followers × 1 write = 200 Redis writes
      
      CELEBRITY (10K - 1M followers): HYBRID fanout
        → Push to active followers only (last login < 7 days)
        → Passive followers fetch on-demand (pull at read time)
      
      MEGA_CELEBRITY (> 1M followers): NO fanout (pure pull)
        → Post stored in author's post timeline only
        → When follower reads feed, they PULL celebrity posts and merge
   ↓
4. Feed caches updated (Redis sorted sets):
   Key: feed:{user_id}
   Score: post timestamp (or ranking score)
   Member: post_id
   ↓
5. Post visible in followers' feeds on next load
```

### Flow 2: Feed Read (Read Path — Hot Path)
```
1. User opens app → GET /api/v1/feed?page_size=20
   ↓
2. Feed Service:
   a. Check feed cache (Redis): feed:{user_id}
      → If cache HIT and fresh (< 5 min old): serve from cache
      → If cache MISS or stale: regenerate feed
   ↓
3. Feed Generation (on cache miss):
   a. Get user's follow list from Follow Graph
   b. Fetch pre-computed feed from Redis (push items from normal users)
   c. Pull recent posts from celebrity/mega-celebrity authors
      (query their post timelines: posts:{author_id})
   d. Merge push + pull results
   e. Rank: apply ML scoring or heuristic (recency × engagement × affinity)
   f. Deduplicate: remove already-seen posts
   g. Write ranked feed to cache: feed:{user_id}
   ↓
4. Paginate:
   a. Cursor = position in ranked feed
   b. Return posts[cursor : cursor + page_size]
   c. Hydrate: fetch full post metadata + author info from cache/DB
   ↓
5. Return JSON response to client (< 200ms P99)
```

### Flow 3: Feed Ranking (Scoring Pipeline)
```
1. For each candidate post in the feed:
   ↓
2. Compute ranking signals:
   a. Recency: time_decay(now - post.created_at)
      → e^(-λ × hours_since_posted), λ = 0.1
   b. Author affinity: how often does user engage with this author?
      → (likes + comments + profile_visits) / (posts_seen from author)
   c. Engagement velocity: how fast is this post getting engagement?
      → (likes_in_last_hour / max(1, hours_since_posted))
   d. Content relevance: topic match between post and user interests
   e. Diversity penalty: reduce score if 3+ posts from same author in window
   ↓
3. Final score = w1 × recency + w2 × affinity + w3 × engagement 
                 + w4 × relevance - w5 × diversity_penalty
   ↓
4. Sort by final score descending → ranked feed
```

### Flow 4: New Post Notification (Real-Time Path)
```
1. Post published → Fanout Service pushes to feed caches
   ↓
2. Simultaneously: publish to "feed-updates" Kafka topic
   ↓
3. WebSocket / SSE Gateway:
   a. Subscribes to "feed-updates" for connected users
   b. If user is online: push notification "3 new posts"
   c. Client shows "New posts available" banner
   ↓
4. User taps banner → client calls GET /api/v1/feed (fresh fetch)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│                                                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐                   │
│  │ iOS App   │  │ Android  │  │ Web App   │  │ API      │                   │
│  │           │  │ App      │  │           │  │ Consumer │                   │
│  └─────┬─────┘  └─────┬────┘  └─────┬─────┘  └─────┬────┘                   │
│        │              │              │              │                         │
└────────┼──────────────┼──────────────┼──────────────┼─────────────────────────┘
         │              │              │              │
         └──────────────┼──────────────┼──────────────┘
                        │  GET /api/v1/feed
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       API GATEWAY / LOAD BALANCER                            │
│                                                                               │
│  • Auth (JWT validation)   • Rate limiting                                   │
│  • Request routing         • Geographic routing (nearest region)             │
│  • CDN for static assets   • WebSocket upgrade for real-time updates        │
└───────────────────────────────┬───────────────────────────────────────────────┘
                                │
                ┌───────────────┼───────────────┐
                │               │               │
          ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼──────┐
          │ FEED       │  │ POST      │  │ SOCIAL     │
          │ SERVICE    │  │ SERVICE   │  │ GRAPH      │
          │            │  │           │  │ SERVICE    │
          │ • Get feed │  │ • Create  │  │            │
          │ • Paginate │  │   post    │  │ • Follow/  │
          │ • Rank     │  │ • Get post│  │   Unfollow │
          │ • Hydrate  │  │ • Delete  │  │ • Get      │
          │            │  │           │  │   followers│
          │ Pods: 200+ │  │ Pods: 50  │  │ • Get      │
          │            │  │           │  │   following│
          └──────┬─────┘  └─────┬─────┘  │            │
                 │              │         │ Pods: 30   │
                 │              │         └──────┬─────┘
                 │              │                │
    ┌────────────┼──────────────┼────────────────┼────────────────┐
    │            │              │                │                │
    ▼            ▼              ▼                ▼                ▼
┌────────┐ ┌─────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────┐
│ FEED   │ │ POST    │  │ POST     │  │ FOLLOW GRAPH │  │ USER     │
│ CACHE  │ │ CACHE   │  │ STORE    │  │ STORE        │  │ PROFILE  │
│        │ │         │  │          │  │              │  │ CACHE    │
│ Redis  │ │ Redis   │  │PostgreSQL│  │ Cassandra    │  │ Redis    │
│ Cluster│ │ Cluster │  │ Sharded  │  │ or Neo4j     │  │          │
│        │ │         │  │          │  │              │  │          │
│ Sorted │ │ Post    │  │ Source   │  │ 200B edges   │  │ Display  │
│ sets:  │ │ metadata│  │ of truth │  │ Sharded by   │  │ names,   │
│ feed:  │ │ + full  │  │ for all  │  │ user_id      │  │ avatars, │
│ {uid}  │ │ content │  │ posts    │  │              │  │ counters │
│        │ │         │  │          │  │              │  │          │
│ ~2 TB  │ │ ~500 GB │  │ ~90 TB   │  │ ~3.2 TB      │  │ ~50 GB   │
└────────┘ └─────────┘  └──────────┘  └──────────────┘  └──────────┘
                │
                │ Kafka: "post-published"
                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA CLUSTER                                        │
│                                                                               │
│  Topic: "post-published"   (post creation events)                            │
│  Topic: "feed-updates"     (new items added to feeds)                        │
│  Topic: "engagement"       (likes, comments, shares for ranking feedback)    │
└──────────┬──────────────────────────────┬────────────────────────────────────┘
           │                              │
     ┌─────▼──────────────┐        ┌─────▼──────────────┐
     │ FANOUT SERVICE      │        │ RANKING SERVICE     │
     │                     │        │                     │
     │ • Consume post      │        │ • Compute relevance │
     │   events            │        │   scores            │
     │ • Determine fanout  │        │ • Author affinity   │
     │   strategy per post │        │ • Engagement velocity│
     │ • Push to feed      │        │ • Topic relevance   │
     │   caches (normal)   │        │ • Diversity control │
     │ • Skip mega-celeb   │        │                     │
     │   (pull on read)    │        │ • Batch + real-time │
     │                     │        │   scoring modes     │
     │ Pods: 100-500       │        │                     │
     │ (scale with writes) │        │ Pods: 20-50         │
     └─────────────────────┘        └─────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                     REAL-TIME UPDATE LAYER                                    │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────┐       │
│  │ WebSocket / SSE GATEWAY                                           │       │
│  │                                                                    │       │
│  │ • Maintains persistent connections with active clients             │       │
│  │ • Subscribes to "feed-updates" Kafka topic per connected user     │       │
│  │ • Pushes "N new posts" notifications to client                    │       │
│  │ • Connection count: ~50M concurrent (10% of DAU online)           │       │
│  │ • Pods: 100-300 (each handles ~200K connections)                  │       │
│  └──────────────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Feed Service** | Generate, rank, paginate, serve feeds | Java/Go on K8s | 200+ pods (read-heavy, CPU for ranking) |
| **Post Service** | CRUD for posts, publish events | Java on K8s | 50 pods |
| **Social Graph Service** | Follow/unfollow, follower queries | Java on K8s | 30 pods |
| **Fanout Service** | Distribute posts to follower feed caches | Go on K8s | 100-500 pods (write-heavy, scales with post volume) |
| **Ranking Service** | ML-based or heuristic feed scoring | Python/Java on K8s | 20-50 pods (CPU/GPU for ML inference) |
| **Feed Cache** | Per-user pre-computed feed (sorted post IDs) | Redis Cluster (sorted sets) | ~2 TB, 50+ nodes |
| **Post Cache** | Full post metadata for hydration | Redis Cluster | ~500 GB, 30+ nodes |
| **Post Store** | Source of truth for all posts | PostgreSQL (sharded by author_id) | 90 TB, sharded |
| **Follow Graph** | Source of truth for follow relationships | Cassandra (or Neo4j) | 3.2 TB, sharded by user_id |
| **Kafka** | Event streaming: post events, engagement, feed updates | Apache Kafka | 20+ brokers |
| **WebSocket Gateway** | Real-time push notifications to clients | Go/Rust on K8s | 100-300 pods |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Fanout Strategy — Push vs. Pull vs. Hybrid

**The Problem**: When a user publishes a post, how do we make it appear in all their followers' feeds? A user with 200 followers is easy (push to 200 feeds). A celebrity with 100M followers is impossible to push to 100M feed caches in real time.

```java
public class FanoutService {

    /**
     * Three fanout strategies based on author's follower count:
     * 
     * PUSH (Fan-out-on-write): Author has < 10K followers
     *   - On post publish: fetch follower list, write post_id to each follower's feed cache
     *   - Pros: feed read is O(1) — just read from cache
     *   - Cons: write amplification (1 post → N cache writes)
     *   - Latency: post visible in ~1-5 seconds (fan to 10K feeds)
     * 
     * PULL (Fan-out-on-read): Author has > 1M followers (mega-celebrity)
     *   - On post publish: store in author's timeline only
     *   - On feed read: fetch posts from all followed celebrities, merge with push feed
     *   - Pros: no write amplification for celebrities
     *   - Cons: feed read is slower (must query N celebrity timelines)
     *   - Tradeoff: read latency increases by ~50-100ms per feed load
     * 
     * HYBRID: Author has 10K - 1M followers
     *   - Push to ACTIVE followers (last login < 7 days)
     *   - Passive followers use pull on next feed load
     *   - ~30% of followers are active → push to 3K-300K instead of 10K-1M
     */
    
    private static final long PUSH_THRESHOLD = 10_000;
    private static final long PULL_THRESHOLD = 1_000_000;
    
    public void onPostPublished(Post post) {
        UserProfile author = userService.getProfile(post.getAuthorId());
        long followerCount = author.getFollowerCount();
        
        if (followerCount < PUSH_THRESHOLD) {
            // PUSH: fan out to all followers
            pushToAllFollowers(post);
        } else if (followerCount > PULL_THRESHOLD) {
            // PULL: store in author's timeline only
            storeInAuthorTimeline(post);
            // No fanout — followers will pull on read
        } else {
            // HYBRID: push to active followers only
            pushToActiveFollowers(post);
            storeInAuthorTimeline(post);
        }
    }
    
    private void pushToAllFollowers(Post post) {
        List<String> followerIds = socialGraphService.getFollowers(post.getAuthorId());
        
        // Batch Redis writes (pipeline for efficiency)
        try (RedisPipeline pipeline = redis.pipeline()) {
            for (String followerId : followerIds) {
                // ZADD feed:{followerId} <timestamp_score> <post_id>
                pipeline.zadd("feed:" + followerId, 
                    post.getCreatedAt().toEpochMilli(), post.getPostId());
                
                // Trim feed to max 500 items (evict oldest)
                pipeline.zremrangeByRank("feed:" + followerId, 0, -501);
            }
            pipeline.sync();
        }
    }
    
    private void pushToActiveFollowers(Post post) {
        List<String> activeFollowers = socialGraphService
            .getActiveFollowers(post.getAuthorId(), Duration.ofDays(7));
        
        // Same as pushToAllFollowers but with filtered list
        // Typically 30% of total followers
        pushToFollowerList(post, activeFollowers);
    }
    
    private void storeInAuthorTimeline(Post post) {
        // ZADD timeline:{authorId} <timestamp> <post_id>
        redis.zadd("timeline:" + post.getAuthorId(), 
            post.getCreatedAt().toEpochMilli(), post.getPostId());
        redis.zremrangeByRank("timeline:" + post.getAuthorId(), 0, -1001);
    }
}
```

**Fanout cost analysis**:
```
Scenario: 500M posts/day, average 200 followers

Pure PUSH:
  500M × 200 = 100B Redis writes/day = 1.16M writes/sec
  BUT: a single celebrity post (100M followers) = 100M writes
  → Takes minutes, creates Redis hotspot, blocks other fanouts
  ❌ Not viable for celebrities

Pure PULL:
  Each feed read queries N followed users' timelines
  200 followed users × 1 query each = 200 queries per feed load
  500K feed loads/sec × 200 = 100M queries/sec
  ❌ Way too many queries

HYBRID (chosen):
  Normal users (99% of posts): PUSH → 200 writes/post
  Celebrity posts (1% of posts): NO PUSH → stored in timeline
  Feed read: cache hit (push items) + 5-10 pull queries (celebrities only)
  
  Push writes: 500M × 0.99 × 200 = ~99B writes/day = ~1.15M/sec ✓
  Pull queries at read: 500K feed reads/sec × 5 celebrity lookups = 2.5M reads/sec ✓
  Total cost: manageable with Redis cluster
```

---

### Deep Dive 2: Feed Cache Design — Redis Sorted Sets for Per-User Feeds

**The Problem**: We need to store a pre-computed feed for 500M DAU. Each feed contains up to 500 ranked post IDs. The cache must support fast reads (ZRANGE for pagination), fast writes (ZADD for fanout), and automatic eviction.

```java
public class FeedCacheService {

    /**
     * Redis data model:
     * 
     * Key: feed:{user_id}
     * Type: Sorted Set (ZSET)
     * Score: post timestamp (epoch millis) or ranking score
     * Member: post_id (string, 16-20 bytes)
     * Max size: 500 items per feed (ZREMRANGEBYRANK trims oldest)
     * TTL: 7 days (inactive users' caches auto-expire)
     * 
     * Operations:
     *   ZADD feed:{uid} <score> <post_id>          — O(log N) write (fanout)
     *   ZREVRANGE feed:{uid} 0 19                   — O(K + log N) read page 1
     *   ZREVRANGEBYSCORE feed:{uid} +inf <cursor>   — cursor-based pagination
     *   ZREMRANGEBYRANK feed:{uid} 0 -501           — trim to 500 items
     *   ZCARD feed:{uid}                            — feed length
     *   
     * Memory per user: 500 items × ~30 bytes = ~15 KB
     * Total memory: 500M DAU × 15 KB = ~7.5 TB
     * → Redis cluster: 50-100 nodes, each 128-256 GB RAM
     */
    
    private static final int MAX_FEED_SIZE = 500;
    private static final int PAGE_SIZE = 20;
    
    // Read feed page (hot path — < 5ms)
    public List<String> getFeedPage(String userId, String cursor, int pageSize) {
        String key = "feed:" + userId;
        
        if (cursor == null) {
            // First page: top N items by score (descending)
            return redis.zrevrange(key, 0, pageSize - 1);
        } else {
            // Subsequent pages: items with score less than cursor
            double cursorScore = Double.parseDouble(cursor);
            return redis.zrevrangeByScore(key, cursorScore, Double.NEGATIVE_INFINITY,
                0, pageSize);
        }
    }
    
    // Write to feed cache (called by fanout service)
    public void appendToFeed(String userId, String postId, double score) {
        String key = "feed:" + userId;
        redis.zadd(key, score, postId);
        redis.zremrangeByRank(key, 0, -(MAX_FEED_SIZE + 1));  // Trim to max size
        redis.expire(key, Duration.ofDays(7));                  // Reset TTL on write
    }
    
    // Merge push + pull results for feed generation
    public List<FeedItem> generateFeed(String userId) {
        // Step 1: Get push items from cache
        List<String> pushPostIds = redis.zrevrange("feed:" + userId, 0, MAX_FEED_SIZE - 1);
        
        // Step 2: Get pull items from celebrity timelines
        List<String> followedCelebrities = socialGraphService
            .getFollowedCelebrities(userId);
        
        List<String> pullPostIds = new ArrayList<>();
        for (String celebId : followedCelebrities) {
            // Get recent posts from celebrity's timeline
            List<String> celebPosts = redis.zrevrange("timeline:" + celebId, 0, 49);
            pullPostIds.addAll(celebPosts);
        }
        
        // Step 3: Merge, deduplicate, rank
        Set<String> allPostIds = new LinkedHashSet<>();
        allPostIds.addAll(pushPostIds);
        allPostIds.addAll(pullPostIds);
        
        // Step 4: Score and sort
        List<FeedItem> rankedFeed = rankingService.rank(userId, new ArrayList<>(allPostIds));
        
        // Step 5: Write ranked feed back to cache
        writeFeedToCache(userId, rankedFeed);
        
        return rankedFeed;
    }
}
```

---

### Deep Dive 3: Feed Ranking — Heuristic and ML-Based Scoring

**The Problem**: A reverse-chronological feed (newest first) is simple but not engaging. Users want the most relevant posts first, even if they're a few hours old. The ranking system must score hundreds of candidate posts in < 50ms per feed request.

```java
public class FeedRankingService {

    /**
     * Two-stage ranking:
     * 
     * Stage 1: CANDIDATE GENERATION (cheap, fast)
     *   - Collect 500 candidate posts from push cache + pull timelines
     *   - Remove already-seen posts
     *   - Remove muted/blocked authors
     *   - Result: ~300-500 candidates
     * 
     * Stage 2: SCORING & RANKING (more expensive, per-candidate)
     *   - For each candidate, compute feature vector:
     *     [recency, author_affinity, engagement_velocity, content_type, 
     *      diversity_penalty, user_interest_match, is_from_close_friend, ...]
     *   - Score using lightweight ML model (logistic regression or small neural net)
     *     or heuristic weighted formula
     *   - Sort by score descending
     *   - Apply post-ranking rules (diversity, ad injection slots)
     * 
     * Heuristic formula (for interviews — simpler to explain):
     *   score = 0.3 × recency + 0.25 × affinity + 0.2 × engagement
     *           + 0.15 × content_relevance + 0.1 × is_close_friend
     *           - diversity_penalty
     */
    
    public List<FeedItem> rank(String userId, List<String> candidatePostIds) {
        // Fetch user's engagement history (precomputed, cached)
        UserEngagementProfile userProfile = engagementService.getProfile(userId);
        
        // Batch fetch post metadata
        Map<String, Post> posts = postService.batchGet(candidatePostIds);
        
        // Score each candidate
        List<ScoredPost> scored = new ArrayList<>();
        Map<String, Integer> authorPostCounts = new HashMap<>();
        
        for (String postId : candidatePostIds) {
            Post post = posts.get(postId);
            if (post == null) continue;
            
            // Track author diversity
            int authorCount = authorPostCounts.merge(post.getAuthorId(), 1, Integer::sum);
            
            double score = computeScore(userId, post, userProfile, authorCount);
            scored.add(new ScoredPost(postId, post.getAuthorId(), score));
        }
        
        // Sort by score descending
        scored.sort(Comparator.comparingDouble(ScoredPost::getScore).reversed());
        
        // Convert to FeedItems (top 500)
        return scored.stream()
            .limit(MAX_FEED_SIZE)
            .map(sp -> new FeedItem(sp.getPostId(), sp.getAuthorId(), 
                sp.getScore(), Instant.now(), FeedItemSource.FOLLOW, false))
            .toList();
    }
    
    private double computeScore(String userId, Post post, 
                                 UserEngagementProfile profile, int authorCount) {
        // Recency: exponential decay — posts older than 24h score < 0.1
        double hoursOld = Duration.between(post.getCreatedAt(), Instant.now()).toHours();
        double recency = Math.exp(-0.1 * hoursOld);
        
        // Author affinity: how much does user engage with this author?
        double affinity = profile.getAuthorAffinity(post.getAuthorId());  // 0.0 to 1.0
        
        // Engagement velocity: is this post going viral?
        double engagementRate = post.getEngagement().getLikes() / 
            Math.max(1.0, hoursOld);
        double engagement = Math.min(1.0, engagementRate / 1000.0);  // Normalize
        
        // Content relevance: does post topic match user interests?
        double relevance = profile.getTopicRelevance(post.getMetadata());
        
        // Close friend boost
        double closeFriend = profile.isCloseFriend(post.getAuthorId()) ? 1.0 : 0.0;
        
        // Diversity penalty: penalize 3rd+ post from same author
        double diversityPenalty = authorCount > 2 ? 0.3 * (authorCount - 2) : 0.0;
        
        return 0.30 * recency 
             + 0.25 * affinity 
             + 0.20 * engagement
             + 0.15 * relevance 
             + 0.10 * closeFriend
             - diversityPenalty;
    }
}
```

**Ranking performance**:
```
Candidates per feed: ~500
Scoring per candidate: ~0.1ms (feature lookup + arithmetic)
Total scoring time: 500 × 0.1ms = 50ms
Sort time: O(N log N) for 500 items = < 1ms
Total ranking overhead: ~50ms per feed generation

Optimization: pre-compute and cache author affinity scores
  → Batch job runs every 6 hours for all DAU
  → Stored in Redis: affinity:{user_id}:{author_id} = 0.73
  → Lookup at scoring time: O(1) per candidate
```

---

### Deep Dive 4: Hot Key Problem — Celebrity Posts and Redis Hotspots

**The Problem**: A celebrity with 100M followers posts. Even with hybrid fanout (no push), when millions of followers read their feed simultaneously, the celebrity's timeline key `timeline:{celebrity_id}` in Redis becomes a hotspot — millions of reads/sec to a single key on a single Redis shard.

```java
public class HotKeyMitigation {

    /**
     * Hot key scenarios:
     * 1. Celebrity timeline reads: ZREVRANGE timeline:{celeb_id} (millions of reads/sec)
     * 2. Viral post metadata reads: GET post:{viral_post_id} (millions of reads/sec)
     * 3. Feed writes during celebrity fanout (if we were pushing)
     * 
     * Solutions:
     * 
     * Solution A: Read Replicas for Hot Keys (chosen)
     *   - Detect hot keys: monitor Redis key access frequency
     *   - For hot keys: replicate to N Redis replicas
     *   - Client-side routing: hash(user_id) % N → pick replica for reads
     *   - Write to primary, replicate async to replicas (< 1ms lag)
     *   - Result: 5 replicas → each handles 1/5 of read traffic
     * 
     * Solution B: Local In-Process Cache (L1 cache)
     *   - Each Feed Service pod caches hot celebrity timelines locally
     *   - TTL: 30 seconds (tolerate 30s staleness)
     *   - Invalidation: subscribe to Redis keyspace notifications
     *   - Result: 200 pods × local cache = hot key never reaches Redis
     * 
     * Solution C: Pre-materialize Celebrity Feeds (batch)
     *   - Every 5 minutes: for top 1000 celebrities, pre-compute their timeline
     *   - Store as a flat JSON blob in CDN or object storage
     *   - Feed Service fetches from CDN instead of Redis
     *   - CDN handles millions of reads/sec natively
     * 
     * Our choice: HYBRID (A + B)
     *   - L1 local cache (30s TTL) absorbs most hot key reads
     *   - Redis read replicas (5x) for cache misses
     *   - Celebrity timelines are small (last 100 posts × 20 bytes = 2 KB)
     *   - Easily fits in local cache for all top 10K celebrities
     */
    
    private final LoadingCache<String, List<String>> localTimelineCache = 
        Caffeine.newBuilder()
            .maximumSize(10_000)        // Top 10K celebrity timelines
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(this::fetchFromRedis);
    
    public List<String> getCelebrityTimeline(String authorId) {
        // L1: local in-process cache (< 0.01ms)
        return localTimelineCache.get("timeline:" + authorId);
    }
    
    private List<String> fetchFromRedis(String key) {
        // L2: Redis read replica (< 1ms)
        // Route to one of N replicas based on consistent hashing
        int replicaIndex = Math.abs(key.hashCode()) % NUM_READ_REPLICAS;
        RedisConnection replica = readReplicas.get(replicaIndex);
        return replica.zrevrange(key, 0, 99);
    }
}
```

---

### Deep Dive 5: Feed Pagination — Cursor-Based with Stable Ordering

**The Problem**: Users scroll through their feed over minutes or hours. New posts arrive while scrolling. If we use simple offset pagination (`LIMIT 20 OFFSET 40`), new items shift positions and users see duplicate posts or miss posts.

```java
public class FeedPaginationService {

    /**
     * Cursor-based pagination with score anchoring:
     * 
     * Problem with offset pagination:
     *   - User loads page 1 (posts 1-20)
     *   - 5 new posts arrive (pushed to top of feed)
     *   - User loads page 2 with offset=20 → gets posts 16-35 (overlaps with page 1!)
     * 
     * Solution: Score-based cursor
     *   - Each feed item has a score (timestamp or ranking score)
     *   - Cursor = score of the LAST item in the previous page
     *   - Next page: "give me the next 20 items with score < cursor"
     *   - New items (score > cursor) don't affect pagination
     * 
     * Redis implementation:
     *   Page 1: ZREVRANGEBYSCORE feed:{uid} +inf -inf LIMIT 0 20
     *   → Returns items with highest scores first
     *   → Cursor = score of item[19] (last item on page)
     *   
     *   Page 2: ZREVRANGEBYSCORE feed:{uid} (cursor -inf LIMIT 0 20
     *   → Returns next 20 items with score < cursor
     *   → Exclusive range "(" ensures no overlap
     * 
     * Handling ties (same score):
     *   - Append post_id to score as tiebreaker: score = timestamp.post_id_hash
     *   - Or use ZRANGEBYLEX for lexicographic ordering within same score
     */
    
    public FeedPage getPage(String userId, String cursor, int pageSize) {
        String feedKey = "feed:" + userId;
        
        Set<ZSetOperations.TypedTuple<String>> results;
        
        if (cursor == null || cursor.isEmpty()) {
            // First page
            results = redis.zrevrangeWithScores(feedKey, 0, pageSize - 1);
        } else {
            double cursorScore = Double.parseDouble(cursor);
            // Exclusive upper bound: score < cursor (no overlap)
            results = redis.zrevrangeByScoreWithScores(
                feedKey, Double.NEGATIVE_INFINITY, cursorScore, 
                0, pageSize);
            // Remove first item if score == cursor (edge case for exclusive)
            results.removeIf(t -> t.getScore() == cursorScore);
            // Re-fetch if we removed one
            if (results.size() < pageSize) {
                results = redis.zrevrangeByScoreWithScores(
                    feedKey, Double.NEGATIVE_INFINITY, cursorScore - 0.001,
                    0, pageSize);
            }
        }
        
        // Build response
        List<String> postIds = results.stream()
            .map(TypedTuple::getValue)
            .toList();
        
        String nextCursor = results.isEmpty() ? null : 
            String.valueOf(results.stream()
                .mapToDouble(TypedTuple::getScore)
                .min().orElse(0));
        
        boolean hasMore = results.size() == pageSize;
        
        return new FeedPage(postIds, nextCursor, hasMore);
    }
}
```

---

### Deep Dive 6: Post Hydration — Efficiently Fetching Full Post Data

**The Problem**: The feed cache stores only post IDs (for memory efficiency). When serving a feed page of 20 items, we need to "hydrate" each ID into full post metadata (author info, content, media URLs, engagement counts). This means 20+ lookups per feed request at 500K requests/sec.

```java
public class PostHydrationService {

    /**
     * Hydration pipeline for a feed page (20 post IDs):
     * 
     * Step 1: Batch fetch post metadata from Post Cache (Redis)
     *   - MGET post:{id1} post:{id2} ... post:{id20}
     *   - Single round-trip: < 2ms for 20 items
     *   - Cache hit rate: ~95% (popular posts are hot)
     * 
     * Step 2: For cache misses → fetch from Post Store (PostgreSQL)
     *   - Batch query: SELECT * FROM posts WHERE post_id IN (...)
     *   - Back-fill cache: SET post:{id} with TTL 1 hour
     *   - Typically 0-1 misses per page (95% hit rate)
     * 
     * Step 3: Batch fetch author profiles
     *   - MGET user:{author_id1} user:{author_id2} ...
     *   - Deduplicate: if same author appears 3 times, fetch once
     *   - Cache hit rate: ~99% (user profiles change infrequently)
     * 
     * Step 4: Batch fetch engagement counts
     *   - Likes, comments, shares are denormalized on the post
     *   - Updated asynchronously via engagement events
     *   - Stale by ~30 seconds (acceptable)
     * 
     * Total hydration time: ~5-10ms for 20 items (parallel fetches)
     */
    
    public List<HydratedPost> hydrate(List<String> postIds) {
        // Step 1: Batch fetch post metadata from cache
        List<String> postKeys = postIds.stream()
            .map(id -> "post:" + id).toList();
        List<Post> posts = redis.mget(postKeys);
        
        // Step 2: Backfill cache misses from DB
        List<String> missedIds = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i++) {
            if (posts.get(i) == null) {
                missedIds.add(postIds.get(i));
            }
        }
        if (!missedIds.isEmpty()) {
            Map<String, Post> dbPosts = postStore.batchGet(missedIds);
            dbPosts.forEach((id, post) -> {
                redis.setex("post:" + id, 3600, post);  // Cache 1 hour
            });
            // Fill in gaps
            for (int i = 0; i < postIds.size(); i++) {
                if (posts.get(i) == null) {
                    posts.set(i, dbPosts.get(postIds.get(i)));
                }
            }
        }
        
        // Step 3: Batch fetch author profiles (deduplicated)
        Set<String> authorIds = posts.stream()
            .filter(Objects::nonNull)
            .map(Post::getAuthorId)
            .collect(Collectors.toSet());
        Map<String, UserProfile> authors = userProfileCache.batchGet(authorIds);
        
        // Step 4: Assemble hydrated posts
        return posts.stream()
            .filter(Objects::nonNull)
            .map(post -> new HydratedPost(
                post, 
                authors.get(post.getAuthorId()),
                post.getEngagement()))
            .toList();
    }
}
```

**Hydration performance**:
```
Per feed page (20 items):
  Post cache MGET: 1 round-trip, < 2ms
  Author cache MGET: 1 round-trip, < 2ms (deduplicated ~10-15 unique authors)
  DB backfill (on miss): ~5ms for 1-2 items (rare, 5% miss rate)
  
  Total hydration: ~5ms (cache hits) to ~10ms (with DB backfill)
  
At 500K feed reads/sec:
  Redis MGET throughput: 500K × 2 = 1M MGET/sec → handled by Redis cluster
  Post cache memory: 10M hot posts × 500 bytes = ~5 GB
  Author cache memory: 1M active authors × 500 bytes = ~500 MB
```

---

### Deep Dive 7: Follow Graph Storage — Querying 200B Edges Efficiently

**The Problem**: The follow graph has 200B edges (1B users × 200 average follows). We need two query patterns: "who does User X follow?" (for feed generation) and "who follows User X?" (for fanout). Both must return in < 10ms.

```java
public class FollowGraphService {

    /**
     * Storage options for 200B follow edges:
     * 
     * Option A: Cassandra (chosen)
     *   - Two tables for bidirectional queries:
     *     
     *     Table 1: following (who does X follow?)
     *       Partition key: follower_id
     *       Clustering key: followee_id
     *       → SELECT * FROM following WHERE follower_id = 'user_123'
     *       → Returns all users that user_123 follows (avg 200)
     *     
     *     Table 2: followers (who follows X?)
     *       Partition key: followee_id
     *       Clustering key: follower_id
     *       → SELECT * FROM followers WHERE followee_id = 'user_789'
     *       → Returns all followers of user_789
     *       → For celebrities: paginated (LIMIT 10000)
     *   
     *   - Why Cassandra: handles 200B rows, scales horizontally,
     *     partition-key queries are O(1), write-friendly
     * 
     * Option B: Neo4j (graph database)
     *   - Natural fit for graph data: (User)-[:FOLLOWS]->(User)
     *   - Pros: graph traversals (2nd-degree connections, mutual follows)
     *   - Cons: harder to scale horizontally at 200B edges
     * 
     * Option C: Adjacency list in Redis
     *   - SET following:{user_id} = [followee_1, followee_2, ...]
     *   - Fast but expensive: 200B × 16 bytes = 3.2 TB in memory
     *   - Better as a cache layer on top of Cassandra
     */
    
    // Cassandra schema
    // CREATE TABLE following (
    //     follower_id TEXT,
    //     followee_id TEXT,
    //     followed_at TIMESTAMP,
    //     PRIMARY KEY (follower_id, followee_id)
    // );
    //
    // CREATE TABLE followers (
    //     followee_id TEXT,
    //     follower_id TEXT,
    //     followed_at TIMESTAMP,
    //     PRIMARY KEY (followee_id, follower_id)
    // );
    
    // Cache layer: Redis SET for frequently accessed users
    // Key: following:{user_id} → SET of followee_ids
    // Key: followers:{user_id} → SET of follower_ids (limited to active followers)
    
    public List<String> getFollowing(String userId) {
        // Check Redis cache first
        Set<String> cached = redis.smembers("following:" + userId);
        if (cached != null && !cached.isEmpty()) {
            return new ArrayList<>(cached);
        }
        
        // Cache miss: query Cassandra
        List<String> followees = cassandra.execute(
            "SELECT followee_id FROM following WHERE follower_id = ?", userId)
            .stream().map(row -> row.getString("followee_id")).toList();
        
        // Backfill cache (TTL: 1 hour)
        if (!followees.isEmpty()) {
            redis.sadd("following:" + userId, followees.toArray(String[]::new));
            redis.expire("following:" + userId, 3600);
        }
        
        return followees;
    }
    
    public List<String> getFollowers(String userId) {
        // Same pattern: Redis cache → Cassandra fallback
        // For celebrities (1M+ followers): ALWAYS paginate, never load all into memory
        return getFollowersPaginated(userId, 0, 10000);
    }
    
    public List<String> getActiveFollowers(String userId, Duration activeWindow) {
        // Query followers table joined with user activity
        // "follower_id WHERE followee_id = ? AND last_active > ?"
        Instant cutoff = Instant.now().minus(activeWindow);
        return cassandra.execute(
            "SELECT follower_id FROM followers WHERE followee_id = ? " +
            "AND follower_id IN (SELECT user_id FROM active_users WHERE last_active > ?)",
            userId, cutoff)
            .stream().map(row -> row.getString("follower_id")).toList();
    }
}
```

---

### Deep Dive 8: Real-Time Feed Updates — WebSocket Push for New Posts

**The Problem**: When a followed user publishes a post, the follower's feed should show a "3 new posts" banner without requiring a manual refresh. With 50M concurrent users, we need an efficient real-time push mechanism.

```java
public class RealTimeFeedUpdater {

    /**
     * Architecture for real-time feed updates:
     * 
     * 1. Post published → Fanout writes to feed caches (as before)
     * 2. Simultaneously: event published to Kafka "feed-updates" topic
     *    Payload: { user_id: "follower_123", new_post_count: 1, latest_at: "..." }
     * 
     * 3. WebSocket Gateway:
     *    - Maintains persistent WebSocket connections with active clients
     *    - Each pod subscribes to Kafka partitions for its connected users
     *    - On receiving feed-update event: push to client via WebSocket
     *    - Client shows banner: "3 new posts" → tap to refresh
     * 
     * Scaling:
     *    - 50M concurrent connections (10% of 500M DAU)
     *    - 300 pods × 200K connections/pod = 60M capacity
     *    - Kafka partitioned by user_id → each pod consumes subset of updates
     * 
     * Alternative: Server-Sent Events (SSE)
     *    - One-directional: server → client only
     *    - Lighter than WebSocket (HTTP-based, no upgrade)
     *    - Better for "new posts count" use case (no client→server needed)
     *    - Chosen for simplicity unless bidirectional needed
     * 
     * Fallback: Client-side polling
     *    - If WebSocket disconnects: poll GET /feed/updates every 30 seconds
     *    - Lightweight endpoint: returns only count + timestamp, no post data
     *    - 50M users × 2 polls/min = 1.67M requests/sec (manageable)
     */
    
    // WebSocket Gateway — handles persistent connections
    public class FeedWebSocketHandler {
        
        // Map: user_id → WebSocket session
        private final ConcurrentHashMap<String, WebSocketSession> sessions = 
            new ConcurrentHashMap<>();
        
        public void onConnect(WebSocketSession session, String userId) {
            sessions.put(userId, session);
        }
        
        public void onDisconnect(String userId) {
            sessions.remove(userId);
        }
        
        // Called by Kafka consumer when feed-update event arrives
        public void onFeedUpdate(String userId, int newPostCount, Instant latestAt) {
            WebSocketSession session = sessions.get(userId);
            if (session != null && session.isOpen()) {
                session.send(new TextMessage(
                    "{\"type\":\"feed_update\"," +
                    "\"new_posts\":" + newPostCount + "," +
                    "\"latest_at\":\"" + latestAt + "\"}"));
            }
            // If user not connected → no-op (they'll see on next app open)
        }
    }
    
    // Kafka consumer for feed-update events
    @KafkaListener(topics = "feed-updates")
    public void consumeFeedUpdate(FeedUpdateEvent event) {
        // Route to correct WebSocket Gateway pod
        // (user_id determines which pod handles this connection)
        webSocketHandler.onFeedUpdate(
            event.getUserId(), event.getNewPostCount(), event.getLatestAt());
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Fanout strategy** | Hybrid: push (normal) + pull (celebrity) | Push is fast for reads; pull avoids 100M-write amplification for celebrities; hybrid balances both |
| **Feed cache** | Redis sorted sets (per-user ZSET) | O(log N) writes, O(K+log N) reads; natural fit for scored/paginated feeds; TTL for inactive users |
| **Ranking** | Heuristic multi-signal scoring | Recency × affinity × engagement × relevance; < 50ms for 500 candidates; easy to tune weights |
| **Hot key mitigation** | L1 local cache (30s TTL) + Redis read replicas | Celebrity timelines are small (2 KB); local cache absorbs 99% of hot-key reads |
| **Pagination** | Cursor-based (score anchor) | Stable during insertions; no duplicate/missed posts; cursor = score of last item |
| **Post hydration** | Batch MGET from Redis + DB backfill | Single round-trip for 20 items (< 2ms); 95% cache hit rate; parallel author fetch |
| **Follow graph** | Cassandra (two tables: following + followers) | 200B edges; partition-key queries O(1); write-friendly; Redis cache on top |
| **Real-time updates** | WebSocket/SSE gateway + Kafka feed-updates topic | 50M concurrent connections; push "new posts" banner; fallback to polling |
| **Post IDs** | Snowflake IDs (timestamp-embedded) | Natural time-ordering; no need for separate timestamp sort; globally unique |
| **Feed TTL** | 7-day expiry on inactive feed caches | Automatic memory reclamation; inactive users regenerate on return |

## Interview Talking Points

1. **"Hybrid fanout: push for normal users, pull for celebrities"** — Normal users (< 10K followers) push post_ids to follower feed caches via Redis ZADD. Celebrities (> 1M followers) store in their own timeline — followers pull at read time and merge. Avoids 100M-write amplification while keeping reads fast (< 200ms P99).

2. **"Redis sorted sets for per-user feed caches"** — Score = timestamp or ranking score. ZADD for writes (O(log N)), ZREVRANGEBYSCORE for cursor-based pagination (O(K + log N)). Feed trimmed to 500 items. TTL for inactive users. ~7.5 TB across 50-100 Redis nodes.

3. **"Multi-signal ranking: recency × affinity × engagement × relevance"** — Heuristic formula or lightweight ML model. 500 candidates scored in < 50ms. Author diversity penalty prevents feed domination. Author affinity pre-computed in batch jobs, cached in Redis for O(1) lookup.

4. **"Hot key mitigation: L1 local cache + Redis read replicas"** — Celebrity timelines (2 KB each) cached locally in each Feed Service pod (30s TTL, Caffeine). 99% of hot-key reads absorbed before reaching Redis. Read replicas (5x) for the remaining 1%.

5. **"Cursor-based pagination with score anchoring"** — Cursor = score of last item on page. Next page = items with score < cursor. New posts (higher score) don't affect pagination. No duplicates or gaps even with concurrent inserts.

6. **"Post hydration via batch MGET"** — Feed cache stores only post IDs (memory efficient). On read: MGET 20 post IDs + MGET author profiles in 2 parallel Redis round-trips (< 5ms). 95% cache hit rate. DB backfill for misses.

7. **"Cassandra follow graph with Redis cache layer"** — Two denormalized tables (following, followers) for bidirectional queries. 200B edges. Partition-key queries O(1). Redis SET cache on top for hot users. Paginated reads for celebrity follower lists.

8. **"WebSocket gateway for real-time 'new posts' banner"** — 50M concurrent connections across 300 pods. Kafka feed-updates topic partitioned by user_id. Push lightweight count notification (not full post data). Fallback to client polling every 30s.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Twitter Timeline** | Same core problem | Twitter is more chronological; Instagram/Facebook more ranked/algorithmic |
| **Notification System** | Same fanout pattern | Notifications push to individual users; feeds push to follower sets |
| **News Feed (Facebook)** | Identical problem | Facebook emphasizes friend-of-friend and group posts; more complex ranking |
| **Content Recommendation** | Same ranking pattern | Recommendations are from non-followed users; feed is from followed users |
| **Pub/Sub System** | Same publish-subscribe model | Pub/Sub is topic-based; feed is user-graph-based with personalized ranking |
| **Leaderboard** | Same Redis sorted set pattern | Leaderboard ranks users by score; feed ranks posts by relevance |
| **Chat System (WhatsApp)** | Same real-time delivery | Chat is bidirectional P2P; feed is one-to-many broadcast |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Feed cache** | Redis Sorted Sets | Memcached / DynamoDB | Memcached: simpler, cheaper for large flat caches; DynamoDB: serverless, auto-scaling |
| **Post store** | PostgreSQL (sharded) | Cassandra / DynamoDB | Cassandra: write-heavy workloads; DynamoDB: serverless, pay-per-request |
| **Follow graph** | Cassandra | Neo4j / TigerGraph | Neo4j: complex graph queries (mutual friends, 2nd-degree); TigerGraph: massive graph analytics |
| **Fanout** | Kafka + custom workers | AWS SNS + SQS / Pulsar | SNS/SQS: managed, less ops; Pulsar: built-in delayed delivery, multi-tenancy |
| **Ranking** | Heuristic formula | TensorFlow Serving / ONNX Runtime | ML models: better personalization, A/B testable; heavier infrastructure |
| **Real-time** | WebSocket Gateway | Firebase / Pusher / SSE | Firebase: managed, mobile-native; SSE: simpler for one-directional push |
| **Post IDs** | Snowflake | ULID / UUID v7 | ULID: lexicographically sortable, no coordinator; UUID v7: standard, timestamp-based |
| **Hot key cache** | Caffeine (L1) + Redis (L2) | Varnish / CDN edge cache | CDN: for public/shared content; Varnish: HTTP-level caching |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Twitter Feed Architecture (Fanout Service Design)
- Instagram Feed Ranking (ML-Based Personalization)
- Facebook News Feed Architecture (TAO Graph, Multifeed)
- Redis Sorted Sets for Leaderboards and Feeds
- Hybrid Push/Pull Fanout Strategy (System Design Primer)
- Snowflake ID Generation (Twitter Engineering)
- Cursor-Based Pagination Best Practices
- Celebrity / Hot-Key Problem in Distributed Caches
