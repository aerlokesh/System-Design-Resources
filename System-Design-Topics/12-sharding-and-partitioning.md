# 🎯 Topic 12: Sharding & Partitioning

> **System Design Interview — Deep Dive**
> A comprehensive guide covering horizontal partitioning (sharding), shard key selection, consistent hashing, range vs hash partitioning, cross-shard queries, resharding strategies, and hot shard mitigation.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Shard — The Scaling Ceiling](#why-shard--the-scaling-ceiling)
3. [Horizontal vs Vertical Partitioning](#horizontal-vs-vertical-partitioning)
4. [Shard Key Selection — The Most Critical Decision](#shard-key-selection--the-most-critical-decision)
5. [Hash-Based Partitioning](#hash-based-partitioning)
6. [Range-Based Partitioning](#range-based-partitioning)
7. [Consistent Hashing](#consistent-hashing)
8. [Consistent Hashing with Virtual Nodes](#consistent-hashing-with-virtual-nodes)
9. [Cross-Shard Queries — The Pain Point](#cross-shard-queries--the-pain-point)
10. [Denormalization to Avoid Cross-Shard JOINs](#denormalization-to-avoid-cross-shard-joins)
11. [Hot Shards and Mitigation](#hot-shards-and-mitigation)
12. [Resharding — Adding More Shards](#resharding--adding-more-shards)
13. [Shard-Nothing Architecture](#shard-nothing-architecture)
14. [Application-Level vs Database-Level Sharding](#application-level-vs-database-level-sharding)
15. [Sharding in Popular Databases](#sharding-in-popular-databases)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Sharding Strategy by System Design Problem](#sharding-strategy-by-system-design-problem)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Sharding** (horizontal partitioning) splits a dataset across multiple database instances (shards), each holding a subset of the data. Every row exists on exactly one shard.

```
Before sharding (single database):
┌──────────────────────────────┐
│    PostgreSQL (1 instance)    │
│    100M users, 10 TB data    │
│    50K writes/sec (at limit) │
└──────────────────────────────┘

After sharding (4 shards):
┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐
│  Shard 0  │ │  Shard 1  │ │  Shard 2  │ │  Shard 3  │
│  25M users│ │  25M users│ │  25M users│ │  25M users│
│  2.5 TB   │ │  2.5 TB   │ │  2.5 TB   │ │  2.5 TB   │
└───────────┘ └───────────┘ └───────────┘ └───────────┘
Total capacity: 200K writes/sec (4x), 10 TB distributed
```

---

## Why Shard — The Scaling Ceiling

### When Single-Instance Hits Its Limit
```
PostgreSQL single instance:
  Max practical storage: ~5-10 TB (before performance degrades)
  Max write throughput: ~50K writes/sec
  Max connections: ~500 (with PgBouncer: ~5000)
  
When you exceed ANY of these:
  Storage > 10 TB → queries slow due to large index scans
  Writes > 50K/sec → replication lag grows, locks contend
  Connections > 5000 → connection pool exhaustion
  
Solution: Distribute data across N instances
```

### Scaling Path
```
Stage 1: Single instance (handles 10K QPS)
Stage 2: Read replicas (handles 50K read QPS, still 10K write QPS)
Stage 3: Caching (handles 200K read QPS, still 10K write QPS)
Stage 4: Sharding (handles N × 10K write QPS)  ← THIS TOPIC
```

---

## Horizontal vs Vertical Partitioning

### Horizontal Partitioning (Sharding)
Split rows across databases. Each shard has the same schema but different data.
```
Shard 0: users with ID 0-24,999,999
Shard 1: users with ID 25,000,000-49,999,999
Shard 2: users with ID 50,000,000-74,999,999
Shard 3: users with ID 75,000,000-99,999,999
```

### Vertical Partitioning
Split columns across databases. Each partition has different columns.
```
User core DB:     user_id, name, email (accessed every request)
User profile DB:  user_id, bio, avatar_url, settings (accessed occasionally)
User analytics DB: user_id, login_count, last_active (accessed rarely)
```

### When to Use Each
- **Horizontal**: When you have too many rows (scale writes and storage).
- **Vertical**: When you have too many columns or different access patterns per column group.
- **Most common in interviews**: Horizontal sharding.

---

## Shard Key Selection — The Most Critical Decision

### The Three Criteria

**1. Even Distribution**: Data should be evenly distributed across shards (no hot shards).

**2. Query Locality**: 90%+ of queries should hit a single shard (no scatter-gather).

**3. Growth Tolerance**: The shard key shouldn't require resharding at 10x scale.

### Why It's Irreversible
Changing the shard key means **migrating every row** in the database. For a billion-row table, that's weeks of migration, requiring dual-write, shadow-read, and cutover — a major engineering effort.

### Good Shard Keys by System

| System | Shard Key | Why |
|---|---|---|
| **Chat messages** | conversation_id | Messages always queried by conversation |
| **E-commerce orders** | user_id | Orders always queried by user |
| **Ticket booking** | event_id | All seats/bookings for one event are co-located |
| **Social feed** | user_id | Timeline always queried by user |
| **Multi-tenant SaaS** | tenant_id | Tenants are isolated, queries always scoped |
| **Time-series metrics** | metric_id + time_bucket | Range scans within one metric |

### Bad Shard Keys

| Shard Key | Problem |
|---|---|
| **created_at** | All recent writes go to the same shard (hot shard) |
| **country** | Uneven: US shard has 10x more data than Luxembourg shard |
| **first_letter_of_name** | Wildly uneven distribution (more names start with 'S' than 'X') |
| **random** | No query locality — every query is scatter-gather |

### Interview Script
> *"The shard key is the single most important design decision in any sharded system, because it's effectively irreversible — changing it requires migrating every row. My criteria: (1) even distribution (no hot shards), (2) query locality (90%+ of queries hit a single shard), (3) growth tolerance (doesn't need resharding at 10x scale). For a chat system, conversation_id is perfect — messages are always queried by conversation, conversations are roughly equal in size, and we can add shards by using consistent hashing without migrating existing conversations."*

---

## Hash-Based Partitioning

### How It Works
```
shard_id = hash(shard_key) % num_shards

Example (4 shards):
  user_id = 123 → hash(123) = 7829 → 7829 % 4 = 1 → Shard 1
  user_id = 456 → hash(456) = 2341 → 2341 % 4 = 1 → Shard 1
  user_id = 789 → hash(789) = 5672 → 5672 % 4 = 0 → Shard 0
```

### Advantages
- **Even distribution**: Good hash function distributes data uniformly.
- **Simple**: One hash + modulo operation.
- **Works for any key type**: Strings, integers, UUIDs.

### Disadvantages
- **No range queries**: Can't efficiently query "all users created between Jan-Mar" (data scattered).
- **Resharding nightmare**: Adding a 5th shard changes `% 4` to `% 5`, remapping ~80% of keys to different shards.

### The Resharding Problem
```
With 4 shards: hash(key) % 4
Add 5th shard: hash(key) % 5

Key with hash 7829:
  Old: 7829 % 4 = 1 (Shard 1)
  New: 7829 % 5 = 4 (Shard 4!) → must migrate

~80% of keys get remapped = massive data migration
```

This is why we need **consistent hashing**.

---

## Range-Based Partitioning

### How It Works
```
Shard 0: user_id 0 - 24,999,999
Shard 1: user_id 25,000,000 - 49,999,999
Shard 2: user_id 50,000,000 - 74,999,999
Shard 3: user_id 75,000,000 - 99,999,999
```

### Advantages
- **Efficient range queries**: "Get all users with ID 1000-2000" hits one shard.
- **Simple to understand**: Clear boundaries.
- **Easy to split**: Shard 0 gets too big? Split into 0a (0-12.5M) and 0b (12.5M-25M).

### Disadvantages
- **Hot shards**: If new users get sequential IDs, the last shard receives all writes.
- **Uneven distribution**: Some ranges may have more data than others.
- **Manual rebalancing**: Need to adjust boundaries as data grows.

### When to Use
- **Time-series data**: Partition by time range (one shard per month).
- **Geographic data**: Partition by region.
- **Alphabetical**: Partition by name range (A-F, G-L, M-R, S-Z).

---

## Consistent Hashing

### The Problem It Solves
With modular hashing (`hash(key) % N`), adding or removing a node remaps ~90% of keys. Consistent hashing reduces this to ~1/N of keys.

### How It Works
```
1. Hash both keys AND nodes onto a circular ring (0 to 2^32)
2. Each key is stored on the first node clockwise from its position

Ring:
     Node A (pos 1000)
    /                \
   /                  \
  Node D (pos 8000)    Node B (pos 3500)
   \                  /
    \                /
     Node C (pos 6000)

Key with hash 2000 → first node clockwise = Node B (at 3500)
Key with hash 4000 → first node clockwise = Node C (at 6000)
Key with hash 7000 → first node clockwise = Node D (at 8000)
Key with hash 9000 → first node clockwise = Node A (at 1000, wraps around)
```

### Adding a Node
```
Add Node E at position 5000:

Before: Keys 3501-6000 → Node C
After:  Keys 3501-5000 → Node E (NEW)
        Keys 5001-6000 → Node C (unchanged)

Only keys between Node B and Node E are remapped.
All other keys are unaffected!
Remapped: ~1/N of keys (where N = number of nodes)
```

### Interview Script
> *"Consistent hashing is essential for our Redis cache layer. With modular hashing (`hash(key) % 10 nodes`), adding an 11th node remaps ~90% of keys — a catastrophic cold-cache event where the database suddenly gets 10x its normal load. With consistent hashing, adding the 11th node only remaps ~9% of keys (roughly 1/11). Combined with 150 virtual nodes per physical server for even distribution, we can scale the cache tier without ever causing a thundering herd on the database."*

---

## Consistent Hashing with Virtual Nodes

### The Problem with Basic Consistent Hashing
With few physical nodes, the distribution is uneven:
```
Node A: responsible for 40% of the ring
Node B: responsible for 15% of the ring
Node C: responsible for 45% of the ring

Node C has 3x more data than Node B → imbalanced!
```

### Virtual Nodes Solution
Each physical node maps to many virtual positions on the ring:
```
Physical Node A → Virtual: A1(pos 500), A2(pos 2300), A3(pos 4100), ... A150
Physical Node B → Virtual: B1(pos 800), B2(pos 1900), B3(pos 3700), ... B150
Physical Node C → Virtual: C1(pos 1200), C2(pos 2800), C3(pos 5500), ... C150

With 150 virtual nodes per physical node:
Each physical node handles ~33.3% ± 2% of keys
(nearly perfectly balanced)
```

### Standard Configuration
- **Redis Cluster**: 16,384 hash slots, distributed across nodes.
- **Cassandra**: 256 virtual nodes per physical node (default).
- **DynamoDB**: Managed partitioning (transparent to user).

---

## Cross-Shard Queries — The Pain Point

### The Problem
```
Query: "Get all orders for user_123 with their product details"

If orders are sharded by user_id:
  → user_123's orders are on Shard 2 (single shard, fast)

If products are sharded by product_id:
  → Product details are scattered across ALL shards
  → Need to query every product shard → scatter-gather → SLOW

Cross-shard JOIN:
  Shard 2 (orders) JOIN All Shards (products)
  = 1 + N queries instead of 1 query
```

### Why Cross-Shard JOINs Are Terrible
1. **Latency**: N parallel queries (one per shard) → latency = max(all shard latencies).
2. **Network**: N round-trips instead of 1.
3. **No transactions**: Can't ACID across shards without 2PC (expensive, fragile).
4. **Complexity**: Application must coordinate, merge, and sort results.

### Solutions
1. **Co-locate related data**: Shard orders and order_items by the same key (user_id).
2. **Denormalize**: Embed product name in the order record.
3. **Application-level JOIN**: Fetch orders from one shard, then batch-fetch product details.
4. **Avoid cross-shard queries in the design**: Structure the system so they're never needed.

---

## Denormalization to Avoid Cross-Shard JOINs

### The Pattern
```
Before (normalized, requires cross-shard JOIN):
  Orders shard: { order_id, user_id, product_id, quantity }
  Products shard: { product_id, name, price, image_url }
  
  Displaying an order requires: orders shard JOIN products shard

After (denormalized, single-shard query):
  Orders shard: { order_id, user_id, product_id, product_name, 
                   product_price, quantity }
  
  Displaying an order: single query on orders shard
```

### The Tradeoff
```
Denormalized:
  ✅ Read: Single-shard, fast
  ❌ Write: When product name changes, update all orders containing that product
  
Normalized:
  ✅ Write: Update product name in one place
  ❌ Read: Cross-shard JOIN, slow
```

### Interview Script
> *"I'd avoid cross-shard JOINs entirely by denormalizing. Instead of storing the user's name only in the Users shard and JOINing it when displaying an order, I'd store a copy of the user's name directly in the Orders shard. When a user changes their name (rare — maybe once every 5 years), a background job updates all their orders. When displaying an order (frequent — millions of times per day), it's a single-shard read. I'm trading write-time complexity on a rare operation for read-time simplicity on an extremely frequent operation."*

---

## Hot Shards and Mitigation

### Causes
1. **Celebrity effect**: All followers of @BarackObama are on one shard.
2. **Temporal hot spot**: Today's data all goes to the latest time-range shard.
3. **Popular item**: One product gets 90% of all traffic.
4. **Uneven key distribution**: Some shard keys map to more data.

### Mitigation Strategies

**Strategy 1: Key Salting**
```
Instead of: shard = hash(user_id) % N
Use:        shard = hash(user_id + random(0,9)) % N

Spreads one user's data across 10 shards.
Cost: Reads must query 10 shards and merge results.
Use for: Extremely hot keys only (e.g., celebrity timelines).
```

**Strategy 2: Dedicated Shard for Hot Keys**
```
If (key is known hot key):
    route to dedicated shard (bigger, more resources)
Else:
    route via consistent hashing
```

**Strategy 3: Caching**
```
Cache the hot key in Redis (absorbs 99% of reads).
Only 1% of traffic reaches the shard.
```

**Strategy 4: Split the Hot Shard**
```
Shard 3 is too hot → split into Shard 3a and Shard 3b.
Reassign half the keys from Shard 3 to the new shard.
```

---

## Resharding — Adding More Shards

### The Challenge
Adding shards means some data must move from existing shards to new shards.

### Online Resharding Process
```
Phase 1: Add new shard (empty)
Phase 2: Start dual-writing (new data goes to both old and new shard)
Phase 3: Backfill: Migrate affected keys from old shard to new shard
Phase 4: Verify: Compare data between old and new routing
Phase 5: Switch reads to new shard routing
Phase 6: Stop dual-writing to old locations
Phase 7: Clean up old data from original shards
```

### With Consistent Hashing
```
Adding a shard remaps only ~1/N of keys.
For 10 shards → 11 shards: Only ~9% of data migrates.
Much better than modular hashing (80% migration).
```

---

## Shard-Nothing Architecture

### Concept
Each shard is completely independent — no shared state, no shared disk, no coordination.

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Shard 0  │  │ Shard 1  │  │ Shard 2  │
│ App + DB │  │ App + DB │  │ App + DB │
│(independent)│(independent)│(independent)│
└──────────┘  └──────────┘  └──────────┘

No cross-shard communication.
Each shard can fail independently.
Scale by adding more shards.
```

### Benefits
- **Linear scaling**: N shards = N× throughput.
- **Fault isolation**: One shard failing doesn't affect others.
- **Independent deployment**: Upgrade one shard at a time.
- **Simple operations**: Each shard is a standalone system.

### Examples
- **Cassandra**: Each node is independent, data distributed via consistent hashing.
- **Slack**: One MySQL cluster per workspace (each workspace is a shard).
- **Discord**: One Cassandra cluster per server cluster (groups of servers).

---

## Application-Level vs Database-Level Sharding

### Application-Level Sharding
```python
def get_shard(user_id):
    shard_id = hash(user_id) % NUM_SHARDS
    return shard_connections[shard_id]

def get_user(user_id):
    shard = get_shard(user_id)
    return shard.query("SELECT * FROM users WHERE id = ?", user_id)
```

**Pros**: Full control, works with any database.
**Cons**: Routing logic in application code, every query must be shard-aware.

### Database-Level Sharding
```
Managed by the database engine:
- Cassandra: Automatic via partition key
- MongoDB: Automatic via shard key
- Vitess: MySQL sharding middleware
- Citus: PostgreSQL sharding extension
```

**Pros**: Transparent to application (mostly), managed rebalancing.
**Cons**: Less control, vendor lock-in, may not optimize for your specific patterns.

---

## Sharding in Popular Databases

| Database | Sharding Approach | Key Concept |
|---|---|---|
| **Cassandra** | Automatic consistent hashing | Partition key determines node |
| **MongoDB** | Hash or range sharding | Shard key defined per collection |
| **DynamoDB** | Automatic | Partition key, managed splits |
| **Vitess** | MySQL sharding middleware | Manages MySQL clusters as shards |
| **Citus** | PostgreSQL extension | Distributed tables with shard key |
| **CockroachDB** | Automatic range partitioning | Ranges split and rebalance automatically |
| **Redis Cluster** | 16,384 hash slots | Slots distributed across nodes |

---

## Interview Talking Points & Scripts

### Shard Key Decision
> *"The shard key is the most important design decision in a sharded system because it's effectively irreversible. For a ticket booking system, I'd shard by event_id — all seats, reservations, and tickets for one event live on the same shard, enabling atomic single-shard transactions."*

### Consistent Hashing
> *"Consistent hashing is essential for our cache layer. Adding an 11th node to a 10-node cluster only remaps ~9% of keys, compared to ~90% with modular hashing. Combined with 150 virtual nodes per physical server, the distribution is nearly perfectly balanced."*

### Cross-Shard Avoidance
> *"I'd avoid cross-shard JOINs entirely by denormalizing. The one-time write cost of duplicating data is trivial compared to the per-request cost of cross-shard queries on every read."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not explaining why you're sharding
**Problem**: Jumping to sharding without establishing that the single-instance limit has been reached.

### ❌ Mistake 2: Choosing a bad shard key
**Problem**: Sharding by created_at (hot shard for recent data) or by country (uneven distribution).

### ❌ Mistake 3: Ignoring cross-shard query implications
**Problem**: Designing a system that requires cross-shard JOINs → defeats the purpose of sharding.

### ❌ Mistake 4: Using modular hashing instead of consistent hashing
**Problem**: `hash(key) % N` makes resharding catastrophic. Always mention consistent hashing.

### ❌ Mistake 5: Not mentioning resharding strategy
**Problem**: If the interviewer asks "what happens when you need more shards?" you need an answer.

---

## Sharding Strategy by System Design Problem

| Problem | Shard Key | Why | Cross-Shard Consideration |
|---|---|---|---|
| **Twitter** | user_id | Timelines queried by user | Search uses separate Elasticsearch |
| **WhatsApp** | conversation_id | Messages always by conversation | User's conversation list: separate index |
| **Ticket Booking** | event_id | Booking transaction is per-event | User's bookings: denormalized copy |
| **E-Commerce** | user_id | Orders always queried by user | Product catalog: separate service |
| **Multi-tenant SaaS** | tenant_id | Complete data isolation | Cross-tenant queries: admin service |
| **Time-Series Metrics** | metric_id | Range scans within one metric | Cross-metric: scatter-gather |
| **URL Shortener** | short_code hash | Even distribution, key-value lookup | No cross-shard queries needed |
| **Chat (Slack)** | workspace_id | Each workspace is independent | Cross-workspace: not needed |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│           SHARDING & PARTITIONING CHEAT SHEET                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  WHEN TO SHARD:                                              │
│    Storage > 10 TB  OR  Writes > 50K/sec  OR  Data growth   │
│    unbounded on single instance                              │
│                                                              │
│  SHARD KEY CRITERIA:                                         │
│    1. Even distribution (no hot shards)                      │
│    2. Query locality (90%+ single-shard queries)             │
│    3. Growth tolerance (works at 10x scale)                  │
│    ⚠️ IRREVERSIBLE — choose carefully                        │
│                                                              │
│  PARTITIONING TYPES:                                         │
│    Hash: Even distribution, no range queries                 │
│    Range: Range queries, risk of hot shards                  │
│    Consistent Hashing: Add/remove nodes with minimal impact  │
│                                                              │
│  CONSISTENT HASHING:                                         │
│    Add 1 node to N nodes → only ~1/N keys remapped          │
│    Virtual nodes (150/server) for even distribution          │
│                                                              │
│  CROSS-SHARD QUERIES:                                        │
│    AVOID by: co-locating related data on same shard          │
│    AVOID by: denormalization (embed, don't JOIN)             │
│    If unavoidable: scatter-gather (slow, last resort)        │
│                                                              │
│  HOT SHARD MITIGATION:                                       │
│    Key salting, dedicated shard, caching, shard splitting    │
│                                                              │
│  RESHARDING:                                                 │
│    Consistent hashing: ~1/N data migration                   │
│    Modular hashing: ~90% data migration (AVOID)              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 2: Database Selection** — Database choice affects sharding options
- **Topic 5: Caching** — Cache layer absorbs hot shard reads
- **Topic 11: ID Generation** — IDs may encode shard information
- **Topic 13: Replication** — Each shard can have replicas
- **Topic 31: Hot Partitions** — Dealing with hot keys in sharded systems

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
