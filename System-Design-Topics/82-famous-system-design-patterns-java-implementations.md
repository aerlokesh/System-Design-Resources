# 🏗️ Topic 82: Famous System Design Patterns — Java Implementations

> Every commonly-referenced system design pattern implemented in production-ready Java. These are the patterns that appear repeatedly across system design interviews and real systems at Netflix, Uber, Amazon, Twitter, etc. Each implementation is self-contained and copy-pasteable.

---

## 📋 Table of Contents

- [1. Virtual Queue (Flash Sale / Waiting Room)](#1-virtual-queue-flash-sale--waiting-room)
- [2. Distributed Counter at Scale](#2-distributed-counter-at-scale)
- [3. Top-K / Trending Computation](#3-top-k--trending-computation)
- [4. Celebrity Fan-Out (Hybrid Push/Pull)](#4-celebrity-fan-out-hybrid-pushpull)
- [5. Token Bucket Rate Limiter](#5-token-bucket-rate-limiter)
- [6. Sliding Window Rate Limiter](#6-sliding-window-rate-limiter)
- [7. Circuit Breaker](#7-circuit-breaker)
- [8. Saga Pattern (Distributed Transaction)](#8-saga-pattern-distributed-transaction)
- [9. Outbox Pattern (Reliable Event Publishing)](#9-outbox-pattern-reliable-event-publishing)
- [10. CQRS (Command Query Responsibility Segregation)](#10-cqrs-command-query-responsibility-segregation)
- [11. Event Sourcing](#11-event-sourcing)
- [12. Idempotency Handler](#12-idempotency-handler)
- [13. Bloom Filter (Membership Check)](#13-bloom-filter-membership-check)
- [14. Consistent Hashing (Ring)](#14-consistent-hashing-ring)
- [15. Leader Election](#15-leader-election)
- [16. Write-Ahead Log (WAL)](#16-write-ahead-log-wal)
- [17. Backpressure Handler](#17-backpressure-handler)
- [18. Bulkhead Isolation](#18-bulkhead-isolation)
- [19. Lease-Based Lock (Distributed)](#19-lease-based-lock-distributed)
- [20. Deduplication Service](#20-deduplication-service)
- [21. Snowflake ID Generator](#21-snowflake-id-generator)
- [22. Read-Your-Writes Consistency](#22-read-your-writes-consistency)
- [23. Hot Key / Hot Partition Handler](#23-hot-key--hot-partition-handler)
- [24. Fan-Out on Write (Timeline)](#24-fan-out-on-write-timeline)
- [25. Change Data Capture (CDC) Consumer](#25-change-data-capture-cdc-consumer)
- [🏆 Pattern Decision Matrix](#-pattern-decision-matrix)

---

## 1. Virtual Queue (Flash Sale / Waiting Room)

```java
/**
 * Virtual Queue: When demand exceeds capacity (flash sales, ticket drops),
 * users enter a queue and are admitted in batches at a controlled rate.
 * Prevents system overload while maintaining fairness (FIFO).
 */
public class VirtualQueueService {
    private final ConcurrentLinkedQueue<QueueEntry> waitingQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, QueuePosition> userPositions = new ConcurrentHashMap<>();
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalAdmitted = new AtomicLong(0);
    private final Semaphore admissionSlots;  // Max concurrent users in the system
    private final ScheduledExecutorService admitter = Executors.newSingleThreadScheduledExecutor();
    
    public VirtualQueueService(int maxConcurrentUsers, int admitRatePerSecond) {
        this.admissionSlots = new Semaphore(maxConcurrentUsers);
        
        // Admit users from queue at controlled rate
        admitter.scheduleAtFixedRate(() -> admitBatch(admitRatePerSecond), 
            0, 1, TimeUnit.SECONDS);
    }
    
    // User requests to join the sale
    public QueueTicket enqueue(String userId) {
        // Prevent duplicate entries
        if (userPositions.containsKey(userId)) {
            return getExistingTicket(userId);
        }
        
        long position = totalEnqueued.incrementAndGet();
        QueueEntry entry = new QueueEntry(userId, position, Instant.now());
        waitingQueue.offer(entry);
        userPositions.put(userId, new QueuePosition(position, QueueStatus.WAITING));
        
        long estimatedWaitSec = (position - totalAdmitted.get()) / admitRatePerSecond;
        
        return new QueueTicket(userId, position, estimatedWaitSec, QueueStatus.WAITING);
    }
    
    // Check position (polled by client every few seconds)
    public QueueTicket checkPosition(String userId) {
        QueuePosition pos = userPositions.get(userId);
        if (pos == null) return QueueTicket.notFound();
        
        if (pos.getStatus() == QueueStatus.ADMITTED) {
            return new QueueTicket(userId, 0, 0, QueueStatus.ADMITTED);
        }
        
        long aheadOfYou = pos.getPosition() - totalAdmitted.get();
        long estimatedWaitSec = Math.max(0, aheadOfYou / admitRatePerSecond);
        return new QueueTicket(userId, aheadOfYou, estimatedWaitSec, QueueStatus.WAITING);
    }
    
    // Admit batch from queue
    private void admitBatch(int batchSize) {
        for (int i = 0; i < batchSize; i++) {
            if (!admissionSlots.tryAcquire()) break;  // System at capacity
            
            QueueEntry entry = waitingQueue.poll();
            if (entry == null) {
                admissionSlots.release();  // No one waiting
                break;
            }
            
            userPositions.put(entry.getUserId(), new QueuePosition(0, QueueStatus.ADMITTED));
            totalAdmitted.incrementAndGet();
            
            // Notify user via WebSocket/SSE that they're admitted
            notificationService.notifyAdmitted(entry.getUserId());
        }
    }
    
    // User finishes (purchased/left) — release slot
    public void releaseSlot(String userId) {
        userPositions.remove(userId);
        admissionSlots.release();
    }
}
```

---

## 2. Distributed Counter at Scale

```java
/**
 * Distributed Counter: Handles millions of increments/sec (view counts, likes).
 * Uses sharding locally + batched writes to persistent store.
 * Trades slight staleness for extreme throughput.
 */
public class DistributedCounterService {
    // Local sharded counters (LongAdder = cell-per-thread, zero contention)
    private final ConcurrentHashMap<String, LongAdder> localCounters = new ConcurrentHashMap<>();
    
    // Pending increments to flush to DB
    private final ConcurrentHashMap<String, LongAdder> pendingFlush = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService flusher = Executors.newScheduledThreadPool(2);
    private final StringRedisTemplate redis;
    
    @PostConstruct
    public void init() {
        flusher.scheduleAtFixedRate(this::flushToRedis, 5, 5, TimeUnit.SECONDS);
    }
    
    // Increment (called millions of times/sec — lock-free)
    public void increment(String key) {
        localCounters.computeIfAbsent(key, k -> new LongAdder()).increment();
        pendingFlush.computeIfAbsent(key, k -> new LongAdder()).increment();
    }
    
    // Get approximate count (local + redis)
    public long getCount(String key) {
        Long redisCount = redis.opsForValue().increment(key, 0);  // Read without increment
        LongAdder local = localCounters.get(key);
        long localCount = (local != null) ? local.sum() : 0;
        return (redisCount != null ? redisCount : 0) + localCount;
    }
    
    // Flush accumulated counts to Redis (batched)
    private void flushToRedis() {
        pendingFlush.forEach((key, adder) -> {
            long count = adder.sumThenReset();
            if (count > 0) {
                redis.opsForValue().increment("counter:" + key, count);
            }
        });
    }
    
    // Get exact count (from Redis only — after flush)
    public long getExactCount(String key) {
        String val = redis.opsForValue().get("counter:" + key);
        return val != null ? Long.parseLong(val) : 0;
    }
}
```

---

## 3. Top-K / Trending Computation

```java
/**
 * Top-K: Find the K most popular items in a sliding time window.
 * Uses time-bucketed counters + periodic computation.
 * Appears in: Trending hashtags, trending products, popular videos.
 */
public class TopKTrendingService {
    // Per-item counts in time buckets (1-minute granularity)
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, LongAdder>> itemBuckets = 
        new ConcurrentHashMap<>();
    
    // Cached result (computed every N seconds)
    private volatile List<TrendingItem> cachedTopK = List.of();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final int k;
    private final Duration windowSize;
    
    public TopKTrendingService(int k, Duration windowSize) {
        this.k = k;
        this.windowSize = windowSize;
        scheduler.scheduleAtFixedRate(this::recompute, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanup, 60, 60, TimeUnit.SECONDS);
    }
    
    // Record event (called on every view/click/like)
    public void record(String itemId) {
        long bucket = System.currentTimeMillis() / 60_000;  // 1-min buckets
        itemBuckets.computeIfAbsent(itemId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(bucket, k -> new LongAdder())
            .increment();
    }
    
    // Get current top-K (from cache — O(1))
    public List<TrendingItem> getTopK() {
        return cachedTopK;
    }
    
    // Recompute top-K (runs in background)
    private void recompute() {
        long now = System.currentTimeMillis() / 60_000;
        long windowStart = now - (windowSize.toMinutes());
        
        // Count total per item within window
        Map<String, Long> scores = new HashMap<>();
        itemBuckets.forEach((itemId, buckets) -> {
            long total = buckets.entrySet().stream()
                .filter(e -> e.getKey() >= windowStart)
                .mapToLong(e -> e.getValue().sum())
                .sum();
            if (total > 0) scores.put(itemId, total);
        });
        
        // Find top-K using min-heap
        PriorityQueue<Map.Entry<String, Long>> minHeap = 
            new PriorityQueue<>(Comparator.comparingLong(Map.Entry::getValue));
        
        for (Map.Entry<String, Long> entry : scores.entrySet()) {
            minHeap.offer(entry);
            if (minHeap.size() > k) minHeap.poll();
        }
        
        // Build sorted result (highest first)
        List<TrendingItem> result = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            Map.Entry<String, Long> entry = minHeap.poll();
            result.add(new TrendingItem(entry.getKey(), entry.getValue()));
        }
        Collections.reverse(result);
        
        cachedTopK = result;
    }
    
    private void cleanup() {
        long cutoff = System.currentTimeMillis() / 60_000 - windowSize.toMinutes() * 2;
        itemBuckets.forEach((itemId, buckets) -> {
            buckets.keySet().removeIf(bucket -> bucket < cutoff);
            if (buckets.isEmpty()) itemBuckets.remove(itemId);
        });
    }
}
```

---

## 4. Celebrity Fan-Out (Hybrid Push/Pull)

```java
/**
 * Celebrity Fan-Out: Normal users use push (fan-out on write), celebrities use pull.
 * Solves the "Lady Gaga problem" — pushing to 50M followers is too slow.
 * When reading timeline: merge pushed items + pull celebrity items.
 */
public class HybridFanOutService {
    private static final int CELEBRITY_THRESHOLD = 100_000;  // >100K followers = celebrity
    private final ExecutorService fanOutPool = Executors.newFixedThreadPool(32);
    
    // Push: write tweet to each follower's timeline cache
    public CompletableFuture<Void> fanOutOnWrite(Tweet tweet) {
        String authorId = tweet.getAuthorId();
        int followerCount = followService.getFollowerCount(authorId);
        
        if (followerCount > CELEBRITY_THRESHOLD) {
            // CELEBRITY: Don't fan out — store in author's tweet list only
            // Followers will PULL when reading their timeline
            celebrityTweetStore.addTweet(authorId, tweet);
            return CompletableFuture.completedFuture(null);
        }
        
        // NORMAL USER: Fan out to all followers' timelines (push)
        List<String> followers = followService.getFollowerIds(authorId);
        List<List<String>> batches = Lists.partition(followers, 1000);
        
        List<CompletableFuture<Void>> futures = batches.stream()
            .map(batch -> CompletableFuture.runAsync(() -> {
                for (String followerId : batch) {
                    timelineCache.pushToTimeline(followerId, tweet.getId(), tweet.getTimestamp());
                }
            }, fanOutPool))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    // Read timeline: merge pushed + pulled
    public List<Tweet> getTimeline(String userId, int limit) {
        // Step 1: Get pre-computed timeline (pushed tweets from normal users)
        List<String> pushedTweetIds = timelineCache.getTimeline(userId, limit);
        
        // Step 2: Get tweets from celebrities this user follows (pull on read)
        List<String> followedCelebrities = followService.getFollowedCelebrities(userId);
        List<Tweet> celebrityTweets = followedCelebrities.parallelStream()
            .flatMap(celeb -> celebrityTweetStore.getRecentTweets(celeb, 10).stream())
            .collect(Collectors.toList());
        
        // Step 3: Merge + sort by timestamp (most recent first)
        List<Tweet> pushedTweets = tweetStore.multiGet(pushedTweetIds);
        List<Tweet> merged = new ArrayList<>(pushedTweets);
        merged.addAll(celebrityTweets);
        merged.sort(Comparator.comparing(Tweet::getTimestamp).reversed());
        
        return merged.subList(0, Math.min(limit, merged.size()));
    }
}
```

---

## 5. Token Bucket Rate Limiter

```java
/**
 * Token Bucket: Allows bursts up to bucket capacity, refills at steady rate.
 * Used by: API gateways, per-user rate limiting, payment throttling.
 */
public class TokenBucketRateLimiter {
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long maxTokens;
    private final long refillRatePerSecond;
    
    public TokenBucketRateLimiter(long maxTokens, long refillRatePerSecond) {
        this.maxTokens = maxTokens;
        this.refillRatePerSecond = refillRatePerSecond;
    }
    
    public boolean tryConsume(String key, int tokens) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(maxTokens));
        return bucket.tryConsume(tokens);
    }
    
    private class Bucket {
        private final AtomicLong availableTokens;
        private final AtomicLong lastRefillNanos;
        
        Bucket(long initial) {
            this.availableTokens = new AtomicLong(initial);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }
        
        boolean tryConsume(int tokens) {
            refill();
            long current;
            do {
                current = availableTokens.get();
                if (current < tokens) return false;
            } while (!availableTokens.compareAndSet(current, current - tokens));
            return true;
        }
        
        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillNanos.get();
            long elapsedNanos = now - last;
            long tokensToAdd = (elapsedNanos * refillRatePerSecond) / 1_000_000_000L;
            
            if (tokensToAdd > 0 && lastRefillNanos.compareAndSet(last, now)) {
                long current = availableTokens.get();
                availableTokens.set(Math.min(maxTokens, current + tokensToAdd));
            }
        }
    }
}
```

---

## 6. Sliding Window Rate Limiter

```java
/**
 * Sliding Window: More precise than fixed window — no boundary burst problem.
 * Tracks exact request timestamps within the window.
 */
public class SlidingWindowRateLimiter {
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> requestLogs = 
        new ConcurrentHashMap<>();
    private final int maxRequests;
    private final Duration windowSize;
    
    public SlidingWindowRateLimiter(int maxRequests, Duration windowSize) {
        this.maxRequests = maxRequests;
        this.windowSize = windowSize;
    }
    
    public boolean isAllowed(String key) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSize.toMillis();
        
        ConcurrentLinkedDeque<Long> log = requestLogs.computeIfAbsent(key, 
            k -> new ConcurrentLinkedDeque<>());
        
        // Remove expired entries
        while (!log.isEmpty() && log.peekFirst() < windowStart) {
            log.pollFirst();
        }
        
        if (log.size() < maxRequests) {
            log.addLast(now);
            return true;
        }
        return false;  // Rate limited
    }
}
```

---

## 7. Circuit Breaker

```java
/**
 * Circuit Breaker: Prevent cascading failures when downstream is unhealthy.
 * States: CLOSED (normal) → OPEN (reject all) → HALF_OPEN (test requests).
 */
public class CircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    private final int failureThreshold;
    private final long cooldownMs;
    
    public CircuitBreaker(int failureThreshold, Duration cooldown) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldown.toMillis();
    }
    
    public <T> T execute(Supplier<T> action, Supplier<T> fallback) {
        if (!isRequestAllowed()) return fallback.get();
        
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            return fallback.get();
        }
    }
    
    private boolean isRequestAllowed() {
        State current = state.get();
        if (current == State.CLOSED) return true;
        if (current == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime >= cooldownMs) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                return true;
            }
            return false;
        }
        return successCount.get() < 3;  // HALF_OPEN: allow 3 test requests
    }
    
    private void onSuccess() {
        if (state.get() == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= 3) {
                state.set(State.CLOSED);
                failureCount.set(0);
            }
        } else {
            failureCount.set(0);
        }
    }
    
    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        if (failureCount.incrementAndGet() >= failureThreshold) {
            state.set(State.OPEN);
        }
    }
}
```

---

## 8. Saga Pattern (Distributed Transaction)

```java
/**
 * Saga: Execute distributed transaction as a sequence of local transactions.
 * Each step has a compensation (rollback) action.
 * If step N fails, compensate steps N-1, N-2, ..., 1.
 */
public class SagaOrchestrator {
    
    public OrderResult createOrderSaga(OrderRequest request) {
        Saga saga = Saga.builder()
            .step("reserve-inventory", 
                () -> inventoryService.reserve(request.getItems()),
                () -> inventoryService.release(request.getItems()))
            .step("charge-payment",
                () -> paymentService.charge(request.getUserId(), request.getTotal()),
                () -> paymentService.refund(request.getUserId(), request.getTotal()))
            .step("create-shipment",
                () -> shippingService.createLabel(request),
                () -> shippingService.cancelLabel(request.getOrderId()))
            .step("send-confirmation",
                () -> notificationService.sendOrderConfirmation(request),
                () -> {})  // No compensation needed for notifications
            .build();
        
        return saga.execute();
    }
    
    public static class Saga {
        private final List<SagaStep> steps;
        
        public Object execute() {
            List<SagaStep> completedSteps = new ArrayList<>();
            
            for (SagaStep step : steps) {
                try {
                    step.getAction().run();
                    completedSteps.add(step);
                    log.info("Saga step completed: {}", step.getName());
                } catch (Exception e) {
                    log.error("Saga step failed: {}. Compensating...", step.getName(), e);
                    compensate(completedSteps);
                    throw new SagaFailedException("Saga failed at step: " + step.getName(), e);
                }
            }
            
            return OrderResult.success();
        }
        
        private void compensate(List<SagaStep> completedSteps) {
            // Compensate in reverse order
            for (int i = completedSteps.size() - 1; i >= 0; i--) {
                try {
                    completedSteps.get(i).getCompensation().run();
                    log.info("Compensation completed: {}", completedSteps.get(i).getName());
                } catch (Exception e) {
                    log.error("Compensation FAILED: {}. Manual intervention required!", 
                        completedSteps.get(i).getName(), e);
                    alertService.sendCritical("Saga compensation failed", e);
                }
            }
        }
    }
}
```

---

## 9. Outbox Pattern (Reliable Event Publishing)

```java
/**
 * Outbox: Guarantee that DB write + event publish happen atomically.
 * Write event to outbox table in same DB transaction as business data.
 * Background poller publishes events to Kafka/SQS.
 */
@Service
@Transactional
public class OutboxPatternService {
    
    // Business operation + outbox write in SAME transaction
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(request);
        orderRepository.save(order);
        
        // Outbox entry — same transaction guarantees consistency
        OutboxEvent outboxEvent = new OutboxEvent(
            UUID.randomUUID().toString(),
            "OrderCreated",
            order.getId(),
            mapper.writeValueAsString(new OrderCreatedEvent(order)),
            Instant.now()
        );
        outboxRepository.save(outboxEvent);
        
        return order;
        // Transaction commits: both order AND outbox event are persisted atomically
    }
}

// Background poller (separate thread)
@Service
public class OutboxPublisher {
    @Scheduled(fixedDelay = 100)  // Every 100ms
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findUnpublished(100);
        
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send("events", event.getAggregateId(), event.getPayload()).get();
                event.markPublished();
                outboxRepository.save(event);
            } catch (Exception e) {
                log.warn("Failed to publish outbox event: {}", event.getId());
                break;  // Retry on next cycle
            }
        }
    }
}
```

---

## 10. CQRS (Command Query Responsibility Segregation)

```java
/**
 * CQRS: Separate write model (commands) from read model (queries).
 * Write to normalized DB, project to denormalized read store (ES, Redis, etc.).
 */
@Service
public class CqrsOrderService {
    
    // ====== COMMAND SIDE (writes) ======
    @Transactional
    public String createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd);
        orderRepository.save(order);  // Write to PostgreSQL (normalized)
        
        // Publish event for read model update
        eventPublisher.publish(new OrderCreatedEvent(order));
        return order.getId();
    }
    
    // ====== QUERY SIDE (reads — from denormalized store) ======
    public OrderView getOrder(String orderId) {
        // Read from Elasticsearch (denormalized, fast)
        return orderViewRepository.findById(orderId);
    }
    
    public List<OrderView> getUserOrders(String userId, int page, int size) {
        return orderViewRepository.findByUserId(userId, PageRequest.of(page, size));
    }
}

// Event handler updates read model
@Service
public class OrderProjection {
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        OrderView view = new OrderView(
            event.getOrderId(),
            event.getUserId(),
            event.getItems(),
            event.getTotal(),
            event.getStatus(),
            event.getCreatedAt()
        );
        // Write to Elasticsearch (read-optimized, denormalized)
        elasticsearchRepository.save(view);
    }
    
    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderView view = elasticsearchRepository.findById(event.getOrderId());
        view.setStatus(event.getNewStatus());
        view.setUpdatedAt(event.getTimestamp());
        elasticsearchRepository.save(view);
    }
}
```

---

## 11. Event Sourcing

```java
/**
 * Event Sourcing: Store all state changes as immutable events.
 * Current state = replay all events. Enables audit trail, time travel, replay.
 */
public class EventSourcedAccount {
    private final String accountId;
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();
    private BigDecimal balance = BigDecimal.ZERO;
    private int version = 0;
    
    // Rebuild state from events
    public static EventSourcedAccount fromEvents(String accountId, List<DomainEvent> history) {
        EventSourcedAccount account = new EventSourcedAccount(accountId);
        for (DomainEvent event : history) {
            account.apply(event);
            account.version++;
        }
        return account;
    }
    
    // Command: deposit money
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new InvalidAmountException();
        raiseEvent(new MoneyDeposited(accountId, amount, Instant.now()));
    }
    
    // Command: withdraw money
    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(balance) > 0) throw new InsufficientFundsException();
        raiseEvent(new MoneyWithdrawn(accountId, amount, Instant.now()));
    }
    
    private void raiseEvent(DomainEvent event) {
        apply(event);
        uncommittedEvents.add(event);
    }
    
    private void apply(DomainEvent event) {
        if (event instanceof MoneyDeposited e) {
            balance = balance.add(e.getAmount());
        } else if (event instanceof MoneyWithdrawn e) {
            balance = balance.subtract(e.getAmount());
        }
    }
    
    public List<DomainEvent> getUncommittedEvents() { return uncommittedEvents; }
    public void markCommitted() { uncommittedEvents.clear(); }
}

// Event Store
@Service
public class EventStoreService {
    public void save(String streamId, List<DomainEvent> events, int expectedVersion) {
        // Optimistic concurrency: reject if version mismatch
        int currentVersion = getStreamVersion(streamId);
        if (currentVersion != expectedVersion) throw new ConcurrencyException();
        
        for (DomainEvent event : events) {
            eventRepository.append(streamId, event, ++currentVersion);
        }
        // Publish to Kafka for projections
        events.forEach(e -> kafkaTemplate.send("domain-events", streamId, serialize(e)));
    }
    
    public List<DomainEvent> loadEvents(String streamId) {
        return eventRepository.getEvents(streamId);
    }
}
```

---

## 12. Idempotency Handler

```java
/**
 * Idempotency: Ensure an operation produces the same result regardless of how many
 * times it's called. Critical for payment processing, API retries.
 */
public class IdempotencyService {
    private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
    
    public <T> T executeIdempotent(String idempotencyKey, Supplier<T> operation) {
        // Check if already processed
        IdempotencyRecord existing = store.get(idempotencyKey);
        if (existing != null && existing.isCompleted()) {
            return (T) existing.getResult();  // Return cached result
        }
        
        // Try to claim this key
        IdempotencyRecord newRecord = new IdempotencyRecord(IdempotencyStatus.IN_PROGRESS);
        IdempotencyRecord claimed = store.putIfAbsent(idempotencyKey, newRecord);
        
        if (claimed != null) {
            // Another thread is processing — wait for result
            return waitForResult(claimed);
        }
        
        // We own this key — execute operation
        try {
            T result = operation.get();
            newRecord.complete(result);
            return result;
        } catch (Exception e) {
            store.remove(idempotencyKey);  // Allow retry on failure
            throw e;
        }
    }
}
```

---

## 13. Bloom Filter (Membership Check)

```java
/**
 * Bloom Filter: Space-efficient probabilistic "might contain" check.
 * False positives possible, false negatives impossible.
 * Use: Username taken check, URL already crawled, email already sent.
 */
public class BloomFilter {
    private final BitSet bitSet;
    private final int size;
    private final int hashCount;
    
    public BloomFilter(int expectedElements, double falsePositiveRate) {
        this.size = optimalSize(expectedElements, falsePositiveRate);
        this.hashCount = optimalHashCount(size, expectedElements);
        this.bitSet = new BitSet(size);
    }
    
    public void add(String element) {
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            bitSet.set(Math.abs(hash % size));
        }
    }
    
    public boolean mightContain(String element) {
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            if (!bitSet.get(Math.abs(hash % size))) return false;
        }
        return true;  // Might be a false positive!
    }
    
    private int hash(String element, int seed) {
        return Hashing.murmur3_128(seed).hashString(element, StandardCharsets.UTF_8).asInt();
    }
    
    private static int optimalSize(int n, double p) { return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2))); }
    private static int optimalHashCount(int m, int n) { return Math.max(1, (int) Math.round((double) m / n * Math.log(2))); }
}
```

---

## 14. Consistent Hashing (Ring)

```java
/**
 * Consistent Hashing: Distribute keys across nodes with minimal redistribution
 * when nodes are added/removed. Used by: DynamoDB, Cassandra, load balancers.
 */
public class ConsistentHashRing<T> {
    private final TreeMap<Long, T> ring = new TreeMap<>();
    private final int virtualNodes;
    private final HashFunction hashFunction = Hashing.murmur3_128();
    
    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }
    
    public void addNode(T node) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(node.toString() + "#" + i);
            ring.put(hash, node);
        }
    }
    
    public void removeNode(T node) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(node.toString() + "#" + i);
            ring.remove(hash);
        }
    }
    
    public T getNode(String key) {
        if (ring.isEmpty()) throw new IllegalStateException("Ring is empty");
        long hash = hash(key);
        Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
        return (entry != null) ? entry.getValue() : ring.firstEntry().getValue();
    }
    
    private long hash(String key) {
        return hashFunction.hashString(key, StandardCharsets.UTF_8).asLong();
    }
}
```

---

## 15. Leader Election

```java
/**
 * Leader Election: Ensure exactly one instance runs a task (job scheduler,
 * partition coordinator, etc.). Uses atomic Redis operation.
 */
public class RedisLeaderElection {
    private final StringRedisTemplate redis;
    private final String instanceId = UUID.randomUUID().toString();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isLeader = false;
    
    public void startElection(String electionName) {
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                Boolean acquired = redis.opsForValue().setIfAbsent(
                    "leader:" + electionName, instanceId, Duration.ofSeconds(30));
                
                if (Boolean.TRUE.equals(acquired)) {
                    if (!isLeader) log.info("Became leader for: {}", electionName);
                    isLeader = true;
                } else {
                    // Check if we're still the leader (refresh TTL)
                    String currentLeader = redis.opsForValue().get("leader:" + electionName);
                    if (instanceId.equals(currentLeader)) {
                        redis.expire("leader:" + electionName, Duration.ofSeconds(30));
                        isLeader = true;
                    } else {
                        if (isLeader) log.info("Lost leadership for: {}", electionName);
                        isLeader = false;
                    }
                }
            } catch (Exception e) {
                log.error("Leader election error", e);
                isLeader = false;
            }
        }, 0, 10, TimeUnit.SECONDS);  // Heartbeat every 10s
    }
    
    public boolean isLeader() { return isLeader; }
}
```

---

## 16. Write-Ahead Log (WAL)

```java
/**
 * WAL: Write operations to a durable log before applying to state.
 * On crash recovery, replay the log. Used by: databases, Kafka, Redis AOF.
 */
public class WriteAheadLog {
    private final Path logFile;
    private final FileChannel channel;
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private final ReentrantLock writeLock = new ReentrantLock();
    
    public long append(byte[] entry) throws IOException {
        writeLock.lock();
        try {
            long seq = sequenceNumber.incrementAndGet();
            ByteBuffer buffer = ByteBuffer.allocate(8 + 4 + entry.length);
            buffer.putLong(seq);
            buffer.putInt(entry.length);
            buffer.put(entry);
            buffer.flip();
            
            channel.write(buffer);
            channel.force(true);  // Fsync — durable after this
            return seq;
        } finally {
            writeLock.unlock();
        }
    }
    
    public List<WalEntry> replayFrom(long fromSequence) throws IOException {
        List<WalEntry> entries = new ArrayList<>();
        ByteBuffer header = ByteBuffer.allocate(12);
        channel.position(0);
        
        while (channel.read(header) == 12) {
            header.flip();
            long seq = header.getLong();
            int length = header.getInt();
            
            if (seq >= fromSequence) {
                ByteBuffer data = ByteBuffer.allocate(length);
                channel.read(data);
                entries.add(new WalEntry(seq, data.array()));
            } else {
                channel.position(channel.position() + length);
            }
            header.clear();
        }
        return entries;
    }
}
```

---

## 17-25: Additional Patterns (Compact)

### 17. Backpressure Handler
```java
public class BackpressureQueue<T> {
    private final BlockingQueue<T> queue;
    private final AtomicLong dropped = new AtomicLong(0);
    
    public BackpressureQueue(int capacity) { this.queue = new ArrayBlockingQueue<>(capacity); }
    
    // Strategy 1: Block producer
    public void putBlocking(T item) throws InterruptedException { queue.put(item); }
    
    // Strategy 2: Drop newest (if full)
    public boolean offerOrDrop(T item) {
        if (!queue.offer(item)) { dropped.incrementAndGet(); return false; }
        return true;
    }
    
    // Strategy 3: Drop oldest (if full)
    public void offerDropOldest(T item) {
        while (!queue.offer(item)) { queue.poll(); dropped.incrementAndGet(); }
    }
}
```

### 18. Bulkhead Isolation
```java
public class BulkheadExecutor {
    private final Map<String, Semaphore> bulkheads = new ConcurrentHashMap<>();
    
    public <T> T execute(String service, int maxConcurrent, Supplier<T> action, Supplier<T> fallback) {
        Semaphore sem = bulkheads.computeIfAbsent(service, k -> new Semaphore(maxConcurrent));
        if (!sem.tryAcquire()) return fallback.get();
        try { return action.get(); }
        finally { sem.release(); }
    }
}
```

### 19. Lease-Based Distributed Lock
```java
public class LeaseLock {
    private final StringRedisTemplate redis;
    
    public Optional<String> acquire(String resource, Duration lease) {
        String token = UUID.randomUUID().toString();
        Boolean got = redis.opsForValue().setIfAbsent("lock:" + resource, token, lease);
        return Boolean.TRUE.equals(got) ? Optional.of(token) : Optional.empty();
    }
    
    public boolean release(String resource, String token) {
        String script = "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
        Long result = redis.execute(new DefaultRedisScript<>(script, Long.class), List.of("lock:" + resource), token);
        return result != null && result == 1;
    }
}
```

### 20. Deduplication Service
```java
public class DeduplicationService {
    private final StringRedisTemplate redis;
    
    public boolean isDuplicate(String eventId) {
        Boolean isNew = redis.opsForValue().setIfAbsent("dedup:" + eventId, "1", Duration.ofHours(24));
        return !Boolean.TRUE.equals(isNew);  // true = already seen
    }
}
```

### 21. Snowflake ID Generator
```java
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1704067200000L; // 2024-01-01
    private final long workerId;
    private long sequence = 0;
    private long lastTimestamp = -1;
    
    public SnowflakeIdGenerator(long workerId) { this.workerId = workerId & 0x3FF; }
    
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 0xFFF;
            if (sequence == 0) timestamp = waitNextMillis(lastTimestamp);
        } else { sequence = 0; }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << 22) | (workerId << 12) | sequence;
    }
    
    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) ts = System.currentTimeMillis();
        return ts;
    }
}
```

### 22. Read-Your-Writes Consistency
```java
public class ReadYourWritesCache {
    private final ConcurrentHashMap<String, VersionedValue> localWrites = new ConcurrentHashMap<>();
    
    public void onWrite(String key, Object value, long version) {
        localWrites.put(key, new VersionedValue(value, version, Instant.now()));
    }
    
    public Object read(String key, Supplier<Object> readFromReplica) {
        VersionedValue local = localWrites.get(key);
        if (local != null && local.getWrittenAt().isAfter(Instant.now().minus(Duration.ofSeconds(5)))) {
            return local.getValue();  // Return own recent write
        }
        return readFromReplica.get();  // Safe to read from replica
    }
}
```

### 23. Hot Key Handler
```java
public class HotKeyHandler {
    private final ConcurrentHashMap<String, LongAdder> accessCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> localCache = new ConcurrentHashMap<>();
    private static final int HOT_THRESHOLD = 1000;  // >1000 accesses/min = hot
    
    public Object get(String key, Supplier<Object> loader) {
        accessCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        
        if (isHotKey(key)) {
            return localCache.computeIfAbsent(key, k -> loader.get());  // JVM-local cache
        }
        return loader.get();  // Normal path (Redis/DB)
    }
    
    private boolean isHotKey(String key) {
        LongAdder counter = accessCounts.get(key);
        return counter != null && counter.sum() > HOT_THRESHOLD;
    }
}
```

### 24. Fan-Out on Write (Timeline)
```java
public void fanOutTweet(String authorId, String tweetId) {
    List<String> followers = followerService.getFollowerIds(authorId);
    List<List<String>> batches = Lists.partition(followers, 500);
    
    CountDownLatch latch = new CountDownLatch(batches.size());
    for (List<String> batch : batches) {
        executor.submit(() -> {
            try {
                for (String followerId : batch) {
                    redis.opsForList().leftPush("timeline:" + followerId, tweetId);
                    redis.opsForList().trim("timeline:" + followerId, 0, 799);
                }
            } finally { latch.countDown(); }
        });
    }
    latch.await(60, TimeUnit.SECONDS);
}
```

### 25. CDC Consumer
```java
@KafkaListener(topics = "dbserver.orders", groupId = "search-indexer")
public void onCdcEvent(ConsumerRecord<String, String> record) {
    CdcEvent event = mapper.readValue(record.value(), CdcEvent.class);
    
    switch (event.getOp()) {
        case "c", "u" -> elasticsearchService.index(event.getAfter());  // Create/Update
        case "d" -> elasticsearchService.delete(event.getBefore().get("id"));  // Delete
    }
}
```

---

## 🏆 Pattern Decision Matrix

| Problem | Pattern | When to Use |
|---|---|---|
| Flash sale overload | Virtual Queue | Demand >> capacity, need fairness |
| Millions of increments/sec | Distributed Counter (sharded) | View counts, likes, metrics |
| Find popular items | Top-K (sliding window) | Trending, leaderboards |
| Celebrity with 50M followers | Hybrid Fan-Out | Push for small users, pull for celebrities |
| API abuse prevention | Token Bucket / Sliding Window | Per-user/per-IP rate limiting |
| Cascading failures | Circuit Breaker | Downstream service flaky |
| Multi-service transaction | Saga + Compensation | Order: reserve → pay → ship |
| DB + event atomicity | Outbox Pattern | Publish event reliably with DB write |
| Read/write at different scale | CQRS | 100x more reads than writes |
| Audit trail + replay | Event Sourcing | Financial, compliance, time-travel |
| Prevent double-processing | Idempotency Key | Payment retries, API retries |
| "Might exist?" check | Bloom Filter | URL crawled, username taken |
| Distribute keys evenly | Consistent Hashing | Sharding, load balancing |
| Single coordinator | Leader Election | Job scheduler, rebalancer |
| Crash recovery | Write-Ahead Log | Database, message broker |
| Too fast producer | Backpressure | Queue overflow prevention |
| Isolate failures | Bulkhead | Per-service thread/connection limits |
| Temporary exclusive access | Lease Lock | Distributed mutex with expiry |
| Exactly-once delivery | Deduplication | Event replay, message retries |
| Globally unique IDs | Snowflake | Distributed ID generation |
| See your own writes | Read-Your-Writes | After profile update, user sees change |
| One key gets 90% traffic | Hot Key Handler | Celebrity profile, viral video |
| Real-time timeline | Fan-Out on Write | Social feed, notifications |
| Keep systems in sync | CDC (Change Data Capture) | DB → Search index, DB → Cache |

---

*Every pattern above is implemented in pure Java with no external dependencies beyond Redis/Kafka where noted. Each is a self-contained production component.*
