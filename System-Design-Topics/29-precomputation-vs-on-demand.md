# 🎯 Topic 29: Pre-Computation vs On-Demand

> **System Design Interview — Deep Dive**
> When to compute at write time vs read time, pre-computed aggregates, the 80/20 rule for pre-computation, and the write amplification tradeoff.

---

## Table of Contents

- [🎯 Topic 29: Pre-Computation vs On-Demand](#-topic-29-pre-computation-vs-on-demand)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Pre-Computation (Compute at Write Time)](#pre-computation-compute-at-write-time)
    - [How It Works](#how-it-works)
    - [Advantages](#advantages)
    - [Disadvantages](#disadvantages)
    - [Interview Script](#interview-script)
  - [On-Demand (Compute at Read Time)](#on-demand-compute-at-read-time)
    - [How It Works](#how-it-works-1)
    - [Advantages](#advantages-1)
    - [Disadvantages](#disadvantages-1)
    - [When On-Demand Is Fine](#when-on-demand-is-fine)
  - [The Tradeoff: Write Amplification vs Read Latency](#the-tradeoff-write-amplification-vs-read-latency)
  - [Pre-Compute the Common Case, On-Demand for the Long Tail](#pre-compute-the-common-case-on-demand-for-the-long-tail)
    - [The 80/20 Strategy](#the-8020-strategy)
    - [Interview Script](#interview-script-1)
  - [Examples by System](#examples-by-system)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [Pre-Compute for SLA](#pre-compute-for-sla)
    - [Common + Long Tail](#common--long-tail)
  - [Common Interview Mistakes](#common-interview-mistakes)
    - [❌ Pre-computing everything (wasteful for rarely-accessed data)](#-pre-computing-everything-wasteful-for-rarely-accessed-data)
    - [❌ Not pre-computing dashboard metrics (users won't wait 10 seconds)](#-not-pre-computing-dashboard-metrics-users-wont-wait-10-seconds)
    - [❌ Not mentioning write amplification as the cost of pre-computation](#-not-mentioning-write-amplification-as-the-cost-of-pre-computation)
    - [❌ Not distinguishing between common case and long tail](#-not-distinguishing-between-common-case-and-long-tail)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Pre-computation**: Do the work at **write time** so reads are instant.
**On-demand**: Do the work at **read time** — only when someone asks.

```
Pre-computed dashboard:
  Write: Each click → HINCRBY campaign:123:clicks 1 (3 writes per event)
  Read:  Dashboard load → HGET campaign:123:clicks → < 1ms

On-demand dashboard:
  Write: Each click → store raw event (1 write)
  Read:  Dashboard load → scan millions of events → GROUP BY → 5-10 seconds
```

---

## Pre-Computation (Compute at Write Time)

### How It Works
```
Every incoming event updates pre-computed aggregates:

Ad click event arrives:
  HINCRBY campaign:123:2025-03-15:14:35 clicks 1    (minute bucket)
  HINCRBY campaign:123:2025-03-15:14 clicks 1        (hour bucket)
  HINCRBY campaign:123:2025-03-15 clicks 1            (day bucket)

Dashboard query "today's clicks for campaign 123":
  HGET campaign:123:2025-03-15 clicks → 68,328 → returned in < 1ms
```

### Advantages
- **Instant reads**: < 1ms for pre-computed results.
- **Predictable latency**: Read time doesn't depend on data volume.
- **Scalable reads**: Pre-computed results cached in Redis — millions of reads/sec.

### Disadvantages
- **Write amplification**: 1 event → 3 writes (minute, hour, day buckets).
- **Storage cost**: Pre-computed aggregates for every dimension.
- **Rigidity**: Can only answer pre-computed questions. New questions need new aggregates.
- **Complexity**: Must handle late events, corrections, backfills.

### Interview Script
> *"I'd pre-compute the dashboard aggregates at write time. Each incoming ad event atomically increments counters for minute, hour, and day buckets in Redis. When the advertiser opens their dashboard, the query is just a `HGET` — < 1ms. Without pre-computation, we'd scan millions of raw events for each dashboard load, taking 5-10 seconds. The tradeoff: 3 Redis writes per event (write amplification), but at our dashboard query SLA of < 500ms, pre-computation is non-negotiable."*

---

## On-Demand (Compute at Read Time)

### How It Works
```
Events stored as raw data:
  INSERT INTO clicks (campaign_id, ad_id, timestamp, user_id, ...) VALUES (...)

Dashboard query:
  SELECT campaign_id, COUNT(*) as clicks
  FROM clicks
  WHERE campaign_id = 123 AND date = '2025-03-15'
  GROUP BY campaign_id
  → Scans all matching rows → aggregates → returns result
```

### Advantages
- **No write amplification**: Raw events stored once.
- **Flexible queries**: Any question can be answered from raw data.
- **Simple writes**: Just store the raw event.
- **No pre-computation logic**: No aggregation pipelines to maintain.

### Disadvantages
- **Slow reads**: Must scan and aggregate at query time.
- **Unpredictable latency**: Depends on data volume and query complexity.
- **Resource-intensive**: Each query consumes CPU, memory, I/O.

### When On-Demand Is Fine
- **Infrequent queries**: Monthly reports run by 5 people → batch job is acceptable.
- **Exploratory analytics**: Ad-hoc questions that can't be pre-computed.
- **Small datasets**: < 1M rows → aggregation is fast enough on-demand.

---

## The Tradeoff: Write Amplification vs Read Latency

```
┌─────────────────────────────────────────────────┐
│                                                 │
│  Pre-Computed           On-Demand               │
│  ─────────────         ──────────               │
│  Write cost: HIGH       Write cost: LOW          │
│  (3 writes/event)       (1 write/event)          │
│                                                 │
│  Read cost: LOW         Read cost: HIGH          │
│  (< 1ms lookup)        (5-10s scan)             │
│                                                 │
│  Flexibility: LOW       Flexibility: HIGH        │
│  (fixed dimensions)     (any query)              │
│                                                 │
│  Dashboard SLA < 500ms → Pre-compute (only option)│
│  Monthly report → On-demand (simpler)            │
│                                                 │
└─────────────────────────────────────────────────┘
```

---

## Pre-Compute the Common Case, On-Demand for the Long Tail

### The 80/20 Strategy
```
Pre-compute what 90% of users need:
  Global top-100 leaderboard → Redis sorted set (ZADD on score update)
  Dashboard daily/hourly aggregates → Redis hash counters
  Feed timelines → Redis sorted sets (fanout on write)

Compute on-demand for the long tail:
  "What's my exact rank out of 100M players?" → ZRANK (computed per request)
  "Show me clicks filtered by custom date range + geo" → ClickHouse query
  Ad-hoc analytics queries → Spark/Athena
```

### Interview Script
> *"I'd pre-compute what 90% of users need, and compute on-demand for the long tail. The global top-100 leaderboard is pre-computed in a Redis sorted set — `ZADD` on every score update, `ZREVRANGE` for the top 100. But 'what's my exact rank out of 100 million players?' is computed on-demand via `ZRANK` — it's still O(log N) in Redis, but it's only called when a specific user asks for it, which is much rarer than loading the top-100 homepage widget."*

---

## Examples by System

| System | Pre-Computed | On-Demand |
|---|---|---|
| **Ad Dashboard** | Daily/hourly click counts per campaign | Custom date range + geo filter |
| **Leaderboard** | Top 100 global ranking | Individual user's rank |
| **Twitter Feed** | Pre-built timeline (fanout on write) | Celebrity tweets merged at read |
| **Analytics** | Minute/hour/day aggregates | Ad-hoc SQL queries |
| **Search** | Inverted index (built on write) | Query execution (at read time) |
| **Recommendations** | Pre-computed candidate set | Final ranking per user at read |
| **Trending Topics** | Top 50 trending (recomputed every 5 min) | Trend history for specific hashtag |

---

## Interview Talking Points & Scripts

### Pre-Compute for SLA
> *"Pre-compute dashboard aggregates at write time. 3 Redis writes per event. Dashboard query is a HGET — < 1ms. Without pre-computation: scan millions of events → 5-10 seconds. At our SLA of < 500ms, pre-computation is non-negotiable."*

### Common + Long Tail
> *"Pre-compute the top 100 leaderboard (viewed millions of times). Compute individual rank on-demand (viewed thousands of times). Optimize for the common case."*

---

## Common Interview Mistakes

### ❌ Pre-computing everything (wasteful for rarely-accessed data)
### ❌ Not pre-computing dashboard metrics (users won't wait 10 seconds)
### ❌ Not mentioning write amplification as the cost of pre-computation
### ❌ Not distinguishing between common case and long tail

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│         PRE-COMPUTATION vs ON-DEMAND CHEAT SHEET             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PRE-COMPUTE WHEN:                                           │
│    Read SLA < 500ms (dashboards, feeds, leaderboards)        │
│    High read:write ratio (computed once, read millions)      │
│    Dimensions are known (fixed aggregation buckets)          │
│                                                              │
│  ON-DEMAND WHEN:                                             │
│    Queries are unpredictable (ad-hoc analytics)              │
│    Data volume is small (< 1M rows, fast enough)             │
│    Rarely accessed (monthly reports)                         │
│                                                              │
│  HYBRID: Pre-compute 90% (common), on-demand 10% (long tail)│
│                                                              │
│  COST: Pre-computation = write amplification (3x writes)     │
│        On-demand = read amplification (scan millions)        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
