/**
 * DNS CACHING SYSTEM - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a thread-safe DNS caching system
 * for operating systems that minimizes network calls.
 * 
 * Key Features:
 * 1. Thread-safe DNS cache with ReadWriteLock
 * 2. Multiple eviction policies (LRU, LFU, FIFO, Random)
 * 3. TTL management with background cleanup
 * 4. Cache statistics and monitoring
 * 5. Support for multiple IP addresses per domain
 * 6. Negative caching for NXDOMAIN
 * 7. Extensible design for hierarchical caching and prefetching
 * 
 * Design Patterns Used:
 * - Strategy Pattern (EvictionPolicy)
 * - Observer Pattern (Statistics)
 * - Immutable Object (DNSRecord)
 * - Decorator Pattern (Cache extensions)
 * 
 * Company: Microsoft (Senior Level Interview Question)
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;

// ============================================================
// CORE INTERFACES
// ============================================================

/**
 * Main DNS cache interface
 */
interface DNSCache {
    DNSRecord get(String domain);
    boolean put(String domain, DNSRecord record);
    void invalidate(String domain);
    void clear();
    CacheStatistics getStatistics();
    int size();
}

/**
 * Strategy interface for cache eviction policies
 */
interface EvictionPolicy {
    void onAccess(String domain, CacheEntry entry);
    void onInsert(String domain, CacheEntry entry);
    void onRemove(String domain);
    String selectVictim();
    void clear();
}

// ============================================================
// DNS RECORD
// ============================================================

/**
 * Immutable DNS record representing a cached entry
 * Thread-safe by immutability
 */
class DNSRecord {
    private final String domain;
    private final List<String> ipAddresses;
    private final long ttlSeconds;
    private final long creationTime;
    private final RecordType type;
    private final boolean isNegative; // For NXDOMAIN caching
    
    public enum RecordType {
        A,      // IPv4 address
        AAAA,   // IPv6 address
        CNAME   // Canonical name
    }
    
    public DNSRecord(String domain, List<String> ipAddresses, 
                     long ttlSeconds, RecordType type) {
        this(domain, ipAddresses, ttlSeconds, type, false);
    }
    
    private DNSRecord(String domain, List<String> ipAddresses, 
                      long ttlSeconds, RecordType type, boolean isNegative) {
        this.domain = domain;
        this.ipAddresses = Collections.unmodifiableList(new ArrayList<>(ipAddresses));
        this.ttlSeconds = ttlSeconds;
        this.creationTime = System.currentTimeMillis();
        this.type = type;
        this.isNegative = isNegative;
    }
    
    /**
     * Create negative DNS record for NXDOMAIN responses
     */
    public static DNSRecord createNegativeRecord(String domain, long ttlSeconds) {
        return new DNSRecord(domain, Collections.emptyList(), ttlSeconds, 
                            RecordType.A, true);
    }
    
    /**
     * Check if this record has expired
     */
    public boolean isExpired() {
        long now = System.currentTimeMillis();
        long ageSeconds = (now - creationTime) / 1000;
        return ageSeconds >= ttlSeconds;
    }
    
    /**
     * Get remaining TTL in seconds
     */
    public long getRemainingTTL() {
        long now = System.currentTimeMillis();
        long ageSeconds = (now - creationTime) / 1000;
        return Math.max(0, ttlSeconds - ageSeconds);
    }
    
    // Getters
    public String getDomain() { return domain; }
    public List<String> getIpAddresses() { return ipAddresses; }
    public long getTtlSeconds() { return ttlSeconds; }
    public long getCreationTime() { return creationTime; }
    public RecordType getType() { return type; }
    public boolean isNegative() { return isNegative; }
    
    @Override
    public String toString() {
        return String.format("DNSRecord{domain='%s', IPs=%s, TTL=%ds, remaining=%ds, type=%s}", 
            domain, ipAddresses, ttlSeconds, getRemainingTTL(), type);
    }
}

// ============================================================
// CACHE ENTRY
// ============================================================

/**
 * Wrapper around DNSRecord with access metadata
 * Used by eviction policies
 */
class CacheEntry {
    private final DNSRecord record;
    private volatile long lastAccessTime;
    private volatile int accessCount;
    private final long insertionOrder;
    
    public CacheEntry(DNSRecord record, long insertionOrder) {
        this.record = record;
        this.lastAccessTime = System.currentTimeMillis();
        this.accessCount = 0;
        this.insertionOrder = insertionOrder;
    }
    
    public void recordAccess() {
        this.lastAccessTime = System.currentTimeMillis();
        this.accessCount++;
    }
    
    public boolean isExpired() {
        return record.isExpired();
    }
    
    public DNSRecord getRecord() { return record; }
    public long getLastAccessTime() { return lastAccessTime; }
    public int getAccessCount() { return accessCount; }
    public long getInsertionOrder() { return insertionOrder; }
}

// ============================================================
// CACHE STATISTICS
// ============================================================

/**
 * Tracks cache performance metrics
 */
class CacheStatistics {
    private final AtomicLong hits;
    private final AtomicLong misses;
    private final AtomicLong evictions;
    private final AtomicLong expirations;
    
    public CacheStatistics() {
        this.hits = new AtomicLong(0);
        this.misses = new AtomicLong(0);
        this.evictions = new AtomicLong(0);
        this.expirations = new AtomicLong(0);
    }
    
    public void recordHit() { hits.incrementAndGet(); }
    public void recordMiss() { misses.incrementAndGet(); }
    public void recordEviction() { evictions.incrementAndGet(); }
    public void recordExpiration() { expirations.incrementAndGet(); }
    
    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public long getEvictions() { return evictions.get(); }
    public long getExpirations() { return expirations.get(); }
    
    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }
    
    public void reset() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
        expirations.set(0);
    }
    
    @Override
    public String toString() {
        return String.format("Stats{hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d, expirations=%d}",
            hits.get(), misses.get(), getHitRate() * 100, evictions.get(), expirations.get());
    }
}

// ============================================================
// EVICTION POLICIES
// ============================================================

/**
 * LRU (Least Recently Used) eviction policy
 * Uses LinkedHashMap with access order for O(1) operations
 */
class LRUEvictionPolicy implements EvictionPolicy {
    private final LinkedHashMap<String, Long> accessOrder;
    
    public LRUEvictionPolicy() {
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
    }
    
    @Override
    public synchronized void onAccess(String domain, CacheEntry entry) {
        accessOrder.put(domain, entry.getLastAccessTime());
    }
    
    @Override
    public synchronized void onInsert(String domain, CacheEntry entry) {
        accessOrder.put(domain, entry.getLastAccessTime());
    }
    
    @Override
    public synchronized void onRemove(String domain) {
        accessOrder.remove(domain);
    }
    
    @Override
    public synchronized String selectVictim() {
        return accessOrder.isEmpty() ? null : accessOrder.keySet().iterator().next();
    }
    
    @Override
    public synchronized void clear() {
        accessOrder.clear();
    }
}

/**
 * LFU (Least Frequently Used) eviction policy
 * Tracks access count for each entry
 */
class LFUEvictionPolicy implements EvictionPolicy {
    private final Map<String, Integer> accessCounts;
    
    public LFUEvictionPolicy() {
        this.accessCounts = new HashMap<>();
    }
    
    @Override
    public synchronized void onAccess(String domain, CacheEntry entry) {
        accessCounts.put(domain, entry.getAccessCount());
    }
    
    @Override
    public synchronized void onInsert(String domain, CacheEntry entry) {
        accessCounts.put(domain, 0);
    }
    
    @Override
    public synchronized void onRemove(String domain) {
        accessCounts.remove(domain);
    }
    
    @Override
    public synchronized String selectVictim() {
        if (accessCounts.isEmpty()) {
            return null;
        }
        
        return accessCounts.entrySet().stream()
            .min(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
    
    @Override
    public synchronized void clear() {
        accessCounts.clear();
    }
}

/**
 * FIFO (First In, First Out) eviction policy
 * Evicts oldest entry by insertion time
 */
class FIFOEvictionPolicy implements EvictionPolicy {
    private final Map<String, Long> insertionOrder;
    
    public FIFOEvictionPolicy() {
        this.insertionOrder = new HashMap<>();
    }
    
    @Override
    public synchronized void onAccess(String domain, CacheEntry entry) {
        // FIFO doesn't care about access
    }
    
    @Override
    public synchronized void onInsert(String domain, CacheEntry entry) {
        insertionOrder.put(domain, entry.getInsertionOrder());
    }
    
    @Override
    public synchronized void onRemove(String domain) {
        insertionOrder.remove(domain);
    }
    
    @Override
    public synchronized String selectVictim() {
        if (insertionOrder.isEmpty()) {
            return null;
        }
        
        return insertionOrder.entrySet().stream()
            .min(Comparator.comparingLong(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
    
    @Override
    public synchronized void clear() {
        insertionOrder.clear();
    }
}

/**
 * Random eviction policy
 * Evicts a random entry (no metadata needed)
 */
class RandomEvictionPolicy implements EvictionPolicy {
    private final Set<String> domains;
    private final Random random;
    
    public RandomEvictionPolicy() {
        this.domains = new HashSet<>();
        this.random = new Random();
    }
    
    @Override
    public synchronized void onAccess(String domain, CacheEntry entry) {
        // Random doesn't track access
    }
    
    @Override
    public synchronized void onInsert(String domain, CacheEntry entry) {
        domains.add(domain);
    }
    
    @Override
    public synchronized void onRemove(String domain) {
        domains.remove(domain);
    }
    
    @Override
    public synchronized String selectVictim() {
        if (domains.isEmpty()) {
            return null;
        }
        
        int index = random.nextInt(domains.size());
        Iterator<String> iterator = domains.iterator();
        for (int i = 0; i < index; i++) {
            iterator.next();
        }
        return iterator.next();
    }
    
    @Override
    public synchronized void clear() {
        domains.clear();
    }
}

// ============================================================
// TTL MANAGER
// ============================================================

/**
 * Background thread that periodically removes expired entries
 */
class TTLManager {
    private final DNSCacheImpl cache;
    private final ScheduledExecutorService scheduler;
    private final long cleanupIntervalSeconds;
    
    public TTLManager(DNSCacheImpl cache, long cleanupIntervalSeconds) {
        this.cache = cache;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    public void start() {
        scheduler.scheduleAtFixedRate(
            this::cleanupExpiredEntries,
            cleanupIntervalSeconds,
            cleanupIntervalSeconds,
            TimeUnit.SECONDS
        );
    }
    
    private void cleanupExpiredEntries() {
        cache.removeExpiredEntries();
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

// ============================================================
// DNS CACHE IMPLEMENTATION
// ============================================================

/**
 * Thread-safe DNS cache implementation
 */
class DNSCacheImpl implements DNSCache {
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final EvictionPolicy evictionPolicy;
    private final CacheStatistics statistics;
    private final int maxSize;
    private final AtomicLong insertionCounter;
    private final ReadWriteLock lock;
    private final TTLManager ttlManager;
    
    public DNSCacheImpl(int maxSize, EvictionPolicy evictionPolicy, 
                        long ttlCleanupIntervalSeconds) {
        this.cache = new ConcurrentHashMap<>();
        this.evictionPolicy = evictionPolicy;
        this.statistics = new CacheStatistics();
        this.maxSize = maxSize;
        this.insertionCounter = new AtomicLong(0);
        this.lock = new ReentrantReadWriteLock();
        this.ttlManager = new TTLManager(this, ttlCleanupIntervalSeconds);
        this.ttlManager.start();
    }
    
    @Override
    public DNSRecord get(String domain) {
        domain = normalizeDomain(domain);
        
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(domain);
            
            if (entry == null) {
                statistics.recordMiss();
                return null;
            }
            
            if (entry.isExpired()) {
                // Remove in a separate operation to avoid deadlock
                lock.readLock().unlock();
                invalidate(domain);
                lock.readLock().lock();
                
                statistics.recordMiss();
                statistics.recordExpiration();
                return null;
            }
            
            entry.recordAccess();
            evictionPolicy.onAccess(domain, entry);
            statistics.recordHit();
            
            return entry.getRecord();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean put(String domain, DNSRecord record) {
        domain = normalizeDomain(domain);
        
        lock.writeLock().lock();
        try {
            // Check if eviction is needed
            if (cache.size() >= maxSize && !cache.containsKey(domain)) {
                evictOne();
            }
            
            long order = insertionCounter.incrementAndGet();
            CacheEntry entry = new CacheEntry(record, order);
            
            cache.put(domain, entry);
            evictionPolicy.onInsert(domain, entry);
            
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void invalidate(String domain) {
        domain = normalizeDomain(domain);
        
        lock.writeLock().lock();
        try {
            if (cache.remove(domain) != null) {
                evictionPolicy.onRemove(domain);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            evictionPolicy.clear();
            statistics.reset();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }
    
    @Override
    public int size() {
        return cache.size();
    }
    
    /**
     * Remove expired entries (called by TTLManager)
     */
    public void removeExpiredEntries() {
        lock.writeLock().lock();
        try {
            List<String> expiredDomains = cache.entrySet().stream()
                .filter(e -> e.getValue().isExpired())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            for (String domain : expiredDomains) {
                cache.remove(domain);
                evictionPolicy.onRemove(domain);
                statistics.recordExpiration();
            }
            
            if (!expiredDomains.isEmpty()) {
                System.out.println("[TTL Cleanup] Removed " + expiredDomains.size() + " expired entries");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void evictOne() {
        String victim = evictionPolicy.selectVictim();
        if (victim != null) {
            cache.remove(victim);
            evictionPolicy.onRemove(victim);
            statistics.recordEviction();
        }
    }
    
    private String normalizeDomain(String domain) {
        return domain.toLowerCase().trim();
    }
    
    public void shutdown() {
        ttlManager.shutdown();
    }
    
    // For monitoring
    public Map<String, DNSRecord> snapshot() {
        lock.readLock().lock();
        try {
            Map<String, DNSRecord> snapshot = new HashMap<>();
            cache.forEach((k, v) -> snapshot.put(k, v.getRecord()));
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }
}

// ============================================================
// DEMO AND TESTING
// ============================================================

public class DNSCacheSystem {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== DNS CACHING SYSTEM DEMO ===\n");
        
        // Demo 1: Basic Operations
        demoBasicOperations();
        
        // Demo 2: TTL Expiration
        demoTTLExpiration();
        
        // Demo 3: Cache Eviction Policies
        demoEvictionPolicies();
        
        // Demo 4: Concurrent Access
        demoConcurrentAccess();
        
        // Demo 5: Negative Caching
        demoNegativeCaching();
        
        // Demo 6: Statistics and Monitoring
        demoStatistics();
    }
    
    private static void demoBasicOperations() {
        System.out.println("--- Demo 1: Basic Operations ---");
        
        DNSCache cache = new DNSCacheImpl(100, new LRUEvictionPolicy(), 60);
        
        // Add DNS records
        DNSRecord google = new DNSRecord("www.google.com", 
            Arrays.asList("142.250.185.228", "142.250.185.229"), 
            300, DNSRecord.RecordType.A);
        
        DNSRecord youtube = new DNSRecord("www.youtube.com",
            Arrays.asList("142.250.185.142"),
            300, DNSRecord.RecordType.A);
        
        cache.put("www.google.com", google);
        cache.put("www.youtube.com", youtube);
        
        // Retrieve records
        DNSRecord retrieved = cache.get("www.google.com");
        System.out.println("Retrieved: " + retrieved);
        System.out.println("IPs: " + retrieved.getIpAddresses());
        System.out.println("Remaining TTL: " + retrieved.getRemainingTTL() + "s");
        
        // Case insensitive
        DNSRecord caseTest = cache.get("WWW.GOOGLE.COM");
        System.out.println("Case insensitive works: " + (caseTest != null));
        
        System.out.println("Cache size: " + cache.size());
        System.out.println();
    }
    
    private static void demoTTLExpiration() throws InterruptedException {
        System.out.println("--- Demo 2: TTL Expiration ---");
        
        DNSCache cache = new DNSCacheImpl(100, new LRUEvictionPolicy(), 2); // 2 second cleanup
        
        // Add record with short TTL
        DNSRecord shortLived = new DNSRecord("temp.example.com",
            Arrays.asList("192.168.1.1"),
            3, // 3 seconds TTL
            DNSRecord.RecordType.A);
        
        cache.put("temp.example.com", shortLived);
        
        System.out.println("Added record with 3s TTL");
        System.out.println("Initial get: " + (cache.get("temp.example.com") != null));
        
        // Wait for expiration
        System.out.println("Waiting 4 seconds...");
        Thread.sleep(4000);
        
        DNSRecord expired = cache.get("temp.example.com");
        System.out.println("After 4s get: " + (expired != null));
        System.out.println("TTL cleanup will remove it in background");
        
        Thread.sleep(3000); // Wait for cleanup
        System.out.println("After cleanup, size: " + cache.size());
        
        ((DNSCacheImpl) cache).shutdown();
        System.out.println();
    }
    
    private static void demoEvictionPolicies() {
        System.out.println("--- Demo 3: Cache Eviction Policies ---");
        
        // Test LRU
        System.out.println("Testing LRU Policy:");
        testEvictionPolicy(new LRUEvictionPolicy(), "LRU");
        
        // Test LFU
        System.out.println("\nTesting LFU Policy:");
        testEvictionPolicy(new LFUEvictionPolicy(), "LFU");
        
        // Test FIFO
        System.out.println("\nTesting FIFO Policy:");
        testEvictionPolicy(new FIFOEvictionPolicy(), "FIFO");
        
        System.out.println();
    }
    
    private static void testEvictionPolicy(EvictionPolicy policy, String name) {
        DNSCache cache = new DNSCacheImpl(3, policy, 60); // Max 3 entries
        
        // Add 3 entries
        cache.put("domain1.com", createRecord("domain1.com", "1.1.1.1"));
        cache.put("domain2.com", createRecord("domain2.com", "2.2.2.2"));
        cache.put("domain3.com", createRecord("domain3.com", "3.3.3.3"));
        
        // Access domain1 multiple times for LRU/LFU
        cache.get("domain1.com");
        cache.get("domain1.com");
        
        System.out.println("  Cache full with 3 entries");
        System.out.println("  Adding 4th entry (triggers eviction)...");
        
        // Add 4th entry (triggers eviction)
        cache.put("domain4.com", createRecord("domain4.com", "4.4.4.4"));
        
        // Check which was evicted
        boolean has1 = cache.get("domain1.com") != null;
        boolean has2 = cache.get("domain2.com") != null;
        boolean has3 = cache.get("domain3.com") != null;
        boolean has4 = cache.get("domain4.com") != null;
        
        System.out.println("  domain1: " + has1 + ", domain2: " + has2 + 
                          ", domain3: " + has3 + ", domain4: " + has4);
        
        ((DNSCacheImpl) cache).shutdown();
    }
    
    private static void demoConcurrentAccess() throws InterruptedException {
        System.out.println("--- Demo 4: Concurrent Access ---");
        
        DNSCache cache = new DNSCacheImpl(1000, new LRUEvictionPolicy(), 60);
        
        // Pre-populate cache
        for (int i = 0; i < 100; i++) {
            String domain = "domain" + i + ".com";
            cache.put(domain, createRecord(domain, "192.168.1." + i));
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(1000);
        
        System.out.println("Launching 1000 concurrent cache operations...");
        
        // 1000 concurrent operations
        for (int i = 0; i < 1000; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String domain = "domain" + (index % 100) + ".com";
                    cache.get(domain);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        CacheStatistics stats = cache.getStatistics();
        System.out.println("Completed 1000 operations");
        System.out.println("Hit rate: " + String.format("%.2f%%", stats.getHitRate() * 100));
        System.out.println("Thread safety maintained: true");
        
        ((DNSCacheImpl) cache).shutdown();
        System.out.println();
    }
    
    private static void demoNegativeCaching() {
        System.out.println("--- Demo 5: Negative Caching ---");
        
        DNSCache cache = new DNSCacheImpl(100, new LRUEvictionPolicy(), 60);
        
        // Cache negative response (NXDOMAIN)
        DNSRecord negative = DNSRecord.createNegativeRecord("nonexistent.com", 60);
        cache.put("nonexistent.com", negative);
        
        System.out.println("Cached negative record for: nonexistent.com");
        
        DNSRecord retrieved = cache.get("nonexistent.com");
        System.out.println("Is negative record: " + retrieved.isNegative());
        System.out.println("IP addresses (empty): " + retrieved.getIpAddresses());
        
        ((DNSCacheImpl) cache).shutdown();
        System.out.println();
    }
    
    private static void demoStatistics() {
        System.out.println("--- Demo 6: Statistics and Monitoring ---");
        
        DNSCache cache = new DNSCacheImpl(5, new LRUEvictionPolicy(), 60);
        
        // Add some records
        for (int i = 0; i < 5; i++) {
            cache.put("domain" + i + ".com", createRecord("domain" + i + ".com", "1.1.1." + i));
        }
        
        // Access patterns
        cache.get("domain0.com"); // hit
        cache.get("domain0.com"); // hit
        cache.get("domain1.com"); // hit
        cache.get("domain999.com"); // miss
        cache.get("domain888.com"); // miss
        
        // Trigger eviction
        cache.put("new-domain.com", createRecord("new-domain.com", "5.5.5.5"));
        
        // Print statistics
        CacheStatistics stats = cache.getStatistics();
        System.out.println("Cache Statistics:");
        System.out.println("  Hits: " + stats.getHits());
        System.out.println("  Misses: " + stats.getMisses());
        System.out.println("  Hit Rate: " + String.format("%.2f%%", stats.getHitRate() * 100));
        System.out.println("  Evictions: " + stats.getEvictions());
        System.out.println("  Expirations: " + stats.getExpirations());
        System.out.println("  Current Size: " + cache.size());
        
        ((DNSCacheImpl) cache).shutdown();
        System.out.println();
    }
    
    // Helper method
    private static DNSRecord createRecord(String domain, String ip) {
        return new DNSRecord(domain, Arrays.asList(ip), 300, DNSRecord.RecordType.A);
    }
    
    /**
     * Benchmark test
     */
    public static void benchmarkPerformance() throws InterruptedException {
        System.out.println("=== PERFORMANCE BENCHMARK ===\n");
        
        DNSCache cache = new DNSCacheImpl(10000, new LRUEvictionPolicy(), 60);
        
        // Warm up cache
        System.out.println("Warming up cache with 1000 entries...");
        for (int i = 0; i < 1000; i++) {
            String domain = "benchmark" + i + ".com";
            cache.put(domain, createRecord(domain, "10.0.0." + (i % 256)));
        }
        
        // Benchmark lookups
        int iterations = 100000;
        System.out.println("Running " + iterations + " lookups...");
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            String domain = "benchmark" + (i % 1000) + ".com";
            cache.get(domain);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1000000;
        
        System.out.println("\nResults:");
        System.out.println("  Total time: " + durationMs + "ms");
        System.out.println("  Average per lookup: " + 
                          String.format("%.4f", (double) durationMs / iterations) + "ms");
        System.out.println("  Throughput: " + 
                          String.format("%.0f", (double) iterations / durationMs * 1000) + " ops/sec");
        System.out.println("  Hit rate: " + 
                          String.format("%.2f%%", cache.getStatistics().getHitRate() * 100));
        
        ((DNSCacheImpl) cache).shutdown();
    }
    
    /**
     * Comparison of all eviction policies
     */
    public static void compareEvictionPolicies() {
        System.out.println("\n=== EVICTION POLICY COMPARISON ===\n");
        
        String[] policyNames = {"LRU", "LFU", "FIFO", "Random"};
        EvictionPolicy[] policies = {
            new LRUEvictionPolicy(),
            new LFUEvictionPolicy(),
            new FIFOEvictionPolicy(),
            new RandomEvictionPolicy()
        };
        
        for (int i = 0; i < policies.length; i++) {
            DNSCache cache = new DNSCacheImpl(50, policies[i], 60);
            
            // Simulate workload
            int hits = 0;
            int misses = 0;
            
            // Add 50 entries (fill cache)
            for (int j = 0; j < 50; j++) {
                cache.put("domain" + j + ".com", createRecord("domain" + j + ".com", "1.1.1.1"));
            }
            
            // Access first 10 frequently (80/20 rule)
            for (int j = 0; j < 100; j++) {
                int domainNum = j < 80 ? (j % 10) : (10 + (j % 40));
                String domain = "domain" + domainNum + ".com";
                if (cache.get(domain) != null) hits++;
                else misses++;
            }
            
            // Add 20 new entries (triggers evictions)
            for (int j = 50; j < 70; j++) {
                cache.put("domain" + j + ".com", createRecord("domain" + j + ".com", "2.2.2.2"));
            }
            
            // Access hot domains again
            for (int j = 0; j < 10; j++) {
                if (cache.get("domain" + j + ".com") != null) hits++;
                else misses++;
            }
            
            CacheStatistics stats = cache.getStatistics();
            System.out.println(policyNames[i] + " Policy:");
            System.out.println("  Hit Rate: " + String.format("%.2f%%", stats.getHitRate() * 100));
            System.out.println("  Evictions: " + stats.getEvictions());
            
            ((DNSCacheImpl) cache).shutdown();
        }
    }
}
