# Google Docs - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Challenges](#core-challenges)
4. [Core Entities and Relationships](#core-entities-and-relationships)
5. [Document Data Structure](#document-data-structure)
6. [Operational Transformation](#operational-transformation)
7. [Class Design](#class-design)
8. [Complete Implementation](#complete-implementation)
9. [Extensibility](#extensibility)
10. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is Google Docs?**

Google Docs is a collaborative online document editor that allows multiple users to simultaneously edit the same document in real-time. Changes are synchronized instantly across all clients, conflicts are automatically resolved, and complete version history is maintained.

**Real-World Examples:**
- Google Docs
- Microsoft Office 365 (Word Online)
- Notion
- Quip
- Confluence

**Core Workflow:**
```
User Opens Doc → Makes Edit → Edit Sent to Server → Server Resolves Conflicts
    ↓
Server Broadcasts to Other Users → All Users See Same Content
    ↓
Version Saved → History Maintained → Can Revert to Any Version
```

**Key Challenges:**
- **Concurrent Editing:** Multiple users editing simultaneously
- **Conflict Resolution:** Maintaining consistency when edits conflict
- **Real-Time Sync:** Sub-second latency for updates
- **Version Control:** Complete edit history with rollback
- **Permissions:** Owner, editor, commenter, viewer roles
- **Rich Text:** Formatting, styles, images, tables
- **Comments & Suggestions:** Collaborative feedback

---

## Requirements

### Functional Requirements

1. **Document Management**
   - Create, read, update, delete documents
   - Document metadata (title, owner, created/modified time)
   - Folder organization (optional for basic LLD)

2. **Text Editing**
   - Insert character at position
   - Delete character at position
   - Support rich text formatting (bold, italic, underline)
   - Undo/redo operations

3. **Real-Time Collaboration**
   - Multiple users editing simultaneously
   - See other users' cursors
   - Auto-save changes
   - Conflict resolution (Operational Transformation or CRDT)

4. **Version Control**
   - Save document versions
   - View version history
   - Restore previous version
   - Compare versions

5. **Permissions & Sharing**
   - Owner can share document
   - Permission levels: Owner, Editor, Commenter, Viewer
   - View who has access
   - Revoke access

6. **Comments & Suggestions**
   - Add comments on text ranges
   - Reply to comments
   - Resolve comments
   - Suggest mode (track changes)

### Non-Functional Requirements

1. **Performance**
   - Edit latency: < 100ms
   - Sync latency: < 500ms
   - Support 100+ concurrent editors

2. **Consistency**
   - Eventual consistency for all clients
   - No data loss
   - Deterministic conflict resolution

3. **Scalability**
   - Handle documents with millions of characters
   - Thousands of versions
   - Hundreds of collaborators

4. **Reliability**
   - Auto-save every few seconds
   - Offline editing support
   - Graceful conflict resolution

### Out of Scope

- File format import/export (Word, PDF)
- Advanced formatting (tables, images, charts)
- Spell check and grammar
- Voice typing
- Translation
- Add-ons and extensions
- Mobile-specific features

---

## Core Challenges

### 1. Concurrent Editing Problem

**Scenario:**
```
Initial doc: "Hello"
User A at position 5: inserts " World" → "Hello World"
User B at position 5: inserts " There" → "Hello There"

Both happen simultaneously. What's the final result?
```

**Solutions:**
1. **Operational Transformation (OT)** - Transform operations
2. **CRDT (Conflict-Free Replicated Data Types)** - Commutative operations
3. **Locking** - One editor at a time (bad UX)

### 2. Operational Transformation (OT)

**Concept:** Transform operations to account for concurrent changes

**Example:**
```
Initial: "abc"
Op1: Insert('x', 1) → "axbc"  (User A)
Op2: Insert('y', 2) → "abyc"  (User B)

Without OT: Inconsistent states
With OT: Transform Op2 to Insert('y', 3) → "axybc" (consistent!)
```

**Transformation Rules:**
```
If Op1 = Insert(char, pos1) and Op2 = Insert(char, pos2):
  If pos2 >= pos1: Transform Op2 to Insert(char, pos2 + 1)
  
If Op1 = Delete(pos1) and Op2 = Insert(char, pos2):
  If pos2 > pos1: Transform Op2 to Insert(char, pos2 - 1)
```

---

## Core Entities and Relationships

### Key Entities

1. **Document**
   - Document ID, title, content
   - Owner, collaborators
   - Created/modified timestamps
   - Current version number

2. **User**
   - User ID, name, email
   - Cursor position in document
   - Permission level

3. **Operation**
   - Operation ID, type (Insert, Delete, Format)
   - Position, character/text
   - User ID, timestamp
   - Version number

4. **Version**
   - Version number
   - Snapshot of document content
   - Timestamp, author
   - Operation that created it

5. **Permission**
   - User ID, document ID
   - Role (Owner, Editor, Commenter, Viewer)
   - Granted by, granted at

6. **Comment**
   - Comment ID, text
   - Text range (start, end positions)
   - Author, timestamp
   - Replies, resolved status

7. **Cursor**
   - User ID
   - Position in document
   - Selection range

### Entity Relationships

```
┌─────────────┐      owns       ┌──────────────┐
│    User     │────────────────►│   Document   │
└──────┬──────┘                 └──────┬───────┘
       │                               │
       │ has permission                │ contains
       │                               │
       ▼                               ▼
┌─────────────┐                 ┌──────────────┐
│ Permission  │                 │   Version    │
└─────────────┘                 └──────────────┘

┌──────────────┐    applied to   ┌──────────────┐
│  Operation   │────────────────►│   Document   │
└──────────────┘                 └──────────────┘

┌──────────────┐    attached to  ┌──────────────┐
│   Comment    │────────────────►│   Document   │
└──────────────┘                 └──────────────┘

┌──────────────┐    tracked in   ┌──────────────┐
│    Cursor    │────────────────►│   Document   │
└──────────────┘                 └──────────────┘
```

---

## Document Data Structure

### Approach 1: Simple String (Baseline)

```java
class Document {
    private StringBuilder content;
    
    public void insert(int position, char c) {
        content.insert(position, c);
    }
    
    public void delete(int position) {
        content.deleteCharAt(position);
    }
}
```

**Pros:** Simple
**Cons:** O(n) for insert/delete, no formatting

### Approach 2: Gap Buffer (Editor Optimization)

```java
class GapBuffer {
    private char[] buffer;
    private int gapStart;
    private int gapEnd;
    
    // Gap is positioned at cursor
    // Insert/delete at cursor is O(1)
}
```

**Pros:** Fast for sequential edits
**Cons:** Slow for random access

### Approach 3: Piece Table (Best for Large Docs)

```java
class PieceTable {
    private final String original;  // Original content (immutable)
    private final StringBuilder add; // Added content
    private final List<Piece> pieces; // Sequence of pieces
    
    static class Piece {
        boolean isOriginal;  // From original or add buffer
        int start;           // Start index in buffer
        int length;          // Length of piece
    }
}
```

**Pros:** 
- O(log n) insert/delete with balanced tree
- Efficient undo/redo
- Memory efficient

**Cons:** More complex implementation

### Our Choice: Simplified Character List

For LLD interviews, use simple approach with focus on OT/CRDT logic:

```java
class Document {
    private final List<Character> content;
    private int version;
    
    public void applyOperation(Operation op) {
        if (op.type == INSERT) {
            content.add(op.position, op.character);
        } else if (op.type == DELETE) {
            content.remove(op.position);
        }
        version++;
    }
}
```

---

## Operational Transformation

### Operation Types

```java
enum OperationType {
    INSERT,  // Insert character at position
    DELETE,  // Delete character at position
    FORMAT   // Apply formatting (bold, italic, etc.)
}

class Operation {
    String id;
    OperationType type;
    int position;
    char character;  // For INSERT
    String userId;
    long timestamp;
    int version;     // Document version when created
}
```

### Transformation Algorithm

```java
class OperationalTransformer {
    /**
     * Transform op2 against op1
     * Returns transformed version of op2
     */
    public Operation transform(Operation op1, Operation op2) {
        // Both inserts
        if (op1.type == INSERT && op2.type == INSERT) {
            if (op2.position > op1.position) {
                // op2 is after op1, shift right
                return new Operation(op2.type, op2.position + 1, op2.character);
            } else if (op2.position == op1.position) {
                // Same position, use timestamp to break tie
                if (op2.timestamp > op1.timestamp) {
                    return new Operation(op2.type, op2.position + 1, op2.character);
                }
            }
        }
        
        // Insert vs Delete
        if (op1.type == INSERT && op2.type == DELETE) {
            if (op2.position >= op1.position) {
                return new Operation(op2.type, op2.position + 1, '\0');
            }
        }
        
        if (op1.type == DELETE && op2.type == INSERT) {
            if (op2.position > op1.position) {
                return new Operation(op2.type, op2.position - 1, op2.character);
            }
        }
        
        // Both deletes
        if (op1.type == DELETE && op2.type == DELETE) {
            if (op2.position > op1.position) {
                return new Operation(op2.type, op2.position - 1, '\0');
            } else if (op2.position == op1.position) {
                return null; // Same position deleted, no-op
            }
        }
        
        return op2; // No transformation needed
    }
}
```

### OT Example Walkthrough

```
Initial: "abc"
Version: 0

User A: Insert('x', 1) at v0 → "axbc"
User B: Insert('y', 2) at v0 → "abyc"

Server receives A's op first:
1. Apply op A: "abc" → "axbc" (v1)
2. Broadcast op A to B

B receives op A:
1. Transform B's op: Insert('y', 2) → Insert('y', 3)
   (because A inserted before B's position)
2. Apply transformed op: "axbc" → "axybc"

Server receives B's op:
1. Transform against A's op: Insert('y', 2) → Insert('y', 3)
2. Apply: "axbc" → "axby" (v2)

Final state everywhere: "axybc" ✓ Consistent!
```

---

## Class Design

### 1. Document

```java
/**
 * Document with content and metadata
 */
public class Document {
    private final String id;
    private String title;
    private final List<Character> content;
    private int version;
    private final String ownerId;
    private final Map<String, Permission> permissions;
    private final List<Version> versionHistory;
    private final List<Comment> comments;
    private final long createdAt;
    private long modifiedAt;
    
    public Document(String id, String title, String ownerId) {
        this.id = id;
        this.title = title;
        this.content = new ArrayList<>();
        this.version = 0;
        this.ownerId = ownerId;
        this.permissions = new ConcurrentHashMap<>();
        this.versionHistory = new ArrayList<>();
        this.comments = new CopyOnWriteArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = createdAt;
        
        // Owner has full permissions
        permissions.put(ownerId, new Permission(ownerId, PermissionLevel.OWNER));
    }
    
    public synchronized void applyOperation(Operation op) {
        if (op.getType() == OperationType.INSERT) {
            content.add(op.getPosition(), op.getCharacter());
        } else if (op.getType() == OperationType.DELETE) {
            if (op.getPosition() < content.size()) {
                content.remove(op.getPosition());
            }
        }
        
        version++;
        modifiedAt = System.currentTimeMillis();
        
        // Save version every N operations
        if (version % 10 == 0) {
            saveVersion();
        }
    }
    
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (char c : content) {
            sb.append(c);
        }
        return sb.toString();
    }
    
    private void saveVersion() {
        versionHistory.add(new Version(version, getContent(), System.currentTimeMillis()));
    }
    
    // Permission management
    public void grantPermission(String userId, PermissionLevel level) {
        permissions.put(userId, new Permission(userId, level));
    }
    
    public boolean hasPermission(String userId, PermissionLevel required) {
        Permission perm = permissions.get(userId);
        return perm != null && perm.getLevel().ordinal() >= required.ordinal();
    }
    
    // Getters
}
```

### 2. Operation

```java
/**
 * Edit operation on document
 */
public class Operation {
    private final String id;
    private final OperationType type;
    private final int position;
    private final Character character; // For INSERT
    private final String userId;
    private final long timestamp;
    private final int version; // Document version when created
    
    public enum OperationType {
        INSERT, DELETE, FORMAT
    }
    
    public Operation(String id, OperationType type, int position, 
                     Character character, String userId, int version) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.character = character;
        this.userId = userId;
        this.timestamp = System.currentTimeMillis();
        this.version = version;
    }
    
    // Getters
}
```

### 3. Permission

```java
/**
 * Permission levels for document access
 */
public enum PermissionLevel {
    VIEWER(0),      // Can only view
    COMMENTER(1),   // Can view and comment
    EDITOR(2),      // Can view, comment, and edit
    OWNER(3);       // Full control
    
    private final int level;
    
    PermissionLevel(int level) {
        this.level = level;
    }
    
    public boolean canEdit() {
        return level >= EDITOR.level;
    }
    
    public boolean canComment() {
        return level >= COMMENTER.level;
    }
}

public class Permission {
    private final String userId;
    private final PermissionLevel level;
    private final long grantedAt;
    
    public Permission(String userId, PermissionLevel level) {
        this.userId = userId;
        this.level = level;
        this.grantedAt = System.currentTimeMillis();
    }
    
    // Getters
}
```

### 4. Version

```java
/**
 * Document version snapshot
 */
public class Version {
    private final int versionNumber;
    private final String content;
    private final long timestamp;
    private final String author;
    
    public Version(int versionNumber, String content, long timestamp) {
        this(versionNumber, content, timestamp, null);
    }
    
    public Version(int versionNumber, String content, long timestamp, String author) {
        this.versionNumber = versionNumber;
        this.content = content;
        this.timestamp = timestamp;
        this.author = author;
    }
    
    // Getters
}
```

### 5. Comment

```java
/**
 * Comment on document text range
 */
public class Comment {
    private final String id;
    private final String documentId;
    private final String authorId;
    private final String text;
    private final int startPosition;
    private final int endPosition;
    private final long timestamp;
    private final List<Comment> replies;
    private boolean resolved;
    
    public Comment(String id, String documentId, String authorId,
                   String text, int startPos, int endPos) {
        this.id = id;
        this.documentId = documentId;
        this.authorId = authorId;
        this.text = text;
        this.startPosition = startPos;
        this.endPosition = endPos;
        this.timestamp = System.currentTimeMillis();
        this.replies = new ArrayList<>();
        this.resolved = false;
    }
    
    public void addReply(Comment reply) {
        replies.add(reply);
    }
    
    public void resolve() {
        resolved = true;
    }
    
    // Getters
}
```

### 6. Cursor

```java
/**
 * User cursor position in document
 */
public class Cursor {
    private final String userId;
    private int position;
    private Integer selectionStart; // null if no selection
    private Integer selectionEnd;
    private long lastUpdated;
    
    public Cursor(String userId, int position) {
        this.userId = userId;
        this.position = position;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public void updatePosition(int newPosition) {
        this.position = newPosition;
        this.selectionStart = null;
        this.selectionEnd = null;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public void setSelection(int start, int end) {
        this.position = start;
        this.selectionStart = start;
        this.selectionEnd = end;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    // Getters
}
```

---

## Complete Implementation

### GoogleDocsService - Main Coordinator

```java
/**
 * Main service managing documents and collaboration
 */
public class GoogleDocsService {
    private final Map<String, Document> documents;
    private final Map<String, User> users;
    private final OperationalTransformer transformer;
    private final Map<String, List<Cursor>> documentCursors; // Document ID → Cursors
    
    public GoogleDocsService() {
        this.documents = new ConcurrentHashMap<>();
        this.users = new ConcurrentHashMap<>();
        this.transformer = new OperationalTransformer();
        this.documentCursors = new ConcurrentHashMap<>();
    }
    
    // Document Management
    public Document createDocument(String userId, String title) {
        String docId = UUID.randomUUID().toString();
        Document doc = new Document(docId, title, userId);
        documents.put(docId, doc);
        return doc;
    }
    
    public Document getDocument(String documentId) {
        return documents.get(documentId);
    }
    
    // Edit Operations
    public void insertCharacter(String documentId, String userId, 
                                int position, char character) {
        Document doc = documents.get(documentId);
        if (doc == null || !doc.hasPermission(userId, PermissionLevel.EDITOR)) {
            return;
        }
        
        Operation op = new Operation(
            UUID.randomUUID().toString(),
            OperationType.INSERT,
            position,
            character,
            userId,
            doc.getVersion()
        );
        
        applyAndBroadcast(doc, op);
    }
    
    public void deleteCharacter(String documentId, String userId, int position) {
        Document doc = documents.get(documentId);
        if (doc == null || !doc.hasPermission(userId, PermissionLevel.EDITOR)) {
            return;
        }
        
        Operation op = new Operation(
            UUID.randomUUID().toString(),
            OperationType.DELETE,
            position,
            null,
            userId,
            doc.getVersion()
        );
        
        applyAndBroadcast(doc, op);
    }
    
    private void applyAndBroadcast(Document doc, Operation op) {
        // Apply operation
        doc.applyOperation(op);
        
        // Broadcast to all active users (in production, use WebSocket)
        broadcastOperation(doc.getId(), op);
    }
    
    private void broadcastOperation(String documentId, Operation op) {
        // In production: Send via WebSocket to all connected clients
        System.out.println("[Broadcast] Op " + op.getType() + " at pos " + 
                          op.getPosition() + " to document " + documentId);
    }
    
    // Permission Management
    public void shareDocument(String documentId, String ownerId, 
                             String targetUserId, PermissionLevel level) {
        Document doc = documents.get(documentId);
        if (doc == null || !doc.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Not document owner");
        }
        
        doc.grantPermission(targetUserId, level);
    }
    
    // Cursor Tracking
    public void updateCursor(String documentId, String userId, int position) {
        List<Cursor> cursors = documentCursors.computeIfAbsent(
            documentId, k -> new CopyOnWriteArrayList<>());
        
        // Find or create cursor for user
        Cursor cursor = cursors.stream()
            .filter(c -> c.getUserId().equals(userId))
            .findFirst()
            .orElse(null);
        
        if (cursor == null) {
            cursor = new Cursor(userId, position);
            cursors.add(cursor);
        } else {
            cursor.updatePosition(position);
        }
    }
    
    public List<Cursor> getActiveCursors(String documentId) {
        return documentCursors.getOrDefault(documentId, Collections.emptyList());
    }
    
    // Comments
    public Comment addComment(String documentId, String userId,
                             String text, int startPos, int endPos) {
        Document doc = documents.get(documentId);
        if (doc == null || !doc.hasPermission(userId, PermissionLevel.COMMENTER)) {
            return null;
        }
        
        Comment comment = new Comment(
            UUID.randomUUID().toString(),
            documentId,
            userId,
            text,
            startPos,
            endPos
        );
        
        doc.addComment(comment);
        return comment;
    }
    
    // Version Control
    public List<Version> getVersionHistory(String documentId) {
        Document doc = documents.get(documentId);
        return doc != null ? doc.getVersionHistory() : Collections.emptyList();
    }
    
    public boolean restoreVersion(String documentId, int versionNumber) {
        Document doc = documents.get(documentId);
        if (doc == null) {
            return false;
        }
        
        Version version = doc.getVersionHistory().stream()
            .filter(v -> v.getVersionNumber() == versionNumber)
            .findFirst()
            .orElse(null);
        
        if (version != null) {
            doc.restoreFromVersion(version);
            return true;
        }
        
        return false;
    }
}
```

---

## Extensibility

### 1. Rich Text Formatting

```java
class FormattedText {
    private final char character;
    private final TextFormat format;
    
    public static class TextFormat {
        boolean bold;
        boolean italic;
        boolean underline;
        String color;
        int fontSize;
        
        public TextFormat merge(TextFormat other) {
            // Merge formatting
            return this;
        }
    }
}

class FormattedDocument extends Document {
    private final List<FormattedText> formattedContent;
    
    public void applyFormat(int start, int end, TextFormat format) {
        for (int i = start; i < end && i < formattedContent.size(); i++) {
            FormattedText ft = formattedContent.get(i);
            formattedContent.set(i, new FormattedText(
                ft.character,
                ft.format.merge(format)
            ));
        }
    }
}
```

### 2. CRDT Alternative (Conflict-Free Replicated Data Type)

```java
class CRDTDocument {
    // Each character has unique ID (site ID + counter)
    private final List<CRDTChar> characters;
    
    static class CRDTChar {
        char value;
        UniqueId id;
        boolean deleted; // Tombstone for deletes
        
        static class UniqueId {
            String siteId;  // User/client ID
            int counter;    // Monotonic counter
            
            // IDs are comparable and determine order
        }
    }
    
    public void insert(char c, int position, String siteId, int counter) {
        // Generate unique ID between previous and next character
        // No transformation needed - IDs determine final order!
    }
}
```

### 3. Suggestion Mode (Track Changes)

```java
class Suggestion {
    private final String id;
    private final String authorId;
    private final OperationType type;
    private final int position;
    private final String originalText;
    private final String suggestedText;
    private SuggestionStatus status;
    
    public enum SuggestionStatus {
        PENDING, ACCEPTED, REJECTED
    }
    
    public void accept() {
        status = SuggestionStatus.ACCEPTED;
        // Apply the change to document
    }
    
    public void reject() {
        status = SuggestionStatus.REJECTED;
        // Discard the suggestion
    }
}
```

### 4. Presence Awareness

```java
class PresenceManager {
    private final Map<String, Map<String, UserPresence>> documentPresence;
    // Document ID → (User ID → Presence)
    
    static class UserPresence {
        String userId;
        Cursor cursor;
        long lastActivity;
        boolean isTyping;
        
        public boolean isActive() {
            return System.currentTimeMillis() - lastActivity < 30000; // 30 sec
        }
    }
    
    public List<UserPresence> getActiveUsers(String documentId) {
        Map<String, UserPresence> presence = documentPresence.get(documentId);
        if (presence == null) {
            return Collections.emptyList();
        }
        
        return presence.values().stream()
            .filter(UserPresence::isActive)
            .collect(Collectors.toList());
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- Real-time collaboration required?
- Conflict resolution approach (OT vs CRDT)?
- Rich text formatting needed?
- Version control depth?
- Scale (users, document size)?

**2. Start with Simple Document (5 minutes)**
- Basic CRUD operations
- Simple string or list of characters
- Single-user first

**3. Add Collaboration (15 minutes)**
- Operation abstraction
- Operational Transformation basics
- Broadcast mechanism
- Show conflict resolution example

**4. Add Features (10 minutes)**
- Permissions
- Comments
- Version control
- Cursor tracking

**5. Discuss Scaling (5 minutes)**
- WebSocket for real-time
- Database for persistence
- Caching strategies
- Conflict resolution at scale

### Common Interview Questions

**Q1: "How do you handle concurrent edits from multiple users?"**

**A:** Operational Transformation:
```
1. Each edit is an Operation (INSERT/DELETE at position)
2. Server maintains authoritative version
3. When receiving operation:
   a. Transform against all concurrent operations
   b. Apply to document
   c. Broadcast to all clients
4. Clients apply same transformations
5. Eventually consistent state
```

**Q2: "What if two users delete the same character?"**

**A:** Handle in transformation:
```java
if (op1.type == DELETE && op2.type == DELETE) {
    if (op2.position == op1.position) {
        return null; // No-op, already deleted
    }
    // Adjust position if op2 is after op1
}
```

**Q3: "How do you track cursor positions across edits?"**

**A:** Transform cursor positions like operations:
```java
void onOperationApplied(Operation op, List<Cursor> cursors) {
    for (Cursor cursor : cursors) {
        if (op.type == INSERT && cursor.position >= op.position) {
            cursor.position++;
        } else if (op.type == DELETE && cursor.position > op.position) {
            cursor.position--;
        }
    }
}
```

**Q4: "How to implement undo/redo?"**

**A:** Maintain operation history per user:
```java
class UndoManager {
    private final Stack<Operation> undoStack = new Stack<>();
    private final Stack<Operation> redoStack = new Stack<>();
    
    public void recordOperation(Operation op) {
        undoStack.push(op);
        redoStack.clear(); // Clear redo on new operation
    }
    
    public Operation undo() {
        if (undoStack.isEmpty()) return null;
        
        Operation op = undoStack.pop();
        redoStack.push(op);
        
        // Return inverse operation
        return op.inverse();
    }
    
    public Operation redo() {
        if (redoStack.isEmpty()) return null;
        
        Operation op = redoStack.pop();
        undoStack.push(op);
        return op;
    }
}
```

**Q5: "How to handle offline editing?"**

**A:** Queue operations locally, sync when online:
```java
class OfflineManager {
    private final Queue<Operation> pendingOperations = new LinkedList<>();
    
    public void queueOperation(Operation op) {
        pendingOperations.offer(op);
    }
    
    public void syncWhenOnline() {
        while (!pendingOperations.isEmpty()) {
            Operation op = pendingOperations.poll();
            sendToServer(op);
        }
    }
}
```

### Design Patterns Used

1. **Command Pattern:** Operations as objects
2. **Observer Pattern:** Broadcasting changes to clients
3. **Strategy Pattern:** Different conflict resolution strategies
4. **Memento Pattern:** Version history and restore
5. **Composite Pattern:** Document structure with nested elements

### Expected Level Performance

**Junior Engineer:**
- Basic document CRUD
- Simple insert/delete operations
- Single-user editing
- Basic version saving

**Mid-Level Engineer:**
- Operational Transformation basics
- Multi-user collaboration
- Permission system
- Comments and version control
- Explain OT with examples

**Senior Engineer:**
- Complete OT implementation
- CRDT alternative discussion
- Rich text formatting
- Offline editing support
- Performance optimization
- Distributed system considerations
- WebSocket integration

### Key Trade-offs

**1. Conflict Resolution**
- **OT:** Well-proven, complex transformation logic
- **CRDT:** Simpler, but larger memory footprint
- **Locking:** Simple but terrible UX

**2. Document Storage**
- **String:** Simple, slow for large docs
- **Piece Table:** Fast, complex
- **Character List:** Balanced for LLD

**3. Versioning**
- **Every operation:** Complete history, large storage
- **Periodic snapshots:** Less storage, less granular
- **Hybrid:** Snapshots + deltas

**4. Real-Time Sync**
- **WebSocket:** True real-time, complex
- **Long polling:** Simpler, higher latency
- **Hybrid:** WebSocket with fallback

### Algorithm Complexity

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Insert character | O(n) | With ArrayList |
| Delete character | O(n) | With ArrayList |
| Get document | O(n) | Build string from list |
| Apply operation | O(1) | Plus insert/delete time |
| Transform operation | O(1) | Simple position arithmetic |
| Find version | O(v) | v = number of versions |

**Optimization:** Use Piece Table or Rope data structure for O(log n) operations

---

## Summary

This Google Docs Low-Level Design demonstrates:

1. **Operational Transformation:** Core conflict resolution algorithm
2. **Real-Time Collaboration:** Multiple concurrent editors
3. **Permission System:** Owner, Editor, Commenter, Viewer roles
4. **Version Control:** Complete history with restore capability
5. **Comments:** Collaborative feedback on text ranges
6. **Cursor Tracking:** See where other users are editing

**Key Takeaways:**
- **OT is the heart** of collaborative editing
- **Transform concurrent operations** to maintain consistency
- **Version control** enables time travel
- **Permissions** control access levels
- **Real-time sync** requires WebSocket or similar
- **Cursors** enhance collaboration awareness

**Related Concepts:**
- CRDT (Conflict-Free Replicated Data Types)
- Piece Table and Rope data structures
- WebSocket for real-time communication
- Event sourcing for version history

**Real-World Applications:**
- Collaborative document editors
- Code editors (VS Code Live Share)
- Collaborative whiteboards (Miro, FigJam)
- Real-time note-taking (Notion, Evernote)

---

*Last Updated: January 5, 2026*
*Difficulty Level: Hard*
*Key Focus: Operational Transformation, Concurrency, Real-Time Collaboration*
*Company: Google (Senior Level - Most Challenging LLD Question)*
*Interview Success Rate: Medium (requires deep understanding of OT/CRDT)*
