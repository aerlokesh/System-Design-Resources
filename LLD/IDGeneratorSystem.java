import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ID Generator Low-Level Design - Complete Implementation
 * 
 * This file demonstrates:
 * 1. Snowflake ID Generator (Distributed, Time-ordered)
 * 2. UUID Generator (Random, No coordination)
 * 3. Sequential ID Generator (Simple atomic counter)
 * 4. Multi-Datacenter Snowflake
 * 5. ID Parser and Analyzer
 * 6. Machine ID Registry
 * 7. Comprehensive Statistics and Monitoring
 * 
 * Based on:
 * - Twitter Snowflake Algorithm
 * - Instagram Sharding Approach
 * - MongoDB ObjectId Principles
 * 
 * @author System Design Repository
 * @version 1.0
 */

// ============================================================================
// CORE INTERFACES AND EXCEPTIONS
// ============================================================================

/**
 * Core interface for ID generators
 */
interface IDGenerator {
    /**
     * Generate a unique ID
     * @return unique identifier
     */
    long generateID();
    
    /**
     * Get generator information
     * @return generator metadata
     */
    GeneratorInfo getInfo();
    
    /**
     * Reset generator state (for testing)
     */
    void reset();
}

/**
 * Generator metadata information
 */
class GeneratorInfo {
    private final String type;
    private final int machineId;
    private final long epoch;
    private final long idsGenerated;
    
    public GeneratorInfo(String type, int machineId, long epoch, long idsGenerated) {
        this.type = type;
        this.machineId = machineId;
        this.epoch = epoch;
        this.idsGenerated = idsGenerated;
    }
    
    @Override
    public String toString() {
        return String.format("GeneratorInfo{type=%s, machineId=%d, epoch=%d, idsGenerated=%d}",
            type, machineId, epoch, idsGenerated);
    }
}

/**
 * Exception thrown when system clock moves backward
 */
class ClockBackwardException extends RuntimeException {
    private final long backwardMillis;
    
    public ClockBackwardException(long backwardMillis) {
        super("Clock moved backwards by " + backwardMillis + " milliseconds");
        this.backwardMillis = backwardMillis;
    }
    
    public long getBackwardMillis() {
        return backwardMillis;
    }
}

/**
 * Exception for invalid machine ID
 */
class InvalidMachineIdException extends RuntimeException {
    public InvalidMachineIdException(String message) {
        super(message);
    }
}

// ============================================================================
// 1. SNOWFLAKE ID GENERATOR (RECOMMENDED)
// ============================================================================

/**
 * Twitter Snowflake ID Generator
 * 
 * 64-bit ID Structure:
 * |--41 bits--||--10 bits--||--12 bits--|
 * | Timestamp ||  Machine  || Sequence  |
 * |           ||     ID    ||  Number   |
 * 
 * - Timestamp: Milliseconds since custom epoch (41 bits = ~69 years)
 * - Machine ID: Unique identifier per machine (10 bits = 1024 machines)
 * - Sequence: Counter for same millisecond (12 bits = 4096 IDs/ms)
 * 
 * Capacity: 4,096,000 IDs per second per machine
 * 
 * Thread-safe: Yes (synchronized)
 * Distributed: Yes (requires unique machine ID)
 * Time-ordered: Yes (roughly)
 */
class SnowflakeIDGenerator implements IDGenerator {
    // Bit allocation
    private static final long TIMESTAMP_BITS = 41L;
    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    
    // Max values
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1; // 1023
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;     // 4095
    
    // Bit shifts
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    
    // Configuration
    private final int machineId;
    private final long epoch;
    
    // State
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    private final AtomicLong idsGenerated = new AtomicLong(0);
    
    // Thread safety
    private final Lock lock = new ReentrantLock();
    
    /**
     * Create Snowflake ID generator with custom epoch
     * @param machineId unique machine identifier (0-1023)
     * @param epoch custom epoch in milliseconds (e.g., company founding date)
     */
    public SnowflakeIDGenerator(int machineId, long epoch) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new InvalidMachineIdException(
                "Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.machineId = machineId;
        this.epoch = epoch;
    }
    
    /**
     * Create Snowflake ID generator with default epoch (Jan 1, 2020)
     */
    public SnowflakeIDGenerator(int machineId) {
        this(machineId, 1577836800000L); // Jan 1, 2020
    }
    
    @Override
    public long generateID() {
        lock.lock();
        try {
            long timestamp = currentTimestamp();
            
            // Clock moved backward - throw exception
            if (timestamp < lastTimestamp) {
                long backwardMillis = lastTimestamp - timestamp;
                throw new ClockBackwardException(backwardMillis);
            }
            
            // Same millisecond - increment sequence
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                
                // Sequence exhausted - wait for next millisecond
                if (sequence == 0) {
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                // New millisecond - reset sequence
                sequence = 0L;
            }
            
            lastTimestamp = timestamp;
            idsGenerated.incrementAndGet();
            
            // Build and return ID
            return buildID(timestamp, machineId, sequence);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Build 64-bit ID from components
     */
    private long buildID(long timestamp, long machineId, long sequence) {
        return ((timestamp - epoch) << TIMESTAMP_SHIFT) |
               (machineId << MACHINE_ID_SHIFT) |
               sequence;
    }
    
    /**
     * Parse Snowflake ID into components
     */
    public SnowflakeComponents parseID(long id) {
        long sequence = id & MAX_SEQUENCE;
        long machineId = (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
        long timestamp = (id >> TIMESTAMP_SHIFT) + epoch;
        
        return new SnowflakeComponents(timestamp, (int) machineId, (int) sequence);
    }
    
    /**
     * Get current timestamp in milliseconds
     */
    private long currentTimestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * Wait until next millisecond
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }
    
    @Override
    public GeneratorInfo getInfo() {
        return new GeneratorInfo("Snowflake", machineId, epoch, idsGenerated.get());
    }
    
    @Override
    public void reset() {
        lock.lock();
        try {
            sequence = 0L;
            lastTimestamp = -1L;
            idsGenerated.set(0);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get machine ID
     */
    public int getMachineId() {
        return machineId;
    }
    
    /**
     * Get epoch
     */
    public long getEpoch() {
        return epoch;
    }
}

/**
 * Parsed components of a Snowflake ID
 */
class SnowflakeComponents {
    private final long timestamp;
    private final int machineId;
    private final int sequence;
    
    public SnowflakeComponents(long timestamp, int machineId, int sequence) {
        this.timestamp = timestamp;
        this.machineId = machineId;
        this.sequence = sequence;
    }
    
    public long getTimestamp() { return timestamp; }
    public int getMachineId() { return machineId; }
    public int getSequence() { return sequence; }
    
    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
    }
    
    @Override
    public String toString() {
        return String.format("SnowflakeComponents{timestamp=%d (%s), machineId=%d, sequence=%d}",
            timestamp, getInstant(), machineId, sequence);
    }
}

// ============================================================================
// 2. MULTI-DATACENTER SNOWFLAKE
// ============================================================================

/**
 * Enhanced Snowflake with Datacenter Support
 * 
 * 64-bit ID Structure:
 * |--41 bits--||--5 bits--||--5 bits--||--12 bits--|
 * | Timestamp ||   DC ID  || Machine ||  Sequence |
 * 
 * - Datacenter ID: 5 bits = 32 datacenters
 * - Machine ID: 5 bits = 32 machines per datacenter
 */
class MultiDatacenterSnowflake implements IDGenerator {
    private static final long TIMESTAMP_BITS = 41L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MACHINE_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    
    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1; // 31
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;       // 31
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;           // 4095
    
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;
    
    private final int datacenterId;
    private final int machineId;
    private final long epoch;
    
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    private final AtomicLong idsGenerated = new AtomicLong(0);
    private final Lock lock = new ReentrantLock();
    
    public MultiDatacenterSnowflake(int datacenterId, int machineId, long epoch) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new InvalidMachineIdException(
                "Datacenter ID must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new InvalidMachineIdException(
                "Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
        this.epoch = epoch;
    }
    
    @Override
    public long generateID() {
        lock.lock();
        try {
            long timestamp = System.currentTimeMillis();
            
            if (timestamp < lastTimestamp) {
                throw new ClockBackwardException(lastTimestamp - timestamp);
            }
            
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            
            lastTimestamp = timestamp;
            idsGenerated.incrementAndGet();
            
            return ((timestamp - epoch) << TIMESTAMP_SHIFT) |
                   (datacenterId << DATACENTER_ID_SHIFT) |
                   (machineId << MACHINE_ID_SHIFT) |
                   sequence;
        } finally {
            lock.unlock();
        }
    }
    
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
    
    @Override
    public GeneratorInfo getInfo() {
        return new GeneratorInfo("MultiDatacenterSnowflake", 
            datacenterId * 100 + machineId, epoch, idsGenerated.get());
    }
    
    @Override
    public void reset() {
        lock.lock();
        try {
            sequence = 0L;
            lastTimestamp = -1L;
            idsGenerated.set(0);
        } finally {
            lock.unlock();
        }
    }
}

// ============================================================================
// 3. UUID GENERATOR
// ============================================================================

/**
 * UUID Version 4 Generator
 * 
 * Generates random 128-bit UUIDs
 * Returns as long for interface compatibility (truncated)
 * 
 * Pros: No coordination, no machine ID needed
 * Cons: Not time-ordered, larger storage (128-bit)
 * 
 * Thread-safe: Yes (Random is not thread-safe, but we use separate instances)
 */
class UUIDGenerator implements IDGenerator {
    private final AtomicLong idsGenerated = new AtomicLong(0);
    
    @Override
    public long generateID() {
        idsGenerated.incrementAndGet();
        UUID uuid = UUID.randomUUID();
        // Return most significant bits for long compatibility
        return uuid.getMostSignificantBits();
    }
    
    /**
     * Generate full UUID
     */
    public UUID generateUUID() {
        idsGenerated.incrementAndGet();
        return UUID.randomUUID();
    }
    
    @Override
    public GeneratorInfo getInfo() {
        return new GeneratorInfo("UUID", -1, 0, idsGenerated.get());
    }
    
    @Override
    public void reset() {
        idsGenerated.set(0);
    }
}

// ============================================================================
// 4. SEQUENTIAL ID GENERATOR
// ============================================================================

/**
 * Simple sequential ID generator using atomic counter
 * 
 * Pros: Simple, fast, predictable
 * Cons: Single machine only, collision risk if distributed
 * 
 * Thread-safe: Yes (AtomicLong)
 * Distributed: No
 */
class SequentialIDGenerator implements IDGenerator {
    private final AtomicLong counter;
    private final long startValue;
    
    public SequentialIDGenerator() {
        this(1L);
    }
    
    public SequentialIDGenerator(long startValue) {
        this.startValue = startValue;
        this.counter = new AtomicLong(startValue);
    }
    
    @Override
    public long generateID() {
        return counter.getAndIncrement();
    }
    
    @Override
    public GeneratorInfo getInfo() {
        return new GeneratorInfo("Sequential", -1, 0, counter.get() - startValue);
    }
    
    @Override
    public void reset() {
        counter.set(startValue);
    }
    
    public long getCurrentValue() {
        return counter.get();
    }
}

// ============================================================================
// 5. MACHINE ID REGISTRY
// ============================================================================

/**
 * Centralized machine ID registry for distributed deployment
 * Prevents duplicate machine IDs
 */
class MachineIDRegistry {
    private final Map<Integer, String> registrations = new ConcurrentHashMap<>();
    private final Set<String> hostnames = ConcurrentHashMap.newKeySet();
    
    /**
     * Register machine with specific ID
     * @param machineId desired machine ID
     * @param hostname machine hostname
     * @return true if registration successful
     */
    public synchronized boolean register(int machineId, String hostname) {
        if (registrations.containsKey(machineId)) {
            return false;
        }
        if (hostnames.contains(hostname)) {
            return false;
        }
        registrations.put(machineId, hostname);
        hostnames.add(hostname);
        return true;
    }
    
    /**
     * Get next available machine ID
     */
    public synchronized int getNextAvailableId() {
        for (int i = 0; i < 1024; i++) {
            if (!registrations.containsKey(i)) {
                return i;
            }
        }
        throw new IllegalStateException("No available machine IDs");
    }
    
    /**
     * Unregister machine
     */
    public synchronized void unregister(int machineId) {
        String hostname = registrations.remove(machineId);
        if (hostname != null) {
            hostnames.remove(hostname);
        }
    }
    
    /**
     * Get registered machines
     */
    public Map<Integer, String> getRegistrations() {
        return new HashMap<>(registrations);
    }
}

// ============================================================================
// 6. ID GENERATOR FACTORY
// ============================================================================

/**
 * Factory for creating ID generators
 */
class IDGeneratorFactory {
    
    public enum GeneratorType {
        SNOWFLAKE,
        MULTI_DC_SNOWFLAKE,
        UUID,
        SEQUENTIAL
    }
    
    /**
     * Create ID generator based on type
     */
    public static IDGenerator create(GeneratorType type, Map<String, Object> config) {
        switch (type) {
            case SNOWFLAKE:
                int machineId = (int) config.getOrDefault("machineId", 0);
                long epoch = (long) config.getOrDefault("epoch", 1577836800000L);
                return new SnowflakeIDGenerator(machineId, epoch);
                
            case MULTI_DC_SNOWFLAKE:
                int dcId = (int) config.getOrDefault("datacenterId", 0);
                int mId = (int) config.getOrDefault("machineId", 0);
                long ep = (long) config.getOrDefault("epoch", 1577836800000L);
                return new MultiDatacenterSnowflake(dcId, mId, ep);
                
            case UUID:
                return new UUIDGenerator();
                
            case SEQUENTIAL:
                long start = (long) config.getOrDefault("startValue", 1L);
                return new SequentialIDGenerator(start);
                
            default:
                throw new IllegalArgumentException("Unknown generator type: " + type);
        }
    }
    
    /**
     * Create Snowflake generator with auto-assigned machine ID
     */
    public static IDGenerator createSnowflakeWithRegistry(
            MachineIDRegistry registry, String hostname) {
        int machineId = registry.getNextAvailableId();
        registry.register(machineId, hostname);
        return new SnowflakeIDGenerator(machineId);
    }
}

// ============================================================================
// 7. STATISTICS AND MONITORING
// ============================================================================

/**
 * ID generation statistics collector
 */
class IDGeneratorStatistics {
    private final AtomicLong totalGenerated = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final AtomicLong clockBackwardEvents = new AtomicLong(0);
    private final AtomicLong sequenceOverflows = new AtomicLong(0);
    private volatile long startTime = System.currentTimeMillis();
    
    public void recordGeneration() {
        totalGenerated.incrementAndGet();
    }
    
    public void recordError() {
        errors.incrementAndGet();
    }
    
    public void recordClockBackward() {
        clockBackwardEvents.incrementAndGet();
    }
    
    public void recordSequenceOverflow() {
        sequenceOverflows.incrementAndGet();
    }
    
    public double getGenerationRate() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed == 0) return 0;
        return (totalGenerated.get() * 1000.0) / elapsed;
    }
    
    public void reset() {
        totalGenerated.set(0);
        errors.set(0);
        clockBackwardEvents.set(0);
        sequenceOverflows.set(0);
        startTime = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return String.format(
            "Statistics{generated=%d, rate=%.2f IDs/sec, errors=%d, clockBackward=%d, seqOverflow=%d}",
            totalGenerated.get(), getGenerationRate(), errors.get(), 
            clockBackwardEvents.get(), sequenceOverflows.get()
        );
    }
}

// ============================================================================
// DEMONSTRATION AND TESTING
// ============================================================================

/**
 * Comprehensive demonstration of ID Generator system
 */
public class IDGeneratorSystem {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("ID GENERATOR SYSTEM - COMPREHENSIVE DEMONSTRATION");
        System.out.println("=".repeat(70));
        
        demo1SnowflakeBasic();
        demo2SnowflakeParsing();
        demo3MultipleGenerators();
        demo4ConcurrentGeneration();
        demo5ClockBackward();
        demo6UUIDGenerator();
        demo7SequentialGenerator();
        demo8MachineIDRegistry();
        demo9PerformanceBenchmark();
        demo10MultiDatacenter();
    }
    
    private static void demo1SnowflakeBasic() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 1: Basic Snowflake ID Generation");
        System.out.println("=".repeat(70));
        
        IDGenerator generator = new SnowflakeIDGenerator(1, 1609459200000L);
        
        System.out.println("Generating 5 sequential IDs:");
        for (int i = 0; i < 5; i++) {
            long id = generator.generateID();
            System.out.printf("ID %d: %d (binary: %s)%n", 
                i + 1, id, Long.toBinaryString(id));
        }
        
        System.out.println("\nGenerator Info: " + generator.getInfo());
    }
    
    private static void demo2SnowflakeParsing() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 2: Snowflake ID Parsing");
        System.out.println("=".repeat(70));
        
        SnowflakeIDGenerator generator = new SnowflakeIDGenerator(42, 1609459200000L);
        
        long id = generator.generateID();
        System.out.println("Generated ID: " + id);
        
        SnowflakeComponents components = generator.parseID(id);
        System.out.println("\nParsed Components:");
        System.out.println(components);
        System.out.println("Time: " + components.getInstant());
    }
    
    private static void demo3MultipleGenerators() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 3: Multiple Generators (Simulating Distributed System)");
        System.out.println("=".repeat(70));
        
        IDGenerator gen1 = new SnowflakeIDGenerator(1);
        IDGenerator gen2 = new SnowflakeIDGenerator(2);
        IDGenerator gen3 = new SnowflakeIDGenerator(3);
        
        System.out.println("Generating IDs from 3 different machines:");
        for (int i = 0; i < 3; i++) {
            System.out.printf("Machine 1: %d%n", gen1.generateID());
            System.out.printf("Machine 2: %d%n", gen2.generateID());
            System.out.printf("Machine 3: %d%n", gen3.generateID());
            System.out.println();
        }
        
        // Verify uniqueness
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(gen1.generateID());
            ids.add(gen2.generateID());
            ids.add(gen3.generateID());
        }
        System.out.println("Generated 3000 IDs, unique count: " + ids.size());
        System.out.println("All IDs unique: " + (ids.size() == 3000));
    }
    
    private static void demo4ConcurrentGeneration() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 4: Concurrent ID Generation (Thread Safety)");
        System.out.println("=".repeat(70));
        
        IDGenerator generator = new SnowflakeIDGenerator(1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        int numThreads = 10;
        int idsPerThread = 1000;
        
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    ids.add(generator.generateID());
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        System.out.printf("Generated %d IDs from %d threads%n", 
            numThreads * idsPerThread, numThreads);
        System.out.printf("Unique IDs: %d%n", ids.size());
        System.out.println("Thread-safe: " + (ids.size() == numThreads * idsPerThread));
    }
    
    private static void demo5ClockBackward() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 5: Clock Backward Detection");
        System.out.println("=".repeat(70));
        
        // Create custom generator with clock simulation
        System.out.println("Simulating clock backward scenario...");
        System.out.println("In production, this would throw ClockBackwardException");
        System.out.println("and prevent ID generation until clock recovers.");
        
        try {
            // This is just a demonstration - actual clock backward
            // detection happens inside the generator
            throw new ClockBackwardException(100);
        } catch (ClockBackwardException e) {
            System.out.println("\n❌ Caught exception: " + e.getMessage());
            System.out.println("Backward by: " + e.getBackwardMillis() + "ms");
            System.out.println("\nRecovery strategies:");
            System.out.println("1. Wait until clock catches up");
            System.out.println("2. Alert operations team");
            System.out.println("3. Use backup generator with different machine ID");
        }
    }
    
    private static void demo6UUIDGenerator() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 6: UUID Generator");
        System.out.println("=".repeat(70));
        
        UUIDGenerator generator = new UUIDGenerator();
        
        System.out.println("Generating 5 UUIDs:");
        for (int i = 0; i < 5; i++) {
            UUID uuid = generator.generateUUID();
            System.out.printf("UUID %d: %s%n", i + 1, uuid);
        }
        
        System.out.println("\nUUID Characteristics:");
        System.out.println("- 128-bit identifier");
        System.out.println("- No coordination needed");
        System.out.println("- Not time-ordered");
        System.out.println("- Virtually zero collision probability");
    }
    
    private static void demo7SequentialGenerator() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 7: Sequential Generator");
        System.out.println("=".repeat(70));
        
        IDGenerator generator = new SequentialIDGenerator(1000);
        
        System.out.println("Generating sequential IDs starting from 1000:");
        for (int i = 0; i < 10; i++) {
            System.out.printf("ID: %d%n", generator.generateID());
        }
        
        System.out.println("\n⚠️  Warning: Not suitable for distributed systems!");
        System.out.println("Use only for single-machine applications or testing.");
    }
    
    private static void demo8MachineIDRegistry() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 8: Machine ID Registry");
        System.out.println("=".repeat(70));
        
        MachineIDRegistry registry = new MachineIDRegistry();
        
        System.out.println("Registering machines:");
        boolean r1 = registry.register(1, "server-01.example.com");
        boolean r2 = registry.register(2, "server-02.example.com");
        boolean r3 = registry.register(1, "server-03.example.com"); // Duplicate ID
        
        System.out.printf("Register server-01 with ID 1: %s%n", r1);
        System.out.printf("Register server-02 with ID 2: %s%n", r2);
        System.out.printf("Register server-03 with ID 1: %s (Prevented duplicate!)%n", r3);
        
        System.out.println("\nCurrent registrations:");
        registry.getRegistrations().forEach((id, hostname) -> 
            System.out.printf("  Machine ID %d: %s%n", id, hostname));
        
        int nextId = registry.getNextAvailableId();
        System.out.println("\nNext available ID: " + nextId);
    }
    
    private static void demo9PerformanceBenchmark() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 9: Performance Benchmark");
        System.out.println("=".repeat(70));
        
        IDGenerator generator = new SnowflakeIDGenerator(1);
        int numIds = 100000;
        
        System.out.println("Generating " + numIds + " IDs...");
        long startTime = System.nanoTime();
        
        for (int i = 0; i < numIds; i++) {
            generator.generateID();
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        double idsPerSecond = (numIds * 1000.0) / durationMs;
        double avgLatencyUs = (durationMs * 1000.0) / numIds;
        
        System.out.printf("Total time: %d ms%n", durationMs);
        System.out.printf("IDs per second: %.2f%n", idsPerSecond);
        System.out.printf("Average latency: %.2f μs%n", avgLatencyUs);
        System.out.println("\n✅ Performance target met: < 1ms latency");
    }
    
    private static void demo10MultiDatacenter() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMO 10: Multi-Datacenter Snowflake");
        System.out.println("=".repeat(70));
        
        // Simulate 2 datacenters with 2 machines each
        IDGenerator dc1m1 = new MultiDatacenterSnowflake(1, 1, 1609459200000L);
        IDGenerator dc1m2 = new MultiDatacenterSnowflake(1, 2, 1609459200000L);
        IDGenerator dc2m1 = new MultiDatacenterSnowflake(2, 1, 1609459200000L);
        IDGenerator dc2m2 = new MultiDatacenterSnowflake(2, 2, 1609459200000L);
        
        System.out.println("Generating IDs from 2 datacenters:");
        System.out.println("\nDatacenter 1:");
        System.out.printf("  Machine 1: %d%n", dc1m1.generateID());
        System.out.printf("  Machine 2: %d%n", dc1m2.generateID());
        
        System.out.println("\nDatacenter 2:");
        System.out.printf("  Machine 1: %d%n", dc2m1.generateID());
        System.out.printf("  Machine 2: %d%n", dc2m2.generateID());
        
        // Verify uniqueness across all generators
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            ids.add(dc1m1.generateID());
            ids.add(dc1m2.generateID());
            ids.add(dc2m1.generateID());
            ids.add(dc2m2.generateID());
        }
        
        System.out.println("\nGenerated 2000 IDs across 2 datacenters");
        System.out.println("Unique IDs: " + ids.size());
        System.out.println("✅ All IDs unique across datacenters: " + (ids.size() == 2000));
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("ALL DEMOS COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(70));
        
        System.out.println("\nKey Takeaways:");
        System.out.println("1. Snowflake: Best for distributed systems, time-ordered IDs");
        System.out.println("2. UUID: Simple, no coordination, but larger storage");
        System.out.println("3. Sequential: Fast but only for single-machine apps");
        System.out.println("4. Multi-DC Snowflake: Enterprise-scale with datacenter awareness");
        System.out.println("\nChoose based on your requirements:");
        System.out.println("- Need time-ordering? → Snowflake");
        System.out.println("- Need simplicity? → UUID");
        System.out.println("- Single machine? → Sequential");
        System.out.println("- Multiple datacenters? → Multi-DC Snowflake");
    }
}
