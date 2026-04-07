# 🧩 System Design — Missing & Additional Topics Cheatsheet

> **Purpose**: This is the **companion** to `system-design-tradeoffs-deep-dives-cheatsheet.md`. It contains **20 deep dives + 10 bonus topics** that are NOT covered in that file but are critical for system design interviews. Together, the two documents give you complete coverage.
>
> **How to Use**: Same as the tradeoffs cheatsheet — find the relevant topic, deliver the **Problem → Solution → How It Works → Talking Point** structure in ~70 seconds.

---

## Table of Contents

### Deep Dives (Not in Tradeoffs Cheatsheet)
- [🧩 System Design — Missing \& Additional Topics Cheatsheet](#-system-design--missing--additional-topics-cheatsheet)
  - [Table of Contents](#table-of-contents)
    - [Deep Dives (Not in Tradeoffs Cheatsheet)](#deep-dives-not-in-tradeoffs-cheatsheet)
    - [Bonus Topics (Scattered Across Other Docs)](#bonus-topics-scattered-across-other-docs)
  - [1. Search Index Pipeline (CDC + Async Indexing)](#1-search-index-pipeline-cdc--async-indexing)
  - [2. Presence / Online Status System](#2-presence--online-status-system)
  - [3. Notification Aggregation](#3-notification-aggregation)
  - [4. Geo-Proximity Search (Geohash / Quadtree)](#4-geo-proximity-search-geohash--quadtree)
  - [5. Circuit Breaker Pattern](#5-circuit-breaker-pattern)
  - [6. CQRS \& Event Sourcing](#6-cqrs--event-sourcing)
    - [CQRS (Command Query Responsibility Segregation)](#cqrs-command-query-responsibility-segregation)
    - [Event Sourcing](#event-sourcing)
  - [7. Zero-Downtime Data Migration](#7-zero-downtime-data-migration)
  - [8. Conflict Resolution \& CRDTs](#8-conflict-resolution--crdts)
    - [Strategies](#strategies)
    - [CRDT Types](#crdt-types)
  - [9. Content-Addressable Caching (Build Systems)](#9-content-addressable-caching-build-systems)
  - [10. Adaptive Bitrate Streaming](#10-adaptive-bitrate-streaming)
  - [11. OAuth / SSO Token Flow](#11-oauth--sso-token-flow)
  - [12. Distributed Counter at Scale](#12-distributed-counter-at-scale)
  - [13. Autocomplete / Typeahead](#13-autocomplete--typeahead)
  - [14. Spell Correction \& Query Rewriting](#14-spell-correction--query-rewriting)
  - [15. Feature Flags \& Gradual Rollout](#15-feature-flags--gradual-rollout)
  - [16. Dead Letter Queues \& Poison Message Handling](#16-dead-letter-queues--poison-message-handling)
  - [17. Database Connection Pooling](#17-database-connection-pooling)
  - [18. Blue-Green \& Canary Deployments](#18-blue-green--canary-deployments)
    - [Blue-Green](#blue-green)
    - [Canary Deployment](#canary-deployment)
  - [19. Idempotency Patterns (Payment-Grade)](#19-idempotency-patterns-payment-grade)
  - [20. Fencing Tokens (Stale Leader Prevention)](#20-fencing-tokens-stale-leader-prevention)
- [🎁 BONUS TOPICS](#-bonus-topics)
  - [21. Cache Stampede Prevention](#21-cache-stampede-prevention)
  - [22. HyperLogLog \& Probabilistic Counting](#22-hyperloglog--probabilistic-counting)
  - [23. Transactional Outbox Pattern](#23-transactional-outbox-pattern)
  - [24. Observability: The 3 Pillars](#24-observability-the-3-pillars)
    - [The 3 Pillars](#the-3-pillars)
    - [The 4 Golden Signals (from Google SRE)](#the-4-golden-signals-from-google-sre)
  - [25. Retry with Exponential Backoff + Jitter](#25-retry-with-exponential-backoff--jitter)
  - [26. Graceful Degradation Playbook](#26-graceful-degradation-playbook)
  - [27. Cache Key Design Best Practices](#27-cache-key-design-best-practices)
  - [28. Back-of-Envelope Estimation Framework](#28-back-of-envelope-estimation-framework)
  - [29. Single-Writer Principle](#29-single-writer-principle)
  - [30. Real-World Company Examples Quick Reference](#30-real-world-company-examples-quick-reference)
  - [🎯 Quick Reference: Which Missing Topic for Which Problem?](#-quick-reference-which-missing-topic-for-which-problem)
  - [🗣️ Interview Tip: Combining Both Cheatsheets](#️-interview-tip-combining-both-cheatsheets)

### Bonus Topics (Scattered Across Other Docs)
21. [Cache Stampede Prevention](#21-cache-stampede-prevention)
22. [HyperLogLog & Probabilistic Counting](#22-hyperloglog--probabilistic-counting)
23. [Transactional Outbox Pattern](#23-transactional-outbox-pattern)
24. [Observability: The 3 Pillars](#24-observability-the-3-pillars)
25. [Retry with Exponential Backoff + Jitter](#25-retry-with-exponential-backoff--jitter)
26. [Graceful Degradation Playbook](#26-graceful-degradation-playbook)
27. [Cache Key Design Best Practices](#27-cache-key-design-best-practices)
28. [Back-of-Envelope Estimation Framework](#28-back-of-envelope-estimation-framework)
29. [Single-Writer Principle](#29-single-writer-principle)
30. [Real-World Company Examples Quick Reference](#30-real-world-company-examples-quick-reference)

---

## 1. Search Index Pipeline (CDC + Async Indexing)

**Applies to**: Tweet search, product search, log search, any full-text search

**Problem**: Search index must stay in sync with the source-of-truth database without coupling the write path to the search system.

**Solution**: Write to DB → CDC (Change Data Capture via Debezium) → Kafka → Search Indexer consumer → Elasticsearch bulk API.

**How It Works**:
1. User creates content → write to primary DB (Cassandra/PostgreSQL)
2. Debezium captures the DB change log (binlog/WAL) and publishes to Kafka
3. Search Indexer consumer reads from Kafka
4. Batches documents (1000 docs/batch) and calls Elasticsearch Bulk API
5. Search index is a **derived view** — can always be rebuilt from source

**Key Details**:
- Indexing lag: ~1-2 seconds (acceptable for most search use cases)
- If Elasticsearch is slow/down, Kafka consumer builds up lag and catches up when ES recovers
- The search index is NOT on the critical write path — if ES dies, writes still succeed
- Index rebuilding: replay all events from Kafka (with sufficient retention) or do a full DB scan

**Talking Point**: "Tweets write to Cassandra first, then a Kafka consumer indexes them in Elasticsearch. Search lag is ~1-2 seconds, but the write path is completely decoupled from search. The search index is a derived view — we can always rebuild it from the source of truth."

---

## 2. Presence / Online Status System

**Applies to**: WhatsApp, Slack, Discord, any chat/collaboration app

**Problem**: Track which users are online/offline in real-time across millions of connections without overwhelming the system.

**Solution**: Redis keys with TTL + client heartbeats.

**How It Works**:
1. On connection: set `online:{user_id}` in Redis with 70s TTL
2. Client sends **heartbeat** every 30s → server refreshes Redis TTL back to 70s
3. If client crashes/disconnects → heartbeat stops → after 70s, key auto-expires → user is offline
4. On expiry: set `last_seen:{user_id}` = current timestamp
5. **Batch check**: `MGET online:{id1} online:{id2} ...` — check 500 contacts in < 2ms

**Why 70s TTL with 30s Heartbeat?**: The 40-second gap provides tolerance for network jitter. If a heartbeat is delayed by 20s due to bad cellular connection, the key still hasn't expired.

**Presence Broadcast**: When user goes online/offline, notify their contacts:
- Lookup contacts → check which are online → push presence change via their WebSocket gateway
- Don't broadcast to ALL contacts — only to contacts who have the chat window open (reduces fanout 10x)

**Talking Point**: "Presence is a Redis key with TTL. Heartbeat refreshes it. If the client crashes, the key auto-expires and the user shows offline. 500 contacts checked in < 2ms via MGET. We only broadcast presence changes to contacts with an active chat window — reduces fanout by 10x."

---

## 3. Notification Aggregation

**Applies to**: Instagram, Facebook, Twitter, any social app with high engagement

**Problem**: A viral post gets 10K likes/hour → sending 10K individual push notifications causes notification fatigue and kills engagement.

**Solution**: Time-windowed aggregation with threshold-based triggering.

**How It Works**:
1. **First interaction**: Immediate notification — "Alice liked your post"
2. **Subsequent interactions (within 5-min window)**: Accumulate in Redis sorted set `notif_batch:{user_id}:{post_id}`
3. **Batch trigger**: When window closes OR count exceeds threshold (50):
   - "Alice and 47 others liked your post"
4. **Priority escalation**: Comments get individual notifications (higher value), likes get batched

**Aggregation Rules** (configurable per event type):

| Event Type | Strategy | Window | Why |
|------------|----------|--------|-----|
| Like | Batch | 5 min | Low value per event, high volume |
| Comment | Individual | N/A | High value, users want to see immediately |
| Follow | Batch | 15 min | Medium volume |
| Mention | Individual | N/A | Requires attention |
| DM | Individual | N/A | Highest urgency |

**Talking Point**: "Instead of 10K individual notifications, I'd aggregate: 'Alice and 49 others liked your photo'. A 5-minute window collects events; the batch fires when the window closes or the count exceeds a threshold. Comments are always individual — they're high-value signals."

---

## 4. Geo-Proximity Search (Geohash / Quadtree)

**Applies to**: Uber (find nearby drivers), Yelp (nearby restaurants), Tinder (nearby users), delivery apps

**Problem**: "Find all drivers within 5km" — can't scan all millions of drivers for every query.

**Solution**: Geohash-based spatial indexing.

**How Geohash Works**:
1. Encode (latitude, longitude) → geohash string (e.g., "9q8yyk")
2. **Same prefix = nearby**: "9q8yyk" and "9q8yym" are close to each other
3. Longer prefix = more precise: 6 chars ≈ 1.2km × 600m cell
4. Store in Redis sorted set: `ZRANGEBYLEX drivers:geo {prefix_min} {prefix_max}`
5. Query "within 5km" → compute neighboring geohash cells → range query on each

**Alternatives**:

| Approach | Pros | Cons | Best For |
|----------|------|------|----------|
| **Geohash + Redis** | Simple, fast, well-understood | Edge effects at cell boundaries | Most use cases |
| **Quadtree** | Adaptive resolution, handles density variation | In-memory, complex to distribute | Uneven density (city vs rural) |
| **S2 Cells (Google)** | No edge effects, hierarchical | More complex, less tooling | Google Maps, high precision |
| **PostGIS / ES geo_distance** | SQL/search integration | Slower for real-time moving objects | Static POI (restaurants, stores) |

**Edge Effect**: A driver 10 meters away might be in a different geohash cell. Solution: always query the target cell + 8 neighboring cells.

**For Moving Objects (Uber drivers)**:
- Drivers update position every 3-5 seconds
- Remove from old geohash cell → add to new geohash cell in Redis
- Use pipeline for atomic move: `ZREM` + `ZADD` in single pipeline

**Talking Point**: "I'd use geohash-based indexing in Redis. Nearby locations share the same geohash prefix, so finding drivers within 5km is a prefix range query — O(log N + K). For edge effects at cell boundaries, I always query the target cell plus 8 neighbors. Drivers update their position every 4 seconds, re-indexed atomically."

---

## 5. Circuit Breaker Pattern

**Applies to**: Microservice resilience, any system calling external dependencies

**Problem**: A slow or failing dependency cascades failures to callers. Without protection, one slow service brings down the entire system.

**Solution**: Circuit breaker — automatically stops calling a failing dependency and returns a fallback.

**States**:
```
CLOSED (normal) → 5 consecutive failures → OPEN (fast-fail for 30s)
                                              ↓ after 30s timeout
                                           HALF-OPEN (1 test request)
                                              ↓ success → CLOSED
                                              ↓ failure → OPEN (another 30s)
```

**How It Works**:
1. **CLOSED**: All requests flow through normally. Track failure count.
2. **OPEN**: All requests immediately fail with fallback response — no actual call to dependency. Timer starts (30s).
3. **HALF-OPEN**: After timer, allow ONE test request through. If succeeds → close circuit. If fails → open again.

**Fallback Strategies**:

| Service Down | Fallback |
|-------------|----------|
| Recommendation Service | Return chronological feed |
| Search Service | Return cached/stale results with banner |
| Payment Gateway | Queue the payment, process async |
| Image Thumbnail Service | Return original image resized client-side |
| Analytics Service | Drop event silently (fire-and-forget) |

**Talking Point**: "If the Recommendation Service times out, the circuit breaker trips — the Feed Service immediately returns a chronological feed instead. After 30 seconds, one test request probes recovery. This prevents a slow dependency from cascading failures to the entire system."

---

## 6. CQRS & Event Sourcing

**Applies to**: Order systems, financial ledgers, audit-heavy domains, collaborative editing

### CQRS (Command Query Responsibility Segregation)

**Problem**: The optimal data model for writes is different from the optimal model for reads. A single model is a compromise for both.

**Solution**: Separate the write model from the read model.

**How It Works**:
1. **Write side**: Accepts commands, validates business rules, stores events/state changes
2. **Read side**: Maintains denormalized, query-optimized views (materialized views)
3. **Sync**: Events from write side update read side asynchronously

### Event Sourcing

**Problem**: You need a complete audit trail and the ability to reconstruct any past state.

**Solution**: Store every state change as an immutable event. Current state = replay all events.

**How It Works**:
1. Instead of `UPDATE orders SET status = 'shipped'`, store event: `{type: "OrderShipped", order_id: 123, timestamp: ...}`
2. Events are append-only and immutable — never updated or deleted
3. Current state = fold/reduce all events for that entity
4. **Snapshots**: Periodically snapshot current state (every 100 events) to avoid replaying entire history

**Benefits**:
- ✅ Complete audit trail (every change recorded)
- ✅ Time-travel queries ("what was the order state at 3 PM?")
- ✅ Replay for bug fixes (fix bug, replay events through corrected logic)
- ✅ Multiple read models from same event stream

**Tradeoffs**:
- ❌ More complex than simple CRUD
- ❌ Event schema evolution is tricky
- ❌ Eventual consistency between write and read models

**Talking Point**: "I'd use event sourcing for the order system — every state change (created, paid, shipped, delivered) is an immutable event. Current state is derived by replaying events. This gives us a complete audit trail and the ability to replay events if we discover a bug in our billing logic."

---

## 7. Zero-Downtime Data Migration

**Applies to**: Any database migration, schema change, or storage backend swap

**Problem**: Migrating from MySQL to DynamoDB (or any DB to any DB) without downtime or data loss.

**Solution**: 4-phase migration with feature flags.

**Phase 1 — Dual Write**:
- Application writes to BOTH old DB and new DB
- Old DB remains the source of truth for reads
- Duration: 1-2 days (validate writes are succeeding)

**Phase 2 — Backfill**:
- Batch job migrates ALL existing data from old → new DB
- Cursor-based (not offset-based) to handle concurrent writes
- Run at low priority to avoid impacting production
- Duration: hours to days depending on data volume

**Phase 3 — Shadow Read**:
- For 10% of traffic, read from BOTH databases and compare results
- Log discrepancies without affecting the response (user always gets old DB result)
- Common issues: timestamp precision, null vs empty string, encoding differences
- Fix migration code, re-backfill affected records
- Duration: 1-2 weeks (until discrepancy rate hits 0%)

**Phase 4 — Cutover**:
- Gradually shift reads via feature flag: 1% → 10% → 50% → 100%
- Monitor error rates, latency, and data correctness at each step
- Rollback: flip flag back to 0% (instant, no code deploy)

**Talking Point**: "The shadow-read phase is the most critical — it catches bugs before they affect users. We read from both databases, compare in a background thread, and only cut over when discrepancy rate drops to zero. Rollback at any phase is just a feature flag flip."

---

## 8. Conflict Resolution & CRDTs

**Applies to**: Multi-region writes, collaborative editing (Google Docs, Figma), multi-master replication

**Problem**: Two users in different regions update the same data simultaneously. When changes replicate, what happens?

### Strategies

| Strategy | How | Pros | Cons | Best For |
|----------|-----|------|------|----------|
| **Last-Writer-Wins (LWW)** | Highest timestamp wins | Simple, no merge logic | Silently drops the "losing" write | Simple fields (profile name, settings) |
| **CRDTs** | Math guarantees convergence | No data loss, no coordination | Limited data types, space overhead | Counters, sets, flags |
| **Application-Level Merge** | Custom merge logic per entity type | Full control | Complex, error-prone | Complex documents |
| **Operational Transform (OT)** | Transform concurrent ops against each other | Proven (Google Docs) | Requires central server | Text editing |

### CRDT Types

| CRDT | What It Does | Example |
|------|-------------|---------|
| **G-Counter** | Grow-only counter (add only) | View count |
| **PN-Counter** | Counter that supports add AND subtract | Like count (like = +1, unlike = -1) |
| **G-Set** | Grow-only set (add only, no remove) | Tags on a post |
| **OR-Set** | Observed-Remove Set (add + remove) | Shopping cart items |
| **LWW-Register** | Last-writer-wins single value | User's display name |
| **RGA** | Replicated Growable Array | Collaborative text editing |

**How CRDTs Work (G-Counter Example)**:
- Each node maintains its own counter: `{node_A: 5, node_B: 3, node_C: 7}`
- Total count = sum of all node counters = 15
- On merge: take MAX of each node's counter → guaranteed convergence, no lost increments
- Two nodes incrementing simultaneously → both increments preserved (no conflict!)

**Talking Point**: "I'd use LWW for simple fields like display name and CRDTs for counters like follower counts. A PN-Counter CRDT guarantees both a like and an unlike from different regions are preserved — no conflicts, no coordination. Figma uses CRDTs for their real-time collaborative design canvas."

---

## 9. Content-Addressable Caching (Build Systems)

**Applies to**: Distributed build systems (Bazel, Buck), package managers, CI/CD pipelines

**Problem**: Full builds take 30+ minutes. Incremental builds need a reliable cache that never serves stale results.

**Solution**: Cache key = SHA-256 hash of ALL inputs. Same inputs → always same output.

**How It Works**:
1. `actionDigest = SHA-256(source_files + command + compiler_version + env_vars + dependencies)`
2. Before executing a build action: check cache for `actionDigest`
3. **Cache hit**: Download cached output (skip build step entirely)
4. **Cache miss**: Execute build → upload result to cache keyed by `actionDigest`

**Why Content-Addressable?**:
- **No TTL needed**: The hash changes if ANY input changes. Old hashes are never stale.
- **No invalidation needed**: Different inputs produce different keys automatically.
- **Deterministic**: Same inputs always produce the same hash → same cached result.
- **The hash IS the correctness guarantee**.

**Numbers**: 80%+ cache hit rate for incremental builds. Reduces 30-min full build to 3-min incremental.

**Talking Point**: "The cache key is a SHA-256 of ALL inputs. If anything changes — even one byte — the hash changes and it's a cache miss. No TTL, no invalidation, no stale cache. The hash IS the correctness guarantee. This gives us 80%+ cache hit rate on incremental builds."

---

## 10. Adaptive Bitrate Streaming

**Applies to**: Netflix, YouTube, Twitch, any video streaming platform

**Problem**: Users are on varying network speeds (Wi-Fi, 4G, 3G, spotty connections). A single video quality causes either buffering (too high) or poor quality (too low).

**Solution**: Pre-encode video at multiple quality levels. Player dynamically switches quality based on real-time bandwidth measurement.

**How It Works**:
1. **Upload time**: Transcode video into multiple quality levels (360p, 480p, 720p, 1080p, 4K)
2. **Segment**: Split each quality into 2-6 second segments (chunks)
3. **Manifest file** (HLS `.m3u8` or DASH `.mpd`): Lists all available qualities + segment URLs
4. **Player logic**:
   - Start with low quality (fast first frame)
   - Measure actual download speed per segment
   - If bandwidth is stable and high → switch UP to next quality
   - If bandwidth drops → switch DOWN immediately (no buffering)
   - Switch happens at segment boundaries (every 2-6 seconds)

**Storage Cost**: One 10-minute video at 5 quality levels ≈ 5× raw storage. Netflix stores 10+ quality levels per video.

**CDN Integration**: Each quality/segment is a separate file on CDN. Popular segments cached at edge. Quality switches don't require different CDN routing — just different URLs.

**Talking Point**: "The video is pre-encoded at multiple bitrates. The player measures actual download speed per segment and switches quality in real-time. On Wi-Fi → 1080p. Switch to cellular → drops to 480p within one segment (~4 seconds). No buffering, no manual quality selection."

---

## 11. OAuth / SSO Token Flow

**Applies to**: SSO login, "Login with Google/Facebook", API authentication

**Problem**: Users need to log in once and access multiple services (Gmail, YouTube, Drive) without re-authenticating.

**Solution**: OAuth 2.0 + OpenID Connect (OIDC) with JWT tokens.

**The Flow**:
1. User clicks "Login" on Service Provider (SP, e.g., Gmail)
2. SP redirects to Identity Provider (IDP, e.g., Google Accounts)
3. IDP checks session cookie → if no session, show login page
4. User authenticates → IDP issues **authorization code** (short-lived, one-time-use)
5. SP exchanges code for tokens **server-to-server** (code + client_secret → access_token + refresh_token)
6. **Access token** (JWT, 15 min): Used for API calls. Validated locally by SP using cached IDP public key.
7. **Refresh token** (7 days): Stored hashed in DB. Used to get new access tokens without re-login.

**Key Security Details**:
- Access token has `aud` (audience) claim — token for Gmail can't be used against YouTube (audience mismatch)
- SPs validate JWTs locally using cached IDP public keys — **zero IDP dependency at runtime**
- If IDP goes down, SPs continue working until tokens expire (15 min)
- Refresh token rotation: each use issues a new refresh token + invalidates the old one

**Talking Point**: "SPs validate JWTs locally using cached public keys — zero IDP dependency at runtime. If the IDP goes down, SPs continue working until tokens expire. Per-SP audience claims mean a token stolen from one service can't be used against another."

---

## 12. Distributed Counter at Scale

**Applies to**: Like counts, view counts, follower counts, inventory counters

**Problem**: A viral post gets 100K likes per second. A single Redis key becomes a hot key bottleneck.

**Solution**: Shard the counter across N keys.

**How It Works**:
1. **Write**: `INCR counter:{post_id}:{random(0-19)}` — write to a random shard out of 20
2. **Read**: `SUM(MGET counter:{post_id}:0 ... counter:{post_id}:19)` — sum all 20 shards
3. Writes scale linearly (20 shards = 20× throughput)
4. Reads are one fan-out query (MGET is O(N) where N = number of shards)

**Periodic Compaction** (for fast reads):
- Background job every minute: sum all shards → store in `counter:{post_id}:total`
- Display uses compacted total (1-minute staleness acceptable for UI)
- Exact count uses real-time sum across shards

**Write Path vs Read Path**:

| Operation | Approach | Latency | Accuracy |
|-----------|----------|---------|----------|
| Increment (like) | Random shard INCR | < 1ms | Exact |
| Display count | Read compacted total | < 1ms | ~1 min stale |
| Billing/analytics | Sum all shards | < 5ms | Exact |

**Talking Point**: "I'd distribute the counter across 20 Redis keys — increment a random shard on write, SUM all 20 on read. Writes scale linearly. For display, a compacted total updated every minute is fast and stale-tolerant. For billing, we sum all shards for exact counts."

---

## 13. Autocomplete / Typeahead

**Applies to**: Search bars, address input, product search, command palettes

**Problem**: Return search suggestions in < 100ms per keystroke as the user types.

**Solution**: Separate autocomplete index with edge n-gram tokenization.

**How It Works**:
1. **Index time**: "iphone" → tokenize into edge n-grams: ["ip", "iph", "ipho", "iphon", "iphone"]
2. **Query time**: User types "iph" → matches the "iph" token → returns ["iPhone 15", "iPhone 14 Pro", "iPhone cases"]
3. **Scoring**: `0.7 × popularity + 0.2 × personalization + 0.1 × trending_boost`

**Caching Strategy** (3-tier):

| Tier | What | Hit Rate | Latency |
|------|------|----------|---------|
| CDN | Top 1000 prefixes ("iph", "sam", "mac") | 20% of traffic | < 3ms |
| Redis | Per-prefix result sets | 50% of traffic | < 5ms |
| Elasticsearch | Rare/long prefixes | 30% of traffic | < 50ms |

**Personalization**: Blend global results with user's recent search history stored in a per-user Redis key. Show personal results first, then global suggestions.

**Update Frequency**: Popularity scores updated hourly from search log aggregations. Trending terms updated every 5 minutes.

**Talking Point**: "A separate autocomplete index with edge n-grams returns suggestions in 8ms vs 50ms from the main search index. CDN caches the top 1000 prefixes (20% of traffic). Redis handles 50%. Only 30% of requests actually hit Elasticsearch. Total: 70% of autocomplete responses in < 5ms."

---

## 14. Spell Correction & Query Rewriting

**Applies to**: Search engines, e-commerce search, chat applications

**Problem**: Users misspell queries — "iphne 15 por" should find "iPhone 15 Pro".

**Solution**: 3-layer spell correction with increasing cost.

**Layer 1 — Elasticsearch Fuzzy Match** (60% of typos, 0ms extra):
- Built-in: `"query": {"fuzzy": {"title": {"value": "iphne", "fuzziness": "AUTO"}}}`
- Handles simple transpositions and single-character errors
- Free — no additional computation

**Layer 2 — Dictionary-Based Correction** (35% of typos, 5ms):
- Compute edit distance (Levenshtein) between query and dictionary terms
- Rank corrections by: `frequency × bigram_probability × (1 / edit_distance)`
- **Context-aware**: "por" after "iphone 13" → "pro" (not "for") based on bigram probability
- Uses a pre-built dictionary from search logs + product catalog

**Layer 3 — Query Rewriting** (5% of typos, 25ms):
- When spelling correction fails, try removing/replacing words iteratively
- "red dress silky new york" → if no results, try "red dress silky" → still no results → "red dress" → results found
- Progressively relax the query until results appear

**Talking Point**: "Layer 1 is free via ES fuzzy matching. Layer 2 catches most remaining typos with context-aware bigram scoring — 'por' after 'iphone' becomes 'pro', not 'for'. Layer 3 progressively relaxes the query. Total: 95% correction rate in < 10ms average."

---

## 15. Feature Flags & Gradual Rollout

**Applies to**: A/B testing, gradual rollout, kill switches, operational safety

**Problem**: Deploying a new feature to 100% of users at once is risky. Need to control who sees what, and kill features instantly if they break.

**Solution**: Feature flag service with targeting rules.

**Architecture**:
1. **Feature Flag Service**: Central store of all flags (backed by DB + Redis cache)
2. **Flag definition**: `{name, state (on/off/percentage), targeting_rules, fallback_value}`
3. **SDK** in each service: checks flag on every request (< 1ms from local cache)
4. **Sync**: SDK polls flag service every 30s for updates (or push via SSE/WebSocket)

**Targeting Rules Examples**:
```
- user_id IN [internal_testers]           → always ON (internal testing)
- country = "US" AND user_id % 100 < 5    → 5% rollout in US only
- account_type = "premium"                 → ON for premium users
- default                                  → OFF
```

**Rollout Pattern**: 1% → 5% → 25% → 50% → 100%. Monitor key metrics at each step. Instant rollback = set flag to 0%.

**Kill Switch**: Critical operations have a flag that can be flipped to disable them instantly — no code deploy, no rollback, just a config change.

**Talking Point**: "Feature flags let us deploy code to production without activating it. We gradually roll out from 1% to 100%, monitoring error rates and business metrics at each step. If anything degrades, we flip the flag to 0% — instant rollback in < 30 seconds, no code redeploy needed."

---

## 16. Dead Letter Queues & Poison Message Handling

**Applies to**: Any async pipeline, message queue processing, event-driven systems

**Problem**: A poison message (unparseable, triggers a bug, causes OOM) blocks the queue — all subsequent messages stuck behind it.

**Solution**: Retry with DLQ (Dead Letter Queue) escalation.

**How It Works**:
1. Consumer attempts to process message
2. **Failure**: Wait exponential backoff (1s → 2s → 4s), retry
3. After **3 failed attempts**: Move message to Dead Letter Queue
4. Main queue continues processing normally (unblocked!)
5. DLQ messages accumulate for investigation
6. Alert on-call team: "47 messages in DLQ in last hour"
7. After bug fix: **replay DLQ** — re-enqueue messages back to main queue

**DLQ Metadata**: Each DLQ message includes:
- Original message body
- Error message / stack trace from last attempt
- Attempt count
- Original queue name
- Timestamps (first attempt, last attempt)

**Monitoring**: Alert when DLQ growth rate exceeds threshold. Daily dashboard of DLQ depth by queue.

**Talking Point**: "After 3 failed processing attempts with exponential backoff, the message moves to a dead letter queue. The main pipeline continues unblocked. Once we fix the bug, we replay the DLQ messages. This prevents one bad message from blocking thousands of good ones."

---

## 17. Database Connection Pooling

**Applies to**: Any high-throughput API backed by a relational database

**Problem**: Creating a new DB connection = TCP handshake + auth + TLS = 10-50ms overhead per request. At 10K QPS, this is unsustainable.

**Solution**: Connection pool — pre-establish N connections, reuse them.

**How It Works**:
1. On app startup: create pool of N pre-established DB connections
2. Request arrives → **borrow** connection from pool (< 1ms)
3. Execute query → **return** connection to pool
4. If all connections busy: wait in queue (with timeout) or reject

**Pool Sizing Formula**: `connections = (cpu_cores × 2) + disk_spindles`
- Typically 20-50 per application instance
- Too few: requests queue up waiting for connections
- Too many: DB overwhelmed by concurrent connections, context switching overhead

**Tools**: HikariCP (Java), PgBouncer (PostgreSQL proxy), ProxySQL (MySQL proxy)

**PgBouncer for Serverless**: When you have 100 Lambda functions each with a connection pool, the DB sees 100 × 30 = 3000 connections. PgBouncer sits in front, multiplexes all into 50 actual DB connections.

**Talking Point**: "Connection pooling eliminates the 10-50ms overhead of establishing a new DB connection per request. A pool of 30 pre-established connections handles 5K QPS per instance with < 1ms acquisition time. For serverless (Lambda), PgBouncer multiplexes 3000 function connections into 50 actual DB connections."

---

## 18. Blue-Green & Canary Deployments

**Applies to**: Zero-downtime deployments, any production system

### Blue-Green

**How It Works**:
1. Two identical environments: Blue (current) and Green (new)
2. Deploy new version to Green (inactive)
3. Run smoke tests against Green
4. Switch load balancer from Blue → Green
5. **Rollback**: Switch LB back to Blue (instant)

**Cost**: 2× infrastructure during deployment window.

### Canary Deployment

**How It Works**:
1. Deploy new version to a small subset of servers
2. Route 5% of traffic to canary (new version)
3. Monitor for 30 min: error rates, latency, business metrics
4. If healthy: 5% → 25% → 50% → 100%
5. If degraded: route 100% back to old version (instant)

**Automated Canary Analysis**: Compare canary metrics against baseline:
- Error rate: canary ≤ baseline × 1.1
- p99 latency: canary ≤ baseline × 1.2
- Business metric (conversion, engagement): canary ≥ baseline × 0.95

| Aspect | Blue-Green | Canary |
|--------|-----------|--------|
| Risk | Full switch (all or nothing) | Gradual (% of traffic) |
| Rollback | Instant (switch LB) | Instant (reroute traffic) |
| Cost | 2× infrastructure | Minimal extra (~5% more) |
| Confidence | Lower (no partial rollout) | Higher (metrics at each step) |
| Best for | Simple apps, infrequent deploys | Large-scale services, frequent deploys |

**Talking Point**: "I'd use canary deployment — route 5% of traffic to the new version, monitor for 30 minutes, then gradually increase. If error rate spikes, we route 100% back to the old version in < 30 seconds. No downtime, controlled risk, and automated analysis catches regressions before they affect most users."

---

## 19. Idempotency Patterns (Payment-Grade)

**Applies to**: Payments, any retry-safe API, order creation, booking confirmation

**Problem**: Network timeout after server processes request but before client receives response. Client retries → duplicate processing (double charge!).

**Pattern 1 — Client-Generated Idempotency Key**:
1. Client generates UUID as idempotency key
2. Sends with request: `POST /charge {amount: $50, idempotency_key: "abc-123"}`
3. Server checks Redis: `SET idempotency:{key} {result} NX EX 86400`
4. If SET succeeds (new key): process payment, store result
5. If SET fails (key exists): return stored result without reprocessing

**Pattern 2 — DB Unique Constraint**:
```sql
INSERT INTO payments (idempotency_key, amount, status)
VALUES ('abc-123', 50.00, 'pending')
ON CONFLICT (idempotency_key) DO NOTHING;
```
If duplicate: insert fails silently, query existing record and return it.

**Pattern 3 — Conditional Write (Optimistic)**:
```sql
UPDATE orders SET status = 'paid', version = 6
WHERE order_id = 123 AND version = 5;
```
If version changed: 0 rows affected → stale, retry with fresh version.

**Pattern 4 — Natural Idempotency**:
- `SET user:123:name "Alice"` is naturally idempotent — running it twice produces the same result
- `INCR counter:123` is NOT idempotent — running it twice increments twice
- Transform non-idempotent ops: instead of `INCR`, use `SET counter:123 5 IF counter:123 = 4`

**Talking Point**: "Every mutating API accepts a client-generated idempotency key. Before processing, we check Redis: if key exists, return the stored result. If new, process and store. Network retries, server crashes, and duplicate messages all produce the same result — no double charges, no double bookings. Stripe implements exactly this pattern."

---

## 20. Fencing Tokens (Stale Leader Prevention)

**Applies to**: Job schedulers, distributed locks, any leader election scenario

**Problem**: Leader A acquires a lock, processes slowly, lock expires. Leader B acquires lock and starts processing. Now BOTH A and B think they're the leader → duplicate processing, data corruption.

**Solution**: Fencing tokens — monotonically increasing tokens tied to lock acquisitions.

**How It Works**:
1. Each lock acquisition generates an increasing token: Leader A gets token=5, Leader B gets token=6
2. When writing to storage, include the fencing token: `WRITE data WITH token=5`
3. Storage layer enforces: **reject writes where token ≤ last-seen token**
4. When stale Leader A (token=5) tries to write after Leader B (token=6), the storage rejects it

**Implementation**:
- ZooKeeper: use the `zxid` (transaction ID) as fencing token — naturally monotonically increasing
- Redis: use a counter `INCR lock_token:{resource}` at each lock acquisition
- Database: use a `leader_token` column, reject updates where `token < current_max_token`

**Without Fencing Tokens** (the disaster):
```
1. Leader A acquires lock (token=5), starts processing job
2. Leader A becomes slow (GC pause, network delay)
3. Lock expires (TTL reached)
4. Leader B acquires lock (token=6), starts processing same job
5. Leader A wakes up, writes result ← STALE, CORRUPTS DATA
```

**With Fencing Tokens**:
```
Step 5: Leader A tries to write with token=5
        Storage sees last token=6 (from Leader B)
        token 5 ≤ 6 → REJECTED ✅ No corruption
```

**Talking Point**: "Fencing tokens prevent the stale-leader problem. Each lock acquisition generates an increasing token. The storage layer rejects writes from old tokens — if stale Leader A (token=5) tries to write after new Leader B (token=6), the write is rejected. No split-brain, no duplicate processing, no data corruption."

---

---

# 🎁 BONUS TOPICS

---

## 21. Cache Stampede Prevention

**Problem**: Popular cache key expires → 50,000 concurrent requests all miss simultaneously → blast the database.

**Solutions**:

| Strategy | How | Best For |
|----------|-----|----------|
| **Distributed Lock** | First request acquires Redis `SETNX` lock → repopulates cache. Others wait 50ms and retry (now hits cache). | Most common, reliable |
| **Probabilistic Early Expiry** | Each request randomly refreshes the key before TTL with probability that increases as TTL approaches. | Hot keys, simple |
| **Stale-While-Revalidate** | Return stale cached value immediately, refresh in background. | When slight staleness is OK |
| **Pre-warm on Write** | Populate cache when data is written, not when read. | Write-through pattern |

**Talking Point**: "When our most popular product page's cache expires, 50K concurrent requests could hit the DB simultaneously. I'd use a distributed lock — the first request acquires a Redis SETNX lock and repopulates the cache; the other 49,999 wait 50ms and retry, hitting the now-populated cache. Total DB load: 1 query instead of 50,000."

---

## 22. HyperLogLog & Probabilistic Counting

**Problem**: Count unique visitors/IPs/users per day. Exact count for 100M unique visitors = 800MB hash set. Need this for every metric × every day.

**Solution**: HyperLogLog — estimates cardinality using only **12 KB** regardless of count.

**Properties**:
- Fixed 12 KB memory whether counting 1,000 or 100 million unique elements
- Error rate: ≈ 0.81% standard error
- Operations: `PFADD` (add element), `PFCOUNT` (get count), `PFMERGE` (merge two HLLs)

**Use Cases**:

| Metric | Exact Cost | HyperLogLog Cost | Savings |
|--------|-----------|-------------------|---------|
| Unique visitors/day | 800 MB | 12 KB | 65,000× |
| Unique IPs per API endpoint | 200 MB | 12 KB | 16,000× |
| Unique search queries/hour | 50 MB | 12 KB | 4,000× |

**Talking Point**: "For 'unique visitors per day', I'd use HyperLogLog — 12 KB of memory regardless of whether we have 1,000 or 100 million unique visitors, with < 1% error. For a dashboard metric, 'approximately 12.3 million unique visitors' is just as useful as 'exactly 12,312,847'. That's a 65,000× memory savings."

---

## 23. Transactional Outbox Pattern

**Problem**: Writing to the database AND publishing to Kafka must be atomic. If the app writes to DB then crashes before publishing to Kafka, the event is lost. If it publishes to Kafka first then crashes before writing to DB, the DB is inconsistent.

**Solution**: Write the event AND business data in the same DB transaction.

**How It Works**:
```sql
BEGIN;
  INSERT INTO orders (id, user_id, amount) VALUES (123, 'alice', 50.00);
  INSERT INTO outbox (event_type, payload, status) VALUES ('OrderCreated', '{"id":123,...}', 'PENDING');
COMMIT;
```

1. Both inserts succeed or fail atomically (single DB transaction)
2. **Outbox Relay** (Debezium CDC or polling service) reads outbox table and publishes to Kafka
3. After successful Kafka publish: mark outbox row as `PUBLISHED`

**Why Not Just Publish to Kafka Directly?**:
- DB write + Kafka publish = two operations that can't be atomic
- If Kafka publish fails after DB write → inconsistent state
- Outbox pattern makes it a single DB transaction + async relay

**Talking Point**: "Instead of writing to the DB and then publishing to Kafka (which can fail between the two operations), I write both the business data and the outbox event in the same database transaction. A CDC connector reads the outbox table and publishes to Kafka. The event is either committed with the business data or not at all — atomically."

---

## 24. Observability: The 3 Pillars

**Problem**: With 50+ microservices, when something goes wrong, you need to quickly answer: What's wrong? What caused it? Where in the call chain?

### The 3 Pillars

| Pillar | What | Tool Examples | Tells You |
|--------|------|---------------|-----------|
| **Metrics** | Quantitative time-series data | Prometheus, CloudWatch, Datadog | **Something is wrong** (latency up, errors spiking) |
| **Logs** | Qualitative event records | ELK Stack, CloudWatch Logs | **What went wrong** (error messages, stack traces) |
| **Traces** | Request journey across services | Jaeger, X-Ray, Zipkin | **Where it went wrong** (which service, which DB query) |

### The 4 Golden Signals (from Google SRE)

| Signal | What to Monitor | Alert Threshold Example |
|--------|----------------|------------------------|
| **Latency** | p50, p95, p99 response times | p99 > 500ms for 5 minutes |
| **Traffic** | Requests per second | QPS drops > 50% from baseline |
| **Errors** | 5xx rate, failed requests | 5xx rate > 1% for 3 minutes |
| **Saturation** | CPU, memory, disk, connections | CPU > 85% or disk > 90% |

**Key Principle**: Alert on **symptoms** (what the user feels), not **causes**. "p99 latency > 500ms" is a symptom. "CPU > 80%" is a cause that might not affect users.

**Talking Point**: "I'd instrument the three pillars: Prometheus metrics tell me something is wrong, structured logs tell me what went wrong, and distributed traces (Jaeger) show me where in the 5-service call chain the 300ms is being spent. I'd alert on the 4 golden signals — latency, traffic, errors, saturation — focusing on user-visible symptoms."

---

## 25. Retry with Exponential Backoff + Jitter

**Problem**: Service B goes down for 10 seconds. All clients retry simultaneously at fixed intervals → synchronized retry storms that DDoS the recovering service.

**Solution**: `delay = min(base_delay × 2^attempt + random(0, base_delay), max_delay)`

**Why Each Component Matters**:
- **Exponential backoff** (`2^attempt`): Progressively back off — 1s, 2s, 4s, 8s, 16s. Gives service time to recover.
- **Jitter** (`random(0, base_delay)`): Randomizes retry timing. Without it, all clients retry at exactly 1s, 2s, 4s — creating synchronized bursts.
- **Max delay cap**: Prevents unreasonably long waits (cap at 30-60s).
- **Max attempts**: Don't retry forever — fail after 3-5 attempts and return error or fallback.

**Without Jitter** (the disaster):
```
t=0:    1000 clients send request → service down
t=1s:   1000 clients retry simultaneously → service still overwhelmed
t=2s:   1000 clients retry simultaneously → service can't recover
t=4s:   1000 clients retry simultaneously → cascading failure continues
```

**With Jitter**:
```
t=0:      1000 clients send request → service down
t=0.8-1.2s: ~200 clients retry (spread across 400ms) → some succeed
t=1.5-2.5s: ~200 clients retry → more succeed as service recovers
t=3-5s:     remaining clients retry → service has recovered
```

**Talking Point**: "Without jitter, all clients retry at exactly the same time — 1s, 2s, 4s — creating synchronized retry storms that keep the service overwhelmed. Jitter randomizes retry timing, spreading the load. It's the difference between 'service recovers in 10 seconds' and 'service cascading fails for 10 minutes'."

---

## 26. Graceful Degradation Playbook

**Principle**: Every component should have a defined fallback. A degraded experience is always better than no experience.

**Degradation Strategies by Component**:

| Component Down | Degradation Strategy | User Impact |
|---------------|---------------------|-------------|
| Recommendation Service | Return chronological feed | Less personalized, still functional |
| Search Index stale | Show results + "results may not include latest" banner | Transparent staleness |
| Image Thumbnail Service | Return original image, resize client-side | Slower loading, still works |
| Analytics Pipeline | Drop analytics events silently | Zero user impact |
| Payment Gateway | Queue payment, show "processing" + email confirmation | Delayed but not lost |
| Database (primary) | Serve stale data from cache (read-only mode) | Can browse, can't write |
| CDN | Serve from origin directly | Higher latency, still works |
| Feature Flag Service | Use cached flags / defaults | Features stay at last known state |

**Key Practices**:
1. **Define fallbacks at design time** — not during an outage
2. **Test degradation monthly** in game days / chaos engineering
3. **Feature flags control degradation** — flip to degraded mode without code deploy
4. **Monitor fallback activation** — alert when a fallback is triggered

**Talking Point**: "Every component has a defined fallback: if recommendations are down, show chronological feed. If search is stale, show results with a banner. If the payment gateway is slow, queue and confirm via email. We test these fallbacks monthly in game days. A degraded experience is infinitely better than an error page."

---

## 27. Cache Key Design Best Practices

**Problem**: Bad cache key design leads to collisions, hard invalidation, and debugging nightmares.

**Best Practices**:

| Rule | Good | Bad | Why |
|------|------|-----|-----|
| **Namespace** | `tweet:123:data` | `123` | Avoid collisions between entity types |
| **Version** | `user:456:profile:v3` | `user:456:profile` | Change cache schema without mass invalidation |
| **Include all query params** | `search:q=iphone:page=2:sort=price` | `search:iphone` | Different queries must have different keys |
| **Consistent ordering** | `feed:user=123:page=1` | `feed:page=1:user=123` (sometimes) | Same logical query should always hit same key |
| **Prefix for bulk invalidation** | `user:123:*` (can `SCAN` and delete) | Random key patterns | Need to invalidate all of user's cache on delete |

**Key Structure Pattern**: `{entity_type}:{entity_id}:{sub_resource}:{version}`

**Examples**:
- `tweet:789:data:v2` — tweet content
- `timeline:123:page:1` — user's timeline page 1
- `search:q=iphone:sort=relevance:page=1` — search results
- `ratelimit:user:456:tweets:minute` — rate limit counter

**Talking Point**: "Cache keys should be namespaced (avoid collisions), versioned (schema changes without mass invalidation), and include all query parameters (different queries, different keys). The pattern `{type}:{id}:{sub}:{version}` keeps keys predictable, debuggable, and easy to bulk-invalidate by prefix."

---

## 28. Back-of-Envelope Estimation Framework

**Key Numbers to Memorize**:

| Metric | Value |
|--------|-------|
| QPS for a web server | ~1,000 QPS (single server) |
| QPS for a database | ~5,000-10,000 QPS (read), ~1,000-5,000 (write) |
| Redis operations/sec | ~100,000 per node |
| Kafka throughput | ~1M messages/sec per broker |
| S3 latency | ~100-200ms (first byte) |
| Redis latency | < 1ms |
| DB latency (indexed) | ~1-5ms |
| CDN edge latency | ~10-30ms |
| Cross-region latency | ~50-200ms |
| SSD random read | ~0.1ms |
| HDD random read | ~10ms |
| 1 GB over 1 Gbps network | ~10 seconds |

**Daily Active Users → QPS**:

| DAU | Avg actions/day/user | QPS (avg) | Peak QPS (3× avg) |
|-----|---------------------|-----------|-------------------|
| 1M | 10 | ~115 | ~350 |
| 10M | 10 | ~1,150 | ~3,500 |
| 100M | 10 | ~11,500 | ~35,000 |
| 500M | 20 | ~115,000 | ~350,000 |

**Formula**: `QPS = DAU × actions_per_user / 86,400`  
**Peak**: `QPS × 3` (rule of thumb)

**Storage Estimation**:
```
Messages/day × avg_size × retention_days × replication_factor
= 1B msgs × 1KB × 365 days × 3 replicas
= 1.095 PB/year
```

**Talking Point**: "With 500M DAU doing 20 actions each, that's ~115K QPS average, ~350K at peak. Each API server handles ~1K QPS, so we need ~350 servers at peak. With 3× replication, 1 billion messages per day at 1KB each is ~1 PB/year of storage."

---

## 29. Single-Writer Principle

**Problem**: Multiple writers to the same data = contention, distributed locking, race conditions, complexity.

**Solution**: Route all writes for a given entity through a single writer (partition/node).

**How It Works**:
- Shard/partition data by entity (user_id, order_id, conversation_id)
- All updates to user 123's balance go through the partition that owns user 123
- No concurrent writers → no locks needed → no contention
- Reads can still be distributed across replicas

**Examples**:
- **Bank balance**: Partition by account_id → all debits/credits for one account serialized through one partition
- **Chat messages**: Partition by conversation_id → messages always ordered within a conversation
- **Kafka**: Messages with same key go to same partition → consumed in order by one consumer

**Tradeoff**: Serialized writes for one entity. But individual write latency is < 5ms, supporting 200 txn/sec per entity — far more than any human user generates.

**Talking Point**: "I follow the single-writer principle — all updates to a given user's balance go through one partition. No concurrent writers means no distributed locks, no contention, no race conditions. The tradeoff is serialized writes per user, but at 5ms per write, one partition handles 200 transactions/sec per user — orders of magnitude more than any human needs."

---

## 30. Real-World Company Examples Quick Reference

> Drop these into interviews: *"This is similar to how [Company] does [X]..."*

| Topic | Company Example | Key Insight |
|-------|----------------|-------------|
| **Start simple** | Instagram: 3 engineers, PostgreSQL, 30M users | Don't over-engineer. Start with Postgres, scale when needed. |
| **Monolith scales** | Shopify: Rails monolith handles Black Friday | Monoliths can scale. Microservices aren't always the answer. |
| **Custom caching** | Facebook TAO: graph store on MySQL + Memcached | Sometimes a specialized cache over boring DB beats a fancy DB. |
| **Write-heavy NoSQL** | Discord: MongoDB → Cassandra → ScyllaDB for trillions of messages | Each migration solved a specific scaling bottleneck. |
| **Kafka was born** | LinkedIn: built Kafka for 1M events/sec activity stream | Kafka solved "multiple consumers reading same event" problem. |
| **Replay matters** | Airbnb: RabbitMQ → Kafka for replay capability | Once you need to reprocess events, log-based beats traditional queue. |
| **Idempotency key** | Stripe: every API endpoint accepts Idempotency-Key header | Gold standard for payment API design — retry = same result. |
| **Custom CDN** | Netflix Open Connect: hardware inside ISPs | At extreme scale, building CDN is cheaper than paying Akamai. |
| **Chaos engineering** | Netflix Chaos Monkey: randomly kills prod instances | If your system can't handle a server dying, it's not production-ready. |
| **Snowflake IDs** | Twitter: created 64-bit time-sortable distributed IDs | De facto standard. 4096 IDs/ms/machine, zero coordination. |
| **Consistent hashing** | Amazon Dynamo paper (2007) | Foundation of DynamoDB, Cassandra, Riak. Availability over consistency. |
| **Global consistency** | Google Spanner: atomic clocks for global strong consistency | Strong consistency at global scale IS possible — with specialized hardware. |
| **CRDTs** | Figma: conflict-free real-time collaborative editing | Multiple users, no conflicts, no central coordinator. |
| **E2EE** | WhatsApp: Signal Protocol — server can't read messages | Privacy guarantee. Tradeoff: no server-side search/moderation. |
| **Error budgets** | Google SRE: 99.9% SLO = 52 min downtime budget/year | Under budget → push features. Over budget → focus on reliability. |
| **Adaptive rate limits** | Stripe: trusted integrations get higher limits | Rate limiting isn't just per-IP — it's per-account, per-action, per-trust. |
| **HyperLogLog** | Redis: unique count in 12 KB regardless of cardinality | 65,000× memory savings for approximate unique counting. |
| **Post-mortem lesson** | Facebook 2021: 6-hour outage, engineers locked out of fix tools | Lesson: out-of-band access for disaster recovery is mandatory. |
| **Post-mortem lesson** | GitLab 2017: deleted prod DB, 5/5 backup methods failed | Lesson: backups are worthless unless you regularly test restoration. |
| **Post-mortem lesson** | Knight Capital 2012: $440M loss in 45 min from bad deploy | Lesson: feature flags + kill switches + canary deploys are non-negotiable. |

---

## 🎯 Quick Reference: Which Missing Topic for Which Problem?

| System Design Problem | Relevant Missing Topics |
|----------------------|------------------------|
| **Twitter / Instagram Feed** | Search Index Pipeline, Notification Aggregation, Distributed Counter, Feature Flags |
| **WhatsApp / Chat** | Presence System, Conflict Resolution (CRDTs), Fencing Tokens, Dead Letter Queues |
| **Google Docs** | CRDTs / Event Sourcing, CQRS, Conflict Resolution |
| **Uber / Lyft** | Geo-Proximity Search (Geohash), Circuit Breaker, Graceful Degradation |
| **Netflix / YouTube** | Adaptive Bitrate Streaming, Content-Addressable Cache, CDN Pipeline |
| **E-Commerce / Booking** | Idempotency Patterns, Distributed Counter, Feature Flags, Dead Letter Queues |
| **Payment System** | Idempotency (Payment-Grade), Transactional Outbox, Exactly-Once via Fencing Tokens |
| **Search System** | Search Index Pipeline, Autocomplete, Spell Correction, Cache Key Design |
| **SSO / Auth System** | OAuth/SSO Token Flow, Feature Flags |
| **Database Migration** | Zero-Downtime Migration (4-phase), Blue-Green Deployment |
| **Monitoring / Metrics** | Observability 3 Pillars, Circuit Breaker, HyperLogLog, Graceful Degradation |
| **Job Scheduler** | Fencing Tokens, Dead Letter Queues, Idempotency Patterns |
| **Distributed Build** | Content-Addressable Caching, Connection Pooling |
| **Any System** | Back-of-Envelope Estimation, Retry + Backoff + Jitter, Cache Stampede Prevention |

---

## 🗣️ Interview Tip: Combining Both Cheatsheets

Use the **tradeoffs cheatsheet** for the main design decisions (database selection, caching, fanout, consistency, etc.) and this **missing topics cheatsheet** for the deep-dive follow-ups.

**Example flow for a Twitter design**:
1. From tradeoffs: "I'd use Cassandra for tweets (write-heavy), Redis for timelines (read-heavy), hybrid fanout, Snowflake IDs"
2. Interviewer: "Tell me more about the search feature" → **Search Index Pipeline** (#1) + **Autocomplete** (#13)
3. Interviewer: "How do you handle like counts on viral tweets?" → **Distributed Counter** (#12) + **Cache Stampede Prevention** (#21)
4. Interviewer: "How would you deploy this safely?" → **Canary Deployment** (#18) + **Feature Flags** (#15) + **Circuit Breaker** (#5)
5. Interviewer: "How do you monitor this?" → **Observability 3 Pillars** (#24)

---

**Document Version**: 1.0  
**Last Updated**: April 2026  
**Coverage**: 20 deep dives + 10 bonus topics missing from the tradeoffs cheatsheet  
**Companion To**: `system-design-tradeoffs-deep-dives-cheatsheet.md`  
**Status**: Complete & Interview-Ready ✅
