import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INTERVIEW-READY Load Balancer System
 * Time to complete: 45-60 minutes
 * Focus: Core concepts, 2 algorithms, clean design
 */

// ==================== Server ====================
class Server {
    private final String id;
    private final String host;
    private final int port;
    private final AtomicInteger activeConnections;
    private boolean isHealthy;

    public Server(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.activeConnections = new AtomicInteger(0);
        this.isHealthy = true;
    }

    public void incrementConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementConnections() {
        activeConnections.decrementAndGet();
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public boolean isHealthy() {
        return isHealthy;
    }

    public void setHealthy(boolean healthy) {
        this.isHealthy = healthy;
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }

    @Override
    public String toString() {
        return id + " (" + host + ":" + port + ") - Connections: " + 
               activeConnections.get() + ", Healthy: " + isHealthy;
    }
}

// ==================== Strategy Pattern ====================
interface LoadBalancingStrategy {
    Server selectServer(List<Server> servers);
}

// Round Robin: Distribute equally
class RoundRobinStrategy implements LoadBalancingStrategy {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public Server selectServer(List<Server> servers) {
        if (servers.isEmpty()) return null;
        int i = Math.abs(index.getAndIncrement() % servers.size());
        return servers.get(i);
    }
}

// Least Connections: Send to least loaded server
class LeastConnectionsStrategy implements LoadBalancingStrategy {
    @Override
    public Server selectServer(List<Server> servers) {
        if (servers.isEmpty()) return null;
        
        Server selected = servers.get(0);
        for (Server server : servers) {
            if (server.getActiveConnections() < selected.getActiveConnections()) {
                selected = server;
            }
        }
        return selected;
    }
}

// ==================== Server Pool ====================
class ServerPool {
    private final List<Server> servers = new CopyOnWriteArrayList<>();

    public void addServer(Server server) {
        servers.add(server);
        System.out.println("Added: " + server.getId());
    }

    public void removeServer(String serverId) {
        servers.removeIf(s -> s.getId().equals(serverId));
        System.out.println("Removed: " + serverId);
    }

    public List<Server> getHealthyServers() {
        List<Server> healthy = new ArrayList<>();
        for (Server server : servers) {
            if (server.isHealthy()) {
                healthy.add(server);
            }
        }
        return healthy;
    }

    public Server getServer(String id) {
        for (Server server : servers) {
            if (server.getId().equals(id)) {
                return server;
            }
        }
        return null;
    }
}

// ==================== Load Balancer ====================
class LoadBalancer {
    private final ServerPool serverPool;
    private LoadBalancingStrategy strategy;

    public LoadBalancer(LoadBalancingStrategy strategy) {
        this.serverPool = new ServerPool();
        this.strategy = strategy;
    }

    public void addServer(Server server) {
        serverPool.addServer(server);
    }

    public void removeServer(String serverId) {
        serverPool.removeServer(serverId);
    }

    public void setStrategy(LoadBalancingStrategy strategy) {
        this.strategy = strategy;
    }

    // Main method: Route request to backend server
    public Server routeRequest(String requestId) {
        List<Server> healthyServers = serverPool.getHealthyServers();
        
        if (healthyServers.isEmpty()) {
            System.out.println("ERROR: No healthy servers for " + requestId);
            return null;
        }

        Server selected = strategy.selectServer(healthyServers);
        if (selected != null) {
            selected.incrementConnections();
            System.out.println(requestId + " -> " + selected.getId());
        }
        
        return selected;
    }

    public void completeRequest(String serverId) {
        Server server = serverPool.getServer(serverId);
        if (server != null) {
            server.decrementConnections();
        }
    }

    public void showStatus() {
        System.out.println("\n=== Load Balancer Status ===");
        for (Server server : serverPool.getHealthyServers()) {
            System.out.println(server);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class LoadBalancerSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Load Balancer Demo ===\n");

        // Create load balancer with Round Robin
        LoadBalancer lb = new LoadBalancer(new RoundRobinStrategy());

        // Add servers
        lb.addServer(new Server("server1", "192.168.1.1", 8080));
        lb.addServer(new Server("server2", "192.168.1.2", 8080));
        lb.addServer(new Server("server3", "192.168.1.3", 8080));

        // Test Round Robin
        System.out.println("\n--- Round Robin Test ---");
        for (int i = 1; i <= 6; i++) {
            Server server = lb.routeRequest("req-" + i);
            if (server != null) {
                Thread.sleep(50);
                lb.completeRequest(server.getId());
            }
        }

        lb.showStatus();

        // Switch to Least Connections
        System.out.println("--- Switching to Least Connections ---");
        lb.setStrategy(new LeastConnectionsStrategy());

        // Simulate uneven load
        lb.routeRequest("req-7"); // Goes to server1
        lb.routeRequest("req-8"); // Goes to server2
        lb.routeRequest("req-9"); // Goes to server3
        
        // Complete only req-7
        lb.completeRequest("server1");
        
        // Next requests should prefer server1 (least connections)
        lb.routeRequest("req-10"); // Should go to server1
        lb.routeRequest("req-11"); // Should go to server1

        lb.showStatus();

        System.out.println("✅ Demo complete!");
    }
}
