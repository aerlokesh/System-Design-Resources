# 🎯 LLD Topic 9: Concurrency & Thread Safety

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering thread safety, synchronization, locks, atomic operations, concurrent collections, race conditions, deadlocks, and how to write thread-safe code in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 9: Concurrency \& Thread Safety](#-lld-topic-9-concurrency--thread-safety)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Race Conditions](#race-conditions)
  - [Synchronized Keyword](#synchronized-keyword)
    - [Synchronized Block (Finer Granularity)](#synchronized-block-finer-granularity)
  - [ReentrantLock](#reentrantlock)
    - [ReentrantLock vs Synchronized](#reentrantlock-vs-synchronized)
  - [ReadWriteLock](#readwritelock)
  - [Atomic Variables](#atomic-variables)
  - [Volatile Keyword](#volatile-keyword)
  - [Concurrent Collections](#concurrent-collections)
  - [Deadlocks](#deadlocks)
    - [Deadlock Prevention](#deadlock-prevention)
  - [Thread Safety Strategies](#thread-safety-strategies)
  - [Producer-Consumer Pattern](#producer-consumer-pattern)
  - [Thread Pools](#thread-pools)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [When Designing a Thread-Safe Class](#when-designing-a-thread-safe-class)
    - [When Asked About Deadlocks](#when-asked-about-deadlocks)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Thread safety means that shared mutable state is accessed by multiple threads **without data corruption, race conditions, or unexpected behavior**. In LLD interviews, concurrency shows up in rate limiters, caches, connection pools, counters, and any shared resource.

```
Thread Safety = Correctness when multiple threads access shared data

Three approaches:
  1. DON'T SHARE: Each thread has its own copy (immutability, ThreadLocal)
  2. DON'T MUTATE: Use immutable objects
  3. SYNCHRONIZE: Coordinate access to shared mutable state
```

---

## Race Conditions

```java
// ❌ BAD: Race condition — two threads can withdraw simultaneously
class BankAccount {
    private double balance = 1000;
    
    void withdraw(double amount) {
        if (balance >= amount) {      // Thread A checks: 1000 >= 500 ✓
                                       // Thread B checks: 1000 >= 800 ✓
            balance -= amount;         // Thread A: balance = 500
                                       // Thread B: balance = -300 ← BUG!
        }
    }
}

// The problem: CHECK-THEN-ACT is not atomic
// Between checking and acting, another thread can change the state
```

---

## Synchronized Keyword

```java
// ✅ GOOD: synchronized ensures mutual exclusion
class BankAccount {
    private double balance;
    
    // Only ONE thread can execute this at a time
    synchronized void withdraw(double amount) {
        if (balance >= amount) {
            balance -= amount;
        }
    }
    
    synchronized void deposit(double amount) {
        balance += amount;
    }
    
    synchronized double getBalance() {
        return balance;
    }
}
```

### Synchronized Block (Finer Granularity)

```java
class InventoryService {
    private final Map<String, Integer> stock = new HashMap<>();
    private final Object lock = new Object();  // Dedicated lock object
    
    boolean reserve(String productId, int quantity) {
        synchronized (lock) {  // Lock only the critical section
            int available = stock.getOrDefault(productId, 0);
            if (available >= quantity) {
                stock.put(productId, available - quantity);
                return true;
            }
            return false;
        }
    }
}
```

---

## ReentrantLock

```java
class RateLimiter {
    private final Lock lock = new ReentrantLock();
    private final Map<String, TokenBucket> buckets = new HashMap<>();
    
    boolean allowRequest(String clientId) {
        lock.lock();
        try {
            TokenBucket bucket = buckets.computeIfAbsent(clientId, 
                k -> new TokenBucket(10, 10));  // 10 tokens, 10/sec refill
            return bucket.tryConsume();
        } finally {
            lock.unlock();  // ALWAYS unlock in finally!
        }
    }
}
```

### ReentrantLock vs Synchronized

```
ReentrantLock advantages:
  ✅ tryLock() — non-blocking attempt to acquire
  ✅ lockInterruptibly() — can be interrupted while waiting
  ✅ Fairness option (new ReentrantLock(true))
  ✅ Can lock/unlock in different methods
  
Synchronized advantages:
  ✅ Simpler syntax, less error-prone
  ✅ Automatically releases on exception
  ✅ JVM optimizations (biased locking, lock coarsening)
```

---

## ReadWriteLock

```java
// Multiple readers OR one writer — great for read-heavy workloads
class Cache<K, V> {
    private final Map<K, V> store = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    V get(K key) {
        rwLock.readLock().lock();       // Multiple threads can read simultaneously
        try {
            return store.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    void put(K key, V value) {
        rwLock.writeLock().lock();      // Exclusive access for writes
        try {
            store.put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
```

---

## Atomic Variables

```java
// For simple counters/flags — no locks needed
class HitCounter {
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong[] windowCounts;
    
    void recordHit() {
        totalHits.incrementAndGet();  // Atomic — thread-safe without locks
    }
    
    long getTotalHits() {
        return totalHits.get();
    }
}

// CAS (Compare-And-Swap) operations
class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    
    int incrementAndGet() {
        return count.incrementAndGet();
    }
    
    // Custom atomic operation using CAS
    boolean tryDecrementIfPositive() {
        while (true) {
            int current = count.get();
            if (current <= 0) return false;
            if (count.compareAndSet(current, current - 1)) return true;
            // If CAS fails, another thread changed it — retry
        }
    }
}
```

---

## Volatile Keyword

```java
// volatile guarantees visibility — not atomicity
class ServiceHealthChecker {
    private volatile boolean isHealthy = true;  // All threads see latest value
    
    // Writer thread
    void markUnhealthy() {
        isHealthy = false;  // Immediately visible to all reader threads
    }
    
    // Reader threads
    boolean isServiceHealthy() {
        return isHealthy;  // Reads latest value, not cached copy
    }
}

// volatile is NOT enough for compound operations:
// volatile int count;
// count++;  ← NOT thread-safe! (read + increment + write)
```

---

## Concurrent Collections

```java
// ConcurrentHashMap — thread-safe map (preferred over Collections.synchronizedMap)
Map<String, UserSession> sessions = new ConcurrentHashMap<>();

// Thread-safe compound operations
sessions.computeIfAbsent("user123", k -> new UserSession());
sessions.compute("user123", (key, session) -> {
    session.updateLastAccess();
    return session;
});

// CopyOnWriteArrayList — for read-heavy, write-rare scenarios
List<EventListener> listeners = new CopyOnWriteArrayList<>();

// BlockingQueue — for producer-consumer
BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>(1000);
taskQueue.put(new Task());    // Blocks if full
Task task = taskQueue.take(); // Blocks if empty

// ConcurrentLinkedQueue — non-blocking queue
Queue<LogEntry> logBuffer = new ConcurrentLinkedQueue<>();
```

---

## Deadlocks

```java
// ❌ DEADLOCK: Two threads lock in different order
// Thread 1: lock(A) → lock(B)
// Thread 2: lock(B) → lock(A)

class TransferService {
    void transfer(Account from, Account to, double amount) {
        synchronized (from) {           // Thread 1 locks Account A
            synchronized (to) {          // Thread 1 waits for Account B
                from.withdraw(amount);   // Thread 2 locks Account B
                to.deposit(amount);      // Thread 2 waits for Account A → DEADLOCK!
            }
        }
    }
}

// ✅ FIX: Always lock in consistent order
void transfer(Account from, Account to, double amount) {
    Account first = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;
    
    synchronized (first) {
        synchronized (second) {
            from.withdraw(amount);
            to.deposit(amount);
        }
    }
}
```

### Deadlock Prevention

```
1. CONSISTENT ORDERING: Always acquire locks in the same order
2. TIMEOUT: Use tryLock(timeout) instead of blocking forever
3. SINGLE LOCK: Use one coarse-grained lock instead of multiple fine-grained
4. AVOID NESTED LOCKS: Restructure to minimize lock nesting
```

---

## Thread Safety Strategies

```
Strategy 1: IMMUTABILITY (best — no synchronization needed)
  - Use final fields, unmodifiable collections
  - Java Records, Strings, BigDecimal are immutable

Strategy 2: THREAD CONFINEMENT
  - Each thread has its own copy (ThreadLocal)
  - No sharing = no contention

Strategy 3: ATOMIC OPERATIONS
  - AtomicInteger, AtomicLong, AtomicReference
  - Lock-free, high performance for simple operations

Strategy 4: SYNCHRONIZED ACCESS
  - synchronized blocks, ReentrantLock
  - Required for compound operations on shared state

Strategy 5: CONCURRENT DATA STRUCTURES
  - ConcurrentHashMap, CopyOnWriteArrayList, BlockingQueue
  - Built-in thread safety, optimized for specific patterns
```

---

## Producer-Consumer Pattern

```java
class TaskProcessor {
    private final BlockingQueue<Runnable> taskQueue;
    private final ExecutorService workers;
    private volatile boolean running = true;
    
    TaskProcessor(int queueSize, int workerCount) {
        this.taskQueue = new LinkedBlockingQueue<>(queueSize);
        this.workers = Executors.newFixedThreadPool(workerCount);
        
        // Start consumer threads
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::processLoop);
        }
    }
    
    // Producer: submit tasks
    void submit(Runnable task) throws InterruptedException {
        taskQueue.put(task);  // Blocks if queue is full (backpressure)
    }
    
    // Consumer: process tasks
    private void processLoop() {
        while (running) {
            try {
                Runnable task = taskQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    void shutdown() {
        running = false;
        workers.shutdown();
    }
}
```

---

## Thread Pools

```java
// Fixed thread pool — bounded concurrency
ExecutorService pool = Executors.newFixedThreadPool(10);

// Custom thread pool with bounded queue
ExecutorService custom = new ThreadPoolExecutor(
    5,                      // core threads
    20,                     // max threads
    60, TimeUnit.SECONDS,   // idle timeout
    new LinkedBlockingQueue<>(100),  // bounded queue
    new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure
);

// CompletableFuture for async composition
CompletableFuture<User> userFuture = CompletableFuture.supplyAsync(
    () -> userService.findById("123"), pool);

CompletableFuture<List<Order>> ordersFuture = userFuture.thenApplyAsync(
    user -> orderService.findByUser(user), pool);
```

---

## Interview Talking Points & Scripts

### When Designing a Thread-Safe Class

> *"This cache will be accessed by multiple request threads concurrently, so I need thread safety. Since it's read-heavy, I'll use a ReadWriteLock — multiple threads can read simultaneously, but writes get exclusive access. For the hit counter, I'll use AtomicLong since it's a simple increment operation and doesn't need a full lock."*

### When Asked About Deadlocks

> *"Deadlocks happen when two threads hold locks and each waits for the other's lock. I prevent them by always acquiring locks in a consistent order — for example, by sorting accounts by ID before locking. Alternatively, I use tryLock with a timeout so threads give up instead of waiting forever."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Ignoring thread safety in shared resources
   Fix: Any mutable state accessed by multiple threads needs protection.

❌ Mistake 2: Synchronizing everything (over-locking)
   Fix: Only synchronize the CRITICAL SECTION, not entire methods.

❌ Mistake 3: Using synchronized Map instead of ConcurrentHashMap
   Fix: ConcurrentHashMap allows concurrent reads and fine-grained locking.

❌ Mistake 4: Thinking volatile makes compound operations atomic
   Fix: volatile only ensures visibility. Use AtomicInteger for count++.

❌ Mistake 5: Not releasing locks in finally block
   Fix: ALWAYS use try/finally with ReentrantLock.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          CONCURRENCY & THREAD SAFETY CHEAT SHEET              │
├──────────────────┬───────────────────────────────────────────┤
│ synchronized     │ Simple mutual exclusion. Auto-release.     │
│ ReentrantLock    │ tryLock, timeout, fairness. Manual release.│
│ ReadWriteLock    │ Multiple readers OR one writer.             │
│ AtomicInteger    │ Lock-free counters. CAS operations.         │
│ volatile         │ Visibility guarantee only. Not atomic.      │
├──────────────────┼───────────────────────────────────────────┤
│ ConcurrentMap    │ Thread-safe map. computeIfAbsent is atomic.│
│ BlockingQueue    │ Producer-consumer. put() blocks if full.    │
│ CopyOnWriteList  │ Read-heavy, write-rare scenarios.          │
├──────────────────┼───────────────────────────────────────────┤
│ Avoid Deadlock   │ Consistent lock ordering + tryLock timeout │
│ Best Strategy    │ Immutability > Atomics > Locks             │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "I prefer immutability first, atomics for simple counters,    │
│  and ReadWriteLock when I need fine-grained control over a    │
│  read-heavy shared resource."                                 │
└──────────────────────────────────────────────────────────────┘
```
