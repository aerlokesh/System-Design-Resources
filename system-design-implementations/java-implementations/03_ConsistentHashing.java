import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * CONSISTENT HASHING - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Hash ring with virtual nodes
 * - Minimal key redistribution when nodes join/leave
 * - Load balancing across distributed nodes
 * - Replication with preference list
 * 
 * Interview talking points:
 * - Used by: DynamoDB, Cassandra, Memcached, Akamai CDN
 * - Without virtual nodes: uneven distribution
 * - Virtual nodes: 100-200 per physical node typical
 * - When node added: only K/N keys need redistribution (K=keys, N=nodes)
 * - Compared to modular hashing: mod N changes ALL mappings when N changes
 */
class ConsistentHashing {

    // ==================== BASIC CONSISTENT HASH RING ====================
    static class HashRing {
        private final TreeMap<Long, String> ring = new TreeMap<>();
        private final Map<String, Integer> nodeVirtualCount = new HashMap<>();
        private final int defaultVirtualNodes;

        HashRing(int defaultVirtualNodes) {
            this.defaultVirtualNodes = defaultVirtualNodes;
        }

        void addNode(String node) {
            addNode(node, defaultVirtualNodes);
        }

        void addNode(String node, int virtualNodes) {
            nodeVirtualCount.put(node, virtualNodes);
            for (int i = 0; i < virtualNodes; i++) {
                long hash = hash(node + "#" + i);
                ring.put(hash, node);
            }
        }

        void removeNode(String node) {
            int vnodes = nodeVirtualCount.getOrDefault(node, defaultVirtualNodes);
            for (int i = 0; i < vnodes; i++) {
                long hash = hash(node + "#" + i);
                ring.remove(hash);
            }
            nodeVirtualCount.remove(node);
        }

        String getNode(String key) {
            if (ring.isEmpty()) return null;
            long hash = hash(key);
            Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
            if (entry == null) entry = ring.firstEntry(); // Wrap around
            return entry.getValue();
        }

        /**
         * Get N distinct nodes for replication (preference list).
         * Used by DynamoDB/Cassandra for replication factor.
         */
        List<String> getNodes(String key, int count) {
            if (ring.isEmpty()) return Collections.emptyList();
            List<String> nodes = new ArrayList<>();
            long hash = hash(key);
            
            // Walk clockwise from the hash position
            NavigableMap<Long, String> tailMap = ring.tailMap(hash, true);
            for (String node : tailMap.values()) {
                if (!nodes.contains(node)) nodes.add(node);
                if (nodes.size() >= count) return nodes;
            }
            // Wrap around
            for (String node : ring.values()) {
                if (!nodes.contains(node)) nodes.add(node);
                if (nodes.size() >= count) return nodes;
            }
            return nodes;
        }

        int getNodeCount() {
            return nodeVirtualCount.size();
        }

        Map<String, Integer> getDistribution(List<String> keys) {
            Map<String, Integer> dist = new HashMap<>();
            for (String key : keys) {
                String node = getNode(key);
                dist.merge(node, 1, Integer::sum);
            }
            return dist;
        }

        private long hash(String key) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
                // Use first 8 bytes for a long hash
                long h = 0;
                for (int i = 0; i < 8; i++) {
                    h = (h << 8) | (digest[i] & 0xFF);
                }
                return h;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ==================== WEIGHTED CONSISTENT HASHING ====================
    /**
     * Nodes with higher weight get more virtual nodes -> more keys.
     * Useful when nodes have different capacities.
     * e.g., 16GB server gets 2x virtual nodes vs 8GB server
     */
    static class WeightedHashRing extends HashRing {
        private final Map<String, Integer> weights = new HashMap<>();
        private final int baseVirtualNodes;

        WeightedHashRing(int baseVirtualNodes) {
            super(baseVirtualNodes);
            this.baseVirtualNodes = baseVirtualNodes;
        }

        void addNode(String node, int weight) {
            weights.put(node, weight);
            super.addNode(node, baseVirtualNodes * weight);
        }

        int getWeight(String node) {
            return weights.getOrDefault(node, 1);
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) {
        System.out.println("=== CONSISTENT HASHING - System Design Demo ===\n");

        // 1. Basic ring with virtual nodes
        System.out.println("--- 1. Basic Hash Ring (150 virtual nodes each) ---");
        HashRing ring = new HashRing(150);
        ring.addNode("server-A");
        ring.addNode("server-B");
        ring.addNode("server-C");

        String[] keys = {"user:1001", "user:1002", "order:5001", "session:abc", "cache:xyz"};
        for (String key : keys) {
            System.out.printf("  %-20s -> %s%n", key, ring.getNode(key));
        }

        // 2. Distribution analysis
        System.out.println("\n--- 2. Load Distribution (10000 keys) ---");
        List<String> manyKeys = new ArrayList<>();
        for (int i = 0; i < 10000; i++) manyKeys.add("key:" + i);

        Map<String, Integer> dist = ring.getDistribution(manyKeys);
        System.out.println("  With 3 nodes:");
        for (Map.Entry<String, Integer> e : dist.entrySet()) {
            int count = e.getValue();
            double pct = count * 100.0 / manyKeys.size();
            String bar = "#".repeat((int)(pct / 2));
            System.out.printf("    %-12s: %5d keys (%5.1f%%) %s%n", e.getKey(), count, pct, bar);
        }

        // 3. Add a node - show minimal redistribution
        System.out.println("\n--- 3. Adding server-D (minimal redistribution) ---");
        Map<String, String> beforeMapping = new HashMap<>();
        for (String key : manyKeys) beforeMapping.put(key, ring.getNode(key));

        ring.addNode("server-D");

        int moved = 0;
        for (String key : manyKeys) {
            if (!ring.getNode(key).equals(beforeMapping.get(key))) moved++;
        }
        System.out.printf("  Keys moved: %d/%d (%.1f%%) - ideally ~%.1f%%%n",
                moved, manyKeys.size(),
                moved * 100.0 / manyKeys.size(),
                100.0 / ring.getNodeCount());

        Map<String, Integer> distAfter = ring.getDistribution(manyKeys);
        System.out.println("  New distribution:");
        for (Map.Entry<String, Integer> e : distAfter.entrySet()) {
            int count = e.getValue();
            double pct = count * 100.0 / manyKeys.size();
            String bar = "#".repeat((int)(pct / 2));
            System.out.printf("    %-12s: %5d keys (%5.1f%%) %s%n", e.getKey(), count, pct, bar);
        }

        // 4. Remove a node
        System.out.println("\n--- 4. Removing server-B ---");
        Map<String, String> beforeRemoval = new HashMap<>();
        for (String key : manyKeys) beforeRemoval.put(key, ring.getNode(key));

        ring.removeNode("server-B");

        int movedAfterRemoval = 0;
        for (String key : manyKeys) {
            if (!ring.getNode(key).equals(beforeRemoval.get(key))) movedAfterRemoval++;
        }
        System.out.printf("  Keys moved: %d/%d (%.1f%%)%n",
                movedAfterRemoval, manyKeys.size(), movedAfterRemoval * 100.0 / manyKeys.size());

        Map<String, Integer> distAfterRemoval = ring.getDistribution(manyKeys);
        for (Map.Entry<String, Integer> e : distAfterRemoval.entrySet()) {
            double pct = e.getValue() * 100.0 / manyKeys.size();
            System.out.printf("    %-12s: %5d keys (%5.1f%%)%n", e.getKey(), e.getValue(), pct);
        }

        // 5. Replication (preference list)
        System.out.println("\n--- 5. Replication (get 2 replicas per key) ---");
        ring.addNode("server-B"); // Re-add for demo
        for (String key : new String[]{"user:1001", "order:5001", "session:abc"}) {
            List<String> replicas = ring.getNodes(key, 2);
            System.out.printf("  %-20s -> primary: %-12s replica: %s%n",
                    key, replicas.get(0), replicas.size() > 1 ? replicas.get(1) : "N/A");
        }

        // 6. Virtual nodes impact
        System.out.println("\n--- 6. Virtual Nodes Impact on Distribution ---");
        int[] vnodeCounts = {1, 10, 50, 150, 500};
        for (int vn : vnodeCounts) {
            HashRing testRing = new HashRing(vn);
            testRing.addNode("A");
            testRing.addNode("B");
            testRing.addNode("C");
            Map<String, Integer> d = testRing.getDistribution(manyKeys);
            double stdDev = calculateStdDev(d.values());
            double idealPct = 100.0 / 3;
            System.out.printf("  VNodes=%3d: ", vn);
            for (Map.Entry<String, Integer> e : d.entrySet()) {
                System.out.printf("%s=%.1f%% ", e.getKey(), e.getValue() * 100.0 / manyKeys.size());
            }
            System.out.printf(" (stddev=%.1f, ideal=%.1f%%)%n", stdDev, idealPct);
        }

        // 7. Weighted nodes
        System.out.println("\n--- 7. Weighted Nodes (different server capacities) ---");
        WeightedHashRing wring = new WeightedHashRing(50);
        wring.addNode("small-8gb", 1);   // 50 vnodes
        wring.addNode("medium-16gb", 2); // 100 vnodes
        wring.addNode("large-32gb", 4);  // 200 vnodes

        Map<String, Integer> wdist = wring.getDistribution(manyKeys);
        for (Map.Entry<String, Integer> e : wdist.entrySet()) {
            double pct = e.getValue() * 100.0 / manyKeys.size();
            System.out.printf("  %-16s (weight=%d): %5d keys (%5.1f%%)%n",
                    e.getKey(), wring.getWeight(e.getKey()), e.getValue(), pct);
        }

        // 8. Modular hashing comparison
        System.out.println("\n--- 8. Consistent vs Modular Hashing ---");
        System.out.println("  Modular: key % N -> when N changes, nearly ALL keys move");
        System.out.println("  Consistent: only K/N keys move when adding/removing a node");
        System.out.println("  Example with 3->4 nodes:");
        int modularMoved = 0;
        for (int i = 0; i < 10000; i++) {
            if (i % 3 != i % 4) modularMoved++;
        }
        System.out.printf("    Modular hashing:    %d/10000 keys moved (%.1f%%)%n",
                modularMoved, modularMoved / 100.0);
        System.out.printf("    Consistent hashing: ~%d/10000 keys moved (~%.1f%%)%n",
                10000 / 4, 100.0 / 4);
    }

    private static double calculateStdDev(Collection<Integer> values) {
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }
}
