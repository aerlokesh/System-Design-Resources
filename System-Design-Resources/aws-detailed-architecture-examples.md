# AWS Detailed Architecture Examples - System Design Interviews

## 🎯 Purpose
Deep-dive into real-world system architectures using AWS services with step-by-step breakdowns, data flows, and scaling strategies.

---

## 📊 Table of Contents
1. [Uber/Ride-Sharing Platform](#example-1-uberride-sharing-platform)
2. [Netflix/Video Streaming](#example-2-netflixvideo-streaming-platform)
3. [Amazon/E-commerce Platform](#example-3-amazone-commerce-platform)
4. [WhatsApp/Chat Application](#example-4-whatsappchat-application)
5. [Food Delivery Platform](#example-5-food-delivery-platform-doordash)
6. [Rate Limiter System](#example-6-distributed-rate-limiter)
7. [Distributed Cache](#example-7-distributed-cache-system)

---

## Example 1: Uber/Ride-Sharing Platform

### Requirements
- Match riders with nearby drivers
- Real-time location tracking
- ETA calculations
- Fare calculation
- Payment processing
- Trip history
- 100M users, 10M drivers
- 50M rides/day
- Location updates every 4 seconds

### Capacity Estimation
```
Rides: 50M/day = 578 rides/sec (peak: 3,000 rides/sec)
Location updates: 10M drivers × 900 updates/hour = 2.5M updates/sec
Peak: 5x average = 12.5M location updates/sec
Storage: 50M rides × 5KB = 250GB/day = 91TB/year
```

### Architecture Components

#### Step 1: Location Service (Critical Path)
```
Service: ElastiCache Redis (Cluster Mode) + DynamoDB

Redis Geospatial Structure:
Key: drivers:{city}:{quadrant}
Type: Geo (GEOADD command)
Data: (latitude, longitude, driverId)

Quadrant Sharding (for scalability):
- Divide each city into grid (e.g., 10x10)
- Each quadrant = separate Redis key
- Reduces single-key bottleneck

Commands:
GEOADD drivers:seattle:Q5 -122.33 47.61 driver123 available
GEORADIUS drivers:seattle:Q5 -122.33 47.61 5 km

Why Redis Geo:
✅ Built-in geospatial queries (GEORADIUS)
✅ Sub-millisecond queries
✅ Can handle 12M+ writes/sec (cluster mode)
✅ Automatic expiration (TTL 30 sec = driver offline)

Persistence (DynamoDB):
Table: DriverLocations
PK: driverId
Attributes: lat, lon, timestamp, status, city
GSI: city-timestamp-index

Purpose:
- Historical tracking
- Replay for Redis failure
- Analytics
- Compliance (rider safety)
```

#### Step 2: Matching Algorithm
```
Service: Lambda + SQS + Step Functions

Ride Request Flow:
1. User opens app, requests ride
2. API Gateway → Lambda (Ride Request Handler)
3. Save request to DynamoDB (PendingRides)
4. Invoke Step Functions (Matching Workflow)

Step Functions Workflow:
State 1: Find Nearby Drivers
- Lambda queries Redis GEORADIUS (5km)
- Filter: available, rating >4.5, vehicle type matches
- Calculate ETA for top 10 drivers (external mapping API)
- Sort by ETA

State 2: Send Requests (Parallel)
- SNS publish to top 3 drivers
- Mobile push notification
- Wait for accept (30-second timeout)

State 3: Handle Accept/Timeout
- If accepted → Proceed to State 4
- If timeout → Expand search radius (5km → 10km)
- Retry with next batch of drivers

State 4: Create Ride
- Update DynamoDB: ActiveRides
- Update Redis: driver status = busy, rider status = in-ride
- Start location tracking stream
- Return ride details to user

Why Step Functions:
✅ Visual workflow
✅ Built-in retries
✅ State persistence
✅ Error handling
✅ Easy to modify workflow
```

#### Step 3: Real-time Trip Tracking
```
Service: Kinesis Data Streams + Lambda + WebSockets

Location Tracking:
1. Driver app sends location every 4 seconds
2. API Gateway → Kinesis Data Stream
3. Lambda processes stream:
   - Update Redis (driver location)
   - Async update DynamoDB (every 30 sec)
   - Calculate ETA
   - Push to rider via WebSocket

4. Rider app receives real-time updates:
   - Driver location on map
   - Updated ETA
   - Route visualization

WebSocket Architecture:
- API Gateway WebSocket API
- Connection table in DynamoDB
  PK: connectionId, userId, rideId
- Lambda pushes updates to specific connections

Why Kinesis:
✅ Handle 12M updates/sec
✅ Multiple consumers (tracking, analytics, fraud detection)
✅ Ordered per driver (partition key = driverId)
✅ Replay capability for debugging
```

#### Step 4: Fare Calculation
```
Service: Lambda + DynamoDB

Fare Calculation Lambda:
Inputs:
- Distance (from GPS coordinates)
- Time duration
- Time of day (surge pricing)
- City/region
- Vehicle type
- Historical demand (from Redis)

Formula:
Base fare + (Distance × per-mile) + (Time × per-minute) + Surge multiplier

Surge Pricing:
- Redis: Real-time demand per quadrant
- If rides/drivers ratio > threshold → apply surge
- Redis sorted set: surge:{city} (score = ratio)

Dynamic Pricing Service:
- Separate Lambda, runs every minute
- Analyzes demand/supply ratio
- Updates surge multipliers in Redis
- Predictive: Use SageMaker for demand forecasting
```

#### Step 5: Payment Processing
```
Service: Lambda + SQS FIFO + RDS

Payment Flow:
1. Trip completes
2. Calculate final fare
3. SQS FIFO queue (payment-processing)
   - GroupId: userId (ensures order)
   - Deduplication: rideId
4. Lambda processes payment:
   - Call payment gateway (Stripe API)
   - Retry logic (3 attempts)
   - Update RDS (transactions table)
5. Success:
   - Update DynamoDB ride status
   - Send receipt via SNS (email/push)
6. Failure:
   - Dead Letter Queue
   - Manual review workflow

Why SQS FIFO:
✅ Exactly-once processing (no duplicate charges)
✅ Ordered per user
✅ Automatic retries
✅ DLQ for failures

Why RDS for Payments:
✅ ACID compliance
✅ Complex queries for accounting
✅ Regulatory compliance
✅ Strong consistency
```

#### Step 6: Trip History & Analytics
```
Service: DynamoDB + S3 + Athena

Trip Storage:
1. Active rides → DynamoDB (hot data)
2. Completed rides → S3 (after 7 days)
   - Partitioned by year/month/day
   - Parquet format for efficient querying

User Trip History:
- DynamoDB Global Secondary Index
- Recent trips cached in ElastiCache
- S3 for older trips (query via Athena)

Analytics Pipeline:
1. Kinesis Data Firehose → S3
2. Glue Crawler → Data Catalog
3. Athena for SQL queries:
   - Revenue by city
   - Peak hours analysis
   - Driver efficiency
   - Popular routes
4. QuickSight dashboards for business
```

### Complete Data Flows

#### Request Ride Flow:
```
1. User opens app
   → API Gateway → Lambda
   → Get user info from ElastiCache
   
2. User enters destination
   → Lambda: Calculate estimated fare
   → Display to user
   
3. User confirms
   → Step Functions: Matching workflow starts
   
4. Find drivers:
   → Lambda → Redis GEORADIUS
   → Get available drivers within 5km
   
5. Send requests:
   → SNS → Mobile push to 3 drivers
   → Wait for acceptance (30 seconds)
   
6. Driver accepts:
   → Lambda → Update DynamoDB & Redis
   → WebSocket push to rider (driver details)
   → Start tracking stream
```

#### Active Trip Flow:
```
1. Driver location every 4 seconds
   → Mobile app → API Gateway → Kinesis
   
2. Kinesis → Lambda (Location Processor)
   → Update Redis: driver location
   → Update DynamoDB (every 30 sec)
   → Calculate ETA (external API or Lambda)
   → Push via WebSocket to rider
   
3. Rider app displays:
   → Driver location on map
   → Updated ETA
   → Route path
```

#### Complete Trip Flow:
```
1. Driver arrives at destination
   → App sends "trip complete" event
   → API Gateway → Lambda
   
2. Calculate final fare:
   → Lambda: Fare calculation
   → Consider: actual distance, time, surge
   → Save to DynamoDB (Rides table)
   
3. Process payment:
   → SQS FIFO queue
   → Lambda → Payment gateway
   → Update RDS (transactions)
   
4. Post-trip:
   → SNS → Email receipt to rider
   → Push notification with trip summary
   → Prompt for driver rating
   → Update Redis: driver status = available
   
5. Analytics:
   → Kinesis → S3 (for business intelligence)
   → Update driver earnings in DynamoDB
```

### Scaling Strategies

#### For 10x Traffic (500M rides/day):
```
Compute:
- Lambda: Reserved concurrency (5,000 concurrent)
- ECS: 500+ containers with auto-scaling
- Step Functions: Express workflows for high-throughput

Database:
- DynamoDB: On-demand mode (auto-scales)
- Redis: 50-node cluster (cluster mode)
- RDS: Aurora with 10 read replicas

Storage:
- S3: Automatic scaling
- CloudFront: 400+ edge locations

Network:
- API Gateway: 10,000 RPS per region
- Multiple regions for global coverage
- Route 53 latency routing

Key Optimization:
- Partition Redis by city/quadrant (avoid hot keys)
- DynamoDB: Proper partition key design (avoid hot partitions)
- Cache driver profiles in ElastiCache
- Pre-calculate surge pricing every minute
```

### Cost Estimation (50M rides/day)
```
Compute:
- Lambda: $3,000/month (matching, processing)
- ECS: $5,000/month (API servers)
- Step Functions: $500/month

Database:
- DynamoDB: $5,000/month (on-demand)
- ElastiCache: $8,000/month (50-node cluster)
- RDS Aurora: $2,000/month

Storage:
- S3: $1,000/month (trip history)

Networking:
- API Gateway: $2,000/month
- Kinesis: $3,000/month
- CloudFront: $500/month

Total: ~$30,000/month
Per ride: $0.60
```

### Monitoring & Alerts
```
Critical Metrics:
✅ Match success rate (> 95%)
✅ Average matching time (< 30 seconds)
✅ Location update latency (< 1 second)
✅ Payment success rate (> 99.9%)
✅ Redis GEORADIUS query time (< 10ms)
✅ Driver availability (supply/demand ratio)
✅ ETA accuracy (actual vs predicted)

Alarms:
- Matching failures > 5%
- Redis CPU > 80%
- DynamoDB throttling > 0
- Kinesis iterator age > 10 seconds
- Payment failures > 0.5%
- WebSocket disconnect rate > 10%

Dashboard:
- Active rides (real-time map)
- Available drivers by city
- Surge pricing heat map
- Revenue by region
- Driver utilization rate
```

---

## Example 2: Netflix/Video Streaming Platform

### Requirements
- 200M subscribers
- 100M concurrent users (peak)
- 10,000+ movies/shows
- Multiple quality levels (480p, 720p, 1080p, 4K)
- Continue watching from any device
- Personalized recommendations
- Download for offline viewing
- <2 second video start time

### Capacity Estimation
```
Storage:
- 10,000 titles × 100GB (4K) × 5 quality levels = 5PB
- With encoding variants = 10PB

Bandwidth:
- 100M concurrent × 5 Mbps average = 500 Tbps
- Monthly: 100M users × 100 hours × 5 Mbps = 180 PB

Metadata:
- 10,000 titles × 100KB = 1GB
- User data: 200M × 10KB = 2TB
```

### Architecture Components

#### Step 1: Content Ingestion & Processing
```
Service: S3 + Step Functions + Lambda + MediaConvert

Upload Flow:
1. Studio uploads master file to S3 (videos-raw bucket)
   - 4K ProRes master (100GB+ per movie)
   
2. S3 Event → Step Functions (Encoding Workflow)

3. Workflow Steps:
   State 1: Validation
   - Lambda checks file integrity
   - Validates format, codec
   
   State 2: Parallel Encoding (MediaConvert jobs)
   - 480p (H.264)
   - 720p (H.264)
   - 1080p (H.264)
   - 4K (H.265/HEVC)
   - Audio tracks (multiple languages)
   - Subtitles (VTT format)
   
   State 3: Thumbnail Generation
   - Lambda + FFmpeg layer
   - Extract frames at intervals
   - Create preview clips
   
   State 4: Quality Check
   - Lambda validates output
   - Checks video/audio sync
   - Verifies all variants exist
   
   State 5: Catalog Update
   - Lambda updates DynamoDB (catalog)
   - Metadata: title, description, cast, genre, duration
   - S3 paths for all variants
   
   State 6: CDN Distribution
   - Copy to multiple S3 regions
   - Invalidate CloudFront cache
   - Mark as "available" in catalog

Encoding Time:
- 2-hour movie: ~4 hours for all variants
- Parallel processing: Finish in 1-2 hours

Cost per Title:
- MediaConvert: ~$50-100 for all variants
- S3 storage: ~$30/month per title
```

#### Step 2: Content Delivery Network
```
Service: CloudFront + S3

Video Delivery:
- S3 buckets in multiple regions (us-east-1, eu-west-1, ap-southeast-1)
- CloudFront distributions per region
- 400+ edge locations globally

Adaptive Bitrate Streaming (ABR):
Format: HLS (HTTP Live Streaming)
- Master playlist (.m3u8)
- Multiple quality playlists
- 10-second segments

Client Request Flow:
1. Request master playlist
   → CloudFront → S3
   → Returns available qualities
   
2. Client chooses quality (based on bandwidth)
   → Requests specific quality playlist
   
3. Download video segments
   → CloudFront edge cache
   → If miss → Origin S3
   → Cache at edge (24 hours)
   
4. Adapt quality dynamically
   → Client monitors bandwidth
   → Switches quality seamlessly

Why CloudFront:
✅ 400+ edge locations (< 50ms latency)
✅ Automatic scaling
✅ DDoS protection (Shield)
✅ Cost-effective ($0.085/GB)
✅ Cache hit ratio > 90%
```

#### Step 3: User Data & Profiles
```
Service: DynamoDB + ElastiCache

DynamoDB Tables:

1. Users
PK: userId
Attributes: email, subscription, preferences, devices

2. WatchHistory
PK: userId
SK: titleId-timestamp
Attributes: progress, quality, device, timestamp
GSI: titleId-index (popular content)

3. Catalog
PK: titleId
Attributes: title, description, genre, cast, duration,
           releaseYear, rating, s3Paths
GSI: genre-rating-index

4. Recommendations
PK: userId
SK: titleId-score
Attributes: computed recommendation score
TTL: 24 hours (refresh daily)

Caching Strategy (ElastiCache Redis):
Hot Data:
- User profile: cache for 1 hour
- Watch progress: cache for 10 minutes
- Popular titles metadata: cache for 1 day
- Search results: cache for 1 hour

Cache Keys:
- user:{userId}
- progress:{userId}:{titleId}
- catalog:{titleId}
- search:{query}
- trending:daily (sorted set)
```

#### Step 4: Video Player API
```
Service: API Gateway + Lambda

Endpoints:

1. GET /play/{titleId}
   → Lambda checks subscription status
   → Gets S3 URLs (presigned, expires in 1 hour)
   → Returns master playlist URL
   → Logs start event to Kinesis
   
2. POST /progress
   → Updates watch position in DynamoDB
   → Updates cache
   → For resume playback
   
3. GET /recommendations
   → Gets from ElastiCache
   → If miss → Query DynamoDB
   → Calls SageMaker endpoint (real-time)
   
4. POST /quality-report
   → Client reports playback quality
   → Kinesis → Analytics
   → Optimize CDN settings

Authentication:
- Cognito for user auth
- JWT tokens
- Device fingerprinting (prevent sharing)
```

#### Step 5: Recommendation Engine
```
Service: SageMaker + EMR + S3

Batch Processing (Nightly):
1. Kinesis Firehose → S3 (viewing data)
2. EMR Spark job:
   - Collaborative filtering
   - Content-based filtering
   - Hybrid model
3. Generate recommendations
4. Save to DynamoDB (Recommendations table)
5. Update ElastiCache (precompute top 20)

Real-time Predictions:
- SageMaker endpoint for on-demand
- Called during browsing
- Personalized thumbnails (A/B testing)

Features:
- Watch history
- Ratings
- Time of day
- Device type
- Similar users' behavior
- Content metadata (genre, cast, etc.)

Model Update:
- Retrain weekly with new data
- A/B test new models (50/50 split)
- Monitor engagement metrics
```

#### Step 6: Search Service
```
Service: OpenSearch + DynamoDB

Index Structure:
- Movies/shows (title, description, cast, genre)
- Real-time updates via DynamoDB Streams

Search Features:
1. Full-text search (title, description)
2. Filters (genre, year, rating, language)
3. Autocomplete (as-you-type)
4. Fuzzy matching (typo tolerance)
5. Ranking by relevance + popularity

Caching:
- Popular searches cached in Redis
- TTL: 1 hour
- Reduces OpenSearch load by 70%
```

#### Step 7: Download Feature
```
Service: S3 + CloudFront + DynamoDB

Offline Download:
1. User clicks download
2. API Gateway → Lambda
3. Lambda:
   - Check subscription (allows downloads?)
   - Check download limit (max 10 titles)
   - Generate presigned S3 URL (expires 24 hours)
   - Track download in DynamoDB
4. App downloads video segments
5. Encrypt locally (DRM)
6. Update DynamoDB: downloaded titles per device

DRM (Digital Rights Management):
- Encrypted at rest in S3
- Decryption keys via separate API
- Keys expire after viewing window
- Prevent screenshot/recording (Widevine/FairPlay)
```

### Complete Data Flows

#### Watch Video Flow:
```
1. User selects movie
   → App → API Gateway → Lambda
   
2. Lambda checks:
   → Cognito: User authenticated?
   → DynamoDB: Active subscription?
   → DynamoDB: Parental controls?
   
3. Get video URLs:
   → Lambda generates presigned S3 URLs
   → Different qualities (480p-4K)
   → Expires in 1 hour
   
4. Return master playlist:
   → Client receives HLS playlist URL
   → CloudFront URL (not direct S3)
   
5. Video playback:
   → Client requests playlist → CloudFront
   → CloudFront serves from edge cache
   → Client downloads segments (10 sec each)
   → Adaptive bitrate based on bandwidth
   
6. Progress tracking:
   → Every 30 seconds: POST /progress
   → Update DynamoDB + Redis cache
   
7. Log viewing data:
   → Kinesis Data Streams
   → Lambda → Analytics/ML pipelines
```

#### Recommendation Generation Flow:
```
Batch (Nightly):
1. S3 (previous day's viewing data)
   → EMR Spark job (collaborative filtering)
   → Generate user-item matrix
   → Calculate recommendations
   → Save to DynamoDB
   → Update cache for active users

Real-time (During Browse):
1. User browses catalog
   → API Gateway → Lambda
   → Check ElastiCache for recommendations
   → If miss → Query DynamoDB
   → Call SageMaker endpoint
   → Blend batch + real-time results
   → Cache and return to user
```

### Scaling to 200M Users

```
Global Distribution:
- 5 AWS regions (us-east-1, eu-west-1, ap-southeast-1, sa-east-1, ap-south-1)
- DynamoDB Global Tables (multi-region writes)
- S3 Cross-Region Replication (automatic)
- CloudFront: Serves from nearest edge
- Route 53: Latency-based routing

Content Optimization:
- Pre-encode popular content (top 20%)
- On-demand encoding for long-tail
- Intelligent caching (80/20 rule)
- Regional content libraries

Infrastructure:
- Lambda: 100,000+ concurrent executions
- API Gateway: Regional endpoints
- DynamoDB: On-demand mode
- S3: Multi-region buckets
- ElastiCache: Redis clusters per region
```

### Cost Optimization

```
Storage:
- S3 Intelligent-Tiering: Auto-move to cheaper tiers
- Lifecycle policy: Move to Glacier after 1 year
- Delete unused variants (SD if only 4K watched)

CDN:
- CloudFront: 90% of traffic from cache (cheaper)
- Reserved CloudFront capacity (20% savings)
- Optimize caching policies

Compute:
- Use Spot for encoding jobs (70% savings)
- Reserved capacity for steady workloads
- Optimize Lambda memory allocation

Estimated Cost (200M users):
- CDN: $15M/month (180PB)
- Storage: $3M/month (10PB + lifecycle)
- Compute: $2M/month
- Database: $1M/month
- Total: ~$21M/month
- Per user: $0.11/month
```

### Monitoring

```
Critical Metrics:
✅ Video start time (p99 < 2 seconds)
✅ Buffering ratio (< 0.1%)
✅ CDN cache hit rate (> 90%)
✅ Encoding job completion (< 4 hours)
✅ API latency (p99 < 100ms)
✅ Error rate (< 0.01%)
✅ Concurrent streams

Quality of Experience:
- Startup time by region
- Buffering events per stream
- Quality adaptation frequency
- Completion rate

Business Metrics:
- Daily/monthly active users
- Watch time per user
- Most popular content
- Revenue per user
- Churn prediction
```

---

## Example 3: Amazon/E-commerce Platform

### Requirements
- Product catalog (100M products)
- Shopping cart
- Checkout & payment
- Order tracking
- Search & filters
- 500M users
- 10M orders/day
- Peak: 1M concurrent users (Prime Day)
- <200ms page load time

### Capacity Estimation
```
Products: 100M × 10KB = 1TB
Orders: 10M/day × 5KB = 50GB/day = 18TB/year
Images: 100M products × 10 images × 100KB = 100TB
Peak Traffic: 1M concurrent × 10 page views = 10M requests/sec
```

### Architecture Components

####  Step 1: Product Catalog
```
Service: DynamoDB + ElastiCache + S3 + CloudFront

DynamoDB Tables:

1. Products
PK: productId
Attributes: name, description, price, category, brand,
           images[], specifications{}, inventory
GSI: category-price-index
GSI: brand-index

2. Inventory
PK: productId-warehouseId
Attributes: quantity, reserved, available
- Strong consistency required

Caching Strategy (Redis):
L1 Cache: Product details (1 hour TTL)
- Popular products always in cache
- LRU eviction for others

L2 Cache: Search results (30 min TTL)
- Common queries cached
- Invalidate on price/inventory change

Images:
- S3: Original high-res images
- Lambda@Edge: Resize on-demand
- CloudFront: Deliver resized images
- WebP format for modern browsers
```

#### Step 2: Search & Discovery
```
Service: OpenSearch + DynamoDB Streams

Indexing:
1. Product changes in DynamoDB
2. DynamoDB Stream → Lambda
3. Lambda updates OpenSearch index
4. Near real-time (<1 second lag)

Search Features:
- Full-text search (name, description)
- Faceted filters (category, brand, price range, rating)
- Autocomplete (suggest as you type)
- Typo tolerance (fuzzy matching)
- Synonyms (phone = mobile = cell)
- Boost popular products

Ranking Algorithm:
- Relevance score (text match)
- Product rating
- Sales volume
- Profit margin (business logic)
- Personalization (user history)

Performance:
- OpenSearch cluster: 10 nodes
- Response time: < 50ms
- Handle 10,000 QPS
```

#### Step 3: Shopping Cart
```
Service: DynamoDB + ElastiCache

Cart Storage:

Option 1: DynamoDB (Persistent)
Table: Carts
PK: userId
Attributes: items[], lastModified, savedForLater[]
- Items: {productId, quantity, price, addedAt}

Option 2: Redis (Fast, Session-based)
Key: cart:{userId}
Type: Hash
Fields: productId → {quantity, price, timestamp}
TTL: 30 days (abandoned cart cleanup)

Why Both:
- Redis: Fast access during browsing (<1ms)
- DynamoDB: Persistence, cross-device sync
- Async sync: Redis → DynamoDB every 5 minutes

Cart Operations:
ADD: HINCRBY cart:{userId} product123 1
REMOVE: HDEL cart:{userId} product123
GET: HGETALL cart:{userId}
COUNT: HLEN cart:{userId}

Benefits:
✅ Sub-millisecond cart operations
✅ Survives browser close (DynamoDB)
✅ Real-time inventory check
✅ Price validation on checkout
```

#### Step 4: Checkout & Payment
```
Service: Step Functions + Lambda + RDS + SQS FIFO

Checkout Workflow (Step Functions):

State 1: Validate Cart
- Lambda checks each item:
  → Still in stock?
  → Price unchanged?
  → Eligible for shipping?
- If validation fails → Return errors

State 2: Reserve Inventory
- Lambda: Update DynamoDB inventory
  → Atomic decrement available quantity
  → Add to reserved (15-minute hold)
- If any item unavailable → Rollback

State 3: Calculate Total
- Lambda: Sum items + tax + shipping
- Apply promo codes
- Calculate final amount

State 4: Process Payment
- Lambda → Payment gateway API (Stripe/Adyen)
- Retry 3 times on failure
- If failure → Release inventory

State 5: Create Order
- Save to RDS (orders table)
- ACID transaction critical
- Generate order ID

State 6: Confirm
- Release inventory reservation
- Finalize inventory deduction
- Send confirmation:
  → SNS → Email/SMS
  → SQS → Shipping service
  → Update user's order history

Why Step Functions:
✅ Visual workflow
✅ Built-in error handling
✅ State persistence
✅ Easy rollback logic
✅ Audit trail

Why RDS for Orders:
✅ ACID compliance (critical for money)
✅ Complex queries (order history, refunds)
✅ Joins (orders, items, users)
✅ Financial reporting
✅ Regulatory compliance
```

#### Step 5: Order Management
```
Service: RDS (Aurora) + DynamoDB + SQS

Database Schema (RDS):

Tables:
1. Orders
   - orderId (PK)
   - userId, totalAmount, status, createdAt
   - shippingAddress, billingAddress

2. OrderItems
   - orderItemId (PK)
   - orderId (FK)
   - productId, quantity, price

3. Payments
   - paymentId (PK)
   - orderId (FK)
   - amount, method, status, transactionId

4. Shipments
   - shipmentId (PK)
   - orderId (FK)
   - carrier, trackingNumber, status

Why RDS (not DynamoDB):
✅ Complex joins for order details
✅ ACID for payment operations
✅ Reporting queries
✅ Multi-table transactions

Order Status Updates:
1. Warehouse → SQS → Lambda
2. Update RDS order status
3. Update DynamoDB (for fast user queries)
4. Cache in Redis (active orders)
5. SNS → Email notification
6. WebSocket → Real-time app update
```

#### Step 6: Recommendation Engine
```
Service: SageMaker + EMR + S3 + Lambda

Data Pipeline:
1. User interactions → Kinesis → S3
   - Views, searches, purchases, cart adds
   
2. Daily Batch (EMR Spark):
   - Collaborative filtering
   - Content-based filtering
   - Frequently bought together
   - Recently viewed
   - Trending in category
   
3. Model Training (SageMaker):
   - Matrix factorization
   - Deep learning (neural collaborative filtering)
   - Feature engineering
   
4. Predictions:
   - Batch: EMR generates recommendations → DynamoDB
   - Real-time: SageMaker endpoint for browse page
   
5. Serving:
   - API Gateway → Lambda
   → Check cache
   → Query DynamoDB
   → Call SageMaker (if needed)
   → Blend results
   → Cache and return

Personalization:
- Homepage: Personalized for each user
- Search results: Ranked by purchase probability
- Product page: "You may also like"
- Email: Personalized product suggestions
```

#### Step 7: Inventory Management
```
Service: DynamoDB + Lambda + EventBridge

Real-time Inventory:
Table: Inventory
PK: productId-warehouseId
Attributes: total, available, reserved, restockDate

Operations (Atomic):
- Reserve: available -= qty, reserved += qty
- Purchase: reserved -= qty
- Restock: total += qty, available += qty
- Cancel: reserved -= qty, available += qty

Low Stock Alerts:
1. DynamoDB Stream → Lambda
2. If available < threshold
3. EventBridge → SNS → Inventory team
4. Auto-reorder from suppliers (API call)

Multi-warehouse:
- Each warehouse separate partition
- Allocate inventory based on:
  → User location
  → Stock levels
  → Shipping cost
```

### Complete Data Flows

#### Browse Product Flow:
```
1. User searches "laptop"
   → API Gateway → Lambda
   
2. Check cache:
   → Redis: search:laptop
   → If hit → Return cached results
   
3. Cache miss:
   → Query OpenSearch
   → Rank by relevance + popularity
   → Get top 100 results
   
4. Enrich results:
   → Batch get from DynamoDB (prices, inventory)
   → Get images from CloudFront
   → Check user's wish list (ElastiCache)
   → Personalize ranking (SageMaker)
   
5. Cache results:
   → Redis: search:laptop
   → TTL: 30 minutes
   
6. Return to user
```

#### Add to Cart Flow:
```
1. User clicks "Add to Cart"
   → API Gateway → Lambda
   
2. Validate:
   → Check inventory in DynamoDB
   → Verify price hasn't changed
   
3. Update cart:
   → Redis HINCRBY cart:{userId} productId quantity
   → Async write to DynamoDB
   
4. Return updated cart:
   → Get all items from Redis
   → Calculate subtotal
   → Return to user (<50ms)
```

#### Checkout & Order Flow:
```
1. User proceeds to checkout
   → API Gateway → Step Functions
   
2. Validate cart (State 1):
   → Lambda gets cart from Redis
   → Verify each item:
     • Still in stock?
     • Price unchanged?
     • Eligible for shipping address?
   → Calculate tax (based on location)
   → Calculate shipping cost
   
3. Reserve inventory (State 2):
   → Lambda: DynamoDB atomic operations
   → For each item:
     UPDATE Inventory
     SET available = available - :qty,
         reserved = reserved + :qty
     WHERE available >= :qty
   → If any fails → Rollback all
   → Reservation expires in 15 minutes
   
4. Payment (State 3):
   → SQS FIFO → Lambda
   → Call payment gateway
   → Tokenize card (don't store)
   → Process payment
   → Retry on failure (3 attempts)
   → Store transaction in RDS
   
5. Create order (State 4):
   → RDS transaction:
     INSERT INTO Orders...
     INSERT INTO OrderItems...
     INSERT INTO Payments...
   → Generate orderId
   
6. Confirm (State 5):
   → Finalize inventory (reserved → sold)
   → Clear Redis cart
   → DynamoDB: User order history
   → SNS: Email confirmation
   → SQS: Shipping queue
   → WebSocket: Real-time notification
   
7. Trigger fulfillment:
   → EventBridge rule
   → Lambda → Warehouse management system
   → Generate packing slip
   → Update estimated delivery
```

### Scaling for Prime Day (10x Traffic)

```
Pre-scaling:
1 week before:
- Increase RDS read replicas (5 → 15)
- Scale Redis clusters (20 → 100 nodes)
- DynamoDB: Switch to on-demand
- Pre-warm CloudFront cache (popular products)
- Increase Lambda reserved concurrency
- Add EC2 spot instances for batch jobs

During event:
- Monitor CloudWatch dashboards
- Auto-scaling triggers
- Kinesis shards auto-scale
- API Gateway: 100K RPS per region

Regional Distribution:
- Deploy to 5 regions
- Route 53 latency routing
- DynamoDB Global Tables
- S3 cross-region replication

Queue Management:
- SQS: Handle 1M orders/hour
- Lambda: Process in parallel (10K concurrent)
- Dead letter queues for failures
- Retry logic with exponential backoff
```

### Cost Optimization

```
Compute:
- Spot instances for batch processing (70% savings)
- Reserved instances for baseline (40% savings)
- Lambda: Optimize memory (cost/performance)

Storage:
- S3 Intelligent-Tiering for product images
- Compress images (WebP)
- CDN caching reduces S3 GET requests

Database:
- DynamoDB: Provisioned for baseline, on-demand for spikes
- Reserved capacity (40% savings)
- RDS: Reserved instances

Estimated Cost (500M users, 10M orders/day):
- Compute: $100,000/month
- Database: $150,000/month
- Storage: $50,000/month
- CDN: $80,000/month
- Search: $30,000/month
- Total: ~$410,000/month
- Per order: $4.10
```

### Monitoring

```
Critical Metrics:
✅ Checkout success rate (> 98%)
✅ Page load time (p99 < 200ms)
✅ Search response time (p99 < 100ms)
✅ Add to cart success (> 99.9%)
✅ Payment success (> 99.5%)
✅ Inventory accuracy (99.9%)
✅ Order processing time (< 30 seconds)

Alarms:
- Checkout failures > 2%
- Out of stock errors spike
- Payment gateway timeout
- Search latency > 200ms
- Database connection pool exhausted
- Lambda concurrent execution limit

Dashboard:
- Orders per minute
- Revenue (real-time)
- Top selling products
- Inventory alerts
- Cart abandonment rate
- Regional performance
```

---

## Example 4: WhatsApp/Chat Application

### Requirements
- 1-on-1 chat
- Group chat (up to 256 members)
- Media sharing (photos, videos, documents)
- End-to-end encryption
- Online status
- 2B users, 100M concurrent
- 100B messages/day
- <100ms message delivery

### Capacity Estimation
```
Messages: 100B/day = 1.16M messages/sec (peak: 5M/sec)
Media: 20% messages = 20B × 1MB = 20PB/day
Storage: 1 year = 7,300PB (with compression ~3,650PB)
Connections: 100M concurrent WebSocket connections
```

### Architecture Components

#### Step 1: WebSocket Connection Management
```
Service: API Gateway WebSocket + DynamoDB + ElastiCache

Connection Handling:
1. User opens app
2. WebSocket connection to API Gateway
3. Lambda (connect handler):
   - Authenticate via Cognito/JWT
   - Store connection in DynamoDB
     Table: Connections
     PK: userId
     SK: connectionId-deviceId
     Attributes: connectedAt, region
   - Cache in Redis: online:{userId} = {connectionIds[]}
   - Broadcast "online" status to contacts
4. Heartbeat every 30 seconds
   - Keep connection alive
   - Update "last seen"

Disconnection:
- Lambda (disconnect handler)
- Remove from DynamoDB & Redis
- Broadcast "offline" status

Multi-Device Support:
- User can have multiple active connections
- Message delivered to all devices
- Redis set: connections:{userId} = [conn1, conn2, conn3]

Why API Gateway WebSocket:
✅ Manages connections automatically
✅ Auto-scales to millions
✅ Built-in authentication
✅ $1 per million messages
```

#### Step 2: Message Storage
```
Service: DynamoDB + S3

Message Tables:

1. Messages
PK: conversationId (userId1_userId2 sorted)
SK: messageId-timestamp
Attributes: senderId, content, type, status, mediaUrl
TTL: 90 days (auto-delete old messages)
- Encrypted at rest

2. GroupMessages
PK: groupId
SK: messageId-timestamp
Attributes: senderId, content, type, status
- Same structure as 1-on-1

3. MessageIndex (for search)
PK: userId
SK: conversationId-timestamp
Attributes: messageId, snippet, unread

Why DynamoDB:
✅ Unlimited scale (1.16M writes/sec easy)
✅ Single-digit ms latency
✅ TTL for automatic cleanup
✅ DynamoDB Streams for indexing

Media Storage:
- Photos/videos → S3
- Encrypted at rest (KMS)
- Compressed (reduce storage)
- CloudFront for delivery
- Presigned URLs (expire in 1 hour)
```

#### Step 3: Message Delivery
```
Service: API Gateway WebSocket + Lambda + SQS

Send Message Flow:
1. Sender app → WebSocket → API Gateway
2. API Gateway → Lambda (message handler)
3. Lambda:
   a) Validate message (size, content)
   b) Encrypt message (client-side keys)
   c) Save to DynamoDB
   d) Get recipient connections from Redis
   e) For each connection:
      - If online → Push via WebSocket immediately
      - If offline → SQS queue for retry
4. Acknowledgment to sender (delivered)

Group Message Delivery:
1. Save message to DynamoDB (GroupMessages)
2. Get all group members from cache
3. Fanout via SQS (one message per member)
4. Lambda workers deliver to each user
5. Track delivery status per user

Message Status:
- Sent: Saved to DB
- Delivered: Pushed to recipient device
- Read: Recipient opened message
- Status stored in DynamoDB
- Real-time updates via WebSocket

Offline Messages:
- SQS FIFO queue per user
- When user comes online:
  - Pull from queue
  - Deliver via WebSocket
  - Mark as delivered
```

#### Step 4: Media Upload/Download
```
Service: S3 + Lambda + CloudFront

Upload Flow:
1. User selects photo
2. App → API Gateway → Lambda
3. Lambda generates presigned S3 URL (POST)
4. App uploads directly to S3
5. S3 Event → Lambda:
   - Create thumbnail
   - Compress (if needed)
   - Scan for malware
   - Generate download presigned URL
6. Save URL in DynamoDB message
7. Deliver message via WebSocket

Download Flow:
1. Recipient receives message with mediaUrl
2. App downloads from CloudFront
3. CloudFront → S3 (if not cached)
4. Decrypt locally (E2E encryption)

Benefits:
✅ Direct S3 upload (no API Gateway limits)
✅ CloudFront: Fast global delivery
✅ Automatic compression
✅ Malware scanning
```

#### Step 5: Online Presence
```
Service: ElastiCache Redis + API Gateway WebSocket

Online Status:
Redis Structure:
Key: online:{userId}
Type: String ("online" or timestamp of last seen)
TTL: 2 minutes (auto-expire if no heartbeat)

Status Updates:
1. WebSocket heartbeat → Lambda
2. Update Redis: SET online:{userId} "online" EX 120
3. Notify contacts:
   - Redis: Get user's contacts
   - For each online contact:
     → Push status via WebSocket

Last Seen:
- If user goes offline
- Redis: SET online:{userId} {timestamp} EX 86400
- Contacts see "last seen at X"

Typing Indicators:
- Real-time via WebSocket
- Not persisted
- Redis pub/sub for group chats
```

#### Step 6: Group Chats
```
Service: DynamoDB + ElastiCache + SQS

Group Management:
Table: Groups
PK: groupId
Attributes: name, creator, members[], createdAt, admins[]

Message Delivery (Optimized):
1. Message sent to group
2. Save to DynamoDB (GroupMessages)
3. Fan-out strategy:
   - Small groups (<10): Direct WebSocket push
   - Large groups (>10): SQS queue + Lambda workers
   
4. Each Lambda worker:
   - Gets batch of members
   - Checks online status in Redis
   - Delivers to online members
   - Queues for offline members

Optimization for Large Groups:
- Don't wait for all deliveries
- Async delivery via SQS
- Batch notifications
- Sender gets "sent" immediately
```

#### Step 7: Message Search
```
Service: DynamoDB + ElastiCache

Search Strategy:
Recent messages (30 days):
- Scan DynamoDB with filters
- Cache common searches in Redis

Older messages:
- Export to S3 (after 30 days)
- Use Athena for search
- Slower but cost-effective

Full-text search:
- Optional: OpenSearch for advanced search
- Indexed by DynamoDB Streams
- Search across all conversations
```

### Complete Data Flows

#### Send 1-on-1 Message:
```
1. User types message, hits send
   → WebSocket → API Gateway → Lambda
   
2. Lambda (Message Handler):
   → Encrypt message (E2E)
   → Generate messageId
   → Save to DynamoDB:
     conversationId = sort(user1_user2)
     SK = messageId-timestamp
   
3. Deliver to recipient:
   → Check Redis: online:{recipientId}
   → If online:
     • Get connectionIds from Redis
     • API Gateway → Push via WebSocket
     • Lambda handles acknowledgment
   → If offline:
     • SQS queue: pending-messages:{recipientId}
     • Deliver when user comes online
     • Push notification via SNS
   
4. Update status:
   → DynamoDB: message status = delivered
   → WebSocket to sender (double checkmark)
```

#### Send Group Message:
```
1. User sends message to group (100 members)
   → WebSocket → API Gateway → Lambda
   
2. Save message:
   → DynamoDB: GroupMessages table
   → PK: groupId, SK: messageId-timestamp
   
3. Get group members:
   → Redis: members:{groupId}
   → If miss → DynamoDB
   
4. Fan-out (parallel):
   → SNS topic → 100 SQS queues (one per member)
   → Lambda workers (50 concurrent)
   → Each worker:
     • Checks if member online
     • Delivers via WebSocket (if online)
     • Queues for later (if offline)
   
5. Track delivery:
   → DynamoDB: MessageDelivery table
   → PK: messageId, SK: userId
   → Status: sent, delivered, read
```

#### User Comes Online:
```
1. App opens → WebSocket connects
   → API Gateway → Lambda (connect handler)
   
2. Lambda:
   → Update Redis: online:{userId} = "online"
   → Get pending messages from SQS
   → Deliver all pending messages
   → Mark as delivered in DynamoDB
   
3. Sync messages:
   → Get latest messages from DynamoDB
   → Messages since last_message_id
   → Push via WebSocket
   
4. Notify contacts:
   → Get user's contacts from cache
   → For each online contact:
     • Push "user online" via WebSocket
```

### Scaling to 2B Users

```
Global Distribution:
- 10 AWS regions worldwide
- Regional API Gateway endpoints
- DynamoDB Global Tables
- S3: Multi-region buckets
- Route 53: Geoproximity routing

WebSocket Connections:
- API Gateway: 100M connections per region
- Shard by user geography
- Connection affinity (user stays on same server)

Message Delivery:
- Lambda: 1M concurrent executions
- SQS: Unlimited throughput
- DynamoDB: On-demand mode

Media Storage:
- S3: Automatic scaling (petabytes)
- CloudFront: 400+ edge locations
- Intelligent-Tiering: Cost optimization

Infrastructure:
- Multi-region active-active
- Cross-region latency < 100ms
- Regional failover (Route 53)
```

### Cost Estimation (2B users, 100B messages/day)

```
Compute:
- Lambda: $50,000/month (message processing)
- API Gateway WebSocket: $100,000/month

Database:
- DynamoDB: $200,000/month (100B messages)
- ElastiCache: $50,000/month (presence, cache)

Storage:
- S3: $300,000/month (media, 20PB)
- CloudFront: $200,000/month

Networking:
- Data transfer: $100,000/month

Total: ~$1M/month
Per user: $0.0005/month ($0.50 per 1000 users)
```

### Monitoring

```
Critical Metrics:
✅ Message delivery time (p99 < 100ms)
✅ WebSocket connection success rate (> 99.9%)
✅ Message delivery success rate (> 99.99%)
✅ Media upload success rate (> 99.9%)
✅ API latency (p50 < 50ms)
✅ Active connections count
✅ Messages per second

Alarms:
- Message delivery failures > 0.01%
- WebSocket disconnections spike
- Lambda errors > 0.1%
- DynamoDB throttling > 0
- S3 upload failures > 0.5%
- High SQS queue depth

Dashboard:
- Messages per second (real-time)
- Active users by region
- Message delivery latency histogram
- Storage usage trend
- Cost per message
```

---

## Example 5: Food Delivery Platform (DoorDash)

### Requirements
- Browse restaurants
- Place orders
- Real-time order tracking
- Driver (Dasher) matching
- Multiple restaurants per order
- 50M users, 5M Dashers
- 20M orders/day
- Average 30-minute delivery

### Capacity Estimation
```
Orders: 20M/day = 231 orders/sec (peak: 1,500/sec)
Active deliveries: 500K concurrent
Dasher locations: 5M × 900 updates/hour = 1.4M updates/sec
Storage: 20M orders × 10KB = 200GB/day = 73TB/year
```

### Architecture Components

#### Step 1: Restaurant Catalog
```
Service: DynamoDB + ElastiCache + OpenSearch

DynamoDB Tables:

1. Restaurants
PK: restaurantId
Attributes: name, address, cuisine, rating, priceRange,
           deliveryTime, isOpen, menuId
GSI: location-cuisine-index (geohash for location queries)
GSI: cuisine-rating-index

2. Menu Items
PK: restaurantId
SK: itemId
Attributes: name, description, price, category, image,
           available, preparationTime

3. Restaurant Hours
PK: restaurantId
SK: dayOfWeek
Attributes: openTime, closeTime, isClosed

Caching:
- Popular restaurants: Redis (1 hour)
- Menu items: Redis (30 minutes)
- Restaurant search results: Redis (15 minutes)

Location-Based Search:
- Geohash in DynamoDB GSI
- OpenSearch for text search + filters
- Combine results and rank by:
  → Distance
  → Rating
  → Delivery time
  → Previous orders (personalization)
```

#### Step 2: Order Placement
```
Service: Step Functions + Lambda + RDS

Order Workflow:

State 1: Validate Order
- Check restaurant is open
- Verify menu items available
- Calculate subtotal
- Validate delivery address

State 2: Calculate Fees
- Delivery fee (based on distance)
- Service fee
- Taxes
- Surge pricing (if applicable)
- Apply promo codes

State 3: Payment Authorization
- Authorize (not capture) amount
- Use Stripe/Adyen
- Hold funds temporarily

State 4: Create Order
- Save to RDS (ACID required)
- orderId, userId, restaurantId, items, total, status
- Store in both RDS and DynamoDB
  → RDS: Complex queries, reporting
  → DynamoDB: Fast status updates

State 5: Notify Restaurant
- SQS → Restaurant tablet
- Print order in kitchen
- Start preparation timer
- Restaurant confirms via tablet

State 6: Find Dasher
- Invoke matching algorithm
- Similar to Uber logic
- Redis GEORADIUS for nearby Dashers
- Consider: rating, acceptance rate, orders in hand

State 7: Confirm
- Return orderId to user
- Start order tracking
- WebSocket for real-time updates
```

#### Step 3: Dasher Matching
```
Service: Lambda + Redis + Step Functions

Matching Logic:
1. Order ready for pickup
2. Step Functions (Dasher Matching)

State 1: Find Eligible Dashers
- Redis GEORADIUS (restaurant location, 5km)
- Filter:
  → Status = available
  → Rating > 4.5
  → Not at maximum orders (typically 3)
  → Vehicle type appropriate (bike, car, scooter)

State 2: Calculate Batching Opportunities
- Check for nearby orders going same direction
- Batch up to 3 orders per Dasher
- Optimize route efficiency

State 3: Offer to Dashers
- Send push notification to top 5 Dashers
- Include: earnings, distance, estimated time
- Wait for acceptance (20 seconds)
- If timeout, try next batch

State 4: Assign Order
- Update DynamoDB: orderId → dasherId
- Update Redis: dasher status and location
- Notify restaurant (Dasher assigned)
- Notify customer (Dasher on the way)
- Start tracking

Batching Algorithm:
- Use k-means clustering for nearby pickups
- TSP (Traveling Salesman) for optimal route
- Balance: efficiency vs customer wait time
```

#### Step 4: Real-time Tracking
```
Service: Kinesis + Lambda + WebSocket + Redis

Tracking Flow:
1. Dasher app sends location (every 5 seconds)
   → API Gateway → Kinesis Data Stream
   
2. Lambda (Location Processor):
   → Update Redis: dasher:{dasherId}
   → Calculate ETA (distance/speed)
   → Update DynamoDB (every 30 sec)
   
3. Push to customers:
   → Get affected orders from Redis
   → For each order's customer:
     • Get connectionId from cache
     • Push location update via WebSocket
     • Update ETA
   
4. Geofencing:
   → When Dasher near restaurant:
     • Notify restaurant (pickup in 2 min)
   → When Dasher near customer:
     • Notify customer (arrival in 2 min)
   → Implemented via Lambda checking coordinates

Multi-Order Tracking:
- Dasher has 3 orders
- Each customer sees:
  → Dasher location
  → ETA to their delivery
  → Other stops (privacy: just count, not addresses)
```

#### Step 5: Order Status Management
```
Service: DynamoDB + Lambda + SNS + WebSocket

Order States:
1. Placed → Restaurant notified
2. Confirmed → Restaurant accepted
3. Preparing → Cooking in progress
4. Ready for Pickup → Dasher dispatched
5. Picked Up → Dasher has order
6. On the Way → Dasher en route to customer
7. Delivered → Order complete
8. Cancelled → Any time before pickup

State Transitions:
- Restaurant tablet updates status
- DynamoDB: Update order status
- DynamoDB Stream → Lambda
- Lambda:
  → Update cache in Redis
  → Push notification to customer (WebSocket)
  → SMS/push notification
  → Log to Kinesis (analytics)

Why DynamoDB:
✅ Fast writes for status updates
✅ DynamoDB Streams for event-driven
✅ TTL for old orders
✅ Global Tables for multi-region
```

#### Step 6: Payment Capture
```
Service: Lambda + SQS FIFO + RDS

Payment Flow:
1. Order delivered
2. Dasher marks "delivered" in app
3. Customer confirms delivery
4. SQS FIFO queue (payment-capture)
5. Lambda:
   - Capture authorized payment
   - Split payment:
     • Restaurant earnings
     • Dasher earnings
     • Platform fee
   - Update RDS (transactions table)
   - Update Dasher/Restaurant balances
6. Generate receipts
7. SNS → Email to customer
8. Update order status to "completed"

Refunds/Cancellations:
- Separate SQS queue
- Step Functions workflow:
  → Validate refund reason
  → Calculate refund amount
  → Process refund via payment gateway
  → Update all relevant records
  → Notify user
```

### Complete Data Flows

#### Place Order Flow:
```
1. User browses restaurants
   → API → Lambda → OpenSearch (location search)
   → ElastiCache (popular restaurants)
   → Return list with ETA
   
2. User selects restaurant
   → Lambda → DynamoDB (get menu)
   → ElastiCache (cache hit for popular items)
   → Return menu
   
3. User adds items to cart
   → Redis: cart:{userId}:{restaurantId}
   → Real-time total calculation
   
4. User checks out
   → Step Functions (Order Workflow)
   → Validate, calculate fees, authorize payment
   → Create order in RDS + DynamoDB
   
5. Notify restaurant
   → SQS → Restaurant system
   → Display on tablet
   → Sound alert
   
6. Restaurant confirms
   → Prep time: 15 minutes
   → Start Dasher matching in 10 minutes
   → Customer notified via WebSocket
```

#### Delivery Flow:
```
1. Order ready
   → Restaurant marks ready in tablet
   → EventBridge event
   → Step Functions (Dasher Matching)
   
2. Dasher accepts
   → Update Redis & DynamoDB
   → Push to customer (Dasher assigned)
   → Push to restaurant (Dasher ETA)
   
3. Dasher picks up
   → Marks "picked up" in app
   → DynamoDB update
   → Customer notification
   → Start delivery tracking
   
4. Real-time tracking:
   → Dasher location → Kinesis
   → Lambda → Redis → WebSocket to customer
   → ETA updates
   
5. Dasher arrives:
   → Geofence trigger
   → Notify customer (arriving now)
   → Customer meets Dasher
   
6. Mark delivered:
   → Dasher marks in app
   → Customer confirms
   → Capture payment
   → Send receipt
   → Complete order
```

### Scaling Strategies

```
For 200M orders/day (10x):

Compute:
- Lambda: 100K concurrent executions
- Step Functions: Express workflows
- ECS: API servers in multiple regions

Database:
- DynamoDB: On-demand mode
- Redis: 100-node cluster
- RDS: Aurora with 20 read replicas

Location Updates:
- Kinesis: 100 shards
- Lambda: Parallel processing
- Redis: Cluster mode (sharding)

Regional:
- 10 regions globally
- Latency-based routing
- DynamoDB Global Tables
- Local Dasher pools per city
```

### Cost Estimation (20M orders/day)

```
Compute:
- Lambda: $10,000/month
- ECS: $8,000/month
- Step Functions: $2,000/month

Database:
- DynamoDB: $15,000/month
- ElastiCache: $10,000/month
- RDS: $5,000/month

Networking:
- API Gateway: $5,000/month
- Kinesis: $8,000/month

Storage:
- S3: $2,000/month

Total: ~$65,000/month
Per order: $3.25
```

### Monitoring

```
Critical Metrics:
✅ Order placement success rate (> 99%)
✅ Dasher matching time (< 5 minutes)
✅ Delivery time accuracy (± 5 minutes)
✅ Customer satisfaction score
✅ Restaurant tablet response time
✅ Payment success rate (> 99.9%)

Alarms:
- Order failures > 1%
- No Dashers available > 5%
- Payment failures spike
- Location update lag > 10 seconds
- Customer wait time > 60 minutes

Dashboard:
- Active orders map (real-time)
- Available Dashers by city
- Average delivery time
- Order volume by hour
- Restaurant performance
```

---

## Example 6: Distributed Rate Limiter

### Requirements
- Limit API requests per user
- Multiple rate limit rules (per hour, per day)
- Distributed across regions
- < 1ms overhead
- Handle 1M requests/sec
- Accurate counting

### Architecture

#### Step 1: Rate Limit Storage
```
Service: ElastiCache Redis (Cluster Mode)

Token Bucket Algorithm:
Key: ratelimit:{userId}:{api}:{window}
Type: String (counter)
TTL: Window duration

Operations:
1. INCR ratelimit:user123:api:hour
2. If count > limit → Reject (429 Too Many Requests)
3. If count <= limit → Allow

Sliding Window:
Key: ratelimit:user123:api:sliding
Type: Sorted Set (score = timestamp)
Operations:
1. ZREMRANGEBYSCORE (remove old entries)
2. ZADD (add current request)
3. ZCARD (count in window)
4. If count > limit → Reject

Why Redis:
✅ Sub-millisecond operations
✅ Atomic INCR
✅ Automatic TTL
✅ Can handle 1M+ ops/sec per node

Multi-Region:
- Regional Redis clusters
- Eventual consistency acceptable
- Sync via DynamoDB Streams (if needed)
```

#### Step 2: Rate Limit Rules
```
Service: DynamoDB + Lambda

Rules Table:
PK: apiEndpoint
Attributes: limits[] = [{window: 'hour', count: 1000}, 
                        {window: 'day', count: 10000}]

Rule Evaluation:
1. API Gateway → Lambda (authorizer)
2. Get rules from cache or DynamoDB
3. Check each limit in Redis
4. If any exceeded → Return 429
5. If all pass → Allow request

Custom Rules:
- Per user tier (free, premium, enterprise)
- Per API endpoint
- Per IP address
- Per API key
```

#### Step 3: API Gateway Integration
```
Service: API Gateway + Lambda Authorizer

Flow:
1. Request → API Gateway
2. Lambda Authorizer:
   - Extract userId/API key
   - Check rate limits in Redis
   - Return Allow/Deny policy
3. If allowed → Forward to backend
4. If denied → Return 429 with Retry-After header

Response Headers:
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 1635724800
```

#### Complete Flow
```
Request comes in:
1. API Gateway receives request
2. Lambda Authorizer triggered
3. Authorizer:
   → Parse userId from token
   → Get rate limit rules from cache
   → For each rule:
     • Redis INCR for counter
     • Check if exceeded
   → Return IAM policy (Allow/Deny)
4. If Allowed:
   → Forward to Lambda/ALB backend
5. If Denied:
   → Return 429 Too Many Requests
   → Include Retry-After header
```

### Scaling

```
For 10M requests/sec:
- Redis: 100-node cluster
- Geo-distributed (5 regions)
- Local rate limiting per region
- Lambda: 10K concurrent authorizers
- API Gateway: Regional endpoints
```

---

## Example 7: Distributed Cache System

### Requirements
- Key-value cache
- < 1ms latency
- 1M operations/sec
- TTL support
- LRU eviction
- Multi-region
- 99.99% availability

### Architecture

```
Service: ElastiCache Redis (Cluster Mode) + DynamoDB

Cache Architecture:
L1: Application-level cache (local)
- In-memory cache in each service
- 100MB per instance
- TTL: 60 seconds

L2: Redis (Regional)
- ElastiCache cluster (10 nodes)
- Sharded by key
- TTL support
- Replication for HA

L3: DynamoDB (Persistent)
- Source of truth
- Slower but durable
- Used for cache warming

Cache Operations:
GET:
1. Check L1 cache
2. If miss → Check L2 (Redis)
3. If miss → Get from L3 (DynamoDB)
4. Populate L1 and L2
5. Return value

SET:
1. Write to L1
2. Async write to L2 (Redis)
3. Async write to L3 (
