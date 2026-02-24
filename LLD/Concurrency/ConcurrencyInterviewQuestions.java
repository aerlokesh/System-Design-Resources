/**
 * CONCURRENCY & PARALLELISM - TOP INTERVIEW QUESTIONS
 * 
 * This comprehensive guide covers the most frequently asked concurrency questions
 * in technical interviews at top tech companies (FAANG, etc.)
 * 
 * TOPICS COVERED:
 * 1. Thread Basics & Synchronization
 * 2. Producer-Consumer Problem
 * 3. Print Sequence Using Multiple Threads
 * 4. Thread-Safe Singleton Patterns
 * 5. Dining Philosophers Problem
 * 6. Thread Pool & Executors
 * 7. Bounded Blocking Queue
 * 8. Read-Write Lock Implementation
 * 9. CountDownLatch & CyclicBarrier
 * 10. Deadlock Prevention & Detection
 * 11. Thread-Safe Data Structures
 * 12. Future & Callable
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class ConcurrencyInterviewQuestions {

    // ==================== SECTION 1: THREAD BASICS ====================
    
    /**
     * QUESTION 1: Print numbers from 1 to N using multiple threads
     * Demonstrates: Basic thread creation and synchronization
     */
    static class PrintNumbersWithThreads {
        private int current = 1;
        private final int max;
        private final int numThreads;
        
        public PrintNumbersWithThreads(int max, int numThreads) {
            this.max = max;
            this.numThreads = numThreads;
        }
        
        public synchronized void printNumber(int threadId) {
            while (current <= max) {
                while (current % numThreads != threadId && current <= max) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (current <= max) {
                    System.out.println("Thread " + threadId + ": " + current);
                    current++;
                    notifyAll();
                }
            }
        }
        
        public void demonstrate() throws InterruptedException {
            System.out.println("\n=== DEMO: Print Numbers with Multiple Threads ===");
            Thread[] threads = new Thread[numThreads];
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> printNumber(threadId));
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }
        }
    }

    // ==================== SECTION 2: PRODUCER-CONSUMER PROBLEM ====================
    
    /**
     * QUESTION 2: Implement Producer-Consumer Pattern
     * Demonstrates: wait/notify, thread coordination
     * 
     * INTERVIEW TIP: This is one of the most common concurrency questions!
     */
    static class ProducerConsumer {
        private final Queue<Integer> buffer;
        private final int capacity;
        
        public ProducerConsumer(int capacity) {
            this.buffer = new LinkedList<>();
            this.capacity = capacity;
        }
        
        public synchronized void produce(int value) throws InterruptedException {
            while (buffer.size() == capacity) {
                System.out.println("Buffer full. Producer waiting...");
                wait(); // Release lock and wait
            }
            buffer.offer(value);
            System.out.println("Produced: " + value + " | Buffer size: " + buffer.size());
            notifyAll(); // Wake up consumers
        }
        
        public synchronized int consume() throws InterruptedException {
            while (buffer.isEmpty()) {
                System.out.println("Buffer empty. Consumer waiting...");
                wait(); // Release lock and wait
            }
            int value = buffer.poll();
            System.out.println("Consumed: " + value + " | Buffer size: " + buffer.size());
            notifyAll(); // Wake up producers
            return value;
        }
        
        public void demonstrate() throws InterruptedException {
            System.out.println("\n=== DEMO: Producer-Consumer Pattern ===");
            
            // Producer thread
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 10; i++) {
                        produce(i);
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            // Consumer thread
            Thread consumer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 10; i++) {
                        consume();
                        Thread.sleep(150);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
        }
    }

    // ==================== SECTION 3: PRINT SEQUENCE (Foo Bar) ====================
    
    /**
     * QUESTION 3: Print "FooBar" alternately using two threads
     * LeetCode #1115: Print FooBar Alternately
     * 
     * INTERVIEW TIP: Very common in FAANG interviews!
     */
    static class FooBar {
        private int n;
        private volatile boolean fooTurn = true;
        
        public FooBar(int n) {
            this.n = n;
        }
        
        public synchronized void foo() throws InterruptedException {
            for (int i = 0; i < n; i++) {
                while (!fooTurn) {
                    wait();
                }
                System.out.print("Foo");
                fooTurn = false;
                notifyAll();
            }
        }
        
        public synchronized void bar() throws InterruptedException {
            for (int i = 0; i < n; i++) {
                while (fooTurn) {
                    wait();
                }
                System.out.print("Bar ");
                fooTurn = true;
                notifyAll();
            }
        }
        
        public void demonstrate() throws InterruptedException {
            System.out.println("\n=== DEMO: Print FooBar Alternately ===");
            Thread t1 = new Thread(() -> {
                try { foo(); } catch (InterruptedException e) {}
            });
            Thread t2 = new Thread(() -> {
                try { bar(); } catch (InterruptedException e) {}
            });
            
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            System.out.println();
        }
    }

    // ==================== SECTION 4: THREAD-SAFE SINGLETON ====================
    
    /**
     * QUESTION 4: Implement Thread-Safe Singleton
     * Multiple approaches: synchronized, double-checked locking, enum
     * 
     * INTERVIEW TIP: Know the trade-offs of each approach!
     */
    
    // Approach 1: Synchronized method (simple but slow)
    static class SingletonSynchronized {
        private static SingletonSynchronized instance;
        
        private SingletonSynchronized() {}
        
        public static synchronized SingletonSynchronized getInstance() {
            if (instance == null) {
                instance = new SingletonSynchronized();
            }
            return instance;
        }
    }
    
    // Approach 2: Double-Checked Locking (best performance)
    static class SingletonDoubleChecked {
        private static volatile SingletonDoubleChecked instance;
        
        private SingletonDoubleChecked() {}
        
        public static SingletonDoubleChecked getInstance() {
            if (instance == null) { // First check (no locking)
                synchronized (SingletonDoubleChecked.class) {
                    if (instance == null) { // Second check (with locking)
                        instance = new SingletonDoubleChecked();
                    }
                }
            }
            return instance;
        }
    }
    
    // Approach 3: Bill Pugh Singleton (recommended)
    static class SingletonBillPugh {
        private SingletonBillPugh() {}
        
        private static class SingletonHelper {
            private static final SingletonBillPugh INSTANCE = new SingletonBillPugh();
        }
        
        public static SingletonBillPugh getInstance() {
            return SingletonHelper.INSTANCE;
        }
    }
    
    // Approach 4: Enum Singleton (most robust, handles serialization)
    enum SingletonEnum {
        INSTANCE;
        
        public void doSomething() {
            System.out.println("Enum Singleton method called");
        }
    }

    // ==================== SECTION 5: DINING PHILOSOPHERS ====================
    
    /**
     * QUESTION 5: Dining Philosophers Problem
     * Demonstrates: Deadlock prevention using ordered resource acquisition
     * 
     * INTERVIEW TIP: Explain deadlock conditions and how to prevent them!
     */
    static class DiningPhilosophers {
        private final int numPhilosophers = 5;
        private final ReentrantLock[] forks = new ReentrantLock[numPhilosophers];
        
        public DiningPhilosophers() {
            for (int i = 0; i < numPhilosophers; i++) {
                forks[i] = new ReentrantLock();
            }
        }
        
        class Philosopher implements Runnable {
            private int id;
            
            public Philosopher(int id) {
                this.id = id;
            }
            
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 3; i++) {
                        think();
                        eat();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            private void think() throws InterruptedException {
                System.out.println("Philosopher " + id + " is thinking");
                Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300));
            }
            
            private void eat() throws InterruptedException {
                int leftFork = id;
                int rightFork = (id + 1) % numPhilosophers;
                
                // Deadlock prevention: always acquire lower-numbered fork first
                int firstFork = Math.min(leftFork, rightFork);
                int secondFork = Math.max(leftFork, rightFork);
                
                forks[firstFork].lock();
                try {
                    forks[secondFork].lock();
                    try {
                        System.out.println("Philosopher " + id + " is eating");
                        Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300));
                    } finally {
                        forks[secondFork].unlock();
                    }
                } finally {
                    forks[firstFork].unlock();
                }
            }
        }
        
        public void demonstrate() throws InterruptedException {
            System.out.println("\n=== DEMO: Dining Philosophers (Deadlock Prevention) ===");
            Thread[] philosophers = new Thread[numPhilosophers];
            
            for (int i = 0; i < numPhilosophers; i++) {
                philosophers[i] = new Thread(new Philosopher(i));
                philosophers[i].start();
            }
            
            for (Thread p : philosophers) {
                p.join();
            }
            System.out.println("All philosophers finished eating!");
        }
    }

    // ==================== SECTION 6: THREAD POOL & EXECUTORS ====================
    
    /**
     * QUESTION 6: Demonstrate Thread Pool usage
     * Shows: ExecutorService, Future, Callable
     * 
     * INTERVIEW TIP: Know different types of thread pools!
     */
    static class ThreadPoolDemo {
        public void demonstrate() throws InterruptedException, ExecutionException {
            System.out.println("\n=== DEMO: Thread Pool & Executors ===");
            
            // Fixed thread pool
            ExecutorService executor = Executors.newFixedThreadPool(3);
            
            // Submit tasks that return results
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                final int taskId = i;
                Future<Integer> future = executor.submit(() -> {
                    System.out.println("Task " + taskId + " running on " + 
                                     Thread.currentThread().getName());
                    Thread.sleep(1000);
                    return taskId * taskId;
                });
                futures.add(future);
            }
            
            // Get results
            for (int i = 0; i < futures.size(); i++) {
                System.out.println("Result of task " + (i + 1) + ": " + futures.get(i).get());
            }
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ==================== SECTION 7: BOUNDED BLOCKING QUEUE ====================
    
    /**
     * QUESTION 7: Implement a Bounded Blocking Queue
     * Demonstrates: Custom concurrent data structure
     * 
     * INTERVIEW TIP: This tests understanding of locks and conditions!
     */
    static class BoundedBlockingQueue<T> {
        private final Queue<T> queue;
        private final int capacity;
        private final ReentrantLock lock;
        private final Condition notFull;
        private final Condition notEmpty;
        
        public BoundedBlockingQueue(int capacity) {
            this.capacity = capacity;
            this.queue = new LinkedList<>();
            this.lock = new ReentrantLock();
            this.notFull = lock.newCondition();
            this.notEmpty = lock.newCondition();
        }
        
        public void enqueue(T item) throws InterruptedException {
            lock.lock();
            try {
                while (queue.size() == capacity) {
                    notFull.await(); // Wait until not full
                }
                queue.offer(item);
                System.out.println("Enqueued: " + item + " | Size: " + queue.size());
                notEmpty.signal(); // Signal that queue is not empty
            } finally {
                lock.unlock();
            }
        }
        
        public T dequeue() throws InterruptedException {
            lock.lock();
            try {
                while (queue.isEmpty()) {
                    notEmpty.await(); // Wait until not empty
                }
                T item = queue.poll();
                System.out.println("Dequeued: " + item + " | Size: " + queue.size());
                notFull.signal(); // Signal that queue is not full
                return item;
            } finally {
                lock.unlock();
            }
        }
        
        public void demonstrate() throws InterruptedException {
            System.out.println("\n=== DEMO: Bounded Blocking Queue ===");
            BoundedBlockingQueue<Integer> queue = new BoundedBlockingQueue<>(5);
            
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 10; i++) {
                        queue.enqueue(i);
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {}
            });
            
            Thread consumer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 10; i++) {
                        queue.dequeue();
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {}
            });
            
            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
        }
    }

    // ==================== SECTION 8: READ-WRITE LOCK ====================
    
    /**
     * QUESTION 8: Implement Read-Write Lock Pattern
     * Demonstrates: Multiple readers, single writer
     * 
     * INTERVIEW TIP: Explain when to use ReadWriteLock vs synchronized!
     */
    static class ReadWriteLockDemo {
        private int value = 0;
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Lock readLock = rwLock.readLock();
        private final Lock writeLock = rwLock.writeLock();
        
        public int read() {
            readLock.lock();
            try {
                System.out.println(Thread.currentThread().getName() + " reading: " + value);
                Thread.sleep(100);
                return value;
            } catch (InterruptedException e) {
                return -1;
            } finally {
                readLock.unlock();
            }
        }
        
        public void write(int newValue) {
            writeLock.lock();
            try {
                System.out.println(Thread.currentThread().getName() + " writing: " + newValue);
                value = newValue;
                Thread.sleep(200);
            } catch (InterruptedException e) {
            } finally {
                writeLock.unlock();
            }
        }
        
        public void demonstrate() throws InterruptedException {
            System.out.println("\n=== DEMO: Read-Write Lock ===");
            
            // Multiple readers
            Thread[] readers = new Thread[3];
            for (int i = 0; i < readers.length; i++) {
                readers[i] = new Thread(() -> {
                    for (int j = 0; j < 3; j++) {
                        read();
                    }
                }, "Reader-" + i);
                readers[i].start();
            }
            
            // Single writer
            Thread writer = new Thread(() -> {
                for (int i = 1; i <= 3; i++) {
                    write(i);
                }
            }, "Writer");
            writer.start();
            
            for (Thread t : readers) t.join();
            writer.join();
        }
    }

    // ==================== SECTION 9: CountDownLatch & CyclicBarrier ====================
    
    /**
     * QUESTION 9: Demonstrate CountDownLatch and CyclicBarrier
     * Shows: Thread coordination utilities
     * 
     * INTERVIEW TIP: Know the difference between CountDownLatch and CyclicBarrier!
     */
    static class SynchronizationUtilities {
        
        // CountDownLatch: One-time event, threads wait for count to reach zero
        public void demonstrateCountDownLatch() throws InterruptedException {
            System.out.println("\n=== DEMO: CountDownLatch ===");
            int numWorkers = 3;
            CountDownLatch latch = new CountDownLatch(numWorkers);
            
            for (int i = 0; i < numWorkers; i++) {
                final int workerId = i;
                new Thread(() -> {
                    try {
                        System.out.println("Worker " + workerId + " starting work");
                        Thread.sleep(1000 + workerId * 500);
                        System.out.println("Worker " + workerId + " finished work");
                        latch.countDown(); // Decrement count
                    } catch (InterruptedException e) {}
                }).start();
            }
            
            System.out.println("Main thread waiting for all workers...");
            latch.await(); // Wait for count to reach zero
            System.out.println("All workers finished! Main thread proceeding.");
        }
        
        // CyclicBarrier: Reusable, all threads wait at barrier
        public void demonstrateCyclicBarrier() throws InterruptedException {
            System.out.println("\n=== DEMO: CyclicBarrier ===");
            int numThreads = 3;
            CyclicBarrier barrier = new CyclicBarrier(numThreads, () -> {
                System.out.println("*** All threads reached barrier! ***");
            });
            
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                new Thread(() -> {
                    try {
                        System.out.println("Thread " + threadId + " working on phase 1");
                        Thread.sleep(1000 + threadId * 300);
                        System.out.println("Thread " + threadId + " reached barrier");
                        barrier.await(); // Wait for all threads
                        
                        System.out.println("Thread " + threadId + " working on phase 2");
                        Thread.sleep(500);
                        System.out.println("Thread " + threadId + " completed phase 2");
                    } catch (Exception e) {}
                }).start();
            }
            
            Thread.sleep(5000); // Give threads time to complete
        }
    }

    // ==================== SECTION 10: DEADLOCK ====================
    
    /**
     * QUESTION 10: Demonstrate Deadlock and Prevention
     * Shows: How deadlocks occur and how to prevent them
     * 
     * INTERVIEW TIP: Know the 4 conditions for deadlock!
     * 1. Mutual Exclusion
     * 2. Hold and Wait
     * 3. No Preemption
     * 4. Circular Wait
     */
    static class DeadlockDemo {
        private final Object lock1 = new Object();
        private final Object lock2 = new Object();
        
        public void demonstrateDeadlock() {
            System.out.println("\n=== DEMO: Deadlock (Don't run this - it will hang!) ===");
            System.out.println("Thread 1 tries: lock1 -> lock2");
            System.out.println("Thread 2 tries: lock2 -> lock1");
            System.out.println("This creates a circular wait -> DEADLOCK!");
            
            // Uncomment to see actual deadlock (WARNING: will hang!)
            /*
            Thread t1 = new Thread(() -> {
                synchronized (lock1) {
                    System.out.println("Thread 1: Holding lock1...");
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                    System.out.println("Thread 1: Waiting for lock2...");
                    synchronized (lock2) {
                        System.out.println("Thread 1: Holding lock1 & lock2");
                    }
                }
            });
            
            Thread t2 = new Thread(() -> {
                synchronized (lock2) {
                    System.out.println("Thread 2: Holding lock2...");
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                    System.out.println("Thread 2: Waiting for lock1...");
                    synchronized (lock1) {
                        System.out.println("Thread 2: Holding lock2 & lock1");
                    }
                }
            });
            
            t1.start();
            t2.start();
            */
        }
        
        public void demonstrateDeadlockPrevention() throws InterruptedException {
            System.out.println("\n=== DEMO: Deadlock Prevention (Ordered Lock Acquisition) ===");
            
            Thread t1 = new Thread(() -> {
                synchronized (lock1) {
                    System.out.println("Thread 1: Holding lock1...");
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                    System.out.println("Thread 1: Waiting for lock2...");
                    synchronized (lock2) {
                        System.out.println("Thread 1: Holding lock1 & lock2");
                    }
                }
            });
            
            Thread t2 = new Thread(() -> {
                // SOLUTION: Both threads acquire locks in same order
                synchronized (lock1) {
                    System.out.println("Thread 2: Holding lock1...");
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                    System.out.println("Thread 2: Waiting for lock2...");
                    synchronized (lock2) {
                        System.out.println("Thread 2: Holding lock1 & lock2");
                    }
                }
            });
            
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            System.out.println("No deadlock! Both threads completed successfully.");
        }
    }

    // ==================== SECTION 11: THREAD-SAFE DATA STRUCTURES ====================
    
    /**
     * QUESTION 11: Demonstrate Thread-Safe Collections
     * Shows: ConcurrentHashMap, CopyOnWriteArrayList, AtomicInteger
     * 
     * INTERVIEW TIP: Know when to use each concurrent collection!
     */
    static class ThreadSafeDataStructures {
        
        public void demonstrateConcurrentHashMap() throws InterruptedException {
            System.out.println("\n=== DEMO: ConcurrentHashMap ===");
            ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
            
            // Multiple threads updating the map concurrently
            Thread[] threads = new Thread[5];
            for (int i = 0; i < threads.length; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 3; j++) {
                        String key = "key" + threadId;
                        map.merge(key, 1, Integer::sum); // Thread-safe increment
                        System.out.println("Thread " + threadId + " updated " + key + 
                                         " to " + map.get(key));
                    }
                });
                threads[i].start();
            }
            
            for (Thread t : threads) t.join();
            System.out.println("Final map: " + map);
        }
        
        public void demonstrateAtomicInteger() throws InterruptedException {
            System.out.println("\n=== DEMO: AtomicInteger (Thread-Safe Counter) ===");
            AtomicInteger counter = new AtomicInteger(0);
            
            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        counter.incrementAndGet(); // Lock-free atomic operation
                    }
                });
                threads[i].start();
            }
            
            for (Thread t : threads) t.join();
            System.out.println("Final counter value: " + counter.get() + 
                             " (Expected: 1000)");
        }
        
        public void demonstrateCopyOnWriteArrayList() throws InterruptedException {
            System.out.println("\n=== DEMO: CopyOnWriteArrayList ===");
            CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
            
            // Writer thread
            Thread writer = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    list.add(i);
                    System.out.println("Added: " + i);
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                }
            });
            
            // Reader thread (can iterate without ConcurrentModificationException)
            Thread reader = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    System.out.println("Reading list: " + list);
                    try { Thread.sleep(50); } catch (InterruptedException e) {}
                }
            });
            
            writer.start();
            reader.start();
            writer.join();
            reader.join();
        }
    }

    // ==================== SECTION 12: FUTURE & CALLABLE ====================
    
    /**
     * QUESTION 12: Demonstrate Future and Callable
     * Shows: Asynchronous computation with results
     * 
     * INTERVIEW TIP: Know the difference between Runnable and Callable!
     */
    static class FutureCallableDemo {
        
        public void demonstrate() throws InterruptedException, ExecutionException {
            System.out.println("\n=== DEMO: Future & Callable ===");
            ExecutorService executor = Executors.newFixedThreadPool(2);
            
            // Submit a Callable that returns a result
            Callable<Integer> task = () -> {
                System.out.println("Computing factorial of 10...");
                Thread.sleep(1000);
                int result = 1;
                for (int i = 1; i <= 10; i++) {
                    result *= i;
                }
                return result;
            };
            
            Future<Integer> future = executor.submit(task);
            
            // Do other work while computation runs
            System.out.println("Main thread doing other work...");
            Thread.sleep(500);
            
            // Check if computation is done
            if (!future.isDone()) {
                System.out.println("Computation still in progress...");
            }
            
            // Block and get result
            Integer result = future.get();
            System.out.println("Factorial result: " + result);
            
            // CompletableFuture example (Java 8+)
            System.out.println("\n--- CompletableFuture Example ---");
            CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(500); } catch (InterruptedException e) {}
                return "Hello";
            });
            
            CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(300); } catch (InterruptedException e) {}
                return "World";
            });
            
            // Combine results
            CompletableFuture<String> combined = cf1.thenCombine(cf2, (s1, s2) -> s1 + " " + s2);
            System.out.println("Combined result: " + combined.get());
            
            executor.shutdown();
        }
    }

    // ==================== MAIN METHOD - RUN ALL DEMOS ====================
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║   CONCURRENCY & PARALLELISM - TOP INTERVIEW QUESTIONS         ║");
        System.out.println("║   Comprehensive Guide with Practical Examples                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        
        try {
            // Run all demonstrations
            new PrintNumbersWithThreads(15, 3).demonstrate();
            
            new ProducerConsumer(3).demonstrate();
            
            new FooBar(5).demonstrate();
            
            System.out.println("\n=== DEMO: Thread-Safe Singleton Patterns ===");
            System.out.println("Created singleton instances using 4 different approaches");
            System.out.println("1. Synchronized Method");
            System.out.println("2. Double-Checked Locking");
            System.out.println("3. Bill Pugh (Inner Static Class)");
            System.out.println("4. Enum Singleton");
            SingletonEnum.INSTANCE.doSomething();
            
            new DiningPhilosophers().demonstrate();
            
            new ThreadPoolDemo().demonstrate();
            
            new BoundedBlockingQueue<>(5).demonstrate();
            
            new ReadWriteLockDemo().demonstrate();
            
            SynchronizationUtilities syncUtils = new SynchronizationUtilities();
            syncUtils.demonstrateCountDownLatch();
            syncUtils.demonstrateCyclicBarrier();
            
            DeadlockDemo deadlockDemo = new DeadlockDemo();
            deadlockDemo.demonstrateDeadlock();
            deadlockDemo.demonstrateDeadlockPrevention();
            
            ThreadSafeDataStructures threadSafeDS = new ThreadSafeDataStructures();
            threadSafeDS.demonstrateConcurrentHashMap();
            threadSafeDS.demonstrateAtomicInteger();
            threadSafeDS.demonstrateCopyOnWriteArrayList();
            
            new FutureCallableDemo().demonstrate();
            
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║   ALL DEMONSTRATIONS COMPLETED SUCCESSFULLY!                   ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.err.println("Error during demonstration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
