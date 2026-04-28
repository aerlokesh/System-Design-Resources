import java.util.*;

/**
 * Custom HashMap - HELLO Interview Framework
 * 
 * Companies: PayPal, Walmart, Flipkart, Amazon, Google +13 more
 * Pattern: Data Structure Design (Hashing + Chaining + Dynamic Resize)
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Separate chaining — linked list per bucket for collision handling
 * 2. Power-of-2 capacity — enables bitwise AND instead of modulo
 * 3. Hash spreading — XOR high bits to reduce clustering
 * 4. Dynamic resize — double capacity when loadFactor > 0.75
 * 5. Null key support — stored at index 0 (like Java HashMap)
 */

@SuppressWarnings("unchecked")
class MyHashMap<K, V> {
    
    // ─── Entry Node (linked list) ───
    static class Entry<K, V> {
        final K key;
        V value;
        final int hash;
        Entry<K, V> next;

        Entry(K key, V value, int hash, Entry<K, V> next) {
            this.key = key; this.value = value;
            this.hash = hash; this.next = next;
        }

        @Override
        public String toString() { return key + "=" + value; }
    }

    // ─── Fields ───
    private Entry<K, V>[] table;
    private int size;
    private int capacity;
    private final float loadFactor;
    private int threshold;          // capacity * loadFactor
    private int resizeCount = 0;

    private static final int DEFAULT_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // ─── Constructors ───

    public MyHashMap() { this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR); }

    public MyHashMap(int initialCapacity) { this(initialCapacity, DEFAULT_LOAD_FACTOR); }

    public MyHashMap(int initialCapacity, float loadFactor) {
        this.capacity = nextPowerOf2(Math.max(initialCapacity, 1));
        this.loadFactor = loadFactor;
        this.threshold = (int) (capacity * loadFactor);
        this.table = new Entry[capacity];
        this.size = 0;
    }

    // ─── Core Operations ───

    /** Put key-value pair. Returns previous value or null. */
    public V put(K key, V value) {
        int hash = hash(key);
        int index = indexFor(hash);

        // Search for existing key in chain
        for (Entry<K, V> e = table[index]; e != null; e = e.next) {
            if (e.hash == hash && keysEqual(e.key, key)) {
                V old = e.value;
                e.value = value;  // update existing
                return old;
            }
        }

        // Key not found — add new entry at head of chain
        table[index] = new Entry<>(key, value, hash, table[index]);
        size++;

        // Resize if needed
        if (size > threshold) resize();
        return null;
    }

    /** Get value by key. Returns null if not found. */
    public V get(K key) {
        int hash = hash(key);
        int index = indexFor(hash);

        for (Entry<K, V> e = table[index]; e != null; e = e.next) {
            if (e.hash == hash && keysEqual(e.key, key))
                return e.value;
        }
        return null;
    }

    /** Remove key. Returns removed value or null. */
    public V remove(K key) {
        int hash = hash(key);
        int index = indexFor(hash);

        Entry<K, V> prev = null;
        for (Entry<K, V> e = table[index]; e != null; prev = e, e = e.next) {
            if (e.hash == hash && keysEqual(e.key, key)) {
                if (prev == null) table[index] = e.next;
                else prev.next = e.next;
                size--;
                return e.value;
            }
        }
        return null;
    }

    public boolean containsKey(K key) {
        return get(key) != null || (key == null && containsNullKey());
    }

    private boolean containsNullKey() {
        for (Entry<K, V> e = table[0]; e != null; e = e.next)
            if (e.key == null) return true;
        return false;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    /** Get all keys */
    public List<K> keys() {
        List<K> keys = new ArrayList<>();
        for (Entry<K, V> bucket : table)
            for (Entry<K, V> e = bucket; e != null; e = e.next)
                keys.add(e.key);
        return keys;
    }

    /** Get all values */
    public List<V> values() {
        List<V> vals = new ArrayList<>();
        for (Entry<K, V> bucket : table)
            for (Entry<K, V> e = bucket; e != null; e = e.next)
                vals.add(e.value);
        return vals;
    }

    public void clear() {
        Arrays.fill(table, null);
        size = 0;
    }

    // ─── Hash Function ───
    // Spread high bits into low bits to reduce clustering
    private int hash(K key) {
        if (key == null) return 0;
        int h = key.hashCode();
        return h ^ (h >>> 16);  // XOR high 16 bits with low 16 bits
    }

    // Bitwise AND instead of modulo (works because capacity is power of 2)
    private int indexFor(int hash) {
        return hash & (capacity - 1);
    }

    private boolean keysEqual(K k1, K k2) {
        return k1 == k2 || (k1 != null && k1.equals(k2));
    }

    // ─── Dynamic Resizing ───
    private void resize() {
        int newCapacity = capacity * 2;
        Entry<K, V>[] newTable = new Entry[newCapacity];

        // Rehash all entries into new table
        for (Entry<K, V> bucket : table) {
            Entry<K, V> e = bucket;
            while (e != null) {
                Entry<K, V> next = e.next;
                int newIndex = e.hash & (newCapacity - 1);
                e.next = newTable[newIndex];
                newTable[newIndex] = e;
                e = next;
            }
        }

        table = newTable;
        capacity = newCapacity;
        threshold = (int) (capacity * loadFactor);
        resizeCount++;
    }

    private int nextPowerOf2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    // ─── Debug / Display ───
    public String getStats() {
        int maxChain = 0, nonEmpty = 0;
        for (Entry<K, V> bucket : table) {
            int len = 0;
            for (Entry<K, V> e = bucket; e != null; e = e.next) len++;
            if (len > 0) nonEmpty++;
            maxChain = Math.max(maxChain, len);
        }
        return String.format("Size=%d | Capacity=%d | LoadFactor=%.2f | Buckets=%d/%d used | MaxChain=%d | Resizes=%d",
            size, capacity, (float) size / capacity, nonEmpty, capacity, maxChain, resizeCount);
    }

    public void displayBuckets() {
        System.out.println("  Bucket contents:");
        for (int i = 0; i < capacity; i++) {
            if (table[i] != null) {
                StringBuilder sb = new StringBuilder("    [" + i + "]: ");
                for (Entry<K, V> e = table[i]; e != null; e = e.next) {
                    sb.append(e).append(" → ");
                }
                sb.append("null");
                System.out.println(sb);
            }
        }
    }
}

// ==================== Main Demo ====================

public class CustomHashMapSystem {
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  Custom HashMap - Chaining + Dynamic Resize         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        // ── Scenario 1: Basic operations ──
        System.out.println("━━━ Scenario 1: put, get, remove, containsKey ━━━");
        MyHashMap<String, Integer> map = new MyHashMap<>(4);
        map.put("Alice", 25);
        map.put("Bob", 30);
        map.put("Charlie", 35);
        map.put("Diana", 28);

        System.out.println("  get(Alice) = " + map.get("Alice"));     // 25
        System.out.println("  get(Bob) = " + map.get("Bob"));         // 30
        System.out.println("  get(Unknown) = " + map.get("Unknown")); // null
        System.out.println("  containsKey(Charlie) = " + map.containsKey("Charlie")); // true
        System.out.println("  containsKey(Eve) = " + map.containsKey("Eve"));         // false
        System.out.println("  " + map.getStats());
        map.displayBuckets();
        System.out.println();

        // ── Scenario 2: Update existing key ──
        System.out.println("━━━ Scenario 2: Update existing key ━━━");
        Integer old = map.put("Alice", 26);
        System.out.println("  Updated Alice: " + old + " → " + map.get("Alice"));
        System.out.println("  Size still: " + map.size()); // should still be 4
        System.out.println();

        // ── Scenario 3: Remove ──
        System.out.println("━━━ Scenario 3: Remove ━━━");
        Integer removed = map.remove("Bob");
        System.out.println("  Removed Bob: " + removed);
        System.out.println("  get(Bob) after remove: " + map.get("Bob")); // null
        System.out.println("  Size: " + map.size()); // 3
        System.out.println("  Remove non-existent: " + map.remove("Nobody")); // null
        System.out.println();

        // ── Scenario 4: Dynamic resizing ──
        System.out.println("━━━ Scenario 4: Dynamic resizing (watch capacity grow) ━━━");
        MyHashMap<Integer, String> intMap = new MyHashMap<>(4);
        System.out.println("  Initial: " + intMap.getStats());

        for (int i = 1; i <= 20; i++) {
            intMap.put(i, "val-" + i);
            if (i % 5 == 0)
                System.out.println("  After " + i + " puts: " + intMap.getStats());
        }
        System.out.println("  All values present: " + (intMap.size() == 20 ? "✅" : "❌"));
        // Verify all retrievable
        boolean allFound = true;
        for (int i = 1; i <= 20; i++) {
            if (!("val-" + i).equals(intMap.get(i))) { allFound = false; break; }
        }
        System.out.println("  All retrievable: " + (allFound ? "✅" : "❌"));
        System.out.println();

        // ── Scenario 5: Collision handling ──
        System.out.println("━━━ Scenario 5: Forced collisions (same hash bucket) ━━━");
        MyHashMap<CollisionKey, String> collisionMap = new MyHashMap<>(8);
        // All these keys hash to the same bucket
        collisionMap.put(new CollisionKey("A", 5), "Alpha");
        collisionMap.put(new CollisionKey("B", 5), "Beta");
        collisionMap.put(new CollisionKey("C", 5), "Charlie");
        collisionMap.put(new CollisionKey("D", 5), "Delta");

        System.out.println("  get(A) = " + collisionMap.get(new CollisionKey("A", 5)));
        System.out.println("  get(D) = " + collisionMap.get(new CollisionKey("D", 5)));
        System.out.println("  " + collisionMap.getStats()); // MaxChain should be 4
        collisionMap.displayBuckets();
        System.out.println();

        // ── Scenario 6: Null key support ──
        System.out.println("━━━ Scenario 6: Null key support ━━━");
        MyHashMap<String, String> nullMap = new MyHashMap<>();
        nullMap.put(null, "null-value");
        nullMap.put("key1", "value1");
        System.out.println("  get(null) = " + nullMap.get(null));       // null-value
        System.out.println("  get(key1) = " + nullMap.get("key1"));     // value1
        System.out.println("  containsKey(null) = " + nullMap.containsKey(null)); // true
        nullMap.remove(null);
        System.out.println("  After remove(null): get(null) = " + nullMap.get(null)); // null
        System.out.println();

        // ── Scenario 7: keys() and values() ──
        System.out.println("━━━ Scenario 7: Iteration (keys, values) ━━━");
        MyHashMap<String, Integer> iterMap = new MyHashMap<>();
        iterMap.put("X", 1);
        iterMap.put("Y", 2);
        iterMap.put("Z", 3);
        System.out.println("  Keys: " + iterMap.keys());
        System.out.println("  Values: " + iterMap.values());
        System.out.println();

        // ── Scenario 8: Clear ──
        System.out.println("━━━ Scenario 8: Clear ━━━");
        iterMap.clear();
        System.out.println("  After clear: size=" + iterMap.size() + ", isEmpty=" + iterMap.isEmpty());

        System.out.println("\n✅ All HashMap scenarios complete.");
    }
}

// Helper class to force hash collisions
class CollisionKey {
    final String name;
    final int forcedHash;

    CollisionKey(String name, int forcedHash) {
        this.name = name; this.forcedHash = forcedHash;
    }

    @Override
    public int hashCode() { return forcedHash; } // all return same hash!

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollisionKey c)) return false;
        return name.equals(c.name);
    }

    @Override
    public String toString() { return name; }
}
