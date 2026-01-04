# DNS Caching System - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [Class Design](#class-design)
5. [Complete Implementation](#complete-implementation)
6. [Cache Eviction Policies](#cache-eviction-policies)
7. [Extensibility](#extensibility)
8. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a DNS Caching System?**

A DNS (Domain Name System) caching system is a component that stores DNS query results to minimize network calls for domain name resolution. When an application needs to resolve a domain name (e.g., www.example.com) to an IP address, the cache is checked first before making an expensive network call to a DNS server.

**Real-World Context:**
- Operating system level DNS cache (Windows DNS Client, macOS mDNSResponder)
- Browser DNS cache (Chrome, Firefox)
- Application-level DNS cache (CDNs, load balancers)
- Deployed on billions of devices worldwide

**Core Challenges:**
- **Performance:** Sub-millisecond lookups for cache hits
- **Memory Efficiency:** Limited memory across diverse devices
- **TTL Management:** Respect DNS record Time-To-Live
- **Thread Safety:** Concurrent access from multiple applications
- **Scalability:** Handle millions of domains on a single device
- **Freshness:** Balance between caching and data staleness

---

## Requirements

### Functional Requirements

1. **Cache Operations**
   - Store DNS records with domain name as key
   - Retrieve DNS records (IP addresses) for domain names
   - Support both IPv4 and IPv6 addresses
   - Update existing cache entries
   - Remove expired or invalid entries

2. **TTL (Time-To-Live) Management**
   - Each DNS record has a TTL (time until expiration)
   - Automatically expire entries when TTL reaches zero
   - Support configurable minimum and maximum TTL values
   - Background thread to clean up expired entries

3. **Cache Eviction**
   - LRU (Least Recently Used) eviction when cache is full
   - Support for multiple eviction policies (LRU, LFU, FIFO)
   - Configurable maximum cache size

4. **Hierarchical Lookup**
   - Check cache before making network calls
   - Fall back to DNS server if cache miss
   - Update cache with fresh DNS responses

5. **Cache Statistics**
   - Track cache hits and misses
   - Monitor cache size and memory usage
   - Report hit rate and performance metrics

### Non-Functional Requirements

1. **Performance**
   - Cache lookups: < 1ms (in-memory)
   - Support 10,000+ queries per second
   - Minimal memory overhead per entry

2. **Memory Efficiency**
   - Bounded memory usage (configurable max size)
   - Efficient storage (no memory leaks)
   - Automatic cleanup of expired entries

3. **Thread Safety**
   - Support concurrent reads from multiple threads
   - Safe concurrent writes
   - No race conditions or deadlocks

4. **Reliability**
   - Handle malformed domains gracefully
   - Recover from errors without crashing
   - No data corruption

5. **Scalability**
   - Support millions of cached entries
   - Work across diverse devices (servers to mobile)
   - Configurable for different use cases

### Out of Scope

- Actual DNS protocol implementation (UDP/TCP)
- DNS server selection and load balancing
- DNSSEC validation
- Persistence to disk (in-memory only)
- Distributed caching across multiple machines
- DNS query parsing and packet creation

---

## Core Entities and Relationships

### Key Entities

1. **DNSCache**
   - Main cache interface
   - Manages all cache operations
   - Handles TTL and eviction
   - Thread-safe coordinator

2. **DNSRecord**
   - Represents a cached DNS entry
   - Contains domain name, IP addresses, TTL
   - Tracks creation time and access time
   - Immutable value object

3. **CacheEntry**
   - Wrapper around DNSRecord
   - Tracks metadata (access count, last access time)
   - Used by eviction policies
   - Mutable state for statistics

4. **EvictionPolicy (Interface)**
   - Strategy for removing entries when cache is full
   - Implementations: LRU, LFU, FIFO, Random
   - Notified on access and insertion

5. **TTLManager**
   - Background thread for cleanup
   - Periodically removes expired entries
   - Configurable cleanup interval

6. **CacheStatistics**
   - Tracks performance metrics
   - Hit/miss ratios
   - Memory usage
   - Query latency

### Entity Relationships

```
┌─────────────────────────────────────────────────┐
│              DNSCache                            │
│  - stores DNSRecords                             │
│  - applies EvictionPolicy                        │
│  - uses TTLManager                               │
│  - tracks CacheStatistics                        │
└───────────────┬─────────────────────────────────┘
                │ contains
                ▼
    ┌───────────────────────────┐
    │      CacheEntry           │
    │  - wraps DNSRecord        │
    │  - tracks metadata        │
    └───────────┬───────────────┘
                │ wraps
                ▼
    ┌───────────────────────────┐
    │       DNSRecord           │
    │  - domain name            │
    │  - IP addresses           │
    │  - TTL, creation time     │
    └───────────────────────────┘

    ┌───────────────────────────┐
    │   EvictionPolicy          │
    │   <<interface>>           │
    └───────────┬───────────────┘
                │ implements
        ┌───────┴────────┬──────────┬─────────┐
        │                │          │         │
    ┌───▼────┐    ┌──────▼───┐  ┌──▼──┐  ┌───▼────┐
    │  LRU   │    │   LFU    │  │FIFO │  │ Random │
    │ Policy │    │  Policy  │  │     │  │ Policy │
    └────────┘    └──────────┘  └─────┘  └────────┘
```

### State Transitions

**Cache Entry Lifecycle:**
```
┌─────────┐   insert()    ┌────────┐   access()   ┌────────┐
│  NONE   │─────────────► │ CACHED │─────────────►│ CACHED │
└─────────┘               └────┬───┘              └────┬───┘
                               │                       │
                               │ TTL expired          │ evicted
                               │ or invalidate        │ (cache full)
                               ▼                       ▼
                          ┌─────────┐           ┌─────────┐
                          │ EXPIRED │           │ EVICTED │
                          └─────────┘           └─────────┘
```

---

## Class Design

### 1. DNSRecord

```java
/**
 * Immutable DNS record representing a cached entry
 */
public class DNSRecord {
    private final String domain;
    private final List<String> ipAddresses; // Can have multiple IPs (A records)
    private final long ttlSeconds;
    private final long creationTime;
    private final RecordType type;
    
    public enum RecordType {
        A,      // IPv4 address
        AAAA,   // IPv6 address
        CNAME   // Canonical name
    }
    
    public DNSRecord(String domain, List<String> ipAddresses, 
                     long ttlSeconds, RecordType type) {
        this.domain = domain;
        this.ipAddresses = Collections.unmodifiableList(new ArrayList<>(ipAddresses));
        this.ttlSeconds = ttlSeconds;
        this.creationTime = System.currentTimeMillis();
        this.type = type;
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
}
```

**Design Decisions:**
- **Immutable:** Thread-safe by design, can be shared safely
- **Multiple IPs:** Supports load balancing and failover
- **Self-contained expiry:** Record knows when it's expired
- **Type support:** Different record types (A, AAAA, CNAME)

### 2. CacheEntry

```java
/**
 * Wrapper around DNSRecord with access metadata
 * Used by eviction policies
 */
public class CacheEntry {
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
    
    /**
     * Record an access to this entry
     */
    public void recordAccess() {
        this.lastAccessTime = System.currentTimeMillis();
        this.accessCount++;
    }
    
    public boolean isExpired() {
        return record.isExpired();
    }
    
    // Getters
    public DNSRecord getRecord() { return record; }
    public long getLastAccessTime() { return lastAccessTime; }
    public int getAccessCount() { return accessCount; }
    public long getInsertionOrder() { return insertionOrder; }
}
```

**Design Decisions:**
- **Mutable metadata:** Tracks access patterns for eviction
- **Volatile fields:** Thread-safe access without locks
- **Insertion order:** Supports FIFO eviction policy
- **Delegation:** Wraps DNSRecord without modifying it

### 3. EvictionPolicy Interface

```java
/**
 * Strategy interface for cache eviction policies
 */
public interface EvictionPolicy {
    /**
     * Called when an entry is accessed
     */
    void onAccess(String domain, CacheEntry entry);
    
    /**
     * Called when a new entry is inserted
     */
    void onInsert(String domain, CacheEntry entry);
    
    /**
     * Called when an entry is removed
     */
    void onRemove(String domain);
    
    /**
     * Select an entry to evict when cache is full
     * @return domain name of entry to evict, or null if no eviction needed
     */
    String selectVictim();
    
    /**
     * Clear all eviction state
     */
    void clear();
}
```

**Design Decisions:**
- **Strategy pattern:** Pluggable eviction policies
- **Event-driven:** Notified of cache operations
- **Simple interface:** Easy to implement new policies
- **Returns domain:** Cache handles actual removal

### 4. DNSCache Interface

```java
/**
 * Main DNS cache interface
 */
public interface DNSCache {
    /**
     * Get DNS record for domain
     * @return DNSRecord if found and not expired, null otherwise
     */
    DNSRecord get(String domain);
    
    /**
     * Put DNS record in cache
     * @return true if inserted, false if rejected
     */
    boolean put(String domain, DNSRecord record);
    
    /**
     * Remove entry from cache
     */
    void invalidate(String domain);
    
    /**
     * Clear all cache entries
     */
    void clear();
    
    /**
     * Get cache statistics
     */
    CacheStatistics getStatistics();
    
    /**
     * Get current cache size
     */
    int size();
}
```

### 5. CacheStatistics

```java
/**
 * Tracks cache performance metrics
 */
public class CacheStatistics {
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
}
```

---

## Cache Eviction Policies

### 1. LRU (Least Recently Used)

**Algorithm:** Evict the entry that hasn't been accessed for the longest time.

**Implementation Strategy:**
- Use LinkedHashMap with access order
- O(1) access, O(1) eviction
- Most commonly used in practice

**Pros:**
- ✅ Good for temporal locality
- ✅ Simple and efficient
- ✅ Predictable behavior

**Cons:**
- ❌ May evict frequently used but not recently accessed items
- ❌ Doesn't consider access frequency

**Best For:**
- General purpose caching
- Web browsing patterns
- Most OS-level DNS caches

### 2. LFU (Least Frequently Used)

**Algorithm:** Evict the entry with the lowest access count.

**Implementation Strategy:**
- Track access count per entry
- Use min-heap or sorted structure
- O(log n) eviction

**Pros:**
- ✅ Keeps frequently accessed items
- ✅ Good for stable access patterns

**Cons:**
- ❌ May keep old entries with high historical count
- ❌ More complex to implement
- ❌ Higher overhead

**Best For:**
- Stable workloads
- Long-running servers
- When access patterns are predictable

### 3. FIFO (First In, First Out)

**Algorithm:** Evict the oldest entry (by insertion time).

**Implementation Strategy:**
- Use queue or linked list
- O(1) access, O(1) eviction
- Very simple

**Pros:**
- ✅ Simplest to implement
- ✅ Fair (no starvation)
- ✅ Minimal overhead

**Cons:**
- ❌ Ignores access patterns
- ❌ May evict frequently used items
- ❌ Poor hit rate in practice

**Best For:**
- Simple scenarios
- When simplicity is more important than hit rate
- Testing and debugging

### 4. Random Eviction

**Algorithm:** Evict a random entry.

**Implementation Strategy:**
- Random selection from cache
- O(1) eviction
- No metadata needed

**Pros:**
- ✅ No overhead for tracking
- ✅ Simple implementation
- ✅ No worst-case scenarios

**Cons:**
- ❌ Unpredictable
- ❌ May evict hot items
- ❌ Lower hit rates

**Best For:**
- Memory-constrained devices
- When metadata overhead is too high
- Uniform access patterns

---

## Complete Implementation

### TTLManager - Background Cleanup

```java
/**
 * Background thread that periodically removes expired entries
 */
public class TTLManager {
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
```

### DNSCacheImpl - Main Implementation

```java
/**
 * Thread-safe DNS cache implementation
 */
public class DNSCacheImpl implements DNSCache {
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
}
```

### LRU Eviction Policy Implementation

```java
/**
 * LRU eviction policy using LinkedHashMap
 */
public class LRUEvictionPolicy implements EvictionPolicy {
    private final LinkedHashMap<String, Long> accessOrder;
    
    public LRUEvictionPolicy() {
        // Access-order LinkedHashMap (true for access order)
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
    }
    
    @Override
    public synchronized void onAccess(String domain, CacheEntry entry) {
        // Update access time
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
        // First entry is least recently used
        return accessOrder.isEmpty() ? null : accessOrder.keySet().iterator().next();
    }
    
    @Override
    public synchronized void clear() {
        accessOrder.clear();
    }
}
```

---

## Extensibility

### 1. Negative Caching

**Problem:** Failed DNS lookups still trigger network calls.

**Solution:** Cache negative responses (NXDOMAIN) with shorter TTL.

```java
public class DNSRecord {
    private final boolean isNegative; // true for NXDOMAIN
    
    public static DNSRecord createNegativeRecord(String domain, long ttlSeconds) {
        return new DNSRecord(domain, Collections.emptyList(), ttlSeconds, 
                            RecordType.A, true);
    }
    
    public boolean isNegative() {
        return isNegative;
    }
}
```

**Benefits:**
- Reduces network calls for non-existent domains
- Protects against DNS amplification attacks
- Faster failure responses

### 2. Hierarchical Caching

**Problem:** Single cache level may not be optimal.

**Solution:** Multi-level cache (L1 → L2 → Network).

```java
public class HierarchicalDNSCache implements DNSCache {
    private final DNSCache l1Cache; // Small, fast
    private final DNSCache l2Cache; // Larger, slower
    
    @Override
    public DNSRecord get(String domain) {
        // Check L1 first
        DNSRecord record = l1Cache.get(domain);
        if (record != null) {
            return record;
        }
        
        // Check L2
        record = l2Cache.get(domain);
        if (record != null) {
            // Promote to L1
            l1Cache.put(domain, record);
            return record;
        }
        
        return null; // Cache miss, caller does network lookup
    }
    
    @Override
    public boolean put(String domain, DNSRecord record) {
        // Write to both levels
        l1Cache.put(domain, record);
        l2Cache.put(domain, record);
        return true;
    }
}
```

**Benefits:**
- Balance between speed and capacity
- Frequently accessed items in fast cache
- Better hit rates overall

### 3. Pre-fetching

**Problem:** Wait for TTL expiry causes cache misses.

**Solution:** Pro-actively refresh entries before expiry.

```java
public class PrefetchingDNSCache extends DNSCacheImpl {
    private final DNSResolver resolver;
    private final ExecutorService prefetchExecutor;
    private final double prefetchThreshold = 0.9; // 90% of TTL
    
    @Override
    public DNSRecord get(String domain) {
        DNSRecord record = super.get(domain);
        
        if (record != null && shouldPrefetch(record)) {
            // Async prefetch in background
            prefetchExecutor.submit(() -> {
                try {
                    DNSRecord fresh = resolver.resolve(domain);
                    put(domain, fresh);
                } catch (Exception e) {
                    // Log error, keep existing cache entry
                }
            });
        }
        
        return record;
    }
    
    private boolean shouldPrefetch(DNSRecord record) {
        double remainingRatio = (double) record.getRemainingTTL() / record.getTtlSeconds();
        return remainingRatio < (1.0 - prefetchThreshold);
    }
}
```

**Benefits:**
- No cache miss for popular domains
- Smoother user experience
- Better resource utilization

### 4. Cache Warming

**Problem:** Cold cache after restart has low hit rate.

**Solution:** Pre-load popular domains on startup.

```java
public class CacheWarmer {
    private final DNSCache cache;
    private final DNSResolver resolver;
    
    public void warmCache(List<String> popularDomains) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (String domain : popularDomains) {
            executor.submit(() -> {
                try {
                    DNSRecord record = resolver.resolve(domain);
                    cache.put(domain, record);
                } catch (Exception e) {
                    // Log and continue
                }
            });
        }
        
        executor.shutdown();
    }
}
```

**Benefits:**
- Better initial hit rate
- Faster application startup experience
- Reduced initial network load

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- Cache size constraints?
- Expected query volume?
- Memory vs hit rate trade-off?
- Single or multi-threaded?
- TTL ranges to support?

**2. Requirements Gathering (3 minutes)**
- Core operations (get, put, invalidate)
- TTL management strategy
- Eviction policy preference
- Performance targets

**3. High-Level Design (5 minutes)**
- Main components (Cache, Record, Policy)
- Data structures (HashMap, LinkedHashMap)
- Thread safety strategy
- TTL cleanup approach

**4. Detailed Implementation (20-25 minutes)**
- Start with DNSRecord (simple value object)
- Implement cache interface
- Add eviction policy
- Demonstrate thread safety
- Show TTL management

**5. Discussion & Trade-offs (5-10 minutes)**
- Eviction policy comparison
- Memory vs hit rate
- Concurrency strategies
- Extensibility points

### Common Interview Questions & Answers

**Q1: "Why use ReadWriteLock instead of synchronized?"**

**A:** ReadWriteLock allows multiple concurrent readers:
- **Performance:** Many DNS lookups can happen simultaneously
- **Contention:** Writes are rare (new domains), reads are frequent
- **Scalability:** Better throughput under read-heavy load
- **Trade-off:** Slightly more complex than synchronized

**Q2: "How do you handle the thundering herd problem?"**

**A:** When many threads request the same expired domain:
```java
private final ConcurrentHashMap<String, CompletableFuture<DNSRecord>> pendingLookups;

public DNSRecord getOrFetch(String domain) {
    DNSRecord cached = get(domain);
    if (cached != null) return cached;
    
    // Only one thread does the lookup
    CompletableFuture<DNSRecord> future = pendingLookups.computeIfAbsent(
        domain,
        k -> CompletableFuture.supplyAsync(() -> resolver.resolve(k))
            .whenComplete((r, e) -> {
                if (r != null) put(k, r);
                pendingLookups.remove(k);
            })
    );
    
    return future.join();
}
```

**Q3: "How would you optimize for mobile devices with limited memory?"**

**A:**
1. **Smaller cache size:** 100-500 entries vs 10,000
2. **Aggressive TTL limits:** Max 5 minutes vs 1 hour
3. **Simple eviction:** FIFO/Random instead of LRU (less metadata)
4. **Negative caching:** Shorter TTL for NXDOMAIN
5. **Memory monitoring:** Clear cache if memory pressure detected

**Q4: "What about persistence across restarts?"**

**A:** Add persistence layer:
```java
public class PersistentDNSCache extends DNSCacheImpl {
    private final CacheSerializer serializer;
    
    public void loadFromDisk() {
        Map<String, DNSRecord> persisted = serializer.deserialize();
        for (Map.Entry<String, DNSRecord> entry : persisted.entrySet()) {
            if (!entry.getValue().isExpired()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }
    
    public void saveToDisk() {
        Map<String, DNSRecord> snapshot = new HashMap<>();
        // Copy non-expired entries
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (!entry.getValue().isExpired()) {
                snapshot.put(entry.getKey(), entry.getValue().getRecord());
            }
        }
        serializer.serialize(snapshot);
    }
}
```

**Trade-offs:**
- **Storage overhead:** Need disk space
- **I/O cost:** Serialization/deserialization time
- **Consistency:** May load stale data if TTL changed
- **Benefit:** Faster startup, better initial hit rate

**Q5: "How do you measure cache effectiveness?"**

**A:** Key metrics to track:
```java
public class CacheMetrics {
    // Hit rate (primary metric)
    double hitRate = hits / (hits + misses);
    
    // Memory efficiency
    double bytesPerEntry = totalMemoryBytes / entryCount;
    
    // Performance
    double avgLookupLatencyMs;
    double p99LookupLatencyMs;
    
    // Churn rate
    double evictionRate = evictions / totalInsertions;
    
    // TTL effectiveness
    double expirationRate = expirations / totalInsertions;
}
```

### Design Patterns Used

1. **Strategy Pattern:** EvictionPolicy implementations
2. **Template Method:** Cache operations with hooks for policies
3. **Observer Pattern:** Statistics tracking
4. **Immutable Object:** DNSRecord (thread-safe)
5. **Decorator Pattern:** HierarchicalDNSCache, PrefetchingDNSCache
6. **Singleton Pattern:** Global DNS cache instance

### Key Trade-offs

**1. Memory vs Hit Rate**
- **More memory:** Higher capacity, better hit rate
- **Less memory:** More evictions, lower hit rate
- **Balance:** Size based on device capabilities

**2. Eviction Policy**
- **LRU:** Best general purpose, moderate overhead
- **LFU:** Best for stable patterns, higher overhead
- **FIFO/Random:** Lowest overhead, worst hit rate

**3. TTL Management**
- **Aggressive cleanup:** Lower memory, more CPU
- **Lazy cleanup:** Higher memory, less CPU
- **Balance:** Background thread with reasonable interval

**4. Thread Safety**
- **ReadWriteLock:** Better read performance, more complex
- **Synchronized:** Simpler, potential bottleneck
- **Lock-free:** Best performance, most complex

### Expected Performance by Level

**Junior Engineer:**
- Basic cache operations (get, put)
- Simple eviction (FIFO)
- Basic thread safety (synchronized)
- TTL concept understanding

**Mid-Level Engineer:**
- Complete implementation with LRU
- ReadWriteLock for concurrency
- TTL management with background thread
- Statistics tracking
- Discussion of trade-offs

**Senior Engineer:**
- Multiple eviction policies
- Advanced concurrency (lock-free where appropriate)
- Extensibility (negative caching, prefetching)
- Production considerations (monitoring, tuning)
- Distributed caching strategy
- Performance optimization techniques

### Code Quality Checklist

✅ **Thread Safety**
- ReadWriteLock for cache operations
- Volatile for metadata
- Atomic operations where needed

✅ **Memory Management**
- Bounded cache size
- Background cleanup
- No memory leaks

✅ **Performance**
- O(1) cache lookups
- Minimal lock contention
- Efficient data structures

✅ **Reliability**
- Handle expired entries
- Graceful error handling
- Domain normalization

✅ **Extensibility**
- Pluggable eviction policies
- Easy to add new features
- Clear interfaces

---

## Summary

This DNS Caching System demonstrates:

1. **High Performance:** Sub-millisecond lookups with efficient data structures
2. **Thread Safety:** ReadWriteLock and proper synchronization
3. **TTL Management:** Background cleanup and automatic expiration
4. **Flexible Eviction:** Strategy pattern for multiple policies
5. **Production Ready:** Statistics, monitoring, and extensibility
6. **Interview Success:** Clear requirements, trade-off analysis, extensible design

**Key Takeaways:**
- **Choose right eviction policy** based on access patterns
- **Thread safety is critical** for shared cache
- **TTL management** balances freshness and network calls
- **Extensibility** through interfaces and patterns
- **Monitoring** is essential for production systems

**Related Concepts:**
- HTTP caching (similar TTL and eviction concepts)
- CDN edge caching
- Database query result caching
- Memcached/Redis distributed caching

---

*Last Updated: January 4, 2026*
*Difficulty Level: Medium*
*Key Focus: Caching, TTL Management, Thread Safety*
*Company: Microsoft (Senior Level)*
*Interview Success Rate: High with proper preparation*
