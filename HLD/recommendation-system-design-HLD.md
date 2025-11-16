# Recommendation System Design - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [Recommendation Generation Flow](#recommendation-generation-flow)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)
12. [Interview Questions & Answers](#interview-questions--answers)

---

## Problem Statement

Design a scalable, real-time recommendation system for streaming platforms (Netflix, Prime Video, Hulu) that:
- Provides personalized content recommendations
- Supports multiple recommendation types (homepage, similar content, trending)
- Handles cold start scenarios (new users, new content)
- Balances personalization with content discovery
- Operates at massive scale with low latency
- Continuously learns from user interactions
- Supports A/B testing for algorithm improvements

### Scale Requirements
- **200 million daily active users (DAU)**
- **60 billion recommendation requests per day**
- **Peak load: 2 million requests per second**
- **Content catalog: 50,000 movies/shows**
- **User interactions: 2 billion per day**
- **Recommendation latency: < 200ms (p95)**
- **Critical recommendations: < 100ms (homepage)**
- **Model training: Daily batch + real-time updates**
- **99.95% availability**
- **Storage: 90+ TB (with replication)**

---

## Functional Requirements

### Must Have (P0)

#### 1. **Personalized Recommendations**
- Generate user-specific recommendations based on:
  - Watch history (last 100 items)
  - Ratings and likes
  - Search history
  - Viewing patterns (time, completion rate)
  - Demographics (age, location, language)
- Return top-K recommendations (K = 20-50)
- Include confidence scores
- Provide explanation ("Because you watched X")

#### 2. **Homepage Recommendations**
- Primary content feed when user opens app
- Multiple rows/carousels:
  - "Trending Now" (global/regional)
  - "Continue Watching" (resume playback)
  - "Top Picks for You" (personalized)
  - "New Releases" (recent additions)
  - "Popular in [Genre]" (genre-based)
  - "Because You Watched [Title]" (similar content)
- Diverse content mix (avoid same genre/actor)
- Refresh on each session

#### 3. **Similar Content Recommendations**
- "More Like This" when viewing title details
- Find similar movies/shows based on:
  - Content attributes (genre, cast, director)
  - User behavior (co-watched items)
  - Hybrid similarity scores
- Return 10-20 similar items
- Real-time computation (< 100ms)

#### 4. **Trending/Popular Content**
- Global trending (what's hot worldwide)
- Regional trending (popular in user's country)
- Time-window based (last 24 hours, 7 days)
- Category-specific trending (trending in Drama)
- Updated every 15 minutes

#### 5. **Continue Watching**
- Show partially watched content
- Sort by recency
- Include progress indicator (60% watched)
- Remove after completion
- Sync across devices

#### 6. **Search Recommendations**
- Real-time query suggestions
- Search result ranking
- Personalized search results
- "Trending Searches" display

#### 7. **Cold Start Handling**
- **New Users**:
  - Onboarding questionnaire (genre preferences)
  - Show popular/trending content
  - Transition to personalized within 5 interactions
- **New Content**:
  - Use content-based features
  - Promote to diverse user segments
  - Collect feedback quickly

### Nice to Have (P1)
- Time-aware recommendations (weekday vs weekend)
- Mood-based recommendations
- Seasonal content promotion
- Social recommendations (friends watching)
- Notification-triggered recommendations
- Offline recommendation sync
- Kids profile recommendations
- Accessibility-focused recommendations
- Multi-language support
- Cross-platform recommendations (phone → TV)

---

## Non-Functional Requirements

### Performance
- **API latency**: < 50ms (send request)
- **Recommendation generation**: < 200ms (p95)
- **Homepage load**: < 100ms (cached)
- **Model inference**: < 50ms per user
- **Real-time feature update**: < 1 second
- **Batch processing**: < 4 hours (daily training)

### Scalability
- Support 200M DAU
- Handle 60B recommendations/day (~695K QPS average)
- Peak capacity: 2M QPS (3x average)
- Scale to 500M users in 2 years
- Content catalog: 100K+ items
- Concurrent model training jobs: 100+

### Availability
- **99.95% uptime** (~4 hours downtime/year)
- Multi-region deployment (US, EU, Asia, LATAM)
- Graceful degradation (fallback to popular content)
- No recommendation loss during failures
- Automatic failover (< 30 seconds)

### Accuracy
- **Precision@10**: > 30% (3 out of 10 relevant)
- **Click-through rate**: > 5%
- **Watch rate**: > 40% (user plays recommended content)
- **Completion rate**: > 60% (finishes watched content)
- **Coverage**: Recommend 80% of catalog within 30 days
- **Diversity**: Top-20 includes 3+ genres

### Data Freshness
- **User interactions**: Real-time processing (< 1 second)
- **User embeddings**: Updated every 5 minutes
- **Trending content**: Updated every 15 minutes
- **Model updates**: Daily batch training
- **Content metadata**: Real-time on upload

### Privacy & Security
- **Data encryption**: At rest and in transit
- **User anonymization**: Hash user IDs
- **Compliance**: GDPR, CCPA compliant
- **Data retention**: 90 days for interactions
- **Access control**: Role-based permissions
- **Audit logging**: All data access logged

---

## Capacity Estimation

### Traffic Estimates

```
Daily Active Users (DAU): 200M
Sessions per user per day: 3
Recommendations per session: 100 (homepage + similar + search)

Total recommendations/day: 200M × 3 × 100 = 60B

Recommendations/second (average): 60B / 86,400 ≈ 695,000 QPS
Recommendations/second (peak - 3x): 695K × 3 ≈ 2,085,000 QPS ≈ 2M QPS

Distribution by type:
- Homepage: 40% → 24B/day → 278K QPS
- Similar content: 30% → 18B/day → 208K QPS
- Search: 15% → 9B/day → 104K QPS
- Trending: 10% → 6B/day → 69K QPS
- Continue watching: 5% → 3B/day → 35K QPS

User interactions (watch, rate, search):
- Interactions per session: 10
- Total interactions/day: 200M × 3 × 10 = 6B
- Interactions/second: 6B / 86,400 ≈ 69,400 QPS
```

### Storage Estimates

**User Profiles**:
```
Users: 200M
Per user:
{
  user_id: 16 bytes (UUID)
  demographics: 50 bytes
  preferences: 100 bytes (JSON)
  created_at: 8 bytes
  last_active: 8 bytes
}
Total per user: ~180 bytes

Total storage: 200M × 180 bytes = 36 GB
With replication (3x): 108 GB
```

**User-Item Interactions**:
```
Interaction types: Watch, Rate, Search, Click
Events per day: 6B
Retention: 90 days

Per interaction:
{
  user_id: 16 bytes
  content_id: 16 bytes
  interaction_type: 10 bytes
  timestamp: 8 bytes
  duration: 4 bytes (watch time)
  rating: 4 bytes
  device_type: 10 bytes
  metadata: 50 bytes
}
Total per interaction: ~120 bytes

Daily storage: 6B × 120 bytes = 720 GB/day
90-day retention: 720 GB × 90 = 64.8 TB
With replication (3x): 194.4 TB
```

**Content Metadata**:
```
Content items: 50,000
Per item:
{
  content_id: 16 bytes
  title: 100 bytes
  description: 500 bytes
  genres: 50 bytes (array)
  cast: 200 bytes (array)
  director: 50 bytes
  year: 4 bytes
  duration: 4 bytes
  rating: 10 bytes
  language: 10 bytes
  thumbnail_url: 200 bytes
  metadata: 500 bytes (JSON)
}
Total per item: ~1,650 bytes ≈ 2 KB

Total storage: 50K × 2 KB = 100 MB
With replication: 300 MB (negligible)
```

**Content Embeddings** (Vector representations):
```
Content items: 50,000
Embedding dimension: 512
Data type: Float32 (4 bytes)

Per embedding: 512 × 4 = 2,048 bytes = 2 KB

Total storage: 50K × 2 KB = 100 MB
With replication: 300 MB
```

**User Embeddings**:
```
Active users (updated regularly): 200M
Embedding dimension: 128
Data type: Float32 (4 bytes)

Per embedding: 128 × 4 = 512 bytes

Total storage: 200M × 512 bytes = 100 GB
TTL: 7 days (stored in Redis)
With replication: 300 GB
```

**Similarity Matrices**:
```
Item-Item similarity (for collaborative filtering):
Items: 50,000
Similar items per item: 100
Per entry: 16 bytes (item_id) + 4 bytes (score) = 20 bytes

Total: 50K × 100 × 20 bytes = 100 MB
With replication: 300 MB

User-User similarity (computed on-demand, not stored)
```

**Model Storage**:
```
Models:
- Collaborative Filtering model: 10 GB
- Content-Based model: 5 GB
- Deep Learning models (Two-Tower, Transformers): 20 GB
- Ensemble models: 5 GB

Total model storage: 40 GB
With versions (keep last 10): 400 GB
```

**Cache (Redis)**:
```
Active user sessions: 10M (5% of DAU online)
Per session:
- User embedding: 512 bytes
- Recent recommendations: 2 KB
- User preferences: 1 KB
Total per session: ~3.5 KB

Total cache: 10M × 3.5 KB = 35 GB

Recommendation cache:
- Pre-computed recommendations: 50M users (25% of users)
- Per user: 5 KB (100 recommendations with scores)
Total: 50M × 5 KB = 250 GB

Total Redis storage: 285 GB
With replication: 855 GB
```

**Total Storage**:
```
User profiles: 0.1 TB
User interactions (90 days): 194.4 TB
Content metadata: 0.0003 TB
Content embeddings: 0.0003 TB
User embeddings (Redis): 0.3 TB
Similarity matrices: 0.0003 TB
Models: 0.4 TB
Cache (Redis): 0.855 TB
─────────────────────────────────
Total: ~196 TB

Storage breakdown:
- Hot data (Redis): ~1.2 TB
- Warm data (PostgreSQL): ~1 TB
- Cold data (Cassandra): ~194 TB
- Object storage (S3): ~1 TB (models, logs)
```

### Bandwidth Estimates

```
Recommendation response size:
- 20 items per response
- Per item: 200 bytes (id, title, thumbnail URL, score, reason)
- Total per response: 20 × 200 = 4 KB

Daily bandwidth (recommendations):
Outgoing: 60B × 4 KB = 240 TB/day
240 TB / 86,400 seconds ≈ 2.8 GB/second

Peak (3x): 8.4 GB/second

User interaction tracking (incoming):
- 6B interactions/day
- Per interaction: 200 bytes
- Daily: 6B × 200 bytes = 1.2 TB/day
- 1.2 TB / 86,400 ≈ 14 MB/second

Total bandwidth:
- Outgoing: 2.8 GB/s (average), 8.4 GB/s (peak)
- Incoming: 14 MB/s
```

### Memory Requirements

```
Application Servers:
- Request handling: 10 GB per server
- Model serving: 20 GB per server
- Connection pooling: 2 GB per server
Total per server: 32 GB

Model Serving:
- Loaded models: 40 GB
- Inference cache: 10 GB
Total per server: 50 GB

Feature Store (Redis):
- User features: 285 GB (distributed)
- Content features: 50 GB
- Trending cache: 10 GB
Total: 345 GB (across cluster)

Total memory: ~400 GB (distributed across services)
```

### Compute Requirements

```
API Servers (recommendation requests):
- Handle: 2M QPS peak
- Per server capacity: 10K QPS
- Servers needed: 2M / 10K = 200 servers
- With redundancy (2x): 400 servers

Recommendation Engine:
- Generate recommendations: 695K/second
- Per server capacity: 5K recs/second
- Servers needed: 695K / 5K = 139 servers
- With redundancy (2x): 280 servers

Stream Processing (user interactions):
- Process: 69.4K interactions/second
- Per worker capacity: 1K/second
- Workers needed: 70 workers

Batch Processing (model training):
- Daily Spark jobs
- Training cluster: 100 nodes (r5.2xlarge)
- Runtime: 4 hours per day

Total compute:
- API servers: 400 × $100/month = $40K/month
- Rec engines: 280 × $150/month = $42K/month
- Stream workers: 70 × $80/month = $5.6K/month
- Batch training: $10K/month (4 hours/day)
- Total: ~$98K/month compute cost
```

### Infrastructure Cost Estimate

```
Compute: $98K/month
Storage:
- Cassandra (196 TB): $40K/month
- Redis (1.2 TB memory): $15K/month
- PostgreSQL (1 TB): $5K/month
- S3 (1 TB): $0.03K/month
Database total: $60K/month

Networking:
- Data transfer: 240 TB/day × $0.09/GB = $648K/month
- (Reduced with CDN caching to ~$100K/month)

CDN: $50K/month
Monitoring/Logging: $10K/month

Total: ~$318K/month (~$3.8M/year)
Cost per DAU: $1.59/month
Cost per recommendation: $0.0053
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                            CLIENT LAYER                            │
│  Web App | Mobile Apps (iOS/Android) | Smart TV | Gaming Console  │
└───────────────────────────┬────────────────────────────────────────┘
                            │ (HTTPS/HTTP2)
                            ↓
┌────────────────────────────────────────────────────────────────────┐
│                         CDN / EDGE CACHE                           │
│  CloudFront / Akamai - Cache popular recommendations (5 min TTL)   │
└───────────────────────────┬────────────────────────────────────────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY / LOAD BALANCER                     │
│  - SSL termination                                                 │
│  - Authentication (JWT tokens)                                     │
│  - Rate limiting (1000 req/min per user)                           │
│  - Request routing                                                 │
│  - A/B testing (route to algorithm variants)                       │
└───────────────────────────┬────────────────────────────────────────┘
                            │
              ┌─────────────┼─────────────┐
              ↓             ↓              ↓
    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
    │Recommendation│  │  User       │  │ Content     │
    │   Service    │  │  Service    │  │ Service     │
    │ (Stateless)  │  │(Stateless)  │  │(Stateless)  │
    └──────┬───────┘  └──────┬──────┘  └──────┬──────┘
           │                 │                 │
           │     ┌───────────┴─────────────┐   │
           │     │                         │   │
           ↓     ↓                         ↓   ↓
┌─────────────────────────────────────────────────────────────┐
│              CACHE LAYER (Redis Cluster)                    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │ User         │ │ Content      │ │ Recommendation   │   │
│  │ Embeddings   │ │ Embeddings   │ │ Cache            │   │
│  │ (128-dim)    │ │ (512-dim)    │ │ (Top-K per user) │   │
│  │ TTL: 1 hour  │ │ TTL: 1 day   │ │ TTL: 30 min      │   │
│  └──────────────┘ └──────────────┘ └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
           │
           ↓
┌─────────────────────────────────────────────────────────────┐
│           RECOMMENDATION ENGINE (Core ML)                   │
│  ┌───────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │Collaborative  │  │Content-Based │  │ Hybrid Model   │  │
│  │Filtering      │  │Filtering     │  │(Two-Tower NN)  │  │
│  │(Matrix Factor)│  │(Embeddings)  │  │(Deep Learning) │  │
│  └───────────────┘  └──────────────┘  └────────────────┘  │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │         RANKING & RE-RANKING LAYER                  │  │
│  │  • Learning to Rank (LTR)                           │  │
│  │  • Diversity injection (MMR)                        │  │
│  │  • Freshness boost (new content)                    │  │
│  │  • Business rules (contracts, originals)            │  │
│  │  • Calibration & position bias correction           │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │            CANDIDATE GENERATION                     │  │
│  │  Step 1: Generate 500 candidates (fast)             │  │
│  │  Step 2: Score & rank to top-50 (accurate)          │  │
│  │  Step 3: Re-rank with business rules (final)        │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────┬───────────────────────────────────┘
                          │
              ┌───────────┴───────────┐
              ↓                       ↓
    ┌──────────────────┐    ┌──────────────────┐
    │ Vector Search    │    │ Approximate NN   │
    │ (FAISS Index)    │    │ (Annoy/ScaNN)    │
    │ - Fast similarity│    │ - Sub-linear     │
    │ - Sub-millisecond│    │   search         │
    └──────────────────┘    └──────────────────┘
                          │
              ┌───────────┴───────────┐
              ↓                       ↓
┌──────────────────────┐    ┌──────────────────────┐
│  FEATURE STORE       │    │  MODEL REGISTRY      │
│  (Redis + S3)        │    │  (MLflow)            │
│                      │    │                      │
│  User Features:      │    │  - Model versions    │
│  - Watch history     │    │  - A/B test variants │
│  - Genre prefs       │    │  - Performance metrics│
│  - Engagement metrics│    │  - Deployment configs│
│                      │    │                      │
│  Content Features:   │    │  Models:             │
│  - Genres, cast      │    │  - CF_v2.3 (active)  │
│  - Popularity score  │    │  - CB_v1.8 (active)  │
│  - Trending score    │    │  - Hybrid_v3.1 (test)│
│  - Embeddings        │    │  - Transformer_v1.0  │
└──────────────────────┘    └──────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────┐
│            EVENT STREAM (Kafka / Kinesis)                   │
│  Topics:                                                    │
│  - user.interactions (watch, rate, search, click)           │
│  - recommendation.requests (logging)                        │
│  - recommendation.served (what was shown)                   │
│  - model.predictions (inference results)                    │
│  Partitions: By user_id (100 partitions)                    │
└──────────────────┬──────────────────────────────────────────┘
                   │
        ┌──────────┼──────────┐
        ↓          ↓           ↓
┌──────────────┐ ┌────────────┐ ┌──────────────┐
│Stream        │ │  Real-time │ │  Analytics   │
│Processing    │ │  Feature   │ │  Aggregation │
│(Flink/Spark) │ │  Update    │ │  (Metrics)   │
└──────┬───────┘ └─────┬──────┘ └──────┬───────┘
       │               │                │
       ↓               ↓                ↓
┌──────────────────────────────────────────────────────┐
│              DATA STORAGE LAYER                      │
│  ┌──────────────┐ ┌──────────────┐ ┌─────────────┐ │
│  │ PostgreSQL   │ │ Cassandra    │ │ ClickHouse  │ │
│  │              │ │              │ │             │ │
│  │ - Users      │ │ - User-Item  │ │ - Events    │ │
│  │ - Content    │ │   interactions│ │ - Metrics   │ │
│  │ - Metadata   │ │ - Watch logs │ │ - Analytics │ │
│  │              │ │ - Time-series│ │ - Reports   │ │
│  │ Master-Slave │ │ Multi-DC     │ │ Columnar    │ │
│  │ Replication  │ │ Replication  │ │ Compression │ │
│  └──────────────┘ └──────────────┘ └─────────────┘ │
└──────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────┐
│            ML TRAINING PIPELINE (Batch)                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Airflow / Kubernetes CronJobs                │  │
│  │                                                       │  │
│  │  Schedule: Daily at 2 AM UTC                         │  │
│  │  ┌─────────────────────────────────────────────┐    │  │
│  │  │ Step 1: Data Collection (Spark)             │    │  │
│  │  │  - Last 7 days interactions from Cassandra  │    │  │
│  │  │  - User profiles from PostgreSQL            │    │  │
│  │  │  - Content metadata                          │    │  │
│  │  └─────────────────┬───────────────────────────┘    │  │
│  │                    ↓                                 │  │
│  │  ┌─────────────────────────────────────────────┐    │  │
│  │  │ Step 2: Feature Engineering (Spark)         │    │  │
│  │  │  - Aggregate user behavior                   │    │  │
│  │  │  - Compute content statistics                │    │  │
│  │  │  - Generate co-occurrence matrix             │    │  │
│  │  │  - Create training dataset                   │    │  │
│  │  └─────────────────┬───────────────────────────┘    │  │
│  │                    ↓                                 │  │
│  │  ┌─────────────────────────────────────────────┐    │  │
│  │  │ Step 3: Model Training (PyTorch/TensorFlow) │    │  │
│  │  │  - Train CF model (ALS)                      │    │  │
│  │  │  - Train Content-Based (Embeddings)          │    │  │
│  │  │  - Train Deep Learning (Two-Tower)           │    │  │
│  │  │  - Hyperparameter tuning (Optuna)            │    │  │
│  │  └─────────────────┬───────────────────────────┘    │  │
│  │                    ↓                                 │  │
│  │  ┌─────────────────────────────────────────────┐    │  │
│  │  │ Step 4: Model Evaluation                    │    │  │
│  │  │  - Offline metrics (Precision@K, NDCG)      │    │  │
│  │  │  - Validation set performance                │    │  │
│  │  │  - Compare with baseline                     │    │  │
│  │  └─────────────────┬───────────────────────────┘    │  │
│  │                    ↓                                 │  │
│  │  ┌─────────────────────────────────────────────┐    │  │
│  │  │ Step 5: Model Deployment                    │    │  │
│  │  │  - Register in MLflow                        │    │  │
│  │  │  - Deploy to model serving (canary 5%)      │    │  │
│  │  │  - A/B test in production                    │    │  │
│  │  │  - Gradual rollout (5% → 25% → 100%)        │    │  │
│  │  └─────────────────────────────────────────────┘    │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────┐
│        MONITORING & OBSERVABILITY                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │ Prometheus   │ │ Grafana      │ │ ELK Stack        │   │
│  │ (Metrics)    │ │ (Dashboards) │ │ (Logs)           │   │
│  │              │ │              │ │                  │   │
│  │- API latency │ │- Rec quality │ │- Error tracking  │   │
│  │- Cache hit   │ │- CTR/watch   │ │- Request tracing │   │
│  │- Model perf  │ │- A/B results │ │- Audit logs      │   │
│  └──────────────┘ └──────────────┘ └──────────────────┘   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │            Distributed Tracing (Jaeger)              │  │
│  │  - End-to-end request flow                           │  │
│  │  - Latency breakdown by component                    │  │
│  │  - Bottleneck identification                         │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Key Architectural Patterns

1. **Microservices Architecture**
   - Recommendation, User, Content services independently scalable
   - Service mesh for inter-service communication
   - Circuit breakers for fault isolation

2. **Two-Tier Caching**
   - L1: CDN edge cache (popular recommendations)
   - L2: Redis cluster (user-specific data)
   - L3: Application-level cache (models)

3. **Event-Driven Processing**
   - Kafka for user interaction events
   - Real-time stream processing with Flink
   - Async updates to feature store

4. **Two-Stage Recommendation**
   - Candidate Generation: Fast retrieval (500 candidates)
   - Ranking: Accurate scoring (top-50)
   - Re-ranking: Business rules application

5. **Lambda Architecture**
   - Batch layer: Daily model training
   - Speed layer: Real-time feature updates
   - Serving layer: Combined results

---

## Core Components

### 1. Recommendation Service (API Layer)

**Purpose**: Entry point for all recommendation requests

**API Endpoints**:

```http
GET /api/v1/recommendations/homepage/{userId}
Query Parameters:
  - device_type: string (web, mobile, tv)
  - limit: int (default: 50)
  - refresh: boolean (bypass cache)

Response:
{
  "user_id": "user_123",
  "sections": [
    {
      "title": "Top Picks for You",
      "items": [
        {
          "content_id": "movie_456",
          "title": "Inception",
          "thumbnail_url": "https://cdn.../thumb.jpg",
          "score": 0.92,
          "reason": "Based on your viewing history"
        }
      ]
    },
    {
      "title": "Trending Now",
      "items": [...]
    }
  ],
  "generated_at": "2024-01-15T10:00:00Z",
  "expires_at": "2024-01-15T10:30:00Z"
}
```

```http
GET /api/v1/recommendations/similar/{contentId}
Query Parameters:
  - user_id: string (optional, for personalization)
  - limit: int (default: 20)

Response:
{
  "content_id": "movie_456",
  "similar_items": [
    {
      "content_id": "movie_789",
      "title": "The Matrix",
      "similarity_score": 0.88,
      "similarity_type": "hybrid"
    }
  ]
}
```

```http
POST /api/v1/interactions/track
Body:
{
  "user_id": "user_123",
  "content_id": "movie_456",
  "event_type": "watch_start|watch_end|rating|search|click",
  "timestamp": "2024-01-15T10:00:00Z",
  "watch_duration": 3600,
  "rating": 4.5,
  "completion_percentage": 85.5
}

Response:
{
  "status": "tracked",
  "interaction_id": "int_789"
}
```

**Processing Flow**:
```
1. Request arrives at API Gateway
2. Authenticate user (JWT validation)
3. Check rate limit (Redis)
4. Route to Recommendation Service
5. Check cache (Redis):
   - If hit: Return cached recommendations
   - If miss: Generate recommendations
6. Return response (< 50ms)

Cache strategy:
- Key: rec:{user_id}:{context}:{device_type}
- TTL: 30 minutes
- Cache hit rate target: > 80%
```

### 2. Collaborative Filtering Engine

**Algorithm: Matrix Factorization (ALS)**

```python
# User-Item interaction matrix R (sparse)
# R[user_id][content_id] = rating/watch_completion

# Factorize: R ≈ U × V^T
# U: User embedding matrix (200M × 128)
# V: Item embedding matrix (50K × 128)

# For user u and item i:
predicted_score = dot_product(user_embedding[u], item_embedding[i])

# Training objective:
minimize: ||R - U × V^T||^2 + λ(||U||^2 + ||V||^2)
where λ = regularization parameter
```

**Implementation**:
```
Training (Spark MLlib ALS):
1. Load interactions from Cassandra
2. Create sparse matrix (implicit feedback)
3. Run ALS algorithm:
   - Rank: 128 dimensions
   - Iterations: 20
   - Regularization: 0.01
4. Generate embeddings
5. Store in Redis and S3

Inference (Real-time):
1. Fetch user embedding from Redis
2. Compute dot product with all item embeddings
3. Use FAISS for approximate nearest neighbor:
   - Index type: IVF (Inverted File)
   - Search: top-500 candidates
   - Time: < 10ms
4. Return scored candidates
```

**Handling Sparsity**:
```
Problem: New users have few interactions (cold start)

Solutions:
1. Weighted hybrid:
   - First 5 interactions: 80% popular, 20% CF
   - 6-20 interactions: 50% CF, 30% popular, 20% content
   - 20+ interactions: 70% CF, 20% content, 10% popular

2. Fallback chain:
   - Try CF → If low confidence → Content-based → Popular
```

### 3. Content-Based Filtering Engine

**Feature Extraction**:
```
Text Features (Title + Description):
- Tokenization & cleaning
- TF-IDF vectorization
- Word2Vec/BERT embeddings (768-dim)
- Reduce to 256-dim (PCA)

Metadata Features:
- Genres: Multi-hot encoding (20-dim)
- Cast: Top 10 actors, one-hot (10-dim)
- Director: One-hot (5-dim)
- Year: Normalized float (1-dim)
- Rating: Categorical (5-dim)
- Language: One-hot (10-dim)

Combined: 512-dimensional embedding
```

**Similarity Computation**:
```python
def content_similarity(item_i, item_j):
    # Cosine similarity
    emb_i = content_embeddings[item_i]
    emb_j = content_embeddings[item_j]
    
    similarity = cosine_similarity(emb_i, emb_j)
    # = dot(emb_i, emb_j) / (norm(emb_i) * norm(emb_j))
    
    return similarity

# Pre-compute for all pairs:
# Store top-100 similar items per content
# Update daily
```

**Visual Features** (Optional enhancement):
```
Thumbnail/Poster Analysis:
1. Extract frames from video (1 frame per minute)
2. Pass through CNN (ResNet-50)
3. Extract features from last layer (2048-dim)
4. Average pool across frames → 2048-dim
5. Reduce to 256-dim
6. Append to content embedding

Use case: Find visually similar content
```

### 4. Hybrid Model (Two-Tower Neural Network)

**Architecture**:
```
User Tower:
  Input: user_id, watch_history[100], demographics
  ↓
  Embedding Layer:
    - user_id → 64-dim
    - watch_history → average of item embeddings → 128-dim
    - demographics → 32-dim
  ↓
  Concatenate → 224-dim
  ↓
  Dense(512, ReLU) + Dropout(0.3)
  ↓
  Dense(256, ReLU) + Dropout(0.2)
  ↓
  Dense(128) → User Embedding (128-dim)

Item Tower:
  Input: content_id, genres, cast, metadata
  ↓
  Embedding Layer:
    - content_id → 64-dim
    - genres → 32-dim (multi-hot)
    - cast → 32-dim
  ↓
  Concatenate → 128-dim
  ↓
  Dense(256, ReLU)
  ↓
  Dense(128) → Item Embedding (128-dim)

Prediction:
  score = dot_product(user_embedding, item_embedding)
  probability = sigmoid(score)
```

**Training**:
```
Loss: Binary Cross-Entropy
Positive samples: User watched content (completion > 60%)
Negative samples: Random unwatched content (1:4 ratio)

Optimization: Adam
Learning rate: 0.001 with decay
Batch size: 1024
Epochs: 10

Data: Last 30 days interactions
Training time: 2 hours on 100 GPU nodes
```

**Inference Optimization**:
```
Offline:
1. Pre-compute all item embeddings
2. Store in FAISS index
3. Update daily

Online:
1. Compute user embedding (< 5ms)
2. FAISS nearest neighbor search (< 10ms)
3. Return top-500 candidates
```

### 5. Ranking & Re-ranking Layer

**Learning to Rank (LTR)**:
```
Features (per user-item pair):
- Collaborative filtering score
- Content-based score
- Hybrid model score
- Popularity score (time-decayed)
- Freshness score (days since release)
- User-item match:
  - Genre overlap
  - Language match
  - Rating compatibility
- Context features:
  - Time of day
  - Day of week
  - Device type

Model: LightGBM Ranker
Objective: Maximize NDCG@20
Training: Pairwise (RankNet)

Output: Final relevance score
```

**Diversity Injection (MMR)**:
```python
def maximal_marginal_relevance(candidates, k=20, lambda_param=0.7):
    """
    Select diverse top-k items
    
    MMR = λ × relevance(item) - (1-λ) × max_similarity(item, selected)
    """
    selected = []
    
    for _ in range(k):
        best_score = -inf
        best_item = None
        
        for item in candidates:
            if item in selected:
                continue
            
            relevance = item.score
            
            if selected:
                # Max similarity to already selected items
                max_sim = max(
                    content_similarity(item, s) 
                    for s in selected
                )
            else:
                max_sim = 0
            
            mmr_score = lambda_param * relevance - (1 - lambda_param) * max_sim
            
            if mmr_score > best_score:
                best_score = mmr_score
                best_item = item
        
        selected.append(best_item)
    
    return selected

# λ = 0.7: Balance between relevance (70%) and diversity (30%)
```

**Freshness Boost**:
```python
def apply_freshness_boost(score, days_since_release):
    """
    Boost new content (< 30 days)
    """
    if days_since_release <= 7:
        boost = 1.3  # 30% boost for first week
    elif days_since_release <= 30:
        boost = 1.1  # 10% boost for first month
    else:
        boost = 1.0  # No boost
    
    return score * boost
```

**Position Bias Correction**:
```python
def calibrate_scores(scores, positions):
    """
    Users click top items more (position bias)
    Calibrate to account for this
    """
    position_weights = [
        1.0,   # Position 1
        0.85,  # Position 2
        0.7,   # Position 3
        0.6,   # Position 4
        0.5,   # Position 5+
    ]
    
    calibrated = []
    for score, pos in zip(scores, positions):
        weight = position_weights[min(pos, 4)]
        calibrated.append(score / weight)
    
    return calibrated
```

### 6. Feature Store

**User Features** (Redis):
```
Key: user_features:{user_id}
Value: {
  "watch_history": ["movie_1", "movie_2", ...],  // Last 100
  "genre_preferences": {
    "Action": 0.35,
    "Drama": 0.25,
    "Comedy": 0.20,
    "Thriller": 0.15,
    "Sci-Fi": 0.05
  },
  "avg_watch_time": 3600,  // seconds
  "completion_rate": 0.75,  // 75%
  "preferred_watch_times": [18, 19, 20, 21],  // hours
  "last_active": "2024-01-15T10:00:00Z",
  "engagement_score": 0.82,
  "embedding": [0.12, -0.34, ...],  // 128-dim
  "demographics": {
    "age_group": "25-34",
    "location": "US-CA",
    "language": "en"
  }
}

TTL: 1 hour
Update: Real-time on interaction
```

**Content Features** (Redis):
```
Key: content_features:{content_id}
Value: {
  "title": "Inception",
  "genres": ["Sci-Fi", "Thriller"],
  "cast": ["Leonardo DiCaprio", "Ellen Page", ...],
  "director": "Christopher Nolan",
  "year": 2010,
  "duration": 148,
  "rating": "PG-13",
  "language": "en",
  "popularity_score": 0.92,  // Time-decayed
  "trending_score": 0.88,
  "avg_rating": 4.7,
  "watch_count_7d": 1500000,
  "completion_rate": 0.82,
  "embedding": [0.45, -0.12, ...],  // 512-dim
  "release_date": "2010-07-16"
}

TTL: 24 hours
Update: On metadata change, daily popularity refresh
```

**Real-time Feature Updates**:
```
User watches content:
1. Kafka event: {user_id, content_id, watch_duration}
2. Flink processes:
   - Update watch_history (append, keep last 100)
   - Update genre_preferences (moving average)
   - Recalculate engagement_score
   - Update user_embedding (incremental)
3. Write to Redis (< 1 second)
4. Invalidate recommendation cache
```

### 7. Trending Service

**Algorithm**: Time-decayed view count
```python
def calculate_trending_score(content_id, time_window="24h"):
    """
    Score based on recent views with exponential decay
    """
    current_time = now()
    decay_factor = 0.5  # Half-life = 12 hours
    
    views = get_views(content_id, time_window)
    
    score = 0
    for view in views:
        time_diff_hours = (current_time - view.timestamp).hours
        decay = exp(-decay_factor * time_diff_hours / 12)
        score += decay
    
    # Normalize by content age (newer content needs fewer views)
    days_since_release = (current_time - content.release_date).days
    if days_since_release < 30:
        score *= 1.5  # Boost new content
    
    return score

# Update every 15 minutes
```

**Regional Trending**:
```
Compute trending per region:
- Country level: US, UK, IN, BR, etc.
- Group smaller countries by region

Store in Redis:
Key: trending:{region}:{time_window}
Value: [
  {"content_id": "movie_1", "score": 0.95},
  {"content_id": "movie_2", "score": 0.89},
  ...
]

Top-50 per region
```

### 8. A/B Testing Framework

**Variant Assignment**:
```python
def assign_variant(user_id, experiment_id):
    """
    Consistent hash-based assignment
    """
    hash_value = hash(f"{user_id}:{experiment_id}")
    bucket = hash_value % 100  # 0-99
    
    # Example: 3 variants with 33/33/34 split
    if bucket < 33:
        return "variant_A"
    elif bucket < 66:
        return "variant_B"
    else:
        return "variant_C"

# Same user always gets same variant for consistency
```

**Metrics Collection**:
```
For each recommendation shown:
1. Log to Kafka:
   {
     "user_id": "user_123",
     "experiment_id": "cf_vs_hybrid_v2",
     "variant": "variant_A",
     "content_ids": ["movie_1", "movie_2", ...],
     "timestamp": "2024-01-15T10:00:00Z"
   }

2. Track user actions:
   - Click: Which items clicked
   - Watch: Watch duration
   - Rating: If rated
   
3. Aggregate metrics:
   - CTR: Clicks / Impressions
   - Watch rate: Watches / Impressions
   - Avg watch time: Total watch time / Watches
   - Engagement: Complex score

4. Statistical significance test:
   - Chi-square test for CTR
   - T-test for watch time
   - Confidence level: 95%
   - Minimum sample: 10K users per variant
```

---

## Database Design

### 1. User Database (PostgreSQL)

**Schema**:
```sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    last_active TIMESTAMP,
    subscription_tier VARCHAR(20),  -- basic, standard, premium
    country_code CHAR(2),
    language VARCHAR(10),
    timezone VARCHAR(50),
    age_group VARCHAR(10),  -- 18-24, 25-34, 35-44, 45+
    INDEX idx_last_active (last_active),
    INDEX idx_country (country_code)
);

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(user_id),
    favorite_genres TEXT[],  -- Array of genres
    disliked_genres TEXT[],
    preferred_content_types TEXT[],  -- movie, series, documentary
    mature_content_allowed BOOLEAN DEFAULT false,
    auto_play_enabled BOOLEAN DEFAULT true,
    notification_enabled BOOLEAN DEFAULT true,
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE user_profiles (
    profile_id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(user_id),
    profile_name VARCHAR(100),
    is_kids_profile BOOLEAN DEFAULT false,
    avatar_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_user_profiles (user_id)
);
```

### 2. Content Database (PostgreSQL)

**Schema**:
```sql
CREATE TABLE content (
    content_id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content_type VARCHAR(20),  -- movie, series, documentary
    description TEXT,
    release_year INTEGER,
    duration_minutes INTEGER,
    rating VARCHAR(10),  -- G, PG, PG-13, R, etc.
    language VARCHAR(10),
    country VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    INDEX idx_content_type (content_type),
    INDEX idx_release_year (release_year),
    INDEX idx_language (language)
);

CREATE TABLE content_genres (
    content_id UUID REFERENCES content(content_id),
    genre VARCHAR(50),
    PRIMARY KEY (content_id, genre),
    INDEX idx_genre (genre)
);

CREATE TABLE content_cast (
    content_id UUID REFERENCES content(content_id),
    person_name VARCHAR(100),
    role VARCHAR(50),  -- actor, director, producer
    character_name VARCHAR(100),
    display_order INTEGER,
    PRIMARY KEY (content_id, person_name, role),
    INDEX idx_person (person_name)
);

CREATE TABLE content_metadata (
    content_id UUID PRIMARY KEY REFERENCES content(content_id),
    thumbnail_url VARCHAR(500),
    trailer_url VARCHAR(500),
    backdrop_url VARCHAR(500),
    imdb_id VARCHAR(20),
    tmdb_id INTEGER,
    keywords TEXT[],
    awards TEXT[]
);

-- Materialized view for fast content search
CREATE MATERIALIZED VIEW content_search AS
SELECT 
    c.content_id,
    c.title,
    c.content_type,
    c.release_year,
    c.language,
    array_agg(DISTINCT g.genre) as genres,
    array_agg(DISTINCT cc.person_name) FILTER (WHERE cc.role = 'actor') as cast,
    array_agg(DISTINCT cc.person_name) FILTER (WHERE cc.role = 'director') as directors
FROM content c
LEFT JOIN content_genres g ON c.content_id = g.content_id
LEFT JOIN content_cast cc ON c.content_id = cc.content_id
GROUP BY c.content_id, c.title, c.content_type, c.release_year, c.language;

CREATE INDEX idx_content_search_title ON content_search USING gin(to_tsvector('english', title));
```

### 3. Interaction Database (Cassandra)

**Schema**:
```cql
-- Watch history by user
CREATE TABLE user_watch_history (
    user_id uuid,
    watched_at timestamp,
    content_id uuid,
    watch_duration int,  -- seconds
    completion_percentage float,
    device_type text,
    session_id uuid,
    PRIMARY KEY ((user_id), watched_at, content_id)
) WITH CLUSTERING ORDER BY (watched_at DESC, content_id ASC)
  AND default_time_to_live = 7776000;  -- 90 days

-- Content views (for trending)
CREATE TABLE content_views (
    content_id uuid,
    view_date date,
    view_hour int,
    user_id uuid,
    country_code text,
    watch_duration int,
    PRIMARY KEY ((content_id, view_date), view_hour, user_id)
) WITH default_time_to_live = 2592000;  -- 30 days

-- User ratings
CREATE TABLE user_ratings (
    user_id uuid,
    rated_at timestamp,
    content_id uuid,
    rating float,  -- 1.0 to 5.0
    PRIMARY KEY ((user_id), rated_at, content_id)
) WITH CLUSTERING ORDER BY (rated_at DESC)
  AND default_time_to_live = 7776000;  -- 90 days

-- Content ratings (aggregated)
CREATE TABLE content_ratings_agg (
    content_id uuid PRIMARY KEY,
    avg_rating float,
    rating_count int,
    rating_distribution map<int, int>,  -- {5: 1000, 4: 500, ...}
    last_updated timestamp
);

-- User search history
CREATE TABLE user_searches (
    user_id uuid,
    searched_at timestamp,
    query text,
    results_clicked set<uuid>,  -- content_ids clicked
    PRIMARY KEY ((user_id), searched_at)
) WITH CLUSTERING ORDER BY (searched_at DESC)
  AND default_time_to_live = 7776000;  -- 90 days

-- Continue watching (partially watched)
CREATE TABLE continue_watching (
    user_id uuid,
    content_id uuid,
    last_watched_at timestamp,
    watch_progress_seconds int,
    total_duration_seconds int,
    PRIMARY KEY ((user_id), last_watched_at, content_id)
) WITH CLUSTERING ORDER BY (last_watched_at DESC);
```

**Partitioning Strategy**:
```
user_watch_history: 
- Partition by user_id
- All user's history co-located
- Efficient queries: "Last 100 watches for user"

content_views:
- Partition by (content_id, view_date)
- Daily partitions per content
- Efficient trending calculation
- Old partitions automatically expire (TTL)

Replication Factor: 3
Consistency Level: QUORUM (write), LOCAL_ONE (read)
```

### 4. Cache Layer (Redis)

**Data Structures**:

```redis
# User embeddings
SET user_emb:{user_id} <binary_vector>
EXPIRE user_emb:{user_id} 3600

# User features
HSET user_feat:{user_id} 
  watch_history "[movie_1,movie_2,...]"
  genres '{"Action": 0.35, "Drama": 0.25}'
  engagement_score 0.82
EXPIRE user_feat:{user_id} 3600

# Content embeddings
SET content_emb:{content_id} <binary_vector>
EXPIRE content_emb:{content_id} 86400

# Recommendation cache
SET rec:{user_id}:homepage <json_recommendations>
EXPIRE rec:{user_id}:homepage 1800

# Trending cache
ZADD trending:global:24h <score> movie_1 <score> movie_2 ...
EXPIRE trending:global:24h 900

# Item-item similarity
ZADD similar:{content_id} <score> item_1 <score> item_2 ...
EXPIRE similar:{content_id} 86400

# Session tracking
SETEX session:{session_id} 7200 '{"user_id": "...", "device": "..."}'

# A/B test assignments
HSET ab_test:{experiment_id} user_123 "variant_A"
EXPIRE ab_test:{experiment_id} 2592000  # 30 days
```

### 5. Analytics Database (ClickHouse)

**Schema**:
```sql
CREATE TABLE recommendation_events (
    event_id UUID,
    user_id UUID,
    session_id UUID,
    content_id UUID,
    event_type String,  -- impression, click, watch, rate
    algorithm String,  -- cf, content, hybrid
    position Int16,
    score Float32,
    timestamp DateTime,
    device_type String,
    country String,
    experiment_id String,
    variant String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, user_id, content_id);

CREATE TABLE user_metrics_daily (
    date Date,
    user_id UUID,
    sessions_count Int16,
    recommendations_shown Int32,
    recommendations_clicked Int32,
    items_watched Int16,
    total_watch_time Int32,
    avg_rating Float32
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, user_id);

CREATE TABLE content_metrics_hourly (
    hour DateTime,
    content_id UUID,
    impressions Int32,
    clicks Int32,
    watches Int32,
    total_watch_time Int32,
    avg_completion_rate Float32
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, content_id);

-- Materialized views for real-time aggregation
CREATE MATERIALIZED VIEW recommendation_metrics_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, algorithm, variant)
AS SELECT
    toStartOfHour(timestamp) as hour,
    algorithm,
    variant,
    countIf(event_type = 'impression') as impressions,
    countIf(event_type = 'click') as clicks,
    countIf(event_type = 'watch') as watches,
    avg(position) as avg_position
FROM recommendation_events
GROUP BY hour, algorithm, variant;
```

**Query Examples**:
```sql
-- CTR by algorithm (last 24 hours)
SELECT 
    algorithm,
    clicks / impressions as ctr
FROM recommendation_metrics_mv
WHERE hour >= now() - INTERVAL 24 HOUR
GROUP BY algorithm;

-- Top performing content (last 7 days)
SELECT 
    content_id,
    sum(watches) as total_watches,
    avg(avg_completion_rate) as completion_rate
FROM content_metrics_hourly
WHERE hour >= now() - INTERVAL 7 DAY
GROUP BY content_id
ORDER BY total_watches DESC
LIMIT 20;
```

---

## Recommendation Generation Flow

### End-to-End Flow: Homepage Recommendations

```
User opens app → Request homepage recommendations

┌─────────────────────────────────────────────────┐
│ T=0ms: Client sends request                     │
└─────────────────────────────────────────────────┘

GET /api/v1/recommendations/homepage/user_alice
Headers:
  Authorization: Bearer <jwt_token>
  Device-Type: mobile_ios

┌─────────────────────────────────────────────────┐
│ T=5ms: API Gateway                              │
└─────────────────────────────────────────────────┘

1. Validate JWT token
2. Extract user_id: alice_123
3. Check rate limit (Redis):
   INCR ratelimit:alice_123:minute
   → 45 requests this minute (< 1000 limit) ✓
4. Route to Recommendation Service

┌─────────────────────────────────────────────────┐
│ T=10ms: Recommendation Service                  │
└─────────────────────────────────────────────────┘

1. Check cache (Redis):
   GET rec:alice_123:homepage:mobile_ios
   → CACHE MISS (expired or not generated)

2. Fetch user features (Redis):
   HGETALL user_feat:alice_123
   → {
       watch_history: [movie_1, movie_2, ...],
       genres: {Action: 0.35, Drama: 0.25},
       engagement_score: 0.82
     }

3. Generate sections in parallel:

┌─────────────────────────────────────────────────┐
│ T=20ms: Parallel Section Generation             │
└─────────────────────────────────────────────────┘

Section 1: "Top Picks for You" (Personalized)
├─ Candidate Generation (Collaborative Filtering)
│  1. Fetch user embedding from Redis (1ms)
│  2. FAISS nearest neighbor search (10ms)
│  3. Return top-500 similar items
│
├─ Candidate Scoring (Hybrid Model)
│  1. Batch inference: 500 items (20ms)
│  2. Get predicted scores
│
├─ Ranking (LTR Model)
│  1. Combine CF score + Hybrid score + features
│  2. LightGBM ranking (5ms)
│  3. Select top-50
│
└─ Re-ranking (Diversity + Business Rules)
   1. Apply MMR for diversity (2ms)
   2. Apply freshness boost
   3. Filter out recently watched
   4. Final top-20

Section 2: "Trending Now" (Popular)
├─ Fetch from cache (Redis): ZRANGE trending:US:24h
│  → Pre-computed, updated every 15 min
│  → Instant retrieval (< 1ms)
│
└─ Personalize order:
   - Boost genres user likes
   - Return top-20

Section 3: "Continue Watching"
├─ Query Cassandra:
   SELECT * FROM continue_watching 
   WHERE user_id = alice_123
   ORDER BY last_watched_at DESC
   LIMIT 10
│
└─ Sort by recency, return all

Section 4: "Because You Watched [Title]"
├─ Get last watched: movie_X
├─ Fetch similar items (Redis):
   ZRANGE similar:movie_X 0 19
│
