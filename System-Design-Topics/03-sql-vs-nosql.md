# 🎯 Topic 3: SQL vs NoSQL

> **System Design Interview — Deep Dive**
> A comprehensive guide covering every dimension of the SQL vs NoSQL decision — when to choose which, the real tradeoffs (not the myths), how modern systems combine both, and how to articulate these choices with precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Fundamental Differences](#fundamental-differences)
3. [SQL Databases — Strengths and Limitations](#sql-databases--strengths-and-limitations)
4. [NoSQL Databases — Categories and Capabilities](#nosql-databases--categories-and-capabilities)
5. [The Real Decision Framework](#the-real-decision-framework)
6. [Myth-Busting: What "NoSQL Is Faster" Actually Means](#myth-busting-what-nosql-is-faster-actually-means)
7. [Scaling SQL vs Scaling NoSQL](#scaling-sql-vs-scaling-nosql)
8. [ACID vs BASE — Transaction Models Compared](#acid-vs-base--transaction-models-compared)
9. [Schema Design Philosophy: Schema-on-Write vs Schema-on-Read](#schema-design-philosophy-schema-on-write-vs-schema-on-read)
10. [The Hybrid Approach: SQL + NoSQL Together](#the-hybrid-approach-sql--nosql-together)
11. [CDC Pattern: SQL as Source of Truth, NoSQL as Read Views](#cdc-pattern-sql-as-source-of-truth-nosql-as-read-views)
12. [Performance Benchmarks and Real Numbers](#performance-benchmarks-and-real-numbers)
13. [Operational Complexity Comparison](#operational-complexity-comparison)
14. [Migration Strategies: SQL ↔ NoSQL](#migration-strategies-sql--nosql)
15. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
16. [Common Interview Mistakes](#common-interview-mistakes)
17. [Decision Matrix for Popular System Design Problems](#decision-matrix-for-popular-system-design-problems)
18. [Deep Dive: When Companies Chose SQL Over NoSQL (And Vice Versa)](#deep-dive-when-companies-chose-sql-over-nosql-and-vice-versa)
19. [The Future: NewSQL and Converging Technologies](#the-future-newsql-and-converging-technologies)
20. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

The SQL vs NoSQL decision is not about which technology is "better" — it's about which **tradeoff profile** matches your specific requirements:

- **SQL** gives you: ACID transactions, JOINs, strong consistency, mature tooling, and a well-understood operational model. It costs you: horizontal write scaling, schema flexibility, and some raw throughput for simple operations.

- **NoSQL** gives you: horizontal write scaling, schema flexibility, and optimized performance for specific access patterns. It costs you: ACID transactions, JOINs, and pushes more complexity into the application layer.

**The mature answer**: Most production systems use **both** — SQL for the transactional core (where correctness matters) and NoSQL for the read-heavy, scale-intensive edges (where performance and availability matter).

---

## Fundamental Differences

| Dimension | SQL (Relational) | NoSQL (Non-Relational) |
|---|---|---|
| **Data Model** | Tables with rows and columns, fixed schema | Documents, key-value, wide-column, graph |
| **Schema** | Schema-on-write (defined upfront, enforced) | Schema-on-read (flexible, per-document) |
| **Relationships** | JOINs (native, efficient) | Denormalization or application-level JOINs |
| **Transactions** | Multi-table ACID transactions | Single-document atomic (most); limited multi-doc |
| **Consistency** | Strong by default | Tunable (eventual to strong) |
| **Scaling** | Vertical + read replicas | Horizontal (sharding built-in) |
| **Write Throughput** | ~50K/sec (single master) | ~100K+/sec per node, linear scaling |
| **Query Language** | SQL (standardized, powerful) | Varies: CQL, MongoDB query, DynamoDB API |
| **Best For** | Transactional workloads, complex queries | High-throughput, flexible schema, specific patterns |

---

## SQL Databases — Strengths and Limitations

### Strengths

#### 1. ACID Transactions
The single biggest advantage of SQL databases. When you need to atomically update multiple tables:

```sql
BEGIN;
  UPDATE accounts SET balance = balance - 500 WHERE account_id = 'A';
  UPDATE accounts SET balance = balance + 500 WHERE account_id = 'B';
  INSERT INTO transfers (from_acct, to_acct, amount, timestamp)
    VALUES ('A', 'B', 500, NOW());
COMMIT;
```

If **any** statement fails, the **entire** transaction rolls back. The money is never "in transit" — it's either in Account A or Account B, never in neither or both.

**Why this matters**: With NoSQL, you'd need to implement this coordination in application code:
1. Debit Account A → success
2. Credit Account B → **fails** (network error)
3. Now Account A is debited but Account B isn't credited → inconsistent state
4. Need manual compensation logic, retry mechanisms, idempotency checks...

For financial systems, health records, booking systems — anywhere data integrity is non-negotiable — ACID transactions are worth the constraints they impose.

#### 2. JOINs and Complex Queries

SQL is the most expressive query language for relational data:

```sql
-- "Find the top 10 customers by revenue who joined in 2024,
--  along with their most recent order and the total number of items"
SELECT
    u.name,
    u.email,
    COUNT(DISTINCT o.order_id) AS order_count,
    SUM(oi.quantity * oi.price) AS total_revenue,
    MAX(o.created_at) AS last_order
FROM users u
JOIN orders o ON u.user_id = o.user_id
JOIN order_items oi ON o.order_id = oi.order_id
WHERE u.created_at >= '2024-01-01'
GROUP BY u.user_id, u.name, u.email
ORDER BY total_revenue DESC
LIMIT 10;
```

This query touches 3 tables, performs aggregations, filtering, and sorting — all in one declarative statement. The database optimizer decides the best execution plan. In NoSQL, you'd need multiple round trips, application-level joins, and manual aggregation.

#### 3. Referential Integrity

Foreign keys prevent orphaned records:
```sql
CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(user_id) ON DELETE CASCADE
);
-- Cannot create an order for a non-existent user
-- Deleting a user cascades to their orders
```

In NoSQL, referential integrity is the application's responsibility. If you forget to clean up orders when deleting a user, you get orphaned data.

#### 4. Mature Ecosystem

- **30+ years of optimization** in query planning and execution.
- **Standard SQL**: Skills transfer across PostgreSQL, MySQL, Oracle, SQL Server.
- **Tooling**: pgAdmin, DataGrip, dbt, Liquibase, Flyway for migrations.
- **Battle-tested**: PostgreSQL handles trillions of rows at companies like Instagram, Uber, and Stripe.

### Limitations

#### 1. Horizontal Write Scaling

A single PostgreSQL master handles ~50K writes/sec. Beyond that, you need:
- **Sharding**: Application-managed partitioning across multiple database instances. Complex, error-prone, and makes JOINs across shards extremely expensive.
- **Citus (distributed PostgreSQL)**: Adds horizontal scaling but with limitations on cross-shard transactions.

NoSQL databases like Cassandra scale writes linearly by adding nodes — no application-level sharding required.

#### 2. Schema Rigidity

Every ALTER TABLE on a large table can be expensive:
```sql
-- Adding a column to a 1-billion-row table
ALTER TABLE tweets ADD COLUMN sentiment_score FLOAT;
-- In older PostgreSQL versions, this could lock the table for minutes
-- Modern PostgreSQL (11+) handles most ALTERs without locking
```

More importantly, when your data model is inherently heterogeneous (product catalogs with different attributes per category), the relational model forces awkward patterns:

**Sparse table** (many NULLs):
```sql
CREATE TABLE products (
    product_id INT,
    name TEXT,
    price DECIMAL,
    -- Electronics fields
    processor TEXT,     -- NULL for clothing
    ram_gb INT,         -- NULL for clothing
    -- Clothing fields
    fabric TEXT,        -- NULL for electronics
    size TEXT,          -- NULL for electronics
    -- Book fields
    isbn TEXT,          -- NULL for non-books
    author TEXT         -- NULL for non-books
);
```

**EAV (Entity-Attribute-Value)** (even worse):
```sql
CREATE TABLE product_attributes (
    product_id INT,
    attribute_name TEXT,
    attribute_value TEXT
);
-- No type safety, no indexing efficiency, terrible query performance
```

#### 3. No Built-In Horizontal Read Scaling for Writes

Read replicas scale reads but not writes. All writes go to one master. If your write volume exceeds the master's capacity, SQL doesn't have a native solution (unlike Cassandra's leaderless architecture where every node accepts writes).

---

## NoSQL Databases — Categories and Capabilities

### Category 1: Key-Value Stores (Redis, DynamoDB, Memcached)

**Data model**: Simple key → value mapping.

**Access pattern**: Point lookups by key. No JOINs, no complex queries.

**Strengths**:
- Fastest reads possible (O(1) hash lookup).
- Sub-millisecond latency (Redis).
- Natural caching layer.
- Simple to operate and scale.

**Limitations**:
- No complex queries.
- No relationships between keys.
- Limited aggregate operations.

**Best for**: Caching, session storage, rate limiting, leaderboards, simple lookups.

### Category 2: Document Stores (MongoDB, DynamoDB, CouchDB)

**Data model**: Self-contained JSON/BSON documents with nested structures.

**Access pattern**: Document CRUD by primary key; secondary indexes for queries.

**Strengths**:
- Flexible schema: each document can have different fields.
- Atomic operations on a single document.
- Nested data avoids JOINs for self-contained entities.
- MongoDB 4.0+ supports multi-document transactions (with caveats).

**Limitations**:
- Cross-document JOINs are expensive or impossible.
- Denormalization leads to data duplication and update complexity.
- Multi-document transactions are slower than single-document operations.

**Best for**: Product catalogs, user profiles, content management, configuration storage.

### Category 3: Wide-Column Stores (Cassandra, HBase, ScyllaDB)

**Data model**: Rows with dynamic columns, organized by partition key and clustering key.

**Access pattern**: Partition-key lookups with range scans within a partition.

**Strengths**:
- Linear horizontal write scaling.
- Excellent for time-series and event data.
- Tunable consistency (from ONE to ALL).
- No single point of failure (leaderless).

**Limitations**:
- No JOINs.
- Query model is very rigid — must query by partition key.
- Cross-partition queries are expensive (scatter-gather).
- Data modeling requires deep understanding of access patterns.

**Best for**: Messaging, event stores, time-series metrics, IoT data, audit logs.

### Category 4: Graph Databases (Neo4j, Amazon Neptune, ArangoDB)

**Data model**: Nodes and edges with properties.

**Access pattern**: Graph traversals — finding paths, patterns, and relationships.

**Strengths**:
- O(1) per hop for relationship traversal (vs O(N) for SQL JOINs).
- Natural modeling for social networks, fraud detection, recommendations.
- Expressive query languages (Cypher, Gremlin, SPARQL).

**Limitations**:
- Not suitable for non-graph workloads.
- Horizontal scaling is harder than document or wide-column stores.
- Smaller ecosystem and community than relational databases.

**Best for**: Social graphs, fraud detection, recommendation engines, knowledge graphs.

---

## The Real Decision Framework

### When SQL Is the Clear Winner

| Scenario | Why SQL? |
|---|---|
| **Multi-table transactions** | ACID atomicity across multiple entities is built-in |
| **Complex queries with JOINs** | SQL optimizer handles 3-5 table JOINs efficiently |
| **Strong consistency required** | Serializable isolation prevents all anomalies |
| **Regulatory/compliance needs** | Auditable, well-understood consistency guarantees |
| **Schema is well-known and stable** | Relational model with constraints catches bugs early |
| **Team expertise is SQL** | Reduced learning curve, faster development |
| **Data volume < 1 TB** | PostgreSQL handles this effortlessly on one instance |
| **Write volume < 50K/sec** | Single PostgreSQL master is sufficient |

### When NoSQL Is the Clear Winner

| Scenario | Why NoSQL? |
|---|---|
| **Write throughput > 50K/sec** | Cassandra/DynamoDB scale writes horizontally |
| **Schema varies per record** | Document model handles heterogeneous data naturally |
| **Simple access patterns** | Key-value lookups don't need SQL's power |
| **Sub-millisecond latency** | Redis operates in-memory, far faster than disk-based SQL |
| **Horizontal scaling is primary concern** | NoSQL is designed for it; SQL bolts it on |
| **Eventual consistency is acceptable** | NoSQL can optimize for availability and throughput |
| **Time-series / event data** | Wide-column stores handle append-heavy workloads efficiently |
| **Graph traversals** | Neo4j outperforms SQL JOINs by orders of magnitude |

### When Either Could Work (And the Tiebreaker)

For many systems, both SQL and NoSQL are viable. The tiebreaker:

1. **If the team knows SQL well and the scale is < 50K writes/sec** → SQL. Operational simplicity wins.
2. **If the data model is naturally non-relational** → NoSQL. Don't force relational modeling.
3. **If you need both transactional integrity AND high throughput** → Use both. SQL for the source of truth, NoSQL for derived views.

---

## Myth-Busting: What "NoSQL Is Faster" Actually Means

### The Myth
"NoSQL databases are faster than SQL databases."

### The Reality

**Per-query performance**: A well-indexed PostgreSQL query returns in 0.5-5ms. A DynamoDB point read returns in 1-5ms. For single-record lookups, they're comparable.

**The actual difference is aggregate throughput**, not per-query speed:

```
PostgreSQL single master:
  - Max write throughput: ~50K writes/sec
  - Bottleneck: single master accepts all writes
  - Scaling: vertical (bigger machine) or complex application sharding

Cassandra cluster (10 nodes):
  - Max write throughput: ~1M writes/sec (100K/node × 10 nodes)
  - No bottleneck: every node accepts writes (leaderless)
  - Scaling: add more nodes (linear throughput increase)
```

**Interview talking point**:
> *"The 'NoSQL is faster' claim is misleading. A well-indexed PostgreSQL query on a properly sized instance returns in 0.5ms. What NoSQL actually gives you is horizontal write scalability — when you need 500K writes/sec and a single PostgreSQL master maxes out at 50K writes/sec, Cassandra's shared-nothing architecture lets you add nodes linearly. Speed per query isn't the differentiator; aggregate write throughput is."*

### Where NoSQL IS Actually Faster

1. **Redis vs PostgreSQL for cache lookups**: 0.1ms vs 2ms. Redis is 20x faster because it's in-memory.
2. **Cassandra partition scan vs PostgreSQL table scan**: For time-series data partitioned correctly, Cassandra reads sequentially within a partition. PostgreSQL would need to scan more data.
3. **DynamoDB single-item lookup vs PostgreSQL**: At extreme scale (>100K QPS), DynamoDB maintains consistent single-digit ms latency because it's built for distributed key-value access.

### Where SQL IS Actually Faster

1. **Complex analytical queries**: `SELECT ... JOIN ... GROUP BY ... HAVING ... ORDER BY` — PostgreSQL's query optimizer handles this much better than application-level aggregation over NoSQL.
2. **Multi-entity transactions**: One `COMMIT` in PostgreSQL vs multiple round trips and coordination in NoSQL.
3. **Ad-hoc queries**: SQL's declarative nature means new query patterns don't require new indexes or table designs.

---

## Scaling SQL vs Scaling NoSQL

### Scaling SQL

```
Stage 1: Single Instance
┌──────────────┐
│  PostgreSQL  │  Handles: ~10K QPS
│   (Master)   │  Cost: $500/month
└──────────────┘

Stage 2: Read Replicas
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Master     │───│  Replica 1   │───│  Replica 2   │
│  (writes)    │   │  (reads)     │   │  (reads)     │
└──────────────┘   └──────────────┘   └──────────────┘
Handles: ~30K read QPS + 10K write QPS
Cost: $1,500/month

Stage 3: Caching + Replicas
┌──────────────┐
│    Redis     │  Cache hit ratio: 95%
│   (cache)    │  Handles: 200K read QPS
└──────┬───────┘
       │ 5% cache miss
┌──────┴───────┐   ┌──────────────┐
│   Master     │───│  Replica     │
│  (writes)    │   │  (reads)     │
└──────────────┘   └──────────────┘
Handles: 200K read QPS + 10K write QPS
Cost: $2,000/month

Stage 4: Sharding (Complex)
┌───────────┐  ┌───────────┐  ┌───────────┐
│  Shard 1  │  │  Shard 2  │  │  Shard 3  │
│ users A-H │  │ users I-P │  │ users Q-Z │
└───────────┘  └───────────┘  └───────────┘
Handles: ~150K write QPS total
Cost: Significant complexity + $5,000+/month
Cross-shard JOINs: Very expensive
Cross-shard transactions: Extremely expensive
```

### Scaling NoSQL (Cassandra Example)

```
Stage 1: Small Cluster
┌────┐ ┌────┐ ┌────┐
│ N1 │ │ N2 │ │ N3 │  3 nodes
└────┘ └────┘ └────┘
Handles: ~300K write QPS
Cost: $1,500/month

Stage 2: Medium Cluster (just add nodes)
┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐
│ N1 │ │ N2 │ │ N3 │ │ N4 │ │ N5 │ │ N6 │  6 nodes
└────┘ └────┘ └────┘ └────┘ └────┘ └────┘
Handles: ~600K write QPS
Cost: $3,000/month

Stage 3: Large Cluster (keep adding)
┌────┐ ┌────┐ ┌────┐ ... ┌─────┐
│ N1 │ │ N2 │ │ N3 │     │ N20 │  20 nodes
└────┘ └────┘ └────┘     └─────┘
Handles: ~2M write QPS
Cost: $10,000/month
No schema changes, no application changes, just more nodes
```

**The key insight**: SQL scaling is **stepwise with complexity jumps** (each stage requires architectural changes). NoSQL scaling is **linear and operational** (add nodes, no code changes).

---

## ACID vs BASE — Transaction Models Compared

### ACID (SQL Default)

| Property | Meaning | Example |
|---|---|---|
| **Atomicity** | All or nothing — if any part fails, everything rolls back | Transfer $500: both debit and credit succeed, or neither does |
| **Consistency** | Database moves from one valid state to another | Balance can never be negative (check constraint) |
| **Isolation** | Concurrent transactions don't interfere with each other | Two concurrent reads of the same balance see consistent values |
| **Durability** | Committed data survives crashes | After COMMIT, data is on disk even if power fails |

### BASE (NoSQL Default)

| Property | Meaning | Example |
|---|---|---|
| **Basically Available** | The system guarantees availability (reads always succeed) | Shopping cart is always readable even during network issues |
| **Soft state** | The system's state may change over time without input | Like count may temporarily differ across replicas |
| **Eventually consistent** | Given enough time, all replicas converge | After a write to one Cassandra node, all 3 replicas sync within 100ms |

### The Practical Difference

**ACID (PostgreSQL)**:
```sql
-- User A: Transfer $500 from checking to savings
BEGIN;
  UPDATE accounts SET balance = balance - 500 WHERE id = 'checking_A';
  UPDATE accounts SET balance = balance + 500 WHERE id = 'savings_A';
COMMIT;
-- Guaranteed: Both updates happen or neither does
-- Guaranteed: No other transaction sees the intermediate state
```

**BASE (Cassandra)**:
```
-- User A: Transfer $500 from checking to savings
-- Step 1: Debit checking
UPDATE checking SET balance = balance - 500 WHERE user = 'A';
-- Step 2: Credit savings
UPDATE savings SET balance = balance + 500 WHERE user = 'A';

-- Problem: If Step 1 succeeds but Step 2 fails:
-- - Checking is debited
-- - Savings is not credited
-- - $500 is "lost" until we detect and fix the inconsistency
-- - Application must handle this with retries, compensation, idempotency
```

**Interview talking point**:
> *"For a financial system, I'd always choose SQL — specifically PostgreSQL with serializable isolation. When transferring $500 from Account A to Account B, I need a guarantee that the debit and credit happen atomically. With eventual consistency, there's a window where the $500 has left Account A but hasn't arrived at Account B — that's a bug report waiting to happen, and in regulated finance, a compliance violation."*

---

## Schema Design Philosophy: Schema-on-Write vs Schema-on-Read

### Schema-on-Write (SQL)

The schema is defined **before** data is written. The database **enforces** the schema on every write.

```sql
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    age INT CHECK (age >= 0 AND age <= 150),
    created_at TIMESTAMP DEFAULT NOW()
);

-- This INSERT fails because email is NULL (violates NOT NULL constraint)
INSERT INTO users (name, age) VALUES ('Alice', 30);
-- ERROR: null value in column "email" violates not-null constraint
```

**Benefits**: Bugs caught at write time. Data is always valid. Schema serves as documentation.

**Cost**: Schema changes require migrations. Heterogeneous data is awkward.

### Schema-on-Read (NoSQL)

No schema is enforced at write time. The application interprets the data at read time.

```javascript
// Document 1: Full profile
{ "user_id": "1", "email": "alice@example.com", "name": "Alice", "age": 30 }

// Document 2: Partial profile (missing email — no error!)
{ "user_id": "2", "name": "Bob" }

// Document 3: Different structure (extra field — no error!)
{ "user_id": "3", "name": "Charlie", "age": 25, "phone": "+1-555-0123" }
```

**Benefits**: Schema can evolve without migrations. Heterogeneous data is natural.

**Cost**: Bugs caught at read time (or not at all). Application must handle missing/unexpected fields. No database-level validation.

### When Each Approach Wins

| Schema-on-Write (SQL) | Schema-on-Read (NoSQL) |
|---|---|
| Data structure is well-known and stable | Data structure evolves rapidly (every sprint) |
| Data integrity is critical (finance, health) | Flexibility is more important than integrity |
| Multiple applications access the same data | Single application owns the data |
| Team values explicit contracts | Team values rapid iteration |
| Regulatory compliance requires validation | Best-effort validation is acceptable |

---

## The Hybrid Approach: SQL + NoSQL Together

Most production systems at scale use **both** SQL and NoSQL, each for what it does best.

### Architecture Pattern: SQL Source of Truth + NoSQL Read Views

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  Application Write Path                                     │
│  ─────────────────────                                      │
│                                                             │
│  User Action ──→ API Server ──→ PostgreSQL (Source of Truth)│
│                                    │                        │
│                                    │ CDC (Change Data       │
│                                    │      Capture)          │
│                                    ▼                        │
│                              ┌─── Kafka ───┐                │
│                              │             │                │
│                              ▼             ▼                │
│                         Elasticsearch   Redis               │
│                         (search)        (cache)             │
│                              │             │                │
│                              ▼             ▼                │
│                         DynamoDB      ClickHouse            │
│                         (mobile API)  (analytics)           │
│                                                             │
│  Application Read Path                                      │
│  ────────────────────                                       │
│                                                             │
│  Search queries ──────────→ Elasticsearch                   │
│  Hot data lookups ────────→ Redis                           │
│  Mobile API calls ────────→ DynamoDB                        │
│  Analytics queries ───────→ ClickHouse                      │
│  Transactional reads ─────→ PostgreSQL (via cache)          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Why This Works

1. **PostgreSQL** is the single source of truth — all writes go here, ensuring ACID guarantees.
2. **CDC (Change Data Capture)** streams every change to Kafka — no dual-write complexity.
3. **Each NoSQL database** is a **derived view** optimized for its specific read pattern:
   - Elasticsearch for full-text search queries.
   - Redis for sub-millisecond cache lookups.
   - DynamoDB for mobile API with flexible schema.
   - ClickHouse for analytical aggregations.

4. **If any NoSQL view is corrupted or lost**, it can be **rebuilt from PostgreSQL** by replaying the CDC stream. No data loss.

**Interview talking point**:
> *"I'd use SQL for the source of truth and build NoSQL read-optimized views from it. The canonical order data lives in PostgreSQL with full referential integrity. A CDC (Change Data Capture) stream from Postgres feeds into Kafka, which populates Elasticsearch for product search, Redis for session caching, and a denormalized DynamoDB table for the mobile API's product listing endpoint. Each consumer gets exactly the data shape it needs."*

---

## CDC Pattern: SQL as Source of Truth, NoSQL as Read Views

### What Is CDC (Change Data Capture)?

CDC captures every INSERT, UPDATE, and DELETE from the database's transaction log (WAL in PostgreSQL, binlog in MySQL) and streams them as events to downstream consumers.

### Tools

| Tool | Source Databases | Target Systems |
|---|---|---|
| **Debezium** | PostgreSQL, MySQL, MongoDB, SQL Server | Kafka, Pulsar |
| **AWS DMS** | Any major SQL/NoSQL | Any major SQL/NoSQL, S3, Redshift |
| **pg_logical** | PostgreSQL | Custom consumers |
| **Maxwell** | MySQL | Kafka, RabbitMQ |

### CDC Event Example

```json
{
  "op": "u",  // update operation
  "before": {
    "user_id": 123,
    "name": "Alice",
    "avatar_url": "old_avatar.jpg"
  },
  "after": {
    "user_id": 123,
    "name": "Alice",
    "avatar_url": "new_avatar.jpg"
  },
  "source": {
    "db": "users_db",
    "table": "users",
    "lsn": 123456789
  },
  "ts_ms": 1711234567890
}
```

### Why CDC Is Better Than Dual-Write

**Dual-write (problematic)**:
```python
def update_user(user_id, name):
    postgres.execute("UPDATE users SET name = ? WHERE id = ?", name, user_id)  # Step 1
    redis.set(f"user:{user_id}:name", name)                                    # Step 2
    elasticsearch.update("users", user_id, {"name": name})                     # Step 3
    # Problem: If Step 1 succeeds but Step 2 or 3 fails,
    # databases are inconsistent. No atomicity across systems.
```

**CDC (correct)**:
```
PostgreSQL ──(WAL)──→ Debezium ──→ Kafka ──→ Redis Consumer
                                         ──→ ES Consumer
                                         ──→ DynamoDB Consumer
# CDC captures the committed WAL entry — guaranteed to match PostgreSQL
# Each consumer processes independently; failures are retried from Kafka
# Kafka retains events for 30 days — consumers can replay if needed
```

---

## Performance Benchmarks and Real Numbers

### Single-Record Read Latency

| Database | Read Latency (p50) | Read Latency (p99) | Notes |
|---|---|---|---|
| Redis | 0.1ms | 0.5ms | In-memory |
| DynamoDB | 2ms | 5ms | Managed, consistent |
| Cassandra (CL=ONE) | 2ms | 10ms | Local read, eventual |
| PostgreSQL (cached) | 0.5ms | 3ms | Buffer pool hit |
| PostgreSQL (disk) | 2ms | 15ms | Requires I/O |
| MongoDB | 1ms | 5ms | WiredTiger engine |
| Elasticsearch | 5ms | 50ms | Not optimized for point reads |

### Write Throughput (Single Instance / Node)

| Database | Max Writes/sec | Notes |
|---|---|---|
| Redis | 100K+ | In-memory, single-threaded per shard |
| Cassandra | 100K+ per node | Linear scaling with nodes |
| DynamoDB | Virtually unlimited | Auto-scales (managed) |
| PostgreSQL | 10-50K | Single master bottleneck |
| MySQL | 10-40K | Single master bottleneck |
| MongoDB | 10-50K | Single master (per shard) |
| Elasticsearch | 10-50K | Bulk indexing preferred |

### Analytical Query Performance (1 Billion Rows)

| Database | Query: SUM with GROUP BY | Notes |
|---|---|---|
| ClickHouse | 1-3 seconds | Columnar, vectorized execution |
| BigQuery | 3-10 seconds | Serverless, petabyte-scale |
| PostgreSQL | 30-120 seconds | Row-based, not optimized for analytics |
| DynamoDB | Not feasible | No native aggregation at this scale |

---

## Operational Complexity Comparison

| Aspect | SQL (PostgreSQL) | NoSQL (Cassandra) | NoSQL (DynamoDB) |
|---|---|---|---|
| **Setup complexity** | Low (single instance) | Medium (multi-node cluster) | Very low (managed service) |
| **Backup / Restore** | pg_dump, WAL archiving | SSTable snapshots | Automatic (AWS-managed) |
| **Schema migration** | Flyway, Liquibase | Manual (add columns carefully) | No schema to migrate |
| **Monitoring** | pg_stat, pgHero | nodetool, DataStax OpsCenter | CloudWatch (built-in) |
| **Scaling** | Manual (add replicas, shard) | Operational (add nodes) | Automatic (on-demand mode) |
| **Team skills needed** | SQL, database administration | Cassandra data modeling, JVM tuning | AWS, DynamoDB API |
| **Operational burden** | Medium | High | Very low |
| **Cost model** | Instance hours | Instance hours × nodes | Per-request pricing |

---

## Migration Strategies: SQL ↔ NoSQL

### Phase 1: Dual-Write
Application writes to both old and new databases simultaneously.

### Phase 2: Backfill
Batch job migrates historical data from old to new database.

### Phase 3: Shadow Read
Read from both databases; compare results. Log discrepancies. Serve from old database.

### Phase 4: Gradual Cutover
Feature flag shifts read traffic: 1% → 10% → 50% → 100% to new database.

### Phase 5: Decommission
Remove old database reads, then writes, then the old database itself.

**Key principle**: Each phase is independently reversible. If problems emerge at any stage, roll back to the previous phase.

---

## Interview Talking Points & Scripts

### The Framework Statement

> *"The decision framework I use: if the data is inherently relational (users have orders, orders have items, items belong to categories) and I need multi-table transactions, SQL is the clear winner. If I need to scale writes beyond 50K/sec or the schema changes every sprint, NoSQL wins. Most systems need both — SQL for the transactional core, NoSQL for the read-heavy caches and search indexes."*

### For Financial / Payment Systems

> *"For a financial system, I'd always choose SQL — specifically PostgreSQL with serializable isolation. When transferring $500 from Account A to Account B, I need a guarantee that the debit and credit happen atomically. With eventual consistency, there's a window where the $500 has left Account A but hasn't arrived at Account B — that's a bug report waiting to happen, and in regulated finance, a compliance violation."*

### Correcting the "NoSQL Is Faster" Myth

> *"The 'NoSQL is faster' claim is misleading. A well-indexed PostgreSQL query on a properly sized instance returns in 0.5ms. What NoSQL actually gives you is horizontal write scalability — when you need 500K writes/sec and a single PostgreSQL master maxes out at 50K writes/sec, Cassandra's shared-nothing architecture lets you add nodes linearly. Speed per query isn't the differentiator; aggregate write throughput is."*

### The Hybrid Approach

> *"I'd use SQL for the source of truth and build NoSQL read-optimized views from it. The canonical order data lives in PostgreSQL with full referential integrity. A CDC (Change Data Capture) stream from Postgres feeds into Kafka, which populates Elasticsearch for product search, Redis for session caching, and a denormalized DynamoDB table for the mobile API's product listing endpoint. Each consumer gets exactly the data shape it needs."*

### Schema Flexibility

> *"For the product catalog, I'd choose a document database — the schema varies wildly per product category. A laptop has processor_speed and RAM. A shirt has color and fabric. A book has ISBN and author. With a relational model, I'd either have a massive sparse table (50 columns, 80% NULL) or an ugly EAV pattern that kills query performance. A document model handles this naturally — each product is a JSON document with whatever fields are relevant."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd use NoSQL because it's more scalable"
**Problem**: Not all NoSQL databases scale the same way. MongoDB's scaling is different from Cassandra's. And PostgreSQL with read replicas + caching scales to very high read QPS.

**Better**: *"I'd use Cassandra specifically because we need horizontal write scaling beyond 50K/sec, and our access pattern is partition-key-based range scans."*

### ❌ Mistake 2: "SQL can't scale"
**Problem**: Instagram runs on PostgreSQL at massive scale. Uber's core trip data is in MySQL (sharded). Stripe processes billions of dollars on PostgreSQL.

**Better**: *"SQL scales vertically and via read replicas. For write scaling beyond a single master, we'd need application-level sharding or a distributed SQL database like Spanner."*

### ❌ Mistake 3: Choosing NoSQL without acknowledging what you lose
**Problem**: Saying "I'd use DynamoDB" without mentioning you lose JOINs, multi-table transactions, and ad-hoc query flexibility.

**Better**: *"I'd use DynamoDB for the product catalog. The tradeoff is losing JOINs and cross-item transactions, but for a product catalog where reads are key-based and the schema varies per category, these capabilities aren't needed."*

### ❌ Mistake 4: Using one technology everywhere
**Problem**: Using PostgreSQL for caching (too slow) or Cassandra for analytics (no aggregation).

**Better**: Polyglot persistence — each database for what it does best.

### ❌ Mistake 5: Not specifying which NoSQL
**Problem**: "I'd use NoSQL" is like saying "I'd use a vehicle." Car? Truck? Motorcycle?

**Better**: Name the specific technology and explain why: *"I'd use Cassandra because..."* or *"I'd use Redis because..."*

---

## Decision Matrix for Popular System Design Problems

| System Design Problem | Source of Truth | Read Cache | Search | Analytics | Why This Split |
|---|---|---|---|---|---|
| **Twitter** | Cassandra (tweets) | Redis (timeline) | Elasticsearch | ClickHouse | High write volume, need search + analytics |
| **WhatsApp** | Cassandra (messages) | Redis (presence) | — | — | Extreme write volume, simple access pattern |
| **E-Commerce** | PostgreSQL (orders) | Redis (session, cart) | Elasticsearch | Redshift | Need ACID for orders, flexibility for catalog |
| **URL Shortener** | DynamoDB (mappings) | Redis (hot URLs) | — | ClickHouse | Simple key-value, need click analytics |
| **Ticket Booking** | PostgreSQL (reservations) | Redis (inventory cache) | — | — | ACID essential for booking, cache for browsing |
| **Payment System** | PostgreSQL (transactions) | Redis (session) | — | — | ACID + serializable isolation is mandatory |
| **Social Feed** | Cassandra (posts) | Redis (feed cache) | Elasticsearch | ClickHouse | Write-heavy, need pre-computed feeds |
| **Leaderboard** | PostgreSQL (scores) | Redis sorted sets | — | — | Redis sorted sets are purpose-built |
| **Video Streaming** | PostgreSQL (metadata) | Redis (CDN config) | Elasticsearch | BigQuery | Metadata needs ACID; content from CDN |

---

## Deep Dive: When Companies Chose SQL Over NoSQL (And Vice Versa)

### Instagram: PostgreSQL at Scale

Instagram chose PostgreSQL and scaled it to billions of rows via sharding. Why?
- ACID transactions for user account operations.
- Complex queries for ad targeting.
- Team expertise in SQL.
- Used pgpartman for table partitioning and Citus for distributed queries.

### Discord: Cassandra → ScyllaDB for Messages

Discord started with Cassandra for message storage but switched to ScyllaDB (Cassandra-compatible, written in C++ instead of Java) for better tail latency. The data model was the same — partition by channel_id, cluster by message_id. They kept the wide-column model because their access pattern (messages in a channel, sorted by time) is a perfect fit.

### Uber: MySQL (Sharded) → Docstore

Uber built a custom document store (Docstore) on top of MySQL. Each logical database is a sharded MySQL cluster, but the API is document-oriented. They got SQL's ACID guarantees with NoSQL's flexible schema.

### Stripe: PostgreSQL for Everything Financial

Stripe runs its core payment processing on PostgreSQL. For financial transactions, there is no substitute for ACID transactions with serializable isolation. They scale via application-level sharding.

### Netflix: Cassandra for Everything Else

Netflix uses Cassandra for virtually all non-transactional data — viewing history, user preferences, content metadata. The access patterns are simple (get by user_id), the write volume is enormous (streaming events), and eventual consistency is fine for "what did this user watch?"

---

## The Future: NewSQL and Converging Technologies

### NewSQL: The Best of Both Worlds?

NewSQL databases aim to provide SQL's ACID transactions with NoSQL's horizontal scalability:

| Database | Key Feature | Tradeoff |
|---|---|---|
| **Google Spanner** | Global strong consistency via TrueTime | Expensive, latency cost of consensus |
| **CockroachDB** | Distributed SQL, PostgreSQL-compatible | Higher latency than single-node PostgreSQL |
| **TiDB** | MySQL-compatible, distributed | Still maturing, some SQL compatibility gaps |
| **YugabyteDB** | PostgreSQL-compatible, distributed | Complex operational model |
| **Vitess** | MySQL sharding middleware | Not truly distributed SQL; sharding layer |

### Converging Features

SQL databases are adding NoSQL features:
- PostgreSQL: JSONB (document storage), full-text search, pub/sub.
- MySQL: Document Store protocol, JSON data type.

NoSQL databases are adding SQL features:
- Cassandra: CQL (SQL-like query language).
- MongoDB: Multi-document transactions (4.0+).
- DynamoDB: PartiQL (SQL-compatible query language).
- Elasticsearch: SQL API.

### What This Means for Interviews

> *"The SQL vs NoSQL boundary is blurring. PostgreSQL's JSONB gives me document storage within a relational engine. MongoDB 4.0+ gives me multi-document transactions in a document store. But the fundamental tradeoffs remain: SQL excels at multi-entity transactions and complex queries; NoSQL excels at horizontal write scaling and schema flexibility. I'd still choose based on the primary access pattern and scale requirement."*

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                  SQL vs NoSQL CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CHOOSE SQL WHEN:                                            │
│  ✓ Need ACID transactions across multiple entities           │
│  ✓ Need complex JOINs and aggregate queries                  │
│  ✓ Data is inherently relational                             │
│  ✓ Schema is stable and well-defined                         │
│  ✓ Write volume < 50K/sec                                    │
│  ✓ Compliance/regulatory requirements                        │
│                                                              │
│  CHOOSE NoSQL WHEN:                                          │
│  ✓ Write volume > 50K/sec (horizontal scaling)               │
│  ✓ Schema varies per record (flexible documents)             │
│  ✓ Sub-ms latency needed (Redis)                             │
│  ✓ Simple access patterns (key-value lookups)                │
│  ✓ Time-series / event data (append-heavy)                   │
│  ✓ Graph traversals (social networks)                        │
│                                                              │
│  THE HYBRID ANSWER (Most Mature):                            │
│  SQL = Source of truth (ACID, correctness)                    │
│  NoSQL = Read views (performance, specific patterns)         │
│  CDC/Kafka = Sync mechanism (SQL → NoSQL)                    │
│                                                              │
│  "NoSQL IS FASTER" — DEBUNKED:                               │
│  Per-query: Similar (0.5-5ms for both)                       │
│  Real advantage: Horizontal WRITE scalability                │
│                                                              │
│  NoSQL TYPES:                                                │
│  Key-Value → Redis, DynamoDB (caching, sessions)             │
│  Document → MongoDB, DynamoDB (flexible schema)              │
│  Wide-Column → Cassandra (time-series, high writes)          │
│  Graph → Neo4j (social graphs, recommendations)              │
│                                                              │
│  INTERVIEW FORMULA:                                          │
│  "I'd choose [SQL/NoSQL] because [access pattern]           │
│   requires [specific capability]. The tradeoff is            │
│   [what we lose], which is acceptable because                │
│   [specific reasoning]."                                     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 1: CAP Theorem** — The consistency-availability tradeoff underlying SQL vs NoSQL choices
- **Topic 2: Database Selection & Data Modeling** — Detailed breakdown of each database type
- **Topic 4: Consistency Models** — The consistency spectrum from eventual to linearizable
- **Topic 12: Sharding & Partitioning** — How both SQL and NoSQL handle horizontal scaling
- **Topic 13: Replication** — Read scaling strategies for both paradigms
- **Topic 36: Distributed Transactions & Sagas** — How to handle cross-service transactions without SQL ACID

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
