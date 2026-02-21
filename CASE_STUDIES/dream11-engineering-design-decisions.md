# Dream11 Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Dream11's engineering blog, conference talks, and tech articles. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Dream11 Engineering Blog (blog.dream11.in), AWS re:Invent talks, Conf42/GopherCon talks, tech articles
>
> **Context**: Dream11 is India's largest fantasy sports platform with 200M+ users. Their biggest challenge: **extreme traffic spikes** during IPL matches — millions of users creating teams and joining contests within a 30-minute window before a match starts.

---

## Table of Contents

1. [Handling IPL-Scale Traffic Spikes](#1-ipl-traffic-spikes)
2. [Microservices on Kubernetes (EKS)](#2-microservices-kubernetes)
3. [Database Strategy — MySQL + Redis + DynamoDB](#3-database-strategy)
4. [Contest Joining — Concurrency at Extreme Scale](#4-contest-joining-concurrency)
5. [Real-Time Leaderboard — Points Calculation](#5-real-time-leaderboard)
6. [Caching Strategy — Multi-Layer with Redis](#6-caching-redis)
7. [Node.js to Go Migration](#7-nodejs-to-go)
8. [Event-Driven Architecture with Kafka](#8-kafka-events)
9. [Payment System — Wallet & Transactions](#9-payment-system)
10. [CDN & API Gateway — Traffic Management](#10-cdn-api-gateway)
11. [Load Testing — Preparing for IPL](#11-load-testing)
12. [Notification System — Millions in Minutes](#12-notification-system)

---

## 1. Handling IPL-Scale Traffic Spikes

**Source**: "Scaling Dream11 for IPL" + AWS re:Invent talks

### The Problem
Dream11's traffic pattern is the **most extreme in Indian tech**: 
- **Normal day**: Moderate traffic
- **IPL match day**: Traffic spikes **100-200×** in the 30 minutes before match start (deadline)
- **IPL Final**: Peak of peaks — millions of concurrent users, tens of millions of API calls per minute
- The spike is **predictable** (match schedule is known) but **massive** (30-minute window)

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Scaling strategy** | Pre-provision before match + auto-scale during | Reactive auto-scaling only | Reactive is too slow for 100× spike; pre-provision based on match importance (IPL Final > league match) |
| **Pre-warming** | Warm up caches, DB connections, DNS, CDN before deadline | Cold start at match time | Cold caches + cold connections + DNS propagation = disaster during spike |
| **Traffic shaping** | Rate limiting + virtual queue at API Gateway | Let all traffic through | Controlled admission prevents backend overload; users wait in queue vs error pages |
| **Architecture** | Stateless microservices + managed databases | Monolith or stateful services | Stateless scales horizontally; managed DB (RDS, DynamoDB) handles spike without DBA intervention |
| **Cloud** | AWS (all-in) | Multi-cloud, on-premise | AWS provides the scale Dream11 needs; deep integration with EKS, RDS, ElastiCache, DynamoDB |

### Pre-Match Preparation Checklist
```
T-24 hours: Scale up EKS nodes, RDS read replicas, ElastiCache nodes
T-6 hours:  Warm caches (popular contests, match data, user profiles)
T-2 hours:  Pre-warm CDN edge caches
T-1 hour:   Scale to peak capacity; enable enhanced monitoring
T-30 min:   Contest joining deadline approaches → peak traffic begins
T-0:        Match starts → traffic drops sharply → scale down gradually
```

### Tradeoff
- ✅ Pre-provisioning handles the 100× spike without user-visible errors
- ✅ Predictable spikes (match schedule known) enable proactive scaling
- ❌ Over-provisioning wastes money during off-peak hours
- ❌ Pre-warming is complex (must warm caches, connections, DNS, CDN)
- ❌ If match is rescheduled/cancelled, pre-provisioned resources are wasted

### Interview Use
> "For systems with predictable extreme traffic spikes (like Dream11 during IPL — 100× normal), I'd pre-provision infrastructure based on event importance rather than relying on reactive auto-scaling. Pre-warm caches, database connections, and CDN edges hours before the spike. Use a virtual queue at the API gateway to shape traffic during the peak. Dream11 handles millions of concurrent users during a 30-minute window using this approach."

---

## 2. Microservices on Kubernetes (EKS)

**Source**: Dream11 engineering talks on their microservice journey

### Problem
Dream11 started as a monolith. As the platform grew to 200M+ users, the monolith couldn't scale independently for different features (contest creation, team management, payments, leaderboard).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Orchestration** | AWS EKS (Managed Kubernetes) | ECS, self-managed K8s | EKS: Kubernetes ecosystem + AWS manages control plane |
| **Service mesh** | Istio (for traffic management, observability) | No mesh, Linkerd | Istio provides canary deployments, circuit breaking, and distributed tracing |
| **Service decomposition** | Domain-driven: Contest Service, Team Service, User Service, Payment Service, Leaderboard Service | Feature-based or random | Domain boundaries are clear and map to team ownership |
| **Communication** | Sync (gRPC for internal) + Async (Kafka for events) | All REST, all async | gRPC for low-latency internal calls; Kafka for decoupled event processing |
| **Deployment** | Blue/green + canary (Istio traffic splitting) | Big-bang deploys | Canary catches regressions before they hit all users; critical during IPL season |

### Tradeoff
- ✅ Independent scaling: leaderboard service scales differently from contest service
- ✅ Independent deployment: update payment service without touching leaderboard
- ✅ Istio provides traffic management and observability out of the box
- ❌ Kubernetes complexity (learning curve, operational overhead)
- ❌ Service-to-service latency (network hops between services)
- ❌ Distributed tracing and debugging are harder than monolith

### Interview Use
> "I'd decompose by domain: Contest Service, Team Service, Payment Service, Leaderboard Service — each scales independently. Internal communication via gRPC (low latency), cross-service events via Kafka (decoupled). Deploy on Kubernetes with Istio for canary deployments — critical when you can't afford regressions during peak events. Dream11 migrated from monolith to microservices on EKS to handle IPL-scale traffic."

---

## 3. Database Strategy

**Source**: Dream11 engineering blog posts on database choices

### Problem
Different data types need different database characteristics: user profiles need strong consistency, contest data needs high read throughput, leaderboard needs sorted access, and session data needs sub-millisecond latency.

### Design Decisions

| Data Type | Database | Why |
|-----------|----------|-----|
| **User profiles, contests (source of truth)** | MySQL (RDS) with read replicas | ACID transactions, relational queries, proven at scale |
| **Session data, caching** | Redis (ElastiCache) | Sub-millisecond, rich data structures (sorted sets for leaderboards) |
| **High-throughput writes (event log, activity)** | DynamoDB | Scales to any write throughput; single-digit ms; fully managed |
| **Search (players, contests)** | Elasticsearch | Full-text search with fuzzy matching |
| **Analytics** | S3 + Athena / Redshift | Cost-effective analytics on event data |

### MySQL Sharding

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Shard key** | user_id for user data, contest_id for contest data | Random | Co-locate related data; user's teams on same shard as user |
| **Read scaling** | RDS read replicas (up to 15) | Application-level caching only | Read replicas handle read-heavy traffic during browsing; write goes to master |
| **Write scaling** | Application-level sharding across multiple RDS instances | Single master with write-through cache | IPL deadline creates massive write spike for contest joining; single master would bottleneck |

### Tradeoff
- ✅ Right database for each access pattern (polyglot persistence)
- ✅ MySQL ACID for financial/contest data (can't afford inconsistency)
- ✅ Redis for hot data (leaderboard, cache) — sub-millisecond
- ✅ DynamoDB for write spikes (auto-scales to any throughput)
- ❌ Multiple databases to operate and monitor
- ❌ Data consistency across databases is eventual (not transactional)
- ❌ Increased operational complexity

### Interview Use
> "I'd use polyglot persistence: MySQL for transactional data (contests, payments — ACID required), Redis for real-time data (leaderboards via sorted sets, caching), DynamoDB for high-throughput event logging. Dream11 uses this combination — MySQL for source of truth, Redis for hot path, DynamoDB for write-heavy event capture. The key is matching the database to the access pattern."

---

## 4. Contest Joining — Concurrency Control

**Source**: Dream11 engineering talks on contest joining architecture

### The Problem
The most critical operation: **joining a contest**. A contest has a fixed number of spots (e.g., 10,000 spots in a mega contest). Millions of users try to join simultaneously in the last minutes before deadline. Must prevent:
- **Overselling**: More users join than available spots
- **Double joining**: Same user joins the same contest twice
- **Payment failures**: User joins but payment fails (must roll back)

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Inventory tracking** | Redis atomic counter (DECR) for available spots | MySQL counter with SELECT FOR UPDATE | Redis DECR is O(1) and atomic; MySQL lock would be a bottleneck at millions of concurrent requests |
| **Double-join prevention** | Redis SET (SETNX) to mark user+contest combination | MySQL unique constraint | Redis is faster for the hot path; MySQL unique constraint as a backup |
| **Flow** | Reserve spot (Redis) → Process payment → Confirm (MySQL) | Payment first, then reserve | Reserve first = fast user feedback; if payment fails, release reservation |
| **Reservation timeout** | 2-minute hold on reserved spot (Redis TTL) | No timeout | If payment fails or times out, spot auto-releases after 2 minutes |
| **Queue** | SQS queue for payment processing (absorbs burst) | Synchronous payment | SQS absorbs the spike; payment workers process at sustainable rate |

### Contest Joining Flow
```
User clicks "Join Contest":
  1. [Redis] DECR contest:{id}:spots → if ≥ 0, spot reserved → else FULL
  2. [Redis] SETNX join:{user_id}:{contest_id} → if success, not a double → else ALREADY JOINED
  3. [Redis] Set 2-minute TTL on reservation
  4. [SQS] Enqueue payment job
  5. Return "Processing..." to user (< 100ms)

Payment Worker (async):
  6. [Payment Gateway] Charge wallet/card
  7. [MySQL] INSERT into contest_entries (transactional with payment record)
  8. [Redis] Confirm reservation (remove TTL)
  9. Notify user: "Joined successfully!" (push notification)

If payment fails:
  10. [Redis] Release reservation: INCR contest:{id}:spots
  11. [Redis] Remove join marker
  12. Notify user: "Payment failed, please retry"

If 2-minute timeout expires (no payment confirmation):
  13. Redis TTL expiry → spot auto-released
  14. [Redis] INCR contest:{id}:spots
```

### Tradeoff
- ✅ Redis DECR handles millions of concurrent spot checks with O(1) atomic operations
- ✅ 2-minute reservation timeout auto-releases abandoned spots
- ✅ Async payment via SQS absorbs the burst without overwhelming payment gateways
- ❌ Brief period where spot is reserved but not yet paid (2-minute window)
- ❌ Redis must not fail during peak (single point of failure for inventory)
- ❌ Eventual consistency: MySQL confirmation happens after Redis reservation

### Interview Use
> "For limited-inventory contention (like Dream11's contest joining with millions of concurrent users), I'd use Redis atomic counter (DECR) for inventory check — O(1), no locks, handles millions/sec. Reserve the spot in Redis with a 2-minute TTL, enqueue payment to SQS (absorbs burst), and confirm in MySQL after payment succeeds. If payment fails, the Redis TTL auto-releases the spot. This is the pattern Dream11 uses for IPL mega contests."

---

## 5. Real-Time Leaderboard — Points Calculation

**Source**: Dream11 engineering blog on leaderboard architecture

### The Problem
During a live cricket match, as each ball is bowled, points change for millions of fantasy teams. The leaderboard must update in near real-time for every contest (some contests have millions of participants). Users constantly refresh to check their rank.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Points computation** | Event-driven: ball event → Kafka → compute service → update all affected teams | Batch (compute after each over) | Users expect near real-time updates (within seconds of each ball) |
| **Leaderboard storage** | Redis Sorted Set (ZADD for score update, ZREVRANK for rank) | MySQL ORDER BY score | Redis ZSET: O(log N) per update, O(log N) per rank query; handles millions of members |
| **Score update strategy** | Incremental: delta score per ball event | Full recompute after each ball | Incremental is much cheaper; only update affected players' scores |
| **Serving** | Pre-compute top 100 (cached) + on-demand rank for individual user | Compute everything on demand | Top 100 is the most viewed; individual rank computed per request (ZREVRANK) |
| **Batch reconciliation** | After each over: full recompute to correct any drift | Trust incremental updates only | Incremental may drift due to missed events; periodic reconciliation ensures accuracy |

### Architecture
```
Ball bowled (from live feed API):
  1. Cricket data provider → Kafka topic "match_events"
  2. Points Calculation Service (Kafka consumer):
     a. Lookup which players are affected
     b. Calculate delta points for each affected player
     c. For each contest:
        - Find all teams containing affected players
        - ZINCRBY leaderboard:{contest_id} {delta} {team_id}
  3. Top-100 cache updated every 5 seconds (Redis → CDN-cached)
  4. Individual rank: user queries → ZREVRANK (< 1ms)

Example:
  Virat Kohli scores a six (+6 batting points)
  → 2M teams contain Kohli → 2M ZINCRBY operations
  → Distributed across Redis cluster → completes in < 2 seconds
```

### Tradeoff
- ✅ Redis Sorted Set: O(log N) per update and rank query — handles millions of members
- ✅ Incremental updates: only process delta, not full recompute
- ✅ Near real-time: leaderboard updates within seconds of each ball
- ❌ 2M ZINCRBY operations per ball for a popular player (heavy Redis load)
- ❌ Incremental can drift — requires periodic reconciliation
- ❌ Redis cluster must handle peak load during match (pre-provision)

### Interview Use
> "For a real-time leaderboard with millions of participants, I'd use Redis Sorted Sets — ZINCRBY for O(log N) score updates, ZREVRANK for O(log N) rank queries. When a score event occurs, compute the delta and incrementally update only affected entries. Pre-compute and cache the top 100 (most viewed). Dream11 updates leaderboards for millions of teams within seconds of each ball during IPL matches."

---

## 6. Caching Strategy — Multi-Layer with Redis

**Source**: Dream11 engineering blog on caching

### Problem
During IPL peak, database queries would be overwhelmed without aggressive caching. But cache must be fresh — contest data, match data, and user data change frequently.

### Design Decisions

| Layer | What's Cached | TTL | Invalidation |
|-------|-------------|-----|-------------|
| **CDN (CloudFront)** | Static assets, match schedules, player lists | 1 hour | Versioned URLs |
| **API Gateway cache** | Popular API responses (match list, trending contests) | 30 seconds | TTL-based |
| **Application Redis** | Contest details, user profiles, match data | 5-60 seconds | Event-driven (Kafka) + TTL safety net |
| **Local (in-process)** | Hot config, feature flags, static reference data | 5 minutes | Periodic refresh |
| **Leaderboard Redis** | Sorted sets for all active contests | No TTL (live during match) | Updated in real-time via incremental score events |

### Cache Warming Before Match
```
T-2 hours before match deadline:
  1. Fetch all contest data → populate Redis
  2. Fetch all player data → populate Redis
  3. Pre-compute contest metadata (spots remaining, entry fee tiers)
  4. Warm CDN edges with match page assets
  5. Verify cache hit rates (target: > 95%)
```

### Interview Use
> "I'd use multi-layer caching: CDN for static content, API Gateway cache for trending API responses (30s TTL), Redis for application data (5-60s TTL with Kafka event invalidation), and in-process cache for hot config. Before a peak event (like IPL match), pre-warm all layers. Dream11 targets 95%+ cache hit rate during peak — without this, the database would be overwhelmed by millions of requests."

---

## 7. Node.js to Go Migration

**Source**: Dream11 engineering blog on Go migration + GopherCon talks

### Problem
Dream11's backend was originally Node.js. During IPL spikes, Node.js's single-threaded model and garbage collection pauses caused latency spikes. Needed better CPU utilization and predictable latency at high load.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Go (Golang) | Java, Rust, stay on Node.js | Go: simple, fast compilation, goroutines for concurrency, low GC pauses, small memory footprint |
| **Migration strategy** | Gradual service-by-service (strangler fig) | Big-bang rewrite | Rewrite high-traffic services first; keep low-traffic services on Node.js |
| **First services migrated** | Contest joining, leaderboard (highest traffic) | Random selection | Migrate the bottleneck services first for maximum impact |
| **Concurrency** | Go goroutines (lightweight threads) | Node.js async/await | Goroutines: true parallelism on multi-core; Node.js is single-threaded per process |
| **Memory** | Go uses ~50% less memory than Node.js for same workload | Accept higher memory | Significant cost savings at Dream11's scale (thousands of containers) |

### Results of Migration
- **Latency**: P99 reduced by 60% (predictable, no GC pauses)
- **CPU utilization**: Better multi-core usage (goroutines vs single-threaded Node.js)
- **Memory**: ~50% reduction per service instance
- **Cost**: Fewer containers needed for same throughput → significant AWS cost savings
- **Developer experience**: Go's simplicity, fast compile times, strong stdlib

### Tradeoff
- ✅ 60% latency reduction, 50% memory reduction
- ✅ Better CPU utilization (goroutines use all cores)
- ✅ Predictable latency (Go's GC is < 1ms)
- ❌ Migration cost (rewriting services takes time)
- ❌ Smaller Go talent pool in India (improving rapidly)
- ❌ Go's error handling is verbose (compared to Node.js try/catch)

### Interview Use
> "Dream11 migrated from Node.js to Go for their highest-traffic services — contest joining and leaderboard. Go's goroutines provide true parallelism (vs Node.js's single-threaded model), GC pauses are < 1ms (vs Node.js's occasional long pauses), and memory usage dropped 50%. The lesson: for latency-sensitive, CPU-intensive services at scale, compiled languages (Go, Rust, Java) outperform interpreted languages (Node.js, Python)."

---

## 8. Event-Driven Architecture with Kafka

**Source**: Dream11 engineering blog on event architecture

### Problem
When a ball is bowled in a cricket match, dozens of downstream systems need to react: update points, refresh leaderboards, trigger notifications, update analytics, and feed the live score ticker. Can't have each system polling for updates.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Event bus** | Kafka (MSK — managed) | SQS, RabbitMQ, Kinesis | High throughput, multiple consumer groups, replay capability, Kafka ecosystem |
| **Key events** | match_ball_event, contest_joined, payment_completed, team_created | Single "all events" topic | Separate topics per event type; consumers subscribe to what they need |
| **Partition key** | match_id for match events, contest_id for contest events | Random | All events for one match on one partition → ordered processing per match |
| **Consumer groups** | Independent per service: leaderboard, notifications, analytics, audit | Shared consumers | Each service processes independently at its own pace |
| **Delivery** | At-least-once + idempotent consumers | Exactly-once | At-least-once is simpler; consumers handle duplicates via idempotent processing |

### Event Flow for a Ball
```
Cricket data provider → match_ball_event → Kafka:
  Consumer Group "leaderboard": Update Redis sorted sets for all affected contests
  Consumer Group "notifications": Send "Wicket!" push to engaged users  
  Consumer Group "live-score": Update live score feed
  Consumer Group "analytics": Log to S3/Redshift for post-match analytics
  Consumer Group "audit": Write to DynamoDB for compliance
```

### Interview Use
> "I'd use Kafka as the central event bus — one ball event in a cricket match triggers leaderboard updates, notifications, live scores, and analytics, all as independent consumer groups. Kafka gives us ordering per match (partition by match_id), replay capability (if a consumer falls behind), and decoupled services. Dream11 processes millions of events during a single IPL match through this event-driven architecture."

---

## 9. Payment System

**Source**: Dream11 engineering blog on fintech architecture

### Problem
Dream11 processes millions of financial transactions during IPL matches: contest entry fees (debits from wallet), prize distributions (credits to wallet), and withdrawals (bank transfers). Must be accurate to the penny — it's real money.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Wallet model** | In-app wallet (pre-loaded balance) | Direct payment per transaction | Wallet: instant debit during peak (no external payment gateway latency); top-up happens pre-match |
| **Transaction model** | Double-entry bookkeeping | Simple credit/debit | Every money movement has a debit and credit entry — always balanced; required for regulatory compliance |
| **Consistency** | Strong (MySQL transactions for wallet operations) | Eventual consistency | Financial data must be exactly correct — no eventual consistency for money |
| **Idempotency** | Idempotency key per transaction | Retry without protection | Network retries during peak must not cause double-charges |
| **Queue** | SQS for payment processing (absorbs contest-joining burst) | Synchronous | Burst of millions of contest joins → SQS absorbs → payment workers process at sustainable rate |
| **Amounts** | Stored as integers (paise/cents) | Floating-point | Avoid floating-point rounding errors in financial calculations |

### Tradeoff
- ✅ Wallet model: instant debit during peak (no external gateway latency)
- ✅ Double-entry bookkeeping: always balanced, audit-ready
- ✅ SQS absorbs the contest-joining payment burst
- ❌ Wallet requires users to pre-load (friction for new users)
- ❌ Strong consistency for every wallet operation limits throughput
- ❌ Regulatory complexity (RBI compliance for wallet operations)

### Interview Use
> "For a high-throughput payment system with extreme spikes, I'd use an in-app wallet model — users pre-load balance, so contest joining is an instant internal wallet debit (no external payment gateway latency during peak). Double-entry bookkeeping for financial accuracy, idempotency keys for retry safety, and SQS to absorb payment processing bursts. Dream11 processes millions of wallet transactions during IPL matches using this pattern."

---

## 10. CDN & API Gateway

**Source**: Dream11 engineering blog on traffic management

### Problem
During IPL peak, millions of API requests per minute hit Dream11's infrastructure. Need to protect backends from being overwhelmed while maintaining fast response times.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **CDN** | CloudFront (for static + API caching) | No CDN, direct to servers | CDN caches match data, player lists, contest catalogs — reduces origin load by 80%+ |
| **API Gateway** | AWS API Gateway + custom rate limiting | Direct to microservices | Rate limiting, auth, throttling at the edge before requests hit services |
| **Rate limiting** | Per-user + per-IP + per-endpoint | Global rate limit only | Granular: limit contest joins per user, don't limit browsing |
| **Response caching** | API Gateway caches GET responses (10-30s TTL) | No API response caching | Identical requests from millions of users (same match, same contest list) served from cache |
| **Compression** | Gzip/Brotli at CDN edge | No compression | Reduces payload size by 60-80% → faster load on mobile networks (critical in India) |

### Interview Use
> "During extreme traffic spikes, I'd layer defenses: CDN caches static + semi-static content (80%+ of read traffic), API Gateway caches popular API responses (10-30s TTL for match data), and per-user rate limiting prevents abuse. Brotli compression at the edge reduces payload 60-80% — critical for mobile users on slow networks. Dream11 uses this layered approach to handle IPL peak traffic."

---

## 11. Load Testing — Preparing for IPL

**Source**: Dream11 engineering talks on load testing strategy

### Problem
You can't wait for IPL to discover your system can't handle the load. Need to simulate IPL-scale traffic in advance and fix bottlenecks before the actual event.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Load testing** | Regular load tests at 2× expected peak traffic | Test at 1× expected peak | 2× gives headroom; real traffic patterns are unpredictable |
| **Traffic simulation** | Realistic user journeys (browse → select players → join contest → check leaderboard) | Simple HTTP flood | Realistic journeys find bottlenecks in the actual user flow, not just raw throughput |
| **Environment** | Production-like environment (same instance types, same DB sizes) | Staging with smaller instances | Staging doesn't reproduce production bottlenecks (disk I/O, network, connection limits) |
| **Chaos** | Inject failures during load test (kill nodes, add latency) | Load test only, no failures | Discover how the system degrades under load + failure simultaneously |
| **Metrics** | Track P99 latency, error rate, DB connection count, Redis memory, Kafka lag | Average latency only | P99 matters more than average; connection counts and Kafka lag are leading indicators |

### Load Test Cadence
```
Weeks before IPL season:
  - Weekly load tests at 1.5× expected peak
  - Fix bottlenecks found
  
Days before IPL:
  - Full-scale test at 2× expected peak
  - Chaos injection (kill 20% of pods, add network latency)
  - Validate auto-scaling, circuit breakers, fallbacks
  
Match day:
  - Pre-provision based on load test validated capacity
  - Real-time monitoring dashboards
  - War room with on-call engineers
```

### Interview Use
> "Before a major traffic event, I'd run load tests at 2× expected peak with realistic user journeys — not just HTTP floods. Test in a production-like environment and inject failures (kill pods, add latency) during the load test to validate resilience under combined load + failure. Dream11 runs weekly load tests before IPL season, targeting 2× peak, which is why they handle millions of concurrent users on match day."

---

## 12. Notification System

**Source**: Dream11 engineering blog on push notifications

### Problem
Send millions of push notifications within minutes — match starting reminders, contest fill alerts, score updates, prize distributions. During IPL, notification volume spikes 50×.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Event-driven: Kafka event → Notification Service → FCM/APNS | Direct push from each service | Centralized: all services publish events, Notification Service handles delivery |
| **Batching** | Batch notifications to FCM/APNS (1000 per API call) | Individual API calls | 1000× fewer API calls to Google/Apple |
| **Priority** | Match-related > Contest-related > Marketing | Single queue | Match starting notifications must arrive before deadline |
| **User preferences** | Per-notification-type (match reminders ON, marketing OFF) | Global on/off | Users want match reminders but not promotional pushes |
| **Rate limiting** | Max 10 notifications per user per day | Unlimited | Prevent notification fatigue — users disable notifications if spammed |
| **Dedup** | Don't re-send same notification within 5 minutes | No dedup | Retry storms during peak could spam users |

### Interview Use
> "For a notification system with extreme spikes (millions in minutes), I'd use Kafka as the event bus → Notification Service → batch to FCM/APNS (1000 per API call). Priority queues ensure match-related notifications arrive before marketing. Per-user daily cap (10/day) prevents fatigue. Dream11 sends millions of notifications in the 30-minute window before each IPL match deadline."

---

## 🎯 Quick Reference: Dream11's Key Decisions

### Traffic & Scaling
| Challenge | Solution |
|-----------|---------|
| 100× traffic spikes (IPL) | Pre-provision + auto-scale + cache warming |
| Millions of concurrent users | Stateless microservices on EKS, managed databases |
| Contest joining (limited inventory) | Redis DECR (atomic counter) + 2-min reservation + SQS payment queue |
| API overwhelm | CDN caching (80%+), API Gateway caching, per-user rate limiting |

### Data
| Data Type | Database | Why |
|-----------|----------|-----|
| Contests, users (source of truth) | MySQL (RDS, sharded) | ACID, relational |
| Leaderboards | Redis Sorted Sets | O(log N) rank/update, real-time |
| Session, cache | Redis (ElastiCache) | Sub-millisecond |
| Event log, activity | DynamoDB | Write-heavy, auto-scales |
| Payments | MySQL (strict ACID, double-entry) | Financial accuracy |

### Architecture
| Decision | Choice | Why |
|----------|--------|-----|
| Language | Node.js → Go (hot services) | 60% latency reduction, 50% memory savings |
| Event bus | Kafka (MSK) | Multiple consumers, replay, high throughput |
| Payments | In-app wallet + SQS queue | Instant debit, absorbs burst |
| Testing | 2× peak load tests weekly before IPL | Find bottlenecks before production |

---

## 🗣️ How to Use Dream11 Examples in Interviews

### Example Sentences
- "For limited-inventory contention, I'd use Redis atomic DECR — like Dream11's contest joining. O(1), no locks, millions per second."
- "Dream11 pre-provisions infrastructure 24 hours before IPL matches — for predictable spikes, proactive scaling beats reactive auto-scaling."
- "For real-time leaderboards with millions of entries, Redis Sorted Sets (ZINCRBY + ZREVRANK) — O(log N) per operation. Dream11 updates millions of team scores within seconds of each ball."
- "Dream11 migrated from Node.js to Go — 60% latency reduction, 50% less memory. For high-throughput, latency-sensitive services, compiled languages win."
- "In-app wallet model avoids external payment gateway latency during peak — instant internal wallet debit. Dream11 uses this for IPL contest joining."
- "Load test at 2× expected peak with realistic user journeys and chaos injection — Dream11 runs these weekly before IPL season."
- "Multi-layer caching: CDN (80% of reads) → API Gateway cache (30s TTL) → Redis (5-60s) → DB. Dream11 targets 95%+ cache hit rate during IPL."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 12 design decisions focused on extreme traffic handling, real-time systems, and Indian tech scale  
**Status**: Complete & Interview-Ready ✅
