import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * 20 REAL-WORLD CONCURRENCY INTERVIEW EXAMPLES
 * Each example is self-contained, runnable, and interview-ready.
 *
 * Compile: javac ConcurrencyRealWorldExamples.java
 * Run:     java ConcurrencyRealWorldExamples
 */
public class ConcurrencyRealWorldExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   20 REAL-WORLD CONCURRENCY INTERVIEW EXAMPLES               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        Ex01_SeatBooking.demo();
        Ex02_BankWithdrawal.demo();
        Ex03_MoneyTransfer.demo();
        Ex04_FlashSale.demo();
        Ex05_RateLimiter.demo();
        Ex06_TaskProcessing.demo();
        Ex07_EmailQueue.demo();
        Ex08_PrintInOrder.demo();
        Ex09_ParallelApiCalls.demo();
        Ex10_ServiceStartup.demo();
        Ex11_GameBarrier.demo();
        Ex12_SharedCounter.demo();
        Ex13_Singleton.demo();
        Ex14_ReaderWriter.demo();
        Ex15_ScheduledJob.demo();
        Ex16_CacheWithExpiry.demo();
        Ex17_HighContentionCounter.demo();
        Ex18_IdempotentPayment.demo();
        Ex19_DownloadSlots.demo();
        Ex20_AsyncWorkflow.demo();

        System.out.println("\n✅ ALL 20 EXAMPLES COMPLETED!");
    }

    // ════════════════════════════════════════════════════════════════
    // 1. SEAT BOOKING — synchronized, ReentrantLock, AtomicInteger
    //    Prevent double booking when multiple users book the last seat
    // ════════════════════════════════════════════════════════════════
    static class Ex01_SeatBooking {
        private final AtomicInteger seats;

        Ex01_SeatBooking(int seats) { this.seats = new AtomicInteger(seats); }

        boolean book(String user) {
            while (true) {
                int cur = seats.get();
                if (cur <= 0) return false;
                if (seats.compareAndSet(cur, cur - 1)) {
                    System.out.println("  " + user + " booked! Left: " + (cur - 1));
                    return true;
                }
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 1. SEAT BOOKING (AtomicInteger CAS) ===");
            Ex01_SeatBooking sys = new Ex01_SeatBooking(2);
            ExecutorService e = Executors.newFixedThreadPool(5);
            for (int i = 1; i <= 5; i++) {
                int id = i;
                e.submit(() -> {
                    if (!sys.book("User" + id)) System.out.println("  User" + id + " FAILED");
                });
            }
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 2. BANK WITHDRAWAL — ReentrantLock
    //    Balance must never go negative with concurrent withdrawals
    // ════════════════════════════════════════════════════════════════
    static class Ex02_BankWithdrawal {
        private double balance;
        private final ReentrantLock lock = new ReentrantLock();

        Ex02_BankWithdrawal(double b) { this.balance = b; }

        boolean withdraw(String user, double amt) {
            lock.lock();
            try {
                if (balance >= amt) {
                    balance -= amt;
                    System.out.printf("  %s withdrew $%.0f | Balance: $%.0f%n", user, amt, balance);
                    return true;
                }
                System.out.printf("  %s FAILED $%.0f | Balance: $%.0f%n", user, amt, balance);
                return false;
            } finally { lock.unlock(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 2. BANK WITHDRAWAL (ReentrantLock) ===");
            Ex02_BankWithdrawal acc = new Ex02_BankWithdrawal(500);
            ExecutorService e = Executors.newFixedThreadPool(5);
            for (int i = 1; i <= 5; i++) {
                int id = i;
                e.submit(() -> acc.withdraw("User" + id, 200));
            }
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.printf("  Final balance: $%.0f%n", acc.balance);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 3. MONEY TRANSFER — Lock ordering to prevent deadlock
    //    A→B and B→A concurrently can deadlock without ordering
    // ════════════════════════════════════════════════════════════════
    static class Ex03_MoneyTransfer {
        static class Account {
            final int id;
            double balance;
            final ReentrantLock lock = new ReentrantLock();
            Account(int id, double b) { this.id = id; this.balance = b; }
        }

        static void transfer(Account from, Account to, double amt) {
            Account first = from.id < to.id ? from : to;
            Account second = from.id < to.id ? to : from;
            first.lock.lock();
            try {
                second.lock.lock();
                try {
                    if (from.balance >= amt) {
                        from.balance -= amt;
                        to.balance += amt;
                        System.out.printf("  $%.0f: Acc%d→Acc%d | Bal: %.0f, %.0f%n",
                                amt, from.id, to.id, from.balance, to.balance);
                    }
                } finally { second.lock.unlock(); }
            } finally { first.lock.unlock(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 3. MONEY TRANSFER (Lock Ordering) ===");
            Account a = new Account(1, 1000), b = new Account(2, 1000);
            ExecutorService e = Executors.newFixedThreadPool(4);
            for (int i = 0; i < 3; i++) {
                e.submit(() -> transfer(a, b, 100));
                e.submit(() -> transfer(b, a, 50));
            }
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.printf("  Total: $%.0f (should be 2000)%n", a.balance + b.balance);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 4. FLASH SALE — AtomicInteger CAS, no overselling
    // ════════════════════════════════════════════════════════════════
    static class Ex04_FlashSale {
        private final AtomicInteger stock;
        Ex04_FlashSale(int s) { stock = new AtomicInteger(s); }

        boolean purchase() {
            while (true) {
                int cur = stock.get();
                if (cur <= 0) return false;
                if (stock.compareAndSet(cur, cur - 1)) return true;
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 4. FLASH SALE (CAS, no overselling) ===");
            Ex04_FlashSale sale = new Ex04_FlashSale(10);
            AtomicInteger wins = new AtomicInteger(), losses = new AtomicInteger();
            ExecutorService e = Executors.newFixedThreadPool(50);
            CountDownLatch done = new CountDownLatch(100);
            for (int i = 0; i < 100; i++) {
                e.submit(() -> {
                    if (sale.purchase()) wins.incrementAndGet();
                    else losses.incrementAndGet();
                    done.countDown();
                });
            }
            done.await();
            e.shutdown();
            System.out.printf("  Stock=10 | Buyers=100 | Won=%d | Lost=%d | Left=%d%n",
                    wins.get(), losses.get(), sale.stock.get());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 5. RATE LIMITER — Semaphore (max N concurrent)
    // ════════════════════════════════════════════════════════════════
    static class Ex05_RateLimiter {
        private final Semaphore sem;
        Ex05_RateLimiter(int max) { sem = new Semaphore(max); }

        void callApi(String req) throws InterruptedException {
            sem.acquire();
            try {
                System.out.println("  → " + req + " processing (avail=" + sem.availablePermits() + ")");
                Thread.sleep(150);
            } finally {
                sem.release();
                System.out.println("  ← " + req + " done");
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 5. RATE LIMITER (Semaphore, max 3) ===");
            Ex05_RateLimiter lim = new Ex05_RateLimiter(3);
            ExecutorService e = Executors.newFixedThreadPool(8);
            for (int i = 1; i <= 6; i++) {
                int id = i;
                e.submit(() -> { try { lim.callApi("Req" + id); } catch (Exception ex) {} });
            }
            e.shutdown();
            e.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 6. TASK PROCESSING — ThreadPoolExecutor with bounded queue
    // ════════════════════════════════════════════════════════════════
    static class Ex06_TaskProcessing {
        static void demo() throws Exception {
            System.out.println("\n=== 6. TASK PROCESSING (ThreadPoolExecutor) ===");
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    2, 4, 30, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(3),
                    new ThreadPoolExecutor.CallerRunsPolicy());

            List<Future<String>> futures = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                int id = i;
                futures.add(pool.submit(() -> {
                    Thread.sleep(80);
                    return "Job" + id + " done by " + Thread.currentThread().getName();
                }));
            }
            for (Future<String> f : futures) System.out.println("  " + f.get());
            pool.shutdown();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 7. EMAIL QUEUE — BlockingQueue Producer-Consumer
    // ════════════════════════════════════════════════════════════════
    static class Ex07_EmailQueue {
        static void demo() throws Exception {
            System.out.println("\n=== 7. EMAIL QUEUE (Producer-Consumer) ===");
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);

            // Producer
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 5; i++) {
                        String email = "user" + i + "@mail.com";
                        queue.put(email);
                        System.out.println("  [P] Queued: " + email);
                    }
                    queue.put("STOP"); // poison pill
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });

            // Consumer
            Thread consumer = new Thread(() -> {
                try {
                    while (true) {
                        String email = queue.take();
                        if ("STOP".equals(email)) break;
                        Thread.sleep(50);
                        System.out.println("  [C] Sent: " + email);
                    }
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });

            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 8. PRINT IN ORDER — CountDownLatch (simplest approach)
    //    Thread C prints 3, Thread B prints 2, Thread A prints 1
    //    Must output: 1 2 3
    // ════════════════════════════════════════════════════════════════
    static class Ex08_PrintInOrder {
        private final CountDownLatch l1 = new CountDownLatch(1);
        private final CountDownLatch l2 = new CountDownLatch(1);

        void first()  { System.out.print("1 "); l1.countDown(); }
        void second() throws InterruptedException { l1.await(); System.out.print("2 "); l2.countDown(); }
        void third()  throws InterruptedException { l2.await(); System.out.println("3"); }

        static void demo() throws Exception {
            System.out.println("\n=== 8. PRINT IN ORDER (CountDownLatch) ===");
            Ex08_PrintInOrder p = new Ex08_PrintInOrder();
            System.out.print("  Output: ");
            Thread t3 = new Thread(() -> { try { p.third(); } catch (Exception e) {} });
            Thread t2 = new Thread(() -> { try { p.second(); } catch (Exception e) {} });
            Thread t1 = new Thread(p::first);
            t3.start(); t2.start(); Thread.sleep(30); t1.start();
            t1.join(); t2.join(); t3.join();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 9. PARALLEL API CALLS — CompletableFuture
    //    Call 3 services in parallel, wait for all, combine results
    // ════════════════════════════════════════════════════════════════
    static class Ex09_ParallelApiCalls {
        static String call(String svc, int ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) {}
            return svc + "-OK";
        }

        static void demo() throws Exception {
            System.out.println("\n=== 9. PARALLEL API CALLS (CompletableFuture) ===");
            long start = System.currentTimeMillis();

            CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> call("UserSvc", 200));
            CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> call("OrderSvc", 300));
            CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> call("PaySvc", 100));

            CompletableFuture.allOf(f1, f2, f3).join();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  Results: " + f1.get() + ", " + f2.get() + ", " + f3.get());
            System.out.println("  Total time: " + elapsed + "ms (parallel, not 600ms sequential)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 10. SERVICE STARTUP DEPENDENCY — CountDownLatch
    //     App starts only after DB + Cache are ready
    // ════════════════════════════════════════════════════════════════
    static class Ex10_ServiceStartup {
        static void demo() throws Exception {
            System.out.println("\n=== 10. SERVICE STARTUP (CountDownLatch) ===");
            CountDownLatch dbReady = new CountDownLatch(1);
            CountDownLatch cacheReady = new CountDownLatch(1);

            new Thread(() -> {
                try {
                    System.out.println("  [DB] Starting...");
                    Thread.sleep(150);
                    System.out.println("  [DB] Ready!");
                    dbReady.countDown();
                } catch (InterruptedException e) {}
            }).start();

            new Thread(() -> {
                try {
                    dbReady.await();
                    System.out.println("  [Cache] DB up → initializing...");
                    Thread.sleep(100);
                    System.out.println("  [Cache] Ready!");
                    cacheReady.countDown();
                } catch (InterruptedException e) {}
            }).start();

            cacheReady.await();
            System.out.println("  [App] All deps ready → App started!");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 11. GAME ROUND BARRIER — CyclicBarrier
    //     All players must finish round before next round starts
    // ════════════════════════════════════════════════════════════════
    static class Ex11_GameBarrier {
        static void demo() throws Exception {
            System.out.println("\n=== 11. GAME ROUND BARRIER (CyclicBarrier) ===");
            int players = 3;
            int rounds = 2;
            CyclicBarrier barrier = new CyclicBarrier(players,
                    () -> System.out.println("  --- All players done! Next round ---"));

            ExecutorService e = Executors.newFixedThreadPool(players);
            for (int p = 1; p <= players; p++) {
                int pid = p;
                e.submit(() -> {
                    try {
                        for (int r = 1; r <= rounds; r++) {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
                            System.out.println("  Player" + pid + " finished round " + r);
                            barrier.await();
                        }
                    } catch (Exception ex) {}
                });
            }
            e.shutdown();
            e.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 12. SHARED COUNTER — AtomicInteger vs synchronized
    // ════════════════════════════════════════════════════════════════
    static class Ex12_SharedCounter {
        static class SyncCounter {
            private int count = 0;
            synchronized void inc() { count++; }
            synchronized int get() { return count; }
        }

        static class AtomicCounter {
            private final AtomicInteger count = new AtomicInteger(0);
            void inc() { count.incrementAndGet(); }
            int get() { return count.get(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 12. SHARED COUNTER (Atomic vs Sync) ===");
            AtomicCounter ac = new AtomicCounter();
            SyncCounter sc = new SyncCounter();
            int threads = 10, opsPerThread = 10000;
            ExecutorService e = Executors.newFixedThreadPool(threads);
            CountDownLatch done = new CountDownLatch(threads * 2);

            long t1 = System.nanoTime();
            for (int i = 0; i < threads; i++) {
                e.submit(() -> {
                    for (int j = 0; j < opsPerThread; j++) ac.inc();
                    done.countDown();
                });
                e.submit(() -> {
                    for (int j = 0; j < opsPerThread; j++) sc.inc();
                    done.countDown();
                });
            }
            done.await();
            long t2 = System.nanoTime();
            e.shutdown();

            System.out.println("  AtomicCounter = " + ac.get() + " (expected " + threads * opsPerThread + ")");
            System.out.println("  SyncCounter   = " + sc.get() + " (expected " + threads * opsPerThread + ")");
            System.out.println("  Time: " + (t2 - t1) / 1_000_000 + "ms");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 13. SINGLETON — Double-Checked Locking + volatile
    // ════════════════════════════════════════════════════════════════
    static class Ex13_Singleton {
        // Approach 1: Double-Checked Locking
        static class DCLSingleton {
            private static volatile DCLSingleton instance;  // volatile prevents reordering!
            private DCLSingleton() { System.out.println("  [DCL] Instance created"); }

            static DCLSingleton getInstance() {
                if (instance == null) {                     // 1st check (no lock)
                    synchronized (DCLSingleton.class) {
                        if (instance == null) {             // 2nd check (with lock)
                            instance = new DCLSingleton();
                        }
                    }
                }
                return instance;
            }
        }

        // Approach 2: Bill Pugh (Holder pattern) — best!
        static class HolderSingleton {
            private HolderSingleton() { System.out.println("  [Holder] Instance created"); }
            private static class Holder {
                static final HolderSingleton INSTANCE = new HolderSingleton();
            }
            static HolderSingleton getInstance() { return Holder.INSTANCE; }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 13. SINGLETON (DCL + Holder) ===");
            ExecutorService e = Executors.newFixedThreadPool(5);
            // Multiple threads get same instance
            Set<Integer> ids = ConcurrentHashMap.newKeySet();
            CountDownLatch done = new CountDownLatch(5);
            for (int i = 0; i < 5; i++) {
                e.submit(() -> {
                    ids.add(System.identityHashCode(HolderSingleton.getInstance()));
                    done.countDown();
                });
            }
            done.await();
            e.shutdown();
            System.out.println("  Unique instances: " + ids.size() + " (should be 1)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 14. READER-WRITER — ReadWriteLock
    //     Many readers, few writers (optimize read-heavy systems)
    // ════════════════════════════════════════════════════════════════
    static class Ex14_ReaderWriter {
        private final Map<String, String> data = new HashMap<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        String read(String key) {
            rwLock.readLock().lock();
            try { return data.getOrDefault(key, "N/A"); }
            finally { rwLock.readLock().unlock(); }
        }

        void write(String key, String val) {
            rwLock.writeLock().lock();
            try { data.put(key, val); }
            finally { rwLock.writeLock().unlock(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 14. READER-WRITER (ReadWriteLock) ===");
            Ex14_ReaderWriter sys = new Ex14_ReaderWriter();
            sys.write("config", "v1");

            ExecutorService e = Executors.newFixedThreadPool(6);
            // 1 writer, 5 readers running concurrently
            e.submit(() -> { sys.write("config", "v2"); System.out.println("  [W] Wrote v2"); });
            for (int i = 1; i <= 5; i++) {
                int id = i;
                e.submit(() -> System.out.println("  [R" + id + "] Read: " + sys.read("config")));
            }
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 15. SCHEDULED CLEANUP JOB — ScheduledExecutorService
    //     Run a task periodically (e.g., cleanup every 5 min)
    // ════════════════════════════════════════════════════════════════
    static class Ex15_ScheduledJob {
        static void demo() throws Exception {
            System.out.println("\n=== 15. SCHEDULED JOB (ScheduledExecutor) ===");
            ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
            AtomicInteger runs = new AtomicInteger(0);

            // Run every 100ms (simulating "every 5 min" for demo)
            ScheduledFuture<?> future = sched.scheduleAtFixedRate(() -> {
                int r = runs.incrementAndGet();
                System.out.println("  [Cleanup] Run #" + r + " at " + System.currentTimeMillis() % 10000);
            }, 0, 100, TimeUnit.MILLISECONDS);

            Thread.sleep(350); // Let it run ~3 times
            future.cancel(false);
            sched.shutdown();
            System.out.println("  Total cleanup runs: " + runs.get());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 16. CACHE WITH EXPIRY — ConcurrentHashMap
    // ════════════════════════════════════════════════════════════════
    static class Ex16_CacheWithExpiry {
        static class ExpiringCache<K, V> {
            private final ConcurrentHashMap<K, CacheEntry<V>> map = new ConcurrentHashMap<>();
            private final long ttlMs;

            ExpiringCache(long ttlMs) { this.ttlMs = ttlMs; }

            static class CacheEntry<V> {
                final V value;
                final long expiresAt;
                CacheEntry(V v, long exp) { value = v; expiresAt = exp; }
                boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
            }

            void put(K key, V value) {
                map.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMs));
            }

            V get(K key) {
                CacheEntry<V> entry = map.get(key);
                if (entry == null) return null;
                if (entry.isExpired()) { map.remove(key); return null; }
                return entry.value;
            }

            // Thread-safe compute-if-absent with expiry check
            V getOrCompute(K key, java.util.function.Function<K, V> loader) {
                CacheEntry<V> entry = map.get(key);
                if (entry != null && !entry.isExpired()) return entry.value;

                // computeIfAbsent is atomic in ConcurrentHashMap
                CacheEntry<V> newEntry = map.compute(key, (k, existing) -> {
                    if (existing != null && !existing.isExpired()) return existing;
                    return new CacheEntry<>(loader.apply(k), System.currentTimeMillis() + ttlMs);
                });
                return newEntry.value;
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 16. CACHE WITH EXPIRY (ConcurrentHashMap) ===");
            ExpiringCache<String, String> cache = new ExpiringCache<>(200); // 200ms TTL
            cache.put("key1", "value1");
            System.out.println("  Immediate get: " + cache.get("key1"));
            Thread.sleep(250);
            System.out.println("  After 250ms:   " + cache.get("key1") + " (expired → null)");

            String computed = cache.getOrCompute("key2", k -> "computed-" + k);
            System.out.println("  Computed: " + computed);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 17. HIGH CONTENTION COUNTER — LongAdder vs AtomicLong
    // ════════════════════════════════════════════════════════════════
    static class Ex17_HighContentionCounter {
        static void demo() throws Exception {
            System.out.println("\n=== 17. HIGH CONTENTION COUNTER (LongAdder) ===");
            LongAdder adder = new LongAdder();
            AtomicLong atomic = new AtomicLong(0);
            int threads = 10, ops = 100_000;

            ExecutorService e = Executors.newFixedThreadPool(threads);
            CountDownLatch done1 = new CountDownLatch(threads);
            CountDownLatch done2 = new CountDownLatch(threads);

            // Benchmark AtomicLong
            long t1 = System.nanoTime();
            for (int i = 0; i < threads; i++) {
                e.submit(() -> {
                    for (int j = 0; j < ops; j++) atomic.incrementAndGet();
                    done1.countDown();
                });
            }
            done1.await();
            long atomicTime = System.nanoTime() - t1;

            // Benchmark LongAdder
            long t2 = System.nanoTime();
            for (int i = 0; i < threads; i++) {
                e.submit(() -> {
                    for (int j = 0; j < ops; j++) adder.increment();
                    done2.countDown();
                });
            }
            done2.await();
            long adderTime = System.nanoTime() - t2;
            e.shutdown();

            System.out.println("  AtomicLong: " + atomic.get() + " in " + atomicTime / 1_000_000 + "ms");
            System.out.println("  LongAdder:  " + adder.sum() + " in " + adderTime / 1_000_000 + "ms");
            System.out.println("  LongAdder is ~" + (atomicTime / Math.max(adderTime, 1)) + "x faster");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 18. IDEMPOTENT PAYMENT — ConcurrentHashMap.putIfAbsent()
    //     Prevent duplicate payment processing
    // ════════════════════════════════════════════════════════════════
    static class Ex18_IdempotentPayment {
        private final ConcurrentHashMap<String, String> processed = new ConcurrentHashMap<>();

        String processPayment(String paymentId, double amount) {
            String existing = processed.putIfAbsent(paymentId, "PROCESSING");
            if (existing != null) {
                System.out.println("  [SKIP] " + paymentId + " already processed");
                return "DUPLICATE";
            }
            // Simulate payment processing
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            processed.put(paymentId, "COMPLETED");
            System.out.printf("  [OK] %s: $%.0f processed%n", paymentId, amount);
            return "SUCCESS";
        }

        static void demo() throws Exception {
            System.out.println("\n=== 18. IDEMPOTENT PAYMENT (putIfAbsent) ===");
            Ex18_IdempotentPayment sys = new Ex18_IdempotentPayment();
            ExecutorService e = Executors.newFixedThreadPool(5);
            // Same payment submitted 5 times concurrently
            for (int i = 0; i < 5; i++) {
                e.submit(() -> sys.processPayment("PAY-001", 99.99));
            }
            // Different payment
            e.submit(() -> sys.processPayment("PAY-002", 50.00));
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 19. FILE DOWNLOAD SLOTS — Semaphore (max 3 concurrent)
    // ════════════════════════════════════════════════════════════════
    static class Ex19_DownloadSlots {
        private final Semaphore slots;
        Ex19_DownloadSlots(int max) { slots = new Semaphore(max); }

        void download(String file) throws InterruptedException {
            System.out.println("  " + file + " waiting for slot...");
            slots.acquire();
            try {
                System.out.println("  " + file + " DOWNLOADING (slots=" + slots.availablePermits() + ")");
                Thread.sleep(200);
                System.out.println("  " + file + " DONE");
            } finally { slots.release(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 19. DOWNLOAD SLOTS (Semaphore, max 3) ===");
            Ex19_DownloadSlots mgr = new Ex19_DownloadSlots(3);
            ExecutorService e = Executors.newFixedThreadPool(6);
            for (int i = 1; i <= 6; i++) {
                int id = i;
                e.submit(() -> { try { mgr.download("File" + id); } catch (Exception ex) {} });
            }
            e.shutdown();
            e.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 20. ASYNC WORKFLOW CHAIN — CompletableFuture
    //     Call ServiceA → then B → then C asynchronously
    // ════════════════════════════════════════════════════════════════
    static class Ex20_AsyncWorkflow {
        static CompletableFuture<String> callServiceA() {
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                System.out.println("  [A] Fetched user data");
                return "UserData";
            });
        }

        static CompletableFuture<String> callServiceB(String input) {
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                System.out.println("  [B] Enriched: " + input);
                return input + "+Enriched";
            });
        }

        static CompletableFuture<String> callServiceC(String input) {
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                System.out.println("  [C] Saved: " + input);
                return input + "+Saved";
            });
        }

        static void demo() throws Exception {
            System.out.println("\n=== 20. ASYNC WORKFLOW (CompletableFuture chain) ===");
            long start = System.currentTimeMillis();

            String result = callServiceA()
                    .thenCompose(a -> callServiceB(a))     // A → B
                    .thenCompose(b -> callServiceC(b))     // B → C
                    .exceptionally(ex -> {                  // Error handling
                        System.out.println("  ERROR: " + ex.getMessage());
                        return "FALLBACK";
                    })
                    .get();

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  Final result: " + result);
            System.out.println("  Chain time: " + elapsed + "ms");
        }
    }
}
