# Concurrency & Threading Primitives - Comprehensive LLD Guide

## Table of Contents
1. [Introduction to Concurrency Primitives](#introduction-to-concurrency-primitives)
2. [Synchronization Mechanisms](#synchronization-mechanisms)
3. [Atomic Operations & Lock-Free Programming](#atomic-operations--lock-free-programming)
4. [Thread-Safe Data Structures](#thread-safe-data-structures)
5. [Advanced Locking Strategies](#advanced-locking-strategies)
6. [Machine Coding Examples](#machine-coding-examples)
7. [Production Patterns](#production-patterns)
8. [Interview Questions & Solutions](#interview-questions--solutions)

---

## Introduction to Concurrency Primitives

### What Are Concurrency Primitives?

Concurrency primitives are low-level building blocks for writing thread-safe code. They provide mechanisms for:
- **Mutual Exclusion:** Ensuring only one thread accesses a resource
- **Coordination:** Allowing threads to communicate and synchronize
- **Atomic Operations:** Operations that complete without interruption
- **Memory Visibility:** Ensuring changes are visible across threads

### Java Concurrency Hierarchy

```
Low Level (Hardware)
    ↓
Atomic Variables (CAS operations)
    ↓
Locks & Synchronization (synchronized, ReentrantLock)
    ↓
Concurrent Collections (ConcurrentHashMap, BlockingQueue)
    ↓
Executors & Thread Pools
    ↓
High-Level Abstractions (CompletableFuture, Streams)
```

---

## Synchronization Mechanisms

### 1. synchronized Keyword

**How It Works:**
- Acquires intrinsic lock (monitor) on object
- Only one thread can hold the lock at a time
- Automatically releases on exception

**Example 1: Synchronized Method**
```java
class Counter {
    private int count = 0;
    
    // Locks on 'this' object
    public synchronized void increment() {
        count++;
    }
    
    // Equivalent to:
    public void incrementEquivalent() {
        synchronized(this) {
            count++;
        }
    }
}
```

**Example 2: Synchronized Block (Finer Granularity)**
```java
class BankAccount {
    private double balance = 0;
    private final Object lock = new Object();
    
    public void deposit(double amount) {
        // Only synchronize critical section
        validate(amount); // No lock needed
        
        synchronized(lock) {
            balance += amount; // Lock only here
        }
        
        logTransaction(amount); // No lock needed
    }
}
```

**When to Use:**
- ✅ Simple mutual exclusion
- ✅ Short critical sections
- ✅ When you don't need advanced features

**Limitations:**
- ❌ Cannot interrupt waiting threads
- ❌ Cannot timeout on lock acquisition
- ❌ No try-lock functionality
- ❌ Must acquire/release in same method

### 2. ReentrantLock

**Advantages over synchronized:**
- Interruptible lock acquisition
- Timed lock acquisition (tryLock with timeout)
- Try-lock (non-blocking attempt)
- Fair/unfair lock policies
- Condition variables

**Example 1: Basic Usage**
```java
class AdvancedCounter {
    private int count = 0;
    private final ReentrantLock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock(); // MUST be in finally!
        }
    }
    
    // Try-lock with timeout
    public boolean tryIncrement(long timeoutMs) {
        try {
            if (lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                try {
                    count++;
                    return true;
                } finally {
                    lock.unlock();
                }
            }
            return false; // Couldn't acquire lock
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
```

**Example 2: Interruptible Lock**
```java
public void processWithInterruption() throws InterruptedException {
    lock.lockInterruptibly(); // Can be interrupted while waiting
    try {
        // Critical section
        processData();
    } finally {
        lock.unlock();
    }
}
```

### 3. ReadWriteLock

**Concept:** Multiple readers OR one writer (not both)

**Performance:**
- Read-heavy workloads: Much faster than synchronized
- Write-heavy workloads: Similar to synchronized
- Mixed workloads: Usually better than synchronized

**Example: Cache Implementation**
```java
class ThreadSafeCache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    public V get(K key) {
        readLock.lock();
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    public void put(K key, V value) {
        writeLock.lock();
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
    
    // Pattern: Read, upgrade to write if needed
    public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
        // Try read lock first
        readLock.lock();
        try {
            V value = cache.get(key);
            if (value != null) {
                return value;
            }
        } finally {
            readLock.unlock();
        }
        
        // Upgrade to write lock
        writeLock.lock();
        try {
            // Double-check (another thread might have added it)
            V value = cache.get(key);
            if (value == null) {
                value = mappingFunction.apply(key);
                cache.put(key, value);
            }
            return value;
        } finally {
            writeLock.unlock();
        }
    }
}
```

### 4. StampedLock (Java 8+)

**Optimistic Read:** Try read without lock, validate later

```java
class Point {
    private double x, y;
    private final StampedLock lock = new StampedLock();
    
    public void move(double deltaX, double deltaY) {
        long stamp = lock.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    public double distanceFromOrigin() {
        long stamp = lock.tryOptimisticRead(); // Optimistic read
        double currentX = x;
        double currentY = y;
        
        if (!lock.validate(stamp)) { // Check if write occurred
            stamp = lock.readLock(); // Upgrade to read lock
            try {
                currentX = x;
                currentY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }
}
```

---

## Atomic Operations & Lock-Free Programming

### Compare-and-Swap (CAS)

**Hardware Primitive:** Atomic operation supported by CPU

```java
// Pseudo-code of CAS
boolean compareAndSwap(int* location, int expectedValue, int newValue) {
    if (*location == expectedValue) {
        *location = newValue;
        return true;
    }
    return false;
}
```

### AtomicInteger - Lock-Free Counter

```java
class LockFreeCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    
    // Lock-free increment
    public int increment() {
        while (true) {
            int current = count.get();
            int next = current + 1;
            if (count.compareAndSet(current, next)) {
                return next;
            }
            // CAS failed, retry (spin)
        }
    }
    
    // Or use built-in method
    public int incrementSimple() {
        return count.incrementAndGet();
    }
    
    // Atomic add and get
    public int addAndGet(int delta) {
        return count.addAndGet(delta);
    }
    
    // Update with function (Java 8+)
    public int doubleValue() {
        return count.updateAndGet(v -> v * 2);
    }
}
```

### AtomicReference - Lock-Free Object Updates

```java
class LockFreeStack<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;
        
        Node(T value) {
            this.value = value;
        }
    }
    
    private final AtomicReference<Node<T>> head = new AtomicReference<>();
    
    public void push(T value) {
        Node<T> newNode = new Node<>(value);
        while (true) {
            Node<T> currentHead = head.get();
            newNode.next = currentHead;
            if (head.compareAndSet(currentHead, newNode)) {
                return; // Success
            }
            // CAS failed, retry
        }
    }
    
    public T pop() {
        while (true) {
            Node<T> currentHead = head.get();
            if (currentHead == null) {
                return null; // Stack is empty
            }
            Node<T> newHead = currentHead.next;
            if (head.compareAndSet(currentHead, newHead)) {
                return currentHead.value; // Success
            }
            // CAS failed, retry
        }
    }
}
```

### AtomicLong - High-Performance Counter

```java
class Metrics {
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    
    public void recordRequest(long latencyMs, boolean success) {
        requestCount.incrementAndGet();
        totalLatency.addAndGet(latencyMs);
        
        if (!success) {
            errorCount.incrementAndGet();
        }
    }
    
    public double getAverageLatency() {
        long requests = requestCount.get();
        return requests == 0 ? 0.0 : (double) totalLatency.get() / requests;
    }
    
    public double getErrorRate() {
        long requests = requestCount.get();
        return requests == 0 ? 0.0 : (double) errorCount.get() / requests;
    }
}
```

### AtomicReferenceFieldUpdater - Advanced

**Use case:** Avoid object overhead when you have many instances

```java
class Node {
    volatile Node next; // Must be volatile
    
    private static final AtomicReferenceFieldUpdater<Node, Node> nextUpdater =
        AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "next");
    
    public boolean casNext(Node expect, Node update) {
        return nextUpdater.compareAndSet(this, expect, update);
    }
}
```

---

## Thread-Safe Data Structures

### 1. ConcurrentHashMap

**Internal Structure:**
- Divided into segments (Java 7) or buckets (Java 8+)
- Lock-free reads (mostly)
- Lock per segment/bucket for writes

**Example: Request Counter**
```java
class RequestCounter {
    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    
    public void recordRequest(String endpoint) {
        counts.computeIfAbsent(endpoint, k -> new AtomicLong(0))
              .incrementAndGet();
    }
    
    public long getCount(String endpoint) {
        AtomicLong counter = counts.get(endpoint);
        return counter == null ? 0 : counter.get();
    }
    
    // Atomic operations
    public void merge(String endpoint, long value) {
        counts.merge(endpoint, new AtomicLong(value), 
            (oldVal, newVal) -> {
                oldVal.addAndGet(newVal.get());
                return oldVal;
            });
    }
}
```

**ConcurrentHashMap vs synchronized HashMap:**
```java
// BAD: Compound operation not atomic
if (!map.containsKey(key)) {
    map.put(key, value);  // Race condition!
}

// GOOD: Use atomic operation
map.putIfAbsent(key, value);

// GOOD: Use computeIfAbsent
map.computeIfAbsent(key, k -> expensiveComputation(k));
```

### 2. CopyOnWriteArrayList

**Concept:** Copy entire array on every write

**When to Use:**
- Read-heavy workloads (1000 reads per 1 write)
- Small lists
- Iteration without ConcurrentModificationException

```java
class EventListeners {
    private final CopyOnWriteArrayList<EventListener> listeners = 
        new CopyOnWriteArrayList<>();
    
    public void addListener(EventListener listener) {
        listeners.add(listener); // Creates new copy
    }
    
    public void removeListener(EventListener listener) {
        listeners.remove(listener); // Creates new copy
    }
    
    public void notifyAll(Event event) {
        // Iteration uses snapshot - no ConcurrentModificationException
        for (EventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}
```

### 3. BlockingQueue - Producer-Consumer

**Implementations:**
- `ArrayBlockingQueue`: Bounded, array-based
- `LinkedBlockingQueue`: Optionally bounded, linked nodes
- `PriorityBlockingQueue`: Unbounded, priority heap
- `SynchronousQueue`: No capacity, direct handoff

```java
class TaskProcessor {
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);
    
    // Producer
    public void submitTask(Task task) throws InterruptedException {
        queue.put(task); // Blocks if full
    }
    
    // Consumer
    public void processTask() throws InterruptedException {
        Task task = queue.take(); // Blocks if empty
        task.execute();
    }
    
    // Try with timeout
    public boolean submitWithTimeout(Task task, long timeoutMs) 
            throws InterruptedException {
        return queue.offer(task, timeoutMs, TimeUnit.MILLISECONDS);
    }
}
```

### 4. ConcurrentLinkedQueue - Non-Blocking

**Lock-free queue using CAS:**

```java
class LockFreeMessageQueue {
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    
    public void enqueue(String message) {
        queue.offer(message); // Never blocks, always succeeds
    }
    
    public String dequeue() {
        return queue.poll(); // Returns null if empty
    }
    
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
```

---

## Advanced Locking Strategies

### 1. Striped Locks (Lock Splitting)

**Concept:** Divide data into stripes, lock per stripe

```java
class StripedCounter {
    private final int stripes = 16;
    private final AtomicLong[] counters = new AtomicLong[stripes];
    private final Lock[] locks = new Lock[stripes];
    
    public StripedCounter() {
        for (int i = 0; i < stripes; i++) {
            counters[i] = new AtomicLong(0);
            locks[i] = new ReentrantLock();
        }
    }
    
    public void increment(String key) {
        int stripe = Math.abs(key.hashCode()) % stripes;
        counters[stripe].incrementAndGet();
    }
    
    public long getTotal() {
        long total = 0;
        for (AtomicLong counter : counters) {
            total += counter.get();
        }
        return total;
    }
}
```

**Performance:**
- 16x better concurrency than single lock
- Reduces contention significantly
- Used in ConcurrentHashMap internally

### 2. Double-Checked Locking (Lazy Initialization)

**Pattern for expensive initialization:**

```java
class ExpensiveResource {
    private static volatile ExpensiveResource instance;
    
    private ExpensiveResource() {
        // Expensive initialization
        initializeResources();
    }
    
    public static ExpensiveResource getInstance() {
        if (instance == null) { // First check (no locking)
            synchronized (ExpensiveResource.class) {
                if (instance == null) { // Second check (with locking)
                    instance = new ExpensiveResource();
                }
            }
        }
        return instance;
    }
}
```

**Why volatile is crucial:**
```java
// Without volatile:
instance = new ExpensiveResource();

// Can be reordered by compiler/CPU to:
1. Allocate memory
2. Set instance pointer (NOW VISIBLE TO OTHER THREADS)
3. Call constructor (NOT YET DONE!)

// Another thread sees non-null instance but uninitialized object!
// volatile prevents this reordering
```

### 3. Lock Ordering (Deadlock Prevention)

**Always acquire locks in consistent order:**

```java
class BankTransfer {
    public void transfer(Account from, Account to, double amount) {
        // Deadlock if two threads transfer in opposite directions!
        
        // SOLUTION: Order by account ID
        Account first = from.getId() < to.getId() ? from : to;
        Account second = first == from ? to : from;
        
        synchronized(first) {
            synchronized(second) {
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }
}
```

### 4. Condition Variables

**More flexible than wait/notify:**

```java
class BoundedBuffer<T> {
    private final Queue<T> buffer = new LinkedList<>();
    private final int capacity;
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    
    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (buffer.size() == capacity) {
                notFull.await(); // Wait on specific condition
            }
            buffer.offer(item);
            notEmpty.signal(); // Wake one waiting consumer
        } finally {
            lock.unlock();
        }
    }
    
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (buffer.isEmpty()) {
                notEmpty.await();
            }
            T item = buffer.poll();
            notFull.signal(); // Wake one waiting producer
            return item;
        } finally {
            lock.unlock();
        }
    }
}
```

---

## Machine Coding Examples

### Example 1: Thread-Safe LRU Cache

```java
class LRUCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, V> cache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<K, V>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }
    
    public V get(K key) {
        lock.readLock().lock();
        try {
            return cache.get(key); // Access order updates
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            cache.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

### Example 2: Rate Limiter with Atomic Operations

```java
class TokenBucketRateLimiter {
    private final AtomicInteger tokens;
    private final int capacity;
    private final ScheduledExecutorService scheduler;
    
    public TokenBucketRateLimiter(int capacity, int refillRate) {
        this.tokens = new AtomicInteger(capacity);
        this.capacity = capacity;
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Refill tokens periodically
        scheduler.scheduleAtFixedRate(this::refill, 1000, 1000, TimeUnit.MILLISECONDS);
    }
    
    private void refill() {
        tokens.updateAndGet(current -> Math.min(capacity, current + refillRate));
    }
    
    public boolean tryAcquire() {
        while (true) {
            int current = tokens.get();
            if (current <= 0) {
                return false; // No tokens available
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true; // Successfully acquired
            }
            // CAS failed, retry
        }
    }
}
```

### Example 3: Thread-Safe Object Pool

```java
class ObjectPool<T> {
    private final ConcurrentLinkedQueue<T> available = new ConcurrentLinkedQueue<>();
    private final AtomicInteger currentSize = new AtomicInteger(0);
    private final int maxSize;
    private final Supplier<T> factory;
    
    public ObjectPool(int maxSize, Supplier<T> factory) {
        this.maxSize = maxSize;
        this.factory = factory;
    }
    
    public T acquire() {
        T obj = available.poll();
        
        if (obj == null && currentSize.get() < maxSize) {
            // Try to create new object
            while (true) {
                int current = currentSize.get();
                if (current >= maxSize) {
                    break; // Another thread created one
                }
                if (currentSize.compareAndSet(current, current + 1)) {
                    return factory.get(); // Successfully created
                }
            }
            
            // Couldn't create, wait for available
            obj = available.poll();
        }
        
        return obj; // May be null if pool exhausted
    }
    
    public void release(T obj) {
        if (obj != null) {
            available.offer(obj);
        }
    }
}
```

### Example 4: Concurrent Event Bus

```java
class EventBus {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<EventListener>> 
        listeners = new ConcurrentHashMap<>();
    
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    public void subscribe(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(listener);
    }
    
    public void unsubscribe(String eventType, EventListener listener) {
        CopyOnWriteArrayList<EventListener> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }
    
    public void publish(String eventType, Event event) {
        CopyOnWriteArrayList<EventListener> list = listeners.get(eventType);
        if (list != null) {
            // Notify all listeners asynchronously
            for (EventListener listener : list) {
                executor.submit(() -> {
                    try {
                        listener.onEvent(event);
                    } catch (Exception e) {
                        // Log error, don't fail other listeners
                    }
                });
            }
        }
    }
}
```

### Example 5: Concurrent Counter with Striping

```java
class HighPerformanceCounter {
    private static final int NUM_STRIPES = 16;
    private final AtomicLong[] counters = new AtomicLong[NUM_STRIPES];
    
    public HighPerformanceCounter() {
        for (int i = 0; i < NUM_STRIPES; i++) {
            counters[i] = new AtomicLong(0);
        }
    }
    
    public void increment() {
        int stripe = getStripe();
        counters[stripe].incrementAndGet();
    }
    
    public long get() {
        long total = 0;
        for (AtomicLong counter : counters) {
            total += counter.get();
        }
        return total;
    }
    
    private int getStripe() {
        // Use thread ID for better distribution
        return (int) (Thread.currentThread().getId() % NUM_STRIPES);
    }
}
```

---

## Production Patterns

### 1. Thread-Safe Lazy Initialization Holder

```java
class DatabaseConnection {
    // Lazy initialization without explicit synchronization
    private static class Holder {
        static final DatabaseConnection INSTANCE = new DatabaseConnection();
    }
    
    private DatabaseConnection() {
        // Expensive initialization
        connectToDatabase();
    }
    
    public static DatabaseConnection getInstance() {
        return Holder.INSTANCE; // Thread-safe, lazy, no synchronization!
    }
}
```

### 2. Volatile for State Flags

```java
class Worker {
    private volatile boolean running = true; // MUST be volatile!
    
    public void work() {
        while (running) { // Another thread can change this
            doWork();
        }
    }
    
    public void stop() {
        running = false; // Visible to worker thread immediately
    }
}
```

**Why volatile:**
```java
// Without volatile, compiler might optimize to:
boolean temp = running;
while (temp) {  // Infinite loop! Never sees update
    doWork();
}
```

### 3. Thread-Safe Builder Pattern

```java
class ThreadSafeBuilder {
    private final AtomicReference<ImmutableConfig> config = 
        new AtomicReference<>();
    
    public static class Builder {
        private String field1;
        private int field2;
        
        public Builder field1(String val) {
            this.field1 = val;
            return this;
        }
        
        public Builder field2(int val) {
            this.field2 = val;
            return this;
        }
        
        public ImmutableConfig build() {
            return new ImmutableConfig(field1, field2);
        }
    }
    
    public void updateConfig(ImmutableConfig newConfig) {
        config.set(newConfig); // Atomic update
    }
    
    public ImmutableConfig getConfig() {
        return config.get(); // Atomic read
    }
}

class ImmutableConfig {
    private final String field1;
    private final int field2;
    
    public ImmutableConfig(String field1, int field2) {
        this.field1 = field1;
        this.field2 = field2;
    }
    
    // Only getters, no setters (immutable)
}
```

### 4. Compare-and-Swap Loop Pattern

```java
class NonBlockingCounter {
    private final AtomicInteger value = new AtomicInteger(0);
    
    // Pattern: CAS loop
    public int addAndGet(int delta) {
        while (true) {
            int current = value.get();
            int next = current + delta;
            if (value.compareAndSet(current, next)) {
                return next;
            }
            // Retry if CAS failed
        }
    }
    
    // More complex example: increment if below threshold
    public boolean incrementIfBelow(int threshold) {
        while (true) {
            int current = value.get();
            if (current >= threshold) {
                return false; // Already at or above threshold
            }
            if (value.compareAndSet(current, current + 1)) {
                return true; // Successfully incremented
            }
            // Retry if CAS failed
        }
    }
}
```

---

## Interview Questions & Solutions

### Q1: Implement a Thread-Safe Singleton

**See Section 4 above for 4 different approaches**

**Best Answer (Bill Pugh):**
```java
class Singleton {
    private Singleton() {}
    
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    
    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

**Why it's thread-safe:**
- Class loading is thread-safe (guaranteed by JVM)
- INSTANCE created only when getInstance() first called (lazy)
- No explicit synchronization needed

### Q2: Print Numbers in Order with N Threads

**Problem:** 3 threads print 1-30, Thread 0 prints 3,6,9..., Thread 1 prints 1,4,7..., Thread 2 prints 2,5,8...

**Solution:**
```java
class NumberPrinter {
    private int current = 1;
    private final int max;
    private final int numThreads;
    
    public synchronized void printNumber(int threadId) {
        while (current <= max) {
            while (current % numThreads != threadId && current <= max) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
            if (current <= max) {
                System.out.println("Thread " + threadId + ": " + current);
                current++;
                notifyAll();
            }
        }
    }
}
```

### Q3: Implement Semaphore from Scratch

**Binary Semaphore (Mutex):**
```java
class BinarySemaphore {
    private boolean available = true;
    
    public synchronized void acquire() throws InterruptedException {
        while (!available) {
            wait();
        }
        available = false;
    }
    
    public synchronized void release() {
        available = true;
        notify();
    }
}
```

**Counting Semaphore:**
```java
class CountingSemaphore {
    private int permits;
    
    public CountingSemaphore(int permits) {
        this.permits = permits;
    }
    
    public synchronized void acquire() throws InterruptedException {
        while (permits == 0) {
            wait();
        }
        permits--;
    }
    
    public synchronized void release() {
        permits++;
        notify();
    }
}
```

### Q4: Thread-Safe Counter - Three Approaches

**Approach 1: synchronized**
```java
class SynchronizedCounter {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
    
    public synchronized int get() {
        return count;
    }
}
```

**Approach 2: ReentrantLock**
```java
class LockedCounter {
    private int count = 0;
    private final Lock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }
}
```

**Approach 3: AtomicInteger (Best Performance)**
```java
class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet(); // Lock-free!
    }
    
    public int get() {
        return count.get(); // No lock needed
    }
}
```

**Performance Comparison:**
- synchronized: ~100ns per operation
- ReentrantLock: ~120ns per operation  
- AtomicInteger: ~20ns per operation (5x faster!)

### Q5: Implement Thread-Safe Bounded Queue

**See BoundedBuffer implementation in Section "Condition Variables"**

### Q6: Fix This Race Condition

```java
// BROKEN CODE:
class BrokenCounter {
    private int count = 0;
    
    public void increment() {
        count++; // NOT ATOMIC! Three operations:
                 // 1. Read count
                 // 2. Add 1
                 // 3. Write back
    }
}

// FIXED VERSION 1: synchronized
class FixedCounter1 {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
}

// FIXED VERSION 2: AtomicInteger
class FixedCounter2 {
    private final AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet();
    }
}
```

---

## Concurrency Patterns in Real LLD Systems

### 1. Rate Limiter (from our LLD examples)

**Token Bucket with CAS:**
```java
class TokenBucket {
    private final AtomicInteger tokens;
    
    public boolean tryConsume() {
        while (true) {
            int current = tokens.get();
            if (current <= 0) {
                return false; // No tokens
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true; // Success
            }
            // CAS failed, retry
        }
    }
    
    public void refill() {
        tokens.updateAndGet(current -> Math.min(capacity, current + 1));
    }
}
```

### 2. Inventory System (from our LLD examples)

**ReadWriteLock for Warehouse:**
```java
class Warehouse {
    private final Map<String, Integer> inventory = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public void addStock(String productId, int quantity) {
        lock.writeLock().lock();
        try {
            inventory.merge(productId, quantity, Integer::sum);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public int getStock(String productId) {
        lock.readLock().lock();
        try {
            return inventory.getOrDefault(productId, 0);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Atomic transfer between warehouses
    public static void transfer(Warehouse from, Warehouse to, 
                                String productId, int quantity) {
        // Lock ordering to prevent deadlock
        Warehouse first = from.hashCode() < to.hashCode() ? from : to;
        Warehouse second = first == from ? to : from;
        
        first.lock.writeLock().lock();
        try {
            second.lock.writeLock().lock();
            try {
                from.removeStock(productId, quantity);
                to.addStock(productId, quantity);
            } finally {
                second.lock.writeLock().unlock();
            }
        } finally {
            first.lock.writeLock().unlock();
        }
    }
}
```

### 3. Circuit Breaker (from our LLD examples)

**Atomic State Transitions:**
```java
class CircuitBreaker {
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    public boolean allowRequest() {
        State currentState = state.get();
        
        if (currentState == State.OPEN) {
            return false; // Circuit open, reject
        }
        
        return true; // Allow request
    }
    
    public void recordFailure() {
        int failures = failureCount.incrementAndGet();
        
        if (failures >= threshold) {
            // Atomic state transition
            state.compareAndSet(State.CLOSED, State.OPEN);
        }
    }
}
```

---

## Memory Models & Happens-Before

### Java Memory Model (JMM)

**Key Concepts:**
1. **Visibility:** Changes made by one thread may not be visible to others
2. **Ordering:** Operations may be reordered by compiler/CPU
3. **Happens-Before:** Guarantees about ordering and visibility

**Happens-Before Rules:**
```java
// Rule 1: Program order
a = 1;
b = 2;
// a = 1 happens-before b = 2 (in same thread)

// Rule 2: Monitor lock
synchronized(lock) {
    x = 1; // Happens-before unlock
}
// unlock happens-before next lock acquisition

// Rule 3: volatile
volatile int v;
v = 42; // Happens-before any subsequent read of v

// Rule 4: Thread start
thread.start(); // Happens-before any action in started thread

// Rule 5: Thread termination
// Actions in thread happen-before thread.join() returns
```

### Example: Visibility Problem

```java
// BROKEN: Without synchronization
class Broken {
    private boolean ready = false;
    private int value = 0;
    
    // Thread 1
    public void writer() {
        value = 42;
        ready = true; // May be reordered before value = 42!
    }
    
    // Thread 2
    public void reader() {
        if (ready) {
            System.out.println(value); // Might print 0!
        }
    }
}

// FIXED: With volatile
class Fixed {
    private volatile boolean ready = false; // Prevents reordering
    private int value = 0;
    
    public void writer() {
        value = 42;
        ready = true; // Happens-before read of ready
    }
    
    public void reader() {
        if (ready) { // Happens-before read of value
            System.out.println(value); // Always prints 42
        }
    }
}
```

---

## Common Pitfalls & Anti-Patterns

### Pitfall 1: Double-Checked Locking Without volatile

```java
// BROKEN
class Broken {
    private static Broken instance; // Missing volatile!
    
    public static Broken getInstance() {
        if (instance == null) {
            synchronized(Broken.class) {
                if (instance == null) {
                    instance = new Broken(); // Partial construction visible!
                }
            }
        }
        return instance;
    }
}
```

### Pitfall 2: Lost Notify

```java
// BROKEN
synchronized(lock) {
    if (condition) {
        lock.notify(); // Only wakes ONE thread
    }
}

// FIXED
synchronized(lock) {
    if (condition) {
        lock.notifyAll(); // Wake ALL waiting threads
    }
}
```

### Pitfall 3: Checking Condition with if Instead of while

```java
// BROKEN
synchronized(lock) {
    if (queue.isEmpty()) { // WRONG! Use while, not if
        lock.wait();
    }
    return queue.poll();
}

// FIXED
synchronized(lock) {
    while (queue.isEmpty()) { // Correct! Recheck after wakeup
        lock.wait();
    }
    return queue.poll();
}
```

### Pitfall 4: Forgetting to Unlock

```java
// BROKEN
public void process() {
    lock.lock();
    // If exception thrown here, lock never released!
    doWork();
    lock.unlock(); // Never reached
}

// FIXED
public void process() {
    lock.lock();
    try {
        doWork();
    } finally {
        lock.unlock(); // Always executed
    }
}
```

---

## Performance Optimization Tips

### 1. Minimize Lock Scope

```java
// BAD: Lock held too long
public synchronized void processRequest(Request req) {
    parseRequest(req);        // 10ms - no lock needed
    updateDatabase(req);      // 100ms - needs lock
    sendResponse(req);        // 20ms - no lock needed
}

// GOOD: Lock only critical section
public void processRequest(Request req) {
    parseRequest(req);        // No lock
    
    synchronized(this) {
        updateDatabase(req);  // Lock only here
    }
    
    sendResponse(req);        // No lock
}
```

### 2. Use Lock-Free When Possible

```java
// Instead of:
class SlowCounter {
    private int count = 0;
    public synchronized void increment() {
        count++;
    }
}

// Use:
class FastCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    public void increment() {
        count.incrementAndGet(); // Much faster!
    }
}
```

### 3. Immutable Objects

```java
// Thread-safe without any synchronization!
class ImmutablePoint {
    private final double x;
    private final double y;
    
    public ImmutablePoint(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    public ImmutablePoint move(double dx, double dy) {
        return new ImmutablePoint(x + dx, y + dy); // New object
    }
    
    // Getters only, no setters
}
```

---

## Summary

### Choosing the Right Primitive

**For Simple Counters:**
- ✅ AtomicInteger/AtomicLong (lock-free, fast)
- ❌ synchronized (slower, but acceptable)

**For Collections:**
- Read-heavy: ConcurrentHashMap, CopyOnWriteArrayList
- Producer-Consumer: BlockingQueue
- Stack/Queue: ConcurrentLinkedQueue (lock-free)

**For Custom Logic:**
- Simple: synchronized
- Advanced: ReentrantLock with Conditions
- Read-heavy: ReadWriteLock
- Optimistic: StampedLock

**For State Flags:**
- ✅ volatile boolean
- ❌ synchronized (overkill)

### Key Takeaways

1. **Atomic variables are fastest** for simple operations
2. **ReadWriteLock for read-heavy** workloads
3. **synchronized is simple** but less flexible
4. **ConcurrentHashMap over Collections.synchronizedMap()**
5. **volatile for visibility** of simple variables
6. **Immutable objects need no synchronization**
7. **Lock ordering prevents deadlock**
8. **Always use finally for unlock()**
9. **Use while not if** for condition checking
10. **notifyAll() safer than notify()**

### Interview Success Strategy

1. **Start simple:** synchronized or AtomicInteger
2. **Explain trade-offs:** Performance vs simplicity
3. **Show thread safety knowledge:** Race conditions, deadlocks
4. **Optimize if asked:** Lock-free, striping, read-write locks
5. **Discuss testing:** How to verify thread safety

---

## Additional Resources

- **Book:** *Java Concurrency in Practice* by Brian Goetz
- **Documentation:** `java.util.concurrent` package
- **Practice:** LeetCode concurrency problems
- **Reference:** Our `ConcurrencyInterviewQuestions.java` file

---

*Last Updated: January 5, 2026*
*Difficulty Level: Advanced*
*Key Focus: Thread Safety, Atomic Operations, Lock-Free Programming*
*Essential for: FAANG interviews, Production systems*
