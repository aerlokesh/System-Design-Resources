# Redis System Design Interview Guide

## Why Redis Matters in Interviews

Redis is frequently asked about in system design interviews because it solves **real scalability problems**:
- Reduces database load (caching)
- Handles high-traffic scenarios (rate limiting)
- Prevents race conditions (distributed locks)
- Enables real-time features (pub/sub, leaderboards)

---

## 1. STRING Operations - Interview Focus

### SETNX (Set if Not Exists) - Distributed Locks

**Interview Question:** "How do you prevent duplicate payment processing when multiple servers handle the same order?"

**Answer:**
```python
# Problem: Multiple servers try to process the same payment
# Without lock: Customer charged twice! 💸💸

# Solution with SETNX:
lock_acquired = r.setnx('payment:order123', 'server1')

if lock_acquired:
    # Only ONE server enters this block
    process_payment(order123)
    r.delete('payment:order123')  # Release lock
else:
    # Other servers skip - payment already being processed
    return "Already processing"
```

**Why This Works:**
- SETNX is **atomic** - only ONE server gets True
- Prevents race conditions in distributed systems
- Used by: Stripe, PayPal, Amazon payments

**Interview Follow-up:** "What if the server crashes before releasing the lock?"
**Answer:** Use `SETEX` with timeout:
```python
r.set('payment:order123', 'server1', nx=True, ex=30)  # Auto-expires in 30s
```

---

### INCR/DECR - Atomic Counters

**Interview Question:** "How does Twitter count tweet likes with millions of concurrent users?"

**Answer:**
```python
# Problem: Read-Modify-Write race condition
current = r.get('tweet:123:likes')  # Read: 1000
current += 1                         # Modify: 1001
r.set('tweet:123:likes', current)    # Write: 1001
# BUT: 100 servers doing this = lost updates!

# Solution: Atomic increment
r.incr('tweet:123:likes')  # Atomic, no race condition
# Redis guarantees: Each like counted exactly once
```

**Real Companies Using This:**
- Twitter: Tweet likes, retweets
- YouTube: Video views
- Reddit: Post upvotes
- Instagram: Photo likes

---

### SETEX - Caching with Auto-Expiration

**Interview Question:** "How do you cache API responses to reduce database load?"

**Answer:**
```python
# Check cache first
cached = r.get('api:user:123')
if cached:
    return cached  # Fast! No DB query

# Cache miss - query database
user_data = database.query('SELECT * FROM users WHERE id=123')
r.setex('api:user:123', 300, user_data)  # Cache 5 minutes
return user_data
```

**Why 5 Minutes?**
- Too short: Excessive DB queries
- Too long: Stale data
- Sweet spot: Balance freshness vs performance

**Interview Stats to Mention:**
- Redis read: ~1ms
- Database read: ~50-100ms
- 50-100x faster with caching! 🚀

---

## 2. LIST Operations - Interview Focus

### LPUSH/RPOP - Task Queues

**Interview Question:** "How does Uber dispatch ride requests to drivers?"

**Answer:**
```python
# Producer (Rider app):
r.rpush('ride_requests:sf', json.dumps({
    'rider': 'user123',
    'location': '37.7749, -122.4194'
}))

# Consumer (Driver matching service):
while True:
    request = r.blpop('ride_requests:sf', timeout=5)
    if request:
        match_driver(request)
```

**Why BLPOP (Blocking)?**
- Workers sleep until work available
- No CPU waste polling
- Instant processing when request arrives

**Used By:**
- Uber: Ride matching
- DoorDash: Order processing
- Stripe: Payment processing
- Slack: Message delivery

---

### LTRIM - Activity Feeds

**Interview Question:** "How does Instagram show your last 50 posts?"

**Answer:**
```python
# Add new post
r.lpush('user:123:posts', 'post_999')  # Newest first
r.ltrim('user:123:posts', 0, 49)       # Keep only 50

# Retrieve feed
posts = r.lrange('user:123:posts', 0, 9)  # First 10 posts
```

**Interview Insight:**
- O(1) insertion (LPUSH)
- O(1) trimming (LTRIM)
- Infinitely scalable

---

## 3. SET Operations - Interview Focus

### SADD + SISMEMBER - Unique Tracking

**Interview Question:** "How does Google Analytics count unique visitors?"

**Answer:**
```python
# Each page visit
visitor_ip = '192.168.1.100'
is_new = r.sadd('visitors:2024-01-15', visitor_ip)

if is_new:  # Returns 1 if new, 0 if duplicate
    r.incr('unique_visitors_count:2024-01-15')

# Get total
unique_count = r.scard('visitors:2024-01-15')  # O(1)
```

**Why Not Database?**
- Database: Scan millions of rows for `COUNT(DISTINCT ip)`
- Redis SET: O(1) check + O(1) count
- 1000x faster! ⚡

---

### SINTER - Common Interests

**Interview Question:** "How does LinkedIn suggest connections (mutual friends)?"

**Answer:**
```python
# Store followers
r.sadd('user:alice:following', 'bob', 'charlie', 'dave')
r.sadd('user:bob:following', 'alice', 'charlie', 'eve')

# Find mutual (common friends)
mutual = r.sinter('user:alice:following', 'user:bob:following')
# Result: {'charlie'}
```

**Interview Calculations:**
- 1000 friends each: Database needs 1,000,000 comparisons
- Redis SET intersection: O(N) where N = smaller set
- Used by: LinkedIn, Facebook, Twitter

---

## 4. HASH Operations - Interview Focus

### HSET/HGETALL - User Profiles

**Interview Question:** "How does Facebook store user profiles?"

**Answer:**
```python
# Store profile (one user = one hash)
r.hset('user:123', mapping={
    'name': 'John',
    'email': 'john@example.com',
    'age': '28',
    'city': 'NYC'
})

# Update one field (without rewriting everything)
r.hset('user:123', 'city', 'LA')  # O(1)

# Get entire profile
profile = r.hgetall('user:123')  # O(N) where N = fields
```

**Why Not Strings?**
```python
# ❌ Bad: Store as JSON string
r.set('user:123', '{"name":"John","age":28}')
# To update age: Must read entire JSON, modify, write back

# ✅ Good: Store as Hash
r.hincrby('user:123', 'age', 1)  # Atomic, efficient
```

---

### HINCRBY - Shopping Carts

**Interview Question:** "How does Amazon handle shopping cart quantities?"

**Answer:**
```python
# Add product to cart
r.hset('cart:user123', 'product_456', 2)  # 2 items

# User adds one more
r.hincrby('cart:user123', 'product_456', 1)  # Now 3

# Get entire cart
cart = r.hgetall('cart:user123')
# {'product_456': '3', 'product_789': '1'}
```

**Concurrency Handling:**
- Multiple tabs adding same product: No problem!
- HINCRBY is atomic - no lost updates

---

## 5. SORTED SET Operations - Interview Focus

### ZADD/ZREVRANGE - Leaderboards

**Interview Question:** "How does Fortnite show real-time leaderboards for millions of players?"

**Answer:**
```python
# Player finishes game
r.zadd('leaderboard:global', {'player123': 5000})  # Score: 5000

# Another game - add score
r.zincrby('leaderboard:global', 800, 'player123')  # Now: 5800

# Get top 10 players
top_10 = r.zrevrange('leaderboard:global', 0, 9, withscores=True)
# [('player999', 9000), ('player123', 5800), ...]
```

**Performance:**
- 1 million players: O(log N) insertion
- Database: Requires full table scan + sort
- Redis: 1000x faster for rankings! 🏆

**Used By:**
- Fortnite: Player rankings
- Duolingo: Language learner rankings
- Stack Overflow: User reputation
- Reddit: Hot posts ranking

---

### ZREMRANGEBYSCORE - Time-based Data Cleanup

**Interview Question:** "How do you implement a sliding window rate limiter?"

**Answer:**
```python
def check_rate_limit(user_id):
    now = time.time()
    key = f'rate:{user_id}'
    
    # Add current request
    r.zadd(key, {f'req_{now}': now})
    
    # Remove requests older than 60 seconds
    r.zremrangebyscore(key, '-inf', now - 60)
    
    # Count requests in last 60 seconds
    count = r.zcard(key)
    
    return count <= 100  # Max 100 requests per minute
```

**Why This Beats Fixed Windows:**
```
Fixed Window: 100 requests at 00:59, 100 at 01:01 = 200 in 2 seconds! 💥
Sliding Window: Smooth, true rate limiting
```

---

## Common Interview Questions & Answers

### Q1: "Why use Redis instead of database?"

**Answer:**
- **Speed**: Redis is in-memory (microseconds vs milliseconds)
- **Atomic Operations**: INCR, SETNX are race-condition-free
- **Data Structures**: Lists, Sets, Sorted Sets built-in
- **TTL**: Automatic expiration (caching, sessions)

**When to Use:**
- ✅ Caching
- ✅ Rate limiting
- ✅ Real-time leaderboards
- ✅ Session storage
- ✅ Distributed locks

**When NOT to Use:**
- ❌ Primary data storage (not durable)
- ❌ Complex queries (SQL better)
- ❌ Large datasets (expensive RAM)

---

### Q2: "What happens if Redis crashes?"

**Answer:**
1. **Persistence Options:**
   - RDB: Snapshot every N minutes (fast, but data loss)
   - AOF: Append-only file (slower, less data loss)
   - Hybrid: RDB + AOF (recommended)

2. **High Availability:**
   - Redis Sentinel: Auto-failover
   - Redis Cluster: Sharding + replication
   - Companies use: 3-5 replicas

3. **Interview Tip:** Mention "Redis is cache, not database"
   - Cache miss → Query database
   - System degrades but doesn't fail

---

### Q3: "How do you scale Redis?"

**Answer:**

**Vertical Scaling (Easier):**
- Upgrade to larger instance
- Limit: Single machine RAM

**Horizontal Scaling (Harder but necessary):**
1. **Read Replicas:**
   - Master: Writes
   - Replicas: Reads only
   - Used for: Analytics, reporting

2. **Sharding/Partitioning:**
   - Hash key → Determine shard
   - `user:123` → Shard 1
   - `user:456` → Shard 2
   - Trade-off: Can't do cross-shard operations

3. **Redis Cluster:**
   - Automatic sharding
   - 16,384 hash slots
   - Used by: Twitter, GitHub, Stack Overflow

---

## Key Interview Takeaways

1. **Know Your Trade-offs:**
   - Speed vs Durability
   - Memory vs Disk
   - Simplicity vs Scalability

2. **Mention Real Companies:**
   - "Twitter uses Redis for timeline caching"
   - "Uber uses Redis for geospatial data"
   - "Instagram uses Redis for feed ranking"

3. **Performance Numbers:**
   - Redis: <1ms latency
   - Database: 50-100ms latency
   - 50-100x speedup with caching

4. **Common Patterns:**
   - Cache-aside pattern
   - Write-through cache
   - Distributed locking
   - Rate limiting
   - Leaderboards

---

## Practice Interview Question

**"Design Instagram's feed system"**

**Redis Usage:**
```python
# 1. Store user feed (List)
r.lpush('feed:user123', 'post_999')
r.ltrim('feed:user123', 0, 99)  # Keep last 100

# 2. Cache post details (Hash)
r.hset('post:999', mapping={
    'author': 'alice',
    'likes': 1500,
    'image_url': 'https://...'
})

# 3. Track post likes (Set)
r.sadd('post:999:liked_by', 'user456')
if r.sismember('post:999:liked_by', 'user456'):
    # Show "You liked this"
    
# 4. Trending posts (Sorted Set)
r.zincrby('trending:today', 1, 'post:999')
trending = r.zrevrange('trending:today', 0, 9)
```

**Scale Considerations:**
- Cache layer: Reduce DB queries by 90%
- TTL: 1 hour for feeds, 5 min for trending
- Replicas: 3 read replicas for global scale

---

Good luck with your interview! 🚀
