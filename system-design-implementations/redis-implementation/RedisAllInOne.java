import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.Tuple;
import java.util.*;

/**
 * REDIS ALL-IN-ONE COMPREHENSIVE GUIDE (Java / Jedis)
 * =====================================================
 * Java equivalent of redis_all_in_one.py
 *
 * Data Structures: Strings, Lists, Sets, Hashes, Sorted Sets
 *
 * Prerequisites:
 *   docker run -d --name redis-learning -p 6379:6379 redis:latest
 *
 * Dependencies (download JARs or use Maven):
 *   - jedis-5.1.0.jar
 *   - commons-pool2-2.12.0.jar
 *   - slf4j-api-2.0.9.jar, slf4j-simple-2.0.9.jar
 *
 * Compile & Run:
 *   javac -cp "jedis-5.1.0.jar:commons-pool2-2.12.0.jar:slf4j-api-2.0.9.jar:." RedisAllInOne.java
 *   java -cp "jedis-5.1.0.jar:commons-pool2-2.12.0.jar:slf4j-api-2.0.9.jar:slf4j-simple-2.0.9.jar:." RedisAllInOne
 */
public class RedisAllInOne {

    private static Jedis redis;

    // ========================================================================
    // 1. REDIS STRINGS
    // ========================================================================
    // Interview: O(1) GET/SET. Used for caching, sessions, counters, locks.

    static void stringsBasicOperations() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("STRINGS: Basic Operations");
        System.out.println("=".repeat(60));

        // SET and GET
        redis.set("username", "john_doe");
        System.out.printf("  SET/GET: %s%n", redis.get("username"));

        // SETEX - set with expiration
        redis.setex("session_token", 3600, "abc123xyz");
        System.out.printf("  SETEX TTL: %d seconds%n", redis.ttl("session_token"));

        // SETNX - distributed lock (Set if Not eXists)
        long r1 = redis.setnx("lock", "process_1");
        long r2 = redis.setnx("lock", "process_2");
        System.out.printf("  SETNX lock: first=%s, second=%s%n", r1 == 1, r2 == 1);

        // MSET / MGET - batch operations (reduce round trips)
        redis.mset("user:1:name", "Alice", "user:1:email", "alice@test.com");
        System.out.printf("  MSET/MGET: %s%n", redis.mget("user:1:name", "user:1:email"));

        // APPEND and STRLEN
        redis.set("msg", "Hello");
        redis.append("msg", " World!");
        System.out.printf("  APPEND: %s (len=%d)%n", redis.get("msg"), redis.strlen("msg"));

        redis.del("username", "session_token", "lock", "user:1:name", "user:1:email", "msg");
    }

    static void stringsCounters() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("STRINGS: Counters (Atomic Operations)");
        System.out.println("=".repeat(60));
        // Interview: INCR is atomic - no race conditions with concurrent clients

        redis.set("views", "0");
        System.out.printf("  INCR: %d%n", redis.incr("views"));
        System.out.printf("  INCRBY +10: %d%n", redis.incrBy("views", 10));
        System.out.printf("  DECR: %d%n", redis.decr("views"));
        System.out.printf("  DECRBY -5: %d%n", redis.decrBy("views", 5));

        redis.set("temp", "20.5");
        System.out.printf("  INCRBYFLOAT +2.3: %.1f%n", redis.incrByFloat("temp", 2.3));

        redis.del("views", "temp");
    }

    static void stringsUseCases() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("STRINGS: Real-World Use Cases");
        System.out.println("=".repeat(60));

        // 1. Session Management - horizontal scaling (any server reads it)
        redis.setex("session:sess_abc", 1800, "{\"user_id\":42,\"role\":\"admin\"}");
        System.out.printf("  Session (30min TTL): %s%n", redis.get("session:sess_abc"));

        // 2. Caching (cache-aside pattern)
        redis.setex("cache:weather:nyc", 300, "{\"temp\":72,\"condition\":\"sunny\"}");
        System.out.printf("  Cache (5min TTL): %s%n", redis.get("cache:weather:nyc"));

        // 3. Distributed Lock with SET NX EX (atomic lock + expiry)
        String lock = redis.set("lock:payment", "p1", SetParams.setParams().nx().ex(30));
        String lock2 = redis.set("lock:payment", "p2", SetParams.setParams().nx().ex(30));
        System.out.printf("  Lock acquired: %s, second attempt: %s%n", lock != null, lock2 != null);

        redis.del("session:sess_abc", "cache:weather:nyc", "lock:payment");
    }

    // ========================================================================
    // 2. REDIS LISTS
    // ========================================================================
    // Interview: O(1) push/pop. Used for queues, feeds, recent items.
    // Companies: Twitter timeline, GitHub activity feed

    static void listsBasicOperations() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("LISTS: Basic Operations");
        System.out.println("=".repeat(60));

        redis.lpush("tasks", "task3", "task2", "task1");
        redis.rpush("tasks", "task4", "task5");
        System.out.printf("  LPUSH+RPUSH: %s%n", redis.lrange("tasks", 0, -1));

        System.out.printf("  LPOP=%s, RPOP=%s%n", redis.lpop("tasks"), redis.rpop("tasks"));
        System.out.printf("  LLEN: %d, LINDEX(0): %s%n", redis.llen("tasks"), redis.lindex("tasks", 0));

        redis.del("tasks");
    }

    static void listsTaskQueue() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("LISTS: Task Queue (Producer-Consumer)");
        System.out.println("=".repeat(60));
        // Interview: RPUSH + BLPOP = reliable queue. BLPOP blocks until available.

        String[] tasks = {"send_email", "process_image", "generate_report"};
        for (String t : tasks) {
            redis.rpush("queue", String.format("{\"task\":\"%s\",\"ts\":%d}", t, System.currentTimeMillis()));
        }
        System.out.printf("  Producer: added %d tasks%n", tasks.length);

        int processed = 0;
        while (redis.llen("queue") > 0) {
            String data = redis.lpop("queue");
            System.out.printf("    Consuming: %s%n", data);
            processed++;
        }
        System.out.printf("  Consumer: processed %d tasks%n", processed);
    }

    static void listsActivityFeed() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("LISTS: Activity Feed (LPUSH + LTRIM)");
        System.out.println("=".repeat(60));
        // Interview: LPUSH + LTRIM keeps fixed-size recent feed

        String key = "feed:user_123";
        for (String a : new String[]{"liked photo", "commented", "shared article"}) {
            redis.lpush(key, String.format("{\"action\":\"%s\"}", a));
            redis.ltrim(key, 0, 49); // Keep 50 max
        }
        System.out.println("  Recent feed:");
        for (String item : redis.lrange(key, 0, 2)) {
            System.out.printf("    %s%n", item);
        }
        redis.del(key);
    }

    // ========================================================================
    // 3. REDIS SETS
    // ========================================================================
    // Interview: O(1) add/remove/membership. Unordered, unique.
    // Companies: Instagram (tags), Stack Overflow (badges)

    static void setsBasicOperations() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SETS: Basic Operations");
        System.out.println("=".repeat(60));

        redis.sadd("colors", "red", "blue", "green", "red"); // dups ignored
        System.out.printf("  SMEMBERS: %s%n", redis.smembers("colors"));
        System.out.printf("  SCARD: %d%n", redis.scard("colors"));
        System.out.printf("  SISMEMBER red=%s, yellow=%s%n",
                redis.sismember("colors", "red"), redis.sismember("colors", "yellow"));
        System.out.printf("  SPOP: %s%n", redis.spop("colors"));

        redis.del("colors");
    }

    static void setsOperations() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SETS: Union / Intersection / Difference");
        System.out.println("=".repeat(60));

        redis.sadd("web", "u1", "u2", "u3");
        redis.sadd("mobile", "u3", "u4", "u5");

        System.out.printf("  SINTER (both):     %s%n", redis.sinter("web", "mobile"));
        System.out.printf("  SUNION (all):      %s%n", redis.sunion("web", "mobile"));
        System.out.printf("  SDIFF (web only):  %s%n", redis.sdiff("web", "mobile"));
        System.out.printf("  SDIFF (mob only):  %s%n", redis.sdiff("mobile", "web"));

        redis.del("web", "mobile");
    }

    static void setsUniqueVisitors() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SETS: Unique Visitor Tracking");
        System.out.println("=".repeat(60));

        String key = "visitors:2024-01-15";
        String[] ips = {"1.2.3.4", "5.6.7.8", "1.2.3.4", "9.0.0.1", "5.6.7.8", "10.0.0.1"};
        for (String ip : ips) redis.sadd(key, ip);
        System.out.printf("  Total hits: %d, Unique: %d%n", ips.length, redis.scard(key));

        redis.del(key);
    }

    static void setsSocialNetwork() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SETS: Social Network");
        System.out.println("=".repeat(60));
        // Interview: SINTER=mutual friends, SDIFF=people you may know

        redis.sadd("alice:followers", "bob", "charlie", "dave");
        redis.sadd("alice:following", "bob", "charlie");

        System.out.printf("  Mutual friends:     %s%n", redis.sinter("alice:followers", "alice:following"));
        System.out.printf("  Not followed back:  %s%n", redis.sdiff("alice:followers", "alice:following"));
        System.out.printf("  Followers: %d, Following: %d%n",
                redis.scard("alice:followers"), redis.scard("alice:following"));

        redis.del("alice:followers", "alice:following");
    }

    // ========================================================================
    // 4. REDIS HASHES
    // ========================================================================
    // Interview: O(1) HGET/HSET. Mini key-value store per key.
    // Memory efficient (ziplist for small hashes).
    // Companies: Instagram (user data), Uber (trip info)

    static void hashesBasicOperations() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("HASHES: Basic Operations");
        System.out.println("=".repeat(60));

        Map<String, String> user = new HashMap<>();
        user.put("name", "John Doe");
        user.put("email", "john@test.com");
        user.put("age", "30");
        redis.hset("user:1000", user);

        System.out.printf("  HGET name: %s%n", redis.hget("user:1000", "name"));
        System.out.printf("  HGETALL: %s%n", redis.hgetAll("user:1000"));
        System.out.printf("  HINCRBY age+1: %d%n", redis.hincrBy("user:1000", "age", 1));
        System.out.printf("  HEXISTS email: %s%n", redis.hexists("user:1000", "email"));
        System.out.printf("  HKEYS: %s%n", redis.hkeys("user:1000"));

        redis.del("user:1000");
    }

    static void hashesShoppingCart() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("HASHES: Shopping Cart");
        System.out.println("=".repeat(60));
        // Interview: fields=product IDs, values=quantities. HINCRBY for qty changes.

        String cart = "cart:user1";
        redis.hset(cart, "prod_101", "2");
        redis.hset(cart, "prod_205", "1");
        redis.hset(cart, "prod_350", "3");
        redis.hincrBy(cart, "prod_101", 1); // +1 quantity
        redis.hdel(cart, "prod_205");        // remove item

        Map<String, String> items = redis.hgetAll(cart);
        int total = items.values().stream().mapToInt(Integer::parseInt).sum();
        System.out.printf("  Cart: %s (total items: %d)%n", items, total);

        redis.del(cart);
    }

    static void hashesFeatureFlags() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("HASHES: Feature Flags");
        System.out.println("=".repeat(60));
        // Interview: All services read flags from Redis. Change = instant propagation.

        Map<String, String> flags = new HashMap<>();
        flags.put("new_ui", "true");
        flags.put("dark_mode", "true");
        flags.put("beta_search", "false");
        redis.hset("flags:app", flags);

        System.out.printf("  new_ui: %s%n", "true".equals(redis.hget("flags:app", "new_ui")));
        redis.hset("flags:app", "beta_search", "true"); // toggle
        System.out.printf("  All flags: %s%n", redis.hgetAll("flags:app"));

        redis.del("flags:app");
    }

    // ========================================================================
    // 5. REDIS SORTED SETS
    // ========================================================================
    // Interview: O(log N) add/remove. Sorted by score.
    // Companies: Reddit (trending), gaming leaderboards, Stack Overflow

    static void sortedSetsBasicOperations() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SORTED SETS: Basic Operations");
        System.out.println("=".repeat(60));

        redis.zadd("scores", 100, "alice");
        redis.zadd("scores", 85, "bob");
        redis.zadd("scores", 92, "charlie");

        System.out.printf("  ZCARD: %d%n", redis.zcard("scores"));
        System.out.printf("  ZSCORE alice: %.0f%n", redis.zscore("scores", "alice"));
        System.out.printf("  ZRANK alice: %d (asc), ZREVRANK: %d (desc)%n",
                redis.zrank("scores", "alice"), redis.zrevrank("scores", "alice"));
        System.out.printf("  ZINCRBY bob+10: %.0f%n", redis.zincrby("scores", 10, "bob"));
        System.out.printf("  ZRANGE (asc): %s%n", redis.zrange("scores", 0, -1));
        System.out.printf("  ZREVRANGE (desc): %s%n", redis.zrevrange("scores", 0, -1));
        System.out.printf("  Score 90-100: %s%n", redis.zrangeByScore("scores", 90, 100));

        redis.del("scores");
    }

    static void sortedSetsLeaderboard() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SORTED SETS: Gaming Leaderboard");
        System.out.println("=".repeat(60));

        String lb = "game:lb";
        redis.zadd(lb, 2500, "alice");
        redis.zadd(lb, 1800, "bob");
        redis.zadd(lb, 3200, "charlie");
        redis.zadd(lb, 2100, "dave");
        redis.zadd(lb, 2800, "eve");

        List<Tuple> top3 = redis.zrevrangeWithScores(lb, 0, 2);
        System.out.println("  Top 3:");
        int rank = 1;
        for (Tuple t : top3)
            System.out.printf("    #%d: %s - %.0f pts%n", rank++, t.getElement(), t.getScore());

        System.out.printf("  alice rank: #%d%n", redis.zrevrank(lb, "alice") + 1);
        redis.zincrby(lb, 500, "bob");
        System.out.printf("  bob after +500: rank #%d%n", redis.zrevrank(lb, "bob") + 1);

        redis.del(lb);
    }

    static void sortedSetsPriorityQueue() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SORTED SETS: Priority Queue");
        System.out.println("=".repeat(60));
        // Interview: Lower score = higher priority. ZPOPMIN gets highest priority.

        String q = "tasks:pq";
        redis.zadd(q, 3, "send_email");
        redis.zadd(q, 1, "process_payment");  // highest priority
        redis.zadd(q, 2, "generate_report");

        System.out.println("  Processing by priority:");
        List<Tuple> all = redis.zpopmin(q, 3);
        for (Tuple t : all)
            System.out.printf("    %s (priority: %.0f)%n", t.getElement(), t.getScore());
    }

    static void sortedSetsRateLimiting() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SORTED SETS: Sliding Window Rate Limiter");
        System.out.println("=".repeat(60));
        // Interview: ZADD timestamp, ZREMRANGEBYSCORE to expire old entries, ZCARD to count

        String key = "rate:user_789";
        int maxReqs = 5;

        System.out.println("  Simulating requests (limit: 5/min):");
        for (int i = 1; i <= 7; i++) {
            double now = System.currentTimeMillis();
            redis.zadd(key, now, "req_" + now + "_" + i);
            redis.zremrangeByScore(key, "-inf", String.valueOf(now - 60000));
            long count = redis.zcard(key);
            redis.expire(key, 60);
            String status = count <= maxReqs ? "ALLOWED" : "BLOCKED";
            System.out.printf("    Request %d: %s (%d/%d)%n", i, status, count, maxReqs);
        }

        redis.del(key);
    }

    // ========================================================================
    // MAIN - Run All Examples
    // ========================================================================
    public static void main(String[] args) {
        try {
            redis = new Jedis("localhost", 6379);
            redis.ping();
            System.out.println("Connected to Redis!\n");

            // STRINGS
            System.out.println("\n>>> REDIS STRINGS <<<");
            stringsBasicOperations();
            stringsCounters();
            stringsUseCases();

            // LISTS
            System.out.println("\n>>> REDIS LISTS <<<");
            listsBasicOperations();
            listsTaskQueue();
            listsActivityFeed();

            // SETS
            System.out.println("\n>>> REDIS SETS <<<");
            setsBasicOperations();
            setsOperations();
            setsUniqueVisitors();
            setsSocialNetwork();

            // HASHES
            System.out.println("\n>>> REDIS HASHES <<<");
            hashesBasicOperations();
            hashesShoppingCart();
            hashesFeatureFlags();

            // SORTED SETS
            System.out.println("\n>>> REDIS SORTED SETS <<<");
            sortedSetsBasicOperations();
            sortedSetsLeaderboard();
            sortedSetsPriorityQueue();
            sortedSetsRateLimiting();

            System.out.println("\n" + "=".repeat(60));
            System.out.println("ALL EXAMPLES COMPLETED!");
            System.out.println("=".repeat(60));

            System.out.println("\n--- Redis Data Structure Cheat Sheet ---");
            System.out.println("  STRING:     Caching, sessions, counters, locks | O(1)");
            System.out.println("  LIST:       Queues, feeds, recent items       | O(1) push/pop");
            System.out.println("  SET:        Unique items, tags, social graph  | O(1) add/check");
            System.out.println("  HASH:       Objects (user, cart, config)      | O(1) per field");
            System.out.println("  SORTED SET: Leaderboards, priority queues     | O(log N)");

            System.out.println("\n--- Interview Key Points ---");
            System.out.println("  Single-threaded: No lock contention, atomic ops");
            System.out.println("  In-memory: ~100K ops/sec, sub-ms latency");
            System.out.println("  Persistence: RDB (snapshots) + AOF (write log)");
            System.out.println("  Replication: Master-replica for reads scaling");
            System.out.println("  Cluster: Sharding across nodes (16384 hash slots)");
            System.out.println("  Eviction: LRU, LFU, TTL-based, no-eviction");
            System.out.println("  Pub/Sub: Real-time messaging between services");

            redis.close();
        } catch (Exception e) {
            System.out.println("\nERROR: Could not connect to Redis!");
            System.out.println("Start Redis: docker run -d -p 6379:6379 redis:latest");
            System.out.println("Error: " + e.getMessage());
        }
    }
}
