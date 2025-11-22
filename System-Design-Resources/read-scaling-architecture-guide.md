# Read Scaling Architecture: System Design Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Read Scaling Fundamentals](#read-scaling-fundamentals)
3. [Architecture Patterns](#architecture-patterns)
4. [Caching Architecture](#caching-architecture)
5. [Database Read Scaling](#database-read-scaling)
6. [Content Delivery Architecture](#content-delivery-architecture)
7. [Search & Indexing Systems](#search--indexing-systems)
8. [Trade-offs & Decision Framework](#trade-offs--decision-framework)
9. [Failure Modes & Resilience](#failure-modes--resilience)
10. [Real-World Architectures](#real-world-architectures)
11. [Interview Strategy](#interview-strategy)

---

## Introduction

Read scaling is the art of serving millions of queries per second while maintaining low latency and high availability. Unlike writes, reads can be cached, replicated, and distributed with eventual consistency, making them highly scalable.

**Core Principles**:
- **Distribute the Load**: Spread reads across multiple servers
- **Cache Aggressively**: Most data is read far more than written
- **Accept Eventual Consistency**: Slightly stale data is often acceptable
- **Layer Your Defenses**: Multiple caching layers reduce load

**Why Read Scaling Matters**:
```
Typical System Load Distribution:
- 90-95% of requests are reads
- 5-10% of requests are writes
- Peak read traffic can be 10x average
- User experience depends on read latency
```

---

## Read Scaling Fundamentals

### The Read Path Problem

**Without Optimization**:
```
Every Request:
User Request → Load Balancer → App Server → Database Query
             → Wait for Disk I/O → Process → Return
             
Result: 
- 50-100ms latency
- Database is bottleneck
- Limited to ~10,000 QPS per database
```

**With Optimization**:
```
Request Flow with Layers:
User Request → CDN (Edge Cache)          [80% hit rate, <10ms]
             → Application Cache (Redis)  [15% hit rate, <5ms]
             → Database Read Replica      [5% miss, ~50ms]
             
Result:
- 95% requests served in <10ms
- Database load reduced 95%
- Can handle 1M+ QPS
```

### Scalability Targets

| Scale | QPS | Users | Architecture |
|-------|-----|-------|--------------|
| **Small** | <1K | <100K | Single database + app cache |
| **Medium** | 1K-10K | 100K-1M | Read replicas + Redis |
| **Large** | 10K-100K | 1M-10M | CDN + sharding + multi-layer cache |
| **Massive** | 100K-1M+ | 10M+ | Global CDN + regional sharding + CQRS |

---

## Architecture Patterns

### Pattern 1: Layered Caching Architecture

**Concept**: Multiple caching layers intercept requests before hitting database

```
┌─────────────────────────────────────────────────┐
│                    Client                        │
└───────────────────────┬─────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────┐
│  Layer 1: CDN (Edge Cache)                      │
│  - Serves static content                        │
│  - Geographic distribution                      │
│  - 80-90% of static requests                    │
│  - <20ms latency                                │
└───────────────────────┬─────────────────────────┘
                        │ (Cache Miss)
┌───────────────────────▼─────────────────────────┐
│  Layer 2: API Gateway Cache                     │
│  - API response caching                         │
│  - Rate limiting                                │
│  - Authentication                               │
│  - 60-70% of API requests                       │
└───────────────────────┬─────────────────────────┘
                        │ (Cache Miss)
┌───────────────────────▼─────────────────────────┐
│  Layer 3: Application Cache (Redis/Memcached)   │
│  - Hot data caching                             │
│  - Session storage                              │
│  - Computed results                             │
│  - 70-80% hit rate                              │
│  - <5ms latency                                 │
└───────────────────────┬─────────────────────────┘
                        │ (Cache Miss)
┌───────────────────────▼─────────────────────────┐
│  Layer 4: Database Query Cache                  │
│  - Recently executed queries                    │
│  - 50-60% hit rate                              │
│  - <10ms latency                                │
└───────────────────────┬─────────────────────────┘
                        │ (Cache Miss)
┌───────────────────────▼─────────────────────────┐
│  Layer 5: Database Read Replicas                │
│  - Load balanced across replicas                │
│  - ~50ms latency                                │
└─────────────────────────────────────────────────┘
```

**Effectiveness Calculation**:
```
Assume 1,000,000 requests/sec:
- Layer 1 (CDN): 800,000 requests (80%)
- Layer 2 (API Gateway): 120,000 requests (60% of 200K)
- Layer 3 (App Cache): 56,000 requests (70% of 80K)
- Layer 4 (DB Query Cache): 12,000 requests (50% of 24K)
- Layer 5 (DB Replicas): 12,000 requests (remaining)

Database Load Reduction: 99% (12K vs 1M requests)
Average Latency: ~15ms (weighted average)
```

### Pattern 2: Read Replica Architecture

**Single Master Pattern**:
```
┌──────────────────────────────────────────────┐
│              Load Balancer                    │
│  - Routes writes to master                   │
│  - Routes reads to replicas (round-robin)    │
└─────┬───────────────────────┬────────────────┘
      │                       │
      │ Writes                │ Reads
      │                       │
      ▼                       ▼
┌─────────────┐         ┌──────────────────────┐
│   Master    │         │   Read Replicas      │
│  Database   │─────────▶│  - Replica 1        │
│             │ Async   │  - Replica 2        │
│  - All      │ Repl.   │  - Replica 3        │
│    Writes   │         │  - Replica 4        │
│  - Critical │         │  - Replica 5        │
│    Reads    │         └──────────────────────┘
└─────────────┘
```

**Scaling Numbers**:
```
Single Master:        10,000 reads/sec
1 Master + 5 Replicas: 50,000 reads/sec
1 Master + 10 Replicas: 100,000 reads/sec

Linear Scaling: Each replica adds ~10K QPS
```

**Geographic Distribution**:
```
┌────────────────────────────────────────────────┐
│            Global Architecture                 │
└────────────────────────────────────────────────┘
         │
         ├─── US-EAST Region
         │    ├─ Master DB (Primary)
         │    └─ Read Replicas (3)
         │
         ├─── US-WEST Region
         │    └─ Read Replicas (3)
         │
         ├─── EU Region
         │    └─ Read Replicas (2)
         │
         └─── ASIA Region
              └─ Read Replicas (2)

Benefits:
- Low latency for local users (<50ms)
- Disaster recovery across regions
- Compliance with data residency laws
```

### Pattern 3: CQRS (Command Query Responsibility Segregation)

**Architecture**:
```
┌──────────────────────────────────────────────┐
│              API Gateway                      │
└──────────┬───────────────────────┬───────────┘
           │                       │
    Writes │                       │ Reads
           │                       │
           ▼                       ▼
┌──────────────────┐      ┌──────────────────┐
│  Write Model     │      │   Read Model     │
│                  │      │                  │
│  - PostgreSQL    │      │  - MongoDB       │
│  - Normalized    │      │  - Denormalized  │
│  - ACID          │      │  - Optimized     │
│  - Transactional │      │  - Fast Queries  │
└────────┬─────────┘      └────────▲─────────┘
         │                          │
         │    Event Stream          │
         └──────────────────────────┘
         (Kafka, RabbitMQ, etc.)
```

**When to Use CQRS**:
- Read and write patterns are very different
- Need to optimize queries independently
- Multiple read models needed (analytics, search, etc.)
- Can accept eventual consistency
- Scale reads and writes independently

**Benefits**:
```
Separation of Concerns:
- Write Model: Optimized for data integrity
- Read Model: Optimized for query performance

Independent Scaling:
- Scale write DB for transaction processing
- Scale read DB for query throughput
- Different technologies for each

Multiple Views:
- SQL read model for reporting
- NoSQL read model for APIs
- Search engine for full-text search
- Cache for hot data
```

### Pattern 4: Materialized Views

**Concept**: Pre-compute expensive queries

```
Traditional Query (Expensive):
┌────────────────────────────────────┐
│ SELECT u.id, u.name,               │
│        COUNT(o.id) as order_count, │
│        SUM(o.total) as total_spent │
│ FROM users u                       │
│ JOIN orders o ON u.id = o.user_id │
│ GROUP BY u.id, u.name              │
└────────────────────────────────────┘
Execution Time: 5-10 seconds on large datasets

Materialized View (Fast):
┌────────────────────────────────────┐
│ CREATE MATERIALIZED VIEW           │
│ user_statistics AS                 │
│   [Above Query]                    │
│                                    │
│ SELECT * FROM user_statistics      │
│ WHERE user_id = 123                │
└────────────────────────────────────┘
Execution Time: <100ms

Refresh Strategy:
- On-demand: Manual refresh when needed
- Scheduled: Cron job every hour
- Incremental: Update only changed data
- Trigger-based: Update on data changes
```

**Use Cases**:
- Analytics dashboards
- Reporting systems
- Aggregated metrics
- Complex JOIN queries
- Historical analysis

---

## Caching Architecture

### Cache Strategy Matrix

| Data Type | TTL | Eviction | Storage | Update Pattern |
|-----------|-----|----------|---------|----------------|
| **User Sessions** | 30 min | LRU | Redis | Write-through |
| **Product Catalog** | 1 hour | LFU | Memcached | Cache-aside |
| **User Profiles** | 10 min | LRU | Redis | Write-through |
| **Hot Content** | 5 min | LFU | Redis | Cache-aside |
| **Static Assets** | 7 days | FIFO | CDN | Cache-aside |
| **Search Results** | 5 min | LRU | Redis | Cache-aside |
| **Analytics** | 1 hour | TTL | Redis | Read-through |

### Cache-Aside Pattern (Lazy Loading)

**Architecture**:
```
┌─────────────────────────────────────────┐
│         Application Server               │
└───────┬─────────────────────────────────┘
        │
        │ 1. Check Cache
        ▼
┌─────────────────┐
│     Cache       │
│    (Redis)      │
└────┬────────────┘
     │
     ├─ Hit? Return data (80-90% of requests)
     │
     └─ Miss?
        │ 2. Query Database
        ▼
   ┌─────────────────┐
   │    Database     │
   └────┬────────────┘
        │ 3. Store in cache
        │ 4. Return to app
        ▼
```

**Characteristics**:
- **Pros**: Only caches what's accessed, cache failures don't break app
- **Cons**: Cache miss penalty (database query), cache stampede risk
- **Best For**: Read-heavy with unpredictable access patterns

### Write-Through Cache Pattern

**Architecture**:
```
┌─────────────────────────────────────────┐
│         Application Server               │
└───────┬─────────────────────┬───────────┘
        │                     │
        │ Write               │ Read
        ▼                     ▼
┌─────────────────┐    ┌─────────────────┐
│     Cache       │◀───│     Cache       │
│    (Redis)      │    │    (Redis)      │
└────┬────────────┘    └─────────────────┘
     │ Sync Write
     │
     ▼
┌─────────────────┐
│    Database     │
└─────────────────┘
```

**Characteristics**:
- **Pros**: Cache always consistent, no miss penalty
- **Cons**: Write latency increased, caches unused data
- **Best For**: Strong consistency requirements, predictable access patterns

### Cache Stampede Prevention

**Problem**:
```
Scenario: Cache expires for popular item

Time 0: Cache expires
Time 1: 1000 concurrent requests arrive
Time 2: All 1000 requests miss cache
Time 3: All 1000 requests query database simultaneously
Result: Database overwhelmed, cascading failure

Database Load: 1000x normal
Response Time: 10-100x slower
```

**Solution: Single-Flight Pattern**:
```
┌──────────────────────────────────────────┐
│     Request Deduplication Layer          │
│                                          │
│  Multiple concurrent requests for        │
│  same key → Only ONE database query      │
│           → Others wait for result       │
│           → All get same cached result   │
└──────────────────────────────────────────┘

Benefits:
- Database sees only 1 request
- All clients get result
- Cache populated once
- No thundering herd
```

### Distributed Caching Architecture

**Consistent Hashing**:
```
┌─────────────────────────────────────────┐
│      Hash Ring (Consistent Hashing)     │
│                                         │
│    Cache Node 1                        │
│    (Handles: Keys 0-333)               │
│         │                              │
│         ├─ Virtual Nodes for          │
│         │  better distribution        │
│         │                              │
│    Cache Node 2                        │
│    (Handles: Keys 334-666)             │
│         │                              │
│    Cache Node 3                        │
│    (Handles: Keys 667-999)             │
└─────────────────────────────────────────┘

Adding Node:
- Only ~25% of keys rehashed
- Other 75% remain in place
- Minimal disruption

Removing Node:
- Keys redistributed to adjacent nodes
- No total cache invalidation
```

---

## Database Read Scaling

### Replication Strategies

**Synchronous Replication**:
```
Write Flow:
Master receives write
  │
  ├─> Sends to Replica 1 ──> Wait for ACK
  ├─> Sends to Replica 2 ──> Wait for ACK  
  └─> Sends to Replica 3 ──> Wait for ACK
  │
  └─> All ACKs received → Commit → Return Success

Characteristics:
- Latency: High (100-200ms)
- Consistency: Strong (no data loss)
- Availability: Lower (all replicas must be up)
- Use Case: Financial transactions, critical data
```

**Asynchronous Replication**:
```
Write Flow:
Master receives write
  │
  ├─> Commit immediately
  ├─> Return Success (50ms)
  │
  └─> Async: Send to replicas in background
      │
      ├─> Replica 1 (lag: 10-100ms)
      ├─> Replica 2 (lag: 10-100ms)
      └─> Replica 3 (lag: 10-100ms)

Characteristics:
- Latency: Low (50ms)
- Consistency: Eventual (possible data loss)
- Availability: Higher (master doesn't wait)
- Use Case: Social feeds, content delivery
```

**Semi-Synchronous Replication**:
```
Write Flow:
Master receives write
  │
  ├─> Sends to ALL replicas
  ├─> Wait for ACK from at least ONE replica
  ├─> Commit
  └─> Return Success (70-100ms)
  │
  └─> Other replicas catch up async

Characteristics:
- Latency: Medium (70-100ms)
- Consistency: Strong-ish (minimal data loss)
- Availability: Medium (need 1 replica up)
- Use Case: E-commerce, user data
```

### Handling Replication Lag

**Read-Your-Writes Consistency**:
```
Problem:
User posts comment → Writes to master
User refreshes page → Reads from replica (lagging)
Result: User doesn't see their own comment

Solution:
┌──────────────────────────────────────┐
│  Track user's last write version    │
│  Route user's reads to master until │
│  replicas catch up to that version  │
└──────────────────────────────────────┘

Implementation:
1. Master returns version number on write
2. Store version in user session
3. Check replica version before routing read
4. Route to master if replica lagging
5. Clear after timeout (5-10 seconds)
```

**Monotonic Reads**:
```
Problem:
Request 1 → Replica A (version 100) → See post
Request 2 → Replica B (version 98) → Don't see post
Result: Data appears to go backwards in time

Solution:
┌──────────────────────────────────────┐
│  Sticky Sessions                     │
│  Route all user requests to same     │
│  replica for session duration        │
└──────────────────────────────────────┘

Benefits:
- Consistent view for user
- No time travel
- Simple to implement (session affinity)
```

### Database Sharding Strategies

**Hash-Based Sharding**:
```
Architecture:
┌────────────────────────────────────┐
│  Shard Router (Application Layer)  │
└────────────┬───────────────────────┘
             │
   Hash(user_id) % num_shards
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌─────────┐     ┌─────────┐
│ Shard 1 │     │ Shard 2 │
│ Users:  │     │ Users:  │
│ 0-1M    │     │ 1M-2M   │
└─────────┘     └─────────┘

Pros:
+ Even distribution
+ Predictable performance
+ Simple to implement

Cons:
- Cross-shard queries difficult
- Resharding complex
- Range queries span shards
```

**Geographic Sharding**:
```
Architecture:
┌────────────────────────────────────┐
│    Geo-Routing Layer               │
└────────┬───────────────────────────┘
         │
    Based on user location
         │
    ┌────┴────┬────────┬────────┐
    │         │        │        │
    ▼         ▼        ▼        ▼
┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐
│US-East│ │US-West│ │ Europe│ │ Asia  │
│ Shard │ │ Shard │ │ Shard │ │ Shard │
└───────┘ └───────┘ └───────┘ └───────┘

Pros:
+ Low latency (local access)
+ Data residency compliance
+ Natural isolation

Cons:
- Global queries complex
- Uneven distribution possible
- Cross-region complexity
```

---

## Content Delivery Architecture

### CDN Architecture Patterns

**Pull-Based CDN**:
```
Flow:
1. User requests content from CDN edge
2. CDN edge checks local cache
3. If miss: CDN pulls from origin
4. CDN caches content
5. CDN serves to user
6. Future requests served from cache

┌──────┐    ┌─────────┐    ┌────────┐
│ User │───▶│CDN Edge │───▶│ Origin │
└──────┘    └─────────┘    └────────┘
            Cache Miss:
            Pull & Cache

Characteristics:
- Lazy population
- Cache on demand
- Origin load on first request
- Suitable for long-tail content
```

**Push-Based CDN**:
```
Flow:
1. Origin pushes content to all CDN edges
2. Content pre-populated before requests
3. User requests always hit cache
4. Zero origin load for cached content

┌────────┐    Push    ┌─────────┐
│ Origin │───────────▶│CDN Edge │
└────────┘            └─────────┘
                           │
                      Proactive
                      Population

Characteristics:
- Pre-population
- Zero cache misses
- Higher CDN costs
- Suitable for popular content
```

### CDN Tiering Strategy

**Multi-Tier Architecture**:
```
┌──────────────────────────────────────┐
│         Tier 1: Edge Nodes           │
│  - Closest to users                  │
│  - Small cache (10-100GB)            │
│  - TTL: 1-24 hours                   │
│  - Handles 80-90% of requests        │
└────────────┬─────────────────────────┘
             │ Cache Miss
┌────────────▼─────────────────────────┐
│       Tier 2: Regional Caches        │
│  - Regional distribution centers     │
│  - Larger cache (100GB-1TB)          │
│  - TTL: 1-7 days                     │
│  - Handles 10-15% of requests        │
└────────────┬─────────────────────────┘
             │ Cache Miss
┌────────────▼─────────────────────────┐
│         Tier 3: Origin               │
│  - Source of truth                   │
│  - Full content storage              │
│  - Handles 1-5% of requests          │
└──────────────────────────────────────┘

Benefits:
- Reduced origin load (95-99%)
- Lower bandwidth costs
- Better cache hit rates
- Faster cache warming
```

---

## Search & Indexing Systems

### Search Architecture Pattern

**Dual-Write Pattern**:
```
┌──────────────────────────────────────┐
│        Application Layer             │
└──────┬───────────────────┬───────────┘
       │                   │
  Write│                   │ Write
       │                   │
       ▼                   ▼
┌─────────────┐     ┌──────────────┐
│  Database   │     │ Search Index │
│ (PostgreSQL)│     │(Elasticsearch│
│             │     │              │
│ Source of   │     │ Optimized    │
│ Truth       │     │ for Search   │
└─────────────┘     └──────────────┘

Consistency Handling:
- Database write succeeds
- Search index write async (can fail)
- Background job reconciles differences
- Accept eventual consistency
```

**Search Index Structure**:
```
Document Sharding:
┌────────────────────────────────────┐
│     Elasticsearch Cluster          │
├────────────────────────────────────┤
│  Shard 0: Docs 0-1M    + 2 Replicas│
│  Shard 1: Docs 1M-2M   + 2 Replicas│
│  Shard 2: Docs 2M-3M   + 2 Replicas│
│  Shard 3: Docs 3M-4M   + 2 Replicas│
└────────────────────────────────────┘

Query Distribution:
- Query sent to all shards
- Each shard searches its partition
- Results merged and ranked
- Parallel processing

Benefits:
- Horizontal scaling
- Fault tolerance (replicas)
- Fast parallel search
- No single point of failure
```

---

## Trade-offs & Decision Framework

### Technology Selection Matrix

| Requirement | Redis | Memcached | CDN | Read Replicas | Elasticsearch |
|-------------|-------|-----------|-----|---------------|---------------|
| **Latency** | <1ms | <1ms | 10-50ms | 10-50ms | 50-200ms |
| **Throughput** | Very High | Very High | Massive | Medium | Medium |
| **Consistency** | Eventual | Eventual | Eventual | Eventual | Eventual |
| **Persistence** | Optional | No | No | Yes | Yes |
| **Complex Queries** | Limited | No | No | Yes | Yes |
| **Cost** | Low | Low | Medium | Medium | High |
| **Best For** | Hot data | Simple cache | Static content | Relational queries | Search |

### Decision Tree

```
Start: Need to scale reads?
│
├─ Static content (images, videos, files)?
│  └─ YES → CDN (CloudFront, Cloudflare)
│
├─ Full-text search required?
│  └─ YES → Search Engine (Elasticsearch, Solr)
│
├─ Hot data (<10GB, frequently accessed)?
│  └─ YES → In-Memory Cache (Redis, Memcached)
│
├─ Complex relational queries?
│  ├─ Data size < 100GB?
│  │  └─ YES → Read Replicas (3-5 replicas)
│  │
│  └─ Data size > 1TB?
│     └─ YES → Sharding + Read Replicas + Cache
│
├─ Very different read/write patterns?
│  └─ YES → CQRS (Separate read/write models)
│
└─ Simple optimization needed?
   └─ YES → Start with:
      1. Application caching (Redis)
      2. CDN for static assets
      3. Database query optimization
      4. Read replicas if needed
```

### Cost-Benefit Analysis

**Small Application** (< 10K QPS):
```
Investment: $500/month
- Single database: $200
- Redis cache: $100
- CDN: $200

Benefits:
- Handle 10K QPS
- <100ms latency
- 99.9% availability

ROI: Excellent (minimal investment, big improvement)
```

**Medium Application** (10K-100K QPS):
```
Investment: $5,000/month
- Database cluster (5 replicas): $2,000
- Redis cluster (3 nodes): $1,000
- CDN (enterprise): $1,500
- Monitoring & tools: $500

Benefits:
- Handle 100K QPS
- <50ms latency
- 99.95% availability

ROI: Good (necessary for scale)
```

**Large Application** (100K-1M QPS):
```
Investment: $50,000/month
- Sharded database (20 shards): $20,000
- Redis clusters (multiple): $10,000
- CDN (global): $10,000
- Elasticsearch cluster: $5,000
- Monitoring & operations: $5,000

Benefits:
- Handle 1M QPS
- <20ms latency globally
- 99.99% availability

ROI: Required (cost of doing business at scale)
```

---

## Failure Modes & Resilience

### Cache Failure Scenarios

**Scenario 1: Cache Server Failure**:
```
Problem:
┌──────────┐
│  Redis   │ ──X── Crashes
│  Server  │
└──────────┘
      │
All requests → Database
      │
Database overwhelmed → Cascading failure

Impact:
- 10-100x database load
- Response time degradation
- Possible database failure
- User experience impact
```

**Mitigation**:
```
Solution 1: Cache Cluster with Replication
┌──────────┐     ┌──────────┐     ┌──────────┐
│ Redis 1  │────▶│ Redis 2  │────▶│ Redis 3  │
│ (Master) │     │(Replica) │     │(Replica) │
└──────────┘     └──────────┘     └──────────┘

- Master fails → Promote replica
- Automatic failover (<30 seconds)
- No data loss (sync replication)
- Minimal disruption

Solution 2: Circuit Breaker
- Detect cache failures
- Temporarily bypass cache
- Limit database load
- Gradually restore cache
```

**Scenario 2: Cache Stampede**:
```
Problem:
Popular item cache expires
  │
1000 concurrent requests
  │
All miss cache simultaneously
  │
1000 database queries
  │
Database overload

Timeline:
T+0s: Cache expires
T+1s: 1000 requests arrive
T+2s: All hit database
T+3s: Database CPU 100%
T+4s: Requests timeout
T+5s: Cascading failure
```

**Mitigation**:
```
Solution: Probabilistic Early Expiration
- Don't expire all caches at same time
- Add random jitter to TTL
- Refresh before expiration
- Single-flight pattern

Implementation:
Cache Entry:
- Base TTL: 300 seconds
- Jitter: ±30 seconds (random)
- Actual TTL: 270-330 seconds

Result:
- Expires spread over 60 seconds
- Database load spread out
- No stampede
```

### Database Replica Failure

**Scenario: Read Replica Fails**:
```
Architecture:
┌─────────┐
│ Master  │
└────┬────┘
     │
     ├─────────────────┬──────────────┐
     ▼                 ▼              ▼
┌─────────┐     ┌─────────┐    ┌─────────┐
│Replica1 │     │Replica2 │    │Replica3 │
│ (Fails) │     │ (Healthy)│    │(Healthy)│
└─────────┘     └─────────┘    └─────────┘
     X

Problem:
- Replica 1 crashes
- Load redirected to Replicas 2 & 3
- Increased load on remaining replicas
- Possible performance degradation
```

**Mitigation**:
```
Solution: Health Checks & Automatic Failover

┌──────────────────────────────────┐
│      Load Balancer               │
│  - Health checks every 10s       │
│  - Remove failed replicas        │
│  - Redistribute load             │
└──────────────────────────────────┘

Timeline:
T+0s: Replica 1 fails
T+10s: Health check detects failure
T+11s: Remove from pool
T+12s: Load redistributed (33% → 50% per replica)

Capacity Planning:
- N+1 redundancy minimum
- Each replica at 60% capacity
- Can handle 1 failure without degradation
- Alert ops for manual intervention
```

### CDN Failure Scenarios

**Scenario: Edge Node Failure**:
```
Problem:
┌──────────┐
│ Edge Node│ ──X── Fails
│ (Sydney) │
└──────────┘
      │
Users in Australia affected

Impact:
- Increased latency for region
- Fallback to next closest edge
- Origin load increases
```

**Mitigation**:
```
Multi-Level Fallback:
┌────────────────────────────────────┐
│  Primary: Sydney Edge (Failed)     │
└────────────┬───────────────────────┘
             │ Fallback
┌────────────▼───────────────────────┐
│  Secondary: Singapore Edge         │
│  - 50ms added latency             │
│  - Handles overflow               │
└────────────┬───────────────────────┘
             │ Fallback
┌────────────▼───────────────────────┐
│  Tertiary: Origin                  │
│  - 200ms added latency            │
│  - Last resort                    │
└────────────────────────────────────┘

Benefits:
- Graceful degradation
- No complete outage
- Automatic recovery
```

---

## Real-World Architectures

### Example 1: Netflix Read Architecture

**Challenge**: Stream to 200M+ subscribers globally

**Architecture**:
```
┌────────────────────────────────────────┐
│         Global CDN (Open Connect)      │
│  - 95% of traffic served from CDN      │
│  - ISP-embedded caches                 │
│  - Predictive pre-caching              │
└────────────┬───────────────────────────┘
             │ Metadata Requests (5%)
┌────────────▼───────────────────────────┐
│         API Gateway Layer              │
│  - ELB (Elastic Load Balancer)         │
│  - Zuul (API Gateway)                  │
│  - Rate limiting & auth                │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│      Application Cache (EVCache)       │
│  - Memcached-based                     │
│  - Hit rate: 85%+                      │
│  - User profiles, preferences          │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│        Cassandra Clusters              │
│  - Multi-region deployment             │
│  - Viewing history                     │
│  - User data                           │
│  - Recommendations                     │
└────────────────────────────────────────┘
```

**Key Decisions**:
- **CDN First**: 95% content delivery from edge
- **Predictive Caching**: Popular shows pre-cached regionally
- **Eventually Consistent**: Viewing progress can lag slightly
- **Multi-Region**: Each region has complete data copy

**Scale Metrics**:
- **Streaming**: 15 petabytes/day
- **API Requests**: 2 billion/day
- **Database Queries**: 500M+/day
- **Cache Hit Rate**: 85-90%

### Example 2: Twitter Read Architecture

**Challenge**: Serve 400M users reading timelines

**Architecture**:
```
┌────────────────────────────────────────┐
│            Load Balancer               │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│         Timeline Cache (Redis)         │
│  - Pre-computed timelines              │
│  - Fan-out on write for most users     │
│  - 5-minute TTL                        │
│  - Hit rate: 80%+                      │
└────────────┬───────────────────────────┘
             │ Cache Miss
┌────────────▼───────────────────────────┐
│       Hybrid Approach                  │
│  - Regular users: Read from cache      │
│  - Celebrities: Fan-out on read        │
│  - Merge cached + celebrity tweets     │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│      Manhattan (Distributed DB)        │
│  - Tweet storage                       │
│  - Read replicas per region            │
│  - Eventually consistent               │
└────────────────────────────────────────┘
```

**Key Decisions**:
- **Hybrid Fan-out**: Write for normal users, read for celebrities
- **Heavy Caching**: Pre-compute timelines for active users
- **Eventually Consistent**: Tweets appear with slight delay
- **Geographic Replicas**: Low latency worldwide

**Scale Metrics**:
- **Timeline Reads**: 300K/sec
- **Tweet Reads**: 200K/sec
- **Cache Hit Rate**: 80-85%
- **Replication Lag**: <100ms

### Example 3: Instagram Feed Architecture

**Challenge**: Serve feeds to 1B+ users

**Architecture**:
```
┌────────────────────────────────────────┐
│         CDN (Static Content)           │
│  - Profile pictures                    │
│  - Photos/videos                       │
│  - 90% of bandwidth                    │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│         Feed Cache (Redis)             │
│  - Last 100 posts per user             │
│  - 5-minute TTL                        │
│  - Pre-computed feeds                  │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│        Read Replicas (50+)             │
│  - Follower/following queries          │
│  - PostgreSQL replicas                 │
│  - Sharded by user_id                  │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│          Cassandra                     │
│  - Denormalized feed table             │
│  - Partitioned by user_id              │
│  - Time-series data                    │
└────────────────────────────────────────┘
```

**Key Decisions**:
- **CDN Heavy**: Most content served from edge
- **Feed Pre-computation**: Feeds generated on write
- **Denormalization**: Optimized feed table
- **Time-Series**: Append-only architecture

**Scale Metrics**:
- **Feed Requests**: 500K/sec
- **CDN Hit Rate**: 90%+
- **Cache Hit Rate**: 85%
- **Database Load**: <5% of total requests

### Example 4: Amazon Product Catalog

**Challenge**: Search/browse 500M+ products

**Architecture**:
```
┌────────────────────────────────────────┐
│         CloudFront CDN                 │
│  - Product images                      │
│  - Static assets                       │
│  - 70% of requests                     │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│      Elasticsearch Cluster             │
│  - Product search                      │
│  - Faceted filtering                   │
│  - Autocomplete                        │
│  - 20-30% of requests                  │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│        Redis Cache                     │
│  - Popular products                    │
│  - Search results                      │
│  - Session data                        │
│  - Hit rate: 80%                       │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│        DynamoDB                        │
│  - Product details                     │
│  - Inventory                           │
│  - Read replicas                       │
└────────────┬───────────────────────────┘
             │
┌────────────▼───────────────────────────┐
│       Aurora (PostgreSQL)              │
│  - Order history                       │
│  - User data                           │
│  - Read replicas (10+)                 │
└────────────────────────────────────────┘
```

**Key Decisions**:
- **Search First**: Elasticsearch for product discovery
- **Multi-Database**: Right tool for each data type
- **Heavy Caching**: Multiple cache layers
- **CDN for Assets**: Reduce origin load

**Scale Metrics**:
- **Product Searches**: 100K/sec
- **Page Views**: 300K/sec
- **CDN Hit Rate**: 90%+
- **Search Latency**: <100ms

---

## Interview Strategy

### The 5-Phase Approach

**Phase 1: Requirements Clarification** (5 minutes)
```
Essential Questions:
1. Read:Write Ratio?
   → Determines if read optimization is priority

2. Expected QPS?
   → Determines scale of solution

3. Data Size?
   → Influences sharding decisions

4. Latency Requirements?
   → Impacts caching strategy

5. Consistency Requirements?
   → Affects replication approach

6. Geographic Distribution?
   → Influences CDN and regional replicas

7. Data Types?
   → Impacts storage and caching choices
```

**Phase 2: Capacity Estimation** (5 minutes)
```
Calculate Scale:
- Daily Active Users (DAU)
- Requests per user per day
- Total requests = DAU × requests/user
- QPS = Total requests / 86,400
- Peak QPS = Average QPS × 3
- Data per request
- Storage needs

Example:
- 10M DAU
- 50 reads/user/day
- Total: 500M reads/day
- Average: 5,800 QPS
- Peak: 17,400 QPS
- Plan for 20,000 QPS
```

**Phase 3: High-Level Design** (10 minutes)
```
Components to Include:
1. Client Layer
   - Mobile apps
   - Web browsers

2. CDN Layer
   - Static assets
   - Cached API responses

3. Load Balancer
   - Distribute traffic
   - Health checks

4. Application Servers
   - Business logic
   - Connection pooling

5. Cache Layer
   - Redis/Memcached
   - Hot data

6. Database Layer
   - Primary database
   - Read replicas
   - Sharding if needed

7. Search (if needed)
   - Elasticsearch
   - Faceted search
```

**Phase 4: Deep Dive** (15 minutes)
```
Drill Into:
1. Caching Strategy
   - What to cache?
   - TTL values?
   - Eviction policy?
   - Invalidation approach?

2. Database Strategy
   - How many replicas?
   - Replication type?
   - Sharding approach?
   - Consistency model?

3. Failure Handling
   - Cache failures?
   - Replica failures?
   - Network partitions?

4. Monitoring
   - Key metrics?
   - Alerting thresholds?
   - Dashboards?
```

**Phase 5: Trade-offs Discussion** (5 minutes)
```
Always Cover:
1. Consistency vs Latency
   - Strong consistency → Higher latency
   - Eventual consistency → Lower latency
   - Decision based on use case

2. Cost vs Performance
   - More replicas → Better performance, higher cost
   - Caching → Lower cost overall
   - CDN → High bandwidth cost savings

3. Complexity vs Benefit
   - CQRS → Complex but powerful
   - Simple caching → Easy wins first

4. Availability vs Consistency
   - CAP theorem applies
   - Choose based on requirements
```

### Sample Interview Response

```
Interviewer: "Design a system to handle 100,000 read requests per second"

You: "Let me clarify the requirements first:

1. What's the read:write ratio?
   [Answer: 95:5]

2. What type of data are we reading?
   [Answer: User profiles and activity feeds]

3. What's the acceptable latency?
   [Answer: <100ms]

4. Strong or eventual consistency?
   [Answer: Eventual is fine]

5. Geographic distribution?
   [Answer: Global users]

Based on these requirements, I propose this architecture:

**High-Level Design:**

Layer 1 - Global CDN:
- CloudFront for static assets (profile pictures)
- Serves 40% of requests (<20ms latency)
- Reduces origin load by 40K QPS

Layer 2 - Regional Caches:
- Redis clusters in 3 regions (US, EU, Asia)
- Cache user profiles (10-minute TTL)
- 80% cache hit rate
- Serves 48K QPS from remaining 60K
- <5ms latency

Layer 3 - Application Servers:
- Auto-scaling groups per region
- Connection pooling (50 connections/server)
- Handles 12K requests hitting database

Layer 4 - Database:
- Primary: PostgreSQL master (writes)
- 10 read replicas across regions
- Each replica: 1,200 QPS (~15% capacity)
- Async replication (~50ms lag)
- Acceptable for use case

**Capacity Math:**
- Total: 100K QPS
- CDN: 40K QPS (40%)
- Cache: 48K QPS (48%)  
- Database: 12K QPS (12%)

**Geographic Distribution:**
- US Region: 50K QPS (3 replicas)
- EU Region: 30K QPS (4 replicas)
- Asia Region: 20K QPS (3 replicas)

**Failure Handling:**
- Cache failure → Circuit breaker pattern
- Replica failure → Load balancer health checks
- Region failure → Failover to nearest region

**Trade-offs:**
+ Very low latency (<50ms average)
+ Scales horizontally (add more replicas/cache nodes)
+ Cost-effective (caching reduces DB load 88%)
+ High availability (multi-region)

- Eventual consistency (50ms replication lag)
- Operational complexity (multi-region management)
- Cache invalidation complexity

**Monitoring:**
- Cache hit rate (target: >80%)
- P99 latency (target: <100ms)
- Database replica lag (target: <100ms)
- Error rate (target: <0.1%)

Would you like me to dive deeper into any component?"
```

### Key Metrics to Discuss

| Metric | Target | Alert Threshold | Critical Threshold |
|--------|--------|----------------|-------------------|
| **Latency (p50)** | <20ms | >50ms | >100ms |
| **Latency (p99)** | <100ms | >200ms | >500ms |
| **Cache Hit Rate** | >80% | <70% | <60% |
| **Error Rate** | <0.1% | >0.5% | >1% |
| **Replica Lag** | <50ms | >100ms | >200ms |
| **Throughput** | 100K QPS | <80K QPS | <50K QPS |

---

## Conclusion

Read scaling is achieved through:

**Core Principles**:
1. **Cache Everything** - Multiple layers reduce database load
2. **Distribute Load** - Replicas and sharding spread traffic
3. **Accept Eventual Consistency** - Enables massive scale
4. **Geographic Distribution** - Reduces latency globally
5. **Plan for Failure** - Redundancy at every level

**Key Takeaways**:
- Start with simple caching (Redis + CDN)
- Add read replicas when database becomes bottleneck
- Consider sharding only when necessary (>1TB data)
- Use CQRS for very different read/write patterns
- Monitor everything and iterate

**Remember**: Premature optimization is evil, but understanding these patterns is essential for system design interviews and building scalable systems.

---

**Document Version**: 2.0 - Architecture Focused  
**Last Updated**: November 2024  
**Focus**: System design patterns, trade-offs, and architectural decisions  
**Companion**: [Write Scaling Architecture Guide](./write-scaling-architecture-guide.md)
