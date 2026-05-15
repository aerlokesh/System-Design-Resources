# 🎯 Topic 20: Java Interview Quick Reference & Common Traps

> **Java Interview — Master Cheat Sheet**
> The ultimate rapid-fire reference covering the most-asked Java interview questions, traps, and one-liner answers.

---

## Top 50 Interview One-Liners

### OOP & Core Java

| # | Question | Answer |
|---|----------|--------|
| 1 | Java is pass-by-? | **Pass-by-value** always. Object references are passed by value (copy of reference). |
| 2 | Can you override static methods? | No — they are **hidden**, not overridden. Resolved by reference type at compile-time. |
| 3 | Can you override private methods? | No — private methods are not inherited. |
| 4 | Can constructor be private? | Yes — used in Singleton, Factory, utility classes. |
| 5 | Can abstract class have constructor? | Yes — called via `super()` from subclass. |
| 6 | Can interface have constructor? | No. |
| 7 | Multiple inheritance in Java? | Not for classes (diamond problem). Yes for interfaces. |
| 8 | `abstract` + `final` together? | No — `final` prevents override, `abstract` requires it. |
| 9 | `abstract` + `static` together? | No — static belongs to class, not overridden. |
| 10 | `abstract` + `private` together? | No — private can't be inherited to be overridden. |

### Strings

| # | Question | Answer |
|---|----------|--------|
| 11 | Why is String immutable? | Thread-safety, String Pool, hashCode caching, security. |
| 12 | `"abc" == new String("abc")`? | **false** — pool vs heap. |
| 13 | `"abc" == "ab" + "c"`? | **true** — compile-time constant folding. |
| 14 | How many objects: `new String("hello")`? | Up to **2** — one in pool + one in heap. |
| 15 | String Pool location? | **Heap** (moved from PermGen in Java 7). |

### Collections

| # | Question | Answer |
|---|----------|--------|
| 16 | HashMap default capacity? | **16**, load factor **0.75**, treeify at **8**. |
| 17 | HashMap thread-safe? | No — use `ConcurrentHashMap`. |
| 18 | Hashtable vs ConcurrentHashMap? | Hashtable locks entire map; CHM locks per-bucket. |
| 19 | Map extends Collection? | **No** — separate hierarchy. |
| 20 | ArrayList default capacity? | **10**, grows **1.5x**. |
| 21 | Fail-fast vs fail-safe? | Fail-fast (ArrayList): CME on modification. Fail-safe (CHM): works on snapshot. |
| 22 | TreeMap vs HashMap? | TreeMap: sorted O(log n). HashMap: unsorted O(1). |
| 23 | Override equals but not hashCode? | HashMap/HashSet **break** — equal objects in different buckets. |

### Concurrency

| # | Question | Answer |
|---|----------|--------|
| 24 | `start()` vs `run()`? | `start()` = new thread. `run()` = same thread. |
| 25 | `sleep()` vs `wait()`? | `sleep()` keeps lock. `wait()` releases lock. |
| 26 | volatile = atomic? | **No** — volatile = visibility only. `count++` still not safe. |
| 27 | Deadlock conditions? | Mutual exclusion + hold-and-wait + no preemption + circular wait. |
| 28 | synchronized vs ReentrantLock? | Lock: tryLock, timeout, fair, interruptible. Sync: simpler. |
| 29 | Thread pool best practice? | `ThreadPoolExecutor` with bounded queue + rejection policy. |
| 30 | `notify()` vs `notifyAll()`? | `notify()` = 1 random thread. `notifyAll()` = all waiting threads (preferred). |

### JVM & Memory

| # | Question | Answer |
|---|----------|--------|
| 31 | Stack vs Heap? | Stack: primitives, refs, frames (per thread). Heap: objects (shared). |
| 32 | PermGen vs Metaspace? | Metaspace replaced PermGen in Java 8 — uses native memory, auto-grows. |
| 33 | GC algorithm? | **Reachability analysis** from GC roots (NOT reference counting). |
| 34 | Default GC since Java 9? | **G1 GC**. |
| 35 | `finalize()` usage? | **Don't** — deprecated. Use try-with-resources. |
| 36 | `System.gc()` guarantees collection? | **No** — it's only a hint. |

### Java 8+

| # | Question | Answer |
|---|----------|--------|
| 37 | Functional interface? | Exactly **one abstract method** (can have default/static). |
| 38 | Stream is reusable? | **No** — single-use. Terminal op exhausts it. |
| 39 | `map()` vs `flatMap()`? | `map()`: 1-to-1 transform. `flatMap()`: 1-to-many (flattens). |
| 40 | Optional for fields? | **No** — only for return types. Use null or default for fields. |
| 41 | `var` = dynamic typing? | **No** — compile-time inference, still strongly typed. |
| 42 | Records are mutable? | **No** — immutable data carriers with auto equals/hashCode/toString. |

### Exception Handling

| # | Question | Answer |
|---|----------|--------|
| 43 | Checked vs Unchecked? | Checked: must handle (IOException). Unchecked: RuntimeException subclasses. |
| 44 | finally always executes? | Not with `System.exit()`, JVM crash, or thread kill. |
| 45 | Return in finally? | **Never** — swallows exceptions silently. |
| 46 | try-with-resources close order? | **Reverse** declaration order. |

### Miscellaneous

| # | Question | Answer |
|---|----------|--------|
| 47 | Integer cache range? | **-128 to 127**. `Integer a=127; Integer b=127; a==b → true`. |
| 48 | Enum constructor visibility? | Always **private** (implicit). |
| 49 | `==` for enums? | **Yes** — safe (singletons). |
| 50 | Shallow vs deep copy? | Shallow: shared refs. Deep: independent copy. Prefer copy constructors. |

---

## The 10 Most Common Traps

### 1. String `==` vs `equals()`
```java
"hello" == new String("hello")  // false — ALWAYS use .equals()
```

### 2. Integer Cache Boundary
```java
Integer a = 127, b = 127;  a == b  // true (cached)
Integer c = 128, d = 128;  c == d  // false (new objects!)
```

### 3. ConcurrentModificationException
```java
for (String s : list) { list.remove(s); }  // 💥 CME — use Iterator.remove()
```

### 4. Null Unboxing
```java
Integer x = null; int y = x;  // 💥 NullPointerException
```

### 5. finally Return Swallows Exceptions
```java
try { throw new Exception(); } finally { return 42; }  // Exception LOST!
```

### 6. Mutable HashMap Key
```java
// Changing key fields after put → object lost forever (different bucket)
```

### 7. Static Method "Override"
```java
Parent p = new Child(); p.staticMethod();  // Calls PARENT (hidden, not overridden)
```

### 8. sleep() vs wait() Lock Behavior
```java
// sleep() KEEPS the lock — other threads still blocked
// wait() RELEASES the lock — other threads can proceed
```

### 9. volatile ≠ Atomic
```java
volatile int count; count++;  // NOT thread-safe (read-modify-write = 3 ops)
```

### 10. Generics Type Erasure
```java
List<String> and List<Integer> are same type at runtime — both just List
```

---

## Interview Answer Templates

### "Tell me about yourself (Java context)"
> *"I'm a Java developer with X years of experience. I've worked extensively with core Java, concurrency, and collections. I've built production services using Spring Boot with microservices architecture. I'm comfortable with JVM internals — memory management, garbage collection, and performance tuning. I follow clean code practices including SOLID principles, design patterns, and comprehensive testing with JUnit and Mockito."*

### "What's your strongest Java skill?"
> *"I'd say my strength is understanding Java at the internals level — how HashMap works bucket-by-bucket, how the JVM manages memory with generational GC, how synchronized differs from ReentrantLock at the implementation level. This deep understanding helps me write performant, thread-safe code and quickly diagnose production issues."*

---

## Topic Index

| # | Topic | Key Interview Areas |
|---|-------|-------------------|
| 01 | [OOP Concepts & Principles](./01-oop-concepts-and-principles.md) | 4 pillars, SOLID, composition vs inheritance |
| 02 | [Collections Framework](./02-collections-framework-deep-dive.md) | HashMap internals, equals/hashCode, ArrayList vs LinkedList |
| 03 | [Strings & Immutability](./03-strings-immutability-and-string-pool.md) | String Pool, == vs equals, StringBuilder |
| 04 | [Exception Handling](./04-exception-handling-deep-dive.md) | Checked vs unchecked, try-with-resources, finally traps |
| 05 | [Generics & Type Erasure](./05-generics-and-type-erasure.md) | PECS, wildcards, erasure limitations |
| 06 | [Multithreading & Concurrency](./06-multithreading-and-concurrency.md) | synchronized, locks, thread pools, CompletableFuture |
| 07 | [JVM Memory & GC](./07-jvm-memory-model-and-garbage-collection.md) | Heap structure, GC algorithms, G1/ZGC |
| 08 | [Java 8+ Features](./08-java-8-plus-features.md) | Streams, lambdas, Optional, Records, Virtual Threads |
| 09 | [Design Patterns](./09-design-patterns-in-java.md) | Singleton, Builder, Strategy, Observer, Decorator |
| 10 | [Serialization & Cloning](./10-serialization-and-cloning.md) | transient, deep vs shallow, immutability rules |
| 11 | [Java I/O & NIO](./11-java-io-and-nio.md) | Streams, channels, buffers, selectors |
| 12 | [Enums, Annotations & Reflection](./12-enums-annotations-and-reflection.md) | Enum internals, custom annotations, reflection API |
| 13 | [Autoboxing & Wrappers](./13-autoboxing-wrapper-classes-and-primitives.md) | Integer cache, unboxing NPE, performance traps |
| 14 | [Inner Classes](./14-inner-classes-and-anonymous-classes.md) | Static vs non-static, memory leak risk |
| 15 | [Class Loading](./15-class-loading-and-initialization.md) | ClassLoader hierarchy, static init order |
| 16 | [Keywords Deep Dive](./16-final-static-and-keywords.md) | final, static, volatile, transient |
| 17 | [Equals & HashCode](./17-equals-hashcode-and-object-contract.md) | Contract rules, implementation recipe |
| 18 | [Performance](./18-java-memory-management-and-performance.md) | Common pitfalls, profiling tools |
| 19 | [Testing](./19-testing-junit-and-mockito.md) | JUnit 5, Mockito, best practices |
| 20 | [Quick Reference](./20-java-interview-quick-reference.md) | Top 50 questions, common traps |

---

> **Good luck with your Java interviews! 🚀**
