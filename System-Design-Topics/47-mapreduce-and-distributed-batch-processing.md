# 🎯 Topic 47: MapReduce & Distributed Batch Processing

> **System Design Interview — Deep Dive**
> A comprehensive guide covering the MapReduce programming model, Hadoop ecosystem, Spark as the modern successor, distributed batch processing patterns, shuffle and sort internals, data locality, fault tolerance, and how to articulate batch processing decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [The MapReduce Programming Model](#the-mapreduce-programming-model)
3. [MapReduce Execution Flow — Step by Step](#mapreduce-execution-flow--step-by-step)
4. [The Shuffle and Sort Phase](#the-shuffle-and-sort-phase)
5. [Fault Tolerance in MapReduce](#fault-tolerance-in-mapreduce)
6. [Data Locality — Moving Compute to Data](#data-locality--moving-compute-to-data)
7. [Hadoop Ecosystem Overview](#hadoop-ecosystem-overview)
8. [Apache Spark — The Modern Successor](#apache-spark--the-modern-successor)
9. [MapReduce vs Spark — When to Use Which](#mapreduce-vs-spark--when-to-use-which)
10. [Common MapReduce Patterns](#common-mapreduce-patterns)
11. [Distributed Joins in Batch Processing](#distributed-joins-in-batch-processing)
12. [Performance Tuning and Numbers](#performance-tuning-and-numbers)
13. [Real-World System Examples](#real-world-system-examples)
14. [Deep Dive: Applying MapReduce to Popular Problems](#deep-dive-applying-mapreduce-to-popular-problems)
15. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
16. [Common Interview Mistakes](#common-interview-mistakes)
17. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**MapReduce** is a programming model for processing large datasets in parallel across a cluster of machines. It breaks computation into two phases — **Map** (transform and filter) and **Reduce** (aggregate) — with an automatic **Shuffle** phase in between that groups related data together.

```
The fundamental insight:
  You have 10 TB of web server logs across 1,000 machines.
  You want: "Count of page views per URL for yesterday."

Single machine approach:
  Read 10 TB sequentially → takes 28 hours (at 100 MB/sec)
  Not feasible for daily reporting.

MapReduce approach:
  1,000 machines each process 10 GB (their local chunk)
  Map: Each machine extracts (URL, 1) pairs from local logs
  Shuffle: Group all counts for the same URL together
  Reduce: Sum counts per URL
  Total time: ~10-30 minutes (parallel processing)
  
  Speedup: 28 hours → 30 minutes (56x, with 1,000 machines)
```

**Why MapReduce matters in system design interviews**: It's the foundation of all large-scale batch processing. Even though Hadoop MapReduce is rarely used directly today (replaced by Spark), the *concepts* — parallel processing, shuffling, partitioning, data locality, fault tolerance — appear in every batch processing discussion.

---

## The MapReduce Programming Model

### The Three Phases

```
INPUT → MAP → SHUFFLE & SORT → REDUCE → OUTPUT

Phase 1: MAP
  Input: Raw data chunks (splits)
  Function: User-defined map(key, value) → list of (key, value) pairs
  Purpose: Transform, filter, extract relevant fields
  Parallelism: One mapper per input split (fully parallel, no coordination)

Phase 2: SHUFFLE & SORT (automatic, framework-managed)
  Input: All (key, value) pairs from all mappers
  Action: Group by key, sort within each group
  Purpose: Bring all values for the same key to the same reducer
  This is the MOST EXPENSIVE phase (network transfer across cluster)

Phase 3: REDUCE
  Input: (key, [list of values]) for each unique key
  Function: User-defined reduce(key, values) → output
  Purpose: Aggregate, summarize, combine values
  Parallelism: One reducer per key partition (parallel across keys)
```

### Classic Example: Word Count

```
Input (3 files distributed across 3 machines):
  File 1: "the cat sat on the mat"
  File 2: "the dog sat on the log"  
  File 3: "the cat and the dog"

MAP PHASE (parallel, one mapper per file):
  Mapper 1: "the cat sat on the mat"
    → ("the", 1), ("cat", 1), ("sat", 1), ("on", 1), ("the", 1), ("mat", 1)
  
  Mapper 2: "the dog sat on the log"
    → ("the", 1), ("dog", 1), ("sat", 1), ("on", 1), ("the", 1), ("log", 1)
  
  Mapper 3: "the cat and the dog"
    → ("the", 1), ("cat", 1), ("and", 1), ("the", 1), ("dog", 1)

SHUFFLE & SORT (automatic):
  Group all values by key:
    "and" → [1]
    "cat" → [1, 1]
    "dog" → [1, 1]
    "log" → [1]
    "mat" → [1]
    "on"  → [1, 1]
    "sat" → [1, 1]
    "the" → [1, 1, 1, 1, 1]

REDUCE PHASE (parallel, one reducer per key group):
  Reducer: For each key, SUM all values
    "and" → 1
    "cat" → 2
    "dog" → 2
    "log" → 1
    "mat" → 1
    "on"  → 2
    "sat" → 2
    "the" → 5
```

### Practical Example: Page View Counts

```python
# MAP: Extract URL from each log line
def map(line_number, log_line):
    # Input: "2025-03-15T14:30:00 GET /products/123 200 45ms"
    url = log_line.split()[2]           # "/products/123"
    date = log_line.split()[0][:10]     # "2025-03-15"
    emit((url, date), 1)               # Key: (URL, date), Value: 1

# REDUCE: Sum counts per (URL, date)
def reduce(key, values):
    # key = ("/products/123", "2025-03-15")
    # values = [1, 1, 1, ..., 1]  (one per page view)
    total = sum(values)
    emit(key, total)                   # ("/products/123", "2025-03-15") → 47,832
```

---

## MapReduce Execution Flow — Step by Step

### Detailed Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                   MAPREDUCE EXECUTION                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  INPUT (HDFS)        MAP PHASE         SHUFFLE     REDUCE    │
│                                                              │
│  ┌─────────┐     ┌──────────┐                               │
│  │ Split 1  │ ──→│ Mapper 1  │──┐                            │
│  │ (64 MB)  │     │           │  │                            │
│  └─────────┘     └──────────┘  │   ┌─────────┐  ┌────────┐ │
│                                │──→│ Sort &   │─→│Reducer │ │
│  ┌─────────┐     ┌──────────┐ │   │ Partition │  │   1    │ │
│  │ Split 2  │ ──→│ Mapper 2  │──┤   └─────────┘  └────────┘ │
│  │ (64 MB)  │     │           │  │                            │
│  └─────────┘     └──────────┘  │   ┌─────────┐  ┌────────┐ │
│                                │──→│ Sort &   │─→│Reducer │ │
│  ┌─────────┐     ┌──────────┐ │   │ Partition │  │   2    │ │
│  │ Split 3  │ ──→│ Mapper 3  │──┘   └─────────┘  └────────┘ │
│  │ (64 MB)  │     │           │                               │
│  └─────────┘     └──────────┘                    OUTPUT      │
│                                                   (HDFS)     │
│  Combiner: Optional local pre-aggregation                    │
│  (like a mini-reduce on the mapper side)                     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Step-by-Step Execution

```
Step 1: INPUT SPLITTING
  10 TB of log files in HDFS
  Split into 64 MB or 128 MB chunks (configurable)
  10 TB / 128 MB = ~80,000 splits → 80,000 map tasks

Step 2: MAP PHASE
  JobTracker assigns map tasks to TaskTrackers (worker nodes)
  Preference: Run mapper on the node where data is stored (data locality)
  Each mapper processes its local split → emits (key, value) pairs
  Mapper output written to LOCAL DISK (not HDFS — intermediate data)
  
  Duration: Depends on map function complexity
  Parallelism: All 80,000 mappers can run in parallel (given enough nodes)
  Typically: 10-100 mappers per node × 1,000 nodes = 10K-100K concurrent

Step 3: COMBINER (optional, highly recommended)
  Mini-reduce on the mapper side BEFORE shuffle
  
  Without combiner:
    Mapper emits: ("the", 1), ("the", 1), ("the", 1), ("the", 1), ("the", 1)
    → 5 key-value pairs sent over network
    
  With combiner:
    Combiner locally aggregates: ("the", 5)
    → 1 key-value pair sent over network
    
  Reduces shuffle data by 80-95% for aggregation operations!
  Combiner function must be associative and commutative (SUM, MAX, MIN — not AVG)

Step 4: SHUFFLE & SORT
  Most expensive phase (network I/O):
    All mapper outputs partitioned by key → sent to appropriate reducer
    Partitioning: hash(key) % num_reducers → determines target reducer
    
  Each reducer receives its partition of data from ALL mappers:
    Reducer 0: All keys where hash(key) % R == 0
    Reducer 1: All keys where hash(key) % R == 1
    ...
    
  Merge-sort: Reducer merges incoming sorted streams into one sorted stream
  
  Network transfer: This is where most time is spent in many jobs.
  Optimization: Compression of shuffle data (snappy/lz4)

Step 5: REDUCE PHASE
  For each unique key, reduce function called with all values:
    reduce("the", [5, 3, 7, 2, 4]) → emit("the", 21)
  
  Output written to HDFS (durable, replicated)
  
Step 6: OUTPUT
  Results in HDFS: /output/part-00000, /output/part-00001, ...
  One output file per reducer
```

---

## The Shuffle and Sort Phase

### Why Shuffle Is the Bottleneck

```
Shuffle = "Send every mapper's output to the right reducer"

Scenario: 1,000 mappers, 100 reducers
  Each mapper sends data to ALL 100 reducers
  Total network connections: 1,000 × 100 = 100,000 connections
  
  If each mapper produces 100 MB of output:
    Total shuffle data: 1,000 × 100 MB = 100 GB over the network
    Network bandwidth: 10 Gbps per node → ~1.25 GB/sec
    Theoretical minimum: 80 seconds for shuffle alone
    
  With compression (snappy, 3x ratio):
    Shuffle data: ~33 GB → ~27 seconds
    
  Without combiner:
    Shuffle data might be 500 GB → 400 seconds (6.7 minutes)!

Key insight: MINIMIZE SHUFFLE DATA
  1. Use combiners (pre-aggregate on mapper side)
  2. Filter early (drop irrelevant data in map phase)
  3. Use efficient serialization (Avro, Protobuf, not JSON)
  4. Compress shuffle output (snappy or lz4)
```

### Partition Function

```
Default: hash(key) % num_reducers

Custom partitioner example (range-based):
  Keys are dates: "2025-03-01" to "2025-03-31"
  
  Default hash: Random distribution (dates scrambled across reducers)
  Custom range: Reducer 0 gets days 1-10, Reducer 1 gets 11-20, Reducer 2 gets 21-31
  
  Why custom? Output files are sorted by date range — useful for downstream processing.

Custom partitioner (locality-aware):
  Keys are user_ids with region prefix: "us_123", "eu_456"
  Partition by region: US keys → Reducer 0, EU keys → Reducer 1
  Result: Region-specific output files (useful for region-specific analytics)
```

---

## Fault Tolerance in MapReduce

### How Failures Are Handled

```
MapReduce is designed for commodity hardware where failures are EXPECTED:

MAPPER FAILURE:
  Mapper 47 crashes (machine dies, OOM, disk error)
  JobTracker detects: No heartbeat for 10 minutes
  Action: Reschedule mapper 47's task on a DIFFERENT node
  Data is still available: Input split is in HDFS (replicated 3x)
  Impact: Just the failed mapper's work is redone. Others unaffected.
  
  Speculative execution: If mapper is SLOW (not dead):
    Run a duplicate on another node → use whichever finishes first
    Prevents "straggler" problem (one slow node delays entire job)

REDUCER FAILURE:
  Reducer 5 crashes
  All mapper output for reducer 5's partition must be re-fetched
  (Mapper output is on LOCAL disk — may still be available if mappers haven't cleaned up)
  If mapper output lost: Re-run affected mappers too
  
JOBTRACKER FAILURE:
  Single point of failure in original Hadoop (addressed in YARN with HA)
  YARN ResourceManager: Active/standby HA with ZooKeeper

NODE FAILURE:
  Node 42 dies (hardware failure)
  All map/reduce tasks on node 42: Failed, rescheduled elsewhere
  Data on node 42: HDFS has replicas on other nodes → no data loss
  HDFS detects under-replication → creates new replicas automatically
```

### Why Fault Tolerance Matters

```
At Google-scale (where MapReduce was invented):
  10,000+ machines in a cluster
  Expected: 1-5 machine failures per day
  
  Without fault tolerance:
    Any failure → entire job restarts → never completes
    
  With MapReduce fault tolerance:
    Machine failure → only affected tasks retry → job completes
    User doesn't even notice (automatic, transparent)
```

---

## Data Locality — Moving Compute to Data

### The Key Principle

```
Traditional approach: Move data to compute
  10 TB of data on storage servers
  Copy 10 TB over network to compute servers → 28 hours at 100 MB/sec
  THEN process → more hours
  
MapReduce approach: Move compute to data
  10 TB split across 1,000 nodes (HDFS stores data locally)
  Run mapper ON THE SAME NODE where the data lives
  Each node reads 10 GB from LOCAL DISK (no network!) → 100 seconds at 100 MB/sec
  
  Speedup from data locality alone: 100x (network eliminated for reads)

HDFS data placement:
  Block 1 of file → Node A, Node C, Node F (3 replicas)
  Block 2 of file → Node B, Node D, Node G (3 replicas)
  
  MapReduce scheduler:
    Map task for Block 1 → schedule on Node A (first choice, local)
    If Node A is busy → schedule on Node C (second choice, same rack)
    If rack is full → any node (last resort, cross-rack network)
    
  Priority: Same node > Same rack > Any node
  Typical: 95% of map tasks run data-local
```

---

## Hadoop Ecosystem Overview

### Core Components

```
┌──────────────────────────────────────────────────────────────┐
│                    HADOOP ECOSYSTEM                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  STORAGE:                                                    │
│  ├── HDFS: Distributed file system (blocks of 128 MB, RF=3) │
│  └── S3: Cloud object storage (AWS alternative to HDFS)      │
│                                                              │
│  COMPUTE:                                                    │
│  ├── MapReduce: Original batch processing (disk-based)       │
│  ├── Spark: Modern batch + stream (memory-based, 10-100x)   │
│  ├── Tez: DAG-based execution (Hive on Tez)                 │
│  └── Flink: Stream processing (with batch capability)        │
│                                                              │
│  RESOURCE MANAGEMENT:                                        │
│  └── YARN: Cluster resource allocation (CPU, memory)         │
│                                                              │
│  QUERY ENGINES:                                              │
│  ├── Hive: SQL-on-Hadoop (compiles SQL to MapReduce/Tez)     │
│  ├── Presto/Trino: Interactive SQL on anything               │
│  └── Impala: Low-latency SQL on HDFS                         │
│                                                              │
│  DATA FORMATS:                                               │
│  ├── Parquet: Columnar format (best for analytics)           │
│  ├── ORC: Columnar format (Hive optimized)                   │
│  ├── Avro: Row-based with schema evolution                   │
│  └── CSV/JSON: Human-readable but slow                       │
│                                                              │
│  COORDINATION:                                               │
│  └── ZooKeeper: Distributed coordination, leader election    │
│                                                              │
│  INGESTION:                                                  │
│  ├── Kafka: Event streaming                                  │
│  ├── Flume: Log collection                                   │
│  └── Sqoop: SQL database → HDFS import/export                │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### HDFS (Hadoop Distributed File System)

```
HDFS architecture:
  NameNode: Metadata (file → block mapping, block → node mapping)
  DataNodes: Store actual data blocks

File storage:
  File "logs.parquet" (1 GB) → split into 8 blocks × 128 MB
  Each block replicated 3x across different racks
  
  Block 1: Node A (rack 1), Node C (rack 2), Node F (rack 3)
  Block 2: Node B (rack 1), Node D (rack 2), Node G (rack 3)
  ...

Properties:
  ✅ Fault tolerant: Any 2 nodes can fail without data loss (RF=3)
  ✅ Scalable: Add nodes → linear storage increase
  ✅ Optimized for large sequential reads (not random access)
  ❌ No in-place updates (append-only)
  ❌ High latency for small files (NameNode overhead)
  ❌ Single NameNode is a bottleneck (HA NameNode in newer versions)
```

---

## Apache Spark — The Modern Successor

### Why Spark Replaced MapReduce

```
MapReduce limitations:
  1. Disk I/O between stages: Map → write to disk → read for shuffle → reduce → write to disk
     Every stage materializes to disk. Iterative algorithms (ML) are painfully slow.
  
  2. Only Map + Reduce: Complex pipelines need CHAINED MapReduce jobs
     Job 1: Map → Reduce (filter)
     Job 2: Map → Reduce (join)
     Job 3: Map → Reduce (aggregate)
     Each job reads/writes HDFS between stages → massive I/O overhead.
  
  3. No in-memory processing: Data goes to disk between every operation.

Spark improvements:
  1. In-memory processing: Keep intermediate data in RAM → 10-100x faster
  2. Rich API: map, filter, join, groupBy, window — all composable in one job
  3. DAG execution: Optimizes the entire pipeline, not just Map→Reduce
  4. Lazy evaluation: Builds execution plan before running → optimizer can improve it
  5. Multi-language: Java, Scala, Python, R, SQL
```

### Spark RDD and DataFrame

```
Spark RDD (Resilient Distributed Dataset):
  Immutable, distributed collection of objects
  Transformations (lazy): map, filter, flatMap, groupByKey, reduceByKey
  Actions (trigger execution): count, collect, saveAsTextFile
  
  Example (word count in Spark):
    sc.textFile("hdfs://logs/*.txt")
      .flatMap(line => line.split(" "))
      .map(word => (word, 1))
      .reduceByKey(_ + _)
      .saveAsTextFile("hdfs://output/word_counts")
    
    One pipeline, optimized as a DAG, intermediate data in memory.
    vs MapReduce: 3 separate disk-based stages.

Spark DataFrame (preferred for analytics):
  Typed, optimized table abstraction with SQL-like API
  Catalyst optimizer: Automatically optimizes query plan
  Tungsten engine: Binary format, cache-friendly, vectorized execution
  
  Example:
    spark.read.parquet("s3://events/clicks/2025-03-15/")
      .groupBy("campaign_id", "hour")
      .agg(count("*").alias("clicks"), sum("cost").alias("spend"))
      .write.parquet("s3://output/daily_aggregates/")
```

### Spark Execution Model

```
Driver Program:
  Creates SparkContext → connects to Cluster Manager (YARN/K8s)
  Submits application with resource requirements
  
Cluster Manager (YARN/Kubernetes):
  Allocates executors (JVM processes) on worker nodes
  
Executors:
  Run tasks (map, reduce, shuffle) in parallel
  Cache data in memory (configurable: MEMORY_ONLY, MEMORY_AND_DISK)
  
  Typical: 200 executors × 4 cores × 16 GB each = 800 parallel tasks, 3.2 TB RAM

Stages and Shuffles:
  Spark divides the DAG into stages at shuffle boundaries:
  
  Stage 1: Read → filter → map (no shuffle needed — "narrow" transformations)
  -- SHUFFLE BOUNDARY (data must be redistributed by key) --
  Stage 2: reduceByKey → sort → write (after shuffle)
  
  Within a stage: Tasks pipeline without writing to disk.
  Between stages: Shuffle writes to disk (like MapReduce, but smarter caching).
```

---

## MapReduce vs Spark — When to Use Which

| Aspect | Hadoop MapReduce | Apache Spark |
|---|---|---|
| **Speed** | Slow (disk-based between stages) | 10-100x faster (in-memory) |
| **API** | Map + Reduce only | Rich: map, filter, join, window, SQL |
| **Iterative algorithms** | Very slow (disk I/O per iteration) | Fast (data stays in memory) |
| **Memory requirements** | Low (disk-based) | High (in-memory, needs more RAM) |
| **Fault tolerance** | Excellent (disk-based, easy to re-read) | Good (RDD lineage for recomputation) |
| **Cost** | Cheaper per node (less RAM needed) | More RAM needed, but faster → fewer node-hours |
| **Maturity** | Very mature, battle-tested at exabyte scale | Mature, dominant for new workloads |
| **Streaming** | No (batch only) | Yes (Structured Streaming) |
| **Best for** | Simple ETL on extremely large datasets | Analytics, ML, iterative algorithms, SQL |

```
Modern recommendation:
  Use Spark for almost everything (default choice).
  Use MapReduce only if:
    - Existing Hadoop infrastructure with MapReduce jobs (migration not justified)
    - Extremely memory-constrained environment
    - Ultra-simple ETL where MapReduce complexity is sufficient
    
  In interviews: Say "I'd use Spark for the batch processing layer"
  unless specifically asked about MapReduce internals.
```

---

## Common MapReduce Patterns

### Pattern 1: Filtering

```
Map: Emit only records matching criteria
Reduce: Identity (no aggregation needed)

Example: "Find all error log entries from yesterday"
  Map: if line.contains("ERROR") and line.date == yesterday → emit(line)
  Reduce: pass through

Spark equivalent:
  logs.filter(col("level") == "ERROR" and col("date") == yesterday)
```

### Pattern 2: Aggregation (GROUP BY)

```
Map: Emit (grouping_key, value_to_aggregate)
Reduce: Apply aggregation function (SUM, COUNT, AVG, MAX)

Example: "Total revenue per product category per day"
  Map: emit((category, date), revenue)
  Reduce: sum(revenues)

With combiner (critical for performance):
  Combiner: Local sum on each mapper → reduces shuffle data by 90%+

Spark equivalent:
  orders.groupBy("category", "date").agg(sum("revenue"))
```

### Pattern 3: Sorting (Top-K)

```
Map: Emit (sort_key, record)
Reduce: Collect and sort (framework sorts automatically)

Example: "Top 100 products by revenue"
  Map: emit(revenue, product_id)  ← key=revenue for automatic sorting
  Reduce: Take first 100

Optimization: Each mapper computes local top-100 → reducer merges
  Instead of shuffling ALL products, only shuffle 100 per mapper.
  1,000 mappers × 100 products = 100,000 candidates → reducer picks top 100.
  
Spark equivalent:
  products.orderBy(desc("revenue")).limit(100)
```

### Pattern 4: Join (Two Datasets)

```
Example: Join clicks with user profiles
  Dataset 1: clicks (click_id, user_id, url, timestamp)
  Dataset 2: users (user_id, name, country)
  
  Output: clicks enriched with user country

Reduce-side join (default):
  Map clicks: emit(user_id, ("click", click_record))
  Map users: emit(user_id, ("user", user_record))
  Shuffle groups by user_id → reducer sees both click and user records
  Reduce: For each user_id, join click records with user record
  
  Cost: Full shuffle of BOTH datasets. Expensive.

Map-side join (broadcast join — for small tables):
  If users table is small enough to fit in memory (< 1 GB):
  Load users table into memory on EVERY mapper (distributed cache)
  Each mapper joins its click records with in-memory user table
  NO SHUFFLE NEEDED → 10-100x faster
  
  Spark equivalent: broadcast join
    clicks.join(broadcast(users), "user_id")
```

### Pattern 5: Inverted Index

```
Example: Build search index from documents
  Input: (doc_id, document_text)
  Output: (word, [doc_id1, doc_id2, ...])

Map: For each word in document → emit(word, doc_id)
Reduce: Collect all doc_ids for each word → emit(word, list_of_doc_ids)

This is how Google's original web search index was built!
  Crawled web pages → MapReduce → inverted index → search queries
```

### Pattern 6: Reconciliation

```
Example: Compare streaming aggregates with exact batch counts (Topic 33)

Map: Read raw events from S3 → emit((campaign_id, date), 1)
Reduce: Sum → exact count per (campaign_id, date)

Second pass:
Map: Read streaming aggregates from ClickHouse → emit((campaign_id, date), stream_count)
     Read batch aggregates → emit((campaign_id, date), batch_count)
Reduce: Compare stream_count vs batch_count → emit discrepancy

This is the canonical use of MapReduce in ad tech: nightly billing reconciliation.
```

---

## Distributed Joins in Batch Processing

### Join Strategies

```
Strategy 1: Shuffle Join (Reduce-Side Join)
  Both tables shuffled by join key → co-located at reducer → joined
  Cost: O(|A| + |B|) shuffle
  Use when: Both tables are large
  
Strategy 2: Broadcast Join (Map-Side Join)
  Small table broadcast to every mapper → joined locally
  Cost: O(|small_table| × num_mappers) broadcast
  Use when: One table fits in memory (< 1-2 GB)
  
Strategy 3: Sort-Merge Join
  Both tables pre-sorted by join key → merge in linear time
  Cost: O(|A| + |B|) but no shuffle if pre-sorted
  Use when: Tables are already sorted (e.g., partitioned Parquet files)

Strategy 4: Bucket Join
  Both tables pre-bucketed by join key (same # of buckets)
  Each bucket pair joined independently → no shuffle
  Use when: Tables are pre-bucketed by the same key

Spark auto-selects the best strategy based on table sizes.
  If one table < spark.sql.autoBroadcastJoinThreshold (default 10 MB) → broadcast
  Otherwise → shuffle (sort-merge) join
```

---

## Performance Tuning and Numbers

### Throughput Numbers

```
Single mapper reading from local HDFS:
  HDD: ~100 MB/sec sequential read
  SSD: ~500 MB/sec sequential read

Single Spark executor:
  In-memory processing: 1-10 GB/sec (depends on operation)
  Disk-based Spark: ~200-500 MB/sec per executor

Cluster-level throughput:
  200-node Spark cluster × 500 MB/sec = 100 GB/sec aggregate
  
  10 TB dataset: ~100 seconds to scan (with 200 nodes)
  Plus aggregation: ~5-10 minutes total for complex analytics

Real-world benchmarks:
  Spark sort record: 100 TB sorted in 23 minutes (on 206 nodes)
  Google MapReduce (original paper): 1 TB sorted in 68 seconds (1,800 nodes)
  
Cost:
  200 Spark executors on spot instances × 4 hours:
    200 × $0.07/hour × 4 = $56 per job
    vs. single machine: $0.34/hour × 28 hours = $9.52 but takes 28 hours
    
  Spot Spark: 56x faster, 6x more expensive per run, but time is money.
```

### Key Tuning Parameters

```
Number of mappers:
  = Total input size / split size
  = 10 TB / 128 MB = ~80,000 mappers
  Each mapper: ~2 minutes to process 128 MB

Number of reducers:
  Too few: Each reducer overloaded, slow, possible OOM
  Too many: Too many small output files, overhead
  Rule of thumb: 0.95 × total_reduce_slots (leave room for retries)
  Or: Total shuffle data / 256 MB per reducer

Spark partitions:
  spark.sql.shuffle.partitions = 200 (default, often too few)
  For large datasets: 2-4x number of cores
  1,000 cores → 2,000-4,000 partitions
  
Memory allocation:
  Spark executor memory: 16-64 GB (depends on data size)
  Overhead: 10% for JVM overhead
  Storage fraction: 60% for caching, 40% for execution
```

---

## Real-World System Examples

### Google — Original MapReduce (2004)

```
Google's web search infrastructure:
  Input: Petabytes of crawled web pages
  MapReduce job 1: Parse HTML → extract URLs → build link graph
  MapReduce job 2: Compute PageRank (iterative — many rounds)
  MapReduce job 3: Build inverted index (word → [document list])
  MapReduce job 4: Rank documents for each query term
  
  Ran on 10,000+ commodity machines
  Processed petabytes daily
  Fault tolerance critical: ~5 machine failures per day at this scale
```

### Facebook — Hive + Spark for Analytics

```
Data warehouse: Exabytes of data in HDFS/S3
  User activity logs: ~600 TB/day ingested
  Processing: Hive SQL queries compiled to Spark jobs
  
Daily analytics:
  DAU/MAU calculations: Spark job over user session data
  Ad revenue reconciliation: Spark job comparing click streams with billing
  Content ranking features: Spark ML pipeline for feed ranking
  
Scale: 300+ PB data warehouse, 1M+ Spark jobs/day
```

### Netflix — Spark for Recommendations + ETL

```
Recommendation system:
  Nightly batch: Spark computes recommendations for all 200M+ users
  Input: Viewing history, ratings, content metadata
  Algorithm: Matrix factorization (iterative — Spark's in-memory advantage)
  Output: Top-100 recommendations per user → stored in Cassandra
  
ETL pipeline:
  Raw playback events → S3 → Spark → aggregated metrics → Redshift/Druid
  200 billion events/day processed by Spark batch jobs
```

### Uber — Spark for Trip Analytics

```
Surge pricing computation:
  Real-time: Flink for live demand/supply signals
  Batch: Spark for historical analysis, model training
  
  Nightly Spark job:
    Input: All trip data for past 30 days
    Compute: Demand patterns by (geo_hash, hour, day_of_week)
    Output: Surge pricing model parameters
    
  Scale: 100M+ trips/month, petabytes of data
```

---

## Deep Dive: Applying MapReduce to Popular Problems

### Ad Click Reconciliation (Nightly Billing)

```
The #1 use case for batch processing in ad tech:

Input: All raw click events for yesterday (S3, Parquet format)
  s3://events/clicks/2025-03-15/*.parquet (~200 billion events)

Spark job:
  val clicks = spark.read.parquet("s3://events/clicks/2025-03-15/")
  
  // Exact dedup by click_id
  val deduped = clicks.dropDuplicates("click_id")
  
  // Fraud filtering
  val valid = deduped.filter(!col("is_bot") && !col("is_duplicate_ip"))
  
  // Aggregate per (campaign, ad, hour)
  val aggregates = valid
    .groupBy("campaign_id", "ad_id", hour("timestamp"))
    .agg(
      count("*").alias("clicks"),
      sum("cost").alias("spend"),
      countDistinct("user_id").alias("unique_users")
    )
  
  // Compare with streaming aggregates (reconciliation)
  val streaming = spark.read.jdbc("clickhouse://...", "streaming_aggregates")
  val comparison = aggregates.join(streaming, Seq("campaign_id", "ad_id", "hour"))
    .withColumn("discrepancy_pct", 
      abs(col("batch_clicks") - col("stream_clicks")) / col("batch_clicks") * 100)
  
  // Write corrected aggregates
  aggregates.write.mode("overwrite").parquet("s3://billing/2025-03-15/")

Duration: 2-4 hours on 200 Spark executors (spot instances)
Cost: ~$56 per run (200 × $0.07/hour × 4 hours)
Result: Exact billable click counts for invoicing
```

### Search Index Building

```
Build inverted index from 10 billion web pages:

Map phase: For each document
  Input: (url, html_content)
  Extract text from HTML
  Tokenize, stem, remove stop words
  For each term: emit(term, (url, position, frequency))

Reduce phase: For each term
  Input: (term, [(url1, pos1, freq1), (url2, pos2, freq2), ...])
  Sort posting list by relevance score
  Write to index segment file

Output: Inverted index files → loaded into search servers

This is still how Google/Bing build their search index.
Updated incrementally (not full rebuild) for real-time freshness.
```

### Recommendation System (Batch)

```
Compute collaborative filtering recommendations:

Input: 
  User-item interactions: (user_id, item_id, rating, timestamp)
  10 billion interactions, 500M users, 50M items

Spark MLlib ALS (Alternating Least Squares):
  // Matrix factorization — iterative algorithm
  val als = new ALS()
    .setMaxIter(20)        // 20 iterations
    .setRank(100)          // 100 latent factors
    .setRegParam(0.01)
    .setUserCol("user_id")
    .setItemCol("item_id")
    .setRatingCol("rating")
  
  val model = als.fit(interactions)
  
  // Generate top-100 recommendations per user
  val recommendations = model.recommendForAllUsers(100)
  recommendations.write.parquet("s3://recs/2025-03-15/")

Duration: 2-6 hours (20 iterations, each requiring shuffle)
Why Spark (not MapReduce): Each iteration reads from previous iteration's output.
  MapReduce: 20 iterations × (read from HDFS + write to HDFS) = massive I/O
  Spark: Keep data in memory between iterations → 10-50x faster
```

---

## Interview Talking Points & Scripts

### When to Mention MapReduce

> *"For the nightly reconciliation job that reprocesses all 200 billion events from yesterday, I'd use Spark (the modern successor to MapReduce). The job reads raw events from S3 in Parquet format, deduplicates by click_id, applies fraud filtering, aggregates per campaign per hour, and compares with streaming aggregates to detect and correct any drift. It runs on 200 spot instances in 2-4 hours for about $56 per run."*

### The MapReduce Concept (Even with Spark)

> *"The processing follows the MapReduce pattern: the Map phase extracts and filters relevant fields from raw events. The Shuffle redistributes data by campaign_id so all events for one campaign end up on the same executor. The Reduce phase aggregates counts. I'd use a combiner — pre-aggregating on each mapper before shuffle — to reduce network transfer by 90%. The Shuffle is always the bottleneck because it requires network transfer of all intermediate data."*

### Why Spark Over MapReduce

> *"I'd use Spark over Hadoop MapReduce because Spark keeps intermediate data in memory between stages. For our recommendation system that requires 20 iterations of matrix factorization, Spark avoids 20 round trips to HDFS — the data stays in executor memory between iterations, making it 10-50x faster than MapReduce for iterative algorithms."*

### Data Locality

> *"A key principle of distributed batch processing is data locality — moving computation to where the data lives, rather than moving data to computation. When Spark reads from HDFS, the scheduler assigns tasks to nodes that have the data block locally. This eliminates network I/O for the read phase, which is a massive optimization when processing 10 TB of data."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd use MapReduce for real-time processing"
**Why it's wrong**: MapReduce is batch-only, with minutes-to-hours latency. Real-time needs Flink or Kafka Streams.
**Better**: "MapReduce/Spark for batch (nightly reconciliation). Flink for real-time streaming aggregation."

### ❌ Mistake 2: Not mentioning the Shuffle bottleneck
**Why it's wrong**: Shuffle is the most expensive part. Without optimization (combiners, compression), jobs are 10x slower.
**Better**: "I'd use combiners to pre-aggregate on mappers before shuffle, reducing network transfer by 90%."

### ❌ Mistake 3: Using MapReduce when Spark is more appropriate
**Why it's wrong**: For iterative algorithms (ML, PageRank, graph processing), MapReduce is 10-100x slower than Spark.
**Better**: "Spark for almost everything. MapReduce only if inheriting legacy infrastructure."

### ❌ Mistake 4: Not mentioning fault tolerance
**Why it's wrong**: At scale (1,000+ nodes), machine failures are expected daily. Fault tolerance is essential.
**Better**: "If a Spark executor fails, Spark re-reads the input partition from S3 and recomputes. RDD lineage tracks how to rebuild any lost partition."

### ❌ Mistake 5: Ignoring data format
**Why it's wrong**: Reading CSV/JSON is 5-10x slower than Parquet for analytical queries.
**Better**: "I'd store raw events in Parquet format — columnar compression means the Spark job only reads the columns it needs, reducing I/O by 80%."

### ❌ Mistake 6: Not mentioning cost optimization
**Why it's wrong**: Batch jobs are perfect for spot instances (60-80% cheaper). Not mentioning this misses a major cost lever.
**Better**: "Nightly Spark job on 200 spot instances at $0.07/hour. If spot is reclaimed, Spark retries the failed tasks on remaining instances."

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│     MAPREDUCE & DISTRIBUTED BATCH PROCESSING CHEAT SHEET     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  MODEL: MAP → SHUFFLE & SORT → REDUCE                        │
│    Map: Transform, filter, extract (parallel, no coordination)│
│    Shuffle: Group by key (MOST EXPENSIVE — network I/O)      │
│    Reduce: Aggregate, summarize (parallel across keys)       │
│                                                              │
│  KEY OPTIMIZATIONS:                                          │
│    Combiner: Pre-aggregate on mapper (reduces shuffle 90%+)  │
│    Data locality: Run compute where data lives (no network)  │
│    Compression: Snappy/LZ4 for shuffle data (3x reduction)  │
│    Parquet format: Columnar, compressed (80% less I/O)       │
│    Broadcast join: Small table in memory (avoids shuffle)    │
│                                                              │
│  SPARK vs MAPREDUCE:                                         │
│    Spark: In-memory, 10-100x faster, rich API, modern        │
│    MapReduce: Disk-based, simple, legacy                     │
│    Use Spark for everything new.                             │
│                                                              │
│  FAULT TOLERANCE:                                            │
│    Mapper fails → rescheduled on another node                │
│    Data in HDFS/S3: Replicated 3x, always available          │
│    Speculative execution: Duplicate slow tasks               │
│                                                              │
│  PERFORMANCE NUMBERS:                                        │
│    Single node: 100-500 MB/sec (disk read)                   │
│    200-node cluster: ~100 GB/sec aggregate scan rate          │
│    10 TB dataset: ~10-30 minutes for complex analytics       │
│    Cost: 200 spot instances × 4 hours = ~$56/job             │
│                                                              │
│  COMMON PATTERNS:                                            │
│    Aggregation: GROUP BY with combiner                       │
│    Filtering: Early filter in map phase                      │
│    Join: Broadcast (small table) or Shuffle (large tables)   │
│    Top-K: Local top-K per mapper → global merge in reducer   │
│    Inverted index: (term → doc_list) for search              │
│    Reconciliation: Batch exact counts vs streaming estimates │
│                                                              │
│  USE IN SYSTEM DESIGN:                                       │
│    Nightly reconciliation (billing, analytics)               │
│    Search index building                                     │
│    Recommendation generation (ML training)                   │
│    ETL pipelines (raw events → aggregated tables)            │
│    Data warehouse loading                                    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 23: Batch vs Stream Processing** — Lambda/Kappa architectures
- **Topic 29: Pre-Computation vs On-Demand** — Batch pre-computation patterns
- **Topic 33: Reconciliation** — Batch reconciliation using Spark
- **Topic 35: Cost vs Performance** — Spot instances for batch workloads
- **Topic 39: Kafka Deep Dive** — Kafka as input/output for batch jobs

---

*This document is part of the System Design Interview Deep Dive series.*
