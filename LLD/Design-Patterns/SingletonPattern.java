import java.util.*;
import java.time.*;

// ==================== SINGLETON PATTERN ====================
// Definition: Ensures a class has only one instance and provides global access to it.
//
// REAL System Design Interview Examples:
// 1. Connection Pool Manager - Single pool for database connections
// 2. Configuration Manager - Single source of application config
// 3. Service Registry - Single registry for service discovery
// 4. Distributed Lock Manager - Coordinating distributed transactions
// 5. Metrics Collector - Single metrics aggregation point
//
// Interview Use Cases:
// - Design Database Layer: Connection pooling
// - Design Microservices: Service registry (Eureka, Consul)
// - Design Distributed System: Distributed locks (Zookeeper, etcd)
// - Design Monitoring: Centralized metrics collection
// - Design Config Management: Single config source

// ==================== EXAMPLE 1: CONNECTION POOL MANAGER ====================
// Used in: HikariCP, Apache Commons DBCP, C3P0
// Interview Question: Design a database connection pooling system

class ConnectionPoolManager {
    private static volatile ConnectionPoolManager instance;
    private Queue<DatabaseConnection> availableConnections;
    private Set<DatabaseConnection> usedConnections;
    private static final int MAX_POOL_SIZE = 20;
    private static final int MIN_POOL_SIZE = 5;
    private int totalConnections;

    private ConnectionPoolManager() {
        availableConnections = new LinkedList<>();
        usedConnections = new HashSet<>();
        totalConnections = 0;
        initializePool();
        System.out.println("🔌 Connection Pool initialized: " + MIN_POOL_SIZE + " connections");
    }

    public static ConnectionPoolManager getInstance() {
        if (instance == null) {
            synchronized (ConnectionPoolManager.class) {
                if (instance == null) {
                    instance = new ConnectionPoolManager();
                }
            }
        }
        return instance;
    }

    private void initializePool() {
        for (int i = 0; i < MIN_POOL_SIZE; i++) {
            availableConnections.offer(new DatabaseConnection("conn-" + (++totalConnections)));
        }
    }

    public synchronized DatabaseConnection getConnection() {
        if (availableConnections.isEmpty() && totalConnections < MAX_POOL_SIZE) {
            DatabaseConnection conn = new DatabaseConnection("conn-" + (++totalConnections));
            System.out.println("➕ Created new connection: " + conn.getId());
            usedConnections.add(conn);
            return conn;
        }

        if (availableConnections.isEmpty()) {
            System.out.println("⏳ Pool exhausted - waiting for connection...");
            return null;
        }

        DatabaseConnection conn = availableConnections.poll();
        usedConnections.add(conn);
        System.out.println("✅ Retrieved connection: " + conn.getId() + 
            " (Available: " + availableConnections.size() + "/" + totalConnections + ")");
        return conn;
    }

    public synchronized void releaseConnection(DatabaseConnection conn) {
        if (usedConnections.remove(conn)) {
            availableConnections.offer(conn);
            System.out.println("🔄 Released connection: " + conn.getId() + 
                " (Available: " + availableConnections.size() + "/" + totalConnections + ")");
        }
    }

    public void getPoolStats() {
        System.out.println("\n📊 Pool Statistics:");
        System.out.println("   Total connections: " + totalConnections);
        System.out.println("   Available: " + availableConnections.size());
        System.out.println("   In use: " + usedConnections.size());
    }
}

class DatabaseConnection {
    private String id;

    public DatabaseConnection(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}

// ==================== EXAMPLE 2: SERVICE REGISTRY ====================
// Used in: Eureka, Consul, Zookeeper, etcd
// Interview Question: Design service discovery for microservices

class ServiceRegistry {
    private static ServiceRegistry instance;
    private Map<String, List<ServiceInstance>> services;
    private Map<String, Long> lastHeartbeat;

    private ServiceRegistry() {
        services = new HashMap<>();
        lastHeartbeat = new HashMap<>();
        System.out.println("🗺️ Service Registry initialized");
    }

    public static synchronized ServiceRegistry getInstance() {
        if (instance == null) {
            instance = new ServiceRegistry();
        }
        return instance;
    }

    public void register(String serviceName, ServiceInstance instance) {
        services.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(instance);
        lastHeartbeat.put(instance.getInstanceId(), System.currentTimeMillis());
        System.out.printf("✅ Registered: %s → %s:%d%n", 
            serviceName, instance.getHost(), instance.getPort());
    }

    public void deregister(String serviceName, String instanceId) {
        List<ServiceInstance> instances = services.get(serviceName);
        if (instances != null) {
            instances.removeIf(i -> i.getInstanceId().equals(instanceId));
            lastHeartbeat.remove(instanceId);
            System.out.println("❌ Deregistered: " + serviceName + " instance " + instanceId);
        }
    }

    public List<ServiceInstance> discover(String serviceName) {
        List<ServiceInstance> instances = services.getOrDefault(serviceName, new ArrayList<>());
        System.out.println("🔍 Discovered " + instances.size() + " instances of " + serviceName);
        return instances;
    }

    public void heartbeat(String instanceId) {
        lastHeartbeat.put(instanceId, System.currentTimeMillis());
    }

    public void displayRegistry() {
        System.out.println("\n📋 Service Registry:");
        services.forEach((service, instances) -> {
            System.out.println("   " + service + " (" + instances.size() + " instances)");
            instances.forEach(i -> System.out.println("      - " + i.getHost() + ":" + i.getPort()));
        });
    }
}

class ServiceInstance {
    private String instanceId;
    private String host;
    private int port;
    private String status;

    public ServiceInstance(String instanceId, String host, int port) {
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.status = "UP";
    }

    public String getInstanceId() { return instanceId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getStatus() { return status; }
}

// ==================== EXAMPLE 3: CONFIGURATION MANAGER ====================
// Used in: Spring Cloud Config, Consul KV, AWS Systems Manager
// Interview Question: Design a configuration management system

class ConfigurationManager {
    private static volatile ConfigurationManager instance;
    private Map<String, Map<String, String>> environmentConfigs;
    private String currentEnvironment;

    private ConfigurationManager() {
        environmentConfigs = new HashMap<>();
        currentEnvironment = "production";
        loadConfigurations();
        System.out.println("⚙️ Configuration Manager initialized");
    }

    public static ConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (ConfigurationManager.class) {
                if (instance == null) {
                    instance = new ConfigurationManager();
                }
            }
        }
        return instance;
    }

    private void loadConfigurations() {
        // Production config
        Map<String, String> prodConfig = new HashMap<>();
        prodConfig.put("db.host", "prod-db.example.com");
        prodConfig.put("db.port", "5432");
        prodConfig.put("cache.host", "prod-redis.example.com");
        prodConfig.put("api.timeout", "30000");
        prodConfig.put("log.level", "INFO");
        environmentConfigs.put("production", prodConfig);

        // Staging config
        Map<String, String> stagingConfig = new HashMap<>();
        stagingConfig.put("db.host", "staging-db.example.com");
        stagingConfig.put("db.port", "5432");
        stagingConfig.put("cache.host", "staging-redis.example.com");
        stagingConfig.put("api.timeout", "60000");
        stagingConfig.put("log.level", "DEBUG");
        environmentConfigs.put("staging", stagingConfig);
    }

    public String getProperty(String key) {
        Map<String, String> config = environmentConfigs.get(currentEnvironment);
        String value = config.getOrDefault(key, "");
        System.out.printf("📖 Config [%s]: %s = %s%n", currentEnvironment, key, value);
        return value;
    }

    public void setEnvironment(String env) {
        this.currentEnvironment = env;
        System.out.println("🔄 Switched to environment: " + env);
    }

    public void reloadConfiguration() {
        System.out.println("🔄 Reloading configuration from remote source...");
        loadConfigurations();
    }
}

// ==================== EXAMPLE 4: METRICS COLLECTOR ====================
// Used in: Prometheus, StatsD, CloudWatch
// Interview Question: Design a metrics collection and aggregation system

class MetricsCollector {
    private static final MetricsCollector instance = new MetricsCollector(); // Eager initialization
    private Map<String, Double> gauges;
    private Map<String, Long> counters;
    private Map<String, List<Double>> histograms;

    private MetricsCollector() {
        gauges = new HashMap<>();
        counters = new HashMap<>();
        histograms = new HashMap<>();
        System.out.println("📊 Metrics Collector initialized");
    }

    public static MetricsCollector getInstance() {
        return instance;
    }

    public void recordGauge(String name, double value) {
        gauges.put(name, value);
        System.out.printf("📈 Gauge: %s = %.2f%n", name, value);
    }

    public void incrementCounter(String name) {
        long count = counters.getOrDefault(name, 0L) + 1;
        counters.put(name, count);
        System.out.printf("➕ Counter: %s = %d%n", name, count);
    }

    public void recordHistogram(String name, double value) {
        histograms.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        System.out.printf("📊 Histogram: %s added value %.2f%n", name, value);
    }

    public void displayMetrics() {
        System.out.println("\n📊 Current Metrics:");
        System.out.println("Gauges: " + gauges);
        System.out.println("Counters: " + counters);
        histograms.forEach((name, values) -> 
            System.out.printf("Histogram %s: %d samples%n", name, values.size()));
    }
}

// ==================== EXAMPLE 5: DISTRIBUTED LOCK MANAGER ====================
// Used in: Zookeeper, etcd, Redis (Redlock)
// Interview Question: Design distributed coordination/locking

class DistributedLockManager {
    private static volatile DistributedLockManager instance;
    private Map<String, LockInfo> locks;
    private Map<String, Long> lockTimeouts;

    private DistributedLockManager() {
        locks = new HashMap<>();
        lockTimeouts = new HashMap<>();
        System.out.println("🔒 Distributed Lock Manager initialized");
    }

    public static DistributedLockManager getInstance() {
        if (instance == null) {
            synchronized (DistributedLockManager.class) {
                if (instance == null) {
                    instance = new DistributedLockManager();
                }
            }
        }
        return instance;
    }

    public synchronized boolean acquireLock(String resource, String clientId, long ttlMs) {
        // Check if lock exists and is not expired
        if (locks.containsKey(resource)) {
            LockInfo existingLock = locks.get(resource);
            long timeout = lockTimeouts.get(resource);
            
            if (System.currentTimeMillis() < timeout) {
                System.out.printf("❌ Lock DENIED: %s is locked by %s%n", resource, existingLock.getClientId());
                return false;
            }
        }

        // Acquire lock
        locks.put(resource, new LockInfo(resource, clientId));
        lockTimeouts.put(resource, System.currentTimeMillis() + ttlMs);
        System.out.printf("🔐 Lock ACQUIRED: %s by %s (TTL: %dms)%n", resource, clientId, ttlMs);
        return true;
    }

    public synchronized boolean releaseLock(String resource, String clientId) {
        LockInfo lock = locks.get(resource);
        
        if (lock == null) {
            System.out.println("⚠️ Lock RELEASE: Resource " + resource + " is not locked");
            return false;
        }

        if (!lock.getClientId().equals(clientId)) {
            System.out.println("❌ Lock RELEASE DENIED: " + clientId + " does not own lock on " + resource);
            return false;
        }

        locks.remove(resource);
        lockTimeouts.remove(resource);
        System.out.println("🔓 Lock RELEASED: " + resource + " by " + clientId);
        return true;
    }

    public void displayLocks() {
        System.out.println("\n🔐 Active Locks:");
        locks.forEach((resource, lock) -> 
            System.out.printf("   %s: held by %s%n", resource, lock.getClientId()));
    }
}

class LockInfo {
    private String resource;
    private String clientId;
    private long acquiredAt;

    public LockInfo(String resource, String clientId) {
        this.resource = resource;
        this.clientId = clientId;
        this.acquiredAt = System.currentTimeMillis();
    }

    public String getClientId() { return clientId; }
}

// ==================== DEMO ====================

public class SingletonPattern {
    public static void main(String[] args) {
        System.out.println("========== SINGLETON PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: CONNECTION POOL MANAGER =====
        System.out.println("===== EXAMPLE 1: CONNECTION POOL (HikariCP/DBCP) =====\n");
        
        ConnectionPoolManager pool1 = ConnectionPoolManager.getInstance();
        ConnectionPoolManager pool2 = ConnectionPoolManager.getInstance();
        
        System.out.println("Both instances are same? " + (pool1 == pool2));
        
        DatabaseConnection conn1 = pool1.getConnection();
        DatabaseConnection conn2 = pool1.getConnection();
        DatabaseConnection conn3 = pool2.getConnection();
        
        pool1.getPoolStats();
        
        pool1.releaseConnection(conn1);
        pool2.releaseConnection(conn2);
        
        pool1.getPoolStats();

        // ===== EXAMPLE 2: SERVICE REGISTRY =====
        System.out.println("\n\n===== EXAMPLE 2: SERVICE REGISTRY (Eureka/Consul) =====\n");
        
        ServiceRegistry registry1 = ServiceRegistry.getInstance();
        ServiceRegistry registry2 = ServiceRegistry.getInstance();
        
        System.out.println("Both instances are same? " + (registry1 == registry2));
        
        registry1.register("user-service", 
            new ServiceInstance("user-svc-1", "192.168.1.10", 8080));
        registry1.register("user-service", 
            new ServiceInstance("user-svc-2", "192.168.1.11", 8080));
        registry2.register("order-service", 
            new ServiceInstance("order-svc-1", "192.168.1.20", 8081));
        
        registry1.displayRegistry();
        
        List<ServiceInstance> userServices = registry2.discover("user-service");
        System.out.println("\nDiscovered instances: " + userServices.size());

        // ===== EXAMPLE 3: CONFIGURATION MANAGER =====
        System.out.println("\n\n===== EXAMPLE 3: CONFIGURATION MANAGER (Spring Cloud Config) =====\n");
        
        ConfigurationManager config1 = ConfigurationManager.getInstance();
        ConfigurationManager config2 = ConfigurationManager.getInstance();
        
        System.out.println("Both instances are same? " + (config1 == config2));
        
        config1.getProperty("db.host");
        config1.getProperty("cache.host");
        config1.getProperty("api.timeout");
        
        System.out.println();
        config1.setEnvironment("staging");
        config2.getProperty("db.host"); // Same instance, sees environment change
        config2.getProperty("log.level");

        // ===== EXAMPLE 4: METRICS COLLECTOR =====
        System.out.println("\n\n===== EXAMPLE 4: METRICS COLLECTOR (Prometheus/StatsD) =====\n");
        
        MetricsCollector metrics1 = MetricsCollector.getInstance();
        MetricsCollector metrics2 = MetricsCollector.getInstance();
        
        System.out.println("Both instances are same? " + (metrics1 == metrics2));
        
        metrics1.recordGauge("cpu_usage", 75.5);
        metrics1.recordGauge("memory_usage", 82.3);
        metrics2.incrementCounter("http_requests_total");
        metrics2.incrementCounter("http_requests_total");
        metrics1.recordHistogram("request_latency_ms", 150.0);
        metrics1.recordHistogram("request_latency_ms", 200.0);
        
        metrics1.displayMetrics();

        // ===== EXAMPLE 5: DISTRIBUTED LOCK MANAGER =====
        System.out.println("\n\n===== EXAMPLE 5: DISTRIBUTED LOCK (Zookeeper/etcd/Redis) =====\n");
        
        DistributedLockManager lockMgr1 = DistributedLockManager.getInstance();
        DistributedLockManager lockMgr2 = DistributedLockManager.getInstance();
        
        System.out.println("Both instances are same? " + (lockMgr1 == lockMgr2));
        
        lockMgr1.acquireLock("inventory:product-123", "service-A", 5000);
        lockMgr2.acquireLock("inventory:product-123", "service-B", 5000); // Will fail
        
        lockMgr1.acquireLock("orders:order-456", "service-B", 5000);
        
        lockMgr1.displayLocks();
        
        lockMgr1.releaseLock("inventory:product-123", "service-A");
        lockMgr2.acquireLock("inventory:product-123", "service-B", 5000); // Now succeeds

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Connection pooling (HikariCP, DBCP)");
        System.out.println("• Service discovery (Eureka, Consul, Zookeeper)");
        System.out.println("• Configuration management (Spring Cloud Config)");
        System.out.println("• Metrics collection (Prometheus, StatsD, CloudWatch)");
        System.out.println("• Distributed locks (Zookeeper, etcd, Redis Redlock)");
    }
}
