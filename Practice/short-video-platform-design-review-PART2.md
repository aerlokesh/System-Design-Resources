# Short Video Platform System Design - Review & Improvements (PART 2)

*This is a continuation of `short-video-platform-design-review.md`*

---

## 8. API DESIGN (Continued)

### 8.2 Feed APIs (Continued)

```http
GET /api/v1/feed/following
Authorization: Bearer {jwt_token}
Query Parameters:
  - page_size: int (default: 20)
  - cursor: string

Response:
{
  "videos": [...],
  "next_cursor": "string",
  "has_more": boolean
}

---

GET /api/v1/feed/trending
Query Parameters:
  - category: string (optional)
  - region: string (optional)
  - time_range: "24h|7d|30d" (default: 24h)

Response:
{
  "videos": [...],
  "next_cursor": "string"
}
```

### 8.3 Social APIs

```http
POST /api/v1/videos/{video_id}/like
Authorization: Bearer {jwt_token}

Response:
{
  "success": boolean,
  "total_likes": number
}

---

DELETE /api/v1/videos/{video_id}/like
Authorization: Bearer {jwt_token}

---

POST /api/v1/videos/{video_id}/comments
Authorization: Bearer {jwt_token}

Request:
{
  "content": "string",
  "parent_comment_id": "uuid" (optional)
}

Response:
{
  "comment_id": "uuid",
  "content": "string",
  "user": {...},
  "created_at": "timestamp"
}

---

GET /api/v1/videos/{video_id}/comments
Query Parameters:
  - page_size: int (default: 20)
  - cursor: string
  - sort: "top|recent" (default: top)

---

POST /api/v1/users/{user_id}/follow
Authorization: Bearer {jwt_token}

Response:
{
  "success": boolean,
  "is_following": boolean
}
```

### 8.4 Search APIs

```http
GET /api/v1/search/videos
Query Parameters:
  - q: string (required)
  - category: string (optional)
  - duration: "short|medium|long" (optional)
  - upload_date: "today|week|month|year" (optional)
  - sort: "relevance|views|recent" (default: relevance)
  - page_size: int (default: 20)
  - cursor: string

Response:
{
  "videos": [...],
  "next_cursor": "string",
  "total_results": number
}

---

GET /api/v1/search/users
Query Parameters:
  - q: string (required)
  - page_size: int (default: 20)
  - cursor: string

Response:
{
  "users": [
    {
      "user_id": "uuid",
      "username": "string",
      "display_name": "string",
      "profile_image_url": "string",
      "verified": boolean,
      "follower_count": number,
      "video_count": number
    }
  ],
  "next_cursor": "string"
}
```

---

## 9. CAPACITY PLANNING & SCALING

### 9.1 Storage Requirements

**Video Storage:**
```
Assumptions:
- 1B DAU
- 10% upload daily = 100M uploads/day
- Average video size: 50MB (before compression)
- Compression ratio: 3:1
- Average compressed size: ~17MB per video
- Multiple quality levels: 1.5x storage multiplier
- Total per video: ~25MB

Daily Storage:
100M videos × 25MB = 2.5 PB/day

Annual Storage:
2.5 PB × 365 = 912 PB ≈ 1 EB/year

With 2-year retention:
~2 EB total storage needed

Cost (S3 Standard → S3 IA → Glacier):
- Hot (30 days): 75 PB @ $0.023/GB = $1.7M/month
- Warm (90 days): 225 PB @ $0.0125/GB = $2.8M/month  
- Cold (rest): ~1.7 EB @ $0.004/GB = $6.8M/month
Total: ~$11.3M/month for storage
```

**Thumbnail Storage:**
```
Per video: 5 thumbnails × 200KB = 1MB
100M videos/day × 1MB = 100 TB/day
Annual: 36.5 PB
2-year: 73 PB
Cost: ~$1.7M/month
```

### 9.2 Bandwidth Requirements

**Video Streaming:**
```
Read Heavy: 10:1 ratio
Daily video views: 1B DAU × 20 videos/user = 20B views/day
Peak traffic (assume 3x avg): 20B × 3 / 86400 = ~700K videos/sec

Average video: 60 seconds
Average bitrate: 2 Mbps (adaptive)
Bandwidth per stream: 2 Mbps

Peak bandwidth:
700K concurrent streams × 2 Mbps = 1.4 Tbps

Monthly egress:
20B views/day × 30 days × 60 sec × 2 Mbps / 8 = ~90 EB/month

CDN Cost (with CDN):
90 EB @ $0.02/GB = $1.8B/month (without CDN)
90 EB @ $0.005/GB = $450M/month (with CDN optimization)
```

### 9.3 Database Scaling

**DynamoDB Requirements:**
```
User Table:
- 1B users × 2KB avg = 2 TB
- Read: 100M QPS (cached, 1% db hit) = 1M RCU
- Write: 10M QPS (user updates) = 10M WCU

Video Metadata Table:
- 100M new videos/day
- 2 years retention: 73B videos
- 73B × 5KB = 365 TB
- Read: Heavy caching, 5% db hit = 5M RCU
- Write: 100M/day = 1.2K WPS avg, 10K WPS peak

Cost Estimate:
- Storage: 367 TB @ $0.25/GB = $92K/month
- Read throughput: 6M RCU = $3M/month
- Write throughput: 10M WCU = $50M/month
```

**PostgreSQL (Follower Graph):**
```
Followers table:
- Avg 500 followers per user
- 1B users × 500 = 500B rows
- 500B × 50 bytes = 25 TB

Sharding Strategy:
- Shard by user_id (follower)
- 100 shards = 250 GB per shard
- Each shard: r5.4xlarge (16 vCPU, 128GB RAM)
- 5 read replicas per shard
- Total: 600 instances

Cost: ~$2M/month
```

**Cassandra (Time-Series):**
```
View History:
- 1B users × 20 videos/day = 20B records/day
- 90-day retention: 1.8T records
- 1.8T × 100 bytes = 180 TB

Replication factor: 3
Total storage: 540 TB

Cost: ~$500K/month
```

### 9.4 Cache Scaling

**Redis Cluster:**
```
Feed Cache:
- 1B users × 100 videos cached = 100B entries
- 100B × 100 bytes (video_id + metadata) = 10 TB
- With overhead: 15 TB

User Session Cache:
- Peak concurrent users: 100M
- 100M × 5KB = 500 GB

Total Redis: ~16 TB
Configuration:
- 500 r6g.8xlarge instances (32GB each)
- Cost: ~$1.5M/month
```

### 9.5 Compute Requirements

**Application Servers:**
```
QPS: 110M total (100M read + 10M write)
Per server capacity: 1K QPS
Required servers: 110K servers

Peak traffic (3x): 330K servers
Use auto-scaling with baseline 150K

Instance type: c6i.2xlarge (8 vCPU)
Cost: ~$20M/month
```

**Video Processing Workers:**
```
Upload rate: 100M videos/day = 1.2K videos/sec
Processing time: 5 minutes avg
Concurrent jobs: 1.2K × 300 = 360K
Workers needed: 40K c6i.4xlarge instances
Cost: ~$5M/month
```

### 9.6 Total Infrastructure Cost Estimate

```
Component                    Monthly Cost
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Storage (S3)                 $11.3M
Thumbnails                   $1.7M
CDN (CloudFront)             $450M
DynamoDB                     $53M
PostgreSQL                   $2M
Cassandra                    $0.5M
Redis                        $1.5M
Application Servers          $20M
Video Processing             $5M
ML Infrastructure            $10M
Monitoring/Logging           $2M
Networking                   $10M
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL                        ~$567M/month
                             ~$6.8B/year
```

---

## 10. PERFORMANCE OPTIMIZATIONS

### 10.1 Video Delivery Optimization

**Adaptive Bitrate Streaming (ABR):**
```
Implement HLS/DASH protocols:
- Automatically adjust quality based on bandwidth
- Chunk videos into 2-10 second segments
- Client requests appropriate quality chunks

Quality Levels:
- 4K: 20 Mbps
- 1080p: 5 Mbps
- 720p: 2.5 Mbps
- 480p: 1 Mbps
- 360p: 600 Kbps

Benefits:
- Reduced buffering
- Better user experience
- Bandwidth optimization
```

**CDN Strategy:**
```
Multi-tier caching:
1. Edge locations (CloudFront): 200+ POPs
2. Regional caches: 20 locations
3. Origin servers: S3 with transfer acceleration

Cache rules:
- Popular videos (>100K views): Cache for 7 days
- Recent videos (<24h): Cache for 1 hour
- Old videos: On-demand caching

Pre-warming:
- Trending videos pushed to all edge locations
- New uploads from verified accounts pre-cached
```

### 10.2 Feed Optimization

**Lazy Loading:**
```
Initial load: 20 videos
Preload next batch: When user reaches video 15
Discard old videos: Keep max 50 in memory

Benefits:
- Reduced initial load time
- Better memory management
- Smooth infinite scroll
```

**Personalization Caching:**
```
Cache layers:
1. L1 (Client): 50 videos, 5 min TTL
2. L2 (Redis): 200 videos, 30 min TTL
3. L3 (Recommendation Service): Fresh generation

Cache key structure:
feed:{user_id}:{context}:{timestamp_bucket}

Context includes:
- Time of day
- Day of week
- Location
- Device type
```

### 10.3 Database Query Optimization

**Hot Data Separation:**
```
Recent data (< 7 days):
- High-performance SSD storage
- Aggressive caching
- Optimized indexes

Warm data (7-90 days):
- Standard storage
- Moderate caching
- Selective indexes

Cold data (> 90 days):
- Archive storage
- Minimal caching
- Basic indexes
```

**Denormalization:**
```
Pre-compute expensive joins:
- User profile with stats (materialized view)
- Video with creator info
- Feed with video metadata

Update via Kafka events
Refresh on write or hourly batch job
```

### 10.4 Real-time Features Optimization

**WebSocket Connection Management:**
```
For live features (live comments, view counts):
- Connection pooling
- Geographic routing
- Max 10K connections per server
- Auto-scaling based on connection count

Rate limiting:
- Max 10 messages/sec per connection
- Burst: 20 messages
- Penalty: Temporary throttling
```

---

## 11. SECURITY & COMPLIANCE

### 11.1 Content Security

**DRM (Digital Rights Management):**
```
- Encrypted video segments (AES-128)
- Token-based access (JWT with short TTL)
- Device fingerprinting
- Watermarking for premium content
```

**Content Moderation:**
```
Automated (AI/ML):
- Violence detection
- NSFW content detection
- Copyright infringement (audio/video fingerprinting)
- Hate speech detection (NLP)

Human Review:
- Flagged content queue
- 24/7 moderation team
- Appeal process
- Community guidelines enforcement
```

### 11.2 User Security

**Authentication:**
```
Multi-factor authentication:
- Email/Phone OTP
- Biometric (mobile)
- Social login (OAuth 2.0)

Session management:
- JWT tokens (15 min access, 7-day refresh)
- Secure cookie storage (HttpOnly, Secure, SameSite)
- Device tracking and suspicious activity alerts
```

**Privacy:**
```
Data encryption:
- At rest: AES-256
- In transit: TLS 1.3
- Database: Encrypted columns for PII

GDPR/CCPA Compliance:
- User data export
- Right to deletion
- Consent management
- Data retention policies
```

### 11.3 API Security

**Rate Limiting:**
```
Tier-based limits:
- Anonymous: 100 req/min
- Authenticated: 1000 req/min
- Verified: 5000 req/min
- API partners: Custom limits

Algorithm: Token bucket with sliding window
Enforcement: API Gateway + Redis
```

**DDoS Protection:**
```
Layers:
1. CloudFlare/AWS Shield: L3/L4 protection
2. WAF: L7 protection, bot detection
3. Rate limiting: Application level
4. CAPTCHA: For suspicious patterns
```

---

## 12. MONITORING & OBSERVABILITY

### 12.1 Key Metrics

**Application Metrics:**
```
- Request latency (p50, p95, p99)
- Error rate (4xx, 5xx)
- Request throughput (QPS)
- Service availability (uptime %)
```

**Business Metrics:**
```
- Daily Active Users (DAU)
- Video upload rate
- Video view rate
- Engagement rate (likes, comments, shares)
- User retention (1-day, 7-day, 30-day)
- Video completion rate
- Average watch time
```

**Infrastructure Metrics:**
```
- CPU utilization
- Memory usage
- Network throughput
- Disk I/O
- Cache hit rate
- Database connection pool
```

### 12.2 Alerting Strategy

**Critical Alerts (Page immediately):**
```
- Service downtime (availability < 99%)
- Error rate spike (>1%)
- Latency spike (p99 > 3s)
- Database connection failures
- CDN failures
```

**Warning Alerts (Slack notification):**
```
- Degraded performance (p95 > 1s)
- High resource utilization (>80%)
- Cache hit rate drop (<80%)
- Increasing error trends
```

### 12.3 Logging Strategy

**Log Levels:**
```
- ERROR: Application errors, exceptions
- WARN: Degraded performance, fallbacks
- INFO: Significant events (uploads, user actions)
- DEBUG: Detailed debugging info (dev/staging only)
```

**Log Aggregation:**
```
Pipeline:
Application → Fluentd → Kafka → Elasticsearch → Kibana

Retention:
- Hot logs (7 days): Fast querying
- Warm logs (30 days): Standard storage
- Cold logs (1 year): Archive
```

---

## 13. DISASTER RECOVERY & HIGH AVAILABILITY

### 13.1 Multi-Region Architecture

**Active-Active Deployment:**
```
Regions: US-EAST, US-WEST, EU-WEST, AP-SOUTHEAST
Each region:
- Full application stack
- Database replicas (read)
- Cache clusters
- Video storage (S3 cross-region replication)

Routing:
- Geographic routing (Route 53)
- Automatic failover
- Health checks every 30 seconds
```

### 13.2 Backup Strategy

**Database Backups:**
```
- Continuous incremental backups (every 5 min)
- Full daily backups (retained 30 days)
- Weekly backups (retained 1 year)
- Point-in-time recovery (up to 35 days)
```

**Video Backups:**
```
- Primary: S3 in main region
- Secondary: S3 cross-region replication
- Archive: Glacier for >1 year old content
- RTO: 1 hour for critical videos
- RPO: 5 minutes
```

### 13.3 Failover Procedures

**Automated Failover:**
```
Triggers:
- Region health check failure (3 consecutive)
- 5xx error rate > 10%
- Latency > 10 seconds

Actions:
1. Route 53 updates DNS (TTL: 60s)
2. Notify on-call team
3. Initiate backup region promotion
4. Verify data consistency
5. Monitor traffic shift
```

---

## 14. COST OPTIMIZATION STRATEGIES

### 14.1 Storage Optimization

**Intelligent Tiering:**
```
Lifecycle policies:
- Day 0-30: S3 Standard (hot)
- Day 31-90: S3 IA (warm)
- Day 91-365: S3 Glacier (cold)
- Day 366+: S3 Deep Archive

Savings: ~70% on storage costs
```

**Deduplication:**
```
- Hash-based duplicate detection
- Share storage for identical videos
- Reference counting for deletion
- Estimated savings: 15-20%
```

### 14.2 Compute Optimization

**Spot Instances:**
```
Use cases:
- Video processing workers (fault-tolerant)
- Batch jobs (analytics, ML training)
- Non-critical background tasks

Savings: ~70% on compute costs
Risk mitigation: Checkpointing, graceful shutdown
```

**Right-sizing:**
```
- Continuous monitoring of resource usage
- Auto-scaling based on actual load
- Instance type recommendations (AWS Compute Optimizer)
- Reserved Instances for baseline capacity (30% savings)
```

### 14.3 CDN Optimization

**Smart Caching:**
```
- Cache only popular content (Pareto principle)
- Regional cache optimization
- Compress assets (Brotli/gzip)
- Image optimization (WebP, AVIF)

Estimated bandwidth reduction: 40-50%
```

---

## 15. FUTURE ENHANCEMENTS

### 15.1 Advanced Features

**Live Streaming:**
```
- WebRTC for low-latency streaming
- Live transcoding
- Chat integration
- Virtual gifts/monetization
```

**AR/VR Filters:**
```
- Client-side rendering (mobile GPU)
- Filter marketplace
- Custom filter creation tools
- Real-time face tracking
```

**AI-Powered Features:**
```
- Auto-captioning
- Video summarization
- Smart video editing
- Personalized thumbnails
- Background removal
```

### 15.2 Monetization Features

**Creator Economy:**
```
- Ad revenue sharing
- Subscription tiers
- Pay-per-view content
- Virtual gifts
- Brand partnerships
- Merchandise integration
```

**Analytics Dashboard:**
```
- Real-time performance metrics
- Audience demographics
- Revenue tracking
- Growth insights
- A/B testing tools
```

---

## 16. SUMMARY OF KEY IMPROVEMENTS

### Critical Additions to Original Design:
1. ✅ **API Gateway & Load Balancer** - Essential for traffic management
2. ✅ **Video Processing Pipeline** - Missing transcoding & optimization
3. ✅ **Recommendation Engine** - Core feature for engagement
4. ✅ **Search Service** - User discovery capability
5. ✅ **Authentication Service** - Security layer
6. ✅ **Notification Service** - User engagement
7. ✅ **Content Moderation** - Platform safety
8. ✅ **Analytics Service** - Business intelligence
9. ✅ **Multi-region Strategy** - Global scale & availability
10. ✅ **Observability Stack** - Production operations

### Architecture Improvements:
1. Hybrid push-pull feed generation
2. Multi-tier caching strategy
3. Database specialization (DynamoDB, PostgreSQL, Cassandra, Elasticsearch)
4. Kafka-based event-driven architecture
5. CDN integration with intelligent caching
6. ML-powered recommendation system
7. Comprehensive security layers
8. Cost optimization strategies

### Scalability Enhancements:
1. Horizontal scaling for all services
2. Database sharding strategies
3. Distributed caching with Redis Cluster
4. Auto-scaling policies
5. Load balancing at multiple layers
6. Async processing for heavy operations
7. Connection pooling and circuit breakers
8. Graceful degradation patterns

---

## 17. CONCLUSION

The original design had a good foundation with appropriate technology choices (Kafka, Redis, S3) and showed understanding of scale (1B DAU). However, it was missing critical components like video processing, recommendation engine, proper load balancing, and observability.

The improved design addresses all these gaps while maintaining pragmatic engineering decisions. The architecture is:
- **Scalable**: Handles 1B DAU with proper sharding and caching
- **Resilient**: Multi-region with automatic failover
- **Performant**: <1s video load time, <500ms API response
- **Cost-effective**: Intelligent tiering and optimization strategies
- **Secure**: Multiple security layers with compliance
- **Observable**: Comprehensive monitoring and alerting

This design can serve as a production-ready blueprint for building a global short-video platform like TikTok or Instagram Reels.

---

**Document Version:** 1.0  
**Last Updated:** November 22, 2025  
**Status:** Complete Architecture Review with Improvements
