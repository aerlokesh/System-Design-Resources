# Short Video Platform - System Architecture Diagram

## Complete System Architecture

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
│  │  • Video Streaming (HLS/DASH) - Adaptive Bitrate                   │    │
│  │  • Static Assets (Thumbnails, Profile Pictures, Icons)             │    │
│  │  • Edge Caching (200+ POPs Globally)                               │    │
│  │  • Multi-tier Cache: Edge → Regional → Origin                      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  GLOBAL LOAD BALANCER (Route 53)                             │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  • Geo-based DNS Routing                                           │    │
│  │  • Health Checks (30s intervals)                                   │    │
│  │  • Automatic Failover                                              │    │
│  │  • Multi-Region Support                                            │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY LAYER                                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  • Rate Limiting (Token Bucket Algorithm)                          │    │
│  │  • Authentication (JWT Validation)                                 │    │
│  │  • Request/Response Transformation                                 │    │
│  │  • API Versioning & Routing                                        │    │
│  │  • Protocol Translation (REST/GraphQL/WebSocket)                   │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LOAD BALANCER (ALB)                           │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  • Health Checks (Target Groups)                                   │    │
│  │  • SSL/TLS Termination                                             │    │
│  │  • Traffic Distribution (Round Robin/Least Connections)            │    │
│  │  • Sticky Sessions Support                                         │    │
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
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │    Social    │  │    Search    │  │Recommendation│  │ Notification │  │
│  │   Service    │  │   Service    │  │   Service    │  │   Service    │  │
│  │              │  │              │  │              │  │              │  │
│  │ • Likes      │  │ • Videos     │  │ • ML Models  │  │ • Push       │  │
│  │ • Comments   │  │ • Users      │  │ • Ranking    │  │ • In-App     │  │
│  │ • Shares     │  │ • Tags       │  │ • Features   │  │ • Email      │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                                              │
│  ┌──────────────┐                                                           │
│  │  Analytics   │                                                           │
│  │   Service    │                                                           │
│  │              │                                                           │
│  │ • Metrics    │                                                           │
│  │ • Events     │                                                           │
│  │ • Reports    │                                                           │
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
│  │  ┌───────────────┐  ┌───────────────┐                             │    │
│  │  │ Trending      │  │ Social Graph  │                             │    │
│  │  │ Videos Cache  │  │ Cache         │                             │    │
│  │  │               │  │               │                             │    │
│  │  │ • Hot Videos  │  │ • Followers   │                             │    │
│  │  │ • TTL: 5min   │  │ • Following   │                             │    │
│  │  └───────────────┘  └───────────────┘                             │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                        Memcached                                   │    │
│  │                                                                     │    │
│  │  • Simple Key-Value Cache                                          │    │
│  │  • User Profiles                                                   │    │
│  │  • Video View Counts                                               │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MESSAGE QUEUE LAYER (Kafka)                             │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │              Kafka Cluster (Multi-partition, Replicated)           │    │
│  │                                                                     │    │
│  │  Topics:                                                            │    │
│  │  ┌──────────────────────────────────────────────────────────┐     │    │
│  │  │  • video.uploaded         - New video upload events      │     │    │
│  │  │  • video.processed        - Video processing complete    │     │    │
│  │  │  • user.interaction       - Likes, comments, shares      │     │    │
│  │  │  • feed.update            - Feed refresh events          │     │    │
│  │  │  • notification.trigger   - Push notification events     │     │    │
│  │  │  • analytics.event        - User behavior events         │     │    │
│  │  └──────────────────────────────────────────────────────────┘     │    │
│  │                                                                     │    │
│  │  Configuration:                                                     │    │
│  │  • Partitions: 100+ per topic                                      │    │
│  │  • Replication Factor: 3                                           │    │
│  │  • Retention: 7 days                                               │    │
│  └────────────────────────────────────────────────────────────────────┘    │
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
│  │ • Transcode  │  │ • Push Model │  │ • Follow     │  │ • FCM/APNS   │  │
│  │ • Thumbnail  │  │ • Pull Model │  │ • Unfollow   │  │ • Email      │  │
│  │ • Moderation │  │ • Redis Sync │  │ • Update     │  │ • SMS        │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐                                        │
│  │  Analytics   │  │   Content    │                                        │
│  │   Workers    │  │  Moderation  │                                        │
│  │              │  │   Workers    │                                        │
│  │              │  │              │                                        │
│  │ • Aggregate  │  │ • AI Check   │                                        │
│  │ • Report     │  │ • Human Rev  │                                        │
│  │ • ETL        │  │ • Appeal     │                                        │
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
│  │  Tables:                                                            │    │
│  │  • Users          - User profiles, settings                        │    │
│  │  • Videos         - Video metadata, stats                          │    │
│  │  • Interactions   - Likes, saves                                   │    │
│  │                                                                     │    │
│  │  Features:                                                          │    │
│  │  • Auto-scaling                                                    │    │
│  │  • Global tables (multi-region)                                    │    │
│  │  • Point-in-time recovery                                          │    │
│  │  • Sharded by user_id                                              │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │              PostgreSQL (Relational Database)                      │    │
│  │                                                                     │    │
│  │  Tables:                                                            │    │
│  │  • Followers      - Social graph (follower/followee)               │    │
│  │  • Comments       - Nested comments structure                      │    │
│  │  • Reports        - Analytics aggregations                         │    │
│  │                                                                     │    │
│  │  Configuration:                                                     │    │
│  │  • Sharded by user_id (100 shards)                                │    │
│  │  • 5 read replicas per shard                                       │    │
│  │  • Connection pooling (PgBouncer)                                  │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                 Cassandra (Time-Series Data)                       │    │
│  │                                                                     │    │
│  │  Tables:                                                            │    │
│  │  • ViewHistory       - User watch history                          │    │
│  │  • UserInteractions  - Engagement timeline                         │    │
│  │  • ActivityLogs      - User activity streams                       │    │
│  │                                                                     │    │
│  │  Configuration:                                                     │    │
│  │  • Replication factor: 3                                           │    │
│  │  • 90-day retention                                                │    │
│  │  • Time-based partitioning                                         │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │               Elasticsearch (Search & Discovery)                   │    │
│  │                                                                     │    │
│  │  Indices:                                                           │    │
│  │  • Videos         - Full-text search on videos                     │    │
│  │  • Users          - User search                                    │    │
│  │  • Tags           - Trending topics                                │    │
│  │                                                                     │    │
│  │  Features:                                                          │    │
│  │  • Real-time indexing                                              │    │
│  │  • Fuzzy search                                                    │    │
│  │  • Autocomplete                                                    │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                  S3 (Object Storage)                               │    │
│  │                                                                     │    │
│  │  Buckets:                                                           │    │
│  │  • videos-raw         - Original uploads                           │    │
│  │  • videos-processed   - Transcoded (360p, 720p, 1080p, 4K)        │    │
│  │  • thumbnails         - Video thumbnails                           │    │
│  │  • avatars            - User profile pictures                      │    │
│  │                                                                     │    │
│  │  Lifecycle:                                                         │    │
│  │  • Hot (0-30 days):    S3 Standard                                │    │
│  │  • Warm (31-90 days):  S3 Infrequent Access                       │    │
│  │  • Cold (91-365 days): S3 Glacier                                 │    │
│  │  • Archive (366+ days): S3 Deep Archive                           │    │
│  │                                                                     │    │
│  │  Features:                                                          │    │
│  │  • Cross-region replication                                        │    │
│  │  • Versioning enabled                                              │    │
│  │  • Server-side encryption (AES-256)                               │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ML & ANALYTICS LAYER                                  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                   Feature Store (Real-time + Batch)                │    │
│  │                                                                     │    │
│  │  Features:                                                          │    │
│  │  • User Features       - Demographics, preferences, history        │    │
│  │  • Video Features      - Embeddings, metadata, performance         │    │
│  │  • Context Features    - Time, location, device                    │    │
│  │  • Interaction Features - Engagement patterns                      │    │
│  │                                                                     │    │
│  │  Storage: Redis (online) + S3 (offline)                            │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │         ML Model Serving (TensorFlow Serving / SageMaker)          │    │
│  │                                                                     │    │
│  │  Models:                                                            │    │
│  │  ┌─────────────────────────────────────────────────────────┐      │    │
│  │  │  Recommendation Model                                   │      │    │
│  │  │  • Collaborative Filtering                              │      │    │
│  │  │  • Content-Based Filtering                              │      │    │
│  │  │  • Deep Neural Networks                                 │      │    │
│  │  │  • A/B Testing Framework                                │      │    │
│  │  └─────────────────────────────────────────────────────────┘      │    │
│  │                                                                     │    │
│  │  ┌─────────────────────────────────────────────────────────┐      │    │
│  │  │  Content Moderation Model                               │      │    │
│  │  │  • Violence Detection                                   │      │    │
│  │  │  • NSFW Detection                                       │      │    │
│  │  │  • Hate Speech Detection                                │      │    │
│  │  └─────────────────────────────────────────────────────────┘      │    │
│  │                                                                     │    │
│  │  ┌─────────────────────────────────────────────────────────┐      │    │
│  │  │  Ranking Model                                          │      │    │
│  │  │  • Engagement Prediction                                │      │    │
│  │  │  • Quality Scoring                                      │      │    │
│  │  │  • Diversity Optimization                               │      │    │
│  │  └─────────────────────────────────────────────────────────┘      │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │              Data Warehouse (Redshift / BigQuery)                  │    │
│  │                                                                     │    │
│  │  Purpose:                                                           │    │
│  │  • Historical analytics                                            │    │
│  │  • Business intelligence reports                                   │    │
│  │  • ML model training data                                          │    │
│  │  • Ad-hoc queries                                                  │    │
│  │                                                                     │    │
│  │  ETL Pipeline: Kafka → S3 → Data Warehouse                         │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      OBSERVABILITY & MONITORING                              │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    Logging (ELK Stack)                             │    │
│  │                                                                     │    │
│  │  Pipeline: Application → Fluentd → Kafka → Elasticsearch → Kibana  │    │
│  │                                                                     │    │
│  │  Retention:                                                         │    │
│  │  • Hot (7 days):   Fast queries                                    │    │
│  │  • Warm (30 days): Standard storage                                │    │
│  │  • Cold (1 year):  Archive                                         │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                Metrics (Prometheus + Grafana)                      │    │
│  │                                                                     │    │
│  │  Metrics:                                                           │    │
│  │  • Application: Latency (p50, p95, p99), Error rates, QPS         │    │
│  │  • Infrastructure: CPU, Memory, Network, Disk I/O                 │    │
│  │  • Business: DAU, Upload rate, Engagement, Retention              │    │
│  │                                                                     │    │
│  │  Retention: 15 days high-res, 1 year downsampled                   │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │              Distributed Tracing (Jaeger / X-Ray)                  │    │
│  │                                                                     │    │
│  │  • Request flow visualization                                      │    │
│  │  • Performance bottleneck identification                           │    │
│  │  • Service dependency mapping                                      │    │
│  │  • Error root cause analysis                                       │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │              Alerting (PagerDuty / Opsgenie)                       │    │
│  │                                                                     │    │
│  │  Critical (Page immediately):                                       │    │
│  │  • Service downtime, Error rate spike, Latency spike              │    │
│  │                                                                     │    │
│  │  Warning (Slack notification):                                      │    │
│  │  • High resource usage, Cache hit rate drop                        │    │
│  └────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Scale & Performance Specifications

### Traffic Capacity
- **Daily Active Users:** 1 Billion
- **Read QPS:** 100 Million
- **Write QPS:** 10 Million
- **Read/Write Ratio:** 10:1
- **Peak Concurrent Streams:** 700K
- **Peak Bandwidth:** 1.4 Tbps

### Storage Capacity
- **Video Storage:** ~2 EB (2-year retention)
- **Daily Upload:** 2.5 PB/day
- **Database Size:** 390 TB
- **Cache Size:** 16 TB (Redis)

### Performance Targets
- **Video Load Time:** < 1 second
- **API Response Time:** < 500ms (p95)
- **Feed Refresh:** < 2 seconds
- **Search Latency:** < 300ms

### Availability & Reliability
- **Uptime SLA:** 99.99%
- **RTO (Recovery Time Objective):** 1 hour
- **RPO (Recovery Point Objective):** 5 minutes
- **Multi-Region:** Active-Active in 4 regions

---

## Technology Stack Summary

| Layer | Technologies |
|-------|-------------|
| **Frontend** | React Native (Mobile), React.js (Web) |
| **CDN** | CloudFront, Akamai |
| **Load Balancing** | Route 53, Application Load Balancer |
| **API Gateway** | Kong, AWS API Gateway |
| **Services** | Node.js, Go, Java (Spring Boot) |
| **Cache** | Redis Cluster, Memcached |
| **Message Queue** | Apache Kafka |
| **Primary DB** | DynamoDB |
| **Relational DB** | PostgreSQL (Sharded) |
| **Time-Series DB** | Apache Cassandra |
| **Search** | Elasticsearch |
| **Object Storage** | Amazon S3 |
| **ML Platform** | TensorFlow, SageMaker |
| **Data Warehouse** | Redshift, BigQuery |
| **Monitoring** | ELK Stack, Prometheus, Grafana, Jaeger |
| **Container Orchestration** | Kubernetes (EKS) |
| **CI/CD** | Jenkins, GitLab CI |

---

## Cost Estimation (Monthly)

| Component | Cost |
|-----------|------|
| CDN (CloudFront) | $450M |
| DynamoDB | $53M |
| Application Servers | $20M |
| Storage (S3) | $11.3M |
| ML Infrastructure | $10M |
| PostgreSQL | $2M |
| Redis Cluster | $1.5M |
| Video Processing | $5M |
| Cassandra | $0.5M |
| Other (Monitoring, Networking) | $14M |
| **TOTAL** | **~$567M/month** |

**Annual Cost:** ~$6.8 Billion

---

## Security Features

- **Authentication:** Multi-factor (Email/SMS/Biometric), OAuth 2.0, JWT
- **Authorization:** Role-based access control (RBAC)
- **Encryption:** AES-256 at rest, TLS 1.3 in transit
- **DRM:** Encrypted video segments with token-based access
- **Rate Limiting:** Tier-based (100-5000 req/min)
- **DDoS Protection:** CloudFlare, AWS Shield, WAF
- **Content Moderation
