import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Rate Limiter System - HELLO Interview Framework
 * 
 * Companies: Atlassian, Microsoft, Oracle, Google, Amazon +13 more
 * Pattern: Strategy Pattern (3 swappable algorithms)
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Strategy Pattern — TokenBucket / FixedWindow / SlidingWindowLog all implement RateLimiter
 * 2. Per-client isolation — each client gets own limiter instance
 * 3. Thread-safe — synchronized for Token Bucket, AtomicInteger for Fixed Window
 * 4. Factory — RateLimiterFactory creates the right strategy from config
 */

// ==================== Strategy Interface ====================

interface RateLimiter {
    /** Returns true if request is allowed, false if rate-limited */
    boolean allowRequest();
    /** Remaining requests before hitting limit */
    int getRemainingRequests();
    /** Algorithm name for display */
    String getAlgorithmName();
}

// ==================== Config ====================

enum RateLimiterType { TOKEN_BUCKET, FIXED_WINDOW, SLIDING_WINDOW_LOG }

class RateLimiterConfig {
    final int maxRequests;
    final long windowSizeMs;
    final int burstCapacity;    // token bucket only
    final double refillRate;    // tokens/sec, token bucket only

    RateLimiterConfig(int maxRequests, long windowSizeMs) {
        this(maxRequests, windowSizeMs, maxRequests, maxRequests);
    }

    RateLimiterConfig(int maxRequests, long windowSizeMs, int burstCapacity, double refillRate) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeMs;
        this.burstCapacity = burstCapacity;
        this.refillRate = refillRate;
    }
}

// ==================== Token Bucket ====================
// Best for: API rate limiting with controlled bursts
// Time: O(1)  Space: O(1)

class TokenBucketLimiter implements RateLimiter {
    private double tokens;
    private final int capacity;
    private final double refillRatePerMs;   // tokens per millisecond
    private long lastRefillTime;

    TokenBucketLimiter(int capacity, double refillRatePerSec) {
        this.capacity = capacity;
        this.tokens = capacity;   // start full
        this.refillRatePerMs = refillRatePerSec / 1000.0;
        this.lastRefillTime = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean allowRequest() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    @Override
    public synchronized int getRemainingRequests() {
        refill();
        return (int) tokens;
    }

    @Override
    public String getAlgorithmName() { return "TokenBucket"; }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillRatePerMs);
            lastRefillTime = now;
        }
    }
}

// ==================== Fixed Window Counter ====================
// Best for: Simple rate limiting where boundary precision not critical
// Time: O(1)  Space: O(1)
// ⚠️ Known issue: boundary burst (2x limit possible at window edge)

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
        // Reset window if expired
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

// ==================== Service (Singleton) ====================
// Per-client rate limiters, managed centrally

class RateLimiterService {
    private static volatile RateLimiterService instance;
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private RateLimiterType defaultType = RateLimiterType.TOKEN_BUCKET;
    private RateLimiterConfig defaultConfig = new RateLimiterConfig(10, 1000, 10, 10); // 10 req/s

    private RateLimiterService() {}

    static RateLimiterService getInstance() {
        if (instance == null) {
            synchronized (RateLimiterService.class) {
                if (instance == null) instance = new RateLimiterService();
            }
        }
        return instance;
    }

    static void resetInstance() {
        synchronized (RateLimiterService.class) { instance = null; }
    }

    /** Set defaults for new clients */
    void setDefaults(RateLimiterType type, RateLimiterConfig config) {
        this.defaultType = type;
        this.defaultConfig = config;
    }

    /** Register client with specific config */
    void registerClient(String clientId, RateLimiterType type, RateLimiterConfig config) {
        limiters.put(clientId, RateLimiterFactory.create(type, config));
    }

    /** Allow request — auto-creates limiter with defaults if new client */
    boolean allowRequest(String clientId) {
        RateLimiter limiter = limiters.computeIfAbsent(clientId,
            k -> RateLimiterFactory.create(defaultType, defaultConfig));
        return limiter.allowRequest();
    }

    int getRemainingRequests(String clientId) {
        RateLimiter limiter = limiters.get(clientId);
        return limiter != null ? limiter.getRemainingRequests() : -1;
    }

    String getAlgorithm(String clientId) {
        RateLimiter limiter = limiters.get(clientId);
        return limiter != null ? limiter.getAlgorithmName() : "none";
    }
}

// ==================== Main Demo ====================

public class RateLimiterSystem {
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  Rate Limiter System - Strategy Pattern (3 algos) ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        // ── Scenario 1: Token Bucket ──
        System.out.println("━━━ Scenario 1: Token Bucket (5 tokens, refill 2/sec) ━━━");
        RateLimiter tokenBucket = new TokenBucketLimiter(5, 2.0);
        System.out.println("  Remaining: " + tokenBucket.getRemainingRequests());

        // Burn through all 5 tokens
        for (int i = 1; i <= 7; i++) {
            boolean allowed = tokenBucket.allowRequest();
            System.out.printf("  Request %d: %s (remaining: %d)%n",
                i, allowed ? "✅ ALLOWED" : "❌ DENIED", tokenBucket.getRemainingRequests());
        }

        // Wait for refill
        System.out.println("  ⏳ Waiting 1 second for refill...");
        Thread.sleep(1000);
        System.out.println("  After refill — Remaining: " + tokenBucket.getRemainingRequests());
        System.out.println("  Request 8: " + (tokenBucket.allowRequest() ? "✅ ALLOWED" : "❌ DENIED"));
        System.out.println();

        // ── Scenario 2: Fixed Window ──
        System.out.println("━━━ Scenario 2: Fixed Window (3 requests per 500ms) ━━━");
        RateLimiter fixedWindow = new FixedWindowLimiter(3, 500);

        for (int i = 1; i <= 5; i++) {
            boolean allowed = fixedWindow.allowRequest();
            System.out.printf("  Request %d: %s (remaining: %d)%n",
                i, allowed ? "✅ ALLOWED" : "❌ DENIED", fixedWindow.getRemainingRequests());
        }

        System.out.println("  ⏳ Waiting for window reset...");
        Thread.sleep(600);
        System.out.println("  After window reset — Remaining: " + fixedWindow.getRemainingRequests());
        System.out.println("  Request 6: " + (fixedWindow.allowRequest() ? "✅ ALLOWED" : "❌ DENIED"));
        System.out.println();

        // ── Scenario 3: Sliding Window Log ──
        System.out.println("━━━ Scenario 3: Sliding Window Log (3 requests per 500ms) ━━━");
        RateLimiter slidingWindow = new SlidingWindowLogLimiter(3, 500);

        for (int i = 1; i <= 5; i++) {
            boolean allowed = slidingWindow.allowRequest();
            System.out.printf("  Request %d: %s (remaining: %d)%n",
                i, allowed ? "✅ ALLOWED" : "❌ DENIED", slidingWindow.getRemainingRequests());
        }

        System.out.println("  ⏳ Waiting for timestamps to expire...");
        Thread.sleep(600);
        System.out.println("  After expiry — Remaining: " + slidingWindow.getRemainingRequests());
        System.out.println("  Request 6: " + (slidingWindow.allowRequest() ? "✅ ALLOWED" : "❌ DENIED"));
        System.out.println();

        // ── Scenario 4: Per-client service with different strategies ──
        System.out.println("━━━ Scenario 4: Per-Client Service (different tiers) ━━━");
        RateLimiterService.resetInstance();
        RateLimiterService service = RateLimiterService.getInstance();

        // Premium client: token bucket, 10 req/s with burst of 20
        service.registerClient("premium-user",
            RateLimiterType.TOKEN_BUCKET,
            new RateLimiterConfig(10, 1000, 20, 10.0));

        // Free client: fixed window, 3 req/s
        service.registerClient("free-user",
            RateLimiterType.FIXED_WINDOW,
            new RateLimiterConfig(3, 1000));

        // API client: sliding window, 5 req/s
        service.registerClient("api-client",
            RateLimiterType.SLIDING_WINDOW_LOG,
            new RateLimiterConfig(5, 1000));

        System.out.println("  Premium [" + service.getAlgorithm("premium-user") + "]:");
        for (int i = 1; i <= 8; i++) {
            System.out.printf("    Req %d: %s%n", i,
                service.allowRequest("premium-user") ? "✅" : "❌");
        }

        System.out.println("  Free [" + service.getAlgorithm("free-user") + "]:");
        for (int i = 1; i <= 5; i++) {
            System.out.printf("    Req %d: %s%n", i,
                service.allowRequest("free-user") ? "✅" : "❌");
        }

        System.out.println("  API [" + service.getAlgorithm("api-client") + "]:");
        for (int i = 1; i <= 7; i++) {
            System.out.printf("    Req %d: %s%n", i,
                service.allowRequest("api-client") ? "✅" : "❌");
        }
        System.out.println();

        // ── Scenario 5: Concurrent thread safety ──
        System.out.println("━━━ Scenario 5: Concurrent Thread Safety (20 threads, 10 limit) ━━━");
        RateLimiter concurrentLimiter = new TokenBucketLimiter(10, 0); // 10 tokens, no refill
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger denied = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                if (concurrentLimiter.allowRequest()) allowed.incrementAndGet();
                else denied.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        System.out.println("  20 concurrent requests → Allowed: " + allowed.get()
            + " | Denied: " + denied.get());
        System.out.println("  Expected: Allowed=10, Denied=10 → "
            + (allowed.get() == 10 ? "✅ CORRECT" : "⚠️ CHECK"));

        // ── Scenario 6: Auto-registration with defaults ──
        System.out.println("\n━━━ Scenario 6: Auto-registered unknown client ━━━");
        service.setDefaults(RateLimiterType.TOKEN_BUCKET,
            new RateLimiterConfig(5, 1000, 5, 5.0));
        System.out.println("  Unknown client first request: "
            + (service.allowRequest("new-user") ? "✅ ALLOWED (auto-created)" : "❌ DENIED"));
        System.out.println("  Algorithm: " + service.getAlgorithm("new-user"));

        System.out.println("\n✅ All rate limiter scenarios complete.");
    }
}
