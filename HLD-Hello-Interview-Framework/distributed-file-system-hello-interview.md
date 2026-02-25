
# Design a Distributed File System - Hello Interview Framework

> **Question**: Design a hierarchical distributed file system that supports immutable file storage and mutable directory operations, with APIs for file upload/retrieval, directory management, and deletion across hundreds of petabytes for thousands of customers. Think HDFS/GFS or hierarchical layer on top of blob stores.
>
> **Asked at**: Databricks, Meta
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## 1️⃣ Requirements

### Functional (P0)
1. **File Upload/Download**: Upload immutable files (chunked for large files), download with range reads. Files are write-once (no in-place modification).
2. **Directory Operations**: Create, list, rename, delete directories. Hierarchical namespace (paths like `/tenant/data/2025/file.parquet`).
3. **File Metadata**: Size, content hash, timestamps, content type, permissions, chunk locations.
4. **Multi-Tenancy**: Thousands of customers with isolated namespaces, per-tenant quotas.
5. **Deletion & GC**: Soft delete (tombstone) → background garbage collection reclaims unreferenced chunks.
6. **Replication**: 3x replication across racks for durability. Re-replicate on chunk server failure.

### Non-Functional Requirements
| Attribute | Target |
|-----------|--------|
| **Metadata Ops Latency** | < 100ms (mkdir, ls, stat) |
| **Download TTFB** | < 1 second |
| **Upload Throughput** | 10 GB/s aggregate across cluster |
| **Availability** | 99.99% for reads, 99.9% for writes |
| **Durability** | 99.999999999% (11 nines, via 3x replication) |
| **Scale** | Hundreds of PB, billions of files, thousands of tenants |
| **Consistency** | Strong for metadata (linearizable), eventual for chunk replication |

### Capacity Estimation
```
Files: 10B total, 100M new/day
Average file size: 100 MB → total: 1 EB (1000 PB)
Chunk size: 64 MB → 15 chunks/file avg → 150B chunks total
Metadata per file: 500 bytes → 10B × 500B = 5 TB metadata
Directory entries: 5B → 5B × 200B = 1 TB

Chunk servers: 1000 PB / 10 TB per server = 100K servers (with 3x = 33K physical)
Metadata ops: 100K/sec (ls, stat, mkdir)
Data throughput: 10 GB/s aggregate reads, 5 GB/s writes
```

---

## 2️⃣ Core Entities

```java
public class FileMetadata {
    String fileId;              // UUID
    String path;                // "/tenant_123/data/2025/01/file.parquet"
    String tenantId;
    long sizeBytes;
    String contentHash;         // SHA-256 of entire file
    String contentType;
    List<String> chunkIds;      // Ordered list of chunk IDs
    Instant createdAt;
    boolean deleted;            // Soft delete flag
    Instant deletedAt;
}

public class DirectoryEntry {
    String path;                // "/tenant_123/data/2025/"
    String parentPath;
    String tenantId;
    EntryType type;             // FILE or DIRECTORY
    String fileId;              // Non-null if FILE
    Instant createdAt;
    Instant modifiedAt;
}

public class Chunk {
    String chunkId;             // UUID
    int chunkIndex;             // Position within file (0, 1, 2, ...)
    long sizeBytes;             // Up to 64 MB
    String checksum;            // CRC32 per chunk
    List<String> replicaLocations; // [server_01, server_02, server_03]
}

public class ChunkServer {
    String serverId;
    String rackId;              // For rack-aware placement
    long capacityBytes;
    long usedBytes;
    ServerStatus status;        // HEALTHY, DEGRADED, DEAD
    Instant lastHeartbeatAt;
}

public class Tenant {
    String tenantId;
    String name;
    long quotaBytes;            // Max storage allowed
    long usedBytes;
    int maxFiles;
}

public class Namespace {        // Top-level bucket per tenant
    String namespaceId;
    String tenantId;
    String rootPath;            // "/tenant_123/"
}
```

---

## 3️⃣ API Design

### 1. Upload File (Chunked)
```
POST /api/v1/files/upload/init
Request: { "path": "/data/2025/file.parquet", "size": 256000000, "content_type": "application/parquet" }
Response: { "upload_id": "upl_001", "chunk_size": 67108864, "num_chunks": 4,
            "chunk_urls": ["https://chunk-srv-01/upload/upl_001/0", ...] }

PUT /chunks/{upload_id}/{chunk_index}  (direct to chunk server)
Body: <binary chunk data>
Response: { "chunk_id": "chk_001", "checksum": "abc123" }

POST /api/v1/files/upload/complete
Request: { "upload_id": "upl_001", "chunk_checksums": ["abc123", "def456", ...] }
Response: { "file_id": "file_001", "path": "/data/2025/file.parquet", "size": 256000000 }
```

### 2. Download File
```
GET /api/v1/files?path=/data/2025/file.parquet
Response: { "file_id": "file_001", "size": 256000000, "chunks": [
  { "index": 0, "url": "https://chunk-srv-01/chunks/chk_001", "size": 67108864 },
  { "index": 1, "url": "https://chunk-srv-02/chunks/chk_002", "size": 67108864 }, ...
]}
Client downloads chunks in parallel from chunk servers directly.
Range reads: GET /chunks/{chunk_id}?offset=0&length=1048576
```

### 3. Directory Operations
```
POST /api/v1/dirs                         — mkdir
Request: { "path": "/data/2025/01/" }

GET /api/v1/dirs?path=/data/2025/&limit=100&cursor=xxx  — ls (paginated)
Response: { "entries": [{ "name": "01/", "type": "DIR" }, { "name": "file.parquet", "type": "FILE", "size": 256MB }], "next_cursor": "..." }

POST /api/v1/files/move                   — rename/move
Request: { "source": "/data/2025/old.parquet", "dest": "/data/2025/new.parquet" }

DELETE /api/v1/files?path=/data/2025/file.parquet  — delete (soft)
Response: { "deleted": true, "gc_after": "2025-01-17T00:00:00Z" }
```

---

## 4️⃣ Data Flow

### Upload Flow
```
1. Client → Metadata Service: initiate upload (path, size)
2. Metadata Service: validate path, check quota, allocate chunk placements
   → Chunk Master selects 3 servers per chunk (rack-aware)
3. Client → Chunk Servers: upload each chunk directly (parallel)
   → Chunk server writes to local disk + replicates to 2 peers (pipeline)
4. Client → Metadata Service: complete upload (all chunk checksums)
5. Metadata Service: verify checksums, create FileMetadata + DirectoryEntry atomically
6. File is now readable
```

### Download Flow
```
1. Client → Metadata Service: resolve path → FileMetadata → chunk list + locations
2. Client → Chunk Servers: download chunks in parallel (closest replica)
3. Client reassembles file from chunks
Optimization: for small files (< 64 MB), single chunk → single request
```

### Delete + GC Flow
```
1. Client → DELETE /files?path=... → Metadata Service marks file as deleted (tombstone)
2. File immediately invisible in directory listing
3. After 7-day grace period: GC job runs
   → Find chunks referenced only by deleted files (reference counting)
   → Delete chunk data from chunk servers
   → Remove metadata entries
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                  │
│  SDK / CLI / REST API (upload, download, ls, mkdir, rm)         │
└──────────────────────────┬───────────────────────────────────────┘
                           │ REST/gRPC
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Auth, Routing, Quota)             │
└──────────┬──────────────────────────┬────────────────────────────┘
           │ Metadata ops             │ Data ops (redirected)
           ▼                          ▼
┌──────────────────┐      ┌──────────────────────────────────────┐
│ METADATA SERVICE │      │        CHUNK SERVER CLUSTER           │
│                  │      │                                       │
│ • Path resolution│      │  ┌─────────┐ ┌─────────┐ ┌────────┐│
│ • Dir operations │      │  │ Server 1 │ │ Server 2 │ │Server N││
│ • File metadata  │      │  │ 10 TB    │ │ 10 TB    │ │ 10 TB  ││
│ • Upload coord   │      │  │ SSD/HDD  │ │ SSD/HDD  │ │SSD/HDD ││
│ • Quota enforce  │      │  └─────────┘ └─────────┘ └────────┘│
│                  │      │                                       │
│ Pods: 50-100     │      │  ~33K servers (100K with replication)│
│ (stateless)      │      │  Data path: client ↔ chunk server    │
└────────┬─────────┘      └──────────────────────────────────────┘
         │
    ┌────┼──────────────────────┐
    │    │                      │
    ▼    ▼                      ▼
┌──────────┐  ┌──────────┐  ┌──────────────┐
│METADATA  │  │ CHUNK    │  │ CHUNK MASTER  │
│STORE     │  │ INDEX    │  │               │
│          │  │          │  │ • Placement   │
│CockroachDB│ │PostgreSQL│  │ • Replication │
│or TiKV   │  │+ Redis   │  │ • Heartbeats  │
│          │  │          │  │ • Re-replicate│
│• Dirs    │  │• chunk→  │  │   on failure  │
│• Files   │  │  servers │  │               │
│• Tenants │  │          │  │ Pods: 5 (Raft)│
│          │  │          │  └──────────────┘
│ ~6 TB    │  │ ~5 TB    │
│ (Raft)   │  │          │
└──────────┘  └──────────┘
```

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Metadata Service** | Namespace ops, path resolution, upload coordination | Go on K8s | 50-100 pods (stateless) |
| **Metadata Store** | Source of truth for dirs, files, tenants | CockroachDB / TiKV (Raft) | ~6 TB, strongly consistent |
| **Chunk Master** | Chunk placement, heartbeats, re-replication | Go (Raft consensus, 5 nodes) | 5 nodes (leader handles placement) |
| **Chunk Servers** | Store actual file data on local disk | Custom daemon on bare metal | ~33K servers, 10 TB each |
| **Chunk Index** | Map chunk_id → server locations | PostgreSQL + Redis cache | ~5 TB |
| **API Gateway** | Auth, tenant routing, quota checks | Envoy / Kong | Auto-scaled |

---

## 6️⃣ Deep Dives

### DD1: Metadata/Namespace — Separating Metadata from Data

**The Problem**: Billions of files in a hierarchical namespace. `ls /tenant/data/2025/` must be fast. Path resolution (`/a/b/c/d.txt` → file_id) must be efficient.

**Solution**: Store directory tree in a strongly consistent distributed DB (CockroachDB/TiKV). Each directory entry is a row. Path resolution = series of lookups (or single lookup if flat key: `tenant:path` → entry).

```
Namespace sharding: by tenant_id (each tenant's namespace on one shard group)
  → All operations for a tenant are single-shard (fast, consistent)
  → Cross-tenant isolation guaranteed by shard boundary

Path storage: flat key-value (not tree traversal)
  Key: "tenant_123:/data/2025/01/file.parquet"
  Value: { type: FILE, file_id: "file_001", size: 256MB, ... }
  
  ls /data/2025/: range scan on prefix "tenant_123:/data/2025/"
  mkdir: insert key "tenant_123:/data/2025/01/" with type=DIR
  rename: delete old key + insert new key (atomic transaction)
```

### DD2: Chunk Storage & Replication — 64 MB Chunks, 3x, Rack-Aware

**The Problem**: Store hundreds of PB across thousands of servers. Each chunk replicated 3x on different racks for durability.

**Solution**: 64 MB chunks (like GFS). Chunk Master assigns placement using rack-aware strategy.

```
Placement algorithm:
  Replica 1: random server in rack A
  Replica 2: random server in rack B (different rack)
  Replica 3: random server in rack C (third rack)
  → Survives entire rack failure

Pipeline replication (on upload):
  Client → Server A → Server B → Server C (chained)
  Each server writes to disk, then forwards to next
  Throughput: limited by slowest link, but parallelized across chunks

Chunk server heartbeat: every 10 seconds to Chunk Master
  Reports: disk usage, chunk list, health status
  If missed 3 heartbeats (30s) → server declared dead → re-replicate its chunks
```

### DD3: File Upload Protocol — Atomic Commit After All Chunks Written

**The Problem**: Large file upload = many chunks written to different servers. If client crashes mid-upload, we have orphaned chunks. File must only be visible after ALL chunks are committed.

**Solution**: Two-phase upload: allocate → write chunks → commit.

```
Phase 1: INIT — Metadata Service creates upload session (not yet visible in namespace)
Phase 2: WRITE — Client uploads chunks directly to chunk servers (parallel)
Phase 3: COMMIT — Client sends all chunk checksums to Metadata Service
  → Metadata Service verifies: all chunks present, checksums match
  → Atomic write: create FileMetadata + DirectoryEntry in single transaction
  → File now visible in directory listing

Orphan cleanup: uploads not committed within 24 hours → chunks GC'd
```

### DD4: Consistency Model — Strong Metadata, Eventual Data

**The Problem**: After upload commit, file must be immediately visible in directory listing (strong consistency). But chunk replication may still be in progress (eventual).

**Solution**: 
- Metadata: strongly consistent via Raft (CockroachDB). After commit → immediately visible.
- Chunks: write succeeds after 2/3 replicas confirm (quorum). Third replica catches up async.
- Read: client reads from any replica. If chunk unavailable on one server, try another replica.

### DD5: Deletion & Garbage Collection — Reference Counting

**The Problem**: Deleting a file should be instant for the user, but actual chunk data may be shared or large. Must not delete chunks still referenced by other files (dedup).

**Solution**: 
```
Soft delete: mark file as deleted (tombstone), remove from directory listing
Grace period: 7 days (allows undo/recovery)
GC job (hourly):
  1. Find files where deletedAt > 7 days ago
  2. For each chunk in file: decrement reference count
  3. If refcount == 0 → delete chunk data from all chunk servers
  4. Delete file metadata
Reference counting handles dedup (multiple files sharing same chunks)
```

### DD6: Hot Directories — Millions of Children

**The Problem**: A directory like `/tenant/logs/` may have 10M files. `ls` must be paginated and fast.

**Solution**:
```
Paginated listing: GET /dirs?path=/logs/&limit=1000&cursor=last_key
  → Range scan on prefix with limit (CockroachDB range scan: O(K) for K results)
  → Cursor = last returned key for continuation

Caching: hot directory listings cached in Redis (TTL: 30s)
  Invalidated on any write to that directory

Optimization: store child count on directory entry (avoid COUNT(*) queries)
  Updated atomically on file create/delete
```

### DD7: Multi-Tenancy — Isolation, Quotas, Fair Scheduling

**The Problem**: Thousands of tenants share infrastructure. One tenant uploading 10 TB shouldn't starve others.

**Solution**:
```
Namespace isolation: tenant_id prefix on all paths and metadata keys
  → No cross-tenant data access possible (enforced at Metadata Service)

Quota enforcement: before upload, check tenant.usedBytes + file.size <= tenant.quotaBytes
  → Reject with 413 Payload Too Large if exceeded

I/O fairness: per-tenant bandwidth throttling at API Gateway
  → Token bucket per tenant: max 1 GB/s upload, 2 GB/s download
  → Prevents one tenant from consuming all chunk server bandwidth

Chunk server isolation: no dedicated servers per tenant (shared infra)
  → Chunk placement spreads tenant's data across many servers (no hotspots)
```

### DD8: Failure Handling — Chunk Server Death & Re-Replication

**The Problem**: With 33K chunk servers, multiple servers fail daily. Must detect failures quickly and re-replicate under-replicated chunks.

**Solution**:
```
Detection: heartbeat timeout (30 seconds → server declared dead)
  Chunk Master maintains: server → [chunk_ids] mapping

Re-replication:
  1. Chunk Master identifies all chunks on dead server
  2. For each chunk: check replica count (should be 3)
     If only 2 replicas remain → schedule re-replication
  3. Re-replication: read chunk from surviving replica → write to new server
     Rate limit: 100 MB/s per server (don't overwhelm healthy servers)
  4. Priority: chunks with fewer replicas first (1 replica = urgent)

Metadata Service HA: 5-node Raft cluster → survives 2 node failures
  Leader handles all writes; followers handle reads
  Leader election: < 5 seconds on failure
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Metadata vs data separation** | Metadata in DB, data in chunk servers | Different access patterns; metadata needs strong consistency, data needs throughput |
| **Chunk size** | 64 MB | Balances metadata overhead vs upload granularity; matches GFS/HDFS |
| **Replication** | 3x rack-aware | Survives rack failure; 11 nines durability; acceptable storage overhead |
| **Namespace storage** | Flat key-value in CockroachDB (tenant:path → entry) | Fast prefix scans for ls; single-shard per tenant; strongly consistent |
| **Upload protocol** | Two-phase (init → write chunks → commit) | Atomic visibility; no partial files; orphan cleanup via TTL |
| **Consistency** | Strong metadata (Raft), eventual chunk replication (2/3 quorum write) | Users see files immediately after commit; chunks catch up async |
| **Deletion** | Soft delete + 7-day grace + background GC with reference counting | Instant user experience; safe recovery window; handles dedup |
| **Failure recovery** | Heartbeat + auto re-replication | Automatic; no manual intervention; priority by replica count |

## Interview Talking Points

1. **"Separate metadata from data: metadata in CockroachDB (Raft), data in chunk servers (local disk)"** — Different consistency/throughput requirements. Metadata is small (6 TB) and needs strong consistency. Data is massive (PBs) and needs throughput.

2. **"64 MB chunks with 3x rack-aware replication and pipeline writes"** — Client → A → B → C pipeline. Rack-aware placement survives entire rack failure. 2/3 quorum write for availability.

3. **"Flat namespace: tenant:path → entry in distributed KV store"** — No tree traversal for path resolution. `ls` = prefix range scan. `rename` = atomic delete + insert. Sharded by tenant for isolation.

4. **"Two-phase upload: allocate → write chunks → atomic commit"** — File invisible until all chunks verified. Orphan chunks cleaned after 24h. Checksums verified at commit.

5. **"Soft delete + reference-counted GC"** — User sees instant delete. 7-day grace period for recovery. GC hourly: decrement refcount, delete when zero. Handles chunk dedup.

6. **"Chunk Master detects failures via heartbeats, auto re-replicates under-replicated chunks"** — 30s timeout. Priority queue: 1-replica chunks first. Rate-limited to avoid overwhelming healthy servers.

---

## 🔗 Related Problems
| Problem | Key Difference |
|---------|---------------|
| **GFS / HDFS** | Our design; we add multi-tenancy and REST API |
| **S3 / Blob Storage** | S3 is flat namespace; we add hierarchical directories |
| **Google Drive** | User-facing sync; we're infrastructure-layer storage |
| **CDN** | CDN caches; we're primary storage with replication |

## 🔧 Technology Alternatives
| Component | Chosen | Alternative |
|-----------|--------|------------|
| **Metadata store** | CockroachDB | TiKV / etcd / FoundationDB |
| **Chunk storage** | Custom daemon | Ceph OSD / MinIO |
| **Replication** | Pipeline (GFS-style) | Erasure coding (Reed-Solomon) for cost |
| **Namespace** | Flat KV (prefix scan) | Tree structure in DB (parent_id foreign key) |

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Time**: 45-60 min
**References**: Google File System (GFS), HDFS Architecture, CockroachDB Raft, Chunk Replication Strategies