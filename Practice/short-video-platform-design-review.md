# Short Video Platform System Design - Review & Improvements

## Current Design Analysis

Based on the provided architecture diagram, here's a comprehensive review of the short video platform design (TikTok/Reels-like system).

---

## 1. STRENGTHS OF CURRENT DESIGN ✅

### 1.1 Good Separation of Concerns
- **Feed Service** and **React Service** are properly separated
- Upload service handles media ingestion independently
- Clear distinction between read and write paths

### 1.2 Appropriate Use of Kafka
- Kafka for event streaming (reel created, like added, comment added) is excellent
- Enables asynchronous processing
- Supports multiple consumers (Feed populator worker, React worker)

### 1.3 Caching Strategy
- Redis cache for feed is correct for read-heavy workload (10:1 read/write ratio)
- Will significantly reduce database load

### 1.4 Storage Selection
- S3 for video storage is appropriate
- Multiple formats storage for different quality levels is good practice

### 1.5 Realistic Scale Estimates
- 1B DAU is reasonable for global platform
- 100M QPS read / 10M QPS write shows understanding of read-heavy nature
- 10:1 read/write ratio is appropriate for social media

---

## 2. CRITICAL ISSUES & GAPS ⚠️

### 2.1 Missing Components

#### **No Load Balancer**
- **Issue**: Direct connection from CDN to API is problematic
- **Impact**: Single point of failure, no traffic distribution
- **Fix Required**: Add Application Load Balancer (ALB)

#### **No API Gateway**
- **Issue**: Missing centralized entry point for API management
- **Impact**: No rate limiting, authentication, request routing at entry
- **Fix Required**: Add API Gateway layer

#### **No Video Processing Pipeline**
- **Issue**: Video upload goes directly to S3 without processing
- **Impact**: No transcoding, no thumbnail generation, no quality optimization
- **Fix Required**: Add video processing service/workers

#### **No Content Delivery Strategy**
- **Issue**: CDN shown but not integrated with video delivery
- **Impact**: Users may experience high latency accessing videos
- **Fix Required**: Proper CDN integration with multi-region strategy

#### **No Search/Discovery Service**
- **Issue**: How do users search for videos or discover new content?
- **Impact**: Limited user engagement paths
- **Fix Required**: Add search/discovery service with Elasticsearch

#### **No Analytics/Metrics Service**
- **Issue**: No way to track video performance, user engagement
- **Impact**: Cannot optimize recommendation algorithm
- **Fix Required**: Add analytics pipeline

### 2.2 Architecture Concerns

#### **Database Choice Unclear**
- **Issue**: "DDB/Postgres" notation is ambiguous
- **Concern**: These serve very different purposes
- **Recommendation**: 
  - DynamoDB for user profiles, video metadata (high read throughput)
  - PostgreSQL for relational data (followers, comments, complex queries)
  - Cassandra for time-series data (feeds, activity logs)

#### **Redis as Cache of Feed - Incomplete**
- **Issue**: No cache invalidation strategy mentioned
- **Issue**: No cache warming strategy
- **Issue**: What happens on cache miss?
- **Recommendation**: Define TTL, cache warming, and fallback strategies

#### **Feed Populator Worker Scalability**
- **Issue**: Single worker shown for 1B DAU
- **Concern**: Massive bottleneck
- **Recommendation**: Distributed worker pool with partitioning

#### **No Monitoring/Observability**
- **Issue**: No mention of logging, metrics, tracing
- **Impact**: Difficult to debug issues at scale
- **Recommendation**: Add comprehensive observability stack

### 2.3 Missing Critical Features

#### **No Recommendation Engine**
- **Issue**: "get recommended reels" in FR but no recommendation service
- **Impact**: Core feature missing from architecture
- **Recommendation**: ML-based recommendation service essential

#### **No User Authentication/Authorization**
- **Issue**: Security layer not shown
- **Impact**: How are users authenticated? How is access controlled?
- **Recommendation**: Add Auth service with OAuth 2.0/JWT

#### **No Notification Service**
- **Issue**: Social features require notifications (likes, comments, followers)
- **Recommendation**: Add push notification service

#### **No Moderation Service**
- **Issue**: Content moderation critical for UGC platforms
- **Recommendation**: Add AI-powered moderation pipeline

---

## 3. SCALABILITY CONCERNS 📈

### 3.1 Database Sharding Strategy
- **Issue**: No sharding strategy mentioned for 1B users
- **Recommendation**: Shard by user_id or geography

### 3.2 Hot Partition Problem
- **Issue**: Popular videos (viral content) will create hotspots
- **Recommendation**: 
  - Implement caching layers at multiple levels
  - Use separate cache for trending/viral content
  - Implement read replicas for hot data

### 3.3 Global Distribution
- **Issue**: No mention of multi-region deployment
- **Recommendation**: Deploy in multiple regions with geo-routing

---

## 4. DETAILED IMPROVEMENTS

### 4.1 Video Upload Flow Improvements

**Current Flow:**
```
Client → API → Upload Service → S3
```

**Improved Flow:**
```
Client → API Gateway → Upload Service → 
  ↓
  1. Generate pre-signed S3 URL
  2. Client uploads directly to S3
  3. Trigger video processing pipeline
  4. Transcode to multiple qualities (360p, 720p, 1080p, 4K)
  5. Generate thumbnails
  6. Extract metadata
  7. Virus scan
  8. Content moderation
  9. Update metadata in DynamoDB
  10. Publish to CDN
  11. Emit "video ready" event to Kafka
```

### 4.2 Feed Generation Improvements

**Current Flow:**
```
Feed Populator Worker → Redis
```

**Improved Flow (Push + Pull Hybrid):**

**Push Model (for users with few followers):**
```
1. User posts video
2. Kafka event triggered
3. Feed populator worker:
   - Fetches follower list
   - Pushes video to each follower's Redis feed cache
   - Works well for users with < 1000 followers
```

**Pull Model (for users with many followers):**
```
1. When user requests feed:
   - Query recent posts from followed users
   - Merge with cached recommendations
   - Rank by ML model
   - Cache result
```

### 4.3 Recommendation System Architecture

**Components Needed:**
```
1. User Profile Service
   - Demographics
   - Interests
   - Watch history
   - Engagement patterns

2. Video Feature Extraction
   - Tags, categories
   - Visual embeddings (CNN)
   - Audio features
   - Creator profile

3. ML Model Service
   - Collaborative filtering
   - Content-based filtering
   - Deep learning model (TensorFlow Serving)
   - A/B testing framework

4. Real-time Feature Store
   - Recent user interactions
   - Trending topics
   - Time-based features

5. Ranking Service
   - Personalization
   - Diversity
   - Freshness
   - Quality signals
```

---

## 5. IMPROVED SYSTEM DESIGN

### 5.1 Complete Architecture Components

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                            │
│  (Mobile Apps, Web, Smart TV)                                    │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        CDN LAYER (CloudFront)                    │
│  • Static assets (thumbnails, profile pics)                      │
│  • Video streaming (HLS/DASH)                                    │
│  • Edge caching                                                  │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GLOBAL LOAD BALANCER (Route 53)              │
│  • Geo-routing                                                   │
│  • Health checks                                                 │
│  • Failover                                                      │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY LAYER                           │
│  • Rate limiting (token bucket)                                  │
│  • Authentication (JWT validation)                               │
│  • Request routing                                               │
│  • Protocol translation                                          │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   APPLICATION LOAD BALANCER                      │
│  • Health checks                                                 │
│  • Traffic distribution                                          │
│  • SSL termination                                               │
└────────────┬────────────────────────────────────────────────────┘
             │
             ├──────────┬──────────┬──────────┬──────────┬─────────┤
             ▼          ▼          ▼          ▼          ▼         ▼
┌───────────────────────────────────────────────────────────────────┐
│                        SERVICE LAYER                              │
│                                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │   Auth      │  │   User      │  │   Video     │             │
│  │   Service   │  │   Service   │  │   Service   │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │   Feed      │  │   Social    │  │   Search    │             │
│  │   Service   │  │   Service   │  │   Service   │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │Recommendation│ │ Notification│  │ Analytics   │             │
│  │   Service   │  │   Service   │  │   Service   │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
└───────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        CACHING LAYER                             │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Redis Cluster (Distributed, Sharded)                    │   │
│  │  • Feed cache (sorted sets by timestamp)                 │   │
│  │  • User session cache                                     │   │
│  │  • Video metadata cache                                   │   │
│  │  • Trending videos cache                                  │   │
│  │  • Social graph cache (followers/following)               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Memcached (for simple key-value)                        │   │
│  │  • User profiles                                          │   │
│  │  • Video counts (views, likes)                            │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MESSAGE QUEUE LAYER                         │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Kafka Cluster (Multi-partition, Multi-replica)          │  │
│  │                                                            │  │
│  │  Topics:                                                   │  │
│  │  • video.uploaded                                          │  │
│  │  • video.processed                                         │  │
│  │  • user.interaction (like, comment, share, follow)        │  │
│  │  • feed.update                                             │  │
│  │  • notification.trigger                                    │  │
│  │  • analytics.event                                         │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
             │
             ├──────────┬──────────┬──────────┬──────────┬─────────┤
             ▼          ▼          ▼          ▼          ▼         ▼
┌───────────────────────────────────────────────────────────────────┐
│                       WORKER/PROCESSOR LAYER                      │
│                                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │   Video     │  │   Feed      │  │   Social    │             │
│  │  Processing │  │  Populator  │  │   Graph     │             │
│  │   Workers   │  │   Workers   │  │   Workers   │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ Notification│  │  Analytics  │  │  Content    │             │
│  │   Workers   │  │   Workers   │  │ Moderation  │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
└───────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       DATA STORAGE LAYER                         │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  DynamoDB (Primary - High Throughput)                    │   │
│  │  • User profiles                                          │   │
│  │  • Video metadata                                         │   │
│  │  • User feed (sorted by timestamp)                        │   │
│  │  • Sharded by user_id                                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  PostgreSQL (Read Replicas - Complex Queries)            │   │
│  │  • User relationships (followers/following)               │   │
│  │  • Comments (with nested structure)                       │   │
│  │  • Analytics aggregations                                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Cassandra (Time-Series Data)                            │   │
│  │  • Activity logs                                          │   │
│  │  • View history                                           │   │
│  │  • User engagement metrics                                │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Elasticsearch (Search & Discovery)                       │   │
│  │  • Video search by title, tags, creator                   │   │
│  │  • User search                                            │   │
│  │  • Trending topics                                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  S3 (Object Storage - Multi-Region)                       │   │
│  │  • Original videos                                        │   │
│  │  • Transcoded videos (multiple qualities)                 │   │
│  │  • Thumbnails                                             │   │
│  │  • User avatars                                           │   │
│  │  Lifecycle: Hot (30 days) → Warm (90 days) → Cold (1yr)  │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ML/ANALYTICS LAYER                            │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Feature Store (Real-time + Batch)                        │   │
│  │  • User features (demographics, preferences)              │   │
│  │  • Video features (embeddings, metadata)                  │   │
│  │  • Context features (time, location, device)              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  ML Model Serving (TensorFlow Serving / SageMaker)       │   │
│  │  • Recommendation model                                   │   │
│  │  • Content moderation model                               │   │
│  │  • Ranking model                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Data Warehouse (Redshift / BigQuery)                     │   │
│  │  • Historical data                                        │   │
│  │  • Batch analytics                                        │   │
│  │  • Model training data                                    │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  OBSERVABILITY LAYER                             │
│  • Logging: ELK Stack (Elasticsearch, Logstash, Kibana)         │
│  • Metrics: Prometheus + Grafana                                 │
│  • Tracing: Jaeger / AWS X-Ray                                   │
│  • Alerting: PagerDuty / Opsgenie                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. KEY WORKFLOWS

### 6.1 Video Upload & Processing Workflow

```
1. User initiates upload
   ↓
2. Auth Service validates user
   ↓
3. Upload Service generates pre-signed S3 URL
   ↓
4. Client uploads directly to S3 (reduces backend load)
   ↓
5. S3 triggers Lambda / SNS notification
   ↓
6. Video Processing Workers (Elastic Transcoder / MediaConvert):
   - Transcode to multiple bitrates (ABR)
     * 360p (mobile, low bandwidth)
     * 720p (standard)
     * 1080p (HD)
     * 4K (premium)
   - Generate thumbnail sprites (for preview)
   - Extract first frame as poster
   - Generate video metadata (duration, resolution, codec)
   - Virus scan (ClamAV)
   - Content moderation (AWS Rekognition / Custom ML)
   ↓
7. Update DynamoDB with video metadata
   ↓
8. Publish to CDN (CloudFront)
   ↓
9. Emit "video.processed" event to Kafka
   ↓
10. Feed Populator Workers consume event:
    - If user has < 1000 followers: Push to follower feeds (Redis)
    - If user has > 1000 followers: Mark for pull-based feed generation
   ↓
11. Notification Workers send push notifications to followers
```

### 6.2 Feed Generation Workflow (Hybrid Push-Pull)

**For Feed Request:**
```
1. User requests feed
   ↓
2. Check Redis cache (user's pre-computed feed)
   ↓
3. If cache hit:
   - Return cached results
   - Async: Refresh cache in background
   ↓
4. If cache miss (or stale):
   - Pull Model:
     a) Fetch following list from Social Graph Service
     b) Query recent videos from followees (last 7 days)
     c) Fetch recommended videos from Recommendation Service
     d) Merge and rank (personalization algorithm)
     e) Cache result in Redis (TTL: 30 minutes)
   ↓
5. Return feed to user
   ↓
6. Track user interactions for recommendation improvement
```

### 6.3 Social Interaction Workflow (Like/Comment/Share)

```
1. User performs action (like, comment, share)
   ↓
2. API Gateway validates request
   ↓
3. Social Service:
   - Write to DynamoDB (video_interactions table)
   - Update counters in Redis (atomic increment)
   - Emit event to Kafka (user.interaction)
   ↓
4. Multiple consumers process event:
   - Analytics Workers: Update engagement metrics
   - Notification Workers: Notify video creator
   - Feed Workers: Update feed scores (boost engagement)
   - Recommendation Workers: Update user profile features
   ↓
5. Acknowledge to user (optimistic UI update)
```

### 6.4 Recommendation System Workflow

```
1. Recommendation Service receives request
   ↓
2. Fetch user features from Feature Store:
   - Historical: Demographics, interests, watch history
   - Real-time: Recent interactions, current context
   ↓
3. Candidate Generation (multiple strategies):
   - Collaborative Filtering: Users similar to you watched...
   - Content-Based: Videos similar to what you watched...
   - Social Graph: Your followers watched...
   - Trending: Popular videos in your region
   - Exploration: Random high-quality videos (10% of feed)
   ↓
4. Candidate Pool: ~1000 videos
   ↓
5. Ranking Phase:
   - ML Model predicts engagement probability
   - Features: Video quality, freshness, diversity, user preference
   - Score each video
   ↓
6. Re-ranking for diversity:
   - Avoid consecutive videos from same creator
   - Mix content types (comedy, education, sports)
   - Consider watch time completion rate
   ↓
7. Final Feed: Top 50 videos
   ↓
8. Cache in Redis (30 min TTL)
   ↓
9. Return to user
   ↓
10. Track user engagement for model improvement
```

---

## 7. DATA MODELS

### 7.1 User Table (DynamoDB)
```javascript
{
  "user_id": "uuid",           // Partition Key
  "username": "string",
  "email": "string",
  "display_name": "string",
  "bio": "string",
  "profile_image_url": "string",
  "verified": boolean,
  "follower_count": number,
  "following_count": number,
  "video_count": number,
  "total_likes": number,
  "created_at": timestamp,
  "updated_at": timestamp
}

// GSI: username-index (for username lookup)
// GSI: email-index (for login)
```

### 7.2 Video Table (DynamoDB)
```javascript
{
  "video_id": "uuid",          // Partition Key
  "user_id": "uuid",           // GSI partition key
  "title": "string",
  "description": "string",
  "tags": ["string"],
  "category": "string",
  "duration": number,          // seconds
  "thumbnail_url": "string",
  "video_urls": {
    "360p": "string",
    "720p": "string",
    "1080p": "string",
    "4K": "string"
  },
  "view_count": number,
  "like_count": number,
  "comment_count": number,
  "share_count": number,
  "status": "processing|ready|failed",
  "visibility": "public|private|followers",
  "location": {
    "latitude": number,
    "longitude": number,
    "city": "string"
  },
  "music_id": "uuid",
  "created_at": timestamp,
  "updated_at": timestamp
}

// GSI: user_id-created_at (for user's videos)
// GSI: status-created_at (for processing queue)
// GSI: category-view_count (for trending by category)
```

### 7.3 User Feed (Redis Sorted Set)
```
Key: feed:{user_id}
Score: timestamp
Value: video_id

ZADD feed:user123 1700000000 video_abc
ZADD feed:user123 1700000001 video_def
ZADD feed:user123 1700000002 video_ghi

// Get latest 20 videos
ZREVRANGE feed:user123 0 19
```

### 7.4 Social Graph (PostgreSQL)
```sql
CREATE TABLE followers (
  id BIGSERIAL PRIMARY KEY,
  follower_user_id UUID NOT NULL,
  followee_user_id UUID NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(follower_user_id, followee_user_id)
);

CREATE INDEX idx_follower ON followers(follower_user_id);
CREATE INDEX idx_followee ON followers(followee_user_id);
```

### 7.5 Comments (PostgreSQL - Nested Set Model)
```sql
CREATE TABLE comments (
  id BIGSERIAL PRIMARY KEY,
  video_id UUID NOT NULL,
  user_id UUID NOT NULL,
  parent_comment_id BIGINT,
  content TEXT NOT NULL,
  like_count INT DEFAULT 0,
  lft INT NOT NULL,              -- For nested set model
  rgt INT NOT NULL,              -- For nested set model
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_video_comments ON comments(video_id, created_at);
CREATE INDEX idx_user_comments ON comments(user_id, created_at);
CREATE INDEX idx_nested_set ON comments(lft, rgt);
```

### 7.6 View History (Cassandra)
```cql
CREATE TABLE view_history (
  user_id UUID,
  video_id UUID,
  watched_at TIMESTAMP,
  watch_duration INT,            -- seconds watched
  completion_rate FLOAT,         -- % of video watched
  source TEXT,                   -- 'feed', 'search', 'profile'
  PRIMARY KEY (user_id, watched_at, video_id)
) WITH CLUSTERING ORDER BY (watched_at DESC);
```

### 7.7 Interactions (Cassandra - Time Series)
```cql
CREATE TABLE user_interactions (
  user_id UUID,
  timestamp TIMESTAMP,
  video_id UUID,
  interaction_type TEXT,         -- 'view', 'like', 'comment', 'share'
  metadata MAP<TEXT, TEXT>,
  PRIMARY KEY (user_id, timestamp, video_id)
) WITH CLUSTERING ORDER BY (timestamp DESC);
```

---

## 8. API DESIGN

### 8.1 Video APIs

```http
POST /api/v1/videos/upload
Authorization: Bearer {jwt_token}
Content-Type: multipart/form-data

Request:
{
  "title": "string",
  "description": "string",
  "tags": ["string"],
  "category": "string",
  "visibility": "public|private|followers",
  "location": {
    "latitude": number,
    "longitude": number
  }
}

Response:
{
  "upload_url": "string",      // Pre-signed S3 URL
  "video_id": "uuid",
  "expires_in": 3600           // seconds
}

---

GET /api/v1/videos/{video_id}
Authorization: Bearer {jwt_token}

Response:
{
  "video_id": "uuid",
  "user": {
    "user_id": "uuid",
    "username": "string",
    "display_name": "string",
    "profile_image_url": "string",
    "verified": boolean
  },
  "title": "string",
  "description": "string",
  "tags": ["string"],
  "video_urls": {
    "360p": "string",
    "720p": "string",
    "1080p": "string"
  },
  "thumbnail_url": "string",
  "duration": number,
  "stats": {
    "views": number,
    "likes": number,
    "comments": number,
    "shares": number
  },
  "user_interaction": {
    "liked": boolean,
    "saved": boolean
  },
  "created_at": "timestamp"
}

---

DELETE /api/v1/videos/{video_id}
Authorization: Bearer {jwt_token}

Response:
{
  "success": boolean,
  "message": "string"
}
```

### 8.2 Feed APIs

```http
GET /api/v1/feed/for-you
Authorization: Bearer {jwt_token}
Query Parameters:
  - page_size: int (default: 20)
  - cursor: string (for pagination)

Response:
{
  "videos": [
    {
      "video_id": "uuid",
      "user": {...},
      "title": "string",
      "video_urls": {...},
      "thumbnail_url": "string",
      "stats": {...}
    }
  ],
  "next_cursor": "string",
  "has_more": boolean
}

---

GET /api/v1/feed/following
Authorization: Bearer {jwt_token}
Query Parameters:
  - page_size: int (default: 20)
  - cursor: string

Response
