# 🎯 Topic 64: AWS RDS Aurora Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Amazon Aurora's architecture — storage-compute separation, distributed quorum writes, 6-way replication, read replicas with <20ms lag, Global Database for multi-region, Aurora Serverless, connection management, failover mechanics, performance characteristics, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Aurora Architecture — Storage-Compute Separation](#aurora-architecture--storage-compute-separation)
3. [Distributed Storage Layer — 6-Way Replication](#distributed-storage-layer--6-way-replication)
4. [Quorum Writes and Reads](#quorum-writes-and-reads)
5. [Read Replicas — Up to 15](#read-replicas--up-to-15)
6. [Failover Mechanics](#failover-mechanics)
7. [Aurora Global Database — Multi-Region](#aurora-global-database--multi-region)
8. [Aurora Serverless v2](#aurora-serverless-v2)
9. [Connection Management — RDS Proxy](#connection-management--rds-proxy)
10. [Storage Auto-Scaling and Limits](#storage-auto-scaling-and-limits)
11. [Backups, Snapshots, and PITR](#backups-snapshots-and-pitr)
12. [Performance Characteristics](#performance-characteristics)
13. [Aurora vs Standard RDS vs DynamoDB](#aurora-vs-standard-rds-vs-dynamodb)
14. [Cost Optimization](#cost-optimization)
15. [Real-World Production Patterns](#real-world-production-patterns)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Amazon Aurora** is a cloud-native relational database that combines the performance and availability of commercial databases with the simplicity and cost of open-source databases. It's MySQL and PostgreSQL-compatible but re-architects the storage layer for cloud — separating compute from storage, using distributed quorum writes across 6 copies of data in 3 AZs, and achieving 5x MySQL / 3x PostgreSQL throughput.

```
Why Aurora matters in system design interviews:

  Standard MySQL on RDS:
    Single EBS volume → single point of failure for storage
    Replication: Ship entire WAL to replicas → slow, high lag
    Failover: 1-2 minutes (promote replica, update DNS)

  Aurora:
    Distributed storage: 6 copies across 3 AZs → survives AZ failure
    Replication: Only ship REDO logs (not data pages) → 10-20ms lag
    Failover: <30 seconds (often <15 seconds)
    Storage scales automatically: 10 GB to 128 TB, no downtime
    Up to 15 read replicas (vs 5 for standard RDS)

  Key insight: Aurora's storage layer is a distributed, self-healing system.
  The compute layer (MySQL/PostgreSQL engine) is separate and replaceable.
```

---

## Aurora Architecture — Storage-Compute Separation

### The Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Compute Layer                              │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │  Writer       │  │  Reader 1    │  │  Reader 2    │  ...×15   │
│  │  Instance     │  │  Instance    │  │  Instance    │           │
│  │  (db.r6g.xl) │  │  (db.r6g.lg) │  │  (db.r6g.lg) │           │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
│         │                  │                  │                   │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          │  Redo logs only  │  Read pages      │  Read pages
          │  (not data pages)│                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Distributed Storage Layer                      │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │            Shared, Distributed Storage Volume               │  │
│  │                                                            │  │
│  │  AZ-1         AZ-2         AZ-3                            │  │
│  │  ┌─────┐     ┌─────┐     ┌─────┐                          │  │
│  │  │Copy1│     │Copy3│     │Copy5│                          │  │
│  │  │Copy2│     │Copy4│     │Copy6│                          │  │
│  │  └─────┘     └─────┘     └─────┘                          │  │
│  │                                                            │  │
│  │  6 copies of every write, across 3 AZs                     │  │
│  │  Storage auto-scales: 10 GB → 128 TB                       │  │
│  │  Self-healing: detects and repairs corrupt segments         │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Why Separation Matters

```
Traditional RDS (MySQL on EBS):
  Compute + Storage tightly coupled on one instance.
  Scaling storage = downtime (resize EBS, or restore from snapshot).
  Replication = ship full write-ahead log to replica → heavy I/O.
  Backup = snapshot entire EBS volume → I/O impact on primary.

Aurora (separated):
  Compute: Stateless query processors. Can be replaced in seconds.
  Storage: Independent distributed system. Auto-scales. Self-heals.
  
  Benefits:
    - Scale compute independently of storage
    - Replace a crashed instance without moving data
    - Add read replicas instantly (they connect to shared storage)
    - Backup is free (continuous to S3, no I/O impact)
    - Point-in-time restore to any second in the last 35 days
```

---

## Distributed Storage Layer — 6-Way Replication

### How It Works

```
Every write to Aurora:
  1. Writer instance sends REDO log record to storage layer
  2. Storage layer writes the redo log to 6 copies (2 per AZ, 3 AZs)
  3. Write is acknowledged when 4 of 6 copies confirm (quorum)
  4. Storage layer applies redo log to data pages asynchronously

Why 6 copies?
  - Lose an entire AZ (2 copies): 4 remaining → still have quorum for writes
  - Lose an AZ + 1 additional failure: 3 remaining → still have quorum for reads
  
  Durability: 99.999999999% (11 nines) — same as S3

Storage segments:
  Data is divided into 10 GB segments, called "Protection Groups"
  Each segment is replicated 6 ways independently
  128 TB max → up to 12,800 protection groups
  A single segment failure: repair in ~10 seconds (copy 10 GB from peers)
```

### Self-Healing

```
Continuous background processes:
  - Monitor every segment for bit rot, corruption, disk failure
  - If a segment is damaged: reconstruct from remaining copies
  - No human intervention needed
  - Repair time for a 10 GB segment: ~10 seconds (within SSD speed limits)
  
  Mean time to repair a segment: 10 seconds
  Mean time to have 2 failures in one protection group: millions of years
  → Effectively zero data loss probability
```

---

## Quorum Writes and Reads

### Write Quorum

```
Write requires 4/6 copies to acknowledge:
  W = 4 (write quorum)
  
  Why 4? Because:
    - If one AZ is down (2 copies unavailable): 4 remaining → still writable
    - If 2 random copies fail: still 4 available → still writable
    - Only fails if 3+ copies fail simultaneously → astronomically unlikely

Write latency:
  Writer sends redo log to all 6 storage nodes in parallel.
  Waits for fastest 4 responses. Ignores slowest 2.
  This MASKS slow disk operations and network hiccups.
  
  Typical write latency: 4-6ms (vs 10-20ms for standard MySQL on EBS)
```

### Read Quorum

```
Read requires 3/6 copies (for strong consistency during recovery):
  R = 3 (read quorum)
  
  W + R > N (4 + 3 > 6) → guarantees read-after-write consistency

Normal reads:
  DON'T use quorum reads for every SELECT (too expensive).
  Instead: Read from the local buffer cache on the compute instance.
  Buffer cache miss: Read from ONE storage node (any of the 6 copies).
  This is safe because all 6 copies converge quickly (redo log applied in <10ms).
  
  Quorum reads only used during crash recovery to determine the latest state.
```

---

## Read Replicas — Up to 15

### How Read Replicas Work in Aurora

```
Traditional MySQL replication:
  Primary → ships entire binary log → Replica applies
  Lag: 100ms-10s (depends on write volume)
  Replicas: Max 5

Aurora replication:
  Primary → writes to SHARED storage layer
  Replicas → read from the SAME shared storage
  Lag: Typically <20ms (just cache invalidation delay)
  Replicas: Up to 15

Why so fast?
  Replicas don't replay the write-ahead log.
  They read directly from the shared storage.
  The primary sends a tiny "cache invalidation" message to replicas.
  Replica updates its buffer cache → sees new data within ~20ms.

  ┌─────────────┐     redo log    ┌─────────────────┐
  │   Writer     │───────────────→│  Shared Storage  │
  │   Instance   │                │  (6 copies)      │
  └──────────────┘                └────────┬────────┘
                                           │
         cache invalidation                │ direct reads
         (~20ms)                           │
         ┌─────────────────────────────────┤
         │              │                  │
  ┌──────▼──────┐ ┌────▼───────┐  ┌──────▼──────┐
  │  Reader 1    │ │  Reader 2   │  │  Reader 3   │
  └─────────────┘ └────────────┘  └─────────────┘
```

### Reader Endpoint (Load Balancing)

```
Aurora provides a READER ENDPOINT:
  reader.mydb.cluster-xxxxx.us-east-1.rds.amazonaws.com
  
  Automatically load-balances across all read replicas.
  Round-robin by default. Connection-level balancing (not query-level).
  
  Application connects to reader endpoint for all READ queries.
  Application connects to writer endpoint for all WRITE queries.
  
  Typical pattern:
    WRITE: writer-endpoint (1 instance)
    READ:  reader-endpoint (load-balanced across 15 replicas)
    
    Read-heavy workload (95% reads): 1 writer + 15 readers → massive read scalability.
```

---

## Failover Mechanics

### Automatic Failover

```
If the writer instance crashes:

  1. Aurora detects failure (within 10-20 seconds via heartbeat)
  2. Aurora promotes a read replica to be the new writer
  3. Cluster endpoint DNS is updated to point to new writer
  4. Total failover time: typically <30 seconds, often <15 seconds

Why so fast?
  - No data replication needed (shared storage already has all data)
  - Just promote compute: "you're now the writer, accept writes"
  - DNS update (cluster endpoint) propagates quickly

Compare to standard RDS:
  Standard MySQL RDS failover: 1-2 minutes
  Aurora failover: <30 seconds (usually 15-20 seconds)

Failover priority:
  You can assign priority tiers (0-15) to read replicas.
  Tier 0 replicas are promoted first.
  Use: Promote the largest instance (most buffer cache = least cache miss after promotion).
```

### Multi-AZ by Default

```
Aurora is ALWAYS multi-AZ (storage layer spans 3 AZs).
  Even with 1 writer and 0 read replicas: data is in 3 AZs.
  
For compute-level HA:
  Add at least 1 read replica in a different AZ.
  Failover: Writer fails → replica in another AZ promoted → <30s.
  
  Without a replica: Writer fails → Aurora launches a NEW instance → 5-10 minutes.
  WITH a replica: <30 seconds failover. Always have at least 1 replica.
```

---

## Aurora Global Database — Multi-Region

### Architecture

```
┌─────────────────────────┐         ┌─────────────────────────┐
│    PRIMARY REGION        │         │   SECONDARY REGION       │
│    (us-east-1)           │         │   (eu-west-1)           │
│                          │         │                          │
│  Writer + Readers        │ Async   │  Readers only            │
│  (full read/write)       │ ←─────→│  (read-only, <1s lag)   │
│                          │ repl.   │                          │
│  Shared Storage (6 copies)│         │  Shared Storage (6 copies)│
└─────────────────────────┘         └─────────────────────────┘

Cross-region replication lag: typically <1 second (using dedicated replication infrastructure)
Promote secondary to primary: <1 minute (for disaster recovery)
```

### Use Cases

```
1. Disaster Recovery:
   Primary region fails entirely → promote secondary region in <1 minute.
   RPO (data loss): <1 second of writes.
   RTO (downtime): <1 minute.

2. Low-latency global reads:
   US users read from us-east-1. EU users read from eu-west-1.
   <1 second replication lag → acceptable for most read workloads.
   
3. Regional data residency:
   Keep a copy of data in EU for GDPR compliance.
   Writes go to primary (US). EU replica serves local reads.
```

---

## Aurora Serverless v2

### How It Works

```
Aurora Serverless v2: Auto-scales compute capacity in real-time.

  Min ACU: 0.5 ACU (1 ACU ≈ 2 GB RAM, ~1 vCPU)
  Max ACU: 128 ACU (256 GB RAM, ~64 vCPU)
  
  Scaling increment: 0.5 ACU
  Scaling speed: Scales up in seconds (not minutes)
  Scales to zero: NO (Serverless v1 could, v2 has minimum 0.5 ACU)

When to use:
  ✅ Unpredictable traffic (spikes and quiet periods)
  ✅ Dev/test environments (pay only for usage)
  ✅ Variable workloads (batch processing + idle periods)
  
  ❌ Steady high-throughput (provisioned is cheaper)
  ❌ Need to scale to zero (use v1 or turn off the cluster)

Cost:
  $0.12 per ACU-hour (us-east-1)
  0.5 ACU idle: $0.06/hour = $43/month (minimum cost)
  128 ACU peak: $15.36/hour (scales down when traffic drops)
```

---

## Connection Management — RDS Proxy

### The Connection Problem

```
Problem: Aurora supports up to ~5000 connections (depends on instance size).
  Lambda functions: Each invocation opens a new connection.
  1000 concurrent Lambda invocations = 1000 connections.
  
  At scale: Connection exhaustion → "Too many connections" error → outage.

Solution: RDS Proxy
  Sits between application and Aurora.
  Maintains a pool of connections to the database.
  Multiplexes application connections onto fewer database connections.
  
  1000 Lambda connections → RDS Proxy → 100 actual DB connections.
  10x reduction in database connection load.
```

### Architecture

```
Lambda / ECS / EC2                RDS Proxy             Aurora Cluster
┌──────────┐                  ┌───────────────┐      ┌──────────────┐
│ App 1     │──conn──→        │               │      │              │
│ App 2     │──conn──→        │  Connection   │─────→│  Writer      │
│ App 3     │──conn──→        │  Pool         │      │              │
│ ...       │──conn──→        │  (100 conns)  │─────→│  Reader 1    │
│ App 1000  │──conn──→        │               │      │  Reader 2    │
└──────────┘                  └───────────────┘      └──────────────┘
     1000 app connections            →            100 DB connections

Additional benefits:
  - Failover transparent: Proxy handles failover to new writer (no client-side retry)
  - IAM auth: Proxy authenticates via IAM (no DB passwords in Lambda env vars)
  - Connection reuse: Lambda cold starts don't open new DB connections
```

---

## Storage Auto-Scaling and Limits

```
Aurora storage:
  Minimum: 10 GB
  Maximum: 128 TB
  Scaling: Automatic (no downtime, no provisioning)
  
  You never specify storage size. Aurora allocates as data grows.
  Storage is billed per GB-month actually used (not provisioned).
  
  Important: Storage does NOT shrink when data is deleted.
    Delete 50 GB of data → storage allocated stays the same.
    To reclaim: Clone the cluster or export/import.
  
  Cost: $0.10/GB/month (Aurora MySQL), $0.10/GB/month (Aurora PostgreSQL)
```

---

## Backups, Snapshots, and PITR

```
Continuous backup (automatic):
  Aurora continuously backs up to S3 (no performance impact).
  Retention: 1-35 days (configurable).
  Point-in-time restore: Recover to ANY second within the retention period.
  
  PITR creates a NEW cluster from the backup.
  Original cluster is unchanged.

Manual snapshots:
  CREATE SNAPSHOT → stored in S3 indefinitely (until you delete).
  Use for: Long-term backups, copy to another region, share with another account.
  
  Snapshot restore: Creates a new cluster (10-30 minutes depending on size).

Backtracking (Aurora MySQL only):
  "Undo" the database to a previous point in time WITHOUT creating a new cluster.
  Rewind the existing cluster by up to 72 hours.
  Much faster than PITR (seconds vs minutes).
  Use case: Undo a bad migration, recover from accidental DELETE.
```

---

## Performance Characteristics

```
Throughput:
  Aurora MySQL: 5x MySQL throughput
  Aurora PostgreSQL: 3x PostgreSQL throughput
  
  Why faster?
    - Fewer I/O operations (only redo logs sent to storage, not full pages)
    - Parallel query: Pushes processing to storage layer
    - Adaptive locking, batch processing optimizations

Latency:
  Write: 4-6ms (quorum ack from storage)
  Read (cache hit): <1ms (buffer cache)
  Read (cache miss): 2-5ms (fetch from storage)
  
  Replica lag: <20ms (cache invalidation)
  Cross-region (Global Database): <1 second

Connections:
  db.r6g.large: ~1000 max connections
  db.r6g.2xlarge: ~2000 max connections
  db.r6g.16xlarge: ~5000 max connections
  With RDS Proxy: Effectively unlimited application connections

IOPS:
  No provisioned IOPS needed. Aurora auto-manages I/O.
  Burst: Up to millions of IOPS (storage layer parallelism).
  Cost: $0.20 per million I/O requests (Aurora MySQL)
```

---

## Aurora vs Standard RDS vs DynamoDB

| Aspect | Aurora | Standard RDS (MySQL) | DynamoDB |
|---|---|---|---|
| **Type** | Relational (SQL) | Relational (SQL) | NoSQL (Key-Value/Document) |
| **Storage** | Distributed, 6 copies | Single EBS volume | Managed, distributed |
| **Max storage** | 128 TB | 64 TB (io2) | Unlimited |
| **Read replicas** | 15 | 5 | DAX (caching) or Global Tables |
| **Failover** | <30 seconds | 1-2 minutes | Instant (multi-AZ by default) |
| **Replica lag** | <20ms | 100ms-10s | 0 (strongly consistent reads available) |
| **Auto-scale compute** | Serverless v2 | No | Yes (on-demand mode) |
| **Auto-scale storage** | Yes | No (provision EBS) | Yes |
| **Throughput** | 5x MySQL | Baseline MySQL | Unlimited (with $$$) |
| **Multi-region** | Global Database (<1s lag) | Cross-region read replicas | Global Tables (active-active) |
| **Price (compute)** | ~$0.29/hr (db.r6g.large) | ~$0.17/hr (db.m6g.large) | Pay per request or provisioned |
| **Best for** | Complex queries, joins, transactions | Simple relational, cost-sensitive | Key-value lookups, massive scale |

### Interview Decision Script

> *"I'd choose Aurora when I need relational semantics (joins, transactions, complex queries) with high availability and read scalability. Aurora's distributed storage gives me 6-way replication with <30s failover — much better than standard RDS. For read-heavy workloads, 15 read replicas with <20ms lag handle massive read scale. I'd choose DynamoDB when I don't need joins/transactions and need unlimited horizontal scaling for key-value access patterns. Standard RDS only for cost-sensitive workloads with lower availability requirements."*

---

## Cost Optimization

```
Aurora cost components:
  1. Compute: Instance-hours ($0.29/hr for db.r6g.large)
  2. Storage: $0.10/GB/month (only what you use)
  3. I/O: $0.20 per million I/O requests
  4. Backup: Free up to cluster size. $0.021/GB/month beyond.
  5. Data transfer: Cross-AZ free. Cross-region: standard rates.

Optimization strategies:
  1. Right-size instances: Monitor CPU/memory. Don't over-provision.
  2. Use Serverless v2 for variable workloads (pay per ACU-hour).
  3. Use Reserved Instances for steady workloads (up to 72% savings).
  4. Read replicas instead of larger writer (cheaper to scale reads).
  5. Use RDS Proxy to reduce connection overhead (fewer compute resources needed).
  6. Avoid Aurora for simple key-value (DynamoDB is cheaper for that pattern).

Example monthly cost (medium workload):
  1 writer (db.r6g.xlarge): $0.58/hr × 730 = $423
  2 readers (db.r6g.large): $0.29/hr × 730 × 2 = $423
  Storage (500 GB): $50
  I/O (100M requests): $20
  RDS Proxy: $0.015/hr × 730 = $11
  Total: ~$927/month
```

---

## Real-World Production Patterns

### Pattern 1: Read-Heavy Web Application

```
Architecture:
  Writer (db.r6g.2xlarge) → handles all writes (5% of queries)
  5 Readers (db.r6g.large) → handle all reads (95% of queries)
  Reader endpoint → load-balances across 5 readers
  RDS Proxy → connection pooling for Lambda/ECS
  
Scale:
  Writes: 5K/sec (single writer handles easily)
  Reads: 50K/sec (10K/reader × 5 readers)
  
Cost: ~$2,500/month (vs $5K+ for equivalent DynamoDB read throughput)
```

### Pattern 2: Multi-Region with Global Database

```
Architecture:
  Primary: us-east-1 (writer + 3 readers)
  Secondary: eu-west-1 (5 readers, read-only)
  Secondary: ap-southeast-1 (3 readers, read-only)
  
  Writes: All go to us-east-1.
  Reads: Served from nearest region (<20ms local, <1s cross-region lag).
  DR: If us-east-1 fails → promote eu-west-1 to primary in <1 minute.
```

### Pattern 3: Serverless with Lambda

```
Architecture:
  API Gateway → Lambda → RDS Proxy → Aurora Serverless v2
  
  Aurora Serverless v2: Scales 0.5-64 ACU based on load.
  RDS Proxy: Handles Lambda connection bursts.
  
  Off-peak: 0.5 ACU ($0.06/hr)
  Peak: 32 ACU ($3.84/hr)
  Average: ~$200/month for a medium SaaS app.
```

---

## Interview Talking Points & Scripts

### Script 1: Why Aurora Over Standard RDS

> *"Aurora separates compute from storage. The storage layer is a distributed system with 6 copies across 3 AZs, using quorum writes (4/6 must acknowledge). This gives 11-nines durability and <30-second failover. Standard RDS uses a single EBS volume — if it fails, you're restoring from backup (minutes to hours). Aurora's replication is also fundamentally better: replicas share the same storage, so replica lag is <20ms (vs seconds for standard MySQL binlog replication). And you get 15 replicas vs 5."*

### Script 2: Failover and HA

> *"Aurora failover is fast because there's no data to copy. The storage is shared — the new writer just needs to start accepting writes on the existing storage. Typical failover: 15-30 seconds. To minimize failover time, I'd have at least one read replica with priority 0 (promoted first) on a large instance (pre-warmed buffer cache). Compare this to standard RDS: 1-2 minutes because it needs to promote a replica that has been asynchronously replicating via binlog. For multi-region DR, Aurora Global Database replicates with <1 second lag and can promote a secondary region in under 1 minute."*

### Script 3: Read Scaling

> *"For a read-heavy workload — say 95% reads — I'd use Aurora with 1 writer and up to 15 read replicas behind the reader endpoint. The reader endpoint load-balances connections across all replicas. Each replica sees new data within 20ms of the write (they share the same storage, just need cache invalidation). For 50K reads/sec: 5 replicas each handling 10K/sec. If I need more, I add replicas — zero configuration change to the application (reader endpoint auto-includes new replicas)."*

### Script 4: Connection Management

> *"For serverless architectures (Lambda + Aurora), connection exhaustion is the biggest risk. Each Lambda invocation opens a connection — at 1000 concurrent invocations, that's 1000 connections against a database that supports maybe 2000. Solution: RDS Proxy. It maintains a pool of ~100 actual database connections and multiplexes thousands of application connections onto them. Lambda functions connect to the proxy endpoint instead. The proxy also handles failover transparently — if the writer fails, the proxy seamlessly reconnects to the new writer without the application knowing."*

### Script 5: Aurora vs DynamoDB Decision

> *"The decision between Aurora and DynamoDB comes down to access patterns. If I need relational operations — joins across tables, complex WHERE clauses, transactions spanning multiple rows, aggregations — Aurora is the right choice. It's a relational database with cloud-native reliability. If my access pattern is simple key-value lookups or single-table queries with known access patterns, DynamoDB is better — it scales horizontally without limits and charges per request. For most web applications that start with a relational model, Aurora is the safer default. You can always extract hot paths to DynamoDB later."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Aurora is just MySQL on AWS"

**Bad**: Treating Aurora as identical to standard MySQL RDS.
**Fix**: Aurora's distributed storage (6 copies, quorum writes, <30s failover) is fundamentally different from MySQL on a single EBS volume.

### ❌ Mistake 2: Not mentioning storage-compute separation

**Bad**: "Aurora replicates data to read replicas."
**Fix**: Replicas SHARE the storage. There's no data replication to replicas — they read from the same distributed storage. Only cache invalidation messages are sent (~20ms lag).

### ❌ Mistake 3: Ignoring connection limits

**Bad**: "Lambda connects directly to Aurora."
**Fix**: Lambda connection storms exhaust DB connections. Use RDS Proxy for connection pooling and multiplexing.

### ❌ Mistake 4: "Aurora scales infinitely"

**Bad**: Implying Aurora can handle any workload.
**Fix**: Aurora has limits: single writer instance, max ~5000 connections, 128 TB storage. For unlimited horizontal write scaling, you need DynamoDB or sharded databases.

### ❌ Mistake 5: Not mentioning failover time

**Bad**: "Aurora is highly available" without specifics.
**Fix**: <30 seconds failover (with a read replica). Without a replica: 5-10 minutes (new instance must be launched). Always maintain at least 1 replica for fast failover.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│              AWS RDS AURORA DEEP DIVE — CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ARCHITECTURE: Storage-compute separation.                           │
│    Storage: Distributed, 6 copies, 3 AZs, self-healing.            │
│    Compute: Stateless. Writer + up to 15 readers.                   │
│                                                                      │
│  WRITES: Quorum 4/6. Latency: 4-6ms. Only redo logs sent.          │
│  READS: From buffer cache (<1ms) or storage (2-5ms).               │
│  REPLICA LAG: <20ms (cache invalidation, not data copy).            │
│                                                                      │
│  FAILOVER: <30s (with replica). Promote reader to writer.           │
│  GLOBAL DB: <1s cross-region lag. Promote in <1 minute for DR.      │
│                                                                      │
│  SCALING:                                                            │
│    Read: Add replicas (up to 15). Reader endpoint load-balances.    │
│    Write: Scale UP the writer instance. Or Aurora Serverless v2.    │
│    Storage: Auto-scales 10 GB → 128 TB. No provisioning.           │
│                                                                      │
│  SERVERLESS v2: 0.5-128 ACU. Scales in seconds. Per-ACU billing.   │
│  RDS PROXY: Connection pooling. Essential for Lambda workloads.     │
│                                                                      │
│  BACKUPS: Continuous to S3. PITR to any second (1-35 days).         │
│  BACKTRACK: Rewind cluster in-place (MySQL only, up to 72 hours).   │
│                                                                      │
│  PERFORMANCE: 5x MySQL, 3x PostgreSQL throughput.                   │
│                                                                      │
│  COST: ~$0.29/hr (db.r6g.large) + $0.10/GB storage + $0.20/M I/O. │
│                                                                      │
│  vs RDS: Better HA, faster failover, more replicas, auto-storage.   │
│  vs DynamoDB: Better for relational/complex queries. DDB for K-V.   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 2: Database Selection** — When to choose relational vs NoSQL
- **Topic 49: DynamoDB Deep Dive** — Comparison with Aurora
- **Topic 50: PostgreSQL Deep Dive** — Aurora is PostgreSQL-compatible
- **Topic 52: Architecting on AWS** — Aurora in AWS architecture

---

*This document is part of the System Design Interview Deep Dive series.*
