# Design a Database Migration System - Hello Interview Framework

> **Question**: Design a large-scale database migration system that can migrate petabytes of data from one database technology to another (e.g., Oracle → PostgreSQL, MySQL → DynamoDB, on-prem → cloud) with zero downtime, data consistency guarantees, and automatic schema translation.
>
> **Asked at**: Amazon, Google, Uber, Stripe, Netflix
>
> **Difficulty**: Hard | **Level**: Staff/Principal

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Full Data Migration**: Migrate entire database contents (schema + data) from source to target database, supporting heterogeneous migrations (e.g., Oracle → PostgreSQL, MySQL → DynamoDB).
2. **Schema Translation**: Automatically translate schema definitions (tables, indexes, constraints, stored procedures, views) from source DB dialect to target DB dialect, flagging incompatible constructs.
3. **Change Data Capture (CDC)**: Continuously replicate ongoing changes (INSERT, UPDATE, DELETE) from the source database to the target during migration, keeping the target synchronized in near real-time (< 5 second replication lag).
4. **Zero-Downtime Cutover**: Support seamless cutover from source to target with zero application downtime — applications continue reading/writing during the entire migration.
5. **Data Validation**: Continuously validate that source and target are consistent — row counts, checksums, sampled row comparisons — and generate correctness reports.

#### Nice to Have (P1)
- Automatic query/ORM translation (SQL dialect rewriting for applications).
- Rollback capability (reverse CDC from target → source if cutover fails).
- Migration progress dashboard with ETA and throughput metrics.
- Data transformation during migration (column renaming, type conversion, data masking).
- Multi-database migration orchestration (migrate 50 databases in a dependency-aware order).

#### Below the Line (Out of Scope)
- Application code refactoring for new database APIs.
- Database performance tuning post-migration.
- Hardware provisioning for target databases.
- Network setup between on-prem and cloud (assume VPN/DirectConnect exists).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Migration Throughput** | 1 TB/hour sustained for bulk load | Migrate 50 TB database within a weekend maintenance window |
| **CDC Replication Lag** | < 5 seconds during steady state | Applications must see consistent data on target before cutover |
| **Data Consistency** | 100% data correctness (zero data loss, zero corruption) | Financial/transactional data cannot tolerate any discrepancy |
| **Downtime During Cutover** | < 30 seconds (effectively zero for most apps) | Business SLA requires 99.99% availability |
| **Availability** | 99.9% for migration service itself | Migration must not stall or require restart |
| **Resumability** | Resume from last checkpoint after any failure | Multi-day migrations cannot restart from scratch |
| **Idempotency** | All operations idempotent | Network retries and restarts must not cause duplicates |
| **Schema Accuracy** | 95%+ automatic translation (manual review for rest) | Reduce human effort; flag edge cases |
| **Concurrency** | Support 50+ simultaneous migrations | Large enterprises migrate hundreds of databases |

### Capacity Estimation

```
Typical large migration scenario:
  Source DB: Oracle 19c (on-premise), 50 TB data, 500 tables
  Target DB: PostgreSQL 16 (AWS RDS), cloud-native
  
Bulk Load Phase:
  Data volume: 50 TB
  Target throughput: 1 TB/hour
  Estimated duration: ~50 hours (~2 days)
  
  Row count: ~200 billion rows across 500 tables
  Average row size: ~250 bytes
  Parallelism: 50 tables concurrently × 10 chunks per table = 500 parallel streams
  Per-stream throughput: 1 TB / 500 = 2 GB/hour per stream
  
  Network: 50 TB / 50 hours = 1 TB/hour = ~2.2 Gbps sustained
  Worker nodes: 50 (each handles 10 streams at 200 MB/hour each)

CDC Phase (during bulk load):
  Source write rate: ~50K transactions/sec
  CDC event size: ~500 bytes (includes before/after image)
  CDC throughput: 50K × 500 bytes = 25 MB/sec
  CDC daily volume: 25 MB/sec × 86400 = 2.16 TB/day
  Buffer (Kafka): 7 days × 2.16 TB = 15 TB

Validation Phase:
  Row-level checksum: 200B rows × 32 bytes (MD5) = 6.4 TB of checksums
  Sampling: 1% sample = 2B rows compared
  Validation throughput: 100M rows/hour
  Full validation time: 200B / 100M = 2000 hours 
    → sampling-based: 2B / 100M = 20 hours

Storage for migration metadata:
  Checkpoint state: ~50 GB (bookmark per chunk per table)
  Schema mappings: ~10 MB
  Validation reports: ~1 GB
  
Total infrastructure for migration:
  50 worker nodes (bulk load)
  10 CDC consumers
  Kafka cluster (20 TB capacity)
  Coordinator service (3 nodes HA)
  Validation workers (20 nodes)
```

---

## 2️⃣ Core Entities

### Entity 1: Migration Job
```java
public class MigrationJob {
    private final String jobId;                    // UUID
    private final String jobName;                  // Human-readable: "Oracle-to-PG-Orders-DB"
    private final DatabaseConnection source;       // Source database connection
    private final DatabaseConnection target;       // Target database connection
    private final MigrationPhase currentPhase;     // SCHEMA_TRANSLATION, BULK_LOAD, CDC, CUTOVER, COMPLETED
    private final MigrationStatus status;          // PENDING, RUNNING, PAUSED, FAILED, COMPLETED
    private final SchemaMapping schemaMapping;     // Source-to-target schema translation rules
    private final MigrationConfig config;          // Parallelism, batch sizes, validation settings
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Map<String, TableMigrationState> tableStates;  // Per-table progress
}

public class DatabaseConnection {
    private final DatabaseType type;               // ORACLE, POSTGRESQL, MYSQL, DYNAMODB, etc.
    private final String host;
    private final int port;
    private final String databaseName;
    private final String username;
    private final String encryptedPassword;        // KMS-encrypted
    private final Map<String, String> connectionProperties; // SSL, timeout, etc.
}

public enum MigrationPhase {
    SCHEMA_TRANSLATION,  // Convert schema from source to target dialect
    BULK_LOAD,           // Full snapshot copy of existing data
    CDC_SYNC,            // Continuous replication of ongoing changes
    VALIDATION,          // Data consistency verification
    CUTOVER,             // Switch application traffic to target
    COMPLETED            // Migration done, cleanup
}
```

### Entity 2: Table Migration State
```java
public class TableMigrationState {
    private final String sourceSchema;             // "ORDERS"
    private final String sourceTable;              // "ORDER_ITEMS"
    private final String targetSchema;             // "public"
    private final String targetTable;              // "order_items"
    private final long totalRows;                  // Estimated row count from source
    private final long migratedRows;               // Rows copied so far
    private final long cdcEventsApplied;           // CDC events replayed
    private final List<ChunkCheckpoint> chunks;    // Chunk-level progress
    private final TableMigrationStatus status;     // PENDING, LOADING, CDC_SYNCING, VALIDATED, DONE
    private final Instant lastCheckpointAt;
    private final String lastError;
}

public class ChunkCheckpoint {
    private final String chunkId;                  // "order_items_chunk_42"
    private final String partitionKey;             // Primary key range: "id BETWEEN 1000001 AND 2000000"
    private final long startKey;                   // Chunk start (for resumability)
    private final long endKey;                     // Chunk end
    private final long rowsMigrated;               // Rows copied in this chunk
    private final ChunkStatus status;              // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private final String checksum;                 // MD5 of chunk data (for validation)
    private final Instant completedAt;
}
```

### Entity 3: Schema Mapping
```java
public class SchemaMapping {
    private final String jobId;
    private final DatabaseType sourceType;
    private final DatabaseType targetType;
    private final List<TableMapping> tableMappings;
    private final List<TypeMapping> typeMappings;          // Oracle NUMBER(10) → PostgreSQL BIGINT
    private final List<IncompatibleConstruct> incompatibles; // Items needing manual review
    private final double autoTranslationRate;               // e.g., 97% auto, 3% manual
}

public class TableMapping {
    private final String sourceSchema;
    private final String sourceTable;
    private final String targetSchema;
    private final String targetTable;
    private final List<ColumnMapping> columns;
    private final List<IndexMapping> indexes;
    private final List<ConstraintMapping> constraints;
    private final List<String> warnings;           // "BITMAP index not supported, converted to B-tree"
}

public class ColumnMapping {
    private final String sourceColumn;
    private final String sourceType;               // "NUMBER(10,2)"
    private final String targetColumn;
    private final String targetType;               // "NUMERIC(10,2)"
    private final String transformExpression;       // Optional: "UPPER(source_col)" or data masking
    private final boolean requiresManualReview;
}
```

### Entity 4: CDC Event
```java
public class CDCEvent {
    private final String eventId;                  // Unique event identifier
    private final CDCOperationType operation;      // INSERT, UPDATE, DELETE
    private final String sourceSchema;
    private final String sourceTable;
    private final Map<String, Object> beforeImage; // Row state before change (for UPDATE/DELETE)
    private final Map<String, Object> afterImage;  // Row state after change (for INSERT/UPDATE)
    private final Map<String, Object> primaryKey;  // Primary key columns and values
    private final long sourceTransactionId;        // Transaction ID for ordering
    private final long sourceLogPosition;          // WAL/redo log position (for checkpointing)
    private final Instant sourceTimestamp;          // When the change happened in source DB
    private final Instant captureTimestamp;         // When CDC captured it
}

public enum CDCOperationType {
    INSERT, UPDATE, DELETE, SCHEMA_CHANGE
}
```

---

## 3️⃣ API Design

### 1. Create Migration Job
```
POST /api/v1/migrations

Request:
{
  "name": "Oracle-to-PG-Orders-DB",
  "source": {
    "type": "ORACLE",
    "host": "oracle-prod.internal.company.com",
    "port": 1521,
    "database": "ORDERSDB",
    "username": "migration_reader",
    "password_secret_arn": "arn:aws:secretsmanager:us-east-1:123456:secret:oracle-migration"
  },
  "target": {
    "type": "POSTGRESQL",
    "host": "orders-pg.cluster-abc123.us-east-1.rds.amazonaws.com",
    "port": 5432,
    "database": "orders",
    "username": "migration_writer",
    "password_secret_arn": "arn:aws:secretsmanager:us-east-1:123456:secret:pg-migration"
  },
  "config": {
    "parallelism": 50,
    "batch_size": 10000,
    "tables_include": ["ORDERS.*"],
    "tables_exclude": ["ORDERS.TEMP_*"],
    "enable_cdc": true,
    "enable_validation": true,
    "validation_sample_rate": 0.01
  }
}

Response (201 Created):
{
  "job_id": "mig_abc123",
  "status": "PENDING",
  "created_at": "2025-01-10T10:00:00Z",
  "phases": ["SCHEMA_TRANSLATION", "BULK_LOAD", "CDC_SYNC", "VALIDATION", "CUTOVER"],
  "estimated_duration_hours": 52
}
```

### 2. Get Migration Status
```
GET /api/v1/migrations/{job_id}/status

Response (200 OK):
{
  "job_id": "mig_abc123",
  "name": "Oracle-to-PG-Orders-DB",
  "current_phase": "BULK_LOAD",
  "status": "RUNNING",
  "started_at": "2025-01-10T10:05:00Z",
  "progress": {
    "schema_translation": {
      "status": "COMPLETED",
      "auto_translated": 485,
      "manual_review_needed": 15,
      "total_tables": 500
    },
    "bulk_load": {
      "status": "IN_PROGRESS",
      "tables_completed": 320,
      "tables_in_progress": 50,
      "tables_pending": 130,
      "total_tables": 500,
      "rows_migrated": 120000000000,
      "total_rows_estimated": 200000000000,
      "bytes_transferred": "30 TB",
      "throughput_mbps": 2200,
      "eta_hours": 20,
      "percent_complete": 60.0
    },
    "cdc": {
      "status": "RUNNING",
      "replication_lag_seconds": 3.2,
      "events_captured": 45000000,
      "events_applied": 44800000,
      "events_pending": 200000
    }
  },
  "errors": [],
  "warnings": ["Table ORDERS.LEGACY_ARCHIVE has no primary key — using ROWID for CDC"]
}
```

### 3. Get Schema Translation Report
```
GET /api/v1/migrations/{job_id}/schema-mapping

Response (200 OK):
{
  "job_id": "mig_abc123",
  "source_type": "ORACLE",
  "target_type": "POSTGRESQL",
  "auto_translation_rate": 0.97,
  "summary": {
    "tables": { "total": 500, "auto": 485, "manual": 15 },
    "columns": { "total": 8500, "auto": 8350, "manual": 150 },
    "indexes": { "total": 1200, "auto": 1150, "incompatible": 50 },
    "constraints": { "total": 600, "auto": 590, "manual": 10 },
    "stored_procedures": { "total": 200, "auto": 50, "manual": 150 },
    "views": { "total": 100, "auto": 85, "manual": 15 }
  },
  "incompatible_constructs": [
    {
      "type": "STORED_PROCEDURE",
      "name": "ORDERS.CALC_SHIPPING_COST",
      "reason": "Uses Oracle-specific SYS_CONTEXT and DBMS_LOB. No direct PostgreSQL equivalent.",
      "suggestion": "Rewrite using PostgreSQL PL/pgSQL with current_setting() and lo_* functions."
    },
    {
      "type": "INDEX",
      "name": "IDX_ORDERS_BITMAP_STATUS",
      "reason": "BITMAP index not supported in PostgreSQL.",
      "suggestion": "Converted to B-tree index. Consider partial index for low-cardinality column."
    }
  ],
  "table_mappings": [
    {
      "source": "ORDERS.ORDER_ITEMS",
      "target": "public.order_items",
      "columns": [
        { "source": "ORDER_ITEM_ID NUMBER(19)", "target": "order_item_id BIGINT", "auto": true },
        { "source": "AMOUNT NUMBER(10,2)", "target": "amount NUMERIC(10,2)", "auto": true },
        { "source": "CREATED_DATE DATE", "target": "created_date TIMESTAMP", "auto": true,
          "warning": "Oracle DATE includes time component; mapped to TIMESTAMP" }
      ]
    }
  ]
}
```

### 4. Start Cutover
```
POST /api/v1/migrations/{job_id}/cutover

Request:
{
  "strategy": "ZERO_DOWNTIME",
  "max_allowed_lag_seconds": 0,
  "rollback_enabled": true,
  "pre_cutover_checks": ["VALIDATE_ROW_COUNTS", "VALIDATE_CHECKSUMS", "CHECK_CDC_LAG"]
}

Response (200 OK):
{
  "job_id": "mig_abc123",
  "cutover_id": "cut_xyz789",
  "status": "IN_PROGRESS",
  "steps": [
    { "step": "PRE_CUTOVER_VALIDATION", "status": "COMPLETED", "duration_ms": 15000 },
    { "step": "DRAIN_CDC_LAG", "status": "COMPLETED", "duration_ms": 3200 },
    { "step": "PAUSE_SOURCE_WRITES", "status": "IN_PROGRESS", "started_at": "2025-01-12T02:00:05Z" },
    { "step": "APPLY_FINAL_CDC", "status": "PENDING" },
    { "step": "VERIFY_CONSISTENCY", "status": "PENDING" },
    { "step": "SWITCH_TRAFFIC", "status": "PENDING" },
    { "step": "ENABLE_TARGET_WRITES", "status": "PENDING" }
  ]
}
```

### 5. Validate Migration
```
POST /api/v1/migrations/{job_id}/validate

Request:
{
  "validation_type": "FULL",
  "sample_rate": 0.01,
  "tables": ["*"],
  "checks": ["ROW_COUNT", "CHECKSUM", "SAMPLE_COMPARE", "CONSTRAINT_CHECK"]
}

Response (200 OK):
{
  "job_id": "mig_abc123",
  "validation_id": "val_def456",
  "status": "COMPLETED",
  "results": {
    "row_count_match": {
      "passed": 498,
      "failed": 2,
      "failures": [
        { "table": "order_items", "source": 50000234, "target": 50000231, "diff": 3,
          "reason": "3 rows in-flight during CDC at snapshot time — will converge" }
      ]
    },
    "checksum_match": {
      "passed": 500,
      "failed": 0
    },
    "sample_compare": {
      "rows_sampled": 2000000000,
      "rows_matched": 1999999998,
      "rows_mismatched": 2,
      "mismatch_rate": 0.000000001,
      "mismatches": [
        { "table": "orders", "pk": {"id": 78923456},
          "diff": "source.amount=100.10, target.amount=100.1",
          "reason": "Trailing zero stripped in NUMERIC conversion — functionally equivalent" }
      ]
    }
  },
  "overall": "PASSED_WITH_WARNINGS"
}
```

---

## 4️⃣ Data Flow

### Flow 1: Full Migration Lifecycle
```
1. User creates migration job via API
   ↓
2. SCHEMA TRANSLATION PHASE:
   a. Connect to source DB, introspect full schema (tables, columns, types, indexes, 
      constraints, views, stored procedures, triggers, sequences)
   b. Apply type mapping rules: Oracle → PostgreSQL dialect
   c. Generate DDL for target DB
   d. Flag incompatible constructs for manual review
   e. User approves schema mapping (with manual overrides)
   f. Execute DDL on target DB (create empty tables, indexes, constraints deferred)
   ↓
3. BULK LOAD PHASE (parallel with CDC start):
   a. Snapshot source DB at consistent point (SCN/LSN)
   b. Divide each table into chunks by primary key range
   c. 500 parallel workers read chunks from source
   d. Transform data (type conversions, column mappings)
   e. Batch-insert into target DB (COPY/bulk load for speed)
   f. Checkpoint progress per chunk (resumable)
   g. After each table completes: enable deferred indexes and constraints
   ↓
4. CDC SYNC PHASE (starts simultaneously with bulk load):
   a. Capture changes from source DB transaction log (Oracle Redo → LogMiner, 
      PostgreSQL → WAL logical decoding, MySQL → binlog)
   b. Publish CDC events to Kafka (ordered by transaction)
   c. CDC consumers apply events to target DB
   d. Events before snapshot SCN → discard (already in bulk load)
   e. Events after snapshot SCN → apply to target
   f. Monitor replication lag (target: < 5 seconds)
   ↓
5. VALIDATION PHASE (continuous):
   a. Compare row counts (source vs target)
   b. Compute table-level checksums
   c. Sample random rows and compare column-by-column
   d. Generate validation report
   ↓
6. CUTOVER PHASE:
   a. Pre-cutover: verify CDC lag < 1 second
   b. Pause source writes briefly (< 30 seconds)
   c. Drain remaining CDC events to target
   d. Final consistency check
   e. Switch application connection strings to target
   f. Resume writes (now going to target)
   g. Optional: enable reverse CDC for rollback capability
```

### Flow 2: CDC Event Processing
```
1. Application writes to source DB (INSERT/UPDATE/DELETE)
   ↓
2. Source DB writes to transaction log (WAL/redo log/binlog)
   ↓
3. CDC Connector reads transaction log:
   - Oracle: LogMiner or Oracle GoldenGate
   - PostgreSQL: pgoutput logical decoding
   - MySQL: binlog reader (Debezium)
   ↓
4. CDC event published to Kafka topic: "cdc.{schema}.{table}"
   Partition key: primary key hash (ensures ordering per row)
   ↓
5. CDC Consumer:
   a. Deserialize event
   b. Apply schema transformation (column mapping, type conversion)
   c. Check if event is before or after snapshot point
   d. Apply to target DB:
      - INSERT → INSERT ON CONFLICT DO NOTHING (idempotent)
      - UPDATE → UPDATE WHERE pk = ? (idempotent)
      - DELETE → DELETE WHERE pk = ? (idempotent)
   e. Commit Kafka offset after successful apply
   ↓
6. Replication lag = now() - event.source_timestamp
   Target: < 5 seconds during steady state
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        SOURCE DATABASE                                    │
│              (Oracle / MySQL / SQL Server / MongoDB)                       │
│                                                                            │
│  Transaction Log ────────────────────────────────────┐                    │
│  (WAL / Redo Log / Binlog)                           │                    │
│                                                       │                    │
│  Tables ──────────────────────────────────┐          │                    │
└────────────────────────────────────────────┼──────────┼────────────────────┘
                                             │          │
                    ┌────────────────────────┘          │
                    │  Bulk Read (parallel chunks)       │ CDC Stream
                    ▼                                    ▼
┌─────────────────────────────┐     ┌──────────────────────────────────┐
│    BULK LOAD WORKERS        │     │     CDC CONNECTOR                 │
│    (50 worker nodes)        │     │     (Debezium / LogMiner)         │
│                             │     │                                    │
│  • Chunk tables by PK range │     │  • Read transaction log           │
│  • SELECT in parallel       │     │  • Convert to CDC events          │
│  • Transform data           │     │  • Publish to Kafka               │
│  • Batch INSERT into target │     │  • Track log position (checkpoint)│
│  • Checkpoint per chunk     │     │                                    │
└──────────────┬──────────────┘     └───────────────┬──────────────────┘
               │                                     │
               │                                     ▼
               │                    ┌──────────────────────────────────┐
               │                    │          KAFKA CLUSTER            │
               │                    │  Topics: cdc.{schema}.{table}    │
               │                    │  Partitioned by PK hash           │
               │                    │  Retention: 7 days                │
               │                    │  Throughput: 25 MB/sec CDC events │
               │                    └───────────────┬──────────────────┘
               │                                     │
               │                                     ▼
               │                    ┌──────────────────────────────────┐
               │                    │       CDC APPLIER                 │
               │                    │       (10 consumer nodes)         │
               │                    │                                    │
               │                    │  • Consume CDC events             │
               │                    │  • Apply schema transformation    │
               │                    │  • Idempotent write to target     │
               │                    │  • Track replication lag          │
               │                    └───────────────┬──────────────────┘
               │                                     │
               ▼                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        TARGET DATABASE                                    │
│              (PostgreSQL / DynamoDB / Aurora / CockroachDB)                │
│                                                                            │
│  Receives both bulk load data AND CDC events                              │
│  Eventually becomes fully consistent with source                          │
└──────────────────────────────────────────────────────────────────────────┘
               
               
┌──────────────────────────────────────────────────────────────────────────┐
│                     MIGRATION COORDINATOR                                 │
│                     (3-node HA cluster)                                    │
│                                                                            │
│  ┌────────────────┐  ┌────────────────┐  ┌─────────────────────────┐     │
│  │ Schema          │  │ Job            │  │ Cutover                  │     │
│  │ Translator      │  │ Orchestrator   │  │ Manager                  │     │
│  │                 │  │                │  │                           │     │
│  │ • Introspect    │  │ • Phase mgmt   │  │ • Drain CDC lag          │     │
│  │ • Map types     │  │ • Worker mgmt  │  │ • Pause source writes    │     │
│  │ • Generate DDL  │  │ • Checkpoint   │  │ • Final validation       │     │
│  │ • Flag issues   │  │ • Monitoring   │  │ • Switch traffic         │     │
│  └────────────────┘  └────────────────┘  └─────────────────────────┘     │
│                                                                            │
│  ┌────────────────┐  ┌─────────────────────────────────────────────┐     │
│  │ Validation      │  │ State Store (PostgreSQL + S3)               │     │
│  │ Engine          │  │                                              │     │
│  │                 │  │ • Job metadata, table states, checkpoints    │     │
│  │ • Row counts    │  │ • Schema mappings, validation reports        │     │
│  │ • Checksums     │  │ • CDC offsets, error logs                    │     │
│  │ • Sampling      │  │                                              │     │
│  └────────────────┘  └─────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────────────┘


┌──────────────────────────────────────────────────────────────────────────┐
│                      DASHBOARD / API                                      │
│  • Migration progress (per-table, per-chunk)                              │
│  • CDC replication lag monitoring                                         │
│  • Schema mapping review and approval                                     │
│  • Validation reports                                                     │
│  • Cutover controls (start, rollback)                                     │
└──────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Migration Coordinator** | Orchestrate phases, manage state, monitor health | Java/Spring Boot (3-node HA) | Leader election via ZooKeeper |
| **Schema Translator** | Introspect source, generate target DDL, map types | Custom engine + rules library | Single-threaded per job |
| **Bulk Load Workers** | Parallel chunked reads from source, bulk writes to target | Java workers on K8s | 10-500 pods, auto-scaled |
| **CDC Connector** | Read source transaction log, publish to Kafka | Debezium / Oracle GoldenGate | 1 per source DB (single-threaded log read) |
| **Kafka** | Buffer CDC events, decouple producer/consumer | Apache Kafka | 50+ partitions per table topic |
| **CDC Applier** | Consume CDC events, apply to target DB | Java consumers on K8s | 10-100 pods, partitioned by table |
| **Validation Engine** | Compare source vs target data | Distributed workers | 20+ nodes for parallel validation |
| **State Store** | Job metadata, checkpoints, schema mappings | PostgreSQL + S3 | Managed RDS |
| **Dashboard** | Real-time migration monitoring, cutover controls | React + WebSocket | Stateless frontend |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Consistent Snapshot — Coordinating Bulk Load + CDC Start Point

**The Problem**: We must ensure every row from the source database ends up in the target exactly once. If we start bulk load and CDC independently, we might miss rows (gap) or duplicate rows (overlap). The snapshot must capture a consistent point-in-time from which CDC begins.

```java
public class ConsistentSnapshotManager {

    /**
     * Strategy: take a consistent snapshot of source DB at a known log position,
     * then start CDC from that exact position.
     * 
     * For Oracle:   SCN (System Change Number) — a monotonic counter
     * For PostgreSQL: LSN (Log Sequence Number) — position in WAL
     * For MySQL:  binlog file + position (or GTID)
     * 
     * Key insight: snapshot SCN/LSN acts as the "handoff point" between
     * bulk load and CDC. Everything ≤ SCN is in the snapshot.
     * Everything > SCN comes from CDC.
     */
    
    public SnapshotResult takeConsistentSnapshot(DatabaseConnection source) {
        switch (source.getType()) {
            case ORACLE:
                return takeOracleSnapshot(source);
            case POSTGRESQL:
                return takePostgresSnapshot(source);
            case MYSQL:
                return takeMySQLSnapshot(source);
            default:
                throw new UnsupportedOperationException("Unsupported: " + source.getType());
        }
    }
    
    private SnapshotResult takeOracleSnapshot(DatabaseConnection source) {
        try (Connection conn = source.connect()) {
            // Step 1: Get current SCN (no locks needed!)
            long snapshotScn = queryScalar(conn, "SELECT CURRENT_SCN FROM V$DATABASE");
            
            // Step 2: Each bulk-load query uses AS OF SCN for consistency
            // SELECT * FROM orders AS OF SCN 123456789 WHERE id BETWEEN ? AND ?
            // This gives a read-consistent view at that exact SCN — no locks
            
            // Step 3: Start CDC from SCN + 1
            // LogMiner: start mining redo logs from SCN 123456789
            
            log.info("Oracle snapshot taken at SCN={}", snapshotScn);
            return new SnapshotResult(snapshotScn, Instant.now());
        }
    }
    
    private SnapshotResult takePostgresSnapshot(DatabaseConnection source) {
        try (Connection conn = source.connect()) {
            // Step 1: Create a replication slot (holds WAL from this point)
            execute(conn, "SELECT pg_create_logical_replication_slot('migration_slot', 'pgoutput')");
            
            // Step 2: Start a REPEATABLE READ transaction (snapshot isolation)
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            
            // Step 3: Get the current LSN
            String lsn = queryScalar(conn, "SELECT pg_current_wal_lsn()::text");
            
            // Step 4: Export snapshot for parallel readers
            String snapshotId = queryScalar(conn, "SELECT pg_export_snapshot()");
            
            // Each bulk-load worker joins this snapshot:
            // SET TRANSACTION SNAPSHOT 'snapshot_id';
            // This guarantees all workers see the same consistent state
            
            log.info("PostgreSQL snapshot at LSN={}, snapshot_id={}", lsn, snapshotId);
            return new SnapshotResult(lsn, snapshotId, Instant.now());
        }
    }
    
    private SnapshotResult takeMySQLSnapshot(DatabaseConnection source) {
        try (Connection conn = source.connect()) {
            // Step 1: FLUSH TABLES WITH READ LOCK (brief global lock)
            execute(conn, "FLUSH TABLES WITH READ LOCK");
            
            // Step 2: Record binlog position
            ResultSet rs = query(conn, "SHOW MASTER STATUS");
            String binlogFile = rs.getString("File");
            long binlogPos = rs.getLong("Position");
            
            // Step 3: Start consistent read (InnoDB MVCC)
            execute(conn, "START TRANSACTION WITH CONSISTENT SNAPSHOT");
            
            // Step 4: Release the global lock (held < 1 second)
            execute(conn, "UNLOCK TABLES");
            
            // Workers use the transaction's consistent snapshot for reads
            // CDC starts from binlogFile:binlogPos
            
            log.info("MySQL snapshot at {}:{}", binlogFile, binlogPos);
            return new SnapshotResult(binlogFile, binlogPos, Instant.now());
        }
    }
}
```

**Snapshot Coordination Timeline**:
```
T=0:    Record snapshot point (SCN/LSN/binlog position)
        ┃
T=0:    Start CDC connector from snapshot point ──→ CDC events buffered in Kafka
        ┃                                            (not applied yet to target)
T=0:    Start bulk load workers with snapshot ──→ Read consistent snapshot
        ┃
T=1-50h: Bulk load in progress...                   CDC events accumulating in Kafka
        ┃
T=50h:  Bulk load COMPLETE for all tables
        ┃
T=50h:  Start applying CDC events from Kafka ──→ Events AFTER snapshot applied
        ┃                                         Events BEFORE snapshot discarded
T=51h:  CDC catches up to real-time (lag < 5s)
        ┃
T=51h:  Target is now synchronized with source ✓
```

**Why this is safe**:
```
Row inserted at T=-1h (before snapshot):
  → Included in bulk load snapshot ✓
  → CDC event generated but has SCN < snapshot_scn → DISCARDED ✓
  → Appears exactly once in target ✓

Row inserted at T=+1h (after snapshot started, during bulk load):
  → NOT in bulk load snapshot (it's a point-in-time read)
  → CDC event generated with SCN > snapshot_scn → APPLIED ✓
  → Appears exactly once in target ✓

Row updated at T=+2h (during bulk load):
  → Original value in bulk load snapshot
  → CDC UPDATE event with SCN > snapshot_scn → APPLIED (overwrites original) ✓
  → Final state is correct ✓
```

---

### Deep Dive 2: Parallel Chunked Bulk Loading — Achieving 1 TB/hour

**The Problem**: Loading 50 TB sequentially would take weeks. We need to parallelize reads from source and writes to target while maintaining resumability if any chunk fails.

```java
public class ChunkedBulkLoader {

    private static final int DEFAULT_CHUNK_SIZE = 1_000_000; // 1M rows per chunk
    private final ExecutorService workerPool;
    private final CheckpointStore checkpointStore;
    
    /**
     * Strategy: divide each table into chunks by primary key range,
     * process chunks in parallel, checkpoint after each chunk.
     * 
     * Chunking approaches:
     *   1. Numeric PK: BETWEEN min AND max, divided into equal ranges
     *   2. UUID PK: Hash-based partitioning (mod N)
     *   3. Composite PK: Use first column for range, fallback to ROWNUM/OFFSET
     *   4. No PK: Use ROWID (Oracle) or ctid (PostgreSQL) — last resort
     */
    
    public void loadTable(MigrationJob job, TableMapping tableMapping, SnapshotResult snapshot) {
        String sourceTable = tableMapping.getSourceFQN();
        String targetTable = tableMapping.getTargetFQN();
        
        // Step 1: Analyze table structure to determine chunking strategy
        TableAnalysis analysis = analyzeTable(job.getSource(), sourceTable);
        ChunkStrategy strategy = selectChunkStrategy(analysis);
        
        // Step 2: Generate chunk definitions
        List<ChunkDefinition> chunks = strategy.generateChunks(
            analysis.getMinPK(), analysis.getMaxPK(), analysis.getEstimatedRows(), DEFAULT_CHUNK_SIZE);
        
        log.info("Table {} divided into {} chunks, estimated {} rows",
            sourceTable, chunks.size(), analysis.getEstimatedRows());
        
        // Step 3: Filter out already-completed chunks (for resumability)
        List<ChunkDefinition> pendingChunks = chunks.stream()
            .filter(c -> !checkpointStore.isChunkCompleted(job.getJobId(), sourceTable, c.getChunkId()))
            .toList();
        
        log.info("{} chunks remaining ({} already completed from previous run)",
            pendingChunks.size(), chunks.size() - pendingChunks.size());
        
        // Step 4: Process chunks in parallel
        int parallelism = Math.min(pendingChunks.size(), job.getConfig().getTableParallelism());
        Semaphore semaphore = new Semaphore(parallelism);
        
        List<CompletableFuture<ChunkResult>> futures = pendingChunks.stream()
            .map(chunk -> CompletableFuture.supplyAsync(() -> {
                semaphore.acquireUninterruptibly();
                try {
                    return processChunk(job, tableMapping, chunk, snapshot);
                } finally {
                    semaphore.release();
                }
            }, workerPool))
            .toList();
        
        // Step 5: Wait for all chunks, handle failures
        List<ChunkResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        
        long failedChunks = results.stream().filter(r -> r.getStatus() == ChunkStatus.FAILED).count();
        if (failedChunks > 0) {
            throw new MigrationException(failedChunks + " chunks failed for table " + sourceTable);
        }
        
        // Step 6: Create indexes and enable constraints (deferred until after load)
        enableDeferredIndexes(job.getTarget(), tableMapping);
        enableForeignKeys(job.getTarget(), tableMapping);
    }
    
    private ChunkResult processChunk(MigrationJob job, TableMapping mapping,
                                      ChunkDefinition chunk, SnapshotResult snapshot) {
        try {
            // Read from source with snapshot consistency
            String selectSQL = buildChunkSelectSQL(mapping, chunk, snapshot);
            
            try (Connection sourceConn = job.getSource().connect();
                 Connection targetConn = job.getTarget().connect()) {
                
                // Apply snapshot isolation
                applySnapshotIsolation(sourceConn, snapshot);
                
                // Use COPY for PostgreSQL target (10x faster than INSERT)
                if (job.getTarget().getType() == DatabaseType.POSTGRESQL) {
                    return copyChunkViaPgCopy(sourceConn, targetConn, selectSQL, mapping, chunk);
                }
                
                // Batch INSERT for other targets
                return batchInsertChunk(sourceConn, targetConn, selectSQL, mapping, chunk);
            }
        } catch (Exception e) {
            log.error("Chunk {} failed: {}", chunk.getChunkId(), e.getMessage());
            checkpointStore.markChunkFailed(job.getJobId(), mapping.getSourceTable(), chunk, e);
            return new ChunkResult(chunk.getChunkId(), ChunkStatus.FAILED, 0, e.getMessage());
        }
    }
    
    /**
     * PostgreSQL COPY is 10x faster than batch INSERT.
     * Stream rows directly from source query to target COPY.
     */
    private ChunkResult copyChunkViaPgCopy(Connection source, Connection target,
                                            String selectSQL, TableMapping mapping,
                                            ChunkDefinition chunk) throws Exception {
        PGConnection pgConn = target.unwrap(PGConnection.class);
        CopyManager copyManager = pgConn.getCopyAPI();
        
        String copySQL = String.format("COPY %s (%s) FROM STDIN WITH (FORMAT csv, NULL '\\N')",
            mapping.getTargetFQN(), mapping.getTargetColumnList());
        
        long rowsCopied = 0;
        try (ResultSet rs = source.createStatement().executeQuery(selectSQL);
             PipedOutputStream pipeOut = new PipedOutputStream();
             PipedInputStream pipeIn = new PipedInputStream(pipeOut)) {
            
            // Producer thread: convert ResultSet rows to CSV
            Thread producer = new Thread(() -> {
                try (OutputStreamWriter writer = new OutputStreamWriter(pipeOut)) {
                    while (rs.next()) {
                        writer.write(resultSetRowToCSV(rs, mapping));
                        writer.write('\n');
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            producer.start();
            
            // Consumer: COPY FROM STDIN
            rowsCopied = copyManager.copyIn(copySQL, pipeIn);
            producer.join();
        }
        
        // Checkpoint this chunk as completed
        String checksum = computeChunkChecksum(source, selectSQL);
        checkpointStore.markChunkCompleted(chunk, rowsCopied, checksum);
        
        return new ChunkResult(chunk.getChunkId(), ChunkStatus.COMPLETED, rowsCopied, null);
    }
}
```

**Chunk Strategy Selection**:
```
Table has auto-increment PK (BIGINT)?
  → Range chunking: id BETWEEN 0-1M, 1M-2M, 2M-3M ...
  → Most efficient, perfectly parallelizable

Table has UUID PK?
  → Hash chunking: MOD(HASHBYTES(pk), N) = chunk_number
  → Requires full table scan per chunk (less efficient)

Table has composite PK (user_id, order_id)?
  → Range on first column: user_id BETWEEN 1-10K, 10K-20K ...
  → Each range may have different sizes (skew)

Table has no PK?
  → Oracle: ROWID ranges (DBMS_PARALLEL_EXECUTE)
  → PostgreSQL: ctid ranges
  → MySQL: internal row_number via LIMIT/OFFSET (worst case)
  
Performance comparison:
  Range chunking on BIGINT PK:     ~1 TB/hour (500 parallel streams)
  Hash chunking on UUID PK:         ~300 GB/hour (full scan overhead)
  ROWID/ctid chunking:              ~500 GB/hour
  LIMIT/OFFSET (no PK):             ~50 GB/hour (sequential scan hell)
```

---

### Deep Dive 3: CDC Conflict Resolution — Bulk Load vs CDC Race Conditions

**The Problem**: During bulk load, CDC events are accumulating in Kafka. When we start applying CDC events, some events may refer to rows that were already loaded (or not yet loaded) by the bulk load workers. We need to handle the overlap correctly.

```java
public class CDCApplier {

    private final SnapshotResult snapshot;
    private final Set<String> bulkLoadCompletedTables;
    
    /**
     * CDC event application rules:
     * 
     * 1. Events with log_position ≤ snapshot_position → SKIP
     *    (these are already captured in the bulk load snapshot)
     * 
     * 2. Events with log_position > snapshot_position → APPLY
     *    (these happened after the snapshot and must be replayed)
     * 
     * 3. For tables still being bulk-loaded:
     *    → Buffer CDC events in Kafka (don't apply yet)
     *    → Once bulk load for that table completes, start applying
     *    → Use idempotent writes to handle any overlap at chunk boundaries
     * 
     * 4. Idempotent write patterns:
     *    INSERT → INSERT ON CONFLICT (pk) DO UPDATE SET ...  (upsert)
     *    UPDATE → UPDATE ... WHERE pk = ? (no-op if row doesn't exist yet)
     *    DELETE → DELETE WHERE pk = ? (no-op if row already deleted)
     */
    
    public void applyCDCEvent(CDCEvent event) {
        // Rule 1: Skip events before snapshot
        if (event.getSourceLogPosition() <= snapshot.getLogPosition()) {
            metrics.counter("cdc.events.skipped.before_snapshot").increment();
            return;
        }
        
        // Rule 2: Check if table's bulk load is complete
        String tableKey = event.getSourceSchema() + "." + event.getSourceTable();
        if (!bulkLoadCompletedTables.contains(tableKey)) {
            // Table still loading — don't apply yet, Kafka will retain the event
            // Consumer will re-process when table is marked complete
            throw new TableNotReadyException(tableKey);
        }
        
        // Rule 3: Transform and apply idempotently
        TableMapping mapping = schemaMapping.getTableMapping(tableKey);
        Map<String, Object> transformedRow = transformRow(event.getAfterImage(), mapping);
        Map<String, Object> transformedPK = transformPK(event.getPrimaryKey(), mapping);
        
        switch (event.getOperation()) {
            case INSERT:
                applyUpsert(mapping.getTargetFQN(), transformedPK, transformedRow);
                break;
            case UPDATE:
                applyUpsert(mapping.getTargetFQN(), transformedPK, transformedRow);
                break;
            case DELETE:
                applyDelete(mapping.getTargetFQN(), transformedPK);
                break;
            case SCHEMA_CHANGE:
                handleSchemaChange(event);
                break;
        }
        
        metrics.counter("cdc.events.applied").increment();
    }
    
    /**
     * Upsert: INSERT ON CONFLICT DO UPDATE
     * This is the key idempotency mechanism.
     * If the row already exists (from bulk load or previous CDC), it gets updated.
     * If the row doesn't exist yet, it gets inserted.
     * Either way, the final state is correct.
     */
    private void applyUpsert(String targetTable, Map<String, Object> pk, Map<String, Object> row) {
        String pkColumns = String.join(", ", pk.keySet());
        String allColumns = String.join(", ", row.keySet());
        String placeholders = row.keySet().stream().map(c -> "?").collect(Collectors.joining(", "));
        String updateSet = row.keySet().stream()
            .filter(c -> !pk.containsKey(c))
            .map(c -> c + " = EXCLUDED." + c)
            .collect(Collectors.joining(", "));
        
        String sql = String.format(
            "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
            targetTable, allColumns, placeholders, pkColumns, updateSet);
        
        targetDb.execute(sql, row.values().toArray());
    }
    
    private void applyDelete(String targetTable, Map<String, Object> pk) {
        String whereClause = pk.keySet().stream()
            .map(c -> c + " = ?")
            .collect(Collectors.joining(" AND "));
        
        String sql = String.format("DELETE FROM %s WHERE %s", targetTable, whereClause);
        int affected = targetDb.execute(sql, pk.values().toArray());
        
        if (affected == 0) {
            // Row was already deleted or never existed — that's fine (idempotent)
            metrics.counter("cdc.delete.no_op").increment();
        }
    }
}
```

**CDC + Bulk Load Coordination Per Table**:
```
Table: order_items (10 chunks)

Time    Bulk Load                  CDC Events in Kafka
─────   ──────────────────         ──────────────────────────
T=0     Start chunk 1-3            CDC events accumulating...
T=1h    Chunk 1 ✓, chunk 4 start  INSERT order_item id=5001 (after snapshot)
T=2h    Chunk 2 ✓, chunk 5 start  UPDATE order_item id=3002 (in chunk 3)
T=3h    Chunk 3 ✓, chunk 6 start  DELETE order_item id=1005 (in chunk 1)
...
T=10h   All 10 chunks ✓           

T=10h   Mark table "bulk_load_complete"
T=10h   Start applying buffered CDC events:
        - INSERT id=5001 → UPSERT (new row, inserted)
        - UPDATE id=3002 → UPSERT (overwrites bulk-loaded value with newer) ✓
        - DELETE id=1005 → DELETE (removes bulk-loaded row) ✓
        
T=10.5h CDC caught up — table fully synchronized ✓
```

---

### Deep Dive 4: Schema Translation Engine — Oracle to PostgreSQL

**The Problem**: Oracle and PostgreSQL have different data types, SQL dialects, stored procedure languages, index types, and constraint behaviors. Automatic translation must handle hundreds of type mappings and flag incompatibilities.

```java
public class SchemaTranslationEngine {

    /**
     * Type mapping rules for Oracle → PostgreSQL.
     * Each rule handles one Oracle type → PostgreSQL equivalent.
     * Rules are ordered by specificity (most specific first).
     */
    
    private static final List<TypeMappingRule> ORACLE_TO_PG_RULES = List.of(
        // Numeric types
        new TypeMappingRule("NUMBER(1)",           "BOOLEAN",    "Oracle uses NUMBER(1) for booleans"),
        new TypeMappingRule("NUMBER(\\d+,0)",      "BIGINT",     "Exact integer mapping", r -> {
            int precision = r.getPrecision();
            if (precision <= 4) return "SMALLINT";
            if (precision <= 9) return "INTEGER";
            if (precision <= 18) return "BIGINT";
            return "NUMERIC(" + precision + ")"; // Very large integers
        }),
        new TypeMappingRule("NUMBER(\\d+,\\d+)",   "NUMERIC",    "Decimal with scale"),
        new TypeMappingRule("NUMBER",              "NUMERIC",    "Unparameterized NUMBER → NUMERIC"),
        new TypeMappingRule("FLOAT",               "DOUBLE PRECISION", "IEEE 754"),
        new TypeMappingRule("BINARY_FLOAT",        "REAL",       "32-bit float"),
        new TypeMappingRule("BINARY_DOUBLE",       "DOUBLE PRECISION", "64-bit float"),
        
        // String types
        new TypeMappingRule("VARCHAR2(\\d+)",       "VARCHAR",    "Variable-length string"),
        new TypeMappingRule("NVARCHAR2(\\d+)",      "VARCHAR",    "Unicode string (PG is always Unicode)"),
        new TypeMappingRule("CHAR(\\d+)",           "CHAR",       "Fixed-length string"),
        new TypeMappingRule("CLOB",                "TEXT",        "Large text"),
        new TypeMappingRule("NCLOB",               "TEXT",        "Unicode large text"),
        new TypeMappingRule("LONG",                "TEXT",        "Deprecated Oracle LONG type"),
        
        // Date/Time types — CRITICAL: Oracle DATE includes time!
        new TypeMappingRule("DATE",                "TIMESTAMP",  
            "WARNING: Oracle DATE includes time component. Mapped to TIMESTAMP, not DATE."),
        new TypeMappingRule("TIMESTAMP(\\d+)",      "TIMESTAMP",  "Timestamp with fractional seconds"),
        new TypeMappingRule("TIMESTAMP.*WITH TIME ZONE", "TIMESTAMPTZ", "Timezone-aware timestamp"),
        new TypeMappingRule("INTERVAL YEAR.*",      "INTERVAL",   "Year-month interval"),
        new TypeMappingRule("INTERVAL DAY.*",       "INTERVAL",   "Day-time interval"),
        
        // Binary types
        new TypeMappingRule("BLOB",                "BYTEA",      "Binary large object"),
        new TypeMappingRule("RAW(\\d+)",           "BYTEA",      "Raw binary data"),
        new TypeMappingRule("LONG RAW",            "BYTEA",      "Deprecated binary type"),
        
        // Special types
        new TypeMappingRule("ROWID",               "TEXT",        "Oracle ROWID has no PG equivalent"),
        new TypeMappingRule("XMLTYPE",             "XML",         "XML data type"),
        new TypeMappingRule("SDO_GEOMETRY",        "GEOMETRY",    "Spatial type (requires PostGIS)")
    );
    
    /**
     * Translate a complete schema from Oracle to PostgreSQL.
     */
    public SchemaMapping translateSchema(DatabaseConnection source, DatabaseConnection target) {
        // Step 1: Introspect source schema
        OracleSchemaIntrospector introspector = new OracleSchemaIntrospector(source);
        SourceSchema sourceSchema = introspector.introspect(); // tables, columns, indexes, etc.
        
        List<TableMapping> tableMappings = new ArrayList<>();
        List<IncompatibleConstruct> incompatibles = new ArrayList<>();
        int autoTranslated = 0, manualNeeded = 0;
        
        for (SourceTable table : sourceSchema.getTables()) {
            TableMapping.Builder mapping = TableMapping.builder()
                .sourceSchema(table.getSchema())
                .sourceTable(table.getName())
                .targetSchema("public")
                .targetTable(toSnakeCase(table.getName())); // ORACLE_TABLE → oracle_table
            
            // Step 2: Translate columns
            for (SourceColumn col : table.getColumns()) {
                TypeMappingRule rule = findMatchingRule(col.getDataType());
                
                if (rule != null) {
                    mapping.addColumn(ColumnMapping.builder()
                        .sourceColumn(col.getName())
                        .sourceType(col.getDataType())
                        .targetColumn(toSnakeCase(col.getName()))
                        .targetType(rule.apply(col))
                        .warning(rule.getWarning())
                        .requiresManualReview(false)
                        .build());
                    autoTranslated++;
                } else {
                    mapping.addColumn(ColumnMapping.builder()
                        .sourceColumn(col.getName())
                        .sourceType(col.getDataType())
                        .targetColumn(toSnakeCase(col.getName()))
                        .targetType("TEXT") // Fallback
                        .requiresManualReview(true)
                        .build());
                    incompatibles.add(new IncompatibleConstruct("COLUMN",
                        table.getName() + "." + col.getName(),
                        "No mapping for type: " + col.getDataType()));
                    manualNeeded++;
                }
            }
            
            // Step 3: Translate indexes
            for (SourceIndex idx : table.getIndexes()) {
                if (idx.getType().equals("BITMAP")) {
                    // BITMAP → B-tree (PostgreSQL doesn't support bitmap indexes)
                    mapping.addIndex(IndexMapping.btree(idx.getName(), idx.getColumns()));
                    mapping.addWarning("BITMAP index " + idx.getName() + 
                        " converted to B-tree. Consider partial index for low-cardinality columns.");
                } else if (idx.getType().equals("FUNCTION-BASED")) {
                    // Attempt to translate function expression
                    String pgExpression = translateExpression(idx.getExpression());
                    mapping.addIndex(IndexMapping.expression(idx.getName(), pgExpression));
                } else {
                    mapping.addIndex(IndexMapping.btree(idx.getName(), idx.getColumns()));
                }
            }
            
            // Step 4: Translate sequences
            for (SourceSequence seq : table.getSequences()) {
                // Oracle SEQUENCE → PostgreSQL SEQUENCE (mostly compatible)
                // Or use GENERATED ALWAYS AS IDENTITY for simple auto-increment
                mapping.addSequence(seq.getName(), "GENERATED ALWAYS AS IDENTITY");
            }
            
            tableMappings.add(mapping.build());
        }
        
        // Step 5: Translate stored procedures (hardest part)
        for (SourceProcedure proc : sourceSchema.getProcedures()) {
            TranslationResult result = translateProcedure(proc);
            if (!result.isAutoTranslated()) {
                incompatibles.add(new IncompatibleConstruct("STORED_PROCEDURE",
                    proc.getName(), result.getReason(), result.getSuggestion()));
            }
        }
        
        double autoRate = (double) autoTranslated / (autoTranslated + manualNeeded);
        log.info("Schema translation: {}/{} auto ({}%), {} need manual review",
            autoTranslated, autoTranslated + manualNeeded, 
            String.format("%.1f", autoRate * 100), manualNeeded);
        
        return new SchemaMapping(tableMappings, incompatibles, autoRate);
    }
}
```

**Common Oracle → PostgreSQL Incompatibilities**:
```
┌──────────────────────────┬────────────────────────────┬──────────────────────┐
│ Oracle Feature           │ PostgreSQL Equivalent      │ Auto-translatable?   │
├──────────────────────────┼────────────────────────────┼──────────────────────┤
│ DATE (includes time)     │ TIMESTAMP                  │ ✅ Yes (with warning)│
│ NUMBER(1) for boolean    │ BOOLEAN                    │ ✅ Yes               │
│ VARCHAR2(N BYTE)         │ VARCHAR(N)                 │ ✅ Yes               │
│ CLOB                     │ TEXT                       │ ✅ Yes               │
│ SEQUENCE.NEXTVAL         │ nextval('seq_name')        │ ✅ Yes               │
│ SYSDATE                  │ CURRENT_TIMESTAMP          │ ✅ Yes               │
│ NVL(a, b)                │ COALESCE(a, b)             │ ✅ Yes               │
│ DECODE(a, b, c, d)       │ CASE WHEN a=b THEN c...   │ ✅ Yes               │
│ ROWNUM                   │ ROW_NUMBER() OVER()        │ ✅ Yes               │
│ BITMAP index             │ B-tree index               │ ⚠️ Partial          │
│ Global temporary table   │ UNLOGGED TABLE / temp      │ ⚠️ Partial          │
│ Materialized view (fast) │ Materialized view (manual) │ ⚠️ Partial          │
│ DBMS_LOB package         │ lo_* functions             │ ❌ Manual            │
│ SYS_CONTEXT              │ current_setting()          │ ❌ Manual            │
│ PL/SQL packages          │ PL/pgSQL (no packages)     │ ❌ Manual            │
│ Oracle Forms/Reports     │ N/A                        │ ❌ Out of scope      │
│ Database Links (DBLink)  │ postgres_fdw / dblink ext  │ ❌ Manual            │
│ Flashback queries        │ N/A                        │ ❌ Not available     │
└──────────────────────────┴────────────────────────────┴──────────────────────┘
```

---

### Deep Dive 5: Zero-Downtime Cutover — The Critical 30-Second Window

**The Problem**: The cutover is the most critical moment. We must switch all application traffic from source to target database with zero data loss and near-zero downtime. Any mistake here can cause data corruption or extended outage.

```java
public class ZeroDowntimeCutoverManager {

    /**
     * Cutover strategies:
     * 
     * Strategy 1: DNS/Proxy Switch (preferred for zero downtime)
     *   - Application connects via a proxy (e.g., ProxySQL, PgBouncer, AWS Route53)
     *   - Cutover = change proxy routing from source → target
     *   - Application sees brief connection reset (~1-5 seconds)
     * 
     * Strategy 2: Application Config Switch
     *   - Update connection string in config service (e.g., Consul, AWS Parameter Store)
     *   - Applications re-read config and reconnect
     *   - Staggered rollout (~30 seconds for all instances)
     * 
     * Strategy 3: Double-Write Period (safest but most complex)
     *   - Application writes to BOTH source and target for N minutes
     *   - Read traffic gradually shifted to target (canary → 10% → 50% → 100%)
     *   - Once 100% reads on target, stop writing to source
     */
    
    public CutoverResult executeCutover(MigrationJob job, CutoverConfig config) {
        CutoverResult result = new CutoverResult(job.getJobId());
        
        try {
            // ── STEP 1: Pre-cutover validation ──────────────────────────
            result.startStep("PRE_CUTOVER_VALIDATION");
            
            // Verify CDC lag is minimal
            double cdcLag = cdcMonitor.getCurrentLagSeconds(job.getJobId());
            if (cdcLag > 5.0) {
                throw new CutoverAbortException("CDC lag too high: " + cdcLag + "s. Must be < 5s.");
            }
            
            // Run quick row count validation
            ValidationResult validation = validator.quickValidate(job);
            if (!validation.isPassed()) {
                throw new CutoverAbortException("Validation failed: " + validation.getSummary());
            }
            
            result.completeStep("PRE_CUTOVER_VALIDATION");
            
            // ── STEP 2: Drain CDC lag to zero ───────────────────────────
            result.startStep("DRAIN_CDC_LAG");
            
            // Wait for CDC to fully catch up (lag → 0)
            boolean drained = cdcMonitor.waitForZeroLag(job.getJobId(), Duration.ofMinutes(5));
            if (!drained) {
                throw new CutoverAbortException("Could not drain CDC lag to zero within 5 minutes");
            }
            
            result.completeStep("DRAIN_CDC_LAG");
            
            // ── STEP 3: Pause source writes (THE CRITICAL MOMENT) ──────
            result.startStep("PAUSE_SOURCE_WRITES");
            Instant pauseStart = Instant.now();
            
            // Option A: Set source DB to read-only
            sourceDb.execute("ALTER DATABASE " + job.getSource().getDatabaseName() + " SET READ_ONLY");
            
            // Option B: Revoke write permissions from app user
            // sourceDb.execute("REVOKE INSERT, UPDATE, DELETE ON SCHEMA orders FROM app_user");
            
            // Option C: Enable kill switch in application (feature flag)
            // featureFlags.set("db.writes.enabled", false);
            
            result.completeStep("PAUSE_SOURCE_WRITES");
            
            // ── STEP 4: Apply final CDC events ─────────────────────────
            result.startStep("APPLY_FINAL_CDC");
            
            // Process any remaining events that arrived between drain and pause
            int finalEvents = cdcApplier.drainRemaining(job.getJobId(), Duration.ofSeconds(10));
            log.info("Applied {} final CDC events after source pause", finalEvents);
            
            result.completeStep("APPLY_FINAL_CDC");
            
            // ── STEP 5: Final consistency verification ──────────────────
            result.startStep("VERIFY_CONSISTENCY");
            
            // Quick hash-based check on 10 critical tables
            List<String> criticalTables = job.getConfig().getCriticalTables();
            for (String table : criticalTables) {
                long sourceCount = sourceDb.queryCount(table);
                long targetCount = targetDb.queryCount(table);
                if (sourceCount != targetCount) {
                    throw new CutoverAbortException(
                        "Row count mismatch on " + table + ": source=" + sourceCount + 
                        ", target=" + targetCount);
                }
            }
            
            result.completeStep("VERIFY_CONSISTENCY");
            
            // ── STEP 6: Switch application traffic ──────────────────────
            result.startStep("SWITCH_TRAFFIC");
            
            // Update DNS/proxy/config to point to target
            switch (config.getSwitchMethod()) {
                case DNS:
                    dnsService.updateCNAME("orders-db.internal", job.getTarget().getHost());
                    break;
                case PROXY:
                    proxyManager.switchBackend("orders-db", job.getTarget().getConnectionString());
                    break;
                case CONFIG:
                    configStore.update("db.orders.connection_string", 
                        job.getTarget().getConnectionString());
                    break;
            }
            
            result.completeStep("SWITCH_TRAFFIC");
            
            // ── STEP 7: Enable target writes ────────────────────────────
            result.startStep("ENABLE_TARGET_WRITES");
            
            // Ensure target DB accepts writes (it was already writable for CDC)
            // Re-enable application writes via feature flag if used
            // featureFlags.set("db.writes.enabled", true);
            
            Instant cutoverEnd = Instant.now();
            Duration downtime = Duration.between(pauseStart, cutoverEnd);
            
            result.completeStep("ENABLE_TARGET_WRITES");
            result.setTotalDowntime(downtime);
            
            log.info("CUTOVER COMPLETE! Total downtime: {} seconds", downtime.getSeconds());
            
            // ── STEP 8: (Optional) Enable reverse CDC for rollback ──────
            if (config.isRollbackEnabled()) {
                reverseCDC.start(job.getTarget(), job.getSource());
                log.info("Reverse CDC enabled. Rollback available for 24 hours.");
            }
            
            return result;
            
        } catch (CutoverAbortException e) {
            // Rollback: re-enable source writes, abort cutover
            log.error("CUTOVER ABORTED: {}", e.getMessage());
            sourceDb.execute("ALTER DATABASE " + job.getSource().getDatabaseName() + " SET READ_WRITE");
            result.setStatus(CutoverStatus.ABORTED);
            result.setError(e.getMessage());
            return result;
        }
    }
}
```

**Cutover Timeline (Happy Path)**:
```
T=0.0s   Pre-cutover validation starts
T=15.0s  Validation passed ✓
T=15.0s  Draining CDC lag...
T=18.2s  CDC lag = 0 ✓
T=18.2s  ★ SOURCE SET TO READ-ONLY ★  ← Downtime begins
T=18.5s  Final 47 CDC events applied
T=19.0s  Row count verification passed ✓
T=19.5s  DNS updated to point to target
T=20.0s  ★ TARGET ACCEPTING WRITES ★  ← Downtime ends
─────────────────────────────────────────
Total downtime: 1.8 seconds

T=20.0s  Applications reconnect (connection pool refresh)
T=25.0s  All application instances connected to target ✓
T=25.0s  Reverse CDC enabled for rollback safety
```

---

### Deep Dive 6: Data Validation Engine — Proving Correctness at Scale

**The Problem**: With 200 billion rows, how do we verify that every row migrated correctly without spending weeks comparing row-by-row?

```java
public class DataValidationEngine {

    /**
     * Multi-level validation strategy:
     * 
     * Level 1: Row counts (fast, catches gross errors)
     * Level 2: Table-level checksums (medium, catches data corruption)
     * Level 3: Sampled row comparison (slow, catches subtle type conversion issues)
     * Level 4: Constraint validation (verify FK/unique/check constraints on target)
     */
    
    public ValidationReport validate(MigrationJob job, ValidationConfig config) {
        ValidationReport report = new ValidationReport(job.getJobId());
        
        // Level 1: Row Count Comparison (~5 minutes for 500 tables)
        report.startCheck("ROW_COUNT");
        for (TableMapping table : job.getSchemaMapping().getTableMappings()) {
            long sourceCount = sourceDb.queryCount(table.getSourceFQN());
            long targetCount = targetDb.queryCount(table.getTargetFQN());
            
            if (sourceCount == targetCount) {
                report.addResult(table.getSourceFQN(), "ROW_COUNT", "PASS", 
                    sourceCount + " rows match");
            } else {
                long diff = Math.abs(sourceCount - targetCount);
                double pctDiff = (double) diff / Math.max(sourceCount, 1) * 100;
                
                // Allow tiny discrepancy during active CDC (rows in-flight)
                if (pctDiff < 0.001 && job.getCurrentPhase() == MigrationPhase.CDC_SYNC) {
                    report.addResult(table.getSourceFQN(), "ROW_COUNT", "WARN",
                        String.format("source=%d, target=%d (diff=%d, %.4f%% — likely in-flight CDC)",
                            sourceCount, targetCount, diff, pctDiff));
                } else {
                    report.addResult(table.getSourceFQN(), "ROW_COUNT", "FAIL",
                        String.format("source=%d, target=%d (diff=%d)", 
                            sourceCount, targetCount, diff));
                }
            }
        }
        
        // Level 2: Chunk-Level Checksums (~2 hours for 50 TB)
        report.startCheck("CHECKSUM");
        for (TableMapping table : job.getSchemaMapping().getTableMappings()) {
            List<ChunkCheckpoint> chunks = job.getTableState(table.getSourceFQN()).getChunks();
            
            for (ChunkCheckpoint chunk : chunks) {
                // Compute checksum on source chunk
                String sourceChecksum = computeChunkChecksum(
                    job.getSource(), table.getSourceFQN(), chunk.getStartKey(), chunk.getEndKey());
                
                // Compute checksum on target chunk (with column mapping applied)
                String targetChecksum = computeChunkChecksum(
                    job.getTarget(), table.getTargetFQN(), chunk.getStartKey(), chunk.getEndKey());
                
                if (!sourceChecksum.equals(targetChecksum)) {
                    report.addResult(table.getSourceFQN(), "CHECKSUM", "FAIL",
                        String.format("Chunk %s checksum mismatch: source=%s, target=%s",
                            chunk.getChunkId(), sourceChecksum, targetChecksum));
                    
                    // Drill down: which rows in this chunk are different?
                    findMismatchedRows(job, table, chunk, report);
                }
            }
        }
        
        // Level 3: Sampled Row Comparison (~20 hours for 1% sample of 200B rows)
        report.startCheck("SAMPLE_COMPARE");
        double sampleRate = config.getSampleRate(); // e.g., 0.01 = 1%
        
        for (TableMapping table : job.getSchemaMapping().getTableMappings()) {
            // Use TABLESAMPLE or random PK selection
            String sampleSQL = String.format(
                "SELECT * FROM %s TABLESAMPLE BERNOULLI(%f)",
                table.getSourceFQN(), sampleRate * 100);
            
            try (ResultSet sourceRows = sourceDb.query(sampleSQL)) {
                while (sourceRows.next()) {
                    Map<String, Object> sourceRow = resultSetToMap(sourceRows);
                    Map<String, Object> pk = extractPK(sourceRow, table);
                    
                    // Look up corresponding row in target
                    Map<String, Object> targetRow = targetDb.findByPK(table.getTargetFQN(), pk);
                    
                    if (targetRow == null) {
                        report.addMismatch(table.getSourceFQN(), pk, "MISSING_IN_TARGET", 
                            sourceRow, null);
                    } else {
                        // Compare column by column (with type conversion awareness)
                        List<String> differences = compareRows(sourceRow, targetRow, table);
                        if (!differences.isEmpty()) {
                            report.addMismatch(table.getSourceFQN(), pk, "VALUE_MISMATCH",
                                sourceRow, targetRow);
                        }
                    }
                }
            }
        }
        
        return report;
    }
    
    /**
     * Compute a deterministic checksum for a chunk of rows.
     * Use ORDER BY pk to ensure consistent ordering between source and target.
     * 
     * The checksum is computed as: MD5(CONCAT(MD5(row1), MD5(row2), ...))
     * This catches any difference in any column of any row.
     */
    private String computeChunkChecksum(DatabaseConnection db, String table,
                                         long startKey, long endKey) {
        // For Oracle:
        // SELECT ORA_HASH(DBMS_UTILITY.GET_HASH_VALUE(
        //   col1||col2||col3, 0, POWER(2,30))) FROM table WHERE id BETWEEN ? AND ?
        
        // For PostgreSQL:
        String sql = String.format("""
            SELECT md5(string_agg(row_hash, '' ORDER BY pk_col)) AS chunk_checksum
            FROM (
                SELECT pk_col, md5(ROW(%s)::text) AS row_hash
                FROM %s
                WHERE pk_col BETWEEN %d AND %d
                ORDER BY pk_col
            ) sub
            """, getAllColumns(table), table, startKey, endKey);
        
        return db.queryScalar(sql);
    }
}
```

**Validation Performance**:
```
Level 1 (Row Counts):
  500 tables × 2 COUNT(*) queries = 1000 queries
  ~6 seconds each (index-only scan) = ~100 minutes
  Can be parallelized: 50 parallel → ~2 minutes

Level 2 (Checksums):
  5000 chunks × 2 checksum queries = 10,000 queries
  ~10 seconds each = ~28 hours sequential
  Parallelized across 20 workers: ~1.4 hours

Level 3 (Sample Compare):
  1% of 200B = 2B rows to compare
  100,000 rows/sec comparison rate
  ~5.5 hours sequential, ~20 minutes parallelized

Total validation time (parallelized): ~2 hours
```

---

### Deep Dive 7: Checkpointing & Resumability — Surviving Failures

**The Problem**: A 50-hour migration will inevitably encounter failures — worker crashes, network timeouts, source DB maintenance windows. The migration must resume from where it left off, not restart from scratch.

```java
public class CheckpointStore {

    /**
     * Checkpoint hierarchy:
     *   Job → Table → Chunk
     * 
     * Each level stores enough state to resume from that point.
     * Checkpoints are written atomically to PostgreSQL (migration metadata DB).
     * 
     * Checkpoint frequency:
     *   - Bulk load: after each chunk completes (~1M rows, ~30 seconds)
     *   - CDC: after each batch of events applied (~1000 events, ~1 second)
     *   - CDC connector: after each Kafka offset commit
     */
    
    // Persist chunk completion atomically
    @Transactional
    public void markChunkCompleted(String jobId, String tableName, 
                                    ChunkDefinition chunk, long rowsCopied, String checksum) {
        jdbc.update("""
            INSERT INTO migration_checkpoints 
                (job_id, table_name, chunk_id, start_key, end_key, 
                 rows_copied, checksum, status, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETED', NOW())
            ON CONFLICT (job_id, table_name, chunk_id) 
            DO UPDATE SET rows_copied = ?, checksum = ?, status = 'COMPLETED', completed_at = NOW()
            """, jobId, tableName, chunk.getChunkId(), chunk.getStartKey(), chunk.getEndKey(),
                rowsCopied, checksum, rowsCopied, checksum);
        
        // Update table-level progress
        jdbc.update("""
            UPDATE migration_table_state 
            SET migrated_rows = migrated_rows + ?, 
                last_checkpoint_at = NOW(),
                chunks_completed = chunks_completed + 1
            WHERE job_id = ? AND table_name = ?
            """, rowsCopied, jobId, tableName);
    }
    
    // CDC checkpoint: store Kafka offset + source log position
    @Transactional
    public void checkpointCDC(String jobId, String tableName,
                               long kafkaOffset, long sourceLogPosition, long eventsApplied) {
        jdbc.update("""
            INSERT INTO cdc_checkpoints 
                (job_id, table_name, kafka_offset, source_log_position, events_applied, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT (job_id, table_name)
            DO UPDATE SET kafka_offset = ?, source_log_position = ?, 
                          events_applied = events_applied + ?, updated_at = NOW()
            """, jobId, tableName, kafkaOffset, sourceLogPosition, eventsApplied,
                kafkaOffset, sourceLogPosition, eventsApplied);
    }
    
    // Resume: find where we left off
    public ResumePoint getResumePoint(String jobId) {
        // 1. Find last completed phase
        MigrationJob job = getJob(jobId);
        
        // 2. For bulk load: find incomplete chunks
        List<ChunkCheckpoint> incompleteChunks = jdbc.query("""
            SELECT * FROM migration_checkpoints 
            WHERE job_id = ? AND status != 'COMPLETED'
            ORDER BY table_name, chunk_id
            """, jobId);
        
        // 3. For CDC: find last committed offset
        Map<String, Long> cdcOffsets = jdbc.query("""
            SELECT table_name, kafka_offset FROM cdc_checkpoints WHERE job_id = ?
            """, jobId).stream()
            .collect(Collectors.toMap(r -> r.getString("table_name"), r -> r.getLong("kafka_offset")));
        
        return new ResumePoint(job.getCurrentPhase(), incompleteChunks, cdcOffsets);
    }
}
```

**Failure Recovery Scenarios**:
```
Scenario 1: Worker node crash during bulk load
  State: 320/500 tables complete, table "orders" has 7/10 chunks done
  Recovery:
    1. Coordinator detects worker heartbeat timeout
    2. Load checkpoint: 320 tables done, "orders" chunks 1-7 done
    3. Reschedule chunks 8-10 to healthy workers
    4. Resume from chunk 8 — no work repeated for chunks 1-7 ✓

Scenario 2: Kafka broker outage during CDC
  State: CDC connector loses Kafka connection
  Recovery:
    1. CDC connector retries Kafka connection (exponential backoff)
    2. Source DB transaction log retains events (Oracle: redo logs, PG: WAL)
    3. When Kafka recovers: CDC resumes from last source log position
    4. No events lost (source DB is the source of truth) ✓

Scenario 3: Target DB runs out of disk during bulk load
  State: Target PostgreSQL disk full, COPY commands fail
  Recovery:
    1. Bulk load workers detect write failures
    2. Failed chunks marked as FAILED in checkpoint store
    3. Operator adds disk to target DB
    4. Resume migration → only failed chunks are re-executed ✓

Scenario 4: Network partition between source and target (on-prem ↔ cloud)
  State: VPN tunnel goes down for 2 hours
  Recovery:
    1. Bulk load workers: connection timeouts, chunks fail
    2. CDC connector: buffers events in Kafka (7-day retention)
    3. When network restores: bulk load resumes from checkpoints
    4. CDC replays 2 hours of buffered events (catch-up) ✓
```

---

### Deep Dive 8: Handling Tables Without Primary Keys

**The Problem**: ~5-10% of tables in legacy databases lack primary keys. Without a unique identifier, we can't chunk tables for parallel loading, perform idempotent CDC operations, or validate row-level correctness.

```java
public class NoPrimaryKeyHandler {

    /**
     * Strategies for tables without primary keys:
     * 
     * 1. Use unique index as surrogate PK (if one exists)
     * 2. Use database-internal row identifiers (ROWID/ctid) for bulk load
     * 3. Add a synthetic PK column to target table
     * 4. For CDC: use full row image as the "key" (all columns)
     */
    
    public PKStrategy selectStrategy(TableAnalysis analysis) {
        // Strategy 1: Check for unique indexes
        if (analysis.hasUniqueIndex()) {
            UniqueIndex idx = analysis.getBestUniqueIndex(); // Prefer smallest, non-nullable
            log.info("Using unique index {} as surrogate PK for {}", idx.getName(), analysis.getTable());
            return new UniqueIndexStrategy(idx);
        }
        
        // Strategy 2: Use internal row ID for bulk load
        switch (analysis.getSourceType()) {
            case ORACLE:
                // Oracle ROWID: physical address of the row
                // Can be used for chunking via DBMS_PARALLEL_EXECUTE
                return new OracleRowIdStrategy();
            case POSTGRESQL:
                // PostgreSQL ctid: (page, offset) tuple
                // Can be used for range scans but changes on VACUUM
                return new PostgresCtidStrategy();
            case MYSQL:
                // MySQL InnoDB: hidden _rowid if single-column PK or no PK
                // Fallback to LIMIT/OFFSET (slow)
                return new MySQLRowIdStrategy();
        }
        
        // Strategy 3: Full-row identity (last resort)
        return new FullRowIdentityStrategy(analysis.getAllColumns());
    }
    
    /**
     * CDC without PK: use full before-image as the "key" for UPDATE/DELETE.
     * 
     * This is less efficient but correct:
     *   UPDATE target SET col1=new1, col2=new2 
     *   WHERE col1=old1 AND col2=old2 AND col3=old3 ...
     * 
     * Risk: if two rows are completely identical, we can't distinguish them.
     * Mitigation: add a synthetic PK to the target table during migration.
     */
    public void applyCDCWithoutPK(CDCEvent event, TableMapping mapping) {
        if (event.getOperation() == CDCOperationType.INSERT) {
            // INSERT is fine without PK — just insert the row
            targetDb.insert(mapping.getTargetFQN(), transformRow(event.getAfterImage(), mapping));
            return;
        }
        
        // UPDATE or DELETE: use all before-image columns as the WHERE clause
        Map<String, Object> beforeImage = transformRow(event.getBeforeImage(), mapping);
        String whereClause = beforeImage.entrySet().stream()
            .map(e -> e.getValue() == null 
                ? e.getKey() + " IS NULL" 
                : e.getKey() + " = ?")
            .collect(Collectors.joining(" AND "));
        
        List<Object> whereParams = beforeImage.values().stream()
            .filter(Objects::nonNull)
            .toList();
        
        if (event.getOperation() == CDCOperationType.UPDATE) {
            Map<String, Object> afterImage = transformRow(event.getAfterImage(), mapping);
            String setClause = afterImage.keySet().stream()
                .map(c -> c + " = ?")
                .collect(Collectors.joining(", "));
            
            String sql = String.format("UPDATE %s SET %s WHERE %s LIMIT 1",
                mapping.getTargetFQN(), setClause, whereClause);
            
            List<Object> allParams = new ArrayList<>(afterImage.values());
            allParams.addAll(whereParams);
            
            int affected = targetDb.execute(sql, allParams.toArray());
            if (affected != 1) {
                log.warn("CDC UPDATE affected {} rows (expected 1) for table {} — possible duplicate rows",
                    affected, mapping.getTargetFQN());
            }
        } else {
            // DELETE
            String sql = String.format("DELETE FROM %s WHERE %s LIMIT 1",
                mapping.getTargetFQN(), whereClause);
            targetDb.execute(sql, whereParams.toArray());
        }
    }
}
```

---

### Deep Dive 9: Throttling & Source DB Impact Control

**The Problem**: Bulk loading reads massive amounts of data from the production source database. If we read too aggressively, we degrade the source DB's performance for live application traffic.

```java
public class SourceThrottleManager {

    /**
     * Throttle migration reads based on source DB health.
     * 
     * Metrics monitored:
     *   - CPU utilization (keep below 70%)
     *   - Active sessions (keep below 80% of max)
     *   - I/O wait time (keep below 30%)
     *   - Replication lag (if source has replicas)
     * 
     * Throttle actions:
     *   - Reduce parallelism (fewer concurrent chunk readers)
     *   - Add sleep between batches
     *   - Pause migration entirely during peak hours
     */
    
    private volatile int currentParallelism;
    private volatile Duration batchDelay;
    
    @Scheduled(fixedRate = 10_000) // Check every 10 seconds
    public void adjustThrottle() {
        SourceDBHealth health = monitorSourceHealth();
        
        if (health.getCpuPercent() > 80 || health.getIoWaitPercent() > 40) {
            // CRITICAL: source DB is stressed — reduce aggressively
            currentParallelism = Math.max(1, currentParallelism / 2);
            batchDelay = Duration.ofMillis(500);
            log.warn("Source DB stressed (CPU={}%, IO_WAIT={}%) — throttling to {} parallel, {}ms delay",
                health.getCpuPercent(), health.getIoWaitPercent(), currentParallelism, batchDelay.toMillis());
            
        } else if (health.getCpuPercent() > 60 || health.getActiveSessionsPct() > 70) {
            // WARNING: approaching limits — moderate throttle
            currentParallelism = Math.max(5, currentParallelism - 5);
            batchDelay = Duration.ofMillis(100);
            
        } else if (health.getCpuPercent() < 40 && health.getIoWaitPercent() < 15) {
            // HEALTHY: can increase throughput
            currentParallelism = Math.min(maxParallelism, currentParallelism + 5);
            batchDelay = Duration.ZERO;
        }
        
        // Publish throttle settings for workers
        throttleConfig.update(currentParallelism, batchDelay);
        metrics.gauge("migration.throttle.parallelism", currentParallelism);
    }
    
    /**
     * Schedule-aware throttling: reduce migration throughput during
     * business hours when source DB has peak application traffic.
     */
    @Scheduled(cron = "0 0 * * * *") // Hourly
    public void applyScheduleThrottle() {
        LocalTime now = LocalTime.now(sourceTimezone);
        
        if (now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(20, 0))) {
            // Business hours: limit to 25% throughput
            maxParallelism = baseParallelism / 4;
            log.info("Business hours: limiting migration to {}% throughput", 25);
        } else {
            // Off-hours: full throughput
            maxParallelism = baseParallelism;
            log.info("Off-hours: migration at full throughput");
        }
    }
}
```

**Throttle Behavior Over 24 Hours**:
```
00:00 ─────────── Off-hours: 100% throughput (500 parallel streams, 1 TB/hr)
│
08:00 ─────────── Business hours start: throttle to 25% (125 streams, 250 GB/hr)
│                  Source DB CPU: ~35% (healthy)
│
12:00 ─────────── Peak traffic: further throttle to 10% if CPU > 60%
│
18:00 ─────────── Traffic declining: gradually restore to 50%
│
20:00 ─────────── Off-hours: restore to 100% throughput
│
Effective throughput: ~14 TB/day (not 24 TB/day) due to daytime throttling
50 TB migration takes ~3.5 days instead of ~2 days
```

---

### Deep Dive 10: Observability & Migration Metrics

```java
public class MigrationMetrics {
    // Progress metrics
    Gauge   tablesCompleted     = Gauge.builder("migration.tables.completed", ...).register();
    Gauge   rowsMigrated        = Gauge.builder("migration.rows.migrated", ...).register();
    Gauge   bytesTransferred    = Gauge.builder("migration.bytes.transferred", ...).register();
    Gauge   percentComplete     = Gauge.builder("migration.percent_complete", ...).register();
    Gauge   etaHours            = Gauge.builder("migration.eta_hours", ...).register();
    
    // Throughput metrics
    Counter rowsPerSecond       = Counter.builder("migration.throughput.rows_per_sec").register();
    Counter bytesPerSecond      = Counter.builder("migration.throughput.bytes_per_sec").register();
    
    // CDC metrics
    Gauge   cdcLagSeconds       = Gauge.builder("migration.cdc.lag_seconds", ...).register();
    Counter cdcEventsApplied    = Counter.builder("migration.cdc.events.applied").register();
    Counter cdcEventsSkipped    = Counter.builder("migration.cdc.events.skipped").register();
    Gauge   cdcEventsPending    = Gauge.builder("migration.cdc.events.pending", ...).register();
    
    // Error metrics
    Counter chunksFailedTotal   = Counter.builder("migration.chunks.failed").register();
    Counter cdcApplyErrors      = Counter.builder("migration.cdc.apply_errors").register();
    Counter connectionTimeouts  = Counter.builder("migration.connections.timeouts").register();
    
    // Source health
    Gauge   sourceCpuPercent    = Gauge.builder("migration.source.cpu_percent", ...).register();
    Gauge   sourceIoWait        = Gauge.builder("migration.source.io_wait_percent", ...).register();
    Gauge   sourceActiveSessions = Gauge.builder("migration.source.active_sessions", ...).register();
}
```

**Key Alerts**:
```yaml
alerts:
  - name: CDCLagCritical
    condition: migration.cdc.lag_seconds > 300
    severity: CRITICAL
    message: "CDC lag > 5 min. Check CDC connector and Kafka health."

  - name: BulkLoadStalled
    condition: rate(migration.rows.migrated, 10m) == 0
    severity: CRITICAL
    message: "No rows migrated in 10 minutes. Migration may be stalled."

  - name: SourceDBStressed
    condition: migration.source.cpu_percent > 80
    severity: CRITICAL
    message: "Source DB CPU > 80%. Auto-throttling migration."

  - name: ValidationMismatch
    condition: migration.validation.mismatch_rate > 0.001
    severity: CRITICAL
    message: "Data mismatch rate > 0.1%. Investigation required before cutover."
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Snapshot method** | DB-native (SCN/LSN/binlog) — no global locks | Oracle AS OF SCN is lock-free; PG exported snapshots enable parallel reads |
| **Bulk load parallelism** | Chunked by PK range, 500 parallel streams | Achieves 1 TB/hour; PK-based chunking enables resumability per chunk |
| **CDC technology** | Debezium + Kafka (log-based CDC) | Log-based is non-invasive (no triggers); Kafka decouples capture from apply |
| **CDC buffering** | Kafka with 7-day retention | Handles multi-day bulk loads; replay on failure; decouples producer/consumer speed |
| **Idempotency** | Upsert (INSERT ON CONFLICT DO UPDATE) | Handles overlap between bulk load and CDC; safe for retries and restarts |
| **Target write method** | PostgreSQL COPY (not INSERT) | 10x faster than batch INSERT; critical for 1 TB/hour throughput |
| **Index creation** | Deferred until after bulk load | Building indexes during load causes 3-5x slowdown; batch-create is faster |
| **Validation** | Multi-level (count → checksum → sample) | Full row comparison is infeasible at 200B rows; sampling catches type conversion bugs |
| **Cutover** | DNS/proxy switch with < 2s write pause | Minimizes downtime; proxy switch is atomic; reverse CDC enables rollback |
| **Checkpoint storage** | PostgreSQL (not in-memory) | Must survive worker crashes; transactional writes ensure consistency |
| **Source throttling** | Adaptive based on CPU/IO metrics | Prevents degradation of production traffic; business-hours aware scheduling |
| **No-PK tables** | ROWID/ctid for bulk + full-row CDC | Handles legacy tables; warns about duplicate-row risks |

## Interview Talking Points

1. **"Snapshot SCN/LSN is the handoff point"** — Everything ≤ SCN is in bulk load, everything > SCN comes from CDC. Zero gap, zero overlap. Oracle AS OF SCN is lock-free.
2. **"Chunked parallel loading with PK ranges"** — 500 parallel streams achieve 1 TB/hour. Each chunk checkpointed independently for resumability. PostgreSQL COPY is 10x faster than INSERT.
3. **"CDC via transaction log, not triggers"** — Log-based CDC (Debezium) is non-invasive. No performance impact on source. Captures all changes including schema changes.
4. **"Kafka buffers CDC during multi-day bulk load"** — 7-day retention handles 50-hour bulk loads. Events accumulate until table's bulk load completes, then applied in order.
5. **"Upsert for idempotent CDC application"** — INSERT ON CONFLICT DO UPDATE handles: (a) bulk load + CDC overlap, (b) CDC retries, (c) worker restarts. Final state always correct.
6. **"Deferred indexes — build after load"** — Creating indexes during bulk load causes 3-5x slowdown. We load data first, then CREATE INDEX CONCURRENTLY in one pass.
7. **"Multi-level validation: count → checksum → sample"** — Row counts take 2 minutes. Chunk checksums take 1.4 hours. 1% sample comparison catches subtle type conversion issues. Full compare is infeasible at 200B rows.
8. **"Cutover in < 2 seconds"** — Drain CDC to zero lag → pause source writes → apply final events → switch DNS → enable target writes. Total write pause: ~1.8 seconds.
9. **"Reverse CDC for rollback safety"** — After cutover, enable CDC from target → source. If issues found within 24 hours, can switch back. Safety net for the riskiest moment.
10. **"Adaptive throttling protects source DB"** — Monitor CPU/IO every 10 seconds. Auto-reduce parallelism if source stressed. Business-hours aware: 25% throughput during peak, 100% off-hours.
11. **"Schema translation is 95% auto, 5% manual"** — Rule-based type mapping handles common cases. Oracle DATE→TIMESTAMP (includes time!), NUMBER(1)→BOOLEAN. Stored procedures flagged for manual rewrite.
12. **"Checkpoint hierarchy: Job → Table → Chunk"** — 50-hour migrations will have failures. Every chunk (~30s of work) is checkpointed. Resume skips completed chunks. Zero repeated work.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Distributed Data Pipeline** | Same bulk data movement pattern | Pipeline is ongoing; migration is one-time with cutover |
| **Event Sourcing / CQRS** | CDC is core to event-driven architectures | Migration CDC is temporary; event sourcing is permanent |
| **Database Replication** | CDC + apply is essentially replication | Replication is same-engine; migration is cross-engine with schema translation |
| **ETL System** | Similar extract-transform-load pattern | ETL transforms for analytics; migration preserves data exactly |
| **Distributed Cache Warming** | Similar bulk population pattern | Cache is eventual; migration requires 100% correctness |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **CDC** | Debezium (open-source) | Oracle GoldenGate | Enterprise Oracle shops with existing license |
| **CDC** | Debezium | AWS DMS | Already on AWS, simpler setup, managed service |
| **Buffer** | Apache Kafka | AWS Kinesis | Serverless preference, lower ops overhead |
| **Bulk Load** | Custom chunked loader | Apache Spark | Very large datasets (100+ TB), existing Spark cluster |
| **Schema Translation** | Custom rule engine | AWS SCT | Quick start, covers common Oracle→PG patterns |
| **Validation** | Custom engine | Great Expectations | Data quality checks beyond migration correctness |
| **Coordinator** | Custom (Spring Boot) | Apache Airflow | DAG-based orchestration, existing Airflow deployment |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 10 topics (choose 2-3 based on interviewer interest)

---

**References**:
- AWS Database Migration Service (DMS) Architecture
- Debezium CDC Connector Documentation
- Oracle Flashback / AS OF SCN Documentation
- PostgreSQL Logical Replication & COPY Protocol
- Stripe's Online Migration Framework (blog post)
- GitHub's MySQL Migration to Vitess
- Uber's Schemaless → DocStore Migration
- Netflix Data Migration Patterns
