# 🎯 Topic 50: PostgreSQL Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering PostgreSQL architecture, ACID transactions, isolation levels, indexing (B-tree, GIN, GiST), MVCC, WAL and replication, connection pooling (PgBouncer), partitioning, JSONB, full-text search, performance tuning, scaling strategies, and how to articulate PostgreSQL design decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [PostgreSQL Architecture](#postgresql-architecture)
3. [ACID Transactions and Isolation Levels](#acid-transactions-and-isolation-levels)
4. [MVCC — Multi-Version Concurrency Control](#mvcc--multi-version-concurrency-control)
5. [Indexing Deep Dive](#indexing-deep-dive)
6. [WAL and Replication](#wal-and-replication)
7. [Connection Pooling (PgBouncer)](#connection-pooling-pgbouncer)
8. [Table Partitioning](#table-partitioning)
9. [JSONB — Document Storage in SQL](#jsonb--document-storage-in-sql)
10. [Full-Text Search](#full-text-search)
11. [Performance Tuning](#performance-tuning)
12. [Scaling PostgreSQL](#scaling-postgresql)
13. [PostgreSQL vs Other Databases](#postgresql-vs-other-databases)
14. [Common Patterns in System Design](#common-patterns-in-system-design)
15. [Real-World Examples at Scale](#real-world-examples-at-scale)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**PostgreSQL** is the most versatile open-source relational database — the "Swiss Army Knife" of databases. It's the default choice in system design interviews when you need ACID transactions, complex queries with JOINs, and a battle-tested operational model.

```
Why PostgreSQL is the default database choice:

  ACID transactions:   Multi-table atomicity (booking + payment in one transaction)
  Complex queries:     JOINs, subqueries, window functions, CTEs, aggregations
  JSONB:               Document storage within a relational engine
  Full-text search:    Built-in search with ranking (avoids needing Elasticsearch for simple cases)
  Extensibility:       Extensions (PostGIS for geo, pg_trig for trigrams, timescaledb for time-series)
  Maturity:            30+ years, trillions of rows at Instagram, Uber, Stripe, Discord
  
  Interview rule: "Start with PostgreSQL unless you have a specific reason not to."
  Specific reasons to switch: >50K writes/sec, flexible schema per record, sub-ms latency needed.
```

---

## PostgreSQL Architecture

### Process Model

```
PostgreSQL uses a process-per-connection model:

  Client 1 → Postmaster → Fork → Backend Process 1 (dedicated to client 1)
  Client 2 → Postmaster → Fork → Backend Process 2 (dedicated to client 2)
  
  Each connection = 1 OS process (~5-10 MB RAM per connection)
  
  100 connections = 100 processes = ~1 GB RAM just for connections
  1,000 connections = 1,000 processes = ~10 GB RAM (+ context switching overhead)
  10,000 connections = UNUSABLE (too many processes, context switching destroys performance)
  
  Solution: Connection pooling (PgBouncer) — covered below.

Background processes:
  WAL Writer:      Flushes Write-Ahead Log to disk
  Background Writer: Writes dirty buffers from shared buffers to disk
  Autovacuum:      Cleans up dead tuples (MVCC garbage collection)
  Checkpoint:      Periodic full flush of all dirty pages to disk
  Stats Collector: Tracks query statistics for pg_stat_statements
```

### Shared Memory

```
shared_buffers: PostgreSQL's main in-memory cache (typically 25% of RAM)

  Query: SELECT * FROM users WHERE id = 123
  
  1. Check shared_buffers → page containing id=123 found? → CACHE HIT (< 0.1ms)
  2. Not in shared_buffers → read from OS page cache → HIT (< 1ms)
  3. Not in OS cache → read from disk → DISK I/O (2-10ms for SSD)
  
  Buffer hit ratio: CRITICAL metric
    > 99%: Excellent (almost all reads from RAM)
    95-99%: Good (occasional disk reads)
    < 95%: Problem (too many disk reads, need more RAM or better indexes)
  
  Check: SELECT pg_stat_get_buf_hit() / (pg_stat_get_buf_hit() + pg_stat_get_buf_read())

Typical configuration (64 GB RAM server):
  shared_buffers = 16 GB (25% of RAM)
  effective_cache_size = 48 GB (how much RAM OS uses for caching)
  work_mem = 256 MB (per-operation sort/hash memory)
  maintenance_work_mem = 2 GB (for VACUUM, CREATE INDEX)
```

---

## ACID Transactions and Isolation Levels

### ACID Properties

```
Atomicity:    All statements in a transaction succeed or all fail.
  BEGIN; UPDATE accounts SET balance = balance - 100 WHERE id = 'A';
        UPDATE accounts SET balance = balance + 100 WHERE id = 'B';
  COMMIT;  -- Both updates happen, or neither does.

Consistency:  Database moves from one valid state to another.
  CHECK (balance >= 0)  -- Balance can never go negative.
  FOREIGN KEY (user_id) REFERENCES users(id) -- No orphaned orders.

Isolation:    Concurrent transactions don't interfere with each other.
  Transaction A reads balance while Transaction B is updating it.
  Isolation level determines what A sees.

Durability:   Once COMMIT returns, data survives crashes.
  COMMIT → WAL flushed to disk → data is safe even if server crashes.
```

### Isolation Levels

```
READ UNCOMMITTED: (PostgreSQL treats as Read Committed)
  Can see uncommitted changes from other transactions.
  PostgreSQL doesn't truly support this — upgrades to Read Committed.

READ COMMITTED (default):
  Each statement sees data committed BEFORE the statement started.
  Different statements in same transaction may see different data.
  
  Transaction A:                    Transaction B:
  SELECT balance FROM accounts;     
  → balance = 100                   UPDATE accounts SET balance = 200;
                                    COMMIT;
  SELECT balance FROM accounts;
  → balance = 200 (sees B's commit!)
  
  Use: Default for most applications. Safe enough, good performance.

REPEATABLE READ:
  Transaction sees a SNAPSHOT from the start. No "phantom reads."
  
  Transaction A:                    Transaction B:
  BEGIN;
  SELECT balance FROM accounts;     
  → balance = 100                   UPDATE accounts SET balance = 200;
                                    COMMIT;
  SELECT balance FROM accounts;
  → balance = 100 (snapshot from BEGIN!)
  
  Use: Reports that need a consistent view of data across multiple queries.

SERIALIZABLE:
  Transactions execute as if they ran one at a time (serial order).
  PostgreSQL detects conflicts → aborts one transaction → application retries.
  
  Highest safety, lowest throughput (~10-30% overhead).
  
  Use: Financial transactions, booking systems where correctness is critical.
  Implementation: SSI (Serializable Snapshot Isolation) — lock-free!
```

### Choosing Isolation Level

```
For system design interviews:

  Default: READ COMMITTED (90% of operations)
    User profile reads, product catalog browsing, feed loading
    
  Upgrade to REPEATABLE READ when:
    Reports/analytics across multiple tables (consistent snapshot)
    Batch operations that must see consistent data
    
  Upgrade to SERIALIZABLE when:
    Money transfers (balance can't go negative)
    Seat booking (can't double-sell)
    Inventory decrement (exact count matters)
    
  Interview script:
  "I'd use Read Committed as the default — it handles 90% of our operations.
   For the payment transfer, I'd upgrade to Serializable to prevent the 
   balance from going negative due to concurrent withdrawals."
```

---

## MVCC — Multi-Version Concurrency Control

### How MVCC Works

```
PostgreSQL never overwrites data in place.
Instead, it creates a NEW VERSION of the row:

  UPDATE users SET name = 'Bob' WHERE id = 1;
  
  Before: Row version 1: {id: 1, name: 'Alice', xmin: 100, xmax: 0}
  After:  Row version 1: {id: 1, name: 'Alice', xmin: 100, xmax: 200} ← marked "dead"
          Row version 2: {id: 1, name: 'Bob', xmin: 200, xmax: 0}    ← new version

  xmin = transaction ID that created this row version
  xmax = transaction ID that deleted/updated this row version (0 = active)

Readers see: The row version valid for their snapshot (based on xmin/xmax and their txn ID)
Writers create: New row versions (never block readers!)

KEY BENEFIT: Readers never block writers. Writers never block readers.
  → High concurrency. No read locks. Reads are always consistent.
```

### VACUUM — The Garbage Collector

```
Problem: Dead row versions accumulate (old versions after UPDATE/DELETE).
  Without cleanup → table bloats → scans get slower → disk fills up.

VACUUM: Marks dead row versions as reusable space.
VACUUM FULL: Rewrites the entire table to reclaim space (locks table!).

Autovacuum: Runs automatically based on thresholds.
  autovacuum_vacuum_threshold = 50 (rows changed before triggering)
  autovacuum_vacuum_scale_factor = 0.2 (20% of table changed → vacuum)
  
  1M row table: Vacuum triggers after 200,050 changes (50 + 20% × 1M)

IMPORTANT: Never disable autovacuum! 
  Without vacuum → transaction ID wraparound → data corruption risk.
  
  Monitor: pg_stat_user_tables.n_dead_tup (dead tuples not yet vacuumed)
  Alert: If dead tuples > 10% of total tuples → autovacuum is falling behind.
```

---

## Indexing Deep Dive

### B-tree Index (Default)

```
CREATE INDEX idx_users_email ON users(email);

B-tree: Balanced tree structure. O(log N) lookups.
  1 billion rows: ~30 levels → 30 I/O operations max per lookup.
  With shared_buffers: Usually 1-3 I/O operations (upper levels cached).

Perfect for: =, <, >, <=, >=, BETWEEN, ORDER BY, LIKE 'prefix%'
Not for: LIKE '%suffix', full-text search, array containment

Composite indexes:
  CREATE INDEX idx_orders_user_date ON orders(user_id, created_at DESC);
  
  Supports:
    WHERE user_id = 123                          ✅ (uses first column)
    WHERE user_id = 123 AND created_at > '2025-03-01' ✅ (uses both columns)
    WHERE user_id = 123 ORDER BY created_at DESC  ✅ (index provides ordering!)
  
  Does NOT support:
    WHERE created_at > '2025-03-01'              ❌ (can't skip first column)
  
  RULE: Composite index columns go from most-selective to least-selective.
    (user_id, created_at) NOT (created_at, user_id) — user_id narrows more.
```

### Partial Indexes

```
CREATE INDEX idx_orders_pending ON orders(created_at) WHERE status = 'pending';

Only indexes rows matching the WHERE clause!

Without partial: Index contains ALL orders (10M rows, ~200 MB)
With partial: Index contains only pending orders (50K rows, ~1 MB)

Use case: "Find all pending orders" → scans tiny 1 MB index, not 200 MB.
  99.5% of orders are complete → why index them for this query?

Cost: Zero overhead for non-matching rows (not in the index = no maintenance).
```

### GIN Index (Inverted Index)

```
CREATE INDEX idx_products_tags ON products USING GIN(tags);

GIN: Generalized Inverted Index. Maps values → rows that contain them.

Use cases:
  JSONB containment: WHERE metadata @> '{"color": "red"}'
  Array containment: WHERE tags @> ARRAY['electronics', 'sale']
  Full-text search:  WHERE to_tsvector(description) @@ to_tsquery('pizza')
  Trigram similarity: WHERE name % 'piza' (fuzzy matching)

Performance: O(1) lookup per value in the inverted index.
  "Find products tagged 'electronics'" → GIN lookup → instant.
```

### Index-Only Scans

```
If the index CONTAINS all columns needed by the query:

  CREATE INDEX idx_users_email_name ON users(email, name);
  
  SELECT name FROM users WHERE email = 'alice@example.com';
  → Index-only scan! Never touches the table (heap) at all.
  → 10x faster than index scan + heap fetch.

INCLUDE columns (PostgreSQL 11+):
  CREATE INDEX idx_users_email ON users(email) INCLUDE (name, avatar_url);
  
  Index key: email (used for searching)
  Included: name, avatar_url (stored in index, not used for searching)
  
  SELECT name, avatar_url FROM users WHERE email = 'alice@example.com';
  → Index-only scan with included columns. Very fast.
```

### EXPLAIN ANALYZE

```
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 123 AND created_at > '2025-03-01';

Output:
  Index Scan using idx_orders_user_date on orders
    Index Cond: (user_id = 123 AND created_at > '2025-03-01')
    Rows Removed by Filter: 0
    Planning Time: 0.2 ms
    Execution Time: 0.5 ms    ← Fast! Index used correctly.

RED FLAGS in EXPLAIN:
  Seq Scan on large table     → Missing index! Add one.
  Rows Removed by Filter: 99% → Index not selective enough. Better index needed.
  Sort (external)             → work_mem too small. Data spills to disk.
  Nested Loop (large tables)  → Consider Hash Join. May need more work_mem.
```

---

## WAL and Replication

### Write-Ahead Log (WAL)

```
Every change is written to WAL BEFORE being applied to the actual table:

  UPDATE users SET name = 'Bob' WHERE id = 1;
  
  1. Write WAL record to WAL file (sequential write, very fast)
  2. Modify row in shared_buffers (in memory)
  3. COMMIT → fsync WAL to disk → return success to client
  4. Background writer eventually flushes dirty page to data file
  
  If crash between step 3 and 4:
    On restart: Replay WAL → reconstruct changes → data consistent.
    
  WAL is the foundation of:
    Durability (crash recovery)
    Replication (stream WAL to replicas)
    Point-in-time recovery (replay WAL to any timestamp)
```

### Streaming Replication

```
Primary → WAL stream → Standby (replica)

  Primary:  Accepts all reads and writes
  Standby:  Read-only, receives WAL stream, applies changes

  Replication lag: Usually < 100ms (same AZ), 1-5ms (same machine)
  
  Synchronous replication:
    Primary waits for standby to confirm WAL receipt before COMMIT returns.
    Zero data loss on primary failure (RPO = 0).
    Cost: +2-5ms per write (network round-trip to standby).
    
  Asynchronous replication (default):
    Primary commits immediately, standby applies later.
    Possible data loss: Last few ms of writes if primary crashes.
    Better performance (no wait for standby).

Read replicas for scaling reads:
  3 replicas → 4x read throughput (3 replicas + primary)
  Route read-only queries to replicas:
    Write queries → Primary (one connection pool)
    Read queries → Replicas (load-balanced connection pool)
```

### Logical Replication (CDC)

```
Logical replication sends DECODED changes (not raw WAL bytes):

  INSERT INTO users (id, name) VALUES (1, 'Alice')
  → Logical replication event: {table: "users", op: "INSERT", data: {id: 1, name: "Alice"}}

Use cases:
  Selective replication: Only replicate specific tables/columns
  Cross-version: Replicate between different PostgreSQL versions
  CDC to external systems: Stream changes to Kafka via Debezium
  
  PostgreSQL → Debezium → Kafka → Elasticsearch (search index)
  PostgreSQL → Debezium → Kafka → Redis (cache invalidation)
  PostgreSQL → Debezium → Kafka → DynamoDB (read-optimized view)
```

---

## Connection Pooling (PgBouncer)

### The Problem

```
Without pooling:
  1,000 API servers × 10 connections each = 10,000 PostgreSQL connections
  Each connection: 1 process, ~10 MB RAM
  Total: 100 GB RAM just for connections + massive context switching
  PostgreSQL max_connections default: 100 (way too low)
  Even at max_connections=5000: Performance degrades badly at >1,000 connections.

With PgBouncer:
  1,000 API servers → PgBouncer (100 pooled connections) → PostgreSQL
  PostgreSQL sees: 100 connections (not 10,000!)
  
  PgBouncer pooling modes:
    Session: One PG connection per client session (least aggressive)
    Transaction: One PG connection per transaction (most common, recommended)
    Statement: One PG connection per statement (most aggressive, limitations)
    
  Transaction pooling:
    Client A: BEGIN; SELECT...; UPDATE...; COMMIT; → uses PG connection #1
    Client A's transaction ends → connection #1 returned to pool
    Client B: BEGIN; SELECT...; → gets connection #1 (reused!)
    
    1,000 concurrent API connections → 100 PG connections → same throughput.
```

### Configuration

```
PgBouncer settings:
  pool_mode = transaction
  default_pool_size = 25         # Connections per database per user
  max_client_conn = 10000        # Max client connections to PgBouncer
  max_db_connections = 100       # Max connections to PostgreSQL
  
PostgreSQL settings:
  max_connections = 150          # PgBouncer pool (100) + admin (50)
  
Result:
  10,000 API server connections → PgBouncer → 100 PostgreSQL connections
  RAM saved: ~99 GB (9,900 fewer processes × 10 MB each)
  CPU saved: Minimal context switching (100 processes, not 10,000)
```

---

## Table Partitioning

### When to Partition

```
Partition when:
  Table exceeds 100 GB (indexes become slow, VACUUM takes hours)
  Queries always filter by partition key (date range, tenant_id)
  Need to drop old data efficiently (DROP PARTITION vs DELETE millions of rows)

Types:
  Range partitioning:  By date range (most common)
  List partitioning:   By discrete values (region, status)
  Hash partitioning:   By hash of column (even distribution)
```

### Range Partitioning by Date

```sql
CREATE TABLE events (
    id BIGSERIAL,
    created_at TIMESTAMPTZ NOT NULL,
    event_type TEXT,
    data JSONB
) PARTITION BY RANGE (created_at);

CREATE TABLE events_2025_03 PARTITION OF events
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE events_2025_04 PARTITION OF events
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');

-- Query with partition pruning:
SELECT * FROM events WHERE created_at BETWEEN '2025-03-15' AND '2025-03-20';
-- PostgreSQL ONLY scans events_2025_03 partition! Others pruned automatically.

-- Drop old data (instant, no vacuum needed):
DROP TABLE events_2024_01;
-- vs DELETE FROM events WHERE created_at < '2024-02-01'; → slow, vacuum needed
```

---

## JSONB — Document Storage in SQL

### Best of Both Worlds

```
PostgreSQL JSONB: Store JSON documents WITHIN a relational database.
  Get document flexibility WITHOUT giving up ACID, JOINs, and SQL.

CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    price DECIMAL NOT NULL,
    metadata JSONB      -- Flexible per-product attributes
);

-- Laptop
INSERT INTO products (name, price, metadata) VALUES (
    'ThinkPad X1', 1499.99,
    '{"processor": "i7", "ram_gb": 16, "screen_size": "14 inch"}'
);

-- T-Shirt
INSERT INTO products (name, price, metadata) VALUES (
    'Classic Tee', 29.99,
    '{"color": "Navy", "sizes": ["S", "M", "L", "XL"], "fabric": "Cotton"}'
);

-- Query JSONB fields:
SELECT name FROM products WHERE metadata->>'processor' = 'i7';
SELECT name FROM products WHERE metadata @> '{"color": "Navy"}';

-- Index JSONB:
CREATE INDEX idx_products_metadata ON products USING GIN(metadata);
-- Now JSONB containment queries use the index → fast!

Use case: Product catalogs where attributes vary per category.
  Relational columns for common fields (name, price, status).
  JSONB for category-specific attributes (processor, fabric, isbn).
```

---

## Full-Text Search

### Built-In Search

```sql
-- Create a text search index:
ALTER TABLE articles ADD COLUMN search_vector tsvector;
UPDATE articles SET search_vector = to_tsvector('english', title || ' ' || body);
CREATE INDEX idx_articles_search ON articles USING GIN(search_vector);

-- Search with ranking:
SELECT title, ts_rank(search_vector, query) AS rank
FROM articles, to_tsquery('english', 'postgres & performance') AS query
WHERE search_vector @@ query
ORDER BY rank DESC
LIMIT 10;

Features:
  ✅ Stemming: "running" matches "run", "runs", "runner"
  ✅ Stop words: Ignores "the", "a", "is"
  ✅ Ranking: BM25-like relevance scoring
  ✅ Language support: English, Spanish, German, etc.
  ✅ Trigram matching: Fuzzy search for misspellings

When PostgreSQL FTS is sufficient:
  < 10M documents, simple search needs, no faceted search
  Avoids adding Elasticsearch as a separate component!

When to add Elasticsearch:
  > 10M documents, complex relevance tuning, faceted search,
  aggregation-heavy analytics, real-time indexing requirements
```

---

## Performance Tuning

### Key Configuration Parameters

```
shared_buffers = 16GB           # 25% of RAM (main cache)
effective_cache_size = 48GB     # Total RAM available for caching
work_mem = 256MB                # Per-operation sort/hash memory
maintenance_work_mem = 2GB      # For VACUUM, CREATE INDEX
wal_buffers = 64MB              # WAL write buffer
max_connections = 150           # Use PgBouncer! Not 10,000.
random_page_cost = 1.1          # For SSD (default 4.0 is for HDD)
effective_io_concurrency = 200  # For SSD (default 1 is for HDD)
```

### Query Optimization Checklist

```
1. EXPLAIN ANALYZE every slow query
2. Add indexes for WHERE, JOIN, ORDER BY columns
3. Use composite indexes for multi-column filters
4. Use partial indexes for filtered queries (WHERE status = 'active')
5. Increase work_mem if sorts spill to disk
6. Use LIMIT with ORDER BY (avoids full sort)
7. Use EXISTS instead of IN for subqueries
8. Batch INSERTs (COPY or multi-row INSERT)
9. Use prepared statements (avoid repeated query parsing)
10. Monitor pg_stat_statements for top slow queries
```

### Performance Numbers

```
Single PostgreSQL instance (modern hardware, SSD):
  Simple SELECT by PK:    0.1-0.5ms  (index lookup + buffer hit)
  Complex JOIN (3 tables): 1-10ms    (with proper indexes)
  Write (INSERT/UPDATE):  0.5-2ms    (WAL + buffer)
  
  Read throughput:  10K-50K QPS (depends on query complexity)
  Write throughput: 10K-50K WPS (depends on index count, row size)
  
With 3 read replicas:
  Read throughput: 40K-200K QPS (4x scaling)
  
With Redis cache (95% hit rate):
  Effective throughput: 200K+ QPS (95% from Redis, 5% from PG)
```

---

## Scaling PostgreSQL

### Vertical Scaling (Scale Up)

```
Stage 1: Optimize queries and indexes (free!)
Stage 2: Increase RAM (shared_buffers, effective_cache_size)
Stage 3: Use SSD (random_page_cost = 1.1)
Stage 4: Bigger instance (more CPU cores, RAM, IOPS)

Practical limit: ~50K writes/sec on a single master.
  Beyond this: Need horizontal scaling (read replicas, sharding).
```

### Read Replicas (Scale Reads)

```
1 primary + N read replicas:
  Primary: All writes + some reads
  Replicas: Read-only queries (load-balanced)
  
  3 replicas: ~4x read throughput
  10 replicas: ~11x read throughput (diminishing returns from replication lag)
  
  Route writes to primary, reads to replicas:
    Application-level routing
    OR PgBouncer with read/write splitting
    OR AWS RDS Proxy with reader endpoint
```

### Application-Level Sharding

```
When single master can't handle write volume (>50K writes/sec):

  Shard by user_id:
    Users A-M → PostgreSQL Shard 1
    Users N-Z → PostgreSQL Shard 2
    
  Application routing:
    shard_id = hash(user_id) % num_shards
    connection = shard_connections[shard_id]
    
  Challenges:
    Cross-shard JOINs: Impossible (application must aggregate)
    Cross-shard transactions: Need Saga pattern
    Rebalancing: Moving users between shards is complex
    
  Alternatives:
    Citus (distributed PostgreSQL): Auto-sharding with SQL compatibility
    CockroachDB: Distributed SQL (PostgreSQL-compatible wire protocol)
    Amazon Aurora: Cloud-native, up to 15 read replicas, faster failover
```

---

## PostgreSQL vs Other Databases

| Aspect | PostgreSQL | MySQL | DynamoDB | MongoDB |
|---|---|---|---|---|
| **Model** | Relational + JSONB | Relational | Key-value + document | Document |
| **ACID** | Full (all isolation levels) | Full (InnoDB) | Limited (100 items) | Limited (4.0+) |
| **JOINs** | Powerful (10+ table JOINs) | Good | None | Limited ($lookup) |
| **JSON** | JSONB (indexed, queryable) | JSON (less capable) | Native (document model) | Native (BSON) |
| **Full-text search** | Built-in (good for basic) | Built-in (basic) | None | Atlas Search |
| **Replication** | Streaming + logical | Binlog | Global Tables (managed) | Replica sets |
| **Scaling writes** | ~50K/sec (vertical) | ~40K/sec (vertical) | Virtually unlimited | Sharded |
| **Extensions** | Rich (PostGIS, TimescaleDB) | Limited | N/A | Limited |
| **Best for** | Complex queries, ACID, versatile | Simple reads, mature replication | Serverless, key-value | Flexible schema |

---

## Common Patterns in System Design

### Pattern 1: Source of Truth + Derived Views

```
PostgreSQL → CDC (Debezium) → Kafka → {Elasticsearch, Redis, DynamoDB}

PostgreSQL: ACID source of truth for orders, users, payments
Elasticsearch: Search index (product search, order search)
Redis: Cache for hot data (user profiles, session data)
DynamoDB: Read-optimized for mobile API (denormalized)

If any derived view is corrupted → rebuild from PostgreSQL.
```

### Pattern 2: Optimistic Locking

```sql
-- Read with version:
SELECT id, name, balance, version FROM accounts WHERE id = 123;
-- version = 5, balance = 1000

-- Update with version check:
UPDATE accounts SET balance = 900, version = 6 
WHERE id = 123 AND version = 5;
-- Returns 1 row affected → success!
-- Returns 0 rows affected → someone else updated → retry!
```

### Pattern 3: Advisory Locks (Distributed Lock Alternative)

```sql
-- Acquire lock on resource 12345:
SELECT pg_advisory_lock(12345);
-- Process order 12345...
SELECT pg_advisory_unlock(12345);

-- Non-blocking try:
SELECT pg_try_advisory_lock(12345);
-- Returns true if acquired, false if already held.

Use: Lightweight distributed lock within PostgreSQL.
  No need for Redis/ZooKeeper for simple coordination.
  But: Only works within the same PostgreSQL instance.
```

---

## Real-World Examples at Scale

### Instagram — PostgreSQL at Billions of Rows

```
Instagram runs on PostgreSQL (sharded):
  Billions of rows across multiple shards
  Sharded by user_id
  Uses pgpartman for table partitioning
  Citus for some distributed query needs
```

### Stripe — PostgreSQL for Financial Data

```
Stripe processes billions in payments on PostgreSQL:
  ACID transactions for money movement (non-negotiable)
  Serializable isolation for balance updates
  Logical replication for analytics pipeline
  Application-level sharding by merchant
```

### Discord — PostgreSQL + Cassandra

```
Discord uses PostgreSQL for:
  User accounts, guilds, channels (relational data needing ACID)
  
Cassandra/ScyllaDB for:
  Messages (write-heavy, time-series, billions of messages)
  
Pattern: PostgreSQL for metadata, Cassandra for high-volume data.
```

---

## Interview Talking Points & Scripts

### Why PostgreSQL

> *"I'd start with PostgreSQL as the source of truth because we need ACID transactions for the booking flow — reserving the seat, charging payment, and creating the confirmation must all succeed or all fail atomically. PostgreSQL handles this natively with a single BEGIN/COMMIT. For the read-heavy product browsing, I'd add a Redis cache with a 95% hit rate, reducing PostgreSQL load by 20x."*

### Scaling Strategy

> *"At our current 10K QPS, a single PostgreSQL instance handles it comfortably. As we grow to 50K reads/sec, I'd add 3 read replicas — routing write queries to the primary and read queries to replicas via PgBouncer. At 200K reads/sec, I'd add Redis caching. If writes eventually exceed 50K/sec, I'd shard by user_id using application-level routing."*

### Indexing

> *"I'd create a composite index on (user_id, created_at DESC) for the 'get recent orders' query. This lets PostgreSQL do an index-only scan — it finds the user's orders AND returns them sorted by date in a single index traversal, without touching the main table. For the product search, I'd use a GIN index on the JSONB metadata column to support flexible attribute queries."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "PostgreSQL can't scale"
**Wrong**: Instagram, Stripe, and Discord run PostgreSQL at massive scale. With read replicas + caching + sharding, PostgreSQL handles billions of rows.

### ❌ Mistake 2: Not mentioning connection pooling
**Wrong**: Direct connections to PostgreSQL at scale = process explosion. Always mention PgBouncer.

### ❌ Mistake 3: Using SELECT * everywhere
**Wrong**: Fetches unnecessary columns. Use specific column lists for index-only scans.

### ❌ Mistake 4: Not mentioning isolation levels
**Wrong**: Saying "I'd use transactions" without specifying isolation level. Read Committed for most, Serializable for financial.

### ❌ Mistake 5: Forgetting VACUUM
**Wrong**: PostgreSQL's MVCC creates dead tuples. Without autovacuum, tables bloat and performance degrades.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               POSTGRESQL DEEP DIVE CHEAT SHEET                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  DEFAULT CHOICE: "Start with PostgreSQL unless you need      │
│    >50K writes/sec, flexible schema, or sub-ms latency."     │
│                                                              │
│  ACID: Full transactions, all isolation levels               │
│    Read Committed: Default (90% of operations)               │
│    Serializable: Financial/booking (correctness critical)    │
│                                                              │
│  MVCC: Readers never block writers. Writers never block readers│
│    VACUUM: Essential garbage collection (never disable!)     │
│                                                              │
│  INDEXES:                                                    │
│    B-tree: Default, O(log N), perfect for =, <, >, ORDER BY │
│    GIN: Inverted index for JSONB, arrays, full-text search   │
│    Partial: Index only matching rows (WHERE status='active') │
│    Composite: (user_id, created_at) for multi-column queries │
│                                                              │
│  REPLICATION:                                                │
│    Streaming: WAL-based, < 100ms lag, read replicas          │
│    Logical: CDC for Kafka/Elasticsearch/Redis sync           │
│    3 replicas = 4x read throughput                           │
│                                                              │
│  CONNECTION POOLING (PgBouncer):                             │
│    10,000 app connections → 100 PG connections               │
│    Transaction pooling mode (recommended)                    │
│    Saves ~99% of connection RAM overhead                     │
│                                                              │
│  PARTITIONING: By date range for time-series (>100GB tables) │
│  JSONB: Document flexibility within relational engine        │
│  FULL-TEXT SEARCH: Built-in, good for <10M documents         │
│                                                              │
│  SCALING STAGES:                                             │
│    1. Optimize indexes + queries (free)                      │
│    2. Read replicas (4x read throughput)                     │
│    3. Redis cache (20x effective read throughput)            │
│    4. PgBouncer (handle 10K+ connections)                    │
│    5. Partitioning (manage large tables)                     │
│    6. Application sharding (>50K writes/sec)                 │
│                                                              │
│  PERFORMANCE:                                                │
│    PK lookup: 0.1-0.5ms. JOIN (3 tables): 1-10ms.           │
│    Reads: 10-50K QPS. Writes: 10-50K WPS.                   │
│    With replicas + cache: 200K+ effective QPS.               │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 2: Database Selection & Data Modeling** — When to choose PostgreSQL vs others
- **Topic 3: SQL vs NoSQL** — PostgreSQL as the SQL champion
- **Topic 5: Caching Strategies** — Redis cache in front of PostgreSQL
- **Topic 13: Replication** — PostgreSQL streaming replication
- **Topic 21: Concurrency Control** — MVCC and isolation levels
- **Topic 36: Distributed Transactions** — PostgreSQL ACID vs Saga pattern
- **Topic 49: DynamoDB Deep Dive** — PostgreSQL vs DynamoDB comparison

---

*This document is part of the System Design Interview Deep Dive series.*
