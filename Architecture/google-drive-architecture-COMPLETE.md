# Google Drive System Design - Complete Architecture Diagram

## Full System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              USER LAYER                                      │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │  Web Client  │    │  Desktop App │    │  Mobile App  │                 │
│  │  (Browser)   │    │  (Mac/Win)   │    │  (iOS/And)   │                 │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                 │
└─────────┼──────────────────┼──────────────────┼────────────────────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       CDN LAYER (CloudFront)                                 │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Edge Locations: 400+ globally                                       │  │
│  │  Content: Thumbnails, previews, small files (<10MB)                  │  │
│  │  Performance: <15ms latency, 95% hit rate                            │  │
│  │  Handles: 60% of read traffic (thumbnails/previews)                  │  │
│  │  Smart routing: Nearest edge for downloads                           │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LOAD BALANCER (ALB)                           │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Multi-AZ Deployment | Health Checks | SSL/TLS Termination           │  │
│  │  Geo-routing | Auto-failover | Sticky sessions (sync)                │  │
│  │  WebSocket support | 99.99% availability                             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────┬────────────────────────────────┬───────────────────────────┘
                 │                                │
         ┌───────┴────────┐              ┌────────┴───────┐
         │                │              │                │
         ▼                ▼              ▼                ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│  API Gateway   │  │  API Gateway   │  │  API Gateway   │  │  API Gateway   │
│  Cluster 1     │  │  Cluster 2     │  │  Cluster 3     │  │  Cluster 4     │
│  (US-EAST-1a)  │  │  (US-EAST-1b)  │  │  (US-WEST)     │  │  (EU-WEST)     │
└────────┬───────┘  └────────┬───────┘  └────────┬───────┘  └────────┬───────┘
         │                   │                   │                   │
         └───────────────────┴───────────────────┴───────────────────┘
                                      │
┌─────────────────────────────────────┼─────────────────────────────────────┐
│                        SERVICE LAYER                                       │
│                                                                            │
│     ┌────────────────────────────────┴────────────────────────────┐       │
│     │                                                              │       │
│     ▼                ▼                ▼                ▼           │       │
│ ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │       │
│ │  UPLOAD  │  │ DOWNLOAD │  │   SYNC   │  │  SHARE   │           │       │
│ │  SERVICE │  │  SERVICE │  │  SERVICE │  │  SERVICE │           │       │
│ │          │  │          │  │          │  │          │           │       │
│ │ Handles: │  │ Handles: │  │ Handles: │  │ Handles: │           │       │
│ │ -Chunking│  │ -Assembly│  │ -Delta   │  │ -Perms   │           │       │
│ │ -Dedup   │  │ -Resume  │  │ -Conflict│  │ -Links   │           │       │
│ │ -Encrypt │  │ -Stream  │  │ -Notify  │  │ -Collab  │           │       │
│ │ -Validate│  │ -Decrypt │  │ -Queue   │  │ -Audit   │           │       │
│ └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │       │
│      │             │             │             │                  │       │
│      ▼             ▼             ▼             ▼                  │       │
│ ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐          │       │
│ │ METADATA │  │ PREVIEW  │  │  SEARCH  │  │ VERSION  │          │       │
│ │  SERVICE │  │  SERVICE │  │  SERVICE │  │  SERVICE │          │       │
│ │          │  │          │  │          │  │          │          │       │
│ │ Handles: │  │ Handles: │  │ Handles: │  │ Handles: │          │       │
│ │ -Tree    │  │ -Thumb   │  │ -Index   │  │ -History │          │       │
│ │ -ACL     │  │ -Convert │  │ -Query   │  │ -Restore │          │       │
│ │ -Quota   │  │ -Cache   │  │ -Filter  │  │ -Diff    │          │       │
│ │ -Activity│  │ -Render  │  │ -Rank    │  │ -Purge   │          │       │
│ └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘          │       │
└──────┼──────────────┼──────────────┼──────────────┼───────────────┘       │
       │              │              │              │
       ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MESSAGE QUEUE (Amazon SQS + SNS)                      │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Queues:                                                             │  │
│  │  • file_uploaded (Standard, FIFO for sync)                           │  │
│  │  • file_modified (Standard)                                          │  │
│  │  • file_shared (Standard)                                            │  │
│  │  • sync_request (FIFO)                                               │  │
│  │  • preview_generation (Standard)                                     │  │
│  │  • search_index (Standard)                                           │  │
│  │                                                                       │  │
│  │  SNS Topics:                                                         │  │
│  │  • file_events (Fan-out to multiple consumers)                       │  │
│  │  • sync_notifications (Push to devices)                              │  │
│  │                                                                       │  │
│  │  Configuration:                                                      │  │
│  │  • Message retention: 14 days                                        │  │
│  │  • Dead letter queues enabled                                        │  │
│  │  • Throughput: 100K messages/sec                                     │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             │ Consumed by Worker Fleet
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       WORKER FLEET                                           │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐     │
│  │  SYNC WORKERS    │  │ PREVIEW WORKERS  │  │  INDEXING WORKERS    │     │
│  │                  │  │                  │  │                      │     │
│  │  Count: 500      │  │  Count: 200      │  │  Count: 100          │     │
│  │  Capacity:       │  │  Function:       │  │  Function:           │     │
│  │  50K files/sec   │  │  - Generate      │  │  - Extract metadata  │     │
│  │                  │  │    thumbnails    │  │  - Full-text index   │     │
│  │  Logic:          │  │  - Convert docs  │  │  - Update ES         │     │
│  │  - Calculate     │  │  - Video frames  │  │  - OCR for images    │     │
│  │    delta         │  │  - Cache to CDN  │  │  - Content analysis  │     │
│  │  - Notify        │  │  - Multiple sizes│  │                      │     │
│  │    clients       │  │                  │  │                      │     │
│  │  - Resolve       │  │                  │  │                      │     │
│  │    conflicts     │  │                  │  │                      │     │
│  └────────┬─────────┘  └────────┬─────────┘  └──────────┬───────────┘     │
└───────────┼──────────────────────┼──────────────────────┼──────────────────┘
            │                      │                      │
            │                      │                      │
    ┌───────┴────────┐     ┌───────┴──────┐      ┌──────┴────────┐
    │                │     │              │      │               │
    ▼                ▼     ▼              ▼      ▼               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STORAGE LAYER                                        │
│                                                                              │
│  ┌────────────────────┐  ┌──────────────────┐  ┌──────────────────────┐   │
│  │   AMAZON S3        │  │   POSTGRESQL     │  │   ELASTICSEARCH      │   │
│  │   (File Blocks)    │  │   (Metadata)     │  │   (Search Index)     │   │
│  │                    │  │                  │  │                      │   │
│  │  Bucket: drive-    │  │  Master + 5      │  │  7-node cluster      │   │
│  │  blocks-{region}   │  │  Read Replicas   │  │  Sharded by user_id  │   │
│  │                    │  │                  │  │  Replication: 2x     │   │
│  │  Key Pattern:      │  │  Tables:         │  │                      │   │
│  │  {hash}/chunk_{n}  │  │  - files         │  │  Full-text search    │   │
│  │                    │  │  - folders       │  │  Metadata search     │   │
│  │  Features:         │  │  - permissions   │  │  Content search      │   │
│  │  - Versioning ON   │  │  - shares        │  │                      │   │
│  │  - Encryption      │  │  - versions      │  │  Features:           │   │
│  │  - Lifecycle       │  │  - sync_state    │  │  - Fuzzy matching    │   │
│  │  - Intelligent-    │  │  - activity_log  │  │  - Filters           │   │
│  │    Tiering         │  │                  │  │  - Aggregations      │   │
│  │                    │  │  Sharded by:     │  │  - Suggestions       │   │
│  │  Storage:          │  │  user_id (hash)  │  │                      │   │
│  │  - Standard: 40%   │  │                  │  │  Latency: 30-80ms    │   │
│  │  - IA: 35%         │  │  Features:       │  │  Index size: 15TB    │   │
│  │  - Glacier: 25%    │  │  - JSONB cols    │  │                      │   │
│  │                    │  │  - Partitioning  │  │                      │   │
│  │  Capacity: 10PB    │  │  - Point-in-time │  │                      │   │
│  │  Latency: 50-200ms │  │    recovery      │  │                      │   │
│  │                    │  │                  │  │                      │   │
│  │                    │  │  Latency: 10-30ms│  │                      │   │
│  └────────────────────┘  └──────────────────┘  └──────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           CACHE LAYER                                        │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐     │
│  │  METADATA CACHE  │  │  BLOCK CACHE     │  │  PREVIEW CACHE       │     │
│  │  (Redis Cluster) │  │  (Redis Cluster) │  │  (Redis Cluster)     │     │
│  │                  │  │                  │  │                      │     │
│  │  Nodes: 15       │  │  Nodes: 20       │  │  Nodes: 10           │     │
│  │  Memory: 480GB   │  │  Memory: 640GB   │  │  Memory: 320GB       │     │
│  │                  │  │                  │  │                      │     │
│  │  Key Pattern:    │  │  Key Pattern:    │  │  Key Pattern:        │     │
│  │  file:{file_id}  │  │  block:{hash}    │  │  thumb:{file_id}     │     │
│  │  folder:{id}     │  │                  │  │                      │     │
│  │  tree:{user_id}  │  │  Type: String    │  │  Type: Binary        │     │
│  │                  │  │  (Binary)        │  │  Data: JPEG/PNG      │     │
│  │  Type: Hash      │  │  Data: Encrypted │  │  Sizes: S/M/L        │     │
│  │  Data: Metadata  │  │  chunks          │  │  TTL: 24 hours       │     │
│  │  TTL: 1 hour     │  │  TTL: 4 hours    │  │                      │     │
│  │                  │  │                  │  │  CDN fallback        │     │
│  │  Hit Rate: 92%   │  │  Hit Rate: 75%   │  │  Hit Rate: 88%       │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘     │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  SYNC STATE CACHE (Redis - Separate Cluster)                        │  │
│  │                                                                       │  │
│  │  Nodes: 10 | Memory: 320GB | Purpose: Real-time sync coordination   │  │
│  │                                                                       │  │
│  │  Key Pattern: sync:{user_id}:{device_id}                            │  │
│  │  Type: Sorted Set (version numbers + file_ids)                       │  │
│  │  TTL: None (persistent sync state)                                   │  │
│  │  Features: Pub/Sub for real-time notifications                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Write Path - File Upload (Chunked Upload)

```
┌──────────────────────────────────────────────────────────────────┐
│                     FILE UPLOAD PATH                              │
│              Target Latency: <200ms (metadata)                    │
│              Actual upload time: Depends on file size             │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   1. Client-Side    │
                    │   Preprocessing     │
                    │   - Calculate MD5   │
                    │   - Split to chunks │
                    │   - 4MB per chunk   │
                    │   Time: Local       │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   2. Upload Init    │
                    │   POST /api/v1/     │
                    │   files/upload      │
                    │   - Auth check      │
                    │   - Quota check     │
                    │   - Dedup check     │
                    │   Time: 20ms        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │   3. Deduplication Check    │
                    │   - Query by content hash   │
                    │   - Check in PostgreSQL     │
                    │   If exists:                │
                    │     → Return existing file  │
                    │     → Create pointer only   │
                    │     → Skip upload (instant) │
                    │   If new:                   │
                    │     → Continue to step 4    │
                    │   Time: 15ms                │
                    └──────────┬──────────────────┘
                               │ (If new file)
                               ▼
                    ┌─────────────────────┐
                    │   4. Chunk Upload   │
                    │   PUT /api/v1/      │
                    │   chunks/{chunk_id} │
                    │   Parallel: 5 chunks│
                    │   Time: Per chunk   │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   5. Upload Service │
                    │   - Encrypt chunk   │
                    │   - Compress (gzip) │
                    │   - Calculate hash  │
                    │   Time: 50ms/chunk  │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   6. Store to S3    │
                    │   - Key: hash/      │
                    │     chunk_{n}       │
                    │   - AES-256 encrypt │
                    │   - Multipart API   │
                    │   Time: 100ms/chunk │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   7. All Chunks     │
                    │      Complete?      │
                    │   - Track progress  │
                    │   - Verify hashes   │
                    │   Time: 10ms        │
                    └──────────┬──────────┘
                               │ (Yes)
                               ▼
                    ┌─────────────────────────────┐
                    │   8. Metadata Service       │
                    │   - Create file record      │
                    │   - Update folder tree      │
                    │   - Set permissions         │
                    │   - Create version 1        │
                    │   - Update user quota       │
                    │   Time: 30ms                │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │   9. Write to PostgreSQL    │
                    │   BEGIN TRANSACTION         │
                    │   INSERT INTO files         │
                    │   INSERT INTO versions      │
                    │   UPDATE folders            │
                    │   UPDATE user_quota         │
                    │   COMMIT                    │
                    │   Time: 25ms                │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────┐
                    │   10. Publish Events        │
                    │   - SQS: file_uploaded      │
                    │   - SNS: sync_notifications │
                    │   - No wait for processing  │
                    │   Time: 10ms                │
                    └──────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  11. Return Success │
                    │  - File ID          │
                    │  - Version ID       │
                    │  - 201 Created      │
                    │  Total: 80-120ms    │
                    │  (metadata only)    │
                    └─────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                    ASYNCHRONOUS PATH                              │
│              (Background Processing)                              │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  SQS Queue          │
                    │  "file_uploaded"    │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┬───────────────┐
              │                │                │               │
              ▼                ▼                ▼               ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
     │  Sync        │  │   Preview    │  │  Index   │  │  Virus   │
     │  Worker      │  │   Worker     │  │  Worker  │  │  Scan    │
     └──────┬───────┘  └──────┬───────┘  └────┬─────┘  └────┬─────┘
            │                 │               │             │
            ▼                 ▼               ▼             ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
     │ Notify all   │  │Generate      │  │ElasticS  │  │ClamAV    │
     │ devices      │  │thumbnails    │  │earch     │  │Scan      │
     └──────────────┘  └──────────────┘  └──────────┘  └──────────┘
```

---

## Read Path - File Download

```
┌──────────────────────────────────────────────────────────────────┐
│                  FILE DOWNLOAD PATH                               │
│              Target Latency: <100ms (small files)                 │
│              Large files: Streaming, depends on bandwidth         │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
                ┌─────────────────────┐
                │  1. API Gateway     │
                │  GET /api/v1/files/ │
                │  {file_id}/download │
                │  - Auth check       │
                │  - Permission check │
                │  Time: 10ms         │
                └──────────┬──────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  2. Download Service        │
                │  - Validate access          │
                │  - Check if cached          │
                │  - Determine strategy       │
                │  Time: 5ms                  │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  3. Metadata Cache (Redis)  │
                │  GET file:{file_id}         │
                │  Returns: File metadata     │
                │  - Size, chunks, hash       │
                │  - Storage locations        │
                │  Time: 3ms                  │
                │  Hit Rate: 92%              │
                └──────────┬──────────────────┘
                           │
          ┌────────────────┴────────────────┐
          │                                 │
          ▼                                 ▼
┌───────────────────┐           ┌────────────────────┐
│  4a. Small File   │           │  4b. Large File    │
│  (<10MB)          │           │  (>10MB)           │
│                   │           │                    │
│  Check Block      │           │  Stream from S3    │
│  Cache (Redis)    │           │  - Range requests  │
│  If cached:       │           │  - Chunk by chunk  │
│    Return directly│           │  - Parallel 5      │
│  Time: 5ms        │           │  Time: Variable    │
└─────────┬─────────┘           └──────────┬─────────┘
          │                                │
          │ (Cache miss)                   │
          └────────────────┬───────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  5. Fetch from S3           │
                │  - Get encrypted chunks     │
                │  - Parallel retrieval       │
                │  Time: 50-150ms/chunk       │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  6. Download Service        │
                │  - Decrypt chunks           │
                │  - Decompress               │
                │  - Verify integrity         │
                │  Time: 30ms/chunk           │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  7. Assemble & Cache        │
                │  - Combine chunks           │
                │  - Cache in Redis (small)   │
                │  - Update CDN (if public)   │
                │  Time: 20ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  8. Stream to Client        │
                │  - Content-Type header      │
                │  - Accept-Ranges           │
                │  - ETag for caching        │
                │  Total: 100-200ms (small)  │
                │  Streaming (large)         │
                └─────────────────────────────┘
```

---

## Sync Path - Real-time Synchronization

```
┌──────────────────────────────────────────────────────────────────┐
│                  FILE SYNC PATH                                   │
│              Target Latency: <500ms (delta sync)                  │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
                ┌─────────────────────┐
                │  1. Client Detects  │
                │     File Change     │
                │  - File system      │
                │    watcher          │
                │  - Calculate delta  │
                │  Time: Local        │
                └──────────┬──────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  2. Delta Calculation       │
                │  - Compare with local cache │
                │  - Identify changed blocks  │
                │  - Binary diff algorithm    │
                │  - Only 10-20% upload       │
                │  Time: Local (50-200ms)     │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  3. Sync Service            │
                │  POST /api/v1/sync/delta    │
                │  - Send changed blocks only │
                │  - Include version vector   │
                │  Time: 15ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  4. Conflict Detection      │
                │  - Check version vectors    │
                │  - Compare timestamps       │
                │  - Check concurrent edits   │
                │  If conflict:               │
                │    → Create conflict copy   │
                │    → Notify all clients     │
                │  If no conflict:            │
                │    → Continue to step 5     │
                │  Time: 10ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  5. Merge Delta Changes     │
                │  - Apply to base version    │
                │  - Update version vector    │
                │  - Store in S3              │
                │  Time: 50ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  6. Update Metadata         │
                │  - New version record       │
                │  - Update PostgreSQL        │
                │  - Invalidate caches        │
                │  Time: 20ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  7. Publish Sync Event      │
                │  - Redis Pub/Sub            │
                │  - SNS notification         │
                │  - SQS for workers          │
                │  Time: 10ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  8. Notify All Devices      │
                │  - WebSocket push           │
                │  - Mobile push (FCM/APNS)   │
                │  - Desktop notification     │
                │  Time: 50-200ms             │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  9. Clients Pull Changes    │
                │  - Delta download           │
                │  - Apply locally            │
                │  - Update local cache       │
                │  Total: 200-500ms           │
                └─────────────────────────────┘
```

---

## Sharing Path - Permission Management

```
┌──────────────────────────────────────────────────────────────────┐
│                  FILE SHARING PATH                                │
│              Target Latency: <100ms                               │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
                ┌─────────────────────┐
                │  1. Share Request   │
                │  POST /api/v1/      │
                │  files/{id}/share   │
                │  - Email or user ID │
                │  - Permission type  │
                │  Time: 10ms         │
                └──────────┬──────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  2. Share Service           │
                │  - Validate requester owns  │
                │  - Check sharing limits     │
                │  - Resolve recipient        │
                │  Time: 15ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  3. Generate Share Link     │
                │  - Create unique token      │
                │  - Set expiration (optional)│
                │  - Configure permissions    │
                │  Time: 5ms                  │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  4. Write ACL to PostgreSQL │
                │  INSERT INTO permissions    │
                │  - file_id                  │
                │  - user_id/email            │
                │  - permission_type          │
                │  - share_token              │
                │  - expires_at               │
                │  Time: 20ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  5. Update Cache            │
                │  - Invalidate file metadata │
                │  - Add to permission cache  │
                │  Time: 10ms                 │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────────────┐
                │  6. Send Notification       │
                │  - Email with link          │
                │  - In-app notification      │
                │  - Activity log             │
                │  Time: 20ms (async)         │
                └──────────┬──────────────────┘
                           │
                           ▼
                ┌─────────────────────┐
                │  7. Return Response │
                │  - Share link       │
                │  - Permission ID    │
                │  Total: 80ms        │
                └─────────────────────┘
```

---

## Component Details

### API Gateway Configuration

```
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY CLUSTER                       │
│                                                              │
│  Instance Type: c5.2xlarge (6 per region)                   │
│  Total Instances: 18 across 3 regions                       │
│                                                              │
│  Features:                                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Authentication:                                     │  │
│  │  - OAuth 2.0 / JWT tokens                            │  │
│  │  - Session management                                │  │
│  │  - Device fingerprinting                             │  │
│  │  - Multi-factor auth support                         │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Rate Limiting:                                      │  │
│  │  - Per user: 1000 API calls/minute                   │  │
│  │  - Per user: 100 uploads/hour                        │  │
│  │  - Per user: 10GB bandwidth/hour                     │  │
│  │  - Per IP: 5000 requests/minute                      │  │
│  │  - Implementation: Token bucket + leaky bucket       │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Request Routing:                                    │  │
│  │  - POST /files/upload → Upload Service               │  │
│  │  - GET /files/download → Download Service            │  │
│  │  - POST /sync → Sync Service                         │  │
│  │  - GET /search → Search Service                      │  │
│  │  - POST /share → Share Service                       │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Database Schemas

**PostgreSQL - Files Table**:
```
┌─────────────────────────────────────────────────────────────┐
│                      FILES TABLE                             │
│                                                              │
│  file_id: UUID PRIMARY KEY                                  │
│  user_id: BIGINT NOT NULL (indexed)                         │
│  parent_folder_id: UUID (self-referencing)                  │
│  name: VARCHAR(255) NOT NULL                                │
│  content_hash: VARCHAR(64) (indexed for dedup)              │
│  mime_type: VARCHAR(100)                                    │
│  size_bytes: BIGINT                                         │
│  chunk_count: INTEGER                                       │
│  storage_class: VARCHAR(20) (Standard/IA/Glacier)           │
│  is_folder: BOOLEAN DEFAULT FALSE                           │
│  version_count: INTEGER DEFAULT 1                           │
│  current_version_id: UUID                                   │
│  created_at: TIMESTAMP NOT NULL                             │
│  updated_at: TIMESTAMP NOT NULL                             │
│  deleted_at: TIMESTAMP (soft delete)                        │
│  metadata: JSONB (custom metadata)                          │
│                                                              │
│  Indexes:                                                    │
│  - PRIMARY KEY on file_id                                   │
│  - INDEX on (user_id, parent_folder_id)                     │
│  - INDEX on content_hash (for deduplication)                │
│  - INDEX on created_at, updated_at                          │
│  - GIN INDEX on metadata                                    │
│                                                              │
│  Partitioning:                                               │
│  - Hash partition by user_id                                │
│  - 16 partitions for distribution                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   PERMISSIONS TABLE                          │
│                                                              │
│  permission_id: UUID PRIMARY KEY                            │
│  file_id: UUID REFERENCES files(file_id)                    │
│  user_id: BIGINT (nullable for public links)                │
│  email: VARCHAR(255) (for pending shares)                   │
│  permission_type: ENUM('view','comment','edit','owner')     │
│  share_token: VARCHAR(64) UNIQUE (for public links)         │
│  created_by: BIGINT NOT NULL                                │
│  created_at: TIMESTAMP NOT NULL                             │
│  expires_at: TIMESTAMP (nullable)                           │
│  revoked_at: TIMESTAMP (nullable)                           │
│                                                              │
│  Indexes:                                                    │
│  - PRIMARY KEY on permission_id                             │
│  - INDEX on file_id                                         │
│  - INDEX on user_id                                         │
│  - UNIQUE INDEX on share_token                              │
│  - INDEX on (file_id, user_id)                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    VERSIONS TABLE                            │
│                                                              │
│  version_id: UUID PRIMARY KEY                               │
│  file_id: UUID REFERENCES files(file_id)                    │
│  version_number: INTEGER NOT NULL                           │
│  content_hash: VARCHAR(64) NOT NULL                         │
│  size_bytes: BIGINT NOT NULL                                │
│  chunk_hashes: JSONB (array of chunk hashes)                │
│  storage_locations: JSONB (S3 keys)                         │
│  created_by: BIGINT NOT NULL                                │
│  created_at: TIMESTAMP NOT NULL                             │
│  change_description: TEXT                                   │
│  is_current: BOOLEAN DEFAULT TRUE                           │
│                                                              │
│  Indexes:                                                    │
│  - PRIMARY KEY on version_id                                │
│  - INDEX on (file_id, version_number)                       │
│  - INDEX on created_at                                      │
│  - INDEX on is_current                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   SYNC_STATE TABLE                           │
│                                                              │
│  user_id: BIGINT NOT NULL                                   │
│  device_id: VARCHAR(64) NOT NULL                            │
│  file_id: UUID NOT NULL                                     │
│  version_id: UUID NOT NULL                                  │
│  sync_status: ENUM('synced','pending','conflict')           │
│  last_synced_at: TIMESTAMP                                  │
│  local_path: TEXT                                           │
│  PRIMARY KEY (user_id, device_id, file_id)                  │
│                                                              │
│  Indexes:                                                    │
│  - PRIMARY KEY on (user_id, device_id, file_id)             │
│  - INDEX on user_id                                         │
│  - INDEX on device_id                                       │
│  - INDEX on sync_status                                     │
└─────────────────────────────────────────────────────────────┘
```

---

## Redis Cache Structures

**Metadata Cache Structure**:
```
┌─────────────────────────────────────────────────────────────┐
│                    REDIS METADATA CACHE                      │
│                                                              │
│  Key Pattern: file:{file_id}                                │
│  Data Type: HASH                                            │
│                                                              │
│  Structure:                                                  │
│  file:abc-123 = {                                           │
│    "name": "document.pdf",                                  │
│    "size": 2048000,                                         │
│    "mime_type": "application/pdf",                          │
│    "owner_id": 12345,                                       │
│    "parent_folder_id": "folder-xyz",                        │
│    "content_hash": "sha256:abc...",                         │
│    "chunk_count": 512,                                      │
│    "version_id": "ver-789",                                 │
│    "created_at": "2024-01-01T00:00:00Z",                    │
│    "updated_at": "2024-01-15T10:30:00Z"                     │
│  }                                                          │
│                                                              │
│  Operations:                                                 │
│  - Set metadata: HSET file:{file_id} field value           │
│  - Get all: HGETALL file:{file_id}                         │
│  - Get specific: HGET file:{file_id} name                  │
│  - TTL: EXPIRE file:{file_id} 3600 (1 hour)                │
│                                                              │
│  Memory per file: ~500 bytes                                │
│  Cached files: 50M active files                             │
│  Total memory needed: ~25GB (with overhead: 50GB)           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    REDIS FOLDER TREE CACHE                   │
│                                                              │
│  Key Pattern: tree:{user_id}:{folder_id}                    │
│  Data Type: SORTED SET                                      │
│                                                              │
│  Structure:                                                  │
│  tree:12345:root = {                                        │
│    "file:abc-123": 1640995200,  ← Score = timestamp        │
│    "file:def-456": 1641000000,                              │
│    "folder:xyz-789": 1641005000                             │
│  }                                                          │
│                                                              │
│  Operations:                                                 │
│  - Add item: ZADD tree:{user_id}:{folder_id} {timestamp} {id}│
│  - Get contents: ZREVRANGE tree:{user_id}:{folder_id} 0 -1 │
│  - Remove: ZREM tree:{user_id}:{folder_id} {id}            │
│  - Count: ZCARD tree:{user_id}:{folder_id}                 │
│  - TTL: EXPIRE tree:{user_id}:{folder_id} 1800             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  REDIS PERMISSION CACHE                      │
│                                                              │
│  Key Pattern: perm:{file_id}:{user_id}                      │
│  Data Type: STRING                                          │
│                                                              │
│  Structure:                                                  │
│  perm:abc-123:67890 = "edit"                                │
│  perm:def-456:67890 = "view"                                │
│                                                              │
│  Operations:                                                 │
│  - Check permission: GET perm:{file_id}:{user_id}          │
│  - Set permission: SET perm:{file_id}:{user_id} {type}     │
│  - TTL: EXPIRE perm:{file_id}:{user_id} 1800               │
│                                                              │
│  Special key for owner check:                                │
│  owner:{file_id} = {user_id}                                │
└─────────────────────────────────────────────────────────────┘
```

---

## Deduplication Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DEDUPLICATION WORKFLOW                            │
└─────────────────────────────────────────────────────────────────────┘

                    User Uploads File
                          │
                          ▼
              ┌─────────────────────┐
              │  Calculate Content  │
              │  Hash (SHA-256)     │
              │  Client-side        │
              └──────────┬──────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │  Query PostgreSQL   │
              │  SELECT * FROM files│
              │  WHERE content_hash │
              │  = {hash}           │
              └──────────┬──────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
   ┌─────────┐      ┌─────────┐    ┌──────────┐
   │ No Match│      │ Match   │    │ Multiple │
   │         │      │ Found   │    │ Matches  │
   └────┬────┘      └────┬────┘    └─────┬────┘
        │                │               │
        ▼                ▼               ▼

Case 1: No Match           Case 2: Match Found        Case 3: Multiple Matches
Upload new file            Create reference only       User has copy already
Store in S3                Skip S3 upload              Just update metadata
Create metadata            Create metadata record      No upload needed
                          Point to existing chunks
                          Instant upload!
                          Save storage space

Benefits of Deduplication:
┌─────────────────────────────────────────────────────────┐
│ • Storage savings: 40-60% for typical users             │
│ • Faster uploads: Instant for duplicate files           │
│ • Bandwidth savings: No transfer for duplicates         │
│ • Cost reduction: Less S3 storage, less transfer        │
└─────────────────────────────────────────────────────────┘

Reference Counting:
┌─────────────────────────────────────────────────────────┐
│ content_hash: abc123                                    │
│ reference_count: 5 ← Number of users with this file    │
│                                                         │
│ Deletion logic:                                         │
│ - User deletes: Decrement reference_count               │
│ - If reference_count > 0: Keep file in S3               │
│ - If reference_count = 0: Mark for deletion            │
│   (Grace period: 30 days, then purge from S3)          │
└─────────────────────────────────────────────────────────┘
```

---

## Conflict Resolution Strategy

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CONFLICT RESOLUTION                               │
└─────────────────────────────────────────────────────────────────────┘

Scenario: Two devices edit same file offline

Device A (Laptop)          Server            Device B (Phone)
     │                       │                      │
     │ Edit file.txt         │                      │ Edit file.txt
     │ Save locally          │                      │ Save locally
     │ (Offline)             │                      │ (Offline)
     │                       │                      │
     ▼                       │                      ▼
Version Vector:             │              Version Vector:
{A:5, B:4} → {A:6, B:4}     │              {A:5, B:4} → {A:5, B:5}
     │                       │                      │
     │ Come online           │                      │ Come online
     │                       │                      │
     ├──Sync Request────────>│                      │
     │  {A:6, B:4}           │<────Sync Request─────┤
     │                       │     {A:5, B:5}       │
     │                       │                      │
     │                   CONFLICT!                  │
     │              Both modified                   │
     │              since last sync                 │
     │                       │                      │
     │                       ▼                      │
     │              ┌─────────────────┐             │
     │              │ Create Both     │             │
     │              │ Versions:       │             │
     │              │                 │             │
     │              │ 1. file.txt     │             │
     │              │    (Device A)   │             │
     │              │                 │             │
     │              │ 2. file.txt     │             │
     │              │    (Device B)   │             │
     │              │                 │             │
     │              │ New Vector:     │             │
     │              │ {A:6, B:5}      │             │
     │              └────────┬────────┘             │
     │                       │                      │
     │<──Notify conflict────┤                      │
     │   Both files exist   │─────Notify conflict─>│
     │                       │     Both files exist │
     │                       │                      │
     ▼                       ▼                      ▼
User sees:            Stored in DB:        User sees:
- file.txt            - file.txt (A)       - file.txt
- file (Device A).txt - file (Device B).txt - file (Device B).txt

User Action Required:
1. Review both versions
2. Manually merge or choose one
3. Delete unwanted version
4. System continues normal sync

Last-Write-Wins Alternative (for non-critical files):
┌─────────────────────────────────────────────────────────┐
│ • Compare timestamps                                    │
│ • Keep most recent version                              │
│ • Discard older version                                 │
│ • Notify user of overwrite                              │
│ • Keep deleted version in trash (30 days)               │
└─────────────────────────────────────────────────────────┘
```

---

## Data Flow Diagrams

### Collaborative Editing Flow

```
Step 1: User A Opens Document
┌──────┐
│User A│ GET /api/v1/files/doc-123/edit
└───┬──┘
    │
    ▼
┌──────────────────────────────────────┐
│ Collaboration Service                │
│ 1. Check permissions (edit access)   │
│ 2. Create editing session            │
│ 3. Return WebSocket URL              │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ WebSocket Connection Established     │
│ - Session ID created                 │
│ - User joined notification broadcast │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Redis Pub/Sub Channel                │
│ - Channel: edit:doc-123              │
│ - Subscribers: All editors           │
└──────────────────────────────────────┘


Step 2: User B Joins Same Document
┌──────┐
│User B│ GET /api/v1/files/doc-123/edit
└───┬──┘
    │
    ▼
┌──────────────────────────────────────┐
│ Connect to same Redis channel        │
│ Broadcast: User B joined             │
│ User A sees: "User B is editing"     │
└──────────────────────────────────────┘


Step 3: User A Makes Edit
┌──────┐
│User A│ Types "Hello"
└───┬──┘
    │
    ▼
┌──────────────────────────────────────┐
│ Operational Transform (OT)           │
│ 1. Capture edit operation            │
│ 2. Calculate position/transform      │
│ 3. Create operation object           │
│    {                                 │
│      user: "A",                      │
│      position: 5,                    │
│      insert: "Hello",                │
│      version: 42                     │
│    }                                 │
└───┬──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐
│ Redis Pub/Sub Broadcast              │
│ PUBLISH edit:doc-123 {operation}     │
│ Latency: 5-10ms                      │
└───┬──────────────────────────────────┘
    │
    ├──────────────────┐
    │                  │
    ▼                  ▼
┌────────┐      ┌────────┐
│ User A │      │ User B │
│ Applies│      │ Applies│
│ locally│      │ remotely│
└────────┘      └────────┘
                Shows "Hello"
                instantly!


Step 4: Periodic Persistence
Every 30 seconds or on significant change:
┌──────────────────────────────────────┐
│ Background Worker                    │
│ 1. Collect all operations            │
│ 2. Apply to base document            │
│ 3. Create new version                │
│ 4. Store in S3                       │
│ 5. Update PostgreSQL metadata        │
│ 6. Continue collaborative session    │
└──────────────────────────────────────┘
```

---

## Capacity & Performance Summary

```
┌─────────────────────────────────────────────────────────────┐
│                   SYSTEM CAPACITY                            │
│                                                              │
│  Write Capacity:                                            │
│  ├─ File uploads: 10,000/sec (peak)                        │
│  ├─ Metadata updates: 50,000/sec                            │
│  ├─ Sync operations: 100,000/sec                            │
│  └─ Total writes: 160,000/sec                               │
│                                                              │
│  Read Capacity:                                             │
│  ├─ File downloads: 50,000/sec                              │
│  ├─ Metadata queries: 200,000/sec                           │
│  ├─ Preview requests: 30,000/sec                            │
│  ├─ Search queries: 5,000/sec                               │
│  └─ Total reads: 285,000/sec                                │
│                                                              │
│  Latency Targets:                                           │
│  ├─ Small file upload (<10MB): <2 sec                       │
│  ├─ Large file upload (1GB): ~30 sec (30MB/s)               │
│  ├─ File download: <100ms (metadata), streaming for data    │
│  ├─ Sync delta: <500ms                                      │
│  ├─ Search: <100ms (p95)                                    │
│  ├─ Share file: <80ms                                       │
│  └─ List folder: <50ms                                      │
│                                                              │
│  Storage:                                                    │
│  ├─ Total capacity: 10PB                                    │
│  ├─ Active storage: 6PB                                     │
│  ├─ Cold storage (Glacier): 2.5PB                           │
│  ├─ Deduplication ratio: 1.6:1 (40% savings)                │
│  └─ Average file size: 2.5MB                                │
│                                                              │
│  Users:                                                      │
│  ├─ Total registered: 2 billion                             │
│  ├─ Daily active: 500 million                               │
│  ├─ Concurrent users: 50 million                            │
│  └─ Average storage per user: 5GB                           │
│                                                              │
│  Availability: 99.95% (4.38 hours downtime/year)            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   COST BREAKDOWN                             │
│                                                              │
│  Infrastructure:                           Monthly Cost      │
│  ├─ S3 Storage (10PB):                                      │
│  │   • Standard (4PB): ................... $92,000          │
│  │   • Infrequent Access (3.5PB): ........ $45,000          │
│  │   • Glacier (2.5PB): .................. $2,500           │
│  ├─ S3 Requests (billions): ............... $25,000          │
│  ├─ PostgreSQL Clusters: .................. $15,000          │
│  ├─ Redis Clusters (5 types): ............. $18,000          │
│  ├─ Elasticsearch: ........................ $8,000           │
│  ├─ SQS/SNS: .............................. $6,000           │
│  ├─ Worker Fleet: ......................... $25,000          │
│  ├─ API Gateways: ......................... $8,000           │
│  ├─ Load Balancers: ....................... $2,000           │
│  ├─ CDN (CloudFront): ..................... $45,000          │
│  ├─ Data Transfer: ........................ $60,000          │
│  └─ Monitoring/Logging: ................... $5,000           │
│                                                              │
│  Total: ~$356,000/month                                     │
│  Per user (500M DAU): $0.000712/day                         │
└─────────────────────────────────────────────────────────────┘
```

---

## Regional Distribution

```
┌─────────────────────────────────────────────────────────────────────┐
│                      GLOBAL DEPLOYMENT                               │
└─────────────────────────────────────────────────────────────────────┘

US-EAST Region (Primary)
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (6 instances)     │
│ • SQS/SNS Queues (all topics)           │
│ • PostgreSQL Master                     │
│ • PostgreSQL Read Replicas (5)          │
│ • Redis Clusters (Full, 5 types)        │
│ • S3 Bucket (primary, 4PB)              │
│ • Elasticsearch (7 nodes)               │
│ • Worker Fleet (400 workers)            │
│ • Handles: 40% of traffic               │
└─────────────────────────────────────────┘

US-WEST Region
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (6 instances)     │
│ • PostgreSQL Read Replicas (3)          │
│ • Redis Cache (Read replicas)           │
│ • S3 Bucket (regional, 2PB)             │
│ • Elasticsearch (5 nodes)               │
│ • Worker Fleet (200 workers)            │
│ • Handles: 30% of traffic               │
└─────────────────────────────────────────┘

EU-WEST Region
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (4 instances)     │
│ • PostgreSQL Read Replicas (2)          │
│ • Redis Cache (Read replicas)           │
│ • S3 Bucket (regional, 2.5PB)           │
│ • Elasticsearch (5 nodes)               │
│ • Worker Fleet (150 workers)            │
│ • Handles: 25% of traffic               │
└─────────────────────────────────────────┘

AP-SOUTH Region
┌─────────────────────────────────────────┐
│ • API Gateway Cluster (2 instances)     │
│ • PostgreSQL Read Replicas (1)          │
│ • Redis Cache (Read replicas)           │
│ • S3 Bucket (regional, 1.5PB)           │
│ • Elasticsearch (3 nodes)               │
│ • Worker Fleet (50 workers)             │
│ • Handles: 5% of traffic                │
└─────────────────────────────────────────┘
```

---

## Quick Reference

### API Endpoints

```
┌─────────────────────────────────────────────────────────────┐
│                    API ENDPOINTS                             │
├─────────────────────────────────────────────────────────────┤
│  POST   /api/v1/files/upload                                │
│         Upload new file (chunked)                           │
│         Latency: 100ms | Dedup: 40% instant                 │
│                                                              │
│  GET    /api/v1/files/{id}/download                         │
│         Download file                                       │
│         Latency: 100ms (small), streaming (large)           │
│                                                              │
│  POST   /api/v1/sync/delta                                  │
│         Sync file changes                                   │
│         Latency: 500ms | Delta: 10-20% upload               │
│                                                              │
│  POST   /api/v1/files/{id}/share                            │
│         Share file/folder                                   │
│         Latency: 80ms | Permissions: view/edit              │
│                                                              │
│  GET    /api/v1/folders/{id}/contents                       │
│         List folder contents                                │
│         Latency: 50ms | Cache hit: 92%                      │
│                                                              │
│  GET    /api/v1/search?q={query}                            │
│         Search files and content                            │
│         Latency: 100ms | Elasticsearch                      │
│                                                              │
│  GET    /api/v1/files/{id}/versions                         │
│         Get file version history                            │
│         Latency: 40ms | 30 versions kept                    │
│                                                              │
│  POST   /api/v1/files/{id}/restore?version={ver}            │
│         Restore previous version                            │
│         Latency: 200ms | Creates new version                │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

```
┌─────────────────────────────────────────────────────────────┐
│                  DESIGN DECISIONS                            │
├─────────────────────────────────────────────────────────────┤
│  Decision 1: Chunked Upload/Download                        │
│  └─ Trade: Complexity for reliability and resume            │
│     Enables: Resume, parallel transfer, deduplication       │
│                                                              │
│  Decision 2: Content-Based Deduplication                    │
│  └─ Trade: Hash calculation overhead for storage savings    │
│     Saves: 40-60% storage, instant duplicate uploads        │
│                                                              │
│  Decision 3: Delta Sync                                     │
│  └─ Trade: Algorithm complexity for bandwidth efficiency    │
│     Reduces: 80-90% sync bandwidth                          │
│                                                              │
│  Decision 4: Multiple Storage Tiers                         │
│  └─ Trade: Lifecycle management for cost optimization       │
│     Saves: 70% on cold storage costs                        │
│                                                              │
│  Decision 5: Eventual Consistency for Sync                  │
│  └─ Trade: Consistency for performance and availability     │
│     Acceptable: 1-2 second sync delay                       │
│                                                              │
│  Decision 6: Separate Metadata & Block Storage              │
│  └─ Trade: Two data stores for optimization                 │
│     PostgreSQL: Fast metadata queries                       │
│     S3: Scalable block storage                              │
│                                                              │
│  Decision 7: Client-Side Encryption                         │
│  └─ Trade: Key management complexity for security           │
│     Security: Zero-knowledge capable                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Security Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      SECURITY LAYERS                                 │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ LAYER 1: TRANSPORT SECURITY                                 │
│ • TLS 1.3 for all connections                               │
│ • Certificate pinning for mobile apps                       │
│ • Perfect forward secrecy                                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ LAYER 2: AUTHENTICATION & AUTHORIZATION                     │
│ • OAuth 2.0 / OpenID Connect                                │
│ • JWT tokens (15-min expiry)                                │
│ • Refresh tokens (30 days)                                  │
│ • Multi-factor authentication                               │
│ • Device fingerprinting                                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ LAYER 3: DATA ENCRYPTION                                    │
│ • At-rest: AES-256 (S3 SSE-KMS)                             │
│ • In-transit: TLS 1.3                                       │
│ • Client-side encryption option                             │
│ • Key rotation: Automatic (90 days)                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ LAYER 4: ACCESS CONTROL                                     │
│ • Fine-grained permissions (RBAC)                           │
│ • Share link expiration                                     │
│ • IP whitelisting (enterprise)                              │
│ • Audit logging (all access)                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ LAYER 5: THREAT PROTECTION                                  │
│ • Rate limiting (DDoS protection)                           │
│ • Virus scanning (ClamAV)                                   │
│ • Anomaly detection (ML-based)                              │
│ • Automated threat response                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Monitoring Dashboard Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│                       GRAFANA DASHBOARD                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Upload Latency     │  │   Download Latency   │                │
│  │   p50: 80ms          │  │   p50: 60ms          │                │
│  │   p99: 200ms ✓       │  │   p99: 150ms ✓       │                │
│  │   [Line Graph]       │  │   [Line Graph]       │                │
│  └──────────────────────┘  └──────────────────────┘                │
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Sync Performance   │  │   Cache Hit Rates    │                │
│  │   Delta: 500ms ✓     │  │   Metadata: 92% ✓    │                │
│  │   Full: 2s ✓         │  │   Blocks: 75% ✓      │                │
│  │   [Line Graph]       │  │   [Bar Chart]        │                │
│  └──────────────────────┘  └──────────────────────┘                │
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Storage Metrics    │  │   Error Rates        │                │
│  │   Used: 6PB/10PB     │  │   4xx: 0.1% ✓        │                │
│  │   Dedup: 40% ✓       │  │   5xx: 0.01% ✓       │                │
│  │   [Gauge]            │  │   [Line Graph]       │                │
│  └──────────────────────┘  └──────────────────────┘                │
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Throughput         │  │   Worker Health      │                │
│  │   Uploads: 8K/s      │  │   Sync: 500/500 ✓    │                │
│  │   Downloads: 45K/s   │  │   Preview: 200/200 ✓ │                │
│  │   [Area Chart]       │  │   [Status Grid]      │                │
│  └──────────────────────┘  └──────────────────────┘                │
│                                                                      │
│  ┌──────────────────────┐  ┌──────────────────────┐                │
│  │   Active Users       │  │   Database Perf      │                │
│  │   Concurrent: 45M    │  │   PostgreSQL: 20ms   │                │
│  │   DAU: 480M          │  │   S3 GET: 100ms      │                │
│  │   [Counter]          │  │   [Line Graph]       │                │
│  └──────────────────────┘  └──────────────────────┘                │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Disaster Recovery

```
┌─────────────────────────────────────────────────────────────┐
│                   DISASTER RECOVERY PLAN                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Recovery Time Objective (RTO): 1 hour                      │
│  Recovery Point Objective (RPO): 5 minutes                  │
│                                                              │
│  Backup Strategy:                                            │
│  ├─ PostgreSQL: Continuous backup + PITR                    │
│  │   • Transaction logs: 5-minute intervals                 │
│  │   • Full backup: Daily                                   │
│  │   • Retention: 30 days                                   │
│  │                                                           │
│  ├─ S3 Storage: Cross-region replication                    │
│  │   • Automatic replication to 2+ regions                  │
│  │   • Versioning enabled (30 versions)                     │
│  │   • Glacier backups (90 days)                            │
│  │                                                           │
│  ├─ Redis: AOF + RDB snapshots                              │
│  │   • AOF: Every second                                    │
│  │   • RDB: Every 5 minutes                                 │
│  │   • Cross-AZ replicas                                    │
│  │                                                           │
│  └─ Elasticsearch: Snapshot to S3                           │
│      • Incremental: Hourly                                  │
│      • Full: Daily                                          │
│      • Retention: 7 days                                    │
│                                                              │
│  Failover Procedures:                                        │
│  ├─ Automatic: Database replicas (30 seconds)               │
│  ├─ Automatic: Load balancer health checks (1 minute)       │
│  ├─ Manual: Regional failover (15 minutes)                  │
│  └─ Manual: Full DR activation (1 hour)                     │
│                                                              │
│  Testing:                                                    │
│  ├─ Quarterly: Full DR drill                                │
│  ├─ Monthly: Failover test                                  │
│  └─ Weekly: Backup verification                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Scalability Considerations

```
┌─────────────────────────────────────────────────────────────┐
│                   SCALING STRATEGIES                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Horizontal Scaling:                                         │
│  ├─ API Gateways: Auto-scaling (CPU > 70%)                  │
│  ├─ Worker Fleet: Based on queue depth                      │
│  ├─ Redis: Add nodes to cluster                             │
│  └─ Elasticsearch: Add nodes + rebalance                     │
│                                                              │
│  Vertical Scaling:                                           │
│  ├─ PostgreSQL: Larger instances (read replicas first)      │
│  ├─ Redis: More memory per node                             │
│  └─ Elasticsearch: More memory/CPU                           │
│                                                              │
│  Sharding:                                                   │
│  ├─ PostgreSQL: By user_id (currently 16 shards)            │
│  ├─ Elasticsearch: By user_id (currently 7 shards)          │
│  └─ Redis: Cluster mode (hash slots)                        │
│                                                              │
│  Growth Projections:                                         │
│  ├─ 1 year: 750M DAU (+50%)                                 │
│  ├─ 2 years: 1B DAU (+100%)                                 │
│  └─ 5 years: 2B DAU (+300%)                                 │
│                                                              │
│  Bottleneck Analysis:                                        │
│  ├─ Current: None (40% capacity)                            │
│  ├─ Next bottleneck: PostgreSQL writes (18 months)          │
│  └─ Mitigation: Add write replicas, increase sharding       │
└─────────────────────────────────────────────────────────────┘
```

---

## Comparison: Twitter vs Google Drive

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ARCHITECTURE COMPARISON                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Aspect              │ Twitter           │ Google Drive             │
│  ────────────────────┼───────────────────┼─────────────────────────│
│  Primary Data        │ Tweets (text)     │ Files (binary)           │
│  Storage             │ DynamoDB          │ S3 + PostgreSQL          │
│  Read Pattern        │ Fan-out feeds     │ Direct access            │
│  Write Pattern       │ Async workers     │ Chunked upload           │
│  Consistency         │ Eventual          │ Strong (metadata)        │
│  Latency Target      │ 50ms              │ 100-500ms (sync)         │
│  Main Challenge      │ Celebrity fanout  │ Large file transfer      │
│  Deduplication       │ None              │ Content-based (40%)      │
│  Versioning          │ Immutable tweets  │ 30 versions              │
│  Real-time           │ Feed updates      │ Collaborative editing    │
│  Caching Strategy    │ Feed IDs          │ Metadata + blocks        │
│  Search              │ Elasticsearch     │ Elasticsearch            │
│  CDN Usage           │ 40% traffic       │ 60% traffic              │
│  Cost/User/Day       │ $0.00041          │ $0.000712                │
│  ────────────────────┼───────────────────┼─────────────────────────│
│  Similarity          │ Both use:                                    │
│                      │ • Microservices architecture                 │
│                      │ • Multi-tier caching                         │
│                      │ • Eventual consistency                       │
│                      │ • Message queues (async processing)          │
│                      │ • Elasticsearch (search)                     │
│                      │ • Multi-region deployment                    │
│                                                                      │
│  Key Difference      │ Twitter: Read-heavy with hot data           │
│                      │ Drive: Write-heavy with cold storage        │
└─────────────────────────────────────────────────────────────────────┘
```

---

**Document Version**: 1.0  
**Created**: November 2024  
**System**: Google Drive Architecture - Complete Design  
**Format**: Based on Twitter Architecture Template  
**Scale**: 500M DAU, 10PB Storage, 99.95% Availability
