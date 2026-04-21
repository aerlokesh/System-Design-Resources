import java.util.*;

// ==================== ITERATOR PATTERN ====================
// Definition: Provides a way to access the elements of an aggregate object sequentially
// without exposing its underlying representation.
//
// REAL System Design Interview Examples:
// 1. Database Cursor/Pagination - Iterate over large result sets
// 2. File System Traversal - Iterate over files/directories
// 3. Social Media Feed - Scroll through infinite feed
// 4. Message Queue Consumer - Process messages one at a time
// 5. Distributed Collection - Iterate over sharded data
//
// Interview Use Cases:
// - Design Pagination: Cursor-based iteration over large datasets
// - Design News Feed: Infinite scroll through merged feeds
// - Design File System: Traverse directory tree
// - Design Log Aggregator: Iterate over distributed logs
// - Design Search Results: Page through results

// ==================== EXAMPLE 1: DATABASE CURSOR PAGINATION ====================
// Used in: MongoDB cursor, PostgreSQL cursor, DynamoDB pagination
// Interview Question: Design a database pagination system

interface DatabaseIterator<T> {
    boolean hasNext();
    T next();
    int getTotalCount();
    int getCurrentPage();
}

class DatabaseRecord {
    private String id;
    private String name;
    private double value;

    public DatabaseRecord(String id, String name, double value) {
        this.id = id;
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("{id=%s, name=%s, value=%.2f}", id, name, value);
    }

    public String getId() { return id; }
}

class CursorPaginationIterator implements DatabaseIterator<List<DatabaseRecord>> {
    private List<DatabaseRecord> allData;
    private int pageSize;
    private int currentIndex = 0;
    private int pageNumber = 0;

    public CursorPaginationIterator(List<DatabaseRecord> data, int pageSize) {
        this.allData = data;
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasNext() {
        return currentIndex < allData.size();
    }

    @Override
    public List<DatabaseRecord> next() {
        if (!hasNext()) throw new NoSuchElementException();

        int end = Math.min(currentIndex + pageSize, allData.size());
        List<DatabaseRecord> page = allData.subList(currentIndex, end);
        String cursor = page.get(page.size() - 1).getId();

        System.out.printf("   📄 Page %d: %d records (cursor: %s, hasMore: %s)%n",
            ++pageNumber, page.size(), cursor, (end < allData.size()));
        currentIndex = end;
        return page;
    }

    @Override
    public int getTotalCount() { return allData.size(); }
    @Override
    public int getCurrentPage() { return pageNumber; }
}

class OffsetPaginationIterator implements DatabaseIterator<List<DatabaseRecord>> {
    private List<DatabaseRecord> allData;
    private int pageSize;
    private int offset = 0;
    private int pageNumber = 0;

    public OffsetPaginationIterator(List<DatabaseRecord> data, int pageSize) {
        this.allData = data;
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasNext() {
        return offset < allData.size();
    }

    @Override
    public List<DatabaseRecord> next() {
        if (!hasNext()) throw new NoSuchElementException();

        int end = Math.min(offset + pageSize, allData.size());
        List<DatabaseRecord> page = allData.subList(offset, end);

        System.out.printf("   📄 Page %d: OFFSET %d LIMIT %d → %d records%n",
            ++pageNumber, offset, pageSize, page.size());
        offset += pageSize;
        return page;
    }

    @Override
    public int getTotalCount() { return allData.size(); }
    @Override
    public int getCurrentPage() { return pageNumber; }
}

// ==================== EXAMPLE 2: FILE SYSTEM TRAVERSAL ====================
// Used in: Find command, IDE file explorer, backup tools
// Interview Question: Design a file system

interface FileSystemIterator {
    boolean hasNext();
    FileNode next();
}

class FileNode {
    private String name;
    private boolean isDirectory;
    private long size;
    private List<FileNode> children;

    public FileNode(String name, boolean isDirectory, long size) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.children = isDirectory ? new ArrayList<>() : null;
    }

    public void addChild(FileNode child) {
        if (isDirectory) children.add(child);
    }

    public String getName() { return name; }
    public boolean isDirectory() { return isDirectory; }
    public long getSize() { return size; }
    public List<FileNode> getChildren() { return children != null ? children : Collections.emptyList(); }

    @Override
    public String toString() {
        return (isDirectory ? "📁 " : "📄 ") + name + (isDirectory ? "/" : " (" + size + " bytes)");
    }
}

// Depth-First iterator
class DFSFileIterator implements FileSystemIterator {
    private Deque<FileNode> stack = new ArrayDeque<>();

    public DFSFileIterator(FileNode root) {
        stack.push(root);
    }

    @Override
    public boolean hasNext() { return !stack.isEmpty(); }

    @Override
    public FileNode next() {
        if (!hasNext()) throw new NoSuchElementException();
        FileNode node = stack.pop();
        // Push children in reverse to process left-to-right
        List<FileNode> children = node.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(children.get(i));
        }
        return node;
    }
}

// Breadth-First iterator
class BFSFileIterator implements FileSystemIterator {
    private Queue<FileNode> queue = new LinkedList<>();

    public BFSFileIterator(FileNode root) {
        queue.offer(root);
    }

    @Override
    public boolean hasNext() { return !queue.isEmpty(); }

    @Override
    public FileNode next() {
        if (!hasNext()) throw new NoSuchElementException();
        FileNode node = queue.poll();
        for (FileNode child : node.getChildren()) {
            queue.offer(child);
        }
        return node;
    }
}

// ==================== EXAMPLE 3: SOCIAL MEDIA FEED ====================
// Used in: Twitter, Instagram, LinkedIn feed
// Interview Question: Design a social media news feed

interface FeedIterator {
    boolean hasNext();
    FeedItem next();
    void refresh();
}

class FeedItem {
    private String postId;
    private String author;
    private String content;
    private long timestamp;
    private double relevanceScore;

    public FeedItem(String postId, String author, String content, long timestamp, double relevanceScore) {
        this.postId = postId;
        this.author = author;
        this.content = content;
        this.timestamp = timestamp;
        this.relevanceScore = relevanceScore;
    }

    @Override
    public String toString() {
        return String.format("[%s] @%s: \"%s\" (score: %.1f)", postId, author, content, relevanceScore);
    }

    public double getRelevanceScore() { return relevanceScore; }
    public long getTimestamp() { return timestamp; }
}

class RankedFeedIterator implements FeedIterator {
    private List<FeedItem> items;
    private int index = 0;
    private int batchSize;

    public RankedFeedIterator(List<FeedItem> items, int batchSize) {
        this.items = new ArrayList<>(items);
        this.items.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        this.batchSize = batchSize;
    }

    @Override
    public boolean hasNext() { return index < items.size(); }

    @Override
    public FeedItem next() {
        if (!hasNext()) throw new NoSuchElementException();
        FeedItem item = items.get(index++);
        System.out.printf("   📰 Feed item %d: %s%n", index, item);
        return item;
    }

    @Override
    public void refresh() {
        System.out.println("   🔄 Refreshing feed...");
        index = 0;
    }

    public List<FeedItem> nextBatch() {
        List<FeedItem> batch = new ArrayList<>();
        for (int i = 0; i < batchSize && hasNext(); i++) {
            batch.add(next());
        }
        return batch;
    }
}

class ChronologicalFeedIterator implements FeedIterator {
    private List<FeedItem> items;
    private int index = 0;

    public ChronologicalFeedIterator(List<FeedItem> items) {
        this.items = new ArrayList<>(items);
        this.items.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
    }

    @Override
    public boolean hasNext() { return index < items.size(); }

    @Override
    public FeedItem next() {
        if (!hasNext()) throw new NoSuchElementException();
        FeedItem item = items.get(index++);
        System.out.printf("   📰 Feed item %d: %s%n", index, item);
        return item;
    }

    @Override
    public void refresh() {
        index = 0;
    }
}

// ==================== EXAMPLE 4: MESSAGE QUEUE CONSUMER ====================
// Used in: Kafka consumer, SQS, RabbitMQ
// Interview Question: Design a message queue

interface MessageIterator {
    boolean hasNext();
    QueueMessage next();
    void acknowledge(String messageId);
}

class QueueMessage {
    private String messageId;
    private String topic;
    private String body;
    private int partition;
    private long offset;

    public QueueMessage(String messageId, String topic, String body, int partition, long offset) {
        this.messageId = messageId;
        this.topic = topic;
        this.body = body;
        this.partition = partition;
        this.offset = offset;
    }

    public String getMessageId() { return messageId; }

    @Override
    public String toString() {
        return String.format("[%s] topic=%s partition=%d offset=%d: %s",
            messageId, topic, partition, offset, body);
    }
}

class KafkaStyleConsumer implements MessageIterator {
    private Queue<QueueMessage> messageBuffer = new LinkedList<>();
    private Set<String> acknowledgedMessages = new HashSet<>();
    private String consumerGroup;
    private long currentOffset = 0;

    public KafkaStyleConsumer(String consumerGroup, List<QueueMessage> messages) {
        this.consumerGroup = consumerGroup;
        this.messageBuffer.addAll(messages);
    }

    @Override
    public boolean hasNext() { return !messageBuffer.isEmpty(); }

    @Override
    public QueueMessage next() {
        if (!hasNext()) throw new NoSuchElementException();
        QueueMessage msg = messageBuffer.poll();
        currentOffset++;
        System.out.printf("   📨 [%s] Consumed: %s%n", consumerGroup, msg);
        return msg;
    }

    @Override
    public void acknowledge(String messageId) {
        acknowledgedMessages.add(messageId);
        System.out.printf("   ✅ [%s] Acknowledged: %s (committed offset: %d)%n",
            consumerGroup, messageId, currentOffset);
    }

    public int getAcknowledgedCount() { return acknowledgedMessages.size(); }
}

// ==================== DEMO ====================

public class IteratorPattern {
    public static void main(String[] args) {
        System.out.println("========== ITERATOR PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: DATABASE PAGINATION =====
        System.out.println("===== EXAMPLE 1: DATABASE PAGINATION =====\n");

        List<DatabaseRecord> records = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            records.add(new DatabaseRecord("id-" + i, "Item-" + i, i * 10.0));
        }

        System.out.println("Cursor-based pagination (pageSize=4):");
        CursorPaginationIterator cursor = new CursorPaginationIterator(records, 4);
        while (cursor.hasNext()) {
            List<DatabaseRecord> page = cursor.next();
        }

        System.out.println("\nOffset-based pagination (pageSize=5):");
        OffsetPaginationIterator offset = new OffsetPaginationIterator(records, 5);
        while (offset.hasNext()) {
            offset.next();
        }

        // ===== EXAMPLE 2: FILE SYSTEM TRAVERSAL =====
        System.out.println("\n\n===== EXAMPLE 2: FILE SYSTEM TRAVERSAL =====\n");

        FileNode root = new FileNode("root", true, 0);
        FileNode src = new FileNode("src", true, 0);
        FileNode docs = new FileNode("docs", true, 0);
        src.addChild(new FileNode("Main.java", false, 2048));
        src.addChild(new FileNode("Utils.java", false, 1024));
        docs.addChild(new FileNode("README.md", false, 512));
        root.addChild(src);
        root.addChild(docs);
        root.addChild(new FileNode("build.xml", false, 256));

        System.out.println("DFS traversal:");
        DFSFileIterator dfs = new DFSFileIterator(root);
        while (dfs.hasNext()) {
            System.out.println("   " + dfs.next());
        }

        System.out.println("\nBFS traversal:");
        BFSFileIterator bfs = new BFSFileIterator(root);
        while (bfs.hasNext()) {
            System.out.println("   " + bfs.next());
        }

        // ===== EXAMPLE 3: SOCIAL MEDIA FEED =====
        System.out.println("\n\n===== EXAMPLE 3: SOCIAL MEDIA FEED =====\n");

        List<FeedItem> feedItems = Arrays.asList(
            new FeedItem("p1", "alice", "Just shipped a new feature!", 1000, 9.5),
            new FeedItem("p2", "bob", "Coffee and code", 999, 3.2),
            new FeedItem("p3", "charlie", "System design is fun", 998, 8.1),
            new FeedItem("p4", "dave", "Lunchtime!", 997, 2.0),
            new FeedItem("p5", "eve", "New blog post on distributed systems", 996, 7.5)
        );

        System.out.println("Ranked feed (by relevance, batch of 3):");
        RankedFeedIterator ranked = new RankedFeedIterator(feedItems, 3);
        ranked.nextBatch();

        System.out.println("\nChronological feed:");
        ChronologicalFeedIterator chrono = new ChronologicalFeedIterator(feedItems);
        while (chrono.hasNext()) {
            chrono.next();
        }

        // ===== EXAMPLE 4: MESSAGE QUEUE =====
        System.out.println("\n\n===== EXAMPLE 4: MESSAGE QUEUE CONSUMER (Kafka) =====\n");

        List<QueueMessage> messages = Arrays.asList(
            new QueueMessage("msg-1", "orders", "Order #001 created", 0, 100),
            new QueueMessage("msg-2", "orders", "Order #002 created", 0, 101),
            new QueueMessage("msg-3", "orders", "Order #001 paid", 0, 102)
        );

        KafkaStyleConsumer consumer = new KafkaStyleConsumer("order-processor", messages);
        while (consumer.hasNext()) {
            QueueMessage msg = consumer.next();
            // Process and acknowledge
            consumer.acknowledge(msg.getMessageId());
        }
        System.out.printf("   📊 Total acknowledged: %d%n", consumer.getAcknowledgedCount());

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Database pagination (cursor/offset-based)");
        System.out.println("• File system traversal (DFS/BFS)");
        System.out.println("• Social media feeds (ranked/chronological)");
        System.out.println("• Message queue consumers (Kafka, SQS, RabbitMQ)");
    }
}
