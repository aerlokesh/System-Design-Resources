# 🎯 Topic 2: Collections Framework Deep Dive

> **Java Interview — Deep Dive**
> A comprehensive guide covering the Java Collections Framework — data structures, internal implementations, performance characteristics, and how to articulate these with depth and precision in a Java interview.

---

## Table of Contents

1. [Collections Hierarchy Overview](#collections-hierarchy-overview)
2. [List Implementations — ArrayList vs LinkedList](#list-implementations--arraylist-vs-linkedlist)
3. [Set Implementations — HashSet, LinkedHashSet, TreeSet](#set-implementations--hashset-linkedhashset-treeset)
4. [Map Implementations — HashMap Deep Dive](#map-implementations--hashmap-deep-dive)
5. [Queue & Deque — PriorityQueue, ArrayDeque](#queue--deque--priorityqueue-arraydeque)
6. [ConcurrentCollections](#concurrent-collections)
7. [Comparable vs Comparator](#comparable-vs-comparator)
8. [Iterator & Fail-Fast vs Fail-Safe](#iterator--fail-fast-vs-fail-safe)
9. [Collections Utility Class](#collections-utility-class)
10. [Immutable Collections (Java 9+)](#immutable-collections-java-9)
11. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
12. [Common Interview Mistakes](#common-interview-mistakes)
13. [Choosing the Right Collection — Decision Guide](#choosing-the-right-collection--decision-guide)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Collections Hierarchy Overview

```
                    Iterable<E>
                        │
                   Collection<E>
                   /    |     \
              List<E>  Set<E>  Queue<E>
              /   \      |   \      \
     ArrayList  LinkedList  HashSet  TreeSet  PriorityQueue
                    │
                  Deque<E>
                    │
               ArrayDeque

                   Map<K,V>  (separate hierarchy — NOT Collection)
                  /    |    \
           HashMap  TreeMap  LinkedHashMap
```

### The Critical Insight

> `Map` does NOT extend `Collection`. This is an extremely common interview trick question. Maps represent key-value associations, not single-element collections.

---

## List Implementations — ArrayList vs LinkedList

### ArrayList — Dynamic Array

**Internal Structure**: Resizable array (`Object[]`)

```java
// Initial capacity = 10 (default)
ArrayList<String> list = new ArrayList<>();

// With initial capacity (optimization)
ArrayList<String> list = new ArrayList<>(1000);
```

**How Resizing Works**:
1. Default initial capacity: **10**
2. When full: new capacity = **oldCapacity + (oldCapacity >> 1)** → 1.5x growth
3. Uses `Arrays.copyOf()` → O(n) operation
4. **Interview Key**: Pre-size when you know the approximate size!

```java
// ❌ BAD — Triggers multiple resizes
ArrayList<Integer> list = new ArrayList<>();
for (int i = 0; i < 1_000_000; i++) list.add(i);

// ✅ GOOD — Single allocation
ArrayList<Integer> list = new ArrayList<>(1_000_000);
for (int i = 0; i < 1_000_000; i++) list.add(i);
```

### LinkedList — Doubly-Linked List

**Internal Structure**: Doubly-linked nodes (`Node<E>` with prev/next pointers)

```java
// Each node:
private static class Node<E> {
    E item;
    Node<E> next;
    Node<E> prev;
}
```

### ArrayList vs LinkedList — Complete Comparison

| Operation | ArrayList | LinkedList |
|-----------|-----------|------------|
| `get(index)` | **O(1)** — direct array access | O(n) — traverse from head/tail |
| `add(end)` | **O(1)** amortized | **O(1)** |
| `add(index)` | O(n) — shift elements | O(n) — find position + O(1) insert |
| `remove(index)` | O(n) — shift elements | O(n) — find position + O(1) unlink |
| `contains(obj)` | O(n) | O(n) |
| Memory per element | **~4 bytes** (reference) | ~24 bytes (node + 2 pointers) |
| Cache locality | **Excellent** (contiguous) | Poor (scattered in heap) |
| Iterator.remove() | O(n) | **O(1)** |

> **Interview Verdict**: **Almost always use ArrayList**. LinkedList wins only for frequent add/remove at both ends (use `ArrayDeque` instead) or when you're iterating and removing simultaneously.

---

## Set Implementations — HashSet, LinkedHashSet, TreeSet

### HashSet — Hash Table

**Internally**: Backed by a `HashMap<E, Object>` where every value is a dummy `PRESENT` object.

```java
// Surprise! HashSet IS a HashMap internally
private transient HashMap<E,Object> map;
private static final Object PRESENT = new Object();

public boolean add(E e) {
    return map.put(e, PRESENT) == null;
}
```

| Property | Value |
|----------|-------|
| Order | **No guaranteed order** |
| Null | **One null allowed** |
| Time complexity | O(1) avg for add/remove/contains |
| Thread-safe | No |

### LinkedHashSet — Insertion-Ordered Hash Table

```java
Set<String> set = new LinkedHashSet<>();
set.add("C"); set.add("A"); set.add("B");
// Iteration order: C, A, B (insertion order preserved)
```

### TreeSet — Sorted Set (Red-Black Tree)

```java
Set<Integer> set = new TreeSet<>();
set.add(3); set.add(1); set.add(2);
// Iteration order: 1, 2, 3 (natural ordering)

// Custom ordering
Set<String> set = new TreeSet<>(Comparator.reverseOrder());
```

| Property | HashSet | LinkedHashSet | TreeSet |
|----------|---------|---------------|---------|
| Order | None | Insertion | Sorted |
| Null | 1 null | 1 null | **No null** (NPE) |
| Performance | **O(1)** | O(1) | O(log n) |
| Backed by | HashMap | LinkedHashMap | TreeMap |

---

## Map Implementations — HashMap Deep Dive

### HashMap Internal Structure (Java 8+)

**Bucket array** + **linked list** → **red-black tree** (when bucket grows large)

```
HashMap internals:
┌──────────────────────────────────────────────┐
│ Node[] table (bucket array)                   │
│ ┌───┬───┬───┬───┬───┬───┬───┬───┐           │
│ │ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │ ...      │
│ └─│─┴───┴─│─┴───┴───┴─│─┴───┴───┘           │
│   │       │           │                       │
│   ▼       ▼           ▼                       │
│  [K,V]  [K,V]       [K,V]                    │
│   │       │           │                       │
│   ▼       ▼           ▼                       │
│  [K,V]  [K,V]     Red-Black                  │
│   │              Tree (≥8)                    │
│   ▼                                           │
│  null                                         │
└──────────────────────────────────────────────┘
```

### Key Numbers (Interview Gold)

| Property | Value |
|----------|-------|
| Default initial capacity | **16** |
| Default load factor | **0.75** |
| Treeify threshold | **8** (linked list → tree) |
| Untreeify threshold | **6** (tree → linked list) |
| Resize factor | **2x** (doubles capacity) |

### How `put(key, value)` Works

1. Calculate `hash(key)`: `(h = key.hashCode()) ^ (h >>> 16)` — spreads higher bits
2. Find bucket index: `(n - 1) & hash` (bitwise AND with capacity - 1)
3. If bucket is empty → insert new node
4. If bucket has entries:
   a. If key exists (equals()) → replace value
   b. If linked list + size < 8 → append to list
   c. If linked list + size ≥ 8 → treeify to red-black tree
5. If `size > capacity * loadFactor` → **resize** (double capacity, rehash all entries)

### hashCode() and equals() Contract (Interview Critical)

```java
// THE RULES:
// 1. If a.equals(b) → a.hashCode() == b.hashCode()    (MANDATORY)
// 2. If a.hashCode() == b.hashCode() → a.equals(b)     (NOT necessarily true — collision)
// 3. If !a.equals(b) → hashCode may or may not differ   (different is better for perf)

public class Employee {
    private int id;
    private String name;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Employee that = (Employee) o;
        return id == that.id && Objects.equals(name, that.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name);  // Uses same fields as equals!
    }
}
```

> **Interview Trap**: If you override `equals()` but NOT `hashCode()`, HashMap/HashSet break! Two "equal" objects go to different buckets.

### HashMap vs TreeMap vs LinkedHashMap

| Feature | HashMap | TreeMap | LinkedHashMap |
|---------|---------|---------|---------------|
| Order | None | Sorted by key | Insertion order |
| Null keys | 1 null key | **No null key** | 1 null key |
| Performance | O(1) | O(log n) | O(1) |
| Backed by | Hash table | Red-black tree | Hash table + linked list |
| Interface | Map | **NavigableMap** | Map |

### ConcurrentHashMap vs Hashtable vs SynchronizedMap

| Feature | HashMap | Hashtable | SynchronizedMap | ConcurrentHashMap |
|---------|---------|-----------|-----------------|-------------------|
| Thread-safe | ❌ | ✅ | ✅ | ✅ |
| Null key/value | ✅/✅ | ❌/❌ | ✅/✅ | ❌/❌ |
| Lock granularity | N/A | Entire map | Entire map | **Per-segment/bucket** |
| Performance (concurrent) | N/A | Poor | Poor | **Excellent** |
| Legacy | No | **Yes (legacy)** | No | No |
| Fail-safe iterator | No | No | No | **Yes** |

---

## Queue & Deque — PriorityQueue, ArrayDeque

### PriorityQueue — Min-Heap

```java
// Default: min-heap (natural ordering)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
minHeap.offer(3); minHeap.offer(1); minHeap.offer(2);
minHeap.poll(); // Returns 1 (smallest)

// Max-heap
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
```

| Operation | Time |
|-----------|------|
| `offer()` / `add()` | O(log n) |
| `poll()` / `remove()` | O(log n) |
| `peek()` | O(1) |
| `contains()` | O(n) |

### ArrayDeque — Double-Ended Queue

```java
// Better than Stack and LinkedList for queue/stack operations
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1); stack.push(2);
stack.pop(); // Returns 2

Deque<Integer> queue = new ArrayDeque<>();
queue.offer(1); queue.offer(2);
queue.poll(); // Returns 1
```

> **Interview Key**: Use `ArrayDeque` instead of `Stack` (legacy) and instead of `LinkedList` for queue operations (better performance due to cache locality).

---

## Comparable vs Comparator

```java
// Comparable — Natural ordering (class implements it)
public class Employee implements Comparable<Employee> {
    private int id;
    
    @Override
    public int compareTo(Employee other) {
        return Integer.compare(this.id, other.id);
    }
}

// Comparator — External/custom ordering
Comparator<Employee> byName = Comparator.comparing(Employee::getName);
Comparator<Employee> byNameThenAge = Comparator
    .comparing(Employee::getName)
    .thenComparingInt(Employee::getAge);

// Sort with comparator
employees.sort(byNameThenAge);
Collections.sort(employees, byName.reversed());
```

| Feature | Comparable | Comparator |
|---------|-----------|------------|
| Package | `java.lang` | `java.util` |
| Method | `compareTo(T)` | `compare(T, T)` |
| Modifies class | Yes | No |
| Multiple orderings | No (one per class) | Yes (unlimited) |
| Use case | Natural ordering | Custom/ad-hoc ordering |

---

## Iterator & Fail-Fast vs Fail-Safe

### Fail-Fast Iterators (ArrayList, HashMap, HashSet)

```java
List<String> list = new ArrayList<>(List.of("A", "B", "C"));
Iterator<String> it = list.iterator();
list.add("D");    // Structural modification
it.next();        // 💥 ConcurrentModificationException!
```

### Fail-Safe Iterators (ConcurrentHashMap, CopyOnWriteArrayList)

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("A", 1); map.put("B", 2);
for (String key : map.keySet()) {
    map.put("C", 3);  // ✅ No exception — works on a snapshot/segment
}
```

### Safe Removal During Iteration

```java
// ❌ BAD — ConcurrentModificationException
for (String item : list) {
    if (item.equals("B")) list.remove(item);
}

// ✅ GOOD — Use Iterator.remove()
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("B")) it.remove();
}

// ✅ GOOD — Java 8+ removeIf
list.removeIf(item -> item.equals("B"));
```

---

## Immutable Collections (Java 9+)

```java
// Java 9+ factory methods
List<String> list = List.of("A", "B", "C");          // Immutable
Set<String> set = Set.of("A", "B", "C");              // Immutable
Map<String, Integer> map = Map.of("A", 1, "B", 2);   // Immutable

// Java 10+ copyOf
List<String> copy = List.copyOf(mutableList);          // Immutable copy

// Attempting modification → UnsupportedOperationException
list.add("D");  // 💥 UnsupportedOperationException
```

> **Interview Trap**: `Collections.unmodifiableList(list)` returns a **view** — changes to the original list are visible. `List.copyOf()` makes an actual **copy**.

---

## Choosing the Right Collection — Decision Guide

```
Need key-value pairs?
├── YES → Need sorted keys?
│         ├── YES → TreeMap
│         └── NO → Need insertion order?
│                  ├── YES → LinkedHashMap
│                  └── NO → Thread-safe?
│                           ├── YES → ConcurrentHashMap
│                           └── NO → HashMap
└── NO → Need unique elements?
         ├── YES → Need sorted?
         │         ├── YES → TreeSet
         │         └── NO → Need insertion order?
         │                  ├── YES → LinkedHashSet
         │                  └── NO → HashSet
         └── NO → Need FIFO queue?
                  ├── YES → Need priority?
                  │         ├── YES → PriorityQueue
                  │         └── NO → ArrayDeque (as queue)
                  └── NO → Need stack (LIFO)?
                           ├── YES → ArrayDeque (as stack)
                           └── NO → Need random access?
                                    ├── YES → ArrayList
                                    └── NO → LinkedList (rare)
```

---

## Interview Talking Points & Scripts

### "Explain how HashMap works internally"

> *"HashMap uses an array of buckets. When you put a key-value pair, it computes hash(key), finds the bucket index using (capacity-1) & hash, and stores it there. If multiple keys hash to the same bucket (collision), they form a linked list. In Java 8+, when a bucket has 8+ entries, it converts to a red-black tree for O(log n) lookups instead of O(n). The default capacity is 16, load factor is 0.75, so it resizes at 12 entries by doubling capacity and rehashing everything."*

### "Why do we need to override both equals() and hashCode()?"

> *"HashMap uses hashCode() to find the bucket and equals() to find the exact key within that bucket. If two objects are equal per equals() but have different hashCode values, they'll go to different buckets and HashMap won't find the match. This violates the contract: equal objects MUST have equal hash codes."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "HashMap is sorted" | HashMap has NO ordering guarantee |
| "LinkedList is faster for insert" | ArrayList is faster for most real-world cases due to cache locality |
| "Use Stack class" | Stack is legacy; use `ArrayDeque` |
| "Map extends Collection" | Map is a separate hierarchy |
| "HashSet stores unique values" | HashSet uses `equals()` + `hashCode()` — without proper overrides, duplicates appear |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│              COLLECTIONS CHEAT SHEET                        │
├────────────────────────────────────────────────────────────┤
│ ArrayList  → O(1) get, O(1) amortized add, 1.5x growth    │
│ LinkedList → O(n) get, O(1) add at ends, 3x memory        │
│ HashMap    → O(1) avg, 16 init, 0.75 LF, tree at 8        │
│ TreeMap    → O(log n), sorted by key, no null keys         │
│ HashSet    → HashMap wrapper, O(1), one null allowed       │
│ TreeSet    → TreeMap wrapper, O(log n), no nulls           │
│ PriorityQueue → Min-heap, O(log n) offer/poll             │
│ ArrayDeque → Better than Stack & LinkedList for LIFO/FIFO  │
│ ConcurrentHashMap → Thread-safe, bucket-level locking      │
│                                                            │
│ KEY RULES:                                                 │
│ • Override equals() → MUST override hashCode()             │
│ • Map ≠ Collection (separate hierarchy)                    │
│ • Fail-fast: ArrayList, HashMap (CME on modification)      │
│ • Fail-safe: ConcurrentHashMap, CopyOnWriteArrayList       │
│ • List.of() → truly immutable (Java 9+)                   │
│ • Collections.unmodifiableList() → just a view             │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Strings, Immutability & String Pool →](./03-strings-immutability-and-string-pool.md)
