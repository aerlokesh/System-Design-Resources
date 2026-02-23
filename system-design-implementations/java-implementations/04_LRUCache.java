import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU CACHE - System Design Implementation
 * 
 * Concepts demonstrated:
 * - O(1) get and put using HashMap + Doubly Linked List
 * - Thread-safe implementation with ReadWriteLock
 * - TTL (Time-To-Live) support
 * - LFU (Least Frequently Used) variant
 * - Cache statistics (hit rate, miss rate)
 * 
 * Interview talking points:
 * - HashMap gives O(1) lookup, DLL gives O(1) eviction
 * - Used in: Memcached, Redis, CPU caches, CDNs
 * - Cache eviction policies: LRU, LFU, FIFO, Random
 * - Write policies: Write-through, Write-back, Write-around
 * - Cache invalidation: TTL, event-driven, manual
 * - Thundering herd: Use distributed lock or request coalescing
 */
class LRUCache {

    // ==================== 1. CLASSIC LRU CACHE ====================
    static class BasicLRUCache<K, V> {
        // Doubly linked list node
        static class Node<K, V> {
            K key;
            V value;
            Node<K, V> prev, next;
            Node(K key, V value) { this.key = key; this.value = value; }
        }

        private final int capacity;
        private final Map<K, Node<K, V>> map;
        private final Node<K, V> head, tail; // Sentinel nodes
        private int size;

        // Stats
        private long hits, misses;

        BasicLRUCache(int capacity) {
            this.capacity = capacity;
            this.map = new HashMap<>();
            this.head = new Node<>(null, null);
            this.tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }

        V get(K key) {
            Node<K, V> node = map.get(key);
            if (node == null) {
                misses++;
                return null;
            }
            hits++;
            moveToHead(node);
            return node.value;
        }

        void put(K key, V value) {
            Node<K, V> node = map.get(key);
            if (node != null) {
                node.value = value;
                moveToHead(node);
            } else {
                node = new Node<>(key, value);
                map.put(key, node);
                addToHead(node);
                size++;
                if (size > capacity) {
                    Node<K, V> evicted = removeTail();
                    map.remove(evicted.key);
                    size--;
                }
            }
        }

        boolean containsKey(K key) { return map.containsKey(key); }
        int size() { return size; }
        double hitRate() { return (hits + misses) == 0 ? 0 : (double) hits / (hits + misses); }

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
            Node<K, V> node = tail.prev;
            removeNode(node);
            return node;
        }

        // For display
        List<K> getOrder() {
            List<K> order = new ArrayList<>();
            Node<K, V> curr = head.next;
            while (curr != tail) {
                order.add(curr.key);
                curr = curr.next;
            }
            return order;
        }
    }

    // ==================== 2. THREAD-SAFE LRU CACHE ====================
    static class ConcurrentLRUCache<K, V> {
        private final BasicLRUCache<K, V> cache;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        ConcurrentLRUCache(int capacity) {
            this.cache = new BasicLRUCache<>(capacity);
        }

        V get(K key) {
            lock.writeLock().lock(); // Write lock because get modifies order
            try { return cache.get(key); }
            finally { lock.writeLock().unlock(); }
        }

        void put(K key, V value) {
            lock.writeLock().lock();
            try { cache.put(key, value); }
            finally { lock.writeLock().unlock(); }
        }

        int size() {
            lock.readLock().lock();
            try { return cache.size(); }
            finally { lock.readLock().unlock(); }
        }

        double hitRate() {
            lock.readLock().lock();
            try { return cache.hitRate(); }
            finally { lock.readLock().unlock(); }
        }
    }

    // ==================== 3. LRU CACHE WITH TTL ====================
    static class TTLLRUCache<K, V> {
        static class TTLNode<K, V> {
            K key;
            V value;
            long expiresAt;
            TTLNode<K, V> prev, next;
            TTLNode(K key, V value, long ttlMs) {
                this.key = key;
                this.value = value;
                this.expiresAt = System.currentTimeMillis() + ttlMs;
            }
            boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
        }

        private final int capacity;
        private final long defaultTTLMs;
        private final Map<K, TTLNode<K, V>> map = new HashMap<>();
        private final TTLNode<K, V> head, tail;
        private int size;

        TTLLRUCache(int capacity, long defaultTTLMs) {
            this.capacity = capacity;
            this.defaultTTLMs = defaultTTLMs;
            head = new TTLNode<>(null, null, 0);
            tail = new TTLNode<>(null, null, 0);
            head.next = tail;
            tail.prev = head;
        }

        V get(K key) {
            TTLNode<K, V> node = map.get(key);
            if (node == null) return null;
            if (node.isExpired()) {
                removeNode(node);
                map.remove(key);
                size--;
                return null;
            }
            moveToHead(node);
            return node.value;
        }

        void put(K key, V value) { put(key, value, defaultTTLMs); }

        void put(K key, V value, long ttlMs) {
            TTLNode<K, V> existing = map.get(key);
            if (existing != null) {
                removeNode(existing);
                size--;
            }
            TTLNode<K, V> node = new TTLNode<>(key, value, ttlMs);
            map.put(key, node);
            addToHead(node);
            size++;
            evictIfNeeded();
        }

        int cleanExpired() {
            int removed = 0;
            Iterator<Map.Entry<K, TTLNode<K, V>>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<K, TTLNode<K, V>> entry = it.next();
                if (entry.getValue().isExpired()) {
                    removeNode(entry.getValue());
                    it.remove();
                    size--;
                    removed++;
                }
            }
            return removed;
        }

        int size() { return size; }

        private void evictIfNeeded() {
            while (size > capacity) {
                TTLNode<K, V> evicted = tail.prev;
                removeNode(evicted);
                map.remove(evicted.key);
                size--;
            }
        }

        private void addToHead(TTLNode<K, V> node) {
            node.prev = head;
            node.next = head.next;
            head.next.prev = node;
            head.next = node;
        }

        private void removeNode(TTLNode<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        private void moveToHead(TTLNode<K, V> node) {
            removeNode(node);
            addToHead(node);
        }
    }

    // ==================== 4. LFU CACHE (Least Frequently Used) ====================
    static class LFUCache<K, V> {
        static class LFUNode<K, V> {
            K key;
            V value;
            int freq;
            LFUNode(K key, V value) { this.key = key; this.value = value; this.freq = 1; }
        }

        private final int capacity;
        private final Map<K, LFUNode<K, V>> keyMap = new HashMap<>();
        private final Map<Integer, LinkedHashSet<K>> freqMap = new HashMap<>();
        private int minFreq;

        LFUCache(int capacity) {
            this.capacity = capacity;
            this.minFreq = 0;
        }

        V get(K key) {
            LFUNode<K, V> node = keyMap.get(key);
            if (node == null) return null;
            incrementFreq(node);
            return node.value;
        }

        void put(K key, V value) {
            if (capacity <= 0) return;
            LFUNode<K, V> node = keyMap.get(key);
            if (node != null) {
                node.value = value;
                incrementFreq(node);
            } else {
                if (keyMap.size() >= capacity) {
                    // Evict least frequent, least recent
                    LinkedHashSet<K> minSet = freqMap.get(minFreq);
                    K evictKey = minSet.iterator().next();
                    minSet.remove(evictKey);
                    if (minSet.isEmpty()) freqMap.remove(minFreq);
                    keyMap.remove(evictKey);
                }
                node = new LFUNode<>(key, value);
                keyMap.put(key, node);
                freqMap.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
                minFreq = 1;
            }
        }

        private void incrementFreq(LFUNode<K, V> node) {
            int oldFreq = node.freq;
            LinkedHashSet<K> oldSet = freqMap.get(oldFreq);
            oldSet.remove(node.key);
            if (oldSet.isEmpty()) {
                freqMap.remove(oldFreq);
                if (minFreq == oldFreq) minFreq++;
            }
            node.freq++;
            freqMap.computeIfAbsent(node.freq, k -> new LinkedHashSet<>()).add(node.key);
        }

        int size() { return keyMap.size(); }

        int getFrequency(K key) {
            LFUNode<K, V> node = keyMap.get(key);
            return node != null ? node.freq : 0;
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== LRU CACHE - System Design Demo ===\n");

        // 1. Basic LRU Cache
        System.out.println("--- 1. Basic LRU Cache (capacity=3) ---");
        BasicLRUCache<String, String> lru = new BasicLRUCache<>(3);
        lru.put("A", "Apple");
        lru.put("B", "Banana");
        lru.put("C", "Cherry");
        System.out.printf("  After A,B,C: %s%n", lru.getOrder());

        lru.get("A"); // Access A, moves to front
        System.out.printf("  After get(A): %s%n", lru.getOrder());

        lru.put("D", "Date"); // Evicts B (least recently used)
        System.out.printf("  After put(D): %s (B evicted)%n", lru.getOrder());
        System.out.printf("  get(B): %s (evicted)%n", lru.get("B"));
        System.out.printf("  get(A): %s%n", lru.get("A"));

        // 2. Cache hit rate
        System.out.println("\n--- 2. Cache Hit Rate ---");
        BasicLRUCache<Integer, String> hitCache = new BasicLRUCache<>(5);
        // Simulate: put 10 items (half will be evicted), then access all
        for (int i = 0; i < 10; i++) hitCache.put(i, "val" + i);
        for (int i = 0; i < 10; i++) hitCache.get(i);
        System.out.printf("  Capacity=5, inserted 10, accessed 10%n");
        System.out.printf("  Hit rate: %.1f%% (5 hits, 5 misses)%n", hitCache.hitRate() * 100);

        // 3. Thread-safe cache
        System.out.println("\n--- 3. Thread-Safe LRU Cache ---");
        ConcurrentLRUCache<Integer, Integer> concCache = new ConcurrentLRUCache<>(100);
        int threads = 8;
        int opsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threads);
        long start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            new Thread(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    int key = (tid * opsPerThread + i) % 200; // Some overlap for hits
                    concCache.put(key, key * 10);
                    concCache.get(key);
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("  %d ops across %d threads in %dms%n",
                threads * opsPerThread * 2, threads, ms);
        System.out.printf("  Size: %d, Hit rate: %.1f%%%n",
                concCache.size(), concCache.hitRate() * 100);

        // 4. TTL cache
        System.out.println("\n--- 4. LRU Cache with TTL ---");
        TTLLRUCache<String, String> ttlCache = new TTLLRUCache<>(10, 500); // 500ms default TTL
        ttlCache.put("fast", "expires quickly", 200);
        ttlCache.put("slow", "expires slowly", 2000);
        ttlCache.put("default", "uses default TTL");

        System.out.printf("  Immediately - fast: %s, slow: %s, default: %s%n",
                ttlCache.get("fast"), ttlCache.get("slow"), ttlCache.get("default"));

        Thread.sleep(300);
        System.out.printf("  After 300ms - fast: %s, slow: %s, default: %s%n",
                ttlCache.get("fast"), ttlCache.get("slow"), ttlCache.get("default"));

        Thread.sleep(300);
        System.out.printf("  After 600ms - fast: %s, slow: %s, default: %s%n",
                ttlCache.get("fast"), ttlCache.get("slow"), ttlCache.get("default"));

        // 5. LFU Cache
        System.out.println("\n--- 5. LFU Cache (Least Frequently Used, capacity=3) ---");
        LFUCache<String, String> lfu = new LFUCache<>(3);
        lfu.put("A", "Apple");
        lfu.put("B", "Banana");
        lfu.put("C", "Cherry");

        // Access A twice, B once -> C has lowest frequency
        lfu.get("A"); lfu.get("A"); // freq(A) = 3
        lfu.get("B");               // freq(B) = 2
        // C has freq = 1

        System.out.printf("  Frequencies: A=%d, B=%d, C=%d%n",
                lfu.getFrequency("A"), lfu.getFrequency("B"), lfu.getFrequency("C"));

        lfu.put("D", "Date"); // Should evict C (lowest freq)
        System.out.printf("  After put(D): A=%s, B=%s, C=%s, D=%s%n",
                lfu.get("A"), lfu.get("B"), lfu.get("C"), lfu.get("D"));
        System.out.println("  C was evicted (lowest frequency)");

        // 6. Java built-in LinkedHashMap as LRU
        System.out.println("\n--- 6. Java LinkedHashMap as LRU (capacity=3) ---");
        LinkedHashMap<String, String> linkedLru = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > 3;
            }
        };
        linkedLru.put("X", "x-val");
        linkedLru.put("Y", "y-val");
        linkedLru.put("Z", "z-val");
        linkedLru.get("X");           // Access X
        linkedLru.put("W", "w-val");  // Evicts Y
        System.out.printf("  Keys: %s (Y evicted after X was accessed)%n", linkedLru.keySet());

        // Summary
        System.out.println("\n--- Eviction Policy Comparison ---");
        System.out.println("  LRU: Evict least recently used. Good for temporal locality.");
        System.out.println("  LFU: Evict least frequently used. Good for popularity-based access.");
        System.out.println("  FIFO: Evict oldest entry. Simple but ignores access patterns.");
        System.out.println("  Random: Evict random entry. O(1) but no intelligence.");
    }
}
