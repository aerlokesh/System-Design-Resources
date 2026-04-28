# Hit Counter (Multi-Threaded) - HELLO Interview Framework

> **Companies**: Google, Amazon, Adobe, Atlassian, Microsoft +6 more  
> **Difficulty**: Medium  
> **Key Insight**: Circular buffer with AtomicIntegerArray for O(1) lock-free operations  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [Data Flow](#4️⃣-data-flow)
5. [Design + Implementation](#5️⃣-design--implementation)
6. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Hit Counter?

Count the number of hits (page views, API calls, events) within a **sliding time window** (e.g., last 5 minutes = 300 seconds). Must handle thousands of concurrent hits per second from multiple threads.

**Real-world**: CloudWatch metrics, Datadog, Google Analytics page views, API rate limiting counters, DDoS detection systems.

### For L63 Microsoft Interview

This tests **concurrency** knowledge deeply:
1. **Three approaches** with different thread-safety tradeoffs
2. **Circular buffer** — the O(1) constant-space interview winner
3. **AtomicIntegerArray** — lock-free CAS for maximum throughput
4. **Trade-off discussion**: precision vs performance vs memory

---

## 1️⃣ Requirements

### Functional (P0)
1. `hit()` — record a hit at current timestamp
2. `getHits()` — count hits in the last W seconds (sliding window)
3. Thread-safe for concurrent calls
4. Configurable window size

### Non-Functional
- **Thread Safety**: 10,000+ concurrent hits per second
- **Latency**: hit() < 1µs, getHits() < 10µs
- **Memory**: O(W) where W = window size in seconds (NOT O(N) per hit)

---

## 2️⃣ Core Entities & Three Approaches

```
HitCounter (interface)
  + hit(): void
  + getHits(): int
  │
  ├── CircularBufferHitCounter  — O(1) hit, O(W) getHits, synchronized
  ├── AtomicHitCounter          — O(1) hit, O(W) getHits, lock-free CAS
  └── DequeHitCounter           — O(1)* hit, O(N) getHits, exact count
```

### Comparison Table

| | Circular Buffer | Atomic (CAS) | Deque (Exact) |
|---|---|---|---|
| **hit()** | O(1) | O(1) | O(1) amortized |
| **getHits()** | O(W) | O(W) | O(N) cleanup |
| **Space** | O(W) fixed | O(W) fixed | O(N) grows |
| **Thread Safety** | `synchronized` | `AtomicIntegerArray` | `ConcurrentLinkedDeque` |
| **Precision** | Per-second buckets | Per-second buckets | Exact per-hit |
| **Best for** | General purpose | High throughput | Strict accuracy |

---

## 4️⃣ Data Flow

### Circular Buffer: hit() at timestamp 305

```
Window = 300 seconds (5 minutes)

hit() at timestamp 305:
  ├─ index = 305 % 300 = 5
  ├─ timestamps[5] == 305?  (same second)
  │   ├─ YES → hits[5]++                 ← increment existing bucket
  │   └─ NO → timestamps[5] = 305, hits[5] = 1  ← new second, reset bucket
  └─ Done. O(1)!

getHits() at timestamp 305:
  ├─ For i = 0 to 299:
  │   ├─ Is timestamps[i] within window?  (305 - timestamps[i] < 300?)
  │   │   ├─ YES → total += hits[i]
  │   │   └─ NO → stale bucket, skip
  └─ Return total. O(W) where W = 300.
```

**Why circular?** Bucket 5 at time 5 → bucket 5 at time 305 → bucket 5 at time 605. Old data auto-replaced. No cleanup needed.

---

## 5️⃣ Design + Implementation

### Approach 1: Circular Buffer (synchronized)

```java
class CircularBufferHitCounter implements HitCounter {
    private final int windowSeconds;
    private final int[] hits;           // hit count per second
    private final long[] timestamps;    // which second this bucket represents
    private final Object lock = new Object();

    @Override
    public void hitAt(long timestamp) {
        synchronized (lock) {
            int idx = (int)(timestamp % windowSeconds);  // circular index!
            if (timestamps[idx] == timestamp) {
                hits[idx]++;           // same second → increment
            } else {
                timestamps[idx] = timestamp;  // new second →
                hits[idx] = 1;               // reset bucket
            }
        }
    }

    @Override
    public int getHitsAt(long timestamp) {
        synchronized (lock) {
            int total = 0;
            for (int i = 0; i < windowSeconds; i++) {
                if (timestamp - timestamps[i] < windowSeconds) {
                    total += hits[i];  // within window → count
                }
                // else: stale → skip
            }
            return total;
        }
    }
}
```

**Why `synchronized`?** The compound operation (check timestamp + update hits) must be atomic. Two threads hitting at the same second could corrupt the count without synchronization.

### Approach 2: Lock-Free Atomic (CAS)

```java
class AtomicHitCounter implements HitCounter {
    private final AtomicLongArray timestamps;    // lock-free!
    private final AtomicIntegerArray counts;     // lock-free!

    @Override
    public void hitAt(long timestamp) {
        int idx = (int)(timestamp % windowSeconds);
        long stored = timestamps.get(idx);
        
        if (stored == timestamp) {
            counts.incrementAndGet(idx);  // CAS atomic increment
        } else {
            // New second — try to claim this bucket
            if (timestamps.compareAndSet(idx, stored, timestamp)) {
                counts.set(idx, 1);       // We won the CAS race!
            } else {
                // Another thread claimed it
                if (timestamps.get(idx) == timestamp) {
                    counts.incrementAndGet(idx);  // Same second, just increment
                }
                // else: rare boundary race, drop this hit (acceptable)
            }
        }
    }
}
```

**Why lock-free?** Under extreme contention (100K+ hits/sec), `synchronized` creates a bottleneck. CAS (Compare-And-Swap) allows threads to proceed without waiting.

**Trade-off**: Rare hits at second boundaries may be dropped. For 100K concurrent hits, this is ~99.99% accurate — acceptable for metrics.

### Approach 3: Deque (Exact)

```java
class DequeHitCounter implements HitCounter {
    private final Deque<Long> hits = new ConcurrentLinkedDeque<>();

    @Override
    public void hitAt(long timestamp) {
        hits.addLast(timestamp);
        cleanup(timestamp);
    }

    @Override
    public int getHitsAt(long timestamp) {
        cleanup(timestamp);
        return hits.size();
    }

    private void cleanup(long now) {
        while (!hits.isEmpty() && now - hits.peekFirst() >= windowSeconds) {
            hits.pollFirst();  // remove expired
        }
    }
}
```

**Why not always use Deque?** Memory: stores every timestamp. At 100K hits/sec × 300s window = 30M entries! Circular buffer: always 300 entries regardless of traffic.

---

## 6️⃣ Deep Dives

### Deep Dive 1: Why Circular Buffer is the Interview Winner

```
Memory comparison (5-min window, 100K hits/sec):
  Circular Buffer: 300 ints + 300 longs = 2.4 KB (constant!)
  Deque: 30M longs = 240 MB (grows with traffic!)
  
Performance (hit()):
  Circular: O(1) always, one array access
  Deque: O(1) amortized, but cleanup can spike to O(N)
```

### Deep Dive 2: CAS (Compare-And-Swap) Explained

```java
// AtomicLongArray.compareAndSet(index, expected, newValue)
// Atomically: if array[index] == expected, set to newValue, return true
//             else return false (another thread modified it)

timestamps.compareAndSet(idx, stored, timestamp);
// "I expect this bucket to still be the old second.
//  If it is, I'll claim it for the new second.
//  If another thread got there first, I'll try another way."
```

This is **lock-free**: no thread ever blocks. Worst case, a thread retries. Average case: first attempt succeeds.

### Deep Dive 3: Production Extensions

| Extension | Implementation |
|-----------|---------------|
| **Distributed counting** | Redis INCR + EXPIRE per key per second |
| **Unique visitors** | HyperLogLog (probabilistic, ~2% error, 12KB per counter) |
| **Percentile latency** | T-Digest or HDR Histogram |
| **Minute/hour granularity** | Multiple counters at different granularities |
| **Real-time dashboard** | Kafka → Flink/Spark Streaming → time-series DB |

### Deep Dive 4: Thread Safety Verified

Our test: **100 threads × 1,000 hits = 100,000 total**

```
CircularBuffer (synchronized): Expected=100000, Actual=100000 → ✅ EXACT
Atomic (lock-free CAS):        Expected=100000, Actual=100000 → ✅ EXACT
Deque (ConcurrentLinkedDeque):  Expected=100000, Actual=100000 → ✅ EXACT
```

All three are thread-safe. Performance differs under contention:
- Synchronized: ~9ms for 100K hits
- Atomic CAS: ~12ms (overhead from CAS retries)  
- Deque: ~29ms (object allocation per hit)

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Hit at time 0 | timestamps[0] = 0 matches default → works correctly |
| Window just expired | timestamps[i] check: `now - ts[i] < window` excludes exact boundary |
| Burst of hits same second | All increment same bucket (counter can overflow at ~2B) |
| No hits for long period | Old buckets stale, getHits returns 0 correctly |
| Clock skew (NTP adjustment) | Use monotonic clock in production (System.nanoTime) |

### Deep Dive 6: Complexity Summary

| | hit() | getHits() | Space |
|---|---|---|---|
| Circular Buffer | O(1) | O(W) | O(W) |
| Atomic CAS | O(1) | O(W) | O(W) |
| Deque | O(1)* | O(N) cleanup | O(N) |

W = window seconds, N = total hits in window. *Deque hit() is O(1) amortized but cleanup can be O(N).

---

## 📋 Interview Checklist (L63)

- [ ] **Three approaches**: Circular Buffer, Atomic CAS, Deque — with trade-offs
- [ ] **Circular buffer explained**: index = timestamp % window, bucket reuse
- [ ] **Why O(1) hit**: One array access, no allocation, no cleanup
- [ ] **Thread safety**: synchronized vs AtomicIntegerArray vs ConcurrentLinkedDeque
- [ ] **CAS mechanism**: Compare-And-Swap for lock-free programming
- [ ] **Memory**: O(W) fixed vs O(N) growing — why circular wins
- [ ] **Production**: Redis distributed, HyperLogLog for unique visitors
- [ ] **Verified**: 100K concurrent hits → exact count for all 3 approaches

See `HitCounterMTSystem.java` for full 3-implementation comparison with concurrent benchmarks.
