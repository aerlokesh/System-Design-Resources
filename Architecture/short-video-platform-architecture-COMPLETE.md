# Short Video Platform - Complete System Architecture (TikTok/Reels Style)

## Full System Architecture (Improved Design)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                    │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │  Mobile App  │    │  Web Client  │    │  Smart TV    │                 │
│  │  (iOS/       │    │  (React)     │    │  Apps        │                 │
│  │  Android)    │    │              │    │              │                 │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                 │
└─────────┼──────────────────┼──────────────────┼────────────────────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       CDN LAYER (CloudFront)                                 │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Edge Locations: 200+ globally                                       │  │
│  │  Content: HLS/DASH video streams, thumbnails, profile images        │  │
│  │  Performance: <100ms video start, 95% hit rate                       │  │
│  │  Handles: 60% of total traffic                                       │  │
│  │  Adaptive Bitrate: 360p, 720p, 1080p, 4K                            │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LOAD BALANCER (ALB)                           │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Multi-AZ Deployment | Health Checks (30s) | SSL Termination         │  │
│  │  Geo-routing | Auto-failover | 99.99% availability                   │  │
│  │  WebSocket support | Sticky sessions                                 │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────┬────────────────────────────────┬───────────────────────────┘
                 │                                │
         ┌───────┴────────┐              ┌────────┴───────┐
         │                │              │                │
         ▼                ▼              ▼                ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│  API Gateway   │  │  API Gateway   │  │  API Gateway   │  │  API Gateway   │
│  Cluster 1     │  │  Cluster 2     │  │  Cluster 3     │  │  Cluster 4     │
│  (US-EAST)     │  │  (US-WEST)     │  │  (EU-WEST)     │  │  (AP-SE)       │
└────────┬───────┘  └────────┬───────┘  └────────┬───────┘  └────────┬───────┘
         │                   │                   │                   │
         └───────────────────┴───────────────────┴───────────────────┘
                                      │
┌─────────────────────────────────────┼─────────────────────────────────────┐
│                        SERVICE LAYER                                       │
│                                                                            │
│     ┌────────────────────────────────┴────────────────────────────┐       │
│     │                                                              │       │
│     ▼                    ▼                    ▼                   ▼       │
│ ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌─────────┐   │
│ │    VIDEO     │   │     FEED     │   │    SOCIAL    │   │  AUTH   │   │
│ │   SERVICE    │   │   SERVICE    │   │   SERVICE    │   │ SERVICE │   │
│ │              │   │              │   │              │   │         │   │
│ │  Handles:    │   │  Handles:    │   │  Handles:    │   │ Handles:│   │
│ │  - Upload    │   │  - For You   │   │  - Like      │   │ - JWT   │   │
│ │  - Metadata  │   │  - Following │   │  - Comment   │   │ - OAuth │   │
│ │  - Status    │   │  - Trending  │   │  - Share     │   │ - MFA   │   │
│ │  - Delete    │   │  - Cache     │   │  - Follow    │   │         │   │
│ └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └────┬────┘   │
│                                                                            │
│     ▼                    ▼                    ▼                   ▼       │
│ ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌─────────┐   │
│ │    SEARCH    │   │RECOMMENDATION│   │ NOTIFICATION │   │ANALYTICS│   │
│ │   SERVICE    │   │   SERVICE    │   │   SERVICE    │   │ SERVICE │   │
│ │              │   │              │   │              │   │         │   │
│ │  Handles:    │   │  Handles:    │   │  Handles:    │   │ Handles:│   │
│ │  - Videos    │   │  - ML Model  │   │  - Push      │   │ - Events│   │
│ │  - Users     │   │  - Ranking   │   │  - In-App    │   │ - Stats │   │
│ │  - Hashtags  │   │  - Features  │   │  - Email     │   │ - Report│   │
│ └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └────┬────┘   │
└────────┼──────────────────┼──────────────────┼────────────────┼─────────┘
         │                  │                  │                │
         │                  │                  │                │
         ▼                  ▼                  ▼                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MESSAGE QUEUE (Apache Kafka)                          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Topics:                                                             │  │
│  │  • video.uploaded (200 partitions)                                   │  │
│  │  • video.processed (200 partitions)                                  │  │
│  │  • user.interaction (100 partitions)                                 │  │
│  │  • feed.update (100 partitions)                                      │  │
│  │  • notification.trigger (50 partitions)                              │  │
│  │                                                                       │  │
│  │  Configuration:                                                      │  │
│  │  • 100 brokers (m5.4xlarge)                                          │  │
│  │  • Replication factor: 3                                             │  │
│  │  • Retention: 7 days                                                 │  │
│  │  • Throughput: 2M messages/sec                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             │ Consumed by Worker Fleet
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       WORKER FLEET                                           │
│                                                                              │
│  ┌───────────────────┐  ┌──────────────────┐  ┌──────────────────────┐    │
│  │ VIDEO PROCESSING  │  │ FEED POPULATOR   │  │ CONTENT MODERATION   │    │
│  │    WORKERS        │  │    WORKERS       │  │     WORKERS          │    │
│  │                   │  │                  │  │                      │    │
│  │  Count: 40,000    │  │  Count: 200      │  │  Count: 100          │    │
│  │  Capacity:        │  │  Function:       │  │  Function:           │    │
│  │  1.2K videos/sec  │  │  - Push to feeds │  │  - AI moderation     │    │
│  │                   │  │  - Pull merge    │  │  - Flag content      │    │
│  │  Logic:           │  │  - Redis sync    │  │  - Human review      │    │
│  │  - Transcode      │  │                  │  │                      │    │
│  │  - Thumbnail      │  │                  │  │                      │    │
│  │  - Metadata       │  │                  │  │                      │    │
│  │  - Watermark      │  │                  │  │                      │    │
│  └─────────┬─────────┘  └─────────┬────────┘  └──────────┬───────────┘    │
│                                                                              │
│  ┌───────────────────┐  ┌──────────────────┐                               │
│  │    ANALYTICS      │  │   NOTIFICATION   │                               │
│  │     WORKERS       │  │     WORKERS      │                               │
│  │                   │  │                  │                               │
│  │  Count: 50        │  │  Count: 150      │                               │
│  │  Function:        │  │  Function:       │                               │
│  │  - ETL jobs       │  │  - FCM/APNS      │                               │
│  │  - Aggregation    │  │  - Email/SMS     │                               │
│  │  - ML features    │  │  - WebSocket     │                               │
│  └─────────┬─────────┘  └─────────┬────────┘                               │
└────────────┼──────────────────────┼──────────────────────────────────────────┘
             │                      │
             │                      │
    ┌────────┴────────┐     ┌───────┴──────┐
    │                 │     │              │
    ▼                 ▼     ▼              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STORAGE LAYER                                        │
│                                                                              │
│  ┌────────────────────┐  ┌──────────────────┐  ┌──────────────────────┐   │
│  │    DYNAMODB        │  │   POSTGRESQL     │  │   CASSANDRA          │   │
│  │    (Videos)        │  │   (Social Graph) │  │   (Time-Series)      │   │
│  │                    │  │                  │  │                      │   │
│  │  Partition: vid_id │  │  Master DB       │  │  Watch history       │   │
│  │  GSI: user_id      │  │  + 600 Replicas  │  │  Interactions        │   │
│  │  GSI: category     │  │                  │  │  Activity logs       │   │
│  │                    │  │  Sharded by      │  │                      │   │
│  │  Capacity:         │  │  user_id (100)   │  │  Replication: 3x     │   │
│  │  10M WCU           │  │                  │  │  Retention: 90 days  │   │
│  │  6M RCU            │  │  Handles:        │  │                      │   │
│  │                    │  │  - Followers     │  │  Latency: 10-30ms    │   │
│  │  Latency: 10-20ms  │  │  - Comments      │  │                      │   │
│  └────────────────────┘  │                  │  └──────────────────────┘   │
│                          │  Latency: 20-50ms│                              │
│  ┌────────────────────┐  └──────────────────┘  ┌──────────────────────┐   │
│  │  ELASTICSEARCH     │                         │    S3 STORAGE        │   │
│  │  (Search Index)    │                         │    (Videos)          │   │
│  │                    │                         │                      │   │
│  │  50-node cluster   │                         │  ~2 EB total         │   │
│  │  Sharded by doc_id │                         │  Multi-region        │   │
│  │  Replication: 2x   │                         │  Lifecycle tiers     │   │
│  │                    │                         │  AES-256 encrypted   │   │
│  │  Latency: 50-100ms │                         │                      │   │
│  └────────────────────┘                         └──────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           CACHE LAYER                                        │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐     │
│  │  FEED CACHE      │  │  VIDEO CACHE     │  │  TRENDING CACHE      │     │
│  │  (Redis Cluster) │  │  (Redis Cluster) │  │  (Redis Cluster)     │     │
│  │                  │  │                  │  │                      │     │
│  │  Nodes: 200      │  │  Nodes: 150      │  │  Nodes: 150          │     │
│  │  Memory: 6.4TB   │  │  Memory: 4.8TB   │  │  Memory: 4.8TB       │     │
│  │                  │  │                  │  │                      │     │
│  │  Key Pattern:    │  │  Key Pattern:    │  │  Key Pattern:        │     │
│  │  feed:{user_id}  │  │  video:{id}      │  │  trending:{region}   │     │
│  │                  │  │                  │  │                      │     │
│  │  Type: ZSET      │  │  Type: Hash      │  │  Type: ZSET          │     │
│  │  Size: 100 IDs   │  │  Data: Full video│  │  Data: Score+video   │     │
│  │  TTL: 30 minutes │  │  TTL: 1 hour     │  │  TTL: 5 minutes      │     │
│  │                  │  │                  │  │                      │     │
│  │  Hit Rate: 85%   │  │  Hit Rate: 70%   │  │  Hit Rate: 95%       │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘     │
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

Video Processing Worker:        Moderation Worker:       Analytics Worker:    Notify Worker:
1. Download from S3           1. AI Content Check      1. Track upload     1. Get followers
2. Transcode (parallel):      2. Violence detect       2. Update stats     2. Filter by prefs
   - 360p (600 Kbps)          3. NSFW detect           3. ML features      3. Batch (1000)
   - 720p (2.5 Mbps)          4. Hate speech           4. User behavior    4. Send via FCM/APNS
   - 1080p (5 Mbps)           5. Copyright check       
   - 4K (20 Mbps)             6. Flag if needed        
3. Generate thumbnails        7. Human review queue    
   (5 different)              8. Update status         
4. Create preview sprite      
5. Extract metadata           
6. Upload to S3               
7. Update DynamoDB            
8. Emit "video.processed"     
   to Kafka                   
9. Time: 2-5 minutes          
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
                    │  - Cache lookup     │
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
                    │  0 19 WITHSCORES            │
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
   │  (85% of requests)  │         │  (15% of requests)  │
   │  - Use cached IDs   │         │  - Call Recommend   │
   │  Time: 0ms          │         │  - Generate fresh   │
   └──────────┬──────────┘         │  Time: 200ms        │
              │                     └──────────┬──────────┘
              │                                │
              └────────────────┬───────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  5. Batch Get Videos│
                    │  - DynamoDB batch   │
                    │  - Get 20 videos    │
                    │  Time: 50ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  6. Hydrate Details │
                    │  - User info        │
                    │  - Like status      │
                    │  - Comment counts   │
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

## Trending Path (GET /api/v1/feed/trending)

```
┌──────────────────────────────────────────────────────────────────┐
│                TRENDING READ PATH                                 │
│              Target Latency: <100ms                               │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  1. API Gateway     │
                    │  - Public endpoint  │
                    │  Time: 10ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  2. API Gateway Cache       │
                    │  - Cached response          │
                    │  - TTL: 1 minute            │
                    │  - Hit rate: 95%            │
                    │  Time: 5ms                  │
                    └──────────┬──────────────────┘
                               │ (On miss)
                               ▼
                    ┌─────────────────────────────┐
                    │  3. Feed Service            │
                    │  - Get from Redis           │
                    │  Time: 10ms                 │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  4. Redis ZSET              │
                    │  ZREVRANGE trending:global  │
                    │  0 19 WITHSCORES            │
                    │  Returns: Top 20 videos     │
                    │  Time: 5ms                  │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  5. Batch Get Video Details │
                    │  - DynamoDB batch           │
                    │  Time: 20ms                 │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  6. Return Trending         │
                    │  - Videos with scores       │
                    │  Total: 50ms (cached)       │
                    │  or 70ms (cache miss)       │
                    └─────────────────────────────┘

Background Processing (Trending Worker):
┌──────────────────────────────────────────────────────────────────┐
│  Every video view:                                                │
│  1. Extract video_id, category, region                           │
│  2. ZINCRBY trending:global {view_weight} {video_id}             │
│  3. ZINCRBY trending:{region} {view_weight} {video_id}           │
│  4. ZINCRBY trending:{category} {view_weight} {video_id}         │
│                                                                   │
│  Score Calculation:                                              │
│  Score = (views × 1000) + (likes × 500) + (shares × 2000)       │
│          - (age_hours × 50)                                      │
│                                                                   │
│  Every 5 minutes:                                                │
│  - Decay scores by 50 points                                     │
│  - Remove entries with score < 100                               │
│  - Recalculate top 100                                           │
└──────────────────────────────────────────────────────────────────┘
```

---

## Fan-out Strategy Detail

```
┌─────────────────────────────────────────────────────────────────────┐
│                    HYBRID FAN-OUT STRATEGY                          │
└─────────────────────────────────────────────────────────────────────┘

                        User Uploads Video
                              │
                              ▼
                   ┌────────────────────┐
                   │  Check Follower    │
                   │  Count             │
                   └──────────┬─────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ < 1K Followers  │  │ 1K-10K          │  │ > 10K           │
│                 │  │ Followers       │  │ Followers       │
│ FAN-OUT ON      │  │                 │  │                 │
│ WRITE           │  │ SELECTIVE       │  │ FAN-OUT ON      │
│                 │  │ FAN-OUT         │  │ READ            │
│ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │
│ │ Push to ALL │ │  │ │ Push to     │ │  │ │ DON'T Push  │ │
│ │ followers'  │ │  │ │ ACTIVE      │ │  │ │ to followers│ │
│ │ Redis feeds │ │  │ │ followers   │ │  │ │             │ │
│ │             │ │  │ │ (last 7 days│ │  │ │ Pull on     │ │
│ │ Immediate   │ │  │ │ activity)   │ │  │ │ demand      │ │
│ │ delivery    │ │  │ │             │ │  │ │             │ │
│ │             │ │  │ │ ~500 users  │ │  │ │ Query DB    │ │
│ │ Cost: Low   │ │  │ │ per creator │ │  │ │ at read time│ │
│ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │
│                 │  │                 │  │                 │
│ Benefits:       │  │ Benefits:       │  │ Benefits:       │
│ • Fast delivery │  │ • Balance cost  │  │ • Lower cost    │
│ • Fresh feeds   │  │ • Reach active  │  │ • On-demand     │
│ • Low load      │  │ • Efficient     │  │ • Scalable      │
└─────────────────┘  └─────────────────┘  └─────────────────┘

Active User Detection:
┌─────────────────────────────────────────────────────────────────┐
│  Criteria for "Active" followers:                               │
│  - Opened app in last 7 days                                    │
│  - Watched at least 1 video in last 3 days                      │
│  - Engagement score > 5 (likes, comments, shares)               │
│                                                                  │
│  Stored in: Redis Bitmap                                        │
│  Key: active_users:{date}                                       │
│  Check: GETBIT active_users:2024-01-15 {user_id}               │
│  Update: Every hour via batch job                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Models

### DynamoDB - Videos Table

```
Table: videos
Partition Key: video_id (String)
GSI-1: user_id-created_at-index
GSI-2: category-trending_score-index
GSI-3: status-processed_at-index

Attributes:
{
  "video_id": "vid_abc123",
  "user_id": "usr_xyz789",
  "created_at": 1703145600000,
  "updated_at": 1703145600000,
  "status": "active",  // uploading, processing, active, deleted
  
  // Video Details
  "title": "Amazing dance moves",
  "description": "Check out my new choreography!",
  "category": "dance",
  "hashtags": ["dance", "tutorial", "trending"],
  "duration_sec": 45,
  "width": 1080,
  "height": 1920,
  
  // URLs
  "s3_key": "videos/2024/01/vid_abc123/master.mp4",
  "thumbnail_url": "https://cdn.../thumb.jpg",
  "preview_sprite_url": "https://cdn.../sprite.jpg",
  
  // Encoded Versions
  "transcoded_urls": {
    "360p": "https://cdn.../360p/playlist.m3u8",
    "720p": "https://cdn.../720p/playlist.m3u8",
    "1080p": "https://cdn.../1080p/playlist.m3u8",
    "4k": "https://cdn.../4k/playlist.m3u8"
  },
  
  // Statistics (updated in batches)
  "view_count": 1500000,
  "like_count": 125000,
  "comment_count": 8500,
  "share_count": 45000,
  "save_count": 12000,
  
  // Trending Score (for ranking)
  "trending_score": 187500,  // calculated score
  "trending_region": ["US", "CA", "UK"],
  
  // Moderation
  "moderation_status": "approved",  // pending, approved, rejected
  "moderation_flags": [],
  "moderation_reviewed_at": 1703145700000,
  
  // Music & Effects
  "music_id": "music_123",
  "effects": ["filter_vintage", "transition_fade"],
  
  // Privacy
  "visibility": "public",  // public, friends, private
  "allow_comments": true,
  "allow_duet": true,
  "allow_stitch": true,
  
  // Location
  "location": {
    "lat": 37.7749,
    "lng": -122.4194,
    "name": "San Francisco, CA"
  },
  
  // Processing Metadata
  "processing_started_at": 1703145610000,
  "processing_completed_at": 1703145650000,
  "processing_duration_sec": 40
}
```

### PostgreSQL - Social Graph

```sql
-- Users Table
CREATE TABLE users (
    user_id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(100),
    bio TEXT,
    avatar_url VARCHAR(500),
    verified BOOLEAN DEFAULT false,
    follower_count INTEGER DEFAULT 0,
    following_count INTEGER DEFAULT 0,
    video_count INTEGER DEFAULT 0,
    total_likes INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_username (username),
    INDEX idx_email (email)
);

-- Followers Table (Sharded by user_id hash)
CREATE TABLE followers (
    id BIGSERIAL PRIMARY KEY,
    follower_user_id VARCHAR(36) NOT NULL,
    following_user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(follower_user_id, following_user_id),
    INDEX idx_follower (follower_user_id),
    INDEX idx_following (following_user_id)
);
-- Partitioned by HASH(follower_user_id) into 100 shards

-- Likes Table
CREATE TABLE likes (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    video_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, video_id),
    INDEX idx_user_video (user_id, video_id),
    INDEX idx_video_created (video_id, created_at)
);

-- Comments Table
CREATE TABLE comments (
    comment_id VARCHAR(36) PRIMARY KEY,
    video_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    parent_comment_id VARCHAR(36),  -- for replies
    content TEXT NOT NULL,
    like_count INTEGER DEFAULT 0,
    reply_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP,
    INDEX idx_video_created (video_id, created_at),
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_parent (parent_comment_id)
);
```

### Cassandra - Time-Series Data

```
-- Watch History
CREATE TABLE watch_history (
    user_id text,
    watched_at timestamp,
    video_id text,
    watch_duration_sec int,
    completed boolean,
    PRIMARY KEY (user_id, watched_at, video_id)
) WITH CLUSTERING ORDER BY (watched_at DESC);

-- Interactions
CREATE TABLE user_interactions (
    user_id text,
    interaction_time timestamp,
    interaction_type text,  // view, like, comment, share
    video_id text,
    metadata text,  // JSON blob
    PRIMARY KEY (user_id, interaction_time, interaction_type)
) WITH CLUSTERING ORDER BY (interaction_time DESC);

-- Activity Logs
CREATE TABLE activity_logs (
    user_id text,
    log_date date,
    hour int,
    activity_type text,
    count counter,
    PRIMARY KEY ((user_id, log_date), hour, activity_type)
);
```

### Redis - Cache Structures

```
# Feed Cache (ZSET)
Key: feed:{user_id}
Type: Sorted Set
Value: video_id
Score: timestamp (for chronological order)
TTL: 30 minutes
Size: 100 videos

Example:
ZADD feed:usr_xyz789 1703145600000 vid_abc123
ZADD feed:usr_xyz789 1703145650000 vid_def456
ZREVRANGE feed:usr_xyz789 0 19  # Get latest 20

# Video Cache (Hash)
Key: video:{video_id}
Type: Hash
Fields: All video metadata
TTL: 1 hour

Example:
HSET video:vid_abc123 title "Amazing dance"
HSET video:vid_abc123 view_count 1500000
HGETALL video:vid_abc123

# Trending Cache (ZSET)
Key: trending:{region}
Type: Sorted Set
Value: video_id
Score: trending_score
TTL: 5 minutes
Size: 100 videos

Example:
ZADD trending:US 187500 vid_abc123
ZREVRANGE trending:US 0 19 WITHSCORES

# User Session Cache
Key: session:{session_id}
Type: String (JSON)
TTL: 24 hours

# Rate Limiter
Key: ratelimit:{user_id}:{endpoint}
Type: String
TTL: 60 seconds
```

---

## API Specifications

### Upload Video
```
POST /api/v1/videos/upload

Request Headers:
Authorization: Bearer {jwt_token}
Content-Type: application/json

Request Body:
{
  "title": "My awesome video",
  "description": "Description here",
  "category": "dance",
  "hashtags": ["dance", "tutorial"],
  "duration_sec": 45,
  "file_size_bytes": 52428800,  // 50MB
  "width": 1080,
  "height": 1920,
  "visibility": "public",
  "music_id": "music_123",
  "location": {
    "lat": 37.7749,
    "lng": -122.4194
  }
}

Response (200 OK):
{
  "video_id": "vid_abc123",
  "upload_url": "https://s3.../presigned-url",
  "upload_expires_at": 1703149200000,
  "status": "uploading"
}

Rate Limit: 10 uploads per hour per user
```

### Get Feed (For You)
```
GET /api/v1/feed/for-you?page=0&limit=20

Request Headers:
Authorization: Bearer {jwt_token}

Query Parameters:
- page: integer (default: 0)
- limit: integer (default: 20, max: 50)

Response (200 OK):
{
  "videos": [
    {
      "video_id": "vid_abc123",
      "user": {
        "user_id": "usr_xyz789",
        "username": "johndoe",
        "display_name": "John Doe",
        "avatar_url": "https://cdn.../avatar.jpg",
        "verified": true
      },
      "title": "Amazing dance moves",
      "description": "Check out my choreography!",
      "category": "dance",
      "hashtags": ["dance", "tutorial"],
      "created_at": 1703145600000,
      "urls": {
        "360p": "https://cdn.../360p/playlist.m3u8",
        "720p": "https://cdn.../720p/playlist.m3u8",
        "1080p": "https://cdn.../1080p/playlist.m3u8"
      },
      "thumbnail_url": "https://cdn.../thumb.jpg",
      "duration_sec": 45,
      "stats": {
        "views": 1500000,
        "likes": 125000,
        "comments": 8500,
        "shares": 45000
      },
      "user_interaction": {
        "liked": false,
        "saved": false
      },
      "music": {
        "music_id": "music_123",
        "title": "Trending Song",
        "artist": "Popular Artist"
      }
    }
  ],
  "next_page": 1,
  "has_more": true
}

Rate Limit: 100 requests per minute per user
Cache: 30 minutes
```

### Like Video
```
POST /api/v1/videos/{video_id}/like

Request Headers:
Authorization: Bearer {jwt_token}

Response (200 OK):
{
  "success": true,
  "like_count": 125001
}

Rate Limit: 500 likes per hour per user
```

### Post Comment
```
POST /api/v1/videos/{video_id}/comments

Request Headers:
Authorization: Bearer {jwt_token}
Content-Type: application/json

Request Body:
{
  "content": "Great video!",
  "parent_comment_id": null  // null for top-level, ID for replies
}

Response (201 Created):
{
  "comment_id": "cmt_abc123",
  "content": "Great video!",
  "user": {
    "user_id": "usr_xyz789",
    "username": "johndoe",
    "avatar_url": "https://cdn.../avatar.jpg"
  },
  "created_at": 1703145600000,
  "like_count": 0,
  "reply_count": 0
}

Rate Limit: 20 comments per minute per user
```

---

## Scaling Numbers & Capacity Planning

### Traffic Estimates
```
Daily Active Users (DAU): 100 million
Monthly Active Users (MAU): 500 million
Peak DAU: 150 million (holidays, events)

Usage Patterns:
- Avg session duration: 52 minutes
- Avg sessions per day: 8
- Avg videos watched per session: 150
- Total daily video views: 120 billion

Creator Stats:
- Daily uploads: 30 million videos
- Avg video size: 50 MB
- Daily upload volume: 1.5 PB
- Storage growth: 45 PB/month

Engagement:
- Like rate: 8% of views
- Comment rate: 0.7% of views
- Share rate: 3% of views
- Follow rate from video: 0.5%
```

### Infrastructure Scale
```
API Gateway:
- 500 instances (m5.2xlarge)
- 4M requests per second peak
- 99.99% availability

Application Servers:
- Video Service: 2,000 instances
- Feed Service: 3,000 instances
- Social Service: 1,500 instances
- Search Service: 500 instances
- Total: 10,000+ application servers

Databases:
- DynamoDB: 10M WCU, 6M RCU
- PostgreSQL: 1 master + 600 read replicas
- Cassandra: 500-node cluster
- Elasticsearch: 50-node cluster

Cache:
- Redis nodes: 500 (r6g.8xlarge)
- Total memory: 16 TB
- Hit rate: 85-95%
- QPS: 10M reads per second

Storage:
- S3: 2 EB total
- 30M videos uploaded daily
- 1.5 PB daily ingestion
- Standard tier: 1.5 EB
- Infrequent access: 400 PB
- Glacier: 100 PB

CDN:
- CloudFront: 200+ edge locations
- Bandwidth: 500 Tbps peak
- Cache hit rate: 95%
- Serves 60% of total traffic

Message Queue:
- Kafka: 100 brokers
- Partitions: 10,000
- Throughput: 2M messages/sec
- Storage: 100 TB (7-day retention)

Workers:
- Video processing: 40,000 workers
- Feed populator: 200 workers
- Content moderation: 100 workers
- Analytics: 50 workers
- Notifications: 150 workers
```

### Cost Breakdown (Monthly)
```
Compute:
- EC2 instances: $2.5M
- Auto-scaling groups: $800K
- Lambda functions: $300K
Subtotal: $3.6M

Storage:
- S3 storage: $3M
- S3 requests: $500K
- EBS volumes: $400K
Subtotal: $3.9M

Database:
- DynamoDB: $2M
- RDS PostgreSQL: $1.2M
- Cassandra: $800K
- Elasticsearch: $500K
Subtotal: $4.5M

Cache:
- ElastiCache Redis: $1.5M

CDN:
- CloudFront: $4M
- Data transfer: $2M
Subtotal: $6M

Networking:
- Load balancers: $300K
- NAT gateways: $200K
- VPN/Direct Connect: $150K
Subtotal: $650K

Message Queue:
- MSK (Kafka): $600K

Other:
- Monitoring/Logging: $200K
- ML/AI services: $500K
- Backup/Disaster recovery: $300K
Subtotal: $1M

Total Monthly: ~$21.2M
Total Yearly: ~$254M
```

---

## Trade-offs & Design Decisions

### 1. Eventual Consistency
**Decision:** Accept eventual consistency for non-critical operations

**Pros:**
- Higher throughput
- Better availability
- Lower latency
- Horizontal scalability

**Cons:**
- View counts may be slightly inaccurate
- Follower feeds take time to update
- Temporary inconsistencies

**Mitigation:**
- Batch updates every 5 minutes
- Real-time for critical ops (likes, comments)
- Idempotency keys for retries

### 2. Hybrid Fan-out
**Decision:** Use different strategies based on follower count

**Pros:**
- Cost-effective for mega-creators
- Fast for small creators
- Balanced approach

**Cons:**
- More complex implementation
- Threshold tuning needed
- Mixed consistency models

**Mitigation:**
- Clear tier definitions
- Monitoring per tier
- Gradual tier transitions

### 3. CDN-Heavy Architecture
**Decision:** 95% traffic through CDN

**Pros:**
- Low latency globally
- Reduced origin load
- Cost-effective bandwidth
- Built-in DDoS protection

**Cons:**
- Cache invalidation complexity
- CDN costs at scale
- Regional availability dependencies

**Mitigation:**
- Multi-CDN strategy
- Origin shields
- Smart cache keys
- Graceful degradation

### 4. Denormalization
**Decision:** Duplicate data across stores

**Pros:**
- Faster reads
- Service independence
- Simpler queries

**Cons:**
- Storage overhead
- Sync complexity
- Potential inconsistencies

**Mitigation:**
- Event-driven updates
- Reconciliation jobs
- TTL-based expiry

### 5. Recommendation Pull Model
**Decision:** Generate recommendations on-demand

**Pros:**
- Fresh recommendations
- User context aware
- Lower storage costs

**Cons:**
- Higher latency on cold start
- More compute intensive
- Cache dependency

**Mitigation:**
- Aggressive caching
- Pre-computation for active users
- Fallback to trending

---

## Monitoring & Observability

### Key Metrics

```
Application Metrics:
┌──────────────────────────────────────────────────────┐
│ Metric                 │ Target    │ Alert Threshold │
├──────────────────────────────────────────────────────┤
│ Video upload success   │ >99.5%    │ <99%           │
│ API latency p50        │ <100ms    │ >150ms         │
│ API latency p99        │ <500ms    │ >1s            │
│ Feed generation time   │ <300ms    │ >500ms         │
│ Video processing time  │ <3min     │ >5min          │
│ CDN cache hit rate     │ >95%      │ <90%           │
│ Error rate             │ <0.1%     │ >0.5%          │
└──────────────────────────────────────────────────────┘

Infrastructure Metrics:
┌──────────────────────────────────────────────────────┐
│ Metric                 │ Target    │ Alert Threshold │
├──────────────────────────────────────────────────────┤
│ CPU utilization        │ <70%      │ >85%           │
│ Memory utilization     │ <80%      │ >90%           │
│ Disk I/O              │ <60%      │ >80%           │
│ Network bandwidth      │ <70%      │ >85%           │
│ Cache hit rate         │ >85%      │ <75%           │
│ Database connections   │ <80%      │ >90%           │
│ Queue depth            │ <1000     │ >5000          │
└──────────────────────────────────────────────────────┘

Business Metrics:
┌──────────────────────────────────────────────────────┐
│ Metric                 │ Target    │ Alert Threshold │
├──────────────────────────────────────────────────────┤
│ DAU                    │ 100M      │ <90M           │
│ Avg session duration   │ 52min     │ <45min         │
│ Video completion rate  │ >60%      │ <50%           │
│ Upload success rate    │ >99%      │ <98%           │
│ Moderation SLA         │ <10min    │ >15min         │
│ Customer support load  │ <10K/day  │ >15K/day       │
└──────────────────────────────────────────────────────┘
```

### Logging Strategy

```
Application Logs:
- Format: JSON structured logs
- Level: INFO in production, DEBUG in staging
- Storage: CloudWatch Logs → S3
- Retention: 30 days hot, 1 year cold
- Volume: 500 GB/day

Access Logs:
- Source: ALB, API Gateway, CDN
- Format: Common Log Format
- Storage: S3
- Retention: 90 days
- Volume: 2 TB/day

Error Logs:
- Level: ERROR, CRITICAL
- Alerting: PagerDuty integration
- Analysis: ELK Stack
- Sampling: 100% of errors

Performance Logs:
- Metrics: Latency, throughput
- Tool: Datadog APM
- Traces: 1% sampling
- Real-user monitoring
```

### Alerting

```
Critical Alerts (PagerDuty):
- Service down (any component)
- Error rate >1%
- Latency p99 >2s
- Database connection pool exhausted
- CDN origin errors >0.5%
- Video processing backlog >1 hour
- Disk space >90%

Warning Alerts (Slack):
- Error rate >0.5%
- Latency p99 >1s
- Cache hit rate <80%
- Queue depth >2000
- CPU >85%
- Memory >90%

Info Alerts (Email):
- Deployment completed
- Scaling event triggered
- Traffic spike detected
- Cost anomaly detected
```

---

## Disaster Recovery & High Availability

### Multi-Region Strategy

```
Primary Region: US-EAST-1
Secondary Region: US-WEST-2
DR Region: EU-WEST-1

┌─────────────────────────────────────────────────────┐
│                  ACTIVE-ACTIVE                      │
│           (US-EAST-1 & US-WEST-2)                   │
│                                                      │
│  • Both regions serve traffic                       │
│  • Route53 latency-based routing                    │
│  • Cross-region replication (async)                 │
│  • RTO: 0 (continuous)                              │
│  • RPO: <5 minutes                                  │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│                  WARM STANDBY                        │
│                 (EU-WEST-1)                          │
│                                                      │
│  • Minimal instances running                        │
│  • Database replicas active                         │
│  • S3 replication enabled                           │
│  • Auto-scaling ready                               │
│  • RTO: <15 minutes                                 │
│  • RPO: <15 minutes                                 │
└─────────────────────────────────────────────────────┘
```

### Backup Strategy

```
Databases:
- Continuous backup (DynamoDB PITR)
- Snapshot every 6 hours (RDS)
- Cross-region replication
- Retention: 30 days

Videos (S3):
- Versioning enabled
- Cross-region replication (async)
- Lifecycle policies:
  * Standard → IA after 30 days
  * IA → Glacier after 90 days
- Retention: Indefinite

Configuration:
- Version control in Git
- Automated backups
- Infrastructure as Code
- Secrets in AWS Secrets Manager

Recovery Testing:
- Monthly DR drills
- Quarterly regional failover
- Annual full-scale test
```

---

## Security Measures

### Authentication & Authorization

```
Authentication:
- JWT tokens (RS256)
- Token expiry: 15 minutes (access), 7 days (refresh)
- MFA for sensitive operations
- OAuth 2.0 for third-party apps
- Rate limiting per user

Authorization:
- Role-based access control (RBAC)
- Resource-level permissions
- API key for service-to-service
- Principle of least privilege

Data Protection:
- TLS 1.3 for all traffic
- Encryption at rest (AES-256)
- KMS for key management
- Field-level encryption (PII)
- Data masking in logs
```

### Content Security

```
Upload Validation:
- File type whitelist (MP4, MOV, AVI)
- Size limit: 500 MB
- Duration limit: 10 minutes
- Virus scanning (ClamAV)
- Metadata stripping

Content Moderation:
- AI-powered detection
  * NSFW content
  * Violence
  * Hate speech
  * Copyright infringement
- Human review for flagged content
- User reporting system
- Appeal process

DDoS Protection:
- CloudFront with AWS Shield
- Rate limiting at multiple layers
- Bot detection
- Geographic restrictions
- IP reputation scoring
```

---

## Future Enhancements

### Phase 1 (Next 6 Months)
- Live streaming support
- AR filters and effects
- Advanced video editing in-app
- Collaborative videos (duets, stitches)
- Creator analytics dashboard

### Phase 2 (6-12 Months)
- AI-powered auto-captions
- Multi-language support
- Virtual gifts and monetization
- Creator marketplace
- Advanced recommendation (GPT-based)

### Phase 3 (12-18 Months)
- VR/360-degree video support
- Blockchain integration for NFTs
- Decentralized content delivery
- AI video generation
- Real-time translation

---

## Conclusion

This short video platform architecture is designed to handle massive scale (100M+ DAU) with:

**Key Strengths:**
- Sub-500ms latency for critical paths
- 99.99% availability SLA
- Horizontal scalability
- Cost-effective CDN-heavy design
- Robust content moderation
- Global presence

**Scalability Path:**
- CDN handles 60% of traffic
- Stateless services for easy scaling
- Sharded databases
- Event-driven architecture
- Caching at multiple layers

**Operational Excellence:**
- Comprehensive monitoring
- Automated alerting
- Multi-region DR
- Infrastructure as Code
- Regular DR testing

This design balances performance, cost, and complexity while providing excellent user experience globally.
