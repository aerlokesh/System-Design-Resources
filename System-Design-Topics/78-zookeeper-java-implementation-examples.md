# 🐘 Topic 78: ZooKeeper Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Apache ZooKeeper using **Apache Curator Framework** (the standard high-level client). Every pattern includes production-ready code with leader election, distributed locks, service discovery, configuration management, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Client Setup & Configuration (Curator)](#1-client-setup--configuration-curator)
- [2. CRUD on ZNodes](#2-crud-on-znodes)
- [3. Distributed Lock (InterProcessMutex)](#3-distributed-lock-interprocessmutex)
- [4. Leader Election](#4-leader-election)
- [5. Service Discovery (Registration)](#5-service-discovery-registration)
- [6. Service Discovery (Lookup)](#6-service-discovery-lookup)
- [7. Distributed Barrier](#7-distributed-barrier)
- [8. Watch & Listen for Changes](#8-watch--listen-for-changes)
- [9. Configuration Management](#9-configuration-management)
- [10. Distributed Queue](#10-distributed-queue)
- [11. Read-Write Lock](#11-read-write-lock)
- [12. Distributed Counter](#12-distributed-counter)
- [13. Ephemeral Nodes (Presence)](#13-ephemeral-nodes-presence)
- [14. Distributed Semaphore](#14-distributed-semaphore)
- [15. Sequence / ID Generator](#15-sequence--id-generator)
- [16. Group Membership](#16-group-membership)
- [17. Cache (PathChildrenCache)](#17-cache-pathchildrencache)
- [18. Health Check & Session Management](#18-health-check--session-management)
- [19. ACL & Security](#19-acl--security)
- [20. Connection Handling & Retry](#20-connection-handling--retry)
- [🏆 Pattern Selection Cheat Sheet](#-pattern-selection-cheat-sheet)

---

## 1. Client Setup & Configuration (Curator)

```java
@Configuration
public class ZooKeeperConfig {
    
    @Bean
    public CuratorFramework curatorClient() {
        CuratorFramework client = CuratorFrameworkFactory.builder()
            .connectString("zk1:2181,zk2:2181,zk3:2181")  // ZK ensemble
            .sessionTimeoutMs(30_000)        // Session timeout (heartbeat)
            .connectionTimeoutMs(10_000)     // Initial connection timeout
            .retryPolicy(new ExponentialBackoffRetry(
                1000,  // Base sleep time (ms)
                5,     // Max retries
                10_000 // Max sleep time (ms)
            ))
            .namespace("myapp")  // All paths prefixed with /myapp
            .build();
        
        client.start();
        
        // Wait for connection
        try {
            if (!client.blockUntilConnected(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to connect to ZooKeeper within 30s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while connecting to ZK", e);
        }
        
        log.info("Connected to ZooKeeper ensemble");
        return client;
    }
    
    @PreDestroy
    public void closeClient(CuratorFramework client) {
        if (client != null) {
            client.close();
            log.info("ZooKeeper client closed");
        }
    }
}
```

---

## 2. CRUD on ZNodes

```java
@Service
public class ZNodeCrudService {
    private final CuratorFramework client;
    
    // ====== CREATE (persistent node) ======
    public String createNode(String path, byte[] data) throws Exception {
        return client.create()
            .creatingParentsIfNeeded()  // Create parent paths if missing
            .withMode(CreateMode.PERSISTENT)
            .forPath(path, data);
    }
    
    // ====== CREATE (ephemeral — auto-deleted when session ends) ======
    public String createEphemeral(String path, byte[] data) throws Exception {
        return client.create()
            .creatingParentsIfNeeded()
            .withMode(CreateMode.EPHEMERAL)
            .forPath(path, data);
    }
    
    // ====== CREATE (sequential — unique ordered name) ======
    public String createSequential(String pathPrefix, byte[] data) throws Exception {
        return client.create()
            .creatingParentsIfNeeded()
            .withMode(CreateMode.PERSISTENT_SEQUENTIAL)
            .forPath(pathPrefix, data);
        // Returns: pathPrefix + 10-digit sequence number (e.g., /locks/lock-0000000001)
    }
    
    // ====== READ ======
    public byte[] getData(String path) throws Exception {
        return client.getData().forPath(path);
    }
    
    public String getDataAsString(String path) throws Exception {
        byte[] data = client.getData().forPath(path);
        return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }
    
    // ====== UPDATE (with version check for optimistic locking) ======
    public void setData(String path, byte[] data) throws Exception {
        client.setData().forPath(path, data);
    }
    
    public void setDataWithVersion(String path, byte[] data, int expectedVersion) throws Exception {
        client.setData()
            .withVersion(expectedVersion)  // Fails if version doesn't match (CAS)
            .forPath(path, data);
    }
    
    // ====== DELETE ======
    public void deleteNode(String path) throws Exception {
        client.delete()
            .guaranteed()           // Retry until deleted (even after connection loss)
            .deletingChildrenIfNeeded()
            .forPath(path);
    }
    
    // ====== CHECK EXISTS ======
    public boolean exists(String path) throws Exception {
        return client.checkExists().forPath(path) != null;
    }
    
    // ====== LIST CHILDREN ======
    public List<String> getChildren(String path) throws Exception {
        return client.getChildren().forPath(path);
    }
}
```

---

## 3. Distributed Lock (InterProcessMutex)

```java
@Service
public class DistributedLockService {
    private final CuratorFramework client;
    
    // ====== Reentrant Mutex Lock (most common) ======
    public <T> T executeWithLock(String lockPath, Duration timeout, Callable<T> action) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, "/locks/" + lockPath);
        
        if (!lock.acquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new LockAcquisitionException("Failed to acquire lock: " + lockPath + " within " + timeout);
        }
        
        try {
            log.info("Lock acquired: {}", lockPath);
            return action.call();
        } finally {
            lock.release();
            log.info("Lock released: {}", lockPath);
        }
    }
    
    // ====== Non-blocking try-lock ======
    public <T> Optional<T> tryExecuteWithLock(String lockPath, Callable<T> action) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, "/locks/" + lockPath);
        
        if (lock.acquire(0, TimeUnit.MILLISECONDS)) {  // Non-blocking
            try {
                return Optional.of(action.call());
            } finally {
                lock.release();
            }
        }
        return Optional.empty();  // Lock busy
    }
    
    // ====== Multi-lock (acquire multiple locks atomically) ======
    public void executeWithMultiLock(List<String> lockPaths, Runnable action) throws Exception {
        List<InterProcessLock> locks = lockPaths.stream()
            .map(path -> new InterProcessMutex(client, "/locks/" + path))
            .collect(Collectors.toList());
        
        InterProcessMultiLock multiLock = new InterProcessMultiLock(locks);
        
        if (!multiLock.acquire(5, TimeUnit.SECONDS)) {
            throw new LockAcquisitionException("Failed to acquire multi-lock");
        }
        
        try {
            action.run();
        } finally {
            multiLock.release();
        }
    }
    
    // ====== Use case: Prevent concurrent order processing ======
    public OrderResult processOrderExclusively(String orderId) throws Exception {
        return executeWithLock("order:" + orderId, Duration.ofSeconds(10), () -> {
            // Only ONE instance processes this order at a time
            Order order = orderRepository.findById(orderId);
            if (order.getStatus() == OrderStatus.PENDING) {
                return processOrder(order);
            }
            return OrderResult.alreadyProcessed();
        });
    }
}
```

---

## 4. Leader Election

```java
@Service
public class LeaderElectionService implements Closeable {
    private final CuratorFramework client;
    private LeaderSelector leaderSelector;
    private volatile boolean isLeader = false;
    
    @PostConstruct
    public void startElection() {
        leaderSelector = new LeaderSelector(client, "/election/scheduler-leader", 
            new LeaderSelectorListenerAdapter() {
                @Override
                public void takeLeadership(CuratorFramework client) throws Exception {
                    // This method is called when THIS instance becomes leader
                    isLeader = true;
                    log.info("🏆 This instance is now the LEADER");
                    Metrics.gauge("leader.status", 1);
                    
                    try {
                        // Leader work: run scheduled jobs, coordinate workers, etc.
                        performLeaderDuties();
                    } finally {
                        isLeader = false;
                        log.info("Leadership relinquished");
                        Metrics.gauge("leader.status", 0);
                    }
                    // When this method returns, leadership is released
                    // and election starts again
                }
            });
        
        leaderSelector.autoRequeue();  // Re-enter election after losing leadership
        leaderSelector.start();
        log.info("Joined leader election for scheduler");
    }
    
    private void performLeaderDuties() throws InterruptedException {
        while (isLeader) {
            // Only leader runs these jobs
            runScheduledJobs();
            rebalancePartitions();
            cleanupExpiredData();
            
            Thread.sleep(10_000);  // Leader loop every 10s
        }
    }
    
    public boolean isLeader() {
        return isLeader;
    }
    
    // ====== Latch-based election (simpler — leader until crash) ======
    public void simpleLeaderElection() {
        LeaderLatch latch = new LeaderLatch(client, "/election/worker-leader", instanceId);
        latch.start();
        
        latch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                log.info("Became leader!");
                startLeaderTasks();
            }
            
            @Override
            public void notLeader() {
                log.info("Lost leadership");
                stopLeaderTasks();
            }
        });
    }
    
    @Override
    public void close() {
        if (leaderSelector != null) {
            leaderSelector.close();
        }
    }
}
```

---

## 5. Service Discovery (Registration)

```java
@Service
public class ServiceRegistrationService implements Closeable {
    private final ServiceDiscovery<ServiceMetadata> serviceDiscovery;
    private ServiceInstance<ServiceMetadata> thisInstance;
    
    public ServiceRegistrationService(CuratorFramework client) throws Exception {
        this.serviceDiscovery = ServiceDiscoveryBuilder.builder(ServiceMetadata.class)
            .client(client)
            .basePath("/services")
            .serializer(new JsonInstanceSerializer<>(ServiceMetadata.class))
            .build();
        
        serviceDiscovery.start();
    }
    
    @PostConstruct
    public void register() throws Exception {
        ServiceMetadata metadata = new ServiceMetadata(
            "1.0.0",
            Map.of("region", "us-east-1", "zone", "us-east-1a"),
            System.currentTimeMillis()
        );
        
        thisInstance = ServiceInstance.<ServiceMetadata>builder()
            .name("order-service")
            .id(UUID.randomUUID().toString())
            .address(getLocalIp())
            .port(8080)
            .payload(metadata)
            .serviceType(ServiceType.DYNAMIC)  // Ephemeral — auto-removed on crash
            .build();
        
        serviceDiscovery.registerService(thisInstance);
        log.info("Registered service: {} at {}:{}", 
            thisInstance.getName(), thisInstance.getAddress(), thisInstance.getPort());
    }
    
    @PreDestroy
    @Override
    public void close() throws Exception {
        if (thisInstance != null) {
            serviceDiscovery.unregisterService(thisInstance);
            log.info("Unregistered service: {}", thisInstance.getId());
        }
        serviceDiscovery.close();
    }
}
```

---

## 6. Service Discovery (Lookup)

```java
@Service
public class ServiceLookupService {
    private final ServiceDiscovery<ServiceMetadata> serviceDiscovery;
    private final Map<String, ServiceProvider<ServiceMetadata>> providers = new ConcurrentHashMap<>();
    
    // ====== Get one instance (load balanced) ======
    public ServiceInstance<ServiceMetadata> getInstance(String serviceName) throws Exception {
        ServiceProvider<ServiceMetadata> provider = providers.computeIfAbsent(serviceName, name -> {
            try {
                ServiceProvider<ServiceMetadata> p = serviceDiscovery.serviceProviderBuilder()
                    .serviceName(name)
                    .providerStrategy(new RoundRobinStrategy<>())  // or RandomStrategy
                    .build();
                p.start();
                return p;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create provider for " + name, e);
            }
        });
        
        ServiceInstance<ServiceMetadata> instance = provider.getInstance();
        if (instance == null) {
            throw new ServiceUnavailableException("No instances available for: " + serviceName);
        }
        return instance;
    }
    
    // ====== Get all instances ======
    public List<ServiceInstance<ServiceMetadata>> getAllInstances(String serviceName) throws Exception {
        Collection<ServiceInstance<ServiceMetadata>> instances = 
            serviceDiscovery.queryForInstances(serviceName);
        return new ArrayList<>(instances);
    }
    
    // ====== Build URL for service call ======
    public String getServiceUrl(String serviceName, String path) throws Exception {
        ServiceInstance<ServiceMetadata> instance = getInstance(serviceName);
        return String.format("http://%s:%d%s", instance.getAddress(), instance.getPort(), path);
    }
}
```

---

## 7. Distributed Barrier

```java
@Service
public class DistributedBarrierService {
    private final CuratorFramework client;
    
    // ====== Double Barrier: all N workers start/finish together ======
    public void executeWithBarrier(String barrierPath, int memberCount, Runnable work) throws Exception {
        DistributedDoubleBarrier barrier = new DistributedDoubleBarrier(
            client, "/barriers/" + barrierPath, memberCount);
        
        // Enter barrier — blocks until all N members enter
        log.info("Waiting at entry barrier ({} members)...", memberCount);
        barrier.enter(30, TimeUnit.SECONDS);
        log.info("All members entered! Starting work...");
        
        try {
            work.run();
        } finally {
            // Leave barrier — blocks until all N members leave
            log.info("Waiting at exit barrier...");
            barrier.leave(60, TimeUnit.SECONDS);
            log.info("All members completed!");
        }
    }
    
    // Use case: Coordinate map-reduce phases
    // Phase 1: All workers map data (barrier ensures all start together)
    // Phase 2: All workers finish mapping before reduce begins
}
```

---

## 8. Watch & Listen for Changes

```java
@Service
public class ZkWatchService {
    private final CuratorFramework client;
    
    // ====== Watch node data changes (persistent watch with Curator) ======
    public void watchNode(String path, Consumer<byte[]> onChange) throws Exception {
        NodeCache cache = new NodeCache(client, path);
        cache.getListenable().addListener(() -> {
            ChildData currentData = cache.getCurrentData();
            if (currentData != null) {
                onChange.accept(currentData.getData());
                log.info("Node {} changed: {}", path, new String(currentData.getData()));
            } else {
                log.info("Node {} deleted", path);
            }
        });
        cache.start(true);  // buildInitial=true: fetch current value immediately
    }
    
    // ====== Watch children changes (service instances, etc.) ======
    public void watchChildren(String parentPath, 
                               Consumer<PathChildrenCacheEvent> onEvent) throws Exception {
        PathChildrenCache cache = new PathChildrenCache(client, parentPath, true);
        cache.getListenable().addListener((client, event) -> {
            switch (event.getType()) {
                case CHILD_ADDED:
                    log.info("Child added: {}", event.getData().getPath());
                    onEvent.accept(event);
                    break;
                case CHILD_REMOVED:
                    log.info("Child removed: {}", event.getData().getPath());
                    onEvent.accept(event);
                    break;
                case CHILD_UPDATED:
                    log.info("Child updated: {}", event.getData().getPath());
                    onEvent.accept(event);
                    break;
            }
        });
        cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
    }
    
    // ====== CuratorCache (Curator 5.x — recommended) ======
    public void watchWithCuratorCache(String path, Consumer<CuratorCacheListenerBuilder> listener) {
        CuratorCache cache = CuratorCache.build(client, path);
        cache.listenable().addListener(CuratorCacheListener.builder()
            .forCreates(node -> log.info("Created: {}", node.getPath()))
            .forChanges((oldNode, newNode) -> log.info("Changed: {}", newNode.getPath()))
            .forDeletes(oldNode -> log.info("Deleted: {}", oldNode.getPath()))
            .build());
        cache.start();
    }
}
```

---

## 9. Configuration Management

```java
@Service
public class DistributedConfigService {
    private final CuratorFramework client;
    private final ConcurrentHashMap<String, String> localCache = new ConcurrentHashMap<>();
    
    // ====== Store config (available to all instances) ======
    public void setConfig(String key, String value) throws Exception {
        String path = "/config/" + key;
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        
        if (client.checkExists().forPath(path) != null) {
            client.setData().forPath(path, data);
        } else {
            client.create().creatingParentsIfNeeded().forPath(path, data);
        }
        
        localCache.put(key, value);
        log.info("Config set: {} = {}", key, value);
    }
    
    // ====== Get config (with local cache + watch for updates) ======
    public String getConfig(String key) throws Exception {
        String cached = localCache.get(key);
        if (cached != null) return cached;
        
        String path = "/config/" + key;
        byte[] data = client.getData().forPath(path);
        String value = new String(data, StandardCharsets.UTF_8);
        localCache.put(key, value);
        
        // Watch for updates
        watchConfigChange(key);
        
        return value;
    }
    
    private void watchConfigChange(String key) throws Exception {
        String path = "/config/" + key;
        NodeCache cache = new NodeCache(client, path);
        cache.getListenable().addListener(() -> {
            ChildData current = cache.getCurrentData();
            if (current != null) {
                String newValue = new String(current.getData(), StandardCharsets.UTF_8);
                String oldValue = localCache.put(key, newValue);
                if (!newValue.equals(oldValue)) {
                    log.info("Config updated: {} = {} (was: {})", key, newValue, oldValue);
                    notifyConfigListeners(key, newValue);
                }
            }
        });
        cache.start();
    }
    
    // ====== Feature flags via ZooKeeper ======
    public boolean isFeatureEnabled(String featureName) {
        String value = localCache.getOrDefault("feature:" + featureName, "false");
        return Boolean.parseBoolean(value);
    }
}
```

---

## 10. Distributed Queue

```java
@Service
public class ZkDistributedQueue {
    private final CuratorFramework client;
    
    // ====== Simple FIFO queue ======
    public void enqueue(String queueName, byte[] data) throws Exception {
        DistributedQueue<byte[]> queue = QueueBuilder.builder(
            client, 
            new QueueConsumer<byte[]>() {
                @Override
                public void consumeMessage(byte[] message) throws Exception {
                    processMessage(message);
                }
                @Override
                public void stateChanged(CuratorFramework c, ConnectionState newState) {
                    log.info("Queue connection state: {}", newState);
                }
            },
            new QueueSerializer<byte[]>() {
                @Override public byte[] serialize(byte[] item) { return item; }
                @Override public byte[] deserialize(byte[] bytes) { return bytes; }
            },
            "/queues/" + queueName
        ).buildQueue();
        
        queue.start();
        queue.put(data);
    }
    
    // ====== Priority queue ======
    public void enqueuePriority(String queueName, byte[] data, int priority) throws Exception {
        DistributedPriorityQueue<byte[]> queue = QueueBuilder.builder(
            client, consumer, serializer, "/queues/" + queueName)
            .buildPriorityQueue(0);  // min priority
        
        queue.start();
        queue.put(data, priority);  // Lower number = higher priority
    }
}
```

---

## 11. Read-Write Lock

```java
@Service
public class ZkReadWriteLockService {
    private final CuratorFramework client;
    
    // ====== Multiple readers OR one exclusive writer ======
    public <T> T executeWithReadLock(String resource, Callable<T> action) throws Exception {
        InterProcessReadWriteLock rwLock = new InterProcessReadWriteLock(
            client, "/locks/rw/" + resource);
        InterProcessMutex readLock = rwLock.readLock();
        
        if (!readLock.acquire(5, TimeUnit.SECONDS)) {
            throw new LockAcquisitionException("Read lock timeout: " + resource);
        }
        try {
            return action.call();
        } finally {
            readLock.release();
        }
    }
    
    public <T> T executeWithWriteLock(String resource, Callable<T> action) throws Exception {
        InterProcessReadWriteLock rwLock = new InterProcessReadWriteLock(
            client, "/locks/rw/" + resource);
        InterProcessMutex writeLock = rwLock.writeLock();
        
        if (!writeLock.acquire(10, TimeUnit.SECONDS)) {
            throw new LockAcquisitionException("Write lock timeout: " + resource);
        }
        try {
            return action.call();
        } finally {
            writeLock.release();
        }
    }
    
    // Use case: Config reads (many concurrent) vs config writes (exclusive)
}
```

---

## 12. Distributed Counter

```java
@Service
public class ZkDistributedCounter {
    private final CuratorFramework client;
    
    // ====== Shared counter (strongly consistent) ======
    public long increment(String counterName) throws Exception {
        SharedCount counter = new SharedCount(client, "/counters/" + counterName, 0);
        counter.start();
        
        try {
            while (true) {
                int current = counter.getCount();
                if (counter.trySetCount(counter.getVersionedValue(), current + 1)) {
                    return current + 1;
                }
                // CAS failed — retry
            }
        } finally {
            counter.close();
        }
    }
    
    // ====== DistributedAtomicLong (Curator built-in) ======
    public long atomicIncrement(String counterName) throws Exception {
        DistributedAtomicLong counter = new DistributedAtomicLong(
            client, "/counters/" + counterName, new ExponentialBackoffRetry(100, 3));
        
        AtomicValue<Long> result = counter.increment();
        if (result.succeeded()) {
            return result.postValue();
        }
        throw new RuntimeException("Atomic increment failed after retries");
    }
    
    public long getCount(String counterName) throws Exception {
        DistributedAtomicLong counter = new DistributedAtomicLong(
            client, "/counters/" + counterName, new ExponentialBackoffRetry(100, 3));
        
        AtomicValue<Long> result = counter.get();
        return result.succeeded() ? result.postValue() : 0;
    }
}
```

---

## 13. Ephemeral Nodes (Presence)

```java
@Service
public class PresenceService {
    private final CuratorFramework client;
    
    // ====== Register presence (auto-removed when service dies) ======
    public void registerPresence(String serviceId, String metadata) throws Exception {
        String path = "/presence/" + serviceId;
        
        // Ephemeral node: deleted when session expires (service crash/shutdown)
        client.create()
            .creatingParentsIfNeeded()
            .withMode(CreateMode.EPHEMERAL)
            .forPath(path, metadata.getBytes());
        
        log.info("Presence registered: {}", serviceId);
    }
    
    // ====== Check if service is alive ======
    public boolean isAlive(String serviceId) throws Exception {
        return client.checkExists().forPath("/presence/" + serviceId) != null;
    }
    
    // ====== Get all live services ======
    public List<String> getLiveServices() throws Exception {
        return client.getChildren().forPath("/presence");
    }
    
    // ====== Watch for services going up/down ======
    public void watchPresence(Consumer<String> onJoin, Consumer<String> onLeave) throws Exception {
        PathChildrenCache cache = new PathChildrenCache(client, "/presence", true);
        cache.getListenable().addListener((c, event) -> {
            String serviceId = event.getData().getPath().replace("/presence/", "");
            switch (event.getType()) {
                case CHILD_ADDED -> onJoin.accept(serviceId);
                case CHILD_REMOVED -> onLeave.accept(serviceId);
            }
        });
        cache.start();
    }
}
```

---

## 14-20: Additional Patterns (Key Code)

### 14. Distributed Semaphore
```java
public void executeWithSemaphore(String name, int maxLeases, Callable<Void> action) throws Exception {
    InterProcessSemaphoreV2 semaphore = new InterProcessSemaphoreV2(
        client, "/semaphores/" + name, maxLeases);
    
    Lease lease = semaphore.acquire(5, TimeUnit.SECONDS);
    if (lease == null) throw new RuntimeException("Semaphore full");
    
    try {
        action.call();
    } finally {
        semaphore.returnLease(lease);
    }
}
```

### 15. Sequence / ID Generator
```java
public long nextId(String sequenceName) throws Exception {
    String path = client.create()
        .creatingParentsIfNeeded()
        .withMode(CreateMode.PERSISTENT_SEQUENTIAL)
        .forPath("/sequences/" + sequenceName + "/id-");
    
    // Path: /sequences/orders/id-0000000042 → extract 42
    String seqStr = path.substring(path.lastIndexOf("-") + 1);
    return Long.parseLong(seqStr);
}
```

### 16. Group Membership
```java
public void joinGroup(String groupName, String memberId, byte[] metadata) throws Exception {
    GroupMember member = new GroupMember(client, "/groups/" + groupName, memberId, metadata);
    member.start();
    // Auto-removed from group when service crashes (ephemeral node)
}

public Map<String, byte[]> getGroupMembers(String groupName) throws Exception {
    GroupMember member = new GroupMember(client, "/groups/" + groupName, "observer", new byte[0]);
    member.start();
    return member.getCurrentMembers();
}
```

### 20. Connection Handling & Retry
```java
// Handle connection state changes
client.getConnectionStateListenable().addListener((c, newState) -> {
    switch (newState) {
        case CONNECTED -> log.info("Connected to ZK");
        case RECONNECTED -> {
            log.warn("Reconnected to ZK — re-registering ephemeral nodes");
            reRegisterEphemeralNodes();
        }
        case SUSPENDED -> log.warn("ZK connection SUSPENDED — pausing operations");
        case LOST -> {
            log.error("ZK session LOST — all ephemeral nodes gone!");
            handleSessionLoss();
        }
    }
});
```

---

## 🏆 Pattern Selection Cheat Sheet

| Use Case | ZooKeeper Pattern | Curator Class | Why |
|---|---|---|---|
| Exclusive access | Distributed lock | `InterProcessMutex` | Only one process at a time |
| Leader/follower | Leader election | `LeaderSelector` / `LeaderLatch` | Single leader coordinates |
| Find services | Service discovery | `ServiceDiscovery` | Dynamic registration + lookup |
| Shared config | Persistent nodes + watches | `NodeCache` | Real-time config propagation |
| Presence/health | Ephemeral nodes | `CreateMode.EPHEMERAL` | Auto-removed on crash |
| Read-heavy locking | Read-write lock | `InterProcessReadWriteLock` | Multiple readers allowed |
| Counter | Atomic counter | `DistributedAtomicLong` | CAS-based distributed increment |
| Rate limiting | Semaphore | `InterProcessSemaphoreV2` | Limit concurrent operations |
| Coordination | Barrier | `DistributedDoubleBarrier` | Sync N processes at a point |
| Unique IDs | Sequential nodes | `PERSISTENT_SEQUENTIAL` | Globally ordered sequences |

| ZooKeeper Best Practices |
|---|
| ✅ Use Curator (never raw ZK API) — handles edge cases |
| ✅ Keep data small (< 1MB per znode, ideally < 1KB) |
| ✅ Use ephemeral nodes for presence/locks |
| ✅ Handle SUSPENDED/LOST states properly |
| ✅ Set appropriate session timeout (10-30s) |
| ❌ Don't use ZK as a database (not designed for storage) |
| ❌ Don't create thousands of watches (memory overhead) |
| ❌ Don't store large payloads (use ZK for coordination only) |

---

*All examples use Apache Curator 5.x (the recommended ZooKeeper client for Java). Raw ZooKeeper API should never be used directly in production.*
