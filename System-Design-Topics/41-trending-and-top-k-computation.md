# 🎯 Topic 39: Trending & Top-K Computation

> **System Design Interview — Deep Dive**
> Trending ≠ most popular — spike-based scoring, Flink windowed aggregation, Redis sorted sets for serving, and anti-bot measures.

---

## Core Insight: Trending ≠ Most Popular

```
"Weather" is always the most mentioned topic — but it's NOT trending.
"Trending" means an UNUSUAL SPIKE relative to baseline.

Formula:
  trend_score = (current_hour_volume / 7_day_hourly_average) × sqrt(unique_users) × time_decay

Example:
  Hashtag A: 1,000 mentions this hour, normally 100/hour → ratio = 10x → TRENDING
  Hashtag B: 50,000 mentions this hour, normally 45,000/hour → ratio = 1.11x → NOT trending

  sqrt(unique_users): Prevents bot manipulation
    1000 tweets from 1 bot:  sqrt(1) = 1
    1000 tweets from 1000 users: sqrt(1000) = 31.6
```

## Architecture

```
Raw events (Kafka) → Flink (5-min tumbling windows) → Compute trend scores
  → Write top 50 to Redis sorted set: ZADD trending:global {score} {hashtag}

Serving:
  ZREVRANGE trending:global 0 49 → top 50 trending hashtags (< 1ms)
  
Regional trending: trending:us-east, trending:ap-northeast (separate sorted sets)
Update frequency: Every 5 minutes (5-minute staleness is invisible to users)
```

## Interview Script
> *"Trending isn't just 'most popular' — it means an unusual spike relative to baseline. I'd compute trend_score = (current_volume / 7_day_average) × sqrt(unique_users) × time_decay. A hashtag with 1,000 mentions that normally gets 100 (10x spike) is more 'trending' than one with 50,000 that normally gets 45,000 (1.11x). The sqrt(unique_users) prevents bot manipulation."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│         TRENDING & TOP-K COMPUTATION CHEAT SHEET             │
├──────────────────────────────────────────────────────────────┤
│  TRENDING = unusual spike, NOT most popular                  │
│  Score = (current / baseline) × sqrt(unique_users) × decay   │
│  Compute: Flink 5-min windows → trend scores                 │
│  Serve: Redis sorted set (ZREVRANGE top 50 in < 1ms)         │
│  Regional: Separate sorted sets per region                   │
│  Anti-bot: sqrt(unique_users) penalizes single-source spam   │
│  Update: Every 5 minutes (invisible staleness)               │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
