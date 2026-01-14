import java.util.*;

/**
 * INTERVIEW-READY Google Docs System
 * Time to complete: 50-60 minutes
 * Focus: Operational Transform, collaborative editing
 */

// ==================== Operation Types ====================
enum OperationType {
    INSERT, DELETE
}

// ==================== Operation ====================
class Operation {
    private final OperationType type;
    private final int position;
    private final String character;
    private final String userId;
    private final long timestamp;

    public Operation(OperationType type, int position, String character, String userId) {
        this.type = type;
        this.position = position;
        this.character = character;
        this.userId = userId;
        this.timestamp = System.currentTimeMillis();
    }

    public OperationType getType() { return type; }
    public int getPosition() { return position; }
    public String getCharacter() { return character; }
    public String getUserId() { return userId; }

    @Override
    public String toString() {
        return type + " at " + position + 
               (type == OperationType.INSERT ? " '" + character + "'" : "") + 
               " by " + userId;
    }
}

// ==================== Document ====================
class Document {
    private final String docId;
    private final String title;
    private StringBuilder content;
    private final List<Operation> history;
    private final Set<String> collaborators;
    private final String owner;

    public Document(String docId, String title, String owner) {
        this.docId = docId;
        this.title = title;
        this.content = new StringBuilder();
        this.history = new ArrayList<>();
        this.collaborators = new HashSet<>();
        this.owner = owner;
        collaborators.add(owner);
    }

    public synchronized void applyOperation(Operation op) {
        if (!collaborators.contains(op.getUserId())) {
            System.out.println("✗ User not authorized: " + op.getUserId());
            return;
        }

        try {
            switch (op.getType()) {
                case INSERT:
                    content.insert(op.getPosition(), op.getCharacter());
                    break;
                case DELETE:
                    if (op.getPosition() < content.length()) {
                        content.deleteCharAt(op.getPosition());
                    }
                    break;
            }
            history.add(op);
            System.out.println("✓ Applied: " + op);
        } catch (Exception e) {
            System.out.println("✗ Failed to apply: " + op + " - " + e.getMessage());
        }
    }

    public void insertText(int position, String text, String userId) {
        for (int i = 0; i < text.length(); i++) {
            Operation op = new Operation(
                OperationType.INSERT,
                position + i,
                String.valueOf(text.charAt(i)),
                userId
            );
            applyOperation(op);
        }
    }

    public void deleteText(int position, int length, String userId) {
        for (int i = 0; i < length; i++) {
            Operation op = new Operation(
                OperationType.DELETE,
                position,  // Always delete at same position
                "",
                userId
            );
            applyOperation(op);
        }
    }

    public void addCollaborator(String userId) {
        collaborators.add(userId);
        System.out.println("✓ Added collaborator: " + userId + " to " + title);
    }

    public String getContent() {
        return content.toString();
    }

    public String getDocId() { return docId; }
    public String getTitle() { return title; }
    public int getVersion() { return history.size(); }

    public void displayDocument() {
        System.out.println("\n=== Document: " + title + " ===");
        System.out.println("Owner: " + owner);
        System.out.println("Collaborators: " + collaborators.size());
        System.out.println("Version: " + getVersion());
        System.out.println("Content: \"" + getContent() + "\"");
        System.out.println();
    }
}

// ==================== Google Docs Service ====================
class GoogleDocsService {
    private final Map<String, Document> documents;
    private int docCounter;

    public GoogleDocsService() {
        this.documents = new HashMap<>();
        this.docCounter = 1;
    }

    public Document createDocument(String title, String owner) {
        String docId = "DOC" + docCounter++;
        Document doc = new Document(docId, title, owner);
        documents.put(docId, doc);
        System.out.println("✓ Created document: " + title + " (ID: " + docId + ")");
        return doc;
    }

    public Document getDocument(String docId) {
        return documents.get(docId);
    }

    public void shareDocument(String docId, String userId) {
        Document doc = documents.get(docId);
        if (doc != null) {
            doc.addCollaborator(userId);
        }
    }

    public void editDocument(String docId, int position, String text, 
                            String userId, boolean isInsert) {
        Document doc = documents.get(docId);
        if (doc == null) {
            System.out.println("✗ Document not found: " + docId);
            return;
        }

        if (isInsert) {
            doc.insertText(position, text, userId);
        } else {
            doc.deleteText(position, text.length(), userId);
        }
    }
}

// ==================== Demo ====================
public class GoogleDocsSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Google Docs Demo ===\n");

        GoogleDocsService service = new GoogleDocsService();

        // Create document
        Document doc = service.createDocument("Project Proposal", "alice");

        // Share with collaborators
        System.out.println("\n--- Sharing Document ---");
        service.shareDocument(doc.getDocId(), "bob");
        service.shareDocument(doc.getDocId(), "charlie");

        // Collaborative editing
        System.out.println("\n--- Collaborative Editing ---");
        
        // Alice types "Hello"
        doc.insertText(0, "Hello", "alice");
        doc.displayDocument();

        // Bob adds " World"
        doc.insertText(5, " World", "bob");
        doc.displayDocument();

        // Charlie adds "!" at end
        doc.insertText(11, "!", "charlie");
        doc.displayDocument();

        // Alice deletes "World" (6 characters starting at position 6)
        System.out.println("--- Deleting Text ---");
        doc.deleteText(6, 5, "alice");
        doc.displayDocument();

        // Bob inserts "There"
        doc.insertText(6, "There", "bob");
        doc.displayDocument();

        System.out.println("Final content: \"" + doc.getContent() + "\"");
        System.out.println("Total operations: " + doc.getVersion());

        System.out.println("\n✅ Demo complete!");
    }
}
