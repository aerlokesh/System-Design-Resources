# LRU Cache - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [Class Design](#class-design)
5. [Implementation Deep Dive](#implementation-deep-dive)
6. [Complete Code Implementation](#complete-code-implementation)
7. [Extensibility](#extensibility)
8. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is an LRU Cache?**

An LRU (Least Recently Used) Cache is a data structure that stores a fixed number of items and evicts the least recently used item when the cache reaches capacity. It provides O(1) time complexity for both get and put operations by combining a hash map for fast lookups and a doubly linked list for maintaining access order.

**Real-World Use Cases:**
- **Web Browsers:** Caching recently visited pages and resources
- **Operating Systems:** Page replacement in virtual memory management
- **CDNs:** Storing frequently accessed content
- **Databases:** Query result caching (MySQL query cache, Redis)
- **CPU Caches:** L1, L2, L3 cache hierarchies
- **Application Servers:** Session data caching
- **Mobile Apps:** Image and data caching to reduce network calls

**Why LRU?**
- Temporal locality principle: Recently accessed items are likely to be accessed again
- Simple to understand and implement
- Predictable eviction behavior
- Good performance for most access patterns

---

## Requirements

### Functional Requirements

1. **Core Operations**
   - `get(key)`: Retrieve value for a key (returns -1 or null if not found)
   - `put(key, value)`: Insert or update a key-value pair
   - Both operations must be O(1) time complexity

2. **Eviction Policy**
   - When cache reaches capacity, evict the least recently used item
   - Access (get or put) marks an item as recently used
   - Move accessed item to front of recency list

3. **Capacity Management**
   - Cache has fixed maximum capacity
   - Capacity specified at initialization
   - Cannot exceed capacity

4. **Update Behavior**
   - Updating existing key moves it to most recently used position
   - Doesn't count as eviction

### Non-Functional Requirements

1. **Performance**
   - O(1) time complexity for get and put operations
   - Minimal memory overhead
   - Fast eviction (no scanning required)

2. **Thread Safety** (for production systems)
   - Support concurrent access from multiple threads
   - No data corruption under concurrent load
   - Maintain consistency of LRU order

3. **Memory Efficiency**
   - Fixed memory footprint proportional to capacity
   - No memory leaks
   - Efficient use of space

4. **Correctness**
   - Always evict truly least recently used item
   - Maintain correct access order
   - Handle edge cases (empty cache, single item, full cache)

### Out of Scope

- Distributed caching across multiple servers
- Persistent storage (all data in memory)
- Cache warming strategies
- Complex eviction policies (LFU, ARC, etc.) - though we'll discuss extensions
- TTL (Time To Live) based expiration - covered in extensions

---

## Core Entities and Relationships

### Key Entities

1. **LRUCache**
   - Main cache interface and implementation
   - Manages capacity and eviction
   - Coordinates between map and list

2. **Node (CacheNode)**
   - Represents a cache entry in doubly linked list
   - Contains: key, value, prev pointer, next pointer
   - Building block of the linked list

3. **DoublyLinkedList**
   - Maintains access order (most recent to least recent)
   - Supports O(1) add to head, remove from tail, remove arbitrary node
   - Head = most recently used, Tail = least recently used

4. **HashMap**
   - Provides O(1) key lookup
   - Maps key → Node reference
   - Enables fast access to any node

### Data Structure Visualization

```
LRU Cache Structure:

HashMap (Key → Node)              Doubly Linked List (Access Order)
┌─────────────────┐              ┌──────────────────────────────────┐
│ Key  →  Node    │              │  Head                       Tail │
├─────────────────┤              │   ↓                          ↓   │
│ "A"  →  Node1 ──┼──┐           │ [Node1] ↔ [Node2] ↔ [Node3]     │
│ "B"  →  Node2 ──┼──┼──┐        │  (MRU)                     (LRU) │
│ "C"  →  Node3 ──┼──┼──┼──┐     └──────────────────────────────────┘
└─────────────────┘  │  │  │              ▲         ▲         ▲
                     │  │  └──────────────┘         │         │
                     │  └───────────────────────────┘         │
                     └────────────────────────────────────────┘

Get("B"): 
1. HashMap lookup: O(1) - find Node2
2. Remove Node2 from current position: O(1)
3. Add Node2 to head: O(1)
4. Return value

Put("D", value) when full:
1. Remove tail node (Node3): O(1)
2. Remove from HashMap: O(1)
3. Create new Node4: O(1)
4. Add Node4 to head: O(1)
5. Add to HashMap: O(1)
```

### Entity Relationships

```
┌─────────────────────────┐
│      LRUCache           │
│  - capacity: int        │
│  - map: HashMap         │
│  - head: Node           │
│  - tail: Node           │
└───────────┬─────────────┘
            │ contains
            ▼
┌─────────────────────────┐
│   HashMap<Key, Node>    │
│  - O(1) lookup          │
└───────────┬─────────────┘
            │ references
            ▼
┌─────────────────────────┐
│       Node              │
│  - key: K               │
│  - value: V             │
│  - prev: Node           │
│  - next: Node           │
└─────────────────────────┘
            ▲
            │ forms
            ▼
┌─────────────────────────┐
│  Doubly Linked List     │
│  - head → most recent   │
│  - tail → least recent  │
└─────────────────────────┘
```

---

## Class Design

### 1. Cache Node

```java
/**
 * Node in the doubly linked list
 * Represents a single cache entry
 */
class Node<K, V> {
    K key;          // Store key for removal from map during eviction
    V value;        // Cached value
    Node<K, V> prev; // Previous node (more recently used)
    Node<K, V> next; // Next node (less recently used)
    
    public Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
}
```

**Design Decisions:**
- Store both key and value (key needed for map removal during eviction)
- Generic types for flexibility
- Simple POJO structure for clarity
- Mutable fields for O(1) pointer updates

**Why store the key?**
When evicting the tail node, we need to remove it from the HashMap as well. Without storing the key in the node, we'd have to iterate through the entire HashMap to find and remove the entry, making eviction O(n) instead of O(1).

### 2. LRU Cache Interface

```java
/**
 * Generic LRU Cache interface
 * @param <K> Key type
 * @param <V> Value type
 */
interface Cache<K, V> {
    /**
     * Get value for key
     * @param key The key to lookup
     * @return Value if present, null otherwise
     */
    V get(K key);
    
    /**
     * Put key-value pair in cache
     * @param key The key
     * @param value The value
     */
    void put(K key, V value);
    
    /**
     * Get current size of cache
     * @return Number of entries in cache
     */
    int size();
    
    /**
     * Get cache capacity
     * @return Maximum number of entries
     */
    int capacity();
    
    /**
     * Clear all entries from cache
     */
    void clear();
}
```

**Design Decisions:**
- Generic interface for reusability
- Simple API focused on core operations
- Size and capacity methods for monitoring
- Clear method for testing and admin operations

### 3. LRU Cache Implementation

```java
/**
 * LRU Cache implementation using HashMap + Doubly Linked List
 * Time Complexity: O(1) for get and put
 * Space Complexity: O(capacity)
 */
class LRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;  // Dummy head (most recent)
    private final Node<K, V> tail;  // Dummy tail (least recent)
    
    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.map = new HashMap<>();
        
        // Create dummy head and tail for simpler edge case handling
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
        
        // Move to front (most recently used)
        moveToHead(node);
        return node.value;
    }
    
    @Override
    public void put(K key, V value) {
        Node<K, V> node = map.get(key);
        
        if (node != null) {
            // Update existing node
            node.value = value;
            moveToHead(node);
        } else {
            // Create new node
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);
            
            // Check capacity and evict if necessary
            if (map.size() > capacity) {
                Node<K, V> lru = removeTail();
                map.remove(lru.key);
            }
        }
    }
    
    // Helper methods for list manipulation
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
```

**Design Decisions:**
- **Dummy nodes:** Simplify edge cases (empty list, single element)
- **HashMap:** O(1) key lookup
- **Doubly linked list:** O(1) removal and insertion at any position
- **moveToHead:** Central operation for maintaining LRU order
- **Immutable capacity:** Set at construction, never changes

**Why Dummy Nodes?**
Dummy head and tail nodes eliminate special cases:
- No null checks when adding to empty list
- No null checks when removing last element
- Consistent pointer manipulation logic
- Simpler, cleaner code

---

## Implementation Deep Dive

### Operation Walkthrough: get(key)

**Scenario:** Cache with capacity 3, current state:
```
HashMap: {"A": Node1, "B": Node2, "C": Node3}
List: head ↔ Node1(A) ↔ Node2(B) ↔ Node3(C) ↔ tail
              (MRU)                    (LRU)
```

**Operation:** `get("B")`

**Step-by-Step:**
```
Step 1: HashMap lookup O(1)
   map.get("B") → Node2

Step 2: Check if found
   Node2 != null → proceed

Step 3: Move to head (mark as recently used)
   a) Remove from current position:
      head ↔ Node1(A) ↔ Node3(C) ↔ tail
      
   b) Add to head:
      head ↔ Node2(B) ↔ Node1(A) ↔ Node3(C) ↔ tail
      (MRU)                          (LRU)

Step 4: Return value
   return Node2.value

Total Time: O(1)
```

### Operation Walkthrough: put(key, value) - New Entry

**Scenario:** Cache with capacity 3, currently full:
```
HashMap: {"A": Node1, "B": Node2, "C": Node3}
List: head ↔ Node1(A) ↔ Node2(B) ↔ Node3(C) ↔ tail
```

**Operation:** `put("D", 4)`

**Step-by-Step:**
```
Step 1: Check if key exists O(1)
   map.get("D") → null (new entry)

Step 2: Create new node O(1)
   Node4(D, 4)

Step 3: Add to HashMap O(1)
   map.put("D", Node4)

Step 4: Add to head of list O(1)
   head ↔ Node4(D) ↔ Node1(A) ↔ Node2(B) ↔ Node3(C) ↔ tail

Step 5: Check capacity O(1)
   map.size() = 4 > capacity (3)
   Need to evict!

Step 6: Remove LRU (tail node) O(1)
   a) Get tail node:
      lru = Node3(C)
   
   b) Remove from list:
      head ↔ Node4(D) ↔ Node1(A) ↔ Node2(B) ↔ tail
   
   c) Remove from HashMap:
      map.remove("C")

Final State:
HashMap: {"D": Node4, "A": Node1, "B": Node2}
List: head ↔ Node4(D) ↔ Node1(A) ↔ Node2(B) ↔ tail
           (MRU)                      (LRU)

Total Time: O(1)
```

### Operation Walkthrough: put(key, value) - Update Existing

**Scenario:** Cache state:
```
HashMap: {"A": Node1, "B": Node2, "C": Node3}
List: head ↔ Node1(A) ↔ Node2(B) ↔ Node3(C) ↔ tail
```

**Operation:** `put("B", 20)` (update existing)

**Step-by-Step:**
```
Step 1: Check if key exists O(1)
   map.get("B") → Node2 (exists!)

Step 2: Update value O(1)
   Node2.value = 20

Step 3: Move to head O(1)
   a) Remove from current position:
      head ↔ Node1(A) ↔ Node3(C) ↔ tail
   
   b) Add to head:
      head ↔ Node2(B) ↔ Node1(A) ↔ Node3(C) ↔ tail

Step 4: No eviction needed
   map.size() = 3 ≤ capacity (3)

Final State:
HashMap: {"B": Node2, "A": Node1, "C": Node3}
List: head ↔ Node2(B) ↔ Node1(A) ↔ Node3(C) ↔ tail
           (MRU)                      (LRU)

Total Time: O(1)
```

### Why O(1) Complexity?

**HashMap Operations:**
- `get(key)`: O(1) average case
- `put(key, value)`: O(1) average case
- `remove(key)`: O(1) average case

**Linked List Operations:**
- Add to head: O(1) with direct pointer
- Remove node: O(1) with direct pointer
- Remove tail: O(1) with tail pointer

**Key Insight:**
We always have direct pointers to the nodes we need to manipulate:
- HashMap gives us direct Node pointer for any key
- Tail pointer gives us direct access to LRU node
- Doubly linked list allows O(1) removal with node pointer

---

## Complete Code Implementation

### Basic LRU Cache (Thread-Unsafe)

```java
/**
 * Complete LRU Cache implementation
 * Thread-unsafe version (for single-threaded use)
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
```

### Thread-Safe LRU Cache

```java
/**
 * Thread-safe LRU Cache using synchronized methods
 * Simple but may have contention under high concurrency
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
```

### High-Performance Thread-Safe LRU Cache (ReadWriteLock)

```java
/**
 * Thread-safe LRU Cache using ReadWriteLock
 * Better performance: multiple concurrent reads, single write
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
        writeLock.lock();  // Need write lock because we modify list order
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
```

### Usage Examples

```java
public class LRUCacheDemo {
    public static void main(String[] args) {
        // Example 1: Basic usage
        basicUsage();
        
        // Example 2: LeetCode-style problem
        leetCodeExample();
        
        // Example 3: Real-world scenario
        realWorldScenario();
    }
    
    private static void basicUsage() {
        System.out.println("=== Basic Usage ===");
        Cache<Integer, String> cache = new LRUCache<>(3);
        
        cache.put(1, "One");
        cache.put(2, "Two");
        cache.put(3, "Three");
        System.out.println("After adding 1,2,3: " + cache);
        // Output: [3=Three, 2=Two, 1=One]
        
        cache.get(1);  // Access 1 (moves to front)
        System.out.println("After get(1): " + cache);
        // Output: [1=One, 3=Three, 2=Two]
        
        cache.put(4, "Four");  // Evicts 2 (LRU)
        System.out.println("After put(4): " + cache);
        // Output: [4=Four, 1=One, 3=Three]
    }
    
    private static void leetCodeExample() {
        System.out.println("\n=== LeetCode Example ===");
        LRUCache<Integer, Integer> cache = new LRUCache<>(2);
        
        cache.put(1, 1);
        cache.put(2, 2);
        System.out.println("get(1): " + cache.get(1));  // returns 1
        cache.put(3, 3);  // evicts key 2
        System.out.println("get(2): " + cache.get(2));  // returns null
        cache.put(4, 4);  // evicts key 1
        System.out.println("get(1): " + cache.get(1));  // returns null
        System.out.println("get(3): " + cache.get(3));  // returns 3
        System.out.println("get(4): " + cache.get(4));  // returns 4
    }
    
    private static void realWorldScenario() {
        System.out.println("\n=== Real World: API Response Cache ===");
        Cache<String, String> apiCache = new LRUCache<>(100);
        
        // Simulate API calls
        String response1 = fetchFromAPI("/users/123", apiCache);
        String response2 = fetchFromAPI("/users/123", apiCache);  // Cache hit!
        String response3 = fetchFromAPI("/products/456", apiCache);
        
        System.out.println("First call: " + response1);
        System.out.println("Second call (cached): " + response2);
    }
    
    private static String fetchFromAPI(String endpoint, Cache<String, String> cache) {
        // Check cache first
        String cached = cache.get(endpoint);
        if (cached != null) {
            System.out.println("Cache HIT for: " + endpoint);
            return cached;
        }
        
        // Cache miss - fetch from API
        System.out.println("Cache MISS for: " + endpoint);
        String response = "Response for " + endpoint;  // Simulate API call
        cache.put(endpoint, response);
        return response;
    }
}
```

---

## Extensibility

### 1. LRU Cache with TTL (Time To Live)

**Question:** "How would you add TTL support so entries expire after a certain time?"

**Answer:**
```java
/**
 * LRU Cache with TTL support
 * Entries expire after specified TTL
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
        long expiryTime;  // When this entry expires
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
        
        // Check if expired
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
            node.expiryTime = expiryTime;  // Reset TTL
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
    
    // Helper methods omitted for brevity (same as base implementation)
    private void addToHead(Node<K, V> node) { /* ... */ }
    private void removeNode(Node<K, V> node) { /* ... */ }
    private void moveToHead(Node<K, V> node) { /* ... */ }
    private Node<K, V> removeTail() { /* ... */ }
    
    @Override
    public int size() { return map.size(); }
    @Override
    public int capacity() { return capacity; }
    @Override
    public void clear() {
        map.clear();
        head.next = tail;
        tail.prev = head;
    }
}
```

**Key Features:**
- Each node stores expiry time
- Lazy eviction on get (check if expired)
- TTL resets on update
- Optional background cleaner thread

**Enhancement: Background Cleaner**
```java
class TTLLRUCacheWithCleaner<K, V> extends TTLLRUCache<K, V> {
    private final ScheduledExecutorService cleaner;
    
    public TTLLRUCacheWithCleaner(int capacity, long ttlMillis) {
        super(capacity, ttlMillis);
        this.cleaner = Executors.newScheduledThreadPool(1);
        startCleaner();
    }
    
    private void startCleaner() {
        cleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            List<K> expiredKeys = new ArrayList<>();
            
            // Find expired entries
            for (Map.Entry<K, Node<K, V>> entry : map.entrySet()) {
                if (now > entry.getValue().expiryTime) {
                    expiredKeys.add(entry.getKey());
                }
            }
            
            // Remove expired entries
            for (K key : expiredKeys) {
                Node<K, V> node = map.remove(key);
                if (node != null) {
                    removeNode(node);
                }
            }
        }, ttlMillis, ttlMillis, TimeUnit.MILLISECONDS);
    }
    
    public void shutdown() {
        cleaner.shutdown();
    }
}
```

### 2. LFU (Least Frequently Used) Cache

**Question:** "How would you implement LFU instead of LRU?"

**Answer:**
```java
/**
 * LFU Cache - evicts least frequently used item
 * Uses frequency counter for each entry
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
                // Evict least frequently used
                DoubleLinkedList<K, V> minFreqList = frequencyMap.get(minFrequency);
                Node<K, V> nodeToRemove = minFreqList.removeTail();
                cache.remove(nodeToRemove.key);
            }
            
            // Add new node
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
        
        // Update minFrequency if needed
        if (freq == minFrequency && list.size == 0) {
            minFrequency++;
        }
        
        // Move to next frequency list
        node.frequency++;
        DoubleLinkedList<K, V> newList = frequencyMap.computeIfAbsent(
            node.frequency, k -> new DoubleLinkedList<>());
        newList.addToHead(node);
    }
    
    @Override
    public int size() { return cache.size(); }
    @Override
    public int capacity() { return capacity; }
    @Override
    public void clear() {
        cache.clear();
        frequencyMap.clear();
        minFrequency = 0;
    }
}
```

**LFU vs LRU:**
- **LFU:** Evicts item with lowest access count
- **LRU:** Evicts item accessed longest ago
- **LFU Pros:** Better for repeated access patterns
- **LFU Cons:** More complex, higher memory overhead

### 3. Two-Level Cache (L1 + L2)

**Question:** "How would you implement a two-level cache system?"

**Answer:**
```java
/**
 * Two-level cache: Fast L1 cache backed by larger L2 cache
 * L1: Small, fast (in-memory LRU)
 * L2: Larger, slower (could be Redis, disk, etc.)
 */
class TwoLevelCache<K, V> implements Cache<K, V> {
    private final Cache<K, V> l1Cache;  // Fast, small
    private final Cache<K, V> l2Cache;  // Slower, larger
    
    public TwoLevelCache(int l1Capacity, int l2Capacity) {
        this.l1Cache = new LRUCache<>(l1Capacity);
        this.l2Cache = new LRUCache<>(l2Capacity);
    }
    
    @Override
    public V get(K key) {
        // Try L1 first
        V value = l1Cache.get(key);
        if (value != null) {
            System.out.println("L1 Hit: " + key);
            return value;
        }
        
        // Try L2
        value = l2Cache.get(key);
        if (value != null) {
            System.out.println("L2 Hit: " + key);
            // Promote to L1
            l1Cache.put(key, value);
            return value;
        }
        
        System.out.println("Cache Miss: " + key);
        return null;
    }
    
    @Override
    public void put(K key, V value) {
        // Write to both caches
        l1Cache.put(key, value);
        l2Cache.put(key, value);
    }
    
    @Override
    public int size() {
        return l1Cache.size();  // L1 size
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
    
    public CacheStats getStats() {
        return new CacheStats(l1Cache.size(), l2Cache.size());
    }
    
    private static class CacheStats {
        final int l1Size;
        final int l2Size;
        
        CacheStats(int l1Size, int l2Size) {
            this.l1Size = l1Size;
            this.l2Size = l2Size;
        }
        
        @Override
        public String toString() {
            return String.format("L1: %d, L2: %d", l1Size, l2Size);
        }
    }
}
```

**Benefits:**
- Fast access for hot data (L1)
- Larger capacity (L2)
- Automatic promotion/demotion
- Common in CPU caches, database systems

### 4. Cache with Eviction Callback

**Question:** "How would you notify when an entry is evicted?"

**Answer:**
```java
/**
 * LRU Cache with eviction callback
 * Useful for cleanup, metrics, logging
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
        SIZE,      // Evicted due to capacity
        EXPIRED,   // Evicted due to TTL
        EXPLICIT   // Explicitly removed
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
            V oldValue = node.value;
            node.value = value;
            moveToHead(node);
            
            // Notify update (optional)
            if (evictionListener != null && !oldValue.equals(value)) {
                evictionListener.onEviction(key, oldValue, EvictionCause.EXPLICIT);
            }
        } else {
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);
            
            if (map.size() > capacity) {
                Node<K, V> lru = removeTail();
                map.remove(lru.key);
                
                // Notify eviction
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
    public int size() { return map.size(); }
    @Override
    public int capacity() { return capacity; }
    @Override
    public void clear() {
        map.clear();
        head.next = tail;
        tail.prev = head;
    }
}

// Usage:
LRUCacheWithCallback<String, String> cache = new LRUCacheWithCallback<>(3, 
    (key, value, cause) -> {
        System.out.println("Evicted: " + key + " = " + value + " (" + cause + ")");
    }
);
```

### 5. Cache with Statistics

**Question:** "How would you add metrics tracking?"

**Answer:**
```java
/**
 * LRU Cache with comprehensive statistics
 */
class InstrumentedLRUCache<K, V> implements Cache<K, V> {
    private final LRUCache<K, V> cache;
    private final CacheStatistics stats;
    
    private static class CacheStatistics {
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong puts = new AtomicLong(0);
        private final AtomicLong evictions = new AtomicLong(0);
        
        public void recordHit() { hits.incrementAndGet(); }
        public void recordMiss() { misses.incrementAndGet(); }
        public void recordPut() { puts.incrementAndGet(); }
        public void recordEviction() { evictions.incrementAndGet(); }
        
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
        
        // Check if eviction occurred
        if (sizeBefore == cache.capacity() && cache.size() == cache.capacity()) {
            stats.recordEviction();
        }
    }
    
    @Override
    public int size() { return cache.size(); }
    @Override
    public int capacity() { return cache.capacity(); }
    @Override
    public void clear() { cache.clear(); }
    
    public CacheStatistics getStatistics() {
        return stats;
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Start with Clarifying Questions (2-3 minutes)**
- What are the capacity constraints?
- Thread safety required?
- Any special requirements (TTL, metrics, etc.)?
- What should happen on cache miss?
- Performance requirements?

**2. Explain the Core Insight (2 minutes)**
"To achieve O(1) for both get and put, we need:
- HashMap for O(1) key lookup
- Doubly linked list for O(1) removal/insertion
- Dummy head/tail to simplify edge cases"

**3. Start with Basic Implementation (10-15 minutes)**
- Define Node class first
- Implement helper methods (addToHead, removeNode, etc.)
- Implement get() and put()
- Test with examples

**4. Discuss Extensions (5 minutes)**
- Thread safety approaches
- TTL support
- Eviction callbacks
- Metrics/monitoring

### Common Interview Questions & Answers

**Q1: "Why use doubly linked list instead of singly linked list?"**

**A:** Doubly linked list allows O(1) removal of arbitrary nodes:
- To remove a node, we need to update prev.next and next.prev
- With singly linked list, we'd need O(n) to find the previous node
- For LRU, we need to remove nodes from middle (when accessed)

**Q2: "Why store key in the Node?"**

**A:** When evicting the tail node:
```java
Node<K, V> lru = removeTail();  // Remove from list
map.remove(lru.key);            // Remove from map - need the key!
```
Without the key in Node, we'd have to iterate HashMap to find and remove it (O(n)).

**Q3: "What's the space complexity?"**

**A:** O(capacity)
- HashMap: O(capacity) entries
- Linked list: O(capacity) nodes
- Each node stores key, value, and 2 pointers
- Total: O(capacity) space

**Q4: "How would you make it thread-safe?"**

**A:** Three approaches:

1. **Synchronized methods** (Simple)
```java
public synchronized V get(K key) { ... }
public synchronized void put(K key, V value) { ... }
```
Pros: Simple, correct
Cons: Contention under high concurrency

2. **ReadWriteLock** (Better)
```java
private final ReadWriteLock lock = new ReentrantReadWriteLock();
// But note: get() modifies list order, so needs write lock
```

3. **Lock-free with ConcurrentHashMap** (Complex)
```java
private final ConcurrentHashMap<K, Node<K, V>> map;
// But list operations still need synchronization
```

**Best approach:** Start with synchronized, optimize if needed.

**Q5: "How does this compare to LinkedHashMap?"**

**A:** Java's LinkedHashMap provides LRU built-in:
```java
Map<K, V> cache = new LinkedHashMap<K, V>(capacity, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > capacity;
    }
};
```

**Differences:**
- LinkedHashMap is more feature-rich
- Our implementation shows understanding of data structures
- Custom implementation allows more control

**Q6: "What if you need to support both LRU and LFU?"**

**A:** Use Strategy pattern:
```java
interface EvictionPolicy<K, V> {
    void recordAccess(K key);
    K selectVictim();
}

class LRUPolicy<K, V> implements EvictionPolicy<K, V> { ... }
class LFUPolicy<K, V> implements EvictionPolicy<K, V> { ... }

class Cache<K, V> {
    private final EvictionPolicy<K, V> policy;
    
    public Cache(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }
}
```

### Common Mistakes to Avoid

❌ **Mistake 1:** Not storing key in Node
```java
class Node<V> {
    V value;  // ❌ Missing key!
    // Can't remove from HashMap during eviction
}
```

❌ **Mistake 2:** Using singly linked list
```java
// ❌ O(n) removal because we can't access prev
void remove(Node node) {
    // Need to find previous node - O(n)
}
```

❌ **Mistake 3:** Forgetting to move node on get()
```java
public V get(K key) {
    Node node = map.get(key);
    return node == null ? null : node.value;  // ❌ Didn't move to head!
}
```

❌ **Mistake 4:** Not using dummy nodes
```java
// ❌ Complicated null checks
if (head == null) {
    head = newNode;
} else {
    // ...
}
```

✅ **Correct:** Always use dummy head/tail

❌ **Mistake 5:** Checking capacity incorrectly
```java
if (map.size() >= capacity) {  // ❌ Wrong condition
    evict();
}
```

✅ **Correct:**
```java
if (map.size() > capacity) {  // After adding new entry
    evict();
}
```

### Expected Level Performance

**Junior Engineer:**
- Understand the problem
- Implement basic LRU with guidance
- Explain O(1) complexity
- Handle basic test cases

**Mid-Level Engineer:**
- Clean implementation with dummy nodes
- Proper edge case handling
- Thread safety discussion
- Test complex scenarios
- Compare with alternatives

**Senior Engineer:**
- Optimal implementation
- Multiple eviction strategies
- Production considerations (metrics, monitoring)
- Trade-off analysis
- Extension proposals (TTL, multi-level, etc.)

### Code Quality Checklist

✅ **Structure**
- Clear class and method names
- Proper encapsulation
- Helper methods for repeated logic

✅ **Edge Cases**
- Empty cache
- Single element
- Full cache
- Update existing key
- Capacity = 1

✅ **Correctness**
- Always maintains LRU order
- No memory leaks
- Proper capacity management

✅ **Performance**
- O(1) get and put
- No unnecessary operations
- Efficient memory usage

✅ **Documentation**
- Clear comments on tricky parts
- Explain dummy node approach
- Document complexity

### Testing Strategy

```java
@Test
public void testBasicOperations() {
    LRUCache<Integer, String> cache = new LRUCache<>(2);
    
    // Test put
    cache.put(1, "one");
    cache.put(2, "two");
    assertEquals(2, cache.size());
    
    // Test get
    assertEquals("one", cache.get(1));
    assertNull(cache.get(3));
    
    // Test eviction
    cache.put(3, "three");  // Evicts 2
    assertNull(cache.get(2));
    assertEquals("three", cache.get(3));
}

@Test
public void testLRUOrder() {
    LRUCache<Integer, String> cache = new LRUCache<>(2);
    
    cache.put(1, "one");
    cache.put(2, "two");
    cache.get(1);  // Access 1, making 2 LRU
    cache.put(3, "three");  // Should evict 2
    
    assertEquals("one", cache.get(1));
    assertNull(cache.get(2));  // 2 was evicted
    assertEquals("three", cache.get(3));
}

@Test
public void testUpdateExisting() {
    LRUCache<Integer, String> cache = new LRUCache<>(2);
    
    cache.put(1, "one");
    cache.put(1, "ONE");  // Update
    
    assertEquals("ONE", cache.get(1));
    assertEquals(1, cache.size());  // Still size 1
}

@Test
public void testCapacityOne() {
    LRUCache<Integer, String> cache = new LRUCache<>(1);
    
    cache.put(1, "one");
    cache.put(2, "two");  // Evicts 1
    
    assertNull(cache.get(1));
    assertEquals("two", cache.get(2));
}
```

---

## Summary

This LRU Cache Low-Level Design demonstrates:

1. **Optimal Data Structure:** HashMap + Doubly Linked List for O(1) operations
2. **Clean Design:** Dummy nodes, helper methods, clear abstractions
3. **Extensibility:** Support for TTL, LFU, callbacks, metrics
4. **Thread Safety:** Multiple approaches (synchronized, ReadWriteLock)
5. **Production Ready:** Metrics, monitoring, eviction callbacks

**Key Takeaways:**
- Understand why HashMap + DLL achieves O(1)
- Store key in Node for O(1) eviction
- Use dummy nodes to simplify edge cases
- Consider thread safety for production
- Know the trade-offs (LRU vs LFU vs others)

**Algorithm Comparison:**

| Eviction Policy | Complexity | Best For | Memory |
|----------------|------------|----------|--------|
| LRU | O(1) | General purpose, temporal locality | O(n) |
| LFU | O(1)* | Repeated access patterns | O(n) |
| FIFO | O(1) | Simple, no access tracking needed | O(n) |
| Random | O(1) | When no pattern exists | O(n) |

*LFU with proper data structures

**Related Topics:**
- System Design: [Distributed Cache HLD](../HLD/distributed-cache-system-design-HLD.md)
- Implementation: [LRUCacheSystem.java](./LRUCacheSystem.java)
- Related: [Rate Limiter LLD](./rate-limiter-LLD.md) (similar data structures)

---

*Last Updated: January 11, 2026*
*Difficulty Level: Medium*
*Interview Success Rate: High with proper preparation*
*LeetCode Problem: #146 LRU Cache*
