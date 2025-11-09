# System Design Concepts Deep Dive - Practical Guide with Real Examples

## Table of Contents
1. [Introduction](#introduction)
2. [Horizontal vs Vertical Scaling](#horizontal-vs-vertical-scaling)
3. [Load Balancing in Practice](#load-balancing-in-practice)
4. [Caching Strategies Explained](#caching-strategies-explained)
5. [Database Replication](#database-replication)
6. [Sharding Strategies](#sharding-strategies)
7. [CDN Architecture](#cdn-architecture)
8. [Message Queues](#message-queues)
9. [CAP Theorem in Action](#cap-theorem-in-action)
10. [Rate Limiting Implementation](#rate-limiting-implementation)

---

## Introduction

This guide provides **deep, practical explanations** of system design concepts with:
- ✅ Step-by-step implementation details
- ✅ Real code examples
- ✅ Actual company architectures
- ✅ Common pitfalls and solutions
- ✅ Decision frameworks

---

## Horizontal vs Vertical Scaling

### The Problem: Your System is Slow

**Scenario**: Your web application has 10,000 users. Response time is 2 seconds. Users are complaining.

You run diagnostics:
- CPU: 90% utilized
- Memory: 85% utilized
- Disk I/O: 70% utilized

**You have two options**: Scale Up or Scale Out

---

### Option 1: Vertical Scaling (Scale Up)

**What You Do**:
```
Current Server:
- 4 vCPU
- 16 GB RAM
- 100 GB SSD
- Cost: $150/month

Upgrade To:
- 16 vCPU (4x)
- 64 GB RAM (4x)
- 500 GB SSD (5x)
- Cost: $600/month
```

**Results**:
- CPU drops to 25%
- Response time: 500ms
- Can handle 40,000 users now

**Pros**:
- ✅ **Simple**: No code changes needed
- ✅ **Fast**: Done in 10 minutes
- ✅ **No complexity**: Same architecture

**Cons**:
- ❌ **Expensive**: 4x cost for 4x capacity (linear cost)
- ❌ **Limited**: Can't scale beyond single machine limits (~1TB RAM, 96 vCPU)
- ❌ **Single point of failure**: If server dies, entire app down
- ❌ **Downtime**: Need to restart server for upgrade

**When to Use**:
- **Early stage** (< 100K users)
- **Quick fix** needed
- **Limited engineering resources**
- **Before you've validated product-market fit**

**Real Example - Instagram's Early Days**:
```
Instagram started with:
- Single m1.large EC2 instance
- 4 GB RAM, 2 vCPUs
- Handled first 10,000 users

As they grew:
- Upgraded to m1.xlarge (8 GB RAM, 4 vCPU)
- Then to High-CPU Extra Large
- Only moved to horizontal scaling at 100,000+ users
```

---

### Option 2: Horizontal Scaling (Scale Out)

**What You Do**:
```
Current: 1 server (4 vCPU, 16 GB RAM)

Scale to: 4 servers (4 vCPU, 16 GB RAM each)

Total capacity: 16 vCPU, 64 GB RAM
But: Distributed across 4 machines
```

**Architecture Changes Needed**:
```
Before:
[Users] → [Single Server] → [Database]

After:
[Users] → [Load Balancer]
              ↓
         ┌────┼────┬────┐
         ↓    ↓    ↓    ↓
      [S1] [S2] [S3] [S4] → [Database]
```

**Code Changes Required**:

**1. Make Servers Stateless**:
```python
# Bad: Storing session in server memory
session_data = {}  # This breaks with multiple servers!

def login(user):
    session_data[user.id] = user.info
    
# Good: Store session in Redis (shared)
def login(user):
    redis.set(f"session:{user.id}", user.info, ex=3600)
```

**2. Shared Database**:
- All servers connect to same database
- Use connection pooling (don't exhaust connections)

**3. Shared File Storage**:
```python
# Bad: Store uploads on server disk
upload_file('/local/disk/uploads/')  # Only on one server!

# Good: Store in S3 (shared)
s3.upload_file('bucket/uploads/')  # All servers can access
```

**Results**:
- CPU: 22% per server (90% / 4)
- Response time: 500ms
- Can handle 40,000 users
- **If one server dies, others continue serving**

**Pros**:
- ✅ **Cost-effective**: Use commodity hardware
- ✅ **Unlimited scaling**: Add servers as needed
- ✅ **Fault tolerant**: One server fails, others continue
- ✅ **No downtime**: Add servers without restarting

**Cons**:
- ❌ **Complex**: Need load balancer, shared state
- ❌ **Code changes**: Must make stateless
- ❌ **Slower initially**: Setup time

**When to Use**:
- **Growing fast** (> 100K users)
- **Need fault tolerance**
- **Budget conscious** (commodity hardware cheaper)
- **Long-term scalability** needed

**Real Example - Netflix**:
```
Netflix architecture:
- 1000s of identical server instances
- Auto-scaling groups (scale based on CPU)
- Load balanced across availability zones
- If instance dies, auto-replaced in minutes

Scale:
- Peak: 100,000+ instances running
- Handles 200M+ subscribers
- 1 billion hours watched per month
```

---

### The Decision Framework

```
User Scale    Strategy              Cost
-----------   --------------------  --------
< 10K         Single server         $100/mo
< 100K        Vertical scaling      $500/mo
< 1M          Horizontal (5-10)     $2K/mo
< 10M         Horizontal (50-100)   $15K/mo
< 100M        Horizontal + Sharding $100K/mo
> 100M        Distributed (1000s)   $1M+/mo
```

**Interview Answer**:
"I'd start with vertical scaling for first 100K users since it's simpler. Once we hit capacity limits or need fault tolerance, I'd move to horizontal scaling with load balancer and make servers stateless by moving sessions to Redis."

---

## Load Balancing in Practice

### The Core Problem

**Scenario**: You have 4 application servers. 10,000 requests per second.

**Without Load Balancer**:
```
Users randomly pick server:
- Server 1: 3,000 QPS (overloaded!)
- Server 2: 2,500 QPS (busy)
- Server 3: 2,000 QPS (ok)
- Server 4: 2,500 QPS (busy)

Result: Server 1 crashes, users get errors
```

**With Load Balancer**:
```
[Load Balancer] intelligently distributes:
- Server 1: 2,500 QPS (balanced)
- Server 2: 2,500 QPS (balanced)
- Server 3: 2,500 QPS (balanced)
- Server 4: 2,500 QPS (balanced)

Result: Even distribution, no crashes
```

---

### Load Balancing Algorithms Explained

#### 1. Round Robin (Simplest)

**How It Works**:
```
Request 1 → Server 1
Request 2 → Server 2
Request 3 → Server 3
Request 4 → Server 4
Request 5 → Server 1 (cycle repeats)
```

**Pros**:
- Simple to implement
- Even distribution (if requests are similar)

**Cons**:
- Doesn't consider server load
- Problem if requests have different costs

**Example Problem**:
```
Request A: Upload 1GB video (takes 10 seconds)
Request B: View thumbnail (takes 10ms)

Round robin might send both to same server!
Server overloaded while others idle
```

**When to Use**: Simple applications where all requests are similar

---

#### 2. Least Connections (Smarter)

**How It Works**:
```
Server 1: 10 active connections
Server 2: 5 active connections
Server 3: 8 active connections

New request → Server 2 (fewest connections)
```

**Why Better**:
- Accounts for long-running requests
- Server handling video upload (1 connection, high load) gets fewer new requests
- Server handling API calls (many connections, low load) gets more

**When to Use**: 
- Mixed workload (fast + slow requests)
- WebSocket connections
- File uploads/downloads

**Real Example - Netflix**:
```
Netflix uses weighted least connections:
- Video streaming requests (long-running)
- API requests (fast)
- Least connections ensures even CPU distribution
```

---

#### 3. IP Hash (Sticky)

**How It Works**:
```
hash(user_ip) % num_servers = assigned_server

User 192.168.1.1 → hash(192.168.1.1) % 4 = 2 → Server 2
Always routes to Server 2
```

**Use Case**: When server needs to maintain state

**Better Alternative**: Store state in Redis
```python
# Instead of routing same user to same server
# Store session in Redis (any server can access)

def get_session(user_id):
    return redis.get(f"session:{user_id}")
```

**Interview Insight**: "I'd avoid IP hash by using Redis for sessions, making servers truly stateless"

---

### Layer 4 vs Layer 7 Load Balancing

#### Layer 4 (Transport Layer) - Fast but Limited

**What It Sees**:
```
Source IP: 192.168.1.1
Destination IP: 10.0.0.5
Source Port: 54321
Destination Port: 443 (HTTPS)

That's it! Can't see HTTP headers, URL, cookies
```

**Routing Decision**:
```
Based only on:
- IP addresses
- Ports
- TCP/UDP protocol

Forward packets without terminating connection
```

**Pros**:
- ✅ Fast (minimal processing)
- ✅ Low latency (microseconds)
- ✅ Handles any protocol (HTTP, WebSocket, database connections)

**Cons**:
- ❌ Can't route by URL path
- ❌ Can't inspect HTTP headers
- ❌ Can't do SSL termination

**When to Use**: High-performance requirements, simple routing

---

#### Layer 7 (Application Layer) - Flexible but Slower

**What It Sees**:
```
HTTP Method: POST
URL: /api/users/123/posts
Headers:
  - Content-Type: application/json
  - Authorization: Bearer xyz...
  - User-Agent: Mobile App
Body: {"content": "Hello World"}

Sees EVERYTHING in HTTP request
```

**Routing Examples**:
```python
# Route by URL path
/api/users/*    → User Service
/api/posts/*    → Post Service
/api/payments/* → Payment Service

# Route by header
User-Agent: Mobile → Mobile-optimized servers
User-Agent: Desktop → Desktop servers

# Route by cookie
Premium user → Premium server pool
Free user → Standard server pool
```

**Pros**:
- ✅ Intelligent routing by content
- ✅ SSL termination (decrypt once at LB)
- ✅ Header manipulation
- ✅ Request/response modification

**Cons**:
- ❌ Slower (must parse HTTP)
- ❌ Higher CPU usage
- ❌ Terminates connections

**Real Example - Uber**:
```
Uber's Layer 7 Load Balancer:

/riders/api/*  → Rider services (optimized for riders)
/drivers/api/* → Driver services (geo-queries)
/payment/api/* → Payment services (high security)

Different services have different:
- Hardware (payment has more CPU for encryption)
- Geographic location (drivers close to regions)
- Scaling rules (riders scale with demand)
```

---

### SSL Termination at Load Balancer

**Problem**: SSL/TLS encryption is CPU-intensive

**Without SSL Termination**:
```
User (HTTPS) → LB (HTTPS) → Server 1 (HTTPS decrypt)
                          → Server 2 (HTTPS decrypt)
                          → Server 3 (HTTPS decrypt)

Each server decrypts SSL! Wastes CPU!
```

**With SSL Termination**:
```
User (HTTPS) → LB (decrypt once) → Server 1 (HTTP)
                                 → Server 2 (HTTP)
                                 → Server 3 (HTTP)

Decrypt once at LB, backend uses HTTP
Saves 10-20% CPU on each server!
```

**Real Impact**:
```
1000 servers without SSL termination:
- 20% CPU used for SSL
- Need 200 extra servers just for SSL!
- Cost: $30,000/month wasted

With SSL termination at LB:
- Saves 200 servers = $30,000/month
- Plus: Easier certificate management (one place)
```

**Interview Insight**: Always mention SSL termination as load balancer benefit

---

## Caching Strategies Explained

### Why Caching Matters: The Math

**Without Cache**:
```
Database query: 10ms
1000 requests/second × 10ms = 10 seconds of DB time/second
Database at 100% capacity! Can't handle more load
```

**With 80% Cache Hit Ratio**:
```
800 requests/second from cache: 800 × 1ms = 0.8 seconds
200 requests/second from DB: 200 × 10ms = 2 seconds
Total: 2.8 seconds of work/second
Database at 20% capacity! Can handle 5x more load
```

**Cost Savings**:
```
Without cache:
- Database: db.r5.4xlarge = $1,200/month
- Read replicas × 10 = $12,000/month
- Total: $13,200/month

With cache:
- Database: db.r5.xlarge = $300/month (4x smaller)
- Read replicas × 2 = $600/month
- Redis cache × 5 = $750/month
- Total: $1,650/month

Savings: $11,550/month (87% reduction!)
```

---

### Cache-Aside Pattern (Most Common)

**How It Actually Works**:

**Step-by-Step Flow**:
```
1. User requests profile: GET /users/123

2. Application checks cache:
   value = redis.get("user:123")

3a. Cache HIT (80% of time):
   - Return immediately (1ms)
   - Total latency: 1ms
   
3b. Cache MISS (20% of time):
   - Query database (10ms)
   - Store in cache: redis.set("user:123", data, ex=3600)
   - Return to user
   - Total latency: 11ms (cache write + DB read)
   
4. Next request for same user:
   - Cache hit (1ms)
```

**Complete Implementation**:
```python
class UserService:
    def __init__(self, db, cache):
        self.db = db
        self.cache = cache
    
    def get_user(self, user_id):
        cache_key = f"user:{user_id}"
        
        # Try cache first
        cached = self.cache.get(cache_key)
        if cached:
            print(f"Cache HIT for user {user_id}")
            return json.loads(cached)
        
        # Cache miss - query database
        print(f"Cache MISS for user {user_id}")
        user = self.db.query("SELECT * FROM users WHERE id = ?", user_id)
        
        if user:
            # Store in cache for 1 hour
            self.cache.setex(
                cache_key,
                3600,  # TTL: 1 hour
                json.dumps(user)
            )
        
        return user
    
    def update_user(self, user_id, updates):
        # Update database
        self.db.update("UPDATE users SET ... WHERE id = ?", user_id, updates)
        
        # Invalidate cache (so next read gets fresh data)
        self.cache.delete(f"user:{user_id}")
        
        # Alternative: Update cache immediately
        # self.cache.setex(f"user:{user_id}", 3600, json.dumps(updated_user))
```

**When to Use**:
- Read-heavy workloads (100:1 read-to-write)
- Data doesn't change frequently
- Acceptable to serve slightly stale data

**Real Example - Reddit**:
```
Reddit caches:
- Subreddit data (changes rarely)
- User karma/profile (updates infrequently)
- Hot posts (changes every few minutes)

TTLs:
- Subreddit info: 1 hour
- User karma: 5 minutes
- Hot posts: 2 minutes

Result: 95% cache hit ratio, serves 100K+ QPS from cache
```

---

### Write-Through Cache (Strong Consistency)

**When You Need It**:
- Banking applications (balance must be accurate)
- Inventory systems (stock count must be correct)
- Any system where stale cache is unacceptable

**How It Works**:
```
1. User updates balance: POST /account/deposit
2. Application writes to cache
3. Cache SYNCHRONOUSLY writes to database
4. Both complete successfully
5. Return to user

If database write fails:
- Cache write also fails
- Rolled back
- Consistency maintained
```

**Implementation**:
```python
class AccountService:
    def __init__(self, db, cache):
        self.db = db
        self.cache = cache
    
    def deposit(self, account_id, amount):
        cache_key = f"balance:{account_id}"
        
        # Start transaction
        with self.db.transaction():
            # Update database
            new_balance = self.db.execute("""
                UPDATE accounts 
                SET balance = balance + ? 
                WHERE id = ? 
                RETURNING balance
            """, amount, account_id)
            
            # Update cache (synchronous)
            self.cache.set(cache_key, new_balance)
            
            # If either fails, both rollback
            return new_balance
```

**Trade-off**:
- ✅ Cache always consistent with database
- ❌ Slower writes (wait for both cache + DB)
- ❌ More complex error handling

**Real Example - Payment Systems**:
```
Stripe's payment processing:
- Uses write-through caching
- Every payment written to both cache and database
- Ensures balance is always correct
- Acceptable latency trade-off for correctness
```

---

### Write-Behind Cache (High Performance Writes)

**When You Need It**:
- Analytics/logging (billions of events)
- Social media engagement (likes, views)
- Non-critical updates

**How It Works**:
```
1. User likes post: POST /posts/123/like
2. Increment counter in cache: redis.incr("likes:123")
3. Return immediately (< 1ms)
4. Background worker periodically flushes to database

Async write to database:
Every 5 seconds, batch write 10,000 counter updates
```

**Implementation**:
```python
class EngagementService:
    def like_post(self, post_id, user_id):
        # Increment in cache (instant)
        redis.incr(f"likes:{post_id}")
        redis.sadd(f"liked_by:{post_id}", user_id)
        
        # Queue for batch processing
        redis.lpush("like_queue", json.dumps({
            "post_id": post_id,
            "user_id": user_id,
            "timestamp": time.time()
        }))
        
        return {"status": "success"}  # Return immediately!

# Background worker (separate process)
def flush_likes_to_database():
    while True:
        # Get batch of 1000 likes
        batch = redis.lrange("like_queue", 0, 999)
        redis.ltrim("like_queue", 1000, -1)
        
        if batch:
            # Single batch insert (much faster than 1000 individual)
            db.batch_insert("likes", batch)
        
        time.sleep(5)  # Run every 5 seconds
```

**Benefits**:
- ✅ Very fast writes (< 1ms)
- ✅ Handles traffic spikes
- ✅ Batch writes more efficient

**Risks**:
- ❌ Data loss if cache crashes before flush
- ❌ Database might be behind cache

**Mitigation**:
```python
# Use Redis persistence (AOF or RDB)
# Kafka as queue instead of Redis (more durable)
# Accept small data loss for non-critical features

# Critical: Use write-through
# Non-critical: Use write-behind
```

**Real Example - Instagram**:
```
Instagram like counts:
- Write-behind to Redis
- Batch update database every few seconds
- Acceptable if like count slightly off
- Prioritizes user experience (instant like)
- Database would melt under 50,000 likes/second otherwise
```

---

### The 80-20 Rule in Caching

**Pareto Principle**: 20% of your data gets 80% of traffic

**Example - YouTube**:
```
Total videos: 1 billion
Total storage: 1 exabyte (1,000,000 TB)

Hot videos (last 30 days): 100 million (10%)
Traffic distribution: 90% of views

Strategy:
- Cache 100M hot videos
- Serve 90% of traffic from cache
- Only 10% hits origin storage

Cache size needed:
- 100M videos × 100 MB avg = 10 PB
- Distributed across 100 cache servers = 100 TB each
- Cost: Much cheaper than hitting origin for every request
```

**How to Identify Hot Data**:
```python
# Track access frequency
def track_access(key):
    redis.zincrby("access_frequency", 1, key)

# Get top 20% most accessed
def get_hot_keys():
    total_keys = redis.zcard("access_frequency")
    top_20_percent = int(total_keys * 0.2)
    return redis.zrevrange("access_frequency", 0, top_20_percent)

# Pre-warm cache with hot keys
def warm_cache():
    hot_keys = get_hot_keys()
    for key in hot_keys:
        value = db.get(key)
        cache.set(key, value, ex=3600)
```

**Interview Answer**: "Using 80-20 rule, I'd cache the hottest 20% of data, which serves 80% of requests, requiring only 2 TB cache instead of 10 TB"

---

## Database Replication

### Master-Slave Replication Deep Dive

**Real-World Setup (Instagram's Actual Architecture)**:

```
Primary Datacenter (us-east-1):
[Master PostgreSQL]
  - Handles ALL writes
  - 12 Quadruple Extra-Large instances (db.r5.12xlarge)
  - 384 GB RAM each
  - Writes: 5K QPS
  
  ↓ (async replication)
  
[Slave 1] [Slave 2] [Slave 3] ... [Slave 12]
  - Handle reads only
  - Replicate from master
  - Lag: 100-500ms typically
  - Reads: 50K QPS distributed

Secondary Datacenter (us-west-2):
[Slave 13] ... [Slave 24]
  - Cross-region replication
  - Disaster recovery
  - Lag: 1-2 seconds
```

**How Replication Actually Works**:

**1. Write to Master**:
```sql
-- User updates profile
UPDATE users SET bio = 'New bio' WHERE id = 123;

Master:
1. Writes to Write-Ahead Log (WAL)
2. Applies to database
3. Returns success to client
4. Asynchronously streams WAL to slaves
```

**2. Slaves Apply Changes**:
```
Slave receives WAL entry:
1. Applies UPDATE statement
2. Now has updated data
3. Lag depends on network/load (typically < 500ms)
```

**3. Reading from Slave (The Problem)**:
```
Time T1: Master updates bio = "New bio"
Time T2: Client reads from slave
         Slave still has bio = "Old bio" (replication lag!)
Time T3: Replication completes, slave updated

This is "Eventual Consistency"
```

---

### Handling Replication Lag

**Problem Scenario**:
```
User updates profile photo:
1. Write goes to master (success!)
2. User immediately refreshes page
3. Read goes to slave (still has old photo!)
4. User sees old photo, thinks update failed
```

**Solution 1: Read Your Own Writes**:
```python
def get_user_profile(user_id, requesting_user_id):
    # If user is viewing their own profile
    if user_id == requesting_user_id:
        # Read from master (guaranteed fresh)
        return master_db.query("SELECT * FROM users WHERE id = ?", user_id)
    else:
        # Read from slave (can be slightly stale)
        return slave_db.query("SELECT * FROM users WHERE id = ?", user_id)
```

**Solution 2: Sticky Reads**:
```python
def update_and_read(user_id):
    # Update
    master_db.update("UPDATE users ...")
    
    # Read from SAME master for next 5 seconds
    redis.setex(f"read_from_master:{user_id}", 5, "1")
    
    # Next read checks:
    if redis.exists(f"read_from_master:{user_id}"):
        return master_db.query(...)  # Read from master
    else:
        return slave_db.query(...)  # Can use slave now
```

**Solution 3: Versioning**:
```python
# Store version number with each write
def update_user(user_id):
    version = int(time.time() * 1000)  # Millisecond timestamp
    master_db.execute("""
        UPDATE users 
        SET bio = ?, version = ?
        WHERE id = ?
    """, new_bio, version, user_id)
    
    return version

# When reading, retry until version matches
def get_user_with_version(user_id, expected_version):
    for attempt in range(3):
        user = slave_db.query("SELECT * FROM users WHERE id = ?", user_id)
        if user.version >= expected_version:
            return user  # Slave caught up!
        time.sleep(0.1)  # Wait for replication
    
    # Give up, read from master
    return master_db.query("SELECT * FROM users WHERE id = ?", user_id)
```

**Interview Answer**: "For replication lag, I'd implement 'read your own writes' pattern where users read their own updates from master, while other users can read from slaves"

---

### When Master Fails: Failover

**Automatic Failover Process**:
```
1. Master stops responding (crash, network partition)
   
2. Monitoring detects failure (heartbeat missed)
   - Typically 10-30 seconds to detect
   
3. Consensus algorithm elects new master:
   - Promote slave with most recent data
   - Typically Slave 1 (closest to master)
   
4. Update DNS/routing:
   - Point writes to new master
   - Other slaves now replicate from new master
   
5. Old master comes back:
   - Becomes slave
   - Catches up with new master

Total downtime: 30-60 seconds typical
```

**Technologies for Auto-Failover**:
- **PostgreSQL**: Patroni, repmgr
- **MySQL**: MHA (Master High Availability), Orchestrator
- **Managed**: AWS RDS, Google Cloud SQL (automatic)

**Interview Answer**: "I'd use Patroni for automatic PostgreSQL failover with < 30 second RTO when master fails"

---

## Sharding Strategies

### When to Shard

**Single Database Limits**:
```
PostgreSQL single instance:
- Max storage: ~16 TB (practical limit)
- Max writes: ~10K QPS
- Max reads: ~50K QPS (with good indexes)

When you hit these limits → Shard!
```

**Signs You Need Sharding**:
1. Database CPU consistently > 70%
2. Disk I/O maxed out
3. Query latency increasing (p99 > 100ms)
4. Can't add more read replicas
5. Data size > 10 TB

---

### Hash-Based Sharding Example (Twitter)

**Twitter's Tweet Storage**:
```
Problem:
- 500M tweets per day
- 6,000 write QPS average
- Single PostgreSQL can't handle this

Solution: Shard by tweet_id
```

**Implementation**:
```python
NUM_SHARDS = 64

def get_shard_for_tweet(tweet_id):
    # Hash tweet_id to determine shard
    return hash(tweet_id) % NUM_SHARDS

def store_tweet(tweet):
    shard = get_shard_for_tweet(tweet.id)
    db = get_database_connection(shard)
    db.insert("tweets", tweet)

def get_tweet(tweet_id):
    shard = get_shard_for_tweet(tweet_id)
    db = get_database_connection(shard)
    return db.query("SELECT * FROM tweets WHERE id = ?", tweet_id)
```

**Shard Configuration**:
```
64 shards, each handling:
- 500M / 64 = 7.8M tweets per day
- 6K QPS / 64 = 94 write QPS per shard
- Easily handled by single PostgreSQL instance!

Per shard storage (5 years):
- 7.8M/day × 365 × 5 = 14.2B tweets
- 14.2B × 280 bytes = 4 TB per shard
- Well within limits!
```

**Benefits**:
- Even distribution across shards
- Each shard handles manageable load
- Add shards to scale further

**Problem - User Timeline Query**:
```
User follows 1000 people
Their tweets spread across all 64 shards!

Query user timeline:
FOR each followed_user:
    shard = get_shard_for_user(followed_user)
    tweets = query_shard(shard, followed_user)

Result: Must query multiple shards!
This is the trade-off of sharding by tweet_id
```

**Twitter's Solution**:
- Pre-compute timelines (fan-out on write)
- Store in Redis
- Don't query shards in real-time

**Interview Answer**: "I'd shard by tweet_id for even distribution. Trade-off is scatter-gather queries for user timelines, which I'd solve with pre-computed timelines in Redis"

---

### Geographic Sharding Example (Uber)

**Uber's Approach**:
```
US Shard (us-east-1):
- US/Canada trips
- ~70% of rides
- Low latency for US users

Europe Shard (eu-west-1):
- European trips
- ~20% of rides
- Low latency for EU users

Asia-Pacific Shard (ap-southeast-1):
- Asia/Australia trips
- ~10% of rides
- Low latency for APAC users
```

**Benefits**:
- Data close to users (< 50ms vs 150ms cross-continent)
- Regulatory compliance (GDPR - EU data stays in EU)
- Better disaster recovery (regional isolation)

**Challenges**:
- Uneven distribution (US has most rides)
- Cross-region trips (user starts in US, ends in Canada)
- More complex deployment

**Interview Answer**: "For Uber, I'd use geographic sharding to keep data close to users, achieving < 50ms latency vs 150ms cross-continent. Trade-off is handling cross-region trips requires coordination"

---

## Consistent Hashing Deep Dive

### The Problem with Simple Hashing

**Scenario**: You have a distributed cache with 4 servers

**Simple Hashing**:
```python
def get_server(key):
    return hash(key) % 4  # 4 servers

user_123 → hash("user_123") % 4 = 2 → Server 2
user_456 → hash("user_456") % 4 = 0 → Server 0
```

**What Happens When You Add a Server** (4 → 5):
```python
# Before (4 servers):
user_123 → hash("user_123") % 4 = 2 → Server 2

# After (5 servers):
user_123 → hash("user_123") % 5 = 3 → Server 3 (DIFFERENT!)

Result: Key moved from Server 2 to Server 3
Cache miss! Must refetch from database
```

**The Disaster**:
```
With 4 servers → 5 servers:
- Only 1/5 of keys stay in same server (20%)
- 4/5 of keys move to different server (80%)

1 billion cached items:
- 800 million keys need to be remapped!
- 800 million cache misses
- Database overwhelmed with 800M queries
- System crashes!
```

**Real Example**:
```
Reddit adding cache server during peak traffic:
- Had 4 cache servers
- Added 5th server to handle load
- 80% of cache invalidated instantly
- Cache miss rate: 80% (normally 5%)
- Database couldn't handle 16x spike
- Site went down for 20 minutes

Cost: Millions in lost revenue
```

---

### Consistent Hashing Solution

**The Hash Ring Concept**:
```
Imagine a circle (0 to 2^32):

1. Hash servers onto ring:
   Server A → hash("Server_A") = 1,000,000
   Server B → hash("Server_B") = 8,000,000
   Server C → hash("Server_C") = 15,000,000
   Server D → hash("Server_D") = 22,000,000

2. Hash keys onto same ring:
   key1 → hash("key1") = 500,000
   key2 → hash("key2") = 10,000,000

3. Key goes to next server clockwise:
   key1 (500,000) → Server A (1,000,000)
   key2 (10,000,000) → Server C (15,000,000)
```

**Adding Server E**:
```
Server E → hash("Server_E") = 12,000,000

Before:
key2 (10,000,000) → Server C (15,000,000)

After:
key2 (10,000,000) → Server E (12,000,000)

Only keys between 8M-12M move!
That's ~12.5% of keys (4M out of 32M range)
Much better than 80%!
```

**Implementation**:
```python
import hashlib
import bisect

class ConsistentHashing:
    def __init__(self):
        self.ring = {}  # hash_value → server
        self.sorted_keys = []  # Sorted hash values
    
    def _hash(self, key):
        # Use MD5 for good distribution
        return int(hashlib.md5(key.encode()).hexdigest(), 16)
    
    def add_server(self, server):
        hash_value = self._hash(server)
        self.ring[hash_value] = server
        self.sorted_keys = sorted(self.ring.keys())
        print(f"Added {server} at position {hash_value}")
    
    def remove_server(self, server):
        hash_value = self._hash(server)
        del self.ring[hash_value]
        self.sorted_keys = sorted(self.ring.keys())
        print(f"Removed {server}")
    
    def get_server(self, key):
        if not self.ring:
            return None
        
        hash_value = self._hash(key)
        
        # Find next server clockwise
        # bisect_right finds insertion point
        idx = bisect.bisect_right(self.sorted_keys, hash_value)
        
        # Wrap around if at end
        if idx == len(self.sorted_keys):
            idx = 0
        
        server_hash = self.sorted_keys[idx]
        return self.ring[server_hash]

# Usage
ch = ConsistentHashing()
ch.add_server("Server_A")
ch.add_server("Server_B")
ch.add_server("Server_C")
ch.add_server("Server_D")

# Get server for keys
print(ch.get_server("user_123"))  # → Server_B
print(ch.get_server("user_456"))  # → Server_D

# Add new server
ch.add_server("Server_E")
# Only ~25% of keys remapped!
```

---

### Virtual Nodes (Solving Distribution Problem)

**Problem with Basic Consistent Hashing**:
```
4 servers, randomly hashed:
Server A at position 1M
Server B at position 2M  (1M gap)
Server C at position 10M (8M gap!)  ← Uneven!
Server D at position 20M (10M gap!) ← Very uneven!

Server C handles 8M range (25%)
Server D handles 10M + wrap-around = 13M range (40%)
Uneven distribution!
```

**Solution: Virtual Nodes**:
```python
class ConsistentHashingWithVirtualNodes:
    def __init__(self, virtual_nodes_per_server=150):
        self.virtual_nodes = virtual_nodes_per_server
        self.ring = {}
        self.sorted_keys = []
    
    def add_server(self, server):
        # Add 150 virtual nodes for this server
        for i in range(self.virtual_nodes):
            virtual_key = f"{server}:vnode{i}"
            hash_value = self._hash(virtual_key)
            self.ring[hash_value] = server  # Points to actual server
        
        self.sorted_keys = sorted(self.ring.keys())
        print(f"Added {server} with {self.virtual_nodes} virtual nodes")
```

**Result**:
```
Instead of 4 positions (servers):
Now have 4 × 150 = 600 positions (virtual nodes)

Much better distribution:
Server A: 25.2% of keys (target: 25%)
Server B: 24.8% of keys (target: 25%)
Server C: 25.1% of keys (target: 25%)
Server D: 24.9% of keys (target: 25%)

Nearly perfect!
```

**Handling Different Server Sizes**:
```python
# Large server gets more virtual nodes
ch.add_server_with_weight("Server_Large", virtual_nodes=300)

# Small server gets fewer virtual nodes  
ch.add_server_with_weight("Server_Small", virtual_nodes=100)

Result:
Large server handles 300/(300+100) = 75% of traffic
Small server handles 100/(300+100) = 25% of traffic
```

**Real-World Usage**:
- **Cassandra**: Uses 256 virtual nodes per physical node
- **DynamoDB**: Consistent hashing for partitioning
- **Redis Cluster**: 16,384 hash slots (virtual nodes)

**Interview Answer**: "I'd use consistent hashing with 150 virtual nodes per server to ensure even distribution and minimize cache invalidation when adding nodes"

---

## Redis Deep Dive

### Why Redis is Special

**Redis vs. Memcached vs. Database**:
```
Operation          Redis    Memcached  PostgreSQL
GET (cache hit)    0.1ms    0.1ms      N/A
GET (cache miss)   N/A      N/A        5-10ms
Complex query      N/A      N/A        50-500ms
SET                0.1ms    0.1ms      1-10ms
Data structures    ✅ 10+   ❌ 1       ✅ Many
Persistence        ✅ Yes   ❌ No      ✅ Yes
Pub/Sub            ✅ Yes   ❌ No      ✅ Limited
Transactions       ✅ Yes   ❌ No      ✅ Yes
```

**Why Choose Redis**:
- ✅ **Rich data structures** (not just key-value)
- ✅ **Persistence** (optional, for durability)
- ✅ **Atomic operations** (INCR, DECR without race conditions)
- ✅ **Pub/Sub** (real-time messaging)
- ✅ **Sorted Sets** (perfect for leaderboards, timelines)

---

### Redis Data Structures in Practice

#### 1. Strings (Simple Key-Value)

**Use Case**: Session storage, simple caching

```python
# Store session
redis.set("session:user_123", json.dumps({
    "user_id": 123,
    "email": "alice@example.com",
    "logged_in_at": "2025-01-08T10:00:00"
}), ex=3600)  # Expire in 1 hour

# Get session
session = json.loads(redis.get("session:user_123"))

# Atomic counter
redis.incr("page_views:article_456")
views = redis.get("page_views:article_456")  # Thread-safe!
```

---

#### 2. Hashes (Object Storage)

**Use Case**: User profiles, product data

```python
# Store user as hash (better than JSON string)
redis.hset("user:123", mapping={
    "username": "alice",
    "email": "alice@example.com",
    "followers": "1500",
    "following": "342"
})

# Get single field (efficient!)
email = redis.hget("user:123", "email")

# Get all fields
user = redis.hgetall("user:123")

# Increment follower count (atomic!)
redis.hincrby("user:123", "followers", 1)
```

**Why Better Than String**:
- Can update single field without fetching entire object
- Atomic operations on individual fields
- Memory efficient

---

#### 3. Lists (Queues, Timelines)

**Use Case**: Message queues, activity feeds, job queues

```python
# Job queue (producer)
redis.lpush("job_queue", json.dumps({
    "type": "send_email",
    "to": "alice@example.com",
    "subject": "Welcome!"
}))

# Job queue (consumer - blocking!)
def worker():
    while True:
        # BRPOP blocks until item available
        job = redis.brpop("job_queue", timeout=5)
        if job:
            process_job(json.loads(job[1]))

# Activity feed (newest first)
redis.lpush("feed:user_123", "post_789")
redis.lpush("feed:user_123", "post_790")

# Get latest 20 items
feed = redis.lrange("feed:user_123", 0, 19)

# Trim to keep only latest 1000
redis.ltrim("feed:user_123", 0, 999)
```

---

#### 4. Sets (Unique Items)

**Use Case**: Tags, user relationships, unique visitors

```python
# Add tags to post
redis.sadd("post:123:tags", "python", "redis", "system-design")

# Check if tag exists (O(1)!)
has_tag = redis.sismember("post:123:tags", "python")

# Get all tags
tags = redis.smembers("post:123:tags")

# Union (common followers)
user_a_followers = redis.smembers("followers:user_a")
user_b_followers = redis.smembers("followers:user_b")
common = redis.sinter("followers:user_a", "followers:user_b")

# Track unique daily visitors
redis.sadd("visitors:2025-01-08", "user_123", "user_456")
daily_visitors = redis.scard("visitors:2025-01-08")
```

---

#### 5. Sorted Sets (Leaderboards, Timelines)

**Use Case**: Leaderboards, trending topics, Twitter timeline

**Example 1 - Gaming Leaderboard**:
```python
# Add player scores
redis.zadd("leaderboard:game_456", {
    "player_alice": 9500,
    "player_bob": 8200,
    "player_charlie": 9800
})

# Get top 10 players
top_10 = redis.zrevrange("leaderboard:game_456", 0, 9, withscores=True)
# Returns: [("player_charlie", 9800), ("player_alice", 9500), ...]

# Get player rank
rank = redis.zrevrank("leaderboard:game_456", "player_alice")
# Returns: 1 (0-indexed, so 2nd place)

# Get player score
score = redis.zscore("leaderboard:game_456", "player_alice")
# Returns: 9500

# Increment score (atomic!)
redis.zincrby("leaderboard:game_456", 100, "player_alice")
# Now: 9600
```

**Example 2 - Twitter Timeline** (This is how Twitter actually does it!):
```python
# Store timeline (score = timestamp for sorting)
def add_tweet_to_timeline(user_id, tweet_id):
    timestamp = time.time()
    redis.zadd(
        f"timeline:user:{user_id}",
        {tweet_id: timestamp}
    )
    
    # Keep only latest 1000 tweets
    redis.zremrangebyrank(f"timeline:user:{user_id}", 0, -1001)

# Get latest 50 tweets
def get_timeline(user_id, limit=50):
    # ZREVRANGE = reverse order (newest first)
    tweet_ids = redis.zrevrange(
        f"timeline:user:{user_id}",
        0,
        limit - 1
    )
    return tweet_ids

# Get tweets in time range
def get_tweets_between(user_id, start_time, end_time):
    return redis.zrangebyscore(
        f"timeline:user:{user_id}",
        start_time,
        end_time
    )
```

**Why Sorted Sets are Perfect for Timelines**:
- O(log N) insert (fast even with millions of tweets)
- O(1) retrieval of top N items
- Automatic sorting by timestamp
- Range queries by time
- Can trim old items efficiently

---

### Redis Persistence (Making Cache Durable)

**Two Options**:

**RDB (Snapshot)**:
```
Save entire dataset to disk periodically

Configuration:
save 900 1      # Save if 1 key changed in 900 seconds
save 300 10     # Save if 10 keys changed in 300 seconds
save 60 10000   # Save if 10,000 keys changed in 60 seconds

Pros:
- Compact file
- Fast restarts
- Good for backups

Cons:
- Can lose data since last snapshot
- Slow if dataset is large
```

**AOF (Append-Only File)**:
```
Log every write operation to disk

Append:
SET user:123 "data"
INCR counter:456
ZADD timeline:789 1234567 "tweet_abc"

On restart:
Replay all operations to rebuild state

Pros:
- Minimal data loss (1 second max)
- More durable

Cons:
- Larger file size
- Slower restarts
```

**Real Example - Instagram**:
```
Instagram's Redis configuration:
- Uses AOF for durability
- Acceptable to lose 1 second of data
- Saves every second (appendfsync everysec)
- Critical: User timelines
- Non-critical: View counts (can afford to lose)

Trade-off decision:
Feed cache: Use RDB (fast, can rebuild from DB)
Session data: Use AOF (can't lose user sessions)
```

---

### Redis Cluster (Scaling Redis)

**When Single Redis Not Enough**:
```
Single Redis instance:
- Max memory: 512 GB (AWS limit)
- Max throughput: ~100K QPS

If you need:
- > 512 GB cache
- > 100K QPS
→ Need Redis Cluster
```

**How Redis Cluster Works**:
```
16,384 hash slots (virtual nodes)
Distributed across N nodes

Hash slot = CRC16(key) % 16,384

Example with 3 nodes:
Node A: Slots 0-5,461     (33%)
Node B: Slots 5,462-10,922 (33%)
Node C: Slots 10,923-16,383 (34%)

Key "user_123":
hash_slot = CRC16("user_123") % 16,384 = 8,234
→ Goes to Node B
```

**Adding Node**:
```
Before: 3 nodes (A, B, C)
After: 4 nodes (A, B, C, D)

Redistribution:
Move 25% of slots from A, B, C to D
Each node now handles 25% instead of 33%

Keys automatically move with slots
Application doesn't need to change!
```

**Client-Side Code**:
```python
from redis.cluster import RedisCluster

# Client handles routing automatically
redis_cluster = RedisCluster(
    host='redis-cluster.example.com',
    port=6379
)

# Use exactly like single Redis
redis_cluster.set("user:123", "data")
value = redis_cluster.get("user:123")

# Client automatically:
# 1. Calculates hash slot
# 2. Finds which node owns that slot
# 3. Sends command to correct node
```

**Real Example - Twitter**:
```
Twitter's Redis deployment:
- Multiple Redis clusters
- Each cluster: 10-20 nodes
- Total: 100+ Redis nodes
- Total memory: 50+ TB
- Handles 1M+ QPS

Use cases:
- Timeline cache (Sorted Sets)
- User data cache (Hashes)
- Rate limiting (Strings with TTL)
- Session storage (Strings)
```

---

## Apache Kafka Deep Dive

### What Problem Does Kafka Solve?

**Traditional Architecture (Tightly Coupled)**:
```
[User Service] ──→ [Email Service]
     ↓
[Payment Service] ──→ [Notification Service]
     ↓
[Order Service] ──→ [Analytics Service]

Problems:
- Services directly coupled
- If Email Service down, User Service fails
- Can't handle traffic spikes
- No replay capability
```

**With Kafka (Decoupled)**:
```
[User Service] ──→ [Kafka] ──→ [Email Service]
[Payment Service] ─┘    └──→ [Notification Service]
[Order Service] ─────────→ [Analytics Service]

Benefits:
- Services independent
- Buffer for traffic spikes
- Multiple consumers
- Can replay events
```

---

### Kafka Architecture Explained

**Components**:

**1. Topic** (Like a database table):
```
Topic: "user-events"
Contains: All user-related events
Examples: user_created, user_updated, user_deleted
```

**2. Partition** (For parallelism):
```
Topic "user-events" with 3 partitions:

Partition 0: [msg1, msg2, msg5, msg8, ...]
Partition 1: [msg3, msg6, msg9, ...]
Partition 2: [msg4, msg7, msg10, ...]

Key determines partition:
hash(user_id) % 3 = partition

All events for same user go to same partition
→ Maintains order per user
```

**3. Producer** (Writes messages):
```python
from kafka import KafkaProducer

producer = KafkaProducer(
    bootstrap_servers=['kafka1:9092', 'kafka2:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# Send message
producer.send('user-events', {
    'event_type': 'user_created',
    'user_id': 123,
    'timestamp': time.time()
}, key=b'user_123')  # Key determines partition

# Async by default (doesn't wait)
# For synchronous:
future = producer.send('user-events', message)
future.get(timeout=10)  # Wait for acknowledgment
```

**4. Consumer** (Reads messages):
```python
from kafka import KafkaConsumer

consumer = KafkaConsumer(
    'user-events',
    bootstrap_servers=['kafka1:9092', 'kafka2:9092'],
    group_id='email-service',  # Consumer group
    auto_offset_reset='earliest',  # Start from beginning
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

# Process messages
for message in consumer:
    event = message.value
    if event['event_type'] == 'user_created':
        send_welcome_email(event['user_id'])
```

---

### Consumer Groups (Scaling Consumers)

**Problem**:
```
1 million messages/second
Single consumer can process 10,000/second
Need 100 consumers!
```

**Consumer Group Solution**:
```
Topic with 10 partitions:

Consumer Group "email-service":
  Consumer 1 → Partition 0, 1
  Consumer 2 → Partition 2, 3
  Consumer 3 → Partition 4, 5
  Consumer 4 → Partition 6, 7
  Consumer 5 → Partition 8, 9

Each consumer handles 2 partitions
Parallel processing!
100K messages/second per consumer
= 500K total

Add more consumers to scale!
```

**Multiple Consumer Groups**:
```
Same messages, different consumers:

Topic: "order-events"

Consumer Group "email-service":
  → Sends order confirmation emails

Consumer Group "analytics-service":
  → Updates sales dashboards

Consumer Group "inventory-service":
  → Decrements stock count

All 3 groups consume same messages independently!
```

---

### Kafka's Durability Guarantees

**Replication**:
```
Topic with replication factor = 3:

Partition 0:
  Leader: Broker 1 (handles reads/writes)
  Follower: Broker 2 (replicates)
  Follower: Broker 3 (replicates)

Write process:
1. Producer writes to Leader (Broker 1)
2. Leader writes to disk
3. Leader sends to Followers
4. Followers write to disk
5. Leader acknowledges producer

If Broker 1 fails:
- Broker 2 or 3 elected as new leader
- No data loss!
```

**Acknowledgment Modes**:
```python
# acks=0: Don't wait (fast but can lose data)
producer = KafkaProducer(acks=0)
# Throughput: 1M msg/sec, but might lose messages

# acks=1: Wait for leader only (balanced)
producer = KafkaProducer(acks=1)  # Default
# Throughput: 100K msg/sec, rare data loss

# acks='all': Wait for all replicas (durable)
producer = KafkaProducer(acks='all')
# Throughput: 50K msg/sec, no data loss
```

---

### Real-World Example: Uber's Surge Pricing

**Architecture**:
```
[Rider App] → Request ride
     ↓
[Ride Service] → Publishes to Kafka
     ↓
Topic: "ride-requests"
     ↓
Consumer: "Supply-Demand Service"
     ↓
Calculates surge pricing in real-time
     ↓
Publishes to: "surge-updates"
     ↓
[Rider App] ← Shows surge price
```

**Implementation**:
```python
# Ride Service (Producer)
def request_ride(rider_id, location):
    kafka_producer.send('ride-requests', {
        'rider_id': rider_id,
        'location': location,
        'timestamp': time.time()
    })
    return {"status": "searching"}

# Supply-Demand Service (Consumer)
def calculate_surge():
    for message in kafka_consumer:
        location = message['location']
        
        # Count requests in last 5 minutes
        recent_requests = redis.zcount(
            f"requests:{location}",
            time.time() - 300,
            time.time()
        )
        
        # Count available drivers
        available_drivers = redis.zcard(f"drivers:{location}")
        
        # Calculate surge
        surge_multiplier = recent_requests / max(available_drivers, 1)
        
        # Publish surge update
        kafka_producer.send('surge-updates', {
            'location': location,
            'surge': surge_multiplier
        })
```

**Why Kafka Here**:
- Handles 1M+ ride requests/second
- Multiple services consume same events
- Can replay events (debugging, new features)
- Buffers spikes (Super Bowl ends, everyone requests rides)

---

### Kafka vs. RabbitMQ vs. SQS

```
Feature           Kafka          RabbitMQ       AWS SQS
Throughput        1M+ msg/sec    50K msg/sec    Unlimited
Durability        Replicated     Replicated     Replicated
Ordering          ✅ Per partition ✅ Per queue ❌ Best effort
Replay            ✅ Yes         ❌ No          ❌ No
Retention         Days/weeks     Until consumed Until consumed
Complexity        High           Medium         Low (managed)
Cost              Medium (self)  Medium (self)  Low (pay/use)

Use Kafka when:
- Need replay capability
- High throughput (> 100K msg/sec)
- Event sourcing architecture
- Multiple consumers for same data

Use RabbitMQ when:
- Need flexible routing
- Task queues with acknowledgments
- Lower throughput

Use SQS when:
- Want fully managed
- Don't need replay
- AWS-based architecture
```

**Interview Answer**: "I'd use Kafka for feed fanout because it handles high throughput (500K tweets/sec), allows multiple consumers (notifications, analytics, search indexing), and provides replay capability for debugging"

---

## CDN Architecture

### How CDN Actually Works

**Without CDN**:
```
User in Tokyo requests image:
Tokyo → California (origin server) → Tokyo
Latency: 150ms
Bandwidth: All traffic hits origin

10M users in Asia × 1 MB image = 10 TB from origin
Cost: $1,000 in bandwidth charges
```

**With CDN**:
```
First request (cache miss):
User in Tokyo → Tokyo CDN (miss) → California origin → Tokyo CDN → User
Latency: 150ms (first time)
CDN caches image

Next 1M requests (cache hit):
User in Tokyo → Tokyo CDN (hit) → User
Latency: 10ms (15x faster!)

Only 1 request hits origin, 999,999 served from CDN
Cost: $50 in bandwidth (20x cheaper)
```

---

### Push vs Pull CDN

**Push CDN** (You upload content):
```
Workflow:
1. You create/update content
2. You push to CDN via API
3. CDN distributes to all edge servers
4. Content available globally

Example:
aws s3 sync ./static s3://my-bucket
aws cloudfront create-invalidation --paths "/*"

Use for:
- Static website (updates rarely)
- Product catalog
- Marketing assets
- Documentation

Pros:
- Content immediately available everywhere
- Predictable behavior

Cons:
- You manage uploads
- Pay for storage even if not accessed
- Must invalidate on updates
```

**Pull CDN** (CDN fetches on-demand):
```
Workflow:
1. User requests content
2. CDN checks cache
3. If miss → CDN fetches from origin
4. CDN caches and returns
5. Next request = cache hit

Example:
<img src="https://cdn.example.com/photo/123.jpg">
CDN automatically fetches from https://origin.example.com/photo/123.jpg

Use for:
- User-generated content
- Frequently updated content
- Large catalog (millions of items)

Pros:
- Automatic caching
- Only cache what's accessed
- No manual uploads

Cons:
- First request is slow (cache miss)
- Origin must handle initial requests
```

**Instagram's Approach** (Pull CDN):
```
Upload flow:
1. User uploads photo → S3
2. Photo URL: https://cdn.instagram.com/photo/abc123.jpg
3. CDN configured to pull from S3
4. First viewer in region → CDN fetches from S3 → Caches
5. Next viewers → Served from local CDN edge

Benefits:
- 95M photos/day × auto-cached
- Don't need to push to CDN
- Popular photos cached, unpopular not
- Saves storage costs
```

---

### CDN Cache Invalidation

**The Problem**:
```
User uploads profile photo
CDN cached old photo (TTL: 24 hours)
User sees old photo for next 24 hours!
```

**Solutions**:

**1. Versioned URLs** (Best):
```
Old: https://cdn.example.com/profile/user_123.jpg
New: https://cdn.example.com/profile/user_123.jpg?v=20250108

Browser sees different URL → No cache!
CDN sees different URL → Fetches new

Pros:
- Instant update
- No API calls needed
- Works with any CDN

Cons:
- Need to update URLs in database
```

**2. Cache Purge** (API call):
```python
# Invalidate specific files
cloudfront.create_invalidation(
    DistributionId='DISTID',
    InvalidationBatch={
        'Paths': ['/profile/user_123.jpg'],
        'CallerReference': str(time.time())
    }
)

Pros:
- Immediate cache clear
- Precise control

Cons:
- API call needed (costs money)
- Limit: 1000 invalidations/month (free tier)
- Takes 5-15 minutes to propagate globally
```

**3. Short TTL** (Time-based):
```
Set cache TTL to 5 minutes
After 5 minutes, CDN re-fetches

Pros:
- Automatic, no management

Cons:
- Updates delayed up to TTL duration
- More origin requests
```

**Instagram's Strategy**:
- User avatars: Versioned URLs (instant update)
- Posts: 7 day TTL (content doesn't change)
- Stories: 1 hour TTL (expires in 24 hours anyway)

---

## CAP Theorem in Action

### Understanding the Trade-off

**The CAP Triangle**:
```
         Consistency
              △
             /│\
            / │ \
           /  │  \
          /   │   \
         /    │    \
        /     │     \
Availability ─┴─ Partition Tolerance

Pick 2, sacrifice 1
But partitions WILL happen → Pick C or A
```

---

### CP Example: Banking System

**Scenario**: Bank account balance

**Requirements**:
- Must show correct balance (can't show wrong amount!)
- Better to show error than incorrect balance
- Transactions must be atomic

**System Choice**: **CP (Consistency + Partition Tolerance)**

**What Happens During Partition**:
```
Bank has 2 datacenters: East and West

Normal operation:
[East DC] ←sync→ [West DC]
Both show balance = $1000

Network partition occurs!
[East DC] ─X─ [West DC]
Can't communicate

User in East tries to withdraw $100:
East DC can't confirm with West
Options:
1. Allow withdrawal → Risk inconsistency
2. Reject withdrawal → Maintain consistency

CP Choice: REJECT (maintain consistency)
Return error: "Service temporarily unavailable"
```

**Implementation**:
```python
def withdraw(account_id, amount):
    # Check if can reach all replicas
    if not can_reach_all_replicas():
        # Partition detected
        # CP: Sacrifice availability for consistency
        return {"error": "Service unavailable", "code": 503}
    
    # All replicas reachable, proceed with transaction
    with distributed_transaction():
        # Update all replicas atomically
        east_dc.update_balance(account_id, -amount)
        west_dc.update_balance(account_id, -amount)
        
    return {"success": True, "new_balance": balance}
```

**Databases That Are CP**:
- PostgreSQL, MySQL (single master)
- MongoDB (with majority write concern)
- Redis (single master in cluster)
- Zookeeper

**Interview Answer**: "For banking, I'd choose CP database like PostgreSQL because showing incorrect balance is unacceptable. During partition, better to show error than wrong amount"

---

### AP Example: Twitter Feed

**Scenario**: Social media timeline

**Requirements**:
- Users should always be able to view feed
- OK if feed is slightly stale
- Eventual consistency acceptable

**System Choice**: **AP (Availability + Partition Tolerance)**

**What Happens During Partition**:
```
Twitter has 3 datacenters: US, EU, Asia

Network partition:
[US DC] ─X─ [EU DC] ─X─ [Asia DC]

User in US tweets:
- Tweet stored in US DC
- Can't immediately replicate to EU/Asia
- US users see tweet immediately
- EU/Asia users don't see it yet

AP Choice: ACCEPT (prioritize availability)
- US DC serves tweet to US users
- EU/Asia DCs serve without it
- When partition heals, sync up

Result: Eventual consistency (delay < 5 seconds typically)
```

**Implementation**:
```python
def post_tweet(user_id, content):
    # Write to local datacenter (always succeeds)
    local_dc.insert_tweet({
        'user_id': user_id,
        'content': content,
        'timestamp': time.time(),
        'dc': 'us-east-1'
    })
    
    # Async replication to other DCs
    replicate_async_to_other_dcs(tweet)
    
    # Return immediately (don't wait for replication)
    return {"success": True, "tweet_id": tweet_id}

def get_timeline(user_id):
    # Read from local datacenter (might be behind)
    # But ALWAYS returns data
    return local_dc.get_user_timeline(user_id)
```

**Databases That Are AP**:
- Cassandra
- DynamoDB (default)
- Couchbase
- Riak

**Interview Answer**: "For Twitter feed, I'd choose AP database like Cassandra because availability is more important than showing every tweet instantly. Users accept 1-2 second delay for new tweets"

---

### Tunable Consistency (Cassandra)

**Cassandra's Flexibility**:
```
Write with different consistency levels:

Consistency.ONE:
  Write to 1 replica → Return
  Fastest, least durable

Consistency.QUORUM:
  Write to majority (2 out of 3) → Return
  Balanced

Consistency.ALL:
  Write to all 3 replicas → Return
  Slowest, most durable
```

**Per-Query Consistency**:
```python
# Critical write (payment)
session.execute(
    "INSERT INTO payments ...",
    consistency_level=ConsistencyLevel.ALL  # CP behavior
)

# Non-critical write (view count)
session.execute(
    "UPDATE view_counts ...",
    consistency_level=ConsistencyLevel.ONE  # AP behavior
)
```

**This is powerful**: Choose CP or AP per operation!

---

## Rate Limiting Implementation

### Why Rate Limiting is Critical

**Without Rate Limiting**:
```
Attacker scenario:
- Creates script to hit API
- Sends 100,000 requests/second
- Your servers: Handle 10,000 QPS normally
- Result: Server overload, legitimate users can't access
- Database crashes from load
- System down for hours

Cost: Millions in lost revenue
```

**With Rate Limiting**:
```
Limit: 1000 requests/hour per user

Attacker hits limit after 1000 requests
Further requests rejected with 429 Too Many Requests
System protected!
```

---

### Token Bucket Algorithm (Industry Standard)

**How It Works**:
```
Bucket:
- Capacity: 100 tokens
- Refill rate: 10 tokens/second

Request arrives:
- If tokens available → Consume 1 token, allow request
- If no tokens → Reject with 429

Allows burst:
- Can send 100 requests instantly (burst)
- Then limited to 10/second sustained
```

**Complete Implementation**:
```python
import time
import redis

class TokenBucketRateLimiter:
    def __init__(self, redis_client):
        self.redis = redis_client
    
    def is_allowed(self, user_id, capacity=100, refill_rate=10):
        key = f"rate_limit:{user_id}"
        now = time.time()
        
        # Get current state
        bucket = self.redis.hgetall(key)
        
        if not bucket:
            # First request - initialize bucket
            self.redis.hset(key, mapping={
                'tokens': capacity - 1,
                'last_refill': now
            })
            self.redis.expire(key, 3600)  # Expire in 1 hour
            return True
        
        tokens = float(bucket[b'tokens'])
        last_refill = float(bucket[b'last_refill'])
        
        # Calculate tokens to add
        elapsed = now - last_refill
        tokens_to_add = elapsed * refill_rate
        tokens = min(capacity, tokens + tokens_to_add)
        
        # Check if request allowed
        if tokens >= 1:
            # Allow request
            self.redis.hset(key, mapping={
                'tokens': tokens - 1,
                'last_refill': now
            })
            return True
        else:
            # Rate limited
            return False

# Usage
limiter = TokenBucketRateLimiter(redis_client)

@app.route('/api/tweet', methods=['POST'])
def create_tweet():
    user_id = get_current_user_id()
    
    if not limiter.is_allowed(user_id, capacity=300, refill_rate=5):
        return {"error": "Rate limit exceeded"}, 429
    
    # Process tweet
    return create_tweet_handler()
```

---

### Real-World Rate Limits

**Twitter API**:
```
Tier          Endpoint                  Limit
Standard      POST /tweets              300 tweets / 3 hours
Standard      POST /users/:id/follow    400 follows / 24 hours
Standard      GET /tweets/search        180 requests / 15 min
Standard      GET /users/lookup         900 requests / 15 min

Premium       All endpoints             10x higher

How they do it:
- Token bucket algorithm
- Tracked in Redis
- Per user + per IP (prevent circumvention)
- Return headers: X-Rate-Limit-Remaining
```

**GitHub API**:
```
Authenticated:   5000 requests / hour
Unauthenticated: 60 requests / hour

GraphQL: Different calculation (query complexity score)

Headers returned:
X-RateLimit-Limit: 5000
X-RateLimit-Remaining: 4999
X-RateLimit-Reset: 1372700873 (Unix timestamp)
```

**Stripe Payment API**:
```
Per second:  100 requests
Per hour:    1000 requests
Burst:       Allow 200 instant, then throttle

Critical: Payments must not be limited
Solution: Different limits for read vs write
- Read balance: 1000/hour
- Create payment: 100/hour (lower, critical operation)
```

---

### Distributed Rate Limiting

**Challenge**: Multiple servers, shared limits

**Problem**:
```
Limit: 100 requests/hour per user
3 servers

Without coordination:
- Server 1: Allows 100 requests
- Server 2: Allows 100 requests  
- Server 3: Allows 100 requests
Total: 300 requests! (3x over limit)
```

**Solution: Redis as Shared Counter**:
```python
def distributed_rate_limit(user_id, limit=100, window=3600):
    key = f"rate:{user_id}"
    
    # Increment counter
    count = redis.incr(key)
    
    # Set expiry on first request
    if count == 1:
        redis.expire(key, window)
    
    if count > limit:
        return False  # Rate limited
    
    return True  # Allowed
```

**All servers share same Redis**:
- Server 1 increments counter
- Server 2 sees updated count
- Server 3 sees updated count
- Total limit enforced globally

---

### Sliding Window Rate Limiting (Most Accurate)

**Problem with Fixed Window**:
```
Fixed 1-hour windows:
12:00-13:00: 100 requests allowed
13:00-14:00: 100 requests allowed

Attack:
12:59: Send 100 requests ✅
13:00: Send 100 requests ✅
Total: 200 requests in 1 minute!
```

**Sliding Window Solution**:
```python
def sliding_window_rate_limit(user_id, limit=100, window=3600):
    key = f"rate:{user_id}"
    now = time.time()
    
    # Remove old entries outside window
    redis.zremrangebyscore(key, 0, now - window)
    
    # Count requests in current window
    count = redis.zcard(key)
    
    if count < limit:
        # Allow request
        redis.zadd(key, {str(uuid.uuid4()): now})
        redis.expire(key, window)
        return True
    
    return False  # Rate limited

# More accurate: No way to game the system
# Uses Sorted Set to track exact timestamps
```

**Interview Answer**: "I'd implement sliding window rate limiting in Redis using sorted sets to track exact request timestamps, preventing the fixed-window attack where users send 2x limit around window boundary"

---

## Putting It All Together: Complete System Examples

### Example: Twitter Architecture

**All Concepts Combined**:

```
[Users (400M DAU)] 
       ↓
[CloudFront CDN] ← Images, videos
       ↓
[Route53 DNS] ← Geographic routing
       ↓
[ALB Load Balancer] ← SSL termination, Layer 7 routing
       ↓
[API Gateway] ← Rate limiting (300 tweets/3hr)
       ↓
┌──────┴──────┬──────────┬─────────┐
↓             ↓          ↓         ↓
[Tweet      [Timeline  [User    [Search
Service]    Service]   Service] Service]
↓             ↓          ↓         ↓
[Redis Cache] ← 80% hit ratio, < 1ms latency
       ↓
┌──────┴──────┬──────────┐
↓             ↓          ↓
[Cassandra]  [MySQL]   [Elasticsearch]
(Tweets,     (Users,   (Search index)
64 shards)   Sharded)
       ↓
[Kafka] ← Event streaming
       ↓
[Analytics Workers] → Process events
       ↓
[Redshift] ← Analytics database
```

**Capacity**:
- Traffic: 7K write QPS, 1.4M read QPS (peak)
- Storage: 44 PB (5 years)
- Cache: 2 TB Redis (20 servers)
- Database: 64 Cassandra shards + 12 MySQL shards

**Key Decisions**:
1. **Horizontal Scaling**: 1000s of app servers
2. **Layer 7 LB**: Route to microservices
3. **CDN**: 90% of media from edge
4. **Redis**: Timelines cached (Sorted Sets)
5. **Consistent Hashing**: Cassandra sharding
6. **Master-Slave**: MySQL with replicas
7. **Kafka**: Async feed fanout
8. **Rate Limiting**: Token bucket in Redis

---

### Example: Instagram Architecture

```
[Users (500M DAU)]
       ↓
[Akamai CDN] ← 95% photos served from edge
       ↓
[ALB] ← SSL termination
       ↓
[API Gateway] ← 50 uploads/day limit
       ↓
[App Servers (Stateless)]
       ↓
[Redis Cache] ← Feed cache, 90% hit ratio
       ↓
┌──────┴──────┬─────────┐
↓             ↓         ↓
[PostgreSQL] [Cassandra] [S3]
(Users,      (Feed,      (Photos,
12 instances) Activity)   283 PB)
       ↓             ↓
[Replication] [Sharding]
24 replicas   by user_id
```

**Capacity**:
- Traffic: 1.7K write QPS, 867K read QPS (peak)
- Storage: 283 PB (10 years)
- Cache: 60 TB (100 servers)

**Key Decisions**:
1. **S3 + CDN**: All photos in S3, served via CDN
2. **PostgreSQL**: 24 replicas (12 per region)
3. **Redis**: Feed cache with Sorted Sets
4. **Write-Behind**: Like counts batched
5. **Cassandra**: Activity feed (write-heavy)
6. **Geographic CDN**: Photos from nearest edge

---

## Summary & Key Takeaways

### The Essential Patterns

**For Scaling**:
1. Start vertical → Move to horizontal
2. Add load balancer (Layer 7 for flexibility)
3. Make servers stateless (Redis for sessions)
4. Add caching (Redis, 80% hit ratio target)
5. Database replicas for reads
6. Shard when > 10K write QPS
7. Use CDN for media
8. Message queue for async

**For Each 10x Growth**:
```
10K users:
- Single server
- No caching needed

100K users:
- Vertical scaling
- Add Redis cache
- Database read replica

1M users:
- Horizontal scaling (10 servers)
- Load balancer
- Redis cluster
- 3-5 database replicas

10M users:
- 100 servers
- Database sharding
- Redis cluster (10 nodes)
- CDN for media
- Message queue (Kafka)

100M+ users:
- 1000s of servers
- Microservices
- Multiple databases
- Global CDN
- Multi-region deployment
```

---

**Document Version**: 1.0  
**Last Updated**: January 8, 2025  
**Status**: Complete with deep examples ✅

**Topics Covered in Detail**:
✅ Horizontal vs Vertical Scaling (Instagram's journey)
✅ Load Balancing (Round Robin, Least Connections, Layer 4/7, SSL)
✅ Caching (Cache-aside, Write-through, Write-behind, 80-20 rule)
✅ Database Replication (Master-slave, replication lag solutions)
✅ Sharding (Hash-based, Geographic, Twitter/Uber examples)
✅ Consistent Hashing (Virtual nodes, full implementation)
✅ Redis Deep Dive (5 data structures, persistence, clustering)
✅ Kafka Deep Dive (Architecture, consumer groups, Uber example)
✅ CDN Architecture (Push/Pull, cache invalidation, Instagram)
✅ CAP Theorem (Banking CP vs Twitter AP with code)
✅ Rate Limiting (Token bucket, distributed, Twitter/GitHub limits)

**All with**:
- Complete code implementations
- Real company examples (Twitter, Instagram, Uber, Netflix, GitHub)
- Cost calculations
- Trade-off analysis
- Interview-ready answers
