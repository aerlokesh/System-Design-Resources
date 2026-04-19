# 🔴 Redis Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how Redis is used in production at companies like Twitter, Instagram, Uber, Stripe, Slack, and more. Every key, command, and data model decision is explained with the **WHY** behind it.

---

## 📋 Table of Contents

- [🔴 Redis Real-World Applications — Complete System Design Examples](#-redis-real-world-applications--complete-system-design-examples)
  - [📋 Table of Contents](#-table-of-contents)
  - [1. Twitter — Home Timeline Feed Cache](#1-twitter--home-timeline-feed-cache)
    - [🏗️ Architecture Context](#️-architecture-context)
    - [📐 Key Design](#-key-design)
    - [💡 Why These Data Structures?](#-why-these-data-structures)
    - [🔧 Commands — Step by Step](#-commands--step-by-step)
  - [2. Twitter — Trending Hashtags](#2-twitter--trending-hashtags)
    - [🏗️ Architecture Context](#️-architecture-context-1)
    - [📐 Key Design](#-key-design-1)
    - [💡 Why These Data Structures?](#-why-these-data-structures-1)
    - [🔧 Commands — Step by Step](#-commands--step-by-step-1)
  - [3. Instagram — Like Count \& Social Graph](#3-instagram--like-count--social-graph)
    - [🏗️ Architecture Context](#️-architecture-context-2)
    - [📐 Key Design](#-key-design-2)
    - [💡 Why These Data Structures?](#-why-these-data-structures-2)
    - [🔧 Commands — Step by Step](#-commands--step-by-step-2)
  - [4. Uber — Real-Time Driver Location](#4-uber--real-time-driver-location)
    - [🏗️ Architecture Context](#️-architecture-context-3)
    - [📐 Key Design](#-key-design-3)
    - [💡 Why These Data Structures?](#-why-these-data-structures-3)
    - [🔧 Commands — Step by Step](#-commands--step-by-step-3)
  - [5. Stripe — Idempotent Payment Processing](#5-stripe--idempotent-payment-processing)
    - [🏗️ Architecture Context](#️-architecture-context-4)
    - [📐 Key Design](#-key-design-4)
    - [🔧 Commands — Step by Step](#-commands--step-by-step-4)
  - [6. Slack — Online Presence System](#6-slack--online-presence-system)
    - [🏗️ Architecture Context](#️-architecture-context-5)
    - [📐 Key Design](#-key-design-5)
    - [🔧 Commands — Step by Step](#-commands--step-by-step-5)
  - [7. Netflix — Session Management](#7-netflix--session-management)
    - [📐 Key Design](#-key-design-6)
    - [🔧 Commands](#-commands)
  - [8. YouTube — Video View Counter](#8-youtube--video-view-counter)
    - [📐 Key Design](#-key-design-7)
    - [🔧 Commands](#-commands-1)
  - [9. WhatsApp — Unread Message Counter](#9-whatsapp--unread-message-counter)
    - [📐 Key Design](#-key-design-8)
    - [🔧 Commands](#-commands-2)
  - [10. Amazon — Shopping Cart](#10-amazon--shopping-cart)
    - [📐 Key Design](#-key-design-9)
    - [🔧 Commands](#-commands-3)
  - [11. Google — API Rate Limiter](#11-google--api-rate-limiter)
    - [📐 Key Design](#-key-design-10)
    - [🔧 Commands — All 3 Approaches](#-commands--all-3-approaches)
  - [12. DoorDash — Real-Time Order Tracking](#12-doordash--real-time-order-tracking)
    - [📐 Key Design](#-key-design-11)
    - [🔧 Commands](#-commands-4)
  - [13. Reddit — Comment Threading \& Voting](#13-reddit--comment-threading--voting)
    - [📐 Key Design](#-key-design-12)
    - [🔧 Commands](#-commands-5)
  - [14. Spotify — Recently Played \& Music Queue](#14-spotify--recently-played--music-queue)
    - [📐 Key Design](#-key-design-13)
    - [🔧 Commands](#-commands-6)
  - [15. LinkedIn — "Who Viewed Your Profile"](#15-linkedin--who-viewed-your-profile)
    - [📐 Key Design](#-key-design-14)
    - [🔧 Commands](#-commands-7)
  - [16–25: Additional Real-World Applications (Summary)](#1625-additional-real-world-applications-summary)
    - [16. Discord — Typing Indicator](#16-discord--typing-indicator)
    - [17. Airbnb — Search Result Caching](#17-airbnb--search-result-caching)
    - [18. GitHub — Feature Flags](#18-github--feature-flags)
    - [19. Tinder — Swipe Deduplication](#19-tinder--swipe-deduplication)
    - [20. TikTok — Precomputed Feed](#20-tiktok--precomputed-feed)
    - [21. Booking.com — Inventory Lock](#21-bookingcom--inventory-lock)
    - [22. Twitter — Cache Invalidation via Pub/Sub](#22-twitter--cache-invalidation-via-pubsub)
    - [23. Uber Eats — Flash Sale / Limited Coupon](#23-uber-eats--flash-sale--limited-coupon)
    - [24. WhatsApp — Message Delivery Status](#24-whatsapp--message-delivery-status)
    - [25. Google Analytics — Unique Visitor Counting](#25-google-analytics--unique-visitor-counting)
  - [🏆 Cheat Sheet: Redis Data Structure Selection Guide](#-cheat-sheet-redis-data-structure-selection-guide)
  - [🎯 Interview Summary: "When Would You Use Redis?"](#-interview-summary-when-would-you-use-redis)

---

## 1. Twitter — Home Timeline Feed Cache

### 🏗️ Architecture Context

When a user opens Twitter, they see their home timeline — a feed of tweets from people they follow. Twitter uses a **fan-out on write** model for most users: when someone tweets, it's pushed into the timelines of all their followers. Redis stores these precomputed timelines.

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `timeline:{user_id}` | List | User's precomputed home timeline (tweet IDs) | 48h |
| `tweet:{tweet_id}` | Hash | Full tweet data (author, text, media, timestamps) | 7d |
| `user:{user_id}:followers` | Set | Set of follower user IDs | None |
| `user:{user_id}:following` | Set | Set of users this person follows | None |
| `user:{user_id}:profile` | Hash | User profile metadata | None |

### 💡 Why These Data Structures?

- **List for timeline**: O(1) push/pop at both ends. Newest tweets go to the head (LPUSH). Bounded with LTRIM to prevent memory bloat. Lists maintain insertion order — exactly what a timeline needs.
- **Hash for tweet**: Allows partial reads (just get author + text without media URLs). Each field is independently accessible. More memory-efficient than separate string keys.
- **Set for followers/following**: O(1) membership check, perfect for "does user A follow user B?" queries. Set operations (SINTER) enable mutual friends.

### 🔧 Commands — Step by Step

```redis
# =============================================
# STEP 1: User "alice" (id: 1001) posts a tweet
# =============================================

# Store the tweet object
HSET tweet:50001 \
  author_id "1001" \
  author_name "alice" \
  text "Redis is the backbone of real-time systems! 🚀" \
  media_url "" \
  reply_count "0" \
  retweet_count "0" \
  like_count "0" \
  created_at "1705363200" \
  is_retweet "false"

# Set TTL — tweets stay in hot cache for 7 days
EXPIRE tweet:50001 604800

# =============================================
# STEP 2: Fan-out — Push tweet to all followers' timelines
# =============================================

# Alice's followers
SADD user:1001:followers "2001" "2002" "2003" "2004" "2005"

# For each follower, push the tweet ID to the HEAD of their timeline
LPUSH timeline:2001 "50001"
LPUSH timeline:2002 "50001"
LPUSH timeline:2003 "50001"
LPUSH timeline:2004 "50001"
LPUSH timeline:2005 "50001"

# Also add to alice's own timeline
LPUSH timeline:1001 "50001"

# CRITICAL: Trim timelines to bounded size (last 800 tweets)
# Without this, timelines grow unbounded → memory disaster
LTRIM timeline:2001 0 799
LTRIM timeline:2002 0 799
LTRIM timeline:2003 0 799
LTRIM timeline:2004 0 799
LTRIM timeline:2005 0 799

# =============================================
# STEP 3: User "bob" (id: 2001) opens their home timeline
# =============================================

# Fetch the latest 20 tweet IDs (page 1)
LRANGE timeline:2001 0 19
# Returns: ["50001", "49998", "49995", ...]

# For each tweet ID, hydrate with full data (use pipeline in production!)
HGETALL tweet:50001
# Returns all tweet fields

# For pagination (page 2):
LRANGE timeline:2001 20 39

# =============================================
# STEP 4: Hybrid fan-out for celebrities
# =============================================

# Problem: Celebrity with 50M followers → fan-out takes too long
# Solution: DON'T fan-out for celebrities. Mix at read time.

# Check follower count
HGET user:1001:profile follower_count
# If > 500,000 → celebrity, skip fan-out

# When bob reads his timeline:
# 1. Get precomputed timeline (regular users' tweets)
LRANGE timeline:2001 0 19

# 2. Get celebrity tweets bob follows (pull model)
SMEMBERS user:2001:following_celebrities
# Returns: ["celeb:3001", "celeb:3002"]

LRANGE tweets_by:3001 0 4
LRANGE tweets_by:3002 0 4

# 3. Merge + sort by timestamp in application layer

# =============================================
# STEP 5: Engagement updates (async)
# =============================================

# When someone likes tweet 50001
HINCRBY tweet:50001 like_count 1

# When someone retweets
HINCRBY tweet:50001 retweet_count 1

# When someone replies
HINCRBY tweet:50001 reply_count 1

# =============================================
# STEP 6: Delete tweet
# =============================================

# Soft delete — mark as deleted, remove from cache
HSET tweet:50001 deleted "true"
# Fan-out removal is expensive, so typically:
# - Filter deleted tweets at read time
# - Async background job cleans timelines

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why not store the full tweet JSON in the list?
# A: Denormalization trap! If tweet text is edited or like count
#    changes, you'd need to update it in millions of timelines.
#    Store only tweet IDs in timelines, hydrate on read.

# Q: Why 800 limit on timeline?
# A: 99% of users don't scroll past 200 tweets. 800 gives buffer.
#    Old tweets fetched from cold storage (Cassandra/Manhattan).
#    Memory: 800 tweet IDs × 8 bytes = ~6.4KB per user.
#    100M users × 6.4KB = ~640GB → sharded across Redis cluster.

# Q: What if Redis goes down?
# A: Timelines are a CACHE, not source of truth. Rebuilt from
#    tweet storage (Cassandra). Fan-out workers replay recent tweets.
#    User sees slightly stale feed temporarily.
```

---

## 2. Twitter — Trending Hashtags

### 🏗️ Architecture Context

Twitter's trending topics show what's popular right now. The system needs to handle millions of tweets per minute, count hashtag mentions in real-time, apply time-decay (recent mentions matter more), and filter by geography.

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `trending:global:{hour}` | Sorted Set | Global hashtag counts for this hour | 6h |
| `trending:geo:{country}:{hour}` | Sorted Set | Country-specific hashtag counts | 6h |
| `trending:global:current` | Sorted Set | Aggregated current trending (weighted) | 5min |
| `trending:blacklist` | Set | Suppressed/banned hashtags | None |
| `trending:history:{date}` | Sorted Set | Historical trending for the day | 30d |

### 💡 Why These Data Structures?

- **Sorted Set for trending**: Score = mention count. ZINCRBY is O(log N) atomic increment. ZRANGE with REV gives top-K instantly. ZUNIONSTORE merges hourly windows with weights for time-decay.
- **Set for blacklist**: O(1) SISMEMBER check before adding hashtag to trending.

### 🔧 Commands — Step by Step

```redis
# =============================================
# STEP 1: Tweet arrives with hashtags
# =============================================

# Tweet: "Just tried #Redis for caching, works great with #NodeJS! #SystemDesign"
# Extract hashtags: #Redis, #NodeJS, #SystemDesign

# Current hour window: 2024-01-15T14 (2pm)
# Atomic increment for each hashtag

# First check blacklist
SISMEMBER trending:blacklist "#Redis"
# Returns 0 → not blacklisted, proceed

ZINCRBY trending:global:2024-01-15T14 1 "#Redis"
ZINCRBY trending:global:2024-01-15T14 1 "#NodeJS"
ZINCRBY trending:global:2024-01-15T14 1 "#SystemDesign"

# Also increment geo-specific (user is from US)
ZINCRBY trending:geo:US:2024-01-15T14 1 "#Redis"
ZINCRBY trending:geo:US:2024-01-15T14 1 "#NodeJS"
ZINCRBY trending:geo:US:2024-01-15T14 1 "#SystemDesign"

# Set TTL on hourly windows
EXPIRE trending:global:2024-01-15T14 21600
EXPIRE trending:geo:US:2024-01-15T14 21600

# =============================================
# STEP 2: Simulate many tweets over time
# =============================================

# Hour 13 (1pm) — previous hour
ZADD trending:global:2024-01-15T13 500 "#Redis" 800 "#WorldCup" 300 "#AI" 200 "#NodeJS" 150 "#CES2024"

# Hour 14 (2pm) — current hour
ZADD trending:global:2024-01-15T14 400 "#Redis" 1200 "#WorldCup" 600 "#AI" 100 "#TypeScript" 350 "#SystemDesign"

# Hour 15 (3pm) — most recent
ZADD trending:global:2024-01-15T15 200 "#Redis" 2000 "#WorldCup" 900 "#AI" 800 "#BreakingNews" 500 "#CES2024"

# =============================================
# STEP 3: Compute current trending (weighted aggregation)
# =============================================

# More recent hours get higher weight
# Weight: 3 hours ago = 1, 2 hours ago = 2, current hour = 4
ZUNIONSTORE trending:global:current 3 \
  trending:global:2024-01-15T13 \
  trending:global:2024-01-15T14 \
  trending:global:2024-01-15T15 \
  WEIGHTS 1 2 4

# Set short TTL — recomputed frequently
EXPIRE trending:global:current 300

# View current global trending top 10
ZRANGE trending:global:current 0 9 REV WITHSCORES
# Expected (roughly):
# #WorldCup: 800 + 2400 + 8000 = 11200
# #AI: 300 + 1200 + 3600 = 5100
# #BreakingNews: 0 + 0 + 3200 = 3200
# #CES2024: 150 + 0 + 2000 = 2150
# #Redis: 500 + 800 + 800 = 2100
# ...

# =============================================
# STEP 4: Geo-specific trending (user is in India)
# =============================================

# Same aggregation but for India
ZADD trending:geo:IN:2024-01-15T13 1000 "#CricketWorldCup" 200 "#Bollywood" 100 "#AI"
ZADD trending:geo:IN:2024-01-15T14 1500 "#CricketWorldCup" 300 "#Bollywood" 400 "#IPLAuction"
ZADD trending:geo:IN:2024-01-15T15 2000 "#CricketWorldCup" 500 "#IPLAuction" 800 "#Modi"

ZUNIONSTORE trending:geo:IN:current 3 \
  trending:geo:IN:2024-01-15T13 \
  trending:geo:IN:2024-01-15T14 \
  trending:geo:IN:2024-01-15T15 \
  WEIGHTS 1 2 4

ZRANGE trending:geo:IN:current 0 9 REV WITHSCORES
# #CricketWorldCup dominates in India!

# =============================================
# STEP 5: Trending with volume spike detection
# =============================================

# Just counting isn't enough — "#goodmorning" gets lots of mentions
# every day but isn't "trending". We need SPIKE detection.

# Store baseline average for each hashtag
HSET hashtag:baseline "#Redis" "200" "#goodmorning" "5000"

# Current hour count
ZSCORE trending:global:2024-01-15T15 "#Redis"
# Returns 200

# Check if it's a spike:
# If current_count > baseline × 3 → trending!
# #Redis: 200 vs baseline 200 → 1x → NOT trending (stable)
# #WorldCup: 2000 vs baseline 100 → 20x → TRENDING! 🔥

# This spike calculation happens in application logic
# Redis just provides the raw counts

# =============================================
# STEP 6: Content moderation — blacklist a hashtag
# =============================================

SADD trending:blacklist "#spam_topic" "#offensive_term"

# Before adding to trending counts:
SISMEMBER trending:blacklist "#spam_topic"
# Returns 1 → skip this hashtag!

# Remove from current trending
ZREM trending:global:current "#spam_topic"

# =============================================
# STEP 7: Historical trending (for analytics)
# =============================================

# End of day: snapshot the top 50 into history
ZRANGESTORE trending:history:2024-01-15 trending:global:current 0 49 REV
EXPIRE trending:history:2024-01-15 2592000

# Query: "What was trending on Jan 15?"
ZRANGE trending:history:2024-01-15 0 9 REV WITHSCORES

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why sorted set instead of a simple counter?
# A: ZINCRBY gives atomic increment + automatic ranking.
#    ZRANGE REV gives top-K in O(log N + K) — no sorting needed!
#    Regular counters would need: read all → sort → take top K.

# Q: Why hourly windows instead of one big sorted set?
# A: Time decay! Old mentions should matter less.
#    ZUNIONSTORE with WEIGHTS gives exponential decay.
#    Also: hourly windows auto-expire, no manual cleanup.

# Q: How to handle 500K tweets/sec?
# A: 1) Client-side batching: aggregate locally for 100ms, then ZINCRBY batch
#    2) Sharding: shard by hashtag first letter across Redis instances
#    3) Two-level counting: in-memory per app server → periodic flush to Redis

# Q: Memory usage?
# A: 100K unique hashtags × ~50 bytes = ~5MB per hourly window
#    6 windows × 5MB = 30MB. Trivial for Redis.

# Cleanup
DEL trending:global:2024-01-15T13 trending:global:2024-01-15T14 trending:global:2024-01-15T15 trending:global:current trending:geo:US:2024-01-15T14 trending:geo:IN:2024-01-15T13 trending:geo:IN:2024-01-15T14 trending:geo:IN:2024-01-15T15 trending:geo:IN:current trending:blacklist trending:history:2024-01-15 hashtag:baseline
```

---

## 3. Instagram — Like Count & Social Graph

### 🏗️ Architecture Context

Instagram handles billions of likes per day. The like count on a post needs to be accurate, the "has user X liked post Y" check must be sub-millisecond (to render the red heart icon), and the social graph (followers/following) drives the entire feed system.

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `post:{post_id}:likes` | Set | Set of user IDs who liked this post | None |
| `post:{post_id}:meta` | Hash | Post metadata (like_count, comment_count, etc.) | None |
| `user:{user_id}:followers` | Set | User's follower IDs | None |
| `user:{user_id}:following` | Set | Users this person follows | None |
| `user:{user_id}:liked_posts` | Sorted Set | Posts user liked (score = timestamp) | None |

### 💡 Why These Data Structures?

- **Set for post likes**: SISMEMBER is O(1) — instant "did I like this?" check needed for every post rendered. SADD is idempotent (double-tapping is safe). SCARD gives exact count.
- **Hash for metadata**: Keeps like_count as a separate counter (HINCRBY) for fast reads without counting the set every time. Denormalized for performance.
- **Sorted Set for liked_posts**: Score = timestamp enables "posts you liked" sorted chronologically. Also enables "undo likes on posts older than X" (ZREMRANGEBYSCORE).

### 🔧 Commands — Step by Step

```redis
# =============================================
# SETUP: Create a post and users
# =============================================

HSET post:9001:meta \
  author_id "1001" \
  caption "Sunset in Barcelona 🌅" \
  image_url "cdn.instagram.com/img/abc123.jpg" \
  like_count "0" \
  comment_count "0" \
  created_at "1705363200"

HSET user:1001:profile username "alice" display_name "Alice" follower_count "15230" following_count "342"
HSET user:2001:profile username "bob" display_name "Bob" follower_count "892" following_count "567"

# =============================================
# ACTION: Bob likes Alice's post
# =============================================

# Check if already liked (prevent double counting)
SISMEMBER post:9001:likes "2001"
# Returns 0 → not yet liked

# Add like (idempotent — SADD ignores if already present)
SADD post:9001:likes "2001"
# Returns 1 → newly added

# Increment counter (only if SADD returned 1!)
HINCRBY post:9001:meta like_count 1

# Track in bob's liked posts (score = current timestamp)
ZADD user:2001:liked_posts 1705363500 "9001"

# =============================================
# ACTION: Charlie and Diana also like the post
# =============================================

SADD post:9001:likes "3001"
HINCRBY post:9001:meta like_count 1
ZADD user:3001:liked_posts 1705363600 "9001"

SADD post:9001:likes "4001"
HINCRBY post:9001:meta like_count 1
ZADD user:4001:liked_posts 1705363700 "9001"

# =============================================
# READ: Render post in feed
# =============================================

# Get post metadata (includes like count for display)
HMGET post:9001:meta like_count comment_count caption
# Returns: "3", "0", "Sunset in Barcelona 🌅"

# Check if current user (bob, id:2001) liked this post
SISMEMBER post:9001:likes "2001"
# Returns 1 → show red heart ❤️

# Check if current user (eve, id:5001) liked this post
SISMEMBER post:9001:likes "5001"
# Returns 0 → show empty heart 🤍

# =============================================
# ACTION: Bob unlikes the post
# =============================================

# Remove from likes set
SREM post:9001:likes "2001"
# Returns 1 → was removed

# Decrement counter
HINCRBY post:9001:meta like_count -1

# Remove from bob's liked posts
ZREM user:2001:liked_posts "9001"

# Verify
HGET post:9001:meta like_count
# Expected: "2" (charlie + diana)
SISMEMBER post:9001:likes "2001"
# Expected: 0

# =============================================
# READ: "Who liked this post?" (first 3 names)
# =============================================

SRANDMEMBER post:9001:likes 3
# Returns 3 random users who liked it
# App hydrates with names: "Charlie, Diana, and 0 others liked this"

# Get total like count for display
SCARD post:9001:likes
# Returns 2

# =============================================
# SOCIAL GRAPH: Follow/Unfollow
# =============================================

# Bob follows Alice
SADD user:2001:following "1001"
SADD user:1001:followers "2001"
HINCRBY user:2001:profile following_count 1
HINCRBY user:1001:profile follower_count 1

# Charlie follows Alice
SADD user:3001:following "1001"
SADD user:1001:followers "3001"

# Bob follows Charlie
SADD user:2001:following "3001"
SADD user:3001:followers "2001"

# =============================================
# SOCIAL GRAPH: Mutual friends / "Followed by"
# =============================================

# "People you both follow" (bob and diana)
SADD user:4001:following "1001" "3001" "5001"
SINTER user:2001:following user:4001:following
# Returns: "1001", "3001" (both follow Alice and Charlie)

# "Followers you know" (mutual followers)
# When viewing Alice's profile as Bob:
# Show "Followed by Charlie and 1 other you follow"
SINTER user:1001:followers user:2001:following
# Returns users who: (follow alice) AND (bob follows them)

# =============================================
# SOCIAL GRAPH: Friend suggestions
# =============================================

# Suggest users that bob's friends follow, but bob doesn't
SDIFF user:3001:following user:2001:following
# Returns: users Charlie follows that Bob doesn't → suggest these!

# =============================================
# READ: Bob's liked posts timeline
# =============================================

# Most recently liked posts (descending by timestamp)
ZREVRANGE user:2001:liked_posts 0 19 WITHSCORES
# Returns post IDs with like timestamps

# Posts bob liked in January 2024
ZRANGEBYSCORE user:2001:liked_posts 1704067200 1706745600

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why both a Set AND a counter for likes?
# A: Set gives: "has user liked?" (O(1)), "who liked?" (SMEMBERS),
#    exact count (SCARD). But SCARD scans the set.
#    Counter in Hash: O(1) read for displaying "3.2M likes"
#    without scanning a 3.2M-member set.
#    Trade-off: Counter can drift if bugs occur → periodic reconciliation job.

# Q: How does this scale to posts with 10M likes?
# A: 10M-member Set = ~80MB. Too big for Redis!
#    Solution: For viral posts, switch to HyperLogLog for count
#    and check "has user liked?" via Bloom Filter + database fallback.
#    Keep Set-based approach only for posts under 100K likes.

# Q: Why not just use a database for likes?
# A: Every time you scroll Instagram, each post needs:
#    1) like_count — displayed under the post
#    2) did_i_like — determines red vs empty heart
#    That's 2 queries PER POST × 20 posts per scroll = 40 queries.
#    At 500K concurrent users scrolling, that's 20M QPS on DB.
#    Redis handles this at sub-millisecond latency.

# Cleanup
DEL post:9001:likes post:9001:meta user:1001:profile user:2001:profile user:2001:liked_posts user:3001:liked_posts user:4001:liked_posts user:2001:following user:1001:followers user:3001:following user:3001:followers user:4001:following
```

---

## 4. Uber — Real-Time Driver Location

### 🏗️ Architecture Context

Uber needs to track millions of active drivers' GPS locations in real-time, find the closest drivers to a rider in <100ms, and update positions every 4 seconds. Redis Geospatial indexes make this possible.

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `drivers:active:{city}` | Geo (Sorted Set) | Active driver locations per city | None |
| `driver:{driver_id}:meta` | Hash | Driver metadata (name, car, rating, status) | None |
| `driver:{driver_id}:last_ping` | String | Last heartbeat timestamp | 120s |
| `ride:{ride_id}:tracking` | Geo (Sorted Set) | Current ride's driver position | 2h |

### 💡 Why These Data Structures?

- **Geo for locations**: Built on Sorted Sets with geohash scores. GEOSEARCH finds nearest drivers in O(N+log(M)) where N is results, M is total points. Redis Geo is optimized for this exact use case.
- **Separate keys per city**: Limits the search space. Searching 10K drivers in Barcelona is faster than searching 5M globally. Natural sharding boundary.
- **TTL on last_ping**: If driver app crashes, the key expires and we know they're gone. No stale drivers.

### 🔧 Commands — Step by Step

```redis
# =============================================
# STEP 1: Drivers come online and share location
# =============================================

# Driver 1: In Barcelona near Sagrada Familia
GEOADD drivers:active:barcelona 2.1744 41.3851 "driver:1001"
HSET driver:1001:meta name "Carlos" car_model "Toyota Prius" car_color "White" rating "4.8" status "available" vehicle_type "standard"
SET driver:1001:last_ping "1705363200" EX 120

# Driver 2: Near La Rambla
GEOADD drivers:active:barcelona 2.1686 41.3794 "driver:2001"
HSET driver:2001:meta name "Maria" car_model "Seat Leon" car_color "Black" rating "4.9" status "available" vehicle_type "standard"
SET driver:2001:last_ping "1705363200" EX 120

# Driver 3: Near Barceloneta Beach
GEOADD drivers:active:barcelona 2.1894 41.3892 "driver:3001"
HSET driver:3001:meta name "Pablo" car_model "Mercedes E-Class" car_color "Silver" rating "4.7" status "available" vehicle_type "premium"
SET driver:3001:last_ping "1705363200" EX 120

# Driver 4: Near Park Güell (farther away)
GEOADD drivers:active:barcelona 2.1527 41.4145 "driver:4001"
HSET driver:4001:meta name "Anna" car_model "Tesla Model 3" car_color "Red" rating "4.95" status "available" vehicle_type "premium"
SET driver:4001:last_ping "1705363200" EX 120

# Driver 5: In Eixample (busy)
GEOADD drivers:active:barcelona 2.1620 41.3925 "driver:5001"
HSET driver:5001:meta name "Jordi" car_model "Honda Civic" car_color "Blue" rating "4.6" status "on_trip" vehicle_type "standard"
SET driver:5001:last_ping "1705363200" EX 120

# =============================================
# STEP 2: Rider requests a ride near Casa Batlló
# =============================================

# Rider location: Casa Batlló (2.1650, 41.3917)
# Find closest 5 drivers within 3km

GEOSEARCH drivers:active:barcelona \
  FROMLONLAT 2.1650 41.3917 \
  BYRADIUS 3 km \
  ASC \
  WITHCOORD WITHDIST \
  COUNT 5

# Returns drivers sorted by distance:
# 1) "driver:5001" - 0.3km (Jordi — but he's on a trip!)
# 2) "driver:1001" - 0.8km (Carlos — available ✓)
# 3) "driver:2001" - 1.4km (Maria — available ✓)
# 4) "driver:3001" - 2.1km (Pablo — available ✓)
# 5) "driver:4001" - 2.7km (Anna — available ✓)

# =============================================
# STEP 3: Filter available drivers (application logic)
# =============================================

# For each returned driver, check status
HGET driver:5001:meta status
# "on_trip" → skip!

HGET driver:1001:meta status
# "available" → candidate!

HGET driver:2001:meta status
# "available" → candidate!

# Also check if driver's last_ping is recent (not stale)
EXISTS driver:1001:last_ping
# 1 → alive (key hasn't expired)

# Select closest available driver: Carlos (driver:1001)

# =============================================
# STEP 4: Assign ride and start tracking
# =============================================

# Update driver status
HSET driver:1001:meta status "on_trip" current_ride "ride:7001"

# Create ride tracking
GEOADD ride:7001:tracking 2.1744 41.3851 "current"
GEOADD ride:7001:tracking 2.1800 41.4000 "destination"
EXPIRE ride:7001:tracking 7200

HSET ride:7001:meta \
  rider_id "8001" \
  driver_id "1001" \
  pickup_lat "41.3917" \
  pickup_lon "2.1650" \
  dest_lat "41.4000" \
  dest_lon "2.1800" \
  status "driver_en_route" \
  created_at "1705363200"

# =============================================
# STEP 5: Driver location updates every 4 seconds
# =============================================

# Update 1: Driver moved toward rider
GEOADD drivers:active:barcelona 2.1680 41.3890 "driver:1001"
SET driver:1001:last_ping "1705363204" EX 120

# Update ride tracking
GEOADD ride:7001:tracking 2.1680 41.3890 "current"

# Distance to pickup
GEODIST ride:7001:tracking "current" "destination" km
# Returns distance remaining

# Update 2: Driver arrived at pickup
GEOADD drivers:active:barcelona 2.1650 41.3917 "driver:1001"
HSET ride:7001:meta status "arrived_at_pickup"

# Update 3: Trip started, driving to destination
GEOADD drivers:active:barcelona 2.1700 41.3950 "driver:1001"
GEOADD ride:7001:tracking 2.1700 41.3950 "current"
HSET ride:7001:meta status "in_progress"

# =============================================
# STEP 6: Ride completed
# =============================================

# Remove from active drivers geo index
ZREM drivers:active:barcelona "driver:1001"

# Update status
HSET driver:1001:meta status "available" current_ride ""
HSET ride:7001:meta status "completed"

# Re-add driver to geo index at final location
GEOADD drivers:active:barcelona 2.1800 41.4000 "driver:1001"

# =============================================
# STEP 7: Driver goes offline
# =============================================

# Remove from geo index
ZREM drivers:active:barcelona "driver:1001"
HSET driver:1001:meta status "offline"
DEL driver:1001:last_ping

# =============================================
# STEP 8: Surge pricing — count drivers in area
# =============================================

# Count available drivers within 2km of high-demand area
GEOSEARCH drivers:active:barcelona \
  FROMLONLAT 2.1700 41.3900 \
  BYRADIUS 2 km \
  COUNT 100

# If count < threshold → enable surge pricing!

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why per-city geo indexes instead of one global index?
# A: 1) Smaller search space = faster queries
#    2) Natural sharding boundary (different Redis instances per city)
#    3) Rider in Barcelona never needs drivers in Madrid

# Q: How to handle drivers at city boundaries?
# A: Drivers near boundaries added to BOTH city indexes.
#    Or use larger regions (country-level) with smaller radius searches.

# Q: Why not just use lat/lon in a hash?
# A: GEOSEARCH does radius queries natively. Without it, you'd need:
#    1) Fetch ALL driver locations
#    2) Calculate Haversine distance for each
#    3) Sort by distance
#    That's O(N) scan vs O(log N + M) geospatial query.

# Q: What about driver location history?
# A: Not in Redis. Stream to Kafka → store in TimescaleDB/Cassandra
#    for trip replay, analytics, and ETA modeling.

# Cleanup
DEL drivers:active:barcelona driver:1001:meta driver:2001:meta driver:3001:meta driver:4001:meta driver:5001:meta driver:1001:last_ping driver:2001:last_ping driver:3001:last_ping driver:4001:last_ping driver:5001:last_ping ride:7001:tracking ride:7001:meta
```

---

## 5. Stripe — Idempotent Payment Processing

### 🏗️ Architecture Context

When a user clicks "Pay" and the network drops, they might retry. Without idempotency, the payment could be processed twice. Stripe uses Redis to ensure each payment request with the same idempotency key is processed exactly once.

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `idempotency:{key}` | String | Stores state: "processing" or JSON result | 24h |
| `lock:payment:{order_id}` | String | Distributed lock for payment processing | 30s |
| `payment:{payment_id}` | Hash | Payment record cache | 7d |

### 🔧 Commands — Step by Step

```redis
# =============================================
# SCENARIO: User pays $99.99 for order #ORD-5001
# Client sends: POST /v1/charges with Idempotency-Key: "idem-abc-123"
# =============================================

# STEP 1: Check idempotency key
GET idempotency:idem-abc-123
# Returns nil → first time seeing this request

# STEP 2: Atomically claim the request (NX = only if not exists)
SET idempotency:idem-abc-123 "processing" NX EX 86400
# Returns "OK" → we own this request
# If returns nil → another instance is already processing it

# STEP 3: Acquire distributed lock on the order
SET lock:payment:ORD-5001 "worker:abc:1705363200" NX EX 30
# Prevents concurrent charges for the same order

# STEP 4: Process payment (call card network, etc.)
# ... application logic ...
# Payment succeeds! Payment ID: PAY-7001

# STEP 5: Store result and update idempotency key
HSET payment:PAY-7001 \
  payment_id "PAY-7001" \
  order_id "ORD-5001" \
  amount "9999" \
  currency "USD" \
  status "succeeded" \
  card_last4 "4242" \
  created_at "1705363200"
EXPIRE payment:PAY-7001 604800

# Overwrite "processing" with actual result
SET idempotency:idem-abc-123 \
  "{\"payment_id\":\"PAY-7001\",\"status\":\"succeeded\",\"amount\":9999}" \
  EX 86400

# Release lock
EVAL "\
if redis.call('GET', KEYS[1]) == ARGV[1] then\
    return redis.call('DEL', KEYS[1])\
else\
    return 0\
end" 1 lock:payment:ORD-5001 "worker:abc:1705363200"

# =============================================
# SCENARIO: Duplicate request arrives (retry)
# =============================================

# Same idempotency key
GET idempotency:idem-abc-123
# Returns: {"payment_id":"PAY-7001","status":"succeeded","amount":9999}

# NOT nil → this is a duplicate!
# Return the cached response. Payment NOT processed again. ✓

# =============================================
# SCENARIO: Concurrent duplicate while processing
# =============================================

# Request 1 is still processing...
GET idempotency:idem-abc-123
# Returns "processing"

# Concurrent Request 2 sees "processing" → two options:
# Option A: Return 409 Conflict
# Option B: Wait briefly and retry

# If too much time passes (processing took > 60s, likely crashed):
# Application can check timestamp embedded in processing state
# and reset the idempotency key to allow retry

# =============================================
# SCENARIO: Processing crashes before completing
# =============================================

# Worker set "processing" but crashed before storing result
# Key is: idempotency:idem-abc-123 = "processing" with TTL 86400

# Option 1: Short TTL for "processing" state specifically
SET idempotency:idem-abc-123 "{\"state\":\"processing\",\"started_at\":1705363200}" NX EX 300
# If not completed in 5 minutes, key expires, client can retry

# Option 2: Dead letter / recovery job
# Background job scans for "processing" keys older than X minutes
# Marks them as "failed", allowing retry

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why Redis instead of database for idempotency?
# A: 1) Checking idempotency key happens on EVERY request — must be fast
#    2) NX flag gives atomic check-and-set (prevents races)
#    3) TTL gives automatic cleanup (no cron job needed)
#    4) Sub-millisecond latency vs 5-10ms for database query

# Q: What if Redis goes down?
# A: Fallback to database-level uniqueness constraint.
#    Order table has UNIQUE(idempotency_key) column.
#    Double-charging is prevented at DB level too — Redis is the fast path.

# Q: Why separate lock AND idempotency key?
# A: Idempotency key is per-request (same request retried).
#    Lock is per-resource (same order from different requests).
#    User might submit two DIFFERENT requests for same order.

# Cleanup
DEL idempotency:idem-abc-123 lock:payment:ORD-5001 payment:PAY-7001
```

---

## 6. Slack — Online Presence System

### 🏗️ Architecture Context

Slack shows a green dot next to users who are online. With millions of users across thousands of workspaces, this needs to be both real-time and resource-efficient. Slack uses a combination of heartbeats and smart batching.

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `presence:{user_id}` | String | Current status ("active"/"away"/"dnd") | 90s |
| `workspace:{ws_id}:online` | Sorted Set | Online members (score = last activity time) | None |
| `user:{user_id}:status` | Hash | Custom status (emoji, text, expiry) | varies |
| `channel:{ch_id}:viewers` | Set | Users currently viewing this channel | None |

### 🔧 Commands — Step by Step

```redis
# =============================================
# STEP 1: User comes online
# =============================================

# Alice opens Slack
SET presence:alice "active" EX 90

# Add to workspace online set with current timestamp
ZADD workspace:W001:online 1705363200 "alice"

# Set custom status
HSET user:alice:status emoji "🔴" text "In a meeting" expires_at "1705366800"

# =============================================
# STEP 2: Heartbeat (every 30 seconds)
# =============================================

# Client sends heartbeat → reset TTL
SET presence:alice "active" EX 90

# Update last activity timestamp
ZADD workspace:W001:online 1705363230 "alice"

# =============================================
# STEP 3: More users come online
# =============================================

SET presence:bob "active" EX 90
ZADD workspace:W001:online 1705363210 "bob"

SET presence:charlie "active" EX 90
ZADD workspace:W001:online 1705363220 "charlie"

SET presence:diana "away" EX 90
ZADD workspace:W001:online 1705363200 "diana"

# =============================================
# STEP 4: Query who's online in workspace
# =============================================

# Get all online users (active in last 90 seconds)
# Assume now = 1705363260
ZRANGEBYSCORE workspace:W001:online 1705363170 +inf
# Returns users with activity in last 90s

# Count online users
ZCOUNT workspace:W001:online 1705363170 +inf

# Clean stale users (no heartbeat for > 90s)
ZREMRANGEBYSCORE workspace:W001:online 0 1705363170

# =============================================
# STEP 5: Check specific user's presence
# =============================================

# Is Bob online?
GET presence:bob
# "active" → online
# nil → offline (key expired, no heartbeat)

# Get Bob's custom status
HGETALL user:bob:status
# Check if status has expired
# if expires_at < now → status expired, clear it

# =============================================
# STEP 6: User goes idle (no keyboard/mouse for 10 min)
# =============================================

SET presence:alice "away" EX 90

# Client continues sending heartbeats (to distinguish away vs offline)
# "away" = app open but idle
# nil (expired) = app closed / disconnected

# =============================================
# STEP 7: User goes into Do Not Disturb
# =============================================

SET presence:alice "dnd" EX 90
HSET user:alice:status emoji "🔕" text "Do not disturb" expires_at "1705377600"

# =============================================
# STEP 8: Channel viewers (for typing indicators)
# =============================================

# Alice opens #general channel
SADD channel:C001:viewers "alice"

# Bob is also viewing #general
SADD channel:C001:viewers "bob"

# Who's viewing #general right now?
SMEMBERS channel:C001:viewers
# Returns: alice, bob

# Alice navigates away
SREM channel:C001:viewers "alice"

# =============================================
# STEP 9: Batch presence queries (efficiency)
# =============================================

# Loading a channel with 50 members — need presence for all
# Instead of 50 separate GET commands, use pipeline:
# (In application code)
# pipeline.get("presence:alice")
# pipeline.get("presence:bob")
# pipeline.get("presence:charlie")
# ... 50 commands in single round trip

# Or for the sorted set approach:
ZRANGEBYSCORE workspace:W001:online 1705363170 +inf
# One command gets ALL online users — then intersect with channel members

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why 90-second TTL with 30-second heartbeat?
# A: 3 missed heartbeats before declaring offline.
#    Handles temporary network blips without false "offline" signals.
#    If heartbeat fails 3 times → user truly disconnected.

# Q: Why Sorted Set AND String keys?
# A: String (with TTL): "Is user X online?" → O(1), auto-cleanup
#    Sorted Set: "Who's online in workspace?" → range query, batch operations
#    Using both gives best of both worlds.

# Q: How to handle 10M concurrent users?
# A: 1) Shard workspaces across Redis instances
#    2) Presence fanout only to VISIBLE contacts (lazy approach)
#    3) Batch heartbeats (one request per 30s per user)
#    4) Don't push every status change to every user — poll on demand

# Cleanup
DEL presence:alice presence:bob presence:charlie presence:diana workspace:W001:online user:alice:status user:bob:status channel:C001:viewers
```

---

## 7. Netflix — Session Management

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `session:{token}` | Hash | Session data (user_id, device, plan, etc.) | 24h |
| `user:{user_id}:sessions` | Set | All active session tokens for a user | None |
| `user:{user_id}:device_limit` | String | Current active stream count | None |

### 🔧 Commands

```redis
# =============================================
# User logs in from Smart TV
# =============================================

# Generate session token (UUID in practice)
HSET session:tok_abc123 \
  user_id "1001" \
  email "user@example.com" \
  plan "premium" \
  max_streams "4" \
  device_type "smart_tv" \
  device_name "Living Room Samsung" \
  ip_address "88.10.20.30" \
  country "ES" \
  login_time "1705363200" \
  last_active "1705363200"
EXPIRE session:tok_abc123 86400

# Track this session under the user
SADD user:1001:sessions "tok_abc123"

# =============================================
# User logs in from phone (second device)
# =============================================

HSET session:tok_def456 \
  user_id "1001" \
  device_type "mobile" \
  device_name "iPhone 15" \
  login_time "1705363500"
EXPIRE session:tok_def456 86400

SADD user:1001:sessions "tok_def456"

# =============================================
# Auth middleware — validate every API request
# =============================================

# Request comes with Authorization: Bearer tok_abc123
HGETALL session:tok_abc123
# Returns session data → user is authenticated

# Refresh TTL (sliding session)
EXPIRE session:tok_abc123 86400

# Update last_active
HSET session:tok_abc123 last_active "1705366800"

# If HGETALL returns empty → session expired/invalid → redirect to login

# =============================================
# Stream limiting — enforce max concurrent streams
# =============================================

# User starts streaming
INCR user:1001:active_streams
GET user:1001:active_streams
# "1" → allowed (premium plan: max 4)

# User starts stream on another device
INCR user:1001:active_streams
# "2" → still allowed

# Check before allowing new stream
GET user:1001:active_streams
# If >= max_streams → deny with "Too many streams" error

# User stops streaming
DECR user:1001:active_streams

# =============================================
# Force logout from all devices
# =============================================

# Get all sessions
SMEMBERS user:1001:sessions
# Returns: ["tok_abc123", "tok_def456"]

# Delete each session
DEL session:tok_abc123 session:tok_def456

# Clear session set
DEL user:1001:sessions

# Reset stream count
DEL user:1001:active_streams

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why Hash instead of serialized JSON string?
# A: Partial reads! Auth middleware only needs user_id + plan.
#    HMGET session:tok_abc123 user_id plan → 2 fields, not entire blob.
#    Also: individual field updates without read-modify-write.

# Q: Why not JWT instead of Redis sessions?
# A: Netflix needs to: force logout, limit streams, track devices.
#    JWT can't be invalidated server-side (until it expires).
#    Redis sessions: DEL session:token → instant logout.

# Q: Memory estimation?
# A: Session hash: ~200 bytes × 200M active sessions = ~40GB
#    Sharded across Redis cluster: ~4GB per shard (10 shards)

# Cleanup
DEL session:tok_abc123 session:tok_def456 user:1001:sessions user:1001:active_streams
```

---

## 8. YouTube — Video View Counter

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `views:video:{video_id}` | String (counter) | Total view count | None |
| `views:video:{video_id}:hourly:{hour}` | String (counter) | Views this hour | 48h |
| `views:video:{video_id}:unique` | HyperLogLog | Approximate unique viewers | 30d |
| `views:realtime:{minute}` | Sorted Set | Videos with most views this minute | 10min |

### 🔧 Commands

```redis
# =============================================
# A user watches video VID-5001
# =============================================

# Increment total views (atomic, handles concurrent views)
INCR views:video:VID-5001
# O(1), single-threaded, no race conditions

# Increment hourly counter (for analytics graphs)
INCR views:video:VID-5001:hourly:2024-01-15T14
EXPIRE views:video:VID-5001:hourly:2024-01-15T14 172800

# Track unique viewers (HyperLogLog — 12KB regardless of viewer count!)
PFADD views:video:VID-5001:unique "user:8001"

# Track for real-time trending
ZINCRBY views:realtime:2024-01-15T14:30 1 "VID-5001"
EXPIRE views:realtime:2024-01-15T14:30 600

# =============================================
# Simulate many views
# =============================================

# 1000 views in the last minute
INCRBY views:video:VID-5001 1000
INCRBY views:video:VID-5001:hourly:2024-01-15T14 1000

# Many unique viewers
PFADD views:video:VID-5001:unique "user:8002" "user:8003" "user:8004" "user:8005"

# =============================================
# Read view count (displayed on video page)
# =============================================

GET views:video:VID-5001
# Returns: "1001" → display "1K views"

# Unique viewer count (for creator analytics)
PFCOUNT views:video:VID-5001:unique
# Returns: ~5 (approximate but ~12KB memory vs exact set)

# Hourly breakdown (for analytics graph)
MGET views:video:VID-5001:hourly:2024-01-15T10 \
     views:video:VID-5001:hourly:2024-01-15T11 \
     views:video:VID-5001:hourly:2024-01-15T12 \
     views:video:VID-5001:hourly:2024-01-15T13 \
     views:video:VID-5001:hourly:2024-01-15T14

# Real-time trending videos right now
ZRANGE views:realtime:2024-01-15T14:30 0 9 REV WITHSCORES

# =============================================
# SHARDED COUNTER for viral videos (10M+ views/sec)
# =============================================

# Problem: INCR on single key → single Redis thread → ~200K ops/sec max
# For a viral video getting 10M views/sec → bottleneck!

# Solution: Shard the counter
# Client randomly picks shard 0-99
INCR views:video:VID-5001:shard:42
INCR views:video:VID-5001:shard:87
INCR views:video:VID-5001:shard:03

# Read total: sum all 100 shards (use Lua or pipeline)
EVAL "\
local total = 0\
for i = 0, 99 do\
    local v = redis.call('GET', KEYS[1] .. ':shard:' .. i)\
    if v then total = total + tonumber(v) end\
end\
return total" 1 "views:video:VID-5001"

# 100 shards × 200K ops/sec = 20M ops/sec capacity ✓

# =============================================
# Periodic flush to database
# =============================================

# Every 5 minutes, a background job:
# 1. Reads and resets the counter
GETDEL views:video:VID-5001
# Returns accumulated count, key deleted

# 2. Writes to database: UPDATE videos SET view_count = view_count + N

# This reduces database writes from 10M/sec to 1/5min

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why not just use database for view counts?
# A: At YouTube scale (800M videos, 1B+ daily views):
#    Direct DB writes would need millions of UPDATE/sec.
#    Redis accumulates counts in memory, periodic batch flush to DB.

# Q: Why HyperLogLog for unique viewers?
# A: Popular video: 100M unique viewers
#    Set: 100M × 8 bytes = 800MB per video!
#    HLL: 12KB per video. For 1M videos = 12GB vs 800TB.

# Q: What about bot/spam views?
# A: Not Redis's job. Application layer deduplicates using:
#    1) Rate limiting per IP (Redis rate limiter)
#    2) Bloom filter for recently counted IP+video combinations
#    3) ML model for view validation (async)

# Cleanup
DEL views:video:VID-5001 views:video:VID-5001:hourly:2024-01-15T14 views:video:VID-5001:unique views:realtime:2024-01-15T14:30 views:video:VID-5001:shard:42 views:video:VID-5001:shard:87 views:video:VID-5001:shard:03
```

---

## 9. WhatsApp — Unread Message Counter

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `unread:{user_id}:{chat_id}` | String (counter) | Unread messages in a specific chat | None |
| `unread:{user_id}:total` | String (counter) | Total unread badge count | None |
| `chat:{chat_id}:last_msg` | Hash | Last message preview for chat list | 30d |

### 🔧 Commands

```redis
# =============================================
# Alice sends a message to Bob in chat CH-001
# =============================================

# Increment bob's unread counter for this chat
INCR unread:bob:CH-001
# Returns: 1

# Increment bob's total unread badge
INCR unread:bob:total
# Returns: 1

# Store last message preview (for chat list screen)
HSET chat:CH-001:last_msg \
  sender "alice" \
  text "Hey, are you coming tonight?" \
  timestamp "1705363200" \
  type "text"

# =============================================
# Alice sends 2 more messages
# =============================================

INCR unread:bob:CH-001
INCR unread:bob:total
HSET chat:CH-001:last_msg sender "alice" text "Let me know!" timestamp "1705363210" type "text"

INCR unread:bob:CH-001
INCR unread:bob:total

# =============================================
# Bob opens the app — see badge on icon
# =============================================

GET unread:bob:total
# Returns: "3" → show "3" on app icon badge

# =============================================
# Bob opens chat list — see per-chat unread counts
# =============================================

# For each chat bob is in:
GET unread:bob:CH-001
# Returns: "3" → show (3) next to Alice's name

GET unread:bob:CH-002
# Returns: nil → 0 unread from Charlie

# Get last message preview for each chat
HGETALL chat:CH-001:last_msg
# Returns: sender=alice, text="Let me know!", timestamp=1705363210

# =============================================
# Bob reads the chat (opens conversation with Alice)
# =============================================

# Get current unread count for this chat
GET unread:bob:CH-001
# Returns: "3"

# Subtract from total badge
DECRBY unread:bob:total 3

# Reset per-chat counter
SET unread:bob:CH-001 "0"

# Verify
GET unread:bob:total
# Returns: "0" (assuming no other unreads)
GET unread:bob:CH-001
# Returns: "0"

# =============================================
# GROUP CHAT: 5 members, Alice sends message
# =============================================

# Members of group GRP-001: alice, bob, charlie, diana, eve
# Alice is the sender → don't increment her counter

INCR unread:bob:GRP-001
INCR unread:bob:total
INCR unread:charlie:GRP-001
INCR unread:charlie:total
INCR unread:diana:GRP-001
INCR unread:diana:total
INCR unread:eve:GRP-001
INCR unread:eve:total

# In production: pipeline these 8 commands in 1 round-trip

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why not count unread from message table?
# A: "SELECT COUNT(*) WHERE chat_id=X AND timestamp > last_read"
#    on every app open with billions of messages = disaster.
#    Pre-computed counters in Redis: O(1) reads.

# Q: Counter drift problem?
# A: If INCR succeeds but message save fails → counter is wrong.
#    Solutions:
#    1) Lua script: save message + increment atomically
#    2) Periodic reconciliation job: recount from message DB
#    3) Accept small drift (pragmatic at WhatsApp scale)

# Cleanup
DEL unread:bob:CH-001 unread:bob:CH-002 unread:bob:total unread:bob:GRP-001 unread:charlie:GRP-001 unread:charlie:total unread:diana:GRP-001 unread:diana:total unread:eve:GRP-001 unread:eve:total chat:CH-001:last_msg
```

---

## 10. Amazon — Shopping Cart

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `cart:{user_id}` | Hash | Cart items (field=product_id, value=quantity) | 30d |
| `cart:{user_id}:saved` | Hash | "Save for later" items | 90d |
| `product:{product_id}:cache` | Hash | Cached product data (name, price, stock) | 1h |

### 🔧 Commands

```redis
# =============================================
# User adds items to cart
# =============================================

# Add 2 units of Product A
HSET cart:user:1001 "PROD-A" "2"

# Add 1 unit of Product B
HSET cart:user:1001 "PROD-B" "1"

# Add 3 units of Product C
HSET cart:user:1001 "PROD-C" "3"

# Set cart expiry (30 days)
EXPIRE cart:user:1001 2592000

# =============================================
# View cart
# =============================================

# Get all items + quantities
HGETALL cart:user:1001
# Returns: PROD-A=2, PROD-B=1, PROD-C=3

# Cart item count (not quantity — number of distinct products)
HLEN cart:user:1001
# Returns: 3

# Check if specific product is in cart
HEXISTS cart:user:1001 "PROD-A"
# Returns: 1 (yes)

# =============================================
# Update quantity
# =============================================

# Change Product A quantity to 5
HSET cart:user:1001 "PROD-A" "5"

# Increment quantity by 1
HINCRBY cart:user:1001 "PROD-B" 1
# Returns: 2

# Decrement (but check for zero in app!)
HINCRBY cart:user:1001 "PROD-C" -1
# Returns: 2

# =============================================
# Remove item from cart
# =============================================

HDEL cart:user:1001 "PROD-B"

HGETALL cart:user:1001
# Returns: PROD-A=5, PROD-C=2

# =============================================
# "Save for later"
# =============================================

# Move PROD-A from cart to saved
HGET cart:user:1001 "PROD-A"
# Returns: "5"
HSET cart:user:1001:saved "PROD-A" "5"
HDEL cart:user:1001 "PROD-A"
EXPIRE cart:user:1001:saved 7776000

# Move back to cart
HGET cart:user:1001:saved "PROD-A"
HSET cart:user:1001 "PROD-A" "5"
HDEL cart:user:1001:saved "PROD-A"

# =============================================
# Cart merge (guest → logged in)
# =============================================

# Guest had items in cart
HSET cart:guest:SESS-XYZ "PROD-D" "1" "PROD-C" "2"

# User logs in → merge guest cart into user cart
# For each item in guest cart, add to user cart if not exists
# Or take max quantity if already exists

EVAL "\
local guest_items = redis.call('HGETALL', KEYS[1])\
for i = 1, #guest_items, 2 do\
    local product = guest_items[i]\
    local qty = tonumber(guest_items[i+1])\
    local existing = tonumber(redis.call('HGET', KEYS[2], product) or '0')\
    if qty > existing then\
        redis.call('HSET', KEYS[2], product, qty)\
    end\
end\
redis.call('DEL', KEYS[1])\
return 'OK'" 2 cart:guest:SESS-XYZ cart:user:1001

HGETALL cart:user:1001
# Merged cart!

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Why Hash and not a JSON string?
# A: Add/remove/update single items without reading entire cart.
#    HSET = O(1) vs GET → parse JSON → modify → SET = O(N).
#    Concurrent add from mobile + web won't conflict.

# Q: Why 30-day TTL?
# A: Abandoned carts waste memory. 30 days is industry standard.
#    Amazon also persists carts to DynamoDB for long-term storage.
#    Redis is the fast cache; DynamoDB is source of truth.

# Cleanup
DEL cart:user:1001 cart:user:1001:saved cart:guest:SESS-XYZ
```

---

## 11. Google — API Rate Limiter

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `ratelimit:{api_key}:{window}` | String (counter) | Fixed window counter | window_size |
| `ratelimit:sliding:{api_key}` | Sorted Set | Sliding window log | window_size |
| `ratelimit:tokens:{api_key}` | String | Token bucket: current tokens | auto |

### 🔧 Commands — All 3 Approaches

```redis
# =============================================
# APPROACH 1: Fixed Window Counter
# =============================================

# Config: 100 requests per minute per API key
# Window: current minute (e.g., 2024-01-15T14:30)

# Request comes in
INCR ratelimit:API-KEY-001:2024-01-15T14:30
# Returns current count

# Set TTL only on first request (Lua for atomicity)
EVAL "\
local count = redis.call('INCR', KEYS[1])\
if count == 1 then\
    redis.call('EXPIRE', KEYS[1], 60)\
end\
if count > tonumber(ARGV[1]) then\
    return 0\
else\
    return count\
end" 1 "ratelimit:API-KEY-001:2024-01-15T14:30" "100"
# Returns 0 if rate limited, else current count

# ⚠️ Problem: 99 requests at :59, 99 requests at :01 = 198 in 2 seconds!

# =============================================
# APPROACH 2: Sliding Window Log (Exact)
# =============================================

# Each request adds timestamp to sorted set
EVAL "\
local key = KEYS[1]\
local now = tonumber(ARGV[1])\
local window = tonumber(ARGV[2])\
local limit = tonumber(ARGV[3])\
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\
local count = redis.call('ZCARD', key)\
if count >= limit then\
    return 0\
end\
redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))\
redis.call('EXPIRE', key, window)\
return limit - count" 1 "ratelimit:sliding:API-KEY-001" "1705363230" "60" "100"
# Returns remaining quota, 0 = rate limited

# =============================================
# APPROACH 3: Token Bucket (Most Flexible)
# =============================================

EVAL "\
local tokens_key = KEYS[1]\
local ts_key = KEYS[2]\
local rate = tonumber(ARGV[1])\
local capacity = tonumber(ARGV[2])\
local now = tonumber(ARGV[3])\
local requested = tonumber(ARGV[4])\
local last_tokens = tonumber(redis.call('GET', tokens_key) or capacity)\
local last_time = tonumber(redis.call('GET', ts_key) or now)\
local delta = math.max(0, now - last_time)\
local filled = math.min(capacity, last_tokens + (delta * rate))\
local allowed = filled >= requested\
if allowed then filled = filled - requested end\
local ttl = math.ceil(capacity / rate) * 2\
redis.call('SETEX', tokens_key, ttl, filled)\
redis.call('SETEX', ts_key, ttl, now)\
if allowed then return 1 else return 0 end\
" 2 "ratelimit:tokens:API-KEY-001" "ratelimit:tokens:API-KEY-001:ts" \
  "1.67" "100" "1705363230" "1"
# rate=1.67 tokens/sec (100/min), capacity=100, request 1 token
# Returns 1=allowed, 0=denied
# Token bucket allows short bursts up to capacity

# =============================================
# Response headers (application builds from Redis data)
# =============================================

# X-RateLimit-Limit: 100
# X-RateLimit-Remaining: 95
# X-RateLimit-Reset: 1705363260 (Unix timestamp when window resets)
# Retry-After: 30 (seconds, only when rate limited)

# =============================================
# KEY INSIGHTS FOR INTERVIEWS
# =============================================

# Q: Which approach to use?
# Fixed Window: Simple, O(1), slight inaccuracy at boundaries
# Sliding Log: Exact, but O(N) memory where N = requests in window
# Token Bucket: Flexible (allows bursts), O(1) space, industry standard

# Q: How does Cloudflare/AWS do it?
# A: Usually token bucket + distributed counting.
#    Each edge node has local bucket, syncs periodically.
#    Allows slight over-limit but prevents massive abuse.

# Cleanup
DEL ratelimit:API-KEY-001:2024-01-15T14:30 ratelimit:sliding:API-KEY-001 ratelimit:tokens:API-KEY-001 ratelimit:tokens:API-KEY-001:ts
```

---

## 12. DoorDash — Real-Time Order Tracking

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `order:{order_id}:status` | Hash | Order status + metadata | 24h |
| `order:{order_id}:events` | Stream | Event log for the order | 7d |
| `dasher:{dasher_id}:location` | Geo | Dasher's current position | None |
| `customer:{cust_id}:active_orders` | Set | Customer's current active orders | 24h |

### 🔧 Commands

```redis
# =============================================
# STEP 1: Order placed
# =============================================

HSET order:ORD-3001:status \
  status "placed" \
  restaurant "Burger Palace" \
  customer_id "C-5001" \
  dasher_id "" \
  items "2x Cheeseburger, 1x Fries, 1x Coke" \
  total "24.99" \
  placed_at "1705363200" \
  estimated_delivery "1705365000"
EXPIRE order:ORD-3001:status 86400

XADD order:ORD-3001:events * event "ORDER_PLACED" detail "Order confirmed"
EXPIRE order:ORD-3001:events 604800

SADD customer:C-5001:active_orders "ORD-3001"

# =============================================
# STEP 2: Restaurant confirms & starts preparing
# =============================================

HSET order:ORD-3001:status status "preparing" restaurant_confirmed_at "1705363320"
XADD order:ORD-3001:events * event "RESTAURANT_CONFIRMED" detail "Burger Palace is preparing your order"

# =============================================
# STEP 3: Dasher assigned
# =============================================

HSET order:ORD-3001:status status "dasher_assigned" dasher_id "D-7001" dasher_name "Alex"
XADD order:ORD-3001:events * event "DASHER_ASSIGNED" detail "Alex is picking up your order"

# Dasher location
GEOADD dasher:D-7001:location 2.1700 41.3900 "current"

# =============================================
# STEP 4: Dasher picks up order
# =============================================

HSET order:ORD-3001:status status "picked_up" picked_up_at "1705363800"
XADD order:ORD-3001:events * event "PICKED_UP" detail "Alex picked up your order"

# =============================================
# STEP 5: Dasher en route — location updates every 5s
# =============================================

GEOADD dasher:D-7001:location 2.1720 41.3920 "current"
GEOADD dasher:D-7001:location 2.1750 41.3950 "current"
GEOADD dasher:D-7001:location 2.1780 41.3970 "current"

# Customer app polls for dasher location
GEOPOS dasher:D-7001:location "current"
# Returns [longitude, latitude] → render on map

# ETA: distance ÷ average speed
GEOADD dasher:D-7001:location 2.1800 41.4000 "destination"
GEODIST dasher:D-7001:location "current" "destination" km
# Returns remaining distance

# =============================================
# STEP 6: Delivered
# =============================================

HSET order:ORD-3001:status status "delivered" delivered_at "1705365100"
XADD order:ORD-3001:events * event "DELIVERED" detail "Order delivered! Enjoy your meal 🍔"

# Remove from active orders
SREM customer:C-5001:active_orders "ORD-3001"

# Clean up dasher location
DEL dasher:D-7001:location

# =============================================
# Customer opens app — get live order status
# =============================================

# Get active orders
SMEMBERS customer:C-5001:active_orders

# For each order: get current status
HGETALL order:ORD-3001:status

# Get timeline of events
XRANGE order:ORD-3001:events - +

# Cleanup
DEL order:ORD-3001:status order:ORD-3001:events dasher:D-7001:location customer:C-5001:active_orders
```

---

## 13. Reddit — Comment Threading & Voting

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `post:{post_id}:comments` | Sorted Set | Top-level comment IDs (score = hot_score) | None |
| `comment:{comment_id}` | Hash | Comment data | None |
| `comment:{comment_id}:replies` | Sorted Set | Reply IDs (score = score) | None |
| `comment:{comment_id}:voters` | Set | Users who voted (for idempotency) | None |

### 🔧 Commands

```redis
# =============================================
# Post a comment on post P-001
# =============================================

HSET comment:C-001 \
  author "alice" \
  text "Redis sorted sets are perfect for leaderboards!" \
  post_id "P-001" \
  parent_id "" \
  score "1" \
  upvotes "1" \
  downvotes "0" \
  created_at "1705363200"

# Add to post's comment list (hot_score as score)
# hot_score = log(upvotes) + (created_at / 45000)
ZADD post:P-001:comments 1705363.2 "C-001"

# Author auto-upvotes their own comment
SADD comment:C-001:voters "alice:up"

# =============================================
# Reply to the comment
# =============================================

HSET comment:C-002 \
  author "bob" \
  text "Totally agree! I use them for ranking too." \
  post_id "P-001" \
  parent_id "C-001" \
  score "1" \
  upvotes "1" \
  downvotes "0" \
  created_at "1705363300"

# Add as reply to parent comment
ZADD comment:C-001:replies 1 "C-002"

# =============================================
# Upvote a comment
# =============================================

# Charlie upvotes C-001
# First check if already voted
SISMEMBER comment:C-001:voters "charlie:up"
# 0 → hasn't voted yet

SISMEMBER comment:C-001:voters "charlie:down"
# 0 → hasn't downvoted either

# Record vote
SADD comment:C-001:voters "charlie:up"

# Update score
HINCRBY comment:C-001 upvotes 1
HINCRBY comment:C-001 score 1

# Update hot_score in sorted set
# (In practice, recalculate hot_score: log(2) + epoch/45000)
ZADD post:P-001:comments 1705363.5 "C-001"

# =============================================
# Change vote (upvote → downvote)
# =============================================

# Diana changes from upvote to downvote on C-002
SREM comment:C-002:voters "diana:up"
SADD comment:C-002:voters "diana:down"
HINCRBY comment:C-002 upvotes -1
HINCRBY comment:C-002 downvotes 1
HINCRBY comment:C-002 score -2

# =============================================
# Read comments (sorted by hot score)
# =============================================

# Get top 20 comments (highest score first)
ZRANGE post:P-001:comments 0 19 REV WITHSCORES

# For each comment ID, get data
HGETALL comment:C-001

# Get replies to a comment
ZRANGE comment:C-001:replies 0 -1 REV

# Check if current user voted
SISMEMBER comment:C-001:voters "currentuser:up"
SISMEMBER comment:C-001:voters "currentuser:down"

# Cleanup
DEL comment:C-001 comment:C-002 post:P-001:comments comment:C-001:replies comment:C-001:voters comment:C-002:voters
```

---

## 14. Spotify — Recently Played & Music Queue

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `user:{user_id}:recently_played` | List | Recently played tracks (bounded) | None |
| `user:{user_id}:queue` | List | Up-next queue | 24h |
| `user:{user_id}:now_playing` | Hash | Currently playing track + state | 30min |
| `track:{track_id}:play_count` | String (counter) | Global play count | None |

### 🔧 Commands

```redis
# =============================================
# User plays a song
# =============================================

# Set now playing
HSET user:1001:now_playing \
  track_id "TRK-5001" \
  title "Bohemian Rhapsody" \
  artist "Queen" \
  album "A Night at the Opera" \
  duration_ms "354000" \
  started_at "1705363200" \
  position_ms "0" \
  is_playing "true"
EXPIRE user:1001:now_playing 1800

# Add to recently played (push to head, keep only 50)
LPUSH user:1001:recently_played "TRK-5001"
LTRIM user:1001:recently_played 0 49

# Increment global play count
INCR track:TRK-5001:play_count

# =============================================
# Manage play queue
# =============================================

# Auto-generated queue (from playlist/album)
RPUSH user:1001:queue "TRK-5002" "TRK-5003" "TRK-5004" "TRK-5005"
EXPIRE user:1001:queue 86400

# User manually adds song to front of queue
LPUSH user:1001:queue "TRK-6001"

# View queue
LRANGE user:1001:queue 0 -1
# Returns: TRK-6001, TRK-5002, TRK-5003, TRK-5004, TRK-5005

# Skip to next song
LPOP user:1001:queue
# Returns: "TRK-6001" → play this next

# =============================================
# Pause / Resume / Seek
# =============================================

# Pause
HSET user:1001:now_playing is_playing "false" position_ms "125000"

# Resume
HSET user:1001:now_playing is_playing "true"

# Seek to position
HSET user:1001:now_playing position_ms "200000"

# =============================================
# Recently played — deduplicate
# =============================================

# Problem: User plays same song 3 times → appears 3 times in recently played
# Solution: Remove old occurrence before adding new one
LREM user:1001:recently_played 0 "TRK-5001"
LPUSH user:1001:recently_played "TRK-5001"
LTRIM user:1001:recently_played 0 49

# =============================================
# View recently played
# =============================================

LRANGE user:1001:recently_played 0 19
# Returns last 20 unique tracks in play order

# Cleanup
DEL user:1001:now_playing user:1001:recently_played user:1001:queue track:TRK-5001:play_count
```

---

## 15. LinkedIn — "Who Viewed Your Profile"

### 📐 Key Design

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `profile_views:{user_id}` | Sorted Set | Viewer IDs (score = timestamp) | 90d |
| `profile_views:{user_id}:count:weekly` | String (counter) | Weekly view count | 7d |
| `profile_views:{user_id}:anonymous` | HyperLogLog | Approximate anonymous view count | 90d |

### 🔧 Commands

```redis
# =============================================
# Someone views Alice's profile
# =============================================

# Bob (user:2001) views Alice (user:1001)
ZADD profile_views:1001 1705363200 "2001"
INCR profile_views:1001:count:weekly
EXPIRE profile_views:1001:count:weekly 604800
EXPIRE profile_views:1001 7776000

# Charlie views Alice
ZADD profile_views:1001 1705363400 "3001"
INCR profile_views:1001:count:weekly

# Anonymous viewer
PFADD profile_views:1001:anonymous "session:abc123"

# Bob views again (3 hours later) — updates timestamp
ZADD profile_views:1001 1705374000 "2001"
# Sorted set deduplicates by member, updates score to latest visit

# =============================================
# Alice checks "Who viewed my profile"
# =============================================

# Weekly view count (shown on dashboard)
GET profile_views:1001:count:weekly
# Returns: "2" (Bob + Charlie, anonymous not counted here)

# Get viewers list (most recent first)
ZREVRANGE profile_views:1001 0 9 WITHSCORES
# Returns: [(2001, 1705374000), (3001, 1705363400)]
# Bob shows as most recent (his second view updated the timestamp)

# Free tier: show count only
# Premium: show actual viewer profiles
# Application filters based on user's subscription

# Approximate total including anonymous
PFCOUNT profile_views:1001:anonymous
# Returns: ~1

# Cleanup
DEL profile_views:1001 profile_views:1001:count:weekly profile_views:1001:anonymous
```

---

## 16–25: Additional Real-World Applications (Summary)

> The patterns below follow the same structure. Key designs and core commands provided.

### 16. Discord — Typing Indicator

```redis
# Ephemeral state — user is typing in channel
SET typing:CH-001:alice "1" EX 5
SET typing:CH-001:bob "1" EX 5

# Check who's typing (SCAN in production)
KEYS typing:CH-001:*
# Returns: typing:CH-001:alice, typing:CH-001:bob
# Keys auto-expire if user stops typing
```

### 17. Airbnb — Search Result Caching

```redis
# Cache search results by query hash
SET search:cache:abc123hash \
  "{\"results\":[{\"id\":1,\"title\":\"Beach House\"},{\"id\":2,\"title\":\"Mountain Cabin\"}]}" \
  EX 300

# Search: hash(filters) → check cache → DB if miss
GET search:cache:abc123hash
# Returns cached results → skip expensive DB query
```

### 18. GitHub — Feature Flags

```redis
# Feature flag: new_ui enabled for 10% of users
HSET feature:new_ui enabled "true" rollout_pct "10" allowed_users "" blocked_users ""

# Check if user should see new feature
# if user_id % 100 < rollout_pct → show new UI
HGET feature:new_ui rollout_pct
# Application: if hash(user_id) % 100 < 10 → enabled

# Force-enable for specific users (employees testing)
SADD feature:new_ui:allowlist "employee:alice" "employee:bob"
SISMEMBER feature:new_ui:allowlist "employee:alice"
# 1 → always enabled regardless of rollout_pct

DEL feature:new_ui feature:new_ui:allowlist
```

### 19. Tinder — Swipe Deduplication

```redis
# Prevent showing same person twice
# Bit: user_id offset in bitmap
SETBIT user:1001:seen 2001 1
SETBIT user:1001:seen 2002 1
SETBIT user:1001:seen 2003 1

# Before showing candidate 2004 to user 1001:
GETBIT user:1001:seen 2004
# 0 → haven't seen, show them!

GETBIT user:1001:seen 2001
# 1 → already swiped, skip!

# Track matches (mutual right-swipes)
SADD user:1001:liked "2001" "2003"
SADD user:2001:liked "1001" "3001"

# Check for mutual match
SISMEMBER user:2001:liked "1001"
# 1 → it's a match! 🎉

DEL user:1001:seen user:1001:liked user:2001:liked
```

### 20. TikTok — Precomputed Feed

```redis
# Precomputed "For You" feed per user (ML model generates this)
LPUSH feed:user:1001 "VID-9001" "VID-9002" "VID-9003" "VID-9004" "VID-9005"
LTRIM feed:user:1001 0 199

# User opens app → get next batch of videos
LRANGE feed:user:1001 0 4
# Returns 5 video IDs to preload

# As user watches, pop from front
LPOP feed:user:1001

# When feed runs low (< 20 items), trigger background job to refill
LLEN feed:user:1001
# If < 20 → async: ML model generates more recommendations → RPUSH

DEL feed:user:1001
```

### 21. Booking.com — Inventory Lock

```redis
# When user starts checkout for Room-3001 on Jan 20:
# Lock the room for 10 minutes to prevent double-booking
SET lock:room:3001:2024-01-20 "session:abc123" NX EX 600
# Returns OK → room locked for this user

# Another user tries to book same room same date:
SET lock:room:3001:2024-01-20 "session:xyz789" NX EX 600
# Returns nil → room is locked by another user!
# Show: "Someone else is booking this room. Try again in a few minutes."

# If first user completes booking → release lock
DEL lock:room:3001:2024-01-20

# If first user abandons → lock auto-expires in 10 minutes

DEL lock:room:3001:2024-01-20
```

### 22. Twitter — Cache Invalidation via Pub/Sub

```redis
# When user profile is updated in DB:
# PUBLISH cache:invalidate:user:1001 "profile_updated"

# All app servers subscribe:
# PSUBSCRIBE cache:invalidate:*

# On receiving message → delete local cache:
# DEL cache:user:1001:profile

# This ensures ALL servers serving cached data clear their stale copies
# within milliseconds of the DB update.
```

### 23. Uber Eats — Flash Sale / Limited Coupon

```redis
# 1000 coupons for first 1000 users
SET coupon:FLASH-50:remaining "1000"

# User tries to claim coupon (atomic!)
EVAL "\
local remaining = tonumber(redis.call('GET', KEYS[1]))\
if remaining > 0 then\
    redis.call('DECR', KEYS[1])\
    redis.call('SADD', KEYS[2], ARGV[1])\
    return 1\
else\
    return 0\
end" 2 "coupon:FLASH-50:remaining" "coupon:FLASH-50:claimed" "user:1001"
# Returns 1 = coupon claimed! 0 = sold out!

# Check remaining
GET coupon:FLASH-50:remaining

# Check who claimed
SCARD coupon:FLASH-50:claimed

DEL coupon:FLASH-50:remaining coupon:FLASH-50:claimed
```

### 24. WhatsApp — Message Delivery Status

```redis
# Message delivery tracking: sent → delivered → read

# Message sent to server
HSET msg:M-9001 status "sent" from "alice" to "bob" sent_at "1705363200"

# Server delivers to Bob's device
HSET msg:M-9001 status "delivered" delivered_at "1705363201"
# Show double gray tick ✓✓

# Bob reads the message
HSET msg:M-9001 status "read" read_at "1705363210"
# Show double blue tick ✓✓

# Alice queries status
HMGET msg:M-9001 status delivered_at read_at
# Returns: "read", "1705363201", "1705363210"

DEL msg:M-9001
```

### 25. Google Analytics — Unique Visitor Counting

```redis
# Daily unique visitors using HyperLogLog (12KB per day!)
PFADD uv:2024-01-15:www.example.com "ip:1.2.3.4" "ip:5.6.7.8" "ip:1.2.3.4"
PFCOUNT uv:2024-01-15:www.example.com
# Returns: ~2

# Weekly uniques (merge daily HLLs)
PFMERGE uv:week3:www.example.com \
  uv:2024-01-15:www.example.com \
  uv:2024-01-16:www.example.com \
  uv:2024-01-17:www.example.com
PFCOUNT uv:week3:www.example.com

# Page-specific tracking
PFADD uv:2024-01-15:www.example.com:/pricing "ip:1.2.3.4" "ip:9.10.11.12"
PFCOUNT uv:2024-01-15:www.example.com:/pricing

# Cost comparison:
# 100M daily visitors:
#   SET (exact):      100M × 40 bytes = 4GB per day
#   HyperLogLog:      12KB per day (0.81% error)
#   Bitmap (numeric):  12.5MB per day (exact, if IDs are numeric)

DEL uv:2024-01-15:www.example.com uv:2024-01-16:www.example.com uv:2024-01-17:www.example.com uv:week3:www.example.com uv:2024-01-15:www.example.com:/pricing
```

---

## 🏆 Cheat Sheet: Redis Data Structure Selection Guide

| Use Case | Data Structure | Why |
|---|---|---|
| Cache (simple K/V) | **String** | SET/GET with EX, O(1) |
| Object/Entity | **Hash** | Partial reads, HINCRBY |
| Queue / Recent items | **List** | LPUSH/RPOP, BLPOP for blocking |
| Unique collection / Tags | **Set** | SADD idempotent, SINTER for intersections |
| Leaderboard / Priority | **Sorted Set** | ZINCRBY + ZRANGE REV |
| Counter | **String (INCR)** | Atomic increment, single-threaded |
| Unique count (approx) | **HyperLogLog** | 12KB regardless of cardinality |
| Boolean flags per user | **Bitmap** | 1 bit per user, BITOP for aggregation |
| Nearby search | **Geo** | Built-in radius queries |
| Event log / Message queue | **Stream** | Consumer groups, persistence |
| Real-time broadcast | **Pub/Sub** | Fire-and-forget, low latency |
| Complex atomic operation | **Lua Script** | Conditional logic + atomicity |
| Distributed lock | **String (NX EX)** | SET NX = atomic acquire |

---

## 🎯 Interview Summary: "When Would You Use Redis?"

| Scenario | Redis Role | Without Redis |
|---|---|---|
| User session | Session store (Hash + TTL) | DB query per request (5ms → 0.1ms) |
| Feed / Timeline | Precomputed lists | Fan-out on read from DB (100ms → 1ms) |
| Like count | Atomic counter | UPDATE with row lock (deadlocks) |
| Trending | Sorted Set aggregation | Full table scan + GROUP BY |
| Rate limiting | Sliding window counter | Application-level (no distributed) |
| Presence | TTL-based keys | Polling database |
| Distributed lock | SET NX EX | Zookeeper (complex) |
| Leaderboard | Sorted Set | ORDER BY + LIMIT (slow at scale) |
| Unique visitors | HyperLogLog | COUNT DISTINCT (expensive) |
| Nearby drivers | Geo queries | PostGIS (heavier, slower) |
| Real-time notifications | Pub/Sub + Streams | WebSocket + database (complex) |
| Idempotency | NX flag | Unique constraint + exception handling |
