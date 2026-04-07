# 🔬 System Design Deep Dives — Complete Cheatsheet (40 Topics)

> **Purpose**: When the interviewer says "let's go deeper on that", deliver a structured mini-essay from this collection. Each deep dive is a reusable module you can plug into ANY HLD interview.
>
> **Format**: Each deep dive covers: The Problem → The Solution → How It Works → Key Details → Numbers → Talking Point.
>
> **Coverage**: 40 deep dives across infrastructure, data, real-time, security, scaling, and operational patterns.

---

## Quick Reference: Which Deep Dive for Which Problem?

| Deep Dive | Best For |
|-----------|----------|
| 1. Hybrid Fan-Out | Twitter, Instagram, any social feed |
| 2. Distributed Rate Limiting | Any API, rate limiter design |
| 3. Snowflake ID Generation | Twitter, chat, any distributed ID need |
| 4. Seat Reservation / Double-Selling | Ticket booking, hotel, flight, flash sale |
| 5. Virtual Queue for Flash Sales | Ticket drops, sneaker launches, GPU sales |
| 6. End-to-End Encryption | WhatsApp, Signal, secure messaging |
| 7. WebSocket at Scale | Chat, notifications, live dashboards |
| 8. Exactly-Once Dedup | Ad clicks, payments, billing pipelines |
| 9. Multi-Window Stream Aggregation | Ad analytics, metrics, monitoring |
| 10. Trending / Top-K | Twitter trending, popular products, leaderboards |
| 11. Saga Pattern | Payment flows, multi-service booking |
| 12. Consistent Hashing | Distributed cache, any sharded system |
| 13. Backpressure & Load Shedding | Event processing, real-time pipelines |
| 14. Database Sharding | Any system needing horizontal scaling |
| 15. CDN & Media Pipeline | Instagram, Netflix, YouTube |
| 16. Distributed Job Scheduling | Cron at scale, workflow orchestration |
| 17. Bloom Filter Dedup | Web crawler, event dedup, URL tracking |
| 18. Batch Reconciliation | Ad billing, financial systems, inventory |
| 19. Anomaly Detection | Ad analytics, fraud detection, monitoring |
| 20. Group Message Fanout | WhatsApp groups, Slack, Discord |
| 21. Search Index Pipeline | Twitter search, product search, log search |
| 22. Presence / Online Status | WhatsApp, Slack, gaming |
| 23. Notification Aggregation | Instagram, Facebook, any social app |
| 24. Geo-Proximity Search | Uber, Yelp, Tinder |
| 25. Leader Election & Fencing | Job schedulers, distributed locks |
| 26. Circuit Breaker Pattern | Microservice resilience |
| 27. CQRS / Event Sourcing | Order systems, audit-heavy domains |
| 28. Data Migration (Zero Downtime) | Database migration, schema changes |
| 29. Conflict Resolution (Multi-Master) | Multi-region writes, collaborative editing |
| 30. Content-Addressable Caching | Build systems (Bazel), package managers |
| 31. Adaptive Bitrate Streaming | Netflix, YouTube, live streaming |
| 32. OAuth / SSO Token Flow | SSO login, API auth |
| 33. Distributed Counter at Scale | Like counts, view counts, inventory |
| 34. Autocomplete / Typeahead | Search bars, address input |
| 35. Spell Correction | Search engines, chat |
| 36. Feature Flags | A/B testing, gradual rollout |
| 37. Dead Letter Queues | Any async pipeline error handling |
| 38. Database Connection Pooling | High-throughput APIs |
| 39. Blue-Green / Canary Deployments | Zero-downtime deploys |
| 40. Idempotency Patterns | Payments, any retry-safe API |

---

## 1. Hybrid Fan-Out (The Celebrity Problem)

**Applies to**: Twitter, Instagram, any social feed

**Problem**: Celebrity with 10M followers tweets → pure push = 10M Redis writes (~100s). Pure pull = 500 DB queries per timeline read.

**Solution**: Push for normal users (< 1M followers), pull for celebrities at read time.

**How it works**: On tweet, check follower count. If < 1M: `ZADD timeline:{follower_id} {ts} {tweet_id}` for each follower. If ≥ 1M: skip. On read: fetch pre-computed timeline from Redis + query celebrity tweets from DB + merge by timestamp.

**Numbers**: Read = 1 Redis call + 5-10 DB calls (celebrities) + in-memory merge. Total < 50ms. Fanout budget drops 40% from celebrity exclusion.

**Talking Point**: "I'd use a hybrid approach with a threshold of ~1M followers. For 99% of users, we push to followers' timelines on write. For celebrities, we skip the fanout and merge their tweets at read time. This prevents the 10-million-write storm when a celebrity tweets."

---

## 2. Distributed Rate Limiting (Token Bucket in Redis)

**Applies to**: Any API, abuse prevention

**Problem**: Multiple API servers must enforce shared rate limit. Local tracking allows users to exceed limits by spreading requests.

**Solution**: Token Bucket in Redis with Lua script for atomicity. `ratelimit:{user_id}:{action}` hash with tokens + last_refill.

**Key detail**: Lua script calculates tokens to add since last refill, caps at max, attempts to consume — all atomic. Without Lua, two concurrent requests both read tokens=1 and both succeed.

**Talking Point**: "I'd implement Token Bucket in Redis with a Lua script for atomicity — the check-and-decrement must be a single atomic operation, otherwise two concurrent requests could both read tokens=1 and both succeed."

---

## 3. Snowflake ID Generation

**Applies to**: Twitter, messaging, any sortable distributed IDs

**Problem**: Need globally unique IDs that are time-sortable, compact (64-bit), and generated without coordination.

**Solution**: 64-bit = {1 unused}{41 bits timestamp}{10 bits machine_id}{12 bits sequence}. 69 years of IDs, 1024 machines, 4096 IDs/ms/machine.

**Why not UUID?**: 128 bits wastes index space, random distribution kills B-tree locality, not sortable.

**Talking Point**: "Each machine generates 4096 IDs per millisecond independently. The timestamp prefix means IDs are roughly chronological — we can sort by ID instead of maintaining a separate timestamp index."

---

## 4. Seat Reservation & Preventing Double-Selling

**Applies to**: Ticket booking, hotel, flight

**Problem**: Two users select same seat simultaneously → both succeed → oversold.

**Solution**: Two-layer locking. Redis `SETNX` (coarse, absorbs thundering herd) + DB `SELECT FOR UPDATE` (fine, correctness guarantee). Hold with expiry (10 min TTL, DB polling every 30s as safety net).

**Talking Point**: "Redis handles the thundering herd (500K concurrent users → 1 gets through), DB pessimistic lock guarantees correctness. Two-tier hold expiration: Redis key TTL for speed, DB polling as safety net."

---

## 5. Virtual Queue for Flash Sales

**Applies to**: Flash sales, sneaker drops, GPU launches

**Problem**: 500K users hit "Buy" simultaneously → server crash.

**Solution**: Redis sorted set as FIFO queue. `ZADD queue:{event} {ts} {user_id}`. Admission worker: `ZPOPMIN 1000` every 10s. Admitted users get time-limited token (5-min TTL). Position via `ZRANK`.

**Talking Point**: "The queue decouples demand from capacity — 500K users waiting in queue, but only 1000 active at any time. No server crush, fair FIFO ordering."

---

## 6. End-to-End Encryption (Signal Protocol)

**Applies to**: WhatsApp, Signal

**Problem**: Server must relay messages but not read them. Even if server is compromised, past messages must be safe.

**Solution**: X3DH key exchange + Double Ratchet. Per-session AES-256-GCM. Server sees encrypted blobs only. Perfect forward secrecy — old keys deleted.

**Tradeoffs**: ✅ Privacy guarantee. ❌ No server-side search, no content moderation, complex multi-device sync, hard message backup/recovery.

**Talking Point**: "The server only sees encrypted blobs — even with a court order, we can't produce message content. The tradeoff is we can't do server-side search or content moderation."

---

## 7. WebSocket at Scale (Millions of Connections)

**Applies to**: Chat, real-time notifications, live dashboards

**Problem**: 500M concurrent persistent connections. Thread-per-connection doesn't scale.

**Solution**: Event-driven I/O (epoll). Gateway servers (65K connections each). Redis connection registry `session:{user_id}:{device}` → `{gateway, conn_id}` with 70s TTL. Cross-server routing via Redis Pub/Sub.

**Presence**: Heartbeat every 30s → refresh Redis TTL. No heartbeat for 70s → key expires → offline.

**Talking Point**: "Each gateway handles 65K connections using non-blocking I/O. Redis tracks which user is on which server. To deliver a message, lookup the gateway in Redis and push via Pub/Sub."

---

## 8. Exactly-Once Event Processing (Dedup at Scale)

**Applies to**: Ad click counting, payment processing

**Problem**: At 2.3M events/sec, 0.1% duplication = millions in overbilling.

**Solution**: Two-tier dedup. Bloom filter (< 1μs, 0.01% false positive) → Redis SETNX (< 1ms, exact). Bloom handles 99.99%, Redis verifies the 0.01%.

**End-to-end**: Dedup layer + Flink checkpointing (30s) + Kafka transactions + crash recovery from checkpoint.

**Talking Point**: "Bloom filter handles 99.99% of events in < 1 microsecond with zero false negatives. The 0.01% that hit Redis are verified exactly. Total: 100% accuracy at billions of events/day."

---

## 9. Multi-Window Stream Aggregation

**Applies to**: Ad analytics, metrics dashboards

**Problem**: Aggregate at minute/hour/day simultaneously, serve each from different storage tier.

**Solution**: Flink with 3 windows. Every 10s flush partials to Redis (minute). Every minute complete to Redis (final). Every hour to ClickHouse SSD. Every day to ClickHouse HDD. Query router merges across tiers.

**Talking Point**: "A single Flink pipeline produces minute, hour, and day aggregates simultaneously. Each window type flushes to a different storage tier. The query router transparently merges across tiers."

---

## 10. Trending / Top-K Calculation

**Applies to**: Twitter trending, popular products

**Problem**: Not "most popular" (that's always weather). Trending = unusual spike relative to baseline.

**Solution**: `trend_score = (current_volume / 7_day_avg) × sqrt(unique_users) × time_decay`. Update Redis sorted set every 5 min. `ZREVRANGE trending:global 0 49` for top 50.

**Why sqrt(unique_users)?**: Prevents bot manipulation. 1000 tweets from 1 bot scores sqrt(1)=1, from 1000 users scores sqrt(1000)=31.6.

**Talking Point**: "The trend score divides current volume by the 7-day average — a hashtag needs to be significantly above its baseline to trend. Diversity factor prevents bot manipulation."

---

## 11. Saga Pattern for Distributed Transactions

**Applies to**: Payment flows, booking flows

**Problem**: Booking involves reserve seat → charge payment → issue ticket. If payment fails, need to roll back. 2PC is slow and fragile.

**Solution**: Sequence of local transactions with compensating actions. Orchestrated (one coordinator) or choreographed (event-driven). Every step + compensation must be idempotent.

**Talking Point**: "I'd use an orchestrated saga — the Reservation Service drives the flow: reserve → charge → confirm → issue. If payment fails at step 2, it calls the step 1 compensation (release seats). Every step is idempotent."

---

## 12. Consistent Hashing for Cache Distribution

**Applies to**: Distributed cache, any sharded system

**Problem**: `hash(key) % N` → adding a node remaps ~100% of keys → cold cache catastrophe.

**Solution**: Hash ring. Adding a node remaps only ~1/N of keys. Virtual nodes (150 per server) ensure even distribution.

**Talking Point**: "Adding a node to a 10-node cluster only remaps ~10% of keys instead of ~100%. Virtual nodes ensure even distribution. We can scale the cache without a thundering herd on the database."

---

## 13. Backpressure & Load Shedding Under Spike

**Applies to**: Event processing, any high-throughput pipeline

**Problem**: Traffic spikes 5-10× during flash events. Consumers can't keep up.

**Solution**: Priority-based shedding. Level 1: auto-scale. Level 2: sample low-priority at 10%, apply correction factor. Level 3: only process high-value events. Clicks (revenue) always at 100%.

**Talking Point**: "Under backpressure, clicks are always processed — they represent revenue. Impressions are sampled at 10% with a correction factor. This preserves billing accuracy while preventing pipeline lag."

---

## 14. Database Sharding Strategy

**Applies to**: Any system at scale needing horizontal DB scaling

**Problem**: Single DB can't handle write throughput. Need to split across instances.

**Key decisions**: Shard key must ensure even distribution + query locality. Ticket booking: shard by event_id (all seats on same shard → single-node transactions). Chat: shard by conversation_id.

**Cross-shard handling**: Best: design so queries never cross shards. If needed: scatter-gather. For joins: denormalize.

**Talking Point**: "I'd shard by event_id — the entire booking transaction executes within one shard. No distributed transactions needed."

---

## 15. CDN & Media Processing Pipeline

**Applies to**: Instagram, Netflix, any media-heavy system

**Problem**: Billions of images/videos served globally.

**Solution**: Client → presigned URL → direct S3 upload → async processing (thumbnails, transcode, compress) → serve via CDN. Content-addressable URLs eliminate cache invalidation.

**Numbers**: 95% CDN cache hit ratio → 20× reduction in origin load. Latency: 15ms (edge) vs 200ms (origin).

**Talking Point**: "Users upload directly to S3 via presigned URL — our backend never touches the bytes. CDN serves everything with 95% hit rate."

---

## 16. Distributed Job Scheduling

**Applies to**: Cron at scale, workflow orchestration

**Problem**: Millions of scheduled jobs, at-least-once execution, no single point of failure.

**Solution**: Leader election (ZooKeeper) for scheduler. Redis sorted set `ZADD jobs {exec_time} {job_id}`. Workers pull from queue. Heartbeat → if missed, reassign. Visibility timeout (SQS pattern). Jobs must be idempotent.

**Talking Point**: "The scheduler leader scans due jobs with `ZRANGEBYSCORE`. Workers are stateless. If a worker dies, heartbeat timeout triggers reassignment. Jobs must be idempotent because at-least-once means they may run twice."

---

## 17. Bloom Filter for URL/Event Dedup

**Applies to**: Web crawler, event dedup

**Properties**: Zero false negatives, tunable false positives. ~1.5 GB for 10B items at 0.1% FP. O(1) operations.

**Rotation for streaming**: Two filters — current + previous. Rotate hourly. Check both.

**Talking Point**: "The Bloom filter checks 'have I seen this URL?' in < 1 microsecond with zero false negatives. 1.5 GB for 10B URLs — 1000× cheaper than storing all URLs in a hash set."

---

## 18. Batch Reconciliation (Stream Accuracy)

**Applies to**: Ad billing, financial systems

**Problem**: Streaming may drift from truth due to late events, sampling, or edge cases.

**Solution**: Nightly Spark job reads ALL raw events from S3, recomputes exact aggregates, compares to streaming, corrects discrepancies > 0.1%. Typically 99.98% match.

**Talking Point**: "The stream gives us speed (< 30s), the batch gives us truth. For billing, we always use batch-reconciled numbers."

---

## 19. Anomaly Detection on Real-Time Metrics

**Applies to**: Monitoring, fraud detection

**Solution**: Z-score on rolling stats (Welford's online algorithm). `z = (current - mean) / stddev`. z > 3 = WARNING, z > 5 = CRITICAL. Memory: O(1) per entity.

**Talking Point**: "Each minute-level aggregate is fed into a per-campaign rolling stats tracker. If the Z-score exceeds 3 standard deviations, we fire an alert — all in real-time with zero manual thresholds."

---

## 20. Group Message Fanout (Chat)

**Applies to**: WhatsApp groups, Slack, Discord

**Problem**: Message to 256 members. Sequential = 12.8s.

**Solution**: Store message once (Cassandra by group_id). Classify online/offline. Online: parallel WebSocket push (~30ms each). Offline: batch push notification (100 per FCM call). Total: ~100ms for 256 members.

**Talking Point**: "The message is stored once — fanout is just notification routing. Online members get it via WebSocket in ~30ms. Offline members get a push notification. Total: ~100ms."

---

## 21. Search Index Pipeline (Async Indexing)

**Applies to**: Tweet search, product search

**Problem**: Search index must stay in sync with source of truth without coupling the write path.

**Solution**: Write to DB → CDC (Debezium) → Kafka → Search Indexer consumer → Elasticsearch bulk API (1000 docs/batch, 10K docs/sec). Index is a derived view — can always rebuild from source.

**Talking Point**: "Tweets write to Cassandra first, then a Kafka consumer indexes them in Elasticsearch. Search lag is ~1-2 seconds, but the write path is completely decoupled from search."

---

## 22. Presence / Online Status

**Applies to**: WhatsApp, Slack, gaming

**Solution**: Redis key `online:{user_id}` with 70s TTL. Client heartbeats every 30s refresh TTL. No heartbeat → key expires → offline. `last_seen:{user_id}` set on expiry. Batch check: `MGET online:{id1} online:{id2}...` — 500 contacts in < 2ms.

**Talking Point**: "Presence is a Redis key with TTL. Heartbeat refreshes it. If the client crashes, the key auto-expires and the user shows offline. 500 contacts checked in < 2ms via MGET."

---

## 23. Notification Aggregation

**Applies to**: Instagram, Facebook

**Problem**: Viral post gets 10K likes/hour → 10K individual push notifications = fatigue.

**Solution**: Time-windowed aggregation. First like → immediate notification. Subsequent likes within 5-min window → batched into "Alice and 47 others liked your post". Redis sorted set for accumulation, timer triggers batch.

**Talking Point**: "Instead of 10K individual notifications, I'd aggregate: 'Alice and 49 others liked your photo'. A 5-minute window collects events; the batch fires when the window closes or the count exceeds a threshold."

---

## 24. Geo-Proximity Search

**Applies to**: Uber (find nearby drivers), Yelp, Tinder

**Problem**: "Find all drivers within 5km of this location" — can't scan all drivers.

**Solution**: Geohash-based indexing. Encode (lat, lng) → geohash string. Same prefix = nearby. Store in Redis sorted set `ZRANGEBYLEX drivers:geohash {prefix}{min} {prefix}{max}`. Or use PostGIS / Elasticsearch geo_distance.

**Alternative**: Quadtree or S2 cells (Google Maps approach).

**Talking Point**: "I'd use geohash-based indexing in Redis. Nearby locations share the same geohash prefix, so finding drivers within 5km is a prefix range query — O(log N + K) where K is the number of nearby drivers."

---

## 25. Leader Election & Fencing Tokens

**Applies to**: Job schedulers, distributed locks, coordination

**Problem**: Only one instance should process a task. If leader crashes, need re-election. Stale leaders must not corrupt data.

**Solution**: ZooKeeper/etcd for leader election. Fencing tokens: each election generates monotonically increasing token. Storage layer rejects writes with token ≤ last-seen.

**Talking Point**: "Fencing tokens prevent the stale-leader problem. Each lock acquisition generates an increasing token. The storage layer rejects writes from old tokens. No split-brain, no duplicate processing."

---

## 26. Circuit Breaker Pattern

**Applies to**: Microservice resilience

**States**: CLOSED (normal) → OPEN (fast fail, no calls for 30s) → HALF-OPEN (one test request) → CLOSED or back to OPEN.

**Trigger**: 5 consecutive failures → open. After 30s timeout → half-open. If test succeeds → close. If test fails → open for another 30s.

**Talking Point**: "If the Recommendation Service times out, the circuit breaker trips — the Feed Service immediately returns chronological feed instead. After 30s, one test request probes recovery. This prevents a slow dependency from cascading."

---

## 27. CQRS / Event Sourcing

**Applies to**: Order systems, audit-heavy domains, financial ledgers

**CQRS**: Separate write model (optimized for writes) from read model (optimized for queries). Write to event store, project into read-optimized views.

**Event Sourcing**: Store every state change as an immutable event. Current state = replay all events. Enables audit trail, time-travel queries, replay for bug fixes.

**Talking Point**: "I'd use event sourcing for the order system — every state change (created, paid, shipped, delivered) is an immutable event. Current state is derived by replaying events. This gives us a complete audit trail and the ability to replay events if we discover a bug."

---

## 28. Data Migration (Zero Downtime)

**Applies to**: Database migration, schema changes

**4-phase approach**: (1) Dual-write to old + new. (2) Backfill new from old (cursor-based batch). (3) Shadow-read: compare 10% of reads from both, log discrepancies. (4) Cutover via feature flag: 1% → 10% → 50% → 100%. Rollback instantly by flipping flag.

**Talking Point**: "The shadow-read phase is the most important — it catches bugs before they affect users. We read from both databases, compare results in a background thread, and only cut over when discrepancy rate drops to zero."

---

## 29. Conflict Resolution (Multi-Master)

**Applies to**: Multi-region writes, collaborative editing

**Strategies**: Last-writer-wins (LWW) for simple fields (profile name). CRDTs for counters (like counts — both increments preserved). Application-level merge for complex data.

**CRDTs**: Conflict-free Replicated Data Types. G-Counter (grow-only), PN-Counter (add/subtract), OR-Set (add/remove set). Guarantee convergence without coordination.

**Talking Point**: "I'd use LWW for simple fields like display name and CRDTs for counters like follower counts. Different conflict resolution strategies for different data types."

---

## 30. Content-Addressable Caching (Build Systems)

**Applies to**: Bazel, Buck, package managers

**Key idea**: `actionDigest = SHA-256(inputs + command + env + compiler)`. Same inputs → same output. No invalidation needed — different inputs get different keys automatically.

**Cache hit rate**: 80%+ for incremental builds. Reduces 30-min full build to 3-min incremental.

**Talking Point**: "The cache key is a SHA-256 of ALL inputs. If anything changes — even one byte — the hash changes and it's a cache miss. No TTL, no invalidation, no stale cache. The hash IS the correctness guarantee."

---

## 31. Adaptive Bitrate Streaming

**Applies to**: Netflix, YouTube, live streaming

**Problem**: Users on varying network speeds need smooth video playback.

**Solution**: Transcode video into multiple quality levels (360p, 720p, 1080p, 4K). Generate HLS/DASH manifest listing all qualities. Player starts low, measures bandwidth, switches up. On bandwidth drop, switches down immediately. Each segment is 2-6 seconds.

**Talking Point**: "The video is pre-encoded at multiple bitrates. The player measures actual download speed per segment and switches quality in real-time. On Wi-Fi → 1080p. Switch to cellular → drops to 480p within one segment (~4 seconds). No buffering."

---

## 32. OAuth / SSO Token Flow

**Applies to**: SSO, API authentication

**Key flow**: SP redirects to IDP → IDP checks session cookie → issues auth code → SP exchanges code for tokens (server-to-server). Access token (JWT, 15 min) validated locally by SP using cached public key. Refresh token (7 days) stored hashed in DB.

**Per-SP audience**: Gmail gets `aud:"gmail"`. Token stolen from Gmail can't be used against YouTube (aud check fails).

**Talking Point**: "SPs validate JWTs locally using cached public keys — zero IDP dependency at runtime. If the IDP goes down, SPs continue working until tokens expire."

---

## 33. Distributed Counter at Scale

**Applies to**: Like counts, view counts, inventory

**Problem**: Single counter → hot key bottleneck. 100K concurrent increments.

**Solution**: Shard the counter across N keys. Write: `INCR counter:{id}:{random(0-19)}`. Read: `SUM(MGET counter:{id}:0 ... counter:{id}:19)`. Writes scale linearly, reads are one fan-out query.

**Periodic compaction**: Background job sums shards and stores final count every minute for fast reads.

**Talking Point**: "I'd distribute the counter across 20 Redis keys — increment a random shard on write, SUM all 20 on read. Writes scale linearly. For display, a compacted total is updated every minute."

---

## 34. Autocomplete / Typeahead

**Applies to**: Search bars, address input

**Solution**: Separate index with edge n-gram tokenization. "iphone" → ["ip", "iph", "ipho", "iphon", "iphone"]. Query "iph" matches the "iph" token. Score by: 0.7 × popularity + 0.2 × personalization + 0.1 × trending.

**Caching**: CDN for top 1000 prefixes (20% of traffic). Redis for per-prefix results (50%). ES query for rare prefixes (30%).

**Talking Point**: "A separate autocomplete index with edge n-grams returns suggestions in 8ms vs 50ms if searching the main index. 70% cache hit rate makes most responses < 3ms."

---

## 35. Spell Correction

**Applies to**: Search engines

**3-layer approach**: Layer 1: ES fuzzy matching (60% of typos, 0ms extra). Layer 2: dictionary-based (edit distance + frequency + bigram context, 35%, 5ms). Layer 3: query rewrite — remove words iteratively until results appear (5%, 25ms).

**Context-aware**: "por" after "iphone 13" → "pro" (not "for") based on bigram probability.

**Talking Point**: "Layer 1 is free. Layer 2 catches most remaining typos with context-aware bigram scoring. Layer 3 is the safety net. Total: 95% correction rate in < 10ms."

---

## 36. Feature Flags

**Applies to**: A/B testing, gradual rollout, kill switches

**Architecture**: Feature flag service with Redis cache. Each flag: name, state (on/off/percentage), targeting rules (user_id, country, % rollout). SDK checks flag on every request (< 1ms from local cache, sync every 30s).

**Rollout pattern**: 1% → 5% → 25% → 50% → 100%. Monitor key metrics at each step. Instant rollback by setting flag to 0%.

**Talking Point**: "Feature flags let us deploy code to production without activating it. We gradually roll out from 1% to 100%, monitoring metrics at each step. If anything degrades, we flip the flag to 0% — instant rollback, no redeploy."

---

## 37. Dead Letter Queues

**Applies to**: Any async pipeline error handling

**Problem**: A poison message (unparseable, triggers bug) blocks the queue — all subsequent messages stuck.

**Solution**: After 3 retries with exponential backoff → move to Dead Letter Queue (DLQ). Alert on-call. Main queue continues processing. DLQ messages are investigated and replayed after fix.

**Talking Point**: "After 3 failed processing attempts, the message moves to a dead letter queue. The main pipeline continues unblocked. The DLQ accumulates for investigation — once we fix the bug, we replay the DLQ messages."

---

## 38. Database Connection Pooling

**Applies to**: High-throughput APIs

**Problem**: Creating a new DB connection = TCP handshake + auth + TLS = 10-50ms overhead per request. At 10K QPS, that's unsustainable.

**Solution**: Connection pool (HikariCP, PgBouncer). Maintain N pre-established connections. Request borrows → uses → returns. Pool size = `connections = (cpu_cores × 2) + disk_spindles` (typically 20-50 per app instance).

**Talking Point**: "Connection pooling eliminates the 10-50ms overhead of establishing a new database connection per request. A pool of 30 pre-established connections handles 5K QPS per app instance with < 1ms acquisition time."

---

## 39. Blue-Green / Canary Deployments

**Applies to**: Zero-downtime deploys

**Blue-Green**: Two identical environments. Deploy to green (inactive). Smoke test. Switch load balancer to green. If issues: switch back to blue instantly. Cost: 2× infrastructure during deploy.

**Canary**: Route 5% of traffic to new version. Monitor errors, latency, business metrics for 30 min. If clean: 5% → 25% → 50% → 100%. If degraded: route 100% back to old version.

**Talking Point**: "I'd use canary deployment — route 5% of traffic to the new version, monitor for 30 minutes, then gradually increase. If error rate spikes, we route 100% back to the old version in < 30 seconds. No downtime, controlled risk."

---

## 40. Idempotency Patterns

**Applies to**: Payments, any retry-safe API

**Problem**: Network timeout after server processes request but before client receives response. Client retries → duplicate processing.

**Patterns**: (1) Client-generated idempotency key (UUID) → server checks before processing. (2) DB unique constraint on request_id → insert fails on duplicate. (3) Conditional writes: `UPDATE ... WHERE version = expected`. (4) Redis SETNX: `SET idempotency:{key} {result} NX EX 86400`.

**Talking Point**: "Every mutating API accepts a client-generated idempotency key. Before processing, we check Redis: if key exists, return the stored result. If new, process and store. This means network retries, server crashes, and duplicate messages all produce the same result — no double charges, no double bookings."

---

## 📚 How to Deliver a Deep Dive in an Interview

### The 4-Part Structure

1. **State the problem clearly** (10 seconds): "The challenge here is that when a celebrity with 10 million followers tweets, pure fan-out on write would mean 10 million Redis writes — about 100 seconds."

2. **Describe the solution** (20 seconds): "So I'd use a hybrid approach with a follower threshold of roughly 1 million. For 99% of users, we push to followers' timelines on write. For celebrities, we skip the fanout and merge at read time."

3. **Explain how it works with specifics** (30 seconds): "On tweet creation, check follower count. Under 1M: ZADD into each follower's Redis timeline. Over 1M: skip. On read, fetch the pre-computed timeline from Redis, query celebrity tweets from Cassandra, merge by timestamp in-memory."

4. **Quantify the impact** (10 seconds): "Total read latency: ~25ms. Fanout budget drops 40%. Read for the user is seamless — they never know two different strategies are at play."

### Total: ~70 seconds per deep dive. Practice until each flows naturally.

---

**Total Deep Dives: 40**
**Coverage: Infrastructure, Data, Real-Time, Security, Scaling, Operations**
**Status: Interview-Ready ✅**
