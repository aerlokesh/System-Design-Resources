# 🎯 Topic 11: ID Generation

> **System Design Interview — Deep Dive**
> A comprehensive guide covering all ID generation strategies — UUID, Snowflake, auto-increment, ULID, counter-based — with tradeoffs around sortability, uniqueness, compactness, and coordination requirements.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [ID Requirements by Use Case](#id-requirements-by-use-case)
3. [UUID v4 — Random, Coordinationless](#uuid-v4--random-coordinationless)
4. [Snowflake IDs — Time-Sortable, Compact](#snowflake-ids--time-sortable-compact)
5. [Auto-Increment — Simple, Sequential](#auto-increment--simple-sequential)
6. [Counter-Based with Base62 Encoding](#counter-based-with-base62-encoding)
7. [ULID — Sortable UUID Alternative](#ulid--sortable-uuid-alternative)
8. [Twitter Snowflake Bit Layout](#twitter-snowflake-bit-layout)
9. [Ticket Server Pattern](#ticket-server-pattern)
10. [Database-Specific ID Strategies](#database-specific-id-strategies)
11. [ID Generation at Scale](#id-generation-at-scale)
12. [ID Properties Comparison](#id-properties-comparison)
13. [B-Tree Index Impact](#b-tree-index-impact)
14. [Security Considerations](#security-considerations)
15. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
16. [Common Interview Mistakes](#common-interview-mistakes)
17. [ID Strategy by System Design Problem](#id-strategy-by-system-design-problem)
18. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Every entity in a distributed system needs a **unique identifier**. The choice of ID generation strategy affects:

1. **Uniqueness**: No collisions across all nodes.
2. **Sortability**: Can you order by ID to get chronological order?
3. **Compactness**: How many bytes? Affects index size, URL length, storage.
4. **Coordination**: Do generators need to talk to each other?
5. **Performance**: How many IDs per second can one node generate?
6. **Information leakage**: Does the ID reveal business information (creation time, volume)?

---

## ID Requirements by Use Case

| Use Case | Must be Unique | Sortable? | Compact? | Coordination-Free? |
|---|---|---|---|---|
| **Tweet ID** | ✅ | ✅ (chronological feed) | ✅ (64-bit) | ✅ (1K+ app servers) |
| **URL shortener code** | ✅ | ❌ | ✅ (7 chars) | Depends |
| **Database primary key** | ✅ | ✅ (B-tree locality) | ✅ | Depends |
| **Distributed trace ID** | ✅ | ❌ | ❌ (128-bit OK) | ✅ |
| **Session token** | ✅ | ❌ | ❌ | ✅ |
| **Order number** | ✅ | ✅ | ✅ (human-readable) | ❌ (sequential OK) |
| **Idempotency key** | ✅ | ❌ | ❌ | ✅ (client-generated) |

---

## UUID v4 — Random, Coordinationless

### How It Works
```
128 bits of (mostly) random data:
  550e8400-e29b-41d4-a716-446655440000
  
Generation: uuid.v4() — no coordination needed
Uniqueness: 2^122 possible values = 5.3 × 10^36
Collision probability: ~1 in 2^61 after generating 1 billion UUIDs
```

### Advantages
- **Zero coordination**: Any node generates independently.
- **Simple**: One function call, no infrastructure.
- **Universally unique**: No collisions across data centers, services, or companies.
- **Standard**: Supported in every language and database.

### Disadvantages
- **128 bits (16 bytes)**: 2x the size of a 64-bit Snowflake ID.
- **Not sortable**: Random distribution → no chronological ordering.
- **Terrible B-tree locality**: Random insertions scatter across the entire index → 5-10x more page splits.
- **Not human-readable**: Hard to communicate verbally or debug.
- **Index bloat**: 10 billion rows × 16 bytes = 160 GB of index data (vs 80 GB for 64-bit).

### B-Tree Impact (Critical for Interviews)
```
Sequential IDs (auto-increment, Snowflake):
  New IDs always append to the END of the B-tree index.
  → Hot right edge, rest of tree is cold and cached.
  → Minimal page splits, excellent cache efficiency.

Random IDs (UUID v4):
  New IDs insert at RANDOM positions in the B-tree.
  → Every page is equally likely to be modified.
  → Constant page splits, poor cache efficiency.
  → 5-10x worse write performance on large tables.
```

### Interview Script
> *"UUID v4 is tempting for its simplicity — just call `uuid.v4()` and you get a globally unique ID with zero coordination. But the tradeoffs are significant: 128 bits wastes index space (our messages table has 10 billion rows — that's 80 GB of extra index data compared to 64-bit IDs). The random distribution kills B-tree locality — insertions scatter across the entire index instead of appending to the end, causing 5-10x more page splits. And UUIDs aren't sortable, so every chronological query needs a separate timestamp index."*

---

## Snowflake IDs — Time-Sortable, Compact

### How It Works
A 64-bit ID composed of:
```
┌─────────────────────────────────────────────────────────┐
│ 1 bit  │    41 bits    │   10 bits   │    12 bits       │
│ unused │   timestamp   │  machine ID │   sequence       │
│  (0)   │  (ms since    │  (0-1023)   │  (0-4095)        │
│        │   epoch)      │             │                   │
└─────────────────────────────────────────────────────────┘

Example: 1765281934182400001
```

### Bit Allocation

| Component | Bits | Range | Duration/Capacity |
|---|---|---|---|
| **Unused** | 1 | Always 0 | Keeps ID positive in signed int |
| **Timestamp** | 41 | ms since custom epoch | ~69 years |
| **Machine ID** | 10 | 0-1023 | 1,024 machines |
| **Sequence** | 12 | 0-4095 | 4,096 IDs per ms per machine |

### Total Capacity
```
Per machine: 4,096 IDs per millisecond = 4,096,000 IDs/sec
Total system: 1,024 machines × 4,096,000 = ~4 billion IDs/sec
```

### Advantages
- **Time-sortable**: IDs naturally sort chronologically → no separate timestamp index.
- **64-bit**: Half the size of UUID → fits in BIGINT, efficient indexes.
- **Coordination-free**: Each machine generates independently using its machine ID.
- **Excellent B-tree locality**: Timestamps increase → IDs append to the right side of the index.
- **Embeds timestamp**: Extract creation time from the ID itself.

### Disadvantages
- **Clock dependency**: Requires reasonably synchronized clocks (NTP). Clock going backwards breaks monotonicity.
- **Machine ID management**: Need to assign unique machine IDs (1024 limit).
- **Custom epoch**: Must choose an epoch that provides enough future runway.
- **Not URL-friendly**: 64-bit number as decimal is 19 digits.

### Clock Skew Handling
```
If clock goes backwards:
  Option 1: Wait until clock catches up (simple, brief pause)
  Option 2: Use the last-known timestamp and increment sequence
  Option 3: Log error and refuse to generate IDs (safest)
```

### Interview Script
> *"I'd use Snowflake IDs because we need three properties simultaneously: time-sortability (for chronological feeds without a separate timestamp index), compactness (64-bit fits in a BIGINT, half the size of UUID's 128 bits), and decentralized generation (each of our 1024 application servers generates IDs independently without coordination). The 41-bit timestamp gives us 69 years, the 10-bit machine ID supports 1024 machines, and the 12-bit sequence allows 4096 IDs per millisecond per machine — 4 million IDs/sec total capacity."*

---

## Auto-Increment — Simple, Sequential

### How It Works
```sql
CREATE TABLE tweets (
    tweet_id BIGSERIAL PRIMARY KEY,  -- Auto-incrementing 64-bit integer
    content TEXT,
    created_at TIMESTAMP
);

INSERT INTO tweets (content) VALUES ('Hello!');
-- tweet_id = 1

INSERT INTO tweets (content) VALUES ('World!');
-- tweet_id = 2
```

### Advantages
- **Simple**: Built into every relational database.
- **Sequential**: Perfect B-tree locality.
- **Compact**: 64-bit integer.
- **Human-readable**: Order #1001, #1002, #1003.

### Disadvantages
- **Single point of failure**: The database is the sole ID generator.
- **Not distributable**: Can't generate IDs on multiple machines without coordination.
- **Information leakage**: ID #1001 reveals ~1000 orders exist.
- **Performance bottleneck**: Central counter limits write throughput.

### Distributed Auto-Increment Patterns
```
Pattern 1: Odd/Even (2 servers)
  Server A generates: 1, 3, 5, 7, 9, ...
  Server B generates: 2, 4, 6, 8, 10, ...
  
Pattern 2: Stride (N servers)
  Server 1 generates: 1, N+1, 2N+1, ...
  Server 2 generates: 2, N+2, 2N+2, ...
  Server K generates: K, N+K, 2N+K, ...
  
Problem: Adding a new server requires changing the stride → messy.
```

---

## Counter-Based with Base62 Encoding

### For URL Shorteners
```
Counter: 1, 2, 3, ... (auto-incrementing)
Base62 encoding: a-z (26) + A-Z (26) + 0-9 (10) = 62 characters

ID 1 → "b" (Base62)
ID 62 → "ba"
ID 3844 → "baa"
ID 3,521,614,606,208 → "aaaaaaa" (7 chars)

7-character Base62: 62^7 = 3.52 trillion unique codes
```

### Architecture
```
┌──────────────┐     ┌──────────────┐
│ Ticket Server│     │ Ticket Server│
│ (odd: 1,3,5) │     │ (even: 2,4,6)│
└──────┬───────┘     └──────┬───────┘
       │                    │
       └──── Application ───┘
             │
             ▼
        Base62 encode
        ID 12345 → "dnh"
        Short URL: bit.ly/dnh
```

### Interview Script
> *"For the URL shortener, I'd use a counter-based approach with Base62 encoding. An auto-incrementing counter generates IDs 1, 2, 3... which I encode as 'a', 'b', 'c'... up to 'a9' (= 62), 'aa' (= 63), etc. A 7-character Base62 string can represent 62^7 = 3.5 trillion unique URLs — enough for decades. The advantage over hash-based approaches: no collisions, predictable short URL length, and sequential IDs are cache-friendly."*

---

## ULID — Sortable UUID Alternative

### Format
```
ULID: 01ARZ3NDEKTSV4RRFFQ69G5FAV

 01ARZ3NDEK       TSV4RRFFQ69G5FAV
 └─────┬────┘     └──────┬────────┘
   Timestamp           Randomness
   (48 bits)           (80 bits)
   
Total: 128 bits (same as UUID)
Encoding: Crockford's Base32 (26 characters)
```

### Advantages over UUID v4
- **Lexicographically sortable**: Timestamp prefix → chronological ordering.
- **Better B-tree locality**: Sequential timestamps → appending inserts.
- **Same size**: 128 bits, compatible with UUID columns.
- **Monotonic**: IDs generated in the same millisecond are still ordered (random part increments).

### Disadvantages
- **128 bits**: Still 2x the size of Snowflake's 64 bits.
- **Less standard**: Fewer native database/library support than UUID.

### When to Choose ULID over Snowflake
- You need UUID-compatible column types (128-bit).
- You don't need machine ID tracking.
- You want sortability without the complexity of Snowflake's bit layout.

---

## Twitter Snowflake Bit Layout

### Original Twitter Implementation
```
Epoch: Twitter epoch (November 4, 2010)

ID = (timestamp - twitter_epoch) << 22 | (datacenter_id << 17) | (worker_id << 12) | sequence

Breakdown:
  41 bits: Milliseconds since Twitter epoch (~69 years)
  5 bits:  Datacenter ID (0-31 = 32 datacenters)
  5 bits:  Worker ID (0-31 = 32 workers per datacenter)
  12 bits: Sequence (0-4095 per millisecond)
```

### Extracting Timestamp from ID
```python
def extract_timestamp(snowflake_id):
    timestamp_ms = (snowflake_id >> 22) + TWITTER_EPOCH
    return datetime.fromtimestamp(timestamp_ms / 1000)

# Example: Tweet ID 1765281934182400001
# → Created at 2024-03-15 14:30:00 UTC
```

### Custom Snowflake Variations
Instagram, Discord, and others use modified bit layouts:
```
Instagram:
  41 bits: timestamp
  13 bits: logical shard ID (0-8191)
  10 bits: sequence (0-1023)
  
Discord:
  42 bits: timestamp (custom epoch: first second of 2015)
  5 bits:  internal worker ID
  5 bits:  internal process ID
  12 bits: increment
```

---

## Ticket Server Pattern

### Flickr's Approach
```
Two MySQL servers with auto-increment:
  Server 1: auto_increment_offset = 1, auto_increment_increment = 2
    → Generates: 1, 3, 5, 7, 9, ...
  
  Server 2: auto_increment_offset = 2, auto_increment_increment = 2
    → Generates: 2, 4, 6, 8, 10, ...

Application round-robins between servers.
If one server dies, the other continues (IDs just have gaps).
```

### Advantages
- Simple.
- 64-bit sequential IDs.
- Two servers for high availability.

### Disadvantages
- Still a centralized service (two points of failure).
- Single digit million IDs/sec max.
- Tight coupling to MySQL.

---

## Database-Specific ID Strategies

| Database | Default ID Strategy | Notes |
|---|---|---|
| **PostgreSQL** | SERIAL / BIGSERIAL | Auto-increment, 32-bit or 64-bit |
| **MySQL** | AUTO_INCREMENT | Single-sequence, per-table |
| **Cassandra** | TIMEUUID | UUID v1 with timestamp — time-sortable |
| **DynamoDB** | Application-generated | No built-in auto-increment |
| **MongoDB** | ObjectId | 12 bytes: timestamp + machine + PID + counter |
| **Redis** | INCR | Atomic counter, ~100K increments/sec |

### Cassandra TIMEUUID
```sql
CREATE TABLE messages (
    conversation_id UUID,
    message_id TIMEUUID,  -- Time-based UUID (sortable)
    content TEXT,
    PRIMARY KEY (conversation_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

### MongoDB ObjectId
```
ObjectId: 507f1f77bcf86cd799439011

┌──────────┬────────┬──────┬──────────┐
│4 bytes   │3 bytes │2 bytes│3 bytes  │
│timestamp │machine │PID   │counter   │
│(seconds) │hash    │      │(random)  │
└──────────┴────────┴──────┴──────────┘

Time-sortable, unique across machines, 12 bytes (96 bits).
```

---

## ID Generation at Scale

### Requirements at Twitter Scale
```
500K tweets/minute = ~8,300 tweets/second
+ DMs, likes, retweets, follows = ~100K ID generations/second
Across 1000+ application servers

Solution: Snowflake
  - Each server has a unique 10-bit machine ID
  - Generates IDs independently (no coordination)
  - 4,096 IDs/ms per server = 4M IDs/sec per server
  - Total capacity: 4 billion IDs/sec (massive headroom)
```

### Requirements at Instagram Scale
```
100M+ photos/day = ~1,200 photos/second
+ comments, likes, stories = ~50K ID generations/second
Across multiple database shards

Solution: Modified Snowflake
  - 41-bit timestamp + 13-bit shard ID + 10-bit sequence
  - Each PostgreSQL shard generates IDs using a PL/pgSQL function
  - IDs embed the shard ID → no cross-shard lookups needed
```

---

## ID Properties Comparison

| Strategy | Size | Sortable | Coordination-Free | Collisions | B-Tree Friendly |
|---|---|---|---|---|---|
| **UUID v4** | 128 bits | ❌ | ✅ | Negligible | ❌ (random scatter) |
| **Snowflake** | 64 bits | ✅ | ✅ | None (by design) | ✅ (append-only) |
| **Auto-Increment** | 64 bits | ✅ | ❌ (central) | None | ✅ (sequential) |
| **ULID** | 128 bits | ✅ | ✅ | Negligible | ✅ (timestamp prefix) |
| **Base62 Counter** | Variable | ❌ | ❌ (central) | None | N/A (key-value) |
| **MongoDB ObjectId** | 96 bits | ✅ | ✅ | Negligible | ✅ (timestamp prefix) |
| **Cassandra TIMEUUID** | 128 bits | ✅ | ✅ | None (UUID v1) | ✅ |

---

## B-Tree Index Impact

```
10 billion rows in a table:

UUID v4 (128-bit, random):
  Index size: 10B × 16 bytes = 160 GB
  Insert pattern: Random → scattered page splits
  Write amplification: ~5-10x due to random I/O
  
Snowflake (64-bit, time-sorted):
  Index size: 10B × 8 bytes = 80 GB (50% smaller)
  Insert pattern: Append-only → no page splits
  Write amplification: ~1x (sequential)
  
Impact: Snowflake IDs give 50% less index storage + 5-10x less write amplification.
At 10B rows, this is the difference between needing 5 vs 25 IOPS-optimized SSDs.
```

---

## Security Considerations

### Information Leakage
```
Auto-increment ID #10042:
  → "This company has ~10,000 orders" (business intelligence)
  → Competitors can estimate volume by creating orders periodically

Snowflake ID 1765281934182400001:
  → Timestamp extractable: created at 2024-03-15 14:30:00
  → Machine ID extractable: which server processed it

UUID v4: 550e8400-e29b-41d4-a716-446655440000
  → No information leakage (fully random)
```

### Mitigations
- **Public-facing IDs**: Use UUID v4 or hash of the internal ID.
- **Internal IDs**: Use Snowflake for performance.
- **URL shortener**: Random Base62 code (not sequential counter).
- **API resources**: Use slugs or opaque tokens, not sequential IDs.

---

## Interview Talking Points & Scripts

### Snowflake for Most Systems
> *"I'd use Snowflake IDs because we need time-sortability, compactness (64-bit), and coordination-free generation across 1024 servers. The 41-bit timestamp gives 69 years, and the 12-bit sequence allows 4096 IDs per millisecond per machine."*

### UUID When Coordination-Free Is Paramount
> *"For distributed trace IDs that span across company boundaries, UUID v4 is ideal — zero coordination, universally unique, standard format. The 128-bit size and poor B-tree locality don't matter because trace IDs aren't stored in indexed database tables."*

### Counter for URL Shortener
> *"For the URL shortener, counter-based with Base62 encoding gives us short, predictable URLs. 7 characters = 3.5 trillion unique URLs. No collisions, no hash conflicts, sequential IDs are cache-friendly."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Always choosing UUID without considering tradeoffs
**Problem**: UUID's 128 bits and random B-tree distribution are expensive at scale.

### ❌ Mistake 2: Not mentioning time-sortability
**Problem**: For feeds and timelines, sortable IDs eliminate the need for a separate timestamp index.

### ❌ Mistake 3: Not addressing clock skew for Snowflake
**Problem**: If the system clock goes backwards, Snowflake can generate duplicate IDs. Must mention NTP and the clock-backwards handling strategy.

### ❌ Mistake 4: Using auto-increment in distributed systems
**Problem**: Auto-increment requires a central counter → bottleneck and single point of failure.

### ❌ Mistake 5: Not considering information leakage
**Problem**: Sequential IDs in public APIs reveal business volume. Use opaque IDs externally.

---

## ID Strategy by System Design Problem

| Problem | Strategy | Why |
|---|---|---|
| **Twitter tweets** | Snowflake | Time-sortable, 64-bit, coordinationless |
| **Chat messages** | TIMEUUID (Cassandra) | Time-sortable within conversation partition |
| **URL shortener** | Counter + Base62 | Short, predictable, no collisions |
| **Distributed traces** | UUID v4 | Zero coordination across companies |
| **E-commerce orders** | Snowflake | Sortable, embeds timestamp |
| **Session tokens** | UUID v4 | Random, no information leakage |
| **Idempotency keys** | Client-generated UUID v4 | Client controls, no coordination |
| **Database PKs (SQL)** | BIGSERIAL or Snowflake | Sequential for B-tree performance |
| **Database PKs (Cassandra)** | TIMEUUID | Native support, time-sortable |
| **Public API resource IDs** | UUID v4 or hashed Snowflake | No information leakage |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               ID GENERATION CHEAT SHEET                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  DEFAULT: Snowflake ID (64-bit, sortable, coordinationless)  │
│                                                              │
│  STRATEGIES:                                                 │
│  Snowflake:      64-bit, time-sorted, 4M IDs/sec/machine    │
│  UUID v4:        128-bit, random, zero coordination          │
│  ULID:           128-bit, time-sorted, UUID-compatible       │
│  Auto-increment: 64-bit, sequential, centralized             │
│  Counter+Base62: Variable, short URLs, centralized           │
│                                                              │
│  CHOOSE BY:                                                  │
│  Need sorted + compact → Snowflake                           │
│  Need zero coordination + standard → UUID v4                 │
│  Need sorted + UUID-compatible → ULID                        │
│  Need short URL codes → Counter + Base62                     │
│  Simple single-DB system → Auto-increment                    │
│                                                              │
│  B-TREE IMPACT:                                              │
│  Sequential IDs → append-only inserts → excellent perf       │
│  Random IDs (UUID) → scattered inserts → 5-10x worse        │
│                                                              │
│  SNOWFLAKE LAYOUT:                                           │
│  [1 unused][41 timestamp][10 machine][12 sequence] = 64 bits│
│  69 years × 1024 machines × 4096 IDs/ms                     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 2: Database Selection** — ID strategy depends on database type
- **Topic 12: Sharding** — Shard key often derived from or related to ID
- **Topic 20: Deduplication** — Idempotency keys as IDs
- **Topic 51: URL Shortener** — Counter + Base62 pattern

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
