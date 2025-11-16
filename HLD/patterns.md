# System Design Patterns - Comprehensive Interview Guide

**Source:** Hello Interview (https://www.hellointerview.com/learn/system-design/patterns/)  
**Version:** 2.0 - Extended Edition (No Code)

---

## Table of Contents

1. [Real-time Updates](#1-real-time-updates)
2. [Dealing with Contention](#2-dealing-with-contention)
3. [Multi-step Processes](#3-multi-step-processes)
4. [Scaling Reads](#4-scaling-reads)
5. [Scaling Writes](#5-scaling-writes)
6. [Handling Large Blobs](#6-handling-large-blobs)
7. [Managing Long Running Tasks](#7-managing-long-running-tasks)
8. [Pattern Combinations](#8-pattern-combinations)
9. [Interview Strategy Guide](#9-interview-strategy-guide)

---

## 1. Real-time Updates

### Overview
**Real-time Updates** addresses the challenge of delivering immediate notifications and data changes from servers to clients as events occur. From chat applications where messages need instant delivery to live dashboards showing real-time metrics, users expect to be notified the moment something happens.

### The Problem

#### Scenario: Collaborative Document Editor (Google Docs)

**Requirements:**
- 50 users editing same document
- Each keystroke must appear on all screens within 100ms
- Must handle network failures gracefully
- Maintain edit conflict resolution
- Scale to 10,000 concurrent documents

**Why Traditional HTTP Fails:**
```
Simple Polling (BAD):
- Client requests every 100ms: 10 requests/sec per user
- 50 users = 500 requests/sec per document
- 10,000 documents = 5 MILLION requests/sec
- Each request: 3-way TCP handshake + HTTP headers (~1KB overhead)
- Bandwidth: 5GB/sec just for polling overhead!
```

**The Challenge:**
Standard HTTP request-response model:
1. Client opens connection
2. Client sends request
3. Server processes
4. Server sends response
5. Connection closes

Problem: Server can't initiate communication!

### Solutions

#### 1. Simple Polling (Baseline - Don't Use)

**How it works:**
- Client repeatedly requests server for updates
- Check every N seconds
- Extremely inefficient

**Performance Analysis:**
```
Cost per user per hour:
- Requests: 3,600 (1 per second)
- Data transferred: ~360KB
- Server load: 3,600 DB queries/hour

For 1 million users:
- 3.6 BILLION requests/hour
- 1 MILLION requests/second
- Result: System collapses
```

**When to use:**
- Never in production

**Trade-offs:**
- ✅ Simple to implement
- ❌ Extremely wasteful
- ❌ High latency
- ❌ Doesn't scale

#### 2. Long Polling (Better, Still Limited)

**How it works:**
- Client makes request
- Server holds connection open
- Server responds when data available or timeout
- Client immediately reconnects

**Improvements:**
- Reduces requests from 3,600/hour to ~120/hour (30s timeout)
- Lower latency: ~0-30s instead of 0-1s
- Still issues: Connection management, scalability

**When to use:**
- Legacy systems
- Simple real-time needs

**Trade-offs:**
- ✅ Better than polling
- ✅ Works through firewalls
- ❌ Still resource intensive
- ❌ Complex connection management

#### 3. Server-Sent Events (SSE)

**How it works:**
- One-way communication (server to client)
- Client opens persistent connection
- Server pushes updates over same connection
- Automatic reconnection built-in

**Performance Metrics:**
```
SSE vs Polling (1 million users):

SSE:
- Connections: 1 million persistent
- Memory: ~1MB per 1000 connections = 1GB total
- Bandwidth: Only when updates occur
- CPU: Minimal (event-driven)

Polling (1s interval):
- Requests: 1 million/second
- DB queries: 1 million/second
- Bandwidth: Constant 1GB/sec minimum
```

**When to use:**
- One-way updates (server to client)
- Notifications, dashboards
- Stock tickers, news feeds

**Trade-offs:**
- ✅ Efficient bandwidth use
- ✅ Auto-reconnection
- ✅ Simple client-side API
- ❌ One-way only
- ❌ Connection limits per domain
- ❌ Not supported in IE

#### 4. WebSockets (Full Duplex)

**How it works:**
- Bidirectional communication
- Single TCP connection
- Low overhead after handshake
- Real-time both directions

**Architecture:**
```
                    Load Balancer
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
   WS Server 1       WS Server 2       WS Server 3
        |                 |                 |
        └─────────────────┼─────────────────┘
                          |
                    Redis Pub/Sub
                          |
                      Database
```

**When to use:**
- Chat applications
- Gaming
- Collaborative editing
- Trading platforms

**Trade-offs:**
- ✅ Full duplex
- ✅ Very low latency
- ✅ Efficient
- ❌ Complex infrastructure
- ❌ Stateful connections
- ❌ Load balancing challenges

#### 5. WebRTC (Peer-to-Peer)

**How it works:**
- Direct peer-to-peer connection
- Signaling server for initial handshake
- STUN/TURN servers for NAT traversal
- Media streams directly between clients

**When to use:**
- Video/audio calls
- Screen sharing
- File transfer
- Gaming

**Trade-offs:**
- ✅ Lowest possible latency
- ✅ Reduced server load
- ✅ Privacy (direct connection)
- ❌ Very complex setup
- ❌ NAT traversal challenges
- ❌ Not all connections possible

### Performance Comparison

| Method | Latency | Bandwidth | Scalability | Complexity | Use Case |
|--------|---------|-----------|-------------|------------|----------|
| Polling | 0-1s | Very High | Poor | Low | Never use |
| Long Polling | 0-30s | High | Medium | Medium | Legacy |
| SSE | 0-100ms | Low | Good | Medium | Notifications |
| WebSocket | 0-50ms | Very Low | Excellent | High | Chat, gaming |
| WebRTC | 0-20ms | Direct P2P | Excellent | Very High | Video/audio |

### Real-World Example: Slack

**Multi-Layer Approach:**

1. **Message Flow:**
   - WebSocket for real-time delivery
   - Redis Pub/Sub for distribution
   - Multiple WebSocket servers
   - Database for persistence

2. **Presence Updates:**
   - Redis sorted sets for online users
   - Heartbeat every 30s
   - Broadcast via WebSocket

3. **Typing Indicators:**
   - Client throttled to 1 event per 3s
   - Server broadcasts (doesn't persist)
   - Auto-expires after 3s

4. **Message History:**
   - REST API for initial load
   - WebSocket for new messages
   - Pagination for older messages

### Common Deep Dives

#### Q1: Connection failures and reconnection?

**Strategies:**
- Exponential backoff: 1s, 2s, 4s, 8s...
- Queue messages while offline
- Request missed messages on reconnect
- Track last received message ID
- Maximum reconnect delay (30s)

**Interview Answer:**
"Implement robust reconnection with exponential backoff to avoid overwhelming the server. Queue messages locally while offline and flush on reconnect. Track the last successfully received message ID so you can request any missed messages when reconnecting."

#### Q2: Single user with millions of followers?

**Problem:** Celebrity posts → 100M followers need update

**Solutions:**

**Fan-out on Write (Push):**
- Pre-compute timelines
- Push to all followers
- Good for regular users (< 10K followers)

**Fan-out on Read (Pull):**
- Compute timeline on demand
- Fetch from followed users
- Good for celebrities (> 100K followers)

**Hybrid (Twitter's Approach):**
- Regular users: Fan-out on write
- Celebrities: Fan-out on read
- Mix both in timeline generation

**Interview Answer:**
"Use a hybrid approach. For regular users, pre-compute and push updates to followers' timelines. For celebrities, compute timelines on-demand by pulling from the celebrity's recent posts. This prevents overwhelming the system when celebrities post while maintaining fast delivery for most users."

#### Q3: Message ordering across distributed servers?

**Solutions:**

**Lamport Timestamps:**
- Logical clock per server
- Increment on each event
- Simple but doesn't capture causality

**Vector Clocks:**
- Track timestamp from each server
- Detect concurrent updates
- Complex but captures causality

**Centralized Sequencer:**
- Single service assigns sequence numbers
- Simple and effective
- Requires high availability

**Interview Answer:**
"For most use cases, use a centralized sequencer with Redis to assign monotonically increasing sequence numbers. It's simple and effective. Only use vector clocks if you need to handle complex conflict resolution like collaborative document editing."

---

## 2. Dealing with Contention

### Overview
**Contention** occurs when multiple users attempt to access or modify the same resource simultaneously. This pattern addresses concurrent access while maintaining consistency.

### The Problem

#### Scenario: Concert Ticket Booking

**Requirements:**
- 50,000 seats
- 2 million fans in first 5 minutes
- No double-booking
- Fair allocation
- High availability

**The Challenge:**
```
Without proper handling:
- 2M users simultaneously
- 100K+ concurrent writes
- Race conditions
- Double-booking
- Angry customers
```

### Solutions

#### 1. Optimistic Locking

**How it works:**
- Read with version number
- Perform operation locally
- Write only if version unchanged
- Retry on conflict

**When to use:**
- Low contention
- Read-heavy workloads
- Can tolerate retries

**Trade-offs:**
- ✅ High throughput when low contention
- ✅ No locks
- ❌ Wasted work on conflicts
- ❌ Poor under high contention

#### 2. Pessimistic Locking

**How it works:**
- Acquire lock before reading
- Hold lock during operation
- Release after write
- Others wait

**Lock Types:**
- Row-level locks
- Table-level locks
- Distributed locks (Redis/Zookeeper)

**When to use:**
- High contention
- Write-heavy
- Cannot tolerate conflicts

**Trade-offs:**
- ✅ Prevents conflicts
- ✅ Predictable
- ❌ Lower throughput
- ❌ Deadlock risk
- ❌ Bottleneck

#### 3. Queue-Based Processing

**How it works:**
- Accept all requests immediately
- Queue for processing
- Process sequentially
- Return asynchronously

**When to use:**
- Can tolerate async
- Handle spikes
- Guarantee processing

**Trade-offs:**
- ✅ Handles unlimited requests
- ✅ System responsive
- ✅ Can retry
- ❌ Not real-time
- ❌ Complex error handling
- ❌ Status tracking needed

#### 4. Sharding/Partitioning

**How it works:**
- Divide data into partitions
- Each handles subset
- Contention only within partition

**Strategies:**
- Geographic sharding
- Hash sharding
- Range sharding
- Feature sharding

**When to use:**
- Natural boundaries
- Horizontal scaling needed

**Trade-offs:**
- ✅ Dramatically reduces contention
- ✅ Scales horizontally
- ❌ Cross-partition complexity
- ❌ Rebalancing difficult

#### 5. Reservation System

**How it works:**
- Pre-allocate in batches
- Each server gets pool
- Local allocation
- Refresh periodically

**When to use:**
- High-volume allocation
- Can pre-allocate
- Occasional waste OK

**Trade-offs:**
- ✅ Eliminates contention
- ✅ High throughput
- ❌ Some waste
- ❌ Rebalancing overhead

### Real-World Example: Ticketmaster

**Multi-Layer Approach:**

1. **Virtual Waiting Room:**
   - Random order assignment
   - Rate-limited entry
   - Prevents rush

2. **Reservation Pool:**
   - Each server gets seats
   - 5-minute soft reservation
   - Returns if abandoned

3. **Database Strategy:**
   - Pessimistic locks on purchase
   - Row-level locking
   - Short duration (< 100ms)

4. **Caching:**
   - Redis tracks inventory
   - Immediate decrement
   - Periodic reconciliation

### Common Deep Dives

#### Q1: Prevent deadlocks?

**Strategies:**
1. Lock ordering (always same order)
2. Timeout (fail fast)
3. Deadlock detection (database)
4. Two-phase locking

**Interview Answer:**
"Enforce consistent lock ordering. Always acquire locks in the same order (e.g., ascending account_id). Set timeouts so transactions fail fast rather than waiting indefinitely."

#### Q2: Distributed locks at scale?

**Options:**

**Redis (Redlock):**
- Fast but not 100% safe
- Good for most cases

**Zookeeper:**
- Slower but more reliable
- Good for critical operations

**Database:**
- Simple for basic needs

**Interview Answer:**
"Use Redis Redlock for high performance when rare failures are acceptable. Use Zookeeper for critical operations requiring absolute safety. Database advisory locks work well for simple cases."

#### Q3: Stock trading - no overselling?

**Solution:**

1. **Inventory Service:**
   - Redis atomic DECR
   - If < 0: reject and INCR back

2. **Flow:**
   - Check inventory (DECR)
   - If >= 0: queue order
   - Process payment
   - Success: confirm
   - Failure: return inventory (INCR)

3. **Safety:**
   - Soft reservation (5 min)
   - Timeout releases
   - Reconciliation

**Interview Answer:**
"Use Redis atomic DECR for inventory. It's single-threaded so naturally serialized. Combined with soft reservations that timeout, idempotency keys, and regular reconciliation, this prevents overselling while maintaining high throughput."

---

## 3. Multi-step Processes

### Overview
**Multi-step Processes** span multiple services or time periods. This pattern ensures consistency when single operations require multiple coordinated actions.

### The Problem

#### Scenario: E-commerce Order

**Requirements:**
- 5 steps: validate, reserve, payment, create, email
- Rollback on any failure
- Atomic (all or nothing)
- Handle partial failures

**The Challenge:**
```
Payment succeeds but order creation fails:
- User charged but no order
- Inventory reserved but not sold
- Inconsistent database
- Angry customer
```

### Solutions

#### 1. Database Transactions (ACID)

**How it works:**
- BEGIN TRANSACTION
- Execute operations
- COMMIT if all succeed
- ROLLBACK if any fails

**Limitations:**
- Single database only
- Long transactions hold locks
- Can't span microservices

**Trade-offs:**
- ✅ Strong consistency
- ✅ Simple model
- ❌ Single database only
- ❌ Poor for long operations

#### 2. Two-Phase Commit (2PC)

**How it works:**

**Phase 1 (Prepare):**
- Coordinator asks all: "Can you commit?"
- Participants lock resources
- Respond YES/NO

**Phase 2 (Commit/Abort):**
- All YES: Tell all to COMMIT
- Any NO: Tell all to ABORT

**Trade-offs:**
- ✅ Strong consistency
- ✅ All-or-nothing
- ❌ Blocking (low availability)
- ❌ Single point of failure
- ❌ Not suitable for microservices

#### 3. Saga Pattern (Preferred)

**How it works:**
- Break into local transactions
- Each service publishes event
- On failure: compensating transactions

**Implementations:**

**Choreography:**
- No coordinator
- Services react to events
- Decentralized

**Orchestration:**
- Central coordinator
- Directs flow
- Easier to manage

**Example Compensation:**
```
1. Reserve Inventory → Success
2. Process Payment → Success  
3. Create Order → FAILURE

Compensation:
1. Refund Payment
2. Release Inventory
```

**Trade-offs:**
- ✅ High availability
- ✅ Scales across services
- ❌ Complex error handling
- ❌ Eventual consistency
- ❌ Compensation required

#### 4. Idempotency Pattern

**How it works:**
- Assign unique ID
- Store processed IDs
- Return cached on duplicate
- Prevents duplicates

**Trade-offs:**
- ✅ Safe to retry
- ✅ Prevents duplicates
- ❌ Must store IDs
- ❌ Expiry needed

#### 5. Outbox Pattern

**How it works:**
- Store event with data (same transaction)
- Background publishes events
- Guarantees delivery

**Trade-offs:**
- ✅ Guaranteed delivery
- ✅ No dual-write problem
- ❌ Additional complexity
- ❌ Polling overhead

### Real-World Example: Uber Ride

**Saga Implementation:**

**Steps:**
1. Find Driver
2. Reserve Driver
3. Calculate Fare
4. Pre-auth Payment
5. Create Ride
6. Notify Parties

**Compensations:**
If step 4 fails:
- Cancel fare
- Release driver
- Return to pool

**Why Saga over 2PC:**
- Driver might not respond (can't block)
- Payment gateway external
- High availability critical

### Common Deep Dives

#### Q1: Compensation fails?

**Solutions:**
1. Retry with backoff
2. Dead letter queue
3. Human workflow
4. Make idempotent

**Interview Answer:**
"Implement retry logic with exponential backoff. If fails after retries, route to dead letter queue for manual intervention. Key is making compensations idempotent so retries are safe."

#### Q2: Saga vs 2PC?

**Decision Matrix:**

| Factor | Saga | 2PC |
|--------|------|-----|
| System | Microservices | Monolith |
| Consistency | Eventual | Immediate |
| Availability | High | Lower |
| Operations | Long | Fast |
| External | Yes | No |

**Interview Answer:**
"Saga for microservices despite complexity. 2PC for tightly-coupled systems. Saga provides high availability but requires handling eventual consistency and compensations."

#### Q3: Exactly-once semantics?

**Solution Layers:**

1. **Producer:** Idempotency keys
2. **Queue:** Deduplication
3. **Consumer:** Idempotent processing
4. **Database:** Unique constraints

**Interview Answer:**
"Achieve effectively exactly-once through idempotency at every layer. Use idempotency keys at API, enable queue deduplication, make operations idempotent with unique constraints."

---

## 4. Scaling Reads

### Overview
**Scaling Reads** handles millions of read requests with low latency. Most systems are 90-95% reads.

### The Problem

#### Scenario: Reddit

**Requirements:**
- 10M daily users
- 50 posts/user/day
- 500M reads/day
- < 100ms response
- Single DB: 10K reads/sec max

**The Challenge:**
```
500M reads/day = 5,787/sec average
Peak (8PM): 57,870/sec

Gap: 47,870/sec must be handled elsewhere
```

### Solutions

#### 1. Database Replication

**How it works:**
- Primary handles writes
- Multiple replicas copy data
- Distribute reads across replicas

**Strategies:**
- Synchronous (strong consistency)
- Asynchronous (eventual consistency)
- Semi-synchronous (hybrid)

**Handling Lag:**
- Read-your-writes consistency
- Monotonic reads
- Bounded staleness

**Trade-offs:**
- ✅ Easy to implement
- ✅ Linear scaling
- ❌ Replication lag
- ❌ Doesn't help writes

#### 2. Caching (Most Effective)

**Cache Levels:**

**L1 - Application (in-process):**
- < 1ms
- 100MB-1GB
- No network

**L2 - Distributed (Redis):**
- 1-5ms
- 10GB-100GB+
- Shared

**L3 - CDN (edge):**
- Variable
- TBs
- Static content

**Cache Patterns:**
- Cache-Aside (lazy loading)
- Read-Through
- Write-Through
- Write-Behind

**Invalidation:**
- TTL (time-based)
- Event-based
- Version-based

**Trade-offs:**
- ✅ 100x faster
- ✅ Reduces DB load
- ❌ Invalidation complexity
- ❌ Memory limits

#### 3. Content Delivery Network

**How it works:**
- Edge servers worldwide
- Serve from nearest location
- Origin updated periodically

**Optimizations:**
- Image resizing
- Format conversion
- Video adaptive bitrate

**Trade-offs:**
- ✅ 10-100x faster globally
- ✅ Offloads origin
- ❌ Cost
- ❌ Invalidation complex

#### 4. Database Indexing

**Index Types:**
- B-Tree (ranges)
- Hash (equality)
- Full-Text (search)
- Composite (multiple columns)

**Strategy:**
- Index WHERE columns
- Index foreign keys
- Monitor performance
- Remove unused

**Trade-offs:**
- ✅ 1000x+ speedup
- ✅ Essential
- ❌ Slows writes
- ❌ Uses space

#### 5. Materialized Views / CQRS

**Materialized Views:**
- Pre-computed results
- Refreshed periodically
- Fast reads

**CQRS:**
- Separate read/write models
- Write: normalized
- Read: denormalized
- Async sync

**Trade-offs:**
- ✅ Extremely fast
- ✅ Independent optimization
- ❌ High complexity
- ❌ Eventual consistency

### Real-World Example: Instagram

**Multi-Layer:**

1. **CDN:** 95% media from edge
2. **Redis:** Feed cached 5 min
3. **Database:** Primary + 10 replicas
4. **CQRS:** Materialized feeds

### Common Deep Dives

#### Q1: Cache stampede?

**Solutions:**
- Probabilistic early expiration
- Locking/mutex
- Background refresh

**Interview Answer:**
"Use background refresh. Worker monitors TTL and refreshes before expiration. Users never experience cache misses. For less critical data, use probabilistic early expiration with jitter."

#### Q2: What to cache?

**Decision Framework:**
1. Access frequency (80/20 rule)
2. Computation cost
3. Data freshness tolerance
4. Size budget
5. Hit rate

**Interview Answer:**
"Analyze query logs for hot data. Evaluate miss cost - expensive joins are prime candidates. Monitor hit rates and adjust TTLs. Use LRU eviction."

#### Q3: Replica lag confusion?

**Solutions:**
- Read-your-writes consistency
- Version-based routing
- Write-path caching
- Optimistic UI

**Interview Answer:**
"Track write timestamps in sessions. Route user's reads to primary for short period after writes. Ensures users see their own changes while benefiting from replica scaling."

---

## 5. Scaling Writes

### Overview
**Scaling Writes** handles high-volume write operations with consistency and durability. Every write must persist.

### The Problem

#### Scenario: Twitter

**Requirements:**
- 500M users
- 1M tweets/min peak
- 3 writes per tweet
- 50,000 writes/sec needed
- Single DB: 10,000 writes/sec max

**The Challenge:**
```
Gap: 40,000 writes/sec

Additional challenges:
- Data consistency
- Can't lose writes
- Horizontal scaling
- Geographic distribution
```

### Solutions

#### 1. Database Sharding

**How it works:**
- Split data across databases
- Sharding key determines partition
- Application routes requests

**Strategies:**
- Hash-based (even distribution)
- Range-based (hot spots possible)
- Geographic (location-based)
- Directory-based (flexible)

**Challenges:**
- Hot shards
- Cross-shard queries
- Rebalancing
- Transaction complexity

**Trade-offs:**
- ✅ Linear scaling
- ✅ Isolated failures
- ❌ Cross-shard complexity
- ❌ Rebalancing difficult

#### 2. Write-Behind Caching

**How it works:**
- Write to cache
- Return immediately
- Async persist to database
- Background workers batch

**Trade-offs:**
- ✅ Very low latency
- ✅ Smooths spikes
- ❌ Data loss risk
- ❌ Complex recovery

#### 3. Partitioned Message Queues

**How it works:**
- Write to queue
- Queue partitioned
- Workers consume
- Async processing

**Trade-offs:**
- ✅ Excellent buffering
- ✅ Built-in retry
- ✅ Scales independently
- ❌ Eventual consistency
- ❌ Ordering complex

#### 4. Batch Writes

**How it works:**
- Accumulate writes
- Submit as batch
- Reduces overhead

**Strategies:**
- Time-based
- Size-based
- Hybrid

**Trade-offs:**
- ✅ Higher throughput
- ✅ Better utilization
- ❌ Higher latency
- ❌ Partial failure complexity

#### 5. Optimized Write Path

**Strategies:**
- Append-only storage
- LSM trees
- Denormalization
- Async indexes

**Trade-offs:**
- ✅ Optimized for patterns
- ✅ Very fast
- ❌ Less flexible
- ❌ May complicate reads

### Real-World Example: Cassandra

**Why Fast:**

1. **Distributed:** No single primary
2. **LSM Trees:** Memory writes, periodic flush
3. **Write Flow:** Commit log → Memtable → SSTable
4. **Performance:** 50K+ writes/sec per node

### Common Deep Dives

#### Q1: Write conflicts in multi-master?

**Solutions:**
- Last-Write-Wins (timestamp)
- Version Vectors (detect conflicts)
- CRDTs (automatic resolution)
- Application-level resolution

**Interview Answer:**
"Use Last-Write-Wins with NTP-synchronized clocks for most cases. For critical data, use version vectors to detect conflicts and CRDTs for automatic resolution. For user-facing conflicts, present both versions and let users merge."

#### Q2: Hot partitions?

**Solutions:**
- Consistent hashing
- Secondary sharding
- Caching hot data
- Resharding

**Interview Answer:**
"Implement caching for immediate relief. Analyze patterns to identify hot keys. For those, implement secondary sharding splitting hot keys across multiple partitions. Long-term, migrate to consistent hashing with virtual nodes."

#### Q3: Lost writes during failover?

**Solutions:**
- Synchronous replication
- Write-Ahead Log
- Application-level acks
- Distributed consensus

**Interview Answer:**
"Implement synchronous replication with quorum writes. Require acknowledgment from majority before confirming to client. Combine with WAL for durability. Latency cost acceptable for critical data."

---

## 6. Handling Large Blobs

### Overview
**Handling Large Blobs** addresses storing and serving large binary objects efficiently. Traditional databases aren't optimized for this.

### The Problem

#### Scenario: Instagram

**Requirements:**
- 1B photos
- 2MB average
- 2 PB total
- 10M views/min
- Global serving

**The Challenge:**
```
Database issues:
- Table size explodes
- Memory inefficient
- Slow queries
- Expensive backups
- Poor network utilization

Bandwidth: 10M views/min × 2MB = 333GB/sec
```

### Solutions

#### 1. Object Storage (S3, GCS)

**How it works:**
- Store blobs in object storage
- Store metadata in database
- Reference by key/URL

**Best Practices:**
- Presigned URLs
- Versioning
- Lifecycle policies
- Intelligent tiering

**Trade-offs:**
- ✅ Unlimited scalability
- ✅ Highly durable (11 9's)
- ✅ Cost-effective
- ❌ Eventual consistency
- ❌ Network latency

#### 2. Content Delivery Network

**How it works:**
- Cache at edge locations
- Serve from nearest
- Origin fetch on miss

**Optimizations:**
- Image resizing
- Format conversion
- Video adaptive bitrate
- Lazy loading

**Trade-offs:**
- ✅ 10-100x faster globally
- ✅ Offloads origin
- ✅ DDoS protection
- ❌ Cost
- ❌ Invalidation complex

#### 3. Chunked Upload/Download

**How it works:**
- Split into chunks
- Parallel transfer
- Resume on failure
- Reassemble

**Benefits:**
- Faster (parallel)
- Resilient
- Progress tracking
- Mobile-friendly

**Trade-offs:**
- ✅ Much faster
- ✅ Resilient
- ❌ Implementation complexity
- ❌ Reassembly overhead

#### 4. Compression and Optimization

**Strategies:**

**Images:**
- Format selection (WebP, AVIF)
- Quality adjustment (80%)
- Dimension resizing
- Progressive JPEG

**Videos:**
- Adaptive bitrate
- Multiple quality levels
- Efficient codecs (H.265, AV1)

**Trade-offs:**
- ✅ Significantly smaller
- ✅ Faster delivery
- ❌ Processing overhead
- ❌ Quality tradeoffs

#### 5. Deduplication

**How it works:**
- Hash file content
- Check if exists
- Reference existing or store new

**Trade-offs:**
- ✅ Massive savings (30-90%)
- ✅ Faster uploads
- ❌ Computation overhead
- ❌ Reference counting complexity

### Real-World Example: YouTube

**Pipeline:**

1. **Upload:** Chunked to nearest datacenter
2. **Processing:** Transcode multiple formats
3. **Storage:** Original (Glacier), Transcoded (S3)
4. **Delivery:** CDN adaptive bitrate

### Common Deep Dives

#### Q1: 10GB video upload?

**Architecture:**

1. **Upload:** S3 Multipart (10MB chunks, parallel)
2. **Processing:** Async transcoding, multiple qualities
3. **Storage:** Original (Glacier), Transcoded (S3)
4. **Delivery:** CloudFront CDN, adaptive bitrate

**Interview Answer:**
"Use S3 Multipart Upload for resilience. Trigger async transcoding for multiple quality levels. Store original in Glacier for cost savings, transcoded in S3. Distribute via CloudFront with adaptive bitrate streaming."

#### Q2: Prevent hotlinking?

**Solutions:**
- Signed URLs (temporary)
- Referer checking
- Token-based auth
- Rate limiting

**Interview Answer:**
"Use signed URLs with short expiration (1 hour). Application generates temporary signed URL. Combined with geo-restriction and rate limiting, this prevents hotlinking while maintaining good UX."

#### Q3: Billions of small files?

**Solutions:**
- File aggregation (Haystack)
- Hierarchical storage
- Sharding
- Object storage

**Interview Answer:**
"Use S3 designed for billions of objects. For tiny files, implement file aggregation packing multiple small files into larger blobs with an index for lookup (Facebook's Haystack approach). This reduces metadata overhead and improves retrieval speed. Implement lifecycle policies to automatically move cold files to cheaper storage tiers."

---

## 7. Managing Long Running Tasks

### Overview
**Long Running Tasks** are operations taking significant time (seconds to hours) requiring special handling for reliability, monitoring, and user experience.

### The Problem

#### Scenario: Video Processing

**Requirements:**
- User uploads video
- Process: validate, transcode, thumbnail, analyze
- Takes 5-30 minutes
- HTTP timeout: 30 seconds
- Need progress updates
- Handle failures gracefully

**The Challenge:**
```
Issues with long-running sync operations:
- HTTP timeouts
- Connection drops
- Server restarts lose progress
- No status tracking
- Wasted resources on retry
- Poor user experience
```

### Solutions

#### 1. Async Task Queue (Most Common)

**How it works:**
- Accept request immediately
- Return task ID
- Queue for processing
- Workers process asynchronously
- Client polls status

**Components:**
- Task Queue (RabbitMQ, SQS, Redis)
- Worker Pool (auto-scaling)
- Status Store (Redis/database)
- Result Store (database/S3)

**Trade-offs:**
- ✅ Reliable (survives restarts)
- ✅ Scalable (add workers)
- ✅ Can retry
- ✅ Progress tracking
- ❌ Complexity
- ❌ Eventual consistency
- ❌ Polling needed

#### 2. Webhooks (Event-Driven Callbacks)

**How it works:**
- Client provides callback URL
- Service calls URL on completion
- No polling needed
- Push-based

**Trade-offs:**
- ✅ No polling
- ✅ Real-time notifications
- ✅ Scalable
- ❌ Requires public endpoint
- ❌ Retry logic needed
- ❌ Security concerns

#### 3. WebSocket/SSE for Progress

**How it works:**
- Persistent connection
- Stream progress updates
- Real-time
- Better UX

**Update Types:**
- Progress percentage
- Current step
- Estimated time remaining
- Intermediate results
- Completion notification

**Trade-offs:**
- ✅ Real-time updates
- ✅ Great UX
- ✅ No polling
- ❌ Connection overhead
- ❌ More complex
- ❌ Connection management

#### 4. Batch Processing

**How it works:**
- Accumulate tasks
- Process together
- Scheduled or size-triggered
- Amortize overhead

**Strategies:**
- Time-based (every N hours)
- Size-based (every N tasks)
- Hybrid (whichever first)
- Priority (high-priority bypass)

**Trade-offs:**
- ✅ Very efficient
- ✅ Lower resource usage
- ✅ Can optimize together
- ❌ Higher latency
- ❌ Complexity
- ❌ Partial failure handling

#### 5. State Machines (Step Functions)

**How it works:**
- Define as state machine
- Each step independent
- Automatic retry
- Track progress

**Trade-offs:**
- ✅ Visual workflow
- ✅ Built-in retry/error handling
- ✅ Durable state tracking
- ✅ Easy debugging
- ❌ Learning curve
- ❌ Can be expensive
- ❌ Vendor lock-in

### Real-World Example: Uber Data Export

**Architecture:**

1. **Request:** API creates task, returns ID, enqueues
2. **Processing:** Worker queries databases, generates files, uploads S3
3. **Notification:** Email, push, dashboard with download link
4. **Monitoring:** CloudWatch tracks duration, alerts, retries (3x), dead letter queue

### Common Deep Dives

#### Q1: Task fails after 2 hours?

**Solutions:**
- Checkpointing (save progress)
- Idempotent steps
- Sub-task division
- Partial results

**Interview Answer:**
"Implement checkpointing where task saves progress every N minutes. On failure, retry from last checkpoint. Design each step to be idempotent. For very long tasks, break into smaller sub-tasks that can be independently retried."

#### Q2: Prevent duplicate execution?

**Solutions:**
- Idempotency keys
- Database constraints
- Distributed lock
- Status checking

**Interview Answer:**
"Require idempotency keys from clients. When submitted, check if key exists. If processing or completed, return existing task ID. Use database unique constraint to prevent race conditions."

#### Q3: User cancels task?

**Architecture:**

1. **Signal:** Mark as "cancelling" in database
2. **Cleanup:** Worker checks periodically, stops gracefully
3. **Resources:** Terminate processes, delete partial results, release locks
4. **Communication:** Send confirmation, explain completion status

**Interview Answer:**
"Implement graceful cancellation. Workers periodically check task status. When cancellation detected, complete current atomic operation, save useful partial results, clean up resources, and mark as cancelled."

---

## 8. Pattern Combinations

### Overview
Real-world systems rarely use a single pattern. **Pattern Combinations** explores how multiple patterns work together to solve complex problems.

### Common Pattern Combinations

#### 1. Real-time Updates + Scaling Reads + Caching

**Use Case:** Live Sports Scores Platform

**Problem:**
- Millions watching same game
- Scores update every few seconds
- Need instant updates
- Can't overload database

**Solution Architecture:**
```
Score Update Flow:
Official Source → API → Database (write)
                         ↓
                   Redis Pub/Sub
                         ↓
          ┌──────────────┼──────────────┐
          ↓              ↓              ↓
    WS Server 1    WS Server 2    WS Server 3
          ↓              ↓              ↓
      Users          Users          Users

Read Flow:
User → CDN (static assets)
    → Redis Cache (current scores - 2s TTL)
    → Read Replica (historical data)
```

**Patterns Combined:**
1. **Real-time Updates:** WebSocket for live push
2. **Scaling Reads:** Read replicas for history
3. **Caching:** Redis for current scores

**Why This Works:**
- WebSocket handles millions of concurrent connections
- Redis cache prevents database overload
- Read replicas handle historical queries
- CDN serves static assets efficiently

#### 2. Multi-step Processes + Long Running Tasks + Contention

**Use Case:** E-commerce Flash Sale

**Problem:**
- Limited inventory (1000 items)
- 100K users trying simultaneously
- Multi-step checkout process
- Must prevent overselling

**Solution Architecture:**
```
1. Queue-Based Entry:
   Users → Virtual Queue → Rate-Limited Entry

2. Reservation System:
   Each server gets inventory pool (100 items)
   Soft reservation (5 min timeout)

3. Saga Pattern Checkout:
   Reserve Item → Process Payment → Create Order
   (With compensations on failure)

4. Async Task Queue:
   Email confirmation
   Update analytics
   Inventory reconciliation
```

**Patterns Combined:**
1. **Contention:** Queue + Reservation system
2. **Multi-step:** Saga pattern for checkout
3. **Long Running:** Async queue for emails

**Why This Works:**
- Queue prevents stampede
- Reservation eliminates lock contention
- Saga ensures consistency
- Async tasks don't block user

#### 3. Scaling Writes + Handling Large Blobs + Sharding

**Use Case:** Instagram Photo Upload

**Problem:**
- Millions of photos daily
- Each photo: metadata + image file
- Global distribution
- Fast upload needed

**Solution Architecture:**
```
Upload Flow:
User → CDN Edge → S3 Multipart Upload
                       ↓
                 [Processing Queue]
                       ↓
                 Resize, Format Convert
                       ↓
                 Store Multiple Versions

Metadata Flow:
Photo Info → Sharded Database (by user_id)
         → Cache Invalidation
         → Search Index Update
```

**Patterns Combined:**
1. **Large Blobs:** S3 + CDN + Chunked upload
2. **Scaling Writes:** Database sharding
3. **Long Running:** Async processing queue

**Why This Works:**
- S3 handles blob storage at scale
- Sharding distributes metadata writes
- Async processing doesn't block upload
- CDN serves images globally

#### 4. Scaling Reads + Real-time Updates + Caching

**Use Case:** Twitter Timeline

**Problem:**
- Read-heavy (1000:1 read:write ratio)
- Need real-time updates
- Millions of concurrent users
- Must be fast globally

**Solution Architecture:**
```
Write Path:
Tweet Creation → Database (sharded by user_id)
              → Fan-out Service (hybrid)
              → Timeline Cache Updates
              → WebSocket Notification

Read Path:
Request → CDN (static assets)
       → Redis (timeline cache)
       → Read Replica (cache miss)
       → WebSocket (new tweets)
```

**Patterns Combined:**
1. **Scaling Reads:** Replicas + caching + CDN
2. **Real-time:** WebSocket for updates
3. **Scaling Writes:** Sharding for tweets

**Why This Works:**
- Caching handles read load
- Sharding handles write load
- WebSocket for instant updates
- CDN reduces latency

### Pattern Selection Guide

**When to Combine Patterns:**

| Scenario | Primary Pattern | Secondary Patterns |
|----------|----------------|-------------------|
| High-traffic API | Scaling Reads | Caching, Replication, CDN |
| Payment Processing | Multi-step Processes | Contention, Idempotency |
| Video Platform | Large Blobs | Scaling Writes, Long Running |
| Chat Application | Real-time Updates | Scaling Reads, Sharding |
| E-commerce Checkout | Contention | Multi-step, Long Running |

### Common Deep Dives

#### Q1: How decide which patterns to combine?

**Decision Process:**

1. **Identify Core Problem:**
   - What's the main bottleneck?
   - What's the traffic pattern?
   - What are consistency requirements?

2. **Start with Primary Pattern:**
   - Choose pattern addressing core problem
   - Implement basic version

3. **Add Secondary Patterns:**
   - Identify remaining bottlenecks
   - Add patterns to address each
   - Ensure patterns work together

4. **Validate Trade-offs:**
   - Check if complexity is justified
   - Ensure maintainability
   - Verify scalability

**Interview Answer:**
"Start by identifying the core problem - is it read scaling, write scaling, consistency, or latency? Choose a primary pattern addressing that. Then identify secondary bottlenecks and add supporting patterns. Each pattern should solve a specific problem without conflicting with others."

#### Q2: Pattern conflicts?

**Common Conflicts:**

**Strong Consistency vs High Availability:**
- Multi-step processes need consistency
- Real-time updates need availability
- Solution: Use Saga pattern for eventual consistency

**Caching vs Real-time:**
- Caching introduces staleness
- Real-time needs immediate updates
- Solution: Cache invalidation with pub/sub

**Sharding vs Joins:**
- Sharding splits data
- Joins need co-located data
- Solution: Denormalize or application-level joins

**Interview Answer:**
"Recognize that some patterns have inherent conflicts. Strong consistency conflicts with high availability - accept eventual consistency when possible. Caching conflicts with real-time - use cache invalidation. Sharding conflicts with joins - denormalize or accept application-level joining."

#### Q3: How avoid over-engineering?

**Guidelines:**

1. **Start Simple:**
   - Use single pattern first
   - Add complexity only when needed
   - Measure before optimizing

2. **Use Metrics:**
   - Define success criteria
   - Monitor key metrics
   - Add patterns to fix bottlenecks

3. **Consider Maintenance:**
   - More patterns = more complexity
   - Team must understand all patterns
   - Document why each pattern used

4. **Apply YAGNI:**
   - You Aren't Gonna Need It
   - Don't add for theoretical problems
   - Wait for real need

**Interview Answer:**
"Start with the simplest solution that could work. Use monitoring to identify real bottlenecks. Add patterns incrementally to address measured problems. Avoid adding patterns for theoretical future scaling issues. Balance complexity against team's ability to maintain the system."

---

## 9. Interview Strategy Guide

### Overview
This guide provides strategies for effectively discussing system design patterns in interviews, helping you demonstrate deep understanding and practical experience.

### Interview Approach

#### 1. Understand Requirements First

**Key Questions to Ask:**
- What's the scale? (users, requests/sec, data volume)
- What are the latency requirements?
- What consistency level is needed?
- Are there geographic requirements?
- What's the read:write ratio?
- What operations are most common?

**Why This Matters:**
- Different scales need different patterns
- Clarifying prevents wrong assumptions
- Shows systematic thinking
- Demonstrates requirement gathering skills

**Interview Tip:**
"Never jump into solutions. Spend 5-10 minutes clarifying requirements. Write them down. This shows you understand that different requirements demand different solutions."

#### 2. Start Simple, Then Scale

**Progressive Approach:**

**Level 1 - Basic:**
- Single server
- Single database
- No caching

**Level 2 - Moderate Scale:**
- Load balancer
- Database replication
- Basic caching

**Level 3 - High Scale:**
- Sharding
- CDN
- Multiple patterns combined

**Why This Matters:**
- Shows understanding of evolution
- Demonstrates practical experience
- Allows discussion of trade-offs
- Interviewer can gauge your level

**Interview Tip:**
"Start with a simple architecture. Say 'This works for 10K users. As we scale to 10M users, we'll need to add...' This shows you understand different scales need different solutions."

#### 3. Discuss Trade-offs

**For Each Pattern, Cover:**

**Benefits:**
- What problem it solves
- When it's appropriate
- Why it's better than alternatives

**Costs:**
- Complexity added
- Operational overhead
- Consistency impacts
- Failure modes

**Alternatives:**
- What else could work
- Why you chose this pattern
- When alternatives are better

**Interview Tip:**
"Always discuss trade-offs. Say 'We could use X which gives us Y benefit but costs us Z.' This shows mature engineering judgment."

#### 4. Use Real-World Examples

**Effective References:**
- "Twitter uses fan-out on write for most users"
- "Instagram uses S3 for blob storage"
- "Uber uses Saga pattern for ride booking"

**Why This Works:**
- Shows you study real systems
- Provides concrete context
- Demonstrates practical knowledge
- Makes discussion more engaging

**Interview Tip:**
"Reference real systems when relevant. But be accurate - don't make up facts about how companies work. Say 'I believe Twitter does X' if unsure."

#### 5. Draw Diagrams

**What to Diagram:**
- Data flow
- Component relationships
- Network boundaries
- User interactions

**Diagram Tips:**
- Keep it simple
- Use standard symbols
- Label everything clearly
- Show directionality

**Interview Tip:**
"Always use the whiteboard/diagram. Visual communication is crucial in system design. Walk through data flow with your diagram."

### Common Interview Questions by Pattern

#### Real-time Updates

**Question:** "Design a chat application for 10M users"

**Good Response Structure:**
1. Clarify: 1-on-1 or group? Message history? Typing indicators?
2. Start simple: HTTP polling
3. Explain limitations: Too many requests
4. Propose WebSocket: Persistent connections
5. Scale: Multiple WS servers + Redis Pub/Sub
6. Discuss: Connection management, reconnection

#### Contention

**Question:** "Design a ticket booking system"

**Good Response Structure:**
1. Clarify: How many seats? Expected concurrency?
2. Identify problem: Race conditions
3. Discuss options: Optimistic vs pessimistic locking
4. Propose solution: Queue + reservation pools
5. Scale: Multiple servers with allocated pools
6. Handle edge cases: Timeouts, abandoned carts

#### Multi-step Processes

**Question:** "Design an order processing system"

**Good Response Structure:**
1. Clarify: Microservices? External payment gateway?
2. List steps: Validate, reserve, pay, confirm
3. Discuss consistency: ACID vs eventual
4. Propose Saga: Orchestration approach
5. Design compensations: Refund, release inventory
6. Handle failures: Retry, dead letter queue

#### Scaling Reads

**Question:** "Design a news feed for Reddit"

**Good Response Structure:**
1. Clarify: Scale? Freshness requirements?
2. Identify bottleneck: Read-heavy workload
3. Propose caching: Redis for hot posts
4. Add replication: Read replicas
5. Add CDN: For images/static assets
6. Discuss invalidation: TTL vs event-based

#### Scaling Writes

**Question:** "Design Twitter's tweet system"

**Good Response Structure:**
1. Clarify: Write volume? Geographic distribution?
2. Identify bottleneck: High write volume
3. Propose sharding: By user_id
4. Discuss fan-out: Hybrid push/pull
5. Handle hot shards: Secondary sharding for celebrities
6. Ensure durability: WAL, replication

### Red Flags to Avoid

**❌ Don't:**
- Jump straight to complex solutions
- Ignore trade-offs
- Make up facts about real systems
- Use buzzwords without understanding
- Design for unlimited scale immediately
- Forget about failure modes
- Ignore operational concerns

**✅ Do:**
- Ask clarifying questions
- Start simple and iterate
- Discuss trade-offs explicitly
- Use real-world examples accurately
- Consider failure scenarios
- Think about monitoring
- Consider team maintainability

### Practice Strategy

**How to Prepare:**

1. **Study Real Systems:**
   - Read engineering blogs
   - Study open-source systems
   - Follow technology conferences
   - Understand production systems

2. **Practice Drawing:**
   - Practice system diagrams
   - Use standard notation
   - Time yourself
   - Get feedback

3. **Learn Patterns Deeply:**
   - Understand when to use each
   - Know trade-offs
   - Study real implementations
   - Practice combinations

4. **Mock Interviews:**
   - Practice with peers
   - Get feedback
   - Time yourself
   - Record and review

**Resources:**
- Engineering blogs (Netflix, Uber, Twitter)
- System Design primer repositories
- "Designing Data-Intensive Applications" book
- Architecture talks on YouTube

### Final Tips

**During the Interview:**
- Think out loud
- Ask questions
- Draw diagrams
- Discuss trade-offs
- Use real examples
- Consider failures
- Think about operations

**Communication:**
- Be clear and concise
- Use proper terminology
- Admit when unsure
- Build on feedback
- Stay organized
- Manage time well

**Mindset:**
- Show growth mindset
- Demonstrate learning ability
- Be collaborative
- Consider team impact
- Think long-term

**Remember:**
The interview is a conversation, not an exam. The interviewer wants to see how you think, how you handle ambiguity, and how you would work as a teammate. Demonstrate clear thinking, good judgment, and strong communication skills.

---

## Conclusion

System design patterns are tools in your engineering toolkit. Knowing when and how to apply them separates good engineers from great ones. This guide has covered:

1. **Core Patterns:** Real-time updates, contention, multi-step processes, scaling reads/writes, large blobs, long-running tasks
2. **Pattern Combinations:** How patterns work together in real systems
3. **Interview Strategy:** How to discuss patterns effectively

**Key Takeaways:**
- No single pattern solves everything
- Always consider trade-offs
- Start simple, scale as needed
- Learn from real-world systems
- Practice, practice, practice

**Next Steps:**
- Deep dive into patterns relevant to your domain
- Study real system architectures
- Practice with mock interviews
- Build side projects applying these patterns
- Stay current with industry practices

Good luck with your system design interviews!
