# 🎯 Topic 45: Observability & Monitoring

> **System Design Interview — Deep Dive**
> A comprehensive guide covering the three pillars of observability (metrics, logs, traces), monitoring architecture, alerting strategies, SLIs/SLOs/SLAs, distributed tracing, log aggregation, dashboards, OpenTelemetry, AWS CloudWatch patterns, anomaly detection, on-call practices, and how to articulate observability decisions in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Three Pillars of Observability](#three-pillars-of-observability)
3. [Metrics — What to Measure](#metrics--what-to-measure)
4. [Metric Types Deep Dive](#metric-types-deep-dive)
5. [Logging — Structured and Centralized](#logging--structured-and-centralized)
6. [Log Levels, Sampling & Cost Control](#log-levels-sampling--cost-control)
7. [Distributed Tracing Deep Dive](#distributed-tracing-deep-dive)
8. [OpenTelemetry — The Standard](#opentelemetry--the-standard)
9. [SLIs, SLOs, SLAs & Error Budgets](#slis-slos-slas--error-budgets)
10. [Alerting Strategies](#alerting-strategies)
11. [Anomaly Detection & Smart Alerting](#anomaly-detection--smart-alerting)
12. [Monitoring Architecture](#monitoring-architecture)
13. [Dashboard Design — What Goes Where](#dashboard-design--what-goes-where)
14. [AWS Observability Stack (CloudWatch, X-Ray)](#aws-observability-stack)
15. [Key Metrics for Every System Design](#key-metrics-for-every-system-design)
16. [On-Call, Runbooks & Incident Response](#on-call-runbooks--incident-response)
17. [Real-World Incident Walkthroughs](#real-world-incident-walkthroughs)
18. [Real-World Tools and Stack Comparison](#real-world-tools-and-stack-comparison)
19. [Applying Observability to System Design Problems](#applying-observability-to-system-design-problems)
20. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
21. [Common Interview Mistakes](#common-interview-mistakes)
22. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Observability** is the ability to understand a system's internal state from its external outputs. In a distributed system with 100+ microservices, when a user reports "the page is slow," you need to quickly determine: Which service is slow? Why? Is it a one-time issue or a systemic problem?

```
Without observability:
  User: "The page is slow"
  Engineer: SSH into each of 50 servers, grep logs, guess which service is slow
  Time to diagnosis: 30-60 minutes (if lucky)

With observability:
  User: "The page is slow"
  Engineer: Dashboard shows P99 latency spike in order-service at 14:30
  Trace shows: order-service → payment-service call taking 5 seconds (normally 50ms)
  Payment-service metrics: Connection pool exhausted (max_connections hit)
  Root cause: Payment provider had a 5-second slowdown → connection pool drained
  Time to diagnosis: 2-5 minutes
```

### Monitoring vs Observability

```
Monitoring: "Is the system healthy?" → Predefined dashboards, known failure modes
Observability: "WHY is the system unhealthy?" → Explore unknown failures, ask new questions

Monitoring tells you WHAT is wrong.
Observability tells you WHY it's wrong.

You need BOTH:
  Monitoring → detect problems (alerts, dashboards)
  Observability → diagnose problems (traces, structured logs, ad-hoc queries)
```

---

## Three Pillars of Observability

### Metrics (Quantitative — WHAT happened)

```
Numbers aggregated over time:
  request_count: 50,000 requests/sec
  error_rate: 0.1% (50 errors/sec)
  latency_p99: 200ms
  cpu_utilization: 65%
  
Storage: Time-series database (Prometheus, InfluxDB, CloudWatch)
Query: "What was the P99 latency for order-service in the last hour?"
Cardinality: Low (aggregated values, not individual events)
Retention: Months to years (cheap to store)
Best for: Dashboards, alerting, trend analysis
Cost: ~$0.30/custom metric/month (CloudWatch)
```

### Logs (Qualitative — WHY it happened)

```
Detailed records of individual events:
  {"timestamp": "2025-03-15T14:30:05Z", "level": "ERROR", 
   "service": "payment-service", "message": "Connection timeout to Stripe API",
   "request_id": "req_abc123", "trace_id": "trace_xyz789",
   "user_id": "user_456", "order_id": "order_123",
   "error_type": "timeout", "duration_ms": 5000}

Storage: Log aggregation system (ELK Stack, CloudWatch Logs, Datadog Logs)
Query: "Show all errors where error_type=timeout AND service=order-service"
Cardinality: High (one log per event, millions per hour)
Retention: Days to weeks (expensive at scale)
Best for: Debugging specific issues, audit trails
Cost: ~$0.50/GB ingested (CloudWatch Logs)
```

### Traces (Contextual — HOW it flowed)

```
End-to-end path of a request through multiple services:

  Trace ID: trace_xyz789
  ├── API Gateway (2ms)
  ├── User Service: getUser (15ms)
  │   └── Redis Cache: GET user:456 (1ms) ✅ HIT
  ├── Order Service: createOrder (180ms)
  │   ├── Inventory Service: checkStock (20ms)
  │   │   └── DynamoDB: GetItem (5ms)
  │   ├── Payment Service: charge (150ms)  ← SLOW! (normally 50ms)
  │   │   └── Stripe API: createCharge (145ms)  ← ROOT CAUSE
  │   └── DB: insertOrder (10ms)
  │       └── Aurora PostgreSQL: INSERT (8ms)
  └── Notification Service: sendConfirmation (5ms)
      └── SQS: SendMessage (3ms)
  Total: 202ms

Storage: Distributed tracing system (Jaeger, Zipkin, AWS X-Ray, Datadog APM)
Query: "Show me the trace for request req_abc123"
Best for: Finding which service in a chain is causing slowness
Cost: Sampling 1-5% of requests keeps costs manageable
```

### How the Three Pillars Work Together

```
ALERT: "P99 latency > 500ms for 5 minutes" (from METRICS)
  ↓
INVESTIGATE: Dashboard shows order-service P99 spike at 14:30 (METRICS)
  ↓
DIG DEEPER: Pull a trace for a slow request (TRACES)
  Trace shows: payment-service call = 450ms (normally 50ms)
  ↓
ROOT CAUSE: Search payment-service logs (LOGS)
  Logs show: "Connection pool exhausted: 100/100 active connections"
  Logs show: "Stripe API response time: 4500ms (threshold: 5000ms)"
  ↓
FIX: Stripe had a slowdown → our connections waited → pool exhausted
  → Increase pool size from 100 to 200
  → Add circuit breaker: fail fast if Stripe > 2s
  → Add timeout: abort after 3s instead of waiting 5s
```

---

## Metrics — What to Measure

### The RED Method (For Request-Driven Services)

```
Rate:     Requests per second (throughput)
Errors:   Failed requests per second (error rate)
Duration: Latency distribution (P50, P95, P99)

Every API endpoint should expose RED metrics:
  order_service_requests_total{method="POST", endpoint="/orders", status="200"}
  order_service_request_duration_seconds{method="POST", endpoint="/orders", quantile="0.99"}
  order_service_errors_total{method="POST", endpoint="/orders", error_type="timeout"}
```

### The USE Method (For Resources/Infrastructure)

```
Utilization: How much of the resource is being used (CPU 65%, memory 80%)
Saturation:  How much work is queued (connection pool: 95/100 in use)
Errors:      Error events for the resource (disk I/O errors, OOM kills)

Every infrastructure component should expose USE metrics:
  cpu_utilization_percent: 65%
  memory_used_bytes / memory_total_bytes: 80%
  connection_pool_active / connection_pool_max: 95/100 (SATURATION WARNING!)
  disk_io_errors_total: 0
```

### The Four Golden Signals (Google SRE)

```
Latency:    How long requests take (P50, P95, P99)
Traffic:    How much demand (requests/sec, messages/sec)
Errors:     Rate of failed requests (HTTP 5xx, timeouts)
Saturation: How "full" the service is (CPU, memory, queue depth)

These four signals answer: "Is my service healthy right now?"
  Latency increasing + Saturation high = Service is overloaded → SCALE UP
  Errors increasing + Traffic constant = Bug deployed → ROLLBACK
  Traffic spiking + Latency normal = Service handling spike well → WATCH
  Errors increasing + Traffic spiking = Overloaded by traffic → SCALE + RATE LIMIT
```

### Percentile Latencies — Why They Matter

```
Why P99 matters more than average:

  Average latency: 50ms (looks great!)
  P99 latency: 2,000ms (1% of users wait 2 seconds!)
  
  If 10M requests/day: 100K users/day experience 2-second delays.
  Average hides the tail latency problem.

  Service A calls Service B calls Service C:
    Each has P99 = 100ms
    Combined P99 ≈ 300ms (worst case: all three hit their P99)
    
  With fan-out to 10 services in parallel:
    Each P99 = 100ms
    P99 of the SLOWEST = much higher (tail amplification)
    At 10 parallel calls, ~10% chance at least one hits P99

What to track:
  P50 (median): "Typical" user experience
  P95: "Most" users' worst case
  P99: "Almost all" users' worst case
  P99.9: Extreme tail (often hardware, GC, or network issues)
  
  Alert on P99, not average. Users don't care about average.
```

---

## Metric Types Deep Dive

### Counter (Monotonically Increasing)

```
# Counts events that only go UP
http_requests_total{method="GET", endpoint="/orders"} = 1,234,567
errors_total{type="timeout"} = 456

# Useful for: Request count, error count, bytes sent
# Rate: rate(http_requests_total[5m]) = requests per second
# ⚠️ Resets to 0 on process restart (use rate() to handle)
```

### Gauge (Goes Up and Down)

```
# Current value that can increase or decrease
cpu_utilization = 65.5
active_connections = 42
queue_depth = 150
memory_used_bytes = 1073741824

# Useful for: CPU, memory, queue depth, active connections, temperature
# Alert: When gauge exceeds threshold for sustained period
```

### Histogram (Distribution of Values)

```
# Tracks the distribution of values (latencies, sizes)
http_request_duration_seconds_bucket{le="0.05"} = 10000  # ≤ 50ms
http_request_duration_seconds_bucket{le="0.1"}  = 15000  # ≤ 100ms
http_request_duration_seconds_bucket{le="0.5"}  = 19000  # ≤ 500ms
http_request_duration_seconds_bucket{le="1.0"}  = 19500  # ≤ 1s
http_request_duration_seconds_bucket{le="+Inf"} = 20000  # total

# Useful for: Latency distributions, request/response sizes
# Calculates: P50, P90, P95, P99 from bucket boundaries
# ⚠️ Choose buckets carefully — can't compute percentiles between buckets
```

### Summary (Pre-Calculated Percentiles)

```
# Pre-computes quantiles on the client side
http_request_duration_seconds{quantile="0.5"}  = 0.045   # P50 = 45ms
http_request_duration_seconds{quantile="0.9"}  = 0.12    # P90 = 120ms
http_request_duration_seconds{quantile="0.99"} = 0.35    # P99 = 350ms

# Useful when: You need exact percentiles, not approximate
# ⚠️ Can't aggregate across instances (use histograms for aggregation)
```

### High Cardinality — The Cost Killer

```
# Cardinality = unique combinations of label values
# GOOD: {method="GET", endpoint="/orders", status="200"} → ~20 combinations
# BAD:  {user_id="user_123"} → millions of unique time series!

# Each unique label combination = one time series
# Prometheus: 10M time series = ~20GB RAM, slow queries
# CloudWatch: $0.30/metric/month × 10M = $3M/month!

# RULES:
# ✅ Labels: method, endpoint, status_code, region, service
# ❌ Labels: user_id, request_id, order_id, session_id (too many unique values)
# → Put high-cardinality data in LOGS, not metrics
```

---

## Logging — Structured and Centralized

### Structured Logging (Always Use JSON)

```
❌ Unstructured: "Error processing order 123 for user 456: timeout"
  Hard to parse, hard to search, hard to aggregate

✅ Structured JSON:
  {
    "timestamp": "2025-03-15T14:30:05.123Z",
    "level": "ERROR",
    "service": "order-service",
    "instance": "order-service-pod-7a",
    "version": "v2.3.1",
    "request_id": "req_abc123",
    "trace_id": "trace_xyz789",
    "span_id": "span_def456",
    "user_id": "user_456",
    "order_id": "order_123",
    "error_type": "timeout",
    "error_code": "PAYMENT_TIMEOUT",
    "message": "Payment service timeout after 5000ms",
    "duration_ms": 5000,
    "downstream_service": "payment-service",
    "retry_count": 2
  }

  Searchable: "Show all errors where error_type=timeout AND service=order-service"
  Aggregatable: "Count errors by error_type in the last hour"
  Correlatable: trace_id links to distributed trace, request_id links to full request lifecycle
```

### Correlation IDs — Tying Everything Together

```
# Every request gets a unique request_id at the API gateway
# Passed to EVERY downstream service call via HTTP header

X-Request-Id: req_abc123
X-Trace-Id: trace_xyz789

# Every log line includes both IDs:
# API Gateway: {"request_id": "req_abc123", "message": "Received POST /orders"}
# Order Service: {"request_id": "req_abc123", "message": "Processing order"}
# Payment Service: {"request_id": "req_abc123", "message": "Charging card"}
# Notification: {"request_id": "req_abc123", "message": "Sending email"}

# To debug: Search all logs where request_id = "req_abc123"
# → See the ENTIRE request lifecycle across ALL services in chronological order
```

### Log Aggregation Pipeline

```
Application → Fluentd/Filebeat → Kafka → Elasticsearch → Kibana

  Application: Writes structured JSON logs to stdout
  Fluentd/Filebeat: Collects logs from all pods/instances
  Kafka: Buffers log volume spikes (decouples collection from indexing)
  Elasticsearch: Indexes and stores logs (full-text search)
  Kibana: Dashboard for searching, filtering, visualizing logs

  Scale: 1M log events/sec → Kafka handles bursts → ES indexes at sustainable rate
  Retention: 7-30 days in ES (older logs → S3 for compliance)
  Cost: ES is expensive at scale → sample verbose logs, keep ERROR logs longer

AWS Stack:
  Application → CloudWatch Agent → CloudWatch Logs → Logs Insights (query)
  OR: Application → CloudWatch Logs → Subscription Filter → Kinesis Firehose → S3 + OpenSearch
```

---

## Log Levels, Sampling & Cost Control

### Log Level Guidelines

```
FATAL:  Application is crashing, immediate intervention needed
        "Out of memory, shutting down"
        Always log. Always alert.

ERROR:  Operation failed, user impact
        "Payment failed: card declined"
        Always log. Alert if rate exceeds threshold.

WARN:   Something unexpected but handled
        "Retry #2 to payment-service (timeout on attempt 1)"
        Always log. No immediate alert.

INFO:   Normal operations, significant events
        "Order created: order_123, user_456, $49.99"
        Log in production. Keep 7-30 days.

DEBUG:  Detailed diagnostic information
        "Cache MISS for key user:456, fetching from DB"
        ⚠️ Disable in production or sample (1%)

TRACE:  Extremely verbose, every function call
        "Entering function validateAddress()"
        ❌ Never in production
```

### Cost Control Strategies

```
# Problem: Logging 1M events/sec at $0.50/GB = $$$

# Strategy 1: Log level gating
# Production: INFO and above only
# Staging: DEBUG and above
# Savings: ~80% reduction (most logs are DEBUG/TRACE)

# Strategy 2: Sampling verbose logs
# Log 100% of ERROR/WARN
# Log 10% of INFO (with request_id for sampling decisions)
# Log 1% of DEBUG
# Consistent sampling: hash(request_id) % 100 < threshold

# Strategy 3: Log enrichment at aggregation layer
# Application logs: minimal (message, error, request_id)
# Fluentd enriches: adds service name, instance, version, region
# → Smaller log messages from app, metadata added centrally

# Strategy 4: Hot/warm/cold storage
# Last 24h: Elasticsearch (fast query)
# Last 7d: S3 Standard (queryable with Athena)
# Last 90d: S3 Glacier (compliance, rarely accessed)
# → 10x cost reduction for older logs

# Strategy 5: Drop known noisy logs
# Filter out health check logs (GET /healthz)
# Filter out bot/crawler requests
# Aggregate repetitive errors (1000 identical timeout logs → 1 log + count)
```

---

## Distributed Tracing Deep Dive

### How It Works — Step by Step

```
1. Request arrives at API Gateway
   → Generate trace_id = "abc123" (or use existing from header)
   → Create ROOT SPAN: {trace_id: "abc123", span_id: "span1", service: "gateway", start: T0}

2. Gateway calls Order Service
   → Pass headers: X-Trace-Id: abc123, X-Parent-Span-Id: span1
   → Order Service creates CHILD SPAN: {trace_id: "abc123", span_id: "span2", 
      parent_span_id: "span1", service: "order-service", start: T1}

3. Order Service calls Payment Service
   → Pass headers: X-Trace-Id: abc123, X-Parent-Span-Id: span2
   → Payment creates CHILD SPAN: {trace_id: "abc123", span_id: "span3",
      parent_span_id: "span2", service: "payment-service", start: T2}

4. Each service reports its span to the tracing backend
   → Backend reconstructs the TREE of spans using parent_span_id
   → Calculates: duration per service, total latency, critical path

Result: A visual tree showing EXACTLY where time was spent
```

### Trace Context Propagation

```
# HTTP Headers (W3C Trace Context standard):
traceparent: 00-abc123def456-span789-01
tracestate: vendor=value

# AWS X-Ray header:
X-Amzn-Trace-Id: Root=1-abc123;Parent=span456;Sampled=1

# gRPC: Metadata headers (same concept)
# Kafka: Record headers (trace context in message metadata)
# SQS: Message attributes (trace context passed with message)
```

### Sampling Strategies

```
# At 50K requests/sec: Can't trace ALL requests (too expensive + too much data)

# Head-based sampling (decision at trace start):
# "Sample 1% of all requests"
# Simple but: might miss the ONE slow request you need

# Tail-based sampling (decision at trace end):
# Collect all spans, decide AFTER trace completes
# "Keep all traces where duration > 1s OR status = error"
# Better but: requires buffering all spans before decision

# Adaptive sampling:
# Normal traffic: Sample 1%
# When errors spike: Sample 100% of errors
# When latency spikes: Sample all requests > P99
# AWS X-Ray: 1 req/sec + 5% of additional requests

# Priority sampling:
# Always trace: Error responses, timeouts, specific user IDs
# Sometimes trace: Normal successful requests (1-5%)
# Never trace: Health checks, readiness probes, static assets
```

### Span Attributes — What to Record

```
# Every span should include:
span.setAttribute("http.method", "POST");
span.setAttribute("http.url", "/api/v1/orders");
span.setAttribute("http.status_code", 200);
span.setAttribute("http.response_size_bytes", 1234);

# Custom business attributes (searchable!):
span.setAttribute("order.id", "ord_123");
span.setAttribute("order.total_cents", 4999);
span.setAttribute("user.tier", "premium");
span.setAttribute("cache.hit", true);
span.setAttribute("db.query_count", 3);
span.setAttribute("retry.count", 0);

# Error attributes:
span.setStatus(StatusCode.ERROR, "Payment timeout");
span.recordException(exception);
```

---

## OpenTelemetry — The Standard

### What Is OpenTelemetry?

```
OpenTelemetry (OTel) = unified standard for metrics + logs + traces
  → Vendor-neutral: Works with ANY backend (Datadog, Grafana, CloudWatch, X-Ray)
  → One SDK: Instrument once, send to any backend
  → Auto-instrumentation: HTTP clients, DB drivers, message queues — automatic!

# Before OTel: Each vendor had its own SDK
# Datadog SDK → only works with Datadog
# Jaeger SDK → only works with Jaeger
# X-Ray SDK → only works with X-Ray
# → Vendor lock-in!

# With OTel:
# OTel SDK → OTel Collector → ANY backend
# Switch from Datadog to Grafana? Change collector config. Zero code changes.
```

### OTel Architecture

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ Application  │────→│ OTel Collector   │────→│ Backend          │
│              │     │ (sidecar or      │     │ • Prometheus     │
│ OTel SDK     │     │  standalone)     │     │ • Jaeger         │
│ • Auto-instr │     │                  │     │ • CloudWatch     │
│ • Manual instr│    │ • Receive        │     │ • Datadog        │
│              │     │ • Process        │     │ • Grafana Cloud  │
│ Exports:     │     │ • Filter         │     │ • X-Ray          │
│ • Traces     │     │ • Sample         │     │                  │
│ • Metrics    │     │ • Batch          │     │                  │
│ • Logs       │     │ • Export         │     │                  │
└──────────────┘     └──────────────────┘     └──────────────────┘
```

### Auto-Instrumentation Example (Java)

```java
// Zero-code instrumentation with Java agent:
// java -javaagent:opentelemetry-javaagent.jar -jar myapp.jar

// Automatically traces:
// ✅ HTTP requests (Spring, JAX-RS, Servlet)
// ✅ Database calls (JDBC, JPA, Hibernate)
// ✅ Redis calls
// ✅ Kafka produce/consume
// ✅ gRPC calls
// ✅ AWS SDK calls (S3, DynamoDB, SQS)

// Manual instrumentation for custom logic:
Span span = tracer.spanBuilder("processOrder").startSpan();
try (Scope scope = span.makeCurrent()) {
    span.setAttribute("order.id", orderId);
    span.setAttribute("order.amount", amount);
    processOrder(orderId);
} catch (Exception e) {
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.recordException(e);
    throw e;
} finally {
    span.end();
}
```

---

## SLIs, SLOs, SLAs & Error Budgets

### Definitions

```
SLI (Service Level Indicator): A metric that measures service health
  "Percentage of requests completing in < 200ms"
  "Percentage of requests returning non-5xx status"
  "Percentage of time the service returns a valid response"

SLO (Service Level Objective): Target value for an SLI
  "99.9% of requests complete in < 200ms" (latency SLO)
  "99.95% of requests return non-5xx" (availability SLO)
  SLO is INTERNAL (engineering target)

SLA (Service Level Agreement): Contract with consequences
  "If availability drops below 99.9%, customer gets credits"
  SLA is EXTERNAL (contractual)
  SLO should be TIGHTER than SLA: If SLA = 99.9%, SLO = 99.95%
  (Buffer before you violate the contract)
```

### Error Budget — The Key Concept

```
SLO = 99.9% availability per month
Error budget = 0.1% = 43.2 minutes of downtime per month

Error budget is like a "failure allowance":
  Used 0 min → 43.2 min remaining → deploy aggressively, experiment
  Used 20 min → 23.2 min remaining → deploy carefully
  Used 40 min → 3.2 min remaining → FREEZE deployments, stabilize
  Used 43.2 min → 0 remaining → SLO VIOLATED, post-mortem required

Error budget BURN RATE alerting:
  Normal burn: 43.2 min / 30 days = 1.44 min/day (1x rate)
  Fast burn: 10x rate = consuming 10 days of budget in 1 day → PAGE NOW
  Slow burn: 2x rate = consuming 2 days of budget in 1 day → ALERT, investigate

This is MORE useful than static threshold alerts:
  "P99 > 500ms" → might be fine if it's been that way for months
  "Error budget burning at 10x" → ALWAYS actionable
```

### Common SLOs for System Design

```
API latency:     P99 < 200ms (user-facing), P99 < 50ms (internal)
Availability:    99.9% (3 nines = 43 min downtime/month)
                 99.95% (4.38 min downtime/month)
                 99.99% (4.32 min downtime/month) — very hard!
Error rate:      < 0.1% (5xx errors)
Throughput:      Handle 100K QPS without degradation
Data freshness:  Dashboard data < 30 seconds old
Data durability: 99.999999999% (11 nines — S3 standard)

Nines table:
  99%     → 7.2 hours downtime/month     (2 nines)
  99.9%   → 43.2 minutes downtime/month  (3 nines)
  99.95%  → 21.6 minutes downtime/month
  99.99%  → 4.32 minutes downtime/month  (4 nines)
  99.999% → 25.9 seconds downtime/month  (5 nines)
```

---

## Alerting Strategies

### Alert on Symptoms, Not Causes

```
❌ Bad alert: "CPU > 80%"
  Why bad: CPU at 85% might be fine (service handles traffic well)
  
✅ Good alert: "P99 latency > 500ms for 5 minutes"
  Why good: Directly measures user impact

❌ Bad alert: "Disk usage > 90%"
  Why bad: Disk might be at 90% for months with no issue
  
✅ Good alert: "Error rate > 0.5% for 3 minutes"
  Why good: Users are experiencing failures RIGHT NOW

Rule: Alerts should be ACTIONABLE
  Can an engineer do something about it RIGHT NOW? → Alert
  Is it informational or trending? → Dashboard, not alert
```

### Alert Severity Levels

```
P1 (SEV1) — Page immediately, 24/7:
  • Service down (0% availability)
  • Error rate > 5% for 3+ minutes
  • Data loss or corruption detected
  • Security breach
  Response time: < 15 minutes

P2 (SEV2) — Page during business hours:
  • P99 > 2x normal for 15+ minutes
  • Error rate > 1% for 10+ minutes
  • Degraded service (partial failures)
  • Error budget burning at 5x rate
  Response time: < 1 hour

P3 (SEV3) — Ticket, next business day:
  • Disk > 85% and growing
  • Certificate expires in < 7 days
  • Dependency deprecated, migration needed
  Response time: < 1 business day

P4 (SEV4) — Dashboard only, weekly review:
  • CPU trending up slowly over weeks
  • Minor increase in tail latency
  • Cost anomaly (spending 20% more than last week)
```

### Reducing Alert Fatigue

```
Problem: 200 alerts/day → engineers ignore them all

Solutions:
  1. Alert on SLO breach, not individual metric thresholds
     "Error budget burning at 10x rate" → actionable
     vs "CPU at 82%" → noise
     
  2. Require sustained condition (not spikes)
     "P99 > 500ms for 5 minutes" → real issue
     vs "P99 > 500ms for 1 second" → normal variance
     
  3. Composite alarms (AND/OR logic)
     "P99 > 500ms AND error rate > 0.5%" → real problem
     vs two separate noisy alerts
     
  4. Auto-resolve
     If condition clears within 5 minutes → auto-close alert
     Only page for persistent issues
     
  5. Deduplication & correlation
     "3 services in the payment path are slow" → one alert
     vs 3 separate alerts for each service
     
  6. Maintenance windows
     Suppress alerts during planned deployments/maintenance
```

---

## Anomaly Detection & Smart Alerting

### Why Static Thresholds Fail

```
# Traffic pattern: e-commerce site
# Monday 9am: 50K RPS (normal)
# Saturday midnight: 5K RPS (normal)
# Black Friday: 200K RPS (normal — annual peak)

# Static alert: "RPS > 100K" → fires on Black Friday (false alarm!)
# Static alert: "RPS < 10K" → fires every night (false alarm!)
# Static alert: "RPS > 60K" → misses Black Friday issues where 150K < expected 200K

# Solution: Anomaly detection
# Learn the PATTERN, alert on DEVIATIONS from pattern
# "RPS is 30% below expected for this day/time" → Black Friday sales are weak → investigate!
# "RPS is 5x higher than expected at 3am" → potential DDoS → investigate!
```

### Anomaly Detection Methods

```
# 1. Standard deviation bands
# Mean ± 2σ over rolling window
# Simple, works for steady traffic
# Fails for: seasonal patterns, trend changes

# 2. Seasonal decomposition
# Model: traffic = trend + seasonal + residual
# Alert when residual exceeds threshold
# Works for: daily/weekly patterns

# 3. ML-based (CloudWatch Anomaly Detection)
# Trains on 2+ weeks of historical data
# Automatically handles: daily patterns, weekly patterns, trends
# Alert when metric exits the "expected band"
# aws cloudwatch put-metric-alarm with ANOMALY_DETECTION_BAND

# 4. Adaptive thresholds
# "Alert if P99 > 2x its average over the same hour last week"
# Handles seasonality without complex ML
```

---

## Monitoring Architecture

### Pull vs Push Model

```
# Pull model (Prometheus):
# Prometheus SCRAPES metrics from service endpoints every 15s
# Service exposes: GET /metrics → returns all metrics
# Pro: Prometheus controls scrape rate, knows when targets are down
# Con: Needs network access to all targets, discovery required

# Push model (CloudWatch, StatsD):
# Service PUSHES metrics to collector
# Service calls: CloudWatch.putMetricData() or StatsD.increment()
# Pro: Works through firewalls, NATs; service controls what to send
# Con: Collector can be overwhelmed; can't detect "no data = down"

# Hybrid (OpenTelemetry):
# Service → OTel Collector (push) → Backend (pull or push)
# Best of both: service pushes to local collector, backend pulls from collector
```

### Complete Monitoring Stack

```
┌──────────────────────────────────────────────────────────────┐
│                MONITORING STACK                                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  METRICS:                                                    │
│    Collection: Prometheus (pull) or CloudWatch (push) or OTel │
│    Storage: Prometheus TSDB / CloudWatch / Cortex / Thanos    │
│    Visualization: Grafana dashboards / CloudWatch Dashboards  │
│    Alerting: Alertmanager / CloudWatch Alarms → PagerDuty     │
│                                                              │
│  LOGS:                                                       │
│    Collection: Fluentd / Fluent Bit / CloudWatch agent        │
│    Transport: Kafka (buffer) or Kinesis Firehose              │
│    Storage: Elasticsearch / OpenSearch / CloudWatch Logs / S3  │
│    Visualization: Kibana / OpenSearch Dashboards / Logs Insights│
│    Cost: Hot (ES) → Warm (S3 Standard) → Cold (Glacier)      │
│                                                              │
│  TRACES:                                                     │
│    Instrumentation: OpenTelemetry SDK (auto + manual)         │
│    Collection: OTel Collector / X-Ray daemon                  │
│    Storage: Jaeger / Tempo / AWS X-Ray / Datadog APM          │
│    Visualization: Jaeger UI / X-Ray console / Grafana Tempo   │
│    Sampling: 1-5% normal, 100% errors                         │
│                                                              │
│  UNIFIED PLATFORMS:                                          │
│    AWS: CloudWatch (metrics+logs) + X-Ray (traces)           │
│    Open Source: Grafana + Prometheus + Loki + Tempo            │
│    SaaS: Datadog / New Relic / Honeycomb                     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Dashboard Design — What Goes Where

### The Dashboard Hierarchy

```
Level 1: EXECUTIVE OVERVIEW (for leadership, status page)
┌────────────────────────────────────────────────────┐
│  Overall Availability: 99.97%  │  Active Incidents: 0 │
│  Error Budget Remaining: 72%   │  Deploys Today: 3    │
└────────────────────────────────────────────────────┘

Level 2: SERVICE OVERVIEW (for on-call, first look at 3 AM)
┌────────────────────────────────────────────────────┐
│ ROW 1: GOLDEN SIGNALS (big numbers + sparklines)    │
│ [Error Rate: 0.02%] [P99: 145ms] [RPS: 12,450] [CPU: 45%]│
│                                                    │
│ ROW 2: BUSINESS METRICS                             │
│ [Orders/min: 834] [Revenue/hr: $42,100]             │
│                                                    │
│ ROW 3: PER-ENDPOINT BREAKDOWN                       │
│ [POST /orders: 200ms] [GET /products: 45ms]         │
│                                                    │
│ ROW 4: DEPENDENCIES                                 │
│ [Payment: 50ms ✅] [DB: 5ms ✅] [Cache: 1ms ✅]    │
└────────────────────────────────────────────────────┘

Level 3: DEEP DIVE (for debugging, root cause analysis)
┌────────────────────────────────────────────────────┐
│ Database: Query latency, connection pool, slow queries│
│ Cache: Hit rate, memory, evictions                   │
│ Queue: Depth, consumer lag, DLQ count               │
│ Infrastructure: CPU, memory, disk, network per host  │
└────────────────────────────────────────────────────┘
```

### Dashboard Best Practices

```
1. GOLDEN SIGNALS first — always visible at the top
2. Time range selector — last 1h default, with zoom
3. Annotations — mark deploys, incidents on timeline
4. Thresholds — green/yellow/red zones on graphs
5. Links — click metric → go to related logs/traces
6. Auto-refresh — 30 seconds for on-call dashboards
7. Variable selectors — filter by service, region, environment
8. Single source of truth — one dashboard per service, not per person
```

---

## AWS Observability Stack

### CloudWatch Metrics

```
# Built-in metrics (free for AWS services):
AWS/Lambda/Duration, Errors, Invocations, Throttles
AWS/DynamoDB/ConsumedReadCapacityUnits, ThrottledRequests
AWS/ALB/HTTPCode_Target_5XX_Count, TargetResponseTime

# Custom metrics (your application):
# Best practice: Embedded Metric Format (EMF)
# One structured log = BOTH a log entry AND a metric!
{
  "_aws": {
    "CloudWatchMetrics": [{
      "Namespace": "MyApp",
      "Dimensions": [["Service", "Operation"]],
      "Metrics": [{"Name": "ProcessingTime", "Unit": "Milliseconds"}]
    }]
  },
  "Service": "OrderService",
  "Operation": "CreateOrder", 
  "ProcessingTime": 145,
  "OrderId": "ord_123"
}
# → CloudWatch Logs gets the full JSON (searchable)
# → CloudWatch Metrics gets ProcessingTime metric (graphable, alarmable)
# → ZERO PutMetricData API calls needed!
```

### CloudWatch Alarms

```
# Static threshold:
aws cloudwatch put-metric-alarm \
  --alarm-name "HighErrorRate" \
  --metric-name "5XXError" \
  --threshold 1 --comparison-operator GreaterThanThreshold \
  --evaluation-periods 3 --datapoints-to-alarm 2 \
  --period 60 \
  --alarm-actions "arn:aws:sns:...:oncall-pager"

# Anomaly detection:
# CloudWatch learns normal patterns → alerts on deviations
# No static threshold needed — adapts to seasonal patterns

# Composite alarm:
# "Fire only if BOTH error rate high AND latency high"
# Reduces noise from transient single-metric spikes

# Metric math:
# FILL(m1, 0) → treat missing data as 0
# RATE(m1) → calculate rate from cumulative counter
# m1/m2*100 → calculate percentage (error rate)
```

### CloudWatch Logs Insights

```sql
-- Find slow requests
fields @timestamp, @duration, @requestId
| filter @duration > 5000
| sort @duration desc
| limit 20

-- Error rate over time
fields @timestamp
| filter @message like /ERROR/
| stats count() as errors by bin(5m)

-- P99 latency by endpoint
fields endpoint, responseTime
| stats percentile(responseTime, 99) as p99 by endpoint
| sort p99 desc

-- All logs for a specific request
fields @timestamp, @message
| filter requestId = "req_abc123"
| sort @timestamp asc
```

### AWS X-Ray

```
# Distributed tracing across AWS services
# Auto-instruments: Lambda, API Gateway, ECS, EC2, SNS, SQS, DynamoDB, S3

# X-Ray trace visualization:
# Shows service map: which services call which
# Shows latency per segment: where time is spent
# Shows errors per service: which service is failing
# Shows annotations: custom searchable metadata

# Sampling: 1 request/sec + 5% of additional (configurable)
# Cost: $0.000005/trace recorded + $0.0000005/trace retrieved

# Service Map: Visual dependency graph of your architecture
# → Instantly see: "Order Service has 5% error rate calling Payment Service"
```

---

## Key Metrics for Every System Design

### Per Service (Application Layer)

```
Request rate (QPS), Error rate (%), Latency (P50/P95/P99)
Active connections, Thread pool utilization
Circuit breaker state (open/closed/half-open)
Dependency call latency + error rate (per downstream service)
Cache hit rate (for services using Redis/ElastiCache)
```

### Per Database

```
Query latency (P50/P99), Queries/sec, Slow query count
Connection pool utilization (active/max)
Replication lag (for read replicas)
Table size, Index size, Dead tuples (PostgreSQL VACUUM)
Throttled requests (DynamoDB), IOPS utilization (RDS)
```

### Per Queue (SQS/Kafka/Kinesis)

```
Consumer lag (messages behind), Messages/sec in/out
Consumer group rebalances/hour
Oldest unprocessed message age (ApproximateAgeOfOldestMessage)
DLQ depth (dead letter queue message count)
Batch size, Processing time per message
```

### Per Cache (Redis/ElastiCache)

```
Cache hit rate (target: >80%), Miss rate
Memory utilization (eviction starts at 100%)
Connection count, Commands/sec
Latency (should be < 1ms P99)
Evictions/sec (if > 0, need more memory)
```

### Infrastructure

```
CPU utilization (alert > 80%), Memory utilization
Disk I/O (IOPS, throughput), Network I/O (bytes in/out)
Pod restarts (Kubernetes), OOM kills, Container health
Load balancer: Active connections, 5xx rate, healthy target count
Auto-scaling: Current/desired/max instances
```

---

## On-Call, Runbooks & Incident Response

### Incident Severity Framework

```
SEV1 (Critical):
  • Total outage, data loss, security breach
  • All hands on deck, war room
  • Communication: Status page update every 15 min
  • Post-mortem: Required within 3 business days

SEV2 (Major):
  • Partial outage, significant degradation
  • Primary on-call + escalation
  • Communication: Status page update every 30 min
  • Post-mortem: Required within 5 business days

SEV3 (Minor):
  • Minor feature broken, workaround exists
  • Primary on-call handles
  • Communication: Internal only
  • Post-mortem: Optional, team decision
```

### Runbook Template

```
# RUNBOOK: High Error Rate Alert — Order Service

## Trigger
CloudWatch Alarm: OrderService-HighErrorRate
Condition: 5xx error rate > 1% for 5 minutes

## Quick Diagnosis (< 2 minutes)
1. Open OrderService dashboard: [link]
2. Check: Is the error rate across ALL endpoints or specific ones?
3. Check: Is it correlated with a recent deployment? (Check deploy annotations)
4. Check: Are downstream dependencies healthy? (Payment, DB, Cache)

## Common Causes & Fixes

### Cause 1: Recent deployment introduced bug
- Evidence: Error started exactly at deploy timestamp
- Fix: Rollback → `aws ecs update-service --force-new-deployment --task-definition <previous>`
- Time to fix: 5-10 minutes

### Cause 2: Database connection pool exhausted
- Evidence: DB connection count at max, slow queries in logs
- Fix: Scale up DB readers OR kill long-running queries
- Command: `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE duration > '60s'`

### Cause 3: Downstream dependency timeout
- Evidence: X-Ray shows 5s latency to payment-service
- Fix: Check payment-service health; if external provider down → enable circuit breaker
- Command: `aws ssm put-parameter --name /config/payment/circuit-breaker --value enabled`

## Escalation
If not resolved in 15 minutes → escalate to #order-service-oncall Slack channel
If SEV1 (total outage) → page secondary on-call and engineering manager
```

---

## Real-World Incident Walkthroughs

### Incident 1: The Cascading Failure

```
Timeline:
14:30 — Alert: Payment service P99 > 2s (normally 50ms)
14:31 — Dashboard: Payment service connection pool at 100% (max 100)
14:32 — Trace: Stripe API responding in 4s (normally 200ms)
14:33 — Alert: Order service error rate > 5% (timeout waiting for payment)
14:34 — Alert: Cart service error rate > 3% (users can't checkout)
14:35 — Alert: Frontend 503 errors > 10%

Root cause: Stripe had a partial outage → our connections waited → pool exhausted
                → order-service timed out → user retried → more requests → worse!

What was MISSING that made it worse:
✗ No circuit breaker on Stripe calls (kept waiting instead of failing fast)
✗ Connection pool too small (100) with no timeout on acquiring connections
✗ No retry budget (unlimited retries amplified the problem)

Fixes:
✓ Circuit breaker: After 5 failures in 10s → OPEN → return cached/default response
✓ Connection pool: 200 max with 2s acquisition timeout
✓ Timeout: 3s hard timeout on Stripe API (was 30s default!)
✓ Retry budget: Max 3 retries per request with exponential backoff
```

### Incident 2: The Silent Data Issue

```
Timeline:
No alerts for 3 days!

Customer report: "My order history shows the wrong prices"

Investigation:
  - Logs: Price update service ran successfully every day ✅
  - Metrics: All green — no errors, normal latency ✅
  - But: Price update was writing to STAGING database (wrong config)
  - Production prices were 3 days stale

What was MISSING:
✗ No DATA CORRECTNESS metric (only availability/latency)
✗ No freshness check ("when was this data last updated?")
✗ No end-to-end test (verify actual price matches expected)

Fixes:
✓ Data freshness metric: "max(last_updated) in price table"
✓ Alert: "Price data > 1 hour stale"
✓ Integration test: Nightly job validates sample prices match source
✓ Config drift detection: Alert if env config doesn't match expected
```

---

## Real-World Tools and Stack Comparison

| Layer | AWS Native | Open Source | SaaS |
|---|---|---|---|
| **Metrics** | CloudWatch Metrics | Prometheus + Grafana | Datadog, New Relic |
| **Logs** | CloudWatch Logs + Insights | ELK (Elasticsearch + Kibana) | Datadog Logs, Splunk |
| **Traces** | X-Ray | Jaeger / Tempo | Datadog APM, Honeycomb |
| **Alerting** | CloudWatch Alarms + SNS | Alertmanager | PagerDuty, OpsGenie |
| **Dashboards** | CloudWatch Dashboards | Grafana | Datadog, Grafana Cloud |
| **On-call** | — | — | PagerDuty, OpsGenie |
| **Status page** | — | Cachet | Statuspage.io |
| **Unified** | CloudWatch + X-Ray | Grafana Stack (Prometheus + Loki + Tempo) | Datadog, New Relic |

### When to Use What

```
Startup/small team → Datadog (all-in-one, easy setup, expensive)
AWS-heavy → CloudWatch + X-Ray (native, no extra infra, adequate)
Cost-sensitive → Grafana Stack open source (free, but you manage it)
Enterprise → Splunk/New Relic (compliance, support contracts)
Platform team → OpenTelemetry + Grafana Cloud (best flexibility)
```

---

## Applying Observability to System Design Problems

### Template: Observability Section in Any System Design

```
"For observability, I'd implement:

METRICS:
- Golden signals per service: latency P99, error rate, RPS, saturation
- Business metrics: [orders/min, conversion rate, revenue/hour]
- Dependency metrics: latency + error rate for each downstream call
- Dashboard: Golden signals at top → business → per-service → dependencies

LOGGING:
- Structured JSON with correlation IDs (request_id, trace_id)
- Log pipeline: [CloudWatch Logs or ELK]
- Retention: 7 days hot, 30 days warm, 90 days cold (S3)

TRACING:
- OpenTelemetry auto-instrumentation + custom spans for business logic
- Sample: 1% normal, 100% errors and slow requests
- Use for: debugging cross-service latency, finding bottlenecks

ALERTING:
- SLO-based: 99.9% availability, P99 < 200ms
- Error budget burn rate alerting (10x → page, 2x → ticket)
- Composite alarms to reduce noise
- Runbook linked to every alert

SLOs:
- Availability: 99.9% (43 min error budget/month)
- Latency: P99 < 200ms
- Error rate: < 0.1%
"
```

---

## Interview Talking Points & Scripts

### Observability Design

> *"For monitoring, I'd implement all three pillars: Prometheus for metrics using the RED method — rate, errors, duration per endpoint — with Grafana dashboards showing golden signals. Structured JSON logs aggregated via Fluentd to Elasticsearch for debugging. OpenTelemetry distributed tracing with 1% sampling for normal traffic and 100% for errors. I'd alert on SLO breaches — if P99 latency exceeds 200ms for 5 minutes or error rate exceeds 0.1% for 3 minutes, PagerDuty pages the on-call. The SLO is 99.9% availability with a monthly error budget of 43 minutes. I'd use error budget burn rate alerting: burning at 10x rate pages immediately, 2x rate creates a ticket."*

### When Things Go Wrong

> *"When a user reports slowness, the on-call checks the Grafana dashboard: P99 latency for the order-service spiked at 14:30. They pull the distributed trace for a slow request — the trace shows the payment-service call taking 5 seconds instead of 50ms. Payment-service metrics show connection pool at 100% utilization. Log search for payment-service errors shows 'Connection timeout to Stripe API'. Root cause: Stripe had a partial outage, exhausting our connection pool. Immediate fix: enable circuit breaker to fail fast. Long-term fix: increase pool size, add timeout, add retry budget."*

### "How would you prevent alert fatigue?"

> *"Three strategies: First, alert on symptoms not causes — P99 latency and error rate, not CPU or memory directly. Second, use SLO-based alerting with error budget burn rate — instead of static thresholds that fire during normal traffic variations, we alert when we're consuming error budget faster than expected. Third, composite alarms — only page when BOTH error rate is elevated AND latency is high, reducing false alarms from transient single-metric spikes."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong | Better |
|---------|---------------|--------|
| Not mentioning observability at all | Every production system needs monitoring | Always include metrics, logs, traces in your design |
| Alerting on CPU/memory directly | These are causes, not symptoms | Alert on P99 latency and error rate (user impact) |
| Not defining SLOs | Can't measure success without targets | "99.9% availability, P99 < 200ms, error rate < 0.1%" |
| No distributed tracing | Can't debug cross-service issues | "OpenTelemetry traces requests across all services" |
| Alerting on averages | Average hides tail latency | Use P99, not average |
| Logging everything | Costs explode, signal drowns in noise | Sample verbose logs, keep 100% of errors |
| No correlation between pillars | Can't go from "what" to "why" | request_id + trace_id in every log line |
| Monitoring only availability | Misses latency degradation, data issues | Monitor: availability + latency + data freshness + business metrics |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│           OBSERVABILITY & MONITORING CHEAT SHEET              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  THREE PILLARS:                                              │
│    Metrics: Numbers over time (Prometheus/CloudWatch)         │
│    Logs: Event records (ELK/CloudWatch Logs)                 │
│    Traces: Request path across services (X-Ray/Jaeger)       │
│    → Connect them: request_id + trace_id in everything       │
│                                                              │
│  METRIC FRAMEWORKS:                                          │
│    RED: Rate, Errors, Duration (per endpoint)                │
│    USE: Utilization, Saturation, Errors (per resource)       │
│    Golden Signals: Latency, Traffic, Errors, Saturation      │
│    → Track P99, not average!                                 │
│                                                              │
│  METRIC TYPES:                                               │
│    Counter: Monotonic up (requests, errors)                  │
│    Gauge: Up/down (CPU, memory, queue depth)                 │
│    Histogram: Distribution (latency buckets → percentiles)   │
│    ⚠️ High cardinality labels = cost killer!                 │
│                                                              │
│  SLOs & ERROR BUDGET:                                        │
│    SLO: 99.9% availability = 43 min downtime/month           │
│    Error budget burn rate alerting (10x=page, 2x=ticket)     │
│    SLO > SLA (buffer before contractual breach)              │
│                                                              │
│  ALERTING:                                                   │
│    Symptoms not causes (P99 > 500ms, not CPU > 80%)         │
│    Sustained conditions (5 min, not 1 second)                │
│    Composite alarms (AND/OR to reduce noise)                 │
│    Anomaly detection for seasonal traffic patterns           │
│    P1: Page now | P2: Business hours | P3: Ticket | P4: Dashboard│
│                                                              │
│  LOGGING:                                                    │
│    Structured JSON with request_id + trace_id               │
│    Levels: ERROR (always) > WARN > INFO > DEBUG (sample)     │
│    Hot/warm/cold storage tiers for cost control              │
│                                                              │
│  TRACING:                                                    │
│    OpenTelemetry = vendor-neutral standard                   │
│    Auto-instrumentation for HTTP, DB, queues, AWS SDK        │
│    Sampling: 1-5% normal, 100% errors/slow                  │
│    Span attributes: http.method, order.id, cache.hit         │
│                                                              │
│  DASHBOARD HIERARCHY:                                        │
│    Level 1: Executive (availability %, incidents)             │
│    Level 2: Service (golden signals, business metrics)       │
│    Level 3: Deep dive (DB, cache, queue, infra)              │
│                                                              │
│  AWS STACK:                                                  │
│    Metrics: CloudWatch + EMF (zero-cost custom metrics)      │
│    Logs: CloudWatch Logs + Insights (query)                  │
│    Traces: X-Ray (auto-instruments Lambda, API GW, SDK)      │
│    Alarms: Static + Anomaly Detection + Composite            │
│                                                              │
│  ON-CALL:                                                    │
│    Every alert → linked runbook                              │
│    Every incident → post-mortem (blameless)                  │
│    Error budget exhausted → freeze deploys                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **[45b: Observability Real-World Applications](./45b-observability-monitoring-real-world-applications.md)** — AWS CloudWatch examples per service
- **Topic 28: Availability & Disaster Recovery** — SLAs and uptime targets
- **Topic 30: Backpressure & Flow Control** — Monitoring queue depth and consumer lag
- **Topic 35: Cost vs Performance** — Monitoring-driven optimization
- **[AWS Architecture Mapping](./52-architecting-on-aws-service-mapping.md)** — Prometheus→CloudWatch, ELK→OpenSearch

---

*This document is part of the System Design Interview Deep Dive series.*
