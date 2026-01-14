import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * INTERVIEW-READY Metrics Collection System
 * Time to complete: 40-50 minutes
 * Focus: Time-series data, aggregation
 */

// ==================== Metric ====================
class Metric {
    private final String name;
    private final double value;
    private final long timestamp;
    private final Map<String, String> tags;

    public Metric(String name, double value, Map<String, String> tags) {
        this.name = name;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
        this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
    }

    public String getName() { return name; }
    public double getValue() { return value; }
    public long getTimestamp() { return timestamp; }
    public Map<String, String> getTags() { return tags; }

    @Override
    public String toString() {
        return name + "=" + value + " " + tags + " @" + timestamp;
    }
}

// ==================== Metric Aggregator Interface ====================
interface MetricAggregator {
    void record(double value);
    double getResult();
    void reset();
}

// ==================== Counter Aggregator ====================
class CounterAggregator implements MetricAggregator {
    private final AtomicLong count = new AtomicLong(0);

    @Override
    public void record(double value) {
        count.addAndGet((long)value);
    }

    @Override
    public double getResult() {
        return count.get();
    }

    @Override
    public void reset() {
        count.set(0);
    }
}

// ==================== Average Aggregator ====================
class AverageAggregator implements MetricAggregator {
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicInteger count = new AtomicInteger(0);

    @Override
    public void record(double value) {
        sum.addAndGet((long)value);
        count.incrementAndGet();
    }

    @Override
    public double getResult() {
        int c = count.get();
        return c == 0 ? 0 : (double)sum.get() / c;
    }

    @Override
    public void reset() {
        sum.set(0);
        count.set(0);
    }
}

// ==================== Min/Max Aggregator ====================
class MinMaxAggregator implements MetricAggregator {
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;

    @Override
    public synchronized void record(double value) {
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public synchronized double getResult() {
        return max;  // Return max for demo
    }

    public synchronized double getMin() {
        return min;
    }

    public synchronized double getMax() {
        return max;
    }

    @Override
    public synchronized void reset() {
        min = Double.MAX_VALUE;
        max = Double.MIN_VALUE;
    }
}

// ==================== Metrics Collector ====================
class MetricsCollector {
    private final Map<String, MetricAggregator> aggregators;
    private final List<Metric> metrics;

    public MetricsCollector() {
        this.aggregators = new ConcurrentHashMap<>();
        this.metrics = new CopyOnWriteArrayList<>();
    }

    public void registerMetric(String name, MetricAggregator aggregator) {
        aggregators.put(name, aggregator);
        System.out.println("✓ Registered metric: " + name);
    }

    public void record(String name, double value, Map<String, String> tags) {
        Metric metric = new Metric(name, value, tags);
        metrics.add(metric);

        MetricAggregator aggregator = aggregators.get(name);
        if (aggregator != null) {
            aggregator.record(value);
        }

        System.out.println("✓ Recorded: " + name + "=" + value);
    }

    public double query(String name) {
        MetricAggregator aggregator = aggregators.get(name);
        if (aggregator == null) {
            System.out.println("✗ Metric not found: " + name);
            return 0;
        }

        double result = aggregator.getResult();
        System.out.println("? Query " + name + " = " + result);
        return result;
    }

    public void displayMetrics() {
        System.out.println("\n=== Metrics Summary ===");
        for (Map.Entry<String, MetricAggregator> entry : aggregators.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().getResult());
        }
        System.out.println("Total metrics recorded: " + metrics.size());
        System.out.println();
    }
}

// ==================== Demo ====================
public class MetricsCollectionSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Metrics Collection Demo ===\n");

        MetricsCollector collector = new MetricsCollector();

        // Register metrics
        collector.registerMetric("api.requests", new CounterAggregator());
        collector.registerMetric("api.latency", new AverageAggregator());
        collector.registerMetric("cpu.usage", new MinMaxAggregator());

        // Record metrics
        System.out.println("\n--- Recording Metrics ---");
        
        Map<String, String> tags = new HashMap<>();
        tags.put("endpoint", "/api/users");
        tags.put("status", "200");

        // API requests
        collector.record("api.requests", 1, tags);
        collector.record("api.requests", 1, tags);
        collector.record("api.requests", 1, tags);

        // API latency
        collector.record("api.latency", 45, tags);
        collector.record("api.latency", 52, tags);
        collector.record("api.latency", 38, tags);

        // CPU usage
        collector.record("cpu.usage", 45.5, null);
        collector.record("cpu.usage", 67.8, null);
        collector.record("cpu.usage", 52.3, null);

        // Query metrics
        System.out.println("\n--- Querying Metrics ---");
        collector.query("api.requests");      // Should show 3
        collector.query("api.latency");       // Should show avg ~45
        collector.query("cpu.usage");         // Should show max 67.8

        collector.displayMetrics();

        System.out.println("✅ Demo complete!");
    }
}
