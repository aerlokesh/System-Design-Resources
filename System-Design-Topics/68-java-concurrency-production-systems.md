# 🎯 Topic 68: Java Concurrency in Production Systems — Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Java concurrency primitives, thread pools, locks, atomic operations, concurrent collections, CompletableFuture, virtual threads, synchronization strategies, deadlock prevention, and how to design high-throughput concurrent systems in Java for production at scale.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Java Memory Model (JMM)](#java-memory-model-jmm)
3. [Thread Lifecycle and Management](#thread-lifecycle-and-management)
4. [Thread Pools and ExecutorService](#thread-pools-and-executorservice)
5. [Synchronization Primitives](#synchronization-primitives)
6. [Lock Implementations](#lock-implementations)
7. [Atomic Operations and CAS](#atomic-operations-and-cas)
8. [Concurrent Collections](#concurrent-collections)
9. [CompletableFuture and Async Patterns](#completablefuture-and-async-patterns)
10. [Virtual Threads (Project Loom)](#virtual-threads-project-loom)
11. [Producer-Consumer Patterns](#producer-consumer-patterns)
12. [Read-Write Lock Strategies](#read-write-lock-strategies)
13. [Deadlock Prevention and Detection](#deadlock-prevention-and-detection)
14. [Thread Safety Patterns](#thread-safety-patterns)
15. [Backpressure and Flow Control](#backpressure-and-flow-control)
16. [Performance Characteristics and Numbers](#performance-characteristics-and-numbers)
17. [Production Anti-Patterns](#production-anti-patterns)
18. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Concurrency** in Java is about managing shared mutable state across multiple threads while maintaining correctness, liveness, and performance. In production systems handling millions of requests, improper concurrency leads to data corruption, deadlocks, thread starvation, and cascading failures.

```
Why Java Concurrency Matters in System Design:

  Single-threaded server:
    1 request at a time → 100ms per request → 10 requests/sec MAX
    
  Multi-threaded server (200 threads):
    200 concurrent requests → 100ms per request → 2,000 requests/sec
    
  Async + Non-blocking (Netty/Virtual Threads):
    10,000 concurrent requests → 100ms per request → 100,000 requests/sec

  Real production numbers:
    Uber: 100K+ concurrent rides → each needs concurrent state management
    Netflix: 200K+ API requests/sec → thread pools, circuit breakers, bulkheads
    Amazon: 400M+ items in cart/day → concurrent inventory updates
    Twitter: 600K+ tweets/sec during peaks → concurrent fan-out processing

  Result: Mastering Java concurrency = building systems that scale.
```

---

## Java Memory Model (JMM)

### Visibility and Ordering

```
The Java Memory Model defines how threads interact through memory:

  Problem: Each CPU core has its own cache:
  
    Thread 1 (Core 1)          Thread 2 (Core 2)
    ┌─────────────┐            ┌─────────────┐
    │ L1 Cache    │            │ L1 Cache    │
    │ flag = true │            │ flag = false │  ← STALE!
    └──────┬──────┘            └──────┬──────┘
           │                          │
    ┌──────┴──────────────────────────┴──────┐
    │            Main Memory                  │
    │            flag = true                  │
    └─────────────────────────────────────────┘
    
  Without proper synchronization:
    Thread 1 writes flag = true
    Thread 2 may NEVER see the update (cached stale value)

  Three guarantees we need:
    1. Visibility: Changes by one thread are seen by others
    2. Atomicity: Operations complete fully or not at all
    3. Ordering: Instructions execute in expected order (no reordering)
```

### Happens-Before Relationship

```java
// Happens-Before guarantees visibility between threads

// Rule 1: Monitor Lock Rule
synchronized (lock) {
    sharedVar = 42;         // Write happens-before...
}
// ... any subsequent lock acquisition by another thread sees 42

// Rule 2: Volatile Variable Rule  
volatile boolean ready = false;
// Thread 1:
data = 42;
ready = true;              // Write to volatile happens-before...
// Thread 2:
if (ready) {               // ... read of same volatile
    // data is GUARANTEED to be 42 here
}

// Rule 3: Thread Start Rule
thread.start();            // Everything before start() is visible to the new thread

// Rule 4: Thread Join Rule
thread.join();             // Everything the thread did is visible after join()

// Rule 5: Final Field Semantics
class Immutable {
    final int value;       // Safely published without synchronization
    Immutable(int v) { this.value = v; }
}
// Any thread reading a properly constructed Immutable sees value correctly
```

### volatile vs synchronized

```
volatile:
  ✅ Guarantees visibility (no caching)
  ✅ Prevents instruction reordering
  ❌ NOT atomic for compound operations (i++ is NOT safe)
  ❌ No mutual exclusion
  Use when: Single writer, multiple readers; flag/status variables

synchronized:
  ✅ Guarantees visibility
  ✅ Guarantees atomicity (mutual exclusion)
  ✅ Prevents reordering
  ❌ Blocks threads (contention = performance hit)
  Use when: Compound operations on shared state; invariants spanning multiple variables

Performance comparison:
  volatile read: ~same as normal read (no cache miss typically)
  volatile write: ~10-100ns (memory barrier cost)
  uncontended synchronized: ~20-50ns (biased locking optimization)
  contended synchronized: ~1-10μs (thread parking/unparking)
  heavily contended: ~10-100μs (context switches, cache invalidation)
```

---

## Thread Lifecycle and Management

### Thread States

```
Thread Lifecycle in Java:

  NEW → RUNNABLE → RUNNING → TERMINATED
         ↕           ↕
      BLOCKED    WAITING/TIMED_WAITING

  NEW: Thread created but not started
  RUNNABLE: Ready to run, waiting for CPU
  RUNNING: Currently executing on a CPU core
  BLOCKED: Waiting to acquire a monitor lock
  WAITING: Waiting indefinitely (wait(), join(), park())
  TIMED_WAITING: Waiting with timeout (sleep(), wait(timeout))
  TERMINATED: Execution completed

Production monitoring:
  Thread dumps show state distribution:
    80% RUNNABLE → healthy (CPU-bound work)
    80% BLOCKED → BAD (lock contention)
    80% WAITING → might be OK (idle pool threads)
    80% TIMED_WAITING → check if stuck in retries
```

### Thread Creation Patterns

```java
// ❌ NEVER do this in production — uncontrolled thread creation
new Thread(() -> processRequest(request)).start();
// Problems: No upper bound, no reuse, no monitoring, OOM risk

// ✅ ALWAYS use thread pools in production
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors() * 2
);
executor.submit(() -> processRequest(request));

// ✅ Even better: Custom thread pool with named threads for debugging
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10,                          // core pool size
    50,                          // max pool size
    60L, TimeUnit.SECONDS,       // idle thread keep-alive
    new LinkedBlockingQueue<>(1000),  // bounded queue
    new ThreadFactoryBuilder()
        .setNameFormat("order-processor-%d")
        .setDaemon(false)
        .setUncaughtExceptionHandler((t, e) -> 
            log.error("Thread {} failed", t.getName(), e))
        .build(),
    new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure strategy
);
```

---

## Thread Pools and ExecutorService

### Production Thread Pool Design

```
Thread Pool Sizing Formula:

  CPU-bound tasks (computation, serialization, encryption):
    threads = number_of_cores (or cores + 1)
    Example: 8-core machine → 8-9 threads
    WHY: More threads = more context switches = slower

  I/O-bound tasks (HTTP calls, DB queries, file I/O):
    threads = number_of_cores × (1 + wait_time / compute_time)
    Example: 8 cores, 100ms wait, 10ms compute → 8 × (1 + 10) = 88 threads
    WHY: While one thread waits for I/O, others can use the CPU

  Mixed workloads:
    Use separate pools for CPU-bound and I/O-bound work (bulkhead pattern)
    Never let slow I/O tasks starve fast CPU tasks

  Real production examples:
    Tomcat default: 200 threads (I/O-bound HTTP handling)
    Netflix Hystrix: 10 threads per downstream service (isolation)
    gRPC server: cores × 2 for request handling
    Kafka consumer: 1 thread per partition (ordering guarantee)
```

### Thread Pool Types and When to Use Each

```java
// 1. FixedThreadPool — Known, bounded workload
// Use: Web server request handlers, batch processing workers
ExecutorService fixed = Executors.newFixedThreadPool(200);
// Internally: new ThreadPoolExecutor(200, 200, 0L, SECONDS, new LinkedBlockingQueue<>())
// ⚠️ DANGER: Unbounded queue! Can cause OOM if tasks arrive faster than processing

// 2. CachedThreadPool — Bursty, short-lived tasks
// Use: Event handlers, async notifications (short tasks only!)
ExecutorService cached = Executors.newCachedThreadPool();
// Internally: new ThreadPoolExecutor(0, MAX_INT, 60L, SECONDS, new SynchronousQueue<>())
// ⚠️ DANGER: Unbounded threads! Can create 10,000+ threads during burst

// 3. ScheduledThreadPool — Periodic/delayed tasks
// Use: Health checks, metrics reporting, cache refresh, retry scheduling
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(4);
scheduled.scheduleAtFixedRate(
    () -> publishMetrics(),
    0, 10, TimeUnit.SECONDS  // every 10 seconds
);

// 4. WorkStealingPool (ForkJoinPool) — Recursive divide-and-conquer
// Use: Parallel stream processing, recursive algorithms
ExecutorService stealing = Executors.newWorkStealingPool();
// Each thread has its own deque; idle threads "steal" from busy threads

// 5. Virtual Thread Pool (Java 21+) — High-concurrency I/O
// Use: Replacing async/reactive code for I/O-bound workloads
ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor();
// Creates a virtual thread per task — millions of concurrent tasks possible

// ✅ PRODUCTION BEST PRACTICE: Custom bounded pool
public static ThreadPoolExecutor createProductionPool(
        String poolName, int coreSize, int maxSize, int queueCapacity) {
    return new ThreadPoolExecutor(
        coreSize,
        maxSize,
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(queueCapacity),  // BOUNDED queue
        new ThreadFactoryBuilder()
            .setNameFormat(poolName + "-%d")
            .setUncaughtExceptionHandler((t, e) -> {
                log.error("[{}] Uncaught exception in thread {}", poolName, t.getName(), e);
                Metrics.counter("thread.uncaught.exception", "pool", poolName).increment();
            })
            .build(),
        (runnable, executor) -> {
            // Custom rejection: log + metrics + optionally block caller
            log.warn("[{}] Task rejected! Queue full. Active: {}, Queue: {}",
                poolName, executor.getActiveCount(), executor.getQueue().size());
            Metrics.counter("thread.pool.rejected", "pool", poolName).increment();
            // CallerRunsPolicy: execute in caller's thread (backpressure)
            if (!executor.isShutdown()) {
                runnable.run();
            }
        }
    );
}
```

### Thread Pool Monitoring

```java
// Production monitoring — expose these metrics
public class ThreadPoolMonitor {
    private final ThreadPoolExecutor pool;
    private final String poolName;
    
    public void reportMetrics() {
        Metrics.gauge("pool.active_threads", poolName, pool.getActiveCount());
        Metrics.gauge("pool.pool_size", poolName, pool.getPoolSize());
        Metrics.gauge("pool.queue_size", poolName, pool.getQueue().size());
        Metrics.gauge("pool.queue_remaining", poolName, pool.getQueue().remainingCapacity());
        Metrics.gauge("pool.completed_tasks", poolName, pool.getCompletedTaskCount());
        Metrics.gauge("pool.largest_pool_size", poolName, pool.getLargestPoolSize());
        
        // Alert if queue utilization > 80%
        double queueUtilization = (double) pool.getQueue().size() / 
            (pool.getQueue().size() + pool.getQueue().remainingCapacity());
        if (queueUtilization > 0.8) {
            alerting.fire("ThreadPool " + poolName + " queue 80% full!");
        }
    }
}
```

---

## Synchronization Primitives

### synchronized Keyword

```java
// Method-level synchronization — locks on 'this' (instance) or Class (static)
public class BankAccount {
    private double balance;
    
    // Locks on 'this' instance — only one thread per account instance
    public synchronized void deposit(double amount) {
        balance += amount;
    }
    
    public synchronized void withdraw(double amount) {
        if (balance >= amount) {
            balance -= amount;
        }
    }
    
    // ⚠️ Problem: Transfer between accounts can DEADLOCK
    // Thread 1: transfer(accountA, accountB) → locks A, then tries to lock B
    // Thread 2: transfer(accountB, accountA) → locks B, then tries to lock A
    public synchronized void transferBroken(BankAccount target, double amount) {
        synchronized (target) { // DEADLOCK RISK!
            this.withdraw(amount);
            target.deposit(amount);
        }
    }
}

// Block-level synchronization — finer-grained control
public class OrderService {
    private final Object inventoryLock = new Object();
    private final Object paymentLock = new Object();
    
    public void processOrder(Order order) {
        // Only lock what's needed, for the shortest time possible
        synchronized (inventoryLock) {
            reserveInventory(order);  // short critical section
        }
        
        // Non-critical work outside lock
        validateShippingAddress(order);
        
        synchronized (paymentLock) {
            chargePayment(order);  // separate lock, no contention with inventory
        }
    }
}
```

### wait() / notify() / notifyAll()

```java
// Classic producer-consumer with wait/notify
public class BoundedBuffer<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;
    
    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }
    
    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) {  // MUST use while, not if (spurious wakeups!)
            wait();  // Release lock, park thread until notified
        }
        queue.add(item);
        notifyAll();  // Wake up ALL waiting consumers (prefer over notify())
    }
    
    public synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();  // Release lock, park thread until notified
        }
        T item = queue.poll();
        notifyAll();  // Wake up ALL waiting producers
        return item;
    }
}

// ⚠️ Why notifyAll() over notify()?
// notify() wakes ONE random thread — if it's the wrong type (producer vs consumer),
// the signal is lost and system can deadlock.
// notifyAll() wakes ALL threads — correct one proceeds, others re-wait.
// Cost: notifyAll() causes "thundering herd" but is SAFE.
```

### Semaphore

```java
// Semaphore — control access to a limited resource pool
public class DatabaseConnectionPool {
    private final Semaphore semaphore;
    private final BlockingQueue<Connection> connections;
    
    public DatabaseConnectionPool(int poolSize) {
        this.semaphore = new Semaphore(poolSize, true);  // fair=true for FIFO ordering
        this.connections = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            connections.add(createConnection());
        }
    }
    
    public Connection acquire(long timeout, TimeUnit unit) throws InterruptedException {
        if (!semaphore.tryAcquire(timeout, unit)) {
            throw new TimeoutException("Connection pool exhausted");
        }
        return connections.poll();  // guaranteed to have one available
    }
    
    public void release(Connection conn) {
        connections.offer(conn);
        semaphore.release();
    }
}

// Production use case: Rate limiting concurrent external API calls
Semaphore apiThrottle = new Semaphore(10);  // max 10 concurrent calls to payment gateway
apiThrottle.acquire();
try {
    paymentGateway.charge(amount);
} finally {
    apiThrottle.release();
}
```

### CountDownLatch and CyclicBarrier

```java
// CountDownLatch — wait for N events to complete (one-shot)
public class ServiceStartup {
    public void startAllServices() throws InterruptedException {
        int serviceCount = 5;
        CountDownLatch latch = new CountDownLatch(serviceCount);
        
        // Start all services in parallel
        executor.submit(() -> { startDatabase(); latch.countDown(); });
        executor.submit(() -> { startCache(); latch.countDown(); });
        executor.submit(() -> { startMessageQueue(); latch.countDown(); });
        executor.submit(() -> { startSearchIndex(); latch.countDown(); });
        executor.submit(() -> { loadFeatureFlags(); latch.countDown(); });
        
        // Wait for ALL services to be ready (with timeout)
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new StartupException("Services failed to start within 30s");
        }
        log.info("All services ready. Accepting traffic.");
    }
}

// CyclicBarrier — synchronize N threads at a common point (reusable)
public class ParallelBatchProcessor {
    private final CyclicBarrier barrier;
    
    public ParallelBatchProcessor(int workerCount) {
        // All workers must reach barrier before ANY proceed
        this.barrier = new CyclicBarrier(workerCount, () -> {
            log.info("All workers completed phase. Merging results...");
            mergePartialResults();
        });
    }
    
    public void processPartition(int partitionId) {
        while (hasMoreBatches()) {
            processBatch(partitionId);
            barrier.await();  // Wait for all partitions to finish this batch
            // Then all proceed to next batch together
        }
    }
}
```

---

## Lock Implementations

### ReentrantLock vs synchronized

```java
// ReentrantLock provides more control than synchronized
public class InventoryService {
    private final ReentrantLock lock = new ReentrantLock(true);  // fair=true
    private final Map<String, Integer> stock = new HashMap<>();
    
    // Advantage 1: tryLock — non-blocking attempt
    public boolean tryReserve(String productId, int quantity) {
        if (lock.tryLock()) {  // Returns immediately if can't acquire
            try {
                int available = stock.getOrDefault(productId, 0);
                if (available >= quantity) {
                    stock.put(productId, available - quantity);
                    return true;
                }
                return false;
            } finally {
                lock.unlock();  // ALWAYS in finally block!
            }
        }
        return false;  // Lock busy — don't wait, return failure
    }
    
    // Advantage 2: tryLock with timeout — bounded waiting
    public boolean reserveWithTimeout(String productId, int quantity) 
            throws InterruptedException {
        if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
            try {
                // Critical section
                return doReserve(productId, quantity);
            } finally {
                lock.unlock();
            }
        }
        // Timeout — service might be degraded
        Metrics.counter("inventory.lock.timeout").increment();
        return false;
    }
    
    // Advantage 3: lockInterruptibly — cancel waiting threads
    public void reserveInterruptible(String productId, int quantity) 
            throws InterruptedException {
        lock.lockInterruptibly();  // Can be interrupted while waiting
        try {
            doReserve(productId, quantity);
        } finally {
            lock.unlock();
        }
    }
    
    // Advantage 4: Condition variables (replaces wait/notify)
    private final Condition stockAvailable = lock.newCondition();
    
    public void waitForStock(String productId, int quantity) throws InterruptedException {
        lock.lock();
        try {
            while (stock.getOrDefault(productId, 0) < quantity) {
                stockAvailable.await();  // Release lock and wait
            }
            doReserve(productId, quantity);
        } finally {
            lock.unlock();
        }
    }
    
    public void restockItem(String productId, int quantity) {
        lock.lock();
        try {
            stock.merge(productId, quantity, Integer::sum);
            stockAvailable.signalAll();  // Wake up waiting threads
        } finally {
            lock.unlock();
        }
    }
}
```

### ReadWriteLock

```java
// ReadWriteLock — multiple readers OR one writer (not both)
public class ConfigurationCache {
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private Map<String, String> config = new HashMap<>();
    
    // Many threads can read simultaneously
    public String getConfig(String key) {
        readLock.lock();
        try {
            return config.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    // Only one thread can write; blocks all readers
    public void updateConfig(Map<String, String> newConfig) {
        writeLock.lock();
        try {
            config = new HashMap<>(newConfig);  // Copy-on-write pattern
        } finally {
            writeLock.unlock();
        }
    }
    
    // Read-heavy workload (95% reads, 5% writes):
    //   With synchronized: all reads serialize → 10K reads/sec
    //   With ReadWriteLock: reads parallel → 200K reads/sec
    //   20x improvement for read-heavy caches!
}
```

### StampedLock (Java 8+)

```java
// StampedLock — optimistic reads for even higher read throughput
public class GeoLocationStore {
    private final StampedLock lock = new StampedLock();
    private double latitude;
    private double longitude;
    
    // Optimistic read — NO locking overhead for reads!
    public double[] getLocation() {
        long stamp = lock.tryOptimisticRead();  // Non-blocking!
        double lat = latitude;
        double lng = longitude;
        if (!lock.validate(stamp)) {
            // A write happened during our read — fall back to read lock
            stamp = lock.readLock();
            try {
                lat = latitude;
                lng = longitude;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return new double[]{lat, lng};
    }
    
    public void updateLocation(double lat, double lng) {
        long stamp = lock.writeLock();
        try {
            this.latitude = lat;
            this.longitude = lng;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    // Performance: Under heavy read contention:
    //   ReentrantReadWriteLock: readers still contend on shared counter
    //   StampedLock optimistic: zero contention for reads (validate is local)
    //   10-50% faster for extreme read-heavy workloads
}
```

---

## Atomic Operations and CAS

### How CAS Works

```
Compare-And-Swap (CAS) — Lock-free concurrency:

  Traditional (lock-based):
    acquire_lock()        ← thread may BLOCK here
    value = read()
    value = value + 1
    write(value)
    release_lock()

  CAS (lock-free):
    loop:
      expected = read()
      desired = expected + 1
      if CAS(expected, desired):  ← atomic CPU instruction
        break                     ← success!
      else:
        retry                     ← another thread changed it, try again

  CPU instruction: CMPXCHG (x86), LL/SC (ARM)
  
  CAS advantages:
    ✅ No blocking — thread never parks/sleeps
    ✅ No context switch overhead
    ✅ No deadlock possible
    ✅ Progress guarantee (at least one thread always makes progress)
    
  CAS disadvantages:
    ❌ Spin-waiting under high contention (CPU waste)
    ❌ ABA problem (value changed A→B→A, CAS thinks unchanged)
    ❌ Only works for single-variable operations
```

### Atomic Classes

```java
// AtomicInteger / AtomicLong — lock-free counters
public class MetricsCollector {
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    
    public void recordRequest(long latencyNanos, boolean success) {
        requestCount.incrementAndGet();           // CAS-based increment
        totalLatencyNanos.addAndGet(latencyNanos); // CAS-based add
        if (!success) {
            errorCount.incrementAndGet();
        }
    }
    
    public double getAverageLatencyMs() {
        long count = requestCount.get();
        if (count == 0) return 0;
        return totalLatencyNanos.get() / count / 1_000_000.0;
    }
    
    public double getErrorRate() {
        long total = requestCount.get();
        if (total == 0) return 0;
        return (double) errorCount.get() / total;
    }
}

// AtomicReference — CAS on object references
public class LockFreeStack<T> {
    private final AtomicReference<Node<T>> top = new AtomicReference<>(null);
    
    public void push(T value) {
        Node<T> newNode = new Node<>(value);
        Node<T> oldTop;
        do {
            oldTop = top.get();
            newNode.next = oldTop;
        } while (!top.compareAndSet(oldTop, newNode));  // CAS loop
    }
    
    public T pop() {
        Node<T> oldTop;
        Node<T> newTop;
        do {
            oldTop = top.get();
            if (oldTop == null) return null;
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop));  // CAS loop
        return oldTop.value;
    }
}

// LongAdder — better than AtomicLong for high-contention counters
public class HighThroughputCounter {
    // AtomicLong: all threads CAS on same memory location → contention
    // LongAdder: each thread updates its own cell → sum on read
    private final LongAdder counter = new LongAdder();
    
    public void increment() {
        counter.increment();  // Near-zero contention!
    }
    
    public long getCount() {
        return counter.sum();  // Slightly stale but eventually consistent
    }
    
    // Performance under 16 threads:
    //   AtomicLong.incrementAndGet(): ~200M ops/sec (CAS retries)
    //   LongAdder.increment(): ~1.5B ops/sec (7x faster!)
}
```

### AtomicStampedReference (ABA Solution)

```java
// Solves the ABA problem with a version stamp
public class LockFreeAccountBalance {
    // Pair of (balance, version) — CAS checks BOTH
    private final AtomicStampedReference<Double> balance;
    
    public LockFreeAccountBalance(double initial) {
        this.balance = new AtomicStampedReference<>(initial, 0);
    }
    
    public boolean withdraw(double amount) {
        int[] stampHolder = new int[1];
        Double current;
        do {
            current = balance.get(stampHolder);
            if (current < amount) return false;
        } while (!balance.compareAndSet(
            current, 
            current - amount,
            stampHolder[0],           // expected stamp
            stampHolder[0] + 1        // new stamp (incremented)
        ));
        return true;
    }
}
```

---

## Concurrent Collections

### ConcurrentHashMap

```java
// ConcurrentHashMap — the workhorse of concurrent Java
public class SessionStore {
    // NOT Collections.synchronizedMap — that locks the ENTIRE map
    // ConcurrentHashMap locks only individual buckets (segments in Java 7, nodes in 8+)
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    
    // Atomic compute operations (Java 8+) — no external locking needed!
    public Session getOrCreateSession(String sessionId, User user) {
        return sessions.computeIfAbsent(sessionId, id -> {
            // This lambda is called atomically for this key
            return new Session(id, user, Instant.now());
        });
    }
    
    public void updateLastAccess(String sessionId) {
        sessions.computeIfPresent(sessionId, (id, session) -> {
            session.setLastAccess(Instant.now());
            return session;
        });
    }
    
    // Atomic merge — increment view count
    public void incrementViewCount(String pageId) {
        sessions.merge(pageId, 1L, Long::sum);
        // If key absent: put(pageId, 1)
        // If key present: put(pageId, existingValue + 1)
        // ALL atomic, no external lock!
    }
    
    // Bulk operations (parallel with threshold)
    public long countActiveSessions() {
        return sessions.reduceValuesToLong(
            100,  // parallelism threshold (parallel if size > 100)
            session -> session.isActive() ? 1L : 0L,
            0L,
            Long::sum
        );
    }
}

// ConcurrentHashMap internal structure (Java 8+):
//   Array of Nodes (bins/buckets)
//   Each bin: linked list (< 8 entries) or red-black tree (≥ 8 entries)
//   Lock: CAS for simple operations; synchronized on bin head for structural changes
//   
//   Concurrency level: effectively unlimited (one thread per bin)
//   Read operations: NEVER block (volatile reads)
//   Write operations: lock only the affected bin
```

### CopyOnWriteArrayList

```java
// CopyOnWriteArrayList — for read-heavy, rarely-modified lists
public class EventListenerRegistry {
    // Every write creates a NEW copy of the underlying array
    // Reads are NEVER blocked (iterate over snapshot)
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();
    
    public void addListener(EventListener listener) {
        listeners.add(listener);  // Expensive: copies entire array
    }
    
    public void removeListener(EventListener listener) {
        listeners.remove(listener);  // Expensive: copies entire array
    }
    
    // Safe iteration — no ConcurrentModificationException ever
    public void fireEvent(Event event) {
        for (EventListener listener : listeners) {  // Iterates over snapshot
            listener.onEvent(event);
        }
    }
    
    // Use when: 
    //   Listeners list changes rarely (add/remove is infrequent)
    //   Events fire constantly (reads dominate)
    //   Example: 5 listeners, 10,000 events/sec → perfect fit
    // Don't use when:
    //   List changes frequently (1000+ writes/sec) → too many copies
}
```

### BlockingQueue Implementations

```java
// ArrayBlockingQueue — fixed capacity, fair ordering optional
BlockingQueue<Task> taskQueue = new ArrayBlockingQueue<>(1000, true);
// Use: Fixed-size task buffer with bounded memory

// LinkedBlockingQueue — optionally bounded, higher throughput
BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>(10_000);
// Use: Producer-consumer with separate head/tail locks (less contention)

// PriorityBlockingQueue — priority ordering
BlockingQueue<Job> priorityQueue = new PriorityBlockingQueue<>(100, 
    Comparator.comparingInt(Job::getPriority));
// Use: Job scheduling where high-priority tasks execute first

// DelayQueue — elements available only after delay expires
DelayQueue<RetryTask> retryQueue = new DelayQueue<>();
// Use: Scheduled retries, rate limiting cooldowns

// SynchronousQueue — zero capacity (direct handoff)
BlockingQueue<Runnable> handoff = new SynchronousQueue<>();
// Use: CachedThreadPool internally uses this; each put() blocks until take()

// LinkedTransferQueue — combines features of all above
TransferQueue<Message> transferQueue = new LinkedTransferQueue<>();
// transfer() blocks until a consumer takes the element (guaranteed delivery)
// Use: When producer must know consumer received the item
```

---

## CompletableFuture and Async Patterns

### Building Async Pipelines

```java
// CompletableFuture — composable async operations
public class OrderProcessingService {
    private final ExecutorService ioPool = createProductionPool("io-pool", 20, 50, 500);
    
    public CompletableFuture<OrderResult> processOrderAsync(Order order) {
        return CompletableFuture
            // Step 1: Validate order (CPU-bound, use common pool)
            .supplyAsync(() -> validateOrder(order))
            
            // Step 2: Check inventory (I/O-bound, use io pool)
            .thenComposeAsync(validOrder -> 
                checkInventory(validOrder), ioPool)
            
            // Step 3: Process payment (I/O-bound, use io pool)
            .thenComposeAsync(inventoryResult -> 
                chargePayment(order, inventoryResult), ioPool)
            
            // Step 4: Create shipment (I/O-bound)
            .thenApplyAsync(paymentResult -> 
                createShipment(order, paymentResult), ioPool)
            
            // Handle errors at any stage
            .exceptionally(throwable -> {
                log.error("Order {} failed: {}", order.getId(), throwable.getMessage());
                compensate(order);  // Saga compensation
                return OrderResult.failed(throwable.getMessage());
            });
    }
    
    // Parallel fan-out: Call multiple services simultaneously
    public CompletableFuture<ProductDetails> getProductDetails(String productId) {
        CompletableFuture<Product> productFuture = 
            CompletableFuture.supplyAsync(() -> productService.get(productId), ioPool);
        CompletableFuture<List<Review>> reviewsFuture = 
            CompletableFuture.supplyAsync(() -> reviewService.getForProduct(productId), ioPool);
        CompletableFuture<PriceInfo> priceFuture = 
            CompletableFuture.supplyAsync(() -> pricingService.getPrice(productId), ioPool);
        CompletableFuture<Inventory> inventoryFuture = 
            CompletableFuture.supplyAsync(() -> inventoryService.check(productId), ioPool);
        
        // Wait for ALL to complete, then combine results
        return CompletableFuture.allOf(productFuture, reviewsFuture, priceFuture, inventoryFuture)
            .thenApply(v -> new ProductDetails(
                productFuture.join(),
                reviewsFuture.join(),
                priceFuture.join(),
                inventoryFuture.join()
            ));
    }
    
    // Timeout pattern
    public CompletableFuture<Response> callWithTimeout(Supplier<Response> supplier) {
        return CompletableFuture.supplyAsync(supplier, ioPool)
            .orTimeout(2, TimeUnit.SECONDS)  // Java 9+
            .exceptionally(ex -> {
                if (ex instanceof TimeoutException) {
                    return Response.timeout();
                }
                return Response.error(ex);
            });
    }
    
    // First-to-complete (racing pattern)
    public CompletableFuture<CacheResult> getFromFastestCache(String key) {
        CompletableFuture<CacheResult> localCache = 
            CompletableFuture.supplyAsync(() -> localCache.get(key));
        CompletableFuture<CacheResult> remoteCache = 
            CompletableFuture.supplyAsync(() -> redisCache.get(key), ioPool);
        
        // Return whichever completes first
        return CompletableFuture.anyOf(localCache, remoteCache)
            .thenApply(result -> (CacheResult) result);
    }
}
```

### Error Handling Patterns

```java
// Retry with exponential backoff using CompletableFuture
public class ResilientService {
    
    public <T> CompletableFuture<T> retryAsync(
            Supplier<CompletableFuture<T>> action, int maxRetries) {
        return retryInternal(action, maxRetries, 0);
    }
    
    private <T> CompletableFuture<T> retryInternal(
            Supplier<CompletableFuture<T>> action, int maxRetries, int attempt) {
        return action.get().exceptionallyCompose(ex -> {
            if (attempt >= maxRetries) {
                return CompletableFuture.failedFuture(ex);
            }
            long delayMs = (long) Math.pow(2, attempt) * 100;  // 100, 200, 400, 800...
            log.warn("Retry {} after {}ms: {}", attempt + 1, delayMs, ex.getMessage());
            
            return CompletableFuture
                .delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                .execute(() -> {})  // delay
                .thenCompose(v -> retryInternal(action, maxRetries, attempt + 1));
        });
    }
    
    // Bulkhead pattern: isolate failures
    public class BulkheadService {
        private final Semaphore paymentSemaphore = new Semaphore(10);
        private final Semaphore inventorySemaphore = new Semaphore(20);
        
        public CompletableFuture<PaymentResult> processPayment(PaymentRequest req) {
            return CompletableFuture.supplyAsync(() -> {
                if (!paymentSemaphore.tryAcquire()) {
                    throw new BulkheadFullException("Payment service at capacity");
                }
                try {
                    return paymentGateway.charge(req);
                } finally {
                    paymentSemaphore.release();
                }
            }, ioPool);
        }
    }
}
```

---

## Virtual Threads (Project Loom)

### Why Virtual Threads Change Everything

```
Traditional Threads (Platform Threads):
  Each thread = 1 OS thread = ~1MB stack memory
  10,000 threads = 10GB memory just for stacks!
  Context switch: 1-10μs (kernel mode)
  Practical limit: ~5,000-10,000 threads per JVM

  Problem for I/O-bound services:
    200 threads × 100ms I/O wait = only 2,000 requests/sec
    Need more threads → can't afford memory
    Solution before Loom: async/reactive (complex code)

Virtual Threads (Java 21+):
  Each virtual thread = ~1KB (dynamically grows)
  1,000,000 virtual threads = ~1GB memory
  Context switch: ~100ns (user mode, no kernel)
  Practical limit: millions per JVM

  Same I/O-bound service:
    1,000,000 virtual threads × 100ms I/O wait = 10,000,000 requests/sec (theoretical)
    With simple blocking code — no reactive/async complexity!
```

### Virtual Threads in Production

```java
// Before Virtual Threads: Reactive / Async (complex)
public Mono<OrderResult> processOrderReactive(Order order) {
    return Mono.fromCallable(() -> validateOrder(order))
        .flatMap(valid -> inventoryClient.check(order))
        .flatMap(inv -> paymentClient.charge(order))
        .flatMap(payment -> shippingClient.createLabel(order))
        .onErrorResume(ex -> Mono.just(OrderResult.failed(ex)))
        .subscribeOn(Schedulers.boundedElastic());
}
// Debugging: stack traces are useless, no breakpoints, hard to reason about

// After Virtual Threads: Simple blocking code (Java 21+)
public OrderResult processOrderSimple(Order order) {
    // This looks blocking but it's on a virtual thread — doesn't block OS thread!
    Order validated = validateOrder(order);
    InventoryResult inv = inventoryClient.check(order);       // "blocks" — actually yields
    PaymentResult payment = paymentClient.charge(order);      // "blocks" — actually yields
    ShipmentLabel label = shippingClient.createLabel(order);  // "blocks" — actually yields
    return OrderResult.success(label);
}
// Debugging: full stack traces, breakpoints work, easy to understand

// Server configuration with virtual threads
public class VirtualThreadServer {
    
    // Option 1: Virtual thread per task executor
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    // Option 2: Spring Boot 3.2+ with virtual threads
    // application.properties: spring.threads.virtual.enabled=true
    
    // Option 3: Tomcat with virtual threads
    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreadCustomizer() {
        return handler -> handler.setExecutor(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }
    
    // Structured Concurrency (Preview in Java 21)
    public OrderResult processWithStructuredConcurrency(Order order) 
            throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // Fan out parallel I/O calls
            var inventoryTask = scope.fork(() -> inventoryService.check(order));
            var priceTask = scope.fork(() -> pricingService.getPrice(order));
            var fraudTask = scope.fork(() -> fraudService.evaluate(order));
            
            scope.join();            // Wait for all
            scope.throwIfFailed();   // Propagate first failure
            
            // All succeeded — combine results
            return new OrderResult(
                inventoryTask.get(),
                priceTask.get(),
                fraudTask.get()
            );
        }
    }
}

// ⚠️ Virtual Thread Pitfalls:
// 1. Don't use synchronized — it "pins" the virtual thread to carrier thread
//    Use ReentrantLock instead
// 2. Don't do CPU-bound work on virtual threads — use platform threads
// 3. Don't pool virtual threads — create new ones (they're cheap)
// 4. ThreadLocal is expensive per virtual thread — use ScopedValue instead
```

---

## Producer-Consumer Patterns

### High-Throughput Event Bus

```java
// Production-grade producer-consumer with batching
public class EventBus {
    private final BlockingQueue<Event> queue;
    private final List<EventConsumer> consumers;
    private final ExecutorService consumerPool;
    private volatile boolean running = true;
    
    public EventBus(int queueCapacity, int consumerCount) {
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.consumers = new ArrayList<>();
        this.consumerPool = Executors.newFixedThreadPool(consumerCount);
        
        for (int i = 0; i < consumerCount; i++) {
            consumerPool.submit(this::consumeLoop);
        }
    }
    
    // Non-blocking publish with backpressure
    public boolean publish(Event event) {
        boolean offered = queue.offer(event);  // Non-blocking
        if (!offered) {
            Metrics.counter("eventbus.dropped").increment();
            return false;  // Queue full — caller handles backpressure
        }
        Metrics.counter("eventbus.published").increment();
        return true;
    }
    
    // Blocking publish with timeout
    public boolean publishWithBackpressure(Event event, Duration timeout) 
            throws InterruptedException {
        return queue.offer(event, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    // Consumer loop with batch draining (reduces contention)
    private void consumeLoop() {
        List<Event> batch = new ArrayList<>(100);
        while (running) {
            try {
                // Wait for first element (with timeout to check shutdown)
                Event first = queue.poll(100, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                
                batch.add(first);
                // Drain up to 99 more without blocking (batch for efficiency)
                queue.drainTo(batch, 99);
                
                // Process batch
                processBatch(batch);
                batch.clear();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // Graceful shutdown
    public void shutdown() {
        running = false;
        consumerPool.shutdown();
        try {
            if (!consumerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                consumerPool.shutdownNow();
                log.warn("EventBus forced shutdown. {} events lost", queue.size());
            }
        } catch (InterruptedException e) {
            consumerPool.shutdownNow();
        }
    }
}
```

### Disruptor Pattern (Ultra-Low-Latency)

```java
// LMAX Disruptor pattern for single-digit microsecond latency
// Used by: LMAX Exchange, financial trading systems
public class RingBufferQueue<T> {
    private final Object[] buffer;
    private final int mask;
    private final AtomicLong writeSequence = new AtomicLong(-1);
    private final AtomicLong readSequence = new AtomicLong(-1);
    
    public RingBufferQueue(int capacity) {
        // Capacity must be power of 2 for bitwise modulo
        this.buffer = new Object[capacity];
        this.mask = capacity - 1;
    }
    
    public boolean offer(T item) {
        long currentWrite = writeSequence.get();
        long nextWrite = currentWrite + 1;
        
        // Check if buffer is full (writer caught up to reader)
        if (nextWrite - readSequence.get() > buffer.length) {
            return false;  // Full — backpressure
        }
        
        buffer[(int)(nextWrite & mask)] = item;  // Bitwise modulo (fast!)
        writeSequence.lazySet(nextWrite);        // Ordered but not volatile (faster)
        return true;
    }
    
    @SuppressWarnings("unchecked")
    public T poll() {
        long currentRead = readSequence.get();
        long nextRead = currentRead + 1;
        
        if (nextRead > writeSequence.get()) {
            return null;  // Empty
        }
        
        T item = (T) buffer[(int)(nextRead & mask)];
        readSequence.lazySet(nextRead);
        return item;
    }
    
    // Performance: 100M+ ops/sec single producer/single consumer
    // vs BlockingQueue: 10-20M ops/sec
    // Key techniques: 
    //   - Power-of-2 capacity (bitwise modulo)
    //   - lazySet (weaker memory ordering, avoids fence)
    //   - No object allocation on hot path
    //   - Cache-line padding to prevent false sharing
}
```

---

## Read-Write Lock Strategies

### Cache with Refresh

```java
// Production cache with background refresh using ReadWriteLock
public class RefreshableCache<K, V> {
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService refresher = Executors.newScheduledThreadPool(2);
    private final Function<K, V> loader;
    private final Duration ttl;
    
    public RefreshableCache(Function<K, V> loader, Duration ttl) {
        this.loader = loader;
        this.ttl = ttl;
        
        // Background refresh thread
        refresher.scheduleAtFixedRate(this::refreshExpired, 
            ttl.toMillis() / 2, ttl.toMillis() / 2, TimeUnit.MILLISECONDS);
    }
    
    public V get(K key) {
        rwLock.readLock().lock();
        try {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.getValue();
            }
        } finally {
            rwLock.readLock().unlock();
        }
        
        // Cache miss or expired — load with write lock
        rwLock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.getValue();
            }
            
            V value = loader.apply(key);
            cache.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
            return value;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    private void refreshExpired() {
        cache.forEach((key, entry) -> {
            if (entry.isNearExpiry()) {  // Refresh before actual expiry
                try {
                    V newValue = loader.apply(key);
                    rwLock.writeLock().lock();
                    try {
                        cache.put(key, new CacheEntry<>(newValue, Instant.now().plus(ttl)));
                    } finally {
                        rwLock.writeLock().unlock();
                    }
                } catch (Exception e) {
                    log.warn("Failed to refresh key {}: {}", key, e.getMessage());
                    // Keep stale value — better than no value
                }
            }
        });
    }
}
```

---

## Deadlock Prevention and Detection

### Deadlock Scenarios and Solutions

```java
// Scenario 1: Lock Ordering Violation
// PROBLEM:
class TransferService {
    void transfer(Account from, Account to, double amount) {
        synchronized (from) {           // Thread 1 locks A
            synchronized (to) {         // Thread 1 waits for B (Thread 2 holds B)
                from.debit(amount);
                to.credit(amount);
            }
        }
    }
}
// Thread 1: transfer(A, B) — locks A, waits for B
// Thread 2: transfer(B, A) — locks B, waits for A → DEADLOCK!

// SOLUTION 1: Consistent lock ordering
class TransferServiceFixed {
    void transfer(Account from, Account to, double amount) {
        // Always lock in consistent order (by ID)
        Account first = from.getId() < to.getId() ? from : to;
        Account second = from.getId() < to.getId() ? to : from;
        
        synchronized (first) {
            synchronized (second) {
                from.debit(amount);
                to.credit(amount);
            }
        }
    }
}

// SOLUTION 2: tryLock with timeout
class TransferServiceTryLock {
    private final Map<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();
    
    boolean transfer(Account from, Account to, double amount) throws InterruptedException {
        ReentrantLock fromLock = accountLocks.computeIfAbsent(from.getId(), 
            id -> new ReentrantLock());
        ReentrantLock toLock = accountLocks.computeIfAbsent(to.getId(), 
            id -> new ReentrantLock());
        
        // Try to acquire both locks with timeout
        boolean gotFrom = fromLock.tryLock(100, TimeUnit.MILLISECONDS);
        if (!gotFrom) return false;  // Couldn't get lock — retry later
        
        try {
            boolean gotTo = toLock.tryLock(100, TimeUnit.MILLISECONDS);
            if (!gotTo) return false;  // Release first lock, retry
            
            try {
                from.debit(amount);
                to.credit(amount);
                return true;
            } finally {
                toLock.unlock();
            }
        } finally {
            fromLock.unlock();
        }
    }
}

// SOLUTION 3: Lock-free with CAS (single object)
class LockFreeBalance {
    private final AtomicLong balance;
    
    boolean debit(long amount) {
        long current;
        do {
            current = balance.get();
            if (current < amount) return false;
        } while (!balance.compareAndSet(current, current - amount));
        return true;
    }
}
```

### Deadlock Detection in Production

```java
// Production deadlock detection
public class DeadlockDetector {
    private final ScheduledExecutorService detector = 
        Executors.newSingleThreadScheduledExecutor();
    
    public void startMonitoring() {
        detector.scheduleAtFixedRate(() -> {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            
            if (deadlockedThreads != null) {
                ThreadInfo[] threadInfos = threadBean.getThreadInfo(deadlockedThreads, true, true);
                
                StringBuilder sb = new StringBuilder("DEADLOCK DETECTED!\n");
                for (ThreadInfo info : threadInfos) {
                    sb.append(String.format("Thread: %s (state: %s)\n", 
                        info.getThreadName(), info.getThreadState()));
                    sb.append(String.format("  Waiting for: %s\n", info.getLockName()));
                    sb.append(String.format("  Held by: %s\n", info.getLockOwnerName()));
                    for (StackTraceElement ste : info.getStackTrace()) {
                        sb.append(String.format("    at %s\n", ste));
                    }
                }
                
                log.error(sb.toString());
                alerting.fireCritical("DEADLOCK", sb.toString());
                // In some systems: kill and restart the affected threads
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
}
```

---

## Thread Safety Patterns

### Immutability (Best Thread Safety)

```java
// Immutable objects are ALWAYS thread-safe — no synchronization needed
public final class PriceQuote {
    private final String symbol;
    private final BigDecimal price;
    private final Instant timestamp;
    private final Map<String, String> metadata;
    
    public PriceQuote(String symbol, BigDecimal price, Instant timestamp, 
                      Map<String, String> metadata) {
        this.symbol = symbol;
        this.price = price;
        this.timestamp = timestamp;
        this.metadata = Map.copyOf(metadata);  // Defensive copy — immutable
    }
    
    // Only getters — no setters, no state mutation
    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, String> getMetadata() { return metadata; }
    
    // To "modify": create a new instance
    public PriceQuote withPrice(BigDecimal newPrice) {
        return new PriceQuote(symbol, newPrice, Instant.now(), metadata);
    }
}

// Java 16+ Records — immutable by default
public record OrderEvent(
    String orderId,
    String customerId,
    OrderStatus status,
    Instant timestamp,
    List<String> items  // ⚠️ Must ensure list is unmodifiable!
) {
    public OrderEvent {
        items = List.copyOf(items);  // Compact constructor — defensive copy
    }
}
```

### Thread-Local Storage

```java
// ThreadLocal — each thread gets its own copy (no sharing = no contention)
public class RequestContext {
    private static final ThreadLocal<RequestInfo> context = new ThreadLocal<>();
    
    public static void setRequestInfo(RequestInfo info) {
        context.set(info);
    }
    
    public static RequestInfo getRequestInfo() {
        return context.get();
    }
    
    public static void clear() {
        context.remove();  // CRITICAL in thread pools! Prevents memory leaks
    }
}

// Usage in web server filter
public class RequestContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
        try {
            RequestContext.setRequestInfo(new RequestInfo(
                extractTraceId(req),
                extractUserId(req),
                Instant.now()
            ));
            chain.doFilter(req, resp);
        } finally {
            RequestContext.clear();  // ALWAYS clear in finally — thread returns to pool!
        }
    }
}

// ⚠️ ThreadLocal pitfalls in production:
// 1. Memory leaks in thread pools (thread reuse with stale data)
// 2. Doesn't propagate to child threads (use InheritableThreadLocal)
// 3. Doesn't work with virtual threads (use ScopedValue in Java 21+)
// 4. Testing difficulty (state invisible from outside)
```

### Double-Checked Locking (Lazy Initialization)

```java
// Lazy singleton initialization — thread-safe
public class ExpensiveResourceHolder {
    private volatile ExpensiveResource instance;  // volatile is REQUIRED!
    
    public ExpensiveResource getInstance() {
        ExpensiveResource result = instance;  // Local variable for performance
        if (result == null) {                 // First check (no lock)
            synchronized (this) {
                result = instance;
                if (result == null) {         // Second check (with lock)
                    instance = result = createExpensiveResource();
                }
            }
        }
        return result;
    }
    
    // Better alternative: Initialization-on-demand holder (lazy + thread-safe)
    private static class Holder {
        static final ExpensiveResource INSTANCE = createExpensiveResource();
        // Class loading guarantees thread safety — no synchronization needed!
    }
    
    public static ExpensiveResource getInstanceHolder() {
        return Holder.INSTANCE;  // Loaded only on first call
    }
}
```

---

## Backpressure and Flow Control

### Reactive Streams Backpressure

```java
// Backpressure in producer-consumer systems
public class BackpressureAwarePublisher {
    private final BlockingQueue<Event> buffer;
    private final AtomicLong demand = new AtomicLong(0);
    
    // Strategy 1: Drop oldest (lossy)
    public void publishDropOldest(Event event) {
        if (!buffer.offer(event)) {
            buffer.poll();           // Remove oldest
            buffer.offer(event);     // Add newest
            Metrics.counter("backpressure.dropped").increment();
        }
    }
    
    // Strategy 2: Block producer (lossless)
    public void publishBlocking(Event event) throws InterruptedException {
        buffer.put(event);  // Blocks if full — backpressures producer
    }
    
    // Strategy 3: Reject with signal (let caller decide)
    public boolean publishTryOffer(Event event) {
        boolean accepted = buffer.offer(event);
        if (!accepted) {
            Metrics.counter("backpressure.rejected").increment();
        }
        return accepted;  // Caller decides: retry, drop, or slow down
    }
    
    // Strategy 4: Rate-limited publishing
    private final RateLimiter rateLimiter = RateLimiter.create(10_000);  // 10K events/sec
    
    public void publishRateLimited(Event event) {
        rateLimiter.acquire();  // Blocks if exceeding rate
        buffer.offer(event);
    }
}

// Flow API (Java 9+) for reactive streams with backpressure
public class EventProcessor implements Flow.Subscriber<Event> {
    private Flow.Subscription subscription;
    private final int batchSize = 100;
    
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(batchSize);  // Request initial batch
    }
    
    @Override
    public void onNext(Event event) {
        processEvent(event);
        subscription.request(1);  // Request next item after processing
        // This creates natural backpressure — publisher can't overwhelm subscriber
    }
    
    @Override
    public void onError(Throwable throwable) {
        log.error("Stream error", throwable);
    }
    
    @Override
    public void onComplete() {
        log.info("Stream completed");
    }
}
```

---

## Performance Characteristics and Numbers

```
Java Concurrency Performance Reference:

Operation                              Time          Notes
─────────────────────────────────────────────────────────────────
Thread creation (platform)             1-10μs        OS thread allocation
Thread creation (virtual)              ~1μs          Just an object on heap
Context switch (platform)              1-10μs        Kernel mode switch
Context switch (virtual)               ~100ns        User mode, no kernel
─────────────────────────────────────────────────────────────────
synchronized (uncontended)             20-50ns       Biased locking
synchronized (contended)               1-10μs        Park/unpark + cache miss
ReentrantLock (uncontended)            20-50ns       Similar to synchronized
ReentrantLock.tryLock()                20-30ns       Non-blocking check
StampedLock (optimistic read)          ~5ns          Just reads a stamp
─────────────────────────────────────────────────────────────────
volatile read                          ~1ns          Same as normal read (usually)
volatile write                         ~10-100ns     Memory barrier
CAS (AtomicLong.incrementAndGet)       10-50ns       Uncontended
CAS (AtomicLong, 16 threads)           200-500ns     Contention + retries
LongAdder.increment (16 threads)       ~30ns         Per-thread cells
─────────────────────────────────────────────────────────────────
ConcurrentHashMap.get()                ~50ns         Volatile read of node
ConcurrentHashMap.put()                100-500ns     CAS or sync on bucket
BlockingQueue.offer()                  100-300ns     Uncontended
BlockingQueue.put() (full)             blocks        Thread parks
─────────────────────────────────────────────────────────────────
CompletableFuture creation             ~100ns        Object allocation
CompletableFuture.supplyAsync()        1-5μs         Thread scheduling overhead
Thread.sleep(1ms)                      1-15ms        OS timer granularity!
Object.wait(1ms)                       1-15ms        Same timer granularity
LockSupport.parkNanos(1ms)             1-15ms        Same timer granularity
─────────────────────────────────────────────────────────────────

Throughput numbers (16-core machine):
  Single-threaded:                     ~10M operations/sec (simple ops)
  ConcurrentHashMap (16 threads):      ~100M reads/sec, ~20M writes/sec
  AtomicLong (16 threads):             ~200M increments/sec
  LongAdder (16 threads):              ~1.5B increments/sec
  ArrayBlockingQueue (P/C):            ~10-20M transfers/sec
  Disruptor ring buffer:               ~100-200M transfers/sec
  
Production thread pool sizing:
  Tomcat default:                      200 threads
  Netflix (per downstream service):    10-20 threads (bulkhead)
  LMAX Exchange:                       1 thread (single-threaded disruptor!)
  Elasticsearch:                       cores × 3 + 1 (for bulk operations)
  Kafka consumer:                      1 thread per partition
```

---

## Production Anti-Patterns

### Common Mistakes in Production

```java
// ❌ Anti-Pattern 1: Unbounded thread creation
public void handleRequest(Request req) {
    new Thread(() -> process(req)).start();  // Creates thread per request
    // 10,000 requests → 10,000 threads → OOM, scheduling nightmare
}

// ❌ Anti-Pattern 2: Unbounded queue (silent memory bomb)
ExecutorService executor = Executors.newFixedThreadPool(10);
// Internally uses LinkedBlockingQueue with Integer.MAX_VALUE capacity!
// If tasks arrive faster than processing → queue grows → OOM after hours/days

// ❌ Anti-Pattern 3: Holding locks during I/O
synchronized (lock) {
    result = httpClient.call(externalService);  // 100ms+ holding lock!
    // All other threads blocked for 100ms+ → performance catastrophe
}

// ❌ Anti-Pattern 4: Not handling InterruptedException properly
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    // WRONG: swallowing the interrupt
    // Thread pool can't shut down gracefully!
}
// ✅ CORRECT:
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // Restore interrupt flag
    return;  // Or throw
}

// ❌ Anti-Pattern 5: Synchronizing on wrong object
private Integer count = 0;
synchronized (count) {  // WRONG! Integer is immutable — reference changes on ++
    count++;  // Now synchronized on OLD object, new threads get NEW object
}

// ❌ Anti-Pattern 6: Check-then-act without atomicity
if (map.containsKey(key)) {     // Thread A checks
    // Thread B removes key here
    map.get(key).process();      // Thread A gets null → NPE!
}
// ✅ CORRECT:
map.computeIfPresent(key, (k, v) -> { v.process(); return v; });

// ❌ Anti-Pattern 7: Using Thread.stop() / Thread.suspend()
thread.stop();   // Deprecated! Leaves objects in inconsistent state
thread.suspend(); // Deprecated! Can cause deadlocks
// ✅ Use interrupt flag and cooperative cancellation

// ❌ Anti-Pattern 8: Forgetting to close/shutdown ExecutorService
ExecutorService pool = Executors.newFixedThreadPool(10);
// Never shut down → JVM won't exit! Non-daemon threads keep JVM alive
// ✅ Always shutdown in finally or shutdown hook:
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    pool.shutdown();
    try { pool.awaitTermination(30, TimeUnit.SECONDS); }
    catch (InterruptedException e) { pool.shutdownNow(); }
}));
```

---

## Interview Talking Points & Scripts

### "How do you handle concurrency in production?"

```
SCRIPT:

"In production Java services, I follow a layered approach to concurrency:

First, I prefer IMMUTABILITY — immutable objects need no synchronization. 
We use Java records and unmodifiable collections extensively.

Second, for shared mutable state, I choose the lightest primitive that's correct:
- ConcurrentHashMap for concurrent maps (computeIfAbsent for atomic updates)
- AtomicLong/LongAdder for counters (CAS-based, lock-free)
- volatile for simple flags (visibility without mutual exclusion)
- ReentrantLock with tryLock for critical sections with timeout
- synchronized only when I need wait/notify semantics

Third, I design around THREAD POOLS with bounded queues and named threads.
We never create raw threads. Every pool has monitoring for queue depth, 
active count, and rejection rate.

Fourth, for I/O-bound workloads with Java 21+, we use virtual threads —
simple blocking code that doesn't consume OS threads during I/O waits.

Finally, we test concurrency with:
- JCStress for correctness verification
- JMH for performance benchmarking
- Thread dump analysis for production debugging
- Deadlock detection scheduled every 10 seconds"
```

### "How do you avoid deadlocks?"

```
SCRIPT:

"We prevent deadlocks through four strategies:

1. LOCK ORDERING: When multiple locks are needed, we always acquire them in 
   a consistent order (e.g., by entity ID). This is enforced in code review.

2. TIMEOUT: We use tryLock with timeout instead of indefinite lock acquisition.
   If we can't get the lock in 100ms, we fail fast and retry.

3. LOCK-FREE: Where possible, we use CAS operations (AtomicReference, 
   ConcurrentHashMap.computeIfAbsent) that can't deadlock by definition.

4. DETECTION: We run a background thread that calls 
   ThreadMXBean.findDeadlockedThreads() every 10 seconds. If detected, 
   we alert immediately and dump thread stacks.

In practice, most deadlocks we've seen came from:
- Database locks (solved with SELECT FOR UPDATE with NOWAIT)
- Nested synchronized blocks (solved with single lock + immutable state)
- Callback ordering (solved with async event-driven patterns)"
```

---

## Summary Cheat Sheet

```
┌─────────────────────────────────────────────────────────────────┐
│         JAVA CONCURRENCY DECISION TREE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Can you make it immutable?                                     │
│    YES → Use final fields, records, unmodifiable collections    │
│          No synchronization needed!                             │
│    NO ↓                                                        │
│                                                                 │
│  Is it a simple counter/flag?                                   │
│    Counter → LongAdder (high contention) or AtomicLong          │
│    Flag → volatile boolean                                      │
│    NO ↓                                                        │
│                                                                 │
│  Is it a concurrent map?                                        │
│    YES → ConcurrentHashMap + computeIfAbsent/merge              │
│    NO ↓                                                        │
│                                                                 │
│  Is it read-heavy (95%+ reads)?                                 │
│    YES → StampedLock (optimistic reads)                        │
│          or CopyOnWriteArrayList (rarely modified lists)        │
│    NO ↓                                                        │
│                                                                 │
│  Do you need timeout/tryLock?                                   │
│    YES → ReentrantLock with tryLock(timeout)                    │
│    NO ↓                                                        │
│                                                                 │
│  Do you need producer-consumer?                                 │
│    YES → LinkedBlockingQueue (bounded!) + thread pool           │
│    NO ↓                                                        │
│                                                                 │
│  Is it I/O-bound with Java 21+?                                │
│    YES → Virtual threads with simple blocking code              │
│    NO ↓                                                        │
│                                                                 │
│  Default → synchronized (simplest, correct, well-understood)    │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  THREAD POOL SIZING:                                            │
│    CPU-bound: cores (or cores + 1)                              │
│    I/O-bound: cores × (1 + wait_time / compute_time)            │
│    Mixed: separate pools (bulkhead pattern)                     │
├─────────────────────────────────────────────────────────────────┤
│  DEADLOCK PREVENTION:                                           │
│    1. Lock ordering (consistent acquisition order)              │
│    2. tryLock with timeout (never wait forever)                 │
│    3. Lock-free (CAS operations where possible)                 │
│    4. Detection + alerting (ThreadMXBean)                       │
├─────────────────────────────────────────────────────────────────┤
│  PRODUCTION ESSENTIALS:                                         │
│    ✅ Bounded queues (never unbounded!)                         │
│    ✅ Named threads (for debugging)                             │
│    ✅ Metrics on every pool (queue size, rejection rate)        │
│    ✅ Graceful shutdown (awaitTermination)                      │
│    ✅ InterruptedException → restore interrupt flag             │
│    ✅ finally blocks for lock.unlock()                          │
│    ❌ Never new Thread() in production                          │
│    ❌ Never hold locks during I/O                               │
│    ❌ Never use Thread.stop()/suspend()                         │
│    ❌ Never ignore InterruptedException                         │
└─────────────────────────────────────────────────────────────────┘
```

---

*This document covers Java concurrency fundamentals for production systems. See Topic 69 for comprehensive real-world examples showing these patterns applied at companies like Netflix, Uber, Amazon, and more.*
