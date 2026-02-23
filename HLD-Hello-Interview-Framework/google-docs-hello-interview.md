# Design Google Docs (Real-Time Collaborative Editing) - Hello Interview Framework

> **Question**: Design a real-time collaborative document editing system like Google Docs where multiple users can simultaneously edit the same document, see each other's cursors and changes in real-time, and have all edits converge to a consistent state.
>
> **Asked at**: Google, Microsoft, Meta, Notion, Figma, Dropbox
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
1. **Real-Time Collaborative Editing**: Multiple users can edit the same document simultaneously, and each user's changes appear on all other users' screens in real-time (< 200ms latency).
2. **Conflict Resolution**: When two users edit the same region simultaneously, the system must automatically resolve conflicts without data loss — all clients must converge to the same final state.
3. **Document Persistence**: Documents are saved automatically and durably. Users never lose work. Full version history is maintained.
4. **Rich Text Editing**: Support formatted text (bold, italic, headers, lists, links, images, tables) — not just plain text.
5. **Presence Awareness**: Show who is currently viewing/editing the document (active cursors, colored selections, user avatars).

#### Nice to Have (P1)
- Offline editing with sync on reconnect.
- Comments, suggestions, and "suggesting mode" (tracked changes).
- Sharing and permissions (view, comment, edit access levels).
- Version history with named versions and restore capability.
- Real-time cursor labels (show each user's name at their cursor position).

#### Below the Line (Out of Scope)
- Spreadsheet or presentation capabilities (only documents).
- Full search across all documents (separate search system).
- File format conversion (export to PDF, DOCX, etc.).
- Mobile-specific editing UI.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Edit Propagation Latency** | < 200ms between users in same region | Users must perceive "instant" collaboration |
| **Conflict Resolution** | Deterministic convergence (all clients reach same state) | Data integrity — no lost edits, no divergence |
| **Availability** | 99.9% for document access, 99.99% for read-only | Users rely on Docs for critical work |
| **Durability** | Zero data loss (every keystroke persisted within 2s) | Users trust "auto-save" to never lose work |
| **Concurrent Editors** | Up to 100 simultaneous editors per document | Large teams and live collaboration events |
| **Document Size** | Up to 1.5M characters (~500 pages) | Google Docs current limit |
| **Total Documents** | 5+ billion documents | Google Workspace scale |
| **Daily Active Users** | 300M+ DAU | Google Docs user base |

### Capacity Estimation

```
Users and Documents:
  DAU: 300 million
  Total documents: 5 billion
  Average document size: 50 KB (text + formatting metadata)
  Total storage: 5B × 50 KB = 250 TB (current document state)

Active Editing Sessions:
  Concurrent editing sessions: ~10 million
  Documents being edited right now: ~5 million
  Average editors per active document: 2
  Documents with 10+ editors: ~100K (collaborative work sessions)

Edit Operations:
  Keystrokes per active user per minute: ~100
  Active editing users at any time: ~10 million
  Operations per second: 10M × 100 / 60 = ~16M ops/sec
  With batching (operations grouped per 50ms): ~320K batched messages/sec

WebSocket Connections:
  Concurrent connections: ~10 million (active editors)
  Connection messages/sec (operations + presence): ~20M/sec
  Bandwidth per connection: ~500 bytes/sec (operations) + ~100 bytes/sec (presence)
  Total bandwidth: 10M × 600 bytes/sec = ~6 GB/sec

Version History:
  Average versions per document per day: 50 (auto-save checkpoints)
  Average version delta size: 500 bytes
  Daily version storage: 5M active docs × 50 × 500 bytes = 125 GB/day
  Annual: ~45 TB/year

Persistence:
  Write throughput: 16M ops/sec → batched to ~320K document writes/sec
  With operation log + periodic snapshots: manageable
```

---

## 2️⃣ Core Entities

### Entity 1: Document
```java
public class Document {
    private final String documentId;              // UUID
    private final String title;
    private final String ownerId;                 // User who created the doc
    private final DocumentContent content;        // Current document content (structured)
    private final long version;                   // Monotonically increasing version number
    private final Instant createdAt;
    private final Instant lastModifiedAt;
    private final String lastModifiedBy;
    private final Map<String, Permission> sharing; // userId → permission level
}

public class DocumentContent {
    // Option A: Plain text (simple but limited)
    // private String text;
    
    // Option B: Rich text as operation log
    // The document is the result of applying all operations in sequence
    private final List<Operation> operationLog;
    
    // Option C: CRDT-based document tree
    // Each character/element has a unique ID and position in a tree
    private final CRDTDocumentTree tree;
}
```

### Entity 2: Operation (the fundamental unit of editing)
```java
public class Operation {
    private final String operationId;             // UUID for dedup
    private final String documentId;
    private final String userId;                  // Who made the edit
    private final long clientVersion;             // Document version when user made the edit
    private final long serverVersion;             // Assigned by server after transformation
    private final OperationType type;
    private final int position;                   // Character/element position in document
    private final String content;                 // For INSERT: the text inserted
    private final int deleteCount;                // For DELETE: number of characters deleted
    private final Map<String, Object> attributes; // For FORMAT: {bold: true, fontSize: 14}
    private final Instant timestamp;
}

public enum OperationType {
    INSERT,    // Insert text at position
    DELETE,    // Delete characters at position
    FORMAT,    // Apply formatting at position range
    RETAIN     // Skip N characters (used in OT to express "no change here")
}
```

### Entity 3: Editing Session
```java
public class EditingSession {
    private final String sessionId;
    private final String documentId;
    private final String userId;
    private final String displayName;
    private final String cursorColor;             // Unique color per user in document
    private final CursorPosition cursor;          // Current cursor position
    private final Selection selection;             // Current text selection (if any)
    private final Instant connectedAt;
    private final Instant lastActivityAt;
    private final WebSocketConnection connection;
}

public class CursorPosition {
    private final int offset;                     // Character offset in document
    private final String elementId;               // For CRDT: element ID near cursor
}

public class Selection {
    private final int anchorOffset;               // Selection start
    private final int focusOffset;                // Selection end
}
```

### Entity 4: Document Version (for history)
```java
public class DocumentVersion {
    private final String documentId;
    private final long version;
    private final String snapshotContent;         // Full document state at this version (periodic)
    private final List<Operation> operationsSinceLastSnapshot;
    private final String userId;                  // Who triggered the save point
    private final Instant timestamp;
    private final String label;                   // Optional: "Version named by user"
}
```

---

## 3️⃣ API Design

### 1. Open Document (establish WebSocket connection)
```
WebSocket: wss://docs.example.com/ws/documents/{document_id}

→ Client sends CONNECT:
{
  "type": "CONNECT",
  "document_id": "doc_abc123",
  "user_id": "usr_456",
  "token": "jwt_token_here"
}

← Server sends DOCUMENT_STATE:
{
  "type": "DOCUMENT_STATE",
  "document_id": "doc_abc123",
  "version": 4523,
  "content": "<rich-text-content>",
  "active_users": [
    { "user_id": "usr_789", "name": "Alice", "color": "#FF5733", "cursor": { "offset": 142 } },
    { "user_id": "usr_101", "name": "Bob", "color": "#33FF57", "cursor": { "offset": 890 } }
  ]
}
```

### 2. Send Edit Operation (client → server)
```
→ Client sends OPERATION:
{
  "type": "OPERATION",
  "client_version": 4523,
  "operations": [
    { "type": "RETAIN", "count": 142 },
    { "type": "INSERT", "content": "Hello " },
    { "type": "RETAIN", "count": 4381 }
  ]
}

← Server acknowledges:
{
  "type": "ACK",
  "server_version": 4524,
  "transformed_operations": [...]
}
```

### 3. Receive Other Users' Edits (server → client)
```
← Server broadcasts to all other users:
{
  "type": "REMOTE_OPERATION",
  "user_id": "usr_456",
  "user_name": "Charlie",
  "server_version": 4524,
  "operations": [
    { "type": "RETAIN", "count": 142 },
    { "type": "INSERT", "content": "Hello " },
    { "type": "RETAIN", "count": 4381 }
  ]
}
```

### 4. Cursor/Presence Update
```
→ Client sends CURSOR_UPDATE:
{
  "type": "CURSOR_UPDATE",
  "cursor": { "offset": 148 },
  "selection": null
}

← Server broadcasts to others:
{
  "type": "REMOTE_CURSOR",
  "user_id": "usr_456",
  "user_name": "Charlie",
  "color": "#5733FF",
  "cursor": { "offset": 148 }
}
```

### 5. REST: Get Document (for initial load / non-realtime access)
```
GET /api/v1/documents/{document_id}

Response (200 OK):
{
  "document_id": "doc_abc123",
  "title": "Project Proposal",
  "owner_id": "usr_123",
  "version": 4523,
  "content": "<rich-text-content>",
  "last_modified_at": "2025-01-10T10:30:00Z",
  "last_modified_by": "usr_456",
  "word_count": 2450,
  "sharing": {
    "usr_456": "EDIT",
    "usr_789": "COMMENT",
    "anyone_with_link": "VIEW"
  }
}
```

### 6. REST: Version History
```
GET /api/v1/documents/{document_id}/versions?limit=20

Response (200 OK):
{
  "document_id": "doc_abc123",
  "versions": [
    {
      "version": 4523,
      "timestamp": "2025-01-10T10:30:00Z",
      "user": "Charlie",
      "summary": "Added introduction paragraph"
    },
    {
      "version": 4500,
      "timestamp": "2025-01-10T10:15:00Z",
      "user": "Alice",
      "summary": "Reformatted table"
    }
  ]
}
```

---

## 4️⃣ Data Flow

### Flow 1: Real-Time Collaborative Edit (Core Flow)
```
1. User A types "Hello " at position 142 in document
   ↓
2. Client A generates operation: [RETAIN 142, INSERT "Hello ", RETAIN 4381]
   Client A applies operation LOCALLY (optimistic update — user sees change instantly)
   Client A sends operation to server via WebSocket (client_version = 4523)
   ↓
3. Collaboration Server receives operation:
   a. Validate: does client_version match expected?
   b. If client_version < server_version: TRANSFORM the operation
      against all operations that happened between client_version and server_version
      (Operational Transformation — OT)
   c. Assign server_version = 4524
   d. Persist operation to Operation Log (append-only, durable)
   e. Send ACK to User A (with server_version and transformed operation)
   ↓
4. Broadcast transformed operation to all OTHER connected clients (User B, C, etc.)
   ↓
5. Client B receives remote operation:
   a. Transform against any of B's pending (unacknowledged) local operations
   b. Apply transformed operation to local document state
   c. Update cursor positions for User A
   d. User B sees "Hello " appear at the correct position
   ↓
6. All clients converge to the same document state at version 4524
   ↓
7. Background: periodically snapshot document state to persistent storage
   (every N operations or every M seconds)
```

### Flow 2: Conflict Resolution with OT
```
Initial document: "The cat sat" (version 100)

User A (at version 100): types "big " at position 4 → "The big cat sat"
  Operation A: [RETAIN 4, INSERT "big ", RETAIN 7]

User B (at version 100): deletes "cat" at position 4 → "The  sat"
  Operation B: [RETAIN 4, DELETE 3, RETAIN 4]

Both operations arrive at server. Server processes A first:
  1. Apply A: "The big cat sat" → version 101
  2. Transform B against A:
     B was: [RETAIN 4, DELETE 3, RETAIN 4]
     After A's insert of 4 chars at pos 4:
     B becomes: [RETAIN 8, DELETE 3, RETAIN 4]  (shifted by 4 characters)
  3. Apply transformed B: "The big  sat" → version 102

Result: "The big  sat" — both edits preserved, no data lost.
Both clients converge to this result after receiving the transformed operations.
```

### Flow 3: Document Persistence
```
1. Every operation is appended to an Operation Log (WAL-style)
   → Durable within 2 seconds of the edit
   ↓
2. Every 100 operations OR every 30 seconds:
   Collaboration Server creates a SNAPSHOT:
   - Full document content at current version
   - Stored in document DB (e.g., Bigtable / DynamoDB)
   ↓
3. For version history:
   - Keep snapshots at meaningful intervals
   - Group operations by user session for "change summaries"
   ↓
4. Recovery from crash:
   - Load latest snapshot
   - Replay operations after snapshot from Operation Log
   - Result: exact document state restored, zero data loss
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           CLIENTS (Browser / App)                           │
│                                                                              │
│  User A types "Hello" ─── WebSocket ───→                                    │
│  User B sees "Hello"  ←── WebSocket ───                                     │
│  User C's cursor at pos 42 ←── WebSocket ───                               │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ WebSocket (persistent connection)
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      WEBSOCKET GATEWAY                                      │
│                      (Layer 7, sticky sessions)                             │
│                                                                              │
│  • Terminates WebSocket connections                                         │
│  • Routes to correct Collaboration Server (by document_id)                 │
│  • Handles auth, rate limiting, connection management                      │
│  • 10M+ concurrent connections                                              │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    COLLABORATION SERVER                                      │
│                    (Stateful — one server per active document)               │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────┐              │
│  │           PER-DOCUMENT STATE (in-memory)                  │              │
│  │                                                            │              │
│  │  • Current document version: 4524                         │              │
│  │  • Recent operations buffer (last 1000 ops)              │              │
│  │  • Connected users: [Alice, Bob, Charlie]                │              │
│  │  • Cursor positions per user                              │              │
│  │  • OT transformation engine                               │              │
│  │                                                            │              │
│  │  When operation arrives:                                  │              │
│  │    1. Transform against concurrent operations (OT)        │              │
│  │    2. Assign server version                               │              │
│  │    3. Append to operation log (async durable write)       │              │
│  │    4. Broadcast to all other connected clients            │              │
│  │    5. ACK to sender                                       │              │
│  └──────────────────────────────────────────────────────────┘              │
│                                                                              │
│  Scaling: 1 server per active document (document = unit of sharding)       │
│  5M active documents × 1 server process each → thousands of server nodes   │
│  Multiple documents per server process (partitioned)                        │
└──────────┬──────────────────────────────────┬────────────────────────────────┘
           │                                   │
     ┌─────▼──────┐                  ┌────────▼────────┐
     │ OPERATION   │                  │ DOCUMENT STORE   │
     │ LOG         │                  │                  │
     │             │                  │ (Persistent      │
     │ Append-only │                  │  document state) │
     │ WAL for     │                  │                  │
     │ operations  │                  │ • Snapshots      │
     │             │                  │ • Version history│
     │ Redis Stream│                  │ • Metadata       │
     │ or Kafka    │                  │                  │
     │             │                  │ Bigtable /       │
     │ Retention:  │                  │ DynamoDB /       │
     │ 7 days      │                  │ S3 + metadata DB │
     └─────────────┘                  └──────────────────┘


┌────────────────────────────────────────────────────────────────────────────┐
│                    DOCUMENT METADATA SERVICE                                │
│                                                                              │
│  • Document ownership, sharing, permissions                                 │
│  • Which collaboration server owns each active document                    │
│  • User sessions, connection routing                                        │
│  • MySQL / PostgreSQL + Redis cache                                         │
└────────────────────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────────────────┐
│                    PRESENCE SERVICE                                          │
│                                                                              │
│  • Track who is viewing/editing each document                               │
│  • Broadcast cursor positions (throttled to 50ms intervals)                │
│  • Powered by Redis Pub/Sub or in-memory on Collaboration Server           │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **WebSocket Gateway** | Connection management, routing, auth | Envoy / Nginx / custom | 10M+ connections, horizontal |
| **Collaboration Server** | OT/CRDT engine, per-document state, broadcast | Java/Go (stateful) | Sharded by document_id |
| **Operation Log** | Durable, ordered append of all operations | Redis Streams / Kafka | Partitioned by document_id |
| **Document Store** | Persistent snapshots, version history | Bigtable / DynamoDB / S3 | Partitioned by document_id |
| **Metadata Service** | Permissions, sharing, document routing | PostgreSQL + Redis | Stateless, replicated |
| **Presence Service** | Active users, cursor positions | Redis Pub/Sub / in-memory | Co-located with Collaboration Server |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Operational Transformation (OT) — The Core Conflict Resolution Algorithm

**The Problem**: When two users edit simultaneously, their operations are based on the same document version but will conflict. OT transforms operations so they can be applied in any order and still converge to the same result.

```java
public class OTEngine {

    /**
     * Operational Transformation: given two concurrent operations A and B
     * that were both based on the same document state, compute A' and B'
     * such that:
     *   apply(apply(doc, A), B') == apply(apply(doc, B), A')
     * 
     * This is the "transformation property" — the diamond property.
     * 
     * Operations are sequences of components:
     *   RETAIN(n)  — skip n characters (no change)
     *   INSERT(s)  — insert string s at current position
     *   DELETE(n)  — delete n characters at current position
     */
    
    /**
     * Transform operation A against operation B.
     * Returns A' — the transformed version of A that accounts for B having been applied.
     */
    public List<OpComponent> transform(List<OpComponent> opA, List<OpComponent> opB) {
        List<OpComponent> transformedA = new ArrayList<>();
        
        int indexA = 0, indexB = 0;
        OpComponent compA = opA.get(indexA);
        OpComponent compB = opB.get(indexB);
        
        while (indexA < opA.size() || indexB < opB.size()) {
            // Case 1: A inserts — B doesn't affect inserts (they go at the current position)
            if (compA != null && compA.isInsert()) {
                transformedA.add(compA);
                // B must account for A's insert by retaining over it
                indexA++;
                compA = indexA < opA.size() ? opA.get(indexA) : null;
                continue;
            }
            
            // Case 2: B inserts — A must skip over B's inserted text
            if (compB != null && compB.isInsert()) {
                transformedA.add(OpComponent.retain(compB.getContent().length()));
                indexB++;
                compB = indexB < opB.size() ? opB.get(indexB) : null;
                continue;
            }
            
            // Case 3: Both retain — take the minimum
            if (compA != null && compA.isRetain() && compB != null && compB.isRetain()) {
                int min = Math.min(compA.getCount(), compB.getCount());
                transformedA.add(OpComponent.retain(min));
                
                if (compA.getCount() > min) compA = OpComponent.retain(compA.getCount() - min);
                else { indexA++; compA = indexA < opA.size() ? opA.get(indexA) : null; }
                
                if (compB.getCount() > min) compB = OpComponent.retain(compB.getCount() - min);
                else { indexB++; compB = indexB < opB.size() ? opB.get(indexB) : null; }
                continue;
            }
            
            // Case 4: A deletes, B retains — A still deletes (adjusted for B's position)
            if (compA != null && compA.isDelete() && compB != null && compB.isRetain()) {
                int min = Math.min(compA.getCount(), compB.getCount());
                transformedA.add(OpComponent.delete(min));
                
                if (compA.getCount() > min) compA = OpComponent.delete(compA.getCount() - min);
                else { indexA++; compA = indexA < opA.size() ? opA.get(indexA) : null; }
                
                if (compB.getCount() > min) compB = OpComponent.retain(compB.getCount() - min);
                else { indexB++; compB = indexB < opB.size() ? opB.get(indexB) : null; }
                continue;
            }
            
            // Case 5: A retains, B deletes — A must skip what B deleted
            if (compA != null && compA.isRetain() && compB != null && compB.isDelete()) {
                int min = Math.min(compA.getCount(), compB.getCount());
                // A was going to retain over these chars, but B deleted them → skip
                
                if (compA.getCount() > min) compA = OpComponent.retain(compA.getCount() - min);
                else { indexA++; compA = indexA < opA.size() ? opA.get(indexA) : null; }
                
                if (compB.getCount() > min) compB = OpComponent.delete(compB.getCount() - min);
                else { indexB++; compB = indexB < opB.size() ? opB.get(indexB) : null; }
                continue;
            }
            
            // Case 6: Both delete same region — only one deletion needed
            if (compA != null && compA.isDelete() && compB != null && compB.isDelete()) {
                int min = Math.min(compA.getCount(), compB.getCount());
                // Both deleting same chars — no-op in transformed A (B already deleted them)
                
                if (compA.getCount() > min) compA = OpComponent.delete(compA.getCount() - min);
                else { indexA++; compA = indexA < opA.size() ? opA.get(indexA) : null; }
                
                if (compB.getCount() > min) compB = OpComponent.delete(compB.getCount() - min);
                else { indexB++; compB = indexB < opB.size() ? opB.get(indexB) : null; }
                continue;
            }
            
            break; // Shouldn't reach here for valid operations
        }
        
        return transformedA;
    }
}
```

**OT Example**:
```
Document: "Hello World" (version 10)

User A (v10): INSERT "Beautiful " at pos 6 → "Hello Beautiful World"
  Op A: [RETAIN 6, INSERT "Beautiful ", RETAIN 5]

User B (v10): DELETE 5 chars at pos 6 → "Hello "
  Op B: [RETAIN 6, DELETE 5]

Server receives A first → applies → "Hello Beautiful World" (v11)

Transform B against A:
  B was:   [RETAIN 6, DELETE 5]
  A added 10 chars ("Beautiful ") at pos 6
  B' becomes: [RETAIN 16, DELETE 5]   (skip over A's insertion)

Apply B': "Hello Beautiful World" → delete 5 at pos 16 → "Hello Beautiful "  (v12)

Alternatively, if server received B first:
  Apply B → "Hello " (v11)
  Transform A against B:
    A was:   [RETAIN 6, INSERT "Beautiful ", RETAIN 5]
    B deleted 5 chars at pos 6
    A' becomes: [RETAIN 6, INSERT "Beautiful "]  (the 5 retained chars no longer exist)
  Apply A' → "Hello Beautiful "  (v12)

SAME RESULT either way! ✓ This is the OT convergence guarantee.
```

---

### Deep Dive 2: OT vs CRDT — Choosing the Conflict Resolution Strategy

**The Problem**: Two dominant approaches exist for real-time collaboration: OT (Operational Transformation) and CRDT (Conflict-free Replicated Data Types). Each has significant trade-offs.

```
┌─────────────────────┬──────────────────────────────┬──────────────────────────────┐
│ Aspect              │ OT (Google Docs uses this)   │ CRDT (Figma, Yjs use this)   │
├─────────────────────┼──────────────────────────────┼──────────────────────────────┤
│ Architecture        │ Centralized server           │ Decentralized (peer-to-peer  │
│                     │ (single source of truth)     │  possible)                   │
│ Server required?    │ Yes (transforms operations)  │ No (clients can merge)       │
│ Complexity          │ Transform functions complex  │ Data structure complex        │
│                     │ (N² pairs for N op types)    │ (unique IDs per character)   │
│ Memory overhead     │ Low (operations are small)   │ High (metadata per character)│
│ Offline support     │ Hard (must reconnect to      │ Easy (merge on reconnect)    │
│                     │  transform pending ops)      │                              │
│ Undo/Redo           │ Easier (inverse operations)  │ Harder (must track intent)   │
│ Rich text           │ Well-supported               │ Emerging (Peritext, etc.)    │
│ Proven at scale     │ Google Docs (15+ years)      │ Figma, Notion (newer)        │
│ Convergence         │ Guaranteed (with correct      │ Guaranteed (by construction) │
│                     │  transform functions)        │                              │
│ Latency             │ Server round-trip required   │ Can apply locally first      │
│ Document size       │ Efficient (no per-char       │ 2-5x overhead (unique IDs)   │
│                     │  metadata)                   │                              │
└─────────────────────┴──────────────────────────────┴──────────────────────────────┘

Our choice: OT (Operational Transformation)

Why:
1. Centralized server gives us a single source of truth → simpler reasoning
2. Lower memory overhead for large documents (no per-character metadata)
3. Battle-tested at Google scale for 15+ years
4. Better support for rich text formatting
5. Undo/redo is more natural

When would CRDT be better:
- Offline-first applications (local-first software)
- Peer-to-peer collaboration (no server needed)
- Applications where server round-trip latency is unacceptable
```

---

### Deep Dive 3: Server-Side Document Session Management

**The Problem**: Each active document needs a "home" server that manages the OT state, connected clients, and operation ordering. How do we assign documents to servers and handle server failures?

```java
public class DocumentSessionManager {

    /**
     * Document-to-server assignment:
     * 
     * Each active document is assigned to exactly ONE collaboration server.
     * This server holds the in-memory OT state and all WebSocket connections for that doc.
     * 
     * Assignment strategy: Consistent hashing on document_id.
     * Lookup: Metadata Service maintains document_id → server mapping.
     * 
     * When a document is opened for the first time (cold start):
     *   1. Gateway queries Metadata Service: "which server owns doc_abc?"
     *   2. If no assignment → assign to least-loaded server via consistent hash
     *   3. Server loads latest snapshot + replays operation log
     *   4. Document is now "hot" — ready for real-time editing
     * 
     * When server crashes:
     *   1. Health check detects server down (< 10s)
     *   2. Connected clients' WebSockets break → clients auto-reconnect
     *   3. Gateway assigns document to new server (next in hash ring)
     *   4. New server loads snapshot + replays op log → recovers full state
     *   5. Clients reconnect, receive current state → resume editing
     *   6. Total disruption: ~10-30 seconds (users see "Reconnecting...")
     */
    
    public String getServerForDocument(String documentId) {
        // Check if document is already assigned to a live server
        String assignedServer = metadataService.getDocumentServer(documentId);
        if (assignedServer != null && healthChecker.isAlive(assignedServer)) {
            return assignedServer;
        }
        
        // Assign to a new server via consistent hashing
        String newServer = consistentHash.getNode(documentId);
        metadataService.assignDocument(documentId, newServer);
        
        // Trigger cold start: load snapshot + replay ops
        collaborationClient.initializeDocument(newServer, documentId);
        
        return newServer;
    }
}
```

**Server failure recovery timeline**:
```
T=0s     Server crashes (holds 500 active documents)
T=5s     Health check detects failure
T=5s     500 documents reassigned to other servers via consistent hash
T=5-15s  Each new server loads snapshot + replays operation log
T=10-30s Clients auto-reconnect via WebSocket Gateway
T=30s    All documents fully recovered, editing resumes

Data loss: ZERO (operation log is durable, separate from server)
Operations during outage: buffered on client, sent on reconnect
```

---

### Deep Dive 4: Persistence — Operation Log + Snapshots

**The Problem**: Every keystroke must be durably persisted (users trust auto-save). But writing the full document on every keystroke is too expensive. We use a WAL (Write-Ahead Log) pattern: log operations immediately, snapshot periodically.

```java
public class DocumentPersistenceManager {

    /**
     * Dual persistence strategy:
     * 
     * 1. Operation Log (WAL): append every operation immediately (durable in < 2s)
     *    - Append-only, sequential writes → very fast
     *    - Used for crash recovery (replay ops from last snapshot)
     *    - Retention: 7 days (older ops are covered by snapshots)
     * 
     * 2. Snapshots: full document state at a point in time
     *    - Created every 100 operations OR every 30 seconds (whichever first)
     *    - Stored in Document Store (Bigtable / DynamoDB / S3)
     *    - Used for: initial document load, version history, recovery
     */
    
    private static final int SNAPSHOT_INTERVAL_OPS = 100;
    private static final Duration SNAPSHOT_INTERVAL_TIME = Duration.ofSeconds(30);
    
    private int opsSinceLastSnapshot = 0;
    private Instant lastSnapshotTime = Instant.now();
    
    // Called after every operation is processed by OT engine
    public void persistOperation(String documentId, Operation op) {
        // Step 1: Append to operation log (async but durable)
        operationLog.append(documentId, op);  // Redis Stream or Kafka
        
        opsSinceLastSnapshot++;
        
        // Step 2: Check if snapshot is needed
        boolean needsSnapshot = opsSinceLastSnapshot >= SNAPSHOT_INTERVAL_OPS
            || Duration.between(lastSnapshotTime, Instant.now()).compareTo(SNAPSHOT_INTERVAL_TIME) > 0;
        
        if (needsSnapshot) {
            createSnapshot(documentId);
        }
    }
    
    private void createSnapshot(String documentId) {
        // Get current document state from in-memory OT engine
        String documentContent = otEngine.getCurrentContent(documentId);
        long version = otEngine.getCurrentVersion(documentId);
        
        // Write snapshot to document store
        documentStore.writeSnapshot(DocumentSnapshot.builder()
            .documentId(documentId)
            .version(version)
            .content(documentContent)
            .timestamp(Instant.now())
            .build());
        
        opsSinceLastSnapshot = 0;
        lastSnapshotTime = Instant.now();
    }
    
    // Recovery: load last snapshot + replay subsequent operations
    public DocumentState recover(String documentId) {
        // Step 1: Load latest snapshot
        DocumentSnapshot snapshot = documentStore.getLatestSnapshot(documentId);
        
        // Step 2: Get all operations AFTER the snapshot version
        List<Operation> replayOps = operationLog.getOperationsAfter(
            documentId, snapshot.getVersion());
        
        // Step 3: Replay operations on top of snapshot
        DocumentState state = DocumentState.fromSnapshot(snapshot);
        for (Operation op : replayOps) {
            state.apply(op);
        }
        
        return state; // Exact state before crash
    }
}
```

**Persistence performance**:
```
Operation log append (Redis Stream / Kafka):
  Latency: < 5ms per operation
  Throughput: 320K batched writes/sec
  Durability: replicated within 2 seconds

Snapshot creation:
  Frequency: every 100 ops or 30 seconds
  Size: avg 50 KB per document
  Write to Bigtable/DynamoDB: ~10ms
  5M active docs / 30s = ~167K snapshots/sec (distributed)

Recovery time (cold start):
  Load snapshot: ~20ms (Bigtable read)
  Replay 100 ops: ~5ms
  Total: ~25ms per document
```

---

### Deep Dive 5: Client-Side OT — Optimistic Local Editing

**The Problem**: Users must see their own edits instantly (no waiting for server round-trip). But the server might transform their operation. The client must handle three states: confirmed, pending, and buffer.

```java
public class ClientOTState {

    /**
     * Client maintains three states:
     * 
     * 1. SYNCHRONIZED: no pending operations, client and server in sync
     * 2. AWAITING_ACK: client sent an operation, waiting for server ACK
     *    - One operation "in-flight" to server
     *    - New local edits go into a buffer
     * 3. AWAITING_ACK_WITH_BUFFER: in-flight op + buffered local ops
     *    - When ACK arrives, send buffer as next operation
     */
    
    enum State { SYNCHRONIZED, AWAITING_ACK, AWAITING_ACK_WITH_BUFFER }
    
    private State state = State.SYNCHRONIZED;
    private Operation pendingOp = null;   // Sent to server, waiting for ACK
    private Operation bufferOp = null;    // Accumulated local edits not yet sent
    
    // User makes a local edit
    public void applyLocal(Operation op) {
        // Always apply locally IMMEDIATELY (optimistic)
        document.apply(op);
        
        switch (state) {
            case SYNCHRONIZED:
                // Send to server immediately
                sendToServer(op);
                pendingOp = op;
                state = State.AWAITING_ACK;
                break;
                
            case AWAITING_ACK:
                // Can't send yet (waiting for previous ACK)
                // Buffer this operation
                bufferOp = op;
                state = State.AWAITING_ACK_WITH_BUFFER;
                break;
                
            case AWAITING_ACK_WITH_BUFFER:
                // Compose new edit into existing buffer
                bufferOp = compose(bufferOp, op);
                break;
        }
    }
    
    // Server acknowledges our operation
    public void onServerAck(long serverVersion) {
        switch (state) {
            case AWAITING_ACK:
                // Our operation was accepted
                pendingOp = null;
                state = State.SYNCHRONIZED;
                break;
                
            case AWAITING_ACK_WITH_BUFFER:
                // Our operation was accepted; now send the buffer
                sendToServer(bufferOp);
                pendingOp = bufferOp;
                bufferOp = null;
                state = State.AWAITING_ACK;
                break;
        }
    }
    
    // Receive another user's operation from server
    public void onRemoteOperation(Operation serverOp) {
        switch (state) {
            case SYNCHRONIZED:
                // No local pending ops — apply directly
                document.apply(serverOp);
                break;
                
            case AWAITING_ACK:
                // Transform server op against our pending op
                TransformResult result = transform(pendingOp, serverOp);
                pendingOp = result.clientOp;      // Updated pending op
                document.apply(result.serverOp);   // Apply transformed server op
                break;
                
            case AWAITING_ACK_WITH_BUFFER:
                // Transform server op against pending AND buffer
                TransformResult r1 = transform(pendingOp, serverOp);
                pendingOp = r1.clientOp;
                TransformResult r2 = transform(bufferOp, r1.serverOp);
                bufferOp = r2.clientOp;
                document.apply(r2.serverOp);
                break;
        }
    }
}
```

**Client state machine**:
```
                    ┌──────────────────┐
                    │   SYNCHRONIZED   │
                    │  (no pending ops)│
                    └───────┬──────────┘
                            │ local edit
                            ▼
                    ┌──────────────────┐
            ┌──────│   AWAITING_ACK   │──────┐
            │      │  (1 op in-flight) │      │
            │      └──────────────────┘      │
            │ server ACK        │ local edit  │ remote op:
            │                   ▼             │ transform against pending
            │      ┌──────────────────────┐   │
            │      │ AWAITING_ACK +       │   │
            │      │ BUFFER               │───┘
            │      │ (in-flight + buffer) │
            │      └──────────┬───────────┘
            │                 │ server ACK → send buffer
            └─────────────────┘
```

---

### Deep Dive 6: Presence — Cursors and Selections

**The Problem**: Users need to see where other collaborators are editing (colored cursors and selections). Cursor positions change on every keystroke — this is high-frequency data that must not overload the system.

```java
public class PresenceManager {

    /**
     * Presence data: cursor position + selection per user per document.
     * 
     * Challenge: if every keystroke sends a cursor update, that's 16M updates/sec
     * globally — same as operation traffic.
     * 
     * Optimization:
     *   1. Throttle cursor updates to every 50ms (vs every keystroke)
     *   2. Batch cursor updates with operation messages (same WebSocket)
     *   3. Cursor positions are EPHEMERAL — never persisted to DB
     *   4. If a cursor update is lost, the next one corrects it (self-healing)
     *   5. Transform cursor positions when remote operations arrive
     */
    
    // Server-side: broadcast cursor updates to other users in the document
    public void handleCursorUpdate(String documentId, String userId, CursorPosition cursor) {
        // Update in-memory state
        sessionState.get(documentId).updateCursor(userId, cursor);
        
        // Broadcast to all OTHER connected users (not the sender)
        List<WebSocketConnection> others = getOtherConnections(documentId, userId);
        CursorBroadcast broadcast = new CursorBroadcast(userId, cursor);
        
        for (WebSocketConnection conn : others) {
            conn.send(broadcast); // Fire and forget — cursor data is ephemeral
        }
    }
    
    /**
     * When a remote operation is applied, all cursor positions must be TRANSFORMED.
     * 
     * Example: User A has cursor at position 100.
     *          User B inserts 10 characters at position 50.
     *          User A's cursor should shift to position 110.
     */
    public CursorPosition transformCursor(CursorPosition cursor, Operation op) {
        int newOffset = cursor.getOffset();
        int pos = 0;
        
        for (OpComponent comp : op.getComponents()) {
            if (comp.isRetain()) {
                pos += comp.getCount();
            } else if (comp.isInsert()) {
                if (pos <= cursor.getOffset()) {
                    newOffset += comp.getContent().length(); // Shift cursor right
                }
                pos += comp.getContent().length();
            } else if (comp.isDelete()) {
                if (pos < cursor.getOffset()) {
                    int deleteEnd = pos + comp.getCount();
                    if (deleteEnd <= cursor.getOffset()) {
                        newOffset -= comp.getCount(); // Shift cursor left
                    } else {
                        newOffset = pos; // Cursor was in deleted range → move to delete point
                    }
                }
            }
        }
        
        return new CursorPosition(Math.max(0, newOffset));
    }
}
```

---

### Deep Dive 7: Version History & Undo/Redo

**The Problem**: Users expect to browse version history ("See changes from Tuesday") and undo/redo their own edits. Both features interact complexly with real-time collaboration.

```java
public class VersionHistoryService {

    /**
     * Version history strategy:
     * 
     * 1. Auto-save checkpoints: snapshot every 30 seconds during active editing
     * 2. Session boundaries: snapshot when a user stops editing (5 min inactivity)
     * 3. Named versions: user explicitly saves a named version ("Final Draft")
     * 4. Change grouping: group operations by user+time for readable diffs
     * 
     * Storage: snapshots in Document Store, indexed by (document_id, version)
     * 
     * Viewing history: load two snapshots → diff them → show changes
     */
    
    public List<VersionSummary> getVersionHistory(String documentId, int limit) {
        List<DocumentSnapshot> snapshots = documentStore.getSnapshots(documentId, limit);
        
        return snapshots.stream().map(s -> VersionSummary.builder()
            .version(s.getVersion())
            .timestamp(s.getTimestamp())
            .userId(s.getUserId())
            .userName(userService.getName(s.getUserId()))
            .changeStats(computeChangeStats(s))
            .build())
            .toList();
    }
    
    /**
     * Undo in collaborative editing is HARD:
     * 
     * Naive undo: reverse the last operation → WRONG if other users edited since
     * 
     * Correct approach: "Undo own operations only"
     *   1. Track each user's operation history separately
     *   2. To undo: compute the INVERSE of the user's last operation
     *   3. Transform the inverse against all operations that happened after it
     *   4. Apply the transformed inverse as a NEW operation
     *   5. This preserves other users' edits while reversing yours
     */
    public Operation computeUndo(String documentId, String userId) {
        // Get user's last operation
        Operation lastOp = getUserLastOperation(documentId, userId);
        
        // Compute inverse
        Operation inverse = computeInverse(lastOp);
        
        // Get all operations that happened AFTER lastOp
        List<Operation> subsequentOps = getOperationsAfter(documentId, lastOp.getServerVersion());
        
        // Transform inverse against each subsequent operation
        Operation transformed = inverse;
        for (Operation op : subsequentOps) {
            transformed = otEngine.transform(transformed, op);
        }
        
        // Apply as a new operation (goes through normal OT pipeline)
        return transformed;
    }
}
```

---

### Deep Dive 8: WebSocket Connection Management at Scale

**The Problem**: 10M concurrent WebSocket connections, each generating ~2 messages/sec (operations + cursor). How do we manage this at scale?

```java
public class WebSocketGateway {

    /**
     * WebSocket scaling strategy:
     * 
     * 1. Connection tier (Gateway): stateless, handles TLS + auth + routing
     *    - Each gateway node handles ~50K connections
     *    - 200+ gateway nodes for 10M connections
     *    - Horizontal scaling via L4 load balancer
     * 
     * 2. Sticky routing: all connections for same document → same Collaboration Server
     *    - Gateway maintains document_id → server mapping (from Metadata Service)
     *    - If server changes (failover), Gateway redirects client
     * 
     * 3. Heartbeat: client sends ping every 30 seconds
     *    - Server responds with pong
     *    - If no pong in 60s → client reconnects
     *    - If no ping in 60s → server closes connection, removes user from presence
     * 
     * 4. Reconnection protocol:
     *    - Client stores last received server_version
     *    - On reconnect: send RECONNECT with last_version
     *    - Server sends all operations since last_version (from operation log)
     *    - Client applies missing operations → back in sync
     */
    
    public void handleReconnect(WebSocket ws, String documentId, long lastVersion) {
        // Get current server version
        long currentVersion = collaborationServer.getVersion(documentId);
        
        if (currentVersion == lastVersion) {
            // Client is up to date — just re-establish connection
            ws.send(new ReconnectResponse("UP_TO_DATE", currentVersion, List.of()));
            return;
        }
        
        // Client missed some operations — send the gap
        List<Operation> missedOps = operationLog.getOperationsRange(
            documentId, lastVersion + 1, currentVersion);
        
        if (missedOps.size() > 1000) {
            // Too many missed ops — send full document state instead
            DocumentState state = collaborationServer.getDocumentState(documentId);
            ws.send(new ReconnectResponse("FULL_SYNC", currentVersion, state));
        } else {
            // Send missed operations for incremental sync
            ws.send(new ReconnectResponse("INCREMENTAL", currentVersion, missedOps));
        }
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Conflict resolution** | OT (Operational Transformation) | Centralized server = single source of truth; lower memory than CRDT; proven at Google scale |
| **Architecture** | Centralized (server per document) | Simpler correctness reasoning; operation ordering guaranteed by single server |
| **Persistence** | Operation log (WAL) + periodic snapshots | WAL gives durability per-keystroke; snapshots give fast recovery + version history |
| **Client editing** | Optimistic local apply + 3-state machine | Users see own edits instantly; pending/buffer states handle concurrency |
| **Presence** | Ephemeral, throttled (50ms), not persisted | Cursor data is high-frequency but low-value if lost; self-correcting |
| **WebSocket management** | Gateway (stateless) + Collaboration Server (stateful) | Separate connection handling from OT logic; independent scaling |
| **Snapshot frequency** | Every 100 ops or 30 seconds | Balances recovery speed (fewer ops to replay) vs write cost |
| **Undo** | Transform inverse against subsequent ops | Correct in collaborative context; preserves other users' edits |
| **Recovery** | Snapshot + replay from operation log | Zero data loss; ~25ms cold start per document |

## Interview Talking Points

1. **"OT transforms concurrent operations for convergence"** — Two users edit same region → server transforms one against the other → both clients converge to identical state. Diamond property guarantees correctness.
2. **"Optimistic local edits with 3-state client"** — User sees own edit instantly (no server round-trip). Client tracks: synchronized / awaiting-ACK / awaiting-ACK-with-buffer. Remote ops transformed against pending state.
3. **"One collaboration server per active document"** — Document is the unit of sharding. Server holds in-memory OT state + connected users. Consistent hashing assigns documents to servers.
4. **"WAL + snapshots for zero data loss"** — Every operation appended to durable log (< 2s). Snapshot every 100 ops or 30s. Recovery = load snapshot + replay log. ~25ms cold start.
5. **"Server failure recovery in < 30 seconds"** — Health check detects crash → reassign document → new server loads snapshot + replays log → clients auto-reconnect. Zero operations lost.
6. **"Cursor updates are ephemeral and throttled"** — Cursors sent every 50ms (not every keystroke). Never persisted. Self-correcting: if one update is lost, next one fixes the position.
7. **"OT over CRDT for lower memory overhead"** — CRDT attaches unique IDs to every character (2-5x document size). OT operations are small and stateless. For 500-page documents, this matters.
8. **"Undo = transform inverse against subsequent ops"** — Can't just reverse last op (other users may have edited). Compute inverse → transform against all later ops → apply as new operation.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Figma** | Same real-time collaboration | Figma uses CRDT; operates on 2D canvas not text |
| **Slack/Chat** | Real-time WebSocket messaging | Chat is append-only; Docs needs positional edits + OT |
| **Distributed Database** | Same consistency challenges | DB uses consensus (Paxos/Raft); Docs uses OT |
| **Version Control (Git)** | Same branching/merging concept | Git resolves conflicts manually; Docs resolves automatically in real-time |
| **Live Streaming** | Same WebSocket infrastructure | Streaming is one-to-many broadcast; Docs is many-to-many bidirectional |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Conflict resolution** | OT | CRDT (Yjs, Automerge) | Offline-first apps; peer-to-peer; no central server |
| **WebSocket** | Native WebSocket | Socket.IO / SSE | Socket.IO for fallback; SSE if only server→client needed |
| **Operation log** | Redis Streams | Apache Kafka | Higher throughput needs; multi-consumer patterns |
| **Document store** | Bigtable / DynamoDB | PostgreSQL + S3 | Smaller scale; simpler ops |
| **Session management** | Consistent hashing | ZooKeeper leader election | Stronger consistency for assignment; more operational complexity |
| **Rich text model** | Custom OT operations | Quill Delta / Slate.js | Leverage existing editor libraries |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Google Wave / Docs OT Architecture (original papers)
- "Operational Transformation in Real-Time Group Editors" (Sun & Ellis, 1998)
- "CRDTs: The Hard Parts" (Martin Kleppmann, 2020)
- Yjs CRDT Framework Documentation
- Figma's Multiplayer Technology Blog Post
- ShareDB — OT Backend Framework
- Peritext: Rich Text CRDT (Ink & Switch)
