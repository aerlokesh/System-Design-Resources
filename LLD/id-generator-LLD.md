# ID Generator - Low-Level Design

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [API Design](#api-design)
5. [Implementation Approaches](#implementation-approaches)
6. [Concurrency & Thread Safety](#concurrency--thread-safety)
7. [Edge Cases](#edge-cases)
8. [Extensibility](#extensibility)

---

## Understanding the Problem

### What is an ID Generator?

An ID Generator is a distributed system component that generates unique identifiers at scale. These IDs are used to uniquely identify entities like users, orders, transactions, or messages across multiple servers and data centers. The system must ensure:
- **Uniqueness**: No two IDs should ever be the same
- **Scalability**: Generate millions of IDs per second
- **High Availability**: Always available even during partial failures
- **Sortability** (optional): IDs can be time-ordered for better database performance
- **Compact**: IDs should be space-efficient (typically 64-bit integers)

### Real-World Examples
- **Twitter Snowflake**: 64-bit IDs with timestamp, datacenter ID, machine ID, and sequence
- **Instagram**: Modified Snowflake with shard ID
- **MongoDB ObjectId**: 12-byte identifier with timestamp, machine ID, process ID, counter
- **UUID**: 128-bit universally unique identifier

---

## Requirements

### Functional Requirements

1. **Generate Unique ID**
   - System generates a unique 64-bit integer ID
   - IDs must never collide across all machines and time
   - Support multiple ID generation strategies

2. **High Throughput**
   - Generate 10,000+ IDs per second per machine
   - Linear scalability with additional machines

3. **Time-Ordered IDs** (Snowflake approach)
   - IDs should be roughly time-ordered
   - Newer IDs should be larger than older IDs
   - Helps with database range queries and index performance

4. **Distributed Generation**
   - Multiple machines can generate IDs independently
   - No coordination required between machines for each ID
   - Each machine has a unique identifier

### Non-Functional Requirements

1. **Latency**: < 1ms per ID generation
2. **Availability**: 99.99% uptime
3. **Durability**: IDs should never be reused even after system restarts
4. **No Single Point of Failure**: System should work even if coordinator is down

### Out of Scope

- ID validation/verification service
- ID to entity mapping/lookup
- Distributed transaction coordination
- ID recycling/reuse of deleted entities
- Custom ID formats (e.g., alphanumeric strings)

---

## Core Entities and Relationships

Based on our requirements, here are the core entities:

### 1. **IDGenerator** (Main Interface)
The orchestrator that coordinates ID generation. Provides a unified interface for different ID generation strategies.

**Responsibilities:**
- Generate unique IDs
- Abstract different generation strategies
- Handle initialization and configuration

### 2. **SnowflakeIDGenerator** (Concrete Implementation)
Implements Twitter's Snowflake algorithm for distributed ID generation.

**Structure (64-bit ID):**
```
|--41 bits--||--10 bits--||--12 bits--|
| Timestamp ||  Machine  || Sequence  |
|           ||     ID    ||  Number   |
```

**Components:**
- **Timestamp** (41 bits): Milliseconds since custom epoch
  - Provides ~69 years of IDs (2^41 milliseconds)
- **Machine ID** (10 bits): Unique identifier for each machine
  - Supports up to 1024 machines (2^10)
- **Sequence** (12 bits): Counter for IDs generated in same millisecond
  - Supports 4096 IDs per millisecond per machine (2^12)

### 3. **UUIDGenerator** (Concrete Implementation)
Generates UUID Version 4 (random) identifiers.

**Characteristics:**
- 128-bit identifier
- Virtually zero collision probability
- Not time-ordered
- String representation (36 characters)

### 4. **SequentialIDGenerator** (Concrete Implementation)
Simple atomic counter-based generator.

**Characteristics:**
- Thread-safe atomic increment
- Single machine only (not distributed)
- Predictable sequence
- Risk of collision in distributed systems

### 5. **MachineIDProvider** (Configuration)
Provides unique machine identifiers for distributed deployment.

**Responsibilities:**
- Assign unique machine IDs
- Handle machine ID registration
- Prevent duplicate machine IDs

---

## API Design

### Core Interface

```java
public interface IDGenerator {
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
}
```

### Snowflake API

```java
public class SnowflakeIDGenerator implements IDGenerator {
    /**
     * Create Snowflake generator
     * @param machineId unique machine identifier (0-1023)
     * @param epoch custom epoch timestamp in milliseconds
     */
    public SnowflakeIDGenerator(int machineId, long epoch);
    
    /**
     * Generate unique Snowflake ID
     * @return 64-bit unique identifier
     * @throws ClockBackwardException if system clock moved backward
     */
    @Override
    public long generateID();
    
    /**
     * Parse Snowflake ID to extract components
     * @param id Snowflake ID to parse
     * @return parsed components (timestamp, machineId, sequence)
     */
    public SnowflakeComponents parseID(long id);
}
```

### Usage Examples

```java
// Snowflake ID Generator
IDGenerator generator = new SnowflakeIDGenerator(
    machineId: 1,
    epoch: 1609459200000L  // Jan 1, 2021
);

long userId = generator.generateID();
// Returns: 7058927249006886913

// Parse ID
SnowflakeComponents components = generator.parseID(userId);
System.out.println("Timestamp: " + components.getTimestamp());
System.out.println("Machine ID: " + components.getMachineId());
System.out.println("Sequence: " + components.getSequence());
```

---

## Implementation Approaches

### Approach 1: Snowflake (Recommended for Distributed Systems)

**Pros:**
- ✅ Distributed - no coordination needed
- ✅ Time-ordered - better for database indexing
- ✅ High performance - O(1) generation
- ✅ Compact - 64-bit integer
- ✅ Predictable capacity planning

**Cons:**
- ❌ Clock dependency - issues with clock skew
- ❌ Fixed machine ID required
- ❌ Limited by sequence size (4096/ms per machine)

**Best For:**
- Distributed microservices
- Time-series data
- High-volume transaction systems
- Database primary keys

### Approach 2: UUID Version 4

**Pros:**
- ✅ No coordination needed
- ✅ No machine ID required
- ✅ Virtually impossible to collide
- ✅ Language/platform support everywhere

**Cons:**
- ❌ 128-bit - larger storage
- ❌ Not time-ordered
- ❌ String representation in many systems
- ❌ Poor database index performance

**Best For:**
- Simple distributed systems
- When time-ordering not important
- Temporary identifiers
- Cross-platform compatibility

### Approach 3: Sequential (Single Machine)

**Pros:**
- ✅ Simple implementation
- ✅ Predictable sequence
- ✅ Small memory footprint
- ✅ Fast - atomic operation

**Cons:**
- ❌ Single machine only
- ❌ Collision risk in distributed setup
- ❌ Predictable (security risk)

**Best For:**
- Development/testing
- Single-server applications
- Order numbers within a session

---

## Concurrency & Thread Safety

### Challenge: Multiple Threads Generating IDs Simultaneously

**Problem Scenario:**
```
Thread 1: generateID() at time T1
Thread 2: generateID() at time T1 (same millisecond)
Thread 3: generateID() at time T1
```

All three threads need unique IDs even though they're in the same millisecond.

### Solution: Sequence Counter

```java
// Thread-safe sequence management
private long sequence = 0L;
private long lastTimestamp = -1L;

public synchronized long generateID() {
    long timestamp = currentTimestamp();
    
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
    return buildID(timestamp, machineId, sequence);
}
```

### Clock Backward Detection

**Problem:** System clock moves backward (NTP adjustment, manual change)

**Solution:**
```java
if (timestamp < lastTimestamp) {
    throw new ClockBackwardException(
        "Clock moved backwards. " +
        "Refusing to generate ID for " +
        (lastTimestamp - timestamp) + " milliseconds"
    );
}
```

**Alternative Recovery Strategies:**
1. Wait until clock catches up (blocking)
2. Use last known timestamp with incremented sequence
3. Shutdown and alert operators

---

## Edge Cases

### 1. Sequence Overflow
**Scenario:** More than 4096 requests in same millisecond on same machine

**Solution:** Wait for next millisecond
```java
if (sequence == 0) {
    timestamp = waitNextMillis(lastTimestamp);
}

private long waitNextMillis(long lastTimestamp) {
    long timestamp = currentTimestamp();
    while (timestamp <= lastTimestamp) {
        timestamp = currentTimestamp();
    }
    return timestamp;
}
```

### 2. Clock Synchronization
**Scenario:** Clocks drift between machines

**Solution:**
- Use NTP for clock synchronization
- Monitor clock skew alerts
- Implement clock backward detection
- Consider using logical clocks for critical systems

### 3. Machine ID Conflicts
**Scenario:** Two machines assigned same machine ID

**Solution:**
- Centralized machine ID registry
- Configuration validation at startup
- Health checks for duplicate detection

### 4. Epoch Overflow
**Scenario:** Timestamp bits exhausted (41 bits = ~69 years)

**Solution:**
- Plan epoch date far in past (e.g., company founding date)
- Monitor remaining timestamp space
- Plan migration strategy years in advance

### 5. System Restart
**Scenario:** Server restarts, sequence resets

**Solution:** No issue - timestamp will be different or sequence starts from 0 safely

### 6. High Load Burst
**Scenario:** Sudden spike of 100,000 requests

**Solution:**
- Sequence counter handles 4096/ms per machine
- 100,000 requests = ~25ms on single machine
- Scale horizontally with more machines

---

## Extensibility

### Future Extensions (Out of Current Scope)

#### 1. **Custom Bit Allocation**
Allow configurable bit sizes for different use cases:
```java
// More machines, fewer sequences
new SnowflakeIDGenerator()
    .withTimestampBits(41)
    .withMachineBits(12)   // 4096 machines
    .withSequenceBits(10)  // 1024 per ms
    .build();
```

#### 2. **Multi-Datacenter Support**
Add datacenter ID to distinguish between geographic regions:
```
|--41 bits--||--5 bits--||--5 bits--||--12 bits--|
| Timestamp ||   DC ID  || Machine ||  Sequence |
```

#### 3. **ID Reservation**
Pre-allocate ID ranges for batch operations:
```java
IDRange range = generator.reserveRange(1000);
// Returns: 100001 to 101000
```

#### 4. **Monitoring & Metrics**
Track ID generation patterns:
- IDs per second
- Sequence overflow events
- Clock backward detections
- Machine ID usage distribution

#### 5. **Custom Encoding**
Support different encodings:
- Base62 for URL-safe IDs
- Base36 for case-insensitive IDs
- Custom character sets

---

## Design Patterns Used

1. **Strategy Pattern**: Different ID generation strategies (Snowflake, UUID, Sequential)
2. **Factory Pattern**: Create appropriate generator based on configuration
3. **Singleton Pattern**: Single generator instance per machine
4. **Template Method**: Common ID generation flow with strategy-specific implementations

---

## Performance Characteristics

| Generator Type | IDs/Second | Latency | Storage | Time-Ordered |
|---------------|------------|---------|---------|--------------|
| Snowflake     | 4M+        | <1ms    | 8 bytes | ✅           |
| UUID v4       | 1M+        | <1ms    | 16 bytes| ❌           |
| Sequential    | 10M+       | <1µs    | 8 bytes | ✅           |

---

## Interview Tips

1. **Start with Requirements**: Clarify if IDs need to be time-ordered, the expected scale, and whether it's distributed
2. **Discuss Trade-offs**: Compare Snowflake vs UUID vs Sequential approaches
3. **Handle Edge Cases**: Show awareness of clock issues, sequence overflow, and machine ID conflicts
4. **Consider Scale**: Explain how the system handles millions of IDs per second
5. **Thread Safety**: Demonstrate understanding of concurrent ID generation
6. **Extensibility**: Mention future enhancements without over-engineering

---

## References

- Twitter Snowflake: https://github.com/twitter-archive/snowflake
- Instagram ID Generation: https://instagram-engineering.com/sharding-ids-at-instagram-1cf5a71e5a5c
- MongoDB ObjectId: https://docs.mongodb.com/manual/reference/method/ObjectId/
- Sonyflake (Sony's Snowflake variant): https://github.com/sony/sonyflake
