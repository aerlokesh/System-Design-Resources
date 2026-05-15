# 📊 Observability & Monitoring Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how observability systems are used in production, with **AWS CloudWatch as the primary reference**. Every metric, alarm, dashboard, and log pattern is explained with the **WHY** behind it.

---

## 📋 Table of Contents

1. [The Three Pillars of Observability](#the-three-pillars-of-observability)
2. [AWS CloudWatch — Complete Deep Dive](#1-aws-cloudwatch--complete-deep-dive)
3. [Amazon — E-Commerce Platform Monitoring](#2-amazon--e-commerce-platform-monitoring)
4. [Netflix — Video Streaming Health Dashboard](#3-netflix--video-streaming-health-dashboard)
5. [Uber — Real-Time Ride Matching Monitoring](#4-uber--real-time-ride-matching-monitoring)
6. [Stripe — Payment Processing Observability](#5-stripe--payment-processing-observability)
7. [Slack — Chat Service SLO Monitoring](#6-slack--chat-service-slo-monitoring)
8. [DoorDash — Delivery Pipeline Monitoring](#7-doordash--delivery-pipeline-monitoring)
9. [Twitch — Live Streaming Infrastructure](#8-twitch--live-streaming-infrastructure)
10. [Robinhood — Trading System Monitoring](#9-robinhood--trading-system-monitoring)
11. [Airbnb — Booking Flow Observability](#10-airbnb--booking-flow-observability)
12. [AWS Lambda — Serverless Monitoring Patterns](#11-aws-lambda--serverless-monitoring-patterns)
13. [ECS/EKS — Container Monitoring on AWS](#12-ecseks--container-monitoring-on-aws)
14. [DynamoDB — Database Monitoring Patterns](#13-dynamodb--database-monitoring-patterns)
15. [API Gateway — Edge Monitoring](#14-api-gateway--edge-monitoring)
16. [16–20: Additional Patterns (Summary)](#1620-additional-patterns-summary)
17. [🏆 CloudWatch Cheat Sheet — Every Service Pattern](#-cloudwatch-cheat-sheet--every-service-pattern)
18. [🎯 Interview Summary](#-interview-summary-how-would-you-design-observability-for-a-production-system)

---

## The Three Pillars of Observability

```
┌───────────────────────────────────────────────────────────────────────┐
│                    THREE PILLARS OF OBSERVABILITY                      │
├──────────────────┬──────────────────┬─────────────────────────────────┤
│     METRICS      │      LOGS        │          TRACES                 │
│  (What happened) │  (Why it happened)│  (How it flowed)              │
├──────────────────┼──────────────────┼─────────────────────────────────┤
│ CloudWatch       │ CloudWatch Logs  │ AWS X-Ray                      │
│ Metrics          │                  │                                 │
│                  │                  │                                 │
│ - Counters       │ - Structured JSON│ - Request path across services │
│ - Gauges         │ - Log groups     │ - Latency per segment          │
│ - Histograms     │ - Insights query │ - Error propagation            │
│ - Percentiles    │ - Metric filters │ - Service map                  │
│                  │ - Retention      │ - Annotations                  │
│                  │                  │                                 │
│ "p99 latency     │ "NullPointer at  │ "API→Lambda→DDB took 340ms,   │
│  is 450ms"       │  OrderService    │  DDB was 280ms of that"       │
│                  │  line 42"        │                                 │
└──────────────────┴──────────────────┴─────────────────────────────────┘
```

### The Golden Signals (Google SRE)

| Signal | What | CloudWatch Metric |
|--------|------|------------------|
| **Latency** | Time to serve a request | `Duration`, `IntegrationLatency`, custom `ResponseTime` |
| **Traffic** | Requests per second | `Count`, `Invocations`, `RequestCount` |
| **Errors** | Rate of failed requests | `5XXError`, `4XXError`, `Errors`, `Faults` |
| **Saturation** | How full your service is | `CPUUtilization`, `MemoryUtilization`, `ConsumedReadCapacityUnits` |

---

## 1. AWS CloudWatch — Complete Deep Dive

### CloudWatch Metrics

```
# Metric structure:
Namespace / MetricName / Dimensions / Value / Timestamp

# Example:
AWS/Lambda / Duration / FunctionName=OrderProcessor / 145ms / 2024-01-15T14:30:00Z
AWS/DynamoDB / ConsumedWriteCapacityUnits / TableName=Orders / 250 / 2024-01-15T14:30:00Z
Custom/MyApp / OrdersProcessed / Environment=prod / 1 / 2024-01-15T14:30:00Z
```

### Custom Metrics (EMF — Embedded Metric Format)

```json
// Structured log that CloudWatch auto-extracts as metrics
// Write to stdout → CloudWatch Logs → Auto-extracted as CloudWatch Metrics
{
  "_aws": {
    "Timestamp": 1705327800000,
    "CloudWatchMetrics": [{
      "Namespace": "MyApp/Orders",
      "Dimensions": [["Environment", "Region"]],
      "Metrics": [
        { "Name": "OrderProcessingTime", "Unit": "Milliseconds" },
        { "Name": "OrderValue", "Unit": "Count" }
      ]
    }]
  },
  "Environment": "prod",
  "Region": "us-east-1",
  "OrderProcessingTime": 145,
  "OrderValue": 4999,
  "OrderId": "ord_abc123",
  "CustomerId": "cust_456"
}
// Result: 
// 1. Full JSON logged to CloudWatch Logs (searchable)
// 2. OrderProcessingTime metric auto-published to CloudWatch Metrics (graphable, alarmable)
// 3. Zero extra API calls — EMF does both in one write!
```

> **Interview Key**: EMF is the **best practice** for custom metrics on AWS. One structured log = both log entry AND metric datapoint. No PutMetricData API calls needed.

### CloudWatch Alarms

```
# Alarm states: OK → INSUFFICIENT_DATA → ALARM

# Static threshold alarm
aws cloudwatch put-metric-alarm \
  --alarm-name "OrderService-HighLatency" \
  --metric-name "Duration" \
  --namespace "AWS/Lambda" \
  --statistic "p99" \
  --period 300 \
  --threshold 3000 \
  --comparison-operator "GreaterThanThreshold" \
  --evaluation-periods 3 \
  --datapoints-to-alarm 2 \
  --alarm-actions "arn:aws:sns:us-east-1:123456:oncall-pager"
# → Fires if p99 latency > 3000ms for 2 out of 3 consecutive 5-min periods

# Anomaly detection alarm (ML-based)
# CloudWatch learns normal patterns → alerts on deviations
# Great for: traffic patterns, seasonal variations, gradual degradation
aws cloudwatch put-metric-alarm \
  --alarm-name "OrderService-AnomalyDetection" \
  --metrics '[{"Id":"m1","MetricStat":{"Metric":{"Namespace":"Custom/MyApp","MetricName":"OrderCount"},"Period":300,"Stat":"Sum"}}]' \
  --threshold-metric-id "ad1"
# Uses ANOMALY_DETECTION_BAND — no static threshold needed

# Composite alarm (AND/OR logic)
# "Fire only if BOTH high error rate AND high latency"
aws cloudwatch put-composite-alarm \
  --alarm-name "OrderService-Critical" \
  --alarm-rule "ALARM(HighErrorRate) AND ALARM(HighLatency)"
  --alarm-actions "arn:aws:sns:us-east-1:123456:sev2-pager"
```

### CloudWatch Logs Insights (Querying)

```sql
-- Find slowest requests in the last hour
fields @timestamp, @duration, @requestId
| filter @duration > 5000
| sort @duration desc
| limit 20

-- Error rate by API endpoint
fields @message
| filter @message like /ERROR/
| stats count() as errors by endpoint
| sort errors desc

-- p50, p90, p99 latency by operation
fields operation, responseTime
| stats percentile(responseTime, 50) as p50,
        percentile(responseTime, 90) as p90,
        percentile(responseTime, 99) as p99
  by operation

-- Find all logs for a specific request (tracing)
fields @timestamp, @message
| filter requestId = "req_abc123"
| sort @timestamp asc

-- Metric filter: Create metric from log patterns
# Log group: /aws/lambda/OrderProcessor
# Filter pattern: { $.statusCode = 500 }
# → Publishes "500ErrorCount" metric to CloudWatch Metrics
# → Can alarm on this metric!

-- Aggregate errors over time (timeseries)
fields @timestamp, @message
| filter @message like /Exception/
| stats count() as errorCount by bin(5m)
| sort @timestamp desc
```

### AWS X-Ray (Distributed Tracing)

```
# X-Ray trace for an API request:
┌─────────────────────────────────────────────────────────────────────────┐
│ Trace ID: 1-5f8b3abc-12345678                                          │
│ Total Duration: 340ms                                                   │
│                                                                         │
│ ┌─ API Gateway ──────── 340ms ────────────────────────────────────────┐ │
│ │                                                                     │ │
│ │  ┌─ Lambda: OrderProcessor ──── 320ms ──────────────────────────┐  │ │
│ │  │                                                               │  │ │
│ │  │  ┌─ DynamoDB: GetItem ────── 15ms ──┐                       │  │ │
│ │  │  │  Table: Users                     │                       │  │ │
│ │  │  └──────────────────────────────────┘                       │  │ │
│ │  │                                                               │  │ │
│ │  │  ┌─ DynamoDB: PutItem ────── 280ms ──┐   ← BOTTLENECK!     │  │ │
│ │  │  │  Table: Orders                      │                     │  │ │
│ │  │  │  ⚠️ RetryCount: 3 (throttled)      │                     │  │ │
│ │  │  └────────────────────────────────────┘                     │  │ │
│ │  │                                                               │  │ │
│ │  │  ┌─ SNS: Publish ────── 12ms ──┐                            │  │ │
│ │  │  │  Topic: order-notifications   │                            │  │ │
│ │  │  └──────────────────────────────┘                            │  │ │
│ │  └───────────────────────────────────────────────────────────────┘  │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘

# X-Ray reveals: DynamoDB PutItem is the bottleneck (280ms with 3 retries = throttled!)
# Action: Increase DynamoDB provisioned capacity or switch to on-demand
```

### X-Ray Instrumentation (Java/Python)

```java
// Java — AWS X-Ray SDK auto-instruments AWS SDK calls
@XRayEnabled
public class OrderService {
    
    @Traced  // Creates subsegment for this method
    public Order processOrder(OrderRequest request) {
        // X-Ray automatically traces:
        // - DynamoDB calls
        // - S3 calls
        // - SQS calls
        // - HTTP calls to other services
        
        // Custom annotation (searchable in X-Ray console)
        AWSXRay.getCurrentSegment().putAnnotation("orderId", request.getOrderId());
        
        // Custom metadata (not searchable, but visible in trace)
        AWSXRay.getCurrentSegment().putMetadata("orderItems", request.getItems());
        
        return dynamoDbClient.putItem(/* ... */);
    }
}
```

```python
# Python — aws-xray-sdk
from aws_xray_sdk.core import xray_recorder, patch_all
patch_all()  # Auto-instruments boto3, requests, sqlalchemy

@xray_recorder.capture('process_order')
def process_order(order_id):
    subsegment = xray_recorder.current_subsegment()
    subsegment.put_annotation('order_id', order_id)
    
    # All boto3 calls auto-traced
    dynamodb.put_item(TableName='Orders', Item={...})
```

---

## 2. Amazon — E-Commerce Platform Monitoring

### 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    AMAZON OBSERVABILITY STACK                 │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ CloudFront│  │API Gateway│  │  Lambda  │  │ DynamoDB │   │
│  │ Metrics   │  │ Metrics   │  │ Metrics  │  │ Metrics  │   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘   │
│       │              │              │              │          │
│       └──────────────┴──────────────┴──────────────┘          │
│                           │                                    │
│                  ┌────────▼────────┐                          │
│                  │   CloudWatch    │                          │
│                  │   Dashboards    │                          │
│                  └────────┬────────┘                          │
│                           │                                    │
│              ┌────────────▼──────────────┐                    │
│              │    CloudWatch Alarms      │                    │
│              │  → SNS → PagerDuty/Slack  │                    │
│              └──────────────────────────┘                    │
│                                                              │
│              ┌─────────────────────────┐                     │
│              │   X-Ray Service Map     │                     │
│              │  (end-to-end trace)     │                     │
│              └─────────────────────────┘                     │
└─────────────────────────────────────────────────────────────┘
```

### Key Metrics to Monitor (Per Service)

```
# API Gateway:
- 5XXError (server errors) → ALARM if > 1% of requests
- 4XXError (client errors) → ALARM if spike (potential DDoS)
- Latency (p50, p90, p99) → ALARM if p99 > SLA threshold
- Count (requests/sec) → Traffic monitoring + anomaly detection

# Lambda (OrderProcessor):
- Duration (p50, p90, p99) → ALARM if p99 > timeout/2
- Errors → ALARM if any errors
- Throttles → ALARM if > 0 (need more concurrency)
- ConcurrentExecutions → approaching limit?
- IteratorAge (if Kinesis/DynamoDB Streams trigger) → ALARM if growing (falling behind!)

# DynamoDB (Orders table):
- ConsumedReadCapacityUnits / ProvisionedReadCapacityUnits → % utilization
- ConsumedWriteCapacityUnits → ALARM if > 80% provisioned
- ThrottledRequests → ALARM if > 0
- SuccessfulRequestLatency → ALARM if increasing
- SystemErrors → AWS-side issue (rare)
- UserErrors → Your code has bugs (wrong queries)

# SQS (Order Queue):
- ApproximateNumberOfMessagesVisible → queue depth (backlog)
- ApproximateAgeOfOldestMessage → ALARM if > threshold (processing stuck!)
- NumberOfMessagesSent vs NumberOfMessagesReceived → producer/consumer balance
- NumberOfMessagesDeleted → successful processing rate
```

### Dashboard Layout (Best Practice)

```
┌────────────────────────────────────────────────────────────┐
│ ROW 1: GOLDEN SIGNALS (the only row you need at 3 AM)      │
│ ┌─────────────┬──────────────┬──────────────┬────────────┐ │
│ │ Error Rate  │ p99 Latency  │ Request Rate │ Saturation │ │
│ │ (big number)│ (big number) │ (sparkline)  │ (gauge)    │ │
│ └─────────────┴──────────────┴──────────────┴────────────┘ │
│                                                            │
│ ROW 2: BUSINESS METRICS                                     │
│ ┌───────────────────┬──────────────────────────────────┐   │
│ │ Orders/min        │ Revenue/hour (if applicable)      │   │
│ │ (timeseries)      │ (timeseries)                      │   │
│ └───────────────────┴──────────────────────────────────┘   │
│                                                            │
│ ROW 3: PER-SERVICE BREAKDOWN                                │
│ ┌──────────┬──────────┬──────────┬──────────┬──────────┐  │
│ │ API GW   │ Lambda   │ DynamoDB │ SQS      │ S3       │  │
│ │ latency  │ errors   │ throttle │ depth    │ errors   │  │
│ └──────────┴──────────┴──────────┴──────────┴──────────┘  │
│                                                            │
│ ROW 4: DOWNSTREAM DEPENDENCIES                              │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ Payment Service | Notification Service | Auth Service │   │
│ │ (latency + error rate for each)                       │   │
│ └──────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

---

## 3. Netflix — Video Streaming Health Dashboard

### Key Metrics

```
# Stream start time (SLO: p99 < 3 seconds)
Custom/Netflix/StreamStartTime → p50, p90, p99

# Rebuffer rate (video stops to load)
Custom/Netflix/RebufferRate → percentage per session
# ALARM if rebuffer rate > 0.5% of sessions

# Bitrate switching (adaptive streaming)
Custom/Netflix/BitrateDowngrades → count per minute
# Spike = CDN/network issues

# Error rate by device type
Custom/Netflix/PlaybackErrors → dimension: { deviceType: "Roku" | "iOS" | "Web" }
# Catch device-specific regressions after app update

# Regional availability
Custom/Netflix/Availability → dimension: { region: "us-east-1" | "eu-west-1" }
# ALARM if any region < 99.95%
```

---

## 4. Uber — Real-Time Ride Matching Monitoring

### Key Metrics

```
# Match time (rider request → driver assigned)
Custom/Uber/MatchTime → p50, p90, p99
# SLO: p99 < 30 seconds in metro areas

# Surge pricing triggers
Custom/Uber/SurgeTriggers → count by region
# Anomaly detection: unusual surge = supply/demand imbalance

# Driver supply (available drivers)
Custom/Uber/AvailableDrivers → gauge by geo-cell
# ALARM if drops below threshold for area

# Trip completion rate
Custom/Uber/TripCompletionRate → percentage
# ALARM if < 95% (drivers canceling or system failures)

# ETA accuracy (predicted vs actual arrival)
Custom/Uber/ETAError → absolute difference in seconds
# Track drift: if ETA consistently off → routing model issue
```

---

## 5. Stripe — Payment Processing Observability

### Key Metrics

```
# Payment success rate (THE most important metric)
Custom/Stripe/PaymentSuccessRate → percentage
# SLO: > 99.9%. ALARM if drops below 99.5% for 5 minutes

# Payment processing latency
Custom/Stripe/PaymentDuration → p50, p90, p99
# SLO: p99 < 2 seconds

# Decline rate by reason
Custom/Stripe/Declines → dimension: { reason: "insufficient_funds" | "card_declined" | "fraud" }
# Spike in "card_declined" = potential processor issue

# Idempotency key collisions
Custom/Stripe/IdempotencyHits → count
# Expected: some retries. ALARM if massive spike (client retry storm)

# Webhook delivery success rate
Custom/Stripe/WebhookDeliveryRate → percentage
# ALARM if < 99% (customer integrations failing)
```

### Distributed Trace: Payment Flow

```
API Request → Auth Check (5ms) → Fraud Detection (50ms) → Card Network (200ms) → Ledger Write (20ms) → Webhook (async)
                                                              ↑
                                                        BOTTLENECK
# X-Ray trace reveals: Card network latency dominates
# Action: Can't reduce card network time → optimize everything else
# + Set timeout on card network call (2s) → fail fast if network slow
```

---

## 6. Slack — Chat Service SLO Monitoring

### SLO-Based Alerting

```
# SLO: 99.99% message delivery success rate
# Error budget per month: 0.01% × 30 days × 24 hours = ~4.3 minutes of downtime

# Error budget burn rate alert:
# If burning error budget 10x faster than expected → page immediately
# If burning 2x faster → Slack alert, investigate within 1 hour

# CloudWatch Math Expression:
# BurnRate = ErrorsInWindow / (TotalInWindow × (1 - SLO_Target))
METRICS([m1, m2])
BurnRate = m1 / (m2 * 0.0001)
# ALARM if BurnRate > 10 for 5 minutes (fast burn)
# ALARM if BurnRate > 2 for 1 hour (slow burn)
```

---

## 7. DoorDash — Delivery Pipeline Monitoring

```
# Order-to-delivery pipeline stages:
PLACED → CONFIRMED → PREPARING → DASHER_ASSIGNED → PICKED_UP → EN_ROUTE → DELIVERED

# Monitor time spent in each stage:
Custom/DoorDash/StageTime → by { stage: "CONFIRMED", region: "SF" }
# ALARM if DASHER_ASSIGNED stage > 10 minutes (no available dashers)
# ALARM if PREPARING stage > 30 minutes (restaurant slow)

# Stuck orders (no state transition for N minutes):
# CloudWatch Logs Insights:
fields orderId, currentStage, lastTransition
| filter (now() - lastTransition) > 1800  -- 30 minutes stuck
| sort lastTransition asc
```

---

## 8. Twitch — Live Streaming Infrastructure

```
# Stream health per channel:
Custom/Twitch/FrameDropRate → by streamerId
Custom/Twitch/Bitrate → by streamerId, quality
Custom/Twitch/ViewerBufferRatio → aggregate

# Infrastructure metrics:
Custom/Twitch/TranscoderUtilization → by region
# ALARM if > 85% (need to scale transcoders)

Custom/Twitch/CDNLatency → p99 by edge_location
# ALARM if p99 > 200ms for any edge location
```

---

## 9. Robinhood — Trading System Monitoring

```
# Order execution latency (regulatory requirement)
Custom/Robinhood/OrderExecutionTime → p99
# SLO: < 100ms (SEC requirement for best execution)
# ALARM if p99 > 50ms (warn) or > 80ms (critical)

# Market data freshness (price staleness)
Custom/Robinhood/PriceStaleness → max age in ms
# ALARM if any symbol > 500ms stale (displaying wrong prices!)

# Portfolio calculation accuracy
Custom/Robinhood/PortfolioCalcTime → p99
# Must complete before market open (9:30 AM ET)
```

---

## 10. Airbnb — Booking Flow Observability

```
# Conversion funnel monitoring:
Custom/Airbnb/SearchRequests → count
Custom/Airbnb/ListingViews → count
Custom/Airbnb/BookingAttempts → count
Custom/Airbnb/BookingSuccess → count

# Conversion rate = BookingSuccess / SearchRequests
# ALARM if conversion rate drops > 20% from baseline (anomaly detection)
# Could indicate: slow search, broken checkout, payment issues

# Search latency by market:
Custom/Airbnb/SearchLatency → p99 by { market: "NYC" | "SF" | "London" }
# Different SLOs per market based on listing density
```

---

## 11. AWS Lambda — Serverless Monitoring Patterns

### Complete Lambda Monitoring Setup

```
# Built-in metrics (free):
AWS/Lambda/Invocations          → traffic volume
AWS/Lambda/Errors               → failed invocations
AWS/Lambda/Duration             → execution time
AWS/Lambda/Throttles            → concurrency limit hit
AWS/Lambda/ConcurrentExecutions → current utilization
AWS/Lambda/IteratorAge          → stream processing lag (Kinesis/DDB Streams)

# Key alarms:
# 1. Error rate > 1% for 5 minutes → page
# 2. p99 Duration > 80% of timeout → warn (about to timeout!)
# 3. Throttles > 0 → concurrency limit hit, increase reserved concurrency
# 4. IteratorAge increasing → falling behind on stream processing

# Lambda Insights (enhanced monitoring):
# CPU, memory, network, temp disk usage per invocation
# Identifies: memory-bound functions, CPU-bound functions
# → Right-size memory allocation (memory also increases CPU)

# Cold start tracking (custom metric via EMF):
{
  "_aws": { "CloudWatchMetrics": [{ "Namespace": "Custom/Lambda", "Metrics": [
    { "Name": "ColdStart", "Unit": "Count" },
    { "Name": "InitDuration", "Unit": "Milliseconds" }
  ]}]},
  "ColdStart": 1,
  "InitDuration": 850,
  "FunctionName": "OrderProcessor"
}
```

---

## 12. ECS/EKS — Container Monitoring on AWS

```
# Container Insights metrics:
AWS/ECS/CPUUtilization → by ServiceName, ClusterName
AWS/ECS/MemoryUtilization → by ServiceName
AWS/ECS/RunningTaskCount → vs DesiredTaskCount

# Key alarms:
# CPU > 80% for 5 minutes → scale out
# Memory > 85% → scale out (OOMKill risk)
# RunningTaskCount < DesiredTaskCount → tasks crashing/failing health checks
# DesiredTaskCount hitting max → need to increase max capacity

# Application-level (from your containers via EMF):
Custom/MyService/RequestLatency → p99
Custom/MyService/ErrorRate → percentage
Custom/MyService/ActiveConnections → gauge
Custom/MyService/QueueDepth → gauge
```

---

## 13. DynamoDB — Database Monitoring Patterns

```
# Critical DynamoDB metrics:
AWS/DynamoDB/ConsumedReadCapacityUnits   → vs provisioned (% usage)
AWS/DynamoDB/ConsumedWriteCapacityUnits  → vs provisioned
AWS/DynamoDB/ThrottledRequests           → ALARM if > 0 (you're losing data!)
AWS/DynamoDB/SuccessfulRequestLatency    → p99 should be < 10ms
AWS/DynamoDB/SystemErrors                → AWS issue (escalate to AWS support)
AWS/DynamoDB/UserErrors                  → YOUR bug (wrong query/schema)

# GSI monitoring (often forgotten!):
AWS/DynamoDB/ConsumedWriteCapacityUnits → by GlobalSecondaryIndexName
# GSIs have SEPARATE capacity — can throttle independently!

# DynamoDB Contributor Insights:
# Shows which partition keys are "hot" (most accessed)
# Identifies hot partitions before they cause throttling
# → CloudWatch metric: DynamoDB/ContributorInsights → top partition keys

# Stream monitoring (if using DynamoDB Streams + Lambda):
AWS/Lambda/IteratorAge for stream-triggered Lambda
# ALARM if IteratorAge > 60 seconds → processing falling behind
# Possible causes: Lambda errors, throttling, slow downstream
```

---

## 14. API Gateway — Edge Monitoring

```
# API Gateway metrics:
AWS/ApiGateway/5XXError     → server errors (YOUR code broke)
AWS/ApiGateway/4XXError     → client errors (bad requests, auth failures)
AWS/ApiGateway/Latency      → end-to-end (includes backend)
AWS/ApiGateway/IntegrationLatency → just backend time
AWS/ApiGateway/Count        → request volume

# Key insight: Latency - IntegrationLatency = API Gateway overhead
# If overhead > 50ms consistently → consider direct Lambda invoke

# Per-route monitoring:
# Dimension: { Resource: "/orders/{id}", Method: "GET" }
# Catch: one slow endpoint dragging down overall p99

# Throttling:
AWS/ApiGateway/Count        → approaching account/stage limit?
# Default: 10,000 RPS per region. ALARM at 8,000 RPS.
```

---

## 16–20: Additional Patterns (Summary)

### 16. S3 — Object Storage Monitoring
```
AWS/S3/BucketSizeBytes → storage growth rate
AWS/S3/NumberOfObjects → object count growth
S3 Request Metrics: 4xxErrors, 5xxErrors, FirstByteLatency
# Enable request metrics per prefix for cost attribution
```

### 17. SQS — Queue Health
```
ApproximateAgeOfOldestMessage → ALARM if growing (consumers stuck!)
ApproximateNumberOfMessagesVisible → backlog depth
NumberOfEmptyReceives → consumers polling empty queue (waste)
→ Switch to long polling (WaitTimeSeconds=20) to reduce empty receives
```

### 18. SNS — Notification Delivery
```
NumberOfMessagesPublished → producer rate
NumberOfNotificationsFailed → delivery failures
# By subscription protocol: Lambda, SQS, HTTP → identify failing subscribers
```

### 19. ElastiCache (Redis) — Cache Monitoring
```
CacheHitRate → ALARM if drops below 80% (cache warming issue or eviction)
EngineCPUUtilization → Redis is single-threaded, 100% = one core saturated
DatabaseMemoryUsagePercentage → ALARM at 80% (eviction starts)
CurrConnections → connection pool sizing
```

### 20. RDS/Aurora — Database Monitoring
```
CPUUtilization → ALARM > 80%
FreeableMemory → ALARM if dropping
DatabaseConnections → vs max_connections (connection pool leak?)
ReadLatency / WriteLatency → ALARM if increasing
ReplicaLag → ALARM if > 1 second (read replica falling behind)
```

---

## 🏆 CloudWatch Cheat Sheet — Every Service Pattern

```
┌────────────────────────────────────────────────────────────────────────┐
│         AWS CLOUDWATCH MONITORING CHEAT SHEET                          │
├────────────────────────────────────────────────────────────────────────┤
│ SERVICE          │ CRITICAL METRICS           │ ALARM THRESHOLD        │
│──────────────────┼────────────────────────────┼────────────────────────│
│ API Gateway      │ 5XXError, Latency p99      │ >1% errors, >SLA ms   │
│ Lambda           │ Errors, Duration, Throttles│ >0 throttles, >80% TO │
│ DynamoDB         │ ThrottledRequests, Latency │ >0 throttles, >10ms   │
│ SQS              │ AgeOfOldestMessage, Depth  │ Age growing, depth>N  │
│ ECS/EKS          │ CPU, Memory, TaskCount     │ >80% CPU, task<desired│
│ RDS/Aurora       │ CPU, Connections, RepLag   │ >80% CPU, lag>1s      │
│ ElastiCache      │ CacheHitRate, CPU, Memory  │ Hit<80%, Mem>80%      │
│ S3               │ 4xx/5xx Errors, Latency    │ >0 5xx, latency spike │
│ CloudFront       │ ErrorRate, CacheHitRatio   │ >1% errors, hit<80%   │
│ SNS              │ DeliveryFailures           │ Any failures           │
│ Kinesis          │ IteratorAge, GetRecords    │ Age increasing         │
│                  │                            │                        │
│ CUSTOM METRICS (EMF):                         │                        │
│ • Structured JSON to stdout → auto-extracted  │                        │
│ • Zero PutMetricData API calls                │                        │
│ • Best practice for Lambda, ECS, EKS          │                        │
│                                                │                        │
│ ALERTING STRATEGY:                            │                        │
│ • Composite alarms (AND/OR) to reduce noise   │                        │
│ • Anomaly detection for traffic patterns       │                        │
│ • SLO-based burn rate for error budgets        │                        │
│ • SNS → Lambda → Slack/PagerDuty integration  │                        │
│                                                │                        │
│ TRACING (X-Ray):                              │                        │
│ • Auto-instruments AWS SDK calls               │                        │
│ • Service map shows dependencies               │                        │
│ • Annotations = searchable, Metadata = detail  │                        │
│ • Sampling: 1 req/sec + 5% of additional       │                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary: "How Would You Design Observability for a Production System?"

> *"I'd implement all three pillars — metrics, logs, and traces — using AWS CloudWatch, CloudWatch Logs, and X-Ray. For metrics, I'd track the four golden signals: latency (p99), traffic (RPS), errors (5XX rate), and saturation (CPU/memory utilization) for every service. I'd use Embedded Metric Format to publish custom metrics from my code with zero extra API calls. For alarms, I'd use composite alarms to reduce noise — only page if BOTH error rate is high AND latency is elevated. I'd use anomaly detection for traffic patterns where static thresholds don't work. For logs, I'd use structured JSON with CloudWatch Logs Insights for fast querying. For traces, X-Ray automatically instruments AWS SDK calls and shows me the service map to find bottlenecks. The dashboard would show golden signals at the top, business metrics in the middle, and per-service breakdown at the bottom. I'd alert to SNS → Lambda → Slack for warnings and PagerDuty for pages."*

### Observability Stack Comparison

| Need | AWS Native | Open Source Alternative |
|------|-----------|------------------------|
| Metrics | **CloudWatch Metrics** | Prometheus + Grafana |
| Logs | **CloudWatch Logs** | ELK Stack (Elasticsearch + Kibana) |
| Traces | **AWS X-Ray** | Jaeger, Zipkin |
| Dashboards | **CloudWatch Dashboards** | Grafana |
| Alerting | **CloudWatch Alarms + SNS** | Prometheus Alertmanager |
| APM | **CloudWatch + X-Ray** | Datadog, New Relic |

> **AWS advantage**: Zero infrastructure to manage, native integration with all AWS services, IAM-based access control, and all telemetry in one place. Trade-off: less flexible query language than Prometheus/Grafana, and CloudWatch can be expensive at high metric volume.

---

> **Related Topics**: [Observability & Monitoring Deep Dive →](./45-observability-and-monitoring.md) | [Kafka Real-World →](./39b-kafka-real-world-applications.md) | [Redis Real-World →](./40b-redis-real-world-applications.md)
