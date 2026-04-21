import java.util.*;

// ==================== MEMENTO PATTERN ====================
// Definition: Captures and externalizes an object's internal state so that it can
// be restored later, without violating encapsulation.
//
// REAL System Design Interview Examples:
// 1. Version Control / Snapshots - Git commits, document versions
// 2. Database Checkpoint/Recovery - Transaction logs, WAL
// 3. Game Save System - Save/load game state
// 4. Configuration Rollback - Rollback to previous config version
// 5. Undo in Applications - Text editor state snapshots
//
// Interview Use Cases:
// - Design Version Control: Store snapshots of state
// - Design Database Recovery: Checkpoint and restore
// - Design Config Management: Rollback to previous versions
// - Design Collaborative Editor: Document version history
// - Design Workflow System: Checkpoint workflow state

// ==================== EXAMPLE 1: DOCUMENT VERSION CONTROL ====================
// Used in: Google Docs, Notion, Confluence, Git
// Interview Question: Design a document versioning system

class DocumentMemento {
    private final String content;
    private final String title;
    private final Map<String, String> metadata;
    private final long timestamp;
    private final String versionId;
    private final String author;

    public DocumentMemento(String content, String title, Map<String, String> metadata, String author) {
        this.content = content;
        this.title = title;
        this.metadata = new HashMap<>(metadata);
        this.timestamp = System.currentTimeMillis();
        this.versionId = "v" + UUID.randomUUID().toString().substring(0, 6);
        this.author = author;
    }

    // Only accessible to the originator
    String getContent() { return content; }
    String getTitle() { return title; }
    Map<String, String> getMetadata() { return new HashMap<>(metadata); }
    public long getTimestamp() { return timestamp; }
    public String getVersionId() { return versionId; }
    public String getAuthor() { return author; }

    @Override
    public String toString() {
        return String.format("[%s] by %s | title='%s' | %d chars",
            versionId, author, title, content.length());
    }
}

class VersionedDocument {
    private String title;
    private String content;
    private Map<String, String> metadata;

    public VersionedDocument(String title) {
        this.title = title;
        this.content = "";
        this.metadata = new HashMap<>();
    }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void addMetadata(String key, String value) { metadata.put(key, value); }

    // Create memento (save state)
    public DocumentMemento save(String author) {
        System.out.printf("   💾 Saving snapshot: title='%s', %d chars%n", title, content.length());
        return new DocumentMemento(content, title, metadata, author);
    }

    // Restore from memento
    public void restore(DocumentMemento memento) {
        this.title = memento.getTitle();
        this.content = memento.getContent();
        this.metadata = memento.getMetadata();
        System.out.printf("   ↩️  Restored to version %s: title='%s', %d chars%n",
            memento.getVersionId(), title, content.length());
    }

    public void printState() {
        System.out.printf("   📄 Current: title='%s' | content='%s'%n",
            title, content.length() > 50 ? content.substring(0, 50) + "..." : content);
    }
}

class DocumentHistory {
    private Deque<DocumentMemento> history = new ArrayDeque<>();
    private int maxVersions;

    public DocumentHistory(int maxVersions) {
        this.maxVersions = maxVersions;
    }

    public void push(DocumentMemento memento) {
        if (history.size() >= maxVersions) {
            history.removeLast(); // Remove oldest
            System.out.println("   🗑️  Oldest version pruned (max history reached)");
        }
        history.push(memento);
    }

    public DocumentMemento pop() {
        if (history.isEmpty()) return null;
        return history.pop();
    }

    public DocumentMemento peek() {
        return history.peek();
    }

    public void printHistory() {
        System.out.println("   📋 Version History (" + history.size() + " versions):");
        int i = 0;
        for (DocumentMemento m : history) {
            System.out.printf("      %d. %s%n", i++, m);
        }
    }

    public int size() { return history.size(); }
}

// ==================== EXAMPLE 2: DATABASE CHECKPOINT ====================
// Used in: PostgreSQL WAL, Redis RDB snapshots, MongoDB checkpoints
// Interview Question: Design a database recovery system

class DatabaseState {
    private Map<String, String> data;
    private long transactionId;
    private long timestamp;

    public DatabaseState(Map<String, String> data, long transactionId) {
        this.data = new HashMap<>(data);
        this.transactionId = transactionId;
        this.timestamp = System.currentTimeMillis();
    }

    public Map<String, String> getData() { return new HashMap<>(data); }
    public long getTransactionId() { return transactionId; }
    public long getTimestamp() { return timestamp; }
}

class SimpleDatabase {
    private Map<String, String> data = new HashMap<>();
    private long currentTransactionId = 0;

    public void put(String key, String value) {
        currentTransactionId++;
        data.put(key, value);
        System.out.printf("   📝 [txn=%d] PUT %s = %s%n", currentTransactionId, key, value);
    }

    public String get(String key) {
        return data.get(key);
    }

    public void delete(String key) {
        currentTransactionId++;
        data.remove(key);
        System.out.printf("   📝 [txn=%d] DELETE %s%n", currentTransactionId, key);
    }

    public DatabaseState checkpoint() {
        System.out.printf("   💾 CHECKPOINT at txn=%d (%d keys)%n", currentTransactionId, data.size());
        return new DatabaseState(data, currentTransactionId);
    }

    public void restoreFromCheckpoint(DatabaseState state) {
        this.data = state.getData();
        this.currentTransactionId = state.getTransactionId();
        System.out.printf("   ↩️  RESTORED to txn=%d (%d keys)%n", currentTransactionId, data.size());
    }

    public void printData() {
        System.out.printf("   🗃️  Database [txn=%d]: %s%n", currentTransactionId, data);
    }
}

class CheckpointManager {
    private List<DatabaseState> checkpoints = new ArrayList<>();

    public void saveCheckpoint(DatabaseState state) {
        checkpoints.add(state);
        System.out.printf("   📋 Checkpoint saved (total: %d)%n", checkpoints.size());
    }

    public DatabaseState getCheckpoint(int index) {
        if (index < 0 || index >= checkpoints.size()) return null;
        return checkpoints.get(index);
    }

    public DatabaseState getLatestCheckpoint() {
        return checkpoints.isEmpty() ? null : checkpoints.get(checkpoints.size() - 1);
    }

    public void printCheckpoints() {
        System.out.println("   📋 Checkpoints:");
        for (int i = 0; i < checkpoints.size(); i++) {
            DatabaseState cp = checkpoints.get(i);
            System.out.printf("      %d. txn=%d, keys=%d%n", i, cp.getTransactionId(), cp.getData().size());
        }
    }
}

// ==================== EXAMPLE 3: CONFIGURATION ROLLBACK ====================
// Used in: Feature flags, service configs, deployment rollback
// Interview Question: Design a configuration management system with rollback

class ConfigSnapshot {
    private final Map<String, String> settings;
    private final String version;
    private final String deployedBy;
    private final long timestamp;

    public ConfigSnapshot(Map<String, String> settings, String version, String deployedBy) {
        this.settings = new HashMap<>(settings);
        this.version = version;
        this.deployedBy = deployedBy;
        this.timestamp = System.currentTimeMillis();
    }

    public Map<String, String> getSettings() { return new HashMap<>(settings); }
    public String getVersion() { return version; }
    public String getDeployedBy() { return deployedBy; }

    @Override
    public String toString() {
        return String.format("[%s] by %s, %d settings", version, deployedBy, settings.size());
    }
}

class ServiceConfig {
    private Map<String, String> settings = new HashMap<>();
    private String currentVersion = "0.0.0";

    public void set(String key, String value) {
        settings.put(key, value);
        System.out.printf("   ⚙️  Set %s = %s%n", key, value);
    }

    public void setVersion(String version) { this.currentVersion = version; }

    public ConfigSnapshot takeSnapshot(String deployedBy) {
        System.out.printf("   💾 Config snapshot v%s (%d settings)%n", currentVersion, settings.size());
        return new ConfigSnapshot(settings, currentVersion, deployedBy);
    }

    public void restoreSnapshot(ConfigSnapshot snapshot) {
        this.settings = snapshot.getSettings();
        this.currentVersion = snapshot.getVersion();
        System.out.printf("   ↩️  Rolled back to v%s (%d settings)%n", currentVersion, settings.size());
    }

    public void printConfig() {
        System.out.printf("   ⚙️  Config v%s: %s%n", currentVersion, settings);
    }
}

class ConfigRollbackManager {
    private Deque<ConfigSnapshot> snapshots = new ArrayDeque<>();

    public void save(ConfigSnapshot snapshot) {
        snapshots.push(snapshot);
    }

    public ConfigSnapshot rollback() {
        if (snapshots.isEmpty()) return null;
        return snapshots.pop();
    }

    public void printHistory() {
        System.out.println("   📋 Config History:");
        int i = 0;
        for (ConfigSnapshot s : snapshots) {
            System.out.printf("      %d. %s%n", i++, s);
        }
    }
}

// ==================== DEMO ====================

public class MementoPattern {
    public static void main(String[] args) {
        System.out.println("========== MEMENTO PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: DOCUMENT VERSION CONTROL =====
        System.out.println("===== EXAMPLE 1: DOCUMENT VERSIONING (Google Docs/Git) =====\n");

        VersionedDocument doc = new VersionedDocument("System Design Notes");
        DocumentHistory history = new DocumentHistory(10);

        doc.setContent("# Introduction to System Design");
        history.push(doc.save("alice"));

        doc.setContent("# Introduction to System Design\n## CAP Theorem\nConsistency, Availability, Partition tolerance");
        history.push(doc.save("alice"));

        doc.setContent("# Introduction to System Design\n## CAP Theorem\n## Load Balancing\nRound robin, least connections");
        history.push(doc.save("bob"));

        doc.printState();
        history.printHistory();

        // Undo last change
        System.out.println("\nUndo last change:");
        DocumentMemento prev = history.pop();
        prev = history.pop(); // Skip current, get previous
        doc.restore(prev);
        doc.printState();

        // ===== EXAMPLE 2: DATABASE CHECKPOINT =====
        System.out.println("\n\n===== EXAMPLE 2: DATABASE CHECKPOINT (PostgreSQL/Redis) =====\n");

        SimpleDatabase db = new SimpleDatabase();
        CheckpointManager cpManager = new CheckpointManager();

        db.put("user:1", "Alice");
        db.put("user:2", "Bob");
        cpManager.saveCheckpoint(db.checkpoint());

        db.put("user:3", "Charlie");
        db.put("order:1", "Order-A");
        cpManager.saveCheckpoint(db.checkpoint());

        // Simulate corruption
        db.delete("user:1");
        db.delete("user:2");
        db.put("bad_data", "corrupted");
        db.printData();

        // Restore from checkpoint
        System.out.println("\nRestoring from checkpoint 0:");
        db.restoreFromCheckpoint(cpManager.getCheckpoint(0));
        db.printData();

        System.out.println("\nRestoring from latest checkpoint:");
        db.restoreFromCheckpoint(cpManager.getLatestCheckpoint());
        db.printData();

        // ===== EXAMPLE 3: CONFIGURATION ROLLBACK =====
        System.out.println("\n\n===== EXAMPLE 3: CONFIGURATION ROLLBACK (Feature Flags) =====\n");

        ServiceConfig config = new ServiceConfig();
        ConfigRollbackManager rollbackMgr = new ConfigRollbackManager();

        config.set("feature.new_ui", "false");
        config.set("rate_limit", "1000");
        config.set("cache_ttl", "300");
        config.setVersion("1.0.0");
        rollbackMgr.save(config.takeSnapshot("alice"));

        config.set("feature.new_ui", "true");
        config.set("rate_limit", "5000");
        config.setVersion("1.1.0");
        rollbackMgr.save(config.takeSnapshot("bob"));

        config.set("feature.new_ui", "true");
        config.set("rate_limit", "500"); // Bad config!
        config.set("cache_ttl", "5");    // Too low!
        config.setVersion("1.2.0");
        config.printConfig();

        // Incident! Rollback!
        System.out.println("\n🚨 Incident detected! Rolling back...");
        ConfigSnapshot safe = rollbackMgr.rollback();
        config.restoreSnapshot(safe);
        config.printConfig();

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Document versioning (Google Docs, Git, Confluence)");
        System.out.println("• Database checkpoints (PostgreSQL WAL, Redis RDB)");
        System.out.println("• Configuration rollback (feature flags, deployments)");
        System.out.println("• Game save systems (save/load state)");
    }
}
