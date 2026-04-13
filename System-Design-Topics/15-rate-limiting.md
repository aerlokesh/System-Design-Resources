# 🎯 Topic 15: Rate Limiting

> **System Design Interview — Deep Dive**
> A comprehensive guide covering rate limiting algorithms (Token Bucket, Leaky Bucket, Fixed Window, Sliding Window), distributed rate limiting with Redis, Lua scripts for atomicity, and graceful handling of rate-limited requests.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Rate Limit](#why-rate-limit)
3. [Rate Limiting Dimensions](#rate-limiting-dimensions)
4. [Token Bucket Algorithm](#token-bucket-algorithm)
5. [Leaky Bucket Algorithm](#leaky-bucket-algorithm)
6. [Fixed Window Counter](#fixed-window-counter)
7. [Sliding Window Log](#sliding-window-log)
8. [Sliding Window Counter](#sliding-window-counter)
9. [Algorithm Comparison](#algorithm-comparison)
10. [Distributed Rate Limiting with Redis](#distributed-rate-limiting-with-redis)
11. [Lua Scripts for Atomicity](#lua-scripts-for-atomicity)
12. [Rate Limit Headers & Client Communication](#rate-limit-headers--client-communication)
13. [Hierarchical Rate Limiting](#hierarchical-rate-limiting)
14. [Rate Limiting Architecture](#rate-limiting-architecture)
15. [Graceful Degradation & Retry-After](#graceful-degradation--retry-after)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Rate Limiting by System Design Problem](#rate-limiting-by-system-design-problem)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Rate limiting** controls how many requests a client can make in a given time window. It protects services from abuse, DDoS attacks, and resource exhaustion.

```
Without rate limiting:
  Malicious client sends 100K requests/sec → service crashes → all users affected

With rate limiting (100 requests/minute):
  Malicious client: First 100 requests → 200 OK
                    Request 101+ → 429 Too Many Requests
  Legitimate users: Unaffected (well within limit)
```

---

## Why Rate Limit

| Reason | Example |
|---|---|
| **Prevent abuse** | Bot scraping all product data |
| **Protect resources** | Database overwhelmed by one heavy client |
| **Fair usage** | Ensure no single tenant monopolizes shared resources |
| **Cost control** | Prevent runaway API costs from misbehaving clients |
| **DDoS mitigation** | Limit request rate per IP |
| **Revenue** | Free tier: 100 req/min; Premium: 10,000 req/min |
| **Compliance** | External API rate limits (Stripe: 100 req/sec) |

---

## Rate Limiting Dimensions

| Dimension | Key | Example |
|---|---|---|
| **Per user** | user_id | Each user: 100 tweets/hour |
| **Per IP** | client_ip | Each IP: 1000 requests/minute |
| **Per API key** | api_key | Each key: 50 calls/second |
| **Per endpoint** | user_id + endpoint | POST /tweet: 10/minute per user |
| **Global** | endpoint | Total /search: 10K/sec across all clients |
| **Per tenant** | tenant_id | Enterprise plan: 100K/hour |

---

## Token Bucket Algorithm

### How It Works
```
Bucket starts with max_tokens tokens.
Each request consumes 1 token.
Tokens are added at a steady rate (refill_rate).
If no tokens available → request rejected (429).

Parameters:
  max_tokens = 10 (bucket capacity)
  refill_rate = 1 token/second

Timeline:
  T=0:  10 tokens. User sends 8 requests → 2 tokens remain.
  T=1:  2 + 1 = 3 tokens (refilled 1).
  T=2:  3 + 1 = 4 tokens.
  T=5:  4 + 3 = 7 tokens.
  T=10: 7 + 5 = 10 tokens (capped at max_tokens).
```

### Visual
```
[●●●●●●●●●●]  10/10 tokens (full)
User sends 7 requests
[●●●○○○○○○○]  3/10 tokens
1 second later (1 token refilled)
[●●●●○○○○○○]  4/10 tokens
```

### Advantages
- **Allows bursts**: A user can use up to `max_tokens` requests instantly.
- **Smooth average rate**: Over time, rate converges to `refill_rate`.
- **Memory efficient**: Only stores 2 values per key (tokens, last_refill_time).
- **Intuitive**: Natural "allowance" metaphor.

### Disadvantages
- **Burst at window boundary**: User can use full bucket, wait for refill, burst again.
- **Clock dependency**: Needs accurate timekeeping for refill calculation.

### Interview Script
> *"Token Bucket is more user-friendly than Leaky Bucket because it allows bursts. A real user opens the app and rapidly scrolls, loading 10 pages in 5 seconds, then idles for a minute. Token Bucket allows this burst (spending 10 tokens instantly) while Leaky Bucket would throttle after the first request. We'd set max_tokens=10 and refill_rate=1/second — the user gets their burst, and if they sustain rapid requests, they hit the limit after 10 seconds."*

---

## Leaky Bucket Algorithm

### How It Works
```
Requests enter a queue (bucket).
Requests are processed at a fixed rate (leak_rate).
If queue is full → new requests are rejected.

Parameters:
  bucket_size = 10 (queue capacity)
  leak_rate = 1 request/second

Behavior:
  Requests come in bursts → queued.
  Processed at steady rate → smoothed output.
  Queue full → 429 Too Many Requests.
```

### Visual
```
Incoming:  ●●●●●●  (6 requests at once)
Queue:     [●●●●●●○○○○]  6/10 slots used
Processing: ●...●...●...  (1 per second, steady)
```

### Advantages
- **Smooth output rate**: Requests processed at exactly `leak_rate`.
- **Prevents bursts**: Output is always steady, never bursty.

### Disadvantages
- **No burst allowance**: Even legitimate bursts are delayed/rejected.
- **Queue management**: Need to manage a FIFO queue.
- **Latency**: Queued requests experience delay.

### Token Bucket vs Leaky Bucket
| Aspect | Token Bucket | Leaky Bucket |
|---|---|---|
| **Bursts** | Allows (up to bucket size) | Prevents (steady output) |
| **Average rate** | Converges to refill_rate | Exactly leak_rate |
| **User experience** | Better (natural browsing) | Worse (throttled feeling) |
| **Implementation** | Counter + timestamp | FIFO queue |
| **Use case** | API rate limiting (DEFAULT) | Traffic shaping, network |

---

## Fixed Window Counter

### How It Works
```
Divide time into fixed windows (e.g., 1-minute windows).
Count requests per window per key.
If count > limit → reject.

Example (limit: 100/minute):
  Window 12:00-12:01: 0 requests
  Request at 12:00:15 → count = 1 → ALLOW
  Request at 12:00:30 → count = 2 → ALLOW
  ...
  Request at 12:00:55 → count = 100 → ALLOW
  Request at 12:00:56 → count = 101 → REJECT (429)
  Window resets at 12:01:00 → count = 0
```

### The Boundary Problem
```
Limit: 100 requests per minute

User sends 100 requests at 12:00:58 (last 2 seconds of window)
User sends 100 requests at 12:01:02 (first 2 seconds of next window)

Both within their respective windows!
But: 200 requests in 4 seconds → 2x the intended rate.

This is the fixed window boundary burst problem.
```

### Advantages
- **Simple**: One counter per window per key.
- **Memory efficient**: Only stores count + window timestamp.
- **Fast**: Single Redis INCR operation.

### Disadvantages
- **Boundary burst**: Up to 2x limit at window boundaries.
- **Unfair**: Users who start at the end of a window get fewer requests.

---

## Sliding Window Log

### How It Works
```
Maintain a sorted list of all request timestamps.
On new request:
  1. Remove timestamps older than window_size
  2. Count remaining timestamps
  3. If count < limit → allow, add current timestamp
  4. If count >= limit → reject

Example (limit: 5 requests per 60 seconds):
  Log: [12:00:10, 12:00:20, 12:00:30, 12:00:45, 12:00:50]
  New request at 12:01:05:
    Remove timestamps before 12:00:05 → [12:00:10, 12:00:20, 12:00:30, 12:00:45, 12:00:50]
    Count: 5 → REJECT
  
  New request at 12:01:15:
    Remove timestamps before 12:00:15 → [12:00:20, 12:00:30, 12:00:45, 12:00:50]
    Count: 4 → ALLOW, add 12:01:15
```

### Advantages
- **Most accurate**: No boundary burst problem.
- **Per-request precision**: Exact sliding window.

### Disadvantages
- **Memory intensive**: Stores every request timestamp (could be millions).
- **Slow cleanup**: Must scan and remove old entries.
- **Not practical at high volume**: 10K requests/sec × 60-second window = 600K entries per key.

---

## Sliding Window Counter

### How It Works (Hybrid)
```
Combines fixed window efficiency with sliding window accuracy.

Weighted average of current and previous window counts:
  rate = prev_window_count × (1 - elapsed_fraction) + current_window_count

Example (limit: 100/minute, window = 1 minute):
  Previous window (12:00-12:01): 80 requests
  Current window (12:01-12:02): 30 requests
  Current time: 12:01:15 (25% into current window)
  
  Weighted rate = 80 × 0.75 + 30 = 60 + 30 = 90 → ALLOW (< 100)
  
  Current time: 12:01:45 (75% into current window):
  Weighted rate = 80 × 0.25 + 30 = 20 + 30 = 50 → ALLOW
```

### Advantages
- **Good accuracy**: Eliminates most of the boundary burst problem.
- **Memory efficient**: Only 2 counters per key (previous + current window).
- **Fast**: Simple arithmetic, no timestamp logs.

### Disadvantages
- **Approximate**: Not perfectly accurate (weighted estimate).
- **Slightly more complex** than fixed window.

---

## Algorithm Comparison

| Algorithm | Accuracy | Memory | Speed | Burst | Best For |
|---|---|---|---|---|---|
| **Token Bucket** | Good | 2 values/key | Fast | Allows | API rate limiting (DEFAULT) |
| **Leaky Bucket** | Good | Queue/key | Medium | Prevents | Traffic shaping |
| **Fixed Window** | Poor (boundary) | 1 counter/key | Fastest | At boundary | Simple use cases |
| **Sliding Window Log** | Perfect | Timestamps/key | Slow | None | Low-volume, high-precision |
| **Sliding Window Counter** | Good | 2 counters/key | Fast | Minimal | Production rate limiting |

### Recommendation
```
Default choice: Token Bucket (best UX, allows natural bursts)
If you need precision: Sliding Window Counter
If you need traffic shaping: Leaky Bucket
If you need simplicity: Fixed Window Counter
```

---

## Distributed Rate Limiting with Redis

### The Problem
With 50 API servers, local rate limiting doesn't work:
```
User makes 100 requests distributed across 50 servers (2 each).
Each server's local limit: 10/minute.
Each server sees: 2 requests → ALLOW.
Total: 100 requests → ALL ALLOWED (should be limited!)
```

### Solution: Centralized Redis Counter
```python
def is_rate_limited(user_id):
    key = f"ratelimit:{user_id}:minute:{current_minute()}"
    count = redis.incr(key)
    if count == 1:
        redis.expire(key, 60)  # Auto-expire after 1 minute
    return count > 100  # Limit: 100 per minute
```

All servers check the same Redis counter → global rate limiting.

### Interview Script
> *"For distributed rate limiting across 50 API servers, every server checks the same Redis instance. If we tracked limits locally per server, a user making 100 requests (distributed across 50 servers, 2 each) would pass every server's local limit of 10/minute while actually sending 100/minute globally. Redis gives us a single global counter that all servers share. The Redis call adds < 1ms to each request, which is negligible."*

---

## Lua Scripts for Atomicity

### The Race Condition Problem
```python
# NON-ATOMIC — Race condition!
tokens = redis.get(key)          # Read: 1 token
if tokens > 0:
    redis.decr(key)              # Decrement: 0 tokens
    return ALLOW
# Between read and decrement, another request also reads 1 token
# Both requests pass → limit exceeded!
```

### The Solution: Redis Lua Script
```lua
-- Token Bucket in a single atomic Lua script
local key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(data[1]) or max_tokens
local last_refill = tonumber(data[2]) or now

-- Calculate refill
local elapsed = now - last_refill
local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate)

-- Try to consume one token
if new_tokens >= 1 then
    redis.call('HMSET', key, 'tokens', new_tokens - 1, 'last_refill', now)
    redis.call('EXPIRE', key, 3600)
    return 1  -- ALLOWED
else
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 3600)
    return 0  -- REJECTED
end
```

### Why Lua?
- **Atomic**: The entire script executes as one operation (no race conditions).
- **Fast**: Runs on the Redis server (no network round-trips between operations).
- **Single command**: `EVAL script 1 key arg1 arg2 arg3`.

### Interview Script
> *"I'd implement Token Bucket in Redis using a Lua script for atomicity. Each user has a Redis hash `ratelimit:{user_id}` with fields `tokens` and `last_refill`. The Lua script calculates elapsed time since last refill, adds proportional tokens (capped at max_tokens), then attempts to consume one token — all in a single atomic operation. Without the Lua script, a race condition exists: two concurrent requests both read tokens=1, both decrement to 0, and both succeed — exceeding the limit."*

---

## Rate Limit Headers & Client Communication

### Standard Headers
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 100        # Max requests allowed in window
X-RateLimit-Remaining: 42     # Requests remaining in current window
X-RateLimit-Reset: 1710500000 # Unix timestamp when window resets
```

### When Limit Exceeded
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 30                # Seconds until client should retry
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1710500060

{
  "error": "rate_limit_exceeded",
  "message": "Rate limit of 100 requests per minute exceeded",
  "retry_after": 30
}
```

### Why Headers Matter
- Clients can self-throttle before hitting the limit.
- SDKs can implement automatic backoff based on `Retry-After`.
- Reduces unnecessary 429 responses.

---

## Hierarchical Rate Limiting

### Multiple Levels
```
Level 1 (Global):   /search endpoint: 10,000 requests/sec total
Level 2 (Per-user): Each user: 10 searches/minute
Level 3 (Per-IP):   Each IP: 100 requests/minute (catch bots)

A request must pass ALL levels:
  Global check → User check → IP check → ALLOW
  If any level rejects → 429
```

### Tiered Limits by Plan
```
Free tier:      100 requests/hour
Basic tier:     1,000 requests/hour
Pro tier:       10,000 requests/hour
Enterprise:     100,000 requests/hour

Stored in user metadata, checked at rate limit time.
```

---

## Rate Limiting Architecture

```
┌──────────┐      ┌─────────────┐      ┌──────────────┐
│  Client   │────→ │  API Gateway │────→ │  Rate Limiter │
│           │      │  (Nginx/ALB) │      │  (Redis)      │
└──────────┘      └──────┬──────┘      └──────┬───────┘
                         │                     │
                    ALLOW │               ┌────┘
                         ▼               ▼ REJECT: 429
                  ┌──────────────┐
                  │  Application  │
                  │  Server       │
                  └──────────────┘
```

### Where to Place Rate Limiting
| Location | Pros | Cons |
|---|---|---|
| **API Gateway** | Centralized, before app servers | Single point |
| **Per-service** | Fine-grained per-endpoint limits | Distributed state needed |
| **Client-side** | Reduces unnecessary requests | Can be bypassed |
| **CDN/Edge** | Blocks abusive traffic early | Limited granularity |

### Best Practice: Multiple Layers
```
Layer 1: CDN/Edge (Cloudflare) — block DDoS, IP rate limit
Layer 2: API Gateway — per-API-key rate limit
Layer 3: Per-service — endpoint-specific limits
```

---

## Graceful Degradation & Retry-After

### What to Do When Redis Is Down
```
Option 1: Fail open (allow all requests)
  → Service stays up, but no rate limiting (risky)

Option 2: Fail closed (reject all requests)
  → No abuse, but service is down for everyone (too aggressive)

Option 3: Local fallback (in-memory approximate rate limiting)
  → Each server does local rate limiting (imperfect but functional)
  → When Redis recovers, switch back to global limiting
```

### Recommendation: Fail open with alerting
> Rate limiting protects the service, but rate limiting failure shouldn't cause the service to fail. Allow traffic through and alert the team to fix Redis.

---

## Interview Talking Points & Scripts

### Token Bucket with Redis Lua
> *"I'd implement Token Bucket in Redis using a Lua script for atomicity. The Lua script calculates refill, checks tokens, and decrements — all in one atomic operation. Without Lua, race conditions allow limit bypass."*

### Distributed Rate Limiting
> *"For distributed rate limiting across 50 API servers, every server checks the same Redis counter. Local rate limiting wouldn't work — a user could distribute 100 requests across 50 servers, 2 each, bypassing every server's local limit."*

### User-Friendly Bursts
> *"Token Bucket is more user-friendly because it allows bursts. A real user opens the app and rapidly scrolls, loading 10 pages. Token Bucket allows this burst. Leaky Bucket would throttle after the first request."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not specifying the algorithm
**Fix**: Name the algorithm (Token Bucket) and explain why.

### ❌ Mistake 2: Local rate limiting in distributed systems
**Fix**: Centralized Redis counter — all servers share the same state.

### ❌ Mistake 3: Not mentioning atomicity/race conditions
**Fix**: Redis Lua scripts prevent TOCTOU (time-of-check-time-of-use) races.

### ❌ Mistake 4: Forgetting to return rate limit headers
**Fix**: `X-RateLimit-Remaining`, `Retry-After` enable client cooperation.

### ❌ Mistake 5: Not handling Redis failure
**Fix**: Fail open with alerting — rate limiter failure shouldn't cause service failure.

---

## Rate Limiting by System Design Problem

| Problem | Dimension | Limit | Algorithm |
|---|---|---|---|
| **Twitter** | Per user, POST /tweet | 300 tweets/3 hours | Token Bucket |
| **Twitter** | Per user, GET /timeline | 900 reads/15 min | Fixed Window |
| **Payment API** | Per API key | 100 charges/sec | Token Bucket |
| **Search** | Per user | 10 searches/min | Sliding Window Counter |
| **Login** | Per IP | 5 attempts/min | Fixed Window |
| **URL Shortener** | Per user | 100 URLs/hour | Token Bucket |
| **File Upload** | Per user | 10 GB/day | Token Bucket (bytes) |
| **Public API** | Per API key, tiered | Free: 100/hr, Pro: 10K/hr | Token Bucket |
| **Chat** | Per user, messages | 30 messages/min | Sliding Window |
| **Notification** | Per user | 50 push/day | Fixed Window (daily) |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                RATE LIMITING CHEAT SHEET                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  DEFAULT: Token Bucket (allows bursts, user-friendly)        │
│                                                              │
│  ALGORITHMS:                                                 │
│  Token Bucket:          Allows bursts, steady average rate   │
│  Leaky Bucket:          Steady output, no bursts             │
│  Fixed Window:          Simple, boundary burst problem       │
│  Sliding Window Log:    Perfect accuracy, memory intensive   │
│  Sliding Window Counter: Good accuracy, memory efficient     │
│                                                              │
│  DISTRIBUTED: Centralized Redis counter (all servers share)  │
│  ATOMICITY: Redis Lua scripts (prevent race conditions)      │
│                                                              │
│  HEADERS:                                                    │
│    X-RateLimit-Limit: max requests allowed                   │
│    X-RateLimit-Remaining: requests left in window            │
│    Retry-After: seconds until client should retry            │
│    Status: 429 Too Many Requests                             │
│                                                              │
│  LAYERS:                                                     │
│    CDN/Edge → API Gateway → Per-service                      │
│                                                              │
│  REDIS FAILURE: Fail open + alert (don't take service down)  │
│                                                              │
│  KEY FORMAT: ratelimit:{user_id}:{endpoint}:{window}         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 12: Sharding** — Rate limit data can be sharded by user_id
- **Topic 21: Concurrency Control** — Rate limiting is a form of admission control
- **Topic 27: Security** — Rate limiting as DDoS mitigation
- **Topic 30: Backpressure** — Rate limiting is one form of backpressure

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
