# LRU Cache - HELLO Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **Get Operation**
   - Retrieve value for a given key
   - Return value if key exists
   - Return null/special value if key doesn't exist
   - Mark key as recently used

2. **Put Operation**
   - Insert key-value pair into cache
   - Update value if key already exists
   - Mark key as recently used
   - Evict least recently used item if cache is full

3. **Fixed Capacity**
   - Cache has maximum capacity
   - Once full, evict LRU item before adding new item

#### Nice to Have (P1)
- Delete/remove specific key
- Clear all entries
- Get cache size and capacity
- Iterator for entries (ordered by recency)
- Time-based expiration (TTL)
- Statistics (hit rate, miss rate)

### Non-Functional Requirements

#### Performance
- **Get**: O(1) time complexity
- **Put**: O(1) time complexity
- **Space**: O(capacity) space complexity

#### Thread Safety
- Support concurrent access from multiple threads
- No race conditions
- No data corruption

#### Memory Efficiency
- Bounded memory usage
- No memory leaks
- Efficient data structure usage

### Constraints and Assumptions

**Constraints**:
- Capacity > 0 (at least 1 item)
- Keys are non-null
- Values can be null (or not, depending on requirements)
- Single machine (no distributed cache)

**Assumptions**:
- Keys are immutable
- Keys implement proper `equals()` and `hashCode()`
- "Recently used" means accessed by either get() or put()
- Recency is based on access order, not insertion order

**Out of Scope**:
- Distributed caching
- Persistent storage
- Different eviction policies (LFU, FIFO, etc.)
- Cache warming strategies

---

## 2️⃣ Core Entities

### Class: LRUCache<K, V>
**Responsibility**: Main cache implementation with LRU eviction

**Key Attributes**:
- `capacity: int` - Maximum number of entries
- `cache: HashMap<K, Node<K, V>>` - Fast O(1) key lookup
- `head: Node<K, V>` - Dummy head of doubly linked list (most recent)
- `tail: Node<K, V>` - Dummy tail of doubly linked list (least recent)

**Key Methods**:
- `get(K key)`: Retrieve value and update recency
- `put(K key, V value)`: Insert/update and manage capacity
- `remove(K key)`: Remove specific entry
- `clear()`: Remove all entries

**Relationships**:
- Contains: HashMap for O(1) lookup
- Contains: Doubly linked list for O(1) reordering
- Uses: Node<K, V> for list nodes

**Data Structure Choice**:
```
HashMap<K, Node<K,V>> + Doubly Linked List = O(1) for both get and put

HashMap provides: O(1) key lookup
Doubly Linked List provides: O(1) add/remove/reorder
```

### Class: Node<K, V>
**Responsibility**: Node in doubly linked list storing cache entry

**Key Attributes**:
- `key: K` - Cache key (needed for eviction)
- `value: V` - Cache value
- `prev: Node<K, V>` - Previous node (more recent)
- `next: Node<K, V>` - Next node (less recent)

**Relationships**:
- Contained by: LRUCache
- Self-reference: prev and next pointers

**Why store key in node?**
When evicting, we need the key to remove from HashMap

### Interface: Cache<K, V> (Optional)
**Responsibility**: Abstract cache interface for multiple implementations

**Key Methods**:
- `get(K key)`: Retrieve value
- `put(K key, V value)`: Store value
- `size()`: Current size
- `capacity()`: Maximum capacity

**Relationships**:
- Implemented by: LRUCache, LFUCache, FIFOCache, etc.
- Allows: Easy swapping of cache implementations

---

## 3️⃣ API Design

### Interface: Cache<K, V>

```java
/**
 * Generic cache interface supporting different eviction policies
 */
public interface Cache<K, V> {
    /**
     * Get value for given key
     * 
     * @param key Cache key
     * @return Value if exists, null otherwise
     * @throws IllegalArgumentException if key is null
     */
    V get(K key);
    
    /**
     * Put key-value pair into cache
     * 
     * @param key Cache key
     * @param value Cache value
     * @throws IllegalArgumentException if key is null
     */
    void put(K key, V value);
    
    /**
     * Remove entry for given key
     * 
     * @param key Key to remove
     * @return Previous value, or null if key didn't exist
     */
    V remove(K key);
    
    /**
     * Clear all entries from cache
     */
    void clear();
    
    /**
     * Get current number of entries
     * 
     * @return Current size
     */
    int size();
    
    /**
     * Get maximum capacity
     * 
     * @return Maximum capacity
     */
    int capacity();
    
    /**
     * Check if cache contains key
     * 
     * @param key Key to check
     * @return true if key exists
     */
    boolean containsKey(K key);
}
```

### Class: LRUCache<K, V>

```java
/**
 * LRU (Least Recently Used) Cache implementation
 * 
 * Provides O(1) get and put operations using HashMap + Doubly Linked List
 * Thread-safe when accessed concurrently
 * 
 * @param <K> Key type - must be non-null, implement equals() and hashCode()
 * @param <V> Value type
 */
public class LRUCache<K, V> implements Cache<K, V> {
    
    /**
     * Create LRU cache with specified capacity
     * 
     * @param capacity Maximum number of entries (must be > 0)
     * @throws IllegalArgumentException if capacity <= 0
     */
    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>(capacity);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }
    
    /**
     * Get value for key, mark as recently used
     * Time: O(1)
     * 
     * @param key Key to look up
     * @return Value if key exists, null otherwise
     */
    @Override
    public V get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        
        Node<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        
        // Move to front (most recently used)
        moveToHead(node);
        return node.value;
    }
    
    /**
     * Put key-value pair, evict LRU if at capacity
     * Time: O(1)
     * 
     * @param key Key to insert/update
     * @param value Value to store
     */
    @Override
    public void put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        
        Node<K, V> node = cache.get(key);
        
        if (node != null) {
            // Update existing
            node.value = value;
            moveToHead(node);
        } else {
            // Insert new
            Node<K, V> newNode = new Node<>(key, value);
            cache.put(key, newNode);
            addToHead(newNode);
            
            if (cache.size() > capacity) {
                // Evict LRU
                Node<K, V> lru = removeTail();
                cache.remove(lru.key);
            }
        }
    }
    
    /**
     * Remove entry for key
     * Time: O(1)
     * 
     * @param key Key to remove
     * @return Previous value, or null if not found
     */
    @Override
    public V remove(K key) {
        if (key == null) {
            return null;
        }
        
        Node<K, V> node = cache.remove(key);
        if (node == null) {
            return null;
        }
        
        removeNode(node);
        return node.value;
    }
    
    @Override
    public void clear() {
        cache.clear();
        head.next = tail;
        tail.prev = head;
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
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }
    
    // Helper methods for doubly linked list manipulation
    private void addToHead(Node<K, V> node) { /* ... */ }
    private void removeNode(Node<K, V> node) { /* ... */ }
    private void moveToHead(Node<K, V> node) { /* ... */ }
    private Node<K, V> removeTail() { /* ... */ }
}
```

### Class: Node<K, V>

```java
/**
 * Node in doubly linked list for LRU ordering
 */
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
```

---

## 4️⃣ Data Flow

### Flow 1: Get (Cache Hit)

**Sequence**:
1. Client calls `get(key)` on LRUCache
2. Look up key in HashMap: O(1)
3. Node found, extract value
4. Remove node from current position in linked list
5. Add node to head (mark as most recently used)
6. Return value to client

**Sequence Diagram**:
```
Client → LRUCache: get("user123")
LRUCache → HashMap: get("user123")
HashMap → LRUCache: Node{key="user123", value="data"}
LRUCache → LRUCache: moveToHead(node)
LRUCache → LRUCache: removeNode(node)
LRUCache → LRUCache: addToHead(node)
LRUCache → Client: "data"
```

**Visual (Linked List)**:
```
Before get("B"):
head ← A ↔ B ↔ C ↔ D → tail
       (most recent)  (LRU)

After get("B"):
head ← B ↔ A ↔ C ↔ D → tail
       (most recent)  (LRU)
```

**Time**: O(1)

### Flow 2: Get (Cache Miss)

**Sequence**:
1. Client calls `get(key)` on LRUCache
2. Look up key in HashMap: O(1)
3. Key not found, return null
4. No list modifications needed

**Sequence Diagram**:
```
Client → LRUCache: get("nonexistent")
LRUCache → HashMap: get("nonexistent")
HashMap → LRUCache: null
LRUCache → Client: null
```

**Time**: O(1)

### Flow 3: Put (Update Existing)

**Sequence**:
1. Client calls `put(key, value)`
2. Look up key in HashMap
3. Node found (key exists)
4. Update node's value
5. Move node to head (mark as most recently used)
6. No eviction needed

**Sequence Diagram**:
```
Client → LRUCache: put("B", "newData")
LRUCache → HashMap: get("B")
HashMap → LRUCache: Node{key="B", value="oldData"}
LRUCache → Node: value = "newData"
LRUCache → LRUCache: moveToHead(node)
LRUCache → Client: void
```

**Visual**:
```
Before put("C", "updated"):
head ← A ↔ B ↔ C ↔ D → tail

After:
head ← C ↔ A ↔ B ↔ D → tail
       (updated value)
```

**Time**: O(1)

### Flow 4: Put (Insert New, No Eviction)

**Sequence**:
1. Client calls `put(key, value)`
2. Look up key in HashMap
3. Key not found (new entry)
4. Create new node
5. Add to HashMap
6. Add node to head of list
7. Check capacity: size <= capacity
8. No eviction needed

**Visual**:
```
Capacity: 4, Current size: 3

Before put("E", "data"):
head ← A ↔ B ↔ C → tail

After:
head ← E ↔ A ↔ B ↔ C → tail
       (new)
```

**Time**: O(1)

### Flow 5: Put (Insert New, With Eviction)

**Sequence**:
1. Client calls `put(key, value)`
2. Key not found (new entry)
3. Create new node
4. Add to HashMap
5. Add node to head of list
6. Check capacity: size > capacity
7. Identify LRU node (tail.prev)
8. Remove LRU node from list
9. Remove LRU key from HashMap
10. Final size = capacity

**Sequence Diagram**:
```
Client → LRUCache: put("E", "data")
LRUCache → HashMap: get("E")
HashMap → LRUCache: null (not found)
LRUCache → LRUCache: newNode = Node("E", "data")
LRUCache → HashMap: put("E", newNode)
LRUCache → LRUCache: addToHead(newNode)
LRUCache → LRUCache: size > capacity?
LRUCache → LRUCache: removeTail() → returns Node("D", ...)
LRUCache → HashMap: remove("D")
LRUCache → Client: void
```

**Visual**:
```
Capacity: 4, Current size: 4

Before put("E", "data"):
head ← A ↔ B ↔ C ↔ D → tail
                   (LRU)

After:
head ← E ↔ A ↔ B ↔ C → tail
       (new)        (new LRU)

D was evicted!
```

**Time**: O(1)

---

## 5️⃣ Design

### Class Diagram

```
┌─────────────────────────────────────┐
│  <<interface>>                      │
│  Cache<K, V>                        │
│  + get(key): V                      │
│  + put(key, value): void            │
│  + remove(key): V                   │
│  + clear(): void                    │
│  + size(): int                      │
│  + capacity(): int                  │
└──────────▲──────────────────────────┘
           │
           │ implements
           │
┌──────────┴──────────────────────────┐
│  LRUCache<K, V>                     │
│  - capacity: int                    │
│  - cache: HashMap<K, Node<K,V>>     │
│  - head: Node<K, V>                 │
│  - tail: Node<K, V>                 │
│                                     │
│  + get(key): V                      │
│  + put(key, value): void            │
│  - addToHead(node): void            │
│  - removeNode(node): void           │
│  - moveToHead(node): void           │
│  - removeTail(): Node<K,V>          │
└─────────────┬───────────────────────┘
              │
              │ contains
              ▼
┌─────────────────────────────────────┐
│  Node<K, V>                         │
│  + key: K                           │
│  + value: V                         │
│  + prev: Node<K, V>                 │
│  + next: Node<K, V>                 │
└─────────────────────────────────────┘

Data Structure Visualization:

HashMap: key → Node
┌─────┬─────────┐
│ "A" │ Node(A) │───┐
├─────┼─────────┤   │
│ "B" │ Node(B) │─┐ │
├─────┼─────────┤ │ │
│ "C" │ Node(C) │ │ │
└─────┴─────────┘ │ │
                  ↓ ↓
Doubly Linked List (LRU order):
dummy                                  dummy
head ↔ Node(A) ↔ Node(B) ↔ Node(C) ↔ tail
       (most)                   (LRU)
```

### Design Patterns

**Pattern: None explicitly, but demonstrates key concepts**

This problem doesn't heavily use traditional design patterns, but demonstrates:

1. **Data Structure Composition**
   - Combining HashMap + Doubly Linked List
   - Each solves a different subproblem
   - Together achieve O(1) performance

2. **Separation of Concerns**
   - HashMap: Fast key lookup
   - Linked List: LRU ordering
   - Node: Data storage

3. **Interface-Based Design** (if using Cache interface)
   - Abstract cache behavior
   - Allow multiple implementations
   - Dependency Inversion Principle

### Why This Data Structure?

**Problem**: Need both O(1) lookup AND O(1) reordering

**Solution 1: HashMap Only**
```
✗ Fast lookup: O(1)
✗ Can't track LRU order
✗ Can't efficiently find LRU item
```

**Solution 2: Doubly Linked List Only**
```
✗ Can track LRU order
✗ Lookup is O(n) - must scan list
✗ Too slow
```

**Solution 3: HashMap + Array**
```
✗ Fast lookup: O(1)
✗ Reordering is O(n) - must shift elements
✗ Too slow
```

**Solution 4: HashMap + Doubly Linked List** ✓
```
✓ Fast lookup: HashMap gives O(1) access to node
✓ Fast reordering: Linked list allows O(1) add/remove
✓ Perfect combination!
```

### Doubly Linked List Operations

**Why doubly linked (not singly)?**
- Need to remove nodes from middle
- Singly linked requires O(n) to find previous
- Doubly linked has prev pointer: O(1)

**Core Operations**:

```java
// Add node right after dummy head (most recent)
private void addToHead(Node<K, V> node) {
    node.prev = head;
    node.next = head.next;
    head.next.prev = node;
    head.next = node;
}

// Remove node from its current position
private void removeNode(Node<K, V> node) {
    node.prev.next = node.next;
    node.next.prev = node.prev;
}

// Move existing node to head
private void moveToHead(Node<K, V> node) {
    removeNode(node);
    addToHead(node);
}

// Remove and return node before dummy tail (LRU)
private Node<K, V> removeTail() {
    Node<K, V> lru = tail.prev;
    removeNode(lru);
    return lru;
}
```

### Thread Safety

**Single-Threaded Version** (as shown above):
- No synchronization
- Not thread-safe
- Fastest performance

**Thread-Safe Version Option 1: Synchronized Methods**:
```java
public synchronized V get(K key) {
    // ... implementation
}

public synchronized void put(K key, V value) {
    // ... implementation
}
```
- Simple to implement
- Coarse-grained locking
- Only one thread at a time
- Can be bottleneck

**Thread-Safe Version Option 2: ReadWriteLock**:
```java
private final ReadWriteLock lock = new ReentrantReadWriteLock();

public V get(K key) {
    lock.readLock().lock();
    try {
        // ... get logic
    } finally {
        lock.readLock().unlock();
    }
}

public void put(K key, V value) {
    lock.writeLock().lock();
    try {
        // ... put logic
    } finally {
        lock.writeLock().unlock();
    }
}
```
- Better for read-heavy workloads
- Multiple readers can proceed concurrently
- Still serializes writes

**Thread-Safe Version Option 3: ConcurrentHashMap + Sync List**:
- Use ConcurrentHashMap for cache
- Synchronize only list operations
- More complex, better concurrency

### Complete Implementation

```java
public class LRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
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
        this.capacity = capacity;
        this.cache = new HashMap<>(capacity);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }
    
    @Override
    public V get(K key) {
        Node<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        moveToHead(node);
        return node.value;
    }
    
    @Override
    public void put(K key, V value) {
        Node<K, V> node = cache.get(key);
        
        if (node != null) {
            node.value = value;
            moveToHead(node);
        } else {
            Node<K, V> newNode = new Node<>(key, value);
            cache.put(key, newNode);
            addToHead(newNode);
            
            if (cache.size() > capacity) {
                Node<K, V> lru = removeTail();
                cache.remove(lru.key);
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
    public V remove(K key) {
        Node<K, V> node = cache.remove(key);
        if (node == null) return null;
        removeNode(node);
        return node.value;
    }
    
    @Override
    public void clear() {
        cache.clear();
        head.next = tail;
        tail.prev = head;
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
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }
}
```

---

## 6️⃣ Deep Dives

### Topic 1: Complexity Analysis

**Time Complexity**:

| Operation | Complexity | Why |
|-----------|------------|-----|
| get(key) | O(1) | HashMap lookup + list reorder (constant pointers) |
| put(key, value) | O(1) | HashMap insert + list operations (constant pointers) |
| remove(key) | O(1) | HashMap delete + list node removal |

**Space Complexity**:
- O(capacity) for HashMap
- O(capacity) for linked list nodes
- Total: O(capacity)

**Proof of O(1)**:
```
get(key):
  1. HashMap.get(key) → O(1)
  2. removeNode(node) → O(1) (4 pointer updates)
  3. addToHead(node) → O(1) (4 pointer updates)
  Total: O(1)

put(key, value):
  1. HashMap.get(key) → O(1)
  2. Either:
     a. Update + moveToHead → O(1)
     b. HashMap.put + addToHead + maybe removeTail → O(1)
  Total: O(1)
```

### Topic 2: Alternative Implementations

**Alternative 1: LinkedHashMap (Java)**
```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    
    public LRUCache(int capacity) {
        super(capacity, 0.75f, true);  // accessOrder = true
        this.capacity = capacity;
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

**Pros**:
- Much simpler code
- Built-in Java solution
- Maintains insertion + access order

**Cons**:
- Less control over implementation
- May not work in all languages
- Interview wants custom implementation

**When to use**:
- Production code (use standard library!)
- Not for interviews (defeats the purpose)

**Alternative 2: TreeMap with Timestamps**
```java
// Each entry has timestamp
class Entry {
    V value;
    long timestamp;
}

Map<K, Entry> cache;
TreeMap<Long, K> timestampIndex;

// On access: update timestamp, reindex
// On eviction: get min timestamp from TreeMap
```

**Pros**:
- No manual linked list management

**Cons**:
- O(log n) operations for TreeMap
- More memory (extra index)
- Worse than O(1) solution

**Alternative 3: Priority Queue**
```java
Map<K, V> cache;
PriorityQueue<Entry<K, timestamp>> pq;
```

**Cons**:
- O(log n) operations
- Updating priority requires removal + re-insertion
- Not suitable for LRU

### Topic 3: Edge Cases

**Edge Case 1: Capacity = 1**
```java
LRUCache<String, String> cache = new LRUCache<>(1);

cache.put("A", "1");  // Cache: [A]
cache.put("B", "2");  // Cache: [B], A evicted
cache.get("B");       // Returns "2"
cache.get("A");       // Returns null
```

**Edge Case 2: Updating Existing Key**
```java
cache.put("A", "1");  // Cache: [A]
cache.put("B", "2");  // Cache: [B, A]
cache.put("A", "3");  // Cache: [A, B] (A moved to front, value updated)
```

**Edge Case 3: Null Values** (if allowed)
```java
cache.put("A", null);  // Should this be allowed?
cache.get("A");        // Returns null - ambiguous with "not found"

// Solution: Use Optional<V> or special sentinel value
```

**Edge Case 4: Concurrent Modification**
```java
// Thread 1
cache.get("A");  // Moves A to head

// Thread 2 (simultaneously)
cache.get("B");  // Moves B to head

// Without synchronization: CORRUPTED linked list!
```

**Edge Case 5: Integer Overflow (Capacity)**
```java
// If using int for capacity
int capacity = Integer.MAX_VALUE;  // 2^31 - 1
// Will cause memory issues before overflow

// Solution: Validate reasonable capacity in constructor
if (capacity > MAX_REASONABLE_CAPACITY) {
    throw new IllegalArgumentException("Capacity too large");
}
```

### Topic 4: Extensibility

**Extension 1: TTL (Time-To-Live)**
```java
class TTLNode<K, V> extends Node<K, V> {
    long expirationTime;
    
    TTLNode(K key, V value, long ttlMillis) {
        super(key, value);
        this.expirationTime = System.currentTimeMillis() + ttlMillis;
    }
    
    boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}

class TTLLRUCache<K, V> extends LRUCache<K, V> {
    @Override
    public V get(K key) {
        TTLNode<K, V> node = (TTLNode<K, V>) cache.get(key);
        if (node == null || node.isExpired()) {
            if (node != null) {
                remove(key);  // Clean up expired
            }
            return null;
        }
        moveToHead(node);
        return node.value;
    }
}
```

**Extension 2: Statistics**
```java
class StatisticalLRUCache<K, V> extends LRUCache<K, V> {
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    
    @Override
    public V get(K key) {
        V value = super.get(key);
        if (value != null) {
            hits.incrementAndGet();
        } else {
            misses.incrementAndGet();
        }
        return value;
    }
    
    public double getHitRate() {
        long totalRequests = hits.get() + misses.get();
        return totalRequests == 0 ? 0.0 : (double) hits.get() / totalRequests;
    }
}
```

**Extension 3: Multiple Eviction Policies**
```java
interface EvictionPolicy<K, V> {
