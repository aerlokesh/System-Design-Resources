# Write Scaling Architecture: System Design Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Write Scaling Fundamentals](#write-scaling-fundamentals)
3. [Architecture Patterns](#architecture-patterns)
4. [Asynchronous Write Patterns](#asynchronous-write-patterns)
5. [Database Write Optimization](#database-write-optimization)
6. [Event-Driven Architecture](#event-driven-architecture)
7. [Conflict Resolution & Consistency](#conflict-resolution--consistency)
8. [Trade-offs & Decision Framework](#trade-offs--decision-framework)
9. [Failure Modes & Resilience](#failure-modes--resilience)
10. [Real-World Architectures](#real-world-architectures)
11. [Interview Strategy](#interview-strategy)

---

## Introduction

Write scaling is fundamentally harder than read scaling. Writes require coordination, consistency guarantees, and careful handling to maintain data integrity. Unlike reads, writes cannot simply be cached away.

**Core Challenges**:
- **Coordination Overhead**: Multiple systems must agree
- **Consistency Requirements**: Data must be correct
- **Sequential Bottlenecks**: Writes often serialize
- **No Caching**: Writes must hit persistent storage
- **Durability**: Must survive failures

**Why Write Scaling is Hard**:
```
Write Path Complexity:
- Validate input
- Acquire locks
- Update multiple indexes
- Replicate to replicas
- Wait for acknowledgments
- Commit transaction
- Invalidate caches
- Return success

vs.

Read Path Simplicity:
- Check cache
- Return if hit
- Query database if miss
```

---

## Write Scaling Fundamentals

### The Write Bottleneck Problem

**Single Master Bottleneck**:
```
All Writes Flow:
┌──────────────────────────────────────┐
│         All Write Traffic            │
└────────────┬─────────────────────────┘
             │
             ▼
    ┌────────────────┐
    │  Single Master │
    │   Database     │
    │  - CPU Limited │
    │  - I/O Limited │
    │  - Lock Cont.  │
    └────────────────┘

Result:
- Maximum ~10,000 writes/sec
- Single point of failure
- No horizontal scaling
- Increasing latency under load
```

**The Write Path**:
```
Time Breakdown (100ms total):
┌──────────────────────────────────────┐
│ 1. Validation          : 5ms         │
│ 2. Lock Acquisition    : 10ms        │
│ 3. Disk Write (WAL)    : 20ms        │
│ 4. Index Updates       : 15ms        │
│ 5. Replication         : 30ms        │
│ 6. Commit              : 10ms        │
│ 7. Cache Invalidation  : 10ms        │
└──────────────────────────────────────┘

Critical Path:
- Cannot skip steps
- Sequential dependencies
- Each step adds latency
```

### Scalability Targets

| Scale | WPS (Writes/Sec) | Strategy | Complexity |
|-------|------------------|----------|------------|
| **Small** | <1K | Single database + WAL | Low |
| **Medium** | 1K-10K | Async processing + batching | Medium |
| **Large** | 10K-100K | Sharding + message queues | High |
| **Massive** | 100K-1M+ | Multi-master + CQRS + event sourcing | Very High |

---

## Architecture Patterns

### Pattern 1: Write-Ahead Logging (WAL)

**Concept**: Log changes before applying them

```
Write Flow with WAL:
┌──────────────────────────────────────┐
│         Client Write Request         │
└────────────┬─────────────────────────┘
             │
             ▼
┌────────────────────────────────────────┐
│  1. Append to Write-Ahead Log          │
│     - Sequential disk write (fast)     │
│     - Guaranteed durability            │
│     - No random I/O                    │
│     Time: 10-20ms                      │
└────────────┬───────────────────────────┘
             │
             ▼
┌────────────────────────────────────────┐
│  2. Acknowledge to Client              │
│     Time: 25ms total                   │
└────────────┬───────────────────────────┘
             │
             ▼
┌────────────────────────────────────────┐
│  3. Apply to Database (Async)          │
│     - Background process               │
│     - Can batch multiple writes        │
│     - Optimize for throughput          │
└────────────────────────────────────────┘
```

**Benefits**:
```
Performance:
- 5-10x faster than direct writes
- Sequential I/O (100K+ writes/sec)
- No waiting for complex operations

Durability:
- Changes persisted immediately
- Can replay log after crash
- No data loss on failure

Recovery:
- WAL survives crashes
- Replay from last checkpoint
- Automatic recovery
```

**Real-World Usage**:
```
PostgreSQL: pg_wal directory
MySQL: InnoDB redo log  
Redis: AOF (Append-Only File)
Kafka: Commit log
Cassandra: Commit log
```

### Pattern 2: Batch Processing

**Concept**: Group multiple writes into single operation

```
Without Batching:
┌────────────────────────────────────┐
│ 1000 Individual Writes             │
│ = 1000 network round trips         │
│ = 1000 database transactions       │
│ Total Time: 7 seconds              │
└────────────────────────────────────┘

With Batching:
┌────────────────────────────────────┐
│ Batch 1: 100 writes → 1 operation  │
│ Batch 2: 100 writes → 1 operation  │
│ ...                                │
│ Batch 10: 100 writes → 1 operation │
│ Total Time: 700ms (10x faster)     │
└────────────────────────────────────┘
```

**Architecture**:
```
┌──────────────────────────────────────┐
│        Write Buffer Layer            │
│                                      │
│  Incoming → Buffer → Batch → DB      │
│  Writes     (100ms)  (Size=100)     │
└──────────────────────────────────────┘

Flush Triggers:
1. Time-based: Every 100ms
2. Size-based: When batch reaches 100 items
3. Memory-based: When buffer reaches threshold
4. Manual: On explicit flush command
```

**Trade-offs**:
```
Batch Size Analysis:

Small Batches (10-50):
+ Lower latency per write (100ms)
+ Less memory usage
+ Faster failure recovery
- More overhead
- Lower throughput

Large Batches (1000-5000):
+ Higher throughput
+ Less network overhead
+ Better compression
- Higher latency (500ms+)
- More memory usage
- Larger rollback on failure

Optimal: 100-500 items or 100-200ms window
```

### Pattern 3: Asynchronous Write Processing

**Architecture**:
```
┌──────────────────────────────────────┐
│          Synchronous Path            │
│  (Critical for user response)        │
└────────────┬─────────────────────────┘
             │
    ┌────────▼────────┐
    │  Write to DB    │  (50ms)
    │  (Critical Data)│
    └────────┬────────┘
             │
    ┌────────▼────────┐
    │ Enqueue Tasks   │  (5ms)
    │ (Non-Critical)  │
    └────────┬────────┘
             │
    ┌────────▼────────┐
    │ Return Success  │  (55ms total)
    └─────────────────┘

┌──────────────────────────────────────┐
│         Asynchronous Path            │
│  (Processed by workers)              │
└────────────┬─────────────────────────┘
             │
    ┌────────▼────────┐
    │  Message Queue  │
    │  - Kafka        │
    │  - RabbitMQ     │
    │  - SQS          │
    └────────┬────────┘
             │
    ┌────────▼────────┐
    │ Worker Processes│
    │ - Update search │
    │ - Send emails   │
    │ - Update cache  │
    │ - Analytics     │
    └─────────────────┘
```

**Use Cases**:
```
Synchronous (Must Complete):
- Financial transactions
- User authentication
- Payment processing
- Inventory deduction
- Critical data updates

Asynchronous (Can Delay):
- Email notifications
- Search index updates
- Analytics processing
- Cache warming
- Report generation
- Recommendation updates
```

### Pattern 4: Database Sharding for Writes

**Hash-Based Sharding**:
```
┌──────────────────────────────────────┐
│       Application Router             │
│  Shard = Hash(key) % num_shards      │
└────────────┬─────────────────────────┘
             │
    ┌────────┴────────┬────────┬────────┐
    │                 │        │        │
    ▼                 ▼        ▼        ▼
┌────────┐      ┌────────┐┌────────┐┌────────┐
│Shard 1 │      │Shard 2 ││Shard 3 ││Shard 4 │
│10K WPS │      │10K WPS ││10K WPS ││10K WPS │
└────────┘      └────────┘└────────┘└────────┘

Total Capacity: 40K writes/sec
Linear Scaling: Add more shards
```

**Geographic Sharding**:
```
Global Write Distribution:
┌──────────────────────────────────────┐
│      Geographic Router               │
│  Route by: User location, IP, etc    │
└────────────┬─────────────────────────┘
             │
    ┌────────┴────────┬────────┬────────┐
    │                 │        │        │
    ▼                 ▼        ▼        ▼
┌────────┐      ┌────────┐┌────────┐┌────────┐
│US-EAST │      │US-WEST ││ EU     ││ ASIA   │
│Shard   │      │Shard   ││ Shard  ││ Shard  │
│        │      │        ││        ││        │
│Write   │      │Write   ││ Write  ││ Write  │
│Local   │      │Local   ││ Local  ││ Local  │
└────────┘      └────────┘└────────┘└────────┘

Benefits:
- Low latency (local writes)
- Data residency compliance
- Natural partitioning
- Disaster recovery

Challenges:
- Cross-shard transactions
- Global consistency
- Data replication
```

**Consistent Hashing** (Better Resharding):
```
Hash Ring Architecture:
┌──────────────────────────────────────┐
│           Virtual Nodes              │
│                                      │
│  Node 1: Positions 0, 100, 200      │
│  Node 2: Positions 50, 150, 250     │
│  Node 3: Positions 75, 175, 275     │
│                                      │
│  Write → Hash key → Find position    │
│  → Clockwise to next node            │
└──────────────────────────────────────┘

Adding Node:
- Only ~25% keys redistributed
- No downtime required
- Gradual migration

Removing Node:
- Keys move to next node
- ~25% affected
- Automatic rebalancing
```

---

## Asynchronous Write Patterns

### Message Queue Architecture

**Basic Queue Pattern**:
```
┌──────────────────────────────────────┐
│         Producer (API)               │
│  - Fast response (10-50ms)           │
│  - Enqueue message                   │
│  - Return immediately                │
└────────────┬─────────────────────────┘
             │
             ▼
┌──────────────────────────────────────┐
│       Message Queue                  │
│  - Durable storage                   │
│  - Ordering guarantees               │
│  - Retry logic                       │
│  - Dead letter queue                 │
└────────────┬─────────────────────────┘
             │
             ▼
┌──────────────────────────────────────┐
│         Consumers (Workers)          │
│  - Process asynchronously            │
│  - Write to database                 │
│  - Update indexes                    │
│  - Handle errors                     │
└──────────────────────────────────────┘
```

**Queue Selection Matrix**:

| Queue Type | Throughput | Ordering | Durability | Use Case |
|------------|------------|----------|------------|----------|
| **Redis** | Very High (1M+/s) | No | Optional | Simple tasks, high speed |
| **RabbitMQ** | High (50K/s) | Yes | Yes | Reliable messaging |
| **Kafka** | Very High (1M+/s) | Yes (per partition) | Yes | Event streams |
| **AWS SQS** | High (varies) | No (FIFO: Yes) | Yes | Managed, serverless |
| **Google Pub/Sub** | Very High | No | Yes | GCP ecosystem |

### Kafka Architecture for Writes

**Topic Partitioning**:
```
Kafka Topic: "user_events"
┌──────────────────────────────────────┐
│        Partition 0 (Leader)          │
│  - User IDs: 0, 4, 8, 12...          │
│  - Replicas: Broker 2, 3             │
└──────────────────────────────────────┘
│        Partition 1 (Leader)          │
│  - User IDs: 1, 5, 9, 13...          │
│  - Replicas: Broker 3, 1             │
└──────────────────────────────────────┘
│        Partition 2 (Leader)          │
│  - User IDs: 2, 6, 10, 14...         │
│  - Replicas: Broker 1, 2             │
└──────────────────────────────────────┘
│        Partition 3 (Leader)          │
│  - User IDs: 3, 7, 11, 15...         │
│  - Replicas: Broker 2, 3             │
└──────────────────────────────────────┘

Benefits:
- Parallel processing
- Ordering per partition
- Horizontal scaling
- Fault tolerance
```

**Write Durability Levels**:
```
acks=0 (Fire and forget):
- No acknowledgment
- Highest throughput
- Possible data loss
- Use: Non-critical metrics

acks=1 (Leader only):
- Leader acknowledges
- Good throughput
- Minimal data loss risk
- Use: User activity logs

acks=all (All replicas):
- All in-sync replicas ACK
- Lower throughput
- No data loss
- Use: Financial transactions
```

### Backpressure Handling

**Problem**:
```
Scenario: Consumers too slow
┌──────────────────────────────────────┐
│  Producers: 10,000 writes/sec        │
│  Consumers: 5,000 writes/sec         │
│  Queue Growth: 5,000 msgs/sec        │
└──────────────────────────────────────┘

After 1 hour:
- Queue size: 18 million messages
- Memory exhaustion
- System crash
```

**Solutions**:
```
1. Rate Limiting:
   ┌──────────────────────────────────┐
   │  Limit producers to queue        │
   │  processing capacity             │
   │  - Token bucket                  │
   │  - Leaky bucket                  │
   │  - Fixed window                  │
   └──────────────────────────────────┘

2. Auto-Scaling Consumers:
   ┌──────────────────────────────────┐
   │  Monitor queue depth             │
   │  Scale consumers automatically   │
   │  - Queue > 1000: Add workers     │
   │  - Queue < 100: Remove workers   │
   └──────────────────────────────────┘

3. Circuit Breaker:
   ┌──────────────────────────────────┐
   │  Detect overload                 │
   │  Reject new writes temporarily   │
   │  Return 503 Service Unavailable  │
   │  Recover gradually               │
   └──────────────────────────────────┘

4. Priority Queues:
   ┌──────────────────────────────────┐
   │  High Priority → Process first   │
   │  Low Priority → Can be delayed   │
   │  Drop low priority under load    │
   └──────────────────────────────────┘
```

---

## Database Write Optimization

### Connection Pooling

**Problem Without Pooling**:
```
Per-Request Connection:
┌──────────────────────────────────────┐
│ Request arrives                      │
│ → Open TCP connection (50ms)         │
│ → Authenticate (20ms)                │
│ → Execute query (30ms)               │
│ → Close connection (10ms)            │
│ Total: 110ms                         │
└──────────────────────────────────────┘

1000 requests = 110 seconds!
```

**With Connection Pool**:
```
Connection Reuse:
┌──────────────────────────────────────┐
│ Pool maintains N connections         │
│ Request arrives                      │
│ → Get connection from pool (1ms)     │
│ → Execute query (30ms)               │
│ → Return connection to pool (1ms)    │
│ Total: 32ms                          │
└──────────────────────────────────────┘

1000 requests = 32 seconds (3.4x faster!)
```

**Pool Sizing**:
```
Formula: connections = ((core_count × 2) + effective_spindle_count)

Example:
- 8 CPU cores
- 4 disk spindles
- Connections = (8 × 2) + 4 = 20

Considerations:
- Too few: Requests wait for connections
- Too many: Context switching overhead
- Monitor: Pool exhaustion rate
```

### Index Optimization for Writes

**The Write Penalty**:
```
Table with 5 Indexes:
┌──────────────────────────────────────┐
│ 1 INSERT operation                   │
│ = 1 table write                      │
│ + 5 index updates                    │
│ = 6 total writes                     │
│ = 6x write amplification             │
└──────────────────────────────────────┘

Impact:
- 1000 writes/sec becomes 6000 writes/sec
- Increased I/O
- Higher latency
- More lock contention
```

**Optimization Strategies**:
```
1. Index Audit:
   ┌──────────────────────────────────┐
   │ Find unused indexes              │
   │ DROP INDEX if not used           │
   │ Monitor query performance        │
   └──────────────────────────────────┘

2. Partial Indexes:
   ┌──────────────────────────────────┐
   │ Index only subset of data        │
   │ Example: WHERE status = 'active' │
   │ Smaller index = faster updates   │
   └──────────────────────────────────┘

3. Defer Index Creation:
   ┌──────────────────────────────────┐
   │ For bulk loads:                  │
   │ 1. Drop indexes                  │
   │ 2. Load data                     │
   │ 3. Recreate indexes              │
   │ = Much faster for large loads    │
   └──────────────────────────────────┘
```

### Bulk Loading Strategies

**Standard vs Optimized**:
```
Standard INSERT (Slow):
┌──────────────────────────────────────┐
│ 1M rows                              │
│ = 1M individual INSERT statements    │
│ = 1M transactions                    │
│ = 1M index updates                   │
│ Time: 30 minutes                     │
└──────────────────────────────────────┘

Optimized COPY (Fast):
┌──────────────────────────────────────┐
│ 1. Disable triggers/constraints      │
│ 2. Drop indexes                      │
│ 3. Use COPY/LOAD DATA                │
│ 4. Recreate indexes (parallel)       │
│ 5. Re-enable constraints             │
│ Time: 3 minutes (10x faster!)        │
└──────────────────────────────────────┘
```

---

## Event-Driven Architecture

### Event Sourcing Pattern

**Concept**: Store all changes as immutable events

```
Traditional (State-Based):
┌──────────────────────────────────────┐
│ Account Table                        │
│ ID | Balance | Updated              │
│ 1  | 1000    | 2024-11-20           │
└──────────────────────────────────────┘

Problem: History lost on each update
```

```
Event Sourcing (Event-Based):
┌──────────────────────────────────────┐
│ Account Events                       │
│ ID | Account | Event       | Amount  │
│ 1  | 1       | Created     | 1000    │
│ 2  | 1       | Withdrawal  | -100    │
│ 3  | 1       | Deposit     | 50      │
└──────────────────────────────────────┘

Current Balance: SUM(amounts) = 950
Benefits: Complete audit trail
```

**Architecture**:
```
┌──────────────────────────────────────┐
│         Command Handler              │
│  - Validates command                 │
│  - Creates event                     │
│  - Appends to event store            │
└────────────┬─────────────────────────┘
             │
             ▼
┌──────────────────────────────────────┐
│          Event Store                 │
│  - Append-only log                   │
│  - Immutable events                  │
│  - Sequence numbers                  │
│  - Very fast writes (WAL)            │
└────────────┬─────────────────────────┘
             │
             ▼
┌──────────────────────────────────────┐
│       Event Processors               │
│  - Rebuild current state             │
│  - Update read models                │
│  - Trigger side effects              │
└──────────────────────────────────────┘
```

**Benefits & Challenges**:
```
Benefits:
+ Complete audit trail
+ Time travel queries
+ Event replay capability
+ Natural fit for async processing
+ Easy to add new projections

Challenges:
- Query complexity (must rebuild state)
- Storage growth (all events kept)
- Event schema evolution
- Snapshot management
```

### CQRS (Command Query Responsibility Segregation)

**Architecture**:
```
┌──────────────────────────────────────┐
│          API Gateway                 │
└────────────┬───────────────┬─────────┘
             │               │
      Writes │               │ Reads
             │               │
             ▼               ▼
┌────────────────┐   ┌────────────────┐
│  Write Model   │   │   Read Model   │
│  (Commands)    │   │   (Queries)    │
│                │   │                │
│  PostgreSQL    │   │   MongoDB      │
│  Normalized    │   │   Denormalized │
│  ACID          │   │   Eventually   │
│  Consistent    │   │   Consistent   │
└───────┬────────┘   └────────────────┘
        │
        │ Events
        ▼
   ┌─────────────┐
   │ Event Bus   │
   │ (Kafka)     │
   └─────┬───────┘
         │
         │ Updates
         ▼
   ┌─────────────┐
   │ Read Model  │
   └─────────────┘
```

**When to Use CQRS**:
```
Good Fit:
- Read and write patterns very different
- Need multiple read models (API, analytics, search)
- Can accept eventual consistency
- High read/write load needs independent scaling
- Complex domain logic

Not Recommended:
- Simple CRUD applications
- Strong consistency required everywhere
- Team unfamiliar with pattern
- Small scale (<10K QPS)
```

**Implementation Patterns**:
```
Pattern 1: Shared Database
┌──────────────────────────────────────┐
│  Same database, different schemas   │
│  Write schema: Normalized            │
│  Read schema: Denormalized views     │
│  Sync: Database triggers             │
└──────────────────────────────────────┘

Pattern 2: Separate Databases
┌──────────────────────────────────────┐
│  Write DB: PostgreSQL                │
│  Read DB: MongoDB                    │
│  Sync: Event stream (Kafka)          │
│  Lag: 100-500ms                      │
└──────────────────────────────────────┘

Pattern 3: Multiple Read Models
┌──────────────────────────────────────┐
│  Write DB: PostgreSQL                │
│  Read DB 1: MongoDB (API)            │
│  Read DB 2: Elasticsearch (Search)   │
│  Read DB 3: Redis (Cache)            │
│  Sync: Multiple event consumers      │
└──────────────────────────────────────┘
```

---

## Conflict Resolution & Consistency

### Multi-Master Replication

**Challenge**: Multiple databases accepting writes

```
Conflict Scenario:
┌────────────────────────────────────┐
│ US Datacenter (Master 1)           │
│ User A: balance=1000 → -100 = 900  │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│ EU Datacenter (Master 2)           │
│ User A: balance=1000 → +50 = 1050  │
└────────────────────────────────────┘

After Replication:
Master 1 sees: 900, then gets 1050
Master 2 sees: 1050, then gets 900

Which is correct? CONFLICT!
```

### Conflict Resolution Strategies

**1. Last Write Wins (LWW)**:
```
Simple Approach:
┌──────────────────────────────────────┐
│ Compare timestamps                   │
│ Newest write wins                    │
│ Discard older write                  │
└──────────────────────────────────────┘

Example:
Write A: balance=900, time=T1
Write B: balance=1050, time=T2
Result: balance=1050 (T2 > T1)

Pros:
+ Simple to implement
+ Always resolves
+ Works everywhere

Cons:
- Data loss possible
- Not semantically correct
- Clock sync issues
```

**2. Version Vectors**:
```
Track Versions Per Node:
┌──────────────────────────────────────┐
│ Node A: {A:1, B:0}  →  value=900    │
│ Node B: {A:0, B:1}  →  value=1050   │
└──────────────────────────────────────┘

Conflict Detection:
Neither version is ancestor of other
→ Manual resolution required

Resolution Options:
1. Prompt user to choose
2. Merge based on business logic
3. Keep both (multi-value)
```

**3. CRDTs (Conflict-Free Replicated Data Types)**:
```
Types of CRDTs:
┌──────────────────────────────────────┐
│ G-Counter (Grow-only)                │
│ - Like counts, view counts           │
│ - Can only increase                  │
│ - Merge: Take maximum                │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│ PN-Counter (Positive-Negative)       │
│ - Inventory, balance                 │
│ - Can increase/decrease              │
│ - Merge: Sum all increments/decrements │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│ LWW-Register                         │
│ - Status fields, metadata            │
│ - Last write wins with timestamp     │
│ - Merge: Keep newest value           │
└──────────────────────────────────────┘

Benefits:
+ Automatic conflict resolution
+ No coordination needed
+ Always converge
+ Highly available
```

### Consistency Models

**Consistency Spectrum**:
```
┌──────────────────────────────────────┐
│ Strong Consistency                   │
│ - All reads see latest write         │
│ - High latency (100-200ms)           │
│ - Lower availability                 │
│ - Use: Banking, payments             │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│ Causal Consistency                   │
│ - Related operations ordered         │
│ - Medium latency (50-100ms)          │
│ - Good availability                  │
│ - Use: Chat, collaborative editing   │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│ Eventual Consistency                 │
│ - Replicas converge eventually       │
│ - Low latency (10-50ms)              │
│ - High availability                  │
│ - Use: Social media, content delivery│
└──────────────────────────────────────┘
```

**CAP Theorem Trade-offs**:
```
Pick Two of Three:
┌────────────────────────────────────┐
│ Consistency (C)                    │
│ All nodes see same data            │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│ Availability (A)                   │
│ Every request gets response        │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│ Partition Tolerance (P)            │
│ System continues despite partitions│
└────────────────────────────────────┘

Common Choices:
- CP: Strong consistency, might be unavailable
- AP: Always available, eventually consistent
- CA: Not possible in distributed systems
```

---

## Trade-offs & Decision Framework

### Technology Selection Matrix

| Strategy | Throughput | Latency | Consistency | Complexity | Cost |
|----------|------------|---------|-------------|------------|------|
| **Vertical Scaling** | Medium | Low | Strong | Low | High |
| **WAL** | High | Very Low | Strong | Medium | Low |
| **Batching** | Very High | Medium | Strong | Low | Low |
| **Async Writes** | High | Very Low | Eventual | Medium | Medium |
| **Sharding** | Very High | Low | Strong* | High | High |
| **Event Sourcing** | High | Low | Strong | High | Medium |
| **CQRS** | High | Low | Eventual | Very High | High |
| **Multi-Master** | Very High | Low | Eventual | Very High | High |

*Strong within shard, Eventual across shards

### Decision Tree

```
Start: Need to scale writes?
│
├─ Writes < 1,000/sec?
│  └─ YES → Vertical Scaling + WAL
│
├─ Can accept eventual consistency?
│  │
│  ├─ YES → Async Processing + Message Queue
│  │
│  └─ NO → Strong consistency required
│     ├─ Single region?
│     │  └─ YES → Vertical + WAL + Batching
│     │
│     └─ Multi-region?
│        └─ YES → Sharding + Synchronous Replication
│
├─ Write-heavy workload (>80% writes)?
│  └─ YES → Event Sourcing + CQRS
│
├─ Data > 1TB?
│  └─ YES → Sharding required
│
└─ Simple optimization first?
   └─ YES → Start with:
      1. Connection pooling
      2. Batch writes
      3. Write-ahead logging
      4. Then scale as needed
```

### Cost-Benefit Analysis

**Small Application** (< 1K WPS):
```
Investment: $500/month
- Single database (optimized): $300
- Connection pooling: Free
- WAL enabled: Free
- Monitoring: $200

Benefits:
- Handle 1K writes/sec
- <100ms latency
- 99.9% availability
- Strong consistency

ROI: Excellent (minimal investment)
```

**Medium Application** (1K-10K WPS):
```
Investment: $5,000/month
- Database cluster: $2,000
- Message queue (Kafka): $1,500
- Worker processes: $1,000
- Monitoring & tools: $500

Benefits:
- Handle 10K writes/sec
- <50ms API latency
- 99.95% availability
- Async processing

ROI: Good (necessary for scale)
```

**Large Application** (10K-100K WPS):
```
Investment: $50,000/month
- Sharded database (10 shards): $20,000
- Kafka clusters: $15,000
- Worker fleet: $10,000
- Monitoring & operations: $5,000

Benefits:
- Handle 100K writes/sec
- <50ms latency
- 99.99% availability
- Global distribution

ROI: Required (cost of doing business)
```

---

## Failure Modes & Resilience

### Message Queue Failure

**Scenario: Queue Overflow**:
```
Problem:
┌──────────────────────────────────────┐
│  Producers: 10K writes/sec           │
│  Consumers: 5K writes/sec            │
│  Queue Growth: 5K msgs/sec           │
└──────────────────────────────────────┘

Timeline:
T+0: Normal operation
T+1h: Queue = 18M messages
T+2h: Memory exhaustion
T+3h: System crash

Impact:
- Data loss
- Service outage
- Recovery time: hours
```

**Mitigation**:
```
Solution 1: Queue Depth Monitoring
┌──────────────────────────────────────┐
│  Monitor: Queue depth                │
│  Alert: Depth > 1000 messages        │
│  Auto-scale: Add consumer workers    │
│  Circuit breaker: Reject at threshold│
└──────────────────────────────────────┘

Solution 2: Dead Letter Queue
┌──────────────────────────────────────┐
│  Failed messages → DLQ               │
│  Retry logic with backoff            │
│  Manual inspection of failures       │
│  Replay after fixing                 │
└──────────────────────────────────────┘

Solution 3: Queue Partitioning
┌──────────────────────────────────────┐
│  Multiple independent queues         │
│  Partition by: user_id, region, etc. │
│  Isolated failures                   │
│  Better parallelization              │
└──────────────────────────────────────┘
```

### Shard Failure Scenarios

**Scenario: Shard Becomes Unavailable**:
```
Problem:
┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
│Shard 1 │  │Shard 2 │  │Shard 3 │  │Shard 4 │
│ Active │  │  FAIL  │  │ Active │  │ Active │
└────────┘  └────────┘  └────────┘  └────────┘
     │           X           │           │
     └───────────┴───────────┴───────────┘
             25% of writes LOST
```

**Mitigation**:
```
Solution 1: Replica Promotion
┌──────────────────────────────────────┐
│  Shard 2 Master FAILS                │
│  → Detect failure (10s)              │
│  → Promote replica to master (30s)   │
│  → Update routing (10s)              │
│  → Resume writes (total: 50s)        │
└──────────────────────────────────────┘

Solution 2: Write Buffering
┌──────────────────────────────────────┐
│  Buffer writes for failed shard      │
│  → Store in local queue/cache        │
│  → Retry when shard recovered        │
│  → No data loss                      │
│  → Higher latency during recovery    │
└──────────────────────────────────────┘

Solution 3: Cross-Shard Replication
┌──────────────────────────────────────┐
│  Replicate data across shards        │
│  → Shard 2 data also in Shard 3      │
│  → Automatic failover                │
│  → Higher storage cost               │
│  → Better availability               │
└──────────────────────────────────────┘
```

### Transaction Failures

**Scenario: Distributed Transaction Fails**:
```
Problem: Two-Phase Commit Failure
┌──────────────────────────────────────┐
│  Phase 1: Prepare                    │
│  → Shard A: OK                       │
│  → Shard B: OK                       │
│  → Shard C: TIMEOUT                  │
│                                      │
│  Phase 2: Abort all shards           │
│  → Rollback Shard A                  │
│  → Rollback Shard B                  │
│  → Shard C unreachable               │
│                                      │
│  Result: Inconsistent state          │
└──────────────────────────────────────┘
```

**Mitigation**:
```
Solution 1: Saga Pattern
┌──────────────────────────────────────┐
│  Break into individual transactions  │
│  Each with compensating action       │
│                                      │
│  Step 1: Reserve inventory → OK      │
│  Step 2: Charge payment → FAIL       │
│  Step 3: Compensate: Release inventory│
│                                      │
│  Result: Eventually consistent       │
└──────────────────────────────────────┘

Solution 2: Idempotent Operations
┌──────────────────────────────────────┐
│  Generate unique transaction ID      │
│  Store ID in each shard              │
│  Retry with same ID (no duplicates)  │
│  Can safely retry failures           │
└──────────────────────────────────────┘

Solution 3: Avoid Distributed Transactions
┌──────────────────────────────────────┐
│  Design to avoid cross-shard writes  │
│  Denormalize data if needed          │
│  Accept eventual consistency         │
│  Much simpler, more scalable         │
└──────────────────────────────────────┘
```

---

## Real-World Architectures

### Example 1: Twitter - Tweet Writes

**Challenge**: Handle 6,000 tweets/second

**Architecture**:
```
┌──────────────────────────────────────┐
│         API Gateway                  │
│  - Rate limiting per user            │
│  - DDoS protection                   │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│      Validation Layer                │
│  - Content moderation                │
│  - Spam detection                    │
│  - Character limit                   │
│  Time: 20ms                          │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│     Tweet Storage (Manhattan)        │
│  - Sharded by tweet_id               │
│  - Write to local shard              │
│  - Async replication                 │
│  Time: 30ms                          │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│      Kafka Event Stream              │
│  - Tweet created event               │
│  - Fan-out processing                │
│  Time: 5ms                           │
│  Total: 55ms response                │
└────────────┬─────────────────────────┘
             │
             ▼
┌──────────────────────────────────────┐
│      Background Workers              │
│  - Timeline fan-out                  │
│  - Search index update               │
│  - Analytics processing              │
│  - Notification delivery             │
└──────────────────────────────────────┘
```

**Key Decisions**:
- **Hybrid Fan-out**: Write for normal users, read for celebrities
- **Async Processing**: Everything except core write is async
- **Sharding**: By tweet_id for even distribution
- **Event-Driven**: Kafka for decoupling

**Scale Metrics**:
- **Peak**: 18,000 tweets/sec (during events)
- **Average**: 6,000 tweets/sec
- **Write Latency**: 55ms
- **Durability**: 3x replication

### Example 2: Uber - Location Updates

**Challenge**: 250,000 location updates/second

**Architecture**:
```
┌──────────────────────────────────────┐
│      Regional API Endpoints          │
│  - Driver app → Nearest endpoint     │
│  - Geo-routing                       │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│      Write Aggregation               │
│  - Batch 10 updates per driver       │
│  - Flush every 4 seconds             │
│  - Reduces DB load 10x               │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│   Cassandra (Time-Series)            │
│  - Partitioned by region + driver    │
│  - TTL: 24 hours (old data deleted)  │
│  - Append-only writes                │
│  - 25K writes/sec per shard          │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│      Redis Cache                     │
│  - Current driver locations          │
│  - 30-second TTL                     │
│  - Updated on each write             │
└──────────────────────────────────────┘
```

**Key Decisions**:
- **Batching**: 10x reduction in database load
- **TTL**: Old locations automatically purged
- **Geographic Sharding**: Low latency writes
- **Time-Series**: Append-only for speed

**Scale Metrics**:
- **Updates**: 250K/sec (1M drivers @ 4s interval)
- **Write Latency**: 20ms (batched)
- **Storage**: 24-hour window only
- **Geographic**: 10 regions globally

### Example 3: Facebook - Status Updates

**Challenge**: 500M status updates/day

**Architecture**:
```
┌──────────────────────────────────────┐
│      Edge Servers                    │
│  - Receive status update             │
│  - Nearest datacenter                │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│    MySQL Shard (by user_id)          │
│  - Write to user's shard             │
│  - Synchronous replication (2x)      │
│  - Strong consistency                │
│  Time: 50ms                          │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│         Kafka Stream                 │
│  - Status update event               │
│  - 100 partitions                    │
│  Time: 10ms                          │
└────────────┬─────────────────────────┘
             │
             ▼
┌──────────────────────────────────────┐
│      Worker Fleet (1000s)            │
│  - Fan-out to friends' feeds         │
│  - Update search index               │
│  - Send notifications                │
│  - Batch writes to Redis             │
└──────────────────────────────────────┘
```

**Key Decisions**:
- **Sharding**: By user_id for write distribution
- **Sync Replication**: Strong consistency for posts
- **Kafka**: Decouple write from fan-out
- **Batch Fan-out**: 100 updates per batch to Redis

**Scale Metrics**:
- **Average**: 5,800 updates/sec
- **Peak**: 17,400 updates/sec
- **Write Latency**: 60ms
- **Fan-out**: Async (1-5 seconds)

### Example 4: Discord - Message Writes

**Challenge**: 5M messages/second

**Architecture**:
```
┌──────────────────────────────────────┐
│      Gateway Servers                 │
│  - WebSocket connections             │
│  - Message validation                │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│      Cassandra Cluster               │
│  - Partitioned by channel_id         │
│  - Time-series design                │
│  - Append-only writes                │
│  - Replication factor: 3             │
│  Time: 15ms                          │
└────────────┬─────────────────────────┘
             │
┌────────────▼─────────────────────────┐
│      Message Queue                   │
│  - Kafka topics per channel          │
│  - Real-time delivery                │
│  Time: 5ms                           │
└────────────┬─────────────────────────┘
             │
             ▼
┌──────────────────────────────────────┐
│      Delivery Workers                │
│  - Push to online users              │
│  - Store for offline users           │
│  - Mobile push notifications         │
└──────────────────────────────────────┘
```

**Key Decisions**:
- **Cassandra**: Perfect for time-series append-only
- **Channel Partitioning**: Even distribution
- **No Updates**: Messages immutable
- **Async Delivery**: Fast acknowledgment

**Scale Metrics**:
- **Peak**: 5M messages/sec
- **Write Latency**: 20ms
- **Storage**: 100 Cassandra nodes
- **Retention**: Configurable per server

---

## Interview Strategy

### The 5-Phase Approach

**Phase 1: Requirements Clarification** (5 minutes)
```
Critical Questions:
1. Write Rate (QPS)?
   → Determines scale requirements

2. Data Size per Write?
   → Influences storage and network

3. Consistency Requirements?
   → Strong vs eventual

4. Acceptable Write Latency?
   → Sync vs async processing

5. Data Relationships?
   → Single vs distributed transactions

6. Geographic Distribution?
   → Multi-region considerations

7. Durability Requirements?
   → Replication factor

8. Budget Constraints?
   → Cost vs performance trade-offs
```

**Phase 2: Capacity Estimation** (5 minutes)
```
Calculate Scale:
- Active users
- Writes per user per day
- Total writes = users × writes/user
- WPS = Total / 86,400
- Peak WPS = Average × 3
- Data per write
- Storage growth rate

Example:
- 1M active users
- 20 writes/user/day
- Total: 20M writes/day
- Average: 231 WPS
- Peak: 700 WPS
- Plan for: 1,000 WPS (with headroom)
```

**Phase 3: High-Level Design** (10 minutes)
```
Components to Include:
1. Client Layer
   - Mobile/web applications

2. API Gateway
   - Rate limiting
   - Authentication
   - Request validation

3. Application Servers
   - Business logic
   - Connection pooling

4. Message Queue (if async)
   - Kafka, RabbitMQ, SQS
   - Decouple processing

5. Database Layer
   - Primary database
   - Replication strategy
   - Sharding if needed

6. Background Workers
   - Async processing
   - Batch operations

7. Monitoring
   - Write latency
   - Queue depth
   - Error rates
```

**Phase 4: Deep Dive** (15 minutes)
```
Focus Areas:
1. Write Path Optimization
   - WAL implementation?
   - Batching strategy?
   - Async processing?

2. Consistency Model
   - Strong or eventual?
   - Replication strategy?
   - Conflict resolution?

3. Scalability
   - Sharding approach?
   - Geographic distribution?
   - Linear scaling path?

4. Failure Handling
   - What if database fails?
   - What if queue overflows?
   - Recovery procedures?

5. Monitoring
   - Key metrics?
   - Alert thresholds?
   - Performance tracking?
```

**Phase 5: Trade-offs Discussion** (5 minutes)
```
Always Cover:
1. Latency vs Throughput
   - Sync writes: Higher latency, guaranteed
   - Async writes: Lower latency, eventual

2. Consistency vs Availability
   - Strong consistency: Lower availability
   - Eventual consistency: Higher availability

3. Cost vs Performance
   - More hardware: Better performance, higher cost
   - Optimization: Same hardware, better results

4. Complexity vs Simplicity
   - Complex solutions: Handle edge cases
   - Simple solutions: Easier to maintain
```

### Sample Interview Response

```
Interviewer: "Design a system to handle 10,000 writes per second"

You: "Let me clarify requirements first:

1. What's the consistency requirement?
   [Answer: Eventual is acceptable]

2. What's acceptable write latency?
   [Answer: <100ms for user-facing API]

3. What type of data?
   [Answer: User activity logs]

4. Geographic distribution?
   [Answer: Global users]

Based on these requirements, here's my design:

**Architecture:**

Layer 1 - API Gateway:
- Rate limiting (100 writes/sec per user)
- Authentication & validation
- Routes to nearest regional endpoint
- Time: 10ms

Layer 2 - Application Servers:
- Connection pooling (50 connections/server)
- Write to database (critical data)
- Enqueue to Kafka (non-critical processing)
- Time: 50ms
- Return success to user (60ms total)

Layer 3 - Database (PostgreSQL):
- Sharded by user_id (4 shards)
- Each shard: 2,500 writes/sec
- WAL enabled for fast writes
- Async replication (2 replicas per shard)
- Strong consistency within shard

Layer 4 - Message Queue (Kafka):
- 10 partitions (1,000 writes/sec each)
- Retention: 7 days
- Replication factor: 3
- Async processing

Layer 5 - Worker Fleet:
- 20 worker processes
- Process 500 writes/sec each
- Update search indexes
- Update analytics
- Generate notifications

**Capacity Math:**
- Total: 10K writes/sec
- Database: 10K writes/sec (sharded)
- Kafka: 10K messages/sec
- Workers: 10K processed/sec

**Failure Handling:**
- Database shard failure → Replica promotion (30s)
- Kafka partition failure → Producer retry
- Worker failure → Auto-scaling replacement
- Queue overflow → Circuit breaker + backpressure

**Trade-offs:**
+ Low latency (60ms API response)
+ High availability (replicated)
+ Scales horizontally
+ Global distribution

- Eventual consistency (background processing)
- Operational complexity (many components)
- Cost (sharding + kafka + workers)

**Monitoring:**
- Write latency (p50, p99)
- Kafka lag
- Database replica lag
- Error rates

Would you like me to dive deeper into any component?"
```

### Key Metrics to Discuss

| Metric | Target | Alert Threshold | Critical Threshold |
|--------|--------|----------------|-------------------|
| **Write Latency (sync)** | <50ms | >100ms | >200ms |
| **Write Latency (async)** | <20ms | >50ms | >100ms |
| **Queue Depth** | <100 | >1000 | >10000 |
| **Success Rate** | >99.9% | <99.5% | <99% |
| **Replication Lag** | <50ms | >100ms | >500ms |
| **Throughput** | 10K WPS | <8K WPS | <5K WPS |

---

## Conclusion

Write scaling requires careful balance between:

**Core Principles**:
1. **Start Simple** - Connection pooling, WAL, batching
2. **Go Async** - Decouple critical from non-critical writes
3. **Shard Carefully** - Only when necessary, with clear strategy
4. **Accept Trade-offs** - Consistency vs availability vs latency
5. **Monitor Everything** - Latency, throughput, errors, lag
6. **Plan for Failure** - Replication, retries, circuit breakers

**Key Takeaways**:
- Writes can't be cached away like reads
- Coordination is expensive - minimize it
- Async processing enables scale
- Sharding provides linear scaling but adds complexity
- Event sourcing fits many write-heavy workloads
- CQRS separates concerns but increases complexity

**Remember**: Start with simple optimizations first. Add complexity only when measurements show it's necessary. Understanding these patterns is essential for both interviews and production systems.

---

**Document Version**: 2.0 - Architecture Focused  
**Last Updated**: November 2024  
**Focus**: System design patterns, trade-offs, and architectural decisions  
**Companion**: [Read Scaling Architecture Guide](./read-scaling-architecture-guide.md)
