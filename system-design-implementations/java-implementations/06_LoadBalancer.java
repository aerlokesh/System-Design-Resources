import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * LOAD BALANCER - System Design Implementation
 * 
 * Algorithms implemented:
 * 1. Round Robin - Rotate through servers sequentially
 * 2. Weighted Round Robin - Higher weight = more requests
 * 3. Least Connections - Route to server with fewest active connections
 * 4. IP Hash - Consistent server for same client (sticky sessions)
 * 5. Random - Simple random selection
 * 6. Health-checked Load Balancer - Skip unhealthy servers
 * 
 * Interview talking points:
 * - L4 (Transport) vs L7 (Application) load balancing
 * - Used by: AWS ELB/ALB, Nginx, HAProxy, Envoy
 * - Health checks: Active (ping) vs Passive (monitor failures)
 * - Session persistence: Cookie-based, IP-based
 * - SSL termination at load balancer
 * - Horizontal scaling: Add more servers behind LB
 */
class LoadBalancer {

    // ==================== SERVER ====================
    static class Server {
        final String id;
        final String address;
        final int weight;
        volatile boolean healthy;
        final AtomicInteger activeConnections = new AtomicInteger(0);
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong totalLatencyMs = new AtomicLong(0);

        Server(String id, String address, int weight) {
            this.id = id;
            this.address = address;
            this.weight = weight;
            this.healthy = true;
        }

        Server(String id, String address) { this(id, address, 1); }

        void startRequest() {
            activeConnections.incrementAndGet();
            totalRequests.incrementAndGet();
        }

        void endRequest(long latencyMs) {
            activeConnections.decrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
        }

        double avgLatency() {
            long reqs = totalRequests.get();
            return reqs == 0 ? 0 : (double) totalLatencyMs.get() / reqs;
        }

        public String toString() {
            return String.format("%s(%s,w=%d,conn=%d,healthy=%s)",
                    id, address, weight, activeConnections.get(), healthy);
        }
    }

    // ==================== 1. ROUND ROBIN ====================
    static class RoundRobinLB {
        private final List<Server> servers;
        private final AtomicInteger index = new AtomicInteger(0);

        RoundRobinLB(List<Server> servers) { this.servers = new ArrayList<>(servers); }

        Server getServer() {
            if (servers.isEmpty()) return null;
            int idx = Math.abs(index.getAndIncrement() % servers.size());
            return servers.get(idx);
        }
    }

    // ==================== 2. WEIGHTED ROUND ROBIN ====================
    static class WeightedRoundRobinLB {
        private final List<Server> servers;
        private final List<Server> weightedList;
        private final AtomicInteger index = new AtomicInteger(0);

        WeightedRoundRobinLB(List<Server> servers) {
            this.servers = servers;
            this.weightedList = new ArrayList<>();
            for (Server s : servers) {
                for (int i = 0; i < s.weight; i++) weightedList.add(s);
            }
        }

        Server getServer() {
            if (weightedList.isEmpty()) return null;
            int idx = Math.abs(index.getAndIncrement() % weightedList.size());
            return weightedList.get(idx);
        }
    }

    // ==================== 3. LEAST CONNECTIONS ====================
    static class LeastConnectionsLB {
        private final List<Server> servers;

        LeastConnectionsLB(List<Server> servers) { this.servers = new ArrayList<>(servers); }

        synchronized Server getServer() {
            return servers.stream()
                    .filter(s -> s.healthy)
                    .min(Comparator.comparingInt(s -> s.activeConnections.get()))
                    .orElse(null);
        }
    }

    // ==================== 4. IP HASH ====================
    static class IPHashLB {
        private final List<Server> servers;

        IPHashLB(List<Server> servers) { this.servers = new ArrayList<>(servers); }

        Server getServer(String clientIP) {
            if (servers.isEmpty()) return null;
            int hash = Math.abs(clientIP.hashCode());
            return servers.get(hash % servers.size());
        }
    }

    // ==================== 5. RANDOM ====================
    static class RandomLB {
        private final List<Server> servers;
        private final Random random = new Random();

        RandomLB(List<Server> servers) { this.servers = new ArrayList<>(servers); }

        Server getServer() {
            if (servers.isEmpty()) return null;
            return servers.get(random.nextInt(servers.size()));
        }
    }

    // ==================== 6. HEALTH-CHECKED LB ====================
    static class HealthCheckedLB {
        private final List<Server> servers;
        private final AtomicInteger index = new AtomicInteger(0);
        private final int failureThreshold;
        private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();

        HealthCheckedLB(List<Server> servers, int failureThreshold) {
            this.servers = new ArrayList<>(servers);
            this.failureThreshold = failureThreshold;
        }

        Server getServer() {
            List<Server> healthy = new ArrayList<>();
            for (Server s : servers) {
                if (s.healthy) healthy.add(s);
            }
            if (healthy.isEmpty()) return null;
            int idx = Math.abs(index.getAndIncrement() % healthy.size());
            return healthy.get(idx);
        }

        void reportFailure(Server server) {
            int count = failureCounts.merge(server.id, 1, Integer::sum);
            if (count >= failureThreshold) {
                server.healthy = false;
            }
        }

        void reportSuccess(Server server) {
            failureCounts.put(server.id, 0);
            server.healthy = true;
        }

        void markHealthy(Server server) {
            server.healthy = true;
            failureCounts.put(server.id, 0);
        }

        List<Server> getHealthyServers() {
            List<Server> result = new ArrayList<>();
            for (Server s : servers) if (s.healthy) result.add(s);
            return result;
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) {
        System.out.println("=== LOAD BALANCER - System Design Demo ===\n");

        List<Server> servers = Arrays.asList(
                new Server("srv-1", "10.0.0.1:8080", 1),
                new Server("srv-2", "10.0.0.2:8080", 2),
                new Server("srv-3", "10.0.0.3:8080", 3)
        );

        // 1. Round Robin
        System.out.println("--- 1. Round Robin ---");
        RoundRobinLB rrLB = new RoundRobinLB(servers);
        for (int i = 0; i < 6; i++) {
            System.out.printf("  Request %d -> %s%n", i + 1, rrLB.getServer().id);
        }

        // 2. Weighted Round Robin
        System.out.println("\n--- 2. Weighted Round Robin ---");
        WeightedRoundRobinLB wrrLB = new WeightedRoundRobinLB(servers);
        Map<String, Integer> wrrDist = new HashMap<>();
        for (int i = 0; i < 600; i++) {
            wrrDist.merge(wrrLB.getServer().id, 1, Integer::sum);
        }
        for (Server s : servers) {
            int count = wrrDist.getOrDefault(s.id, 0);
            System.out.printf("  %s (weight=%d): %d requests (%.1f%%)%n",
                    s.id, s.weight, count, count * 100.0 / 600);
        }

        // 3. Least Connections
        System.out.println("\n--- 3. Least Connections ---");
        LeastConnectionsLB lcLB = new LeastConnectionsLB(servers);
        servers.get(0).activeConnections.set(5);
        servers.get(1).activeConnections.set(2);
        servers.get(2).activeConnections.set(8);
        for (int i = 0; i < 3; i++) {
            Server s = lcLB.getServer();
            System.out.printf("  Request %d -> %s (connections: %d)%n",
                    i + 1, s.id, s.activeConnections.get());
            s.startRequest();
        }
        // Reset
        servers.forEach(s -> s.activeConnections.set(0));

        // 4. IP Hash (Sticky Sessions)
        System.out.println("\n--- 4. IP Hash (Sticky Sessions) ---");
        IPHashLB ipLB = new IPHashLB(servers);
        String[] clientIPs = {"192.168.1.1", "192.168.1.2", "10.0.0.5", "192.168.1.1"};
        for (String ip : clientIPs) {
            System.out.printf("  Client %s -> %s%n", ip, ipLB.getServer(ip).id);
        }
        System.out.println("  (Same IP always goes to same server)");

        // 5. Random
        System.out.println("\n--- 5. Random ---");
        RandomLB rLB = new RandomLB(servers);
        Map<String, Integer> rDist = new HashMap<>();
        for (int i = 0; i < 600; i++) {
            rDist.merge(rLB.getServer().id, 1, Integer::sum);
        }
        for (Server s : servers) {
            System.out.printf("  %s: %d requests (%.1f%%)%n",
                    s.id, rDist.getOrDefault(s.id, 0), rDist.getOrDefault(s.id, 0) * 100.0 / 600);
        }

        // 6. Health-Checked
        System.out.println("\n--- 6. Health-Checked Load Balancer ---");
        HealthCheckedLB hcLB = new HealthCheckedLB(servers, 3);
        System.out.printf("  Healthy servers: %d%n", hcLB.getHealthyServers().size());

        // Simulate failures on srv-2
        Server failing = servers.get(1);
        System.out.printf("  Simulating 3 failures on %s...%n", failing.id);
        for (int i = 0; i < 3; i++) hcLB.reportFailure(failing);
        System.out.printf("  %s healthy: %s%n", failing.id, failing.healthy);
        System.out.printf("  Healthy servers: %d%n", hcLB.getHealthyServers().size());

        // Route traffic - only goes to healthy
        Map<String, Integer> hcDist = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            Server s = hcLB.getServer();
            if (s != null) hcDist.merge(s.id, 1, Integer::sum);
        }
        System.out.println("  Traffic distribution (srv-2 excluded):");
        for (Map.Entry<String, Integer> e : hcDist.entrySet()) {
            System.out.printf("    %s: %d requests%n", e.getKey(), e.getValue());
        }

        // Recover
        hcLB.markHealthy(failing);
        System.out.printf("  After recovery: %d healthy servers%n", hcLB.getHealthyServers().size());

        // 7. Throughput comparison
        System.out.println("\n--- 7. Throughput Test (100K requests) ---");
        int total = 100_000;
        long t1 = System.nanoTime();
        RoundRobinLB perfRR = new RoundRobinLB(servers);
        for (int i = 0; i < total; i++) perfRR.getServer();
        long rrTime = (System.nanoTime() - t1) / 1_000_000;

        t1 = System.nanoTime();
        LeastConnectionsLB perfLC = new LeastConnectionsLB(servers);
        for (int i = 0; i < total; i++) perfLC.getServer();
        long lcTime = (System.nanoTime() - t1) / 1_000_000;

        System.out.printf("  Round Robin:       %dms%n", rrTime);
        System.out.printf("  Least Connections: %dms%n", lcTime);

        // Summary
        System.out.println("\n--- Algorithm Comparison ---");
        System.out.println("  Round Robin:       Simple, equal distribution, no state");
        System.out.println("  Weighted RR:       Respects server capacity differences");
        System.out.println("  Least Connections: Best for varying request durations");
        System.out.println("  IP Hash:           Sticky sessions, good for stateful apps");
        System.out.println("  Random:            Simple, statistically even, no coordination");
        System.out.println("  Health-Checked:    Automatic failover, production-essential");
    }
}
