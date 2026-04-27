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

#### 🌳 TreeMap & TreeSet — Complete Operations Reference

> **Backed by Red-Black Tree**: O(log N) for all operations, sorted order, range queries. Essential for interval problems, sliding window with sorted access, and calendar/scheduling questions.

##### TreeMap — All Operations

```java
TreeMap<Integer, String> treeMap = new TreeMap<>();

// ===== Basic CRUD — O(log N) each =====
treeMap.put(5, "five");               // Insert/update
treeMap.put(3, "three");
treeMap.put(8, "eight");
treeMap.put(1, "one");
treeMap.put(10, "ten");
// TreeMap auto-sorted: {1=one, 3=three, 5=five, 8=eight, 10=ten}

treeMap.get(5);                       // "five" — O(log N)
treeMap.containsKey(5);               // true — O(log N)
treeMap.containsValue("five");        // true — O(N) ⚠️ (scans all values)
treeMap.remove(3);                    // "three" — O(log N)
treeMap.size();                       // 4 — O(1)
treeMap.isEmpty();                    // false — O(1)

// ===== Boundary Queries (THE POWER OF TreeMap) — O(log N) each =====
treeMap.firstKey();                   // 1 — smallest key
treeMap.lastKey();                    // 10 — largest key
treeMap.firstEntry();                 // 1=one — smallest entry (Map.Entry)
treeMap.lastEntry();                  // 10=ten — largest entry

// ===== Floor / Ceiling / Lower / Higher — O(log N) ⭐ =====
treeMap.floorKey(6);                  // 5 — greatest key ≤ 6
treeMap.floorKey(5);                  // 5 — greatest key ≤ 5 (inclusive)
treeMap.ceilingKey(6);                // 8 — smallest key ≥ 6
treeMap.ceilingKey(8);                // 8 — smallest key ≥ 8 (inclusive)
treeMap.lowerKey(5);                  // 1 — greatest key STRICTLY < 5
treeMap.higherKey(5);                 // 8 — smallest key STRICTLY > 5

treeMap.floorEntry(6);               // 5=five — entry with greatest key ≤ 6
treeMap.ceilingEntry(6);             // 8=eight — entry with smallest key ≥ 6
treeMap.lowerEntry(5);               // 1=one — entry with greatest key < 5
treeMap.higherEntry(5);              // 8=eight — entry with smallest key > 5

// ===== Poll (Remove + Return) — O(log N) =====
treeMap.pollFirstEntry();             // removes and returns 1=one (smallest)
treeMap.pollLastEntry();              // removes and returns 10=ten (largest)

// ===== Range Views (Sub-Maps) — O(log N) to create, iterate O(K) =====
treeMap.headMap(5);                   // keys < 5 → {1=one, 3=three} (exclusive)
treeMap.headMap(5, true);             // keys ≤ 5 → {1=one, 3=three, 5=five} (inclusive)
treeMap.tailMap(5);                   // keys ≥ 5 → {5=five, 8=eight, 10=ten} (inclusive)
treeMap.tailMap(5, false);            // keys > 5 → {8=eight, 10=ten} (exclusive)
treeMap.subMap(3, 8);                 // keys [3, 8) → {3=three, 5=five}
treeMap.subMap(3, true, 8, true);     // keys [3, 8] → {3=three, 5=five, 8=eight}

// ===== Navigation — O(log N) =====
treeMap.descendingMap();              // Reverse-order view: {10, 8, 5, 3, 1}
treeMap.descendingKeySet();           // Reverse-order key set
treeMap.navigableKeySet();            // Ascending key set

// ===== Iteration (always in sorted order!) =====
for (Map.Entry<Integer, String> e : treeMap.entrySet()) {
    System.out.println(e.getKey() + "=" + e.getValue());
}
// Output: 1=one, 3=three, 5=five, 8=eight, 10=ten (SORTED!)

// ===== Custom Comparator (reverse order, custom logic) =====
TreeMap<Integer, String> reverseMap = new TreeMap<>(Collections.reverseOrder());
TreeMap<String, Integer> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
```

##### TreeSet — All Operations

```java
TreeSet<Integer> treeSet = new TreeSet<>();

// ===== Basic CRUD — O(log N) each =====
treeSet.add(5);                       // true (added)
treeSet.add(3);
treeSet.add(8);
treeSet.add(1);
treeSet.add(10);
treeSet.add(5);                       // false (duplicate, not added)
// TreeSet auto-sorted: [1, 3, 5, 8, 10]

treeSet.contains(5);                  // true — O(log N)
treeSet.remove(3);                    // true — O(log N)
treeSet.size();                       // 4 — O(1)

// ===== Boundary Queries — O(log N) =====
treeSet.first();                      // 1 — smallest element
treeSet.last();                       // 10 — largest element

// ===== Floor / Ceiling / Lower / Higher — O(log N) ⭐ =====
treeSet.floor(6);                     // 5 — greatest element ≤ 6
treeSet.ceiling(6);                   // 8 — smallest element ≥ 6
treeSet.lower(5);                     // 1 — greatest element STRICTLY < 5
treeSet.higher(5);                    // 8 — smallest element STRICTLY > 5

// ===== Poll — O(log N) =====
treeSet.pollFirst();                  // 1 — removes and returns smallest
treeSet.pollLast();                   // 10 — removes and returns largest

// ===== Range Views — O(log N) + O(K) iteration =====
treeSet.headSet(5);                   // elements < 5 → [1, 3]
treeSet.headSet(5, true);             // elements ≤ 5 → [1, 3, 5]
treeSet.tailSet(5);                   // elements ≥ 5 → [5, 8, 10]
treeSet.tailSet(5, false);            // elements > 5 → [8, 10]
treeSet.subSet(3, 8);                 // elements [3, 8) → [3, 5]
treeSet.subSet(3, true, 8, true);     // elements [3, 8] → [3, 5, 8]

// ===== Descending =====
treeSet.descendingSet();              // Reverse-order view: [10, 8, 5, 3, 1]
treeSet.descendingIterator();         // Reverse iterator

// ===== Custom Comparator =====
TreeSet<Integer> descSet = new TreeSet<>(Collections.reverseOrder());
TreeSet<String> caseInsensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
TreeSet<int[]> byFirstElement = new TreeSet<>((a, b) -> Integer.compare(a[0], b[0]));
```

##### 🎯 TreeMap / TreeSet Interview Use Cases

| Problem Pattern | Use TreeMap/TreeSet For | Key Methods |
|----------------|------------------------|-------------|
| **Calendar / Meeting Rooms** | Find overlapping intervals | `floorKey()`, `ceilingKey()`, `subMap()` |
| **Sliding Window with Sorted Access** | Maintain sorted window | `add()`, `remove()`, `first()`, `last()` |
| **Count of Smaller Numbers After Self** | Sorted order tracking | `headSet().size()` |
| **Stock Price / Time Series** | Range queries by timestamp | `floorEntry(timestamp)`, `subMap(t1, t2)` |
| **My Calendar I/II/III** | Interval scheduling | `floorKey()`, `put()`, range check |
| **Data Stream as Disjoint Intervals** | Merge intervals dynamically | `lowerEntry()`, `higherEntry()` |
| **Top K / Ranking** | Sorted unique elements + range | `first()`, `last()`, `pollFirst()` |
| **Contains Duplicate III** | Check if any value within range | `floor()`, `ceiling()` |
| **Kth Smallest in BST** | Sorted traversal | In-order iteration |

##### Common Interview Pattern — Calendar Booking with TreeMap

```java
// My Calendar: Check if new event [start, end) conflicts with existing
TreeMap<Integer, Integer> calendar = new TreeMap<>(); // start → end

public boolean book(int start, int end) {
    // Find the event that starts just before or at 'start'
    Map.Entry<Integer, Integer> prev = calendar.floorEntry(start);
    // Find the event that starts just after 'start'  
    Map.Entry<Integer, Integer> next = calendar.ceilingEntry(start);
    
    // Check no overlap with previous event
    if (prev != null && prev.getValue() > start) return false;
    // Check no overlap with next event
    if (next != null && next.getKey() < end) return false;
    
    calendar.put(start, end);
    return true;
}
```

##### Common Interview Pattern — Sliding Window Median with TreeSet

```java
// Use two TreeSets to maintain a balanced partition
TreeSet<int[]> small = new TreeSet<>((a, b) -> 
    a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
TreeSet<int[]> large = new TreeSet<>((a, b) -> 
    a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
// small.last() = max of lower half, large.first() = min of upper half
```

---

#### 🔤 String Operations — Complete Reference

```java
String s = "Hello, World!";

// ===== Basic Info =====
s.length();                           // 13 — O(1)
s.isEmpty();                          // false — O(1)
s.isBlank();                          // false (Java 11+, true if only whitespace) — O(N)
s.charAt(0);                          // 'H' — O(1)

// ===== Search =====
s.indexOf('o');                       // 4 — first occurrence — O(N)
s.indexOf("World");                   // 7 — first occurrence of substring — O(N*M)
s.lastIndexOf('o');                   // 8 — last occurrence — O(N)
s.contains("World");                  // true — O(N*M)
s.startsWith("Hello");               // true — O(M)
s.endsWith("!");                      // true — O(M)

// ===== Extract =====
s.substring(7);                       // "World!" — O(N) creates new string
s.substring(7, 12);                   // "World" — [inclusive, exclusive) — O(N)
s.toCharArray();                      // char[] {'H','e','l','l','o',...} — O(N)
s.chars();                            // IntStream of chars

// ===== Transform =====
s.toLowerCase();                      // "hello, world!" — O(N), new String
s.toUpperCase();                      // "HELLO, WORLD!" — O(N), new String
s.trim();                             // removes leading/trailing whitespace — O(N)
s.strip();                            // Java 11+, Unicode-aware trim — O(N)
s.replace('l', 'L');                  // "HeLLo, WorLd!" — replace all chars — O(N)
s.replace("World", "Java");           // "Hello, Java!" — replace substring — O(N)
s.replaceAll("[aeiou]", "*");         // regex replace — O(N)
s.replaceFirst("l", "L");            // "HeLlo, World!" — first match only

// ===== Split & Join =====
"a,b,c".split(",");                   // String[] {"a", "b", "c"} — O(N)
"a,,b".split(",", -1);               // {"a", "", "b"} — preserves empty strings
String.join("-", "a", "b", "c");     // "a-b-c" — O(N)
String.join(",", listOfStrings);     // Join collection

// ===== Compare =====
s.equals("Hello, World!");            // true — O(N), ALWAYS use .equals() not ==
s.equalsIgnoreCase("hello, world!"); // true — O(N)
s.compareTo("Hi");                    // <0 (lexicographic, "He" < "Hi") — O(N)
"abc".compareTo("abd");               // -1 ('c' < 'd')

// ===== Convert =====
String.valueOf(42);                   // "42" — int to String
String.valueOf(3.14);                 // "3.14" — double to String
String.valueOf(true);                 // "true"
String.valueOf(new char[]{'a','b'});  // "ab"
Integer.parseInt("42");               // 42 — String to int (throws NumberFormatException)
Integer.valueOf("42");                // Integer(42) — boxed
Double.parseDouble("3.14");           // 3.14

// ===== char operations =====
Character.isLetter('a');              // true
Character.isDigit('5');               // true
Character.isLetterOrDigit('a');       // true
Character.isWhitespace(' ');          // true
Character.isUpperCase('A');           // true
Character.isLowerCase('a');           // true
Character.toLowerCase('A');           // 'a'
Character.toUpperCase('a');           // 'A'
'a' - 'a';                           // 0 (useful for array index: char - 'a')
'z' - 'a';                           // 25
```

##### StringBuilder (Mutable Strings — Use in Interviews!)

```java
// ⚠️ String concatenation in loops is O(N²) → ALWAYS use StringBuilder
StringBuilder sb = new StringBuilder();

sb.append("Hello");                   // "Hello" — O(1) amortized
sb.append(' ');                       // "Hello " — append char
sb.append(42);                        // "Hello 42" — append int
sb.insert(5, ",");                    // "Hello, 42" — O(N) shift
sb.delete(5, 6);                      // "Hello 42" — O(N) shift
sb.deleteCharAt(5);                   // "Hello42" — O(N)
sb.replace(5, 7, " World");          // "Hello World"
sb.reverse();                         // "dlroW olleH" — O(N) in-place
sb.charAt(0);                         // 'd' — O(1)
sb.setCharAt(0, 'D');                // "DlroW olleH"
sb.length();                          // 11 — O(1)
sb.toString();                        // Convert to String — O(N)

// ===== Common interview pattern: build string efficiently =====
StringBuilder result = new StringBuilder();
for (char c : s.toCharArray()) {
    if (Character.isLetter(c)) {
        result.append(Character.toLowerCase(c));
    }
}
String cleaned = result.toString();

// ===== StringBuffer = thread-safe version (rarely needed) =====
// Use StringBuilder (faster) unless sharing across threads
```

##### Common String Interview Patterns

```java
// ===== Frequency count with int[26] (lowercase only) =====
int[] freq = new int[26];
for (char c : s.toCharArray()) {
    freq[c - 'a']++;                  // O(1) per char
}

// ===== Frequency count with int[128] (all ASCII) =====
int[] freq = new int[128];
for (char c : s.toCharArray()) {
    freq[c]++;
}

// ===== Check if anagram =====
// Sort both → compare, OR use frequency array

// ===== Reverse a string =====
new StringBuilder(s).reverse().toString();

// ===== Check palindrome =====
int left = 0, right = s.length() - 1;
while (left < right) {
    if (s.charAt(left++) != s.charAt(right--)) return false;
}
return true;

// ===== String to int (without Integer.parseInt) =====
int num = 0;
for (char c : s.toCharArray()) {
    num = num * 10 + (c - '0');       // '3' - '0' = 3
}
```

#### 📚 Stack Operations — Complete Reference

> **Rule**: Use `ArrayDeque` as stack, NOT `java.util.Stack` (legacy, synchronized, slow)

```java
// ===== Stack using ArrayDeque (preferred) =====
Deque<Integer> stack = new ArrayDeque<>();

stack.push(1);                        // Push — O(1) — adds to front
stack.push(2);                        // Stack: [2, 1] (top → bottom)
stack.push(3);                        // Stack: [3, 2, 1]

stack.peek();                         // 3 — look at top, don't remove — O(1)
stack.pop();                          // 3 — remove and return top — O(1)
stack.isEmpty();                      // false — O(1)
stack.size();                         // 2 — O(1)
stack.contains(1);                    // true — O(N)

// ===== Common Stack Interview Patterns =====

// Pattern 1: Matching brackets
Deque<Character> stack = new ArrayDeque<>();
for (char c : s.toCharArray()) {
    if (c == '(' || c == '[' || c == '{') {
        stack.push(c);
    } else {
        if (stack.isEmpty()) return false;
        char top = stack.pop();
        if (c == ')' && top != '(') return false;
        if (c == ']' && top != '[') return false;
        if (c == '}' && top != '{') return false;
    }
}
return stack.isEmpty();

// Pattern 2: Monotonic Stack (Next Greater Element)
Deque<Integer> stack = new ArrayDeque<>();
int[] result = new int[nums.length];
Arrays.fill(result, -1);
for (int i = 0; i < nums.length; i++) {
    while (!stack.isEmpty() && nums[stack.peek()] < nums[i]) {
        result[stack.pop()] = nums[i];
    }
    stack.push(i);
}

// Pattern 3: Evaluate Reverse Polish Notation
Deque<Integer> stack = new ArrayDeque<>();
for (String token : tokens) {
    if ("+-*/".contains(token)) {
        int b = stack.pop(), a = stack.pop();
        switch (token) {
            case "+": stack.push(a + b); break;
            case "-": stack.push(a - b); break;
            case "*": stack.push(a * b); break;
            case "/": stack.push(a / b); break;
        }
    } else {
        stack.push(Integer.parseInt(token));
    }
}
return stack.pop();

// Pattern 4: Min Stack (O(1) getMin)
Deque<int[]> stack = new ArrayDeque<>(); // [value, currentMin]
void push(int val) {
    int min = stack.isEmpty() ? val : Math.min(val, stack.peek()[1]);
    stack.push(new int[]{val, min});
}
int getMin() { return stack.peek()[1]; }
```

#### 🔢 Array & Math Utilities — Quick Reference

```java
// ===== Arrays utility =====
int[] arr = new int[10];
Arrays.fill(arr, -1);                // Fill all with -1 — O(N)
Arrays.fill(arr, 2, 5, 0);          // Fill index [2,5) with 0 — O(K)
Arrays.copyOf(arr, 20);             // Copy with new length (pads 0s) — O(N)
Arrays.copyOfRange(arr, 2, 5);      // Copy range [2, 5) — O(K)
Arrays.equals(arr1, arr2);          // Deep equality — O(N)
Arrays.deepEquals(arr2d1, arr2d2);  // 2D array equality
Arrays.toString(arr);               // "[1, 2, 3]" — for printing
Arrays.deepToString(arr2d);         // "[[1,2],[3,4]]"
Arrays.binarySearch(arr, target);   // Binary search (MUST be sorted!) — O(log N)
Arrays.stream(arr).sum();           // Sum of array — O(N)
Arrays.stream(arr).max().getAsInt(); // Max — O(N)
Arrays.stream(arr).min().getAsInt(); // Min — O(N)

// ===== Collections utility =====
Collections.max(list);               // Max element — O(N)
Collections.min(list);               // Min element — O(N)
Collections.frequency(list, element); // Count occurrences — O(N)
Collections.swap(list, i, j);       // Swap two elements — O(1)
Collections.rotate(list, distance);  // Rotate list — O(N)
Collections.shuffle(list);           // Random shuffle — O(N)
Collections.unmodifiableList(list);  // Immutable wrapper
Collections.singletonList(item);     // Immutable single-element list
Collections.nCopies(5, "x");        // ["x","x","x","x","x"] immutable

// ===== Math operations =====
Math.max(a, b);                      // Max of two
Math.min(a, b);                      // Min of two
Math.abs(-5);                        // 5
Math.pow(2, 10);                     // 1024.0 (double)
Math.sqrt(16);                       // 4.0
Math.log(Math.E);                    // 1.0 (natural log)
Math.log10(100);                     // 2.0
Math.ceil(3.2);                      // 4.0 (round up)
Math.floor(3.8);                     // 3.0 (round down)
Math.round(3.5);                     // 4 (round to nearest)
Integer.MAX_VALUE;                   // 2,147,483,647 (~2.1 billion)
Integer.MIN_VALUE;                   // -2,147,483,648
Long.MAX_VALUE;                      // ~9.2 × 10^18
// ⚠️ Overflow check: if (a > Integer.MAX_VALUE - b) → overflow!

// ===== Bit manipulation =====
n & 1;                               // Check if odd (last bit)
n >> 1;                              // Divide by 2
n << 1;                              // Multiply by 2
n & (n - 1);                         // Remove lowest set bit
Integer.bitCount(n);                 // Count set bits (1s)
Integer.toBinaryString(n);           // "1010"
n ^ n;                               // 0 (XOR with self)
a ^ b ^ a;                           // b (XOR cancel out duplicates)

// ===== Random =====
Random rand = new Random();
rand.nextInt(10);                    // Random int [0, 10)
rand.nextInt(max - min + 1) + min;   // Random int [min, max]
```

---

#### 🔀 Comparators, Comparable & Sorting — Complete Reference

> **Critical for interviews**: Sorting and custom ordering come up in almost every coding round.

##### `Comparable` vs `Comparator`

| Feature | `Comparable<T>` | `Comparator<T>` |
|---------|----------------|-----------------|
| Package | `java.lang` | `java.util` |
| Method | `compareTo(T other)` | `compare(T a, T b)` |
| Where defined | Inside the class itself | External / separate class or lambda |
| Number of orderings | 1 (natural ordering) | ∞ (unlimited custom orderings) |
| Usage | `Collections.sort(list)` | `Collections.sort(list, comparator)` |
| When to use | Default/natural order for your class | Custom or multiple sort orders |

##### Implementing `Comparable` (Natural Ordering)

```java
class Employee implements Comparable<Employee> {
    String name;
    int salary;
    int age;
    
    @Override
    public int compareTo(Employee other) {
        // Return: negative (this < other), 0 (equal), positive (this > other)
        return Integer.compare(this.salary, other.salary); // ascending by salary
    }
}

// Usage: natural order
Collections.sort(employees);           // uses compareTo
employees.stream().sorted();            // uses compareTo
TreeSet<Employee> set = new TreeSet<>(); // uses compareTo
```

##### Creating `Comparator` (Custom Ordering) — All Methods

```java
// ===== Method 1: Lambda (most common in interviews) =====
Comparator<Employee> bySalary = (a, b) -> Integer.compare(a.salary, b.salary);
Comparator<Employee> byName = (a, b) -> a.name.compareTo(b.name);
Comparator<Employee> byAge = (a, b) -> a.age - b.age;  // ⚠️ OK only if no overflow risk

// ===== Method 2: Comparator.comparing (cleanest, preferred) =====
Comparator<Employee> bySalary = Comparator.comparing(e -> e.salary);
Comparator<Employee> bySalary = Comparator.comparing(Employee::getSalary);
Comparator<Employee> byName   = Comparator.comparing(Employee::getName);

// ===== Method 3: Chained / Multi-level sorting =====
Comparator<Employee> byNameThenSalary = Comparator
    .comparing(Employee::getName)                      // Primary: by name ascending
    .thenComparing(Employee::getSalary);               // Secondary: by salary ascending

Comparator<Employee> complex = Comparator
    .comparing(Employee::getDepartment)                // 1st: department ascending
    .thenComparing(Employee::getSalary, Comparator.reverseOrder()) // 2nd: salary DESCENDING
    .thenComparing(Employee::getName);                 // 3rd: name ascending

// ===== Method 4: Reverse order =====
Comparator<Employee> bySalaryDesc = Comparator.comparing(Employee::getSalary).reversed();
Comparator<Integer> descending = Comparator.reverseOrder();
Comparator<Integer> ascending = Comparator.naturalOrder();

// ===== Method 5: Null-safe comparators =====
Comparator<Employee> byNameNullFirst = Comparator.comparing(
    Employee::getName, Comparator.nullsFirst(Comparator.naturalOrder()));
Comparator<Employee> byNameNullLast = Comparator.comparing(
    Employee::getName, Comparator.nullsLast(Comparator.naturalOrder()));

// ===== Method 6: Comparator for int/long/double (avoids boxing) =====
Comparator<Employee> bySalary = Comparator.comparingInt(Employee::getSalary);
Comparator<Employee> byBigSalary = Comparator.comparingLong(Employee::getBigSalary);
Comparator<Employee> byRating = Comparator.comparingDouble(Employee::getRating);
```

##### Sorting Arrays

```java
int[] nums = {5, 3, 1, 4, 2};

// ===== Primitive arrays =====
Arrays.sort(nums);                          // Ascending — O(N log N) Dual-Pivot QuickSort
// ⚠️ NO built-in descending sort for primitives! Options:
// Option A: Sort ascending then reverse
Arrays.sort(nums);
for (int i = 0, j = nums.length - 1; i < j; i++, j--) {
    int tmp = nums[i]; nums[i] = nums[j]; nums[j] = tmp;
}
// Option B: Use Integer[] wrapper
Integer[] boxed = Arrays.stream(nums).boxed().toArray(Integer[]::new);
Arrays.sort(boxed, Collections.reverseOrder()); // Descending

// ===== Partial sort (range) =====
Arrays.sort(nums, 1, 4);                   // Sort index [1, 4) only

// ===== 2D array sort =====
int[][] intervals = {{3,5}, {1,3}, {2,8}};
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);          // Sort by first element
Arrays.sort(intervals, (a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]); // By first, then second
Arrays.sort(intervals, Comparator.comparingInt(a -> a[0])); // Cleaner syntax

// ===== String array sort =====
String[] words = {"banana", "apple", "cherry"};
Arrays.sort(words);                                       // Lexicographic ascending
Arrays.sort(words, Comparator.comparingInt(String::length)); // By length
Arrays.sort(words, (a, b) -> b.compareTo(a));            // Reverse lexicographic

// ===== char array sort =====
char[] chars = "interview".toCharArray();
Arrays.sort(chars);                          // Sort characters → "eiinrtvw"
```

##### Sorting Collections (Lists)

```java
List<Integer> list = new ArrayList<>(Arrays.asList(5, 3, 1, 4, 2));

// ===== Basic sort =====
Collections.sort(list);                              // Ascending
Collections.sort(list, Collections.reverseOrder());  // Descending
list.sort(Comparator.naturalOrder());                // Same as Collections.sort
list.sort(Comparator.reverseOrder());                // Descending

// ===== Custom object sort =====
List<Employee> employees = new ArrayList<>();
employees.sort(Comparator.comparing(Employee::getSalary));                    // By salary asc
employees.sort(Comparator.comparing(Employee::getSalary).reversed());        // By salary desc
employees.sort(Comparator.comparing(Employee::getName)
    .thenComparingInt(Employee::getSalary));                                   // Multi-level

// ===== Stream sort (returns NEW sorted stream, doesn't modify original) =====
List<Integer> sorted = list.stream()
    .sorted()                                         // Ascending
    .collect(Collectors.toList());

List<Integer> sortedDesc = list.stream()
    .sorted(Comparator.reverseOrder())               // Descending
    .collect(Collectors.toList());

List<Employee> top3BySalary = employees.stream()
    .sorted(Comparator.comparing(Employee::getSalary).reversed())
    .limit(3)
    .collect(Collectors.toList());
```

##### Sorting Maps (by Key / by Value)

```java
Map<String, Integer> map = new HashMap<>();
map.put("banana", 2); map.put("apple", 5); map.put("cherry", 1);

// ===== Sort by key =====
Map<String, Integer> sortedByKey = new TreeMap<>(map); // Natural key order

// ===== Sort by value (ascending) =====
List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
entries.sort(Map.Entry.comparingByValue());

// ===== Sort by value (descending) =====
entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

// ===== Using stream — sort by value and collect to LinkedHashMap =====
Map<String, Integer> sortedByValue = map.entrySet().stream()
    .sorted(Map.Entry.comparingByValue())
    .collect(Collectors.toMap(
        Map.Entry::getKey, Map.Entry::getValue,
        (e1, e2) -> e1, LinkedHashMap::new));  // LinkedHashMap preserves insertion order

// ===== Top K by value =====
List<String> top2Keys = map.entrySet().stream()
    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
    .limit(2)
    .map(Map.Entry::getKey)
    .collect(Collectors.toList());
```

##### Sorting Algorithms — Time & Space Complexity

| Algorithm | Best | Average | Worst | Space | Stable | In-Place | Used In |
|-----------|------|---------|-------|-------|--------|----------|---------|
| **Arrays.sort (primitives)** | O(N log N) | O(N log N) | O(N log N) | O(log N) | ❌ No | ✅ Yes | Java (Dual-Pivot QuickSort) |
| **Arrays.sort (objects)** | O(N log N) | O(N log N) | O(N log N) | O(N) | ✅ Yes | ❌ No | Java (TimSort) |
| **Collections.sort** | O(N log N) | O(N log N) | O(N log N) | O(N) | ✅ Yes | ❌ No | Java (TimSort) |
| **Merge Sort** | O(N log N) | O(N log N) | O(N log N) | O(N) | ✅ Yes | ❌ No | External sort, linked lists |
| **Quick Sort** | O(N log N) | O(N log N) | O(N²) | O(log N) | ❌ No | ✅ Yes | General purpose (avg fast) |
| **Heap Sort** | O(N log N) | O(N log N) | O(N log N) | O(1) | ❌ No | ✅ Yes | PriorityQueue internally |
| **Insertion Sort** | O(N) ⭐ | O(N²) | O(N²) | O(1) | ✅ Yes | ✅ Yes | Nearly sorted, small N (<50) |
| **Bubble Sort** | O(N) | O(N²) | O(N²) | O(1) | ✅ Yes | ✅ Yes | ❌ Never use (teaching only) |
| **Selection Sort** | O(N²) | O(N²) | O(N²) | O(1) | ❌ No | ✅ Yes | ❌ Rarely used |
| **Counting Sort** | O(N+K) | O(N+K) | O(N+K) | O(K) | ✅ Yes | ❌ No | Small range integers |
| **Radix Sort** | O(NK) | O(NK) | O(NK) | O(N+K) | ✅ Yes | ❌ No | Fixed-length integers/strings |
| **Bucket Sort** | O(N+K) | O(N+K) | O(N²) | O(N+K) | ✅ Yes | ❌ No | Uniformly distributed data |

> **Key insight**: Java's `Arrays.sort()` uses **Dual-Pivot QuickSort** for primitives (not stable, but fast) and **TimSort** for objects (stable, needed for `Comparable` contract). `Collections.sort()` always uses **TimSort**.

##### ⚠️ Common Sorting Pitfalls

```java
// PITFALL 1: Integer overflow in comparator
(a, b) -> a - b;           // ❌ OVERFLOW if a=Integer.MAX_VALUE, b=-1
(a, b) -> Integer.compare(a, b);  // ✅ Safe always

// PITFALL 2: Sorting primitives descending — no direct way
Arrays.sort(intArray);      // Only ascending for primitives
// Must use Integer[] + Comparator, or sort then reverse

// PITFALL 3: Comparator contract violation (transitivity)
// If compare(a,b) > 0 and compare(b,c) > 0, then compare(a,c) MUST be > 0
// Violating this causes IllegalArgumentException with TimSort

// PITFALL 4: Modifying collection while sorting
// Never modify a list while Collections.sort is running → ConcurrentModificationException

// PITFALL 5: Sorting a subList
list.subList(0, 5).sort(null); // ✅ Sorts only first 5 elements of original list (view-based)
```

##### 🎯 Quick Sorting Cheat Sheet for Interviews

| Need | Code |
|------|------|
| Sort array ascending | `Arrays.sort(arr)` |
| Sort array descending | `Integer[] → Arrays.sort(arr, Collections.reverseOrder())` |
| Sort list ascending | `list.sort(null)` or `Collections.sort(list)` |
| Sort list descending | `list.sort(Collections.reverseOrder())` |
| Sort by custom field | `list.sort(Comparator.comparing(Obj::getField))` |
| Sort by field descending | `list.sort(Comparator.comparing(Obj::getField).reversed())` |
| Multi-level sort | `Comparator.comparing(A).thenComparing(B)` |
| Sort 2D array by col 0 | `Arrays.sort(arr, (a,b) -> Integer.compare(a[0], b[0]))` |
| Sort map by value | `entries.sort(Map.Entry.comparingByValue())` |
| Top K elements | `PriorityQueue` (min-heap size K) or sort + take first K |
| K-th largest | `PriorityQueue` (min-heap size K) → peek = k-th largest |
| Partial sort | `Arrays.sort(arr, fromIndex, toIndex)` |
| Stable sort needed | `Collections.sort` / `Arrays.sort(Object[])` (TimSort) |
| Sort characters in string | `char[] c = s.toCharArray(); Arrays.sort(c); new String(c)` |

---

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
