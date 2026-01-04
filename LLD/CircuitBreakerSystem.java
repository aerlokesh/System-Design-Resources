/**
 * CIRCUIT BREAKER - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a resilience pattern for managing
 * API calls to external dependencies with automatic failure detection
 * and recovery.
 * 
 * Key Features:
 * 1. Three-state machine (CLOSED, OPEN, HALF_OPEN)
 * 2. Sliding window failure detection
 * 3. Automatic state transitions
 * 4. Thread-safe with atomic operations
 * 5. Fallback mechanism for graceful degradation
 * 6. Comprehensive metrics and monitoring
 * 7. Configurable thresholds and timeouts
 * 
 * Design Patterns Used:
 * - State Pattern (CircuitBreakerState)
 * - Strategy Pattern (Fallback strategies)
 * - Builder Pattern (Configuration)
 * - Observer Pattern (Metrics tracking)
 * - Proxy Pattern (Wrapping external calls)
 * 
 * Company: Microsoft (Senior Level Interview Question)
 * Real-world libraries: Netflix Hystrix, Resilience4j
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

// ============================================================
// ENUMS AND EXCEPTIONS
// ============================================================

enum CircuitBreakerState {
    CLOSED,      // Normal operation
    OPEN,        // Failing, reject all
    HALF_OPEN    // Testing recovery
}

class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}

// ============================================================
// CONFIGURATION
// ============================================================

/**
 * Configuration for circuit breaker behavior
 * Uses Builder pattern for easy construction
 */
class CircuitBreakerConfig {
    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutDurationMs;
    private final long slidingWindowMs;
    private final int halfOpenMaxCalls;
    
    private CircuitBreakerConfig(int failureThreshold, int successThreshold,
                                 long timeoutDurationMs, long slidingWindowMs,
                                 int halfOpenMaxCalls) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutDurationMs = timeoutDurationMs;
        this.slidingWindowMs = slidingWindowMs;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
    }
    
    public static class Builder {
        private int failureThreshold = 5;
        private int successThreshold = 2;
        private long timeoutDurationMs = 60000;
        private long slidingWindowMs = 10000;
        private int halfOpenMaxCalls = 3;
        
        public Builder failureThreshold(int val) {
            this.failureThreshold = val;
            return this;
        }
        
        public Builder successThreshold(int val) {
            this.successThreshold = val;
            return this;
        }
        
        public Builder timeoutDuration(long ms) {
            this.timeoutDurationMs = ms;
            return this;
        }
        
        public Builder slidingWindow(long ms) {
            this.slidingWindowMs = ms;
            return this;
        }
        
        public Builder halfOpenMaxCalls(int val) {
            this.halfOpenMaxCalls = val;
            return this;
        }
        
        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(failureThreshold, successThreshold,
                                           timeoutDurationMs, slidingWindowMs,
                                           halfOpenMaxCalls);
        }
    }
    
    public int getFailureThreshold() { return failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public long getTimeoutDurationMs() { return timeoutDurationMs; }
    public long getSlidingWindowMs() { return slidingWindowMs; }
    public int getHalfOpenMaxCalls() { return halfOpenMaxCalls; }
}

// ============================================================
// METRICS
// ============================================================

class StateTransitionEvent {
    private final CircuitBreakerState fromState;
    private final CircuitBreakerState toState;
    private final long timestamp;
    
    public StateTransitionEvent(CircuitBreakerState from, CircuitBreakerState to, long timestamp) {
        this.fromState = from;
        this.toState = to;
        this.timestamp = timestamp;
    }
    
    public CircuitBreakerState getFromState() { return fromState; }
    public CircuitBreakerState getToState() { return toState; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("%s → %s at %d", fromState, toState, timestamp);
    }
}

/**
 * Tracks circuit breaker metrics
 */
class CircuitBreakerMetrics {
    private final AtomicLong totalRequests;
    private final AtomicLong successfulRequests;
    private final AtomicLong failedRequests;
    private final AtomicLong rejectedRequests;
    private final AtomicInteger stateTransitions;
    private final List<StateTransitionEvent> transitionHistory;
    
    public CircuitBreakerMetrics() {
        this.totalRequests = new AtomicLong(0);
        this.successfulRequests = new AtomicLong(0);
        this.failedRequests = new AtomicLong(0);
        this.rejectedRequests = new AtomicLong(0);
        this.stateTransitions = new AtomicInteger(0);
        this.transitionHistory = new CopyOnWriteArrayList<>();
    }
    
    public void recordSuccess() {
        totalRequests.incrementAndGet();
        successfulRequests.incrementAndGet();
    }
    
    public void recordFailure() {
        totalRequests.incrementAndGet();
        failedRequests.incrementAndGet();
    }
    
    public void recordRejection() {
        rejectedRequests.incrementAndGet();
    }
    
    public void recordStateTransition(CircuitBreakerState from, CircuitBreakerState to) {
        stateTransitions.incrementAndGet();
        transitionHistory.add(new StateTransitionEvent(from, to, System.currentTimeMillis()));
    }
    
    public double getFailureRate() {
        long total = totalRequests.get();
        return total == 0 ? 0.0 : (double) failedRequests.get() / total;
    }
    
    public double getSuccessRate() {
        long total = totalRequests.get();
        return total == 0 ? 0.0 : (double) successfulRequests.get() / total;
    }
    
    public long getTotalRequests() { return totalRequests.get(); }
    public long getSuccessfulRequests() { return successfulRequests.get(); }
    public long getFailedRequests() { return failedRequests.get(); }
    public long getRejectedRequests() { return rejectedRequests.get(); }
    public int getStateTransitions() { return stateTransitions.get(); }
    public List<StateTransitionEvent> getTransitionHistory() { 
        return new ArrayList<>(transitionHistory); 
    }
    
    @Override
    public String toString() {
        return String.format("Metrics{total=%d, success=%d, failed=%d, rejected=%d, transitions=%d, failureRate=%.2f%%}",
            getTotalRequests(), getSuccessfulRequests(), getFailedRequests(), 
            getRejectedRequests(), getStateTransitions(), getFailureRate() * 100);
    }
}

// ============================================================
// FAILURE DETECTOR
// ============================================================

/**
 * Tracks failures in a sliding time window
 */
class FailureDetector {
    private final Queue<Long> failureTimestamps;
    private final long windowSizeMs;
    
    public FailureDetector(long windowSizeMs) {
        this.failureTimestamps = new ConcurrentLinkedQueue<>();
        this.windowSizeMs = windowSizeMs;
    }
    
    public synchronized void recordFailure() {
        long now = System.currentTimeMillis();
        failureTimestamps.offer(now);
        removeExpiredFailures(now);
    }
    
    public synchronized int getFailureCount() {
        removeExpiredFailures(System.currentTimeMillis());
        return failureTimestamps.size();
    }
    
    public synchronized void reset() {
        failureTimestamps.clear();
    }
    
    private void removeExpiredFailures(long now) {
        long windowStart = now - windowSizeMs;
        while (!failureTimestamps.isEmpty() && 
               failureTimestamps.peek() < windowStart) {
            failureTimestamps.poll();
        }
    }
}

// ============================================================
// CIRCUIT BREAKER
// ============================================================

/**
 * Main circuit breaker implementation
 * Thread-safe with atomic state transitions
 */
class CircuitBreaker {
    private final CircuitBreakerConfig config;
    private final CircuitBreakerMetrics metrics;
    private final FailureDetector failureDetector;
    private final AtomicReference<CircuitBreakerState> state;
    private final AtomicLong lastOpenTime;
    private final AtomicInteger consecutiveSuccesses;
    private final AtomicInteger halfOpenCallCount;
    private final ReadWriteLock lock;
    
    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
        this.metrics = new CircuitBreakerMetrics();
        this.failureDetector = new FailureDetector(config.getSlidingWindowMs());
        this.state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        this.lastOpenTime = new AtomicLong(0);
        this.consecutiveSuccesses = new AtomicInteger(0);
        this.halfOpenCallCount = new AtomicInteger(0);
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Execute a call through the circuit breaker
     */
    public <T> T call(Callable<T> operation) throws Exception {
        checkAndUpdateState();
        
        CircuitBreakerState currentState = state.get();
        
        if (currentState == CircuitBreakerState.OPEN) {
            metrics.recordRejection();
            throw new CircuitBreakerOpenException("Circuit breaker is OPEN");
        }
        
        if (currentState == CircuitBreakerState.HALF_OPEN) {
            if (!tryAcquireHalfOpenPermit()) {
                metrics.recordRejection();
                throw new CircuitBreakerOpenException("Half-open limit reached");
            }
        }
        
        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        } finally {
            if (currentState == CircuitBreakerState.HALF_OPEN) {
                releaseHalfOpenPermit();
            }
        }
    }
    
    /**
     * Execute with fallback
     */
    public <T> T callWithFallback(Callable<T> operation, Callable<T> fallback) {
        try {
            return call(operation);
        } catch (Exception e) {
            try {
                return fallback.call();
            } catch (Exception fallbackException) {
                throw new RuntimeException("Both operation and fallback failed", fallbackException);
            }
        }
    }
    
    private void onSuccess() {
        metrics.recordSuccess();
        
        if (state.get() == CircuitBreakerState.HALF_OPEN) {
            int successes = consecutiveSuccesses.incrementAndGet();
            if (successes >= config.getSuccessThreshold()) {
                transitionTo(CircuitBreakerState.CLOSED);
            }
        }
    }
    
    private void onFailure() {
        metrics.recordFailure();
        failureDetector.recordFailure();
        
        CircuitBreakerState currentState = state.get();
        
        if (currentState == CircuitBreakerState.HALF_OPEN) {
            transitionTo(CircuitBreakerState.OPEN);
        } else if (currentState == CircuitBreakerState.CLOSED) {
            if (failureDetector.getFailureCount() >= config.getFailureThreshold()) {
                transitionTo(CircuitBreakerState.OPEN);
            }
        }
    }
    
    private void checkAndUpdateState() {
        if (state.get() == CircuitBreakerState.OPEN) {
            long timeSinceOpen = System.currentTimeMillis() - lastOpenTime.get();
            if (timeSinceOpen >= config.getTimeoutDurationMs()) {
                transitionTo(CircuitBreakerState.HALF_OPEN);
            }
        }
    }
    
    private void transitionTo(CircuitBreakerState newState) {
        lock.writeLock().lock();
        try {
            CircuitBreakerState oldState = state.get();
            if (oldState == newState) {
                return;
            }
            
            state.set(newState);
            metrics.recordStateTransition(oldState, newState);
            
            if (newState == CircuitBreakerState.OPEN) {
                lastOpenTime.set(System.currentTimeMillis());
                consecutiveSuccesses.set(0);
                halfOpenCallCount.set(0);
            } else if (newState == CircuitBreakerState.HALF_OPEN) {
                consecutiveSuccesses.set(0);
                halfOpenCallCount.set(0);
            } else if (newState == CircuitBreakerState.CLOSED) {
                failureDetector.reset();
                consecutiveSuccesses.set(0);
                halfOpenCallCount.set(0);
            }
            
            System.out.println("[Circuit Breaker] State: " + oldState + " → " + newState);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private boolean tryAcquireHalfOpenPermit() {
        while (true) {
            int current = halfOpenCallCount.get();
            if (current >= config.getHalfOpenMaxCalls()) {
                return false;
            }
            if (halfOpenCallCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
    
    private void releaseHalfOpenPermit() {
        halfOpenCallCount.decrementAndGet();
    }
    
    public CircuitBreakerState getState() {
        return state.get();
    }
    
    public CircuitBreakerMetrics getMetrics() {
        return metrics;
    }
    
    public int getCurrentFailureCount() {
        return failureDetector.getFailureCount();
    }
}

// ============================================================
// MOCK EXTERNAL SERVICE
// ============================================================

/**
 * Simulates an external API with configurable failure rate
 */
class ExternalAPISimulator {
    private final Random random;
    private double failureRate;
    private long latencyMs;
    
    public ExternalAPISimulator(double failureRate, long latencyMs) {
        this.random = new Random();
        this.failureRate = failureRate;
        this.latencyMs = latencyMs;
    }
    
    public String makeRequest() throws Exception {
        // Simulate network latency
        Thread.sleep(latencyMs);
        
        // Simulate failures
        if (random.nextDouble() < failureRate) {
            throw new Exception("External API failed");
        }
        
        return "Success: Data from API";
    }
    
    public void setFailureRate(double rate) {
        this.failureRate = rate;
    }
    
    public void setLatency(long ms) {
        this.latencyMs = ms;
    }
}

// ============================================================
// DEMO AND TESTING
// ============================================================

public class CircuitBreakerSystem {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== CIRCUIT BREAKER SYSTEM DEMO ===\n");
        
        // Demo 1: State Transitions
        demoStateTransitions();
        
        // Demo 2: Fallback Mechanism
        demoFallbackMechanism();
        
        // Demo 3: Recovery Flow
        demoRecoveryFlow();
        
        // Demo 4: Concurrent Access
        demoConcurrentAccess();
        
        // Demo 5: Metrics and Monitoring
        demoMetrics();
    }
    
    private static void demoStateTransitions() throws Exception {
        System.out.println("--- Demo 1: State Transitions ---");
        
        CircuitBreakerConfig config = new CircuitBreakerConfig.Builder()
            .failureThreshold(3)
            .successThreshold(2)
            .timeoutDuration(5000)  // 5 seconds
            .slidingWindow(10000)
            .halfOpenMaxCalls(2)
            .build();
        
        CircuitBreaker breaker = new CircuitBreaker(config);
        ExternalAPISimulator api = new ExternalAPISimulator(1.0, 10); // 100% failure
        
        System.out.println("Initial state: " + breaker.getState());
        
        // Cause failures to trip circuit
        System.out.println("\nCausing 3 failures to trip circuit...");
        for (int i = 1; i <= 3; i++) {
            try {
                breaker.call(() -> api.makeRequest());
            } catch (Exception e) {
                System.out.println("  Call " + i + ": Failed (expected)");
            }
        }
        
        System.out.println("Current state: " + breaker.getState());
        System.out.println("Failures in window: " + breaker.getCurrentFailureCount());
        
        // Try to make call when OPEN
        System.out.println("\nTrying call while OPEN:");
        try {
            breaker.call(() -> api.makeRequest());
        } catch (CircuitBreakerOpenException e) {
            System.out.println("  Rejected: " + e.getMessage());
        }
        
        // Wait for timeout
        System.out.println("\nWaiting for timeout (5 seconds)...");
        Thread.sleep(5500);
        
        // Check state after timeout
        try {
            breaker.call(() -> api.makeRequest());
        } catch (Exception e) {
            // This triggers state check
        }
        System.out.println("State after timeout: " + breaker.getState());
        
        System.out.println();
    }
    
    private static void demoFallbackMechanism() {
        System.out.println("--- Demo 2: Fallback Mechanism ---");
        
        CircuitBreakerConfig config = new CircuitBreakerConfig.Builder()
            .failureThreshold(2)
            .timeoutDuration(60000)
            .build();
        
        CircuitBreaker breaker = new CircuitBreaker(config);
        ExternalAPISimulator api = new ExternalAPISimulator(1.0, 10);
        
        // Cause failures
        for (int i = 0; i < 2; i++) {
            try {
                breaker.call(() -> api.makeRequest());
            } catch (Exception e) {
                // Ignore
            }
        }
        
        System.out.println("Circuit is now: " + breaker.getState());
        
        // Use fallback
        String result = breaker.callWithFallback(
            () -> api.makeRequest(),
            () -> "Cached data from fallback"
        );
        
        System.out.println("Result with fallback: " + result);
        System.out.println();
    }
    
    private static void demoRecoveryFlow() throws Exception {
        System.out.println("--- Demo 3: Recovery Flow ---");
        
        CircuitBreakerConfig config = new CircuitBreakerConfig.Builder()
            .failureThreshold(3)
            .successThreshold(2)
            .timeoutDuration(3000)  // 3 seconds
            .halfOpenMaxCalls(2)
            .build();
        
        CircuitBreaker breaker = new CircuitBreaker(config);
        ExternalAPISimulator api = new ExternalAPISimulator(1.0, 10);
        
        // Trip circuit
        System.out.println("Tripping circuit with failures...");
        for (int i = 0; i < 3; i++) {
            try {
                breaker.call(() -> api.makeRequest());
            } catch (Exception e) {
                // Ignore
            }
        }
        System.out.println("State: " + breaker.getState());
        
        // Wait for timeout
        System.out.println("\nWaiting for timeout...");
        Thread.sleep(3500);
        
        // Service recovers
        api.setFailureRate(0.0); // Service is now healthy
        
        System.out.println("Service recovered. Making test calls...");
        
        // Make successful calls in HALF_OPEN
        for (int i = 1; i <= 2; i++) {
            try {
                breaker.call(() -> api.makeRequest());
                String result = breaker.call(() -> api.makeRequest());
                System.out.println("  Test call " + i + ": Success");
            } catch (Exception e) {
                System.out.println("  Test call " + i + ": Failed");
            }
        }
        
        System.out.println("\nFinal state: " + breaker.getState());
        System.out.println("Circuit successfully recovered!\n");
    }
    
    private static void demoConcurrentAccess() throws Exception {
        System.out.println("--- Demo 4: Concurrent Access ---");
        
        CircuitBreakerConfig config = new CircuitBreakerConfig.Builder()
            .failureThreshold(10)
            .successThreshold(3)
            .timeoutDuration(5000)
            .build();
        
        CircuitBreaker breaker = new CircuitBreaker(config);
        ExternalAPISimulator api = new ExternalAPISimulator(0.3, 10); // 30% failure
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(100);
        
        System.out.println("Launching 100 concurrent requests (30% failure rate)...");
        
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    breaker.call(() -> api.makeRequest());
                } catch (Exception e) {
                    // Expected failures
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        System.out.println("\nResults:");
        System.out.println("  Final state: " + breaker.getState());
        System.out.println("  " + breaker.getMetrics());
        
        System.out.println();
    }
    
    private static void demoMetrics() throws Exception {
        System.out.println("--- Demo 5: Metrics and Monitoring ---");
        
        CircuitBreakerConfig config = new CircuitBreakerConfig.Builder()
            .failureThreshold(5)
            .successThreshold(2)
            .timeoutDuration(2000)
            .build();
        
        CircuitBreaker breaker = new CircuitBreaker(config);
        ExternalAPISimulator api = new ExternalAPISimulator(0.0, 5);
        
        // Make some successful calls
        System.out.println("Making successful calls...");
        for (int i = 0; i < 10; i++) {
            try {
                breaker.call(() -> api.makeRequest());
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Cause failures
        System.out.println("Causing failures...");
        api.setFailureRate(1.0);
        for (int i = 0; i < 5; i++) {
            try {
                breaker.call(() -> api.makeRequest());
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Try rejected calls
        System.out.println("Circuit is OPEN, making rejected calls...");
        for (int i = 0; i < 3; i++) {
            try {
                breaker.call(() -> api.makeRequest());
            } catch (CircuitBreakerOpenException e) {
                // Expected
            }
        }
        
        // Print comprehensive metrics
        CircuitBreakerMetrics metrics = breaker.getMetrics();
        System.out.println("\nCircuit Breaker Metrics:");
        System.out.println("  Total Requests: " + metrics.getTotalRequests());
        System.out.println("  Successful: " + metrics.getSuccessfulRequests());
        System.out.println("  Failed: " + metrics.getFailedRequests());
        System.out.println("  Rejected: " + metrics.getRejectedRequests());
        System.out.println("  Success Rate: " + String.format("%.2f%%", metrics.getSuccessRate() * 100));
        System.out.println("  Failure Rate: " + String.format("%.2f%%", metrics.getFailureRate() * 100));
        System.out.println("  State Transitions: " + metrics.getStateTransitions());
        
        System.out.println("\nState Transition History:");
        for (StateTransitionEvent event : metrics.getTransitionHistory()) {
            System.out.println("  " + event);
        }
        
        System.out.println();
    }
    
    /**
     * Comprehensive integration test
     */
    public static void integrationTest() throws Exception {
        System.out.println("\n=== INTEGRATION TEST ===\n");
        
        CircuitBreakerConfig config = new CircuitBreakerConfig.Builder()
            .failureThreshold(5)
            .successThreshold(3)
            .timeoutDuration(3000)
            .slidingWindow(5000)
            .halfOpenMaxCalls(3)
            .build();
        
        CircuitBreaker breaker = new CircuitBreaker(config);
        ExternalAPISimulator api = new ExternalAPISimulator(0.0, 50);
        
        System.out.println("Phase 1: Normal operation (CLOSED state)");
        makeRequests(breaker, api, 10, "Normal");
        System.out.println("  State: " + breaker.getState() + "\n");
        
        System.out.println("Phase 2: Service degrades, circuit trips");
        api.setFailureRate(1.0);
        makeRequests(breaker, api, 6, "Degraded");
        System.out.println("  State: " + breaker.getState() + "\n");
        
        System.out.println("Phase 3: Circuit OPEN, fast failures");
        makeRequests(breaker, api, 5, "Open");
        System.out.println("  All requests rejected immediately\n");
        
        System.out.println("Phase 4: Waiting for recovery timeout...");
        Thread.sleep(3500);
        
        System.out.println("Phase 5: Testing recovery (HALF_OPEN)");
        api.setFailureRate(0.0); // Service recovered
        makeRequests(breaker, api, 4, "Testing");
        System.out.println("  State: " + breaker.getState() + "\n");
        
        System.out.println("Phase 6: Full recovery (CLOSED)");
        makeRequests(breaker, api, 5, "Recovered");
        System.out.println("  State: " + breaker.getState() + "\n");
        
        System.out.println("Final Metrics: " + breaker.getMetrics());
    }
    
    private static void makeRequests(CircuitBreaker breaker, ExternalAPISimulator api,
                                     int count, String phase) {
        int success = 0, failed = 0, rejected = 0;
        
        for (int i = 0; i < count; i++) {
            try {
                breaker.call(() -> api.makeRequest());
                success++;
            } catch (CircuitBreakerOpenException e) {
                rejected++;
            } catch (Exception e) {
                failed++;
            }
        }
        
        System.out.println("  " + phase + ": " + success + " success, " + 
                          failed + " failed, " + rejected + " rejected");
    }
}
