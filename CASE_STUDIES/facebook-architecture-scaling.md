# Facebook Architecture: The Ultimate Scaling Journey 🚀

## 📋 Table of Contents
1. [Introduction](#introduction)
2. [The Early Days (2004-2006)](#the-early-days-2004-2006)
3. [Initial Scaling Challenges (2006-2008)](#initial-scaling-challenges-2006-2008)
4. [The LAMP to Custom Infrastructure Era (2008-2010)](#the-lamp-to-custom-infrastructure-era-2008-2010)
5. [Massive Scale Solutions (2010-2015)](#massive-scale-solutions-2010-2015)
6. [Modern Facebook Architecture (2015-Present)](#modern-facebook-architecture-2015-present)
7. [Key Technologies & Innovations](#key-technologies--innovations)
8. [System Design Interview Insights](#system-design-interview-insights)
9. [Lessons Learned](#lessons-learned)

---

## Introduction

Facebook's journey from a Harvard dorm room project to serving **3+ billion users** daily is one of the most remarkable scaling stories in tech history. This case study explores how Facebook evolved its architecture, the challenges they faced, and the innovative solutions they developed.

**Key Metrics (Current):**
- 3+ billion monthly active users
- 100+ petabytes of storage
- Hundreds of millions of photos uploaded daily
- Billions of API calls per day
- Sub-second response time for most operations

---

## The Early Days (2004-2006)

### 🎯 Initial Architecture

**The Problem:** Build a social network for college students

**Technology Stack:**
```
┌─────────────────────────────────────────┐
│         Browser (User Interface)         │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         Apache Web Server (PHP)          │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│              MySQL Database              │
└─────────────────────────────────────────┘
```

**Stack Details:**
- **Frontend:** HTML, CSS, JavaScript
- **Backend:** PHP (chosen for rapid development)
- **Database:** Single MySQL server
- **Web Server:** Apache
- **Server:** Single physical server

### Key Characteristics
- **Monolithic Architecture:** Everything in one codebase
- **Simple Deployment:** Single server deployment
- **Direct Database Access:** No caching layer
- **Vertical Scaling:** Just add more RAM/CPU

### Why This Worked Initially
✅ **Fast Development:** PHP enabled quick feature development  
✅ **Simple Deployment:** No complex infrastructure  
✅ **Cost-Effective:** Minimal server requirements  
✅ **Easy Debugging:** All code in one place  

---

## Initial Scaling Challenges (2006-2008)

### 📈 The Growth Problem

**User Growth:**
- 2004: 1,000 users (Harvard only)
- 2005: 1 million users (college networks)
- 2006: 12 million users (opened to everyone)
- 2008: 100 million users

### Challenge #1: Database Bottleneck

**Problem:** Single MySQL database couldn't handle the load

```
❌ BEFORE (Single Database)
        1000s of requests/sec
                ↓
        ┌──────────────┐
        │    MySQL     │ ← Bottleneck!
        └──────────────┘

✅ AFTER (Read Replicas)
    Writes           Reads (90% of traffic)
      ↓               ↓
┌──────────┐    ┌──────────┐
│  Master  │ ──→│ Replica  │
│  MySQL   │    │  MySQL   │
└──────────┘    └──────────┘
                     ↓
                ┌──────────┐
                │ Replica  │
                │  MySQL   │
                └──────────┘
```

**Solution #1: Master-Slave Replication**
- Master for writes
- Multiple slaves for reads
- Load balancing across read replicas
- 90% of queries were reads → significant improvement

### Challenge #2: Web Server Overload

**Problem:** Single Apache server couldn't handle traffic

**Solution #2: Horizontal Scaling + Load Balancing**

```
                User Requests
                      ↓
            ┌─────────────────┐
            │  Load Balancer  │
            └─────────────────┘
                 ↓  ↓  ↓
        ┌────────┴───┴───┴────────┐
        ↓          ↓          ↓
   ┌────────┐ ┌────────┐ ┌────────┐
   │PHP App │ │PHP App │ │PHP App │
   │Server 1│ │Server 2│ │Server 3│
   └────────┘ └────────┘ └────────┘
```

**Key Improvements:**
- Added multiple web servers
- Implemented load balancing (initially hardware, then software)
- Stateless application servers
- Session data moved to shared storage

### Challenge #3: Repeated Database Queries

**Problem:** Same queries executed thousands of times per second

**Solution #3: Memcached Layer**

```
                Application
                     ↓
            ┌──────────────┐
            │   Is data    │
            │ in cache?    │
            └──────────────┘
               ↓       ↓
           YES ↓       ↓ NO
    ┌──────────┐   ┌──────────────┐
    │Memcached │   │Fetch from DB │
    │  (RAM)   │←──│ + Cache it   │
    └──────────┘   └──────────────┘
```

**Impact:**
- 95%+ cache hit rate
- Reduced database load by 80%
- Sub-millisecond response times
- Cost-effective (RAM cheaper than DB operations)

---

## The LAMP to Custom Infrastructure Era (2008-2010)

### 🔧 Hitting PHP Limits

**The Problem:** PHP couldn't scale further
- CPU-intensive operations
- Memory inefficiency
- Slow execution for complex operations

### Innovation #1: HipHop for PHP (HPHPc)

**What:** PHP-to-C++ compiler

```
PHP Source Code
      ↓
┌──────────────┐
│   HPHPc      │ Compiler
│  Translator  │
└──────────────┘
      ↓
  C++ Code
      ↓
┌──────────────┐
│  Compiled    │
│   Binary     │
└──────────────┘
```

**Results:**
- **50% reduction in CPU usage**
- **6x performance improvement**
- Maintained PHP developer experience
- Saved millions in infrastructure costs

### Innovation #2: TAO (The Associations and Objects)

**The Problem:** Graph data model doesn't fit well in relational DB

**TAO Architecture:**

```
           Facebook Social Graph
                  ↓
        ┌──────────────────────┐
        │  TAO Cache Layer     │
        │  (Distributed Cache) │
        └──────────────────────┘
                  ↓
        ┌──────────────────────┐
        │   MySQL Shards       │
        │  (Data Persistence)  │
        └──────────────────────┘
```

**Key Features:**
- **Objects:** Users, Photos, Posts, Comments
- **Associations:** Friendships, Likes, Comments
- Write-through cache
- Consistency guarantees
- Efficient graph queries

**Example: Fetching a News Feed**
```
1. Get user's friends (Association query)
2. Get recent posts from friends (Object query)
3. Get likes/comments (Association query)
4. Return aggregated feed

TAO makes this FAST by:
- Caching associations
- Batch queries
- Predictive prefetching
```

---

## Massive Scale Solutions (2010-2015)

### 🌐 Global Distribution Challenge

**Problem:** Users worldwide need low latency

### Solution: Multi-Region Data Centers

```
┌─────────────────────────────────────────────────────┐
│                  Global Architecture                 │
├─────────────────────────────────────────────────────┤
│                                                       │
│  USA West        USA East        Europe        Asia  │
│     ↓               ↓               ↓            ↓   │
│  ┌──────┐       ┌──────┐       ┌──────┐    ┌──────┐│
│  │ DC 1 │←─────→│ DC 2 │←─────→│ DC 3 │←──→│ DC 4 ││
│  └──────┘       └──────┘       └──────┘    └──────┘│
│     ↑               ↑               ↑            ↑   │
│     └───────────────┴───────────────┴────────────┘   │
│              Cross-Region Replication                │
└─────────────────────────────────────────────────────┘
```

**Components:**

1. **Edge Locations (PoPs)**
   - Static content (images, CSS, JS)
   - CDN for media files
   - Closest to users

2. **Regional Data Centers**
   - Full application stack
   - Database replicas
   - Caching layers

3. **Master Data Centers**
   - Primary database writes
   - Data consistency coordination
   - Backup & disaster recovery

### Database Sharding Strategy

**The Problem:** Single MySQL database → 100+ million users

**Sharding Approach:**

```
                User Request
                     ↓
            ┌──────────────────┐
            │  Shard Router    │
            │  (Based on User  │
            │      ID)         │
            └──────────────────┘
                 ↓  ↓  ↓  ↓
        ┌────────┴───┴───┴───┴────────┐
        ↓         ↓         ↓          ↓
   ┌────────┐┌────────┐┌────────┐┌────────┐
   │Shard 1 ││Shard 2 ││Shard 3 ││Shard N │
   │Users   ││Users   ││Users   ││Users   │
   │1-10M   ││10-20M  ││20-30M  ││...     │
   └────────┘└────────┘└────────┘└────────┘
```

**Sharding Key Design:**
- **User ID based sharding** for most data
- Consistent hashing for distribution
- Virtual shards for rebalancing
- Co-location of related data

**Challenges & Solutions:**

| Challenge | Solution |
|-----------|----------|
| Cross-shard queries | Denormalization + Application-level joins |
| Rebalancing shards | Virtual sharding + gradual migration |
| Hot shards | Further subdivision + caching |
| Distributed transactions | Avoid when possible; eventual consistency |

---

## Modern Facebook Architecture (2015-Present)

### 🏗️ Complete System Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                        USER LAYER                              │
│  Web Browser / Mobile App / Instagram / WhatsApp              │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                      EDGE LAYER (CDN)                          │
│  • Static Content (Images, Videos, CSS, JS)                   │
│  • Edge PoPs (Points of Presence globally)                    │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                    API GATEWAY LAYER                           │
│  • Rate Limiting                                               │
│  • Authentication/Authorization                                │
│  • Request Routing                                             │
│  • Protocol Translation (REST, GraphQL)                        │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                   APPLICATION SERVICES                         │
├───────────────────────────────────────────────────────────────┤
│  News Feed  │  Messaging  │  Photos  │  Ads  │  Search       │
│  Service    │  Service    │  Service │  Svc  │  Service      │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                      CACHING LAYER                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Memcached   │  │     TAO      │  │    Redis     │       │
│  │   (Cache)    │  │ (Graph Cache)│  │  (Session)   │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                      DATA STORAGE LAYER                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │    MySQL     │  │    Cassandra │  │     HDFS     │       │
│  │   (Sharded)  │  │  (Messages)  │  │  (Analytics) │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │    Haystack  │  │     RocksDB  │  │     Scribe   │       │
│  │   (Photos)   │  │   (Key-Value)│  │    (Logs)    │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└───────────────────────────────────────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────────┐
│                   MESSAGE QUEUE / STREAMING                    │
│  • Kafka (Real-time data streams)                             │
│  • RabbitMQ (Job queues)                                      │
│  • Custom pub/sub systems                                     │
└───────────────────────────────────────────────────────────────┘
```

### Component Deep Dive

#### 1. **News Feed Architecture**

```
User Opens Facebook
        ↓
┌─────────────────────┐
│  News Feed Request  │
└─────────────────────┘
        ↓
┌─────────────────────┐
│  Check Cache Layer  │
│  (Materialized Feed)│
└─────────────────────┘
        ↓
    Cache Hit?
    ↓         ↓
   YES        NO
    ↓         ↓
 Return   ┌────────────────┐
  Feed    │  Generate Feed │
          │  1. Get Friends │
          │  2. Get Posts   │
          │  3. Rank Posts  │
          │  4. Cache Result│
          └────────────────┘
```

**Ranking Algorithm Factors:**
- Affinity score (how close are you to the poster?)
- Post weight (type: photo > status update)
- Time decay (recency matters)
- Engagement signals (likes, comments, shares)

**Performance Optimizations:**
- Pre-compute feeds for active users
- Incremental updates instead of full regeneration
- Parallel fetching of components
- Edge computing for personalization

#### 2. **Photo Storage: Haystack**

**The Problem:** Billions of photos, traditional file systems don't scale

**Haystack Design:**

```
Photo Upload Request
        ↓
┌───────────────────────────┐
│   Upload API Server       │
└───────────────────────────┘
        ↓
┌───────────────────────────┐
│   Store in Haystack       │
│   • Volume: Append-only   │
│   • Index: Metadata       │
│   • No small file problem │
└───────────────────────────┘
        ↓
┌───────────────────────────┐
│   Return Photo URL        │
│   (CDN + Cache enabled)   │
└───────────────────────────┘
```

**Key Innovations:**
- **Needle in Haystack:** Bundle multiple photos in large files
- **Direct access:** Avoid file system metadata overhead
- **CDN integration:** Serve from edge locations
- **Efficient storage:** 10 images per second uploaded!

**Volume Structure:**
```
┌────────────────────────────────────┐
│         Haystack Volume            │
├────────────────────────────────────┤
│ Photo1 | Photo2 | Photo3 | ...    │
│  (8KB) | (12KB) | (5KB)  |        │
└────────────────────────────────────┘
         ↑
    In-memory index
    (photo_id → offset)
```

#### 3. **Real-time Messaging**

**WhatsApp + Messenger Architecture:**

```
┌──────────────────────────────────────────┐
│         Client Applications              │
└──────────────────────────────────────────┘
              ↓ WebSocket/MQTT
┌──────────────────────────────────────────┐
│      Real-time Connection Server         │
│      (Maintains persistent connections)  │
└──────────────────────────────────────────┘
              ↓
┌──────────────────────────────────────────┐
│        Message Routing Layer             │
│        (Find recipient's server)         │
└──────────────────────────────────────────┘
              ↓
┌──────────────────────────────────────────┐
│      Cassandra (Message Storage)         │
│      • High write throughput             │
│      • Distributed storage               │
└──────────────────────────────────────────┘
```

**Key Features:**
- **End-to-end encryption** (Signal Protocol)
- **Offline message queue**
- **Delivery receipts** (sent, delivered, read)
- **Multi-device sync**
- **Billions of messages per day**

---

## Key Technologies & Innovations

### 1. **Custom Hardware**

Facebook doesn't just use commodity hardware—they design it!

**Open Compute Project (OCP):**
```
Traditional Server Rack     Facebook's Design
┌────────────────┐         ┌────────────────┐
│ Brand Server 1 │         │ Custom Server  │
│ Brand Server 2 │         │ Optimized for  │
│ Brand Server 3 │   →    │ Workload       │
│ ...            │         │ 38% more       │
│ Expensive      │         │ power efficient│
└────────────────┘         └────────────────┘
```

**Benefits:**
- 38% more energy efficient
- 24% cost reduction
- Better performance per watt
- Modular design

### 2. **HHVM (HipHop Virtual Machine)**

Evolution from HPHPc:

```
PHP Code → HHVM → JIT Compilation → Machine Code
                     (Just-in-Time)
```

**Advantages over HPHPc:**
- No pre-compilation needed
- Easier debugging
- Hot code reloading
- Still 9x faster than standard PHP

### 3. **GraphQL**

**Problem:** Mobile apps make too many REST API calls

```
❌ REST Approach (Multiple Requests)
GET /users/123
GET /users/123/posts
GET /posts/456/comments
GET /posts/789/likes
(4 round trips!)

✅ GraphQL (Single Request)
POST /graphql
{
  user(id: "123") {
    name
    posts {
      title
      comments { text }
      likes { count }
    }
  }
}
(1 round trip!)
```

**Benefits:**
- Reduce network overhead
- Fetch exactly what you need
- Type-safe queries
- Better mobile performance

### 4. **React & React Native**

**Web Performance:**
```
Traditional Web App          React App
├─ Server Render           ├─ Virtual DOM
├─ Full Page Reload        ├─ Component Updates
├─ Slow Updates            ├─ Fast Re-renders
└─ Poor UX                 └─ Smooth UX
```

**Mobile Efficiency:**
- **React Native:** Write once, deploy on iOS + Android
- Native performance
- Hot reloading for development
- Shared codebase = faster development

### 5. **Machine Learning Infrastructure**

```
┌─────────────────────────────────────────┐
│         ML Use Cases at Facebook        │
├─────────────────────────────────────────┤
│ • News Feed Ranking                     │
│ • Ad Targeting                          │
│ • Content Moderation                    │
│ • Friend Suggestions                    │
│ • Photo Tagging                         │
│ • Translation                           │
│ • Search Ranking                        │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│         FBLearner Flow                  │
│    (ML Platform)                        │
│  • Feature Engineering                  │
│  • Model Training                       │
│  • A/B Testing                          │
│  • Deployment                           │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│        PyTorch (Deep Learning)          │
│    • Custom CUDA kernels                │
│    • Distributed training               │
│    • GPU clusters                       │
└─────────────────────────────────────────┘
```

---

## System Design Interview Insights

### 🎯 Key Takeaways for Interviews

#### 1. **Start Simple, Scale Gradually**

```
Interview Approach:

Step 1: Basic Architecture
├─ Web Server
├─ Database
└─ Load Balancer

Step 2: Identify Bottlenecks
├─ Database reads → Add caching
├─ Database writes → Add sharding
└─ Static content → Add CDN

Step 3: Scale Further
├─ Microservices
├─ Message queues
└─ Geographic distribution
```

#### 2. **Focus on Trade-offs**

| Decision | Trade-off |
|----------|-----------|
| **Caching** | Speed ↑ but Stale Data Risk ↑ |
| **Denormalization** | Read Speed ↑ but Write Complexity ↑ |
| **Sharding** | Scalability ↑ but Query Complexity ↑ |
| **Async Processing** | Throughput ↑ but Complexity ↑ |
| **Eventual Consistency** | Availability ↑ but Strong Consistency ↓ |

#### 3. **Numbers You Should Know**

```
Request Latency Targets:
├─ Database Query: < 10ms
├─ Cache Access: < 1ms
├─ API Call: < 100ms
├─ Page Load: < 1s
└─ Video Start: < 2s

Throughput:
├─ DB: 10K QPS per server
├─ Cache: 1M QPS per server
├─ Web Server: 1K RPS
└─ CDN: 10K+ RPS per edge

Storage:
├─ Photo: 200KB average
├─ Video: 50MB average
├─ Post: 1KB
└─ Message: 100 bytes
```

#### 4. **Facebook-Specific Design Patterns**

**Pattern 1: Feed Ranking**
```python
def rank_feed(user_id):
    # 1. Get candidate posts
    friends = get_friends(user_id)
    posts = get_recent_posts(friends, limit=1000)
    
    # 2. Score each post
    scored_posts = []
    for post in posts:
        score = calculate_score(
            affinity=get_affinity(user_id, post.author),
            weight=get_post_weight(post.type),
            time_decay=get_time_decay(post.timestamp),
            engagement=get_engagement_signals(post)
        )
        scored_posts.append((post, score))
    
    # 3. Return top N
    return sorted(scored_posts, key=lambda x: x[1], reverse=True)[:20]
```

**Pattern 2: Distributed Counting**
```python
# Problem: Count likes on a post (billions of posts, millions of likes)

# ❌ Bad: Increment counter on every like
UPDATE posts SET like_count = like_count + 1 WHERE id = ?
# (Database bottleneck!)

# ✅ Good: Buffer + Batch Update
class LikeCounter:
    def __init__(self):
        self.buffer = defaultdict(int)  # In-memory
        self.redis = Redis()
        
    def increment_like(self, post_id):
        self.buffer[post_id] += 1
        
        # Flush to Redis every N likes or every T seconds
        if self.buffer[post_id] >= FLUSH_THRESHOLD:
            self.flush_to_redis(post_id)
    
    def flush_to_redis(self, post_id):
        # Atomic increment in Redis
        self.redis.incrby(f"post:{post_id}:likes", self.buffer[post_id])
        self.buffer[post_id] = 0
        
    # Background job: Redis → Database
    def sync_to_database(self):
        # Batch update every minute
        for post_id in self.redis.keys("post:*:likes"):
            count = self.redis.get(post_id)
            update_database(post_id, count)
```

**Pattern 3: Fan-out on Write vs Read**

```
Fan-out on Write (Facebook uses this for News Feed):
┌────────────┐
│ User Posts │
└────────────┘
      ↓
Push to all followers' feeds immediately
      ↓
┌─────────────────────────────┐
│ Follower 1's Feed Cache     │
│ Follower 2's Feed Cache     │
│ Follower 3's Feed Cache     │
│ ...                         │
└─────────────────────────────┘
      ↓
Fast reads (already materialized)

Trade-off:
✅ Fast reads
✅ Simple read logic
❌ Slow writes (for users with many followers)
❌ More storage
```

#### 5. **System Design Template**

```
1. REQUIREMENTS CLARIFICATION (5 min)
   ├─ Functional: What features?
   ├─ Non-functional: Scale, latency, availability?
   └─ Constraints: Budget, time, tech stack?

2. CAPACITY ESTIMATION (5 min)
   ├─ Users: DAU, MAU
   ├─ Traffic: QPS (read/write ratio)
   ├─ Storage: Size per record × records
   └─ Bandwidth: Upload/download

3. API DESIGN (5 min)
   ├─ Define endpoints
   ├─ Request/response format
   └─ Authentication

4. DATA MODEL (5 min)
   ├─ Schema design
   ├─ Relationships
   └─ Storage choice (SQL/NoSQL)

5. HIGH-LEVEL DESIGN (10 min)
   ├─ Draw architecture diagram
   ├─ Explain data flow
   └─ Identify components

6. DETAILED DESIGN (15 min)
   ├─ Deep dive on 2-3 components
   ├─ Discuss trade-offs
   └─ Handle edge cases

7. SCALE & OPTIMIZE (5 min)
   ├─ Caching strategy
   ├─ Database sharding
   ├─ Load balancing
   └─ CDN for static content
```

---

## Lessons Learned

### 💡 Strategic Lessons

#### 1. **Vertical Scaling → Horizontal Scaling**
- Start simple with vertical scaling
- Move to horizontal when vertical hits limits
- Design for horizontal scaling from start (stateless services)

#### 2. **Monolith → Microservices**
```
Evolution Path:
Monolith (0-1M users)
    ↓
Modular Monolith (1-10M users)
    ↓
Service-Oriented Architecture (10-100M users)
    ↓
Microservices (100M+ users)
```

#### 3. **Build vs Buy**

| Stage | Approach | Reason |
|-------|----------|--------|
| Early | **Buy/Use OSS** | Speed to market |
| Growing | **Customize OSS** | Specific needs |
| Scale | **Build Custom** | No off-the-shelf solution |

**Examples:**
- **Buy:** MySQL, Memcached, Linux
- **Customize:** HHVM (PHP), TAO (MySQL cache)
- **Build:** Haystack, React, GraphQL

#### 4. **Data Locality Matters**

```
Co-locate Related Data:

User's Profile + Posts + Photos
        ↓
    Same Shard
        ↓
Avoid Cross-shard Joins!
```

#### 5. **Cache Invalidation is Hard**

```
Strategies Facebook Uses:

1. Time-based Expiration
   └─ Good for: Content that changes infrequently

2. Write-through Cache
   └─ Good for: Critical consistency (TAO)

3. Event-driven Invalidation
   └─ Good for: Real-time updates

4. Probabilistic Invalidation
   └─ Good for: Reducing thundering herd
```

### 🚨 Common Pitfalls to Avoid

#### 1. **Premature Optimization**
```
❌ Don't: Build for 1B users on day 1
✅ Do: Build for current scale + 10x growth
```

#### 2. **Ignoring Monitoring**
```
Must Have:
├─ Metrics (Latency, Error rate, Throughput)
├─ Logging (Debug production issues)
├─ Alerting (Proactive issue detection)
└─ Dashboards (Real-time visibility)
```

#### 3. **Single Points of Failure**
```
Eliminate SPOFs:
├─ Load Balancer → Multiple LBs
├─ Database → Replication
├─ Cache → Distributed cache
└─ Region → Multi-region
```

#### 4. **Not Considering Failure Modes**

```
Ask These Questions:
├─ What if database goes down?
├─ What if cache is unavailable?
├─ What if network partition happens?
├─ What if deployment fails?
└─ What if data center loses power?
```

---

## Key Interview Questions & Answers

### Q1: How does Facebook handle billions of photos?

**Answer:**
- **Haystack:** Custom file system that stores multiple photos in large files
- **CDN:** Edge caching for fast delivery
- **Multiple Sizes:** Generate and store different resolutions
- **Lazy Deletion:** Mark deleted instead of immediate removal
- **Replication:** 3x replication for durability

### Q2: How would you design Facebook's News Feed?

**Answer Structure:**
1. **Requirements:** Personalized feed, low latency (<1s), billions of users
2. **Data Model:** Users, Posts, Friendships, Likes, Comments
3. **Architecture:**
   - Fan-out on write for most users
   - Hybrid approach for celebrities (fan-out on read)
   - Ranking algorithm with ML
   - Heavy caching at multiple layers
4. **Challenges:** Scale, freshness, personalization

### Q3: How does Facebook achieve high availability?

**Answer:**
- **Multi-region deployment:** Data centers worldwide
- **Replication:** Database replication across regions
- **Caching:** Multiple layers reduce database dependency
- **Graceful degradation:** Serve stale data if fresh unavailable
- **Circuit breakers:** Prevent cascade failures
- **Chaos engineering:** Test failure scenarios regularly

### Q4: Explain Facebook's sharding strategy

**Answer:**
- **Vertical sharding:** Separate tables (users, posts, photos)
- **Horizontal sharding:** Split tables by user ID
- **Consistent hashing:** Even distribution
- **Virtual shards:** Easy rebalancing
- **Co-location:** Keep related data together

### Q5: How would you handle a celebrity with 100M followers posting?

**Answer:**
- **Problem:** Can't fan-out to 100M users instantly
- **Solution:**
  - Use fan-out on read for celebrities
  - Cache aggregated feed
  - Pull celebrity posts during feed generation
  - Use message queues for async processing
  - Implement rate limiting

---

## Real-World Scenarios & Solutions

### Scenario 1: Thundering Herd Problem

**Problem:** Cache expires, thousands of requests hit database

```
Time 0: Cache expires
Time 1: 10,000 requests hit cache
Time 2: All 10,000 go to database
Time 3: Database overloaded!
```

**Facebook's Solution:**

```python
import random

def get_with_jitter(key, ttl):
    value = cache.get(key)
    
    if value is None:
        # Acquire lock to prevent multiple DB hits
        lock = distributed_lock.acquire(f"lock:{key}", timeout=5)
        
        if lock:
            try:
                value = database.get(key)
                # Add random jitter to TTL
                jittered_ttl = ttl + random.randint(0, ttl * 0.1)
                cache.set(key, value, jittered_ttl)
            finally:
                lock.release()
        else:
            # Wait and retry from cache
            time.sleep(0.1)
            return get_with_jitter(key, ttl)
    
    return value
```

**Key Techniques:**
- Probabilistic early expiration
- Distributed locking
- TTL jitter
- Negative caching

### Scenario 2: Hot Shard Problem

**Problem:** One shard gets significantly more traffic

```
Normal Distribution:
Shard 1: 10K QPS ✅
Shard 2: 10K QPS ✅
Shard 3: 10K QPS ✅

Hot Shard Problem:
Shard 1: 10K QPS ✅
Shard 2: 50K QPS ❌ Overloaded!
Shard 3: 10K QPS ✅
```

**Solutions:**

1. **Further Subdivide Hot Shard**
   ```
   Shard 2 → Shard 2a + Shard 2b
   50K QPS → 25K QPS + 25K QPS
   ```

2. **Add More Cache**
   - Increase cache hit rate for hot data
   - Use local cache on app servers

3. **Read Replicas**
   - Add more read replicas for hot shard
   - Route reads to replicas

### Scenario 3: Cross-Datacenter Consistency

**Problem:** User in Europe updates profile, user in Asia should see update

**Facebook's Approach:**

```
┌─────────────────────────────────────────┐
│         Master Datacenter (US)          │
│         (Source of Truth)               │
└─────────────────────────────────────────┘
              ↓
    Async Replication (seconds delay)
              ↓
┌──────────────┐        ┌──────────────┐
│   Europe DC  │        │   Asia DC    │
│   (Replica)  │        │   (Replica)  │
└──────────────┘        └──────────────┘
```

**Strategies:**
- **Eventual consistency:** Accept seconds of delay
- **Conflict resolution:** Last write wins
- **Critical data:** Sync to master first
- **User affinity:** Route to same DC when possible

### Scenario 4: Deployment Without Downtime

**Zero-Downtime Deployment Process:**

```
Step 1: Deploy to 1% of servers
        ↓
     Monitor metrics
        ↓
Step 2: Deploy to 10% (if healthy)
        ↓
     Monitor metrics
        ↓
Step 3: Deploy to 50% (if healthy)
        ↓
     Monitor metrics
        ↓
Step 4: Deploy to 100% (if healthy)

At any point: Rollback if issues detected
```

**Key Practices:**
- **Blue-Green Deployment:** Two identical environments
- **Canary Releases:** Gradual rollout
- **Feature Flags:** Toggle features without deployment
- **Database Migrations:** Backward compatible changes

---

## Performance Optimization Techniques

### 1. **Connection Pooling**

```python
# ❌ Bad: Create new connection per request
def get_user(user_id):
    conn = mysql.connect()
    user = conn.query("SELECT * FROM users WHERE id = ?", user_id)
    conn.close()
    return user

# ✅ Good: Reuse connections
connection_pool = mysql.ConnectionPool(size=100)

def get_user(user_id):
    with connection_pool.get_connection() as conn:
        return conn.query("SELECT * FROM users WHERE id = ?", user_id)
```

### 2. **Batch API Calls**

```python
# ❌ Bad: N+1 queries
def get_posts_with_authors(post_ids):
    posts = []
    for post_id in post_ids:
        post = get_post(post_id)  # 1 DB call
        author = get_user(post.author_id)  # 1 DB call
        posts.append((post, author))
    return posts  # Total: 2N DB calls!

# ✅ Good: Batch queries
def get_posts_with_authors(post_ids):
    posts = get_posts_batch(post_ids)  # 1 DB call
    author_ids = [p.author_id for p in posts]
    authors = get_users_batch(author_ids)  # 1 DB call
    authors_dict = {a.id: a for a in authors}
    return [(p, authors_dict[p.author_id]) for p in posts]
    # Total: 2 DB calls!
```

### 3. **Async Processing**

```python
# Sync: User waits for everything
def create_post_sync(user_id, content):
    post = save_post(user_id, content)  # 100ms
    notify_followers(user_id, post.id)  # 500ms ⏰ SLOW
    update_search_index(post.id)       # 200ms ⏰ SLOW
    return post
# Total: 800ms wait time

# Async: User gets instant response
def create_post_async(user_id, content):
    post = save_post(user_id, content)  # 100ms
    
    # Queue background jobs
    job_queue.enqueue(notify_followers, user_id, post.id)
    job_queue.enqueue(update_search_index, post.id)
    
    return post
# Total: 100ms wait time! ✅
```

### 4. **Query Optimization**

```sql
-- ❌ Bad: Full table scan
SELECT * FROM posts 
WHERE author_id = 123 
ORDER BY created_at DESC;

-- ✅ Good: Use composite index
CREATE INDEX idx_author_created ON posts(author_id, created_at DESC);

SELECT * FROM posts 
WHERE author_id = 123 
ORDER BY created_at DESC
LIMIT 20;
```

### 5. **Denormalization**

```
Normalized (Many Joins):
┌────────┐     ┌────────┐     ┌──────────┐
│ Posts  │ ──→ │ Users  │     │ Comments │
└────────┘     └────────┘     └──────────┘
      ↓            ↓                ↓
   post_id     author_id        comment_id

Query: 3 JOINs required ⏰ SLOW

Denormalized (No Joins):
┌────────────────────────────┐
│ Posts (with author name)   │
├────────────────────────────┤
│ post_id                    │
│ author_id                  │
│ author_name (denormalized) │
│ comment_count (cached)     │
└────────────────────────────┘

Query: Single table lookup ✅ FAST
```

---

## Final Checklist for System Design Interviews

### ✅ Before the Interview

- [ ] Review common system design patterns
- [ ] Practice capacity estimation calculations
- [ ] Study real-world architectures (Facebook, Twitter, Netflix)
- [ ] Understand CAP theorem and trade-offs
- [ ] Know performance numbers (latency, throughput)

### ✅ During the Interview

- [ ] Clarify requirements before designing
- [ ] Start with simple design, then scale
- [ ] Draw clear diagrams
- [ ] Explain trade-offs for each decision
- [ ] Consider failure scenarios
- [ ] Discuss monitoring and observability
- [ ] Ask clarifying questions
- [ ] Think out loud

### ✅ Key Topics to Cover

- [ ] Load balancing strategy
- [ ] Caching layers and invalidation
- [ ] Database sharding approach
- [ ] CDN for static content
- [ ] Message queues for async processing
- [ ] Monitoring and alerting
- [ ] Security considerations
- [ ] Disaster recovery

---

## Additional Resources

### 📚 Facebook Engineering Blog
- [Facebook Engineering](https://engineering.fb.com/)
- TAO: Facebook's Distributed Data Store
- Scaling Memcached at Facebook
- Finding a Needle in Haystack: Facebook's Photo Storage

### 🎥 Tech Talks
- Facebook @Scale Conference talks
- How Facebook Serves Billions of Photos
- Building Real-time Infrastructure at Facebook

### 📖 Papers
- "TAO: Facebook's Distributed Data Store for the Social Graph"
- "f4: Facebook's Warm BLOB Storage System"
- "Scaling Memcache at Facebook"

### 🔧 Open Source Projects
- React / React Native
- GraphQL
- PyTorch
- Folly (C++ library)
- RocksDB
- Presto

---

## Conclusion

Facebook's architecture evolution demonstrates key principles for building scalable systems:

1. **Start Simple:** Begin with proven technologies
2. **Iterate Based on Need:** Don't over-engineer early
3. **Measure Everything:** Data-driven optimization
4. **Build vs Buy:** Know when to use existing solutions
5. **Embrace Trade-offs:** No perfect solution exists
6. **Think Long-term:** Consider maintenance and evolution
7. **Learn from Failures:** Use issues as learning opportunities

**Remember:** Facebook wasn't built in a day. They evolved from a simple PHP app to one of the world's most complex distributed systems through continuous iteration, learning, and optimization.

For your system design interview, focus on understanding the **why** behind each architectural decision, not just the **what**. Demonstrate your ability to reason about trade-offs, handle scale, and design resilient systems.

---

**Last Updated:** November 2025  
**Document Version:** 1.0  

*Good luck with your system design interviews! 🚀*
