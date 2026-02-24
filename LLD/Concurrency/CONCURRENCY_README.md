# Java Concurrency & Parallelism - Interview Questions Guide

## 📚 Overview

This comprehensive Java program covers **12 most frequently asked concurrency questions** in technical interviews at top tech companies (FAANG, Microsoft, Google, Amazon, etc.). Each section demonstrates a real-world concurrency pattern with working code examples.

## 🎯 Topics Covered

1. **Thread Basics & Synchronization** - Print numbers using multiple threads
2. **Producer-Consumer Problem** - Classic concurrency pattern with wait/notify
3. **Print Sequence (FooBar)** - LeetCode #1115, alternating thread execution
4. **Thread-Safe Singleton Patterns** - 4 different approaches (synchronized, double-checked, Bill Pugh, enum)
5. **Dining Philosophers Problem** - Deadlock prevention with ordered resource acquisition
6. **Thread Pool & Executors** - ExecutorService, Future, Callable
7. **Bounded Blocking Queue** - Custom concurrent data structure with locks
8. **Read-Write Lock Pattern** - Multiple readers, single writer optimization
9. **CountDownLatch & CyclicBarrier** - Thread coordination utilities
10. **Deadlock Prevention & Detection** - 4 deadlock conditions and solutions
11. **Thread-Safe Data Structures** - ConcurrentHashMap, AtomicInteger, CopyOnWriteArrayList
12. **Future & Callable** - Asynchronous computation with CompletableFuture

## 🚀 How to Compile and Run

### Prerequisites
- Java 8 or higher (Java 11+ recommended)
- Terminal/Command Prompt

### Option 1: Compile and Run Directly

```bash
# Navigate to the LLD directory
cd LLD

# Compile the program
javac ConcurrencyInterviewQuestions.java

# Run the program
java ConcurrencyInterviewQuestions
```

### Option 2: Run with Java 11+ (no separate compilation)

```bash
# Navigate to the LLD directory
cd LLD

# Run directly
java ConcurrencyInterviewQuestions.java
```

## 📖 Understanding the Output

When you run the program, you'll see demonstrations of all 12 concurrency patterns in sequence:

```
╔════════════════════════════════════════════════════════════════╗
║   CONCURRENCY & PARALLELISM - TOP INTERVIEW QUESTIONS         ║
║   Comprehensive Guide with Practical Examples                  ║
╚════════════════════════════════════════════════════════════════╝

=== DEMO: Print Numbers with Multiple Threads ===
Thread 0: 3
Thread 1: 1
Thread 2: 2
...

=== DEMO: Producer-Consumer Pattern ===
Produced: 1 | Buffer size: 1
Consumed: 1 | Buffer size: 0
...
```

## 🎓 Study Tips for Interviews

### Key Concepts to Master

1. **Synchronization Mechanisms**
   - `synchronized` keyword
   - `wait()` and `notify()`/`notifyAll()`
   - `ReentrantLock` and `Condition`

2. **Thread-Safe Collections**
   - When to use `ConcurrentHashMap` vs `Collections.synchronizedMap()`
   - `CopyOnWriteArrayList` for read-heavy scenarios
   - `BlockingQueue` implementations

3. **Atomic Variables**
   - `AtomicInteger`, `AtomicLong`, `AtomicReference`
   - Lock-free thread-safe operations
   - Compare-and-swap (CAS) operations

4. **Deadlock Four Conditions**
   - Mutual Exclusion
   - Hold and Wait
   - No Preemption
   - Circular Wait
   
   **Prevention**: Break any one of these conditions!

5. **Thread Pools**
   - `FixedThreadPool` - fixed number of threads
   - `CachedThreadPool` - creates threads as needed
   - `SingleThreadExecutor` - single worker thread
   - `ScheduledThreadPool` - scheduled/delayed tasks

### Common Interview Questions

**Q: What's the difference between `synchronized` method and `ReentrantLock`?**
- `ReentrantLock` offers more flexibility: tryLock(), timed locks, interruptible locks
- `synchronized` is simpler and automatically releases on exception
- `ReentrantLock` requires manual unlock in finally block

**Q: When to use `volatile` keyword?**
- For simple flags/state variables
- Ensures visibility across threads
- Does NOT provide atomicity for compound operations (use Atomic classes instead)

**Q: What's the difference between `CountDownLatch` and `CyclicBarrier`?**
- `CountDownLatch`: One-time event, cannot be reset
- `CyclicBarrier`: Reusable, threads wait for each other at barrier point

**Q: How to prevent deadlock?**
- Always acquire locks in the same order (see Dining Philosophers)
- Use timeout with `tryLock()`
- Avoid nested locks when possible
- Use higher-level concurrency utilities

## 🔧 Customizing the Examples

Each demonstration is self-contained. You can modify parameters:

```java
// Change number of threads
new PrintNumbersWithThreads(30, 5).demonstrate(); // 30 numbers, 5 threads

// Change buffer capacity
new ProducerConsumer(10).demonstrate(); // Buffer size of 10

// Change number of philosophers
// Edit DiningPhilosophers class: numPhilosophers = 7
```

## 📝 Code Structure

The file is organized into static nested classes for each topic:

```
ConcurrencyInterviewQuestions.java
├── PrintNumbersWithThreads      (Thread basics)
├── ProducerConsumer             (wait/notify pattern)
├── FooBar                       (Alternating execution)
├── Singleton classes            (4 implementations)
├── DiningPhilosophers           (Deadlock prevention)
├── ThreadPoolDemo               (Executors)
├── BoundedBlockingQueue         (Custom queue)
├── ReadWriteLockDemo            (Read/Write locks)
├── SynchronizationUtilities     (Latch & Barrier)
├── DeadlockDemo                 (Deadlock examples)
├── ThreadSafeDataStructures     (Concurrent collections)
└── FutureCallableDemo           (Async computation)
```

## 🎯 Interview Tips

1. **Explain Your Reasoning**: Always explain why you choose a particular synchronization mechanism
2. **Consider Edge Cases**: What happens with 0 threads? Empty queue? Null values?
3. **Thread Safety**: Always discuss thread safety implications
4. **Performance**: Discuss trade-offs between different approaches
5. **Testing**: Mention how you would test concurrent code (race conditions are hard to reproduce!)

## 📚 Additional Resources

- **Java Concurrency in Practice** by Brian Goetz (must-read!)
- **Effective Java** by Joshua Bloch (Item 78-84 on concurrency)
- Java Documentation: `java.util.concurrent` package
- LeetCode Concurrency Problems: #1114, #1115, #1116, #1117, #1195, #1226

## 🐛 Common Pitfalls to Avoid

1. **Forgetting to call `notifyAll()`** - Can leave threads waiting forever
2. **Using `notify()` instead of `notifyAll()`** - May not wake the right thread
3. **Not using `volatile` for shared flags** - Visibility issues
4. **Forgetting to unlock in `finally` block** - Can cause deadlock
5. **Using `Thread.stop()`** - Deprecated and unsafe
6. **Not handling `InterruptedException` properly** - Always restore interrupt status

## 💡 Practice Suggestions

1. **Run the code** and observe the output patterns
2. **Modify parameters** to see different behaviors
3. **Add `Thread.sleep()` delays** to observe race conditions
4. **Remove synchronization** and see what breaks
5. **Implement your own variations** of each pattern
6. **Try to break the code** - best way to understand edge cases!

## 🏆 Interview Success Strategy

1. **Start with requirements** - clarify the problem
2. **Discuss approach** - explain synchronization strategy
3. **Write clean code** - use proper try-finally blocks
4. **Test edge cases** - discuss what could go wrong
5. **Optimize if needed** - discuss performance implications

---

**Good luck with your interviews! 🚀**

Remember: Concurrency is hard - if you understand these patterns, you're ahead of most candidates!
