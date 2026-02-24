import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * 15 MORE REAL-WORLD CONCURRENCY INTERVIEW EXAMPLES (36-50)
 * Companion to ConcurrencyRealWorldExamples.java (1-20)
 *            and ConcurrencyRealWorldExamples2.java (21-35)
 *
 * Compile: javac ConcurrencyRealWorldExamples3.java
 * Run:     java ConcurrencyRealWorldExamples3
 */
public class ConcurrencyRealWorldExamples3 {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   15 MORE CONCURRENCY EXAMPLES (36-50)                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        Ex36_SharedCache.demo();
        Ex37_Leaderboard.demo();
        Ex38_OrderQueue.demo();
        Ex39_PerUserRateLimit.demo();
        Ex40_LoggingSystem.demo();
        Ex41_DistributedLock.demo();
        Ex42_FileChunkProcessing.demo();
        Ex43_CountdownLaunch.demo();
        Ex44_ShoppingCart.demo();
        Ex45_ResourcePool.demo();
        Ex46_ParallelMapReduce.demo();
        Ex47_PriorityTasks.demo();
        Ex48_EmailDedup.demo();
        Ex49_CounterReset.demo();
        Ex50_TurnTimer.demo();

        System.out.println("\n✅ ALL 15 EXAMPLES (36-50) COMPLETED!");
    }

    // ════════════════════════════════════════════════════════════════
    // 36. SHARED CACHE UPDATE — ReadWriteLock
    //     Many readers, one writer at a time
    // ════════════════════════════════════════════════════════════════
    static class Ex36_SharedCache {
        private final Map<String, String> cache = new HashMap<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        String get(String key) {
            rwLock.readLock().lock();
            try { return cache.getOrDefault(key, "MISS"); }
            finally { rwLock.readLock().unlock(); }
        }

        void put(String key, String val) {
            rwLock.writeLock().lock();
            try {
                cache.put(key, val);
                System.out.println("  [W] Updated " + key + "=" + val);
            } finally { rwLock.writeLock().unlock(); }
        }

        void refresh(Map<String, String> newData) {
            rwLock.writeLock().lock();
            try {
                cache.clear();
                cache.putAll(newData);
                System.out.println("  [W] Cache refreshed with " + newData.size() + " entries");
            } finally { rwLock.writeLock().unlock(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 36. SHARED CACHE (ReadWriteLock) ===");
            Ex36_SharedCache c = new Ex36_SharedCache();
            c.put("config.timeout", "30s");
            c.put("config.retries", "3");

            ExecutorService e = Executors.newFixedThreadPool(6);
            // 5 readers + 1 writer concurrently
            for (int i = 1; i <= 5; i++) {
                int id = i;
                e.submit(() -> System.out.println("  [R" + id + "] timeout=" + c.get("config.timeout")));
            }
            e.submit(() -> c.put("config.timeout", "60s"));
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 37. MULTIPLAYER LEADERBOARD — ConcurrentHashMap + AtomicInteger
    //     Update scores concurrently without losing updates
    // ════════════════════════════════════════════════════════════════
    static class Ex37_Leaderboard {
        private final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();

        void addScore(String player, int points) {
            scores.computeIfAbsent(player, k -> new AtomicInteger(0)).addAndGet(points);
        }

        int getScore(String player) {
            AtomicInteger s = scores.get(player);
            return s == null ? 0 : s.get();
        }

        List<Map.Entry<String, Integer>> topN(int n) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>();
            scores.forEach((k, v) -> list.add(Map.entry(k, v.get())));
            list.sort((a, b) -> b.getValue() - a.getValue());
            return list.subList(0, Math.min(n, list.size()));
        }

        static void demo() throws Exception {
            System.out.println("\n=== 37. LEADERBOARD (ConcurrentHashMap+Atomic) ===");
            Ex37_Leaderboard lb = new Ex37_Leaderboard();
            ExecutorService e = Executors.newFixedThreadPool(10);
            CountDownLatch done = new CountDownLatch(1000);

            String[] players = {"Alice", "Bob", "Charlie", "Diana", "Eve"};
            for (int i = 0; i < 1000; i++) {
                int idx = i;
                e.submit(() -> {
                    lb.addScore(players[idx % 5], ThreadLocalRandom.current().nextInt(1, 10));
                    done.countDown();
                });
            }
            done.await();
            e.shutdown();

            System.out.println("  Top 3: " + lb.topN(3));
            int total = 0;
            for (String p : players) {
                int s = lb.getScore(p);
                System.out.println("  " + p + ": " + s);
                total += s;
            }
            System.out.println("  Total points distributed: " + total + " (1000 adds, no lost updates)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 38. ORDER PROCESSING QUEUE — BlockingQueue
    //     Multiple consumer threads process orders safely
    // ════════════════════════════════════════════════════════════════
    static class Ex38_OrderQueue {
        static class Order {
            final String id;
            final double amount;
            Order(String id, double amt) { this.id = id; this.amount = amt; }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 38. ORDER QUEUE (BlockingQueue) ===");
            BlockingQueue<Order> queue = new LinkedBlockingQueue<>(10);
            AtomicInteger processed = new AtomicInteger(0);

            // Producer: place 8 orders
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 8; i++) {
                        queue.put(new Order("ORD-" + i, i * 10.0));
                        System.out.println("  [P] Placed ORD-" + i);
                    }
                } catch (InterruptedException ex) {}
            });

            // 3 Consumer workers
            Runnable consumer = () -> {
                try {
                    while (true) {
                        Order o = queue.poll(500, TimeUnit.MILLISECONDS);
                        if (o == null) break; // timeout = no more orders
                        Thread.sleep(50);
                        processed.incrementAndGet();
                        System.out.printf("  [C-%s] Processed %s ($%.0f)%n",
                                Thread.currentThread().getName(), o.id, o.amount);
                    }
                } catch (InterruptedException ex) {}
            };

            producer.start();
            Thread c1 = new Thread(consumer, "W1");
            Thread c2 = new Thread(consumer, "W2");
            Thread c3 = new Thread(consumer, "W3");
            c1.start(); c2.start(); c3.start();

            producer.join(); c1.join(); c2.join(); c3.join();
            System.out.println("  Total processed: " + processed.get() + " (should be 8)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 39. PER-USER API RATE LIMITER — Semaphore per user
    //     Each user limited to N concurrent requests independently
    // ════════════════════════════════════════════════════════════════
    static class Ex39_PerUserRateLimit {
        private final ConcurrentHashMap<String, Semaphore> userLimits = new ConcurrentHashMap<>();
        private final int maxPerUser;

        Ex39_PerUserRateLimit(int max) { this.maxPerUser = max; }

        String handleRequest(String user, String endpoint) throws InterruptedException {
            Semaphore sem = userLimits.computeIfAbsent(user, k -> new Semaphore(maxPerUser));
            if (!sem.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                System.out.println("  [429] " + user + " → " + endpoint + " RATE LIMITED");
                return "429 Too Many Requests";
            }
            try {
                Thread.sleep(100); // simulate API work
                System.out.println("  [200] " + user + " → " + endpoint + " OK");
                return "200 OK";
            } finally { sem.release(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 39. PER-USER RATE LIMIT (Semaphore/user, max 2) ===");
            Ex39_PerUserRateLimit limiter = new Ex39_PerUserRateLimit(2);
            ExecutorService e = Executors.newFixedThreadPool(10);

            // Alice sends 4 concurrent requests (only 2 allowed)
            for (int i = 1; i <= 4; i++) {
                int id = i;
                e.submit(() -> {
                    try { limiter.handleRequest("Alice", "/api/data" + id); }
                    catch (Exception ex) {}
                });
            }
            // Bob sends 2 (within limit)
            for (int i = 1; i <= 2; i++) {
                int id = i;
                e.submit(() -> {
                    try { limiter.handleRequest("Bob", "/api/data" + id); }
                    catch (Exception ex) {}
                });
            }
            e.shutdown();
            e.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 40. THREAD-SAFE LOGGING — ConcurrentLinkedQueue
    //     Multiple threads write logs without mangling output
    // ════════════════════════════════════════════════════════════════
    static class Ex40_LoggingSystem {
        static class ThreadSafeLogger {
            private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
            private final int flushThreshold;

            ThreadSafeLogger(int threshold) { this.flushThreshold = threshold; }

            void log(String level, String msg) {
                String entry = String.format("[%s][%s][%s] %s",
                        System.currentTimeMillis() % 10000,
                        Thread.currentThread().getName(), level, msg);
                buffer.offer(entry);

                if (buffer.size() >= flushThreshold) flush();
            }

            void flush() {
                List<String> batch = new ArrayList<>();
                String entry;
                while ((entry = buffer.poll()) != null) batch.add(entry);
                if (!batch.isEmpty()) {
                    System.out.println("  [FLUSH] " + batch.size() + " entries:");
                    batch.forEach(e -> System.out.println("    " + e));
                }
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 40. LOGGING SYSTEM (ConcurrentLinkedQueue) ===");
            ThreadSafeLogger logger = new ThreadSafeLogger(5);
            ExecutorService e = Executors.newFixedThreadPool(4);

            for (int i = 0; i < 8; i++) {
                int id = i;
                e.submit(() -> logger.log(id % 2 == 0 ? "INFO" : "WARN", "Event " + id));
            }
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
            logger.flush(); // flush remaining
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 41. DISTRIBUTED LOCK SIMULATION — ReentrantLock + tryLock()
    //     Only one service processes a resource; others retry
    // ════════════════════════════════════════════════════════════════
    static class Ex41_DistributedLock {
        private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        boolean processWithLock(String resource, String worker) {
            ReentrantLock lock = locks.computeIfAbsent(resource, k -> new ReentrantLock());
            boolean acquired = false;
            try {
                acquired = lock.tryLock(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) { return false; }

            if (!acquired) {
                System.out.println("  " + worker + " → " + resource + " LOCKED by another, skipping");
                return false;
            }
            try {
                System.out.println("  " + worker + " → " + resource + " ACQUIRED, processing...");
                Thread.sleep(200);
                System.out.println("  " + worker + " → " + resource + " DONE, releasing");
                return true;
            } catch (InterruptedException e) { return false; }
            finally { lock.unlock(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 41. DISTRIBUTED LOCK (tryLock) ===");
            Ex41_DistributedLock sys = new Ex41_DistributedLock();
            ExecutorService e = Executors.newFixedThreadPool(4);

            // 3 workers compete for same resource
            for (int i = 1; i <= 3; i++) {
                int id = i;
                e.submit(() -> sys.processWithLock("order-123", "Worker" + id));
            }
            // Different resource — no contention
            e.submit(() -> sys.processWithLock("order-456", "Worker4"));

            e.shutdown();
            e.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 42. FILE CHUNK PROCESSING — ExecutorService + Future
    //     Split file into chunks, process in parallel, combine
    // ════════════════════════════════════════════════════════════════
    static class Ex42_FileChunkProcessing {
        static int processChunk(String chunk) throws InterruptedException {
            Thread.sleep(50);
            int wordCount = chunk.split("\\s+").length;
            System.out.println("  [" + Thread.currentThread().getName() + "] Chunk: "
                    + chunk.substring(0, Math.min(20, chunk.length())) + "... → " + wordCount + " words");
            return wordCount;
        }

        static void demo() throws Exception {
            System.out.println("\n=== 42. FILE CHUNK PROCESSING (ExecutorService+Future) ===");
            // Simulate a file split into chunks
            List<String> chunks = List.of(
                    "The quick brown fox jumps over the lazy dog",
                    "Java concurrency is powerful but tricky to master",
                    "Thread safety requires careful design and testing",
                    "Lock free algorithms use CAS operations for performance"
            );

            ExecutorService pool = Executors.newFixedThreadPool(3);
            List<Future<Integer>> futures = new ArrayList<>();
            for (String chunk : chunks) {
                futures.add(pool.submit(() -> processChunk(chunk)));
            }

            int totalWords = 0;
            for (Future<Integer> f : futures) totalWords += f.get();

            pool.shutdown();
            System.out.println("  Total word count: " + totalWords);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 43. COUNTDOWN BEFORE LAUNCH — CountDownLatch
    //     Wait for all services to initialize before starting
    // ════════════════════════════════════════════════════════════════
    static class Ex43_CountdownLaunch {
        static void demo() throws Exception {
            System.out.println("\n=== 43. COUNTDOWN LAUNCH (CountDownLatch) ===");
            String[] services = {"Database", "Cache", "MessageQueue", "AuthService"};
            CountDownLatch allReady = new CountDownLatch(services.length);

            for (String svc : services) {
                new Thread(() -> {
                    try {
                        int initTime = ThreadLocalRandom.current().nextInt(100, 300);
                        Thread.sleep(initTime);
                        System.out.println("  [" + svc + "] Ready! (" + initTime + "ms)");
                        allReady.countDown();
                    } catch (InterruptedException e) {}
                }).start();
            }

            System.out.println("  [Main] Waiting for all " + services.length + " services...");
            allReady.await(5, TimeUnit.SECONDS);
            System.out.println("  [Main] 🚀 ALL SERVICES READY — LAUNCHING!");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 44. THREAD-SAFE SHOPPING CART — ConcurrentHashMap
    //     Add/remove items from same cart concurrently
    // ════════════════════════════════════════════════════════════════
    static class Ex44_ShoppingCart {
        private final ConcurrentHashMap<String, AtomicInteger> items = new ConcurrentHashMap<>();

        void addItem(String item, int qty) {
            items.computeIfAbsent(item, k -> new AtomicInteger(0)).addAndGet(qty);
            System.out.println("  [+] Added " + qty + "x " + item);
        }

        boolean removeItem(String item, int qty) {
            AtomicInteger current = items.get(item);
            if (current == null) return false;
            while (true) {
                int cur = current.get();
                if (cur < qty) return false;
                if (current.compareAndSet(cur, cur - qty)) {
                    if (cur - qty == 0) items.remove(item);
                    System.out.println("  [-] Removed " + qty + "x " + item);
                    return true;
                }
            }
        }

        Map<String, Integer> getCart() {
            Map<String, Integer> snap = new HashMap<>();
            items.forEach((k, v) -> { if (v.get() > 0) snap.put(k, v.get()); });
            return snap;
        }

        static void demo() throws Exception {
            System.out.println("\n=== 44. SHOPPING CART (ConcurrentHashMap) ===");
            Ex44_ShoppingCart cart = new Ex44_ShoppingCart();
            ExecutorService e = Executors.newFixedThreadPool(4);

            e.submit(() -> cart.addItem("Laptop", 1));
            e.submit(() -> cart.addItem("Mouse", 2));
            e.submit(() -> cart.addItem("Keyboard", 1));
            e.submit(() -> cart.addItem("Mouse", 1)); // another thread adds more
            Thread.sleep(50);
            e.submit(() -> cart.removeItem("Mouse", 1));

            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.println("  Cart: " + cart.getCart());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 45. RESOURCE POOL (DB Connections) — Semaphore
    //     Limit concurrent connections; block excess threads
    // ════════════════════════════════════════════════════════════════
    static class Ex45_ResourcePool {
        private final Semaphore permits;
        private final BlockingQueue<String> pool;

        Ex45_ResourcePool(int size) {
            permits = new Semaphore(size);
            pool = new LinkedBlockingQueue<>();
            for (int i = 1; i <= size; i++) pool.offer("Connection-" + i);
        }

        String acquire(String requester) throws InterruptedException {
            permits.acquire();
            String conn = pool.poll();
            System.out.println("  " + requester + " acquired " + conn
                    + " (avail=" + permits.availablePermits() + ")");
            return conn;
        }

        void release(String requester, String conn) {
            pool.offer(conn);
            permits.release();
            System.out.println("  " + requester + " released " + conn
                    + " (avail=" + permits.availablePermits() + ")");
        }

        static void demo() throws Exception {
            System.out.println("\n=== 45. DB CONNECTION POOL (Semaphore, size 3) ===");
            Ex45_ResourcePool dbPool = new Ex45_ResourcePool(3);
            ExecutorService e = Executors.newFixedThreadPool(6);

            for (int i = 1; i <= 6; i++) {
                int id = i;
                e.submit(() -> {
                    try {
                        String conn = dbPool.acquire("Thread" + id);
                        Thread.sleep(150); // use connection
                        dbPool.release("Thread" + id, conn);
                    } catch (InterruptedException ex) {}
                });
            }
            e.shutdown();
            e.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 46. PARALLEL MAP-REDUCE — ForkJoinPool
    //     Split dataset, process in parallel, combine results
    // ════════════════════════════════════════════════════════════════
    static class Ex46_ParallelMapReduce {
        static class SumTask extends RecursiveTask<Long> {
            private final int[] data;
            private final int lo, hi;
            private static final int THRESHOLD = 100;

            SumTask(int[] data, int lo, int hi) {
                this.data = data; this.lo = lo; this.hi = hi;
            }

            @Override
            protected Long compute() {
                if (hi - lo <= THRESHOLD) {
                    long sum = 0;
                    for (int i = lo; i < hi; i++) sum += data[i];
                    return sum;
                }
                int mid = (lo + hi) / 2;
                SumTask left = new SumTask(data, lo, mid);
                SumTask right = new SumTask(data, mid, hi);
                left.fork();
                long rightResult = right.compute();
                long leftResult = left.join();
                return leftResult + rightResult;
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 46. PARALLEL MAP-REDUCE (ForkJoinPool) ===");
            int[] data = new int[10_000];
            long expected = 0;
            for (int i = 0; i < data.length; i++) {
                data[i] = ThreadLocalRandom.current().nextInt(1, 100);
                expected += data[i];
            }

            ForkJoinPool pool = new ForkJoinPool();
            long result = pool.invoke(new SumTask(data, 0, data.length));
            pool.shutdown();

            System.out.println("  Array size: " + data.length);
            System.out.println("  Sequential sum: " + expected);
            System.out.println("  ForkJoin sum:   " + result);
            System.out.println("  Match: " + (expected == result));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 47. PRIORITY TASK EXECUTION — PriorityBlockingQueue
    //     Higher priority tasks execute before lower priority
    // ════════════════════════════════════════════════════════════════
    static class Ex47_PriorityTasks {
        static class PriorityTask implements Comparable<PriorityTask>, Runnable {
            final String name;
            final int priority; // lower = higher priority

            PriorityTask(String name, int priority) {
                this.name = name; this.priority = priority;
            }

            @Override
            public int compareTo(PriorityTask o) { return Integer.compare(this.priority, o.priority); }

            @Override
            public void run() {
                System.out.println("  [P" + priority + "] Executing: " + name
                        + " on " + Thread.currentThread().getName());
                try { Thread.sleep(50); } catch (InterruptedException e) {}
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 47. PRIORITY TASKS (PriorityBlockingQueue) ===");
            PriorityBlockingQueue<PriorityTask> queue = new PriorityBlockingQueue<>();

            // Add tasks in random priority order
            queue.put(new PriorityTask("Low-cleanup", 5));
            queue.put(new PriorityTask("HIGH-payment", 1));
            queue.put(new PriorityTask("Med-notification", 3));
            queue.put(new PriorityTask("HIGH-fraud-check", 1));
            queue.put(new PriorityTask("Low-analytics", 5));
            queue.put(new PriorityTask("Med-email", 3));

            // Single-threaded consumer to show priority order
            System.out.println("  Execution order (lower P = higher priority):");
            while (!queue.isEmpty()) {
                PriorityTask task = queue.take();
                task.run();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 48. EMAIL DEDUPLICATION — ConcurrentHashMap.putIfAbsent()
    //     Ensure same email ID is not sent twice
    // ════════════════════════════════════════════════════════════════
    static class Ex48_EmailDedup {
        private final ConcurrentHashMap<String, Boolean> sentEmails = new ConcurrentHashMap<>();
        private final AtomicInteger sentCount = new AtomicInteger(0);

        boolean sendEmail(String emailId, String to) {
            if (sentEmails.putIfAbsent(emailId, Boolean.TRUE) != null) {
                System.out.println("  [DUP] " + emailId + " already sent → skipped");
                return false;
            }
            try { Thread.sleep(30); } catch (InterruptedException e) {}
            sentCount.incrementAndGet();
            System.out.println("  [SENT] " + emailId + " → " + to);
            return true;
        }

        static void demo() throws Exception {
            System.out.println("\n=== 48. EMAIL DEDUP (putIfAbsent) ===");
            Ex48_EmailDedup sys = new Ex48_EmailDedup();
            ExecutorService e = Executors.newFixedThreadPool(6);

            // Same email triggered by multiple events
            String[] emailIds = {"E-001", "E-002", "E-001", "E-003", "E-002", "E-001"};
            for (String id : emailIds) {
                e.submit(() -> sys.sendEmail(id, "user@example.com"));
            }
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.println("  Total sent: " + sys.sentCount.get() + " (should be 3 unique)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 49. COUNTER WITH RESET — AtomicInteger + synchronized
    //     Increment and periodically reset safely
    // ════════════════════════════════════════════════════════════════
    static class Ex49_CounterReset {
        private final AtomicInteger counter = new AtomicInteger(0);

        void increment() { counter.incrementAndGet(); }

        // Reset returns the old value atomically
        int resetAndGet() {
            return counter.getAndSet(0);
        }

        // Safer compound: increment-if-below-threshold
        boolean incrementIfBelow(int threshold) {
            while (true) {
                int cur = counter.get();
                if (cur >= threshold) return false;
                if (counter.compareAndSet(cur, cur + 1)) return true;
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 49. COUNTER RESET (AtomicInteger) ===");
            Ex49_CounterReset sys = new Ex49_CounterReset();
            ExecutorService e = Executors.newFixedThreadPool(5);
            CountDownLatch done = new CountDownLatch(100);

            // 100 increments
            for (int i = 0; i < 100; i++) {
                e.submit(() -> { sys.increment(); done.countDown(); });
            }
            done.await();

            System.out.println("  Counter before reset: " + sys.counter.get());
            int old = sys.resetAndGet();
            System.out.println("  Reset returned: " + old + " | Counter now: " + sys.counter.get());

            // Test increment-if-below
            for (int i = 0; i < 10; i++) sys.incrementIfBelow(5);
            System.out.println("  After 10 incrementIfBelow(5): " + sys.counter.get() + " (capped at 5)");

            e.shutdown();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 50. MULTIPLAYER TURN TIMER — ScheduledExecutor + CountDownLatch
    //     Each player has N seconds; auto-skip if time expires
    // ════════════════════════════════════════════════════════════════
    static class Ex50_TurnTimer {
        static void demo() throws Exception {
            System.out.println("\n=== 50. TURN TIMER (Scheduled+Latch) ===");
            String[] players = {"Alice", "Bob", "Charlie"};
            int turnTimeMs = 300; // 300ms per turn for demo

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

            for (String player : players) {
                CountDownLatch turnDone = new CountDownLatch(1);
                AtomicBoolean timedOut = new AtomicBoolean(false);

                // Auto-skip timer
                ScheduledFuture<?> timer = scheduler.schedule(() -> {
                    if (turnDone.getCount() > 0) {
                        timedOut.set(true);
                        turnDone.countDown();
                        System.out.println("  ⏰ " + player + " TIMED OUT! Auto-skipping.");
                    }
                }, turnTimeMs, TimeUnit.MILLISECONDS);

                // Simulate player action (some fast, some slow)
                new Thread(() -> {
                    try {
                        int thinkTime = ThreadLocalRandom.current().nextInt(100, 500);
                        Thread.sleep(thinkTime);
                        if (turnDone.getCount() > 0) {
                            turnDone.countDown();
                            timer.cancel(false);
                            System.out.println("  ✓ " + player + " played in " + thinkTime + "ms");
                        }
                    } catch (InterruptedException e) {}
                }).start();

                turnDone.await(); // wait for player action OR timeout
            }

            scheduler.shutdown();
            System.out.println("  Game complete!");
        }
    }
}
