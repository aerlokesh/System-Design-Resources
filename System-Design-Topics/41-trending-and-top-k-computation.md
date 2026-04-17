# 🎯 Topic 41: Trending & Top-K Computation

> **System Design Interview — Deep Dive**
> A comprehensive guide covering trending hashtags, top-K computation at scale, Count-Min Sketch for approximate frequency, min-heap for top-K maintenance, sliding windows for recency weighting, distributed aggregation, decay functions, and how to articulate trending system decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [What Makes Something "Trending"](#what-makes-something-trending)
3. [Exact Top-K — The Simple Approach](#exact-top-k--the-simple-approach)
4. [Approximate Top-K — Count-Min Sketch + Min-Heap](#approximate-top-k--count-min-sketch--min-heap)
5. [Sliding Window Approaches](#sliding-window-approaches)
6. [Decay Functions — Recency Weighting](#decay-functions--recency-weighting)
7. [Distributed Top-K Architecture](#distributed-top-k-architecture)
8. [The Lambda Pattern — Stream + Batch](#the-lambda-pattern--stream--batch)
9. [Hot Key Problem in Trending Systems](#hot-key-problem-in-trending-systems)
10. [Real-World System Examples](#real-world-system-examples)
11. [Deep Dive: Applying Trending to Popular Problems](#deep-dive-applying-trending-to-popular-problems)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Trending computation** answers: "What topics/hashtags/products are suddenly popular RIGHT NOW?" It's not about absolute popularity (top-K all time) — it's about **velocity of growth** relative to a baseline.

```
Top-K (absolute): What has the most total count?
  #love: 50M tweets all time → Top-1 (but not "trending" — always high)

Trending: What has the biggest spike RIGHT NOW relative to its normal rate?
  #WorldCup: Usually 10K tweets/hour → suddenly 500K tweets/hour (50x spike!)
  → TRENDING! Even though #love has more total tweets.

The core formula:
  Trending score = current_rate / baseline_rate
  
  #WorldCup:  500K/hour ÷ 10K/hour  = 50x → TRENDING 🔥
  #love:      51M/hour ÷ 50M/hour    = 1.02x → Not trending (normal rate)
  #NewAlbum:  100K/hour ÷ 5K/hour    = 20x → Trending
```

---

## What Makes Something "Trending"

### Trending vs Popular

```
POPULAR (Top-K by absolute count):
  "What are the most-used hashtags of all time?"
  Answer: #love, #instagood, #photooftheday (always the same boring list)
  Changes: Rarely. Same items dominate forever.

TRENDING (Top-K by velocity/acceleration):
  "What hashtags are spiking right now?"
  Answer: #WorldCup, #BreakingNews, #NewiPhoneLaunch
  Changes: Every few minutes. Reflects current events.

TRENDING requires:
  1. Current rate: How fast is the count growing RIGHT NOW?
  2. Baseline rate: What's "normal" for this item?
  3. Comparison: Current rate vs baseline → trending score
  4. Recency: Recent activity matters more than old activity
```

### Trending Score Formulas

```
Formula 1: Simple ratio
  trending_score = count_last_hour / avg_count_per_hour_last_30_days
  Pros: Simple, intuitive
  Cons: New topics with no baseline get infinite score

Formula 2: Z-score (statistical)
  trending_score = (current_count - mean) / std_deviation
  "How many standard deviations above the mean?"
  Pros: Statistically sound, handles variance
  Cons: Assumes normal distribution (not always true)

Formula 3: Exponential decay (Twitter-style)
  trending_score = Σ (event_weight × decay^(now - event_time))
  Recent events weighted more heavily than old ones
  Score naturally decays over time → topics "untend" automatically
  Pros: Smooth, no fixed windows, captures recency
  Cons: More complex to compute

Formula 4: Log-scaled with time penalty
  trending_score = log(1 + count_last_hour) / (hours_since_first_event + 2)²
  Similar to Hacker News/Reddit ranking
  Pros: Prevents runaway scores, incorporates time decay
  Cons: Tuning constants is art, not science
```

---

## Exact Top-K — The Simple Approach

### HashMap + Sorting

```
Simplest implementation:

1. Maintain a HashMap: {hashtag → count}
   On each event: hashtag_counts[tag] += 1

2. Every N seconds: Sort by count → take top K

Problem: Doesn't scale.
  10 billion unique hashtags × 100 bytes = 1 TB of memory!
  Sorting 10 billion entries every 5 seconds → impossible.

Where it works:
  Small cardinality: Top-10 products out of 50,000 products → fits in memory
  Low volume: < 10K events/sec → simple HashMap works fine
  
  If cardinality > 1M or volume > 100K events/sec → need approximate approach.
```

### Redis Sorted Set (Moderate Scale)

```
ZINCRBY trending:hashtags 1 "#WorldCup"
ZINCRBY trending:hashtags 1 "#BreakingNews"
ZREVRANGE trending:hashtags 0 9 WITHSCORES  → Top 10

Advantages:
  O(log N) increment, O(log N + K) for top-K query
  Handles millions of unique items
  Atomic operations (no race conditions)
  
Limitations:
  Memory: Each item = ~100 bytes → 10M items = 1 GB
  Single Redis instance: ~100K ZINCRBY/sec
  No built-in decay (score only goes up, never decreases)
  
  Fix for decay: Use multiple sorted sets for time windows
    trending:hashtags:14:30  (14:30 - 14:35 window)
    trending:hashtags:14:35  (14:35 - 14:40 window)
    
    ZUNIONSTORE trending:merged 12 trending:hashtags:14:* WEIGHTS 1 0.9 0.8 ...
    → Merge last 12 windows with decay weights
    ZREVRANGE trending:merged 0 9 → Top 10 with recency weighting
```

---

## Approximate Top-K — Count-Min Sketch + Min-Heap

### Count-Min Sketch (CMS)

```
A probabilistic data structure that estimates frequency of items:

Structure:
  d hash functions, each mapping to a row of w counters
  
  ┌─────────────────────────────┐
  │ h1: [0][3][0][1][5][0][2]  │  ← Row 1 (w counters)
  │ h2: [1][0][4][0][0][3][0]  │  ← Row 2
  │ h3: [0][2][0][0][3][1][0]  │  ← Row 3
  │ h4: [2][0][1][3][0][0][4]  │  ← Row 4 (d rows total)
  └─────────────────────────────┘

Add event "#WorldCup":
  Compute h1("#WorldCup") = 4, h2("#WorldCup") = 2, h3("#WorldCup") = 5, h4("#WorldCup") = 1
  Increment: row1[4]++, row2[2]++, row3[5]++, row4[1]++

Query count for "#WorldCup":
  Look up: row1[4], row2[2], row3[5], row4[1]
  Return: MIN(row1[4], row2[2], row3[5], row4[1])
  → Minimum across all rows (minimizes over-counting from collisions)

Properties:
  ✅ Never undercounts (always ≥ true count)
  ✅ May overcount by a small amount (hash collisions)
  ✅ Fixed memory: d × w counters (independent of number of unique items!)
  ✅ O(d) per update and query (constant time)

Memory: 
  d = 5 rows, w = 10,000 columns, 4 bytes per counter
  = 5 × 10,000 × 4 = 200 KB for ANY number of unique items!
  Error rate: ~0.01% with these parameters.
  
  vs HashMap: 10 billion items × 100 bytes = 1 TB
  CMS: 200 KB. Same job. Approximate but bounded error.
```

### Min-Heap for Top-K

```
Maintain a min-heap of size K (the current top-K items):

  For each event:
    1. Estimate count using Count-Min Sketch
    2. If count > heap minimum (smallest item in top-K):
       Remove heap minimum
       Insert this item with its count
    3. If count ≤ heap minimum: Skip (not in top-K)

  Min-heap of size K = 100:
    Maintains the 100 items with highest estimated counts
    Heap minimum = the threshold to enter top-K
    
    Insert/remove: O(log K) = O(log 100) = O(7)
    Total per event: O(d) for CMS + O(log K) for heap = O(constant)

Complete algorithm:
  CMS (200 KB) + Min-Heap of 100 (< 10 KB) = ~210 KB total
  Handles billions of events with bounded memory.
  
  This is the interview answer for "How would you compute top-K trending hashtags?"
```

### Heavy Hitters Algorithm (Space-Saving)

```
Alternative to CMS + Min-Heap:

Space-Saving algorithm:
  Maintain exactly K counters (one per tracked item).
  
  On event for item X:
    If X is already tracked → increment its counter
    If X is not tracked → replace the item with LOWEST counter
      Set new item's counter = lowest_counter + 1
      (Over-estimates, but guarantees heavy hitters are found)

  Memory: Exactly K counters × (key + count) = K × ~200 bytes
  K = 1000 → 200 KB
  
  Advantage: Guaranteed to find all items with frequency > N/K
    (N = total events, K = number of counters)
  
  Disadvantage: Counter values are over-estimates for non-heavy-hitters

Both CMS+MinHeap and Space-Saving are valid interview answers.
```

---

## Sliding Window Approaches

### Fixed Tumbling Windows

```
Divide time into fixed 5-minute windows:

  Window 14:00-14:05: {#WorldCup: 50K, #Music: 30K, #News: 20K}
  Window 14:05-14:10: {#WorldCup: 45K, #Music: 35K, #News: 25K}
  Window 14:10-14:15: {#WorldCup: 60K, #Music: 28K, #News: 22K}

Top-K for "last 15 minutes":
  Merge last 3 windows → {#WorldCup: 155K, #Music: 93K, #News: 67K}
  Top-3: #WorldCup, #Music, #News

Implementation:
  Redis sorted set per window:
    ZINCRBY trending:14:00 1 "#WorldCup"
    ZINCRBY trending:14:05 1 "#WorldCup"
    
  Merge for top-K:
    ZUNIONSTORE trending:merged 3 trending:14:00 trending:14:05 trending:14:10
    ZREVRANGE trending:merged 0 9

Pros: Simple, easy to implement, easy to expire old windows
Cons: Boundary problem (event at 14:04:59 and 14:05:01 in different windows)
```

### Sliding Window with Buckets

```
Smoother: Use 1-minute buckets, aggregate last 15 buckets:

  Bucket 14:00: counts
  Bucket 14:01: counts
  ...
  Bucket 14:14: counts
  
  Every minute: Drop oldest bucket, add new bucket
  Top-K: Merge last 15 buckets with optional decay weights

  Decay weights: [1.0, 0.95, 0.90, 0.85, ..., 0.30]
  Most recent minute gets full weight, oldest gets 30% weight.
  
  Result: Smoother trending that favors recency.
```

### Exponential Moving Average (EMA)

```
No explicit windows — continuous decay:

  For each hashtag:
    ema_count = α × current_count + (1 - α) × previous_ema_count
    
    α = 0.1: Slow decay (long memory, stable trends)
    α = 0.5: Fast decay (short memory, captures sudden spikes)
  
  Updated every second (or every N events):
    ema["#WorldCup"] = 0.3 × count_this_second + 0.7 × ema["#WorldCup"]
  
  Top-K: Sort by EMA value

Pros: 
  No window boundaries. Smooth. Self-decaying.
  Items that stop getting events naturally drop in score.
Cons:
  Must maintain state per item (EMA value)
  Harder to reason about than fixed windows
```

---

## Decay Functions — Recency Weighting

### Why Decay Matters

```
Without decay:
  #WorldCup got 5M tweets during the final yesterday.
  Today: 500 tweets/hour.
  Without decay: #WorldCup is still "trending" (highest total count).
  With decay: #WorldCup's old tweets lose weight → drops off trending.

Decay makes trending RESPONSIVE to current events.
```

### Time-Based Decay

```
Score = Σ (1.0 × e^(-λ × age_in_hours))

Each event contributes less as it ages:
  Age 0 hours: weight = 1.0 (full contribution)
  Age 1 hour: weight = 0.5 (half contribution)
  Age 2 hours: weight = 0.25
  Age 6 hours: weight = 0.016 (nearly zero)

Half-life λ tuning:
  λ = 0.693 / half_life_hours
  
  Half-life = 1 hour: Very responsive (only recent events matter)
  Half-life = 6 hours: Moderate (trending for a few hours)
  Half-life = 24 hours: Slow (trending for a day)
  
  Twitter-style: 1-2 hour half-life (fast-moving trends)
  Reddit-style: 12-24 hour half-life (slower, daily cycle)
```

---

## Distributed Top-K Architecture

### Two-Level Aggregation

```
Problem: 1 billion events/hour across 100 Kafka partitions.
  Can't send ALL events to one node for counting.

Solution: Two-level aggregation (Map-Reduce style):

Level 1: Local Top-K per partition (parallel)
  Each of 100 Flink workers maintains local CMS + min-heap
  Every 1 minute: Emit local top-1000 to aggregation topic
  
Level 2: Global Top-K (single aggregator)
  One aggregator receives 100 × 1000 = 100,000 candidates
  Merges into global top-K
  Emits final top-10 trending to Redis for serving

Architecture:
  Kafka (100 partitions)
    → Flink workers (100 instances, each: local CMS + min-heap)
      → Kafka "local-top-k" topic (100K candidates/minute)
        → Flink aggregator (1 instance: global merge)
          → Redis sorted set (final top-K for API serving)
          → Cache: Top-10 trending refreshed every 1-5 minutes

Why two levels?
  Level 1: Embarrassingly parallel. Each worker handles 10M events/hour.
  Level 2: Small input (100K candidates, not 1B events). One machine handles it.
  
  1B events reduced to 100K candidates → 10,000x reduction before global aggregation.
```

### Scatter-Gather Pattern

```
Alternative for serving layer:

  API request: "Get trending hashtags"
  
  Gateway → scatter to 10 aggregator shards → each returns local top-10
  Gateway → merge 10 × 10 = 100 candidates → global top-10

  Each shard: Handles 1/10 of the hashtag space
  Gateway: Merge is cheap (100 items)
  
  Latency: Max latency across 10 shards + merge time ≈ 5-10ms

This is the pattern Twitter uses for trending topics.
```

---

## The Lambda Pattern — Stream + Batch

### Why Both Stream and Batch?

```
Stream (real-time, approximate):
  Flink processes events as they arrive
  CMS + min-heap → approximate top-K
  Updated every 30 seconds
  Accuracy: ~95% (good enough for display)
  
Batch (nightly, exact):
  Spark reads ALL events from S3
  Exact counts → exact top-K
  Updated once per day at 3 AM
  Accuracy: 100% (authoritative)

Reconciliation:
  Stream shows "trending now" (real-time, approximate)
  Batch corrects "trending yesterday" (exact, for analytics/reporting)
  
  For trending display: Stream is sufficient (approximate is fine)
  For ad billing based on trending placement: Use batch (exact counts)
```

---

## Hot Key Problem in Trending Systems

### The Viral Hashtag Problem

```
#WorldCupFinal: 2M tweets/minute during the match
  All 2M events have key = "#WorldCupFinal"
  If using Redis ZINCRBY: 2M writes/minute to one key → HOT KEY!
  Single Redis instance: ~100K ops/sec → needs 20x capacity for one key

Solutions:

1. Write buffering + batching:
   Buffer increments locally (in-memory counter)
   Flush to Redis every 1 second: ZINCRBY trending 2000 "#WorldCupFinal"
   Instead of 2M individual increments → 60 batched increments/minute
   
2. Key sharding (see Topic 31):
   ZINCRBY trending:0 1 "#WorldCupFinal"
   ZINCRBY trending:1 1 "#WorldCupFinal"
   ...
   ZINCRBY trending:9 1 "#WorldCupFinal"
   → 10 shards → 200K writes/shard → within limits
   
   Top-K query: ZUNIONSTORE across 10 shards, then ZREVRANGE

3. Local aggregation in Flink:
   Each Flink worker: Local counter for #WorldCupFinal
   Every 5 seconds: Flush delta to global aggregator
   200 workers × 1 flush/5s = 40 flushes/sec to global → easy
```

---

## Real-World System Examples

### Twitter — Trending Topics

```
Architecture:
  Tweet events → Kafka → Storm/Heron (streaming framework)
  → Local top-K per region per 5-minute window
  → Global aggregator → Redis → API
  
  Trending is PER-REGION:
    US trending ≠ Japan trending ≠ UK trending
    Each region has its own aggregation pipeline
    
  Filtering:
    Remove spam/bot activity
    Remove previously-trending (suppress re-trending)
    Remove inappropriate content
    
  Refresh: Every 5 minutes. Display up to 30 trending topics.
  Scale: ~500K tweets/minute processed for trending computation.
```

### YouTube — Trending Videos

```
Not purely algorithmic — combines:
  View velocity (views/hour)
  Geographic diversity (trending in multiple countries)
  Source diversity (not all views from one referral)
  Freshness (recently uploaded)
  
  Anti-gaming: Filters artificial view inflation
  Human curation: Some manual review of trending list
  
  Updated: Every 15 minutes per country
```

### Amazon — Trending Products ("Movers & Shakers")

```
"Products with the biggest rank improvement in the last 24 hours"

  Yesterday rank: #50,000
  Today rank: #500
  Improvement: 99x → TRENDING!

  Computed by: Batch Spark job comparing today's sales rank with yesterday's
  Updated: Every hour (batch, not real-time)
  
  Simpler than hashtag trending: Fixed product catalog (~500M items)
  No approximation needed: Can maintain exact counts for all products.
```

---

## Deep Dive: Applying Trending to Popular Problems

### Trending Hashtags (Twitter)

```
Requirements:
  500K tweets/minute, each with 0-5 hashtags
  Top 10 trending per country, updated every 5 minutes
  Trending = high velocity relative to baseline

Architecture:
  Kafka "tweets" topic (500 partitions)
    → Flink (500 workers)
      Map: Extract hashtags, emit (country, hashtag, 1)
      KeyBy: (country, hashtag)
      Window: 5-minute tumbling, event time
      Aggregate: Count per (country, hashtag)
    → Flink aggregator
      KeyBy: country
      Compare with 7-day baseline (stored in Redis)
      Compute trending score: current_count / baseline_avg
      Top-10 per country
    → Redis: ZADD trending:{country} {score} {hashtag}

API:
  GET /v1/trending?country=US
  → ZREVRANGE trending:US 0 9 WITHSCORES
  → ["#WorldCup (50x)", "#NewAlbum (20x)", "#BreakingNews (15x)", ...]

Latency: < 5 minutes from tweet to trending display
```

### Trending Searches (Google-style)

```
"What are people searching for right now?"

  Query events → Kafka → Flink
  → CMS per 5-minute window → approximate counts per query
  → Compare with 30-day baseline per query
  → Top-K by velocity (trending score)
  
  Privacy: Aggregate only. Individual queries not stored.
  Filtering: Remove queries with < N unique users (prevent single-user manipulation)
  
  Serving: Pre-computed top-20 trending searches per country → Redis → CDN
  Refresh: Every 15 minutes
```

### Top-K Products by Sales (E-Commerce)

```
"Best sellers in Electronics, updated hourly"

  Order events → Kafka → Flink
    KeyBy: category
    Window: 1-hour tumbling
    Aggregate: Count per (category, product_id)
  → Redis sorted set per category
  
  ZADD bestsellers:electronics {count} {product_id}
  ZREVRANGE bestsellers:electronics 0 99 → Top 100 electronics

  Simpler than trending: No baseline comparison needed.
  Just absolute count within the window.
  
  But for "Movers & Shakers": Compare with yesterday's rank → trending formula.
```

---

## Interview Talking Points & Scripts

### The Architecture

> *"For trending hashtags, I'd use a two-level aggregation pipeline. Kafka ingests tweet events into 500 partitions. 500 Flink workers each maintain a local Count-Min Sketch and min-heap — the CMS uses only 200 KB of memory regardless of how many unique hashtags exist. Every minute, each worker emits its local top-1,000 to a global aggregator. The aggregator merges 500K candidates into the global top-10 per country. Results are written to Redis and served to the API. Total memory for the approximate counting: ~100 MB across the entire cluster, handling billions of events."*

### Why Approximate

> *"We don't need exact counts for trending. The purpose is to surface what's spiking — whether a hashtag has 502,000 or 504,000 tweets doesn't change whether it's trending. Count-Min Sketch gives us bounded error (±0.01%) in constant memory (200 KB per worker). The alternative — exact HashMap — would need terabytes to track billions of unique hashtags. The approximation is a deliberate tradeoff: 99.99% accuracy for 10,000x memory savings."*

### Trending vs Popular

> *"Trending is about velocity, not absolute count. #love has 50 million tweets but it's not trending — that's its normal rate. #WorldCup normally gets 10K tweets/hour but during the final it spikes to 500K/hour — that's a 50x velocity increase, which makes it trending. I'd compute the trending score as current_rate / baseline_rate, where baseline is the 7-day average for that hashtag."*

### Decay and Freshness

> *"I'd use exponential time decay with a 2-hour half-life. Each event's contribution to the trending score halves every 2 hours. This means a topic that was trending 4 hours ago has only 25% of its original score — it naturally drops off the trending list without any explicit removal logic. For the real-time display, scores are recomputed every 5 minutes in Flink."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using exact HashMap for billions of unique items
**Why it's wrong**: 10B unique hashtags × 100 bytes = 1 TB of memory. Doesn't fit.
**Better**: Count-Min Sketch (200 KB) + Min-Heap (10 KB). Same result, bounded error.

### ❌ Mistake 2: Confusing "trending" with "popular"
**Why it's wrong**: Saying "sort by total count" gives a static list (#love is always #1).
**Better**: Trending = velocity. Compare current rate with baseline rate.

### ❌ Mistake 3: No time decay
**Why it's wrong**: Without decay, yesterday's trending topic stays trending forever.
**Better**: Exponential decay (2-hour half-life) or windowed approach (last 1-hour count).

### ❌ Mistake 4: Single-node aggregation for all events
**Why it's wrong**: 1B events/hour → one node can't process them all.
**Better**: Two-level aggregation. Local top-K per worker → global merge of candidates.

### ❌ Mistake 5: Not mentioning spam/bot filtering
**Why it's wrong**: Without filtering, bots can make anything "trending" artificially.
**Better**: "I'd filter by requiring N unique users per item before it qualifies for trending, and apply bot detection before counting."

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          TRENDING & TOP-K COMPUTATION CHEAT SHEET             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  TRENDING ≠ POPULAR:                                         │
│    Popular: Highest absolute count (#love = 50M)             │
│    Trending: Highest velocity spike (#WorldCup = 50x normal) │
│    Score = current_rate / baseline_rate                       │
│                                                              │
│  APPROXIMATE COUNTING (for billions of unique items):        │
│    Count-Min Sketch: 200 KB, O(1) update/query, ±0.01%      │
│    Min-Heap (size K): Maintains current top-K items          │
│    CMS + Min-Heap = O(210 KB) for top-K of billions of items │
│                                                              │
│  EXACT COUNTING (for moderate cardinality):                  │
│    Redis sorted set: ZINCRBY + ZREVRANGE                     │
│    Works for < 10M unique items per window                   │
│                                                              │
│  TIME DECAY:                                                 │
│    Exponential: weight = e^(-λ × age). Half-life 1-2 hours.  │
│    Windowed: 5-min tumbling windows with decay weights       │
│    EMA: α × current + (1-α) × previous                      │
│                                                              │
│  DISTRIBUTED ARCHITECTURE:                                   │
│    Level 1: Local CMS + min-heap per Flink worker (parallel) │
│    Level 2: Global merge of top-K candidates (single node)   │
│    1B events → 100K candidates → global top-10               │
│    Result: Redis sorted set → API → Display                  │
│                                                              │
│  LAMBDA PATTERN:                                             │
│    Stream: Real-time approximate trending (Flink, 30s delay) │
│    Batch: Nightly exact counts for reconciliation (Spark)    │
│                                                              │
│  HOT KEY MITIGATION:                                         │
│    Write batching (buffer 1s → batch ZINCRBY)                │
│    Key sharding (#WorldCup → 10 sharded keys)               │
│    Local aggregation in Flink before global merge            │
│                                                              │
│  ANTI-GAMING:                                                │
│    Min unique users per item (prevents single-user spam)     │
│    Bot detection before counting                             │
│    Geographic diversity requirement                          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 29: Pre-Computation vs On-Demand** — Pre-computed trending lists
- **Topic 31: Hot Partitions & Hot Keys** — Viral hashtag hot key problem
- **Topic 38: Bloom Filters & Probabilistic DS** — Count-Min Sketch details
- **Topic 39: Kafka Deep Dive** — Kafka as event source for trending
- **Topic 40: Redis Deep Dive** — Redis sorted sets for top-K serving
- **Topic 48: Flink Deep Dive** — Flink windowing for trending computation

---

*This document is part of the System Design Interview Deep Dive series.*
