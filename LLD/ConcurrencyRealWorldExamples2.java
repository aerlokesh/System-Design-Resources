import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * 15 MORE REAL-WORLD CONCURRENCY INTERVIEW EXAMPLES (21-35)
 * Companion to ConcurrencyRealWorldExamples.java (1-20)
 *
 * Compile: javac ConcurrencyRealWorldExamples2.java
 * Run:     java ConcurrencyRealWorldExamples2
 */
public class ConcurrencyRealWorldExamples2 {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   15 MORE CONCURRENCY EXAMPLES (21-35)                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        Ex21_TicketWaitlist.demo();
        Ex22_RestaurantTables.demo();
        Ex23_ConcurrentDownloader.demo();
        Ex24_InventoryFlashSale.demo();
        Ex25_ImagePipeline.demo();
        Ex26_PrintCyclic.demo();
        Ex27_AuctionSystem.demo();
        Ex28_GameTurnSync.demo();
        Ex29_PaymentDedup.demo();
        Ex30_SessionCleanup.demo();
        Ex31_MetricsCounter.demo();
        Ex32_WalletDeduction.demo();
        Ex33_LazySingleton.demo();
        Ex34_AsyncChain.demo();
        Ex35_SessionLimit.demo();

        System.out.println("\n✅ ALL 15 EXAMPLES (21-35) COMPLETED!");
    }

    // ════════════════════════════════════════════════════════════════
    // 21. TICKET BOOKING WITH WAITLIST
    //     ReentrantLock + Condition
    //     Users wait until seats are released (cancellations)
    // ════════════════════════════════════════════════════════════════
    static class Ex21_TicketWaitlist {
        private int availableTickets;
        private final Lock lock = new ReentrantLock();
        private final Condition ticketAvailable = lock.newCondition();

        Ex21_TicketWaitlist(int tickets) { this.availableTickets = tickets; }

        void book(String user) throws InterruptedException {
            lock.lock();
            try {
                while (availableTickets <= 0) {
                    System.out.println("  " + user + " → WAITLISTED (no tickets)");
                    ticketAvailable.await(); // wait for cancellation
                }
                availableTickets--;
                System.out.println("  " + user + " → BOOKED! Remaining: " + availableTickets);
            } finally { lock.unlock(); }
        }

        void cancel(String user) {
            lock.lock();
            try {
                availableTickets++;
                System.out.println("  " + user + " → CANCELLED. Remaining: " + availableTickets);
                ticketAvailable.signal(); // wake one waitlisted user
            } finally { lock.unlock(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 21. TICKET BOOKING WITH WAITLIST (Lock+Condition) ===");
            Ex21_TicketWaitlist sys = new Ex21_TicketWaitlist(1); // only 1 ticket

            Thread t1 = new Thread(() -> {
                try { sys.book("Alice"); } catch (InterruptedException e) {}
            });
            Thread t2 = new Thread(() -> {
                try { sys.book("Bob"); } catch (InterruptedException e) {}
            });

            t1.start();
            Thread.sleep(50);
            t2.start(); // Bob will be waitlisted
            Thread.sleep(100);
            sys.cancel("Alice"); // Alice cancels → Bob gets the ticket
            t1.join(); t2.join();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 22. RESTAURANT TABLE ALLOCATION
    //     Semaphore — 10 tables, guests wait if all occupied
    // ════════════════════════════════════════════════════════════════
    static class Ex22_RestaurantTables {
        private final Semaphore tables;

        Ex22_RestaurantTables(int numTables) { tables = new Semaphore(numTables); }

        void dine(String guest) throws InterruptedException {
            System.out.println("  " + guest + " waiting for table... (avail=" + tables.availablePermits() + ")");
            tables.acquire();
            try {
                System.out.println("  " + guest + " SEATED (avail=" + tables.availablePermits() + ")");
                Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300)); // eating
                System.out.println("  " + guest + " finished dining");
            } finally { tables.release(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 22. RESTAURANT TABLES (Semaphore, 3 tables) ===");
            Ex22_RestaurantTables restaurant = new Ex22_RestaurantTables(3);
            ExecutorService e = Executors.newFixedThreadPool(6);
            for (int i = 1; i <= 6; i++) {
                int id = i;
                e.submit(() -> {
                    try { restaurant.dine("Guest" + id); } catch (Exception ex) {}
                });
            }
            e.shutdown();
            e.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 23. CONCURRENT FILE DOWNLOADER
    //     ExecutorService — download multiple files in parallel
    // ════════════════════════════════════════════════════════════════
    static class Ex23_ConcurrentDownloader {
        static String downloadFile(String url) throws InterruptedException {
            int size = ThreadLocalRandom.current().nextInt(100, 500);
            Thread.sleep(size); // simulate download time proportional to size
            return url + " (" + size + "KB)";
        }

        static void demo() throws Exception {
            System.out.println("\n=== 23. CONCURRENT FILE DOWNLOADER (ExecutorService) ===");
            ExecutorService pool = Executors.newFixedThreadPool(3); // max 3 parallel downloads
            List<String> urls = List.of(
                    "file1.zip", "file2.zip", "file3.zip",
                    "file4.zip", "file5.zip"
            );

            long start = System.currentTimeMillis();
            List<Future<String>> futures = new ArrayList<>();
            for (String url : urls) {
                futures.add(pool.submit(() -> {
                    System.out.println("  [" + Thread.currentThread().getName() + "] Downloading " + url);
                    return downloadFile(url);
                }));
            }

            for (Future<String> f : futures) {
                System.out.println("  Downloaded: " + f.get());
            }
            pool.shutdown();
            System.out.println("  Total time: " + (System.currentTimeMillis() - start) + "ms (parallel!)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 24. INVENTORY DEDUCTION IN FLASH SALE
    //     AtomicInteger — reduce stock atomically, track per-user orders
    // ════════════════════════════════════════════════════════════════
    static class Ex24_InventoryFlashSale {
        private final ConcurrentHashMap<String, AtomicInteger> inventory = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, List<String>> orders = new ConcurrentHashMap<>();

        Ex24_InventoryFlashSale() {
            inventory.put("iPhone", new AtomicInteger(5));
            inventory.put("MacBook", new AtomicInteger(3));
        }

        boolean purchase(String user, String product) {
            AtomicInteger stock = inventory.get(product);
            if (stock == null) return false;

            while (true) {
                int cur = stock.get();
                if (cur <= 0) return false;
                if (stock.compareAndSet(cur, cur - 1)) {
                    orders.computeIfAbsent(user, k -> new CopyOnWriteArrayList<>()).add(product);
                    return true;
                }
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 24. INVENTORY FLASH SALE (AtomicInteger) ===");
            Ex24_InventoryFlashSale shop = new Ex24_InventoryFlashSale();
            ExecutorService e = Executors.newFixedThreadPool(20);
            CountDownLatch done = new CountDownLatch(20);

            for (int i = 0; i < 20; i++) {
                int id = i;
                e.submit(() -> {
                    String product = id % 2 == 0 ? "iPhone" : "MacBook";
                    boolean ok = shop.purchase("User" + id, product);
                    System.out.println("  User" + id + " → " + product + ": " + (ok ? "✓" : "✗ SOLD OUT"));
                    done.countDown();
                });
            }
            done.await();
            e.shutdown();
            System.out.println("  Remaining: iPhone=" + shop.inventory.get("iPhone").get()
                    + ", MacBook=" + shop.inventory.get("MacBook").get());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 25. MULTI-STAGE PIPELINE (Image Processing)
    //     BlockingQueue chain: Load → Process → Save
    // ════════════════════════════════════════════════════════════════
    static class Ex25_ImagePipeline {
        static final String POISON = "STOP";

        static void demo() throws Exception {
            System.out.println("\n=== 25. IMAGE PIPELINE (BlockingQueue chain) ===");
            BlockingQueue<String> loadedQueue = new LinkedBlockingQueue<>(5);
            BlockingQueue<String> processedQueue = new LinkedBlockingQueue<>(5);

            // Stage 1: Loader
            Thread loader = new Thread(() -> {
                try {
                    for (int i = 1; i <= 4; i++) {
                        String img = "img" + i + ".jpg";
                        Thread.sleep(50);
                        loadedQueue.put(img);
                        System.out.println("  [Load] " + img);
                    }
                    loadedQueue.put(POISON);
                } catch (InterruptedException ex) {}
            });

            // Stage 2: Processor
            Thread processor = new Thread(() -> {
                try {
                    while (true) {
                        String img = loadedQueue.take();
                        if (POISON.equals(img)) { processedQueue.put(POISON); break; }
                        Thread.sleep(80); // simulate processing
                        String processed = img.replace(".jpg", "_processed.jpg");
                        processedQueue.put(processed);
                        System.out.println("  [Process] " + img + " → " + processed);
                    }
                } catch (InterruptedException ex) {}
            });

            // Stage 3: Saver
            Thread saver = new Thread(() -> {
                try {
                    while (true) {
                        String img = processedQueue.take();
                        if (POISON.equals(img)) break;
                        Thread.sleep(30); // simulate saving
                        System.out.println("  [Save] " + img + " → disk ✓");
                    }
                } catch (InterruptedException ex) {}
            });

            loader.start(); processor.start(); saver.start();
            loader.join(); processor.join(); saver.join();
            System.out.println("  Pipeline complete!");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 26. PRINT NUMBERS CYCLICALLY (1,2,3,1,2,3,...)
    //     3 threads take turns printing their number repeatedly
    //     Concept: CyclicBarrier + synchronized
    // ════════════════════════════════════════════════════════════════
    static class Ex26_PrintCyclic {
        private int turn = 0;
        private final int numThreads = 3;
        private final int rounds = 3;

        synchronized void printNumber(int threadId) throws InterruptedException {
            for (int r = 0; r < rounds; r++) {
                while (turn % numThreads != threadId) wait();
                System.out.print((threadId + 1) + " ");
                turn++;
                notifyAll();
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 26. PRINT CYCLIC 1,2,3 (wait/notify) ===");
            Ex26_PrintCyclic sys = new Ex26_PrintCyclic();
            System.out.print("  Output: ");

            Thread[] threads = new Thread[3];
            for (int i = 0; i < 3; i++) {
                int id = i;
                threads[i] = new Thread(() -> {
                    try { sys.printNumber(id); } catch (InterruptedException e) {}
                });
                threads[i].start();
            }
            for (Thread t : threads) t.join();
            System.out.println(" (3 rounds of 1,2,3)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 27. ONLINE AUCTION SYSTEM
    //     ReadWriteLock — many view bid (read), one places bid (write)
    // ════════════════════════════════════════════════════════════════
    static class Ex27_AuctionSystem {
        private String highestBidder = "none";
        private double highestBid = 0;
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        double viewBid(String viewer) {
            rwLock.readLock().lock();
            try {
                System.out.printf("  [View] %s sees $%.0f by %s%n", viewer, highestBid, highestBidder);
                return highestBid;
            } finally { rwLock.readLock().unlock(); }
        }

        boolean placeBid(String bidder, double amount) {
            rwLock.writeLock().lock();
            try {
                if (amount > highestBid) {
                    highestBid = amount;
                    highestBidder = bidder;
                    System.out.printf("  [Bid!] %s bids $%.0f → NEW HIGHEST%n", bidder, amount);
                    return true;
                }
                System.out.printf("  [Bid] %s bids $%.0f → too low (current $%.0f)%n",
                        bidder, amount, highestBid);
                return false;
            } finally { rwLock.writeLock().unlock(); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 27. ONLINE AUCTION (ReadWriteLock) ===");
            Ex27_AuctionSystem auction = new Ex27_AuctionSystem();
            ExecutorService e = Executors.newFixedThreadPool(8);

            // Bidders
            e.submit(() -> auction.placeBid("Alice", 100));
            e.submit(() -> auction.placeBid("Bob", 150));
            e.submit(() -> auction.placeBid("Charlie", 120)); // too low

            // Viewers (can read concurrently)
            for (int i = 1; i <= 4; i++) {
                int id = i;
                e.submit(() -> auction.viewBid("Viewer" + id));
            }

            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.printf("  Winner: %s at $%.0f%n", auction.highestBidder, auction.highestBid);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 28. MULTIPLAYER GAME TURN SYNC
    //     CyclicBarrier — all players complete turn before next round
    // ════════════════════════════════════════════════════════════════
    static class Ex28_GameTurnSync {
        static void demo() throws Exception {
            System.out.println("\n=== 28. GAME TURN SYNC (CyclicBarrier) ===");
            int numPlayers = 4;
            int numRounds = 3;
            AtomicInteger roundNum = new AtomicInteger(0);

            CyclicBarrier barrier = new CyclicBarrier(numPlayers, () -> {
                int r = roundNum.incrementAndGet();
                System.out.println("  ══ Round " + r + " complete! All players synced ══");
            });

            ExecutorService e = Executors.newFixedThreadPool(numPlayers);
            for (int p = 1; p <= numPlayers; p++) {
                int pid = p;
                e.submit(() -> {
                    try {
                        for (int r = 1; r <= numRounds; r++) {
                            // Simulate variable turn time
                            Thread.sleep(ThreadLocalRandom.current().nextInt(30, 150));
                            System.out.println("  Player" + pid + " done turn " + r);
                            barrier.await(); // wait for all players
                        }
                    } catch (Exception ex) {}
                });
            }
            e.shutdown();
            e.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 29. PAYMENT DEDUPLICATION
    //     ConcurrentHashMap.putIfAbsent() — process each payment once
    // ════════════════════════════════════════════════════════════════
    static class Ex29_PaymentDedup {
        private final ConcurrentHashMap<String, String> processed = new ConcurrentHashMap<>();
        private final AtomicInteger processCount = new AtomicInteger(0);

        String process(String paymentId, double amount) {
            // putIfAbsent is atomic — only first call returns null
            String prev = processed.putIfAbsent(paymentId, "IN_PROGRESS");
            if (prev != null) {
                System.out.printf("  [DUP] %s already handled → skipped%n", paymentId);
                return "DUPLICATE";
            }
            // Only one thread reaches here per paymentId
            try { Thread.sleep(50); } catch (InterruptedException ex) {}
            processed.put(paymentId, "DONE");
            int count = processCount.incrementAndGet();
            System.out.printf("  [OK] %s: $%.2f processed (#%d)%n", paymentId, amount, count);
            return "SUCCESS";
        }

        static void demo() throws Exception {
            System.out.println("\n=== 29. PAYMENT DEDUP (putIfAbsent) ===");
            Ex29_PaymentDedup sys = new Ex29_PaymentDedup();
            ExecutorService e = Executors.newFixedThreadPool(10);

            // Same 3 payments, each submitted 3 times
            String[] payments = {"PAY-A", "PAY-B", "PAY-C"};
            for (int i = 0; i < 9; i++) {
                String pid = payments[i % 3];
                double amt = (i % 3 + 1) * 25.50;
                e.submit(() -> sys.process(pid, amt));
            }
            e.shutdown();
            e.awaitTermination(3, TimeUnit.SECONDS);
            System.out.println("  Total unique processed: " + sys.processCount.get() + " (should be 3)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 30. SCHEDULED SESSION CLEANUP
    //     ScheduledExecutorService — remove expired sessions every N ms
    // ════════════════════════════════════════════════════════════════
    static class Ex30_SessionCleanup {
        private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();
        private final long ttlMs;

        Ex30_SessionCleanup(long ttlMs) { this.ttlMs = ttlMs; }

        void addSession(String id) {
            sessions.put(id, System.currentTimeMillis() + ttlMs);
            System.out.println("  [+] Session " + id + " created");
        }

        int cleanup() {
            long now = System.currentTimeMillis();
            int removed = 0;
            var it = sessions.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.getValue() < now) {
                    it.remove();
                    removed++;
                    System.out.println("  [×] Session " + entry.getKey() + " expired → removed");
                }
            }
            return removed;
        }

        static void demo() throws Exception {
            System.out.println("\n=== 30. SESSION CLEANUP (ScheduledExecutor) ===");
            Ex30_SessionCleanup mgr = new Ex30_SessionCleanup(150); // 150ms TTL
            mgr.addSession("S1");
            mgr.addSession("S2");

            ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
            AtomicInteger totalCleaned = new AtomicInteger(0);

            sched.scheduleAtFixedRate(() -> {
                int cleaned = mgr.cleanup();
                totalCleaned.addAndGet(cleaned);
            }, 100, 100, TimeUnit.MILLISECONDS);

            Thread.sleep(100);
            mgr.addSession("S3"); // added after S1,S2 created
            Thread.sleep(200);

            sched.shutdown();
            sched.awaitTermination(1, TimeUnit.SECONDS);
            System.out.println("  Active sessions: " + mgr.sessions.size()
                    + " | Total cleaned: " + totalCleaned.get());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 31. HIGH-FREQUENCY METRICS COUNTER
    //     LongAdder — millions of increments per second
    // ════════════════════════════════════════════════════════════════
    static class Ex31_MetricsCounter {
        static class MetricsRegistry {
            private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

            void increment(String metric) {
                counters.computeIfAbsent(metric, k -> new LongAdder()).increment();
            }

            long get(String metric) {
                LongAdder adder = counters.get(metric);
                return adder == null ? 0 : adder.sum();
            }

            Map<String, Long> snapshot() {
                Map<String, Long> snap = new HashMap<>();
                counters.forEach((k, v) -> snap.put(k, v.sum()));
                return snap;
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 31. HIGH-FREQ METRICS (LongAdder) ===");
            MetricsRegistry metrics = new MetricsRegistry();
            int threads = 8, opsPerThread = 100_000;
            ExecutorService e = Executors.newFixedThreadPool(threads);
            CountDownLatch done = new CountDownLatch(threads);

            long start = System.nanoTime();
            for (int i = 0; i < threads; i++) {
                int id = i;
                e.submit(() -> {
                    for (int j = 0; j < opsPerThread; j++) {
                        metrics.increment("http.requests");
                        if (j % 10 == 0) metrics.increment("http.errors");
                    }
                    done.countDown();
                });
            }
            done.await();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            e.shutdown();

            System.out.println("  Snapshot: " + metrics.snapshot());
            System.out.println("  " + (threads * opsPerThread) + " increments in " + elapsed + "ms");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 32. WALLET DEDUCTION SYSTEM
    //     synchronized — deduct concurrently, never go negative
    // ════════════════════════════════════════════════════════════════
    static class Ex32_WalletDeduction {
        private double balance;

        Ex32_WalletDeduction(double b) { balance = b; }

        synchronized boolean deduct(String user, double amt) {
            if (balance >= amt) {
                balance -= amt;
                System.out.printf("  %s deducted $%.0f → balance $%.0f%n", user, amt, balance);
                return true;
            }
            System.out.printf("  %s FAILED $%.0f → balance $%.0f (insufficient)%n", user, amt, balance);
            return false;
        }

        synchronized double getBalance() { return balance; }

        static void demo() throws Exception {
            System.out.println("\n=== 32. WALLET DEDUCTION (synchronized) ===");
            Ex32_WalletDeduction wallet = new Ex32_WalletDeduction(300);
            ExecutorService e = Executors.newFixedThreadPool(6);
            for (int i = 1; i <= 6; i++) {
                int id = i;
                e.submit(() -> wallet.deduct("User" + id, 80));
            }
            e.shutdown();
            e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.printf("  Final balance: $%.0f (>= 0 ✓)%n", wallet.getBalance());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 33. LAZY SINGLETON
    //     volatile + double-checked locking vs Holder pattern
    // ════════════════════════════════════════════════════════════════
    static class Ex33_LazySingleton {
        // Approach 1: DCL
        static class ConfigManager {
            private static volatile ConfigManager instance;
            private final Map<String, String> config = new HashMap<>();

            private ConfigManager() {
                config.put("db.url", "jdbc:mysql://prod:3306/app");
                config.put("cache.ttl", "300");
                System.out.println("  [ConfigManager] Heavy init done!");
            }

            static ConfigManager getInstance() {
                if (instance == null) {
                    synchronized (ConfigManager.class) {
                        if (instance == null) {
                            instance = new ConfigManager();
                        }
                    }
                }
                return instance;
            }

            String get(String key) { return config.get(key); }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 33. LAZY SINGLETON (DCL + volatile) ===");
            ExecutorService e = Executors.newFixedThreadPool(5);
            Set<Integer> hashCodes = ConcurrentHashMap.newKeySet();
            CountDownLatch done = new CountDownLatch(5);

            for (int i = 0; i < 5; i++) {
                e.submit(() -> {
                    ConfigManager cm = ConfigManager.getInstance();
                    hashCodes.add(System.identityHashCode(cm));
                    System.out.println("  [" + Thread.currentThread().getName()
                            + "] db.url=" + cm.get("db.url"));
                    done.countDown();
                });
            }
            done.await();
            e.shutdown();
            System.out.println("  Unique instances: " + hashCodes.size() + " (should be 1)");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 34. ASYNC WORKFLOW CHAIN (with error handling & timeout)
    //     CompletableFuture — A → B → C with fallback
    // ════════════════════════════════════════════════════════════════
    static class Ex34_AsyncChain {
        static CompletableFuture<String> fetchUser(int userId) {
            return CompletableFuture.supplyAsync(() -> {
                sleep(100);
                System.out.println("  [1] Fetched user " + userId);
                return "User:" + userId;
            });
        }

        static CompletableFuture<String> fetchOrders(String user) {
            return CompletableFuture.supplyAsync(() -> {
                sleep(100);
                System.out.println("  [2] Fetched orders for " + user);
                return user + "+Orders[3]";
            });
        }

        static CompletableFuture<String> sendNotification(String data) {
            return CompletableFuture.supplyAsync(() -> {
                sleep(80);
                System.out.println("  [3] Notification sent: " + data);
                return "NOTIFIED:" + data;
            });
        }

        static void sleep(int ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) {}
        }

        static void demo() throws Exception {
            System.out.println("\n=== 34. ASYNC CHAIN (CompletableFuture) ===");
            long start = System.currentTimeMillis();

            // Chain: fetchUser → fetchOrders → sendNotification
            String result = fetchUser(42)
                    .thenCompose(user -> fetchOrders(user))
                    .thenCompose(orders -> sendNotification(orders))
                    .exceptionally(ex -> {
                        System.out.println("  [ERROR] " + ex.getMessage());
                        return "FALLBACK_NOTIFICATION";
                    })
                    .orTimeout(5, TimeUnit.SECONDS) // timeout safety
                    .get();

            System.out.println("  Result: " + result);
            System.out.println("  Time: " + (System.currentTimeMillis() - start) + "ms");

            // Parallel fan-out example
            System.out.println("  --- Parallel fan-out ---");
            CompletableFuture<String> f1 = fetchUser(1);
            CompletableFuture<String> f2 = fetchUser(2);
            CompletableFuture<String> f3 = fetchUser(3);

            String combined = CompletableFuture.allOf(f1, f2, f3)
                    .thenApply(v -> f1.join() + " | " + f2.join() + " | " + f3.join())
                    .get();
            System.out.println("  Combined: " + combined);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 35. SESSION LIMIT — Semaphore (max N active sessions per user)
    //     Allow only N active sessions; extras wait or get rejected
    // ════════════════════════════════════════════════════════════════
    static class Ex35_SessionLimit {
        private final ConcurrentHashMap<String, Semaphore> userSessions = new ConcurrentHashMap<>();
        private final int maxSessions;

        Ex35_SessionLimit(int max) { this.maxSessions = max; }

        boolean startSession(String user, String device) {
            Semaphore sem = userSessions.computeIfAbsent(user, k -> new Semaphore(maxSessions));
            if (sem.tryAcquire()) {
                System.out.println("  " + user + "@" + device + " → SESSION STARTED (avail="
                        + sem.availablePermits() + ")");
                return true;
            }
            System.out.println("  " + user + "@" + device + " → REJECTED (max " + maxSessions + " sessions)");
            return false;
        }

        void endSession(String user, String device) {
            Semaphore sem = userSessions.get(user);
            if (sem != null) {
                sem.release();
                System.out.println("  " + user + "@" + device + " → SESSION ENDED (avail="
                        + sem.availablePermits() + ")");
            }
        }

        static void demo() throws Exception {
            System.out.println("\n=== 35. SESSION LIMIT (Semaphore, max 2) ===");
            Ex35_SessionLimit sys = new Ex35_SessionLimit(2);

            // Alice logs in from 3 devices — 3rd should be rejected
            sys.startSession("Alice", "Phone");
            sys.startSession("Alice", "Laptop");
            sys.startSession("Alice", "Tablet"); // rejected!

            // Alice logs out from Phone → Tablet can now connect
            sys.endSession("Alice", "Phone");
            sys.startSession("Alice", "Tablet"); // now succeeds

            // Bob is independent
            sys.startSession("Bob", "Phone");
            sys.startSession("Bob", "Laptop");
        }
    }
}
