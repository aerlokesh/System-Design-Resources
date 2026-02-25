import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 25 REAL-WORLD REDIS INTERVIEW EXAMPLES IN JAVA
 * 
 * Each example simulates Redis behavior using Java data structures so it
 * compiles and runs WITHOUT a Redis server — just like the Concurrency examples.
 * 
 * In a real interview, you'd use Jedis/Lettuce. Here we focus on the PATTERNS.
 * Each example shows:
 *   - The real Redis commands + time complexity
 *   - The Java implementation of the pattern
 *   - Concurrency handling (thread safety)
 *
 * Compile: javac RedisRealWorldExamples.java
 * Run:     java RedisRealWorldExamples
 */
public class RedisRealWorldExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   25 REAL-WORLD REDIS INTERVIEW EXAMPLES (Java)               ║");
        System.out.println("║   Simulated Redis — No Server Required                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        Ex01_RateLimiter.demo();
        Ex02_SessionStore.demo();
        Ex03_OTPVerification.demo();
        Ex04_IdempotentPayment.demo();
        Ex05_PageViewCounter.demo();
        Ex06_FlashSaleStock.demo();
        Ex07_CacheWithTTL.demo();
        Ex08_DistributedLock.demo();
        Ex09_UserProfile.demo();
        Ex10_ShoppingCart.demo();
        Ex11_FeatureFlags.demo();
        Ex12_MessageQueue.demo();
        Ex13_RecentActivity.demo();
        Ex14_BlockingWorkerQueue.demo();
        Ex15_RecentlyViewedProducts.demo();
        Ex16_UniqueVisitors.demo();
        Ex17_OnlineUsers.demo();
        Ex18_MutualFriends.demo();
        Ex19_Leaderboard.demo();
        Ex20_DelayedJobScheduler.demo();
        Ex21_RandomWinner.demo();
        Ex22_SubscriptionCheck.demo();
        Ex23_UniqueCountHyperLogLog.demo();
        Ex24_SlidingWindowRateLimiter.demo();
        Ex25_AtomicTransfer.demo();

        System.out.println("\n✅ ALL 25 EXAMPLES COMPLETED!");
    }

    // ════════════════════════════════════════════════════════════════
    // 1. API RATE LIMITER — INCR + EXPIRE (Lua for atomicity)
    //    Redis: INCR rate:user:123:window → if 1, EXPIRE 60 → O(1)
    //    Real: Instagram, Stripe, any API gateway
    // ════════════════════════════════════════════════════════════════
    static class Ex01_RateLimiter {
        private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        private final int limit;
        private final int windowSeconds;

        Ex01_RateLimiter(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }

        // Simulates: Lua { INCR key; if result==1 then EXPIRE key window; if result>limit return 0; return 1 }
        boolean isAllowed(String userId) {
            String key = "rate:" + userId + ":" + (System.currentTimeMillis() / 1000 / windowSeconds);
            AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
            int count = counter.incrementAndGet();
            return count <= limit;
        }

        static void demo() {
            System.out.println("=== 1. API RATE LIMITER (INCR + EXPIRE) ===");
            System.out.println("    Redis: INCR rate:user:123:window → O(1)");
            System.out.println("    Lua: atomic INCR + EXPIRE (no race between the two)");

            Ex01_RateLimiter limiter = new Ex01_RateLimiter(10, 60);
            int allowed = 0, rejected = 0;
            for (int i = 0; i < 15; i++) {
                if (limiter.isAllowed("user:123")) allowed++;
                else rejected++;
            }
            System.out.printf("    Limit=10 | Requests=15 | Allowed=%d | Rejected=%d%n%n", allowed, rejected);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 2. SESSION TOKEN STORE — SETEX (set + expire atomically)
    //    Redis: SETEX session:token 3600 '{json}' → O(1)
    //    Real: Netflix, Spotify, any auth system
    // ════════════════════════════════════════════════════════════════
    static class Ex02_SessionStore {
        private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> expiry = new ConcurrentHashMap<>();

        // SETEX key seconds value → O(1)
        void setex(String key, int seconds, String value) {
            store.put(key, value);
            expiry.put(key, System.currentTimeMillis() + seconds * 1000L);
        }

        // GET key → O(1)
        String get(String key) {
            Long exp = expiry.get(key);
            if (exp != null && System.currentTimeMillis() > exp) {
                store.remove(key);
                expiry.remove(key);
                return null;
            }
            return store.get(key);
        }

        // DEL key → O(1)
        void del(String key) { store.remove(key); expiry.remove(key); }

        static void demo() {
            System.out.println("=== 2. SESSION TOKEN STORE (SETEX) ===");
            System.out.println("    Redis: SETEX session:token 3600 '{json}' → O(1)");
            System.out.println("    TTL auto-expires sessions. No cleanup needed.");

            Ex02_SessionStore sessions = new Ex02_SessionStore();
            sessions.setex("session:abc123", 3600, "{\"user_id\":123,\"role\":\"premium\"}");
            System.out.println("    GET session: " + sessions.get("session:abc123"));
            sessions.del("session:abc123");
            System.out.println("    After logout: " + sessions.get("session:abc123") + " (null)\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 3. OTP VERIFICATION — SETEX + Lua (atomic verify + attempts)
    //    Redis: SETEX otp:phone 300 '847293' → O(1)
    //    Lua: atomic INCR attempts + GET code + compare
    //    Real: WhatsApp, Uber, any 2FA
    // ════════════════════════════════════════════════════════════════
    static class Ex03_OTPVerification {
        private final Map<String, String> codes = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final int maxAttempts;

        Ex03_OTPVerification(int maxAttempts) { this.maxAttempts = maxAttempts; }

        void generateOTP(String phone, String code) {
            codes.put(phone, code);
            attempts.put(phone, new AtomicInteger(0));
        }

        // Lua: atomic { INCR attempts; if > max → DEL; GET code; compare }
        // Returns: 1=verified, 0=wrong, -1=locked out
        int verify(String phone, String code) {
            AtomicInteger att = attempts.get(phone);
            if (att == null) return -1;
            if (att.incrementAndGet() > maxAttempts) {
                codes.remove(phone); attempts.remove(phone);
                return -1; // LOCKED OUT
            }
            String stored = codes.get(phone);
            if (stored != null && stored.equals(code)) {
                codes.remove(phone); attempts.remove(phone);
                return 1;  // VERIFIED
            }
            return 0;      // WRONG CODE
        }

        static void demo() {
            System.out.println("=== 3. OTP VERIFICATION (SETEX + Lua) ===");
            System.out.println("    Redis: SETEX otp:phone 300 '847293' → O(1)");
            System.out.println("    Lua: atomic verify + attempt counter");

            Ex03_OTPVerification otp = new Ex03_OTPVerification(3);
            otp.generateOTP("+14155551234", "847293");
            System.out.println("    Wrong code: " + otp.verify("+14155551234", "000000"));
            System.out.println("    Wrong code: " + otp.verify("+14155551234", "111111"));
            System.out.println("    Correct:    " + otp.verify("+14155551234", "847293") + " (1=verified)\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 4. IDEMPOTENT PAYMENT — SETNX (set-if-not-exists)
    //    Redis: SET payment:id PROCESSING EX 86400 NX → O(1)
    //    Real: Stripe, Razorpay — process exactly once
    // ════════════════════════════════════════════════════════════════
    static class Ex04_IdempotentPayment {
        private final ConcurrentHashMap<String, String> processed = new ConcurrentHashMap<>();

        String processPayment(String paymentId, double amount) {
            // SETNX: only first caller wins
            String existing = processed.putIfAbsent(paymentId, "PROCESSING");
            if (existing != null) {
                return "DUPLICATE → cached: " + existing;
            }
            // Process payment...
            String result = "SUCCESS:txn_" + paymentId + ":$" + amount;
            processed.put(paymentId, result);
            return result;
        }

        static void demo() throws Exception {
            System.out.println("=== 4. IDEMPOTENT PAYMENT (SETNX) ===");
            System.out.println("    Redis: SET payment:id PROCESSING NX EX 86400 → O(1)");
            System.out.println("    Only first caller wins. Retries get cached result.");

            Ex04_IdempotentPayment sys = new Ex04_IdempotentPayment();
            ExecutorService e = Executors.newFixedThreadPool(5);
            for (int i = 0; i < 5; i++) {
                e.submit(() -> System.out.println("    " + Thread.currentThread().getName()
                    + " → " + sys.processPayment("PAY-001", 99.99)));
            }
            e.shutdown(); e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.println();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 5. PAGE VIEW COUNTER — INCR (atomic, O(1))
    //    Redis: INCR views:article:456 → O(1)
    //    Real: Medium, Dev.to — 100K+ increments/sec
    // ════════════════════════════════════════════════════════════════
    static class Ex05_PageViewCounter {
        private final ConcurrentHashMap<String, AtomicLong> views = new ConcurrentHashMap<>();

        long incr(String key) {
            return views.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        }

        long get(String key) {
            AtomicLong v = views.get(key);
            return v != null ? v.get() : 0;
        }

        static void demo() throws Exception {
            System.out.println("=== 5. PAGE VIEW COUNTER (INCR) ===");
            System.out.println("    Redis: INCR views:article:456 → O(1), atomic");

            Ex05_PageViewCounter counter = new Ex05_PageViewCounter();
            ExecutorService e = Executors.newFixedThreadPool(10);
            CountDownLatch done = new CountDownLatch(1000);
            for (int i = 0; i < 1000; i++) {
                e.submit(() -> { counter.incr("article:456"); done.countDown(); });
            }
            done.await(); e.shutdown();
            System.out.println("    1000 concurrent INCR → views = " + counter.get("article:456") + "\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 6. FLASH SALE STOCK — Lua (atomic GET+check+DECRBY)
    //    Redis: Lua{GET→check→DECRBY} → O(1), no overselling
    //    Real: Amazon Lightning Deals, flash sales
    // ════════════════════════════════════════════════════════════════
    static class Ex06_FlashSaleStock {
        private final AtomicInteger stock;

        Ex06_FlashSaleStock(int initialStock) { this.stock = new AtomicInteger(initialStock); }

        // Simulates Lua: atomic { local s = GET; if s < qty return 0; DECRBY; return remaining }
        int purchase(int qty) {
            while (true) {
                int current = stock.get();
                if (current < qty) return -1; // SOLD OUT
                if (stock.compareAndSet(current, current - qty)) return current - qty;
            }
        }

        static void demo() throws Exception {
            System.out.println("=== 6. FLASH SALE STOCK (Lua — no overselling) ===");
            System.out.println("    Redis: Lua{GET + check + DECRBY} → O(1), atomic");

            Ex06_FlashSaleStock sale = new Ex06_FlashSaleStock(10);
            AtomicInteger wins = new AtomicInteger(), losses = new AtomicInteger();
            ExecutorService e = Executors.newFixedThreadPool(50);
            CountDownLatch done = new CountDownLatch(50);
            for (int i = 0; i < 50; i++) {
                e.submit(() -> {
                    if (sale.purchase(1) >= 0) wins.incrementAndGet();
                    else losses.incrementAndGet();
                    done.countDown();
                });
            }
            done.await(); e.shutdown();
            System.out.printf("    Stock=10 | Buyers=50 | Won=%d | Lost=%d | Remaining=%d%n%n",
                    wins.get(), losses.get(), sale.stock.get());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 7. CACHE WITH TTL — SET EX (cache-aside pattern)
    //    Redis: GET key → miss → query DB → SET key val EX 300 → O(1)
    //    Real: Twitter timeline cache, any read-heavy system
    // ════════════════════════════════════════════════════════════════
    static class Ex07_CacheWithTTL {
        private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

        String getOrLoad(String key) {
            String val = cache.get(key);
            if (val != null) { System.out.println("      CACHE HIT: " + key); return val; }
            System.out.println("      CACHE MISS: " + key + " → querying DB...");
            String dbResult = "{\"user\":\"alice\",\"tweets\":[\"Hello\"]}"; // simulate DB
            cache.put(key, dbResult); // SET key val EX 300
            return dbResult;
        }

        static void demo() {
            System.out.println("=== 7. CACHE WITH TTL (SET EX — cache-aside) ===");
            System.out.println("    Redis: GET key → miss → SET key val EX 300 → O(1)");

            Ex07_CacheWithTTL c = new Ex07_CacheWithTTL();
            c.getOrLoad("timeline:user:123"); // MISS
            c.getOrLoad("timeline:user:123"); // HIT
            c.cache.remove("timeline:user:123"); // DEL (invalidate)
            c.getOrLoad("timeline:user:123"); // MISS again
            System.out.println();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 8. DISTRIBUTED LOCK — SET NX PX + Lua release
    //    Redis: SET lock:resource owner NX PX 30000 → O(1)
    //    Lua release: if GET == owner then DEL → O(1)
    //    Real: Cron jobs, leader election, resource mutex
    // ════════════════════════════════════════════════════════════════
    static class Ex08_DistributedLock {
        private final ConcurrentHashMap<String, String> locks = new ConcurrentHashMap<>();

        boolean acquire(String resource, String owner) {
            // SET key owner NX → only if not exists
            return locks.putIfAbsent("lock:" + resource, owner) == null;
        }

        boolean release(String resource, String owner) {
            // Lua: if GET(key) == owner then DEL(key) → prevent stealing
            return locks.remove("lock:" + resource, owner);
        }

        static void demo() {
            System.out.println("=== 8. DISTRIBUTED LOCK (SET NX PX + Lua) ===");
            System.out.println("    Redis: SET lock:res owner NX PX 30000 → O(1)");
            System.out.println("    Lua: if GET == owner then DEL (safe release)");

            Ex08_DistributedLock lock = new Ex08_DistributedLock();
            System.out.println("    Server-A acquire: " + lock.acquire("daily_report", "server-A"));
            System.out.println("    Server-B acquire: " + lock.acquire("daily_report", "server-B"));
            System.out.println("    Server-B release (not owner): " + lock.release("daily_report", "server-B"));
            System.out.println("    Server-A release: " + lock.release("daily_report", "server-A"));
            System.out.println("    Server-B retry:   " + lock.acquire("daily_report", "server-B") + "\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 9. USER PROFILE — HASH (HSET, HGET, HINCRBY, HGETALL)
    //    Redis: HSET user:123 name Alice followers 15420 → O(1)/field
    //    Real: Instagram, any user profile system
    // ════════════════════════════════════════════════════════════════
    static class Ex09_UserProfile {
        private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> hashes = new ConcurrentHashMap<>();

        void hset(String key, String field, String value) {
            hashes.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(field, value);
        }
        String hget(String key, String field) {
            Map<String, String> h = hashes.get(key);
            return h != null ? h.get(field) : null;
        }
        long hincrBy(String key, String field, long delta) {
            ConcurrentHashMap<String, String> h = hashes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
            // Atomic increment within hash field
            String val = h.merge(field, String.valueOf(delta),
                    (old, inc) -> String.valueOf(Long.parseLong(old) + Long.parseLong(inc)));
            return Long.parseLong(val);
        }
        Map<String, String> hgetAll(String key) {
            return hashes.getOrDefault(key, new ConcurrentHashMap<>());
        }

        static void demo() {
            System.out.println("=== 9. USER PROFILE (HASH) ===");
            System.out.println("    Redis: HSET user:123 name Alice → O(1)");
            System.out.println("    HINCRBY user:123 followers 1 → O(1) atomic");

            Ex09_UserProfile redis = new Ex09_UserProfile();
            redis.hset("user:123", "username", "alice_dev");
            redis.hset("user:123", "followers", "15420");
            redis.hset("user:123", "posts", "347");
            System.out.println("    HGET username: " + redis.hget("user:123", "username"));
            System.out.println("    HINCRBY followers +1: " + redis.hincrBy("user:123", "followers", 1));
            System.out.println("    HGETALL: " + redis.hgetAll("user:123") + "\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 10. SHOPPING CART — HASH per user
    //     Redis: HSET cart:user:123 sku:LAPTOP 1 → O(1)
    //     HINCRBY cart:user:123 sku:MOUSE 1 → atomic qty update
    //     Real: Amazon, any e-commerce
    // ════════════════════════════════════════════════════════════════
    static class Ex10_ShoppingCart {
        private final ConcurrentHashMap<String, String> cart = new ConcurrentHashMap<>();

        void addItem(String sku, int qty) { cart.put(sku, String.valueOf(qty)); }
        void updateQty(String sku, int delta) {
            cart.merge(sku, String.valueOf(delta),
                    (old, inc) -> String.valueOf(Integer.parseInt(old) + Integer.parseInt(inc)));
        }
        void removeItem(String sku) { cart.remove(sku); }
        Map<String, String> getAll() { return new HashMap<>(cart); }
        int itemCount() { return cart.size(); }

        static void demo() {
            System.out.println("=== 10. SHOPPING CART (HASH) ===");
            System.out.println("    Redis: HSET cart:user:123 sku:LAPTOP 1 → O(1)");

            Ex10_ShoppingCart cart = new Ex10_ShoppingCart();
            cart.addItem("sku:LAPTOP", 1);
            cart.addItem("sku:MOUSE", 2);
            System.out.println("    Cart: " + cart.getAll());
            cart.updateQty("sku:MOUSE", 1);
            System.out.println("    After +1 mouse: " + cart.getAll());
            cart.removeItem("sku:MOUSE");
            System.out.println("    After remove: " + cart.getAll() + " | items=" + cart.itemCount() + "\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 11. FEATURE FLAGS — HASH (per-user + global defaults)
    //     Redis: HGET flags:user:123 dark_mode → O(1)
    //     Fallback: HGET flags:global dark_mode → O(1)
    //     Real: Netflix, any feature toggle system
    // ════════════════════════════════════════════════════════════════
    static class Ex11_FeatureFlags {
        private final Map<String, Map<String, String>> flags = new ConcurrentHashMap<>();

        void setGlobal(String feature, boolean enabled) {
            flags.computeIfAbsent("global", k -> new ConcurrentHashMap<>())
                 .put(feature, enabled ? "1" : "0");
        }
        void setUserFlag(String userId, String feature, boolean enabled) {
            flags.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                 .put(feature, enabled ? "1" : "0");
        }
        boolean isEnabled(String userId, String feature) {
            // Check user override first, then global
            Map<String, String> userFlags = flags.get(userId);
            if (userFlags != null && userFlags.containsKey(feature))
                return "1".equals(userFlags.get(feature));
            Map<String, String> globalFlags = flags.get("global");
            return globalFlags != null && "1".equals(globalFlags.get(feature));
        }

        static void demo() {
            System.out.println("=== 11. FEATURE FLAGS (HASH) ===");
            System.out.println("    Redis: HGET flags:user:123 dark_mode → O(1)");

            Ex11_FeatureFlags ff = new Ex11_FeatureFlags();
            ff.setGlobal("dark_mode", false);
            ff.setGlobal("new_player", false);
            ff.setUserFlag("user:123", "dark_mode", true); // override
            System.out.println("    user:123 dark_mode: " + ff.isEnabled("user:123", "dark_mode"));
            System.out.println("    user:123 new_player: " + ff.isEnabled("user:123", "new_player"));
            System.out.println("    user:999 dark_mode: " + ff.isEnabled("user:999", "dark_mode") + "\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 12. MESSAGE QUEUE — LIST (LPUSH + RPOP = FIFO)
    //     Redis: LPUSH queue:jobs '{json}' / RPOP queue:jobs → O(1)
    //     Real: Uber ride dispatch, email sending
    // ════════════════════════════════════════════════════════════════
    static class Ex12_MessageQueue {
        private final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();

        void lpush(String msg) { queue.addFirst(msg); }      // O(1)
        String rpop() { return queue.pollLast(); }             // O(1)
        int llen() { return queue.size(); }                    // O(1)

        static void demo() {
            System.out.println("=== 12. MESSAGE QUEUE (LIST — LPUSH/RPOP) ===");
            System.out.println("    Redis: LPUSH queue:jobs msg / RPOP queue:jobs → O(1)");

            Ex12_MessageQueue q = new Ex12_MessageQueue();
            q.lpush("{\"rider\":123,\"pickup\":\"Times Square\"}");
            q.lpush("{\"rider\":456,\"pickup\":\"Central Park\"}");
            q.lpush("{\"rider\":789,\"pickup\":\"Wall Street\"}");
            System.out.println("    Queue depth: " + q.llen());
            System.out.println("    RPOP (oldest): " + q.rpop());
            System.out.println("    RPOP (next):   " + q.rpop());
            System.out.println("    Remaining: " + q.llen() + "\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 13. RECENT ACTIVITY — LIST (LPUSH + LTRIM = bounded)
    //     Redis: LPUSH activity:user:123 '{json}' / LTRIM 0 49 → O(1)
    //     Real: LinkedIn activity feed, notifications
    // ════════════════════════════════════════════════════════════════
    static class Ex13_RecentActivity {
        private final LinkedList<String> list = new LinkedList<>();
        private final int maxSize;

        Ex13_RecentActivity(int maxSize) { this.maxSize = maxSize; }

        synchronized void addActivity(String activity) {
            list.addFirst(activity);  // LPUSH
            while (list.size() > maxSize) list.removeLast();  // LTRIM
        }

        synchronized List<String> getRecent(int count) {
            return new ArrayList<>(list.subList(0, Math.min(count, list.size())));  // LRANGE 0 count-1
        }

        static void demo() {
            System.out.println("=== 13. RECENT ACTIVITY (LIST — LPUSH + LTRIM) ===");
            System.out.println("    Redis: LPUSH + LTRIM 0 49 → bounded to 50 items");

            Ex13_RecentActivity feed = new Ex13_RecentActivity(5);
            for (int i = 1; i <= 8; i++) feed.addActivity("action_" + i);
            System.out.println("    Last 3 (of 8 added, max 5): " + feed.getRecent(3) + "\n");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 14. BLOCKING WORKER QUEUE — BLPOP (block until job arrives)
    //     Redis: BLPOP queue:emails 30 → blocks up to 30s
    //     Real: Email workers, background job processors
    // ════════════════════════════════════════════════════════════════
    static class Ex14_BlockingWorkerQueue {
        private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

        void enqueue(String job) { queue.offer(job); }

        // Simulates BLPOP: blocks until job available or timeout
        String blockingPop(long timeoutMs) throws InterruptedException {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        static void demo() throws Exception {
            System.out.println("=== 14. BLOCKING WORKER QUEUE (BLPOP) ===");
            System.out.println("    Redis: BLPOP queue:emails 30 → O(1), blocks until job");

            Ex14_BlockingWorkerQueue q = new Ex14_BlockingWorkerQueue();

            Thread worker = new Thread(() -> {
                try {
                    String job = q.blockingPop(2000);
                    System.out.println("    [Worker] Got job: " + job);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            worker.start();
            Thread.sleep(100); // Worker is blocking...
            q.enqueue("{\"to\":\"alice@gmail.com\",\"subject\":\"Welcome!\"}");
            worker.join(3000);
            System.out.println();
        }
    }

    // 15. RECENTLY VIEWED — LIST (LREM + LPUSH + LTRIM = no dupes, bounded)
    static class Ex15_RecentlyViewedProducts {
        private final LinkedList<String> list = new LinkedList<>();
        private final int max;
        Ex15_RecentlyViewedProducts(int max) { this.max = max; }

        synchronized void viewed(String product) {
            list.remove(product);     // LREM (remove old occurrence)
            list.addFirst(product);   // LPUSH (add to front)
            while (list.size() > max) list.removeLast(); // LTRIM
        }
        synchronized List<String> getRecent() { return new ArrayList<>(list); }

        static void demo() {
            System.out.println("=== 15. RECENTLY VIEWED (LREM+LPUSH+LTRIM) ===");
            System.out.println("    Redis: LREM key 0 val → LPUSH key val → LTRIM 0 19");
            Ex15_RecentlyViewedProducts rv = new Ex15_RecentlyViewedProducts(3);
            rv.viewed("LAPTOP"); rv.viewed("MOUSE"); rv.viewed("KEYBOARD"); rv.viewed("LAPTOP");
            System.out.println("    History (LAPTOP viewed twice, deduped): " + rv.getRecent() + "\n");
        }
    }

    // 16. UNIQUE VISITORS — SET (SADD + SCARD)
    static class Ex16_UniqueVisitors {
        private final Set<String> visitors = ConcurrentHashMap.newKeySet();
        boolean sadd(String member) { return visitors.add(member); } // O(1)
        int scard() { return visitors.size(); }                       // O(1)

        static void demo() {
            System.out.println("=== 16. UNIQUE VISITORS (SET — SADD + SCARD) ===");
            System.out.println("    Redis: SADD visitors:page:date user:123 → O(1)");
            Ex16_UniqueVisitors v = new Ex16_UniqueVisitors();
            System.out.println("    SADD user:123 (new): " + v.sadd("user:123"));
            System.out.println("    SADD user:456 (new): " + v.sadd("user:456"));
            System.out.println("    SADD user:123 (dup): " + v.sadd("user:123"));
            System.out.println("    SCARD (unique count): " + v.scard() + "\n");
        }
    }

    // 17. ONLINE USERS — SET (SADD, SREM, SCARD, SISMEMBER)
    static class Ex17_OnlineUsers {
        private final Set<String> online = ConcurrentHashMap.newKeySet();
        void addUser(String u) { online.add(u); }
        void removeUser(String u) { online.remove(u); }
        boolean isOnline(String u) { return online.contains(u); } // SISMEMBER O(1)
        int count() { return online.size(); }                      // SCARD O(1)

        static void demo() {
            System.out.println("=== 17. ONLINE USERS (SET — SADD/SREM/SISMEMBER) ===");
            System.out.println("    Redis: SISMEMBER online:server user:123 → O(1)");
            Ex17_OnlineUsers ou = new Ex17_OnlineUsers();
            ou.addUser("alice"); ou.addUser("bob"); ou.addUser("charlie");
            System.out.println("    Online: " + ou.count() + " | bob online: " + ou.isOnline("bob"));
            ou.removeUser("bob");
            System.out.println("    After bob leaves: " + ou.count() + " | bob online: " + ou.isOnline("bob") + "\n");
        }
    }

    // 18. MUTUAL FRIENDS — SET (SINTER)
    static class Ex18_MutualFriends {
        static void demo() {
            System.out.println("=== 18. MUTUAL FRIENDS (SET — SINTER) ===");
            System.out.println("    Redis: SINTER friends:alice friends:bob → O(N*M)");
            Set<String> alice = new HashSet<>(Arrays.asList("bob", "charlie", "dave", "eve"));
            Set<String> bob = new HashSet<>(Arrays.asList("alice", "charlie", "frank"));
            Set<String> mutual = new HashSet<>(alice);
            mutual.retainAll(bob); // SINTER
            System.out.println("    Alice's friends: " + alice);
            System.out.println("    Bob's friends:   " + bob);
            System.out.println("    Mutual (SINTER): " + mutual + "\n");
        }
    }

    // 19. LEADERBOARD — SORTED SET (ZADD, ZREVRANGE, ZREVRANK)
    static class Ex19_Leaderboard {
        private final ConcurrentSkipListMap<Double, Set<String>> byScore = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
        private final ConcurrentHashMap<String, Double> scores = new ConcurrentHashMap<>();

        void zadd(String member, double score) {
            Double old = scores.put(member, score);
            if (old != null) { Set<String> s = byScore.get(old); if (s != null) s.remove(member); }
            byScore.computeIfAbsent(score, k -> ConcurrentHashMap.newKeySet()).add(member);
        }
        void zincrby(String member, double delta) {
            double newScore = scores.merge(member, delta, Double::sum);
            Double old = newScore - delta;
            Set<String> s = byScore.get(old); if (s != null) s.remove(member);
            byScore.computeIfAbsent(newScore, k -> ConcurrentHashMap.newKeySet()).add(member);
        }
        List<String> topN(int n) {
            List<String> result = new ArrayList<>();
            for (Map.Entry<Double, Set<String>> e : byScore.entrySet()) {
                for (String m : e.getValue()) {
                    result.add(m + ":" + e.getKey().intValue());
                    if (result.size() >= n) return result;
                }
            }
            return result;
        }

        static void demo() {
            System.out.println("=== 19. LEADERBOARD (SORTED SET — ZADD/ZREVRANGE) ===");
            System.out.println("    Redis: ZADD lb 1500 alice → O(log N) | ZREVRANGE lb 0 2 → top 3");
            Ex19_Leaderboard lb = new Ex19_Leaderboard();
            lb.zadd("alice", 1500); lb.zadd("bob", 2200); lb.zadd("charlie", 1800); lb.zadd("dave", 3100);
            lb.zincrby("alice", 500); // alice now 2000
            System.out.println("    Top 3: " + lb.topN(3));
            System.out.println("    Alice score: " + lb.scores.get("alice").intValue() + "\n");
        }
    }

    // 20. DELAYED JOB SCHEDULER — SORTED SET (score = timestamp)
    static class Ex20_DelayedJobScheduler {
        private final TreeMap<Long, List<String>> jobs = new TreeMap<>();

        synchronized void schedule(String job, long executeAt) {
            jobs.computeIfAbsent(executeAt, k -> new ArrayList<>()).add(job);
        }
        synchronized List<String> pollDue(long now) {
            List<String> due = new ArrayList<>();
            while (!jobs.isEmpty() && jobs.firstKey() <= now) {
                due.addAll(jobs.pollFirstEntry().getValue());
            }
            return due;
        }

        static void demo() {
            System.out.println("=== 20. DELAYED JOB SCHEDULER (ZSET — score=timestamp) ===");
            System.out.println("    Redis: ZADD scheduler:jobs <timestamp> job → O(log N)");
            Ex20_DelayedJobScheduler s = new Ex20_DelayedJobScheduler();
            long now = System.currentTimeMillis();
            s.schedule("send_email_alice", now - 1000);   // past → due
            s.schedule("send_email_bob", now - 500);      // past → due
            s.schedule("send_email_charlie", now + 60000); // future → not due
            List<String> due = s.pollDue(now);
            System.out.println("    Due now: " + due);
            System.out.println("    Remaining (future): " + s.jobs.size() + "\n");
        }
    }

    // 21. RANDOM WINNER — SET (SPOP)
    static class Ex21_RandomWinner {
        static void demo() {
            System.out.println("=== 21. RANDOM WINNER (SET — SPOP) ===");
            System.out.println("    Redis: SPOP contest:entries 3 → O(N), removes random members");
            List<String> entries = new ArrayList<>(Arrays.asList("user:100","user:200","user:300","user:400","user:500"));
            Collections.shuffle(entries);
            List<String> winners = entries.subList(0, 3);
            System.out.println("    Winners (SPOP 3): " + winners + "\n");
        }
    }

    // 22. SUBSCRIPTION CHECK — SET (SISMEMBER)
    static class Ex22_SubscriptionCheck {
        static void demo() {
            System.out.println("=== 22. SUBSCRIPTION CHECK (SET — SISMEMBER) ===");
            System.out.println("    Redis: SISMEMBER subscribers:premium user:123 → O(1)");
            Set<String> premium = new HashSet<>(Arrays.asList("user:123", "user:456"));
            System.out.println("    user:123 premium? " + premium.contains("user:123")); // O(1)
            System.out.println("    user:999 premium? " + premium.contains("user:999") + "\n");
        }
    }

    // 23. UNIQUE COUNT (HyperLogLog approx) — PFADD/PFCOUNT
    static class Ex23_UniqueCountHyperLogLog {
        static void demo() {
            System.out.println("=== 23. UNIQUE COUNT — HYPERLOGLOG (PFADD/PFCOUNT) ===");
            System.out.println("    Redis: PFADD hll:visitors user:123 → O(1), 12KB fixed memory");
            System.out.println("    100M unique visitors: SET=1.6GB vs HLL=12KB (~0.81% error)");
            // Simulate with HashSet (exact), real Redis uses probabilistic 12KB structure
            Set<String> hll = new HashSet<>();
            for (int i = 0; i < 10000; i++) hll.add("user:" + (i % 8500)); // 8500 unique
            System.out.println("    Added 10000 elements (8500 unique) → PFCOUNT = " + hll.size() + "\n");
        }
    }

    // 24. SLIDING WINDOW RATE LIMITER — SORTED SET
    static class Ex24_SlidingWindowRateLimiter {
        private final TreeMap<Long, String> requests = new TreeMap<>();
        private final int limit;
        private final long windowMs;

        Ex24_SlidingWindowRateLimiter(int limit, long windowMs) {
            this.limit = limit; this.windowMs = windowMs;
        }

        synchronized boolean isAllowed(String requestId) {
            long now = System.nanoTime() / 1_000_000;
            // ZREMRANGEBYSCORE key 0 (now - window)
            requests.headMap(now - windowMs).clear();
            // ZCARD key → count in window
            if (requests.size() >= limit) return false;
            // ZADD key now requestId
            requests.put(now, requestId);
            return true;
        }

        static void demo() {
            System.out.println("=== 24. SLIDING WINDOW RATE LIMITER (SORTED SET) ===");
            System.out.println("    Redis: ZADD rate:user ts uuid → ZREMRANGEBYSCORE → ZCARD → O(log N)");
            Ex24_SlidingWindowRateLimiter rl = new Ex24_SlidingWindowRateLimiter(5, 1000);
            int allowed = 0;
            for (int i = 0; i < 8; i++) {
                if (rl.isAllowed("req:" + i)) allowed++;
            }
            System.out.println("    Limit=5/sec | Requests=8 | Allowed=" + allowed + "\n");
        }
    }

    // 25. ATOMIC TRANSFER — Lua Script (DECRBY + INCRBY atomically)
    static class Ex25_AtomicTransfer {
        private final ConcurrentHashMap<String, AtomicLong> wallets = new ConcurrentHashMap<>();

        void setBalance(String user, long amount) {
            wallets.put(user, new AtomicLong(amount));
        }

        // Simulates Lua: atomic { if GET(from) < amount return error; DECRBY from; INCRBY to }
        synchronized String transfer(String from, String to, long amount) {
            AtomicLong fromBal = wallets.get(from);
            AtomicLong toBal = wallets.get(to);
            if (fromBal == null || toBal == null) return "ACCOUNT_NOT_FOUND";
            if (fromBal.get() < amount) return "INSUFFICIENT_FUNDS";
            fromBal.addAndGet(-amount);
            toBal.addAndGet(amount);
            return "SUCCESS: " + from + "=" + fromBal.get() + ", " + to + "=" + toBal.get();
        }

        static void demo() {
            System.out.println("=== 25. ATOMIC TRANSFER (Lua — DECRBY+INCRBY) ===");
            System.out.println("    Redis: Lua{GET→check→DECRBY→INCRBY} → O(1), all-or-nothing");
            Ex25_AtomicTransfer sys = new Ex25_AtomicTransfer();
            sys.setBalance("alice", 1000);
            sys.setBalance("bob", 500);
            System.out.println("    Transfer 300: " + sys.transfer("alice", "bob", 300));
            System.out.println("    Transfer 800: " + sys.transfer("alice", "bob", 800));
            System.out.println("    Final: alice=" + sys.wallets.get("alice").get()
                    + " bob=" + sys.wallets.get("bob").get() + "\n");
        }
    }
}
