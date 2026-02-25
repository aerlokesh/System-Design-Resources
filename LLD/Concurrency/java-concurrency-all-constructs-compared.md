# Java Concurrency — Every Construct Compared & Explained

> A single reference that answers: **"What is X, how is it different from Y, and when do I pick which?"**

---

## 🗺️ The Full Map of `java.util.concurrent`

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     JAVA CONCURRENCY UNIVERSE                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  LOCKS & SYNCHRONIZATION          THREAD-SAFE COLLECTIONS              │
│  ─────────────────────            ───────────────────────              │
│  synchronized (keyword)           ConcurrentHashMap                    │
│  ReentrantLock                    ConcurrentSkipListMap                │
│  ReentrantReadWriteLock           ConcurrentLinkedQueue                │
│  StampedLock                      ConcurrentLinkedDeque                │
│  Condition                        CopyOnWriteArrayList                 │
│  volatile (keyword)               CopyOnWriteArraySet                  │
│                                                                         │
│  COORDINATION / SIGNALING         BLOCKING QUEUES                      │
│  ────────────────────────         ──────────────────                   │
│  Semaphore                        ArrayBlockingQueue                   │
│  CountDownLatch                   LinkedBlockingQueue                  │
│  CyclicBarrier                    PriorityBlockingQueue               │
│  Phaser                           DelayQueue                          │
│  Exchanger                        SynchronousQueue                    │
│  LockSupport                      LinkedTransferQueue                 │
│                                                                         │
│  EXECUTORS & THREAD POOLS         ATOMIC VARIABLES                     │
│  ────────────────────────         ────────────────                     │
│  ExecutorService                  AtomicInteger / AtomicLong           │
│  ThreadPoolExecutor               AtomicBoolean                       │
│  ScheduledExecutorService         AtomicReference<V>                   │
│  ForkJoinPool                     AtomicIntegerArray                  │
│  CompletableFuture                AtomicStampedReference              │
│  Future / Callable                LongAdder / LongAccumulator         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 1. LOCKS & SYNCHRONIZATION — Compared

### 1.1 `synchronized` vs `ReentrantLock`

**What is `synchronized`?** A built-in Java keyword that makes a block of code (or entire method) mutually exclusive — only one thread can execute it at a time. When a thread enters a `synchronized` block, it acquires an **intrinsic lock** (also called a **monitor**) on the specified object. Every other thread trying to enter that same block waits until the first thread exits. The lock is automatically released when the block exits, even if an exception is thrown. It's the simplest way to make code thread-safe.

**What is `ReentrantLock`?** An explicit lock from `java.util.concurrent.locks` that does the same thing as `synchronized` — mutual exclusion — but gives you **manual control**. You call `lock()` to acquire and `unlock()` to release. The word "reentrant" means the **same thread** can acquire the lock multiple times without deadlocking itself (it just increments a hold count). The key advantage over `synchronized` is flexibility: you can try to acquire without blocking (`tryLock()`), set timeouts, make it fair (FIFO ordering), and create multiple wait-conditions.

**Why two options?** `synchronized` is sufficient for 90% of cases and is simpler (auto-releases, no `finally` needed). `ReentrantLock` exists for the 10% where you need advanced features like non-blocking attempts, timeouts, or multiple condition variables.

| Feature | `synchronized` | `ReentrantLock` |
|---|---|---|
| **Type** | JVM keyword (intrinsic lock) | Explicit `java.util.concurrent` class |
| **Lock acquisition** | Automatic on method/block entry | Manual: `lock.lock()` |
| **Lock release** | Automatic on exit (even on exception) | Manual: `lock.unlock()` in `finally` |
| **Try-lock (non-blocking)** | ❌ Not possible | ✅ `tryLock()` returns immediately |
| **Timed lock** | ❌ | ✅ `tryLock(5, SECONDS)` |
| **Interruptible** | ❌ Thread blocks forever | ✅ `lockInterruptibly()` |
| **Fair ordering** | ❌ No guarantee | ✅ `new ReentrantLock(true)` |
| **Multiple conditions** | ❌ One wait-set per object | ✅ `lock.newCondition()` — multiple |
| **Performance** | JVM-optimized (biased locking) | Slightly more overhead |
| **Reentrancy** | ✅ Same thread can re-enter | ✅ Same thread can re-enter |
| **Deadlock diagnosis** | Harder | `getHoldCount()`, `isHeldByCurrentThread()` |

```java
// synchronized — simple, safe, less flexible
synchronized (this) {
    count++;
}   // auto-unlocks

// ReentrantLock — powerful, must unlock manually
lock.lock();
try {
    count++;
} finally {
    lock.unlock();  // MUST be in finally
}
```

**When to pick which:**
- **`synchronized`** → 90% of cases. Simple mutual exclusion, short critical sections.
- **`ReentrantLock`** → When you need tryLock, timeouts, interruptibility, fairness, or multiple conditions.

---

### 1.2 `ReentrantLock` vs `ReentrantReadWriteLock`

**What is `ReentrantReadWriteLock`?** A lock that splits access into **two modes**: a **read lock** that multiple threads can hold simultaneously (for reading shared data), and a **write lock** that only one thread can hold (for modifying data), which also blocks all readers. The insight: if 95% of your operations just *read* data, why should readers block each other? With a regular `ReentrantLock`, they do. With `ReadWriteLock`, readers run concurrently — only writers need exclusive access. Think of it like a library: many people can read books at the same time, but the librarian needs everyone out to rearrange shelves.

| Feature | `ReentrantLock` | `ReentrantReadWriteLock` |
|---|---|---|
| **Read concurrency** | ❌ One thread at a time | ✅ Multiple readers simultaneously |
| **Write concurrency** | One writer | One writer (blocks all readers) |
| **Best for** | General mutual exclusion | Read-heavy workloads (caches, config) |
| **Complexity** | Simple | Two lock objects: `readLock()` + `writeLock()` |
| **Lock downgrade** | N/A | ✅ Write → Read (hold write, acquire read, release write) |
| **Lock upgrade** | N/A | ❌ Read → Write NOT supported (causes deadlock) |

```java
ReadWriteLock rwLock = new ReentrantReadWriteLock();

// Multiple threads can do this simultaneously
rwLock.readLock().lock();
try { return cache.get(key); }
finally { rwLock.readLock().unlock(); }

// Only ONE thread can do this (blocks all readers)
rwLock.writeLock().lock();
try { cache.put(key, value); }
finally { rwLock.writeLock().unlock(); }
```

**Rule of thumb:** If reads outnumber writes 10:1 or more → `ReadWriteLock`. Otherwise → `ReentrantLock`.

---

### 1.3 `ReentrantReadWriteLock` vs `StampedLock`

**What is `StampedLock`?** A lock (Java 8+) that adds an **optimistic read** mode on top of the read/write model. With `ReadWriteLock`, even readers acquire a real lock (which blocks writers). With `StampedLock`, you can do an **optimistic read** that acquires no lock at all — it just grabs a "stamp" (a version number). After reading, you check: "has anyone written since I got my stamp?" If no → your data is valid, no lock was ever held. If yes → fall back to a real read lock. This is dramatically faster when writes are rare because readers never block anything. The trade-off: it's not reentrant and has no `Condition` support, so it's harder to use correctly.

| Feature | `ReentrantReadWriteLock` | `StampedLock` (Java 8+) |
|---|---|---|
| **Reentrant** | ✅ Yes | ❌ No |
| **Condition support** | ✅ Yes | ❌ No |
| **Optimistic reads** | ❌ No | ✅ `tryOptimisticRead()` — no lock acquired! |
| **Performance** | Good | Better (especially read-heavy) |
| **Complexity** | Medium | High (stamp management) |
| **Lock upgrade** | ❌ | ✅ Read → Write via `tryConvertToWriteLock()` |

```java
StampedLock sl = new StampedLock();

// Optimistic read: NO LOCK acquired, just a "stamp"
long stamp = sl.tryOptimisticRead();
double x = this.x;
double y = this.y;
if (!sl.validate(stamp)) {    // Was there a write in between?
    stamp = sl.readLock();     // Fall back to real read lock
    try { x = this.x; y = this.y; }
    finally { sl.unlockRead(stamp); }
}
```

**When to pick:** `StampedLock` when reads vastly dominate and you want maximum throughput. Otherwise `ReadWriteLock` is safer and simpler.

---

### 1.4 `volatile` vs `synchronized` vs `Atomic*`

**What is `volatile`?** A Java keyword you put on a field to guarantee that **every thread sees the most recent write**. Without `volatile`, threads can cache field values in CPU registers/caches and never see updates from other threads. `volatile` forces reads/writes to go through main memory. **But** it only makes *single reads and writes* atomic — compound operations like `i++` (read → add → write = 3 steps) are still broken under concurrency.

**What is `AtomicInteger`?** A class that wraps an `int` and provides atomic compound operations (like `incrementAndGet()`, `compareAndSet()`) without using locks. Internally, it uses **CAS (Compare-And-Swap)** — a CPU-level instruction that atomically says "if the value is still X, change it to Y, otherwise try again." This is **lock-free**: no thread ever blocks. Threads that lose the CAS race simply spin and retry. Great for counters, sequence generators, and any single-variable updates under moderate contention.

| Feature | `volatile` | `synchronized` | `AtomicInteger` |
|---|---|---|---|
| **Guarantees visibility** | ✅ | ✅ | ✅ |
| **Guarantees atomicity** | ❌ (single read/write only) | ✅ | ✅ |
| **Compound operations** | ❌ (`i++` is NOT atomic) | ✅ | ✅ (`incrementAndGet()`) |
| **Blocking** | No | Yes (waits for lock) | No (lock-free CAS spin) |
| **Use case** | Flags, state booleans | Critical sections | Counters, accumulators |
| **Performance** | Fastest (just memory fence) | Moderate | Fast (CAS) |

```java
// volatile — visibility only, NOT atomic for i++
volatile boolean shutdown = false;  // Good ✅
volatile int count = 0;
count++;  // BROKEN ❌ — read + add + write is 3 steps

// AtomicInteger — atomic compound ops, lock-free
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();  // Atomic ✅, lock-free ✅

// synchronized — heaviest, but handles any logic
synchronized(this) {
    if (count < max) count++;  // Any complex logic ✅
}
```

**Decision tree:**
```
Need to share a boolean/flag?          → volatile
Need to atomically increment/CAS?      → AtomicInteger/AtomicLong
Need compound check-then-act logic?    → synchronized or Lock
```

---

## 2. COORDINATION & SIGNALING — Compared

### 2.1 `Semaphore`

**What:** A counter of permits. Threads `acquire()` a permit (blocks if none available) and `release()` it back.

**Key insight:** Unlike a lock, **any thread can release** (not just the one that acquired). And permits can be > 1.

```java
Semaphore sem = new Semaphore(3);  // 3 permits

sem.acquire();     // -1 permit (blocks if 0)
try {
    accessResource();
} finally {
    sem.release();  // +1 permit
}
```

| `Semaphore(1)` (binary) | `ReentrantLock` |
|---|---|
| Any thread can release | Only owning thread can unlock |
| Not reentrant | Reentrant |
| No ownership tracking | Tracks owner thread |
| Use for: resource pools, throttling | Use for: mutual exclusion |

**Real-world uses:**
- Connection pool: `Semaphore(maxConnections)`
- Rate limiting: limit concurrent requests
- Bounded resource access (e.g., max 5 DB connections)

---

### 2.2 `CountDownLatch` vs `CyclicBarrier`

**What is `CountDownLatch`?** A one-shot synchronization aid that lets one or more threads wait until a set of operations in other threads completes. You create it with a count (say 3), various threads call `countDown()` as they finish, and any waiting thread blocked on `await()` is released when the count hits zero. It's **one-shot**: once the count reaches zero, it stays there — you can't reset it. Use it for "don't start until all services are ready" or "wait for all workers to finish."

**What is `CyclicBarrier`?** A reusable synchronization point where a fixed number of threads wait for **each other**. Unlike `CountDownLatch` (where some threads count down and others wait), with `CyclicBarrier` the **same threads** that call `await()` are the ones that wait. Once all N threads arrive at the barrier, they're all released simultaneously and the barrier resets automatically for the next round. Use it for iterative algorithms where threads process a phase, sync up, then proceed to the next phase.

| Feature | `CountDownLatch` | `CyclicBarrier` |
|---|---|---|
| **Concept** | "Wait for N events to happen" | "N threads wait for each other" |
| **Reusable** | ❌ One-shot, cannot reset | ✅ Resets automatically after each barrier trip |
| **Who counts down** | Any thread calls `countDown()` | Threads call `await()` on themselves |
| **Who waits** | Different threads call `await()` | The same threads that called `await()` |
| **Typical use** | "Start when all services ready" | "Process next batch when all workers done" |
| **Callback on completion** | ❌ | ✅ Optional `Runnable` runs when barrier trips |

```java
// CountDownLatch — "main thread waits for 3 workers to finish"
CountDownLatch latch = new CountDownLatch(3);

// Worker threads:
doWork();
latch.countDown();  // "I'm done"

// Main thread:
latch.await();  // Blocks until count reaches 0
System.out.println("All workers done!");
// latch CANNOT be reused after this

// ─────────────────────────────────────────────

// CyclicBarrier — "3 threads wait for each other at checkpoint"
CyclicBarrier barrier = new CyclicBarrier(3, () -> 
    System.out.println("All 3 arrived! Starting next phase.")
);

// Each worker thread:
phase1Work();
barrier.await();  // Wait for all 3 threads to reach here
phase2Work();
barrier.await();  // Wait again (barrier resets automatically!)
phase3Work();
```

**Analogy:**
- `CountDownLatch` = Rocket launch countdown. Hits 0, launches once. Done.
- `CyclicBarrier` = Friends meeting at restaurant. Everyone waits until all arrive. Next week, same thing.

---

### 2.3 `Phaser` (Java 7+)

**What:** A reusable, flexible barrier that supports **dynamic participant count** and **multiple phases**.

Think of it as `CyclicBarrier` + `CountDownLatch` combined, but parties can register/deregister dynamically.

```java
Phaser phaser = new Phaser(1);  // 1 = the main thread

for (int i = 0; i < 3; i++) {
    phaser.register();  // Dynamically add participant
    new Thread(() -> {
        phaser.arriveAndAwaitAdvance();  // Phase 0
        doPhase1();
        phaser.arriveAndAwaitAdvance();  // Phase 1
        doPhase2();
        phaser.arriveAndDeregister();    // Leave after phase 2
    }).start();
}

phaser.arriveAndDeregister();  // Main thread deregisters
```

| Feature | `CyclicBarrier` | `Phaser` |
|---|---|---|
| Fixed party count | ✅ Set at construction | ❌ Dynamic register/deregister |
| Phase numbering | ❌ | ✅ `getPhase()` returns current phase |
| Tiered/tree structure | ❌ | ✅ For massive parallelism |
| Complexity | Low | High |

**When to pick:** Use `Phaser` only when you need dynamic party count or multi-phase pipelines. Otherwise, `CyclicBarrier` is simpler.

---

### 2.4 `Exchanger`

**What:** Two threads swap data with each other at a rendezvous point.

```java
Exchanger<String> exchanger = new Exchanger<>();

// Thread 1:
String fromThread2 = exchanger.exchange("Hello from T1");

// Thread 2:
String fromThread1 = exchanger.exchange("Hello from T2");
// Now T1 has "Hello from T2" and T2 has "Hello from T1"
```

**Use case:** Pipeline stages where one thread fills a buffer and another processes it — they swap full/empty buffers.

---

### 2.5 Quick Comparison Table

| Construct | Reusable | Parties | Blocking | Use Case |
|---|---|---|---|---|
| `Semaphore` | ✅ | N/A (permits) | `acquire()` blocks | Resource pool, throttling |
| `CountDownLatch` | ❌ | Fixed at creation | `await()` blocks | Wait for N events |
| `CyclicBarrier` | ✅ (auto-reset) | Fixed at creation | `await()` blocks | Threads sync at checkpoint |
| `Phaser` | ✅ | Dynamic | `await()` blocks | Multi-phase + dynamic parties |
| `Exchanger` | ✅ | Exactly 2 | `exchange()` blocks | Swap data between 2 threads |

---

## 3. EXECUTORS & THREAD POOLS — Compared

### 3.1 `ExecutorService` — The Interface

**What is `ExecutorService`?** An abstraction that manages a **thread pool** — a reusable set of threads that execute submitted tasks. Instead of creating `new Thread()` for every task (expensive: each thread costs ~1 MB stack memory + OS overhead), you submit tasks to an `ExecutorService`, which assigns them to its pre-created threads. When a task finishes, the thread goes back to the pool and picks up the next task. This is how virtually all real Java applications manage concurrency: you control the number of threads, the queue for waiting tasks, and what happens when the system is overloaded.

**What is a thread pool?** A fixed set of worker threads that sit idle waiting for work. Tasks are queued and dispatched to available threads. Benefits: (1) reuse threads instead of creating/destroying them, (2) bound the total number of threads to prevent resource exhaustion, (3) decouple task submission from execution. The `ThreadPoolExecutor` class is the implementation you configure directly in production.

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

// Submit Runnable (no return value)
executor.execute(() -> System.out.println("fire and forget"));

// Submit Callable (returns Future)
Future<String> future = executor.submit(() -> {
    return computeResult();
});
String result = future.get();  // Blocks until done

executor.shutdown();           // Finish submitted tasks, reject new ones
executor.shutdownNow();        // Interrupt all, reject new ones
```

### 3.2 Thread Pool Types

| Factory Method | Pool Type | Behavior | Use Case |
|---|---|---|---|
| `newFixedThreadPool(n)` | Fixed | Exactly `n` threads, unbounded queue | Known workload, CPU-bound |
| `newCachedThreadPool()` | Cached | 0→∞ threads, 60s idle timeout | Many short-lived tasks, I/O-bound |
| `newSingleThreadExecutor()` | Single | 1 thread, unbounded queue | Sequential task processing |
| `newScheduledThreadPool(n)` | Scheduled | `n` threads, supports delay/periodic | Cron jobs, heartbeats, timeouts |
| `newWorkStealingPool()` | ForkJoin | `Runtime.availableProcessors()` threads | Recursive divide-and-conquer |

```java
// ⚠️ DANGER: newFixedThreadPool uses UNBOUNDED LinkedBlockingQueue
// Under load → OOM because tasks queue up infinitely!

// Production-safe: configure ThreadPoolExecutor directly
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    4,                          // corePoolSize
    8,                          // maxPoolSize
    60, TimeUnit.SECONDS,       // idle thread keepAlive
    new ArrayBlockingQueue<>(100),  // BOUNDED queue!
    new ThreadPoolExecutor.CallerRunsPolicy()  // Backpressure
);
```

### 3.3 Rejection Policies (when queue is full + all threads busy)

| Policy | Behavior |
|---|---|
| `AbortPolicy` (default) | Throws `RejectedExecutionException` |
| `CallerRunsPolicy` | Submitting thread runs the task itself (backpressure!) |
| `DiscardPolicy` | Silently drops the task |
| `DiscardOldestPolicy` | Drops oldest queued task, retries submit |

---

### 3.4 `Future` vs `CompletableFuture`

**What is `Future`?** A handle to a result that will be available *sometime in the future*. You submit a `Callable` to an `ExecutorService`, get back a `Future`, and call `future.get()` to wait (block) for the result. That's it — you can only block and wait. You can't chain actions, combine results, or react when it completes.

**What is `CompletableFuture`?** A **promise** — a composable, non-blocking container for an asynchronous result. Think of it as a pipeline: you describe *what should happen* when the result arrives (`thenApply`), *what to do if it fails* (`exceptionally`), and *how to combine* multiple async results (`thenCombine`, `allOf`) — all without blocking any thread. The callbacks fire automatically when the result becomes available.

**Why it exists:** `Future.get()` blocks the calling thread, which defeats the purpose of async programming. If you call 3 services with `Future`, you block waiting for each one. With `CompletableFuture`, you describe the entire async workflow as a chain, and the framework runs it on a thread pool (default: `ForkJoinPool.commonPool()`) without you ever blocking.

**Mental model:**
```
Future:              "Here's a ticket. Come back later and WAIT in line for your result."
CompletableFuture:   "Here's a ticket. Tell me what to do when it's ready — I'll call you."
```

**Core concept — it's a monad-like chain:**
```java
// Think of it like Stream operations, but for async results:
CompletableFuture.supplyAsync(() -> fetchUser(userId))    // Step 1: async call
    .thenApply(user -> user.getEmail())                    // Step 2: transform (when Step 1 done)
    .thenCompose(email -> sendEmailAsync(email))           // Step 3: another async call (flatMap)
    .thenAccept(result -> log.info("Sent: " + result))    // Step 4: consume final result
    .exceptionally(ex -> { log.error("Failed", ex); return null; });  // Handle any error in chain
// NOTHING BLOCKS. The chain describes what happens; execution is handled by the thread pool.
```

**The key methods explained:**
```
Creating:
  supplyAsync(Supplier)          — Run a function async, return CF with result
  runAsync(Runnable)             — Run a task async, return CF<Void>
  completedFuture(value)         — Already-completed CF (for testing/mocking)

Transforming (when this CF completes):
  thenApply(Function)            — Transform result: A → B (like Stream.map)
  thenCompose(Function)          — Chain another async call: A → CF<B> (like Stream.flatMap)
  thenAccept(Consumer)           — Consume result, return CF<Void>
  thenRun(Runnable)              — Run action after completion, ignores result

Combining multiple CFs:
  thenCombine(other, BiFunction) — When BOTH complete, combine their results
  allOf(cf1, cf2, cf3...)        — Complete when ALL complete (returns CF<Void>)
  anyOf(cf1, cf2, cf3...)        — Complete when FIRST completes

Error handling:
  exceptionally(Function)        — Recover from exception, provide fallback value
  handle(BiFunction)             — Handle both success and failure (like try-catch-finally)
  whenComplete(BiConsumer)       — Side-effect on completion (doesn't change result)

Manual completion:
  complete(value)                — Manually set the result (for custom async patterns)
  completeExceptionally(ex)      — Manually set an exception
```

**Comparison Table:**

| Feature | `Future` | `CompletableFuture` |
|---|---|---|
| **Get result** | `get()` — blocking only | `get()` + async callbacks |
| **Chain operations** | ❌ | ✅ `thenApply`, `thenCompose`, `thenAccept` |
| **Combine futures** | ❌ | ✅ `allOf`, `anyOf`, `thenCombine` |
| **Exception handling** | `try-catch` around `get()` | ✅ `exceptionally()`, `handle()` |
| **Complete manually** | ❌ | ✅ `complete(value)`, `completeExceptionally(ex)` |
| **Non-blocking** | ❌ | ✅ Callbacks run when result available |

```java
// Future — old school, blocking
Future<String> future = executor.submit(() -> fetchData());
String result = future.get();  // BLOCKS here ❌

// CompletableFuture — modern, non-blocking, composable
CompletableFuture.supplyAsync(() -> fetchData())
    .thenApply(data -> parse(data))           // Chain
    .thenApply(parsed -> transform(parsed))   // Chain
    .thenAccept(result -> save(result))       // Terminal
    .exceptionally(ex -> {                     // Error handling
        log.error("Failed", ex);
        return null;
    });

// Combine multiple
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> fetchFromA());
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> fetchFromB());

CompletableFuture<String> combined = f1.thenCombine(f2, (a, b) -> a + b);

// Wait for ALL
CompletableFuture.allOf(f1, f2, f3).thenRun(() -> System.out.println("All done!"));

// Wait for ANY (first to complete)
CompletableFuture.anyOf(f1, f2, f3).thenAccept(first -> System.out.println("First: " + first));
```

---

### 3.5 `ForkJoinPool`

**What:** A thread pool optimized for **recursive divide-and-conquer** tasks using **work-stealing**.

**Key difference from `ThreadPoolExecutor`:** Worker threads that run out of tasks can **steal** work from other workers' queues.

```java
class SumTask extends RecursiveTask<Long> {
    private final long[] array;
    private final int lo, hi;
    static final int THRESHOLD = 1000;

    protected Long compute() {
        if (hi - lo <= THRESHOLD) {
            long sum = 0;
            for (int i = lo; i < hi; i++) sum += array[i];
            return sum;
        }
        int mid = (lo + hi) / 2;
        SumTask left  = new SumTask(array, lo, mid);
        SumTask right = new SumTask(array, mid, hi);
        left.fork();                    // Submit left to pool
        long rightResult = right.compute();  // Compute right in current thread
        long leftResult  = left.join();      // Wait for left
        return leftResult + rightResult;
    }
}

ForkJoinPool pool = new ForkJoinPool();  // default = num CPUs
long result = pool.invoke(new SumTask(array, 0, array.length));
```

| `ThreadPoolExecutor` | `ForkJoinPool` |
|---|---|
| Tasks are independent | Tasks spawn sub-tasks |
| Shared work queue | Per-worker deques + work stealing |
| Best for: I/O tasks, web requests | Best for: Recursive computation, parallel streams |

**Note:** `parallelStream()` uses the common `ForkJoinPool.commonPool()` internally.

---

## 4. THREAD-SAFE COLLECTIONS — Compared

### 4.1 Maps

**What is `ConcurrentHashMap`?** The standard thread-safe `Map` for concurrent Java programs. Unlike `Hashtable` (which locks the entire table on every operation) or `synchronizedMap` (which wraps a `HashMap` with a single global lock), `ConcurrentHashMap` uses **per-bucket locking** (Java 8+): it only locks the specific bucket being modified, so operations on different buckets proceed in parallel. Reads are entirely **lock-free** (using `volatile` reads). Most importantly, it provides **atomic compound operations** like `computeIfAbsent()`, `merge()`, and `putIfAbsent()` that are impossible to do safely with a regular `HashMap` + external synchronization.

**What is `ConcurrentSkipListMap`?** A thread-safe **sorted** map (implements `NavigableMap`) based on a skip list data structure. Unlike `ConcurrentHashMap` (unordered, O(1)), `ConcurrentSkipListMap` maintains keys in sorted order with O(log n) operations. It uses **lock-free CAS** internally. Use it when you need a concurrent map *with ordering* — e.g., range queries, floor/ceiling key lookups, or an ordered leaderboard.

| Collection | Thread-Safe | Null Keys/Values | Lock Granularity | Performance |
|---|---|---|---|---|
| `HashMap` | ❌ | ✅ null key + values | N/A | Fastest (single-threaded) |
| `Hashtable` | ✅ | ❌ | Whole table lock | Slowest — don't use |
| `Collections.synchronizedMap()` | ✅ | ✅ null key + values | Whole map lock | Slow under contention |
| `ConcurrentHashMap` | ✅ | ❌ | Per-bucket (Java 8+) | Best concurrent perf |
| `ConcurrentSkipListMap` | ✅ | ❌ | Lock-free (CAS) | Sorted order + O(log n) |

```java
// ConcurrentHashMap — THE default concurrent map
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

// Atomic compound operations (these are THE reason to use it)
map.putIfAbsent("key", 1);                      // Atomic check-and-put
map.computeIfAbsent("key", k -> expensive(k));   // Atomic compute-if-missing
map.merge("key", 1, Integer::sum);               // Atomic merge
map.compute("key", (k, v) -> v == null ? 1 : v + 1);  // Atomic update

// ⚠️ WARNING: This is NOT atomic even with ConcurrentHashMap!
if (!map.containsKey("key")) {  // Another thread could insert here!
    map.put("key", value);       // Race condition!
}
// Use putIfAbsent() or computeIfAbsent() instead
```

**ConcurrentHashMap internals (Java 8+):**
```
Bucket 0: [Node] → [Node] → [Node]    ← linked list (≤8 entries)
Bucket 1: [TreeNode]                    ← red-black tree (>8 entries)
Bucket 2: empty
Bucket 3: [Node] → [Node]
...
Bucket N: [Node]

- Reads: lock-free (volatile reads)
- Writes: synchronized per-bucket (only locks that one bucket)
- Resize: concurrent, incremental transfer
```

---

### 4.2 Lists

**What is `CopyOnWriteArrayList`?** A thread-safe `List` where every modification (add, set, remove) creates a **fresh copy of the entire underlying array**. Reads never lock — they just read the current snapshot of the array. This means reads are blazing fast (no synchronization at all), but writes are expensive (O(n) array copy). It also means iterators never throw `ConcurrentModificationException` because they iterate over a snapshot. Use it only when reads vastly outnumber writes (event listener lists, configuration, observer patterns) and the list is small.

| Collection | Thread-Safe | Read Perf | Write Perf | Iterator |
|---|---|---|---|---|
| `ArrayList` | ❌ | O(1) | O(1) amortized | Fail-fast |
| `Vector` | ✅ | O(1) | O(1) | Fail-fast (legacy, don't use) |
| `Collections.synchronizedList()` | ✅ | O(1) + lock | O(1) + lock | Must manually synchronize |
| `CopyOnWriteArrayList` | ✅ | O(1) no lock! | O(n) copies array! | Snapshot — never fails |

```java
// CopyOnWriteArrayList — copies ENTIRE array on every write
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
list.add("item");      // Copies whole array → O(n) 🐌
String s = list.get(0); // No lock needed → O(1) 🚀

// Perfect for: event listeners, configuration lists, observers
// Terrible for: frequently modified lists, large lists
```

**When to use `CopyOnWriteArrayList`:**
- Reads >> Writes (like 1000:1)
- Small lists (< 100 elements)
- Need to iterate without `ConcurrentModificationException`
- Event listener lists, observer patterns

---

### 4.3 Queues

**What is a `BlockingQueue`?** A thread-safe queue that **blocks** the calling thread when the operation can't proceed immediately: `put()` blocks when the queue is full (backpressure on producers), `take()` blocks when the queue is empty (consumers wait for work). This is the backbone of the **producer-consumer pattern** — the most fundamental concurrency pattern in Java. The blocking behavior eliminates the need for busy-waiting or manual `wait/notify` coordination.

**What is `SynchronousQueue`?** A special `BlockingQueue` with **zero capacity** — it doesn't store any elements. A `put()` blocks until another thread calls `take()`, and vice versa. It's a direct hand-off mechanism. `CachedThreadPool` uses it internally: when a task is submitted, it's handed directly to a waiting thread — if no thread is waiting, a new one is created.

| Queue | Bounded | Blocking | Lock-Free | Ordering | Use Case |
|---|---|---|---|---|---|
| `ArrayBlockingQueue` | ✅ (fixed) | ✅ | ❌ | FIFO | Bounded producer-consumer |
| `LinkedBlockingQueue` | Optional | ✅ | ❌ | FIFO | Default for thread pools |
| `PriorityBlockingQueue` | ❌ | ✅ (take only) | ❌ | Priority | Scheduled tasks, priorities |
| `SynchronousQueue` | 0 capacity | ✅ | ❌ | Direct handoff | `CachedThreadPool` uses this |
| `DelayQueue` | ❌ | ✅ | ❌ | Delay-based | Scheduled execution, TTL |
| `LinkedTransferQueue` | ❌ | ✅ | ✅ | FIFO | High-perf producer-consumer |
| `ConcurrentLinkedQueue` | ❌ | ❌ | ✅ | FIFO | Non-blocking queue |

```java
// BlockingQueue — the backbone of producer-consumer
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);

// Producer:
queue.put(task);     // Blocks if full
queue.offer(task, 5, SECONDS);  // Blocks up to 5s, returns false if still full

// Consumer:
Task t = queue.take();     // Blocks if empty
Task t = queue.poll(5, SECONDS);  // Blocks up to 5s, returns null if still empty

// SynchronousQueue — no buffering, direct handoff
SynchronousQueue<Task> sq = new SynchronousQueue<>();
sq.put(task);   // Blocks UNTIL a consumer calls take()
sq.take();      // Blocks UNTIL a producer calls put()
```

---

## 5. ATOMIC VARIABLES — Compared

### 5.1 The Atomic Family

**What are Atomic variables?** A family of classes (`AtomicInteger`, `AtomicLong`, `AtomicReference`, etc.) that provide **lock-free, thread-safe** operations on single values using **CAS (Compare-And-Swap)** — a hardware CPU instruction. CAS works like: "if the current value is `expected`, set it to `newValue` and return true; otherwise return false and I'll try again." Since it's a single CPU instruction, it's atomic — no lock needed. This makes them dramatically faster than `synchronized` for simple operations like counters, but they only work for single-variable updates (not compound logic across multiple fields).

| Class | What It Wraps | Key Operations |
|---|---|---|
| `AtomicInteger` | `int` | `incrementAndGet()`, `compareAndSet()`, `updateAndGet()` |
| `AtomicLong` | `long` | Same as AtomicInteger but for `long` |
| `AtomicBoolean` | `boolean` | `compareAndSet()`, `getAndSet()` |
| `AtomicReference<V>` | Object reference | `compareAndSet()`, `updateAndGet()` |
| `AtomicIntegerArray` | `int[]` | Per-index atomic ops |
| `AtomicStampedReference<V>` | Reference + int stamp | Solves ABA problem |
| `AtomicMarkableReference<V>` | Reference + boolean mark | Soft-delete patterns |

### 5.2 `AtomicInteger` vs `LongAdder`

**What is `LongAdder`?** A high-performance counter (Java 8+) designed for situations where many threads are incrementing the same counter simultaneously. `AtomicLong` has a problem under high contention: all threads CAS on the *same memory location*, causing constant cache-line bouncing and CAS failures. `LongAdder` solves this by **striping**: it maintains multiple internal cells, and each thread increments a *different cell* (based on thread hash). To read the total, you call `sum()` which adds up all cells. The trade-off: `sum()` gives a slightly approximate result during concurrent updates (exact at rest), and there's no `incrementAndGet()` (you can't get the value back on increment). But throughput can be 2-10x better under high contention.

| Feature | `AtomicInteger` | `LongAdder` (Java 8+) |
|---|---|---|
| **Mechanism** | Single CAS on one variable | Striped cells, CAS per stripe |
| **Contention** | High (all threads CAS same variable) | Low (threads spread across cells) |
| **Read** | `get()` — O(1) exact | `sum()` — O(stripes) approximate* |
| **Write** | `incrementAndGet()` | `increment()` (no return value!) |
| **Use case** | Few threads, need exact value | High contention, counters/metrics |
| **Throughput** | Good | 2-10x better under contention |

*`sum()` is exact at rest, but may miss concurrent updates during the sum.

```java
// AtomicInteger — good for low contention
AtomicInteger counter = new AtomicInteger(0);
int newVal = counter.incrementAndGet();  // Returns new value

// LongAdder — much faster under high contention
LongAdder adder = new LongAdder();
adder.increment();        // No return value! (that's the tradeoff)
long total = adder.sum(); // Get current sum (slightly approximate under load)

// LongAccumulator — generalized LongAdder
LongAccumulator max = new LongAccumulator(Long::max, Long.MIN_VALUE);
max.accumulate(42);
max.accumulate(99);
long result = max.get();  // 99
```

**Rule:** If you just need a high-throughput counter and don't need the return value → `LongAdder`. Otherwise → `AtomicLong`.

---

### 5.3 The ABA Problem

```
Thread 1 reads A
Thread 2 changes A → B → A  (puts A back)
Thread 1 does CAS(expected=A, new=C) → SUCCEEDS!
But the value was changed and changed back — Thread 1 missed the change.
```

**Solution:** `AtomicStampedReference` — attaches a version stamp.

```java
AtomicStampedReference<String> ref = new AtomicStampedReference<>("A", 0);

int[] stampHolder = new int[1];
String val = ref.get(stampHolder);  // val="A", stamp=0

// CAS checks BOTH value AND stamp
ref.compareAndSet("A", "C", 0, 1);  // Only succeeds if value=A AND stamp=0
```

---

## 6. THE `wait/notify` vs `Condition` vs `Lock` COMPARISON

**What is `wait()/notify()`?** The original Java mechanism for threads to communicate. A thread that calls `obj.wait()` inside a `synchronized(obj)` block releases the lock and goes to sleep until another thread calls `obj.notify()` (wake one) or `obj.notifyAll()` (wake all). The problem: there's only ONE wait-set per object, so you can't distinguish between "waiting because queue is full" and "waiting because queue is empty."

**What is `Condition`?** The modern replacement for `wait/notify`, used with `ReentrantLock`. You create multiple `Condition` objects from a single lock: `notFull = lock.newCondition()`, `notEmpty = lock.newCondition()`. Producers `await()` on `notFull`, consumers `await()` on `notEmpty`. When a consumer takes an item, it signals `notFull` — waking ONLY a producer. This is more efficient and less error-prone than `notifyAll()` which wakes everyone.

**What is `LockSupport`?** The lowest-level thread parking mechanism in Java. `LockSupport.park()` blocks the current thread, `LockSupport.unpark(thread)` unblocks a specific thread. Unlike `wait/notify`, it doesn't require holding a lock and can target a specific thread. You almost never use it directly — it's what frameworks and JDK internals use to build higher-level constructs like `ReentrantLock`, `Phaser`, and `CompletableFuture`.

| Feature | `wait()/notify()` | `Condition` | `LockSupport.park/unpark` |
|---|---|---|---|
| **Requires** | `synchronized` block | `ReentrantLock` | Nothing |
| **Multiple conditions** | ❌ One per object | ✅ Multiple per lock | N/A (per-thread) |
| **Spurious wakeups** | ✅ Possible | ✅ Possible | ✅ Possible |
| **Target specific thread** | ❌ `notify()` picks random | ❌ `signal()` picks random | ✅ `unpark(thread)` |
| **Must hold lock to signal** | ✅ | ✅ | ❌ |
| **Use level** | Low | Medium | Very low (framework internals) |

```java
// wait/notify — old school
synchronized(lock) {
    while (!condition) lock.wait();   // Must use while, not if
    lock.notifyAll();
}

// Condition — modern, more flexible
Lock lock = new ReentrantLock();
Condition notFull  = lock.newCondition();  // Separate condition for "not full"
Condition notEmpty = lock.newCondition();  // Separate condition for "not empty"

lock.lock();
try {
    while (isFull()) notFull.await();     // Wait on specific condition
    add(item);
    notEmpty.signal();                     // Signal specific condition
} finally {
    lock.unlock();
}

// LockSupport — lowest level, used by framework authors
LockSupport.park();              // Block current thread
LockSupport.unpark(someThread);  // Unblock specific thread
```

**Why `Condition` is better than `wait/notify`:**
With `wait/notify`, you have ONE wait-set per object. All producers AND consumers wait in the same set. `notifyAll()` wakes everyone — wasteful.
With `Condition`, producers wait on `notFull`, consumers wait on `notEmpty`. You signal exactly the right set.

---

## 7. THE MASTER DECISION FLOWCHART

```
START: "I need thread-safe access to shared state"
│
├─ Is it a single boolean/flag?
│   └─ YES → volatile
│
├─ Is it a counter or single numeric value?
│   ├─ Low contention → AtomicInteger / AtomicLong
│   └─ High contention (many threads) → LongAdder
│
├─ Is it a Map?
│   ├─ Need sorted order? → ConcurrentSkipListMap
│   └─ Otherwise → ConcurrentHashMap
│
├─ Is it a List?
│   ├─ Read-heavy, small, rarely modified? → CopyOnWriteArrayList
│   └─ Otherwise → Collections.synchronizedList() or external sync
│
├─ Is it a Queue (producer-consumer)?
│   ├─ Need bounded? → ArrayBlockingQueue
│   ├─ Need priority? → PriorityBlockingQueue
│   ├─ Need direct handoff? → SynchronousQueue
│   ├─ Need delay? → DelayQueue
│   └─ Need non-blocking? → ConcurrentLinkedQueue
│
├─ Need mutual exclusion on custom logic?
│   ├─ Simple, short → synchronized
│   ├─ Need tryLock/timeout/fairness → ReentrantLock
│   └─ Read-heavy? → ReentrantReadWriteLock or StampedLock
│
├─ Need to limit concurrent access to N?
│   └─ Semaphore(N)
│
├─ Need threads to wait for events/each other?
│   ├─ "Wait for N events, one-time" → CountDownLatch
│   ├─ "N threads sync, reusable" → CyclicBarrier
│   └─ "Dynamic parties, multi-phase" → Phaser
│
├─ Need async computation?
│   ├─ Simple task → ExecutorService + Future
│   ├─ Chain/compose → CompletableFuture
│   └─ Recursive divide-and-conquer → ForkJoinPool
│
└─ Need a thread pool?
    ├─ Fixed workload → newFixedThreadPool (or custom ThreadPoolExecutor)
    ├─ Bursty I/O → newCachedThreadPool
    ├─ Periodic/delayed → newScheduledThreadPool
    └─ PRODUCTION: Always use ThreadPoolExecutor with bounded queue
```

---

## 8. QUICK REFERENCE: ONE-LINE SUMMARY OF EACH

| Construct | One-Line Summary |
|---|---|
| `synchronized` | Built-in lock; simple, auto-release, no timeout/tryLock |
| `volatile` | Visibility guarantee for single reads/writes; no atomicity for `i++` |
| `ReentrantLock` | Explicit lock with tryLock, timeout, fairness, multiple conditions |
| `ReentrantReadWriteLock` | Many readers OR one writer; great for read-heavy data |
| `StampedLock` | Optimistic reads without locking; highest throughput, not reentrant |
| `Condition` | Like wait/notify but with multiple wait-sets per lock |
| `Semaphore` | Counting permits; any thread can release; for resource pools |
| `CountDownLatch` | One-shot "wait for N events to complete" |
| `CyclicBarrier` | Reusable "N threads wait for each other at checkpoint" |
| `Phaser` | Dynamic-party, multi-phase CyclicBarrier + CountDownLatch |
| `Exchanger` | Two threads swap objects at a rendezvous point |
| `AtomicInteger` | Lock-free int using CAS; for counters with low contention |
| `AtomicReference` | Lock-free object reference swap using CAS |
| `AtomicStampedReference` | AtomicReference + version stamp; solves ABA problem |
| `LongAdder` | Striped counter; much faster than AtomicLong under high contention |
| `ConcurrentHashMap` | Lock-per-bucket map; lock-free reads, atomic compound ops |
| `CopyOnWriteArrayList` | Copy-on-write list; zero-cost reads, O(n) writes |
| `ArrayBlockingQueue` | Bounded blocking FIFO queue for producer-consumer |
| `LinkedBlockingQueue` | Optionally bounded blocking queue; used by thread pools |
| `SynchronousQueue` | Zero-capacity direct handoff; used by CachedThreadPool |
| `PriorityBlockingQueue` | Unbounded blocking priority queue |
| `DelayQueue` | Elements available only after their delay expires |
| `ConcurrentLinkedQueue` | Lock-free non-blocking FIFO queue |
| `ExecutorService` | Manages thread pool; submit tasks, get Futures |
| `ThreadPoolExecutor` | Configurable thread pool: core/max size, queue, rejection policy |
| `ScheduledExecutorService` | Thread pool with delay and periodic scheduling |
| `ForkJoinPool` | Work-stealing pool for recursive divide-and-conquer tasks |
| `Future` | Handle to async result; blocking `get()` only |
| `CompletableFuture` | Composable async; chain, combine, handle errors non-blocking |
| `ForkJoinTask` | Task for ForkJoinPool; RecursiveTask (returns value) or RecursiveAction |

---

## 9. COMMON INTERVIEW QUESTIONS — RAPID FIRE

**Q: ConcurrentHashMap vs Hashtable?**
→ Both thread-safe. `Hashtable` locks entire table (slow). `ConcurrentHashMap` locks per-bucket (fast). `ConcurrentHashMap` doesn't allow null keys/values. Never use `Hashtable`.

**Q: ConcurrentHashMap vs Collections.synchronizedMap()?**
→ `synchronizedMap` wraps any Map with a single lock. `ConcurrentHashMap` has per-bucket locks + lock-free reads + atomic compound ops (`computeIfAbsent`). Always prefer `ConcurrentHashMap`.

**Q: When does ConcurrentHashMap need external synchronization?**
→ When you do a non-atomic compound operation: `if (!map.containsKey(k)) map.put(k, v)` is a race condition. Use `putIfAbsent()` or `computeIfAbsent()` instead.

**Q: synchronized vs ReentrantLock?**
→ `synchronized` for 90% of cases (simpler, auto-release). `ReentrantLock` when you need tryLock, timeout, fairness, or multiple conditions.

**Q: CountDownLatch vs CyclicBarrier?**
→ Latch = one-shot, different threads count down, others wait. Barrier = reusable, same threads wait for each other.

**Q: volatile vs AtomicInteger?**
→ `volatile` ensures visibility but NOT atomicity of compound ops (`i++`). `AtomicInteger` guarantees both. Use `volatile` for flags, `AtomicInteger` for counters.

**Q: Why can't you upgrade a ReadLock to WriteLock in ReentrantReadWriteLock?**
→ If Thread A holds read lock and tries to acquire write lock, it blocks waiting for all readers to release. But Thread A IS a reader and won't release until it gets the write lock → deadlock. Use `StampedLock.tryConvertToWriteLock()` instead.

**Q: What is the ABA problem?**
→ CAS succeeds because value looks unchanged (A→B→A), but it actually changed and came back. Use `AtomicStampedReference` to detect intermediate changes via a version stamp.

**Q: Why use LongAdder over AtomicLong?**
→ Under high contention, many threads CAS-spinning on one variable wastes CPU. `LongAdder` stripes across cells so threads don't contend. Trade-off: `sum()` is slightly approximate during concurrent writes.

**Q: What happens if you call Future.get() and the task throws?**
→ `get()` throws `ExecutionException` wrapping the original exception. Always call `e.getCause()` to get the real exception.

**Q: How does ForkJoinPool work-stealing work?**
→ Each worker has a deque. It pushes/pops from one end. When idle, it steals from the OTHER end of another worker's deque. This minimizes contention.

---

*Created: February 24, 2026*
*Companion files: `concurrency-primitives-comprehensive-guide.md`, `ConcurrencyInterviewQuestions.java`*
