# 🎯 Topic 18: Java Memory Management & Performance

> **Java Interview — Deep Dive**
> Covering memory optimization, common performance pitfalls, profiling, and interview-ready best practices.

---

## Common Performance Pitfalls

### 1. String Concatenation in Loops

```java
// ❌ O(n²) — Creates n String objects
String result = "";
for (int i = 0; i < 100000; i++) result += i;

// ✅ O(n) — Single StringBuilder
StringBuilder sb = new StringBuilder(100000);
for (int i = 0; i < 100000; i++) sb.append(i);
```

### 2. Autoboxing in Loops

```java
// ❌ Creates millions of Long objects
Long sum = 0L;
for (long i = 0; i < 1_000_000; i++) sum += i;

// ✅ Use primitive
long sum = 0L;
for (long i = 0; i < 1_000_000; i++) sum += i;
```

### 3. Not Pre-Sizing Collections

```java
// ❌ Multiple resizes (10 → 15 → 22 → 33 → ...)
List<String> list = new ArrayList<>();

// ✅ Single allocation
List<String> list = new ArrayList<>(10000);
HashMap<String, Integer> map = new HashMap<>(10000, 0.75f);
```

### 4. Using Wrong Collection

```java
// ❌ LinkedList for random access → O(n)
LinkedList<Integer> list = new LinkedList<>();
list.get(5000);  // Traverses 5000 nodes!

// ✅ ArrayList for random access → O(1)
ArrayList<Integer> list = new ArrayList<>();
list.get(5000);  // Direct array access
```

### 5. Memory Leaks

```java
// ❌ Static collection grows forever
private static List<Object> cache = new ArrayList<>();

// ❌ ThreadLocal without cleanup in thread pools
threadLocal.set(value);
// Must call threadLocal.remove() in finally!

// ❌ Unclosed resources
Connection conn = getConnection();  // Never closed!
// Always use try-with-resources

// ❌ Listeners never unregistered
eventBus.register(listener);
// Must unregister when done
```

---

## Memory-Efficient Coding

### Use Appropriate Types

```java
// Use int[] instead of Integer[] (24 bytes vs 4 bytes per element)
// Use EnumSet instead of HashSet<Enum> (bit-vector, ultra-fast)
// Use EnumMap instead of HashMap<Enum, V> (array-backed, fast)
// Use byte/short when range fits (instead of int)
```

### Lazy Initialization

```java
// ❌ Eager — always creates even if unused
private final ExpensiveObject obj = new ExpensiveObject();

// ✅ Lazy — created only when needed
private ExpensiveObject obj;
public ExpensiveObject getObj() {
    if (obj == null) obj = new ExpensiveObject();
    return obj;
}
```

### Object Pooling (When Appropriate)

```java
// Thread pools (ExecutorService) — reuse threads
// Connection pools (HikariCP) — reuse DB connections
// String.intern() — reuse String objects from pool
// Boolean.valueOf() — returns cached TRUE/FALSE
```

---

## Profiling & Diagnostics

| Tool | Purpose |
|------|---------|
| `jps` | List running Java processes |
| `jstack <pid>` | Thread dump (deadlock detection) |
| `jmap -heap <pid>` | Heap summary |
| `jmap -histo <pid>` | Object histogram |
| `jstat -gc <pid>` | GC statistics |
| `jconsole` | GUI monitoring (memory, threads, CPU) |
| `VisualVM` | Profiling, heap dump analysis |
| `async-profiler` | Low-overhead CPU/allocation profiling |
| `-XX:+HeapDumpOnOutOfMemoryError` | Auto heap dump on OOM |

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│ Performance Pitfalls:                                       │
│ • String concat in loops → StringBuilder                   │
│ • Autoboxing in loops → use primitives                     │
│ • Wrong collection type → ArrayList > LinkedList           │
│ • Not pre-sizing collections → specify initial capacity    │
│ • Memory leaks → close resources, remove ThreadLocal, unreg listeners│
│                                                            │
│ Optimization: lazy init, object pooling, right data types   │
│ Profiling: jstack (threads), jmap (heap), jstat (GC)       │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Testing in Java — JUnit, Mockito →](./19-testing-junit-and-mockito.md)
