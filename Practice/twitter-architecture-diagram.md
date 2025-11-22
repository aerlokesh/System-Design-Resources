# Twitter System Design - Complete Architecture Diagram

## Full System Architecture (Improved Design)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              USER LAYER                                      │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │  Web Client  │    │  iOS App     │    │ Android App  │                 │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                 │
└─────────┼──────────────────┼──────────────────┼────────────────────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       CDN LAYER (CloudFront)                                 │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Edge Locations: 300+ globally                                       │  │
│  │  Content: Static assets, images, videos, profile pictures           │  │
│  │  Performance: <20ms latency, 90% hit rate                            │  │
│  │  Handles: 40% of total traffic                                       │  │
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
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────┬────────────────────────────────┬───────────────────────────┘
                 │                                │
         ┌───────┴────────┐              ┌────────┴───────┐
         │                │              │                │
         ▼                ▼              ▼                ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│  API Gateway   │  │  API Gateway   │  │  API Gateway   │  │  API Gateway   │
│  Cluster 1     │  │  Cluster 2     │  │  Cluster 3     │  │  Cluster 4     │
│  (US-EAST-1a)  │  │  (US-EAST-1b)  │  │  (US-WEST)     │  │  (EU-WEST)     │
└────────┬───────┘  └────────┬───────┘  └────────┬───────┘  └────────┬───────┘
         │                   │                   │                   │
         └───────────────────┴───────────────────┴───────────────────┘
                                      │
┌─────────────────────────────────────┼─────────────────────────────────────┐
│                        SERVICE LAYER                                       │
│                                                                            │
│     ┌────────────────────────────────┴────────────────────────────┐       │
│     │                                                              │       │
│     ▼                          ▼                        ▼         │       │
│ ┌──────────────┐      ┌──────────────┐      ┌──────────────┐    │       │
│ │    TWEET     │      │     FEED     │      │   TRENDING   │    │       │
│ │   SERVICE    │      │   SERVICE    │      │   SERVICE    │    │       │
│ │              │      │              │      │              │    │       │
│ │  Handles:    │      │  Handles:    │      │  Handles:    │    │       │
│ │  - Create    │      │  - Get feed  │      │  - Calculate │    │       │
│ │  - Delete    │      │  - Cache     │      │  - Update    │    │       │
│ │  - Edit      │      │  - Hydrate   │      │  - Scores    │    │       │
│ │  - Validate  │      │  - Merge     │      │  - Decay     │    │       │
│ └──────┬───────┘      └──────┬───────┘      └──────┬───────┘    │       │
└────────┼──────────────────────┼──────────────────────┼────────────┘       │
         │                      │                      │
         │                      │                      │
         ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MESSAGE QUEUE (Apache Kafka)                          │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Topics:                                                             │  │
│  │  • tweet_created (100 partitions)                                    │  │
│  │  • tweet_deleted (50 partitions)                                     │  │
│  │  • like_added (50 partitions)                                        │  │
│  │  • retweet_created (50 partitions)                                   │  │
│  │                                                                       │  │
│  │  Configuration:                                                      │  │
│  │  • 10 brokers (m5.2xlarge)                                           │  │
│  │  • Replication factor: 3                                             │  │
│  │  • Retention: 7 days                                                 │  │
│  │  • Throughput: 500K messages/sec                                     │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             │ Consumed by Worker Fleet
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       WORKER FLEET                                           │
│                                                                              │
│  ┌───────────────────┐  ┌──────────────────┐  ┌──────────────────────┐    │
│  │  FAN-OUT WORKERS  │  │ SEARCH WORKERS   │  │  TRENDING WORKERS    │    │
│  │                   │  │                  │  │                      │    │
│  │  Count: 200       │  │  Count: 50       │  │  Count: 30           │    │
│  │  Capacity:        │  │  Function:       │  │  Function:           │    │
│  │  20K tweets/sec   │  │  - Index tweets  │  │  - Extract hashtags  │    │
│  │                   │  │  - Update ES     │  │  - Update scores     │    │
│  │  Logic:           │  │  - Handle search │  │  - Decay old trends  │    │
│  │  - Get followers  │  │                  │  │  - Regional trending │    │
│  │  - Push to feeds  │  │                  │  │                      │    │
│  │  - Batch writes   │  │                  │  │                      │    │
│  │  - Handle celebs  │  │                  │  │                      │    │
│  └─────────┬─────────┘  └─────────┬────────┘  └──────────┬───────────┘    │
└────────────┼──────────────────────┼──────────────────────┼─────────────────┘
             │                      │                      │
             │                      │                      │
    ┌────────┴────────┐     ┌───────┴──────┐      ┌──────┴────────┐
    │                 │     │              │      │               │
    ▼                 ▼     ▼              ▼      ▼               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STORAGE LAYER                                        │
│                                                                              │
│  ┌────────────────────┐  ┌──────────────────┐  ┌──────────────────────┐   │
│  │    DYNAMODB        │  │   POSTGRESQL     │  │   ELASTICSEARCH      │   │
│  │    (Tweets)        │  │   (Users/Graph)  │  │   (Search Index)     │   │
│  │                    │  │                  │  │                      │   │
│  │  Partition: user_id│  │  Master DB       │  │  5-node cluster      │   │
│  │  Sort: timestamp   │  │  + 10 Replicas   │  │  Sharded by doc_id   │   │
│  │  GSI: tweet_id     │  │                  │  │  Replication: 2x     │   │
│  │  GSI: hashtag      │  │  Sharded by      │  │                      │   │
│  │                    │  │  user_id (hash)  │  │  Full-text search    │   │
│  │  Capacity:         │  │                  │  │  Hashtag queries     │   │
│  │  20K WCU           │  │  Handles:        │  │  User search         │   │
│  │  200K RCU          │  │  - User profiles │  │                      │   │
│  │                    │  │  - Relationships │  │  Latency: 50-100ms   │   │
│  │  Latency: 10-20ms  │  │  - Auth data     │  │                      │   │
│  └────────────────────┘  │                  │  │                      │   │
│                          │  Latency: 20-50ms│  │                      │   │
│                          └──────────────────┘  └──────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           CACHE LAYER                                        │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐     │
│  │  FEED CACHE      │  │  TWEET CACHE     │  │  TRENDING CACHE      │     │
│  │  (Redis Cluster) │  │  (Redis Cluster) │  │  (Redis Cluster)     │     │
│  │                  │  │                  │  │                      │     │
│  │  Nodes: 10       │  │  Nodes: 5        │  │  Nodes: 3            │     │
│  │  Memory: 320GB   │  │  Memory: 160GB   │  │  Memory: 48GB        │     │
│  │                  │  │                  │  │                      │     │
│  │  Key Pattern:    │  │  Key Pattern:    │  │  Key Pattern:        │     │
│  │  feed:{user_id}  │  │  tweet:{id}      │  │  trending:global     │     │
│  │                  │  │                  │  │  trending:us         │     │
│  │  Type: List      │  │  Type: Hash      │  │  Type: Sorted Set    │     │
│  │  Size: 1000 IDs  │  │  Data: Full tweet│  │  Data: Score+tag     │     │
│  │  TTL: 5 minutes  │  │  TTL: 10 minutes │  │  TTL: 1 hour         │     │
│  │                  │  │                  │  │                      │     │
│  │  Hit Rate: 85%   │  │  Hit Rate: 70%   │  │  Hit Rate: 95%       │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Write Path (POST /api/v1/tweet)

```
┌──────────────────────────────────────────────────────────────────┐
│                     SYNCHRONOUS PATH                              │
│                  (User-Facing Response)                           │
│                     Target: <50ms                                 │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   1. API Gateway    │
                    │   - Authentication  │
                    │   - Rate Limiting   │
                    │   Time: 10ms        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   2. Tweet Service  │
                    │   - Validation      │
                    │   - Spam check      │
                    │   Time: 5ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   3. DynamoDB Write │
                    │   - Store tweet     │
                    │   - Partition: user │
                    │   Time: 20ms        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  4. Kafka Publish   │
                    │  - Async event      │
                    │  - No wait          │
                    │  Time: 5ms          │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  5. Return Success  │
                    │  - Tweet ID         │
                    │  - 201 Created      │
                    │  Total: 40ms        │
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
                    │  "tweet_created"    │
                    │  100 partitions     │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┬──────────────┐
              │                │                │              │
              ▼                ▼                ▼              ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
     │  Fan-out     │  │   Search     │  │ Trending │  │  Notify  │
     │  Worker      │  │   Worker     │  │  Worker  │  │  Worker  │
     └──────┬───────┘  └──────┬───────┘  └────┬─────┘  └────┬─────┘
            │                 │               │             │
            ▼                 ▼               ▼             ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
     │ Redis Feed   │  │Elasticsearch │  │  Redis   │  │Push Notif│
     │ Cache        │  │ Index Update │  │  ZSORT   │  │ Service  │
     └──────────────┘  └──────────────┘  └──────────┘  └──────────┘
```

---

## Read Path (GET /api/v1/feed)

```
┌──────────────────────────────────────────────────────────────────┐
│                  FEED READ PATH                                   │
│              Target Latency: <50ms                                │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  1. API Gateway     │
                    │  - Auth check       │
                    │  - Cache check      │
                    │  Time: 5ms          │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  2. Feed Service    │
                    │  - Route request    │
                    │  Time: 2ms          │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  3. Feed Cache (Redis)      │
                    │  LRANGE feed:{user_id} 0 99 │
                    │  Returns: [tweet_ids]       │
                    │  Time: 3ms                  │
                    │  Hit Rate: 85%              │
                    └──────────┬──────────────────┘
                               │
                               ▼
              ┌────────────────┴────────────────┐
              │                                 │
              ▼                                 ▼
   ┌─────────────────────┐         ┌─────────────────────┐
   │  4a. Regular Feed   │         │  4b. Celebrity Feed │
   │  (Pre-computed)     │         │  (If follows celebs)│
   │  - Already in cache │         │  ZREVRANGE          │
   │                     │         │  celeb:tweets       │
   └──────────┬──────────┘         └──────────┬──────────┘
              │                                │
              └────────────────┬───────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  5. Merge & Sort    │
                    │  - By timestamp     │
                    │  Time: 5ms          │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  6. Hydrate Details │
                    │  - Batch GET tweets │
                    │  - From DynamoDB    │
                    │  - Add user info    │
                    │  Time: 20ms         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  7. Return Feed     │
                    │  - 100 tweets       │
                    │  Total: 35-50ms     │
                    └─────────────────────┘
```

---

## Trending Path (GET /api/v1/trending)

```
┌──────────────────────────────────────────────────────────────────┐
│                TRENDING READ PATH                                 │
│              Target Latency: <20ms                                │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  1. API Gateway     │
                    │  - Public endpoint  │
                    │  Time: 5ms          │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  2. API Gateway Cache       │
                    │  - Cached response          │
                    │  - TTL: 1 minute            │
                    │  - Hit rate: 95%            │
                    │  Time: 2ms                  │
                    └──────────┬──────────────────┘
                               │ (On miss)
                               ▼
                    ┌─────────────────────────────┐
                    │  3. Trending Service        │
                    │  - Get from Redis           │
                    │  Time: 5ms                  │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  4. Redis ZSORT             │
                    │  ZREVRANGE trending:global  │
                    │  0 9 WITHSCORES             │
                    │  Returns: Top 10 hashtags   │
                    │  Time: 3ms                  │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │  5. Return Trending         │
                    │  - Topics with scores       │
                    │  Total: 15ms (cached)       │
                    │  or 20ms (cache miss)       │
                    └─────────────────────────────┘

Background Processing (Trending Worker):
┌──────────────────────────────────────────────────────────────────┐
│  Every tweet:                                                     │
│  1. Extract hashtags                                             │
│  2. ZINCRBY trending:global {score} {hashtag}                    │
│  3. ZINCRBY trending:{region} {score} {hashtag}                  │
│                                                                   │
│  Score Calculation:                                              │
│  Score = (mentions × 1000) + (velocity × 500) - (age_hours × 10)│
│                                                                   │
│  Every minute:                                                   │
│  - Decay scores by 10 points                                     │
│  - Remove entries with score < 0                                 │
└──────────────────────────────────────────────────────────────────┘
```

---

## Fan-out Strategy Detail

```
┌─────────────────────────────────────────────────────────────────────┐
│                    HYBRID FAN-OUT STRATEGY                          │
└─────────────────────────────────────────────────────────────────────┘

                        User Posts Tweet
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
│ < 1K Followers  │  │ 1K-100K         │  │ > 100K          │
│                 │  │ Followers       │  │ Followers       │
│ FAN-OUT ON      │  │                 │  │                 │
│ WRITE           │  │ SELECTIVE       │  │ FAN-OUT ON      │
│                 │  │ FAN-OUT         │  │ READ            │
│ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │
│ │ Push to ALL │ │  │ │ Push to     │ │  │ │ DON'T Push  │ │
│ │ followers'  │ │  │ │ ACTIVE      │ │  │ │ to followers│ │
│ │ Redis feeds │ │  │ │ followers   │ │  │ │             │ │
│ │             │ │  │ │ (7 days)    │ │  │ │ Cache tweet │ │
│ │ LPUSH       │ │  │ │             │ │  │ │ centrally   │ │
│ │ feed:{fid}  │ │  │ │ Reduce load │ │  │ │             │ │
│ │ {tweet_id}  │ │  │ │ by 80-90%   │ │  │ │ Merge on    │ │
│ │             │ │  │ │             │ │  │ │ read request│ │
│ │ Fast reads  │ │  │ │ Balance     │ │  │ │             │ │
│ │ <5ms        │ │  │ │ write/read  │ │  │ │ Slower reads│ │
│ └─────────────┘ │  │ └─────────────┘ │  │ │ +20ms       │ │
│                 │  │                 │  │ └─────────────┘ │
│ High write cost │  │ Moderate cost   │  │ Low write cost │
│ 90% of users    │  │ 9% of users     │  │ 1% of users    │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

---

## Data Flow Diagrams

### Tweet Creation Data Flow

```
Step 1: User Posts Tweet
┌──────┐
│ User │ POST /api/v1/tweet
└───┬──┘ body: { content, media_urls }
    │
    ▼
┌──────────────────────────────────────┐
│ Load Balancer                        │
│ Routes to nearest API Gateway        │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ API Gateway                          │
│ 1. Verify JWT token                  │
│ 2. Check rate limit (100/hour/user)  │
│ 3. Route to Tweet Service            │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Tweet Service                        │
│ 1. Validate content (280 chars)      │
│ 2. Check spam/abuse                  │
│ 3. Generate tweet_id (UUID)          │
│ 4. Add metadata (timestamp, etc)     │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ DynamoDB (Tweets Table)              │
│ PUT Item:                            │
│ {                                    │
│   partition_key: user_id,            │
│   sort_key: timestamp,               │
│   tweet_id: uuid,                    │
│   content: text,                     │
│   media_urls: [urls],                │
│   hashtags: [tags]                   │
│ }                                    │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Kafka Producer                       │
│ Send to topic: "tweet_created"       │
│ Key: user_id (for partitioning)      │
│ Value: {tweet_id, user_id, data}     │
│ acks: 1 (leader only - fast)         │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Return Response                      │
│ Status: 201 Created                  │
│ Body: { tweet_id, created_at }       │
│ Latency: 40ms                        │
└──────────────────────────────────────┘


Step 2: Async Processing (Workers consume from Kafka)
┌──────────────────────────────────────┐
│ Kafka Topic: tweet_created           │
│ Message available                    │
└───┬──────────────────────────────────┘
    │
    ├──────────────────┬──────────────────┬──────────────────┐
    │                  │                  │                  │
    ▼                  ▼                  ▼                  ▼
┌────────┐      ┌────────┐      ┌────────┐      ┌────────┐
│Fan-out │      │ Search │      │Trending│      │ Notify │
│Worker  │      │ Worker │      │ Worker │      │ Worker │
└───┬────┘      └───┬────┘      └───┬────┘      └───┬────┘
    │               │               │               │
    ▼               ▼               ▼               ▼

Fan-out Logic:                Search Logic:         Trending Logic:      Notify Logic:
1. Get user's              1. Extract text       1. Extract tags      1. Get followers
   follower list           2. Tokenize           2. Update ZSORT      2. Filter by
2. Check follower          3. Index in ES        3. Calculate score      preferences
   count                   4. Update mappings    4. Regional updates  3. Batch send
3. If < 1000:              5. Refresh index      5. Decay old scores  4. Push via FCM
   Push to all                                                           /APNS
4. If > 1000:
   Skip (fan-out
   on read)
5. Batch writes
   (100 at a time)
```

### Feed Read Data Flow

```
Step 1: User Requests Feed
┌──────┐
│ User │ GET /api/v1/feed?userId=123
└───┬──┘
    │
    ▼
┌──────────────────────────────────────┐
│ Load Balancer → API Gateway          │
│ Time: 5ms                            │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Feed Service                         │
│ Route to feed logic                  │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Redis Feed Cache Check               │
│ LRANGE feed:123 0 99                 │
│ Returns: List of tweet IDs           │
└───┬──────────────────────────────────┘
    │
    │ 85% Cache Hit
    │
    ▼
┌──────────────────────────────────────┐
│ Get Tweet Details (Batch)            │
│ DynamoDB BatchGetItem                │
│ Keys: [tweet_ids from cache]         │
│ Time: 20ms                           │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Check for Celebrity Following        │
│ PostgreSQL read replica              │
│ Query: Get celebrity users followed  │
│ Time: 10ms                           │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Get Celebrity Tweets (if any)        │
│ Redis: ZREVRANGE celeb:tweets        │
│ Time: 5ms                            │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Merge & Sort All Tweets              │
│ Application logic                    │
│ Sort by: timestamp DESC              │
│ Time: 5ms                            │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Return Feed to User                  │
│ Status: 200 OK                       │
│ Body: [100 tweets with full data]   │
│ Total Latency: 45-50ms               │
└──────────────────────────────────────┘
```

---

## Component Details

### API Gateway Configuration

```
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY CLUSTER                       │
│                                                              │
│  Instance Type: t3.medium (4 per region)                    │
│  Total Instances: 12 across 3 regions                       │
│                                                              │
│  Features:                                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Authentication:                                     │  │
│  │  - JWT token validation                              │  │
│  │  - OAuth 2.0 support                                 │  │
│  │  - Session management                                │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Rate Limiting:                                      │  │
│  │  - Per user: 100 tweets/hour                         │  │
│  │  - Per user: 1000 API calls/hour                     │  │
│  │  - Per IP: 10K requests/hour                         │  │
│  │  - Implementation: Token bucket                      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Request Routing:                                    │  │
│  │  - POST /tweet → Tweet Service                       │  │
│  │  - GET /feed → Feed Service                          │  │
│  │  - GET /trending → Trending Service                  │  │
│  │  - GET /search → Search Service                      │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Database Schemas

**DynamoDB - Tweets Table**:
```
┌─────────────────────────────────────────────────────────────┐
│                      TWEETS TABLE                            │
│                                                              │
│  Partition Key: user_id (String)                            │
│  Sort Key: timestamp (Number, milliseconds)                 │
│                                                              │
│  Attributes:                                                 │
│  ├─ tweet_id: String (UUID)                                 │
│  ├─ content: String (max 280 chars)                         │
│  ├─ media_urls: List<String>                                │
│  ├─ hashtags: Set<String>                                   │
│  ├─ mentions: Set<String>                                   │
│  ├─ like_count: Number                                      │
│  ├─ retweet_count: Number                                   │
│  ├─ reply_count: Number                                     │
│  ├─ is_reply: Boolean                                       │
│  ├─ reply_to_tweet_id: String (optional)                    │
│  └─ created_at: String (ISO 8601)                           │
│                                                              │
│  Global Secondary Indexes:                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  GSI-1: tweet_id (PK) → Full tweet data             │  │
│  │  - For direct tweet lookups by ID                   │  │
│  │  - Read capacity: 50K RCU                            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  GSI-2: hashtag (PK), timestamp (SK) → tweets       │  │
│  │  - For hashtag timeline queries                     │  │
│  │  - Read capacity: 20K RCU                            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  Capacity:                                                   │
│  - Write: 20,000 WCU (on-demand)                            │
│  - Read: 200,000 RCU (on-demand)                            │
│  - Storage: 90TB/year                                       │
└─────────────────────────────────────────────────────────────┘
```

**PostgreSQL - Users & Relationships**:
```
┌─────────────────────────────────────────────────────────────┐
│                      USERS TABLE                             │
│                                                              │
│  user_id: BIGSERIAL PRIMARY KEY                             │
│  username: VARCHAR(15) UNIQUE NOT NULL                      │
│  email: VARCHAR(255) UNIQUE NOT NULL                        │
│  display_name: VARCHAR(50)                                  │
│  bio: TEXT                                                  │
│  profile_image_url: VARCHAR(255)                            │
│  follower_count: INTEGER DEFAULT 0                          │
│  following_count: INTEGER DEFAULT 0                         │
│  verified: BOOLEAN DEFAULT FALSE                            │
│  created_at: TIMESTAMP                                      │
│                                                              │
│  Indexes:                                                    │
│  - PRIMARY KEY on user_id                                   │
│  - UNIQUE INDEX on username                                 │
│  - UNIQUE INDEX on email                                    │
│  - INDEX on created_at                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   RELATIONSHIPS TABLE                        │
│                                                              │
│  id: BIGSERIAL PRIMARY KEY                                  │
│  follower_id: BIGINT REFERENCES users(user_id)              │
│  following_id: BIGINT REFERENCES users(user_id)             │
│  created_at: TIMESTAMP DEFAULT NOW()                        │
│                                                              │
│  Indexes:                                                    │
│  - PRIMARY KEY on id                                        │
│  - INDEX on (follower_id, following_id) UNIQUE              │
│  - INDEX on follower_id                                     │
│  - INDEX on following_id                                    │
│  - INDEX on created_at                                      │
│                                                              │
│  Partitioning:                                               │
│  - Hash partition by follower_id                            │
│  - 8 partitions for even distribution                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Redis Cache Structures

**Feed Cache Structure**:
```
┌─────────────────────────────────────────────────────────────┐
│                    REDIS FEED CACHE                          │
│                                                              │
│  Key Pattern: feed:{user_id}                                │
│  Data Type: LIST                                            │
│                                                              │
│  Structure:                                                  │
│  feed:123 = [                                               │
│    "tweet_id_999",  ← Most recent                           │
│    "tweet_id_998",                                          │
│    "tweet_id_997",                                          │
│    ...                                                      │
│    "tweet_id_001"   ← Oldest (1000th)                       │
│  ]                                                          │
│                                                              │
│  Operations:                                                 │
│  - Add tweet: LPUSH feed:{follower_id} {tweet_id}          │
│  - Trim list: LTRIM feed:{follower_id} 0 999               │
│  - Get feed: LRANGE feed:{user_id} 0 99                    │
│  - TTL: EXPIRE feed:{user_id} 300 (5 minutes)              │
│                                                              │
│  Memory per user: ~800 bytes (1000 UUIDs)                   │
│  Active users: 100M                                         │
│  Total memory needed: ~80GB (with overhead: 160GB)          │
└─────────────────────────────────────────────────────────────┘
```

**Trending Cache Structure**:
```
┌─────────────────────────────────────────────────────────────┐
│                  REDIS TRENDING CACHE                        │
│                                                              │
│  Key Pattern: trending:{region}                             │
│  Data Type: SORTED SET (ZSET)                               │
│                                                              │
│  Structure:                                                  │
│  trending:global = {                                        │
│    "#WorldCup": 75000000,     ← Score                       │
│    "#Elections": 45000000,                                  │
│    "#Technology": 12000000,                                 │
│    ...                                                      │
│  }                                                          │
│                                                              │
│  Operations:                                                 │
│  - Add/Update: ZINCRBY trending:global {increment} {tag}   │
│  - Get top 10: ZREVRANGE trending:global 0 9 WITHSCORES    │
│  - Decay: ZINCRBY trending:global -10 {tag}                │
│  - Remove low: ZREMRANGEBYSCORE trending:global -inf 0     │
│                                                              │
│  Variants:                                                   │
│  - trending:global (worldwide)                              │
│  - trending:us (United States)                              │
│  - trending:uk (United Kingdom)                             │
│  - trending:tech (Technology category)                      │
│  - trending:sports (Sports category)                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Capacity & Performance Summary

```
┌─────────────────────────────────────────────────────────────┐
│                   SYSTEM CAPACITY                            │
│                                                              │
│  Write Capacity:                                            │
│  ├─ Tweets: 20,000/sec (peak)                              │
│  ├─ Likes: 100,000/sec                                      │
│  ├─ Retweets: 10,000/sec                                    │
│  └─ Total writes: 130,000/sec                               │
│                                                              │
│  Read Capacity:                                             │
│  ├─ Feed requests: 150,000/sec                              │
│  ├─ Tweet views: 50,000/sec                                 │
│  ├─ Profile views: 20,000/sec                               │
│  └─ Total reads: 220,000/sec                                │
│                                                              │
│  Latency Targets:                                           │
│  ├─ Tweet creation: <50ms (p99)                             │
│  ├─ Feed load: <50ms (p99)                                  │
│  ├─ Trending: <20ms (p99)                                   │
│  └─ Search: <100ms (p99)                                    │
│                                                              │
│  Availability: 99.99% (52 minutes downtime/year)            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   COST BREAKDOWN                             │
│                                                              │
│  Infrastructure:                           Monthly Cost      │
│  ├─ DynamoDB (tweets): ................... $12,000          │
│  ├─ PostgreSQL (users): .................. $3,000           │
│  ├─ Redis Clusters (3): .................. $4,000           │
│  ├─ Elasticsearch: ........................ $2,500           │
│  ├─ Kafka Cluster: ........................ $5,000           │
│  ├─ Worker Fleet: ......................... $8,000           │
│  ├─ API Gateways: ......................... $2,000           │
│  ├─ Load Balancers: ....................... $500            │
│  ├─ CDN (CloudFront): ..................... $3,000           │
│  └─ Monitoring/Logging: ................... $1,000           │
│                                                              │
│  Total: ~$41,000/month                                      │
│  Per user (100M DAU): $0.00041/day                          │
└─────────────────────────────────────────────────────────────┘
```

---

## Regional Distribution

```
┌─────────────────────────────────────────────────────────────────────┐
│                      GLOBAL DEPLOYMENT                               │
└─────────────────────────────────────────────────────────────────────┘

US-EAST Region (Primary)
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (4 instances)     │
│ • Kafka Cluster (10 brokers)           │
│ • PostgreSQL Master                     │
│ • PostgreSQL Read Replicas (4)          │
│ • Redis Clusters (Full)                 │
│ • DynamoDB (regional table)             │
│ • Worker Fleet (150 workers)            │
│ • Handles: 50% of traffic               │
└─────────────────────────────────────────┘

US-WEST Region
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (4 instances)     │
│ • PostgreSQL Read Replicas (3)          │
│ • Redis Cache (Read replicas)           │
│ • DynamoDB (regional table)             │
│ • Worker Fleet (100 workers)            │
│ • Handles: 30% of traffic               │
└─────────────────────────────────────────┘

EU-WEST Region
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (3 instances)     │
│ • PostgreSQL Read Replicas (2)          │
│ • Redis Cache (Read replicas)           │
│ • DynamoDB (regional table)             │
│ • Worker Fleet (50 workers)             │
│ • Handles: 15% of traffic               │
└─────────────────────────────────────────┘

AP-SOUTH Region
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (2 instances)     │
│ • PostgreSQL Read Replicas (1)          │
│ • Redis Cache (Read replicas)           │
│ • DynamoDB (regional table)             │
│ • Worker Fleet (30 workers)             │
│ • Handles: 5% of traffic                │
└─────────────────────────────────────────┘
```

---

## Quick Reference

### API Endpoints

```
┌─────────────────────────────────────────────────────────────┐
│                    API ENDPOINTS                             │
├─────────────────────────────────────────────────────────────┤
│  POST   /api/v1/tweet                                       │
│         Create new tweet                                    │
│         Latency: 40ms | Rate: 100/hour/user                 │
│                                                              │
│  GET    /api/v1/feed?userId={id}&limit=100                  │
│         Get user timeline                                   │
│         Latency: 45ms | Cache hit: 85%                      │
│                                                              │
│  GET    /api/v1/trending?region={region}                    │
│         Get trending topics                                 │
│         Latency: 15ms | Cache hit: 95%                      │
│                                                              │
│  GET    /api/v1/tweet/{id}                                  │
│         Get single tweet                                    │
│         Latency: 20ms | Cache hit: 70%                      │
│                                                              │
│  POST   /api/v1/tweet/{id}/like                             │
│         Like a tweet                                        │
│         Latency: 30ms | Async processing                    │
│                                                              │
│  GET    /api/v1/search?q={query}&type={type}                │
│         Search tweets, users, hashtags                      │
│         Latency: 100ms | Elasticsearch                      │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

```
┌─────────────────────────────────────────────────────────────┐
│                  DESIGN DECISIONS                            │
├─────────────────────────────────────────────────────────────┤
│  Decision 1: Eventual Consistency                           │
│  └─ Trade: Strong consistency for performance               │
│     Acceptable for social media                             │
│                                                              │
│  Decision 2: Hybrid Fan-out                                 │
│  └─ Trade: Complexity for write scalability                 │
│     Prevents celebrity write storms                         │
│                                                              │
│  Decision 3: Multiple Databases                             │
│  └─ Trade: Operational overhead for optimization            │
│     Right tool for each data type                           │
│                                                              │
│  Decision 4: Async Processing                               │
│  └─ Trade: Eventual consistency for low latency             │
│     Users see tweets within 1-2 seconds                     │
│                                                              │
│  Decision 5: Multi-Tier Caching                             │
│  └─ Trade: Cache invalidation complexity for performance    │
│     Reduces database load by 90%+                           │
└─────────────────────────────────────────────────────────────┘
```

---

## Monitoring Dashboard Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│                       GRAFANA DASHBOARD                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Write Latency      │  │   Read Latency       │                │
│  │   p50: 30ms          │  │   p50: 20ms          │                │
│  │   p99: 80ms ✓        │  │   p99: 45ms ✓        │                │
│  │   [Line Graph]       │  │   [Line Graph]       │                │
│  └──────────────────────┘  └──────────────────────┘                │
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Cache Hit Rates    │  │   Error Rates        │                │
│  │   Feed: 85% ✓        │  │   4xx: 0.05% ✓       │                │
│  │   Tweet: 70% ✓       │  │   5xx: 0.01% ✓       │                │
│  │   [Bar Chart]        │  │   [Line Graph]       │                │
│  └──────────────────────┘  └──────────────────────┘                │
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Kafka Lag          │  │   Worker Health      │                │
│  │   tweet_created: 234 │  │   Fan-out: 200/200   │                │
│  │   ✓ < 1000           │  │   Search: 50/50 ✓    │                │
│  │   [Gauge]            │  │   [Status Grid]      │                │
│  └──────────────────────┘  └──────────────────────┘                │
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Throughput         │  │   Database Perf      │                │
│  │   Writes: 6.2K/s     │  │   DynamoDB: 15ms     │                │
│  │   Reads: 58K/s       │  │   PostgreSQL: 30ms   │                │
│  │   [Area Chart]       │  │   [Line Graph]       │                │
│  └──────────────────────┘  └──────────────────────┘                │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Comparison: Original vs Improved

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ARCHITECTURE COMPARISON                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Aspect              │ Original          │ Improved                 │
│  ────────────────────┼───────────────────┼─────────────────────────│
│  Load Balancer       │ ✗ Missing         │ ✓ ALB Multi-AZ          │
│  API Gateway HA      │ ✗ Single instance │ ✓ Multi-region clusters │
│  Service Separation  │ ⚠ Unclear         │ ✓ Clear microservices   │
│  User Database       │ ✗ Missing         │ ✓ PostgreSQL            │
│  Tweet Storage       │ ✓ DynamoDB        │ ✓ DynamoDB (optimized)  │
│  Fan-out Strategy    │ ✗ Not defined     │ ✓ Hybrid approach       │
│  Caching Layers      │ ⚠ Multiple, unclear│ ✓ 6-tier hierarchy     │
│  Search              │ ✗ Missing         │ ✓ Elasticsearch         │
│  Monitoring          │ ✗ Not included    │ ✓ Comprehensive         │
│  Failure Handling    │ ✗ Not addressed   │ ✓ Multi-level redundancy│
│  ────────────────────┼───────────────────┼─────────────────────────│
│  Availability        │ ~99%              │ 99.99%                  │
│  Write Capacity      │ ~1K tweets/sec    │ 20K tweets/sec          │
│  Read Capacity       │ ~10K reads/sec    │ 200K reads/sec          │
│  Write Latency       │ Unclear           │ 40ms (p50)              │
│  Read Latency        │ Unclear           │ 30-50ms (p50)           │
│  Monthly Cost        │ ~$5K              │ ~$41K                   │
│                                                                      │
│  GRADE               │ B+ (70/100)       │ A (95/100)              │
└─────────────────────────────────────────────────────────────────────┘
```

---

**Document Version**: 1.0  
**Created**: November 2024  
**System**: Twitter Architecture - Complete Design  
**Companion**: [Design Review](./twitter-design-review.md)
