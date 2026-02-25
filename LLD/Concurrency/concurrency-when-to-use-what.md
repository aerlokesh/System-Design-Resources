# Java Concurrency — When to Use What

> Quick-reference decision guide. Start from your **problem**, find the **right tool**.

---

## 🔥 The 30-Second Cheat Sheet

| I need to… | Use this | Why not the alternatives |
|---|---|---|
| Share a boolean flag between threads | `volatile boolean` | `synchronized` is overkill, `AtomicBoolean` is overkill |
| Increment a counter (few threads) | `AtomicInteger` | `synchronized` is 5x slower, `volatile` can't do `i++` |
| Increment a counter (many threads) | `LongAdder` | `AtomicLong` has CAS contention under high concurrency |
| Protect a critical section (simple) | `synchronized` | It's the simplest, JVM-optimized, auto-releases |
| Protect a critical section (need timeout/tryLock) | `ReentrantLock` | `synchronized` can't timeout or try without blocking |
| Cache with many reads, rare writes | `ReentrantReadWriteLock` | `synchronized` blocks all readers unnecessarily |
| Cache with extreme read throughput | `StampedLock` optimistic read | `ReadWriteLock` still acquires a read lock |
| Thread-safe Map | `ConcurrentHashMap` | `synchronizedMap` locks the whole map |
| Thread-safe List (read-heavy, small) | `CopyOnWriteArrayList` | `synchronizedList` locks on every read too |
| Producer-consumer queue (bounded) | `ArrayBlockingQueue` | It blocks when full/empty — built-in backpressure |
| Producer-consumer queue (unbounded) | `LinkedBlockingQueue` | Or `ConcurrentLinkedQueue` if you don't need blocking |
| Wait for N tasks to finish | `CountDownLatch` | One-shot, simple, exactly this use case |
| N threads sync at checkpoint, repeat | `CyclicBarrier` | Reusable, unlike `CountDownLatch` |
| Limit concurrent access to N | `Semaphore(N)` | Not a lock — it's a permit counter |
| Run tasks in a thread pool | `ThreadPoolExecutor` (manual config) | Factory methods use unbounded queues → OOM risk |
| Async computation with chaining | `CompletableFuture` | `Future.get()` blocks, can't chain |
| Recursive divide-and-conquer | `ForkJoinPool` | Work-stealing optimized for recursive subtasks |

---

## 🧭 Decision Flowcharts

### "I need to protect shared mutable state"

```
What kind of state?
│
├── A single boolean/flag?
│   └── volatile ✅
│       (visibility only — no compound ops needed)
│
├── A single number (counter, gauge)?
│   ├── Threads ≤ 4?  → AtomicInteger / AtomicLong
│   ├── Threads > 4?  → LongAdder (striped, way faster)
│   └── Need the return value on update? → AtomicLong.incrementAndGet()
│       (LongAdder.increment() returns void)
│
├── A Map?
│   ├── Sorted order needed?  → ConcurrentSkipListMap
│   └── Otherwise             → ConcurrentHashMap
│       ⚠️ Use computeIfAbsent/putIfAbsent for atomic compound ops
│       ⚠️ containsKey() + put() is NOT atomic even with CHM
│
├── A List?
│   ├── Small + reads >> writes?  → CopyOnWriteArrayList
│   └── Large or write-heavy?     → synchronizedList + external sync
│
├── A Queue?
│   │   (see Queue section below)
│
└── Custom logic / multiple variables?
    ├── Simple, short critical section?  → synchronized
    ├── Need tryLock / timeout?          → ReentrantLock
    ├── Reads >> Writes?                 → ReentrantReadWriteLock
    └── Extreme read throughput?         → StampedLock (optimistic read)
```

### "I need threads to coordinate"

```
What kind of coordination?
│
├── "Main thread waits for N workers to finish"
│   └── CountDownLatch(N)
│       Workers call countDown(), main calls await()
│       ⚠️ One-shot only — cannot reset
│
├── "N threads wait for each other at a checkpoint, then repeat"
│   └── CyclicBarrier(N)
│       Each thread calls await(), barrier resets automatically
│       ✅ Optional Runnable on barrier trip
│
├── "Dynamic number of participants across multiple phases"
│   └── Phaser
│       register()/deregister() dynamically
│       ⚠️ Complex — only use if CyclicBarrier doesn't fit
│
├── "Two threads swap data"
│   └── Exchanger
│       Thread A exchanges with Thread B at rendezvous point
│
├── "Limit concurrent access to a resource pool (N connections)"
│   └── Semaphore(N)
│       acquire() takes a permit, release() returns it
│       ⚠️ Not a lock — any thread can release
│
├── "Producer-consumer with backpressure"
│   └── BlockingQueue (see Queue section)
│
└── "Notify specific waiting threads"
    ├── One condition?  → wait()/notifyAll() inside synchronized
    └── Multiple conditions? → ReentrantLock + Condition objects
        (e.g., separate notFull and notEmpty conditions)
```

### "I need a queue"

```
What are the requirements?
│
├── Need blocking (producer waits when full, consumer waits when empty)?
│   │
│   ├── Fixed size, bounded?
│   │   └── ArrayBlockingQueue ✅
│   │       Best default for bounded producer-consumer
│   │
│   ├── Optionally bounded / higher throughput?
│   │   └── LinkedBlockingQueue
│   │       Separate locks for head/tail → better concurrency than ABQ
│   │       ⚠️ Default unbounded — always set a capacity!
│   │
│   ├── Priority ordering?
│   │   └── PriorityBlockingQueue
│   │       ⚠️ Unbounded — cannot block on put()
│   │
│   ├── Elements available only after a delay?
│   │   └── DelayQueue
│   │       Items implement Delayed interface
│   │       Use for: scheduled tasks, TTL expiry, retry with backoff
│   │
│   └── Direct handoff (zero buffering)?
│       └── SynchronousQueue
│           put() blocks until a take() is ready
│           Used by CachedThreadPool internally
│
└── Non-blocking (poll returns null if empty)?
    ├── FIFO? → ConcurrentLinkedQueue (lock-free)
    └── FIFO + blocking optional? → LinkedTransferQueue
```

### "I need async computation"

```
What kind of computation?
│
├── Submit a task, get result later?
│   ├── Just need the result (blocking OK)?
│   │   └── ExecutorService.submit() → Future.get()
│   │
│   └── Need to chain/compose/handle errors without blocking?
│       └── CompletableFuture ✅
│           .supplyAsync() → .thenApply() → .thenAccept()
│           .exceptionally() for error handling
│           .allOf() / .anyOf() to combine
│
├── Recursive divide-and-conquer?
│   └── ForkJoinPool + RecursiveTask/RecursiveAction
│       Work-stealing for balanced parallelism
│       Also powers parallelStream() internally
│
└── Need a thread pool?
    ├── Known fixed workload?         → ThreadPoolExecutor(n, n, bounded queue)
    ├── Bursty short-lived I/O tasks? → ThreadPoolExecutor with 0 core, max threads, SynchronousQueue
    ├── Periodic/scheduled tasks?     → ScheduledThreadPoolExecutor
    │
    └── ⚠️ PRODUCTION RULE: Never use Executors factory methods
        They create unbounded queues → OOM under load
        Always configure ThreadPoolExecutor manually with:
        - Bounded queue (ArrayBlockingQueue)
        - Rejection policy (CallerRunsPolicy for backpressure)
```

---

## 🎯 Scenario-Based Picks

### Scenario 1: "I'm building a cache"

```
Read/Write ratio?
│
├── 90% reads → ReentrantReadWriteLock
│   Multiple readers, exclusive writer
│
├── 99%+ reads → StampedLock (optimistic read)
│   Reads don't even acquire a lock — just validate after
│
├── Concurrent key-value cache → ConcurrentHashMap
│   Use computeIfAbsent() for lazy loading
│
└── Small config map, rarely updated → CopyOnWriteArrayList / volatile reference to immutable Map
```

**Example:** DNS cache, config cache, session cache

### Scenario 2: "I'm building a rate limiter"

```
Algorithm?
│
├── Token Bucket
│   └── AtomicInteger for tokens + CAS loop for tryAcquire()
│       ScheduledExecutorService for refill
│
├── Sliding Window Counter
│   └── ConcurrentHashMap<TimeSlot, AtomicInteger>
│
└── Fixed Window
    └── AtomicInteger + volatile long windowStart
```

### Scenario 3: "I'm building a connection pool"

```
Semaphore(maxConnections) to limit concurrent access
+ ConcurrentLinkedQueue<Connection> for available connections
+ AtomicInteger for tracking pool size

acquire(): semaphore.acquire() → queue.poll() → create if null
release(): queue.offer(conn) → semaphore.release()
```

### Scenario 4: "I'm building a metrics/monitoring system"

```
High-throughput counters → LongAdder (not AtomicLong!)
Gauges (current value)   → AtomicLong or volatile
Histograms               → ConcurrentSkipListMap or striped buckets
Per-endpoint tracking    → ConcurrentHashMap<String, LongAdder>
```

### Scenario 5: "I'm building a notification/event system"

```
Listener registry    → ConcurrentHashMap<EventType, CopyOnWriteArrayList<Listener>>
Async dispatch       → ThreadPoolExecutor
Event queue          → LinkedBlockingQueue (bounded)
Fan-out to consumers → CompletableFuture.allOf()
```

### Scenario 6: "I'm building a job scheduler"

```
Job queue      → PriorityBlockingQueue (priority-based)
                 OR DelayQueue (time-based)
Worker pool    → ScheduledThreadPoolExecutor
Job state      → ConcurrentHashMap<JobId, AtomicReference<State>>
Coordination   → CountDownLatch (wait for dependent jobs)
```

### Scenario 7: "I'm building a thread-safe singleton"

```
Approach?
│
├── Best: Initialization-on-demand holder (Bill Pugh)
│   private static class Holder { static final T INSTANCE = new T(); }
│   No synchronization, lazy, thread-safe via classloader guarantee
│
├── OK: enum singleton
│   enum Singleton { INSTANCE; }
│   Simplest, serialization-safe
│
├── Acceptable: Double-checked locking
│   ⚠️ MUST use volatile
│
└── Avoid: Synchronized getInstance()
    Locks on every call, even after init
```

---

## ⚖️ Head-to-Head Comparisons (Quick Reference)

### Lock Selection

| Situation | Pick | Reason |
|---|---|---|
| Simple, short critical section | `synchronized` | Simplest, JVM-optimized, auto-release |
| Need tryLock or timeout | `ReentrantLock` | `synchronized` blocks forever |
| Need interruptible wait | `ReentrantLock.lockInterruptibly()` | `synchronized` can't be interrupted |
| Need fair ordering | `ReentrantLock(true)` | `synchronized` has no fairness guarantee |
| Multiple wait conditions | `ReentrantLock` + `Condition` | `synchronized` has one wait-set |
| Reads >> Writes | `ReentrantReadWriteLock` | Allows concurrent readers |
| Extreme read throughput | `StampedLock` optimistic read | No lock acquired for reads |

### Counter Selection

| Situation | Pick | Reason |
|---|---|---|
| Single flag | `volatile boolean` | Cheapest — just a memory fence |
| Counter, low contention (≤4 threads) | `AtomicInteger` | Lock-free CAS, returns value |
| Counter, high contention (>4 threads) | `LongAdder` | Striped cells, 2-10x faster |
| Need return value on update | `AtomicLong.incrementAndGet()` | `LongAdder.increment()` returns void |
| Max/min accumulation | `LongAccumulator` | `LongAccumulator(Long::max, Long.MIN_VALUE)` |
| Detect ABA problem | `AtomicStampedReference` | Version stamp prevents A→B→A false CAS |

### Collection Selection

| Situation | Pick | Reason |
|---|---|---|
| Key-value, concurrent access | `ConcurrentHashMap` | Per-bucket locks, lock-free reads |
| Sorted key-value, concurrent | `ConcurrentSkipListMap` | Lock-free, O(log n) |
| List, reads >> writes, small | `CopyOnWriteArrayList` | Zero-cost reads, snapshot iterator |
| List, write-heavy or large | `Collections.synchronizedList()` | COW copies on every write = O(n) |
| Queue, blocking, bounded | `ArrayBlockingQueue` | Built-in backpressure |
| Queue, blocking, unbounded (careful!) | `LinkedBlockingQueue(capacity)` | Always set a capacity |
| Queue, non-blocking | `ConcurrentLinkedQueue` | Lock-free CAS |
| Queue, priority + blocking | `PriorityBlockingQueue` | Heap-ordered, blocks on take |
| Queue, delayed elements | `DelayQueue` | Elements available after delay |
| Queue, direct handoff | `SynchronousQueue` | Zero capacity, 1:1 handoff |

### Coordination Selection

| Situation | Pick | Reason |
|---|---|---|
| Wait for N events (one-time) | `CountDownLatch(N)` | Simple, one-shot |
| N threads sync at checkpoint (reusable) | `CyclicBarrier(N)` | Auto-resets after each trip |
| Dynamic parties + multiple phases | `Phaser` | register/deregister dynamically |
| Limit concurrent access to N resources | `Semaphore(N)` | Counting permits |
| Two threads swap data | `Exchanger` | Bidirectional rendezvous |
| Wake specific threads | `LockSupport.unpark(thread)` | Low-level, framework use |

### Executor Selection

| Situation | Pick | Reason |
|---|---|---|
| Fixed known workload | `ThreadPoolExecutor(n, n, ..., boundedQueue)` | Predictable resource usage |
| Bursty I/O tasks | `ThreadPoolExecutor(0, max, ..., SynchronousQueue)` | Creates threads on demand |
| Periodic/delayed tasks | `ScheduledThreadPoolExecutor` | Built-in scheduling |
| Recursive computation | `ForkJoinPool` | Work-stealing |
| Async chaining | `CompletableFuture` (uses FJP by default) | Non-blocking composition |

---

## 🚫 Common Mistakes & What to Use Instead

| Mistake | Why It's Wrong | Fix |
|---|---|---|
| `volatile int count; count++` | `i++` is 3 ops (read, add, write) — not atomic | `AtomicInteger.incrementAndGet()` |
| `if (!map.containsKey(k)) map.put(k,v)` | Race between check and put, even with CHM | `map.putIfAbsent(k, v)` or `computeIfAbsent()` |
| `Executors.newFixedThreadPool(n)` in production | Unbounded `LinkedBlockingQueue` → OOM | Manual `ThreadPoolExecutor` with bounded queue |
| `synchronized` for read-heavy cache | All readers block each other | `ReentrantReadWriteLock` |
| `AtomicLong` for high-contention counter | CAS spin wastes CPU under contention | `LongAdder` |
| `notify()` instead of `notifyAll()` | Can wake wrong thread → deadlock | `notifyAll()` (or better: `Condition.signal()`) |
| `if (condition) wait()` | Spurious wakeup skips recheck | `while (condition) wait()` |
| `lock.lock(); doWork(); lock.unlock();` | Exception before unlock → lock never released | `try { doWork(); } finally { lock.unlock(); }` |
| DCL singleton without `volatile` | Partially constructed object visible to other threads | Add `volatile` to instance field |
| Read lock → try upgrade to write lock (RRWL) | Thread holds read, waits for write, but can't release read → deadlock | Release read, acquire write, re-check (or use `StampedLock`) |

---

## 📐 Rules of Thumb

1. **Start with the simplest tool.** `synchronized` and `AtomicInteger` cover 80% of cases.
2. **Only optimize when you measure contention.** Don't prematurely reach for `StampedLock` or `LongAdder`.
3. **Prefer immutable objects.** No synchronization needed if nothing mutates.
4. **Prefer `ConcurrentHashMap` over `synchronizedMap`.** Always.
5. **Always bound your queues in production.** Unbounded = eventual OOM.
6. **Always configure `ThreadPoolExecutor` manually.** Factory methods are for demos.
7. **Lock ordering prevents deadlocks.** If you must lock A and B, always lock the lower ID first.
8. **Minimize lock scope.** Only hold the lock during the critical section, not I/O or computation.
9. **`volatile` is for visibility, not atomicity.** Fine for flags, wrong for counters.
10. **`finally` block for `unlock()`.** Non-negotiable.

---

## 🗂️ Quick Lookup by Java Class

| Class | Category | When to Use |
|---|---|---|
| `synchronized` | Lock | Default choice for mutual exclusion |
| `volatile` | Visibility | Single variable flags/state |
| `ReentrantLock` | Lock | tryLock, timeout, fairness, conditions |
| `ReentrantReadWriteLock` | Lock | Read-heavy workloads |
| `StampedLock` | Lock | Extreme read throughput (optimistic reads) |
| `Condition` | Coordination | Multiple wait-sets per lock |
| `Semaphore` | Coordination | Resource pools, throttling |
| `CountDownLatch` | Coordination | One-time "wait for N" |
| `CyclicBarrier` | Coordination | Reusable "all threads meet" |
| `Phaser` | Coordination | Dynamic parties, multi-phase |
| `Exchanger` | Coordination | Two-thread data swap |
| `AtomicInteger/Long` | Atomic | Low-contention counters |
| `AtomicReference` | Atomic | Lock-free object swaps |
| `AtomicStampedReference` | Atomic | CAS with ABA protection |
| `LongAdder` | Atomic | High-contention counters |
| `LongAccumulator` | Atomic | Custom accumulation (max, min, etc.) |
| `ConcurrentHashMap` | Collection | Thread-safe map (default choice) |
| `ConcurrentSkipListMap` | Collection | Sorted thread-safe map |
| `CopyOnWriteArrayList` | Collection | Read-heavy small lists |
| `ArrayBlockingQueue` | Queue | Bounded blocking producer-consumer |
| `LinkedBlockingQueue` | Queue | Higher-throughput blocking queue |
| `PriorityBlockingQueue` | Queue | Priority-ordered blocking queue |
| `DelayQueue` | Queue | Time-delayed elements |
| `SynchronousQueue` | Queue | Direct handoff (zero buffer) |
| `ConcurrentLinkedQueue` | Queue | Non-blocking FIFO |
| `ThreadPoolExecutor` | Executor | Production thread pool |
| `ScheduledThreadPoolExecutor` | Executor | Periodic/delayed tasks |
| `ForkJoinPool` | Executor | Recursive parallel computation |
| `CompletableFuture` | Async | Non-blocking async chaining |
| `Future` | Async | Simple blocking async result |

---

*Created: February 24, 2026*
*Companion files: `java-concurrency-all-constructs-compared.md`, `concurrency-primitives-comprehensive-guide.md`*