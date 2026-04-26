# Design Azure Blob Storage (Cloud Storage System) — Hello Interview Framework

> **Question**: Architect a service to store and retrieve blobs (large binary files) globally with high durability (11+ nines) and high throughput — similar to Azure Blob Storage or Amazon S3.
>
> **Asked at**: Microsoft, Amazon, Google
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
1. **Put Object**: Upload blobs from bytes to 5 TB. Support single-part and multi-part uploads for large objects.
2. **Get Object**: Download blobs by key. Support range reads (byte ranges) for partial downloads. Presigned URLs for direct CDN access.
3. **Delete Object**: Delete individual blobs. Soft delete with retention period. Bulk delete.
4. **List Objects**: List blobs in a container with prefix filtering and pagination. Hierarchical namespace support (virtual directories).
5. **Containers/Buckets**: Create named containers to organize blobs. Per-container access policies.
6. **Access Control**: Shared Access Signatures (SAS tokens), RBAC, ACLs. Public/private containers. Anonymous read access for public blobs.
7. **Metadata**: Custom key-value metadata on blobs. System properties (content-type, cache-control, content-encoding).

#### Nice to Have (P1)
- Storage tiers (Hot/Cool/Archive) with lifecycle management
- Blob versioning and snapshots
- Immutable storage (WORM — Write Once Read Many) for compliance
- Change feed (event stream of blob changes)
- Object replication (cross-region, cross-account)
- Server-side encryption (SSE with platform keys or customer-managed keys)
- Content-based addressing (content hash as key)
- Append blobs (optimized for append operations — logs)
- Page blobs (optimized for random read/write — VM disks)

#### Below the Line (Out of Scope)
- Azure Data Lake Storage Gen2 (hierarchical namespace details)
- CDN integration details
- Azure Storage Explorer UI
- Azure File Shares (SMB/NFS protocol)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Storage capacity** | Exabytes (unlimited per account) | Cloud-scale storage |
| **Objects stored** | Trillions | Every service uses blob storage |
| **Durability** | 99.999999999% (11 nines) | Data must never be lost |
| **Availability** | 99.99% (read), 99.9% (write) | RA-GRS reads from secondary |
| **Throughput** | 60 Gbps per storage account | High-bandwidth workloads |
| **Latency** | < 10ms p99 (single blob read, hot tier) | Performance-sensitive apps |
| **Object size** | 1 byte to 5 TB (block blob) | Wide range of use cases |
| **Consistency** | Strong (read-after-write) | No stale reads after successful write |
| **IOPS** | 20,000 IOPS per storage account | High request rate |

### Capacity Estimation

```
Scale:
  Storage accounts: 100M+
  Total data stored: 100+ exabytes
  Objects: 100+ trillion
  
Traffic:
  PUT requests/sec: 5M (global)
  GET requests/sec: 50M (global)
  Throughput: petabits/sec aggregate
  
  Average object size: 1 MB (highly variable: 1 KB → 5 TB)
  
Storage Nodes:
  Each node: 100 TB raw storage (HDD + SSD cache)
  Nodes needed for 100 EB: 1M+ storage nodes
  Replication factor: 3 (LRS) or 6 (GRS)
  Raw storage needed: 300 EB - 600 EB

Metadata:
  100T objects × 500 bytes metadata = 50 PB metadata
  Metadata must be on SSD for performance
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  StorageAccount  │────▶│  Container        │────▶│  Blob              │
│                  │     │  (Bucket)         │     │                    │
│ accountId        │     │ containerId       │     │ blobId             │
│ accountName      │     │ accountId         │     │ containerId        │
│ region           │     │ name              │     │ name (key)         │
│ redundancy       │     │ accessLevel       │     │ type (block/       │
│ (LRS/ZRS/GRS/   │     │ (private/blob/    │     │  append/page)      │
│  RA-GRS)         │     │  container)       │     │ size               │
│ tier (Standard/  │     │ metadata{}        │     │ contentType        │
│  Premium)        │     │ lastModified      │     │ contentMD5         │
│ endpoints[]      │     │ leaseState        │     │ eTag               │
│ encryption       │     │ immutability      │     │ tier (hot/cool/    │
│ networkRules     │     └──────────────────┘     │  archive)          │
└─────────────────┘                                │ metadata{}         │
                                                    │ blocks[]           │
┌─────────────────┐     ┌──────────────────┐     │ createdAt          │
│  Block           │     │  BlobVersion      │     │ lastModified       │
│                  │     │                   │     │ leaseState         │
│ blockId          │     │ versionId         │     │ snapshotTime       │
│ blobId           │     │ blobId            │     └───────────────────┘
│ index            │     │ size              │
│ size             │     │ contentMD5        │     ┌───────────────────┐
│ extentRef        │     │ createdAt         │     │  Extent            │
│ (physical loc)   │     │ isCurrentVersion  │     │  (Physical chunk)  │
└─────────────────┘     └──────────────────┘     │                    │
                                                    │ extentId           │
                                                    │ nodeId             │
                                                    │ diskId             │
                                                    │ offset             │
                                                    │ length             │
                                                    │ replicaNodes[]     │
                                                    │ checksum           │
                                                    └───────────────────┘
```

---

## 3️⃣ API Design

### Put Blob (Small — Single Upload)
```
PUT https://{account}.blob.core.windows.net/{container}/{blobName}
Authorization: SharedKey {account}:{signature}
Content-Type: application/octet-stream
Content-Length: 1048576
x-ms-blob-type: BlockBlob
x-ms-meta-project: "alpha"

<binary data>

Response: 201 Created
ETag: "0x8D72B..."
x-ms-request-id: "req_abc123"
```

### Put Blob (Large — Block Upload)
```
// Step 1: Upload blocks
PUT https://{account}.blob.core.windows.net/{container}/{blob}?comp=block&blockid={base64BlockId}
Content-Length: 4194304
<4MB block data>

// Step 2: Commit block list
PUT https://{account}.blob.core.windows.net/{container}/{blob}?comp=blocklist
{
  "Latest": ["block_0", "block_1", "block_2", ..., "block_N"]
}

Response: 201 Created
ETag: "0x8D72C..."
```

### Get Blob
```
GET https://{account}.blob.core.windows.net/{container}/{blobName}
Authorization: SharedKey {account}:{signature}
Range: bytes=0-1048575              // optional: partial read

Response: 200 OK (or 206 Partial Content)
Content-Length: 1048576
Content-Type: application/pdf
ETag: "0x8D72B..."
x-ms-meta-project: "alpha"

<binary data>
```

### Generate SAS Token (Presigned URL)
```
// Server generates SAS URL:
https://{account}.blob.core.windows.net/{container}/{blob}
  ?sv=2024-11-04
  &st=2025-01-15T10:00:00Z
  &se=2025-01-15T12:00:00Z
  &sr=b                    // resource: blob
  &sp=r                    // permission: read
  &sig={HMAC-SHA256-signature}

// Client can GET this URL without any auth header
// SAS encodes: expiry, permissions, resource scope
```

---

## 4️⃣ Data Flow

### Write Path (Put Blob)

```
Client uploads blob → Front-End Layer (REST endpoint)
        │
        ▼
   Front-End (FE) Node:
        │
        ├── 1. Authenticate request (SharedKey / SAS / Azure AD)
        ├── 2. Validate: container exists, quota not exceeded
        ├── 3. Route to Partition Server for this container
        │
        ▼
   Partition Server (PS):
        │
        ├── 1. Allocate extent (physical storage location)
        │       → Choose extent nodes based on:
        │         - Fault domain diversity (different racks/PDUs)
        │         - Disk space availability
        │         - Network proximity
        ├── 2. Write data to primary extent node
        │       → Primary replicates to 2 secondary extent nodes (chain replication)
        │       → Wait for ALL replicas to acknowledge (synchronous)
        ├── 3. Update metadata index:
        │       → blob name → [block list → extent references]
        │       → Strong consistency: metadata update is atomic
        ├── 4. Return success to client (201 Created)
        │
        └── Data is now durable on 3 replicas across fault domains
```

### Read Path (Get Blob)

```
Client requests blob → Front-End Layer
        │
        ▼
   Front-End (FE) Node:
        │
        ├── 1. Authenticate
        ├── 2. Route to Partition Server
        │
        ▼
   Partition Server (PS):
        │
        ├── 1. Lookup blob metadata: name → block list → extent locations
        ├── 2. For each extent:
        │       → Read from nearest healthy replica
        │       → SSD cache hit? → serve from cache (< 1ms)
        │       → Cache miss? → read from HDD (5-10ms)
        ├── 3. Stream data back to client
        │
        └── For range reads: only fetch relevant extents
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                    STORAGE STAMP (per region)                         │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                   FRONT-END LAYER (FE)                         │  │
│  │                                                               │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │  │
│  │  │  FE-1    │  │  FE-2    │  │  FE-3    │  │  FE-N    │    │  │
│  │  │          │  │          │  │          │  │          │    │  │
│  │  │ REST API │  │ REST API │  │ REST API │  │ REST API │    │  │
│  │  │ Auth     │  │ Auth     │  │ Auth     │  │ Auth     │    │  │
│  │  │ Routing  │  │ Routing  │  │ Routing  │  │ Routing  │    │  │
│  │  │ (stateless)│ │          │  │          │  │          │    │  │
│  │  └─────┬────┘  └─────┬────┘  └─────┬────┘  └─────┬────┘    │  │
│  │        └──────────────┴──────────────┴──────────────┘         │  │
│  └───────────────────────────────┬───────────────────────────────┘  │
│                                  │                                   │
│  ┌───────────────────────────────┴───────────────────────────────┐  │
│  │                 PARTITION LAYER (PS)                            │  │
│  │                                                               │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │  │
│  │  │  PS-1    │  │  PS-2    │  │  PS-3    │  │  PS-N    │    │  │
│  │  │          │  │          │  │          │  │          │    │  │
│  │  │ Metadata │  │ Metadata │  │ Metadata │  │ Metadata │    │  │
│  │  │ Index    │  │ Index    │  │ Index    │  │ Index    │    │  │
│  │  │ (B-tree) │  │ (B-tree) │  │ (B-tree) │  │ (B-tree) │    │  │
│  │  │          │  │          │  │          │  │          │    │  │
│  │  │ Blob→    │  │ Blob→    │  │ Blob→    │  │ Blob→    │    │  │
│  │  │ Extent   │  │ Extent   │  │ Extent   │  │ Extent   │    │  │
│  │  │ mapping  │  │ mapping  │  │ mapping  │  │ mapping  │    │  │
│  │  └─────┬────┘  └─────┬────┘  └─────┬────┘  └─────┬────┘    │  │
│  │        └──────────────┴──────────────┴──────────────┘         │  │
│  └───────────────────────────────┬───────────────────────────────┘  │
│                                  │                                   │
│  ┌───────────────────────────────┴───────────────────────────────┐  │
│  │                   EXTENT LAYER (EN)                             │  │
│  │            (Distributed File System — DFS)                     │  │
│  │                                                               │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │  │
│  │  │  EN-1    │  │  EN-2    │  │  EN-3    │  │  EN-N    │    │  │
│  │  │          │  │          │  │          │  │          │    │  │
│  │  │ HDD x36  │  │ HDD x36  │  │ HDD x36  │  │ HDD x36  │    │  │
│  │  │ SSD x4   │  │ SSD x4   │  │ SSD x4   │  │ SSD x4   │    │  │
│  │  │ (cache)  │  │ (cache)  │  │ (cache)  │  │ (cache)  │    │  │
│  │  │          │  │          │  │          │  │          │    │  │
│  │  │ Extents: │  │ Extents: │  │ Extents: │  │ Extents: │    │  │
│  │  │ sealed   │  │ sealed   │  │ sealed   │  │ sealed   │    │  │
│  │  │ append   │  │ append   │  │ append   │  │ append   │    │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │  │
│  │                                                               │  │
│  │  Replication: chain replication across fault domains          │  │
│  │  EN-1 (primary) → EN-2 (secondary) → EN-3 (tertiary)       │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  STAMP MANAGER                                                 │  │
│  │  - Extent placement decisions                                 │  │
│  │  - Failure detection & re-replication                         │  │
│  │  - Load balancing across extent nodes                         │  │
│  │  - Garbage collection (deleted blob cleanup)                  │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│               LOCATION SERVICE (Global)                              │
│                                                                      │
│  Maps: account name → storage stamp (region + cluster)              │
│  DNS: {account}.blob.core.windows.net → stamp IP                   │
│  Handles: account creation, inter-stamp migration                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Durability — How to Achieve 11 Nines

**Problem**: 11 nines durability means losing less than 1 object per 100 billion per year. With trillions of objects, any data loss is unacceptable. How?

**Solution: Multi-Layer Redundancy + Erasure Coding**

```
Redundancy Options:

LRS (Locally Redundant Storage):
  3 synchronous copies within one datacenter
  Different fault domains (racks, power distribution units)
  Durability: ~11 nines
  Survives: disk failure, rack failure
  Doesn't survive: datacenter disaster

ZRS (Zone-Redundant Storage):
  3 synchronous copies across 3 availability zones
  Each AZ is a separate physical datacenter
  Durability: ~12 nines
  Survives: AZ failure, datacenter fire

GRS (Geo-Redundant Storage):
  3 copies in primary region (LRS) + 3 copies in secondary region (async)
  Secondary is 300+ km away
  Durability: ~16 nines
  Survives: entire region outage

RA-GRS (Read-Access GRS):
  Same as GRS + read access to secondary region
  RPO: < 15 minutes (async replication lag)

Erasure Coding (for cold data):
  Instead of 3 full copies: split data into k fragments + p parity
  Example: 12+4 scheme (12 data + 4 parity)
  → Can lose any 4 fragments and reconstruct data
  → Storage overhead: 1.33x vs 3x for triple replication
  → Used for Cool/Archive tiers (read performance is less critical)

Data Integrity:
  Every extent has CRC-32 checksum
  Verified on every read (detect bit rot)
  Background scrubbing: read every extent monthly, verify checksum
  If corruption detected → restore from replica
  
  Checksum chain: 
    block checksum → extent checksum → disk-level checksum
    Triple verification at every layer
```

### Deep Dive 2: Extent Architecture & Chain Replication

**Problem**: How does the extent layer actually store data and replicate it with strong consistency?

**Solution: Sealed Extents + Chain Replication**

```
Extent = fundamental storage unit (typically 1-3 GB)

Extent Lifecycle:
  1. Extent created (open for appends)
  2. Blobs' blocks are appended to extent
  3. When extent reaches target size → sealed (immutable)
  4. Sealed extents can be compacted, tiered, erasure-coded

Chain Replication for writes:
  Client → FE → PS → EN-primary → EN-secondary → EN-tertiary
                                                          │
                                                   ACK propagates back:
                                         EN-tertiary → EN-secondary → EN-primary → PS → FE → Client

  Write is only acknowledged when ALL 3 replicas confirm.
  This guarantees strong consistency: any subsequent read
  from ANY replica will return the latest data.

Failure Handling:
  EN-2 (secondary) fails during write:
    1. Stamp Manager detects failure (heartbeat timeout 10s)
    2. Seal current extent (no more appends)
    3. Select new EN for re-replication
    4. Copy extent from EN-1 (primary) to EN-4 (new secondary)
    5. Update extent location metadata
    6. Open new extent with new 3-node chain

  Total re-replication time: minutes (background, non-blocking)
  During re-replication: reads served from remaining 2 replicas

Read Path Optimization:
  Primary extent node handles reads by default
  If primary is slow → client retries on secondary (hedged read)
  SSD cache on each extent node: recently written/read extents
```

### Deep Dive 3: Metadata Scalability (Partition Layer)

**Problem**: With trillions of objects, the metadata index must support fast lookups, range scans (list operations), and strong consistency. How to scale the partition layer?

**Solution: Distributed B-Tree with Range Partitioning**

```
Partition Layer Architecture:

Each Partition Server manages a range of the key space:
  PS-1: accounts A-F → containers → blobs
  PS-2: accounts G-M → containers → blobs  
  PS-3: accounts N-T → containers → blobs
  PS-4: accounts U-Z → containers → blobs

Key format (hierarchical):
  {accountName}/{containerName}/{blobName}
  
  This allows:
  - Point lookup: exact blob → O(log N) B-tree search
  - Prefix scan: list blobs in container → range scan on prefix
  - Hierarchical listing: "virtual directories" by prefix

Index Structure (per partition server):
  B-tree on SSD
  Key: full blob path
  Value: {
    blobProperties (size, contentType, eTag, ...),
    blockList: [{blockId, extentRef, offset, length}],
    metadata: {custom key-value pairs}
  }

Partition Splits:
  When a partition grows too large (>X GB or >Y IOPS):
    1. Stamp Manager identifies hot partition
    2. Choose split point (midpoint of key range)
    3. Copy second half to new Partition Server
    4. Atomically update routing table
    5. Old PS serves first half, new PS serves second half
  
  Split is transparent to clients — FE routes based on current partition map

Strong Consistency for Metadata:
  Partition Server writes metadata to a replicated log (Paxos)
  → All metadata changes are durable before ACK
  → Read from any replica returns latest committed state
  
  This guarantees read-after-write consistency:
  PUT blob → GET blob immediately after → always returns new blob
```

### Deep Dive 4: Storage Tiering & Lifecycle Management

**Problem**: Hot tier costs $0.018/GB/month but most data is rarely accessed. How to automatically move data to cheaper tiers?

**Solution: Policy-Based Lifecycle Management with Background Tiering**

```
Storage Tiers:
  Hot:     SSD-cached, 3x replication    → $0.018/GB/month
  Cool:    HDD-only, 3x replication       → $0.010/GB/month  
  Cold:    HDD, 2x replication            → $0.0036/GB/month
  Archive: Erasure coded, offline storage → $0.001/GB/month

Lifecycle Policy:
{
  "rules": [
    {
      "name": "tier-to-cool",
      "filters": { "prefixMatch": ["logs/", "backups/"] },
      "actions": {
        "baseBlob": {
          "tierToCool": { "daysAfterModificationGreaterThan": 30 },
          "tierToArchive": { "daysAfterModificationGreaterThan": 90 },
          "delete": { "daysAfterModificationGreaterThan": 365 }
        }
      }
    }
  ]
}

Tiering Process:
  Background job runs daily per storage account:
  1. Scan blob metadata for blobs matching policy filters
  2. For each matching blob past threshold:
     a. Cool tier: move extents to HDD-only nodes, update metadata
     b. Archive tier: erasure-code extents (12+4), move to cold storage
     c. Delete: mark for garbage collection
  3. Update blob tier in metadata
  
  Note: tiering changes metadata only — data doesn't physically move
  for hot→cool (same extent, just reclassified)
  Archive: data IS physically rewritten (erasure coding)

Rehydration (archive → hot):
  User requests archived blob → 202 Accepted
  Background: reconstruct from erasure-coded fragments
  Standard priority: up to 15 hours
  High priority: < 1 hour
  Rehydrated copy placed in hot tier temporarily
```

### Deep Dive 5: Consistency Model & Read-After-Write Guarantee

**Problem**: In a distributed system with replication, how do we guarantee that after a successful PUT, any subsequent GET returns the new data (strong consistency)?

**Solution: Synchronous Replication + Single Partition Server Authority**

```
Write Path Consistency:
  1. Client PUT → FE → Partition Server (PS)
  2. PS writes blob data to extent layer (chain replication, synchronous)
     → ALL 3 replicas confirmed before PS proceeds
  3. PS updates metadata index (Paxos-replicated log)
     → Metadata committed to majority before ACK
  4. PS returns 201 to FE → FE returns to client
  
  At this point: data is on 3 extent replicas + metadata committed
  Any subsequent read will see the new blob.

Read Path Consistency:
  1. Client GET → FE → Partition Server (PS)
  2. PS looks up metadata in local B-tree (always current — PS is authority)
  3. PS reads data from extent layer (any replica)
  4. Return to client
  
  Since PS is the single authority for its partition:
  - Metadata is always the latest committed version
  - Extent data is the same on all replicas (synchronous replication)
  → Strong consistency guaranteed!

Edge Cases:
  PS failover during write:
    - New PS is elected (Paxos) 
    - Replays committed log → recovers exact state
    - Uncommitted writes are lost → client gets error → must retry
  
  Conditional writes (optimistic concurrency):
    PUT with If-Match: "old-etag"
    → PS checks current eTag → 412 if mismatch → client knows conflict
    
  Listing consistency:
    List operations are consistent within a single partition
    Cross-partition listing may see slightly stale data (eventual)
    → For most use cases this is fine (blob names don't change often)
```
