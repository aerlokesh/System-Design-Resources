# 🎯 LLD Topic 13: Collections & Data Structure Selection

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering Java Collections Framework — when to use List vs Set vs Map, performance characteristics, choosing the right data structure for LLD problems, thread-safe collections, and common pitfalls.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Decision Framework](#decision-framework)
3. [List Implementations Deep Dive](#list-implementations)
4. [Set Implementations Deep Dive](#set-implementations)
5. [Map Implementations Deep Dive](#map-implementations)
6. [Queue and Deque Deep Dive](#queue-and-deque)
7. [Thread-Safe Collections](#thread-safe-collections)
8. [Performance Comparison Table](#performance-comparison)
9. [Advanced Data Structures](#advanced-data-structures)
10. [Data Structure Selection for LLD Problems](#lld-problem-mapping)
11. [Real-World Selection Examples](#real-world-examples)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Choosing the right data structure is a **critical LLD skill**. The wrong choice can mean O(n) lookups where O(1) is possible, thread-safety bugs, or wasted memory. In LLD interviews, **justifying your data structure choice** is as important as the code itself.

```
The question to always ask:
  "What is the DOMINANT OPERATION on this data?"
  
  Frequent lookups by key?     → HashMap           O(1)
  Frequent sorted access?      → TreeMap            O(log n)
  Frequent contains checks?    → HashSet            O(1)
  Frequent add/remove at ends? → ArrayDeque          O(1)
  Need priority ordering?      → PriorityQueue       O(log n)
  Need producer-consumer?      → BlockingQueue        Thread-safe
  Need LRU eviction?           → LinkedHashMap        O(1) with auto-evict
```

---

## Decision Framework

```
START HERE:
  
  Do you need key-value pairs?
  ├── YES → Do you need sorted keys?
  │         ├── YES → TreeMap (O(log n) ops, range queries)
  │         └── NO  → Do you need insertion order?
  │                   ├── YES → LinkedHashMap (O(1) + order)
  │                   └── NO  → HashMap (O(1) default choice)
  │
  └── NO → Do you need uniqueness?
           ├── YES → Do you need sorted order?
           │         ├── YES → TreeSet (O(log n), range ops)
           │         └── NO  → HashSet (O(1) contains)
           │
           └── NO → Do you need random access by index?
                    ├── YES → ArrayList (O(1) get by index)
                    └── NO  → Do you need FIFO/LIFO?
                             ├── FIFO → ArrayDeque as Queue
                             ├── LIFO → ArrayDeque as Stack
                             └── Priority → PriorityQueue
```

---

## List Implementations Deep Dive

### ArrayList — The Default List

```java
// ArrayList: Dynamic array. O(1) random access, O(1) amortized add at end
List<User> users = new ArrayList<>();

// When to use:
// ✅ Random access by index needed
// ✅ Iteration-heavy (cache-friendly due to contiguous memory)
// ✅ Add/remove mostly at the END
// ❌ Frequent insertions/deletions in the MIDDLE (O(n) shifting)

// Sizing tip: pre-size when you know the count
List<Order> orders = new ArrayList<>(1000);  // Avoids resizing

// Common operations and complexity:
users.get(5);           // O(1) — direct index access
users.add(user);        // O(1) amortized — at end
users.add(0, user);     // O(n) — shifts all elements
users.remove(5);        // O(n) — shifts elements after index
users.contains(user);   // O(n) — linear scan
users.indexOf(user);    // O(n) — linear scan
```

### LinkedList — Rarely Used (Know Why)

```java
// LinkedList: Doubly-linked list. O(1) add/remove at ends if you have the node.
LinkedList<Task> tasks = new LinkedList<>();

// When to use:
// ✅ Implementing Deque (double-ended queue)
// ❌ Almost everything else — ArrayList is faster due to CPU cache locality

// Why ArrayList usually beats LinkedList:
//   ArrayList: elements in contiguous memory → CPU cache hits
//   LinkedList: elements scattered in heap → CPU cache misses
//   Result: ArrayList is faster even for some insert/remove operations
```

### CopyOnWriteArrayList — Thread-Safe, Read-Heavy

```java
// Creates a copy of the entire array on each write
List<EventListener> listeners = new CopyOnWriteArrayList<>();

// When to use:
// ✅ Read-heavy, write-rare (Observer pattern listeners)
// ✅ Small lists (< 1000 elements)
// ❌ Write-heavy (each write copies the entire array)
// ❌ Large lists (copying is expensive)

// Perfect for: notification subscribers, configuration watchers
listeners.add(new EmailListener());     // Copies entire array
listeners.add(new SmsListener());       // Copies entire array again

// But reads are lock-free:
for (EventListener l : listeners) {     // No synchronization needed!
    l.onEvent(event);
}
```

---

## Set Implementations Deep Dive

### HashSet — The Default Set

```java
Set<String> uniqueEmails = new HashSet<>();

// O(1) for add, remove, contains — backed by HashMap internally
uniqueEmails.add("alice@test.com");     // O(1)
uniqueEmails.contains("alice@test.com"); // O(1)
uniqueEmails.remove("alice@test.com");  // O(1)

// Requires proper equals() and hashCode() on elements!
// If hashCode is bad (all same bucket), degrades to O(n)
```

### LinkedHashSet — HashSet + Insertion Order

```java
Set<String> recentSearches = new LinkedHashSet<>();
recentSearches.add("laptop");
recentSearches.add("keyboard");
recentSearches.add("mouse");

// Iteration order: laptop, keyboard, mouse (insertion order preserved)
// Still O(1) for add/remove/contains

// Use case: maintain insertion order while ensuring uniqueness
// Example: recently viewed products, search history
```

### TreeSet — Sorted Set with Range Queries

```java
TreeSet<Integer> scores = new TreeSet<>();
scores.addAll(List.of(85, 42, 97, 63, 78, 91));

// O(log n) for add, remove, contains — backed by Red-Black Tree

scores.first();            // 42 — smallest
scores.last();             // 97 — largest
scores.floor(80);          // 78 — largest ≤ 80
scores.ceiling(80);        // 85 — smallest ≥ 80
scores.higher(78);         // 85 — strictly greater than 78
scores.lower(85);          // 78 — strictly less than 85
scores.subSet(60, 90);     // [63, 78, 85] — range query
scores.headSet(80);        // [42, 63, 78] — all < 80
scores.tailSet(80);        // [85, 91, 97] — all ≥ 80

// Use case: leaderboards, scheduling (next event after time T)
```

### EnumSet — Ultra-Fast Enum Sets

```java
// Internally uses bit vector — one bit per enum constant
Set<Permission> adminPerms = EnumSet.of(
    Permission.READ, Permission.WRITE, Permission.DELETE, Permission.ADMIN);
Set<Permission> userPerms = EnumSet.of(Permission.READ);

// Operations are O(1) bitwise operations
adminPerms.contains(Permission.ADMIN);  // O(1) — single bit check
adminPerms.containsAll(userPerms);       // O(1) — single bitwise AND

// Use case: permissions, feature flags, day-of-week sets
Set<DayOfWeek> weekdays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
```

---

## Map Implementations Deep Dive

### HashMap — The Default Map

```java
Map<String, User> userCache = new HashMap<>();

// O(1) average for get/put/containsKey
// Java 8+: buckets switch from linked list to balanced tree at 8 entries
// → worst case degrades from O(n) to O(log n)

// Key operations:
userCache.put("user123", user);                    // O(1)
userCache.get("user123");                           // O(1)
userCache.containsKey("user123");                   // O(1)
userCache.getOrDefault("user456", User.GUEST);      // O(1)
userCache.putIfAbsent("user123", newUser);           // O(1)

// Merge/compute for atomic read-modify-write:
userCache.compute("user123", (key, existing) -> {
    return existing != null ? existing.withLastLogin(now()) : createUser(key);
});

// Initial capacity tip: avoid rehashing
int expectedSize = 1000;
Map<String, User> map = new HashMap<>(expectedSize * 4 / 3 + 1);  // Load factor 0.75
```

### LinkedHashMap — HashMap + Order (LRU Cache!)

```java
// Insertion order by default
Map<String, String> insertionOrder = new LinkedHashMap<>();
insertionOrder.put("c", "3");
insertionOrder.put("a", "1");
insertionOrder.put("b", "2");
// Iteration: c→3, a→1, b→2 (insertion order)

// ACCESS ORDER: The killer feature for LRU caches
Map<String, String> lruCache = new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > 100;  // Auto-evict when size exceeds 100
    }
};

// How it works:
lruCache.put("a", "1");
lruCache.put("b", "2");
lruCache.put("c", "3");
lruCache.get("a");         // Moves "a" to END (most recently used)
// Internal order: b→2, c→3, a→1
// If we exceed capacity, "b" gets evicted (least recently used)

// This is a complete O(1) LRU cache implementation in ~5 lines!
```

### TreeMap — Sorted Map with Range Operations

```java
TreeMap<Long, Event> timeline = new TreeMap<>();
timeline.put(1000L, new Event("login"));
timeline.put(2000L, new Event("purchase"));
timeline.put(3000L, new Event("logout"));
timeline.put(1500L, new Event("browse"));

// O(log n) operations — backed by Red-Black Tree
timeline.firstEntry();              // 1000 → login
timeline.lastEntry();               // 3000 → logout
timeline.floorEntry(1800L);         // 1500 → browse (largest ≤ 1800)
timeline.ceilingEntry(1800L);       // 2000 → purchase (smallest ≥ 1800)
timeline.subMap(1000L, 2500L);      // Events between 1000-2500

// Use case: event timelines, time-series data, scheduled tasks, interval lookups
```

### ConcurrentHashMap — Thread-Safe Map

```java
Map<String, Session> sessions = new ConcurrentHashMap<>();

// Thread-safe WITHOUT external synchronization
// Uses fine-grained locking (bucket-level, not table-level)
// Allows concurrent reads AND concurrent writes to different buckets

// CRITICAL: computeIfAbsent is ATOMIC
sessions.computeIfAbsent("user123", k -> new Session());
// Without ConcurrentHashMap: two threads could create two sessions!

// Atomic merge
sessions.merge("user123", newSession, (oldVal, newVal) -> {
    oldVal.updateLastAccess();
    return oldVal;
});

// WRONG way to use:
// if (!map.containsKey(key)) { map.put(key, value); }  // NOT atomic — race condition!
// RIGHT way:
sessions.putIfAbsent(key, value);  // Atomic
sessions.computeIfAbsent(key, k -> createValue(k));  // Atomic
```

### EnumMap — Fastest Map for Enum Keys

```java
Map<DayOfWeek, List<Meeting>> schedule = new EnumMap<>(DayOfWeek.class);
for (DayOfWeek day : DayOfWeek.values()) {
    schedule.put(day, new ArrayList<>());
}

// Backed by a simple array — O(1) with no hashing overhead
// Faster and more memory-efficient than HashMap for enum keys
```

---

## Queue and Deque Deep Dive

### ArrayDeque — The Default Queue/Stack

```java
// As a Queue (FIFO):
Queue<Task> taskQueue = new ArrayDeque<>();
taskQueue.offer(new Task("A"));   // Add to tail
taskQueue.offer(new Task("B"));
Task next = taskQueue.poll();      // Remove from head → Task A

// As a Stack (LIFO):
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);     // Add to head
stack.push(2);
stack.push(3);
int top = stack.pop();  // Remove from head → 3

// ALWAYS prefer ArrayDeque over Stack class (legacy) and LinkedList
// ArrayDeque is faster due to contiguous memory
```

### PriorityQueue — Min-Heap by Default

```java
// Min-heap: smallest element is always at the head
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
minHeap.offer(30); minHeap.offer(10); minHeap.offer(20);
minHeap.poll();  // 10 (smallest)
minHeap.poll();  // 20
minHeap.poll();  // 30

// Max-heap: largest first
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());

// Custom priority: tasks by deadline
PriorityQueue<Task> taskQueue = new PriorityQueue<>(
    Comparator.comparing(Task::getDeadline));

// Use cases:
// - Job Scheduler: next task by execution time
// - Dijkstra's algorithm: next node by distance
// - Merge K sorted lists: next smallest element
// - Top-K problems: maintain K largest/smallest
```

### BlockingQueue — Thread-Safe Producer-Consumer

```java
BlockingQueue<Job> jobQueue = new LinkedBlockingQueue<>(1000);

// Producer thread:
jobQueue.put(new Job());    // Blocks if queue is FULL (backpressure!)

// Consumer thread:
Job job = jobQueue.take();   // Blocks if queue is EMPTY (waits for work!)

// Non-blocking alternatives:
jobQueue.offer(job);         // Returns false if full
jobQueue.poll();             // Returns null if empty
jobQueue.offer(job, 5, TimeUnit.SECONDS);  // Wait up to 5 seconds

// ArrayBlockingQueue: fixed capacity, fair option
BlockingQueue<Job> fair = new ArrayBlockingQueue<>(100, true);

// PriorityBlockingQueue: thread-safe priority queue
BlockingQueue<Task> prioritized = new PriorityBlockingQueue<>();
```

---

## Thread-Safe Collections

```
Single-threaded:           Thread-safe equivalent:
──────────────────────────────────────────────────
ArrayList                  CopyOnWriteArrayList (read-heavy)
                           Collections.synchronizedList() (write-heavy)
HashMap                    ConcurrentHashMap (preferred)
                           Collections.synchronizedMap() (legacy)
HashSet                    ConcurrentHashMap.newKeySet()
                           Collections.synchronizedSet()
TreeMap                    ConcurrentSkipListMap
TreeSet                    ConcurrentSkipListSet
ArrayDeque                 LinkedBlockingDeque
PriorityQueue              PriorityBlockingQueue
LinkedList (as Queue)      LinkedBlockingQueue

RULE: Prefer java.util.concurrent classes over Collections.synchronized*()
      ConcurrentHashMap > Collections.synchronizedMap(new HashMap<>())
```

---

## Performance Comparison

```
Operation      ArrayList  LinkedList  HashSet  TreeSet  HashMap  TreeMap
────────────────────────────────────────────────────────────────────────
get(index)     O(1)       O(n)        N/A      N/A      N/A      N/A
get(key)       N/A        N/A         N/A      N/A      O(1)     O(log n)
add(end)       O(1)*      O(1)        O(1)     O(log n) O(1)     O(log n)
add(middle)    O(n)       O(1)**      N/A      N/A      N/A      N/A
contains       O(n)       O(n)        O(1)     O(log n) O(1)***  O(log n)
remove         O(n)       O(1)**      O(1)     O(log n) O(1)     O(log n)
min/max        O(n)       O(n)        O(n)     O(log n) O(n)     O(log n)
floor/ceiling  N/A        N/A         N/A      O(log n) N/A      O(log n)
iteration      O(n)       O(n)        O(n)     O(n)     O(n)     O(n)

* amortized    ** if you have the iterator/node    *** containsKey

Memory per element (approximate):
  ArrayList:    ~4 bytes (just a reference in array)
  LinkedList:   ~48 bytes (node + prev + next + element)
  HashMap:      ~48 bytes (entry + key + value + hash + next)
  TreeMap:      ~56 bytes (entry + parent + left + right + color)
  EnumSet:      ~1 bit per enum constant (!)
```

---

## Data Structure Selection for LLD Problems

| LLD Problem | Data Structure | Why |
|---|---|---|
| **LRU Cache** | `LinkedHashMap(access-order=true)` | O(1) get/put with auto-LRU eviction |
| **Parking Lot spots** | `HashMap<SpotId, Vehicle>` | O(1) lookup by spot ID |
| **Available spots by type** | `EnumMap<SpotType, Queue<Spot>>` | Fast per-type spot allocation |
| **Leaderboard** | `TreeMap<Score, Set<User>>` | Sorted by score, range queries |
| **Rate Limiter buckets** | `ConcurrentHashMap<ClientId, Bucket>` | Thread-safe per-client state |
| **Hit Counter** | `AtomicLong[]` circular array | Fixed-window O(1) counting |
| **Task Scheduler** | `PriorityQueue<ScheduledTask>` | Next task by execution time |
| **Event subscribers** | `CopyOnWriteArrayList<Listener>` | Read-heavy, write-rare |
| **Unique ID tracker** | `HashSet<String>` | O(1) duplicate detection |
| **Undo/Redo** | Two `ArrayDeque` stacks | Push/pop O(1) |
| **File system children** | `TreeMap<String, FSNode>` | Sorted directory listing |
| **Time-series events** | `TreeMap<Timestamp, Event>` | Range queries by time |
| **Permission checks** | `EnumSet<Permission>` | O(1) bit-vector operations |
| **Chat message history** | `ArrayDeque<Message>` (bounded) | Recent messages, discard old |

---

## Real-World Selection Examples

### Example 1: Designing a Rate Limiter

```java
class RateLimiter {
    // WHY ConcurrentHashMap: thread-safe, O(1) per-client lookup
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    
    boolean allowRequest(String clientId) {
        // computeIfAbsent is ATOMIC — no race condition creating buckets
        TokenBucket bucket = buckets.computeIfAbsent(clientId, 
            k -> new TokenBucket(maxTokens, refillRate));
        return bucket.tryConsume();
    }
}
```

### Example 2: Designing a Leaderboard

```java
class Leaderboard {
    // WHY TreeMap: sorted by score, range queries for "top N"
    private final TreeMap<Integer, Set<String>> scoreToUsers = new TreeMap<>(Comparator.reverseOrder());
    // WHY HashMap: O(1) lookup of user's current score
    private final HashMap<String, Integer> userToScore = new HashMap<>();
    
    void updateScore(String userId, int newScore) {
        Integer oldScore = userToScore.put(userId, newScore);
        if (oldScore != null) {
            scoreToUsers.get(oldScore).remove(userId);
        }
        scoreToUsers.computeIfAbsent(newScore, k -> new HashSet<>()).add(userId);
    }
    
    List<String> getTopN(int n) {
        List<String> result = new ArrayList<>();
        for (Set<String> users : scoreToUsers.values()) {
            for (String user : users) {
                result.add(user);
                if (result.size() >= n) return result;
            }
        }
        return result;
    }
}
```

### Example 3: Designing a Job Scheduler

```java
class JobScheduler {
    // WHY PriorityQueue: always get the next job by execution time
    private final PriorityQueue<ScheduledJob> jobQueue = new PriorityQueue<>(
        Comparator.comparing(ScheduledJob::getExecutionTime));
    
    // WHY HashMap: O(1) lookup for job cancellation
    private final Map<String, ScheduledJob> jobIndex = new HashMap<>();
    
    void schedule(String jobId, Instant executionTime, Runnable task) {
        ScheduledJob job = new ScheduledJob(jobId, executionTime, task);
        jobQueue.offer(job);
        jobIndex.put(jobId, job);
    }
    
    void cancel(String jobId) {
        ScheduledJob job = jobIndex.remove(jobId);
        if (job != null) {
            job.markCancelled();
            // Don't remove from PQ — too expensive (O(n)). Skip on poll instead.
        }
    }
    
    void executeReady() {
        while (!jobQueue.isEmpty() && jobQueue.peek().getExecutionTime().isBefore(Instant.now())) {
            ScheduledJob job = jobQueue.poll();
            if (!job.isCancelled()) {
                job.execute();
            }
        }
    }
}
```

---

## Interview Talking Points & Scripts

### When Choosing a Data Structure

> *"For the LRU cache, I'll use LinkedHashMap with access-order mode — it automatically moves accessed entries to the end, so the least-recently-used entry is always at the head. I override removeEldestEntry() to auto-evict when the cache exceeds capacity. This gives me O(1) for both get and put with built-in LRU behavior."*

### When Asked About Thread Safety

> *"For the rate limiter, I need ConcurrentHashMap because multiple request threads will access it simultaneously. Each client ID maps to a TokenBucket. computeIfAbsent() is atomic, so I don't need external synchronization for bucket creation. For the notification listener list, I'll use CopyOnWriteArrayList since listeners rarely change but are iterated on every event."*

### When Justifying Over HashMap

> *"I chose TreeMap here instead of HashMap because I need range queries — finding all events between time T1 and T2. TreeMap gives me O(log n) for floor/ceiling queries and subMap, while HashMap would require O(n) scanning. The trade-off is O(log n) inserts instead of O(1), but the query pattern justifies it."*

---

## Common Interview Mistakes

```
❌ Mistake 1: Using ArrayList when you need O(1) contains
   Fix: Use HashSet for contains checks. ArrayList.contains() is O(n).

❌ Mistake 2: Using HashMap for sorted/ordered data
   Fix: TreeMap for sorted by key, LinkedHashMap for insertion order.

❌ Mistake 3: Using Collections.synchronizedMap instead of ConcurrentHashMap
   Fix: ConcurrentHashMap is faster (fine-grained locking) and has 
   atomic compound operations (computeIfAbsent).

❌ Mistake 4: Not pre-sizing ArrayList/HashMap
   Fix: If you know the size, pass it to constructor to avoid resizing.

❌ Mistake 5: Using LinkedList as a general-purpose list
   Fix: ArrayList is faster for almost everything. Use ArrayDeque for stack/queue.

❌ Mistake 6: Using Stack class
   Fix: Stack is legacy and synchronized. Use ArrayDeque instead.

❌ Mistake 7: Forgetting equals/hashCode for HashMap keys
   Fix: Custom objects used as keys MUST implement equals() and hashCode().
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          DATA STRUCTURE SELECTION CHEAT SHEET                  │
├──────────────────┬───────────────────────────────────────────┤
│ Default List     │ ArrayList (random access O(1))             │
│ Default Set      │ HashSet (contains O(1))                    │
│ Default Map      │ HashMap (get/put O(1))                     │
│ Default Queue    │ ArrayDeque (push/pop/offer/poll O(1))      │
├──────────────────┼───────────────────────────────────────────┤
│ Need sorted?     │ TreeSet / TreeMap (O(log n), range queries)│
│ Need order?      │ LinkedHashSet / LinkedHashMap              │
│ Need priority?   │ PriorityQueue (min/max heap O(log n))      │
│ Need thread-safe?│ ConcurrentHashMap / CopyOnWriteArrayList  │
│ Need LRU?        │ LinkedHashMap(access-order=true)           │
│ Need enum keys?  │ EnumSet (bit vector) / EnumMap (array)     │
│ Need blocking?   │ LinkedBlockingQueue / ArrayBlockingQueue   │
├──────────────────┼───────────────────────────────────────────┤
│ AVOID            │ LinkedList (use ArrayDeque), Stack class    │
│                  │ Vector (use ArrayList), Hashtable (use CHM) │
├──────────────────┴───────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "I choose data structures based on the dominant operation:     │
│  O(1) lookup → HashMap, sorted range → TreeMap, thread-safe   │
│  → ConcurrentHashMap, LRU → LinkedHashMap, priority → PQ."   │
└──────────────────────────────────────────────────────────────┘
```
