# ☕ Java Concurrency Real-World Applications — Complete Production Examples

> Each example below is a **full mini system design** showing exactly how Java concurrency is used in production at companies like Netflix, Uber, Amazon, Stripe, Twitter, and more. Every thread pool, lock, atomic operation, and synchronization decision is explained with the **WHY** behind it — all in **Java only**.

---

## 📋 Table of Contents

- [1. Netflix — Circuit Breaker with Thread Pool Isolation](#1-netflix--circuit-breaker-with-thread-pool-isolation)
- [2. Uber — Concurrent Ride Matching Engine](#2-uber--concurrent-ride-matching-engine)
- [3. Amazon — Concurrent Shopping Cart with Optimistic Locking](#3-amazon--concurrent-shopping-cart-with-optimistic-locking)
- [4. Stripe — Idempotent Payment Processing with CAS](#4-stripe--idempotent-payment-processing-with-cas)
- [5. Twitter — Concurrent Fan-Out on Write](#5-twitter--concurrent-fan-out-on-write)
- [6. Netflix — Async Recommendation Aggregator](#6-netflix--async-recommendation-aggregator)
- [7. Uber — Real-Time Surge Pricing Calculator](#7-uber--real-time-surge-pricing-calculator)
- [8. Amazon — Inventory Reservation with Distributed Locking](#8-amazon--inventory-reservation-with-distributed-locking)
- [9. Slack — Concurrent WebSocket Connection Manager](#9-slack--concurrent-websocket-connection-manager)
- [10. DoorDash — Order State Machine with Event-Driven Concurrency](#10-doordash--order-state-machine-with-event-driven-concurrency)
- [11. Robinhood — Stock Price Streaming with Ring Buffer](#11-robinhood--stock-price-streaming-with-ring-buffer)
- [12. WhatsApp — Message Delivery Queue per User](#12-whatsapp--message-delivery-queue-per-user)
- [13. YouTube — Concurrent View Counter (LongAdder)](#13-youtube--concurrent-view-counter-longadder)
- [14. LinkedIn — Parallel Profile Enrichment Pipeline](#14-linkedin--parallel-profile-enrichment-pipeline)
- [15. Spotify — Concurrent Playlist Operations with ReadWriteLock](#15-spotify--concurrent-playlist-operations-with-readwritelock)
- [16. Google — Concurrent Rate Limiter (Token Bucket)](#16-google--concurrent-rate-limiter-token-bucket)
- [17. Netflix — Bulkhead Pattern for Service Isolation](#17-netflix--bulkhead-pattern-for-service-isolation)
- [18. Amazon — Concurrent Search Aggregator (Fan-Out/Fan-In)](#18-amazon--concurrent-search-aggregator-fan-outfan-in)
- [19. Uber — Concurrent ETA Calculator with Virtual Threads](#19-uber--concurrent-eta-calculator-with-virtual-threads)
- [20. Stripe — Webhook Delivery with Retry and Backoff](#20-stripe--webhook-delivery-with-retry-and-backoff)
- [21. Twitter — Trending Topics with Concurrent Sliding Window](#21-twitter--trending-topics-with-concurrent-sliding-window)
- [22. Netflix — Session Management with Concurrent Cache](#22-netflix--session-management-with-concurrent-cache)
- [23. DoorDash — Concurrent Delivery Assignment with Fair Locking](#23-doordash--concurrent-delivery-assignment-with-fair-locking)
- [24. Amazon — Flash Sale with AtomicInteger Stock Counter](#24-amazon--flash-sale-with-atomicinteger-stock-counter)
- [25. Kafka Consumer — Multi-Threaded Partition Processing](#25-kafka-consumer--multi-threaded-partition-processing)
- [🏆 Cheat Sheet: Java Concurrency Pattern Selection Guide](#-cheat-sheet-java-concurrency-pattern-selection-guide)
- [🎯 Interview Summary: "When Would You Use Which Pattern?"](#-interview-summary-when-would-you-use-which-pattern)

---

## 1. Netflix — Circuit Breaker with Thread Pool Isolation

### 🏗️ Architecture Context

Netflix calls 100+ downstream microservices per API request. If one service goes down, its slow responses can consume all threads and cascade failures across the entire system. Solution: Each downstream service gets its own isolated thread pool (bulkhead pattern) with a circuit breaker that trips after threshold failures.

### 📐 Concurrency Design

| Component | Pattern | Why |
|---|---|---|
| Per-service thread pool | `ThreadPoolExecutor` with bounded queue | Isolation — slow service can't consume all threads |
| Circuit state | `AtomicReference<State>` | Lock-free state transitions (CLOSED→OPEN→HALF_OPEN) |
| Failure counter | `AtomicInteger` | Lock-free increment under high concurrency |
| Timeout tracking | `CompletableFuture.orTimeout()` | Non-blocking timeout without blocking threads |
| Metrics window | `LongAdder` + scheduled reset | High-throughput counting with minimal contention |

### 💡 Why This Concurrency Design?

- **Separate thread pools per service**: If payment-service is slow (5s timeout), its 10 threads fill up, but recommendation-service still has its own 10 threads running fine. Without isolation, one slow service blocks ALL 200 shared threads.
- **AtomicReference for state**: Circuit breaker state changes are rare but must be visible to all threads immediately. CAS ensures exactly one thread transitions the state.
- **LongAdder for metrics**: Thousands of requests/sec updating failure counts — AtomicLong would cause CAS contention; LongAdder distributes across cells.

### 🔧 Java Implementation — Step by Step

```java
public class CircuitBreaker {
    
    enum State { CLOSED, OPEN, HALF_OPEN }
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    
    private final int failureThreshold;       // e.g., 5 failures
    private final long resetTimeoutMs;         // e.g., 30 seconds
    private final int halfOpenMaxRequests;     // e.g., 3 test requests
    
    private final ThreadPoolExecutor servicePool;
    private final String serviceName;
    
    public CircuitBreaker(String serviceName, int poolSize, int queueSize,
                          int failureThreshold, long resetTimeoutMs) {
        this.serviceName = serviceName;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.halfOpenMaxRequests = 3;
        
        // Isolated thread pool for this specific downstream service
        this.servicePool = new ThreadPoolExecutor(
            poolSize, poolSize,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueSize),
            new ThreadFactoryBuilder()
                .setNameFormat(serviceName + "-circuit-%d")
                .build(),
            new ThreadPoolExecutor.AbortPolicy()  // Reject immediately when full
        );
    }
    
    // ====== EXECUTE WITH CIRCUIT BREAKER ======
    public <T> CompletableFuture<T> execute(Supplier<T> action, Supplier<T> fallback) {
        // Step 1: Check if circuit allows request
        if (!allowRequest()) {
            Metrics.counter("circuit.rejected", "service", serviceName).increment();
            return CompletableFuture.completedFuture(fallback.get());
        }
        
        // Step 2: Execute in isolated thread pool with timeout
        return CompletableFuture.supplyAsync(action, servicePool)
            .orTimeout(2, TimeUnit.SECONDS)  // Hard timeout
            .thenApply(result -> {
                onSuccess();
                return result;
            })
            .exceptionally(ex -> {
                onFailure();
                Metrics.counter("circuit.failure", "service", serviceName).increment();
                return fallback.get();
            });
    }
    
    // ====== STATE MACHINE ======
    private boolean allowRequest() {
        State current = state.get();
        
        switch (current) {
            case CLOSED:
                return true;  // Normal operation
                
            case OPEN:
                // Check if reset timeout has passed
                if (System.currentTimeMillis() - lastFailureTime >= resetTimeoutMs) {
                    // Try to transition to HALF_OPEN (only one thread wins this CAS)
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        successCount.set(0);
                        Metrics.counter("circuit.half_open", "service", serviceName).increment();
                    }
                    return true;  // Allow test request
                }
                return false;  // Still in cooldown — reject
                
            case HALF_OPEN:
                // Allow limited test requests
                return successCount.get() < halfOpenMaxRequests;
                
            default:
                return false;
        }
    }
    
    private void onSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= halfOpenMaxRequests) {
                // Enough successes — close the circuit (CAS for thread safety)
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    failureCount.set(0);
                    log.info("[{}] Circuit CLOSED — service recovered", serviceName);
                }
            }
        } else {
            failureCount.set(0);  // Reset on success in CLOSED state
        }
    }
    
    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        int failures = failureCount.incrementAndGet();
        
        if (failures >= failureThreshold) {
            // Trip the circuit (CAS — only one thread opens it)
            State prev = state.get();
            if (prev == State.CLOSED || prev == State.HALF_OPEN) {
                if (state.compareAndSet(prev, State.OPEN)) {
                    log.warn("[{}] Circuit OPEN after {} failures!", serviceName, failures);
                    Metrics.counter("circuit.opened", "service", serviceName).increment();
                }
            }
        }
    }
}

// ====== USAGE IN PRODUCTION ======
public class RecommendationService {
    private final CircuitBreaker mlServiceBreaker = 
        new CircuitBreaker("ml-ranking", 10, 50, 5, 30_000);
    private final CircuitBreaker userServiceBreaker = 
        new CircuitBreaker("user-profile", 15, 100, 3, 20_000);
    
    public CompletableFuture<List<Recommendation>> getRecommendations(String userId) {
        CompletableFuture<UserProfile> profileFuture = userServiceBreaker.execute(
            () -> userService.getProfile(userId),
            () -> UserProfile.DEFAULT  // Fallback: cached/default profile
        );
        
        return profileFuture.thenCompose(profile -> 
            mlServiceBreaker.execute(
                () -> mlService.rank(profile),
                () -> getPopularItems()  // Fallback: trending items
            )
        );
    }
}
```

---

## 2. Uber — Concurrent Ride Matching Engine

### 🏗️ Architecture Context

Uber matches riders to drivers in real-time. When a rider requests a ride, the system must concurrently scan nearby drivers, check their availability, calculate ETAs, and assign the best match — all within 2-3 seconds. Multiple riders in the same area compete for the same drivers simultaneously.

### 📐 Concurrency Design

| Component | Pattern | Why |
|---|---|---|
| Driver location index | `ConcurrentHashMap<GeoCell, Set<Driver>>` | Concurrent reads/writes as drivers move |
| Driver availability | `AtomicBoolean` per driver | Lock-free availability toggle |
| Ride assignment | `compareAndSet` on driver state | Exactly one rider wins a driver (no double-booking) |
| ETA calculation | `CompletableFuture.allOf()` parallel fan-out | Calculate ETAs for 10+ drivers simultaneously |
| Match scoring | `ForkJoinPool` | Parallel scoring of candidates |

### 🔧 Java Implementation

```java
public class RideMatchingEngine {
    
    // Geo-sharded driver index: each cell is ~100m x 100m
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<DriverState>> driverGrid = 
        new ConcurrentHashMap<>();
    
    private final ExecutorService etaPool = new ThreadPoolExecutor(
        20, 50, 60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(200),
        new ThreadFactoryBuilder().setNameFormat("eta-calc-%d").build()
    );
    
    // ====== DRIVER STATE — Thread-safe with CAS ======
    public static class DriverState {
        private final String driverId;
        private final AtomicReference<DriverStatus> status;
        private volatile double latitude;
        private volatile double longitude;
        private volatile long lastUpdateMs;
        
        enum DriverStatus { AVAILABLE, MATCHING, ON_TRIP, OFFLINE }
        
        // CAS-based claim: exactly ONE rider can claim this driver
        public boolean tryClaim() {
            return status.compareAndSet(DriverStatus.AVAILABLE, DriverStatus.MATCHING);
        }
        
        public void releaseClaim() {
            status.compareAndSet(DriverStatus.MATCHING, DriverStatus.AVAILABLE);
        }
        
        public void confirmTrip() {
            status.set(DriverStatus.ON_TRIP);
        }
    }
    
    // ====== MATCH RIDER TO BEST DRIVER ======
    public CompletableFuture<MatchResult> matchRider(RideRequest request) {
        // Step 1: Find nearby drivers (concurrent read from geo-index)
        List<DriverState> candidates = findNearbyDrivers(
            request.getPickupLat(), request.getPickupLng(), 3.0 // 3km radius
        );
        
        if (candidates.isEmpty()) {
            return CompletableFuture.completedFuture(MatchResult.noDrivers());
        }
        
        // Step 2: Calculate ETA for all candidates IN PARALLEL
        List<CompletableFuture<ScoredDriver>> etaFutures = candidates.stream()
            .map(driver -> CompletableFuture.supplyAsync(() -> {
                double eta = calculateETA(driver, request);
                double score = scoreDriver(driver, request, eta);
                return new ScoredDriver(driver, eta, score);
            }, etaPool))
            .collect(Collectors.toList());
        
        // Step 3: Wait for all ETAs, sort by score, try to claim best driver
        return CompletableFuture.allOf(etaFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<ScoredDriver> scored = etaFutures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparingDouble(ScoredDriver::getScore).reversed())
                    .collect(Collectors.toList());
                
                // Step 4: Try to CLAIM the best driver (CAS — handle race conditions)
                for (ScoredDriver candidate : scored) {
                    if (candidate.getDriver().tryClaim()) {
                        // WE WON! This driver is now ours
                        log.info("Matched rider {} to driver {} (ETA: {}s)",
                            request.getRiderId(), candidate.getDriver().getDriverId(),
                            candidate.getEta());
                        return MatchResult.success(candidate);
                    }
                    // Another rider claimed this driver first — try next best
                    Metrics.counter("match.contention").increment();
                }
                
                // All drivers were claimed by other riders
                return MatchResult.noDrivers();
            });
    }
    
    // ====== GEO-INDEX: Concurrent driver location updates ======
    public void updateDriverLocation(String driverId, double lat, double lng) {
        String oldCell = getGeoCell(driverLocations.get(driverId));
        String newCell = getGeoCell(lat, lng);
        
        if (!newCell.equals(oldCell)) {
            // Driver moved to new cell — atomic remove + add
            if (oldCell != null) {
                driverGrid.computeIfPresent(oldCell, (cell, drivers) -> {
                    drivers.removeIf(d -> d.getDriverId().equals(driverId));
                    return drivers.isEmpty() ? null : drivers;  // Remove empty cells
                });
            }
            driverGrid.computeIfAbsent(newCell, k -> new ConcurrentSkipListSet<>())
                .add(getDriverState(driverId));
        }
        
        // Update coordinates (volatile writes — visible to matching threads)
        DriverState state = getDriverState(driverId);
        state.latitude = lat;
        state.longitude = lng;
        state.lastUpdateMs = System.currentTimeMillis();
    }
    
    private List<DriverState> findNearbyDrivers(double lat, double lng, double radiusKm) {
        Set<String> nearbyCells = getNearbyCells(lat, lng, radiusKm);
        
        return nearbyCells.stream()
            .map(driverGrid::get)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .filter(d -> d.status.get() == DriverState.DriverStatus.AVAILABLE)
            .filter(d -> System.currentTimeMillis() - d.lastUpdateMs < 30_000) // Active in last 30s
            .collect(Collectors.toList());
    }
}
```

---

## 3. Amazon — Concurrent Shopping Cart with Optimistic Locking

### 🏗️ Architecture Context

Amazon's shopping cart handles 400M+ item additions per day. Multiple browser tabs, mobile app, and Alexa can modify the same cart simultaneously. The system must handle concurrent modifications without losing items or corrupting quantities.

### 📐 Concurrency Design

| Component | Pattern | Why |
|---|---|---|
| Cart state | `ConcurrentHashMap<ItemId, AtomicInteger>` | Lock-free per-item quantity updates |
| Cart version | `AtomicLong` (optimistic locking) | Detect concurrent modifications |
| Price calculation | `ReadWriteLock` | Consistent snapshot for checkout |
| Cart merge | `computeIfAbsent` + `addAndGet` | Atomic add-or-create without external lock |

### 🔧 Java Implementation

```java
public class ConcurrentShoppingCart {
    
    private final String cartId;
    private final ConcurrentHashMap<String, CartItem> items = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);
    private final ReadWriteLock checkoutLock = new ReentrantReadWriteLock();
    
    public static class CartItem {
        private final String productId;
        private final AtomicInteger quantity;
        private volatile BigDecimal unitPrice;
        private volatile String productName;
        
        public CartItem(String productId, int qty, BigDecimal price, String name) {
            this.productId = productId;
            this.quantity = new AtomicInteger(qty);
            this.unitPrice = price;
            this.productName = name;
        }
    }
    
    // ====== ADD ITEM — Lock-free, handles concurrent adds ======
    public CartModificationResult addItem(String productId, int quantity, 
                                           BigDecimal price, String name) {
        checkoutLock.readLock().lock();  // Allow concurrent adds, block during checkout
        try {
            CartItem item = items.computeIfAbsent(productId, id -> 
                new CartItem(id, 0, price, name)  // Create with 0, then add below
            );
            
            int newQty = item.quantity.addAndGet(quantity);  // Atomic add
            
            // Enforce max quantity per item
            if (newQty > 99) {
                item.quantity.addAndGet(-quantity);  // Roll back
                return CartModificationResult.maxQuantityExceeded();
            }
            
            long newVersion = version.incrementAndGet();
            Metrics.counter("cart.item.added").increment();
            
            return CartModificationResult.success(newVersion, getCartSummary());
        } finally {
            checkoutLock.readLock().unlock();
        }
    }
    
    // ====== UPDATE QUANTITY — CAS loop for safety ======
    public CartModificationResult updateQuantity(String productId, int newQuantity,
                                                  long expectedVersion) {
        checkoutLock.readLock().lock();
        try {
            // Optimistic locking: reject if cart was modified since client's last read
            if (version.get() != expectedVersion) {
                return CartModificationResult.versionConflict(version.get());
            }
            
            CartItem item = items.get(productId);
            if (item == null) {
                return CartModificationResult.itemNotFound();
            }
            
            if (newQuantity <= 0) {
                items.remove(productId);
            } else {
                item.quantity.set(newQuantity);
            }
            
            long newVersion = version.incrementAndGet();
            return CartModificationResult.success(newVersion, getCartSummary());
        } finally {
            checkoutLock.readLock().unlock();
        }
    }
    
    // ====== CHECKOUT — Exclusive lock, consistent snapshot ======
    public CheckoutResult checkout(long expectedVersion) {
        checkoutLock.writeLock().lock();  // Block ALL modifications during checkout
        try {
            if (version.get() != expectedVersion) {
                return CheckoutResult.versionConflict(
                    "Cart modified since last view. Please review changes.");
            }
            
            if (items.isEmpty()) {
                return CheckoutResult.emptyCart();
            }
            
            // Create immutable snapshot for payment processing
            Map<String, Integer> snapshot = items.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    e -> e.getValue().quantity.get()
                ));
            
            BigDecimal total = items.values().stream()
                .map(item -> item.unitPrice.multiply(BigDecimal.valueOf(item.quantity.get())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return CheckoutResult.success(snapshot, total);
        } finally {
            checkoutLock.writeLock().unlock();
        }
    }
    
    // ====== MERGE CARTS — When user logs in (guest cart + account cart) ======
    public void mergeCarts(ConcurrentShoppingCart otherCart) {
        checkoutLock.writeLock().lock();
        try {
            otherCart.items.forEach((productId, otherItem) -> {
                items.merge(productId, otherItem, (existing, incoming) -> {
                    existing.quantity.addAndGet(incoming.quantity.get());
                    return existing;
                });
            });
            version.incrementAndGet();
        } finally {
            checkoutLock.writeLock().unlock();
        }
    }
}
```

---

## 4. Stripe — Idempotent Payment Processing with CAS

### 🏗️ Architecture Context

Payment systems must NEVER charge a customer twice. Network retries, client timeouts, and server restarts can cause duplicate requests. Stripe uses idempotency keys to ensure exactly-once processing even under concurrent retries.

### 🔧 Java Implementation

```java
public class IdempotentPaymentProcessor {
    
    // Idempotency store: key → processing state
    private final ConcurrentHashMap<String, PaymentState> idempotencyStore = 
        new ConcurrentHashMap<>();
    
    enum ProcessingStatus { IN_PROGRESS, COMPLETED, FAILED }
    
    static class PaymentState {
        final AtomicReference<ProcessingStatus> status;
        final CountDownLatch completionLatch;  // Other threads wait for first to finish
        volatile PaymentResult result;
        final long createdAt;
        
        PaymentState() {
            this.status = new AtomicReference<>(ProcessingStatus.IN_PROGRESS);
            this.completionLatch = new CountDownLatch(1);
            this.createdAt = System.currentTimeMillis();
        }
    }
    
    public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) 
            throws InterruptedException {
        
        // Step 1: Try to claim this idempotency key (CAS — first writer wins)
        PaymentState newState = new PaymentState();
        PaymentState existing = idempotencyStore.putIfAbsent(idempotencyKey, newState);
        
        if (existing != null) {
            // Key already exists — this is a RETRY
            return handleRetry(existing, idempotencyKey);
        }
        
        // Step 2: WE are the first request with this key — process payment
        try {
            PaymentResult result = executePayment(request);
            newState.result = result;
            newState.status.set(ProcessingStatus.COMPLETED);
            newState.completionLatch.countDown();  // Wake up any waiting retries
            
            Metrics.counter("payment.processed").increment();
            return result;
            
        } catch (Exception e) {
            newState.result = PaymentResult.failed(e.getMessage());
            newState.status.set(ProcessingStatus.FAILED);
            newState.completionLatch.countDown();
            
            // Remove failed entries so retry can try again
            idempotencyStore.remove(idempotencyKey);
            Metrics.counter("payment.failed").increment();
            throw e;
        }
    }
    
    private PaymentResult handleRetry(PaymentState existing, String key) 
            throws InterruptedException {
        switch (existing.status.get()) {
            case COMPLETED:
                // Already processed — return cached result (idempotent!)
                Metrics.counter("payment.idempotent.hit").increment();
                return existing.result;
                
            case IN_PROGRESS:
                // Another thread is currently processing — WAIT for it
                log.info("Idempotency key {} in progress, waiting...", key);
                boolean completed = existing.completionLatch.await(30, TimeUnit.SECONDS);
                if (completed) {
                    return existing.result;
                }
                throw new TimeoutException("Payment processing timeout for key: " + key);
                
            case FAILED:
                // Previous attempt failed — allow retry
                Metrics.counter("payment.retry.after_failure").increment();
                idempotencyStore.remove(key);
                return processPayment(key, reconstructRequest(key));
                
            default:
                throw new IllegalStateException("Unknown status");
        }
    }
    
    // Cleanup expired idempotency keys (run every hour)
    @Scheduled(fixedRate = 3600_000)
    public void cleanupExpiredKeys() {
        long expiryThreshold = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        idempotencyStore.entrySet().removeIf(entry -> 
            entry.getValue().createdAt < expiryThreshold &&
            entry.getValue().status.get() != ProcessingStatus.IN_PROGRESS
        );
    }
}
```

---

## 5. Twitter — Concurrent Fan-Out on Write

### 🏗️ Architecture Context

When a celebrity with 50M followers tweets, that tweet must be pushed into 50 million user timelines. This fan-out is done concurrently across a pool of workers, each handling a batch of followers. The system must handle 600K+ tweets/sec at peak.

### 🔧 Java Implementation

```java
public class TweetFanOutService {
    
    private final ExecutorService fanOutPool = new ThreadPoolExecutor(
        32, 64, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(10_000),
        new ThreadFactoryBuilder().setNameFormat("fanout-worker-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy()  // Backpressure when overwhelmed
    );
    
    private final LongAdder totalFanOuts = new LongAdder();
    private final LongAdder totalFailures = new LongAdder();
    private final Semaphore concurrentFanOuts = new Semaphore(100);  // Max 100 concurrent tweets
    
    // ====== FAN-OUT A TWEET TO ALL FOLLOWERS ======
    public CompletableFuture<FanOutResult> fanOutTweet(Tweet tweet) {
        List<String> followers = getFollowerIds(tweet.getAuthorId());
        
        // For celebrities (>500K followers): use async fan-out
        if (followers.size() > 500_000) {
            return asyncFanOut(tweet, followers);
        }
        
        // For normal users: synchronous batched fan-out
        return batchedFanOut(tweet, followers);
    }
    
    private CompletableFuture<FanOutResult> batchedFanOut(Tweet tweet, List<String> followers) {
        // Acquire semaphore to limit concurrent fan-outs
        try {
            if (!concurrentFanOuts.tryAcquire(5, TimeUnit.SECONDS)) {
                return CompletableFuture.completedFuture(FanOutResult.throttled());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        
        // Split followers into batches of 1000
        List<List<String>> batches = partition(followers, 1000);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch completionLatch = new CountDownLatch(batches.size());
        
        for (List<String> batch : batches) {
            fanOutPool.submit(() -> {
                try {
                    // Write tweet to each user's timeline cache
                    for (String followerId : batch) {
                        try {
                            timelineCache.pushToTimeline(followerId, tweet);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            deadLetterQueue.add(new FailedFanOut(followerId, tweet));
                        }
                    }
                } finally {
                    completionLatch.countDown();
                    totalFanOuts.add(batch.size());
                }
            });
        }
        
        // Return future that completes when all batches are done
        return CompletableFuture.supplyAsync(() -> {
            try {
                completionLatch.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrentFanOuts.release();
            }
            return new FanOutResult(successCount.get(), failCount.get());
        });
    }
    
    private CompletableFuture<FanOutResult> asyncFanOut(Tweet tweet, List<String> followers) {
        // Celebrity tweet: push to Kafka for async processing
        // Don't block the request thread for 50M fan-outs
        kafkaProducer.send("celebrity-fanout", tweet.getAuthorId(), 
            new FanOutEvent(tweet, followers.size()));
        return CompletableFuture.completedFuture(FanOutResult.queued(followers.size()));
    }
}
```

---

## 6. Netflix — Async Recommendation Aggregator

### 🏗️ Architecture Context

Netflix's home page calls 5-8 different recommendation engines in parallel (trending, because-you-watched, new-releases, top-picks, etc.). Each engine has different latency profiles. The page must render in <200ms even if some engines are slow.

### 🔧 Java Implementation

```java
public class RecommendationAggregator {
    
    private final ExecutorService recPool = Executors.newFixedThreadPool(30,
        new ThreadFactoryBuilder().setNameFormat("rec-engine-%d").build());
    
    public PageRecommendations getHomePageRecommendations(String userId) {
        // Launch ALL recommendation engines in parallel
        CompletableFuture<List<Content>> trendingFuture = callWithFallback(
            () -> trendingEngine.getForUser(userId),
            () -> trendingEngine.getGlobal(),  // Fallback: non-personalized
            "trending", 150  // 150ms timeout
        );
        
        CompletableFuture<List<Content>> becauseYouWatchedFuture = callWithFallback(
            () -> similarityEngine.getRecommendations(userId),
            () -> Collections.emptyList(),
            "because-you-watched", 200
        );
        
        CompletableFuture<List<Content>> topPicksFuture = callWithFallback(
            () -> mlRankingEngine.getTopPicks(userId),
            () -> popularContent.getTopItems(50),
            "top-picks", 250
        );
        
        CompletableFuture<List<Content>> newReleasesFuture = callWithFallback(
            () -> catalogService.getNewReleases(userId),
            () -> catalogService.getNewReleasesGlobal(),
            "new-releases", 100
        );
        
        CompletableFuture<List<Content>> continueWatchingFuture = callWithFallback(
            () -> viewingHistory.getContinueWatching(userId),
            () -> Collections.emptyList(),
            "continue-watching", 100
        );
        
        // Wait for ALL with a global deadline of 300ms
        // Whatever finishes in time gets included; rest get fallbacks
        try {
            CompletableFuture.allOf(
                trendingFuture, becauseYouWatchedFuture, topPicksFuture,
                newReleasesFuture, continueWatchingFuture
            ).get(300, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Some engines timed out — that's OK, fallbacks will be used
            Metrics.counter("rec.page.partial_timeout").increment();
        } catch (Exception e) {
            log.warn("Recommendation aggregation error", e);
        }
        
        // Collect results (completed futures have results, timed-out have fallbacks)
        return new PageRecommendations(
            getResultOrFallback(continueWatchingFuture, "Continue Watching"),
            getResultOrFallback(trendingFuture, "Trending Now"),
            getResultOrFallback(topPicksFuture, "Top Picks for You"),
            getResultOrFallback(becauseYouWatchedFuture, "Because You Watched"),
            getResultOrFallback(newReleasesFuture, "New Releases")
        );
    }
    
    private <T> CompletableFuture<T> callWithFallback(
            Supplier<T> primary, Supplier<T> fallback, String engineName, long timeoutMs) {
        return CompletableFuture.supplyAsync(primary, recPool)
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                Metrics.counter("rec.engine.fallback", "engine", engineName).increment();
                log.debug("Engine {} fell back: {}", engineName, ex.getMessage());
                return fallback.get();
            });
    }
    
    private <T> T getResultOrFallback(CompletableFuture<T> future, String name) {
        try {
            return future.getNow(null);  // Non-blocking get
        } catch (Exception e) {
            return null;
        }
    }
}
```

---

## 7. Uber — Real-Time Surge Pricing Calculator

### 🏗️ Architecture Context

Uber calculates surge pricing by counting ride requests vs available drivers per geo-zone in real-time (sliding window). Thousands of events/sec flow in. The calculator must be lock-free for high throughput.

### 🔧 Java Implementation

```java
public class SurgePricingCalculator {
    
    // Per-zone counters using LongAdder for high-throughput concurrent updates
    private final ConcurrentHashMap<String, ZoneMetrics> zoneMetrics = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService calculator = Executors.newScheduledThreadPool(4,
        new ThreadFactoryBuilder().setNameFormat("surge-calc-%d").build());
    
    static class ZoneMetrics {
        // Using LongAdder instead of AtomicLong: 7x faster under contention
        final LongAdder requestCount = new LongAdder();
        final LongAdder supplyCount = new LongAdder();
        final AtomicReference<BigDecimal> currentMultiplier = 
            new AtomicReference<>(BigDecimal.ONE);
        
        // Sliding window: track counts per 10-second bucket
        final ConcurrentHashMap<Long, LongAdder> requestBuckets = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, LongAdder> supplyBuckets = new ConcurrentHashMap<>();
    }
    
    // ====== RECORD DEMAND (called on every ride request) ======
    public void recordRideRequest(String zoneId) {
        ZoneMetrics metrics = zoneMetrics.computeIfAbsent(zoneId, k -> new ZoneMetrics());
        metrics.requestCount.increment();
        
        long bucket = getCurrentBucket();
        metrics.requestBuckets.computeIfAbsent(bucket, k -> new LongAdder()).increment();
    }
    
    // ====== RECORD SUPPLY (called on every driver location update in zone) ======
    public void recordDriverAvailable(String zoneId) {
        ZoneMetrics metrics = zoneMetrics.computeIfAbsent(zoneId, k -> new ZoneMetrics());
        metrics.supplyCount.increment();
        
        long bucket = getCurrentBucket();
        metrics.supplyBuckets.computeIfAbsent(bucket, k -> new LongAdder()).increment();
    }
    
    // ====== CALCULATE SURGE (runs every 10 seconds per zone) ======
    @PostConstruct
    public void startSurgeCalculation() {
        calculator.scheduleAtFixedRate(() -> {
            zoneMetrics.forEach((zoneId, metrics) -> {
                BigDecimal newMultiplier = calculateSurgeMultiplier(metrics);
                
                // CAS update: multiple calc threads can't corrupt the value
                metrics.currentMultiplier.set(newMultiplier);
                
                Metrics.gauge("surge.multiplier", zoneId, newMultiplier.doubleValue());
            });
            
            // Cleanup old buckets (older than 5 minutes)
            cleanupOldBuckets();
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    private BigDecimal calculateSurgeMultiplier(ZoneMetrics metrics) {
        // Sum requests in last 5 minutes (sliding window)
        long windowStart = getCurrentBucket() - 30;  // 30 buckets × 10s = 5 minutes
        
        long totalRequests = metrics.requestBuckets.entrySet().stream()
            .filter(e -> e.getKey() >= windowStart)
            .mapToLong(e -> e.getValue().sum())
            .sum();
        
        long totalSupply = metrics.supplyBuckets.entrySet().stream()
            .filter(e -> e.getKey() >= windowStart)
            .mapToLong(e -> e.getValue().sum())
            .sum();
        
        if (totalSupply == 0) return new BigDecimal("3.0");  // Max surge if no supply
        
        double ratio = (double) totalRequests / totalSupply;
        
        // Surge tiers
        if (ratio <= 1.0) return BigDecimal.ONE;           // No surge
        if (ratio <= 1.5) return new BigDecimal("1.25");
        if (ratio <= 2.0) return new BigDecimal("1.5");
        if (ratio <= 3.0) return new BigDecimal("2.0");
        if (ratio <= 5.0) return new BigDecimal("2.5");
        return new BigDecimal("3.0");                       // Max surge cap
    }
    
    // ====== GET PRICE (called on every price estimate) ======
    public BigDecimal getSurgeMultiplier(String zoneId) {
        ZoneMetrics metrics = zoneMetrics.get(zoneId);
        if (metrics == null) return BigDecimal.ONE;
        return metrics.currentMultiplier.get();  // Volatile read — always fresh
    }
    
    private long getCurrentBucket() {
        return System.currentTimeMillis() / 10_000;  // 10-second buckets
    }
}
```

---

## 8. Amazon — Inventory Reservation with Distributed Locking

### 🏗️ Architecture Context

During flash sales (Prime Day), thousands of users try to buy the same limited-stock item simultaneously. The system must prevent overselling while maintaining high throughput. A single lock per SKU would create a bottleneck.

### 🔧 Java Implementation

```java
public class InventoryReservationService {
    
    // Striped locks: distribute contention across multiple locks
    // Instead of 1 lock for all products → 256 locks (products hash to a stripe)
    private static final int STRIPE_COUNT = 256;
    private final ReentrantLock[] locks = new ReentrantLock[STRIPE_COUNT];
    
    private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReservationInfo> reservations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reservationExpirer = 
        Executors.newSingleThreadScheduledExecutor();
    
    public InventoryReservationService() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            locks[i] = new ReentrantLock(true);  // Fair lock for flash sales
        }
        
        // Expire uncompleted reservations every 30 seconds
        reservationExpirer.scheduleAtFixedRate(this::expireStaleReservations, 
            30, 30, TimeUnit.SECONDS);
    }
    
    private ReentrantLock getLockForProduct(String productId) {
        int stripe = Math.abs(productId.hashCode() % STRIPE_COUNT);
        return locks[stripe];
    }
    
    // ====== RESERVE STOCK — With striped locking ======
    public ReservationResult reserveStock(String productId, int quantity, String userId) {
        ReentrantLock lock = getLockForProduct(productId);
        
        // Try lock with timeout — don't wait forever during flash sales
        try {
            if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                Metrics.counter("inventory.lock.timeout", "product", productId).increment();
                return ReservationResult.temporarilyUnavailable();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ReservationResult.error("Interrupted");
        }
        
        try {
            AtomicInteger available = stock.get(productId);
            if (available == null) {
                return ReservationResult.productNotFound();
            }
            
            // CAS loop to decrement stock
            int current;
            do {
                current = available.get();
                if (current < quantity) {
                    Metrics.counter("inventory.out_of_stock", "product", productId).increment();
                    return ReservationResult.outOfStock(current);
                }
            } while (!available.compareAndSet(current, current - quantity));
            
            // Create reservation with TTL
            String reservationId = UUID.randomUUID().toString();
            reservations.put(reservationId, new ReservationInfo(
                productId, quantity, userId, 
                Instant.now().plus(10, ChronoUnit.MINUTES)  // 10-minute hold
            ));
            
            Metrics.counter("inventory.reserved", "product", productId).increment();
            return ReservationResult.success(reservationId, current - quantity);
            
        } finally {
            lock.unlock();
        }
    }
    
    // ====== CONFIRM RESERVATION (after payment succeeds) ======
    public boolean confirmReservation(String reservationId) {
        ReservationInfo info = reservations.remove(reservationId);
        if (info == null) return false;
        
        // Stock already decremented — just finalize
        Metrics.counter("inventory.confirmed", "product", info.productId).increment();
        return true;
    }
    
    // ====== CANCEL / EXPIRE — Return stock ======
    public void cancelReservation(String reservationId) {
        ReservationInfo info = reservations.remove(reservationId);
        if (info != null) {
            // Return stock (atomic increment — no lock needed for additions)
            stock.get(info.productId).addAndGet(info.quantity);
            Metrics.counter("inventory.released", "product", info.productId).increment();
        }
    }
    
    private void expireStaleReservations() {
        Instant now = Instant.now();
        reservations.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt.isBefore(now)) {
                // Return stock to available pool
                ReservationInfo info = entry.getValue();
                stock.get(info.productId).addAndGet(info.quantity);
                Metrics.counter("inventory.expired", "product", info.productId).increment();
                log.info("Reservation {} expired. Returned {} units of {}",
                    entry.getKey(), info.quantity, info.productId);
                return true;
            }
            return false;
        });
    }
}
```

---

## 9. Slack — Concurrent WebSocket Connection Manager

### 🏗️ Architecture Context

Slack maintains persistent WebSocket connections with millions of users. When a message is sent to a channel, it must be pushed to ALL connected members of that channel concurrently. Users connect/disconnect constantly.

### 🔧 Java Implementation

```java
public class WebSocketConnectionManager {
    
    // User → WebSocket session (ConcurrentHashMap for O(1) lookup)
    private final ConcurrentHashMap<String, WebSocketSession> connections = 
        new ConcurrentHashMap<>(100_000);
    
    // Channel → Set of user IDs (CopyOnWriteArraySet: reads >> writes)
    private final ConcurrentHashMap<String, Set<String>> channelMembers = 
        new ConcurrentHashMap<>();
    
    // Dedicated pool for message broadcasting (separate from connection handling)
    private final ExecutorService broadcastPool = new ThreadPoolExecutor(
        16, 32, 60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(50_000),
        new ThreadFactoryBuilder().setNameFormat("broadcast-%d").build()
    );
    
    private final LongAdder messagesDelivered = new LongAdder();
    private final LongAdder deliveryFailures = new LongAdder();
    
    // ====== USER CONNECTS ======
    public void onConnect(String userId, WebSocketSession session) {
        WebSocketSession old = connections.put(userId, session);
        if (old != null && old.isOpen()) {
            // Close old session (user reconnected from different device)
            old.close(CloseStatus.POLICY_VIOLATION);
        }
        Metrics.gauge("ws.active_connections", connections.size());
    }
    
    // ====== USER DISCONNECTS ======
    public void onDisconnect(String userId) {
        connections.remove(userId);
        Metrics.gauge("ws.active_connections", connections.size());
    }
    
    // ====== BROADCAST MESSAGE TO CHANNEL ======
    public CompletableFuture<BroadcastResult> broadcastToChannel(
            String channelId, Message message) {
        Set<String> members = channelMembers.get(channelId);
        if (members == null || members.isEmpty()) {
            return CompletableFuture.completedFuture(BroadcastResult.noMembers());
        }
        
        AtomicInteger delivered = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(members.size());
        
        String payload = serializeMessage(message);
        
        for (String userId : members) {
            broadcastPool.submit(() -> {
                try {
                    WebSocketSession session = connections.get(userId);
                    if (session != null && session.isOpen()) {
                        session.sendMessage(new TextMessage(payload));
                        delivered.incrementAndGet();
                        messagesDelivered.increment();
                    } else {
                        // User offline — queue for later delivery
                        offlineQueue.enqueue(userId, message);
                    }
                } catch (IOException e) {
                    failed.incrementAndGet();
                    deliveryFailures.increment();
                    connections.remove(userId);  // Stale connection — remove
                } finally {
                    latch.countDown();
                }
            });
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new BroadcastResult(delivered.get(), failed.get(), members.size());
        });
    }
    
    // ====== JOIN CHANNEL ======
    public void joinChannel(String userId, String channelId) {
        channelMembers.computeIfAbsent(channelId, 
            k -> ConcurrentHashMap.newKeySet()  // Thread-safe Set
        ).add(userId);
    }
    
    // ====== LEAVE CHANNEL ======
    public void leaveChannel(String userId, String channelId) {
        channelMembers.computeIfPresent(channelId, (k, members) -> {
            members.remove(userId);
            return members.isEmpty() ? null : members;  // GC empty channels
        });
    }
}
```

---

## 10. DoorDash — Order State Machine with Event-Driven Concurrency

### 🏗️ Architecture Context

A DoorDash order goes through multiple states (PLACED → CONFIRMED → PREPARING → PICKED_UP → DELIVERED). Multiple systems (restaurant, driver, customer) can trigger state transitions concurrently. Only valid transitions must be allowed.

### 🔧 Java Implementation

```java
public class OrderStateMachine {
    
    private final ConcurrentHashMap<String, OrderContext> orders = new ConcurrentHashMap<>();
    
    enum OrderState {
        PLACED, CONFIRMED, PREPARING, READY_FOR_PICKUP, 
        PICKED_UP, EN_ROUTE, DELIVERED, CANCELLED
    }
    
    // Valid state transitions
    private static final Map<OrderState, Set<OrderState>> VALID_TRANSITIONS = Map.of(
        OrderState.PLACED, Set.of(OrderState.CONFIRMED, OrderState.CANCELLED),
        OrderState.CONFIRMED, Set.of(OrderState.PREPARING, OrderState.CANCELLED),
        OrderState.PREPARING, Set.of(OrderState.READY_FOR_PICKUP, OrderState.CANCELLED),
        OrderState.READY_FOR_PICKUP, Set.of(OrderState.PICKED_UP),
        OrderState.PICKED_UP, Set.of(OrderState.EN_ROUTE),
        OrderState.EN_ROUTE, Set.of(OrderState.DELIVERED),
        OrderState.DELIVERED, Set.of(),
        OrderState.CANCELLED, Set.of()
    );
    
    static class OrderContext {
        final String orderId;
        final AtomicReference<OrderState> state;
        final ReentrantLock transitionLock = new ReentrantLock();
        final List<StateTransitionEvent> history = new CopyOnWriteArrayList<>();
        volatile Instant lastUpdated;
        
        OrderContext(String orderId) {
            this.orderId = orderId;
            this.state = new AtomicReference<>(OrderState.PLACED);
            this.lastUpdated = Instant.now();
        }
    }
    
    // ====== TRANSITION STATE — Thread-safe with validation ======
    public TransitionResult transition(String orderId, OrderState targetState, 
                                        String triggeredBy) {
        OrderContext ctx = orders.get(orderId);
        if (ctx == null) return TransitionResult.orderNotFound();
        
        // Use lock for compound operation (check valid + transition + record history)
        if (!ctx.transitionLock.tryLock()) {
            // Another transition is in progress — don't wait, let caller retry
            return TransitionResult.conflict("Concurrent transition in progress");
        }
        
        try {
            OrderState currentState = ctx.state.get();
            
            // Validate transition
            Set<OrderState> validTargets = VALID_TRANSITIONS.get(currentState);
            if (!validTargets.contains(targetState)) {
                return TransitionResult.invalidTransition(currentState, targetState);
            }
            
            // Execute transition
            ctx.state.set(targetState);
            ctx.lastUpdated = Instant.now();
            ctx.history.add(new StateTransitionEvent(
                currentState, targetState, triggeredBy, Instant.now()
            ));
            
            // Fire async side effects (notifications, driver assignment, etc.)
            fireTransitionEvents(ctx, currentState, targetState);
            
            Metrics.counter("order.transition", 
                "from", currentState.name(), "to", targetState.name()).increment();
            
            return TransitionResult.success(targetState);
            
        } finally {
            ctx.transitionLock.unlock();
        }
    }
    
    private void fireTransitionEvents(OrderContext ctx, OrderState from, OrderState to) {
        CompletableFuture.runAsync(() -> {
            switch (to) {
                case CONFIRMED:
                    notificationService.notifyRestaurant(ctx.orderId);
                    break;
                case READY_FOR_PICKUP:
                    driverMatchingService.assignDriver(ctx.orderId);
                    notificationService.notifyDriver(ctx.orderId);
                    break;
                case PICKED_UP:
                    notificationService.notifyCustomer(ctx.orderId, "Your order is on the way!");
                    break;
                case DELIVERED:
                    notificationService.notifyCustomer(ctx.orderId, "Delivered!");
                    billingService.finalizeCharge(ctx.orderId);
                    break;
                case CANCELLED:
                    billingService.refund(ctx.orderId);
                    notificationService.notifyAll(ctx.orderId, "Order cancelled");
                    break;
            }
        });
    }
}
```

---

## 11. Robinhood — Stock Price Streaming with Ring Buffer

### 🏗️ Architecture Context

Robinhood streams real-time stock prices to millions of users. The market data feed produces millions of price updates/sec. A lock-free ring buffer ensures zero-copy, ultra-low-latency distribution from producer to consumers.

### 🔧 Java Implementation

```java
public class StockPriceStreamProcessor {
    
    // Ring buffer: single producer (market feed), multiple consumers (user streams)
    private final int bufferSize = 1024 * 1024;  // 1M entries (power of 2!)
    private final PriceUpdate[] ringBuffer = new PriceUpdate[bufferSize];
    private final int mask = bufferSize - 1;
    
    // Producer sequence (only market feed writer increments this)
    private final AtomicLong producerSequence = new AtomicLong(-1);
    
    // Each consumer tracks its own read position
    private final ConcurrentHashMap<String, AtomicLong> consumerSequences = 
        new ConcurrentHashMap<>();
    
    // Per-symbol latest price (for point queries)
    private final ConcurrentHashMap<String, PriceUpdate> latestPrices = new ConcurrentHashMap<>();
    
    public record PriceUpdate(String symbol, BigDecimal price, BigDecimal change, 
                               long timestamp, long sequence) {}
    
    // ====== PRODUCER: Market data feed writes prices ======
    public void publishPrice(String symbol, BigDecimal price, BigDecimal change) {
        long sequence = producerSequence.incrementAndGet();
        int index = (int)(sequence & mask);
        
        PriceUpdate update = new PriceUpdate(symbol, price, change, 
            System.nanoTime(), sequence);
        
        ringBuffer[index] = update;  // Single writer — no sync needed
        latestPrices.put(symbol, update);  // Update latest (ConcurrentHashMap)
    }
    
    // ====== CONSUMER: User stream reads prices ======
    public List<PriceUpdate> pollUpdates(String consumerId, int maxBatch) {
        AtomicLong consumerSeq = consumerSequences.computeIfAbsent(
            consumerId, k -> new AtomicLong(producerSequence.get())  // Start from current
        );
        
        List<PriceUpdate> updates = new ArrayList<>(maxBatch);
        long currentConsumerSeq = consumerSeq.get();
        long availableSeq = producerSequence.get();
        
        // Read up to maxBatch entries that producer has written
        long readUpto = Math.min(currentConsumerSeq + maxBatch, availableSeq);
        
        for (long seq = currentConsumerSeq + 1; seq <= readUpto; seq++) {
            int index = (int)(seq & mask);
            PriceUpdate update = ringBuffer[index];
            
            if (update != null && update.sequence() == seq) {
                updates.add(update);
            } else {
                // Consumer fell too far behind — producer overwrote our data
                Metrics.counter("ringbuffer.consumer.overflow", "consumer", consumerId).increment();
                break;
            }
        }
        
        // Advance consumer position
        if (!updates.isEmpty()) {
            consumerSeq.set(currentConsumerSeq + updates.size());
        }
        
        return updates;
    }
    
    // ====== GET LATEST PRICE (point query) ======
    public PriceUpdate getLatestPrice(String symbol) {
        return latestPrices.get(symbol);  // O(1) volatile read
    }
    
    // ====== WATCHLIST STREAMING ======
    public void streamWatchlist(String userId, List<String> symbols, 
                                WebSocketSession session) {
        String consumerId = "user-" + userId;
        
        // Dedicated virtual thread per user for streaming
        Thread.startVirtualThread(() -> {
            while (session.isOpen()) {
                List<PriceUpdate> updates = pollUpdates(consumerId, 100);
                
                List<PriceUpdate> relevant = updates.stream()
                    .filter(u -> symbols.contains(u.symbol()))
                    .collect(Collectors.toList());
                
                if (!relevant.isEmpty()) {
                    session.sendMessage(new TextMessage(serialize(relevant)));
                } else {
                    LockSupport.parkNanos(1_000_000);  // 1ms pause if no updates
                }
            }
            consumerSequences.remove(consumerId);  // Cleanup on disconnect
        });
    }
}
```

---

## 12. WhatsApp — Message Delivery Queue per User

### 🏗️ Architecture Context

WhatsApp delivers messages in strict order per conversation. Each user has their own delivery queue. When online, messages push instantly via WebSocket. When offline, messages queue until reconnection.

### 🔧 Java Implementation

```java
public class MessageDeliveryService {
    
    // Per-user delivery queue (ordered, bounded)
    private final ConcurrentHashMap<String, LinkedBlockingQueue<ChatMessage>> userQueues = 
        new ConcurrentHashMap<>();
    
    // Online status tracking
    private final ConcurrentHashMap<String, WebSocketSession> onlineUsers = 
        new ConcurrentHashMap<>();
    
    // Delivery confirmation tracking
    private final ConcurrentHashMap<String, CompletableFuture<DeliveryAck>> pendingAcks = 
        new ConcurrentHashMap<>();
    
    private final ExecutorService deliveryPool = Executors.newFixedThreadPool(64,
        new ThreadFactoryBuilder().setNameFormat("msg-delivery-%d").build());
    
    // ====== SEND MESSAGE ======
    public CompletableFuture<SendResult> sendMessage(ChatMessage message) {
        String recipientId = message.getRecipientId();
        
        // Check if recipient is online
        WebSocketSession session = onlineUsers.get(recipientId);
        
        if (session != null && session.isOpen()) {
            // ONLINE: Deliver immediately
            return deliverImmediately(session, message);
        } else {
            // OFFLINE: Queue for later delivery
            return queueForLater(recipientId, message);
        }
    }
    
    private CompletableFuture<SendResult> deliverImmediately(
            WebSocketSession session, ChatMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String payload = serialize(message);
                session.sendMessage(new TextMessage(payload));
                
                // Wait for delivery acknowledgment (with timeout)
                CompletableFuture<DeliveryAck> ackFuture = new CompletableFuture<>();
                pendingAcks.put(message.getMessageId(), ackFuture);
                
                DeliveryAck ack = ackFuture.get(5, TimeUnit.SECONDS);
                return SendResult.delivered(ack.getTimestamp());
                
            } catch (TimeoutException e) {
                // No ACK received — queue message for retry
                pendingAcks.remove(message.getMessageId());
                queueForLater(message.getRecipientId(), message);
                return SendResult.queued();
            } catch (Exception e) {
                return SendResult.failed(e.getMessage());
            }
        }, deliveryPool);
    }
    
    private CompletableFuture<SendResult> queueForLater(String userId, ChatMessage message) {
        LinkedBlockingQueue<ChatMessage> queue = userQueues.computeIfAbsent(
            userId, k -> new LinkedBlockingQueue<>(10_000)  // Max 10K queued messages
        );
        
        boolean offered = queue.offer(message);
        if (!offered) {
            // Queue full — oldest messages drop (or persist to DB)
            queue.poll();  // Remove oldest
            queue.offer(message);
            Metrics.counter("msg.queue.overflow", "user", userId).increment();
        }
        
        return CompletableFuture.completedFuture(SendResult.queued());
    }
    
    // ====== USER COMES ONLINE — Flush queued messages ======
    public void onUserOnline(String userId, WebSocketSession session) {
        onlineUsers.put(userId, session);
        
        // Drain queued messages in order
        CompletableFuture.runAsync(() -> {
            LinkedBlockingQueue<ChatMessage> queue = userQueues.get(userId);
            if (queue == null || queue.isEmpty()) return;
            
            List<ChatMessage> pending = new ArrayList<>();
            queue.drainTo(pending, 100);  // Deliver up to 100 at once
            
            for (ChatMessage msg : pending) {
                try {
                    session.sendMessage(new TextMessage(serialize(msg)));
                    Thread.sleep(10);  // Small delay to not overwhelm client
                } catch (Exception e) {
                    // Re-queue undelivered messages at the front
                    queue.addAll(pending.subList(pending.indexOf(msg), pending.size()));
                    break;
                }
            }
        }, deliveryPool);
    }
    
    // ====== RECEIVE ACK FROM CLIENT ======
    public void onAckReceived(String messageId) {
        CompletableFuture<DeliveryAck> future = pendingAcks.remove(messageId);
        if (future != null) {
            future.complete(new DeliveryAck(messageId, Instant.now()));
        }
    }
}
```

---

## 13. YouTube — Concurrent View Counter (LongAdder)

### 🏗️ Architecture Context

YouTube gets 500M+ video views/day. Each view increments a counter. Under viral load, a single video can get 1M+ views/minute. AtomicLong would cause extreme CAS contention. LongAdder distributes counts across CPU-local cells.

### 🔧 Java Implementation

```java
public class VideoViewCounterService {
    
    // LongAdder per video — near-zero contention even at 1M views/sec
    private final ConcurrentHashMap<String, LongAdder> liveCounters = new ConcurrentHashMap<>();
    
    // Buffered writes to database (batch every 5 seconds)
    private final ConcurrentHashMap<String, LongAdder> pendingFlush = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService flusher = Executors.newScheduledThreadPool(2);
    
    @PostConstruct
    public void init() {
        flusher.scheduleAtFixedRate(this::flushToDatabaseBatch, 5, 5, TimeUnit.SECONDS);
    }
    
    // ====== INCREMENT VIEW — Called millions of times/sec ======
    public void recordView(String videoId, String userId) {
        // Deduplicate: don't count same user twice in 30 seconds
        String dedupeKey = videoId + ":" + userId;
        Boolean exists = dedupeCache.putIfAbsent(dedupeKey, Boolean.TRUE);
        if (exists != null) {
            Metrics.counter("views.deduplicated").increment();
            return;  // Already counted
        }
        
        // Increment counter (LongAdder — lock-free, minimal contention)
        liveCounters.computeIfAbsent(videoId, k -> new LongAdder()).increment();
        pendingFlush.computeIfAbsent(videoId, k -> new LongAdder()).increment();
        
        Metrics.counter("views.counted").increment();
    }
    
    // ====== GET VIEW COUNT (approximate — eventually consistent) ======
    public long getViewCount(String videoId) {
        // DB count + live count (not yet flushed)
        long dbCount = databaseViewCount(videoId);
        LongAdder liveAdder = liveCounters.get(videoId);
        long liveCount = (liveAdder != null) ? liveAdder.sum() : 0;
        return dbCount + liveCount;
    }
    
    // ====== FLUSH TO DATABASE (batch) ======
    private void flushToDatabaseBatch() {
        // Swap pendingFlush with fresh map (atomic reference swap pattern)
        ConcurrentHashMap<String, LongAdder> toFlush = new ConcurrentHashMap<>();
        pendingFlush.forEach((videoId, adder) -> {
            long count = adder.sumThenReset();  // Get count and reset to 0
            if (count > 0) {
                toFlush.put(videoId, new LongAdder());
                toFlush.get(videoId).add(count);
            }
        });
        
        // Batch UPDATE: UPDATE videos SET view_count = view_count + ? WHERE id = ?
        List<ViewCountUpdate> updates = toFlush.entrySet().stream()
            .map(e -> new ViewCountUpdate(e.getKey(), e.getValue().sum()))
            .collect(Collectors.toList());
        
        if (!updates.isEmpty()) {
            database.batchUpdateViewCounts(updates);
            log.info("Flushed {} video view counts to DB", updates.size());
        }
    }
}
```

---

## 14. LinkedIn — Parallel Profile Enrichment Pipeline

### 🏗️ Architecture Context

When you view a LinkedIn profile, the page calls 8+ microservices in parallel: profile data, connections count, activity feed, skills endorsements, recommendations, experience verification, profile strength, and content suggestions.

### 🔧 Java Implementation

```java
public class ProfileEnrichmentService {
    
    // Structured Concurrency approach (Java 21+)
    public EnrichedProfile getFullProfile(String profileId, String viewerId) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // Fan out ALL enrichment calls in parallel
            var basicProfile = scope.fork(() -> profileService.getBasic(profileId));
            var connections = scope.fork(() -> connectionService.getCount(profileId));
            var mutualConns = scope.fork(() -> connectionService.getMutual(profileId, viewerId));
            var activity = scope.fork(() -> activityService.getRecent(profileId, 5));
            var skills = scope.fork(() -> skillService.getEndorsed(profileId));
            var recommendations = scope.fork(() -> recService.getReceived(profileId));
            var experience = scope.fork(() -> experienceService.getVerified(profileId));
            var profileStrength = scope.fork(() -> strengthService.calculate(profileId));
            
            // Wait for all with timeout
            scope.joinUntil(Instant.now().plusMillis(500));  // 500ms deadline
            scope.throwIfFailed();
            
            return EnrichedProfile.builder()
                .basic(basicProfile.get())
                .connectionCount(connections.get())
                .mutualConnections(mutualConns.get())
                .recentActivity(activity.get())
                .topSkills(skills.get())
                .recommendations(recommendations.get())
                .experience(experience.get())
                .profileStrength(profileStrength.get())
                .build();
                
        } catch (Exception e) {
            // Graceful degradation: return partial profile
            log.warn("Profile enrichment partial failure for {}", profileId, e);
            return getPartialProfile(profileId);
        }
    }
    
    // Pre-Java 21 version using CompletableFuture
    public EnrichedProfile getFullProfileLegacy(String profileId, String viewerId) {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        
        var basicFuture = supplyWithTimeout(
            () -> profileService.getBasic(profileId), pool, 200);
        var connsFuture = supplyWithTimeout(
            () -> connectionService.getCount(profileId), pool, 150);
        var activityFuture = supplyWithTimeout(
            () -> activityService.getRecent(profileId, 5), pool, 300);
        var skillsFuture = supplyWithTimeout(
            () -> skillService.getEndorsed(profileId), pool, 200);
        
        CompletableFuture.allOf(basicFuture, connsFuture, activityFuture, skillsFuture)
            .orTimeout(500, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> null)  // Allow partial results
            .join();
        
        return EnrichedProfile.builder()
            .basic(getOrDefault(basicFuture, Profile.EMPTY))
            .connectionCount(getOrDefault(connsFuture, 0))
            .recentActivity(getOrDefault(activityFuture, List.of()))
            .topSkills(getOrDefault(skillsFuture, List.of()))
            .build();
    }
    
    private <T> CompletableFuture<T> supplyWithTimeout(
            Supplier<T> supplier, ExecutorService pool, long timeoutMs) {
        return CompletableFuture.supplyAsync(supplier, pool)
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> null);
    }
}
```

---

## 15. Spotify — Concurrent Playlist Operations with ReadWriteLock

### 🏗️ Architecture Context

Spotify playlists can have thousands of songs and be edited by multiple collaborators simultaneously. Reads (playing songs, viewing playlist) vastly outnumber writes (adding/removing songs). ReadWriteLock allows concurrent reads while serializing writes.

### 🔧 Java Implementation

```java
public class CollaborativePlaylist {
    
    private final String playlistId;
    private final StampedLock lock = new StampedLock();  // Optimistic reads for playing
    private final List<Track> tracks = new ArrayList<>();
    private final AtomicLong version = new AtomicLong(0);
    
    // ====== PLAY (optimistic read — no lock contention) ======
    public Track getTrackAtIndex(int index) {
        // Optimistic read: assume no concurrent write
        long stamp = lock.tryOptimisticRead();
        Track track = (index < tracks.size()) ? tracks.get(index) : null;
        
        if (!lock.validate(stamp)) {
            // A write happened — fall back to read lock
            stamp = lock.readLock();
            try {
                track = (index < tracks.size()) ? tracks.get(index) : null;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return track;
    }
    
    // ====== SHUFFLE (read lock — concurrent with other reads) ======
    public List<Track> getShuffled() {
        long stamp = lock.readLock();
        try {
            List<Track> copy = new ArrayList<>(tracks);
            Collections.shuffle(copy);
            return copy;
        } finally {
            lock.unlockRead(stamp);
        }
    }
    
    // ====== ADD TRACK (write lock — exclusive) ======
    public PlaylistModResult addTrack(Track track, String userId) {
        long stamp = lock.writeLock();
        try {
            if (tracks.contains(track)) {
                return PlaylistModResult.duplicate();
            }
            tracks.add(track);
            long newVersion = version.incrementAndGet();
            return PlaylistModResult.success(newVersion, tracks.size());
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    // ====== REORDER (write lock — exclusive) ======
    public PlaylistModResult moveTrack(int fromIndex, int toIndex, String userId) {
        long stamp = lock.writeLock();
        try {
            if (fromIndex < 0 || fromIndex >= tracks.size() || 
                toIndex < 0 || toIndex >= tracks.size()) {
                return PlaylistModResult.invalidIndex();
            }
            Track track = tracks.remove(fromIndex);
            tracks.add(toIndex, track);
            version.incrementAndGet();
            return PlaylistModResult.success(version.get(), tracks.size());
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
```

---

## 16. Google — Concurrent Rate Limiter (Token Bucket)

### 🔧 Java Implementation

```java
public class ConcurrentTokenBucketRateLimiter {
    
    private final AtomicLong availableTokens;
    private final AtomicLong lastRefillTimestamp;
    private final long maxTokens;
    private final long refillRatePerSecond;
    
    public ConcurrentTokenBucketRateLimiter(long maxTokens, long refillRatePerSecond) {
        this.maxTokens = maxTokens;
        this.refillRatePerSecond = refillRatePerSecond;
        this.availableTokens = new AtomicLong(maxTokens);
        this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
    }
    
    public boolean tryAcquire(int tokens) {
        refill();
        
        // CAS loop to atomically decrement tokens
        long current;
        do {
            current = availableTokens.get();
            if (current < tokens) {
                Metrics.counter("ratelimit.rejected").increment();
                return false;  // Not enough tokens
            }
        } while (!availableTokens.compareAndSet(current, current - tokens));
        
        Metrics.counter("ratelimit.allowed").increment();
        return true;
    }
    
    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillTimestamp.get();
        long elapsedNanos = now - last;
        
        if (elapsedNanos <= 0) return;
        
        long tokensToAdd = (elapsedNanos * refillRatePerSecond) / 1_000_000_000L;
        if (tokensToAdd <= 0) return;
        
        // CAS to update timestamp (only one thread refills)
        if (lastRefillTimestamp.compareAndSet(last, now)) {
            long current = availableTokens.get();
            long newTokens = Math.min(maxTokens, current + tokensToAdd);
            availableTokens.set(newTokens);
        }
    }
}
```

---

## 17. Netflix — Bulkhead Pattern for Service Isolation

### 🔧 Java Implementation

```java
public class BulkheadManager {
    
    private final ConcurrentHashMap<String, Semaphore> serviceBulkheads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> rejectionCounters = new ConcurrentHashMap<>();
    
    public BulkheadManager() {
        // Each downstream service gets its own concurrency limit
        serviceBulkheads.put("payment-service", new Semaphore(10));
        serviceBulkheads.put("user-service", new Semaphore(20));
        serviceBulkheads.put("inventory-service", new Semaphore(15));
        serviceBulkheads.put("recommendation-service", new Semaphore(25));
        serviceBulkheads.put("notification-service", new Semaphore(30));
    }
    
    public <T> T executeWithBulkhead(String serviceName, Callable<T> action, 
                                      Supplier<T> fallback) throws Exception {
        Semaphore bulkhead = serviceBulkheads.get(serviceName);
        if (bulkhead == null) throw new IllegalArgumentException("Unknown service: " + serviceName);
        
        // Try to acquire a permit (non-blocking with timeout)
        if (!bulkhead.tryAcquire(100, TimeUnit.MILLISECONDS)) {
            // Bulkhead full — this service is at capacity
            rejectionCounters.computeIfAbsent(serviceName, k -> new LongAdder()).increment();
            Metrics.counter("bulkhead.rejected", "service", serviceName).increment();
            log.warn("Bulkhead full for {}. Available: {}", serviceName, bulkhead.availablePermits());
            return fallback.get();  // Return degraded response
        }
        
        try {
            return action.call();
        } finally {
            bulkhead.release();
        }
    }
}
```

---

## 18. Amazon — Concurrent Search Aggregator (Fan-Out/Fan-In)

### 🔧 Java Implementation

```java
public class ProductSearchAggregator {
    
    private final ExecutorService searchPool = Executors.newFixedThreadPool(20);
    
    public SearchResults search(String query, SearchFilters filters) {
        // Fan-out: search multiple indices in parallel
        CompletableFuture<List<Product>> productsFuture = 
            CompletableFuture.supplyAsync(() -> productIndex.search(query, filters), searchPool);
        CompletableFuture<List<Product>> sponsoredFuture = 
            CompletableFuture.supplyAsync(() -> adService.getSponsoredResults(query), searchPool);
        CompletableFuture<Map<String, Integer>> facetsFuture = 
            CompletableFuture.supplyAsync(() -> facetService.computeFacets(query, filters), searchPool);
        CompletableFuture<SpellCorrection> spellFuture = 
            CompletableFuture.supplyAsync(() -> spellChecker.check(query), searchPool);
        CompletableFuture<List<String>> suggestionsFuture = 
            CompletableFuture.supplyAsync(() -> suggestService.getSuggestions(query), searchPool);
        
        // Fan-in: wait max 200ms, take whatever is ready
        try {
            CompletableFuture.allOf(productsFuture, sponsoredFuture, facetsFuture,
                spellFuture, suggestionsFuture)
                .get(200, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Metrics.counter("search.partial_timeout").increment();
        } catch (Exception e) {
            log.warn("Search aggregation error", e);
        }
        
        return SearchResults.builder()
            .products(getOrDefault(productsFuture, List.of()))
            .sponsored(getOrDefault(sponsoredFuture, List.of()))
            .facets(getOrDefault(facetsFuture, Map.of()))
            .spellCorrection(getOrDefault(spellFuture, null))
            .suggestions(getOrDefault(suggestionsFuture, List.of()))
            .build();
    }
    
    private <T> T getOrDefault(CompletableFuture<T> future, T defaultValue) {
        try { return future.getNow(defaultValue); }
        catch (Exception e) { return defaultValue; }
    }
}
```

---

## 19. Uber — Concurrent ETA Calculator with Virtual Threads

### 🔧 Java Implementation (Java 21+)

```java
public class ETACalculatorService {
    
    // Virtual threads: 1 million concurrent ETA calculations with simple blocking code
    private final ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor();
    
    public Map<String, Duration> calculateETAsForNearbyDrivers(
            Location pickup, List<Driver> nearbyDrivers) {
        
        ConcurrentHashMap<String, Duration> etas = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(nearbyDrivers.size());
        
        for (Driver driver : nearbyDrivers) {
            vtPool.submit(() -> {
                try {
                    // Each virtual thread makes a blocking HTTP call to maps service
                    // This would exhaust platform threads but virtual threads handle millions
                    RouteResponse route = mapsService.getRoute(  // Blocking call — OK!
                        driver.getLocation(), pickup
                    );
                    etas.put(driver.getId(), route.getDuration());
                } catch (Exception e) {
                    etas.put(driver.getId(), Duration.ofMinutes(99));  // Error → high ETA
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(3, TimeUnit.SECONDS);  // Wait max 3s for all ETAs
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return etas;
    }
}
```

---

## 20. Stripe — Webhook Delivery with Retry and Backoff

### 🔧 Java Implementation

```java
public class WebhookDeliveryService {
    
    private final DelayQueue<WebhookRetryTask> retryQueue = new DelayQueue<>();
    private final ExecutorService deliveryPool = Executors.newFixedThreadPool(20);
    private final AtomicInteger activeDeliveries = new AtomicInteger(0);
    
    @PostConstruct
    public void startRetryConsumer() {
        Thread.startVirtualThread(() -> {
            while (true) {
                try {
                    WebhookRetryTask task = retryQueue.take();  // Blocks until delay expires
                    deliveryPool.submit(() -> deliverWebhook(task));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    public void deliverWebhook(WebhookRetryTask task) {
        activeDeliveries.incrementAndGet();
        try {
            HttpResponse response = httpClient.send(task.buildRequest());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Metrics.counter("webhook.delivered").increment();
            } else {
                scheduleRetry(task, "HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            scheduleRetry(task, e.getMessage());
        } finally {
            activeDeliveries.decrementAndGet();
        }
    }
    
    private void scheduleRetry(WebhookRetryTask task, String reason) {
        if (task.getAttempt() >= 5) {
            // Max retries reached — send to dead letter queue
            deadLetterQueue.add(task);
            Metrics.counter("webhook.failed_permanently").increment();
            return;
        }
        
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        long delayMs = (long) Math.pow(2, task.getAttempt()) * 1000;
        task.incrementAttempt();
        task.setDelayMs(delayMs);
        retryQueue.offer(task);
        
        Metrics.counter("webhook.retry_scheduled", "attempt", 
            String.valueOf(task.getAttempt())).increment();
    }
    
    static class WebhookRetryTask implements Delayed {
        private final String url;
        private final String payload;
        private final AtomicInteger attempt = new AtomicInteger(0);
        private volatile long executeAtMs;
        
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(executeAtMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
        
        @Override
        public int compareTo(Delayed other) {
            return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), 
                               other.getDelay(TimeUnit.MILLISECONDS));
        }
        
        void setDelayMs(long delayMs) { this.executeAtMs = System.currentTimeMillis() + delayMs; }
        int getAttempt() { return attempt.get(); }
        void incrementAttempt() { attempt.incrementAndGet(); }
    }
}
```

---

## 21. Twitter — Trending Topics with Concurrent Sliding Window

### 🔧 Java Implementation

```java
public class TrendingTopicsService {
    
    // Per-hashtag counter with time-bucketed sliding window
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, LongAdder>> hashtagWindows = 
        new ConcurrentHashMap<>();
    
    // Cached top-K result (refreshed every 10 seconds)
    private volatile List<TrendingTopic> cachedTrending = List.of();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::computeTopTrending, 0, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupOldBuckets, 60, 60, TimeUnit.SECONDS);
    }
    
    // Called on every tweet (millions/sec)
    public void recordHashtag(String hashtag) {
        long bucket = System.currentTimeMillis() / 60_000;  // 1-minute buckets
        
        hashtagWindows.computeIfAbsent(hashtag, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(bucket, k -> new LongAdder())
            .increment();
    }
    
    // Compute top 10 trending (runs every 10 seconds in background)
    private void computeTopTrending() {
        long now = System.currentTimeMillis() / 60_000;
        long windowStart = now - 60;  // Last 60 minutes
        
        // Use parallel stream for computation across many hashtags
        List<TrendingTopic> trending = hashtagWindows.entrySet().parallelStream()
            .map(entry -> {
                long count = entry.getValue().entrySet().stream()
                    .filter(e -> e.getKey() >= windowStart)
                    .mapToLong(e -> e.getValue().sum())
                    .sum();
                return new TrendingTopic(entry.getKey(), count);
            })
            .sorted(Comparator.comparingLong(TrendingTopic::count).reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        cachedTrending = trending;  // Volatile write — visible to all reader threads
    }
    
    // API: Get trending (just reads volatile — no lock, no computation)
    public List<TrendingTopic> getTrending() {
        return cachedTrending;
    }
    
    private void cleanupOldBuckets() {
        long cutoff = System.currentTimeMillis() / 60_000 - 120;  // Keep 2 hours
        hashtagWindows.forEach((hashtag, buckets) -> {
            buckets.keySet().removeIf(bucket -> bucket < cutoff);
            if (buckets.isEmpty()) hashtagWindows.remove(hashtag);
        });
    }
}
```

---

## 22. Netflix — Session Management with Concurrent Cache

### 🔧 Java Implementation

```java
public class SessionManager {
    
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService expiryChecker = Executors.newSingleThreadScheduledExecutor();
    
    @PostConstruct
    public void init() {
        expiryChecker.scheduleAtFixedRate(this::evictExpiredSessions, 30, 30, TimeUnit.SECONDS);
    }
    
    public UserSession createSession(String userId, DeviceInfo device) {
        String sessionId = generateSecureId();
        UserSession session = new UserSession(sessionId, userId, device, 
            Instant.now(), Instant.now().plus(Duration.ofHours(24)));
        
        sessions.put(sessionId, session);
        
        // Enforce max concurrent sessions per user (e.g., 4 screens)
        enforceMaxSessions(userId, 4);
        
        return session;
    }
    
    public UserSession validateSession(String sessionId) {
        return sessions.computeIfPresent(sessionId, (id, session) -> {
            if (session.isExpired()) {
                return null;  // Remove expired — returning null removes from map
            }
            session.touch();  // Update last-accessed timestamp
            return session;
        });
    }
    
    private void enforceMaxSessions(String userId, int maxSessions) {
        List<UserSession> userSessions = sessions.values().stream()
            .filter(s -> s.getUserId().equals(userId))
            .sorted(Comparator.comparing(UserSession::getLastAccessed))
            .collect(Collectors.toList());
        
        // Remove oldest sessions if over limit
        while (userSessions.size() > maxSessions) {
            UserSession oldest = userSessions.remove(0);
            sessions.remove(oldest.getSessionId());
            Metrics.counter("session.evicted.max_limit").increment();
        }
    }
    
    private void evictExpiredSessions() {
        int evicted = 0;
        for (var entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                sessions.remove(entry.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("Evicted {} expired sessions. Active: {}", evicted, sessions.size());
        }
    }
}
```

---

## 23. DoorDash — Concurrent Delivery Assignment with Fair Locking

### 🔧 Java Implementation

```java
public class DeliveryAssignmentService {
    
    // Fair lock ensures FIFO ordering — first driver to respond gets the order
    private final ReentrantLock assignmentLock = new ReentrantLock(true);  // FAIR
    private final Condition orderAvailable = assignmentLock.newCondition();
    
    private final PriorityBlockingQueue<DeliveryOrder> availableOrders = 
        new PriorityBlockingQueue<>(100, 
            Comparator.comparingLong(DeliveryOrder::getCreatedAtMs));  // Oldest first
    
    // Driver waits for next available order (called when driver is idle)
    public DeliveryOrder waitForOrder(String driverId, Duration maxWait) 
            throws InterruptedException {
        assignmentLock.lock();
        try {
            long deadlineNanos = System.nanoTime() + maxWait.toNanos();
            
            while (availableOrders.isEmpty()) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) return null;  // Timeout — no orders available
                orderAvailable.awaitNanos(remaining);
            }
            
            DeliveryOrder order = availableOrders.poll();
            if (order != null) {
                order.assignTo(driverId);
                Metrics.counter("delivery.assigned").increment();
            }
            return order;
            
        } finally {
            assignmentLock.unlock();
        }
    }
    
    // New order arrives (called by order service)
    public void publishOrder(DeliveryOrder order) {
        assignmentLock.lock();
        try {
            availableOrders.offer(order);
            orderAvailable.signal();  // Wake up ONE waiting driver (fair — longest waiting)
        } finally {
            assignmentLock.unlock();
        }
    }
}
```

---

## 24. Amazon — Flash Sale with AtomicInteger Stock Counter

### 🔧 Java Implementation

```java
public class FlashSaleService {
    
    // Atomic counters per product — no lock needed for decrement
    private final ConcurrentHashMap<String, AtomicInteger> flashStock = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> purchasedUsers = new ConcurrentHashMap<>();
    
    public FlashSaleResult attemptPurchase(String saleId, String productId, String userId) {
        // Step 1: Check if user already purchased (one per customer)
        Set<String> buyers = purchasedUsers.computeIfAbsent(productId, 
            k -> ConcurrentHashMap.newKeySet());
        if (!buyers.add(userId)) {
            return FlashSaleResult.alreadyPurchased();
        }
        
        // Step 2: Atomically decrement stock
        AtomicInteger stock = flashStock.get(productId);
        if (stock == null) return FlashSaleResult.saleNotFound();
        
        int remaining;
        do {
            remaining = stock.get();
            if (remaining <= 0) {
                buyers.remove(userId);  // Rollback the add above
                return FlashSaleResult.soldOut();
            }
        } while (!stock.compareAndSet(remaining, remaining - 1));
        
        // Step 3: Success! Stock decremented atomically
        Metrics.counter("flashsale.purchased", "product", productId).increment();
        return FlashSaleResult.success(remaining - 1);  // Items left
    }
    
    public void initializeSale(String productId, int quantity) {
        flashStock.put(productId, new AtomicInteger(quantity));
        purchasedUsers.put(productId, ConcurrentHashMap.newKeySet());
        log.info("Flash sale initialized: {} with {} units", productId, quantity);
    }
}
```

---

## 25. Kafka Consumer — Multi-Threaded Partition Processing

### 🔧 Java Implementation

```java
public class KafkaMultiThreadedConsumer {
    
    // One thread per partition — maintains ordering guarantee within partition
    private final ConcurrentHashMap<Integer, ExecutorService> partitionWorkers = 
        new ConcurrentHashMap<>();
    
    private final KafkaConsumer<String, String> consumer;
    private volatile boolean running = true;
    
    public void startConsuming() {
        consumer.subscribe(List.of("order-events"));
        
        // Main polling loop (single thread)
        while (running) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, String> record : records) {
                // Dispatch to partition-specific worker thread
                ExecutorService worker = partitionWorkers.computeIfAbsent(
                    record.partition(),
                    p -> Executors.newSingleThreadExecutor(
                        new ThreadFactoryBuilder()
                            .setNameFormat("kafka-partition-" + p + "-%d")
                            .build()
                    )
                );
                
                worker.submit(() -> processRecord(record));
            }
        }
    }
    
    private void processRecord(ConsumerRecord<String, String> record) {
        try {
            OrderEvent event = deserialize(record.value());
            orderProcessor.process(event);
            
            // Commit offset for this partition
            consumer.commitSync(Map.of(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)
            ));
        } catch (Exception e) {
            log.error("Failed to process record: partition={}, offset={}", 
                record.partition(), record.offset(), e);
            Metrics.counter("kafka.process.error").increment();
        }
    }
    
    public void shutdown() {
        running = false;
        partitionWorkers.values().forEach(ExecutorService::shutdown);
        consumer.close();
    }
}
```

---

## 🏆 Cheat Sheet: Java Concurrency Pattern Selection Guide

| Scenario | Pattern | Key Class | Why |
|---|---|---|---|
| High-throughput counter | Lock-free | `LongAdder` | Zero contention, cells per CPU |
| Concurrent map operations | Lock-free per bucket | `ConcurrentHashMap` | computeIfAbsent/merge atomic |
| Circuit breaker state | CAS | `AtomicReference` | Single state variable, rare updates |
| Rate limiting | CAS loop | `AtomicLong` | Token bucket with atomic decrement |
| Connection pool | Counting | `Semaphore` | Limit concurrent access to N |
| Service startup | Latch | `CountDownLatch` | Wait for N services to be ready |
| Read-heavy cache | Optimistic read | `StampedLock` | Zero read contention |
| Message delivery | Blocking queue | `LinkedBlockingQueue` | Bounded producer-consumer |
| Scheduled retry | Delay queue | `DelayQueue` | Elements available after delay |
| Parallel API calls | Async composition | `CompletableFuture.allOf()` | Fan-out/fan-in pattern |
| I/O heavy service | Virtual threads | `Executors.newVirtualThreadPerTaskExecutor()` | 1M+ concurrent I/O operations |
| Exclusive write | Fair lock | `ReentrantLock(true)` | FIFO ordering prevents starvation |
| Flash sale stock | CAS decrement | `AtomicInteger.compareAndSet()` | Lock-free inventory check |
| Per-user ordering | Single-thread per partition | `Executors.newSingleThreadExecutor()` | Order guarantee |
| Event broadcasting | Copy-on-write | `CopyOnWriteArrayList` | Reads >> writes (listeners) |

---

## 🎯 Interview Summary: "When Would You Use Which Pattern?"

```
QUICK DECISION MATRIX:

  "How do you handle 1M+ counter increments/sec?"
  → LongAdder (7x faster than AtomicLong under contention)

  "How do you prevent double-processing of payments?"
  → ConcurrentHashMap.putIfAbsent() + CountDownLatch for concurrent retries

  "How do you call 5 services in parallel with timeout?"
  → CompletableFuture.allOf().orTimeout(200ms) with fallbacks

  "How do you prevent one slow service from killing your system?"
  → Semaphore per service (bulkhead) + CircuitBreaker (AtomicReference state)

  "How do you handle 50M fan-outs for a celebrity tweet?"
  → ThreadPoolExecutor with batched work + CountDownLatch + Kafka for overflow

  "How do you handle millions of I/O-bound requests?"
  → Virtual Threads (Java 21+) — simple blocking code at massive scale

  "How do you prevent overselling during flash sales?"
  → AtomicInteger.compareAndSet() in CAS loop (lock-free decrement)

  "How do you maintain message ordering?"
  → Single thread per partition (Kafka pattern) — never parallelize within a key

  "How do you handle concurrent cache reads/writes?"
  → StampedLock optimistic read (99% of requests) + write lock (rare updates)
```

---

*This document provides 25 complete real-world Java concurrency examples. See Topic 68 for the theoretical foundations, performance characteristics, and decision frameworks behind these patterns.*
