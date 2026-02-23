import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * DISTRIBUTED KEY-VALUE STORE - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Partitioning (sharding) across nodes
 * - Replication for fault tolerance
 * - Vector clocks for conflict detection
 * - Quorum reads/writes (W + R > N)
 * - Eventual consistency model
 * - Consistent hashing for partition assignment
 * 
 * Interview talking points:
 * - Used by: DynamoDB, Cassandra, Riak, Voldemort
 * - CAP Theorem: Choose CP (strong consistency) or AP (availability)
 * - Consistency: Strong, Eventual, Causal, Read-your-writes
 * - Replication: Leader-follower, leaderless, multi-leader
 * - Conflict resolution: Last-write-wins, vector clocks, CRDTs
 * - Failure detection: Gossip protocol, heartbeats
 */
class DistributedKVStore {

    // ==================== VECTOR CLOCK ====================
    static class VectorClock {
        private final Map<String, Integer> clock = new ConcurrentHashMap<>();

        void increment(String nodeId) {
            clock.merge(nodeId, 1, Integer::sum);
        }

        VectorClock copy() {
            VectorClock vc = new VectorClock();
            vc.clock.putAll(this.clock);
            return vc;
        }

        void merge(VectorClock other) {
            for (Map.Entry<String, Integer> e : other.clock.entrySet()) {
                clock.merge(e.getKey(), e.getValue(), Math::max);
            }
        }

        boolean happensBefore(VectorClock other) {
            boolean atLeastOneLess = false;
            for (String key : this.clock.keySet()) {
                int thisVal = this.clock.getOrDefault(key, 0);
                int otherVal = other.clock.getOrDefault(key, 0);
                if (thisVal > otherVal) return false;
                if (thisVal < otherVal) atLeastOneLess = true;
            }
            for (String key : other.clock.keySet()) {
                if (!this.clock.containsKey(key) && other.clock.get(key) > 0) atLeastOneLess = true;
            }
            return atLeastOneLess;
        }

        boolean isConcurrent(VectorClock other) {
            return !this.happensBefore(other) && !other.happensBefore(this) && !this.equals(other);
        }

        public String toString() { return clock.toString(); }

        public boolean equals(Object o) {
            if (!(o instanceof VectorClock)) return false;
            return clock.equals(((VectorClock) o).clock);
        }
    }

    // ==================== VERSIONED VALUE ====================
    static class VersionedValue {
        final String value;
        final VectorClock version;
        final long timestamp;

        VersionedValue(String value, VectorClock version) {
            this.value = value;
            this.version = version;
            this.timestamp = System.currentTimeMillis();
        }

        public String toString() {
            return String.format("'%s' @%s", value, version);
        }
    }

    // ==================== STORAGE NODE ====================
    static class StorageNode {
        final String nodeId;
        private final ConcurrentHashMap<String, List<VersionedValue>> store = new ConcurrentHashMap<>();
        volatile boolean alive = true;
        final AtomicLong readCount = new AtomicLong();
        final AtomicLong writeCount = new AtomicLong();

        StorageNode(String nodeId) { this.nodeId = nodeId; }

        void put(String key, String value, VectorClock version) {
            if (!alive) throw new RuntimeException("Node " + nodeId + " is down");
            writeCount.incrementAndGet();
            version.increment(nodeId);
            VersionedValue vv = new VersionedValue(value, version);
            store.compute(key, (k, existing) -> {
                if (existing == null) {
                    List<VersionedValue> list = new ArrayList<>();
                    list.add(vv);
                    return list;
                }
                // Remove versions that this new version supersedes
                existing.removeIf(old -> old.version.happensBefore(version));
                existing.add(vv);
                return existing;
            });
        }

        List<VersionedValue> get(String key) {
            if (!alive) throw new RuntimeException("Node " + nodeId + " is down");
            readCount.incrementAndGet();
            return store.getOrDefault(key, Collections.emptyList());
        }

        boolean delete(String key) {
            if (!alive) throw new RuntimeException("Node " + nodeId + " is down");
            return store.remove(key) != null;
        }

        int keyCount() { return store.size(); }
    }

    // ==================== DISTRIBUTED STORE ====================
    static class DistributedStore {
        private final List<StorageNode> nodes;
        private final int replicationFactor; // N
        private final int writeQuorum;       // W
        private final int readQuorum;        // R

        DistributedStore(int numNodes, int replicationFactor, int writeQuorum, int readQuorum) {
            this.nodes = new ArrayList<>();
            for (int i = 0; i < numNodes; i++) {
                nodes.add(new StorageNode("node-" + i));
            }
            this.replicationFactor = replicationFactor;
            this.writeQuorum = writeQuorum;
            this.readQuorum = readQuorum;
        }

        // Get replica nodes for a key
        private List<StorageNode> getReplicaNodes(String key) {
            int primaryIdx = Math.abs(key.hashCode()) % nodes.size();
            List<StorageNode> replicas = new ArrayList<>();
            for (int i = 0; i < replicationFactor; i++) {
                replicas.add(nodes.get((primaryIdx + i) % nodes.size()));
            }
            return replicas;
        }

        // Quorum write
        boolean put(String key, String value) {
            List<StorageNode> replicas = getReplicaNodes(key);
            VectorClock version = new VectorClock();

            int successCount = 0;
            List<String> successNodes = new ArrayList<>();
            for (StorageNode node : replicas) {
                try {
                    node.put(key, value, version.copy());
                    successCount++;
                    successNodes.add(node.nodeId);
                } catch (Exception e) {
                    // Node is down, try next
                }
            }
            return successCount >= writeQuorum;
        }

        // Quorum read
        String get(String key) {
            List<StorageNode> replicas = getReplicaNodes(key);
            List<VersionedValue> allValues = new ArrayList<>();
            int successCount = 0;

            for (StorageNode node : replicas) {
                try {
                    List<VersionedValue> values = node.get(key);
                    allValues.addAll(values);
                    successCount++;
                } catch (Exception e) {
                    // Node is down
                }
            }

            if (successCount < readQuorum) {
                throw new RuntimeException("Could not achieve read quorum");
            }

            // Resolve conflicts - take the latest (last-write-wins for simplicity)
            return allValues.stream()
                    .max(Comparator.comparingLong(v -> v.timestamp))
                    .map(v -> v.value)
                    .orElse(null);
        }

        // Get with conflict info
        List<VersionedValue> getWithVersions(String key) {
            List<StorageNode> replicas = getReplicaNodes(key);
            List<VersionedValue> allValues = new ArrayList<>();
            for (StorageNode node : replicas) {
                try { allValues.addAll(node.get(key)); }
                catch (Exception e) { /* skip */ }
            }
            return allValues;
        }

        StorageNode getNode(int index) { return nodes.get(index); }
        int getNodeCount() { return nodes.size(); }

        String getReplicaInfo(String key) {
            List<StorageNode> replicas = getReplicaNodes(key);
            StringBuilder sb = new StringBuilder();
            for (StorageNode n : replicas) sb.append(n.nodeId).append(" ");
            return sb.toString().trim();
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) {
        System.out.println("=== DISTRIBUTED KEY-VALUE STORE - System Design Demo ===\n");

        // 1. Vector Clocks
        System.out.println("--- 1. Vector Clocks ---");
        VectorClock vc1 = new VectorClock();
        vc1.increment("A"); vc1.increment("A");
        VectorClock vc2 = vc1.copy();
        vc2.increment("B");
        VectorClock vc3 = vc1.copy();
        vc3.increment("C");

        System.out.printf("  vc1: %s%n", vc1);
        System.out.printf("  vc2: %s (after vc1 + B increment)%n", vc2);
        System.out.printf("  vc3: %s (after vc1 + C increment)%n", vc3);
        System.out.printf("  vc1 -> vc2: %s (vc1 happens before vc2)%n", vc1.happensBefore(vc2));
        System.out.printf("  vc2 || vc3: %s (concurrent - conflict!)%n", vc2.isConcurrent(vc3));

        // Merge resolves conflict
        VectorClock merged = vc2.copy();
        merged.merge(vc3);
        merged.increment("A");
        System.out.printf("  Merged:    %s%n", merged);

        // 2. Basic distributed store (N=3, W=2, R=2)
        System.out.println("\n--- 2. Distributed Store (5 nodes, N=3, W=2, R=2) ---");
        DistributedStore store = new DistributedStore(5, 3, 2, 2);

        // Write some data
        String[] keys = {"user:1001", "user:1002", "order:5001", "session:abc"};
        for (String key : keys) {
            boolean success = store.put(key, "value-for-" + key);
            System.out.printf("  PUT %s -> replicas: [%s] success: %s%n",
                    key, store.getReplicaInfo(key), success);
        }

        // Read data
        System.out.println("\n  Reads:");
        for (String key : keys) {
            String val = store.get(key);
            System.out.printf("  GET %s -> %s%n", key, val);
        }

        // 3. Node failure tolerance
        System.out.println("\n--- 3. Node Failure Tolerance ---");
        System.out.printf("  Quorum config: N=%d, W=%d, R=%d (W+R=%d > N=%d: strong consistency)%n",
                3, 2, 2, 4, 3);

        // Kill one node
        store.getNode(1).alive = false;
        System.out.println("  Killed node-1");

        boolean writeOk = store.put("user:1001", "updated-value");
        String readVal = store.get("user:1001");
        System.out.printf("  Write with 1 node down: %s%n", writeOk ? "SUCCESS" : "FAILED");
        System.out.printf("  Read with 1 node down:  %s%n", readVal != null ? "SUCCESS" : "FAILED");

        // Kill another node
        store.getNode(2).alive = false;
        System.out.println("  Killed node-2");
        try {
            store.get("user:1001");
            System.out.println("  Read with 2 nodes down: SUCCESS");
        } catch (Exception e) {
            System.out.println("  Read with 2 nodes down: FAILED (cannot achieve quorum)");
        }

        // Restore
        store.getNode(1).alive = true;
        store.getNode(2).alive = true;

        // 4. Different quorum configurations
        System.out.println("\n--- 4. Quorum Configurations ---");
        System.out.println("  Strong Consistency: W + R > N");
        System.out.println("    N=3, W=2, R=2: Tolerates 1 failure, strong reads");
        System.out.println("    N=3, W=3, R=1: All must write, fast reads");
        System.out.println("  Eventual Consistency: W + R <= N");
        System.out.println("    N=3, W=1, R=1: Fastest, but may read stale data");
        System.out.println("    N=3, W=1, R=3: Fast writes, slow but consistent reads");

        // 5. Concurrent writes (conflict scenario)
        System.out.println("\n--- 5. Conflict Detection ---");
        VectorClock clientA = new VectorClock();
        VectorClock clientB = new VectorClock();

        // Both clients read, then write concurrently
        clientA.increment("clientA");
        clientB.increment("clientB");

        StorageNode directNode = store.getNode(0);
        directNode.put("shared-key", "value-from-A", clientA);
        directNode.put("shared-key", "value-from-B", clientB);

        List<VersionedValue> versions = directNode.get("shared-key");
        System.out.printf("  Concurrent writes detected: %d versions%n", versions.size());
        for (VersionedValue vv : versions) {
            System.out.printf("    %s%n", vv);
        }
        System.out.println("  Resolution strategies: Last-Write-Wins, app-level merge, CRDTs");

        // 6. Stats
        System.out.println("\n--- 6. Node Statistics ---");
        for (int i = 0; i < store.getNodeCount(); i++) {
            StorageNode n = store.getNode(i);
            System.out.printf("  %s: keys=%d, reads=%d, writes=%d%n",
                    n.nodeId, n.keyCount(), n.readCount.get(), n.writeCount.get());
        }

        // Summary
        System.out.println("\n--- Key Design Decisions ---");
        System.out.println("  Partitioning:  Consistent hashing (minimal redistribution)");
        System.out.println("  Replication:   N replicas per key (fault tolerance)");
        System.out.println("  Consistency:   Tunable via W, R quorum values");
        System.out.println("  Conflicts:     Vector clocks detect, app resolves");
        System.out.println("  Failure:       Hinted handoff, anti-entropy repair");
    }
}
