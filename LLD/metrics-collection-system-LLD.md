# Metrics Collection System - Low-Level Design

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [API Design](#api-design)
5. [Lock-Free Writes](#lock-free-writes)
6. [Snapshot Isolation](#snapshot-isolation)
7. [Implementation Approaches](#implementation-approaches)
8. [Memory Optimization](#memory-optimization)
9. [Edge Cases](#edge-cases)
10. [Extensibility](#extensibility)

---

## Understanding the Problem

### What is a Metrics Collection System?

A metrics collection system records and aggregates application metrics for monitoring, alerting, and analysis. It must handle:
- **High Write Throughput**: Millions of metric updates per second
- **Low Latency**: < 1ms per write operation
- **Snapshot Export**: Periodic export of current metrics without blocking writes
- **Multiple Metric Types**: Counters, gauges, timers, histograms

### Real-World Examples
- **Prometheus**: Time-series database with pull-based metrics
- **Datadog**: Cloud monitoring with agent-based collection
- **AWS CloudWatch**: Managed metrics service
- **Grafana**: Visualization and dashboarding
- **Dropwizard Metrics**: Java metrics library

### Core Challenge

The main challenge is achieving **high write throughput** while maintaining **snapshot consistency**:

```
Thread 1: counter.increment()  // Write operation
Thread 2: counter.increment()  // Write operation
Thread 3: counter.increment()  // Write operation
Thread 4: snapshot()           // Read all metrics - must not block writes!
```

Traditional locks would:
- ❌ Block all writes during snapshot
- ❌ Create contention on shared counters
- ❌ Limit throughput

Solution requires:
- ✅ Lock-free writes using LongAdder
- ✅ Snapshot isolation with copy-on-write
- ✅ Eventual consistency model

---

## Requirements

### Functional Requirements

1. **Metric Types**
   - Counter: Monotonically increasing value (requests, errors)
   - Gauge: Point-in-time value (memory usage, active connections)
   - Timer: Duration measurements (latency, response time)
   - Histogram: Distribution of values

2. **Metric Operations**
   - Record metric value
   - Increment/decrement counter
   - Update gauge
   - Record timer duration
   - Get current value

3. **Snapshot Export**
   - Export all metrics at point in time
   - Don't block ongoing writes
   - Consistent view of all metrics
   - Periodic scheduled exports

### Non-Functional Requirements

1. **High Throughput**: Handle 1M+ writes per second
2. **Low Latency**: < 1ms write operations
3. **Lock-Free**: No blocking on write path
4. **Low Memory**: Efficient storage of millions of metrics
5. **Snapshot Isolation**: Consistent snapshots without blocking writes
6. **Eventual Consistency**: Writes eventually visible in snapshots

### Out of Scope

- Persistent storage
- Time-series database integration
- Metric aggregation across multiple nodes
- Real-time querying
- Alerting/anomaly detection

---

## Core Entities and Relationships

### 1. **MetricsCollector** (Main Orchestrator)
Central registry managing all metrics.

**Responsibilities:**
- Register/create metrics
- Coordinate snapshot exports
- Manage metric lifecycle

### 2. **Counter**
Monotonically increasing value.

**Characteristics:**
- Only increases (never decreases)
- Lock-free increments using LongAdder
- Example: request_count, error_count

### 3. **Gauge**
Point-in-time value that can go up or down.

**Characteristics:**
- Can increase or decrease
- Last value wins
- Example: memory_usage, active_connections

### 4. **Timer**
Measures duration of operations.

**Characteristics:**
- Records multiple durations
- Calculates statistics (min, max, avg, p95, p99)
- Internally uses histogram

### 5. **Histogram**
Distribution of values.

**Characteristics:**
- Tracks value distribution
- Calculates percentiles
- Uses buckets for efficiency

### 6. **MetricSnapshot**
Immutable point-in-time view of all metrics.

**Characteristics:**
- Copy-on-write semantics
- Doesn't block writers
- Consistent view

---

## API Design

### Core Interface

```java
public interface MetricsCollector {
    /**
     * Get or create a counter metric
     */
    Counter counter(String name);
    
    /**
     * Get or create a gauge metric
     */
    Gauge gauge(String name);
    
    /**
     * Get or create a timer metric
     */
    Timer timer(String name);
    
    /**
     * Export snapshot of all metrics
     */
    MetricSnapshot snapshot();
    
    /**
     * Schedule periodic snapshot exports
     */
    void scheduleSnapshot(long intervalMs, SnapshotConsumer consumer);
}
```

### Counter API

```java
public interface Counter {
    /**
     * Increment by 1
     */
    void increment();
    
    /**
     * Increment by delta
     */
    void increment(long delta);
    
    /**
     * Get current count
     */
    long getCount();
}
```

### Timer API

```java
public interface Timer {
    /**
     * Record duration in milliseconds
     */
    void record(long durationMs);
    
    /**
     * Time a code block
     */
    <T> T time(Supplier<T> operation);
    
    /**
     * Get statistics
     */
    TimerStats getStats();
}
```

### Usage Examples

```java
MetricsCollector metrics = new LockFreeMetricsCollector();

// Counter usage
Counter requests = metrics.counter("http.requests");
requests.increment();

// Gauge usage
Gauge memoryUsage = metrics.gauge("memory.used");
memoryUsage.set(Runtime.getRuntime().totalMemory());

// Timer usage
Timer latency = metrics.timer("http.latency");
latency.time(() -> {
    // Expensive operation
    return processRequest();
});

// Snapshot export
MetricSnapshot snapshot = metrics.snapshot();
System.out.println("Total requests: " + snapshot.getCounter("http.requests"));
```

---

## Lock-Free Writes

### Challenge: Contention on Shared Counters

**Problem:**
```java
// Multiple threads updating same counter
AtomicLong counter = new AtomicLong(0);

// High contention - all threads compete for same variable
counter.incrementAndGet();  // Thread 1
counter.incrementAndGet();  // Thread 2  
counter.incrementAndGet();  // Thread 3
```

Under high contention, AtomicLong performance degrades due to CAS retries.

### Solution: LongAdder

**LongAdder** maintains multiple cells, reducing contention:

```java
LongAdder counter = new LongAdder();

// Different threads update different cells - no contention!
counter.increment();  // Thread 1 → Cell 1
counter.increment();  // Thread 2 → Cell 2
counter.increment();  // Thread 3 → Cell 3

// Sum all cells on read
long total = counter.sum();  // Cell 1 + Cell 2 + Cell 3
```

**Key Benefits:**
- ✅ Multiple threads write to different cells
- ✅ No CAS contention
- ✅ 10x-100x faster under high contention
- ✅ Eventually consistent reads

**Trade-offs:**
- ❌ Slightly higher memory (multiple cells)
- ❌ sum() is not atomic snapshot
- ❌ Best for high contention scenarios

### LongAdder Internals

```
LongAdder
├── base: long (low contention path)
└── cells: Cell[] (high contention path)
    ├── Cell 1: value
    ├── Cell 2: value
    └── Cell 3: value

increment():
1. Try to update base with CAS
2. If CAS fails → allocate cell and update it
3. Each thread gets its own cell (thread-local)

sum():
1. Read base
2. Sum all cell values
3. Return total
```

---

## Snapshot Isolation

### Challenge: Consistent Snapshots Without Blocking

**Problem:** Need consistent view of all metrics without stopping writes

**Naive Approach (Blocks Writers):**
```java
synchronized (metricsLock) {
    // Blocks all writers!
    return new MetricSnapshot(
        counters.clone(),
        gauges.clone(),
        timers.clone()
    );
}
```

**Lock-Free Approach (Copy-on-Write):**
```java
// 1. Take snapshot of metric registry (fast)
Map<String, Counter> counterSnapshot = 
    new ConcurrentHashMap<>(counters);

// 2. Read each metric value (lock-free)
Map<String, Long> counterValues = counterSnapshot.entrySet()
    .stream()
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> e.getValue().getCount()  // LongAdder.sum()
    ));

return new MetricSnapshot(counterValues);
```

### Copy-on-Write Pattern

**Strategy:** Create new collection for snapshot, leave original for writers

```java
class MetricsRegistry {
    // Writers use this (never blocked)
    private final ConcurrentHashMap<String, Counter> counters;
    
    public MetricSnapshot snapshot() {
        // 1. Shallow copy of map (fast O(n))
        Map<String, Counter> snapshot = new HashMap<>(counters);
        
        // 2. Read values from original counters
        // Writers can continue updating counters
        Map<String, Long> values = snapshot.entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getCount()
            ));
        
        return new MetricSnapshot(values);
    }
}
```

**Benefits:**
- ✅ No locks on write path
- ✅ Snapshot creation doesn't block writers
- ✅ Consistent view at point in time
- ✅ Minimal memory overhead

**Eventual Consistency:**
```
Time    Thread 1        Thread 2        Thread 3
----    --------        --------        --------
T1      inc counter     
T2                      inc counter     
T3                                      snapshot() → 2
T4      inc counter     
T5                                      snapshot() → 3
```

Snapshot at T3 sees 2 increments (eventually consistent).

---

## Implementation Approaches

### Approach 1: LongAdder-Based (Recommended)

**Data Structure:**
```java
class Counter {
    private final LongAdder adder = new LongAdder();
    
    public void increment() {
        adder.increment();  // Lock-free, high performance
    }
    
    public long getCount() {
        return adder.sum();  // Eventually consistent
    }
}
```

**Pros:**
- ✅ Highest write throughput
- ✅ Lock-free increments
- ✅ Scales linearly with threads
- ✅ Low latency

**Cons:**
- ❌ sum() is not atomic
- ❌ Slightly more memory per counter
- ❌ Eventually consistent reads

**Best For:**
- High-throughput counters
- Many concurrent writers
- Monitoring/observability systems

### Approach 2: AtomicLong-Based

**Data Structure:**
```java
class Counter {
    private final AtomicLong counter = new AtomicLong(0);
    
    public void increment() {
        counter.incrementAndGet();  // CAS loop
    }
    
    public long getCount() {
        return counter.get();  // Atomic read
    }
}
```

**Pros:**
- ✅ Atomic reads
- ✅ Lower memory per counter
- ✅ Simple implementation

**Cons:**
- ❌ Contention under high load
- ❌ CAS retries slow down throughput
- ❌ Doesn't scale well

**Best For:**
- Low-to-medium contention
- When atomic reads required
- Memory-constrained environments

### Approach 3: Striped Counter

**Data Structure:**
```java
class StripedCounter {
    private final AtomicLong[] stripes;
    private final int mask;
    
    public StripedCounter(int numStripes) {
        this.stripes = new AtomicLong[numStripes];
        this.mask = numStripes - 1;
        for (int i = 0; i < numStripes; i++) {
            stripes[i] = new AtomicLong(0);
        }
    }
    
    public void increment() {
        int index = ThreadLocalRandom.current().nextInt() & mask;
        stripes[index].incrementAndGet();
    }
    
    public long getCount() {
        long sum = 0;
        for (AtomicLong stripe : stripes) {
            sum += stripe.get();
        }
        return sum;
    }
}
```

**Pros:**
- ✅ Reduces contention
- ✅ Middle ground between AtomicLong and LongAdder
- ✅ Predictable memory usage

**Cons:**
- ❌ More complex than AtomicLong
- ❌ Still has some contention
- ❌ Requires tuning stripe count

---

## Memory Optimization

### Challenge: Millions of Metrics

**Problem:** Application has 1M+ unique metrics

**Memory Per Metric:**
```
Counter:
- String name: 40 bytes (average)
- LongAdder: 48 bytes (object header + cells)
- Map entry overhead: 32 bytes
Total: ~120 bytes per counter

For 1M counters: 120MB
For 10M counters: 1.2GB
```

### Optimization Strategies

#### 1. **Lazy Initialization**
Don't create metric until first use:
```java
public Counter counter(String name) {
    return counters.computeIfAbsent(name, k -> new Counter());
}
```

#### 2. **String Interning**
Reuse metric name strings:
```java
public Counter counter(String name) {
    String internedName = name.intern();  // Reuse string
    return counters.computeIfAbsent(internedName, k -> new Counter());
}
```

#### 3. **Metric Expiration**
Remove unused metrics:
```java
class ExpiringMetric {
    private final Counter counter;
    private volatile long lastAccessTime;
    
    public void increment() {
        counter.increment();
        lastAccessTime = System.currentTimeMillis();
    }
    
    public boolean isExpired(long ttlMs) {
        return System.currentTimeMillis() - lastAccessTime > ttlMs;
    }
}
```

#### 4. **Compression in Snapshots**
Use efficient serialization:
```java
// Instead of full metric names, use IDs
Map<Integer, Long> compressedSnapshot = new HashMap<>();
compressedSnapshot.put(metricNameToId.get("http.requests"), count);
```

---

## Edge Cases

### 1. Snapshot During High Write Load
**Scenario:** Snapshot called while 100K writes/sec happening

**Solution:** 
- LongAdder cells isolate writers
- Snapshot reads cells without blocking
- Some writes may not be in snapshot (eventual consistency)

### 2. Metric Name Conflicts
**Scenario:** Two threads create same metric simultaneously

**Solution:** ConcurrentHashMap.computeIfAbsent() ensures single instance
```java
public Counter counter(String name) {
    return counters.computeIfAbsent(name, k -> new Counter());
}
```

### 3. Counter Overflow
**Scenario:** Counter exceeds Long.MAX_VALUE

**Solution:**
- Use modulo arithmetic (wraps around)
- Monitor for overflow with separate metric
- Reset counters periodically

### 4. Memory Leak from Unused Metrics
**Scenario:** Metrics created but never removed

**Solution:**
- Implement TTL-based expiration
- Periodic cleanup of old metrics
- Soft references for rarely-used metrics

### 5. Snapshot Export Failure
**Scenario:** Export consumer throws exception

**Solution:**
- Try-catch around export
- Log error but continue collection
- Queue failed snapshots for retry

### 6. High Cardinality Metrics
**Scenario:** Metrics with userId in name (millions of unique names)

**Solution:**
- Warn on high cardinality
- Limit max unique metric names
- Use tags instead of names

---

## Extensibility

### Future Extensions

#### 1. **Distributed Metrics**
Aggregate metrics across multiple servers:
```java
class DistributedMetricsCollector {
    private final List<MetricsCollector> nodeCollectors;
    
    public MetricSnapshot aggregateSnapshot() {
        return nodeCollectors.stream()
            .map(MetricsCollector::snapshot)
            .reduce(MetricSnapshot::merge)
            .orElse(MetricSnapshot.empty());
    }
}
```

#### 2. **Metric Tags**
Add dimensions to metrics:
```java
Counter requests = metrics.counter("http.requests")
    .tag("endpoint", "/api/users")
    .tag("method", "GET")
    .tag("status", "200");
```

#### 3. **Percentile Calculations**
Add histogram-based percentiles:
```java
class Histogram {
    private final long[] buckets;
    
    public double percentile(double p) {
        // Calculate p-th percentile
    }
}
```

#### 4. **Metric Alerting**
Trigger alerts on thresholds:
```java
metrics.counter("errors")
    .alert(count -> count > 100, "High error rate");
```

#### 5. **Custom Exporters**
Support multiple export formats:
```java
interface MetricExporter {
    void export(MetricSnapshot snapshot);
}

class PrometheusExporter implements MetricExporter {
    public void export(MetricSnapshot snapshot) {
        // Export in Prometheus format
    }
}
```

---

## Design Patterns Used

1. **Registry Pattern**: Central registry for all metrics
2. **Copy-on-Write**: Snapshot isolation
3. **Lazy Initialization**: Create metrics on demand
4. **Strategy Pattern**: Different metric types
5. **Observer Pattern**: Snapshot consumers

---

## Performance Characteristics

| Metric Type | Write Latency | Memory/Metric | Contention | Read Consistency |
|------------|---------------|---------------|------------|------------------|
| LongAdder Counter | < 1μs | 48 bytes | None | Eventual |
| AtomicLong Counter | 1-10μs | 16 bytes | High | Strong |
| Striped Counter | < 5μs | 128 bytes | Low | Eventual |
| Gauge | < 1μs | 16 bytes | Medium | Strong |
| Timer | 10-50μs | 256 bytes | Low | Eventual |

**Recommended:** LongAdder for counters, AtomicReference for gauges

---

## Interview Tips

1. **Explain LongAdder**: Why it's better than AtomicLong under contention
2. **Copy-on-Write**: How snapshots don't block writers
3. **Eventual Consistency**: Trade-off for high throughput
4. **Memory Overhead**: Discuss optimization strategies
5. **Scalability**: How system handles millions of metrics
6. **Real-World Use Cases**: Prometheus, Datadog, CloudWatch
7. **Trade-offs**: Consistency vs performance

### Common Interview Questions

Q: Why use LongAdder instead of AtomicLong?
A: LongAdder uses multiple cells to reduce contention, achieving 10-100x better performance under high load

Q: How do you take snapshots without blocking writes?
A: Copy-on-write: copy the metric registry, then read values. Writers continue using original registry

Q: What's the memory overhead per metric?
A: ~50-150 bytes per metric depending on type. Can optimize with lazy init and string interning

Q: How do you handle eventual consistency?
A: Acceptable trade-off for monitoring. Snapshot may miss recent writes, but overall trends are accurate

Q: How would you scale this to distributed system?
A: Each node collects locally, central aggregator merges snapshots periodically

---

## References

- Dropwizard Metrics: https://metrics.dropwizard.io/
- Prometheus Architecture: https://prometheus.io/docs/introduction/overview/
- Java LongAdder: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/LongAdder.html
- Copy-on-Write Pattern: https://en.wikipedia.org/wiki/Copy-on-write
- High-Performance Metrics: Practical Guide to System Metrics
