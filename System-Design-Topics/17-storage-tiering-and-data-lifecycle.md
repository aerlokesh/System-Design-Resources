# 🎯 Topic 17: Storage Tiering & Data Lifecycle

> **System Design Interview — Deep Dive**
> A comprehensive guide covering hot/warm/cold/archive storage tiers, data rollup and downsampling, query routing across tiers, cost optimization, and TTL-based data lifecycle management.

---

## Table of Contents

- [🎯 Topic 17: Storage Tiering \& Data Lifecycle](#-topic-17-storage-tiering--data-lifecycle)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [The 4-Tier Storage Model](#the-4-tier-storage-model)
  - [Hot Tier — Redis / In-Memory](#hot-tier--redis--in-memory)
    - [Characteristics](#characteristics)
    - [What to Store](#what-to-store)
    - [Data Structures](#data-structures)
  - [Warm Tier — SSD-Based Databases](#warm-tier--ssd-based-databases)
    - [Characteristics](#characteristics-1)
    - [What to Store](#what-to-store-1)
    - [Rollup from Hot to Warm](#rollup-from-hot-to-warm)
  - [Cold Tier — HDD-Based Databases](#cold-tier--hdd-based-databases)
    - [Characteristics](#characteristics-2)
    - [Rollup from Warm to Cold](#rollup-from-warm-to-cold)
  - [Archive Tier — Object Storage (S3/Glacier)](#archive-tier--object-storage-s3glacier)
    - [Characteristics](#characteristics-3)
    - [S3 Storage Classes](#s3-storage-classes)
    - [What to Store](#what-to-store-2)
  - [Data Rollup \& Downsampling](#data-rollup--downsampling)
    - [The Process](#the-process)
    - [Interview Script](#interview-script)
  - [Query Router — Transparent Multi-Tier Access](#query-router--transparent-multi-tier-access)
    - [How It Works](#how-it-works)
    - [The User Doesn't Know](#the-user-doesnt-know)
    - [Interview Script](#interview-script-1)
  - [Cost Comparison Across Tiers](#cost-comparison-across-tiers)
    - [Example: 200 Billion Events Per Day](#example-200-billion-events-per-day)
    - [Interview Script](#interview-script-2)
  - [TTL-Based Data Lifecycle](#ttl-based-data-lifecycle)
    - [Automatic Expiration](#automatic-expiration)
    - [S3 Lifecycle Policy (JSON)](#s3-lifecycle-policy-json)
  - [Data Migration Between Tiers](#data-migration-between-tiers)
    - [Automated Nightly Job](#automated-nightly-job)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [The 4-Tier Architecture](#the-4-tier-architecture)
    - [Cost Justification](#cost-justification)
  - [Common Interview Mistakes](#common-interview-mistakes)
    - [❌ Mistake 1: One storage tier for everything](#-mistake-1-one-storage-tier-for-everything)
    - [❌ Mistake 2: Not mentioning rollup/downsampling](#-mistake-2-not-mentioning-rollupdownsampling)
    - [❌ Mistake 3: Not mentioning the query router](#-mistake-3-not-mentioning-the-query-router)
    - [❌ Mistake 4: Not quantifying cost savings](#-mistake-4-not-quantifying-cost-savings)
  - [Storage Tiering by System Design Problem](#storage-tiering-by-system-design-problem)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Not all data is accessed equally. **Recent, frequently accessed data** should be on fast, expensive storage. **Old, rarely accessed data** should be on slow, cheap storage. Storage tiering matches **data access frequency to storage cost**.

```
Access frequency over time:
  Day 1:    ████████████████  (100% of queries)
  Day 7:    ████████          (50% of queries)
  Day 30:   ██                (10% of queries)
  Day 365:  ▌                 (< 1% of queries)

Optimal storage:
  Day 1:    Redis ($25/GB/month)      → sub-ms response
  Day 7:    ClickHouse SSD ($0.10/GB) → 200ms response
  Day 30:   ClickHouse HDD ($0.03/GB) → 2-5s response
  Day 365:  S3 Glacier ($0.004/GB)    → minutes to hours
```

---

## The 4-Tier Storage Model

```
┌─────────────────────────────────────────────────────────────┐
│                    4-TIER STORAGE MODEL                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  HOT (0-24 hours)                                           │
│  ├── Storage: Redis (in-memory)                             │
│  ├── Granularity: Minute-level                              │
│  ├── Latency: < 10ms                                        │
│  ├── Cost: $25/GB/month                                     │
│  └── Use: Real-time dashboards, live metrics                │
│                                                             │
│  WARM (1-30 days)                                           │
│  ├── Storage: ClickHouse on SSD                             │
│  ├── Granularity: Hourly                                    │
│  ├── Latency: 200ms                                         │
│  ├── Cost: $0.10/GB/month                                   │
│  └── Use: Historical queries, weekly reports                │
│                                                             │
│  COLD (30 days - 2 years)                                   │
│  ├── Storage: ClickHouse on HDD                             │
│  ├── Granularity: Daily                                     │
│  ├── Latency: 2-5 seconds                                   │
│  ├── Cost: $0.03/GB/month                                   │
│  └── Use: Monthly reports, trend analysis                   │
│                                                             │
│  ARCHIVE (> 30 days raw, > 2 years aggregated)              │
│  ├── Storage: S3 Glacier / S3 IA                            │
│  ├── Granularity: Raw events (compressed)                   │
│  ├── Latency: Minutes to hours                              │
│  ├── Cost: $0.004/GB/month                                  │
│  └── Use: Compliance, audit, replay                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Hot Tier — Redis / In-Memory

### Characteristics
```
Storage: Redis, Memcached, or in-memory databases
Data age: Last 24 hours (configurable)
Granularity: Minute-level or raw events
Latency: < 10ms (typically < 1ms)
Cost: ~$25/GB/month (RAM is expensive)
```

### What to Store
- Real-time dashboard counters (clicks, impressions, revenue per minute).
- Active user sessions.
- Rate limiting counters.
- Pre-computed timeline caches.
- Leaderboard data.

### Data Structures
```
Metrics: Redis Hash
  HINCRBY campaign:123:2025-03-15:14:35 clicks 1
  HINCRBY campaign:123:2025-03-15:14:35 impressions 50

Timelines: Redis Sorted Set
  ZADD timeline:user:456 1710500000 tweet_789

Counters: Redis String
  INCR likes:tweet:123
```

---

## Warm Tier — SSD-Based Databases

### Characteristics
```
Storage: ClickHouse on SSD, PostgreSQL, TimescaleDB
Data age: 1-30 days
Granularity: Hourly aggregates (rolled up from minute data)
Latency: 100-500ms
Cost: ~$0.10/GB/month
```

### What to Store
- Hourly aggregated metrics.
- Recent historical queries ("last 7 days, hourly breakdown").
- Operational dashboards with adjustable time ranges.

### Rollup from Hot to Warm
```
Nightly job (3 AM UTC):
  For each metric in Redis (minute-level):
    Aggregate minute → hourly:
      SUM(clicks) for 14:00-14:59 → hourly_clicks_14
      SUM(clicks) for 15:00-15:59 → hourly_clicks_15
    Write hourly aggregates to ClickHouse SSD
    Delete minute-level data from Redis (older than 24h)

Storage reduction: 60x (60 minutes → 1 hourly row)
```

---

## Cold Tier — HDD-Based Databases

### Characteristics
```
Storage: ClickHouse on HDD, S3 + Athena, BigQuery
Data age: 30 days - 2 years
Granularity: Daily aggregates (rolled up from hourly)
Latency: 2-10 seconds
Cost: ~$0.03/GB/month
```

### Rollup from Warm to Cold
```
Monthly job:
  For data older than 30 days in ClickHouse SSD:
    Aggregate hourly → daily:
      SUM(clicks) for all hours on 2025-03-15 → daily_clicks
    Write daily aggregates to ClickHouse HDD
    Delete hourly data from SSD (older than 30 days)

Storage reduction: 24x (24 hours → 1 daily row)
Total reduction from raw: 60 × 24 = 1,440x
```

---

## Archive Tier — Object Storage (S3/Glacier)

### Characteristics
```
Storage: S3 Standard, S3 IA, S3 Glacier, S3 Glacier Deep Archive
Data age: > 30 days (raw events), > 2 years (aggregates)
Granularity: Raw events (compressed Parquet/ORC files)
Latency: Seconds (S3 Standard) to hours (Glacier Deep Archive)
Cost: $0.004-$0.023/GB/month
```

### S3 Storage Classes

| Class | Cost/GB/month | Retrieval Time | Use Case |
|---|---|---|---|
| **S3 Standard** | $0.023 | Instant | Frequently accessed raw data |
| **S3 IA** | $0.0125 | Instant | Monthly access |
| **S3 Glacier IR** | $0.004 | Milliseconds | Quarterly access |
| **S3 Glacier** | $0.004 | 3-5 hours | Annual access, compliance |
| **Glacier Deep** | $0.00099 | 12-48 hours | 7-year compliance retention |

### What to Store
- Raw event logs (for Kafka replay, audit, compliance).
- Compressed data lake files (Parquet format).
- Backups and snapshots.
- Regulatory data retention (GDPR: "right to access" data).

---

## Data Rollup & Downsampling

### The Process
```
Raw events (2.3M/sec):
  {campaign_id: 123, ad_id: 456, event: "click", timestamp: 14:35:22.123}

Minute rollup (in Redis):
  campaign:123:ad:456:2025-03-15:14:35 → {clicks: 47, impressions: 2340}

Hourly rollup (in ClickHouse SSD):
  campaign:123:ad:456:2025-03-15:14 → {clicks: 2847, impressions: 140,200}

Daily rollup (in ClickHouse HDD):
  campaign:123:ad:456:2025-03-15 → {clicks: 68,328, impressions: 3,364,800}

Storage comparison (per campaign per day):
  Raw:    ~5 GB (millions of individual events)
  Minute: ~80 MB (1440 minute-level rows)
  Hourly: ~3.3 MB (24 hourly rows)
  Daily:  ~140 KB (1 daily row)

Total reduction: ~35,000x from raw to daily
```

### Interview Script
> *"I'd use a 4-tier storage strategy. Hot tier: Redis for the last 24 hours at minute granularity — dashboard queries get sub-10ms response. Warm tier: ClickHouse on SSD for 1-30 days at hourly granularity — historical queries in 200ms. Cold tier: ClickHouse on HDD for 30 days to 2 years at daily granularity — reports in 2-5 seconds. Archive tier: S3 Glacier for raw events older than 30 days — $0.004/GB/month for compliance retention. A nightly rollup job aggregates minute → hour and hour → day, reducing storage by 60x for data older than 24 hours."*

---

## Query Router — Transparent Multi-Tier Access

### How It Works
```
User query: "Show me clicks for campaign 123, last 7 days, hourly breakdown"

Query Router logic:
  1. Today's data (0-24h):  → Redis (minute-level, aggregate to hourly on-the-fly)
  2. Days 2-7 data:         → ClickHouse SSD (pre-aggregated hourly)
  3. Merge results
  4. Return seamless response

User query: "Show me clicks for campaign 123, last 90 days, daily breakdown"
  1. Today + yesterday:     → Redis (aggregate to daily on-the-fly)
  2. Days 3-30:            → ClickHouse SSD (aggregate hourly → daily on-the-fly)
  3. Days 31-90:           → ClickHouse HDD (pre-aggregated daily)
  4. Merge results
  5. Return seamless response
```

### The User Doesn't Know
The user sees one API, one response. They have no idea that three different storage systems served their query. The query router handles all the complexity.

### Interview Script
> *"The query router transparently handles tier boundaries. When an advertiser queries 'last 7 days, hourly breakdown', the router reads today's data from Redis (minute-level, aggregated to hourly on the fly) and the other 6 days from ClickHouse SSD (pre-aggregated hourly). It merges the results and returns a seamless response. The advertiser has no idea that two different storage systems served their query."*

---

## Cost Comparison Across Tiers

### Example: 200 Billion Events Per Day

```
Storage costs if everything on one tier:
  All in Redis:        $500K/month  (insane)
  All on SSD:          $50K/month   (expensive)
  All on HDD:          $15K/month   (slow for recent data)
  All in S3:           $3K/month    (way too slow)

Tiered storage:
  Redis (24h, minute):  $2K/month
  SSD (30d, hourly):    $5K/month
  HDD (2y, daily):      $5K/month
  S3 (raw, compressed): $3K/month
  Total:                $15K/month

Savings vs all-SSD:     $35K/month (70% reduction)
Savings vs all-Redis:   $485K/month (97% reduction)
```

### Interview Script
> *"Tiered storage is how we keep the analytics pipeline affordable at 200 billion events per day. Raw events in S3: $0.023/GB/month. Same data on SSD in ClickHouse: $0.10/GB/month. In Redis: $25/GB/month. By keeping only the last 24 hours in Redis, 30 days in ClickHouse SSD, and everything else in S3, our monthly storage cost is ~$15K instead of ~$500K if we kept everything on SSD. The 33x savings come from matching the storage tier to the data's access frequency."*

---

## TTL-Based Data Lifecycle

### Automatic Expiration
```
Redis:
  SET metric:campaign:123:minute:2025-03-15-14-35 "..." EX 86400
  → Auto-expires after 24 hours

Cassandra:
  INSERT INTO metrics (...) USING TTL 2592000;  -- 30 days
  → Row automatically deleted after TTL

S3 Lifecycle Rules:
  Rule 1: After 30 days → move to S3 IA
  Rule 2: After 90 days → move to S3 Glacier
  Rule 3: After 7 years → delete (regulatory requirement met)
```

### S3 Lifecycle Policy (JSON)
```json
{
  "Rules": [
    {
      "ID": "tier-down-to-IA",
      "Status": "Enabled",
      "Transitions": [
        { "Days": 30, "StorageClass": "STANDARD_IA" },
        { "Days": 90, "StorageClass": "GLACIER" },
        { "Days": 2555, "StorageClass": "DEEP_ARCHIVE" }
      ],
      "Expiration": { "Days": 2555 }
    }
  ]
}
```

---

## Data Migration Between Tiers

### Automated Nightly Job
```
3:00 AM UTC - Rollup & Tier Migration:

Step 1: Aggregate Redis minute → ClickHouse hourly
  For each expired minute bucket in Redis:
    SUM, COUNT, AVG → write to ClickHouse SSD

Step 2: Aggregate ClickHouse hourly → daily (for data > 30 days)
  GROUP BY day → write to ClickHouse HDD
  DELETE hourly data older than 30 days from SSD

Step 3: Archive raw events to S3
  Compress raw Kafka events → Parquet files → S3

Step 4: S3 lifecycle auto-manages Glacier transitions

Monitoring: Track rollup job duration, data consistency checks.
```

---

## Interview Talking Points & Scripts

### The 4-Tier Architecture
> *"Hot: Redis (24h, minute, <10ms). Warm: ClickHouse SSD (30d, hourly, 200ms). Cold: ClickHouse HDD (2y, daily, 2-5s). Archive: S3 Glacier (raw, compliance). Nightly rollup reduces storage 60x per tier transition."*

### Cost Justification
> *"Tiered storage saves us $485K/month compared to keeping everything in Redis. We match storage tier to access frequency — only the hottest data gets the most expensive storage."*

---

## Common Interview Mistakes

### ❌ Mistake 1: One storage tier for everything
**Fix**: Different access patterns need different storage. Hot data in Redis, cold data in S3.

### ❌ Mistake 2: Not mentioning rollup/downsampling
**Fix**: Minute → hour → day aggregation reduces storage 1,440x.

### ❌ Mistake 3: Not mentioning the query router
**Fix**: The router transparently spans tiers — user sees one seamless API.

### ❌ Mistake 4: Not quantifying cost savings
**Fix**: Show the $/GB/month difference between tiers and total savings.

---

## Storage Tiering by System Design Problem

| Problem | Hot Tier | Warm Tier | Cold Tier | Archive |
|---|---|---|---|---|
| **Ad Click Aggregator** | Redis (minute) | ClickHouse SSD (hourly) | ClickHouse HDD (daily) | S3 (raw events) |
| **Metrics/Monitoring** | Redis (1-min resolution) | TimescaleDB (hourly) | S3 + Athena (daily) | S3 Glacier |
| **Chat Messages** | Redis (recent conversations) | Cassandra (all messages) | — | S3 (old archives) |
| **User Activity** | Redis (online status) | PostgreSQL (30d history) | — | S3 (compliance) |
| **Video Metadata** | Redis (trending) | PostgreSQL (catalog) | — | S3 (removed content) |
| **Log Analytics** | Elasticsearch (7d) | S3 + Athena (30d) | — | S3 Glacier (1y) |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│         STORAGE TIERING & DATA LIFECYCLE CHEAT SHEET         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  HOT:     Redis, 0-24h, minute, <10ms,  $25/GB/month        │
│  WARM:    ClickHouse SSD, 1-30d, hourly, 200ms, $0.10/GB    │
│  COLD:    ClickHouse HDD, 30d-2y, daily, 2-5s, $0.03/GB    │
│  ARCHIVE: S3 Glacier, >30d raw, min-hrs, $0.004/GB          │
│                                                              │
│  ROLLUP: minute → hour (60x), hour → day (24x)              │
│  TOTAL REDUCTION: ~1,440x from raw to daily                  │
│                                                              │
│  QUERY ROUTER: Spans tiers transparently                     │
│  USER SEES: One API, one response, multiple backends         │
│                                                              │
│  COST SAVINGS: 70-97% vs single-tier storage                 │
│                                                              │
│  TTL: Redis EX, Cassandra TTL, S3 Lifecycle Rules            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
