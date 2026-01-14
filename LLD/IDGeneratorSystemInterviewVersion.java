import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * INTERVIEW-READY ID Generator System
 * Time to complete: 35-45 minutes
 * Focus: Unique ID generation strategies (UUID, Snowflake-like)
 */

// ==================== ID Generator Interface ====================
interface IDGenerator {
    String generateId();
    String getName();
}

// ==================== UUID Generator ====================
class UUIDGenerator implements IDGenerator {
    @Override
    public String generateId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getName() {
        return "UUID";
    }
}

// ==================== Sequential ID Generator ====================
class SequentialIDGenerator implements IDGenerator {
    private final AtomicLong counter;
    private final String prefix;

    public SequentialIDGenerator(String prefix, long startValue) {
        this.prefix = prefix;
        this.counter = new AtomicLong(startValue);
    }

    @Override
    public String generateId() {
        long id = counter.getAndIncrement();
        return prefix + id;
    }

    @Override
    public String getName() {
        return "Sequential";
    }
}

// ==================== Snowflake-like ID Generator ====================
class SnowflakeIDGenerator implements IDGenerator {
    private final long workerId;
    private final long datacenterId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    // Bit allocation (simplified)
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    public SnowflakeIDGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID out of range");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("Datacenter ID out of range");
        }

        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    @Override
    public synchronized String generateId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & ((1L << SEQUENCE_BITS) - 1);
            if (sequence == 0) {
                // Wait for next millisecond
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        long id = (timestamp << TIMESTAMP_SHIFT) |
                  (datacenterId << DATACENTER_ID_SHIFT) |
                  (workerId << WORKER_ID_SHIFT) |
                  sequence;

        return String.valueOf(id);
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    @Override
    public String getName() {
        return "Snowflake";
    }
}

// ==================== ID Generator Service ====================
class IDGeneratorService {
    private final Map<String, IDGenerator> generators;

    public IDGeneratorService() {
        this.generators = new HashMap<>();
    }

    public void registerGenerator(String type, IDGenerator generator) {
        generators.put(type, generator);
        System.out.println("✓ Registered generator: " + type);
    }

    public String generateId(String type) {
        IDGenerator generator = generators.get(type);
        if (generator == null) {
            System.out.println("✗ Generator not found: " + type);
            return null;
        }

        String id = generator.generateId();
        System.out.println("Generated [" + type + "]: " + id);
        return id;
    }

    public void generateBatch(String type, int count) {
        System.out.println("\n--- Generating " + count + " IDs (" + type + ") ---");
        for (int i = 0; i < count; i++) {
            generateId(type);
        }
    }
}

// ==================== Demo ====================
public class IDGeneratorSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== ID Generator Demo ===\n");

        IDGeneratorService service = new IDGeneratorService();

        // Register generators
        service.registerGenerator("uuid", new UUIDGenerator());
        service.registerGenerator("user", new SequentialIDGenerator("USER-", 1000));
        service.registerGenerator("order", new SequentialIDGenerator("ORD-", 5000));
        service.registerGenerator("snowflake", new SnowflakeIDGenerator(1, 1));

        // Test 1: UUID generation
        System.out.println("\n--- Test 1: UUID Generator ---");
        service.generateId("uuid");
        service.generateId("uuid");

        // Test 2: Sequential IDs
        System.out.println("\n--- Test 2: Sequential Generator ---");
        service.generateBatch("user", 5);

        // Test 3: Snowflake IDs
        System.out.println("\n--- Test 3: Snowflake Generator ---");
        service.generateBatch("snowflake", 5);

        // Test 4: Different types
        System.out.println("\n--- Test 4: Mixed Generators ---");
        service.generateId("user");
        service.generateId("order");
        service.generateId("uuid");
        service.generateId("snowflake");

        System.out.println("\n✅ Demo complete!");
    }
}
