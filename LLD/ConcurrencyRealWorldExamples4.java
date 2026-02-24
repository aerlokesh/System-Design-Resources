import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * 21 MORE CONCURRENCY EXAMPLES (51-71)
 * Run: java ConcurrencyRealWorldExamples4
 */
public class ConcurrencyRealWorldExamples4 {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   CONCURRENCY EXAMPLES 51-71                                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        Ex51.demo(); Ex52.demo(); Ex53.demo(); Ex54.demo(); Ex55.demo();
        Ex56.demo(); Ex57.demo(); Ex58.demo(); Ex59.demo(); Ex60.demo();
        Ex61.demo(); Ex62.demo(); Ex63.demo(); Ex64.demo(); Ex65.demo();
        Ex66.demo(); Ex67.demo(); Ex68.demo(); Ex69.demo(); Ex70.demo();
        Ex71.demo();
        System.out.println("\n✅ ALL 21 EXAMPLES (51-71) COMPLETED!");
    }

    // 51. FILE UPLOAD LIMITER — Semaphore
    static class Ex51 {
        static final Semaphore slots = new Semaphore(3);
        static void upload(String file) throws InterruptedException {
            slots.acquire();
            try {
                System.out.println("  ↑ " + file + " uploading (slots=" + slots.availablePermits() + ")");
                Thread.sleep(150);
            } finally { slots.release(); System.out.println("  ✓ " + file + " done"); }
        }
        static void demo() throws Exception {
            System.out.println("\n=== 51. FILE UPLOAD LIMITER (Semaphore, max 3) ===");
            ExecutorService e = Executors.newFixedThreadPool(6);
            for (int i = 1; i <= 6; i++) { int id=i; e.submit(() -> { try{upload("file"+id);}catch(Exception x){} }); }
            e.shutdown(); e.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // 52. CHAT SERVER BROADCAST — CopyOnWriteArrayList
    static class Ex52 {
        static final CopyOnWriteArrayList<String> clients = new CopyOnWriteArrayList<>();
        static void broadcast(String msg) {
            for (String c : clients) System.out.println("  [→" + c + "] " + msg);
        }
        static void demo() throws Exception {
            System.out.println("\n=== 52. CHAT BROADCAST (CopyOnWriteArrayList) ===");
            clients.addAll(List.of("Alice", "Bob", "Charlie"));
            ExecutorService e = Executors.newFixedThreadPool(3);
            e.submit(() -> broadcast("Hello everyone!"));
            e.submit(() -> { clients.add("Diana"); System.out.println("  [+] Diana joined"); });
            e.submit(() -> broadcast("Welcome Diana!"));
            e.shutdown(); e.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // 53. LEADER ELECTION — tryLock()
    static class Ex53 {
        static final ReentrantLock leaderLock = new ReentrantLock();
        static volatile String leader = "none";
        static boolean tryBecomeLeader(String node) {
            if (leaderLock.tryLock()) {
                leader = node;
                System.out.println("  " + node + " → ELECTED LEADER ✓");
                // In real system, leader holds lock until stepping down
                leaderLock.unlock(); // release after election for demo
                return true;
            }
            System.out.println("  " + node + " → not leader (already taken)");
            return false;
        }
        static void demo() throws Exception {
            System.out.println("\n=== 53. LEADER ELECTION (tryLock) ===");
            ExecutorService e = Executors.newFixedThreadPool(5);
            CountDownLatch done = new CountDownLatch(5);
            for (int i = 1; i <= 5; i++) { int id=i; e.submit(() -> { tryBecomeLeader("Node"+id); done.countDown(); }); }
            done.await(); e.shutdown();
            System.out.println("  Final leader: " + leader);
        }
    }

    // 54. CONCURRENT BANK STATEMENT GENERATION — ExecutorService + Future
    static class Ex54 {
        static String generateStatement(String account) throws InterruptedException {
            Thread.sleep(100);
            double bal = ThreadLocalRandom.current().nextDouble(1000, 50000);
            return String.format("%s: $%.2f", account, bal);
        }
        static void demo() throws Exception {
            System.out.println("\n=== 54. BANK STATEMENTS (ExecutorService+Future) ===");
            ExecutorService pool = Executors.newFixedThreadPool(3);
            String[] accounts = {"ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005"};
            List<Future<String>> futures = new ArrayList<>();
            for (String acc : accounts) futures.add(pool.submit(() -> generateStatement(acc)));
            for (Future<String> f : futures) System.out.println("  " + f.get());
            pool.shutdown();
        }
    }

    // 55. SCHEDULED REPORT GENERATION — ScheduledExecutorService
    static class Ex55 {
        static void demo() throws Exception {
            System.out.println("\n=== 55. SCHEDULED REPORTS (ScheduledExecutor) ===");
            ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
            AtomicInteger count = new AtomicInteger(0);
            ScheduledFuture<?> f = sched.scheduleAtFixedRate(() -> {
                int c = count.incrementAndGet();
                System.out.println("  [Report #" + c + "] Generated at " + System.currentTimeMillis() % 10000);
            }, 0, 100, TimeUnit.MILLISECONDS);
            Thread.sleep(350); f.cancel(false); sched.shutdown();
            System.out.println("  Total reports: " + count.get());
        }
    }

    // 56. THREAD-SAFE LRU CACHE — LinkedHashMap + ReentrantLock
    static class Ex56 {
        static class LRUCache<K,V> {
            private final int capacity;
            private final LinkedHashMap<K,V> map;
            private final ReentrantLock lock = new ReentrantLock();

            LRUCache(int cap) {
                this.capacity = cap;
                this.map = new LinkedHashMap<>(cap, 0.75f, true) {
                    protected boolean removeEldestEntry(Map.Entry<K,V> e) { return size() > capacity; }
                };
            }
            V get(K key) { lock.lock(); try { return map.get(key); } finally { lock.unlock(); } }
            void put(K key, V val) { lock.lock(); try { map.put(key, val); } finally { lock.unlock(); } }
            int size() { lock.lock(); try { return map.size(); } finally { lock.unlock(); } }
            String snapshot() { lock.lock(); try { return map.toString(); } finally { lock.unlock(); } }
        }
        static void demo() throws Exception {
            System.out.println("\n=== 56. LRU CACHE (LinkedHashMap+Lock) ===");
            LRUCache<String,String> cache = new LRUCache<>(3);
            ExecutorService e = Executors.newFixedThreadPool(4);
            e.submit(() -> { cache.put("A","1"); cache.put("B","2"); cache.put("C","3"); });
            Thread.sleep(30);
            e.submit(() -> cache.get("A")); // access A → A becomes most recent
            Thread.sleep(30);
            e.submit(() -> { cache.put("D","4"); System.out.println("  After adding D: " + cache.snapshot()); });
            // B should be evicted (LRU)
            e.shutdown(); e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.println("  Final: " + cache.snapshot() + " (B evicted, size=" + cache.size() + ")");
        }
    }

    // 57. GAME SCOREBOARD — ConcurrentHashMap + AtomicInteger
    static class Ex57 {
        static final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();
        static void addScore(String player, int pts) {
            scores.computeIfAbsent(player, k -> new AtomicInteger(0)).addAndGet(pts);
        }
        static void demo() throws Exception {
            System.out.println("\n=== 57. GAME SCOREBOARD (ConcurrentHashMap+Atomic) ===");
            ExecutorService e = Executors.newFixedThreadPool(5);
            String[] players = {"Warrior", "Mage", "Archer"};
            CountDownLatch done = new CountDownLatch(300);
            for (int i = 0; i < 300; i++) {
                int idx = i;
                e.submit(() -> { addScore(players[idx%3], ThreadLocalRandom.current().nextInt(1,20)); done.countDown(); });
            }
            done.await(); e.shutdown();
            scores.forEach((k,v) -> System.out.println("  " + k + ": " + v.get()));
        }
    }

    // 58. WORKER POOL FOR IMAGE RESIZING — ExecutorService + BlockingQueue
    static class Ex58 {
        static void demo() throws Exception {
            System.out.println("\n=== 58. IMAGE RESIZE WORKERS (Executor+Queue) ===");
            BlockingQueue<String> tasks = new LinkedBlockingQueue<>();
            for (int i = 1; i <= 6; i++) tasks.put("img" + i + ".png");
            ExecutorService pool = Executors.newFixedThreadPool(3);
            AtomicInteger processed = new AtomicInteger(0);
            for (int w = 0; w < 3; w++) {
                pool.submit(() -> {
                    String img;
                    while ((img = tasks.poll()) != null) {
                        try { Thread.sleep(80); } catch (InterruptedException ex) {}
                        processed.incrementAndGet();
                        System.out.println("  [" + Thread.currentThread().getName() + "] Resized " + img);
                    }
                });
            }
            pool.shutdown(); pool.awaitTermination(3, TimeUnit.SECONDS);
            System.out.println("  Total resized: " + processed.get());
        }
    }

    // 59. PAYMENT GATEWAY QUEUE — BlockingQueue
    static class Ex59 {
        static void demo() throws Exception {
            System.out.println("\n=== 59. PAYMENT GATEWAY QUEUE (BlockingQueue) ===");
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);
            AtomicInteger ok = new AtomicInteger(0);
            Thread producer = new Thread(() -> {
                try { for (int i=1;i<=6;i++) { queue.put("PAY-"+i); System.out.println("  [Q] PAY-"+i); } queue.put("STOP");
                } catch (InterruptedException e) {} });
            Thread consumer = new Thread(() -> {
                try { while(true) { String p=queue.take(); if("STOP".equals(p)) break;
                    Thread.sleep(50); ok.incrementAndGet(); System.out.println("  [$] Processed "+p); }
                } catch (InterruptedException e) {} });
            producer.start(); consumer.start(); producer.join(); consumer.join();
            System.out.println("  Total: " + ok.get());
        }
    }

    // 60. SESSION MANAGEMENT — ConcurrentHashMap
    static class Ex60 {
        static final ConcurrentHashMap<String,String> sessions = new ConcurrentHashMap<>();
        static void login(String user, String token) { sessions.put(user, token); System.out.println("  [+] "+user+" logged in"); }
        static void logout(String user) { sessions.remove(user); System.out.println("  [-] "+user+" logged out"); }
        static void demo() throws Exception {
            System.out.println("\n=== 60. SESSION MANAGEMENT (ConcurrentHashMap) ===");
            ExecutorService e = Executors.newFixedThreadPool(4);
            e.submit(() -> login("Alice","tok1")); e.submit(() -> login("Bob","tok2"));
            e.submit(() -> login("Charlie","tok3"));
            Thread.sleep(30);
            e.submit(() -> logout("Bob"));
            e.shutdown(); e.awaitTermination(2, TimeUnit.SECONDS);
            System.out.println("  Active: " + sessions.keySet());
        }
    }

    // 61. STOCK PRICE AGGREGATOR — LongAdder
    static class Ex61 {
        static void demo() throws Exception {
            System.out.println("\n=== 61. STOCK PRICE AGGREGATOR (LongAdder) ===");
            LongAdder totalUpdates = new LongAdder();
            LongAdder priceSum = new LongAdder();
            int threads=8, ops=100_000;
            ExecutorService e = Executors.newFixedThreadPool(threads);
            CountDownLatch done = new CountDownLatch(threads);
            for (int i=0;i<threads;i++) {
                e.submit(() -> { for(int j=0;j<ops;j++) { priceSum.add(ThreadLocalRandom.current().nextInt(100,200)); totalUpdates.increment(); } done.countDown(); });
            }
            done.await(); e.shutdown();
            System.out.println("  Updates: "+totalUpdates.sum()+" | Avg price: "+(priceSum.sum()/totalUpdates.sum()));
        }
    }

    // 62. MULTI-STAGE DATA PIPELINE — BlockingQueue chain
    static class Ex62 {
        static void demo() throws Exception {
            System.out.println("\n=== 62. DATA PIPELINE (3-stage BlockingQueue) ===");
            BlockingQueue<String> q1 = new LinkedBlockingQueue<>(5), q2 = new LinkedBlockingQueue<>(5);
            Thread fetcher = new Thread(() -> { try { for(int i=1;i<=4;i++){Thread.sleep(30);q1.put("data-"+i);System.out.println("  [Fetch] data-"+i);} q1.put("END"); } catch(InterruptedException e){} });
            Thread processor = new Thread(() -> { try { while(true){String d=q1.take();if("END".equals(d)){q2.put("END");break;}Thread.sleep(50);q2.put(d+"-proc");System.out.println("  [Proc] "+d);} } catch(InterruptedException e){} });
            Thread saver = new Thread(() -> { try { while(true){String d=q2.take();if("END".equals(d))break;Thread.sleep(20);System.out.println("  [Save] "+d+" ✓");} } catch(InterruptedException e){} });
            fetcher.start();processor.start();saver.start();fetcher.join();processor.join();saver.join();
        }
    }

    // 63. MULTI-LEVEL CACHE — ReadWriteLock
    static class Ex63 {
        static final Map<String,String> l1 = new HashMap<>(), l2 = new HashMap<>();
        static final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        static String get(String key) {
            rwLock.readLock().lock();
            try { String v=l1.get(key); if(v!=null) return "L1:"+v; v=l2.get(key); return v!=null?"L2:"+v:"MISS"; }
            finally { rwLock.readLock().unlock(); }
        }
        static void put(String key, String val) {
            rwLock.writeLock().lock();
            try { l1.put(key,val); l2.put(key,val); } finally { rwLock.writeLock().unlock(); }
        }
        static void demo() throws Exception {
            System.out.println("\n=== 63. MULTI-LEVEL CACHE (ReadWriteLock) ===");
            put("user:1","Alice"); put("user:2","Bob");
            ExecutorService e = Executors.newFixedThreadPool(4);
            for(int i=1;i<=3;i++){int id=i;e.submit(()->System.out.println("  [R] user:"+id+"="+get("user:"+id)));}
            e.submit(()->{put("user:3","Charlie");System.out.println("  [W] Added user:3");});
            e.shutdown();e.awaitTermination(2,TimeUnit.SECONDS);
        }
    }

    // 64. ASYNC EVENT PROCESSING — CompletableFuture
    static class Ex64 {
        static CompletableFuture<String> processEvent(String event) {
            return CompletableFuture.supplyAsync(() -> { try{Thread.sleep(80);}catch(InterruptedException e){} return event+"-processed"; });
        }
        static void demo() throws Exception {
            System.out.println("\n=== 64. ASYNC EVENTS (CompletableFuture) ===");
            CompletableFuture<String> e1=processEvent("click"), e2=processEvent("scroll"), e3=processEvent("submit");
            CompletableFuture.allOf(e1,e2,e3).join();
            System.out.println("  Results: "+e1.get()+", "+e2.get()+", "+e3.get());
        }
    }

    // 65. BANK TRANSFER DEADLOCK AVOIDANCE — tryLock + retry
    static class Ex65 {
        static class Acc { int id; double bal; ReentrantLock lock=new ReentrantLock(); Acc(int id,double b){this.id=id;this.bal=b;} }
        static boolean transfer(Acc from, Acc to, double amt) throws InterruptedException {
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
            while(System.nanoTime()<deadline) {
                if(from.lock.tryLock()) { try { if(to.lock.tryLock()) { try {
                    if(from.bal>=amt){from.bal-=amt;to.bal+=amt;System.out.printf("  $%.0f: %d→%d%n",amt,from.id,to.id);return true;}
                    return false;
                } finally{to.lock.unlock();} } } finally{from.lock.unlock();} }
                Thread.sleep(ThreadLocalRandom.current().nextInt(1,5));
            }
            return false;
        }
        static void demo() throws Exception {
            System.out.println("\n=== 65. DEADLOCK-FREE TRANSFER (tryLock+retry) ===");
            Acc a=new Acc(1,1000),b=new Acc(2,1000);
            ExecutorService e=Executors.newFixedThreadPool(4);
            for(int i=0;i<3;i++){e.submit(()->{try{transfer(a,b,100);}catch(Exception x){}});e.submit(()->{try{transfer(b,a,50);}catch(Exception x){}});}
            e.shutdown();e.awaitTermination(3,TimeUnit.SECONDS);
            System.out.printf("  Total: $%.0f (should be 2000)%n",a.bal+b.bal);
        }
    }

    // 66. GAME ROUND BARRIER — CyclicBarrier
    static class Ex66 {
        static void demo() throws Exception {
            System.out.println("\n=== 66. GAME ROUND BARRIER (CyclicBarrier) ===");
            CyclicBarrier barrier=new CyclicBarrier(3,()->System.out.println("  ══ Round complete! ══"));
            ExecutorService e=Executors.newFixedThreadPool(3);
            for(int p=1;p<=3;p++){int pid=p;e.submit(()->{try{for(int r=1;r<=2;r++){Thread.sleep(ThreadLocalRandom.current().nextInt(50,150));System.out.println("  P"+pid+" done round "+r);barrier.await();}}catch(Exception x){}});}
            e.shutdown();e.awaitTermination(3,TimeUnit.SECONDS);
        }
    }

    // 67. FILE PROCESSING DEPENDENCIES — CountDownLatch
    static class Ex67 {
        static void demo() throws Exception {
            System.out.println("\n=== 67. FILE DEPENDENCIES (CountDownLatch) ===");
            CountDownLatch prereqs = new CountDownLatch(3);
            String[] files={"config.xml","schema.sql","keys.pem"};
            for(String f:files){new Thread(()->{try{Thread.sleep(ThreadLocalRandom.current().nextInt(100,200));System.out.println("  [✓] "+f+" processed");prereqs.countDown();}catch(InterruptedException e){}}).start();}
            prereqs.await();
            System.out.println("  [→] All prereqs done → starting dependent task!");
        }
    }

    // 68. PERIODIC TEMP CLEANUP — ScheduledExecutorService
    static class Ex68 {
        static void demo() throws Exception {
            System.out.println("\n=== 68. TEMP FILE CLEANUP (ScheduledExecutor) ===");
            AtomicInteger cleaned=new AtomicInteger(0);
            ScheduledExecutorService s=Executors.newScheduledThreadPool(1);
            ScheduledFuture<?> f=s.scheduleAtFixedRate(()->{int c=cleaned.addAndGet(ThreadLocalRandom.current().nextInt(1,5));System.out.println("  [Clean] Removed files, total="+c);},0,100,TimeUnit.MILLISECONDS);
            Thread.sleep(350);f.cancel(false);s.shutdown();
            System.out.println("  Total cleaned: "+cleaned.get());
        }
    }

    // 69. CONCURRENT AUCTION — ReadWriteLock
    static class Ex69 {
        static double highBid=0; static String winner="none";
        static final ReadWriteLock rwl=new ReentrantReadWriteLock();
        static boolean bid(String who,double amt){rwl.writeLock().lock();try{if(amt>highBid){highBid=amt;winner=who;System.out.printf("  [Bid!] %s: $%.0f%n",who,amt);return true;}System.out.printf("  [Low] %s: $%.0f < $%.0f%n",who,amt,highBid);return false;}finally{rwl.writeLock().unlock();}}
        static double view(String who){rwl.readLock().lock();try{return highBid;}finally{rwl.readLock().unlock();}}
        static void demo() throws Exception {
            System.out.println("\n=== 69. CONCURRENT AUCTION (ReadWriteLock) ===");
            ExecutorService e=Executors.newFixedThreadPool(6);
            e.submit(()->bid("Alice",100));e.submit(()->bid("Bob",150));e.submit(()->bid("Charlie",120));
            Thread.sleep(30);
            for(int i=1;i<=3;i++){int id=i;e.submit(()->System.out.printf("  [View%d] $%.0f%n",id,view("V"+id)));}
            e.shutdown();e.awaitTermination(2,TimeUnit.SECONDS);
            System.out.printf("  Winner: %s at $%.0f%n",winner,highBid);
        }
    }

    // 70. FLASH SALE WALLET — AtomicInteger
    static class Ex70 {
        static final AtomicInteger wallet = new AtomicInteger(500);
        static boolean deduct(String user, int amt) {
            while(true){int cur=wallet.get();if(cur<amt){System.out.println("  "+user+" FAILED $"+amt+" (bal=$"+cur+")");return false;}if(wallet.compareAndSet(cur,cur-amt)){System.out.println("  "+user+" deducted $"+amt+" → $"+(cur-amt));return true;}}
        }
        static void demo() throws Exception {
            System.out.println("\n=== 70. FLASH SALE WALLET (AtomicInteger CAS) ===");
            ExecutorService e=Executors.newFixedThreadPool(6);
            for(int i=1;i<=6;i++){int id=i;e.submit(()->deduct("User"+id,120));}
            e.shutdown();e.awaitTermination(2,TimeUnit.SECONDS);
            System.out.println("  Final balance: $"+wallet.get());
        }
    }

    // 71. MULTIPLAYER TURN ORDER — Semaphore chain
    static class Ex71 {
        static void demo() throws Exception {
            System.out.println("\n=== 71. TURN ORDER (Semaphore chain) ===");
            int players=4, rounds=2;
            Semaphore[] turns=new Semaphore[players];
            turns[0]=new Semaphore(1); // Player 0 starts
            for(int i=1;i<players;i++) turns[i]=new Semaphore(0);

            ExecutorService e=Executors.newFixedThreadPool(players);
            for(int p=0;p<players;p++){
                int pid=p;
                e.submit(()->{try{for(int r=0;r<rounds;r++){
                    turns[pid].acquire(); // wait for my turn
                    System.out.println("  Player"+(pid+1)+" plays (round "+(r+1)+")");
                    Thread.sleep(50);
                    turns[(pid+1)%players].release(); // pass to next
                }}catch(InterruptedException ex){}});
            }
            e.shutdown();e.awaitTermination(5,TimeUnit.SECONDS);
        }
    }
}