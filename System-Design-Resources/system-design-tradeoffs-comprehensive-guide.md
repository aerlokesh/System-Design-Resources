# Comprehensive System Design Trade-offs Guide

## Table of Contents
1. [Introduction](#introduction)
2. [CAP Theorem Trade-offs](#cap-theorem-trade-offs)
3. [Consistency vs Availability](#consistency-vs-availability)
4. [Latency vs Throughput](#latency-vs-throughput)
5. [Read vs Write Optimization](#read-vs-write-optimization)
6. [Normalization vs Denormalization](#normalization-vs-denormalization)
7. [SQL vs NoSQL](#sql-vs-nosql)
8. [Vertical vs Horizontal Scaling](#vertical-vs-horizontal-scaling)
9. [Synchronous vs Asynchronous Processing](#synchronous-vs-asynchronous-processing)
10. [Monolithic vs Microservices](#monolithic-vs-microservices)
11. [Caching Trade-offs](#caching-trade-offs)
12. [Push vs Pull Models](#push-vs-pull-models)
13. [Strong vs Eventual Consistency](#strong-vs-eventual-consistency)
14. [Batch vs Stream Processing](#batch-vs-stream-processing)
15. [Client-side vs Server-side Rendering](#client-side-vs-server-side-rendering)
16. [Stateful vs Stateless Services](#stateful-vs-stateless-services)
17. [Replication Trade-offs](#replication-trade-offs)
18. [Partitioning/Sharding Trade-offs](#partitioning-sharding-trade-offs)
19. [Security vs Performance](#security-vs-performance)
20. [Cost vs Performance](#cost-vs-performance)

---

## Introduction

System design is fundamentally about making informed trade-offs. No system is perfect for all scenarios, and every architectural decision involves compromises. This guide covers all major trade-offs you'll encounter in system design interviews with real-world examples.

### Key Principle
**"There is no one-size-fits-all solution in system design. Every decision is a trade-off."**

---

## 1. CAP Theorem Trade-offs

### Overview
CAP Theorem states that a distributed system can provide only two of three guarantees:
- **Consistency (C)**: All nodes see the same data at the same time
- **Availability (A)**: Every request receives a response (success or failure)
- **Partition Tolerance (P)**: System continues operating despite network partitions

### Trade-off Matrix

| Choice | What You Get | What You Sacrifice | Use Case |
|--------|--------------|-------------------|----------|
| **CP** | Consistency + Partition Tolerance | Availability | Banking systems, inventory management |
| **AP** | Availability + Partition Tolerance | Consistency | Social media feeds, analytics dashboards |
| **CA** | Consistency + Availability | Partition tolerance (impractical in distributed systems) | Single-node databases |

### Real Interview Examples

#### Example 1: Instagram Feed (AP System)
**Question**: Design Instagram's news feed

**Trade-off Decision**:
- Choose **AP** (Availability + Partition Tolerance)
- **Why**: Users should always see *something* in their feed, even if it's slightly stale
- **Sacrifice**: Immediate consistency - a new post might take seconds to appear globally
- **Implementation**: 
  - Use eventual consistency
  - Accept stale reads
  - Prioritize user experience

```
Timeline:
0s: User A posts photo
2s: User B (same region) sees it
5s: User C (different region) sees it
10s: All users globally see it (eventual consistency achieved)
```

#### Example 2: Payment Processing (CP System)
**Question**: Design a payment processing system (e.g., Stripe, PayPal)

**Trade-off Decision**:
- Choose **CP** (Consistency + Partition Tolerance)
- **Why**: Cannot have double-spending or inconsistent balances
- **Sacrifice**: Availability - system may reject requests during network partitions
- **Implementation**:
  - Use distributed transactions
  - Two-phase commit
  - Strong consistency guarantees

```
Scenario: Network partition occurs
CP System Response:
- Block transactions until consistency can be guaranteed
- Return error: "Service temporarily unavailable"
- Preserve data integrity at all costs

AP System Response (WRONG for payments):
- Continue processing
- Risk: Double-spending or inconsistent balances
- Data corruption possible
```

---

## 2. Consistency vs Availability

### The Fundamental Trade-off

**Strong Consistency**: All clients see the same data at the same time
- Higher latency
- Lower availability
- Better for financial systems

**Eventual Consistency**: Data will become consistent given enough time
- Lower latency
- Higher availability
- Better for social media, caching

### Decision Framework

```
┌─────────────────────────────────────────────────┐
│  Does incorrect data cause financial loss or    │
│  safety issues?                                  │
│                                                  │
│  YES → Strong Consistency                       │
│  NO  → Eventual Consistency (probably okay)     │
└─────────────────────────────────────────────────┘
```

### Real Interview Examples

#### Example 3: Facebook Live Comments (Eventual Consistency)
**Question**: Design Facebook Live commenting system

**Trade-off Analysis**:

| Aspect | Strong Consistency | Eventual Consistency (CHOSEN) |
|--------|-------------------|-------------------------------|
| User Experience | Comments appear in exact order | Comments may appear out of order briefly |
| Latency | High (200-500ms) | Low (50-100ms) |
| Scalability | Limited | Excellent |
| Cost | High | Low |
| Acceptable? | Users expect real-time | Users tolerate slight delays |

**Decision**: Eventual Consistency
**Reasoning**: Missing/delayed comment for 1-2 seconds is acceptable vs system downtime

#### Example 4: Stock Trading Platform (Strong Consistency)
**Question**: Design a stock trading system

**Trade-off Analysis**:

```
Strong Consistency REQUIRED:

Order Book State:
- BUY:  $100.00 × 1000 shares
- SELL: $100.50 × 500 shares

If eventual consistency:
- User A sees price $100.50
- User B sees price $100.00 (stale)
- Both place orders
- Result: Incorrect execution, regulatory violations

Strong Consistency Guarantees:
- All users see identical order book
- Trades execute at correct prices
- Regulatory compliance maintained
- Worth the latency cost (50-100ms additional)
```

---

## 3. Latency vs Throughput

### Definitions

**Latency**: Time to complete a single request (milliseconds)
**Throughput**: Number of requests processed per unit time (requests/second)

### The Trade-off

Optimizing for one often hurts the other:
- **Low Latency**: Might reduce throughput (dedicated resources per request)
- **High Throughput**: Might increase latency (batching, queuing)

### Decision Matrix

| System Type | Priority | Example | Optimization Strategy |
|-------------|----------|---------|----------------------|
| Gaming servers | **Latency** | Counter-Strike, Fortnite | Dedicated connections, no batching |
| Analytics pipelines | **Throughput** | Data warehouses | Heavy batching, bulk processing |
| E-commerce checkout | **Latency** | Amazon checkout | Fast response critical |
| Log aggregation | **Throughput** | ELK stack | Batch processing acceptable |
| Video streaming | **Both** | Netflix | CDN + adaptive bitrate |

### Real Interview Examples

#### Example 5: YouTube Video Upload (Throughput Priority)
**Question**: Design YouTube's video upload system

**Trade-off Decision**:

```
Latency Impact: Acceptable
- User uploads video → Takes 5-30 minutes to process
- User expectation: "Processing will take time"
- Not real-time critical

Throughput Impact: Critical
- Millions of uploads/day
- Each video: Multiple resolutions, formats
- Massive compute requirements

Optimization Strategy (Throughput-focused):
1. Batch processing (process 100s of videos together)
2. Async queues (user doesn't wait)
3. Resource pooling (share compute across uploads)
4. Off-peak processing (lower costs)

Result:
- Latency: 10-30 minutes ✓ (acceptable)
- Throughput: 500+ hours uploaded/minute ✓
- Cost: Significantly reduced ✓
```

#### Example 6: Uber Ride Matching (Latency Priority)
**Question**: Design Uber's ride matching system

**Trade-off Decision**:

```
Latency Impact: Critical
- User books ride → Expects match in 5-30 seconds
- Driver sees request → Must respond in 15 seconds
- Real-time expectations

Throughput Impact: Lower priority
- Geographical distribution spreads load
- Not all users booking simultaneously

Optimization Strategy (Latency-focused):
1. No batching (immediate matching)
2. Geo-partitioning (reduce search space)
3. In-memory processing (Redis/cache)
4. Dedicated compute per region

Result:
- Latency: 5-10 seconds ✓ (excellent)
- Throughput: Sufficient for distributed load ✓
- Cost: Higher, but worth it for UX ✓
```

---

## 4. Read vs Write Optimization

### The Fundamental Trade-off

Systems are typically optimized for either reads OR writes, rarely both equally.

### Optimization Strategies

| Optimization | Techniques | Cost | Use Case |
|--------------|-----------|------|----------|
| **Read-Heavy** | Caching, read replicas, denormalization, CDN | Storage cost | Social media, news sites |
| **Write-Heavy** | Write buffers, async processing, eventual consistency | Complexity | IoT sensors, logging |
| **Balanced** | Hybrid approaches, partitioning | Higher cost | E-commerce platforms |

### Real Interview Examples

#### Example 7: Twitter Timeline (Read-Optimized)
**Question**: Design Twitter's timeline system

**Read/Write Analysis**:
```
Read Pattern:
- 1 billion timeline views/day
- Each view reads ~100 tweets
- Total: 100+ billion reads/day

Write Pattern:
- 500 million tweets/day
- Much lower than reads

Ratio: 200:1 (Read:Write)
```

**Trade-off Decision**:

```
Read Optimization Strategy (CHOSEN):

1. Pre-compute Timelines (Write-time fan-out)
   - When user tweets → immediately write to followers' timelines
   - Write amplification: 1 tweet × 1000 followers = 1000 writes
   - Cost: High write cost

2. Benefits:
   - Timeline reads are instant (just fetch pre-computed data)
   - No computation at read time
   - Scales to billions of reads

3. Celebrity Problem:
   - Celebrity with 100M followers → 100M writes per tweet (too expensive)
   - Solution: Hybrid approach
     - Normal users: Pre-compute (write-time fan-out)
     - Celebrities: Compute on read (read-time fan-in)

Implementation:
┌─────────────────────────────────────────┐
│ User A (1000 followers) tweets          │
│ ↓                                        │
│ Write to 1000 followers' timelines      │
│ Read: SELECT * FROM timeline WHERE      │
│       user_id = X (pre-computed)        │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ Celebrity (100M followers) tweets       │
│ ↓                                        │
│ Write only to database                  │
│ Read: Compute timeline on-demand from   │
│       followed celebrities + normal     │
│       users                              │
└─────────────────────────────────────────┘
```

#### Example 8: Logging System (Write-Optimized)
**Question**: Design a distributed logging system (like ELK)

**Read/Write Analysis**:
```
Write Pattern:
- 1 million log entries/second
- Continuous high-volume writes
- Time-series data

Read Pattern:
- Occasional debugging/analysis
- Usually querying recent logs
- Can tolerate some latency

Ratio: 1000:1 (Write:Read)
```

**Trade-off Decision**:

```
Write Optimization Strategy (CHOSEN):

1. Append-only writes
   - No updates, only inserts
   - Sequential disk writes (fast)
   - Minimal locking

2. Batching & Buffering
   - Buffer 1000s of logs in memory
   - Bulk write to disk
   - Trade-off: Slight data loss risk during crashes

3. Delayed Indexing
   - Write first, index later
   - Async indexing process
   - Trade-off: Recent logs may not be searchable immediately

4. Time-based partitioning
   - Partition by day/hour
   - Old partitions read-only (optimized storage)

Benefits:
- Handles millions of writes/second
- Low write latency
- Cost-effective storage

Sacrifices:
- Read queries may be slower
- Recent data search delay (30-60 seconds)
- Complex query optimization needed
```

---

## 5. Normalization vs Denormalization

### Definitions

**Normalization**: Organizing data to reduce redundancy
**Denormalization**: Adding redundancy to optimize reads

### Trade-off Comparison

| Aspect | Normalized | Denormalized |
|--------|-----------|--------------|
| Storage | Less space | More space |
| Write Performance | Faster (single location) | Slower (multiple locations) |
| Read Performance | Slower (joins needed) | Faster (pre-joined) |
| Data Consistency | Easier | Harder |
| Use Case | OLTP, Write-heavy | OLAP, Read-heavy |

### Real Interview Examples

#### Example 9: E-commerce Product Catalog (Denormalized)
**Question**: Design Amazon's product catalog system

**Trade-off Analysis**:

```
Normalized Approach (NOT chosen):
┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│   Products   │────▶│  Categories │────▶│   Reviews    │
│              │     │             │     │              │
│ product_id   │     │ category_id │     │ review_id    │
│ name         │     │ name        │     │ product_id   │
│ price        │     │             │     │ rating       │
└──────────────┘     └─────────────┘     └──────────────┘

To display product page:
- Query Products table
- Query Categories table (JOIN)
- Query Reviews table (JOIN)
- Aggregate reviews (computation)
Result: 3+ database queries, slow

Denormalized Approach (CHOSEN):
┌────────────────────────────────────────┐
│         Product Document               │
│                                        │
│ product_id: "B00X123"                 │
│ name: "iPhone 15"                     │
│ price: $999                           │
│ category: "Electronics > Phones"      │
│ avg_rating: 4.5                       │
│ review_count: 1234                    │
│ top_reviews: [...]                    │
│ related_products: [...]               │
│ inventory: {...}                      │
└────────────────────────────────────────┘

To display product page:
- Single query to get complete document
Result: 1 database query, very fast

Trade-offs Made:
Sacrifices:
- Storage: 3x more space (duplicate category names, reviews)
- Writes: When updating review, must update product doc
- Consistency: Small delay in review count updates

Benefits:
- Read speed: 10x faster (critical for user experience)
- Scalability: Can cache entire document
- User experience: Page loads in <100ms

Decision: Worth the trade-off because:
- Reads >> Writes (100:1 ratio)
- User experience is paramount
- Storage is cheap, user patience is not
```

#### Example 10: Banking System (Normalized)
**Question**: Design a banking transaction system

**Trade-off Analysis**:

```
Denormalized Approach (WRONG):
┌─────────────────────────────────────┐
│      Account Document               │
│                                     │
│ account_id: "12345"                │
│ balance: $10,000                   │
│ transactions: [                     │
│   {id: 1, amount: -$50},          │
│   {id: 2, amount: +$100},         │
│   ...                              │
│ ]                                   │
└─────────────────────────────────────┘

Problems:
- Document grows unbounded
- Concurrent update conflicts
- Difficult to ensure transaction integrity
- Regulatory compliance issues

Normalized Approach (CHOSEN):
┌─────────────┐     ┌──────────────────┐
│  Accounts   │     │   Transactions   │
│             │     │                  │
│ account_id  │◀────│ transaction_id   │
│ balance     │     │ from_account     │
│ created_at  │     │ to_account       │
│             │     │ amount           │
│             │     │ timestamp        │
└─────────────┘     └──────────────────┘

Benefits:
- ACID compliance guaranteed
- Clear audit trail
- Easy to query transaction history
- No document size limits
- Data integrity ensured

Trade-offs Made:
Sacrifices:
- Read performance: Need JOIN for full history
- More complex queries

Benefits:
- Data integrity: CRITICAL for finance
- Audit compliance: Required by law
- Transaction safety: No double-spending
- Scalability: Can partition by time

Decision: Must choose normalized because:
- Financial accuracy is non-negotiable
- Regulatory requirements mandate it
- Read speed < Data correctness
- Can optimize reads with caching
```

---

## 6. SQL vs NoSQL

### Decision Framework

```
┌──────────────────────────────────────────────────┐
│         SQL vs NoSQL Decision Tree              │
└──────────────────────────────────────────────────┘

Do you need ACID transactions?
│
├─ YES → SQL (PostgreSQL, MySQL)
│
└─ NO → Continue...
    │
    Is your data schema flexible/changing?
    │
    ├─ YES → NoSQL (MongoDB, Cassandra)
    │
    └─ NO → Continue...
        │
        Do you need complex joins/queries?
        │
        ├─ YES → SQL
        │
        └─ NO → NoSQL may be better
```

### Trade-off Matrix

| Aspect | SQL | NoSQL |
|--------|-----|-------|
| **Schema** | Fixed, defined upfront | Flexible, schema-less |
| **Consistency** | Strong (ACID) | Eventual (BASE) |
| **Scaling** | Vertical (harder) | Horizontal (easier) |
| **Joins** | Excellent | Poor/None |
| **Query Language** | SQL (standardized) | Varies (learning curve) |
| **Use Case** | Financial, inventory | Social media, IoT |

### Real Interview Examples

#### Example 11: Uber Trip Records (SQL)
**Question**: Design Uber's trip recording system

**Database Choice Analysis**:

```
Requirements:
✓ Transactions: Driver payment, rider charge must be atomic
✓ Complex queries: "Find all trips in SF on Friday between 5-7pm"
✓ Referential integrity: Trip → Driver → Rider relationships
✓ Financial data: Must be accurate, no data loss
✓ Audit trail: Required for disputes

SQL (CHOSEN):
┌─────────────────────────────────────────────┐
│ Schema Design                               │
│                                             │
│ trips                                       │
│ ├─ trip_id (PK)                            │
│ ├─ driver_id (FK → drivers.driver_id)     │
│ ├─ rider_id (FK → riders.rider_id)        │
│ ├─ start_time                              │
│ ├─ end_time                                │
│ ├─ fare_amount                             │
│ └─ status                                  │
│                                             │
│ Complex Query Example:                      │
│ SELECT d.name, COUNT(*) as trip_count,     │
│        AVG(t.fare_amount) as avg_fare      │
│ FROM trips t                                │
│ JOIN drivers d ON t.driver_id = d.id       │
│ WHERE t.start_time BETWEEN '...' AND '...' │
│ GROUP BY d.name                             │
│ HAVING COUNT(*) > 10                        │
└─────────────────────────────────────────────┘

Why NOT NoSQL:
- Cannot guarantee atomic payment transactions
- Complex analytics queries would be inefficient
- Relationship management would be manual
- Financial accuracy requires ACID

Trade-offs Made:
Sacrifices:
- Scaling complexity (vertical scaling first)
- Schema changes require migrations

Benefits:
- Data integrity guaranteed
- Complex queries supported
- Transaction safety
- Proven for financial systems
```

#### Example 12: Instagram Photos (NoSQL)
**Question**: Design Instagram's photo storage system

**Database Choice Analysis**:

```
Requirements:
✓ Massive scale: Billions of photos
✓ High write throughput: Millions of uploads/day
✓ Simple queries: Fetch photos by user_id
✓ Flexible schema: Photo metadata may vary
✓ Geographic distribution: Global users
✗ No complex joins needed
✗ No strict transactions needed

NoSQL (CHOSEN) - Cassandra/DynamoDB:
┌─────────────────────────────────────────────┐
│ Data Model                                  │
│                                             │
│ photo_metadata {                            │
│   photo_id: "abc123",                       │
│   user_id: "user456",                       │
│   url: "cdn.instagram.com/...",            │
│   filters: ["Valencia", "Contrast"],        │
│   location: {...},                          │
│   likes_count: 1234,                        │
│   created_at: "...",                        │
│   custom_fields: {...}  // Flexible!        │
│ }                                            │
│                                             │
│ Simple Query Pattern:                        │
│ Get all photos for user:                    │
│ SELECT * FROM photos WHERE user_id = X      │
│ (No joins needed!)                          │
└─────────────────────────────────────────────┘

Why NOT SQL:
- Sharding billions of photos complex in SQL
- Schema changes needed frequently (new features)
- No need for complex joins
- Horizontal scaling easier

Trade-offs Made:
Sacrifices:
- Cannot do complex analytics easily
- No ACID transactions (but don't need them)
- Must denormalize data

Benefits:
- Scales to billions of records easily
- High write throughput
- Flexible schema for rapid iteration
- Geographic distribution (multi-region)
- Lower latency (partition by user_id)

Decision: NoSQL wins because:
- Scale requirements massive
- Simple access patterns
- Speed > Complex queries
- Schema flexibility valuable
```

---

## 7. Vertical vs Horizontal Scaling

### Definitions

**Vertical Scaling (Scale Up)**: Add more power to existing machine
**Horizontal Scaling (Scale Out)**: Add more machines

### Trade-off Comparison

| Aspect | Vertical | Horizontal |
|--------|----------|------------|
| **Cost (initial)** | Lower | Higher |
| **Cost (long-term)** | Higher (limits) | Lower |
| **Complexity** | Simple | Complex |
| **Single Point of Failure** | Yes | No |
| **Scalability Limit** | Hardware limits | Nearly unlimited |
| **Downtime for scaling** | Yes | No |

### Real Interview Examples

#### Example 13: Startup Phase (Vertical Scaling)
**Question**: Design a system for a startup with 10K users

**Scaling Decision**:

```
Context:
- Current: 10K users
- Growth: Unknown
- Team: 3 engineers
- Budget: Limited

Vertical Scaling (CHOSEN):
┌─────────────────────────────────────┐
│   Single Server Architecture        │
│                                     │
│   ┌─────────────────────┐          │
│   │  t3.xlarge          │          │
│   │  4 vCPU, 16GB RAM   │          │
│   │                     │          │
│   │  App + Database     │          │
│   │  on same machine    │          │
│   └─────────────────────┘          │
│                                     │
│   If needed → Upgrade to:          │
│   t3.2xlarge (8 vCPU, 32GB)       │
└─────────────────────────────────────┘

Benefits:
- Simple: One server to manage
- Cost: $200/month vs $1000+ for distributed
- Fast: No network latency
- Easy debugging: All logs in one place

Trade-offs:
- Single point of failure
- Downtime during upgrades
- Limited by hardware (max ~96 vCPU, 384GB)

When to Switch to Horizontal:
- Users > 100K
- Revenue > $100K/month
- Cannot afford downtime
- Team grows to 5+ engineers
```

#### Example 14: Netflix Scale (Horizontal Scaling)
**Question**: Design Netflix's video streaming infrastructure

**Scaling Decision**:

```
Context:
- Current: 200M+ users
- Traffic: Massive, global
- Availability: 99.99% required
- Budget: Large but must be efficient

Horizontal Scaling (REQUIRED):
┌────────────────────────────────────────────────┐
│   Distributed Architecture                     │
│                                                │
│   Load Balancer                                │
│   ↓    ↓    ↓    ↓    ↓                       │
│   [Web][Web][Web][Web][Web]  ← 1000s of       │
│     ↓    ↓    ↓    ↓    ↓        servers      │
│   [DB1][DB2][DB3][DB4][DB5]                   │
│                                                │
│   ┌───────────────────────────────┐           │
│   │  Benefits of Horizontal:      │           │
│   │  • No single point of failure │           │
│   │  • Rolling deployments        │           │
│   │  • Auto-scaling               │           │
│   │  • Geographic distribution    │           │
│   └───────────────────────────────┘           │
└────────────────────────────────────────────────┘

Example Scaling Event:
Time: Friday 8pm (peak usage)
- Auto-scaling triggers
- Adds 500 servers in 5 minutes
- Users: Unaffected, seamless
- Cost: $50K/hour peak, $5K/hour off-peak

With Vertical Scaling (IMPOSSIBLE):
- Would need world's largest server
- No such hardware exists
- Single point of failure unacceptable
- Cannot serve global users efficiently

Trade-offs Made:
Sacrifices:
- Complexity: Distributed systems are hard
- Cost: 1000s of servers, load balancers, etc.
- Debugging: Distributed tracing needed

Benefits:
- Unlimited scaling capability
- High availability (99.99%+)
- Geographic distribution (low latency)
- Fault tolerance
- Elastic scaling (cost optimization)

Decision: Horizontal is ONLY option at this scale
```

---

## 8. Synchronous vs Asynchronous Processing

### Definitions

**Synchronous**: Client waits for operation to complete
**Asynchronous**: Client continues without waiting, gets result later

### Trade-off Matrix

| Aspect | Synchronous | Asynchronous |
|--------|-------------|--------------|
| **User Experience** | Immediate feedback | Requires status updates |
| **Complexity** | Simple | Complex (queues, workers) |
| **Resource Usage** | Blocking | Non-blocking |
| **Error Handling** | Direct | Requires retry logic |
| **Latency Perception** | Real-time | Delayed |

### Real Interview Examples

#### Example 15: Order Processing - Hybrid Approach
**Question**: Design Amazon's order processing system

**Processing Decision**:

```
Order Flow Analysis:

Step 1: Order Submission (SYNCHRONOUS)
┌────────────────────────────────────────┐
│ User clicks "Place Order"              │
│ ↓                                      │
│ Validate credit card                   │
│ ↓                                      │
│ Reserve inventory                      │
│ ↓                                      │
│ Return: "Order Confirmed! #12345"     │
│                                        │
│ Time: 200-500ms                        │
│ User waits: ACCEPTABLE                 │
└────────────────────────────────────────┘

Why Synchronous:
- User MUST know if order succeeded
- Payment validation needed immediately
- Inventory reservation is critical
- User expects immediate confirmation

Step 2: Order Processing (ASYNCHRONOUS)
┌────────────────────────────────────────┐
│ Background Processing Queue            │
│                                        │
│ Task 1: Send confirmation email        │
│ Task 2: Update recommendation engine   │
│ Task 3: Notify warehouse               │
│ Task 4: Update analytics               │
│ Task 5: Trigger shipping workflow      │
│                                        │
│ Time: 5-30 minutes                     │
│ User doesn't wait: GOOD                │
└────────────────────────────────────────┘

Why Asynchronous:
- These tasks are not time-critical
- User doesn't need to wait
- Can retry if failures occur
- Better resource utilization

Trade-off Decision:

SYNCHRONOUS for:
✓ Payment validation (immediate result needed)
✓ Inventory check (prevent overselling)
✓ Order confirmation (user expectation)

ASYNCHRONOUS for:
✓ Emails (user doesn't wait for email)
✓ Analytics (can process later)
✓ Recommendations update (eventual consistency OK)
✓ Third-party notifications (may be slow/unreliable)

Result:
- User experience: Excellent (fast checkout)
- System reliability: High (retry failed tasks)
- Resource efficiency: Optimal (parallel processing)
- Cost: Lower (spread out processing)
```

#### Example 16: Video Upload (Asynchronous)
**Question**: Design TikTok's video upload system

**Processing Decision**:

```
Video Upload Flow (ASYNCHRONOUS):

┌────────────────────────────────────────────────┐
│ Synchronous Part (User Waits)                 │
│                                                │
│ 1. User selects video                         │
│ 2. Upload to S3 (raw video)                   │
│ 3. Create database record                      │
│ 4. Return: "Upload successful! Processing..." │
│                                                │
│ Time: 5-30 seconds                             │
│ User Experience: Acceptable (progress bar)     │
└────────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────────┐
│ Asynchronous Part (Background)                 │
│                                                │
│ Queue: Video Processing Jobs                   │
│                                                │
│ Job 1: Transcode to 1080p    → 5 minutes      │
│ Job 2: Transcode to 720p     → 3 minutes      │
│ Job 3: Transcode to 480p     → 2 minutes      │
│ Job 4: Generate thumbnail     → 1 minute       │
│ Job 5: Content moderation     → 2 minutes      │
│ Job 6: Extract metadata       → 30 seconds     │
│                                                │
│ Total Time: 10-15 minutes                      │
└────────────────────────────────────────────────┘

Why NOT Synchronous:
- Processing 10-15 minutes unacceptable for UX
- User would wait and likely close app
- Ties up server resources during wait
- Cannot handle concurrent uploads efficiently

Benefits of Asynchronous:
1. User Experience:
   - Upload "succeeds" immediately
   - User continues using app
   - Notification when ready

2. Scalability:
   - Can process 1000s of videos concurrently
   - Queue handles spikes gracefully
   - Workers can scale independently

3. Cost Efficiency:
   - Batch similar resolutions together
   - Use spot instances for processing
   - Off-peak processing discounts

Trade-offs:
- User must wait to share video
- Requires notification system
- More complex architecture
- Need monitoring for failed jobs
```

---

## 9. Monolithic vs Microservices

### Definitions

**Monolithic**: Single codebase, deployed as one unit
**Microservices**: Multiple independent services, each with own codebase

### Trade-off Matrix

| Aspect | Monolithic | Microservices |
|--------|-----------|---------------|
| **Complexity** | Simple | Complex |
| **Deployment** | All-or-nothing | Independent services |
| **Scaling** | Scale entire app | Scale services independently |
| **Development Speed** | Fast initially | Slow initially, fast later |
| **Team Structure** | Single team | Multiple autonomous teams |
| **Failure Impact** | Entire app down | Isolated failures |

### Real Interview Examples

#### Example 17: Early Startup (Monolith)
**Question**: Design a system for a new food delivery app

**Architecture Decision**:

```
Context:
- Team: 5 engineers
- Timeline: 6 months to launch
- Uncertainty: Business model may change
- Scale: Unknown

Monolithic Architecture (CHOSEN):
┌────────────────────────────────────────┐
│         Single Application             │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │  User Service                    │ │
│  │  Restaurant Service              │ │
│  │  Order Service                   │ │
│  │  Payment Service                 │ │
│  │  Delivery Service                │ │
│  │                                  │ │
│  │  Shared: Database, Auth, Utils   │ │
│  └──────────────────────────────────┘ │
│                                        │
│  Deploy: Single unit                   │
│  Database: Single PostgreSQL           │
└────────────────────────────────────────┘

Benefits:
1. Development Speed:
   - Fast iterations
   - Shared code reuse
   - Easy debugging (single codebase)
   - Simple testing

2. Operational Simplicity:
   - One deployment
   - One server to monitor
   - Single database
   - Easier troubleshooting

3. Cost:
   - Lower infrastructure costs
   - Fewer resources needed
   - Single server initially

Trade-offs:
- Limited scaling (must scale entire app)
- Deployment risk (one bug breaks everything)
- Slower as codebase grows
- Team coordination needed

When to Switch to Microservices:
- Team > 15-20 engineers
- Clear service boundaries
- Different scaling needs
- Revenue supports complexity
```

#### Example 18: Netflix Scale (Microservices)
**Question**: Design Netflix's backend architecture

**Architecture Decision**:

```
Context:
- Team: 1000+ engineers
- Scale: 200M+ users
- Services: 700+ microservices
- Requirements: High availability

Microservices Architecture (REQUIRED):
┌────────────────────────────────────────────────┐
│   Independent Services                         │
│                                                │
│   [User Service]    [Streaming Service]       │
│   [Auth Service]    [Recommendation Service]  │
│   [Payment Service] [Analytics Service]       │
│   [CDN Service]     [Encoding Service]        │
│   ... 700+ more services                      │
│                                                │
│   Each service:                                │
│   • Own database                               │
│   • Independent deployment                     │
│   • Separate team                              │
│   • Can use different tech stack              │
└────────────────────────────────────────────────┘

Benefits:
1. Independent Scaling:
   - Streaming service: 1000s of instances
   - Payment service: 10s of instances
   - Each scales based on need

2. Team Autonomy:
   - 100+ teams work independently
   - Fast parallel development
   - No coordination bottlenecks

3. Fault Isolation:
   - Recommendation down? Streaming still works
   - Payment issues? Don't affect playback
   - Graceful degradation

4. Technology Flexibility:
   - Node.js for real-time services
   - Java for transaction services
   - Python for ML services
   - Choose best tool per service

Trade-offs:
- High complexity (service mesh, API gateway)
- Distributed debugging difficult
- Network latency between services
- Data consistency challenges
- Higher operational costs

Decision: Microservices REQUIRED because:
- Scale demands independent scaling
- Team size requires autonomy
- Availability needs fault isolation
- Worth the complexity cost
```

---

## 10. Caching Trade-offs

### Cache Strategies Comparison

| Strategy | Read Performance | Write Performance | Consistency | Complexity |
|----------|------------------|-------------------|-------------|------------|
| **Cache-Aside** | Good | Excellent | Eventual | Low |
| **Read-Through** | Excellent | Good | Eventual | Medium |
| **Write-Through** | Good | Poor | Strong | Medium |
| **Write-Behind** | Good | Excellent | Eventual | High |

### Real Interview Examples

#### Example 19: Reddit - Cache-Aside
**Question**: Design Reddit's post caching system

**Caching Decision**:

```
Cache-Aside Strategy (CHOSEN):

Read Flow:
1. Request post data
2. Check cache (Redis)
   └─ HIT → Return cached data
   └─ MISS → Query database
             → Store in cache
             → Return data

Write Flow:
1. Update post in database
2. Invalidate cache entry
3. Next read will repopulate cache

┌─────────────────────────────────────────────┐
│  Read Operation (Cache Hit)                 │
│  ↓                                          │
│  Redis GET post:12345                       │
│  ↓                                          │
│  Return in 1ms ✓                           │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Read Operation (Cache Miss)                │
│  ↓                                          │
│  Redis GET post:12345 → NULL               │
│  ↓                                          │
│  PostgreSQL query (50ms)                    │
│  ↓                                          │
│  Redis SET post:12345 value EX 3600        │
│  ↓                                          │
│  Return in 50ms (first time)               │
└─────────────────────────────────────────────┘

Benefits:
- Simple to implement
- Application controls caching logic
- Resilient (cache failure = slower, not broken)
- Works with existing code

Trade-offs:
- Cache miss penalty (database query)
- Potential stale data
- Application must manage cache

Cache Invalidation Strategy:
- TTL: 1 hour (balance freshness vs load)
- On update: Invalidate immediately
- Popular posts: Pre-warm cache
```

#### Example 20: Write-Through for Banking
**Question**: Design a banking system's account balance cache

**Caching Decision**:

```
Write-Through Strategy (CHOSEN):

Write Flow:
1. Update database (synchronous)
2. Update cache (synchronous)
3. Return success only after both complete

Read Flow:
1. Always read from cache (guaranteed fresh)
2. Cache miss → Database → Update cache

┌─────────────────────────────────────────────┐
│  Account Balance Update                     │
│                                             │
│  1. BEGIN TRANSACTION                       │
│  2. UPDATE accounts SET balance = X         │
│  3. COMMIT                                  │
│  4. Redis SET account:12345:balance X       │
│  5. Return success ✓                        │
│                                             │
│  Time: 50-100ms                             │
│  Guarantee: Cache always matches DB         │
└─────────────────────────────────────────────┘

Why Write-Through:
- Financial data must be consistent
- Read performance critical (account checks)
- Can tolerate slower writes
- Cache always reflects true balance

Trade-offs:
- Slower writes (must update both)
- Increased write latency
- More complex error handling

Benefits:
- Strong consistency
- Fast reads (always cached)
- No stale data
- Regulatory compliance
```

---

## 11. Push vs Pull Models

### Definitions

**Push**: Server sends data to clients proactively
**Pull**: Clients request data from server

### Trade-off Matrix

| Aspect | Push | Pull |
|--------|------|------|
| **Latency** | Instant updates | Polling delay |
| **Server Load** | Connection overhead | Request spikes |
| **Client Complexity** | Handle incoming data | Simpler (request/response) |
| **Bandwidth** | Only when needed | Potential waste (polling) |
| **Use Case** | Real-time updates | Occasional checks |

### Real Interview Examples

#### Example 21: WhatsApp Messages (Push)
**Question**: Design WhatsApp's message delivery system

**Model Decision**:

```
Push Model via WebSocket (CHOSEN):

Architecture:
┌────────────────────────────────────────────┐
│  User A sends message                      │
│  ↓                                         │
│  WhatsApp Server                           │
│  ↓                                         │
│  Push to User B's WebSocket (INSTANT)     │
│  ↓                                         │
│  User B receives (notification + display)  │
│                                            │
│  Latency: 50-200ms                         │
└────────────────────────────────────────────┘

Why Push:
- Instant delivery expected
- Users online (persistent connection)
- Bandwidth efficient (no polling)
- Battery efficient

Implementation:
- WebSocket connection per active user
- Connection pooling (millions of users)
- Fallback to push notifications (offline users)
- Message queue (delivery guarantee)

Trade-offs:
- Server maintains millions of connections
- Complex connection management
- Reconnection logic needed
- Higher server costs

Benefits:
- Sub-second message delivery
- Real-time experience
- No polling waste
- Better user experience
```

#### Example 22: Email Client (Pull)
**Question**: Design an email client sync system

**Model Decision**:

```
Pull Model with Smart Polling (CHOSEN):

Architecture:
┌────────────────────────────────────────────┐
│  Email Client Polling Strategy             │
│                                            │
│  App in foreground:                        │
│    Poll every 60 seconds                   │
│                                            │
│  App in background:                        │
│    Poll every 15 minutes                   │
│                                            │
│  User manually refreshes:                  │
│    Poll immediately                        │
│                                            │
│  No new mail after 5 checks:              │
│    Exponential backoff → 5 min            │
└────────────────────────────────────────────┘

Why Pull:
- Email not time-critical (seconds delay OK)
- Users not always online
- Simpler client implementation
- Lower server resource usage

Benefits:
- Simple HTTP requests
- No persistent connections
- Works with firewalls
- Easy to implement

Trade-offs:
- Delayed delivery (up to poll interval)
- Wasted requests (polling when no mail)
- Battery drain (frequent checks)

Optimizations:
- Push notifications (wake up to poll)
- Conditional requests (If-Modified-Since)
- Exponential backoff (reduce waste)
- User-configured intervals
```

---

## 12. Batch vs Stream Processing

### Definitions

**Batch Processing**: Process large volumes of data at scheduled intervals
**Stream Processing**: Process data continuously as it arrives

### Trade-off Matrix

| Aspect | Batch | Stream |
|--------|-------|--------|
| **Latency** | High (hours/days) | Low (seconds/minutes) |
| **Throughput** | Very high | Moderate |
| **Complexity** | Low | High |
| **Cost** | Lower (scheduled resources) | Higher (always running) |
| **Use Case** | Reports, analytics | Real-time alerts, fraud detection |

### Real Interview Examples

#### Example 23: Spotify Wrapped (Batch)
**Question**: Design Spotify's year-end user statistics

**Processing Decision**:

```
Batch Processing (CHOSEN):

Data Pipeline:
┌────────────────────────────────────────────┐
│  Annual Batch Job (December 1st)          │
│                                            │
│  1. Extract: All user listening data      │
│     Source: 1 year of streaming logs      │
│     Volume: Petabytes of data             │
│                                            │
│  2. Transform: Calculate statistics       │
│     • Top artists                          │
│     • Top songs                            │
│     • Total minutes                        │
│     • Genres                               │
│     • Listening patterns                   │
│                                            │
│  3. Load: Store results                    │
│     • User-specific reports                │
│     • Pre-rendered graphics                │
│                                            │
│  Processing Time: 48-72 hours              │
│  Cost: $100K (spot instances)              │
└────────────────────────────────────────────┘

Why Batch:
- Annual data (not time-sensitive)
- Massive dataset (year of history)
- Complex calculations (percentiles, rankings)
- Cost optimization (scheduled resources)

Benefits:
- Process petabytes efficiently
- Use cheap compute (spot instances)
- Complex aggregations possible
- Results pre-computed

Trade-offs:
- Long processing time
- Results not real-time
- Large resource spike

Optimization:
- Partition by user/region
- Parallel processing (MapReduce)
- Pre-aggregate monthly data
- Cache intermediate results
```

#### Example 24: Uber Fraud Detection (Stream)
**Question**: Design Uber's real-time fraud detection

**Processing Decision**:

```
Stream Processing (CHOSEN):

Real-time Pipeline:
┌────────────────────────────────────────────┐
│  Event Stream (Kafka)                      │
│                                            │
│  Ride Request Event                        │
│  ↓                                         │
│  Stream Processor (Flink/Spark)           │
│  ↓                                         │
│  Fraud Detection Rules:                    │
│  • Multiple rides same time?               │
│  • Unusual location pattern?               │
│  • Payment method flagged?                 │
│  • Velocity check (10 rides/hour?)        │
│  ↓                                         │
│  Decision: BLOCK or ALLOW                  │
│  ↓                                         │
│  Response in < 100ms                       │
└────────────────────────────────────────────┘

Why Stream:
- Must detect fraud BEFORE ride completes
- Real-time decision required
- Continuous data flow
- Immediate action needed

Implementation:
- Kafka for event streaming
- Flink for processing
- Redis for state (recent activity)
- ML models (fraud patterns)

Benefits:
- Instant fraud prevention
- Continuous monitoring
- Low latency decisions
- Prevents losses immediately

Trade-offs:
- Complex architecture
- Always-on resources (expensive)
- State management challenges
- Harder to debug

Cost Comparison:
- Batch: Process daily, lose $50K/day to fraud
- Stream: Always running, cost $10K/day
- Decision: Stream worth 5x cost (saves $40K/day)
```

---

## 13. Client-side vs Server-side Rendering

### Definitions

**CSR (Client-Side Rendering)**: Browser renders HTML with JavaScript
**SSR (Server-Side Rendering)**: Server sends pre-rendered HTML

### Trade-off Matrix

| Aspect | CSR | SSR |
|--------|-----|-----|
| **Initial Load** | Slow (download JS first) | Fast (HTML ready) |
| **SEO** | Poor (requires extra work) | Excellent |
| **Interactivity** | Excellent (after load) | Slower transitions |
| **Server Load** | Low | High |
| **Caching** | Easy (static assets) | Complex (dynamic HTML) |

### Real Interview Examples

#### Example 25: Gmail (CSR)
**Question**: Design Gmail's web interface

**Rendering Decision**:

```
Client-Side Rendering (CHOSEN):

Initial Load:
┌────────────────────────────────────────────┐
│  1. Download HTML shell (5KB)              │
│  2. Download JS bundle (500KB)             │
│  3. Execute JavaScript                      │
│  4. API calls for emails                   │
│  5. Render interface                        │
│                                            │
│  Total: 2-3 seconds initial                │
└────────────────────────────────────────────┘

After Load:
- Instant navigation (no page reload)
- Real-time updates (WebSocket)
- Smooth animations
- Offline capable (Service Worker)

Why CSR:
- Highly interactive application
- Authenticated users (no SEO needs)
- Long sessions (initial load acceptable)
- Real-time features critical

Benefits:
- Instant email switching
- Real-time notifications
- Smooth UI interactions
- Lower server costs

Trade-offs:
- Slow initial load (2-3s)
- Large JavaScript bundle
- Requires powerful client device
- SEO not possible (but not needed)

Optimization:
- Code splitting (load features on demand)
- Service Worker (cache assets)
- Progressive Web App
- Lazy loading
```

#### Example 26: E-commerce Product Pages (SSR)
**Question**: Design Amazon's product page rendering

**Rendering Decision**:

```
Server-Side Rendering (CHOSEN):

Request Flow:
┌────────────────────────────────────────────┐
│  User requests: /product/iphone-15         │
│  ↓                                         │
│  Server:                                    │
│  1. Query database (product info)          │
│  2. Render HTML with product data          │
│  3. Send complete HTML                     │
│  ↓                                         │
│  Browser displays immediately               │
│                                            │
│  Time to First Contentful Paint: 500ms     │
└────────────────────────────────────────────┘

Why SSR:
- SEO critical (Google shopping)
- Fast initial load essential (conversions)
- Works without JavaScript
- Crawlers must see content

Benefits:
1. SEO:
   - Google indexes full content
   - Product appears in search
   - Social media previews work

2. Performance:
   - Instant content display
   - Works on slow devices
   - Low-end phone support

3. Conversion:
   - Fast load = higher sales
   - 100ms delay = 1% revenue loss

Trade-offs:
- Server rendering costs
- Cache complexity
- Slower subsequent navigation
- Higher server load

Hybrid Approach:
- SSR for first page load
- CSR for subsequent navigation
- Best of both worlds

Implementation:
- Next.js / React SSR
- CDN caching (edge rendering)
- Incremental Static Regeneration
- Personalization via client-side
```

---

## 14. Stateful vs Stateless Services

### Definitions

**Stateful**: Server maintains client session state
**Stateless**: Server stores no client information between requests

### Trade-off Matrix

| Aspect | Stateful | Stateless |
|--------|----------|-----------|
| **Scaling** | Complex (sticky sessions) | Easy (any server) |
| **Resilience** | Server failure = lost state | No state to lose |
| **Performance** | Fast (cached state) | Slower (fetch each time) |
| **Complexity** | Higher | Lower |

### Real Interview Examples

#### Example 27: Gaming Server (Stateful)
**Question**: Design a multiplayer game server

**Architecture Decision**:

```
Stateful Design (CHOSEN):

Game Session State (In-Memory):
┌────────────────────────────────────────────┐
│  Game Server Instance #42                  │
│                                            │
│  Active Game Session:                      │
│  • Players: [P1, P2, P3, P4]              │
│  • Positions: {...}                        │
│  • Health: {...}                           │
│  • Inventory: {...}                        │
│  • Physics state: {...}                    │
│  • Last 100 actions: [...]                │
│                                            │
│  Update Rate: 60 times/second              │
│  Latency: 16ms per update                  │
└────────────────────────────────────────────┘

Why Stateful:
- 60 FPS requires instant state access
- Cannot fetch from database each frame
- Player session lasts hours
- Consistency critical (game state)

Benefits:
- Ultra-low latency (in-memory)
- Complex state management
- Real-time synchronization
- Consistent game experience

Trade-offs:
- Players sticky to specific server
- Server crash = session lost
- Cannot easily scale mid-game
- More complex deployment

Mitigation:
- State snapshots every 5 seconds
- Reconnection with state recovery
- Gradual migration (finish game first)
- Monitoring for server health
```

#### Example 28: REST API (Stateless)
**Question**: Design a public REST API

**Architecture Decision**:

```
Stateless Design (CHOSEN):

Request Flow:
┌────────────────────────────────────────────┐
│  Request with all context                  │
│                                            │
│  GET /api/users/123                        │
│  Authorization: Bearer JWT_TOKEN           │
│  ↓                                         │
│  Load Balancer → Any Available Server      │
│  ↓                                         │
│  Server:                                    │
│  1. Decode JWT (user info + permissions)   │
│  2. Query database                          │
│  3. Return response                         │
│  4. Forget everything                       │
│                                            │
│  Next request → Can go to different server │
└────────────────────────────────────────────┘

Why Stateless:
- Easy horizontal scaling
- Server failures don't matter
- Simple deployment
- No session management

Benefits:
1. Scaling:
   - Add servers instantly
   - No session migration
   - Perfect load distribution

2. Reliability:
   - Any server failure OK
   - No state to lose
   - Automatic recovery

3. Simplicity:
   - No session storage
   - Easy to understand
   - Standard REST principles

Trade-offs:
- JWT validation every request
- Database query every request
- Slightly higher latency
- Larger request size (include context)

Optimization:
- Cache user data in Redis
- JWT with short expiry
- Database connection pooling
- Read replicas for queries
```

---

## 15. Replication Trade-offs

### Replication Strategies

**Leader-Follower**: One write node, multiple read nodes
**Multi-Leader**: Multiple write nodes
**Leaderless**: All nodes equal (Cassandra-style)

### Trade-off Matrix

| Strategy | Write Complexity | Read Scalability | Consistency | Use Case |
|----------|------------------|------------------|-------------|----------|
| **Leader-Follower** | Simple | Excellent | Strong/Eventual | Most web apps |
| **Multi-Leader** | Complex | Excellent | Eventual | Multi-datacenter |
| **Leaderless** | Complex | Excellent | Tunable | High availability |

### Real Interview Examples

#### Example 29: Read-Heavy App (Leader-Follower)
**Question**: Design Medium's article serving system

**Replication Decision**:

```
Leader-Follower Replication (CHOSEN):

Architecture:
┌────────────────────────────────────────────┐
│  Leader (Primary) - us-west-2             │
│  • Handles all writes                      │
│  • Single source of truth                  │
│  • Replicates to followers                 │
│  ↓         ↓         ↓                     │
│  Follower  Follower  Follower             │
│  (Read)    (Read)    (Read)               │
└────────────────────────────────────────────┘

Read/Write Split:
- Writes: Always to leader
- Reads: Round-robin to followers
- Replication lag: < 100ms

Read Pattern (99% of traffic):
┌────────────────────────────────────────────┐
│  User requests article                     │
│  ↓                                         │
│  Load balancer → Random follower           │
│  ↓                                         │
│  Return article (cached in follower)       │
│  ↓                                         │
│  Response time: 10-50ms                    │
└────────────────────────────────────────────┘

Write Pattern (1% of traffic):
┌────────────────────────────────────────────┐
│  Author publishes article                  │
│  ↓                                         │
│  Route to leader                            │
│  ↓                                         │
│  Write to database                          │
│  ↓                                         │
│  Async replicate to followers (100ms)      │
│  ↓                                         │
│  Return success                             │
└────────────────────────────────────────────┘

Benefits:
- Read scalability (add more followers)
- Simple consistency model
- No write conflicts
- Easy to implement

Trade-offs:
- Replication lag (eventual consistency)
- Leader is single point of failure
- All writes through one node
- Read-after-write may show stale data

Handling Replication Lag:
- Author reads from leader (see own writes)
- Critical reads from leader
- Timestamp-based consistency
- UI shows "Publishing..." during replication
```

---

## 16. Partitioning/Sharding Trade-offs

### Sharding Strategies

**Hash-based**: Partition by hash of key
**Range-based**: Partition by key ranges
**Geography-based**: Partition by location

### Real Interview Examples

#### Example 30: User Data Sharding
**Question**: Design sharding for 1B users

**Sharding Decision**:

```
Hash-based Sharding (CHOSEN):

Shard Function:
user_shard = hash(user_id) % num_shards

┌────────────────────────────────────────────┐
│  Shard 0 (users 0-99M)                    │
│  Shard 1 (users 100M-199M)                │
│  Shard 2 (users 200M-299M)                │
│  ...                                       │
│  Shard 9 (users 900M-999M)                │
└────────────────────────────────────────────┘

Query Routing:
┌────────────────────────────────────────────┐
│  Get user_id=753124689                     │
│  ↓                                         │
│  Calculate: hash(753124689) % 10 = 7       │
│  ↓                                         │
│  Route to Shard 7                          │
│  ↓                                         │
│  Query single shard                        │
│  ↓                                         │
│  Return result                              │
└────────────────────────────────────────────┘

Benefits:
- Even data distribution
- Simple implementation
- No hot spots
- Predictable performance

Trade-offs:
- Range queries expensive (query all shards)
- Rebalancing requires resharding
- Related data may split across shards
- Joins across shards impossible

Hot Shard Problem:
- Celebrity users (100M followers)
- Solution: Consistent hashing
- Solution: Dedicated shard for VIPs
- Solution: Cache celebrity data
```

---

## 17. Security vs Performance

### Common Trade-offs

| Security Feature | Performance Impact | When to Use |
|------------------|-------------------|-------------|
| Encryption at rest | 5-10% overhead | Financial data, PII |
| Encryption in transit (TLS) | 1-3% overhead | Always |
| Input validation | Negligible | Always |
| Rate limiting | Minimal | Public APIs |
| Authentication checks | 10-50ms per request | All requests |

### Real Interview Examples

#### Example 31: API Security
**Question**: Design secure API with good performance

**Security Layering**:

```
Balanced Security Approach:

┌────────────────────────────────────────────┐
│  Request Processing Pipeline               │
│                                            │
│  1. Rate Limiting (1ms)                    │
│     • Stop DDoS                            │
│     • Redis counter check                  │
│                                            │
│  2. TLS Termination (5ms)                  │
│     • Decrypt at load balancer             │
│     • Internal HTTP (fast)                 │
│                                            │
│  3. JWT Validation (10ms)                  │
│     • Verify signature                     │
│     • Check expiration                     │
│     • Cache valid JWTs (1ms)               │
│                                            │
│  4. Input Validation (1ms)                 │
│     • Sanitize inputs                      │
│     • Prevent SQL injection                │
│                                            │
│  5. Business Logic (50ms)                  │
│                                            │
│  Total Security Overhead: 17ms             │
│  Total Request Time: 67ms                  │
│  Security Cost: 25% of request time        │
└────────────────────────────────────────────┘

Trade-offs Made:

High Security (Sensitive Data):
- Check permissions on every field
- Audit log every access
- Encrypt everything
- Cost: 100ms+ per request
- Use for: Banking, Healthcare

Balanced (Most APIs):
- Authentication required
- Basic authorization
- TLS encryption
- Rate limiting
- Cost: 20-50ms overhead
- Use for: Most applications

Low Security (Public Data):
- Optional authentication
- Aggressive caching
- Minimal validation
- Cost: <5ms overhead
- Use for: Public content, CDN
```

---

## 18. Cost vs Performance

### Optimization Strategies

**Performance-First**: Spend for speed
**Cost-First**: Accept slower for cheaper
**Balanced**: Optimize both

### Real Interview Examples

#### Example 32: Startup vs Enterprise
**Question**: Compare infrastructure strategies

**Cost Optimization**:

```
Startup ($5K/month budget):
┌────────────────────────────────────────────┐
│  Cost-Optimized Architecture               │
│                                            │
│  • 2x application servers (t3.medium)     │
│  • 1x database (t3.large)                  │
│  • No caching (save $500/month)           │
│  • No CDN (save $1000/month)              │
│  • No monitoring (use free tier)          │
│  • Single region                           │
│                                            │
│  Performance:                               │
│  • Page load:
