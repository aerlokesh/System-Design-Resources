# 🎯 Topic 6: Multithreading & Concurrency

> **Java Interview — Deep Dive**
> A comprehensive guide covering threads, synchronization, locks, thread pools, concurrent utilities, and how to articulate these with depth in a Java interview.

---

## Table of Contents

1. [Thread Basics — Creating & Running Threads](#thread-basics--creating--running-threads)
2. [Thread Lifecycle & States](#thread-lifecycle--states)
3. [Synchronization — synchronized, volatile](#synchronization--synchronized-volatile)
4. [Locks — ReentrantLock, ReadWriteLock](#locks--reentrantlock-readwritelock)
5. [Thread Communication — wait/notify](#thread-communication--waitnotify)
6. [Thread Pool & ExecutorService](#thread-pool--executorservice)
7. [Callable, Future & CompletableFuture](#callable-future--completablefuture)
8. [Concurrent Collections](#concurrent-collections)
9. [Atomic Classes & CAS](#atomic-classes--cas)
10. [Common Concurrency Problems](#common-concurrency-problems)
11. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
12. [Common Interview Mistakes](#common-interview-mistakes)
13. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Thread Basics — Creating & Running Threads

### Three Ways to Create Threads

```java
// Way 1: Extend Thread class
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("Thread: " + Thread.currentThread().getName());
    }
}
new MyThread().start();

// Way 2: Implement Runnable (PREFERRED)
class MyRunnable implements Runnable {
    @Override
    public void run() {
        System.out.println("Runnable: " + Thread.currentThread().getName());
    }
}
new Thread(new MyRunnable()).start();

// Way 3: Lambda (Java 8+) — MOST PREFERRED
new Thread(() -> System.out.println("Lambda thread")).start();

// Way 4: Callable + Future (returns result)
Callable<Integer> task = () -> 42;
Future<Integer> future = executor.submit(task);
```

### `start()` vs `run()`

```java
Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName()));

t.start();  // ✅ Creates NEW thread, executes run() in that thread
t.run();    // ❌ Calls run() in CURRENT thread — no new thread created!
```

> **Interview Trap**: Calling `run()` directly does NOT create a new thread. You MUST call `start()`.

### Runnable vs Thread

| Feature | extends Thread | implements Runnable |
|---------|---------------|-------------------|
| Inheritance | ❌ Can't extend another class | ✅ Can extend another class |
| Reusability | ❌ Limited | ✅ Can be used with executors |
| Separation of concerns | ❌ Task + thread coupled | ✅ Task separated from thread |
| Recommendation | Avoid | **Preferred** |

---

## Thread Lifecycle & States

```
        ┌────────────────────────────────────────┐
        │                                        │
   NEW ──→ RUNNABLE ──→ RUNNING ──→ TERMINATED  │
        │     ↑   ↓                              │
        │     │  BLOCKED ←→ WAITING              │
        │     │  TIMED_WAITING                   │
        └────────────────────────────────────────┘
```

| State | Description | Triggered By |
|-------|-------------|-------------|
| `NEW` | Thread created but not started | `new Thread()` |
| `RUNNABLE` | Ready to run / running | `start()` |
| `BLOCKED` | Waiting for monitor lock | Entering `synchronized` block |
| `WAITING` | Waiting indefinitely | `wait()`, `join()`, `LockSupport.park()` |
| `TIMED_WAITING` | Waiting with timeout | `sleep(ms)`, `wait(ms)`, `join(ms)` |
| `TERMINATED` | Finished execution | `run()` completes or exception |

### Important Thread Methods

```java
Thread.sleep(1000);          // Pause current thread (does NOT release lock!)
thread.join();               // Wait for thread to finish
thread.join(5000);           // Wait max 5 seconds
Thread.yield();              // Hint to scheduler (rarely used)
thread.interrupt();          // Sets interrupt flag
Thread.currentThread();      // Get current thread reference
thread.setDaemon(true);      // Daemon thread (dies when main exits)
thread.isAlive();            // Check if still running
thread.setPriority(5);       // 1-10 (MIN=1, NORM=5, MAX=10)
```

### Daemon vs User Threads

```java
Thread daemon = new Thread(() -> {
    while (true) { /* background work */ }
});
daemon.setDaemon(true);  // Must set BEFORE start()
daemon.start();
// JVM exits when all USER threads finish — daemon threads are killed
```

---

## Synchronization — synchronized, volatile

### The Problem: Race Condition

```java
class Counter {
    private int count = 0;
    
    // ❌ NOT thread-safe — race condition
    public void increment() {
        count++;  // NOT atomic: read → increment → write
    }
}
```

### synchronized Keyword

```java
class Counter {
    private int count = 0;
    
    // Method-level synchronization (lock on 'this')
    public synchronized void increment() {
        count++;
    }
    
    // Block-level synchronization (finer control)
    public void increment() {
        synchronized (this) {
            count++;
        }
    }
    
    // Static synchronized (lock on Class object)
    public static synchronized void staticMethod() {
        // Lock on Counter.class
    }
}
```

### synchronized Rules

| Feature | Detail |
|---------|--------|
| Lock object | Instance method: `this`, Static method: `Class` object |
| Reentrant | ✅ Same thread can re-enter same lock |
| Fairness | ❌ No fairness guarantee |
| Interruptible | ❌ Can't interrupt waiting thread |
| Try-lock | ❌ No timeout option |

### volatile Keyword

```java
class Flag {
    private volatile boolean running = true;  // Visible to all threads
    
    public void stop() { running = false; }
    
    public void run() {
        while (running) {  // Always reads from main memory
            // work
        }
    }
}
```

### volatile vs synchronized

| Feature | volatile | synchronized |
|---------|----------|-------------|
| Visibility | ✅ | ✅ |
| Atomicity | ❌ (single read/write only) | ✅ |
| Mutual exclusion | ❌ | ✅ |
| Performance | Faster | Slower |
| Use case | Flags, status variables | Compound operations |

> **Interview Key**: `volatile` guarantees visibility but NOT atomicity. `count++` with volatile is still NOT thread-safe because `count++` is three operations (read, increment, write).

---

## Locks — ReentrantLock, ReadWriteLock

### ReentrantLock — Better than synchronized

```java
class Counter {
    private int count = 0;
    private final ReentrantLock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();  // ALWAYS unlock in finally!
        }
    }
    
    // Try-lock with timeout
    public boolean tryIncrement() throws InterruptedException {
        if (lock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                count++;
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;  // Couldn't acquire lock
    }
}
```

### ReentrantLock vs synchronized

| Feature | synchronized | ReentrantLock |
|---------|-------------|---------------|
| Lock/unlock | Automatic | Manual (must unlock in finally) |
| Fairness | No | Configurable (`new ReentrantLock(true)`) |
| Try-lock | No | `tryLock()`, `tryLock(timeout)` |
| Interruptible | No | `lockInterruptibly()` |
| Condition | `wait()`/`notify()` | `newCondition()` |
| Multiple conditions | No | Yes |
| Performance | Similar | Similar (slightly better for contention) |

### ReadWriteLock — Multiple Readers, Single Writer

```java
class Cache<K, V> {
    private final Map<K, V> map = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public V get(K key) {
        rwLock.readLock().lock();    // Multiple readers allowed simultaneously
        try {
            return map.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    public void put(K key, V value) {
        rwLock.writeLock().lock();   // Exclusive — no readers or writers
        try {
            map.put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
```

---

## Thread Communication — wait/notify

```java
class ProducerConsumer {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int MAX_SIZE = 10;
    
    public synchronized void produce(int item) throws InterruptedException {
        while (queue.size() == MAX_SIZE) {
            wait();  // Release lock, wait until notified
        }
        queue.add(item);
        notifyAll();  // Wake up waiting consumers
    }
    
    public synchronized int consume() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();  // Release lock, wait until notified
        }
        int item = queue.poll();
        notifyAll();  // Wake up waiting producers
        return item;
    }
}
```

### wait/notify Rules (Interview Critical)

| Rule | Detail |
|------|--------|
| Must hold lock | `wait()` and `notify()` must be called inside `synchronized` |
| `wait()` releases lock | Thread gives up the lock while waiting |
| `sleep()` does NOT release lock | Thread keeps the lock while sleeping |
| Always use `while` not `if` | Spurious wakeups can happen |
| Prefer `notifyAll()` | `notify()` wakes only one random thread |

---

## Thread Pool & ExecutorService

### Why Thread Pools?

- Creating threads is expensive (~1MB stack per thread)
- Thread pools **reuse** threads
- Control max concurrency
- Queue overflow handling

### Types of Thread Pools

```java
// Fixed thread pool — N threads, unbounded queue
ExecutorService fixed = Executors.newFixedThreadPool(10);

// Cached thread pool — creates threads as needed, reuses idle ones
ExecutorService cached = Executors.newCachedThreadPool();

// Single thread executor — one thread, tasks queued
ExecutorService single = Executors.newSingleThreadExecutor();

// Scheduled thread pool — delayed/periodic tasks
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(5);

// ✅ BEST PRACTICE — Use ThreadPoolExecutor directly
ExecutorService custom = new ThreadPoolExecutor(
    5,                          // core pool size
    10,                         // max pool size
    60, TimeUnit.SECONDS,       // keep-alive for idle threads
    new LinkedBlockingQueue<>(100),  // bounded queue!
    new ThreadPoolExecutor.CallerRunsPolicy()  // rejection policy
);
```

### Rejection Policies

| Policy | Behavior |
|--------|----------|
| `AbortPolicy` (default) | Throws `RejectedExecutionException` |
| `CallerRunsPolicy` | Caller thread executes the task |
| `DiscardPolicy` | Silently drops the task |
| `DiscardOldestPolicy` | Drops oldest queued task, retries |

### Proper Shutdown

```java
executor.shutdown();                    // Graceful — finish running tasks
executor.shutdownNow();                 // Aggressive — interrupt running tasks
executor.awaitTermination(30, TimeUnit.SECONDS);  // Wait for completion
```

---

## Callable, Future & CompletableFuture

### Callable vs Runnable

| Feature | Runnable | Callable |
|---------|----------|----------|
| Return value | void | V (generic) |
| Exception | Can't throw checked | Can throw checked |
| Method | `run()` | `call()` |

### Future

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
Future<Integer> future = executor.submit(() -> {
    Thread.sleep(2000);
    return 42;
});

future.isDone();           // Check if complete
future.cancel(true);       // Cancel (mayInterrupt)
Integer result = future.get();           // Blocking wait
Integer result = future.get(5, TimeUnit.SECONDS);  // Timeout wait
```

### CompletableFuture (Java 8+) — Non-Blocking Async

```java
// Run async task
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return fetchDataFromDB();
});

// Chain operations
CompletableFuture<Integer> result = CompletableFuture
    .supplyAsync(() -> fetchUser("id123"))         // Async fetch
    .thenApply(user -> user.getOrders())           // Transform
    .thenApply(orders -> orders.size())            // Transform again
    .exceptionally(ex -> {                         // Error handling
        logger.error("Failed", ex);
        return 0;
    });

// Combine two futures
CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> "Hello");
CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> "World");

CompletableFuture<String> combined = future1.thenCombine(future2, 
    (s1, s2) -> s1 + " " + s2);  // "Hello World"

// Wait for all
CompletableFuture.allOf(future1, future2, future3).join();

// Wait for any
CompletableFuture.anyOf(future1, future2, future3).join();
```

---

## Concurrent Collections

| Collection | Non-Concurrent | Concurrent Equivalent |
|-----------|---------------|----------------------|
| HashMap | ❌ Not thread-safe | `ConcurrentHashMap` |
| ArrayList | ❌ Not thread-safe | `CopyOnWriteArrayList` |
| TreeMap | ❌ Not thread-safe | `ConcurrentSkipListMap` |
| LinkedList (queue) | ❌ Not thread-safe | `ConcurrentLinkedQueue` |
| PriorityQueue | ❌ Not thread-safe | `PriorityBlockingQueue` |

### ConcurrentHashMap Internals (Java 8+)

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

// Atomic compound operations
map.putIfAbsent("key", 1);
map.computeIfAbsent("key", k -> expensiveComputation(k));
map.merge("key", 1, Integer::sum);  // Atomic increment

// ❌ BAD — Not atomic
if (!map.containsKey("key")) {
    map.put("key", value);  // Race condition between check and put!
}

// ✅ GOOD — Atomic
map.putIfAbsent("key", value);
```

### BlockingQueue — Producer-Consumer Made Easy

```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);

// Producer
queue.put(task);    // Blocks if full
queue.offer(task, 5, TimeUnit.SECONDS);  // Timeout

// Consumer
Task task = queue.take();     // Blocks if empty
Task task = queue.poll(5, TimeUnit.SECONDS);  // Timeout
```

---

## Atomic Classes & CAS

### Compare-And-Swap (CAS)

```java
AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet();     // Atomic i++
counter.getAndIncrement();     // Atomic i++ (returns old value)
counter.compareAndSet(5, 10);  // If value==5, set to 10 (atomic)
counter.addAndGet(5);          // Atomic i += 5

// How CAS works (pseudo-code):
// 1. Read current value
// 2. Compute new value
// 3. If current value hasn't changed → swap to new value
// 4. If changed → retry (spin)
// No lock needed! Uses CPU-level atomic instruction
```

### Atomic Classes

| Class | For |
|-------|-----|
| `AtomicInteger` | Thread-safe int |
| `AtomicLong` | Thread-safe long |
| `AtomicBoolean` | Thread-safe boolean |
| `AtomicReference<T>` | Thread-safe object reference |
| `LongAdder` | High-contention counter (better than AtomicLong) |
| `AtomicStampedReference` | Solves ABA problem |

---

## Common Concurrency Problems

### 1. Deadlock

```java
// Thread 1: lock A → lock B
// Thread 2: lock B → lock A
// → DEADLOCK!

// Prevention:
// 1. Always acquire locks in the same order
// 2. Use tryLock() with timeout
// 3. Avoid nested locks
```

### 2. Livelock

Two threads keep responding to each other without progress (like two people in a hallway).

### 3. Starvation

Low-priority thread never gets CPU because high-priority threads dominate.

### 4. Race Condition

```java
// ❌ Check-then-act (race condition)
if (map.containsKey(key)) {
    return map.get(key);  // Another thread might remove between check and get!
}

// ✅ Use atomic operations
map.computeIfAbsent(key, k -> createValue(k));
```

---

## Interview Talking Points & Scripts

### "Explain the difference between synchronized and ReentrantLock"

> *"Both provide mutual exclusion, but ReentrantLock is more flexible. It supports tryLock() with timeout so threads don't block forever, lockInterruptibly() so waiting threads can be interrupted, fair lock ordering to prevent starvation, and multiple Condition objects for complex coordination. The trade-off is you must manually unlock in a finally block — synchronized handles this automatically."*

### "How does ConcurrentHashMap work?"

> *"In Java 8+, ConcurrentHashMap uses per-bucket locking with CAS operations. Reads are almost entirely lock-free using volatile reads. Writes lock only the specific bucket being modified using synchronized on the bucket's first node. This gives much better concurrency than Hashtable or Collections.synchronizedMap, which lock the entire map. It also provides atomic compound operations like computeIfAbsent and merge."*

### "What causes a deadlock and how do you prevent it?"

> *"Deadlock occurs when two or more threads each hold a lock and wait for a lock held by the other, creating a circular dependency. Four conditions must all be true: mutual exclusion, hold-and-wait, no preemption, and circular wait. To prevent it, I ensure consistent lock ordering, use tryLock with timeout, minimize lock scope, and avoid nested locking where possible."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "volatile makes operations atomic" | volatile only guarantees visibility, not atomicity |
| "sleep() releases the lock" | `sleep()` does NOT release the lock; `wait()` does |
| "Calling run() starts a thread" | `run()` runs in current thread; `start()` creates new thread |
| "Thread pool is always better" | Wrong pool config (unbounded queue) can cause OOM |
| "synchronized is always slower" | Modern JVMs optimize uncontended synchronized very well |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│           CONCURRENCY CHEAT SHEET                           │
├────────────────────────────────────────────────────────────┤
│ Thread Creation: Runnable (preferred) > Thread > Callable   │
│ start() = new thread, run() = same thread                  │
│                                                            │
│ Synchronization:                                            │
│ • synchronized → automatic lock/unlock, reentrant           │
│ • volatile → visibility only, NOT atomicity                 │
│ • ReentrantLock → tryLock, timeout, fair, interruptible     │
│ • ReadWriteLock → multiple readers OR single writer          │
│                                                            │
│ Thread Communication:                                       │
│ • wait() → releases lock + waits (use in while loop!)       │
│ • notify()/notifyAll() → wakes waiting threads              │
│ • sleep() → pauses but KEEPS lock                           │
│                                                            │
│ Thread Pools:                                               │
│ • Use ThreadPoolExecutor with bounded queue                 │
│ • Always set rejection policy                               │
│ • Shutdown gracefully with shutdown() + awaitTermination()  │
│                                                            │
│ Atomic & CAS:                                               │
│ • AtomicInteger/Long → lock-free thread-safe counters       │
│ • CAS = Compare-And-Swap (CPU instruction, no lock)         │
│ • LongAdder → better than AtomicLong for high contention    │
│                                                            │
│ CompletableFuture:                                          │
│ • Non-blocking async with chaining                          │
│ • supplyAsync → thenApply → thenCombine → exceptionally     │
│                                                            │
│ Deadlock Prevention:                                        │
│ • Consistent lock ordering                                  │
│ • tryLock with timeout                                      │
│ • Minimize lock scope and nesting                           │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Java Memory Model & Garbage Collection →](./07-jvm-memory-model-and-garbage-collection.md)
