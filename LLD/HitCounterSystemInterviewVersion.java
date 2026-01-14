import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Hit Counter System
 * Time to complete: 30-40 minutes
 * Focus: Sliding window with timestamp tracking
 */

// ==================== Hit Counter ====================
class HitCounter {
    private final int windowSeconds;
    private final Queue<Long> timestamps;

    public HitCounter(int windowSeconds) {
        this.windowSeconds = windowSeconds;
        this.timestamps = new LinkedList<>();
    }

    public synchronized void hit() {
        long now = System.currentTimeMillis() / 1000;  // Convert to seconds
        timestamps.offer(now);
        System.out.println("HIT recorded at " + now);
    }

    public synchronized int getHits() {
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - windowSeconds;

        // Remove expired timestamps
        while (!timestamps.isEmpty() && timestamps.peek() <= windowStart) {
            timestamps.poll();
        }

        int count = timestamps.size();
        System.out.println("Hits in last " + windowSeconds + "s: " + count);
        return count;
    }

    public void displayState() {
        System.out.println("Current hits stored: " + timestamps.size());
    }
}

// ==================== Optimized Hit Counter (Using Buckets) ====================
class BucketedHitCounter {
    private final int windowSeconds;
    private final int buckets;
    private final int[] counts;
    private final long[] timestamps;

    public BucketedHitCounter(int windowSeconds, int buckets) {
        this.windowSeconds = windowSeconds;
        this.buckets = buckets;
        this.counts = new int[buckets];
        this.timestamps = new long[buckets];
    }

    public synchronized void hit() {
        long now = System.currentTimeMillis() / 1000;
        int index = (int)(now % buckets);

        // If bucket is stale, reset it
        if (timestamps[index] != now) {
            timestamps[index] = now;
            counts[index] = 0;
        }

        counts[index]++;
        System.out.println("HIT recorded in bucket " + index);
    }

    public synchronized int getHits() {
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - windowSeconds;
        int total = 0;

        for (int i = 0; i < buckets; i++) {
            if (timestamps[i] > windowStart) {
                total += counts[i];
            }
        }

        System.out.println("Hits in last " + windowSeconds + "s: " + total);
        return total;
    }
}

// ==================== Demo ====================
public class HitCounterSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Hit Counter Demo ===\n");

        // Test 1: Simple timestamp-based counter
        System.out.println("--- Test 1: Timestamp-Based (300s window) ---");
        HitCounter counter1 = new HitCounter(300);

        for (int i = 1; i <= 5; i++) {
            counter1.hit();
            Thread.sleep(100);
        }

        counter1.getHits();
        counter1.displayState();

        // Test 2: Bucketed counter (more memory efficient)
        System.out.println("\n--- Test 2: Bucketed Counter (60s window, 60 buckets) ---");
        BucketedHitCounter counter2 = new BucketedHitCounter(60, 60);

        for (int i = 1; i <= 10; i++) {
            counter2.hit();
            Thread.sleep(50);
        }

        counter2.getHits();

        // Simulate time passing
        System.out.println("\n[Simulating 2 seconds...]");
        Thread.sleep(2000);

        counter2.hit();
        counter2.hit();
        counter2.getHits();

        System.out.println("\n✅ Demo complete!");
    }
}
