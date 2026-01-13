# Load Balancer - HELLO Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **Multiple Load Balancing Algorithms**
   - Round Robin: Distribute requests equally in rotation
   - Least Connections: Send to server with fewest active connections
   - Weighted Round Robin: Distribute based on server capacity
   - IP Hash: Consistent mapping of client IP to server
   - Random: Random server selection

2. **Server Pool Management**
   - Add servers dynamically
   - Remove servers dynamically
   - Track server status (active/inactive)
   - Maintain list of healthy servers

3. **Health Checks**
   - Periodic health check for each server
   - Mark unhealthy servers as inactive
   - Automatically recover when server becomes healthy
   - Configurable health check interval

4. **Request Distribution**
   - Select server based on chosen algorithm
   - Route request to selected server
   - Handle server unavailability
   - Return response to client

5. **Connection Tracking**
   - Track active connections per server
   - Increment on request start
   - Decrement on request completion
   - Thread-safe counter updates

#### Nice to Have (P1)
- SSL/TLS termination
- Request retries on failure
- Sticky sessions (session affinity)
- Dynamic weight adjustment based on load
- Metrics and monitoring dashboard
- Circuit breaker integration
- Geographic routing
- Rate limiting per server

### Non-Functional Requirements

#### Performance
- **Request routing**: < 5ms overhead
- **Throughput**: Handle 10,000+ requests/second
- **Health check**: < 100ms per server
- **Low latency**: Minimal impact on response time

#### Scalability
- Support 100+ backend servers
- Handle 100,000+ concurrent connections
- Horizontal scaling of load balancer itself

#### Availability
- **Uptime**: 99.99% (4 nines)
- No single point of failure
- Graceful degradation when servers fail
- Automatic recovery

#### Thread Safety
- Multiple threads can route requests concurrently
- Safe concurrent server pool updates
- No race conditions in connection tracking

#### Configurability
- Pluggable load balancing algorithms
- Configurable health check parameters
- Dynamic algorithm switching

### Constraints and Assumptions

**Constraints**:
- Single load balancer instance (not distributed)
- Layer 7 (application layer) load balancing
- HTTP/HTTPS traffic only
- In-memory state (no persistence)

**Assumptions**:
- Backend servers expose health check endpoint
- Servers can handle concurrent connections
- Network is reliable (temporary failures acceptable)
- Client retries handled elsewhere

**Out of Scope**:
- Layer 4 (TCP/UDP) load balancing
- Global load balancing across data centers
- SSL certificate management
- Request transformation/rewriting
- Authentication/authorization

---

## 2️⃣ Core Entities

### Class: Server
**Responsibility**: Represent a backend server

**Key Attributes**:
- `id: String` - Unique server identifier
- `host: String` - Server hostname/IP
- `port: int` - Server port
- `weight: int` - Server capacity weight (for weighted algorithms)
- `isHealthy: AtomicBoolean` - Current health status
- `activeConnections: AtomicInteger` - Current connection count

**Key Methods**:
- `incrementConnections()`: Increment active connections
- `decrementConnections()`: Decrement active connections
- `getActiveConnections()`: Get current connection count
- `isAvailable()`: Check if server can accept requests
- `getAddress()`: Get full server address

**Relationships**:
- Managed by: ServerPool
- Selected by: LoadBalancingStrategy
- Monitored by: HealthChecker

### Class: ServerPool
**Responsibility**: Manage collection of backend servers

**Key Attributes**:
- `servers: CopyOnWriteArrayList<Server>` - All registered servers
- `healthyServers: CopyOnWriteArrayList<Server>` - Currently healthy servers
- `lock: ReadWriteLock` - For safe concurrent access

**Key Methods**:
- `addServer(Server)`: Add new server to pool
- `removeServer(serverId)`: Remove server from pool
- `getHealthyServers()`: Get list of available servers
- `updateServerHealth(serverId, isHealthy)`: Update server status
- `getAllServers()`: Get all servers regardless of health

**Relationships**:
- Contains: Multiple Server instances
- Used by: LoadBalancer, HealthChecker
- Thread-safe: CopyOnWriteArrayList for concurrent reads

### Interface: LoadBalancingStrategy
**Responsibility**: Define algorithm for server selection

**Key Methods**:
- `selectServer(List<Server>)`: Choose server from healthy pool
- `getName()`: Get strategy name

**Relationships**:
- Implemented by: All algorithm classes
- Used by: LoadBalancer
- Strategy pattern: Pluggable algorithms

**Implementations**:
```
LoadBalancingStrategy
    ├─ RoundRobinStrategy
    ├─ LeastConnectionsStrategy
    ├─ WeightedRoundRobinStrategy
    ├─ IPHashStrategy
    └─ RandomStrategy
```

### Class: RoundRobinStrategy
**Responsibility**: Distribute requests equally in rotation

**Key Attributes**:
- `currentIndex: AtomicInteger` - Next server index

**Algorithm**:
```
nextServer = servers[(currentIndex++) % servers.size()]
```

### Class: LeastConnectionsStrategy
**Responsibility**: Route to server with fewest connections

**Algorithm**:
```
select server with min(activeConnections)
```

### Class: WeightedRoundRobinStrategy
**Responsibility**: Distribute based on server weights

**Key Attributes**:
- `currentIndex: AtomicInteger` - Current position
- `currentWeight: AtomicInteger` - Remaining weight for current server

**Algorithm**:
```
Weighted distribution:
Server A (weight=3): gets 3 requests
Server B (weight=1): gets 1 request
Pattern: A, A, A, B, A, A, A, B...
```

### Class: IPHashStrategy
**Responsibility**: Consistent client-to-server mapping

**Algorithm**:
```
serverIndex = hash(clientIP) % servers.size()
```

**Benefit**: Same client always goes to same server (session affinity)

### Class: HealthChecker
**Responsibility**: Monitor server health periodically

**Key Attributes**:
- `serverPool: ServerPool` - Pool to monitor
- `checkInterval: Duration` - Time between checks
- `scheduler: ScheduledExecutorService` - Background scheduler
- `healthCheckUrl: String` - Endpoint to check (e.g., "/health")

**Key Methods**:
- `start()`: Begin periodic health checks
- `stop()`: Stop health checking
- `checkServer(Server)`: Check single server health
- `checkAllServers()`: Check all servers

**Relationships**:
- Monitors: ServerPool
- Updates: Server health status
- Background task: Runs on scheduler

### Class: LoadBalancer
**Responsibility**: Main coordinator for request routing

**Key Attributes**:
- `serverPool: ServerPool` - Backend servers
- `strategy: LoadBalancingStrategy` - Selection algorithm
- `healthChecker: HealthChecker` - Health monitor
- `metrics: LoadBalancerMetrics` - Statistics tracker

**Key Methods**:
- `routeRequest(Request)`: Select server and route request
- `setStrategy(LoadBalancingStrategy)`: Change algorithm
- `addServer(Server)`: Add backend server
- `removeServer(serverId)`: Remove backend server
- `getMetrics()`: Get performance statistics

**Relationships**:
- Uses: ServerPool, LoadBalancingStrategy, HealthChecker
- Coordinates: All components
- Facade pattern: Simple external interface

### Class: LoadBalancerMetrics
**Responsibility**: Track performance statistics

**Key Attributes**:
- `totalRequests: AtomicLong` - Total requests handled
- `successfulRequests: AtomicLong` - Successful routings
- `failedRequests: AtomicLong` - Failed routings
- `requestsPerServer: ConcurrentHashMap<String, AtomicLong>` - Per-server counts

**Key Methods**:
- `recordRequest(serverId, success)`: Record request outcome
- `getStats()`: Get aggregated statistics
- `reset()`: Reset all counters

---

## 3️⃣ API Design

### Class: Server

```java
/**
 * Represents a backend server
 * Thread-safe for concurrent access
 */
public class Server {
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
    
    /**
     * Increment active connection count
     */
    public void incrementConnections() {
        activeConnections.incrementAndGet();
    }
    
    /**
     * Decrement active connection count
     */
    public void decrementConnections() {
        activeConnections.decrementAndGet();
    }
    
    /**
     * Get current active connections
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }
    
    /**
     * Check if server can accept requests
     */
    public boolean isAvailable() {
        return isHealthy.get();
    }
    
    /**
     * Update health status
     */
    public void setHealthy(boolean healthy) {
        isHealthy.set(healthy);
    }
    
    /**
     * Get full server address
     */
    public String getAddress() {
        return host + ":" + port;
    }
    
    // Getters
    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getWeight() { return weight; }
    
    @Override
    public String toString() {
        return String.format("Server[id=%s, address=%s, healthy=%s, connections=%d]",
            id, getAddress(), isHealthy.get(), activeConnections.get());
    }
}
```

### Class: ServerPool

```java
/**
 * Thread-safe pool of backend servers
 */
public class ServerPool {
    private final CopyOnWriteArrayList<Server> servers;
    private final CopyOnWriteArrayList<Server> healthyServers;
    private final ReadWriteLock lock;
    
    public ServerPool() {
        this.servers = new CopyOnWriteArrayList<>();
        this.healthyServers = new CopyOnWriteArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Add server to pool
     * @param server Server to add
     * @return true if added successfully
     */
    public boolean addServer(Server server) {
        lock.writeLock().lock();
        try {
            // Check if server already exists
            if (servers.stream().anyMatch(s -> s.getId().equals(server.getId()))) {
                return false;
            }
            
            servers.add(server);
            if (server.isAvailable()) {
                healthyServers.add(server);
            }
            
            System.out.println("Added server: " + server);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove server from pool
     * @param serverId Server ID to remove
     * @return true if removed successfully
     */
    public boolean removeServer(String serverId) {
        lock.writeLock().lock();
        try {
            Server server = findServerById(serverId);
            if (server == null) {
                return false;
            }
            
            servers.remove(server);
            healthyServers.remove(server);
            
            System.out.println("Removed server: " + serverId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update server health status
     * @param serverId Server ID
     * @param healthy New health status
     */
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
                // Server recovered
                healthyServers.add(server);
                System.out.println("Server recovered: " + serverId);
            } else if (!healthy && wasHealthy) {
                // Server failed
                healthyServers.remove(server);
                System.out.println("Server failed: " + serverId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get list of healthy servers
     * @return List of available servers (thread-safe copy)
     */
    public List<Server> getHealthyServers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(healthyServers);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all servers regardless of health
     * @return List of all servers (thread-safe copy)
     */
    public List<Server> getAllServers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(servers);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get server by ID
     */
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
    
    /**
     * Get count of healthy servers
     */
    public int getHealthyServerCount() {
        return healthyServers.size();
    }
    
    /**
     * Check if pool has any healthy servers
     */
    public boolean hasHealthyServers() {
        return !healthyServers.isEmpty();
    }
}
```

### Interface: LoadBalancingStrategy

```java
/**
 * Strategy interface for server selection algorithms
 */
public interface LoadBalancingStrategy {
    /**
     * Select a server from healthy pool
     * 
     * @param servers List of healthy servers
     * @return Selected server, or null if none available
     */
    Server selectServer(List<Server> servers);
    
    /**
     * Get strategy name
     * @return Name of this strategy
     */
    String getName();
    
    /**
     * Reset strategy state (if any)
     */
    default void reset() {}
}
```

### Class: RoundRobinStrategy

```java
/**
 * Round Robin load balancing strategy
 * Distributes requests equally across servers
 */
public class RoundRobinStrategy implements LoadBalancingStrategy {
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
```

### Class: LeastConnectionsStrategy

```java
/**
 * Least Connections load balancing strategy
 * Sends requests to server with fewest active connections
 */
public class LeastConnectionsStrategy implements LoadBalancingStrategy {
    
    @Override
    public Server selectServer(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        
        // Find server with minimum active connections
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
```

### Class: WeightedRoundRobinStrategy

```java
/**
 * Weighted Round Robin strategy
 * Distributes requests based on server weights
 */
public class WeightedRoundRobinStrategy implements LoadBalancingStrategy {
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
        
        // Simple weighted selection
        synchronized (this) {
            int totalWeight = servers.stream()
                .mapToInt(Server::getWeight)
                .sum();
            
            if (totalWeight == 0) {
                return servers.get(0);
            }
            
            int weight = currentWeight.get();
            int index = currentIndex.get();
            
            Server selected = servers.get(index);
            weight--;
            
            if (weight <= 0) {
                // Move to next server
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
```

### Class: IPHashStrategy

```java
/**
 * IP Hash strategy for consistent routing
 * Same client IP always routes to same server
 */
public class IPHashStrategy implements LoadBalancingStrategy {
    
    /**
     * Select server based on client IP hash
     * @param servers List of servers
     * @param clientIP Client IP address
     * @return Selected server
     */
    public Server selectServer(List<Server> servers, String clientIP) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        
        if (clientIP == null || clientIP.isEmpty()) {
            // Fallback to first server
            return servers.get(0);
        }
        
        int hash = clientIP.hashCode();
        int index = Math.abs(hash % servers.size());
        return servers.get(index);
    }
    
    @Override
    public Server selectServer(List<Server> servers) {
        // Default implementation without IP
        return servers.isEmpty() ? null : servers.get(0);
    }
    
    @Override
    public String getName() {
        return "IPHash";
    }
}
```

### Class: HealthChecker

```java
/**
 * Periodic health checker for servers
 * Runs in background to monitor server health
 */
public class HealthChecker {
    private final ServerPool serverPool;
    private final Duration checkInterval;
    private final String healthCheckUrl;
    private final int timeoutMs;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    
    public HealthChecker(ServerPool serverPool, Duration checkInterval,
                        String healthCheckUrl, int timeoutMs) {
        this.serverPool = serverPool;
        this.checkInterval = checkInterval;
        this.healthCheckUrl = healthCheckUrl;
        this.timeoutMs = timeoutMs;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Start periodic health checks
     */
    public void start() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            return; // Already running
        }
        
        scheduledTask = scheduler.scheduleAtFixedRate(
            this::checkAllServers,
            0,
            checkInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("Health checker started. Interval: " + checkInterval);
    }
    
    /**
     * Stop health checking
     */
    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        scheduler.shutdown();
        System.out.println("Health checker stopped");
    }
    
    /**
     * Check all servers in pool
     */
    private void checkAllServers() {
        List<Server> servers = serverPool.getAllServers();
        
        for (Server server : servers) {
            boolean healthy = checkServer(server);
            serverPool.updateServerHealth(server.getId(), healthy);
        }
    }
    
    /**
     * Check single server health
     * @param server Server to check
     * @return true if healthy
     */
    private boolean checkServer(Server server) {
        try {
            // Simulate HTTP health check
            // In real implementation, use HttpClient
            String url = "http://" + server.getAddress() + healthCheckUrl;
            
            // Simulated check: assume server is healthy
            // Real implementation would make actual HTTP request
            return simulateHealthCheck(server);
            
        } catch (Exception e) {
            System.err.println("Health check failed for " + server.getId() + ": " + 
                             e.getMessage());
            return false;
        }
    }
    
    /**
     * Simulate health check (for demo purposes)
     * Real implementation would use HttpClient
     */
    private boolean simulateHealthCheck(Server server) {
        // In real implementation:
        // HttpClient client = HttpClient.newHttpClient();
        // HttpRequest request = HttpRequest.newBuilder()
        //     .uri(URI.create(url))
        //     .timeout(Duration.ofMillis(timeoutMs))
        //     .GET()
        //     .build();
        // HttpResponse<String> response = client.send(request, 
        //     HttpResponse.BodyHandlers.ofString());
        // return response.statusCode() == 200;
        
        // Simulate: 95% healthy, 5% unhealthy
        return Math.random() > 0.05;
    }
}
```

### Class: LoadBalancer

```java
/**
 * Main load balancer implementation
 * Coordinates all components
 */
public class LoadBalancer {
    private final ServerPool serverPool;
    private volatile LoadBalancingStrategy strategy;
    private final HealthChecker healthChecker;
    private final LoadBalancerMetrics metrics;
    
    public LoadBalancer(LoadBalancingStrategy strategy) {
        this.serverPool = new ServerPool();
        this.strategy = strategy;
        this.healthChecker = new HealthChecker(
            serverPool,
            Duration.ofSeconds(10),
            "/health",
            5000
        );
        this.metrics = new LoadBalancerMetrics();
    }
    
    /**
     * Start load balancer
     */
    public void start() {
        healthChecker.start();
        System.out.println("Load balancer started with strategy: " + 
                         strategy.getName());
    }
    
    /**
     * Stop load balancer
     */
    public void stop() {
        healthChecker.stop();
        System.out.println("Load balancer stopped");
    }
    
    /**
     * Route request to backend server
     * 
     * @param request Request to route
     * @return Selected server, or null if none available
     */
    public Server routeRequest(Request request) {
        List<Server> healthyServers = serverPool.getHealthyServers();
        
        if (healthyServers.isEmpty()) {
            metrics.recordFailedRequest();
            System.err.println("No healthy servers available");
            return null;
        }
        
        // Select server using strategy
        Server selected = strategy.selectServer(healthyServers);
        
        if (selected == null) {
            metrics.recordFailedRequest();
            return null;
        }
        
        // Track connection
        selected.incrementConnections();
        metrics.recordRequest(selected.getId(), true);
        
        System.out.println("Routed request to: " + selected.getId());
        return selected;
    }
    
    /**
     * Complete request (decrement connection count)
     * @param serverId Server that handled request
     */
    public void completeRequest(String serverId) {
        Server server = serverPool.getServer(serverId);
        if (server != null) {
            server.decrementConnections();
        }
    }
    
    /**
     * Add backend server
     */
    public void addServer(Server server) {
        serverPool.addServer(server);
    }
    
    /**
     * Remove backend server
     */
    public void removeServer(String serverId) {
        serverPool.removeServer(serverId);
    }
    
    /**
     * Change load balancing strategy
     */
    public void setStrategy(LoadBalancingStrategy strategy) {
        this.strategy = strategy;
        System.out.println("Strategy changed to: " + strategy.getName());
    }
    
    /**
     * Get current metrics
     */
    public LoadBalancerMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Get server pool
     */
    public ServerPool getServerPool() {
        return serverPool;
    }
}
```

### Class: LoadBalancerMetrics

```java
/**
 * Track load balancer statistics
 */
public class LoadBalancerMetrics {
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
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalRequests.get());
        stats.put("successful", successfulRequests.get());
        stats.put("failed", failedRequests.get());
        stats.put("successRate", getSuccessRate());
        stats.put("requestsPerServer", getRequestsPerServer());
        return stats;
    }
    
    public double getSuccessRate() {
        long total = totalRequests.get();
        return total == 0 ? 0.0 : 
            (double) successfulRequests.get() / total * 100;
    }
    
    private Map<String, Long> getRequestsPerServer() {
        Map<String, Long> result = new HashMap<>();
        requestsPerServer.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    
    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        requestsPerServer.clear();
    }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Route Request (Round Robin - Success)

**Scenario**: Route incoming request using Round Robin

**Sequence**:
1. Client sends HTTP request to load balancer
2. Load balancer receives request
3. Call `routeRequest(request)`
4. Get healthy servers from pool: [Server1, Server2, Server3]
5. Strategy selects server: Round Robin with index=0
6. Select Server1
7. Increment Server1 connections: 5 → 6
8. Record metrics: successful request to Server1
9. Return Server1 to client
10. Forward request to Server1
11. Server1 processes and returns response
12. Call `completeRequest("server1")`
13. Decrement Server1 connections: 6 → 5

**Sequence Diagram**:
```
Client → LoadBalancer: routeRequest(req)
LoadBalancer → ServerPool: getHealthyServers()
ServerPool → LoadBalancer: [S1, S2, S3]
LoadBalancer → Strategy: selectServer([S1, S2, S3])
Strategy → Strategy: index = 0 % 3 = 0
Strategy → LoadBalancer: Server1
LoadBalancer → Server1: incrementConnections()
LoadBalancer → Metrics: recordRequest("server1", true)
LoadBalancer → Client: Server1
Client → Server1: Forward HTTP request
Server1 → Client: HTTP response
Client → LoadBalancer: completeRequest("server1")
LoadBalancer → Server1: decrementConnections()
```

**Round Robin Progression**:
```
Request 1: index=0 → Server1
Request 2: index=1 → Server2
Request 3: index=2 → Server3
Request 4: index=3 → index%3=0 → Server1 (loop)
```

### Flow 2: Route Request (Least Connections)

**Scenario**: Select server with fewest connections

**Current State**:
```
Server1: 8 active connections
Server2: 3 active connections  ← Least connections
Server3: 5 active connections
```

**Sequence**:
1. Request arrives
2. Get healthy servers: [Server1, Server2, Server3]
3. LeastConnectionsStrategy iterates:
   - Server1: 8 connections
   - Server2: 3 connections ← Minimum
   - Server3: 5 connections
4. Select Server2
5. Increment Server2: 3 → 4
6. Route to Server2

**Result**: Distributes load evenly based on actual server load

### Flow 3: Health Check and Recovery

**Scenario**: Periodic health monitoring

**Sequence**:
1. HealthChecker scheduler triggers every 10 seconds
2. Get all servers from pool: [Server1, Server2, Server3]
3. Check Server1: HTTP GET /health → 200 OK ✓
4. Check Server2: HTTP GET /health → Timeout ✗
5. Check Server3: HTTP GET /health → 200 OK ✓
6. Update Server2 health: healthy=false
7. Remove Server2 from healthyServers list
8. Future requests only go to Server1 and Server3
9. Next health check cycle (10s later):
10. Check Server2 again: 200 OK ✓
11. Update Server2 health: healthy=true
12. Add Server2 back to healthyServers
13. Server2 starts receiving traffic again

**Automatic Recovery**:
```
T=0s:   [S1✓, S2✓, S3✓] - All healthy
T=10s:  Health check detects S2 failure
T=10s:  [S1✓, S2✗, S3✓] - S2 marked unhealthy
T=20s:  Traffic only to S1 and S3
T=30s:  Health check detects S2 recovery
T=30s:  [S1✓, S2✓, S3✓] - S2 back online
```

### Flow 4: No Healthy Servers

**Scenario**: All servers fail

**Sequence**:
1. Request arrives
2. Call `routeRequest(request)`
3. Get healthy servers: []
4. healthyServers.isEmpty() == true
5. Record failed request in metrics
6. Log error: "No healthy servers available"
7. Return null to caller
8. Client receives 503 Service Unavailable

**Handling**:
```java
if (healthyServers.isEmpty()) {
    metrics.recordFailedRequest();
    System.err.println("No healthy servers available");
    return null;
}
```

### Flow 5: Dynamic Server Addition

**Scenario**: Add new server to running load balancer

**Sequence**:
1. Admin calls `loadBalancer.addServer(newServer)`
2. LoadBalancer → ServerPool.addServer(server)
3. Acquire write lock
4. Check if server ID already exists: No
5. Add to servers list
6. Check if healthy: Yes
7. Add to healthyServers list
8. Release write lock
9. Print "Added server: server4"
10. Next request can route to new server
11. Health checker will monitor new server

**No Downtime**: Addition happens while handling requests

---

## 5️⃣ Design

### Class Diagram

```
┌─────────────────────────────────────┐
│  Server                             │
│  - id: String                       │
│  - host: String                     │
│  - port: int                        │
│  - weight: int                      │
│  - isHealthy: AtomicBoolean         │
│  - activeConnections: AtomicInteger │
│  + incrementConnections()           │
│  + decrementConnections()           │
│  + isAvailable()                    │
└─────────────────────────────────────┘
           ▲
           │ contains
           │
┌──────────┴──────────────────────────┐
│  ServerPool                         │
│  - servers: CopyOnWriteArrayList    │
│  - healthyServers: CopyOnWrite...   │
│  - lock: ReadWriteLock              │
│  + addServer(Server)                │
│  + removeServer(String)             │
│  + getHealthyServers()              │
│  + updateServerHealth()             │
└─────────────┬───────────────────────┘
              │ used by
              │
┌─────────────▼───────────────────────┐
│  <<interface>>                      │
│  LoadBalancingStrategy              │
│  + selectServer(List<Server>)       │
│  + getName()                        │
└────────▲────────────────────────────┘
         │
         │ implements
         │
    ┌────┴───┬──────────┬─────────┬──────────┐
    │        │          │         │          │
┌───▼──┐ ┌──▼───┐  ┌───▼───┐ ┌──▼────┐ ┌───▼──┐
│Round │ │Least │  │Weight │ │IPHash │ │Random│
│Robin │ │Conn  │  │  RR   │ │       │ │      │
└──────┘ └──────┘  └───────┘ └───────┘ └──────┘

┌─────────────────────────────────────┐
│  HealthChecker                      │
│  - serverPool: ServerPool           │
│  - checkInterval: Duration          │
│  - scheduler: ScheduledExecutor     │
│  + start()                          │
│  + stop()                           │
│  + checkAllServers()                │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  LoadBalancer                       │
│  - serverPool: ServerPool           │
│  - strategy: LoadBalancingStrategy  │
│  - healthChecker: HealthChecker     │
│  - metrics: LoadBalancerMetrics     │
│  + routeRequest(Request)            │
│  + completeRequest(String)          │
│  + addServer(Server)                │
│  + setStrategy(Strategy)            │
└─────────────┬───────────────────────┘
              │
              │ tracks
              ▼
┌─────────────────────────────────────┐
│  LoadBalancerMetrics                │
│  - totalRequests: AtomicLong        │
│  - successfulRequests: AtomicLong   │
│  - failedRequests: AtomicLong       │
│  - requestsPerServer: HashMap       │
│  + recordRequest()                  │
│  + getStats()                       │
└─────────────────────────────────────┘
```

### Design Patterns

**Pattern 1: Strategy Pattern**
- **Why**: Multiple load balancing algorithms with different behaviors
- **Where**: LoadBalancingStrategy interface with 5 implementations
- **How**: Load balancer delegates server selection to strategy
- **Benefit**: Easy to add new algorithms, runtime strategy switching

**Pattern 2: Facade Pattern**
- **Why**: Simplify complex subsystem interactions
- **Where**: LoadBalancer class coordinates ServerPool, Strategy, HealthChecker
- **How**: Simple public methods hide internal complexity
- **Benefit**: Clean API for clients, internal changes don't affect clients

**Pattern 3: Observer Pattern** (implicit)
- **Why**: HealthChecker monitors and updates server status
- **Where**: HealthChecker observes servers, updates ServerPool
- **How**: Periodic checks trigger status updates
- **Benefit**: Automatic recovery, decoupled monitoring

### Thread Safety

**Approach 1: CopyOnWriteArrayList**
```java
private final CopyOnWriteArrayList<Server> servers;
private final CopyOnWriteArrayList<Server> healthyServers;
```
- **Pros**: Lock-free reads, perfect for read-heavy workload
- **Cons**: Expensive writes (copy entire list)
- **Use case**: Server list changes infrequently, reads very frequent

**Approach 2: ReadWriteLock**
```java
private final ReadWriteLock lock;

public List<Server> getHealthyServers() {
    lock.readLock().lock();
    try {
        return new ArrayList<>(healthyServers);
    } finally {
        lock.readLock().unlock();
    }
}
```
- **Pros**: Multiple concurrent readers, exclusive writes
- **Cons**: More complex than synchronized
- **Use case**: Separate read/write operations

**Approach 3: Atomic Operations**
```java
private final AtomicInteger activeConnections;

public void incrementConnections() {
    activeConnections.incrementAndGet();
}
```
- **Pros**: Lock-free, high performance
- **Cons**: Limited to simple operations
- **Use case**: Connection counting

### Algorithm Comparison

| Algorithm | Time | Space | Pros | Cons | Best For |
|-----------|------|-------|------|------|----------|
| Round Robin | O(1) | O(1) | Simple, fair | Ignores load | Equal servers |
| Least Connections | O(n) | O(1) | Load-aware | Overhead | Variable load |
| Weighted RR | O(1) | O(1) | Capacity-aware | Complex | Different capacities |
| IP Hash | O(1) | O(1) | Session affinity | Uneven distribution | Stateful sessions |
| Random | O(1) | O(1) | No state | No fairness | Simple setup |

---

## 6️⃣ Deep Dives

### Topic 1: Algorithm Trade-offs

**Round Robin**:
```
Pros:
✅ O(1) server selection
✅ Fair distribution (each server gets equal requests)
✅ No state beyond index
✅ Simple implementation

Cons:
❌ Ignores server load
❌ Ignores server capacity differences
❌ Can overload slow servers

When to use: Homogeneous servers with similar capacity
```

**Least Connections**:
```
Pros:
✅ Load-aware (considers actual server load)
✅ Automatically adapts to slow servers
✅ Better for long-lived connections

Cons:
❌ O(n) selection (must check all servers)
❌ Short-lived connections may not balance well
❌ Connection count != actual load

When to use: Long-lived connections, varying request durations
```

**Weighted Round Robin**:
```
Pros:
✅ Respects server capacity differences
✅ Fair based on weights
✅ Good for heterogeneous servers

Cons:
❌ Requires manual weight configuration
❌ Doesn't adapt to runtime conditions
❌ More complex state management

When to use: Known capacity differences (CPU, memory, bandwidth)
```

**IP Hash**:
```
Pros:
✅ Session affinity (same client → same server)
✅ No session storage needed
✅ Works with stateful applications

Cons:
❌ Uneven distribution (popular IPs overload servers)
❌ Server failure breaks sessions
❌ Limited load balancing flexibility

When to use: Stateful applications, session persistence required
```

### Topic 2: Health Check Strategies

**Active Health Checks** (Current Implementation):
```java
// Periodic HTTP GET to /health endpoint
GET http://server:port/health
Expected: 200 OK

Pros:
✅ Proactive detection
✅ Configurable interval
✅ Works even without traffic

Cons:
❌ Overhead on servers
❌ False positives (network glitch)
❌ Requires health endpoint
```

**Passive Health Checks** (Alternative):
```java
// Monitor actual request failures
if (request fails 3 consecutive times) {
    markServerUnhealthy();
}

Pros:
✅ No extra requests
✅ Detects actual issues
✅ No false positives

Cons:
❌ Requires traffic to detect
❌ Some requests will fail
❌ Slower detection
```

**Hybrid Approach** (Best):
```java
// Combine both:
// 1. Active checks every 30s
// 2. Passive monitoring on requests
// 3. Quick removal on failure
// 4. Slow recovery (multiple successful checks)

if (activeCheckFails() || consecutiveFailures >= 3) {
    markUnhealthy();
}

if (activeCheckSucceeds() 3 times in a row) {
    markHealthy();
}
```

**Configuration Example**:
```java
HealthCheckConfig config = new HealthCheckConfig.Builder()
    .activeCheckInterval(Duration.ofSeconds(30))
    .activeCheckTimeout(Duration.ofSeconds(5))
    .passiveFailureThreshold(3)
    .recoverySuccessThreshold(3)
    .build();
```

### Topic 3: Consistent Hashing (Advanced)

**Problem with Simple IP Hash**:
```
Servers: [S1, S2, S3]
hash(IP) % 3 → server index

If S2 fails:
Servers: [S1, S3]
hash(IP) % 2 → NEW server index!

Result: ALL sessions redistributed!
```

**Consistent Hashing Solution**:
```java
class ConsistentHashStrategy implements LoadBalancingStrategy {
    private final TreeMap<Integer, Server> ring;
    private final int virtualNodes = 150;
    
    public ConsistentHashStrategy() {
        this.ring = new TreeMap<>();
    }
    
    public void addServer(Server server) {
        for (int i = 0; i < virtualNodes; i++) {
            String key = server.getId() + "#" + i;
            int hash = key.hashCode();
            ring.put(hash, server);
        }
    }
    
    @Override
    public Server selectServer(List<Server> servers, String clientIP) {
        if (ring.isEmpty()) {
            return null;
        }
        
        int hash = clientIP.hashCode();
        
        // Find first server >= hash
        Map.Entry<Integer, Server> entry = ring.ceilingEntry(hash);
        
        if (entry == null) {
            // Wrap around to first server
            entry = ring.firstEntry();
        }
        
        return entry.getValue();
    }
}
```

**Benefits**:
```
Server fails → Only affected keys move
Server adds → Minimal redistribution
Avg: 1/n of keys move when n servers
```

### Topic 4: Connection Draining

**Problem**: Removing server with active connections

**Solution: Graceful Shutdown**:
```java
class Server {
    private volatile boolean accepting = true;
    
    public void startDraining() {
        accepting = false;
        System.out.println("Server entering drain mode: " + id);
    }
    
    public boolean canAcceptNewRequests() {
        return accepting && isHealthy.get();
    }
    
    public boolean hasPendingConnections() {
        return activeConnections.get() > 0;
    }
}

class ServerPool {
    public void removeServerGracefully(String serverId, Duration timeout) {
        Server server = getServer(serverId);
        if (server == null) return;
        
        // Step 1: Stop new requests
        server.startDraining();
        
        // Step 2: Wait for existing connections
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        
        while (server.hasPendingConnections() && 
               System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        
        // Step 3: Force removal after timeout
        int remaining = server.getActiveConnections();
        if (remaining > 0) {
            System.out.println("Forcefully removing server with " + 
                             remaining + " active connections");
        }
        
        removeServer(serverId);
    }
}
```

**Timeline**:
```
T=0s:  Start draining → Stop accepting new requests
T=0-30s: Wait for active connections to complete
T=30s: Remove server (even if connections remain)
```

### Topic 5: Metrics and Monitoring

**Key Metrics to Track**:
```java
class EnhancedMetrics {
    // Throughput
    private final AtomicLong requestsPerSecond;
    
    // Latency
    private final AtomicLong totalLatencyMs;
    private final AtomicLong minLatencyMs;
    private final AtomicLong maxLatencyMs;
    
    // Per-Server Metrics
    private final Map<String, ServerMetrics> serverMetrics;
    
    // Error Rates
    private final AtomicLong timeouts;
    private final AtomicLong rejections;
    
    public void recordRequest(String serverId, long latencyMs, boolean success) {
        requestsPerSecond.incrementAndGet();
        
        if (success) {
            totalLatencyMs.addAndGet(latencyMs);
            updateMinMax(latencyMs);
        }
        
        serverMetrics.computeIfAbsent(serverId, k -> new ServerMetrics())
            .recordRequest(latencyMs, success);
    }
    
    public Map<String, Object> getDetailedStats() {
        return Map.of(
            "throughput_rps", calculateRPS(),
            "avg_latency_ms", calculateAvgLatency(),
            "p50_latency_ms", calculateP50(),
            "p95_latency_ms", calculateP95(),
            "p99_latency_ms", calculateP99(),
            "error_rate", calculateErrorRate(),
            "per_server", getPerServerStats()
        );
    }
}
```

**Dashboard Example**:
```
=== Load Balancer Metrics ===
Throughput: 5,234 req/sec
Success Rate: 99.7%
Avg Latency: 45ms (P95: 120ms, P99: 250ms)

Server Distribution:
  server1: 1,745 req (33.3%) - 42ms avg
  server2: 1,821 req (34.8%) - 48ms avg
  server3: 1,668 req (31.9%) - 43ms avg
  
Errors: 15 timeouts, 3 rejections
```

### Topic 6: Load Balancer High Availability

**Problem**: Single load balancer is single point of failure

**Solution: Active-Passive Failover**:
```
┌──────────────┐
│  Active LB   │ ──► Handles all traffic
└──────────────┘
       │
       │ heartbeat
       ▼
┌──────────────┐
│  Passive LB  │ ──► Standby, takes over if active fails
└──────────────┘

DNS/VIP switches from active to passive on failure
```

**Solution: Active-Active (Better)**:
```
      DNS Round Robin
           │
     ┌─────┴─────┐
     ▼           ▼
┌─────────┐  ┌─────────┐
│  LB 1   │  │  LB 2   │
└────┬────┘  └────┬────┘
     │            │
     └──────┬─────┘
            ▼
    Backend Servers
```

**Consistent State Sharing**:
```java
// Use distributed cache for shared state
RedisClient redis = new RedisClient();

class DistributedHealthChecker {
    public void updateServerHealth(String serverId, boolean healthy) {
        // Update local
        localPool.updateServerHealth(serverId, healthy);
        
        // Publish to other load balancers
        redis.publish("health_updates", 
            new HealthUpdate(serverId, healthy));
    }
    
    public void onHealthUpdate(HealthUpdate update) {
        // Receive update from other LBs
        localPool.updateServerHealth(
            update.getServerId(), 
            update.isHealthy()
        );
    }
}
```

---

## Summary

The Load Balancer LLD demonstrates:

1. **Strategy Pattern**: 5 pluggable algorithms for different use cases
2. **Thread Safety**: CopyOnWriteArrayList, ReadWriteLock, Atomic operations
3. **Health Monitoring**: Active checks with automatic recovery
4. **Metrics Tracking**: Comprehensive statistics for monitoring
5. **Graceful Operations**: Connection draining, dynamic server management
6. **Production Ready**: Error handling, edge cases, extensibility

**Key Takeaways**:
- Choose algorithm based on requirements (fairness vs load-awareness)
- Implement robust health checking for automatic recovery
- Use appropriate thread-safety mechanisms for concurrent access
- Track metrics for visibility and debugging
- Plan for high availability and graceful degradation

**Related Resources**:
- System Design: [Distributed Load Balancer HLD](../HLD/)
- Implementation: [LoadBalancerSystem.java](../LLD/LoadBalancerSystem.java)
- Patterns: [Strategy Pattern](../LLD/Design-Patterns/StrategyPattern.java)

---

*Interview Difficulty: Medium to Hard*
*Time to Complete: 45-60 minutes*
*Key Focus: Strategy Pattern, Thread Safety, Algorithm Trade-offs*
