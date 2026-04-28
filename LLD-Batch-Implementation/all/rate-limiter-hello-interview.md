# Rate Limiter - HELLO Interview Framework

> **Companies**: Atlassian, Microsoft, Oracle, Google, Amazon, Uber, Stripe, Cloudflare +13 more  
> **Difficulty**: Medium  
> **Primary Pattern**: Strategy (3 swappable algorithms)  
> **Time**: 35 minutes  
> **Reference**: [HelloInterview LLD Delivery](https://www.hellointerview.com/learn/low-level-design/in-a-hurry/delivery)

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Rate Limiter?

A rate limiter controls how many requests a client can make within a time window. It's the **bouncer at the door** — if you exceed your quota, you're denied entry until the window resets.

**Real-world examples:**
- **Twitter API**: 300 tweets per 3 hours per user
- **GitHub API**: 5,000 requests per hour per token
- **AWS API Gateway**: Configurable per endpoint
- **Stripe**: 100 requests per second per API key

**Why rate limiting matters:**
- **Prevent abuse**: Stop DDoS, brute-force, scraping
- **Fair usage**: Ensure one client doesn't monopolize resources
- **Cost control**: Prevent runaway API costs
- **System stability**: Protect backend from overload

### For a Microsoft L63 Interview

At L63, the interviewer expects you to:
1. **Know multiple algorithms** and their trade-offs (not just one)
2. **Explain the Strategy pattern** for swapping algorithms at runtime
3. **Discuss thread safety** with `synchronized`, `AtomicInteger`, CAS
4. **Identify the Fixed Window boundary problem** unprompted
5. **Discuss distributed rate limiting** (Redis, even if not implementing)

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions to Ask
- "Per-client or global rate limiting?" → **Per-client** (by API key / user ID)
- "What algorithms should we support?" → Multiple: Token Bucket, Fixed Window, Sliding Window
- "Distributed or single-node?" → **Single-node** (mention Redis for distributed)
- "Thread-safe?" → Yes, concurrent requests from same client
- "What response on limit exceeded?" → Return false / HTTP 429

### Functional Requirements

#### Must Have (P0)
1. **Multiple Algorithms** (Strategy Pattern)
   - **Token Bucket**: Smooth rate with controlled bursts
   - **Fixed Window Counter**: Simple, per-time-window count
   - **Sliding Window Log**: Precise, no boundary issues

2. **Per-Client Limiting**
   - Each client (identified by key) has independent limits
   - Auto-create limiter on first request (lazy initialization)

3. **Allow/Deny Decision**
   - `allowRequest(clientId)` → `true` (allowed) or `false` (rate-limited)
   - Thread-safe for concurrent requests from same client

4. **Configurable Rules**
   - Different limits per tier (premium vs free)
   - Different algorithms per client

#### Nice to Have (P1)
- Rate limit headers (X-RateLimit-Remaining, X-RateLimit-Reset)
- Distributed rate limiting (Redis SETNX / Lua scripts)
- Sliding Window Counter (hybrid of Fixed + Sliding)
- Graceful degradation under load

### Non-Functional Requirements
- **Thread Safety**: 10,000+ concurrent requests
- **Latency**: < 1ms per decision
- **Memory**: O(C) where C = unique clients (Token Bucket/Fixed Window), O(C × R) for Sliding Window Log where R = requests
- **Accuracy**: No over-counting or under-counting

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌─────────────────────────────────────────────────────────┐
│                RateLimiterService (Singleton)            │
│  - limiters: ConcurrentHashMap<String, RateLimiter>     │
│  - defaultType: RateLimiterType                         │
│  - defaultConfig: RateLimiterConfig                     │
│  - allowRequest(clientId): boolean                      │
│  - registerClient(id, type, config): void               │
└────────────────────────┬────────────────────────────────┘
                         │ creates via Factory
                         ▼
┌─────────────────────────────────────────────────────────┐
│              RateLimiter (interface)                     │
│  + allowRequest(): boolean                              │
│  + getRemainingRequests(): int                          │
│  + getAlgorithmName(): String                           │
└────────┬───────────────┬────────────────┬───────────────┘
         │               │                │
   ┌─────▼──────┐ ┌─────▼──────────┐ ┌───▼──────────────┐
   │TokenBucket │ │FixedWindow     │ │SlidingWindowLog  │
   │            │ │Counter         │ │                  │
   │- tokens    │ │- counter       │ │- timestamps      │
   │  (double)  │ │  (AtomicInt)   │ │  (Deque<Long>)   │
   │- capacity  │ │- windowStart   │ │- windowSizeMs    │
   │- refillRate│ │- windowSizeMs  │ │- maxRequests     │
   │- lastRefill│ │- maxRequests   │ │                  │
   │            │ │                │ │                  │
   │synchronized│ │ AtomicInteger  │ │ synchronized     │
   │ (refill+   │ │ + double-check │ │                  │
   │  check)    │ │   lock         │ │                  │
   └────────────┘ └────────────────┘ └──────────────────┘
```

### Interface: RateLimiter
```java
interface RateLimiter {
    boolean allowRequest();
    int getRemainingRequests();
    String getAlgorithmName();
}
```

### Token Bucket
| Attribute | Type | Description |
|-----------|------|-------------|
| tokens | double | Current available tokens (starts full) |
| capacity | int | Maximum burst size |
| refillRatePerMs | double | Tokens added per millisecond |
| lastRefillTime | long | Last refill timestamp |

**How it works**: Tokens refill at a steady rate. Each request costs 1 token. If tokens ≥ 1, allow and deduct. Otherwise deny. Allows **controlled bursts** up to capacity.

### Fixed Window Counter
| Attribute | Type | Description |
|-----------|------|-------------|
| counter | AtomicInteger | Requests in current window |
| windowStart | long (volatile) | Start of current window |
| windowSizeMs | long | Window duration |
| maxRequests | int | Max requests per window |

**How it works**: Count requests per time window. Reset when window expires. ⚠️ Has **boundary problem** (2x burst at window edge).

### Sliding Window Log
| Attribute | Type | Description |
|-----------|------|-------------|
| timestamps | Deque<Long> | Request timestamps |
| windowSizeMs | long | Window duration |
| maxRequests | int | Max requests per window |

**How it works**: Keep log of every timestamp. Remove expired. Count remaining. **Most precise** but O(N) memory.

---

## 3️⃣ API Design

```java
interface RateLimiter {
    /** Returns true if request allowed, false if rate-limited */
    boolean allowRequest();
    /** Remaining requests before hitting limit */
    int getRemainingRequests();
    /** Algorithm name for logging */
    String getAlgorithmName();
}

class RateLimiterService {
    /** Check if client's request is allowed */
    boolean allowRequest(String clientId);
    /** Register client with specific algorithm */
    void registerClient(String clientId, RateLimiterType type, RateLimiterConfig config);
    /** Set defaults for auto-created limiters */
    void setDefaults(RateLimiterType type, RateLimiterConfig config);
}

enum RateLimiterType { TOKEN_BUCKET, FIXED_WINDOW, SLIDING_WINDOW_LOG }

class RateLimiterConfig {
    int maxRequests;      // requests per window
    long windowSizeMs;    // window duration
    int burstCapacity;    // token bucket max burst
    double refillRate;    // tokens per second
}
```

---

## 4️⃣ Data Flow

### Token Bucket: allowRequest()

```
Request arrives → TokenBucket.allowRequest()
  │
  ├─ synchronized block (atomic refill + check)
  │
  ├─ Step 1: Refill tokens
  │   ├─ elapsed = now - lastRefillTime
  │   ├─ newTokens = elapsed × refillRatePerMs
  │   ├─ tokens = min(capacity, tokens + newTokens)
  │   └─ lastRefillTime = now
  │
  ├─ Step 2: Check & deduct
  │   ├─ tokens >= 1.0?
  │   │   ├─ YES → tokens -= 1.0 → return true ✅
  │   │   └─ NO  → return false ❌
```

**Why synchronized on both refill AND check?**
Without it, two threads could both see tokens=1, both deduct, and tokens goes to -1.

### Fixed Window: allowRequest()

```
Request arrives → FixedWindowCounter.allowRequest()
  │
  ├─ now = currentTimeMillis()
  │
  ├─ Is window expired? (now - windowStart >= windowSizeMs)
  │   ├─ YES → Double-check lock:
  │   │   ├─ synchronized(this):
  │   │   │   ├─ Re-check: still expired?
  │   │   │   │   ├─ YES → counter.set(0), windowStart = now
  │   │   │   │   └─ NO → another thread already reset
  │   │
  │   └─ NO → continue
  │
  ├─ counter.incrementAndGet() <= maxRequests?
  │   ├─ YES → return true ✅
  │   └─ NO  → return false ❌
```

**Why double-check locking for reset?** 
Without it, 100 threads might all see "expired" and reset the counter 100 times.

### Sliding Window Log: allowRequest()

```
Request arrives → SlidingWindowLog.allowRequest()
  │
  ├─ synchronized block
  │
  ├─ Step 1: Cleanup expired
  │   ├─ cutoff = now - windowSizeMs
  │   └─ while (timestamps.peekFirst() <= cutoff) → pollFirst()
  │
  ├─ Step 2: Check count
  │   ├─ timestamps.size() < maxRequests?
  │   │   ├─ YES → timestamps.addLast(now) → return true ✅
  │   │   └─ NO  → return false ❌
```

---

## 5️⃣ Design + Implementation

### Design Pattern: Strategy (Primary)

**Why Strategy Pattern?**
- **Different use cases need different algorithms**:
  - Token Bucket: API rate limiting (most common, allows bursts)
  - Fixed Window: Simple rate counting (dashboard metrics)
  - Sliding Window: Strict compliance (payment APIs)
- **Swap at runtime**: Change algorithm without code changes
- **Per-client**: Premium users get Token Bucket, free users get Fixed Window

```
RateLimiterService.allowRequest("user-123")
  ├─ Look up limiter for "user-123" in ConcurrentHashMap
  │   ├─ Found → limiter.allowRequest()  // polymorphic call
  │   └─ Not found → create with defaults, store, then call
  │
  └─ The service doesn't know or care which algorithm it is!
      It just calls the interface method. That's Strategy.
```

### Thread Safety Strategy (Per Algorithm)

| Algorithm | Thread Safety Mechanism | Why This Approach |
|-----------|------------------------|-------------------|
| Token Bucket | `synchronized` | Refill + check must be atomic (compound operation) |
| Fixed Window | `AtomicInteger` + double-check lock | Increment is atomic, reset needs lock |
| Sliding Window | `synchronized` | Cleanup + add must be atomic |

### Complete Implementation

```java
// ==================== Token Bucket ====================
// Best for: API rate limiting with controlled bursts
// Time: O(1)  Space: O(1)

class TokenBucketLimiter implements RateLimiter {
    private double tokens;
    private final int capacity;
    private final double refillRatePerMs;
    private long lastRefillTime;

    TokenBucketLimiter(int capacity, double refillRatePerSec) {
        this.capacity = capacity;
        this.tokens = capacity;             // start FULL
        this.refillRatePerMs = refillRatePerSec / 1000.0;
        this.lastRefillTime = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean allowRequest() {
        // Step 1: Refill based on elapsed time
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillRatePerMs);
            lastRefillTime = now;
        }
        // Step 2: Check and deduct
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    @Override
    public synchronized int getRemainingRequests() {
        // Refill before checking (lazy refill)
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillRatePerMs);
            lastRefillTime = now;
        }
        return (int) tokens;
    }

    @Override
    public String getAlgorithmName() { return "TokenBucket"; }
}
```

**Key design insight**: Token Bucket uses **lazy refill** — tokens aren't added by a background timer. Instead, on each request, we calculate how many tokens *should have been added* since the last call. This means **zero background threads**.

```java
// ==================== Fixed Window Counter ====================
// Best for: Simple rate limiting where boundary precision not critical
// Time: O(1)  Space: O(1)
// ⚠️ Boundary problem: 2x burst possible at window edge

class FixedWindowLimiter implements RateLimiter {
    private final int maxRequests;
    private final long windowSizeMs;
    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile long windowStart;

    FixedWindowLimiter(int maxRequests, long windowSizeMs) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeMs;
        this.windowStart = System.currentTimeMillis();
    }

    @Override
    public boolean allowRequest() {
        long now = System.currentTimeMillis();
        // Reset window if expired (double-check locking)
        if (now - windowStart >= windowSizeMs) {
            synchronized (this) {
                if (now - windowStart >= windowSizeMs) {
                    counter.set(0);
                    windowStart = now;
                }
            }
        }
        // Atomically increment and check
        return counter.incrementAndGet() <= maxRequests;
    }

    @Override
    public int getRemainingRequests() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= windowSizeMs) return maxRequests;
        return Math.max(0, maxRequests - counter.get());
    }

    @Override
    public String getAlgorithmName() { return "FixedWindow"; }
}
```

**Why `volatile` on `windowStart`?** Multiple threads read it outside the synchronized block to check if window expired. `volatile` ensures they see the latest value written inside the synchronized block.

**Why `AtomicInteger` for counter?** `incrementAndGet()` is a single atomic CAS operation — no lock needed for the hot path (incrementing). Only the cold path (window reset) needs the synchronized block.

```java
// ==================== Sliding Window Log ====================
// Best for: Precise rate limiting, strict compliance
// Time: O(N) cleanup  Space: O(N) where N = requests in window

class SlidingWindowLogLimiter implements RateLimiter {
    private final int maxRequests;
    private final long windowSizeMs;
    private final Deque<Long> timestamps = new ArrayDeque<>();

    SlidingWindowLogLimiter(int maxRequests, long windowSizeMs) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeMs;
    }

    @Override
    public synchronized boolean allowRequest() {
        long now = System.currentTimeMillis();
        // Remove expired timestamps
        long cutoff = now - windowSizeMs;
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
            timestamps.pollFirst();
        }
        // Check and add
        if (timestamps.size() < maxRequests) {
            timestamps.addLast(now);
            return true;
        }
        return false;
    }

    @Override
    public synchronized int getRemainingRequests() {
        long now = System.currentTimeMillis();
        long cutoff = now - windowSizeMs;
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
            timestamps.pollFirst();
        }
        return Math.max(0, maxRequests - timestamps.size());
    }

    @Override
    public String getAlgorithmName() { return "SlidingWindowLog"; }
}
```

```java
// ==================== Factory ====================

class RateLimiterFactory {
    static RateLimiter create(RateLimiterType type, RateLimiterConfig config) {
        return switch (type) {
            case TOKEN_BUCKET -> new TokenBucketLimiter(config.burstCapacity, config.refillRate);
            case FIXED_WINDOW -> new FixedWindowLimiter(config.maxRequests, config.windowSizeMs);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogLimiter(config.maxRequests, config.windowSizeMs);
        };
    }
}
```

```java
// ==================== Service (Singleton) ====================

class RateLimiterService {
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private RateLimiterType defaultType = RateLimiterType.TOKEN_BUCKET;
    private RateLimiterConfig defaultConfig;

    boolean allowRequest(String clientId) {
        RateLimiter limiter = limiters.computeIfAbsent(clientId,
            k -> RateLimiterFactory.create(defaultType, defaultConfig));
        return limiter.allowRequest();
    }

    void registerClient(String id, RateLimiterType type, RateLimiterConfig config) {
        limiters.put(id, RateLimiterFactory.create(type, config));
    }
}
```

**Why `computeIfAbsent`?** It's atomic — if two threads call simultaneously for a new client, only one creates the limiter. The other gets the same instance. No duplicate limiters.

---

## 6️⃣ Deep Dives

### Deep Dive 1: Algorithm Comparison Table

| | Token Bucket | Fixed Window | Sliding Window Log |
|---|---|---|---|
| **Time per request** | O(1) | O(1) | O(N) cleanup |
| **Space** | O(1) | O(1) | O(N) per client |
| **Burst handling** | ✅ Controlled bursts up to capacity | ⚠️ 2x burst at boundary | ❌ No bursts |
| **Precision** | Good | Approximate | Exact |
| **Thread safety** | `synchronized` | `AtomicInteger` + lock | `synchronized` |
| **Best for** | API rate limiting (most common) | Simple counters | Strict compliance |
| **Used by** | AWS, Stripe, GitHub | Simple dashboards | Payment/financial APIs |

### Deep Dive 2: The Fixed Window Boundary Problem

```
Window 1: [0s ─────── 60s]   Window 2: [60s ─────── 120s]
                     │                  │
           59s: 100 reqs           61s: 100 reqs
                     │                  │
                     └── 200 reqs in 2 seconds! ──┘

Expected: max 100 per 60s
Actual: 200 in a 2-second span across boundary
```

**Solutions:**
1. **Token Bucket** — natural rate smoothing, no boundaries
2. **Sliding Window Counter** (hybrid): Weight current + previous window
   ```
   count = previous_window_count × overlap% + current_window_count
   overlap% = (windowSize - elapsed_in_current) / windowSize
   ```
3. **Sliding Window Log** — tracks exact timestamps, no boundary issues

### Deep Dive 3: Distributed Rate Limiting (L63 Discussion Point)

Single-node is our implementation, but the interviewer will ask: **"What about multiple servers?"**

**Redis-based approach:**
```
MULTI
  INCR rate_limit:user123:window_1714300000
  EXPIRE rate_limit:user123:window_1714300000 60
EXEC
```

**Lua script (atomic):**
```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if count > tonumber(ARGV[2]) then
    return 0  -- denied
end
return 1      -- allowed
```

**Trade-offs of distributed:**
| Approach | Consistency | Latency | Complexity |
|----------|------------|---------|------------|
| Local only | ❌ Per-server only | ~0.01ms | Low |
| Redis centralized | ✅ Global | ~1-5ms | Medium |
| Token Bucket with Redis | ✅ Global | ~2ms | Medium-High |
| Sliding Window + Redis | ✅ Global + precise | ~3ms | High |

### Deep Dive 4: Token Bucket — Why "Lazy Refill"?

**Alternative: Timer-based refill**
```java
// Bad approach: background thread
ScheduledExecutorService.scheduleAtFixedRate(() -> {
    tokens = Math.min(capacity, tokens + refillRate);
}, 0, 1, TimeUnit.SECONDS);
```

**Problems with timer approach:**
- Extra thread per client (100K clients = 100K threads!)
- Timer granularity issues (1-second timer can't do fractional refills)
- Thread synchronization complexity

**Lazy refill is superior:**
```java
// On each request, calculate how many tokens SHOULD exist
long elapsed = now - lastRefillTime;
tokens = min(capacity, tokens + elapsed * refillRatePerMs);
```
- Zero background threads
- Sub-millisecond precision
- Simple code
- Used by all production implementations (AWS, Stripe, etc.)

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| First request (new client) | `computeIfAbsent` creates limiter lazily |
| Exactly at limit (request #100 of 100) | Allowed (≤ not <) |
| Rapid burst then pause | Token Bucket: tokens refill during pause |
| System clock skew | Use monotonic clock (System.nanoTime) in production |
| Integer overflow in counter | AtomicInteger wraps at Integer.MAX_VALUE (unlikely) |
| Very long idle period | Token Bucket caps at capacity (no overflow) |

### Deep Dive 6: HTTP Response Headers (Production)

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100        ← total allowed per window
X-RateLimit-Remaining: 0      ← remaining in current window
X-RateLimit-Reset: 1714300060 ← Unix timestamp when window resets
Retry-After: 30               ← seconds until retry
```

### Deep Dive 7: Complexity Analysis

| Operation | Token Bucket | Fixed Window | Sliding Window Log |
|-----------|-------------|-------------|-------------------|
| allowRequest() | O(1) | O(1) | O(R) cleanup |
| getRemainingRequests() | O(1) | O(1) | O(R) cleanup |
| Space per client | O(1) | O(1) | O(R) |
| Total space (C clients) | O(C) | O(C) | O(C × R) |

Where R = requests in window, C = unique clients

---

## 📋 Interview Checklist (L63 Microsoft)

### What Interviewer Looks For:
- [ ] **Strategy Pattern**: Explained why, showed interface + 3 implementations
- [ ] **Token Bucket**: Lazy refill, capacity as burst, refillRate as sustained
- [ ] **Fixed Window Boundary Problem**: Identified unprompted
- [ ] **Thread Safety**: `synchronized` vs `AtomicInteger` vs CAS — used correctly per algorithm
- [ ] **Per-client isolation**: ConcurrentHashMap + computeIfAbsent
- [ ] **Distributed discussion**: Mentioned Redis, Lua scripts, consistency trade-offs
- [ ] **Complexity**: O(1) for Token Bucket/Fixed Window, O(N) for Sliding Window
- [ ] **Production**: HTTP 429 headers, retry-after, graceful degradation

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities + Strategy | 5 min |
| API Design | 3 min |
| Implementation (Token Bucket first, then others) | 15-20 min |
| Deep Dives (boundary problem, distributed, thread safety) | 5-10 min |
| **Total** | **~35 min** |
