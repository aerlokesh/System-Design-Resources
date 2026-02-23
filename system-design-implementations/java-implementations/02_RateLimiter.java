import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * RATE LIMITER - System Design Implementation
 * 
 * Four algorithms implemented:
 * 1. Token Bucket - Tokens refill at fixed rate, burst allowed
 * 2. Sliding Window Log - Exact count using timestamps
 * 3. Fixed Window Counter - Simple counter per time window
 * 4. Leaky Bucket - Constant output rate, queue overflow
 * 
 * Interview talking points:
 * - Token Bucket: Used by AWS API Gateway, Stripe
 * - Sliding Window: Most accurate but memory-heavy
 * - Fixed Window: Simple but boundary burst issue
 * - Leaky Bucket: Smooths traffic, used in networking
 * - Distributed: Use Redis INCR + EXPIRE for atomic operations
 * - Return 429 Too Many Requests with Retry-After header
 * - Rate limit by: IP, User ID, API key, endpoint
 */
class RateLimiter {

    // ==================== 1. TOKEN BUCKET ====================
    /**
     * Tokens are added at a fixed rate. Each request consumes one token.
     * If bucket is empty, request is rejected.
     * Allows bursts up to bucket capacity.
     * 
     * Used by: AWS, Stripe, most API gateways
     */
    static class TokenBucket {
        private final int capacity;           // Max tokens
        private final double refillRate;      // Tokens per second
        private double tokens;
        private long lastRefillTime;

        TokenBucket(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTime = System.nanoTime();
        }

        synchronized boolean allowRequest() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        synchronized boolean allowRequest(int cost) {
            refill();
            if (tokens >= cost) {
                tokens -= cost;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillTime) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsed * refillRate);
            lastRefillTime = now;
        }

        synchronized double getAvailableTokens() {
            refill();
            return tokens;
        }
    }

    // ==================== 2. SLIDING WINDOW LOG ====================
    /**
     * Stores timestamp of each request. Counts requests in the sliding window.
     * Most accurate but uses more memory (stores all timestamps).
     * 
     * In production: Use Redis sorted sets with ZRANGEBYSCORE
     */
    static class SlidingWindowLog {
        private final int maxRequests;
        private final long windowSizeMs;
        private final ConcurrentLinkedDeque<Long> requestLog = new ConcurrentLinkedDeque<>();

        SlidingWindowLog(int maxRequests, long windowSizeMs) {
            this.maxRequests = maxRequests;
            this.windowSizeMs = windowSizeMs;
        }

        synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();
            long windowStart = now - windowSizeMs;

            // Remove expired entries
            while (!requestLog.isEmpty() && requestLog.peekFirst() <= windowStart) {
                requestLog.pollFirst();
            }

            if (requestLog.size() < maxRequests) {
                requestLog.addLast(now);
                return true;
            }
            return false;
        }

        synchronized int getCurrentCount() {
            long windowStart = System.currentTimeMillis() - windowSizeMs;
            while (!requestLog.isEmpty() && requestLog.peekFirst() <= windowStart) {
                requestLog.pollFirst();
            }
            return requestLog.size();
        }
    }

    // ==================== 3. FIXED WINDOW COUNTER ====================
    /**
     * Divides time into fixed windows. Counter resets at window boundary.
     * Simple but allows 2x burst at window boundaries.
     * 
     * Example: 100 req/min window, 100 requests at :59, 100 at :00 = 200 in 2 seconds
     */
    static class FixedWindowCounter {
        private final int maxRequests;
        private final long windowSizeMs;
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long windowStart;

        FixedWindowCounter(int maxRequests, long windowSizeMs) {
            this.maxRequests = maxRequests;
            this.windowSizeMs = windowSizeMs;
            this.windowStart = System.currentTimeMillis();
        }

        boolean allowRequest() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowSizeMs) {
                synchronized (this) {
                    if (now - windowStart >= windowSizeMs) {
                        counter.set(0);
                        windowStart = now;
                    }
                }
            }
            return counter.incrementAndGet() <= maxRequests;
        }

        int getCurrentCount() { return counter.get(); }
    }

    // ==================== 4. LEAKY BUCKET ====================
    /**
     * Requests enter a queue (bucket). Processed at a fixed rate.
     * If queue is full, request is dropped.
     * Smooths out bursty traffic to a constant rate.
     * 
     * Used in: Network traffic shaping, message processing
     */
    static class LeakyBucket {
        private final int capacity;
        private final double leakRate;  // requests per second
        private final LinkedBlockingQueue<String> queue;
        private volatile boolean running = true;

        LeakyBucket(int capacity, double leakRate) {
            this.capacity = capacity;
            this.leakRate = leakRate;
            this.queue = new LinkedBlockingQueue<>(capacity);

            // Background thread to "leak" (process) requests
            Thread leaker = new Thread(() -> {
                while (running) {
                    try {
                        String req = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (req != null) {
                            // Process request (simulated)
                            Thread.sleep((long) (1000.0 / leakRate));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            leaker.setDaemon(true);
            leaker.start();
        }

        boolean allowRequest(String requestId) {
            return queue.offer(requestId);
        }

        int getQueueSize() { return queue.size(); }
        void stop() { running = false; }
    }

    // ==================== 5. PER-USER RATE LIMITER ====================
    /**
     * Manages rate limiters per user/client.
     * Each user gets their own Token Bucket.
     * 
     * In production: Store in Redis with user-specific keys
     */
    static class PerUserRateLimiter {
        private final int capacity;
        private final double refillRate;
        private final ConcurrentHashMap<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();

        PerUserRateLimiter(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
        }

        boolean allowRequest(String userId) {
            TokenBucket bucket = userBuckets.computeIfAbsent(userId,
                    k -> new TokenBucket(capacity, refillRate));
            return bucket.allowRequest();
        }

        double getTokens(String userId) {
            TokenBucket bucket = userBuckets.get(userId);
            return bucket != null ? bucket.getAvailableTokens() : capacity;
        }
    }

    // ==================== 6. TIERED RATE LIMITER ====================
    /**
     * Different rate limits based on user tier/plan.
     * Free: 10 req/min, Pro: 100 req/min, Enterprise: 1000 req/min
     */
    static class TieredRateLimiter {
        enum Tier {
            FREE(10, 0.17),          // 10 req/min
            PRO(100, 1.67),          // 100 req/min
            ENTERPRISE(1000, 16.67); // 1000 req/min

            final int capacity;
            final double refillRate;
            Tier(int capacity, double refillRate) {
                this.capacity = capacity;
                this.refillRate = refillRate;
            }
        }

        private final ConcurrentHashMap<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Tier> userTiers = new ConcurrentHashMap<>();

        void setTier(String userId, Tier tier) {
            userTiers.put(userId, tier);
            userBuckets.put(userId, new TokenBucket(tier.capacity, tier.refillRate));
        }

        boolean allowRequest(String userId) {
            Tier tier = userTiers.getOrDefault(userId, Tier.FREE);
            TokenBucket bucket = userBuckets.computeIfAbsent(userId,
                    k -> new TokenBucket(tier.capacity, tier.refillRate));
            return bucket.allowRequest();
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== RATE LIMITER - System Design Demo ===\n");

        // 1. Token Bucket
        System.out.println("--- 1. Token Bucket (capacity=5, refill=2/sec) ---");
        TokenBucket tb = new TokenBucket(5, 2);
        for (int i = 1; i <= 8; i++) {
            boolean allowed = tb.allowRequest();
            System.out.printf("  Request %d: %s (tokens: %.1f)%n", i, allowed ? "ALLOWED" : "REJECTED", tb.getAvailableTokens());
        }
        System.out.println("  Waiting 2 seconds for refill...");
        Thread.sleep(2000);
        System.out.printf("  After wait - tokens: %.1f%n", tb.getAvailableTokens());
        System.out.printf("  Request 9: %s%n", tb.allowRequest() ? "ALLOWED" : "REJECTED");

        // 2. Sliding Window Log
        System.out.println("\n--- 2. Sliding Window Log (max=3, window=1sec) ---");
        SlidingWindowLog swl = new SlidingWindowLog(3, 1000);
        for (int i = 1; i <= 5; i++) {
            boolean allowed = swl.allowRequest();
            System.out.printf("  Request %d: %s (count: %d)%n", i, allowed ? "ALLOWED" : "REJECTED", swl.getCurrentCount());
        }
        System.out.println("  Waiting 1.1 seconds...");
        Thread.sleep(1100);
        System.out.printf("  After wait - Request: %s (count: %d)%n",
                swl.allowRequest() ? "ALLOWED" : "REJECTED", swl.getCurrentCount());

        // 3. Fixed Window Counter
        System.out.println("\n--- 3. Fixed Window Counter (max=3, window=1sec) ---");
        FixedWindowCounter fwc = new FixedWindowCounter(3, 1000);
        for (int i = 1; i <= 5; i++) {
            boolean allowed = fwc.allowRequest();
            System.out.printf("  Request %d: %s (count: %d)%n", i, allowed ? "ALLOWED" : "REJECTED", fwc.getCurrentCount());
        }
        System.out.println("  Waiting for window reset...");
        Thread.sleep(1100);
        System.out.printf("  After reset - Request: %s (count: %d)%n",
                fwc.allowRequest() ? "ALLOWED" : "REJECTED", fwc.getCurrentCount());

        // 4. Leaky Bucket
        System.out.println("\n--- 4. Leaky Bucket (capacity=3, leak=2/sec) ---");
        LeakyBucket lb = new LeakyBucket(3, 2);
        for (int i = 1; i <= 6; i++) {
            boolean allowed = lb.allowRequest("req-" + i);
            System.out.printf("  Request %d: %s (queue: %d)%n", i, allowed ? "QUEUED" : "DROPPED", lb.getQueueSize());
        }
        Thread.sleep(2000);
        System.out.printf("  After 2s processing: queue size = %d%n", lb.getQueueSize());
        lb.stop();

        // 5. Per-User
        System.out.println("\n--- 5. Per-User Rate Limiter (5 tokens, 1/sec refill) ---");
        PerUserRateLimiter purl = new PerUserRateLimiter(5, 1);
        String[] users = {"alice", "bob"};
        for (int i = 0; i < 7; i++) {
            for (String user : users) {
                boolean allowed = purl.allowRequest(user);
                if (i < 3 || i >= 5)
                    System.out.printf("  %s request %d: %s%n", user, i + 1, allowed ? "ALLOWED" : "REJECTED");
            }
            if (i == 3) System.out.println("  ... (skipping middle requests)");
        }

        // 6. Tiered
        System.out.println("\n--- 6. Tiered Rate Limiter ---");
        TieredRateLimiter trl = new TieredRateLimiter();
        trl.setTier("free_user", TieredRateLimiter.Tier.FREE);
        trl.setTier("pro_user", TieredRateLimiter.Tier.PRO);
        trl.setTier("ent_user", TieredRateLimiter.Tier.ENTERPRISE);

        int freeAllowed = 0, proAllowed = 0, entAllowed = 0;
        for (int i = 0; i < 20; i++) {
            if (trl.allowRequest("free_user")) freeAllowed++;
            if (trl.allowRequest("pro_user")) proAllowed++;
            if (trl.allowRequest("ent_user")) entAllowed++;
        }
        System.out.printf("  Free user:       %d/20 allowed (limit: 10)%n", freeAllowed);
        System.out.printf("  Pro user:        %d/20 allowed (limit: 100)%n", proAllowed);
        System.out.printf("  Enterprise user: %d/20 allowed (limit: 1000)%n", entAllowed);

        System.out.println("\n--- Algorithm Comparison ---");
        System.out.println("  Token Bucket:     Best for bursty traffic, simple, memory efficient");
        System.out.println("  Sliding Window:   Most accurate, higher memory (stores timestamps)");
        System.out.println("  Fixed Window:     Simplest, but boundary burst problem");
        System.out.println("  Leaky Bucket:     Smooth output rate, good for downstream protection");
    }
}
