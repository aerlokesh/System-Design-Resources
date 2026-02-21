# System Design Mnemonics & Memory Aids

> **Purpose**: Quick mnemonics, acronyms, and memory tricks to recall system design concepts instantly during interviews.

---

## Table of Contents

1. [OSI 7 Layers](#1-osi-7-layers)
2. [CAP Theorem](#2-cap-theorem)
3. [ACID Properties (Databases)](#3-acid-properties)
4. [BASE Properties (NoSQL)](#4-base-properties)
5. [SOLID Principles](#5-solid-principles)
6. [HTTP Methods (REST)](#6-http-methods-rest)
7. [HTTP Status Codes](#7-http-status-codes)
8. [System Design Interview Framework](#8-system-design-interview-framework)
9. [Non-Functional Requirements](#9-non-functional-requirements)
10. [Caching Strategies](#10-caching-strategies)
11. [Database Types](#11-database-types)
12. [Consistency Models](#12-consistency-models)
13. [Rate Limiting Algorithms](#13-rate-limiting-algorithms)
14. [Load Balancing Algorithms](#14-load-balancing-algorithms)
15. [Kafka Key Concepts](#15-kafka-key-concepts)
16. [Scaling Patterns](#16-scaling-patterns)
17. [Back-of-Envelope Numbers](#17-back-of-envelope-numbers)
18. [Availability Nines](#18-availability-nines)
19. [Microservices Patterns](#19-microservices-patterns)
20. [Failure Handling](#20-failure-handling)
21. [Data Structures for System Design](#21-data-structures-for-system-design)
22. [Sharding Strategies](#22-sharding-strategies)
23. [Message Delivery Guarantees](#23-message-delivery-guarantees)
24. [DNS Record Types](#24-dns-record-types)
25. [Common Port Numbers](#25-common-port-numbers)
26. [Powers of 2 (Capacity Math)](#26-powers-of-2)
27. [Latency Numbers Every Programmer Should Know](#27-latency-numbers)

---

## 1. OSI 7 Layers

### Mnemonic: **"Please Do Not Throw Sausage Pizza Away"**

| Layer | Name | Mnemonic Word | What It Does | Protocol Examples |
|-------|------|--------------|--------------|-------------------|
| 7 | **A**pplication | **A**way | User-facing (HTTP, DNS) | HTTP, HTTPS, FTP, SMTP, DNS |
| 6 | **P**resentation | **P**izza | Data format/encryption | SSL/TLS, JPEG, JSON |
| 5 | **S**ession | **S**ausage | Connection management | NetBIOS, RPC |
| 4 | **T**ransport | **T**hrow | End-to-end delivery | TCP, UDP |
| 3 | **N**etwork | **N**ot | Routing/IP addressing | IP, ICMP, BGP |
| 2 | **D**ata Link | **D**o | Frames/MAC addressing | Ethernet, WiFi, ARP |
| 1 | **P**hysical | **P**lease | Bits on wire/air | Cables, radio signals |

**Bottom → Top**: Please Do Not Throw Sausage Pizza Away
**Top → Bottom**: All People Seem To Need Data Processing

### Interview Shortcut
For system design, you really only need 4 layers:
- **Layer 4** (Transport): TCP vs UDP — "reliable vs fast"
- **Layer 7** (Application): HTTP, WebSocket, gRPC — "what protocol for our API?"
- **Layer 4 LB vs Layer 7 LB**: "TCP pass-through vs content-based routing"

---

## 2. CAP Theorem

### Mnemonic: **"You Can Always Pick 2"**

```
    C ───── A
    │       │
    └── P ──┘
    
Pick 2 of 3:
  CP = Consistent + Partition-tolerant (sacrifice Availability)
  AP = Available + Partition-tolerant (sacrifice Consistency)
  CA = Consistent + Available (only without partitions — single node)
```

### Memory Aid: **"CPA — like an accountant"**
- **C**onsistency = everyone sees the same data
- **P**artition tolerance = system works despite network splits
- **A**vailability = every request gets a response

### Quick Decision:
- **Money/inventory** → CP (consistency matters — better to reject than to double-sell)
- **Social feeds/likes** → AP (availability matters — stale is OK, errors are not)

---

## 3. ACID Properties

### Mnemonic: The word **ACID** itself — think "acid test" for reliability

| Letter | Property | Memory Aid | Meaning |
|--------|----------|-----------|---------|
| **A** | Atomicity | "**A**ll or nothing" | Transaction fully completes or fully rolls back |
| **C** | Consistency | "**C**orrect state" | DB moves from one valid state to another |
| **I** | Isolation | "**I**nvisible to others" | Concurrent transactions don't interfere |
| **D** | Durability | "**D**isk survives" | Committed data survives crashes |

**When to mention**: SQL databases, financial transactions, booking systems, payments

---

## 4. BASE Properties

### Mnemonic: **"BASE is the opposite of ACID"** (chemistry: acid vs base)

| Letter | Property | Meaning |
|--------|----------|---------|
| **BA** | **B**asically **A**vailable | System is always available (may return stale data) |
| **S** | **S**oft state | State may change over time without input (due to eventual consistency) |
| **E** | **E**ventual consistency | Given enough time, all replicas converge |

**When to mention**: NoSQL databases (Cassandra, DynamoDB), social feeds, counters

---

## 5. SOLID Principles

### Mnemonic: The word **SOLID** — think "solid code"

| Letter | Principle | Memory Aid |
|--------|-----------|-----------|
| **S** | Single Responsibility | "**S**ingle job per class" |
| **O** | Open/Closed | "**O**pen for extension, closed for modification" |
| **L** | Liskov Substitution | "**L**ike parent, like child" (subtypes replaceable) |
| **I** | Interface Segregation | "**I**nterfaces should be small" (don't force unused methods) |
| **D** | Dependency Inversion | "**D**epend on abstractions, not concretions" |

**When to mention**: LLD interviews, service design, API interface design

---

## 6. HTTP Methods (REST)

### Mnemonic: **"Get People Posted, Put Patches, Delete"**

| Method | Action | Idempotent? | Safe? | Memory Aid |
|--------|--------|-------------|-------|-----------|
| **GET** | Read | ✅ | ✅ | "**G**rab data" |
| **POST** | Create | ❌ | ❌ | "**P**ost a new letter" |
| **PUT** | Replace/Update (full) | ✅ | ❌ | "**Put** the whole thing back" |
| **PATCH** | Partial Update | ❌ | ❌ | "**Patch** a hole (partial fix)" |
| **DELETE** | Delete | ✅ | ❌ | Self-explanatory |

### Idempotency Trick
**"GPD are idempotent"** — GET, PUT, DELETE can be safely retried. POST and PATCH cannot (without idempotency keys).

---

## 7. HTTP Status Codes

### Mnemonic: **"1-Info, 2-Success, 3-Redirect, 4-You Messed Up, 5-I Messed Up"**

| Range | Category | Memory Aid | Key Codes |
|-------|----------|-----------|-----------|
| **1xx** | Informational | "**1**nformational" | 100 Continue |
| **2xx** | Success | "**2** thumbs up ✌️" | 200 OK, 201 Created, 204 No Content |
| **3xx** | Redirect | "**3**… 2… 1… redirect!" | 301 Moved Permanently, 302 Found (temp redirect), 304 Not Modified |
| **4xx** | Client Error | "**4**ault is yours" | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 429 Too Many Requests |
| **5xx** | Server Error | "**5**erver broke" | 500 Internal Error, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout |

### Must-Know Codes for System Design
- **200**: OK (GET success)
- **201**: Created (POST success)
- **204**: No Content (DELETE success)
- **301**: Permanent redirect (URL shortener!)
- **302**: Temporary redirect
- **400**: Bad request (validation error)
- **401**: Unauthorized (not logged in)
- **403**: Forbidden (logged in but no permission)
- **404**: Not found
- **409**: Conflict (double-booking, race condition)
- **429**: Rate limited
- **500**: Server error
- **503**: Service unavailable (overloaded)

---

## 8. System Design Interview Framework

### Hello Interview Framework: **"READHD"** (6 Steps)

| Step | Name | Mnemonic | What to Do |
|------|------|----------|-----------|
| 1 | **R**equirements | "**R**equirements first!" | Clarify functional + non-functional requirements |
| 2 | **E**ntities | "**E**ntities/data model" | Define core entities and relationships |
| 3 | **A**PI | "**A**PI design" | Define endpoints (REST/GraphQL/gRPC) |
| 4 | **D**ata Flow | "**D**ata flow" | Walk through key user flows step-by-step |
| 5 | **H**igh-Level Design | "**H**igh-level architecture" | Draw the component diagram |
| 6 | **D**eep Dives | "**D**ive deep" | Discuss tradeoffs, scaling, failure handling |

### Alternative Framework: **"RESHADE"**

- **R**equirements → **E**stimation → **S**torage/Schema → **H**igh-Level Design → **A**PI → **D**eep Dives → **E**valuation

---

## 9. Non-Functional Requirements

### Mnemonic: **"SCALDR"** (like "scalder" — it scales and burns through problems)

| Letter | NFR | Memory Aid |
|--------|-----|-----------|
| **S** | **S**calability | How many users/requests? |
| **C** | **C**onsistency | Strong or eventual? |
| **A** | **A**vailability | What uptime SLA? (99.9%? 99.99%?) |
| **L** | **L**atency | How fast? (p95, p99) |
| **D** | **D**urability | Can we lose data? |
| **R** | **R**eliability | What happens when things fail? |

### Extended: **"SCALDR + MF"** (add Maintainability + Fault tolerance)

---

## 10. Caching Strategies

### Mnemonic: **"AWWRR"** (like a pirate growl 🏴‍☠️)

| Letter | Strategy | When |
|--------|----------|------|
| **A**side | Cache-**A**side (lazy load) | Most common — read-heavy, OK with brief staleness |
| **W**rite-through | **W**rite-through | Need cache always fresh |
| **W**rite-behind | **W**rite-behind (back) | Write-heavy, batch to DB |
| **R**ead-through | **R**ead-through | Cache handles DB fetch on miss |
| **R**efresh-ahead | **R**efresh before expiry | Hot keys, predictable access |

### Cache Invalidation Trick: **"TEV"**
- **T**TL (time-based expiry)
- **E**vent-driven (publish on write → invalidate)
- **V**ersion-based (check version number)

---

## 11. Database Types

### Mnemonic: **"RDGKW"** — **"Really Determined Gorillas Know Wisdom"**

| Letter | Type | Example | Best For |
|--------|------|---------|----------|
| **R**elational | SQL | MySQL, PostgreSQL | Transactions, JOINs, structured data |
| **D**ocument | NoSQL | MongoDB, DynamoDB | Flexible schema, hierarchical data |
| **G**raph | Graph | Neo4j, Neptune | Relationships, social graphs |
| **K**ey-Value | Cache/Store | Redis, Memcached | Caching, sessions, counters |
| **W**ide-Column | Column-family | Cassandra, HBase | Write-heavy, time-series, messages |

### Quick Decision:
- "Need transactions?" → **R**elational
- "Need flexible schema?" → **D**ocument
- "Need relationships?" → **G**raph
- "Need speed (< 1ms)?" → **K**ey-Value
- "Need write throughput?" → **W**ide-Column

---

## 12. Consistency Models

### Mnemonic: **"SECRM"** — **"Strong Eventual Causal Read Monotonic"** (from strongest to weakest)

| Model | Strength | Memory Aid |
|-------|----------|-----------|
| **S**trong | 💪💪💪 | "**S**ynchronized everywhere" — always see latest write |
| **E**ventual | 💪 | "**E**ventually they'll agree" — replicas converge over time |
| **C**ausal | 💪💪 | "**C**ause before effect" — replies after messages |
| **R**ead-your-writes | 💪💪 | "**R**ead what I just wrote" — user sees own updates |
| **M**onotonic reads | 💪💪 | "**M**oving forward only" — never see older version after newer |

---

## 13. Rate Limiting Algorithms

### Mnemonic: **"TL FSS"** — **"Too Late For Sliding Sliders"**

| Letters | Algorithm | Memory Aid |
|---------|-----------|-----------|
| **T**oken | Token Bucket | "**T**okens in a bucket — take one per request" |
| **L**eaky | Leaky Bucket | "**L**eaking at a steady rate" |
| **F**ixed | Fixed Window | "**F**ixed time box — count resets each window" |
| **S**liding Log | Sliding Window Log | "**S**liding window of timestamps" |
| **S**liding Counter | Sliding Window Counter | "**S**mooth approximation of sliding log" |

### Quick Pick:
- **Token Bucket**: Allows bursts → best for most APIs
- **Fixed Window**: Simplest but boundary-burst problem
- **Sliding Window Counter**: Best balance of accuracy + efficiency

---

## 14. Load Balancing Algorithms

### Mnemonic: **"RR WLC IR"** — **"Round Robin, Weighted, Least Connections, IP, Random"**

| Algorithm | When to Use | Memory Aid |
|-----------|-------------|-----------|
| **R**ound **R**obin | Equal servers, stateless | "**R**otate through the list" |
| **W**eighted RR | Different server sizes | "**W**eighed — bigger gets more" |
| **L**east **C**onnections | Long-lived connections | "**L**east busy gets next request" |
| **I**P Hash | Session stickiness | "**I**P determines server" |
| **R**andom | Simple, scales well | "**R**andomly pick one" |

---

## 15. Kafka Key Concepts

### Mnemonic: **"BTPC OCR"** — **"Big Topics Partition Consumers, Offsets Control Replay"**

| Letter | Concept | One-Liner |
|--------|---------|-----------|
| **B**roker | Server | Kafka server that stores partitions |
| **T**opic | Category | Named stream of events |
| **P**artition | Shard | Ordered log within a topic |
| **C**onsumer Group | Team | Set of consumers sharing work |
| **O**ffset | Position | Consumer's progress marker |
| **C**ommit | Checkpoint | Save consumer position |
| **R**eplication | Copies | RF=3 means 3 copies of each partition |

### Kafka Rules of Thumb:
- "**1 partition = 1 consumer max**" (per group)
- "**Ordering = partition key**" (same key → same partition → ordered)
- "**Multiple groups = independent reads**" (fanout, search, analytics)
- "**Consumer lag = trouble**" (monitor it!)

---

## 16. Scaling Patterns

### Mnemonic: **"VRCS"** — **"Vertically, Replicate, Cache, Shard"**

| Pattern | What | When |
|---------|------|------|
| **V**ertical scaling | Bigger machine | First step, simplest, has limits |
| **R**ead replicas | Copy data to read-only nodes | Read-heavy (1000:1 read:write) |
| **C**aching | Store hot data in memory | Reduce DB load |
| **S**harding | Split data across nodes | Write-heavy, data too large for one node |

### Scaling Decision Ladder:
```
Step 1: Vertical scaling (bigger box)
Step 2: Add caching (Redis)
Step 3: Read replicas (scale reads)
Step 4: Sharding (scale writes)
Step 5: Microservices (scale teams)
```

---

## 17. Back-of-Envelope Numbers

### Mnemonic: **"2.5 Rule"** — Most daily-to-QPS conversions ≈ dividing by ~100K

```
1 day = 86,400 seconds ≈ ~100K seconds (for quick math)

So: X million/day ÷ 100K = X × 10 QPS

Examples:
  1M requests/day ÷ 100K = ~10 QPS
  10M requests/day ÷ 100K = ~100 QPS
  100M requests/day ÷ 100K = ~1,000 QPS
  1B requests/day ÷ 100K = ~10,000 QPS
```

### Key Storage Numbers: **"KB MB GB TB PB"**

```
1 KB = 1,000 bytes ≈ a short text message
1 MB = 1,000 KB ≈ a photo
1 GB = 1,000 MB ≈ a movie
1 TB = 1,000 GB ≈ a small database
1 PB = 1,000 TB ≈ a large company's data
```

### Quick Storage Formulas:
```
Daily storage = events/day × bytes/event
Yearly storage = daily × 365
Total with replication = storage × RF
```

---

## 18. Availability Nines

### Mnemonic: **"2-3-4-5 → Days-Hours-Minutes-Minutes"**

| Nines | Availability | Downtime/Year | Memory Aid |
|-------|-------------|---------------|-----------|
| **2** nines | 99% | **3.65 days** | "2 nines = **3** days" |
| **3** nines | 99.9% | **8.77 hours** | "3 nines = **8** hours" |
| **4** nines | 99.99% | **52.6 minutes** | "4 nines = **52** minutes" |
| **5** nines | 99.999% | **5.26 minutes** | "5 nines = **5** minutes" |

### Quick Trick:
- Move from N nines to N+1 nines = **divide downtime by 10**
- 3 nines (8 hours) → 4 nines (≈50 min) → 5 nines (≈5 min)

---

## 19. Microservices Patterns

### Mnemonic: **"SCAR BED"**

| Letter | Pattern | What It Solves |
|--------|---------|---------------|
| **S**aga | Distributed transactions | Multi-service workflows (reserve → pay → confirm) |
| **C**ircuit Breaker | Cascading failures | Stop calling a dead service |
| **A**PI Gateway | Entry point | Auth, rate limiting, routing |
| **R**etry + Backoff | Transient failures | Retry with exponential delay |
| **B**ulkhead | Isolation | One service failure doesn't bring down others |
| **E**vent-Driven | Decoupling | Services communicate via events (Kafka) |
| **D**iscovery | Finding services | Service registry (Consul, Eureka, K8s DNS) |

---

## 20. Failure Handling

### Mnemonic: **"FRITES"** (like French fries 🍟)

| Letter | Strategy | When |
|--------|----------|------|
| **F**ailover | Switch to backup | Primary dies → standby takes over |
| **R**etry | Try again | Transient network error |
| **I**dempotency | Safe to repeat | Ensure duplicate requests have same effect |
| **T**imeout | Give up after N seconds | Don't wait forever for a dead service |
| **E**xponential backoff | Slow down retries | Don't hammer a recovering service |
| **S**hedding | Drop low-priority work | System overloaded → protect critical paths |

---

## 21. Data Structures for System Design

### Mnemonic: **"BLUSH QT"**

| Letter | Data Structure | System Design Use |
|--------|---------------|-------------------|
| **B**loom filter | Probabilistic set membership | URL dedup (crawler), ad click dedup |
| **L**SM tree | Log-structured merge tree | Write-heavy DBs (Cassandra, RocksDB) |
| **U**nion-Find | Disjoint sets | Social network connected components |
| **S**orted Set (Redis ZSET) | Sorted by score | Leaderboards, timelines, rate limiting |
| **H**ash map / ring | Key-value + consistent hashing | Caching, sharding |
| **Q**ueue (priority) | Priority queue | Job scheduling, notification priority |
| **T**rie | Prefix tree | Autocomplete, IP routing, DNS |

---

## 22. Sharding Strategies

### Mnemonic: **"HRCG"** — **"Hash, Range, Consistent, Geo"**

| Strategy | Key Idea | Best For |
|----------|----------|----------|
| **H**ash | `hash(key) % N` | Even distribution (user_id) |
| **R**ange | Key ranges (A-M, N-Z) | Time-series, range queries |
| **C**onsistent hashing | Hash ring + virtual nodes | Cache nodes, minimal reshuffling |
| **G**eographic | By region/country | Data residency, low latency |

### Shard Key Decision: **"Keep related data together"**
- Chat messages → shard by conversation_id
- User data → shard by user_id
- Tickets → shard by event_id
- Analytics → shard by campaign_id

---

## 23. Message Delivery Guarantees

### Mnemonic: **"AME"** — **"At-most, At-least, Exactly"** (in order of increasing difficulty)

| Guarantee | Mnemonic | Risk | Use Case |
|-----------|----------|------|----------|
| **A**t-most-once | "**A**t most = might miss" | May lose messages | Logging, metrics |
| At-**l**east-once | "At **L**east = might repeat" | May get duplicates | Most things (+ idempotency) |
| **E**xactly-once | "**E**xpensive but exact" | Most complex | Payments, billing |

### Trick to Remember Order:
**"Most → Least → Exact"** = increasing reliability AND complexity

---

## 24. DNS Record Types

### Mnemonic: **"A for Address, C for Canonical, M for Mail, N for Name Server"**

| Type | What | Memory Aid |
|------|------|-----------|
| **A** | IPv4 address | "**A**ddress → IP" |
| **AAAA** | IPv6 address | "**A**ddress but longer (4 A's = IPv6)" |
| **CNAME** | Alias to another domain | "**C**anonical **Name** → points to real name" |
| **MX** | Mail server | "**M**ail e**X**change" |
| **NS** | Name server | "**N**ame **S**erver" |
| **TXT** | Text record | "**T**e**xt** — verification, SPF" |
| **SRV** | Service location | "**S**e**rv**ice discovery" |

---

## 25. Common Port Numbers

### Mnemonic: **"80 for HTTP, 443 for HTTPS, the rest by fame"**

| Port | Service | Memory Aid |
|------|---------|-----------|
| **22** | SSH | "**22** = Secure Shell" |
| **53** | DNS | "**53** = DNS (5+3=8, DNS has 8 record types? just memorize)" |
| **80** | HTTP | "**80** = web" |
| **443** | HTTPS | "**443** = secure web" |
| **3306** | MySQL | "**3306** = MySQL" |
| **5432** | PostgreSQL | "**5432** = Postgres (5-4-3-2 countdown)" |
| **6379** | Redis | "**6379** = Redis" |
| **9092** | Kafka | "**9092** = Kafka" |
| **27017** | MongoDB | "**27017** = Mongo" |

---

## 26. Powers of 2

### Mnemonic: **"1-2-4-8-16-32-64-128-256-512-1024"**

| Power | Value | Practical Meaning |
|-------|-------|-------------------|
| 2^10 | 1,024 | ≈ 1 **K**ilo (1 KB) |
| 2^20 | 1,048,576 | ≈ 1 **M**ega (1 MB) |
| 2^30 | 1,073,741,824 | ≈ 1 **G**iga (1 GB) |
| 2^40 | ~1.1 × 10^12 | ≈ 1 **T**era (1 TB) |
| 2^50 | ~1.1 × 10^15 | ≈ 1 **P**eta (1 PB) |

### Quick Capacity Math:
```
2^10 ≈ 1 Thousand (K)
2^20 ≈ 1 Million (M)
2^30 ≈ 1 Billion (G)
2^40 ≈ 1 Trillion (T)
```

### Useful Combos:
- **64-bit integer range**: 2^63 ≈ 9.2 × 10^18 (Snowflake IDs fit here)
- **UUID space**: 2^128 ≈ 3.4 × 10^38 (practically infinite)
- **Kafka partitions max**: 4096 IDs/ms = 2^12

---

## 27. Latency Numbers

### Mnemonic: **"Cache < Memory < SSD < Disk < Network < Internet"**

| Operation | Latency | Memory Aid |
|-----------|---------|-----------|
| L1 cache reference | **1 ns** | "Lightning fast" |
| L2 cache reference | **4 ns** | "Still in the chip" |
| Main memory (RAM) | **100 ns** | "**H**undred nanoseconds" |
| SSD random read | **16 μs** | "**S**SD = **S**ixteen micro" |
| HDD random read | **2-10 ms** | "**H**DD = **H**eavy wait" |
| Same datacenter round trip | **0.5 ms** | "Half a millisecond" |
| Redis GET | **< 1 ms** | "Redis = sub-millisecond" |
| Database query | **1-10 ms** | "DB = few milliseconds" |
| Cross-region round trip | **50-150 ms** | "Cross country = 100ms" |
| Internet request | **100-500 ms** | "Internet = hundreds of ms" |

### Interview Shortcut: **"The 1-10-100 Rule"**
- **1 ms**: Cache/Redis/in-memory
- **10 ms**: Database query, same-region service call
- **100 ms**: Cross-region, external API, user-perceived threshold

---

## 🎯 Master Mnemonic Summary

| Topic | Mnemonic |
|-------|----------|
| OSI Layers | **P**lease **D**o **N**ot **T**hrow **S**ausage **P**izza **A**way |
| CAP | **CPA** like an accountant — pick 2 of 3 |
| ACID | **A**ll or nothing, **C**orrect, **I**nvisible, **D**isk survives |
| BASE | Opposite of ACID — **B**asically **A**vailable, **S**oft state, **E**ventual |
| SOLID | **S**ingle, **O**pen/Closed, **L**iskov, **I**nterface, **D**ependency |
| HTTP Codes | **1**-info, **2**-success✌️, **3**-redirect, **4**-your fault, **5**-server fault |
| Interview Framework | **READHD** — Requirements, Entities, API, Data Flow, High-Level, Deep Dives |
| NFRs | **SCALDR** — Scalability, Consistency, Availability, Latency, Durability, Reliability |
| Caching | **AWWRR** — Aside, Write-through, Write-behind, Read-through, Refresh |
| Databases | **RDGKW** — Relational, Document, Graph, Key-Value, Wide-Column |
| Rate Limiting | **TL FSS** — Token, Leaky, Fixed, Sliding Log, Sliding Counter |
| Load Balancing | **RR WLC IR** — Round Robin, Weighted, Least Conn, IP, Random |
| Kafka | **BTPC OCR** — Broker, Topic, Partition, Consumer, Offset, Commit, Replication |
| Scaling | **VRCS** — Vertical, Replicate, Cache, Shard |
| Availability | **2-3-4-5** → **3 days, 8 hours, 52 min, 5 min** |
| Microservices | **SCAR BED** — Saga, Circuit Breaker, API Gateway, Retry, Bulkhead, Event-Driven, Discovery |
| Failure | **FRITES** 🍟 — Failover, Retry, Idempotency, Timeout, Exponential backoff, Shedding |
| Data Structures | **BLUSH QT** — Bloom, LSM, Union-Find, Sorted Set, Hash