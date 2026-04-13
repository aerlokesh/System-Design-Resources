# 🎯 Topic 2: Database Selection & Data Modeling

> **System Design Interview — Deep Dive**
> A comprehensive guide covering how to select the right database technology for any system design problem, how to model data effectively, and how to articulate database decisions with depth, specific numbers, and tradeoff reasoning.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [The Database Landscape](#the-database-landscape)
3. [Decision Framework: How to Choose a Database](#decision-framework-how-to-choose-a-database)
4. [Relational Databases (SQL)](#relational-databases-sql)
5. [Wide-Column Stores](#wide-column-stores)
6. [Document Databases](#document-databases)
7. [Key-Value Stores](#key-value-stores)
8. [Graph Databases](#graph-databases)
9. [Time-Series Databases](#time-series-databases)
10. [Columnar / Analytics Databases](#columnar--analytics-databases)
11. [Search Engines](#search-engines)
12. [Data Modeling Principles](#data-modeling-principles)
13. [Denormalization: When and Why](#denormalization-when-and-why)
14. [Polyglot Persistence](#polyglot-persistence)
15. [Database Per Microservice Pattern](#database-per-microservice-pattern)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Deep Dive: Database Selection for Popular Problems](#deep-dive-database-selection-for-popular-problems)
19. [Performance Characteristics Comparison](#performance-characteristics-comparison)
20. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Database selection is not about finding the "best" database — it's about finding the best fit for your specific access patterns, consistency requirements, scale needs, and operational constraints.**

The right database depends on:
1. **Data model**: Relational? Document? Graph? Time-series?
2. **Access patterns**: OLTP (many small reads/writes)? OLAP (few large analytical queries)?
3. **Scale requirements**: Read-heavy? Write-heavy? Both?
4. **Consistency needs**: ACID transactions? Eventual consistency?
5. **Query patterns**: JOINs? Range scans? Full-text search? Graph traversals?
6. **Operational complexity**: Team expertise? Managed service availability?

---

## The Database Landscape

```
┌──────────────────────────────────────────────────────────────────┐
│                     DATABASE TAXONOMY                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  RELATIONAL (SQL)                                                │
│  ├── PostgreSQL    (most versatile, JSONB, full-text search)     │
│  ├── MySQL         (battle-tested, great replication)            │
│  ├── Amazon Aurora  (cloud-native, 5x MySQL throughput)          │
│  └── Google Spanner (global consistency, TrueTime)               │
│                                                                  │
│  WIDE-COLUMN                                                     │
│  ├── Cassandra     (linear write scaling, leaderless)            │
│  ├── HBase         (Hadoop ecosystem, strong consistency)        │
│  └── ScyllaDB      (Cassandra-compatible, C++ for performance)   │
│                                                                  │
│  DOCUMENT                                                        │
│  ├── MongoDB       (flexible schema, rich query language)        │
│  ├── DynamoDB      (serverless, single-digit ms latency)         │
│  └── CouchDB       (offline-first, HTTP API)                     │
│                                                                  │
│  KEY-VALUE                                                       │
│  ├── Redis         (in-memory, data structures, sub-ms)          │
│  ├── Memcached     (pure caching, multi-threaded)                │
│  └── etcd          (distributed config, consensus-based)         │
│                                                                  │
│  GRAPH                                                           │
│  ├── Neo4j         (native graph, Cypher query language)         │
│  └── Amazon Neptune (managed, supports Gremlin & SPARQL)         │
│                                                                  │
│  TIME-SERIES                                                     │
│  ├── InfluxDB      (purpose-built for metrics)                   │
│  ├── TimescaleDB   (PostgreSQL extension for time-series)        │
│  └── Prometheus    (pull-based metrics collection)               │
│                                                                  │
│  COLUMNAR / ANALYTICS                                            │
│  ├── ClickHouse    (real-time analytics, columnar)               │
│  ├── Apache Druid  (real-time OLAP, sub-second queries)          │
│  ├── Amazon Redshift (cloud data warehouse)                      │
│  └── BigQuery      (serverless, petabyte-scale analytics)        │
│                                                                  │
│  SEARCH                                                          │
│  ├── Elasticsearch (full-text search, analytics)                 │
│  └── Apache Solr   (enterprise search, Lucene-based)             │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Decision Framework: How to Choose a Database

### Step 1: Characterize Your Data Model

| If your data is... | Consider... |
|---|---|
| Structured with relationships (users → orders → items) | PostgreSQL, MySQL |
| Semi-structured with varying schema per record | MongoDB, DynamoDB |
| Highly connected (friends, followers, recommendations) | Neo4j, Neptune |
| Time-stamped metrics/events | InfluxDB, TimescaleDB, Cassandra |
| Key-value pairs with fast lookup | Redis, DynamoDB |
| Large text needing full-text search | Elasticsearch |
| Analytical aggregations over large datasets | ClickHouse, BigQuery |

### Step 2: Characterize Your Access Patterns

| If your pattern is... | Consider... |
|---|---|
| Single-record CRUD by primary key | DynamoDB, Redis, any SQL |
| Complex JOINs across multiple entities | PostgreSQL, MySQL |
| Range scans within a partition (e.g., messages in a chat) | Cassandra, DynamoDB |
| Full-text search with relevance ranking | Elasticsearch |
| Graph traversals (friends-of-friends, recommendations) | Neo4j |
| Aggregations with GROUP BY on large datasets | ClickHouse, BigQuery |
| High-velocity writes (>100K/sec) | Cassandra, DynamoDB, Redis |

### Step 3: Characterize Your Scale Requirements

| Requirement | Threshold | Recommendation |
|---|---|---|
| Reads/sec | < 10K | Single PostgreSQL + connection pooling |
| Reads/sec | 10K-100K | PostgreSQL + read replicas |
| Reads/sec | 100K-1M | Add Redis caching layer |
| Reads/sec | > 1M | Sharded database + CDN + cache |
| Writes/sec | < 10K | Single PostgreSQL master |
| Writes/sec | 10K-50K | PostgreSQL with batching |
| Writes/sec | 50K-500K | Cassandra or DynamoDB |
| Writes/sec | > 500K | Cassandra cluster or sharded architecture |

### Step 4: Evaluate Consistency & Transaction Needs

| If you need... | Choose... |
|---|---|
| ACID transactions across multiple tables | PostgreSQL, MySQL |
| Atomic operations on single item | DynamoDB, Redis |
| Multi-document transactions | MongoDB (4.0+), PostgreSQL |
| Eventual consistency is fine | Cassandra, DynamoDB (default) |
| Global strong consistency | Google Spanner |

---

## Relational Databases (SQL)

### PostgreSQL — The Swiss Army Knife

**When to choose PostgreSQL**:
- You need ACID transactions across multiple tables.
- Your data is inherently relational (entities have foreign key relationships).
- You need complex queries: JOINs, subqueries, window functions, CTEs.
- You want a single database that handles JSON (JSONB), full-text search, and geospatial data.
- You're not sure — PostgreSQL is the safest default choice for most applications.

**Performance characteristics**:
- Single-instance: ~10K-50K QPS (depending on query complexity and hardware).
- Read replicas: Linear read scaling (3 replicas = ~3x read throughput).
- Write ceiling: ~50K writes/sec on a well-tuned single master.
- Connection pooling (PgBouncer): Essential for high-concurrency workloads.

**Interview talking point**:
> *"I'd use PostgreSQL here because we need ACID transactions across multiple tables — when a user books a ticket, we update the seat status to 'HELD', create a reservation record, and decrement the available count, all atomically. If any step fails, the whole thing rolls back. With NoSQL, I'd need to implement this coordination in application code, which is error-prone and harder to test."*

### MySQL — The Battle-Tested Workhorse

**When to choose MySQL**:
- Read-heavy workloads with straightforward schemas.
- When you need mature replication (MySQL's replication ecosystem is incredibly mature).
- Compatibility with existing tooling and team expertise.
- When you want InnoDB's excellent crash recovery and MVCC.

**Key difference from PostgreSQL**: MySQL is traditionally stronger at simple reads and replication; PostgreSQL is stronger at complex queries, JSON, and extensibility.

### Amazon Aurora — Cloud-Native SQL

**When to choose Aurora**:
- You want managed PostgreSQL/MySQL with 5x throughput improvement.
- You need up to 15 read replicas with < 10ms replication lag.
- Storage auto-scales up to 128 TB.
- You're on AWS and want reduced operational burden.

### Google Spanner — Global Strong Consistency

**When to choose Spanner**:
- You need globally distributed SQL with strong consistency.
- Financial systems requiring external consistency across regions.
- You can afford the premium ($0.90/node-hour vs $0.05 for a comparably sized RDS instance).

---

## Wide-Column Stores

### Cassandra — Linear Horizontal Write Scaling

**When to choose Cassandra**:
- Write-heavy workloads exceeding 50K writes/sec.
- Time-series data (messages, events, metrics).
- When you need linear horizontal scaling (add nodes = proportionally more throughput).
- When eventual consistency is acceptable for most operations.

**Data modeling principles (critical for interviews)**:

Cassandra's data model is **query-driven** — you design tables around your queries, not your entities.

```sql
-- Table designed for "get all messages in a conversation, sorted by time"
CREATE TABLE messages (
    conversation_id UUID,
    message_id TIMEUUID,
    sender_id UUID,
    content TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (conversation_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

**Key concepts**:
- **Partition key** (`conversation_id`): Determines which node stores the data. All messages for one conversation are co-located on the same node.
- **Clustering key** (`message_id`): Determines sort order within the partition. Messages are stored sorted by time.
- **Range scans are fast**: `SELECT * FROM messages WHERE conversation_id = ? LIMIT 50` is a sequential read within a single partition — O(1) seek + O(50) sequential read.

**Performance characteristics**:
- Write throughput: ~10K-100K writes/sec per node (depending on data size).
- Read latency: < 5ms for partition-key lookups.
- Linear scaling: 10 nodes = ~10x throughput.
- No single point of failure (leaderless architecture).

**Interview talking point**:
> *"Cassandra is ideal for this message store — with 1.15 million writes per second across 500 million daily active users, we need a database with linear horizontal write scaling. Cassandra's leaderless architecture means every node accepts writes, there's no single-master bottleneck, and adding 10 more nodes increases throughput by roughly 10x. The tradeoff is we lose JOINs and transactions, but for messages partitioned by conversation_id, we never need cross-partition queries."*

**Anti-patterns to mention**:
- ❌ Cross-partition queries (scatter-gather — hits all nodes, very slow).
- ❌ Deleting large amounts of data (tombstones cause read performance issues).
- ❌ Updating rows frequently (each update is a new write; old versions accumulate until compaction).
- ❌ Using Cassandra for small datasets (< 10GB) — PostgreSQL is simpler and faster.

---

## Document Databases

### MongoDB / DynamoDB — Flexible Schema

**When to choose a document database**:
- Schema varies significantly per record (product catalogs, user-generated content).
- You want to store and query nested/hierarchical data naturally.
- You need atomic operations on a single document.
- Your access pattern is primarily key-based lookups with occasional secondary index queries.

**Product catalog example**:
```json
// Laptop
{
  "product_id": "laptop-001",
  "name": "ThinkPad X1 Carbon",
  "category": "electronics",
  "price": 1499.99,
  "specs": {
    "processor": "Intel i7-1365U",
    "ram_gb": 16,
    "screen_size": "14 inch",
    "battery_hours": 15
  }
}

// T-Shirt
{
  "product_id": "shirt-042",
  "name": "Classic Cotton Tee",
  "category": "clothing",
  "price": 29.99,
  "specs": {
    "color": "Navy Blue",
    "size": ["S", "M", "L", "XL"],
    "fabric": "100% Cotton",
    "care": "Machine wash cold"
  }
}
```

**Interview talking point**:
> *"For the product catalog, I'd choose DynamoDB — the schema varies wildly per product category. A laptop has processor_speed, RAM, and screen_size. A shirt has color, size, and fabric. A book has ISBN, author, and page_count. A document model handles this naturally — each product is a JSON document with whatever fields are relevant. With a relational model, I'd either have a massive sparse table or an ugly EAV (entity-attribute-value) pattern."*

### DynamoDB Specifics

- **Single-digit millisecond latency** at any scale (consistent performance).
- **Provisioned capacity**: You specify read/write capacity units (predictable cost).
- **On-demand capacity**: Auto-scales, pay per request (higher per-request cost, but no capacity planning).
- **Global tables**: Multi-region, multi-active replication.
- **Streams**: CDC (Change Data Capture) for building derived views.
- **Partition key + sort key**: Composite primary key for efficient range queries within a partition.

---

## Key-Value Stores

### Redis — In-Memory Data Structure Server

**When to choose Redis**:
- Sub-millisecond latency requirements.
- Caching layer for hot data.
- Leaderboards (sorted sets).
- Rate limiting (atomic counters with TTL).
- Session storage.
- Pub/Sub messaging.
- Distributed locks.

**Data structures and their use cases**:

| Redis Data Structure | Operation Complexity | Use Case |
|---|---|---|
| **String** | GET/SET: O(1) | Session storage, caching, counters |
| **Hash** | HGET/HSET: O(1) | User profiles, object caching |
| **List** | LPUSH/RPOP: O(1) | Message queues, activity feeds |
| **Set** | SADD/SISMEMBER: O(1) | Tags, unique visitors, mutual friends |
| **Sorted Set** | ZADD: O(log N), ZRANGE: O(log N + M) | Leaderboards, priority queues, time-sorted feeds |
| **HyperLogLog** | PFADD/PFCOUNT: O(1) | Unique visitor counting (approximate) |
| **Bloom Filter** | BF.ADD/BF.EXISTS: O(K) | Deduplication, "have I seen this?" |

**Interview talking point**:
> *"I'd use Redis sorted sets for the leaderboard — `ZADD leaderboard {score} {user_id}` to update a score in O(log N), `ZREVRANGE leaderboard 0 99` to get the top 100 in O(log N + 100), and `ZRANK leaderboard {user_id}` to get any user's rank in O(log N). For 100 million users, that's about 27 operations internally per query — all in-memory, sub-millisecond. No other data structure gives you sorted insert + rank lookup + range query all in logarithmic time."*

**Memory considerations**:
- Redis stores everything in RAM: ~1 byte overhead per key + actual data.
- 100 million keys × 100 bytes average value = ~10 GB RAM.
- Redis cluster: Sharding across multiple nodes for > 25 GB datasets.
- Persistence: RDB (snapshots) or AOF (append-only file) for durability.

---

## Graph Databases

### Neo4j — Native Graph Storage

**When to choose a graph database**:
- Social graphs (friends, followers, mutual connections).
- Recommendation engines ("users who liked X also liked Y").
- Fraud detection (finding suspicious transaction patterns).
- Knowledge graphs.
- Any query involving variable-depth traversals.

**Why graphs beat relational for relationship-heavy queries**:

The query "find all users within 3 hops who share at least 2 mutual friends":

**In SQL (painful)**:
```sql
-- This is a simplified version; the real query is much worse
SELECT DISTINCT u3.user_id
FROM friends f1
JOIN friends f2 ON f1.friend_id = f2.user_id
JOIN friends f3 ON f2.friend_id = f3.user_id
JOIN users u3 ON f3.friend_id = u3.user_id
WHERE f1.user_id = :user_id
  AND u3.user_id != :user_id
  AND (SELECT COUNT(*) FROM friends mf1
       JOIN friends mf2 ON mf1.friend_id = mf2.friend_id
       WHERE mf1.user_id = :user_id AND mf2.user_id = u3.user_id) >= 2;
-- Performance: seconds on large datasets, multiple self-JOINs
```

**In Cypher (Neo4j)**:
```cypher
MATCH (me:User {id: $userId})-[:FRIEND*1..3]-(candidate:User)
WHERE candidate <> me
  AND size([(me)-[:FRIEND]-(mutual)-[:FRIEND]-(candidate) | mutual]) >= 2
RETURN DISTINCT candidate
-- Performance: milliseconds, natural graph traversal
```

**Interview talking point**:
> *"For the social graph — friends, followers, mutual connections, 'friends of friends who also follow X' — I'd reach for Neo4j. A query like 'find all users within 3 hops who share at least 2 mutual friends' is a natural graph traversal that runs in milliseconds. In a relational database, that's a self-JOIN nightmare — 3 levels deep with deduplication, easily taking seconds on a large dataset."*

---

## Time-Series Databases

### InfluxDB / TimescaleDB / Cassandra for Time-Series

**When to choose time-series optimized storage**:
- Metrics collection (CPU, memory, request latency).
- IoT sensor data.
- Financial tick data.
- Event logging with time-based queries.

**Key characteristics of time-series workloads**:
- **Write-heavy**: Continuous stream of new data points.
- **Append-only**: Data is rarely updated after creation.
- **Time-range queries**: "Give me CPU usage for the last 6 hours."
- **Downsampling**: Minute → hour → day aggregation for older data.
- **TTL-based expiration**: Old data is less valuable and can be deleted.

**Cassandra for time-series (common interview pattern)**:

```sql
-- Partition key: (metric_name, day)
-- Clustering key: timestamp
CREATE TABLE metrics (
    metric_name TEXT,
    day DATE,
    timestamp TIMESTAMP,
    value DOUBLE,
    PRIMARY KEY ((metric_name, day), timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);
```

**Interview talking point**:
> *"For time-series metrics, I'd choose Cassandra with a composite partition key of (metric_name, day) and a clustering key of timestamp. This means all data points for 'cpu_usage' on '2025-03-15' are co-located on a single partition, making range scans like 'give me the last 6 hours of cpu_usage' extremely fast — it's a sequential read within one partition. The partition caps at ~100MB for a day of minute-level data, which is well within Cassandra's sweet spot."*

---

## Columnar / Analytics Databases

### ClickHouse — Real-Time Analytics

**When to choose ClickHouse**:
- Analytics dashboards with sub-second query requirements.
- Large-scale aggregation (GROUP BY, COUNT, SUM) over billions of rows.
- Wide tables (50+ columns) where queries only access a few columns.
- Time-series analytics with complex filtering.

**Why columnar storage is faster for analytics**:

Row-based storage (PostgreSQL):
```
Row 1: [campaign_id, ad_id, clicks, impressions, spend, timestamp, ...]
Row 2: [campaign_id, ad_id, clicks, impressions, spend, timestamp, ...]
-- Query: SELECT campaign_id, SUM(clicks) GROUP BY campaign_id
-- Reads ALL columns for every row, then discards most of them
```

Columnar storage (ClickHouse):
```
Column "campaign_id": [c1, c2, c1, c3, c1, c2, ...]
Column "clicks":      [5, 3, 7, 2, 9, 1, ...]
-- Query: SELECT campaign_id, SUM(clicks) GROUP BY campaign_id
-- Reads ONLY the 2 columns needed, skips the other 48+ columns
-- 10-25x less I/O for wide tables
```

**Interview talking point**:
> *"I'd use ClickHouse for the analytics dashboard — it's a columnar database that can scan 1 billion rows with a GROUP BY on campaign_id and aggregate click counts in under 2 seconds. The columnar format means when I query `SELECT campaign_id, SUM(clicks) GROUP BY campaign_id`, it only reads the campaign_id and clicks columns from disk, skipping all other columns. For wide tables with 50+ columns, this is a 10-25x I/O reduction compared to row-based databases."*

---

## Search Engines

### Elasticsearch — Full-Text Search & Analytics

**When to choose Elasticsearch**:
- Full-text search with relevance ranking.
- Faceted search (filter by category, price range, brand).
- Log aggregation and analysis (ELK stack).
- Geospatial search ("restaurants near me").

**Key concepts**:
- **Inverted index**: Maps terms to document IDs. "pizza" → [doc1, doc5, doc12].
- **BM25 scoring**: Relevance algorithm considering term frequency and document length.
- **Analyzers**: Tokenization, stemming, stop-word removal for natural language.
- **Sharding**: Data distributed across shards for horizontal scaling.

**Important**: Elasticsearch is a **derived view**, not a source of truth. The canonical data lives in your primary database; Elasticsearch is populated asynchronously via CDC or Kafka.

---

## Data Modeling Principles

### 1. Model Around Access Patterns, Not Entities

**Relational modeling** (entity-first): Define entities and relationships, normalize to 3NF, then figure out how to query them.

**NoSQL modeling** (query-first): Define your queries first, then design the data model to serve those queries efficiently.

```
Example: Chat message system

Queries:
Q1: Get recent messages for conversation X (sorted by time, paginated)
Q2: Get unread message count for user Y across all conversations
Q3: Search messages by content

Data model:
- Cassandra table for Q1 (partition: conversation_id, clustering: timestamp)
- Redis counter for Q2 (key: unread:{user_id})
- Elasticsearch index for Q3 (derived from Cassandra via Kafka)
```

### 2. Denormalize for Read Performance

**Normalized** (3NF):
```
tweets (tweet_id, content, author_id, created_at)
users (user_id, name, avatar_url, bio)

-- Read tweet with author info: JOIN
SELECT t.*, u.name, u.avatar_url
FROM tweets t JOIN users u ON t.author_id = u.user_id
WHERE t.tweet_id = ?
```

**Denormalized**:
```
tweets (tweet_id, content, author_id, author_name, author_avatar_url, created_at)

-- Read tweet with author info: single lookup
SELECT * FROM tweets WHERE tweet_id = ?
```

**Interview talking point**:
> *"I'd denormalize the feed data — store the author name and avatar URL directly in each tweet document instead of JOINing with the user table at read time. Yes, when a user changes their profile picture, I need to update it in potentially millions of tweet documents. But profile picture changes happen maybe once a month per user, while tweet reads happen billions of times per day. I'm optimizing for the 99.9999% case."*

### 3. Composite Keys for Efficient Range Queries

Design your primary key to support your most common range query:

```
-- "Get all orders for user X in the last 30 days"
PRIMARY KEY (user_id, order_date)
-- Single-partition range scan, extremely fast

-- "Get all messages in conversation X, most recent first"
PRIMARY KEY (conversation_id, message_timestamp DESC)
-- Sequential read within one partition
```

### 4. Cache Hot Data, Archive Cold Data

```
Hot (last 24h)  → Redis         (sub-ms, $25/GB/month)
Warm (1-30 days) → Primary DB   (ms, $0.10/GB/month on SSD)
Cold (>30 days)  → S3/Glacier   (seconds, $0.004/GB/month)
```

---

## Denormalization: When and Why

### The Core Tradeoff

| Aspect | Normalized | Denormalized |
|---|---|---|
| **Write complexity** | Simple (update one place) | Complex (update many places) |
| **Read complexity** | Complex (JOINs) | Simple (single lookup) |
| **Storage** | Minimal (no duplication) | Higher (data duplicated) |
| **Consistency** | Guaranteed (single source) | Eventually consistent |
| **Best for** | Write-heavy, low read volume | Read-heavy, high read volume |

### When to Denormalize

1. **Read-to-write ratio > 100:1**: If data is read 100x more than it's written, optimize for reads.
2. **JOINs across shards**: In a sharded system, cross-shard JOINs are extremely expensive. Denormalize to keep queries within one shard.
3. **Latency SLA < 10ms**: If you can't afford the latency of a JOIN, embed the data.
4. **NoSQL database**: Most NoSQL databases don't support JOINs. Denormalization is required.

### Denormalization Patterns

**Pattern 1: Embedding**
Store related data within the document.
```json
{
  "tweet_id": "123",
  "content": "Hello world",
  "author": {
    "user_id": "456",
    "name": "Alice",
    "avatar_url": "cdn.example.com/alice.jpg"
  }
}
```

**Pattern 2: Pre-computed aggregates**
Store computed values instead of computing at read time.
```
-- Instead of COUNT(*) on every read:
tweets (tweet_id, content, like_count, retweet_count, reply_count)
-- Increment counters on write, read is just a field lookup
```

**Pattern 3: Materialized views**
A separate read-optimized table populated by write-time triggers or CDC.
```
-- Source of truth (normalized):
orders (order_id, user_id, product_id, quantity, price, status)

-- Materialized view (denormalized, optimized for dashboard):
order_summary (user_id, total_orders, total_spend, last_order_date)
-- Updated via CDC stream when orders table changes
```

---

## Polyglot Persistence

**Polyglot persistence** means using different database technologies for different parts of the system, each chosen for its strengths.

### Example Architecture: E-Commerce Platform

```
┌─────────────────────────────────────────────────────────────┐
│                    E-Commerce Platform                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  PostgreSQL ──── Orders, Users, Payments                    │
│                  (ACID transactions, referential integrity)  │
│                                                             │
│  Redis ───────── Session cache, rate limiting,              │
│                  shopping cart, leaderboard                  │
│                  (sub-ms latency, data structures)           │
│                                                             │
│  DynamoDB ────── Product catalog                            │
│                  (flexible schema per category)              │
│                                                             │
│  Elasticsearch ── Product search, autocomplete              │
│                  (full-text search, relevance ranking)       │
│                                                             │
│  Cassandra ───── Click events, view history                 │
│                  (high write throughput, time-series)        │
│                                                             │
│  ClickHouse ──── Analytics dashboard                        │
│                  (columnar aggregations, fast GROUP BY)      │
│                                                             │
│  Neo4j ───────── Recommendations                            │
│                  ("users who bought X also bought Y")        │
│                                                             │
│  S3 ──────────── Product images, invoices                   │
│                  (blob storage, 11 nines durability)         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Interview talking point**:
> *"I'd use SQL for the source of truth and build NoSQL read-optimized views from it. The canonical order data lives in PostgreSQL with full referential integrity. A CDC (Change Data Capture) stream from Postgres feeds into Kafka, which populates Elasticsearch for product search, Redis for session caching, and a denormalized DynamoDB table for the mobile API's product listing endpoint. Each consumer gets exactly the data shape it needs."*

---

## Database Per Microservice Pattern

Each microservice owns its database exclusively. No other service accesses it directly.

### Benefits
- **Independent deployment**: Change schema without coordinating with other teams.
- **Independent scaling**: Scale the database for the hot service without affecting others.
- **Technology freedom**: Each service picks the best database for its access patterns.
- **Failure isolation**: One database going down doesn't cascade to other services.

### Challenges
- **No cross-service JOINs**: Must use API calls or event-driven denormalization.
- **Distributed transactions**: Use Saga pattern instead of 2PC.
- **Data duplication**: Same data may exist in multiple databases (by design).
- **Operational complexity**: More databases to manage, monitor, and backup.

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ Tweet Service │    │ User Service │    │ Search Svc   │
│              │    │              │    │              │
│  Cassandra   │    │  PostgreSQL  │    │ Elasticsearch│
│  (messages)  │    │  (profiles)  │    │  (index)     │
└──────────────┘    └──────────────┘    └──────────────┘
        │                   │                   │
        └───────── Kafka Event Bus ─────────────┘
```

---

## Interview Talking Points & Scripts

### Default Choice Explanation

> *"My default choice is PostgreSQL unless I have a specific reason to choose something else. PostgreSQL handles ACID transactions, JSON documents (JSONB), full-text search, geospatial queries, and scales to 50K writes/sec with read replicas. For 80% of system design problems, it's the right starting point."*

### When to Upgrade from PostgreSQL

> *"I'd switch from PostgreSQL to a specialized database when we hit a specific limitation:
> - Write throughput > 50K/sec → Cassandra or DynamoDB
> - Need for flexible schema across millions of documents → DynamoDB/MongoDB
> - Sub-millisecond latency for hot data → Redis
> - Complex graph traversals → Neo4j
> - Full-text search with relevance ranking → Elasticsearch
> - Analytics over billions of rows → ClickHouse"*

### Cache + Database Pattern

> *"I'd keep the user profile in PostgreSQL as the source of truth but cache the hot fields (name, avatar_url, bio) in Redis with a 10-minute TTL. At our scale, 99.2% of profile reads hit Redis (sub-millisecond), and the 0.8% cache misses fall through to Postgres. This saves us from needing 12 Postgres read replicas — instead, a 3-node Redis cluster handles the read volume at 1/10th the cost."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd use MongoDB because it's NoSQL and scales better"
**Why it's wrong**: MongoDB doesn't inherently scale better than PostgreSQL. It scales differently (sharding instead of replication), and for most workloads under 50K QPS, PostgreSQL is faster and simpler.

### ❌ Mistake 2: Choosing a database without explaining access patterns
**Why it's wrong**: The database choice is meaningless without context. "I'd use Cassandra" is incomplete. "I'd use Cassandra because we need to handle 1M writes/sec of time-series events partitioned by device_id with range scans by timestamp" shows understanding.

### ❌ Mistake 3: Using one database for everything
**Why it's wrong**: PostgreSQL can't serve sub-millisecond cache lookups. Redis can't do complex analytical aggregations. Elasticsearch can't guarantee ACID transactions. Different requirements need different tools.

### ❌ Mistake 4: Not mentioning the source of truth
**Why it's wrong**: When using multiple databases, you must specify which is the source of truth and how derived views are kept in sync (CDC, Kafka events, dual-write).

### ❌ Mistake 5: Choosing a database based on hype
**Why it's wrong**: Don't choose DynamoDB because "Amazon uses it" or Cassandra because "Netflix uses it." Choose based on your specific access patterns, scale requirements, and consistency needs.

---

## Deep Dive: Database Selection for Popular Problems

### Twitter / Social Feed
- **Tweets**: Cassandra (high write throughput, time-series partitioned by user_id)
- **Timeline cache**: Redis sorted sets (pre-computed, sub-ms reads)
- **User profiles**: PostgreSQL (relational, ACID for account operations)
- **Search**: Elasticsearch (full-text search with relevance)
- **Analytics**: ClickHouse (aggregations over billions of tweets)

### WhatsApp / Chat
- **Messages**: Cassandra (partition by conversation_id, clustering by timestamp)
- **Online presence**: Redis (TTL-based keys, sub-ms lookups)
- **User accounts**: PostgreSQL (ACID for phone number verification, account management)

### URL Shortener
- **URL mappings**: DynamoDB or Redis (simple key-value, high read throughput)
- **Analytics**: ClickHouse (click tracking aggregations)

### E-Commerce
- **Orders, Payments**: PostgreSQL (ACID transactions)
- **Product catalog**: DynamoDB (flexible schema per category)
- **Search**: Elasticsearch (faceted search, autocomplete)
- **Recommendations**: Neo4j (graph-based "similar products")
- **Session/Cart**: Redis (ephemeral, fast)

### Leaderboard
- **Rankings**: Redis sorted sets (O(log N) for all operations)
- **Historical rankings**: PostgreSQL or Cassandra (per-period snapshots)

---

## Performance Characteristics Comparison

| Database | Read Latency | Write Latency | Write Throughput | Horizontal Scale | Consistency |
|---|---|---|---|---|---|
| PostgreSQL | 1-5ms | 1-5ms | 10-50K/sec | Read replicas | Strong (ACID) |
| Cassandra | 2-10ms | 1-5ms | 100K+/sec/node | Linear (add nodes) | Tunable |
| DynamoDB | 1-5ms | 1-5ms | Virtually unlimited | Auto (managed) | Eventual (default) |
| Redis | 0.1-1ms | 0.1-1ms | 100K+/sec/node | Cluster sharding | Strong (single node) |
| MongoDB | 1-5ms | 1-5ms | 50K+/sec | Sharding | Tunable |
| Neo4j | 1-10ms | 5-20ms | 10-50K/sec | Read replicas | ACID |
| Elasticsearch | 10-100ms | 50-200ms | 10-50K/sec | Sharding | Near-real-time |
| ClickHouse | 100ms-5s | 1-10ms (batch) | 500K+/sec (batch) | Sharding | Eventual |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│             DATABASE SELECTION CHEAT SHEET                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  DEFAULT: PostgreSQL (safe, versatile, ACID, JSONB)          │
│                                                              │
│  SWITCH WHEN:                                                │
│  Writes > 50K/sec ────────────→ Cassandra / DynamoDB         │
│  Sub-ms latency needed ───────→ Redis                        │
│  Flexible schema per doc ─────→ DynamoDB / MongoDB           │
│  Relationship traversals ─────→ Neo4j                        │
│  Full-text search ────────────→ Elasticsearch                │
│  Analytics on billions ───────→ ClickHouse                   │
│  Time-series metrics ─────────→ Cassandra / InfluxDB         │
│  Global strong consistency ───→ Google Spanner               │
│                                                              │
│  MODELING RULES:                                             │
│  1. Model around access patterns, not entities               │
│  2. Denormalize for reads when read:write ratio > 100:1      │
│  3. One source of truth + derived views via CDC/Kafka        │
│  4. Composite keys for efficient range queries               │
│  5. Each microservice owns its database exclusively          │
│                                                              │
│  FORMULA FOR INTERVIEW:                                      │
│  "I'd choose [DB] because [access pattern] requires          │
│   [capability], and the tradeoff of [limitation] is          │
│   acceptable because [specific reasoning with numbers]"      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 1: CAP Theorem** — Consistency-availability tradeoffs affecting DB choice
- **Topic 3: SQL vs NoSQL** — Deeper comparison of relational vs non-relational
- **Topic 5: Caching Strategies** — Cache + DB patterns
- **Topic 12: Sharding & Partitioning** — Horizontal database scaling
- **Topic 13: Replication** — Read scaling and high availability
- **Topic 17: Storage Tiering** — Hot/warm/cold data strategies

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
