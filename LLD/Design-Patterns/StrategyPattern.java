import java.util.*;

// ==================== STRATEGY PATTERN ====================
// Definition: Defines a family of algorithms, encapsulates each one, and makes them
// interchangeable.
//
// REAL System Design Interview Examples:
// 1. Load Balancing Strategies - Round Robin, Least Connections, IP Hash
// 2. Caching Eviction Policies - LRU, LFU, FIFO
// 3. Data Partitioning Strategies - Hash, Range, Consistent Hashing
// 4. Replication Strategies - Master-Slave, Multi-Master, Quorum
// 5. Rate Limiting Algorithms - Token Bucket, Leaky Bucket, Fixed Window
//
// Interview Use Cases:
// - Design Load Balancer: Different algorithms for traffic distribution
// - Design Cache: Multiple eviction policies
// - Design Distributed Database: Partitioning strategies
// - Design CDN: Content routing strategies
// - Design API Gateway: Rate limiting algorithms

// ==================== EXAMPLE 1: LOAD BALANCING STRATEGIES ====================
// Used in: Nginx, HAProxy, AWS ELB, Kubernetes
// Interview Question: Design a load balancer

interface LoadBalancingStrategy {
    String selectServer(List<Server> servers, String clientId);
    String getStrategyName();
}

class Server {
    private String id;
    private String ip;
    private int activeConnections;
    private boolean healthy;

    public Server(String id, String ip) {
        this.id = id;
        this.ip = ip;
        this.activeConnections = 0;
        this.healthy = true;
    }

    public String getId() { return id; }
    public String getIp() { return ip; }
    public int getActiveConnections() { return activeConnections; }
    public boolean isHealthy() { return healthy; }
    public void incrementConnections() { activeConnections++; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }
}

class RoundRobinStrategy implements LoadBalancingStrategy {
    private int currentIndex = 0;

    @Override
    public String selectServer(List<Server> servers, String clientId) {
        List<Server> healthyServers = new ArrayList<>();
        for (Server server : servers) {
            if (server.isHealthy()) {
                healthyServers.add(server);
            }
        }
        
        if (healthyServers.isEmpty()) {
            return null;
        }
        
        Server selected = healthyServers.get(currentIndex % healthyServers.size());
        currentIndex++;
        
        System.out.println("🔄 Round Robin: Selected " + selected.getId() + " (" + selected.getIp() + ")");
        return selected.getId();
    }

    @Override
    public String getStrategyName() {
        return "Round Robin";
    }
}

class LeastConnectionsStrategy implements LoadBalancingStrategy {
    @Override
    public String selectServer(List<Server> servers, String clientId) {
        Server selected = null;
        int minConnections = Integer.MAX_VALUE;
        
        for (Server server : servers) {
            if (server.isHealthy() && server.getActiveConnections() < minConnections) {
                minConnections = server.getActiveConnections();
                selected = server;
            }
        }
        
        if (selected == null) {
            return null;
        }
        
        System.out.printf("⚖️ Least Connections: Selected %s (%d connections)%n", 
            selected.getId(), selected.getActiveConnections());
        return selected.getId();
    }

    @Override
    public String getStrategyName() {
        return "Least Connections";
    }
}

class IPHashStrategy implements LoadBalancingStrategy {
    @Override
    public String selectServer(List<Server> servers, String clientId) {
        List<Server> healthyServers = new ArrayList<>();
        for (Server server : servers) {
            if (server.isHealthy()) {
                healthyServers.add(server);
            }
        }
        
        if (healthyServers.isEmpty()) {
            return null;
        }
        
        int hash = Math.abs(clientId.hashCode());
        Server selected = healthyServers.get(hash % healthyServers.size());
        
        System.out.printf("🔑 IP Hash: Client %s → %s (sticky session)%n", 
            clientId, selected.getId());
        return selected.getId();
    }

    @Override
    public String getStrategyName() {
        return "IP Hash";
    }
}

class LoadBalancer {
    private LoadBalancingStrategy strategy;
    private List<Server> servers;

    public LoadBalancer(List<Server> servers) {
        this.servers = servers;
    }

    public void setStrategy(LoadBalancingStrategy strategy) {
        this.strategy = strategy;
        System.out.println("\n📊 Load Balancer: Using " + strategy.getStrategyName() + " strategy");
    }

    public String route(String clientId) {
        if (strategy == null) {
            throw new IllegalStateException("Load balancing strategy not set");
        }
        return strategy.selectServer(servers, clientId);
    }
}

// ==================== EXAMPLE 2: CACHE EVICTION POLICIES ====================
// Used in: Redis, Memcached, CDN caching
// Interview Question: Design a caching system

interface EvictionStrategy {
    String evict(Map<String, CacheEntry> cache);
    void recordAccess(String key);
    String getStrategyName();
}

class CacheEntry {
    String key;
    String value;
    long accessTime;
    int accessCount;

    public CacheEntry(String key, String value) {
        this.key = key;
        this.value = value;
        this.accessTime = System.currentTimeMillis();
        this.accessCount = 1;
    }

    public void access() {
        this.accessTime = System.currentTimeMillis();
        this.accessCount++;
    }
}

class LRUEvictionStrategy implements EvictionStrategy {
    @Override
    public String evict(Map<String, CacheEntry> cache) {
        String lruKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (CacheEntry entry : cache.values()) {
            if (entry.accessTime < oldestTime) {
                oldestTime = entry.accessTime;
                lruKey = entry.key;
            }
        }
        
        System.out.println("🗑️ LRU Eviction: Removing " + lruKey + " (least recently used)");
        return lruKey;
    }

    @Override
    public void recordAccess(String key) {
        // LRU tracks access time
    }

    @Override
    public String getStrategyName() {
        return "LRU (Least Recently Used)";
    }
}

class LFUEvictionStrategy implements EvictionStrategy {
    @Override
    public String evict(Map<String, CacheEntry> cache) {
        String lfuKey = null;
        int minCount = Integer.MAX_VALUE;
        
        for (CacheEntry entry : cache.values()) {
            if (entry.accessCount < minCount) {
                minCount = entry.accessCount;
                lfuKey = entry.key;
            }
        }
        
        System.out.println("🗑️ LFU Eviction: Removing " + lfuKey + " (" + minCount + " accesses)");
        return lfuKey;
    }

    @Override
    public void recordAccess(String key) {
        // LFU tracks access count
    }

    @Override
    public String getStrategyName() {
        return "LFU (Least Frequently Used)";
    }
}

class FIFOEvictionStrategy implements EvictionStrategy {
    private Queue<String> insertionOrder = new LinkedList<>();

    @Override
    public String evict(Map<String, CacheEntry> cache) {
        String fifoKey = insertionOrder.poll();
        System.out.println("🗑️ FIFO Eviction: Removing " + fifoKey + " (first in)");
        return fifoKey;
    }

    @Override
    public void recordAccess(String key) {
        if (!insertionOrder.contains(key)) {
            insertionOrder.offer(key);
        }
    }

    @Override
    public String getStrategyName() {
        return "FIFO (First In First Out)";
    }
}

// ==================== EXAMPLE 3: DATA PARTITIONING STRATEGIES ====================
// Used in: Cassandra, MongoDB sharding, Distributed databases
// Interview Question: Design a distributed database

interface PartitioningStrategy {
    int getPartition(String key, int numPartitions);
    String getStrategyName();
}

class HashPartitioningStrategy implements PartitioningStrategy {
    @Override
    public int getPartition(String key, int numPartitions) {
        int partition = Math.abs(key.hashCode() % numPartitions);
        System.out.printf("🔑 Hash Partitioning: Key '%s' → Partition %d%n", key, partition);
        return partition;
    }

    @Override
    public String getStrategyName() {
        return "Hash Partitioning";
    }
}

class RangePartitioningStrategy implements PartitioningStrategy {
    @Override
    public int getPartition(String key, int numPartitions) {
        // Assume keys are numeric IDs
        try {
            long id = Long.parseLong(key);
            int partition = (int) ((id / 1000) % numPartitions);
            System.out.printf("📊 Range Partitioning: Key '%s' → Partition %d (range-based)%n", key, partition);
            return partition;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getStrategyName() {
        return "Range Partitioning";
    }
}

class ConsistentHashingStrategy implements PartitioningStrategy {
    private TreeMap<Integer, Integer> ring = new TreeMap<>();

    public ConsistentHashingStrategy(int numPartitions) {
        // Create virtual nodes on the ring
        for (int i = 0; i < numPartitions; i++) {
            for (int j = 0; j < 3; j++) { // 3 virtual nodes per partition
                int hash = (i + "-vnode-" + j).hashCode();
                ring.put(hash, i);
            }
        }
    }

    @Override
    public int getPartition(String key, int numPartitions) {
        int hash = key.hashCode();
        Map.Entry<Integer, Integer> entry = ring.ceilingEntry(hash);
        
        if (entry == null) {
            entry = ring.firstEntry();
        }
        
        int partition = entry.getValue();
        System.out.printf("🔄 Consistent Hashing: Key '%s' → Partition %d (hash ring)%n", key, partition);
        return partition;
    }

    @Override
    public String getStrategyName() {
        return "Consistent Hashing";
    }
}

// ==================== EXAMPLE 4: RATE LIMITING ALGORITHMS ====================
// Used in: API Gateway, Nginx, Rate limiters
// Interview Question: Design rate limiting for an API

interface RateLimitStrategy {
    boolean allowRequest(String clientId);
    String getAlgorithmName();
}

class TokenBucketStrategy implements RateLimitStrategy {
    private Map<String, Integer> tokens = new HashMap<>();
    private static final int MAX_TOKENS = 10;
    private static final int REFILL_RATE = 1; // per second

    @Override
    public boolean allowRequest(String clientId) {
        int currentTokens = tokens.getOrDefault(clientId, MAX_TOKENS);
        
        if (currentTokens > 0) {
            tokens.put(clientId, currentTokens - 1);
            System.out.printf("✅ Token Bucket: Allowed for %s (%d tokens remaining)%n", 
                clientId, currentTokens - 1);
            return true;
        }
        
        System.out.printf("❌ Token Bucket: Rate limited %s (no tokens)%n", clientId);
        return false;
    }

    @Override
    public String getAlgorithmName() {
        return "Token Bucket";
    }
}

class LeakyBucketStrategy implements RateLimitStrategy {
    private Map<String, Queue<Long>> buckets = new HashMap<>();
    private static final int BUCKET_SIZE = 10;
    private static final long LEAK_RATE_MS = 1000; // 1 request per second

    @Override
    public boolean allowRequest(String clientId) {
        Queue<Long> bucket = buckets.computeIfAbsent(clientId, k -> new LinkedList<>());
        long now = System.currentTimeMillis();
        
        // Remove leaked requests
        while (!bucket.isEmpty() && now - bucket.peek() > LEAK_RATE_MS) {
            bucket.poll();
        }
        
        if (bucket.size() < BUCKET_SIZE) {
            bucket.offer(now);
            System.out.printf("✅ Leaky Bucket: Allowed for %s (%d/%d in bucket)%n", 
                clientId, bucket.size(), BUCKET_SIZE);
            return true;
        }
        
        System.out.printf("❌ Leaky Bucket: Rate limited %s (bucket full)%n", clientId);
        return false;
    }

    @Override
    public String getAlgorithmName() {
        return "Leaky Bucket";
    }
}

class FixedWindowStrategy implements RateLimitStrategy {
    private Map<String, Integer> windowCounts = new HashMap<>();
    private Map<String, Long> windowStarts = new HashMap<>();
    private static final int MAX_REQUESTS = 100;
    private static final long WINDOW_SIZE_MS = 60000; // 1 minute

    @Override
    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();
        long windowStart = windowStarts.getOrDefault(clientId, now);
        
        // Check if window expired
        if (now - windowStart >= WINDOW_SIZE_MS) {
            windowStarts.put(clientId, now);
            windowCounts.put(clientId, 0);
        }
        
        int count = windowCounts.getOrDefault(clientId, 0);
        
        if (count < MAX_REQUESTS) {
            windowCounts.put(clientId, count + 1);
            System.out.printf("✅ Fixed Window: Allowed for %s (%d/%d in window)%n", 
                clientId, count + 1, MAX_REQUESTS);
            return true;
        }
        
        System.out.printf("❌ Fixed Window: Rate limited %s (window exceeded)%n", clientId);
        return false;
    }

    @Override
    public String getAlgorithmName() {
        return "Fixed Window";
    }
}

// ==================== EXAMPLE 5: COMPRESSION STRATEGIES ====================
// Used in: CDN, HTTP servers, File storage
// Interview Question: Design a file storage/transfer system

interface CompressionStrategy {
    byte[] compress(byte[] data);
    byte[] decompress(byte[] data);
    String getAlgorithmName();
    double getCompressionRatio();
}

class GzipCompression implements CompressionStrategy {
    @Override
    public byte[] compress(byte[] data) {
        System.out.printf("📦 Gzip: Compressing %d bytes → %d bytes (%.1f%% reduction)%n", 
            data.length, data.length / 2, 50.0);
        return new byte[data.length / 2]; // Simulated
    }

    @Override
    public byte[] decompress(byte[] data) {
        System.out.println("📂 Gzip: Decompressing " + data.length + " bytes");
        return new byte[data.length * 2]; // Simulated
    }

    @Override
    public String getAlgorithmName() {
        return "Gzip";
    }

    @Override
    public double getCompressionRatio() {
        return 0.5;
    }
}

class BrotliCompression implements CompressionStrategy {
    @Override
    public byte[] compress(byte[] data) {
        System.out.printf("📦 Brotli: Compressing %d bytes → %d bytes (%.1f%% reduction)%n", 
            data.length, data.length / 3, 66.7);
        return new byte[data.length / 3]; // Simulated
    }

    @Override
    public byte[] decompress(byte[] data) {
        System.out.println("📂 Brotli: Decompressing " + data.length + " bytes");
        return new byte[data.length * 3]; // Simulated
    }

    @Override
    public String getAlgorithmName() {
        return "Brotli";
    }

    @Override
    public double getCompressionRatio() {
        return 0.33;
    }
}

class ZstdCompression implements CompressionStrategy {
    @Override
    public byte[] compress(byte[] data) {
        System.out.printf("📦 Zstandard: Compressing %d bytes → %d bytes (%.1f%% reduction)%n", 
            data.length, (int)(data.length * 0.4), 60.0);
        return new byte[(int)(data.length * 0.4)]; // Simulated
    }

    @Override
    public byte[] decompress(byte[] data) {
        System.out.println("📂 Zstandard: Decompressing " + data.length + " bytes");
        return new byte[(int)(data.length * 2.5)]; // Simulated
    }

    @Override
    public String getAlgorithmName() {
        return "Zstandard";
    }

    @Override
    public double getCompressionRatio() {
        return 0.4;
    }
}

// ==================== DEMO ====================

public class StrategyPattern {
    public static void main(String[] args) {
        System.out.println("========== STRATEGY PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: LOAD BALANCING STRATEGIES =====
        System.out.println("===== EXAMPLE 1: LOAD BALANCING (Nginx/HAProxy/AWS ELB) =====");
        
        List<Server> servers = Arrays.asList(
            new Server("server-1", "192.168.1.1"),
            new Server("server-2", "192.168.1.2"),
            new Server("server-3", "192.168.1.3")
        );
        
        // Set different connection counts
        servers.get(0).incrementConnections();
        servers.get(0).incrementConnections();
        servers.get(1).incrementConnections();
        
        LoadBalancer lb = new LoadBalancer(servers);
        
        lb.setStrategy(new RoundRobinStrategy());
        lb.route("client-1");
        lb.route("client-2");
        lb.route("client-3");
        lb.route("client-4");
        
        lb.setStrategy(new LeastConnectionsStrategy());
        lb.route("client-5");
        lb.route("client-6");
        
        lb.setStrategy(new IPHashStrategy());
        lb.route("192.168.100.1");
        lb.route("192.168.100.1"); // Same client, same server
        lb.route("192.168.100.2");

        // ===== EXAMPLE 2: CACHE EVICTION POLICIES =====
        System.out.println("\n\n===== EXAMPLE 2: CACHE EVICTION POLICIES (Redis/Memcached) =====\n");
        
        Map<String, CacheEntry> cache = new HashMap<>();
        cache.put("key1", new CacheEntry("key1", "value1"));
        cache.put("key2", new CacheEntry("key2", "value2"));
        cache.put("key3", new CacheEntry("key3", "value3"));
        
        // Simulate accesses
        cache.get("key1").access();
        cache.get("key1").access();
        cache.get("key2").access();
        
        System.out.println("Cache state:");
        cache.values().forEach(e -> 
            System.out.printf("   %s: %d accesses%n", e.key, e.accessCount));
        
        System.out.println("\nUsing LRU:");
        EvictionStrategy lru = new LRUEvictionStrategy();
        lru.evict(cache);
        
        System.out.println("\nUsing LFU:");
        EvictionStrategy lfu = new LFUEvictionStrategy();
        lfu.evict(cache);
        
        System.out.println("\nUsing FIFO:");
        EvictionStrategy fifo = new FIFOEvictionStrategy();
        fifo.recordAccess("key1");
        fifo.recordAccess("key2");
        fifo.recordAccess("key3");
        fifo.evict(cache);

        // ===== EXAMPLE 3: DATA PARTITIONING =====
        System.out.println("\n\n===== EXAMPLE 3: DATA PARTITIONING (Cassandra/MongoDB) =====\n");
        
        int numPartitions = 4;
        
        System.out.println("Partitioning user data across " + numPartitions + " shards:");
        
        PartitioningStrategy hashStrategy = new HashPartitioningStrategy();
        hashStrategy.getPartition("user-1001", numPartitions);
        hashStrategy.getPartition("user-1002", numPartitions);
        hashStrategy.getPartition("user-1003", numPartitions);
        
        System.out.println();
        
        PartitioningStrategy rangeStrategy = new RangePartitioningStrategy();
        rangeStrategy.getPartition("1001", numPartitions);
        rangeStrategy.getPartition("2500", numPartitions);
        rangeStrategy.getPartition("3800", numPartitions);
        
        System.out.println();
        
        PartitioningStrategy consistentHash = new ConsistentHashingStrategy(numPartitions);
        consistentHash.getPartition("user-1001", numPartitions);
        consistentHash.getPartition("user-1002", numPartitions);

        // ===== EXAMPLE 4: RATE LIMITING ALGORITHMS =====
        System.out.println("\n\n===== EXAMPLE 4: RATE LIMITING (API Gateway) =====\n");
        
        System.out.println("Using Token Bucket:");
        RateLimitStrategy tokenBucket = new TokenBucketStrategy();
        for (int i = 0; i < 12; i++) {
            tokenBucket.allowRequest("client-A");
        }
        
        System.out.println("\nUsing Leaky Bucket:");
        RateLimitStrategy leakyBucket = new LeakyBucketStrategy();
        for (int i = 0; i < 12; i++) {
            leakyBucket.allowRequest("client-B");
        }
        
        System.out.println("\nUsing Fixed Window:");
        RateLimitStrategy fixedWindow = new FixedWindowStrategy();
        fixedWindow.allowRequest("client-C");
        fixedWindow.allowRequest("client-C");

        // ===== EXAMPLE 5: COMPRESSION STRATEGIES =====
        System.out.println("\n\n===== EXAMPLE 5: COMPRESSION STRATEGIES (CDN/Storage) =====\n");
        
        byte[] data = new byte[1000];
        
        CompressionStrategy gzip = new GzipCompression();
        byte[] gzipCompressed = gzip.compress(data);
        
        System.out.println();
        
        CompressionStrategy brotli = new BrotliCompression();
        byte[] brotliCompressed = brotli.compress(data);
        
        System.out.println();
        
        CompressionStrategy zstd = new ZstdCompression();
        byte[] zstdCompressed = zstd.compress(data);

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Load balancers (Nginx, HAProxy, AWS ELB)");
        System.out.println("• Cache eviction (Redis, Memcached)");
        System.out.println("• Database partitioning (Cassandra, MongoDB)");
        System.out.println("• API rate limiting (API Gateway, Kong)");
        System.out.println("• Compression (CDN, HTTP servers, Storage)");
    }
}
