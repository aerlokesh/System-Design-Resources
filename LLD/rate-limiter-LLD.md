# Rate Limiter - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [Class Design](#class-design)
5. [Algorithm Implementations](#algorithm-implementations)
6. [Complete Code Implementation](#complete-code-implementation)
7. [Extensibility](#extensibility)
8. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Rate Limiter?**

A rate limiter controls how many requests a client can make to an API within a specific time window. When a request comes in, the rate limiter checks if the client has exceeded their quota. If they're under the limit, the request proceeds. If they've hit the cap, the request gets rejected. This protects APIs from abuse and ensures fair resource allocation across clients.

**Real-World Use Cases:**
- API Gateway rate limiting (AWS API Gateway, Kong)
- DDoS protection
- Cost control for paid APIs
- Fair resource allocation in multi-tenant systems
- Preventing brute force attacks

---

## Requirements

### Functional Requirements

1. **Configuration Management**
   - Configuration is provided at startup (loaded once)
   - Each endpoint can have its own rate limiting configuration
   - Support for algorithm-specific parameters

2. **Request Processing**
   - System receives requests with `(clientId: string, endpoint: string)`
   - Each endpoint specifies:
     - Algorithm to use (e.g., "TokenBucket", "SlidingWindowLog")
     - Algorithm-specific parameters (e.g., capacity, refillRatePerSecond)

3. **Rate Limit Enforcement**
   - System enforces rate limits by checking clientId against endpoint configuration
   - Return structured result: `(allowed: boolean, remaining: int, retryAfterMs: long | null)`

4. **Default Configuration**
   - If endpoint has no configuration, use a default limit

5. **Client Isolation**
   - Each client (identified by clientId) has independent rate limits
   - One client hitting their limit doesn't affect others

### Non-Functional Requirements

1. **Performance**
   - Low latency (<10ms per check)
   - High throughput (10,000+ requests/second)

2. **Memory Efficiency**
   - Bounded memory growth
   - Efficient storage for client state

3. **Thread Safety**
   - Support concurrent requests from multiple threads

4. **Accuracy**
   - Minimize false positives (blocking valid requests)
   - Prevent gaming the system at window boundaries

### Out of Scope

- Distributed rate limiting (Redis, coordination across servers)
- Dynamic configuration updates
- Metrics and monitoring dashboards
- Complex config validation

---

## Core Entities and Relationships

### Key Entities

1. **RateLimiter (Interface)**
   - Core abstraction for all rate limiting strategies
   - Methods: `allowRequest()`, `reset()`, `getConfig()`

2. **RateLimiterConfig**
   - Configuration for rate limiting behavior
   - Contains: maxRequests, timeWindow, algorithm type

3. **Limiter Implementations**
   - TokenBucketLimiter
   - LeakyBucketLimiter
   - FixedWindowLimiter
   - SlidingWindowLogLimiter
   - SlidingWindowCounterLimiter

4. **LimiterFactory**
   - Creates appropriate limiter based on configuration
   - Factory pattern for extensibility

5. **RateLimitResult**
   - Encapsulates the result of a rate limit check
   - Contains: allowed status, remaining quota, retry time

6. **RateLimiterService**
   - High-level API for managing multiple rate limiters
   - Maps endpoints to their respective limiters

### Entity Relationships

```
┌─────────────────────────┐
│   RateLimiterService    │
│  - manages limiters     │
│  - routes requests      │
└───────────┬─────────────┘
            │ contains
            ▼
┌─────────────────────────┐
│   LimiterFactory        │
│  - creates limiters     │
└───────────┬─────────────┘
            │ creates
            ▼
┌─────────────────────────┐
│   RateLimiter           │◄────────┐
│   <<interface>>         │         │ implements
└─────────────────────────┘         │
            ▲                        │
            │ implements             │
            │                        │
    ┌───────┴────────┬──────────┬───┴────────┬──────────────┐
    │                │          │            │              │
┌───▼────┐  ┌────────▼───┐  ┌──▼──────┐  ┌──▼────────┐  ┌─▼────────┐
│ Token  │  │   Leaky    │  │  Fixed  │  │  Sliding  │  │ Sliding  │
│ Bucket │  │   Bucket   │  │  Window │  │  Window   │  │  Window  │
│        │  │            │  │         │  │    Log    │  │  Counter │
└────────┘  └────────────┘  └─────────┘  └───────────┘  └──────────┘
```

---

## Class Design

### 1. RateLimiter Interface

```java
/**
 * Core interface for all rate limiting strategies
 */
interface RateLimiter {
    /**
     * Check if a request should be allowed
     * @param clientId Unique identifier for the client
     * @return true if request is allowed, false otherwise
     */
    boolean allowRequest(String clientId);
    
    /**
     * Reset rate limit for a specific client
     * @param clientId Unique identifier for the client
     */
    void reset(String clientId);
    
    /**
     * Get the configuration for this rate limiter
     * @return RateLimiterConfig object
     */
    RateLimiterConfig getConfig();
}
```

**Design Decisions:**
- Simple boolean return for basic usage
- `reset()` method for testing and admin operations
- Exposes config for introspection

### 2. RateLimiterConfig

```java
/**
 * Configuration for rate limiter behavior
 */
class RateLimiterConfig {
    private final int maxRequests;          // Maximum requests allowed
    private final long timeWindowMillis;    // Time window in milliseconds
    private final RateLimiterType type;     // Algorithm type
    
    public RateLimiterConfig(int maxRequests, long timeWindowMillis, 
                             RateLimiterType type) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.type = type;
    }
    
    // Getters
    public int getMaxRequests() { return maxRequests; }
    public long getTimeWindowMillis() { return timeWindowMillis; }
    public RateLimiterType getType() { return type; }
}

enum RateLimiterType {
    TOKEN_BUCKET,
    LEAKY_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER
}
```

**Design Decisions:**
- Immutable configuration (final fields)
- Type-safe enum for algorithm selection
- Simple value object pattern

### 3. LimiterFactory

```java
/**
 * Factory for creating rate limiter instances
 * Uses Factory Pattern for extensibility
 */
class RateLimiterFactory {
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

**Design Decisions:**
- Factory pattern allows adding new algorithms without changing client code
- Static factory method for simplicity
- Throws exception for unknown types

### 4. RateLimitResult

```java
/**
 * Result of a rate limit check
 */
class RateLimitResult {
    private final boolean allowed;
    private final int remaining;
    private final Long retryAfterMs;
    
    public RateLimitResult(boolean allowed, int remaining, Long retryAfterMs) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.retryAfterMs = retryAfterMs;
    }
    
    public boolean isAllowed() { return allowed; }
    public int getRemaining() { return remaining; }
    public Long getRetryAfterMs() { return retryAfterMs; }
}
```

**Design Decisions:**
- Encapsulates all information needed by caller
- Provides retry guidance for blocked requests
- Immutable result object

---

## Algorithm Implementations

### 1. Token Bucket Algorithm

**Concept:**
- Bucket has fixed capacity of tokens
- Tokens are added at fixed rate
- Request consumes one token
- If no tokens available, request is rejected

**Visual Representation:**
```
Time: 0ms        500ms       1000ms      1500ms
      ┌────┐     ┌────┐      ┌────┐      ┌────┐
      │ 10 │ --> │  7 │  --> │  9 │  --> │  6 │
      └────┘     └────┘      └────┘      └────┘
      Start      3 req       +2 tokens   3 req
                 consumed    refilled    consumed
```

**Pros:**
- ✅ Allows bursts of traffic
- ✅ Simple to implement
- ✅ Memory efficient (O(1) per client)

**Cons:**
- ❌ Requires background thread for refilling
- ❌ May allow bursts that overwhelm downstream

**Best For:**
- API rate limiting with burst support
- CDN request limiting
- General purpose rate limiting

**Implementation Highlights:**
```java
class TokenBucket {
    private final int capacity;
    private final AtomicInteger tokens;
    
    public boolean tryConsume() {
        while (true) {
            int current = tokens.get();
            if (current <= 0) return false;
            if (tokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }
    
    public void refill() {
        tokens.updateAndGet(current -> Math.min(capacity, current + 1));
    }
}
```

**Thread Safety:** Lock-free CAS operations

### 2. Leaky Bucket Algorithm

**Concept:**
- Requests added to queue (bucket)
- Requests processed at fixed rate
- If queue full, request rejected

**Visual Representation:**
```
Incoming Requests    Bucket (Queue)      Outgoing (Fixed Rate)
     ┌───┐              ╔═══╗               ┌───┐
     │ R │ ──────►      ║ R ║  ──────►      │ R │  1 req/sec
     └───┘              ║ R ║               └───┘
     ┌───┐              ║ R ║
     │ R │ ──────►      ╚═══╝
     └───┘               Full
```

**Pros:**
- ✅ Smooth traffic output
- ✅ Predictable output rate
- ✅ Good for traffic shaping

**Cons:**
- ❌ Can add latency (queuing)
- ❌ Queue management overhead

**Best For:**
- Traffic shaping to downstream systems
- Network bandwidth limiting
- Smooth load distribution

**Implementation Highlights:**
```java
class LeakyBucket {
    private final Queue<Long> queue;
    
    public synchronized boolean tryAdd() {
        if (queue.size() >= capacity) return false;
        queue.offer(System.currentTimeMillis());
        return true;
    }
    
    public synchronized void leak() {
        queue.poll(); // Remove one request
    }
}
```

**Thread Safety:** Synchronized methods

### 3. Fixed Window Counter

**Concept:**
- Time divided into fixed windows
- Counter tracks requests in current window
- Counter resets at window boundary

**Visual Representation:**
```
Window 1 (0-1000ms)    Window 2 (1000-2000ms)    Window 3 (2000-3000ms)
     ┌─────────┐            ┌─────────┐               ┌─────────┐
     │ Count=5 │            │ Count=3 │               │ Count=7 │
     └─────────┘            └─────────┘               └─────────┘
         ▲                      ▲ Reset                   ▲ Reset
      5 requests             at 1000ms                 at 2000ms
```

**Pros:**
- ✅ Memory efficient (O(1) per client)
- ✅ Very simple implementation
- ✅ Fast O(1) operations

**Cons:**
- ❌ Boundary issue: 2x burst at window edges
- ❌ Less accurate than sliding window

**Best For:**
- Simple rate limiting with low memory
- High-throughput systems
- When approximate limiting is acceptable

**The Boundary Problem:**
```
Time:    |-------- Window 1 --------|-------- Window 2 --------|
         0ms                      1000ms                     2000ms
Limit:   5 requests/window         5 requests/window

Requests:         [4 req at 900ms] + [5 req at 1100ms] = 9 requests in 200ms!
                           ▲                    ▲
                        Window 1             Window 2
```

**Implementation Highlights:**
```java
class WindowCounter {
    private volatile long currentWindow;
    private final AtomicInteger count;
    
    public synchronized boolean increment(long window, int maxRequests) {
        if (window != currentWindow) {
            currentWindow = window;
            count.set(0);
        }
        if (count.get() >= maxRequests) return false;
        count.incrementAndGet();
        return true;
    }
}
```

**Thread Safety:** Synchronized method with atomic counter

### 4. Sliding Window Log

**Concept:**
- Stores timestamp of each request
- Removes timestamps outside window
- Most accurate algorithm

**Visual Representation:**
```
Time Window: 1000ms
Current Time: 1500ms

Request Log:
[600ms, 750ms, 900ms, 1100ms, 1400ms]
   ❌     ❌      ✅      ✅       ✅
  old    old   valid   valid   valid

Window Start: 1500ms - 1000ms = 500ms
Valid Requests: 3 (900ms, 1100ms, 1400ms)
```

**Pros:**
- ✅ Most accurate
- ✅ No boundary issues
- ✅ True sliding window

**Cons:**
- ❌ High memory usage (O(n) where n = requests in window)
- ❌ O(n) lookup time

**Best For:**
- High-value APIs (payment, financial)
- When accuracy is critical
- Low-frequency, high-impact requests

**Implementation Highlights:**
```java
class SlidingWindowLog {
    private final Queue<Long> requestTimestamps;
    
    public synchronized boolean allowRequest(long currentTime, int maxRequests) {
        // Remove expired timestamps
        long windowStart = currentTime - windowSize;
        while (!requestTimestamps.isEmpty() && 
               requestTimestamps.peek() <= windowStart) {
            requestTimestamps.poll();
        }
        
        if (requestTimestamps.size() >= maxRequests) return false;
        requestTimestamps.offer(currentTime);
        return true;
    }
}
```

**Thread Safety:** Synchronized method

### 5. Sliding Window Counter (Hybrid)

**Concept:**
- Combines fixed window with sliding window
- Uses weighted count from previous window
- Balance of accuracy and efficiency

**Mathematical Formula:**
```
EstimatedCount = PreviousWindowCount × (1 - CurrentProgress) + CurrentWindowCount

Where CurrentProgress = (CurrentTime % WindowSize) / WindowSize
```

**Example Calculation:**
```
Window Size: 1000ms
Current Time: 1300ms (300ms into current window)
Previous Window Count: 10 requests
Current Window Count: 3 requests

CurrentProgress = 300 / 1000 = 0.3
PreviousWeight = 1 - 0.3 = 0.7

EstimatedCount = 10 × 0.7 + 3 = 7 + 3 = 10 requests
```

**Visual Representation:**
```
Previous Window      Current Window
  (0-1000ms)          (1000-2000ms)
┌──────────────┐    ┌──────────────┐
│   10 req     │    │    3 req     │
└──────────────┘    └──────────────┘
         ▲               ▲
      Weight:         Weight:
       0.7             1.0
       
Time: 1300ms
Estimate: 10×0.7 + 3×1.0 = 10 requests
```

**Pros:**
- ✅ Good accuracy (better than fixed window)
- ✅ Memory efficient (O(1) per client)
- ✅ No boundary issues
- ✅ Fast O(1) operations

**Cons:**
- ❌ Slight approximation (not 100% accurate)
- ❌ More complex than fixed window

**Best For:**
- General purpose rate limiting
- Production APIs
- Balance of performance and accuracy

**Implementation Highlights:**
```java
class SlidingCounter {
    private volatile long previousWindowStart;
    private volatile long currentWindowStart;
    private final AtomicInteger previousCount;
    private final AtomicInteger currentCount;
    
    public synchronized boolean allowRequest(long currentTime, int maxRequests) {
        long currentWindow = currentTime / windowSize;
        
        if (currentWindow > currentWindowStart) {
            previousWindowStart = currentWindowStart;
            previousCount.set(currentCount.get());
            currentWindowStart = currentWindow;
            currentCount.set(0);
        }
        
        long windowProgress = currentTime % windowSize;
        double previousWeight = 1.0 - (double) windowProgress / windowSize;
        double estimatedCount = previousCount.get() * previousWeight + currentCount.get();
        
        if (estimatedCount >= maxRequests) return false;
        currentCount.incrementAndGet();
        return true;
    }
}
```

**Thread Safety:** Synchronized method with atomic counters

---

## Complete Code Implementation

### Main RateLimiter Interface

```java
/**
 * Main interface for all rate limiting strategies
 */
interface RateLimiter {
    boolean allowRequest(String clientId);
    void reset(String clientId);
    RateLimiterConfig getConfig();
}
```

### Token Bucket Implementation (Full)

```java
class TokenBucketRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, TokenBucket> buckets;
    private final ScheduledExecutorService scheduler;

    public TokenBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.buckets = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        startTokenRefiller();
    }

    private void startTokenRefiller() {
        long refillInterval = config.getTimeWindowMillis() / config.getMaxRequests();
        scheduler.scheduleAtFixedRate(() -> {
            buckets.values().forEach(TokenBucket::refill);
        }, refillInterval, refillInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean allowRequest(String clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, 
            k -> new TokenBucket(config.getMaxRequests()));
        return bucket.tryConsume();
    }

    @Override
    public void reset(String clientId) {
        buckets.remove(clientId);
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }

    private static class TokenBucket {
        private final int capacity;
        private final AtomicInteger tokens;

        public TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
        }

        public boolean tryConsume() {
            while (true) {
                int current = tokens.get();
                if (current <= 0) return false;
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        public void refill() {
            tokens.updateAndGet(current -> Math.min(capacity, current + 1));
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
```

### Decorator Pattern for Enhanced Features

```java
/**
 * Base decorator for adding features to rate limiters
 */
class RateLimiterDecorator implements RateLimiter {
    protected final RateLimiter rateLimiter;

    public RateLimiterDecorator(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean allowRequest(String clientId) {
        return rateLimiter.allowRequest(clientId);
    }

    @Override
    public void reset(String clientId) {
        rateLimiter.reset(clientId);
    }

    @Override
    public RateLimiterConfig getConfig() {
        return rateLimiter.getConfig();
    }
}

/**
 * Logging decorator - adds request logging
 */
class LoggingRateLimiter extends RateLimiterDecorator {
    public LoggingRateLimiter(RateLimiter rateLimiter) {
        super(rateLimiter);
    }

    @Override
    public boolean allowRequest(String clientId) {
        boolean allowed = super.allowRequest(clientId);
        System.out.println(String.format(
            "[RateLimiter] Client: %s, Allowed: %s, Time: %d",
            clientId, allowed, System.currentTimeMillis()
        ));
        return allowed;
    }
}

/**
 * Metrics decorator - tracks statistics
 */
class MetricsRateLimiter extends RateLimiterDecorator {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong blockedRequests = new AtomicLong(0);

    public MetricsRateLimiter(RateLimiter rateLimiter) {
        super(rateLimiter);
    }

    @Override
    public boolean allowRequest(String clientId) {
        totalRequests.incrementAndGet();
        boolean allowed = super.allowRequest(clientId);
        
        if (allowed) {
            allowedRequests.incrementAndGet();
        } else {
            blockedRequests.incrementAndGet();
        }
        
        return allowed;
    }

    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("total", totalRequests.get());
        metrics.put("allowed", allowedRequests.get());
        metrics.put("blocked", blockedRequests.get());
        return metrics;
    }
}
```

### High-Level Service API

```java
/**
 * Main service class that manages rate limiting across endpoints
 */
class RateLimiterService {
    private final Map<String, RateLimiter> rateLimiters;
    private final RateLimiterConfig defaultConfig;

    public RateLimiterService(RateLimiterConfig defaultConfig) {
        this.rateLimiters = new ConcurrentHashMap<>();
        this.defaultConfig = defaultConfig;
    }

    public void registerRateLimiter(String endpoint, RateLimiter rateLimiter) {
        rateLimiters.put(endpoint, rateLimiter);
    }

    public boolean checkRateLimit(String endpoint, String clientId) {
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(
            endpoint,
            k -> RateLimiterFactory.createRateLimiter(defaultConfig)
        );
        return rateLimiter.allowRequest(clientId);
    }

    public void resetRateLimit(String endpoint, String clientId) {
        RateLimiter rateLimiter = rateLimiters.get(endpoint);
        if (rateLimiter != null) {
            rateLimiter.reset(clientId);
        }
    }
}
```

### Usage Example

```java
public class RateLimiterDemo {
    public static void main(String[] args) {
        // Create service with default config
        RateLimiterConfig defaultConfig = new RateLimiterConfig(
            100,  // 100 requests
            60000,  // per 60 seconds
            RateLimiterType.TOKEN_BUCKET
        );
        
        RateLimiterService service = new RateLimiterService(defaultConfig);
        
        // Register custom rate limiter for sensitive endpoint
        RateLimiterConfig loginConfig = new RateLimiterConfig(
            5,     // 5 requests
            60000, // per 60 seconds
            RateLimiterType.SLIDING_WINDOW_LOG
        );
        service.registerRateLimiter("/login", 
            RateLimiterFactory.createRateLimiter(loginConfig));
        
        // Check rate limits
        String clientId = "user123";
        
        if (service.checkRateLimit("/login", clientId)) {
            System.out.println("Login request allowed");
        } else {
            System.out.println("Rate limit exceeded");
        }
    }
}
```

---

## Extensibility

### 1. Adding New Rate Limiting Algorithms

**Question:** "How would you add a new rate limiting algorithm?"

**Answer:**
```java
// Step 1: Implement the RateLimiter interface
class AdaptiveRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, AdaptiveState> states;
    
    // Adjusts limits based on system load
    @Override
    public boolean allowRequest(String clientId) {
        AdaptiveState state = states.computeIfAbsent(clientId, 
            k -> new AdaptiveState());
        
        // Adjust limit based on current load
        int dynamicLimit = calculateDynamicLimit();
        return state.checkAndIncrement(dynamicLimit);
    }
    
    private int calculateDynamicLimit() {
        // Adjust based on CPU, memory, or downstream health
        double systemLoad = getSystemLoad();
        return (int) (config.getMaxRequests() * (1.0 - systemLoad));
    }
    
    // ... other methods
}

// Step 2: Add to enum
enum RateLimiterType {
    TOKEN_BUCKET,
    LEAKY_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER,
    ADAPTIVE  // New type
}

// Step 3: Update factory
class RateLimiterFactory {
    public static RateLimiter createRateLimiter(RateLimiterConfig config) {
        switch (config.getType()) {
            // ... existing cases
            case ADAPTIVE:
                return new AdaptiveRateLimiter(config);
            default:
                throw new IllegalArgumentException("Unknown type");
        }
    }
}
```

**Key Points:**
- Open/Closed Principle: Open for extension, closed for modification
- No changes to existing code or clients
- Factory pattern makes this seamless

### 2. Dynamic Configuration Updates

**Question:** "How would you handle dynamic configuration updates?"

**Answer:**
```java
class DynamicRateLimiterService extends RateLimiterService {
    private final ConfigWatcher configWatcher;
    
    public DynamicRateLimiterService(RateLimiterConfig defaultConfig) {
        super(defaultConfig);
        this.configWatcher = new ConfigWatcher(this);
        this.configWatcher.start();
    }
    
    public void updateConfiguration(String endpoint, RateLimiterConfig newConfig) {
        // Graceful transition: create new limiter, swap atomically
        RateLimiter newLimiter = RateLimiterFactory.createRateLimiter(newConfig);
        RateLimiter oldLimiter = rateLimiters.put(endpoint, newLimiter);
        
        // Clean up old limiter if needed
        if (oldLimiter instanceof TokenBucketRateLimiter) {
            ((TokenBucketRateLimiter) oldLimiter).shutdown();
        }
        
        System.out.println("Updated config for endpoint: " + endpoint);
    }
}

class ConfigWatcher {
    private final DynamicRateLimiterService service;
    private final ScheduledExecutorService scheduler;
    
    public ConfigWatcher(DynamicRateLimiterService service) {
        this.service = service;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            // Poll config source (file, DB, etcd, etc.)
            Map<String, RateLimiterConfig> newConfigs = loadConfigs();
            newConfigs.forEach(service::updateConfiguration);
        }, 0, 60, TimeUnit.SECONDS);
    }
    
    private Map<String, RateLimiterConfig> loadConfigs() {
        // Load from external source
        return new HashMap<>();
    }
}
```

**Key Points:**
- Atomic swap of rate limiters
- No downtime during config updates
- Graceful cleanup of old resources

### 3. Thread Safety for Concurrent Requests

**Question:** "How would you handle thread safety?"

**Current Approaches:**
1. **Synchronized Methods** (Leaky Bucket, Sliding Window Log)
   - Simple but can become bottleneck
   - One thread at a time per client

2. **Lock-Free CAS** (Token Bucket)
   - Better performance
   - Compare-and-swap operations
   - Retry on contention

3. **Striped Locks** (Advanced)
```java
class StripedRateLimiter implements RateLimiter {
    private final int stripes = 16;
    private final Lock[] locks;
    private final Map<String, ClientState>[] segments;
    
    @SuppressWarnings("unchecked")
    public StripedRateLimiter(RateLimiterConfig config) {
        this.locks = new Lock[stripes];
        this.segments = new Map[stripes];
        
        for (int i = 0; i < stripes; i++) {
            locks[i] = new ReentrantLock();
            segments[i] = new HashMap<>();
        }
    }
    
    @Override
    public boolean allowRequest(String clientId) {
        int stripe = Math.abs(clientId.hashCode()) % stripes;
        Lock lock = locks[stripe];
        
        lock.lock();
        try {
            ClientState state = segments[stripe].computeIfAbsent(
                clientId, k -> new ClientState());
            return state.checkLimit();
        } finally {
            lock.unlock();
        }
    }
    
    // Other methods...
}
```

**Key Points:**
- Reduces contention by spreading load across multiple locks
- Better scalability for high concurrency
- Hash clientId to determine stripe

### 4. Memory Growth Management

**Question:** "How would you handle memory growth from tracking many clients?"

**Answer:**
```java
class MemoryEfficientRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, ClientState> states;
    private final ScheduledExecutorService cleanupScheduler;
    
    public MemoryEfficientRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.states = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newScheduledThreadPool(1);
        startCleanup();
    }
    
    private void startCleanup() {
        // Remove inactive clients every 5 minutes
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long inactiveThreshold = config.getTimeWindowMillis() * 2;
            
            states.entrySet().removeIf(entry -> {
                ClientState state = entry.getValue();
                return (now - state.getLastAccessTime()) > inactiveThreshold;
            });
            
            System.out.println("Cleanup: " + states.size() + " active clients");
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    @Override
    public boolean allowRequest(String clientId) {
        ClientState state = states.computeIfAbsent(clientId, 
            k -> new ClientState(config));
        state.updateLastAccessTime();
        return state.checkLimit();
    }
    
    private static class ClientState {
        private volatile long lastAccessTime;
        private final RateLimiterConfig config;
        // ... other state
        
        public ClientState(RateLimiterConfig config) {
            this.config = config;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
        
        public boolean checkLimit() {
            // Implementation
            return true;
        }
    }
    
    public void shutdown() {
        cleanupScheduler.shutdown();
    }
}
```

**Strategies:**
1. **TTL-based eviction:** Remove inactive clients after timeout
2. **LRU cache:** Use fixed-size cache with LRU eviction
3. **Bloom filters:** For negative lookups (client not rate limited)
4. **Memory limits:** Set max clients, use eviction policy

**Key Points:**
- Periodic cleanup prevents unbounded growth
- Track last access time per client
- Balance between memory and accuracy

### 5. Distributed Rate Limiting

**Question:** "How would you extend this to work across multiple servers?"

**Answer:**
```java
/**
 * Distributed rate limiter using Redis
 */
class RedisRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final RedisClient redis;
    
    public RedisRateLimiter(RateLimiterConfig config, RedisClient redis) {
        this.config = config;
        this.redis = redis;
    }
    
    @Override
    public boolean allowRequest(String clientId) {
        String key = "rate_limit:" + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - config.getTimeWindowMillis();
        
        // Use Redis Sorted Set (ZSET) for sliding window log
        String luaScript = 
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])\n" +
            "local count = redis.call('ZCARD', KEYS[1])\n" +
            "if count < tonumber(ARGV[2]) then\n" +
            "  redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[5])\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end";
        
        Object result = redis.eval(luaScript,
            Arrays.asList(key),
            Arrays.asList(
                String.valueOf(windowStart),
                String.valueOf(config.getMaxRequests()),
                String.valueOf(now),
                UUID.randomUUID().toString(),
                String.valueOf(config.getTimeWindowMillis() / 1000)
            )
        );
        
        return (Long) result == 1;
    }
    
    @Override
    public void reset(String clientId) {
        String key = "rate_limit:" + clientId;
        redis.del(key);
    }
    
    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }
}
```

**Key Points:**
- Use Redis for shared state across servers
- Lua scripts ensure atomicity
- ZSET for sliding window implementation
- Set expiration to prevent memory leak

---

## Interview Tips

### Approach Strategy

**1. Start with Clarifying Questions (3-5 minutes)**
- What algorithms should we support?
- In-memory or distributed?
- Thread safety requirements?
- Memory constraints?
- Performance requirements?

**2. Define Requirements Clearly (2-3 minutes)**
- Write down functional requirements
- Note non-functional requirements
- Explicitly state what's out of scope

**3. Design Core Interfaces First (5 minutes)**
- Start with `RateLimiter` interface
- Define `RateLimiterConfig`
- Plan for extensibility early

**4. Implement One Algorithm Well (10-15 minutes)**
- Start with Token Bucket or Sliding Window Counter
- Get one implementation working correctly
- Show thread safety considerations

**5. Discuss Trade-offs (5 minutes)**
- Compare algorithms (accuracy vs memory)
- Discuss when to use each
- Show understanding of real-world considerations

### Common Interview Questions & Answers

**Q1: "Why use Strategy pattern for algorithms?"**
**A:** Different algorithms have different trade-offs. Strategy pattern allows:
- Runtime algorithm selection
- Easy addition of new algorithms
- No changes to client code when adding algorithms
- Each algorithm encapsulated in its own class

**Q2: "How do you handle the boundary problem in Fixed Window?"**
**A:** Use Sliding Window Counter algorithm:
- Maintains both previous and current window counts
- Calculates weighted average based on time position
- Smooth transition without 2x burst at boundaries
- Only slight approximation, much better than fixed window

**Q3: "Which algorithm would you recommend for production?"**
**A:** Depends on requirements:
- **General purpose:** Sliding Window Counter (balance of accuracy and efficiency)
- **Burst support:** Token Bucket (allows controlled bursts)
- **Traffic shaping:** Leaky Bucket (smooth output)
- **High accuracy:** Sliding Window Log (memory intensive)
- **Simple/high throughput:** Fixed Window (acceptable approximation)

**Q4: "How would you monitor and debug rate limiting issues?"**
**A:**
```java
// Add metrics decorator
MetricsRateLimiter metricsLimiter = new MetricsRateLimiter(baseLimiter);

// Add logging decorator
LoggingRateLimiter loggingLimiter = new LoggingRateLimiter(metricsLimiter);

// Expose metrics endpoint
@GET("/metrics/rate-limiter")
public Map<String, Object> getMetrics() {
    return Map.of(
        "total_requests", metrics.getTotalRequests(),
        "blocked_requests", metrics.getBlockedRequests(),
        "block_rate", metrics.getBlockRate(),
        "active_clients", getActiveClientCount()
    );
}
```

**Q5: "What happens when server restarts?"**
**A:** Two approaches:
1. **Accept loss (simple):** In-memory state lost, clients get fresh limits
2. **Persist state (complex):** 
   - Periodically snapshot to disk/Redis
   - Restore on startup
   - Add complexity, reduces performance

For most use cases, accepting loss is fine since:
- Limits reset naturally over time
- Brief window of looser limiting is acceptable
- Much simpler implementation

### Design Patterns Used

1. **Strategy Pattern:** Different rate limiting algorithms
2. **Factory Pattern:** Creating rate limiters based on config
3. **Decorator Pattern:** Adding logging, metrics, features
4. **Singleton Pattern:** Single instance of RateLimiterService
5. **Template Method:** Common structure for all limiters

### Algorithm Comparison Table

| Algorithm | Accuracy | Memory | Complexity | Burst | Best For |
|-----------|----------|--------|------------|-------|----------|
| Token Bucket | Good | O(1) | Medium | ✅ Yes | General API rate limiting |
| Leaky Bucket | Good | O(n) | Medium | ❌ No | Traffic shaping |
| Fixed Window | Low | O(1) | Low | ⚠️ Boundary | Simple, high throughput |
| Sliding Window Log | High | O(n) | High | ❌ No | Financial, high-value |
| Sliding Window Counter | Good | O(1) | Medium | ⚠️ Limited | Production, balanced |

### Expected Level Performance

**Junior Engineer:**
- Implement one algorithm (Fixed Window or Token Bucket)
- Basic thread safety understanding
- Simple interface design

**Mid-Level Engineer:**
- Implement 2-3 algorithms
- Compare trade-offs clearly
- Proper thread safety (CAS, synchronized)
- Factory pattern for extensibility
- Discussion of distributed approach

**Senior Engineer:**
- Complete system with multiple algorithms
- Decorator pattern for features
- Memory management strategies
- Distributed implementation approach
- Production considerations (monitoring, config updates)
- Clear articulation of trade-offs

### Code Quality Checklist

✅ **Interface Design**
- Clear, focused interfaces
- Proper abstraction levels
- Extensibility built-in

✅ **Thread Safety**
- CAS operations for performance
- Proper synchronization where needed
- No data races or deadlocks

✅ **Error Handling**
- Null checks where needed
- Proper exception handling
- Graceful degradation

✅ **Documentation**
- Clear comments on complex logic
- Usage examples
- Trade-off discussions

✅ **Testing Considerations**
- Easy to unit test
- Mockable dependencies
- Test edge cases (boundary, concurrency)

---

## Summary

This Rate Limiter Low-Level Design demonstrates:

1. **Multiple Algorithms:** Five different rate limiting strategies, each with specific use cases
2. **Solid Design Principles:** Strategy, Factory, and Decorator patterns for extensibility
3. **Thread Safety:** Lock-free CAS operations and proper synchronization
4. **Production Ready:** Memory management, metrics, logging, and monitoring
5. **Interview Success:** Clear requirements, trade-off analysis, extensibility discussion

**Key Takeaways:**
- Choose algorithm based on requirements (accuracy vs efficiency)
- Use patterns for extensibility (Strategy, Factory, Decorator)
- Consider thread safety early
- Plan for memory management in production
- Understand trade-offs and communicate them clearly

**Related Resources:**
- System Design: [Distributed Rate Limiter HLD](../HLD/rate-limiter-system-design-HLD.md)
- Implementation: [RateLimiterSystem.java](./RateLimiterSystem.java)
- Patterns: [Strategy Pattern](./Design-Patterns/StrategyPattern.java)

---

*Last Updated: January 4, 2026*
*Difficulty Level: Medium to Hard*
*Interview Success Rate: High with proper preparation*
