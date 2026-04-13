# 🎯 Topic 7: Push vs Pull (Fanout)

> **System Design Interview — Deep Dive**
> A comprehensive guide covering fanout-on-write (push), fanout-on-read (pull), and the hybrid approach — the most critical architecture decision in any social feed or notification system.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Fanout-on-Write (Push Model)](#fanout-on-write-push-model)
3. [Fanout-on-Read (Pull Model)](#fanout-on-read-pull-model)
4. [The Celebrity Problem](#the-celebrity-problem)
5. [Hybrid Approach — The Production Solution](#hybrid-approach--the-production-solution)
6. [The Hybrid Read Path](#the-hybrid-read-path)
7. [Fanout Budget & Capacity Planning](#fanout-budget--capacity-planning)
8. [Data Structures for Timeline Storage](#data-structures-for-timeline-storage)
9. [Fanout Service Architecture](#fanout-service-architecture)
10. [Handling Deletes and Edits](#handling-deletes-and-edits)
11. [Real-World Implementations](#real-world-implementations)
12. [Push vs Pull for Other Systems](#push-vs-pull-for-other-systems)
13. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
14. [Common Interview Mistakes](#common-interview-mistakes)
15. [Applying Push/Pull to System Design Problems](#applying-pushpull-to-system-design-problems)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

When User A creates content (tweet, post, photo) and User B follows User A, **how does User B's timeline get updated?**

Two fundamental approaches:

| Approach | When It Happens | Who Does the Work | Analogy |
|---|---|---|---|
| **Push (Fanout-on-Write)** | When A posts | A's server pushes to all followers' timelines | Mail carrier delivers to every mailbox |
| **Pull (Fanout-on-Read)** | When B reads their feed | B's server fetches from all followees | Going to the post office to check all mailboxes |

---

## Fanout-on-Write (Push Model)

### How It Works
```
1. Alice posts a tweet
2. System looks up Alice's followers: [Bob, Charlie, Dana, ... 500 followers]
3. For EACH follower, push tweet_id into their pre-computed timeline
4. When Bob opens his feed, his timeline is already built — just read it

Write path:
Alice tweets → Fanout Service → ZADD timeline:bob {timestamp} {tweet_id}
                               → ZADD timeline:charlie {timestamp} {tweet_id}
                               → ZADD timeline:dana {timestamp} {tweet_id}
                               → ... (500 times)

Read path:
Bob opens feed → ZREVRANGE timeline:bob 0 19 → returns 20 most recent tweet_ids
              → Batch fetch tweet content from Cassandra
              → Return assembled timeline
```

### Advantages
- **Fast reads**: Timeline is pre-computed. Reading is a single Redis `ZREVRANGE` call — < 5ms.
- **Predictable read latency**: Read path is simple regardless of how many people the user follows.
- **Works great for 99% of users**: Most users have < 10K followers — fanout is fast and cheap.

### Disadvantages
- **Slow writes for popular users**: If Alice has 10 million followers, one tweet triggers 10 million Redis writes.
- **Write amplification**: 1 tweet → N writes (N = follower count).
- **Storage overhead**: Every follower's timeline stores a copy of the tweet_id.
- **Wasted work**: Many followers may never read their timeline (inactive users).

### Performance Characteristics
```
Write latency (per tweet):
  100 followers:     ~1ms  (100 ZADD operations, pipelined)
  10,000 followers:  ~100ms (10K ZADD operations)
  1,000,000 followers: ~10 seconds (1M ZADD operations)
  10,000,000 followers: ~100 seconds ← UNACCEPTABLE

Read latency: ~5ms (single ZREVRANGE + batch content fetch)
```

---

## Fanout-on-Read (Pull Model)

### How It Works
```
1. Alice posts a tweet → stored in Alice's tweet list only
2. When Bob opens his feed:
   a. Look up who Bob follows: [Alice, Eve, Frank, ... 300 followees]
   b. For EACH followee, fetch their recent tweets
   c. Merge all tweets by timestamp
   d. Return the top 20

Write path:
Alice tweets → INSERT INTO tweets (tweet_id, user_id, content, timestamp)
            → Done! No fanout.

Read path:
Bob opens feed → Get Bob's followee list (300 users)
              → For each followee: SELECT recent tweets
              → Merge 300 lists by timestamp
              → Return top 20 tweets
```

### Advantages
- **Fast writes**: Write is a single database insert — < 5ms regardless of follower count.
- **No wasted work**: Only compute the timeline when someone actually reads it.
- **Always fresh**: Timeline is computed on-demand, so it's never stale.
- **No storage overhead**: No pre-computed timelines to maintain.

### Disadvantages
- **Slow reads**: Must query N followees at read time. 300 followees = 300 queries.
- **Unpredictable read latency**: Users following more people get slower feeds.
- **High read-time compute**: Merging 300 sorted lists is CPU-intensive.
- **Doesn't scale for high QPS**: If 100K users refresh their feeds simultaneously, each triggering 300 queries, that's 30 million DB queries.

### Performance Characteristics
```
Write latency: ~5ms (single insert)

Read latency (per feed request):
  Following 50 people:   ~50ms  (50 parallel queries + merge)
  Following 300 people:  ~200ms (300 parallel queries + merge)
  Following 1000 people: ~500ms ← Noticeable to users
```

---

## The Celebrity Problem

### Why Pure Push Fails at Scale

When @BarackObama (133 million followers) tweets:
```
Pure push: 133,000,000 Redis ZADD operations
At 100K writes/sec per Redis node: 133M / 100K = 1,330 seconds = ~22 minutes

By the time fanout completes:
  - The tweet is 22 minutes old (no longer "real-time")
  - Redis cluster consumed massive bandwidth
  - Other users' fanouts are delayed (queue backup)
  - If Obama tweets twice in 22 minutes, the second tweet's fanout
    starts before the first finishes → cascading delays
```

### Why Pure Pull Also Fails

If Bob follows 500 people including 10 celebrities:
```
Pure pull: Fetch recent tweets from 500 followees
  - 490 normal users: Fast (small tweet lists, partitioned well)
  - 10 celebrities: Slow (millions of tweets, hot partitions)
  - Total: ~500 parallel queries, merge 500 lists
  - Latency: 200-500ms per feed refresh
  - At 100K concurrent feed refreshes: 50M queries → DB overload
```

### The Insight
Neither pure approach works because the follower distribution is **power-law**: 99.99% of users have < 10K followers (push works great), 0.01% have millions (push is catastrophic). We need **different strategies for different users**.

---

## Hybrid Approach — The Production Solution

### The Algorithm
```
Define threshold: CELEBRITY_THRESHOLD = 1,000,000 followers

On tweet creation:
  IF author.follower_count < CELEBRITY_THRESHOLD:
    → Fan out on write (push tweet_id to all followers' timelines)
  ELSE:
    → Do NOT fan out (store tweet in author's tweet list only)

On timeline read:
  1. Fetch pre-computed timeline from Redis (has normal users' tweets)
  2. Fetch recent tweets from celebrity followees (pulled on demand)
  3. Merge both lists by timestamp
  4. Return top N tweets
```

### Why This Works
```
99.99% of users (< 1M followers):
  → Fan out on write
  → Their tweets appear in followers' pre-computed timelines
  → Read path: already there, no extra work

0.01% of users (> 1M followers — celebrities):
  → Skip fanout (avoid 10M+ write storm)
  → Their tweets are pulled at read time
  → Read path: small extra cost (5-10 celebrity queries)

Result:
  Write: No 10M-write storms (celebrities excluded from fanout)
  Read: ~25ms (pre-computed timeline + 5-10 celebrity queries merged)
  User experience: Identical whether the tweet came from push or pull
```

### Interview Script
> *"I'd use a hybrid approach with a follower threshold of ~1 million. For 99% of users who have under 1M followers, we fan out on write — when they tweet, we push the tweet_id into each follower's Redis timeline using `ZADD timeline:{follower_id} {timestamp} {tweet_id}`. For the top 0.01% with millions of followers (celebrities), we skip the fanout entirely and merge their tweets at read time. This prevents the 10-million-write storm that would take ~100 seconds when a celebrity tweets."*

---

## The Hybrid Read Path

### Step-by-Step
```
Bob opens his feed:

Step 1: Fetch pre-computed timeline from Redis
  ZREVRANGE timeline:bob 0 99
  → Returns 100 tweet_ids from normal users Bob follows
  → Latency: < 5ms

Step 2: Identify Bob's celebrity followees
  SMEMBERS celebrity_followees:bob
  → Returns [obama, taylorswift, elonmusk, ...] (typically 5-10)
  → Latency: < 1ms

Step 3: Fetch recent tweets from each celebrity (parallelized)
  For each celebrity:
    SELECT tweet_id, timestamp FROM tweets 
    WHERE user_id = ? AND timestamp > (now - 24h)
    ORDER BY timestamp DESC LIMIT 20
  → 5-10 parallel queries to Cassandra
  → Latency: < 20ms total (parallelized)

Step 4: Merge the two lists by timestamp (in memory)
  merged = merge_sorted([pre_computed_timeline, celebrity_tweets])
  → Take top 20
  → Latency: < 1ms

Step 5: Batch-fetch full tweet content
  For each tweet_id in merged list:
    Fetch from tweet content cache (Redis) or Cassandra
  → Latency: < 10ms (batch read)

Total read latency: ~25ms
```

### Interview Script
> *"The read path for the hybrid approach works like this: fetch the pre-computed timeline from Redis (covers normal users' tweets) — that's one `ZREVRANGE` call, < 5ms. Then query Cassandra for recent tweets from the user's celebrity followees — typically just 5-10 celebrities, so it's 5-10 simple queries parallelized, < 20ms total. Merge the two lists by timestamp in-memory, < 1ms. Total read latency: ~25ms for a fully assembled, personalized timeline. The user never knows two different strategies are at play."*

---

## Fanout Budget & Capacity Planning

### Calculating Fanout Volume
```
Given:
  500K tweets per minute
  Average followers per user: 200
  Celebrity exclusion reduces fanout by ~40%

Fanout volume:
  Without celebrity exclusion: 500K × 200 = 100M ZADD/min = 1.67M ZADD/sec
  With celebrity exclusion:    500K × 120 = 60M ZADD/min = 1.0M ZADD/sec

Redis capacity needed:
  At 100K ZADD/sec per Redis node:
    Without exclusion: 17 nodes
    With exclusion: 10 nodes

Savings: 7 Redis nodes = ~40% infrastructure cost reduction
```

### Timeline Storage Size
```
Each timeline entry: tweet_id (8 bytes) + score/timestamp (8 bytes) = 16 bytes
Timeline length: 800 entries per user (last ~2 days of content)
Active users: 200 million

Storage: 200M × 800 × 16 bytes = 2.56 TB

Redis nodes needed (at 25GB usable per node):
  2,560 GB / 25 GB = ~103 nodes

With replication (RF=2): ~206 Redis nodes for timeline storage
```

### Interview Script
> *"The fanout budget is a real capacity planning constraint. If we process 500K tweets per minute and the average user has 200 followers, that's 100 million Redis ZADD operations per minute — about 1.67 million per second. That requires roughly 17 Redis nodes. Celebrity exclusion reduces this by ~40% to ~10 nodes. That's a 40% infrastructure savings from one algorithmic decision."*

---

## Data Structures for Timeline Storage

### Redis Sorted Set (Primary)
```
Key: timeline:{user_id}
Score: timestamp (Unix epoch in milliseconds)
Member: tweet_id

Operations:
  ZADD timeline:bob 1710500000000 tweet_123    # Add tweet to timeline
  ZREVRANGE timeline:bob 0 19                   # Get 20 most recent
  ZREMRANGEBYRANK timeline:bob 0 -801           # Trim to 800 entries
  ZRANK timeline:bob tweet_123                  # Check if tweet exists
```

### Why Sorted Set?
- **O(log N) insert**: Efficient even with 800 entries.
- **O(log N + M) range query**: Get top-M entries instantly.
- **Automatic ordering**: Sorted by timestamp (score).
- **Deduplication**: Same tweet_id won't appear twice.
- **Trim**: Easy to keep timeline at bounded size.

### Alternative: Cassandra Timeline Table
```sql
CREATE TABLE timelines (
    user_id UUID,
    tweet_id TIMEUUID,
    PRIMARY KEY (user_id, tweet_id)
) WITH CLUSTERING ORDER BY (tweet_id DESC);

-- Read timeline: partition-key scan, already sorted
SELECT tweet_id FROM timelines WHERE user_id = ? LIMIT 20;
```

For cold storage (timelines older than 2 days), Cassandra is cheaper than Redis.

---

## Fanout Service Architecture

```
┌──────────┐                        ┌──────────────┐
│  Tweet   │  TweetCreated event    │   Fanout     │
│ Service  │ ─────────────────────→ │   Service    │
└──────────┘                        │              │
                                    │ 1. Check if  │
                                    │    celebrity │
                                    │              │
                         ┌──────────┤ 2a. If normal│──→ Push to followers'
                         │          │    user:     │    Redis timelines
                         │          │    fan out   │
                         │          │              │
                         │          │ 2b. If celeb:│──→ Store in celeb's
                         │          │    skip      │    tweet list only
                         │          └──────────────┘
                         │
                         ▼
                    ┌─────────┐
                    │  Kafka  │  (buffering, ordering, replay)
                    └────┬────┘
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
         ┌────────┐ ┌────────┐ ┌────────┐
         │Fanout  │ │Fanout  │ │Fanout  │  (stateless workers)
         │Worker 1│ │Worker 2│ │Worker 3│
         └───┬────┘ └───┬────┘ └───┬────┘
             │          │          │
             ▼          ▼          ▼
         ┌────────────────────────────┐
         │    Redis Cluster           │
         │  (pre-computed timelines)  │
         └────────────────────────────┘
```

### Key Design Decisions
1. **Kafka between Tweet Service and Fanout**: Decouples tweet creation from fanout. If fanout is slow, tweets still save successfully.
2. **Multiple Fanout Workers**: Parallelism — each worker processes different users' fanouts.
3. **Partition by tweet author**: All fanout for one tweet goes to one worker → avoids duplicate processing.
4. **Async**: Author gets "posted" confirmation immediately; fanout happens in background.

---

## Handling Deletes and Edits

### Tweet Deletion
```
1. Delete tweet from source (Cassandra/PostgreSQL)
2. Publish TweetDeleted event to Kafka
3. Fanout workers: ZREM timeline:{follower_id} {tweet_id} for each follower
   (Same fanout as creation, but removing instead of adding)
4. Alternative: Lazy deletion — when rendering timeline, skip tweet_ids
   that return NULL from content lookup (tombstone check)
```

### Tweet Edit
```
Option 1: In-place update (if tweet content is stored separately)
  - Tweet content in Cassandra is updated
  - Timeline only stores tweet_ids (pointers) → no timeline update needed
  - Next read fetches updated content automatically

Option 2: Delete + re-create
  - For timeline entries that embed content (denormalized)
  - Delete old entry, insert new entry with same timestamp
```

### Lazy Deletion (Preferred)
```
On timeline read:
  tweet_ids = ZREVRANGE timeline:bob 0 19
  tweets = batch_fetch_content(tweet_ids)
  
  # Filter out deleted tweets (content returns NULL)
  live_tweets = [t for t in tweets if t is not None]
  
  # Optionally clean up timeline in background
  for null_tweet_id in (tweet_ids - live_tweet_ids):
      ZREM timeline:bob {null_tweet_id}
  
  return live_tweets
```

---

## Real-World Implementations

### Twitter
- **Hybrid model**: Push for normal users, pull for celebrities.
- **Celebrity threshold**: ~few thousand followers (varies dynamically).
- **Timeline cache**: Redis (called "TimelinesCache" internally).
- **Tweet storage**: Manhattan (custom distributed key-value store, successor to FlockDB).
- **Fanout service**: Processes ~400K tweets/minute with fan-out.

### Instagram
- **Push model**: Fanout on write for most users.
- **Media focus**: Timeline includes photos/videos (heavier content).
- **Cassandra + Redis**: Cassandra for persistent storage, Redis for hot timelines.

### Facebook
- **Pull-based with heavy caching**: Due to complex ranking algorithm.
- **News Feed is ranked, not chronological**: ML model scores each candidate post.
- **TAO**: Custom graph database for social graph operations.
- **Aggregator service**: Pulls candidates, ranks them, caches results.

---

## Push vs Pull for Other Systems

### Notification Systems
```
Push (preferred):
  Event occurs → immediately push notification to user's notification inbox
  User opens app → read from pre-built inbox
  
Why push: Notifications are time-sensitive; delay defeats the purpose.
```

### Email Inbox
```
Push:
  Email arrives → stored in recipient's mailbox immediately
  User opens inbox → read from mailbox
  
Why push: Users expect emails to be there when they check.
```

### Search Results
```
Pull:
  User searches → query executed on demand → results ranked → returned
  
Why pull: Queries are unpredictable; can't pre-compute all possible searches.
```

### Leaderboard
```
Push (hybrid):
  Score update → ZADD to sorted set (push)
  User views leaderboard → ZREVRANGE (read pre-computed ranking)
  
Why push: Leaderboard must be up-to-date; sorted set handles both.
```

### Chat Messages
```
Push:
  Alice sends message → stored + pushed to Bob's connection
  
Why push: Real-time delivery is the core requirement.
```

---

## Interview Talking Points & Scripts

### The Hybrid Explanation
> *"Pure push doesn't work at celebrity scale — when @BarackObama with 133 million followers tweets, pure fanout means 133 million Redis writes. At 100K writes/sec per Redis node, that's 1,330 seconds (~22 minutes) just for one tweet's fanout. By then, the tweet is old news. Pure pull doesn't work either — merging 500 followees' tweets at read time means 500 DB queries, way too slow. The hybrid gives us the best of both worlds."*

### The Threshold Decision
> *"The threshold is a configurable value, not hard-coded. I'd start at 1 million followers and tune based on observed fanout latency. If we find that fanout for users with 500K followers is already causing queue backup, we'd lower the threshold. It's a reversible decision adjustable via configuration."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Only describing push OR pull
**Problem**: Neither works alone at scale. Always present the hybrid.

### ❌ Mistake 2: Not mentioning the celebrity problem
**Problem**: The celebrity problem is the entire reason the hybrid exists. It's the core insight.

### ❌ Mistake 3: Not explaining the read path for hybrid
**Problem**: Saying "we merge at read time" without detailing the two sources (Redis pre-computed + celebrity pull) and how they're combined.

### ❌ Mistake 4: Ignoring inactive users
**Problem**: Pushing to millions of inactive users wastes resources. Consider only fanning out to recently active users' timelines.

### ❌ Mistake 5: Not quantifying the fanout budget
**Problem**: Saying "we push to followers" without calculating the write volume (tweets/sec × avg followers) shows lack of capacity planning awareness.

---

## Applying Push/Pull to System Design Problems

| Problem | Model | Reasoning |
|---|---|---|
| **Twitter/Instagram Feed** | Hybrid (push normal + pull celebrity) | Power-law follower distribution |
| **WhatsApp Messages** | Push | Messages must be delivered immediately |
| **Notifications** | Push | Time-sensitive; user expects them pre-built |
| **Search Results** | Pull | Can't pre-compute infinite query space |
| **Email Inbox** | Push | Emails expected to be there when checking |
| **Leaderboard** | Push (ZADD on score change) | Rankings must be real-time |
| **News Aggregator (RSS)** | Pull (periodic) | Sources are external, polled periodically |
| **Stock Ticker** | Push (SSE/WebSocket) | Real-time price updates |
| **Activity Feed (LinkedIn)** | Hybrid | Similar to Twitter; company pages = celebrities |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              PUSH vs PULL (FANOUT) CHEAT SHEET               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PUSH (Fanout-on-Write):                                     │
│    Write: Push to all followers' timelines                   │
│    Read: Single lookup (pre-computed)                        │
│    ✓ Fast reads (~5ms)                                       │
│    ✗ Slow writes for celebrities (10M+ writes)               │
│                                                              │
│  PULL (Fanout-on-Read):                                      │
│    Write: Single insert                                      │
│    Read: Query all followees, merge results                  │
│    ✓ Fast writes (~5ms)                                      │
│    ✗ Slow reads (300+ queries per feed load)                 │
│                                                              │
│  HYBRID (Production Solution):                               │
│    Normal users (<1M followers): Push                        │
│    Celebrities (>1M followers): Pull at read time            │
│    Read: Pre-computed + celebrity merge = ~25ms              │
│    Savings: ~40% less fanout infrastructure                  │
│                                                              │
│  KEY NUMBERS:                                                │
│    Fanout volume: tweets/min × avg_followers                 │
│    Celebrity threshold: ~1M followers (configurable)         │
│    Timeline size: ~800 entries per user in Redis             │
│    Read latency: ~25ms (hybrid)                              │
│                                                              │
│  DATA STRUCTURE: Redis Sorted Set                            │
│    ZADD timeline:{user} {timestamp} {tweet_id}               │
│    ZREVRANGE timeline:{user} 0 19 (top 20)                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 8: Synchronous vs Asynchronous** — Fanout is async via Kafka
- **Topic 10: Message Queues vs Event Streams** — Kafka for fanout events
- **Topic 31: Hot Partitions** — Celebrity tweets as hot keys
- **Topic 37: WebSocket & Real-Time** — Push for real-time delivery
- **Topic 41: Notification Systems** — Push-based notification delivery

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
