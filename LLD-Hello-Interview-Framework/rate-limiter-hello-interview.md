# Rate Limiter - HELLO Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **Rate Limit Enforcement**
   - Check if a client can make a request
   - Track request count per client
   - Block requests exceeding limits

2. **Multiple Algorithms Support**
   - Token Bucket (burst support)
   - Leaky Bucket (smooth output)
   - Fixed Window (simple)
   - Sliding Window Log (accurate)
   - Sliding Window Counter (balanced)

3. **Configuration Management**
   - Set limits per endpoint
   - Define time windows (per second/minute/hour)
   - Specify algorithm per endpoint

4. **Client Isolation**
   - Each client has independent limits
   - One client hitting limit doesn't affect others

#### Nice to Have (P1)
- Dynamic configuration updates
- Metrics and monitoring
- Distributed rate limiting (Redis-based)
- Custom error messages
- Whitelist/blacklist clients

### Non-Functional Requirements

#### Performance
- **Latency**: < 10ms per check (P99)
- **Throughput**: 10,000+ requests/second
- **Memory**: O(1) per client for most algorithms

#### Scalability
- Support millions of clients
- Handle traffic spikes
- Bounded memory growth

#### Thread Safety
- Must support concurrent requests
- No race conditions
- Lock-free where possible

#### Reliability
- No false positives (blocking valid requests)
- No false negatives (allowing invalid requests)
- Graceful degradation under load

### Constraints and Assumptions

**Constraints**:
- Single server implementation (no distributed coordination)
- In-memory storage (no database)
- Configuration loaded at startup

**Assumptions**:
- ClientId is provided with each request
- Time synchronization is accurate
- Reasonable number of active clients (< 1M)

**Out of Scope**:
- Distributed consensus (Zookeeper, etcd)
- Persistent storage
- Real-time configuration changes
- Complex analytics

---

## 2️⃣ Core Entities

### Interface: RateLimiter
**Responsibility**: Define contract for all rate limiting strategies

**Key Methods**:
- `allowRequest(String clientId)`: Check if request allowed
- `reset(String clientId)`: Reset limits for testing
- `getConfig()`: Get limiter configuration

**Relationships**:
- Implemented by: All concrete limiters
- Used by: RateLimiterService

```
<<interface>>
RateLimiter
    ▲
    │ implements
    ├─────────────────┬─────────────────┬──────────────────┬────────────────┐
    │                 │                 │                  │                │
TokenBucket    LeakyBucket    FixedWindow    SlidingWindowLog    SlidingWindowCounter
```

### Class: RateLimiterConfig
**Responsibility**: Store configuration parameters

**Key Attributes**:
- `maxRequests: int` - Maximum requests allowed
- `timeWindowMillis: long` - Time window in milliseconds
- `type: RateLimiterType` - Algorithm type enum

**Relationships**:
- Used by: All RateLimiter implementations
- Created by: Builder or constructor

### Class: TokenBucketRateLimiter
**Responsibility**: Implement token bucket algorithm with burst support

**Key Attributes**:
- `config: RateLimiterConfig` - Configuration
- `buckets: ConcurrentHashMap<String, TokenBucket>` - Per-client buckets
- `scheduler: ScheduledExecutorService` - Token refiller

**Key Methods**:
- `allowRequest()`: Try to consume token
- `startTokenRefiller()`: Background refill task

**Relationships**:
- Implements: RateLimiter
- Contains: TokenBucket (inner class)
- Uses: ScheduledExecutorService

**Algorithm**: Tokens added at fixed rate, request consumes one token

### Class: FixedWindowRateLimiter
**Responsibility**: Simple counter-based rate limiting with fixed windows

**Key Attributes**:
- `config: RateLimiterConfig`
- `windows: ConcurrentHashMap<String, WindowCounter>`

**Key Methods**:
- `allowRequest()`: Increment counter if below limit
- `getCurrentWindow()`: Calculate current window

**Relationships**:
- Implements: RateLimiter
- Contains: WindowCounter (inner class)

**Algorithm**: Counter resets at window boundaries

### Class: SlidingWindowLogRateLimiter
**Responsibility**: Most accurate rate limiting using request timestamps

**Key Attributes**:
- `config: RateLimiterConfig`
- `requestLogs: ConcurrentHashMap<String, Queue<Long>>`

**Key Methods**:
- `allowRequest()`: Add timestamp if within limits
- `removeExpiredTimestamps()`: Clean old timestamps

**Relationships**:
- Implements: RateLimiter
- Uses: Queue<Long> for timestamp storage

**Algorithm**: Stores all timestamps, removes expired ones

### Class: SlidingWindowCounterRateLimiter
**Responsibility**: Balanced approach using weighted counts

**Key Attributes**:
- `config: RateLimiterConfig`
- `counters: ConcurrentHashMap<String, SlidingCounter>`

**Key Methods**:
- `allowRequest()`: Calculate weighted count
- `updateWindows()`: Shift windows when needed

**Relationships**:
- Implements: RateLimiter
- Contains: SlidingCounter (inner class)

**Algorithm**: Weighted average of current and previous windows

### Class: RateLimiterFactory
**Responsibility**: Create appropriate limiter based on configuration

**Key Methods**:
- `createRateLimiter(RateLimiterConfig)`: Factory method

**Relationships**:
- Creates: All RateLimiter implementations
- Uses: Factory pattern

### Class: RateLimiterService
**Responsibility**: High-level API managing multiple rate limiters

**Key Attributes**:
- `rateLimiters: Map<String, RateLimiter>` - Endpoint to limiter mapping
- `defaultConfig: RateLimiterConfig` - Default configuration

**Key Methods**:
- `registerRateLimiter()`: Register custom limiter
- `checkRateLimit()`: Check if request allowed
- `resetRateLimit()`: Reset for testing

**Relationships**:
- Contains: Multiple RateLimiter instances
- Uses: RateLimiterFactory

### Decorator: RateLimiterDecorator (Base)
**Responsibility**: Base for adding features to limiters

**Key Methods**:
- Wraps: RateLimiter methods
- Delegates: To wrapped limiter

**Relationships**:
- Implements: RateLimiter
- Decorates: RateLimiter instances

### Decorator: LoggingRateLimiter
**Responsibility**: Add logging to rate limiter

**Relationships**:
- Extends: RateLimiterDecorator
- Logs: All requests and results

### Decorator: MetricsRateLimiter
**Responsibility**: Track statistics (allowed/blocked counts)

**Key Attributes**:
- `totalRequests: AtomicLong`
- `allowedRequests: AtomicLong`
- `blockedRequests: AtomicLong`

**Relationships**:
- Extends: RateLimiterDecorator
- Provides: Metrics API

---

## 3️⃣ API Design

### Interface: RateLimiter

```java
/**
 * Core interface for all rate limiting strategies
 */
public interface RateLimiter {
    /**
     * Check if a request should be allowed for the given client
     * 
     * @param clientId Unique identifier for the client
     * @return true if request is allowed, false if rate limit exceeded
     * @throws IllegalArgumentException if clientId is null or empty
     */
    boolean allowRequest(String clientId);
    
    /**
     * Reset rate limit state for a specific client
     * Useful for testing and admin operations
     * 
     * @param clientId Unique identifier for the client
     */
    void reset(String clientId);
    
    /**
     * Get the configuration for this rate limiter
     * 
     * @return RateLimiterConfig object
     */
    RateLimiterConfig getConfig();
}
```

### Class: RateLimiterConfig

```java
/**
 * Immutable configuration for rate limiter behavior
 */
public class RateLimiterConfig {
    /**
     * Create a new rate limiter configuration
     * 
     * @param maxRequests Maximum number of requests allowed in time window
     * @param timeWindowMillis Time window in milliseconds
     * @param type Algorithm type to use
     * @throws IllegalArgumentException if maxRequests <= 0 or timeWindowMillis <= 0
     */
    public RateLimiterConfig(int maxRequests, long timeWindowMillis, 
                             RateLimiterType type) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (timeWindowMillis <= 0) {
            throw new IllegalArgumentException("timeWindowMillis must be positive");
        }
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.type = type;
    }
    
    public int getMaxRequests() { return maxRequests; }
    public long getTimeWindowMillis() { return timeWindowMillis; }
    public RateLimiterType getType() { return type; }
}
```

### Enum: RateLimiterType

```java
/**
 * Available rate limiting algorithms
 */
public enum RateLimiterType {
    /** Token bucket - allows bursts, good for APIs */
    TOKEN_BUCKET,
    
    /** Leaky bucket - smooth output, good for traffic shaping */
    LEAKY_BUCKET,
    
    /** Fixed window - simple but has boundary issues */
    FIXED_WINDOW,
    
    /** Sliding window log - most accurate but memory intensive */
    SLIDING_WINDOW_LOG,
    
    /** Sliding window counter - balanced accuracy and efficiency */
    SLIDING_WINDOW_COUNTER
}
```

### Class: RateLimiterFactory

```java
/**
 * Factory for creating rate limiter instances
 */
public class RateLimiterFactory {
    /**
     * Create a rate limiter based on configuration
     * 
     * @param config Rate limiter configuration
     * @return Appropriate RateLimiter implementation
     * @throws IllegalArgumentException if type is unknown
     */
    public static RateLimiter createRateLimiter(RateLimiterConfig config) {
        switch (config.getType()) {
            case TOKEN_BUCKET:
                return new TokenBucketRateLimiter(config);
            case LEAKY_BUCKET:
                return new LeakyBucketRateLimiter(config);
            case FIXED_WINDOW:
                return new FixedWindowRateLimiter(config);
            case SLIDING_WINDOW_LOG:
                return new SlidingWindowLogRateLimiter(config);
            case SLIDING_WINDOW_COUNTER:
                return new SlidingWindowCounterRateLimiter(config);
            default:
                throw new IllegalArgumentException("Unknown type: " + config.getType());
        }
    }
}
```

### Class: RateLimiterService

```java
/**
 * High-level service for managing rate limiters across endpoints
 */
public class RateLimiterService {
    /**
     * Create service with default configuration
     * 
     * @param defaultConfig Configuration to use for unregistered endpoints
     */
    public RateLimiterService(RateLimiterConfig defaultConfig) {
        this.rateLimiters = new ConcurrentHashMap<>();
        this.defaultConfig = defaultConfig;
    }
    
    /**
     * Register a custom rate limiter for specific endpoint
     * 
     * @param endpoint Endpoint path (e.g., "/api/login")
     * @param rateLimiter Rate limiter instance to use
     */
    public void registerRateLimiter(String endpoint, RateLimiter rateLimiter) {
        rateLimiters.put(endpoint, rateLimiter);
    }
    
    /**
     * Check if request is allowed for given endpoint and client
     * 
     * @param endpoint Endpoint being accessed
     * @param clientId Client making the request
     * @return true if allowed, false if rate limited
     */
    public boolean checkRateLimit(String endpoint, String clientId) {
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(
            endpoint,
            k -> RateLimiterFactory.createRateLimiter(defaultConfig)
        );
        return rateLimiter.allowRequest(clientId);
    }
    
    /**
     * Reset rate limit for specific endpoint and client
     * 
     * @param endpoint Endpoint to reset
     * @param clientId Client to reset
     */
    public void resetRateLimit(String endpoint, String clientId) {
        RateLimiter rateLimiter = rateLimiters.get(endpoint);
        if (rateLimiter != null) {
            rateLimiter.reset(clientId);
        }
    }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Allow Request (Token Bucket - Success)

**Sequence**:
1. Client makes request to `/api/data` with clientId `user123`
2. RateLimiterService looks up rate limiter for `/api/data`
3. If not found, creates default TokenBucket limiter
4. Service calls `allowRequest("user123")` on limiter
5. TokenBucket gets or creates bucket for `user123`
6. TokenBucket tries to consume one token using CAS operation
7. If tokens available (> 0), decrement and return `true`
8. Background thread refills tokens at configured rate
9. Request proceeds to application logic

**Sequence Diagram**:
```
Client → Service: checkRateLimit("/api/data", "user123")
Service → Factory: createRateLimiter() [if needed]
Factory → Service: TokenBucketRateLimiter
Service → TokenBucket: allowRequest("user123")
TokenBucket → TokenBucket: getBucket("user123")
TokenBucket → TokenBucket: tryConsume() [CAS]
TokenBucket → Service: true
Service → Client: true (allowed)
```

**Time**: ~1-5ms

### Flow 2: Block Request (Fixed Window - Exceeded)

**Sequence**:
1. Client makes 11th request in same window with clientId `user456`
2. RateLimiterService calls `allowRequest("user456")`
3. FixedWindow calculates current window: `currentTime / windowSize`
4. Checks if window changed since last request
5. If same window, increments counter atomically
6. Counter check: `count >= maxRequests` (10 >= 10)
7. Returns `false` without incrementing
8. Service returns `false` to client
9. Client receives HTTP 429 Too Many Requests

**Sequence Diagram**:
```
Client → Service: checkRateLimit("/api/login", "user456")
Service → FixedWindow: allowRequest("user456")
FixedWindow → FixedWindow: calculateWindow()
FixedWindow → FixedWindow: checkCounter()
FixedWindow → Service: false (exceeded)
Service → Client: false (blocked)
Client ← Server: HTTP 429
```

**Time**: ~1-3ms

### Flow 3: Window Transition (Sliding Window Counter)

**Sequence**:
1. Client makes request at time 1300ms (window size: 1000ms)
2. Current window: 1 (1000-2000ms range)
3. Previous window: 0 (0-1000ms range) had 8 requests
4. Current window has 2 requests so far
5. Calculate progress: `300ms / 1000ms = 0.3` (30% into window)
6. Calculate previous weight: `1 - 0.3 = 0.7` (70% relevance)
7. Estimate count: `8 * 0.7 + 2 = 5.6 + 2 = 7.6`
8. Check: `7.6 < 10` (limit), so allow request
9. Increment current window counter: `2 → 3`
10. Return `true`

**Mathematical Flow**:
```
Time: 1300ms
Window size: 1000ms
Previous window (0-1000ms): 8 requests
Current window (1000-2000ms): 2 requests

Step 1: Current window = 1300 / 1000 = 1
Step 2: Progress = 1300 % 1000 = 300ms
Step 3: Previous weight = 1 - (300/1000) = 0.7
Step 4: Estimated = 8 * 0.7 + 2 = 7.6
Step 5: Check: 7.6 < 10 ✓
Step 6: Increment current counter: 3
Step 7: Allow request
```

### Flow 4: Client Isolation

**Scenario**: Two clients accessing same endpoint

**Sequence**:
1. Client A (`userA`) makes 10 requests rapidly
2. All 10 requests succeed, A's counter reaches limit
3. Client A's 11th request blocked
4. Client B (`userB`) makes first request
5. Service gets separate bucket/counter for `userB`
6. Client B's request succeeds (independent state)
7. Client A still blocked, B continues normally

**Key Point**: Each clientId has completely isolated state

---

## 5️⃣ Design

### Class Diagram

```
┌────────────────────────────────┐
│   RateLimiterService           │
│  + checkRateLimit()            │
│  + registerRateLimiter()       │
│  + resetRateLimit()            │
└─────────┬──────────────────────┘
          │ contains
          │ Map<String, RateLimiter>
          ▼
┌────────────────────────────────┐
│   <<interface>>                │
│   RateLimiter                  │
│  + allowRequest(clientId)      │
│  + reset(clientId)             │
│  + getConfig()                 │
└────────▲───────────────────────┘
         │
         │ implements
         │
    ┌────┴────┬──────────┬──────────┬────────────┐
    │         │          │          │            │
┌───▼────┐ ┌──▼──┐  ┌───▼────┐ ┌───▼──────┐ ┌──▼────────┐
│ Token  │ │Leaky│  │ Fixed  │ │ Sliding  │ │  Sliding  │
│ Bucket │ │Bucket  │ Window │ │  Window  │ │  Window   │
│        │ │     │  │        │ │   Log    │ │  Counter  │
└────────┘ └─────┘  └────────┘ └──────────┘ └───────────┘

┌────────────────────────────────┐
│   RateLimiterFactory           │
│  + createRateLimiter()         │  (Factory Pattern)
└────────────────────────────────┘

┌────────────────────────────────┐
│   RateLimiterConfig            │
│  - maxRequests: int            │
│  - timeWindowMillis: long      │
│  - type: RateLimiterType       │
└────────────────────────────────┘

┌────────────────────────────────┐
│   RateLimiterDecorator         │  (Decorator Pattern)
│  - rateLimiter: RateLimiter    │
└────────▲───────────────────────┘
         │
         │ extends
         │
    ┌────┴──────┬──────────┐
    │           │          │
┌───▼──────┐ ┌──▼───────┐ ┌▼────────┐
│ Logging  │ │ Metrics  │ │ Other   │
│ Limiter  │ │ Limiter  │ │ Decorators
└──────────┘ └──────────┘ └─────────┘
```

### Design Patterns

**Pattern 1: Strategy Pattern**
- **Why**: Different rate limiting algorithms have different trade-offs
- **Where**: RateLimiter interface with multiple implementations
- **How**: Each algorithm encapsulated in its own class
- **Benefit**: Easy to add new algorithms, runtime selection

**Pattern 2: Factory Pattern**
- **Why**: Create appropriate limiter based on configuration
- **Where**: RateLimiterFactory.createRateLimiter()
- **How**: Switch on RateLimiterType enum
- **Benefit**: Centralized creation logic, easy to extend

**Pattern 3: Decorator Pattern**
- **Why**: Add features (logging, metrics) without modifying core
- **Where**: LoggingRateLimiter, MetricsRateLimiter
- **How**: Wrap RateLimiter and delegate with added behavior
- **Benefit**: Flexible feature composition, Open/Closed Principle

### Data Structures

**1. ConcurrentHashMap<String, State>**
- **Purpose**: Store per-client state (buckets, counters, logs)
- **Why**: Thread-safe, lock-free reads, good performance
- **Alternative**: Synchronized HashMap (worse performance)

**2. AtomicInteger for Counters**
- **Purpose**: Thread-safe counter operations
- **Why**: Lock-free CAS operations, high performance
- **Alternative**: synchronized blocks (more overhead)

**3. Queue<Long> for Timestamps**
- **Purpose**: Store request timestamps in Sliding Window Log
- **Why**: FIFO access, easy to remove expired entries
- **Alternative**: TreeSet (overkill for this use case)

**4. ScheduledExecutorService**
- **Purpose**: Background token refilling in Token Bucket
- **Why**: Built-in Java support, reliable scheduling
- **Alternative**: Manual thread management (more complex)

### Thread Safety

**Approach 1: Lock-Free CAS (Token Bucket)**
```java
public boolean tryConsume() {
    while (true) {
        int current = tokens.get();
        if (current <= 0) return false;
        // Atomic compare-and-set
        if (tokens.compareAndSet(current, current - 1)) {
            return true;
        }
        // Retry if CAS failed due to contention
    }
}
```
- **Pros**: No locks, high performance, no deadlocks
- **Cons**: Can retry on high contention

**Approach 2: Synchronized Methods (Sliding Window Log)**
```java
public synchronized boolean allowRequest(String clientId) {
    // Remove expired timestamps
    // Check count
    // Add new timestamp
    return count < maxRequests;
}
```
- **Pros**: Simple, easy to reason about
- **Cons**: Only one thread at a time per client

**Approach 3: Striped Locks (Advanced)**
```java
private final Lock[] locks = new Lock[16];

public boolean allowRequest(String clientId) {
    int stripe = Math.abs(clientId.hashCode()) % 16;
    locks[stripe].lock();
    try {
        // Process request
    } finally {
        locks[stripe].unlock();
    }
}
```
- **Pros**: Reduces contention, better scalability
- **Cons**: More complex, more memory

### Complete Implementation

See [RateLimiterSystem.java](../LLD/RateLimiterSystem.java) for full implementation with:
- All 5 algorithms
- Factory pattern
- Decorator pattern
- Thread safety
- Comprehensive tests

---

## 6️⃣ Deep Dives

### Topic 1: Algorithm Trade-offs

**Comparison Matrix**:

| Algorithm | Accuracy | Memory | Complexity | Burst | Use Case |
|-----------|----------|--------|------------|-------|----------|
| Token Bucket | Good | O(1) | Medium | ✅ Yes | General API limiting |
| Leaky Bucket | Good | O(n) | Medium | ❌ No | Traffic shaping |
| Fixed Window | Low | O(1) | Low | ⚠️ Boundary | High throughput |
| Sliding Window Log | High | O(n) | High | ❌ No | Financial APIs |
| Sliding Window Counter | Good | O(1) | Medium | ⚠️ Limited | Production default |

**When to Use What**:

1. **Token Bucket** - Default choice for APIs
   - Allows controlled bursts
   - Good balance of accuracy and performance
   - Example: REST API rate limiting

2. **Sliding Window Counter** - Production systems
   - Better accuracy than Fixed Window
   - O(1) memory like Fixed Window
   - Example: High-scale web services

3. **Sliding Window Log** - High-value transactions
   - Most accurate
   - Worth the memory cost
   - Example: Payment APIs, trading platforms

4. **Fixed Window** - Simple high-throughput
   - Easiest to implement
   - Acceptable for non-critical APIs
   - Example: Internal service calls

5. **Leaky Bucket** - Traffic shaping
   - Smooth output rate
   - Good for downstream protection
   - Example: Message queue producers

### Topic 2: Extensibility

**Adding New Algorithm**:

```java
// Step 1: Create new implementation
class AdaptiveRateLimiter implements RateLimiter {
    @Override
    public boolean allowRequest(String clientId) {
        // Adjust limits based on system load
        double systemLoad = getSystemLoad();
        int dynamicLimit = (int) (config.getMaxRequests() * (1.0 - systemLoad));
        return checkLimit(clientId, dynamicLimit);
    }
}

// Step 2: Add to enum
enum RateLimiterType {
    TOKEN_BUCKET,
    SLIDING_WINDOW_COUNTER,
    ADAPTIVE  // New type
}

// Step 3: Update factory
class RateLimiterFactory {
    public static RateLimiter createRateLimiter(RateLimiterConfig config) {
        switch (config.getType()) {
            case ADAPTIVE:
                return new AdaptiveRateLimiter(config);
            // ... other cases
        }
    }
}
```

**No changes needed in**:
- RateLimiter interface
- RateLimiterService
- Client code
- Existing algorithms

**This demonstrates Open/Closed Principle**: Open for extension, closed for modification.

### Topic 3: Edge Cases

**Edge Case 1: Boundary Problem (Fixed Window)**
```
Window 1 (0-1000ms)     Window 2 (1000-2000ms)
Limit: 5 requests       Limit: 5 requests

Requests at 900ms: 5 ✓  (all allowed)
Requests at 1100ms: 5 ✓ (all allowed)

Result: 10 requests in 200ms! (2x burst)
```

**Solution**: Use Sliding Window Counter instead

**Edge Case 2: Clock Skew**
```java
// Problem: System clock adjusted backwards
long now = System.currentTimeMillis();  // 1000ms
// Clock adjusted backwards
now = System.currentTimeMillis();  // 500ms

// Solution: Use monotonic clock
long now = System.nanoTime() / 1_000_000;  // Monotonic
```

**Edge Case 3: Integer Overflow**
```java
// Problem: Counter overflow after long running
AtomicInteger counter = new AtomicInteger(Integer.MAX_VALUE);
counter.incrementAndGet();  // Wraps to negative!

// Solution: Use AtomicLong or periodic reset
AtomicLong counter = new AtomicLong();
```

**Edge Case 4: Null/Empty ClientId**
```java
public boolean allowRequest(String clientId) {
    if (clientId == null || clientId.trim().isEmpty()) {
        throw new IllegalArgumentException("clientId cannot be null or empty");
    }
    // ... rest of logic
}
```

**Edge Case 5: Memory Leak (Inactive Clients)**
```java
// Problem: Clients never removed, unbounded growth
ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

// Solution: Periodic cleanup
ScheduledExecutorService cleanup = Executors.newScheduledThreadPool(1);
cleanup.scheduleAtFixedRate(() -> {
    long now = System.currentTimeMillis();
    states.entrySet().removeIf(entry -> 
        now - entry.getValue().lastAccessTime > INACTIVE_THRESHOLD
    );
}, 5, 5, TimeUnit.MINUTES);
```

### Topic 4: Performance Optimizations

**Optimization 1: Reduce CAS Contention**
```java
// Before: Single AtomicInteger
AtomicInteger tokens = new AtomicInteger(capacity);

// After: LongAdder for less contention
LongAdder tokens = new LongAdder();
tokens.add(capacity);

// Trade-off: Slightly less accurate, much better performance
```

**Optimization 2: Lazy Initialization**
```java
// Don't create state until first request
buckets.computeIfAbsent(clientId, k -> new TokenBucket(capacity));

// vs. Pre-creating for all possible clients (wasteful)
```

**Optimization 3: Batch Refills**
```java
// Instead of refilling every client every millisecond
scheduler.scheduleAtFixedRate(() -> {
    buckets.values().forEach(TokenBucket::refill);
}, refillInterval, refillInterval, TimeUnit.MILLISECONDS);

// Batch refills reduce scheduler overhead
```

**Optimization 4: Fast Path for Hot Clients**
```java
// Cache recently accessed clients
private final Cache<String, TokenBucket> hotClients = 
    Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build();

public boolean allowRequest(String clientId) {
    TokenBucket bucket = hotClients.get(clientId, 
        k -> buckets.computeIfAbsent(k, v -> new TokenBucket()));
    return bucket.tryConsume();
}
```

### Topic 5: Distributed Rate Limiting

**Challenge**: Rate limiting across multiple servers

**Solution 1: Redis-Based (Recommended)**
```java
class RedisRateLimiter implements RateLimiter {
    private final RedisClient redis;
    
    public boolean allowRequest(String clientId) {
        String key = "rate_limit:" + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - config.getTimeWindowMillis();
        
        // Atomic Lua script
        String luaScript = 
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])\n" +
            "local count = redis.call('ZCARD', KEYS[1])\n" +
            "if count < tonumber(ARGV[2]) then\n" +
            "  redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])\n" +
            "  redis.call('EXPIRE', KEYS[
