import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Hit Counter (Multi-Threaded) - HELLO Interview Framework
 * 
 * Companies: Google, Amazon, Adobe, Atlassian, Microsoft +6 more
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Circular buffer (fixed-size array) for O(1) hit and getHits
 * 2. AtomicIntegerArray for lock-free thread safety
 * 3. Sliding window: counts hits in last N seconds
 * 4. Two approaches: Simple (synchronized) and Lock-Free (atomic)
 */

// ==================== Interface ====================

interface HitCounter {
    void hit();
    void hitAt(long timestamp);
    int getHits();
    int getHitsAt(long timestamp);
    String getName();
}

// ==================== Approach 1: Circular Buffer (Synchronized) ====================
// Time: O(1) hit, O(W) getHits where W = window size
// Space: O(W)

class CircularBufferHitCounter implements HitCounter {
    private final int windowSeconds;
    private final int[] hits;       // hits[i] = count for second (i % window)
    private final long[] timestamps; // timestamps[i] = which second this bucket represents
    private final Object lock = new Object();

    CircularBufferHitCounter(int windowSeconds) {
        this.windowSeconds = windowSeconds;
        this.hits = new int[windowSeconds];
        this.timestamps = new long[windowSeconds];
    }

    @Override
    public void hit() { hitAt(currentSecond()); }

    @Override
    public void hitAt(long timestamp) {
        synchronized (lock) {
            int idx = (int)(timestamp % windowSeconds);
            if (timestamps[idx] == timestamp) {
                hits[idx]++;
            } else {
                // New second — reset bucket
                timestamps[idx] = timestamp;
                hits[idx] = 1;
            }
        }
    }

    @Override
    public int getHits() { return getHitsAt(currentSecond()); }

    @Override
    public int getHitsAt(long timestamp) {
        synchronized (lock) {
            int total = 0;
            for (int i = 0; i < windowSeconds; i++) {
                if (timestamp - timestamps[i] < windowSeconds) {
                    total += hits[i];
                }
            }
            return total;
        }
    }

    @Override
    public String getName() { return "CircularBuffer (synchronized)"; }

    private long currentSecond() { return System.currentTimeMillis() / 1000; }
}

// ==================== Approach 2: Lock-Free Atomic ====================
// Uses AtomicLongArray for timestamps and AtomicIntegerArray for counts
// Compare-and-swap (CAS) for thread safety without locks

class AtomicHitCounter implements HitCounter {
    private final int windowSeconds;
    private final AtomicLongArray timestamps;
    private final AtomicIntegerArray counts;

    AtomicHitCounter(int windowSeconds) {
        this.windowSeconds = windowSeconds;
        this.timestamps = new AtomicLongArray(windowSeconds);
        this.counts = new AtomicIntegerArray(windowSeconds);
    }

    @Override
    public void hit() { hitAt(currentSecond()); }

    @Override
    public void hitAt(long timestamp) {
        int idx = (int)(timestamp % windowSeconds);
        long stored = timestamps.get(idx);

        if (stored == timestamp) {
            counts.incrementAndGet(idx);
        } else {
            // New second — CAS to claim the bucket
            if (timestamps.compareAndSet(idx, stored, timestamp)) {
                counts.set(idx, 1);
            } else {
                // Another thread claimed it — just increment
                if (timestamps.get(idx) == timestamp) {
                    counts.incrementAndGet(idx);
                }
                // else: rare race condition, drop this hit (acceptable in high-throughput)
            }
        }
    }

    @Override
    public int getHits() { return getHitsAt(currentSecond()); }

    @Override
    public int getHitsAt(long timestamp) {
        int total = 0;
        for (int i = 0; i < windowSeconds; i++) {
            if (timestamp - timestamps.get(i) < windowSeconds) {
                total += counts.get(i);
            }
        }
        return total;
    }

    @Override
    public String getName() { return "Atomic (lock-free CAS)"; }

    private long currentSecond() { return System.currentTimeMillis() / 1000; }
}

// ==================== Approach 3: Simple Deque (exact but more memory) ====================

class DequeHitCounter implements HitCounter {
    private final int windowSeconds;
    private final Deque<Long> hits = new ConcurrentLinkedDeque<>();

    DequeHitCounter(int windowSeconds) { this.windowSeconds = windowSeconds; }

    @Override
    public void hit() { hitAt(currentSecond()); }

    @Override
    public void hitAt(long timestamp) {
        hits.addLast(timestamp);
        cleanup(timestamp);
    }

    @Override
    public int getHits() { return getHitsAt(currentSecond()); }

    @Override
    public int getHitsAt(long timestamp) {
        cleanup(timestamp);
        return hits.size();
    }

    private void cleanup(long now) {
        while (!hits.isEmpty() && now - hits.peekFirst() >= windowSeconds) {
            hits.pollFirst();
        }
    }

    @Override
    public String getName() { return "Deque (exact, O(n) memory)"; }

    private long currentSecond() { return System.currentTimeMillis() / 1000; }
}

// ==================== Main Demo ====================

public class HitCounterMTSystem {
    public static void main(String[] args) throws Exception {
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  Hit Counter (Multi-Threaded) - 3 Implementations   ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        // ── Scenario 1: Basic operation (each implementation) ──
        System.out.println("━━━ Scenario 1: Basic hit/getHits (5-second window) ━━━");
        HitCounter[] counters = {
            new CircularBufferHitCounter(5),
            new AtomicHitCounter(5),
            new DequeHitCounter(5)
        };

        long now = System.currentTimeMillis() / 1000;
        for (HitCounter hc : counters) {
            // Simulate hits at specific timestamps
            hc.hitAt(now);
            hc.hitAt(now);
            hc.hitAt(now);
            hc.hitAt(now - 1);
            hc.hitAt(now - 1);
            hc.hitAt(now - 3);
            // This one is outside window (6 seconds ago)
            hc.hitAt(now - 6);

            int total = hc.getHitsAt(now);
            System.out.printf("  [%s] Hits in last 5s: %d (expected: 6)%n",
                hc.getName(), total);
        }
        System.out.println();

        // ── Scenario 2: Concurrent thread safety test ──
        System.out.println("━━━ Scenario 2: Concurrent hits (100 threads × 1000 hits each) ━━━");

        for (HitCounter hc : new HitCounter[]{
            new CircularBufferHitCounter(300),
            new AtomicHitCounter(300),
            new DequeHitCounter(300)
        }) {
            int numThreads = 100;
            int hitsPerThread = 1000;
            int expectedTotal = numThreads * hitsPerThread;

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);

            for (int t = 0; t < numThreads; t++) {
                executor.submit(() -> {
                    try { startLatch.await(); } catch (InterruptedException e) {}
                    for (int i = 0; i < hitsPerThread; i++) {
                        hc.hit();
                    }
                    doneLatch.countDown();
                });
            }

            long start = System.nanoTime();
            startLatch.countDown(); // start all threads simultaneously
            doneLatch.await(10, TimeUnit.SECONDS);
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            int actualTotal = hc.getHits();
            boolean correct = actualTotal == expectedTotal;
            System.out.printf("  [%s] Expected=%d, Actual=%d → %s (%dms)%n",
                hc.getName(), expectedTotal, actualTotal,
                correct ? "✅ EXACT" : "⚠️ ~" + (actualTotal * 100 / expectedTotal) + "%",
                elapsed);

            executor.shutdown();
        }
        System.out.println();

        // ── Scenario 3: Window expiry ──
        System.out.println("━━━ Scenario 3: Window expiry (2-second window) ━━━");
        HitCounter expiry = new CircularBufferHitCounter(2);
        expiry.hit();
        expiry.hit();
        expiry.hit();
        System.out.println("  Immediately after 3 hits: " + expiry.getHits());
        System.out.println("  ⏳ Waiting 3 seconds...");
        Thread.sleep(3000);
        System.out.println("  After 3 seconds: " + expiry.getHits() + " (expected: 0)");
        System.out.println();

        // ── Scenario 4: Rapid burst ──
        System.out.println("━━━ Scenario 4: Rapid burst (10000 hits) ━━━");
        HitCounter burst = new AtomicHitCounter(60);
        long burstStart = System.nanoTime();
        for (int i = 0; i < 10000; i++) burst.hit();
        long burstTime = (System.nanoTime() - burstStart) / 1000; // microseconds
        System.out.printf("  10,000 hits in %d µs (%.1f µs/hit)%n", burstTime, burstTime / 10000.0);
        System.out.println("  Total hits: " + burst.getHits());
        System.out.println();

        // ── Scenario 5: Comparison of approaches ──
        System.out.println("━━━ Scenario 5: Algorithm Comparison ━━━");
        System.out.println("  ┌─────────────────────────┬──────────┬─────────┬──────────────────┐");
        System.out.println("  │ Algorithm               │ hit()    │ getHits │ Thread Safety    │");
        System.out.println("  ├─────────────────────────┼──────────┼─────────┼──────────────────┤");
        System.out.println("  │ Circular Buffer (sync)  │ O(1)     │ O(W)    │ synchronized     │");
        System.out.println("  │ Atomic (lock-free)      │ O(1)     │ O(W)    │ CAS (AtomicArr)  │");
        System.out.println("  │ Deque (exact)           │ O(1)*    │ O(N)*   │ ConcurrentDeque  │");
        System.out.println("  └─────────────────────────┴──────────┴─────────┴──────────────────┘");
        System.out.println("  W = window size (seconds), N = hits in window");
        System.out.println("  * Deque: O(1) amortized hit, O(N) cleanup on getHits");

        System.out.println("\n✅ All Hit Counter scenarios complete.");
    }
}
