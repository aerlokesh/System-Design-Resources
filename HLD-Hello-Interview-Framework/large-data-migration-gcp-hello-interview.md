# Design a System to Migrate Large Data to Google Cloud — Hello Interview Framework

> **Question**: Design a system to migrate petabytes of data (files, databases, VM disks) from on-premise or another cloud to Google Cloud Platform with minimal downtime, zero data loss, and data integrity guarantees.
>
> **Source**: [systemdesign.io](https://systemdesign.io/question/create-a-system-to-migrate-large-data-to-google-cloud)
>
> **Key Interview Questions**:
> - How will you structure the migration? Which machine manages it?
> - How do we ensure no data is lost in the process?
> - How will you ensure that the file's data didn't change in transit?
> - What are some strategies to speed up the transfer?
>
> **Asked at**: Google, Amazon (AWS), Microsoft, Stripe, Netflix
>
> **Difficulty**: Hard | **Level**: Senior/Staff

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
1. **Data Discovery & Inventory**: Automatically scan and catalog all data sources (databases, file stores, VM disks) — sizes, types, dependencies, access patterns — to produce a migration plan.
2. **Multi-Type Data Transfer**: Migrate heterogeneous data — object storage (files/blobs), relational databases, NoSQL stores, and block storage (VM disks) — to their appropriate GCP targets (Cloud Storage, Cloud SQL, Compute Engine).
3. **Data Integrity Verification**: Guarantee zero data loss via checksum-based validation at chunk, file, and table level — before, during, and after migration.
4. **Minimal-Downtime Database Migration**: Support online database migration via Change Data Capture (CDC) with < 30-minute cutover for critical systems.
5. **Migration Orchestration**: A central coordinator that manages wave-based phased migration, handles dependency ordering, allocates bandwidth, and tracks per-resource state.
6. **Checkpoint & Resume**: Persist migration progress so that any failure (worker crash, network outage) resumes from the last checkpoint, never from zero.
7. **Progress Monitoring**: Real-time dashboard showing per-resource progress, transfer rates, errors, validation status, and ETA.

#### Nice to Have (P1)
- Automated rollback on cutover failure (reverse CDC / traffic switch)
- Bandwidth throttling with time-of-day scheduling (reduce during business hours)
- Transfer Appliance support for extremely large datasets (>100 TB over physical device)
- Cost prediction and optimization (storage class selection)
- Compliance reporting (GDPR data residency, HIPAA encryption audit trail)

#### Below the Line (Out of Scope)
- Application code refactoring for GCP APIs
- GCP project/IAM setup and network provisioning (assume Dedicated Interconnect exists)
- Post-migration performance tuning
- Multi-cloud simultaneous migration (single source → GCP only)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Transfer Throughput** | 10 TB/hour sustained (object storage) | Migrate 8 PB in ~5 months with overhead |
| **DB Replication Lag** | < 5 seconds steady-state | Application must see consistent data before cutover |
| **Data Integrity** | 100% — zero data loss, zero corruption | Enterprise/financial data demands absolute correctness |
| **Downtime (Critical DB)** | < 30 minutes during cutover | Business SLA requires high availability |
| **Downtime (Object Storage)** | Zero — background sync | Files can be synced invisibly behind applications |
| **Migration Service Uptime** | 99.9% | 6-month project can't tolerate stalled migration |
| **Resumability** | Resume from last chunk checkpoint | Multi-month migrations can't restart from scratch |
| **Parallel Streams** | 1000+ concurrent file transfers | Saturate 10-40 Gbps dedicated interconnect |
| **Security** | TLS 1.3 in transit, CMEK at rest | Compliance (GDPR, HIPAA, SOC 2) |

### Capacity Estimation

```
Total data to migrate: 10 PB

Breakdown by type:
  Relational databases:  500 TB  (PostgreSQL, MySQL, Oracle)  →  Cloud SQL
  Object storage:          8 PB  (files, backups, archives)   →  Cloud Storage
  Block storage:         1.5 PB  (VM disks)                   →  Compute Engine

Network: 10 Gbps Dedicated Interconnect (scale to 40 Gbps)
  Theoretical max: 10 Gbps = 1.25 GB/s = 4.5 TB/hour
  70% efficiency:  ~3.15 TB/hour = 75.6 TB/day
  With 40 Gbps:   ~12.6 TB/hour = 302 TB/day

Timeline estimate:
  10 PB / 75.6 TB/day = 132 days ≈ 4.5 months (raw transfer)
  With validation, cutover, buffer = 6 months total

Phased approach:
  Month 1-2:   Non-critical data (2 PB) — archives, dev/test
  Month 3-4:   Important data (5 PB) — active files, backups
  Month 5:     Critical databases (500 TB) + critical VMs (1 PB)
  Month 6:     Final validation, cutover, decommission

Migration infrastructure:
  File transfer workers:    100 instances (n2-standard-4)
  DB replication workers:    50 instances
  Validation workers:        50 instances
  Coordinator service:       10 instances (HA)
  Kafka cluster:             10 brokers (for CDC)
  Temporary staging:        500 TB Cloud Storage

  Monthly cost: ~$91K compute + network + storage
  Total 6-month cost: ~$580K (infrastructure only)
```

---

## 2️⃣ Core Entities

### Entity 1: Migration Job
```java
public class MigrationJob {
    private final String jobId;                      // UUID: "mig_abc123"
    private final String name;                       // "Q1-2026-GCP-Migration"
    private final MigrationPhase currentPhase;       // DISCOVERY, TRANSFER, VALIDATION, CUTOVER, DONE
    private final MigrationStatus status;            // PENDING, RUNNING, PAUSED, FAILED, COMPLETED
    private final List<MigrationWave> waves;         // Phased waves of resources
    private final NetworkConfig networkConfig;       // Interconnect, VPN, bandwidth allocation
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Map<String, ResourceMigrationState> resourceStates;
}

public enum MigrationPhase {
    DISCOVERY,      // Scan and inventory all data sources
    PLANNING,       // Generate migration plan with waves and priorities
    TRANSFER,       // Active data transfer (bulk + CDC + files)
    VALIDATION,     // Post-transfer integrity verification
    CUTOVER,        // Switch traffic from source to GCP
    DECOMMISSION,   // Shut down source systems
    COMPLETED
}
```

### Entity 2: Migration Resource
```java
public abstract class MigrationResource {
    private final String resourceId;               // UUID
    private final ResourceType type;               // DATABASE, OBJECT_STORE, VM_DISK
    private final String sourcePath;               // Connection string or path
    private final String gcpTarget;                // Cloud SQL instance, GCS bucket, GCE VM
    private final long totalBytes;
    private final int priority;                    // 0=critical, 1=important, 2=non-critical
    private final List<String> dependsOn;          // Resource IDs this depends on
    private final int waveNumber;
    private final ResourceMigrationState state;
}

public class DatabaseResource extends MigrationResource {
    private final DatabaseType dbType;             // POSTGRESQL, MYSQL, ORACLE, MONGODB
    private final long tableCount;
    private final int writeRateTps;
    private final MigrationStrategy strategy;      // ONLINE_CDC, OFFLINE_DUMP, HYBRID
    private final Duration maxDowntime;
}

public class ObjectStoreResource extends MigrationResource {
    private final long fileCount;
    private final String accessPattern;            // HOT, WARM, COLD, ARCHIVE
    private final String gcpStorageClass;          // STANDARD, NEARLINE, COLDLINE, ARCHIVE
    private final TransferMethod method;           // TRANSFER_SERVICE, APPLIANCE, GSUTIL
}

public class VMDiskResource extends MigrationResource {
    private final String vmName;
    private final String osType;
    private final int cpuCores;
    private final int memoryGB;
    private final String gceMachineType;
    private final List<String> applications;
}
```

### Entity 3: Chunk Checkpoint
```java
public class ChunkCheckpoint {
    private final String chunkId;
    private final long startOffset;                // Byte offset or PK range start
    private final long endOffset;
    private final long bytesTransferred;
    private final String sourceChecksum;           // SHA-256 of source chunk
    private final String destChecksum;             // SHA-256 after upload
    private final ChunkStatus status;              // PENDING, IN_PROGRESS, COMPLETED, FAILED
    private final int retryCount;
    private final Instant completedAt;
}
```

### Entity 4: Transfer Chunk (unit of work)
```java
public class TransferChunk {
    private final String chunkId;
    private final String resourceId;
    private final ResourceType resourceType;
    private final String sourcePath;
    private final String destPath;                 // GCS URI, Cloud SQL table, GCE disk
    private final long offsetStart;
    private final long offsetEnd;
    private final long sizeBytes;
    private final String expectedChecksum;         // Pre-computed source SHA-256
    private final CompressionType compression;     // GZIP, LZ4, NONE
    private final EncryptionConfig encryption;     // TLS + CMEK config
}
```

---

## 3️⃣ API Design

### 1. Create Migration Job
```
POST /api/v1/migrations

Request:
{
  "name": "Q1-2026-GCP-Migration",
  "source_environment": {
    "type": "ON_PREMISE",
    "datacenter": "us-east-dc1",
    "interconnect_id": "ic-prod-10gbps"
  },
  "gcp_project": "my-company-prod",
  "gcp_region": "us-central1",
  "resources": [
    {
      "type": "DATABASE",
      "source": { "db_type": "POSTGRESQL", "host": "pg-prod-01.internal", "port": 5432 },
      "gcp_target": "cloud-sql://orders-pg-prod",
      "priority": 0,
      "max_downtime_minutes": 30,
      "strategy": "ONLINE_CDC"
    },
    {
      "type": "OBJECT_STORE",
      "source": { "storage_type": "NFS", "mount_path": "/data/backups", "estimated_size_tb": 500 },
      "gcp_target": "gs://company-backups-prod",
      "gcp_storage_class": "NEARLINE",
      "priority": 1,
      "transfer_method": "TRANSFER_SERVICE"
    }
  ],
  "config": {
    "max_bandwidth_gbps": 10,
    "enable_compression": true,
    "validation_level": "FULL",
    "business_hours_throttle_pct": 25
  }
}

Response (201):
{
  "job_id": "mig_abc123",
  "status": "PENDING",
  "waves": [
    { "wave": 1, "resources": 50,  "data_size_tb": 2000, "type": "Non-critical" },
    { "wave": 2, "resources": 200, "data_size_tb": 5000, "type": "Important" },
    { "wave": 3, "resources": 100, "data_size_tb": 3000, "type": "Critical" }
  ],
  "estimated_duration_months": 6,
  "estimated_cost_usd": 580000
}
```

### 2. Get Migration Status
```
GET /api/v1/migrations/{job_id}/status

Response:
{
  "job_id": "mig_abc123",
  "current_phase": "TRANSFER",
  "overall_progress": {
    "bytes_transferred": "6.5 PB",
    "bytes_total": "10 PB",
    "percent_complete": 65.0,
    "current_transfer_rate_tbph": 8.5,
    "eta_days": 18
  },
  "by_type": {
    "databases": { "completed": 20, "total": 25, "cdc_lag_avg_sec": 2.1 },
    "object_stores": { "transferred_pb": 5.5, "total_pb": 8.0 },
    "vm_disks": { "completed": 325, "total": 500 }
  },
  "validation": { "checksum_pass_rate": 99.99, "files_validated": 65000000 }
}
```

### 3. Trigger Cutover
```
POST /api/v1/migrations/{job_id}/resources/{resource_id}/cutover

Request:
{
  "strategy": "ZERO_DOWNTIME_CDC",
  "max_lag_before_cutover_sec": 1,
  "pre_cutover_validation": true,
  "rollback_enabled": true
}

Response:
{
  "cutover_id": "cut_xyz789",
  "steps": [
    { "step": "VALIDATE_DATA",         "status": "COMPLETED" },
    { "step": "DRAIN_CDC_LAG",         "status": "COMPLETED" },
    { "step": "SET_SOURCE_READ_ONLY",  "status": "IN_PROGRESS" },
    { "step": "APPLY_FINAL_EVENTS",    "status": "PENDING" },
    { "step": "SWITCH_TRAFFIC_TO_GCP", "status": "PENDING" },
    { "step": "ENABLE_GCP_WRITES",     "status": "PENDING" }
  ]
}
```

### 4. Validate Resource
```
POST /api/v1/migrations/{job_id}/resources/{resource_id}/validate

Response:
{
  "status": "PASSED",
  "checks": {
    "file_count": { "source": 10500000, "destination": 10500000, "match": true },
    "total_bytes": { "source": "512 TB", "destination": "512 TB", "match": true },
    "checksum": { "files_checked": 10500000, "mismatches": 0 },
    "metadata": { "permissions_match": true, "timestamps_match": true }
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Object Storage Migration (8 PB)
```
1. Discovery agent scans source file systems (NFS, S3-compatible)
   → Catalogs: 100M files, 8 PB total, access pattern per directory
   ↓
2. Coordinator assigns transfer method per data class:
   - Hot data (2 PB)     → Transfer Service (incremental sync)
   - Warm data (3 PB)    → Transfer Service (batch)
   - Cold data (2 PB)    → Transfer Appliance (physical device)
   - Archive data (1 PB) → gsutil parallel upload
   ↓
3. Workers process file chunks in parallel:
   a. Read file from source
   b. Compress with LZ4 (if beneficial)
   c. Stream over Dedicated Interconnect (TLS 1.3)
   d. Write to Cloud Storage (multipart upload for files > 32 MB)
   e. Compute SHA-256 of uploaded object
   f. Compare checksum: source vs destination
   g. Checkpoint chunk as COMPLETED
   ↓
4. Incremental sync for hot data (hourly delta until cutover)
   ↓
5. Post-migration validation: file count, total bytes, checksums ✓
```

### Flow 2: Database Migration (500 TB with CDC)
```
1. Per-database strategy:
   - Critical DBs → ONLINE_CDC (< 30 min downtime)
   - Non-critical  → OFFLINE_DUMP (hours of downtime OK)
   ↓
2. Online CDC flow:
   a. Record snapshot point (pg_current_wal_lsn / SCN / binlog pos)
   b. Start parallel bulk load with exported snapshot
   c. Simultaneously start CDC connector (WAL stream → Kafka)
   d. After bulk load done → apply CDC events from Kafka
   e. Events ≤ snapshot LSN → SKIP; events > snapshot LSN → APPLY (upsert)
   f. Monitor replication lag → < 5 seconds
   ↓
3. Cutover: set read-only → drain lag → validate → switch → resume writes
   Total downtime: ~20 minutes
```

### Flow 3: VM Migration (500 VMs)
```
1. Install migration agent → snapshot disk → stream to GCP → create GCE VM
2. Boot test in GCP → cutover DNS/LB → shut down source VM
3. Waves: dev/test first → non-critical → critical (10 parallel at a time)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SOURCE ENVIRONMENT                                │
│             (On-Premise / AWS / Azure)                               │
│                                                                      │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │ Databases  │  │Object Storage│  │  VM Disks    │               │
│  │ 500 TB     │  │ 8 PB         │  │  1.5 PB      │               │
│  └─────┬──────┘  └──────┬───────┘  └──────┬───────┘               │
└────────┼────────────────┼─────────────────┼─────────────────────────┘
         │                │                 │
         ▼                ▼                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│              DISCOVERY & ASSESSMENT AGENT                            │
│  Scans all sources → inventory, dependency graph, migration plan    │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│           MIGRATION COORDINATOR  (10-node HA cluster)                │
│                                                                      │
│  ┌────────────────┐ ┌───────────────┐ ┌───────────────────────┐   │
│  │ Wave Planner   │ │ State Manager │ │ Bandwidth Allocator   │   │
│  │ (dependency    │ │ (checkpoints, │ │ (QoS: 40% DB,        │   │
│  │  DAG, priority)│ │  per-resource)│ │  50% files, 10% VMs) │   │
│  └────────────────┘ └───────────────┘ └───────────────────────┘   │
│                                                                      │
│  State Store: PostgreSQL (metadata, checkpoints, validation)         │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ FILE TRANSFER  │ │ DB REPLICATION │ │ VM MIGRATION   │
│ WORKERS (100)  │ │ WORKERS (50)   │ │ WORKERS (20)   │
│                │ │                │ │                │
│• 1000 parallel │ │• Bulk load     │ │• Snapshot+     │
│  streams       │ │  (PK chunks)   │ │  stream disk   │
│• LZ4 compress  │ │• CDC→Kafka→    │ │• Create GCE VM │
│• SHA-256 per   │ │  Upsert        │ │• Boot test     │
│  chunk         │ │• Replication   │ │• DNS cutover   │
│• Multipart     │ │  lag monitor   │ │                │
│  upload to GCS │ │• Checkpoint    │ │                │
│• Resume on fail│ │  per table     │ │                │
└───────┬────────┘ └───────┬────────┘ └───────┬────────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     NETWORK LAYER                                    │
│                                                                      │
│  PRIMARY:  Dedicated Interconnect (10-40 Gbps, <10ms, private)      │
│  BACKUP:   Cloud VPN (1 Gbps, auto-failover <30s)                   │
│  FALLBACK: Internet (for small, non-critical)                        │
│                                                                      │
│  Optimizations: TCP BBR, LZ4 compression, parallel streams,         │
│                 deduplication, QoS bandwidth allocation               │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   GOOGLE CLOUD PLATFORM                              │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                VALIDATION SERVICE (50 workers)                │  │
│  │  Pre:  Source health checks, connectivity                     │  │
│  │  During: Per-chunk checksum, replication lag                  │  │
│  │  Post: Row counts, file counts, full checksum sweep           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐        │
│  │  Cloud SQL   │  │Cloud Storage │  │ Compute Engine   │        │
│  │  (Databases) │  │  (Files)     │  │ (VMs)            │        │
│  │              │  │              │  │                   │        │
│  │  PostgreSQL  │  │  Standard    │  │  500 VMs          │        │
│  │  MySQL       │  │  Nearline    │  │  Auto-scaling     │        │
│  │  HA Multi-AZ │  │  Coldline    │  │  Load balanced    │        │
│  │              │  │  Archive     │  │                   │        │
│  └──────────────┘  └──────────────┘  └──────────────────┘        │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │             MONITORING & DASHBOARD                             │  │
│  │  Cloud Monitoring + Grafana: progress, bandwidth, errors,     │  │
│  │  validation status, ETA, cost tracking                        │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Scaling |
|-----------|---------|---------|
| **Migration Coordinator** | Orchestrate waves, manage state, allocate bandwidth | 10-node HA, leader election |
| **Discovery Agent** | Scan sources, build inventory, generate plan | Single pass, parallelized scan |
| **File Transfer Workers** | Parallel chunked file transfer with checksums | 100 pods, auto-scaled to 500 |
| **DB Replication Workers** | Bulk load + CDC for databases | 50 pods, 1 per source DB |
| **VM Migration Workers** | Snapshot, stream, create GCE VMs | 20 pods, 10-20 parallel VMs |
| **Kafka Cluster** | Buffer CDC events during multi-day migrations | 10 brokers, 7-day retention |
| **Validation Service** | Checksum verification, row/file counts | 50 workers, parallelized |
| **State Store** | Job metadata, checkpoints, validation reports | PostgreSQL (managed) |

---

## 6️⃣ Deep Dives

### Deep Dive 1: How Will You Structure the Migration? Which Machine Manages It?

**The Problem**: With 10 PB across databases, files, and VMs — all with different migration methods, priorities, and dependencies — we need a central brain that orchestrates everything without becoming a bottleneck.

**Answer: The Migration Coordinator**

The coordinator is a 10-node HA cluster that acts as the "control plane" for the entire migration. It does NOT transfer data itself — it assigns work to specialized workers and tracks their progress.

```java
public class MigrationCoordinator {

    /**
     * The coordinator is the "brain" of the migration system.
     * It runs as a 10-node cluster with leader election (via ZooKeeper).
     * Only the leader makes scheduling decisions; followers replicate state.
     * 
     * Key responsibilities:
     * 1. Wave Planning: determine which resources to migrate in which order
     * 2. Worker Assignment: dispatch chunks to appropriate worker pools
     * 3. Bandwidth Allocation: QoS across competing transfer streams
     * 4. State Tracking: checkpoint every chunk for resumability
     * 5. Failure Handling: detect failed workers, reschedule their chunks
     */

    private final WavePlanner wavePlanner;
    private final WorkerPool fileWorkers;       // 100 instances
    private final WorkerPool dbWorkers;         // 50 instances
    private final WorkerPool vmWorkers;         // 20 instances
    private final CheckpointStore checkpointStore;  // PostgreSQL
    private final BandwidthAllocator bandwidthAllocator;

    public void executeMigration(MigrationJob job) {
        // Phase 1: Plan waves based on priority and dependencies
        List<MigrationWave> waves = wavePlanner.plan(job);
        // Wave 1: non-critical (archives, dev/test) — 2 PB
        // Wave 2: important (active files, backups) — 5 PB
        // Wave 3: critical (databases, prod VMs) — 3 PB

        for (MigrationWave wave : waves) {
            // Phase 2: Topological sort resources by dependency DAG
            List<MigrationResource> ordered = topologicalSort(wave.getResources());

            // Phase 3: Dispatch resources to worker pools
            for (MigrationResource resource : ordered) {
                // Wait for dependencies to complete
                waitForDependencies(resource.getDependsOn());

                switch (resource.getType()) {
                    case OBJECT_STORE:
                        dispatchFileTransfer((ObjectStoreResource) resource);
                        break;
                    case DATABASE:
                        dispatchDatabaseMigration((DatabaseResource) resource);
                        break;
                    case VM_DISK:
                        dispatchVMMigration((VMDiskResource) resource);
                        break;
                }
            }

            // Phase 4: Wait for all resources in wave to complete + validate
            waitForWaveCompletion(wave);
            validateWave(wave);  // All checksums must pass before next wave
        }
    }

    /**
     * Dependency example:
     *   postgres-main → api-service → web-app
     *   Must migrate postgres-main first, then api-service, then web-app
     */
    private List<MigrationResource> topologicalSort(List<MigrationResource> resources) {
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (MigrationResource r : resources) {
            graph.putIfAbsent(r.getResourceId(), new ArrayList<>());
            inDegree.putIfAbsent(r.getResourceId(), 0);
            for (String dep : r.getDependsOn()) {
                graph.computeIfAbsent(dep, k -> new ArrayList<>()).add(r.getResourceId());
                inDegree.merge(r.getResourceId(), 1, Integer::sum);
            }
        }

        // BFS (Kahn's algorithm)
        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<MigrationResource> sorted = new ArrayList<>();
        Map<String, MigrationResource> lookup = resources.stream()
            .collect(Collectors.toMap(MigrationResource::getResourceId, Function.identity()));

        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(lookup.get(id));
            for (String neighbor : graph.getOrDefault(id, List.of())) {
                if (inDegree.merge(neighbor, -1, Integer::sum) == 0) {
                    queue.add(neighbor);
                }
            }
        }
        return sorted;
    }

    private void dispatchFileTransfer(ObjectStoreResource resource) {
        // Split files into chunks of 10,000 files each
        List<TransferChunk> chunks = createFileChunks(resource, 10_000);

        // Assign bandwidth quota
        long bandwidthBps = bandwidthAllocator.allocate(resource, 0.50); // 50% of total

        // Submit chunks to file worker pool (round-robin across 100 workers)
        for (TransferChunk chunk : chunks) {
            // Skip already-completed chunks (resumability)
            if (checkpointStore.isCompleted(chunk.getChunkId())) continue;
            fileWorkers.submit(new FileTransferTask(chunk, bandwidthBps));
        }
    }
}
```

**Why 10-node HA cluster?**
```
- Leader handles scheduling decisions (~1000 decisions/minute)
- Followers replicate state store (hot standby)
- If leader fails: new leader elected in <5 seconds (ZooKeeper)
- State persisted in PostgreSQL
- No data transfer through coordinator — only control plane traffic
- Coordinator CPU usage: <10% (it only makes decisions, not moves data)
```

**Wave-based phased migration structure**:
```
Wave 1 (Month 1-2): Non-critical — 2 PB
  ├─ Archives (1 PB) → Cloud Storage Archive
  ├─ Old backups (500 TB) → Cloud Storage Coldline
  └─ Dev/test data (500 TB) → Cloud Storage Standard
  Purpose: Prove the process, build confidence, low-risk data

Wave 2 (Month 3-4): Important — 5 PB
  ├─ Active files (3 PB) → Cloud Storage Standard
  ├─ Recent backups (1.5 PB) → Cloud Storage Nearline
  └─ Non-critical VMs (100 VMs, 500 TB) → Compute Engine
  Purpose: Bulk of the data, process is now proven

Wave 3 (Month 5): Critical — 3 PB
  ├─ Production databases (500 TB) → Cloud SQL (via CDC)
  └─ Production VMs (400 VMs, 1 PB) → Compute Engine
  Purpose: Highest risk, done last with proven methods

Wave 4 (Month 6): Validation + Decommission
  ├─ Full validation sweep (100% checksum)
  ├─ Final cutover of remaining systems
  └─ Decommission source environment
```

---

### Deep Dive 2: How Do We Ensure No Data Is Lost?

**The Problem**: Over a 6-month migration of 10 PB across 100M files and 25 databases, many things can go wrong — network drops, worker crashes, disk corruption, race conditions. We need absolute guarantees that every byte arrives intact.

**Answer: Three-Layer Zero-Loss Architecture**

```java
public class ZeroDataLossGuarantee {

    /**
     * LAYER 1: Checkpoint-Based Resumability
     * Every unit of work (chunk) is checkpointed AFTER successful transfer.
     * If anything fails, we resume from the last checkpoint — never lose progress.
     * 
     * LAYER 2: Per-Chunk Checksum Verification
     * Every chunk has a SHA-256 computed at source and compared at destination.
     * If checksums don't match → retry that chunk. Never mark as complete.
     * 
     * LAYER 3: Post-Migration Full Validation
     * After all transfers complete, run a comprehensive sweep:
     * - File count comparison (source vs destination)
     * - Total bytes comparison
     * - Full checksum verification (100% of files)
     * - Database row counts + table checksums
     * - Metadata validation (timestamps, permissions)
     */

    // === LAYER 1: Checkpoint & Resume ===

    public void transferFileChunk(TransferChunk chunk) {
        // Step 1: Check if already completed (from a previous run)
        if (checkpointStore.isCompleted(chunk.getChunkId())) {
            log.info("Chunk {} already completed, skipping", chunk.getChunkId());
            return; // Resume past this chunk
        }

        try {
            // Step 2: Compute source checksum BEFORE transfer
            String sourceChecksum = computeSHA256(chunk.getSourcePath(),
                                                   chunk.getOffsetStart(),
                                                   chunk.getOffsetEnd());

            // Step 3: Transfer data
            byte[] data = readFromSource(chunk);
            byte[] compressed = compress(data, chunk.getCompression());
            writeToGCS(chunk.getDestPath(), compressed);

            // Step 4: Compute destination checksum AFTER transfer
            String destChecksum = computeSHA256FromGCS(chunk.getDestPath());

            // Step 5: VERIFY checksums match
            if (!sourceChecksum.equals(destChecksum)) {
                throw new ChecksumMismatchException(
                    "Chunk " + chunk.getChunkId() + ": source=" + sourceChecksum
                    + " dest=" + destChecksum);
            }

            // Step 6: Checkpoint as COMPLETED (atomic write to PostgreSQL)
            checkpointStore.markCompleted(chunk.getChunkId(), sourceChecksum, destChecksum);

        } catch (Exception e) {
            // Mark as FAILED, increment retry count
            checkpointStore.markFailed(chunk.getChunkId(), e.getMessage());

            if (chunk.getRetryCount() < MAX_RETRIES) {
                retryQueue.enqueue(chunk.withRetry(chunk.getRetryCount() + 1));
            } else {
                alertService.critical("Chunk " + chunk.getChunkId()
                    + " failed after " + MAX_RETRIES + " retries: " + e.getMessage());
            }
        }
    }

    // === LAYER 2: Database CDC Zero-Loss ===

    /**
     * For databases, zero data loss requires:
     * 1. Consistent snapshot at a known log position (LSN/SCN)
     * 2. CDC captures ALL changes from that position forward
     * 3. Idempotent upserts handle any overlap safely
     * 4. Final validation confirms source == destination
     */
    public void ensureDatabaseZeroLoss(DatabaseResource db) {
        // Take consistent snapshot
        long snapshotLSN = recordSnapshotPoint(db);

        // Start CDC from snapshot point (captures everything after)
        startCDCFromPosition(db, snapshotLSN);

        // Bulk load uses snapshot isolation (captures everything at/before snapshot)
        startBulkLoadWithSnapshot(db, snapshotLSN);

        // Result: bulk load covers ≤ LSN, CDC covers > LSN
        // Together they cover 100% of data with NO gap and NO loss

        // Idempotent upserts (INSERT ON CONFLICT DO UPDATE) handle any
        // edge-case overlap at the boundary — safe to replay
    }

    // === LAYER 3: Post-Migration Full Validation ===

    public ValidationReport fullValidation(MigrationJob job) {
        ValidationReport report = new ValidationReport();

        // For each file resource
        for (ObjectStoreResource resource : job.getFileResources()) {
            long sourceCount = countFilesInSource(resource);
            long destCount = countFilesInGCS(resource);
            report.addCheck("file_count", resource.getResourceId(),
                            sourceCount == destCount);

            // Full checksum sweep (100% of files)
            List<String> mismatches = new ArrayList<>();
            for (FileManifestEntry entry : getManifest(resource)) {
                String destChecksum = getGCSChecksum(entry.getDestPath());
                if (!entry.getSourceChecksum().equals(destChecksum)) {
                    mismatches.add(entry.getPath());
                }
            }
            report.addCheck("checksum", resource.getResourceId(),
                            mismatches.isEmpty());
        }

        // For each database resource
        for (DatabaseResource db : job.getDatabaseResources()) {
            for (String table : db.getTables()) {
                long sourceRows = countRows(db.getSource(), table);
                long destRows = countRows(db.getGcpTarget(), table);
                report.addCheck("row_count:" + table, db.getResourceId(),
                                sourceRows == destRows);

                // Table-level checksum
                String srcHash = tableChecksum(db.getSource(), table);
                String dstHash = tableChecksum(db.getGcpTarget(), table);
                report.addCheck("table_checksum:" + table, db.getResourceId(),
                                srcHash.equals(dstHash));
            }
        }

        return report; // Must be 100% PASSED before cutover is allowed
    }
}
```

**Failure scenarios and how zero-loss is maintained**:
```
Scenario 1: Worker crashes mid-transfer of chunk #42
  → Chunk #42 never checkpointed as COMPLETED
  → On restart: coordinator sees chunk #42 as IN_PROGRESS
  → Reschedules to another worker → re-transfers from start of chunk
  → Checksum verified → checkpoint → no data lost ✓

Scenario 2: Network drops for 2 hours during file transfer
  → In-flight chunks fail with timeout
  → Chunks marked as FAILED in checkpoint store
  → When network restores: workers retry failed chunks
  → Already-completed chunks are skipped (checkpointed)
  → No data lost, no duplicates ✓

Scenario 3: Database row inserted during CDC handoff (edge case)
  → Row at exactly the snapshot boundary
  → Bulk load includes it (snapshot isolation)
  → CDC also captures it (event at boundary LSN)
  → CDC applies as UPSERT (INSERT ON CONFLICT DO UPDATE)
  → Same data written twice = idempotent → final state correct ✓

Scenario 4: File corrupted during transfer (bit flip)
  → Source checksum: abc123
  → Destination checksum: xyz789 (different!)
  → Worker detects mismatch → marks chunk FAILED → re-transfers
  → After re-transfer: checksums match → checkpoint → no corruption ✓
```

---

### Deep Dive 3: How Will You Ensure the File's Data Didn't Change in Transit?

**The Problem**: When transferring 100 million files over months, how do we guarantee that every single file arrived exactly as it was at the source? Network corruption, silent disk errors, and encoding issues can all cause subtle data changes.

**Answer: Multi-Level Checksum Architecture**

```java
public class DataIntegrityVerifier {

    /**
     * LEVEL 1: Per-Chunk Checksum (during transfer)
     *   - Before sending: compute SHA-256 of source chunk
     *   - After receiving: compute SHA-256 of destination chunk
     *   - Compare immediately → retry if mismatch
     *   - Granularity: every 100 MB chunk
     * 
     * LEVEL 2: Per-File/Object Checksum (after transfer)
     *   - Cloud Storage computes CRC32C and MD5 on ingestion
     *   - Compare against source-computed checksum
     *   - Catches any corruption during multipart assembly
     * 
     * LEVEL 3: Manifest-Based Full Sweep (post-migration)
     *   - Pre-compute manifest: {path, size, SHA-256} for every source file
     *   - Post-migration: verify every destination object matches manifest
     *   - Catches anything missed by per-chunk checks
     */

    // === LEVEL 1: Per-Chunk Verification ===

    public TransferResult transferWithIntegrity(TransferChunk chunk) {
        // Read source data
        byte[] sourceData = readSource(chunk.getSourcePath(),
                                        chunk.getOffsetStart(), chunk.getSizeBytes());

        // Compute source checksum
        String sourceHash = sha256(sourceData);

        // Compress (preserves integrity — checksum is of raw data)
        byte[] compressed = lz4Compress(sourceData);

        // Transfer over TLS 1.3 (encryption in transit)
        uploadToGCS(chunk.getDestPath(), compressed);

        // Read back from GCS and decompress
        byte[] destCompressed = downloadFromGCS(chunk.getDestPath());
        byte[] destData = lz4Decompress(destCompressed);

        // Compute destination checksum
        String destHash = sha256(destData);

        // VERIFY
        if (!sourceHash.equals(destHash)) {
            throw new IntegrityViolationException(
                String.format("Chunk %s integrity failed: src=%s dst=%s",
                    chunk.getChunkId(), sourceHash, destHash));
        }

        return new TransferResult(chunk.getChunkId(), sourceHash, destHash, true);
    }

    // === LEVEL 2: GCS-Native Verification ===

    /**
     * Cloud Storage computes checksums on upload.
     * We can verify without downloading the entire file again.
     */
    public boolean verifyGCSObject(String gcsPath, String expectedMD5) {
        // GCS returns object metadata including MD5 and CRC32C
        GCSObjectMetadata metadata = gcsClient.getObjectMetadata(gcsPath);

        // Compare MD5
        if (!metadata.getMd5Hash().equals(expectedMD5)) {
            log.error("GCS object {} MD5 mismatch: expected={} actual={}",
                gcsPath, expectedMD5, metadata.getMd5Hash());
            return false;
        }

        // Also verify object size
        if (metadata.getSize() != expectedSize) {
            log.error("GCS object {} size mismatch: expected={} actual={}",
                gcsPath, expectedSize, metadata.getSize());
            return false;
        }

        return true;
    }

    // === LEVEL 3: Manifest-Based Full Sweep ===

    /**
     * Pre-migration: build a complete manifest of every file at the source.
     * Post-migration: verify every entry in the manifest exists at destination
     * with matching checksum and size.
     */
    public ManifestValidationResult validateAgainstManifest(
            ObjectStoreResource resource) {

        // Load pre-computed manifest (built during discovery phase)
        FileManifest manifest = manifestStore.load(resource.getResourceId());
        // Example: 10,500,000 entries for one NFS share

        ManifestValidationResult result = new ManifestValidationResult();
        AtomicInteger validated = new AtomicInteger(0);
        AtomicInteger mismatched = new AtomicInteger(0);
        AtomicInteger missing = new AtomicInteger(0);

        // Parallel validation across 50 workers
        manifest.entries().parallelStream().forEach(entry -> {
            String gcsPath = mapToGCSPath(entry.getSourcePath(), resource);

            // Check existence
            if (!gcsClient.objectExists(gcsPath)) {
                result.addMissing(entry.getSourcePath());
                missing.incrementAndGet();
                return;
            }

            // Check checksum
            GCSObjectMetadata metadata = gcsClient.getObjectMetadata(gcsPath);
            if (!entry.getSha256().equals(metadata.getCustomMetadata("source-sha256"))) {
                result.addMismatch(entry.getSourcePath(),
                    entry.getSha256(), metadata.getCustomMetadata("source-sha256"));
                mismatched.incrementAndGet();
                return;
            }

            validated.incrementAndGet();
        });

        result.setSummary(validated.get(), mismatched.get(), missing.get());
        return result;
    }

    // === Database Row-Level Integrity ===

    /**
     * For databases: compute a deterministic checksum per table chunk.
     * Order rows by PK → hash each row → aggregate into chunk checksum.
     */
    public String computeTableChunkChecksum(Connection conn, String table,
                                             long pkStart, long pkEnd) {
        // PostgreSQL example:
        String sql = """
            SELECT md5(string_agg(row_hash, '' ORDER BY pk_col)) AS chunk_checksum
            FROM (
                SELECT pk_col, md5(ROW(*)::text) AS row_hash
                FROM %s
                WHERE pk_col BETWEEN %d AND %d
                ORDER BY pk_col
            ) sub
            """.formatted(table, pkStart, pkEnd);

        return conn.queryScalar(sql);
    }
}
```

**Checksum verification at every layer**:
```
Source File (on-premise)
  │
  ├─ SHA-256 computed ─────────────────────────────┐
  │                                                 │
  ▼                                                 │
LZ4 Compression                                    │
  │                                                 │
  ▼                                                 │
TLS 1.3 Encryption (in-transit)                    │
  │                                                 │
  ▼                                                 │
GCS Multipart Upload                               │
  │                                                 │
  ├─ GCS CRC32C + MD5 computed on ingest ──────┐  │
  │                                             │  │
  ▼                                             │  │
Destination Object (Cloud Storage)              │  │
  │                                             │  │
  ├─ SHA-256 computed ──────────────────────────┤──┤
  │                                             │  │
  ▼                                             ▼  ▼
COMPARE: source SHA-256 == destination SHA-256?
         source size == destination size?
         GCS MD5 == expected MD5?

If ALL match: ✅ CHUNK VERIFIED — checkpoint as COMPLETED
If ANY mismatch: ❌ RETRY — re-transfer chunk from source
```

---

### Deep Dive 4: What Are Some Strategies to Speed Up the Transfer?

**The Problem**: 10 PB over a 10 Gbps link takes 132 days at theoretical max. In practice, individual file transfers are much slower due to latency, small files, protocol overhead, and contention. We need strategies to approach wire-speed.

**Answer: Seven Acceleration Strategies**

```java
public class TransferAccelerator {

    /**
     * STRATEGY 1: Massive Parallelism
     * A single TCP stream on a 10 Gbps link gets maybe 200 Mbps (latency-limited).
     * 1000 parallel streams can saturate the full 10 Gbps.
     * 
     * Each of 100 workers runs 10 concurrent transfers = 1000 total streams.
     */
    
    // Single stream: limited by RTT (round-trip time)
    // Bandwidth = TCP window size / RTT
    // Window = 4 MB, RTT = 20ms → 200 MB/s = 1.6 Gbps (ONE stream)
    // 
    // 100 workers × 10 streams each = 1000 streams
    // 1000 × 200 MB/s = theoretical 200 GB/s (way more than 10 Gbps)
    // Actual: saturate 10 Gbps link → 1.25 GB/s = 4.5 TB/hour
    
    private static final int WORKERS = 100;
    private static final int STREAMS_PER_WORKER = 10;
    private static final int TOTAL_STREAMS = WORKERS * STREAMS_PER_WORKER; // 1000

    /**
     * STRATEGY 2: Compression
     * Compress data before transfer → effectively increases bandwidth.
     * Text files: 70-80% reduction (5:1 ratio)
     * Logs: 85-90% reduction (10:1 ratio)
     * Already-compressed (images, video): skip compression (0% gain)
     */
    public byte[] smartCompress(byte[] data, String fileType) {
        if (isAlreadyCompressed(fileType)) {
            return data; // Don't compress images, videos, zips
        }

        byte[] compressed = LZ4.compress(data); // LZ4: fast, decent ratio
        double ratio = (double) compressed.length / data.length;

        if (ratio > 0.90) {
            return data; // Compression not worthwhile (<10% savings)
        }

        return compressed; // Return compressed version
        
        // Effective bandwidth increase:
        // 10 Gbps link + 3:1 average compression = 30 Gbps effective
        // 4.5 TB/hour → 13.5 TB/hour effective throughput
    }

    /**
     * STRATEGY 3: Multipart Upload (for large files)
     * Cloud Storage supports parallel upload of file parts.
     * A 10 GB file uploaded as 100 × 100 MB parts → 100x speedup.
     */
    public void multipartUpload(String sourcePath, String gcsPath, long fileSize) {
        int partSize = 100 * 1024 * 1024; // 100 MB parts
        int numParts = (int) Math.ceil((double) fileSize / partSize);

        // Upload parts in parallel
        List<CompletableFuture<PartResult>> futures = new ArrayList<>();
        for (int i = 0; i < numParts; i++) {
            long offset = (long) i * partSize;
            long length = Math.min(partSize, fileSize - offset);
            futures.add(CompletableFuture.supplyAsync(() ->
                uploadPart(sourcePath, gcsPath, offset, length, i)));
        }

        // Wait for all parts
        List<PartResult> parts = futures.stream()
            .map(CompletableFuture::join).toList();

        // Compose parts into final object
        gcsClient.composeObject(gcsPath, parts);

        // 10 GB file: serial upload @ 100 MB/s = 100 seconds
        //             100 parallel parts @ 100 MB/s each = 1 second
        // Speedup: 100x
    }

    /**
     * STRATEGY 4: Deduplication
     * Skip files that already exist at destination with matching checksum.
     * Critical for incremental syncs and retries.
     */
    public boolean shouldTransfer(FileManifestEntry entry) {
        // Check if file already exists at destination
        if (!gcsClient.objectExists(entry.getDestPath())) {
            return true; // File doesn't exist — must transfer
        }

        // Check if checksums match
        String destChecksum = gcsClient.getChecksum(entry.getDestPath());
        if (entry.getSourceChecksum().equals(destChecksum)) {
            return false; // Already transferred correctly — SKIP
        }

        return true; // Checksum mismatch — re-transfer
        
        // In practice: 20-40% of files are duplicates across directories
        // Savings: skip 2-3 PB of redundant transfers
    }

    /**
     * STRATEGY 5: TCP Tuning
     * Optimize TCP parameters for long-distance, high-bandwidth transfers.
     */
    public void configureTCPOptimizations() {
        // BBR congestion control (developed by Google)
        // Better than Cubic for high-bandwidth, high-latency links
        // sysctl -w net.ipv4.tcp_congestion_control=bbr

        // Large socket buffers (for high bandwidth-delay product)
        // sysctl -w net.core.rmem_max=16777216  (16 MB)
        // sysctl -w net.core.wmem_max=16777216  (16 MB)

        // Large TCP window (auto-tuned with BBR)
        // sysctl -w net.ipv4.tcp_window_scaling=1

        // Result: single stream throughput increases from 200 Mbps → 2 Gbps
        // With 1000 streams: easily saturate 10-40 Gbps
    }

    /**
     * STRATEGY 6: Small File Batching
     * Small files (<1 MB) have high per-file overhead (connection setup,
     * metadata operations). Batch them into tar archives for transfer.
     */
    public void batchSmallFiles(List<FileManifestEntry> smallFiles) {
        // Group into batches of 10,000 files
        List<List<FileManifestEntry>> batches = Lists.partition(smallFiles, 10_000);

        for (List<FileManifestEntry> batch : batches) {
            // Create tar archive in memory
            byte[] tarArchive = createTarArchive(batch);

            // Transfer single tar file (much faster than 10,000 individual transfers)
            uploadToGCS("gs://bucket/batches/batch-" + batchId + ".tar", tarArchive);

            // Extract on destination (Cloud Function or worker)
            extractTarOnGCS("gs://bucket/batches/batch-" + batchId + ".tar",
                            "gs://bucket/data/");
        }

        // 10,000 small files individually: 10,000 HTTP requests = 100 seconds
        // 10,000 files as one tar: 1 HTTP request = 0.5 seconds
        // Speedup: 200x for small files
    }

    /**
     * STRATEGY 7: Transfer Appliance (for massive cold data)
     * Skip the network entirely for very large datasets.
     * Google ships a physical device to your datacenter.
     */
    public void useTransferAppliance(ObjectStoreResource resource) {
        // 1 PB Transfer Appliance:
        //   Copy to device: 1 PB / 10 Gbps local = 9 days
        //   Ship to Google: 3 days
        //   Ingest to GCS: 3 days
        //   Total: ~15 days
        //
        // vs Network transfer:
        //   1 PB / 10 Gbps WAN = 9 days (but consumes all bandwidth)
        //
        // Transfer Appliance advantage:
        //   - Doesn't consume network bandwidth (other transfers continue)
        //   - No bandwidth cost
        //   - Encrypted device (AES-256)
        //   - Best for cold/archive data that doesn't need incremental sync
    }
}
```

**Speed comparison summary**:
```
Strategy              │ Without    │ With       │ Speedup
──────────────────────┼────────────┼────────────┼─────────
Parallel streams      │ 200 Mbps   │ 10 Gbps    │ 50x
Compression           │ 4.5 TB/hr  │ 13.5 TB/hr │ 3x
Multipart upload      │ 100 sec    │ 1 sec      │ 100x (per large file)
Deduplication         │ 10 PB      │ 7 PB       │ 30% less data
TCP tuning (BBR)      │ 200 Mbps   │ 2 Gbps     │ 10x (per stream)
Small file batching   │ 100 sec    │ 0.5 sec    │ 200x (per batch)
Transfer Appliance    │ Network    │ Physical   │ ∞ (zero network)

Combined effective throughput:
  Raw link: 4.5 TB/hour
  Optimized: 10-15 TB/hour (compression + parallelism)
  With appliance: ~20 TB/hour equivalent (parallel paths)
```

---

### Deep Dive 5: Checkpoint & Resumability — Surviving Multi-Month Failures

**The Problem**: A 6-month migration will absolutely encounter failures. Workers crash, networks drop, disks fill up. We can't restart from scratch — that could cost weeks.

```java
public class CheckpointStore {

    /**
     * Checkpoint granularity:
     *   Files: per batch of 10,000 files (~30 seconds of work)
     *   Databases: per chunk of 1M rows (~60 seconds of work)
     *   VMs: per disk snapshot
     * 
     * On failure, maximum re-work is ONE chunk (~30-60 seconds).
     * All completed chunks are skipped on restart.
     */

    @Transactional
    public void markChunkCompleted(String jobId, String resourceId,
                                    String chunkId, long bytesTransferred,
                                    String sourceChecksum, String destChecksum) {
        jdbc.update("""
            INSERT INTO migration_checkpoints
                (job_id, resource_id, chunk_id, bytes_transferred,
                 source_checksum, dest_checksum, status, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED', NOW())
            ON CONFLICT (job_id, resource_id, chunk_id)
            DO UPDATE SET status = 'COMPLETED',
                          bytes_transferred = ?,
                          completed_at = NOW()
            """, jobId, resourceId, chunkId, bytesTransferred,
                sourceChecksum, destChecksum, bytesTransferred);
    }

    /**
     * Resume: find all incomplete chunks for a resource
     */
    public List<String> getIncompleteChunks(String jobId, String resourceId) {
        return jdbc.queryForList("""
            SELECT chunk_id FROM migration_checkpoints
            WHERE job_id = ? AND resource_id = ? AND status != 'COMPLETED'
            ORDER BY chunk_id
            """, String.class, jobId, resourceId);
    }

    /**
     * Resume migration from where we left off
     */
    public ResumePoint getResumePoint(String jobId) {
        // Get job status
        MigrationJob job = jobStore.get(jobId);

        // Get completed resources (skip entirely)
        Set<String> completedResources = getCompletedResources(jobId);

        // Get per-resource incomplete chunks (resume within resource)
        Map<String, List<String>> incompleteChunks = new HashMap<>();
        for (String resourceId : job.getAllResourceIds()) {
            if (!completedResources.contains(resourceId)) {
                incompleteChunks.put(resourceId, getIncompleteChunks(jobId, resourceId));
            }
        }

        return new ResumePoint(job.getCurrentPhase(), completedResources, incompleteChunks);
    }
}
```

**Resume example**:
```
Migration state at failure:
  Job: mig_abc123, Phase: TRANSFER
  Resources:
    db-orders (25 chunks): 23/25 complete → Resume from chunk 24
    files-backup (1000 chunks): 850/1000 complete → Resume from chunk 851
    vm-web-01: COMPLETED (skip)
    vm-api-01: NOT_STARTED → Start fresh

On restart:
  1. Load resume point from PostgreSQL
  2. Skip vm-web-01 (already done)
  3. Skip db-orders chunks 1-23 (already done), process chunks 24-25
  4. Skip files-backup chunks 1-850 (already done), process chunks 851-1000
  5. Start vm-api-01 from scratch

Maximum re-work: 2 chunks (~1 minute) instead of restarting everything
```

---

### Deep Dive 6: Network Optimization — Dedicated Interconnect vs VPN vs Internet

```
┌─────────────────────────────────────────────────────────────────────┐
│                  NETWORK PATH SELECTION                              │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ PATH 1: Dedicated Interconnect (PRIMARY)                      │ │
│  │ Bandwidth: 10-40 Gbps    Latency: <10ms    SLA: 99.99%      │ │
│  │ Private link (not over internet)                              │ │
│  │ Cost: $0.02/GB port + $0 data transfer                       │ │
│  │ Use for: ALL data (databases, files, VMs)                     │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ PATH 2: Cloud VPN (BACKUP)                                    │ │
│  │ Bandwidth: 1 Gbps        Latency: 30-50ms  SLA: 99.9%       │ │
│  │ Encrypted tunnel over internet                                │ │
│  │ Auto-failover: <30 seconds if Interconnect fails              │ │
│  │ Use for: failover, small non-critical transfers               │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ PATH 3: Internet (FALLBACK)                                   │ │
│  │ Bandwidth: variable       Latency: variable                   │ │
│  │ Use for: tiny files, last-resort only                         │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  Failover: Primary fails → VPN active in <30s → migration continues │
│  Bandwidth scaling: 10 Gbps → 20 Gbps → 40 Gbps as needed          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Alternative | Why |
|----------|--------|-------------|-----|
| **Migration structure** | Phased waves (non-critical → critical) | Big-bang (all at once) | Lower risk per wave; learn from each phase; easier rollback |
| **Orchestration** | Central coordinator (10-node HA) | Decentralized (each worker self-manages) | Need global state for dependencies, bandwidth QoS, wave gating |
| **Network** | Dedicated Interconnect (10-40 Gbps) | Internet / VPN only | Predictable throughput, lower latency, cost-effective for PBs |
| **Data integrity** | SHA-256 per chunk + manifest sweep | Trust the network (no checksums) | Zero tolerance for corruption; checksums catch silent errors |
| **DB migration** | Online CDC for critical DBs | Offline dump/restore for all | < 30 min downtime for critical; dump acceptable for non-critical |
| **CDC buffering** | Kafka (7-day retention) | Direct apply (no buffer) | Multi-day bulk loads need buffering; Kafka decouples capture/apply |
| **File transfer** | Parallel streams + compression + batching | Sequential transfer | 50x-200x speedup; necessary to hit 6-month timeline |
| **Large cold data** | Transfer Appliance (physical device) | Network transfer | Doesn't consume bandwidth; faster for >100 TB of cold data |
| **Checkpoint storage** | PostgreSQL (transactional) | In-memory / Redis | Must survive crashes; transactional writes ensure consistency |
| **Validation** | 100% checksum sweep | Sampling (1% spot-check) | Enterprise data requires absolute correctness guarantee |

## Interview Talking Points

1. **"The coordinator is the brain, workers are the muscles"** — Coordinator (10-node HA) makes scheduling decisions and tracks state. Workers (170 instances) do the actual data transfer. Separation of concerns: coordinator never touches data.

2. **"Three layers prevent data loss"** — Layer 1: per-chunk checkpoint (resume on failure). Layer 2: SHA-256 checksum per chunk (catch corruption). Layer 3: full manifest sweep post-migration (catch anything missed).

3. **"Snapshot LSN is the handoff point for databases"** — Bulk load covers everything ≤ LSN. CDC covers everything > LSN. Together: zero gap, zero overlap. Idempotent upserts handle boundary edge cases safely.

4. **"Seven strategies to speed up transfer"** — Parallelism (50x), compression (3x), multipart upload (100x per large file), deduplication (30% less data), TCP BBR tuning (10x per stream), small file batching (200x), Transfer Appliance (zero network).

5. **"Phased waves: non-critical first, critical last"** — Wave 1 proves the process on low-risk data. By Wave 3, the team has months of experience. Critical databases migrated with proven, battle-tested methods.

6. **"Checkpoint every 30-60 seconds of work"** — Maximum re-work on failure is one chunk. Across a 6-month migration, we never restart from scratch. Resume from exactly where we stopped.

7. **"GCS computes checksums on ingest"** — Cloud Storage natively provides CRC32C and MD5 on upload. Combined with our source SHA-256, we get triple verification at zero extra cost.

8. **"Bandwidth allocation via QoS"** — 50% for files (bulk), 40% for databases (latency-sensitive CDC), 10% for VMs. Business hours: throttle to 25%. Off-hours: 100%. Prevents source system degradation.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Database Migration System** | Deep dive on DB-specific CDC, schema translation | GCP migration is broader (files + VMs + DBs) |
| **Distributed File System** | Similar chunk-based transfer patterns | DFS is ongoing; migration is one-time with cutover |
| **CDN / Content Distribution** | Similar parallel transfer optimization | CDN distributes to many; migration is one source → one dest |
| **Backup & Disaster Recovery** | Similar data integrity and checksum patterns | Backup is periodic; migration is a one-time full transfer |
| **ETL Pipeline** | Similar bulk data movement | ETL transforms data; migration preserves data exactly |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Source**: [systemdesign.io](https://systemdesign.io/question/create-a-system-to-migrate-large-data-to-google-cloud)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (choose 2-3 based on interviewer interest)
**Related HLD**: `HLD/large-data-migration-gcp-system-HLD.md`
**Related Architecture**: `Architecture/large-data-migration-gcp-architecture-COMPLETE.md`
