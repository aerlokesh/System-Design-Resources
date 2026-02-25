# Metrics & Observability — Complete Guide for System Design Interviews

> A single reference that answers: **"What are Counter, Gauge, Histogram, Timer? When do I use which? What metrics should I mention in an interview?"**

---

## 🗺️ The Full Map of Observability

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OBSERVABILITY UNIVERSE                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  THE THREE PILLARS                                                      │
│  ─────────────────                                                      │
│  1. METRICS    — Numbers over time (Counter, Gauge, Histogram, Timer)  │
│  2. LOGS       — Structured event records (text, JSON)                 │
│  3. TRACES     — Request path through distributed services             │
│                                                                         │
│  METRIC TYPES                     TOOLS                                │
│  ────────────                     ─────                                │
│  Counter                          Prometheus / Grafana                 │
│  Gauge                            Datadog                              │
│  Histogram                        AWS CloudWatch                       │
│  Timer                            New Relic                            │
│  Summary                          InfluxDB / Telegraf                  │
│  Distribution                     OpenTelemetry (standard)             │
│                                                                         │
│  ALERTING                         LOG AGGREGATION                      │
│  ────────                         ───────────────                      │
│  PagerDuty                        ELK Stack (Elasticsearch+Kibana)     │
│  OpsGenie                         Splunk                               │
│  Alert thresholds                 Loki (Grafana)                       │
│  Anomaly detection                Fluentd / Fluent Bit                 │
│                                                                         │
│  TRACING                                                                │
│  ───────                                                                │
│  Jaeger                                                                 │
│  Zipkin                                                                 │
│  AWS X-Ray                                                              │
│  OpenTelemetry (unified)                                                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 1. THE THREE PILLARS OF OBSERVABILITY

### What are they and why do you need all three?

**Metrics** tell you *what* is happening — "error rate is 5%", "latency is 200ms", "queue depth is 50K". They're cheap (just numbers), aggregated, and great for dashboards and alerts. But they don't tell you *why*.

**Logs** tell you *why* something happened — "Request abc123 failed because user_id was null at PaymentService.java:42". They're expensive (text), high-volume, and great for debugging specific incidents. But they don't show you the full path.

**Traces** tell you *where* time was spent across services — "This request took 800ms total: 200ms in API Gateway → 50ms in Auth Service → 500ms in Database → 50ms in Cache". They're essential for debugging latency in distributed systems.

```
"The server is slow"
    │
    ├─ METRICS tell you: p99 latency jumped from 100ms to 2000ms at 3:45 PM
    │
    ├─ TRACES tell you: the slow requests are spending 1800ms in the Payment DB
    │
    └─ LOGS tell you: Payment DB log shows "lock wait timeout" on table orders
    
    → Root cause: A long-running migration query is locking the orders table
```

**In a system design interview**, you mainly discuss **metrics** (what to measure, what to alert on). Logs and traces are mentioned briefly as "we'd use structured logging with ELK and distributed tracing with Jaeger."

---

## 2. METRIC TYPES — The Core Four

### 2.1 Counter

**What is a Counter?** A number that can **only go up** (or reset to zero on restart). It tracks **cumulative totals** of events that have happened. You never set a Counter to a specific value — you only `increment()` it. To get useful information, you compute the **rate** (change per second) or **delta** (change over a time window).

**Mental model:** A Counter is like an odometer in a car. It only goes up. The number itself (342,891 km) isn't that useful — what's useful is "how many km did I drive in the last hour?" (the rate).

**When to use:** Anything you want to count the total occurrences of — requests, errors, bytes transferred, messages processed, cache hits, items sold.

```java
// What a Counter looks like (Micrometer/Prometheus style)
Counter requestsTotal = Counter.builder("http.requests.total")
    .tag("method", "POST")
    .tag("endpoint", "/api/orders")
    .tag("status", "200")
    .register(registry);

// Usage: increment on each event
requestsTotal.increment();  // Now: 1
requestsTotal.increment();  // Now: 2
requestsTotal.increment();  // Now: 3
// Value only goes up. On service restart, resets to 0.

// In Prometheus, you query the RATE, not the raw value:
// rate(http_requests_total[5m])  → "requests per second over last 5 minutes"
// increase(http_requests_total[1h]) → "total requests in the last hour"
```

**Common Counter metrics in system design:**
```
http_requests_total{method, endpoint, status}    — Total HTTP requests
errors_total{type, service}                      — Total errors
messages_processed_total{topic}                  — Kafka messages consumed
cache_hits_total{cache_name}                     — Cache hits
cache_misses_total{cache_name}                   — Cache misses
bytes_transferred_total{direction}               — Network bytes in/out
db_queries_total{operation, table}               — Database queries
retries_total{service}                           — Retry attempts
rate_limit_rejected_total                        — Rate-limited requests
```

**Key rule: Counter values are meaningless alone. Always compute rate() or increase().**
```
Raw counter value: 1,482,937        ← useless by itself
rate(counter[5m]):  250 req/sec     ← useful!
increase(counter[1h]): 900,000      ← useful!
```

---

### 2.2 Gauge

**What is a Gauge?** A number that can **go up or down**. It represents a **point-in-time snapshot** of some current state — the current temperature, the current queue depth, how many connections are open right now. Unlike a Counter, a Gauge can be set to any value and fluctuates freely.

**Mental model:** A Gauge is like the speedometer in a car (not the odometer). It shows the *current* speed — 60 km/h right now, 80 km/h a minute later, 0 km/h when stopped. The value at any moment tells you something useful by itself.

**When to use:** Anything that represents a current state — queue size, memory usage, active connections, thread pool size, cache size, disk usage, number of items in a buffer.

```java
// What a Gauge looks like
Gauge queueDepth = Gauge.builder("queue.depth", queue, Queue::size)
    .register(registry);

Gauge memoryUsed = Gauge.builder("jvm.memory.used_bytes",
    () -> Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
    .register(registry);

Gauge activeConnections = Gauge.builder("db.connections.active",
    connectionPool, ConnectionPool::getActiveCount)
    .register(registry);

// Value fluctuates: 5, 12, 3, 50, 2, 0, 100, ...
// Each value is a meaningful snapshot of current state
```

**Common Gauge metrics in system design:**
```
queue_depth{queue_name}                     — Items waiting in queue
db_connections_active{pool_name}            — Open DB connections
db_connections_idle{pool_name}              — Idle DB connections
thread_pool_active{pool_name}               — Active threads
thread_pool_queue_size{pool_name}           — Queued tasks
memory_used_bytes{type}                     — JVM/system memory
disk_used_bytes{mount}                      — Disk usage
cache_size{cache_name}                      — Items in cache
cache_memory_bytes{cache_name}              — Cache memory usage
kafka_consumer_lag{topic, consumer_group}   — Messages behind
replication_lag_seconds{replica}            — DB replication delay
open_file_descriptors                       — OS file handles
pending_tasks{service}                      — Unprocessed work items
```

**Key rule: Gauge values ARE meaningful by themselves (unlike Counters).**
```
queue_depth = 50,000       ← "There are 50K items waiting" — immediately useful
connections_active = 95    ← "95 of 100 DB connections in use" — almost exhausted!
memory_used = 7.2 GB       ← "Using 7.2 GB of 8 GB" — about to OOM
kafka_lag = 2,000,000      ← "2M messages behind" — consumers can't keep up
```

---

### 2.3 Histogram

**What is a Histogram?** A metric that **buckets observations into predefined ranges** and lets you compute **percentiles** (p50, p95, p99) and **averages** server-side. When you observe a value (like a request latency of 142ms), the Histogram increments the appropriate bucket (e.g., "100-200ms bucket") and adds to a sum and count. This lets Prometheus compute percentiles without storing every individual observation.

**Mental model:** Imagine sorting students' test scores into buckets: 0-10, 10-20, 20-30, ..., 90-100. You don't store every score — just "12 students scored between 70-80." From the buckets, you can estimate the median, the 95th percentile, etc.

**When to use:** Anything where you care about the **distribution** — latencies, request sizes, response times, processing durations. The average alone is misleading ("the average response time is 100ms" hides the fact that 1% of requests take 5 seconds).

```java
// What a Histogram looks like (Prometheus-style)
// Pre-define buckets for expected latency range
Histogram requestDuration = Histogram.builder("http.request.duration_seconds")
    .buckets(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
    .tag("endpoint", "/api/orders")
    .register(registry);

// Usage: observe each request's duration
requestDuration.observe(0.142);  // 142ms → goes into 0.25 bucket (≤250ms)
requestDuration.observe(0.053);  // 53ms  → goes into 0.1 bucket (≤100ms)
requestDuration.observe(2.100);  // 2.1s  → goes into 2.5 bucket (≤2.5s)

// Prometheus stores 3 things per histogram:
// http_request_duration_seconds_bucket{le="0.01"}  = 0
// http_request_duration_seconds_bucket{le="0.025"} = 0
// http_request_duration_seconds_bucket{le="0.05"}  = 0
// http_request_duration_seconds_bucket{le="0.1"}   = 1   (the 53ms request)
// http_request_duration_seconds_bucket{le="0.25"}  = 2   (53ms + 142ms)
// http_request_duration_seconds_bucket{le="0.5"}   = 2
// http_request_duration_seconds_bucket{le="1.0"}   = 2
// http_request_duration_seconds_bucket{le="2.5"}   = 3   (all three)
// http_request_duration_seconds_bucket{le="5.0"}   = 3
// http_request_duration_seconds_bucket{le="10.0"}  = 3
// http_request_duration_seconds_bucket{le="+Inf"}  = 3
// http_request_duration_seconds_sum   = 2.295  (total of all observations)
// http_request_duration_seconds_count = 3      (number of observations)

// To compute percentiles (in PromQL):
// histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))
// → p99 latency over last 5 minutes
```

**Why percentiles matter more than averages:**
```
Scenario: 100 requests, 99 take 50ms, 1 takes 10,000ms

Average: (99×50 + 1×10000) / 100 = 149ms     ← "looks fine!"
p99:     10,000ms                              ← "1% of users wait 10 SECONDS"
p50:     50ms                                  ← "typical user is fine"

The average hides the outlier. p99 reveals it.
That's why SLAs are defined on percentiles, not averages.
```

---

### 2.4 Timer (≈ specialized Histogram)

**What is a Timer?** A convenience metric that combines a **Counter** (how many events) + a **Histogram** (distribution of durations) in one. In Micrometer (Java), `Timer` is the go-to for measuring *how long things take*. It automatically records count, total time, and distribution. In Prometheus, there's no separate Timer type — you just use a Histogram with time values.

**Mental model:** A Timer is a stopwatch that also keeps statistics. Every time you time something, it notes: "that took 142ms" and updates the count, sum, and distribution buckets automatically.

**When to use:** Any operation you want to know the duration of — HTTP request handling, database queries, cache lookups, message processing, external API calls.

```java
// What a Timer looks like (Micrometer)
Timer requestTimer = Timer.builder("http.request.duration")
    .tag("method", "GET")
    .tag("endpoint", "/api/users")
    .publishPercentiles(0.5, 0.95, 0.99)    // Client-side percentiles
    .publishPercentileHistogram()             // Server-side histogram buckets
    .register(registry);

// Usage Option 1: Wrap a block of code
requestTimer.record(() -> {
    handleRequest();  // Timer measures how long this takes
});

// Usage Option 2: Manual start/stop
Timer.Sample sample = Timer.start(registry);
handleRequest();
sample.stop(requestTimer);  // Records elapsed time

// Usage Option 3: Record a known duration
requestTimer.record(Duration.ofMillis(142));

// What gets recorded:
// http_request_duration_count = 1            (number of calls)
// http_request_duration_sum = 0.142          (total seconds)
// http_request_duration_max = 0.142          (max observed)
// http_request_duration{quantile="0.5"}  = 0.142   (p50)
// http_request_duration{quantile="0.95"} = 0.142   (p95)
// http_request_duration{quantile="0.99"} = 0.142   (p99)
```

---

### 2.5 Counter vs Gauge vs Histogram vs Timer — Comparison

| Metric Type | Direction | What it measures | Value alone meaningful? | Example |
|---|---|---|---|---|
| **Counter** | Only up ↑ | Cumulative total of events | ❌ Need `rate()` | Total requests, total errors |
| **Gauge** | Up ↑ and down ↓ | Current state snapshot | ✅ Yes | Queue depth, memory used |
| **Histogram** | Bucketed observations | Distribution of values | ❌ Need `histogram_quantile()` | Latency distribution |
| **Timer** | Counter + Histogram | Duration of operations | ✅ Gives count, sum, percentiles | Request duration, query time |

**The decision flowchart:**
```
"What metric type should I use?"
│
├─ Am I counting events (requests, errors, messages)?
│   └─ Counter ✅
│
├─ Am I measuring current state (queue size, connections, memory)?
│   └─ Gauge ✅
│
├─ Am I measuring how long something takes?
│   └─ Timer ✅ (which is Counter + Histogram internally)
│
├─ Am I measuring the distribution of values (not just time)?
│   └─ Histogram ✅ (e.g., request payload sizes, batch sizes)
│
└─ Am I unsure?
    ├─ "Can the value go down?" → Gauge
    ├─ "Is it a cumulative count?" → Counter
    └─ "Do I need percentiles?" → Histogram/Timer
```

---

## 3. TAGS / LABELS — The Secret Weapon

**What are tags (labels)?** Key-value pairs attached to a metric that let you **slice and dice** data. Without tags, `http_requests_total = 1,000,000` tells you nothing. With tags, `http_requests_total{method="POST", endpoint="/api/orders", status="500"} = 42` tells you exactly what's failing.

**Why they matter in interviews:** Tags are how you go from "something is wrong" to "POST requests to /api/orders are returning 500 errors." Every metric you mention in an interview should have relevant tags.

```java
// Without tags — almost useless
Counter requests = Counter.builder("requests").register(registry);

// With tags — powerful, queryable
Counter requests = Counter.builder("http.requests.total")
    .tag("method", "POST")           // GET, POST, PUT, DELETE
    .tag("endpoint", "/api/orders")  // Which API endpoint
    .tag("status", "200")            // HTTP status code
    .tag("service", "order-service") // Which microservice
    .register(registry);

// Now you can query:
// rate(http_requests_total{status="500"}[5m])                → error rate
// rate(http_requests_total{endpoint="/api/orders"}[5m])      → orders API traffic
// rate(http_requests_total{method="POST", status="200"}[5m]) → successful writes
```

**Common tags to include:**
```
service       — Which microservice (order-service, user-service)
endpoint      — Which API endpoint (/api/orders, /api/users/{id})
method        — HTTP method (GET, POST, PUT, DELETE)
status        — HTTP status code (200, 400, 404, 500)
region        — Deployment region (us-east-1, eu-west-1)
instance      — Specific instance/pod (pod-abc123)
cache_name    — Which cache (user-cache, product-cache)
queue_name    — Which queue (order-events, notifications)
db_operation  — Database operation (SELECT, INSERT, UPDATE)
error_type    — Error classification (timeout, validation, auth)
```

**⚠️ Warning: High cardinality kills you.**
```
BAD:  .tag("user_id", userId)       → Millions of unique values → OOM in Prometheus
BAD:  .tag("request_id", requestId) → Unbounded unique values → metrics explosion
GOOD: .tag("status", "500")         → ~5 unique values (200, 400, 404, 500, 503)
GOOD: .tag("endpoint", "/api/users")→ ~20 unique values (bounded by API count)

Rule: each tag should have LOW cardinality (< 100 unique values)
```

---

## 4. THE FOUR GOLDEN SIGNALS (Google SRE)

**What are they?** Google's Site Reliability Engineering book defines four signals that every service should monitor. If you mention these in an interview, you immediately sound like you know what you're doing.

### 4.1 Latency
**What:** How long requests take. Distinguish between successful and failed requests (a fast 500 error shouldn't improve your latency stats).

```java
// Measure with a Timer, split by status
Timer requestLatency = Timer.builder("http.request.duration")
    .tag("endpoint", "/api/orders")
    .tag("status", "200")  // Separate timer for each status!
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry);

// Alert on: p99 latency > 500ms for 5 minutes
// Dashboard: p50, p95, p99 latency over time (line chart)
```

### 4.2 Traffic
**What:** How much demand is hitting your system. Requests per second for web services, transactions per second for databases, messages per second for queues.

```java
// Measure with a Counter, compute rate()
Counter requestsTotal = Counter.builder("http.requests.total")
    .tag("endpoint", "/api/orders")
    .tag("method", "POST")
    .register(registry);

// Dashboard: rate(http_requests_total[5m]) → requests per second
// Useful for: capacity planning, spotting traffic spikes, correlating with errors
```

### 4.3 Errors
**What:** The rate of failed requests. Can be explicit (HTTP 500s) or implicit (HTTP 200 but wrong content, slow responses counted as errors by SLA).

```java
// Measure with Counters
Counter errorsTotal = Counter.builder("http.errors.total")
    .tag("endpoint", "/api/orders")
    .tag("error_type", "timeout")  // timeout, validation, internal, auth
    .register(registry);

// Error rate = errors / total requests
// rate(http_errors_total[5m]) / rate(http_requests_total[5m])
// Alert on: error rate > 1% for 5 minutes
```

### 4.4 Saturation
**What:** How "full" your system is — the utilization of constrained resources. When saturation approaches 100%, performance degrades and requests start failing.

```java
// Measure with Gauges (current state)
Gauge cpuUtilization = Gauge.builder("system.cpu.utilization", ...).register(registry);
Gauge memoryUtilization = Gauge.builder("system.memory.utilization", ...).register(registry);
Gauge dbConnectionsUsed = Gauge.builder("db.connections.utilization",
    pool, p -> (double) p.getActive() / p.getMax()).register(registry);
Gauge queueDepth = Gauge.builder("queue.depth", queue, Queue::size).register(registry);
Gauge diskUsage = Gauge.builder("disk.usage.percent", ...).register(registry);

// Alert on: CPU > 80%, memory > 90%, DB connections > 85%, disk > 85%
// Dashboard: all saturation metrics on one panel (area chart)
```

### Summary: The Four Golden Signals

```
┌─────────────────────────────────────────────────────────────────────┐
│                  THE FOUR GOLDEN SIGNALS                             │
│                                                                      │
│  LATENCY     → Timer     → "How long are requests taking?"          │
│                             p50, p95, p99 response time              │
│                                                                      │
│  TRAFFIC     → Counter   → "How much demand is on the system?"      │
│                             Requests per second                      │
│                                                                      │
│  ERRORS      → Counter   → "What fraction of requests fail?"        │
│                             Error rate (errors / total)              │
│                                                                      │
│  SATURATION  → Gauge     → "How full are our resources?"            │
│                             CPU, memory, connections, queue depth    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 5. THE RED AND USE METHODS

### 5.1 RED Method (for request-driven services)

**What:** For every service, monitor **R**ate, **E**rrors, **D**uration. It's a simplified version of the Four Golden Signals, focused on microservices.

```
Rate     = requests per second                  (Counter → rate())
Errors   = errors per second or error %         (Counter → rate())
Duration = latency distribution (p50/p95/p99)   (Timer/Histogram)
```

**When to use in interviews:** For any API service, web service, or microservice. "For the Order Service, I'd track RED metrics: request rate, error rate, and latency percentiles."

### 5.2 USE Method (for resources/infrastructure)

**What:** For every resource (CPU, memory, disk, network, connections), monitor **U**tilization, **S**aturation, **E**rrors.

```
Utilization = % of capacity being used          (Gauge, 0-100%)
Saturation  = queued work / waiting              (Gauge, queue depth)
Errors      = error count for this resource      (Counter)
```

**When to use in interviews:** For infrastructure components. "For the database connection pool, I'd track USE metrics: utilization (active/max connections), saturation (queued queries waiting for a connection), and errors (connection timeouts)."

---

## 6. WHAT METRICS TO MENTION IN SYSTEM DESIGN INTERVIEWS

### Template: For any system, mention these metrics:

```
1. API LAYER (RED)
   Counter: requests_total{endpoint, method, status}
   Counter: errors_total{endpoint, error_type}
   Timer:   request_duration{endpoint, method}  → p50, p95, p99

2. BUSINESS LOGIC
   Counter: [business_events]_total             (orders_placed, messages_sent, likes_created)
   Counter: [business_failures]_total           (payment_failed, validation_rejected)
   Gauge:   [business_state]                    (active_users, pending_orders)

3. DATABASE
   Timer:   db_query_duration{operation, table} → p99
   Gauge:   db_connections_active{pool}
   Gauge:   db_connections_idle{pool}
   Counter: db_errors_total{error_type}
   Gauge:   replication_lag_seconds              (if read replicas)

4. CACHE
   Counter: cache_hits_total{cache}
   Counter: cache_misses_total{cache}
   Gauge:   cache_size{cache}                   (items or bytes)
   Timer:   cache_latency{operation}            (get, set, delete)
   Derived: hit_rate = hits / (hits + misses)   → target > 95%

5. MESSAGE QUEUE / KAFKA
   Gauge:   consumer_lag{topic, group}          (messages behind)
   Counter: messages_produced_total{topic}
   Counter: messages_consumed_total{topic}
   Timer:   message_processing_duration{topic}

6. INFRASTRUCTURE (USE)
   Gauge:   cpu_utilization_percent
   Gauge:   memory_used_bytes / memory_total
   Gauge:   disk_used_bytes / disk_total
   Gauge:   network_bytes_in / network_bytes_out
   Gauge:   open_file_descriptors

7. RATE LIMITING
   Counter: rate_limit_allowed_total
   Counter: rate_limit_rejected_total
   Gauge:   rate_limit_current_usage{user/ip}
```

---

## 7. REAL EXAMPLES: Metrics for Common System Designs

### Example 1: URL Shortener
```java
// Core business
Counter urlsCreated      = counter("urls.created.total");
Counter urlsRedirected   = counter("urls.redirected.total", "status"); // 301, 404
Timer   redirectLatency  = timer("urls.redirect.duration");

// Cache
Counter cacheHit         = counter("urls.cache.hit");
Counter cacheMiss        = counter("urls.cache.miss");
// Hit rate = hit / (hit + miss) → target > 99% (most URLs are hot)

// Database
Timer   dbWriteLatency   = timer("urls.db.write.duration");
Timer   dbReadLatency    = timer("urls.db.read.duration");
Gauge   dbConnections    = gauge("urls.db.connections.active");

// Alert: redirect latency p99 > 50ms, error rate > 0.1%, cache hit rate < 95%
```

### Example 2: Chat / Messaging System
```java
// Core business
Counter messagesSent     = counter("chat.messages.sent.total");
Counter messagesDelivered= counter("chat.messages.delivered.total");
Counter messagesFailed   = counter("chat.messages.failed.total");
Timer   sendLatency      = timer("chat.message.send.duration");
Timer   deliveryLatency  = timer("chat.message.delivery.duration"); // end-to-end

// WebSocket
Gauge   activeConnections = gauge("chat.websocket.connections.active");
Counter connectionErrors  = counter("chat.websocket.errors.total");

// Queue
Gauge   queueDepth       = gauge("chat.queue.pending_messages");
Gauge   kafkaLag         = gauge("chat.kafka.consumer_lag");

// Presence
Gauge   onlineUsers      = gauge("chat.users.online");

// Alert: delivery latency p99 > 500ms, queue depth > 100K, 
//        failed messages > 0.1%, websocket errors spike
```

### Example 3: Like/Unlike System
```java
// Write path
Counter likesTotal       = counter("likes.total", "action");       // like, unlike
Counter idempotentSkips  = counter("likes.idempotent_skip");       // Already liked → skip
Timer   writeLatency     = timer("likes.write.duration");          // p99 < 50ms

// Read path
Timer   statusCheckLatency  = timer("likes.status_check.duration");  // "Did I like this?"
Timer   countReadLatency    = timer("likes.count.read.duration");    // "How many likes?"

// Cache
Counter cacheHitStatus      = counter("likes.cache.hit", "type");  // status, count
Counter cacheMissStatus     = counter("likes.cache.miss", "type");
// Hit rate should be > 95% for like counts (read-heavy)

// Counter aggregation (if using write-behind pattern)
Gauge   pendingDeltaSize = gauge("likes.counter.pending_deltas");  // Deltas not yet flushed
Counter counterFlushes   = counter("likes.counter.flushes");       // How often we flush to DB
Counter countReconciled  = counter("likes.counter.reconciled");    // Drift corrections

// Rate limiting
Counter rateLimitRejected = counter("likes.ratelimit.rejected");

// Infrastructure
Gauge   redisMemoryUsed  = gauge("likes.redis.memory_bytes");
Gauge   dbConnectionPool = gauge("likes.db.connections.active");
Gauge   kafkaLag         = gauge("likes.kafka.consumer_lag");

// KEY ALERTS:
// - writeLatency p99 > 100ms → "writes are slow, check DB/Redis"
// - pendingDeltaSize > 10,000 → "flush pipeline is backed up"
// - cacheHitRate < 80% → "cache is cold or undersized"
// - kafkaLag > 50,000 → "consumers can't keep up"
// - rateLimitRejected rate > 100/sec → "possible bot attack"
```

### Example 4: News Feed / Timeline
```java
// Feed generation
Counter feedsGenerated   = counter("feed.generated.total");
Timer   feedGenLatency   = timer("feed.generation.duration");       // p99 < 200ms
Gauge   feedFanoutQueue  = gauge("feed.fanout.queue_depth");

// Feed reads
Counter feedViews        = counter("feed.views.total");
Timer   feedLoadLatency  = timer("feed.load.duration");             // p99 < 100ms
Counter feedLoadErrors   = counter("feed.load.errors.total");

// Cache
Counter feedCacheHit     = counter("feed.cache.hit");
Counter feedCacheMiss    = counter("feed.cache.miss");

// Content ranking
Timer   rankingLatency   = timer("feed.ranking.duration");

// Alert: feedGenLatency p99 > 500ms, fanoutQueue > 1M, feedLoadLatency p99 > 200ms
```

### Example 5: Rate Limiter
```java
Counter requestsAllowed  = counter("ratelimit.allowed.total", "tier");   // free, premium
Counter requestsRejected = counter("ratelimit.rejected.total", "tier");
Gauge   currentBucketFill= gauge("ratelimit.bucket.fill_ratio", "user_tier");
Timer   checkLatency     = timer("ratelimit.check.duration");            // p99 < 1ms

// Redis (if using token bucket in Redis)
Gauge   redisOpsPerSec   = gauge("ratelimit.redis.ops_per_sec");
Timer   redisLatency     = timer("ratelimit.redis.latency");

// Alert: rejected rate > 10% of total (misconfigured limits?),
//        checkLatency p99 > 5ms (Redis too slow), redis errors spike
```

---

## 8. ALERTING — What to Alert On

### Alert Design Principles

**Alert on symptoms, not causes.** Users care about "the API is slow" (symptom), not "CPU is at 80%" (cause). CPU at 80% might be perfectly fine. API latency at 5 seconds is always bad.

```
GOOD ALERTS (symptoms):
  ✅ "p99 latency > 500ms for 5 minutes"           → Users are affected
  ✅ "Error rate > 1% for 5 minutes"                → Users see errors
  ✅ "Zero requests in 5 minutes"                   → Service might be down

BAD ALERTS (causes, noisy):
  ❌ "CPU > 70%"                                    → Might be normal under load
  ❌ "Single request failed"                         → Happens all the time, noise
  ❌ "Memory > 60%"                                  → GC will handle it
```

### Alert Severity Levels

```
P0 (CRITICAL) — Page immediately, wake people up:
  • Service completely down (zero traffic)
  • Error rate > 10% for 5 minutes
  • Data loss detected
  • All replicas unhealthy

P1 (HIGH) — Page during business hours:
  • p99 latency > 2x normal for 10 minutes
  • Error rate > 1% for 10 minutes
  • Database replication lag > 30 seconds
  • Queue depth growing unbounded

P2 (MEDIUM) — Ticket, fix within 24 hours:
  • Cache hit rate < 90%
  • Disk usage > 80%
  • Certificate expiring in 7 days
  • Degraded redundancy (1 of 3 replicas down)

P3 (LOW) — Informational:
  • Traffic anomaly (2x normal, might be marketing campaign)
  • Slow query detected (> 1 second)
  • Dependency latency increased
```

### SLIs, SLOs, and SLAs

**SLI (Service Level Indicator):** A metric that measures service quality. Example: "p99 latency of the /api/orders endpoint."

**SLO (Service Level Objective):** A target value for an SLI. Example: "p99 latency < 200ms for 99.9% of the time."

**SLA (Service Level Agreement):** A contract with customers. Example: "99.9% availability or we refund credits."

```
SLI → What you measure        "p99 latency"
SLO → What you target         "p99 latency < 200ms, 99.9% of the time"
SLA → What you promise         "99.9% uptime, or financial penalties"

SLO is stricter than SLA (you catch issues before customers notice)
Error budget = 100% - SLO = 0.1% → you're allowed to fail 0.1% of the time
  In a month: 43,200 minutes × 0.001 = 43.2 minutes of allowed downtime
```

---

## 9. INTERVIEW CHEAT SHEET — What to Say

### When the interviewer asks "How would you monitor this system?"

**Step 1:** Mention the Four Golden Signals
> "I'd start with the four golden signals for every service: latency (p50/p95/p99), traffic (requests per second), errors (error rate), and saturation (CPU, memory, connections)."

**Step 2:** Add system-specific business metrics
> "For this Like system specifically, I'd track likes/second, cache hit rate for like counts, and pending delta flushes for the write-behind counter."

**Step 3:** Mention alerting strategy
> "I'd alert on symptoms: p99 latency > SLO, error rate > 1%, and queue depth growing unbounded. Not on causes like CPU usage."

**Step 4:** Briefly mention logs and traces
> "For debugging, we'd use structured JSON logging with correlation IDs, aggregated in ELK/Splunk. For distributed tracing, Jaeger or OpenTelemetry to trace request flow across services."

### Quick phrases for interviews:

```
"I'd use a Counter for [requests/errors/events] because it only goes up"
"I'd use a Gauge for [queue depth/connections/memory] because it fluctuates"
"I'd use a Timer for [request latency/query duration] to get p50/p95/p99"
"I'd tag every metric with [endpoint/status/service] so we can slice the data"
"I'd alert on p99 latency > X and error rate > Y — symptoms, not causes"
"Cache hit rate = hits/(hits+misses) — target > 95%"
"I'd monitor consumer lag on Kafka — if it grows, consumers can't keep up"
"I'd use the RED method for services and USE method for infrastructure"
```

---

## 10. COMMON INTERVIEW QUESTIONS — RAPID FIRE

**Q: Counter vs Gauge?**
→ Counter only goes up (total requests). Gauge goes up and down (queue depth). Counter needs `rate()` to be useful. Gauge is useful by itself.

**Q: Why percentiles over averages?**
→ Average hides outliers. 99 requests at 50ms + 1 at 10s = 149ms average (looks fine!). But p99 = 10s (1% of users suffer). SLAs use percentiles.

**Q: What is a Histogram?**
→ Buckets observations into ranges (0-100ms, 100-200ms, etc.) to compute percentiles server-side. Prometheus computes p99 from bucket counts without storing every value.

**Q: What's the difference between Timer and Histogram?**
→ Timer = Histogram + Counter specialized for durations. In Micrometer, use `Timer` for "how long did this take." In Prometheus, they're the same thing (just a Histogram with time values).

**Q: What is high cardinality and why is it bad?**
→ Tags with too many unique values (like `user_id`) create millions of time series in Prometheus → memory explosion → OOM. Keep tags to < 100 unique values.

**Q: What are the Four Golden Signals?**
→ Latency (Timer, p99), Traffic (Counter, rate), Errors (Counter, error rate), Saturation (Gauge, resource utilization). From Google SRE book.

**Q: What's the RED method?**
→ Rate, Errors, Duration — for every microservice. Simplified Golden Signals for request-driven services.

**Q: What's the USE method?**
→ Utilization, Saturation, Errors — for every resource (CPU, memory, disk, connections). For infrastructure monitoring.

**Q: SLI vs SLO vs SLA?**
→ SLI = the metric (p99 latency). SLO = the target (p99 < 200ms, 99.9%). SLA = the contract with customers (99.9% uptime or refund). SLO is always stricter than SLA.

**Q: How do you calculate cache hit rate?**
→ `hits / (hits + misses)`. Both are Counters. Use `rate()` over a time window: `rate(cache_hits[5m]) / (rate(cache_hits[5m]) + rate(cache_misses[5m]))`. Target: > 95%.

**Q: What should you alert on?**
→ Symptoms, not causes. "p99 latency > 500ms for 5 min" (symptom) is better than "CPU > 80%" (cause). Alerts should be actionable — if you can't do anything about it, don't alert.

**Q: How do you monitor Kafka consumers?**
→ Consumer lag (Gauge): messages produced - messages consumed = how far behind. If lag grows over time → consumers can't keep up → need more consumer instances or faster processing.

---

*Created: February 25, 2026*
*Companion to: `LLD/Concurrency/java-concurrency-all-constructs-compared.md` (similar style for concurrency)*
