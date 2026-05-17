# 🎯 Topic 54: Distributed Rate Limiting — Token Bucket in Redis

> **System Design Interview — Deep Dive**
> A comprehensive guide covering distributed rate limiting with the Token Bucket algorithm implemented in Redis, Lua script atomicity, Redis Cluster sharding for rate limit keys, local + remote hybrid strategies, multi-tier & hierarchical limiting, sliding window hybrids, clock handling, failover patterns, cost-aware token sizes, observability, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Distributed Rate Limiting Is Hard](#why-distributed-rate-limiting-is-hard)
3. [Token Bucket Refresher — The Mental Model](#token-bucket-refresher--the-mental-model)
4. [Implementing Token Bucket in Redis](#implementing-token-bucket-in-redis)
5. [Atomic Lua Script — Line-by-Line Walkthrough](#atomic-lua-script--line-by-line-walkthrough)
6. [Variable-Cost Token Consumption](#variable-cost-token-consumption)
7. [Redis Key Design for Rate Limiting](#redis-key-design-for-rate-limiting)
8. [Redis Cluster Considerations](#redis-cluster-considerations)
9. [Local + Remote Hybrid Architecture](#local--remote-hybrid-architecture)
10. [Multi-Tier & Hierarchical Rate Limiting](#multi-tier--hierarchical-rate-limiting)
11. [Sliding Window + Token Bucket Hybrid](#sliding-window--token-bucket-hybrid)
12. [Clock and Time Handling](#clock-and-time-handling)
13. [Failover and Graceful Degradation](#failover-and-graceful-degradation)
14. [Rate Limit Observability](#rate-limit-observability)
15. [Performance Tuning & Benchmarks](#performance-tuning--benchmarks)
16. [Real-World Production Patterns](#real-world-production-patterns)
17. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
18. [Common Interview Mistakes](#common-interview-mistakes)
19. [Distributed Rate Limiting by System Design Problem](#distributed-rate-limiting-by-system-design-problem)
20. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Distributed rate limiting** enforces request quotas across a fleet of application servers using a centralized or coordinated store (typically Redis). Unlike local in-memory limiters, a distributed rate limiter provides a **globally consistent** view of a client's request budget regardless of which server handles the request.

```
The fundamental problem:

  50 API servers, each with a local rate limiter (100 req/min per user)

  User sends 50 requests, each to a different server:
    Server 1:  1 request  → local count = 1  → ALLOW
    Server 2:  1 request  → local count = 1  → ALLOW
    ...
    Server 50: 1 request  → local count = 1  → ALLOW

  Result: 50 requests allowed. Each server only saw 1.
  The user could send 50 × 100 = 5,000 req/min while the intended limit is 100.

  Solution: All 50 servers share the same counter in Redis.
    Every server INCRs the same key → global count is accurate.
```

### Why Token Bucket + Redis?

| Factor | Why Token Bucket | Why Redis |
|---|---|---|
| **Burst tolerance** | Allows natural traffic bursts up to bucket capacity | — |
| **Smooth average** | Refill rate enforces a long-term average | — |
| **Memory efficiency** | Only 2 fields per key (`tokens`, `last_refill`) | — |
| **Global state** | — | Single shared counter across all servers |
| **Sub-ms latency** | — | In-memory → 0.1–0.5 ms per call |
| **Atomic operations** | — | Lua scripts execute without race conditions |
| **TTL auto-cleanup** | — | `EXPIRE` removes stale buckets automatically |
| **Battle-tested** | Used by Stripe, Cloudflare, GitHub, Discord | Redis Cluster handles 100K+ ops/sec per shard |

---

## Why Distributed Rate Limiting Is Hard

### Challenge 1: Race Conditions

```
Two servers concurrently check the same user's bucket:

  Server A: reads tokens = 1
  Server B: reads tokens = 1         ← stale read
  Server A: decrements → tokens = 0
  Server B: decrements → tokens = -1 ← limit exceeded!

  Both requests pass even though only 1 token was available.
  This is a classic TOCTOU (Time-Of-Check-Time-Of-Use) bug.
```

**Solution**: Redis Lua scripts execute atomically — read + check + decrement in a single uninterruptible operation.

### Challenge 2: Network Latency

```
Every request must check Redis before processing:

  Request → Redis check (0.3ms) → Process (5ms) → Response
  vs.
  Request → Local check (0.01ms) → Process (5ms) → Response

  At 10K requests/sec, that's 3 seconds of cumulative Redis latency per second.
  Acceptable? Usually yes — 0.3ms is negligible per-request.
  At extreme scale? Use local + remote hybrid (Section 9).
```

### Challenge 3: Redis Availability

```
If Redis goes down, what happens to rate limiting?

  Option A: Fail OPEN  → allow all traffic (risk: abuse during outage)
  Option B: Fail CLOSED → reject all traffic (risk: total service outage)
  Option C: Fail to LOCAL → fall back to in-memory limiter (imperfect but functional)

  Best practice: Option C with Option A as ultimate fallback.
```

### Challenge 4: Clock Skew

```
Server A clock: 12:00:00.000
Server B clock: 12:00:00.150  ← 150ms ahead

  Server A calculates refill based on its clock.
  Server B calculates a slightly different refill.
  Over time, different servers compute different token counts.

  Solution: Use Redis server time (redis.call('TIME')) inside Lua scripts,
            NOT the application server's clock.
```

### Challenge 5: Hot Keys in Redis Cluster

```
Rate limit key: ratelimit:user:12345

  If User 12345 makes 10K requests/sec, the Redis shard holding
  this key becomes a hot spot.

  Solution: Hash tags for co-location or accept single-shard hot keys
            (Redis handles 100K+ ops/sec per shard — usually fine).
```

---

## Token Bucket Refresher — The Mental Model

```
Imagine a bucket that holds marbles:

  ┌───────────────┐
  │ ● ● ● ● ● ● ● │  capacity = 10 (max marbles)
  │ ● ● ●         │  current = 7 marbles
  └───────────────┘
        ↑
  A machine drops 2 marbles/second into the bucket (refill_rate).
  If the bucket is full, extra marbles are discarded (capped at capacity).

  Each API request takes 1 marble out.
  If the bucket is empty → request is rejected (429).

  Key insight: The user can "save up" marbles during idle time,
               then spend them in a burst.

  Parameters:
    capacity (max_tokens):  Maximum burst size
    refill_rate:            Sustained requests/sec allowed
    current_tokens:         Tokens available right now
    last_refill_time:       When we last calculated a refill
```

### Lazy Refill (Critical for Redis Implementation)

```
We do NOT run a background process that adds tokens every second.
That would require a timer per user — millions of timers = impractical.

Instead, we calculate refill ON DEMAND when a request arrives:

  1. Request arrives at time T_now
  2. Read last_refill_time (T_last) and current tokens from Redis
  3. elapsed = T_now - T_last
  4. new_tokens = min(capacity, current + elapsed × refill_rate)
  5. Attempt to consume 1 token from new_tokens
  6. Write updated tokens and T_now back to Redis

  This is called "lazy evaluation" — we compute the refill retroactively.
  No background jobs. No timers. Just math on each request.

  Example:
    capacity = 10, refill_rate = 2/sec
    Last request was 3 seconds ago. tokens was 4.
    new_tokens = min(10, 4 + 3 × 2) = min(10, 10) = 10
    Consume 1 → tokens = 9. Write back.
```

---

## Implementing Token Bucket in Redis

### Data Model

```
Redis Hash per rate-limit key:

  KEY:    ratelimit:{user_id}:{endpoint}
  FIELDS:
    tokens      → float (current token count)
    last_refill → float (Unix timestamp with ms precision)

  Example:
    ratelimit:user_42:/api/search
      tokens:      7.5
      last_refill: 1710500000.123

  TTL: Auto-expire after inactivity (e.g., 2× the refill-to-full time)
       If capacity=100 and refill_rate=10/sec, full refill in 10 seconds.
       Set TTL = 3600 (1 hour) to handle long idle periods.
```

### Non-Atomic Approach (❌ DO NOT USE)

```python
# This has a RACE CONDITION — shown here to explain WHY we need Lua

def check_rate_limit(redis_client, user_id, max_tokens, refill_rate):
    key = f"ratelimit:{user_id}"
    now = time.time()
    
    # Step 1: Read current state
    data = redis_client.hmget(key, 'tokens', 'last_refill')       # NETWORK CALL 1
    tokens = float(data[0]) if data[0] else max_tokens
    last_refill = float(data[1]) if data[1] else now
    
    # Step 2: Calculate refill
    elapsed = now - last_refill
    tokens = min(max_tokens, tokens + elapsed * refill_rate)
    
    # ⚠️ RACE WINDOW: Between Step 1 (read) and Step 3 (write),
    #    another server can read the SAME old token count,
    #    both think tokens > 0, both allow the request.
    
    # Step 3: Consume and write back
    if tokens >= 1:
        tokens -= 1
        redis_client.hmset(key, {'tokens': tokens, 'last_refill': now})  # NETWORK CALL 2
        redis_client.expire(key, 3600)                                    # NETWORK CALL 3
        return True  # ALLOWED
    else:
        return False  # REJECTED
```

**Problems**:
1. **TOCTOU race condition** — 3 separate Redis calls, not atomic.
2. **3 network round-trips** — slower (0.3ms × 3 = ~1ms).
3. **Partial failure** — if write fails after read, state is inconsistent.

---

## Atomic Lua Script — Line-by-Line Walkthrough

### The Production-Grade Lua Script

```lua
-- TOKEN BUCKET RATE LIMITER — Atomic Redis Lua Script
-- Returns: {allowed (0/1), tokens_remaining, retry_after_ms}

-- KEYS[1] = rate limit key (e.g., "ratelimit:user_42:/api/search")
local key = KEYS[1]

-- ARGV[1] = max_tokens (bucket capacity)
-- ARGV[2] = refill_rate (tokens per second)
-- ARGV[3] = now (current Unix timestamp with ms precision)
-- ARGV[4] = cost (tokens to consume, usually 1)
local max_tokens  = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now         = tonumber(ARGV[3])
local cost        = tonumber(ARGV[4]) or 1

-- Step 1: Read current bucket state
local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens      = tonumber(data[1])
local last_refill = tonumber(data[2])

-- Step 2: Initialize if first request (bucket starts full)
if tokens == nil then
    tokens = max_tokens
    last_refill = now
end

-- Step 3: Calculate lazy refill
local elapsed    = math.max(0, now - last_refill)
local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate)

-- Step 4: Attempt to consume tokens
if new_tokens >= cost then
    -- ALLOWED: consume tokens
    new_tokens = new_tokens - cost
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) + 60)
    return {1, math.floor(new_tokens * 1000) / 1000, 0}
    --     allowed=1, remaining tokens, retry_after=0
else
    -- REJECTED: calculate retry-after (time until enough tokens refill)
    local deficit = cost - new_tokens
    local retry_after_ms = math.ceil((deficit / refill_rate) * 1000)
    -- Update state even on rejection (to record the refill calculation)
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) + 60)
    return {0, math.floor(new_tokens * 1000) / 1000, retry_after_ms}
    --     allowed=0, remaining tokens, retry_after in ms
end
```

### Why Each Part Matters

```
Step 1 — HMGET: Single command reads both fields. Atomic within Lua context.

Step 2 — Initialize: On the very first request, the bucket starts full.
  This is user-friendly: a new user gets their full burst allowance immediately.
  Alternative: Start empty and let the user build up tokens. More restrictive.

Step 3 — Lazy refill:
  elapsed = now - last_refill  → seconds since last check
  new_tokens = tokens + elapsed × refill_rate  → retroactive refill
  min(max_tokens, ...)  → cap at bucket capacity (no overflow)
  math.max(0, ...)  → handle clock weirdness (elapsed should never be negative)

Step 4a — Allowed:
  Deduct the cost, update state, set TTL, return success + remaining tokens.

Step 4b — Rejected:
  Calculate how long until enough tokens are available.
  deficit = cost - new_tokens (how many more tokens we need)
  retry_after = deficit / refill_rate (seconds to wait)
  Still update state to record the refill that happened.
  Return failure + retry_after so the client knows when to retry.

TTL = max_tokens / refill_rate + 60:
  Time for a completely empty bucket to refill, plus 60s buffer.
  Ensures inactive users' keys are cleaned up automatically.
  Example: capacity=100, rate=10/sec → TTL = 10 + 60 = 70 seconds.
```

### Calling From Application Code (Python)

```python
import redis
import time

class DistributedTokenBucket:
    """Production-grade distributed rate limiter using Redis Token Bucket."""
    
    LUA_SCRIPT = """
    local key = KEYS[1]
    local max_tokens  = tonumber(ARGV[1])
    local refill_rate = tonumber(ARGV[2])
    local now         = tonumber(ARGV[3])
    local cost        = tonumber(ARGV[4]) or 1
    
    local data = redis.call('HMGET', key, 'tokens', 'last_refill')
    local tokens      = tonumber(data[1])
    local last_refill = tonumber(data[2])
    
    if tokens == nil then
        tokens = max_tokens
        last_refill = now
    end
    
    local elapsed    = math.max(0, now - last_refill)
    local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate)
    
    if new_tokens >= cost then
        new_tokens = new_tokens - cost
        redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
        redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) + 60)
        return {1, math.floor(new_tokens * 1000) / 1000, 0}
    else
        local deficit = cost - new_tokens
        local retry_after_ms = math.ceil((deficit / refill_rate) * 1000)
        redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
        redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) + 60)
        return {0, math.floor(new_tokens * 1000) / 1000, retry_after_ms}
    end
    """
    
    def __init__(self, redis_client, max_tokens, refill_rate):
        self.redis = redis_client
        self.max_tokens = max_tokens
        self.refill_rate = refill_rate
        self.script = self.redis.register_script(self.LUA_SCRIPT)
    
    def allow(self, key: str, cost: int = 1) -> dict:
        """
        Check if a request is allowed.
        Returns: {allowed: bool, remaining: float, retry_after_ms: int}
        """
        now = time.time()
        result = self.script(
            keys=[key],
            args=[self.max_tokens, self.refill_rate, now, cost]
        )
        return {
            'allowed': bool(result[0]),
            'remaining': result[1] / 1000,  # Lua returns tokens × 1000
            'retry_after_ms': result[2]
        }

# Usage
r = redis.Redis(host='redis-cluster.example.com', port=6379, decode_responses=False)
limiter = DistributedTokenBucket(r, max_tokens=100, refill_rate=10)  # 100 burst, 10/sec sustained

# In request handler
result = limiter.allow(f"ratelimit:user:{user_id}:/api/search")
if not result['allowed']:
    return Response(
        status=429,
        headers={
            'Retry-After': str(result['retry_after_ms'] / 1000),
            'X-RateLimit-Limit': '100',
            'X-RateLimit-Remaining': str(result['remaining']),
        },
        body={'error': 'rate_limit_exceeded'}
    )
```

### Calling From Application Code (Java)

```java
public class DistributedTokenBucket {
    private final JedisPool jedisPool;
    private final int maxTokens;
    private final double refillRate;
    private final String luaScript;
    private String scriptSha;
    
    public DistributedTokenBucket(JedisPool pool, int maxTokens, double refillRate) {
        this.jedisPool = pool;
        this.maxTokens = maxTokens;
        this.refillRate = refillRate;
        this.luaScript = loadLuaScript();  // Load from file or string
        try (Jedis jedis = pool.getResource()) {
            this.scriptSha = jedis.scriptLoad(luaScript);  // Pre-cache script
        }
    }
    
    public RateLimitResult allow(String key) {
        return allow(key, 1);
    }
    
    public RateLimitResult allow(String key, int cost) {
        try (Jedis jedis = jedisPool.getResource()) {
            double now = System.currentTimeMillis() / 1000.0;
            List<String> keys = Collections.singletonList(key);
            List<String> args = Arrays.asList(
                String.valueOf(maxTokens),
                String.valueOf(refillRate),
                String.valueOf(now),
                String.valueOf(cost)
            );
            
            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) jedis.evalsha(scriptSha, keys, args);
            
            return new RateLimitResult(
                result.get(0) == 1,             // allowed
                result.get(1) / 1000.0,         // remaining tokens
                result.get(2).intValue()         // retry_after_ms
            );
        } catch (JedisNoScriptException e) {
            // Script evicted from cache — reload and retry
            try (Jedis jedis = jedisPool.getResource()) {
                this.scriptSha = jedis.scriptLoad(luaScript);
            }
            return allow(key, cost);  // retry once
        }
    }
}
```

---

## Variable-Cost Token Consumption

Not all requests are equal. A search query is cheaper than uploading a 10 GB file.

### Cost-Based Rate Limiting

```
Bucket: capacity=1000, refill_rate=100/sec

  GET  /api/search          → cost = 1 token
  POST /api/upload (1MB)    → cost = 10 tokens
  POST /api/upload (100MB)  → cost = 100 tokens
  POST /api/bulk-export     → cost = 500 tokens

Why? Expensive operations consume more resources:
  - Search: hits cache, cheap
  - Upload: writes to S3 + DynamoDB + triggers processing pipeline
  - Bulk export: scans entire table, heavy on DB

The Lua script already supports this via the `cost` parameter (ARGV[4]).
```

### Dynamic Cost Calculation

```python
def get_request_cost(request):
    """Calculate token cost based on request type and payload."""
    
    endpoint_costs = {
        'GET /api/search': 1,
        'GET /api/feed': 2,
        'POST /api/tweet': 5,
        'POST /api/upload': lambda r: max(1, r.content_length // (1024 * 1024)),  # 1 token per MB
        'POST /api/bulk-export': 500,
    }
    
    cost_or_fn = endpoint_costs.get(f"{request.method} {request.path}", 1)
    if callable(cost_or_fn):
        return cost_or_fn(request)
    return cost_or_fn
```

### Interview Script
> *"Not all requests cost the same. I'd assign variable token costs: a read costs 1 token, a tweet costs 5, and a bulk export costs 500. This way, a user with 1,000 tokens can do 1,000 reads or 2 bulk exports — reflecting the actual server-side cost. The Lua script accepts a cost parameter, so no changes to the rate limiter itself."*

---

## Redis Key Design for Rate Limiting

### Key Naming Convention

```
Pattern: ratelimit:{dimension}:{identifier}:{scope}

Examples:
  ratelimit:user:12345:/api/search          # Per-user, per-endpoint
  ratelimit:user:12345:global               # Per-user, all endpoints
  ratelimit:ip:203.0.113.42:global          # Per-IP
  ratelimit:apikey:sk_live_abc123:global    # Per-API-key
  ratelimit:tenant:acme-corp:global         # Per-tenant (multi-tenant SaaS)
  ratelimit:global:/api/search              # Global endpoint limit
```

### Key Sizing

```
Each rate limit key uses a Redis hash with 2 fields:
  tokens (float as string):      ~8-12 bytes
  last_refill (float as string): ~15-18 bytes
  Hash overhead:                 ~100 bytes (Redis hash metadata)

  Total per key: ~130 bytes

  Scale calculation:
    10M users × 1 key/user    = 10M keys × 130 bytes = 1.3 GB
    10M users × 5 keys/user   = 50M keys × 130 bytes = 6.5 GB
    (5 keys = 5 different endpoint limits per user)

  A single 16 GB Redis instance can handle ~120M rate limit keys.
  With Redis Cluster (e.g., 6 shards): ~720M keys.
```

### TTL Strategy

```
Set TTL = time_to_full_refill + buffer

  capacity=100, refill_rate=10/sec → full refill in 10 sec
  TTL = 10 + 60 = 70 seconds (generous buffer)

  Why?
    - Too short TTL: Key expires mid-use → user gets a free refill → burst exploit
    - Too long TTL: Stale keys waste memory
    - TTL refreshed on every request (Lua script calls EXPIRE) → active users' keys never expire
    - Only truly inactive users' keys get cleaned up
    
  For per-day limits (e.g., 1000 requests/day):
    refill_rate = 1000/86400 ≈ 0.0116/sec
    TTL = 86400 + 3600 = 90000 seconds (25 hours)
```

---

## Redis Cluster Considerations

### How Redis Cluster Routes Rate Limit Keys

```
Redis Cluster has 16,384 hash slots.
Each key is assigned to a slot: CRC16(key) mod 16384.
Each shard owns a range of slots.

  Key: ratelimit:user:42:/api/search
  CRC16("ratelimit:user:42:/api/search") mod 16384 → slot 8721
  Shard 3 owns slots 8192-12287 → routed to Shard 3

  Different users land on different shards → natural load distribution.
  But one user's different endpoints land on different shards too.
```

### Hash Tags for Co-location

```
Problem: Multi-key operations (e.g., hierarchical limiting) need all
         keys on the same shard. Redis Cluster doesn't support cross-shard
         Lua scripts.

Solution: Hash tags — Redis only hashes the content inside {}.

  ratelimit:{user:42}:/api/search   → CRC16("user:42") → slot X
  ratelimit:{user:42}:/api/upload   → CRC16("user:42") → slot X
  ratelimit:{user:42}:global        → CRC16("user:42") → slot X

  All keys for User 42 land on the same shard.
  Now a single Lua script can atomically check all limits for one user.

Tradeoff: If one user is extremely active, their shard gets hot.
          For most workloads, this is fine — Redis handles 100K+ ops/sec/shard.
```

### Cross-Shard Global Limits

```
Global limit: /api/search → max 50,000 requests/sec across all users

  Problem: A global key lives on ONE shard. At 50K ops/sec, that shard is a hotspot.

  Solution 1: Approximate with per-shard counters
    Split into N sub-keys: ratelimit:global:/api/search:{shard_0..N}
    Each server increments a random sub-key.
    Total ≈ sum of all sub-keys (eventual consistency).
    Good enough for global limits — off by at most N× per check.

  Solution 2: Local counter + periodic sync
    Each server tracks its own count locally.
    Every 1 second, report to Redis: INCRBY global_key local_count
    Reset local counter.
    Read global count to check if near limit.
    Accuracy: within 1-second window × num_servers.

  Solution 3: Accept single-shard hot key
    At 50K ops/sec on a dedicated Redis instance: 50% of capacity.
    Redis can handle it. Just monitor and scale vertically if needed.
```

---

## Local + Remote Hybrid Architecture

### The Problem with Pure-Remote

```
At extreme scale (100K+ requests/sec per service):
  100K requests × 0.3ms Redis latency = 30 seconds of cumulative latency/sec
  Plus: 100K connections to Redis per second (connection overhead)

Not the per-request latency (0.3ms is fine).
But the aggregate load on Redis and network becomes significant.
```

### Hybrid Architecture

```
┌─────────────────────────────────────────────────┐
│                  API Server                      │
│                                                  │
│  ┌──────────────────┐   ┌────────────────────┐   │
│  │  Local Token      │   │  Remote Redis       │   │
│  │  Bucket (in-mem)  │   │  Token Bucket       │   │
│  │                   │   │                     │   │
│  │  Capacity: 20     │   │  Capacity: 100      │   │
│  │  Refill: 4/sec    │   │  Refill: 10/sec     │   │
│  │  (local share)    │   │  (global truth)     │   │
│  └────────┬─────────┘   └──────────┬──────────┘   │
│           │                        │               │
│           ▼                        ▼               │
│  ┌──────────────────────────────────────────────┐ │
│  │          Rate Limit Decision Logic            │ │
│  │                                               │ │
│  │  1. Check local bucket FIRST (no network)     │ │
│  │  2. If local allows → check remote Redis      │ │
│  │  3. If both allow → PASS                      │ │
│  │  4. If either rejects → REJECT                │ │
│  │                                               │ │
│  │  Optimization: If local rejects, skip Redis   │ │
│  │  (saves a network call for obviously-rejected │ │
│  │   requests)                                   │ │
│  └──────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

### Splitting Capacity Across Servers

```
Global limit: 100 requests/sec per user
Servers: 5

Approach 1: Equal split
  Each server's local bucket: 100 / 5 = 20 tokens
  Problem: If all traffic hits 1 server, user gets only 20/sec.
  
Approach 2: Local bucket as "fast reject" + Redis as truth
  Local bucket: 100 tokens (same as global)
  Redis bucket: 100 tokens (authoritative)
  
  Flow:
    1. Local check (0.01ms): Is user clearly over limit? → fast reject
    2. Redis check (0.3ms): Authoritative check → allow or reject
    
  Benefit: Abusive users get rejected locally (no Redis call).
           Legitimate users get the full 100/sec regardless of which server.

Approach 3: Token pre-fetching
  Server requests a "batch" of tokens from Redis.
  Example: Server asks Redis for 10 tokens → grants 10 local.
  Serve the next 10 requests from local bucket (0 Redis calls).
  When local tokens run out → fetch another batch from Redis.
  
  Tradeoff: Latency↓ (fewer Redis calls), Accuracy↓ (local tokens may expire unused).
```

### Token Pre-Fetching Implementation

```python
class HybridTokenBucket:
    """Local + Redis hybrid with token pre-fetching."""
    
    def __init__(self, redis_client, max_tokens, refill_rate, batch_size=10):
        self.redis_limiter = DistributedTokenBucket(redis_client, max_tokens, refill_rate)
        self.batch_size = batch_size
        self.local_tokens = {}  # key → remaining local tokens
        self.lock = threading.Lock()
    
    def allow(self, key: str) -> bool:
        with self.lock:
            # Check local batch first
            if key in self.local_tokens and self.local_tokens[key] > 0:
                self.local_tokens[key] -= 1
                return True
            
            # Local batch exhausted → fetch new batch from Redis
            result = self.redis_limiter.allow(key, cost=self.batch_size)
            if result['allowed']:
                self.local_tokens[key] = self.batch_size - 1  # -1 for current request
                return True
            
            return False
```

---

## Multi-Tier & Hierarchical Rate Limiting

### Layered Limits

```
A single user might be subject to multiple limits simultaneously:

  Tier 1 — Global endpoint limit:
    /api/search → 50,000 requests/sec total (all users combined)

  Tier 2 — Per-tenant limit:
    Tenant "acme-corp" → 10,000 requests/sec (all users in tenant)

  Tier 3 — Per-user limit:
    User 42 → 100 requests/sec

  Tier 4 — Per-user per-endpoint limit:
    User 42 on /api/search → 20 requests/sec

  A request must pass ALL tiers:
    Global OK → Tenant OK → User OK → User+Endpoint OK → ALLOW
    Any tier rejects → 429 with appropriate headers
```

### Multi-Key Lua Script (Single Shard)

```lua
-- Check multiple rate limit tiers atomically
-- KEYS[1..N] = rate limit keys (must be on same shard via hash tags)
-- ARGV = max_tokens_1, refill_rate_1, max_tokens_2, refill_rate_2, ..., now, cost

local num_keys = #KEYS
local now  = tonumber(ARGV[num_keys * 2 + 1])
local cost = tonumber(ARGV[num_keys * 2 + 2]) or 1

-- First pass: check all buckets WITHOUT consuming
local allowed = true
local results = {}
for i = 1, num_keys do
    local key = KEYS[i]
    local max_tokens  = tonumber(ARGV[(i-1) * 2 + 1])
    local refill_rate = tonumber(ARGV[(i-1) * 2 + 2])
    
    local data = redis.call('HMGET', key, 'tokens', 'last_refill')
    local tokens      = tonumber(data[1]) or max_tokens
    local last_refill = tonumber(data[2]) or now
    
    local elapsed    = math.max(0, now - last_refill)
    local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate)
    
    results[i] = {key = key, tokens = new_tokens, max_tokens = max_tokens,
                  refill_rate = refill_rate}
    
    if new_tokens < cost then
        allowed = false
    end
end

-- Second pass: consume from ALL buckets only if ALL allow
if allowed then
    for i = 1, num_keys do
        local r = results[i]
        local new_tokens = r.tokens - cost
        redis.call('HMSET', KEYS[i], 'tokens', new_tokens, 'last_refill', now)
        redis.call('EXPIRE', KEYS[i], math.ceil(r.max_tokens / r.refill_rate) + 60)
    end
    return 1  -- ALLOWED
else
    -- Update refill state for all buckets even on rejection
    for i = 1, num_keys do
        local r = results[i]
        redis.call('HMSET', KEYS[i], 'tokens', r.tokens, 'last_refill', now)
        redis.call('EXPIRE', KEYS[i], math.ceil(r.max_tokens / r.refill_rate) + 60)
    end
    return 0  -- REJECTED
end
```

### Why Two-Pass Is Important

```
Without two-pass (naive approach):
  Check Tier 1 → consume 1 token → ALLOW
  Check Tier 2 → consume 1 token → ALLOW  
  Check Tier 3 → REJECT (no tokens)

  Problem: Tier 1 and Tier 2 consumed tokens, but the request was rejected!
  The user "lost" tokens for a request that never went through.

With two-pass:
  Check Tier 1 → has tokens? YES (don't consume yet)
  Check Tier 2 → has tokens? YES (don't consume yet)
  Check Tier 3 → has tokens? NO → REJECT (no tokens consumed from any tier)

  Only consume from ALL tiers if ALL tiers have sufficient tokens.
  This prevents "token leakage" from failed multi-tier checks.
```

---

## Sliding Window + Token Bucket Hybrid

### Why Combine?

```
Token Bucket alone:
  User with 100-token bucket can send 100 requests in 1 millisecond.
  For the next 10 seconds (at 10/sec refill), they wait.
  This burst can overwhelm downstream services.

Sliding Window alone:
  Smooth rate enforcement, no bursts.
  But no "saved up" capacity — user can't do a legitimate burst
  (e.g., opening the app and loading multiple pages).

Hybrid: Token Bucket for burst allowance + Sliding Window as a ceiling
  Token Bucket: max_tokens=50, refill_rate=10/sec
  Sliding Window: max 20 requests per 1-second window

  User can burst up to 20/sec (sliding window cap) and sustain 10/sec.
  Without the sliding window, they could fire 50 in a single millisecond.
```

### Implementation

```python
def check_rate_limit_hybrid(user_id, request):
    key_tb = f"ratelimit:tb:{{user:{user_id}}}"
    key_sw = f"ratelimit:sw:{{user:{user_id}}}"
    
    # Check 1: Sliding window (burst ceiling)
    window_key = f"{key_sw}:{int(time.time())}"
    current_count = redis.incr(window_key)
    if current_count == 1:
        redis.expire(window_key, 2)  # 2-second TTL for cleanup
    
    if current_count > 20:  # Max 20 requests per second
        return REJECT
    
    # Check 2: Token bucket (sustained rate)
    result = token_bucket_lua.execute(key_tb, max_tokens=50, refill_rate=10)
    if not result['allowed']:
        return REJECT
    
    return ALLOW
```

---

## Clock and Time Handling

### The Server Clock Problem

```
Application servers have imperfect clocks:
  Server A: 12:00:00.000 (NTP-synced)
  Server B: 12:00:00.150 (150ms drift)
  Server C: 11:59:59.900 (100ms behind)

If the Lua script uses ARGV[3] (application server's clock):
  Server A sends now=1710500000.000
  Server B sends now=1710500000.150 (thinks 150ms more have passed)
  Server C sends now=1710499999.900 (thinks 100ms LESS have passed)

  Server B refills slightly MORE tokens than it should.
  Server C refills slightly FEWER tokens than it should.
  Over many requests, this introduces inaccuracy.
```

### Solution 1: Use Redis Server Time

```lua
-- Inside Lua script, use Redis server time instead of ARGV[3]
local redis_time = redis.call('TIME')
local now = tonumber(redis_time[1]) + tonumber(redis_time[2]) / 1000000
-- redis_time[1] = seconds, redis_time[2] = microseconds

-- All servers now use the SAME clock (Redis server's clock).
-- No more drift between application servers.
```

### Solution 2: Accept Application Clock (Pragmatic)

```
In practice, most teams use the application server's clock:
  - NTP keeps servers within 1-10ms of each other
  - At refill_rate=10/sec, 10ms drift = 0.1 tokens of error
  - Negligible for rate limiting (not financial transactions)
  
  Benefit: One fewer Redis command inside Lua (TIME call has overhead).
  
When to use Redis TIME:
  - Strict financial/billing rate limits
  - Servers in different data centers (larger clock skew)
  - Compliance requirements for exact enforcement
```

### Handling Negative Elapsed Time

```lua
-- What if a server's clock goes BACKWARD (NTP correction)?
local elapsed = now - last_refill  -- Could be negative!

-- Protection: clamp to zero
local elapsed = math.max(0, now - last_refill)

-- If negative: no tokens refilled (conservative). Next request with a
-- forward-moving clock will catch up on the refill.
```

---

## Failover and Graceful Degradation

### Redis High Availability Setup

```
Production rate limiting Redis topology:

  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
  │ Redis Primary │────→│ Redis Replica│────→│ Redis Replica│
  │ (writes)      │     │ (read-only)  │     │ (read-only)  │
  └──────┬────────┘     └─────────────┘     └─────────────┘
         │
  ┌──────▼────────┐
  │ Redis Sentinel │  ← Automatic failover (promotes replica to primary)
  └───────────────┘

  Or: Redis Cluster (multiple primaries, each owning a shard)
      Built-in failover for each shard.
```

### Failover Scenarios and Responses

```
Scenario 1: Redis primary fails, Sentinel promotes replica
  Behavior: 1-5 second failover window. During this time:
    - Writes fail (INCR, HMSET)
    - Reads might succeed (replica is still up)
  Impact: Rate limit data since last replication is lost.
    - Some users get a "free" burst (bucket resets to full on first access)
    - Acceptable: self-corrects within seconds.

Scenario 2: Full Redis cluster outage
  Behavior: All Redis operations fail.
  Response: Activate fallback strategy.

Scenario 3: Network partition (app servers can't reach Redis)
  Behavior: Same as full outage from the app server's perspective.
  Response: Same fallback strategy.
```

### Three-Tier Fallback Strategy

```python
class ResilientRateLimiter:
    """Rate limiter with graceful degradation."""
    
    def __init__(self, redis_client, config):
        self.redis_limiter = DistributedTokenBucket(redis_client, ...)
        self.local_limiters = {}  # In-memory fallback per key
        self.circuit_breaker = CircuitBreaker(
            failure_threshold=5,     # 5 consecutive failures
            recovery_timeout=30,     # Try Redis again after 30 seconds
        )
    
    def allow(self, key: str) -> bool:
        # Tier 1: Try Redis (distributed, accurate)
        if self.circuit_breaker.is_closed():
            try:
                result = self.redis_limiter.allow(key)
                self.circuit_breaker.record_success()
                return result['allowed']
            except RedisError:
                self.circuit_breaker.record_failure()
        
        # Tier 2: Fall back to local in-memory limiter (imperfect but functional)
        if key not in self.local_limiters:
            # Local limit = global limit / estimated_server_count
            estimated_servers = 10
            self.local_limiters[key] = LocalTokenBucket(
                max_tokens=self.config.max_tokens // estimated_servers,
                refill_rate=self.config.refill_rate / estimated_servers
            )
        
        local_result = self.local_limiters[key].allow()
        if not local_result:
            return False
        
        # Tier 3: If local limiter is uncertain, fail OPEN (allow)
        return True
```

### Circuit Breaker for Redis Calls

```
States:
  CLOSED: Normal operation. All requests go to Redis.
  OPEN:   Redis is down. Skip Redis, use local fallback.
  HALF-OPEN: Try a single Redis request. If it succeeds → CLOSED. If it fails → OPEN.

  ┌────────┐  5 failures  ┌────────┐  30 sec timeout  ┌───────────┐
  │ CLOSED  │ ───────────→ │  OPEN  │ ────────────────→ │ HALF-OPEN │
  └────────┘               └────────┘                   └───────────┘
       ↑                                                      │
       │              success                                 │
       └──────────────────────────────────────────────────────┘
                              failure → back to OPEN
```

---

## Rate Limit Observability

### Key Metrics to Track

```
Metric 1: Rate limit decisions
  rate_limit.allowed{user, endpoint, tier}     = counter
  rate_limit.rejected{user, endpoint, tier}    = counter
  rate_limit.rejection_ratio = rejected / (allowed + rejected)

  Alert: rejection_ratio > 20% for a specific endpoint → investigate

Metric 2: Redis latency
  rate_limit.redis_latency_ms{operation}       = histogram
  p50 = 0.2ms, p99 = 0.8ms, p99.9 = 2ms

  Alert: p99 > 5ms → Redis under load or network issue

Metric 3: Fallback activations
  rate_limit.fallback_activated{reason}        = counter
  rate_limit.circuit_breaker_state{state}      = gauge

  Alert: Any fallback activation → ops team notified

Metric 4: Token bucket state
  rate_limit.tokens_remaining{user, endpoint}  = gauge (sampled)
  rate_limit.burst_utilization = (capacity - remaining) / capacity

  Useful for capacity planning: Are users consistently near zero tokens?
```

### Logging for Debugging

```json
{
  "event": "rate_limit_check",
  "user_id": "user_42",
  "endpoint": "/api/search",
  "result": "rejected",
  "tokens_remaining": 0.0,
  "retry_after_ms": 3200,
  "tier": "per_user",
  "redis_latency_ms": 0.4,
  "fallback_used": false,
  "timestamp": "2024-03-15T12:00:00.123Z"
}
```

### Dashboard Layout

```
┌──────────────────────────────────────────────────────────────────┐
│              RATE LIMITING DASHBOARD                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ Requests/sec    │  │ Rejection Rate   │  │ Redis Latency  │  │
│  │     45,231      │  │      2.3%        │  │  p99: 0.6ms    │  │
│  │  ▃▅▆██▇▆▅▃▂    │  │  ▁▁▂▁▁▃▁▁▁▁     │  │  ▁▁▁▁▁▂▁▁▁▁   │  │
│  └─────────────────┘  └──────────────────┘  └────────────────┘  │
│                                                                  │
│  Top Rate-Limited Users (last hour):                             │
│  ┌────────┬──────────┬──────────┬────────────┐                   │
│  │ User   │ Rejected │ Allowed  │ Ratio      │                   │
│  ├────────┼──────────┼──────────┼────────────┤                   │
│  │ bot_99 │ 45,231   │ 1,000    │ 97.8%      │  ← likely abuse  │
│  │ u_123  │ 312      │ 10,500   │ 2.9%       │  ← heavy user    │
│  │ u_456  │ 45       │ 8,200    │ 0.5%       │  ← normal        │
│  └────────┴──────────┴──────────┴────────────┘                   │
│                                                                  │
│  Circuit Breaker: ● CLOSED (healthy)                             │
│  Fallback Mode:   ● INACTIVE                                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Performance Tuning & Benchmarks

### Redis Lua Script Performance

```
Benchmark: Token Bucket Lua script on Redis 7.0

  Single Redis instance (8-core, 32 GB):
    Throughput: ~150K rate limit checks/sec
    Latency:    p50 = 0.15ms, p99 = 0.5ms, p99.9 = 1.2ms
    CPU:        ~40% at 150K ops/sec

  Redis Cluster (6 shards):
    Throughput: ~800K rate limit checks/sec
    Latency:    Same per-shard (0.15ms p50)

  Comparison to alternatives:
    Redis Lua:        150K ops/sec, 0.15ms p50
    Redis MULTI/EXEC: 120K ops/sec, 0.20ms p50  (pipeline overhead)
    Separate commands: 80K ops/sec, 0.45ms p50   (3 round-trips)
    DynamoDB:         20K ops/sec, 3ms p50        (network + read/write)
```

### Optimization Techniques

```
1. EVALSHA instead of EVAL
   EVAL sends the full Lua script text every time (~500 bytes).
   EVALSHA sends the SHA1 hash of the script (20 bytes).
   → 96% reduction in command payload.
   → Always use EVALSHA with EVAL as fallback.

2. Redis connection pooling
   Don't create a new connection per request.
   Pool size = num_threads × 1.5 (rule of thumb).
   For 50 threads: pool size = 75 connections.

3. Pipeline non-dependent calls
   If checking multiple independent rate limits (different users),
   pipeline the Lua calls:
     pipe = redis.pipeline()
     for user in users:
         pipe.evalsha(sha, keys=[f"ratelimit:{user}"], args=[...])
     results = pipe.execute()
   → One round-trip for all checks.

4. Script caching
   Use SCRIPT LOAD once at startup → get SHA.
   Use EVALSHA for all subsequent calls.
   Handle NOSCRIPT error: reload script, retry.

5. Minimize Lua script complexity
   Every redis.call() inside Lua adds ~0.01ms.
   Our script uses 3 calls (HMGET + HMSET + EXPIRE) → ~0.03ms internal.
   Keep it minimal.
```

---

## Real-World Production Patterns

### Pattern 1: API Gateway Rate Limiting (Stripe-Style)

```
Stripe's rate limiting:
  - 100 requests/sec per API key (live mode)
  - 25 requests/sec per API key (test mode)
  - Token Bucket with burst allowance

Implementation:
  API Gateway (Kong/Envoy) → Redis check → Backend service

  Key: ratelimit:apikey:{api_key}
  Bucket: capacity=100, refill_rate=100/sec
  (Allows burst of 100, sustains 100/sec)

  Headers returned:
    X-RateLimit-Limit: 100
    X-RateLimit-Remaining: 73
    Retry-After: 0.5  (when rejected)
```

### Pattern 2: Discord's Per-Route Rate Limiting

```
Discord uses per-route buckets:
  POST /channels/{id}/messages → 5 messages/5 seconds per channel
  PATCH /guilds/{id}          → 2 requests/10 seconds per guild
  GET /gateway/bot            → 1 request/5 seconds globally

Each route has its own bucket with different parameters.
Bucket ID is returned in headers so clients can track locally.

  Key pattern: ratelimit:{route_hash}:{resource_id}
  Different capacity and refill_rate per route.
```

### Pattern 3: Multi-Tenant SaaS (Tiered Plans)

```
Plan-based rate limiting:

  Free:       10 requests/min   → capacity=10,  refill_rate=0.167/sec
  Starter:    100 requests/min  → capacity=100, refill_rate=1.667/sec
  Pro:        1000 requests/min → capacity=1000, refill_rate=16.67/sec
  Enterprise: 10000 requests/min + dedicated instance

Implementation:
  1. On request, look up user's plan (cached in Redis or local cache).
  2. Select rate limit parameters based on plan.
  3. Run Token Bucket Lua with plan-specific params.

  Key: ratelimit:{tenant}:{user}
  
  Config stored separately:
    plans:free     → {"max_tokens": 10, "refill_rate": 0.167}
    plans:starter  → {"max_tokens": 100, "refill_rate": 1.667}
    plans:pro      → {"max_tokens": 1000, "refill_rate": 16.67}
```

### Pattern 4: Cloudflare-Style IP Rate Limiting

```
Edge rate limiting at the CDN layer:
  - Per IP: 1000 requests/10 seconds
  - Per IP+URL pattern: 100 requests/10 seconds
  - Geolocation-based: Different limits per country

Challenge: Running at CDN edge (200+ PoPs worldwide).
  Solution: Each PoP has its own Redis instance.
  Trade-off: A user hitting multiple PoPs can exceed the global limit.
  Mitigation: Limits set conservatively (PoP limit < global limit / expected_pops).
```

### Pattern 5: Distributed Cron/Job Rate Limiting

```
Rate limiting background job execution:
  - Max 10 email sends/second (email provider limit)
  - Max 5 concurrent report generations (CPU-bound)

  Token Bucket for throughput limiting:
    key: ratelimit:job:email_send
    capacity=10, refill_rate=10/sec

  Semaphore pattern for concurrency limiting:
    key: semaphore:job:report_gen
    Use Redis SETNX with TTL for distributed semaphore.
    Max 5 concurrent holders.
```

---

## Interview Talking Points & Scripts

### Script 1: Core Architecture

> *"For distributed rate limiting, I'd implement Token Bucket in Redis using a Lua script. Each user has a Redis hash with `tokens` and `last_refill` fields. On every request, the Lua script atomically: (1) reads current state, (2) calculates lazy refill based on elapsed time, (3) attempts to consume a token, and (4) writes back the new state. The entire operation is atomic because Redis executes Lua scripts in a single thread — no race conditions."*

### Script 2: Why Not Local Rate Limiting

> *"Local rate limiting doesn't work in a distributed system. With 50 API servers, a user can distribute 5,000 requests — 100 per server — and each server's local limiter only sees 100, well under the limit. But globally, the user sent 5,000 requests. By using a centralized Redis counter, every server shares the same token bucket, giving us an accurate global view."*

### Script 3: Handling Redis Failure

> *"If Redis goes down, I'd use a three-tier fallback: First, try Redis (the source of truth). If Redis is unreachable, circuit breaker trips and we fall back to in-memory rate limiting — each server enforces a proportional share of the global limit (global_limit / estimated_servers). As a last resort, fail open — allow the traffic through and alert the ops team. Rate limiting should protect the service, but rate limiter failure shouldn't take the service down."*

### Script 4: Variable Cost

> *"Not all requests cost the same on the backend. A search query might cost 1 token, but a bulk data export costs 500 tokens. The Lua script accepts a `cost` parameter, so the same Token Bucket handles both. This way, a user with 1,000 tokens can do 1,000 searches or 2 bulk exports — accurately reflecting the server-side cost of each operation."*

### Script 5: Multi-Tier Limits

> *"I'd implement hierarchical rate limiting: a global endpoint limit (50K/sec for `/search`), per-tenant limit (10K/sec for enterprise customers), and per-user limit (100/sec). A request must pass all tiers. Using Redis hash tags, all keys for one user land on the same shard, so a single Lua script can atomically check all tiers. Importantly, I'd use a two-pass approach — first check all tiers without consuming tokens, then consume from all only if every tier has capacity. This prevents 'token leakage' where tokens are consumed from passing tiers even though a later tier rejects."*

### Script 6: Performance at Scale

> *"A single Redis instance can handle ~150K rate limit checks per second with our Lua script. For higher throughput, Redis Cluster with 6 shards gives us ~800K checks/sec. I'd use EVALSHA instead of EVAL to reduce command payload by 96%, connection pooling to avoid per-request connection overhead, and pipeline independent checks to minimize round-trips."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using multiple Redis commands instead of Lua

**Bad**: `GET key → check → SET key` (3 commands, race condition).
**Fix**: Single Lua script → `HMGET + check + HMSET` in one atomic operation.

### ❌ Mistake 2: Forgetting lazy refill

**Bad**: Running a background process to add tokens every second for every user.
**Fix**: Calculate refill on-demand: `elapsed × refill_rate`. No background jobs.

### ❌ Mistake 3: Not handling Redis failure

**Bad**: "If Redis is down, we just reject everything."
**Fix**: Three-tier fallback: Redis → in-memory → fail open + alerting.

### ❌ Mistake 4: Single global rate limit key as a hot key

**Bad**: One Redis key for global limit → single shard hotspot at high traffic.
**Fix**: Split into N sub-keys, each server increments a random sub-key, sum for total.

### ❌ Mistake 5: Not mentioning clock handling

**Bad**: Ignoring that different servers have different clocks.
**Fix**: Use Redis `TIME` in Lua for strict consistency, or accept NTP-synced app clocks for pragmatic cases.

### ❌ Mistake 6: Consuming tokens from passing tiers when a later tier rejects

**Bad**: Check Tier 1 → consume → Check Tier 2 → reject → Tier 1 tokens wasted.
**Fix**: Two-pass Lua: check all tiers first, consume only if all pass.

### ❌ Mistake 7: Not returning rate limit headers

**Bad**: Client gets a 429 with no information about when to retry.
**Fix**: Return `Retry-After`, `X-RateLimit-Remaining`, `X-RateLimit-Limit` on every response.

### ❌ Mistake 8: Identical limits for all request types

**Bad**: Every API call costs 1 token regardless of server cost.
**Fix**: Variable-cost tokens: cheap reads = 1, expensive writes = 10, bulk operations = 500.

---

## Distributed Rate Limiting by System Design Problem

| Problem | Dimension | Token Bucket Config | Special Considerations |
|---|---|---|---|
| **Twitter** | Per user, POST /tweet | capacity=300, refill=300/10800 (300/3hr) | Variable cost: tweet with media = 5 tokens |
| **Stripe API** | Per API key | capacity=100, refill=100/sec | Per-route overrides, test vs live mode |
| **Discord** | Per channel, messages | capacity=5, refill=1/sec | Per-route bucket IDs in response headers |
| **Search Engine** | Per user + global | User: cap=20, refill=2/sec; Global: 50K/sec | Global uses sharded sub-keys |
| **File Upload** | Per user, bytes | capacity=10GB, refill=1GB/hr | Cost = bytes / 1MB (variable cost) |
| **Payment Gateway** | Per merchant | capacity=50, refill=50/sec | Strict — use Redis TIME for clock |
| **Chat (WhatsApp)** | Per user, messages | capacity=30, refill=0.5/sec | Sliding window hybrid for burst ceiling |
| **Notification System** | Per user, daily | capacity=50, refill=50/86400 | Long TTL (25 hours) |
| **Public API (GitHub)** | Per token, hourly | capacity=5000, refill=5000/3600 | Different limits per auth method |
| **CDN/Edge (Cloudflare)** | Per IP, per PoP | capacity=1000, refill=100/sec | Per-PoP Redis, conservative limits |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│          DISTRIBUTED RATE LIMITING — TOKEN BUCKET IN REDIS            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  WHY DISTRIBUTED:                                                    │
│    Local limiters fail with N servers — user bypasses by spreading   │
│    requests. Redis = single global truth.                            │
│                                                                      │
│  TOKEN BUCKET PARAMS:                                                │
│    capacity (max_tokens): Maximum burst size                         │
│    refill_rate: Sustained requests/sec                               │
│    cost: Tokens consumed per request (variable)                      │
│                                                                      │
│  LAZY REFILL:                                                        │
│    No background timers. Calculate on demand:                        │
│    new_tokens = min(capacity, current + elapsed × refill_rate)       │
│                                                                      │
│  REDIS DATA MODEL:                                                   │
│    Hash per key: { tokens: float, last_refill: float }               │
│    TTL = capacity/refill_rate + 60s                                  │
│    ~130 bytes per key                                                │
│                                                                      │
│  ATOMICITY: Lua script (read + refill + consume + write = 1 op)      │
│    EVALSHA for efficiency (20 bytes vs 500 bytes payload)            │
│                                                                      │
│  MULTI-TIER: Two-pass Lua                                            │
│    Pass 1: Check all tiers (don't consume)                           │
│    Pass 2: Consume from all ONLY if all tiers allow                  │
│    Hash tags: {user:42} → co-locate all keys on same shard           │
│                                                                      │
│  HYBRID LOCAL+REMOTE:                                                │
│    Local bucket = fast reject (no network)                           │
│    Redis bucket = authoritative check                                │
│    Token pre-fetching: batch N tokens locally                        │
│                                                                      │
│  FAILOVER STRATEGY:                                                  │
│    Redis → In-memory fallback → Fail open + alert                    │
│    Circuit breaker: 5 failures → open → 30s recovery                 │
│                                                                      │
│  CLOCK HANDLING:                                                     │
│    Strict: redis.call('TIME') inside Lua                             │
│    Pragmatic: NTP-synced app clock (1-10ms drift = negligible)       │
│    Always: math.max(0, elapsed) to handle backward clock jumps       │
│                                                                      │
│  PERFORMANCE:                                                        │
│    Single Redis: ~150K checks/sec, 0.15ms p50                       │
│    6-shard cluster: ~800K checks/sec                                 │
│    EVALSHA + connection pooling + pipelining                         │
│                                                                      │
│  HEADERS (every response):                                           │
│    X-RateLimit-Limit: max requests allowed                           │
│    X-RateLimit-Remaining: tokens left                                │
│    Retry-After: seconds until enough tokens refill                   │
│    Status: 429 Too Many Requests                                     │
│                                                                      │
│  OBSERVABILITY:                                                      │
│    Metrics: allowed/rejected counters, Redis latency, fallback count │
│    Dashboard: rejection ratio, top limited users, circuit breaker    │
│    Alert: rejection ratio > 20%, Redis p99 > 5ms, fallback active   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 15: Rate Limiting** — Algorithm overview (Token/Leaky/Fixed/Sliding Window)
- **Topic 21: Concurrency Control** — Rate limiting as admission control
- **Topic 27: Security** — Rate limiting as DDoS mitigation
- **Topic 30: Backpressure** — Rate limiting is one form of backpressure
- **Topic 31: Hot Partitions** — Global rate limit keys as hot keys in Redis Cluster
- **Topic 40: Redis Deep Dive** — Redis architecture, Lua scripting, Cluster, persistence
- **Topic 40b: Redis Real-World** — Redis patterns across production systems

---

*This document is part of the System Design Interview Deep Dive series.*
