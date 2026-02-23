import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/**
 * CIRCUIT BREAKER - System Design Implementation
 * 
 * States:
 * - CLOSED: Normal operation. Requests pass through. Track failures.
 * - OPEN: Failures exceeded threshold. Requests fail fast (no call to service).
 * - HALF_OPEN: After timeout, allow a test request. If succeeds -> CLOSED, fails -> OPEN.
 * 
 * Interview talking points:
 * - Used by: Netflix Hystrix, Resilience4j, AWS
 * - Prevents cascading failures in microservices
 * - Fail fast instead of waiting for timeout
 * - Combined with: Retry, Timeout, Bulkhead, Fallback
 * - Metrics: failure rate, response time, circuit state
 * - Sliding window: count-based or time-based
 */
class CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    // ==================== BASIC CIRCUIT BREAKER ====================
    static class BasicCircuitBreaker {
        private volatile State state = State.CLOSED;
        private final int failureThreshold;
        private final long openTimeoutMs;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private volatile long lastFailureTime = 0;
        private final String name;

        // Metrics
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong totalFailures = new AtomicLong(0);
        private final AtomicLong totalShortCircuited = new AtomicLong(0);

        BasicCircuitBreaker(String name, int failureThreshold, long openTimeoutMs) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.openTimeoutMs = openTimeoutMs;
        }

        <T> T execute(Supplier<T> action, Supplier<T> fallback) {
            totalCalls.incrementAndGet();

            if (state == State.OPEN) {
                if (System.currentTimeMillis() - lastFailureTime >= openTimeoutMs) {
                    state = State.HALF_OPEN;
                } else {
                    totalShortCircuited.incrementAndGet();
                    return fallback.get();
                }
            }

            try {
                T result = action.get();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                return fallback.get();
            }
        }

        private void onSuccess() {
            successCount.incrementAndGet();
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                failureCount.set(0);
            }
        }

        private void onFailure() {
            totalFailures.incrementAndGet();
            failureCount.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
            if (failureCount.get() >= failureThreshold) {
                state = State.OPEN;
            }
        }

        State getState() { return state; }
        String getName() { return name; }
        long getTotalCalls() { return totalCalls.get(); }
        long getTotalFailures() { return totalFailures.get(); }
        long getShortCircuited() { return totalShortCircuited.get(); }
        double failureRate() {
            long total = totalCalls.get() - totalShortCircuited.get();
            return total == 0 ? 0 : (double) totalFailures.get() / total;
        }
    }

    // ==================== SLIDING WINDOW CIRCUIT BREAKER ====================
    static class SlidingWindowCircuitBreaker {
        private volatile State state = State.CLOSED;
        private final int windowSize;
        private final double failureRateThreshold;
        private final long openTimeoutMs;
        private final int halfOpenPermits;
        private final ConcurrentLinkedDeque<Boolean> results = new ConcurrentLinkedDeque<>();
        private volatile long openedAt = 0;
        private final AtomicInteger halfOpenAttempts = new AtomicInteger(0);
        private final String name;

        SlidingWindowCircuitBreaker(String name, int windowSize, double failureRateThreshold,
                                     long openTimeoutMs, int halfOpenPermits) {
            this.name = name;
            this.windowSize = windowSize;
            this.failureRateThreshold = failureRateThreshold;
            this.openTimeoutMs = openTimeoutMs;
            this.halfOpenPermits = halfOpenPermits;
        }

        <T> T execute(Supplier<T> action, Supplier<T> fallback) {
            if (!allowRequest()) return fallback.get();

            try {
                T result = action.get();
                recordResult(true);
                return result;
            } catch (Exception e) {
                recordResult(false);
                return fallback.get();
            }
        }

        private boolean allowRequest() {
            switch (state) {
                case CLOSED: return true;
                case OPEN:
                    if (System.currentTimeMillis() - openedAt >= openTimeoutMs) {
                        state = State.HALF_OPEN;
                        halfOpenAttempts.set(0);
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    return halfOpenAttempts.incrementAndGet() <= halfOpenPermits;
                default: return false;
            }
        }

        private synchronized void recordResult(boolean success) {
            results.addLast(success);
            while (results.size() > windowSize) results.pollFirst();

            if (state == State.HALF_OPEN) {
                if (success) {
                    state = State.CLOSED;
                    results.clear();
                } else {
                    state = State.OPEN;
                    openedAt = System.currentTimeMillis();
                }
                return;
            }

            if (results.size() >= windowSize) {
                long failures = results.stream().filter(r -> !r).count();
                double rate = (double) failures / results.size();
                if (rate >= failureRateThreshold) {
                    state = State.OPEN;
                    openedAt = System.currentTimeMillis();
                }
            }
        }

        State getState() { return state; }
        double getCurrentFailureRate() {
            if (results.isEmpty()) return 0;
            long failures = results.stream().filter(r -> !r).count();
            return (double) failures / results.size();
        }
    }

    // ==================== CIRCUIT BREAKER REGISTRY ====================
    static class CircuitBreakerRegistry {
        private final Map<String, BasicCircuitBreaker> breakers = new ConcurrentHashMap<>();

        BasicCircuitBreaker getOrCreate(String name, int threshold, long timeout) {
            return breakers.computeIfAbsent(name,
                    k -> new BasicCircuitBreaker(name, threshold, timeout));
        }

        Map<String, State> getAllStates() {
            Map<String, State> states = new HashMap<>();
            breakers.forEach((name, cb) -> states.put(name, cb.getState()));
            return states;
        }

        void printDashboard() {
            System.out.println("  Circuit Breaker Dashboard:");
            for (Map.Entry<String, BasicCircuitBreaker> e : breakers.entrySet()) {
                BasicCircuitBreaker cb = e.getValue();
                System.out.printf("    %-20s | State: %-9s | Calls: %d | Failures: %d | Short-circuited: %d%n",
                        cb.getName(), cb.getState(), cb.getTotalCalls(),
                        cb.getTotalFailures(), cb.getShortCircuited());
            }
        }
    }

    // ==================== SIMULATED SERVICE ====================
    static class UnstableService {
        private double failureRate;
        private final Random random = new Random();

        UnstableService(double failureRate) { this.failureRate = failureRate; }

        String call() {
            if (random.nextDouble() < failureRate) {
                throw new RuntimeException("Service unavailable");
            }
            return "SUCCESS";
        }

        void setFailureRate(double rate) { this.failureRate = rate; }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== CIRCUIT BREAKER - System Design Demo ===\n");

        // 1. Basic Circuit Breaker
        System.out.println("--- 1. Basic Circuit Breaker ---");
        BasicCircuitBreaker cb = new BasicCircuitBreaker("payment-service", 3, 2000);
        UnstableService service = new UnstableService(0.0);

        // Normal operation
        for (int i = 0; i < 3; i++) {
            String result = cb.execute(() -> service.call(), () -> "FALLBACK");
            System.out.printf("  Call %d: %s (state: %s)%n", i + 1, result, cb.getState());
        }

        // Make service fail
        service.setFailureRate(1.0);
        System.out.println("  [Service starts failing]");
        for (int i = 0; i < 5; i++) {
            String result = cb.execute(() -> service.call(), () -> "FALLBACK");
            System.out.printf("  Call %d: %s (state: %s, failures: %d)%n",
                    i + 4, result, cb.getState(), cb.getTotalFailures());
        }

        // Wait for timeout, then half-open
        System.out.println("  [Waiting 2s for circuit to try half-open...]");
        Thread.sleep(2100);
        service.setFailureRate(0.0); // Service recovered
        String result = cb.execute(() -> service.call(), () -> "FALLBACK");
        System.out.printf("  After timeout: %s (state: %s)%n", result, cb.getState());

        // 2. Sliding Window
        System.out.println("\n--- 2. Sliding Window Circuit Breaker (window=10, threshold=50%%) ---");
        SlidingWindowCircuitBreaker swcb = new SlidingWindowCircuitBreaker(
                "order-service", 10, 0.5, 1000, 3);
        UnstableService svc2 = new UnstableService(0.0);

        // 7 successes, then 5 failures
        for (int i = 0; i < 7; i++) swcb.execute(() -> svc2.call(), () -> "FALLBACK");
        System.out.printf("  After 7 successes: state=%s, failRate=%.0f%%%n",
                swcb.getState(), swcb.getCurrentFailureRate() * 100);

        svc2.setFailureRate(1.0);
        for (int i = 0; i < 5; i++) swcb.execute(() -> svc2.call(), () -> "FALLBACK");
        System.out.printf("  After 5 failures:  state=%s, failRate=%.0f%%%n",
                swcb.getState(), swcb.getCurrentFailureRate() * 100);

        // 3. Circuit Breaker Registry
        System.out.println("\n--- 3. Circuit Breaker Registry (microservice dashboard) ---");
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry();
        BasicCircuitBreaker cbPayment = registry.getOrCreate("payment-svc", 3, 5000);
        BasicCircuitBreaker cbInventory = registry.getOrCreate("inventory-svc", 5, 3000);
        BasicCircuitBreaker cbNotification = registry.getOrCreate("notification-svc", 2, 10000);

        // Simulate some calls
        for (int i = 0; i < 10; i++) cbPayment.execute(() -> "ok", () -> "fallback");
        // Make inventory fail
        for (int i = 0; i < 5; i++) {
            cbInventory.execute(() -> { throw new RuntimeException("fail"); }, () -> "fallback");
        }
        for (int i = 0; i < 3; i++) cbNotification.execute(() -> "ok", () -> "fallback");

        registry.printDashboard();

        // 4. State transitions
        System.out.println("\n--- 4. State Transition Summary ---");
        System.out.println("  CLOSED --[failure threshold exceeded]--> OPEN");
        System.out.println("  OPEN   --[timeout elapsed]-------------> HALF_OPEN");
        System.out.println("  HALF_OPEN --[test request succeeds]----> CLOSED");
        System.out.println("  HALF_OPEN --[test request fails]-------> OPEN");

        System.out.println("\n--- Related Patterns ---");
        System.out.println("  Retry:     Retry N times before marking as failure");
        System.out.println("  Timeout:   Don't wait forever for a response");
        System.out.println("  Bulkhead:  Isolate failures (thread pools, semaphores)");
        System.out.println("  Fallback:  Graceful degradation when circuit is open");
    }
}
