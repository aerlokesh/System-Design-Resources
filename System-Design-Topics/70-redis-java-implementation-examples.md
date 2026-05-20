# 🔴 Topic 70: Redis Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Redis using **Jedis** and **Lettuce** clients. Every pattern includes production-ready code with connection pooling, error handling, serialization, and the **WHY** behind each design decision. All examples are ready to copy into a Spring Boot or standalone Java service.

---

## 📋 Table of Contents

- [1. Connection Setup & Pooling](#1-connection-setup--pooling)
- [2. Basic CRUD Operations](#2-basic-crud-operations)
- [3. Distributed Lock (Redlock)](#3-distributed-lock-redlock)
- [4. Rate Limiter (Sliding Window)](#4-rate-limiter-sliding-window)
- [5. Session Store](#5-session-store)
- [6. Leaderboard (Sorted Sets)](#6-leaderboard-sorted-sets)
- [7. Pub/Sub Messaging](#7-pubsub-messaging)
- [8. Cache-Aside Pattern](#8-cache-aside-pattern)
- [9. Distributed Counter](#9-distributed-counter)
- [10. Queue (Producer-Consumer)](#10-queue-producer-consumer)
- [11. Bloom Filter](#11-bloom-filter)
- [12. Geo-Spatial Queries](#12-geo-spatial-queries)
- [13. Stream Processing (Redis Streams)](#13-stream-processing-redis-streams)
- [14. Pipeline & Batch Operations](#14-pipeline--batch-operations)
- [15. Lua Scripting for Atomicity](#15-lua-scripting-for-atomicity)
- [16. Circuit Breaker State Store](#16-circuit-breaker-state-store)
- [17. Real-Time Analytics (HyperLogLog)](#17-real-time-analytics-hyperloglog)
- [18. Idempotency Key Store](#18-idempotency-key-store)
- [19. Feature Flags](#19-feature-flags)
- [20. Inventory Hold with TTL](#20-inventory-hold-with-ttl)
- [🏆 Client Selection Guide](#-client-selection-guide)

---

## 1. Connection Setup & Pooling

### Jedis (Synchronous, Simple)

```java
// ====== Jedis Connection Pool — Production Configuration ======
public class JedisConfig {
    
    public static JedisPool createPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);           // Max connections in pool
        poolConfig.setMaxIdle(64);             // Max idle connections
        poolConfig.setMinIdle(16);             // Min idle (pre-warmed)
        poolConfig.setMaxWaitMillis(2000);     // Max wait for connection (2s)
        poolConfig.setTestOnBorrow(true);      // Validate before use
        poolConfig.setTestWhileIdle(true);     // Validate idle connections
        poolConfig.setTimeBetweenEvictionRunsMillis(30_000); // Eviction check every 30s
        
        return new JedisPool(poolConfig, 
            "redis-cluster.prod.internal", // Host
            6379,                          // Port
            2000,                          // Connection timeout (ms)
            "your-redis-password",         // Password
            0,                             // Database index
            true                           // SSL
        );
    }
}

// Usage pattern (always use try-with-resources!)
public class RedisService {
    private final JedisPool pool;
    
    public String get(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        } // Connection automatically returned to pool
    }
}
```

### Lettuce (Async, Reactive, Thread-Safe)

```java
// ====== Lettuce Connection — Production Configuration ======
public class LettuceConfig {
    
    public static RedisClient createClient() {
        RedisURI uri = RedisURI.builder()
            .withHost("redis-cluster.prod.internal")
            .withPort(6379)
            .withPassword("your-redis-password".toCharArray())
            .withSsl(true)
            .withTimeout(Duration.ofSeconds(2))
            .build();
        
        RedisClient client = RedisClient.create(uri);
        
        // Connection pool for Lettuce
        client.setOptions(ClientOptions.builder()
            .autoReconnect(true)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(2)))
            .socketOptions(SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(2))
                .keepAlive(true)
                .build())
            .build());
        
        return client;
    }
    
    // For Spring Boot (recommended)
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("redis-cluster.prod.internal");
        config.setPort(6379);
        config.setPassword("your-redis-password");
        
        LettucePoolingClientConfiguration poolConfig = LettucePoolingClientConfiguration.builder()
            .poolConfig(new GenericObjectPoolConfig<>() {{
                setMaxTotal(128);
                setMaxIdle(64);
                setMinIdle(16);
            }})
            .commandTimeout(Duration.ofSeconds(2))
            .build();
        
        return new LettuceConnectionFactory(config, poolConfig);
    }
}
```

### Spring Boot RedisTemplate

```java
@Configuration
public class RedisTemplateConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        
        // JSON serialization (human-readable, cross-language compatible)
        Jackson2JsonRedisSerializer<Object> jsonSerializer = 
            new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.activateDefaultTyping(om.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL);
        jsonSerializer.setObjectMapper(om);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        
        return template;
    }
}
```

---

## 2. Basic CRUD Operations

```java
@Service
public class RedisCrudService {
    private final StringRedisTemplate redis;
    
    // ====== STRING: Set with TTL ======
    public void setWithTTL(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }
    
    // ====== STRING: Get or compute (cache-aside) ======
    public String getOrCompute(String key, Supplier<String> loader, Duration ttl) {
        String cached = redis.opsForValue().get(key);
        if (cached != null) return cached;
        
        String computed = loader.get();
        redis.opsForValue().set(key, computed, ttl);
        return computed;
    }
    
    // ====== SETNX: Set only if not exists (for locks/dedup) ======
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(
            redis.opsForValue().setIfAbsent(key, value, ttl)
        );
    }
    
    // ====== HASH: Store object fields ======
    public void saveUserProfile(String userId, UserProfile profile) {
        String key = "user:" + userId;
        Map<String, String> fields = Map.of(
            "name", profile.getName(),
            "email", profile.getEmail(),
            "tier", profile.getTier().name(),
            "lastLogin", profile.getLastLogin().toString()
        );
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, Duration.ofHours(24));
    }
    
    public UserProfile getUserProfile(String userId) {
        String key = "user:" + userId;
        Map<Object, Object> fields = redis.opsForHash().entries(key);
        if (fields.isEmpty()) return null;
        
        return new UserProfile(
            (String) fields.get("name"),
            (String) fields.get("email"),
            Tier.valueOf((String) fields.get("tier")),
            Instant.parse((String) fields.get("lastLogin"))
        );
    }
    
    // ====== LIST: Recent activity feed ======
    public void addToFeed(String userId, String activity) {
        String key = "feed:" + userId;
        redis.opsForList().leftPush(key, activity);
        redis.opsForList().trim(key, 0, 99);  // Keep only last 100 items
        redis.expire(key, Duration.ofDays(7));
    }
    
    public List<String> getFeed(String userId, int offset, int count) {
        String key = "feed:" + userId;
        return redis.opsForList().range(key, offset, offset + count - 1);
    }
    
    // ====== SET: Tags, memberships ======
    public void addToSet(String key, String... members) {
        redis.opsForSet().add(key, members);
    }
    
    public Set<String> getSetMembers(String key) {
        return redis.opsForSet().members(key);
    }
    
    public boolean isMember(String key, String member) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key, member));
    }
    
    // ====== Atomic increment ======
    public long increment(String key) {
        return redis.opsForValue().increment(key);
    }
    
    public long incrementBy(String key, long delta) {
        return redis.opsForValue().increment(key, delta);
    }
}
```

---

## 3. Distributed Lock (Redlock)

```java
@Service
public class RedisDistributedLock {
    private final StringRedisTemplate redis;
    
    // ====== ACQUIRE LOCK with SET NX EX (atomic) ======
    public Optional<String> acquireLock(String lockName, Duration ttl) {
        String lockKey = "lock:" + lockName;
        String lockValue = UUID.randomUUID().toString();  // Unique owner ID
        
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, lockValue, ttl);
        
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.of(lockValue);  // Lock acquired — return token
        }
        return Optional.empty();  // Lock held by someone else
    }
    
    // ====== RELEASE LOCK (Lua script — atomic check-and-delete) ======
    private static final String RELEASE_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";
    
    private final RedisScript<Long> releaseScript = 
        new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
    
    public boolean releaseLock(String lockName, String lockValue) {
        String lockKey = "lock:" + lockName;
        Long result = redis.execute(releaseScript, List.of(lockKey), lockValue);
        return result != null && result == 1;
    }
    
    // ====== HIGH-LEVEL USAGE ======
    public <T> T executeWithLock(String lockName, Duration ttl, Supplier<T> action) {
        Optional<String> token = acquireLock(lockName, ttl);
        if (token.isEmpty()) {
            throw new LockAcquisitionException("Could not acquire lock: " + lockName);
        }
        
        try {
            return action.get();
        } finally {
            releaseLock(lockName, token.get());
        }
    }
    
    // ====== WITH RETRY ======
    public <T> T executeWithLockRetry(String lockName, Duration ttl, 
                                       int maxRetries, Supplier<T> action) {
        for (int i = 0; i < maxRetries; i++) {
            Optional<String> token = acquireLock(lockName, ttl);
            if (token.isPresent()) {
                try {
                    return action.get();
                } finally {
                    releaseLock(lockName, token.get());
                }
            }
            try { Thread.sleep(50 + (long)(Math.random() * 50)); }  // Jitter
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        throw new LockAcquisitionException("Failed after " + maxRetries + " retries");
    }
}
```

---

## 4. Rate Limiter (Sliding Window)

```java
@Service
public class RedisRateLimiter {
    private final StringRedisTemplate redis;
    
    // ====== SLIDING WINDOW RATE LIMITER (Lua for atomicity) ======
    private static final String RATE_LIMIT_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        
        -- Remove entries outside the window
        redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
        
        -- Count current entries
        local count = redis.call('ZCARD', key)
        
        if count < limit then
            -- Add current request
            redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
            redis.call('EXPIRE', key, window / 1000)
            return 1  -- Allowed
        else
            return 0  -- Rate limited
        end
        """;
    
    private final RedisScript<Long> rateLimitScript = 
        new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
    
    public RateLimitResult isAllowed(String identifier, int maxRequests, Duration window) {
        String key = "ratelimit:" + identifier;
        long nowMs = System.currentTimeMillis();
        
        Long result = redis.execute(rateLimitScript,
            List.of(key),
            String.valueOf(window.toMillis()),
            String.valueOf(maxRequests),
            String.valueOf(nowMs)
        );
        
        boolean allowed = result != null && result == 1;
        
        if (!allowed) {
            Metrics.counter("ratelimit.rejected", "identifier", identifier).increment();
        }
        
        return new RateLimitResult(allowed, maxRequests, getRemainingRequests(key));
    }
    
    // ====== SIMPLE FIXED WINDOW (faster, less precise) ======
    public boolean isAllowedFixedWindow(String identifier, int maxRequests, Duration window) {
        String key = "ratelimit:fixed:" + identifier + ":" + 
            (System.currentTimeMillis() / window.toMillis());
        
        Long count = redis.opsForValue().increment(key);
        if (count == 1) {
            redis.expire(key, window);  // Set TTL on first request
        }
        
        return count <= maxRequests;
    }
}
```

---

## 5. Session Store

```java
@Service
public class RedisSessionStore {
    private final RedisTemplate<String, Object> redis;
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    
    public String createSession(String userId, Map<String, Object> attributes) {
        String sessionId = UUID.randomUUID().toString();
        String key = "session:" + sessionId;
        
        Map<String, Object> sessionData = new HashMap<>(attributes);
        sessionData.put("userId", userId);
        sessionData.put("createdAt", Instant.now().toString());
        sessionData.put("lastAccess", Instant.now().toString());
        
        redis.opsForHash().putAll(key, sessionData);
        redis.expire(key, SESSION_TTL);
        
        // Index: userId → sessionIds (for "logout all devices")
        redis.opsForSet().add("user:sessions:" + userId, sessionId);
        
        return sessionId;
    }
    
    public Map<Object, Object> getSession(String sessionId) {
        String key = "session:" + sessionId;
        Map<Object, Object> session = redis.opsForHash().entries(key);
        
        if (!session.isEmpty()) {
            // Touch: update lastAccess and reset TTL (sliding expiration)
            redis.opsForHash().put(key, "lastAccess", Instant.now().toString());
            redis.expire(key, SESSION_TTL);
        }
        
        return session;
    }
    
    public void invalidateSession(String sessionId) {
        String key = "session:" + sessionId;
        Object userId = redis.opsForHash().get(key, "userId");
        redis.delete(key);
        
        if (userId != null) {
            redis.opsForSet().remove("user:sessions:" + userId, sessionId);
        }
    }
    
    public void invalidateAllUserSessions(String userId) {
        Set<Object> sessionIds = redis.opsForSet().members("user:sessions:" + userId);
        if (sessionIds != null) {
            List<String> keys = sessionIds.stream()
                .map(id -> "session:" + id)
                .collect(Collectors.toList());
            redis.delete(keys);
        }
        redis.delete("user:sessions:" + userId);
    }
}
```

---

## 6. Leaderboard (Sorted Sets)

```java
@Service
public class RedisLeaderboardService {
    private final StringRedisTemplate redis;
    
    public void addScore(String leaderboardId, String playerId, double score) {
        String key = "leaderboard:" + leaderboardId;
        redis.opsForZSet().add(key, playerId, score);
    }
    
    public void incrementScore(String leaderboardId, String playerId, double increment) {
        String key = "leaderboard:" + leaderboardId;
        redis.opsForZSet().incrementScore(key, playerId, increment);
    }
    
    // Top N players (descending score)
    public List<LeaderboardEntry> getTopN(String leaderboardId, int n) {
        String key = "leaderboard:" + leaderboardId;
        Set<ZSetOperations.TypedTuple<String>> results = 
            redis.opsForZSet().reverseRangeWithScores(key, 0, n - 1);
        
        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (var tuple : results) {
            entries.add(new LeaderboardEntry(rank++, tuple.getValue(), tuple.getScore()));
        }
        return entries;
    }
    
    // Get player's rank and score
    public LeaderboardEntry getPlayerRank(String leaderboardId, String playerId) {
        String key = "leaderboard:" + leaderboardId;
        Long rank = redis.opsForZSet().reverseRank(key, playerId);
        Double score = redis.opsForZSet().score(key, playerId);
        
        if (rank == null || score == null) return null;
        return new LeaderboardEntry(rank.intValue() + 1, playerId, score);
    }
    
    // Players around a given player (context)
    public List<LeaderboardEntry> getAroundPlayer(String leaderboardId, 
                                                   String playerId, int range) {
        String key = "leaderboard:" + leaderboardId;
        Long rank = redis.opsForZSet().reverseRank(key, playerId);
        if (rank == null) return List.of();
        
        long start = Math.max(0, rank - range);
        long end = rank + range;
        
        Set<ZSetOperations.TypedTuple<String>> results = 
            redis.opsForZSet().reverseRangeWithScores(key, start, end);
        
        List<LeaderboardEntry> entries = new ArrayList<>();
        int r = (int) start + 1;
        for (var tuple : results) {
            entries.add(new LeaderboardEntry(r++, tuple.getValue(), tuple.getScore()));
        }
        return entries;
    }
}
```

---

## 7. Pub/Sub Messaging

```java
// ====== PUBLISHER ======
@Service
public class RedisEventPublisher {
    private final StringRedisTemplate redis;
    
    public void publishEvent(String channel, Object event) {
        String payload = objectMapper.writeValueAsString(event);
        redis.convertAndSend(channel, payload);
    }
    
    public void publishOrderEvent(OrderEvent event) {
        publishEvent("orders:" + event.getStatus().name().toLowerCase(), event);
    }
}

// ====== SUBSCRIBER ======
@Configuration
public class RedisSubscriberConfig {
    
    @Bean
    public RedisMessageListenerContainer messageListenerContainer(
            LettuceConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        
        // Subscribe to specific channels
        container.addMessageListener(orderCreatedListener(), 
            new ChannelTopic("orders:created"));
        container.addMessageListener(orderCompletedListener(),
            new ChannelTopic("orders:completed"));
        
        // Pattern subscription (all order events)
        container.addMessageListener(allOrdersListener(),
            new PatternTopic("orders:*"));
        
        return container;
    }
    
    @Bean
    public MessageListener orderCreatedListener() {
        return (message, pattern) -> {
            String payload = new String(message.getBody());
            OrderEvent event = objectMapper.readValue(payload, OrderEvent.class);
            log.info("Order created: {}", event.getOrderId());
            orderProcessor.handleCreated(event);
        };
    }
}
```

---

## 8. Cache-Aside Pattern

```java
@Service
public class RedisCacheAside {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    
    // ====== Generic cache-aside with stampede prevention ======
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        // Step 1: Try cache
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return mapper.readValue(cached, type);
        }
        
        // Step 2: Cache miss — acquire lock to prevent stampede
        String lockKey = "lock:cache:" + key;
        boolean gotLock = Boolean.TRUE.equals(
            redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10)));
        
        if (gotLock) {
            try {
                // Double-check after acquiring lock
                cached = redis.opsForValue().get(key);
                if (cached != null) return mapper.readValue(cached, type);
                
                // Load from source
                T value = loader.get();
                if (value != null) {
                    // Add jitter to TTL (prevent thundering herd on expiry)
                    Duration jitteredTtl = ttl.plus(Duration.ofSeconds(
                        ThreadLocalRandom.current().nextLong(0, ttl.toSeconds() / 10)));
                    redis.opsForValue().set(key, mapper.writeValueAsString(value), jitteredTtl);
                }
                return value;
            } finally {
                redis.delete(lockKey);
            }
        } else {
            // Another thread is loading — wait and retry
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            cached = redis.opsForValue().get(key);
            return cached != null ? mapper.readValue(cached, type) : loader.get();
        }
    }
    
    // ====== Write-through: update cache on write ======
    public <T> void writeThrough(String key, T value, Duration ttl) {
        redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
    }
    
    // ====== Invalidate on write ======
    public void invalidate(String... keys) {
        redis.delete(List.of(keys));
    }
    
    // ====== Invalidate by pattern ======
    public void invalidateByPattern(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
```

---

## 9. Distributed Counter

```java
@Service
public class RedisDistributedCounter {
    private final StringRedisTemplate redis;
    
    // ====== Simple atomic counter ======
    public long increment(String counterName) {
        return redis.opsForValue().increment("counter:" + counterName);
    }
    
    // ====== Counter with daily reset ======
    public long incrementDaily(String counterName) {
        String key = "counter:daily:" + counterName + ":" + LocalDate.now();
        Long count = redis.opsForValue().increment(key);
        if (count == 1) {
            redis.expire(key, Duration.ofDays(2));  // Auto-cleanup
        }
        return count;
    }
    
    // ====== Sharded counter (for extreme throughput) ======
    private static final int SHARD_COUNT = 16;
    
    public void incrementSharded(String counterName) {
        int shard = ThreadLocalRandom.current().nextInt(SHARD_COUNT);
        redis.opsForValue().increment("counter:" + counterName + ":shard:" + shard);
    }
    
    public long getShardedCount(String counterName) {
        long total = 0;
        for (int i = 0; i < SHARD_COUNT; i++) {
            String val = redis.opsForValue().get("counter:" + counterName + ":shard:" + i);
            if (val != null) total += Long.parseLong(val);
        }
        return total;
    }
}
```

---

## 10. Queue (Producer-Consumer)

```java
@Service
public class RedisQueueService {
    private final StringRedisTemplate redis;
    
    // ====== PUSH to queue (producer) ======
    public void enqueue(String queueName, String message) {
        redis.opsForList().leftPush("queue:" + queueName, message);
    }
    
    // ====== POP from queue (consumer — blocking) ======
    public String dequeue(String queueName, Duration timeout) {
        return redis.opsForList().rightPop("queue:" + queueName, timeout);
    }
    
    // ====== Reliable queue with processing list (no message loss) ======
    public String dequeueReliable(String queueName) {
        // RPOPLPUSH: atomically move from queue to processing list
        return redis.opsForList().rightPopAndLeftPush(
            "queue:" + queueName, 
            "queue:" + queueName + ":processing"
        );
    }
    
    public void acknowledgeProcessed(String queueName, String message) {
        redis.opsForList().remove("queue:" + queueName + ":processing", 1, message);
    }
    
    // ====== Priority queue using sorted set ======
    public void enqueuePriority(String queueName, String message, double priority) {
        redis.opsForZSet().add("pqueue:" + queueName, message, priority);
    }
    
    public String dequeuePriority(String queueName) {
        Set<String> items = redis.opsForZSet().range("pqueue:" + queueName, 0, 0);
        if (items != null && !items.isEmpty()) {
            String item = items.iterator().next();
            redis.opsForZSet().remove("pqueue:" + queueName, item);
            return item;
        }
        return null;
    }
}
```

---

## 11. Bloom Filter

```java
@Service
public class RedisBloomFilter {
    private final StringRedisTemplate redis;
    
    // Using Redis Bitfield for Bloom filter implementation
    // Production: use RedisBloom module (BF.ADD, BF.EXISTS)
    
    public void add(String filterName, String element) {
        String key = "bloom:" + filterName;
        int[] hashes = getHashes(element, 3);  // 3 hash functions
        
        for (int hash : hashes) {
            redis.opsForValue().setBit(key, Math.abs(hash % 10_000_000), true);
        }
    }
    
    public boolean mightContain(String filterName, String element) {
        String key = "bloom:" + filterName;
        int[] hashes = getHashes(element, 3);
        
        for (int hash : hashes) {
            Boolean bit = redis.opsForValue().getBit(key, Math.abs(hash % 10_000_000));
            if (!Boolean.TRUE.equals(bit)) return false;  // Definitely NOT in set
        }
        return true;  // MIGHT be in set (false positive possible)
    }
    
    private int[] getHashes(String element, int count) {
        int[] hashes = new int[count];
        for (int i = 0; i < count; i++) {
            hashes[i] = (element + "::" + i).hashCode();
        }
        return hashes;
    }
}
```

---

## 12. Geo-Spatial Queries

```java
@Service
public class RedisGeoService {
    private final StringRedisTemplate redis;
    
    // ====== Add location ======
    public void addLocation(String type, String id, double lat, double lng) {
        redis.opsForGeo().add("geo:" + type, new Point(lng, lat), id);
    }
    
    // ====== Find nearby (radius search) ======
    public List<GeoResult<String>> findNearby(String type, double lat, double lng, 
                                               double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = 
            redis.opsForGeo().radius("geo:" + type,
                new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                    .includeDistance()
                    .includeCoordinates()
                    .sortAscending()
                    .limit(20)
            );
        
        return results.getContent().stream()
            .map(r -> new GeoResult<>(r.getContent().getName(), r.getDistance().getValue()))
            .collect(Collectors.toList());
    }
    
    // ====== Distance between two points ======
    public Double getDistance(String type, String id1, String id2) {
        Distance distance = redis.opsForGeo().distance("geo:" + type, id1, id2, Metrics.KILOMETERS);
        return distance != null ? distance.getValue() : null;
    }
}
```

---

## 13. Stream Processing (Redis Streams)

```java
@Service
public class RedisStreamService {
    private final StringRedisTemplate redis;
    
    // ====== PUBLISH to stream ======
    public String publishEvent(String streamName, Map<String, String> eventData) {
        RecordId recordId = redis.opsForStream().add(
            StreamRecords.mapBacked(eventData).withStreamKey("stream:" + streamName)
        );
        return recordId.getValue();
    }
    
    // ====== CONSUME from stream (consumer group) ======
    public void createConsumerGroup(String streamName, String groupName) {
        try {
            redis.opsForStream().createGroup("stream:" + streamName, groupName);
        } catch (Exception e) {
            // Group already exists — OK
        }
    }
    
    public List<MapRecord<String, Object, Object>> readFromGroup(
            String streamName, String groupName, String consumerName, int count) {
        return redis.opsForStream().read(
            Consumer.from(groupName, consumerName),
            StreamReadOptions.empty().count(count).block(Duration.ofSeconds(2)),
            StreamOffset.create("stream:" + streamName, ReadOffset.lastConsumed())
        );
    }
    
    // ====== ACK processed message ======
    public void acknowledge(String streamName, String groupName, String... recordIds) {
        redis.opsForStream().acknowledge("stream:" + streamName, groupName, recordIds);
    }
}
```

---

## 14. Pipeline & Batch Operations

```java
@Service
public class RedisPipelineService {
    private final StringRedisTemplate redis;
    
    // ====== Pipeline: execute multiple commands in one round-trip ======
    public Map<String, String> multiGet(List<String> keys) {
        List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.stringCommands().get(key.getBytes());
            }
            return null;
        });
        
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            if (results.get(i) != null) {
                map.put(keys.get(i), (String) results.get(i));
            }
        }
        return map;
    }
    
    // ====== Batch write with pipeline ======
    public void batchSet(Map<String, String> keyValues, Duration ttl) {
        redis.executePipelined((RedisCallback<Object>) connection -> {
            for (var entry : keyValues.entrySet()) {
                connection.stringCommands().setEx(
                    entry.getKey().getBytes(),
                    ttl.getSeconds(),
                    entry.getValue().getBytes()
                );
            }
            return null;
        });
    }
}
```

---

## 15. Lua Scripting for Atomicity

```java
@Service
public class RedisLuaService {
    private final StringRedisTemplate redis;
    
    // ====== Token Bucket Rate Limiter (atomic Lua) ======
    private static final String TOKEN_BUCKET_SCRIPT = """
        local key = KEYS[1]
        local max_tokens = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local requested = tonumber(ARGV[4])
        
        local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
        local tokens = tonumber(bucket[1]) or max_tokens
        local last_refill = tonumber(bucket[2]) or now
        
        local elapsed = now - last_refill
        local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate / 1000)
        
        if new_tokens >= requested then
            new_tokens = new_tokens - requested
            redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
            redis.call('EXPIRE', key, max_tokens / refill_rate * 2000)
            return 1
        else
            redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
            return 0
        end
        """;
    
    private final RedisScript<Long> tokenBucketScript = 
        new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);
    
    public boolean consumeToken(String bucketName, long maxTokens, long refillRatePerSec) {
        Long result = redis.execute(tokenBucketScript,
            List.of("bucket:" + bucketName),
            String.valueOf(maxTokens),
            String.valueOf(refillRatePerSec),
            String.valueOf(System.currentTimeMillis()),
            "1"
        );
        return result != null && result == 1;
    }
}
```

---

## 16-20: Additional Patterns (Summary)

### 16. Circuit Breaker State Store
```java
public void recordFailure(String service) {
    String key = "circuit:" + service + ":failures";
    redis.opsForValue().increment(key);
    redis.expire(key, Duration.ofSeconds(60));  // Window
}

public boolean isCircuitOpen(String service) {
    String val = redis.opsForValue().get("circuit:" + service + ":failures");
    return val != null && Long.parseLong(val) > 5;  // Threshold
}
```

### 17. HyperLogLog (Unique Visitors)
```java
public void recordVisitor(String pageId, String visitorId) {
    redis.opsForHyperLogLog().add("visitors:" + pageId, visitorId);
}

public long getUniqueVisitors(String pageId) {
    return redis.opsForHyperLogLog().size("visitors:" + pageId);
}
```

### 18. Idempotency Key
```java
public boolean isProcessed(String idempotencyKey) {
    return Boolean.TRUE.equals(redis.hasKey("idempotent:" + idempotencyKey));
}

public void markProcessed(String idempotencyKey, String result) {
    redis.opsForValue().set("idempotent:" + idempotencyKey, result, Duration.ofHours(24));
}
```

### 19. Feature Flags
```java
public boolean isFeatureEnabled(String feature, String userId) {
    // Check if user is in rollout percentage
    return Boolean.TRUE.equals(redis.opsForSet().isMember("feature:" + feature, userId)) ||
           Boolean.TRUE.equals(redis.opsForValue().getBit("feature:rollout:" + feature, 
               Math.abs(userId.hashCode() % 100)));
}
```

### 20. Inventory Hold with TTL
```java
public boolean holdInventory(String productId, String cartId, int qty) {
    String key = "hold:" + productId + ":" + cartId;
    Boolean set = redis.opsForValue().setIfAbsent(key, String.valueOf(qty), Duration.ofMinutes(15));
    if (Boolean.TRUE.equals(set)) {
        redis.opsForValue().decrement("stock:" + productId, qty);
        return true;
    }
    return false;
}
```

---

## 🏆 Client Selection Guide

| Feature | Jedis | Lettuce | Spring Data Redis |
|---|---|---|---|
| Thread safety | Pool-based (1 conn per thread) | Single shared connection | Abstracts both |
| Async support | ❌ No | ✅ Native | ✅ Via Lettuce |
| Reactive | ❌ No | ✅ Reactive Streams | ✅ ReactiveRedisTemplate |
| Cluster support | ✅ Basic | ✅ Advanced | ✅ Both |
| Connection pooling | ✅ JedisPool | ✅ Netty-based | ✅ Configurable |
| Performance | Good for simple | Better under load | Depends on backend |
| Use when | Simple scripts, legacy | Production microservices | Spring Boot apps |

---

*All examples use Spring Data Redis (backed by Lettuce) unless otherwise noted. For raw Jedis/Lettuce usage, adjust the connection setup accordingly.*
