import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

// ==================== Core Server Class ====================

/**
 * Represents a backend server
 * Thread-safe for concurrent access
 */
class Server {
    private final String id;
    private final String host;
    private final int port;
    private final int weight;
    private final AtomicBoolean isHealthy;
    private final AtomicInteger activeConnections;

    public Server(String id, String host, int port, int weight) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Server ID cannot be null/empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (weight < 1) {
            throw new IllegalArgumentException("Weight must be positive");
        }

        this.id = id;
        this.host = host;
        this.port = port;
        this.weight = weight;
        this.isHealthy = new AtomicBoolean(true);
        this.activeConnections = new AtomicInteger(0);
    }

    public void incrementConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementConnections() {
        int connections = activeConnections.decrementAndGet();
        if (connections < 0) {
            activeConnections.set(0);
        }
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public boolean isAvailable() {
        return isHealthy.get();
    }

    public void setHealthy(boolean healthy) {
        isHealthy.set(healthy);
    }

    public String getAddress() {
        return host + ":" + port;
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getWeight() { return weight; }

    @Override
    public String toString() {
        return String.format("Server[id=%s, address=%s, healthy=%s, connections=%d, weight=%d]",
                id, getAddress(), isHealthy.get(), activeConnections.get(), weight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return id.equals(server.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

// ==================== Server Pool Management ====================

/**
 * Thread-safe pool of backend servers
 */
class ServerPool {
    private final CopyOnWriteArrayList<Server> servers;
    private final CopyOnWriteArrayList<Server> healthyServers;
    private final ReadWriteLock lock;

    public ServerPool() {
        this.servers = new CopyOnWriteArrayList<>();
        this.healthyServers = new CopyOnWriteArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public boolean addServer(Server server) {
        lock.writeLock().lock();
        try {
            if (servers.stream().anyMatch(s -> s.getId().equals(server.getId()))) {
                System.out.println("Server already exists: " + server.getId());
                return false;
            }

            servers.add(server);
            if (server.isAvailable()) {
                healthyServers.add(server);
            }

            System.out.println("✓ Added server: " + server);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeServer(String serverId) {
        lock.writeLock().lock();
        try {
            Server server = findServerById(serverId);
            if (server == null) {
                System.out.println("Server not found: " + serverId);
                return false;
            }

            servers.remove(server);
            healthyServers.remove(server);

            System.out.println("✗ Removed server: " + serverId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateServerHealth(String serverId, boolean healthy) {
        lock.writeLock().lock();
        try {
            Server server = findServerById(serverId);
            if (server == null) {
                return;
            }

            boolean wasHealthy = server.isAvailable();
            server.setHealthy(healthy);

            if (healthy && !wasHealthy) {
                healthyServers.add(server);
                System.out.println("♥ Server recovered: " + serverId);
            } else if (!healthy && wasHealthy) {
                healthyServers.remove(server);
                System.out.println("✗ Server failed: " + serverId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Server> getHealthyServers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(healthyServers);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Server> getAllServers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(servers);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Server getServer(String serverId) {
        lock.readLock().lock();
        try {
            return findServerById(serverId);
        } finally {
            lock.readLock().unlock();
        }
    }

    private Server findServerById(String serverId) {
        return servers.stream()
                .filter(s -> s.getId().equals(serverId))
                .findFirst()
                .orElse(null);
    }

    public int getHealthyServerCount() {
        return healthyServers.size();
    }

    public boolean hasHealthyServers() {
        return !healthyServers.isEmpty();
    }

    public void displayStatus() {
        System.out.println("\n=== Server Pool Status ===");
        System.out.println("Total servers: " + servers.size());
        System.out.println("Healthy servers: " + healthyServers.size());
        System.out.println("\nServer Details:");
        for (Server server : servers) {
            System.out.println("  " + server);
        }
        System.out.println();
    }
}

// ==================== Load Balancing Strategy Interface ====================

/**
 * Strategy interface for server selection algorithms
 * Strategy Pattern: Allows pluggable algorithms
 */
interface LoadBalancingStrategy {
    Server selectServer(List<Server> servers);
    String getName();
    default void reset() {}
}

// ==================== Load Balancing Strategies ====================

/**
 * Round Robin Strategy - Distributes requests equally in rotation
 */
class RoundRobinStrategy implements LoadBalancingStrategy {
    private final AtomicInteger currentIndex;

    public RoundRobinStrategy() {
        this.currentIndex = new AtomicInteger(0);
    }

    @Override
    public Server selectServer(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        int index = Math.abs(currentIndex.getAndIncrement() % servers.size());
        return servers.get(index);
    }

    @Override
    public String getName() {
        return "RoundRobin";
    }

    @Override
    public void reset() {
        currentIndex.set(0);
    }
}

/**
 * Least Connections Strategy - Routes to server with fewest active connections
 */
class LeastConnectionsStrategy implements LoadBalancingStrategy {

    @Override
    public Server selectServer(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        Server selected = servers.get(0);
        int minConnections = selected.getActiveConnections();

        for (Server server : servers) {
            int connections = server.getActiveConnections();
            if (connections < minConnections) {
                selected = server;
                minConnections = connections;
            }
        }

        return selected;
    }

    @Override
    public String getName() {
        return "LeastConnections";
    }
}

/**
 * Weighted Round Robin Strategy - Distributes based on server capacity weights
 */
class WeightedRoundRobinStrategy implements LoadBalancingStrategy {
    private final AtomicInteger currentIndex;
    private final AtomicInteger currentWeight;

    public WeightedRoundRobinStrategy() {
        this.currentIndex = new AtomicInteger(0);
        this.currentWeight = new AtomicInteger(0);
    }

    @Override
    public Server selectServer(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        synchronized (this) {
            int totalWeight = servers.stream()
                    .mapToInt(Server::getWeight)
                    .sum();

            if (totalWeight == 0) {
                return servers.get(0);
            }

            int weight = currentWeight.get();
            int index = currentIndex.get();

            if (index >= servers.size()) {
                index = 0;
            }

            Server selected = servers.get(index);

            if (weight <= 0) {
                weight = selected.getWeight();
            }

            weight--;

            if (weight <= 0) {
                index = (index + 1) % servers.size();
                selected = servers.get(index);
                weight = selected.getWeight();
            }

            currentWeight.set(weight);
            currentIndex.set(index);

            return selected;
        }
    }

    @Override
    public String getName() {
        return "WeightedRoundRobin";
    }

    @Override
    public void reset() {
        currentIndex.set(0);
        currentWeight.set(0);
    }
}

/**
 * IP Hash Strategy - Consistent client-to-server mapping
 * Same client IP always routes to same server (session affinity)
 */
class IPHashStrategy implements LoadBalancingStrategy {
    private String lastClientIP;

    @Override
    public Server selectServer(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        if (lastClientIP != null && !lastClientIP.isEmpty()) {
            return selectServerByIP(servers, lastClientIP);
        }

        return servers.get(0);
    }

    public Server selectServerByIP(List<Server> servers, String clientIP) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        if (clientIP == null || clientIP.isEmpty()) {
            return servers.get(0);
        }

        this.lastClientIP = clientIP;
        int hash = clientIP.hashCode();
        int index = Math.abs(hash % servers.size());
        return servers.get(index);
    }

    @Override
    public String getName() {
        return "IPHash";
    }
}

/**
 * Random Strategy - Random server selection
 */
class RandomStrategy implements LoadBalancingStrategy {
    private final Random random;

    public RandomStrategy() {
        this.random = new Random();
    }

    @Override
    public Server selectServer(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        int index = random.nextInt(servers.size());
        return servers.get(index);
    }

    @Override
    public String getName() {
        return "Random";
    }
}

// ==================== Health Checker ====================

/**
 * Periodic health checker for servers
 * Runs in background to monitor server health
 */
class HealthChecker {
    private final ServerPool serverPool;
    private final Duration checkInterval;
    private final String healthCheckUrl;
    private final int timeoutMs;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final Random random;

    public HealthChecker(ServerPool serverPool, Duration checkInterval,
                         String healthCheckUrl, int timeoutMs) {
        this.serverPool = serverPool;
        this.checkInterval = checkInterval;
        this.healthCheckUrl = healthCheckUrl;
        this.timeoutMs = timeoutMs;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.random = new Random();
    }

    public void start() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            return;
        }

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::checkAllServers,
                0,
                checkInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        System.out.println("Health checker started. Interval: " + checkInterval);
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        scheduler.shutdown();
        System.out.println("Health checker stopped");
    }

    private void checkAllServers() {
        List<Server> servers = serverPool.getAllServers();

        System.out.println("\n--- Health Check Cycle ---");
        for (Server server : servers) {
            boolean healthy = checkServer(server);
            serverPool.updateServerHealth(server.getId(), healthy);
        }
    }

    private boolean checkServer(Server server) {
        try {
            boolean healthy = simulateHealthCheck(server);

            if (healthy) {
                System.out.println("  ✓ " + server.getId() + " is healthy");
            } else {
                System.out.println("  ✗ " + server.getId() + " is unhealthy");
            }

            return healthy;
        } catch (Exception e) {
            System.err.println("  ✗ Health check failed for " + server.getId());
            return false;
        }
    }

    private boolean simulateHealthCheck(Server server) {
        // Simulate: 90% healthy, 10% unhealthy
        // Real implementation would make HTTP GET to server/health
        return random.nextDouble() > 0.1;
    }
}

// ==================== Metrics Tracker ====================

/**
 * Track load balancer statistics
 */
class LoadBalancerMetrics {
    private final AtomicLong totalRequests;
    private final AtomicLong successfulRequests;
    private final AtomicLong failedRequests;
    private final ConcurrentHashMap<String, AtomicLong> requestsPerServer;

    public LoadBalancerMetrics() {
        this.totalRequests = new AtomicLong(0);
        this.successfulRequests = new AtomicLong(0);
        this.failedRequests = new AtomicLong(0);
        this.requestsPerServer = new ConcurrentHashMap<>();
    }

    public void recordRequest(String serverId, boolean success) {
        totalRequests.incrementAndGet();

        if (success) {
            successfulRequests.incrementAndGet();
            requestsPerServer
                    .computeIfAbsent(serverId, k -> new AtomicLong(0))
                    .incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
    }

    public void recordFailedRequest() {
        totalRequests.incrementAndGet();
        failedRequests.incrementAndGet();
    }

    public double getSuccessRate() {
        long total = totalRequests.get();
        return total == 0 ? 0.0 :
                (double) successfulRequests.get() / total * 100;
    }

    public void displayStats() {
        System.out.println("\n=== Load Balancer Metrics ===");
        System.out.println("Total Requests: " + totalRequests.get());
        System.out.println("Successful: " + successfulRequests.get());
        System.out.println("Failed: " + failedRequests.get());
        System.out.printf("Success Rate: %.2f%%\n", getSuccessRate());
        System.out.println("\nRequests Per Server:");
        requestsPerServer.forEach((serverId, count) ->
                System.out.println("  " + serverId + ": " + count.get())
        );
        System.out.println();
    }

    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        requestsPerServer.clear();
    }
}

// ==================== Load Balancer (Main Coordinator) ====================

/**
 * Main load balancer implementation
 * Facade Pattern: Simplifies complex interactions between components
 */
class LoadBalancer {
    private final ServerPool serverPool;
    private volatile LoadBalancingStrategy strategy;
    private final HealthChecker healthChecker;
    private final LoadBalancerMetrics metrics;

    public LoadBalancer(LoadBalancingStrategy strategy) {
        this.serverPool = new ServerPool();
        this.strategy = strategy;
        this.healthChecker = new HealthChecker(
                serverPool,
                Duration.ofSeconds(5),
                "/health",
                3000
        );
        this.metrics = new LoadBalancerMetrics();
    }

    public void start() {
        healthChecker.start();
        System.out.println("\n🚀 Load balancer started with strategy: " +
                strategy.getName());
    }

    public void stop() {
        healthChecker.stop();
        System.out.println("\n🛑 Load balancer stopped");
    }

    public Server routeRequest(String requestId) {
        List<Server> healthyServers = serverPool.getHealthyServers();

        if (healthyServers.isEmpty()) {
            metrics.recordFailedRequest();
            System.err.println("❌ No healthy servers available for request: " + requestId);
            return null;
        }

        Server selected = strategy.selectServer(healthyServers);

        if (selected == null) {
            metrics.recordFailedRequest();
            System.err.println("❌ Strategy failed to select server for: " + requestId);
            return null;
        }

        selected.incrementConnections();
        metrics.recordRequest(selected.getId(), true);

        System.out.println("→ Request " + requestId + " routed to: " +
                selected.getId() + " (connections: " +
                selected.getActiveConnections() + ")");

        return selected;
    }

    public void completeRequest(String serverId, String requestId) {
        Server server = serverPool.getServer(serverId);
        if (server != null) {
            server.decrementConnections();
            System.out.println("✓ Request " + requestId + " completed on: " +
                    serverId + " (connections: " +
                    server.getActiveConnections() + ")");
        }
    }

    public void addServer(Server server) {
        serverPool.addServer(server);
    }

    public void removeServer(String serverId) {
        serverPool.removeServer(serverId);
    }

    public void setStrategy(LoadBalancingStrategy strategy) {
        this.strategy = strategy;
        strategy.reset();
        System.out.println("\n⚡ Strategy changed to: " + strategy.getName());
    }

    public LoadBalancerMetrics getMetrics() {
        return metrics;
    }

    public ServerPool getServerPool() {
        return serverPool;
    }

    public void displayStatus() {
        serverPool.displayStatus();
        metrics.displayStats();
    }
}

// ==================== Demo Class ====================

public class LoadBalancerSystem {

    /**
     * Demo: Round Robin Strategy
     */
    static void demoRoundRobin() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 1: Round Robin Load Balancing");
        System.out.println("=".repeat(60));

        LoadBalancer lb = new LoadBalancer(new RoundRobinStrategy());

        lb.addServer(new Server("server1", "192.168.1.1", 8080, 1));
        lb.addServer(new Server("server2", "192.168.1.2", 8080, 1));
        lb.addServer(new Server("server3", "192.168.1.3", 8080, 1));

        lb.start();

        System.out.println("\nSending 9 requests...");
        for (int i = 1; i <= 9; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(100);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        lb.displayStatus();
        lb.stop();
    }

    /**
     * Demo: Least Connections Strategy
     */
    static void demoLeastConnections() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 2: Least Connections Load Balancing");
        System.out.println("=".repeat(60));

        LoadBalancer lb = new LoadBalancer(new LeastConnectionsStrategy());

        lb.addServer(new Server("server1", "192.168.1.1", 8080, 1));
        lb.addServer(new Server("server2", "192.168.1.2", 8080, 1));
        lb.addServer(new Server("server3", "192.168.1.3", 8080, 1));

        lb.start();

        System.out.println("\nSending requests with varying durations...");

        for (int i = 1; i <= 6; i++) {
            Server server = lb.routeRequest("req-" + i);

            if (i % 2 == 0 && server != null) {
                Thread.sleep(200);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        System.out.println("\nCurrent server loads:");
        for (Server server : lb.getServerPool().getAllServers()) {
            System.out.println("  " + server.getId() + ": " +
                    server.getActiveConnections() + " active connections");
        }

        for (int i = 1; i <= 6; i++) {
            if (i % 2 != 0) {
                Server server = lb.getServerPool().getServer("server" + ((i % 3) + 1));
                if (server != null) {
                    lb.completeRequest(server.getId(), "req-" + i);
                }
            }
        }

        lb.displayStatus();
        lb.stop();
    }

    /**
     * Demo: Weighted Round Robin Strategy
     */
    static void demoWeightedRoundRobin() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 3: Weighted Round Robin Load Balancing");
        System.out.println("=".repeat(60));

        LoadBalancer lb = new LoadBalancer(new WeightedRoundRobinStrategy());

        lb.addServer(new Server("server1", "192.168.1.1", 8080, 3));
        lb.addServer(new Server("server2", "192.168.1.2", 8080, 2));
        lb.addServer(new Server("server3", "192.168.1.3", 8080, 1));

        lb.start();

        System.out.println("\nSending 12 requests (expecting 6:4:2 distribution)...");
        for (int i = 1; i <= 12; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(50);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        lb.displayStatus();
        lb.stop();
    }

    /**
     * Demo: Health Check and Recovery
     */
    static void demoHealthCheck() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 4: Health Check and Recovery");
        System.out.println("=".repeat(60));

        LoadBalancer lb = new LoadBalancer(new RoundRobinStrategy());

        lb.addServer(new Server("server1", "192.168.1.1", 8080, 1));
        lb.addServer(new Server("server2", "192.168.1.2", 8080, 1));
        lb.addServer(new Server("server3", "192.168.1.3", 8080, 1));

        lb.start();

        System.out.println("\nSending initial requests...");
        for (int i = 1; i <= 6; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(100);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        System.out.println("\n⚠️  Manually marking server2 as unhealthy...");
        lb.getServerPool().updateServerHealth("server2", false);

        System.out.println("\nSending requests (server2 should be skipped)...");
        for (int i = 7; i <= 12; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(100);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        System.out.println("\n♥️  Manually marking server2 as healthy...");
        lb.getServerPool().updateServerHealth("server2", true);

        Thread.sleep(6000);

        lb.displayStatus();
        lb.stop();
    }

    /**
     * Demo: Dynamic Server Management
     */
    static void demoDynamicServerManagement() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 5: Dynamic Server Management");
        System.out.println("=".repeat(60));

        LoadBalancer lb = new LoadBalancer(new RoundRobinStrategy());

        lb.addServer(new Server("server1", "192.168.1.1", 8080, 1));
        lb.addServer(new Server("server2", "192.168.1.2", 8080, 1));

        lb.start();

        System.out.println("\nSending requests to 2 servers...");
        for (int i = 1; i <= 4; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(100);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        System.out.println("\n➕ Adding server3 dynamically...");
        lb.addServer(new Server("server3", "192.168.1.3", 8080, 1));

        System.out.println("\nSending requests to 3 servers...");
        for (int i = 5; i <= 10; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(100);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        System.out.println("\n➖ Removing server2...");
        lb.removeServer("server2");

        System.out.println("\nSending requests to 2 remaining servers...");
        for (int i = 11; i <= 14; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(100);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        lb.displayStatus();
        lb.stop();
    }

    /**
     * Demo: Strategy Switching
     */
    static void demoStrategySwitching() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 6: Strategy Switching");
        System.out.println("=".repeat(60));

        LoadBalancer lb = new LoadBalancer(new RoundRobinStrategy());

        lb.addServer(new Server("server1", "192.168.1.1", 8080, 1));
        lb.addServer(new Server("server2", "192.168.1.2", 8080, 1));
        lb.addServer(new Server("server3", "192.168.1.3", 8080, 1));

        lb.start();

        System.out.println("\n--- Using Round Robin ---");
        for (int i = 1; i <= 6; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(50);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        lb.setStrategy(new LeastConnectionsStrategy());
        System.out.println("\n--- Using Least Connections ---");
        for (int i = 7; i <= 12; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(50);
                lb.completeRequest(server.getId(), "req-" + i);
            }
        }

        lb.displayStatus();
        lb.stop();
    }

    /**
     * Demo: Concurrent Requests
     */
    static void demoConcurrentRequests() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 7: Concurrent Request Handling");
        System.out.println("=".repeat(60));

        LoadBalancer lb = new LoadBalancer(new LeastConnectionsStrategy());

        lb.addServer(new Server("server1", "192.168.1.1", 8080, 1));
        lb.addServer(new Server("server2", "192.168.1.2", 8080, 1));
        lb.addServer(new Server("server3", "192.168.1.3", 8080, 1));

        lb.start();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(20);

        System.out.println("\nSending 20 concurrent requests...");

        for (int i = 1; i <= 20; i++) {
            final int requestNum = i;
            executor.submit(() -> {
                try {
                    Server server = lb.routeRequest("concurrent-req-" + requestNum);
                    if (server != null) {
                        Thread.sleep(new Random().nextInt(500) + 100);
                        lb.completeRequest(server.getId(), "concurrent-req-" + requestNum);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Thread.sleep(1000);

        lb.displayStatus();
        lb.stop();
    }

    // ==================== Main Method ====================

    public static void main(String[] args) {
        try {
            System.out.println("\n");
            System.out.println("╔════════════════════════════════════════════════════════╗");
            System.out.println("║      LOAD BALANCER SYSTEM - Complete Demo             ║");
            System.out.println("║                                                        ║");
            System.out.println("║  Demonstrating:                                        ║");
            System.out.println("║  1. Round Robin Strategy                               ║");
            System.out.println("║  2. Least Connections Strategy                         ║");
            System.out.println("║  3. Weighted Round Robin Strategy                      ║");
            System.out.println("║  4. Health Check and Recovery                          ║");
            System.out.println("║  5. Dynamic Server Management                          ║");
            System.out.println("║  6. Strategy Switching                                 ║");
            System.out.println("║  7. Concurrent Request Handling                        ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");

            demoRoundRobin();
            Thread.sleep(2000);

            demoLeastConnections();
            Thread.sleep(2000);

            demoWeightedRoundRobin();
            Thread.sleep(2000);

            demoHealthCheck();
            Thread.sleep(2000);

            demoDynamicServerManagement();
            Thread.sleep(2000);

            demoStrategySwitching();
            Thread.sleep(2000);

            demoConcurrentRequests();

            System.out.println("\n" + "=".repeat(60));
            System.out.println("✅ All demos completed successfully!");
            System.out.println("=".repeat(60) + "\n");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Demo interrupted: " + e.getMessage());
        }
    }
}
