# Scaling Reads: Comprehensive System Design Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Why Read Scaling Matters](#why-read-scaling-matters)
3. [Read vs Write Patterns](#read-vs-write-patterns)
4. [Core Read Scaling Strategies](#core-read-scaling-strategies)
5. [Database-Level Read Scaling](#database-level-read-scaling)
6. [Caching Strategies](#caching-strategies)
7. [Content Delivery Networks (CDN)](#content-delivery-networks-cdn)
8. [Search Engines & Indexing](#search-engines--indexing)
9. [Advanced Patterns](#advanced-patterns)
10. [Trade-offs & Decision Matrix](#trade-offs--decision-matrix)
11. [Real-World Examples](#real-world-examples)
12. [Interview Framework](#interview-framework)

---

## Introduction

Read scaling is one of the most critical aspects of system design interviews. Most real-world applications have read-heavy workloads (90-95% reads vs 5-10% writes), making read optimization essential for:
- Performance
- User experience
- Cost efficiency
- System reliability

This guide covers all major read scaling techniques you'll encounter in system design interviews.

---

## Why Read Scaling Matters

### Read-Heavy Workload Examples
- **Social Media**: Users scroll feeds (read) more than posting (write)
- **E-commerce**: Product browsing (read) >> purchasing (write)
- **News Websites**: Reading articles (read) >> publishing articles (write)
- **Video Streaming**: Watching videos (read) >> uploading videos (write)

### Key Metrics
- **Read Latency**: Time to retrieve data (target: <100ms)
- **Throughput**: Requests per second (QPS)
- **Availability**: System uptime (target: 99.99%+)
- **Consistency**: Data freshness requirements

---

## Read vs Write Patterns

### Read Characteristics
```
Read Operations:
✓ Can be cached
✓ Can be replicated
✓ Can be eventually consistent
✓ Can be parallelized
✓ Idempotent (safe to retry)
```

### Write Characteristics
```
Write Operations:
✗ Harder to cache (cache invalidation)
✗ Require coordination
✗ Need strong consistency
✗ Serialization bottlenecks
✗ Not always idempotent
```

### Read/Write Ratios
| System Type | Read:Write Ratio | Optimization Focus |
|-------------|------------------|-------------------|
| Social Media | 95:5 | Aggressive read caching |
| E-commerce | 90:10 | Product catalog caching |
| Banking | 70:30 | Balanced approach |
| Analytics | 99:1 | Read replicas, materialized views |
| Messaging | 50:50 | Hybrid optimization |

---

## Core Read Scaling Strategies

### 1. Vertical Scaling (Scale Up)
**What**: Increase hardware resources (CPU, RAM, SSD)

**When to Use**:
- Small to medium applications
- Simplest starting point
- Before horizontal scaling

**Pros**:
- Simple implementation
- No code changes needed
- No distributed complexity

**Cons**:
- Physical limits (~96 cores, ~1TB RAM)
- Single point of failure
- Expensive at scale
- Downtime for upgrades

**Example**:
```
Before: 4 cores, 16GB RAM → Handles 1,000 QPS
After:  16 cores, 64GB RAM → Handles 4,000 QPS
```

---

### 2. Database Read Replicas
**What**: Create read-only copies of the database

**Architecture**:
```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│  Load Balancer  │
└─────────┬───────┘
          │
    ┌─────┴─────┬─────────┬─────────┐
    ▼           ▼         ▼         ▼
┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
│ Master │  │Replica1│  │Replica2│  │Replica3│
│(Write) │  │ (Read) │  │ (Read) │  │ (Read) │
└────┬───┘  └────────┘  └────────┘  └────────┘
     │           ▲         ▲         ▲
     └───────────┴─────────┴─────────┘
         Replication Stream
```

**Implementation Steps**:
1. **Set up master-slave replication**
2. **Configure async/sync replication**
3. **Route reads to replicas**
4. **Route writes to master**

**Read Replica Patterns**:

**Pattern A: Application-Level Routing**
```python
class DatabaseRouter:
    def read(self, query):
        replica = self.get_random_replica()
        return replica.execute(query)
    
    def write(self, query):
        return self.master.execute(query)
    
    def get_random_replica(self):
        return random.choice(self.replicas)
```

**Pattern B: Proxy-Level Routing** (ProxySQL, MaxScale)
```sql
-- Automatic routing based on query type
SELECT * FROM users;  -- Routes to replica
UPDATE users SET ...  -- Routes to master
```

**Replication Types**:

| Type | Lag | Consistency | Use Case |
|------|-----|-------------|----------|
| Synchronous | 0ms | Strong | Financial transactions |
| Semi-sync | <10ms | Strong-ish | Critical reads |
| Asynchronous | 10-100ms | Eventual | Social feeds |

**Handling Replication Lag**:
```python
# Strategy 1: Read-Your-Writes Consistency
def update_profile(user_id, data):
    # Write to master
    master.update(user_id, data)
    
    # Force next read from master for this user
    cache.set(f"force_master_{user_id}", True, ttl=5)

def get_profile(user_id):
    if cache.get(f"force_master_{user_id}"):
        return master.query(user_id)
    return replica.query(user_id)
```

**Pros**:
- Linear read scaling (add more replicas)
- Geographic distribution possible
- Fault tolerance
- No application logic changes (mostly)

**Cons**:
- Replication lag (eventual consistency)
- Increased storage costs
- Complex failover
- Master still bottleneck for writes

**Scaling Numbers**:
```
1 Master + 0 Replicas: 10,000 reads/sec
1 Master + 3 Replicas: 40,000 reads/sec
1 Master + 10 Replicas: 100,000 reads/sec
```

---

### 3. Database Sharding (Horizontal Partitioning)
**What**: Split data across multiple databases

**When to Use**:
- Dataset > 1TB
- Single database maxed out
- Need geographic distribution

**Sharding Strategies**:

**A. Hash-Based Sharding**
```python
def get_shard(user_id):
    return hash(user_id) % NUM_SHARDS

# Example:
user_123 → hash(123) % 4 = Shard 2
user_456 → hash(456) % 4 = Shard 0
```

**B. Range-Based Sharding**
```python
def get_shard(user_id):
    if user_id < 1000000:
        return "shard_0"
    elif user_id < 2000000:
        return "shard_1"
    else:
        return "shard_2"
```

**C. Geographic Sharding**
```python
SHARD_MAP = {
    'US-EAST': 'shard_us_east',
    'US-WEST': 'shard_us_west',
    'EU': 'shard_europe',
    'ASIA': 'shard_asia'
}

def get_shard(user_location):
    return SHARD_MAP[user_location]
```

**D. Directory-Based Sharding**
```python
# Lookup table
SHARD_DIRECTORY = {
    'user_1': 'shard_a',
    'user_2': 'shard_b',
    'user_3': 'shard_a',
}
```

**Challenges**:
```
1. Cross-Shard Queries
   Problem: JOIN across shards
   Solution: Denormalization, application-level joins

2. Resharding
   Problem: Adding/removing shards
   Solution: Consistent hashing

3. Hotspots
   Problem: Uneven data distribution
   Solution: Better shard key selection

4. Transactions
   Problem: ACID across shards
   Solution: 2PC, Sagas, eventual consistency
```

**Consistent Hashing** (Better Resharding):
```python
class ConsistentHash:
    def __init__(self, nodes=None, replicas=3):
        self.replicas = replicas
        self.ring = dict()
        self.sorted_keys = []
        
        if nodes:
            for node in nodes:
                self.add_node(node)
    
    def add_node(self, node):
        for i in range(self.replicas):
            key = self.hash(f"{node}:{i}")
            self.ring[key] = node
            self.sorted_keys.append(key)
        self.sorted_keys.sort()
    
    def get_node(self, key):
        if not self.ring:
            return None
        hash_key = self.hash(key)
        for node_key in self.sorted_keys:
            if hash_key <= node_key:
                return self.ring[node_key]
        return self.ring[self.sorted_keys[0]]
```

---

## Caching Strategies

### Caching Layers
```
┌──────────────┐
│   Browser    │  ← Client-side cache
└──────┬───────┘
       │
┌──────▼───────┐
│     CDN      │  ← Edge cache
└──────┬───────┘
       │
┌──────▼───────┐
│  API Gateway │  ← API cache
└──────┬───────┘
       │
┌──────▼───────┐
│  Redis/      │  ← Application cache
│  Memcached   │
└──────┬───────┘
       │
┌──────▼───────┐
│   Database   │  ← Query cache
└──────────────┘
```

### 1. Cache-Aside (Lazy Loading)
```python
def get_user(user_id):
    # Try cache first
    user = cache.get(f"user:{user_id}")
    
    if user is None:
        # Cache miss - fetch from DB
        user = db.query("SELECT * FROM users WHERE id = ?", user_id)
        
        # Store in cache
        cache.set(f"user:{user_id}", user, ttl=3600)
    
    return user

def update_user(user_id, data):
    # Update database
    db.update(user_id, data)
    
    # Invalidate cache
    cache.delete(f"user:{user_id}")
```

**Pros**: Only cache what's needed, cache failures don't break app
**Cons**: Cache miss penalty, potential stampede

---

### 2. Write-Through Cache
```python
def update_user(user_id, data):
    # Write to cache AND database
    db.update(user_id, data)
    cache.set(f"user:{user_id}", data, ttl=3600)
    
def get_user(user_id):
    # Always read from cache
    return cache.get(f"user:{user_id}")
```

**Pros**: Cache always consistent, no miss penalty
**Cons**: Write latency, unused data cached

---

### 3. Write-Behind (Write-Back) Cache
```python
def update_user(user_id, data):
    # Write to cache immediately
    cache.set(f"user:{user_id}", data, ttl=3600)
    
    # Queue for async DB write
    write_queue.enqueue({
        'user_id': user_id,
        'data': data
    })

# Background worker
def flush_to_database():
    while True:
        batch = write_queue.dequeue_batch(100)
        db.batch_update(batch)
```

**Pros**: Fast writes, batching possible
**Cons**: Data loss risk, complex recovery

---

### 4. Read-Through Cache
```python
class ReadThroughCache:
    def get(self, key):
        value = self.cache.get(key)
        if value is None:
            value = self.load_from_db(key)
            self.cache.set(key, value)
        return value
```

**Pros**: Abstraction, automatic population
**Cons**: Cache miss penalty

---

### Cache Eviction Policies

| Policy | Description | Use Case |
|--------|-------------|----------|
| LRU | Least Recently Used | General purpose |
| LFU | Least Frequently Used | Popular items |
| FIFO | First In First Out | Simple queues |
| TTL | Time To Live | Time-sensitive data |
| Random | Random eviction | When access pattern unclear |

---

### Cache Stampede Prevention
```python
# Problem: 1000 concurrent requests when cache expires
# Solution: Single-flight/dog-pile prevention

from threading import Lock

class CacheWithStampedePrevention:
    def __init__(self):
        self.cache = {}
        self.locks = {}
    
    def get_or_compute(self, key, compute_fn):
        # Check cache
        if key in self.cache:
            return self.cache[key]
        
        # Acquire lock for this key
        if key not in self.locks:
            self.locks[key] = Lock()
        
        with self.locks[key]:
            # Double-check after acquiring lock
            if key in self.cache:
                return self.cache[key]
            
            # Only one thread computes
            value = compute_fn()
            self.cache[key] = value
            return value
```

---

### Distributed Caching
```
┌─────────┐  ┌─────────┐  ┌─────────┐
│ Redis 1 │  │ Redis 2 │  │ Redis 3 │
│  Shard  │  │  Shard  │  │  Shard  │
└─────────┘  └─────────┘  └─────────┘
     ▲            ▲            ▲
     └────────────┴────────────┘
         Consistent Hashing
```

**Redis Cluster Example**:
```python
from redis.cluster import RedisCluster

cache = RedisCluster(
    startup_nodes=[
        {"host": "redis-1", "port": 6379},
        {"host": "redis-2", "port": 6379},
        {"host": "redis-3", "port": 6379},
    ]
)

# Automatic sharding
cache.set("user:123", json.dumps(user_data))
cache.get("user:123")
```

---

## Content Delivery Networks (CDN)

### CDN Architecture
```
User in Tokyo → CDN Edge (Tokyo) → Origin Server (US)
                   [Cache Hit]
                   ↓
                Response: 20ms

vs.

User in Tokyo → Origin Server (US) → Response: 200ms
```

### CDN Strategies

**1. Static Content Caching**
```
HTML, CSS, JS → CDN (24 hours)
Images → CDN (7 days)
Videos → CDN (30 days)
```

**2. Dynamic Content Acceleration**
```
CDN → Edge compute
    → Pre-rendered pages
    → API responses with short TTL
```

**3. CDN Configuration Example**
```javascript
// CloudFront configuration
{
  "Origins": [{
    "DomainName": "api.example.com",
    "Id": "api-origin"
  }],
  "DefaultCacheBehavior": {
    "TargetOriginId": "api-origin",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": ["GET", "HEAD"],
    "CachedMethods": ["GET", "HEAD"],
    "MinTTL": 0,
    "DefaultTTL": 3600,
    "MaxTTL": 86400,
    "Compress": true
  }
}
```

**4. Cache Headers**
```http
# Response from origin
Cache-Control: public, max-age=3600
ETag: "abc123"
Last-Modified: Wed, 21 Oct 2024 07:28:00 GMT

# Conditional request
If-None-Match: "abc123"
If-Modified-Since: Wed, 21 Oct 2024 07:28:00 GMT

# CDN response
304 Not Modified (if unchanged)
200 OK (if changed)
```

---

## Search Engines & Indexing

### When to Use Search Engines
- Full-text search
- Faceted search (filters)
- Fuzzy matching
- Relevance ranking
- Analytics queries

### Elasticsearch Architecture
```
┌────────────────────────────────────┐
│        Load Balancer              │
└────────┬───────────────────────────┘
         │
    ┌────┴────┬────────┬────────┐
    ▼         ▼        ▼        ▼
┌────────┐┌────────┐┌────────┐┌────────┐
│ Node 1 ││ Node 2 ││ Node 3 ││ Node 4 │
│Shard 0 ││Shard 1 ││Shard 2 ││Shard 0 │
│Replica ││Replica ││Replica ││Replica │
└────────┘└────────┘└────────┘└────────┘
```

### Indexing Strategy
```python
from elasticsearch import Elasticsearch

es = Elasticsearch(['localhost:9200'])

# Index document
es.index(
    index='products',
    id=product_id,
    body={
        'name': 'iPhone 15',
        'description': 'Latest smartphone...',
        'price': 999,
        'category': 'electronics',
        'tags': ['phone', 'apple', '5g']
    }
)

# Search with multiple filters
results = es.search(
    index='products',
    body={
        'query': {
            'bool': {
                'must': [
                    {'match': {'description': 'smartphone'}}
                ],
                'filter': [
                    {'range': {'price': {'lte': 1000}}},
                    {'term': {'category': 'electronics'}}
                ]
            }
        }
    }
)
```

### Dual-Write Pattern
```python
def create_product(product_data):
    # Write to database (source of truth)
    product = db.products.insert(product_data)
    
    # Index in Elasticsearch (async)
    index_queue.enqueue({
        'action': 'index',
        'product_id': product.id,
        'data': product_data
    })
    
    return product
```

---

## Advanced Patterns

### 1. CQRS (Command Query Responsibility Segregation)
```
Write Model (Commands)          Read Model (Queries)
┌──────────────┐                ┌──────────────┐
│  Write DB    │───Events──────▶│  Read DB     │
│ (PostgreSQL) │                │  (MongoDB)   │
│              │                │              │
│ Normalized   │                │ Denormalized │
│ ACID         │                │ Optimized    │
└──────────────┘                └──────────────┘
```

**Implementation**:
```python
# Command (Write) Side
class CreateOrderCommand:
    def execute(self, order_data):
        # Write to normalized database
        order = write_db.orders.create(order_data)
        
        # Publish event
        event_bus.publish('OrderCreated', {
            'order_id': order.id,
            'user_id': order.user_id,
            'items': order.items,
            'total': order.total
        })

# Query (Read) Side
class OrderCreatedHandler:
    def handle(self, event):
        # Update denormalized read model
        read_db.user_orders.update(
            {'user_id': event['user_id']},
            {
                '$push': {
                    'orders': {
                        'id': event['order_id'],
                        'total': event['total'],
                        'created_at': datetime.now()
                    }
                }
            }
        )
```

**Benefits**:
- Optimized read models
- Independent scaling
- Multiple read models possible

**Challenges**:
- Eventual consistency
- Complexity
- Event versioning

---

### 2. Materialized Views
```sql
-- Regular view (query every time)
CREATE VIEW user_stats AS
SELECT 
    user_id,
    COUNT(*) as total_orders,
    SUM(amount) as total_spent
FROM orders
GROUP BY user_id;

-- Materialized view (pre-computed)
CREATE MATERIALIZED VIEW user_stats_mv AS
SELECT 
    user_id,
    COUNT(*) as total_orders,
    SUM(amount) as total_spent
FROM orders
GROUP BY user_id;

-- Refresh strategy
REFRESH MATERIALIZED VIEW CONCURRENTLY user_stats_mv;
```

**Refresh Strategies**:
1. **On-demand**: Manual refresh
2. **Scheduled**: Cron job (e.g., hourly)
3. **Incremental**: Update only changed data
4. **Real-time**: Trigger-based

---

### 3. Denormalization
```sql
-- Normalized (requires JOIN)
SELECT u.name, o.total, p.name
FROM users u
JOIN orders o ON u.id = o.user_id
JOIN products p ON o.product_id = p.id;

-- Denormalized (single table)
SELECT user_name, order_total, product_name
FROM order_details;
```

**When to Denormalize**:
- Read frequency >> Write frequency
- JOIN performance becomes bottleneck
- Data rarely changes
- Consistency can be eventual

---

### 4. Database Indexing
```sql
-- Create index on frequently queried columns
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_order_user_date ON orders(user_id, created_at);

-- Covering index (includes all needed columns)
CREATE INDEX idx_product_search 
ON products(category, price) 
INCLUDE (name, description);

-- Partial index (filtered)
CREATE INDEX idx_active_users 
ON users(email) 
WHERE status = 'active';
```

**Index Types**:
| Type | Use Case | Example |
|------|----------|---------|
| B-Tree | Range queries, sorting | `id > 100` |
| Hash | Exact matches | `email = 'user@example.com'` |
| GiST/GIN | Full-text, JSON | `description @@ 'search'` |
| Bitmap | Low cardinality | `status IN ('active', 'pending')` |

---

### 5. Database Query Optimization
```sql
-- Bad: Full table scan
SELECT * FROM orders WHERE YEAR(created_at) = 2024;

-- Good: Index-friendly
SELECT * FROM orders 
WHERE created_at >= '2024-01-01' 
  AND created_at < '2025-01-01';

-- Bad: N+1 queries
for user in users:
    orders = db.query("SELECT * FROM orders WHERE user_id = ?", user.id)

-- Good: Single query with JOIN
orders = db.query("""
    SELECT u.*, o.*
    FROM users u
    LEFT JOIN orders o ON u.id = o.user_id
""")
```

---

### 6. Connection Pooling
```python
from sqlalchemy import create_engine
from sqlalchemy.pool import QueuePool

# Connection pool configuration
engine = create_engine(
    'postgresql://user:pass@localhost/db',
    poolclass=QueuePool,
    pool_size=20,          # Maintain 20 connections
    max_overflow=10,       # Allow 10 additional connections
    pool_timeout=30,       # Wait 30s for available connection
    pool_recycle=3600      # Recycle connections after 1 hour
)

# Reuse connections efficiently
with engine.connect() as conn:
    result = conn.execute("SELECT * FROM users")
```

---

### 7. Batch Processing & Pagination
```python
# Bad: Load all data at once
all_users = db.query("SELECT * FROM users")  # 1M records

# Good: Process in batches
def process_users_in_batches(batch_size=1000):
    offset = 0
    while True:
        batch = db.query(
            "SELECT * FROM users LIMIT ? OFFSET ?",
            batch_size, offset
        )
        
        if not batch:
            break
        
        process_batch(batch)
        offset += batch_size

# Better: Cursor-based pagination
def process_users_cursor(batch_size=1000):
    last_id = 0
    while True:
        batch = db.query(
            "SELECT * FROM users WHERE id > ? ORDER BY id LIMIT ?",
            last_id, batch_size
        )
        
        if not batch:
            break
        
        process_batch(batch)
        last_id = batch[-1].id
```

---

## Trade-offs & Decision Matrix

### Technique Comparison

| Technique | Complexity | Cost | Consistency | Latency | Use When |
|-----------|------------|------|-------------|---------|----------|
| Vertical Scaling | Low | High | Strong | Low | Starting out |
| Read Replicas | Medium | Medium | Eventual | Low | Read >> Write |
| Sharding | High | Medium | Strong* | Low | Data > 1TB |
| Caching | Medium | Low | Eventual | Very Low | Hot data |
| CDN | Low | Medium | Eventual | Very Low | Static content |
| Search Engine | Medium | Medium | Eventual | Low | Full-text search |
| CQRS | High | High | Eventual | Very Low | Complex reads |
| Materialized Views | Medium | Low | Eventual | Very Low | Analytics |

*Strong within shard, Eventual across shards

---

### Decision Tree

```
Start: Need to scale reads?
│
├─ Static content (images, videos, JS)?
│  └─ YES → Use CDN
│
├─ Full-text search required?
│  └─ YES → Use Elasticsearch
│
├─ Data size < 100GB?
│  │
│  ├─ Read:Write < 80:20?
│  │  └─ YES → Caching + Read Replicas
│  │
│  └─ Read:Write > 80:20?
│     └─ YES → Aggressive Caching
│
└─ Data size > 1TB?
   └─ YES → Sharding + Caching + Read Replicas
```

---

## Real-World Examples

### Example 1: Instagram Feed
**Problem**: 1 billion users, reading feeds constantly

**Solution**:
```
1. CDN: Profile pictures, videos
2. Redis Cache: 
   - Feed cache per user (last 100 posts)
   - TTL: 5 minutes
3. Read Replicas: 
   - 50 replicas for follower/following queries
4. Cassandra: 
   - Denormalized feed table
   - Partitioned by user_id
```

**Architecture**:
```
CDN (Images/Videos)
    │
    ▼
┌─────────┐     ┌──────────┐
│ Redis   │────▶│ Feed API │
│ Cache   │     └────┬─────┘
└─────────┘          │
                     ▼
    ┌────────────────┴────────────────┐
    │                                 │
    ▼                                 ▼
┌──────────┐                   ┌──────────┐
│Cassandra │                   │PostgreSQL│
│(Feed)    │                   │Read      │
│          │                   │Replicas  │
└──────────┘                   └──────────┘
```

---

### Example 2: Netflix Video Streaming
**Problem**: Stream videos to 200M+ users globally

**Solution**:
```
1. CDN: 95% of traffic
   - Open Connect (Netflix's CDN)
   - ISP-embedded caches
   
2. Pre-computation:
   - Multiple bitrate versions
   - Different codec formats
   
3. Predictive Caching:
   - Cache popular content before demand
   - Regional preferences
   
4. Cassandra:
   - User viewing history
   - Watch progress
   - Recommendations
```

---

### Example 3: Twitter Timeline
**Problem**: Generate timeline for 400M users

**Solution**:
```
1. Fan-out on Write (for most users):
   - When user tweets, push to all followers' caches
   - Redis cache per user
   
2. Fan-out on Read (for celebrities):
   - Dynamically generate timeline
   - Merge cached celebrity tweets
   
3. Manhattan (distributed DB):
   - Tweet storage with read replicas
   
4. GraphJet:
   - In-memory graph for recommendations
```

**Hybrid Approach**:
```python
def get_timeline(user_id):
    # Get cached timeline
    cached_timeline = redis.get(f"timeline:{user_id}")
    
    # Get celebrity tweets (not in cache)
    celebrity_following = get_celebrity_following(user_id)
    celebrity_tweets = fetch_recent_tweets(celebrity_following)
    
    # Merge and sort
    return merge_and_sort(cached_timeline, celebrity_tweets)
```

---

### Example 4: Amazon Product Catalog
**Problem**: Search and browse 500M+ products

**Solution**:
```
1. Elasticsearch:
   - Product search
   - Faceted filtering
   - Autocomplete
   
2. DynamoDB:
   - Product details
   - Inventory
   
3. CloudFront CDN:
   - Product images
   - Static assets
   
4. ElastiCache (Redis):
   - Popular products
   - User session data
   
5. Read Replicas:
   - Order history
   - Reviews
```

**Indexing Strategy**:
```json
{
  "mappings": {
    "properties": {
      "name": { "type": "text", "analyzer": "english" },
      "description": { "type": "text" },
      "price": { "type": "float" },
      "category": { "type": "keyword" },
      "brand": { "type": "keyword" },
      "rating": { "type": "float" },
      "in_stock": { "type": "boolean" }
    }
  }
}
```

---

## Interview Framework

### How to Approach Read Scaling in Interviews

**Step 1: Clarify Requirements** (5 minutes)
```
Questions to Ask:
1. What is the read:write ratio?
2. What is the expected QPS (queries per second)?
3. How many users? (DAU - Daily Active Users)
4. What is the data size?
5. What are the latency requirements?
6. What are the consistency requirements?
7. What is the geographic distribution?
8. What is the budget/cost sensitivity?
```

**Step 2: Calculate Scale** (5 minutes)
```
Example Calculation:
- 100M DAU (Daily Active Users)
- Each user makes 50 read requests/day
- Total reads/day = 100M × 50 = 5B reads/day
- Reads/second = 5B / 86400 = ~58,000 QPS
- Peak (3x average) = ~175,000 QPS
```

**Step 3: Choose Techniques** (10 minutes)
```
Decision Framework:

1. Start with Caching (almost always)
   - Redis for hot data
   - CDN for static content
   
2. Add Read Replicas if:
   - QPS > 10,000
   - Single DB becoming bottleneck
   
3. Consider Sharding if:
   - Data > 1TB
   - Read replicas maxed out
   - Need geographic distribution
   
4. Add Search Engine if:
   - Complex search requirements
   - Full-text search needed
   
5. Use CQRS if:
   - Very complex read patterns
   - Multiple read models needed
```

**Step 4: Design Architecture** (15 minutes)
```
Components to Include:
1. Load Balancer
2. API Servers
3. Cache Layer (Redis/Memcached)
4. Database (Master + Replicas)
5. CDN (for static content)
6. Search Engine (if needed)
7. Monitoring & Metrics
```

**Step 5: Discuss Trade-offs** (5 minutes)
```
Always Mention:
1. Consistency vs Latency
2. Cost vs Performance
3. Complexity vs Maintainability
4. Read optimization vs Write impact
```

---

### Sample Interview Response Template

```
Interviewer: "Design a system to handle 100,000 read requests per second"

You: "Let me start by clarifying some requirements:
1. What is the read:write ratio?
2. What type of data are we reading?
3. What are the consistency requirements?
4. What is the acceptable latency?

[After getting answers...]

Based on 90:10 read:write ratio and eventual consistency being acceptable,
I propose a multi-layered approach:

Layer 1 - CDN:
- Serve static content (images, CSS, JS)
- CloudFront or similar
- Reduces origin load by 70-80%

Layer 2 - Application Cache:
- Redis cluster with 3 shards
- Cache hot data with 5-minute TTL
- Cache-aside pattern
- Expected cache hit rate: 80%

Layer 3 - Database Read Replicas:
- 5 read replicas across regions
- Async replication (acceptable lag: 100ms)
- Load balancer distributes reads
- Handles remaining 20% of requests

This gives us:
- 70,000 QPS → CDN (static)
- 24,000 QPS → Redis cache
- 6,000 QPS → Database replicas (distributed)

Each replica handles ~1,200 QPS, well within limits.

Trade-offs:
+ Very low latency (<50ms)
+ Cost effective
+ Scales horizontally
- Eventual consistency
- Cache invalidation complexity
- Monitoring overhead
"
```

---

### Key Metrics to Discuss

| Metric | Good | Acceptable | Poor |
|--------|------|------------|------|
| **Read Latency** | <50ms | 50-100ms | >100ms |
| **Cache Hit Rate** | >90% | 70-90% | <70% |
| **Availability** | 99.99% | 99.9% | <99.9% |
| **Replication Lag** | <10ms | 10-100ms | >100ms |
| **Cost per 1M reads** | <$1 | $1-$5 | >$5 |

---

### Common Interview Questions & Answers

**Q: Why not just scale vertically?**
```
A: Vertical scaling has limits:
- Physical hardware limits (~96 cores, 1TB RAM)
- Single point of failure
- Expensive beyond a certain point
- Downtime required for upgrades
- Better to combine vertical + horizontal
```

**Q: When would you NOT use caching?**
```
A: Avoid caching when:
- Data changes very frequently
- Strong consistency required (financial transactions)
- Cache invalidation becomes too complex
- Data access pattern is random (no hot data)
- Storage cost of cache > database
```

**Q: How do you handle cache invalidation?**
```
A: Multiple strategies:
1. TTL (Time To Live) - simplest
2. Write-through - update cache on write
3. Event-driven - invalidate on data change
4. Versioning - append version to cache key
5. Cache warming - preload expected data
```

**Q: What if a replica fails?**
```
A: Failover strategy:
1. Health checks detect failure (10-30 seconds)
2. Load balancer removes from pool
3. Traffic redistributed to healthy replicas
4. Alert ops team
5. Auto-recover or manual intervention
6. Promote replica to master if needed
```

**Q: How do you prevent cache stampede?**
```
A: Multiple approaches:
1. Probabilistic early expiration
2. Lock-based loading (single-flight)
3. Background refresh before expiry
4. Staggered TTLs
5. Circuit breaker pattern
```

**Q: Database vs Cache - when to use which?**
```
A: Use Cache for:
- Hot data (frequently accessed)
- Computed/aggregated results
- Session data
- Temporary data

Use Database for:
- Source of truth
- Persistent data
- Complex queries
- Transactions
- Long-term storage
```

---

### Advanced Interview Topics

**1. Multi-Region Read Scaling**
```
Challenge: Serve users globally with low latency

Solution:
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   US-EAST   │     │   EU-WEST   │     │  AP-SOUTH   │
│             │     │             │     │             │
│ Cache + DB  │     │ Cache + DB  │     │ Cache + DB  │
│  Replica    │     │  Replica    │     │  Replica    │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┴───────────────────┘
                  Cross-Region Replication
                  
Considerations:
- Geo-routing (Route 53, CloudFlare)
- Cross-region replication lag
- Conflict resolution
- Data sovereignty/compliance
```

**2. Read-After-Write Consistency**
```python
# Problem: User updates profile, immediately views it, sees old data

# Solution 1: Sticky sessions
def update_profile(user_id, data):
    master.update(user_id, data)
    # Force this user to read from master for 5 seconds
    sticky_cache.set(f"read_master:{user_id}", True, ttl=5)

def get_profile(user_id):
    if sticky_cache.get(f"read_master:{user_id}"):
        return master.query(user_id)
    return replica.query(user_id)

# Solution 2: Version tracking
def update_profile(user_id, data):
    version = master.update_and_get_version(user_id, data)
    cache.set(f"user_version:{user_id}", version)

def get_profile(user_id):
    expected_version = cache.get(f"user_version:{user_id}")
    profile = replica.query(user_id)
    if profile.version >= expected_version:
        return profile
    # Fall back to master
    return master.query(user_id)
```

**3. Handling Hot Partitions**
```
Problem: Celebrity user causes uneven load on shard

Solutions:
1. Dedicated shard for popular entities
2. Aggressive caching at multiple levels
3. Request coalescing
4. Read from replica of hot shard
5. Content pre-generation

Example:
┌──────────────┐
│Celebrity Data│
│              │
│ Replicated   │
│ 10x times    │
└──────────────┘
      │
   10 Replicas (instead of 3)
   Better load distribution
```

**4. Monitoring Read Performance**
```python
# Key metrics to track
metrics = {
    'cache_hit_rate': 0.85,        # Target: >80%
    'cache_miss_latency': 45,      # ms, Target: <100ms
    'replica_lag': 50,             # ms, Target: <100ms
    'p99_latency': 95,             # ms, Target: <100ms
    'qps': 50000,                  # queries/second
    'error_rate': 0.001,           # Target: <0.1%
    'connection_pool_usage': 0.70  # Target: <80%
}

# Alerts to configure
alerts = [
    'cache_hit_rate < 70%',
    'p99_latency > 150ms',
    'replica_lag > 200ms',
    'error_rate > 0.5%'
]
```

---

### Quick Reference: Read Scaling Cheat Sheet

**Latency Targets**:
```
CDN:           10-50ms    (edge cache hit)
Redis:         1-5ms      (in-memory)
Read Replica:  10-50ms    (SSD)
Search Engine: 50-200ms   (complex queries)
Master DB:     20-100ms   (direct query)
```

**When to Add Each Layer**:
```
Caching:        At 1,000+ QPS
Read Replicas:  At 10,000+ QPS
CDN:            Day 1 (for static content)
Sharding:       At 1TB+ data or 100,000+ QPS
Search Engine:  When full-text search needed
CQRS:           When read patterns very complex
```

**Cost Comparison** (relative):
```
Caching:          $ (cheap)
CDN:              $$ (moderate)
Read Replicas:    $$$ (moderate to expensive)
Sharding:         $$$$ (expensive - ops cost)
CQRS:             $$$$$ (very expensive - complexity)
```

---

### Conclusion

Read scaling is fundamental to building performant systems. The key principles are:

1. **Cache aggressively** - Most systems are read-heavy
2. **Layer your caching** - CDN, application cache, database cache
3. **Use read replicas** - Scale horizontally for databases
4. **Shard when necessary** - For very large datasets
5. **Monitor everything** - Cache hit rates, latency, errors
6. **Trade consistency for performance** - When appropriate
7. **Plan for failure** - Replicas provide redundancy

Remember: Start simple, measure, and scale as needed. Premature optimization is the root of all evil, but being prepared with scaling strategies is essential for system design interviews and real-world systems.

---

### Additional Resources

- **Books**: 
  - "Designing Data-Intensive Applications" by Martin Kleppmann
  - "System Design Interview" by Alex Xu
  
- **Papers**:
  - "Scaling Memcache at Facebook"
  - "TAO: Facebook's Distributed Data Store"
  
- **Tools**:
  - Redis (caching)
  - Memcached (caching)
  - PostgreSQL (read replicas)
  - Elasticsearch (search)
  - CloudFront/CloudFlare (CDN)

---

**Document Version**: 1.0  
**Last Updated**: November 2024  
**Author**: System Design Interview Prep Guide
