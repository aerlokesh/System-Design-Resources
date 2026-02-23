# Design Facebook Post Privacy System - Hello Interview Framework

> **Question**: Design the privacy/visibility system for Facebook posts that controls who can see each post. Support privacy levels (Public, Friends, Friends of Friends, Only Me, Custom Lists), enforce privacy at read time across News Feed, Profile, Search, and API, and handle complex social graph queries at scale.
>
> **Asked at**: Meta, Google, LinkedIn, Twitter/X, Snap
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
1. **Privacy Levels**: Each post has a privacy setting: Public, Friends, Friends of Friends (FoF), Only Me, or Custom List (include/exclude specific users or friend lists).
2. **Read-Time Enforcement**: When any user attempts to view a post (via Feed, Profile, Search, API, or shared link), the system must enforce privacy — return the post only if the viewer is authorized.
3. **Default Privacy**: Users can set a default privacy level for new posts. Individual posts can override the default.
4. **Retroactive Privacy Changes**: Users can change a post's privacy after creation (e.g., change from Public → Friends Only). The change must take effect immediately across all surfaces.
5. **Custom Lists**: Users can create named friend lists (e.g., "Close Friends", "Coworkers") and use them as include/exclude lists in privacy settings.

#### Nice to Have (P1)
- "Friends except..." (Friends minus a specific subset).
- Tag-based privacy (tagged users can see the post even if not in the privacy group).
- Shared post privacy (if User A shares User B's post, which privacy applies?).
- Post visibility in Groups (group members can see regardless of friendship).
- Privacy for comments and reactions (inherit from post, or separate settings).
- "Who can find me by search" privacy control.

#### Below the Line (Out of Scope)
- Blocking/unfriending (assume separate system handles social graph changes).
- Content moderation / community standards enforcement.
- Ads targeting based on privacy settings.
- Message/Chat privacy (different system).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Privacy Check Latency** | < 10ms per post per viewer | Privacy is on the hot path of every Feed render |
| **Throughput** | 10M+ privacy checks/sec | Every post in every Feed load requires a check |
| **Correctness** | 100% — zero false positives (never show unauthorized) | Privacy violations are legal liability and user trust issues |
| **Availability** | 99.99% | Privacy service down = Feed/Profile down |
| **Consistency** | Strong for denials, eventual for additions | Revoking access must be immediate; granting can be seconds-delayed |
| **Retroactive Latency** | < 5 seconds for privacy changes to take effect | User changes post to "Only Me" → disappears from all feeds within 5s |
| **Scale** | 3B+ users, 200B+ posts, avg 338 friends per user | Facebook-scale social graph |
| **Custom Lists** | Up to 100 lists per user, 5000 members per list | Power users create many lists |

### Capacity Estimation

```
Social Graph:
  Users: 3 billion
  Average friends per user: 338
  Total friendship edges: 3B × 338 / 2 = ~507 billion edges
  Storage for graph: 507B × 16 bytes (two user IDs) = ~8 TB

Posts:
  Total posts: 200 billion
  New posts per day: 2 billion
  Average post metadata: 200 bytes (includes privacy setting)
  Post metadata storage: 200B × 200 bytes = 40 TB

Privacy Checks:
  Feed loads per day: 10 billion
  Posts per feed load: 50 (candidate set before filtering)
  Privacy checks per day: 10B × 50 = 500 billion
  Privacy check QPS: 500B / 86400 = ~5.8M checks/sec
  Peak (2x): ~12M checks/sec

Friends-of-Friends (FoF) queries:
  FoF set size: 338 × 338 = ~114K users (avg)
  FoF queries per day: ~5% of privacy checks = 25B/day
  FoF query QPS: ~290K/sec
  
  Pre-computing FoF for all users: 3B × 114K × 8 bytes = too large (~2.7 PB)
  → Must compute on-the-fly or use Bloom filters

Custom Lists:
  Users with custom lists: ~10% of 3B = 300M
  Average lists per user: 3
  Average members per list: 50
  Total list memberships: 300M × 3 × 50 = 45B entries
  Storage: 45B × 16 bytes = ~720 GB

Privacy setting storage per post:
  Post ID: 8 bytes
  Privacy level enum: 1 byte
  Custom list IDs (if applicable): 0-32 bytes
  Total: ~15 bytes per post (average)
  200B posts × 15 bytes = 3 TB
```

---

## 2️⃣ Core Entities

### Entity 1: Post Privacy Setting
```java
public class PostPrivacySetting {
    private final long postId;
    private final long authorId;
    private final PrivacyLevel level;
    private final CustomPrivacy customPrivacy;    // Only set if level == CUSTOM
    private final Instant createdAt;
    private final Instant updatedAt;
}

public enum PrivacyLevel {
    PUBLIC,               // Anyone can see (even non-logged-in users)
    FRIENDS,              // Only direct friends of the author
    FRIENDS_OF_FRIENDS,   // Direct friends + their friends (2-hop)
    ONLY_ME,              // Only the author
    CUSTOM                // Specific include/exclude lists
}

public class CustomPrivacy {
    private final Set<Long> includeUserIds;       // Specific users who CAN see
    private final Set<Long> excludeUserIds;       // Specific users who CANNOT see
    private final Set<Long> includeFriendListIds; // Friend lists whose members CAN see
    private final Set<Long> excludeFriendListIds; // Friend lists whose members CANNOT see
    private final boolean includeAllFriends;       // Start with all friends, then apply excludes
}
```

### Entity 2: Friend List
```java
public class FriendList {
    private final long listId;
    private final long ownerId;                    // User who created this list
    private final String name;                     // "Close Friends", "Coworkers"
    private final FriendListType type;             // USER_CREATED, SYSTEM (e.g., "Close Friends")
    private final Set<Long> memberUserIds;         // Users in this list
    private final Instant createdAt;
    private final Instant updatedAt;
}

public enum FriendListType {
    USER_CREATED,     // User manually created
    SMART_LIST,       // System-generated (e.g., "Family", based on relationship status)
    CLOSE_FRIENDS,    // Special system list
    ACQUAINTANCES     // Special system list (lower priority in Feed)
}
```

### Entity 3: Privacy Check Request
```java
public class PrivacyCheckRequest {
    private final long viewerId;          // Who is trying to see the post
    private final long postId;            // Which post
    private final long authorId;          // Who wrote the post
    private final PrivacyLevel level;     // Post's privacy setting
    private final String surface;         // "news_feed", "profile", "search", "direct_link"
}
```

### Entity 4: Privacy Check Result
```java
public class PrivacyCheckResult {
    private final long postId;
    private final long viewerId;
    private final boolean allowed;          // Can this viewer see this post?
    private final PrivacyDecisionReason reason;  // Why allowed/denied
    private final long latencyMicros;       // How long the check took
}

public enum PrivacyDecisionReason {
    PUBLIC_POST,                // Post is public
    VIEWER_IS_AUTHOR,           // Viewer is the post author
    VIEWER_IS_FRIEND,           // Viewer is a direct friend
    VIEWER_IS_FOF,              // Viewer is friend-of-friend
    VIEWER_IN_CUSTOM_INCLUDE,   // Viewer is in custom include list
    VIEWER_IN_CUSTOM_EXCLUDE,   // Denied: viewer in exclude list
    ONLY_ME,                    // Denied: only author can see
    NOT_FRIEND,                 // Denied: viewer is not a friend
    NOT_FOF                     // Denied: viewer is not within 2 hops
}
```

---

## 3️⃣ API Design

### 1. Set Post Privacy
```
PUT /api/v1/posts/{post_id}/privacy

Request:
{
  "level": "CUSTOM",
  "custom": {
    "include_all_friends": true,
    "exclude_user_ids": [789, 101],
    "exclude_friend_list_ids": [42]
  }
}

Response (200 OK):
{
  "post_id": 123456,
  "privacy": {
    "level": "CUSTOM",
    "description": "Friends except 2 people and list 'Coworkers'",
    "effective_viewer_count_estimate": 312
  },
  "updated_at": "2025-01-10T10:00:00Z"
}
```

### 2. Set Default Privacy
```
PUT /api/v1/users/me/privacy/default

Request:
{
  "default_level": "FRIENDS",
  "default_custom": null
}

Response (200 OK):
{
  "default_level": "FRIENDS",
  "applied_to_future_posts": true
}
```

### 3. Check Privacy (Internal — used by Feed/Profile/Search)
```
POST /api/v1/internal/privacy/check-batch

Request:
{
  "viewer_id": 456,
  "checks": [
    { "post_id": 100, "author_id": 789, "level": "FRIENDS" },
    { "post_id": 101, "author_id": 789, "level": "PUBLIC" },
    { "post_id": 102, "author_id": 321, "level": "FRIENDS_OF_FRIENDS" },
    { "post_id": 103, "author_id": 654, "level": "CUSTOM" }
  ]
}

Response (200 OK):
{
  "viewer_id": 456,
  "results": [
    { "post_id": 100, "allowed": true, "reason": "VIEWER_IS_FRIEND" },
    { "post_id": 101, "allowed": true, "reason": "PUBLIC_POST" },
    { "post_id": 102, "allowed": true, "reason": "VIEWER_IS_FOF" },
    { "post_id": 103, "allowed": false, "reason": "VIEWER_IN_CUSTOM_EXCLUDE" }
  ],
  "total_latency_ms": 8
}
```

> **Batch API** is critical — a single Feed load checks 50+ posts at once. Individual RPCs would be too slow.

### 4. Manage Friend Lists
```
POST /api/v1/users/me/friend-lists

Request:
{
  "name": "Close Friends",
  "member_user_ids": [101, 102, 103, 104]
}

Response (201 Created):
{
  "list_id": 42,
  "name": "Close Friends",
  "member_count": 4
}
```

### 5. Get Post Privacy Explanation (for the post author)
```
GET /api/v1/posts/{post_id}/privacy/explain

Response (200 OK):
{
  "post_id": 123456,
  "privacy": {
    "level": "CUSTOM",
    "description": "Friends except Coworkers list",
    "can_see_count": 312,
    "sample_viewers": ["Alice", "Bob", "Charlie"],
    "excluded_count": 26,
    "sample_excluded": ["Dave (Coworkers)", "Eve (excluded individually)"]
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Privacy Check During Feed Load (Hot Path)
```
1. User opens Facebook → Feed Service fetches candidate posts
   ↓
2. Feed Service has 500 candidate posts (from ranking pipeline)
   ↓
3. Feed Service calls Privacy Service: batch-check(viewer=456, posts=[500 posts])
   ↓
4. Privacy Service processes each post:
   
   For each post:
   a. If level == PUBLIC → ALLOW (instant, no graph query)
   b. If level == ONLY_ME → ALLOW only if viewer == author
   c. If level == FRIENDS → check friendship(viewer, author)
      → Redis lookup: SISMEMBER friends:{author_id} {viewer_id}
      → O(1), < 1ms
   d. If level == FRIENDS_OF_FRIENDS → check FoF(viewer, author)
      → Get viewer's friends + author's friends
      → Check intersection (are they within 2 hops?)
      → Use Bloom filter for fast negative check
   e. If level == CUSTOM → evaluate custom rules:
      → Check exclude lists first (deny fast)
      → Then check include lists
      → Fall back to include_all_friends if set
   ↓
5. Privacy Service returns: [allowed_post_ids]
   ↓
6. Feed Service filters candidates → renders only allowed posts
   Total latency for 500 checks: < 10ms (batched, parallelized)
```

### Flow 2: Retroactive Privacy Change
```
1. User changes post #123 from PUBLIC → ONLY_ME
   ↓
2. Privacy Service updates post privacy in DB
   ↓
3. Publish event to Kafka: {"post_id": 123, "old": "PUBLIC", "new": "ONLY_ME"}
   ↓
4. Cache Invalidation:
   a. Invalidate cached privacy for post #123 in Privacy Cache
   b. No need to update every feed — privacy is checked at read time
   ↓
5. Next time ANY user loads Feed and post #123 is a candidate:
   → Privacy check runs → ONLY_ME → denied for everyone except author
   → Post disappears from all feeds within next refresh (~seconds)
   ↓
6. Search Index: remove post #123 from public search index (async)
```

### Flow 3: Friendship Change Affects Privacy
```
1. User A unfriends User B
   ↓
2. Social Graph Service removes edge A ↔ B
   ↓
3. Publish event: {"type": "UNFRIEND", "user_a": A, "user_b": B}
   ↓
4. Privacy Cache: 
   a. Invalidate friends set for User A and User B
   b. Invalidate FoF Bloom filters involving A or B
   ↓
5. Next Feed load by User B:
   → User A's FRIENDS-only posts → privacy check → A and B no longer friends
   → Posts denied → disappear from B's feed
   
   Note: NO retroactive scan of all of A's posts needed.
   Privacy is enforced at READ time — unfriending naturally hides posts
   on the next read.
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                           │
│          (Feed, Profile, Search, Shared Links, API)                       │
└─────────────────────────────┬────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    FEED / PROFILE / SEARCH SERVICE                        │
│                                                                            │
│  1. Fetch candidate posts (from ranking/retrieval pipeline)               │
│  2. Call Privacy Service with batch of posts + viewer ID                  │
│  3. Filter out denied posts                                               │
│  4. Return only authorized posts to client                                │
└─────────────────────────────┬────────────────────────────────────────────┘
                              │ Batch privacy check
                              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                       PRIVACY SERVICE                                     │
│                       (Stateless, 100+ pods)                              │
│                                                                            │
│  ┌─────────────────────────────────────────────────────┐                 │
│  │               PRIVACY DECISION ENGINE                │                 │
│  │                                                       │                 │
│  │  For each (post, viewer) pair:                       │                 │
│  │                                                       │                 │
│  │  PUBLIC?  ────→ ALLOW                                │                 │
│  │  ONLY_ME? ────→ viewer == author ? ALLOW : DENY      │                 │
│  │  FRIENDS? ────→ areFriends(viewer, author)           │                 │
│  │  FOF?     ────→ areFriendsOfFriends(viewer, author)  │                 │
│  │  CUSTOM?  ────→ evaluateCustomRules(viewer, post)    │                 │
│  │                                                       │                 │
│  └──────────┬───────────────────────┬────────────────────┘                │
│             │                       │                                      │
│    ┌────────▼────────┐    ┌────────▼────────┐                            │
│    │ Friendship Cache │    │ Custom List Cache│                            │
│    │ (Redis)          │    │ (Redis)          │                            │
│    │                  │    │                  │                            │
│    │ friends:{uid}    │    │ list:{lid}       │                            │
│    │ = Set of friend  │    │ = Set of members │                            │
│    │   user IDs       │    │                  │                            │
│    │                  │    │ post_privacy:{pid}│                           │
│    │ fof_bloom:{uid}  │    │ = privacy config │                            │
│    │ = Bloom filter   │    │                  │                            │
│    │   for FoF set    │    │                  │                            │
│    └────────┬─────────┘    └────────┬─────────┘                           │
│             │                       │                                      │
└─────────────┼───────────────────────┼──────────────────────────────────────┘
              │ Cache miss            │ Cache miss
              ▼                       ▼
┌──────────────────────────┐  ┌────────────────────────────────┐
│   SOCIAL GRAPH SERVICE   │  │   POST METADATA SERVICE        │
│   (TAO / Graph DB)       │  │   (MySQL / TAO)                │
│                          │  │                                │
│  • Friendship edges      │  │  • Post privacy settings       │
│  • Friend lists          │  │  • Custom list memberships     │
│  • FoF traversals        │  │  • Default privacy per user    │
│                          │  │                                │
│  Storage: ~8 TB          │  │  Storage: ~3 TB (privacy only) │
│  (adjacency lists)       │  │                                │
└──────────────────────────┘  └────────────────────────────────┘


┌──────────────────────────────────────────────────────────────────────────┐
│                    PRIVACY CHANGE PIPELINE                                │
│                                                                            │
│  User changes privacy → DB update → Kafka event →                        │
│    • Invalidate Privacy Cache                                             │
│    • Update Search Index (remove/add from public index)                  │
│    • Audit log                                                            │
└──────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Privacy Service** | Evaluate privacy rules for (post, viewer) pairs | Java/C++ on K8s | 100+ stateless pods, horizontal |
| **Friendship Cache** | Fast friend set lookups (SISMEMBER) | Redis Cluster | Sharded by user_id, ~500 GB |
| **FoF Bloom Filters** | Fast negative check for Friends-of-Friends | In-memory Bloom filters | ~1 GB per server |
| **Social Graph (TAO)** | Source of truth for friendships and lists | TAO (Meta's graph store) | Sharded, replicated |
| **Post Metadata** | Privacy settings per post | MySQL + memcached | Sharded by post_id |
| **Kafka** | Privacy change events, cache invalidation | Apache Kafka | Low-latency fanout |
| **Search Index** | Public post indexing/de-indexing | Elasticsearch | Updated on privacy change |

---

## 6️⃣ Deep Dives

### Deep Dive 1: The Privacy Decision Engine — Evaluating Rules in < 10ms

**The Problem**: For each (post, viewer) pair, we must evaluate the privacy rule and return allow/deny. The hot path sees 12M checks/sec at peak. The decision must be correct 100% of the time and fast.

```java
public class PrivacyDecisionEngine {

    /**
     * Privacy evaluation order (optimized for speed):
     * 
     * 1. SHORT-CIRCUIT checks (no graph queries needed):
     *    - PUBLIC → ALLOW
     *    - ONLY_ME → viewer == author ? ALLOW : DENY
     *    - viewer == author → ALLOW (author can always see own posts)
     * 
     * 2. FRIENDSHIP check (single Redis lookup):
     *    - FRIENDS → SISMEMBER friends:{author} {viewer}
     * 
     * 3. CUSTOM evaluation (multiple lookups):
     *    - Check exclude lists first (deny fast)
     *    - Then check include lists
     * 
     * 4. FOF check (most expensive — deferred to last):
     *    - Bloom filter for fast negative
     *    - Full graph traversal only if Bloom says "maybe"
     */
    
    public PrivacyCheckResult check(long viewerId, long postId, 
                                     long authorId, PostPrivacySetting privacy) {
        // Short-circuit: author can always see own posts
        if (viewerId == authorId) {
            return PrivacyCheckResult.allow(postId, viewerId, PrivacyDecisionReason.VIEWER_IS_AUTHOR);
        }
        
        switch (privacy.getLevel()) {
            case PUBLIC:
                return PrivacyCheckResult.allow(postId, viewerId, PrivacyDecisionReason.PUBLIC_POST);
                
            case ONLY_ME:
                return PrivacyCheckResult.deny(postId, viewerId, PrivacyDecisionReason.ONLY_ME);
                
            case FRIENDS:
                return checkFriends(viewerId, authorId, postId);
                
            case FRIENDS_OF_FRIENDS:
                return checkFriendsOfFriends(viewerId, authorId, postId);
                
            case CUSTOM:
                return checkCustom(viewerId, authorId, postId, privacy.getCustomPrivacy());
                
            default:
                // Fail closed: deny if unknown privacy level
                return PrivacyCheckResult.deny(postId, viewerId, PrivacyDecisionReason.NOT_FRIEND);
        }
    }
    
    /**
     * Batch check: process 50-500 posts for one viewer.
     * 
     * Optimization: group posts by author (same author → single friendship check),
     * and group by privacy level (all PUBLIC posts → instant allow).
     */
    public List<PrivacyCheckResult> checkBatch(long viewerId, List<PrivacyCheckRequest> requests) {
        // Step 1: Partition by privacy level
        Map<PrivacyLevel, List<PrivacyCheckRequest>> byLevel = requests.stream()
            .collect(Collectors.groupingBy(r -> r.getLevel()));
        
        List<PrivacyCheckResult> results = new ArrayList<>();
        
        // Step 2: Process PUBLIC first (instant, no queries)
        byLevel.getOrDefault(PrivacyLevel.PUBLIC, List.of())
            .forEach(r -> results.add(PrivacyCheckResult.allow(
                r.getPostId(), viewerId, PrivacyDecisionReason.PUBLIC_POST)));
        
        // Step 3: Process ONLY_ME (instant)
        byLevel.getOrDefault(PrivacyLevel.ONLY_ME, List.of())
            .forEach(r -> results.add(r.getAuthorId() == viewerId
                ? PrivacyCheckResult.allow(r.getPostId(), viewerId, PrivacyDecisionReason.VIEWER_IS_AUTHOR)
                : PrivacyCheckResult.deny(r.getPostId(), viewerId, PrivacyDecisionReason.ONLY_ME)));
        
        // Step 4: Process FRIENDS — batch friendship check
        List<PrivacyCheckRequest> friendsChecks = byLevel.getOrDefault(PrivacyLevel.FRIENDS, List.of());
        if (!friendsChecks.isEmpty()) {
            // Get unique authors to check friendship with
            Set<Long> authorIds = friendsChecks.stream()
                .map(PrivacyCheckRequest::getAuthorId)
                .collect(Collectors.toSet());
            
            // Batch Redis: for each author, is viewer a friend?
            Map<Long, Boolean> friendshipMap = friendshipCache.batchCheckFriendship(viewerId, authorIds);
            
            friendsChecks.forEach(r -> {
                boolean isFriend = friendshipMap.getOrDefault(r.getAuthorId(), false);
                results.add(isFriend
                    ? PrivacyCheckResult.allow(r.getPostId(), viewerId, PrivacyDecisionReason.VIEWER_IS_FRIEND)
                    : PrivacyCheckResult.deny(r.getPostId(), viewerId, PrivacyDecisionReason.NOT_FRIEND));
            });
        }
        
        // Step 5: Process FOF (most expensive)
        byLevel.getOrDefault(PrivacyLevel.FRIENDS_OF_FRIENDS, List.of())
            .forEach(r -> results.add(checkFriendsOfFriends(viewerId, r.getAuthorId(), r.getPostId())));
        
        // Step 6: Process CUSTOM
        byLevel.getOrDefault(PrivacyLevel.CUSTOM, List.of())
            .forEach(r -> results.add(checkCustom(viewerId, r.getAuthorId(), r.getPostId(),
                getCustomPrivacy(r.getPostId()))));
        
        return results;
    }
}
```

**Batch optimization impact**:
```
Without batching (50 posts individually):
  50 × Redis round-trips = 50 × 0.5ms = 25ms

With batching (50 posts, grouped by author):
  Step 1: Group by level → ~0.01ms
  Step 2: 30 PUBLIC posts → 0ms (no queries)
  Step 3: 15 FRIENDS posts from 8 unique authors → 1 pipeline Redis call → 0.5ms
  Step 4: 3 FOF posts → 3 Bloom checks + 0-1 graph query → 2ms
  Step 5: 2 CUSTOM posts → 2 list lookups → 1ms
  ─────────────────────────────
  Total: ~3.5ms for 50 posts (vs 25ms without batching)
```

---

### Deep Dive 2: Friends-of-Friends (FoF) — The Hardest Privacy Check

**The Problem**: "Friends of Friends" means the viewer must be within 2 hops in the social graph from the author. With 338 average friends, FoF set = ~114K users. Computing this per-post per-viewer is expensive.

```java
public class FoFPrivacyChecker {

    /**
     * FoF check: is viewer within 2 hops of author?
     * 
     * Naive approach: 
     *   Get author's friends (338) → for each, get their friends (338 × 338 = 114K)
     *   → check if viewer is in the set → O(N²) graph queries = TOO SLOW
     * 
     * Optimized approach (3 layers):
     *   Layer 1: Bloom filter pre-check (< 0.1ms)
     *   Layer 2: Friend set intersection (< 1ms)
     *   Layer 3: Full graph query (< 5ms, rare)
     */
    
    // Layer 1: Bloom filter (pre-computed per user, ~10 KB per user)
    // Contains all FoF user IDs. False positive rate ~1%.
    // If Bloom says NO → definitely not FoF → DENY (fast path, 99% of denials)
    // If Bloom says YES → might be FoF → proceed to Layer 2
    
    public PrivacyCheckResult checkFoF(long viewerId, long authorId, long postId) {
        // Layer 1: Bloom filter check (< 0.1ms, in-memory)
        BloomFilter<Long> authorFoFBloom = bloomFilterCache.get(authorId);
        if (authorFoFBloom != null && !authorFoFBloom.mightContain(viewerId)) {
            // Definitely NOT FoF
            return PrivacyCheckResult.deny(postId, viewerId, PrivacyDecisionReason.NOT_FOF);
        }
        
        // Layer 2: Friend set intersection (< 1ms)
        // If viewer and author share at least one mutual friend → viewer is FoF
        // This is cheaper than computing full FoF set
        Set<Long> viewerFriends = friendshipCache.getFriends(viewerId);   // Redis SMEMBERS
        Set<Long> authorFriends = friendshipCache.getFriends(authorId);   // Redis SMEMBERS
        
        // Direct friendship check (viewer is actually a FRIEND, which implies FoF)
        if (authorFriends.contains(viewerId)) {
            return PrivacyCheckResult.allow(postId, viewerId, PrivacyDecisionReason.VIEWER_IS_FRIEND);
        }
        
        // Intersection check: do they share any mutual friend?
        // If viewer is friends with anyone who is friends with author → FoF
        boolean hasMutualFriend = viewerFriends.stream().anyMatch(authorFriends::contains);
        
        if (hasMutualFriend) {
            return PrivacyCheckResult.allow(postId, viewerId, PrivacyDecisionReason.VIEWER_IS_FOF);
        }
        
        // Layer 3: Full graph traversal (only if Bloom said "maybe" but intersection said "no")
        // This handles Bloom filter false positives (~1% of cases)
        return PrivacyCheckResult.deny(postId, viewerId, PrivacyDecisionReason.NOT_FOF);
    }
}
```

**FoF Bloom Filter Design**:
```
Per-user Bloom filter for FoF set:
  FoF set size: ~114K users (avg)
  Target false positive rate: 1%
  Bloom filter size: ~137 KB per user (m = 1.1M bits, k = 7 hash functions)
  
  Total for 3B users: 3B × 137 KB = ~411 TB → TOO LARGE for all users
  
  Solution: only pre-compute Bloom filters for ACTIVE users who have FoF posts
  Active users with FoF-privacy posts: ~100M
  100M × 137 KB = ~13.7 TB → sharded across Redis cluster
  
  Cache policy: LRU with 24h TTL. Recompute on friendship changes.

FoF check performance:
  Bloom says NO (true negative):  99% of denials → < 0.1ms
  Bloom says YES (true positive): 99% of these are actual FoF → intersection confirms in < 1ms
  Bloom says YES (false positive): ~1% → intersection check returns NO → deny → < 1ms
  Full graph traversal needed:     ~0% (intersection catches all cases)
```

---

### Deep Dive 3: Custom Privacy Evaluation — Include/Exclude Logic

**The Problem**: Custom privacy is the most flexible but most complex check. A post might be set to "Friends, except Coworkers list, plus specifically include User X". The evaluation order and precedence rules must be well-defined.

```java
public class CustomPrivacyEvaluator {

    /**
     * Custom privacy evaluation rules (precedence order):
     * 
     * 1. EXPLICIT EXCLUDE always wins (if viewer is in exclude list → DENY)
     * 2. EXPLICIT INCLUDE overrides everything else (if viewer is in include list → ALLOW)
     * 3. If include_all_friends is true → check friendship → ALLOW if friend
     * 4. Check include_friend_list_ids → ALLOW if viewer is in any included list
     * 5. Default → DENY (if none of the above match)
     * 
     * This precedence ensures:
     *   - "Friends except Dave" → Dave is denied even though he's a friend
     *   - "Close Friends list + specifically Alice" → Alice sees it even if not in Close Friends
     */
    
    public PrivacyCheckResult evaluateCustom(long viewerId, long authorId,
                                              long postId, CustomPrivacy custom) {
        // Step 1: Check explicit excludes FIRST (deny fast)
        if (custom.getExcludeUserIds().contains(viewerId)) {
            return PrivacyCheckResult.deny(postId, viewerId, 
                PrivacyDecisionReason.VIEWER_IN_CUSTOM_EXCLUDE);
        }
        
        // Check exclude friend lists
        for (long listId : custom.getExcludeFriendListIds()) {
            Set<Long> listMembers = friendListCache.getMembers(listId);
            if (listMembers.contains(viewerId)) {
                return PrivacyCheckResult.deny(postId, viewerId,
                    PrivacyDecisionReason.VIEWER_IN_CUSTOM_EXCLUDE);
            }
        }
        
        // Step 2: Check explicit includes (allow fast)
        if (custom.getIncludeUserIds().contains(viewerId)) {
            return PrivacyCheckResult.allow(postId, viewerId,
                PrivacyDecisionReason.VIEWER_IN_CUSTOM_INCLUDE);
        }
        
        // Step 3: Check include_all_friends
        if (custom.isIncludeAllFriends()) {
            boolean isFriend = friendshipCache.areFriends(viewerId, authorId);
            if (isFriend) {
                return PrivacyCheckResult.allow(postId, viewerId,
                    PrivacyDecisionReason.VIEWER_IS_FRIEND);
            }
        }
        
        // Step 4: Check include friend lists
        for (long listId : custom.getIncludeFriendListIds()) {
            Set<Long> listMembers = friendListCache.getMembers(listId);
            if (listMembers.contains(viewerId)) {
                return PrivacyCheckResult.allow(postId, viewerId,
                    PrivacyDecisionReason.VIEWER_IN_CUSTOM_INCLUDE);
            }
        }
        
        // Step 5: Default deny
        return PrivacyCheckResult.deny(postId, viewerId, PrivacyDecisionReason.NOT_FRIEND);
    }
}
```

**Custom privacy examples**:
```
Example 1: "Friends except Coworkers"
  config: { include_all_friends: true, exclude_friend_list_ids: [42] }
  
  Alice (friend, not in Coworkers) → Step 3: isFriend=true → ALLOW ✓
  Bob (friend, in Coworkers list)  → Step 1: in exclude list → DENY ✓
  Charlie (not a friend)           → Step 3: isFriend=false → Step 5: DENY ✓

Example 2: "Only Close Friends + specifically Mom"
  config: { include_friend_list_ids: [10], include_user_ids: [mom_id] }
  
  Mom (not in Close Friends)       → Step 2: in include_user_ids → ALLOW ✓
  Dave (in Close Friends list)     → Step 4: in include list → ALLOW ✓
  Eve (friend, not in list)        → Step 5: DENY ✓

Example 3: "Friends except Dave, but include Dave's wife"
  config: { include_all_friends: true, exclude_user_ids: [dave_id], include_user_ids: [wife_id] }
  
  Dave     → Step 1: in exclude → DENY ✓ (exclude wins over include_all_friends)
  Dave's wife → Step 2: in explicit include → ALLOW ✓
  Alice (friend) → Step 3: isFriend=true → ALLOW ✓
```

---

### Deep Dive 4: Friendship Cache — Redis Data Model for 3B Users

**The Problem**: Every FRIENDS-level privacy check needs to know "is viewer a friend of author?" This happens 12M times/sec. The social graph must be accessible in < 1ms.

```java
public class FriendshipCache {

    /**
     * Redis data model for friendship lookups:
     * 
     * Key: friends:{user_id}
     * Type: Redis SET
     * Value: Set of friend user IDs
     * 
     * Example: friends:123 → {456, 789, 101, 102, ...}  (avg 338 members)
     * 
     * Operations:
     *   SISMEMBER friends:123 456  → Is 456 a friend of 123? O(1)
     *   SMEMBERS friends:123       → Get all friends of 123. O(N)
     *   SCARD friends:123          → How many friends does 123 have? O(1)
     * 
     * Memory per user: 338 friends × 8 bytes = ~2.7 KB
     * Active users in cache: 500M (DAU)
     * Total cache: 500M × 2.7 KB = ~1.35 TB → sharded across Redis cluster
     */
    
    private final RedisCluster redis;
    
    // Single friendship check: O(1)
    public boolean areFriends(long userId, long friendId) {
        return redis.sismember("friends:" + userId, String.valueOf(friendId));
    }
    
    // Batch friendship check: check if viewer is friends with multiple authors
    // Uses Redis PIPELINE to batch multiple SISMEMBER calls in one round-trip
    public Map<Long, Boolean> batchCheckFriendship(long viewerId, Set<Long> authorIds) {
        Map<Long, Boolean> results = new HashMap<>();
        
        try (Pipeline pipe = redis.pipelined()) {
            Map<Long, Response<Boolean>> responses = new HashMap<>();
            for (long authorId : authorIds) {
                responses.put(authorId, 
                    pipe.sismember("friends:" + authorId, String.valueOf(viewerId)));
            }
            pipe.sync();
            
            responses.forEach((authorId, response) -> results.put(authorId, response.get()));
        }
        
        return results;
    }
    
    // Get all friends (for FoF intersection check)
    public Set<Long> getFriends(long userId) {
        Set<String> members = redis.smembers("friends:" + userId);
        return members.stream().map(Long::parseLong).collect(Collectors.toSet());
    }
    
    // Cache invalidation on friendship change
    public void onFriendshipChange(long userA, long userB, boolean added) {
        if (added) {
            redis.sadd("friends:" + userA, String.valueOf(userB));
            redis.sadd("friends:" + userB, String.valueOf(userA));
        } else {
            redis.srem("friends:" + userA, String.valueOf(userB));
            redis.srem("friends:" + userB, String.valueOf(userA));
        }
        // Also invalidate FoF Bloom filters
        invalidateFoFBloom(userA);
        invalidateFoFBloom(userB);
    }
}
```

**Redis cluster sizing**:
```
Active users (DAU): 500M
Friends per user: 338 avg
Bytes per friend entry: 8 bytes (long)
Set overhead per key: ~200 bytes

Memory per user: 200 + (338 × 8) = ~2.9 KB
Total: 500M × 2.9 KB = ~1.45 TB

Redis nodes: 50 nodes × 32 GB RAM each = 1.6 TB capacity
Sharding: consistent hashing by user_id
Replication: 2 replicas per shard (for availability)
Total nodes: 150 (50 primary + 100 replicas)

Throughput: 
  Each Redis node handles ~200K ops/sec
  50 primary nodes × 200K = 10M ops/sec
  Sufficient for 12M checks/sec (with read replicas handling reads)
```

---

### Deep Dive 5: Read-Time vs Write-Time Privacy Enforcement

**The Problem**: Should we enforce privacy when the post is written to the Feed (write-time fanout) or when the Feed is read (read-time check)?

```
┌─────────────────────────┬──────────────────────────────┬──────────────────────────────┐
│ Aspect                  │ Write-Time Enforcement        │ Read-Time Enforcement         │
├─────────────────────────┼──────────────────────────────┼──────────────────────────────┤
│ When privacy is checked │ When post is created         │ When feed is loaded           │
│ Feed fanout             │ Only fan out to allowed users│ Fan out to all, filter later  │
│ Retroactive changes     │ Must re-fan-out to update    │ Naturally enforced on next read│
│ Unfriend handling       │ Must retroactively remove    │ Naturally enforced on next read│
│ Latency (write)         │ Slower writes (fan-out work) │ Fast writes                   │
│ Latency (read)          │ Fast reads (pre-filtered)    │ Slower reads (check per post) │
│ Correctness             │ Can be stale until re-fanout │ Always current                │
│ Storage                 │ More (per-user feed copies)  │ Less (shared post storage)    │
└─────────────────────────┴──────────────────────────────┴──────────────────────────────┘

Our choice: READ-TIME ENFORCEMENT (with optimizations)

Why:
1. Retroactive changes are FREE — change privacy, next read enforces it
2. Unfriend → automatically hides posts (no re-fanout needed)
3. 100% correctness guaranteed (no stale fanout state)
4. Simpler architecture (no re-fanout pipeline for privacy changes)

The cost: every Feed read must run privacy checks on candidate posts.
Mitigations:
  - Batch checking (50 posts in 3.5ms)
  - Short-circuit PUBLIC posts (60% of posts → instant allow)
  - Redis friendship cache (< 0.5ms per lookup)
  - Bloom filters for FoF (< 0.1ms negative check)
```

---

### Deep Dive 6: Privacy for Shared Posts & Tags

**The Problem**: When User A shares User B's post, which privacy setting applies? What about tagged users — should they see the post even if not in the privacy group?

```java
public class SharedPostPrivacyResolver {

    /**
     * Shared post privacy rules:
     * 
     * Original post by B (privacy: FRIENDS)
     * Shared by A (privacy: PUBLIC)
     * 
     * Who can see A's share?
     *   - A's audience (PUBLIC) ∩ B's audience (B's FRIENDS)
     *   - Result: Only B's friends who are also in A's audience can see the full content
     *   - Non-friends of B see: "A shared a post" (with limited preview or no content)
     * 
     * This is the INTERSECTION model: both the original and share privacy must be satisfied.
     */
    
    public PrivacyCheckResult checkSharedPost(long viewerId, long sharerId, 
                                               long originalAuthorId,
                                               PostPrivacySetting sharePrivacy,
                                               PostPrivacySetting originalPrivacy) {
        // Check share privacy (can viewer see A's share?)
        PrivacyCheckResult shareCheck = check(viewerId, sharerId, sharePrivacy);
        if (!shareCheck.isAllowed()) return shareCheck;
        
        // Check original post privacy (can viewer see B's content?)
        PrivacyCheckResult originalCheck = check(viewerId, originalAuthorId, originalPrivacy);
        if (!originalCheck.isAllowed()) {
            // Viewer can see that A shared something, but not the content
            return PrivacyCheckResult.partial(shareCheck.getPostId(), viewerId,
                "SHARED_WITHOUT_CONTENT");
        }
        
        // Both checks passed → full access
        return PrivacyCheckResult.allow(shareCheck.getPostId(), viewerId,
            PrivacyDecisionReason.PUBLIC_POST);
    }
    
    /**
     * Tag-based privacy extension:
     * 
     * If User C is tagged in a post, C can see the post REGARDLESS of
     * the privacy setting (unless C is explicitly excluded).
     * 
     * This is an OVERRIDE: tagged users bypass the normal privacy check.
     * But explicit exclude still wins (if you're excluded AND tagged, you're denied).
     */
    public PrivacyCheckResult checkWithTags(long viewerId, long postId,
                                             PostPrivacySetting privacy,
                                             Set<Long> taggedUserIds) {
        // If viewer is tagged → allow (unless explicitly excluded)
        if (taggedUserIds.contains(viewerId)) {
            if (privacy.getLevel() == PrivacyLevel.CUSTOM 
                && privacy.getCustomPrivacy().getExcludeUserIds().contains(viewerId)) {
                // Explicitly excluded overrides tag
                return PrivacyCheckResult.deny(postId, viewerId, 
                    PrivacyDecisionReason.VIEWER_IN_CUSTOM_EXCLUDE);
            }
            return PrivacyCheckResult.allow(postId, viewerId, 
                PrivacyDecisionReason.VIEWER_IS_TAGGED);
        }
        
        // Not tagged → normal privacy check
        return check(viewerId, postId, privacy);
    }
}
```

---

### Deep Dive 7: Retroactive Privacy Changes — Immediate Effect

**The Problem**: When a user changes a post from PUBLIC to ONLY_ME, the post must disappear from everyone's feed immediately. How do we achieve < 5s propagation?

```java
public class PrivacyChangeHandler {

    /**
     * Read-time enforcement makes retroactive changes nearly free:
     * 
     * 1. Update privacy in DB (source of truth) → immediate
     * 2. Invalidate cache entry for this post's privacy → < 100ms
     * 3. Next Feed load → cache miss → reads new privacy → enforced
     * 
     * No need to:
     *   - Scan all feeds containing this post
     *   - Send push notifications to remove the post
     *   - Update a fan-out table
     * 
     * The only delay is the cache TTL / invalidation propagation.
     */
    
    public void handlePrivacyChange(long postId, long authorId,
                                     PrivacyLevel oldLevel, PrivacyLevel newLevel) {
        // Step 1: Update DB (strongly consistent write)
        postMetadataService.updatePrivacy(postId, newLevel);
        
        // Step 2: Invalidate privacy cache (all replicas)
        privacyCache.invalidate("post_privacy:" + postId);
        
        // Step 3: If changing from PUBLIC → non-public, remove from search index
        if (oldLevel == PrivacyLevel.PUBLIC && newLevel != PrivacyLevel.PUBLIC) {
            searchIndexService.deindexPost(postId);
        }
        
        // Step 4: If changing from non-public → PUBLIC, add to search index
        if (oldLevel != PrivacyLevel.PUBLIC && newLevel == PrivacyLevel.PUBLIC) {
            searchIndexService.indexPost(postId);
        }
        
        // Step 5: Publish event for audit log
        kafka.send("privacy-changes", new PrivacyChangeEvent(postId, authorId, oldLevel, newLevel));
        
        // Step 6: (Optional) If the post is currently "hot" (in many active feeds),
        // proactively push invalidation via WebSocket to connected clients
        if (isHotPost(postId)) {
            webSocketService.broadcast("post_privacy_changed", postId);
        }
    }
}
```

**Propagation timeline**:
```
T=0.0s   User clicks "Change to Only Me"
T=0.1s   DB updated with new privacy level ✓
T=0.2s   Cache invalidated across all Redis replicas ✓
T=0.3s   Search index removal queued ✓
T=0.5s   Next Feed load by any viewer → cache miss → reads DB → ONLY_ME → denied ✓
T=2.0s   Search index fully updated ✓
T=5.0s   All active browser sessions refresh → post gone ✓

Total effective propagation: < 1 second for new Feed loads
                             < 5 seconds for already-rendered pages (on next refresh)
```

---

### Deep Dive 8: Fail-Closed Design — What Happens When Privacy Service Is Down?

**The Problem**: If the Privacy Service is unavailable, should we show all posts (fail-open) or show no posts (fail-closed)? This is a critical design decision.

```java
public class PrivacyFailsafePolicy {

    /**
     * FAIL-CLOSED: If privacy check fails, DENY the post.
     * 
     * Rationale:
     *   - Showing an unauthorized post is a privacy VIOLATION (legal liability)
     *   - Not showing an authorized post is a degraded EXPERIENCE (annoying but safe)
     *   - Privacy violations can cause lawsuits, regulatory fines, loss of user trust
     *   - Degraded experience causes user complaints but no legal consequences
     * 
     * Therefore: ALWAYS fail-closed. Better to show an empty feed than a privacy violation.
     */
    
    public PrivacyCheckResult checkWithFallback(long viewerId, long postId,
                                                 long authorId, PostPrivacySetting privacy) {
        try {
            return check(viewerId, postId, authorId, privacy);
        } catch (RedisConnectionException | TimeoutException e) {
            // Cache is down — try source of truth (DB)
            try {
                boolean isFriend = socialGraphService.areFriends(viewerId, authorId);
                // ... proceed with DB-based check
            } catch (Exception dbEx) {
                // Both cache AND DB are down → FAIL CLOSED
                metrics.counter("privacy.check.fail_closed").increment();
                return PrivacyCheckResult.deny(postId, viewerId, 
                    PrivacyDecisionReason.SERVICE_UNAVAILABLE);
            }
        }
    }
    
    /**
     * Graceful degradation tiers:
     * 
     * Tier 1 (normal): Privacy Service + Redis cache → < 10ms ✓
     * Tier 2 (cache down): Privacy Service + DB fallback → < 50ms ⚠️
     * Tier 3 (service degraded): Only allow PUBLIC and ONLY_ME posts 
     *         (these don't need graph queries) → partial feed ⚠️
     * Tier 4 (total failure): Fail closed → empty feed ✗ (but safe)
     */
    
    public List<PrivacyCheckResult> degradedCheck(long viewerId, 
                                                    List<PrivacyCheckRequest> requests) {
        // Tier 3: only process posts that don't need graph lookups
        return requests.stream()
            .map(r -> {
                if (r.getLevel() == PrivacyLevel.PUBLIC) {
                    return PrivacyCheckResult.allow(r.getPostId(), viewerId, 
                        PrivacyDecisionReason.PUBLIC_POST);
                } else if (r.getAuthorId() == viewerId) {
                    return PrivacyCheckResult.allow(r.getPostId(), viewerId,
                        PrivacyDecisionReason.VIEWER_IS_AUTHOR);
                } else {
                    // Cannot verify friendship → deny (fail-closed)
                    return PrivacyCheckResult.deny(r.getPostId(), viewerId,
                        PrivacyDecisionReason.SERVICE_UNAVAILABLE);
                }
            })
            .toList();
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Enforcement timing** | Read-time (not write-time) | Retroactive changes are free; unfriends naturally enforced; 100% correct |
| **Friendship storage** | Redis SET per user | O(1) SISMEMBER; batch pipeline for multiple checks; < 0.5ms |
| **FoF check** | Bloom filter + set intersection | Bloom catches 99% of negatives in 0.1ms; intersection handles positives in < 1ms |
| **Batch API** | Group by level, pipeline Redis calls | 50 posts in 3.5ms vs 25ms individually |
| **Custom precedence** | Exclude > Include > Friends > Default | Explicit exclude always wins; prevents unintended access |
| **Shared post privacy** | Intersection of share + original privacy | Viewer must satisfy BOTH; prevents privacy escalation via sharing |
| **Failure mode** | Fail-closed (deny on error) | Privacy violation is worse than empty feed; legal and trust implications |
| **Cache invalidation** | Post-level invalidation + TTL | Retroactive changes propagate in < 1s; friendship changes invalidate user sets |
| **FoF Bloom storage** | Only for active users with FoF posts | 100M users × 137 KB = 13.7 TB; not 3B users (too large) |

## Interview Talking Points

1. **"Read-time enforcement for free retroactive changes"** — Change privacy from PUBLIC to ONLY_ME → next Feed read denies the post. No fan-out recalculation needed. Unfriend → posts hide on next read.
2. **"Batch privacy check: 50 posts in 3.5ms"** — Group by privacy level. PUBLIC = instant. FRIENDS = pipeline Redis SISMEMBER. FOF = Bloom filter. CUSTOM = list lookups. 7x faster than individual checks.
3. **"FoF via Bloom filter + set intersection"** — Bloom filter (137 KB/user) catches 99% of "not FoF" in 0.1ms. Set intersection finds mutual friends in < 1ms. No full graph traversal needed.
4. **"Fail-closed: deny on error"** — Showing an unauthorized post = privacy violation (lawsuits). Empty feed = degraded UX. We always choose safety over availability for privacy.
5. **"Custom privacy: exclude always wins"** — "Friends except Dave" → Dave is denied even if include_all_friends is true. Explicit exclude overrides everything except explicit include of the same user.
6. **"Redis SET for friendship: O(1) SISMEMBER"** — 500M active users × 338 friends × 8 bytes = 1.45 TB in Redis. 50 shards handle 10M ops/sec. Pipeline batches for multiple authors.
7. **"Shared post = intersection of both privacies"** — A shares B's FRIENDS post publicly. Viewer must be B's friend AND in A's audience. Prevents privacy escalation through sharing.
8. **"< 1 second propagation for privacy changes"** — DB write → cache invalidation → next read enforces. No need to scan or update feeds. Search index updated async.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **News Feed** | Privacy is the key filter in Feed pipeline | Feed focuses on ranking; privacy focuses on access control |
| **Social Graph** | Privacy depends on friendship data | Graph stores relationships; privacy enforces rules on top |
| **Access Control (RBAC/ABAC)** | Same authorization pattern | RBAC is role-based; FB privacy is relationship-based (social graph) |
| **Search with ACL** | Similar per-document access control | Search needs pre-filtered index; FB uses read-time filtering |
| **Google Docs Sharing** | Similar permission model | Docs has link-based sharing; FB is relationship-graph-based |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Friendship store** | Redis SET | TAO (Meta's graph DB) | Source of truth (not cache); handles graph traversals natively |
| **Friendship store** | Redis SET | Neo4j | Graph-heavy queries (multi-hop); smaller scale |
| **FoF check** | Bloom + intersection | Pre-computed adjacency lists | If FoF queries dominate (> 30% of checks) |
| **Privacy config** | MySQL + memcached | DynamoDB | Serverless, auto-scaling for bursty writes |
| **Enforcement** | Read-time | Write-time (fan-out filtered) | If read latency is the bottleneck and privacy changes are rare |
| **Cache** | Redis Cluster | Memcached | Simpler; no SET operations; key-value only |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Meta TAO: Facebook's Distributed Data Store for the Social Graph
- Bloom Filter Theory and Applications (Broder & Mitzenmacher)
- Facebook Privacy Architecture (Engineering blog posts)
- Redis SET Operations Performance Characteristics
- GDPR Right to be Forgotten — Technical Implementation
- Access Control in Distributed Systems (Google Zanzibar / SpiceDB)
