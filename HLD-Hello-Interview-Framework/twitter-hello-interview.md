# Twitter System Design - Hello Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [High-Level Design](#5️⃣-high-level-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **User Management**
   - User registration and authentication
   - Profile management (bio, profile picture, verification badge)
   - User search by username/display name
   - Follow/unfollow users
   - Block/mute users

2. **Tweet Operations**
   - Post tweets (280 character limit)
   - Delete tweets
   - Like/unlike tweets
   - Retweet/undo retweet
   - Quote tweet (retweet with comment)
   - Reply to tweets (threaded conversations)

3. **Timeline Features**
   - Home timeline (tweets from followed users)
   - User timeline (specific user's tweets)
   - Mentions timeline (tweets mentioning user)
   - Chronological and algorithmic sorting

4. **Discovery**
   - Trending topics/hashtags
   - Search tweets by keyword, hashtag
   - Search users
   - Suggested users to follow

#### Nice to Have (P1)
- Direct messaging between users
- Tweet bookmarking
- Lists (curated groups of users)
- Spaces (audio conversations)
- Twitter Blue (premium features)
- Tweet editing (with history)
- Polls
- Threads (connected tweet series)

### Non-Functional Requirements

#### Scalability
- **Daily Active Users (DAU)**: 400 million
- **Tweets per day**: 500 million (~6K writes/second)
- **Timeline requests**: 500 billion per day (~6M reads/second)
- **Read:Write ratio**: 1000:1 (read-heavy)
- **Traffic spikes**: Handle 10x normal during major events

#### Performance
- **Timeline load time**: < 200ms (p99)
- **Tweet post time**: < 100ms
- **Search latency**: < 300ms
- **Timeline updates**: Near real-time (< 5 seconds)
- **API response time**: < 100ms (p95)

#### Availability
- **Uptime**: 99.99% (4 nines)
- **Multi-region deployment** for low latency
- **Graceful degradation** during partial failures

#### Consistency
- **Eventual consistency** for timelines (acceptable delay)
- **Strong consistency** for tweets and user data
- **Causal consistency** for reply chains

#### Security
- OAuth 2.0 authentication
- Rate limiting (prevent spam/abuse)
- Content moderation (AI + manual review)
- DDoS protection
- HTTPS for all communications

### Capacity Estimation

#### Traffic Estimates
```
Daily Active Users (DAU): 400M
Tweets per day: 500M
Average timeline refreshes per user: 10/day

Read requests/day: 400M × 10 × 50 tweets = 200 billion
Read QPS: 200B / 86,400 ≈ 2.3M QPS
Peak read QPS (5x): ~11.5M QPS

Write requests/day: 500M tweets
Write QPS: 500M / 86,400 ≈ 6K QPS
Peak write QPS (10x during events): ~60K QPS
```

#### Storage Estimates
```
Average tweet text: 200 bytes
With metadata (user_id, timestamp, etc.): 500 bytes
Photos/videos (20% of tweets): Additional storage

Daily tweet storage:
  Text: 500M × 500 bytes = 250 GB
  Media (20%): 100M × 500 KB avg = 50 TB
  Total: ~50 TB/day

Yearly storage: 50 TB × 365 = 18.25 PB/year
5-year storage: ~91 PB

With compression & lifecycle policies:
  Effective storage: ~55 PB (40% savings)
```

#### Bandwidth Estimates
```
Incoming (tweets):
  50 TB/day ÷ 86,400 seconds = 578 MB/s

Outgoing (timeline reads, 1000:1 ratio):
  578 MB/s × 1000 = 578 GB/s
```

#### Memory Estimates (Caching)
```
Cache hot timelines (20% of active users):
  400M × 20% = 80M users
  50 tweets × 500 bytes = 25 KB per timeline
  Total: 80M × 25 KB = 2 TB

Cache hot tweets (recent 2 hours):
  6K QPS × 7,200 seconds = 43M tweets
  43M × 500 bytes = 21.5 GB

Cache user profiles (active users):
  80M users × 2 KB = 160 GB

Total cache: ~2.2 TB distributed across 50 Redis servers
```

#### Database Estimates
```
Users: 500M × 2 KB = 1 TB
Tweets: 20B × 500 bytes = 10 TB
Likes: 50B × 100 bytes = 5 TB
Retweets: 10B × 100 bytes = 1 TB
Follows: 50B × 50 bytes = 2.5 TB
Total: ~19.5 TB (excluding media)
```

---

## 2️⃣ Core Entities

### Entity 1: User
**Purpose**: Represents a registered Twitter user

**Schema**:
```sql
Users Table (MySQL):
- user_id (PK, UUID)
- username (unique, indexed, varchar 20)
- email (unique, indexed)
- display_name (varchar 50)
- bio (varchar 160)
- profile_picture_url
- is_verified (boolean)
- is_protected (boolean, private account)
- follower_count (counter)
- following_count (counter)
- tweet_count (counter)
- created_at (timestamp)
- updated_at (timestamp)

Indexes:
- PRIMARY KEY (user_id)
- UNIQUE INDEX (username)
- UNIQUE INDEX (email)
- INDEX (display_name) -- for search
```

**Relationships**:
- Has many: Tweets, Likes, Retweets
- Many-to-many: Followers/Following (self-referential)

**Sharding**: By user_id hash (10 shards)

### Entity 2: Tweet
**Purpose**: Represents a 280-character tweet

**Schema**:
```cql
Tweets Table (Cassandra):
- tweet_id (PK, UUID/Snowflake)
- user_id (indexed)
- content (text, max 280 chars)
- created_at (timestamp, indexed)
- type (enum: ORIGINAL, RETWEET, QUOTE_TWEET, REPLY)
- reply_to_tweet_id (nullable)
- retweet_of_tweet_id (nullable)
- quoted_tweet_id (nullable)
- hashtags (set<text>)
- mentioned_users (set<text>)
- media_urls (list<text>)
- like_count (counter)
- retweet_count (counter)
- reply_count (counter)
- view_count (counter)

Indexes:
- PRIMARY KEY (tweet_id)
- INDEX (user_id, created_at DESC)
- INDEX (created_at DESC)
```

**Relationships**:
- Belongs to: User
- Has many: Likes, Retweets, Replies
- Optional parent: Tweet (for replies)

**ID Generation**: Snowflake algorithm (64-bit, sortable by time)

### Entity 3: Follow
**Purpose**: Social graph (who follows whom)

**Schema**:
```cql
Followers Table (Cassandra):
- user_id (partition key)
- follower_id (clustering key)
- created_at (timestamp)
PRIMARY KEY (user_id, follower_id)

Following Table (Cassandra):
- user_id (partition key)
- followee_id (clustering key)
- created_at (timestamp)
PRIMARY KEY (user_id, followee_id)
```

**Design Note**: Denormalized for fast bidirectional queries

**Relationships**:
- Links two Users
- Self-referential many-to-many

### Entity 4: Like
**Purpose**: Tracks user likes on tweets

**Schema**:
```cql
Likes Table (Cassandra):
- tweet_id (partition key)
- user_id (clustering key)
- created_at (timestamp)
PRIMARY KEY (tweet_id, user_id)

User_Likes Table (Cassandra):
- user_id (partition key)
- tweet_id (clustering key)
- created_at (timestamp)
PRIMARY KEY (user_id, tweet_id)
```

**Design Note**: Dual tables for efficient querying from both perspectives

### Entity 5: Retweet
**Purpose**: Tracks retweets

**Schema**:
```cql
Retweets Table (Cassandra):
- original_tweet_id (partition key)
- user_id (clustering key)
- retweet_id (UUID)
- created_at (timestamp)
PRIMARY KEY (original_tweet_id, user_id)
```

**Relationships**:
- Links User and Tweet
- Creates new tweet entry with type=RETWEET

### Entity 6: Timeline (Cached)
**Purpose**: Pre-computed user timelines for fast access

**Redis Structure**:
```
Key: timeline:user:{user_id}
Type: Sorted Set (ZSET)
Members: tweet_id
Score: timestamp (Unix milliseconds)

Commands:
ZADD timeline:user:123 1704672000000 tweet_abc
ZREVRANGE timeline:user:123 0 49  -- Get 50 recent tweets
ZCARD timeline:user:123  -- Count tweets in timeline
```

**TTL**: 2 days (172,800 seconds)

**Size**: ~50 tweets per timeline (configurable)

### Entity 7: Hashtag
**Purpose**: Track hashtags for trending topics

**Schema**:
```cql
Hashtags Table (Cassandra):
- hashtag (PK, text)
- tweet_count (counter)
- last_updated (timestamp)

Hashtag_Timeline Table:
- hashtag (partition key)
- tweet_id (clustering key, timeuuid)
- created_at (timestamp)
PRIMARY KEY (hashtag, tweet_id)
WITH CLUSTERING ORDER BY (tweet_id DESC)
```

**Use Case**: Trending topics, hashtag search

### Entity 8: Direct Message
**Purpose**: Private messages between users

**Schema**:
```cql
Conversations Table (MySQL):
- conversation_id (PK, UUID)
- participant_1_id
- participant_2_id
- last_message_id
- updated_at (timestamp)
INDEX (participant_1_id, updated_at)
INDEX (participant_2_id, updated_at)

Messages Table (Cassandra):
- conversation_id (partition key)
- message_id (clustering key, timeuuid)
- sender_id
- receiver_id
- content (text)
- is_read (boolean)
- created_at (timestamp)
PRIMARY KEY (conversation_id, message_id)
WITH CLUSTERING ORDER BY (message_id DESC)
```

**Relationships**:
- Conversation links two Users
- Message belongs to Conversation

---

## 3️⃣ API Design

### Authentication Header
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### User APIs

#### 1. Register User
```
POST /api/v1/auth/register

Request:
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "hashed_password",
  "display_name": "Alice Smith"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "user_id": "usr_123abc",
    "username": "alice",
    "access_token": "jwt_token_here",
    "refresh_token": "refresh_token_here"
  }
}
```

#### 2. Get User Profile
```
GET /api/v1/users/{username}

Response (200 OK):
{
  "success": true,
  "data": {
    "user_id": "usr_123abc",
    "username": "alice",
    "display_name": "Alice Smith",
    "bio": "Software Engineer | Tech Enthusiast",
    "profile_picture_url": "https://cdn.twitter.com/...",
    "is_verified": false,
    "is_protected": false,
    "follower_count": 1523,
    "following_count": 456,
    "tweet_count": 892,
    "is_following": true,
    "is_followed_by": false
  }
}
```

#### 3. Follow User
```
POST /api/v1/users/{username}/follow

Response (200 OK):
{
  "success": true,
  "data": {
    "username": "bob",
    "is_following": true
  }
}
```

#### 4. Get Followers
```
GET /api/v1/users/{username}/followers?cursor={cursor}&limit=20

Response (200 OK):
{
  "success": true,
  "data": {
    "users": [
      {
        "user_id": "usr_456",
        "username": "charlie",
        "display_name": "Charlie Brown",
        "profile_picture_url": "url",
        "is_verified": false,
        "is_following": true
      }
    ],
    "pagination": {
      "next_cursor": "xyz789",
      "has_more": true
    }
  }
}
```

### Tweet APIs

#### 5. Post Tweet
```
POST /api/v1/tweets

Request:
{
  "content": "Excited to learn system design! #Engineering #Tech",
  "reply_to_tweet_id": null,
  "media_urls": ["https://s3.amazonaws.com/media/abc.jpg"]
}

Response (201 Created):
{
  "success": true,
  "data": {
    "tweet_id": "tweet_123abc",
    "user": {
      "username": "alice",
      "display_name": "Alice Smith",
      "profile_picture_url": "url"
    },
    "content": "Excited to learn system design! #Engineering #Tech",
    "created_at": "2025-01-08T14:30:00Z",
    "metrics": {
      "like_count": 0,
      "retweet_count": 0,
      "reply_count": 0,
      "view_count": 0
    },
    "entities": {
      "hashtags": ["Engineering", "Tech"],
      "mentions": [],
      "urls": [],
      "media": [{"url": "...", "type": "photo"}]
    }
  }
}
```

#### 6. Get Tweet
```
GET /api/v1/tweets/{tweet_id}

Response (200 OK):
{
  "success": true,
  "data": {
    "tweet_id": "tweet_123abc",
    "user": {...},
    "content": "...",
    "type": "ORIGINAL",
    "created_at": "2025-01-08T14:30:00Z",
    "metrics": {
      "like_count": 152,
      "retweet_count": 34,
      "reply_count": 12,
      "view_count": 5023
    },
    "is_liked": false,
    "is_retweeted": false
  }
}
```

#### 7. Delete Tweet
```
DELETE /api/v1/tweets/{tweet_id}

Response (204 No Content)
```

#### 8. Like Tweet
```
POST /api/v1/tweets/{tweet_id}/like

Response (200 OK):
{
  "success": true,
  "data": {
    "tweet_id": "tweet_123abc",
    "like_count": 153
  }
}
```

#### 9. Unlike Tweet
```
DELETE /api/v1/tweets/{tweet_id}/like

Response (200 OK):
{
  "success": true,
  "data": {
    "tweet_id": "tweet_123abc",
    "like_count": 152
  }
}
```

#### 10. Retweet
```
POST /api/v1/tweets/{tweet_id}/retweet

Response (201 Created):
{
  "success": true,
  "data": {
    "retweet_id": "tweet_456def",
    "original_tweet_id": "tweet_123abc",
    "retweet_count": 35
  }
}
```

#### 11. Quote Tweet
```
POST /api/v1/tweets/{tweet_id}/quote

Request:
{
  "content": "Great insights on system design!"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "tweet_id": "tweet_789ghi",
    "quoted_tweet_id": "tweet_123abc",
    "content": "Great insights on system design!"
  }
}
```

#### 12. Reply to Tweet
```
POST /api/v1/tweets/{tweet_id}/reply

Request:
{
  "content": "Thanks for sharing this!"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "tweet_id": "tweet_xyz",
    "reply_to_tweet_id": "tweet_123abc",
    "content": "Thanks for sharing this!"
  }
}
```

### Timeline APIs

#### 13. Get Home Timeline
```
GET /api/v1/timelines/home?cursor={cursor}&limit=50

Response (200 OK):
{
  "success": true,
  "data": {
    "tweets": [
      {
        "tweet_id": "tweet_123",
        "user": {...},
        "content": "...",
        "created_at": "2025-01-08T14:30:00Z",
        "metrics": {...}
      }
    ],
    "meta": {
      "cursor": "next_token_xyz",
      "result_count": 50
    }
  }
}
```

#### 14. Get User Timeline
```
GET /api/v1/timelines/user/{username}?cursor={cursor}&limit=50

Response: Same structure as Home Timeline
```

#### 15. Get Mentions Timeline
```
GET /api/v1/timelines/mentions?cursor={cursor}&limit=50

Response: Same structure as Home Timeline
```

### Search APIs

#### 16. Search Tweets
```
GET /api/v1/search/tweets?q={query}&cursor={cursor}&limit=20

Query examples:
- "system design"
- "from:alice"
- "#engineering"
- "since:2025-01-01"

Response (200 OK):
{
  "success": true,
  "data": {
    "tweets": [...],
    "meta": {
      "cursor": "xyz",
      "result_count": 20
    }
  }
}
```

#### 17. Search Users
```
GET /api/v1/search/users?q={query}&limit=20

Response (200 OK):
{
  "success": true,
  "data": {
    "users": [
      {
        "user_id": "usr_789",
        "username": "alice_tech",
        "display_name": "Alice Tech",
        "bio": "...",
        "is_verified": true,
        "follower_count": 125000
      }
    ]
  }
}
```

### Trending APIs

#### 18. Get Trending Topics
```
GET /api/v1/trends/global

Response (200 OK):
{
  "success": true,
  "data": {
    "trends": [
      {
        "name": "#Engineering",
        "tweet_volume": 125300,
        "url": "/search?q=%23Engineering"
      },
      {
        "name": "System Design",
        "tweet_volume": 89200,
        "url": "/search?q=System%20Design"
      }
    ],
    "as_of": "2025-01-08T15:00:00Z"
  }
}
```

### Rate Limiting Response
```
HTTP 429 Too Many Requests

X-Rate-Limit-Limit: 15
X-Rate-Limit-Remaining: 0
X-Rate-Limit-Reset: 1704729600

{
  "errors": [{
    "code": 88,
    "message": "Rate limit exceeded"
  }]
}
```

---

## 4️⃣ Data Flow

### Flow 1: User Registration & Authentication
```
1. User submits registration form
   ↓
2. API Gateway validates input (username format, email, etc.)
   ↓
3. User Service checks if username/email already exists
   ↓
4. Hash password (bcrypt, 12 rounds)
   ↓
5. Insert user record into MySQL (sharded by user_id)
   ↓
6. Generate Snowflake ID for user_id
   ↓
7. Generate JWT access token (1 hour TTL)
   ↓
8. Generate refresh token (7 days TTL, stored in Redis)
   ↓
9. Return tokens to client
   ↓
10. Client stores tokens securely
```

### Flow 2: Post Tweet
```
1. User composes tweet in mobile app
   ↓
2. App sends: POST /api/v1/tweets
   ↓
3. API Gateway:
   - Validates JWT token
   - Checks rate limit (100 tweets/hour per user)
   ↓
4. Tweet Service:
   - Validates content (≤ 280 chars)
   - Extracts hashtags (#...) and mentions (@...)
   - Generates Snowflake tweet_id
   - Writes to Cassandra (async replication)
   ↓
5. Publish event to Kafka: "tweet.created"
   ↓
6. Return success to user (< 100ms)

Async Processing (via Kafka consumers):

7. Fanout Worker:
   - Get follower list from Graph DB
   - Check follower count:
     * If < 1M: Push to all followers' Redis timelines
     * If > 1M: Skip fanout (celebrity, pull on read)
   - Batch write to Redis (pipeline)
   ↓
8. Notification Worker:
   - Send notifications to mentioned users
   - Push notifications to active followers (WebSocket/FCM)
   ↓
9. Search Indexer Worker:
   - Index tweet in Elasticsearch
   - Update user's tweet count
   ↓
10. Trending Worker:
    - Increment hashtag counters
    - Update trending scores (Storm/Flink)
    ↓
11. Analytics Worker:
    - Log event to data warehouse (Hadoop/Redshift)
```

### Flow 3: View Home Timeline
```
1. User opens Twitter app or pulls to refresh
   ↓
2. App sends: GET /api/v1/timelines/home?limit=50
   ↓
3. API Gateway validates JWT & checks rate limit
   ↓
4. Timeline Service:
   - Check Redis cache for user's timeline
   ↓
5a. Cache HIT (common case):
    - ZREVRANGE timeline:user:{id} 0 49
    - Get 50 tweet IDs with timestamps
    - Jump to step 6
    ↓
5b. Cache MISS (cold start or expired):
    - Get user's following list (Graph DB)
    - Classify users: celebrities (>1M followers) vs normal
    - Fetch pre-computed tweets (normal users from Redis)
    - Fetch celebrity tweets on-demand (Cassandra query)
    - Merge and sort by timestamp
    - Cache result in Redis (2 day TTL)
    ↓
6. Hydrate tweet details (batch queries):
   - Tweet content, metrics from Cassandra/Cache
   - User info (username, avatar, etc.) from Cache/MySQL
   - Media URLs from CDN
   - Check if user liked/retweeted (Redis)
   ↓
7. Optional: Apply ML ranking algorithm
   - Calculate engagement score per tweet
   - Re-rank based on relevance to user
   ↓
8. Return paginated response with cursor
   ↓
9. Client renders timeline:
   - Display tweets with lazy image loading
   - Prefetch next page when user scrolls to 80%
```

### Flow 4: Like Tweet
```
1. User taps heart icon
   ↓
2. Optimistic UI update (instant visual feedback)
   ↓
3. App sends: POST /api/v1/tweets/{id}/like
   ↓
4. API Gateway validates & routes
   ↓
5. Tweet Service:
   - Check if already liked (Redis cache check)
   - If not liked:
     a. Insert into Likes table (Cassandra)
     b. Insert into User_Likes table (Cassandra)
     c. Increment like_count in Redis:
        HINCRBY tweet:{id}:metrics like_count 1
     d. Add to liked set:
        SADD user:{user_id}:liked_tweets {tweet_id}
   ↓
6. Publish event to Kafka: "tweet.liked"
   ↓
7. Return success (< 50ms)

Async Processing:

8. Notification Worker:
   - Get tweet author from DB
   - Send push notification: "{user} liked your tweet"
   - Batch if many likes (aggregate notifications)
   ↓
9. Analytics Worker:
   - Update engagement metrics
   - Log to data warehouse
   ↓
10. Trending Worker:
    - Boost tweet's viral score
    - May surface in trending/explore feed
```

### Flow 5: Search Tweets
```
1. User types in search box
   ↓
2. Autocomplete: GET /api/v1/search/autocomplete?q={prefix}
   - Query Elasticsearch with prefix match
   - Return suggestions (hashtags, users) in < 100ms
   ↓
3. User submits full search query
   ↓
4. Search Service:
   - Parse query (keywords, operators, filters)
   - Build Elasticsearch query:
     {
       "query": {
         "bool": {
           "must": [{"match": {"content": "system design"}}],
           "filter": [
             {"range": {"created_at": {"gte": "2025-01-01"}}},
             {"term": {"user_id": "usr_123"}}
           ]
         }
       },
       "sort": [{"created_at": "desc"}, {"engagement_score": "desc"}]
     }
   ↓
5. Execute search against Elasticsearch cluster
   ↓
6. Rank results:
   - Recency (70% weight)
   - Engagement (20% weight)
   - User relevance (10% weight)
   ↓
7. Fetch full tweet details from Cache/Cassandra
   ↓
8. Return top 20 results with cursor
   ↓
9. User scrolls: Load more with next cursor
```

### Flow 6: Real-time Timeline Update
```
1. User A posts tweet
   ↓
2. Fanout Worker pushes to User B's timeline (Redis)
   ↓
3. User B has app open with active WebSocket connection
   ↓
4. WebSocket Server detects new tweet in User B's timeline:
   - Poll Redis timeline every 5 seconds (or use Redis pub/sub)
   - Detect new tweets with timestamp > last_seen
   ↓
5. Push notification via WebSocket:
   {
     "type": "new_tweet",
     "tweet_id": "tweet_xyz",
     "user": "alice"
   }
   ↓
6. Client receives WebSocket message
   ↓
7. Client fetches tweet details via API
   ↓
8. Display notification banner: "1 new tweet"
   ↓
9. User taps banner → Refresh timeline
```

### Flow 7: Trending Topics Calculation
```
Real-time Stream Processing:

1. Tweet posted → Kafka topic: "tweets"
   ↓
2. Storm/Flink streaming job consumes events
   ↓
3. Extract hashtags from tweet
   ↓
4. Maintain sliding windows (1 hour, 6 hours, 24 hours)
   ↓
5. Count tweets per hashtag per window
   ↓
6. Calculate trend score:
   Score = (Current Volume / Historical Avg) × 
           (Velocity) × 
           (Diversity Factor)
   ↓
7. Update trending cache (Redis Sorted Set):
   ZADD trending:global {score} {hashtag}
   ↓
8. Keep top 50 trends
   ↓
9. Update every 5 minutes
   ↓
10. API endpoint reads from cache (< 10ms)
```

### Flow 8: Direct Messaging
```
1. User A sends DM to User B
   ↓
2. Message Service:
   - Create/retrieve conversation between users
   - Generate message_id (timeuuid for ordering)
   - Write to Cassandra Messages table
   - Update conversation's last_message_id
   ↓
3. WebSocket Server:
   - Check if User B is online
   - If online: Push message via WebSocket
   - If offline: Queue for push notification
   ↓
4. User B receives message (< 100ms if online)
   ↓
5. Read receipt:
   - User B opens conversation
   - Update is_read = true in Messages table
   - Send read receipt to User A via WebSocket
   ↓
6. Push Notification (if User B offline):
   - Notification Service sends FCM/APNS
   - "New message from @alice: Hello!"
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
                            [Mobile/Web Clients]
                                     |
                                     ↓
                     [CDN - CloudFront/Fastly]
                     - Images, Videos, Avatars
                     - 95% cache hit ratio
                                     |
                                     ↓
                           [DNS - Route53]
                           Geographic routing
                                     |
                                     ↓
                [Application Load Balancer (ALB)]
                     Multi-AZ, SSL termination
                                     |
             ┌───────────────────────┼───────────────────────┐
             ↓                       ↓                       ↓
    [API Gateway]           [WebSocket Server]      [GraphQL API]
    - Auth & Rate Limit     - Real-time updates    - Flexible queries
             |                       |                       |
             └───────────────────────┴───────────────────────┘
                                     |
                    ┌────────────────┴────────────────┐
                    ↓                                  ↓
        [Application Services]              [Background Workers]
        Microservices (Stateless):          Kafka Consumers:
        • Tweet Service                     • Fanout Worker
        • Timeline Service                  • Notification Worker
        • User Service                      • Search Indexer
        • Search Service                    • Trending Worker
        • DM Service                        • Analytics Worker
                    |                                  |
        ┌───────────┴──────────┐                     |
        ↓                      ↓                     ↓
   [Cache Layer]          [Message Queue]
   Redis Cluster          Apache Kafka
   • Timelines            • High throughput
   • User profiles        • Durable logs
   • Tweet metrics        • Event streaming
   • Rate limits          • 6K writes/sec
   (2.2TB total)          • 3 replicas
                    |
    ┌───────────────┴────────────┬────────────┬────────────┐
    ↓                            ↓            ↓            ↓
[User DB]                   [Tweet DB]   [Graph DB]  [Search]
MySQL                       Cassandra    Cassandra   Elasticsearch
Master                      Cluster      Followers   Multi-node
+ 3 replicas                • Tweets     Following   • Tweet search
Sharded                     • Likes      Denormal    • User search
by user_id                  • Retweets   ized        • Hashtag search
                            Write-heavy
                    |
         ┌──────────┴──────────┐
         ↓                     ↓
[Object Storage - S3]   [Data Warehouse]
Media files              Hadoop/Spark
CDN integration          Analytics
Lifecycle policies       ML Training
```

### Component Details

#### 1. Load Balancer (ALB)
**Purpose**: Distribute traffic across multiple application servers

**Configuration**:
- Layer 7 (HTTP/HTTPS) routing
- SSL/TLS termination
- Health checks every 10 seconds
- Geographic routing (Route53 integration)
- Auto-scaling integration
- Sticky sessions for WebSocket

**Capacity**: 50K concurrent connections per LB

#### 2. API Gateway
**Purpose**: Centralized entry point for authentication and rate limiting

**Responsibilities**:
- JWT token validation (OAuth 2.0)
- Rate limiting per user/IP (Token Bucket algorithm)
- Request validation and sanitization
- API versioning (v1, v2)
- Metrics collection
- Response caching

**Rate Limits**:
```
Per User:
- Tweets: 100/hour
- Follows: 400/day
- Likes: 1000/day
- API calls: 1500/15-min

Per IP (unauthenticated):
- Registration: 10/hour
- Login: 20/hour
- API calls: 15/15-min
```

**Technology**: Kong, AWS API Gateway, NGINX

#### 3. Microservices

**Tweet Service**:
- Create/delete tweets
- Extract hashtags and mentions
- Write to Cassandra
- Publish events to Kafka

**Timeline Service**:
- Generate personalized timelines
- Hybrid push-pull implementation
- Cache management (Redis)
- ML-based ranking

**User Service**:
- User registration/authentication
- Profile management
- Follow/unfollow operations
- User search

**Search Service**:
- Full-text tweet search
- User search
- Hashtag search
- Elasticsearch integration

**DM Service**:
- Direct messaging
- Conversation management
- WebSocket connections
- Read receipts

#### 4. Cache Layer (Redis Cluster)

**What We Cache**:
```
Timelines (2 day TTL):
  Key: timeline:user:{user_id}
  Type: Sorted Set
  Size: 50 tweets per user

Tweet Metrics (5 min TTL):
  Key: tweet:{tweet_id}:metrics
  Type: Hash
  Fields: like_count, retweet_count, reply_count

User Profiles (1 hour TTL):
  Key: user:{user_id}
  Type: Hash
  Fields: username, display_name, avatar, verified

Rate Limits (dynamic TTL):
  Key: ratelimit:{user_id}:{action}
  Type: String (counter)

User Sessions (1 hour TTL):
  Key: session:{user_id}
  Type: Hash
```

**Cluster Config**:
- 50 nodes × 50GB = 2.5TB total
- 5 master shards
- 2 replicas per shard
- Redis Sentinel for failover
- Consistent hashing for distribution

#### 5. Databases

**A. User Database (MySQL)**
- Purpose: User accounts, profiles, settings
- Sharding: By user_id hash (10 shards)
- Replication: 1 master + 3 read replicas per shard
- Queries: User auth, profile lookups, follow operations

**B. Tweet Database (Cassandra)**
- Purpose: Tweets, replies, quote tweets
- Partitioning: By tweet_id
- Replication Factor: 3
- Write throughput: 6K writes/sec (peak 60K)
- Consistency: Quorum reads/writes

**C. Graph Database (Cassandra)**
- Purpose: Social graph (followers/following)
- Denormalized: Separate tables for bidirectional queries
- Fast fanout for timeline generation
- Supports millions of follows per user

**D. Search Engine (Elasticsearch)**
- Purpose: Full-text search
- Cluster: 6 nodes (3 master, 3 data)
- Sharding: 15 shards, 2 replicas
- Near real-time indexing (< 1 second)

#### 6. Message Queue (Apache Kafka)

**Topics**:
```
tweets (high volume):
- All tweet creation events
- 6K msgs/sec average, 60K peak

tweet_engagements:
- Likes, retweets, replies
- 100K msgs/sec

user_actions:
- Follows, unfollows, blocks
- 10K msgs/sec

notifications:
- Push notification events
- 50K msgs/sec
```

**Configuration**:
- 10 brokers
- 3 replicas per partition
- 7 day retention
- Partitioning by user_id for ordering

**Consumer Groups**:
- Fanout workers (10 consumers)
- Notification workers (5 consumers)
- Search indexers (3 consumers)
- Trending workers (2 consumers)
- Analytics workers (2 consumers)

#### 7. Object Storage (S3)

**Bucket Structure**:
```
twitter-media/
  ├── photos/
  │   ├── original/{tweet_id}.jpg
  │   ├── large/{tweet_id}_1200.jpg
  │   └── thumb/{tweet_id}_400.jpg
  └── videos/
      ├── original/{tweet_id}.mp4
      └── transcoded/{tweet_id}_720p.mp4
```

**Lifecycle Policies**:
- Standard: 0-90 days
- Infrequent Access: 90-365 days
- Glacier: 365+ days

**CDN Integration**:
- CloudFront distribution
- 95% cache hit ratio
- Edge locations worldwide

#### 8. WebSocket Server

**Purpose**: Real-time updates for active users

**Use Cases**:
- New tweets in timeline
- New notifications
- Direct messages
- Typing indicators

**Architecture**:
- Stateful connections
- Connection pooling per user
- Redis pub/sub for coordination
- Auto-reconnect on failure

**Scale**: 10M concurrent WebSocket connections

---

## 6️⃣ Deep Dives

### Deep Dive 1: Timeline Generation (Hybrid Fan-out)

**The Celebrity Problem**:
```
Scenario: Celebrity with 10M followers tweets

Pure Push (Fan-out on Write):
- Write to 10M Redis timelines
- Time: ~100 seconds
- Database overload
- Followers see tweet after long delay

Pure Pull (Fan-out on Read):
- Each follower queries celebrity's tweets
- 10M queries per minute
- Database overload on reads
- High latency

Hybrid Solution:
- Normal users (<1M): Fan-out on write
- Celebrities (>1M): Fan-out on read
- Best of both worlds
```

**Implementation**:

```python
class TimelineService:
    CELEBRITY_THRESHOLD = 1_000_000
    
    def post_tweet(self, tweet_id, user_id):
        # Write tweet to Cassandra
        cassandra.insert("tweets", tweet_id, ...)
        
        # Publish to Kafka for async processing
        kafka.publish("tweet.created", {
            "tweet_id": tweet_id,
            "user_id": user_id
        })
    
    def fanout_tweet(self, tweet_id, user_id):
        """Async worker processes this"""
        
        # Get follower count
        follower_count = get_follower_count(user_id)
        
        if follower_count > self.CELEBRITY_THRESHOLD:
            # Skip fanout for celebrities
            logger.info(f"Skipping fanout for celebrity {user_id}")
            return
        
        # Get all followers
        followers = cassandra.query(
            "SELECT follower_id FROM followers WHERE user_id = ?",
            user_id
        )
        
        # Batch write to Redis (pipeline for performance)
        pipeline = redis.pipeline()
        timestamp = int(time.time() * 1000)
        
        for follower in followers:
            pipeline.zadd(
                f"timeline:user:{follower.follower_id}",
                {tweet_id: timestamp}
            )
            # Keep only recent 500 tweets
            pipeline.zremrangebyrank(
                f"timeline:user:{follower.follower_id}",
                0, -501
            )
        
        pipeline.execute()
    
    def get_timeline(self, user_id, limit=50):
        """Generate user's timeline"""
        
        # Check cache
        cached_tweets = redis.zrevrange(
            f"timeline:user:{user_id}",
            0,
            limit - 1
        )
        
        # Get celebrity following list
        following = get_following(user_id)
        celebrities = [u for u in following 
                      if u.follower_count > self.CELEBRITY_THRESHOLD]
        
        if not celebrities:
            # No celebrities followed, return cached timeline
            return self.hydrate_tweets(cached_tweets)
        
        # Fetch celebrity tweets on-demand
        celebrity_tweets = cassandra.query("""
            SELECT * FROM tweets
            WHERE user_id IN ?
            AND created_at > ?
            ORDER BY created_at DESC
            LIMIT 50
        """, [c.user_id for c in celebrities],
            datetime.now() - timedelta(days=1))
        
        # Merge cached and celebrity tweets
        all_tweets = self.merge_tweets(cached_tweets, celebrity_tweets)
        
        # Apply ML ranking if enabled
        if use_algorithmic_timeline():
            all_tweets = self.rank_tweets(user_id, all_tweets)
        
        return all_tweets[:limit]
    
    def rank_tweets(self, user_id, tweets):
        """ML-based ranking"""
        features = []
        
        for tweet in tweets:
            # Calculate engagement score
            engagement = (
                tweet.like_count * 1.0 +
                tweet.retweet_count * 2.0 +
                tweet.reply_count * 3.0
            )
            
            # Recency decay
            hours_old = (now() - tweet.created_at).total_seconds() / 3600
            recency = 1.0 / (1 + hours_old)
            
            # User affinity
            affinity = get_user_affinity(user_id, tweet.user_id)
            
            score = engagement * recency * affinity
            features.append((tweet, score))
        
        # Sort by score
        ranked = sorted(features, key=lambda x: x[1], reverse=True)
        return [tweet for tweet, score in ranked]
```

### Deep Dive 2: Snowflake ID Generation

**Requirements**:
- Unique across all shards
- Sortable by time (k-sortable)
- 64-bit integer
- Generate 10K+ IDs/second per machine

**Snowflake Structure** (64 bits):
```
| 1 bit  | 41 bits      | 10 bits  | 12 bits   |
|--------|--------------|----------|-----------|
| unused | timestamp ms | machine  | sequence  |
|   0    | since epoch  | ID       | per ms    |
```

**Breakdown**:
- **1 bit**: Unused (always 0)
- **41 bits**: Milliseconds since custom epoch (69 years)
- **10 bits**: Machine ID (1024 machines)
- **12 bits**: Sequence (4096 IDs per millisecond)

**Implementation**:

```python
class SnowflakeIDGenerator:
    EPOCH = 1288834974657  # Twitter's custom epoch (Nov 04, 2010)
    MACHINE_ID_BITS = 10
    SEQUENCE_BITS = 12
    
    MAX_MACHINE_ID = (1 << MACHINE_ID_BITS) - 1
    MAX_SEQUENCE = (1 << SEQUENCE_BITS) - 1
    
    def __init__(self, machine_id):
        if machine_id > self.MAX_MACHINE_ID or machine_id < 0:
            raise ValueError(f"Machine ID must be 0-{self.MAX_MACHINE_ID}")
        
        self.machine_id = machine_id
        self.sequence = 0
        self.last_timestamp = -1
    
    def generate(self):
        timestamp = self._current_millis()
        
        if timestamp < self.last_timestamp:
            raise Exception("Clock moved backwards!")
        
        if timestamp == self.last_timestamp:
            # Same millisecond, increment sequence
            self.sequence = (self.sequence + 1) & self.MAX_SEQUENCE
            
            if self.sequence == 0:
                # Sequence exhausted, wait for next millisecond
                timestamp = self._wait_next_millis(self.last_timestamp)
        else:
            # New millisecond, reset sequence
            self.sequence = 0
        
        self.last_timestamp = timestamp
        
        # Construct ID:
        # timestamp(41) | machine_id(10) | sequence(12)
        id = ((timestamp - self.EPOCH) << 22) | \
             (self.machine_id << 12) | \
             self.sequence
        
        return id
    
    def _current_millis(self):
        return int(time.time() * 1000)
    
    def _wait_next_millis(self, last_timestamp):
        timestamp = self._current_millis()
        while timestamp <= last_timestamp:
            timestamp = self._current_millis()
        return timestamp

# Usage
generator = SnowflakeIDGenerator(machine_id=1)
tweet_id = generator.generate()
# Output: 1234567890123456789
```

**Benefits**:
- **K-sortable**: IDs are roughly chronological
- **Unique**: No coordination needed between machines
- **High throughput**: 4096 IDs per millisecond per machine
- **Compact**: Fits in 64-bit integer (better than UUID)

### Deep Dive 3: Rate Limiting (Token Bucket)

**Algorithm**:

```python
class TokenBucket:
    """Token bucket rate limiter using Redis"""
    
    def __init__(self, redis_client):
        self.redis = redis_client
    
    def is_allowed(self, user_id, action, capacity, refill_rate):
        """
        Check if user action is allowed
        
        Args:
            user_id: User identifier
            action: Action type (e.g., "tweet", "follow")
            capacity: Maximum tokens (burst size)
            refill_rate: Tokens per second
        
        Returns:
            bool: True if allowed, False if rate limited
        """
        key = f"ratelimit:{user_id}:{action}"
        now = time.time()
        
        # Get bucket state
        bucket = self.redis.hgetall(key)
        
        if not bucket:
            # New bucket
            tokens = capacity - 1
            last_refill = now
        else:
            tokens = float(bucket['tokens'])
            last_refill = float(bucket['last_refill'])
            
            # Refill tokens based on elapsed time
            elapsed = now - last_refill
            tokens_to_add = elapsed * refill_rate
            tokens = min(capacity, tokens + tokens_to_add)
        
        if tokens >= 1:
            # Consume token
            self.redis.hset(key, mapping={
                'tokens': tokens - 1,
                'last_refill': now
            })
            self.redis.expire(key, 3600)  # 1 hour TTL
            return True
        
        # Rate limited
        return False

# Usage
limiter = TokenBucket(redis_client)

# Allow 100 tweets per hour (refill rate: 100/3600 = 0.0278/sec)
if limiter.is_allowed(user_id, "tweet", capacity=100, refill_rate=0.0278):
    # Post tweet
    post_tweet()
else:
    # Rate limited
    raise RateLimitError("Tweet limit exceeded: 100/hour")
```

**Alternative: Sliding Window**:

```python
def check_rate_limit_sliding_window(user_id, action, limit, window_seconds):
    """Sliding window rate limiter"""
    key = f"rate:{user_id}:{action}"
    now = time.time()
    
    # Remove old entries
    redis.zremrangebyscore(key, 0, now - window_seconds)
    
    # Count requests in window
    current_count = redis.zcard(key)
    
    if current_count < limit:
        # Add current request
        redis.zadd(key, {str(uuid.uuid4()): now})
        redis.expire(key, window_seconds)
        return True
    
    return False  # Rate limited
```

### Deep Dive 4: Trending Topics

**Real-time Stream Processing**:

```python
class TrendingTopicsCalculator:
    """Calculate trending hashtags using stream processing"""
    
    def __init__(self):
        self.window_size = 3600  # 1 hour
        self.historical_days = 7
    
    def calculate_trend_score(self, hashtag, current_count, unique_users):
        """
        Calculate trend score for hashtag
        
        Score = (Volume Ratio) × (Diversity) × (Time Decay)
        """
        # Get historical average
        historical_avg = self.get_historical_average(
            hashtag, 
            days=self.historical_days
        )
        
        if historical_avg == 0:
            historical_avg = 1  # Avoid division by zero
        
        # Volume ratio
        volume_ratio = current_count / historical_avg
        
        # Diversity factor (more unique users = higher score)
        # Cap at 2x boost
        diversity_factor = min(unique_users / 100.0, 2.0)
        
        # Time decay (favor recent activity)
        time_decay = 1.0  # Can add exponential decay
        
        score = volume_ratio * diversity_factor * time_decay
        
        return score
    
    def get_historical_average(self, hashtag, days=7):
        """Get average tweets per hour over last N days"""
        # Query from analytics database
        result = analytics_db.query("""
            SELECT AVG(tweet_count) 
            FROM hashtag_hourly_stats
            WHERE hashtag = ?
            AND date > NOW() - INTERVAL ? DAY
        """, hashtag, days)
        
        return result[0] if result else 0
    
    def update_trending(self):
        """Update trending hashtags (run every 5 minutes)"""
        
        # Get hashtag counts from last hour
        hashtag_counts = redis.hgetall("hashtags:hourly")
        
        trending = []
        
        for hashtag, count_data in hashtag_counts.items():
            count, unique_users = count_data.split(":")
            
            score = self.calculate_trend_score(
                hashtag,
                int(count),
                int(unique_users)
            )
            
            if score > 1.5:  # Trending threshold
                trending.append((hashtag, score, int(count)))
        
        # Sort by score
        trending.sort(key=lambda x: x[1], reverse=True)
        
        # Update Redis cache
        pipeline = redis.pipeline()
        pipeline.delete("trending:global")
        
        for hashtag, score, count in trending[:50]:
            pipeline.zadd("trending:global", {hashtag: score})
            pipeline.hset(f"trending:details:{hashtag}", mapping={
                "score": score,
                "tweet_count": count
            })
        
        pipeline.execute()

# Storm/Flink topology for real-time counting
def process_tweet_stream():
    """Process incoming tweets for trending calculation"""
    
    for tweet in kafka.consume("tweets"):
        hashtags = extract_hashtags(tweet.content)
        
        for hashtag in hashtags:
            # Increment hourly count
            redis.hincrby("hashtags:hourly", hashtag, 1)
            
            # Track unique users
            redis.sadd(f"hashtag:users:{hashtag}", tweet.user_id)
            
            # Update unique user count
            unique_count = redis.scard(f"hashtag:users:{hashtag}")
            redis.hset(
                "hashtags:hourly",
                hashtag,
                f"{count}:{unique_count}"
            )
```

### Deep Dive 5: Search with Elasticsearch

**Indexing Strategy**:

```python
# Elasticsearch index mapping
TWEET_INDEX_MAPPING = {
    "mappings": {
        "properties": {
            "tweet_id": {"type": "keyword"},
            "user_id": {"type": "keyword"},
            "username": {"type": "keyword"},
            "content": {
                "type": "text",
                "analyzer": "standard",
                "fields": {
                    "keyword": {"type": "keyword"}
                }
            },
            "hashtags": {"type": "keyword"},
            "mentions": {"type": "keyword"},
            "created_at": {"type": "date"},
            "like_count": {"type": "integer"},
            "retweet_count": {"type": "integer"},
            "reply_count": {"type": "integer"},
            "engagement_score": {"type": "float"}
        }
    }
}

# Indexing pipeline
def index_tweet(tweet):
    """Index tweet in Elasticsearch"""
    
    # Calculate engagement score
    engagement_score = (
        tweet.like_count * 1.0 +
        tweet.retweet_count * 2.0 +
        tweet.reply_count * 3.0
    )
    
    document = {
        "tweet_id": tweet.tweet_id,
        "user_id": tweet.user_id,
        "username": tweet.username,
        "content": tweet.content,
        "hashtags": tweet.hashtags,
        "mentions": tweet.mentions,
        "created_at": tweet.created_at,
        "like_count": tweet.like_count,
        "retweet_count": tweet.retweet_count,
        "reply_count": tweet.reply_count,
        "engagement_score": engagement_score
    }
    
    es.index(
        index="tweets",
        id=tweet.tweet_id,
        document=document
    )

# Search query
def search_tweets(query, filters=None):
    """Search tweets with ranking"""
    
    search_query = {
        "query": {
            "bool": {
                "must": [
                    {
                        "multi_match": {
                            "query": query,
                            "fields": ["content^2", "hashtags", "username"],
                            "fuzziness": "AUTO"
                        }
                    }
                ],
                "filter": []
            }
        },
        "sort": [
            {"_score": "desc"},
            {"created_at": "desc"},
            {"engagement_score": "desc"}
        ],
        "size": 20
    }
    
    # Add filters
    if filters:
        if "from_user" in filters:
            search_query["query"]["bool"]["filter"].append({
                "term": {"user_id": filters["from_user"]}
            })
        
        if "since" in filters:
            search_query["query"]["bool"]["filter"].append({
                "range": {"created_at": {"gte": filters["since"]}}
            })
        
        if "hashtag" in filters:
            search_query["query"]["bool"]["filter"].append({
                "term": {"hashtags": filters["hashtag"]}
            })
    
    results = es.search(index="tweets", body=search_query)
    return results["hits"]["hits"]
```

### Deep Dive 6: High Availability & Disaster Recovery

**Multi-Region Architecture**:

```
Region            Traffic    Role
us-east-1         40%        Primary (writes + reads)
us-west-2         30%        Secondary (reads only)
eu-west-1         20%        Secondary (reads only)
ap-southeast-1    10%        Secondary (reads only)

Replication:
- MySQL: Master (us-east-1) → Async replication to all regions
- Cassandra: Multi-datacenter replication (RF=3 per DC)
- Redis: Cross-region replication for critical caches
```

**Failover Procedures**:

1. **Database Master Failure**:
```python
# Automatic failover with Patroni
def handle_master_failure():
    # 1. Detect failure (health check failure)
    if not ping_master():
        logger.error("Master database down!")
        
        # 2. Promote healthiest slave to master
        slave = select_healthiest_slave()
        promote_to_master(slave)
        
        # 3. Update DNS/config to point to new master
        update_db_endpoint(slave.host)
        
        # 4. Resume writes
        # Writes queued in Kafka during failover
        resume_writes()
        
    # Expected downtime: < 30 seconds
```

2. **Region Failure**:
```python
def handle_region_failure(failed_region):
    # 1. Detect region failure
    if not health_check(failed_region):
        logger.critical(f"Region {failed_region} down!")
        
        # 2. Route traffic to healthy regions
        update_dns_routing(exclude=failed_region)
        
        # 3. Promote read replicas in secondary region
        if failed_region == "us-east-1":  # Primary region
            promote_read_replicas("us-west-2")
        
        # 4. Scale up capacity in healthy regions
        auto_scale_services(multiplier=1.5)
        
    # Expected downtime: < 5 minutes
```

3. **Cache Failure (Graceful Degradation)**:
```python
def get_timeline_with_fallback(user_id):
    try:
        # Try cache first
        return get_timeline_from_cache(user_id)
    except RedisError:
        logger.warning("Redis cache unavailable, falling back to DB")
        
        # Fall back to database (slower but works)
        timeline = generate_timeline_from_db(user_id)
        
        # Rate limit to protect database
        rate_limit_user(user_id, action="timeline_request")
        
        return timeline
```

### Trade-offs & Alternatives

#### 1. Cassandra vs MySQL for Tweets

**Chose: Cassandra**
- ✅ Write-heavy workload (6K writes/sec)
- ✅ Linear scalability
- ✅ No single point of failure
- ❌ No ACID transactions
- ❌ Limited query flexibility

**Alternative: MySQL with Sharding**
- ✅ ACID transactions
- ✅ Rich query support
- ❌ Complex sharding logic
- ❌ Write bottleneck

#### 2. Hybrid vs Pure Push/Pull

**Chose: Hybrid**
- ✅ Fast reads for most users
- ✅ Handles celebrity problem
- ❌ More complex implementation
- ❌ Two code paths to maintain

**Alternative: Pure Pull**
- ✅ Simpler implementation
- ✅ Always works
- ❌ Slow timeline generation
- ❌ High database load

#### 3. WebSocket vs Polling

**Chose: WebSocket for Real-time**
- ✅ Instant updates
- ✅ Lower latency
- ✅ Reduced bandwidth
- ❌ Stateful connections
- ❌ Connection management complexity

**Alternative: Long Polling**
- ✅ Simpler implementation
- ✅ Works with HTTP/1.1
- ❌ Higher latency
- ❌ More server resources

---

## Summary

**Key Design Decisions**:
1. ✅ Hybrid fan-out (solves celebrity problem)
2. ✅ Snowflake IDs (sortable, distributed generation)
3. ✅ Cassandra for tweets (write-heavy, scalable)
4. ✅ Redis for timelines (fast sorted sets)
5. ✅ Token bucket rate limiting (fair, effective)
6. ✅ Elasticsearch for search (full-text, fast)
7. ✅ Kafka for events (high throughput, durable)

**Scalability Achievements**:
- Handles 400M DAU
- Processes 500M tweets/day (6K writes/sec)
- Serves 200B timeline requests/day (2.3M reads/sec)
- Real-time updates (< 5 seconds)
- 99.99% uptime

**Interview Talking Points**:
- Why hybrid fan-out? Balances performance and celebrity problem
- Why Snowflake IDs? Sortable, no coordination needed
- Why Cassandra? Write-heavy, linear scalability, no SPOF
- Why Redis? Fast sorted sets for timelines, low latency
- Why token bucket? Fair rate limiting, handles bursts
- Why Elasticsearch? Full-text search, near real-time indexing

---

**Document Version**: 1.0 - Hello Interview Framework  
**Last Updated**: November 2025  
**System**: Twitter Clone  
**Status**: Complete & Interview-Ready ✅
