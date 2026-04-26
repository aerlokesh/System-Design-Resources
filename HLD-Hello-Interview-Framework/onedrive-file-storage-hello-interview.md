# Design OneDrive (Distributed File Storage) — Hello Interview Framework

> **Question**: Design a cloud-based file storage and sync service like OneDrive that handles large file uploads, real-time syncing across devices, version history, and sharing — serving hundreds of millions of users.
>
> **Asked at**: Microsoft, Google, Amazon, Dropbox
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
1. **File Upload/Download**: Upload files up to 250 GB. Resumable uploads for large files. Download files and folders (as ZIP for folders).
2. **Folder Management**: Create/rename/move/delete files and folders. Hierarchical directory structure.
3. **Cross-Device Sync**: Changes on one device sync to all linked devices automatically. Desktop sync client watches local folder.
4. **File Versioning**: Maintain version history (up to 500 versions). Restore previous versions. View version diff metadata.
5. **Sharing & Permissions**: Share files/folders via link (view, edit, no-download). Share with specific users/groups. Organizational sharing policies.
6. **Conflict Resolution**: When two users edit the same file offline, detect conflicts and offer resolution.
7. **Search**: Search files by name, content, and metadata across a user's OneDrive.

#### Nice to Have (P1)
- Real-time co-authoring (Office files — Word, Excel, PowerPoint)
- Recycle bin (30-day soft delete)
- Storage quota management
- Offline access (mark files for offline availability)
- Activity feed (who viewed/edited what)
- Personal Vault (extra-secure folder with 2FA)
- Ransomware detection and recovery
- External sharing with expiration and password protection

#### Below the Line (Out of Scope)
- Office Online editing experience
- SharePoint integration details
- Admin compliance (DLP, sensitivity labels)
- Migration tools from other cloud storage

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Registered users** | 500 million | Microsoft 365 + free tier users |
| **DAU** | 100 million | Active file users |
| **Files stored** | 1 trillion files | Average 2,000 files per user |
| **Storage** | 500 PB total | Average 1 GB per user across tiers |
| **Upload throughput** | Handle 100K concurrent uploads | Peak hours |
| **Sync latency** | < 5 seconds for metadata changes | Near real-time sync |
| **Upload speed** | Near line speed (limited by client network) | Chunked parallel upload |
| **Availability** | 99.99% | Business-critical for M365 |
| **Durability** | 99.999999999% (11 nines) | Zero data loss |
| **Consistency** | Strong consistency for metadata, eventual for sync propagation | Correctness vs speed tradeoff |

### Capacity Estimation

```
Files:
  Total files: 1 trillion
  New files/day: 2 billion (uploads + creates)
  File updates/day: 5 billion (edits, renames, moves)
  Average file size: 500 KB (median), 5 MB (mean — skewed by large files)

Storage:
  Total file content: 500 PB
  Daily new storage: 2B × 5 MB = 10 PB/day (before dedup)
  After dedup + compression: ~3 PB/day net new
  Metadata storage: 1T files × 1 KB metadata = 1 PB

Upload Traffic:
  Uploads/sec: 2B / 86,400 ≈ 23K uploads/sec
  Peak (3x): ~70K uploads/sec
  Bandwidth: 70K × 5 MB / 8 = ~44 GB/s peak upload bandwidth

Sync Events:
  Metadata changes/day: 5B
  Sync events/sec: 5B / 86,400 ≈ 58K events/sec
  Peak: ~200K events/sec
  
API Calls:
  Read QPS (list, download, metadata): ~500K QPS peak
  Write QPS (upload, edit, move): ~200K QPS peak
```

---

## 2️⃣ Core Entities

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│  User        │────▶│  Drive       │────▶│  DriveItem     │
│              │     │               │     │  (File/Folder) │
│ userId       │     │ driveId       │     │                │
│ tenantId     │     │ ownerId       │     │ itemId         │
│ email        │     │ driveType     │     │ driveId        │
│ quota        │     │ (personal/   │     │ parentId       │
│              │     │  business)    │     │ name           │
└─────────────┘     │ quotaUsed    │     │ type (file/    │
                     │ quotaTotal   │     │  folder)       │
                     └──────────────┘     │ size           │
                                           │ mimeType       │
┌─────────────┐                            │ contentHash    │
│  FileVersion │                           │ eTag           │
│              │                           │ createdAt      │
│ versionId    │◀───────────────────────── │ modifiedAt     │
│ itemId       │                           │ createdBy      │
│ size         │                           │ modifiedBy     │
│ modifiedBy   │                           │ shared         │
│ createdAt    │                           │ deleted        │
│ contentHash  │                           └───────────────┘
│ blobRef      │                                   │
└─────────────┘                                    │
                                                    ▼
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│  SharingLink │     │  Permission  │     │  FileChunk     │
│              │     │               │     │                │
│ linkId       │     │ permissionId │     │ chunkId        │
│ itemId       │     │ itemId       │     │ itemId         │
│ type (view/  │     │ grantedTo    │     │ versionId      │
│  edit)       │     │ roles[]      │     │ chunkIndex     │
│ password     │     │ inherited    │     │ blobRef        │
│ expiresAt    │     │ link         │     │ size           │
│ scope        │     └──────────────┘     │ checksum       │
└─────────────┘                            └───────────────┘
```

**Key Relationships:**
- Each **User** has a **Drive** (their OneDrive root)
- A Drive contains **DriveItems** (files and folders) in a tree structure
- Files have **FileVersions** and are stored as **FileChunks** in blob storage
- **Permissions** and **SharingLinks** control access at any level of the tree

---

## 3️⃣ API Design

### Upload File (Chunked — Large Files)
```
// Step 1: Create upload session
POST /api/v1/drives/{driveId}/items/{parentId}:/filename.pptx:/createUploadSession
Authorization: Bearer <token>

Request:
{
  "item": {
    "name": "presentation.pptx",
    "conflictBehavior": "rename"    // rename | replace | fail
  }
}

Response: 200 OK
{
  "uploadUrl": "https://upload.onedrive.com/sessions/abc123",
  "expirationDateTime": "2025-01-16T10:30:00Z",
  "nextExpectedRanges": ["0-"]
}

// Step 2: Upload chunks (4 MB each)
PUT https://upload.onedrive.com/sessions/abc123
Content-Range: bytes 0-4194303/67108864
Content-Length: 4194304

<binary chunk data>

Response: 202 Accepted
{
  "nextExpectedRanges": ["4194304-"],
  "expirationDateTime": "2025-01-16T10:30:00Z"
}

// Final chunk response: 201 Created
{
  "id": "item_789",
  "name": "presentation.pptx",
  "size": 67108864,
  "file": { "hashes": { "sha256Hash": "abc..." } },
  "createdDateTime": "2025-01-15T10:30:00Z"
}
```

### Download File
```
GET /api/v1/drives/{driveId}/items/{itemId}/content
Authorization: Bearer <token>

Response: 302 Found
Location: https://cdn.onedrive.com/files/{blobRef}?token=sas_token&expiry=...
```

### Sync Changes (Delta Query)
```
GET /api/v1/drives/{driveId}/root/delta?token={deltaToken}
Authorization: Bearer <token>

Response: 200 OK
{
  "value": [
    { "id": "item_1", "name": "file.txt", "changeType": "modified", ... },
    { "id": "item_2", "name": "old.doc", "changeType": "deleted", ... },
    { "id": "item_3", "name": "new.pptx", "changeType": "created", ... }
  ],
  "@delta.token": "new_delta_token_xyz",
  "@odata.nextLink": null
}
```

### Share File
```
POST /api/v1/drives/{driveId}/items/{itemId}/createLink
Authorization: Bearer <token>

Request:
{
  "type": "edit",                   // view | edit
  "scope": "organization",         // anonymous | organization | specific
  "password": "SecurePass123",
  "expirationDateTime": "2025-06-01T00:00:00Z"
}

Response: 201 Created
{
  "link": {
    "type": "edit",
    "webUrl": "https://1drv.ms/p/s!abc123",
    "application": { "displayName": "OneDrive" }
  }
}
```

---

## 4️⃣ Data Flow

### File Upload (Large File — Chunked)

```
Client (Desktop Sync Agent)
        │
        ├── 1. Detect local file change (filesystem watcher)
        │
        ├── 2. Compute file hash (SHA-256) and split into 4MB chunks
        │
        ├── 3. POST createUploadSession → API Gateway → Upload Service
        │       │
        │       └── Upload Service creates session in Redis
        │           { sessionId, driveId, itemId, expectedSize, receivedChunks[] }
        │
        ├── 4. For each chunk (parallel, up to 4 concurrent):
        │       PUT chunk → Upload Service
        │       │
        │       ├── Validate chunk hash
        │       ├── Write chunk to Azure Blob Storage (temp container)
        │       ├── Update session progress in Redis
        │       └── Return next expected ranges
        │
        ├── 5. All chunks received:
        │       Upload Service:
        │       ├── Assemble chunk references into file manifest
        │       ├── Dedup check: does this content hash already exist? 
        │       │   Yes → reference existing blob, skip storage
        │       │   No  → commit chunks to permanent blob storage
        │       ├── Create FileVersion record
        │       ├── Update DriveItem metadata (size, hash, modifiedAt)
        │       └── Publish "file.changed" event to Event Grid
        │
        └── 6. Sync event propagates to other devices (see sync flow)
```

### Cross-Device Sync

```
File changed on Device A
        │
        ▼
"file.changed" event published to Azure Event Grid
        │
        ├──▶ Sync Notification Service
        │       │
        │       ├── Lookup: which devices are connected for this user?
        │       │   (WebSocket connection registry in Redis)
        │       │
        │       ├── Online devices → push sync notification via WebSocket
        │       │   { "type": "delta", "driveId": "...", "itemId": "..." }
        │       │
        │       └── Offline devices → will sync on next connection
        │               (using delta token from last sync)
        │
        ▼
Device B receives push notification:
        │
        ├── GET /delta?token={lastDeltaToken}
        │   → Returns list of changed items since last sync
        │
        ├── For each changed item:
        │   ├── Download changed file content
        │   ├── Apply to local filesystem
        │   └── Update local delta token
        │
        └── Conflict? (local changes + remote changes on same file)
            ├── Auto-merge if possible (Office co-authoring)
            └── Create conflict copy: "file (conflict - Device B).txt"
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                     │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐       │
│  │  Desktop  │  │  Mobile   │  │  Web App  │  │  Office   │       │
│  │Sync Client│  │   App     │  │  (React)  │  │  Apps     │       │
│  │(Win/Mac)  │  │(iOS/And)  │  │           │  │(Word/PPT) │       │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘       │
│        └───────────────┴───────────────┴───────────────┘             │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTPS + WebSocket
                             ▼
┌────────────────────────────────────────────────────────────────────┐
│                     EDGE / GATEWAY LAYER                            │
│  ┌────────────────────┐    ┌──────────────────────────┐            │
│  │  Azure Front Door  │    │  API Gateway              │            │
│  │  (CDN + Global LB) │    │  (Auth, Rate Limit,       │            │
│  │                    │    │   Throttle, Route)        │            │
│  └────────┬───────────┘    └────────────┬─────────────┘            │
└───────────┴─────────────────────────────┴──────────────────────────┘
                                          │
        ┌─────────────────────────────────┼──────────────────────────┐
        ▼                                 ▼                          ▼
┌─────────────────┐     ┌──────────────────────┐     ┌──────────────────┐
│ Metadata Service│     │  Upload Service       │     │  Sync Service    │
│ (Stateless)     │     │  (Stateless)          │     │  (Stateless)     │
│                 │     │                       │     │                  │
│ - CRUD items    │     │ - Chunked upload      │     │ - Delta queries  │
│ - Permissions   │     │ - Session mgmt        │     │ - Change feed    │
│ - Versioning    │     │ - Dedup (content hash)│     │ - Push notif.    │
│ - Sharing links │     │ - Resume upload       │     │ - Conflict detect│
│ - Search        │     │ - Virus scan trigger  │     │                  │
└────────┬────────┘     └──────────┬────────────┘     └────────┬─────────┘
         │                         │                            │
         ▼                         ▼                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     EVENT BUS                                        │
│  ┌─────────────────────────────────────────────────────────┐        │
│  │  Azure Event Grid / Service Bus                          │        │
│  │  Topics: file.created, file.modified, file.deleted,      │        │
│  │          file.shared, file.versioned                     │        │
│  └─────────────────────────────────────────────────────────┘        │
└──────┬──────────┬──────────────────┬──────────────┬─────────────────┘
       ▼          ▼                  ▼              ▼
┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────────┐
│Thumbnail │ │ Virus    │ │ Search       │ │ Activity/Audit   │
│Generator │ │ Scanner  │ │ Indexer      │ │ Logger           │
│          │ │          │ │              │ │                  │
│ Generate │ │ Scan new │ │ Index file   │ │ Log all file     │
│ previews │ │ uploads  │ │ metadata +   │ │ operations for   │
│ for imgs,│ │ quarantine│ │ content to  │ │ compliance       │
│ docs,etc │ │ if threat│ │ Elasticsearch│ │                  │
└──────────┘ └──────────┘ └──────────────┘ └──────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                   │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐      │
│  │ Azure SQL /  │  │ Azure Blob   │  │ Azure Redis Cache    │      │
│  │ Cosmos DB    │  │ Storage      │  │                      │      │
│  │              │  │              │  │ - Upload sessions    │      │
│  │ - DriveItems │  │ - File chunks│  │ - Delta tokens       │      │
│  │ - Permissions│  │ - Thumbnails │  │ - Connection registry│      │
│  │ - Versions   │  │ - 11 nines   │  │ - Rate limit counters│      │
│  │ - Shares     │  │   durability │  │ - Dedup cache        │      │
│  │ - Users      │  │ - LRS/ZRS/GRS│  │                      │      │
│  └──────────────┘  └──────────────┘  └──────────────────────┘      │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐                                 │
│  │ Elasticsearch│  │ Azure CDN    │                                 │
│  │              │  │              │                                 │
│  │ File search  │  │ Serve file   │                                 │
│  │ (name +     │  │ downloads    │                                 │
│  │  content)   │  │ from edge    │                                 │
│  └──────────────┘  └──────────────┘                                 │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Scale |
|-----------|---------------|-------|
| **Azure Front Door** | Global LB, CDN for downloads, DDoS protection | Global |
| **API Gateway** | Azure AD auth, rate limiting, request routing | Regional |
| **Metadata Service** | File/folder CRUD, permissions, versioning, shares | ~300 instances |
| **Upload Service** | Chunked upload, session management, dedup, assembly | ~500 instances |
| **Sync Service** | Delta queries, change notification push, conflict detection | ~400 instances |
| **Azure Blob Storage** | File content storage with 11 nines durability | Global, exabyte-scale |
| **Azure SQL / Cosmos DB** | Metadata: items, permissions, versions, shares | Multi-region |
| **Redis Cache** | Sessions, tokens, connection registry, hot metadata | ~50 node cluster |
| **Elasticsearch** | File name + content search | ~50 nodes |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Chunked Upload & Resumable Uploads

**Problem**: Users upload files up to 250 GB over unreliable networks. How do we handle this reliably?

**Solution: Session-Based Chunked Upload Protocol**

```
Upload Protocol:

1. CREATE SESSION
   Client → POST /createUploadSession
   Server → 
     - Generate uploadSessionId (UUID)
     - Store in Redis:
       HSET session:{id} driveId "dr_1" parentId "folder_2" 
         fileName "big-video.mp4" totalSize 268435456 
         receivedBytes 0 chunks "" expiry 604800
     - Return uploadUrl with session ID embedded

2. UPLOAD CHUNKS (4 MB default, configurable 320 KB - 60 MB)
   Client → PUT uploadUrl
     Content-Range: bytes {start}-{end}/{totalSize}
     Content-MD5: {chunk_hash}
   
   Server →
     - Validate Content-Range is in nextExpectedRanges
     - Validate Content-MD5 matches data
     - Write chunk to Azure Blob (temp container):
       Container: upload-staging
       Blob: {sessionId}/chunk_{index}
     - Update Redis session:
       HSET session:{id} receivedBytes {end+1}
       RPUSH session:{id}:chunks "chunk_{index}:{blobRef}:{hash}"
     - Return 202 with nextExpectedRanges

3. FINALIZE (server-side, on last chunk)
   - All chunks received → trigger assembly:
     a. Verify all chunk hashes
     b. Compute full file SHA-256 from chunk hashes (Merkle tree)
     c. Check dedup: does this hash exist in content store?
        YES → reference existing blob, skip copy
        NO  → commit chunks using Azure Blob "Put Block List"
              (blocks already uploaded as individual blobs)
     d. Create DriveItem + FileVersion in metadata DB
     e. Cleanup: delete staging blobs, delete Redis session
     f. Publish "file.created" event

4. RESUME (on failure)
   Client → GET uploadUrl
   Server → Return current state:
     { "nextExpectedRanges": ["33554432-"], "expirationDateTime": "..." }
   Client → Resume from last incomplete chunk

Session expiry: 7 days (configurable)
```

**Parallel Chunk Upload:**
```
Client splits file into N chunks
Uploads 4 chunks in parallel:
  Thread 1: chunks 0, 4, 8, ...
  Thread 2: chunks 1, 5, 9, ...
  Thread 3: chunks 2, 6, 10, ...
  Thread 4: chunks 3, 7, 11, ...

Server tracks received ranges, not sequential order.
nextExpectedRanges can be: ["4194304-8388607", "16777216-"]
(meaning chunks 1 and 3 are missing, 0 and 2 received)
```

---

### Deep Dive 2: Content-Addressable Storage & Deduplication

**Problem**: Many users store the same files (company templates, shared documents, email attachments). How do we avoid storing duplicate content and save petabytes of storage?

**Solution: Block-Level Content-Addressable Dedup**

```
Deduplication Strategy:

1. FILE-LEVEL DEDUP (fast, first pass):
   - Compute SHA-256 of entire file content
   - Check dedup index: HGET dedup:file:{sha256} → blobRef
   - If exists → create new DriveItem pointing to same blob
   - Savings: ~30% for enterprise (common templates, etc.)

2. BLOCK-LEVEL DEDUP (deeper, for large files):
   - Split file into variable-size blocks using content-defined chunking
     (Rabin fingerprint with average 4 MB block size)
   - Each block: SHA-256 hash → check dedup index
   - Store only new blocks
   - File manifest = ordered list of block references
   
   Manifest:
   {
     "fileId": "item_789",
     "versionId": "v_3",
     "blocks": [
       { "hash": "abc123", "blobRef": "blob/abc123", "size": 4194304 },
       { "hash": "def456", "blobRef": "blob/def456", "size": 4194304 },
       { "hash": "abc123", "blobRef": "blob/abc123", "size": 4194304 }, // same block!
       { "hash": "ghi789", "blobRef": "blob/ghi789", "size": 2097152 }
     ],
     "totalSize": 14680064
   }

3. DELTA SYNC (for file updates):
   - User edits a 100 MB PowerPoint, changes 2 slides
   - Content-defined chunking identifies which blocks changed
   - Only upload the changed blocks (~8 MB instead of 100 MB)
   - New version manifest references old unchanged blocks + new blocks
   
   Version N blocks:   [A] [B] [C] [D] [E]
   Version N+1 blocks: [A] [B'] [C] [D] [E']  ← only B' and E' uploaded
```

**Dedup Index (Redis + persistent store):**
```
# Hot dedup cache (Redis)
HSET dedup:block:{sha256} blobRef "blob/abc123" refCount 42 size 4194304

# Cold dedup index (Azure Table Storage / Cosmos DB)
PartitionKey: first 4 chars of hash
RowKey: full hash
Value: { blobRef, refCount, createdAt }

# Garbage collection:
# When refCount reaches 0 (no files reference this block) → mark for deletion
# Background GC job: delete blocks with refCount=0 older than 30 days
```

---

### Deep Dive 3: Sync Protocol & Conflict Resolution

**Problem**: A user has 3 devices (desktop at work, laptop at home, phone). They edit a file on the desktop while the laptop is offline. The laptop comes online and has its own changes. How do we handle this?

**Solution: Delta Sync with Last-Writer-Wins + Conflict Copies**

```
Sync Protocol:

Each device maintains:
  - deltaToken: opaque cursor representing last sync point
  - localChangeLog: list of unsynced local changes

PULL SYNC (device comes online or receives push):
  1. GET /drives/{id}/root/delta?token={deltaToken}
  2. Server returns all changes since that token:
     [
       { itemId: "f1", changeType: "modified", eTag: "v3", ... },
       { itemId: "f2", changeType: "created", ... },
       { itemId: "f3", changeType: "deleted", ... }
     ]
  3. For each change:
     - If no local pending change → apply directly
     - If local pending change on same file → CONFLICT

PUSH SYNC (local change detected):
  1. Desktop sync client detects file change (filesystem watcher)
  2. Compute file hash, check if changed since last known version
  3. Upload changed file with If-Match: {lastKnownETag}
     - 200 OK → success, update local eTag
     - 412 Precondition Failed → server has newer version → conflict

CONFLICT RESOLUTION:
  Strategy 1 — Last-Writer-Wins (default for most files):
    - Server version wins
    - Local version saved as conflict copy:
      "report.docx" → "report (Desktop - conflict).docx"
    - User manually resolves
    
  Strategy 2 — Auto-Merge (Office documents):
    - Office files support OT (Operational Transform) / CRDT
    - Server merges changes at the operation level
    - Both users' changes preserved
    - Works even for offline scenarios (queue operations, replay)
    
  Strategy 3 — Fork (developer mode):
    - Both versions kept
    - User shown diff and chooses
```

**Filesystem Watcher (Desktop Client):**
```
Platform-specific implementations:
  Windows: ReadDirectoryChangesW API
  macOS: FSEvents API  
  Linux: inotify

Watcher detects: CREATE, MODIFY, DELETE, RENAME, MOVE

Debounce: Wait 2s after last change before syncing
  (avoid syncing every keystroke during active editing)

Filter: Ignore temp files (~$*.tmp, .~lock.*, thumbs.db)
```

---

### Deep Dive 4: Sharing & Permission Model

**Problem**: OneDrive supports sharing at file and folder level, with inherited permissions, external sharing, and organizational policies. How to model this efficiently?

**Solution: Hierarchical Permission Model with Effective Permission Calculation**

```
Permission Hierarchy:
  Drive (root) 
    └── Folder A (shared with Team X: Edit)
         ├── Folder B (inherits from A)
         │    ├── File 1 (inherits from B → from A)
         │    └── File 2 (shared with User Y: View — OVERRIDE)
         └── File 3 (inherits from A)

Permission Types:
  - Owner: full control (the drive owner)
  - Edit: read + write + delete (within shared scope)
  - View: read-only
  - No-Download View: view in browser only, no download

Sharing Scopes:
  - Specific people (email addresses)
  - People in your organization (Azure AD tenant)
  - Anyone with the link (anonymous)

Effective Permission Calculation:
  effectivePerm(item) = 
    max(
      directPermissions(item),
      inheritedPermissions(item.parent)
    )
  
  With "stop inheritance" override:
    if item has explicit permissions → use those, ignore parent
    else → inherit from parent

Database Schema (Azure SQL):
  Permissions table:
    permissionId | itemId | grantedToId | grantedToType | role | inherited | linkId
    
  Index: (itemId, grantedToId) → fast permission check
  Index: (grantedToId) → "shared with me" view

Permission Check (on every API call):
  1. Check Redis cache: "perm:{userId}:{itemId}" → role (TTL 5 min)
  2. Cache miss → walk up the tree:
     a. Check direct permissions on item
     b. If none, check parent folder
     c. Repeat until drive root
  3. Cache result
  4. Cache invalidation: on permission change, invalidate item + all children
     → Fan-out invalidation via event: "permission.changed" + {itemId, recursive: true}
```

---

### Deep Dive 5: Storage Tiering & Cost Optimization

**Problem**: With 500 PB of storage, costs are enormous. Most files are rarely accessed after the first week. How do we optimize storage costs?

**Solution: Automated Storage Tiering**

```
Azure Blob Storage Tiers:
  Hot    → Frequently accessed (first 30 days) — $0.018/GB/month
  Cool   → Infrequent access (30-90 days)     — $0.01/GB/month  
  Archive → Rarely accessed (>90 days)          — $0.001/GB/month

Tiering Policy (automated):
  
  File uploaded → Hot tier
       │
  30 days, no access → Lifecycle rule moves to Cool tier
       │
  90 days, no access → Move to Archive tier
       │
  Accessed in Archive → Rehydrate to Hot (takes 1-15 hours)
                        Show user: "This file is being restored..."

Implementation:
  Azure Blob Storage Lifecycle Management Rules:
  {
    "rules": [
      {
        "name": "coolAfter30Days",
        "type": "Lifecycle",
        "definition": {
          "filters": { "blobTypes": ["blockBlob"] },
          "actions": {
            "baseBlob": {
              "tierToCool": { "daysAfterLastAccessTimeGreaterThan": 30 }
            }
          }
        }
      },
      {
        "name": "archiveAfter90Days",
        "definition": {
          "actions": {
            "baseBlob": {
              "tierToArchive": { "daysAfterLastAccessTimeGreaterThan": 90 }
            }
          }
        }
      }
    ]
  }

Versioning Cost Optimization:
  - Keep last 30 days of versions in Hot
  - Older versions → Cool
  - Versions >1 year → Archive
  - Free tier: only last 25 versions kept (auto-delete oldest)

Dedup savings: 30-40% reduction in raw storage
Compression: Additional 20-30% for compressible files (docs, text)
Tiering: 60-70% cost reduction for cold data

Total effective cost: ~$0.005/GB/month blended (vs $0.018 all-hot)
```
