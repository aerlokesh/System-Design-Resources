# File Synchronization and Transfer System - HELLO Interview Framework

> **Companies**: Microsoft (OneDrive), Google (Drive), Dropbox  
> **Difficulty**: Hard  
> **Pattern**: State Pattern (file lifecycle) + Chain of Responsibility (sync filters/validators)  
> **Time**: 45 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a File Synchronization and Transfer System?

A system that keeps files in sync between a **local client** and a **host server**. When a user modifies a file locally, the change is detected, validated, and uploaded. When a file changes on the server (e.g., another device or collaborator), the change is pulled down. The system must track each file's **state** (pending upload, synced, modified, conflicted) and apply a **pipeline of checks** (size limits, file type validation, conflict detection) before any transfer occurs.

**Real-world**: Microsoft OneDrive, Google Drive Desktop, Dropbox client, rsync, AWS DataSync, Azure File Sync, Syncthing.

### For L63 Microsoft Interview

1. **State Pattern**: File states (SYNCED, MODIFIED_LOCAL, PENDING_UPLOAD, UPLOADING, PENDING_DOWNLOAD, DOWNLOADING, CONFLICTED, ERROR) — each state dictates what operations are valid and how transitions happen
2. **Chain of Responsibility**: Sync filter pipeline — FileSizeValidator → FileTypeFilter → ConflictDetector → CompressionHandler → TransferHandler — each handler decides to process or reject
3. **SyncEngine**: Orchestrates local watcher + server poller, triggers upload/download flows
4. **Conflict resolution**: Last-write-wins vs keep-both vs manual-merge strategies
5. **Chunked transfer**: Large files split into chunks for resumable uploads/downloads
6. **Event-driven**: FileWatcher detects local changes, ServerPoller detects remote changes

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What triggers sync?" → Local file change (watcher) + periodic server poll + manual trigger
- "Conflict handling?" → When same file modified on both sides — configurable strategy (last-write-wins, keep-both, manual)
- "File size limits?" → Configurable max size per file (e.g., 5GB), enforced before transfer
- "Resumable transfers?" → Yes, chunked upload/download with progress tracking
- "What about file types?" → Configurable allow/deny list (e.g., block .exe, .tmp)
- "Multiple clients?" → Server is source of truth, each client syncs independently

### Functional Requirements (P0)
1. **Detect local file changes** (create, modify, delete) via file watcher
2. **Detect remote file changes** via server polling
3. **Upload** modified local files to host server
4. **Download** new/modified remote files to local client
5. **Track file state** through lifecycle: SYNCED → MODIFIED → PENDING_UPLOAD → UPLOADING → SYNCED
6. **Validation pipeline**: File size, file type, conflict detection — applied before any transfer
7. **Conflict detection and resolution** when both local and remote changed

### Functional Requirements (P1)
- Chunked/resumable transfer for large files
- Selective sync (choose which folders to sync)
- Bandwidth throttling

### Non-Functional
- Thread-safe for concurrent file operations
- Idempotent sync operations (retry-safe)
- Ordered state transitions (no illegal jumps)
- O(1) file state lookup by path

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────────────────────────────────────────────────────┐
│                         SyncEngine                                │
│  - localWatcher: FileWatcher                                     │
│  - serverPoller: ServerPoller                                    │
│  - fileRegistry: Map<String, SyncFile>                           │
│  - syncFilterChain: SyncHandler (Chain of Responsibility)        │
│  - conflictStrategy: ConflictResolutionStrategy                  │
│  - triggerSync(path) / syncAll() / resolveConflict(path)         │
└──────────────┬───────────────────────────────────────────────────┘
               │ manages
     ┌─────────┼──────────┬──────────────┐
     ▼         ▼          ▼              ▼
┌──────────┐ ┌────────────────┐ ┌─────────────────────────┐
│ SyncFile │ │  FileState     │ │  SyncHandler (Chain)     │
│          │ │  (interface)   │ │  (abstract)              │
│ - path   │ ├────────────────┤ ├─────────────────────────┤
│ - name   │ │ SyncedState    │ │ FileSizeValidator        │
│ - size   │ │ ModifiedLocal  │ │ FileTypeFilter           │
│ - hash   │ │ PendingUpload  │ │ ConflictDetector         │
│ - state  │ │ UploadingState │ │ CompressionHandler       │
│ - local  │ │ PendingDownload│ │ TransferHandler          │
│   Version│ │ Downloading    │ └─────────────────────────┘
│ - remote │ │ ConflictedState│
│   Version│ │ ErrorState     │ ┌─────────────────────────┐
│ - lastMod│ └────────────────┘ │ ConflictResolution      │
│          │                    │  Strategy (interface)    │
│ - onLocal│                    ├─────────────────────────┤
│   Change │                    │ LastWriteWinsStrategy    │
│ - onRemot│                    │ KeepBothStrategy         │
│   eChange│                    │ ManualResolveStrategy    │
│ - upload │                    └─────────────────────────┘
│ - download│
└──────────┘
```

### Enum: SyncDirection
```
UPLOAD    → Local change needs to go to server
DOWNLOAD  → Remote change needs to come to local
BOTH      → Conflict — both sides changed
```

### Enum: FileOperation
```
CREATE   → New file detected
MODIFY   → Existing file content changed
DELETE   → File removed
RENAME   → File moved/renamed
```

### State Interface: FileState (State Pattern)
```
Each state determines:
  - onLocalChange()  → What happens when local file changes in THIS state
  - onRemoteChange() → What happens when remote file changes in THIS state
  - upload()         → Can we upload? What transition?
  - download()       → Can we download? What transition?
  - getName()        → State name for logging/display
```

### State Transitions
```
                     onLocalChange()
    ┌──────────┐ ─────────────────→ ┌────────────────┐
    │  SYNCED   │                    │ MODIFIED_LOCAL  │
    └──────┬───┘ ←───────────────── └───────┬────────┘
           │      download complete          │ triggerUpload()
           │                                 ▼
           │                        ┌────────────────┐
           │    upload complete      │ PENDING_UPLOAD  │
           │ ◄────────────────────  └───────┬────────┘
           │                                 │ startUpload()
           │                                 ▼
           │                        ┌────────────────┐
           │    upload success       │   UPLOADING     │
           │ ◄────────────────────  └───────┬────────┘
           │                                 │ failure
           │       onRemoteChange()          ▼
           │ ─────────────────────→ ┌────────────────┐
           │                        │PENDING_DOWNLOAD │
           │  download complete     └───────┬────────┘
           │ ◄────────────────────          │ startDownload()
           │                                ▼
           │                        ┌────────────────┐
           │                        │  DOWNLOADING    │
           │ ◄───────────────────── └────────────────┘
           │    download success
           │
           │  local+remote change    ┌────────────────┐
           │ ──────────────────────→ │  CONFLICTED     │
           │  resolved               └────────────────┘
           │ ◄──────────────────────
           │
           │  any failure            ┌────────────────┐
           └────────────────────────→│    ERROR        │
              retry                  └────────────────┘
              ◄──────────────────────
```

### Class: SyncFile
| Attribute | Type | Description |
|-----------|------|-------------|
| path | String | "/docs/report.pdf" (relative sync path) |
| fileName | String | "report.pdf" |
| fileSize | long | Bytes |
| localHash | String | SHA-256 of local file content |
| remoteHash | String | SHA-256 of last known server version |
| state | FileState | Current state object (State Pattern) |
| localVersion | int | Local modification counter |
| remoteVersion | int | Server version counter |
| lastModifiedLocal | long | Local file timestamp |
| lastModifiedRemote | long | Server file timestamp |
| retryCount | int | Failed sync attempts |

### Class: SyncHandler (Chain of Responsibility)
| Attribute | Type | Description |
|-----------|------|-------------|
| nextHandler | SyncHandler | Next in chain (or null = end) |
| handle(SyncRequest) | SyncResult | Process or pass to next |

---

## 3️⃣ API Design

```java
class SyncEngine {
    /** Trigger sync for a specific file path */
    SyncResult triggerSync(String filePath);
    
    /** Sync all tracked files */
    List<SyncResult> syncAll();
    
    /** Register a new file for syncing */
    SyncFile registerFile(String path, long size, String hash);
    
    /** Handle local file change event (from FileWatcher) */
    void onLocalFileChanged(String path, FileOperation operation);
    
    /** Handle remote file change event (from ServerPoller) */
    void onRemoteFileChanged(String path, int newVersion, String newHash);
    
    /** Resolve a conflicted file */
    SyncResult resolveConflict(String path, ConflictResolution resolution);
    
    /** Get current state of a file */
    FileState getFileState(String path);
    
    /** Set conflict resolution strategy */
    void setConflictStrategy(ConflictResolutionStrategy strategy);
    
    /** Get sync status summary */
    SyncStatus getStatus();
}
```

### SyncRequest (passed through the chain)
```java
class SyncRequest {
    SyncFile file;
    SyncDirection direction;    // UPLOAD or DOWNLOAD
    FileOperation operation;    // CREATE, MODIFY, DELETE
    byte[] content;             // File content (or chunked reference)
    Map<String, String> metadata;
}
```

### SyncResult
```java
class SyncResult {
    String filePath;
    boolean success;
    String message;             // "Uploaded successfully" or "Blocked: file too large"
    String blockedByHandler;    // Which handler in chain blocked it (if any)
    FileState newState;
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Local File Modified → Upload (Happy Path)

```
User edits "report.pdf" locally
  │
  ├─ FileWatcher detects change
  │   └─ onLocalFileChanged("/docs/report.pdf", MODIFY)
  │
  ├─ SyncFile state: SYNCED
  │   └─ state.onLocalChange() → transition to MODIFIED_LOCAL
  │       ├─ localHash = computeSHA256(file)
  │       ├─ localVersion++
  │       └─ state = new ModifiedLocalState()
  │
  ├─ triggerSync("/docs/report.pdf")
  │   └─ state.upload() → transition to PENDING_UPLOAD
  │
  ├─ Build SyncRequest(file, UPLOAD, MODIFY)
  │   └─ Pass through Chain of Responsibility:
  │
  │   ┌─ FileSizeValidator ─────────────────────────┐
  │   │  file.size (2MB) <= maxSize (5GB)?  ✅ PASS │
  │   └──────────────┬──────────────────────────────┘
  │                  ▼
  │   ┌─ FileTypeFilter ────────────────────────────┐
  │   │  ".pdf" in allowedTypes?  ✅ PASS           │
  │   └──────────────┬──────────────────────────────┘
  │                  ▼
  │   ┌─ ConflictDetector ──────────────────────────┐
  │   │  remoteVersion == lastKnownRemote?  ✅ PASS │
  │   │  (no server changes since last sync)        │
  │   └──────────────┬──────────────────────────────┘
  │                  ▼
  │   ┌─ CompressionHandler ────────────────────────┐
  │   │  file > 1MB? → compress payload  ✅ DONE    │
  │   └──────────────┬──────────────────────────────┘
  │                  ▼
  │   ┌─ TransferHandler ───────────────────────────┐
  │   │  state → UPLOADING                          │
  │   │  Upload to server → SUCCESS                 │
  │   │  state → SYNCED                             │
  │   │  remoteHash = localHash                     │
  │   │  remoteVersion = server.newVersion          │
  │   └─────────────────────────────────────────────┘
  │
  └─ Return: SyncResult(success=true, "Uploaded successfully")
```

### Scenario 2: Remote File Changed → Download

```
ServerPoller detects "notes.txt" changed on server
  │
  ├─ onRemoteFileChanged("/docs/notes.txt", version=5, hash="abc123")
  │
  ├─ SyncFile state: SYNCED
  │   └─ state.onRemoteChange() → transition to PENDING_DOWNLOAD
  │       ├─ file.remoteVersion = 5 (was 4)
  │       └─ state = new PendingDownloadState()
  │
  ├─ triggerSync("/docs/notes.txt")
  │   └─ state.download() → transition to DOWNLOADING
  │
  ├─ Build SyncRequest(file, DOWNLOAD, MODIFY)
  │   └─ Chain of Responsibility:
  │       FileSizeValidator ✅ → FileTypeFilter ✅ → ConflictDetector ✅
  │       → TransferHandler: Download from server → Write to local
  │
  ├─ state → SYNCED
  │   ├─ localHash = newHash
  │   ├─ localVersion = remoteVersion
  │   └─ lastModifiedLocal = now()
  │
  └─ Return: SyncResult(success=true, "Downloaded successfully")
```

### Scenario 3: Conflict Detection (Both Sides Modified)

```
SyncFile "shared.docx": state = MODIFIED_LOCAL (user edited locally)
  │
  ├─ ServerPoller: onRemoteFileChanged("shared.docx", version=8, hash="xyz")
  │   └─ state.onRemoteChange():
  │       ├─ Currently MODIFIED_LOCAL + remote also changed = CONFLICT!
  │       └─ state = new ConflictedState()
  │
  ├─ ConflictDetector in chain:
  │   ├─ localHash ≠ remoteHash AND localVersion ≠ remoteVersion
  │   ├─ CONFLICT DETECTED → invoke ConflictResolutionStrategy
  │   │
  │   ├─ Strategy: LastWriteWins
  │   │   ├─ lastModifiedLocal > lastModifiedRemote?
  │   │   │   YES → Upload local version (local wins)
  │   │   │   NO  → Download remote version (remote wins)
  │   │   └─ state → SYNCED
  │   │
  │   ├─ Strategy: KeepBoth
  │   │   ├─ Rename local → "shared (local copy).docx"
  │   │   ├─ Download remote as "shared.docx"
  │   │   ├─ Register new file "shared (local copy).docx"
  │   │   └─ Both files → SYNCED
  │   │
  │   └─ Strategy: ManualResolve
  │       ├─ state stays CONFLICTED
  │       ├─ Notify user: "Conflict on shared.docx — choose version"
  │       └─ Wait for resolveConflict(path, choice)
  │
  └─ Return: SyncResult(success=false, "Conflict detected", "ConflictDetector")
```

### Scenario 4: Chain of Responsibility Blocks Transfer

```
User saves "huge-video.mp4" (12GB) locally
  │
  ├─ FileWatcher → onLocalFileChanged("huge-video.mp4", CREATE)
  │   └─ state: SYNCED → MODIFIED_LOCAL → PENDING_UPLOAD
  │
  ├─ Build SyncRequest(file, UPLOAD, CREATE)
  │   └─ Chain of Responsibility:
  │
  │   ┌─ FileSizeValidator ─────────────────────────┐
  │   │  file.size (12GB) <= maxSize (5GB)?  ❌ FAIL │
  │   │  BLOCK! Do NOT pass to next handler.        │
  │   │  state → ERROR                              │
  │   └─────────────────────────────────────────────┘
  │       (FileTypeFilter, ConflictDetector, TransferHandler NEVER called)
  │
  └─ Return: SyncResult(success=false,
  │     "File exceeds max size (12GB > 5GB)", blockedBy="FileSizeValidator")
```

### Scenario 5: Upload Failure + Retry

```
Upload of "data.csv" fails (network error)
  │
  ├─ TransferHandler: Upload attempt → IOException
  │   ├─ state: UPLOADING → ERROR
  │   ├─ retryCount++ (now 1)
  │   └─ Schedule retry with exponential backoff (2^1 = 2 seconds)
  │
  ├─ Retry #1 (after 2s): Upload attempt → IOException
  │   ├─ retryCount++ (now 2)
  │   └─ Schedule retry (2^2 = 4 seconds)
  │
  ├─ Retry #2 (after 4s): Upload attempt → SUCCESS
  │   ├─ state: ERROR → UPLOADING → SYNCED
  │   ├─ retryCount = 0
  │   └─ remoteHash = localHash
  │
  └─ If retryCount > maxRetries (3):
      ├─ state stays ERROR
      ├─ Notify user: "Sync failed for data.csv after 3 retries"
      └─ File enters dead letter queue for manual inspection
```

---

## 5️⃣ Design + Implementation

### State Pattern: File Lifecycle States

```java
// ==================== STATE INTERFACE ====================
interface FileState {
    /** React to a local file change in this state */
    FileState onLocalChange(SyncFile file);
    
    /** React to a remote file change in this state */
    FileState onRemoteChange(SyncFile file);
    
    /** Attempt to upload — may transition or throw */
    FileState upload(SyncFile file);
    
    /** Attempt to download — may transition or throw */
    FileState download(SyncFile file);
    
    /** State name for display/logging */
    String getName();
    
    /** Can the file be modified in this state? */
    boolean isTransferable();
}
```

**Why State Pattern here?** Each file state has fundamentally different behavior:
- **SYNCED**: `onLocalChange()` → MODIFIED_LOCAL, `onRemoteChange()` → PENDING_DOWNLOAD
- **UPLOADING**: `onLocalChange()` → queue change for after upload, `upload()` → IllegalStateException (already uploading!)
- **CONFLICTED**: `upload()` and `download()` both blocked until conflict resolved
- Using enums + switch would scatter this logic across the SyncEngine. State Pattern encapsulates behavior per state.

### Concrete State Implementations

```java
// ==================== SYNCED STATE ====================
// 💬 "The stable state — file is identical on client and server"
class SyncedState implements FileState {
    @Override
    public FileState onLocalChange(SyncFile file) {
        file.setLocalHash(computeHash(file));
        file.incrementLocalVersion();
        System.out.println("  📝 " + file.getFileName() + ": local change detected → MODIFIED_LOCAL");
        return new ModifiedLocalState();
    }
    
    @Override
    public FileState onRemoteChange(SyncFile file) {
        System.out.println("  ☁️ " + file.getFileName() + ": remote change detected → PENDING_DOWNLOAD");
        return new PendingDownloadState();
    }
    
    @Override
    public FileState upload(SyncFile file) {
        throw new IllegalStateException("File is already synced — nothing to upload");
    }
    
    @Override
    public FileState download(SyncFile file) {
        throw new IllegalStateException("File is already synced — nothing to download");
    }
    
    @Override
    public String getName() { return "SYNCED"; }
    
    @Override
    public boolean isTransferable() { return false; }
}

// ==================== MODIFIED LOCAL STATE ====================
// 💬 "User changed the file — needs upload"
class ModifiedLocalState implements FileState {
    @Override
    public FileState onLocalChange(SyncFile file) {
        // Another local change while already modified — update hash, stay in same state
        file.setLocalHash(computeHash(file));
        return this;  // coalesce multiple edits
    }
    
    @Override
    public FileState onRemoteChange(SyncFile file) {
        // Both local AND remote changed → CONFLICT!
        System.out.println("  ⚠️ " + file.getFileName() + ": CONFLICT — both sides modified!");
        return new ConflictedState();
    }
    
    @Override
    public FileState upload(SyncFile file) {
        System.out.println("  ⬆️ " + file.getFileName() + ": queued for upload → PENDING_UPLOAD");
        return new PendingUploadState();
    }
    
    @Override
    public FileState download(SyncFile file) {
        throw new IllegalStateException("Cannot download — local changes would be overwritten");
    }
    
    @Override
    public String getName() { return "MODIFIED_LOCAL"; }
    
    @Override
    public boolean isTransferable() { return true; }
}

// ==================== UPLOADING STATE ====================
// 💬 "Transfer in progress — block concurrent operations"
class UploadingState implements FileState {
    @Override
    public FileState onLocalChange(SyncFile file) {
        // Queue this change — will re-sync after current upload finishes
        file.setDirtyAfterTransfer(true);
        System.out.println("  ⏳ " + file.getFileName() + ": changed during upload — will re-sync after");
        return this;  // stay in UPLOADING
    }
    
    @Override
    public FileState onRemoteChange(SyncFile file) {
        // Server changed while we're uploading — will become conflict after upload
        file.setConflictPending(true);
        return this;
    }
    
    @Override
    public FileState upload(SyncFile file) {
        throw new IllegalStateException("Upload already in progress for " + file.getFileName());
    }
    
    @Override
    public FileState download(SyncFile file) {
        throw new IllegalStateException("Cannot download while uploading " + file.getFileName());
    }
    
    @Override
    public String getName() { return "UPLOADING"; }
    
    @Override
    public boolean isTransferable() { return false; }
}

// ==================== CONFLICTED STATE ====================
// 💬 "Both sides changed — all transfers blocked until resolved"
class ConflictedState implements FileState {
    @Override
    public FileState onLocalChange(SyncFile file) {
        return this;  // still conflicted, local change noted
    }
    
    @Override
    public FileState onRemoteChange(SyncFile file) {
        return this;  // still conflicted
    }
    
    @Override
    public FileState upload(SyncFile file) {
        throw new IllegalStateException("Resolve conflict before uploading " + file.getFileName());
    }
    
    @Override
    public FileState download(SyncFile file) {
        throw new IllegalStateException("Resolve conflict before downloading " + file.getFileName());
    }
    
    @Override
    public String getName() { return "CONFLICTED"; }
    
    @Override
    public boolean isTransferable() { return false; }
}
```

### Chain of Responsibility: Sync Filter Pipeline

```java
// ==================== SYNC HANDLER (CHAIN OF RESPONSIBILITY) ====================
// 💬 PAUSE: "Each handler in the chain validates/transforms the sync request.
//    If a handler rejects → short-circuit, remaining handlers never execute.
//    If a handler passes → forward to next. This is exactly like servlet filters."

abstract class SyncHandler {
    protected SyncHandler nextHandler;
    
    public SyncHandler setNext(SyncHandler next) {
        this.nextHandler = next;
        return next;  // fluent API for chaining
    }
    
    public SyncResult handle(SyncRequest request) {
        SyncResult result = process(request);
        if (!result.isSuccess()) {
            return result;  // SHORT-CIRCUIT: block, don't pass to next
        }
        if (nextHandler != null) {
            return nextHandler.handle(request);
        }
        return result;  // end of chain — all passed
    }
    
    /** Each handler implements its specific validation/transformation logic */
    protected abstract SyncResult process(SyncRequest request);
    
    public abstract String getName();
}

// ==================== HANDLER 1: FILE SIZE VALIDATOR ====================
class FileSizeValidator extends SyncHandler {
    private final long maxFileSizeBytes;  // e.g., 5GB
    
    public FileSizeValidator(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
    
    @Override
    protected SyncResult process(SyncRequest request) {
        if (request.getFile().getFileSize() > maxFileSizeBytes) {
            return SyncResult.blocked(request.getFile().getPath(),
                "File exceeds max size (" + formatSize(request.getFile().getFileSize()) 
                + " > " + formatSize(maxFileSizeBytes) + ")",
                getName());
        }
        System.out.println("    ✅ FileSizeValidator: " + formatSize(request.getFile().getFileSize()) + " OK");
        return SyncResult.passed(request.getFile().getPath());
    }
    
    @Override
    public String getName() { return "FileSizeValidator"; }
}

// ==================== HANDLER 2: FILE TYPE FILTER ====================
class FileTypeFilter extends SyncHandler {
    private final Set<String> blockedExtensions;  // e.g., {".tmp", ".exe", ".lock"}
    
    public FileTypeFilter(Set<String> blockedExtensions) {
        this.blockedExtensions = blockedExtensions;
    }
    
    @Override
    protected SyncResult process(SyncRequest request) {
        String ext = getExtension(request.getFile().getFileName());
        if (blockedExtensions.contains(ext.toLowerCase())) {
            return SyncResult.blocked(request.getFile().getPath(),
                "File type '" + ext + "' is blocked from syncing",
                getName());
        }
        System.out.println("    ✅ FileTypeFilter: '" + ext + "' allowed");
        return SyncResult.passed(request.getFile().getPath());
    }
    
    @Override
    public String getName() { return "FileTypeFilter"; }
}

// ==================== HANDLER 3: CONFLICT DETECTOR ====================
class ConflictDetector extends SyncHandler {
    private final ConflictResolutionStrategy conflictStrategy;
    
    public ConflictDetector(ConflictResolutionStrategy strategy) {
        this.conflictStrategy = strategy;
    }
    
    @Override
    protected SyncResult process(SyncRequest request) {
        SyncFile file = request.getFile();
        
        // Conflict: local and remote both changed since last sync
        if (file.getLocalVersion() != file.getRemoteVersion()
                && !file.getLocalHash().equals(file.getRemoteHash())) {
            System.out.println("    ⚠️ ConflictDetector: CONFLICT on " + file.getFileName());
            return conflictStrategy.resolve(file, request);
        }
        
        System.out.println("    ✅ ConflictDetector: no conflict");
        return SyncResult.passed(file.getPath());
    }
    
    @Override
    public String getName() { return "ConflictDetector"; }
}

// ==================== HANDLER 4: COMPRESSION HANDLER ====================
class CompressionHandler extends SyncHandler {
    private final long compressionThresholdBytes;  // e.g., 1MB
    
    public CompressionHandler(long thresholdBytes) {
        this.compressionThresholdBytes = thresholdBytes;
    }
    
    @Override
    protected SyncResult process(SyncRequest request) {
        if (request.getFile().getFileSize() > compressionThresholdBytes) {
            request.setCompressed(true);
            System.out.println("    🗜️ CompressionHandler: compressing "
                + formatSize(request.getFile().getFileSize()));
        } else {
            System.out.println("    ✅ CompressionHandler: small file, skip compression");
        }
        return SyncResult.passed(request.getFile().getPath());
    }
    
    @Override
    public String getName() { return "CompressionHandler"; }
}

// ==================== HANDLER 5: TRANSFER HANDLER (Terminal) ====================
class TransferHandler extends SyncHandler {
    private final FileTransferService transferService;
    
    public TransferHandler(FileTransferService transferService) {
        this.transferService = transferService;
    }
    
    @Override
    protected SyncResult process(SyncRequest request) {
        try {
            if (request.getDirection() == SyncDirection.UPLOAD) {
                request.getFile().setState(new UploadingState());
                transferService.upload(request);
                request.getFile().syncVersions();
                request.getFile().setState(new SyncedState());
                System.out.println("    ⬆️ TransferHandler: upload SUCCESS");
            } else {
                request.getFile().setState(new DownloadingState());
                transferService.download(request);
                request.getFile().syncVersions();
                request.getFile().setState(new SyncedState());
                System.out.println("    ⬇️ TransferHandler: download SUCCESS");
            }
            return SyncResult.success(request.getFile().getPath(),
                request.getDirection() == SyncDirection.UPLOAD 
                    ? "Uploaded successfully" : "Downloaded successfully");
        } catch (TransferException e) {
            request.getFile().setState(new ErrorState());
            request.getFile().incrementRetryCount();
            return SyncResult.error(request.getFile().getPath(), 
                "Transfer failed: " + e.getMessage(), getName());
        }
    }
    
    @Override
    public String getName() { return "TransferHandler"; }
}
```

### Building the Chain (Fluent API)

```java
// ==================== CHAIN ASSEMBLY ====================
// 💬 "Chain is built once, reused for every sync request.
//    Order matters: validate BEFORE transferring. Fail fast."

class SyncEngine {
    private final SyncHandler syncChain;
    private final Map<String, SyncFile> fileRegistry = new ConcurrentHashMap<>();
    
    public SyncEngine(ConflictResolutionStrategy conflictStrategy,
                      FileTransferService transferService) {
        // Build chain: validate → detect conflicts → compress → transfer
        SyncHandler sizeValidator = new FileSizeValidator(5L * 1024 * 1024 * 1024);  // 5GB
        SyncHandler typeFilter = new FileTypeFilter(Set.of(".tmp", ".lock", ".exe", ".swp"));
        SyncHandler conflictDetector = new ConflictDetector(conflictStrategy);
        SyncHandler compression = new CompressionHandler(1024 * 1024);  // 1MB threshold
        SyncHandler transfer = new TransferHandler(transferService);
        
        // Link chain: size → type → conflict → compress → transfer
        sizeValidator.setNext(typeFilter)
                     .setNext(conflictDetector)
                     .setNext(compression)
                     .setNext(transfer);
        
        this.syncChain = sizeValidator;  // head of chain
    }
    
    public SyncResult triggerSync(String path) {
        SyncFile file = fileRegistry.get(path);
        if (file == null) throw new FileNotFoundException("File not tracked: " + path);
        
        SyncDirection direction = determineDirection(file);
        SyncRequest request = new SyncRequest(file, direction, FileOperation.MODIFY);
        
        return syncChain.handle(request);
    }
    
    public void onLocalFileChanged(String path, FileOperation op) {
        SyncFile file = fileRegistry.get(path);
        if (file == null) {
            file = new SyncFile(path);
            fileRegistry.put(path, file);
        }
        FileState newState = file.getState().onLocalChange(file);
        file.setState(newState);
    }
    
    public void onRemoteFileChanged(String path, int newVersion, String newHash) {
        SyncFile file = fileRegistry.get(path);
        if (file == null) {
            file = new SyncFile(path);
            fileRegistry.put(path, file);
        }
        file.setRemoteVersion(newVersion);
        file.setRemoteHash(newHash);
        FileState newState = file.getState().onRemoteChange(file);
        file.setState(newState);
    }
    
    private SyncDirection determineDirection(SyncFile file) {
        if (file.getState() instanceof ModifiedLocalState 
                || file.getState() instanceof PendingUploadState) {
            return SyncDirection.UPLOAD;
        }
        if (file.getState() instanceof PendingDownloadState) {
            return SyncDirection.DOWNLOAD;
        }
        return SyncDirection.UPLOAD;  // default
    }
}
```

### Conflict Resolution Strategies

```java
// ==================== CONFLICT RESOLUTION STRATEGIES ====================

interface ConflictResolutionStrategy {
    SyncResult resolve(SyncFile file, SyncRequest request);
    String getName();
}

// Strategy 1: Last modification wins
class LastWriteWinsStrategy implements ConflictResolutionStrategy {
    @Override
    public SyncResult resolve(SyncFile file, SyncRequest request) {
        if (file.getLastModifiedLocal() >= file.getLastModifiedRemote()) {
            // Local is newer → proceed with upload
            request.setDirection(SyncDirection.UPLOAD);
            System.out.println("    🏆 LastWriteWins: local is newer → uploading");
            return SyncResult.passed(file.getPath());
        } else {
            // Remote is newer → switch to download
            request.setDirection(SyncDirection.DOWNLOAD);
            System.out.println("    🏆 LastWriteWins: remote is newer → downloading");
            return SyncResult.passed(file.getPath());
        }
    }
    
    @Override
    public String getName() { return "LastWriteWins"; }
}

// Strategy 2: Keep both versions
class KeepBothStrategy implements ConflictResolutionStrategy {
    @Override
    public SyncResult resolve(SyncFile file, SyncRequest request) {
        String conflictName = file.getFileName().replace(".", " (conflicted copy).");
        System.out.println("    📋 KeepBoth: saving local as '" + conflictName + "', downloading remote");
        // Rename local file, download remote version
        // Both versions preserved — user decides later
        return SyncResult.passed(file.getPath());
    }
    
    @Override
    public String getName() { return "KeepBoth"; }
}

// Strategy 3: Manual resolution required
class ManualResolveStrategy implements ConflictResolutionStrategy {
    @Override
    public SyncResult resolve(SyncFile file, SyncRequest request) {
        file.setState(new ConflictedState());
        System.out.println("    🛑 ManualResolve: user must choose version for " + file.getFileName());
        return SyncResult.blocked(file.getPath(), 
            "Conflict detected — manual resolution required", "ConflictDetector");
    }
    
    @Override
    public String getName() { return "ManualResolve"; }
}
```

### Sequence Diagram: Synchronization Sub-Process

```
┌────────┐   ┌───────────┐   ┌──────────┐   ┌─────────────┐   ┌──────────┐   ┌────────┐
│FileWatch│   │ SyncEngine│   │SyncFile   │   │SyncHandler  │   │Transfer  │   │ Host   │
│  er    │   │           │   │(State)    │   │  Chain      │   │ Service  │   │ Server │
└───┬────┘   └─────┬─────┘   └─────┬─────┘   └──────┬──────┘   └────┬─────┘   └───┬────┘
    │              │               │                 │               │              │
    │ fileChanged  │               │                 │               │              │
    │─────────────>│               │                 │               │              │
    │              │               │                 │               │              │
    │              │ onLocalChange │                 │               │              │
    │              │──────────────>│                 │               │              │
    │              │               │                 │               │              │
    │              │  newState     │                 │               │              │
    │              │<──────────────│                 │               │              │
    │              │  (MODIFIED_   │                 │               │              │
    │              │   LOCAL)      │                 │               │              │
    │              │               │                 │               │              │
    │              │ upload()      │                 │               │              │
    │              │──────────────>│                 │               │              │
    │              │               │                 │               │              │
    │              │  PENDING_     │                 │               │              │
    │              │  UPLOAD       │                 │               │              │
    │              │<──────────────│                 │               │              │
    │              │               │                 │               │              │
    │              │ handle(req)   │                 │               │              │
    │              │────────────────────────────────>│               │              │
    │              │               │                 │               │              │
    │              │               │    FileSizeValidator.process()  │              │
    │              │               │                 │──┐            │              │
    │              │               │                 │  │ size OK    │              │
    │              │               │                 │<─┘            │              │
    │              │               │                 │               │              │
    │              │               │    FileTypeFilter.process()     │              │
    │              │               │                 │──┐            │              │
    │              │               │                 │  │ type OK    │              │
    │              │               │                 │<─┘            │              │
    │              │               │                 │               │              │
    │              │               │    ConflictDetector.process()   │              │
    │              │               │                 │──┐            │              │
    │              │               │                 │  │ no conflict│              │
    │              │               │                 │<─┘            │              │
    │              │               │                 │               │              │
    │              │               │    TransferHandler.process()    │              │
    │              │               │                 │  upload(req)  │              │
    │              │               │                 │──────────────>│              │
    │              │               │                 │               │  PUT /file   │
    │              │               │                 │               │─────────────>│
    │              │               │                 │               │              │
    │              │               │                 │               │  200 OK      │
    │              │               │                 │               │<─────────────│
    │              │               │                 │  success      │              │
    │              │               │                 │<──────────────│              │
    │              │               │                 │               │              │
    │              │               │ setState(SYNCED)│               │              │
    │              │               │<────────────────│               │              │
    │              │               │                 │               │              │
    │              │ SyncResult    │                 │               │              │
    │              │ (success)     │                 │               │              │
    │              │<───────────────────────────────│               │              │
    │              │               │                 │               │              │
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: State Pattern vs Enum + Switch

| Approach | Pros | Cons | When to Use |
|----------|------|------|-------------|
| **Enum + Switch** | Simple, easy to read | Scattered logic, open/closed violation | ≤4 states, simple transitions |
| **State Pattern** | Encapsulated behavior, extensible | More classes, indirection | Complex per-state behavior (our case) |

**Why State Pattern here**: Each state has 4 different behaviors (`onLocalChange`, `onRemoteChange`, `upload`, `download`). With 8 states × 4 operations = 32 cases, a switch statement would be unmaintainable. State pattern keeps each state's logic in one class.

```
8 states × 4 operations:
  - Enum switch: ONE massive switch with 32 cases (SRP violation)
  - State pattern: 8 classes, each with 4 focused methods (clean)
```

### Deep Dive 2: Chain of Responsibility vs If-Else Pipeline

| Approach | Extensibility | Testability | Reordering |
|----------|--------------|-------------|------------|
| **If-else cascade** | Modify existing code | Test entire chain | Rewrite the method |
| **Chain of Responsibility** | Add new handler class | Test each handler alone | Reconfigure chain links |

**Real-world parallel**: This is exactly how middleware works in web frameworks:
- **Java Servlet Filters**: AuthFilter → LogFilter → CorsFilter → Servlet
- **Spring Interceptors**: PreHandle chain → Controller → PostHandle chain
- **Express.js Middleware**: auth() → rateLimit() → validate() → handler()

Each handler is independently testable, reorderable, and removable.

### Deep Dive 3: Conflict Resolution Comparison

| Strategy | Data Loss Risk | User Experience | Best For |
|----------|---------------|----------------|----------|
| **Last-Write-Wins** | ⚠️ Overwrites older version | Transparent, automatic | Single-user multi-device |
| **Keep Both** | ✅ None — both preserved | Clutters folders with copies | Shared/team drives |
| **Manual Resolve** | ✅ None — user decides | Interrupts workflow | Critical documents |
| **Three-Way Merge** | ✅ None if mergeable | Complex for binary files | Text files (git-style) |

**Production systems**:
- **Dropbox**: Keep Both (creates "conflicted copy")
- **OneDrive**: Last-Write-Wins for auto, manual for shared
- **Git**: Three-way merge with manual conflict markers
- **Google Docs**: Real-time OT (Operational Transform) — no conflicts possible

### Deep Dive 4: Chunked Transfer for Large Files

```java
class ChunkedTransferHandler extends SyncHandler {
    private static final int CHUNK_SIZE = 4 * 1024 * 1024;  // 4MB chunks
    
    @Override
    protected SyncResult process(SyncRequest request) {
        long fileSize = request.getFile().getFileSize();
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        
        for (int i = 0; i < totalChunks; i++) {
            byte[] chunk = readChunk(request.getFile(), i, CHUNK_SIZE);
            boolean uploaded = uploadChunk(chunk, i, totalChunks);
            
            if (!uploaded) {
                // Resume from this chunk on retry
                request.getFile().setLastSuccessfulChunk(i - 1);
                return SyncResult.error(request.getFile().getPath(),
                    "Upload failed at chunk " + i + "/" + totalChunks, getName());
            }
            
            // Update progress: (i+1)/totalChunks * 100%
            double progress = (double)(i + 1) / totalChunks * 100;
            request.getFile().setSyncProgress(progress);
        }
        
        return SyncResult.success(request.getFile().getPath(), "Uploaded in " + totalChunks + " chunks");
    }
}
```

**Why chunked?**
- **Resumable**: Network failure at chunk 50/100 → resume from chunk 50, not restart
- **Progress tracking**: Show user "50% uploaded"
- **Memory efficient**: Don't load 5GB file into memory — stream 4MB at a time
- **Parallel upload**: Upload chunks 1-3 in parallel (P1 enhancement)

### Deep Dive 5: FileWatcher + ServerPoller Architecture

```
┌──────────────────────────────────────────────────────┐
│                     SyncEngine                        │
│                                                      │
│  ┌─────────────┐              ┌──────────────────┐   │
│  │ FileWatcher  │              │  ServerPoller     │   │
│  │ (local FS)   │              │  (HTTP polling)   │   │
│  │              │              │                   │   │
│  │ WatchService │              │ ScheduledExecutor │   │
│  │ → CREATE     │   triggers   │ → poll every 30s  │   │
│  │ → MODIFY     │─────────────>│ → GET /changes    │   │
│  │ → DELETE     │              │   since=timestamp  │   │
│  └─────────────┘              └──────────────────┘   │
│         │                              │              │
│         └──────────┬───────────────────┘              │
│                    ▼                                  │
│            ┌──────────────┐                           │
│            │  Event Queue  │  ← all changes funnel    │
│            │  (Blocking)   │     through single queue │
│            └──────┬───────┘                           │
│                   ▼                                   │
│            ┌──────────────┐                           │
│            │ SyncProcessor│  ← single-threaded        │
│            │  (Consumer)  │    to avoid race conditions│
│            └──────────────┘                           │
└──────────────────────────────────────────────────────┘
```

**FileWatcher** (Java `WatchService`):
- OS-level file system notifications (inotify on Linux, FSEvents on macOS)
- Debounce: Wait 500ms after last change event before triggering sync (avoid sync on every keystroke)

**ServerPoller**:
- HTTP long-polling or periodic GET `/api/changes?since=<lastSyncTimestamp>`
- In production: WebSocket or Server-Sent Events for real-time push

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| File deleted locally while uploading | Cancel upload, send DELETE to server |
| File deleted remotely while user editing | Download-delete blocked, keep local copy |
| Same file changed by 3 devices simultaneously | Server serializes — first upload wins, others get conflict |
| File renamed locally | Detect as DELETE + CREATE, or use file ID (inode) for rename detection |
| Symbolic links / hard links | Skip by default — configurable in FileTypeFilter |
| Empty file (0 bytes) | Valid sync, no compression needed, skip chunking |
| File locked by another process | Retry with backoff, notify user after max retries |
| Network disconnection mid-transfer | Chunked resume from last successful chunk |
| Circular symbolic link loop | FileWatcher depth limit + visited set |
| Very long file paths (>260 chars on Windows) | Validate in FileSizeValidator handler, reject early |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space | Note |
|-----------|------|-------|------|
| File state lookup | O(1) | — | ConcurrentHashMap by path |
| State transition | O(1) | — | Direct method dispatch (no switch) |
| Chain processing | O(K) | — | K = number of handlers in chain (fixed, ~5) |
| Sync all files | O(N × K) | — | N files, K handlers per file |
| Conflict detection | O(1) | — | Hash comparison |
| Chunked upload | O(F/C) | O(C) | F = file size, C = chunk size, only 1 chunk in memory |
| File registry | — | O(N) | N tracked files |

### Deep Dive 8: Production Enhancements

| Enhancement | Approach | Benefit |
|-------------|----------|---------|
| **Delta sync** | Upload only changed bytes (rsync algorithm, rolling checksum) | 90% bandwidth reduction for small edits |
| **Deduplication** | Content-addressable storage (hash → single copy) | Save storage when multiple users have same file |
| **Bandwidth throttling** | Token bucket rate limiter in TransferHandler | Don't saturate user's network |
| **Selective sync** | User chooses folders → FileWatcher only watches selected paths | Save local disk space |
| **Offline queue** | Queue changes locally → flush when online | Works without internet |
| **Encryption** | Add EncryptionHandler to chain (before TransferHandler) | End-to-end encrypted sync |
| **Webhooks** | Replace ServerPoller with server push notifications | Real-time sync, no polling overhead |

---

## 📋 Interview Checklist (L63)

- [ ] **State Pattern**: 8 file states with encapsulated behavior — SYNCED, MODIFIED_LOCAL, PENDING_UPLOAD, UPLOADING, PENDING_DOWNLOAD, DOWNLOADING, CONFLICTED, ERROR
- [ ] **Chain of Responsibility**: Sync filter pipeline — FileSizeValidator → FileTypeFilter → ConflictDetector → CompressionHandler → TransferHandler
- [ ] **Short-circuit**: Chain stops on first failure — fail fast before transfer
- [ ] **Conflict resolution**: Strategy pattern for 3 approaches (LastWriteWins, KeepBoth, ManualResolve)
- [ ] **State transitions**: Clear diagram of valid/invalid transitions, illegal state throws exception
- [ ] **Sequence diagram**: Full walkthrough of sync sub-process (watcher → state → chain → transfer → server)
- [ ] **Event-driven**: FileWatcher (local OS events) + ServerPoller (remote changes)
- [ ] **Chunked transfer**: Resumable large file uploads/downloads
- [ ] **Production**: Delta sync, deduplication, encryption handler, webhooks over polling

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 5 min |
| Core Entities (SyncFile, FileState, SyncHandler) | 7 min |
| State Pattern Implementation (8 states) | 12 min |
| Chain of Responsibility (5 handlers) | 10 min |
| Conflict Resolution Strategies | 5 min |
| Deep Dives (chunked, delta, edge cases) | 6 min |
| **Total** | **~45 min** |

### Design Pattern Summary

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **State** | FileState interface + 8 concrete states | Each file state has fundamentally different behavior for all 4 operations |
| **Chain of Responsibility** | SyncHandler pipeline (5 handlers) | Validation/transformation filters — independently testable, reorderable |
| **Strategy** | ConflictResolutionStrategy (3 implementations) | Swappable conflict resolution without modifying SyncEngine |
| **Observer** | FileWatcher + ServerPoller → SyncEngine | Decouple change detection from sync logic |

See `FileSyncTransferSystem.java` for full implementation with upload, download, conflict, and chain-blocking demos.
