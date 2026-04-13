# 🎯 Topic 31: Hot Partitions & Hot Keys

> **System Design Interview — Deep Dive**
> Handling viral content, celebrity users, flash sales — key salting, multi-layer caching, dedicated shards, and CDN absorption for hot key mitigation.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [What Causes Hot Keys](#what-causes-hot-keys)
3. [Key Salting (Partition Spreading)](#key-salting-partition-spreading)
4. [Multi-Layer Caching for Hot Keys](#multi-layer-caching-for-hot-keys)
5. [Dedicated Shards / Special Handling](#dedicated-shards--special-handling)
6. [CDN Absorption](#cdn-absorption)
7. [Hot Key Detection](#hot-key-detection)
8. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
9. [Common Interview Mistakes](#common-interview-mistakes)
10. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **hot key/partition** receives disproportionately more traffic than others, overwhelming that single node while others sit idle. This is the #1 cause of unexplained performance degradation in sharded systems.

```
Normal:    Partition 0: 20%, Partition 1: 20%, Partition 2: 20%, ...
Hot key:   Partition 0: 80%, Partition 1: 5%, Partition 2: 5%, ...

Partition 0 is overloaded → high latency → timeouts → cascading failures
Other partitions are idle → wasted capacity
```

---

## What Causes Hot Keys

| Cause | Example | Scale |
|---|---|---|
| **Viral content** | One tweet gets 50% of all engagement | Millions of reads/writes per second |
| **Celebrity user** | @BarackObama's profile viewed constantly | 100K+ reads/sec for one key |
| **Flash sale** | One product gets 500K simultaneous buyers | Extreme write contention |
| **Trending topic** | #SuperBowl during the game | Millions of writes to one counter |
| **Breaking news** | Single article gets all traffic | Massive read spike |
| **Popular stream** | #1 Twitch streamer live | Millions of concurrent viewers |

---

## Key Salting (Partition Spreading)

### The Problem
```
Kafka partition key: tweet_id = "viral_tweet_123"
All events for this tweet → ONE partition → ONE consumer
Consumer capacity: 10K events/sec
Actual load: 100K events/sec → consumer can't keep up → lag grows
```

### The Solution: Append Random Salt
```
Instead of:  partition_key = tweet_id
Use:         partition_key = tweet_id + "_" + random(0, 19)

"viral_tweet_123_0"  → Partition 7
"viral_tweet_123_1"  → Partition 12
"viral_tweet_123_2"  → Partition 3
...
"viral_tweet_123_19" → Partition 15

Load spread across 20 partitions instead of 1.
Each partition handles 5K events/sec (100K / 20) — within capacity.
```

### The Cost: Aggregation Step
```
Without salting: Counter for tweet_123 is in one place. Simple HGET.
With salting:    Counter is spread across 20 keys.

Read: Must query all 20 salted keys and SUM:
  HGET counter:viral_tweet_123_0:clicks → 5,231
  HGET counter:viral_tweet_123_1:clicks → 4,892
  ...
  Total = SUM(all 20) = 98,472

Or: Use a tumbling window aggregation (Flink merges every 10 seconds):
  Flink reads 20 partial counts → SUM → writes total to serving layer
  Dashboard reads the pre-merged total → no client-side aggregation
```

### Interview Script
> *"For a viral tweet generating 50% of all engagement events, I'd salt the Kafka partition key. Instead of partitioning by `tweet_id` (which sends everything to one partition), I'd partition by `tweet_id + random(0-19)` — spreading the load across 20 partitions. A downstream Flink aggregation step merges the 20 partial counts every 10 seconds. The salting adds 10 seconds of latency but prevents a single partition from becoming a bottleneck."*

---

## Multi-Layer Caching for Hot Keys

### Architecture
```
Layer 1: CDN (edge cache, 1-second TTL)
  99% of browse requests served from CDN edge
  Origin only gets 1% of traffic

Layer 2: Application-level cache (Redis, 1-second TTL)
  Cache the hot key in Redis
  1% of requests that reach our servers → Redis hit (< 1ms)

Layer 3: Database
  Only touched by:
    - Purchase transactions (need exact, real-time data)
    - Cache misses (< 0.01% of browse traffic)

Result:
  100K requests/sec for flash sale product page
  → 99K served by CDN (never touches our servers)
  → 990 served by Redis (never touches DB)
  → 10 actual DB queries (purchase transactions only)
```

### Interview Script
> *"For a flash sale item where 100K users check inventory simultaneously, I'd use multi-layered caching. Layer 1: CDN caches the product page for 1 second — 99% of requests served from edge. Layer 2: Redis with 1-second TTL for the inventory count. Layer 3: the actual database row is only touched by the purchase transaction, not the browse request. The 'approximately 47 remaining' count can be 1 second stale — accuracy only matters at the moment of purchase."*

---

## Dedicated Shards / Special Handling

### Route Hot Keys Differently
```
if (key in known_hot_keys):
    route_to_dedicated_shard(key)  # Bigger, more resources
else:
    route_via_consistent_hashing(key)  # Normal routing

Dedicated shard: Larger instance, more memory, higher IOPS
  Only handles hot keys → can be optimized specifically
```

### Dynamic Hot Key Detection
```
Monitor request distribution per shard:
  If one shard gets > 3x average traffic → flag as hot
  Automatically route that key's reads to a replicated cache
  Alert operations team for manual review
```

---

## CDN Absorption

### For Read-Heavy Hot Keys
```
Product page during flash sale:
  Set Cache-Control: public, max-age=1 (1-second freshness)
  
  At 100K requests/sec:
    CDN serves 99,000 requests from edge (cached)
    Origin gets 1,000 requests/sec (one per second per edge location)
  
  Origin load: Reduced 100x by CDN
  User impact: Product info at most 1 second stale (acceptable for browsing)
  
  Purchase button → NOT cached (bypasses CDN, hits real-time inventory)
```

---

## Hot Key Detection

### Metrics to Monitor
```
1. Kafka partition lag imbalance:
   If one partition's lag is 10x others → hot partition

2. Redis key access frequency:
   OBJECT FREQ key (requires LFU eviction policy)
   If one key has 1000x more accesses → hot key

3. Database query distribution:
   If one row/partition gets 80% of queries → hot partition

4. CDN cache hit ratio drop:
   If one URL has abnormally low hit ratio → uncacheable hot content
```

### Automated Response
```
Detection: Monitoring detects hot key (> 5x average traffic)
Response 1: Automatically add local cache for this key (in-process)
Response 2: Replicate the key's data to multiple read replicas
Response 3: Alert on-call engineer for manual mitigation
Response 4: If persists > 10 minutes → auto-create dedicated shard
```

---

## Interview Talking Points & Scripts

### Key Salting
> *"Salt the partition key with random(0-19). Spread one hot key across 20 partitions. Flink merges partial counts every 10 seconds. 10s latency tradeoff for preventing single-partition bottleneck."*

### Multi-Layer Caching
> *"CDN (1s TTL) absorbs 99% of reads. Redis absorbs 99% of what remains. DB only touched by purchase transactions. 100K requests → 10 DB queries."*

---

## Common Interview Mistakes

### ❌ Not considering hot keys in sharded designs
### ❌ Only one solution (need layered: CDN + cache + salting + dedicated shard)
### ❌ Not mentioning the aggregation cost of key salting
### ❌ Ignoring the difference between read-hot and write-hot keys

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          HOT PARTITIONS & HOT KEYS CHEAT SHEET               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CAUSES: Viral content, celebrity, flash sale, trending      │
│                                                              │
│  SOLUTIONS (layered):                                        │
│  1. CDN: Absorb 99% of reads at edge (1s TTL)               │
│  2. Cache: Redis absorbs remaining reads (1s TTL)            │
│  3. Key Salting: Spread writes across N partitions           │
│     Cost: Aggregation step (Flink merge every 10s)           │
│  4. Dedicated Shard: Bigger instance for known hot keys      │
│  5. Dynamic Detection: Monitor + auto-mitigate               │
│                                                              │
│  READ-HOT: CDN + cache (absorb reads)                        │
│  WRITE-HOT: Key salting + aggregation (spread writes)        │
│                                                              │
│  DETECTION: Partition lag imbalance, key frequency,           │
│             query distribution, CDN hit ratio anomaly         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
