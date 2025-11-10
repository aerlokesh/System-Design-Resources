# Common System Design Problems & Anti-patterns Guide
## Thundering Herd, Thrashing, and Other Critical Issues for Interviews

## Table of Contents
1. [Introduction](#introduction)
2. [Cache Problems](#cache-problems)
3. [Database Problems](#database-problems)
4. [Distributed System Problems](#distributed-system-problems)
5. [Performance Problems](#performance-problems)
6. [Scaling Problems](#scaling-problems)
7. [Network Problems](#network-problems)
8. [Resource Management Problems](#resource-management-problems)
9. [How to Discuss in Interviews](#how-to-discuss-in-interviews)
10. [Problem-Solution Quick Reference](#problem-solution-quick-reference)

---

## Introduction

In system design interviews, identifying potential problems and proposing solutions demonstrates production experience and deep thinking. This guide covers critical issues you should proactively address.

### Why These Matter in Interviews

```
Demonstrating Problem Awareness Shows:
✅ Production experience
✅ Depth of knowledge
✅ Proactive thinking
✅ Understanding of failure modes
✅ Ability to design resilient systems

Interview Impact:
"I'll use caching here" → Good
"I'll use caching, but need to handle cache stampede
by using probabilistic early expiration" → Excellent!
```

---

## Cache Problems

### 1. Thundering Herd (Cache Stampede)

**Problem**: When cached item expires, multiple requests simultaneously hit backend

```
Timeline of Disaster:

t=0: Cache entry expires
t=1: Request 1 → Cache miss → Query DB
t=2: Request 2 → Cache miss → Query DB
t=3: Request 3 → Cache miss → Query DB
...
t=100: Request 100 → Cache miss → Query DB

Result:
- 100 simultaneous DB queries
- Database overload
- Slow responses for all
- Potential DB crash

Visualization:
┌────────────┐
│   Cache    │ ← Entry expires
└──────┬─────┘
       │
   [MISS]
       │
   100 simultaneous
   requests ↓
       │
┌──────▼─────┐
│  Database  │ ← OVERLOAD!
└────────────┘

Real Example:
Popular product page cache expires
→ 10K requests hit DB simultaneously
→ DB response time: 10ms → 10,000ms
→ DB crashes
→ Entire site down
```

**Solutions**:

```
Solution 1: Request Coalescing (Best)
First request acquires lock, others wait for result

┌────────────┐
│   Cache    │ ← MISS
└──────┬─────┘
       │
  Request 1 → Acquires lock → Queries DB → Updates cache
  Request 2 ┐
  Request 3 ├→ Wait for Request 1's result
  Request 4 ┘
  
Result: Only 1 DB query instead of 100

Implementation:
- Use distributed lock (Redis)
- First request gets lock
- Others wait on lock
- All get result when ready

Solution 2: Probabilistic Early Expiration
Refresh cache before it actually expires

TTL: 60 seconds
Probabilistic refresh starts at: 50 seconds
Probability increases as expiration approaches

Formula:
refresh_probability = (current_time - cache_time) / TTL
if (random() < refresh_probability * factor):
    refresh_cache_async()

Benefit: Proactive refresh, no stampede

Solution 3: Always Serve Stale + Background Refresh
Serve expired cache while refreshing in background

Cache-Control: max-age=60, stale-while-revalidate=300

Process:
1. Cache expires
2. Serve stale content immediately
3. Refresh in background
4. Update cache
5. Next request gets fresh data

Benefit: Zero wait time, eventual freshness

Solution 4: Cache Warming
Pre-populate cache before traffic arrives

Use Cases:
- After deployment
- Before Black Friday
- After cache clear
- New content launches

Process:
1. Identify hot items
2. Pre-load into cache
3. Before opening traffic
4. No cold starts
```

### 2. Cache Penetration

**Problem**: Requests for non-existent data bypass cache and hit database

```
Scenario:
Attacker queries: /api/users/99999999 (doesn't exist)

Flow:
Request → Cache (miss) → Database → Not found
Repeat 10K times → 10K DB queries for nothing!

Impact:
- Wasted DB resources
- Potential DoS attack
- Cache useless for defense

Attack Pattern:
for (i = 0; i < 10000; i++):
    query("/api/users/" + random_nonexistent_id())

Result: Database overwhelmed with pointless queries
```

**Solutions**:

```
Solution 1: Cache Null Values (Simple)
Store "not found" in cache

Cache: user:99999999 → NULL
TTL: 5 minutes

Next request:
→ Cache hit (NULL)
→ Return 404
→ No DB query

Pros: Simple, effective
Cons: Cache pollution if many non-existent keys

Solution 2: Bloom Filter (Better)
Probabilistic data structure, fast membership check

Before querying DB:
if not bloom_filter.might_contain(key):
    return "Not found"
    
Only query DB if bloom filter says "might exist"

Characteristics:
- False positives possible (might say exists when doesn't)
- No false negatives (if says doesn't exist, definitely doesn't)
- Very fast (O(1))
- Memory efficient

Example:
1B users, 1% false positive rate
Bloom filter: Only 1.2GB memory!

Solution 3: Request Validation
Validate request before processing

Example:
User ID format: 1-100000000
If outside range → Immediate 400 Bad Request
Never hits cache or DB

Patterns:
- UUID format validation
- Range checks
- Whitelist validation
```

### 3. Cache Avalanche

**Problem**: Large portion of cache expires at same time

```
Scenario:
Batch job populates cache at midnight
All entries: TTL = 1 hour
At 1:00 AM: ALL entries expire simultaneously

Timeline:
00:00 - Cache populated (10K entries)
00:59 - All entries valid
01:00 - ALL expire at once!
01:00 - 10K requests → 10K DB queries
01:00 - Database overload

Similar to thundering herd but affects multiple keys
```

**Solutions**:

```
Solution 1: Randomize TTL (Best)
Add jitter to expiration times

Base TTL: 3600 seconds (1 hour)
Actual TTL: 3600 + random(0, 600)

Result:
- Entries expire over 10-minute window
- Gradual DB load increase
- No spike

Solution 2: Staggered Cache Population
Don't populate all at once

Instead of:
for item in items:
    cache.set(item, ttl=3600)

Do:
for i, item in enumerate(items):
    ttl = 3600 + (i % 600)
    cache.set(item, ttl=ttl)

Solution 3: Multi-Level Cache
L1 (hot): Short TTL, refresh often
L2 (warm): Medium TTL
L3 (cold): Long TTL

If L1 expires, serve from L2 while refreshing L1
```

### 4. Cache Inconsistency

**Problem**: Cache and database out of sync

```
Scenario:
1. User updates profile in DB
2. Cache still has old data
3. Other users see stale information
4. Inconsistent user experience

Timeline:
t=0:  DB: name="John", Cache: name="John" ✓
t=10: User updates → DB: name="Jane"
t=11: Cache still: name="John" ✗ (STALE!)
t=15: Other user reads cache → sees "John" (WRONG!)
```

**Solutions**:

```
Solution 1: Write-Through Cache
Update cache immediately after DB write

Process:
1. Write to DB
2. Update cache
3. Return success

Pros: Always consistent
Cons: Extra latency on writes

Solution 2: Cache Invalidation
Delete cache entry on write

Process:
1. Write to DB
2. Delete from cache
3. Next read: Cache miss → Fetch from DB → Cache

Pros: Simple, eventually consistent
Cons: Next request slower (cache miss)

Solution 3: Time-Based Expiration
Accept short-term inconsistency

TTL: 30 seconds
Inconsistency window: Max 30 seconds

Good for: Non-critical data (likes, view counts)
Bad for: Critical data (inventory, payments)

Solution 4: Event-Driven Invalidation
Publish cache invalidation events

Process:
1. Write to DB
2. Publish event (Kafka/Redis)
3. Cache listeners receive event
4. Invalidate relevant cache entries

Benefit: Distributed cache consistency
```

---

## Database Problems

### 1. N+1 Query Problem

**Problem**: Making N database queries when 1 would suffice

```
Scenario: Fetch posts with author names

Bad Approach (N+1):
posts = db.query("SELECT * FROM posts LIMIT 10")
for post in posts:
    author = db.query("SELECT * FROM users WHERE id = ?", post.author_id)
    
Queries: 1 (posts) + 10 (authors) = 11 queries!

With 100 posts: 101 queries!

Timeline:
- Query 1: Fetch 100 posts (10ms)
- Queries 2-101: Fetch each author (10ms each)
- Total time: 10 + (100 * 10) = 1010ms (1 second!)

Database load: 101 queries for single page
```

**Solutions**:

```
Solution 1: JOIN Query
Single query with JOIN

posts_with_authors = db.query("""
    SELECT posts.*, users.name as author_name
    FROM posts
    JOIN users ON posts.author_id = users.id
    LIMIT 100
""")

Queries: 1 query total
Time: 15ms (slightly slower query, but one round trip)
Database load: 1 query

Improvement: 101 queries → 1 query (100x better!)

Solution 2: Batch Query
Fetch all authors in one query

posts = db.query("SELECT * FROM posts LIMIT 100")
author_ids = [post.author_id for post in posts]
authors = db.query(
    "SELECT * FROM users WHERE id IN (?)",
    author_ids
)

Queries: 2 queries total (posts + authors)
Time: 20ms
Database load: 2 queries

Improvement: 101 queries → 2 queries (50x better!)

Solution 3: Dataloader Pattern (GraphQL)
Batch and cache requests within single request

Automatically combines:
- Multiple user queries into one
- Deduplicates requests
- Caches within request scope

Used by: GraphQL servers, modern ORMs
```

### 2. Hot Partition (Hot Shard)

**Problem**: Uneven load distribution across database shards

```
Scenario: User database sharded by userId

Shard 1: Celebrity users (Kim Kardashian, etc.)
  - 1M followers each
  - High read load
  - Load: 100K QPS

Shard 2: Regular users
  - 100 followers each
  - Normal read load
  - Load: 100 QPS

Visualization:
┌──────────────┐
│   Shard 1    │ ← 100K QPS (OVERLOADED!)
│  (Celebrities)│
└──────────────┘

┌──────────────┐
│   Shard 2    │ ← 100 QPS (underutilized)
│ (Regular)    │
└──────────────┘

Problem: Sharding doesn't help!
Can't scale Shard 1 independently
```

**Solutions**:

```
Solution 1: Dedicated Shards for Hot Keys
Separate shards for known hot data

┌──────────────┐
│Celebrity Shard│ ← Multiple replicas
│  (Read-heavy) │    (20 read replicas)
└──────────────┘

┌──────────────┐
│ Regular Shard│ ← Standard replicas
│   (Normal)   │    (3 read replicas)
└──────────────┘

Benefit: Scale hot shards independently

Solution 2: Caching Hot Data
Aggressively cache celebrity profiles

Cache Layer:
Celebrity profiles: 95% cache hit
Regular profiles: 50% cache hit

Load on Shard 1: 100K → 5K QPS (20x reduction!)

Solution 3: Read Replicas for Hot Shards
Add more read replicas for hot shards

Shard 1: 1 primary + 20 read replicas
Shard 2: 1 primary + 3 read replicas

Each replica: 5K QPS
20 replicas: 100K QPS (balanced!)

Solution 4: Consistent Hashing with Virtual Nodes
Better distribution

Traditional: hash(key) % N
Problem: Uneven distribution

Consistent Hashing: 
- Multiple virtual nodes per physical node
- Better load distribution
- Easier rebalancing
```

### 3. Database Connection Exhaustion

**Problem**: Running out of database connections

```
Scenario:
Database max connections: 100
Application servers: 20
Each creates 10 connections: 20 * 10 = 200

Result: Connection pool exhausted!
New requests fail with "Too many connections"

Cascade Effect:
1. DB connections full
2. Requests queue up
3. Application servers slow
4. Users see timeouts
5. More retry requests
6. Even worse overload
```

**Solutions**:

```
Solution 1: Connection Pooling
Reuse connections efficiently

Configuration:
- Pool size: 10 connections per app server
- Max wait time: 5 seconds
- Idle timeout: 30 seconds
- Connection validation on borrow

Benefit: Controlled resource usage

Solution 2: Calculate Optimal Pool Size
Formula:
Pool Size = (Core Count * 2) + Disk Count

Example:
- 4 cores
- 1 disk
- Pool size: (4 * 2) + 1 = 9 connections

Don't over-provision!

Solution 3: Queue-Based Processing
Queue requests when connections busy

Process:
1. Request arrives
2. All connections busy → Add to queue
3. Connection available → Process from queue
4. Controlled concurrency

Benefit: Graceful degradation

Solution 4: Read Replicas
Distribute read load

Write queries → Primary (1 connection pool)
Read queries → Replicas (N connection pools)

Benefit: More total connections available
```

---

## Distributed System Problems

### 1. Split Brain

**Problem**: Network partition causes multiple nodes to think they're primary

```
Scenario:
┌─────────┐   Network   ┌─────────┐
│ Node A  │   Partition │ Node B  │
│(Primary)│◄─────X─────►│(Backup) │
└─────────┘             └─────────┘

After Partition:
Node A: "I'm primary!" (accepts writes)
Node B: "A is dead, I'm primary now!" (accepts writes)

Result:
- Two primaries (split brain)
- Conflicting writes
- Data inconsistency
- Data loss on merge

Example:
Node A: balance = $100 → withdraw $50 → balance = $50
Node B: balance = $100 → deposit $25 → balance = $125

When partition heals: Which is correct?!
```

**Solutions**:

```
Solution 1: Quorum-Based Decisions
Require majority to make decisions

3-node cluster:
Need 2 nodes to agree (quorum)

After partition:
- Side with 2 nodes: Can elect primary ✓
- Side with 1 node: Cannot (no quorum) ✗

Benefit: Only one primary possible

Solution 2: Fencing Tokens
Use monotonically increasing tokens

Process:
1. Leader gets token (e.g., 5)
2. All operations tagged with token
3. Storage only accepts higher tokens
4. Old leader (token 4) gets rejected

Benefit: Old leader can't cause damage

Solution 3: Witness Node
Third party helps decide

Cluster: Node A, Node B, Witness
Partition: A vs B+Witness

Witness sides with one node
Only one can become primary

Solution 4: Shared Storage Lease
Use external storage for coordination

Leader writes "I'm alive" to shared storage every 5s
If can't write → Step down as leader
Only one node can hold write lock

Common in: Zookeeper, etcd, Consul
```

### 2. Cascading Failures

**Problem**: Failure in one component causes failures in dependent components

```
Scenario:

Service A → Service B → Service C → Database

Database slow (10s response time)
↓
Service C times out waiting for DB
↓
Service C fails/overloads
↓
Service B times out waiting for C
↓
Service B fails/overloads  
↓
Service A times out waiting for B
↓
Entire system down!

Timeline:
t=0:  Database slow (10s queries)
t=5:  Service C: 500 errors (timeout)
t=10: Service B: 500 errors (C down)
t=15: Service A: 500 errors (B down)
t=20: All services down (cascade complete)

Cascade Effect:
One slow database → Entire system failure
```

**Solutions**:

```
Solution 1: Circuit Breaker
Stop calling failed service, fail fast

States:
Closed → Service OK, pass requests
Open → Service failing, reject immediately
Half-Open → Test if recovered

Process:
1. Service B fails (50% errors)
2. Circuit opens
3. Service A stops calling B
4. Service A returns cached/default response
5. After 30s, circuit tries again (half-open)

Benefit: Prevent cascade, fast failure

Solution 2: Timeouts
Set aggressive timeouts

Bad:
timeout = 30 seconds (too long!)
User waits 30s, resources held

Good:
timeout = 1 second
Fail fast, free resources quickly

Best Practice:
- API calls: 1-5 seconds
- Database: 100-500ms
- Cache: 50-100ms

Solution 3: Bulkheads
Isolate failures, separate resource pools

Separate thread pools:
- Critical operations: 80 threads
- Non-critical operations: 20 threads

If non-critical pool exhausts:
- Critical operations unaffected
- Isolated failure

Like ship compartments: one floods, others safe

Solution 4: Graceful Degradation
Reduce functionality instead of complete failure

Example:
Recommendation service down:
- Don't fail entire page
- Show default recommendations
- Log error for investigation
- User sees page (without personalized recs)

Fallback Chain:
1. Try primary service
2. Try cache
3. Try backup service  
4. Return default
5. Only then fail
```

### 3. Distributed Clock Skew

**Problem**: Servers have different times, causes ordering issues

```
Scenario:
Server A: 10:00:00 AM
Server B: 10:00:05 AM (5 seconds ahead)

User posts tweet on Server A: timestamp = 10:00:00
User posts another on Server B: timestamp = 10:00:05

Timeline shows: Second tweet before first! (Wrong order)

Real Impact:
- Messages out of order
- Audit logs incorrect
- Cache expiration issues
- Distributed locks problems
```

**Solutions**:

```
Solution 1: Logical Clocks (Lamport)
Use sequence numbers instead of timestamps

Each server maintains counter:
Server A: counter = 42 → increment to 43
Server B: counter = 38 → increment to 39

Ordering by counter, not time
Benefit: Correct causal ordering

Solution 2: Vector Clocks
Track causality across servers

Each server has vector:
Server A: [A:5, B:3, C:2]
Server B: [A:4, B:6, C:2]

Can determine which happened first
Used in: Cassandra, Riak, Dynamo

Solution 3: NTP Synchronization
Keep clocks in sync

Configuration:
- Use NTP servers
- Sync every minute
- Monitor clock drift
- Alert if drift > 100ms

Best Practice:
- Use NTP
- Plus logical clocks for ordering
- Don't rely solely on timestamps

Solution 4: Hybrid Logical Clocks (HLC)
Combine physical time + logical counter

Used in: CockroachDB, YugabyteDB
Benefit: Human-readable + causally ordered
```

---

## Performance Problems

### 1. Slow Query Performance

**Problem**: Database queries take too long

```
Scenario:
Query: SELECT * FROM users WHERE email = 'john@example.com'
No index on email column
Full table scan: 10M rows

Result:
- Query time: 5 seconds (terrible!)
- Database CPU: 100%
- Other queries slow
- Application timeout

Impact at Scale:
100 requests/sec * 5 seconds = 500 concurrent queries
Database overwhelmed!
```

**Solutions**:

```
Solution 1: Indexes (Most Important)
Add index on frequently queried columns

CREATE INDEX idx_users_email ON users(email);

Result:
- Query time: 5 seconds → 5ms (1000x faster!)
- Index lookup instead of full scan

Best Practices:
✅ Index foreign keys
✅ Index WHERE clause columns
✅ Index JOIN columns
✅ Compound indexes for multiple columns
❌ Don't over-index (slows writes)

Solution 2: Query Optimization
Optimize the query itself

Bad:
SELECT * FROM orders 
WHERE YEAR(created_at) = 2024

Problem: Function on column prevents index use

Good:
SELECT * FROM orders
WHERE created_at >= '2024-01-01'
  AND created_at < '2025-01-01'

Benefit: Can use index on created_at

Solution 3: Denormalization
Store precomputed results

Instead of:
SELECT users.*, COUNT(posts.id) as post_count
FROM users
JOIN posts ON users.id = posts.user_id
GROUP BY users.id

Store post_count in users table:
UPDATE users SET post_count = post_count + 1

Trade-off: Storage space for query speed

Solution 4: Caching
Cache query results

Popular queries: Cache for 5-60 minutes
Reduces database load 80-90%

Solution 5: Read Replicas
Distribute read load

Primary: Writes only
Replicas (5): Reads only

Load: 90% reads, 10% writes
Replicas handle most load
```

### 2. Thrashing

**Problem**: System spends more time managing resources than doing useful work

```
Type 1: Memory Thrashing (Disk Swapping)

Scenario:
Application needs: 16GB RAM
Server has: 8GB RAM

Result:
- Constant paging to disk
- Disk I/O (slow!)
- CPU mostly waiting
- Throughput collapses

Timeline:
Normal: 1000 requests/sec
Thrashing: 10 requests/sec (100x slower!)

Visualization:
RAM Full → Swap to Disk → Slow
└─→ More requests queue → More RAM needed
    └─→ More swapping → Slower
        └─→ Cycle continues!

Type 2: CPU Thrashing (Context Switching)

Scenario:
100 threads on 4-core CPU

Result:
- Constant context switching
- CPU overhead
- Little actual work done

Type 3: Database Thrashing

Scenario:
- Too many concurrent queries
- Database spends time managing queries
- Little actual query execution
```

**Solutions**:

```
Solution 1: Right-Size Resources
Monitor actual needs, provision accordingly

Monitoring:
- Memory usage
- Disk I/O wait
- CPU context switches
- Swap usage

Action:
If memory > 80% sustained → Scale up/out

Solution 2: Limit Concurrency
Control concurrent operations

Example:
- Max concurrent DB connections: 50
- Max threads: 4 * CPU cores
- Queue requests beyond limit

Benefit: Prevent thrashing

Solution 3: Backpressure
Slow down upstream when overloaded

Process:
1. Detect overload (queue > 1000)
2. Return 429 or 503
3. Client backs off
4. System recovers

Better than accepting all requests and thrashing

Solution 4: Load Shedding
Drop low-priority requests when overloaded

Priority levels:
- Critical: Always process
- High: Process if possible
- Low: Drop first when overloaded

Example:
Under load → Drop analytics, keep core functionality

Solution 5: Horizontal Scaling
Add more servers before thrashing

Auto-scale trigger:
- Memory > 70%
- CPU > 70%
- Queue depth > 100

Prevent reaching thrashing state
```

---

## Scaling Problems

### 1. Single Point of Failure (SPOF)

**Problem**: One component failure brings down entire system

```
Scenario:
┌─────────┐
│ Load    │
│Balancer │ ← SPOF!
└────┬────┘
     │
 ┌───┴───┬────┐
 │       │    │
[S1]   [S2]  [S3]

If load balancer fails → All servers unreachable

Common SPOFs:
- Single load balancer
- Single database
- Single message queue
- Single API gateway
- Single DNS server
```

**Solutions**:

```
Solution: Redundancy at Every Layer

Load Balancers:
┌─────┐  ┌─────┐
│ LB1 │  │ LB2 │  Active-Active or Active-Passive
└─────┘  └─────┘

Database:
┌─────────┐  ┌─────────┐  ┌─────────┐
│Primary  │  │Replica 1│  │Replica 2│
└─────────┘  └─────────┘  └─────────┘

Message Queue:
┌──────┐  ┌──────┐  ┌──────┐
│Kafka1│  │Kafka2│  │Kafka3│  Clustered
└──────┘  └──────┘  └──────┘

API Gateway:
┌────┐  ┌────┐  ┌────┐
│AG1 │  │AG2 │  │AG3 │  Multiple instances
└────┘  └────┘  └────┘

Best Practice:
- No single anything
- Redundancy at all layers
- Automated failover
- Health checks
```

### 2. Resource Starvation

**Problem**: One resource hogs resources, starves others

```
Scenario: Shared Thread Pool

Slow API endpoint: /api/slow-report (30 seconds)
Fast API endpoint: /api/quick-read (10ms)

Request pattern:
- 100 /slow-report requests arrive
- Use all 100 threads (30s each)
- New /quick-read requests wait
- Fast endpoint starved by slow endpoint!

User Impact:
- Quick reads: 10ms → 30 seconds!
- All endpoints appear slow
- Poor user experience
```

**Solutions**:

```
Solution 1: Separate Resource Pools (Bulkheads)
Isolate resources per endpoint type

Thread Pools:
- Critical operations: 80 threads
- Reports: 20 threads
- Background: 10 threads

Benefit: Slow reports can't starve critical ops

Solution 2: Priority Queues
Process high-priority first

Queue levels:
1. Critical (user-facing)
2. High (important background)
3. Medium (nice-to-have)
4. Low (can wait)

Under load: Drop low-priority

Solution 3: Async Processing
Move slow operations out of request path

Pattern:
POST /api/reports → Return 202 Accepted immediately
Background worker generates report
GET /api/reports/{id}/status → Check progress
Notification when complete

Benefit: Fast endpoint stays fast

Solution 4: Timeout Different Operations Differently
Short timeout for critical, longer for non-critical

Critical API: 500ms timeout
Reports: 30s timeout
Background: 5min timeout

Fail fast for user-facing operations
```

### 3. Retry Storm

**Problem**: Failed requests retry, causing even more load

```
Scenario:
Database slow (responds in 5s)
Application timeout: 1s

Timeline:
t=0:  Request 1 → DB (will take 5s)
t=1:  Request 1 times out → Retry
t=1:  Request 2 (original) → DB
t=2:  Request 1 retry times out → Retry again
t=2:  Request 3 (original) → DB
...

Result:
- Exponential request growth
- Database increasingly overloaded
- Situation gets worse, not better!

Visualization:
Requests:  1 → 2 → 4 → 8 → 16 → 32 → OVERLOAD!
Database:  Slow → Slower → Slowest → DOWN
```

**Solutions**:

```
Solution 1: Exponential Backoff
Increase delay between retries

Retry 1: Wait 1 second
Retry 2: Wait 2 seconds
Retry 3: Wait 4 seconds
Retry 4: Wait 8 seconds
Max: 30 seconds

Benefit: Gives system time to recover

Solution 2: Jitter
Add randomness to backoff

Instead of: exactly 4 seconds
Use: 3-5 seconds (random)

Benefit: Prevents synchronized retries

Solution 3: Max Retries + Circuit Breaker
Limit attempts, then stop trying

Max retries: 3
After 3 failures → Open circuit
Don't retry for 30 seconds
Then try again (half-open)

Benefit: Stop hammering failed service

Solution 4: Retry Budget
Limit total retries across system

Budget: 10% of requests can be retries
Track: retry_rate = retries / total_requests

If retry_rate > 10%:
- Stop retrying
- Investigate root cause
- Don't make problem worse

Used by: Google SRE practices
```

---

## Network Problems

### 1. Network Partition

**Problem**: Network split divides system into isolated groups

```
Scenario:
┌──────────┐        ┌──────────┐
│ Client A │        │ Client B │
└────┬─────┘        └────┬─────┘
     │                   │
┌────▼────┐    X    ┌────▼────┐
│Server 1 │◄───────►│Server 2 │
│(US East)│ Network │(US West)│
└─────────┘ Partition└─────────┘

Impact:
- Clients see different data
- Write conflicts possible
- Inconsistent state
- Service disruption

CAP Theorem applies:
Must choose: Consistency OR Availability
```

**Solutions**:

```
Solution 1: CP (Consistency)
Reject requests in minority partition

3-node cluster partitioned:
- 2 nodes: Continue operation ✓
- 1 node: Reject requests ✗

Benefit: Consistent
Cost: Lower availability

Use: Banking, inventory

Solution 2: AP (Availability)  
Accept requests in all partitions

Both partitions continue
Data conflicts resolved later

Benefit: Available
Cost: Temporary inconsistency

Use: Social media, recommendations

Solution 3: Conflict-Free Replicated Data Types (CRDTs)
Data structures that automatically merge

Examples:
- Counter: Merge by summing
- Set: Merge by union
- Register: Last-write-wins with timestamp

Benefit: Automatic conflict resolution

Solution 4: Multi-Region Active-Active
Deploy in multiple independent regions

Region failure:
- Other regions continue
- Clients route to healthy region
- Data replicated async

Cost: Higher, complexity
Benefit: Highest availability
```

### 2. Connection Pool Exhaustion

**Problem**: Running out of outbound connections

```
Scenario:
Service A calls Service B
100 threads in Service A
Each thread makes HTTP call to Service B
Connection pool size: 50

Result: 50 threads waiting for connections!
Requests queue up, timeouts occur

Impact:
- Service A appears slow
- Cascades to dependent services
- User-facing latency increases
```

**Solutions**:

```
Solution: Properly Size Connection Pools

Formula:
connections_needed = 
    concurrent_requests * avg_response_time

Example:
- 100 requests/sec
- Average response: 100ms
- Concurrent: 100 * 0.1 = 10 connections needed
- Add buffer (2x): 20 connections

Monitor and adjust based on actual usage
```

---

## Resource Management Problems

### 1. Memory Leak

**Problem**: Memory not released, gradually consumes all RAM

```
Impact Over Time:
Hour 1: Memory usage: 2GB
Hour 2: Memory usage: 4GB
Hour 3: Memory usage: 6GB
Hour 4: Memory usage: 8GB (MAX!)
Hour 5: Out of Memory → Crash!

Common Causes:
- Forgotten event listeners
- Growing caches without eviction
- Circular references
- Unclosed connections
```

**Solutions**:

```
Solution 1: Monitor Memory Usage
Track memory over time

Alert if:
- Memory growth without flattening
- Memory > 80% sustained

Solution 2: Restart Strategy
Periodic restarts before memory exhausts

- Rolling restart every 24 hours
- Before memory becomes issue
- Automated, zero-downtime

Solution 3: Memory Limits
Enforce limits, automatic cleanup

- Max cache size: 1GB
- LRU eviction when full
- Object pool limits

Solution 4: Proper Resource Management
Always close resources

Pattern:
Open connection/file/stream
Try to use
Finally: Always close

Prevents leaks
```

### 2. File Descriptor Exhaustion

**Problem**: Running out of file descriptors (open files/sockets)

```
Scenario:
System limit: 1024 file descriptors
Application:
- 500 database connections
- 400 HTTP connections
- 100 file handles
- 50 sockets

Total: 1050 → LIMIT EXCEEDED!

Error: "Too many open files"

Impact:
- Can't accept new connections
- Can't open files
- Application crashes
```

**Solutions**:

```
Solution 1: Increase Limits
System configuration

ulimit -n 65536  # Increase to 65K

Best Practice:
- Development: 1024
- Production: 65536
- High-scale: 1000000

Solution 2: Connection Pooling
Reuse connections

Instead of:
- Open connection per request
- 1000 requests = 1000 connections

Use pool:
- 50 connections reused
- 1000 requests = 50 connections

Solution 3: Monitor Usage
Track open file descriptors

Alert if > 80% of limit

Solution 4: Close Resources
Always close when done

Pattern:
- Open → Use → Close
- Use try-finally blocks
- Automated resource management
```

---

## How to Discuss in Interviews

### Framework for Mentioning Problems

```
STEP 1: Design Initial System
"For caching, I'll use Redis with 5-minute TTL"

STEP 2: Proactively Identify Problems
"This could cause thundering herd when cache expires,
where all requests hit the database simultaneously"

STEP 3: Propose Solutions
"To mitigate, I'll use request coalescing - first
request acquires lock and fetches data, others wait
for the result. This reduces 1000 DB queries to just 1"

STEP 4: Show Trade-offs
"Alternative is probabilistic early expiration, which
is simpler but requires tuning the refresh window"

This shows: Depth, experience, proactive thinking
```

### When to Mention Specific Problems

```
┌────────────────────┬────────────────────────────────┐
│ Problem            │ Mention When Discussing...     │
├────────────────────┼────────────────────────────────┤
│ Thundering Herd    │ Caching strategy               │
├────────────────────┼────────────────────────────────┤
│ Cache Penetration  │ Security, DoS protection       │
├────────────────────┼────────────────────────────────┤
│ N+1 Queries        │ Database access patterns       │
├────────────────────┼────────────────────────────────┤
│ Hot Partition      │ Database sharding              │
├────────────────────┼────────────────────────────────┤
│ Split Brain        │ Leader election, HA            │
├────────────────────┼────────────────────────────────┤
│ Cascading Failure  │ Service dependencies           │
├────────────────────┼────────────────────────────────┤
│ Thrashing          │ Resource provisioning          │
├────────────────────┼────────────────────────────────┤
│ SPOF               │ High availability design       │
├────────────────────┼────────────────────────────────┤
│ Retry Storm        │ Error handling, resilience     │
├────────────────────┼────────────────────────────────┤
│ Clock Skew         │ Distributed ordering           │
└────────────────────┴────────────────────────────────┘
```

### Strong Interview Phrases

```
✅ "To prevent thundering herd, I'll use request coalescing
   where only the first request queries the database"

✅ "For hot partitions, I'll add more read replicas to
   celebrity user shards and cache aggressively"

✅ "To avoid cascading failures, I'll implement circuit
   breakers with 30-second timeout and graceful degradation"

✅ "N+1 queries are a concern here, so I'll use batch
   loading to fetch all authors in one query"

✅ "To prevent split brain, I'll use quorum-based leader
   election requiring majority consensus"

✅ "For retry handling, I'll implement exponential backoff
   with jitter to avoid retry storms"
```

---

## Problem-Solution Quick Reference

### Cache Issues

```
Thundering Herd:
→ Request coalescing (lock first request)
→ Probabilistic early expiration
→ Stale-while-revalidate

Cache Penetration:
→ Cache null values
→ Bloom filter
→ Request validation

Cache Avalanche:
→ Randomize TTL (add jitter)
→ Staggered population
→ Multi-level cache

Cache Inconsistency:
→ Write-through cache
→ Cache invalidation on write
→ Event-driven invalidation
```

### Database Issues

```
N+1 Queries:
→ JOIN queries
→ Batch loading
→ Dataloader pattern

Hot Partition:
→ Dedicated shards for hot data
→ Aggressive caching
→ More replicas for hot shards

Connection Exhaustion:
→ Connection pooling
→ Calculate optimal size
→ Read replicas
```

### Distributed System Issues

```
Split Brain:
→ Quorum-based decisions
→ Fencing tokens
→ Witness node

Cascading Failures:
→ Circuit breaker
→ Timeouts (aggressive)
→ Bulkheads (isolation)
→ Graceful degradation

Clock Skew:
→ Logical clocks (Lamport)
→ Vector clocks
→ NTP + logical clocks
```

### Performance Issues

```
Slow Queries:
→ Add indexes
→ Query optimization
→ Caching
→ Read replicas

Thrashing:
→ Right-size resources
→ Limit concurrency
→ Backpressure
→ Load shedding

Resource Starvation:
→ Separate thread pools
→ Priority queues
→ Async processing
```

### Scaling Issues

```
SPOF:
→ Redundancy everywhere
→ Multi-AZ deployment
→ Automated failover

Retry Storm:
→ Exponential backoff
→ Jitter
→ Circuit breaker
→ Retry budget
```

---

## Conclusion

Proactively identifying and addressing these problems in interviews demonstrates production maturity and deep system understanding.

### Remember to:

1. **Identify problems before interviewer asks**
   - Shows proactive thinking
   - Demonstrates experience

2. **Explain impact clearly**
   - Use specific examples
   - Show cascade effects

3. **Propose concrete solutions**
   - Not just "use caching"
   - But "use request coalescing to prevent thundering herd"

4. **Discuss trade-offs**
   - Every solution has costs
   - Show you understand them

### Top 5 Problems to Always Consider

```
1. Thundering Herd
   When: Discussing caching
   Solution: Request coalescing

2. Cascading Failures
   When: Microservices dependencies
   Solution: Circuit breaker + timeouts

3. Hot Partition
   When: Database sharding
   Solution: Cache hot data + more replicas

4. N+1 Queries
   When: Data fetching
   Solution: Batch loading or JOINs

5. Split Brain
   When: Leader election/HA
   Solution: Quorum-based decisions
```

### Interview Impact

```
Average Candidate:
"I'll use caching to improve performance"

Strong Candidate:
"I'll use caching with 5-minute TTL. To prevent
thundering herd when cache expires, I'll implement
request coalescing where the first request acquires
a lock and fetches from DB, while others wait for
the result. This reduces 1000 concurrent DB queries
to just 1, preventing database overload."

Difference: Specificity + Problem awareness + Solution
```

Good luck with your interviews!
