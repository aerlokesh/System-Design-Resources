# 🏗️ System Design — Fundamentals & Interview Framework Cheatsheet

> **Purpose**: The **final companion** completing your cheatsheet collection. Covers two categories still missing:
> 1. **The Interview Framework** — how to structure the 45-minute conversation (the meta-skill)
> 2. **Distributed Systems Fundamentals** — underlying concepts interviewers expect you to know but rarely have their own "design problem" (Raft, Gossip, WAL, LSM Trees, etc.)
>
> **Together with**: `tradeoffs-deep-dives-cheatsheet.md` (tradeoffs + deep dives) + `missing-topics-cheatsheet.md` (additional topics) = **complete interview coverage**.

---

## Table of Contents

### Part 1: The Interview Framework
1. [The 45-Minute System Design Template](#1-the-45-minute-system-design-template)
2. [Requirements Gathering Checklist](#2-requirements-gathering-checklist)
3. [Capacity Estimation Template](#3-capacity-estimation-template)
4. [API Design Quick Template](#4-api-design-quick-template)
5. [Data Model Design Patterns](#5-data-model-design-patterns)
6. [How to Draw the High-Level Architecture](#6-how-to-draw-the-high-level-architecture)
7. [Deep Dive Selection Strategy](#7-deep-dive-selection-strategy)
8. [Common Interviewer Follow-Ups & How to Handle Them](#8-common-interviewer-follow-ups--how-to-handle-them)

### Part 2: Distributed Systems Fundamentals
9. [Consensus Algorithms (Raft & Paxos)](#9-consensus-algorithms-raft--paxos)
10. [Gossip Protocol](#10-gossip-protocol)
11. [Write-Ahead Log (WAL)](#11-write-ahead-log-wal)
12. [LSM Trees vs B-Trees (Storage Engine Internals)](#12-lsm-trees-vs-b-trees-storage-engine-internals)
13. [Merkle Trees (Anti-Entropy & Data Verification)](#13-merkle-trees-anti-entropy--data-verification)
14. [Vector Clocks & Lamport Timestamps](#14-vector-clocks--lamport-timestamps)
15. [Quorum Mathematics (W + R > N)](#15-quorum-mathematics-w--r--n)

### Part 3: Infrastructure Patterns
16. [DNS & DNS-Based Routing](#16-dns--dns-based-routing)
17. [Service Discovery](#17-service-discovery)
18. [API Gateway Pattern](#18-api-gateway-pattern)
19. [Service Mesh & Sidecar Pattern](#19-service-mesh--sidecar-pattern)
20. [Health Checks (Liveness vs Readiness)](#20-health-checks-liveness-vs-readiness)
21. [Configuration Management (etcd / Consul / ZooKeeper)](#21-configuration-management-etcd--consul--zookeeper)

### Part 4: Data & Serialization
22. [Data Serialization Formats](#22-data-serialization-formats)
23. [Data Lake vs Data Warehouse](#23-data-lake-vs-data-warehouse)
24. [MapReduce & Distributed Computing Fundamentals](#24-mapreduce--distributed-computing-fundamentals)
25. [Compression Strategies](#25-compression-strategies)

---

---

# Part 1: The Interview Framework

---

## 1. The 45-Minute System Design Template

> This is the **most important section** — knowing the structure prevents rambling and ensures you hit all the signals interviewers look for.

### The Timeline

| Phase | Time | What You Do | Interviewer Signal |
|-------|------|-------------|-------------------|
| **1. Requirements** | 5 min | Clarify functional + non-functional requirements | Can you scope a problem? |
| **2. Estimation** | 3 min | Back-of-envelope (QPS, storage, bandwidth) | Can you think at scale? |
| **3. API Design** | 3 min | Define core APIs (REST endpoints) | Can you design clean interfaces? |
| **4. Data Model** | 4 min | Schema + database choice + shard key | Can you model data correctly? |
| **5. High-Level Design** | 10 min | Draw architecture diagram, walk through flows | Can you decompose a system? |
| **6. Deep Dives** | 15 min | 2-3 deep dives on critical components | Can you go deep on hard problems? |
| **7. Scaling & Bottlenecks** | 5 min | Identify bottlenecks, discuss scaling strategies | Can you think about production? |

### Phase-by-Phase Guide

**Phase 1 — Requirements (5 min)**:
- "Before I start designing, let me clarify the requirements"
- Ask 3-5 targeted questions (see Requirements Checklist below)
- State explicit assumptions: "I'll assume 500M DAU, with ~20 actions/user/day"
- Define scope: "I'll focus on the core feed and posting features, and defer DMs and search unless we have time"

**Phase 2 — Estimation (3 min)**:
- Use DAU → QPS formula (see Estimation Template)
- Estimate storage for 1 year
- Only do this if the numbers matter for design decisions (e.g., "this tells us we need 350K QPS at peak, which means we need a distributed database, not a single instance")

**Phase 3 — API Design (3 min)**:
- Define 3-5 core endpoints
- Use REST format: `POST /tweets`, `GET /timeline?cursor=X&limit=20`
- Mention pagination strategy, authentication method

**Phase 4 — Data Model (4 min)**:
- Define 2-3 core entities with key fields
- State database choice + WHY
- State shard key + WHY
- Mention indexes

**Phase 5 — High-Level Design (10 min)**:
- Draw boxes: Client → LB → API Gateway → Services → Database/Cache/Queue
- Walk through the two main flows (e.g., "post a tweet" and "read timeline")
- Label each component and explain its purpose

**Phase 6 — Deep Dives (15 min)** ⭐ Most important:
- Pick 2-3 of the hardest/most interesting components
- Deliver structured deep dives (Problem → Solution → How → Numbers → Talking Point)
- This is where you differentiate yourself

**Phase 7 — Wrap-Up (5 min)**:
- Proactively identify bottlenecks
- Discuss scaling strategy (horizontal, caching, sharding)
- Mention monitoring, alerting, failure handling
- If time: discuss future extensions

### 🚫 Common Mistakes
- Diving into design without clarifying requirements
- Spending 15 min on the high-level diagram and 5 min on deep dives (should be opposite)
- Not mentioning tradeoffs ("I'd choose X because Y, the tradeoff is Z")
- Designing for day-1 scale instead of current + 10x
- Not considering failure modes

---

## 2. Requirements Gathering Checklist

### Functional Requirements (What does the system do?)
Ask these for EVERY system design:

| Question | Why It Matters |
|----------|---------------|
| "What are the core features?" | Scope — don't design everything |
| "Who are the users?" | B2C vs B2B, single-tenant vs multi-tenant |
| "What are the main user flows?" | Defines your API and data model |
| "Do we need real-time? What latency?" | WebSocket vs polling, caching strategy |
| "Read-heavy or write-heavy?" | Database choice, caching strategy |

### Non-Functional Requirements (How well must it work?)

| Requirement | Question to Ask | Design Impact |
|------------|----------------|---------------|
| **Scale** | "How many DAU? Peak concurrent users?" | Server count, database choice |
| **Latency** | "What's the target p99 latency?" | Caching, pre-computation, CDN |
| **Availability** | "What availability target? 99.9%? 99.99%?" | Replication, multi-region, failover |
| **Consistency** | "Can we tolerate stale data? For how long?" | Strong vs eventual consistency |
| **Durability** | "Can we lose any data? Ever?" | Replication factor, backup strategy |
| **Storage** | "How much data? Retention period?" | Storage tiering, archival |

### Template Statement After Requirements
> "Let me summarize: we're designing a [system] for [DAU] users with [read/write ratio]. The core features are [1, 2, 3]. We need [availability%] availability, [latency]ms p99 latency, and [consistency model] consistency. I'll focus on [scope] and defer [out-of-scope] unless we have time."

---

## 3. Capacity Estimation Template

### Step 1: QPS
```
DAU = 500M
Actions per user per day = 20 (10 reads + 10 writes)
Total daily requests = 500M × 20 = 10B

Average QPS = 10B / 86,400 ≈ 115,000 QPS
Peak QPS = 115K × 3 = 345,000 QPS (rule of thumb: 3× average)

Read QPS = 345K × 0.9 = ~310K (read-heavy)
Write QPS = 345K × 0.1 = ~35K
```

### Step 2: Storage
```
New data per day:
- Tweets: 500M users × 0.5 tweets/day × 500 bytes = 125 GB/day
- Media: 500M × 0.1 photos/day × 2 MB = 100 TB/day
- Metadata: ~10% of content = 12.5 GB/day

Per year (text only): 125 GB × 365 = ~45 TB
Per year (with media): 100 TB × 365 = ~36 PB
With replication (3×): ~108 PB
```

### Step 3: Bandwidth
```
Ingress (writes): 35K QPS × 1 KB avg = 35 MB/s
Egress (reads): 310K QPS × 10 KB avg = 3.1 GB/s

CDN absorbs 95% of media reads → origin egress = 155 MB/s
```

### Step 4: Server Count
```
API servers: 345K peak QPS / 1K QPS per server = 345 servers
With headroom (1.5×): ~520 servers
Database: depends on choice (Cassandra ~10K writes/sec per node → ~4 nodes for writes)
Redis: 310K reads / 100K per node = ~4 nodes (for cache)
```

### Key Insight
> Don't just compute numbers — tie them to design decisions. "At 35K writes/sec, a single PostgreSQL master can't handle this — we need either sharding or a distributed write store like Cassandra."

---

## 4. API Design Quick Template

### Format
```
POST   /v1/tweets                    — Create a tweet
GET    /v1/timeline?cursor=X&limit=20 — Get home timeline  
GET    /v1/tweets/{id}               — Get a tweet
POST   /v1/tweets/{id}/like          — Like a tweet
DELETE /v1/tweets/{id}/like          — Unlike a tweet
GET    /v1/search?q=keyword&limit=20  — Search tweets
```

### Mention These Quickly
- **Authentication**: "All endpoints require a JWT in the Authorization header"
- **Pagination**: "Cursor-based pagination for feeds, offset for admin"
- **Rate Limiting**: "Rate limited at 100 req/min per user via Token Bucket"
- **Versioning**: "URL-based versioning: /v1/, /v2/"
- **Idempotency**: "POST endpoints accept Idempotency-Key header"

---

## 5. Data Model Design Patterns

### Pattern 1: Simple Entity + Foreign Keys (SQL)
```
users:     user_id (PK), name, email, created_at
tweets:    tweet_id (PK), user_id (FK), content, media_url, created_at
follows:   follower_id, followee_id (composite PK), created_at
likes:     user_id, tweet_id (composite PK), created_at
```
**Use when**: Data is relational, need transactions, < 50K writes/sec

### Pattern 2: Wide-Column / Denormalized (NoSQL)
```
Messages table (Cassandra):
  Partition key: conversation_id
  Clustering key: message_id (Snowflake — time-sorted)
  Columns: sender_id, content, media_url, created_at
```
**Use when**: Write-heavy, need horizontal scaling, queries are partition-key-based

### Pattern 3: Document (MongoDB/DynamoDB)
```
Product document:
{
  id: "prod_123",
  name: "iPhone 15",
  category: "electronics",
  price: 999,
  specs: { ram: "8GB", storage: "256GB", color: "blue" },
  reviews_count: 4521,
  avg_rating: 4.7
}
```
**Use when**: Varied schema per entity, hierarchical data, flexible queries

### Shard Key Selection Checklist
- ✅ Even distribution (no hot shards)
- ✅ Most queries hit a single shard
- ✅ Related data is co-located
- ✅ Won't need to change at 10× scale
- ❌ Avoid: timestamp alone (hot partition for current time)
- ❌ Avoid: user_id for celebrity-heavy systems (hot users)

---

## 6. How to Draw the High-Level Architecture

### Standard Components (left to right)

```
[Clients] → [CDN] → [Load Balancer] → [API Gateway] → [Services] → [Data Stores]
                                                            ↓
                                                      [Message Queue]
                                                            ↓
                                                     [Async Workers]
```

### What to Include in Every Diagram

| Component | Why |
|-----------|-----|
| **Load Balancer** | Shows you understand traffic distribution |
| **API Gateway** | Shows you understand routing, auth, rate limiting |
| **Service boxes** (2-4) | Shows you understand service decomposition |
| **Primary database** | With type label (e.g., "PostgreSQL", "Cassandra") |
| **Cache** | Shows you understand read optimization (Redis) |
| **Message queue** | Shows you understand async processing (Kafka) |
| **Object storage** | For media (S3) |
| **CDN** | For static/media content |

### Walk-Through Pattern
After drawing, walk through the two main flows:
1. **Write flow**: "When a user posts a tweet: Client → API Gateway → Tweet Service → write to Cassandra → publish to Kafka → Fanout Workers consume → write to Redis timelines"
2. **Read flow**: "When a user opens their feed: Client → API Gateway → Timeline Service → read from Redis cache → if miss, reconstruct from DB → return paginated results"

---

## 7. Deep Dive Selection Strategy

### How to Pick Which 2-3 Topics to Deep Dive

**Rule**: Pick the topics that are (a) most critical to the system's success, and (b) showcase the most interesting tradeoffs.

| System Type | Best Deep Dives |
|-------------|----------------|
| Social Feed (Twitter) | Hybrid fanout, Timeline caching, Search pipeline |
| Chat (WhatsApp) | WebSocket at scale, E2EE, Presence system |
| Booking (Ticketmaster) | Concurrency control, Virtual queue, Saga pattern |
| Analytics (Ad Aggregator) | Stream processing, Dedup, Reconciliation |
| Video (Netflix) | Adaptive bitrate, CDN pipeline, Recommendation |
| Search (Google) | Inverted index, Ranking, Spell correction |
| Payment (Stripe) | Idempotency, Saga, Exactly-once processing |

### How to Deliver a Deep Dive (70 seconds)

1. **State the problem** (10s): "The challenge is that when a celebrity tweets, pure fanout means 10M Redis writes"
2. **Describe the solution** (20s): "I'd use a hybrid approach with a 1M follower threshold"
3. **Explain how it works** (30s): Specific data structures, algorithms, data flow
4. **Quantify** (10s): "Total read latency: ~25ms. Fanout budget drops 40%."

---

## 8. Common Interviewer Follow-Ups & How to Handle Them

| Interviewer Says | What They Want | How to Respond |
|-----------------|---------------|----------------|
| "What if this gets 10× more traffic?" | Scaling strategy | Horizontal scaling, sharding, caching, CDN |
| "What happens if [component] goes down?" | Failure handling | Redundancy, failover, graceful degradation, circuit breaker |
| "How do you ensure no data loss?" | Durability | Replication factor, WAL, backups, acknowledge only after persist |
| "What are the tradeoffs of this approach?" | Tradeoff awareness | State what you gain and what you give up |
| "How would you test this?" | Operational maturity | Load testing, chaos engineering, integration tests, canary deploys |
| "How do you monitor this?" | Observability | Metrics (Prometheus), logs (ELK), traces (Jaeger), 4 golden signals |
| "What if two users do X simultaneously?" | Concurrency | Locking strategy (optimistic/pessimistic), idempotency |
| "How do you handle hot keys?" | Hot partition | Key salting, local caching, dedicated partition |
| "Let's go deeper on [X]" | Technical depth | Deliver a structured deep dive (Problem → Solution → How → Numbers) |
| "What would you do differently if starting over?" | Reflection / maturity | Acknowledge trade-offs, mention what you'd optimize |

---

---

# Part 2: Distributed Systems Fundamentals

---

## 9. Consensus Algorithms (Raft & Paxos)

**When It Comes Up**: Leader election, distributed locks, metadata management, any "how do N nodes agree?"

### Raft (Easier to Understand)

**Problem**: N servers need to agree on a sequence of commands (replicated log), even if some fail.

**How It Works**:
1. **Leader Election**: Nodes start as followers. If no heartbeat from leader for a random timeout (150-300ms), a follower becomes a candidate and requests votes. Majority vote → becomes leader.
2. **Log Replication**: Leader receives client writes → appends to its log → replicates to followers → once majority ACK → commit → apply to state machine → respond to client.
3. **Safety**: Only one leader per term. Logs are strictly ordered. Committed entries are never lost.

**Key Properties**:
- Tolerates `(N-1)/2` failures (3 nodes → 1 failure, 5 nodes → 2 failures)
- Leader handles all writes (single writer)
- Read can go to leader (strong) or any node (eventual)

**Where Used**: etcd (Kubernetes), CockroachDB, TiKV, Consul

### Paxos (Harder, More Theoretical)

Same problem, different algorithm. Multi-Paxos is used in Google's Spanner and Chubby. Raft was designed as a more understandable alternative to Paxos.

**Talking Point**: "For leader election, I'd rely on Raft consensus via etcd — it tolerates N/2 failures, ensures exactly one leader per term, and is battle-tested in Kubernetes. I don't need to implement Raft myself; I'd use it through etcd or ZooKeeper."

---

## 10. Gossip Protocol

**When It Comes Up**: Membership detection, failure detection, decentralized state propagation (Cassandra, DynamoDB)

**Problem**: In a cluster of N nodes, how does each node know about all other nodes (membership) and their health?

**Solution**: Epidemic-style information spreading. Each node periodically picks a random peer and exchanges state.

**How It Works**:
1. Every 1 second, each node picks a random peer
2. Sends its "state table" (list of all known nodes + their heartbeat counters)
3. Peer merges the incoming state with its own (take the higher heartbeat for each node)
4. Information spreads exponentially: 1 → 2 → 4 → 8 → ... → all N nodes

**Convergence Time**: `O(log N)` rounds. For 1000 nodes with 1-second gossip interval, full propagation takes ~10 seconds.

**Failure Detection**:
- If node A's heartbeat counter hasn't incremented for 30 seconds (as seen by node B through gossip), B marks A as suspected.
- If multiple nodes agree A is dead → A is marked as down → data is re-replicated.

**Where Used**: Cassandra (membership + failure detection), DynamoDB (ring management), Consul (cluster health)

**Talking Point**: "Cassandra uses gossip protocol for cluster membership — each node pings a random peer every second, exchanging heartbeat counters. Information about a new node or a failure spreads to all 1000 nodes in ~10 seconds. No central coordinator needed."

---

## 11. Write-Ahead Log (WAL)

**When It Comes Up**: Database durability, crash recovery, any "how do you ensure no data loss?"

**Problem**: If a database crashes mid-write (after modifying memory but before flushing to disk), the data is lost or corrupted.

**Solution**: Write every change to a sequential log file BEFORE applying it to the actual data structures.

**How It Works**:
1. Client sends write request
2. Database appends the operation to the WAL (sequential write → very fast)
3. Database acknowledges the write to the client
4. Later, database applies the change to the actual data files (B-tree, SSTable, etc.)
5. On crash: replay the WAL from the last checkpoint → recover all committed writes

**Why Sequential?**: Writing to the WAL is sequential I/O (append-only), which is 100× faster than random I/O. The actual data structure updates (random I/O) happen later in the background.

**Where Used**: PostgreSQL (WAL), MySQL (redo log), Cassandra (commit log), Kafka (the log IS the data), etcd, every serious database.

**Talking Point**: "Durability is guaranteed by the Write-Ahead Log — every write is appended to the WAL before acknowledgment. On crash, we replay the WAL from the last checkpoint. Sequential writes to WAL are 100× faster than random I/O to the B-tree, so this doesn't hurt performance."

---

## 12. LSM Trees vs B-Trees (Storage Engine Internals)

**When It Comes Up**: Database selection, "why Cassandra for writes?", "why PostgreSQL for reads?"

### B-Tree (PostgreSQL, MySQL, traditional RDBMS)

**How It Works**: Balanced tree structure. Data is stored in fixed-size pages (4-16 KB). Reads are O(log N) — tree traversal. Writes update pages in-place (random I/O).

**Pros**: Fast reads, fast point lookups, efficient range scans
**Cons**: Write amplification (update page + WAL), random I/O on writes, page splits on insert

### LSM Tree (Cassandra, RocksDB, LevelDB, HBase)

**How It Works**:
1. Writes go to an in-memory buffer (memtable)
2. When memtable is full → flush to disk as a sorted file (SSTable)
3. Background compaction merges SSTables periodically
4. Reads check memtable → then SSTables (with bloom filter to skip irrelevant files)

**Pros**: Extremely fast writes (all sequential I/O), high write throughput
**Cons**: Read amplification (may check multiple SSTables), space amplification (before compaction), compaction can cause latency spikes

### Comparison

| Dimension | B-Tree | LSM Tree |
|-----------|--------|----------|
| **Write speed** | Slower (random I/O) | Faster (sequential I/O) |
| **Read speed** | Faster (one tree traversal) | Slower (check multiple SSTables) |
| **Write amplification** | Moderate | Higher (compaction rewrites data) |
| **Space amplification** | Lower | Higher (multiple copies before compaction) |
| **Best for** | Read-heavy, OLTP | Write-heavy, time-series, logs |

**Talking Point**: "Cassandra uses LSM trees — writes go to an in-memory buffer and periodically flush to sorted disk files. This converts random writes into sequential I/O, which is why Cassandra handles 500K writes/sec while PostgreSQL (B-tree) tops out at ~50K. The tradeoff: reads are slower because they may check multiple SSTables."

---

## 13. Merkle Trees (Anti-Entropy & Data Verification)

**When It Comes Up**: Data consistency across replicas, "how do you detect which data is out of sync?"

**Problem**: Two replicas have billions of records. How do you efficiently find which records differ?

**Solution**: Merkle tree — a binary tree of hashes. Compare root hashes: if same → data identical. If different → traverse tree to find exact differing ranges.

**How It Works**:
1. Divide data into ranges (e.g., key ranges A-F, G-M, N-S, T-Z)
2. Hash each range
3. Build a binary tree: parent hash = hash(left_child_hash + right_child_hash)
4. Compare root hashes between replicas:
   - Same → all data matches (done!)
   - Different → compare children → narrow down to the exact differing range
5. Only sync the differing range (not all data)

**Efficiency**: Instead of comparing billions of records, compare O(log N) hashes. For 1 billion records, ~30 hash comparisons identify the exact differing range.

**Where Used**: Cassandra (anti-entropy repair), Amazon DynamoDB (background consistency), Git (content addressing), Bitcoin (transaction verification)

**Talking Point**: "Cassandra uses Merkle trees for anti-entropy repair — to find which records differ between replicas, we compare root hashes. If they match, all billion records are identical. If not, we traverse the tree and narrow down to the exact differing key range in O(log N) comparisons. Only the differing data is synced."

---

## 14. Vector Clocks & Lamport Timestamps

**When It Comes Up**: Conflict detection in multi-master systems, "how do you know which write happened first?"

**Problem**: In a distributed system without a global clock, how do you determine the order of events?

### Lamport Timestamps (Simpler)

**How It Works**:
- Each node maintains a counter
- On local event: `counter++`
- On send: attach counter to message
- On receive: `counter = max(local_counter, received_counter) + 1`

**Guarantee**: If event A happened before event B, then `timestamp(A) < timestamp(B)`. But NOT the reverse — equal timestamps don't mean simultaneous.

### Vector Clocks (Richer)

**How It Works**:
- Each node maintains a vector of counters, one per node: `[A:3, B:5, C:2]`
- On local event at node A: increment A's entry: `[A:4, B:5, C:2]`
- On send: attach entire vector
- On receive: take element-wise max + increment own entry

**Detecting Conflicts**: 
- If `VC1 < VC2` (every element ≤, at least one <): event 1 happened before event 2
- If neither `VC1 < VC2` nor `VC2 < VC1`: events are **concurrent** → conflict!

**Where Used**: Amazon DynamoDB (original Dynamo paper), Riak (for conflict detection)

**In Practice**: Most production systems use **Last-Writer-Wins** with physical timestamps instead of vector clocks (simpler, good enough). Vector clocks are academically correct but complex to implement.

**Talking Point**: "Vector clocks detect concurrent writes — if neither clock dominates the other, the writes are concurrent and need conflict resolution. In practice, most systems use Last-Writer-Wins with physical timestamps — simpler and good enough for most cases. Vector clocks matter when you truly can't lose either write."

---

## 15. Quorum Mathematics (W + R > N)

**When It Comes Up**: Any distributed database discussion (Cassandra, DynamoDB), consistency tuning

**The Formula**: `W + R > N` guarantees that reads see the latest write.
- **N** = total replicas
- **W** = replicas that must ACK a write
- **R** = replicas that must respond to a read

### Common Configurations

| Config | N | W | R | Write Latency | Read Latency | Consistency | Fault Tolerance |
|--------|---|---|---|--------------|-------------|-------------|-----------------|
| **Strong** | 3 | 3 | 1 | High (wait for all) | Low | Strong | 0 write failures |
| **Quorum** | 3 | 2 | 2 | Medium | Medium | Strong (W+R=4>3) | 1 node for R or W |
| **Fast reads** | 3 | 3 | 1 | High | Very low | Strong (W+R=4>3) | 0 write failures |
| **Fast writes** | 3 | 1 | 3 | Very low | High | Strong (W+R=4>3) | 0 read failures |
| **Eventual** | 3 | 1 | 1 | Very low | Very low | Eventual (W+R=2≤3) | 2 failures |

### The Standard Choice
**N=3, W=2, R=2**: Tolerates 1 node failure for both reads and writes. Good balance of consistency, availability, and performance. This is the default for Cassandra and DynamoDB.

**Talking Point**: "With N=3, W=2, R=2, the quorum math guarantees I always read the latest write (W+R=4 > N=3). We tolerate 1 node failure for both reads and writes. If I need faster reads at the cost of slower writes, I can shift to W=3, R=1 — still consistent, but reads touch only 1 replica."

---

---

# Part 3: Infrastructure Patterns

---

## 16. DNS & DNS-Based Routing

**When It Comes Up**: "How does the client reach your service?", multi-region architecture, failover

**How DNS Works** (simplified):
1. Client queries `api.twitter.com`
2. DNS resolver checks cache → if miss, queries authoritative DNS
3. Authoritative DNS returns IP address(es)
4. Client connects to that IP

**DNS-Based Routing Strategies**:

| Strategy | How | Use Case |
|----------|-----|----------|
| **Round-Robin** | Return different IPs on each query | Basic load distribution |
| **Geo-based** | Return IP of nearest data center based on client's location | Multi-region (EU user → EU server) |
| **Latency-based** | Return IP of lowest-latency server | Performance optimization |
| **Weighted** | Return IPs in proportion to weights (90% to primary, 10% to canary) | Canary deployments |
| **Failover** | Return primary IP; on health check failure, switch to backup | Disaster recovery |

**DNS TTL Tradeoff**:
- Short TTL (30s): Fast failover, but more DNS queries (higher latency, more load on DNS)
- Long TTL (3600s): Fewer DNS queries, but slow failover (users keep connecting to dead server for up to 1 hour)
- Common: 60-300 seconds for most services

**Talking Point**: "DNS geolocation routing sends EU users to eu-west-1 and US users to us-east-1 automatically. TTL is set to 60 seconds — short enough for fast failover if a region goes down, but long enough to keep DNS query volume manageable."

---

## 17. Service Discovery

**When It Comes Up**: "How do services find each other?", microservices architecture

**Problem**: In a microservices world with auto-scaling, services spin up/down constantly. Hardcoding IP addresses is impossible.

### Client-Side Discovery
- **How**: Services register with a service registry (Consul, etcd, ZooKeeper). Client queries registry to find available instances. Client-side load balancing.
- **Tools**: Netflix Eureka + Ribbon, Consul
- **Pros**: Client picks best instance (least connections, locality-aware)
- **Cons**: Every client needs discovery logic

### Server-Side Discovery
- **How**: Client sends request to a load balancer/proxy. The proxy queries the service registry and routes to an available instance.
- **Tools**: AWS ALB + ECS/EKS, Kubernetes Service (kube-proxy), Consul Connect
- **Pros**: Clients are simple (just call the LB). Discovery logic centralized.
- **Cons**: Extra network hop

### Kubernetes Service Discovery
- **How**: Each service gets a DNS name (`tweet-service.default.svc.cluster.local`). kube-proxy routes to healthy pods. No external registry needed.
- **This is the de facto standard** for most modern deployments.

**Talking Point**: "In Kubernetes, each service gets a DNS name — the Tweet Service calls `user-service.default.svc.cluster.local` to reach any healthy User Service pod. kube-proxy handles load balancing. No hardcoded IPs, no external service registry — Kubernetes handles it natively."

---

## 18. API Gateway Pattern

**When It Comes Up**: System entry point, microservices architecture, auth/rate-limiting

**Problem**: Clients shouldn't call 10 different microservices directly. Need a single entry point for authentication, rate limiting, routing, and protocol translation.

**What an API Gateway Does**:

| Function | How |
|----------|-----|
| **Routing** | Route `/api/tweets/*` to Tweet Service, `/api/users/*` to User Service |
| **Authentication** | Validate JWT, inject user context headers |
| **Rate Limiting** | Per-user, per-IP, per-API-key throttling |
| **SSL Termination** | Handle TLS, backends communicate over plain HTTP internally |
| **Request/Response Transform** | Protocol translation (REST → gRPC), response aggregation |
| **Caching** | Cache GET responses with short TTL |
| **Logging/Metrics** | Request logging, latency metrics, error rates |
| **Circuit Breaking** | Stop routing to unhealthy backends |

**Tools**: Kong, AWS API Gateway, Nginx, Envoy, Zuul (Netflix)

**BFF (Backend for Frontend) Pattern**: Separate API gateways for different clients:
- Web BFF: optimized for web client (full payloads)
- Mobile BFF: optimized for mobile (smaller payloads, fewer round trips)
- Partner BFF: optimized for external APIs (different auth, stricter rate limits)

**Talking Point**: "The API Gateway is the single entry point — it handles authentication (validate JWT), rate limiting (Token Bucket per user), and routing (/tweets → Tweet Service, /users → User Service). It also terminates TLS so internal services communicate over plain HTTP. I'd use a BFF pattern: separate gateways for web and mobile clients."

---

## 19. Service Mesh & Sidecar Pattern

**When It Comes Up**: "How do services communicate securely?", microservices networking, observability

**Problem**: With 100+ microservices, every service needs: mTLS, retries, circuit breaking, tracing, load balancing. Implementing this in every service is duplicated effort.

**Solution**: Service mesh — infrastructure layer that handles service-to-service communication transparently via sidecar proxies.

**How It Works**:
1. Each service pod gets a **sidecar proxy** (Envoy) injected alongside it
2. All inbound/outbound traffic flows through the sidecar
3. The sidecar handles: mTLS encryption, retries, circuit breaking, load balancing, observability
4. **Control plane** (Istio) configures all sidecars centrally

**What the Sidecar Handles (so your code doesn't have to)**:
- ✅ Mutual TLS (zero-trust networking)
- ✅ Automatic retries with backoff
- ✅ Circuit breaking
- ✅ Distributed tracing (inject trace headers)
- ✅ Load balancing (least connections, locality-aware)
- ✅ Traffic splitting (canary: 5% to v2, 95% to v1)

**Tools**: Istio + Envoy, Linkerd, AWS App Mesh

**Talking Point**: "I'd use a service mesh (Istio + Envoy sidecar) so that mTLS, retries, circuit breaking, and distributed tracing are handled at the infrastructure layer — not in application code. Every service gets an Envoy sidecar that transparently intercepts all traffic. The control plane manages configuration centrally."

---

## 20. Health Checks (Liveness vs Readiness)

**When It Comes Up**: Kubernetes deployments, load balancer routing, availability

**Problem**: How does the system know if a service instance is healthy?

### Two Types of Health Checks

| Check | Question It Answers | Failure Action | Example |
|-------|-------------------|----------------|---------|
| **Liveness** | "Is this process alive and not deadlocked?" | Kill and restart the container | HTTP GET `/health/live` returns 200 |
| **Readiness** | "Can this instance handle traffic right now?" | Remove from load balancer (don't kill) | HTTP GET `/health/ready` returns 200 |

### Why Two Checks?

**Liveness**: The process is stuck (deadlock, infinite loop, OOM). The ONLY fix is to restart it.
- Check: "Can the process respond at all?"
- On failure: Kubernetes restarts the pod

**Readiness**: The process is alive but not ready for traffic (warming cache, loading ML model, DB connection initializing).
- Check: "Can the process handle requests correctly?"
- On failure: Remove from Service endpoints (no traffic routed). Don't restart — it might recover.

### Health Check Implementation
```json
GET /health/live → { "status": "UP" }  // 200 if process is running

GET /health/ready → {
  "status": "UP",
  "checks": {
    "database": "UP",     // can connect to DB
    "cache": "UP",        // can connect to Redis  
    "warm": true          // cache is warmed
  }
}
```

**Talking Point**: "I'd configure both liveness and readiness probes. Liveness restarts deadlocked containers. Readiness removes instances from the load balancer during startup or when dependencies are down — so users never hit an instance that can't serve traffic correctly."

---

## 21. Configuration Management (etcd / Consul / ZooKeeper)

**When It Comes Up**: Leader election, service discovery, feature flags, distributed coordination

**Problem**: Distributed systems need a shared, consistent configuration store for: leader election, service discovery, feature flags, distributed locks, cluster membership.

### Comparison

| Tool | Consensus | Language | Sweet Spot |
|------|-----------|----------|------------|
| **ZooKeeper** | ZAB (Paxos-like) | Java | Hadoop ecosystem, legacy systems |
| **etcd** | Raft | Go | Kubernetes, modern cloud-native |
| **Consul** | Raft | Go | Service mesh, multi-DC, health checking |

### Common Use Cases

| Use Case | How | Which Tool |
|----------|-----|-----------|
| **Leader Election** | Create ephemeral node; holder is leader; on disconnect, auto-removed → re-election | ZooKeeper, etcd |
| **Service Discovery** | Register service endpoint on startup; deregister on shutdown; query for healthy instances | Consul, etcd |
| **Distributed Lock** | Create key with lease/TTL; owner holds lock; auto-released on crash | etcd, ZooKeeper |
| **Feature Flags** | Store flag state as key-value; services watch for changes | etcd, Consul |
| **Cluster Membership** | Heartbeat registration; TTL expiry for dead nodes | Consul, ZooKeeper |

**Talking Point**: "For leader election, I'd use etcd (since we're on Kubernetes). The scheduler acquires a lease on a key — if the leader crashes, the lease expires and another instance acquires it. etcd uses Raft consensus, so we're guaranteed exactly one leader even during network partitions."

---

---

# Part 4: Data & Serialization

---

## 22. Data Serialization Formats

**When It Comes Up**: "What format for messages in Kafka?", "How do services communicate?", schema evolution

| Format | Type | Size | Speed | Schema Evolution | Human Readable | Best For |
|--------|------|------|-------|-----------------|---------------|----------|
| **JSON** | Text | Large | Slow | Weak (no schema) | ✅ Yes | REST APIs, config files, debugging |
| **Protocol Buffers** | Binary | Small (3-10× smaller than JSON) | Fast | ✅ Strong (field numbers) | ❌ No | gRPC, internal services |
| **Avro** | Binary | Small | Fast | ✅ Strong (schema registry) | ❌ No | Kafka events, data pipelines |
| **Thrift** | Binary | Small | Fast | ✅ Good | ❌ No | Facebook ecosystem |
| **MessagePack** | Binary | Medium | Fast | Weak | ❌ No | Cache values, compact JSON replacement |

### Schema Evolution (Why It Matters)

**Problem**: You add a field to your Kafka event schema. Old consumers don't know about this field. New consumers expect it. How to avoid breaking everything?

**Solutions**:
- **Protobuf**: Fields are numbered, not named. Adding field #5 doesn't affect consumers that only know fields #1-4. They simply ignore unknown fields.
- **Avro + Schema Registry**: Schema stored centrally (Confluent Schema Registry). Producer registers new schema. Consumer fetches schema by ID from registry. Backward/forward compatibility enforced.

**Talking Point**: "For Kafka events, I'd use Avro with a Schema Registry — producers register schemas, consumers fetch them by ID, and the registry enforces backward compatibility. This means I can add fields without breaking existing consumers."

---

## 23. Data Lake vs Data Warehouse

**When It Comes Up**: Analytics architecture, "where does the raw data go?", ML pipeline data source

| Aspect | Data Lake | Data Warehouse |
|--------|-----------|---------------|
| **Data format** | Raw (JSON, Parquet, logs, images) | Structured (star/snowflake schema) |
| **Schema** | Schema-on-read (interpret when querying) | Schema-on-write (enforce on insert) |
| **Storage** | Cheap object storage (S3, GCS) | Purpose-built DB (Redshift, BigQuery, Snowflake) |
| **Cost** | Very low ($0.023/GB/month on S3) | Higher ($0.10-$1/GB/month) |
| **Query speed** | Slow (scan raw files) | Fast (pre-optimized, indexed) |
| **Best for** | Raw data archive, ML training, exploration | BI dashboards, business reporting, SQL analytics |

### Modern Architecture: Lakehouse
- Store raw data in Data Lake (S3 + Parquet)
- Use query engines that work directly on the lake (Spark, Presto, Athena)
- Get warehouse-like performance without moving data
- Tools: Databricks Delta Lake, Apache Iceberg, Apache Hudi

**Talking Point**: "Raw events go to S3 (data lake) in Parquet format — cheap, durable, unlimited scale. For dashboards, I'd ETL aggregated data into a data warehouse (BigQuery or Redshift) for fast SQL queries. The lake is the archive and ML training source; the warehouse is the BI/reporting layer."

---

## 24. MapReduce & Distributed Computing Fundamentals

**When It Comes Up**: Batch processing, "how does Spark work?", large-scale data processing

**Problem**: Process 100 TB of data. No single machine has enough RAM or disk.

**Solution**: Split data across N machines, process in parallel, combine results.

### MapReduce Phases

**1. Map Phase**: Each worker processes a chunk of input, emits (key, value) pairs
```
Input: "the cat sat on the mat"
Map output: [(the, 1), (cat, 1), (sat, 1), (on, 1), (the, 1), (mat, 1)]
```

**2. Shuffle Phase**: Group all values by key, redistribute to reducers
```
Shuffle: {the: [1, 1], cat: [1], sat: [1], on: [1], mat: [1]}
```

**3. Reduce Phase**: Each reducer aggregates values for its keys
```
Reduce output: [(the, 2), (cat, 1), (sat, 1), (on, 1), (mat, 1)]
```

### Modern Equivalents

| Original | Modern | Improvement |
|----------|--------|------------|
| Hadoop MapReduce | Apache Spark | 10-100× faster (in-memory) |
| HDFS | S3 / GCS | Cheaper, managed |
| Hive (SQL on MapReduce) | Presto / Athena / BigQuery | Interactive query speed |
| Storm (streaming) | Flink / Kafka Streams | Exactly-once, event time processing |

**Talking Point**: "The nightly reconciliation job uses Spark on S3 — Spark reads 200 billion events partitioned by date, aggregates by (campaign_id, ad_id, hour), and compares to streaming results. Spark distributes this across 200 worker nodes, processing the full day in ~4 hours. It's the modern equivalent of MapReduce, but 100× faster because it processes in-memory."

---

## 25. Compression Strategies

**When It Comes Up**: Storage cost optimization, network bandwidth, columnar databases

### Types

| Algorithm | Speed | Ratio | Best For |
|-----------|-------|-------|----------|
| **LZ4** | Very fast | ~2-3× | Real-time compression (Kafka, Redis) |
| **Snappy** | Fast | ~2-3× | Hadoop, BigQuery, general purpose |
| **Gzip** | Medium | ~5-8× | HTTP responses, file archives |
| **Zstd** | Fast + good ratio | ~3-6× | Best of both worlds (becoming standard) |
| **Brotli** | Slow | ~6-10× | Static web assets (CSS, JS) |

### Where Compression Matters Most

| Layer | What to Compress | Algorithm | Savings |
|-------|-----------------|-----------|---------|
| **Kafka** | Message batches | LZ4 or Snappy | 3-5× bandwidth reduction |
| **Columnar DB** (ClickHouse) | Column data | LZ4 + dictionary encoding | 10-20× storage reduction |
| **API responses** | JSON payloads | Gzip or Brotli | 5-10× smaller responses |
| **Object storage** | Archived data (S3) | Gzip or Zstd | 5-8× storage cost reduction |
| **CDN** | Static assets | Brotli (pre-compressed) | 10× smaller JS/CSS |

**Why Columnar Compression is Special**: In ClickHouse, a column like `country_code` has millions of rows but only ~200 unique values. Dictionary encoding replaces each value with a 1-byte index. Combined with LZ4, this achieves 10-20× compression — scanning 1 billion rows of a column takes seconds.

**Talking Point**: "I'd enable LZ4 compression on Kafka topics — message batches compress 3-5× with negligible CPU cost, reducing broker storage and network bandwidth significantly. For ClickHouse, columnar compression achieves 10-20× reduction because columns have low cardinality — a country_code column with 200 unique values compresses extremely well."

---

---

## 🎯 Complete Coverage Map: All 3 Cheatsheets Combined

| Category | Tradeoffs Cheatsheet | Missing Topics Cheatsheet | This Cheatsheet |
|----------|---------------------|--------------------------|-----------------|
| **Database Selection** | ✅ 5 DB types, SQL vs NoSQL | | ✅ LSM vs B-Tree internals |
| **Consistency** | ✅ CAP, 5 consistency models | ✅ CRDTs | ✅ Vector clocks, quorum math |
| **Caching** | ✅ 5 strategies, invalidation | ✅ Stampede, key design | |
| **Communication** | ✅ REST/WS/SSE/gRPC | | ✅ Service mesh, API gateway |
| **Queues/Streaming** | ✅ Queue vs stream | ✅ DLQ, outbox pattern | ✅ Serialization formats |
| **Scaling** | ✅ Sharding, replication, LB | ✅ Distributed counter | ✅ DNS routing, service discovery |
| **Search** | ✅ ES, inverted index | ✅ Autocomplete, spell check, CDC pipeline | |
| **Real-Time** | ✅ WebSocket, fanout | ✅ Presence, notification aggregation | |
| **Security** | ✅ E2EE, JWT, OAuth | ✅ OAuth/SSO deep dive | ✅ Service mesh mTLS |
| **Processing** | ✅ Batch vs stream, dedup | ✅ HyperLogLog | ✅ MapReduce, compression |
| **Operations** | ✅ Availability, DR | ✅ Feature flags, canary, circuit breaker, observability | ✅ Health checks, config management |
| **Fundamentals** | | | ✅ Raft, gossip, WAL, Merkle trees |
| **Interview Skills** | ✅ Delivery framework | | ✅ **Full 45-min template, estimation, follow-ups** |
| **Data Architecture** | ✅ Storage tiering | | ✅ Data lake vs warehouse |

### Total Coverage Across All 3 Documents
- **Tradeoffs Cheatsheet**: 35 tradeoff categories + 20 deep dives
- **Missing Topics Cheatsheet**: 20 deep dives + 10 bonus topics
- **This Cheatsheet**: 8 interview framework sections + 7 distributed systems fundamentals + 6 infrastructure patterns + 4 data topics

**Grand Total**: ~105 distinct topics covering every aspect of system design interviews.

---

**Document Version**: 1.0  
**Last Updated**: April 2026  
**Coverage**: Interview framework + distributed systems fundamentals + infrastructure patterns + data engineering  
**Companion To**: `system-design-tradeoffs-deep-dives-cheatsheet.md` + `system-design-missing-topics-cheatsheet.md`  
**Status**: Complete & Interview-Ready ✅
