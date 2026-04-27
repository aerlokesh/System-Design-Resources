# 🧩 DSA / Algorithm Interview Template — Microsoft

> **Time**: 45 minutes | **Language**: Java (or your preference)
> **Goal**: Demonstrate structured problem-solving, not just "get to the answer"

---

## ⏱️ Timeline (45 min)

| Phase | Time | What You Do |
|-------|------|-------------|
| **1. Understand & Clarify** | 5 min | Repeat problem, ask clarifying questions |
| **2. Pattern Match** | 3 min | Identify the pattern / data structure |
| **3. Approach & Walkthrough** | 5 min | Explain approach, dry-run on example |
| **4. Code** | 20 min | Write clean, correct code |
| **5. Test & Edge Cases** | 7 min | Walk through test cases, fix bugs |
| **6. Complexity & Follow-ups** | 5 min | State T/S complexity, discuss optimizations |

---

## Phase 1️⃣ — Understand & Ask Clarifying Questions (5 min)

> **Say**: "Before I start coding, let me make sure I fully understand the problem."

### 🔑 Clarifying Questions Checklist

#### Input / Output
- [ ] What is the **input format**? (array, string, tree, graph, matrix?)
- [ ] What is the **expected output**? (return value, print, modify in-place?)
- [ ] What are the **input constraints**? (size N, value range, sorted?)

#### Edge Cases
- [ ] Can the input be **empty or null**?
- [ ] Can there be **duplicates**?
- [ ] Can values be **negative**?
- [ ] What if there's **no valid answer**? (return -1, empty list, throw?)

#### Scale & Performance
- [ ] What is the **expected size of input**? (tells you if O(n²) is ok or need O(n log n))
- [ ] Are there **memory constraints**? (can I use extra space?)

#### Assumptions
- [ ] Can I **modify the input** in-place?
- [ ] Is the input **sorted** or do I need to sort it?
- [ ] Are there **concurrent** calls to this function? (thread-safety needed?)

### 📝 Template Statements After Clarifying

```
"So to summarize:
 - Input: [describe]
 - Output: [describe]
 - Constraints: N up to [X], values in range [Y]
 - Edge cases: I'll handle [empty input, no solution, duplicates]
 - I'll assume [sorted/unsorted, no concurrent access unless asked]"
```

---

## Phase 2️⃣ — Pattern Matching (3 min)

> **Say**: "Let me think about which pattern fits here..."

### 🧠 Pattern Recognition Cheat Sheet

| If You See... | Think Pattern | Common DS |
|--------------|--------------|-----------|
| "Subarray sum", "contiguous" | **Sliding Window** / Prefix Sum | Array, HashMap |
| "k-th largest/smallest" | **Heap** / QuickSelect | PriorityQueue |
| "Sorted array, find target" | **Binary Search** | Array |
| "All permutations/combinations" | **Backtracking** | Recursion + choices |
| "Shortest path, fewest steps" | **BFS** | Queue |
| "Connected components, cycles" | **DFS / Union-Find** | Stack, Graph |
| "Optimal substructure + overlapping" | **Dynamic Programming** | Array/HashMap memo |
| "Interval merge/overlap" | **Sorting + Sweep** | Intervals |
| "Frequency, anagram, duplicates" | **HashMap / Counting** | HashMap |
| "Next greater/smaller element" | **Monotonic Stack** | Stack |
| "Top K", "K most frequent" | **Heap** or **Bucket Sort** | PriorityQueue |
| "LinkedList manipulation" | **Two Pointers** (slow/fast) | LinkedList |
| "Tree path/depth/ancestor" | **DFS / Recursion** | Tree |
| "Trie/prefix matching" | **Trie** | Trie |
| "String matching" | **Two Pointers / Sliding Window** | String |
| "Design a data structure" | **HashMap + LinkedList** (LRU) | Composite |
| "Minimize/maximize with constraint" | **Binary Search on Answer** | Array |

### 📝 Template Statement

```
"I see [pattern indicator], so I think this is a [PATTERN NAME] problem.
 My approach will be:
 1. [Step 1]
 2. [Step 2]
 3. [Step 3]
 Time: O(___), Space: O(___)"
```

---

## Phase 3️⃣ — Approach & Dry Run (5 min)

> **Say**: "Let me walk through my approach with this example before coding."

### Steps
1. **State the algorithm in plain English** (2-3 sentences)
2. **Dry-run on the given example** step by step
3. **Confirm with interviewer**: "Does this approach sound good?"

### 📝 Approach Template

```
"My approach:
 1. I'll use a [HashMap / two pointers / BFS / etc.]
 2. First, I'll [initialize/preprocess]
 3. Then, I'll [iterate/recurse] doing [key operation]
 4. Finally, I'll [return/collect result]
 
 Let me trace through the example:
   Input: [example]
   Step 1: [trace]
   Step 2: [trace]
   Output: [expected result] ✓
   
 Time Complexity: O(___) because [reason]
 Space Complexity: O(___) because [reason]"
```

---

## Phase 4️⃣ — Code (20 min)

> **Key principle**: Write **clean, readable, correct** code. Not clever, not minimal — READABLE.

### 🏗️ Code Structure Template (Java)

```java
import java.util.*;

class Solution {

    /**
     * [Brief description of what this method does]
     * 
     * Pattern: [Sliding Window / BFS / DP / etc.]
     * Time:  O(___) 
     * Space: O(___)
     *
     * @param input  description of input
     * @return       description of output
     * @throws IllegalArgumentException if input is invalid
     */
    public ReturnType solve(InputType input) {
        // ========== Step 0: Input Validation ==========
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        if (input.length == 0) {
            return defaultValue; // or throw, or empty result — clarify!
        }

        // ========== Step 1: Initialize ==========
        // Declare data structures, variables
        
        // ========== Step 2: Core Logic ==========
        // Main algorithm loop/recursion
        
        // ========== Step 3: Return Result ==========
        return result;
    }
}
```

### ✅ Coding Best Practices for Microsoft

| Do This | Not This |
|---------|----------|
| Use descriptive variable names: `leftPointer`, `windowSum` | `l`, `s` |
| Add brief inline comments for key steps | No comments at all |
| Handle edge cases with clear checks at the top | Assume happy path |
| Use helper methods for complex sub-problems | One giant method |
| Use `final` for constants | Magic numbers |
| Throw meaningful exceptions | Return -1 silently |

---

## Phase 5️⃣ — Exception Handling & Concurrency (7 min)

### 🚨 Exception Handling Template

```java
public ReturnType solve(InputType input) {
    // ===== Input Validation (throw early) =====
    if (input == null) {
        throw new IllegalArgumentException("Input cannot be null");
    }
    if (input.length == 0) {
        throw new IllegalArgumentException("Input cannot be empty");
    }
    if (k < 0 || k > input.length) {
        throw new IllegalArgumentException(
            "k must be between 0 and " + input.length + ", got: " + k);
    }

    // ===== Boundary checks inside loops =====
    try {
        // core logic
    } catch (ArithmeticException e) {
        // handle overflow: e.g., use long instead of int
        throw new ArithmeticException("Integer overflow in computation");
    }
    
    // ===== No valid answer case =====
    // Option A: Return sentinel
    return -1; // "As discussed, returning -1 if no valid answer"
    
    // Option B: Return Optional
    return Optional.empty();
    
    // Option C: Throw  
    throw new NoSuchElementException("No valid solution found");
}
```

### 🧵 Concurrency Template (If Asked)

> **When to discuss**: If interviewer says "What if this is called concurrently?" or "Make it thread-safe"

```java
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

class ThreadSafeSolution {
    
    // ===== Option 1: synchronized (simplest) =====
    private final Object lock = new Object();
    
    public synchronized ReturnType solve(InputType input) {
        // entire method is thread-safe
    }
    
    // ===== Option 2: ReentrantLock (more control) =====
    private final ReentrantLock lock = new ReentrantLock();
    
    public ReturnType solve(InputType input) {
        lock.lock();
        try {
            // critical section
            return result;
        } finally {
            lock.unlock(); // ALWAYS in finally
        }
    }
    
    // ===== Option 3: ReadWriteLock (reads >> writes) =====
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public ReturnType read(InputType input) {
        rwLock.readLock().lock();
        try {
            return result; // multiple readers allowed
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    public void write(InputType input) {
        rwLock.writeLock().lock();
        try {
            // exclusive access
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    // ===== Option 4: ConcurrentHashMap (for shared maps) =====
    private final ConcurrentHashMap<Key, Value> map = new ConcurrentHashMap<>();
    
    // ===== Option 5: AtomicInteger (for counters) =====
    private final AtomicInteger counter = new AtomicInteger(0);
    
    // ===== Option 6: volatile (for flags) =====
    private volatile boolean isRunning = true;
}
```

### 📊 Java Collections — Time Complexity & Operations Reference

> **Know this cold**: Interviewers expect you to justify your data structure choice with time complexity.

#### 🗺️ Map Implementations

| Operation | `HashMap` | `LinkedHashMap` | `TreeMap` | `ConcurrentHashMap` | `ConcurrentSkipListMap` |
|-----------|----------|----------------|----------|--------------------|-----------------------|
| `put(K, V)` | **O(1)** avg | **O(1)** avg | **O(log N)** | **O(1)** avg | **O(log N)** |
| `get(K)` | **O(1)** avg | **O(1)** avg | **O(log N)** | **O(1)** avg | **O(log N)** |
| `remove(K)` | **O(1)** avg | **O(1)** avg | **O(log N)** | **O(1)** avg | **O(log N)** |
| `containsKey(K)` | **O(1)** avg | **O(1)** avg | **O(log N)** | **O(1)** avg | **O(log N)** |
| `containsValue(V)` | **O(N)** | **O(N)** | **O(N)** | **O(N)** | **O(N)** |
| Iteration order | ❌ None | ✅ Insertion order | ✅ Sorted (natural/comparator) | ❌ None | ✅ Sorted |
| Null keys | ✅ 1 null key | ✅ 1 null key | ❌ No (if using Comparator) | ❌ No | ❌ No |
| Null values | ✅ Yes | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| Thread-safe | ❌ No | ❌ No | ❌ No | ✅ Yes | ✅ Yes |

**Key Operations on Map**:
```java
Map<String, Integer> map = new HashMap<>();
map.put("key", 1);                    // Insert/update
map.get("key");                       // Get value (null if missing)
map.getOrDefault("key", 0);          // Get with default
map.containsKey("key");              // Check key exists
map.containsValue(1);               // Check value exists (O(N)!)
map.remove("key");                   // Remove by key
map.size();                          // Number of entries
map.isEmpty();                       // Check empty
map.keySet();                        // Set of keys
map.values();                        // Collection of values
map.entrySet();                      // Set of Map.Entry<K,V>
map.putIfAbsent("key", 1);          // Insert only if key missing
map.merge("key", 1, Integer::sum);  // Atomic merge (great for counting!)
map.compute("key", (k, v) -> v + 1); // Atomic compute
map.forEach((k, v) -> ...);         // Iterate
```

#### 📋 List Implementations

| Operation | `ArrayList` | `LinkedList` | `CopyOnWriteArrayList` | `Vector` (legacy) |
|-----------|------------|-------------|----------------------|------------------|
| `get(index)` | **O(1)** ⭐ | **O(N)** | **O(1)** | **O(1)** |
| `add(E)` (end) | **O(1)** amortized | **O(1)** | **O(N)** (copies array) | **O(1)** amortized |
| `add(index, E)` | **O(N)** (shift) | **O(N)** (traverse) | **O(N)** | **O(N)** |
| `remove(index)` | **O(N)** (shift) | **O(N)** (traverse) | **O(N)** | **O(N)** |
| `contains(E)` | **O(N)** | **O(N)** | **O(N)** | **O(N)** |
| `indexOf(E)` | **O(N)** | **O(N)** | **O(N)** | **O(N)** |
| `set(index, E)` | **O(1)** | **O(N)** | **O(N)** | **O(1)** |
| Iterator safety | ❌ Fail-fast | ❌ Fail-fast | ✅ Snapshot (safe) | ❌ Fail-fast |
| Thread-safe | ❌ No | ❌ No | ✅ Yes | ✅ Yes (legacy) |
| Best for | Random access, iteration | Frequent add/remove at ends | Read-heavy concurrent | ❌ Don't use |

**Key Operations on List**:
```java
List<String> list = new ArrayList<>();
list.add("item");                     // Append to end — O(1)
list.add(0, "first");                // Insert at index — O(N)
list.get(0);                         // Access by index — O(1) ArrayList, O(N) LinkedList
list.set(0, "updated");              // Replace at index — O(1)
list.remove(0);                      // Remove by index — O(N)
list.remove("item");                 // Remove first occurrence — O(N)
list.contains("item");              // Search — O(N)
list.indexOf("item");               // Find index — O(N)
list.size();                         // Size
list.isEmpty();                      // Check empty
list.subList(0, 3);                  // View of range (inclusive, exclusive)
list.sort(Comparator.naturalOrder()); // Sort — O(N log N)
Collections.reverse(list);          // Reverse — O(N)
list.toArray(new String[0]);         // Convert to array
list.stream().filter(...).collect(...); // Stream API
```

#### 🎯 Set Implementations

| Operation | `HashSet` | `LinkedHashSet` | `TreeSet` | `ConcurrentHashMap.newKeySet()` | `ConcurrentSkipListSet` |
|-----------|----------|----------------|----------|-------------------------------|------------------------|
| `add(E)` | **O(1)** avg | **O(1)** avg | **O(log N)** | **O(1)** avg | **O(log N)** |
| `remove(E)` | **O(1)** avg | **O(1)** avg | **O(log N)** | **O(1)** avg | **O(log N)** |
| `contains(E)` | **O(1)** avg | **O(1)** avg | **O(log N)** | **O(1)** avg | **O(log N)** |
| `first()` / `last()` | ❌ N/A | ❌ N/A | **O(log N)** | ❌ N/A | **O(log N)** |
| `floor()` / `ceiling()` | ❌ N/A | ❌ N/A | **O(log N)** | ❌ N/A | **O(log N)** |
| Iteration order | ❌ None | ✅ Insertion order | ✅ Sorted | ❌ None | ✅ Sorted |
| Null elements | ✅ 1 null | ✅ 1 null | ❌ No | ❌ No | ❌ No |
| Thread-safe | ❌ No | ❌ No | ❌ No | ✅ Yes | ✅ Yes |

**Key Operations on Set**:
```java
Set<String> set = new HashSet<>();
set.add("item");                     // Add — O(1)
set.remove("item");                  // Remove — O(1)
set.contains("item");               // Check membership — O(1) ⭐
set.size();                          // Size
set.isEmpty();                       // Check empty

// TreeSet specific (sorted operations)
TreeSet<Integer> sorted = new TreeSet<>();
sorted.first();                      // Smallest element — O(log N)
sorted.last();                       // Largest element — O(log N)
sorted.floor(5);                     // Greatest element ≤ 5 — O(log N)
sorted.ceiling(5);                   // Smallest element ≥ 5 — O(log N)
sorted.lower(5);                     // Greatest element < 5 — O(log N)
sorted.higher(5);                    // Smallest element > 5 — O(log N)
sorted.headSet(5);                   // Elements < 5
sorted.tailSet(5);                   // Elements ≥ 5
sorted.subSet(2, 8);                 // Elements in range [2, 8)
sorted.pollFirst();                  // Remove and return smallest — O(log N)
sorted.pollLast();                   // Remove and return largest — O(log N)
```

#### 📦 Queue & Deque Implementations

| Operation | `ArrayDeque` | `LinkedList` | `PriorityQueue` | `ConcurrentLinkedQueue` | `LinkedBlockingQueue` | `PriorityBlockingQueue` |
|-----------|-------------|-------------|-----------------|------------------------|--------------------|----------------------|
| `offer(E)` / `add(E)` | **O(1)** amort | **O(1)** | **O(log N)** | **O(1)** | **O(1)** | **O(log N)** |
| `poll()` / `remove()` | **O(1)** | **O(1)** | **O(log N)** | **O(1)** | **O(1)** | **O(log N)** |
| `peek()` | **O(1)** | **O(1)** | **O(1)** | **O(1)** | **O(1)** | **O(1)** |
| `contains(E)` | **O(N)** | **O(N)** | **O(N)** | **O(N)** | **O(N)** | **O(N)** |
| `size()` | **O(1)** | **O(1)** | **O(1)** | **O(N)** ⚠️ | **O(1)** | **O(1)** |
| Ordering | FIFO / LIFO | FIFO / LIFO | Priority (min-heap) | FIFO | FIFO | Priority |
| Blocking | ❌ No | ❌ No | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| Thread-safe | ❌ No | ❌ No | ❌ No | ✅ Yes | ✅ Yes | ✅ Yes |

**Key Operations on Queue / Deque / PriorityQueue**:
```java
// ===== Queue (FIFO) =====
Queue<Integer> queue = new ArrayDeque<>();  // ⭐ Preferred over LinkedList
queue.offer(1);                       // Enqueue — O(1)
queue.poll();                         // Dequeue — O(1) (null if empty)
queue.peek();                         // Look at front — O(1)
queue.isEmpty();                      // Check empty

// ===== Deque (Double-ended, also use as Stack) =====
Deque<Integer> deque = new ArrayDeque<>();
deque.offerFirst(1);                  // Add to front — O(1)
deque.offerLast(2);                   // Add to back — O(1)
deque.pollFirst();                    // Remove from front — O(1)
deque.pollLast();                     // Remove from back — O(1)
deque.peekFirst();                    // Look at front — O(1)
deque.peekLast();                     // Look at back — O(1)

// ===== Stack (use Deque, NOT Stack class) =====
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);                        // Push — O(1)
stack.pop();                          // Pop — O(1)
stack.peek();                         // Top — O(1)

// ===== PriorityQueue (Min-Heap by default) =====
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
PriorityQueue<int[]> custom = new PriorityQueue<>((a, b) -> a[0] - b[0]); // custom comparator

minHeap.offer(5);                     // Insert — O(log N)
minHeap.poll();                       // Remove min — O(log N)
minHeap.peek();                       // Get min — O(1)
minHeap.size();                       // Size — O(1)
// NOTE: PriorityQueue does NOT support O(log N) remove(Object) — it's O(N)!
// For O(log N) arbitrary removal, use TreeMap or indexed heap
```

#### 🔑 Quick Decision Guide — Which Collection to Use?

| Need | Use This | Why |
|------|----------|-----|
| Key-value lookup O(1) | `HashMap` | Hash table, amortized O(1) |
| Sorted key-value | `TreeMap` | Red-black tree, O(log N), sorted iteration |
| Key-value with insertion order | `LinkedHashMap` | O(1) lookup + linked list for order |
| LRU Cache | `LinkedHashMap` (with `removeEldestEntry`) | Insertion/access order + auto-evict |
| Random access list | `ArrayList` | Array-backed, O(1) indexed access |
| Frequent insert/delete at ends | `ArrayDeque` | Faster than LinkedList, less memory |
| FIFO queue | `ArrayDeque` | ⭐ Preferred over LinkedList for queue |
| LIFO stack | `ArrayDeque` | ⭐ Preferred over Stack class (legacy) |
| Min/Max element fast | `PriorityQueue` | Heap, O(1) peek, O(log N) insert/remove |
| Unique elements O(1) | `HashSet` | Hash table, O(1) add/remove/contains |
| Sorted unique elements | `TreeSet` | Red-black tree, O(log N), range queries |
| Deduplication | `HashSet` or `LinkedHashSet` | O(1) contains check |
| Counting / Frequency | `HashMap<K, Integer>` | Use `merge()` or `getOrDefault()` |
| Sliding window | `ArrayDeque` | Use as deque for window boundaries |
| Graph adjacency list | `HashMap<Node, List<Node>>` | O(1) neighbor lookup |
| Interval / range queries | `TreeMap` | `floorKey()`, `ceilingKey()`, `subMap()` |

---

### 🧵 Concurrent Collections — Complete Reference

> **Rule**: Never use regular collections (`ArrayList`, `HashMap`, `HashSet`, `LinkedList`, `TreeMap`, `PriorityQueue`) in multi-threaded code. Always swap to their concurrent counterparts.

#### 📋 Non-Thread-Safe → Thread-Safe Collection Mapping

| Non-Thread-Safe | Thread-Safe Replacement | When to Use |
|----------------|------------------------|-------------|
| `ArrayList` | `CopyOnWriteArrayList` | Read-heavy, rare writes (iterators never throw ConcurrentModificationException) |
| `ArrayList` | `Collections.synchronizedList(new ArrayList<>())` | Balanced read/write (wraps with synchronized, must sync on iteration) |
| `ArrayList` | `Vector` | ❌ Legacy — avoid in interviews, use CopyOnWriteArrayList instead |
| `LinkedList` | `ConcurrentLinkedDeque` | Non-blocking concurrent deque (lock-free) |
| `LinkedList` (as Queue) | `ConcurrentLinkedQueue` | Non-blocking concurrent FIFO queue (lock-free) |
| `HashMap` | `ConcurrentHashMap` | ⭐ **Go-to choice**. Segment-level locking, high concurrency |
| `HashMap` | `Collections.synchronizedMap(new HashMap<>())` | Simple synchronized wrapper (coarse lock, slower) |
| `HashMap` | `Hashtable` | ❌ Legacy — avoid, use ConcurrentHashMap instead |
| `TreeMap` | `ConcurrentSkipListMap` | Sorted concurrent map (O(log N) ops, lock-free reads) |
| `HashSet` | `ConcurrentHashMap.newKeySet()` | ⭐ **Go-to for concurrent sets** (backed by ConcurrentHashMap) |
| `HashSet` | `CopyOnWriteArraySet` | Read-heavy, small sets, rare writes |
| `TreeSet` | `ConcurrentSkipListSet` | Sorted concurrent set (O(log N), lock-free reads) |
| `PriorityQueue` | `PriorityBlockingQueue` | Thread-safe priority queue (blocking on take when empty) |
| `ArrayDeque` | `ConcurrentLinkedDeque` | Non-blocking concurrent deque |
| `ArrayDeque` (as Stack) | `ConcurrentLinkedDeque` | Use `push()`/`pop()` methods for stack behavior |
| `LinkedList` (as Deque) | `LinkedBlockingDeque` | Bounded blocking deque (producer-consumer with capacity) |

#### 🔄 Blocking Queues (Producer-Consumer Pattern)

| Queue Type | Behavior | Use Case |
|-----------|----------|----------|
| `ArrayBlockingQueue` | Bounded, backed by array, FIFO | Fixed-size buffer between producer/consumer |
| `LinkedBlockingQueue` | Optionally bounded, backed by linked nodes | Default choice for producer-consumer |
| `PriorityBlockingQueue` | Unbounded, priority-ordered | Task scheduling by priority |
| `DelayQueue` | Elements available only after delay expires | Scheduled tasks, retry with delay |
| `SynchronousQueue` | Zero-capacity, direct handoff | Direct producer → consumer handoff (no buffering) |
| `LinkedTransferQueue` | Combination of LinkedBlockingQueue + SynchronousQueue | High-performance producer-consumer |

#### ⚛️ Atomic Variables (Lock-Free)

```java
// For counters, flags, references — avoid locks entirely
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();           // thread-safe ++count
count.getAndIncrement();           // thread-safe count++
count.compareAndSet(expected, new); // CAS operation

AtomicLong bigCount = new AtomicLong(0L);
AtomicBoolean flag = new AtomicBoolean(false);
AtomicReference<Node> head = new AtomicReference<>(null);

// For high-contention counters (better than AtomicLong)
LongAdder adder = new LongAdder();   // striped, reduces contention
adder.increment();
adder.sum();                          // get total
```

#### 🔧 Code Examples — Common Swaps

```java
// ❌ BAD: Not thread-safe
List<String> list = new ArrayList<>();
Map<String, Integer> map = new HashMap<>();
Set<String> set = new HashSet<>();
Queue<Task> queue = new LinkedList<>();
Deque<Task> stack = new ArrayDeque<>();

// ✅ GOOD: Thread-safe equivalents
List<String> list = new CopyOnWriteArrayList<>();                // read-heavy
List<String> list = Collections.synchronizedList(new ArrayList<>()); // balanced
Map<String, Integer> map = new ConcurrentHashMap<>();            // ⭐ always use this
Set<String> set = ConcurrentHashMap.newKeySet();                 // ⭐ concurrent set
Queue<Task> queue = new ConcurrentLinkedQueue<>();               // non-blocking
Queue<Task> queue = new LinkedBlockingQueue<>();                 // blocking (producer-consumer)
Deque<Task> stack = new ConcurrentLinkedDeque<>();               // concurrent stack/deque
NavigableMap<String, Integer> sorted = new ConcurrentSkipListMap<>(); // sorted + concurrent
```

#### ⚠️ Common Pitfalls to Mention

```java
// PITFALL 1: ConcurrentHashMap does NOT lock for compound operations
map.put(key, map.get(key) + 1);  // ❌ NOT atomic! (check-then-act race)
map.merge(key, 1, Integer::sum); // ✅ Atomic compound operation
map.compute(key, (k, v) -> v == null ? 1 : v + 1); // ✅ Also atomic

// PITFALL 2: synchronizedList needs manual sync on iteration
List<String> syncList = Collections.synchronizedList(new ArrayList<>());
synchronized (syncList) {          // ✅ Must sync manually for iteration
    for (String s : syncList) {
        // process
    }
}
// CopyOnWriteArrayList does NOT need this — iterators are snapshot-based

// PITFALL 3: ConcurrentHashMap does NOT allow null keys or values
map.put(null, "value");  // ❌ NullPointerException
map.put("key", null);    // ❌ NullPointerException
// HashMap allows both null key and null values

// PITFALL 4: Size of concurrent collections is approximate
int size = concurrentMap.size(); // Approximate! Not exact in concurrent env
// Use only for monitoring, NOT for logic decisions
```

### 🧵 Concurrency Discussion Points

| Scenario | Solution | Say This |
|----------|----------|----------|
| Simple shared state | `synchronized` | "For simplicity, I'll use synchronized. In production, I'd consider finer-grained locks." |
| Read-heavy workload | `ReadWriteLock` | "Since reads far outnumber writes, a ReadWriteLock gives better concurrency." |
| Counter / flag | `AtomicInteger` / `volatile` | "For a simple counter, CAS-based AtomicInteger avoids lock overhead." |
| High-contention counter | `LongAdder` | "LongAdder uses striped cells to reduce contention — better than AtomicLong under high concurrency." |
| Shared Map | `ConcurrentHashMap` | "ConcurrentHashMap provides segment-level locking, far better than synchronized HashMap." |
| Shared List (read-heavy) | `CopyOnWriteArrayList` | "CopyOnWriteArrayList is ideal here — reads are lock-free, writes copy the array." |
| Shared List (balanced) | `Collections.synchronizedList` | "I'll wrap with synchronizedList and manually sync during iteration." |
| Shared Set | `ConcurrentHashMap.newKeySet()` | "Backed by ConcurrentHashMap — same great concurrency characteristics." |
| Sorted concurrent map | `ConcurrentSkipListMap` | "ConcurrentSkipListMap gives sorted order with O(log N) lock-free reads." |
| Priority Queue (concurrent) | `PriorityBlockingQueue` | "PriorityBlockingQueue is thread-safe and blocks consumers when empty." |
| Producer-consumer | `LinkedBlockingQueue` | "A BlockingQueue naturally handles synchronization between producer and consumer threads." |
| Direct handoff | `SynchronousQueue` | "SynchronousQueue does direct handoff — no buffering, producer waits for consumer." |
| Stack (concurrent) | `ConcurrentLinkedDeque` | "I'll use ConcurrentLinkedDeque with push/pop for a lock-free concurrent stack." |

---

## Phase 6️⃣ — Test Cases & Complexity (5 min)

### 🧪 Test Cases Template

```
"Let me walk through test cases:

 1. NORMAL CASE:    Input: [typical example]  → Expected: [result] ✓
 2. EDGE - EMPTY:   Input: []                 → Expected: [default/throw] ✓  
 3. EDGE - SINGLE:  Input: [one element]      → Expected: [result] ✓
 4. EDGE - LARGE:   Input: [max constraints]  → Expected: runs in time ✓
 5. EDGE - SPECIAL: Input: [all same / negative / overflow] → Expected: [result] ✓
"
```

### 📊 Complexity Analysis Template

```
"Time Complexity:  O(___) 
 - [Main loop iterates N times]
 - [Each iteration does O(log N) heap operation]
 - Total: O(N log N)

 Space Complexity: O(___)
 - [HashMap stores at most N entries]
 - [Recursion stack goes K levels deep]
 - Total: O(N + K)"
```

### Common Complexity Targets by Input Size

| N Range | Acceptable Complexity | Patterns |
|---------|----------------------|----------|
| N ≤ 20 | O(2^N), O(N!) | Backtracking, brute force |
| N ≤ 500 | O(N³) | DP 3D |
| N ≤ 5,000 | O(N²) | DP 2D, nested loops |
| N ≤ 100,000 | O(N log N) | Sorting, binary search, heap |
| N ≤ 10,000,000 | O(N) | Hash map, two pointers, sliding window |
| N > 10,000,000 | O(log N), O(1) | Binary search, math |

---

## 🎯 Microsoft-Specific Tips

### What Microsoft Interviewers Look For
1. **Communication**: Talk through your thought process out loud
2. **Clean code**: Readable, well-structured, good naming
3. **Edge case handling**: Null checks, empty inputs, overflow
4. **Testing mindset**: Trace through examples, verify correctness
5. **Scalability awareness**: Discuss time/space tradeoffs
6. **Concurrency awareness**: Know when/how to make things thread-safe

### 🗣️ Key Phrases to Use

| Moment | Say This |
|--------|----------|
| Starting | "Let me make sure I understand the problem correctly..." |
| After clarifying | "So the constraints are X, which tells me O(N²) won't work, I need O(N log N) or better." |
| Choosing approach | "I see this maps to a [pattern] problem because [reason]." |
| Before coding | "Let me outline my approach: I'll use [DS] to [strategy]. Sound good?" |
| While coding | "Here I'm [explaining current step]..." |
| Hit a bug | "I see the issue — [explain]. Let me fix this." |
| Done coding | "Let me trace through with this test case to verify..." |
| Complexity | "Time is O(___) because [reason]. Space is O(___) because [reason]." |
| Follow-up | "To optimize further, I could use [approach] which would give O(___), trading off [tradeoff]." |

---

## 📋 Quick Self-Check Before Submitting

- [ ] Did I handle **null / empty** input?
- [ ] Did I handle the **no valid answer** case?
- [ ] Are my variable names **descriptive**?
- [ ] Did I avoid **integer overflow** (use `long` if needed)?
- [ ] Did I state **time and space** complexity?
- [ ] Did I trace through **at least 2 test cases**?
- [ ] Can I explain what happens with **concurrent** access? (even if not coded)
- [ ] Is my code **modular** (helper methods for sub-tasks)?
