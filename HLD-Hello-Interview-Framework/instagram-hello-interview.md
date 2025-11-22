# Instagram System Design - Hello Interview Framework

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
   - Profile management (bio, profile picture, privacy settings)
   - User search by username
   - Follow/unfollow users

2. **Content Management**
   - Upload photos/videos (single or carousel)
   - Add captions, hashtags, location tags
   - Delete posts
   - Edit captions

3. **Social Interactions**
   - Like/unlike posts
   - Comment on posts (with nested replies)
   - Share posts to other users

4. **Feed System**
   - Home feed showing posts from followed users
   - Explore feed with recommended content
   - Chronological and algorithmic feed options

5. **Stories**
   - Post stories (24-hour expiry)
   - View stories from followed users
   - Track story views

#### Nice to Have (P1)
- Direct messaging between users
- User tagging in posts
- Saved posts collection
- Activity notifications
- Hashtag following
- Live streaming
- Reels (short videos)

### Non-Functional Requirements

#### Scalability
- **Daily Active Users (DAU)**: 500 million
- **Photos/Videos uploaded**: 50 million per day
- **Feed requests**: 5 billion per day
- **Storage capacity**: 100+ PB
- **Read:Write ratio**: 100:1 (read-heavy)

#### Performance
- **Feed load time**: < 500ms (p99)
- **Image load time**: < 200ms (p99)
- **Upload time**: < 2s for photos, < 10s for videos
- **Search latency**: < 300ms
- **API response time**: < 100ms (p95)

#### Availability
- **Uptime**: 99.99% (4 nines)
- **Multi-region deployment** for disaster recovery
- **Graceful degradation** during partial failures

#### Consistency
- **Eventual consistency** for feed updates (acceptable delay)
- **Strong consistency** for user authentication
- **Causal consistency** for comments/likes

#### Security
- OAuth 2.0 authentication
- HTTPS for all communications (TLS 1.3)
- Encryption at rest (S3, databases)
- Rate limiting per user/IP
- DDoS protection
- Content moderation (AI + manual review)

### Capacity Estimation

#### Traffic Estimates
```
Daily Active Users (DAU): 500M
Average reads per user: 10 feed refreshes/day

Read requests/day: 500M × 10 = 5 billion
Read QPS: 5B / 86,400 ≈ 58K QPS
Peak read QPS (3x): ~175K QPS

Write requests/day: 50M uploads
Write QPS: 50M / 86,400 ≈ 580 QPS
Peak write QPS (3x): ~1,740 QPS
```

#### Storage Estimates
```
Average photo size: 200 KB
Average video size: 2 MB
Photos per day: 50M × 90% = 45M
Videos per day: 50M × 10% = 5M

Daily storage:
  Photos: 45M × 200 KB = 9 TB/day
  Videos: 5M × 2 MB = 10 TB/day
  Total: ~19 TB/day

Yearly storage: 19 TB × 365 = 6.9 PB/year
10-year storage: ~69 PB

With multiple image sizes (original, large, medium, thumbnail):
  Actual storage: 69 PB × 2.5 = 172.5 PB
```

#### Bandwidth Estimates
```
Incoming (uploads):
  19 TB/day ÷ 86,400 seconds = 220 MB/s
  Peak (3x): 660 MB/s

Outgoing (downloads, 100:1 ratio):
  220 MB/s × 100 = 22 GB/s
  Peak (3x): 66 GB/s
```

#### Memory Estimates (Caching)
```
Cache 20% of daily feed requests:
  5B × 20% = 1B requests
  Average cache entry: 1 KB
  Total memory: 1B × 1 KB = 1 TB

Distributed across cache cluster:
  20 Redis servers: 50 GB each
```

#### Database Estimates
```
Users: 2B × 1 KB = 2 TB
Posts: 10B × 500 bytes = 5 TB
Likes: 50B × 100 bytes = 5 TB
Comments: 20B × 200 bytes = 4 TB
Followers: 100B × 50 bytes = 5 TB
Total: ~21 TB (excluding media)
```

---

## 2️⃣ Core Entities

### Entity 1: User
**Purpose**: Represents a registered Instagram user

**Schema**:
```sql
Users Table:
- user_id (PK, UUID)
- username (unique, indexed)
- email (unique, indexed)
- full_name
- bio (text, max 150 chars)
- profile_picture_url
- is_verified (boolean)
- privacy_level (enum: public, private)
- follower_count (counter)
- following_count (counter)
- post_count (counter)
- created_at (timestamp)
- updated_at (timestamp)

Indexes:
- PRIMARY KEY (user_id)
- UNIQUE INDEX (username)
- UNIQUE INDEX (email)
- INDEX (created_at)
```

**Relationships**:
- Has many: Posts, Stories, Comments, Likes
- Many-to-many: Followers/Following (self-referential)

### Entity 2: Post
**Purpose**: Represents a photo or video post

**Schema**:
```sql
Posts Table:
- post_id (PK, UUID)
- user_id (FK, indexed)
- caption (text, max 2200 chars)
- media_urls (JSON array)
  [
    {
      "type": "image|video",
      "original": "url",
      "large": "url",
      "medium": "url",
      "thumbnail": "url"
    }
  ]
- media_type (enum: photo, video, carousel)
- location_id (FK to Locations, nullable)
- location_name (string)
- hashtags (array of strings)
- is_archived (boolean)
- comments_disabled (boolean)
- likes_count (counter, cached)
- comments_count (counter, cached)
- shares_count (counter, cached)
- created_at (timestamp, indexed)
- updated_at (timestamp)

Indexes:
- PRIMARY KEY (post_id)
- INDEX (user_id, created_at DESC)
- INDEX (created_at DESC)
- INDEX (location_id)
```

**Relationships**:
- Belongs to: User
- Has many: Comments, Likes, Tags
- Optional: Location

### Entity 3: Like
**Purpose**: Tracks user likes on posts

**Schema**:
```sql
Likes Table:
- post_id (FK to Posts)
- user_id (FK to Users)
- created_at (timestamp)

PRIMARY KEY (post_id, user_id)
INDEX (post_id, created_at DESC)
INDEX (user_id, created_at DESC)
```

**Relationships**:
- Belongs to: User
- Belongs to: Post

**Design Note**: Composite primary key prevents duplicate likes

### Entity 4: Comment
**Purpose**: User comments on posts

**Schema**:
```sql
Comments Table:
- comment_id (PK, UUID)
- post_id (FK to Posts, indexed)
- user_id (FK to Users)
- parent_comment_id (FK to Comments, nullable)
- text (varchar 2200)
- likes_count (counter)
- is_deleted (boolean)
- created_at (timestamp)
- updated_at (timestamp)

Indexes:
- PRIMARY KEY (comment_id)
- INDEX (post_id, created_at)
- INDEX (user_id, created_at)
- INDEX (parent_comment_id)
```

**Relationships**:
- Belongs to: User, Post
- Optional parent: Comment (for nested replies)

### Entity 5: Follow
**Purpose**: Social graph (who follows whom)

**Schema**:
```sql
Followers Table:
- follower_id (FK to Users)
- followee_id (FK to Users)
- created_at (timestamp)
- status (enum: active, pending, blocked)

PRIMARY KEY (follower_id, followee_id)
INDEX (followee_id, created_at)
INDEX (follower_id, created_at)
INDEX (followee_id, status)
```

**Relationships**:
- Links two Users
- Self-referential many-to-many

**Design Note**: 
- `follower_id` = user who follows
- `followee_id` = user being followed
- Status "pending" for private accounts

### Entity 6: Story
**Purpose**: 24-hour ephemeral content

**Schema**:
```sql
Stories Table:
- story_id (PK, UUID)
- user_id (FK to Users, indexed)
- media_url (string)
- media_type (enum: image, video)
- duration (int, seconds)
- view_count (counter)
- created_at (timestamp, indexed)
- expires_at (timestamp, indexed)

Indexes:
- PRIMARY KEY (story_id)
- INDEX (user_id, created_at DESC)
- INDEX (expires_at)

Story_Views Table:
- story_id (FK to Stories)
- viewer_id (FK to Users)
- viewed_at (timestamp)

PRIMARY KEY (story_id, viewer_id)
INDEX (story_id, viewed_at)
INDEX (viewer_id, viewed_at)
```

**Relationships**:
- Belongs to: User
- Has many: Story Views

**Design Note**: Background job deletes expired stories hourly

### Entity 7: Feed (Cached)
**Purpose**: Pre-computed user feeds for fast access

**Redis Structure**:
```
Key: feed:{user_id}
Type: Sorted Set
Members: post_id
Score: timestamp or ranking_score

Example:
ZADD feed:user123 1705603200 post_abc
ZADD feed:user123 1705606800 post_xyz
```

**TTL**: 5-10 minutes

### Entity 8: Message (DM)
**Purpose**: Direct messages between users

**Schema**:
```sql
Conversations Table:
- conversation_id (PK, UUID)
- participant_1_id (FK to Users)
- participant_2_id (FK to Users)
- last_message_id (FK to Messages)
- updated_at (timestamp)

PRIMARY KEY (conversation_id)
INDEX (participant_1_id, updated_at)
INDEX (participant_2_id, updated_at)

Messages Table (Cassandra):
- conversation_id (partition key)
- message_id (clustering key, timeuuid)
- sender_id (FK to Users)
- receiver_id (FK to Users)
- content (text)
- message_type (enum: text, image, video, post_share)
- is_read (boolean)
- created_at (timestamp)
- read_at (timestamp, nullable)

PRIMARY KEY ((conversation_id), message_id)
```

**Relationships**:
- Conversation links two Users
- Message belongs to Conversation and User

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
POST /api/v1/users/register

Request:
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "hashed_password",
  "full_name": "Alice Johnson"
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

#### 2. Login
```
POST /api/v1/users/login

Request:
{
  "username": "alice",
  "password": "hashed_password"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "user_id": "usr_123abc",
    "username": "alice",
    "access_token": "jwt_token",
    "refresh_token": "refresh_token"
  }
}
```

#### 3. Get User Profile
```
GET /api/v1/users/{username}

Response (200 OK):
{
  "success": true,
  "data": {
    "user_id": "usr_123abc",
    "username": "alice",
    "full_name": "Alice Johnson",
    "bio": "Photography enthusiast 📸",
    "profile_picture": "https://cdn.instagram.com/users/alice.jpg",
    "is_verified": false,
    "follower_count": 1523,
    "following_count": 456,
    "post_count": 89,
    "is_private": false,
    "is_following": true,
    "is_followed_by": false
  }
}
```

#### 4. Update Profile
```
PUT /api/v1/users/{username}

Request:
{
  "full_name": "Alice J.",
  "bio": "Travel & Photography 🌍📸",
  "profile_picture": "base64_or_url",
  "privacy_level": "public"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "user_id": "usr_123abc",
    "username": "alice",
    "full_name": "Alice J.",
    "bio": "Travel & Photography 🌍📸"
  }
}
```

#### 5. Follow User
```
POST /api/v1/users/{username}/follow

Response (200 OK):
{
  "success": true,
  "data": {
    "username": "bob",
    "status": "following",
    "pending": false
  }
}
```

#### 6. Unfollow User
```
DELETE /api/v1/users/{username}/follow

Response (200 OK):
{
  "success": true,
  "message": "Successfully unfollowed bob"
}
```

#### 7. Get Followers
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
        "full_name": "Charlie Brown",
        "profile_picture": "url",
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

#### 8. Get Following
```
GET /api/v1/users/{username}/following?cursor={cursor}&limit=20

Response: Same structure as Get Followers
```

#### 9. Search Users
```
GET /api/v1/users/search?q={query}&limit=20

Response (200 OK):
{
  "success": true,
  "data": {
    "users": [
      {
        "user_id": "usr_789",
        "username": "alice_photo",
        "full_name": "Alice Photography",
        "profile_picture": "url",
        "is_verified": true,
        "follower_count": 125000
      }
    ]
  }
}
```

### Post APIs

#### 10. Create Post
```
POST /api/v1/posts

Request:
{
  "caption": "Beautiful sunset! 🌅 #sunset #photography",
  "media": [
    {
      "type": "image",
      "url": "s3_key_or_presigned_url"
    }
  ],
  "location": {
    "name": "Santa Monica Beach",
    "lat": 34.0195,
    "lng": -118.4912
  }
}

Response (201 Created):
{
  "success": true,
  "data": {
    "post_id": "post_abc123",
    "user_id": "usr_123abc",
    "caption": "Beautiful sunset! 🌅 #sunset #photography",
    "media": [...],
    "created_at": "2025-01-08T18:30:00Z"
  }
}
```

#### 11. Get Post
```
GET /api/v1/posts/{post_id}

Response (200 OK):
{
  "success": true,
  "data": {
    "post_id": "post_abc123",
    "user": {
      "user_id": "usr_123abc",
      "username": "alice",
      "profile_picture": "url"
    },
    "media": [
      {
        "type": "image",
        "url": "https://cdn.instagram.com/...",
        "width": 1080,
        "height": 1080
      }
    ],
    "caption": "Beautiful sunset! 🌅 #sunset #photography",
    "location": {
      "name": "Santa Monica Beach"
    },
    "likes_count": 1523,
    "comments_count": 89,
    "is_liked": false,
    "created_at": "2025-01-08T18:30:00Z"
  }
}
```

#### 12. Delete Post
```
DELETE /api/v1/posts/{post_id}

Response (204 No Content)
```

#### 13. Update Post Caption
```
PUT /api/v1/posts/{post_id}

Request:
{
  "caption": "Updated caption with new hashtags #newhashtag"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "post_id": "post_abc123",
    "caption": "Updated caption with new hashtags #newhashtag"
  }
}
```

#### 14. Like Post
```
POST /api/v1/posts/{post_id}/like

Response (200 OK):
{
  "success": true,
  "data": {
    "post_id": "post_abc123",
    "likes_count": 1524
  }
}
```

#### 15. Unlike Post
```
DELETE /api/v1/posts/{post_id}/like

Response (200 OK):
{
  "success": true,
  "data": {
    "post_id": "post_abc123",
    "likes_count": 1523
  }
}
```

#### 16. Get Post Likes
```
GET /api/v1/posts/{post_id}/likes?cursor={cursor}&limit=20

Response (200 OK):
{
  "success": true,
  "data": {
    "users": [
      {
        "user_id": "usr_456",
        "username": "bob",
        "full_name": "Bob Smith",
        "profile_picture": "url"
      }
    ],
    "pagination": {
      "next_cursor": "xyz",
      "has_more": true
    }
  }
}
```

#### 17. Add Comment
```
POST /api/v1/posts/{post_id}/comments

Request:
{
  "text": "Amazing photo! 😍",
  "parent_comment_id": null
}

Response (201 Created):
{
  "success": true,
  "data": {
    "comment_id": "cmt_789",
    "post_id": "post_abc123",
    "user": {
      "username": "charlie",
      "profile_picture": "url"
    },
    "text": "Amazing photo! 😍",
    "created_at": "2025-01-08T19:00:00Z"
  }
}
```

#### 18. Get Comments
```
GET /api/v1/posts/{post_id}/comments?cursor={cursor}&limit=20

Response (200 OK):
{
  "success": true,
  "data": {
    "comments": [
      {
        "comment_id": "cmt_789",
        "user": {
          "username": "charlie",
          "profile_picture": "url"
        },
        "text": "Amazing photo! 😍",
        "likes_count": 5,
        "created_at": "2025-01-08T19:00:00Z",
        "replies_count": 2
      }
    ],
    "pagination": {
      "next_cursor": "xyz",
      "has_more": true
    }
  }
}
```

### Feed APIs

#### 19. Get Home Feed
```
GET /api/v1/feed/home?cursor={cursor}&limit=20

Response (200 OK):
{
  "success": true,
  "data": {
    "posts": [
      {
        "post_id": "post_abc123",
        "user": {...},
        "media": [...],
        "caption": "...",
        "likes_count": 1523,
        "comments_count": 89,
        "created_at": "2025-01-08T18:30:00Z"
      }
    ],
    "pagination": {
      "next_cursor": "xyz789",
      "has_more": true
    }
  }
}
```

#### 20. Get Explore Feed
```
GET /api/v1/feed/explore?cursor={cursor}&limit=20

Response: Same structure as Home Feed
```

#### 21. Get User Feed (Profile Posts)
```
GET /api/v1/feed/user/{username}?cursor={cursor}&limit=20

Response: Same structure as Home Feed
```

### Story APIs

#### 22. Create Story
```
POST /api/v1/stories

Request:
{
  "media_url": "s3_key",
  "media_type": "image",
  "duration": 5
}

Response (201 Created):
{
  "success": true,
  "data": {
    "story_id": "story_xyz",
    "user_id": "usr_123abc",
    "media_url": "https://cdn.instagram.com/stories/...",
    "expires_at": "2025-01-09T18:30:00Z",
    "created_at": "2025-01-08T18:30:00Z"
  }
}
```

#### 23. Get Stories Feed
```
GET /api/v1/stories/feed

Response (200 OK):
{
  "success": true,
  "data": {
    "stories": [
      {
        "user": {
          "username": "alice",
          "profile_picture": "url"
        },
        "items": [
          {
            "story_id": "story_xyz",
            "media_url": "url",
            "media_type": "image",
            "created_at": "2025-01-08T18:30:00Z",
            "expires_at": "2025-01-09T18:30:00Z",
            "view_count": 523,
            "is_viewed": false
          }
        ],
        "unviewed_count": 3
      }
    ]
  }
}
```

#### 24. Mark Story as Viewed
```
POST /api/v1/stories/{story_id}/view

Response (200 OK):
{
  "success": true,
  "data": {
    "story_id": "story_xyz",
    "view_count": 524
  }
}
```

### Media Upload API

#### 25. Request Upload URL
```
POST /api/v1/media/upload-url

Request:
{
  "file_type": "image/jpeg",
  "file_size": 2048576
}

Response (200 OK):
{
  "success": true,
  "data": {
    "upload_url": "https://s3.presigned.url",
    "media_key": "media/user123/abc.jpg",
    "expires_in": 3600
  }
}
```

### Rate Limiting
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 998
X-RateLimit-Reset: 1705604800
```

---

## 4️⃣ Data Flow

### Flow 1: User Registration & Login
```
1. User submits registration form
   ↓
2. API Gateway validates input
   ↓
3. User Service checks if username/email exists
   ↓
4. Hash password (bcrypt/scrypt)
   ↓
5. Insert user record into PostgreSQL
   ↓
6. Generate JWT access token (1 hour TTL)
   ↓
7. Generate refresh token (7 days TTL)
   ↓
8. Store refresh token in Redis
   ↓
9. Return tokens to client
   ↓
10. Client stores tokens securely
```

### Flow 2: Upload Photo/Video
```
1. User selects media in mobile app
   ↓
2. App requests pre-signed S3 URL
   POST /api/v1/media/upload-url
   ↓
3. API Server validates auth & generates S3 URL
   ↓
4. App uploads media directly to S3
   (bypasses application server)
   ↓
5. App confirms upload completion
   POST /api/v1/posts (with S3 key)
   ↓
6. Post Service:
   - Creates post record in database
   - Publishes event to Kafka
   ↓
7. Image Processing Worker (consumes from Kafka):
   - Downloads original from S3
   - Generates thumbnails (large, medium, small)
   - Applies compression
   - Uploads processed images back to S3
   - Updates post record with URLs
   ↓
8. Feed Fanout Worker:
   - Gets list of followers (< 10K check)
   - If normal user: pushes post_id to followers' Redis feeds
   - If celebrity: skip fanout (pull at read time)
   ↓
9. Notification Worker:
   - Sends push notifications to followers
   ↓
10. Search Indexer:
    - Updates Elasticsearch with post data
    ↓
11. CDN automatically caches images on first request
```

### Flow 3: View Home Feed
```
1. User opens Instagram app or refreshes feed
   ↓
2. App sends request: GET /api/v1/feed/home?limit=20
   ↓
3. API Gateway:
   - Validates JWT token
   - Checks rate limit (Redis)
   - Routes to Feed Service
   ↓
4. Feed Service:
   - Checks Redis cache: feed:{user_id}
   ↓
5a. Cache HIT:
    - Retrieve post IDs from Redis Sorted Set
    - ZREVRANGE feed:{user_id} 0 19
    ↓
5b. Cache MISS:
    - Get user's following list
    - Classify celebrities (>10K followers)
    - Get pre-computed posts (normal users)
    - Get celebrity posts on-demand (SQL query)
    - Merge and rank posts
    - Store in Redis (5 min TTL)
    ↓
6. Hydrate post details:
   - Batch query: post metadata from database
   - Get like/comment counts from Redis
   - Get user info from cache
   ↓
7. For each post:
   - Generate CDN URLs for media
   - Check if user liked post (Redis)
   ↓
8. Apply ranking algorithm:
   Score = likes×1 + comments×2 + shares×3 
           + recency×10 + affinity×5
   ↓
9. Return top 20 posts as JSON
   ↓
10. Client renders feed
    - Images loaded from CDN
    - Lazy load videos on scroll
```

### Flow 4: Like a Post
```
1. User taps heart icon on post
   ↓
2. Optimistic UI update (instant feedback)
   ↓
3. App sends: POST /api/v1/posts/{post_id}/like
   ↓
4. API Gateway validates & routes
   ↓
5. Post Service:
   - Check if already liked (Redis cache)
   - If not liked:
     a. Insert into Likes table
     b. Increment likes_count in Redis
     c. INCR post:{post_id}:likes
     d. SADD post:{post_id}:liked_by {user_id}
   ↓
6. Publish event to Kafka:
   {
     "event": "post_liked",
     "post_id": "...",
     "user_id": "...",
     "timestamp": "..."
   }
   ↓
7. Notification Worker (async):
   - Get post author from DB
   - Send push notification to author
   - Store notification in DB
   ↓
8. Analytics Worker (async):
   - Update engagement metrics
   - Log event to data warehouse
   ↓
9. Feed Ranker Worker (async):
   - Update post ranking score
   - May promote post in explore feed
   ↓
10. Return success to client
```

### Flow 5: Add Comment
```
1. User types comment and hits send
   ↓
2. App sends: POST /api/v1/posts/{post_id}/comments
   ↓
3. API Gateway validates auth
   ↓
4. Comment Service:
   - Validate comment text (length, content)
   - Insert into Comments table
   - Increment post's comments_count
   ↓
5. Update Redis cache:
   - INCR post:{post_id}:comments
   ↓
6. Publish event to Kafka
   ↓
7. Notification Worker:
   - Notify post author
   - Notify mentioned users (@username)
   ↓
8. Content Moderation Worker:
   - Run AI moderation for spam/abuse
   - Flag if necessary
   ↓
9. Return comment to client
```

### Flow 6: Search Users
```
1. User types in search bar
   ↓
2. App sends: GET /api/v1/users/search?q=alice
   ↓
3. API Gateway routes to Search Service
   ↓
4. Search Service:
   - Query Elasticsearch index
   - Match on username, full_name, bio
   - Fuzzy matching for typos
   ↓
5. Ranking:
   - Verified users ranked higher
   - More followers = higher score
   - Exact matches ranked highest
   ↓
6. Get user details from cache/DB
   ↓
7. Return top 20 results
   ↓
8. Client displays with profile pictures
```

### Flow 7: Post Story
```
1. User creates story (photo/video)
   ↓
2. Upload media to S3 (similar to post upload)
   ↓
3. App sends: POST /api/v1/stories
   ↓
4. Story Service:
   - Create story record (expires_at = now + 24h)
   - Store in PostgreSQL & cache in Redis
   ↓
5. Add to user's story feed:
   - ZADD stories:{user_id} {timestamp} {story_id}
   - SET story:{story_id} {story_data} EX 86400
   ↓
6. Notification Worker:
   - Notify close friends/followers
   ↓
7. Return story to client
   ↓
8. Background Job (hourly):
   - DELETE FROM stories WHERE expires_at < NOW()
```

### Flow 8: Direct Messaging
```
1. User sends message to another user
   ↓
2. App sends: POST /api/v1/messages
   ↓
3. Message Service:
   - Create/get conversation between users
   - Insert message into Cassandra
   ↓
4. WebSocket Server:
   - Check if receiver is online
   - If online: push message via WebSocket
   - If offline: queue for push notification
   ↓
5. Update conversation last_message
   ↓
6. Push Notification Service (if offline):
   - Send FCM/APNS notification
   ↓
7. Mark as delivered when received
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
                              [Mobile/Web Clients]
                                      |
                                      ↓
                    ┌─────────────────────────────────┐
                    |     CDN (CloudFront/Akamai)     |
                    |   - Static assets               |
                    |   - Images/Videos (90% cache)   |
                    └─────────────────────────────────┘
                                      |
                    ┌─────────────────┴─────────────────┐
                    ↓                                   ↓
           [DNS - Route53]                    [S3 Object Storage]
                    ↓                          - 100+ PB media
    [Application Load Balancer]                - Lifecycle policies
         (Multi-AZ, SSL)                       - Cross-region replication
                    |
    ┌───────────────┼───────────────┐
    ↓               ↓               ↓
[API Gateway]  [WebSocket]  [GraphQL Gateway]
- Auth          - Real-time   - Flexible queries
- Rate limit    - DMs          - Feed aggregation
- Routing       - Notifications
    |               |               |
    └───────────────┴───────────────┘
                    |
         ┌──────────┴──────────┐
         ↓                     ↓
[Application Tier]      [Background Workers]
  Microservices:           Kafka Consumers:
  • User Service          • Image Processor
  • Post Service          • Feed Fanout
  • Feed Service          • Notification
  • Story Service         • Analytics
  • Search Service        • Content Moderator
  • Message Service       • Search Indexer
                    |
         ┌──────────┴──────────┐
         ↓                     ↓
[Cache Layer]           [Message Queue]
Redis Cluster            Apache Kafka
• Feed cache            • Event streaming
• Session store         • High throughput
• Counters              • Durable logs
• Rate limits           • 10M+ msg/sec
  (1TB distributed)
         |
    ┌────┴─────┬──────────┬──────────┬──────────┐
    ↓          ↓          ↓          ↓          ↓
[User DB]  [Post DB] [Graph DB] [Message DB] [Search]
PostgreSQL PostgreSQL  Neo4j     Cassandra  Elasticsearch
Master     Master      Cluster   Cluster    Cluster
+ 5 slaves + 5 slaves
Sharded    Sharded               Multi-DC    Multi-node
by user_id by post_id            Replicas    Shards
                    |
         ┌──────────┴──────────┐
         ↓                     ↓
[Data Warehouse]         [Monitoring]
  Hadoop/Spark           • Prometheus
  Analytics              • Grafana
  ML Training            • ELK Stack
  Business Intel         • PagerDuty
```

### Component Details

#### 1. Load Balancer (ALB)
**Purpose**: Distribute traffic across application servers

**Features**:
- Layer 7 (HTTP/HTTPS) routing
- SSL/TLS termination
- Health checks every 10 seconds
- Sticky sessions for WebSocket
- Cross-zone load balancing
- Auto-scaling integration

**Capacity**: 50K concurrent connections per LB

#### 2. API Gateway
**Purpose**: Single entry point, auth, rate limiting

**Responsibilities**:
- JWT token validation (OAuth 2.0)
- Rate limiting using token bucket:
  - 1000 requests/hour per user
  - 50 uploads/day per user
  - 200 follows/hour per user
- Request routing to microservices
- Response caching (CDN integration)
- API versioning (/v1, /v2)

**Technology**: AWS API Gateway, Kong Gateway

#### 3. Microservices Architecture

**User Service**:
- User registration/login
- Profile management
- Follow/unfollow operations
- Authentication & authorization

**Post Service**:
- Create/edit/delete posts
- Like/unlike functionality
- Comment management
- Media URL generation

**Feed Service**:
- Home feed generation (hybrid push-pull)
- Explore feed recommendations
- User timeline
- Feed ranking algorithm

**Story Service**:
- Create/view stories
- 24-hour expiry management
- View tracking

**Search Service**:
- User search
- Hashtag search
- Location search
- Elasticsearch integration

**Message Service**:
- Direct messaging
- Conversation management
- WebSocket connections
- Message delivery

**Notification Service**:
- Push notifications (FCM/APNS)
- In-app notifications
- Email notifications (batched)
- Notification preferences

#### 4. Cache Layer (Redis Cluster)

**What We Cache**:
```
• User Sessions (30 min TTL)
  Key: session:{user_id}
  Value: JWT metadata

• Feed Cache (5 min TTL)
  Key: feed:{user_id}
  Type: Sorted Set
  Members: post_ids
  Score: ranking_score

• Post Counters (no TTL, updated on change)
  Key: post:{post_id}:likes
  Key: post:{post_id}:comments
  Key: post:{post_id}:shares

• User Profiles (1 hour TTL)
  Key: user:{user_id}
  Value: JSON profile data

• Story Feed (24 hour TTL)
  Key: stories:{user_id}
  Type: Sorted Set

• Rate Limit Counters (1 hour window)
  Key: ratelimit:{user_id}:{action}
  Value: counter
```

**Cache Strategy**:
- Cache-aside (lazy loading)
- Write-through for critical data
- Cache warming for popular content

**Cluster Config**:
- 20 nodes × 50GB = 1TB total
- 3 master shards
- 2 replicas per shard
- Redis Sentinel for failover

#### 5. Database Architecture

**A. User Database (PostgreSQL)**
- **Purpose**: User accounts, profiles, relationships
- **Schema**: Users, Followers, Settings
- **Scaling**: 
  - Sharded by user_id (10 shards)
  - Master + 5 read replicas per shard
  - Vertical partitioning by data type
- **Replication**: Async replication (eventual consistency)

**B. Post Database (PostgreSQL/Cassandra)**
- **Purpose**: Posts, likes, comments
- **Schema**: Posts, Likes, Comments
- **Scaling**:
  - Sharded by post_id (20 shards)
  - Time-based partitioning (monthly)
  - Cassandra for high write throughput
- **Replication**: Multi-master (Cassandra)

**C. Graph Database (Neo4j)**
- **Purpose**: Social graph queries
- **Use Cases**:
  - Mutual followers
  - Friend suggestions
  - Shortest path between users
  - Community detection
- **Alternative**: Denormalized adjacency lists in PostgreSQL

**D. Message Database (Cassandra)**
- **Purpose**: Direct messages
- **Schema**: Conversations, Messages
- **Partitioning**: By conversation_id
- **TTL**: Optional message expiry
- **Replication**: RF=3 across data centers

#### 6. Object Storage (Amazon S3)

**Bucket Structure**:
```
instagram-media/
  ├── {region}/
  │   ├── {user_id_shard}/
  │   │   ├── posts/
  │   │   │   ├── {post_id}/
  │   │   │   │   ├── original.jpg
  │   │   │   │   ├── large.jpg (1080px)
  │   │   │   │   ├── medium.jpg (640px)
  │   │   │   │   └── thumb.jpg (150px)
  │   │   ├── stories/
  │   │   └── profile/
```

**Lifecycle Policies**:
- Standard storage: 0-90 days
- Infrequent Access: 90-365 days
- Glacier: 365+ days (archive)
- Delete after 10 years

**CDN Integration**:
- CloudFront distribution per region
- Custom SSL certificate
- Cache control headers
- Signed URLs for private content

#### 7. Message Queue (Apache Kafka)

**Topics**:
```
• post_created (high volume)
• post_liked
• post_commented
• user_followed
• story_posted
• message_sent
• notification_triggered
```

**Partitioning**: By user_id for ordering

**Consumer Groups**:
- Image processing (3 consumers)
- Feed fanout (10 consumers)
- Notifications (5 consumers)
- Analytics (2 consumers)
- Search indexing (2 consumers)

**Retention**: 7 days

#### 8. Search Engine (Elasticsearch)

**Indices**:
```
• users
  - username, full_name, bio
  - follower_count (boost factor)
  
• hashtags
  - tag name
  - post count
  - trending score
  
• locations
  - name, coordinates
  - post count
```

**Features**:
- Fuzzy search (edit distance ≤ 2)
- Autocomplete (prefix matching)
- Synonym handling
- Real-time indexing via Kafka

**Cluster**: 6 nodes (3 master, 3 data)

#### 9. CDN Strategy

**Content Types**:
- **Images**: 90% cache hit ratio
- **Videos**: 70% cache hit ratio
- **Profile pictures**: 95% cache hit
- **Static assets**: 99% cache hit

**Cache Invalidation**:
- Manual purge on content deletion
- Time-based expiry (varies by content)
- Version-based URLs for updates

**Geographic Distribution**:
- 200+ edge locations worldwide
- Regional POPs for major markets
- Reduced latency to < 50ms

#### 10. Monitoring & Observability

**Metrics**:
- Request rate, error rate, latency (RED)
- CPU, memory, disk, network (USE)
- Business metrics (DAU, engagement)

**Logging**:
- Centralized via ELK Stack
- Structured JSON logs
- Log levels: ERROR, WARN, INFO, DEBUG
- Retention: 30 days

**Tracing**:
- Distributed tracing (Jaeger)
- Request ID across services
- Span tracking for performance

**Alerting**:
- PagerDuty for on-call
- Slack for non-critical
- Escalation policies

---

## 6️⃣ Deep Dives

### Deep Dive 1: Feed Generation (Hybrid Push-Pull)

**Challenge**: Generate personalized feeds for 500M users efficiently

**Three Approaches Compared**:

| Approach | Write Speed | Read Speed | Storage | Celebrity Problem |
|----------|-------------|------------|---------|-------------------|
| Fan-out on Write | Slow | Fast | High | ❌ Fails |
| Fan-out on Read | Fast | Slow | Low | ✅ Works |
| Hybrid | Medium | Fast | Medium | ✅ Works |

**Instagram's Hybrid Solution**:

```python
def generate_feed(user_id, limit=20):
    """Hybrid feed generation"""
    
    # Step 1: Get following list with follower counts
    following = get_following_with_counts(user_id)
    
    # Step 2: Classify users
    celebrities = [u for u in following if u.follower_count > 10000]
    normal_users = [u for u in following if u.follower_count <= 10000]
    
    # Step 3: Get pre-computed feed (normal users)
    cached_posts = redis.zrevrange(
        f"feed:{user_id}", 
        0, 
        limit * 2  # Get extra for filtering
    )
    
    # Step 4: Get celebrity posts on-demand
    celebrity_posts = []
    if celebrities:
        celebrity_posts = db.query("""
            SELECT * FROM posts 
            WHERE user_id IN %(celebrity_ids)s 
            AND created_at > NOW() - INTERVAL '7 days'
            ORDER BY created_at DESC 
            LIMIT 50
        """, celebrity_ids=[c.id for c in celebrities])
    
    # Step 5: Merge posts
    all_posts = merge_posts(cached_posts, celebrity_posts)
    
    # Step 6: Apply ML ranking
    ranked_posts = rank_with_ml_model(user_id, all_posts)
    
    # Step 7: Return top N
    return ranked_posts[:limit]


def rank_with_ml_model(user_id, posts):
    """ML-based ranking"""
    features = []
    
    for post in posts:
        feature_vector = {
            # Engagement signals
            'likes': post.likes_count,
            'comments': post.comments_count,
            'shares': post.shares_count,
            
            # Recency (exponential decay)
            'hours_ago': (now() - post.created_at).hours,
            'recency_score': exp(-0.1 * hours_ago),
            
            # User affinity
            'user_affinity': get_affinity(user_id, post.user_id),
            'past_engagement': get_past_engagement(user_id, post.user_id),
            
            # Content type preference
            'is_video': post.media_type == 'video',
            'user_prefers_video': get_video_preference(user_id),
            
            # Social signals
            'mutual_friends_liked': count_mutual_likes(user_id, post.id),
            'is_following': is_following(user_id, post.user_id)
        }
        features.append(feature_vector)
    
    # Run ML model
    scores = ml_model.predict(features)
    
    # Sort by score
    ranked = sorted(zip(posts, scores), key=lambda x: x[1], reverse=True)
    
    return [post for post, score in ranked]
```

**Feed Fanout Worker** (for normal users):
```python
def fanout_post(post_id, user_id):
    """Push post to followers' feeds"""
    
    # Check follower count
    follower_count = get_follower_count(user_id)
    
    if follower_count > 10000:
        # Celebrity: skip fanout
        return
    
    # Get all followers
    followers = get_followers(user_id)
    
    # Batch write to Redis
    pipeline = redis.pipeline()
    timestamp = int(time.time())
    
    for follower_id in followers:
        pipeline.zadd(
            f"feed:{follower_id}",
            {post_id: timestamp}
        )
        # Keep only recent 500 posts
        pipeline.zremrangebyrank(
            f"feed:{follower_id}",
            0, -501
        )
    
    pipeline.execute()
```

### Deep Dive 2: Image Upload & Processing Pipeline

**Complete Flow**:

```
┌─────────┐    1. Request     ┌─────────┐
│         │─────upload URL───→│   API   │
│  Client │                   │ Gateway │
│         │←──2. Pre-signed──│         │
└─────────┘       URL         └─────────┘
     │                              │
     │ 3. Direct upload             │ 4. Confirm
     ↓                              ↓
┌─────────┐                   ┌─────────┐
│   S3    │                   │  Post   │
│ Bucket  │                   │ Service │
└─────────┘                   └─────────┘
     │                              │
     │                              │ 5. Publish event
     │                              ↓
     │                        ┌─────────┐
     │                        │  Kafka  │
     │                        └─────────┘
     │                              │
     │                              │ 6. Consume
     │                              ↓
     │                        ┌──────────────┐
     │←───7. Download────────│    Image     │
     │                        │  Processing  │
     │──→8. Upload processed→│    Worker    │
     │                        └──────────────┘
     │                              │
     │                              │ 9. Update DB
     │                              ↓
     │                        ┌──────────────┐
     │                        │  PostgreSQL  │
     │                        └──────────────┘
```

**Image Processing Details**:

```python
def process_image(s3_key, post_id):
    """Generate multiple sizes"""
    
    # Download original
    img = download_from_s3(s3_key)
    
    sizes = {
        'original': None,  # Keep as-is
        'large': 1080,
        'medium': 640,
        'thumbnail': 150
    }
    
    processed_urls = {}
    
    for size_name, width in sizes.items():
        if size_name == 'original':
            # Just optimize
            optimized = optimize_image(img, quality=90)
            url = upload_to_s3(optimized, f"{post_id}/original.jpg")
        else:
            # Resize + optimize
            resized = resize_image(img, width=width, maintain_aspect=True)
            optimized = optimize_image(resized, quality=85)
            url = upload_to_s3(optimized, f"{post_id}/{size_name}.jpg")
        
        processed_urls[size_name] = url
    
    # Update database
    update_post_media_urls(post_id, processed_urls)
    
    # Invalidate CDN cache if updating
    invalidate_cdn_cache(post_id)
    
    return processed_urls
```

**Optimization Techniques**:
- WebP format (30% smaller than JPEG)
- Progressive JPEG for faster load
- Lazy loading below fold
- Blur-up technique (tiny placeholder)
- Responsive images (`srcset`)

### Deep Dive 3: Scalability & Sharding

**Database Sharding Strategy**:

```python
def get_shard_id(key, num_shards=10):
    """Consistent hashing for shard selection"""
    hash_value = hashlib.md5(key.encode()).hexdigest()
    return int(hash_value, 16) % num_shards


# User sharding
user_shard = get_shard_id(user_id, num_shards=10)
db = get_db_connection(f"user_db_shard_{user_shard}")

# Post sharding  
post_shard = get_shard_id(post_id, num_shards=20)
db = get_db_connection(f"post_db_shard_{post_shard}")
```

**Cross-Shard Queries** (Challenge):

Problem: User's posts spread across multiple shards

Solution: Scatter-gather pattern
```python
def get_user_posts(user_id, limit=20):
    """Query all shards in parallel"""
    
    results = []
    with ThreadPoolExecutor(max_workers=20) as executor:
        futures = []
        
        for shard_id in range(20):  # 20 post shards
            db = get_db_connection(f"post_db_shard_{shard_id}")
            future = executor.submit(
                db.query,
                "SELECT * FROM posts WHERE user_id = %s ORDER BY created_at DESC LIMIT 20",
                user_id
            )
            futures.append(future)
        
        for future in futures:
            results.extend(future.result())
    
    # Merge and sort
    sorted_posts = sorted(results, key=lambda p: p.created_at, reverse=True)
    return sorted_posts[:limit]
```

### Deep Dive 4: High Availability & Disaster Recovery

**Multi-Region Setup**:

```
Primary Region (us-east-1):
- Master databases
- 50% of traffic
- All writes go here

Secondary Region (us-west-2):
- Read replicas
- 30% of traffic
- Reads only

Tertiary Region (eu-west-1):
- Read replicas
- 20% of traffic
- Reads only
```

**Failover Scenarios**:

1. **Database Master Failure**:
   - Automatic promotion of slave to master (Patroni)
   - Expected downtime: < 30 seconds
   - Writes queued in Kafka during failover

2. **Region Failure**:
   - DNS failover to secondary region
   - Promote secondary's read replica to master
   - Expected downtime: < 5 minutes

3. **Cache Failure**:
   - Graceful degradation to database
   - Rate limiting to protect DB
   - Performance impact: 2-3x slower

### Deep Dive 5: Security & Content Moderation

**Authentication Flow**:
```
1. User login → Generate JWT access token (1 hour)
2. Include refresh token (7 days)
3. Store refresh token in Redis
4. On token expiry: use refresh token to get new access token
5. Rotate refresh tokens on use
```

**Content Moderation Pipeline**:

```python
def moderate_content(post_id, media_url):
    """AI + Manual moderation"""
    
    # Step 1: AI moderation
    ai_result = ai_moderator.analyze(media_url)
    
    if ai_result.confidence > 0.95:
        if ai_result.category in ['adult', 'violence', 'hate']:
            # Auto-reject
            flag_post(post_id, reason=ai_result.category)
            notify_user(post_id, 'content_policy_violation')
            return 'REJECTED'
    
    # Step 2: User reports
    report_count = get_report_count(post_id)
    if report_count > 10:
        # Add to manual review queue
        add_to_review_queue(post_id, priority='high')
    
    # Step 3: Manual review (if flagged)
    if ai_result.confidence < 0.95 and ai_result.category != 'safe':
        add_to_review_queue(post_id, priority='medium')
    
    return 'APPROVED'
```

**Rate Limiting (Token Bucket)**:

```python
def check_rate_limit(user_id, action):
    """Token bucket algorithm"""
    
    key = f"ratelimit:{user_id}:{action}"
    
    limits = {
        'api_request': (1000, 3600),  # 1000 per hour
        'post_upload': (50, 86400),   # 50 per day
        'follow': (200, 3600),        # 200 per hour
        'like': (500, 3600)           # 500 per hour
    }
    
    limit, window = limits[action]
    
    # Get current count
    current = redis.get(key) or 0
    
    if int(current) >= limit:
        raise RateLimitExceeded(f"Limit: {limit}/{window}s")
    
    # Increment
    pipe = redis.pipeline()
    pipe.incr(key)
    pipe.expire(key, window)
    pipe.execute()
    
    return True
```

### Deep Dive 6: Performance Optimizations

**Connection Pooling**:
```python
# PostgreSQL
db_pool = psycopg2.pool.ThreadedConnectionPool(
    minconn=10,
    maxconn=50,
    host='db.instagram.com',
    database='instagram_users'
)

# Redis
redis_pool = redis.ConnectionPool(
    host='redis.instagram.com',
    port=6379,
    max_connections=100
)
```

**N+1 Query Prevention**:
```python
# Bad: N+1 queries
posts = Post.query.filter_by(user_id=user_id).all()
for post in posts:
    likes = Like.query.filter_by(post_id=post.id).count()  # N queries!

# Good: Single query with JOIN
posts = db.session.query(
    Post,
    func.count(Like.id).label('like_count')
).outerjoin(Like).group_by(Post.id).all()
```

**Batch Operations**:
```python
# Batch get from cache
def get_posts_batch(post_ids):
    """Get multiple posts in one Redis call"""
    pipe = redis.pipeline()
    for post_id in post_ids:
        pipe.hgetall(f"post:{post_id}")
    return pipe.execute()
```

### Trade-offs & Alternatives

#### 1. Eventual Consistency vs Strong Consistency

**Decision**: Eventual consistency for feeds, strong for payments

**Trade-off**:
- ✅ Better performance & availability
- ❌ Users might see stale data briefly
- Acceptable for social media (not financial)

#### 2. Microservices vs Monolith

**Decision**: Microservices

**Trade-off**:
- ✅ Independent scaling
- ✅ Team autonomy
- ❌ Network latency
- ❌ Distributed tracing complexity

#### 3. SQL vs NoSQL

**Decision**: Hybrid (PostgreSQL + Cassandra + Neo4j)

**Trade-off**:
- ✅ Best tool for each use case
- ❌ Operational complexity
- ❌ Need multiple expertise

#### 4. CDN vs Direct Serving

**Decision**: CDN (CloudFront)

**Trade-off**:
- ✅ 90% faster for global users
- ✅ Reduced bandwidth costs
- ❌ Cache invalidation complexity
- ❌ Additional cost (but worth it)

---

## Summary

**Key Design Decisions**:
1. ✅ Hybrid push-pull feed (balances write/read performance)
2. ✅ Direct S3 upload (bypasses app servers)
3. ✅ Redis for feed caching (90% cache hit ratio)
4. ✅ Kafka for async processing (decouples services)
5. ✅ Microservices architecture (independent scaling)
6. ✅ Multi-region deployment (low latency globally)

**Scalability Achievements**:
- Handles 500M DAU
- Processes 50M uploads/day
- Serves 5B feed requests/day
- Stores 100+ PB of media
- 99.99% uptime

**Interview Talking Points**:
- Why hybrid feed? Balance performance & celebrity problem
- Why Redis? Fast, supports sorted sets, pub/sub
- Why S3? Durable, scalable, cheap for media
- Why Kafka? High throughput, durable, decouples services
- Why sharding? Horizontal scalability for databases

---

**Document Version**: 1.0 - Hello Interview Framework  
**Last Updated**: November 2025  
**System**: Instagram Clone  
**Status**: Complete & Interview-Ready ✅
