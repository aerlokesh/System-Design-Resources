# 🎯 Topic 7: JVM Memory Model & Garbage Collection

> **Java Interview — Deep Dive**
> A comprehensive guide covering JVM architecture, memory areas, garbage collection algorithms, GC tuning, and how to articulate these in a Java interview.

---

## Table of Contents

1. [JVM Architecture Overview](#jvm-architecture-overview)
2. [Runtime Memory Areas](#runtime-memory-areas)
3. [Heap Structure — Young & Old Generation](#heap-structure--young--old-generation)
4. [Stack vs Heap](#stack-vs-heap)
5. [Garbage Collection Fundamentals](#garbage-collection-fundamentals)
6. [GC Algorithms](#gc-algorithms)
7. [Garbage Collectors — Serial, Parallel, G1, ZGC](#garbage-collectors--serial-parallel-g1-zgc)
8. [Memory Leaks in Java](#memory-leaks-in-java)
9. [JVM Tuning & Flags](#jvm-tuning--flags)
10. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
11. [Common Interview Mistakes](#common-interview-mistakes)
12. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## JVM Architecture Overview

```
┌─────────────────── JVM ───────────────────────┐
│                                                │
│  .java → javac → .class (bytecode)             │
│                    │                           │
│            Class Loader Subsystem               │
│            (Loading → Linking → Init)           │
│                    │                           │
│         ┌─── Runtime Data Areas ────┐          │
│         │ Method Area | Heap | Stack │          │
│         │ PC Register | Native Stack │         │
│         └──────────────────────────┘          │
│                    │                           │
│          Execution Engine                      │
│          (Interpreter + JIT Compiler + GC)      │
│                                                │
└────────────────────────────────────────────────┘
```

---

## Runtime Memory Areas

### Method Area (Metaspace since Java 8)

- Stores **class metadata**, static variables, constant pool, method bytecode
- **PermGen** (Java ≤ 7) → **Metaspace** (Java 8+) — uses native memory, auto-grows
- Shared across all threads

### Heap

- **All objects** and arrays live here
- Shared across all threads
- Managed by Garbage Collector
- `-Xms` (initial) and `-Xmx` (maximum) control size

### Stack (per thread)

- Each thread gets its own stack
- Stores **frames**: local variables, operand stack, method references
- **LIFO** — frame pushed on method call, popped on return
- `-Xss` controls stack size (default ~512KB–1MB)
- `StackOverflowError` when too deep (recursion)

### PC Register (per thread)

- Points to current instruction being executed
- One per thread

### Native Method Stack (per thread)

- For native (C/C++) method calls via JNI

---

## Heap Structure — Young & Old Generation

```
┌──────────────────── HEAP ────────────────────────┐
│                                                    │
│  ┌─── Young Generation (Minor GC) ────────────┐  │
│  │                                              │  │
│  │  ┌─────┐  ┌──────┐  ┌──────┐               │  │
│  │  │ Eden │  │  S0  │  │  S1  │               │  │
│  │  │      │  │(From)│  │ (To) │               │  │
│  │  └─────┘  └──────┘  └──────┘               │  │
│  │  (New objects)  (Survivors)                   │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ┌─── Old Generation (Major GC / Full GC) ─────┐  │
│  │                                              │  │
│  │  Long-lived objects (survived many GCs)       │  │
│  │                                              │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ┌─── Metaspace (native memory) ────────────────┐  │
│  │  Class metadata, static vars, constant pool   │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘
```

### Object Lifecycle

1. **New object** → allocated in **Eden**
2. Eden fills → **Minor GC** runs
3. Surviving objects → move to **Survivor (S0/S1)** with age counter
4. Each Minor GC → survivors copied between S0 ↔ S1, age incremented
5. Age reaches threshold (default 15) → **promoted to Old Generation**
6. Old Gen fills → **Major GC / Full GC** (expensive, stop-the-world)

### Default Ratios

| Area | Default Ratio |
|------|--------------|
| Young : Old | 1 : 2 |
| Eden : S0 : S1 | 8 : 1 : 1 |

---

## Stack vs Heap

| Feature | Stack | Heap |
|---------|-------|------|
| Stores | Primitives, references, frames | Objects, arrays |
| Access | Fast (LIFO) | Slower (random) |
| Thread | Private (per thread) | Shared (all threads) |
| Size | Small (~512KB–1MB) | Large (configurable) |
| Error | `StackOverflowError` | `OutOfMemoryError` |
| Management | Automatic (method call/return) | Garbage Collector |
| Allocation | Compile-time | Runtime |

### What Goes Where?

```java
public void example() {
    int x = 10;                   // x → Stack (primitive)
    String name = "hello";        // name reference → Stack, "hello" → String Pool (Heap)
    User user = new User();       // user reference → Stack, User object → Heap
    int[] arr = new int[5];       // arr reference → Stack, array → Heap
}
// When method returns: all Stack variables popped
// Heap objects: eligible for GC when no references remain
```

---

## Garbage Collection Fundamentals

### What Is GC?

Automatic memory management that identifies and reclaims objects no longer referenced.

### How Does GC Know What to Collect?

**Reachability Analysis** (NOT reference counting):
1. Start from **GC Roots** (stack variables, static fields, active threads)
2. Traverse all reachable references (mark phase)
3. Everything unreachable is garbage (sweep phase)

### GC Roots

- Local variables on the stack
- Active threads
- Static fields
- JNI references
- Classes loaded by system class loader

### Types of GC Events

| Type | What | Scope | Impact |
|------|------|-------|--------|
| **Minor GC** | Young Gen full | Eden + Survivors | Fast (~ms), short pause |
| **Major GC** | Old Gen full | Old Generation | Slow (~100ms+), long pause |
| **Full GC** | Entire heap | Young + Old + Metaspace | Slowest, longest pause |

### Reference Types

```java
// Strong Reference — default, never GC'd while reachable
User user = new User();

// Soft Reference — GC'd only when memory is low (good for caches)
SoftReference<User> soft = new SoftReference<>(new User());

// Weak Reference — GC'd at next GC cycle
WeakReference<User> weak = new WeakReference<>(new User());

// Phantom Reference — for cleanup actions before finalization
PhantomReference<User> phantom = new PhantomReference<>(new User(), queue);
```

| Ref Type | GC'd When | Use Case |
|----------|----------|----------|
| Strong | Never (while reachable) | Default |
| Soft | Memory pressure | Caches |
| Weak | Next GC | WeakHashMap, listeners |
| Phantom | Already finalized | Cleanup, native resources |

---

## GC Algorithms

### Mark-Sweep

1. **Mark**: Traverse from GC roots, mark live objects
2. **Sweep**: Free unmarked objects
3. **Problem**: Memory fragmentation

### Mark-Sweep-Compact

1. Mark + Sweep (same as above)
2. **Compact**: Move surviving objects together, eliminating fragmentation
3. **Problem**: Compaction is expensive (moving objects)

### Mark-Copy (Used in Young Gen)

1. Divide memory into two halves
2. Allocate in one half
3. On GC: copy live objects to other half, clear first half
4. **Why Young Gen**: Most objects die young → few to copy → very fast

---

## Garbage Collectors — Serial, Parallel, G1, ZGC

### Serial GC

```bash
-XX:+UseSerialGC
```
- Single-threaded GC
- Stop-the-world for both minor and major GC
- **Use case**: Small apps, single CPU

### Parallel GC (Throughput Collector)

```bash
-XX:+UseParallelGC
```
- Multi-threaded GC (young + old gen)
- Maximizes throughput (% time not in GC)
- **Use case**: Batch processing, throughput-sensitive apps

### G1 GC (Garbage First) — Default since Java 9

```bash
-XX:+UseG1GC
```
- Divides heap into **regions** (~1-32MB each)
- No fixed young/old boundaries — regions assigned dynamically
- Collects regions with most garbage first (hence "Garbage First")
- **Predictable pause times** with `-XX:MaxGCPauseMillis=200`
- **Use case**: Large heaps (4GB+), latency-sensitive apps

### ZGC (Java 11+)

```bash
-XX:+UseZGC
```
- **Sub-millisecond pauses** (< 1ms) regardless of heap size
- Concurrent — almost no stop-the-world
- Handles terabyte-sized heaps
- **Use case**: Ultra-low latency apps

### Shenandoah GC (Java 12+)

```bash
-XX:+UseShenandoahGC
```
- Similar to ZGC — concurrent, low-pause
- **Use case**: Low-latency applications

### Comparison

| Collector | Throughput | Latency | Heap Size | Default |
|-----------|-----------|---------|-----------|---------|
| Serial | Low | High pause | Small | Java 1-4 |
| Parallel | **High** | Medium pause | Medium | Java 5-8 |
| G1 | Medium-High | **Low pause** | Large | **Java 9+** |
| ZGC | Medium | **Sub-ms pause** | **Terabytes** | Java 15+ option |

---

## Memory Leaks in Java

### Common Causes

```java
// 1. Static collections that grow forever
private static final List<Object> cache = new ArrayList<>();
public void process(Object obj) {
    cache.add(obj);  // Never removed!
}

// 2. Unclosed resources
Connection conn = dataSource.getConnection();
// Forgot conn.close() → connection leak

// 3. Listeners not unregistered
button.addActionListener(listener);
// Never removed — listener (and its references) stay alive

// 4. Inner classes holding outer reference
class Outer {
    byte[] data = new byte[10_000_000];
    
    class Inner {  // Implicitly holds reference to Outer
        void doSomething() { }
    }
}
// Even if Outer has no other references, Inner keeps it alive

// 5. ThreadLocal not cleaned
ThreadLocal<byte[]> threadLocal = new ThreadLocal<>();
threadLocal.set(new byte[10_000_000]);
// In thread pools: thread reuse means value persists → leak!
// Always: threadLocal.remove() in finally block
```

---

## JVM Tuning & Flags

### Essential JVM Flags

```bash
# Heap size
-Xms512m          # Initial heap size
-Xmx4g            # Maximum heap size

# GC selection
-XX:+UseG1GC      # Use G1 collector
-XX:+UseZGC       # Use ZGC

# GC tuning
-XX:MaxGCPauseMillis=200    # Target max pause (G1)
-XX:NewRatio=2              # Old:Young ratio
-XX:SurvivorRatio=8         # Eden:Survivor ratio

# Metaspace
-XX:MetaspaceSize=256m      # Initial metaspace
-XX:MaxMetaspaceSize=512m   # Max metaspace

# Stack
-Xss512k          # Thread stack size

# GC logging
-Xlog:gc*         # GC logging (Java 9+)

# Heap dump on OOM
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
```

---

## Interview Talking Points & Scripts

### "Explain JVM memory areas"

> *"JVM memory has five main areas. The Heap is shared across all threads and stores all objects — it's divided into Young Generation (Eden + Survivors) and Old Generation. The Stack is per-thread and stores method frames with local variables and references. The Method Area (Metaspace since Java 8) stores class metadata and static variables. Each thread also has a PC Register pointing to the current instruction and a Native Method Stack for JNI calls. The GC manages the heap automatically."*

### "How does garbage collection work?"

> *"GC uses reachability analysis starting from GC roots — stack variables, static fields, active threads. Any object reachable from these roots is alive; everything else is garbage. The heap is generational: new objects go to Eden, survive Minor GCs into Survivor spaces with an age counter, and eventually promote to Old Gen. Minor GCs are fast because most young objects die quickly (generational hypothesis). G1, the default since Java 9, divides the heap into regions and prioritizes collecting the most garbage-filled regions first, achieving predictable pause times."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong |
|---------|---------------|
| "Java has no memory leaks" | Objects referenced from static/unclosed/threadlocal can leak |
| "finalize() cleans up resources" | Deprecated, unreliable — use try-with-resources |
| "System.gc() forces collection" | It's only a **hint** — JVM may ignore it |
| "PermGen exists in modern Java" | Replaced by Metaspace in Java 8 |
| "GC uses reference counting" | Java uses **reachability analysis** from GC roots |
| "Full GC is always stop-the-world" | ZGC/Shenandoah are mostly concurrent |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│           JVM & GC CHEAT SHEET                              │
├────────────────────────────────────────────────────────────┤
│ Memory: Heap (objects) | Stack (frames) | Metaspace (classes)│
│                                                            │
│ Heap: Eden → S0/S1 (age++) → Old Gen                       │
│ Minor GC: Young Gen | Major GC: Old Gen | Full GC: All     │
│                                                            │
│ GC Algorithm: Reachability from GC Roots (NOT ref counting) │
│ Generational Hypothesis: most objects die young             │
│                                                            │
│ Collectors:                                                 │
│ • Serial → single-thread, small apps                       │
│ • Parallel → multi-thread, throughput                      │
│ • G1 → regions, predictable pauses (DEFAULT Java 9+)       │
│ • ZGC → sub-ms pauses, terabyte heaps                      │
│                                                            │
│ References: Strong > Soft > Weak > Phantom                  │
│                                                            │
│ Leak Sources: static collections, unclosed resources,       │
│   listeners, inner classes, ThreadLocal without remove()    │
│                                                            │
│ Key Flags: -Xms, -Xmx, -Xss, -XX:+UseG1GC                │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Java 8+ Features — Streams, Lambdas, Optional →](./08-java-8-plus-features.md)
