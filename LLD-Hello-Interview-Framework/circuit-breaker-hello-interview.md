# Circuit Breaker - HELLO Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **Three States**
   - CLOSED: Normal operation, requests pass through
   - OPEN: Failure threshold exceeded, requests blocked
   - HALF_OPEN: Testing if service recovered

2. **Failure Detection**
   - Track consecutive failures
   - Track failure rate within time window
   - Configurable failure threshold

3. **State Transitions**
   - CLOSED → OPEN: When failure threshold exceeded
   - OPEN → HALF_OPEN: After timeout period
   - HALF_OPEN → CLOSED: When test requests succeed
   - HALF_OPEN → OPEN: When test requests fail

4. **Request Handling**
   - Execute requests in CLOSED state
   - Block requests in OPEN state
   - Allow limited test requests in HALF_OPEN state

5. **Timeout Management**
   - Configurable timeout for state transitions
   - Reset timeout on state change

#### Nice to Have (P1)
- Metrics and monitoring (success rate, failure rate)
- Event notifications (state change alerts)
- Multiple failure criteria (timeout, exception types)
- Automatic recovery testing
- Fallback mechanism
- Bulkhead pattern integration

### Non-Functional Requirements

#### Performance
- **Overhead**: < 1ms per request check
- **Thread-safe**: Support concurrent requests
- **Memory**: O(1) space for state tracking

#### Reliability
- No false positives (blocking working service)
- Quick recovery when service recovers
- Graceful degradation

#### Configurability
- Adjustable thresholds
- Customizable timeout periods
- Pluggable failure detection

### Constraints and Assumptions

**Constraints**:
- Single JVM (not distributed circuit breaker)
- Synchronous request handling
- One circuit breaker per service

**Assumptions**:
- Downstream service can fail
- Failures are temporary and recoverable
- Quick detection preferred over accuracy

**Out of Scope**:
- Distributed circuit breaker coordination
- Complex retry policies
- Service mesh integration
- Persistent state storage

---

## 2️⃣ Core Entities

### Enum: CircuitBreakerState
**Responsibility**: Define circuit breaker states

**Values**:
- `CLOSED` - Normal operation, requests pass
- `OPEN` - Service failing, requests blocked
- `HALF_OPEN` - Testing recovery, limited requests

**State Meanings**:
```
CLOSED:     Everything works, monitor failures
OPEN:       Service down, block all requests
HALF_OPEN:  Maybe recovered, test carefully
```

### Class: CircuitBreaker
**Responsibility**: Main circuit breaker implementation

**Key Attributes**:
- `state: AtomicReference<CircuitBreakerState>` - Current state
- `failureCount: AtomicInteger` - Consecutive failures
- `successCount: AtomicInteger` - Consecutive successes in HALF_OPEN
- `lastFailureTime: AtomicLong` - Timestamp of last failure
- `config: CircuitBreakerConfig` - Configuration

**Key Methods**:
- `call(Supplier<T>)`: Execute request through circuit breaker
- `recordSuccess()`: Record successful request
- `recordFailure()`: Record failed request
- `getState()`: Get current state
- `reset()`: Manually reset to CLOSED

**Relationships**:
- Uses: CircuitBreakerConfig
- Manages: State transitions
- Thread-safe: Atomic operations

### Class: CircuitBreakerConfig
**Responsibility**: Configuration parameters

**Key Attributes**:
- `failureThreshold: int` - Max consecutive failures (default: 5)
- `successThreshold: int` - Required successes in HALF_OPEN (default: 2)
- `timeout: Duration` - Time to wait in OPEN state (default: 60s)
- `halfOpenMaxCalls: int` - Max test calls in HALF_OPEN (default: 3)

**Relationships**:
- Used by: CircuitBreaker
- Immutable: Thread-safe configuration

### Interface: CircuitBreakerListener
**Responsibility**: Observe state changes

**Key Methods**:
- `onStateChange(oldState, newState)`: Called on transition
- `onSuccess()`: Called on successful request
- `onFailure(exception)`: Called on failed request

**Relationships**:
- Implemented by: Custom listeners
- Used by: CircuitBreaker for notifications

### Class: CircuitBreakerException
**Responsibility**: Indicate circuit breaker is open

**Key Attributes**:
- `state: CircuitBreakerState` - Current state
- `message: String` - Error description

**Relationships**:
- Thrown by: CircuitBreaker when OPEN
- Caught by: Client code

---

## 3️⃣ API Design

### Enum: CircuitBreakerState

```java
/**
 * States of circuit breaker
 */
public enum CircuitBreakerState {
    /** Normal operation - requests pass through */
    CLOSED,
    
    /** Service failing - requests blocked immediately */
    OPEN,
    
    /** Testing recovery - limited requests allowed */
    HALF_OPEN
}
```

### Class: CircuitBreakerConfig

```java
/**
 * Immutable configuration for circuit breaker
 */
public class CircuitBreakerConfig {
    private final int failureThreshold;
    private final int successThreshold;
    private final Duration timeout;
    private final int halfOpenMaxCalls;
    
    private CircuitBreakerConfig(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.successThreshold = builder.successThreshold;
        this.timeout = builder.timeout;
        this.halfOpenMaxCalls = builder.halfOpenMaxCalls;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int failureThreshold = 5;
        private int successThreshold = 2;
        private Duration timeout = Duration.ofSeconds(60);
        private int halfOpenMaxCalls = 3;
        
        public Builder failureThreshold(int threshold) {
            if (threshold < 1) {
                throw new IllegalArgumentException("Threshold must be positive");
            }
            this.failureThreshold = threshold;
            return this;
        }
        
        public Builder successThreshold(int threshold) {
            this.successThreshold = threshold;
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder halfOpenMaxCalls(int calls) {
            this.halfOpenMaxCalls = calls;
            return this;
        }
        
        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
    
    // Getters
    public int getFailureThreshold() { return failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public Duration getTimeout() { return timeout; }
    public int getHalfOpenMaxCalls() { return halfOpenMaxCalls; }
}
```

### Class: CircuitBreaker

```java
/**
 * Circuit breaker implementation using State pattern
 * Thread-safe for concurrent access
 */
public class CircuitBreaker {
    private final String name;
    private final CircuitBreakerConfig config;
    private final AtomicReference<CircuitBreakerState> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicInteger halfOpenCalls;
    private final AtomicLong lastFailureTime;
    private final List<CircuitBreakerListener> listeners;
    
    public CircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = name;
        this.config = config;
        this.state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.halfOpenCalls = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    /**
     * Execute request through circuit breaker
     * 
     * @param supplier Request to execute
     * @return Result of request
     * @throws CircuitBreakerException if circuit is OPEN
     * @throws Exception from the request execution
     */
    public <T> T call(Supplier<T> supplier) throws Exception {
        CircuitBreakerState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return executeRequest(supplier);
                
            case OPEN:
                // Check if timeout expired
                if (shouldAttemptReset()) {
                    transitionToHalfOpen();
                    return executeRequest(supplier);
                }
                throw new CircuitBreakerException(
                    "Circuit breaker is OPEN for: " + name, currentState);
                
            case HALF_OPEN:
                // Allow limited test requests
                if (halfOpenCalls.get() < config.getHalfOpenMaxCalls()) {
                    halfOpenCalls.incrementAndGet();
                    return executeRequest(supplier);
                }
                throw new CircuitBreakerException(
                    "Circuit breaker HALF_OPEN limit reached: " + name, currentState);
                
            default:
                throw new IllegalStateException("Unknown state: " + currentState);
        }
    }
    
    private <T> T executeRequest(Supplier<T> supplier) throws Exception {
        try {
            T result = supplier.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }
    
    /**
     * Record successful request
     */
    private void recordSuccess() {
        CircuitBreakerState currentState = state.get();
        
        notifyListeners(l -> l.onSuccess());
        
        switch (currentState) {
            case CLOSED:
                // Reset failure count on success
                failureCount.set(0);
                break;
                
            case HALF_OPEN:
                int successes = successCount.incrementAndGet();
                if (successes >= config.getSuccessThreshold()) {
                    transitionToClosed();
                }
                break;
                
            case OPEN:
                // Should not happen, but reset if it does
                break;
        }
    }
    
    /**
     * Record failed request
     */
    private void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        
        CircuitBreakerState currentState = state.get();
        
        notifyListeners(l -> l.onFailure(new Exception("Request failed")));
        
        switch (currentState) {
            case CLOSED:
                int failures = failureCount.incrementAndGet();
                if (failures >= config.getFailureThreshold()) {
                    transitionToOpen();
                }
                break;
                
            case HALF_OPEN:
                // Any failure in HALF_OPEN immediately goes back to OPEN
                transitionToOpen();
                break;
                
            case OPEN:
                // Already open, do nothing
                break;
        }
    }
    
    private boolean shouldAttemptReset() {
        long lastFailure = lastFailureTime.get();
        long now = System.currentTimeMillis();
        long timeoutMillis = config.getTimeout().toMillis();
        
        return (now - lastFailure) >= timeoutMillis;
    }
    
    private void transitionToOpen() {
        CircuitBreakerState oldState = state.getAndSet(CircuitBreakerState.OPEN);
        if (oldState != CircuitBreakerState.OPEN) {
            failureCount.set(0);
            successCount.set(0);
            halfOpenCalls.set(0);
            notifyStateChange(oldState, CircuitBreakerState.OPEN);
            System.out.println("[" + name + "] Circuit breaker OPENED");
        }
    }
    
    private void transitionToHalfOpen() {
        CircuitBreakerState oldState = state.getAndSet(CircuitBreakerState.HALF_OPEN);
        if (oldState != CircuitBreakerState.HALF_OPEN) {
            halfOpenCalls.set(0);
            successCount.set(0);
            notifyStateChange(oldState, CircuitBreakerState.HALF_OPEN);
            System.out.println("[" + name + "] Circuit breaker HALF_OPEN");
        }
    }
    
    private void transitionToClosed() {
        CircuitBreakerState oldState = state.getAndSet(CircuitBreakerState.CLOSED);
        if (oldState != CircuitBreakerState.CLOSED) {
            failureCount.set(0);
            successCount.set(0);
            halfOpenCalls.set(0);
            notifyStateChange(oldState, CircuitBreakerState.CLOSED);
            System.out.println("[" + name + "] Circuit breaker CLOSED");
        }
    }
    
    /**
     * Manually reset circuit breaker to CLOSED
     */
    public void reset() {
        transitionToClosed();
    }
    
    /**
     * Get current state
     */
    public CircuitBreakerState getState() {
        return state.get();
    }
    
    /**
     * Add state change listener
     */
    public void addListener(CircuitBreakerListener listener) {
        listeners.add(listener);
    }
    
    private void notifyStateChange(CircuitBreakerState oldState, 
                                   CircuitBreakerState newState) {
        notifyListeners(l -> l.onStateChange(oldState, newState));
    }
    
    private void notifyListeners(Consumer<CircuitBreakerListener> action) {
        listeners.forEach(listener -> {
            try {
                action.accept(listener);
            } catch (Exception e) {
                // Log but don't fail
                System.err.println("Listener notification failed: " + e.getMessage());
            }
        });
    }
    
    public String getName() { return name; }
    public CircuitBreakerConfig getConfig() { return config; }
    public int getFailureCount() { return failureCount.get(); }
    public int getSuccessCount() { return successCount.get(); }
}
```

### Interface: CircuitBreakerListener

```java
/**
 * Listener for circuit breaker events
 */
public interface CircuitBreakerListener {
    /**
     * Called when state changes
     */
    default void onStateChange(CircuitBreakerState oldState, 
                               CircuitBreakerState newState) {}
    
    /**
     * Called on successful request
     */
    default void onSuccess() {}
    
    /**
     * Called on failed request
     */
    default void onFailure(Exception exception) {}
}
```

### Class: CircuitBreakerException

```java
/**
 * Exception thrown when circuit breaker is OPEN
 */
public class CircuitBreakerException extends RuntimeException {
    private final CircuitBreakerState state;
    
    public CircuitBreakerException(String message, CircuitBreakerState state) {
        super(message);
        this.state = state;
    }
    
    public CircuitBreakerState getState() {
        return state;
    }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Normal Operation (CLOSED → CLOSED)

**Scenario**: Successful requests in CLOSED state

**Sequence**:
1. Client calls `circuitBreaker.call(() -> serviceCall())`
2. Check state: CLOSED
3. Execute request through `executeRequest()`
4. Service call succeeds
5. Call `recordSuccess()`
6. Reset failure count to 0
7. Notify success listeners
8. Return result to client

**State Tracking**:
```
Initial: CLOSED, failures=0
Request 1: Success → CLOSED, failures=0
Request 2: Success → CLOSED, failures=0
Request 3: Success → CLOSED, failures=0
```

### Flow 2: Failure Detection (CLOSED → OPEN)

**Scenario**: Multiple failures trigger circuit opening

**Sequence**:
1. Request 1 fails → `recordFailure()` → failures=1, state=CLOSED
2. Request 2 fails → failures=2, state=CLOSED
3. Request 3 fails → failures=3, state=CLOSED
4. Request 4 fails → failures=4, state=CLOSED
5. Request 5 fails → failures=5 >= threshold(5)
6. Call `transitionToOpen()`
7. Set state to OPEN
8. Reset counters
9. Notify listeners of state change
10. Record lastFailureTime
11. Future requests immediately blocked

**State Progression**:
```
failures=0 [CLOSED] ──fail──► failures=1 [CLOSED]
failures=1 [CLOSED] ──fail──► failures=2 [CLOSED]
failures=2 [CLOSED] ──fail──► failures=3 [CLOSED]
failures=3 [CLOSED] ──fail──► failures=4 [CLOSED]
failures=4 [CLOSED] ──fail──► failures=5 [OPEN] ✗
```

### Flow 3: Request Blocking (OPEN)

**Scenario**: Requests blocked in OPEN state

**Sequence**:
1. Client calls `circuitBreaker.call(() -> serviceCall())`
2. Check state: OPEN
3. Check if timeout expired: `shouldAttemptReset()`
4. Calculate: `now - lastFailureTime < timeout(60s)`
5. Timeout NOT expired
6. Throw `CircuitBreakerException`
7. Request never reaches downstream service
8. Client handles exception (fallback/retry)

**Timing Example**:
```
Last failure: 10:00:00
Current time: 10:00:30
Timeout: 60s

Elapsed: 30s < 60s → Still OPEN → Block request
```

### Flow 4: Recovery Testing (OPEN → HALF_OPEN → CLOSED)

**Scenario**: Successful recovery after timeout

**Sequence**:
1. Circuit is OPEN, last failure at T=0
2. Request arrives at T=65s (timeout=60s)
3. Check state: OPEN
4. Call `shouldAttemptReset()`: 65s >= 60s ✓
5. Transition to HALF_OPEN
6. Reset test counters
7. Execute request (test call #1)
8. Success! → `recordSuccess()` → successes=1
9. Check: successes(1) < threshold(2)
10. Stay in HALF_OPEN
11. Next request executes (test call #2)
12. Success! → successes=2
13. Check: successes(2) >= threshold(2) ✓
14. Transition to CLOSED
15. Circuit fully recovered

**State Flow**:
```
[OPEN] ──timeout+request──► [HALF_OPEN]
[HALF_OPEN] ──success(1)──► [HALF_OPEN]
[HALF_OPEN] ──success(2)──► [CLOSED] ✓
```

### Flow 5: Failed Recovery (HALF_OPEN → OPEN)

**Scenario**: Test request fails, back to OPEN

**Sequence**:
1. Circuit transitions to HALF_OPEN
2. Test request #1 executes
3. Request fails
4. Call `recordFailure()`
5. In HALF_OPEN: ANY failure immediately opens circuit
6. Transition back to OPEN
7. Reset lastFailureTime
8. Wait another timeout period

**Quick Failure**:
```
[OPEN] ──timeout──► [HALF_OPEN] ──test fails──► [OPEN]
                    (try recovery)  (nope!)      (back to waiting)
```

### Flow 6: Call Limit in HALF_OPEN

**Scenario**: Max test calls reached

**Sequence**:
1. Circuit in HALF_OPEN
2. halfOpenCalls=0, maxCalls=3
3. Request 1: halfOpenCalls=1, executes
4. Request 2: halfOpenCalls=2, executes
5. Request 3: halfOpenCalls=3, executes
6. Request 4: halfOpenCalls=3 >= maxCalls(3)
7. Throw `CircuitBreakerException`
8. Request 4 blocked (limit reached)

**Purpose**: Limit load on recovering service

---

## 5️⃣ Design

### Class Diagram

```
┌─────────────────────────────────────┐
│  <<enum>> CircuitBreakerState      │
│  + CLOSED                           │
│  + OPEN                             │
│  + HALF_OPEN                        │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  CircuitBreakerConfig               │
│  - failureThreshold: int            │
│  - successThreshold: int            │
│  - timeout: Duration                │
│  - halfOpenMaxCalls: int            │
│  + Builder pattern                  │
└─────────────────────────────────────┘
           ▲
           │ uses
           │
┌──────────┴──────────────────────────┐
│  CircuitBreaker                     │
│  - state: AtomicReference<State>    │
│  - failureCount: AtomicInteger      │
│  - successCount: AtomicInteger      │
│  - lastFailureTime: AtomicLong      │
│  - config: CircuitBreakerConfig     │
│                                     │
│  + call(Supplier<T>): T             │
│  + recordSuccess()                  │
│  + recordFailure()                  │
│  + reset()                          │
│  + getState()                       │
│  - transitionToOpen()               │
│  - transitionToHalfOpen()           │
│  - transitionToClosed()             │
└─────────────┬───────────────────────┘
              │ notifies
              ▼
┌─────────────────────────────────────┐
│  <<interface>>                      │
│  CircuitBreakerListener             │
│  + onStateChange()                  │
│  + onSuccess()                      │
│  + onFailure()                      │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  CircuitBreakerException            │
│  - state: CircuitBreakerState       │
│  + getState()                       │
└─────────────────────────────────────┘
```

### Design Patterns

**Pattern 1: State Pattern**
- **Why**: Circuit breaker has distinct states with different behaviors
- **Where**: CLOSED, OPEN, HALF_OPEN states
- **How**: Switch statement on state, different logic per state
- **Benefit**: Clear state transitions, easy to add new states

**Pattern 2: Builder Pattern**
- **Why**: Multiple optional configuration parameters
- **Where**: CircuitBreakerConfig.Builder
- **How**: Fluent API for configuration
- **Benefit**: Readable configuration, immutable config object

**Pattern 3: Observer Pattern**
- **Why**: Notify external systems of state changes
- **Where**: CircuitBreakerListener interface
- **How**: List of listeners, notify on events
- **Benefit**: Loose coupling, extensible monitoring

**Pattern 4: Proxy Pattern** (implicit)
- **Why**: Wrap service calls with circuit breaker logic
- **Where**: `call(Supplier<T>)` method
- **How**: Execute supplier within circuit breaker context
- **Benefit**: Transparent to client, easy integration

### State Machine

```
State Transition Diagram:

     ┌──────────────────────────────────────────┐
     │                                          │
     │                                          │
     ▼                                          │
┌─────────┐         threshold reached      ┌────▼───┐
│ CLOSED  │────────────────────────────────►│  OPEN  │
│         │                                 │        │
└─────────┘                                 └────┬───┘
     ▲                                           │
     │                                           │
     │ success                                   │ timeout
     │ threshold                                 │ elapsed
     │ reached                                   │
     │                                           ▼
     │                                      ┌─────────┐
     └──────────────────────────────────────│  HALF   │
                                            │  OPEN   │
                                            └────┬────┘
                                                 │
                                                 │ any
                                                 │ failure
                                                 │
                                                 └──────►back to OPEN
```

### Thread Safety

**Approach: Lock-Free with Atomic Operations**

```java
// State management
private final AtomicReference<CircuitBreakerState> state;

// Counters
private final AtomicInteger failureCount;
private final AtomicInteger successCount;
private final AtomicLong lastFailureTime;

// Benefits:
// - No locks needed
// - High concurrency
// - No deadlocks
// - Compare-and-swap operations
```

**Race Condition Handling**:
```java
// Multiple threads may read same state
CircuitBreakerState currentState = state.get();

// But atomic operations ensure consistency
failureCount.incrementAndGet();  // Thread-safe increment

// State transitions use atomic update
state.getAndSet(CircuitBreakerState.OPEN);  // Atomic swap
```

### Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|-----------------|-------|
| call() | O(1) | Constant time state check |
| recordSuccess() | O(1) | Atomic increment |
| recordFailure() | O(1) | Atomic increment + state check |
| State transition | O(1) | Atomic reference swap |

**Memory**: O(1) - Fixed size regardless of traffic

---

## 6️⃣ Deep Dives

### Topic 1: State Transition Logic

**Critical Decision Points**:

1. **CLOSED → OPEN**:
```java
if (failureCount >= threshold) {
    transitionToOpen();
}
```
- Triggers: Consecutive failures
- Purpose: Protect downstream service
- Fast-fail: Stop sending requests immediately

2. **OPEN → HALF_OPEN**:
```java
if (now - lastFailure >= timeout) {
    transitionToHalfOpen();
}
```
- Triggers: Timeout expiration
- Purpose: Test if service recovered
- Automatic: No manual intervention needed

3. **HALF_OPEN → CLOSED**:
```java
if (successCount >= successThreshold) {
    transitionToClosed();
}
```
- Triggers: Successful test requests
- Purpose: Resume normal operation
- Gradual: Require multiple successes

4. **HALF_OPEN → OPEN**:
```java
// ANY failure in HALF_OPEN
recordFailure() → transitionToOpen();
```
- Triggers: Single test failure
- Purpose: Service not recovered
- Conservative: Don't risk overwhelming service

**Why This Design?**

- **Conservative**: Better to be cautious with recovery
- **Fast-fail**: Quickly detect and respond to failures
- **Automatic**: No manual reset required
- **Gradual**: Test recovery before full traffic

### Topic 2: Configuration Trade-offs

**Failure Threshold**:
```java
failureThreshold = 5  // Default
```

**Too Low** (e.g., 2):
- ❌ False positives: Temporary glitches trigger opening
- ❌ Chatty: Circuit opens frequently
- ✅ Fast protection: Quick response to failures

**Too High** (e.g., 20):
- ✅ Fewer false positives
- ❌ Slow protection: Many failures before opening
- ❌ More load on failing service

**Recommendation**: 3-10 based on service criticality

**Timeout Duration**:
```java
timeout = Duration.ofSeconds(60)  // Default
```

**Too Short** (e.g., 5s):
- ✅ Quick recovery attempts
- ❌ May test before service recovered
- ❌ Unstable: Frequent HALF_OPEN attempts

**Too Long** (e.g., 5min):
- ✅ Service has time to recover
- ❌ Slow recovery: Users wait longer
- ❌ May miss quick recoveries

**Recommendation**: 30-120s for most services

**Success Threshold**:
```java
successThreshold = 2  // Default
```

**Purpose**: Confirm recovery before resuming full traffic

**Too Low** (1):
- ❌ One lucky success may not indicate recovery
- ❌ Risk reopening too early

**Too High** (10):
- ✅ Very confident service recovered
- ❌ Slow recovery: More test requests needed
- ❌ May block legitimate traffic

**Recommendation**: 2-5 successes

### Topic 3: Edge Cases

**Edge Case 1: Concurrent State Transitions**
```java
// Thread 1: Records 5th failure
failureCount.incrementAndGet();  // Now 5
if (failures >= threshold) {
    transitionToOpen();  // Sets state to OPEN
}

// Thread 2: Simultaneously records failure
failureCount.incrementAndGet();  // Now 6
if (failures >= threshold) {
    transitionToOpen();  // Already OPEN, no-op
}
```

**Solution**: Atomic operations + idempotent transitions

**Edge Case 2: Timeout Race Condition**
```java
// Thread 1: Checks timeout
if (shouldAttemptReset()) {  // Returns true
    transitionToHalfOpen();   // Sets HALF_OPEN
}

// Thread 2: Simultaneously checks
if (shouldAttemptReset()) {  // Also true!
    transitionToHalfOpen();   // Idempotent, stays HALF_OPEN
}
```

**Solution**: Atomic state updates prevent issues

**Edge Case 3: Listener Exception**
```java
notifyListeners(l -> l.onStateChange(old, new));

// If listener throws exception:
try {
    action.accept(listener);
} catch (Exception e) {
    // Log and continue - don't fail circuit breaker
}
```

**Solution**: Catch and log listener exceptions

**Edge Case 4: Zero Threshold**
```java
if (threshold < 1) {
    throw new IllegalArgumentException("Threshold must be positive");
}
```

**Solution**: Validation in configuration builder

### Topic 4: Integration Patterns

**Pattern 1: Service Wrapper**
```java
public class ResilientServiceClient {
    private final ServiceClient client;
    private final Circuit
