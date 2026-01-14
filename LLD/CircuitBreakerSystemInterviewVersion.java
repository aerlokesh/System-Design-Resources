import java.time.Duration;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/**
 * INTERVIEW-READY Circuit Breaker System
 * Time to complete: 40-50 minutes
 * Focus: State pattern with 3 states
 */

// ==================== Circuit Breaker States ====================
enum CircuitState {
    CLOSED,      // Normal - requests pass through
    OPEN,        // Failing - requests blocked
    HALF_OPEN    // Testing - limited requests
}

// ==================== Circuit Breaker ====================
class CircuitBreaker {
    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutMs;
    
    private final AtomicReference<CircuitState> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicLong lastFailureTime;

    public CircuitBreaker(String name, int failureThreshold, 
                         int successThreshold, long timeoutMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutMs = timeoutMs;
        
        this.state = new AtomicReference<>(CircuitState.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
    }

    public <T> T call(Supplier<T> operation) throws Exception {
        CircuitState currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return executeRequest(operation);

            case OPEN:
                if (shouldAttemptReset()) {
                    transitionToHalfOpen();
                    return executeRequest(operation);
                }
                throw new Exception("Circuit OPEN for " + name);

            case HALF_OPEN:
                return executeRequest(operation);

            default:
                throw new Exception("Unknown state");
        }
    }

    private <T> T executeRequest(Supplier<T> operation) throws Exception {
        try {
            T result = operation.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }

    private void recordSuccess() {
        CircuitState currentState = state.get();

        switch (currentState) {
            case CLOSED:
                failureCount.set(0);
                break;

            case HALF_OPEN:
                int successes = successCount.incrementAndGet();
                if (successes >= successThreshold) {
                    transitionToClosed();
                }
                break;
        }
    }

    private void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        CircuitState currentState = state.get();

        switch (currentState) {
            case CLOSED:
                int failures = failureCount.incrementAndGet();
                if (failures >= failureThreshold) {
                    transitionToOpen();
                }
                break;

            case HALF_OPEN:
                transitionToOpen();
                break;
        }
    }

    private boolean shouldAttemptReset() {
        long elapsed = System.currentTimeMillis() - lastFailureTime.get();
        return elapsed >= timeoutMs;
    }

    private void transitionToOpen() {
        state.set(CircuitState.OPEN);
        failureCount.set(0);
        successCount.set(0);
        System.out.println("[" + name + "] → OPEN");
    }

    private void transitionToHalfOpen() {
        state.set(CircuitState.HALF_OPEN);
        successCount.set(0);
        System.out.println("[" + name + "] → HALF_OPEN");
    }

    private void transitionToClosed() {
        state.set(CircuitState.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        System.out.println("[" + name + "] → CLOSED");
    }

    public CircuitState getState() {
        return state.get();
    }

    public String getName() {
        return name;
    }
}

// ==================== Simulated Service ====================
class ExternalService {
    private int callCount = 0;
    private boolean shouldFail = false;

    public String makeCall() throws Exception {
        callCount++;
        
        if (shouldFail) {
            throw new Exception("Service unavailable");
        }
        
        return "Success " + callCount;
    }

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }
}

// ==================== Demo ====================
public class CircuitBreakerSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Circuit Breaker Demo ===\n");

        CircuitBreaker cb = new CircuitBreaker(
            "payment-service",
            3,     // failure threshold
            2,     // success threshold
            5000   // timeout ms
        );

        ExternalService service = new ExternalService();

        // Test 1: Normal operation (CLOSED)
        System.out.println("--- Test 1: Normal Operation ---");
        for (int i = 1; i <= 3; i++) {
            try {
                String result = cb.call(() -> {
                    try {
                        return service.makeCall();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                System.out.println("✓ Call " + i + ": " + result);
            } catch (Exception e) {
                System.out.println("✗ Call " + i + ": " + e.getMessage());
            }
        }

        // Test 2: Trigger failures (CLOSED → OPEN)
        System.out.println("\n--- Test 2: Trigger Failures ---");
        service.setShouldFail(true);
        
        for (int i = 1; i <= 5; i++) {
            try {
                cb.call(() -> {
                    try {
                        return service.makeCall();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                System.out.println("✗ Call " + i + ": " + e.getMessage());
            }
            Thread.sleep(100);
        }

        System.out.println("State: " + cb.getState());

        // Test 3: Requests blocked (OPEN)
        System.out.println("\n--- Test 3: Requests Blocked ---");
        for (int i = 1; i <= 3; i++) {
            try {
                cb.call(() -> "Should not reach");
                System.out.println("✓ Request passed");
            } catch (Exception e) {
                System.out.println("✗ Request " + i + " blocked: " + e.getMessage());
            }
        }

        // Test 4: Wait for timeout and recovery
        System.out.println("\n--- Test 4: Timeout and Recovery ---");
        System.out.println("Waiting 5 seconds for timeout...");
        Thread.sleep(5000);

        service.setShouldFail(false);

        // First request after timeout → HALF_OPEN
        System.out.println("Sending test requests...");
        for (int i = 1; i <= 3; i++) {
            try {
                String result = cb.call(() -> {
                    try {
                        return service.makeCall();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                System.out.println("✓ Test " + i + ": " + result + " [State: " + cb.getState() + "]");
            } catch (Exception e) {
                System.out.println("✗ Test " + i + ": " + e.getMessage());
            }
            Thread.sleep(500);
        }

        System.out.println("\nFinal State: " + cb.getState());
        System.out.println("\n✅ Demo complete!");
    }
}
