# 🎯 LLD Topic 19: Concurrency Patterns — Advanced

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering advanced concurrency patterns — Producer-Consumer, Read-Write Lock, Object Pool, Semaphore, CountDownLatch, CompletableFuture, and their applications in LLD problems.

---

## Core Concept

Beyond basic synchronization, these patterns solve specific concurrency problems that appear frequently in LLD interviews: rate limiting, connection pools, async processing, and parallel task coordination.

---

## Producer-Consumer with BlockingQueue

```java
class EventProcessor {
    private final BlockingQueue<Event> queue;
    private final List<Thread> consumers;
    private volatile boolean running = true;
    
    EventProcessor(int queueCapacity, int consumerCount) {
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.consumers = new ArrayList<>();
        
        for (int i = 0; i < consumerCount; i++) {
            Thread t = new Thread(this::consumeLoop, "consumer-" + i);
            t.start();
            consumers.add(t);
        }
    }
    
    // Producer
    void publish(Event event) {
        if (!queue.offer(event, 5, TimeUnit.SECONDS)) {
            throw new QueueFullException("Event queue is full");
        }
    }
    
    // Consumer
    private void consumeLoop() {
        while (running) {
            Event event = queue.poll(1, TimeUnit.SECONDS);
            if (event != null) processEvent(event);
        }
    }
}
```

---

## Object Pool Pattern

```java
class ConnectionPool {
    private final BlockingQueue<Connection> available;
    private final Set<Connection> inUse = ConcurrentHashMap.newKeySet();
    private final int maxSize;
    
    ConnectionPool(int maxSize) {
        this.maxSize = maxSize;
        this.available = new LinkedBlockingQueue<>(maxSize);
        for (int i = 0; i < maxSize; i++) {
            available.add(createConnection());
        }
    }
    
    Connection acquire(long timeout, TimeUnit unit) throws TimeoutException {
        Connection conn = available.poll(timeout, unit);
        if (conn == null) throw new TimeoutException("No connections available");
        inUse.add(conn);
        return conn;
    }
    
    void release(Connection conn) {
        inUse.remove(conn);
        available.offer(conn);
    }
    
    int getAvailableCount() { return available.size(); }
    int getInUseCount() { return inUse.size(); }
}
```

---

## Semaphore — Rate Limiting

```java
class RateLimiter {
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    
    RateLimiter(int maxRequestsPerSecond) {
        this.semaphore = new Semaphore(maxRequestsPerSecond);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Replenish permits every second
        scheduler.scheduleAtFixedRate(
            () -> semaphore.release(maxRequestsPerSecond - semaphore.availablePermits()),
            1, 1, TimeUnit.SECONDS
        );
    }
    
    boolean tryAcquire() {
        return semaphore.tryAcquire();
    }
}
```

---

## CountDownLatch — Wait for All

```java
class ParallelDataLoader {
    List<Data> loadAll(List<String> sources) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(sources.size());
        List<Data> results = Collections.synchronizedList(new ArrayList<>());
        
        for (String source : sources) {
            executor.submit(() -> {
                try {
                    Data data = loadFromSource(source);
                    results.add(data);
                } finally {
                    latch.countDown();  // Signal completion
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);  // Wait for ALL to complete
        return results;
    }
}
```

---

## CompletableFuture — Async Composition

```java
class OrderProcessingService {
    CompletableFuture<OrderResult> processOrderAsync(Order order) {
        // Parallel: validate inventory AND check fraud simultaneously
        CompletableFuture<Boolean> inventoryCheck = CompletableFuture.supplyAsync(
            () -> inventoryService.checkAvailability(order.getItems()));
        
        CompletableFuture<Boolean> fraudCheck = CompletableFuture.supplyAsync(
            () -> fraudService.isSafe(order));
        
        // Wait for both, then charge payment
        return inventoryCheck.thenCombine(fraudCheck, (inStock, safe) -> {
            if (!inStock) throw new OutOfStockException();
            if (!safe) throw new FraudDetectedException();
            return true;
        }).thenApplyAsync(ok -> {
            paymentService.charge(order);
            return new OrderResult(order.getId(), "SUCCESS");
        }).exceptionally(ex -> {
            return new OrderResult(order.getId(), "FAILED: " + ex.getMessage());
        });
    }
}
```

---

## Thread-Safe Singleton with Double-Checked Locking

```java
class ConfigManager {
    private static volatile ConfigManager instance;
    private final Map<String, String> config;
    
    private ConfigManager() {
        config = loadConfig();  // Expensive
    }
    
    static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }
}
```

---

## Patterns Applied to LLD Problems

| LLD Problem | Pattern | Why |
|---|---|---|
| **Connection Pool** | Object Pool + Semaphore | Bounded resource sharing |
| **Rate Limiter** | Token Bucket + Semaphore | Controlled rate of access |
| **Job Scheduler** | Producer-Consumer + PriorityQueue | Queued execution by priority |
| **Notification System** | CompletableFuture | Parallel channel dispatch |
| **Cache** | ReadWriteLock | Concurrent reads, exclusive writes |
| **Load Balancer** | AtomicInteger (round-robin) | Lock-free request distribution |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          ADVANCED CONCURRENCY PATTERNS                        │
├──────────────────┬───────────────────────────────────────────┤
│ Producer-Consumer│ BlockingQueue bridges threads safely       │
│ Object Pool      │ Reuse expensive objects (connections)      │
│ Semaphore        │ Limit concurrent access (rate limiting)    │
│ CountDownLatch   │ Wait for N tasks to complete               │
│ CompletableFuture│ Async composition, parallel + sequential   │
│ ReadWriteLock    │ Many readers OR one writer                 │
├──────────────────┴───────────────────────────────────────────┤
│ "I choose concurrency patterns based on the coordination      │
│  need: BlockingQueue for producer-consumer, Semaphore for     │
│  rate limiting, CompletableFuture for async composition."     │
└──────────────────────────────────────────────────────────────┘
```
