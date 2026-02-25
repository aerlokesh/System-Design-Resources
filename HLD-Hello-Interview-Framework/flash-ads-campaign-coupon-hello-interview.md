# Design an Ads Campaign with 5-Minute Availability & Coupon - Hello Interview Framework

> **Question**: Design an advertising campaign system that handles time-sensitive promotions with 5-minute availability windows and coupon distribution. The system should manage campaign scheduling, real-time availability tracking, and coupon redemption. Think flash sales on Amazon, Groupon lightning deals, or Instagram story ads that go live briefly and drive urgent action.
>
> **Asked at**: Meta
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## 1️⃣ Requirements

### Functional (P0)
1. **Campaign Scheduling**: Advertisers create campaigns with: start time, duration (typically 5 minutes), total coupon inventory, discount amount, targeting criteria. Campaign goes live at exact scheduled time.
2. **Real-Time Availability**: During the 5-minute window, show users how many coupons remain. Update in real time as coupons are claimed. Show "SOLD OUT" when inventory depleted.
3. **Coupon Claiming**: Users claim coupons during the window. One coupon per user per campaign. Claimed coupons have a redemption deadline (e.g., 24 hours). No overselling (claimed ≤ inventory).
4. **Coupon Redemption**: Users apply claimed coupons at checkout. Validate: coupon exists, belongs to user, not expired, not already redeemed. Atomic redemption.
5. **Precise Timing**: Campaign starts and ends at EXACTLY the scheduled time (±1 second). No early access, no late claims.
6. **Burst Traffic Handling**: When campaign goes live, thousands of users hit simultaneously. Handle 100K+ claims/sec for popular campaigns without overselling.

### Functional (P1)
- Campaign targeting (geo, demographics, past purchase behavior)
- Waitlist (if sold out, join waitlist for unclaimed coupons that expire)
- Campaign analytics (impressions, claims, redemptions, conversion rate)
- A/B testing (different discount amounts, durations)
- Push notification: "Flash sale starting in 1 minute!"
- Advertiser dashboard: real-time view of campaign performance

### Non-Functional Requirements
| Attribute | Target |
|-----------|--------|
| **Claim Latency** | < 200ms per claim request |
| **No Overselling** | Claimed coupons ≤ inventory (ZERO tolerance) |
| **Timing Precision** | Campaign starts within ±1 second of scheduled time |
| **Burst Capacity** | 100K claim requests/sec per campaign |
| **Fairness** | First-come-first-served; no user can claim twice |
| **Availability** | 99.99% during campaign windows |
| **Redemption** | < 100ms validation at checkout |

### Capacity Estimation
```
Campaigns: 1000 active campaigns/day, 100 concurrent at peak
Coupons per campaign: avg 10K, max 100K
Total coupons/day: 10M

Claim traffic:
  Popular campaign: 100K claims/sec burst (first 30 seconds)
  Average campaign: 5K claims/sec
  Total claims/day: ~50M attempts (many fail: sold out / duplicate)

Redemptions: ~30% of claimed coupons redeemed = 3M/day
  Redemption requests: ~35/sec sustained

Storage:
  Campaign metadata: 1000 × 2 KB = 2 MB/day
  Coupon records: 10M × 200 bytes = 2 GB/day
  Claim records: 50M × 100 bytes = 5 GB/day
```

---

## 2️⃣ Core Entities

```java
public class Campaign {
    String campaignId;
    String advertiserId;
    String title;               // "50% Off AirPods - 5 Min Only!"
    String description;
    double discountAmount;      // 50.00 (or percentage)
    DiscountType discountType;  // FIXED_AMOUNT, PERCENTAGE
    int totalInventory;         // 10,000 coupons available
    int claimedCount;           // Current count (denormalized)
    int redeemedCount;
    Instant scheduledStart;     // Exact start time
    Instant scheduledEnd;       // Start + 5 minutes
    Duration redeemDeadline;    // 24 hours after claim
    CampaignStatus status;      // SCHEDULED, LIVE, ENDED, CANCELLED
    TargetingCriteria targeting;// Geo, demographics, etc.
    Instant createdAt;
}
public enum CampaignStatus { SCHEDULED, LIVE, ENDED, SOLD_OUT, CANCELLED }
public enum DiscountType { FIXED_AMOUNT, PERCENTAGE, FREE_ITEM }

public class Coupon {
    String couponId;            // UUID
    String campaignId;
    String couponCode;          // "FLASH-ABC123" (unique, user-facing)
    String userId;              // Who claimed it (null if unclaimed)
    CouponStatus status;        // AVAILABLE, CLAIMED, REDEEMED, EXPIRED
    Instant claimedAt;
    Instant expiresAt;          // claimedAt + redeemDeadline
    Instant redeemedAt;
    String orderId;             // Order where coupon was used
}
public enum CouponStatus { AVAILABLE, CLAIMED, REDEEMED, EXPIRED, CANCELLED }

public class ClaimRequest {
    String requestId;           // Idempotency key
    String campaignId;
    String userId;
    Instant requestedAt;
    ClaimResult result;         // SUCCESS, SOLD_OUT, DUPLICATE, CAMPAIGN_NOT_LIVE
}

public class Redemption {
    String redemptionId;
    String couponId;
    String userId;
    String orderId;
    double discountApplied;
    Instant redeemedAt;
    RedemptionStatus status;    // SUCCESS, INVALID_COUPON, EXPIRED, ALREADY_REDEEMED
}

public class CampaignInventory {     // Redis counter
    String campaignId;
    int totalInventory;
    int remainingCount;          // Atomic counter (Redis DECR)
}

public class UserClaimRecord {       // Dedup: one claim per user per campaign
    String campaignId;
    String userId;
    String couponId;
    Instant claimedAt;
}
```

---

## 3️⃣ API Design

### 1. Create Campaign (Advertiser)
```
POST /api/v1/campaigns
Request: {
  "title": "50% Off AirPods - 5 Min Only!",
  "discount": { "type": "PERCENTAGE", "amount": 50 },
  "total_inventory": 10000,
  "scheduled_start": "2025-01-10T18:00:00Z",
  "duration_minutes": 5,
  "redeem_deadline_hours": 24,
  "targeting": { "geo": ["US"], "min_age": 18 }
}
Response (201): { "campaign_id": "camp_001", "status": "SCHEDULED", "start": "...", "end": "..." }
```

### 2. Get Campaign (User — Show on Feed/App)
```
GET /api/v1/campaigns/{campaign_id}
Response: {
  "campaign_id": "camp_001", "title": "50% Off AirPods",
  "status": "LIVE", "remaining": 3421, "total": 10000,
  "ends_at": "2025-01-10T18:05:00Z", "time_remaining_sec": 187
}
```

### 3. Claim Coupon (User — HOT PATH)
```
POST /api/v1/campaigns/{campaign_id}/claim
Headers: X-Idempotency-Key: req_abc123
Response (200): {
  "coupon_id": "cpn_001", "coupon_code": "FLASH-ABC123",
  "discount": "50% off", "expires_at": "2025-01-11T18:00:00Z",
  "remaining": 3420
}
Response (409): { "error": "SOLD_OUT" }
Response (409): { "error": "ALREADY_CLAIMED" }
Response (403): { "error": "CAMPAIGN_NOT_LIVE" }
```

### 4. Redeem Coupon (At Checkout)
```
POST /api/v1/coupons/{coupon_code}/redeem
Request: { "order_id": "ord_001", "order_total": 24999 }
Response (200): { "discount_applied": 12499, "new_total": 12500, "redeemed": true }
Response (400): { "error": "COUPON_EXPIRED" }
Response (400): { "error": "ALREADY_REDEEMED" }
```

### 5. Get Live Campaigns (User Feed)
```
GET /api/v1/campaigns/live?geo=US
Response: { "campaigns": [{ "id": "camp_001", "title": "...", "remaining": 3421, "ends_at": "..." }] }
```

### 6. Campaign Analytics (Advertiser Dashboard)
```
GET /api/v1/campaigns/{campaign_id}/analytics
Response: {
  "impressions": 500000, "claims": 10000, "redemptions": 3200,
  "claim_rate": 0.02, "redemption_rate": 0.32,
  "avg_claim_time_ms": 150, "peak_claims_per_sec": 85000
}
```

---

## 4️⃣ Data Flow

### Campaign Lifecycle
```
1. Advertiser creates campaign (SCHEDULED)
   ↓
2. Scheduler triggers at exact start time → campaign status = LIVE
   a. Pre-warm: load inventory counter into Redis (DECR counter ready)
   b. Pre-warm: notify CDN/edge to start serving campaign
   c. Push notification to targeted users: "Flash sale starting NOW!"
   ↓
3. Users see LIVE campaign → claim coupons (burst traffic)
   ↓
4. After 5 minutes OR inventory depleted → campaign status = ENDED/SOLD_OUT
   a. Reject any late claims
   b. Unclaimed coupons → can be reclaimed if waitlisted users exist
   ↓
5. Users redeem coupons at checkout within 24 hours
   ↓
6. After 24 hours: unclaimed/unredeemed coupons expire
```

### Coupon Claim (Critical Hot Path)
```
1. User taps "Claim Coupon" → POST /campaigns/{id}/claim
   ↓
2. Claim Service:
   a. CHECK TIMING: is campaign currently LIVE? (Redis: campaign:{id}:status)
      → If not LIVE → 403 CAMPAIGN_NOT_LIVE
   
   b. CHECK DUPLICATE: has user already claimed? (Redis SET: claimed:{campaign}:{user})
      → If exists → 409 ALREADY_CLAIMED
   
   c. DECREMENT INVENTORY: Redis DECR campaign:{id}:remaining
      → If result >= 0 → coupon available! Proceed.
      → If result < 0 → SOLD OUT. INCR back (undo). → 409 SOLD_OUT
   
   d. ASSIGN COUPON: pop from pre-generated coupon pool (Redis LIST: coupons:{campaign})
      LPOP coupons:camp_001 → "FLASH-ABC123"
   
   e. RECORD CLAIM:
      - SADD claimed:camp_001 user_123 (dedup set)
      - Write claim to Kafka "claims" topic (async DB persist)
   
   f. Return coupon code + details to user
   ↓
3. Claim Writer (Kafka consumer):
   a. Persist claim to Coupon DB (PostgreSQL)
   b. Update campaign analytics counters

All Redis operations: < 5ms total → response < 200ms
No DB in the hot path — Redis only for claims
```

### Coupon Redemption (At Checkout)
```
1. User enters coupon code at checkout
   ↓
2. Redemption Service:
   a. Look up coupon by code (Redis cache or DB)
   b. Validate:
      - Coupon exists? → 404 if not
      - Belongs to this user? → 403 if not
      - Status == CLAIMED? → 400 if EXPIRED or REDEEMED
      - Not expired? (claimedAt + 24h > now) → 400 if expired
   c. Atomically mark as REDEEMED:
      Redis: SET coupon:{code}:status REDEEMED (with CAS / Lua script)
      If race condition (two checkouts): only first succeeds
   d. Calculate discount, apply to order
   e. Persist redemption to DB (async via Kafka)
   ↓
3. Return discount applied + new order total
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────────┐
│                    ADVERTISERS + USERS                             │
│  Advertiser Dashboard (create campaigns, view analytics)         │
│  User App (view live campaigns, claim coupons, redeem at checkout)│
└──────────────────────────┬────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Auth, Rate Limit)                  │
└──────┬──────────────────────────┬──────────────────┬─────────────┘
       │ Campaign CRUD            │ Claim (HOT PATH) │ Redeem
       ▼                          ▼                  ▼
┌──────────────┐    ┌──────────────────┐   ┌──────────────────┐
│ CAMPAIGN     │    │ CLAIM SERVICE    │   │ REDEMPTION       │
│ SERVICE      │    │                  │   │ SERVICE          │
│              │    │ • Check timing   │   │                  │
│ • CRUD       │    │ • Check dedup    │   │ • Validate coupon│
│ • Schedule   │    │ • Decrement inv  │   │ • Mark redeemed  │
│ • Analytics  │    │ • Assign coupon  │   │ • Apply discount │
│              │    │ • All in Redis   │   │                  │
│ Pods: 10     │    │ Pods: 50-200     │   │ Pods: 20         │
└──────┬───────┘    │ (auto-scale      │   └────────┬─────────┘
       │            │  on campaign      │            │
       │            │  start)           │            │
       │            └────────┬──────────┘            │
       │                     │                       │
  ┌────┼─────────────────────┼───────────────────────┼────┐
  │    ▼                     ▼                       ▼    │
  │ ┌──────────┐  ┌──────────────────┐  ┌──────────────┐ │
  │ │Campaign  │  │ INVENTORY +      │  │ Coupon DB     │ │
  │ │DB        │  │ CLAIM REDIS      │  │ (PostgreSQL)  │ │
  │ │(Postgres)│  │ CLUSTER          │  │               │ │
  │ │          │  │                  │  │ • Coupons     │ │
  │ │• Campaigns│ │ • remaining:     │  │ • Claims      │ │
  │ │• Targeting│ │   DECR counter   │  │ • Redemptions │ │
  │ │• Analytics│ │ • claimed:{c}:{u}│  │               │ │
  │ │          │  │   dedup SET      │  │ ~7 GB/day     │ │
  │ │ ~2 MB/day│  │ • coupons:{c}    │  │               │ │
  │ │          │  │   pre-gen LIST   │  └──────────────┘ │
  │ └──────────┘  │ • coupon:{code}  │                   │
  │               │   status HASH    │                   │
  │               │                  │                   │
  │               │ ~100 MB per      │                   │
  │               │ campaign         │                   │
  │               └──────────────────┘                   │
  └──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│  KAFKA: "claims" (async persist to DB) + "analytics" (counters)  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│  CAMPAIGN SCHEDULER                                               │
│  • Cron-based: 1 second precision                                │
│  • At scheduled_start: flip campaign status to LIVE in Redis     │
│  • At scheduled_end: flip to ENDED, reject new claims            │
│  • Pre-warm Redis 30 seconds before start (load inventory, codes)│
│  • Trigger push notifications to targeted users                  │
└──────────────────────────────────────────────────────────────────┘
```

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Campaign Service** | CRUD, scheduling, analytics | Go on K8s | 10 pods |
| **Claim Service** | Process coupon claims (HOT PATH) | Go on K8s | 50-200 pods (auto-scale on campaign start) |
| **Redemption Service** | Validate + apply coupons at checkout | Go on K8s | 20 pods |
| **Inventory Redis** | Atomic counters + dedup + coupon pool | Redis Cluster | ~100 MB/campaign |
| **Campaign DB** | Campaign metadata, targeting | PostgreSQL | ~small |
| **Coupon DB** | Coupon records, claims, redemptions | PostgreSQL | ~7 GB/day |
| **Kafka** | Async claim persistence + analytics pipeline | Kafka | 5 brokers |
| **Campaign Scheduler** | Precise start/stop timing | Go + Redis (distributed lock) | 3 pods (leader election) |

---

## 6️⃣ Deep Dives

### DD1: Atomic Inventory Control — Zero Overselling

**Problem**: 100K users try to claim 10K coupons in 30 seconds. Must never give out coupon #10,001.

```
Redis DECR for atomic inventory:

Pre-warm: SET campaign:camp_001:remaining 10000

On claim:
  remaining = DECR campaign:camp_001:remaining
  if remaining >= 0:
    → Claim succeeds! Assign coupon.
  else:
    → Sold out! INCR campaign:camp_001:remaining (undo the over-decrement)
    → Return 409 SOLD_OUT

Why this works:
  - DECR is ATOMIC in Redis (single-threaded command execution)
  - No race condition: two concurrent DECRs will return 0 and -1 respectively
  - Only the one that got >= 0 wins
  - The -1 response undoes immediately (INCR)

Performance: DECR = O(1), < 0.1ms per operation
  Redis single instance: ~500K DECR/sec → handles 100K claims/sec easily
  
Alternative (Lua script for atomic check-and-decrement):
  EVAL "
    local remaining = redis.call('GET', KEYS[1])
    if tonumber(remaining) > 0 then
      return redis.call('DECR', KEYS[1])
    else
      return -1
    end
  " 1 campaign:camp_001:remaining
```

### DD2: User Deduplication — One Claim Per User Per Campaign

**Problem**: User rapidly clicks "Claim" 10 times, or retries after timeout. Must give exactly one coupon.

```
Redis SET for dedup:

On claim:
  result = SADD claimed:camp_001 user_123
  if result == 1: → new member (first claim) → proceed
  if result == 0: → already exists → 409 ALREADY_CLAIMED

SADD is atomic → no race condition between concurrent clicks

Combined with DECR (Lua script for both operations atomically):
  EVAL "
    local already = redis.call('SISMEMBER', KEYS[1], ARGV[1])  -- check dedup
    if already == 1 then return -2 end                           -- already claimed
    local remaining = redis.call('DECR', KEYS[2])               -- decrement inventory
    if remaining < 0 then
      redis.call('INCR', KEYS[2])                               -- undo
      return -1                                                  -- sold out
    end
    redis.call('SADD', KEYS[1], ARGV[1])                        -- mark claimed
    return redis.call('LPOP', KEYS[3])                           -- assign coupon code
  " 3 claimed:camp_001 campaign:camp_001:remaining coupons:camp_001 user_123

Single Lua script = atomic: dedup + decrement + assign coupon in ONE operation
No possibility of: decrement without dedup, or dedup without coupon assignment
```

### DD3: Precise Campaign Timing — ±1 Second Accuracy

**Problem**: Campaign must go live at EXACTLY 18:00:00. Not 17:59:58 or 18:00:02. Users must not claim early.

```
Campaign lifecycle timing:

Pre-warm (T-30 seconds):
  - Load inventory counter to Redis
  - Pre-generate coupon codes → Redis LIST
  - Pre-scale Claim Service pods (auto-scaler triggered)
  - Send push notifications: "Flash sale in 30 seconds!"

Activation (T=0, ±1 second):
  Campaign Scheduler checks every 1 second:
    if now() >= campaign.scheduledStart AND status == SCHEDULED:
      SET campaign:camp_001:status LIVE (Redis)
      → Campaign is now live

  Claim Service checks: if campaign status != LIVE → reject claim
  → Transition is instant (single Redis SET)

Deactivation (T+5 minutes):
  Scheduler: SET campaign:camp_001:status ENDED
  → All subsequent claims rejected immediately

Clock synchronization:
  All pods use NTP-synced system clocks (< 50ms drift)
  Scheduler uses Redis-based distributed lock (only one scheduler instance)
  Campaign status in Redis = single source of truth for live/not-live
```

### DD4: Burst Traffic — 100K Claims/Sec Auto-Scaling

**Problem**: At T=0, 500K users hit "Claim" simultaneously. Normal traffic is 1K/sec. Must scale from 1K to 100K/sec in seconds.

```
Pre-scaling strategy:

1. PREDICTIVE SCALING: Claim Service auto-scaler pre-provisions pods
   - 30 seconds before campaign start: scale to max pods (200)
   - After campaign ends (5 min): scale back down
   - Triggered by Campaign Scheduler (sends scale event)

2. REDIS CAPACITY: single Redis cluster handles 500K ops/sec
   - DECR + SADD + LPOP = 3 ops per claim = 300K claims/sec capacity
   - If needed: Redis Cluster with hash slots by campaign_id

3. API GATEWAY: burst rate limiting
   - Per-user: max 5 requests/sec (prevent bot abuse)
   - Per-campaign: no limit (legitimate burst)
   - Global: 200K requests/sec capacity

4. LOAD BALANCER: pre-warmed connections
   - Establish connection pools 30s before campaign
   - Avoid cold-start TCP handshake overhead during burst

5. QUEUE-BASED OVERFLOW (fallback):
   - If burst exceeds capacity: queue claims in Kafka
   - Process from queue: may add ~2-3 seconds latency
   - Better than: dropping requests or returning 503
```

### DD5: Coupon Code Management — Pre-Generated Pool

**Problem**: Must assign unique coupon codes to users instantly during burst. Can't generate codes on-the-fly (too slow under contention).

```
Pre-generation strategy:

Before campaign starts (during pre-warm, T-30s):
  1. Generate 10K unique codes: "FLASH-" + random 6-char alphanumeric
  2. Push all codes to Redis LIST: RPUSH coupons:camp_001 "FLASH-ABC123" "FLASH-DEF456" ...
  3. On claim: LPOP coupons:camp_001 → instant, O(1), atomic

Code generation:
  - UUID-based: "FLASH-" + first 6 chars of UUID = "FLASH-A3F2B1"
  - Or sequential with random offset: "FLASH-" + (offset + index).toString(36)
  - Validate uniqueness in batch before pushing to Redis

Code storage:
  - Redis LIST during campaign (fast LPOP)
  - After claim: code stored in Coupon DB (PostgreSQL) for redemption lookup
  - Index: coupon_code → { campaign_id, user_id, status, expires_at }

Code lookup at redemption:
  - Redis HASH: coupon:{code} → { campaign_id, user_id, status, expires_at }
  - Or PostgreSQL index on coupon_code (for less frequent redemption path)
```

### DD6: Redemption Validation — Atomic at Checkout

**Problem**: User tries to redeem coupon. Must validate: exists, belongs to user, not expired, not already redeemed. Two concurrent checkout attempts must not both succeed.

```
Atomic redemption (Redis Lua script):

EVAL "
  local status = redis.call('HGET', KEYS[1], 'status')
  if status ~= 'CLAIMED' then return 'INVALID_STATUS:' .. (status or 'NOT_FOUND') end
  
  local user = redis.call('HGET', KEYS[1], 'user_id')
  if user ~= ARGV[1] then return 'WRONG_USER' end
  
  local expires = tonumber(redis.call('HGET', KEYS[1], 'expires_at'))
  if tonumber(ARGV[2]) > expires then return 'EXPIRED' end
  
  redis.call('HSET', KEYS[1], 'status', 'REDEEMED')
  redis.call('HSET', KEYS[1], 'redeemed_at', ARGV[2])
  redis.call('HSET', KEYS[1], 'order_id', ARGV[3])
  return 'SUCCESS'
" 1 coupon:FLASH-ABC123 user_123 1736553600 ord_001

All validation + status update in ONE atomic operation
No race condition between two concurrent checkouts
```

### DD7: Expiration & Cleanup — Unclaimed and Unredeemed Coupons

**Problem**: After 24 hours, unclaimed coupons should be reclaimed. Unredeemed claimed coupons should expire. Must handle gracefully.

```
Expiration pipeline:

1. CLAIMED BUT NOT REDEEMED (24h expiry):
   - Background job runs every 5 minutes
   - Scan coupon DB: WHERE status = 'CLAIMED' AND claimedAt + 24h < NOW()
   - Mark as EXPIRED
   - INCR campaign:{id}:remaining (return to inventory if campaign still has time)

2. UNCLAIMED DURING CAMPAIGN:
   - After campaign ends (5 min): remaining inventory = unclaimed
   - If waitlist enabled: assign to waitlisted users (FIFO)
   - Else: mark campaign as ENDED, unclaimed coupons stay AVAILABLE (for late-arriving users? No — campaign over)

3. REDIS CLEANUP:
   - 1 hour after campaign ends: delete Redis keys
     DEL campaign:camp_001:remaining
     DEL claimed:camp_001
     DEL coupons:camp_001
   - Coupon lookup data stays in Redis for 24h (redemption window)
   - After 24h: delete coupon:{code} hashes from Redis
```

### DD8: Fairness & Anti-Fraud — Preventing Bot Abuse

**Problem**: Bots can claim all 10K coupons in seconds. Must ensure fair access for real users.

```
Anti-bot measures:

1. RATE LIMITING: per-user, per-IP
   - Max 5 claim requests/sec per user
   - Max 50 requests/sec per IP (shared networks)
   - 429 response with Retry-After header

2. CAPTCHA / CHALLENGE: for suspicious traffic
   - If >3 rapid claims from same IP → CAPTCHA gate
   - Device fingerprint check (is this a real browser/app?)

3. QUEUE-BASED FAIRNESS (for very popular campaigns):
   - Instead of direct Redis DECR: join a virtual queue
   - Queue processes in FIFO order
   - Each user gets a position: "You are #2345 in line"
   - Prevents: fastest network connection wins

4. ONE PER USER: enforced via SADD (user_id in claimed set)
   - Even if user has multiple devices/sessions
   - Auth required: must be logged in to claim

5. FRAUD DETECTION: post-claim analysis
   - Flag: same credit card across multiple accounts
   - Flag: bulk redemptions from same IP
   - Auto-revoke coupons if fraud detected
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Inventory control** | Redis DECR (atomic counter) | Zero overselling; O(1); 500K ops/sec single instance |
| **User dedup** | Redis SADD (atomic set add) | One claim per user; O(1); combined with DECR in Lua script |
| **Claim hot path** | ALL in Redis (no DB in hot path) | < 200ms claims; DB write is async via Kafka |
| **Coupon assignment** | Pre-generated codes in Redis LIST (LPOP) | Instant O(1) assignment; no generation under contention |
| **Campaign timing** | Redis status flag + NTP-synced scheduler | ±1 second precision; single source of truth for live/not-live |
| **Burst scaling** | Pre-scale pods 30s before campaign; Redis handles throughput | 100K claims/sec; no cold start; predictive scaling |
| **Redemption** | Atomic Lua script (validate + mark redeemed) | No double-redemption; single atomic operation |
| **Persistence** | Async via Kafka (claims persisted after Redis) | Hot path stays fast; eventual consistency for DB |

## Interview Talking Points

1. **"Redis DECR for atomic inventory: zero overselling at 100K claims/sec"** — DECR is atomic in Redis. If result >= 0 → claim succeeds. If < 0 → INCR to undo, return SOLD_OUT. Single Redis instance handles 500K DECR/sec.

2. **"Lua script combines dedup + decrement + assign in ONE atomic operation"** — SISMEMBER (check dedup) → DECR (inventory) → SADD (mark claimed) → LPOP (assign code). All atomic. No race conditions possible.

3. **"No DB in the claim hot path — Redis only, async persist via Kafka"** — Claim completes in < 200ms (3 Redis ops). DB write happens async via Kafka consumer. Claim Service is stateless → auto-scales to 200 pods.

4. **"Pre-warm 30 seconds before: load inventory, pre-generate codes, pre-scale pods"** — Campaign Scheduler triggers at T-30s. Redis loaded. Pods scaled. Push notifications sent. At T=0: flip status to LIVE, claims flow instantly.

5. **"±1 second campaign timing via Redis status flag + NTP-synced scheduler"** — Scheduler checks every 1 second. Campaign status in Redis = single source of truth. Claim Service checks status before processing. Late claims rejected immediately.

6. **"Anti-bot: rate limiting + CAPTCHA + queue-based fairness + fraud detection"** — Per-user rate limit (5 req/sec). CAPTCHA for suspicious IPs. Virtual queue for ultra-popular campaigns. Post-claim fraud detection auto-revokes suspicious coupons.

---

## 🔗 Related Problems
| Problem | Key Difference |
|---------|---------------|
| **Flash Sale (Amazon Lightning Deals)** | Our exact design |
| **Ticket Booking (concerts)** | Similar inventory contention; tickets have seats, coupons are fungible |
| **Rate Limiter** | Same token bucket pattern; we limit claims not API calls |
| **Stock Trading** | Similar atomic decrement; stocks have market price, coupons are fixed |

## 🔧 Technology Alternatives
| Component | Chosen | Alternative |
|-----------|--------|------------|
| **Inventory counter** | Redis DECR | DynamoDB atomic counter / PostgreSQL SELECT FOR UPDATE |
| **Dedup** | Redis SADD | Bloom filter (probabilistic) / DB unique constraint |
| **Coupon pool** | Redis LIST (LPOP) | Pre-assigned in DB / UUID generation on-the-fly |
| **Scheduling** | Redis + Go scheduler | Temporal / AWS Step Functions |
| **Async persistence** | Kafka | SQS / RabbitMQ |

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Time**: 45-60 min
**References**: Amazon Lightning Deals Architecture, Redis Atomic Operations, Lua Scripting for Atomicity, Flash Sale Design Patterns
