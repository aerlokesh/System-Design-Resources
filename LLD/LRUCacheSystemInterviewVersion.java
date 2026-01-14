import java.util.*;

/**
 * INTERVIEW-READY LRU Cache System
 * Time to complete: 30-45 minutes
 * Focus: HashMap + Doubly Linked List for O(1) operations
 */

// ==================== Node for Doubly Linked List ====================
class Node {
    int key;
    int value;
    Node prev;
    Node next;

    Node(int key, int value) {
        this.key = key;
        this.value = value;
    }
}

// ==================== LRU Cache ====================
class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> cache;
    private final Node head;  // Dummy head (most recent)
    private final Node tail;  // Dummy tail (least recent)

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        
        // Initialize dummy nodes
        this.head = new Node(0, 0);
        this.tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        Node node = cache.get(key);
        if (node == null) {
            System.out.println("GET " + key + " -> MISS");
            return -1;
        }

        // Move to front (most recently used)
        moveToHead(node);
        System.out.println("GET " + key + " -> HIT (" + node.value + ")");
        return node.value;
    }

    public void put(int key, int value) {
        Node node = cache.get(key);

        if (node != null) {
            // Update existing
            node.value = value;
            moveToHead(node);
            System.out.println("PUT " + key + "=" + value + " (updated)");
        } else {
            // Add new
            Node newNode = new Node(key, value);
            cache.put(key, newNode);
            addToHead(newNode);

            // Evict LRU if over capacity
            if (cache.size() > capacity) {
                Node lru = removeTail();
                cache.remove(lru.key);
                System.out.println("PUT " + key + "=" + value + " (evicted key " + lru.key + ")");
            } else {
                System.out.println("PUT " + key + "=" + value);
            }
        }
    }

    // Add node right after head
    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    // Remove node from current position
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    // Move node to head
    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }

    // Remove and return LRU node
    private Node removeTail() {
        Node lru = tail.prev;
        removeNode(lru);
        return lru;
    }

    public void displayCache() {
        System.out.print("Cache (MRU->LRU): [");
        Node current = head.next;
        while (current != tail) {
            System.out.print(current.key + "=" + current.value);
            if (current.next != tail) {
                System.out.print(", ");
            }
            current = current.next;
        }
        System.out.println("] Size: " + cache.size() + "/" + capacity + "\n");
    }
}

// ==================== Demo ====================
public class LRUCacheSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== LRU Cache Demo ===\n");

        // Create cache with capacity 3
        LRUCache cache = new LRUCache(3);

        // Test basic operations
        System.out.println("--- Test 1: Basic Put/Get ---");
        cache.put(1, 100);
        cache.put(2, 200);
        cache.put(3, 300);
        cache.displayCache();

        cache.get(1);  // Access key 1 (moves to front)
        cache.displayCache();

        // Test eviction
        System.out.println("--- Test 2: Eviction ---");
        cache.put(4, 400);  // Should evict key 2 (LRU)
        cache.displayCache();

        // Test cache miss
        System.out.println("--- Test 3: Cache Miss ---");
        cache.get(2);  // Should return -1 (evicted)
        cache.displayCache();

        // Test update
        System.out.println("--- Test 4: Update ---");
        cache.put(3, 333);  // Update key 3
        cache.displayCache();

        System.out.println("✅ Demo complete!");
    }
}
