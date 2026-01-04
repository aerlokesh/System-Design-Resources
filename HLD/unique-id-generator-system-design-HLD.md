# Unique ID Generator System Design - High Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Capacity Estimation](#capacity-estimation)
4. [API Design](#api-design)
5. [High-Level Architecture](#high-level-architecture)
6. [Detailed Component Design](#detailed-component-design)
7. [Database Schema](#database-schema)
8. [Different Approaches & Comparison](#different-approaches--comparison)
9. [Trade-offs and Optimizations](#trade-offs-and-optimizations)
10. [Edge Cases & Failure Scenarios](#edge-cases--failure-scenarios)
11. [Monitoring and Metrics](#monitoring-and-metrics)
12. [Interview Tips](#interview-tips)

---

## Problem Statement

Design a distributed unique ID generator system that can generate billions of unique IDs per day across multiple data centers with the following characteristics:
- **Globally unique**: No two IDs should be the same
- **Scalable**: Handle high throughput (millions of IDs per second)
- **Available**: 99.99% availability
- **Low latency**: Generate IDs in < 10ms
- **Sortable by time** (optional but preferred)

**Real-world Examples**: Twitter Snowflake, Instagram ID generation, MongoDB ObjectID, UUID generation

---

## Requirements

### Functional Requirements
1. **Generate unique 64-bit IDs**
   - Each ID must be globally unique
   - No collisions across all servers
2. **ID generation should be fast** (< 10ms)
3. **IDs should be sortable by time** (chronologically ordered)
4. **Support high throughput** (millions of IDs/second)
5. **IDs should be numeric only** (for URL shortening, database indexing)

### Non-Functional Requirements
1. **High Availability**: 99.99% uptime
2. **Low Latency**: < 10ms to generate an ID
3. **Scalability**: Horizontally scalable to 1000s of servers
4. **Fault Tolerance**: System should work even if some nodes fail
5. **No coordination required**: Servers should generate IDs independently
6. **Compact IDs**: 64-bit integers (not strings like UUIDs)

### Extended Requirements
1. **Clock synchronization handling**: Deal with clock drift/skew
2. **Batch ID generation**: Generate multiple IDs in one request
3. **ID validation API**: Check if an ID is valid
4. **Extract metadata from ID**: Get timestamp, server ID from an ID

---

## Capacity Estimation

### Traffic Estimates
- **IDs generated per day**: 10 billion
- **IDs per second**: 10B / 86400 = ~116,000 IDs/sec
- **Peak traffic**: 3x average = 348,000 IDs/sec

### Storage Estimates
- **ID size**: 8 bytes (64-bit integer)
- **Daily storage**: 10B * 8 bytes = 80 GB/day
- **Annual storage**: 80 GB * 365 = 29.2 TB/year
- **With metadata** (timestamp, server_id): ~100 GB/day

### Bandwidth Estimates
- **Request size**: ~100 bytes (including headers)
- **Response size**: ~50 bytes (ID + metadata)
- **Incoming bandwidth**: 116K * 100 bytes = 11.6 MB/sec
- **Outgoing bandwidth**: 116K * 50 bytes = 5.8 MB/sec

### Server Estimates
- **IDs per server**: 10,000 IDs/sec per server
- **Servers needed**: 348,000 / 10,000 = ~35 servers (peak)
- **With redundancy**: 50-70 servers across multiple data centers

---

## API Design

### 1. Generate Single ID
```
POST /api/v1/id/generate
Response:
{
  "id": 1234567890123456789,
  "timestamp": 1702345678000,
  "generated_at": "2024-12-12T10:30:00Z"
}
```

### 2. Generate Batch IDs
```
POST /api/v1/id/batch
Request:
{
  "count": 100
}
Response:
{
  "ids": [123456, 123457, 123458, ...],
  "count": 100
}
```

### 3. Get ID Metadata
```
GET /api/v1/id/{id}/metadata
Response:
{
  "id": 1234567890123456789,
  "timestamp": 1702345678000,
  "datacenter_id": 2,
  "machine_id": 15,
  "sequence": 4095
}
```

### 4. Validate ID
```
GET /api/v1/id/{id}/validate
Response:
{
  "valid": true,
  "reason": "Valid ID format"
}
```

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Load Balancer                            │
│                      (Route to any ID server)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  ID Generator   │ │  ID Generator   │ │  ID Generator   │
│    Server 1     │ │    Server 2     │ │    Server N     │
│                 │ │                 │ │                 │
│  Machine ID: 1  │ │  Machine ID: 2  │ │  Machine ID: N  │
│  Datacenter: A  │ │  Datacenter: A  │ │  Datacenter: B  │
└─────────────────┘ └─────────────────┘ └─────────────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
                             ▼
                ┌────────────────────────┐
                │  Configuration Store   │
                │   (ZooKeeper/etcd)     │
                │  - Machine IDs         │
                │  - Datacenter IDs      │
                └────────────────────────┘
```

### Key Components

1. **ID Generator Servers**
   - Stateless servers that generate IDs
   - Each server has unique machine ID
   - No coordination needed between servers

2. **Load Balancer**
   - Distributes requests across servers
   - Health checking
   - Can route to any server (stateless)

3. **Configuration Store (ZooKeeper/etcd)**
   - Assigns unique machine IDs to each server
   - Stores datacenter configurations
   - Leader election for machine ID assignment

4. **Monitoring & Alerting**
   - Track ID generation rate
   - Monitor clock drift
   - Alert on collisions (should never happen)

---

## Detailed Component Design

### Approach 1: Twitter Snowflake (Recommended)

#### ID Structure (64 bits)
```
┌─────────────────────────────────────────────────────────────────┐
│ 1 bit │  41 bits         │  10 bits        │  12 bits          │
│ Sign  │  Timestamp       │  Machine ID     │  Sequence Number  │
│  0    │  (milliseconds)  │  (datacenter +  │  (0-4095)        │
│       │  since epoch)    │   machine)      │                   │
└─────────────────────────────────────────────────────────────────┘
```

#### Bit Breakdown
- **1 bit**: Sign bit (always 0, unused)
- **41 bits**: Timestamp in milliseconds since custom epoch
  - Can represent ~69 years: 2^41 / (1000 * 3600 * 24 * 365) ≈ 69 years
  - Custom epoch: January 1, 2024 (adjust as needed)
- **10 bits**: Machine ID (datacenter ID + machine ID)
  - 5 bits datacenter ID: 32 datacenters
  - 5 bits machine ID: 32 machines per datacenter
  - Total: 1024 unique machines
- **12 bits**: Sequence number (0-4095)
  - Resets every millisecond
  - Allows 4096 IDs per millisecond per machine

#### ID Generation Algorithm

```java
public class SnowflakeIdGenerator {
    private final long epoch = 1704067200000L; // Jan 1, 2024
    private final long datacenterIdBits = 5L;
    private final long machineIdBits = 5L;
    private final long sequenceBits = 12L;
    
    private final long maxDatacenterId = ~(-1L << datacenterIdBits);
    private final long maxMachineId = ~(-1L << machineIdBits);
    private final long maxSequence = ~(-1L << sequenceBits);
    
    private final long machineIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + machineIdBits;
    private final long timestampShift = sequenceBits + machineIdBits + datacenterIdBits;
    
    private long datacenterId;
    private long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    public synchronized long generateId() {
        long timestamp = System.currentTimeMillis();
        
        // Clock moved backwards - wait
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }
        
        // Same millisecond - increment sequence
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0) {
                // Sequence exhausted, wait for next millisecond
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond - reset sequence
            sequence = 0L;
        }
        
        lastTimestamp = timestamp;
        
        // Combine all parts into 64-bit ID
        return ((timestamp - epoch) << timestampShift)
             | (datacenterId << datacenterIdShift)
             | (machineId << machineIdShift)
             | sequence;
    }
    
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
```

#### Advantages
- ✅ **Sortable**: IDs are chronologically ordered
- ✅ **Compact**: 64-bit integers (vs 128-bit UUIDs)
- ✅ **Scalable**: No coordination needed between servers
- ✅ **High performance**: Millions of IDs per second
- ✅ **Metadata extraction**: Can extract timestamp, machine ID

#### Disadvantages
- ❌ **Clock dependency**: Requires synchronized clocks (NTP)
- ❌ **Clock drift issues**: Need to handle clock going backwards
- ❌ **Limited lifetime**: 41 bits = ~69 years from epoch

---

### Approach 2: UUID (Universally Unique Identifier)

#### Structure
- **128-bit number** (not 64-bit)
- Represented as 32 hexadecimal digits
- Example: `550e8400-e29b-41d4-a716-446655440000`

#### Types
- **UUID v1**: Timestamp + MAC address (predictable)
- **UUID v4**: Random (most common)
- **UUID v7**: Unix timestamp + random (sortable)

#### Advantages
- ✅ **No coordination needed**: Can generate anywhere
- ✅ **Extremely low collision probability**: 2^128 possibilities
- ✅ **Simple to implement**: Built into most languages

#### Disadvantages
- ❌ **Not 64-bit**: 128-bit (16 bytes vs 8 bytes)
- ❌ **Not integer**: String representation
- ❌ **Not sortable** (except UUID v7)
- ❌ **Poor database indexing**: Random UUIDs cause index fragmentation
- ❌ **Not human-readable**

---

### Approach 3: Database Auto-Increment

#### Structure
```sql
CREATE TABLE id_generator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
);

-- Generate ID
INSERT INTO id_generator VALUES ();
SELECT LAST_INSERT_ID();
```

#### Advantages
- ✅ **Simple to implement**
- ✅ **Guaranteed uniqueness**
- ✅ **Sortable** (sequential)

#### Disadvantages
- ❌ **Single point of failure**: Database bottleneck
- ❌ **Poor scalability**: Limited by database throughput
- ❌ **Network latency**: Extra database round trip
- ❌ **Hard to scale horizontally**: Need sharding/multi-master

#### Multi-Master Approach
```
Server 1: Generates 1, 3, 5, 7, 9, ... (odd numbers)
Server 2: Generates 2, 4, 6, 8, 10, ... (even numbers)
```
- Use `AUTO_INCREMENT_INCREMENT = 2`
- Use `AUTO_INCREMENT_OFFSET = 1 or 2`
- **Problem**: Gaps in sequence, not truly sequential

---

### Approach 4: Ticket Server (Centralized)

#### Architecture
```
┌──────────────┐     ┌──────────────┐
│ App Server 1 │────▶│              │
├──────────────┤     │   Ticket     │
│ App Server 2 │────▶│   Server     │──▶ Redis/DB
├──────────────┤     │  (ID Gen)    │
│ App Server N │────▶│              │
└──────────────┘     └──────────────┘
```

#### Implementation (Redis)
```redis
INCR id_counter  # Atomic increment
```

#### Advantages
- ✅ **Simple implementation**
- ✅ **Guaranteed uniqueness**
- ✅ **Numeric IDs**

#### Disadvantages
- ❌ **Single point of failure**
- ❌ **Scalability bottleneck**
- ❌ **Network latency for every request**

---

### Approach 5: MongoDB ObjectID

#### Structure (96 bits = 12 bytes)
```
┌──────────────────────────────────────────────────────┐
│ 4 bytes     │ 3 bytes    │ 2 bytes   │ 3 bytes      │
│ Timestamp   │ Machine ID │ Process   │ Counter      │
│ (seconds)   │            │ ID        │              │
└──────────────────────────────────────────────────────┘
```

#### Example
```
507f1f77bcf86cd799439011
└─┬─┘└─┬─┘└─┬─┘└───┬────┘
  │    │    │      └─ Counter (3 bytes)
  │    │    └──────── Process ID (2 bytes)
  │    └───────────── Machine ID (3 bytes)
  └────────────────── Timestamp (4 bytes)
```

#### Advantages
- ✅ **No coordination needed**
- ✅ **Sortable by time**
- ✅ **Collision resistant**

#### Disadvantages
- ❌ **96-bit** (not 64-bit requirement)
- ❌ **Not numeric** (hex string)
- ❌ **Timestamp in seconds** (lower resolution)

---

### Approach 6: Instagram's ID Generation

#### Structure (64 bits)
```
┌─────────────────────────────────────────────┐
│ 41 bits        │ 13 bits    │ 10 bits      │
│ Timestamp (ms) │ Shard ID   │ Sequence     │
└─────────────────────────────────────────────┘
```

#### Features
- **41 bits timestamp**: Millisecond precision
- **13 bits shard ID**: 8192 shards (PostgreSQL DB shards)
- **10 bits sequence**: 1024 per millisecond per shard

#### Implementation (PostgreSQL)
```sql
CREATE SEQUENCE table_id_seq;

CREATE OR REPLACE FUNCTION next_id(OUT result bigint) AS $$
DECLARE
    our_epoch bigint := 1314220021721;
    seq_id bigint;
    now_millis bigint;
    shard_id int := 1;
BEGIN
    SELECT nextval('table_id_seq') % 1024 INTO seq_id;
    SELECT FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000) INTO now_millis;
    result := (now_millis - our_epoch) << 23;
    result := result | (shard_id << 10);
    result := result | (seq_id);
END;
$$ LANGUAGE PLPGSQL;
```

---

## Different Approaches Comparison

| Approach | Uniqueness | Scalability | Sortable | Size | Complexity | Best For |
|----------|-----------|-------------|----------|------|------------|----------|
| **Snowflake** | ✅ Guaranteed | ⭐⭐⭐⭐⭐ | ✅ Yes | 64-bit | Medium | **Distributed systems** |
| **UUID v4** | ✅ Very High | ⭐⭐⭐⭐⭐ | ❌ No | 128-bit | Low | Quick prototypes |
| **UUID v7** | ✅ Very High | ⭐⭐⭐⭐⭐ | ✅ Yes | 128-bit | Low | Modern apps |
| **DB Auto-Inc** | ✅ Guaranteed | ⭐ | ✅ Yes | 64-bit | Low | Small apps |
| **Ticket Server** | ✅ Guaranteed | ⭐⭐ | ✅ Yes | 64-bit | Low | Medium apps |
| **MongoDB OID** | ✅ Very High | ⭐⭐⭐⭐ | ✅ Yes | 96-bit | Low | MongoDB apps |
| **Instagram** | ✅ Guaranteed | ⭐⭐⭐⭐ | ✅ Yes | 64-bit | Medium | Sharded DBs |

---

## Database Schema

### ID Generation Log (Optional - for auditing)
```sql
CREATE TABLE id_generation_log (
    id BIGINT PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    datacenter_id INT NOT NULL,
    machine_id INT NOT NULL,
    sequence INT NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_timestamp (timestamp),
    INDEX idx_machine (datacenter_id, machine_id)
);
```

### Machine Registry
```sql
CREATE TABLE machine_registry (
    machine_id INT PRIMARY KEY,
    datacenter_id INT NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    status ENUM('active', 'inactive') DEFAULT 'active',
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat TIMESTAMP,
    UNIQUE KEY unique_datacenter_machine (datacenter_id, machine_id)
);
```

---

## Trade-offs and Optimizations

### 1. Clock Synchronization

**Problem**: Server clocks may drift

**Solutions**:
1. **NTP (Network Time Protocol)**
   - Synchronize clocks across servers
   - Typical accuracy: 1-50ms
   - Monitor clock drift

2. **Handle Clock Going Backwards**
   ```java
   if (currentTime < lastTimestamp) {
       // Option 1: Throw exception
       throw new ClockMovedBackwardsException();
       
       // Option 2: Wait until clock catches up
       while (currentTime <= lastTimestamp) {
           Thread.sleep(1);
           currentTime = System.currentTimeMillis();
       }
       
       // Option 3: Use last timestamp and increment sequence
       currentTime = lastTimestamp;
       sequence = (sequence + 1) & maxSequence;
   }
   ```

3. **Logical Clocks (Hybrid Logical Clocks - HLC)**
   - Combine physical and logical time
   - Monotonically increasing even if clock goes backwards

### 2. Machine ID Assignment

**Problem**: Need unique machine IDs across all servers

**Solutions**:

1. **ZooKeeper/etcd**
   ```
   Server starts → Request machine ID from ZooKeeper
   → ZooKeeper assigns next available ID
   → Server registers with heartbeat
   → If server dies, ID becomes available after timeout
   ```

2. **Configuration File**
   - Manually assign machine IDs
   - Simple but error-prone
   - Good for small deployments

3. **Database-based Registry**
   - Store machine assignments in database
   - Use locking to prevent conflicts

### 3. Sequence Exhaustion

**Problem**: What if we generate 4096 IDs in same millisecond?

**Solutions**:
1. **Wait for next millisecond**
   ```java
   if (sequence == 0) {
       // Sequence wrapped around
       timestamp = waitNextMillis(lastTimestamp);
   }
   ```

2. **Increase sequence bits**
   - Trade off machine ID bits for sequence bits
   - Example: 8-bit machine ID + 14-bit sequence = 16,384 IDs/ms

### 4. High Availability

**Strategies**:
1. **Multi-Datacenter Deployment**
   - Deploy across multiple regions
   - Each datacenter has unique ID range

2. **Redundancy**
   - Run multiple ID servers
   - Load balance across servers
   - Any server can generate IDs

3. **Failover**
   - If server dies, reassign machine ID
   - ZooKeeper handles automatic failover

### 5. Performance Optimizations

1. **Batch ID Generation**
   ```java
   public List<Long> generateBatch(int count) {
       List<Long> ids = new ArrayList<>(count);
       synchronized(this) {
           for (int i = 0; i < count; i++) {
               ids.add(generateId());
           }
       }
       return ids;
   }
   ```

2. **Pre-generate IDs**
   - Generate IDs in background
   - Store in memory buffer
   - Serve from buffer (very fast)
   - Trade-off: Some IDs wasted if server crashes

3. **Lock-free Implementation**
   - Use atomic operations (CAS - Compare-And-Swap)
   - Avoid synchronized blocks
   - Higher throughput

---

## Edge Cases & Failure Scenarios

### 1. Clock Drift
**Scenario**: Server clock moves forward then backwards
```
T=1000ms → Generate ID with timestamp 1000
Clock jumps to T=2000ms → Generate ID with timestamp 2000
Clock corrects to T=1500ms → Collision risk!
```
**Solution**: Reject requests or use last known timestamp

### 2. Machine ID Collision
**Scenario**: Two servers get same machine ID
**Prevention**:
- Use ZooKeeper/etcd for coordination
- Implement health checks
- Automatic deregistration on heartbeat failure

### 3. Network Partition
**Scenario**: Server loses connection to coordination service
**Solution**:
- Use lease-based machine IDs (expire after N seconds)
- Server must renew lease periodically
- If lease expires, stop generating IDs

### 4. Sequence Overflow
**Scenario**: Generate > 4096 IDs in same millisecond
**Solution**:
- Wait for next millisecond (adds latency)
- Or increase sequence bits (reduce machine ID bits)

### 5. Data Center Failure
**Scenario**: Entire datacenter goes down
**Solution**:
- Route traffic to other datacenters
- Each datacenter has unique ID range
- No ID conflicts

### 6. Duplicate IDs
**Scenario**: Configuration error leads to duplicate machine IDs
**Detection**:
- Log generated IDs (sample)
- Run collision detection job
- Alert on duplicates

---

## Monitoring and Metrics

### Key Metrics to Track

1. **ID Generation Rate**
   ```
   - IDs per second per server
   - IDs per second per datacenter
   - Peak vs average throughput
   ```

2. **Latency Metrics**
   ```
   - p50, p95, p99 latency
   - Should be < 10ms
   ```

3. **Clock Metrics**
   ```
   - Clock drift from NTP server
   - Clock backward events
   - Time to sync
   ```

4. **Sequence Metrics**
   ```
   - Sequence exhaustion events
   - Time spent waiting for next millisecond
   ```

5. **Machine Health**
   ```
   - Active machine count
   - Machine registration/deregistration events
   - Heartbeat failures
   ```

### Alerts
- Clock drift > 100ms
- Clock backward event
- Machine ID collision detected
- Sequence exhaustion rate > 1%
- Server response time > 50ms

---

## Interview Tips

### Clarifying Questions to Ask

1. **Scale Requirements**
   - How many IDs per second?
   - Peak vs average load?
   - Geographic distribution?

2. **ID Characteristics**
   - Must IDs be sortable?
   - Numeric only or alphanumeric?
   - 64-bit constraint or flexible?

3. **Availability Requirements**
   - Acceptable downtime?
   - Single region or multi-region?
   - Disaster recovery needs?

4. **Latency Requirements**
   - Acceptable latency for ID generation?
   - Batch generation needed?

### Key Points to Cover

1. **Start with Simple Approach**
   - Begin with database auto-increment
   - Explain limitations
   - Build up to distributed solution

2. **Explain Trade-offs**
   - UUID vs Snowflake
   - Coordination vs independence
   - Latency vs consistency

3. **Address Clock Issues**
   - Show awareness of clock drift
   - Explain NTP synchronization
   - Handle clock backwards

4. **Show Scalability Understanding**
   - Horizontal scaling
   - Multi-datacenter deployment
   - No coordination overhead

### Common Mistakes to Avoid

❌ **Using random numbers without collision handling**
❌ **Not considering clock synchronization**
❌ **Single point of failure (single database)**
❌ **Not handling sequence exhaustion**
❌ **Ignoring network partition scenarios**

### Bonus Points

✅ **Mention real-world implementations** (Twitter Snowflake, Instagram)
✅ **Discuss monitoring and alerting**
✅ **Show code examples**
✅ **Cover edge cases proactively**
✅ **Explain bit allocation trade-offs**

---

## Summary

### Recommended Approach: Twitter Snowflake

**Why?**
- ✅ Meets 64-bit requirement
- ✅ Sortable by timestamp
- ✅ Highly scalable (no coordination)
- ✅ Low latency (< 1ms)
- ✅ Battle-tested at massive scale

**Architecture**:
```
ID = [1-bit][41-bit Timestamp][10-bit Machine][12-bit Sequence]
```

**Key Design Decisions**:
1. Use NTP for clock synchronization
2. ZooKeeper for machine ID assignment
3. Handle clock backwards gracefully
4. Deploy across multiple datacenters
5. Monitor clock drift and sequence exhaustion

**Throughput**:
- Per machine: 4,096,000 IDs/second (theoretical)
- Per machine: ~1,000,000 IDs/second (practical)
- 1024 machines: ~1 billion IDs/second

This design provides a robust, scalable, and production-ready solution for generating unique IDs in a distributed system.

---

## References
- Twitter Snowflake: https://blog.twitter.com/engineering/en_us/a/2010/announcing-snowflake
- Instagram IDs: https://instagram-engineering.com/sharding-ids-at-instagram-1cf5a71e5a5c
- MongoDB ObjectID: https://docs.mongodb.com/manual/reference/method/ObjectId/
- UUID: https://datatracker.ietf.org/doc/html/rfc4122
