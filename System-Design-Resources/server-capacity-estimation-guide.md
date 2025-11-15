# Server Capacity Estimation & Back-of-Envelope Calculations Guide

**Master the art of estimating infrastructure requirements in system design interviews**

---

## Table of Contents
1. [Introduction](#introduction)
2. [Core Concepts & Units](#core-concepts--units)
3. [Step-by-Step Estimation Framework](#step-by-step-estimation-framework)
4. [Traffic Estimation](#traffic-estimation)
5. [Server Capacity Calculations](#server-capacity-calculations)
6. [Storage Estimation](#storage-estimation)
7. [Bandwidth Estimation](#bandwidth-estimation)
8. [Database Sizing](#database-sizing)
9. [Cache Sizing](#cache-sizing)
10. [Complete Examples](#complete-examples)
11. [Common Mistakes to Avoid](#common-mistakes-to-avoid)
12. [Interview Tips](#interview-tips)

---

## Introduction

**Why Capacity Estimation Matters in Interviews:**
- Demonstrates practical engineering thinking
- Shows you understand scale
- Proves you can work with constraints (budget, hardware limits)
- Critical for making architectural decisions

**Interview Expectation:**
- Rough estimates (order of magnitude)
- Show your thought process
- Use reasonable assumptions
- Round numbers for simplicity

---

## Core Concepts & Units

### Time Units
```
1 second     = 1,000 milliseconds (ms)
1 minute     = 60 seconds
1 hour       = 3,600 seconds
1 day        = 86,400 seconds     ≈ 100,000 seconds (for easy math)
1 month      = 2,592,000 seconds  ≈ 2.5 million seconds
1 year       = 31,536,000 seconds ≈ 30 million seconds
```

### Storage Units
```
1 Byte       = 8 bits
1 KB         = 1,000 Bytes        ≈ 10³ Bytes
1 MB         = 1,000 KB           ≈ 10⁶ Bytes
1 GB         = 1,000 MB           ≈ 10⁹ Bytes
1 TB         = 1,000 GB           ≈ 10¹² Bytes
1 PB         = 1,000 TB           ≈ 10¹⁵ Bytes
```

### Network Units
```
1 Kbps       = 1,000 bits/second
1 Mbps       = 1,000 Kbps         = 1 million bits/second
1 Gbps       = 1,000 Mbps         = 1 billion bits/second

Conversion: 1 Gbps = 125 MB/second (divide by 8)
```

### Power of 2 (Memory)
```
2¹⁰ = 1,024   ≈ 1 thousand    (1 KB)
2²⁰ = 1,048,576 ≈ 1 million   (1 MB)
2³⁰ = 1,073,741,824 ≈ 1 billion (1 GB)
```

### Typical Latencies (For Reference)
```
L1 Cache:              0.5 ns
L2 Cache:              7 ns
RAM:                   100 ns
SSD Read:              150 μs    = 0.15 ms
HDD Seek:              10 ms
Network (same DC):     0.5 ms
Network (US coast):    50 ms
Network (US-Europe):   150 ms
```

---

## Step-by-Step Estimation Framework

### The 5-Step Process

```
Step 1: CLARIFY REQUIREMENTS
  ↓
Step 2: ESTIMATE TRAFFIC (QPS)
  ↓
Step 3: CALCULATE STORAGE NEEDS
  ↓
Step 4: ESTIMATE BANDWIDTH
  ↓
Step 5: SIZE INFRASTRUCTURE (Servers, DB, Cache)
```

### Step 1: Clarify Requirements

**Always ask:**
- Daily Active Users (DAU)?
- Read:Write ratio?
- Data retention period?
- Peak vs average traffic?
- Geographic distribution?
- Latency requirements?

**Example Questions:**
> "How many daily active users do we expect?"
> "What's the typical read-to-write ratio?"
> "Do we need to support global traffic?"

### Step 2: Estimate Traffic (QPS)

**Formula:**
```
QPS = (DAU × Actions per User per Day) / 86,400 seconds
Peak QPS = Average QPS × Peak Factor (typically 2-3x)
```

**Example:**
```
DAU: 10 million users
Each user: 20 requests per day
Average QPS = (10M × 20) / 86,400 = 200M / 86,400 ≈ 2,315 QPS
Peak QPS = 2,315 × 3 = ~7,000 QPS
```

### Step 3: Calculate Storage Needs

**Formula:**
```
Total Storage = (New Data per Day) × (Retention Period in Days) × (Replication Factor)
```

**Example:**
```
Daily uploads: 1 million photos
Average photo size: 2 MB
Retention: 10 years
Replication: 3x

Storage = 1M × 2MB × 3,650 days × 3
        = 2TB × 3,650 × 3
        = 21.9 PB ≈ 22 PB
```

### Step 4: Estimate Bandwidth

**Formula:**
```
Bandwidth = (Average Request Size × QPS) / Network Speed
```

**Example:**
```
Average response: 100 KB
QPS: 10,000
Bandwidth = 100 KB × 10,000 = 1 GB/second = 8 Gbps
```

### Step 5: Size Infrastructure

**Server Count Formula:**
```
Servers Needed = (Peak QPS) / (QPS per Server)

Where QPS per Server depends on:
- CPU-bound: 1,000-10,000 QPS per server
- I/O-bound: 100-1,000 QPS per server
- Complex computation: 10-100 QPS per server
```

---

## Traffic Estimation

### Basic QPS Calculation

**Template:**
```
DAU (Daily Active Users): X
Actions per user per day: Y
Total daily requests = X × Y
Seconds in a day ≈ 100,000
Average QPS = (X × Y) / 100,000
```

### Read/Write Split

**Formula:**
```
Total QPS = Read QPS + Write QPS
Read QPS = Total QPS × (Read Ratio / 100)
Write QPS = Total QPS × (Write Ratio / 100)
```

**Example: Twitter-like System**
```
DAU: 100 million
Each user reads 100 tweets, posts 1 tweet per day
Read:Write ratio = 100:1

Read requests = 100M × 100 = 10 billion reads/day
Write requests = 100M × 1 = 100 million writes/day

Read QPS = 10B / 100,000 = 100,000 QPS
Write QPS = 100M / 100,000 = 1,000 QPS
Total = 101,000 QPS
```

### Peak Traffic Multiplier

```
Peak Factor by Application Type:
- E-commerce (holiday sales): 5-10x
- News sites (breaking news): 10-20x
- Social media: 2-3x
- B2B applications: 1.5-2x
- Gaming (launch/events): 10-50x
```

**Example:**
```
Average QPS: 10,000
Application: Social media
Peak QPS = 10,000 × 3 = 30,000 QPS
Design for: 30,000 QPS
```

---

## Server Capacity Calculations

### Method 1: QPS-Based Estimation

**Formula:**
```
Number of Servers = (Peak QPS) / (QPS per Server) × Safety Factor

Safety Factor: 1.5-2x for redundancy/failover
```

**Example: API Server Estimation**
```
Peak QPS: 30,000
QPS per server: 1,000 (typical for API server)
Safety factor: 1.5

Servers = (30,000 / 1,000) × 1.5 = 45 servers
```

### Method 2: Resource-Based Estimation

**CPU-Based:**
```
Assume: Each request uses 10ms CPU time
Server has: 8 cores
Max concurrent requests = 8 cores × (1000ms / 10ms) = 800 req/sec per server
```

**Memory-Based:**
```
Assume: Each request needs 50 MB memory
Server has: 64 GB RAM
Max concurrent requests = 64,000 MB / 50 MB = 1,280 requests
```

**Take the minimum** as your bottleneck.

### Typical Server Specs & Capacity

```
Small Server:
- 4 vCPUs, 16 GB RAM
- Capacity: 500-1,000 QPS (API server)
- Capacity: 100-500 QPS (DB queries)

Medium Server:
- 8 vCPUs, 32 GB RAM  
- Capacity: 1,000-2,000 QPS (API server)
- Capacity: 500-1,000 QPS (DB queries)

Large Server:
- 16 vCPUs, 64 GB RAM
- Capacity: 2,000-5,000 QPS (API server)
- Capacity: 1,000-2,000 QPS (DB queries)
```

### Load Balancer Capacity

```
Modern Load Balancer:
- Can handle: 100,000-1,000,000 QPS
- Typically not a bottleneck
- Use 2+ for high availability
```

---

## Storage Estimation

### Data Growth Calculation

**Formula:**
```
Total Storage = Initial Data + (Daily Growth × Days) × Replication × Overhead

Where:
- Replication: 3x (typical for HDFS, S3)
- Overhead: 1.2x (20% for metadata, indexes)
```

### Example 1: Photo Storage (Instagram-like)

```
Given:
- 500 million DAU
- 10% post photo daily = 50 million photos/day
- Average photo: 2 MB
- Retention: Forever
- Replication: 3x

Calculation:
Daily storage = 50M × 2 MB = 100 TB/day
Yearly storage = 100 TB × 365 = 36.5 PB/year
5-year storage = 36.5 PB × 5 = 182.5 PB

With replication = 182.5 PB × 3 = 547.5 PB ≈ 550 PB
```

### Example 2: Chat Messages (WhatsApp-like)

```
Given:
- 2 billion users
- 50 messages/user/day
- Average message: 100 bytes
- Retention: 5 years

Calculation:
Daily messages = 2B × 50 = 100 billion messages
Daily storage = 100B × 100 bytes = 10 TB/day
Yearly = 10 TB × 365 = 3.65 PB/year
5-year = 3.65 PB × 5 = 18.25 PB

With replication (3x) = 18.25 PB × 3 = 54.75 PB ≈ 55 PB
```

### Example 3: Video Storage (YouTube-like)

```
Given:
- 500 hours of video uploaded per minute
- Average bitrate: 5 Mbps (after compression)
- Retention: Forever

Calculation:
Per minute = 500 hours = 30,000 minutes of content
Per minute storage = 30,000 min × 60 sec × 5 Mbps / 8
                   = 30,000 × 60 × 5 / 8 MB
                   = 1,125,000 MB = 1,125 GB/minute

Daily = 1,125 GB × 60 × 24 = 1,620,000 GB = 1.6 PB/day
Yearly = 1.6 PB × 365 = 584 PB/year

With replication (3x) = 584 × 3 = 1,752 PB ≈ 1.8 EB/year
```

### Storage Hardware Planning

```
HDD (Bulk Storage):
- Capacity: 10-18 TB per disk
- Cost: ~$20/TB
- Use for: Cold storage, backups

SSD (Fast Storage):
- Capacity: 1-8 TB per disk
- Cost: ~$100/TB
- Use for: Hot data, databases

Object Storage (S3):
- Capacity: Unlimited
- Cost: $0.023/GB/month (Standard)
- Use for: Scalable storage, backups
```

---

## Bandwidth Estimation

### Ingress (Upload) Bandwidth

**Formula:**
```
Ingress Bandwidth = (Upload Size × Uploads per Second) × 8 bits
```

**Example: Photo Upload**
```
Photos uploaded: 1,000 per second
Average size: 2 MB
Bandwidth = 1,000 × 2 MB × 8 = 16 Gbps
```

### Egress (Download) Bandwidth

**Formula:**
```
Egress Bandwidth = (Response Size × QPS) × 8 bits
```

**Example: API Responses**
```
QPS: 100,000
Average response: 10 KB
Bandwidth = 100,000 × 10 KB × 8 = 8 Gbps
```

### Total Bandwidth

```
Total = Ingress + Egress

Typically:
- Read-heavy apps: Egress >> Ingress (10:1 ratio)
- Upload-heavy apps: Ingress >> Egress (1:10 ratio)
- Balanced apps: ~Equal
```

### CDN Bandwidth Savings

```
Without CDN: 100% traffic to origin servers
With CDN (90% cache hit): 10% traffic to origin

Example:
Total bandwidth needed: 100 Gbps
With CDN: 10 Gbps to origin, 90 Gbps from CDN
```

---

## Database Sizing

### Connection Pool Sizing

**Formula:**
```
Connection Pool Size = (Number of App Servers × Connections per Server)
Typically: 10-50 connections per app server
```

**Example:**
```
App servers: 100
Connections per server: 20
Total DB connections needed = 100 × 20 = 2,000 connections

MySQL max_connections = 2,000 (configure this)
```

### Database QPS Capacity

```
MySQL/PostgreSQL (Single Instance):
- Read: 10,000-50,000 QPS (with proper indexes)
- Write: 1,000-5,000 QPS
- Complex queries: 100-1,000 QPS

With Read Replicas:
- Scale reads linearly (5 replicas = 5x read capacity)
- Writes still limited by primary

Sharded Database:
- Scale both reads and writes linearly
- N shards = N × capacity
```

### Database Server Calculation

**Example: High-Read System**
```
Read QPS: 50,000
Write QPS: 5,000

Option 1: Read Replicas
- Primary (writes): 1 server for 5,000 write QPS ✓
- Replicas (reads): 50,000 / 10,000 = 5 read replicas
- Total: 6 database servers (1 primary + 5 replicas)

Option 2: Sharding
- Shards needed for writes: 5,000 / 5,000 = 1 shard (but need more for reads)
- Shards needed for reads: 50,000 / 10,000 = 5 shards
- Total: 5 shards (each handles 10,000 reads + 1,000 writes)
```

### Database Storage Calculation

**Formula:**
```
DB Storage = (Row Size × Number of Rows) × Index Overhead × Safety Buffer

Index Overhead: 1.5-2x (50-100% for indexes)
Safety Buffer: 1.5x (50% free space for operations)
```

**Example: User Database**
```
Users: 100 million
Row size: 1 KB (user profile data)
Indexes: 1.5x overhead
Buffer: 1.5x safety

Storage = 100M × 1 KB × 1.5 × 1.5
        = 100 GB × 2.25
        = 225 GB per database instance
```

---

## Cache Sizing

### Cache Size Estimation

**Formula:**
```
Cache Size = (Hot Data Size) × (Cache Hit Ratio) / (Compression Factor)

Hot Data: Most frequently accessed data (often 20% of total)
Cache Hit Ratio Target: 80-95%
Compression: 2-3x with compression enabled
```

### Example 1: Session Cache

```
Concurrent users: 1 million
Session data per user: 10 KB
Cache size = 1M × 10 KB = 10 GB

With Redis cluster (5 nodes) = 2 GB per node
```

### Example 2: Content Cache

```
Total content: 100 TB
Hot data (20%): 20 TB
Target cache hit: 90%
Compression: 2x

Cache needed = 20 TB × 0.9 / 2 = 9 TB

With Redis servers (64 GB each) = 9,000 / 64 = 141 servers
More practical: Use CDN for content caching
```

### Redis Capacity Planning

```
Redis Instance Specs:
- Small: 8 GB RAM = ~6 GB usable (75% due to overhead)
- Medium: 32 GB RAM = ~24 GB usable
- Large: 64 GB RAM = ~48 GB usable

Redis Cluster:
- Distribute data across multiple nodes
- Example: 1 TB cache = ~21 nodes (48 GB each)
```

### Cache Eviction Strategy

```
LRU (Least Recently Used): Most common
- Keep: Hot data
- Evict: Cold data

TTL (Time to Live):
- Short-lived data: 5-15 minutes
- Medium: 1-24 hours
- Long-lived: Days to weeks
```

---

## Complete Examples

### Example 1: Design Twitter

**Step 1: Clarify Requirements**
```
DAU: 200 million
Tweets per user per day: 2 (post) + 200 (read timeline)
Tweet size: 280 chars ≈ 280 bytes
Media (20% of tweets): 2 MB average
Retention: 5 years
```

**Step 2: Traffic Estimation**
```
Write QPS:
- Daily writes = 200M × 2 = 400M tweets/day
- Write QPS = 400M / 86,400 ≈ 4,600 tweets/sec
- Peak write QPS = 4,600 × 3 = 13,800 tweets/sec

Read QPS:
- Daily reads = 200M × 200 = 40B timeline views/day
- Read QPS = 40B / 86,400 ≈ 463,000 QPS
- Peak read QPS = 463,000 × 3 = 1.39M QPS
```

**Step 3: Storage Estimation**
```
Text tweets:
- 400M tweets/day × 280 bytes × 80% = 89.6 GB/day
- 5 years = 89.6 GB × 365 × 5 = 163.5 TB

Media:
- 400M tweets/day × 2 MB × 20% = 160 TB/day
- 5 years = 160 TB × 365 × 5 = 292 PB

Total with replication (3x):
- Text: 163.5 TB × 3 = 490 TB
- Media: 292 PB × 3 = 876 PB
- Total: ~880 PB
```

**Step 4: Server Estimation**
```
API Servers (Read):
- Peak read QPS: 1.39M
- QPS per server: 10,000 (with caching)
- Servers = 1,390,000 / 10,000 = 139 servers
- With safety factor (1.5x): 209 servers

API Servers (Write):
- Peak write QPS: 13,800
- QPS per server: 1,000
- Servers = 13,800 / 1,000 = 14 servers
- With safety factor: 21 servers

Total API servers: ~230 servers
```

**Step 5: Database & Cache**
```
Database:
- Write QPS: 13,800
- Shards needed: 13,800 / 5,000 = 3 shards
- Read replicas per shard: 463,000 / 3 / 10,000 = 16 replicas
- Total DB: 3 shards × (1 primary + 16 replicas) = 51 servers

Cache (Redis):
- Hot data: 20% of recent tweets (last 7 days)
- Recent tweets: 400M × 7 × 280 bytes = 784 GB
- Cache with 90% hit ratio: 705 GB
- Redis servers (64 GB each): 12 servers
```

**Step 6: Bandwidth**
```
Upload (ingress):
- Write: 13,800 tweets/sec × 280 bytes = 3.86 MB/sec
- Media: 13,800 × 0.2 × 2 MB = 5.52 GB/sec = 44 Gbps

Download (egress):
- Timeline reads: 1.39M QPS × 10 KB avg = 13.9 GB/sec = 111 Gbps
- With CDN (90% offload): 11 Gbps to origin
```

### Example 2: Design URL Shortener

**Step 1: Requirements**
```
URLs shortened: 100 million per month
URL reads: 100:1 read:write ratio
URL length: 100 characters
Short code length: 7 characters
Retention: 10 years
```

**Step 2: Traffic**
```
Write QPS:
- Monthly: 100M
- Per second: 100M / (30 × 86,400) ≈ 39 URLs/sec
- Peak: 39 × 2 = 78 URLs/sec

Read QPS:
- Read:Write = 100:1
- Read QPS = 39 × 100 = 3,900 QPS
- Peak: 7,800 QPS
```

**Step 3: Storage**
```
Total URLs in 10 years:
- URLs = 100M/month × 12 × 10 = 12 billion URLs

Storage per URL:
- Original URL: 100 bytes
- Short code: 7 bytes
- Metadata: 50 bytes
- Total: ~157 bytes ≈ 200 bytes

Total storage = 12B × 200 bytes = 2.4 TB
With replication (3x) = 7.2 TB
```

**Step 4: Server Capacity**
```
API Servers:
- Peak total QPS: 7,800
- QPS per server: 5,000 (simple lookups)
- Servers = 7,800 / 5,000 = 2 servers
- With redundancy: 3-4 servers

Database:
- QPS: 7,800 (mostly reads)
- Single DB can handle this
- Use 1 primary + 2-3 read replicas
- Total: 4 DB servers
```

**Step 5: Cache**
```
Cache hot URLs (80/20 rule):
- Hot URLs: 20% of total = 2.4B URLs
- Storage: 2.4B × 200 bytes = 480 GB
- Cache with 90% hit: 432 GB
- Redis servers: 8 servers (64 GB each)
```

### Example 3: Design Instagram

**Requirements:**
```
DAU: 500 million
Photos per user per day: 2 (upload) + 50 (view)
Photo size: 2 MB average
Storage retention: Forever
```

**Traffic:**
```
Upload QPS:
- Daily uploads: 500M × 2 = 1B photos/day
- Upload QPS = 1B / 86,400 ≈ 11,574 photos/sec
- Peak: 35,000 photos/sec

View QPS:
- Daily views: 500M × 50 = 25B views/day
- View QPS = 25B / 86,400 ≈ 289,352 QPS
- Peak: 868,000 QPS
```

**Storage (10 years):**
```
Daily: 1B × 2 MB = 2 PB/day
Yearly: 2 PB × 365 = 730 PB/year
10 years: 7.3 EB
With replication (3x): 22 EB
```

**Servers:**
```
Upload servers: 35,000 / 500 = 70 servers
View servers (with CDN 95% hit): 868,000 × 0.05 / 10,000 = 5 servers
Storage servers: Use cloud object storage (S3/GCS)
```

---

## Common Mistakes to Avoid

### ❌ Mistake 1: Not Rounding Numbers
```
Bad: "We need 2,347.6 servers"
Good: "We need approximately 2,400 servers, let's round to 2,500 for safety"
```

### ❌ Mistake 2: Forgetting Peak Traffic
```
Bad: Calculate only for average QPS
Good: Always calculate peak (2-3x average) and design for peak
```

### ❌ Mistake 3: Ignoring Replication
```
Bad: "We need 100 TB storage"
Good: "We need 100 TB × 3 (replication) = 300 TB"
```

### ❌ Mistake 4: Not Accounting for Safety Buffer
```
Bad: "1,000 QPS needs exactly 1 server"
Good: "1,000 QPS needs 1-2 servers for redundancy and failover"
```

### ❌ Mistake 5: Mixing Units
```
Bad: "Bandwidth is 100 MB" (MB what? Per second? Total?)
Good: "Bandwidth is 100 MB/second = 800 Mbps"
```

### ❌ Mistake 6: Overly Precise Calculations
```
Bad: "Exactly 2,347.632 servers needed"
Good: "Approximately 2,300-2,500 servers needed"
```

### ❌ Mistake 7: Not Explaining Assumptions
```
Bad: "We need 100 servers" (why?)
Good: "Assuming 1,000 QPS per server and 100,000 total QPS, we need 100 servers"
```

---

## Interview Tips

### Calculation Shortcuts

**Use Powers of 10:**
```
1 million = 10^6 = 1M
1 billion = 10^9 = 1B
1 trillion = 10^12 = 1T
```

**Round for Easy Math:**
```
86,400 seconds/day ≈ 100,000 (easier to divide)
365 days/year ≈ 400 (easier for rough estimates)
2.5 million seconds/month (use this directly)
```

**Common Conversions:**
```
1 GB/sec = 8 Gbps
1 Gbps = 125 MB/sec
1 million QPS × 1 KB = 1 GB/sec bandwidth
```

### Interview Framework

**Step 1: State Assumptions Clearly**
```
"Let me assume we have 100 million daily active users..."
"I'll assume the average image size is 2 MB..."
```

**Step 2: Show Your Math**
```
"So that's 100 million users × 10 posts per day = 1 billion posts per day
1 billion divided by roughly 100,000 seconds = 10,000 posts per second"
```

**Step 3: Round and Simplify**
```
"That's 10,247 QPS, let's round to 10,000 for simplicity"
```

**Step 4: Add Safety Margin**
```
"For peak traffic and redundancy, I'd design for 2-3x this, so 30,000 QPS"
```

**Step 5: Validate Reasonableness**
```
"Does 30,000 QPS seem reasonable? That's 30 servers if each handles 1,000 QPS"
```

### Sample Interview Dialogue

**Interviewer:** "How many servers do we need for Instagram?"

**You:** 
> "Let me break this down step by step. First, let me clarify the scale: 
>
> **Assumptions:**
> - 500 million daily active users
> - Each user views 50 photos per day
> - That's 25 billion photo views per day
> 
> **QPS Calculation:**
> - 25 billion requests / 100,000 seconds per day = 250,000 QPS average
> - For peak traffic, let's multiply by 3x = 750,000 QPS
> 
> **With CDN:**
> - CDN can cache 95% of image requests
> - Origin servers handle 5% = 37,500 QPS
> 
> **Server Sizing:**
> - Each server can handle ~5,000 QPS (image serving)
> - Servers needed: 37,500 / 5,000 = 7.5
> - Rounding up with redundancy: 10-12 origin servers
> 
> **Plus:**
> - 20-30 servers for upload/metadata APIs
> - Load balancers, databases, caches separately
> 
> So roughly 30-40 application servers, plus infrastructure."

### When You Don't Know Something

**Good Responses:**
```
✅ "I'm not sure about the exact capacity, but I'd estimate based on industry standards..."
✅ "Let me assume a typical server handles X QPS, we can adjust if needed"
✅ "I'd benchmark this in practice, but for estimation purposes..."
```

**Bad Responses:**
```
❌ "I don't know" (and stop there)
❌ Making up random numbers without reasoning
❌ Being overly confident in wrong assumptions
```

### Cheat Sheet for Common Scenarios

```
Small App (< 10K users):
- 1-2 servers
- 1 database
- No caching needed

Medium App (100K users):
- 5-10 application servers
- 1 primary DB + 2 replicas
- 1-2 cache servers

Large App (1M users):
- 20-50 application servers
- Database sharding (3-5 shards)
- Cache cluster (5-10 servers)
- CDN required

Very Large App (10M+ users):
- 100+ application servers
- 10+ database shards
- 20+ cache servers
- Multi-region CDN
- Microservices architecture
```

---

## Quick Reference Tables

### Typical Server Capacities
| Resource | Capacity |
|----------|----------|
| API Server (simple) | 5,000-10,000 QPS |
| API Server (complex) | 1,000-2,000 QPS |
| Database (reads) | 10,000-50,000 QPS |
| Database (writes) | 1,000-5,000 QPS |
| Redis Cache | 100,000+ ops/sec |
| Load Balancer | 100,
