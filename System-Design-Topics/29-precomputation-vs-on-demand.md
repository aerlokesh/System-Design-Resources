# 🎯 Topic 29: Pre-Computation vs On-Demand

> **System Design Interview — Deep Dive**
> A comprehensive guide covering when to compute at write time vs read time, pre-computed aggregates, the 80/20 rule for pre-computation, write amplification tradeoffs, materialized views, Lambda architecture, and how to articulate pre-computation decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Pre-Computation: Compute at Write Time](#pre-computation-compute-at-write-time)
3. [On-Demand: Compute at Read Time](#on-demand-compute-at-read-time)
4. [The Tradeoff: Write Amplification vs Read Latency](#the-tradeoff-write-amplification-vs-read-latency)
5. [Pre-Compute the Common Case, On-Demand for the Long Tail](#pre-compute-the-common-case-on-demand-for-the-long-tail)
6. [Materialized Views](#materialized-views)
7. [Lambda Architecture: Pre-Computed + On-Demand Together](#lambda-architecture-pre-computed--on-demand-together)
8. [Pre-Computation Patterns by Data Structure](#pre-computation-patterns-by-data-structure)
9. [Handling Late Events and Corrections](#handling-late-events-and-corrections)
10. [Real-World System Examples](#real-world-system-examples)
11. [Deep Dive: Applying Pre-Computation to Popular Problems](#deep-dive-applying-pre-computation-to-popular-problems)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Pre-computation**: Do the work at **write time** so reads are instant.
**On-demand**: Do the work at **read time** — only when someone asks.

The decision depends on a simple ratio: **how many times is the result read vs how often does the input change?**

```
Pre-computed dashboard:
  Write: Each ad click → HINCRBY campaign:123:clicks 1 (3 writes per event)
  Read:  Dashboard load → HGET campaign:123:clicks → < 1ms

On-demand dashboard:
  Write: Each ad click → store raw event (1 write per event)
  Read:  Dashboard load → scan millions of events → GROUP BY → 5-10 seconds

Decision framework:
  If dashboard is loaded 1M times/day and clicks happen 10M times/day:
    Pre-compute: 10M × 3 writes + 1M × 1 read = 31M operations
    On-demand: 10M × 1 write + 1M × (scan millions) = 10M writes + huge read cost
    
    Pre-compute WINS when reads are frequent and expensive.
    On-demand WINS when reads are rare and inputs change constantly.
```

---

## Pre-Computation: Compute at Write Time

### How It Works

```
Every incoming event updates pre-computed aggregates at multiple granularities:

Ad click event arrives at 14:35:42 UTC:

  HINCRBY campaign:123:2025-03-15:14:35 clicks 1    (minute bucket)
  HINCRBY campaign:123:2025-03-15:14 clicks 1        (hour bucket)
  HINCRBY campaign:123:2025-03-15 clicks 1            (day bucket)
  HINCRBY campaign:123:2025-03 clicks 1               (month bucket)

Dashboard query "today's clicks for campaign 123":
  HGET campaign:123:2025-03-15 clicks → 68,328 → returned in < 1ms

Dashboard query "clicks per hour today":
  HMGET campaign:123:2025-03-15:00 campaign:123:2025-03-15:01 ... :23 clicks
  → [2841, 1203, 892, ... 5821] → returned in < 2ms

No scanning. No aggregation. Just key lookups.
```

### Advantages

- **Instant reads**: < 1ms for pre-computed results — just a key lookup.
- **Predictable latency**: Read time doesn't depend on data volume. Whether you have 10K or 10B events, the dashboard loads in the same time.
- **Scalable reads**: Pre-computed results cached in Redis — millions of reads/sec.
- **SLA-friendly**: Dashboard SLA of < 500ms is trivially met.

### Disadvantages

- **Write amplification**: 1 event → 4+ writes (minute, hour, day, month buckets, possibly per dimension).
- **Storage cost**: Pre-computed aggregates for every dimension combination. If you have 10 campaigns × 50 ad groups × 5 metrics × 365 days = 912,500 pre-computed keys.
- **Rigidity**: Can only answer pre-defined questions. "Show me clicks by geo and device type" requires pre-computing that specific combination.
- **Correction complexity**: If a bug causes incorrect counting, you can't "re-aggregate" — you must rebuild from raw events.
- **Late events**: Events arriving after the aggregation window complicate the pre-computed totals.

### When Pre-Computation Is Essential

```
Pre-compute when ALL of these are true:
  1. Read SLA < 500ms (dashboard, feed, leaderboard)
  2. High read:write ratio for the computed result
  3. Dimensions are known upfront (fixed aggregation buckets)
  4. Data volume makes on-demand computation infeasible (billions of events)

Examples:
  ✅ Ad dashboard: 100K advertisers check stats 10x/day = 1M reads/day
     Alternative: Each read scans 10M events → $5,000/day in compute
     Pre-compute: 3 Redis writes per event → $150/day
     
  ✅ Social feed timeline: 500M users refresh feed 10x/day = 5B reads/day
     Alternative: Each read queries followers + sort + rank → 500ms
     Pre-compute: Fanout on write → feed ready in Redis → < 10ms read
     
  ✅ Leaderboard: Top 100 viewed 10M times/day
     Alternative: Each read sorts 100M scores → seconds
     Pre-compute: Redis sorted set, updated on score change → < 1ms read
```

---

## On-Demand: Compute at Read Time

### How It Works

```
Events stored as raw data — no pre-aggregation:

INSERT INTO clicks (campaign_id, ad_id, geo, device, timestamp, user_id, ...)
  VALUES (123, 456, 'US', 'mobile', '2025-03-15T14:35:42Z', 'user_789', ...)

Dashboard query (on-demand):
  SELECT campaign_id, COUNT(*) as clicks, SUM(cost) as spend
  FROM clicks
  WHERE campaign_id = 123 AND date = '2025-03-15'
  GROUP BY campaign_id
  → Scans all matching rows → aggregates → returns result
  → Duration: 3-10 seconds depending on data volume
```

### Advantages

- **No write amplification**: Raw events stored once. Simplest write path.
- **Flexible queries**: Any question can be answered from raw data. No need to pre-define dimensions.
- **Simple write logic**: Just store the event. No aggregation pipelines to maintain.
- **Self-correcting**: Fix a bug → re-query with corrected logic → instant correction.
- **Ad-hoc analysis**: "Show me clicks from iOS in Germany between 2-3 PM last Tuesday" — no pre-computation needed.

### Disadvantages

- **Slow reads**: Must scan and aggregate at query time. Latency depends on data volume.
- **Unpredictable latency**: 1M events → 500ms. 1B events → 30 seconds. Hard to guarantee SLA.
- **Resource-intensive**: Each query consumes CPU, memory, I/O. 1M concurrent dashboard loads would collapse the database.
- **Expensive at scale**: ClickHouse query cost scales with data volume. Pre-computed lookup is O(1).

### When On-Demand Is Fine

```
On-demand when ANY of these are true:
  1. Queries are rare (< 100/day for this specific computation)
  2. Data volume is small (< 10M rows — aggregation is fast enough)
  3. Queries are unpredictable (ad-hoc analytics, exploration)
  4. Latency tolerance is high (> 5 seconds acceptable)
  5. Correctness > speed (billing reports that must be exact)

Examples:
  ✅ Monthly executive reports (5 people view once/month)
  ✅ Ad-hoc analytics investigations (data scientist exploring patterns)
  ✅ Historical trend analysis ("What happened 6 months ago?")
  ✅ Nightly batch reconciliation (correctness over speed)
  ✅ Low-traffic admin dashboards (internal tools used by 10 people)
```

---

## The Tradeoff: Write Amplification vs Read Latency

### Quantified Comparison

```
Scenario: 10M clicks/day, 1M dashboard loads/day

┌──────────────────────────────────────────────────────────────┐
│                    PRE-COMPUTED                               │
├──────────────────────────────────────────────────────────────┤
│  Write cost: 10M events × 4 Redis writes = 40M ops/day      │
│  Write latency: 0.1ms per event (Redis HINCRBY)              │
│  Storage: 100K dimension keys × 100 bytes = 10 MB            │
│  Read cost: 1M loads × 1 HGET = 1M ops/day                  │
│  Read latency: < 1ms (key lookup)                            │
│  Daily compute cost: ~$150 (Redis operations)                 │
│  Read SLA: ✅ < 1ms (trivially met)                          │
├──────────────────────────────────────────────────────────────┤
│                    ON-DEMAND                                  │
├──────────────────────────────────────────────────────────────┤
│  Write cost: 10M events × 1 insert = 10M ops/day             │
│  Write latency: 1ms per event (Cassandra/ClickHouse insert)  │
│  Storage: 10M events × 200 bytes = 2 GB/day                  │
│  Read cost: 1M loads × scan 10M events = 10T read ops/day    │
│  Read latency: 3-10 seconds (full scan + aggregation)        │
│  Daily compute cost: ~$5,000 (ClickHouse compute)             │
│  Read SLA: ❌ 3-10 seconds (far exceeds 500ms SLA)           │
└──────────────────────────────────────────────────────────────┘

Pre-compute:  $150/day,  < 1ms reads  ✅
On-demand:    $5,000/day, 3-10s reads  ❌

Pre-computation is 33x cheaper AND 3,000x faster for this use case.
```

### The Decision Matrix

| Factor | Choose Pre-Compute | Choose On-Demand |
|---|---|---|
| Read frequency | > 1,000 reads/day for this result | < 100 reads/day |
| Read SLA | < 500ms required | > 5 seconds acceptable |
| Input data volume | > 10M events per aggregation window | < 1M events |
| Query dimensions | Known upfront, fixed set | Unpredictable, ad-hoc |
| Correctness requirement | Approximate OK for display | Exact required for billing |
| Write budget | Can afford 3-10x write amplification | Must minimize writes |
| Data freshness | Real-time or near-real-time | T+1 (next day) is fine |

---

## Pre-Compute the Common Case, On-Demand for the Long Tail

### The 80/20 Strategy

The most powerful pattern combines both approaches:

```
Pre-compute what 90% of users need (common case):
  ✅ Global top-100 leaderboard → Redis sorted set (ZADD on score update)
  ✅ Dashboard daily/hourly aggregates → Redis hash counters
  ✅ Feed timelines for active users → Redis sorted sets (fanout on write)
  ✅ Campaign-level click totals → Pre-computed per day/hour

Compute on-demand for the long tail (rare queries):
  ✅ "What's my exact rank out of 100M players?" → ZRANK (computed per request)
  ✅ "Show clicks filtered by custom date range + geo + device" → ClickHouse
  ✅ "Compare this week vs same week last year" → Spark/Athena
  ✅ Ad-hoc analyst queries → Raw events in S3 + Presto
```

### Twitter Feed: The Canonical Example

```
Pre-compute (common case — 99% of users):
  When @alice tweets:
    For each of Alice's followers (say 500):
      ZADD timeline:{follower_id} {tweet_timestamp} {tweet_id}
    
  When a follower opens Twitter:
    ZREVRANGE timeline:{user_id} 0 49  → top 50 tweets, pre-sorted → < 5ms
    
On-demand (long tail — 1% of users: celebrities):
  @BarackObama has 130M followers.
  Fanning out to 130M timelines per tweet = 130M Redis writes = impractical.
  
  Instead: Merge at read time.
    User follows Obama → timeline = pre-computed_timeline UNION obama_recent_tweets
    Extra 5-10ms for the merge, but avoids 130M write operations per Obama tweet.

Hybrid result:
  99% of users: Pre-computed feed → < 5ms read (fanout on write)
  1% of users with celebrity follows: Mixed feed → 10-15ms read (fanout on write + merge)
```

### Leaderboard: Pre-Computed + On-Demand

```
Pre-computed (viewed millions of times):
  Top 100 global leaderboard
  Updated on every score change: ZADD leaderboard {score} {user_id}
  Read: ZREVRANGE leaderboard 0 99 → < 1ms
  
On-demand (viewed thousands of times):
  Individual user rank: "You are ranked #47,832 out of 100M players"
  Computed per request: ZREVRANK leaderboard {user_id} → < 1ms (O(log N))
  Not pre-computed because there are 100M individual ranks to maintain.
  
On-demand (viewed rarely):
  "Show me the leaderboard for players in Europe who joined in 2024"
  Custom query → ClickHouse → 2-5 seconds
  Not worth pre-computing (too many dimension combinations, rarely queried)
```

---

## Materialized Views

### What Is a Materialized View?

A **materialized view** is a pre-computed query result stored as a read-optimized table. It's the database-level equivalent of pre-computation.

```
Source of truth (normalized):
  orders (order_id, user_id, product_id, quantity, price, status, created_at)
  products (product_id, name, category)
  users (user_id, name, region)

Materialized view (denormalized, optimized for dashboard):
  daily_sales_summary:
    date       | category    | region | total_orders | total_revenue
    2025-03-15 | Electronics | US     | 12,847       | $2,341,000
    2025-03-15 | Electronics | EU     | 8,421        | $1,567,000
    ...

Refresh strategies:
  1. Full refresh: DROP + rebuild every hour (simple, expensive)
  2. Incremental refresh: Only process new/changed rows since last refresh
  3. Real-time (CDC): Debezium streams changes → Flink updates view continuously
```

### Materialized View vs Application-Level Pre-Computation

| Aspect | Materialized View | Application Pre-Computation |
|---|---|---|
| **Implementation** | Database feature (PostgreSQL, ClickHouse) | Application code + Redis/cache |
| **Refresh trigger** | Scheduled (cron) or CDC-driven | Event-driven (on each write) |
| **Latency** | Minutes (scheduled) to seconds (CDC) | Real-time (sub-second) |
| **Flexibility** | SQL-based (any SQL query can be materialized) | Custom code (any logic) |
| **Operational complexity** | Low (database manages it) | High (application must maintain) |
| **Best for** | Analytics dashboards, reporting | User-facing real-time features |

---

## Lambda Architecture: Pre-Computed + On-Demand Together

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    LAMBDA ARCHITECTURE                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Raw Events ──→ Kafka ──┬──→ Speed Layer (Flink)             │
│                         │    Real-time approximate counts     │
│                         │    Pre-computed in Redis             │
│                         │    Latency: < 1 second              │
│                         │                                     │
│                         └──→ Batch Layer (Spark)              │
│                              Nightly exact recomputation      │
│                              Stored in ClickHouse             │
│                              Latency: T+1 (next day)          │
│                                                              │
│  Serving Layer:                                               │
│    Dashboard "today": Speed Layer (approximate, real-time)    │
│    Dashboard "yesterday+": Batch Layer (exact, reconciled)    │
│    Invoice: Batch Layer only (exact required)                 │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Why both?
  Speed Layer: Fast but approximate (sampling, late events missed)
  Batch Layer: Slow but exact (processes ALL events, no sampling)
  
  Together: Real-time freshness + batch accuracy
```

### Kappa Architecture (Simplified Alternative)

```
Use ONE stream processing layer for both:

Raw Events → Kafka → Flink → Pre-computed views (Redis/ClickHouse)

Same pipeline handles real-time AND reprocessing.
To correct historical data: Reset Flink offset → reprocess from Kafka.

Simpler than Lambda (one codebase, not two).
Requires Kafka to retain sufficient history (30+ days).
Used by LinkedIn, Uber, Confluent.
```

---

## Pre-Computation Patterns by Data Structure

### Redis-Based Pre-Computation

| Pattern | Data Structure | Write Operation | Read Operation | Use Case |
|---|---|---|---|---|
| **Counters** | Hash | `HINCRBY key field 1` | `HGET key field` | Click counts, view counts |
| **Leaderboard** | Sorted Set | `ZADD lb score user` | `ZREVRANGE lb 0 99` | Top-K rankings |
| **Timeline** | Sorted Set | `ZADD tl ts tweet` | `ZREVRANGE tl 0 49` | Social feed |
| **Unique count** | HyperLogLog | `PFADD visitors uid` | `PFCOUNT visitors` | Unique visitors |
| **Recent items** | List (capped) | `LPUSH recent item` | `LRANGE recent 0 19` | Recent activity |
| **Set membership** | Set | `SADD online uid` | `SISMEMBER online uid` | Presence check |

### Flink/Kafka Streams Pre-Computation

```
Tumbling window aggregation:

clickStream
  .keyBy(event -> event.getCampaignId())
  .window(TumblingEventTimeWindows.of(Time.minutes(1)))
  .aggregate(new ClickAggregator())
  .addSink(redisSink);

Produces:
  campaign:123:2025-03-15:14:35 → {clicks: 847, impressions: 12000, spend: 423.50}
  campaign:123:2025-03-15:14:36 → {clicks: 921, impressions: 13200, spend: 460.50}
  
  Each minute bucket pre-computed and stored in Redis.
  Dashboard reads minute buckets → instant.
```

---

## Handling Late Events and Corrections

### The Late Event Problem

```
Event with timestamp 14:35:42 arrives at 14:40:00 (5 minutes late).

Problem: The 14:35 minute bucket was already "closed" and written to Redis.
  The pre-computed count for 14:35 is missing this event.

Solutions:

1. Allowed Lateness (Flink watermarks):
   Window stays "open" for N minutes after nominal close.
   Late events within the allowed lateness → update the bucket.
   Events beyond allowed lateness → dropped or sent to side output.
   
   window.allowedLateness(Time.minutes(5))
   
2. Update-in-place:
   Late event arrives → HINCRBY the closed bucket anyway.
   Dashboard shows slightly delayed correction.
   Acceptable for most real-time dashboards.

3. Nightly reconciliation:
   Batch job reads ALL raw events (including late ones).
   Recomputes exact totals.
   Overwrites pre-computed values.
   Stream is approximate; batch is authoritative.
```

### Correction Strategies

```
Bug discovered in pre-computation logic:

Strategy 1: Reprocess from Kafka
  Kafka retains 30 days of events.
  Reset consumer offset to the affected time range.
  Recompute affected buckets.
  Overwrite in Redis.
  Duration: Minutes to hours depending on data volume.

Strategy 2: Batch recomputation
  Spark reads raw events from S3 (all-time history).
  Recomputes all affected aggregates.
  Writes corrected values to serving layer.
  Duration: Hours.

Strategy 3: Invalidate + lazy recompute
  Delete affected pre-computed keys.
  Next read triggers on-demand computation.
  Cache the result for future reads.
  Duration: Instant (invalidation), gradual (recomputation as needed).
```

---

## Real-World System Examples

### Google Analytics — Pre-Computed Dashboard

```
Page views arrive as events:
  {page: "/blog/post-1", visitor: "abc", timestamp: "2025-03-15T14:35:42Z"}

Pre-computed per (page, date, hour):
  page_views:/blog/post-1:2025-03-15:14 → 2,847
  unique_visitors:/blog/post-1:2025-03-15 → HyperLogLog (12 KB)
  bounce_rate:/blog/post-1:2025-03-15 → 0.42

Dashboard loads:
  Fetch pre-computed values → < 100ms
  
Custom date range query:
  "Show me page views from March 1-15 for /blog/post-1"
  → Sum 15 daily pre-computed values → instant
  
Custom dimension query:
  "Show me page views by country for /blog/post-1"
  → Pre-computed if common dimension, on-demand if rare
```

### Uber — Pre-Computed ETA

```
ETA for ride request:
  On-demand: Calculate route + traffic + driver speed → 200-500ms
  Pre-computed: Pre-calculate ETAs for common origin-destination pairs
  
  Pre-compute top 10,000 routes (airport → city center, etc.)
  Cache ETA with 5-minute TTL
  95% of requests hit pre-computed cache → < 10ms
  5% of requests: Compute on-demand → 200ms
```

### Netflix — Pre-Computed Recommendations

```
Recommendation generation is EXPENSIVE:
  Matrix factorization over billions of ratings → hours of computation
  Can't do this in real-time for each page load.

Pre-computed:
  Nightly batch: Spark computes top-100 recommendations per user
  Stored in Cassandra: user_id → [movie_1, movie_2, ..., movie_100]
  Page load: Read from Cassandra → < 5ms
  
On-demand refinement:
  User's recent activity (last 5 minutes) → real-time re-ranking
  Take pre-computed list → re-order based on recent signals → < 50ms
  
Hybrid: Batch pre-computation + real-time re-ranking
```

---

## Deep Dive: Applying Pre-Computation to Popular Problems

### Ad Click Dashboard

```
Pre-computed (viewed 1M times/day):
  Per campaign per day: clicks, impressions, spend, CTR, CPC
  Per campaign per hour: clicks, impressions (for hourly chart)
  Per ad group per day: clicks, impressions (drill-down)
  
  Write path: Each click → 4 HINCRBY operations in Redis
  Read path: Dashboard loads → 5-10 HGET operations → < 5ms
  
On-demand (viewed 100 times/day):
  "Show clicks from iOS in Germany between 2-3 PM last Tuesday"
  → ClickHouse query on raw events → 2-5 seconds
  Not worth pre-computing (too many dimension combinations)
```

### Search Results

```
Pre-computed (at index time):
  Inverted index: "pizza" → [doc_1, doc_5, doc_12, ...]
  Document scores: BM25 computed at index time
  
On-demand (at query time):
  Query parsing, boolean logic, ranking combination
  Personalization (user preferences, location)
  Spell correction, synonym expansion
  
Hybrid: Index is pre-computed, ranking is on-demand.
```

### Social Media Feed

```
Pre-computed for regular users (99%):
  Fanout on write → timeline pre-built in Redis sorted set
  Read: ZREVRANGE → < 5ms
  Write: Each tweet → N Redis writes (N = follower count)

On-demand for celebrity followers (1%):
  Merge celebrity tweets into timeline at read time
  Read: Pre-computed timeline + celebrity merge → 10-15ms

On-demand for search/explore:
  "Trending tweets" computed every 5 minutes
  "Search: #topic" → Elasticsearch query → 50-200ms
```

---

## Interview Talking Points & Scripts

### Pre-Compute for SLA

> *"I'd pre-compute the dashboard aggregates at write time. Each incoming ad event atomically increments counters for minute, hour, and day buckets in Redis. When the advertiser opens their dashboard, the query is just a HGET — < 1ms. Without pre-computation, we'd scan 10 million raw events for each dashboard load, taking 5-10 seconds. The tradeoff: 4 Redis writes per event (write amplification), but at our dashboard SLA of < 500ms and 1M daily loads, pre-computation is non-negotiable. It's also 33x cheaper than on-demand computation."*

### The 80/20 Strategy

> *"I'd pre-compute what 90% of users need, and compute on-demand for the long tail. The global top-100 leaderboard is pre-computed in a Redis sorted set — ZADD on every score update, ZREVRANGE for the top 100. But 'what's my exact rank out of 100 million players?' is computed on-demand via ZRANK — it's still O(log N) in Redis, sub-millisecond, but only called when a specific user asks. Custom leaderboard filters go to ClickHouse. Each tier matches the access pattern to the right cost model."*

### Feed Pre-Computation

> *"For the social feed, I'd pre-compute timelines via fanout on write. When a user tweets, their tweet is pushed to each follower's timeline in Redis. The tradeoff: a user with 500 followers causes 500 Redis writes per tweet. But each of those 500 followers opens their feed 10+ times per day — that's 5,000 reads served in < 5ms each. The write amplification pays for itself many times over. For celebrities with 10M+ followers, I'd switch to merge-at-read to avoid the write storm."*

### Late Events

> *"Pre-computed aggregates have a late event problem. An event timestamped 14:35 that arrives at 14:40 after the 14:35 window closed would be missed. I'd handle this with Flink's allowed lateness — the window stays open for 5 minutes after nominal close. Events beyond the 5-minute window are processed in the nightly batch reconciliation, which recomputes exact totals from raw events in S3."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Pre-computing everything
**Why it's wrong**: Wasteful for rarely-accessed data. Pre-computing 10M dimension combinations when 99% are never queried wastes storage and write bandwidth.
**Better**: Pre-compute the top 10% most-accessed dimensions. On-demand for the long tail.

### ❌ Mistake 2: Not pre-computing dashboard metrics
**Why it's wrong**: Users won't wait 10 seconds for a dashboard to load. On-demand computation at scale is expensive and slow.
**Better**: Pre-compute known dashboard dimensions. Dashboard loads → key lookups → < 1ms.

### ❌ Mistake 3: Not mentioning write amplification as the cost
**Why it's wrong**: Pre-computation isn't free. 1 event → 4+ writes. At 10M events/day, that's 40M+ extra write operations.
**Better**: Quantify the write amplification and explain why the read savings justify it.

### ❌ Mistake 4: Not distinguishing between common case and long tail
**Why it's wrong**: A single strategy doesn't fit all query patterns. Pre-compute for the homepage leaderboard; on-demand for custom filtered views.
**Better**: "Pre-compute the top-100 (viewed millions of times), on-demand for individual rank (viewed thousands of times)."

### ❌ Mistake 5: Forgetting about late events
**Why it's wrong**: In real-world systems, events arrive late (network delays, retries, timezone issues). Pre-computed aggregates become inaccurate.
**Better**: Mention allowed lateness, batch reconciliation, or update-in-place for late events.

### ❌ Mistake 6: Not mentioning reconciliation for pre-computed data
**Why it's wrong**: Pre-computed aggregates can drift from raw data (bugs, sampling, late events). Without reconciliation, you'd never know.
**Better**: "Nightly batch job recomputes exact aggregates from raw events in S3 and corrects any drift in the pre-computed values."

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
│    Data volume makes on-demand infeasible (billions of rows) │
│                                                              │
│  ON-DEMAND WHEN:                                             │
│    Queries are unpredictable (ad-hoc analytics)              │
│    Data volume is small (< 10M rows, fast enough)            │
│    Rarely accessed (monthly reports, < 100 reads/day)        │
│    Exact correctness required (billing, invoicing)           │
│                                                              │
│  HYBRID (best of both):                                      │
│    Pre-compute 90% (common) + on-demand 10% (long tail)      │
│    Speed layer (Flink) + batch layer (Spark)                 │
│    Stream for freshness, batch for accuracy                  │
│                                                              │
│  COST:                                                       │
│    Pre-computation = write amplification (3-10x writes)      │
│    On-demand = read amplification (scan millions per query)  │
│    Pre-compute: $150/day vs on-demand: $5,000/day (example)  │
│                                                              │
│  PATTERNS:                                                   │
│    Counters: Redis HINCRBY (minute/hour/day buckets)         │
│    Leaderboard: Redis ZADD (sorted set)                      │
│    Feed: Redis ZADD (fanout on write, merge for celebrities) │
│    Analytics: Flink tumbling windows → Redis/ClickHouse      │
│    Recommendations: Nightly Spark batch → Cassandra           │
│                                                              │
│  RECONCILIATION:                                             │
│    Nightly batch recomputes exact aggregates from raw events  │
│    Corrects drift from late events, sampling, bugs           │
│    Stream = approximate real-time, batch = authoritative      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 5: Caching Strategies** — Pre-computed results often live in cache
- **Topic 7: Push vs Pull (Fanout)** — Fanout on write is pre-computation of feeds
- **Topic 23: Batch vs Stream Processing** — Lambda/Kappa architectures
- **Topic 33: Reconciliation** — Ensuring pre-computed values match reality
- **Topic 35: Cost vs Performance** — Economic analysis of pre-computation

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
