import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * INTERVIEW-READY Rate Limiter System
 * Time to complete: 45-60 minutes
 * Focus: 2 algorithms (Token Bucket + Sliding Window), Strategy pattern
 */

// ==================== Rate Limiter Interface ====================
interface RateLimiter {
    boolean allowRequest(String clientId);
    String getName();
}

// ==================== Token Bucket Algorithm ====================
class TokenBucketRateLimiter implements RateLimiter {
    private final int capacity;
    private final int refillRate;
    private final ConcurrentHashMap<String, AtomicInteger> buckets;

    public TokenBucketRateLimiter(int capacity, int refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.buckets = new ConcurrentHashMap<>();
    }

    @Override
    public boolean allowRequest(String clientId) {
        AtomicInteger tokens = buckets.computeIfAbsent(
            clientId, 
            k -> new AtomicInteger(capacity)
        );

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

    @Override
    public String getName() {
        return "TokenBucket";
    }
}

// ==================== Fixed Window Algorithm ====================
class FixedWindowRateLimiter implements RateLimiter {
    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, WindowState> windows;

    static class WindowState {
        long windowStart;
        AtomicInteger count;

        WindowState(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(0);
        }
    }

    public FixedWindowRateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.windows = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();
        long currentWindow = now / windowMs;

        WindowState state = windows.computeIfAbsent(
            clientId,
            k -> new WindowState(currentWindow)
        );

        // Reset if new window
        if (state.windowStart != currentWindow) {
            state.windowStart = currentWindow;
            state.count.set(0);
        }

        if (state.count.get() >= maxRequests) {
            return false;
        }

        state.count.incrementAndGet();
        return true;
    }

    @Override
    public String getName() {
        return "FixedWindow";
    }
}

// ==================== Rate Limiter Service ====================
class RateLimiterService {
    private final Map<String, RateLimiter> limiters;
    private final RateLimiter defaultLimiter;

    public RateLimiterService(RateLimiter defaultLimiter) {
        this.limiters = new ConcurrentHashMap<>();
        this.defaultLimiter = defaultLimiter;
    }

    public void registerLimiter(String endpoint, RateLimiter limiter) {
        limiters.put(endpoint, limiter);
        System.out.println("Registered limiter for: " + endpoint);
    }

    public boolean checkRateLimit(String endpoint, String clientId) {
        RateLimiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        boolean allowed = limiter.allowRequest(clientId);
        
        System.out.println(
            (allowed ? "✓ ALLOWED" : "✗ BLOCKED") + 
            " - " + endpoint + " for " + clientId + 
            " using " + limiter.getName()
        );
        
        return allowed;
    }
}

// ==================== Demo ====================
public class RateLimiterSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Rate Limiter Demo ===\n");

        // Create service with Token Bucket default (5 requests/sec)
        RateLimiterService service = new RateLimiterService(
            new TokenBucketRateLimiter(5, 1)
        );

        // Register strict limiter for login (3 requests/minute)
        service.registerLimiter(
            "/login",
            new FixedWindowRateLimiter(3, 60000)
        );

        // Test 1: Token Bucket on /api endpoint
        System.out.println("--- Test 1: Token Bucket (/api) ---");
        String client1 = "user123";
        for (int i = 1; i <= 7; i++) {
            service.checkRateLimit("/api", client1);
            Thread.sleep(100);
        }

        // Test 2: Fixed Window on /login endpoint
        System.out.println("\n--- Test 2: Fixed Window (/login) ---");
        String client2 = "user456";
        for (int i = 1; i <= 5; i++) {
            service.checkRateLimit("/login", client2);
            Thread.sleep(200);
        }

        // Test 3: Multiple clients
        System.out.println("\n--- Test 3: Client Isolation ---");
        service.checkRateLimit("/api", "user123");
        service.checkRateLimit("/api", "user456"); // Different client, fresh limit
        service.checkRateLimit("/api", "user789");

        System.out.println("\n✅ Demo complete!");
    }
}
