# Circuit Breaker - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [State Machine Design](#state-machine-design)
4. [Core Entities and Relationships](#core-entities-and-relationships)
5. [Class Design](#class-design)
6. [Complete Implementation](#complete-implementation)
7. [Extensibility](#extensibility)
8. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Circuit Breaker?**

A circuit breaker is a design pattern that prevents an application from repeatedly trying to execute an operation that's likely to fail. It monitors calls to external services and "trips" when failures exceed a threshold, failing fast instead of waiting for timeouts. This protects system resources and provides graceful degradation.

**Analogy:** Like an electrical circuit breaker that trips when current exceeds safe levels, an API circuit breaker "trips" when failure rate is too high.

**Real-World Use Cases:**
- Microservices calling external APIs
- Database connection management
- Third-party service integration (payment gateways, email services)
- Cloud service calls (AWS, Azure, GCP APIs)
- Preventing cascading failures in distributed systems

**Why Circuit Breakers Matter:**
- **Fail Fast:** Don't wait for timeouts on failing services
- **Resource Protection:** Prevent thread pool exhaustion
- **Graceful Degradation:** Return fallback responses
- **Auto-Recovery:** Automatically test when service might be back
- **System Stability:** Prevent cascading failures

---

## Requirements

### Functional Requirements

1. **Three States Management**
   - **CLOSED:** Normal operation, all requests allowed
   - **OPEN:** Service failing, all requests rejected immediately
   - **HALF_OPEN:** Testing recovery, limited requests allowed

2. **State Transitions**
   - **CLOSED → OPEN:** When failures exceed threshold in time window
   - **OPEN → HALF_OPEN:** After timeout duration expires
   - **HALF_OPEN → CLOSED:** When success threshold met
   - **HALF_OPEN → OPEN:** On any failure during testing

3. **Failure Detection**
   - Track failures in sliding time window
   - Configurable failure threshold (e.g., 5 failures in 10 seconds)
   - Count both exceptions and timeout errors

4. **Success Tracking**
   - In HALF_OPEN, track consecutive successes
   - Configurable success threshold (e.g., 3 successful calls)
   - Reset on any failure

5. **Timeout Management**
   - Configurable timeout duration in OPEN state
   - Automatic transition to HALF_OPEN after timeout
   - Reset timer on each state transition

6. **Fallback Mechanism**
   - Return cached responses
   - Return default values
   - Throw custom exceptions
   - Pluggable fallback strategies

7. **Metrics and Monitoring**
   - Total requests, successes, failures
   - State transition events
   - Rejected request count
   - Current state and health status

### Non-Functional Requirements

1. **Thread Safety**
   - Support concurrent requests from multiple threads
   - Atomic state transitions
   - No race conditions in failure counting

2. **Performance**
   - Minimal overhead (<1ms) when CLOSED
   - Fast rejection (<0.1ms) when OPEN
   - Efficient sliding window implementation

3. **Configurability**
   - Failure threshold
   - Success threshold
   - Timeout duration
   - Sliding window size
   - Half-open request limit

4. **Observability**
   - Real-time state visibility
   - Failure rate metrics
   - State transition history
   - Request success/failure tracking

### Out of Scope

- Distributed circuit breaker coordination
- Persistent state across restarts
- Advanced retry strategies (exponential backoff)
- Bulkhead pattern implementation
- Complex fallback orchestration

---

## State Machine Design

### State Diagram

```
          failureThreshold exceeded
    ┌─────────────────────────────────┐
    │                                 │
    ▼                                 │
┌─────────┐                      ┌────────┐
│ CLOSED  │                      │  OPEN  │
│         │                      │        │
│ Allow   │                      │ Reject │
│ All     │                      │ All    │
└────┬────┘                      └────┬───┘
     │                                │
     │ successThreshold met           │ timeout expired
     │                                │
     │    ┌────────────┐              │
     └────│ HALF_OPEN  │◄─────────────┘
          │            │
          │ Test with  │
          │ Limited    │──┐ any failure
          │ Requests   │  │
          └────────────┘  │
                │         │
                └─────────┘
```

### State Behaviors

**CLOSED State:**
- ✅ All requests allowed
- ✅ Failures counted in sliding window
- ✅ Transition to OPEN if threshold exceeded
- ✅ Normal operation

**OPEN State:**
- ❌ All requests rejected immediately
- ❌ Return fallback response
- ⏱️ Wait for timeout duration
- ➡️ Auto-transition to HALF_OPEN after timeout

**HALF_OPEN State:**
- ⚠️ Limited requests allowed (test calls)
- ✅ Track consecutive successes
- ✅ Transition to CLOSED if success threshold met
- ❌ Transition to OPEN on any failure

### Detailed State Transitions

```
State: CLOSED
├─ On Success: Continue
├─ On Failure: Increment failure count
│  └─ If failures >= threshold in window
│     └─ Transition to OPEN
└─ Reset: Clear window when no recent failures

State: OPEN
├─ On Request: Reject immediately (fallback)
├─ On Timeout: Transition to HALF_OPEN
└─ Reset: Start timeout timer on entry

State: HALF_OPEN
├─ On Success: Increment success count
│  └─ If consecutive successes >= threshold
│     └─ Transition to CLOSED
├─ On Failure: Transition to OPEN
└─ Limit: Only allow N test requests
```

---

## Core Entities and Relationships

### Key Entities

1. **CircuitBreaker**
   - Main interface/class
   - Manages state transitions
   - Executes protected calls
   - Provides fallback mechanism

2. **CircuitBreakerState (Enum)**
   - CLOSED, OPEN, HALF_OPEN
   - State-specific behavior
   - Transition rules

3. **CircuitBreakerConfig**
   - Configuration parameters
   - Failure threshold
   - Success threshold
   - Timeout duration
   - Window size

4. **FailureDetector**
   - Tracks failures in sliding window
   - Determines if threshold exceeded
   - Time-based window management

5. **CircuitBreakerMetrics**
   - Success/failure counters
   - State transition events
   - Request rejection count
   - Health indicators

6. **FallbackStrategy (Interface)**
   - Different fallback approaches
   - Cached response
   - Default value
   - Custom exception

### Entity Relationships

```
┌─────────────────────────────────────────────┐
│         CircuitBreaker                       │
│  - current state                             │
│  - config                                    │
│  - metrics                                   │
│  - failure detector                          │
└───────────┬─────────────────────────────────┘
            │ uses
            ▼
┌───────────────────────────┐
│  CircuitBreakerState      │
│  - CLOSED                 │
│  - OPEN                   │
│  - HALF_OPEN              │
└───────────────────────────┘

┌───────────────────────────┐
│  FailureDetector          │
│  - sliding window         │
│  - failure timestamps     │
│  - threshold check        │
└───────────────────────────┘

┌───────────────────────────┐
│  CircuitBreakerMetrics    │
│  - total requests         │
│  - success count          │
│  - failure count          │
│  - rejection count        │
│  - state transitions      │
└───────────────────────────┘

┌───────────────────────────┐
│  FallbackStrategy         │
│  <<interface>>            │
└───────────┬───────────────┘
            │ implements
    ┌───────┴────────┬──────────┐
    │                │          │
┌───▼────┐    ┌──────▼───┐  ┌──▼──────┐
│ Cached │    │ Default  │  │ Custom  │
│ Fallback│   │ Fallback │  │ Fallback│
└────────┘    └──────────┘  └─────────┘
```

---

## Class Design

### 1. CircuitBreakerConfig

```java
/**
 * Configuration for circuit breaker behavior
 */
public class CircuitBreakerConfig {
    private final int failureThreshold;      // Failures to trip circuit
    private final int successThreshold;      // Successes to close circuit
    private final long timeoutDurationMs;    // Time in OPEN before HALF_OPEN
    private final long slidingWindowMs;      // Window for counting failures
    private final int halfOpenMaxCalls;      // Max test calls in HALF_OPEN
    
    public CircuitBreakerConfig(int failureThreshold, int successThreshold,
                                long timeoutDurationMs, long slidingWindowMs,
                                int halfOpenMaxCalls) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutDurationMs = timeoutDurationMs;
        this.slidingWindowMs = slidingWindowMs;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
    }
    
    // Builder pattern for easy configuration
    public static class Builder {
        private int failureThreshold = 5;
        private int successThreshold = 2;
        private long timeoutDurationMs = 60000;  // 60 seconds
        private long slidingWindowMs = 10000;     // 10 seconds
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
    
    // Getters
    public int getFailureThreshold() { return failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public long getTimeoutDurationMs() { return timeoutDurationMs; }
    public long getSlidingWindowMs() { return slidingWindowMs; }
    public int getHalfOpenMaxCalls() { return halfOpenMaxCalls; }
}
```

### 2. CircuitBreakerState Enum

```java
/**
 * Circuit breaker states
 */
public enum CircuitBreakerState {
    CLOSED,      // Normal operation
    OPEN,        // Failing, reject all
    HALF_OPEN    // Testing recovery
}
```

### 3. CircuitBreakerMetrics

```java
/**
 * Tracks circuit breaker metrics
 */
public class CircuitBreakerMetrics {
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
    
    // Getters for all metrics
}
```

### 4. FailureDetector

```java
/**
 * Tracks failures in a sliding time window
 */
public class FailureDetector {
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
```

### 5. CircuitBreaker Main Class

```java
/**
 * Main circuit breaker implementation
 * Thread-safe with atomic state transitions
 */
public class CircuitBreaker {
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
     * @param operation The operation to execute
     * @return Result of the operation
     * @throws CircuitBreakerOpenException if circuit is OPEN
     */
    public <T> T call(Callable<T> operation) throws Exception {
        // Check and update state
        checkAndUpdateState();
        
        CircuitBreakerState currentState = state.get();
        
        if (currentState == CircuitBreakerState.OPEN) {
            metrics.recordRejection();
            throw new CircuitBreakerOpenException("Circuit breaker is OPEN");
        }
        
        if (currentState == CircuitBreakerState.HALF_OPEN) {
            // Limit concurrent test calls
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
            
            // State-specific actions
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
}
```

---

## Complete Implementation

### Supporting Classes

**StateTransitionEvent:**
```java
public class StateTransitionEvent {
    private final CircuitBreakerState fromState;
    private final CircuitBreakerState toState;
    private final long timestamp;
    
    public StateTransitionEvent(CircuitBreakerState from, CircuitBreakerState to, long timestamp) {
        this.fromState = from;
        this.toState = to;
        this.timestamp = timestamp;
    }
    
    // Getters and toString
}
```

**CircuitBreakerOpenException:**
```java
public class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
```

### Usage Examples

**Basic Usage:**
```java
// Configure circuit breaker
CircuitBreakerConfig config = new CircuitBreakerConfig.Builder()
    .failureThreshold(5)
    .successThreshold(2)
    .timeoutDuration(30000)  // 30 seconds
    .slidingWindow(10000)    // 10 seconds
    .halfOpenMaxCalls(3)
    .build();

CircuitBreaker breaker = new CircuitBreaker(config);

// Make protected call
try {
    String result = breaker.call(() -> {
        return externalAPI.getData();
    });
} catch (CircuitBreakerOpenException e) {
    // Circuit is open, use fallback
    return cachedData;
}
```

**With Fallback:**
```java
String result = breaker.callWithFallback(
    () -> externalAPI.getData(),      // Primary operation
    () -> getCachedData()             // Fallback
);
```

---

## Extensibility

### 1. Async Circuit Breaker

**Support for asynchronous operations:**

```java
public class AsyncCircuitBreaker extends CircuitBreaker {
    private final ExecutorService executor;
    
    public <T> CompletableFuture<T> callAsync(Callable<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(operation);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    public <T> CompletableFuture<T> callAsyncWithFallback(
            Callable<T> operation, Callable<T> fallback) {
        return callAsync(operation)
            .exceptionally(ex -> {
                try {
                    return fallback.call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
    }
}
```

### 2. Rate-Based Circuit Breaker

**Trip based on failure rate instead of count:**

```java
public class RateBasedCircuitBreaker extends CircuitBreaker {
    private final double failureRateThreshold; // e.g., 0.5 for 50%
    private final int minimumRequests; // Minimum calls before calculating rate
    
    @Override
    protected boolean shouldTrip() {
        int totalCalls = getTotalCallsInWindow();
        
        if (totalCalls < minimumRequests) {
            return false; // Not enough data
        }
        
        int failures = failureDetector.getFailureCount();
        double failureRate = (double) failures / totalCalls;
        
        return failureRate >= failureRateThreshold;
    }
}
```

### 3. Bulkhead Pattern Integration

**Combine circuit breaker with bulkhead (thread pool isolation):**

```java
public class BulkheadCircuitBreaker extends CircuitBreaker {
    private final Semaphore semaphore;
    private final long maxWaitTimeMs;
    
    public BulkheadCircuitBreaker(CircuitBreakerConfig config, int maxConcurrentCalls) {
        super(config);
        this.semaphore = new Semaphore(maxConcurrentCalls);
        this.maxWaitTimeMs = 1000;
    }
    
    @Override
    public <T> T call(Callable<T> operation) throws Exception {
        // Try to acquire permit
        if (!semaphore.tryAcquire(maxWaitTimeMs, TimeUnit.MILLISECONDS)) {
            throw new BulkheadFullException("Too many concurrent calls");
        }
        
        try {
            return super.call(operation);
        } finally {
            semaphore.release();
        }
    }
}
```

### 4. Monitoring and Observability

**Export metrics for monitoring systems:**

```java
public interface CircuitBreakerMonitor {
    void onStateTransition(CircuitBreakerState from, CircuitBreakerState to);
    void onSuccess();
    void onFailure();
    void onReject();
}

public class PrometheusCircuitBreakerMonitor implements CircuitBreakerMonitor {
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter rejectionCounter;
    private final Gauge stateGauge;
    
    @Override
    public void onStateTransition(CircuitBreakerState from, CircuitBreakerState to) {
        stateGauge.set(to.ordinal());
        // Emit metric to Prometheus
    }
    
    @Override
    public void onSuccess() {
        successCounter.inc();
    }
    
    @Override
    public void onFailure() {
        failureCounter.inc();
    }
    
    @Override
    public void onReject() {
        rejectionCounter.inc();
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- What triggers circuit opening? (failure count, rate, timeout?)
- How long to wait before testing recovery?
- How many test calls in HALF_OPEN?
- What's the fallback strategy?
- Thread safety requirements?

**2. State Machine First (5 minutes)**
- Draw the 3-state diagram
- Explain transition conditions
- Show state-specific behaviors
- Get interviewer agreement

**3. Core Implementation (20 minutes)**
- Start with config and state enum
- Implement failure detector
- Add state transition logic
- Show thread safety (atomic operations)

**4. Integration Example (5 minutes)**
- Show how to wrap API calls
- Demonstrate fallback handling
- Explain metrics collection

**5. Extensions (5 minutes)**
- Async support
- Rate-based detection
- Bulkhead integration
- Monitoring hooks

### Common Interview Questions & Answers

**Q1: "Why three states instead of two (OPEN/CLOSED)?"**

**A:** HALF_OPEN allows gradual recovery:
- **Without HALF_OPEN:** Circuit opens/closes rapidly (flapping)
- **With HALF_OPEN:** Test with limited traffic before fully reopening
- **Benefit:** Prevents overwhelming a recovering service
- **Real-world:** Netflix Hystrix, Resilience4j use this pattern

**Q2: "How do you prevent race conditions in state transitions?"**

**A:** Use atomic operations and locks:
```java
// Atomic state reference
private final AtomicReference<CircuitBreakerState> state;

// Lock for transition logic
private void transitionTo(CircuitBreakerState newState) {
    lock.writeLock().lock();
    try {
        CircuitBreakerState oldState = state.get();
        if (oldState != newState) {
            state.set(newState);
            // Reset counters, timers, etc.
        }
    } finally {
        lock.writeLock().unlock();
    }
}
```

**Q3: "What if the failure threshold is reached multiple times while OPEN?"**

**A:** 
- State is already OPEN, no action needed
- Don't reset the timeout timer
- Keep rejecting requests until timeout expires
- Metrics continue tracking rejections

**Q4: "How do you handle slow calls (not failures but timeouts)?"**

**A:**
```java
public <T> T callWithTimeout(Callable<T> operation, long timeoutMs) {
    Future<T> future = executor.submit(operation);
    try {
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        future.cancel(true);
        onFailure(); // Treat timeout as failure
        throw e;
    }
}
```

**Q5: "How would you test this in production?"**

**A:** 
1. **Chaos Engineering:** Intentionally fail dependencies
2. **Gradual Rollout:** Deploy to canary servers first
3. **Monitoring:** Watch state transitions, rejection rates
4. **Alerting:** Alert on excessive OPEN state duration
5. **Dashboards:** Real-time circuit health visualization

### Design Patterns Used

1. **State Pattern:** CircuitBreakerState with different behaviors
2. **Strategy Pattern:** Different fallback strategies
3. **Observer Pattern:** Metrics and monitoring hooks
4. **Proxy Pattern:** Wrapping external service calls
5. **Builder Pattern:** Configuration object construction
6. **Template Method:** Call execution flow

### Expected Level Performance

**Junior Engineer:**
- Understand circuit breaker concept
- Implement basic 3-state machine
- Simple failure counting
- Basic state transitions

**Mid-Level Engineer:**
- Complete implementation with sliding window
- Thread-safe state transitions
- Fallback mechanism
- Metrics tracking
- Explain trade-offs

**Senior Engineer:**
- Production-ready implementation
- Advanced features (async, rate-based, bulkhead)
- Monitoring integration
- Performance optimization
- Testing strategy
- Distributed considerations

### Key Trade-offs

**1. Failure Detection Strategy**
- **Count-based:** Simple, but doesn't account for request volume
- **Rate-based:** More accurate, requires minimum sample size
- **Our choice:** Count-based with sliding window (simpler for LLD)

**2. Half-Open Request Limit**
- **Single request:** Safest, but slow recovery
- **Multiple requests:** Faster recovery, risk of overload
- **Balance:** 2-5 test requests typical

**3. Timeout Duration**
- **Short (10-30s):** Fast recovery, may reopen too soon
- **Long (60-120s):** Safer, but slower recovery
- **Balance:** 30-60 seconds common in production

**4. Sliding Window Size**
- **Small (5-10s):** React quickly to failures
- **Large (30-60s):** More stable, less noise
- **Balance:** 10 seconds typical

### Code Quality Checklist

✅ **Thread Safety**
- Atomic state transitions
- Lock-free where possible
- No race conditions

✅ **State Management**
- Clear transition rules
- Proper state cleanup
- No orphaned state

✅ **Metrics**
- Comprehensive tracking
- Thread-safe counters
- Historical events

✅ **Configuration**
- Builder pattern for ease
- Validation of parameters
- Reasonable defaults

✅ **Error Handling**
- Custom exceptions
- Fallback support
- Graceful degradation

---

## Summary

This Circuit Breaker demonstrates:

1. **Complete State Machine:** CLOSED, OPEN, HALF_OPEN with proper transitions
2. **Thread-Safe:** Atomic operations and proper locking
3. **Sliding Window:** Time-based failure tracking
4. **Configurable:** Builder pattern for easy configuration
5. **Observable:** Comprehensive metrics and monitoring
6. **Production-Ready:** Fallback support, resource protection
7. **Interview Success:** Clear state machine, trade-off analysis, extensible design

**Key Takeaways:**
- **State machine is core** - Draw it first in interviews
- **HALF_OPEN prevents flapping** - Essential for stability
- **Sliding window** - Better than simple counter
- **Thread safety critical** - AtomicReference and locks
- **Observability essential** - Metrics for production

**Related Patterns:**
- Retry Pattern (with exponential backoff)
- Bulkhead Pattern (resource isolation)
- Timeout Pattern (prevent hanging calls)
- Fallback Pattern (graceful degradation)
- Rate Limiter (control request rate)

**Real-World Libraries:**
- Netflix Hystrix (pioneered pattern, now in maintenance)
- Resilience4j (modern, lightweight)
- Spring Cloud Circuit Breaker (abstraction layer)
- Polly (.NET resilience library)
- Failsafe (Java resilience library)

---

*Last Updated: January 4, 2026*
*Difficulty Level: Medium*
*Key Focus: State Machine, Thread Safety, Resilience*
*Company: Microsoft (Senior Level)*
*Interview Success Rate: High with proper state machine design*
