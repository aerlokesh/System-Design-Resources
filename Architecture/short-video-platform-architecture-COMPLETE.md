# Short Video Platform - Complete System Architecture (TikTok/Reels Style)

*Complete architecture document with detailed workflows, data flows, and component specifications*

---

## Table of Contents
1. [Full System Architecture](#full-system-architecture)
2. [Write Path - Video Upload](#write-path-post-apiv1videosupload)
3. [Read Path - Feed Generation](#read-path-get-apiv1feedfor-you)
4. [Recommendation System Workflow](#recommendation-system-workflow)
5. [Fan-out Strategy](#fan-out-strategy-detail)
6. [Video Processing Pipeline](#video-processing-pipeline-detail)
7. [Social Interaction Flow](#social-interaction-flow-likecommentshare)
8. [Data Models](#data-models)
9. [Redis Cache Structures](#redis-cache-structures)
10. [Database Schemas](#database-schemas)
11. [Component Configuration](#component-configuration-details)
12. [Capacity & Performance](#capacity--performance-summary)
13. [Regional Distribution](#regional-distribution)
14. [Technology Stack](#technology-stack)
15. [Monitoring Dashboard](#monitoring-dashboard)
16. [Architecture Comparison](#comparison-original-vs-improved)
17. [API Endpoints Reference](#quick-reference---api-endpoints)

---

## Full System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                    │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │   Mobile     │    │     Web      │    │   Smart TV   │                 │
│  │   (iOS/      │    │   Browser    │    │   Apps       │                 │
│  │   Android)   │    │              │    │              │                 │
│  └──────────────┘    └──────────────┘    └──────────────┘                 │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CDN LAYER (CloudFront)                                │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  • Video Streaming (HLS/DASH) - Adaptive Bitrate (360p-4K)        │    │
│  │  • Static Assets (Thumbnails, Profile Pictures, Icons)             │    │
│  │  • Edge Caching (200+ POPs Globally)                               │    │
│  │  • Multi-tier Cache: Edge → Regional → Origin                      │    │
│  │  • Performance: <100ms video start time, 95% cache hit             │    │
│  │  • Handles: 60% of total traffic                                   │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  GLOBAL LOAD BALANCER (Route 53)                             │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  • Geo-based DNS Routing (Latency-based)                           │    │
│  │  • Health Checks (30s intervals)                                   │    │
│  │  • Automatic Failover (<30s)                                       │    │
│  │  • Multi-Region Support (4 regions)                                │    │
│  │  • Weighted routing for A/B testing                                │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY LAYER                                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  • Rate Limiting (Token Bucket Algorithm)                          │    │
│  │    - Anonymous: 100 req/min                                        │    │
│  │    - Authenticated: 1000 req/min                                   │    │
│  │    - Upload: 10 videos/day/user                                    │    │
│  │  • Authentication (JWT Validation)                                 │    │
│  │  • Request/Response Transformation                                 │    │
│  │  • API Versioning & Routing                                        │    │
│  │  • Protocol Translation (REST/GraphQL/WebSocket)                   │    │
│  │  • Request deduplication                                           │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LOAD BALANCER (ALB)                           │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  • Health Checks (Target Groups)                                   │    │
│  │  • SSL/TLS Termination (TLS 1.3)                                   │    │
│  │  • Traffic Distribution (Least Outstanding Requests)               │    │
│  │  • Sticky Sessions Support (Cookie-based)                          │    │
│  │  • WebSocket support for live features                             │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└───┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬──┘
    │         │         │         │         │         │         │         │
    ▼         ▼         ▼         ▼         ▼         ▼         ▼         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SERVICE LAYER                                      │
│                     (Microservices Architecture)                             │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │     Auth     │  │     User     │  │    Video     │  │     Feed     │  │
│  │   Service    │  │   Service    │  │   Service    │  │   Service    │  │
│  │              │  │              │  │              │  │              │  │
│  │ • JWT Auth   │  │ • Profiles   │  │ • Metadata   │  │ • For You    │  │
│  │ • OAuth 2.0  │  │ • Settings   │  │ • Upload     │  │ • Following  │  │
│  │ • MFA        │  │ • Followers  │  │ • Status     │  │ • Trending   │  │
│  │              │  │ • Bio        │  │ • Transcode  │  │ • Personalize│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │    Social    │  │    Search    │  │Recommendation│  │ Notification │  │
│  │   Service    │  │   Service    │  │   Service    │  │   Service    │  │
│  │              │  │              │  │              │  │              │  │
│  │ • Likes      │  │ • Videos     │  │ • ML Models  │  │ • Push       │  │
│  │ • Comments   │  │ • Users      │  │ • Ranking    │  │ • In-App     │  │
│  │ • Shares     │  │ • Tags       │  │ • Features   │  │ • Email      │  │
│  │ • Follows    │  │ • Hashtags   │  │ • A/B Test   │  │ • SMS        │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                                              │
│  ┌──────────────┐                                                           │
│  │  Analytics   │                                                           │
│  │   Service    │                                                           │
│  │              │                                                           │
│  │ • Metrics    │                                                           │
│  │ • Events     │                                                           │
│  │ • Reports    │                                                           │
│  │ • Insights   │                                                           │
│  └──────────────┘                                                           │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CACHING LAYER                                      │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    Redis Cluster (Sharded)                         │    │
│  │                                                                     │    │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐         │    │
│  │  │ Feed Cache    │  │ Session Cache │  │ Metadata Cache│         │    │
│  │  │               │  │               │  │               │         │    │
│  │  │ • Sorted Sets │  │ • User        │  │ • Video Info  │         │    │
│  │  │ • User Feeds  │  │   Sessions    │  │ • Counts      │         │    │
│  │  │ • TTL: 30min  │  │ • TTL: 24hr   │  │ • TTL: 1hr    │         │    │
│  │  └───────────────┘  └───────────────┘  └───────────────┘         │    │
│  │                                                                     │    │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐         │    │
│  │  │ Trending      │  │ Social Graph  │  │ Video Cache   │         │    │
│  │  │ Videos Cache  │  │ Cache         │  │               │         │    │
│  │  │               │  │               │  │ • Hot videos  │         │    │
│  │  │ • Hot Videos  │  │ • Followers   │  │ • Thumbnails  │         │    │
│  │  │ • TTL: 5min   │  │ • Following   │  │ • TTL: 10min  │         │    │
│  │  └───────────────┘  └───────────────┘  └───────────────┘         │    │
│  │                                                                     │    │
│  │  Nodes: 500 (r6g.8xlarge - 32GB each)                              │    │
│  │  Total Memory: 16 TB                                               │    │
│  │  Hit Rate: Feed 85%, Video 70%, Trending 95%                       │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MESSAGE QUEUE LAYER (Kafka)                             │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │              Kafka Cluster (Multi-partition, Replicated)             │  │
│  │                                                                       │  │
│  │  Topics:                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────┐     │  │
│  │  │  • video.uploaded         - New video upload events        │     │  │
│  │  │  • video.processed        - Video processing complete      │     │  │
│  │  │  • user.interaction       - Likes, comments, shares        │     │  │
│  │  │  • feed.update            - Feed refresh events            │     │  │
│  │  │  • notification.trigger   - Push notification events       │     │  │
│  │  │  • analytics.event        - User behavior events           │     │  │
│  │  └────────────────────────────────────────────────────────────┘     │  │
│  │                                                                       │  │
│  │  Configuration:                                                       │  │
│  │  • Brokers: 100 (m5.4xlarge)                                         │  │
│  │  • Partitions: 200+ per topic                                        │  │
│  │  • Replication Factor: 3                                             │  │
│  │  • Retention: 7 days                                                 │  │
│  │  • Throughput: 2M messages/sec                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└───┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────┘
    │          │          │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼          ▼          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        WORKER/PROCESSOR LAYER                                │
│                     (Event-Driven Background Jobs)                           │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │    Video     │  │     Feed     │  │    Social    │  │ Notification │  │
│  │  Processing  │  │  Populator   │  │    Graph     │  │   Workers    │  │
│  │   Workers    │  │   Workers    │  │   Workers    │  │              │  │
│  │              │  │              │  │              │  │              │  │
│  │ Count: 40K   │  │ Count: 200   │  │ Count: 100   │  │ Count: 150   │  │
│  │              │  │              │  │              │  │              │  │
│  │ • Transcode  │  │ • Push Model │  │ • Follow     │  │ • FCM/APNS   │  │
│  │ • Thumbnail  │  │ • Pull Model │  │ • Unfollow   │  │ • Email      │  │
│  │ • Moderation │  │ • Redis Sync │  │ • Update     │  │ • SMS        │  │
│  │ • Watermark  │  │ • Merge      │  │ • Notify     │  │ • WebSocket  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐                                        │
│  │  Analytics   │  │   Content    │                                        │
│  │   Workers    │  │  Moderation  │                                        │
│  │              │  │   Workers    │                                        │
│  │              │  │              │                                        │
│  │ Count: 50    │  │ Count: 100   │                                        │
│  │              │  │              │                                        │
│  │ • Aggregate  │  │ • AI Check   │                                        │
│  │ • Report     │  │ • Human Rev  │                                        │
│  │ • ETL        │  │ • Appeal     │                                        │
│  │ • ML Train   │  │ • Tag        │                                        │
│  └──────────────┘  └──────────────┘                                        │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DATA STORAGE LAYER                                    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                   DynamoDB (Primary Database)                      │    │
│  │                                                                     │    │
│  │  Tables: Users, Videos, Interactions                               │    │
│  │  Capacity: 10M WCU, 6M RCU, 365 TB storage                         │    │
│  │  Latency: 10-20ms                                                  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │              PostgreSQL (Relational - Sharded)                     │    │
│  │                                                                     │    │
│  │  Tables: Followers, Comments, Reports                              │    │
│  │  Config: 100 shards, 600 instances total                           │    │
│  │  Latency: 20-50ms                                                  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                 Cassandra (Time-Series)                            │    │
│  │                                                                     │    │
│  │  Tables: ViewHistory, UserInteractions, ActivityLogs               │    │
│  │  Config: Replication 3x, 90-day retention                          │    │
│  │  Latency: 10-30ms                                                  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │               Elasticsearch (Search)                               │    │
│  │                                                                     │    │
│  │  Indices: Videos, Users, Tags                                      │    │
│  │  Config: 50-node cluster, Real-time indexing                       │    │
│  │  Latency: 50-100ms                                                 │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                  S3 (Object Storage - 2 EB)                        │    │
│  │                                                                     │    │
│  │  Buckets: videos-raw, videos-processed, thumbnails, avatars        │    │
│  │  Lifecycle: Standard → IA → Glacier → Deep Archive                 │    │
│  │  Features: Cross-region replication, Encryption                    │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ML & ANALYTICS LAYER                                  │
│                                                                              │
│  ┌─ Feature Store (Real-time + Batch)                                ─┐    │
│  │  User, Video, Context, Interaction Features                        │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─ ML Model Serving (TensorFlow / SageMaker)                        ─┐    │
│  │  Recommendation, Content Moderation, Ranking Models                │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─ Data Warehouse (Redshift / BigQuery)                             ─┐    │
│  │  Historical analytics, BI reports, ML training data                │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      OBSERVABILITY & MONITORING                              │
│                                                                              │
│  • Logging: ELK Stack (Fluentd → Kafka → ES → Kibana)                       │
│  • Metrics: Prometheus + Grafana                                            │
│  • Tracing: Jaeger / AWS X-Ray                                              │
│  • Alerting: PagerDuty / Opsgenie                                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Write Path (POST /api/v1/videos/upload)

```
┌──────────────────────────────────────────────────────────────────┐
│                     SYNCHRONOUS PATH                              │
│                  (User-Facing Response)                           │
│                     Target: <500ms                                │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   1. API Gateway    │
                    │   - Authentication  │
                    │   - Rate Limiting   │
                    │   Time: 50ms        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   2. Video Service  │
                    │   - Validation      │
                    │   - Check quota     │
                    │   Time: 30ms        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌──────────────────────────┐
                    │  3. Generate Pre-signed  │
                    │     S3 URL               │
                    │  - 1 hour expiry         │
                    │  Time: 20ms              │
                    └──────────┬───────────────┘
                               │
                               ▼
                    ┌──────────────────────────┐
                    │  4. Create Video Record  │
                    │  - DynamoDB write        │
                    │  - Status: "uploading"   │
                    │  Time: 30ms              │
                    └──────────┬───────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  5. Return URL      │
                    │  - Upload URL       │
                    │  - Video ID         │
                    │  Total: 130ms       │
                    └─────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│              CLIENT DIRECT UPLOAD (Out of Band)                   │
│                   Not counted in API latency                      │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  6. Client Uploads  │
                    │  - Direct to S3     │
                    │  - Multipart upload │
                    │  - Resume support   │
                    │  Time: 2-60 seconds │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  7. S3 Notification │
                    │  - Lambda trigger   │
                    │  - SNS publish      │
                    │  Time: <1 second    │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  8. Kafka Publish   │
                    │  - video.uploaded   │
                    │  Time: 10ms         │
                    └─────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                    ASYNCHRONOUS PATH                              │
│              (Background Processing)                              │
│                   Eventually Consistent                           │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  Kafka Topic        │
                    │  "video.uploaded"   │
                    │  200 partitions     │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┬─────────────┐
              │                │                │             │
              ▼                ▼                ▼             ▼
     ┌───────────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
     │ Video Process │  │  Moderation  │  │Analytics │  │  Notify  │
     │   Worker      │  │   Worker     │  │  Worker  │  │  Worker  │
     └───────┬───────┘  └──────┬───────┘  └────┬─────┘  └────┬─────┘
             │                 │               │             │
             ▼                 ▼               ▼             ▼

Video Processing:        Moderation:          Analytics:         Notification:
1. Download from S3    1. AI Content Check   1. Track upload    1. Get followers
2. Transcode:          2. Violence detect    2. Update stats    2. Send push
   - 360p, 720p,       3. NSFW detect        3. ML features     3. In-app notif
     1080p, 4K         4. Hate speech        4. User behavior   
3. Thumbnails          5. Flag if needed     
4. Preview sprite      6. Human review       
5. Extract metadata    7. Update status      
6. Upload to S3        
7. Update DynamoDB     
8. Emit "processed"    
9. Time: 2-5 min       
```

---

## Read Path (GET /api/v1/feed/for-you)

```
┌──────────────────────────────────────────────────────────────────┐
│                  FEED READ PATH                                   │
│              Target Latency: <500ms                               │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  1. API Gateway     │
                    │  - Auth check       │
                    │  Time: 20ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  2. Feed Service    │
                    │  - Route request    │
                    │  Time: 10ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  3. Feed Cache (Redis)      │
                    │  ZREVRANGE feed:{user_id}   │
                    │  Returns: [video_ids]       │
                    │  Time: 15ms                 │
                    │  Hit Rate: 85%              │
                    └──────────┬──────────────────┘
                               │
                               ▼
              ┌────────────────┴────────────────┐
              │                                 │
              ▼                                 ▼
   ┌─────────────────────┐         ┌─────────────────────┐
   │  4a. Cache Hit      │         │  4b. Cache Miss     │
   │  (85% requests)     │         │  (15% requests)     │
   │  - Use cached IDs   │         │  - Call ML Recom    │
   │  Time: 0ms          │         │  Time: 200ms        │
   └──────────┬──────────┘         └──────────┬──────────┘
              │                                │
              └────────────────┬───────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  5. Batch Get Videos│
                    │  - DynamoDB batch   │
                    │  - 20 videos        │
                    │  Time: 50ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  6. Hydrate Details │
                    │  - User info        │
                    │  - Like status      │
                    │  Time: 30ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  7. Return Feed     │
                    │  - 20 videos        │
                    │  Total: 125ms (hit) │
                    │  or 325ms (miss)    │
                    └─────────────────────┘
```

---

## Recommendation System Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│              RECOMMENDATION GENERATION                           │
│          (Called on Feed Cache Miss)
