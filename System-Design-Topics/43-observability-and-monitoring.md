# 🎯 Topic 43: Observability & Monitoring

> **System Design Interview — Deep Dive**
> Three pillars (metrics, logs, traces), the 4 golden signals, alerting on symptoms vs causes, and distributed tracing for microservices.

---

## Three Pillars of Observability

```
1. METRICS (Prometheus/CloudWatch):
   Quantitative — p50/p95/p99 latency, error rate, QPS, Kafka consumer lag
   "How many? How fast? What percentage?"

2. LOGS (ELK / CloudWatch Logs):
   Qualitative — structured JSON with request_id, user_id, error details
   "What happened? What went wrong?"

3. TRACES (Jaeger / X-Ray):
   Relational — full journey of a request across 5 microservices
   "Where did it happen? Which service is the bottleneck?"

Together:
  Metrics tell you SOMETHING is wrong (alert fires).
  Logs tell you WHAT went wrong (error details).
  Traces tell you WHERE it went wrong (which service, which query).
```

## The 4 Golden Signals

```
1. LATENCY:    p99 > 500ms for 5 minutes → alert
   "How long are requests taking?"

2. TRAFFIC:    QPS drops > 50% from baseline → alert
   "Are users reaching us?" (drop = upstream issue)

3. ERRORS:     5xx rate > 1% for 3 minutes → alert
   "Are requests failing?"

4. SATURATION: CPU > 85% or disk > 90% → alert
   "Are we running out of resources?"

Key principle: ALERT ON SYMPTOMS (user-visible), INVESTIGATE CAUSES.
  "p99 latency > 500ms" → user-visible symptom → ALERT
  "CPU > 80%" → potential cause → INVESTIGATE (may not affect users)
```

## Distributed Tracing

```
User reports "my feed is slow":
  Without tracing: Guess which of 5 services is slow.
  With tracing:    See the waterfall:
    API Gateway:      10ms (routing)
    Timeline Service: 50ms (Redis fetch)
    User Service:     300ms (DB query) ← BOTTLENECK!
    Media Service:    20ms (URL generation)
    Response:         20ms (serialization)
    Total:            400ms

Immediately clear: User Service needs a DB index.
```

## Structured Logging

```json
{
  "timestamp": "2025-03-15T14:30:00.123Z",
  "level": "ERROR",
  "service": "payment-service",
  "request_id": "req-abc-123",
  "user_id": "user-456",
  "trace_id": "trace-789",
  "message": "Payment gateway timeout",
  "error": "ConnectionTimeout after 3000ms",
  "gateway": "stripe",
  "amount": 49.99,
  "retry_count": 2
}
```

## Interview Script
> *"I'd instrument the three pillars of observability. Metrics: p50/p95/p99 latency, error rate, QPS. Logs: structured JSON with request_id for correlation. Traces: the full journey across microservices showing that 300ms of total 400ms is spent in the User Service — immediately pointing to the bottleneck. I'd alert on the 4 golden signals: latency, traffic, errors, and saturation."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          OBSERVABILITY & MONITORING CHEAT SHEET               │
├──────────────────────────────────────────────────────────────┤
│  THREE PILLARS:                                              │
│    Metrics: How many? How fast? (Prometheus/CloudWatch)       │
│    Logs: What happened? (ELK, structured JSON)               │
│    Traces: Where? Which service? (Jaeger/X-Ray)              │
│                                                              │
│  4 GOLDEN SIGNALS:                                           │
│    Latency: p99 > 500ms → alert                             │
│    Traffic: QPS drop > 50% → alert                           │
│    Errors:  5xx > 1% → alert                                │
│    Saturation: CPU > 85% → alert                            │
│                                                              │
│  PRINCIPLE: Alert on SYMPTOMS, investigate CAUSES            │
│  TRACING: Essential for microservices debugging              │
│  LOGGING: Structured JSON, request_id for correlation        │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
