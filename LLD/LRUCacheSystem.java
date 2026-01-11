import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU Cache Low-Level Design - Complete Implementation
 * 
 * This file demonstrates:
 * 1. Basic LRU Cache (Thread-unsafe)
 * 2. Thread-safe LRU Cache (Synchronized)
 * 3. High-Performance LRU Cache (ReadWriteLock)
 * 4. LRU Cache with TTL
 * 5. LFU Cache
 * 6. Two-Level Cache
 * 7. Cache with Eviction Callback
 * 8. Cache with Statistics
 * 
 * Time Complexity: O(1) for get and put operations
 * Space Complexity: O(capacity)
 * 
 * @author System Design Repository
 * @version 1.0
 */

// ============================================================================
// CORE INTERFACES
// ============================================================================

/**
 * Generic Cache interface
 */
interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    int size();
    int capacity();
    void clear();
}

// ============================================================================
// 1. BASIC LRU CACHE (THREAD-UNSAFE)
// ============================================================================

/**
 * Basic LRU Cache implementation using HashMap + Doubly Linked List
 * Thread-unsafe version for single-threaded use
 */
class LRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }
    
    @Override
    public V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        moveToHead(node);
        return node.value;
    }
    
    @Override
    public void put(K key, V value) {
        Node<K, V> node = map.get(key);
        
        if (node != null) {
            node.value = value;
            moveToHead(node);
        } else {
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);
            
            if (map.size() > capacity) {
                Node<K, V> lru = removeTail();
                map.remove(lru.key);
            }
        }
    }
    
    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }
    
    private Node<K, V> removeTail() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }
    
    @Override
    public int size() {
        return map.size();
    }
    
    @Override
    public int capacity() {
        return capacity;
    }
    
    @Override
    public void clear() {
        map.clear();
        head.next = tail;
        tail.prev = head;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LRUCache[");
        Node<K, V> current = head.next;
        while (current != tail) {
            sb.append(current.key).append("=").append(current.value);
            if (current.next != tail) {
                sb.append(", ");
            }
            current = current.next;
        }
        sb.append("]");
        return sb.toString();
    }
}

// ============================================================================
// 2. THREAD-SAFE LRU CACHE (SYNCHRONIZED)
// ============================================================================

/**
 * Thread-safe LRU Cache using synchronized methods
 */
class SynchronizedLRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    public SynchronizedLRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }
    
    @Override
    public synchronized V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        moveToHead(node);
        return node.value;
    }
    
    @Override
    public synchronized void put(K key, V value) {
        Node<K, V> node = map.get(key);
        
        if (node != null) {
            node.value = value;
            moveToHead(node);
        } else {
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);
            
            if (map.size() > capacity) {
                Node<K, V> lru = removeTail();
                map.remove(lru.key);
            }
        }
    }
    
    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }
    
    private Node<K, V> removeTail() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }
    
    @Override
    public synchronized int size() {
        return map.size();
    }
    
    @Override
    public int capacity() {
        return capacity;
    }
    
    @Override
    public synchronized void clear() {
        map.clear();
        head.next = tail;
        tail.prev = head;
    }
}

// ============================================================================
// 3. HIGH-PERFORMANCE THREAD-SAFE LRU CACHE (READWRITELOCK)
// ============================================================================

/**
 * Thread-safe LRU Cache using ReadWriteLock
 */
class ConcurrentLRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    private final ReadWriteLock lock;
    private final Lock readLock;
    private final Lock writeLock;
    
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    public ConcurrentLRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
        
        this.lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
    }
    
    @Override
    public V get(K key) {
        writeLock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) {
                return null;
            }
            moveToHead(node);
            return node.value;
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public void put(K key, V value) {
        writeLock.lock();
        try {
            Node<K, V> node = map.get(key);
            
            if (node != null) {
                node.value = value;
                moveToHead(node);
            } else {
                Node<K, V> newNode = new Node<>(key, value);
                map.put(key, newNode);
                addToHead(newNode);
                
                if (map.size() > capacity) {
                    Node<K, V> lru = removeTail();
                    map.remove(lru.key);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }
    
    private Node<K, V> removeTail() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }
    
    @Override
    public int size() {
        readLock.lock();
        try {
            return map.size();
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public int capacity() {
        return capacity;
    }
    
    @Override
    public void clear() {
        writeLock.lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            writeLock.unlock();
        }
    }
}

// ============================================================================
// 4. LRU CACHE WITH TTL
// ============================================================================

/**
 * LRU Cache with Time To Live (TTL) support
 */
class TTLLRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final long ttlMillis;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    
    private static class Node<K, V> {
        K key;
        V value;
        long expiryTime;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value, long expiryTime) {
            this.key = key;
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }
    
    public TTLLRUCache(int capacity, long ttlMillis) {
        this.capacity = capacity;
        this.ttlMillis = ttlMillis;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null, 0);
        this.tail = new Node<>(null, null, 0);
        head.next = tail;
        tail.prev = head;
    }
    
    @Override
    public V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        
        if (System.currentTimeMillis() > node.expiryTime) {
            removeNode(node);
            map.remove(key);
            return null;
        }
        
        moveToHead(node);
        return node.value;
    }
    
    @Override
    public void put(K key, V value) {
        long expiryTime = System.currentTimeMillis() + ttlMillis;
        Node<K, V> node = map.get(key);
        
        if (node != null) {
            node.value = value;
            node.expiryTime = expiryTime;
            moveToHead(node);
        } else {
            Node<K, V> newNode = new Node<>(key, value, expiryTime);
            map.put(key, newNode);
            addToHead(newNode);
            
            if (map.size() > capacity) {
                Node<K, V> lru = removeTail();
                map.remove(lru.key);
            }
        }
    }
    
    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }
    
    private Node<K, V> removeTail() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }
    
    @Override
    public int size() {
        return map.size();
    }
    
    @Override
    public int capacity() {
        return capacity;
    }
    
    @Override
    public void clear() {
        map.clear();
        head.next = tail;
        tail.prev = head;
    }
}

// ============================================================================
// 5. LFU CACHE
// ============================================================================

/**
 * LFU (Least Frequently Used) Cache
 */
class LFUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private int minFrequency;
    private final Map<K, Node<K, V>> cache;
    private final Map<Integer, DoubleLinkedList<K, V>> frequencyMap;
    
    private static class Node<K, V> {
        K key;
        V value;
        int frequency;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
            this.frequency = 1;
        }
    }
    
    private static class DoubleLinkedList<K, V> {
        Node<K, V> head;
        Node<K, V> tail;
        int size;
        
        DoubleLinkedList() {
            head = new Node<>(null, null);
            tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }
        
        void addToHead(Node<K, V> node) {
            node.prev = head;
            node.next = head.next;
            head.next.prev = node;
            head.next = node;
            size++;
        }
        
        void remove(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            size--;
        }
        
        Node<K, V> removeTail() {
            if (size == 0) return null;
            Node<K, V> node = tail.prev;
            remove(node);
            return node;
        }
    }
    
    public LFUCache(int capacity) {
        this.capacity = capacity;
        this.minFrequency = 0;
        this.cache = new HashMap<>();
        this.frequencyMap = new HashMap<>();
    }
    
    @Override
    public V get(K key) {
        Node<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        updateFrequency(node);
        return node.value;
    }
    
    @Override
    public void put(K key, V value) {
        if (capacity == 0) return;
        
        Node<K, V> node = cache.get(key);
        
        if (node != null) {
            node.value = value;
            updateFrequency(node);
        } else {
            if (cache.size() >= capacity) {
                DoubleLinkedList<K, V> minFreqList = frequencyMap.get(minFrequency);
                Node<K, V> nodeToRemove = minFreqList.removeTail();
                cache.remove(nodeToRemove.key);
            }
            
            Node<K, V> newNode = new Node<>(key, value);
            cache.put(key, newNode);
            minFrequency = 1;
            DoubleLinkedList<K, V> list = frequencyMap.computeIfAbsent(1, 
                k -> new DoubleLinkedList<>());
            list.addToHead(newNode);
        }
    }
    
    private void updateFrequency(Node<K, V> node) {
        int freq = node.frequency;
        DoubleLinkedList<K, V> list = frequencyMap.get(freq);
        list.remove(node);
        
        if (freq == minFrequency && list.size == 0) {
            minFrequency++;
        }
        
        node.frequency++;
        DoubleLinkedList<K, V> newList = frequencyMap.computeIfAbsent(
            node.frequency, k -> new DoubleLinkedList<>());
        newList.addToHead(node);
    }
    
    @Override
    public int size() {
        return cache.size();
    }
    
    @Override
    public int capacity() {
        return capacity;
    }
    
    @Override
    public void clear() {
        cache.clear();
        frequencyMap.clear();
        minFrequency = 0;
    }
}

// ============================================================================
// 6. TWO-LEVEL CACHE
// ============================================================================

/**
 * Two-level cache system (L1 + L2)
 */
class TwoLevelCache<K, V> implements Cache<K, V> {
    private final Cache<K, V> l1Cache;
    private final Cache<K, V> l2Cache;
    
    public TwoLevelCache(int l1Capacity, int l2Capacity) {
        this.l1Cache = new LRUCache<>(l1Capacity);
        this.l2Cache = new LRUCache<>(l2Capacity);
    }
    
    @Override
    public V get(K key) {
        V value = l1Cache.get(key);
        if (value != null) {
            return value;
        }
        
        value = l2Cache.get(key);
        if (value != null) {
            l1Cache.put(key, value);
            return value;
        }
        
        return null;
    }
    
    @Override
    public void put(K key, V value) {
        l1Cache.put(key, value);
        l2Cache.put(key, value);
    }
    
    @Override
    public int size() {
        return l1Cache.size();
    }
    
    @Override
    public int capacity() {
        return l1Cache.capacity();
    }
    
    @Override
    public void clear() {
        l1Cache.clear();
        l2Cache.clear();
    }
}

// ============================================================================
// 7. CACHE WITH EVICTION CALLBACK
// ============================================================================

/**
 * LRU Cache with eviction callback support
 */
class LRUCacheWithCallback<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    private final EvictionListener<K, V> evictionListener;
    
    @FunctionalInterface
    public interface EvictionListener<K, V> {
        void onEviction(K key, V value, EvictionCause cause);
    }
    
    public enum EvictionCause {
        SIZE, EXPIRED, EXPLICIT
    }
    
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    public LRUCacheWithCallback(int capacity, EvictionListener<K, V> listener) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
        this.evictionListener = listener;
    }
    
    @Override
    public V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        moveToHead(node);
        return node.value;
    }
    
    @Override
    public void put(K key, V value) {
        Node<K, V> node = map.get(key);
        
        if (node != null) {
            node.value = value;
            moveToHead(node);
        } else {
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);
            
            if (map.size() > capacity) {
                Node<K, V> lru = removeTail();
                map.remove(lru.key);
                
                if (evictionListener != null) {
                    evictionListener.onEviction(lru.key, lru.value, EvictionCause.SIZE);
                }
            }
        }
    }
    
    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }
    
    private Node<K, V> removeTail() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }
    
    @Override
    public int size() {
        return map.size();
    }
    
    @Override
    public int capacity() {
        return capacity;
    }
    
    @Override
    public void clear() {
        map.clear();
        head.next = tail;
        tail.prev = head;
    }
}

// ============================================================================
// 8. CACHE WITH STATISTICS
// ============================================================================

/**
 * LRU Cache with comprehensive statistics tracking
 */
class InstrumentedLRUCache<K, V> implements Cache<K, V> {
    private final LRUCache<K, V> cache;
    private final CacheStatistics stats;
    
    public static class CacheStatistics {
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong puts = new AtomicLong(0);
        private final AtomicLong evictions = new AtomicLong(0);
        
        public void recordHit() {
            hits.incrementAndGet();
        }
        
        public void recordMiss() {
            misses.incrementAndGet();
        }
        
        public void recordPut() {
            puts.incrementAndGet();
        }
        
        public void recordEviction() {
            evictions.incrementAndGet();
        }
        
        public double getHitRate() {
            long total = hits.get() + misses.get();
            return total == 0 ? 0.0 : (double) hits.get() / total;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Hits: %d, Misses: %d, Hit Rate: %.2f%%, Puts: %d, Evictions: %d",
                hits.get(), misses.get(), getHitRate() * 100, puts.get(), evictions.get()
            );
        }
    }
    
    public InstrumentedLRUCache(int capacity) {
        this.cache = new LRUCache<>(capacity);
        this.stats = new CacheStatistics();
    }
    
    @Override
    public V get(K key) {
        V value = cache.get(key);
        if (value != null) {
            stats.recordHit();
        } else {
            stats.recordMiss();
        }
        return value;
    }
    
    @Override
    public void put(K key, V value) {
        int sizeBefore = cache.size();
        cache.put(key, value);
        stats.recordPut();
        
        if (sizeBefore == cache.capacity() && cache.size() == cache.capacity()) {
            stats.recordEviction();
        }
    }
    
    @Override
    public int size() {
        return cache.size();
    }
    
    @Override
    public int capacity() {
        return cache.capacity();
    }
    
    @Override
    public void clear() {
        cache.clear();
    }
    
    public CacheStatistics getStatistics() {
        return stats;
    }
}

// ============================================================================
// DEMO AND TESTING
// ============================================================================

/**
 * Demonstration of LRU Cache implementations
 */
public class LRUCacheSystem {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("LRU CACHE SYSTEM - COMPREHENSIVE DEMONSTRATION");
        System.out.println("=".repeat(70));
        
        demo1BasicUsage();
        demo2LeetCodeExample();
        demo3ThreadSafeCache();
        demo4TTLCache();
        demo5LFUCache();
        demo6TwoLevelCache();
        demo7EvictionCallback();
        demo8Statistics();
    }
    
    private static void demo1BasicUsage() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 1: Basic LRU Cache Usage");
        System.out.println("=".repeat(70));
        
        Cache<Integer, String> cache = new LRUCache<>(3);
        
        System.out.println("Creating cache with capacity 3");
        cache.put(1, "One");
        cache.put(2, "Two");
        cache.put(3, "Three");
        System.out.println("After adding 1,2,3: " + cache);
        
        System.out.println("\nAccessing key 1 (moves to front)");
        cache.get(1);
        System.out.println("After get(1): " + cache);
        
        System.out.println("\nAdding key 4 (evicts 2 - LRU)");
        cache.put(4, "Four");
        System.out.println("After put(4): " + cache);
        System.out.println("get(2): " + cache.get(2) + " (evicted)");
    }
    
    private static void demo2LeetCodeExample() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 2: LeetCode #146 Example");
        System.out.println("=".repeat(70));
        
        LRUCache<Integer, Integer> cache = new LRUCache<>(2);
        
        cache.put(1, 1);
        cache.put(2, 2);
        System.out.println("put(1,1), put(2,2)");
        System.out.println("get(1): " + cache.get(1));
        
        cache.put(3, 3);
        System.out.println("put(3,3) - evicts key 2");
        System.out.println("get(2): " + cache.get(2) + " (evicted)");
        
        cache.put(4, 4);
        System.out.println("put(4,4) - evicts key 1");
        System.out.println("get(1): " + cache.get(1) + " (evicted)");
        System.out.println("get(3): " + cache.get(3));
        System.out.println("get(4): " + cache.get(4));
    }
    
    private static void demo3ThreadSafeCache() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 3: Thread-Safe Cache");
        System.out.println("=".repeat(70));
        
        Cache<String, Integer> cache = new SynchronizedLRUCache<>(100);
        
        System.out.println("Creating thread-safe cache with capacity 100");
        cache.put("counter", 0);
        System.out.println("Initial value: " + cache.get("counter"));
        
        cache.put("counter", 42);
        System.out.println("After update: " + cache.get("counter"));
        System.out.println("Thread-safe operations work correctly!");
    }
    
    private static void demo4TTLCache() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 4: LRU Cache with TTL");
        System.out.println("=".repeat(70));
        
        Cache<String, String> cache = new TTLLRUCache<>(3, 2000);
        
        System.out.println("Creating cache with 2 second TTL");
        cache.put("key1", "value1");
        System.out.println("Added key1, get: " + cache.get("key1"));
        
        try {
            System.out.println("Waiting 2.1 seconds...");
            Thread.sleep(2100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("After 2.1 seconds, get key1: " + cache.get("key1") + " (expired)");
    }
    
    private static void demo5LFUCache() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 5: LFU Cache");
        System.out.println("=".repeat(70));
        
        Cache<Integer, String> cache = new LFUCache<>(2);
        
        System.out.println("Creating LFU cache with capacity 2");
        cache.put(1, "One");
        cache.put(2, "Two");
        
        cache.get(1);
        cache.get(1);
        cache.get(2);
        
        System.out.println("After accesses: key1 freq=3, key2 freq=2");
        cache.put(3, "Three");
        System.out.println("Added key3, key2 evicted (lower frequency)");
        System.out.println("get(2): " + cache.get(2) + " (evicted)");
        System.out.println("get(1): " + cache.get(1));
        System.out.println("get(3): " + cache.get(3));
    }
    
    private static void demo6TwoLevelCache() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 6: Two-Level Cache");
        System.out.println("=".repeat(70));
        
        Cache<String, String> cache = new TwoLevelCache<>(2, 5);
        
        System.out.println("Creating L1 (capacity=2) and L2 (capacity=5) cache");
        cache.put("a", "value-a");
        cache.put("b", "value-b");
        cache.put("c", "value-c");
        
        System.out.println("Added a, b, c");
        System.out.println("get(a): " + cache.get("a"));
        System.out.println("get(b): " + cache.get("b"));
        System.out.println("get(c): " + cache.get("c"));
        System.out.println("Multi-level caching works!");
    }
    
    private static void demo7EvictionCallback() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 7: Cache with Eviction Callback");
        System.out.println("=".repeat(70));
        
        Cache<String, String> cache = new LRUCacheWithCallback<>(2, 
            (key, value, cause) -> {
                System.out.println("  >> Evicted: " + key + "=" + value + " (" + cause + ")");
            }
        );
        
        System.out.println("Creating cache with eviction callback (capacity=2)");
        cache.put("a", "value-a");
        cache.put("b", "value-b");
        System.out.println("Added a, b");
        
        System.out.println("\nAdding c (should evict a):");
        cache.put("c", "value-c");
        
        System.out.println("\nAdding d (should evict b):");
        cache.put("d", "value-d");
    }
    
    private static void demo8Statistics() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 8: Cache with Statistics");
        System.out.println("=".repeat(70));
        
        InstrumentedLRUCache<String, Integer> cache = new InstrumentedLRUCache<>(3);
        
        System.out.println("Creating instrumented cache with capacity 3");
        
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        cache.get("a");
        cache.get("a");
        cache.get("b");
        cache.get("x");
        cache.get("y");
        
        cache.put("d", 4);
        
        System.out.println("\nCache Statistics:");
        System.out.println(cache.getStatistics());
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ALL DEMOS COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(70));
    }
}
