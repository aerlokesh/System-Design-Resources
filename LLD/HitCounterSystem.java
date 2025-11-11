import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

/**
 * HIT COUNTER SYSTEM - LOW LEVEL DESIGN
 * Design a counter that tracks webpage visits/hits in a time window
 * 
 * Requirements:
 * 1. Count hits in last N seconds (e.g., last 5 minutes)
 * 2. Thread-safe for concurrent access
 * 3. Memory efficient
 * 4. Fast read/write operations (low latency)
 * 5. Support multiple time windows
 * 
 * This implementation demonstrates:
 * - THREE different algorithms with trade-offs
 * - Strategy Pattern for swappable implementations
 * - Concurrent data structures
 * - Real-time analytics
 * - Performance comparisons
 * 
 * Common Interview Question: Design hit counter like Google Analytics
 */

// ==================== Enums ====================

enum TimeWindow {
    LAST_SECOND(1),
    LAST_MINUTE(60),
    LAST_5_MINUTES(300),
    LAST_HOUR(3600),
    LAST_DAY(86400);
    
    private final int seconds;
    
    TimeWindow(int seconds) { this.seconds = seconds; }
    public int getSeconds() { return seconds; }
}

enum CounterType {
    SLIDING_WINDOW,   // Most efficient for fixed windows
    FIXED_WINDOW,     // Simplest but has boundary issues
    SLIDING_LOG       // Most accurate but memory intensive
}

// ==================== Hit Counter Interface ====================

/**
 * Base interface for all counter implementations
 * 
 * Why interface?
 * - Strategy Pattern: Swap implementations easily
 * - Testing: Mock implementations for tests
 * - Extensibility: Add new counter types
 * - Polymorphism: Code to interface, not implementation
 */
interface HitCounter {
    void hit(long timestamp);
    long getHits(long timestamp, int windowSize);
    void reset();
    CounterType getType();
}

// ==================== Implementation 1: Sliding Window Counter ====================

/**
 * Sliding Window Counter using circular buffer
 * 
 * Algorithm:
 * 1. Use array indexed by timestamp % windowSize
 * 2. On hit: increment counter at index
 * 3. On read: sum all counters in window
 * 
 * Time Complexity:
 * - hit(): O(1) - direct array access
 * - getHits(): O(windowSize) - scan array
 * 
 * Space Complexity: O(windowSize)
 * 
 * Pros:
 * ✓ Fixed memory (doesn't grow with traffic)
 * ✓ Fast writes (O(1))
 * ✓ Predictable performance
 * ✓ No cleanup needed
 * 
 * Cons:
 * ✗ Reads are O(windowSize) - slower for large windows
 * ✗ Less accurate for variable traffic
 * ✗ Requires knowing window size upfront
 * 
 * When to Use:
 * - Fixed window size known
 * - High write volume
 * - Memory constraints
 * - Predictable traffic patterns
 * 
 * Real Example:
 * Google Analytics: "Hits in last 60 seconds"
 * Array[60], each slot = 1 second
 * Write: O(1), Read: O(60) = acceptable
 * 
 * Problem Avoided: Memory Exhaustion
 * Unlike storing every hit, fixed array size prevents memory growth
 */
class SlidingWindowCounter implements HitCounter {
    private final int windowSize;
    private final long[] timestamps;
    private final AtomicLong[] counts;
    private final Object lock = new Object();
    
    public SlidingWindowCounter(int windowSize) {
        this.windowSize = windowSize;
        this.timestamps = new long[windowSize];
        this.counts = new AtomicLong[windowSize];
        for (int i = 0; i < windowSize; i++) {
            counts[i] = new AtomicLong(0);
        }
    }
    
    @Override
    public void hit(long timestamp) {
        synchronized (lock) {
            int index = (int) (timestamp % windowSize);
            
            // If this timestamp slot is old, reset it
            if (timestamps[index] != timestamp) {
                timestamps[index] = timestamp;
                counts[index].set(1);
            } else {
                counts[index].incrementAndGet();
            }
        }
    }
    
    @Override
    public long getHits(long timestamp, int windowSize) {
        synchronized (lock) {
            long total = 0;
            long cutoffTime = timestamp - windowSize;
            
            // Sum all counts within window
            for (int i = 0; i < this.windowSize; i++) {
                if (timestamps[i] > cutoffTime && timestamps[i] <= timestamp) {
                    total += counts[i].get();
                }
            }
            
            return total;
        }
    }
    
    @Override
    public void reset() {
        synchronized (lock) {
            Arrays.fill(timestamps, 0);
            for (AtomicLong count : counts) {
                count.set(0);
            }
        }
    }
    
    @Override
    public CounterType getType() {
        return CounterType.SLIDING_WINDOW;
    }
}

// ==================== Implementation 2: Fixed Window Counter ====================

/**
 * Fixed Window Counter using time buckets
 * 
 * Algorithm:
 * 1. Divide time into fixed buckets (e.g., 1-minute buckets)
 * 2. Each bucket = counter for that time period
 * 3. On hit: increment current bucket
 * 4. On read: sum buckets in window
 * 
 * Time Complexity:
 * - hit(): O(1) - hash map access
 * - getHits(): O(number of buckets in window)
 * 
 * Space Complexity: O(number of active buckets)
 * 
 * Pros:
 * ✓ Very fast writes (O(1))
 * ✓ Fast reads (O(buckets), typically small)
 * ✓ Simple to implement
 * ✓ Works with any window size
 * 
 * Cons:
 * ✗ Boundary problem (can double-count)
 * ✗ Less accurate than sliding window
 * ✗ Needs periodic cleanup
 * 
 * Boundary Problem Explained:
 * 
 * Window: Last 60 seconds
 * Bucket size: 60 seconds
 * 
 * Time:      |------ Bucket 1 ------|------ Bucket 2 ------|
 * Seconds:   0    30    60    90    120
 * Hits:           100         50
 * 
 * At t=90:
 * Count = Bucket 1 (100 hits) + Bucket 2 (50 hits) = 150
 * But actual hits in last 60 seconds (30-90) = only 50!
 * 
 * Overcount by including hits from 0-30 (outside window)
 * 
 * Solution: Use smaller buckets
 * Instead of 60-second buckets, use 1-second buckets
 * More buckets, more accurate, slight overhead
 * 
 * When to Use:
 * - Approximate counts OK
 * - Need extreme performance
 * - Small bucket sizes acceptable
 * - Simple implementation preferred
 * 
 * Real Example:
 * Twitter trending topics: "Tweets in last hour"
 * 1-minute buckets, slight inaccuracy OK
 * Priority: Speed > 100% accuracy
 */
class FixedWindowCounter implements HitCounter {
    private final ConcurrentHashMap<Long, AtomicLong> buckets;
    private final int bucketSizeSeconds;
    
    public FixedWindowCounter(int bucketSizeSeconds) {
        this.buckets = new ConcurrentHashMap<>();
        this.bucketSizeSeconds = bucketSizeSeconds;
    }
    
    @Override
    public void hit(long timestamp) {
        long bucketKey = timestamp / bucketSizeSeconds;
        buckets.computeIfAbsent(bucketKey, k -> new AtomicLong(0))
               .incrementAndGet();
        
        // Cleanup old buckets periodically
        cleanupOldBuckets(timestamp);
    }
    
    @Override
    public long getHits(long timestamp, int windowSize) {
        long cutoffTime = timestamp - windowSize;
        long startBucket = cutoffTime / bucketSizeSeconds;
        long endBucket = timestamp / bucketSizeSeconds;
        
        long total = 0;
        for (long bucket = startBucket; bucket <= endBucket; bucket++) {
            AtomicLong count = buckets.get(bucket);
            if (count != null) {
                total += count.get();
            }
        }
        
        return total;
    }
    
    /**
     * Memory Management: Remove old buckets
     * 
     * Why cleanup?
     * - Prevent memory leak
     * - Old buckets never read again
     * - Keep memory footprint bounded
     * 
     * Trade-off:
     * - Cleanup adds overhead
     * - But prevents unbounded growth
     */
    private void cleanupOldBuckets(long currentTimestamp) {
        long cutoffBucket = (currentTimestamp - 3600) / bucketSizeSeconds;
        buckets.keySet().removeIf(bucket -> bucket < cutoffBucket);
    }
    
    @Override
    public void reset() {
        buckets.clear();
    }
    
    @Override
    public CounterType getType() {
        return CounterType.FIXED_WINDOW;
    }
}

// ==================== Implementation 3: Sliding Log Counter ====================

/**
 * Sliding Log Counter: Store every hit timestamp
 * 
 * Algorithm:
 * 1. Store all hit timestamps in queue
 * 2. On hit: add timestamp to queue
 * 3. On read: remove old timestamps, count remaining
 * 
 * Time Complexity:
 * - hit(): O(1) - queue append
 * - getHits(): O(n) where n = hits in window
 * 
 * Space Complexity: O(hits in window)
 * 
 * Pros:
 * ✓ 100% accurate (no approximation)
 * ✓ No boundary issues
 * ✓ Works for any window size
 * ✓ No configuration needed
 * 
 * Cons:
 * ✗ Memory grows with hits (unbounded!)
 * ✗ Slow reads with high traffic
 * ✗ Not suitable for high-volume systems
 * 
 * Memory Growth Problem:
 * 
 * Traffic: 1000 hits/second
 * Window: 300 seconds (5 minutes)
 * Memory: 300,000 timestamps stored!
 * 
 * Each timestamp: 8 bytes (long)
 * Total: 300,000 * 8 = 2.4 MB per URL
 * 
 * With 10,000 URLs: 24 GB memory! (PROBLEM!)
 * 
 * When to Use:
 * - Low traffic (< 100 hits/sec)
 * - Need 100% accuracy
 * - Short time windows
 * - Audit trails required
 * 
 * Real Example:
 * Financial transactions: "Transactions in last 60 seconds"
 * Low volume (100/sec), need exact count
 * Accuracy > memory efficiency
 * 
 * Interview Tip:
 * "For high-traffic systems, I'd use Fixed Window Counter.
 *  For critical systems needing accuracy, Sliding Log Counter.
 *  Trade-off: Speed vs. Accuracy vs. Memory"
 */
class SlidingLogCounter implements HitCounter {
    private final Queue<Long> timestamps;
    private final Object lock = new Object();
    
    public SlidingLogCounter() {
        this.timestamps = new LinkedList<>();
    }
    
    @Override
    public void hit(long timestamp) {
        synchronized (lock) {
            timestamps.offer(timestamp);
        }
    }
    
    @Override
    public long getHits(long timestamp, int windowSize) {
        synchronized (lock) {
            long cutoffTime = timestamp - windowSize;
            
            // Remove expired timestamps (cleanup)
            while (!timestamps.isEmpty() && timestamps.peek() <= cutoffTime) {
                timestamps.poll();
            }
            
            // Remaining timestamps are in window
            return timestamps.size();
        }
    }
    
    @Override
    public void reset() {
        synchronized (lock) {
            timestamps.clear();
        }
    }
    
    @Override
    public CounterType getType() {
        return CounterType.SLIDING_LOG;
    }
}

// ==================== Multi-Window Hit Counter ====================

/**
 * Supports multiple time windows simultaneously
 * 
 * Why separate counters per window?
 * - Different algorithms optimal for different windows
 * - Last second: Sliding Window (small, fast)
 * - Last day: Fixed Window (large, memory efficient)
 * 
 * Use Case:
 * Dashboard showing: "Hits in last minute, hour, day"
 * All updated in real-time from single hit event
 */
class MultiWindowHitCounter {
    private final Map<TimeWindow, HitCounter> counters;
    private final CounterType counterType;
    
    public MultiWindowHitCounter(CounterType counterType) {
        this.counters = new ConcurrentHashMap<>();
        this.counterType = counterType;
        
        // Initialize counter for each time window
        for (TimeWindow window : TimeWindow.values()) {
            counters.put(window, createCounter(counterType, window.getSeconds()));
        }
    }
    
    private HitCounter createCounter(CounterType type, int windowSize) {
        switch (type) {
            case SLIDING_WINDOW:
                return new SlidingWindowCounter(windowSize);
            case FIXED_WINDOW:
                return new FixedWindowCounter(Math.max(1, windowSize / 60));
            case SLIDING_LOG:
                return new SlidingLogCounter();
            default:
                throw new IllegalArgumentException("Unknown counter type");
        }
    }
    
    public void hit() {
        long timestamp = System.currentTimeMillis() / 1000;
        hit(timestamp);
    }
    
    public void hit(long timestamp) {
        // Update all window counters
        for (HitCounter counter : counters.values()) {
            counter.hit(timestamp);
        }
    }
    
    public long getHits(TimeWindow window) {
        long timestamp = System.currentTimeMillis() / 1000;
        return getHits(timestamp, window);
    }
    
    public long getHits(long timestamp, TimeWindow window) {
        HitCounter counter = counters.get(window);
        return counter.getHits(timestamp, window.getSeconds());
    }
    
    public Map<TimeWindow, Long> getAllWindowHits() {
        Map<TimeWindow, Long> result = new HashMap<>();
        long timestamp = System.currentTimeMillis() / 1000;
        
        for (TimeWindow window : TimeWindow.values()) {
            result.put(window, getHits(timestamp, window));
        }
        
        return result;
    }
    
    public void reset() {
        for (HitCounter counter : counters.values()) {
            counter.reset();
        }
    }
}

// ==================== URL Counter Manager ====================

/**
 * Manages hit counters for multiple URLs/pages
 * 
 * Use Case: Google Analytics tracking multiple websites
 * Each URL has independent counter
 * 
 * Scalability Considerations:
 * - 1M URLs * 1KB per counter = 1GB memory
 * - Could add LRU eviction for inactive URLs
 * - Could persist to disk for historical data
 * - Could use Redis for distributed counting
 */
class UrlHitCounterManager {
    private final ConcurrentHashMap<String, MultiWindowHitCounter> urlCounters;
    private final CounterType defaultCounterType;
    
    public UrlHitCounterManager(CounterType defaultCounterType) {
        this.urlCounters = new ConcurrentHashMap<>();
        this.defaultCounterType = defaultCounterType;
    }
    
    /**
     * Record a hit on URL
     * 
     * Why computeIfAbsent?
     * - Thread-safe counter creation
     * - Atomic check-and-create
     * - No race conditions
     */
    public void recordHit(String url) {
        urlCounters.computeIfAbsent(url, k -> new MultiWindowHitCounter(defaultCounterType))
                   .hit();
    }
    
    public long getHits(String url, TimeWindow window) {
        MultiWindowHitCounter counter = urlCounters.get(url);
        return counter != null ? counter.getHits(window) : 0;
    }
    
    public Map<TimeWindow, Long> getUrlStats(String url) {
        MultiWindowHitCounter counter = urlCounters.get(url);
        return counter != null ? counter.getAllWindowHits() : Collections.emptyMap();
    }
    
    /**
     * Get top N URLs by traffic
     * 
     * Use Case: "Top 10 pages this hour"
     * 
     * Performance Note:
     * - O(n log k) where n = URLs, k = limit
     * - For millions of URLs, consider approximate algorithm
     * - Count-Min Sketch for top-k estimation
     */
    public Map<String, Long> getTopUrls(TimeWindow window, int limit) {
        return urlCounters.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getHits(window)
            ))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    public void resetUrl(String url) {
        MultiWindowHitCounter counter = urlCounters.get(url);
        if (counter != null) {
            counter.reset();
        }
    }
    
    public void resetAll() {
        urlCounters.values().forEach(MultiWindowHitCounter::reset);
    }
    
    public Set<String> getAllUrls() {
        return new HashSet<>(urlCounters.keySet());
    }
}

// ==================== Analytics Dashboard ====================

/**
 * Real-time analytics dashboard
 * 
 * Features:
 * - Per-URL statistics
 * - Top URLs ranking
 * - All URLs summary
 * 
 * Production Extensions:
 * - Graphs/charts
 * - Alerting (traffic spikes)
 * - Anomaly detection
 * - Historical trends
 */
class AnalyticsDashboard {
    private final UrlHitCounterManager counterManager;
    
    public AnalyticsDashboard(UrlHitCounterManager counterManager) {
        this.counterManager = counterManager;
    }
    
    public void printUrlStats(String url) {
        System.out.println("\n===== Statistics for: " + url + " =====");
        Map<TimeWindow, Long> stats = counterManager.getUrlStats(url);
        
        if (stats.isEmpty()) {
            System.out.println("No data available");
            return;
        }
        
        for (TimeWindow window : TimeWindow.values()) {
            Long hits = stats.get(window);
            System.out.printf("%-15s: %,d hits%n", window.name(), hits);
        }
        System.out.println("=====================================\n");
    }
    
    public void printTopUrls(TimeWindow window, int limit) {
        System.out.println("\n===== Top " + limit + " URLs (" + window.name() + ") =====");
        Map<String, Long> topUrls = counterManager.getTopUrls(window, limit);
        
        int rank = 1;
        for (Map.Entry<String, Long> entry : topUrls.entrySet()) {
            System.out.printf("%d. %-30s: %,d hits%n", 
                rank++, entry.getKey(), entry.getValue());
        }
        System.out.println("===========================================\n");
    }
    
    public void printAllUrlsSummary() {
        System.out.println("\n===== All URLs Summary =====");
        Set<String> urls = counterManager.getAllUrls();
        
        for (String url : urls) {
            long hitsLastMinute = counterManager.getHits(url, TimeWindow.LAST_MINUTE);
            long hitsLastHour = counterManager.getHits(url, TimeWindow.LAST_HOUR);
            System.out.printf("%-30s: Last min=%,d, Last hour=%,d%n", 
                url, hitsLastMinute, hitsLastHour);
        }
        System.out.println("============================\n");
    }
}

// ==================== Demo ====================

public class HitCounterSystem {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Hit Counter System Demo ===\n");
        
        // Test 1: Compare algorithms
        System.out.println("--- Test 1: Algorithm Comparison ---\n");
        compareAlgorithms();
        
        // Test 2: Multi-window counter
        System.out.println("\n--- Test 2: Multi-Window Counter ---\n");
        testMultiWindow();
        
        // Test 3: URL Analytics
        System.out.println("\n--- Test 3: URL Analytics Dashboard ---\n");
        testUrlAnalytics();
        
        // Test 4: Performance benchmark
        System.out.println("\n--- Test 4: Performance Benchmark ---\n");
        benchmarkPerformance();
        
        System.out.println("\n=== Demo Completed ===");
    }
    
    /**
     * Compare different counter implementations
     * Shows accuracy differences
     */
    private static void compareAlgorithms() {
        long baseTime = 1000;
        
        System.out.println("Testing with 100 hits over 100 seconds...\n");
        
        // Test all three implementations
        HitCounter[] counters = {
            new SlidingWindowCounter(60),
            new FixedWindowCounter(1),
            new SlidingLogCounter()
        };
        
        // Record 100 hits
        for (HitCounter counter : counters) {
            for (int i = 0; i < 100; i++) {
                counter.hit(baseTime + i);
            }
            
            long hits = counter.getHits(baseTime + 99, 60);
            System.out.printf("%-20s: %d hits in last 60s%n", 
                counter.getType(), hits);
        }
        
        System.out.println("\nNote: All should show 60 hits (timestamps 40-99)");
    }
    
    /**
     * Test multi-window counter
     */
    private static void testMultiWindow() throws InterruptedException {
        MultiWindowHitCounter counter = new MultiWindowHitCounter(CounterType.SLIDING_LOG);
        
        // Simulate real-time hits
        System.out.println("Simulating 50 hits over time...\n");
        for (int i = 0; i < 50; i++) {
            counter.hit();
            Thread.sleep(20);
        }
        
        // Display all window statistics
        Map<TimeWindow, Long> stats = counter.getAllWindowHits();
        System.out.println("Hit Statistics:");
        for (Map.Entry<TimeWindow, Long> entry : stats.entrySet()) {
            System.out.printf("%-15s: %,d hits%n", entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Test URL-based analytics
     * Simulates real website traffic
     */
    private static void testUrlAnalytics() throws InterruptedException {
        UrlHitCounterManager manager = new UrlHitCounterManager(CounterType.SLIDING_LOG);
        AnalyticsDashboard dashboard = new AnalyticsDashboard(manager);
        
        // Simulate traffic to different URLs
        String[] urls = {"/home", "/products", "/about", "/contact", "/blog"};
        Random random = new Random();
        
        System.out.println("Simulating 200 hits across 5 URLs...\n");
        for (int i = 0; i < 200; i++) {
            String url = urls[random.nextInt(urls.length)];
            manager.recordHit(url);
            Thread.sleep(5);
        }
        
        // Display analytics
        dashboard.printAllUrlsSummary();
        dashboard.printTopUrls(TimeWindow.LAST_MINUTE, 3);
        dashboard.printUrlStats("/home");
    }
    
    /**
     * Performance benchmark
     * Compare write throughput of different algorithms
     */
    private static void benchmarkPerformance() {
        int numHits = 10000;
        CounterType[] types = {
            CounterType.SLIDING_WINDOW,
            CounterType.FIXED_WINDOW,
            CounterType.SLIDING_LOG
        };
        
        System.out.println("Writing 10,000 hits per algorithm...\n");
        
        for (CounterType type : types) {
            MultiWindowHitCounter counter = new MultiWindowHitCounter(type);
            
            long startTime = System.nanoTime();
            for (int i = 0; i < numHits; i++) {
                counter.hit();
            }
            long endTime = System.nanoTime();
            
            long durationMs = (endTime - startTime) / 1_000_000;
            double hitsPerMs = (double) numHits / durationMs;
            
            System.out.printf("%-20s: %,d hits in %d ms (%.0f hits/ms)%n",
                type.name(), numHits, durationMs, hitsPerMs);
        }
        
        System.out.println("\nConclusion:");
        System.out.println("- Fixed Window: Fastest (O(1) writes)");
        System.out.println("- Sliding Window: Fast with fixed memory");
        System.out.println("- Sliding Log: Slowest but most accurate");
    }
}
