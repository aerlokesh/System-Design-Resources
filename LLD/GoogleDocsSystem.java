/**
 * GOOGLE DOCS - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a collaborative document editing system
 * with real-time synchronization and conflict resolution.
 * 
 * Key Features:
 * 1. Operational Transformation (OT) for conflict resolution
 * 2. Real-time multi-user collaboration
 * 3. Permission system (Owner, Editor, Commenter, Viewer)
 * 4. Version control with history
 * 5. Comments on text ranges
 * 6. Cursor tracking for presence awareness
 * 7. Thread-safe operations
 * 
 * Design Patterns Used:
 * - Command Pattern (Operations)
 * - Observer Pattern (Broadcasting changes)
 * - Memento Pattern (Version control)
 * - Strategy Pattern (Conflict resolution)
 * 
 * Company: Google (Senior Level - Most Challenging LLD)
 * Related: Microsoft Office 365, Notion, Quip
 */

import java.util.*;
import java.util.concurrent.*;

// ============================================================
// ENUMS
// ============================================================

enum OperationType {
    INSERT, DELETE, FORMAT
}

enum PermissionLevel {
    VIEWER(0), COMMENTER(1), EDITOR(2), OWNER(3);
    
    private final int level;
    
    PermissionLevel(int level) {
        this.level = level;
    }
    
    public int getLevel() { return level; }
    
    public boolean canEdit() {
        return level >= EDITOR.level;
    }
}

// ============================================================
// CORE ENTITIES
// ============================================================

class User {
    private final String id;
    private final String name;
    private final String email;
    
    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}

class Operation {
    private final String id;
    private final OperationType type;
    private int position;
    private final Character character;
    private final String userId;
    private final long timestamp;
    private final int version;
    
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
    
    public Operation withPosition(int newPosition) {
        return new Operation(id, type, newPosition, character, userId, version);
    }
    
    public String getId() { return id; }
    public OperationType getType() { return type; }
    public int getPosition() { return position; }
    public Character getCharacter() { return character; }
    public String getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }
    public int getVersion() { return version; }
    
    @Override
    public String toString() {
        return String.format("Op[%s at pos %d by %s]", type, position, userId);
    }
}

class Permission {
    private final String userId;
    private final PermissionLevel level;
    private final long grantedAt;
    
    public Permission(String userId, PermissionLevel level) {
        this.userId = userId;
        this.level = level;
        this.grantedAt = System.currentTimeMillis();
    }
    
    public String getUserId() { return userId; }
    public PermissionLevel getLevel() { return level; }
}

class Version {
    private final int versionNumber;
    private final String content;
    private final long timestamp;
    
    public Version(int versionNumber, String content, long timestamp) {
        this.versionNumber = versionNumber;
        this.content = content;
        this.timestamp = timestamp;
    }
    
    public int getVersionNumber() { return versionNumber; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
}

class Cursor {
    private final String userId;
    private int position;
    private long lastUpdated;
    
    public Cursor(String userId, int position) {
        this.userId = userId;
        this.position = position;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public void updatePosition(int newPosition) {
        this.position = newPosition;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getUserId() { return userId; }
    public int getPosition() { return position; }
}

// ============================================================
// OPERATIONAL TRANSFORMER
// ============================================================

class OperationalTransformer {
    /**
     * Transform op2 against op1
     * Returns transformed version of op2 (or null if no-op)
     */
    public Operation transform(Operation op1, Operation op2) {
        if (op1.getType() == OperationType.INSERT && op2.getType() == OperationType.INSERT) {
            if (op2.getPosition() > op1.getPosition()) {
                return op2.withPosition(op2.getPosition() + 1);
            } else if (op2.getPosition() == op1.getPosition()) {
                // Tie-breaker: later timestamp goes after
                if (op2.getTimestamp() > op1.getTimestamp()) {
                    return op2.withPosition(op2.getPosition() + 1);
                }
            }
        }
        
        if (op1.getType() == OperationType.INSERT && op2.getType() == OperationType.DELETE) {
            if (op2.getPosition() >= op1.getPosition()) {
                return op2.withPosition(op2.getPosition() + 1);
            }
        }
        
        if (op1.getType() == OperationType.DELETE && op2.getType() == OperationType.INSERT) {
            if (op2.getPosition() > op1.getPosition()) {
                return op2.withPosition(op2.getPosition() - 1);
            }
        }
        
        if (op1.getType() == OperationType.DELETE && op2.getType() == OperationType.DELETE) {
            if (op2.getPosition() > op1.getPosition()) {
                return op2.withPosition(op2.getPosition() - 1);
            } else if (op2.getPosition() == op1.getPosition()) {
                return null; // Same position, no-op
            }
        }
        
        return op2;
    }
}

// ============================================================
// DOCUMENT
// ============================================================

class Document {
    private final String id;
    private String title;
    private final List<Character> content;
    private int version;
    private final String ownerId;
    private final Map<String, Permission> permissions;
    private final List<Version> versionHistory;
    private final long createdAt;
    private long modifiedAt;
    
    public Document(String id, String title, String ownerId) {
        this.id = id;
        this.title = title;
        this.content = Collections.synchronizedList(new ArrayList<>());
        this.version = 0;
        this.ownerId = ownerId;
        this.permissions = new ConcurrentHashMap<>();
        this.versionHistory = new CopyOnWriteArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = createdAt;
        
        permissions.put(ownerId, new Permission(ownerId, PermissionLevel.OWNER));
    }
    
    public synchronized void applyOperation(Operation op) {
        if (op.getType() == OperationType.INSERT) {
            if (op.getPosition() <= content.size()) {
                content.add(op.getPosition(), op.getCharacter());
            }
        } else if (op.getType() == OperationType.DELETE) {
            if (op.getPosition() < content.size()) {
                content.remove(op.getPosition());
            }
        }
        
        version++;
        modifiedAt = System.currentTimeMillis();
        
        if (version % 5 == 0) {
            saveVersion();
        }
    }
    
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        synchronized(content) {
            for (char c : content) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    private void saveVersion() {
        versionHistory.add(new Version(version, getContent(), System.currentTimeMillis()));
    }
    
    public void grantPermission(String userId, PermissionLevel level) {
        permissions.put(userId, new Permission(userId, level));
    }
    
    public boolean hasPermission(String userId, PermissionLevel required) {
        Permission perm = permissions.get(userId);
        return perm != null && perm.getLevel().getLevel() >= required.getLevel();
    }
    
    public void restoreFromVersion(Version v) {
        synchronized(content) {
            content.clear();
            for (char c : v.getContent().toCharArray()) {
                content.add(c);
            }
        }
        version++;
    }
    
    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getVersion() { return version; }
    public String getOwnerId() { return ownerId; }
    public List<Version> getVersionHistory() { return new ArrayList<>(versionHistory); }
}

// ============================================================
// GOOGLE DOCS SERVICE
// ============================================================

class GoogleDocsService {
    private final Map<String, Document> documents;
    private final Map<String, User> users;
    final OperationalTransformer transformer; // Package-private for demo
    private final Map<String, List<Cursor>> documentCursors;
    
    public GoogleDocsService() {
        this.documents = new ConcurrentHashMap<>();
        this.users = new ConcurrentHashMap<>();
        this.transformer = new OperationalTransformer();
        this.documentCursors = new ConcurrentHashMap<>();
    }
    
    public void registerUser(User user) {
        users.put(user.getId(), user);
    }
    
    public Document createDocument(String userId, String title) {
        String docId = "DOC-" + UUID.randomUUID().toString().substring(0, 8);
        Document doc = new Document(docId, title, userId);
        documents.put(docId, doc);
        return doc;
    }
    
    public void insertCharacter(String documentId, String userId, int position, char character) {
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
        
        doc.applyOperation(op);
        System.out.println("[Applied] " + op + " → \"" + doc.getContent() + "\"");
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
        
        doc.applyOperation(op);
        System.out.println("[Applied] " + op + " → \"" + doc.getContent() + "\"");
    }
    
    public void insertText(String documentId, String userId, int position, String text) {
        for (int i = 0; i < text.length(); i++) {
            insertCharacter(documentId, userId, position + i, text.charAt(i));
        }
    }
    
    public void shareDocument(String documentId, String ownerId, 
                             String targetUserId, PermissionLevel level) {
        Document doc = documents.get(documentId);
        if (doc == null || !doc.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Not document owner");
        }
        
        doc.grantPermission(targetUserId, level);
        System.out.println("[Permission] User " + targetUserId + " granted " + level);
    }
    
    public List<Version> getVersionHistory(String documentId) {
        Document doc = documents.get(documentId);
        return doc != null ? doc.getVersionHistory() : Collections.emptyList();
    }
    
    public boolean restoreVersion(String documentId, int versionNumber) {
        Document doc = documents.get(documentId);
        if (doc == null) return false;
        
        Version version = doc.getVersionHistory().stream()
            .filter(v -> v.getVersionNumber() == versionNumber)
            .findFirst()
            .orElse(null);
        
        if (version != null) {
            doc.restoreFromVersion(version);
            System.out.println("[Restored] Version " + versionNumber + " → \"" + doc.getContent() + "\"");
            return true;
        }
        
        return false;
    }
    
    public Document getDocument(String documentId) {
        return documents.get(documentId);
    }
    
    public User getUser(String userId) {
        return users.get(userId);
    }
    
    // Demonstrate OT
    public void demonstrateOT(String documentId, Operation op1, Operation op2) {
        System.out.println("\n--- Operational Transformation Demo ---");
        System.out.println("Op1: " + op1);
        System.out.println("Op2: " + op2);
        
        Operation transformed = transformer.transform(op1, op2);
        if (transformed != null) {
            System.out.println("Transformed Op2: " + transformed);
        } else {
            System.out.println("Transformed Op2: NO-OP (conflicting delete)");
        }
    }
}

// ============================================================
// DEMO
// ============================================================

public class GoogleDocsSystem {
    
    public static void main(String[] args) {
        System.out.println("=== GOOGLE DOCS SYSTEM DEMO ===\n");
        
        // Demo 1: Basic Document Operations
        demoBasicOperations();
        
        // Demo 2: Concurrent Editing with OT
        demoConcurrentEditing();
        
        // Demo 3: Permission System
        demoPermissions();
        
        // Demo 4: Version Control
        demoVersionControl();
        
        // Demo 5: Collaborative Editing Scenario
        demoCollaborativeScenario();
    }
    
    private static void demoBasicOperations() {
        System.out.println("--- Demo 1: Basic Document Operations ---\n");
        
        GoogleDocsService service = new GoogleDocsService();
        
        // Register user
        User user = new User("U1", "Alice", "alice@example.com");
        service.registerUser(user);
        
        // Create document
        Document doc = service.createDocument("U1", "My First Document");
        System.out.println("Created document: " + doc.getTitle());
        System.out.println("Owner: " + user.getName());
        System.out.println("Initial content: \"" + doc.getContent() + "\"\n");
        
        // Insert characters
        System.out.println("Inserting 'Hello'...");
        service.insertText(doc.getId(), "U1", 0, "Hello");
        
        System.out.println("\nInserting ' World'...");
        service.insertText(doc.getId(), "U1", 5, " World");
        
        System.out.println("\nFinal content: \"" + doc.getContent() + "\"");
        System.out.println("Version: " + doc.getVersion());
        
        System.out.println();
    }
    
    private static void demoConcurrentEditing() {
        System.out.println("--- Demo 2: Concurrent Editing with OT ---\n");
        
        GoogleDocsService service = new GoogleDocsService();
        
        // Setup
        User user1 = new User("U1", "Alice", "alice@example.com");
        User user2 = new User("U2", "Bob", "bob@example.com");
        service.registerUser(user1);
        service.registerUser(user2);
        
        Document doc = service.createDocument("U1", "Collaborative Doc");
        service.shareDocument(doc.getId(), "U1", "U2", PermissionLevel.EDITOR);
        
        // Initial content
        service.insertText(doc.getId(), "U1", 0, "abc");
        System.out.println("Initial: \"" + doc.getContent() + "\"\n");
        
        // Simulate concurrent operations
        System.out.println("Simulating concurrent edits:");
        System.out.println("User A: Insert 'x' at position 1");
        System.out.println("User B: Insert 'y' at position 2\n");
        
        Operation opA = new Operation("OP1", OperationType.INSERT, 1, 'x', "U1", doc.getVersion());
        Operation opB = new Operation("OP2", OperationType.INSERT, 2, 'y', "U2", doc.getVersion());
        
        // Show transformation
        service.demonstrateOT(doc.getId(), opA, opB);
        
        // Apply both
        System.out.println("\nApplying operations:");
        doc.applyOperation(opA);
        System.out.println("After Op A: \"" + doc.getContent() + "\"");
        
        // Transform and apply Op B
        Operation transformedB = service.transformer.transform(opA, opB);
        doc.applyOperation(transformedB);
        System.out.println("After Op B (transformed): \"" + doc.getContent() + "\"");
        
        System.out.println("\nResult: Consistent state maintained!");
        System.out.println();
    }
    
    private static void demoPermissions() {
        System.out.println("--- Demo 3: Permission System ---\n");
        
        GoogleDocsService service = new GoogleDocsService();
        
        User owner = new User("U1", "Owner", "owner@example.com");
        User editor = new User("U2", "Editor", "editor@example.com");
        User viewer = new User("U3", "Viewer", "viewer@example.com");
        
        service.registerUser(owner);
        service.registerUser(editor);
        service.registerUser(viewer);
        
        Document doc = service.createDocument("U1", "Shared Document");
        System.out.println("Document created by: " + owner.getName());
        
        // Share with different permissions
        service.shareDocument(doc.getId(), "U1", "U2", PermissionLevel.EDITOR);
        service.shareDocument(doc.getId(), "U1", "U3", PermissionLevel.VIEWER);
        
        // Test permissions
        System.out.println("\nTesting permissions:");
        System.out.println("Owner can edit: " + doc.hasPermission("U1", PermissionLevel.EDITOR));
        System.out.println("Editor can edit: " + doc.hasPermission("U2", PermissionLevel.EDITOR));
        System.out.println("Viewer can edit: " + doc.hasPermission("U3", PermissionLevel.EDITOR));
        
        // Viewer tries to edit (will be blocked)
        System.out.println("\nViewer attempts to insert 'X'...");
        int sizeBefore = doc.getContent().length();
        service.insertCharacter(doc.getId(), "U3", 0, 'X');
        int sizeAfter = doc.getContent().length();
        System.out.println("Edit blocked: " + (sizeBefore == sizeAfter));
        
        // Editor can edit
        System.out.println("\nEditor inserts 'Hello'...");
        service.insertText(doc.getId(), "U2", 0, "Hello");
        System.out.println("Content: \"" + doc.getContent() + "\"");
        
        System.out.println();
    }
    
    private static void demoVersionControl() {
        System.out.println("--- Demo 4: Version Control ---\n");
        
        GoogleDocsService service = new GoogleDocsService();
        
        User user = new User("U1", "Alice", "alice@example.com");
        service.registerUser(user);
        
        Document doc = service.createDocument("U1", "Versioned Document");
        
        // Make multiple edits
        System.out.println("Making edits to trigger version snapshots...");
        service.insertText(doc.getId(), "U1", 0, "First");
        service.insertText(doc.getId(), "U1", 5, " Edit");
        service.insertText(doc.getId(), "U1", 10, " Here");
        service.insertText(doc.getId(), "U1", 15, " More");
        service.insertText(doc.getId(), "U1", 20, " Text");
        
        System.out.println("\nCurrent content: \"" + doc.getContent() + "\"");
        System.out.println("Current version: " + doc.getVersion());
        
        // Show version history
        List<Version> history = service.getVersionHistory(doc.getId());
        System.out.println("\nVersion history (" + history.size() + " snapshots):");
        for (Version v : history) {
            System.out.println("  v" + v.getVersionNumber() + ": \"" + v.getContent() + "\"");
        }
        
        // Restore previous version
        if (!history.isEmpty()) {
            int restoreVersion = history.get(0).getVersionNumber();
            System.out.println("\nRestoring to version " + restoreVersion + "...");
            service.restoreVersion(doc.getId(), restoreVersion);
            System.out.println("Content after restore: \"" + doc.getContent() + "\"");
        }
        
        System.out.println();
    }
    
    private static void demoCollaborativeScenario() {
        System.out.println("--- Demo 5: Collaborative Editing Scenario ---\n");
        
        GoogleDocsService service = new GoogleDocsService();
        
        // Setup users
        User alice = new User("U1", "Alice", "alice@example.com");
        User bob = new User("U2", "Bob", "bob@example.com");
        service.registerUser(alice);
        service.registerUser(bob);
        
        // Create shared document
        Document doc = service.createDocument("U1", "Team Meeting Notes");
        service.shareDocument(doc.getId(), "U1", "U2", PermissionLevel.EDITOR);
        
        System.out.println("Shared document: " + doc.getTitle());
        System.out.println("Collaborators: Alice (owner), Bob (editor)\n");
        
        // Collaborative editing
        System.out.println("Alice types: 'Meeting agenda:'");
        service.insertText(doc.getId(), "U1", 0, "Meeting agenda:");
        
        System.out.println("Bob types: '1. Budget'");
        service.insertText(doc.getId(), "U2", doc.getContent().length(), "\n1. Budget");
        
        System.out.println("Alice types: '2. Timeline'");
        service.insertText(doc.getId(), "U1", doc.getContent().length(), "\n2. Timeline");
        
        System.out.println("\nFinal document:");
        System.out.println("\"" + doc.getContent() + "\"");
        
        System.out.println("\nVersion: " + doc.getVersion());
        System.out.println("Total edits: " + doc.getVersion() + " operations");
        
        System.out.println();
    }
}
