# 🔧 Topic 83: Production Java Patterns — Retry, Graceful Shutdown, Feature Flags & More

> Patterns that every production Java service needs but rarely get covered in system design resources. These are the operational patterns that keep services reliable at 3 AM. All Java, fully commented, production-ready.

---

## 📋 Table of Contents

- [1. Retry with Exponential Backoff + Jitter](#1-retry-with-exponential-backoff--jitter)
- [2. Graceful Shutdown & Connection Draining](#2-graceful-shutdown--connection-draining)
- [3. Feature Flags / Canary Rollout](#3-feature-flags--canary-rollout)
- [4. Scatter-Gather (Parallel Fan-Out + Merge)](#4-scatter-gather-parallel-fan-out--merge)
- [5. Dead Letter Queue Processing Pipeline](#5-dead-letter-queue-processing-pipeline)
- [6. Config Hot-Reload (No Restart)](#6-config-hot-reload-no-restart)
- [7. Priority Queue with Starvation Prevention](#7-priority-queue-with-starvation-prevention)
- [8. Throttle / Debounce (Batch Rapid Events)](#8-throttle--debounce-batch-rapid-events)
- [9. Health Check (Liveness + Readiness)](#9-health-check-liveness--readiness)
- [10. Audit Trail / Immutable Event Log](#10-audit-trail--immutable-event-log)
- [11. Timeout Cascade Prevention](#11-timeout-cascade-prevention)
- [12. Request Coalescing (Thunder Herd Prevention)](#12-request-coalescing-thunder-herd-prevention)
- [13. Poison Pill Detection & Quarantine](#13-poison-pill-detection--quarantine)
- [14. Graceful Degradation (Shed Load)](#14-graceful-degradation-shed-load)
- [15. Connection Pool Leak Detection](#15-connection-pool-leak-detection)
- [🏆 Production Readiness Checklist](#-production-readiness-checklist)

---

## 1. Retry with Exponential Backoff + Jitter

```java
/**
 * Retry: Handle transient failures (network blips, 503s, timeouts).
 * 
 * WHY exponential backoff? → Gives failing service time to recover.
 * WHY jitter? → Prevents "thundering herd" when all clients retry at same time.
 * 
 * Without jitter: 1000 clients all retry at exactly 1s, 2s, 4s → spikes
 * With jitter: 1000 clients retry at random(0-1s), random(0-2s), random(0-4s) → smooth
 */
@Service
public class RetryService {
    
    /**
     * Execute with retry. Handles:
     * - Exponential backoff (1s, 2s, 4s, 8s, 16s...)
     * - Full jitter (randomize entire delay)
     * - Max attempts cap
     * - Non-retryable exceptions (don't retry validation errors)
     * - Timeout per attempt
     * - Metrics on retry count
     */
    public <T> T executeWithRetry(String operationName, Callable<T> operation, 
                                    RetryConfig config) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            try {
                if (attempt > 0) {
                    long delay = calculateDelay(attempt, config);
                    log.info("[{}] Retry attempt {}/{} after {}ms", 
                        operationName, attempt, config.getMaxRetries(), delay);
                    Thread.sleep(delay);
                    Metrics.counter("retry.attempt", "operation", operationName, 
                        "attempt", String.valueOf(attempt)).increment();
                }
                
                // Execute with per-attempt timeout
                return executeWithTimeout(operation, config.getPerAttemptTimeout());
                
            } catch (Exception e) {
                lastException = e;
                
                // Don't retry non-transient errors
                if (isNonRetryable(e, config)) {
                    log.warn("[{}] Non-retryable error: {}", operationName, e.getMessage());
                    Metrics.counter("retry.non_retryable", "operation", operationName).increment();
                    throw e;
                }
                
                if (attempt == config.getMaxRetries()) {
                    log.error("[{}] All {} retries exhausted", operationName, config.getMaxRetries());
                    Metrics.counter("retry.exhausted", "operation", operationName).increment();
                }
            }
        }
        
        throw lastException;
    }
    
    /**
     * Calculate delay with full jitter.
     * Base delay doubles each attempt: 100ms, 200ms, 400ms, 800ms...
     * Jitter randomizes from 0 to calculated delay (prevents thundering herd).
     */
    private long calculateDelay(int attempt, RetryConfig config) {
        // Exponential: baseDelay * 2^attempt
        long exponentialDelay = config.getBaseDelayMs() * (1L << Math.min(attempt, 10));
        
        // Cap at max delay
        long cappedDelay = Math.min(exponentialDelay, config.getMaxDelayMs());
        
        // Full jitter: random between 0 and cappedDelay
        return ThreadLocalRandom.current().nextLong(0, cappedDelay + 1);
    }
    
    private boolean isNonRetryable(Exception e, RetryConfig config) {
        // Never retry these:
        return e instanceof IllegalArgumentException ||
               e instanceof NullPointerException ||
               e instanceof SecurityException ||
               config.getNonRetryableExceptions().stream()
                   .anyMatch(cls -> cls.isInstance(e));
    }
    
    private <T> T executeWithTimeout(Callable<T> operation, Duration timeout) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<T> future = executor.submit(operation);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RetryableException("Operation timed out after " + timeout);
        } finally {
            executor.shutdown();
        }
    }
}

// Configuration
public record RetryConfig(
    int maxRetries,           // e.g., 3
    long baseDelayMs,         // e.g., 100
    long maxDelayMs,          // e.g., 10000 (10s cap)
    Duration perAttemptTimeout,  // e.g., 5s
    Set<Class<? extends Exception>> nonRetryableExceptions
) {
    public static RetryConfig defaults() {
        return new RetryConfig(3, 100, 10_000, Duration.ofSeconds(5), 
            Set.of(IllegalArgumentException.class, ValidationException.class));
    }
}

// Spring @Retryable alternative (annotation-based)
@Service
public class AnnotatedRetryService {
    
    @Retryable(
        retryFor = {TransientException.class, TimeoutException.class},
        noRetryFor = {ValidationException.class},
        maxAttempts = 4,
        backoff = @Backoff(delay = 200, multiplier = 2, maxDelay = 5000, random = true)
    )
    public PaymentResult chargePayment(PaymentRequest request) {
        return paymentGateway.charge(request);
    }
    
    @Recover  // Called when all retries exhausted
    public PaymentResult recoverPayment(Exception e, PaymentRequest request) {
        log.error("Payment failed after all retries: {}", request.getId(), e);
        return PaymentResult.failed("Service unavailable, please try again later");
    }
}
```

---

## 2. Graceful Shutdown & Connection Draining

```java
/**
 * Graceful Shutdown: When deploying new version, finish in-flight requests
 * before killing the old instance. Without this: users get 502 errors during deploy.
 * 
 * Steps:
 * 1. Receive SIGTERM (K8s sends this 30s before SIGKILL)
 * 2. Stop accepting NEW requests (health check returns 503)
 * 3. Wait for in-flight requests to complete (with timeout)
 * 4. Close connections (DB, Redis, Kafka)
 * 5. Exit
 */
@Component
public class GracefulShutdownHandler {
    
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final CountDownLatch shutdownComplete = new CountDownLatch(1);
    private final Duration drainTimeout = Duration.ofSeconds(25);  // K8s gives 30s, use 25
    
    // Services to close
    private final List<AutoCloseable> resources = new CopyOnWriteArrayList<>();
    
    /**
     * Call at start of every request (e.g., in a servlet filter).
     * Returns false if shutting down (reject new requests with 503).
     */
    public boolean tryAcquireRequest() {
        if (shuttingDown.get()) {
            return false;  // Reject — we're shutting down
        }
        activeRequests.incrementAndGet();
        return true;
    }
    
    /**
     * Call at end of every request (in finally block of filter).
     */
    public void releaseRequest() {
        activeRequests.decrementAndGet();
    }
    
    /**
     * Register resources that need cleanup (DB pools, consumers, etc.)
     */
    public void registerResource(AutoCloseable resource) {
        resources.add(resource);
    }
    
    /**
     * Triggered by SIGTERM or Spring ApplicationContext closing.
     */
    @PreDestroy
    public void initiateShutdown() {
        log.info("🛑 Graceful shutdown initiated. Active requests: {}", activeRequests.get());
        shuttingDown.set(true);
        
        // Step 1: Wait for in-flight requests to drain
        long deadline = System.currentTimeMillis() + drainTimeout.toMillis();
        while (activeRequests.get() > 0 && System.currentTimeMillis() < deadline) {
            log.info("Waiting for {} active requests to complete...", activeRequests.get());
            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }
        
        if (activeRequests.get() > 0) {
            log.warn("⚠️ {} requests still active after drain timeout!", activeRequests.get());
            Metrics.counter("shutdown.forced_kill").increment();
        } else {
            log.info("✅ All requests drained successfully");
        }
        
        // Step 2: Close resources in reverse order (LIFO — like a stack)
        Collections.reverse(resources);
        for (AutoCloseable resource : resources) {
            try {
                log.info("Closing: {}", resource.getClass().getSimpleName());
                resource.close();
            } catch (Exception e) {
                log.warn("Failed to close {}: {}", resource.getClass().getSimpleName(), e.getMessage());
            }
        }
        
        // Step 3: Final flush of metrics/logs
        Metrics.counter("shutdown.completed").increment();
        log.info("🏁 Shutdown complete. Goodbye!");
        shutdownComplete.countDown();
    }
    
    public boolean isShuttingDown() { return shuttingDown.get(); }
    public int getActiveRequests() { return activeRequests.get(); }
}

// Servlet Filter that uses the shutdown handler
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GracefulShutdownFilter implements Filter {
    private final GracefulShutdownHandler shutdownHandler;
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) 
            throws IOException, ServletException {
        if (!shutdownHandler.tryAcquireRequest()) {
            ((HttpServletResponse) resp).setStatus(503);
            ((HttpServletResponse) resp).getWriter().write("Service shutting down");
            return;
        }
        
        try {
            chain.doFilter(req, resp);
        } finally {
            shutdownHandler.releaseRequest();
        }
    }
}

// Spring Boot configuration for Tomcat graceful shutdown
// application.yml:
// server.shutdown: graceful
// spring.lifecycle.timeout-per-shutdown-phase: 30s
```

---

## 3. Feature Flags / Canary Rollout

```java
/**
 * Feature Flags: Toggle features at runtime without deploy.
 * 
 * Use cases:
 * - Canary release: roll out to 5% of users → 25% → 50% → 100%
 * - Kill switch: instantly disable broken feature
 * - A/B testing: show different UI to different segments
 * - Beta access: enable for specific user list
 */
@Service
public class FeatureFlagService {
    
    // In production: use LaunchDarkly, Unleash, or AWS AppConfig
    // This is a self-contained implementation for understanding
    
    private final ConcurrentHashMap<String, FeatureFlag> flags = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;  // For distributed flag state
    
    /**
     * Check if feature is enabled for a specific user.
     * 
     * Evaluation order:
     * 1. Is feature globally disabled? → false
     * 2. Is user in explicit allow-list? → true
     * 3. Is user in explicit block-list? → false
     * 4. Is user within rollout percentage? → based on consistent hash
     */
    public boolean isEnabled(String featureName, String userId) {
        FeatureFlag flag = getFlag(featureName);
        if (flag == null) return false;
        
        // Kill switch — globally off
        if (!flag.isEnabled()) return false;
        
        // Explicit allow-list (beta testers, internal employees)
        if (flag.getAllowList().contains(userId)) return true;
        
        // Explicit block-list (problematic users)
        if (flag.getBlockList().contains(userId)) return false;
        
        // Percentage rollout — deterministic (same user always gets same result)
        if (flag.getRolloutPercentage() >= 100) return true;
        if (flag.getRolloutPercentage() <= 0) return false;
        
        // Consistent hashing: user always maps to same bucket
        int bucket = Math.abs((userId + ":" + featureName).hashCode() % 100);
        return bucket < flag.getRolloutPercentage();
    }
    
    /**
     * Check with context (for complex targeting rules).
     */
    public boolean isEnabled(String featureName, String userId, Map<String, String> context) {
        if (!isEnabled(featureName, userId)) return false;
        
        FeatureFlag flag = getFlag(featureName);
        
        // Segment targeting (e.g., only for region=US, tier=premium)
        for (var rule : flag.getTargetingRules()) {
            String contextValue = context.get(rule.getAttribute());
            if (contextValue != null && !rule.getValues().contains(contextValue)) {
                return false;  // User doesn't match targeting rule
            }
        }
        
        return true;
    }
    
    /**
     * Update flag (API for ops dashboard).
     */
    public void updateFlag(String featureName, FeatureFlagUpdate update) {
        FeatureFlag flag = flags.computeIfAbsent(featureName, k -> new FeatureFlag(featureName));
        
        if (update.getEnabled() != null) flag.setEnabled(update.getEnabled());
        if (update.getRolloutPercentage() != null) flag.setRolloutPercentage(update.getRolloutPercentage());
        if (update.getAllowList() != null) flag.setAllowList(update.getAllowList());
        
        // Persist to Redis (so all instances see the change immediately)
        redis.opsForValue().set("feature:" + featureName, mapper.writeValueAsString(flag));
        
        // Publish change event (other instances refresh their cache)
        redis.convertAndSend("feature-flag-updates", featureName);
        
        log.info("Feature flag updated: {} → {}% rollout, enabled={}", 
            featureName, flag.getRolloutPercentage(), flag.isEnabled());
    }
    
    /**
     * Gradual rollout helper: increase by N% every interval.
     */
    public void scheduleGradualRollout(String featureName, int startPercent, 
                                        int endPercent, int stepPercent, Duration interval) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger current = new AtomicInteger(startPercent);
        
        scheduler.scheduleAtFixedRate(() -> {
            int next = Math.min(current.addAndGet(stepPercent), endPercent);
            updateFlag(featureName, new FeatureFlagUpdate(null, next, null));
            log.info("Gradual rollout: {} now at {}%", featureName, next);
            
            if (next >= endPercent) {
                scheduler.shutdown();
                log.info("Gradual rollout complete: {} at 100%", featureName);
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    // Listen for flag updates from other instances
    @PostConstruct
    public void subscribeToUpdates() {
        redis.subscribe((message, pattern) -> {
            String flagName = new String(message.getBody());
            String json = redis.opsForValue().get("feature:" + flagName);
            if (json != null) {
                flags.put(flagName, mapper.readValue(json, FeatureFlag.class));
            }
        }, "feature-flag-updates");
    }
}

// Usage in code:
@Service
public class OrderService {
    private final FeatureFlagService featureFlags;
    
    public OrderResult createOrder(OrderRequest request, String userId) {
        // Feature flag: new payment flow
        if (featureFlags.isEnabled("new-payment-flow", userId)) {
            return newPaymentProcessor.process(request);
        } else {
            return legacyPaymentProcessor.process(request);
        }
    }
}
```

---

## 4. Scatter-Gather (Parallel Fan-Out + Merge)

```java
/**
 * Scatter-Gather: Call N services in parallel, merge results.
 * Used for: search across shards, aggregating recommendations, price comparison.
 * 
 * Key considerations:
 * - Timeout: don't wait forever for slow responders
 * - Partial results: return what we have even if some fail
 * - Weighting: some sources more important than others
 */
@Service
public class ScatterGatherService {
    private final ExecutorService scatterPool = Executors.newFixedThreadPool(20);
    
    /**
     * Fan out to multiple services, gather results with deadline.
     * Returns partial results if some services timeout/fail.
     */
    public <T> GatherResult<T> scatterGather(List<ScatterTask<T>> tasks, Duration deadline) {
        List<CompletableFuture<TaskResult<T>>> futures = tasks.stream()
            .map(task -> CompletableFuture.supplyAsync(() -> {
                try {
                    T result = task.getAction().call();
                    return new TaskResult<>(task.getName(), result, null);
                } catch (Exception e) {
                    return new TaskResult<>(task.getName(), null, e);
                }
            }, scatterPool).orTimeout(deadline.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> new TaskResult<>(task.getName(), null, ex)))
            .collect(Collectors.toList());
        
        // Wait for ALL with global deadline (some may have already timed out individually)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(deadline.toMillis() + 100, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Some tasks timed out — that's OK, we'll collect what's ready
        }
        
        // Gather results
        List<T> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        
        for (CompletableFuture<TaskResult<T>> future : futures) {
            TaskResult<T> result = future.getNow(null);
            if (result != null && result.getResult() != null) {
                successes.add(result.getResult());
            } else {
                failures.add(result != null ? result.getTaskName() : "unknown");
            }
        }
        
        Metrics.counter("scatter_gather.total_tasks").increment(tasks.size());
        Metrics.counter("scatter_gather.successes").increment(successes.size());
        Metrics.counter("scatter_gather.failures").increment(failures.size());
        
        return new GatherResult<>(successes, failures, successes.size() == tasks.size());
    }
    
    // Usage: Search across 5 shards
    public SearchResults distributedSearch(String query) {
        List<ScatterTask<List<Product>>> tasks = searchShards.stream()
            .map(shard -> new ScatterTask<>("shard-" + shard.getId(), 
                () -> shard.search(query, 20)))
            .collect(Collectors.toList());
        
        GatherResult<List<Product>> results = scatterGather(tasks, Duration.ofMillis(200));
        
        // Merge and rank all results
        List<Product> merged = results.getSuccesses().stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparingDouble(Product::getRelevanceScore).reversed())
            .limit(20)
            .collect(Collectors.toList());
        
        return new SearchResults(merged, results.isComplete());
    }
}
```

---

## 5. Dead Letter Queue Processing Pipeline

```java
/**
 * DLQ Pipeline: Structured handling of permanently failed messages.
 * 
 * Flow: Main Queue → Process → [Fail 3x] → DLQ → Alert → Manual Review → Retry/Discard
 */
@Service
public class DlqPipeline {
    
    /**
     * Process DLQ messages with structured triage.
     */
    @Scheduled(fixedDelay = 30_000)  // Every 30s
    public void processDlq() {
        List<DlqMessage> messages = dlqRepository.findUnresolved(50);
        
        for (DlqMessage msg : messages) {
            DlqAction action = triageMessage(msg);
            
            switch (action) {
                case AUTO_RETRY -> {
                    // Transient issue resolved — put back on main queue
                    mainQueue.send(msg.getOriginalPayload());
                    msg.setResolution("AUTO_RETRIED");
                    Metrics.counter("dlq.auto_retried").increment();
                }
                case DISCARD -> {
                    // Invalid data — will never succeed
                    msg.setResolution("DISCARDED: " + msg.getLastError());
                    Metrics.counter("dlq.discarded").increment();
                }
                case ESCALATE -> {
                    // Needs human review
                    alertService.sendAlert("DLQ message needs review: " + msg.getId());
                    msg.setResolution("ESCALATED");
                    Metrics.counter("dlq.escalated").increment();
                }
                case TRANSFORM_AND_RETRY -> {
                    // Fix the message and retry
                    String fixed = transformMessage(msg);
                    mainQueue.send(fixed);
                    msg.setResolution("TRANSFORMED_AND_RETRIED");
                }
            }
            
            dlqRepository.save(msg);
        }
    }
    
    private DlqAction triageMessage(DlqMessage msg) {
        String error = msg.getLastError();
        
        // Validation errors → discard (will never succeed)
        if (error.contains("ValidationException") || error.contains("MalformedJSON")) {
            return DlqAction.DISCARD;
        }
        
        // Timeout/connection errors that are now resolved → auto retry
        if (error.contains("TimeoutException") || error.contains("ConnectionRefused")) {
            if (isServiceHealthy(msg.getTargetService())) {
                return DlqAction.AUTO_RETRY;
            }
        }
        
        // Schema version mismatch → transform and retry
        if (error.contains("UnknownField") || error.contains("SchemaVersion")) {
            return DlqAction.TRANSFORM_AND_RETRY;
        }
        
        // Everything else → human review
        return DlqAction.ESCALATE;
    }
}
```

---

## 6. Config Hot-Reload (No Restart)

```java
/**
 * Hot-Reload: Change configuration without restarting the service.
 * Sources: file system, Redis, AWS AppConfig, ZooKeeper.
 */
@Service
public class HotReloadConfigService {
    private final ConcurrentHashMap<String, String> configCache = new ConcurrentHashMap<>();
    private final List<Consumer<String>> changeListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
    
    @PostConstruct
    public void startWatching() {
        // Poll config source every 10 seconds
        poller.scheduleAtFixedRate(this::refreshConfig, 0, 10, TimeUnit.SECONDS);
    }
    
    public String get(String key) {
        return configCache.get(key);
    }
    
    public String get(String key, String defaultValue) {
        return configCache.getOrDefault(key, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        String val = configCache.get(key);
        return val != null ? Integer.parseInt(val) : defaultValue;
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = configCache.get(key);
        return val != null ? Boolean.parseBoolean(val) : defaultValue;
    }
    
    public void onChange(Consumer<String> listener) {
        changeListeners.add(listener);
    }
    
    private void refreshConfig() {
        try {
            Map<String, String> newConfig = loadFromSource();
            
            // Detect changes
            for (var entry : newConfig.entrySet()) {
                String oldValue = configCache.put(entry.getKey(), entry.getValue());
                if (!entry.getValue().equals(oldValue)) {
                    log.info("Config changed: {} = {} (was: {})", 
                        entry.getKey(), entry.getValue(), oldValue);
                    changeListeners.forEach(l -> l.accept(entry.getKey()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to refresh config", e);
            // Keep existing values on failure — don't clear cache
        }
    }
    
    private Map<String, String> loadFromSource() {
        // Option 1: Redis
        Map<Object, Object> entries = redis.opsForHash().entries("app:config");
        return entries.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
    }
}

// Usage: Rate limit changes without restart
@Service
public class DynamicRateLimiter {
    private final HotReloadConfigService config;
    
    public boolean isAllowed(String userId) {
        // Rate limit reads from hot config — changes take effect in <10s
        int limit = config.getInt("rate_limit.per_user.per_minute", 100);
        return rateLimiter.isAllowed(userId, limit);
    }
}
```

---

## 7. Priority Queue with Starvation Prevention

```java
/**
 * Priority Queue that ensures low-priority items eventually get processed.
 * Without starvation prevention: if high-priority items keep arriving,
 * low-priority items wait forever.
 * 
 * Solution: "aging" — increase priority of waiting items over time.
 */
@Service
public class FairPriorityQueue<T> {
    
    private final PriorityBlockingQueue<PrioritizedItem<T>> queue;
    private final ScheduledExecutorService ager = Executors.newSingleThreadScheduledExecutor();
    private final Duration maxWait;  // After this, item gets max priority
    
    public FairPriorityQueue(Duration maxWait) {
        this.maxWait = maxWait;
        this.queue = new PriorityBlockingQueue<>(100, 
            Comparator.comparingInt(PrioritizedItem::getEffectivePriority));
        
        // Age items every 5 seconds (increase priority of waiting items)
        ager.scheduleAtFixedRate(this::ageItems, 5, 5, TimeUnit.SECONDS);
    }
    
    public void enqueue(T item, int priority) {
        queue.offer(new PrioritizedItem<>(item, priority, Instant.now()));
    }
    
    public T dequeue(Duration timeout) throws InterruptedException {
        PrioritizedItem<T> item = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return item != null ? item.getItem() : null;
    }
    
    /**
     * Aging: boost priority of items that have been waiting too long.
     * A low-priority item (priority=10) that waited 50% of maxWait 
     * gets boosted to effectively priority=5.
     */
    private void ageItems() {
        Instant now = Instant.now();
        // Note: We rebuild the queue to re-sort with updated effective priorities
        // In production, use a more efficient approach (e.g., separate queues per level)
        for (PrioritizedItem<T> item : queue) {
            Duration waited = Duration.between(item.getEnqueuedAt(), now);
            double waitRatio = (double) waited.toMillis() / maxWait.toMillis();
            int boost = (int) (item.getOriginalPriority() * Math.min(waitRatio, 1.0));
            item.setBoost(boost);
        }
    }
    
    static class PrioritizedItem<T> {
        private final T item;
        private final int originalPriority;  // Lower = higher priority
        private final Instant enqueuedAt;
        private volatile int boost = 0;
        
        int getEffectivePriority() {
            return Math.max(0, originalPriority - boost);  // Boosted = lower number = higher prio
        }
    }
}
```

---

## 8. Throttle / Debounce (Batch Rapid Events)

```java
/**
 * Throttle: Don't process every event — batch them.
 * 
 * Example: User types in search box → don't search on every keystroke.
 * Example: 100 notifications arrive in 1 second → send 1 batch notification.
 */
@Service
public class ThrottleService {
    
    private final ConcurrentHashMap<String, ThrottleState> throttles = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * Throttle: Execute at most once per interval.
     * First call executes immediately. Subsequent calls within interval are dropped.
     * After interval passes, next call executes.
     */
    public <T> Optional<T> throttle(String key, Duration interval, Supplier<T> action) {
        ThrottleState state = throttles.computeIfAbsent(key, k -> new ThrottleState());
        long now = System.currentTimeMillis();
        
        if (now - state.lastExecuted.get() >= interval.toMillis()) {
            if (state.lastExecuted.compareAndSet(state.lastExecuted.get(), now)) {
                return Optional.of(action.get());
            }
        }
        return Optional.empty();  // Throttled — skipped
    }
    
    /**
     * Debounce: Wait for inactivity, then execute once.
     * Resets timer on each new event. Only fires after quiet period.
     * 
     * Use case: Notification batching — wait 5s of no new notifications, then send batch.
     */
    public void debounce(String key, Duration quietPeriod, Runnable action) {
        ThrottleState state = throttles.computeIfAbsent(key, k -> new ThrottleState());
        
        // Cancel previous scheduled execution
        ScheduledFuture<?> previous = state.scheduledFuture.getAndSet(null);
        if (previous != null) previous.cancel(false);
        
        // Schedule new execution after quiet period
        ScheduledFuture<?> future = scheduler.schedule(action, 
            quietPeriod.toMillis(), TimeUnit.MILLISECONDS);
        state.scheduledFuture.set(future);
    }
    
    /**
     * Batch collector: Collect items, flush after size OR time threshold.
     */
    public <T> void collectAndFlush(String batchKey, T item, int maxBatchSize, 
                                     Duration maxWait, Consumer<List<T>> flusher) {
        BatchState<T> batch = (BatchState<T>) throttles.computeIfAbsent(batchKey, 
            k -> new BatchState<>(maxBatchSize, maxWait, flusher, scheduler));
        
        batch.add(item);
    }
}
```

---

## 9. Health Check (Liveness + Readiness)

```java
/**
 * Health checks for Kubernetes / load balancer probes.
 * 
 * Liveness: "Is the process alive?" → restart if false
 * Readiness: "Can it serve traffic?" → remove from LB if false
 */
@RestController
@RequestMapping("/health")
public class HealthCheckController {
    
    private final List<HealthIndicator> indicators;
    private final GracefulShutdownHandler shutdownHandler;
    
    /**
     * Liveness: Returns 200 if JVM is running and not deadlocked.
     * K8s restarts pod if this fails.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        // Check for deadlocks
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = threadBean.findDeadlockedThreads();
        
        if (deadlocked != null) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "DEADLOCKED",
                "deadlockedThreads", deadlocked.length
            ));
        }
        
        // Check memory (OOM imminent?)
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = 1.0 - ((double) runtime.freeMemory() / runtime.maxMemory());
        if (memoryUsage > 0.95) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "MEMORY_CRITICAL",
                "usage", String.format("%.1f%%", memoryUsage * 100)
            ));
        }
        
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
    
    /**
     * Readiness: Returns 200 if service can handle requests.
     * K8s removes from service endpoints if this fails.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        // Don't accept traffic during shutdown
        if (shutdownHandler.isShuttingDown()) {
            return ResponseEntity.status(503).body(Map.of("status", "SHUTTING_DOWN"));
        }
        
        // Check all dependencies
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean allHealthy = true;
        
        for (HealthIndicator indicator : indicators) {
            HealthResult result = indicator.check();
            checks.put(indicator.getName(), result);
            if (!result.isHealthy()) allHealthy = false;
        }
        
        int status = allHealthy ? 200 : 503;
        return ResponseEntity.status(status).body(Map.of(
            "status", allHealthy ? "UP" : "DOWN",
            "checks", checks
        ));
    }
}

// Health indicators for dependencies
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    public String getName() { return "database"; }
    
    public HealthResult check() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return HealthResult.healthy();
        } catch (Exception e) {
            return HealthResult.unhealthy("DB unreachable: " + e.getMessage());
        }
    }
}

@Component
public class RedisHealthIndicator implements HealthIndicator {
    public String getName() { return "redis"; }
    
    public HealthResult check() {
        try {
            redis.opsForValue().get("health:ping");
            return HealthResult.healthy();
        } catch (Exception e) {
            return HealthResult.unhealthy("Redis unreachable: " + e.getMessage());
        }
    }
}
```

---

## 10-15: Additional Patterns (Key Code)

### 10. Audit Trail
```java
@Aspect
@Component
public class AuditAspect {
    @Around("@annotation(Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String user = SecurityContext.getCurrentUser();
        String action = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        Object result = joinPoint.proceed();
        
        auditRepository.save(new AuditEntry(
            UUID.randomUUID().toString(), user, action,
            mapper.writeValueAsString(args), mapper.writeValueAsString(result),
            Instant.now(), getClientIp()
        ));
        
        return result;
    }
}
```

### 12. Request Coalescing (Thunder Herd Prevention)
```java
// Multiple threads requesting same data → only ONE actually loads
public class RequestCoalescer<K, V> {
    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
    
    public V getOrLoad(K key, Function<K, V> loader) throws Exception {
        CompletableFuture<V> future = inFlight.computeIfAbsent(key, k -> {
            return CompletableFuture.supplyAsync(() -> loader.apply(k));
        });
        
        try {
            return future.get(5, TimeUnit.SECONDS);
        } finally {
            inFlight.remove(key, future);  // Remove only if same future
        }
    }
}
// 100 threads request user:123 → only 1 DB query, 99 wait for result
```

### 14. Graceful Degradation (Shed Load)
```java
@Service
public class LoadShedder {
    private final AtomicInteger currentLoad = new AtomicInteger(0);
    private final int maxLoad;
    
    public <T> T executeOrShed(int priority, Supplier<T> action, Supplier<T> degraded) {
        int load = currentLoad.incrementAndGet();
        try {
            if (load > maxLoad) {
                // System overloaded — shed low-priority requests
                if (priority > 5) {  // Low priority threshold
                    Metrics.counter("load_shed.rejected").increment();
                    return degraded.get();  // Return cached/simplified response
                }
            }
            return action.get();
        } finally {
            currentLoad.decrementAndGet();
        }
    }
}
```

---

## 🏆 Production Readiness Checklist

| Category | Pattern | Status Check |
|---|---|---|
| **Resilience** | Retry + backoff + jitter | ✅ Transient failures auto-recover |
| **Resilience** | Circuit breaker | ✅ Cascading failures prevented |
| **Resilience** | Graceful degradation | ✅ Returns cached data under load |
| **Deployment** | Graceful shutdown | ✅ Zero 502s during deploy |
| **Deployment** | Health checks (live + ready) | ✅ K8s/LB knows when to route |
| **Deployment** | Feature flags | ✅ Kill switch + canary rollout |
| **Operations** | Config hot-reload | ✅ Tune without restart |
| **Operations** | Structured logging | ✅ Searchable in Log Insights |
| **Operations** | Metrics + alerts | ✅ Know before users complain |
| **Data** | Dead letter queue | ✅ No message silently lost |
| **Data** | Audit trail | ✅ Compliance + debugging |
| **Data** | Idempotency | ✅ Safe retries everywhere |
| **Performance** | Request coalescing | ✅ No thunder herd on cache miss |
| **Performance** | Throttle/debounce | ✅ Batch rapid events |
| **Performance** | Connection pool monitoring | ✅ Detect leaks early |

---

*These patterns complement the system design patterns in Topic 82. Topic 82 = distributed system patterns, Topic 83 = operational production patterns that keep services reliable.*
