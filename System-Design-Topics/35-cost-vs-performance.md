# 🎯 Topic 35: Cost vs Performance

> **System Design Interview — Deep Dive**
> A comprehensive guide covering caching as a cost lever, tiered storage savings, spot instances for batch workloads, pre-computation economics, right-sizing infrastructure, quantifying ROI of architectural decisions, and how to articulate cost-performance tradeoffs with specific numbers in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [The Cost-Performance Spectrum](#the-cost-performance-spectrum)
3. [Caching — The Highest ROI Lever](#caching--the-highest-roi-lever)
4. [Tiered Storage — Matching Cost to Access Frequency](#tiered-storage--matching-cost-to-access-frequency)
5. [Compute Cost Optimization](#compute-cost-optimization)
6. [Pre-Computation Economics](#pre-computation-economics)
7. [CDN Economics](#cdn-economics)
8. [Database Cost Optimization](#database-cost-optimization)
9. [Right-Sizing: Design for 10x, Path to 100x](#right-sizing-design-for-10x-path-to-100x)
10. [Cost of Over-Engineering vs Under-Engineering](#cost-of-over-engineering-vs-under-engineering)
11. [Real-World Cost Breakdowns](#real-world-cost-breakdowns)
12. [Deep Dive: Cost Decisions for Popular System Design Problems](#deep-dive-cost-decisions-for-popular-system-design-problems)
13. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
14. [Common Interview Mistakes](#common-interview-mistakes)
15. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Every architectural decision has a **cost-performance tradeoff**. The goal isn't minimizing cost or maximizing performance independently — it's **maximizing performance per dollar** for your specific requirements.

```
The wrong question:  "What's the cheapest way to build this?"
The wrong question:  "What's the fastest way to build this?"
The right question:  "What's the most cost-efficient way to meet our SLA?"

Example:
  SLA: P99 read latency < 50ms, 100K reads/sec
  
  Option A: 20 PostgreSQL read replicas
    Cost: $30,000/month
    Latency: 5-15ms ✓ (overshoots SLA by 3x — wasted money)
  
  Option B: 1 PostgreSQL master + 3-node Redis cache (95% hit rate)
    Cost: $2,500/month
    Latency: 0.5ms (cache hit) / 10ms (cache miss) → P99 < 15ms ✓
  
  Option B delivers 12x cost savings while exceeding the SLA.
```

---

## The Cost-Performance Spectrum

### Infrastructure Cost Components

| Component | Typical Cost Range | What Drives Cost |
|---|---|---|
| **Compute (EC2/ECS)** | $50-$5,000/month per instance | CPU, memory, instance family |
| **Database (RDS/DynamoDB)** | $100-$50,000/month | Instance size, IOPS, storage |
| **Cache (Redis/ElastiCache)** | $50-$5,000/month per node | Memory size, node count |
| **Storage (S3)** | $0.023/GB/month (Standard) | Volume, access frequency |
| **CDN (CloudFront)** | $0.085/GB transferred | Transfer volume, cache hit ratio |
| **Messaging (Kafka/SQS)** | $200-$10,000/month | Throughput, retention |
| **Search (Elasticsearch)** | $500-$20,000/month | Data volume, query complexity |
| **Bandwidth** | $0.09/GB (AWS egress) | Data transfer out |

### The 80/20 Rule of Infrastructure Cost

```
Typical cost distribution for a web application:
  Database:    35-45% of total infrastructure cost
  Compute:     25-35%
  CDN/Bandwidth: 10-15%
  Cache:       5-10%
  Storage:     3-5%
  Messaging:   2-5%
  Monitoring:  2-3%

Optimization priority: Database → Compute → CDN → Cache
```

---

## Caching — The Highest ROI Lever

### The Math: Why Caching Is the #1 Cost Lever

```
Scenario: 200K reads/sec, P99 < 50ms

Without cache:
  PostgreSQL can handle ~10K reads/sec per replica
  Need: 200K / 10K = 20 read replicas
  Cost: 20 × $1,500/month = $30,000/month

With Redis cache (95% hit rate):
  Redis handles 200K × 0.95 = 190K reads/sec
  3-node Redis cluster: $600/month
  PostgreSQL handles 200K × 0.05 = 10K reads/sec (1 replica)
  Cost: $600 + $1,500 = $2,100/month

  Savings: $27,900/month = $334,800/year
  ROI: $600 investment → $27,900 savings = 46.5x return

With Redis cache (99% hit rate — warm cache):
  Redis: 198K reads/sec → 3-node cluster: $600/month
  PostgreSQL: 2K reads/sec → 1 instance: $800/month
  Cost: $1,400/month
  
  Savings: $28,600/month from $600 investment = 47.7x return
```

### Cache Hit Rate vs Cost Impact

| Cache Hit Rate | DB Load (200K total) | DB Replicas Needed | Monthly Cost |
|---|---|---|---|
| 0% (no cache) | 200K/sec | 20 | $30,000 |
| 80% | 40K/sec | 4 | $6,600 |
| 90% | 20K/sec | 2 | $3,600 |
| 95% | 10K/sec | 1 | $2,100 |
| 99% | 2K/sec | 1 | $1,400 |

**The insight**: Going from 0% to 90% cache hit rate reduces database cost by 88%. The last 9% (90% → 99%) saves another 61%. Diminishing returns, but still significant.

### Interview Script

> *"Caching is the single highest-ROI architectural lever. Our $600/month Redis cluster absorbs 95% of reads, saving us from provisioning 20 PostgreSQL replicas at $30,000/month. That's a 46x return on investment. And read latency drops from 5ms to 0.5ms — a 10x performance improvement for free."*

---

## Tiered Storage — Matching Cost to Access Frequency

### Storage Cost Comparison

| Tier | Technology | Cost/GB/Month | Access Latency | Best For |
|---|---|---|---|---|
| **In-memory** | Redis | $25.00 | < 1ms | Hot data (sessions, cache) |
| **SSD** | EBS gp3 / RDS | $0.08 | 1-5ms | Warm data (active records) |
| **HDD** | EBS st1 | $0.045 | 5-15ms | Warm-cold (logs, old records) |
| **Object (Standard)** | S3 Standard | $0.023 | 50-200ms | Cold data (media, backups) |
| **Object (IA)** | S3 Infrequent Access | $0.0125 | 50-200ms | Rarely accessed data |
| **Archive** | S3 Glacier | $0.004 | Minutes-hours | Compliance archives |
| **Deep Archive** | S3 Glacier Deep | $0.00099 | 12-48 hours | Legal retention |

### The Impact: All-Redis vs Tiered Storage

```
Scenario: 10 TB of data, 1% accessed frequently

Option A: All in Redis
  10 TB × $25/GB = $250,000/month

Option B: Tiered storage
  100 GB hot data in Redis:   100 × $25 = $2,500/month
  900 GB warm data on SSD:    900 × $0.08 = $72/month
  9 TB cold data in S3:       9,000 × $0.023 = $207/month
  Total: $2,779/month

  Savings: $247,221/month = 99% reduction
  Performance impact: Zero for 99% of requests (hot data in Redis)
```

### Data Lifecycle Automation

```
Automatic tier migration based on access patterns:

Day 0-7:     Redis (sub-ms, frequently accessed)
Day 7-30:    Primary DB / SSD (ms, occasionally accessed)
Day 30-90:   S3 Standard (50ms, rarely accessed)
Day 90-365:  S3 Infrequent Access (50ms, very rarely accessed)
Day 365+:    S3 Glacier (minutes, compliance/archival only)

Implementation:
  S3 Lifecycle Rules: Automatic tier transitions
  Application: TTL-based eviction from Redis → fallback to DB → fallback to S3
  
Real example (Twitter):
  Recent tweets (48h): Redis sorted sets → sub-ms
  Older tweets: Cassandra → 2-5ms
  Very old tweets: Cold storage → 50-200ms
```

### Interview Script

> *"I'd tier the storage by access frequency. The hot 1% of data — active sessions, recent feed items, trending content — lives in Redis at $25/GB for sub-ms access. The warm 9% lives on SSD at $0.08/GB. The remaining 90% goes to S3 at $0.023/GB. Total: $2,800/month instead of $250,000/month if we kept everything in Redis. Zero performance impact because 99% of user requests hit the hot tier."*

---

## Compute Cost Optimization

### Spot Instances for Batch Workloads

```
Spot instances: 60-90% cheaper than on-demand

Eligible workloads (fault-tolerant, interruptible):
  ✅ Nightly Spark reconciliation jobs
  ✅ Machine learning training
  ✅ Video transcoding
  ✅ Log processing
  ✅ Data pipeline backfills
  
Not eligible (cannot tolerate interruption):
  ❌ API servers (user-facing)
  ❌ Database instances
  ❌ WebSocket gateway servers
  ❌ Leader election coordinators

Cost comparison:
  On-demand c5.2xlarge: $0.34/hour
  Spot c5.2xlarge:      $0.07/hour (80% cheaper)
  
  Nightly Spark job: 200 instances × 4 hours
    On-demand: 200 × $0.34 × 4 = $272/run × 365 = $99,280/year
    Spot:      200 × $0.07 × 4 = $56/run  × 365 = $20,440/year
    Savings:   $78,840/year per job

If spot instances are reclaimed:
  Spark's fault tolerance reruns affected tasks on remaining instances
  Job may take 10-20% longer, but cost is still 70% less
```

### Reserved Instances for Steady-State

```
For always-on workloads (API servers, databases):

On-demand:     $0.34/hour × 8,760 hours = $2,978/year
1-year reserved: $0.22/hour equivalent = $1,927/year (35% savings)
3-year reserved: $0.14/hour equivalent = $1,226/year (59% savings)

Strategy:
  Baseline load (always needed): Reserved instances (1-year or 3-year)
  Peak handling (spikes): On-demand auto-scaling
  Batch processing: Spot instances
  
  Example: API servers
    Baseline: 10 instances (reserved, 35% savings)
    Peak: Auto-scale to 30 instances (on-demand)
    Nightly batch: 50 spot instances
```

### Auto-Scaling to Match Demand

```
Without auto-scaling:
  Provision for peak: 30 instances × 24 hours = 720 instance-hours/day
  Cost: 720 × $0.34 = $245/day

With auto-scaling:
  Night (2 AM): 5 instances
  Morning ramp: 15 instances
  Peak (12 PM): 30 instances
  Evening taper: 10 instances
  Average: 15 instances × 24 hours = 360 instance-hours/day
  Cost: 360 × $0.34 = $122/day
  
  Savings: 50% by matching capacity to demand
```

---

## Pre-Computation Economics

### The Write Amplification vs Read Cost Tradeoff

```
Ad click dashboard — 10M clicks/day, viewed by 100K advertisers

Option A: On-demand computation
  Each dashboard load: Scan 10M events → GROUP BY → aggregate
  Cost per query: ~$0.005 (ClickHouse compute)
  Queries per day: 100K advertisers × 10 views = 1M queries
  Daily cost: 1M × $0.005 = $5,000/day = $150,000/month
  Latency: 3-10 seconds per dashboard load

Option B: Pre-computed aggregates
  Write: Each click → 3 Redis HINCRBY (minute, hour, day buckets)
  Write cost: 10M × 3 = 30M Redis operations → $50/day
  Dashboard load: 1 HGET → $0.0001 per query
  Query cost: 1M × $0.0001 = $100/day
  Total daily cost: $150/day = $4,500/month
  Latency: < 1ms

  Savings: $145,500/month (97%)
  Performance: 3,000x faster (10s → 1ms)
```

### When Pre-Computation Doesn't Pay Off

```
Scenario: Custom analytics report run by 5 people monthly

Pre-compute all possible dimensions:
  10 campaigns × 50 ads × 365 days × 24 hours × 5 metrics = 21.9M pre-computed cells
  Most will never be queried → wasted storage and compute

On-demand:
  5 queries/month × $0.05/query = $0.25/month
  Latency: 30 seconds (acceptable for monthly reports)

  Pre-computation ROI is negative when:
    - Queries are rare (< 100/day per dimension)
    - Dimensions are numerous and unpredictable
    - Users can tolerate seconds of latency
```

---

## CDN Economics

### Origin Offload Savings

```
Scenario: 100K requests/sec for product images

Without CDN:
  All requests hit origin servers
  Need: 50 origin servers at $500/month each = $25,000/month
  Bandwidth: 100K × 200KB × 86,400 sec = 1.7 PB/day out of origin
  Bandwidth cost: ~$150,000/month (at $0.09/GB)
  Total: $175,000/month

With CloudFront CDN (95% cache hit rate):
  CDN serves 95K of 100K requests from edge
  CDN cost: ~$14,000/month (data transfer + requests)
  Origin handles 5K requests/sec → 3 servers: $1,500/month
  Origin bandwidth: 5% of original → $7,500/month
  Total: $23,000/month

  Savings: $152,000/month (87%)
  Additional benefit: 10-50ms latency (edge) vs 100-200ms (origin)
```

### CDN TTL vs Freshness Tradeoff

```
Higher TTL → Higher hit rate → Lower cost → Staler content

TTL = 1 second:   Hit rate ~80%  → More origin load, fresher
TTL = 60 seconds:  Hit rate ~95%  → Less origin load, some staleness
TTL = 1 hour:     Hit rate ~99%  → Minimal origin load, stale by up to 1 hour
TTL = 1 day:      Hit rate ~99.9% → Almost no origin load, very stale

Strategy:
  Static assets (JS, CSS, images): TTL = 1 year (versioned filenames)
  Product images: TTL = 1 hour (rarely change)
  Product prices: TTL = 60 seconds (change occasionally)
  Inventory count: TTL = 5 seconds (changes frequently)
  User-specific data: No CDN caching (personalized)
```

---

## Database Cost Optimization

### Read Replicas vs Cache: When to Use Which

```
PostgreSQL read replica: $1,500/month for ~10K QPS
Redis node:             $200/month for ~100K QPS

Read replicas are better when:
  - Complex queries (JOINs, aggregations) are common
  - Data changes frequently (cache invalidation overhead)
  - Strong consistency is required

Redis cache is better when:
  - Simple key-value lookups dominate (user profiles, session data)
  - High read-to-write ratio (>10:1)
  - Sub-millisecond latency is required
  - Cost efficiency is critical

Hybrid approach (most systems):
  PostgreSQL (1 master + 1 replica): $3,000/month
  Redis (3-node cluster): $600/month
  Total: $3,600/month
  Handles: 200K reads/sec (95% from cache, 5% from DB)
  Alternative without cache: 20 replicas at $30,000/month
```

### Provisioned vs On-Demand (DynamoDB)

```
Provisioned capacity:
  You specify: 1,000 RCU + 500 WCU
  Cost: $0.00065/RCU/hour + $0.00065/WCU/hour
  Monthly: ~$700/month
  Best for: Predictable, steady traffic

On-demand capacity:
  You pay per request: $0.25/million reads, $1.25/million writes
  At 1,000 reads/sec + 500 writes/sec:
  Monthly: ~$2,300/month (3.3x more expensive)
  Best for: Unpredictable, spiky traffic

Strategy:
  Start on-demand (no capacity planning needed)
  Once traffic pattern is stable → switch to provisioned (60-70% cheaper)
  Use auto-scaling for provisioned to handle spikes
```

### Connection Pooling Savings

```
Without connection pooling:
  1,000 concurrent API instances × 10 DB connections each = 10,000 connections
  PostgreSQL max_connections: ~5,000 (after that, OOM)
  Solution: More DB instances → $$$

With PgBouncer (connection pooling):
  1,000 API instances → PgBouncer (100 pooled connections) → PostgreSQL
  PostgreSQL sees 100 connections, not 10,000
  One DB instance handles the load
  
  Cost savings: Avoid needing 2-3 additional DB instances
  Savings: ~$3,000-$4,500/month
```

---

## Right-Sizing: Design for 10x, Path to 100x

### The Premature Optimization Trap

```
At 1K QPS → Don't build for 1M QPS
  Single PostgreSQL handles 1K QPS easily
  Cost: $500/month
  Complexity: Low (one database, one server)

  Building for 1M QPS at 1K QPS:
    Sharded database (3 shards): $4,500/month
    Redis cluster: $600/month
    Kafka cluster: $1,200/month
    Elasticsearch: $2,000/month
    Kubernetes: $3,000/month
    Total: $11,300/month for 1K QPS
    
  That's 22x more expensive for the same traffic level.
```

### Scaling Stages with Cost

```
Stage 1: 0 - 1K QPS ($500-$1,500/month)
  Single PostgreSQL instance
  Single application server
  No caching needed
  No message queue needed
  
Stage 2: 1K - 10K QPS ($1,500-$5,000/month)
  PostgreSQL + 1-2 read replicas
  2-5 application servers behind ALB
  Redis for session management and hot data cache
  
Stage 3: 10K - 100K QPS ($5,000-$25,000/month)
  PostgreSQL + Redis cache (95% hit rate)
  10-20 application servers with auto-scaling
  CDN for static assets
  Elasticsearch for search (if needed)
  
Stage 4: 100K - 1M QPS ($25,000-$150,000/month)
  Sharded database or DynamoDB
  Redis cluster (multi-node)
  Kafka for event streaming
  CDN for all cacheable content
  Microservices architecture
  
Stage 5: 1M+ QPS ($150,000+/month)
  Multi-region deployment
  Multiple sharded databases
  Custom infrastructure
  Dedicated networking
```

### The Design Rule

```
Design for 10x current load (handles growth for 1-2 years).
Have a clear PATH to 100x (know what changes are needed, but don't build them yet).

Example at 10K QPS:
  Design for: 100K QPS (add caching, read replicas, CDN)
  Path to 1M: "When we hit 100K, we'll shard the database, move to 
              Cassandra for write-heavy tables, and add Kafka for 
              event-driven architecture."
  
  Don't build Kafka and Cassandra today — they add operational 
  complexity that's unjustified at 10K QPS.
```

---

## Cost of Over-Engineering vs Under-Engineering

### Over-Engineering Costs

```
Building microservices for a 3-person team:
  Kubernetes cluster: $3,000/month
  Service mesh (Istio): Complexity overhead
  5 separate databases: $5,000/month
  Kafka cluster: $1,200/month
  CI/CD for 10 services: 10x deployment complexity
  
  Total: $10,000/month + 60% of engineering time on infrastructure
  
  Monolith alternative: $1,500/month + 20% on infrastructure
  
  At 3 engineers, the monolith lets you ship features 3x faster.
```

### Under-Engineering Costs

```
Not adding caching at 100K QPS:
  20 PostgreSQL replicas: $30,000/month
  Latency: 5-15ms
  
  With $600 Redis investment: $2,100/month
  
  Under-engineering cost: $27,900/month wasted on unnecessary DB replicas
```

### The Sweet Spot Decision Framework

| Current QPS | Team Size | Recommendation |
|---|---|---|
| < 1K | 1-3 | Monolith + single DB. Don't optimize. Ship features. |
| 1K-10K | 3-10 | Monolith + read replicas + Redis cache. Consider CDN. |
| 10K-100K | 10-30 | Start splitting hot services. Add Kafka for async. Shard if needed. |
| 100K-1M | 30-100 | Microservices. Multi-AZ. Sharded everything. CDN mandatory. |
| > 1M | 100+ | Multi-region. Custom infrastructure. Dedicated SRE team. |

---

## Real-World Cost Breakdowns

### Twitter-Scale System (~300K tweets/sec, ~600K reads/sec)

```
Estimated infrastructure:
  Cassandra cluster (tweets):     $80,000/month (50+ nodes)
  Redis cluster (timelines):      $40,000/month (100+ nodes)
  Kafka cluster (event streaming): $20,000/month
  Elasticsearch (search):         $30,000/month
  API/compute (EC2):              $100,000/month (500+ instances)
  CDN (media delivery):           $50,000/month
  Total: ~$320,000/month

  Biggest lever: Redis caching absorbs 95% of reads
  Without Redis: Would need 10x more Cassandra nodes = $800K/month
  Redis ROI: $40K investment saves $720K = 18x return
```

### Startup-Scale System (~1K QPS, 100K users)

```
Estimated infrastructure:
  RDS PostgreSQL (db.r6g.large):  $300/month
  ElastiCache Redis (cache.r6g.large): $200/month
  2x EC2 (c5.xlarge) + ALB:      $400/month
  S3 (media storage):             $50/month
  CloudFront CDN:                 $100/month
  Total: ~$1,050/month

  At this scale, the database is 30% of cost.
  Biggest optimization: Use reserved instances → save 35%
```

---

## Deep Dive: Cost Decisions for Popular System Design Problems

### URL Shortener

```
100M URLs, 10K reads/sec, 100 writes/sec

Option A: DynamoDB (on-demand)
  Reads: 10K/sec × $0.25/M = $216/day = $6,480/month
  Writes: 100/sec × $1.25/M = $10.80/day = $324/month
  Storage: 100M × 200 bytes = 20 GB = $5/month
  Total: $6,809/month

Option B: DynamoDB (provisioned)
  10K RCU + 100 WCU = $2,100/month
  Storage: $5/month
  Total: $2,105/month (69% cheaper)

Option C: Redis (hot) + DynamoDB (all)
  Redis: Top 10M URLs (80% of traffic) → $200/month
  DynamoDB (provisioned, lower): 2K RCU + 100 WCU = $500/month
  Total: $700/month (90% cheaper than Option A)
```

### Chat System (WhatsApp-like)

```
500M users, 100B messages/day

Storage:
  Messages: 100B × 200 bytes = 20 TB/day → Cassandra
  Cassandra cluster: 100 nodes × $800/month = $80,000/month
  
  Optimization: Compress messages (50% savings) + tier old messages
    Active (30 days): 600 TB in Cassandra = $80,000/month
    Archive (>30 days): S3 = $500/month per TB
    Savings: 70% reduction in Cassandra nodes over time

Compute:
  WebSocket gateways: 10M / 65K connections = 154 servers
  Cost: 154 × $500/month = $77,000/month
  
  Optimization: Use Graviton instances (20% cheaper)
  Savings: $15,400/month
```

---

## Interview Talking Points & Scripts

### The Caching Lever

> *"Caching is the single best cost-performance lever. Our $600/month Redis cluster saves $28,000/month in database costs — a 46x ROI. And read latency drops from 5ms to 0.5ms. The key insight: at a 95% cache hit rate, I need only 1 PostgreSQL replica instead of 20."*

### Tiered Storage

> *"I'd tier the storage: hot data (last 48h) in Redis at $25/GB, warm data (last 30 days) on SSD at $0.08/GB, cold data in S3 at $0.023/GB, archival in Glacier at $0.004/GB. This reduces storage cost by 97% compared to keeping everything in Redis, with zero impact on user-facing latency since 99% of requests hit the hot tier."*

### Spot Instances

> *"Spot instances for batch processing cut compute costs by 60-80%. Our nightly Spark reconciliation job uses spot at $0.07/hour instead of $0.34/hour on-demand. If AWS reclaims instances, Spark's fault tolerance reruns affected tasks — the job might take 20% longer, but it costs 80% less."*

### Right-Sizing

> *"At our current 10K QPS, I'd design for 100K — that means adding Redis caching and a CDN. But I wouldn't build for 1M QPS today. When we reach 100K, we'll shard the database and add Kafka. Premature optimization at 10K QPS would triple our infrastructure cost and double our operational complexity for zero user benefit."*

### CDN Savings

> *"CloudFront at $14,000/month absorbs 95% of static content requests, saving us $150,000/month in origin server costs and bandwidth. Plus, edge delivery reduces latency from 200ms to 20ms for global users."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Designing for peak from day one
**Why it's wrong**: Building a Kafka + Cassandra + microservices architecture for a system with 1K QPS wastes money and engineering time.
**Better**: Start simple (monolith + PostgreSQL + Redis), scale incrementally.

### ❌ Mistake 2: Not quantifying the cost of architectural decisions
**Why it's wrong**: Saying "I'd add caching" without quantifying the ROI shows surface-level understanding.
**Better**: "A 3-node Redis cluster at $600/month eliminates the need for 18 additional PostgreSQL replicas at $27,000/month — a 45x ROI."

### ❌ Mistake 3: Keeping all data in the same storage tier
**Why it's wrong**: 99% of data is cold but stored at hot-tier prices.
**Better**: Tier by access frequency — Redis / SSD / S3 / Glacier.

### ❌ Mistake 4: Using on-demand pricing for predictable workloads
**Why it's wrong**: On-demand DynamoDB is 3x more expensive than provisioned for steady traffic.
**Better**: Provisioned for baseline, on-demand or auto-scaling for spikes.

### ❌ Mistake 5: Not mentioning spot instances for batch workloads
**Why it's wrong**: Missing 60-80% compute savings for fault-tolerant jobs.
**Better**: Spot for batch (Spark, ML training, transcoding), reserved for steady-state, on-demand for peaks.

### ❌ Mistake 6: Over-engineering infrastructure for team size
**Why it's wrong**: A 3-person team running Kubernetes with 10 microservices spends 60% of time on infrastructure.
**Better**: Match architecture complexity to team size and traffic scale.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│             COST vs PERFORMANCE CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  #1 LEVER: CACHING (30-50x ROI)                              │
│    $600/month Redis saves $28K/month in DB replicas          │
│    95% hit rate → 20x fewer DB nodes needed                  │
│                                                              │
│  #2 LEVER: TIERED STORAGE (97% savings)                      │
│    Redis ($25/GB) → SSD ($0.08) → S3 ($0.023) → Glacier     │
│    Match storage tier to access frequency                    │
│                                                              │
│  #3 LEVER: COMPUTE OPTIMIZATION                              │
│    Spot instances: 60-80% cheaper for batch jobs             │
│    Reserved: 35-59% cheaper for steady-state                 │
│    Auto-scaling: 50% cheaper by matching demand              │
│                                                              │
│  #4 LEVER: CDN (87% origin cost reduction)                   │
│    $14K/month CDN saves $150K/month in origin costs          │
│    95% cache hit rate → 20x less origin traffic              │
│                                                              │
│  #5 LEVER: PRE-COMPUTATION (97% query cost reduction)        │
│    3 Redis writes per event vs scan millions per query       │
│                                                              │
│  RIGHT-SIZING:                                               │
│    Design for 10x current load                               │
│    Have a PATH to 100x (don't build it yet)                  │
│    Match complexity to team size and traffic                 │
│                                                              │
│  SCALING STAGES:                                             │
│    < 1K QPS:  Monolith + single DB ($500/mo)                 │
│    1K-10K:    + Read replicas + Redis ($1.5K-5K/mo)          │
│    10K-100K:  + CDN + begin splitting services ($5K-25K/mo)  │
│    100K-1M:   + Sharding + Kafka + microservices ($25K-150K) │
│    > 1M:      + Multi-region + custom infra ($150K+)         │
│                                                              │
│  INTERVIEW FORMULA:                                          │
│  "This $X/month investment saves $Y/month by [mechanism],    │
│   a [Y/X]x ROI, while improving [latency/throughput] by Zx" │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 5: Caching Strategies** — Detailed cache patterns and hit rate optimization
- **Topic 17: Storage Tiering & Data Lifecycle** — Hot/warm/cold data management
- **Topic 19: CDN & Edge Caching** — CDN architecture and economics
- **Topic 29: Pre-Computation vs On-Demand** — Write amplification vs read cost
- **Topic 14: Load Balancing** — Auto-scaling and capacity management

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
