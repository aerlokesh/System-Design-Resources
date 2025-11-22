# Scaling Writes: Comprehensive System Design Guide

## Table of Contents
- [Scaling Writes: Comprehensive System Design Guide](#scaling-writes-comprehensive-system-design-guide)
  - [Table of Contents](#table-of-contents)
  - [Introduction](#introduction)
  - [Why Write Scaling is Challenging](#why-write-scaling-is-challenging)
    - [Write vs Read Differences](#write-vs-read-differences)
    - [Why Writes Are Hard](#why-writes-are-hard)
    - [Common Write Bottlenecks](#common-write-bottlenecks)
  - [Write Patterns \& Characteristics](#write-patterns--characteristics)
    - [Write Types](#write-types)
    - [Write Patterns](#write-patterns)
  - [Core Write Scaling Strategies](#core-write-scaling-strategies)
    - [1. Vertical Scaling (Scale Up)](#1-vertical-scaling-scale-up)
    - [2. Write-Ahead Logging (WAL)](#2-write-ahead-logging-wal)
    - [3. Database Connection Pooling](#3-database-connection-pooling)
    - [4. Batch Writing](#4-batch-writing)
    - [5. Asynchronous Writes](#5-asynchronous-writes)
    - [6. Database Sharding for Writes](#6-database-sharding-for-writes)
  - [Database Write Optimization](#database-write-optimization)
    - [1. Index Optimization](#1-index-optimization)
    - [2. Bulk Loading](#2-bulk-loading)
    - [3. Write Buffering](#3-write-buffering)
  - [Asynchronous Processing](#asynchronous-processing)
    - [Message Queues](#message-queues)
    - [Apache Kafka for High-Throughput Writes](#apache-kafka-for-high-throughput-writes)
    - [Benefits of Async Processing](#benefits-of-async-processing)
  - [Event-Driven Architecture](#event-driven-architecture)
    - [Event Sourcing](#event-sourcing)
    - [CQRS (Command Query Responsibility Segregation)](#cqrs-command-query-responsibility-segregation)
  - [Conflict Resolution \& Consistency](#conflict-resolution--consistency)
    - [Multi-Master Replication](#multi-master-replication)
    - [Conflict Resolution Strategies](#conflict-resolution-strategies)
    - [Consistency Models](#consistency-models)
  - [Trade-offs \& Decision Matrix](#trade-offs--decision-matrix)
    - [Technique Comparison](#technique-comparison)
    - [Decision Tree](#decision-tree)
  - [Real-World Examples](#real-world-examples)
    - [Example 1: Twitter - Tweet Creation](#example-1-twitter---tweet-creation)
    - [Example 2: Uber - Ride Writes](#example-2-uber---ride-writes)
    - [Example 3: Facebook - Status Updates](#example-3-facebook---status-updates)
    - [Example 4: Discord - Message Writes](#example-4-discord---message-writes)
  - [Interview Framework](#interview-framework)
    - [How to Approach Write Scaling in Interviews](#how-to-approach-write-scaling-in-interviews)
    - [Sample Interview Response Template](#sample-interview-response-template)
    - [Key Metrics to Discuss](#key-metrics-to-discuss)
    - [Common Interview Questions \& Answers](#common-interview-questions--answers)
    - [Advanced Interview Topics](#advanced-interview-topics)
    - [Quick Reference: Write Scaling Cheat Sheet](#quick-reference-write-scaling-cheat-sheet)
    - [Conclusion](#conclusion)
    - [Additional Resources](#additional-resources)

---

## Introduction

Write scaling is one of the most challenging aspects of system design. Unlike reads, writes cannot simply be cached or replicated - they require coordination, consistency, and careful handling to maintain data integrity.

**Key Challenges**:
- Coordination overhead
- ACID compliance requirements
- Lock contention
- Sequential bottlenecks
- Data consistency across nodes
- Conflict resolution

This guide covers all major write scaling techniques for system design interviews.

---

## Why Write Scaling is Challenging

### Write vs Read Differences

| Aspect | Reads | Writes |
|--------|-------|--------|
| **Caching** | Easy to cache | Hard to cache (invalidation) |
| **Replication** | Simple copies | Requires coordination |
| **Consistency** | Eventual OK | Often needs strong |
| **Parallelization** | Easy | Complex |
| **Idempotency** | Usually idempotent | Often not idempotent |
| **Conflicts** | No conflicts | Possible conflicts |
| **Performance** | Can be very fast | Inherently slower |

### Why Writes Are Hard

```
Read Path (Simple):
User → Cache → [Cache Hit] → Return Data (1-5ms)

Write Path (Complex):
User → Validation → Transaction Begin
    → Lock Acquisition → Write to Disk
    → Update Indexes → Replicate to Replicas
    → Wait for Acknowledgments → Transaction Commit
    → Update Cache → Return Success (50-200ms)
```

### Common Write Bottlenecks

1. **Single Master Bottleneck**
   - All writes go to one node
   - CPU/Memory/Disk I/O limits
   - Network bandwidth limits

2. **Lock Contention**
   - Multiple writes to same data
   - Row-level locks
   - Table-level locks

3. **Disk I/O**
   - Sequential write limitations
   - Fsync() penalties
   - RAID overhead

4. **Replication Lag**
   - Waiting for replicas
   - Network latency
   - Replica processing time

5. **Index Maintenance**
   - Updating multiple indexes
   - B-tree rebalancing
   - Write amplification

---

## Write Patterns & Characteristics

### Write Types

**1. Transactional Writes (OLTP)**
```
Characteristics:
- Small, frequent writes
- Strong consistency required
- Low latency needed
- Examples: Payment processing, user registration

Challenges:
- Lock contention
- High coordination overhead
- Difficult to scale horizontally
```

**2. Analytical Writes (OLAP)**
```
Characteristics:
- Large, batch writes
- Eventual consistency acceptable
- High throughput needed
- Examples: Log ingestion, analytics data

Advantages:
- Can batch operations
- Easier to scale
- Less coordination needed
```

**3. Time-Series Writes**
```
Characteristics:
- Append-only
- Time-ordered
- High volume
- Examples: Metrics, logs, sensor data

Optimization:
- No updates needed
- Partitioned by time
- Easy to scale
```

### Write Patterns

| Pattern | Description | Use Case | Complexity |
|---------|-------------|----------|------------|
| **Synchronous** | Wait for write completion | Financial transactions | High |
| **Asynchronous** | Return immediately, write later | Social media posts | Medium |
| **Batch** | Group multiple writes | Log ingestion | Medium |
| **Stream** | Continuous write flow | Real-time analytics | High |
| **Event Sourcing** | Store state changes as events | Audit logs | High |

---

## Core Write Scaling Strategies

### 1. Vertical Scaling (Scale Up)

**What**: Increase single server capacity

**Approach**:
```
Hardware Improvements:
- Faster CPUs (higher clock speed)
- More RAM (reduce disk I/O)
- SSDs/NVMe (faster disk writes)
- Better RAID configuration
- Faster network cards (10Gbps+)
```

**Example Impact**:
```
Before: HDD, 16GB RAM
- 1,000 writes/sec
- 50ms average latency

After: NVMe SSD, 128GB RAM
- 10,000 writes/sec
- 5ms average latency
```

**Pros**:
- Simple to implement
- No code changes
- Strong consistency maintained

**Cons**:
- Physical limits (~50K writes/sec)
- Expensive beyond certain point
- Single point of failure
- Downtime for upgrades

**When to Use**:
- First optimization step
- Budget available
- Before adding complexity

---

### 2. Write-Ahead Logging (WAL)

**What**: Log changes before applying them

**How It Works**:
```
┌──────────────────────────────────────┐
│         Write Request                │
└────────────┬─────────────────────────┘
             │
             ▼
┌────────────────────────────┐
│  1. Append to WAL (Fast)   │  ← Sequential write
│     Sequential disk write  │     (100K+ writes/sec)
└────────────┬───────────────┘
             │
             ▼
┌────────────────────────────┐
│  2. Acknowledge Client     │  ← Fast response
└────────────┬───────────────┘
             │
             ▼
┌────────────────────────────┐
│  3. Apply to Database      │  ← Async, can batch
│     (Background process)    │
└────────────────────────────┘
```

**Implementation Example**:
```python
class WriteAheadLog:
    def __init__(self, log_file):
        self.log_file = log_file
        self.buffer = []
        
    def write(self, operation):
        # 1. Append to log (fast, sequential)
        log_entry = {
            'timestamp': time.time(),
            'operation': operation,
            'data': operation.data
        }
        self.log_file.append(json.dumps(log_entry) + '\n')
        self.log_file.flush()  # Ensure durability
        
        # 2. Buffer for batch processing
        self.buffer.append(operation)
        
        # 3. Return immediately
        return True
    
    def apply_to_database(self):
        # Background worker applies buffered operations
        while self.buffer:
            batch = self.buffer[:1000]  # Process in batches
            self.db.batch_write(batch)
            self.buffer = self.buffer[1000:]
```

**Benefits**:
- **Fast writes**: Sequential disk I/O (10x faster)
- **Durability**: Changes persisted immediately
- **Recovery**: Can replay log after crash
- **Batching**: Can optimize application to database

**Real-World Usage**:
- PostgreSQL (pg_wal)
- MySQL (InnoDB redo log)
- Redis (AOF - Append Only File)
- Kafka (commit log)

---

### 3. Database Connection Pooling

**What**: Reuse database connections

**Problem Without Pooling**:
```python
# Bad: Create new connection for each write
def write_user(user_data):
    conn = connect_to_database()  # 50-100ms overhead!
    conn.execute("INSERT INTO users VALUES (?)", user_data)
    conn.close()

# Result: 10 writes/sec with 100ms connection overhead
```

**Solution With Pooling**:
```python
from sqlalchemy import create_engine
from sqlalchemy.pool import QueuePool

# Create connection pool
engine = create_engine(
    'postgresql://user:pass@localhost/db',
    poolclass=QueuePool,
    pool_size=50,           # Maintain 50 connections
    max_overflow=20,        # Allow 20 additional
    pool_timeout=30,        # Wait 30s for connection
    pool_recycle=3600,      # Recycle after 1 hour
    pool_pre_ping=True      # Verify before use
)

def write_user(user_data):
    # Reuse pooled connection (< 1ms overhead)
    with engine.connect() as conn:
        conn.execute("INSERT INTO users VALUES (?)", user_data)

# Result: 1000+ writes/sec
```

**Sizing Guidelines**:
```
Pool Size Calculation:
- Base on concurrent write operations
- Typically: (2 * CPU cores) + disk spindles
- Example: 8 cores + 4 disks = 20 connections

Monitor:
- Pool exhaustion rate
- Wait time for connections
- Idle connection count
```

---

### 4. Batch Writing

**What**: Group multiple writes into single operation

**Single Write (Slow)**:
```python
# 1000 individual writes = 1000 network round trips
for i in range(1000):
    db.execute("INSERT INTO logs VALUES (?, ?)", i, data[i])
    # Each: 5ms network + 2ms processing = 7ms
    # Total: 7000ms (7 seconds)
```

**Batch Write (Fast)**:
```python
# 1 batch write = 1 network round trip
values = [(i, data[i]) for i in range(1000)]
db.executemany("INSERT INTO logs VALUES (?, ?)", values)
# Total: 5ms network + 200ms processing = 205ms
# 34x faster!
```

**Optimal Batching Strategy**:
```python
class BatchWriter:
    def __init__(self, batch_size=1000, flush_interval=1.0):
        self.batch_size = batch_size
        self.flush_interval = flush_interval
        self.buffer = []
        self.last_flush = time.time()
        
    def write(self, data):
        self.buffer.append(data)
        
        # Flush if batch full or time elapsed
        if (len(self.buffer) >= self.batch_size or 
            time.time() - self.last_flush >= self.flush_interval):
            self.flush()
    
    def flush(self):
        if not self.buffer:
            return
            
        # Batch write to database
        db.executemany("INSERT INTO table VALUES (?)", self.buffer)
        self.buffer = []
        self.last_flush = time.time()
```

**Batch Size Trade-offs**:
```
Small Batches (100):
+ Lower latency per write
+ Less memory usage
- More network overhead
- More transaction overhead

Large Batches (10,000):
+ Better throughput
+ Less network overhead
- Higher latency per write
- More memory usage
- Larger transaction rollback cost
```

**Best Practices**:
```
1. Time-based flushing: Don't wait forever
2. Size-based flushing: Don't grow unbounded
3. Error handling: Partial batch failures
4. Backpressure: Slow down if buffer fills
```

---

### 5. Asynchronous Writes

**What**: Return success before write completes

**Synchronous (Slow but Safe)**:
```python
def create_post(user_id, content):
    # 1. Validate
    if not validate(content):
        return error
    
    # 2. Write to database (50ms)
    post_id = db.insert_post(user_id, content)
    
    # 3. Update search index (100ms)
    search.index_post(post_id, content)
    
    # 4. Update cache (10ms)
    cache.set(f"post:{post_id}", post_data)
    
    # 5. Send notifications (200ms)
    notify_followers(user_id, post_id)
    
    # Total: 360ms latency
    return post_id
```

**Asynchronous (Fast)**:
```python
def create_post(user_id, content):
    # 1. Validate
    if not validate(content):
        return error
    
    # 2. Write to database only (50ms)
    post_id = db.insert_post(user_id, content)
    
    # 3. Queue other operations (5ms)
    task_queue.enqueue({
        'type': 'post_created',
        'post_id': post_id,
        'user_id': user_id,
        'content': content
    })
    
    # Total: 55ms latency (6.5x faster!)
    return post_id

# Background workers handle the rest
def background_worker():
    while True:
        task = task_queue.dequeue()
        
        # Update search index
        search.index_post(task['post_id'], task['content'])
        
        # Update cache
        cache.set(f"post:{task['post_id']}", task)
        
        # Send notifications
        notify_followers(task['user_id'], task['post_id'])
```

**Queue Implementation Options**:
```
1. Redis (Simple, in-memory)
2. RabbitMQ (Reliable, persistent)
3. Apache Kafka (High throughput, distributed)
4. AWS SQS (Managed, scalable)
5. Celery (Python-specific, easy)
```

**Trade-offs**:
```
Pros:
+ Much lower latency
+ Better user experience
+ Can handle traffic spikes
+ Retry logic separated

Cons:
- Eventual consistency
- More complex debugging
- Need monitoring/alerting
- Queue can become bottleneck
```

---

### 6. Database Sharding for Writes

**What**: Distribute writes across multiple databases

**Single Database (Bottleneck)**:
```
All Writes → [Master DB] → Limited by single server
                ↓
              10K writes/sec max
```

**Sharded Database (Scaled)**:
```
User 1-1M    → [Shard 1] → 10K writes/sec
User 1M-2M   → [Shard 2] → 10K writes/sec
User 2M-3M   → [Shard 3] → 10K writes/sec
User 3M-4M   → [Shard 4] → 10K writes/sec
                            ↓
                    Total: 40K writes/sec
```

**Sharding Strategies for Writes**:

**A. Hash-Based Sharding** (Best for even distribution)
```python
class HashBasedSharding:
    def __init__(self, num_shards):
        self.shards = [DatabaseConnection(f"shard_{i}") 
                       for i in range(num_shards)]
    
    def write(self, user_id, data):
        # Determine shard
        shard_id = hash(user_id) % len(self.shards)
        shard = self.shards[shard_id]
        
        # Write to specific shard
        return shard.insert(data)
    
    def read(self, user_id):
        shard_id = hash(user_id) % len(self.shards)
        return self.shards[shard_id].query(user_id)
```

**B. Range-Based Sharding** (Good for ordered data)
```python
class RangeBasedSharding:
    def __init__(self):
        self.ranges = [
            (0, 1000000, 'shard_1'),
            (1000000, 2000000, 'shard_2'),
            (2000000, 3000000, 'shard_3'),
        ]
    
    def write(self, user_id, data):
        for start, end, shard_name in self.ranges:
            if start <= user_id < end:
                shard = self.get_shard(shard_name)
                return shard.insert(data)
```

**C. Geographic Sharding** (Low latency writes)
```python
class GeographicSharding:
    def __init__(self):
        self.shards = {
            'US': DatabaseConnection('us-east-1'),
            'EU': DatabaseConnection('eu-west-1'),
            'ASIA': DatabaseConnection('ap-south-1'),
        }
    
    def write(self, user_location, data):
        region = self.get_region(user_location)
        return self.shards[region].insert(data)
```

**Challenges with Sharding**:
```
1. Cross-Shard Transactions
   Problem: User follows someone on different shard
   Solution: 
   - Two-phase commit (slow)
   - Eventual consistency
   - Saga pattern

2. Resharding
   Problem: Adding/removing shards
   Solution:
   - Consistent hashing
   - Virtual shards
   - Planned downtime

3. Hotspots
   Problem: Celebrity user on one shard
   Solution:
   - Better shard key
   - Dedicated shards for hot data
   - Further partitioning

4. Operational Complexity
   Problem: Managing multiple databases
   Solution:
   - Automation tools
   - Good monitoring
   - Database proxies (Vitess, ProxySQL)
```

---

## Database Write Optimization

### 1. Index Optimization

**The Index Trade-off**:
```
More Indexes:
+ Faster reads
- Slower writes (update all indexes)
- More storage space
- Write amplification

Fewer Indexes:
- Slower reads
+ Faster writes
+ Less storage space
```

**Write Impact Example**:
```sql
-- Table with 5 indexes
CREATE TABLE users (
    id INT PRIMARY KEY,           -- Index 1
    email VARCHAR(255),
    username VARCHAR(100),
    created_at TIMESTAMP
);

CREATE INDEX idx_email ON users(email);        -- Index 2
CREATE INDEX idx_username ON users(username);  -- Index 3
CREATE INDEX idx_created ON users(created_at); -- Index 4
CREATE INDEX idx_composite ON users(email, username); -- Index 5

-- Single INSERT updates 5 indexes!
INSERT INTO users VALUES (1, 'user@example.com', 'john', NOW());
-- Write cost: 1 table + 5 indexes = 6 writes

-- Optimization: Remove unused indexes
DROP INDEX idx_composite;  -- If not used in queries
-- New write cost: 1 table + 4 indexes = 5 writes (17% faster)
```

**Best Practices**:
```
1. Audit index usage regularly
   SELECT * FROM pg_stat_user_indexes 
   WHERE idx_scan = 0;

2. Drop unused indexes

3. Consider partial indexes
   CREATE INDEX idx_active_users 
   ON users(email) 
   WHERE active = true;

4. Delay index creation for bulk loads
   - Load data first
   - Create indexes after
   - Much faster for large imports
```

### 2. Bulk Loading

**Standard Insert (Slow)**:
```sql
-- 1M rows with autocommit
BEGIN;
INSERT INTO logs VALUES (1, 'data1');
COMMIT;
BEGIN;
INSERT INTO logs VALUES (2, 'data2');
COMMIT;
-- ... repeat 1M times
-- Time: 30 minutes
```

**Optimized Bulk Load (Fast)**:
```sql
-- Disable indexes temporarily
DROP INDEX idx_log_timestamp;

-- Bulk load with COPY
COPY logs FROM '/tmp/data.csv' WITH (FORMAT csv);
-- Time: 2 minutes

-- Recreate indexes
CREATE INDEX idx_log_timestamp ON logs(timestamp);
-- Time: 3 minutes

-- Total: 5 minutes (6x faster!)
```

**PostgreSQL COPY Example**:
```python
import psycopg2
from io import StringIO

def bulk_load(data):
    # Create CSV in memory
    csv_data = StringIO()
    for row in data:
        csv_data.write(f"{row[0]},{row[1]},{row[2]}\n")
    csv_data.seek(0)
    
    # Use COPY for fast bulk insert
    conn = psycopg2.connect(...)
    cursor = conn.cursor()
    cursor.copy_from(csv_data, 'logs', sep=',', 
                     columns=('id', 'timestamp', 'message'))
    conn.commit()
```

### 3. Write Buffering

**What**: Accumulate writes in memory before flushing

**Implementation**:
```python
class WriteBuffer:
    def __init__(self, buffer_size=10000, flush_interval=5):
        self.buffer = []
        self.buffer_size = buffer_size
        self.flush_interval = flush_interval
        self.last_flush = time.time()
        self.lock = threading.Lock()
        
        # Start background flush thread
        self.flush_thread = threading.Thread(target=self._auto_flush)
        self.flush_thread.daemon = True
        self.flush_thread.start()
    
    def write(self, data):
        with self.lock:
            self.buffer.append(data)
            
            # Flush if buffer full
            if len(self.buffer) >= self.buffer_size:
                self._flush()
    
    def _flush(self):
        if not self.buffer:
            return
        
        # Batch write to database
        batch = self.buffer[:]
        self.buffer = []
        self.last_flush = time.time()
        
        try:
            db.bulk_insert(batch)
        except Exception as e:
            # Handle errors - maybe re-queue
            log.error(f"Flush failed: {e}")
            self.buffer = batch + self.buffer
    
    def _auto_flush(self):
        while True:
            time.sleep(1)
            with self.lock:
                if (time.time() - self.last_flush >= self.flush_interval 
                    and self.buffer):
                    self._flush()
```

**Benefits**:
```
Without Buffering:
- 1000 writes = 1000 DB calls
- Time: 10 seconds (10ms/write)

With Buffering (flush every 100):
- 1000 writes = 10 DB calls
- Time: 1 second (1ms/write)
- 10x faster!
```

---

## Asynchronous Processing

### Message Queues

**Architecture**:
```
┌──────────┐     ┌──────────┐     ┌──────────┐
│  Writer  │────▶│  Queue   │────▶│  Worker  │
│ Service  │     │          │     │ Process  │
└──────────┘     └──────────┘     └──────────┘
   Fast              Buffer         Handles
   Response          Decouples      Heavy Work
```

**Implementation with Redis Queue**:
```python
import redis
import json

class RedisQueue:
    def __init__(self, name):
        self.redis = redis.Redis()
        self.queue_name = name
    
    def enqueue(self, task):
        # Add to queue (fast, O(1))
        self.redis.rpush(self.queue_name, json.dumps(task))
    
    def dequeue(self, timeout=1):
        # Blocking pop (waits for items)
        result = self.redis.blpop(self.queue_name, timeout)
        if result:
            return json.loads(result[1])
        return None

# Producer (API server)
def create_order(order_data):
    # 1. Quick validation
    if not validate_order(order_data):
        return error
    
    # 2. Save to database (50ms)
    order_id = db.insert_order(order_data)
    
    # 3. Queue async tasks (5ms)
    queue = RedisQueue('order_processing')
    queue.enqueue({
        'type': 'process_order',
        'order_id': order_id,
        'tasks': ['send_email', 'update_inventory', 'charge_card']
    })
    
    # 4. Return quickly (55ms total)
    return {'order_id': order_id, 'status': 'pending'}

# Consumer (Background worker)
def worker():
    queue = RedisQueue('order_processing')
    while True:
        task = queue.dequeue()
        if task:
            process_task(task)

def process_task(task):
    if task['type'] == 'process_order':
        send_confirmation_email(task['order_id'])
        update_inventory(task['order_id'])
        charge_credit_card(task['order_id'])
        db.update_order_status(task['order_id'], 'completed')
```

### Apache Kafka for High-Throughput Writes

**Why Kafka**:
```
Throughput: 1M+ messages/sec per broker
Durability: Replicated, persisted to disk
Ordering: Per-partition ordering guaranteed
Scalability: Add brokers horizontally
Retention: Configurable message retention
```

**Kafka Write Pattern**:
```python
from kafka import KafkaProducer
import json

# Producer configuration
producer = KafkaProducer(
    bootstrap_servers=['localhost:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8'),
    acks='all',           # Wait for all replicas
    retries=3,            # Retry on failure
    batch_size=16384,     # Batch for efficiency
    linger_ms=10          # Wait up to 10ms for batch
)

def log_event(event_data):
    # Async send (returns immediately)
    future = producer.send('events', event_data)
    
    # Optional: Wait for confirmation
    # record_metadata = future.get(timeout=10)
    
    return True

# Example usage
log_event({
    'user_id': 12345,
    'action': 'purchase',
    'amount': 99.99,
    'timestamp': time.time()
})

# Flush before shutdown
producer.flush()
```

**Consumer Processing**:
```python
from kafka import KafkaConsumer

consumer = KafkaConsumer(
    'events',
    bootstrap_servers=['localhost:9092'],
    group_id='analytics-group',
    auto_offset_reset='earliest',
    enable_auto_commit=False  # Manual commit for control
)

for message in consumer:
    event = json.loads(message.value)
    
    # Process event
    store_in_database(event)
    update_analytics(event)
    
    # Commit offset after successful processing
    consumer.commit()
```

### Benefits of Async Processing

```
Synchronous System:
- API latency: 500ms
- Max throughput: 2 requests/sec per server
- Blocking operations
- Tight coupling

Asynchronous System:
- API latency: 50ms (10x faster)
- Max throughput: 20 requests/sec per server (10x more)
- Non-blocking operations
- Loose coupling
- Better failure isolation
```

---

## Event-Driven Architecture

### Event Sourcing

**What**: Store all changes as immutable events

**Traditional Approach (State-Based)**:
```sql
-- Store current state only
CREATE TABLE account (
    id INT PRIMARY KEY,
    balance DECIMAL(10,2),
    updated_at TIMESTAMP
);

-- Update loses history
UPDATE account SET balance = 1000 WHERE id = 123;
UPDATE account SET balance = 900 WHERE id = 123;
-- Previous balance (1000) is lost
```

**Event Sourcing Approach (Event-Based)**:
```sql
-- Store all events
CREATE TABLE account_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT,
    event_type VARCHAR(50),
    amount DECIMAL(10,2),
    timestamp TIMESTAMP,
    metadata JSON
);

-- All changes recorded
INSERT INTO account_events VALUES 
    (1, 123, 'account_created', 1000, NOW(), '{}'),
    (2, 123, 'withdrawal', -100, NOW(), '{"atm_id": "ATM001"}'),
    (3, 123, 'deposit', 50, NOW(), '{"check_num": "1234"}');

-- Reconstruct current state
SELECT SUM(amount) as balance 
FROM account_events 
WHERE account_id = 123;
-- Result: 950
```

**Implementation**:
```python
class EventStore:
    def append_event(self, aggregate_id, event):
        # Append event (append-only, fast)
        self.db.insert('events', {
            'aggregate_id': aggregate_id,
            'event_type': event['type'],
            'event_data': json.dumps(event['data']),
            'timestamp': time.time(),
            'version': self.get_next_version(aggregate_id)
        })
    
    def get_events(self, aggregate_id, from_version=0):
        # Read events for aggregate
        return self.db.query(
            'SELECT * FROM events '
            'WHERE aggregate_id = ? AND version > ? '
            'ORDER BY version',
            aggregate_id, from_version
        )
    
    def get_current_state(self, aggregate_id):
        # Rebuild state from events
        events = self.get_events(aggregate_id)
        state = {}
        for event in events:
            state = self.apply_event(state, event)
        return state

# Usage
event_store = EventStore()

# Deposit money
event_store.append_event('account_123', {
    'type': 'money_deposited',
    'data': {'amount': 100, 'source': 'wire_transfer'}
})

# Withdraw money
event_store.append_event('account_123', {
    'type': 'money_withdrawn',
    'data': {'amount': 50, 'atm_id': 'ATM001'}
})

# Get current balance
current_state = event_store.get_current_state('account_123')
```

**Benefits**:
```
1. Complete Audit Trail
   - Every change recorded
   - Can answer "what happened when?"
   - Regulatory compliance

2. Temporal Queries
   - State at any point in time
   - "What was balance on Jan 1?"

3. Event Replay
   - Rebuild state from events
   - Fix bugs by replaying
   - Create new projections

4. Scalability
   - Append-only (very fast writes)
   - No updates or deletes
   - Can partition by aggregate
```

**Challenges**:
```
1. Query Complexity
   - Need to rebuild state
   - Solution: Snapshots, CQRS

2. Event Schema Evolution
   - Old events with old schema
   - Solution: Versioning, upcasting

3. Storage Growth
   - Events accumulate
   - Solution: Snapshots, archiving
```

### CQRS (Command Query Responsibility Segregation)

**What**: Separate write model from read model

**Architecture**:
```
┌────────────────┐
│    Commands    │
│   (Writes)     │
└───────┬────────┘
        │
        ▼
┌────────────────┐      Events      ┌────────────────┐
│  Write Model   │─────────────────▶│   Read Model   │
│  (PostgreSQL)  │                  │   (MongoDB)    │
│                │                  │                │
│  Normalized    │                  │ Denormalized   │
│  ACID          │                  │ Optimized      │
└────────────────┘                  └────────────────┘
```

**Implementation**:
```python
# Write Side (Commands)
class OrderService:
    def create_order(self, order_data):
        # 1. Validate command
        if not self.validate_order(order_data):
            raise ValidationError()
        
        # 2. Write to write database (normalized)
        order_id = self.write_db.orders.insert({
            'user_id': order_data['user_id'],
            'status': 'pending',
            'created_at': datetime.now()
        })
        
        for item in order_data['items']:
            self.write_db.order_items.insert({
                'order_id': order_id,
                'product_id': item['product_id'],
                'quantity': item['quantity']
            })
        
        # 3. Publish event
        self.event_bus.publish('OrderCreated', {
            'order_id': order_id,
            'user_id': order_data['user_id'],
            'items': order_data['items'],
            'total': self.calculate_total(order_data['items'])
        })
        
        return order_id

# Read Side (Queries)
class OrderQueryService:
    def handle_order_created(self, event):
        # Update denormalized read model
        self.read_db.user_orders.update(
            {'user_id': event['user_id']},
            {
                '$push': {
                    'orders': {
                        'order_id': event['order_id'],
                        'total': event['total'],
                        'items': event['items'],
                        'created_at': datetime.now()
                    }
                }
            },
            upsert=True
        )
    
    def get_user_orders(self, user_id):
        # Fast read from denormalized model
        return self.read_db.user_orders.find_one({'user_id': user_id})
```

**Benefits**:
```
1. Optimized Models
   - Write model: Normalized, ACID
   - Read model: Denormalized, fast queries

2. Independent Scaling
   - Scale write database for writes
   - Scale read database for reads
   - Different technologies

3. Multiple Read Models
   - Different views of same data
   - Optimized for different queries

4. Better Performance
   - No complex JOINs on reads
   - Simpler queries
```

**Challenges**:
```
1. Eventual Consistency
   - Read model lags behind writes
   - Solution: Acceptable for most use cases

2. Complexity
   - Two models to maintain
   - Event synchronization
   - Solution: Use when benefits outweigh cost

3. Event Versioning
   - Schema changes over time
   - Solution: Event upcasting, versioning
```

---

## Conflict Resolution & Consistency

### Multi-Master Replication

**Challenge**: Multiple databases accepting writes simultaneously

**Conflict Scenarios**:
```
Scenario 1: Concurrent Updates
User A (NY):  balance = 1000 → withdraw 100 → balance = 900
User B (LA):  balance = 1000 → deposit 50  → balance = 1050
Result: Conflict! Which is correct?

Scenario 2: Unique Constraint Violation
User A (NY):  INSERT INTO users (id=1, email='john@example.com')
User B (LA):  INSERT INTO users (id=2, email='john@example.com')
Result: Duplicate email!
```

### Conflict Resolution Strategies

**1. Last Write Wins (LWW)**
```python
class LastWriteWins:
    def resolve_conflict(self, value_a, value_b):
        # Compare timestamps
        if value_a['timestamp'] > value_b['timestamp']:
            return value_a
        return value_b

# Example
value_a = {'balance': 900, 'timestamp': 1000}
value_b = {'balance': 1050, 'timestamp': 1005}
result = LastWriteWins().resolve_conflict(value_a, value_b)
# Result: {'balance': 1050} (newer timestamp wins)
```

**Pros**: Simple, works everywhere
**Cons**: Data loss possible, not always correct

---

**2. Version Vectors**
```python
class VersionVector:
    def __init__(self):
        self.versions = {}  # {node_id: version}
    
    def increment(self, node_id):
        self.versions[node_id] = self.versions.get(node_id, 0) + 1
    
    def merge(self, other):
        # Take maximum version for each node
        for node_id, version in other.versions.items():
            self.versions[node_id] = max(
                self.versions.get(node_id, 0),
                version
            )
    
    def conflicts_with(self, other):
        # Check if neither is ancestor of other
        self_newer = False
        other_newer = False
        
        for node_id in set(self.versions) | set(other.versions):
            self_ver = self.versions.get(node_id, 0)
            other_ver = other.versions.get(node_id, 0)
            
            if self_ver > other_ver:
                self_newer = True
            elif other_ver > self_ver:
                other_newer = True
        
        return self_newer and other_newer  # True if conflict

# Usage
v1 = VersionVector()
v1.versions = {'node_a': 1, 'node_b': 2}

v2 = VersionVector()
v2.versions = {'node_a': 2, 'node_b': 1}

if v1.conflicts_with(v2):
    # Conflict detected! Need manual resolution
    resolve_manually(v1, v2)
```

---

**3. CRDTs (Conflict-Free Replicated Data Types)**
```python
# Counter CRDT (Grow-only)
class GCounter:
    def __init__(self, node_id):
        self.node_id = node_id
        self.counts = {}  # {node_id: count}
    
    def increment(self, amount=1):
        # Only local node can increment its counter
        self.counts[self.node_id] = self.counts.get(self.node_id, 0) + amount
    
    def merge(self, other):
        # Merge takes maximum for each node
        for node_id, count in other.counts.items():
            self.counts[node_id] = max(
                self.counts.get(node_id, 0),
                count
            )
    
    def value(self):
        # Total is sum of all node counts
        return sum(self.counts.values())

# Example: Distributed like counter
node_a = GCounter('node_a')
node_a.increment(5)  # {node_a: 5}

node_b = GCounter('node_b')
node_b.increment(3)  # {node_b: 3}

# Merge (no conflicts!)
node_a.merge(node_b)
print(node_a.value())  # 8

node_b.merge(node_a)
print(node_b.value())  # 8
```

**CRDT Types**:
```
G-Counter: Grow-only counter (likes, views)
PN-Counter: Positive-Negative counter (inventory)
G-Set: Grow-only set (tags, followers)
OR-Set: Observed-Remove set (shopping cart)
LWW-Register: Last-Write-Wins register (status)
```

---

### Consistency Models

| Model | Description | Guarantees | Use Case |
|-------|-------------|------------|----------|
| **Strong** | All reads see latest write | Linearizability | Financial systems |
| **Eventual** | All replicas converge eventually | Eventually consistent | Social media |
| **Causal** | Related operations ordered | Happens-before | Chat systems |
| **Read-Your-Writes** | User sees own writes | Session consistency | User profiles |
| **Monotonic Reads** | No going backwards in time | Progressive reads | Timeline feeds |

**Implementation Example**:
```python
# Read-Your-Writes Consistency
class ReadYourWritesDB:
    def __init__(self):
        self.master = MasterDB()
        self.replicas = [ReplicaDB() for _ in range(3)]
        self.user_write_versions = {}
    
    def write(self, user_id, data):
        # Write to master
        version = self.master.write(data)
        
        # Track version for this user
        self.user_write_versions[user_id] = version
        
        return version
    
    def read(self, user_id, query):
        user_version = self.user_write_versions.get(user_id, 0)
        
        # Try replicas first
        for replica in self.replicas:
            if replica.get_version() >= user_version:
                return replica.read(query)
        
        # Fall back to master if replicas not caught up
        return self.master.read(query)
```

---

## Trade-offs & Decision Matrix

### Technique Comparison

| Technique | Write Throughput | Latency | Consistency | Complexity | Cost |
|-----------|-----------------|---------|-------------|------------|------|
| **Vertical Scaling** | Medium | Low | Strong | Low | High |
| **WAL** | High | Very Low | Strong | Medium | Low |
| **Connection Pooling** | Medium | Low | Strong | Low | Low |
| **Batch Writes** | Very High | Medium | Strong | Low | Low |
| **Async Writes** | High | Very Low | Eventual | Medium | Medium |
| **Sharding** | Very High | Low | Strong* | High | High |
| **Event Sourcing** | High | Low | Strong | High | Medium |
| **CQRS** | High | Low | Eventual | Very High | High |
| **Multi-Master** | Very High | Low | Eventual | Very High | High |

*Strong within shard, Eventual across shards

---

### Decision Tree

```
Need to scale writes?
│
├─ Can accept eventual consistency?
│  │
│  ├─ YES → Async Processing + Message Queue
│  │
│  └─ NO → Strong consistency required
│     │
│     ├─ Single region?
│     │  └─ YES → Vertical + WAL + Batching
│     │
│     └─ Multi-region?
│        └─ YES → Sharding + CQRS
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
      3. WAL
      4. Then scale as needed
```

---

## Real-World Examples

### Example 1: Twitter - Tweet Creation

**Problem**: Handle 6,000 tweets/second

**Solution**:
```
1. Async Processing:
   - API accepts tweet (50ms)
   - Queue for processing (5ms)
   - Return immediately (55ms total)

2. Fan-out on Write:
   - Background workers process queue
   - Write tweet to storage (Cassandra)
   - Push to followers' timelines (Redis)
   - Update search index (async)

3. Optimization:
   - Batch writes to Redis (1000 at a time)
   - Async replication across datacenters
   - Celebrity users: Fan-out on read instead

Architecture:
┌────────┐     ┌──────────┐     ┌──────────┐
│ API    │────▶│ Kafka    │────▶│ Workers  │
│ Server │     │ Queue    │     │ (100s)   │
└────────┘     └──────────┘     └────┬─────┘
                                      │
                ┌─────────────────────┴──────┐
                │                            │
                ▼                            ▼
         ┌──────────┐                 ┌──────────┐
         │Cassandra │                 │  Redis   │
         │(Tweets)  │                 │(Timelines)│
         └──────────┘                 └──────────┘

Throughput: 
- 6K tweets/sec → 600 workers × 10 tweets/sec each
- Peak: 18K tweets/sec during major events
```

---

### Example 2: Uber - Ride Writes

**Problem**: Update driver location every 4 seconds for 1M drivers

**Solution**:
```
1. Write Optimization:
   - Batch location updates (10 drivers/batch)
   - Write to time-series database (Cassandra)
   - Partition by geographic region
   - TTL on old locations (keep 24 hours)

2. Multi-Master Replication:
   - Regional clusters (US-East, US-West, EU, Asia)
   - Writes go to nearest cluster
   - Eventual consistency across regions
   - Conflict resolution: Last Write Wins (timestamp)

3. Caching:
   - Redis for active driver locations
   - 30-second TTL
   - Update on every location ping

Scale:
- 1M drivers × 1 update/4sec = 250K writes/sec
- 10 geographic shards = 25K writes/sec per shard
- Each shard: 5 Cassandra nodes = 5K writes/sec per node
- Well within capacity (10K writes/sec per node)
```

---

### Example 3: Facebook - Status Updates

**Problem**: 500M+ status updates daily

**Solution**:
```
1. Write Path:
   - Post to edge server (nearest datacenter)
   - Write to MySQL shard (by user_id hash)
   - Replicate to 2 slave DBs (async)
   - Enqueue for fan-out (Kafka)
   
2. Fan-out Strategy:
   - Most users: Fan-out on write
     * Write to friends' feed caches
     * Batch writes to Redis
   - Celebrities: Fan-out on read
     * Write to central cache
     * Merge on feed request

3. Optimization:
   - Batch writes: 100 status updates/query
   - Connection pooling: 50 connections/server
   - Write-ahead log for durability
   - Async replication across datacenters

Throughput:
- 500M updates/day = ~5,800 updates/sec average
- Peak: 3x average = ~17,400 updates/sec
- 20 DB shards = ~870 updates/sec per shard
- Easy to handle with optimization
```

---

### Example 4: Discord - Message Writes

**Problem**: Deliver 5M+ messages/second

**Solution**:
```
1. Time-Series Approach:
   - Messages are append-only
   - Cassandra for storage
   - Partitioned by channel_id + timestamp
   - No updates or deletes

2. Write Path:
   - Validate message (10ms)
   - Write to Cassandra (5ms)
   - Publish to message queue (5ms)
   - Return to user (20ms total)
   - Async: Push to online users via WebSocket

3. Optimization:
   - Batch writes when possible
   - Local write buffering (100ms window)
   - Compression before storage
   - Replication factor: 3

Architecture:
┌────────┐     ┌──────────┐     ┌──────────┐
│Gateway │────▶│Cassandra │────▶│ Message  │
│Servers │     │ Cluster  │     │ Queue    │
└────────┘     └──────────┘     └────┬─────┘
                                      │
                                      ▼
                               ┌──────────┐
                               │WebSocket │
                               │ Servers  │
                               └──────────┘

Scale:
- 5M messages/sec across all channels
- 100 Cassandra nodes = 50K writes/sec per node
- Linear scaling by adding nodes
```

---

## Interview Framework

### How to Approach Write Scaling in Interviews

**Step 1: Clarify Requirements** (5 minutes)
```
Critical Questions:
1. What is the write rate (QPS)?
2. What is the data size per write?
3. What are the consistency requirements?
4. What is the acceptable write latency?
5. Is the data relational or can it be denormalized?
6. Are there any geographic requirements?
7. What is the read:write ratio?
8. Are writes independent or related (transactions)?
```

**Step 2: Calculate Scale** (5 minutes)
```
Example Calculation:
- 10M active users
- Each user writes 10 times/day
- Total writes/day = 100M writes/day
- Writes/second = 100M / 86400 = ~1,160 QPS
- Peak (3x average) = ~3,500 QPS
- Data size: 1KB per write
- Storage/day = 100M × 1KB = 100GB/day
- Storage/year = 36TB/year
```

**Step 3: Choose Base Strategy** (10 minutes)
```
Decision Framework:

1. Start Simple:
   - Connection pooling
   - Batch writes
   - Write-ahead logging

2. Add Async if:
   - Latency matters more than consistency
   - Can tolerate eventual consistency
   - Need to handle traffic spikes

3. Consider Sharding if:
   - Single DB can't handle load (>10K QPS)
   - Data > 1TB
   - Need geographic distribution

4. Use Event Sourcing if:
   - Need complete audit trail
   - Append-only workload
   - Complex state transitions

5. Use CQRS if:
   - Complex read patterns
   - Need multiple views
   - Can accept eventual consistency
```

**Step 4: Design Architecture** (15 minutes)
```
Components to Include:

1. Load Balancer
   - Distribute write traffic
   - Health checks
   - Sticky sessions if needed

2. Application Servers
   - Connection pooling
   - Input validation
   - Business logic

3. Message Queue (if async)
   - Kafka, RabbitMQ, SQS
   - Persist writes
   - Decouple processing

4. Database Layer
   - Primary database (writes)
   - Replicas (reads)
   - Sharding if needed

5. Monitoring
   - Write latency (p50, p99)
   - Queue depth
   - Error rates
   - Throughput
```

**Step 5: Discuss Trade-offs** (5 minutes)
```
Always Mention:
1. Consistency vs Availability (CAP theorem)
2. Latency vs Throughput
3. Cost vs Performance
4. Complexity vs Maintainability
5. Operational overhead
```

---

### Sample Interview Response Template

```
Interviewer: "Design a system to handle 10,000 writes per second"

You: "Let me clarify some requirements first:
1. What's the consistency requirement?
2. What's the acceptable latency?
3. What type of data are we writing?

[After getting answers: Eventual consistency OK, <100ms latency, user activity logs]

Based on these requirements, I'll design a write-optimized system:

Layer 1 - API Servers:
- 10 API servers behind load balancer
- Each handles ~1,000 writes/sec
- Connection pool of 50 connections per server
- Input validation and batching

Layer 2 - Message Queue (Kafka):
- Buffer writes for async processing
- 3 brokers with replication
- Partitioned by user_id for ordering
- Returns success immediately (<50ms)

Layer 3 - Write Workers:
- 20 workers consuming from Kafka
- Batch writes (100 per batch)
- Write to Cassandra
- Each processes ~500 writes/sec

Layer 4 - Storage (Cassandra):
- 10-node cluster
- Replication factor: 3
- Partitioned by user_id
- Each node: ~1,000 writes/sec

Performance:
- API latency: 50ms (just queue)
- Actual processing: 200ms (async)
- Total throughput: 10K writes/sec
- Scales linearly (add more workers/nodes)

Trade-offs:
+ Low latency for users
+ High throughput
+ Scales horizontally
+ Fault tolerant
- Eventual consistency
- More complex operations
- Need monitoring for queue depth
- Higher cost (more components)

Alternative if strong consistency needed:
- Remove message queue
- Write directly to database
- Use synchronous replication
- Add more DB shards
- Trade-off: Higher latency (100-200ms)
"
```

---

### Key Metrics to Discuss

| Metric | Good | Acceptable | Poor |
|--------|------|------------|------|
| **Write Latency (sync)** | <50ms | 50-200ms | >200ms |
| **Write Latency (async)** | <20ms | 20-50ms | >50ms |
| **Throughput** | >10K QPS | 1K-10K QPS | <1K QPS |
| **Success Rate** | >99.99% | 99.9-99.99% | <99.9% |
| **Queue Lag** | <1s | 1-10s | >10s |
| **Replication Lag** | <100ms | 100ms-1s | >1s |

---

### Common Interview Questions & Answers

**Q: Why is write scaling harder than read scaling?**
```
A: Several reasons:
1. Can't cache writes (invalidation)
2. Need coordination for consistency
3. Must maintain data integrity
4. Locks create contention
5. Single master bottleneck
6. Index updates add overhead
7. Replication adds latency
```

**Q: How do you handle write hotspots?**
```
A: Multiple strategies:
1. Better shard key selection
   - Avoid celebrity problem
   - Hash-based distribution

2. Write buffering/coalescing
   - Combine multiple writes
   - Reduce database load

3. Dedicated shards
   - Hot users on separate shard
   - More resources allocated

4. Rate limiting
   - Protect from abuse
   - Fair usage policies

5. Async processing
   - Queue writes
   - Smooth out spikes
```

**Q: How do you ensure write durability?**
```
A: Multiple levels:
1. Write-Ahead Logging (WAL)
   - Log before applying
   - Can replay after crash

2. Synchronous replication
   - Wait for replica ack
   - Slower but durable

3. Fsync to disk
   - Force write to physical disk
   - Survives crashes

4. Distributed consensus
   - Raft, Paxos
   - Majority must acknowledge

5. Backup strategies
   - Regular snapshots
   - Point-in-time recovery
```

**Q: What's the difference between sharding and replication?**
```
A: Key differences:

Sharding:
- Horizontal partitioning
- Splits DATA across nodes
- Increases write capacity
- Each shard has subset of data
- For scaling

Replication:
- Copies ALL data
- Same data on multiple nodes
- Increases read capacity
- For redundancy/availability
- For fault tolerance

Combined:
- Shard for write scaling
- Replicate each shard for reads
- Best of both worlds
```

**Q: How do you test write scaling?**
```
A: Testing approach:
1. Load Testing
   - Simulate write load
   - Gradually increase QPS
   - Find breaking point

2. Chaos Engineering
   - Kill nodes randomly
   - Network partitions
   - Verify recovery

3. Metrics to Monitor
   - Write latency (p50, p99, p999)
   - Throughput (QPS)
   - Error rate
   - Queue depth
   - Database connections

4. Tools
   - JMeter, Gatling (load)
   - Chaos Monkey (failures)
   - Prometheus (metrics)
   - Grafana (visualization)
```

---

### Advanced Interview Topics

**1. Write-Write Conflicts**
```python
# Problem: Two users update same account
User A: balance = 1000 → withdraw 100 → balance = 900
User B: balance = 1000 → deposit 50 → balance = 1050
# Result: One update lost!

# Solution 1: Optimistic Locking
def withdraw(account_id, amount):
    account = db.get_account(account_id)
    original_version = account.version
    
    new_balance = account.balance - amount
    
    # Update only if version unchanged
    success = db.update_account(
        account_id,
        new_balance,
        where_version=original_version,
        new_version=original_version + 1
    )
    
    if not success:
        # Retry or fail
        raise ConcurrentModificationError()

# Solution 2: Pessimistic Locking
def withdraw(account_id, amount):
    with db.lock_row(account_id):
        account = db.get_account(account_id)
        new_balance = account.balance - amount
        db.update_account(account_id, new_balance)
```

**2. Two-Phase Commit (2PC)**
```python
# For distributed transactions across shards

class TwoPhaseCommit:
    def execute_transaction(self, operations):
        transaction_id = generate_id()
        
        # Phase 1: Prepare
        prepared_nodes = []
        for op in operations:
            node = self.get_node(op.shard_id)
            if node.prepare(transaction_id, op):
                prepared_nodes.append(node)
            else:
                # Abort if any fails
                self.abort(transaction_id, prepared_nodes)
                return False
        
        # Phase 2: Commit
        for node in prepared_nodes:
            node.commit(transaction_id)
        
        return True
    
    def abort(self, transaction_id, nodes):
        for node in nodes:
            node.abort(transaction_id)
```

**Downsides**:
- Blocking protocol
- Coordinator single point of failure
- Slow (multiple round trips)
- Use Saga pattern instead for microservices

**3. Saga Pattern** (Better for microservices)
```python
class OrderSaga:
    def create_order(self, order_data):
        # Step 1: Reserve inventory
        inventory_id = inventory_service.reserve(order_data)
        
        try:
            # Step 2: Charge payment
            payment_id = payment_service.charge(order_data)
            
            try:
                # Step 3: Create order
                order_id = order_service.create(order_data)
                return order_id
            
            except Exception:
                # Compensate: Refund payment
                payment_service.refund(payment_id)
                raise
        
        except Exception:
            # Compensate: Release inventory
            inventory_service.release(inventory_id)
            raise

# Or use event-driven saga:
# 1. Publish OrderCreated event
# 2. Each service subscribes
# 3. Each service publishes completion event
# 4. Saga coordinator tracks progress
# 5. Compensating transactions on failure
```

---

### Quick Reference: Write Scaling Cheat Sheet

**When to Use Each Technique**:
```
Connection Pooling:  Always (free performance)
Batch Writes:        At 100+ writes/sec
WAL:                 At 1,000+ writes/sec
Async Processing:    When latency critical
Sharding:            At 10,000+ writes/sec or 1TB+ data
Event Sourcing:      When audit trail needed
CQRS:                When read/write patterns very different
Multi-Master:        When multi-region writes required
```

**Latency Targets**:
```
Synchronous Write:   50-200ms
Async Write:         10-50ms (queue)
Batch Write:         100-500ms (larger batches)
Cross-DC Replication: 50-200ms
Distributed Transaction: 200-1000ms (avoid if possible)
```

**Throughput Limits** (per node):
```
RDBMS (MySQL/Postgres):  5-10K writes/sec
NoSQL (Cassandra):       10-50K writes/sec
Time-Series (InfluxDB):  50-100K writes/sec
In-Memory (Redis):       100-500K writes/sec
Message Queue (Kafka):   1M+ messages/sec
```

---

### Conclusion

Write scaling requires careful consideration of:

1. **Consistency Requirements** - Can you accept eventual consistency?
2. **Latency vs Throughput** - Optimize for user experience
3. **Cost vs Complexity** - Start simple, scale as needed
4. **Operational Overhead** - Can you manage the system?

**Key Principles**:
- Start with simple optimizations (pooling, batching, WAL)
- Use async processing when possible
- Shard only when necessary (adds complexity)
- Monitor everything (latency, throughput, errors)
- Plan for failures (retries, compensation, idempotency)
- Test at scale before production

**Remember**: Premature optimization is the root of all evil, but understanding scaling strategies is essential for system design interviews and building production systems.

---

### Additional Resources

**Books**:
- "Designing Data-Intensive Applications" by Martin Kleppmann
- "Database Internals" by Alex Petrov
- "Building Microservices" by Sam Newman

**Papers**:
- "The Log: What every software engineer should know about real-time data's unifying abstraction"
- "Kafka: A Distributed Messaging System for Log Processing"
- "Dynamo: Amazon's Highly Available Key-value Store"
- "Spanner: Google's Globally-Distributed Database"

**Tools & Technologies**:
- **Databases**: PostgreSQL, MySQL, Cassandra, MongoDB
- **Message Queues**: Kafka, RabbitMQ, AWS SQS, Google Pub/Sub
- **Caching**: Redis, Memcached
- **Monitoring**: Prometheus, Grafana, Datadog

**Online Resources**:
- Martin Kleppmann's Blog
- High Scalability (highscalability.com)
- AWS Architecture Blog
- Engineering blogs: Uber, Netflix, Discord, Twitter

---

**Document Version**: 1.0  
**Last Updated**: November 2024  
**Author**: System Design Interview Prep Guide  
**Companion Document**: [Scaling Reads Guide](./scaling-reads-system-design-guide.md)
