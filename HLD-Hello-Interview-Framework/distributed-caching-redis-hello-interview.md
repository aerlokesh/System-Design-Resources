# Design a Distributed Caching Solution (Azure Cache for Redis) — Hello Interview Framework

> **Question**: Design a distributed in-memory cache (like Azure Cache for Redis or Amazon ElastiCache) that can be used by various services to improve read performance, reduce database load, and provide sub-millisecond data access at scale.
>
> **Asked at**: Microsoft, Amazon, Google, Redis Labs
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Key-Value Operations**: GET, SET, DEL with sub-millisecond latency. Support TTL (time-to-live) per key. Atomic operations (INCR, DECR, CAS).
2. **Data Structures**: Strings, hashes, lists, sets, sorted sets. Each with rich command set.
3. **Distributed Clustering**: Shard data across multiple nodes. Auto-rebalance on node add/remove. Consistent hashing or hash slot assignment.
4. **Replication**: Primary-replica replication for high availability. Auto-failover when primary fails. Read replicas for read scaling.
5. **Eviction Policies**: LRU, LFU, random, volatile-ttl. Configurable max memory. Graceful eviction when memory full.
6. **Persistence (optional)**: Snapshot (RDB) for point-in-time backups. Append-only file (AOF) for durability. Hybrid persistence.
7. **Pub/Sub**: Publish/subscribe messaging through the cache. Channel-based and pattern-based subscriptions.

#### Nice to Have (P1)
- Lua scripting (server-side atomic operations)
- Transactions (MULTI/EXEC)
- Streams (append-only log data structure)
- Geospatial commands (GEOADD, GEORADIUS)
- Cluster-aware client with automatic routing
- Active-Active geo-replication
- Access control (ACLs, per-user permissions)
- TLS encryption in transit

#### Below the Line (Out of Scope)
- Full database capabilities (joins, complex queries)
- Client library implementation
- Monitoring dashboard UI

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Latency** | < 1ms p99 (single key, local) | In-memory speed |
| **Throughput** | 1M+ ops/sec per node | High performance |
| **Memory per node** | Up to 500 GB | Large working sets |
| **Cluster size** | Up to 1000 nodes | Massive scale |
| **Availability** | 99.999% with replicas | Cache miss → database overload |
| **Failover time** | < 15 seconds | Minimal disruption |
| **Data loss on failover** | < 1 second of writes | Async replication tradeoff |
| **Network bandwidth** | 10+ Gbps per node | High throughput workloads |

### Capacity Estimation

```
Cluster Example (large deployment):
  Nodes: 100 (50 primaries + 50 replicas)
  Memory per node: 100 GB
  Total memory: 5 TB (across primaries)
  Total with replicas: 10 TB
  
Operations:
  Total ops/sec: 50M (across cluster)
  Per node: 1M ops/sec
  Average value size: 500 bytes
  
Network:
  Per node: 1M × 500 bytes = 500 MB/s = 4 Gbps
  Total cluster: 200 Gbps aggregate
  
Key Space:
  Total keys: 10 billion (across cluster)
  Per shard: 200M keys
  Average key size: 50 bytes
  Key overhead per entry: ~100 bytes (pointers, metadata)
  Memory per shard: 200M × (50 + 500 + 100) = ~130 GB
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  CacheCluster    │────▶│  Shard (Primary)  │────▶│  CacheEntry        │
│                  │     │                   │     │                    │
│ clusterId        │     │ shardId           │     │ key                │
│ name             │     │ hashSlotRange     │     │ value (bytes)      │
│ nodeCount        │     │ nodeId            │     │ type (string/hash/ │
│ replicaCount     │     │ role (primary/    │     │  list/set/zset)    │
│ maxMemory        │     │  replica)         │     │ ttl                │
│ evictionPolicy   │     │ status            │     │ createdAt          │
│ version          │     │ memoryUsed        │     │ lastAccessed       │
│ endpoints[]      │     │ keyCount          │     │ encoding           │
│ persistence      │     │ replicas[]        │     │ serializedSize     │
└─────────────────┘     │ replicationOffset │     └───────────────────┘
                         └──────────────────┘
                         
┌─────────────────┐     ┌──────────────────┐
│  HashSlot        │     │  ClientConnection │
│                  │     │                   │
│ slotId (0-16383) │     │ connectionId     │
│ assignedShard    │     │ clientAddr       │
│ state (stable/   │     │ assignedShard    │
│  migrating/      │     │ lastCommand      │
│  importing)      │     │ database         │
└─────────────────┘     └──────────────────┘
```

---

## 3️⃣ API Design

### Basic Operations
```
// SET with TTL
SET user:123:session "eyJhbGci..." EX 3600

// GET
GET user:123:session
→ "eyJhbGci..."

// HSET (hash — store object fields)
HSET user:123:profile name "Alice" email "alice@contoso.com" plan "premium"
HGET user:123:profile name → "Alice"
HGETALL user:123:profile → { name: "Alice", email: "alice@contoso.com", plan: "premium" }

// Atomic increment (rate limiting)
INCR ratelimit:api:user_123:minute_2025011510
EXPIRE ratelimit:api:user_123:minute_2025011510 120
→ Returns current count (atomic, no race condition)

// Sorted Set (leaderboard)
ZADD leaderboard:game1 1450 "player_abc"
ZADD leaderboard:game1 1520 "player_def"
ZREVRANGE leaderboard:game1 0 9 WITHSCORES → top 10

// Pub/Sub
SUBSCRIBE channel:notifications:user_123
PUBLISH channel:notifications:user_123 '{"type":"newMessage","from":"Bob"}'

// Pipeline (batch commands, single round trip)
PIPELINE:
  GET user:123:profile
  GET user:123:session
  ZRANK leaderboard:game1 player_abc
EXECUTE
→ Returns all 3 results in one network round trip
```

---

## 4️⃣ Data Flow

### Cache-Aside Pattern (Most Common)

```
Application → needs data for user:123
        │
        ├── 1. GET user:123 from Redis
        │       │
        │       ├── HIT → return cached data (< 1ms) ✓
        │       │
        │       └── MISS → continue to step 2
        │
        ├── 2. Query primary database (Cosmos DB / SQL)
        │       → returns user data (5-50ms)
        │
        ├── 3. SET user:123 data EX 3600 in Redis
        │       → populate cache for future reads
        │
        └── 4. Return data to caller

Cache Invalidation:
  On write to database:
    Application updates user:123 in DB
    → DEL user:123 from Redis (invalidate)
    → Next read will populate fresh data from DB
  
  OR: Write-Through
    Application writes to Redis + DB simultaneously
    → Redis always has latest data
    → Higher write latency, simpler read path
```

### Write Flow Through Cluster

```
Client sends: SET user:123 "data"
        │
        ▼
   Client Library (cluster-aware):
     1. Compute hash slot: CRC16("user:123") % 16384 = 5421
     2. Lookup slot 5421 → mapped to Shard 3 (node: 10.0.0.5:6379)
     3. Send SET command to node 10.0.0.5
        │
        ▼
   Primary Node (Shard 3):
     1. Execute SET in memory (< 0.01ms)
     2. Return OK to client
     3. Async: replicate to replica node(s)
        Primary → Replica (async, < 1ms typically)
     4. If AOF enabled: append to AOF file (fsync policy: everysec)
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                                  │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ Web App  │  │ API      │  │ Worker   │  │ Session  │           │
│  │ Service  │  │ Gateway  │  │ Service  │  │ Service  │           │
│  │          │  │          │  │          │  │          │           │
│  │ (cache   │  │ (rate    │  │ (job     │  │ (session │           │
│  │  aside)  │  │  limit)  │  │  results)│  │  store)  │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
│       └──────────────┴──────────────┴──────────────┘                 │
│                                   │                                  │
│                      Cluster-Aware Client Library                   │
│                      (hash slot routing, pipelining,                │
│                       connection pooling, retry logic)              │
└───────────────────────────────────┬─────────────────────────────────┘
                                    │
┌───────────────────────────────────┼─────────────────────────────────┐
│              DISTRIBUTED CACHE CLUSTER                               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │              HASH SLOT MAP (16384 slots)                    │    │
│  │  Slots 0-5460    → Shard 1 (Primary: Node A)              │    │
│  │  Slots 5461-10922 → Shard 2 (Primary: Node C)              │    │
│  │  Slots 10923-16383 → Shard 3 (Primary: Node E)              │    │
│  └────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                     │
│  │ Shard 1  │    │ Shard 2  │    │ Shard 3  │                     │
│  │          │    │          │    │          │                     │
│  │ ┌──────┐ │    │ ┌──────┐ │    │ ┌──────┐ │                     │
│  │ │Node A│ │    │ │Node C│ │    │ │Node E│ │                     │
│  │ │PRIMARY│ │    │ │PRIMARY│ │    │ │PRIMARY│ │                     │
│  │ │100 GB │ │    │ │100 GB │ │    │ │100 GB │ │                     │
│  │ └───┬───┘ │    │ └───┬───┘ │    │ └───┬───┘ │                     │
│  │     │     │    │     │     │    │     │     │                     │
│  │ ┌───▼───┐ │    │ ┌───▼───┐ │    │ ┌───▼───┐ │                     │
│  │ │Node B│ │    │ │Node D│ │    │ │Node F│ │                     │
│  │ │REPLICA│ │    │ │REPLICA│ │    │ │REPLICA│ │                     │
│  │ │100 GB │ │    │ │100 GB │ │    │ │100 GB │ │                     │
│  │ └──────┘ │    │ └──────┘ │    │ └──────┘ │                     │
│  └──────────┘    └──────────┘    └──────────┘                     │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │  CLUSTER MANAGER (Sentinel / built-in cluster)              │    │
│  │                                                            │    │
│  │  - Monitor node health (heartbeat every 1s)                │    │
│  │  - Detect failures (3 missed heartbeats → suspected)       │    │
│  │  - Promote replica to primary on failure                   │    │
│  │  - Rebalance hash slots on add/remove node                │    │
│  │  - Cluster state propagation (gossip protocol)             │    │
│  └────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    PERSISTENCE LAYER (optional)                       │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐     │
│  │ RDB Snapshots│  │ AOF Log      │  │ Azure Blob Storage   │     │
│  │              │  │              │  │                      │     │
│  │ Point-in-time│  │ Every write  │  │ Backup snapshots     │     │
│  │ binary dump  │  │ appended     │  │ for disaster         │     │
│  │ Every 60 min │  │ fsync: 1/sec │  │ recovery             │     │
│  └──────────────┘  └──────────────┘  └──────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Data Partitioning — Hash Slots

**Problem**: With 5 TB of data, no single machine can hold everything. How to distribute data across nodes while maintaining O(1) routing?

**Solution: Hash Slot Assignment (Redis Cluster approach)**

```
Hash Slot Scheme:

Total hash slots: 16,384 (fixed, regardless of cluster size)

Key → Slot mapping:
  slot = CRC16(key) % 16384

Slot → Node mapping:
  Maintained in cluster state (gossip protocol):
  slots[0..5460] → Node A (Shard 1 primary)
  slots[5461..10922] → Node C (Shard 2 primary)
  slots[10923..16383] → Node E (Shard 3 primary)

Client Routing:
  1. Client maintains local copy of slot map
  2. For each command: compute slot, send to correct node
  3. If wrong node → node returns MOVED 5421 10.0.0.5:6379
     Client updates local map, resends to correct node
  4. Cluster map changes are rare → local map usually correct

Why 16384 slots (not more, not fewer)?
  - Small enough: slot map fits in ~2 KB (easy to gossip)
  - Large enough: good distribution even with 1000 nodes
  - 16384 / 100 nodes = ~164 slots per node (fine granularity)

Hash Tags (co-locate related keys):
  Keys: "user:123:profile", "user:123:session", "user:123:cart"
  Without hash tags: each may go to different shard
  With hash tags: use {user:123} as hash tag portion
    CRC16("user:123") → all 3 keys on same shard
  → Enables multi-key operations (MGET, transactions) on related data

Adding a Node:
  1. New node joins cluster (empty)
  2. Cluster manager initiates slot migration:
     Move 1/4 of slots to new node (if going from 3→4 nodes)
  3. Migration process per slot:
     a. Mark slot as "migrating" on source, "importing" on destination
     b. Source: for each key in slot → MIGRATE to destination
     c. During migration: source handles existing keys, redirects new keys
     d. All keys moved → update slot map → propagate via gossip
  4. Total rebalance time: minutes (transparent to clients)
```

### Deep Dive 2: Eviction Policies — What to Remove When Memory is Full

**Problem**: Node hits max memory (100 GB). New SET arrives. Which existing key should be evicted?

**Solution: Configurable Eviction Policy with Sampling**

```
Eviction Policies:

1. LRU (Least Recently Used) — allkeys-lru:
   Evict the key that was accessed longest ago.
   
   Implementation (approximated):
     Redis doesn't track exact LRU (too expensive for billions of keys)
     Instead: sample N random keys (default N=5), evict the oldest accessed
     
     Each key stores: last_access_timestamp (24 bits = 194 days precision)
     On GET/SET: update last_access_timestamp
     On eviction: sample 5 random keys → evict the one with oldest timestamp
   
   Pros: good general-purpose policy
   Cons: not perfect LRU (sampling approximation)

2. LFU (Least Frequently Used) — allkeys-lfu:
   Evict the key accessed least often.
   
   Implementation (logarithmic counter):
     Each key stores: frequency_counter (8 bits, max 255)
     Counter increments logarithmically:
       P(increment) = 1 / (counter × factor + 1)
       → High counts increment very slowly
     Counter decays over time (halves every N minutes)
   
   Pros: better for skewed access patterns (keep hot keys)
   Cons: new keys start cold → may be evicted before they prove valuable

3. volatile-ttl:
   Among keys with TTL set: evict the one expiring soonest.
   Keys without TTL are never evicted.
   
   Pros: predictable, respects application's TTL choices
   Cons: if no keys have TTL → no eviction → OOM error

4. noeviction:
   Don't evict anything. Return OOM error on write when full.
   Reads still work.
   
   Use case: cache that must not silently drop data

TTL-Based Expiration (separate from eviction):
  Active expiration: background thread samples 20 random TTL keys every 100ms
    If >25% are expired → repeat immediately (aggressive cleanup)
  Lazy expiration: on every access, check if key is expired
    If expired → delete and return nil (as if not found)
  
  Combination ensures expired keys are cleaned up promptly
  without consuming too much CPU on expiration checks.

Memory Reporting:
  INFO MEMORY:
    used_memory: 95.5 GB
    maxmemory: 100 GB
    evicted_keys: 1.2M (since start)
    mem_fragmentation_ratio: 1.08
    
  Alerting: trigger alarm at 90% memory utilization
```

### Deep Dive 3: Failover & High Availability

**Problem**: A primary node crashes. 200M keys become unavailable. How to fail over to a replica in <15 seconds with minimal data loss?

**Solution: Async Replication + Automatic Failover via Cluster Manager**

```
Replication Architecture:

Primary → Replica (async replication):
  1. Primary executes write command
  2. Primary returns OK to client (immediately)
  3. Primary sends write to replica (async, in replication stream)
  4. Replica applies write
  
  Replication lag: typically < 1ms, worst case < 1 second
  Data at risk on crash: up to 1 second of writes

Failover Sequence:
  T+0s:   Node A (primary, Shard 1) crashes
  T+1s:   Cluster nodes notice A missed heartbeat
  T+3s:   Multiple nodes mark A as "subjectively failed"
  T+5s:   Majority agrees → A marked "objectively failed" (FAIL state)
  T+6s:   Node B (replica of A) initiates failover:
           1. B stops replicating from A
           2. B promotes itself to primary
           3. B takes ownership of A's hash slots (0-5460)
           4. B broadcasts new cluster state via gossip
  T+8s:   All cluster nodes updated → route to B for slots 0-5460
  T+10s:  Clients receive MOVED redirections → update local maps
  T+12s:  Traffic flowing to B normally
  
  Total downtime: ~10-15 seconds
  Data loss: up to 1 second of writes (async replication gap)

Split-Brain Prevention:
  Scenario: network partition isolates A from the cluster
  A still thinks it's primary → accepts writes → diverge!
  
  Protection: 
    Primary checks: can I reach majority of cluster nodes?
    If isolated (can't reach majority) → stop accepting writes
    Timeout: NODE_TIMEOUT (default 15s)
    
    After partition heals:
      A discovers B is now primary → A becomes replica of B
      A's divergent writes are LOST (conflict resolution: new primary wins)

Read Replicas for Scale:
  Read-heavy workloads: route GET commands to replicas
  READONLY mode: client connects to replica, sends reads
  Benefit: 2x read throughput (primary + replica)
  Caveat: replica may serve slightly stale data (async lag)
  
  For strong consistency: always read from primary
  For eventual consistency acceptable: read from nearest replica

Persistence Recovery:
  If both primary AND replica fail:
    1. Restart node
    2. Load from RDB snapshot (point-in-time backup)
    3. Replay AOF log (if enabled) → recover to near-crash state
    4. Data loss: depends on RDB frequency + AOF fsync policy
       - AOF always: < 1 second loss
       - AOF everysec: < 1 second loss  
       - RDB only (every 60 min): up to 60 minutes loss
```

### Deep Dive 4: Cache Stampede Prevention

**Problem**: A hot key expires. 10,000 concurrent requests hit the cache simultaneously, all get MISS, all query the database at once → database overload (thundering herd / cache stampede).

**Solution: Locking + Stale-While-Revalidate**

```
Approach 1 — Distributed Lock (recommended):
  
  Request 1: GET hot_key → MISS
    1. Attempt lock: SET lock:hot_key 1 NX EX 5 (lock for 5s)
       → SUCCESS (got lock)
    2. Query database
    3. SET hot_key <data> EX 3600 (repopulate cache)
    4. DEL lock:hot_key (release lock)
  
  Requests 2–10000 (concurrent):
    1. GET hot_key → MISS
    2. SET lock:hot_key 1 NX → FAIL (already locked)
    3. Wait 50ms, retry GET hot_key
       → Eventually HIT (request 1 populated cache)
    4. Return cached data
  
  Result: only 1 database query instead of 10,000!

Approach 2 — Probabilistic Early Expiration:
  Store: value + logical TTL
  SET hot_key { data: ..., expires_at: now() + 3600 }
  Actual Redis TTL: 4000 (extra buffer)
  
  On GET:
    remaining_ttl = value.expires_at - now()
    if remaining_ttl < 0: definitely expired → refresh
    elif remaining_ttl < 300: probabilistically refresh
      P(refresh) = 1 - (remaining_ttl / 300)
      → As expiry approaches, more likely to trigger background refresh
    else: serve normally
  
  Effect: one random request refreshes BEFORE actual expiry
  → Cache never actually expires → no stampede

Approach 3 — Never Expire + Background Refresh:
  Set no TTL on hot keys
  Background worker: refresh hot keys every N minutes
  → Cache always populated, zero stampedes
  Con: stale data if refresh fails
  → Acceptable for many use cases (product catalogs, configs)

Recommendation: 
  Use Approach 1 (distributed lock) as primary defense
  + Approach 2 (early refresh) for known-hot keys
  + Monitoring: alert on cache miss rate spike → indicates potential stampede
```
