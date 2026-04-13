# 🎯 Topic 20: Deduplication

> **System Design Interview — Deep Dive**
> Comprehensive coverage of deduplication strategies — Bloom filters, idempotency keys, two-tier dedup, content hashing, and how to prevent duplicate processing in distributed systems.

---

## Table of Contents

- [🎯 Topic 20: Deduplication](#-topic-20-deduplication)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Why Deduplication Matters](#why-deduplication-matters)
  - [Bloom Filter — Probabilistic Dedup](#bloom-filter--probabilistic-dedup)
    - [How It Works](#how-it-works)
    - [Configuration](#configuration)
    - [Trade-offs](#trade-offs)
    - [Interview Script](#interview-script)
  - [Two-Tier Deduplication Strategy](#two-tier-deduplication-strategy)
    - [Architecture](#architecture)
    - [Why Two Tiers?](#why-two-tiers)
    - [Interview Script](#interview-script-1)
  - [Idempotency Keys — Payment Dedup](#idempotency-keys--payment-dedup)
    - [The Problem](#the-problem)
    - [The Solution](#the-solution)
    - [Implementation](#implementation)
    - [Interview Script](#interview-script-2)
  - [Content-Based Dedup (Hashing)](#content-based-dedup-hashing)
    - [For Media Files](#for-media-files)
    - [For Messages](#for-messages)
  - [Event Dedup in Stream Processing](#event-dedup-in-stream-processing)
    - [The At-Least-Once Challenge](#the-at-least-once-challenge)
    - [Solutions](#solutions)
  - [Dedup at Different Layers](#dedup-at-different-layers)
  - [Redis-Based Exact Dedup](#redis-based-exact-dedup)
    - [Pattern: SETNX with TTL](#pattern-setnx-with-ttl)
    - [Memory Estimation](#memory-estimation)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [Two-Tier for High Volume](#two-tier-for-high-volume)
    - [Idempotency for Payments](#idempotency-for-payments)
    - [Bloom Filter for Web Crawler](#bloom-filter-for-web-crawler)
  - [Common Interview Mistakes](#common-interview-mistakes)
    - [❌ Mistake 1: Not mentioning dedup for at-least-once delivery](#-mistake-1-not-mentioning-dedup-for-at-least-once-delivery)
    - [❌ Mistake 2: Using only Bloom filter for billing-critical dedup](#-mistake-2-using-only-bloom-filter-for-billing-critical-dedup)
    - [❌ Mistake 3: Not mentioning idempotency keys for payment APIs](#-mistake-3-not-mentioning-idempotency-keys-for-payment-apis)
    - [❌ Mistake 4: Unbounded dedup state](#-mistake-4-unbounded-dedup-state)
  - [Dedup Strategy by System Design Problem](#dedup-strategy-by-system-design-problem)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Deduplication** ensures the same operation is not processed more than once, even when retries, network failures, or duplicate messages cause the same request/event to arrive multiple times.

```
Without dedup:
  Client retries payment (network timeout) → server processes TWICE → double charge!

With dedup:
  Client retries payment with same idempotency key → server detects duplicate → returns original result
```

---

## Why Deduplication Matters

| Scenario | Without Dedup | With Dedup |
|---|---|---|
| **Payment retry** | Double charge ($100 twice) | Charge once, return cached result |
| **Ad click counting** | Inflated counts → overbilling | Accurate counts → fair billing |
| **Email notification** | User gets same email 5 times | User gets email once |
| **Message delivery** | Same message appears multiple times | Message appears once |
| **Web crawler** | Crawls same URL repeatedly (wastes resources) | Skips already-crawled URLs |
| **Event processing** | Same event counted multiple times | Event processed exactly once |

---

## Bloom Filter — Probabilistic Dedup

### How It Works
A **Bloom filter** is a space-efficient probabilistic data structure that tells you:
- **"Definitely not seen"** (100% accurate — zero false negatives)
- **"Probably seen"** (small false positive rate — configurable)

```
Bit array of size m, k hash functions:

Insert "url_abc":
  hash1("url_abc") % m = 42   → set bit 42
  hash2("url_abc") % m = 1337 → set bit 1337
  hash3("url_abc") % m = 789  → set bit 789

Check "url_xyz":
  hash1("url_xyz") % m = 42   → bit 42 is SET
  hash2("url_xyz") % m = 100  → bit 100 is NOT SET
  → "Definitely not seen" (at least one bit is 0)

Check "url_abc":
  hash1("url_abc") % m = 42   → SET
  hash2("url_abc") % m = 1337 → SET
  hash3("url_abc") % m = 789  → SET
  → "Probably seen" (all bits are 1, but could be coincidental)
```

### Configuration
```
For 10 billion items at 0.01% false positive rate:
  Bit array size: ~1.5 GB
  Hash functions: 10

Compare to hash set:
  10 billion URLs × 20 bytes/URL = 200 GB

Memory savings: 133x (1.5 GB vs 200 GB)
```

### Trade-offs
```
✅ O(1) lookup and insert (constant time)
✅ 1000x less memory than exact data structures
✅ Zero false negatives ("definitely not seen" is always correct)
✅ Perfect for "have I seen this before?" checks

❌ False positives (0.01-1% configurable — "maybe seen" may be wrong)
❌ Cannot delete items (standard Bloom filter)
❌ Cannot enumerate stored items
❌ Size must be pre-configured (can't grow dynamically)
```

### Interview Script
> *"The Bloom filter lets us check 'have I seen this URL before?' in < 1 microsecond with zero false negatives and a tunable false positive rate. For our web crawler indexing 10 billion URLs, I'd configure it with m = 1.5 GB and k = 10 hash functions, giving a false positive rate of 0.1%. That means 1 in 1000 new URLs is incorrectly flagged as 'already crawled' — acceptable for a crawler, and the memory cost is 1000x less than storing all 10 billion URLs in a hash set."*

---

## Two-Tier Deduplication Strategy

### Architecture
```
Tier 1: Bloom Filter (fast, approximate)
  Check: "Have I seen this click event before?"
  If Bloom says "definitely not seen" (99.99%) → process immediately
  If Bloom says "maybe seen" (0.01%) → go to Tier 2

Tier 2: Redis Exact Lookup (slower, exact)
  Check: SETNX click:{event_id} 1 EX 3600
  If SET succeeded (new) → process
  If SET failed (exists) → duplicate, skip

Result:
  99.99% of events: resolved in < 1 microsecond (Bloom filter)
  0.01% of events: resolved in < 1 millisecond (Redis)
  0% false negatives, 0% false positives (perfect accuracy)
```

### Why Two Tiers?
```
Bloom-only: 0.01% false positives → 0.01% of legitimate events skipped
  At 2.3M events/sec: 230 events/sec incorrectly skipped

Redis-only: Exact, but 2.3M Redis SETNX/sec → expensive, high latency
  Need a large Redis cluster just for dedup

Two-tier: Bloom handles 99.99%, Redis handles 0.01%
  Redis load: 230 requests/sec (vs 2.3M) → trivial
  Accuracy: 100% (Redis catches Bloom's false positives)
```

### Interview Script
> *"I'd use a two-tier dedup strategy for the ad click pipeline processing 2.3 million events per second. Tier 1: in-memory Bloom filter (~1GB for 10 billion items at 0.01% false positive rate) — checks in < 1 microsecond. If the Bloom says 'definitely not seen' (99.99% of events), we process it immediately. If the Bloom says 'maybe seen' (0.01%), we go to Tier 2: Redis SETNX with 1-hour TTL — exact check in < 1ms. This gives us 100% accuracy while the Bloom filter handles 99.99% of the volume at near-zero cost."*

---

## Idempotency Keys — Payment Dedup

### The Problem
```
1. Client sends: POST /charge { amount: $100, card: xxxx }
2. Server charges card → $100 deducted
3. Response lost (network timeout)
4. Client retries: POST /charge { amount: $100, card: xxxx }
5. Server charges card AGAIN → $200 deducted total!
```

### The Solution
```
1. Client generates unique key: idempotency_key = uuid()
2. Client sends: POST /charge { amount: $100, key: "abc-123" }
3. Server: SET idempotency:abc-123 {result} NX EX 86400
   NX = only set if not exists; EX = expire in 24 hours
4. Key was NEW → process payment → store result → return result
5. Network timeout, client retries with SAME key
6. Server: SET idempotency:abc-123 ... → key EXISTS
7. Return stored result from step 4 → no double charge
```

### Implementation
```python
def charge_payment(request):
    key = f"idempotency:{request.idempotency_key}"
    
    # Check if already processed
    existing_result = redis.get(key)
    if existing_result:
        return json.loads(existing_result)  # Return cached result
    
    # Acquire lock (prevent concurrent processing of same key)
    if not redis.set(f"lock:{key}", "1", nx=True, ex=30):
        return {"status": "processing"}  # Another request is handling this
    
    try:
        # Process the payment
        result = payment_gateway.charge(request.amount, request.card)
        
        # Store result for future dedup
        redis.set(key, json.dumps(result), ex=86400)  # 24-hour TTL
        
        return result
    finally:
        redis.delete(f"lock:{key}")
```

### Interview Script
> *"For the payment API, every request must carry a client-generated idempotency key (a UUID). Before processing, we check Redis: `SET idempotency:{key} {result_json} NX EX 86400`. If the SET succeeds (key was new), we process the payment and store the result. If the SET fails (key existed), we return the stored result. This means if the client's network drops after we charge the card but before they receive the response, they retry with the same idempotency key and get the original result — no double charge."*

---

## Content-Based Dedup (Hashing)

### For Media Files
```
Upload flow:
  1. Compute SHA-256 hash of file content
  2. Check: SELECT * FROM media WHERE content_hash = 'abc123...'
  3. If exists → reuse existing processed files (skip processing)
  4. If not → process normally, store hash for future dedup

Savings: ~15-20% of uploads are duplicates (reposts, forwards)
```

### For Messages
```
Dedup within a time window:
  hash = SHA-256(user_id + content + timestamp_minute)
  Check: Redis SET dedup:{hash} 1 NX EX 60
  If new → deliver message
  If exists → duplicate within same minute → skip
```

---

## Event Dedup in Stream Processing

### The At-Least-Once Challenge
```
Kafka delivers at-least-once by default:
  Producer → Kafka (ACK) → Consumer processes → Consumer commits offset
  
  If consumer crashes AFTER processing but BEFORE committing offset:
    Consumer restarts → reads from last committed offset → REPROCESSES events
    
  Events 100-105: processed, offset committed at 100
  Events 106-110: processed, consumer crashes before commit
  Consumer restarts: reads from offset 100 → events 106-110 processed AGAIN
```

### Solutions

**Solution 1: Idempotent Consumer**
```python
def process_event(event):
    # Check if already processed
    if redis.sismember(f"processed:{event.topic}", event.id):
        return  # Skip duplicate
    
    # Process
    handle_event(event)
    
    # Mark as processed
    redis.sadd(f"processed:{event.topic}", event.id)
    redis.expire(f"processed:{event.topic}", 86400)  # 24-hour window
```

**Solution 2: Transactional Outbox**
```
Write business data AND dedup marker in same transaction:
  BEGIN;
    INSERT INTO orders (order_id, ...) VALUES (...);
    INSERT INTO processed_events (event_id) VALUES (event.id);
  COMMIT;

If the event was already processed, the INSERT fails (duplicate key).
Atomic: either both succeed or neither does.
```

**Solution 3: Kafka Exactly-Once (Transactional)**
```
Producer: enable.idempotence=true + transactional.id
Consumer: isolation.level=read_committed

Kafka guarantees: each event processed exactly once within the transaction boundary.
Cost: ~20% throughput reduction.
```

---

## Dedup at Different Layers

```
┌────────────────────────────────────────────────────────────┐
│                DEDUP AT EVERY LAYER                         │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Layer 1: Client-Side                                      │
│  ├── Disable submit button after click                     │
│  ├── Debounce rapid API calls (300ms)                      │
│  └── Include idempotency key in retry                      │
│                                                            │
│  Layer 2: API Gateway                                      │
│  ├── Reject duplicate requests (same idempotency key)      │
│  └── Rate limiting (prevents rapid duplicates)             │
│                                                            │
│  Layer 3: Application Service                              │
│  ├── Idempotency key check in Redis                        │
│  ├── Database unique constraints                           │
│  └── Optimistic locking (version check)                    │
│                                                            │
│  Layer 4: Message Consumer                                 │
│  ├── Bloom filter for fast dedup                           │
│  ├── Redis SETNX for exact dedup                           │
│  └── Transactional outbox pattern                          │
│                                                            │
│  Layer 5: Database                                         │
│  ├── UNIQUE constraints                                    │
│  ├── INSERT ... ON CONFLICT DO NOTHING                     │
│  └── Upsert (INSERT or UPDATE)                             │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## Redis-Based Exact Dedup

### Pattern: SETNX with TTL
```python
def is_duplicate(event_id, ttl_seconds=3600):
    """Returns True if this event was already seen within the TTL window."""
    result = redis.set(
        f"dedup:{event_id}",
        "1",
        nx=True,    # Only set if not exists
        ex=ttl_seconds  # Auto-expire
    )
    return result is None  # None means key already existed = duplicate
```

### Memory Estimation
```
100 million unique events per hour:
  Key: "dedup:{event_id}" → ~30 bytes per key
  Value: "1" → 1 byte
  Redis overhead: ~50 bytes per key
  
  Memory: 100M × 80 bytes = 8 GB
  
  With 1-hour TTL: Max 8 GB at steady state (old keys expire)
  Redis instance: r6g.xlarge (26 GB) → comfortable
```

---

## Interview Talking Points & Scripts

### Two-Tier for High Volume
> *"Two-tier dedup: Bloom filter (99.99% of checks in < 1μs) + Redis SETNX (0.01% exact check in < 1ms). 100% accuracy, minimal Redis load."*

### Idempotency for Payments
> *"Every payment request carries a client-generated idempotency key. Redis SETNX stores the result. Retries with the same key return the cached result — no double charge."*

### Bloom Filter for Web Crawler
> *"Bloom filter with 1.5 GB for 10 billion URLs at 0.01% false positive. 1000x less memory than a hash set. Acceptable for a crawler where skipping 0.01% of new URLs is fine."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not mentioning dedup for at-least-once delivery
**Fix**: Kafka is at-least-once by default. Consumer must be idempotent.

### ❌ Mistake 2: Using only Bloom filter for billing-critical dedup
**Fix**: Bloom filter has false positives. For billing, use exact dedup (Redis SETNX or DB unique constraint).

### ❌ Mistake 3: Not mentioning idempotency keys for payment APIs
**Fix**: Every payment API must support idempotency keys. Stripe, PayPal, and every serious payment API does this.

### ❌ Mistake 4: Unbounded dedup state
**Fix**: Use TTL on dedup keys. Events older than the TTL window don't need dedup.

---

## Dedup Strategy by System Design Problem

| Problem | Dedup Method | Scope | TTL |
|---|---|---|---|
| **Ad click counting** | Two-tier (Bloom + Redis) | Per click event | 1 hour |
| **Payment processing** | Idempotency key in Redis | Per payment request | 24 hours |
| **Web crawler** | Bloom filter | Per URL | Permanent (until rebuild) |
| **Email notification** | Redis SETNX | Per notification | 1 hour |
| **Chat messages** | Content hash + timestamp | Per conversation | 1 minute |
| **Media upload** | SHA-256 content hash | Global | Permanent |
| **Event processing** | Transactional outbox | Per event | 24 hours |
| **API retries** | Idempotency key | Per request | 1 hour |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               DEDUPLICATION CHEAT SHEET                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  BLOOM FILTER: Probabilistic, 1000x less memory             │
│    "Definitely not seen" (100%) or "maybe seen" (FP rate)    │
│    Use: Web crawler, high-volume first-pass filter           │
│                                                              │
│  TWO-TIER: Bloom (fast) + Redis (exact)                      │
│    99.99% resolved by Bloom, 0.01% by Redis                  │
│    100% accuracy, minimal Redis load                         │
│    Use: Ad click counting, event dedup                       │
│                                                              │
│  IDEMPOTENCY KEY: Client-generated UUID per request          │
│    Redis: SET idempotency:{key} {result} NX EX 86400        │
│    Use: Payments, any API where retries cause harm           │
│                                                              │
│  CONTENT HASH: SHA-256 of file/message content               │
│    Use: Media dedup, message dedup                           │
│                                                              │
│  TRANSACTIONAL OUTBOX: Business data + dedup marker          │
│    in same DB transaction                                    │
│    Use: Event sourcing, exactly-once processing              │
│                                                              │
│  PRINCIPLE: Make every operation idempotent.                 │
│    Then the universal answer to failure is "retry."          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
