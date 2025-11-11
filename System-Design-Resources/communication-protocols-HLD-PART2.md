# Communication Protocols - HLD Interview Guide (Part 2)

**Continuation from Part 1 - Focus on HLD Interview Questions**

---

## Top 10 HLD Interview Questions with Protocol Analysis

### Question 1: Design WhatsApp / Messenger

**Requirements:**
- 2 billion users
- Real-time messaging (1-1 and group)
- Message delivery guarantee
- Online/offline status
- Read receipts
- Media sharing

**Protocol Selection & Architecture:**

```
Primary Protocol: WebSocket (Bidirectional, Real-time)

Architecture:
┌──────────┐                     ┌────────────────┐
│  Client  │────WebSocket───────→│  Chat Gateway  │
│ (Mobile) │←───WebSocket────────│   (Node.js)    │
└──────────┘                     │ 100K active    │
                                 │ connections    │
                                 └────────┬───────┘
                                          │
                         ┌────────────────┼────────────────┐
                         ↓                ↓                ↓
                  [Redis Pub/Sub]   [Kafka Queue]   [Presence Service]
                         │                │                │
                         ↓                ↓                ↓
                  [Message DB]    [Media Service]   [User Status DB]
                  (Cassandra)      (S3 + CDN)        (Redis)
```

**Protocol Decisions:**

1. **WebSocket for Real-Time Chat**
   ```
   Why:
   ├── Bidirectional: Client and server both send
   ├── Low latency: <100ms message delivery
   ├── Persistent connection: No reconnect overhead
   ├── Efficient: 2-byte frame vs 800-byte HTTP header
   └── Real-time presence: Online/typing indicators
   
   Trade-offs:
   ├── More complex than REST
   ├── Need sticky sessions at load balancer
   ├── Requires connection state management
   └── But essential for chat UX
   ```

2. **gRPC for Microservices**
   ```
   Internal services use gRPC:
   ├── Auth Service ←→ Chat Service
   ├── Media Service ←→ Storage Service
   ├── Notification Service ←→ Push Service
   
   Why gRPC:
   ├── 5-10x faster than REST
   ├── Strongly typed contracts (protobuf)
   ├── Streaming for large media uploads
   └── Efficient for high-throughput internal calls
   ```

3. **REST for File Upload**
   ```
   Media upload flow:
   1. Client requests upload URL (REST API)
   2. Server generates pre-signed S3 URL
   3. Client uploads directly to S3 (HTTP PUT)
   4. Client confirms upload (REST API)
   
   Why REST for this:
   ├── Standard HTTP works everywhere
   ├── Can resume failed uploads
   ├── Direct S3 upload (bypass app server)
   └── Well-supported by mobile SDKs
   ```

4. **Kafka for Message Queue**
   ```
   Use Cases:
   ├── Message persistence (before delivery)
   ├── Offline message storage
   ├── Push notification triggers
   ├── Analytics events
   └── Message indexing for search
   
   Why Kafka:
   ├── High throughput: 1M+ messages/sec
   ├── Durable: Messages persisted to disk
   ├── Replay capability: Reprocess if needed
   └── Ordering guaranteed per partition
   ```

**Capacity Estimation:**
```
Users: 2 billion monthly, 200M daily active
Messages: 50 billion/day = 580K messages/second

Connection Requirements:
├── 200M DAU, 20% concurrent = 40M connections
├── 100K connections per WebSocket server
├── Need: 400 WebSocket servers
└── With 3x redundancy: 1,200 servers

Storage (Messages):
├── Average message: 100 bytes
├── 50B messages/day × 100 bytes = 5 TB/day
├── 30 days retention: 150 TB
└── With replication (3x): 450 TB

Storage (Media):
├── 20% of messages have media
├── Average media: 200 KB
├── 10B media/day × 200 KB = 2 PB/day
├── 90 days retention: 180 PB
└── With CDN caching: Reduce origin by 80%
```

**Key Design Decisions:**

1. **Message Delivery Guarantee**
   ```
   Three-Acknowledgment System:
   
   Client A                Server                 Client B
      │                      │                      │
      │──Send Message────────→                      │
      │←─ACK (Received)──────│                      │
      │                      │──Forward Message────→│
      │                      │←─ACK (Delivered)─────│
      │←─Delivery Receipt────│                      │
      │                      │←─Read Receipt────────│
      │←─Read Receipt────────│                      │
   
   States:
   ├── Sent: Server received
   ├── Delivered: Recipient device received
   └── Read: Recipient viewed message
   ```

2. **Group Chat Scalability**
   ```
   Problem: 256-member group, 1 message = 255 deliveries
   
   Solution: Fan-out optimization
   ├── Small groups (<20): Fan-out on write
   ├── Large groups (>20): Fan-out on read
   └── For 100+ member groups: Paginate members
   
   Architecture:
   [Message] → [Kafka] → [Fan-out Worker]
                              ↓
                      [Recipient Queue 1]
                      [Recipient Queue 2]
                      [Recipient Queue N]
                              ↓
                      [WebSocket Gateways]
   ```

3. **Offline Message Handling**
   ```
   When user is offline:
   1. Store in Kafka (durable)
   2. Persist to Cassandra (user_id + message_id)
   3. Send push notification (APNs/FCM)
   4. On reconnect: Fetch missed messages
   5. Mark as delivered, send ACK
   
   Pull-based on reconnect:
   GET /messages/missed?since=<last_message_id>&limit=100
   ```

**Interview Talking Points:**
```
"For WhatsApp, I'd use WebSocket for real-time chat because:

1. Real-Time Requirement:
   - Users expect <100ms message delivery
   - WebSocket provides persistent bidirectional channel
   - Can push messages instantly when they arrive
   
2. Presence Detection:
   - Online status needs real-time updates
   - WebSocket makes this natural (connection = online)
   - Typing indicators need bidirectional
   
3. Scale Strategy:
   - 40M concurrent connections
   - 100K connections per server
   - Need 400 servers (1,200 with redundancy)
   - Use sticky sessions at load balancer
   - Redis Pub/Sub for cross-server messaging
   
4. Alternative Considered - Long Polling:
   - Rejected due to latency (1-2 second delays)
   - Much higher server load (constant requests)
   - Battery drain on mobile
   - WebSocket is superior for this use case
   
5. Hybrid Approach:
   - WebSocket for active chats
   - REST for API calls (user profile, settings)
   - gRPC for internal microservices
   - Kafka for message durability
   - Push notifications for offline users"
```

---

### Question 2: Design Uber / Lyft (Location Tracking)

**Requirements:**
- Real-time driver location updates
- Match riders with nearby drivers
- ETA calculation
- Route optimization
- 1M concurrent rides

**Protocol Selection & Architecture:**

```
Primary Protocols: WebSocket + gRPC + Kafka

Architecture:
┌─────────────┐             ┌──────────────────┐
│ Driver App  │───WS────────→│  Location Gateway│
│ (sends GPS) │←──WS─────────│    (Go Server)   │
└─────────────┘             │  10K drivers/srv │
                            └─────────┬────────┘
                                      │
                            ┌─────────┼─────────┐
                            ↓         ↓         ↓
                      [Kafka]  [Redis Geo] [Driver DB]
                         │          │
                         ↓          ↓
┌─────────────┐    [Matching]  [Location]
│ Rider App   │    [Service]   [Service]
│             │         │          │
│ (requests)  │         └──gRPC───┘
└─────────────┘              ↓
                     [ETA Calculator]
```

**Protocol Decisions:**

1. **WebSocket for Driver Location**
   ```
   Why WebSocket for drivers:
   ├── Continuous GPS updates (every 5 seconds)
   ├── Efficient: Send only lat/long coordinates
   ├── Persistent connection: No reconnect overhead
   ├── Bidirectional: Server can send ride requests
   └── Low battery drain vs polling
   
   Message Format:
   {
     "type": "location_update",
     "driver_id": "D123",
     "latitude": 37.7749,
     "longitude": -122.4194,
     "heading": 90,
     "speed": 45,
     "timestamp": 1704978000
   }
   
   Size: ~150 bytes vs ~950 bytes with HTTP overhead
   Bandwidth savings: 84% reduction
   ```

2. **REST for Rider Requests**
   ```
   Rider actions are request-response:
   ├── POST /rides (request a ride)
   ├── GET /rides/:id (check status)
   ├── GET /drivers/nearby (find drivers)
   └── PUT /rides/:id/cancel (cancel ride)
   
   Why REST:
   ├── Sporadic actions (not continuous)
   ├── Standard HTTP (works everywhere)
   ├── Cacheable responses (nearby drivers)
   └── Simpler than WebSocket for this use case
   ```

3. **gRPC for Internal Services**
   ```
   Microservices communication:
   
   [Matching Service] ←gRPC→ [Location Service]
          ↓
   [Pricing Service] ←gRPC→ [Route Service]
          ↓
   [Notification Service] ←gRPC→ [Driver Service]
   
   Why gRPC:
   ├── 10ms average latency (vs 50ms REST)
   ├── High throughput: 100K+ RPC/second
   ├── Streaming for route updates
   └── Type-safe contracts between services
   ```

4. **Kafka for Event Streaming**
   ```
   Events to track:
   ├── Ride requested
   ├── Driver assigned
   ├── Driver arrived
   ├── Trip started
   ├── Trip completed
   └── Payment processed
   
   Why Kafka:
   ├── Durable event log
   ├── Multiple consumers (analytics, billing, ML)
   ├── Replay capability
   └── Ordering per rider/driver
   ```

**Geospatial Query Optimization:**
```
Challenge: Find drivers within 5km of rider

Solution 1: Redis Geospatial (Recommended)
redis> GEOADD drivers:seattle -122.4194 37.7749 driver_123
redis> GEORADIUS drivers:seattle -122.4194 37.7749 5 km

Benefits:
├── Sub-millisecond query time
├── Sorted by distance
├── Can add driver metadata
└── Scales to millions of drivers

Solution 2: PostgreSQL + PostGIS
SELECT * FROM drivers 
WHERE ST_DWithin(
  location, 
  ST_MakePoint(-122.4194, 37.7749)::geography,
  5000  -- meters
)
ORDER BY ST_Distance(location, ST_MakePoint(-122.4194, 37.7749))
LIMIT 10;

Benefits:
├── Rich queries (car type, rating)
├── ACID transactions
✗ Slower than Redis (10-50ms)
✗ Harder to scale
```

**Capacity Estimation:**
```
Active Drivers: 1M globally
Active Riders: 10M globally
Concurrent Rides: 1M

Location Updates:
├── 1M drivers × (1 update / 5 seconds)
├── = 200K updates/second
├── 150 bytes each
└── Bandwidth: 30 MB/second incoming

Driver Matching Queries:
├── 10M riders checking every 10 seconds
├── = 1M queries/second
├── Redis can handle: 100K queries/sec per node
└── Need: 10 Redis nodes for driver lookup

WebSocket Connections:
├── 1M drivers need persistent connections
├── 10K connections per server
└── Need: 100 WebSocket servers
```

**Key Design Decisions:**

1. **Location Update Frequency Trade-off**
   ```
   ┌─────────────────┬─────────────┬─────────────┐
   │ Update Interval │ Accuracy    │ Bandwidth   │
   ├─────────────────┼─────────────┼─────────────┤
   │ 1 second        │ Very High   │ 5x higher   │
   │ 5 seconds       │ High        │ Optimal     │
   │ 10 seconds      │ Medium      │ 50% saving  │
   │ 30 seconds      │ Low         │ 83% saving  │
   └─────────────────┴─────────────┴─────────────┘
   
   Decision: 5 seconds
   ├── Good accuracy for ETA
   ├── Acceptable bandwidth
   └── Smooth map animation
   ```

2. **Geohashing for Sharding**
   ```
   Problem: 1M drivers globally
   
   Solution: Geohash sharding
   ├── Divide world into grid squares
   ├── Each square = one Redis instance
   ├── Route to Redis based on geohash
   └── Can query adjacent squares for edge cases
   
   Example: San Francisco
   Geohash: 9q8y (precision 4)
   └── All SF drivers in one Redis shard
   ```

**Interview Talking Points:**
```
"For Uber, I'd use different protocols for different components:

1. WebSocket for Driver GPS:
   - Drivers send location every 5 seconds
   - Need persistent connection (not REST)
   - WebSocket: 150 bytes vs 950 bytes with REST
   - At 200K updates/sec, save 160 MB/second bandwidth
   - Battery efficient for drivers

2. REST for Rider App:
   - Riders make sporadic requests
   - Request ride, check status, cancel
   - REST is simpler for these actions
   - Can cache nearby drivers list

3. gRPC for Microservices:
   - Matching Service needs <10ms calls to Location Service
   - High throughput: 100K+ matches/second
   - gRPC provides 5x better performance than REST

4. Kafka for Events:
   - Ride lifecycle events
   - Analytics, billing, ML all consume same events
   - Durable, replayable
   
5. Redis Geospatial:
   - Find drivers within 5km: <1ms query time
   - Updates via WebSocket → Kafka → Redis
   - Can scale to 10M drivers

This hybrid approach optimizes for each component's needs."
```

---

### Question 3: Design Twitter / Instagram Feed

**Requirements:**
- 500M users
- Home feed (posts from followed users)
- Trending feed
- Real-time likes/comments
- 50M posts/day

**Protocol Selection & Architecture:**

```
Primary Protocols: REST + WebSocket + Kafka + gRPC

Architecture:
┌────────────┐         REST        ┌─────────────┐
│ Mobile App │──GET /feed─────────→│ API Gateway │
└────────────┘                     └──────┬──────┘
                                          │
                    ┌─────────────────────┼────────────┐
                    ↓                     ↓            ↓
            [Feed Service]       [Post Service]  [User Service]
                    │                     │            │
                    ↓                     ↓            ↓
            [Redis Feed Cache]   [Post DB]      [User DB]
                    │             (Cassandra)    (PostgreSQL)
                    ↓
            [Kafka] ←──── [Fan-out Workers]
                    │
                    ↓
     ┌──────────────┼──────────────┐
     ↓              ↓               ↓
[WebSocket]  [Notification]  [Analytics]
[Server]       [Service]       [Service]
```

**Protocol Decisions:**

1. **REST for Feed API**
   ```
   Feed Request:
   GET /api/v1/feed/home?cursor=xyz&limit=20
   
   Response:
   {
     "posts": [...],
     "pagination": {
       "next_cursor": "abc123",
       "has_more": true
     }
   }
   
   Why REST:
   ├── Feed is pull-based (user refreshes)
   ├── Cacheable at CDN/Redis
   ├── Cursor pagination works well
   ├── Standard HTTP semantics
   └── Works on slow networks
   
   Performance:
   ├── With Redis cache: <50ms p99
   ├── 80% cache hit ratio
   └── CDN offloads 60% of requests
   ```

2. **WebSocket for Real-Time Updates**
   ```
   Use Cases:
   ├── New posts from followed users
   ├── Like counter updates
   ├── New comments
   └── Trending topics
   
   Architecture:
   [Post Created] → [Kafka] → [Fan-out Worker]
                                    ↓
                            [WebSocket Gateway]
                                    ↓
                            [Active Users' Browsers]
   
   Why WebSocket here:
   ├── Push updates without refresh
   ├── Real-time like counters
   ├── "1 new post" notification banner
   └── Better UX than polling
   
   Fallback:
   If WebSocket unavailable → REST polling every 60s
   ```

3. **Kafka for Feed Fanout**
   ```
   Fan-out Strategy (Hybrid):
   
   Approach 1: Fan-out on Write (Normal Users)
   ├── User posts
   ├── Fan-out to all followers' feeds
   ├── Pre-compute feeds in Redis
   └── Fast read (pre-computed)
   
   Approach 2: Fan-out on Read (Celebrities)
   ├── Celebrity posts (>1M followers)
   ├── Don't fan-out (too expensive)
   ├── Merge at read time
   └── Slower read, but acceptable
   
   Why Kafka:
   ├── Buffer post events
   ├── Multiple consumers (feed, search, analytics)
   ├── Replay on fan-out failure
   └── Ordering per user
   ```

4. **gRPC for Internal Services**
   ```
   Service Communication:
   [Feed Service] ──gRPC──→ [Post Service]
                            (Get post details)
   
   [Feed Service] ──gRPC──→ [User Service]
                            (Get user profiles)
   
   [Feed Service] ──gRPC──→ [Like Service]
                            (Get like counts)
   
   Why gRPC:
   ├── Feed needs data from 3+ services
   ├── Low latency critical (<10ms per call)
   ├── Can batch via gRPC streaming
   └── Type-safe contracts
   ```

**Feed Generation Algorithm:**
```
Hybrid Approach:

For normal users (< 10K followers):
1. Post event → Kafka
2. Fan-out worker reads from Kafka
3. Get follower list from database
4. Insert into each follower's Redis feed
   ZADD feed:user123 <timestamp> <post_id>
5. User requests feed: Read from Redis
   ZREVRANGE feed:user123 0 19

For celebrities (> 10K followers):
1. Post event → Kafka → Post DB
2. NO fan-out (too expensive)
3. User requests feed:
   - Get pre-computed feed (normal users)
   - Get celebrity posts (on-demand query)
   - Merge and sort by timestamp
   - Return top 20
```

**Capacity Estimation:**
```
Users: 500M total, 50M DAU
Posts: 50M/day = 580 posts/second
Feed Requests: 50M users × 10 refreshes/day = 500M requests/day = 5.8K QPS

Storage:
├── Posts: 50M/day × 1KB = 50 GB/day
├── 3 years: 50 GB × 365 × 3 = 55 TB
├── Media: 50M/day × 30% × 200KB = 3 TB/day
└── Media 3 years: 3.3 PB

Redis Feed Cache:
├── Active users: 50M DAU
├── 20 posts per feed × 100 bytes = 2KB
├── Total: 50M × 2KB = 100 GB
└── With replication (3x): 300 GB

Fan-out Calculation:
├── Average followers: 500
├── 50M posts/day × 500 followers = 25 billion writes/day
├── = 289K writes/second
├── Kafka can handle: 1M messages/second
└── No bottleneck
```

**Key Design Decisions:**

1. **Cache Strategy**
   ```
   Three-Level Caching:
   
   Level 1: CDN (CloudFront)
   ├── Static assets (images, videos, JS/CSS)
   ├── 90% cache hit ratio
   └── Reduces origin load by 90%
   
   Level 2: Redis (Feed Cache)
   ├── Pre-computed feeds
   ├── 80% hit ratio
   ├── <5ms response time
   └── TTL: 15 minutes
   
   Level 3: Database (PostgreSQL/Cassandra)
   ├── Source of truth
   ├── Only 4% of requests reach here
   └── Can handle with replication
   ```

2. **Ranking Algorithm**
   ```
   Chronological vs Algorithmic:
   
   Chronological (Simple):
   ├── Sort by post timestamp
   ├── Fast (no computation)
   ├── Fair (all posts shown)
   └── May show boring content
   
   Algorithmic (Complex):
   ├── Rank by engagement score
   ├── Score = (likes × 1.0) + (comments × 2.0) 
               + (shares × 3.0) + (recency × 10.0)
   ├── Personalized (user's past behavior)
   ├── More engaging
   └── Requires ML model
   
   Decision: Hybrid
   ├── Default: Chronological
   ├── Option: "Top posts" (algorithmic)
   └── Let user choose
   ```

**Interview Talking Points:**
```
"For Twitter Feed, I'd use a hybrid protocol approach:

1. REST for Feed API:
   - Feed is pull-based (user refreshes)
   - GET /feed returns 20 posts
   - Cache at CDN + Redis (95% hit rate)
   - <50ms latency for cached feeds
   
2. WebSocket for Real-Time:
   - Push new posts without refresh
   - Real-time like counter updates
   - "5 new tweets" notification banner
   - Fallback to polling if WS unavailable
   
3. Fan-out Strategy (Hybrid):
   - Normal users: Fan-out on write
     * Pre-compute feeds
     * Fast read
   - Celebrities: Fan-out on read
     * Merge at request time
     * Avoid 1M+ fan-out
   
4. Kafka for Durability:
   - Post events → Kafka
   - Multiple consumers (feed, search, analytics)
   - Can replay if fan-out fails
   - 1M messages/second capacity
   
5. Scale Numbers:
   - 500M users, 50M DAU
   - 580 posts/second
   - 25B feed writes/day (fan-out)
   - 100 GB Redis for feeds
   
This handles Twitter's scale requirements efficiently."
```

---

### Question 4: Design Stock Trading Platform (Robinhood)

**Requirements:**
- Real-time stock prices
- Order placement/execution
- Portfolio tracking
- 10M users, 100K concurrent traders
- <100ms latency for price updates

**Protocol Selection:**

```
Protocols: WebSocket + gRPC + Kafka + REST

Architecture:
┌──────────────┐            ┌─────────────────┐
│ Trading App  │───WS──────→│ Price Gateway   │
│              │←──WS───────│  (Real-time)    │
└──────────────┘            └────────┬────────┘
                                     │
                            ┌────────┼────────┐
                            ↓        ↓        ↓
                      [Redis]  [Kafka]  [Time-Series DB]
                         │        │        (InfluxDB)
                         ↓        ↓
┌──────────────┐    [Price]  [Order]
│ Mobile App   │    [Cache]  [Service]
│              │        │        │
│ (REST)       │───────gRPC──────┘
└──────────────┘              ↓
                        [Trade Engine]
                              ↓
                        [Order DB]
                        (PostgreSQL)
```

**Protocol Decisions:**

1. **WebSocket for Live Prices**
   ```
   Price Update Flow:
   [Stock Exchange] → [Market Data Feed]
                              ↓
                      [Price Service]
                              ↓
                      [Kafka] → [Fan-out]
                              ↓
                      [WebSocket Gateway]
                              ↓
                      [1M connected clients]
   
   Why WebSocket:
   ├── Real-time: <100ms from exchange to user
   ├── Efficient: Can stream 100+ stocks per connection
   ├── Bidirectional: User can subscribe/unsubscribe
   ├── Low overhead: 2-14 byte frame header
   └── Push-based: No polling needed
   
   Message Format (Binary):
   {
     "type": "price_update",
     "symbol": "AAPL",
     "price": 150.25,
     "change": +0.75,
     "volume": 1000000,
     "timestamp": 1704978000
   }
   
   Optimization: Use Protobuf
   ├── JSON: ~120 bytes
   ├── Protobuf: ~30 bytes
   └── 75% bandwidth savings
   ```

2. **REST for Order Placement**
   ```
   Order Actions:
   POST /api/v1/orders
   {
     "symbol": "AAPL",
     "type": "market",
     "side": "buy",
     "quantity": 10,
     "price": 150.25 (for limit orders)
   }
   
   Why REST:
   ├── Idempotency critical (via order ID)
   ├── Synchronous response (order accepted/rejected)
   ├── Standard HTTP status codes
   ├── Easy to implement authentication
   └── Audit trail (HTTP logs)
   
   Response:
   {
     "order_id": "ORD123",
     "status": "pending",
     "created_at": "2025-01-10T10:00:00Z"
   }
   ```

3. **Kafka for Order Processing**
   ```
   Order Lifecycle:
   [REST API] → [Kafka] → [Trade Engine]
                    ↓
           [Multiple Consumers]
           ├── Portfolio Service
           ├── Notification Service
           ├── Compliance Service
           └── Analytics Service
   
   Why Kafka:
   ├── Durable order queue (crash-safe)
   ├── Exactly-once delivery (critical for money)
   ├── Audit log (replay capability)
   ├── Multiple consumers need same data
   └── Can handle 100K orders/second
   ```

4. **gRPC for Internal Trading Engine**
   ```
   Services:
   [Order Service] ──gRPC──→ [Portfolio Service]
                            (Check buying power)
   
   [Order Service] ──gRPC──→ [Risk Service]
                            (Margin requirements)
   
   [Order Service] ──gRPC──→ [Settlement Service]
                            (Execute trade)
   
   Why gRPC:
   ├── Low latency critical (<10ms per call)
   ├── Trade execution requires 5-10 service calls
   ├── Strong typing (avoid errors with money)
   ├── Streaming for order book updates
   └── 10x faster than REST for internal calls
   ```

**Real-Time Data Optimization:**
```
Challenge: 10M users watching 100+ stocks each

Solution: Throttling + Batching

Throttling:
├── Exchange updates: 1000 times/second
├── Clients need: 1 update/second max
├── Server throttles: Send every 1000th update
└── Still feels "real-time" to users

Batching:
// Instead of sending individual updates
{ "AAPL": 150.25 }
{ "GOOGL": 2800.50 }
{ "MSFT": 380.75 }

// Batch multiple symbols
{
  "updates": [
    { "symbol": "AAPL", "price": 150.25, ... },
    { "symbol": "GOOGL", "price": 2800.50, ... },
    { "symbol": "MSFT", "price": 380.75, ... }
  ],
  "timestamp": 1704978000
}

Bandwidth Savings:
├── Individual: 100 updates × 150 bytes = 15 KB
├── Batched: 1 batch × 5 KB = 5 KB
└── 67% reduction
```

**Capacity Estimation:**
```
Users: 10M registered, 100K concurrent traders
Stock Universe: 10K stocks
Price Updates: 10K stocks × 1 update/second = 10K updates/sec

WebSocket Connections:
├── 100K active traders
├── 10K connections per server
└── Need: 10 WebSocket servers

Price Distribution:
├── 10K stocks × 1 update/sec = 10K updates
├── With 100K subscribers average per stock
├── = 1B updates/second to distribute
├── With throttling (1/sec per stock per user): 1M/sec
└── 100 WebSocket servers can handle this

Order Processing:
├── Peak trading: 100K orders/second
├── Average: 10K orders/second
├── Kafka throughput: 1M messages/second
└── No bottleneck
```

**Interview Talking Points:**
```
"For Robinhood, protocols are chosen by latency requirements:

1. WebSocket for Market Data (CRITICAL):
   - Stock prices change milliseconds
   - Users need <100ms updates for trading decisions
   - WebSocket optimal for continuous stream
   - Can push to 100K users simultaneously
   
2. REST for Order Entry (RELIABILITY):
   - Order placement needs acknowledgment
   - Idempotency via order ID prevents duplicates
   - Can retry on failure safely
   - Audit trail for regulatory compliance
   
3. Kafka for Order Book (DURABILITY):
   - Every order must be persisted
   - Zero data loss acceptable
   - Multiple consumers (trade engine, risk, compliance)
   - Exactly-once semantics for money operations
   
4. gRPC for Trade Execution:
   - Order Service → Portfolio (check funds): 5ms
   - Order Service → Risk Engine (margin check): 5ms
   - Order Service → Trade Execution: 10ms
   - Total: 20ms for internal calls
   - REST would be 100ms+ (unacceptable)

Trade-off: Complexity vs Performance
- More protocols = more complexity
- But latency requirements justify this
- Critical for user experience and safety"
```

---

### Question 5: Design YouTube / Netflix (Video Streaming)

**Requirements:**
- 1 billion users
- 500M hours watched/day
- Video uploads
- Adaptive bitrate streaming
- Recommendations

**Protocol Selection:**

```
Protocols: HTTP/2 (HLS/DASH) + REST + Kafka + CDN

Architecture:
┌─────────────┐         REST         ┌──────────────┐
│ Video Player│────Metadata──────────→│ API Gateway  │
│             │                       └──────┬───────┘
│  (Browser)  │                              │
│             │         HTTP/2               ↓
│             │◄───Video Chunks───────[CDN (CloudFront)]
└─────────────┘                              │
                                             ↓
                                      [Origin Servers]
                                             │
                                    ┌────────┼────────┐
                                    ↓        ↓        ↓
                              [Video DB]  [S3]   [Encoding]
                              (Cassandra) (Storage) (Service)
```

**Protocol Decisions:**

1. **HTTP/2 for Video Streaming (NOT WebSocket)**
   ```
   Why HTTP/2, not WebSocket:
   ├── CDN support: Every CDN supports HTTP
   ├── Range requests: Can seek to any point
   ├── Caching: HTTP caching infrastructure works
   ├── Firewall friendly: Port 443 always open
   └── Adaptive bitrate: HLS/DASH standards
   
   HLS (HTTP Live Streaming):
   ├── Break video into 10-second chunks
   ├── Multiple quality levels (360p, 720p, 1080p, 4K)
   ├── Client requests appropriate quality
   ├── Switch quality mid-stream (adaptive)
   └── All standard HTTP GET requests
   
   vs WebSocket:
   ├── WebSocket doesn't cache well
   ├── Can't seek backward easily
   ├── CDN integration complex
   └── HTTP/2 is better for video
   ```

2. **REST for Video Metadata**
   ```
   APIs:
   ├── GET /videos/:id (video details)
   ├── POST /videos/:id/view (increment view count)
   ├── GET /videos/:id/comments (paginated)
   ├── POST /videos/:id/like
   └── GET /recommendations (ML-based)
   
   Why REST:
   ├── Standard CRUD operations
   ├── Cacheable responses
   ├── Works everywhere (browsers, mobile)
   └── Simple authentication
   ```

3. **Kafka for Video Processing Pipeline**
   ```
   Upload Flow:
   [Upload] → [S3] → [Kafka Event]
                          ↓
            ┌─────────────┼─────────────┐
            ↓             ↓              ↓
     [Transcoding]  [Thumbnail]   [Content Moderation]
      (Multiple     (Generator)    (AI Service)
       resolutions)
            │             │              │
            └─────────────┼──────────────┘
                          ↓
                    [Video Ready]
                          ↓
                    [Notification Service]
   
   Why Kafka:
   ├── Decouple upload from processing
   ├── Multiple workers process in parallel
   ├── Can retry failed encodings
   ├── Track progress per video
   └── Handle spikes in uploads
   ```

4. **gRPC for Internal Microservices**
   ```
   Services:
   [Video Service] ──gRPC──→ [Recommendation Engine]
   [Video Service] ──gRPC──→ [Analytics Service]
   [Video Service] ──gRPC──→ [Ad Service]
   
   Why gRPC:
   ├── Internal only (not user-facing)
   ├── High throughput needed
   ├── Streaming for batch operations
   └── Type safety for contracts
   ```

**Adaptive Bitrate Streaming:**
```
How HLS Works:

1. Video encoded at multiple bitrates:
   ├── 4K (3840×2160): 15 Mbps
   ├── 1080p: 5 Mbps
   ├── 720p: 2.5 Mbps
   ├── 480p: 1 Mbps
   └── 360p: 0.5 Mbps

2. Each quality split into segments:
   video_1080p_00001.ts (10 seconds)
   video_1080p_00002.ts (10 seconds)
   ...

3. Manifest file (playlist):
   #EXTM3U
   #EXT-X-STREAM-INF:BANDWIDTH=5000000
   1080p.m3u8
   #EXT-X-STREAM-INF:BANDWIDTH=2500000
   720p.m3u8

4. Client behavior:
   ├── Start with low quality (fast start)
   ├── Measure bandwidth after each chunk
   ├── Switch to higher quality if bandwidth allows
   ├── Drop to lower quality if buffering
   └── Seamless quality switching

Why This Works:
├── Client controls quality (not server)
├── Works on varying network speeds
├── Standard HTTP (cacheable)
└── CDN-friendly (each chunk cached)
```

**CDN Strategy:**
```
Netflix's Approach:

Global CDN Network:
├── 15,000+ servers in 1,000+ locations
├── Popular content replicated everywhere
├── Long-tail content pulled on-demand
├── 95%+ cache hit ratio
└── Serves from <50ms away from users

Cache Hierarchy:
Level 1: Edge (ISP partner caches)
└── Serves 80% of traffic
    
Level 2: Regional CDN
└── Serves 15% of traffic
    
Level 3: Origin
└── Serves 5% of traffic (new/rare content)

Benefits:
├── Reduced origin bandwidth: 95% savings
├── Lower latency: <50ms globally
├── Better user experience
└── Cost savings: CDN cheaper than origin bandwidth
```

**Capacity Estimation:**
```
Users: 1B total, 100M concurrent viewers
Videos Watched: 500M hours/day
Average Video: 10 minutes
Total Video Plays: 3B/day = 35K plays/second

Bandwidth:
├── Average quality: 2 Mbps
├── 100M concurrent × 2 Mbps = 200 Tbps
├── With CDN caching: 10 Tbps from origin
└── CDN: 190 Tbps (95% offload)

Storage:
├── 500 hours uploaded/minute
├── Each video: 5 resolutions × 10 min × 100 MB = 5 GB
├── 500 hours/min × 6 videos × 5 GB = 15 TB/minute
├── Per day: 21 PB/day
└── 3 years: 23,000 PB = 23 exabytes
```

**Interview Talking Points:**
```
"For Netflix, I'd use HTTP/2 with HLS, not WebSocket:

1. HTTP/2 for Video Delivery:
   - Industry standard (HLS/DASH)
   - CDN compatible (critical for global scale)
   - Cacheable chunks (95%+ hit ratio)
   - Range requests for seeking
   - Works through any firewall
   
2. Why NOT WebSocket:
   - WebSocket bypasses CDN caching
   - Can't leverage HTTP cache infrastructure
   - Seeking backward is complex
   - No benefit over HTTP/2 for video
   - HTTP/2 multiplexing handles multiple chunks
   
3. REST for Application APIs:
   - Browse catalog
   - Search videos
   - User profiles
   - Recommendations
   - Standard CRUD operations
   
4. Kafka for Video Processing:
   - Upload → Transcode → Multiple qualities
   - Decouple upload from processing
   - Parallel processing of chunks
   - Handle 500 hours/minute upload rate
   
5. CDN Strategy (Critical):
   - 15K+ servers globally
   - 95% cache hit ratio
   - 200 Tbps served mostly from cache
   - Only 10 Tbps from origin
   - Massive cost and performance win

Key Insight: Video streaming is unique - HTTP/2 + CDN
is optimal, WebSocket adds no value here."
```

---

### Question 6: Design E-commerce Platform (Amazon)

**Requirements:**
- 300M users
- Product catalog (100M products)
- Real-time inventory
- Order processing
- Payment integration

**Protocol Selection:**

```
Protocols: REST (Primary) + gRPC + Kafka + GraphQL

Architecture:
┌──────────┐         REST          ┌─────────────┐
│ Browser  │──────────────────────→│ API Gateway │
└──────────┘                       └──────┬──────┘
                                          │
                      ┌───────────────────┼────────────┐
                      ↓                   ↓            ↓
            [Product Service]    [Order Service]  [User Service]
                      │                   │            │
                      ↓                   ↓            ↓
            [Product DB]          [Order DB]    [User DB]
            (Elasticsearch)       (PostgreSQL)  (PostgreSQL)
                      │                   │
                      └──────Kafka────────┘
                              ↓
                 ┌────────────┼─────────────┐
                 ↓            ↓              ↓
          [Inventory]   [Payment]    [Notification]
           [Service]     [Service]     [Service]
```

**Protocol Decisions:**

1. **REST for Customer-Facing APIs**
   ```
   Why REST:
   ├── Public API (third-party sellers need access)
   ├── Standard HTTP (works everywhere)
   ├── Cacheable product pages (80% cache hit)
   ├── Simple for web/mobile integration
   └── Mature tooling ecosystem
   
   Key APIs:
   ├── GET /products?category=electronics&page=1
   ├── POST /cart/items
   ├── POST /orders
   ├── GET /orders/:id/tracking
   └── POST /payments
   
   Performance:
   ├── Product pages: <200ms p99 (with cache)
   ├── Search: <500ms p99
   ├── Checkout: <1s p99
   └── 80% requests served from CDN/Redis
   ```

2. **GraphQL for Mobile App (Optional)**
   ```
   Mobile-specific challenges:
   ├── Slow networks (3G/4G)
   ├── Limited bandwidth
   ├── Battery constraints
   └── Need minimal data transfer
   
   GraphQL Benefits:
   ├── Client specifies exact fields needed
   ├── Single request for complex pages
   ├── No over-fetching (save bandwidth)
   └── Better mobile experience
   
   Example:
   # Mobile home page (1 request)
   query HomePage {
     user { id, name, cartCount }
     recommendations(limit: 10) {
       id, title, price, thumbnail
     }
     categories { id, name }
   }
   
   vs REST (multiple requests):
   ├── GET /user/profile
   ├── GET /recommendations
   ├── GET /categories
   └── GET /cart/count
   ```

3. **gRPC for Internal Microservices**
   ```
   Critical Path Services:
   
   [Order Service] ──gRPC──→ [Inventory Service]
                            (Check stock availability)
                            Response: <5ms
   
   [Order Service] ──gRPC──→ [Pricing Service]
                            (Calculate total, taxes)
                            Response: <5ms
   
   [Order Service] ──gRPC──→ [Payment Service]
                            (Process payment)
                            Response: <10ms
   
   Why gRPC:
   ├── Order checkout has strict latency budget
   ├── Need 5-10 service calls per order
   ├── gRPC: 5ms vs REST: 50ms per call
   ├── Total: 50ms vs 500ms
   └── 10x faster checkout experience
   ```

4. **Kafka for Order Processing**
   ```
   Order Event Stream:
   
   [Order Placed] → [Kafka Topic: orders]
                          ↓
          ┌───────────────┼────────────────┐
          ↓               ↓                 ↓
   [Inventory     [Payment        [Notification
    Decrement]     Processing]     Service]
          ↓               ↓                 ↓
   [Shipping      [Fraud          [Email
    Service]       Detection]      Service]
   
   Why Kafka:
   ├── Decouple order placement from fulfillment
   ├── Multiple teams consume same events
   ├── Can replay for debugging
   ├── Guaranteed ordering per order
   └── Handles Black Friday spikes (10x traffic)
   ```

**Inventory Consistency Challenge:**
```
Problem: Overselling (100 items, but 200 orders placed)

Solution 1: Pessimistic Locking (Database)
├── Lock item before purchase
├── Decrement quantity
├── Release lock
✗ Slow (10-50ms per order)
✗ Doesn't scale
✗ Deadlocks possible

Solution 2: Optimistic Locking
├── Read quantity with version
├── Attempt to update
├── Retry if version changed
✓ Fast for low contention
✗ High retry rate for hot items

Solution 3: Redis Atomic Decrement (Recommended)
WATCH inventory:item_123
GET inventory:item_123
// If quantity > 0
MULTI
DECR inventory:item_123
EXEC
// If EXEC succeeds, proceed with order

Benefits:
├── Atomic operation
├── <1ms latency
├── Scales horizontally
├── No deadlocks
└── Can handle 100K orders/second

Eventual Consistency:
├── Redis decrement (immediate)
├── Async sync to database (eventual)
├── Acceptable for e-commerce
└── Critical: Prevent overselling (Redis ensures this)
```

**Capacity Estimation:**
```
Users: 300M total, 20M DAU
Orders: 1M/day = 12 orders/second
Peak (Black Friday): 10x = 120 orders/second

Product Catalog:
├── 100M products
├── Average product data: 10 KB
├── Total: 1 TB
├── With images: +10 TB
└── Cache top 1M products: 10 GB

Order Processing:
├── Average order: 3 items
├── 1M orders/day × 3 = 3M item checks
├── = 35 checks/second (average)
├── Peak: 350 checks/second
└── Redis can handle 100K ops/second easily
```

**Interview Talking Points:**
```
"For Amazon, I'd primarily use REST:

1. REST is Perfect Here:
   - E-commerce is request-response based
   - Browse products (GET /products)
   - Add to cart (POST /cart/items)
   - Checkout (POST /orders)
   - Standard operations map to HTTP methods
   
2. Caching is Key:
   - Product pages: Cache for 1 hour
   - Category pages: Cache for 15 minutes
   - User cart: Cache for 5 minutes
   - 80% requests served from cache
   - Massive savings on database load
   
3. GraphQL for Mobile (Optional):
   - Mobile apps have bandwidth constraints
   - GraphQL reduces data transfer
   - Single request for complex pages
   - Worthwhile for user experience
   
4. gRPC for Checkout Flow:
   - Time-sensitive: Check inventory, calculate price, process payment
   - Need <100ms total latency
   - gRPC for internal service calls
   - 5-10x faster than REST
   
5. Kafka for Async Operations:
   - Send order confirmation email
   - Update inventory
   - Trigger shipping workflow
   - Update analytics
   - These don't need to be synchronous

No WebSocket Needed:
- E-commerce doesn't need real-time push
- Polling is sufficient for order status
- REST is simpler and works better here"
```

---

### Question 7: Design Google Docs (Collaborative Editing)

**Requirements:**
- Real-time collaboration
- Multiple users editing same document
- Conflict resolution
- Version history
- 100M documents, 10M concurrent users

**Protocol Selection:**

```
Primary Protocol: WebSocket (Operational Transform)

Architecture:
┌─────────┐                    ┌──────────────────┐
│ Client 1│──────WebSocket────→│                  │
├─────────┤                    │  Collaboration   │
│ Client 2│──────WebSocket────→│  Server          │
├─────────┤                    │  (Node.js)       │
│ Client 3│──────WebSocket────→│                  │
└─────────┘                    └────────┬─────────┘
                                        │
                            ┌───────────┼───────────┐
                            ↓           ↓           ↓
                      [Redis]     [Document    [Version
                      [State]      Service]     Control]
                                       │            │
                                       ↓            ↓
                                  [MongoDB]    [Git-like
                                  (Documents)   Storage]
```

**Protocol Decisions:**

1. **WebSocket for Real-Time Editing**
   ```
   Why WebSocket is ESSENTIAL:
   ├── Character-by-character updates
   ├── Need <100ms latency (feels instant)
   ├── Bidirectional: All clients send edits
   ├── Cursor positions need real-time sync
   └── Presence awareness (who's editing)
   
   Operation Flow:
   User A types "H"
      ↓
   Operation: { insert: "H", position: 0, user: "A" }
      ↓
   WebSocket → Server
      ↓
   Operational Transform (resolve conflicts)
      ↓
   Broadcast to User B, C via WebSocket
      ↓
   Users B, C see "H" appear
   
   Latency Breakdown:
   ├── Client → Server: 20ms
   ├── OT processing: 5ms
   ├── Server → Other clients: 20ms
   └── Total: 45ms (imperceptible to users)
   ```

2. **Operational Transform (OT) Algorithm**
   ```
   Problem: Concurrent Edits
   
   Initial: "Hello"
   
   User A (position 5): Insert "!"
   User B (position 0): Insert "Hi, "
   
   Without OT:
   User A sees: "Hello!"
   User B sees: "Hi, Hello"
   Server: Conflict! Which is correct?
   
   With OT:
   1. Transform operations based on context
   2. User A's insert at position 5
   3. User B inserted 4 chars at position 0
   4. Transform A's position: 5 + 4 = 9
   5. Final: "Hi, Hello!"
   
   All clients converge to same state
   ```

3. **REST for Document Management**
   ```
   Non-editing operations:
   ├── POST /documents (create)
   ├── GET /documents/:id (load)
   ├── DELETE /documents/:id
   ├── GET /documents/:id/versions
   └── POST /documents/:id/share
   
   Why REST:
   ├── Standard operations (not real-time)
   ├── Can cache document metadata
   ├── Simple authentication/authorization
   └── Works for document list, permissions
   ```

4. **Kafka for Version History**
   ```
   Every edit operation → Kafka
   ├── Persistent log of all changes
   ├── Can rebuild document from operations
   ├── Time travel: View document at any point
   ├── Audit trail
   └── Disaster recovery (replay from Kafka)
   
   Why Kafka:
   ├── Append-only log (natural for versions)
   ├── Replay capability
   ├── Multiple consumers (search indexer, analytics)
   └── Durable (no data loss)
   ```

**Why NOT Use:**

1. **❌ REST (Too Slow for Editing)**
   ```
   If using REST:
   ├── Each keystroke = HTTP request
   ├── 50-100ms latency per keystroke
   ├── Feels laggy to users
   ├── High server load
   └── Unacceptable for collaborative editing
   ```

2. **❌ Long Polling (Inefficient)**
   ```
   If using Long Polling:
   ├── Client polls for changes
   ├── 500ms-1s delay per update
   ├── Not suitable for keystroke-level updates
   └── Much higher server load than WebSocket
   ```

**Capacity Estimation:**
```
Users: 100M total, 10M concurrent editors
Documents: 100M active
Edits: 10M users × 100 keystrokes/min = 1B edits/hour = 278K edits/sec

WebSocket Connections:
├── 10M concurrent users
├── 10K connections per server
└── Need: 1,000 WebSocket servers

Redis State:
├── Each document state: ~100 KB
├── 10M active documents
├── Total: 1 TB
└── With replication: 3 TB

Kafka Logs:
├── 278K operations/second
├── Average operation: 100 bytes
├── = 27.8 MB/second = 2.4 TB/day
└── 90 days retention: 216 TB
```

**Interview Talking Points:**
```
"For Google Docs, WebSocket is non-negotiable:

1. Real-Time Requirement:
   - Users expect <100ms latency for keystrokes
   - Anything slower feels broken
   - WebSocket provides <50ms end-to-end
   - No other protocol can achieve this
   
2. Bidirectional Nature:
   - All users send edits
   - All users receive edits
   - Simultaneous editing
   - WebSocket handles this naturally
   
3. Operational Transform:
   - Resolve conflicting edits
   - Guarantee eventual consistency
   - All clients converge to same state
   - Complex but necessary for collaboration
   
4. Scale Challenge:
   - 10M concurrent editors
   - 278K edits/second
   - Each edit broadcast to other doc editors
   - Need 1,000 WebSocket servers
   - Redis for document state cache
   
5. Alternatives Rejected:
   - REST: Too slow (50-100ms per keystroke)
   - Long Polling: 500ms-1s lag (unacceptable)
   - SSE: One-way only (need bidirectional)
   - Only WebSocket works for this use case

Trade-off: Complexity for UX
- WebSocket + OT is complex
- But necessary for real-time collaboration
- Users expect Google Docs-level experience"
```

---

### Question 8: Design Online Gaming Platform

**Requirements:**
- Multiplayer games (100 players per match)
- Real-time position updates
- <50ms latency required
- Anti-cheat system
- Voice chat

**Protocol Selection:**

```
Protocols: UDP (Primary) + WebSocket (Fallback) + TCP

Architecture:
┌──────────────┐         UDP           ┌────────────────┐
│ Game Client  │───Position Updates───→│  Game Server   │
│              │←──Game State─────────│  (C++/Rust)    │
│              │                       │  Tick rate:    │
│              │         TCP           │  60 Hz         │
│              │◄──Critical Events─────│                │
└──────────────┘                       └────────┬───────┘
                                                │
                                       ┌────────┼────────┐
                                       ↓        ↓        ↓
                                  [Redis]   [Match]  [Player DB]
                                  [State]   [Making] (PostgreSQL)
```

**Protocol Decisions:**

1. **UDP for Game State (Primary)**
   ```
   Why UDP, not TCP:
   ├── No handshake overhead (connectionless)
   ├── No retransmission (don't need old data)
   ├── Lower latency: 1-10ms vs 20-50ms for TCP
   ├── Packet loss acceptable (game interpolates)
   └── Essential for competitive gaming
   
   What UDP is Used For:
   ├── Player position updates (60 times/second)
   ├── Bullet trajectories
   ├── Physics updates
   └── Any data where latest value matters most
   
   UDP Characteristics:
   ├── Unreliable: Packets may be lost (acceptable)
   ├── No ordering: Handle out-of-order packets
   ├── No congestion control: Application manages
   └── Fast: Minimal protocol overhead
   ```

2. **TCP for Critical Events**
   ```
   What Needs Reliability (Use TCP):
   ├── Player joined/left match
   ├── Weapon picked up
   ├── Score updates
   ├── Chat messages
   └── Game over / match results
   
   Why TCP for these:
   ├── Cannot lose these events
   ├── Ordering matters (player joined before player scored)
   ├── Retransmission acceptable (not time-critical)
   └── Delivery guarantee needed
   
   Implementation:
   - Separate TCP connection for reliable events
   - UDP for position updates (60 Hz)
   - Hybrid approach common in games
   ```

3. **WebSocket for Browser Games (Fallback)**
   ```
   If running in browser:
   ├── Cannot use raw UDP (browser limitation)
   ├── Use WebSocket (runs over TCP)
   ├── Accept higher latency (20-50ms)
   └── Works for casual games (not competitive FPS)
   
   Example: Agar.io, Slither.io
   ├── WebSocket-based
   ├── 30-50ms latency
   ├── Acceptable for these game types
   └── Wouldn't work for Counter-Strike
   ```

4. **REST for Game Lobby / Matchmaking**
   ```
   Non-game operations:
   ├── GET /matches/available
   ├── POST /matches/join
   ├── GET /player/stats
   ├── POST /player/loadout
   └── GET /leaderboards
   
   Why REST:
   ├── Standard request-response
   ├── Not latency-critical
   ├── Cacheable (leaderboards)
   └── Simple to implement
   ```

**Client-Side Prediction & Reconciliation:**
```
Problem: 50ms network latency feels laggy

Solution: Client-Side Prediction

1. User presses "W" (move forward)
2. Client immediately shows movement (optimistic)
3. Client sends input to server via UDP
4. Server processes input
5. Server sends authoritative position
6. Client reconciles:
   - If positions match: Continue
   - If positions differ: Snap to server position

Benefits:
├── Feels instant (no perceived latency)
├── Works even with 50-100ms ping
├── Smooth gameplay
└── Server still authoritative (anti-cheat)
```

**Capacity Estimation:**
```
Concurrent Players: 1M globally
Matches: 10K concurrent (100 players each)
Position Updates: 1M players × 60 updates/sec = 60M updates/sec

Bandwidth per Player:
├── Sending: 60 updates/sec × 50 bytes = 3 KB/sec
├── Receiving: 99 other players × 60 updates/sec × 50 bytes = 297 KB/sec
├── Total: 300 KB/sec per player = 2.4 Mbps
└── Acceptable for modern internet

Server Requirements:
├── Each game server: 100 players max
├── 10K matches = 10K game servers
├── CPU-intensive (physics simulation)
├── Dedicated servers (not containerized)
└── Geographic distribution (low latency)
```

**Interview Talking Points:**
```
"For online gaming, I'd use UDP with TCP fallback:

1. UDP for Position Updates (CRITICAL):
   - Games need <50ms latency
   - TCP adds 20-30ms overhead (retransmission, ordering)
   - UDP: 1-10ms latency
   - Packet loss acceptable (client interpolates)
   - Essential for competitive gaming (CS:GO, Valorant)
   
2. TCP for Critical Events:
   - Chat messages must arrive
   - Score updates must be reliable
   - Match results must be ordered
   - Use separate TCP connection
   
3. Client-Side Prediction:
   - Mitigate network latency
   - Show movement immediately
   - Reconcile with server later
   - Makes 100ms ping feel like 10ms
   
4. WebSocket for Browser Games:
   - Browser can't use raw UDP
   - WebSocket acceptable for casual games
   - 20-50ms latency (vs 1-10ms UDP)
   - Works for Agar.io, not Call of Duty
   
5. REST for Game Metadata:
   - Player stats, leaderboards, matchmaking
   - Not latency-critical
   - Cacheable
   - Standard operations

UDP vs TCP Trade-off:
- UDP: Fast but unreliable (perfect for position)
- TCP: Slow but reliable (perfect for events)
- Gaming needs BOTH - hybrid approach
- This is why native games outperform browser games"
```

---

### Question 9: Design IoT System (Smart Home)

**Requirements:**
- 100M devices (sensors, cameras, thermostats)
- Telemetry data collection
- Remote control
- Battery-powered devices
- Intermittent connectivity

**Protocol Selection:**

```
Primary Protocol: MQTT (IoT-Optimized)

Architecture:
┌─────────────────┐        MQTT         ┌──────────────┐
│ IoT Devices     │────Publish──────────→│ MQTT Broker  │
│ (Temperature,   │◄───Subscribe─────────│ (Mosquitto/  │
│  Motion, etc.)  │                      │  HiveMQ)     │
└─────────────────┘                      └──────┬───────┘
                                                │
                                    ┌───────────┼───────────┐
                                    ↓           ↓           ↓
                              [Kafka]    [Time-Series]  [Alert]
                              [Bridge]   [DB:InfluxDB] [Service]
                                    │           │
                                    ↓           ↓
                              [Analytics]  [Dashboard]
                              [Pipeline]   [Service]
```

**Why MQTT (NOT HTTP):**

```
MQTT vs HTTP Comparison:

┌──────────────────┬─────────────┬──────────────┐
│ Aspect           │ MQTT        │ HTTP         │
├──────────────────┼─────────────┼──────────────┤
│ Header Size      │ 2 bytes     │ 800+ bytes   │
│ Connection       │ Persistent  │ Per-request  │
│ Bandwidth        │ Very Low    │ High         │
│ Battery Life     │ Weeks       │ Days         │
│ QoS Levels       │ 3 levels    │ No           │
│ Offline Support  │ Built-in    │ Manual       │
│ Bi-directional   │ Pub/Sub     │ Request-Res  │
│ Latency          │ <50ms       │ 100-200ms    │
└──────────────────┴─────────────┴──────────────┘

Battery Impact Example:
Device: Temperature sensor, battery-powered

HTTP (Polling every 60s):
├── Connect (TCP handshake): 50mA for 100ms
├── Send data: 50mA for 100ms
├── Disconnect: 50mA for 50ms
├── Total per send: 12.5 mAh
├── Per day: 1440 sends × 12.5 mAh = 18 Ah
└── Battery life: 3-4 days (small battery)

MQTT (Persistent connection):
├── Connect once: 50mA for 100ms = 1.4 mAh
├── Publish (minimal header): 30mA for 20ms
├── Per send: 0.17 mAh
├── Per day: 1440 sends × 0.17 mAh = 245 mAh
└── Battery life: 30-60 days (same battery)

Result: MQTT provides 10x better battery life
```

**MQTT Features for IoT:**

```
1. Quality of Service (QoS) Levels:

QoS 0 (At most once):
├── Fire and forget
├── No acknowledgment
├── Fast, but may lose messages
└── Use: Non-critical sensor data

QoS 1 (At least once):
├── Acknowledgment required
├── May deliver duplicates
├── Guaranteed delivery
└── Use: Most IoT telemetry

QoS 2 (Exactly once):
├── 4-way handshake
├── Slower, no duplicates
├── Guaranteed exactly once
└── Use: Critical commands (unlock door)

2. Retained Messages:
├── Broker stores last message per topic
├── New subscribers get last state
├── Example: thermostat/temperature
└── Useful for device state sync

3. Last Will and Testament (LWT):
├── Device registers "will" message on connect
├── If device disconnects unexpectedly
├── Broker publishes will message
└── Use: Detect device offline

4. Clean Session vs Persistent Session:
├── Clean: Discard state on disconnect
├── Persistent: Store subscriptions, offline messages
└── Use persistent for battery-powered devices
```

**Topic Design:**

```
MQTT Topic Hierarchy:

home/bedroom/temperature
home/bedroom/humidity
home/bedroom/motion
home/livingroom/temperature
home/garage/door

Wildcards:
+ (single level): home/+/temperature (all rooms)
# (multi level): home/# (everything in home)

Subscribe Patterns:
├── Monitor all bedroom sensors: home/bedroom/#
├── All temperature sensors: home/+/temperature
└── Everything: #
```

**Architecture Patterns:**

```
1. Hub-and-Spoke (Home Automation):
┌────────┐     ┌────────┐     ┌────────┐
│Sensor 1│────→│        │←────│Control │
└────────┘     │  MQTT  │     └────────┘
┌────────┐     │ Broker │     ┌────────┐
│Sensor 2│────→│        │←────│Mobile  │
└────────┘     └────────┘     │ App    │
┌────────┐                    └────────┘
│Sensor N│────→
└────────┘

Benefits:
├── Centralized management
├── Easy to add devices
├── Single point of configuration
└── Can work offline (local broker)

2. Cloud-Connected (Industrial IoT):
┌────────┐           ┌────────┐
│Factory │──MQTT────→│ Cloud  │
│Devices │           │ Broker │
└────────┘           └────┬───┘
                          │
              ┌───────────┼───────────┐
              ↓           ↓           ↓
         [Storage]   [Analytics] [Alerts]

Benefits:
├── Global visibility
├── Advanced analytics
├── Machine learning
└── Remote management
```

**Capacity Estimation:**
```
Devices: 100M (sensors, cameras, actuators)
Active Devices: 50M (50% online at any time)
Telemetry: 50M × (1 message / 60 seconds) = 833K messages/sec

Message Size:
├── Average: 50 bytes (sensor reading)
├── MQTT overhead: 2 bytes
├── Total: 52 bytes per message
└── Bandwidth: 833K × 52 bytes = 43 MB/second

Storage (Time-Series):
├── 833K messages/sec × 52 bytes = 43 MB/sec
├── Per day: 3.7 TB
├── 1 year retention: 1.35 PB
└── With compression: ~400 TB

Broker Requirements:
├── Each broker: 100K connections
├── 50M devices = 500 MQTT brokers
├── With clustering and redundancy
└── Deploy across regions
```

**Interview Talking Points:**
```
"For IoT/Smart Home, MQTT is the clear choice:

1. Battery Efficiency (CRITICAL):
   - Devices are battery-powered
   - HTTP polling: 3-4 days battery life
   - MQTT persistent connection: 30-60 days
   - 10x improvement is game-changing
   
2. Bandwidth Optimization:
   - MQTT header: 2 bytes
   - HTTP header: 800+ bytes
   - For sensor data (50 bytes payload):
     * MQTT: 52 bytes total
     * HTTP: 850 bytes total
   - 16x more efficient
   
3. Pub/Sub Model:
   - Devices publish to topics
   - Services subscribe to topics of interest
   - Decoupled architecture
   - Easy to add new consumers
   
4. QoS Levels:
   - QoS 0 for temperature (lossy OK)
   - QoS 1 for motion detection (need reliability)
   - QoS 2 for door locks (exactly once)
   - Flexible per use case
   
5. Offline Support:
   - Devices may have intermittent connectivity
   - MQTT buffers messages during offline
   - Delivers when connection restored
   - Built-in resilience

Why NOT HTTP:
- 10x worse battery life (unacceptable)
- 16x more bandwidth
- No pub/sub (need polling)
- No QoS levels
- Not designed for IoT constraints

Scale: 100M devices, 833K messages/sec
- 500 MQTT brokers
- 43 MB/second bandwidth
- 3.7 TB/day storage"
```

---

### Question 10: Design Live Streaming (Twitch)

**Requirements:**
- Live video streaming
- Real-time chat
- Low latency (<3 seconds)
- 10M concurrent viewers
- Streamer interactions

**Protocol Selection:**

```
Protocols: WebRTC/RTMP + HTTP/2 (HLS) + WebSocket + REST

Architecture:
┌────────────┐                          ┌──────────────┐
│ Streamer   │──RTMP/WebRTC────────────→│ Ingest Server│
│ (OBS/App)  │                          └──────┬───────┘
└────────────┘                                 │
                                               ↓
                                        [Transcoding]
                                        [Cluster]
                                               │
                            ┌──────────────────┼──────────────┐
                            ↓                  ↓              ↓
                      [Low-Latency]      [Standard]    [VOD Storage]
                      [WebRTC/RTMP]      [HLS/DASH]    [S3]
                            │                  │
        ┌───────────────────┼──────────────────┤
        ↓                   ↓                  ↓
   [Interactive]       [Regular]          [Playback]
   [Viewers]           [Viewers]          [Viewers]
   (<3s latency)       (10-30s latency)   (Any time)
        │                   │
        └────WebSocket──────┘
                │
         [Chat Service]
```

**Protocol Decisions:**

1. **RTMP/WebRTC for Stream Ingestion**
   ```
   Streamer → Platform ingestion:
   
   Option 1: RTMP (Traditional)
   ├── Mature, well-supported
   ├── Works with OBS, streaming software
   ├── TCP-based (reliable)
   ├── 5-10 second latency
   └── Industry standard
   
   Option 2: WebRTC (Modern, Low-Latency)
   ├── <1 second latency possible
   ├── Browser-based (no software needed)
   ├── UDP-based (faster)
   ├── More complex to implement
   └── Growing adoption
   
   Decision: Support both
   ├── RTMP for streamers (compatibility)
   ├── WebRTC for ultra-low-latency
   └── Let streamer choose
   ```

2. **HTTP/2 + HLS for Viewer Delivery**
   ```
   Why HLS for majority of viewers:
   ├── CDN compatible (critical for scale)
   ├── Works in all browsers
   ├── Adaptive bitrate
   ├── 10-30 second latency (acceptable)
   └── Can serve millions via CDN
   
   HLS Architecture:
   [Live Stream] → [Transcoder]
                        ↓
            [Generate HLS segments]
            (360p, 720p, 1080p)
                        ↓
                    [CDN Push]
                        ↓
                 [10M viewers]
   
   Latency Breakdown:
   ├── Encoding: 2-3 seconds
   ├── Segmenting: 4-6 seconds (10s chunks)
   ├── CDN propagation: 2-4 seconds
   ├── Client buffering: 2-3 seconds
   └── Total: 10-16 seconds
   
   Trade-off: Latency vs Scale
   ├── Lower latency: Use WebRTC (<1s)
   ├── But WebRTC doesn't scale as well
   └── HLS scales to millions via CDN
   ```

3. **WebSocket for Chat**
   ```
   Chat Requirements:
   ├── 10M viewers, 10% active in chat = 1M
   ├── Messages: 100 messages/second per channel
   ├── Need bidirectional (viewers send/receive)
   └── Real-time (<100ms)
   
   Architecture:
   [Viewer 1] ──WebSocket──→ ┌──────────────┐
   [Viewer 2] ──WebSocket──→ │  Chat Server │
   [Viewer N] ──WebSocket──→ │  (100K conn) │
                              └──────┬───────┘
                                     │
                              ┌──────┴───────┐
                              ↓              ↓
                        [Redis Pub/Sub] [Chat DB]
                                           (Cassandra)
   
   Why WebSocket:
   ├── Chat is bidirectional
   ├── Need <100ms latency
   ├── Persistent connection efficient
   └── Real-time moderation possible
   ```

4. **REST for Stream Management**
   ```
   APIs:
   ├── POST /streams/start (begin streaming)
   ├── POST /streams/stop (end streaming)
   ├── GET /streams/:id (stream info)
   ├── GET /streams/live (discover live streams)
   └── POST /clips (create clip from VOD)
   
   Why REST:
   ├── Management operations (not streaming)
   ├── Standard CRUD
   ├── Easy authentication
   └── Cacheable discovery pages
   ```

**Low-Latency Streaming Approaches:**

```
Approach 1: HLS (Standard)
Latency: 10-30 seconds
Pros: CDN-friendly, scales to millions
Cons: High latency

Approach 2: Low-Latency HLS (LL-HLS)
Latency: 2-5 seconds
Pros: CDN-friendly, lower latency
Cons: More complex, limited support

Approach 3: WebRTC (Ultra-Low-Latency)
Latency: <1 second (sub-second possible)
Pros: Best latency, interactive
Cons: Doesn't scale well, CDN support limited

Approach 4: Hybrid (Twitch's Approach)
├── Interactive viewers: WebRTC (<1s)
├── Regular viewers: HLS (10-30s)
├── VOD viewers: HLS from S3
└── Best of both worlds
```

**Capacity Estimation:**
```
Concurrent Streams: 100K live streams
Viewers: 10M concurrent (average 100 viewers/stream)
Peak: 50M concurrent (major events)

Ingest Bandwidth:
├── 100K streams × 5 Mbps (1080p) = 500 Gbps
├── Need transcoding immediately
└── Output: 500 Gbps × 5 qualities = 2.5 Tbps

Distribution Bandwidth:
├── 10M viewers × 2 Mbps (average) = 20 Tbps
├── With CDN caching: 2 Tbps from origin
└── CDN serves: 18 Tbps (90% offload)

Chat Messages:
├── 1M active chatters
├── 10 messages/minute per user
├── = 167K messages/second
├── 10K WebSocket connections per server
└── Need: 100 chat servers

Storage (VODs):
├── 100K streams × 2 hours average = 200K hours/day
├── 1 hour @ 1080p ≈ 2 GB
├── Per day: 400 TB
├── 90 days retention: 36 PB
└── With compression: ~25 PB
```

**Interview Talking Points:**
```
"For Twitch, I'd use a hybrid streaming approach:

1. RTMP for Stream Ingestion:
   - Standard protocol for streamers
   - Works with OBS, XSplit, etc.
   - Reliable (TCP-based)
   - 5-10 second latency acceptable for ingestion
   
2. HLS for Most Viewers:
   - 90% of viewers use HLS
   - CDN-friendly (scales to millions)
   - 10-30 second latency acceptable for viewing
   - Can serve 10M viewers easily
   
3. WebRTC for Interactive Streams:
   - Sub-second latency for streamers
   - Important for fast-paced games
   - Viewer participation (co-streaming)
   - Doesn't scale as well (use for <10K viewers)
   
4. WebSocket for Chat:
   - 1M concurrent chatters
   - <100ms message delivery
   - Bi directional (viewers send, receive)
   - Essential for community engagement
   
5. Trade-off Analysis:
   - Low latency (WebRTC): <1s, but expensive
   - Standard latency (HLS): 10-30s, but scales
   - Choose based on content type:
     * Gaming tournaments: WebRTC
     * Casual streams: HLS
   - Hybrid approach serves both needs

Scale Considerations:
- 20 Tbps bandwidth (CDN handles 90%)
- 100 chat servers for 1M chatters
- 400 TB/day for VOD storage
- This architecture handles Twitch's scale"
```

---

## Protocol Comparison Matrix

### Complete Protocol Comparison Table

```
┌────────────┬─────────┬─────────┬──────────┬─────────┬─────────┬──────────┬─────────┐
│ Protocol   │ Latency │ Through │ Direction│ Data    │ Battery │ Scale   │ Use Case│
│            │         │ -put    │          │ Type    │         │         │         │
├────────────┼─────────┼─────────┼──────────┼─────────┼─────────┼──────────┼─────────┤
│ HTTP/REST  │ 50-100ms│ 10K QPS │ Req-Res  │ Text    │ Medium  │ Very High│ CRUD API│
│ gRPC       │ 5-10ms  │ 100K QPS│ 4 Patterns│Binary  │ Good    │ Very High│ Micro-  │
│            │         │         │          │(Protobuf│         │          │ services│
│ WebSocket  │ 50-100ms│ High    │ Bi-dir   │ Both    │ Good    │ High     │ Chat,   │
│            │         │         │          │         │         │          │ Gaming  │
│ GraphQL    │ 50-100ms│ Medium  │ Req-Res  │ Text    │ Medium  │ High     │ Complex │
│            │         │         │          │ (JSON)  │         │          │ Data    │
│ SSE        │ 50-100ms│ Medium  │ Server→C │ Text    │ Good    │ Medium   │ Live    │
│            │         │         │          │         │         │          │ Updates │
│ Long Poll  │ 0.5-2s  │ Low     │ Req-Res  │ Text    │ Poor    │ Low      │ Fallback│
│ Kafka      │ 10-50ms │ 1M msg/s│ Pub-Sub  │ Both    │ N/A     │ Very High│ Event   │
│            │         │         │          │         │         │          │ Stream  │
│ RabbitMQ   │ 10-100ms│ 20K m/s │ Pub-Sub  │ Both    │ N/A     │ High     │ Task    │
│            │         │         │          │         │         │          │ Queue   │
│ MQTT       │ 10-50ms │ High    │ Pub-Sub  │ Both    │ Excellent│ High    │ IoT     │
│ TCP        │ 10-50ms │ High    │ Stream   │ Bytes   │ N/A     │ Very High│ Reliable│
│ UDP        │ 1-10ms  │ Very    │ Datagram │ Bytes   │ N/A     │ Very High│ Real-   │
│            │         │ High    │          │         │         │          │ time    │
└────────────┴─────────┴─────────┴──────────┴─────────┴─────────┴──────────┴─────────┘
```

### Use Case to Protocol Mapping

```
┌─────────────────────────────────┬──────────────────────────────────────┐
│ Use Case                        │ Protocol Choice                      │
├─────────────────────────────────┼──────────────────────────────────────┤
│ Public REST API                 │ HTTP/REST + JSON                     │
│ Mobile App Backend              │ REST (main) + WebSocket (real-time)  │
│ Internal Microservices          │ gRPC + Protobuf                      │
│ Real-Time Chat                  │ WebSocket (primary)                  │
│ Live Notifications              │ WebSocket or SSE                     │
│ Social Media Feed               │ REST + WebSocket (hybrid)            │
│ Video Streaming                 │ HTTP/2 + HLS/DASH                    │
│ Live Streaming (<3s latency)    │ WebRTC or LL-HLS                     │
│ E-commerce                      │ REST + GraphQL (mobile)              │
│ Collaborative Editing           │ WebSocket + Operational Transform    │
│ Stock Trading                   │ WebSocket + gRPC + Kafka             │
│ Ride Sharing (GPS tracking)     │ WebSocket + gRPC + Redis Geo         │
│ Online Gaming (FPS)             │ UDP + TCP (hybrid)                   │
│ IoT / Smart Home                │ MQTT                                 │
│ Event-Driven Architecture       │ Kafka or RabbitMQ                    │
│ Task Queue / Background Jobs    │ RabbitMQ or AWS SQS                  │
│ Logs Aggregation                │ Kafka + gRPC streaming               │
│ Metrics Collection              │ MQTT (devices) or gRPC (servers)     │
│ File Upload/Download            │ HTTP/REST with multipart or chunking │
│ GraphQL API                     │ HTTP/REST (POST to /graphql)         │
└─────────────────────────────────┴──────────────────────────────────────┘
```

---

## Protocol Decision Framework

### Decision Flowchart

```
Start: Need to choose communication protocol

                    ┌─────────────────┐
                    │ Real-time       │
                    │ Required?       │
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    ↓                 ↓
                 [YES]             [NO]
                    │                 │
                    │                 ↓
                    │          ┌──────────────┐
                    │          │ Public API?  │
                    │          └──────┬───────┘
                    │                 │
                    │          ┌──────┴──────┐
                    │          ↓             ↓
                    │       [YES]         [NO]
                    │          │             │
                    │          ↓             ↓
                    │      [REST API]   ┌─────────────┐
                    │                   │ Microservice│
                    │                   │ Internal?   │
                    │                   └──────┬──────┘
                    │                          │
                    │                   ┌──────┴──────┐
                    │                   ↓             ↓
                    │                [YES]         [NO]
                    │                   │             │
                    │                   ↓             ↓
                    │              [gRPC]        [REST]
                    │
                    ↓
            ┌───────────────┐
            │ Bidirectional?│
            └───────┬───────┘
                    │
            ┌───────┴────────┐
            ↓                ↓
         [YES]            [NO]
            │                │
            ↓                ↓
    ┌──────────────┐  ┌──────────────┐
    │ Binary data? │  │ Browser      │
    └──────┬───────┘  │ Client?      │
           │          └──────┬───────┘
    ┌──────┴──────┐          │
    ↓             ↓   ┌──────┴──────┐
 [YES]         [NO]  ↓             ↓
    │             │ [YES]         [NO]
    ↓             ↓   │             │
[WebSocket]      │   ↓             ↓
[+Binary]        │ [SSE]     [WebSocket]
                 │           [or SSE]
                 ↓
         ┌──────────────┐
         │ Chat/Gaming? │
         └──────┬───────┘
                │
         ┌──────┴──────┐
         ↓             ↓
    [Chat/Collab]  [Gaming]
         │             │
         ↓             ↓
   [WebSocket]   [UDP+TCP]
```

### Decision Matrix by Requirements

```
Requirement: Low Latency (<10ms)
└── Internal: gRPC
└── Gaming: UDP
└── Else: WebSocket

Requirement: High Throughput (>100K QPS)
└── Internal: gRPC
└── Events: Kafka
└── Else: HTTP/2

Requirement: Bidirectional Communication
└── Real-time: WebSocket
└── Else: Two REST endpoints

Requirement: Server Push Only
└── Text data: SSE
└── Binary data: WebSocket
└── Else: Long Polling (fallback)

Requirement: Battery Efficiency
└── IoT devices: MQTT
└── Mobile: REST with caching
└── Else: WebSocket (better than polling)

Requirement: CDN Caching
└── Static files: HTTP/2
└── Video: HLS/DASH
└── API: REST
└── Not WebSocket (bypasses CDN)

Requirement: Public API
└── Third-party: REST
└── Mobile-optimized: GraphQL
└── Not gRPC (poor tooling for external devs)

Requirement: Strong Typing
└── Use: gRPC or GraphQL
└── Not REST (unless OpenAPI)

Requirement: Event-Driven Architecture
└── High throughput: Kafka
└── Task queues: RabbitMQ
└── Simple: AWS SQS

Requirement: IoT / Resource-Constrained
└── Use: MQTT
└── Fallback: CoAP
└── Not HTTP (too heavy)
```

---

## Performance Benchmarks & Metrics

### Latency Comparison

```
┌──────────────┬───────────────┬────────────────┬──────────────┐
│ Protocol     │ First Request │ Subsequent     │ Payload Size │
│              │ (Cold Start)  │ (Warm)         │ (User Object)│
├──────────────┼───────────────┼────────────────┼──────────────┤
│ HTTP/REST    │ 130-370ms     │ 50-100ms       │ 950 bytes    │
│              │ (DNS+TCP+TLS) │ (keep-alive)   │ (with header)│
├──────────────┼───────────────┼────────────────┼──────────────┤
│ gRPC         │ 100-200ms     │ 5-10ms         │ 50 bytes     │
│              │ (TCP+TLS+H2)  │ (multiplexed)  │ (with header)│
├──────────────┼───────────────┼────────────────┼──────────────┤
│ WebSocket    │ 150-300ms     │ 50-100ms       │ 52 bytes     │
│              │ (WS handshake)│ (persistent)   │ (JSON frame) │
├──────────────┼───────────────┼────────────────┼──────────────┤
│ GraphQL      │ 130-370ms     │ 50-150ms       │ Variable     │
│              │ (same as HTTP)│ (depends on    │ (optimized)  │
│              │               │  query)        │              │
├──────────────┼───────────────┼────────────────┼──────────────┤
│ SSE          │ 130-300ms     │ 50-100ms       │ 102 bytes    │
│              │               │ (push instant) │ (event frame)│
├──────────────┼───────────────┼────────────────┼──────────────┤
│ MQTT         │ 50-100ms      │ 10-50ms        │ 52 bytes     │
│              │               │ (persistent)   │ (tiny header)│
├──────────────┼───────────────┼────────────────┼──────────────┤
│ UDP          │ N/A           │ 1-10ms         │ Minimal      │
│              │ (no handshake)│ (no overhead)  │ (app-defined)│
├──────────────┼───────────────┼────────────────┼──────────────┤
│ TCP          │ 50-100ms      │ 10-50ms        │ Minimal      │
│              │ (handshake)   │ (stream)       │ (app-defined)│
└──────────────┴───────────────┴────────────────┴──────────────┘
```

### Throughput Comparison (Single Server)

```
┌──────────────┬──────────────┬───────────────────┬─────────────┐
│ Protocol     │
