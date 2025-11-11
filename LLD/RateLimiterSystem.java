/**
 * RATE LIMITER - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates various rate limiting algorithms:
 * 1. Token Bucket Algorithm
 * 2. Leaky Bucket Algorithm
 * 3. Fixed Window Counter
 * 4. Sliding Window Log
 * 5. Sliding Window Counter
 * 
 * Key Design Principles:
 * - Strategy Pattern for different algorithms
 * - Thread-safe implementations
 * - Distributed rate limiting support
 * - Configurable per user/IP/API key
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// ============================================================
// CORE INTERFACES
// ============================================================

/**
 * Main interface for all rate limiting strategies
 */
interface RateLimiter {
    boolean allowRequest(String clientId);
    void reset(String clientId);
    RateLimiterConfig getConfig();
}

/**
 * Configuration for rate limiter
 */
class RateLimiterConfig {
    private final int maxRequests;
    private final long timeWindowMillis;
    private final RateLimiterType type;

    public RateLimiterConfig(int maxRequests, long timeWindowMillis, RateLimiterType type) {
        this.maxRequests = maxRequests;
        this.timeWindowMillis = timeWindowMillis;
        this.type = type;
    }

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

// ============================================================
// 1. TOKEN BUCKET ALGORITHM
// ============================================================

/**
 * Token Bucket Algorithm:
 * - Bucket has fixed capacity of tokens
 * - Tokens are added at fixed rate
 * - Request consumes one token
 * - If no tokens available, request is rejected
 * 
 * Pros: Allows bursts, Simple implementation
 * Cons: Requires background thread for token refill
 * 
 * Use Case: API rate limiting with burst support
 */
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
                if (current <= 0) {
                    return false;
                }
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

// ============================================================
// 2. LEAKY BUCKET ALGORITHM
// ============================================================

/**
 * Leaky Bucket Algorithm:
 * - Requests added to queue (bucket)
 * - Requests processed at fixed rate
 * - If queue full, request rejected
 * 
 * Pros: Smooth traffic, predictable output rate
 * Cons: Can add latency, queue management overhead
 * 
 * Use Case: Traffic shaping, smooth load on downstream systems
 */
class LeakyBucketRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, LeakyBucket> buckets;
    private final ScheduledExecutorService scheduler;

    public LeakyBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.buckets = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        startLeaker();
    }

    private void startLeaker() {
        long leakInterval = config.getTimeWindowMillis() / config.getMaxRequests();
        scheduler.scheduleAtFixedRate(() -> {
            buckets.values().forEach(LeakyBucket::leak);
        }, leakInterval, leakInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean allowRequest(String clientId) {
        LeakyBucket bucket = buckets.computeIfAbsent(clientId,
            k -> new LeakyBucket(config.getMaxRequests()));
        return bucket.tryAdd();
    }

    @Override
    public void reset(String clientId) {
        buckets.remove(clientId);
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }

    private static class LeakyBucket {
        private final int capacity;
        private final Queue<Long> queue;

        public LeakyBucket(int capacity) {
            this.capacity = capacity;
            this.queue = new ConcurrentLinkedQueue<>();
        }

        public synchronized boolean tryAdd() {
            if (queue.size() >= capacity) {
                return false;
            }
            queue.offer(System.currentTimeMillis());
            return true;
        }

        public synchronized void leak() {
            queue.poll(); // Remove one request
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}

// ============================================================
// 3. FIXED WINDOW COUNTER
// ============================================================

/**
 * Fixed Window Counter:
 * - Time divided into fixed windows
 * - Counter tracks requests in current window
 * - Counter resets at window boundary
 * 
 * Pros: Memory efficient, simple
 * Cons: Boundary issue (2x burst at window edges)
 * 
 * Use Case: Simple rate limiting with low memory
 */
class FixedWindowRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, WindowCounter> counters;

    public FixedWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.counters = new ConcurrentHashMap<>();
    }

    @Override
    public boolean allowRequest(String clientId) {
        WindowCounter counter = counters.computeIfAbsent(clientId,
            k -> new WindowCounter(config.getTimeWindowMillis()));
        
        long currentWindow = getCurrentWindow();
        return counter.increment(currentWindow, config.getMaxRequests());
    }

    private long getCurrentWindow() {
        return System.currentTimeMillis() / config.getTimeWindowMillis();
    }

    @Override
    public void reset(String clientId) {
        counters.remove(clientId);
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }

    private static class WindowCounter {
        private final long windowSize;
        private volatile long currentWindow;
        private final AtomicInteger count;

        public WindowCounter(long windowSize) {
            this.windowSize = windowSize;
            this.currentWindow = -1;
            this.count = new AtomicInteger(0);
        }

        public synchronized boolean increment(long window, int maxRequests) {
            if (window != currentWindow) {
                currentWindow = window;
                count.set(0);
            }

            if (count.get() >= maxRequests) {
                return false;
            }

            count.incrementAndGet();
            return true;
        }
    }
}

// ============================================================
// 4. SLIDING WINDOW LOG
// ============================================================

/**
 * Sliding Window Log:
 * - Stores timestamp of each request
 * - Removes timestamps outside window
 * - Accurate but memory intensive
 * 
 * Pros: Most accurate, no boundary issues
 * Cons: High memory usage, O(n) lookup
 * 
 * Use Case: High-value APIs, financial transactions
 */
class SlidingWindowLogRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, SlidingWindowLog> logs;

    public SlidingWindowLogRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.logs = new ConcurrentHashMap<>();
    }

    @Override
    public boolean allowRequest(String clientId) {
        SlidingWindowLog log = logs.computeIfAbsent(clientId,
            k -> new SlidingWindowLog(config.getTimeWindowMillis()));
        
        long currentTime = System.currentTimeMillis();
        return log.allowRequest(currentTime, config.getMaxRequests());
    }

    @Override
    public void reset(String clientId) {
        logs.remove(clientId);
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }

    private static class SlidingWindowLog {
        private final long windowSize;
        private final Queue<Long> requestTimestamps;

        public SlidingWindowLog(long windowSize) {
            this.windowSize = windowSize;
            this.requestTimestamps = new ConcurrentLinkedQueue<>();
        }

        public synchronized boolean allowRequest(long currentTime, int maxRequests) {
            // Remove expired timestamps
            long windowStart = currentTime - windowSize;
            while (!requestTimestamps.isEmpty() && 
                   requestTimestamps.peek() <= windowStart) {
                requestTimestamps.poll();
            }

            if (requestTimestamps.size() >= maxRequests) {
                return false;
            }

            requestTimestamps.offer(currentTime);
            return true;
        }
    }
}

// ============================================================
// 5. SLIDING WINDOW COUNTER (HYBRID)
// ============================================================

/**
 * Sliding Window Counter:
 * - Combines fixed window with sliding window
 * - Uses weighted count from previous window
 * - Memory efficient with good accuracy
 * 
 * Pros: Balance of accuracy and efficiency
 * Cons: Slight approximation
 * 
 * Use Case: General purpose rate limiting
 */
class SlidingWindowCounterRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final ConcurrentHashMap<String, SlidingCounter> counters;

    public SlidingWindowCounterRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.counters = new ConcurrentHashMap<>();
    }

    @Override
    public boolean allowRequest(String clientId) {
        SlidingCounter counter = counters.computeIfAbsent(clientId,
            k -> new SlidingCounter(config.getTimeWindowMillis()));
        
        long currentTime = System.currentTimeMillis();
        return counter.allowRequest(currentTime, config.getMaxRequests());
    }

    @Override
    public void reset(String clientId) {
        counters.remove(clientId);
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }

    private static class SlidingCounter {
        private final long windowSize;
        private volatile long previousWindowStart;
        private volatile long currentWindowStart;
        private final AtomicInteger previousCount;
        private final AtomicInteger currentCount;

        public SlidingCounter(long windowSize) {
            this.windowSize = windowSize;
            this.previousWindowStart = 0;
            this.currentWindowStart = 0;
            this.previousCount = new AtomicInteger(0);
            this.currentCount = new AtomicInteger(0);
        }

        public synchronized boolean allowRequest(long currentTime, int maxRequests) {
            long currentWindow = currentTime / windowSize;
            
            // Slide window if needed
            if (currentWindow > currentWindowStart) {
                previousWindowStart = currentWindowStart;
                previousCount.set(currentCount.get());
                currentWindowStart = currentWindow;
                currentCount.set(0);
            }

            // Calculate weighted count
            long windowProgress = currentTime % windowSize;
            double previousWeight = 1.0 - (double) windowProgress / windowSize;
            double estimatedCount = previousCount.get() * previousWeight + currentCount.get();

            if (estimatedCount >= maxRequests) {
                return false;
            }

            currentCount.incrementAndGet();
            return true;
        }
    }
}

// ============================================================
// FACTORY PATTERN FOR RATE LIMITER CREATION
// ============================================================

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
                throw new IllegalArgumentException("Unknown rate limiter type: " + config.getType());
        }
    }
}

// ============================================================
// DISTRIBUTED RATE LIMITER (Redis-based)
// ============================================================

/**
 * Interface for distributed rate limiting using Redis
 * This would use Redis commands like INCR, EXPIRE for distributed coordination
 */
interface DistributedRateLimiter extends RateLimiter {
    // Redis-based implementation using Lua scripts for atomicity
    // Example keys: rate_limit:{clientId}:{window}
}

// ============================================================
// RATE LIMITER MIDDLEWARE/DECORATOR
// ============================================================

/**
 * Decorator pattern for adding features to rate limiters
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
 * Logging decorator
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
 * Metrics decorator
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

// ============================================================
// RATE LIMITER SERVICE (HIGH-LEVEL API)
// ============================================================

/**
 * Main service class that manages rate limiting
 */
class RateLimiterService {
    private final Map<String, RateLimiter> rateLimiters;
    private final RateLimiterConfig defaultConfig;

    public RateLimiterService(RateLimiterConfig defaultConfig) {
        this.rateLimiters = new ConcurrentHashMap<>();
        this.defaultConfig = defaultConfig;
    }

    public void registerRateLimiter(String name, RateLimiter rateLimiter) {
        rateLimiters.put(name, rateLimiter);
    }

    public boolean checkRateLimit(String rateLimiterName, String clientId) {
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(
            rateLimiterName,
            k -> RateLimiterFactory.createRateLimiter(defaultConfig)
        );
        return rateLimiter.allowRequest(clientId);
    }

    public void resetRateLimit(String rateLimiterName, String clientId) {
        RateLimiter rateLimiter = rateLimiters.get(rateLimiterName);
        if (rateLimiter != null) {
            rateLimiter.reset(clientId);
        }
    }
}

// ============================================================
// DEMO AND TESTING
// ============================================================

public class RateLimiterSystem {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== RATE LIMITER DEMO ===\n");
        
        // Demo 1: Token Bucket
        demoTokenBucket();
        
        // Demo 2: Fixed Window
        demoFixedWindow();
        
        // Demo 3: Sliding Window Counter
        demoSlidingWindowCounter();
        
        // Demo 4: Multiple Algorithms Comparison
        compareAlgorithms();
        
        // Demo 5: With Metrics
        demoWithMetrics();
    }

    private static void demoTokenBucket() throws InterruptedException {
        System.out.println("--- Token Bucket Algorithm ---");
        RateLimiterConfig config = new RateLimiterConfig(5, 1000, RateLimiterType.TOKEN_BUCKET);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(config);
        
        String clientId = "user123";
        
        // Try 10 requests immediately
        for (int i = 1; i <= 10; i++) {
            boolean allowed = limiter.allowRequest(clientId);
            System.out.println("Request " + i + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
        }
        
        // Wait and try again
        Thread.sleep(1000);
        System.out.println("\nAfter 1 second:");
        for (int i = 1; i <= 3; i++) {
            boolean allowed = limiter.allowRequest(clientId);
            System.out.println("Request " + i + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
        }
        
        limiter.shutdown();
        System.out.println();
    }

    private static void demoFixedWindow() {
        System.out.println("--- Fixed Window Counter ---");
        RateLimiterConfig config = new RateLimiterConfig(3, 1000, RateLimiterType.FIXED_WINDOW);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(config);
        
        String clientId = "user456";
        
        for (int i = 1; i <= 5; i++) {
            boolean allowed = limiter.allowRequest(clientId);
            System.out.println("Request " + i + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
        }
        System.out.println();
    }

    private static void demoSlidingWindowCounter() {
        System.out.println("--- Sliding Window Counter ---");
        RateLimiterConfig config = new RateLimiterConfig(5, 2000, RateLimiterType.SLIDING_WINDOW_COUNTER);
        SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter(config);
        
        String clientId = "user789";
        
        for (int i = 1; i <= 8; i++) {
            boolean allowed = limiter.allowRequest(clientId);
            System.out.println("Request " + i + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
            try {
                Thread.sleep(300); // Space out requests
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    private static void compareAlgorithms() {
        System.out.println("--- Algorithm Comparison ---");
        
        RateLimiterType[] types = {
            RateLimiterType.FIXED_WINDOW,
            RateLimiterType.SLIDING_WINDOW_LOG,
            RateLimiterType.SLIDING_WINDOW_COUNTER
        };
        
        for (RateLimiterType type : types) {
            RateLimiterConfig config = new RateLimiterConfig(5, 1000, type);
            RateLimiter limiter = RateLimiterFactory.createRateLimiter(config);
            
            int allowed = 0;
            int blocked = 0;
            
            for (int i = 0; i < 10; i++) {
                if (limiter.allowRequest("client1")) {
                    allowed++;
                } else {
                    blocked++;
                }
            }
            
            System.out.println(type + " - Allowed: " + allowed + ", Blocked: " + blocked);
        }
        System.out.println();
    }

    private static void demoWithMetrics() {
        System.out.println("--- Rate Limiter with Metrics ---");
        
        RateLimiterConfig config = new RateLimiterConfig(5, 1000, RateLimiterType.TOKEN_BUCKET);
        TokenBucketRateLimiter baseLimiter = new TokenBucketRateLimiter(config);
        MetricsRateLimiter metricsLimiter = new MetricsRateLimiter(baseLimiter);
        
        // Simulate requests
        for (int i = 0; i < 10; i++) {
            metricsLimiter.allowRequest("user999");
        }
        
        // Print metrics
        Map<String, Long> metrics = metricsLimiter.getMetrics();
        System.out.println("Total Requests: " + metrics.get("total"));
        System.out.println("Allowed: " + metrics.get("allowed"));
        System.out.println("Blocked: " + metrics.get("blocked"));
        System.out.println("Block Rate: " + 
            (metrics.get("blocked") * 100.0 / metrics.get("total")) + "%");
        
        baseLimiter.shutdown();
        System.out.println();
    }
}
