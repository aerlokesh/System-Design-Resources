# 🎯 Topic 62: Collaborative Editing

> **System Design Interview — Deep Dive**
> A comprehensive guide covering real-time collaborative editing systems (Google Docs, Figma, Notion) — Operational Transformation (OT) vs Conflict-Free Replicated Data Types (CRDTs), WebSocket-based real-time sync, cursor/presence awareness, document versioning, conflict resolution, undo/redo in collaborative contexts, offline editing with eventual convergence, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Collaborative Editing Is Hard](#why-collaborative-editing-is-hard)
3. [Operational Transformation (OT)](#operational-transformation-ot)
4. [CRDTs — Conflict-Free Replicated Data Types](#crdts--conflict-free-replicated-data-types)
5. [OT vs CRDT — Detailed Comparison](#ot-vs-crdt--detailed-comparison)
6. [System Architecture](#system-architecture)
7. [Real-Time Transport — WebSocket Layer](#real-time-transport--websocket-layer)
8. [Document Model and Operations](#document-model-and-operations)
9. [Server-Side Conflict Resolution](#server-side-conflict-resolution)
10. [Cursor and Presence Awareness](#cursor-and-presence-awareness)
11. [Document Versioning and History](#document-versioning-and-history)
12. [Undo/Redo in Collaborative Editing](#undoredo-in-collaborative-editing)
13. [Offline Editing and Sync](#offline-editing-and-sync)
14. [Permissions and Access Control](#permissions-and-access-control)
15. [Scaling Collaborative Sessions](#scaling-collaborative-sessions)
16. [Storage and Persistence](#storage-and-persistence)
17. [Performance and Latency](#performance-and-latency)
18. [Real-World Production Patterns](#real-world-production-patterns)
19. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
20. [Common Interview Mistakes](#common-interview-mistakes)
21. [Collaborative Editing by System Design Problem](#collaborative-editing-by-system-design-problem)
22. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Collaborative editing** allows multiple users to simultaneously view and modify the same document in real-time, with each user's changes instantly reflected to all other participants — without conflicts, without data loss, and with the illusion that everyone is editing a single, coherent document.

```
The fundamental challenge:

  User A in New York types "Hello" at position 0.
  User B in London deletes character at position 3 simultaneously.
  
  Without coordination:
    User A sees: "Hello"
    User B sees: "Helo"   ← deleted wrong character!
    
  Documents DIVERGE. Users see different content. Chaos.

  With collaborative editing (OT or CRDT):
    Both operations are transformed relative to each other.
    Both users converge to the same final document.
    Intention is preserved: A's "Hello" and B's deletion both apply correctly.

  The key guarantee: CONVERGENCE.
    All users eventually see the same document state,
    regardless of the order operations arrive.
```

### The Three Requirements

```
1. CONVERGENCE: All replicas reach the same state eventually.
   No matter what order operations arrive, the final document is identical.

2. INTENTION PRESERVATION: Each user's edit does what they intended.
   If I delete the word "bad", it shouldn't accidentally delete "good" 
   because another user's insert shifted positions.

3. RESPONSIVENESS: Local edits appear instantly (< 50ms).
   The user's keystrokes feel immediate. Network latency is hidden.
   Edits are applied LOCALLY first, then synced to the server.
```

---

## Why Collaborative Editing Is Hard

### Challenge 1: Concurrent Edits at Different Positions

```
Document: "ABCDE"

  User A: Insert "X" at position 2 → "ABXCDE"
  User B: Insert "Y" at position 4 → "ABCDYE"

  If User B applies their operation on User A's version:
    User A's doc: "ABXCDE" → Insert "Y" at position 4 → "ABXCYDE"
    
  But User B intended to insert after "D" (originally position 4).
  In the new document "ABXCDE", "D" is now at position 4, so inserting at 4 is correct!
  
  Wait — it works? Only because the insert was AFTER A's insert.
  What if both inserts are at the SAME position?
```

### Challenge 2: Same-Position Concurrent Inserts

```
Document: "ABC"

  User A: Insert "X" at position 1 → "AXBC"
  User B: Insert "Y" at position 1 → "AYBC"

  Conflict! Both want to insert at position 1.
  Final document could be "AXYBC" or "AYXBC".
  
  We need a TIE-BREAKING RULE:
    E.g., User with lower ID goes first → "AXYBC" (X before Y).
    As long as ALL replicas use the same rule → convergence guaranteed.
```

### Challenge 3: Insert + Delete Conflicts

```
Document: "ABCDE"

  User A: Delete character at position 2 (delete "C") → "ABDE"
  User B: Insert "X" at position 2 → "ABXCDE"

  Problem: User B's insert at position 2 was BEFORE the "C" that User A deleted.
  After A's delete, should B's "X" be at position 2 or position 1?

  OT solution: Transform B's operation.
    A deleted at position 2. B inserted at position 2.
    Since B's position == A's position, B's insert stays at position 2.
    But if B had inserted at position 3 (after the deleted char),
    B's operation would be transformed to position 2 (shifted left by 1).
```

### Challenge 4: Network Latency and Ordering

```
  User A (NYC, 50ms to server):
    T=0ms: Types "H" → applies locally → sends to server
    T=50ms: Server receives "H"
    
  User B (London, 150ms to server):
    T=10ms: Types "X" → applies locally → sends to server
    T=160ms: Server receives "X"

  Server sees: "H" first, then "X" (even though B typed first!).
  Operations arrive OUT OF ORDER relative to real time.
  
  Solution: Each operation carries a VERSION NUMBER.
    "H" was made on document version 5.
    "X" was made on document version 5 too (concurrent).
    Server transforms X against H before applying.
```

### Challenge 5: Scaling to Millions of Documents

```
Google Docs: 1B+ documents, millions of active collaborative sessions.
Each session: 2-50 concurrent editors.
Each editor: 5-20 operations per second (typing speed).

  1M active sessions × 10 editors × 10 ops/sec = 100M operations/sec globally.
  
  Can't route all operations through one server.
  Need per-document session routing + horizontal scaling.
```

---

## Operational Transformation (OT)

### How OT Works

```
Core idea: When concurrent operations conflict, TRANSFORM one operation
against the other so they can both be applied without conflict.

  Transform function: transform(op_a, op_b) → (op_a', op_b')
    op_a' = op_a transformed so it can apply AFTER op_b.
    op_b' = op_b transformed so it can apply AFTER op_a.

Example:
  Document: "ABC" (version 0)
  
  User A (version 0): Insert("X", pos=1)  → "AXBC"
  User B (version 0): Insert("Y", pos=2)  → "ABYC"

  Server receives A's operation first:
    Apply A: "ABC" → "AXBC" (version 1)
    
  Server receives B's operation:
    B's op was made on version 0, but doc is now at version 1.
    Must transform B's op against A's op.
    
    transform(Insert("Y", 2), Insert("X", 1)):
      A inserted at position 1 (before B's position 2).
      B's position shifts right by 1: Insert("Y", 3).
    
    Apply transformed B: "AXBC" → "AXBYC" (version 2)
    
  Send to clients:
    User A receives transformed B: Insert("Y", 3) → applies on "AXBC" → "AXBYC" ✓
    User B receives A: Insert("X", 1) → applies on "ABYC" → "AXBYC" ✓
    
  Both converge to "AXBYC". ✓
```

### OT Transformation Rules

```
For a text document, operations are: Insert(char, pos) and Delete(pos).

transform(Insert(c1, p1), Insert(c2, p2)):
  if p1 < p2:   return (Insert(c1, p1), Insert(c2, p2+1))  # B shifts right
  if p1 > p2:   return (Insert(c1, p1+1), Insert(c2, p2))  # A shifts right
  if p1 == p2:  return tie-break by user ID                  # deterministic order

transform(Insert(c, p1), Delete(p2)):
  if p1 <= p2:  return (Insert(c, p1), Delete(p2+1))        # Delete shifts right
  if p1 > p2:   return (Insert(c, p1-1), Delete(p2))        # Insert shifts left

transform(Delete(p1), Delete(p2)):
  if p1 < p2:   return (Delete(p1), Delete(p2-1))           # B's position shifts left
  if p1 > p2:   return (Delete(p1-1), Delete(p2))           # A's position shifts left
  if p1 == p2:  return (NoOp, NoOp)                         # Both deleted same char
```

### OT Architecture (Google Docs Style)

```
┌────────┐     WebSocket     ┌──────────────────┐     ┌──────────┐
│ Client A│←───────────────→│  OT Server        │←──→│ Database  │
└────────┘                   │  (per-document)   │     └──────────┘
                             │                   │
┌────────┐     WebSocket     │  - Receives ops   │
│ Client B│←───────────────→│  - Transforms      │
└────────┘                   │  - Broadcasts      │
                             │  - Stores history  │
┌────────┐     WebSocket     │                   │
│ Client C│←───────────────→│                   │
└────────┘                   └──────────────────┘

The server is the SINGLE SOURCE OF TRUTH for operation ordering.
All operations go through the server, which assigns a global order.
Clients apply operations optimistically (local-first), then reconcile.
```

### Client-Side OT (Optimistic Application)

```
Client maintains:
  - Local document state (what the user sees)
  - Pending operations (sent to server, not yet acknowledged)
  - Server-confirmed state (last ACK'd version)

Flow:
  1. User types → operation created → applied LOCALLY immediately (responsive!)
  2. Operation sent to server.
  3. Meanwhile, incoming operations from server:
     - Transform incoming ops against pending ops.
     - Apply transformed ops to local state.
  4. Server ACKs our operation → remove from pending.
  5. If server returns a TRANSFORMED version → update local state.

This is the "Jupiter model" (used by Google Wave, Google Docs):
  Client and server maintain state diagrams.
  Client applies ops optimistically.
  Server resolves conflicts and broadcasts canonical order.
```

---

## CRDTs — Conflict-Free Replicated Data Types

### How CRDTs Work

```
Core idea: Design the data structure so that ANY ordering of operations
produces the same final result. No transformation needed.
Operations commute — order doesn't matter.

For text editing, use a sequence CRDT (e.g., RGA, LSEQ, Yjs):
  Instead of position-based operations (Insert at pos 3),
  use IDENTITY-based operations (Insert after char ID "abc").

  Each character has a unique, immutable ID.
  Inserts reference the ID of the character they come after.
  Deletes mark a character ID as "tombstoned" (not removed, just hidden).

Example (simplified):
  Document: [A(id=1), B(id=2), C(id=3)]
  
  User A: Insert "X" after id=1 → X gets id=4
    → [A(1), X(4), B(2), C(3)]
    
  User B: Insert "Y" after id=2 → Y gets id=5
    → [A(1), B(2), Y(5), C(3)]

  Merge (any order):
    [A(1), X(4), B(2), Y(5), C(3)]
    
  Both users converge regardless of order. No transformation needed.
```

### Why CRDTs Don't Need a Server

```
OT: Requires a central server to order operations.
    Client → Server → Transform → Broadcast.
    Server is the authority.

CRDT: No central server needed (can work peer-to-peer).
    Client A → Client B (direct sync).
    Each client merges independently.
    Convergence is GUARANTEED by the data structure's math.

But in practice, even CRDT-based systems often use a server:
  - For persistence (save the document)
  - For presence/cursor awareness
  - For access control
  - For offline sync (store-and-forward)
  
  The server is a RELAY, not an authority.
```

### Popular CRDT Libraries

```
Yjs:        JavaScript CRDT library. Used by many editors.
Automerge:  Rust/JS CRDT library. Used by Ink & Switch.
Diamond Types: Rust CRDT. High-performance text editing.
LSEQ/RGA:   Academic CRDT algorithms for sequences.

Yjs example:
  const ydoc = new Y.Doc()
  const ytext = ydoc.getText('content')
  
  // User A
  ytext.insert(0, "Hello")
  
  // User B (concurrent)
  ytext.insert(0, "World")
  
  // After sync: "WorldHello" or "HelloWorld" (deterministic tie-break)
  // Both users see the same result.
```

### CRDT Character IDs and Tombstones

```
Each character in a CRDT text has:
  - Unique ID: (user_id, logical_clock) → globally unique, never reused.
  - Content: The actual character ("A", "B", etc.)
  - Deleted flag: true/false (tombstone)
  - Left origin: ID of the character to the left at insert time.
  - Right origin: ID of the character to the right at insert time.

Insert:
  New char with ID=(user3, clock=7) after char ID=(user1, clock=2).
  Position is RELATIVE (to other char IDs), not absolute.
  → Survives concurrent inserts/deletes at the same position.

Delete:
  Mark char ID=(user1, clock=2) as deleted (tombstone).
  Character is NOT removed from the data structure.
  Just hidden from the visible text.
  
  Why tombstones? If we actually deleted the char, concurrent
  inserts that reference it would lose their anchor.
  
  Garbage collection: Periodically remove tombstones that all
  users have acknowledged (no more references possible).
```

---

## OT vs CRDT — Detailed Comparison

| Aspect | OT | CRDT |
|---|---|---|
| **Central server** | Required (orders operations) | Optional (peer-to-peer possible) |
| **Complexity** | Transform functions are tricky (N² pairs) | Data structure design is complex |
| **Correctness** | Hard to prove (many edge cases) | Mathematically proven convergence |
| **Memory** | Low (stores operations) | Higher (tombstones, character IDs) |
| **Latency** | Requires server round-trip for ordering | Can merge locally (lower latency) |
| **Offline** | Difficult (server must resolve) | Natural (merge when reconnected) |
| **Undo** | Complex (inverse operations) | Complex (requires tracking) |
| **Bandwidth** | Smaller messages (position + char) | Larger messages (IDs + metadata) |
| **Used by** | Google Docs, Microsoft Office Online | Figma, Linear, Apple Notes |
| **Maturity** | 30+ years (since 1989) | ~15 years (since ~2009) |

### When to Choose Which

```
Choose OT when:
  ✅ You have a reliable central server
  ✅ Low memory overhead is important
  ✅ Using a proven framework (Google's OT, ShareDB)
  ✅ Document is mostly text (well-understood transforms)

Choose CRDT when:
  ✅ Offline editing is critical (mobile, unreliable networks)
  ✅ Peer-to-peer sync is desired (no central server)
  ✅ Complex data structures (not just text — trees, maps, lists)
  ✅ Ease of correctness proof matters
  ✅ Using modern libraries (Yjs, Automerge)
  
Modern recommendation: CRDT with Yjs.
  - Well-maintained, battle-tested JavaScript library.
  - Works offline, in browsers, with servers.
  - Handles text, rich text, XML, maps, arrays.
  - Used by Notion, Linear, and many others.
```

---

## System Architecture

### High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                        Client Layer                                  │
│                                                                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                         │
│  │ Client A  │  │ Client B  │  │ Client C  │                         │
│  │ (Editor)  │  │ (Editor)  │  │ (Editor)  │                         │
│  │           │  │           │  │           │                         │
│  │ Local Doc │  │ Local Doc │  │ Local Doc │                         │
│  │ OT/CRDT  │  │ OT/CRDT  │  │ OT/CRDT  │                         │
│  └─────┬────┘  └─────┬────┘  └─────┬────┘                         │
│        │              │              │                              │
└────────┼──────────────┼──────────────┼──────────────────────────────┘
         │  WebSocket   │  WebSocket   │  WebSocket
         │              │              │
┌────────▼──────────────▼──────────────▼──────────────────────────────┐
│                    Collaboration Layer                               │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                 WebSocket Gateway                              │    │
│  │  Routes connections to the correct document session server    │    │
│  └──────────────────────────┬────────────────────────────────────┘    │
│                             │                                        │
│  ┌──────────────────────────▼────────────────────────────────────┐    │
│  │              Document Session Servers                          │    │
│  │                                                               │    │
│  │  Doc "abc": Server 1 (handles all clients for this doc)       │    │
│  │  Doc "def": Server 2                                          │    │
│  │  Doc "ghi": Server 3                                          │    │
│  │                                                               │    │
│  │  Each server:                                                 │    │
│  │    - Maintains in-memory document state                       │    │
│  │    - Applies and transforms operations (OT)                   │    │
│  │    - Broadcasts changes to connected clients                  │    │
│  │    - Periodically persists to storage                         │    │
│  └───────────────────────────┬───────────────────────────────────┘    │
│                              │                                       │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────────┐
│                      Storage Layer                                    │
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐            │
│  │ Document DB   │    │ Op Log       │    │ Object Store │            │
│  │ (PostgreSQL)  │    │ (Cassandra/  │    │ (S3)         │            │
│  │               │    │  DynamoDB)   │    │              │            │
│  │ - Metadata    │    │ - All ops    │    │ - Snapshots  │            │
│  │ - Permissions │    │ - History    │    │ - Revisions  │            │
│  │ - Ownership   │    │ - Versions   │    │ - Exports    │            │
│  └──────────────┘    └──────────────┘    └──────────────┘            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Document Session Routing

```
Problem: Each document needs a SINGLE server to maintain operation order (for OT).
         Multiple servers handling the same document → conflicts.

Solution: Consistent hashing or partition-based routing.

  hash(document_id) % N → Session Server
  
  All clients editing document "abc" → routed to Server 1.
  All clients editing document "def" → routed to Server 2.
  
  One server per active document. No conflicts.
  
  If that server dies:
    Detect failure (heartbeat timeout).
    Another server takes over (re-load document from storage).
    Clients reconnect via WebSocket gateway.
```

---

## Real-Time Transport — WebSocket Layer

### WebSocket Message Protocol

```
Client → Server messages:
  {
    "type": "operation",
    "doc_id": "abc123",
    "version": 42,                    // Client's known server version
    "ops": [
      {"type": "insert", "pos": 5, "text": "hello"},
      {"type": "delete", "pos": 10, "count": 3}
    ]
  }

  {
    "type": "cursor_update",
    "doc_id": "abc123",
    "user_id": "user:42",
    "position": 15,
    "selection": {"start": 10, "end": 15}
  }

Server → Client messages:
  {
    "type": "operation",
    "doc_id": "abc123",
    "version": 43,
    "user_id": "user:99",            // Who made this change
    "ops": [{"type": "insert", "pos": 3, "text": "x"}]
  }

  {
    "type": "ack",
    "doc_id": "abc123",
    "version": 43                     // Server accepted your operation
  }

  {
    "type": "presence_update",
    "doc_id": "abc123",
    "users": [
      {"id": "user:42", "name": "Alice", "cursor": 15, "color": "#FF5733"},
      {"id": "user:99", "name": "Bob", "cursor": 8, "color": "#33FF57"}
    ]
  }
```

### Connection Management

```
Connection lifecycle:
  1. Client opens document → HTTP request to get document content + metadata.
  2. Client establishes WebSocket to the collaboration server.
  3. Server adds client to the document session's subscriber list.
  4. Client sends initial cursor position.
  5. Bidirectional messages flow (operations, cursors, presence).
  6. On disconnect: Remove from subscriber list. Broadcast presence update.
  7. On reconnect: Sync missed operations (send all ops since client's last version).

Heartbeat:
  Client → Server: PING every 30 seconds.
  Server → Client: PONG response.
  If no PONG within 10 seconds → connection dead → reconnect.
```

---

## Document Model and Operations

### Operation Types for Rich Text

```
For a rich-text document (Google Docs style):

  Insert:    {"type": "insert", "pos": 5, "text": "hello", "attrs": {"bold": true}}
  Delete:    {"type": "delete", "pos": 5, "count": 3}
  Retain:    {"type": "retain", "count": 10}  // Skip N characters (no change)
  Format:    {"type": "format", "pos": 5, "count": 3, "attrs": {"bold": true}}

Delta format (like Quill.js / OT-rich-text):
  A single operation is a sequence of retain/insert/delete:
  
  [retain(5), insert("hello", {bold: true}), retain(10), delete(3)]
  
  Meaning: Skip 5 chars → insert "hello" (bold) → skip 10 → delete 3.
  
  This format is efficient and composable.
```

### Document State

```
The document state is the result of applying ALL operations in order:

  Version 0:  ""                         (empty document)
  Version 1:  "Hello"                    (User A inserts "Hello")
  Version 2:  "Hello World"              (User B inserts " World")
  Version 3:  "Hello Beautiful World"    (User C inserts "Beautiful ")
  Version 4:  "Hello Beautiful World!"   (User A inserts "!")

  Current state = apply(op1, op2, op3, op4) to empty document.
  
  To reconstruct any version: replay ops from 0 to that version.
  To get the latest: keep the latest snapshot + apply recent ops.
```

---

## Server-Side Conflict Resolution

### The Server's Role in OT

```
The server maintains:
  1. Document state (latest version)
  2. Version counter
  3. Operation history (all applied ops)
  4. List of connected clients (with their last known version)

When an operation arrives from a client:
  1. Client says: "I made this op on version V"
  2. Server's current version is V + N (N operations happened since V)
  3. Server transforms the incoming op against ops V+1, V+2, ..., V+N
  4. Applies the transformed op → version V+N+1
  5. Broadcasts transformed op to all other clients
  6. ACKs the original client with the new version number
```

### Transformation Pipeline

```
Client sends: op_client (made on version 5)
Server is at: version 8 (ops 6, 7, 8 happened since version 5)

  op_client' = transform(op_client, op6)
  op_client'' = transform(op_client', op7)
  op_client''' = transform(op_client'', op8)
  
  Apply op_client''' → server is now at version 9.
  Broadcast op_client''' to all other clients.
  
  This is called the "server-side transformation pipeline."
```

---

## Cursor and Presence Awareness

### Showing Other Users' Cursors

```
Each client sends cursor position on every change:
  - Position: character offset in the document
  - Selection: start and end of highlighted text
  - User metadata: name, avatar, assigned color

Server broadcasts cursor updates to all clients in the session.

  ┌─────────────────────────────────────────────────────────┐
  │  Hello |World. This is a collaborative document.         │
  │        ↑                                                 │
  │     Alice (blue cursor)                                  │
  │                                                          │
  │  Hello World. This |is a collaborative document.         │
  │                    ↑                                     │
  │     Bob (green cursor)                                   │
  └─────────────────────────────────────────────────────────┘

Cursor transformation:
  When an operation is applied, ALL cursor positions must be transformed.
  If Alice inserts text before Bob's cursor → Bob's cursor shifts right.
  
  This uses the same transform logic as operations:
    new_cursor_pos = transform_position(cursor_pos, applied_operation)
```

### Presence Protocol

```
User joins:
  Server → all clients: {"type": "user_joined", "user": {"id": "user:42", "name": "Alice"}}

User leaves:
  Server → all clients: {"type": "user_left", "user_id": "user:42"}

Cursor update (throttled to 50ms interval):
  Client → Server: {"type": "cursor", "pos": 15, "selection": null}
  Server → others: {"type": "cursor", "user_id": "user:42", "pos": 15}

Typing indicator:
  Client → Server: {"type": "typing", "is_typing": true}
  Auto-clear after 3 seconds of inactivity.
```

---

## Document Versioning and History

### Version Model

```
Each operation creates a new version:

  Version 0: Initial document (empty or template)
  Version 1: First edit
  Version 2: Second edit
  ...
  Version N: Current state

Storage:
  Option A: Store every operation (event sourcing)
    Replay all ops to reconstruct any version.
    Pros: Complete history, fine-grained undo.
    Cons: Replay can be slow for long documents.
    
  Option B: Periodic snapshots + ops since last snapshot
    Snapshot every 100 operations.
    To reconstruct version 350: Load snapshot at 300, replay ops 301-350.
    Pros: Fast reconstruction.
    Cons: Snapshots take storage.
    
  Recommended: Option B (snapshots + ops).
```

### Snapshot Strategy

```
Snapshot frequency:
  Every 100 operations OR every 5 minutes (whichever comes first).
  
Snapshot content:
  {
    "doc_id": "abc123",
    "version": 300,
    "content": "<full document content>",
    "created_at": "2024-03-15T12:00:00Z"
  }

Operation log:
  {
    "doc_id": "abc123",
    "version": 301,
    "user_id": "user:42",
    "ops": [...],
    "timestamp": "2024-03-15T12:00:05Z"
  }

To reconstruct version 350:
  1. Load snapshot at version 300.
  2. Load ops 301-350 from the op log.
  3. Apply ops sequentially.
  4. Result: Document at version 350.
```

### Revision History (User-Facing)

```
"Version history" (like Google Docs):
  Group operations by user + time window:
    
    March 15, 12:00 PM — Alice (42 edits)
    March 15, 11:45 AM — Bob (15 edits)
    March 15, 11:30 AM — Alice (23 edits)
    
  User can click any revision to view it (read-only).
  User can restore any revision (creates a new version that resets to that state).
  
  Implementation:
    Group ops by (user_id, 5-minute window) → one "revision" per group.
    Store the snapshot at the end of each revision for quick viewing.
```

---

## Undo/Redo in Collaborative Editing

### The Undo Problem

```
In single-user editing: Undo = reverse the last operation.
In collaborative editing: Whose "last operation"?

  User A types "Hello".
  User B types "World".
  User A presses Undo.
  
  Should Undo remove "Hello" (A's last op) or "World" (globally last op)?
  Answer: Remove "Hello" (A's own last operation). Don't undo B's work!

This is called "LOCAL UNDO":
  Each user maintains their own undo stack.
  Undo reverses THEIR last operation, not the global last operation.
```

### Local Undo with OT

```
User A's undo stack: [Insert("Hello", 0)]
User B's undo stack: [Insert("World", 5)]

Document: "HelloWorld"

User A presses Undo:
  Inverse of Insert("Hello", 0) = Delete(0, 5)
  But B's insert shifted things! "World" is now at position 5.
  Transform the inverse against all operations that happened since:
    Delete(0, 5) transformed against Insert("World", 5) → Delete(0, 5)
    (No shift needed because B's insert was after A's text.)
  Apply: "HelloWorld" → "World"
  
  User A's "Hello" is undone. User B's "World" remains. ✓
```

---

## Offline Editing and Sync

### The Offline Challenge

```
User goes offline (subway, airplane, bad connection).
User continues editing locally.
When back online: Sync local changes with server.

Problem: Many operations may have happened on the server while offline.
  User made 50 local edits.
  Server received 200 edits from other users.
  Must merge all without conflicts.
```

### CRDT Advantage for Offline

```
With CRDTs: Offline is natural.
  Each edit creates a CRDT operation (insert/delete with character IDs).
  When reconnected: Send all local operations to server.
  Server merges using CRDT merge rules (commutative, idempotent).
  Convergence is GUARANTEED regardless of how many offline ops.

With OT: Offline is harder.
  Must transform ALL local ops against ALL server ops.
  If 50 local ops × 200 server ops = 10,000 transformations.
  Complex but doable. Google Docs handles this.
```

### Sync Protocol

```
Reconnection flow:
  1. Client reconnects.
  2. Client sends: "My last known version is V. I have 50 pending ops."
  3. Server sends: "Here are ops V+1 through V+200 that you missed."
  4. Client applies/transforms missed ops against local pending ops.
  5. Client sends its 50 pending ops (now transformed).
  6. Server applies them (possibly transforming against intervening ops).
  7. Both converge. Normal real-time flow resumes.
```

---

## Permissions and Access Control

### Permission Levels

```
Owner:   Full control (delete doc, manage permissions, edit, view)
Editor:  Can edit and view. Cannot delete or manage permissions.
Commenter: Can view and add comments. Cannot edit content.
Viewer:  Read-only access.

Implementation:
  Permissions checked at:
    1. HTTP layer: Can this user access this document?
    2. WebSocket layer: Can this user send operations?
    3. Operation layer: Is this operation allowed for this permission level?
    
  Viewer connects via WebSocket (to get real-time updates)
  but server rejects any operations they try to send.
```

### Sharing Model

```
  Document: "Quarterly Report"
  Owner: alice@company.com
  Editors: [bob@company.com, carol@company.com]
  Viewers: [link-based access with "anyone with the link"]
  
  Storage:
    documents table:      {doc_id, owner_id, title, created_at}
    permissions table:    {doc_id, user_id, role, granted_at}
    sharing_links table:  {doc_id, link_token, role, expires_at}
```

---

## Scaling Collaborative Sessions

### Per-Document Server Affinity

```
Each active document is "pinned" to one server (session server).
All WebSocket connections for that document route to the same server.

  Routing: WebSocket Gateway uses consistent hashing on doc_id.
    doc "abc123" → hash → Server 2
    doc "def456" → hash → Server 5
    doc "ghi789" → hash → Server 1

  Why pin to one server?
    OT requires a single-threaded operation ordering per document.
    Multiple servers handling the same doc → split-brain → divergence.
    One server per doc → simple, correct.
```

### Handling Server Failure

```
Server 2 dies (was handling doc "abc123"):
  1. WebSocket gateway detects failure (heartbeat timeout).
  2. Clients are disconnected.
  3. Another server takes ownership of doc "abc123".
  4. New server loads document from storage (latest snapshot + ops).
  5. Clients reconnect → routed to new server.
  6. Clients send their pending ops → server reconciles.
  
  Total failover time: 5-15 seconds.
  User experience: "Reconnecting..." indicator for a few seconds.
```

### Scaling to Millions of Documents

```
10M documents total, 100K active at any time.
Each session server handles ~1000 concurrent documents.
Need: 100 session servers.

  Memory per document: ~1 MB (document content + metadata + connection list)
  Memory per server: 1000 docs × 1 MB = 1 GB → easily fits.
  
  WebSocket connections:
    100K active docs × 5 users avg = 500K WebSocket connections.
    100 servers → 5K connections per server → manageable.
```

---

## Storage and Persistence

### Write-Ahead Operation Log

```
Every operation is written to a durable log BEFORE being acknowledged:

  1. Client sends operation → server receives.
  2. Server writes to operation log (Cassandra/DynamoDB).
  3. Server applies operation to in-memory document.
  4. Server broadcasts to other clients.
  5. Server ACKs the client.

Why write-ahead?
  If the server crashes between step 2 and step 4:
    Operation is in the log → can be replayed on recovery.
    No data loss even on crash.
```

### Periodic Persistence

```
In-memory document state is periodically saved:

  Every 5 minutes OR every 100 operations:
    Serialize document state → save to S3 as a snapshot.
    Record the snapshot version in the database.
    
  This bounds recovery time:
    Worst case: Load latest snapshot + replay 100 ops (< 1 second).
    Without snapshots: Replay ALL ops from the beginning (could take minutes).
```

---

## Performance and Latency

### Latency Targets

```
Local keystroke → visible on screen:    < 16ms (60 FPS frame budget)
Local edit → visible to other users:    < 200ms (feels "real-time")
Server round-trip:                       50-150ms (depending on region)
Reconnection after disconnect:           < 5 seconds
Document open time:                      < 2 seconds (initial load)
```

### Optimization Techniques

```
1. Local-first: Apply edits locally before sending to server.
   User sees their own keystrokes instantly (0ms perceived latency).

2. Operation batching: Bundle multiple keystrokes into one message.
   Don't send a WebSocket message per character.
   Bundle into 50ms intervals: "Hel" → one message, not three.

3. Compression: Compress operation messages (delta encoding).
   Instead of full document, send only the changed operations.

4. Lazy loading: Don't load entire document for large docs.
   Load visible section first, load rest on scroll.

5. Presence throttling: Don't send cursor updates on every keystroke.
   Throttle to every 50ms. Smooth animation on the receiving end.
```

---

## Real-World Production Patterns

### Pattern 1: Google Docs (OT-Based)

```
Architecture:
  - Jupiter OT algorithm (proprietary variant of OT)
  - Server-authoritative: Single server per document resolves conflicts
  - Operations: Rich-text deltas (insert, delete, format)
  - Transport: WebSocket with long-polling fallback
  - Storage: Bigtable for operation log, Colossus for snapshots
  
Scale:
  - 1B+ documents
  - Millions of concurrent collaborative sessions
  - < 200ms edit propagation globally

Key decisions:
  - Server resolves ALL conflicts (centralized authority)
  - Clients apply optimistically, reconcile on server ACK
  - 30-second autosave + operation log for durability
```

### Pattern 2: Figma (CRDT-Based)

```
Architecture:
  - Custom CRDT for design documents (components, layers, vectors)
  - Not just text — 2D spatial operations (move, resize, group)
  - Client-side CRDT merge (Rust/WASM in browser)
  - Server for persistence + relay
  
Scale:
  - Millions of design files
  - Real-time collaboration on complex 2D canvases
  - 60 FPS rendering with concurrent edits

Key decisions:
  - CRDT for offline support and complex data structures
  - Multiplayer cursors with 50ms updates
  - Incremental rendering (only re-render changed elements)
```

### Pattern 3: Notion (CRDT + Block-Based)

```
Architecture:
  - Block-based document model (each paragraph, heading, list = a block)
  - Blocks form a tree structure (nested blocks for indentation)
  - CRDT for block ordering and content within blocks
  - PostgreSQL for metadata, S3 for content
  
Scale:
  - 30M+ users
  - Complex document structures (databases, kanban, wikis)
  
Key decisions:
  - Block granularity (not character-level OT)
  - Each block is independently editable
  - Concurrent edits to different blocks don't conflict
  - Same-block conflicts use CRDT merge
```

### Pattern 4: VS Code Live Share (OT-Based)

```
Architecture:
  - OT for code editing (language-aware operations)
  - Shared terminal, debugger, server
  - Azure Relay for WebSocket connectivity
  - Guest editors run in their own VS Code (not a web editor)
  
Key decisions:
  - Language-server aware editing (autocomplete, refactoring)
  - Permission model: read-only or read-write per file
  - Cursor following: "Follow" another user's cursor
```

---

## Interview Talking Points & Scripts

### Script 1: Core Architecture

> *"For collaborative editing, I'd use a WebSocket-based architecture with document session servers. Each active document is pinned to one server via consistent hashing. Clients connect through a WebSocket gateway, which routes them to the correct session server. The server maintains the document state in memory, applies and transforms operations using OT, broadcasts changes to all connected clients, and periodically persists snapshots to storage. Local edits are applied immediately for responsiveness, then reconciled when the server acknowledges."*

### Script 2: OT vs CRDT Choice

> *"I'd choose between OT and CRDT based on the requirements. OT is proven at scale by Google Docs — it requires a central server for operation ordering but has lower memory overhead. CRDTs (like Yjs) are mathematically guaranteed to converge without a central server, making them better for offline editing and peer-to-peer scenarios. For a Google Docs clone with reliable servers, OT is fine. For a mobile-first app with offline support, I'd use CRDTs. In either case, local edits are applied instantly and synced asynchronously."*

### Script 3: Conflict Resolution

> *"Conflicts are resolved via transformation. If User A inserts at position 5 and User B deletes at position 3 concurrently, B's delete shifts A's insert position from 5 to 4 — because one character before position 5 was removed. The server applies these transformations to ensure all clients converge to the same document state. The key guarantee is that every user's intention is preserved — if you typed 'Hello', it stays 'Hello' regardless of concurrent edits around it."*

### Script 4: Scaling

> *"Each document is handled by a single session server for operation ordering. With consistent hashing, I'd route all WebSocket connections for a document to the same server. For 100K active documents with 5 users each (500K connections), 100 session servers handle 5K connections each — well within capacity. If a server fails, the WebSocket gateway detects it, another server loads the document from the last snapshot plus the operation log, and clients reconnect within seconds."*

### Script 5: Offline and History

> *"For offline editing, I'd use CRDTs so operations can be applied locally without a server and merged on reconnection. The merge is conflict-free by design. For version history, I store all operations in an append-only log (Cassandra) and take document snapshots every 100 operations. Users can browse revision history grouped by (user, 5-minute window), view any past version, or restore it. The operation log is the source of truth — snapshots are just an optimization for fast reconstruction."*

### Script 6: Presence and Cursors

> *"Cursor and presence awareness runs over the same WebSocket connection. Each client sends cursor position throttled to 50ms intervals. The server broadcasts cursor updates to all other clients in the session. When an operation is applied, all cursor positions are transformed accordingly — if someone inserts text before your cursor, your cursor shifts right. Users see colored cursors with names, creating the collaborative 'Google Docs' experience."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Just lock the paragraph being edited"

**Bad**: Pessimistic locking prevents concurrent editing entirely.
**Fix**: OT or CRDT allows true simultaneous editing without locks. Every character is independently editable.

### ❌ Mistake 2: "Last write wins"

**Bad**: Overwriting one user's changes with another's → data loss.
**Fix**: Transform/merge operations so ALL users' changes are preserved.

### ❌ Mistake 3: Ignoring local-first editing

**Bad**: "Send every keystroke to the server and wait for confirmation."
**Fix**: Apply locally first (0ms latency), then sync. Reconcile on server ACK.

### ❌ Mistake 4: Not explaining convergence

**Bad**: "Users edit and it syncs somehow."
**Fix**: Explicitly state: "OT transforms operations so all replicas converge to the same state regardless of operation arrival order."

### ❌ Mistake 5: Single server for all documents

**Bad**: "One collaboration server handles all documents."
**Fix**: Per-document routing. Consistent hashing → each doc on one server. Scales horizontally.

### ❌ Mistake 6: Not mentioning cursor/presence

**Bad**: Collaborative editing without showing other users' cursors.
**Fix**: Cursor awareness via throttled position broadcasts (50ms). Transform positions on edits.

### ❌ Mistake 7: No offline story

**Bad**: "If the user goes offline, edits are lost."
**Fix**: Queue local operations, sync on reconnection. CRDTs handle this naturally. OT requires transformation against missed ops.

### ❌ Mistake 8: Confusing OT and CRDT

**Bad**: Mixing up how they work or saying they're the same.
**Fix**: OT = transform operations relative to each other (needs server ordering). CRDT = data structure guarantees convergence without transformation (works peer-to-peer).

---

## Collaborative Editing by System Design Problem

| Problem | Approach | Data Model | Key Considerations |
|---|---|---|---|
| **Google Docs** | OT | Rich-text deltas | Server-authoritative, undo per user |
| **Figma** | CRDT | 2D canvas objects | Spatial operations, 60 FPS |
| **Notion** | CRDT (block) | Block tree | Block-level granularity, nested |
| **VS Code Live Share** | OT | Source code files | Language-aware, file-level permissions |
| **Google Sheets** | OT | Cell-based grid | Formula dependencies, cell ranges |
| **Miro (whiteboard)** | CRDT | 2D objects | Free-form spatial, large canvases |
| **Confluence** | OT | Rich text + macros | Block-level locking for macros |
| **Multiplayer Game** | State sync | Entity positions | Interpolation, lag compensation |
| **Shared Todo List** | CRDT | List items | Simple merge, offline-first |
| **Chat Messages** | Append-only | Message log | No conflict (append is commutative) |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│            COLLABORATIVE EDITING — CHEAT SHEET                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  GUARANTEES:                                                         │
│    1. Convergence: All users see the same final document.            │
│    2. Intention preservation: Each edit does what was intended.       │
│    3. Responsiveness: Local edits appear instantly (< 50ms).         │
│                                                                      │
│  OT (Operational Transformation):                                    │
│    Transform concurrent ops so they apply correctly in any order.    │
│    Requires central server for operation ordering.                   │
│    Used by: Google Docs, Microsoft Office Online.                    │
│    Pros: Low memory, proven at scale.                                │
│    Cons: Complex transforms, server-dependent.                       │
│                                                                      │
│  CRDT (Conflict-Free Replicated Data Types):                         │
│    Data structure designed so merge is always conflict-free.          │
│    No central server needed (peer-to-peer possible).                 │
│    Used by: Figma, Notion, Linear.                                   │
│    Pros: Offline-first, proven convergence, no server authority.     │
│    Cons: Higher memory (tombstones, char IDs), larger messages.      │
│                                                                      │
│  ARCHITECTURE:                                                       │
│    Clients ↔ WebSocket Gateway ↔ Document Session Server ↔ Storage   │
│    One session server per document (consistent hashing).             │
│    Local-first: Apply optimistically, reconcile on ACK.              │
│                                                                      │
│  CURSOR/PRESENCE:                                                    │
│    Throttled cursor broadcasts (50ms). Colored cursors per user.     │
│    Transform cursor positions when operations applied.               │
│                                                                      │
│  VERSIONING:                                                         │
│    Operation log (append-only) + periodic snapshots.                 │
│    Reconstruct any version: snapshot + replay ops.                   │
│    Revision history grouped by (user, time window).                  │
│                                                                      │
│  UNDO:                                                               │
│    Local undo: Each user undoes their OWN last operation.            │
│    Transform the inverse against all subsequent operations.          │
│                                                                      │
│  OFFLINE:                                                            │
│    CRDTs: Natural (merge on reconnect, convergence guaranteed).      │
│    OT: Queue local ops, transform against missed server ops.         │
│                                                                      │
│  SCALING:                                                            │
│    Per-document routing via consistent hashing.                      │
│    100K active docs × 5 users = 500K connections.                    │
│    100 session servers (5K connections each).                        │
│    Failover: Load snapshot + op log, clients reconnect.              │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 37: WebSocket and Real-Time at Scale** — Transport layer for collaborative editing
- **Topic 4: Consistency Models** — Eventual consistency in CRDTs
- **Topic 21: Concurrency Control** — Optimistic concurrency (OT is a form of it)
- **Topic 22: Delivery Guarantees** — Exactly-once operation delivery
- **Topic 61: ZooKeeper & Configuration** — Session server coordination

---

*This document is part of the System Design Interview Deep Dive series.*
