# Custom HashMap - HELLO Interview Framework

> **Companies**: PayPal, Walmart, Flipkart, Amazon, Google, Microsoft, Apple +13 more  
> **Difficulty**: Medium  
> **Primary Pattern**: Data Structure Design (Hashing + Chaining)  
> **Time**: 35 minutes  
> **Reference**: [HelloInterview LLD Delivery](https://www.hellointerview.com/learn/low-level-design/in-a-hurry/delivery)

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions to Ask
- "What operations?" → put, get, remove, containsKey
- "Handle collisions?" → Yes, chaining (linked list per bucket)
- "Fixed size or resizable?" → Dynamic resizing (like Java HashMap)
- "Support generics?" → Yes, `<K, V>`
- "Null key support?" → Yes (stored at index 0, like Java HashMap)
- "Thread safety?" → P1, mention ConcurrentHashMap

### Functional Requirements

#### Must Have (P0)
1. **Core Operations**
   - `put(key, value)` → insert or update, return previous value
   - `get(key)` → retrieve value, null if absent
   - `remove(key)` → delete entry, return removed value
   - `containsKey(key)` → boolean existence check

2. **Collision Handling**
   - Separate chaining with linked list per bucket
   - Multiple keys with same hash share a bucket
   - Traverse chain to find matching key

3. **Dynamic Resizing**
   - When load factor exceeds threshold (0.75), double capacity
   - Rehash all existing entries into new table
   - Capacity always power of 2 (for bitwise optimization)

4. **Null Key Support**
   - Null key hashes to index 0
   - At most one null key (overwrite on duplicate)

5. **Additional Operations**
   - `size()`, `isEmpty()`, `clear()`
   - `keys()`, `values()` for iteration

#### Nice to Have (P1)
- Thread-safe version (ConcurrentHashMap)
- Tree-ification of long chains (Java 8+: chain > 8 → red-black tree)
- Iterator with fail-fast behavior
- Custom load factor

### Non-Functional Requirements
- **Average O(1)** for put/get/remove
- **Space**: O(N) where N = entries
- **Load Factor**: 0.75 default (balance between time and space)

### Constraints & Assumptions
- Keys must implement `equals()` and `hashCode()` correctly
- Initial capacity = 16 (default)
- Resize doubles capacity (16 → 32 → 64 → ...)
- Capacity always power of 2

---

## 2️⃣ Core Entities

### Internal Structure Diagram
```
MyHashMap<K, V>
  │
  ├─ table: Entry<K,V>[]     ← array of bucket heads
  │    │
  │    ├─ [0]: Entry("null"=val) → null
  │    ├─ [1]: null
  │    ├─ [2]: Entry("Bob"=30) → Entry("Dave"=25) → null    ← chain!
  │    ├─ [3]: Entry("Alice"=25) → null
  │    ├─ ...
  │    └─ [15]: null
  │
  ├─ size: int                ← number of entries
  ├─ capacity: int            ← table.length (always power of 2)
  ├─ loadFactor: float        ← 0.75 default
  └─ threshold: int           ← capacity × loadFactor (resize trigger)

Entry<K, V> (linked list node):
  ┌─────────────────────┐
  │  key: K              │
  │  value: V            │
  │  hash: int (cached)  │
  │  next: Entry<K,V>    │──→ next node in chain (or null)
  └─────────────────────┘
```

### Class: Entry<K, V> (Static Inner)
| Attribute | Type | Description |
|-----------|------|-------------|
| key | K | The key (final) |
| value | V | The value (mutable for updates) |
| hash | int | Cached hash (avoid recomputing) |
| next | Entry<K,V> | Next in collision chain |

**Why cache hash?** During resize, we need hash for every entry. Computing `hashCode()` is potentially expensive.

### Class: MyHashMap<K, V>
| Attribute | Type | Description |
|-----------|------|-------------|
| table | Entry<K,V>[] | Array of bucket heads |
| size | int | Number of key-value pairs |
| capacity | int | Table length (power of 2) |
| loadFactor | float | 0.75 default |
| threshold | int | capacity × loadFactor |

---

## 3️⃣ API Design

```java
class MyHashMap<K, V> {
    /** Insert or update. Returns previous value or null. O(1) avg */
    V put(K key, V value);
    
    /** Retrieve value. Returns null if absent. O(1) avg */
    V get(K key);
    
    /** Remove entry. Returns removed value or null. O(1) avg */
    V remove(K key);
    
    /** Check if key exists. O(1) avg */
    boolean containsKey(K key);
    
    /** Number of entries */
    int size();
    
    /** True if empty */
    boolean isEmpty();
    
    /** Remove all entries */
    void clear();
    
    /** All keys (unordered) */
    List<K> keys();
    
    /** All values (unordered) */
    List<V> values();
}
```

---

## 4️⃣ Data Flow

### Scenario 1: put("Alice", 25) — No Collision

```
put("Alice", 25):
  │
  ├─ hash = hash("Alice")
  │   ├─ h = "Alice".hashCode() → 63431934
  │   └─ h ^ (h >>> 16) → 63432903  (spread high bits)
  │
  ├─ index = hash & (capacity - 1)
  │   └─ 63432903 & 15 = 7  (bitwise AND, power-of-2 trick)
  │
  ├─ table[7] == null?  → YES (empty bucket)
  │
  ├─ table[7] = new Entry("Alice", 25, hash, null)
  │
  ├─ size++ → 1
  ├─ size(1) > threshold(12)? → NO, no resize
  └─ Return: null (no previous value)
```

### Scenario 2: put("Bob", 30) — Collision at Same Bucket

```
put("Bob", 30):    ← hash("Bob") & 15 = 7 (same bucket as Alice!)
  │
  ├─ index = 7
  ├─ table[7] != null → traverse chain
  │   ├─ entry("Alice"): hash match? NO (different hash)
  │   │   → key equals? NO → continue
  │   └─ entry.next == null → end of chain
  │
  ├─ Add at HEAD of chain:
  │   table[7] = new Entry("Bob", 30, hash, old_head=Alice_entry)
  │   chain: Bob → Alice → null
  │
  ├─ size++ → 2
  └─ Return: null
```

### Scenario 3: put("Alice", 26) — Update Existing

```
put("Alice", 26):
  │
  ├─ index = 7
  ├─ table[7] != null → traverse chain
  │   ├─ entry("Bob"): hash match? NO → continue
  │   └─ entry("Alice"): hash match? YES
  │       └─ key equals? YES → UPDATE!
  │           old = 25, entry.value = 26
  │
  ├─ size unchanged (no new entry)
  └─ Return: 25 (previous value)
```

### Scenario 4: Dynamic Resize

```
State: size=12, capacity=16, threshold=12, loadFactor=0.75

put(new_key, new_value):
  ├─ Insert normally... size++ → 13
  ├─ size(13) > threshold(12)?  → YES! RESIZE!
  │
  ├─ resize():
  │   ├─ newCapacity = 16 * 2 = 32
  │   ├─ newTable = new Entry[32]
  │   │
  │   ├─ For each entry in old table:
  │   │   ├─ Recompute: newIndex = entry.hash & (32 - 1)
  │   │   │   (bitwise AND with 31 instead of 15)
  │   │   │   Some entries stay, some move to newIndex = oldIndex + 16
  │   │   └─ Insert into newTable[newIndex]
  │   │
  │   ├─ table = newTable
  │   ├─ capacity = 32
  │   └─ threshold = 32 * 0.75 = 24
```

### Scenario 5: get("Alice") — Chain Traversal

```
get("Alice"):
  │
  ├─ hash = hash("Alice") → 63432903
  ├─ index = 63432903 & (capacity - 1) → 7
  │
  ├─ table[7]: Entry("Bob")
  │   ├─ hash match? NO → next
  │   └─ Entry("Alice")
  │       ├─ hash match? YES
  │       └─ key.equals("Alice")? YES → return 26
  │
  └─ Return: 26
```

---

## 5️⃣ Design

### Hash Function: Why XOR Shift?

Java HashMap's `hash()` function:
```java
int hash(K key) {
    if (key == null) return 0;
    int h = key.hashCode();
    return h ^ (h >>> 16);  // XOR high 16 bits into low 16 bits
}
```

**Why?** Because index = `hash & (capacity - 1)`:
- With capacity=16, only bottom 4 bits determine the bucket
- If hashCode() has poor distribution in low bits, many collisions!
- XOR shift ensures high bits influence the index too

```
hashCode(): 1010 1100 0011 0111 0000 0000 0000 0101
>>> 16:     0000 0000 0000 0000 1010 1100 0011 0111
XOR:        1010 1100 0011 0111 1010 1100 0011 0010
& 15:                                         0010 → index 2

Without XOR: 0000 0000 0000 0101 & 15 = 0101 → index 5
(ignores top 28 bits entirely!)
```

### Index Calculation: Why Bitwise AND?

```java
int index = hash & (capacity - 1);  // same as hash % capacity
```

**Why not modulo (`%`)?** Because capacity is always a power of 2:
- `hash & (16-1)` = `hash & 0x0F` → extracts bottom 4 bits
- Bitwise AND is ~10x faster than modulo on most CPUs
- **Only works when capacity is power of 2!** (that's why we enforce it)

### Collision Resolution: Separate Chaining

**Why chaining over open addressing?**

| Approach | Pros | Cons |
|----------|------|------|
| **Separate Chaining** | Simple, graceful degradation, handles high load | Extra memory (node pointers) |
| **Open Addressing (Linear Probing)** | Cache-friendly, no pointers | Clustering, tricky deletion |
| **Robin Hood Hashing** | Low variance in probe length | Complex implementation |

Chaining is the **interview default** — simple to code, easy to explain, same as Java HashMap.

### Resize Strategy

**When**: `size > capacity * loadFactor`
**How**: Double capacity, rehash all entries

```
Load factor = 0.75 is optimal because:
- < 0.5: Too many empty buckets (wastes memory)
- 0.75: ~3-4 entries per bucket on average (good balance)
- > 0.9: Many collisions, chains get long (O(N) degradation)
```

**Rehash is O(N)** but happens infrequently. Amortized cost per put is still O(1).

### Why Power-of-2 Capacity?

1. **Bitwise AND** instead of modulo (10x faster)
2. **Predictable resize** behavior (entries either stay or move by +capacity)
3. **Java HashMap convention** (interviewers expect this)

```java
int nextPowerOf2(int n) {
    int p = 1;
    while (p < n) p <<= 1;  // 1, 2, 4, 8, 16, ...
    return p;
}
// nextPowerOf2(5) → 8
// nextPowerOf2(17) → 32
```

### Data Structure Choices

| Structure | Purpose | Why |
|-----------|---------|-----|
| `Entry<K,V>[]` table | Bucket array | Direct index access O(1) |
| Linked list (Entry.next) | Collision chain | Simple, O(K) traversal |
| Cached `hash` in Entry | Avoid recomputing | hashCode() can be expensive |
| Power-of-2 capacity | Bitwise optimization | `& (cap-1)` instead of `% cap` |

---

### Complete Implementation

See `CustomHashMapSystem.java` for full runnable code including:
- Generic `MyHashMap<K, V>` with chaining
- Hash spreading with XOR shift
- Dynamic resizing with rehash
- Null key support
- Forced collision demo
- Performance stats (bucket utilization, max chain length)

---

## 6️⃣ Deep Dives

### Deep Dive 1: What Happens During Resize?

```
Before resize (capacity=4, size=4):
  [0]: A=1 → D=4 → null     (2 entries, hash%4 = 0 for both)
  [1]: null
  [2]: B=2 → null
  [3]: C=3 → null

After resize (capacity=8):
  Recompute: index = hash & 7 (was hash & 3)
  
  [0]: A=1 → null             (A's hash & 7 = 0, stays)
  [1]: null
  [2]: B=2 → null             (stays)
  [3]: C=3 → null             (stays)
  [4]: D=4 → null             (D's hash & 7 = 4, moved! old=0, new=0+4)
  [5-7]: null

Key insight: Each entry either stays at index i or moves to i + oldCapacity.
Java 8 exploits this: check bit (hash & oldCapacity) == 0 → stays, else moves.
```

### Deep Dive 2: Java 8 Treeification

When a chain exceeds 8 nodes, Java 8 converts it to a red-black tree:
- **Chain (≤8)**: O(N) lookup
- **Tree (>8)**: O(log N) lookup
- **Untreeify**: When chain shrinks below 6, convert back to linked list

```
Threshold chosen because:
- Under good hash distribution, P(chain > 8) ≈ 0.00000006
- If it happens, hashCode() is terrible → tree mitigates damage
- We mention this in interview but DON'T implement it
```

### Deep Dive 3: Why `equals()` AND `hashCode()` Together?

**Contract**: If `a.equals(b)`, then `a.hashCode() == b.hashCode()`

```
Scenario: Two different objects with same hashCode but not equal
  put(key1, "A")  → hash=5, index=5, stored
  put(key2, "B")  → hash=5, index=5
    → chain at [5]: traverse
    → key1.hashCode() == key2.hashCode()? YES (both 5)
    → key1.equals(key2)? NO → different key → add to chain
    
  get(key2)
    → hash=5, index=5
    → traverse chain: key1.equals(key2)? NO → next
    → key2.equals(key2)? YES → return "B" ✓
```

**If only hashCode checked (no equals)**: Colliding keys would overwrite each other!

### Deep Dive 4: Thread Safety Options

| Approach | How | Performance | When |
|----------|-----|-------------|------|
| `synchronized` block | Lock entire map | Low concurrency | Simple apps |
| `Collections.synchronizedMap()` | Wrapper with global lock | Same as above | Quick fix |
| `ConcurrentHashMap` | Segment-level / CAS | High concurrency | Production |
| `ReadWriteLock` | Multiple readers, exclusive writer | Good read-heavy | Custom maps |

**In interview**: Mention ConcurrentHashMap. Don't implement it (too complex for 35 min).

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Null key | hash(null) = 0, stored at index 0 |
| Null value | Allowed, stored normally |
| Duplicate key | Update value, return old |
| All keys same hash | Single chain of length N (worst case O(N)) |
| Very large map | Multiple resizes, amortized O(1) |
| Remove from empty bucket | Returns null |
| Remove middle of chain | prev.next = current.next |

### Deep Dive 6: Complexity Analysis

| Operation | Average | Worst (all collisions) |
|-----------|---------|----------------------|
| put | O(1) | O(N) |
| get | O(1) | O(N) |
| remove | O(1) | O(N) |
| containsKey | O(1) | O(N) |
| resize | O(N) | O(N) |
| Space | O(N) | O(N + capacity) |

**Amortized put (including resize)**: O(1)

Proof: Resize happens when size doubles. Cost of resize = O(N). 
Between resizes, N inserts happen. Each insert O(1). 
Total: O(N) + N × O(1) = O(N). Per operation: O(N)/N = O(1).

### Deep Dive 7: HashMap vs TreeMap vs LinkedHashMap

| Feature | HashMap | TreeMap | LinkedHashMap |
|---------|---------|---------|---------------|
| Order | None | Sorted (key) | Insertion order |
| put/get | O(1) | O(log N) | O(1) |
| Underlying | Array + chains | Red-black tree | HashMap + doubly-linked list |
| Null keys | One allowed | Not allowed | One allowed |
| Use when | General purpose | Need sorted keys | Need insertion order / LRU |

---

## 📋 Interview Checklist

### What Interviewer Looks For:
- [ ] **Hash Function**: XOR shift to spread bits, explain why
- [ ] **Index Calculation**: Bitwise AND (not modulo), power-of-2 capacity
- [ ] **Collision Handling**: Separate chaining, traverse chain with equals()
- [ ] **Dynamic Resize**: When load factor exceeded, double + rehash
- [ ] **equals/hashCode Contract**: Both needed, explain why
- [ ] **Null Key Support**: Hash to 0, special handling
- [ ] **Complexity**: O(1) average, O(N) worst, amortized resize proof
- [ ] **Java 8 Treeification**: Mention chains > 8 → red-black tree
- [ ] **Thread Safety**: Mention ConcurrentHashMap as production solution

### Time Spent:
| Phase | Target |
|-------|--------|
| Requirements | 3-5 min |
| Core Design | 5 min |
| API + Hash Function | 5 min |
| Implementation | 15-20 min |
| Deep Dives | 5-10 min |
| **Total** | **~35 min** |
