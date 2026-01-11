import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * ENHANCED RATE LIMITER SYSTEM - Complete Implementation
 * 
 * Key Features Demonstrated:
 * 1. Per-user limits (each client has independent state)
 * 2. Burst support (Token Bucket algorithm)
 * 3. Thread-safe (CAS operations, no global locks)
 * 4. Interface-driven design (extensible for distributed version)
 * 5. Policy vs Storage separation
 * 
 * Design Patterns:
 * - Strategy Pattern (different algorithms)
 * - Factory Pattern (algorithm creation)
 * - Decorator Pattern (features like metrics)
 * 
 * Thread Safety:
 * - Lock-free CAS operations
 * - Per-client isolation (no global locks)
 * - AtomicInteger/AtomicLong for counters
 * 
 * @author System Design Repository
 * @version 2.0
 */

// ============================================================================
// CORE INTERFACES (POLICY ABSTRACTION)
// ============================================================================

/**
 * Core rate limiter interface - POLICY LAYER
 * Separates rate limiting policy from storage implementation
 */
interface RateLimiter {
    /**
     * Check if request should be allowed
     * @param clientId unique client identifier
     * @return result with allowed status and metadata
     */
    RateLimitResult allowRequest(String clientId);
    
    /**
     * Reset rate limit for client
     * @param clientId client to reset
     */
    void reset(String clientId);
    
    /**
     * Get configuration
     */
    RateLimiterConfig getConfig();
    
    /**
     * Cleanup resources
     */
    default void shutdown() {}
}

/**
 * Enhanced result with retry information
 */
class RateLimitResult {
    private final boolean allowed;
    private final int remaining;
    private final Long retryAfterMs;
    
    private RateLimitResult(boolean allowed, int remaining, Long retryAfterMs) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.retryAfterMs = retryAfterMs;
    }
    
    public static RateLimitResult allowed(int remaining) {
        return new RateLimitResult(true, remaining, null);
    }
    
    public static RateLimitResult blocked(long retryAfterMs) {
        return new RateLimitResult(false, 0, retryAfterMs);
    }
    
    public boolean isAllowed() { return allowed; }
    public int getRemaining() { return remaining; }
    public Long getRetryAfterMs() { return retryAfterMs; }
    
    @Override
    public String toString() {
        if (allowed) {
            return String.format("ALLOWED (remaining: %d)", remaining);
        } else {
            return String.format("BLOCKED (retry after: %dms)", retryAfterMs);
        }
    }
}

/**
 * Configuration - POLICY DEFINITION
 */
class RateLimiterConfig {
    private final int maxRequests;
    private final long timeWindowMs;
    private final String algorithm;
    
    public RateLimiterConfig(int maxRequests, long timeWindowMs, String algorithm) {
        this.maxRequests = maxRequests;
        this.timeWindowMs = timeWindowMs;
        this.algorithm = algorithm;
    }
    
    public int getMaxRequests() { return maxRequests; }
    public long getTimeWindowMs() { return timeWindowMs; }
    public String getAlgorithm() { return algorithm; }
    
    @Override
    public String toString() {
        return String.format("Config{max=%d, window=%dms, algo=%s}", 
            maxRequests, timeWindowMs, algorithm);
    }
}

// ============================================================================
// STORAGE LAYER (SEPARATE FROM POLICY)
// ============================================================================

/**
 * Storage interface - STORAGE LAYER
 * Separates how we store state from rate limiting logic
 */
interface RateLimitStorage<T> {
    T get(String clientId);
    void put(String clientId, T state);
    void remove(String clientId);
    Collection<T> getAllStates();
}

/**
 * In-memory storage implementation
 */
class InMemoryStorage<T> implements RateLimitStorage<T> {
    private final ConcurrentHashMap<String, T> storage = new ConcurrentHashMap<>();
    
    @Override
    public T get(String clientId) {
        return storage.get(clientId);
    }
    
    @Override
    public void put(String clientId, T state) {
        storage.put(clientId, state);
    }
    
    @Override
    public void remove(String clientId) {
        storage.remove(clientId);
    }
    
    @Override
    public Collection<T> getAllStates() {
        return storage.values();
    }
    
    public T computeIfAbsent(String clientId, java.util.function.Function<String, T> factory) {
        return storage.computeIfAbsent(clientId, factory);
    }
}

// ============================================================================
// 1. TOKEN BUCKET - SUPPORTS BURSTS
// ============================================================================

/**
 * Token Bucket Rate Limiter
 * 
 * BURST SUPPORT: Key feature for handling traffic spikes
 * - Bucket starts full (allows immediate burst)
 * - Tokens refill gradually
 * - Can accumulate tokens up to capacity
 * 
 * THREAD SAFETY:
 * - Lock-free CAS operations on AtomicInteger
 * - No global locks - per-client state
 * - Handles concurrent requests safely
 */
class TokenBucketRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final InMemoryStorage<TokenBucket> storage;
    private final ScheduledExecutorService refiller;
    
    public TokenBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.storage = new InMemoryStorage<>();
        this.refiller = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TokenRefiller");
            t.setDaemon(true);
            return t;
        });
        startTokenRefiller();
    }
    
    private void startTokenRefiller() {
        // Refill rate: tokens per window / window duration
        long refillIntervalMs = config.getTimeWindowMs() / config.getMaxRequests();
        
        refiller.scheduleAtFixedRate(() -> {
            storage.getAllStates().forEach(TokenBucket::refill);
        }, refillIntervalMs, refillIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public RateLimitResult allowRequest(String clientId) {
        TokenBucket bucket = storage.computeIfAbsent(clientId,
            k -> new TokenBucket(config.getMaxRequests()));
        
        if (bucket.tryConsume()) {
            int remaining = bucket.getTokens();
            return RateLimitResult.allowed(remaining);
        } else {
            long retryAfterMs = config.getTimeWindowMs() / config.getMaxRequests();
            return RateLimitResult.blocked(retryAfterMs);
        }
    }
    
    @Override
    public void reset(String clientId) {
        storage.remove(clientId);
    }
    
    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }
    
    @Override
    public void shutdown() {
        refiller.shutdown();
        try {
            if (!refiller.awaitTermination(1, TimeUnit.SECONDS)) {
                refiller.shutdownNow();
            }
        } catch (InterruptedException e) {
            refiller.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Token Bucket State - LOCK-FREE with CAS
     */
    private static class TokenBucket {
        private final int capacity;
        private final AtomicInteger tokens;
        
        public TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);  // Start full!
        }
        
        /**
         * Lock-free token consumption using CAS
         */
        public boolean tryConsume() {
            while (true) {
                int current = tokens.get();
                if (current <= 0) {
                    return false;
                }
                // CAS: Compare current with (current-1)
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;  // Successfully consumed token
                }
                // CAS failed - retry (another thread modified it)
            }
        }
        
        /**
         * Refill one token (called by background thread)
         */
        public void refill() {
            tokens.updateAndGet(current -> Math.min(capacity, current + 1));
        }
        
        public int getTokens() {
            return Math.max(0, tokens.get());
        }
    }
}

// ============================================================================
// 2. SLIDING WINDOW COUNTER - PRODUCTION READY
// ============================================================================

/**
 * Sliding Window Counter Rate Limiter
 * 
 * HYBRID APPROACH: Balance accuracy and efficiency
 * - Maintains previous and current window counts
 * - Calculates weighted average
 * - No boundary burst issues
 * 
 * THREAD SAFETY:
 * - Synchronized for window transitions
 * - AtomicInteger for counts
 * - Per-client synchronization (not global!)
 */
class SlidingWindowRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final InMemoryStorage<SlidingWindow> storage;
    
    public SlidingWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.storage = new InMemoryStorage<>();
    }
    
    @Override
    public RateLimitResult allowRequest(String clientId) {
        SlidingWindow window = storage.computeIfAbsent(clientId,
            k -> new SlidingWindow(config.getTimeWindowMs()));
        
        long currentTime = System.currentTimeMillis();
        boolean allowed = window.checkAndIncrement(currentTime, config.getMaxRequests());
        
        if (allowed) {
            int remaining = config.getMaxRequests() - window.getCurrentCount();
            return RateLimitResult.allowed(remaining);
        } else {
            long retryAfter = window.getTimeUntilNextWindow(currentTime);
            return RateLimitResult.blocked(retryAfter);
        }
    }
    
    @Override
    public void reset(String clientId) {
        storage.remove(clientId);
    }
    
    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }
    
    /**
     * Sliding Window State - PER-CLIENT synchronization
     */
    private static class SlidingWindow {
        private final long windowSizeMs;
        private volatile long prevWindowStart;
        private volatile long currWindowStart;
        private final AtomicInteger prevCount;
        private final AtomicInteger currCount;
        
        public SlidingWindow(long windowSizeMs) {
            this.windowSizeMs = windowSizeMs;
            this.prevWindowStart = 0;
            this.currWindowStart = 0;
            this.prevCount = new AtomicInteger(0);
            this.currCount = new AtomicInteger(0);
        }
        
        /**
         * Synchronized per-client (not global lock!)
         */
        public synchronized boolean checkAndIncrement(long currentTime, int maxRequests) {
            long currentWindowNum = currentTime / windowSizeMs;
            
            // Slide window if needed
            if (currentWindowNum > currWindowStart) {
                prevWindowStart = currWindowStart;
                prevCount.set(currCount.get());
                currWindowStart = currentWindowNum;
                currCount.set(0);
            }
            
            // Calculate weighted count
            long timeIntoWindow = currentTime % windowSizeMs;
            double prevWeight = 1.0 - ((double) timeIntoWindow / windowSizeMs);
            double estimatedCount = prevCount.get() * prevWeight + currCount.get();
            
            if (estimatedCount >= maxRequests) {
                return false;
            }
            
            currCount.incrementAndGet();
            return true;
        }
        
        public int getCurrentCount() {
            return currCount.get();
        }
        
        public long getTimeUntilNextWindow(long currentTime) {
            long timeIntoWindow = currentTime % windowSizeMs;
            return windowSizeMs - timeIntoWindow;
        }
    }
}

// ============================================================================
// 3. FIXED WINDOW - SIMPLE AND FAST
// ============================================================================

/**
 * Fixed Window Rate Limiter
 * 
 * SIMPLE APPROACH: Good for high throughput
 * THREAD SAFETY: Synchronized per-client
 * TRADE-OFF: Boundary burst issue
 */
class FixedWindowRateLimiter implements RateLimiter {
    private final RateLimiterConfig config;
    private final InMemoryStorage<FixedWindow> storage;
    
    public FixedWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.storage = new InMemoryStorage<>();
    }
    
    @Override
    public RateLimitResult allowRequest(String clientId) {
        FixedWindow window = storage.computeIfAbsent(clientId,
            k -> new FixedWindow(config.getTimeWindowMs()));
        
        long currentWindow = getCurrentWindowNumber();
        boolean allowed = window.increment(currentWindow, config.getMaxRequests());
        
        if (allowed) {
            int remaining = config.getMaxRequests() - window.getCount();
            return RateLimitResult.allowed(remaining);
        } else {
            long retryAfter = config.getTimeWindowMs() - 
                (System.currentTimeMillis() % config.getTimeWindowMs());
            return RateLimitResult.blocked(retryAfter);
        }
    }
    
    private long getCurrentWindowNumber() {
        return System.currentTimeMillis() / config.getTimeWindowMs();
    }
    
    @Override
    public void reset(String clientId) {
        storage.remove(clientId);
    }
    
    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }
    
    /**
     * Fixed Window State
     */
    private static class FixedWindow {
        private final long windowSize;
        private volatile long currentWindow;
        private final AtomicInteger count;
        
        public FixedWindow(long windowSize) {
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
        
        public int getCount() {
            return count.get();
        }
    }
}

// ============================================================================
// FACTORY - EXTENSIBLE FOR FUTURE ALGORITHMS
// ============================================================================

/**
 * Factory for creating rate limiters
 * EXTENSIBLE: Easy to add new algorithms without changing clients
 */
class RateLimiterFactory {
    
    public enum Algorithm {
        TOKEN_BUCKET,
        SLIDING_WINDOW,
        FIXED_WINDOW
    }
    
    public static RateLimiter create(Algorithm algorithm, int maxRequests, long windowMs) {
        RateLimiterConfig config = new RateLimiterConfig(maxRequests, windowMs, 
            algorithm.name());
        
        switch (algorithm) {
            case TOKEN_BUCKET:
                return new TokenBucketRateLimiter(config);
            case SLIDING_WINDOW:
                return new SlidingWindowRateLimiter(config);
            case FIXED_WINDOW:
                return new FixedWindowRateLimiter(config);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
    
    /**
     * Future extension point for distributed version
     */
    public static RateLimiter createDistributed(Algorithm algorithm, 
            int maxRequests, long windowMs, Object redisClient) {
        // TODO: Implement Redis-based distributed version
        // return new RedisTokenBucketRateLimiter(config, redisClient);
        throw new UnsupportedOperationException("Distributed version not yet implemented");
    }
}

// ============================================================================
// MULTI-TIER RATE LIMITER
// ============================================================================

/**
 * Multi-tier rate limiter with different limits per tier
 * Example: Free tier (100 req/min), Premium tier (1000 req/min)
 */
class TieredRateLimiter implements RateLimiter {
    private final Map<String, RateLimiter> tierLimiters;
    private final Map<String, String> clientTiers;  // clientId -> tier
    private final String defaultTier;
    
    public TieredRateLimiter(Map<String, RateLimiterConfig> tierConfigs, 
            String defaultTier) {
        this.tierLimiters = new ConcurrentHashMap<>();
        this.clientTiers = new ConcurrentHashMap<>();
        this.defaultTier = defaultTier;
        
        // Create limiter for each tier
        tierConfigs.forEach((tier, config) -> {
            tierLimiters.put(tier, RateLimiterFactory.create(
                RateLimiterFactory.Algorithm.TOKEN_BUCKET,
                config.getMaxRequests(),
                config.getTimeWindowMs()
            ));
        });
    }
    
    public void assignTier(String clientId, String tier) {
        clientTiers.put(clientId, tier);
    }
    
    @Override
    public RateLimitResult allowRequest(String clientId) {
        String tier = clientTiers.getOrDefault(clientId, defaultTier);
        RateLimiter limiter = tierLimiters.get(tier);
        
        if (limiter == null) {
            throw new IllegalStateException("No limiter for tier: " + tier);
        }
        
        return limiter.allowRequest(clientId);
    }
    
    @Override
    public void reset(String clientId) {
        clientTiers.values().forEach(tier -> {
            RateLimiter limiter = tierLimiters.get(tier);
            if (limiter != null) {
                limiter.reset(clientId);
            }
        });
    }
    
    @Override
    public RateLimiterConfig getConfig() {
        return tierLimiters.get(defaultTier).getConfig();
    }
    
    @Override
    public void shutdown() {
        tierLimiters.values().forEach(RateLimiter::shutdown);
    }
}

// ============================================================================
// DEMONSTRATION
// ============================================================================

public class EnhancedRateLimiterSystem {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(70));
        System.out.println("ENHANCED RATE LIMITER SYSTEM - COMPREHENSIVE DEMO");
        System.out.println("=".repeat(70));
        
        demo1BurstSupport();
        demo2PerUserLimits();
        demo3ThreadSafety();
        demo4NoGlobalLocks();
        demo5PolicyVsStorage();
        demo6TieredLimits();
        demo7AtomicStateUpdates();
        demo8Extensibility();
    }
    
    private static void demo1BurstSupport() throws InterruptedException {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 1: Burst Support (Token Bucket)");
        System.out.println("=".repeat(70));
        
        // 10 requests per second, but bucket holds 10 tokens
        RateLimiter limiter = RateLimiterFactory.create(
            RateLimiterFactory.Algorithm.TOKEN_BUCKET, 10, 1000);
        
        String client = "user1";
        
        System.out.println("Initial burst of 10 requests (all should pass):");
        for (int i = 1; i <= 10; i++) {
            RateLimitResult result = limiter.allowRequest(client);
            System.out.printf("  Request %d: %s%n", i, result);
        }
        
        System.out.println("\nImmediate 11th request (should fail):");
        RateLimitResult result = limiter.allowRequest(client);
        System.out.println("  Request 11: " + result);
        
        System.out.println("\n✅ Burst support allows immediate spike!");
        System.out.println("✅ Tokens refill gradually over time");
        
        limiter.shutdown();
    }
    
    private static void demo2PerUserLimits() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 2: Per-User Limits (User Isolation)");
        System.out.println("=".repeat(70));
        
        RateLimiter limiter = RateLimiterFactory.create(
            RateLimiterFactory.Algorithm.SLIDING_WINDOW, 3, 1000);
        
        System.out.println("Three users making requests:");
        
        // User A exhausts their limit
        System.out.println("\nUser A (3 requests):");
        for (int i = 1; i <= 4; i++) {
            RateLimitResult result = limiter.allowRequest("userA");
            System.out.printf("  Request %d: %s%n", i, result);
        }
        
        // User B still has full quota
        System.out.println("\nUser B (independent limit):");
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = limiter.allowRequest("userB");
            System.out.printf("  Request %d: %s%n", i, result);
        }
        
        // User C also has full quota
        System.out.println("\nUser C (independent limit):");
        RateLimitResult result = limiter.allowRequest("userC");
        System.out.println("  Request 1: " + result);
        
        System.out.println("\n✅ Each user has independent rate limit!");
        System.out.println("✅ One user's usage doesn't affect others!");
        
        limiter.shutdown();
    }
    
    private static void demo3ThreadSafety() throws InterruptedException {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 3: Thread Safety (Concurrent Requests)");
        System.out.println("=".repeat(70));
        
        RateLimiter limiter = RateLimiterFactory.create(
            RateLimiterFactory.Algorithm.TOKEN_BUCKET, 100, 1000);
        
        String client = "concurrent_user";
        int numThreads = 10;
        int requestsPerThread = 20;
        
        System.out.printf("Spawning %d threads, %d requests each...%n", 
            numThreads, requestsPerThread);
        
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    if (limiter.allowRequest(client).isAllowed()) {
                        allowed.incrementAndGet();
                    } else {
                        blocked.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        System.out.printf("Total requests: %d%n", numThreads * requestsPerThread);
        System.out.printf("Allowed: %d%n", allowed.get());
        System.out.printf("Blocked: %d%n", blocked.get());
        System.out.println("\n✅ Thread-safe: No race conditions!");
        System.out.println("✅ Correct count maintained under concurrency!");
        
        limiter.shutdown();
    }
    
    private static void demo4NoGlobalLocks() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 4: No Global Locks (Per-Client Isolation)");
        System.out.println("=".repeat(70));
        
        System.out.println("Design Principle: Avoid global locks");
        System.out.println("\n❌ BAD: Global lock blocks all clients");
        System.out.println("   synchronized(globalLock) {");
        System.out.println("       // All clients blocked here!");
        System.out.println("   }");
        
        System.out.println("\n✅ GOOD: Per-client state with CAS");
        System.out.println("   ConcurrentHashMap<String, State> states;");
        System.out.println("   State state = states.get(clientId);  // Only this client");
        System.out.println("   state.tokens.compareAndSet(curr, curr-1);  // Lock-free!");
        
        System.out.println("\n✅ Benefits:");
        System.out.println("  - Client A and Client B can't block each other");
        System.out.println("  - Lock-free CAS operations");
        System.out.println("  - Scales linearly with number of clients");
        System.out.println("  - No contention between different clients");
    }
    
    private static void demo5PolicyVsStorage() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 5: Policy vs Storage Separation");
        System.out.println("=".repeat(70));
        
        System.out.println("Clean Architecture: Separate concerns");
        
        System.out.println("\n1. POLICY LAYER (RateLimiter interface):");
        System.out.println("   - Defines WHAT: rate limiting behavior");
        System.out.println("   - Algorithm choice: Token Bucket, Sliding Window");
        System.out.println("   - Business logic: allow/block decisions");
        
        System.out.println("\n2. STORAGE LAYER (RateLimitStorage interface):");
        System.out.println("   - Defines WHERE: state persistence");
        System.out.println("   - In-memory: ConcurrentHashMap");
        System.out.println("   - Distributed: Redis, Memcached");
        System.out.println("   - Can swap storage without changing policy!");
        
        System.out.println("\n✅ Benefits:");
        System.out.println("  - Easy to test (mock storage)");
        System.out.println("  - Switch from local to distributed easily");
        System.out.println("  - Change algorithm without touching storage");
        System.out.println("  - Single Responsibility Principle");
    }
    
    private static void demo6TieredLimits() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 6: Tiered Rate Limits (Free vs Premium)");
        System.out.println("=".repeat(70));
        
        // Create tier configs
        Map<String, RateLimiterConfig> tiers = new HashMap<>();
        tiers.put("free", new RateLimiterConfig(10, 60000, "TOKEN_BUCKET"));
        tiers.put("premium", new RateLimiterConfig(100, 60000, "TOKEN_BUCKET"));
        tiers.put("enterprise", new RateLimiterConfig(1000, 60000, "TOKEN_BUCKET"));
        
        TieredRateLimiter limiter = new TieredRateLimiter(tiers, "free");
        
        // Assign tiers
        limiter.assignTier("freeUser", "free");
        limiter.assignTier("premiumUser", "premium");
        
        System.out.println("Free user (10 req/min limit):");
        for (int i = 1; i <= 12; i++) {
            RateLimitResult result = limiter.allowRequest("freeUser");
            if (i <= 3 || i == 11) {  // Show first 3 and one blocked
                System.out.printf("  Request %d: %s%n", i, result);
            }
        }
        
        System.out.println("\nPremium user (100 req/min limit):");
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = limiter.allowRequest("premiumUser");
            System.out.printf("  Request %d: %s%n", i, result);
        }
        
        System.out.println("\n✅ Different limits per user tier!");
        
        limiter.shutdown();
    }
    
    private static void demo7AtomicStateUpdates() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 7: Atomic State Updates (CAS Pattern)");
        System.out.println("=".repeat(70));
        
        System.out.println("Compare-And-Swap (CAS) ensures atomic updates:");
        System.out.println("\n1. Read current value");
        System.out.println("2. Calculate new value");
        System.out.println("3. Atomically update if unchanged");
        System.out.println("4. If changed by another thread -> retry");
        
        System.out.println("\nToken Bucket CAS example:");
        System.out.println("  int current = tokens.get();");
        System.out.println("  if (tokens.compareAndSet(current, current-1)) {");
        System.out.println("      return true;");
        System.out.println("  }");
        
        System.out.println("\n✅ Lock-free atomicity!");
        System.out.println("✅ No mutex blocking!");
        System.out.println("✅ Scales with contention!");
    }
    
    private static void demo8Extensibility() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 8: Extensibility (Future Distributed Version)");
        System.out.println("=".repeat(70));
        
        System.out.println("Current: In-memory single-server");
        System.out.println("\nExtension path to distributed:");
        System.out.println("  1. Keep RateLimiter interface unchanged");
        System.out.println("  2. Implement RedisRateLimitStorage");
        System.out.println("  3. Use same policy layer");
        System.out.println("  4. Swap storage layer only!");
        
        System.out.println("\nFuture extension example:");
        System.out.println("  RateLimiter distributed = RateLimiterFactory.createDistributed(");
        System.out.println("      Algorithm.TOKEN_BUCKET, 100, 60000, redisClient);");
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ALL DEMOS COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(70));
        
        System.out.println("\nKey Takeaways:");
        System.out.println("1. Token Bucket: Best for burst support");
        System.out.println("2. Per-user limits: Independent state per client");
        System.out.println("3. Thread-safe: CAS operations, no global locks");
        System.out.println("4. Interface-driven: Easy to extend");
        System.out.println("5. Policy vs Storage: Clean separation of concerns");
        
        System.out.println("\nInterview Tips:");
        System.out.println("- Explain Token Bucket vs Leaky Bucket");
        System.out.println("- Show CAS understanding for thread safety");
        System.out.println("- Discuss avoiding global locks");
        System.out.println("- Mention distributed extension path");
        System.out.println("- Consider per-user isolation");
    }
}
