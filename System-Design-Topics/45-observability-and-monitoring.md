# 🎯 Topic 45: Observability & Monitoring

> **System Design Interview — Deep Dive**
> A comprehensive guide covering the three pillars of observability (metrics, logs, traces), monitoring architecture, alerting strategies, SLIs/SLOs/SLAs, distributed tracing, log aggregation, dashboards, and how to articulate observability decisions in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Three Pillars of Observability](#three-pillars-of-observability)
3. [Metrics — What to Measure](#metrics--what-to-measure)
4. [Logging — Structured and Centralized](#logging--structured-and-centralized)
5. [Distributed Tracing](#distributed-tracing)
6. [SLIs, SLOs, and SLAs](#slis-slos-and-slas)
7. [Alerting Strategies](#alerting-strategies)
8. [Monitoring Architecture](#monitoring-architecture)
9. [Key Metrics for System Design](#key-metrics-for-system-design)
10. [Real-World Tools and Stack](#real-world-tools-and-stack)
11. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
12. [Common Interview Mistakes](#common-interview-mistakes)
13. [Summary Cheat Sheet](#summary-cheat-sheet)

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

---

## Three Pillars of Observability

### Metrics (Quantitative)

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
```

### Logs (Qualitative)

```
Detailed records of individual events:
  {"timestamp": "2025-03-15T14:30:05Z", "level": "ERROR", 
   "service": "payment-service", "message": "Connection timeout to Stripe API",
   "request_id": "req_abc123", "user_id": "user_456", "duration_ms": 5000}

Storage: Log aggregation system (ELK Stack, CloudWatch Logs, Datadog Logs)
Query: "Show me all ERROR logs from payment-service in the last 10 minutes"
Cardinality: High (one log per event, millions per hour)
Retention: Days to weeks (expensive at scale)
Best for: Debugging specific issues, audit trails
```

### Traces (Contextual)

```
End-to-end path of a request through multiple services:

  Trace ID: trace_xyz789
  ├── API Gateway (2ms)
  ├── User Service: getUser (15ms)
  ├── Order Service: createOrder (180ms)
  │   ├── Inventory Service: checkStock (20ms)
  │   ├── Payment Service: charge (150ms)  ← SLOW! (normally 50ms)
  │   │   └── Stripe API: createCharge (145ms)  ← ROOT CAUSE
  │   └── DB Write: insertOrder (10ms)
  └── Notification Service: sendConfirmation (5ms)
  Total: 202ms

Storage: Distributed tracing system (Jaeger, Zipkin, AWS X-Ray, Datadog APM)
Query: "Show me the trace for request req_abc123"
Best for: Finding which service in a chain is causing slowness
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

### The USE Method (For Resources)

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
  Latency increasing + Saturation high = Service is overloaded
  Errors increasing + Traffic constant = Bug deployed
  Traffic spiking + Latency normal = Service is handling the spike well
```

### Percentile Latencies

```
Why P99 matters more than average:

  Average latency: 50ms (looks great!)
  P99 latency: 2,000ms (1% of users wait 2 seconds!)
  
  If 10M requests/day: 100K users/day experience 2-second delays.
  Average hides the tail latency problem.

What to track:
  P50 (median): "Typical" user experience
  P95: "Most" users' worst case
  P99: "Almost all" users' worst case
  P99.9: Extreme tail (often hardware or GC issues)
  
  Alert on P99, not average. Users don't care about average.
```

---

## Logging — Structured and Centralized

### Structured Logging

```
❌ Unstructured: "Error processing order 123 for user 456: timeout"
  Hard to parse, hard to search, hard to aggregate

✅ Structured JSON:
  {
    "timestamp": "2025-03-15T14:30:05.123Z",
    "level": "ERROR",
    "service": "order-service",
    "instance": "order-service-pod-7a",
    "request_id": "req_abc123",
    "trace_id": "trace_xyz789",
    "user_id": "user_456",
    "order_id": "order_123",
    "error_type": "timeout",
    "message": "Payment service timeout after 5000ms",
    "duration_ms": 5000
  }

  Searchable: "Show all errors where error_type=timeout AND service=order-service"
  Aggregatable: "Count errors by error_type in the last hour"
  Correlatable: trace_id links to distributed trace
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
```

---

## Distributed Tracing

### How It Works

```
Every request gets a unique trace_id at the API gateway:

  trace_id = generate_uuid()
  
  Each service:
    1. Receives trace_id in request header (X-Trace-Id)
    2. Creates a span (start_time, end_time, service_name, operation)
    3. Passes trace_id to downstream service calls
    4. Reports span to tracing backend

  Tracing backend reconstructs the full request path:
    API Gateway → User Service → Order Service → Payment Service → DB

  Instrumentation: OpenTelemetry SDK (automatic for most frameworks)
    Adds ~1-5ms overhead per request (sampling reduces this)

Sampling:
  At 50K requests/sec: Can't trace ALL requests (too expensive)
  Sample 1% → 500 traces/sec (enough to find patterns)
  Always trace: Errors, slow requests (> P99), specific user IDs
```

---

## SLIs, SLOs, and SLAs

### Definitions

```
SLI (Service Level Indicator): A metric that measures service health
  Example: "Percentage of requests completing in < 200ms"
  Example: "Percentage of requests returning non-5xx status"

SLO (Service Level Objective): Target value for an SLI
  Example: "99.9% of requests complete in < 200ms" (P99 < 200ms)
  Example: "99.95% of requests return non-5xx" (error rate < 0.05%)

SLA (Service Level Agreement): Contract with consequences
  Example: "If availability drops below 99.9%, customer gets credits"
  SLA is EXTERNAL (contractual). SLO is INTERNAL (engineering target).
  SLO should be TIGHTER than SLA: If SLA = 99.9%, SLO = 99.95%
  (Buffer before you violate the contract)

Error budget:
  SLO = 99.9% availability per month
  Error budget = 0.1% = 43.2 minutes of downtime per month
  
  If you've used 30 minutes of error budget:
    13.2 minutes remaining → slow down deployments, be cautious
  If you've used 0 minutes:
    Full 43.2 minutes available → safe to deploy, experiment
```

### Common SLOs for System Design

```
API latency:     P99 < 200ms (user-facing), P99 < 50ms (internal)
Availability:    99.9% (3 nines = 43 min downtime/month)
Error rate:      < 0.1% (5xx errors)
Throughput:      Handle 100K QPS without degradation
Data freshness:  Dashboard data < 30 seconds old
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

Alert hierarchy:
  P1 (Page immediately): Service down, error rate > 5%, data loss risk
  P2 (Page during business hours): P99 > 2x normal, error rate > 1%
  P3 (Ticket, next business day): Disk > 85%, certificate expires in 7 days
  P4 (Dashboard only): CPU trending up, queue depth growing slowly
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
     
  3. Aggregate related alerts
     "3 services in the payment path are slow" → one alert
     vs 3 separate alerts for each service
     
  4. Auto-resolve
     If condition clears within 5 minutes → auto-close alert
     Only page for persistent issues
```

---

## Monitoring Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                MONITORING STACK                                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  METRICS:                                                    │
│    Collection: Prometheus (pull) or StatsD/CloudWatch (push) │
│    Storage: Prometheus TSDB / InfluxDB / CloudWatch          │
│    Visualization: Grafana dashboards                         │
│    Alerting: Prometheus Alertmanager → PagerDuty/Slack       │
│                                                              │
│  LOGS:                                                       │
│    Collection: Fluentd / Filebeat / CloudWatch agent         │
│    Transport: Kafka (buffer)                                 │
│    Storage: Elasticsearch / CloudWatch Logs / S3             │
│    Visualization: Kibana / CloudWatch Insights               │
│                                                              │
│  TRACES:                                                     │
│    Instrumentation: OpenTelemetry SDK                        │
│    Collection: OpenTelemetry Collector                       │
│    Storage: Jaeger / AWS X-Ray / Datadog APM                 │
│    Visualization: Jaeger UI / X-Ray console                  │
│                                                              │
│  UNIFIED:                                                    │
│    Datadog / New Relic / Grafana Cloud                       │
│    (All three pillars in one platform)                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Key Metrics for System Design

### Per Service

```
Request rate (QPS), Error rate (%), Latency (P50/P95/P99)
Active connections, Thread pool utilization
Queue depth (for async services)
Cache hit rate (for services using Redis)
```

### Per Database

```
Query latency (P50/P99), Queries/sec
Connection pool utilization (active/max)
Replication lag (for read replicas)
Table size, Index size, Dead tuples (PostgreSQL VACUUM health)
```

### Per Queue (Kafka/SQS)

```
Consumer lag (messages behind), Messages/sec in/out
Consumer group rebalances/hour
Oldest unprocessed message age
DLQ depth (dead letter queue size)
```

### Infrastructure

```
CPU utilization, Memory utilization
Disk I/O, Network I/O
Pod restarts (Kubernetes), OOM kills
Load balancer: Active connections, 5xx rate, healthy targets
```

---

## Real-World Tools and Stack

| Layer | AWS | Open Source | SaaS |
|---|---|---|---|
| **Metrics** | CloudWatch | Prometheus + Grafana | Datadog |
| **Logs** | CloudWatch Logs | ELK (Elasticsearch + Logstash + Kibana) | Datadog Logs |
| **Traces** | X-Ray | Jaeger / Zipkin | Datadog APM |
| **Alerting** | CloudWatch Alarms | Alertmanager | PagerDuty |
| **On-call** | — | — | PagerDuty / OpsGenie |

---

## Interview Talking Points & Scripts

### Observability Design

> *"For monitoring, I'd implement all three pillars: Prometheus for metrics (RED method — rate, errors, duration per endpoint), structured JSON logs aggregated via Fluentd to Elasticsearch, and distributed tracing with OpenTelemetry to trace requests across services. I'd alert on SLO breaches — if P99 latency exceeds 200ms for 5 minutes or error rate exceeds 0.1% for 3 minutes, PagerDuty pages the on-call. I'd set the SLO at 99.9% availability with a monthly error budget of 43 minutes."*

### When Things Go Wrong

> *"When a user reports slowness, the on-call checks the Grafana dashboard: P99 latency for the order-service spiked at 14:30. They pull the distributed trace for a slow request — the trace shows the payment-service call taking 5 seconds instead of 50ms. Payment-service metrics show connection pool at 100% utilization. Root cause: upstream payment provider had a slowdown, exhausting our connection pool. Fix: increase pool size + add circuit breaker to fail fast."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not mentioning observability at all
**Better**: Always mention metrics, logging, and tracing as part of your design.

### ❌ Mistake 2: Alerting on raw metrics instead of user impact
**Better**: Alert on P99 latency and error rate, not CPU or memory directly.

### ❌ Mistake 3: Not mentioning SLOs
**Better**: Define SLOs upfront: "99.9% availability, P99 < 200ms, error rate < 0.1%."

### ❌ Mistake 4: No distributed tracing
**Better**: "OpenTelemetry traces every request across services — when the page is slow, I can see exactly which service in the chain is causing the delay."

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│           OBSERVABILITY & MONITORING CHEAT SHEET              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  THREE PILLARS:                                              │
│    Metrics: Numbers over time (Prometheus/Grafana)           │
│    Logs: Event records (ELK/CloudWatch)                      │
│    Traces: Request path across services (Jaeger/X-Ray)       │
│                                                              │
│  KEY METRICS (RED + USE + Four Golden Signals):              │
│    Rate, Errors, Duration (per endpoint)                     │
│    Utilization, Saturation, Errors (per resource)            │
│    P99 > average (tail latency matters!)                     │
│                                                              │
│  SLOs: 99.9% availability, P99 < 200ms, error < 0.1%        │
│  ERROR BUDGET: 0.1% = 43 minutes downtime/month             │
│                                                              │
│  ALERTING: Symptoms not causes. P99 > 500ms for 5 min.      │
│    P1: Page immediately (service down, data loss)            │
│    P2: Business hours (elevated errors)                      │
│    P3: Ticket (trending issues)                              │
│                                                              │
│  STRUCTURED LOGGING: JSON format, request_id, trace_id       │
│  SAMPLING: Trace 1% of requests, 100% of errors              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 28: Availability & Disaster Recovery** — SLAs and uptime targets
- **Topic 30: Backpressure & Flow Control** — Monitoring queue depth and consumer lag
- **Topic 35: Cost vs Performance** — Monitoring-driven optimization

---

*This document is part of the System Design Interview Deep Dive series.*
