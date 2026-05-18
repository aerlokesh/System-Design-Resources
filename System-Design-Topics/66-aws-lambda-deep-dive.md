# 🎯 Topic 66: AWS Lambda Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering AWS Lambda — serverless compute, cold starts, execution model, event sources, concurrency controls, Lambda@Edge, Provisioned Concurrency, Lambda + API Gateway patterns, Step Functions orchestration, cost model, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Execution Model — How Lambda Works](#execution-model--how-lambda-works)
3. [Cold Starts — The Key Challenge](#cold-starts--the-key-challenge)
4. [Event Sources and Triggers](#event-sources-and-triggers)
5. [Concurrency Model](#concurrency-model)
6. [Lambda + API Gateway](#lambda--api-gateway)
7. [Lambda + SQS/Kinesis (Polling)](#lambda--sqskinesis-polling)
8. [Lambda + S3 Events](#lambda--s3-events)
9. [Step Functions — Orchestration](#step-functions--orchestration)
10. [Lambda@Edge and CloudFront Functions](#lambdaedge-and-cloudfront-functions)
11. [Provisioned Concurrency](#provisioned-concurrency)
12. [VPC and Database Access](#vpc-and-database-access)
13. [Limits and Constraints](#limits-and-constraints)
14. [Cost Model and Optimization](#cost-model-and-optimization)
15. [Lambda vs ECS/Fargate vs EC2](#lambda-vs-ecsfargate-vs-ec2)
16. [Real-World Production Patterns](#real-world-production-patterns)
17. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
18. [Common Interview Mistakes](#common-interview-mistakes)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**AWS Lambda** is a serverless compute service that runs code in response to events without provisioning or managing servers. You upload code, define triggers, and Lambda handles scaling from zero to thousands of concurrent executions automatically.

```
The mental model:

  Event occurs → Lambda invoked → Code runs → Result returned → Lambda scales to zero
  
  No servers. No scaling configuration. No idle cost.
  Pay ONLY for execution time (per ms) and requests.

  ┌──────────┐     trigger     ┌──────────────┐     ┌──────────────┐
  │ Event     │───────────────→│   Lambda      │────→│  Destination  │
  │ Source    │                │   Function    │     │  (DB, S3, ..) │
  │           │                │   (your code) │     │               │
  └──────────┘                └──────────────┘     └──────────────┘
  
  Event sources: API Gateway, SQS, Kinesis, S3, DynamoDB Streams,
                 CloudWatch Events, SNS, Cognito, IoT, Step Functions...
```

### When to Use Lambda

```
USE Lambda when:
  ✅ Event-driven workloads (API calls, file uploads, queue processing)
  ✅ Sporadic/unpredictable traffic (scales to zero between requests)
  ✅ Short-lived tasks (<15 minutes)
  ✅ Don't want to manage servers/containers
  ✅ Glue logic between AWS services
  ✅ Cost matters at low-medium volume (cheaper than always-on)

DON'T USE Lambda when:
  ❌ Long-running processes (>15 min) → use ECS/Fargate
  ❌ Steady high throughput 24/7 → EC2/ECS is cheaper
  ❌ Need GPU → EC2 with GPU instances
  ❌ Need persistent connections (WebSocket server) → ECS/EC2
  ❌ Large deployment packages (>250 MB unzipped) → containers
  ❌ Sub-10ms latency required (cold starts add 100-500ms)
```

---

## Execution Model — How Lambda Works

### Invocation Lifecycle

```
1. EVENT arrives (API Gateway request, SQS message, S3 event)
2. Lambda Service checks: Is there a WARM execution environment?
   a. YES → Route request to warm environment → execute handler
   b. NO → COLD START:
      - Download code package from S3
      - Create execution environment (microVM via Firecracker)
      - Initialize runtime (Python, Node, Java, etc.)
      - Run initialization code (outside handler)
      - Execute handler function
3. Handler runs → returns result
4. Environment stays WARM for ~5-15 minutes (reused for next invocation)
5. If no invocations for ~15 min → environment destroyed (scale to zero)
```

### Execution Environment

```
Each Lambda execution environment:
  - Isolated microVM (Firecracker) — security boundary
  - Dedicated CPU, memory, /tmp storage
  - Persists between invocations (warm reuse)
  - NOT shared between different functions
  
Resources:
  Memory: 128 MB to 10,240 MB (10 GB)
  CPU: Proportional to memory (1792 MB ≈ 1 vCPU, 10240 MB ≈ 6 vCPUs)
  /tmp storage: 512 MB (configurable up to 10 GB)
  Timeout: 1 second to 15 minutes
  
Runtime: Python, Node.js, Java, Go, .NET, Ruby, or custom runtime (any language)
```

---

## Cold Starts — The Key Challenge

### What Causes Cold Starts

```
Cold start occurs when:
  1. First invocation (no environment exists)
  2. Burst of traffic (new environments needed in parallel)
  3. Code/config update (old environments invalidated)
  4. Long idle period (environment recycled after ~15 min)

Cold start duration:
  Python/Node.js:     100-300ms (interpreted, fast startup)
  Go/Rust:            50-100ms  (compiled, minimal runtime)
  Java/.NET:          500-2000ms (JVM/CLR startup, class loading)
  Java + VPC:         1000-5000ms (VPC ENI attachment adds time)
  
  With Provisioned Concurrency: 0ms (pre-warmed environments)
```

### Cold Start Mitigation

```
1. Provisioned Concurrency:
   Keep N environments pre-warmed at all times.
   Cost: Pay for idle time ($0.000004646/GB-second when provisioned but not used).
   Use for: Latency-sensitive APIs, Java functions.

2. Minimize package size:
   Smaller deployment package → faster download → faster cold start.
   Use layers for shared dependencies.
   Tree-shake unused code.
   
3. Lazy initialization:
   Don't initialize everything in the handler's init phase.
   Lazy-load heavy SDKs only when needed.

4. Choose interpreted runtimes for latency-sensitive paths:
   Python/Node.js: 100-300ms cold start (acceptable for many APIs)
   Java: 500-2000ms (use Provisioned Concurrency or GraalVM native image)

5. Keep functions warm (anti-pattern but works):
   CloudWatch Events: Invoke every 5 minutes to prevent recycling.
   Not recommended — Provisioned Concurrency is the proper solution.
```

---

## Event Sources and Triggers

### Synchronous Invocation (Request-Response)

```
Caller WAITS for Lambda to complete and return a response.

Sources:
  - API Gateway → Lambda (HTTP request → response)
  - ALB → Lambda (HTTP)
  - CloudFront (Lambda@Edge)
  - Cognito (custom auth logic)
  - SDK direct invoke (InvocationType="RequestResponse")

Flow:
  Client → API Gateway → Lambda → Response → Client
  Latency: Cold start + function execution time
  Error: Caller sees the error (4xx/5xx) → can retry
```

### Asynchronous Invocation (Fire-and-Forget)

```
Caller sends event and doesn't wait for completion.
Lambda queues internally and processes asynchronously.

Sources:
  - S3 event notifications
  - SNS
  - CloudWatch Events / EventBridge
  - SDK direct invoke (InvocationType="Event")

Flow:
  S3 upload → Lambda service queues event → processes when ready
  Retries: 2 automatic retries on failure (configurable)
  DLQ: Failed events → SQS DLQ or SNS topic
  
  Caller gets 202 Accepted immediately (doesn't wait for result).
```

### Poll-Based Invocation (Stream/Queue Processing)

```
Lambda SERVICE polls the source and invokes your function with a batch.

Sources:
  - SQS (Standard and FIFO)
  - Kinesis Data Streams
  - DynamoDB Streams
  - Kafka (MSK)

Flow:
  Lambda service polls SQS → gets batch of messages → invokes your function
  Your function processes the batch → success = messages deleted
  Failure → messages return to queue (visibility timeout) → retry
  
  You don't manage polling code. Lambda handles it.
  Concurrency = number of shards/partitions (for Kinesis/DynamoDB)
  Concurrency = scales with queue depth (for SQS)
```

---

## Concurrency Model

### How Concurrency Works

```
Concurrency = number of simultaneous Lambda executions.

  1 request at a time → 1 concurrent execution
  100 simultaneous requests → 100 concurrent executions
  Each execution has its own environment (isolated)

Account-level limit: 1000 concurrent executions (default, can request increase to 10K+)
Function-level reserved: Set per-function (e.g., "this function can use max 100")

  Account total: 1000
  Function A reserved: 200 → guaranteed 200, max 200
  Function B reserved: 100 → guaranteed 100, max 100
  Unreserved pool: 700 → shared among all other functions
```

### Burst Limits

```
Initial burst: 500-3000 concurrent executions immediately (region-dependent)
After burst: Scale up by 500 additional per minute

Example:
  T=0:   0 concurrent → suddenly 5000 requests arrive
  T=0:   Lambda scales to 3000 immediately (burst limit)
  T=1min: Scales to 3500 (+500)
  T=2min: Scales to 4000 (+500)
  T=5min: Reaches 5000 (serves all requests)
  
  Requests during ramp-up that exceed concurrency → throttled (429) or queued
```

---

## Lambda + API Gateway

### REST API Pattern

```
┌────────┐     HTTP      ┌──────────────┐     invoke    ┌──────────┐
│ Client  │─────────────→│ API Gateway   │─────────────→│ Lambda    │
└────────┘               │ (REST API)    │              │ Function  │
                         └──────────────┘              └──────────┘

API Gateway handles:
  - Request routing (/users/{id} → UserFunction)
  - Authentication (JWT, IAM, Cognito)
  - Rate limiting (throttle per API key)
  - Request/response transformation
  - CORS headers
  - Caching (optional)

Lambda handles:
  - Business logic
  - Database queries
  - Response generation
```

### Function URL (Simpler Alternative)

```
Lambda Function URL: Direct HTTPS endpoint for a Lambda function.
  No API Gateway needed. Simpler + cheaper for simple use cases.
  
  URL: https://abcdef123456.lambda-url.us-east-1.on.aws/
  
  Use: Simple webhooks, internal APIs, cost-sensitive endpoints.
  Missing: No rate limiting, no auth (built-in), no request transformation.
```

---

## Lambda + SQS/Kinesis (Polling)

### SQS Integration

```
Lambda polls SQS → receives batch → invokes function → deletes on success.

Configuration:
  BatchSize: 1-10,000 messages per invocation
  MaximumBatchingWindowInSeconds: 0-300 (wait up to 5 min to fill batch)
  FunctionResponseTypes: ["ReportBatchItemFailures"] (report which messages failed)
  
  Concurrency: Lambda auto-scales based on queue depth.
    Queue depth 1000, batch size 10 → ~100 concurrent executions.
    Scales up as queue grows, down as queue drains.

Error handling:
  Entire batch fails → messages return to queue (visibility timeout)
  Partial failure (with ReportBatchItemFailures) → only failed messages return
  After maxReceiveCount → DLQ
```

### Kinesis Integration

```
Lambda polls Kinesis shards → invokes function per shard.

Configuration:
  BatchSize: 1-10,000 records
  ParallelizationFactor: 1-10 (concurrent batches per shard)
  BisectBatchOnFunctionError: true (split on failure to isolate bad records)
  MaximumRetryAttempts: 0-10,000
  OnFailure.Destination: SQS DLQ ARN
  
  Concurrency = number_of_shards × parallelization_factor
    10 shards × factor 5 = 50 concurrent Lambda executions max
```

---

## Step Functions — Orchestration

### When Lambda Isn't Enough Alone

```
Problem: Complex workflows with multiple steps, branching, retries, waiting.
  Lambda: Single function, max 15 minutes.
  Step Functions: Orchestrate multiple Lambdas into a workflow.

Example: Order processing workflow
  1. Validate order (Lambda, 2 sec)
  2. Check inventory (Lambda, 1 sec)
  3. If in stock → charge payment (Lambda, 3 sec) → ship (Lambda, 2 sec)
  4. If not in stock → notify customer (Lambda, 1 sec)
  5. Wait 24 hours → follow up email (Lambda)

Step Functions handles:
  - Sequencing, branching, parallel execution
  - Retries with backoff per step
  - Waiting (Wait state: up to 1 year)
  - Error handling and compensation
  - Visual workflow monitoring
```

---

## Lambda@Edge and CloudFront Functions

```
Lambda@Edge: Run Lambda at CloudFront edge locations (200+ globally).
  Trigger: CloudFront events (viewer-request, origin-request, etc.)
  Use: URL rewriting, A/B testing, authentication at edge, geo-routing.
  Limits: 5s timeout (viewer), 30s (origin). 128 MB memory max.
  Runtime: Node.js, Python only.
  
CloudFront Functions: Even lighter-weight. 1ms execution.
  Use: Simple header manipulation, URL redirect, cache key normalization.
  Limits: 2 MB package, 10 KB response, JavaScript only.
  
When to use which:
  CloudFront Functions: Simple header/URL manipulation (cheapest, fastest)
  Lambda@Edge: Complex logic at edge (auth, A/B test, SSR)
  Regular Lambda: Everything else (not at edge)
```

---

## Provisioned Concurrency

```
Problem: Cold starts add 100-2000ms latency for the first request.
Solution: Keep N environments pre-warmed and ready.

Configuration:
  aws lambda put-provisioned-concurrency-config \
    --function-name MyFunction \
    --provisioned-concurrent-executions 100

  100 environments are always warm. No cold starts for the first 100 concurrent requests.
  Request 101+ → uses on-demand (may cold start).

Cost:
  Provisioned: $0.000004646/GB-second (for being ready, even if not executing)
  + Standard execution cost when actually running
  
  Example: 100 provisioned × 1 GB memory × 3600 sec/hour = $1.67/hour = $1,220/month
  
Use when:
  ✅ API with strict latency requirements (<100ms p99)
  ✅ Java/.NET functions (high cold start time)
  ✅ Predictable traffic patterns (provision for expected load)
  
  Auto Scaling: Schedule based (provision more during business hours).
```

---

## VPC and Database Access

### Lambda in VPC

```
Problem: Lambda needs to access RDS/ElastiCache/internal services in a VPC.
Solution: Configure Lambda to run inside the VPC.

How it works:
  Lambda creates an ENI (Elastic Network Interface) in your VPC subnet.
  Function can access VPC resources (RDS, Redis, etc.).
  
Performance impact:
  BEFORE 2019: VPC cold start added 10-30 seconds (creating ENI on each cold start)
  AFTER 2019: AWS pre-creates ENIs ("Hyperplane" optimization). Additional cold start: ~1s.
  
  Still: Avoid VPC if you don't need it. Adds complexity + slight latency.

Internet access from VPC Lambda:
  Lambda in VPC → NO internet access by default
  Solution: NAT Gateway in public subnet → route internet traffic through it
  Or: VPC Endpoints for AWS services (S3, DynamoDB, SQS — no internet needed)
```

### Database Connections

```
Problem: Each Lambda invocation opens a DB connection.
  1000 concurrent Lambdas → 1000 DB connections → database overwhelmed!
  
Solution: RDS Proxy (see Topic 64 — Aurora Deep Dive)
  Lambda → RDS Proxy → Aurora/RDS
  RDS Proxy pools connections: 1000 Lambda connections → 50 DB connections.
```

---

## Limits and Constraints

```
Execution:
  Timeout: Max 15 minutes
  Memory: 128 MB to 10,240 MB
  /tmp storage: 512 MB to 10 GB
  Environment variables: 4 KB total
  
Deployment:
  Package size: 50 MB (zipped), 250 MB (unzipped)
  With container images: Up to 10 GB
  Layers: 5 per function, 250 MB total (unzipped)

Concurrency:
  Account default: 1000 concurrent (request increase to 10K+)
  Burst: 500-3000 immediate (region-dependent)
  Scale rate: +500/minute after burst

Payload:
  Synchronous: 6 MB request/response
  Asynchronous: 256 KB event payload
  
Network:
  Ephemeral storage: 512 MB - 10 GB
  No inbound connections (Lambda is always invoked, never accepts connections)
```

---

## Cost Model and Optimization

### Pricing

```
Lambda pricing (us-east-1):
  Requests: $0.20 per million
  Duration: $0.0000166667 per GB-second (first 6 billion GB-sec/month)
  
  Free tier: 1M requests + 400,000 GB-seconds per month

Example:
  10M requests/month, 500ms average duration, 512 MB memory:
  Requests: 10M × $0.20/M = $2.00
  Duration: 10M × 0.5s × 0.5 GB × $0.0000166667 = $41.67
  Total: ~$44/month

Comparison at steady load:
  Lambda: $44/month for bursty 10M requests
  EC2 (t3.medium always-on): ~$30/month — but serves ALL traffic at steady cost
  
  Break-even: ~3M requests/month at 500ms/512MB
  Below 3M: Lambda cheaper
  Above 3M steady: EC2/Fargate cheaper
```

### Cost Optimization

```
1. Right-size memory: More memory = more CPU = faster execution = less duration cost.
   Sometimes INCREASING memory REDUCES cost (faster execution time offsets higher per-ms rate).
   Use AWS Lambda Power Tuning tool.

2. Minimize cold starts: Provisioned Concurrency is expensive.
   Use only where latency is critical.

3. Batch processing: Process 100 SQS messages per invocation (not 1).
   Reduces invocation count by 100x.

4. Avoid VPC if not needed: VPC adds latency and ENI cost.

5. Use ARM (Graviton2): 20% cheaper AND 20% faster than x86.
   Change architecture to "arm64" → instant savings.
```

---

## Lambda vs ECS/Fargate vs EC2

| Aspect | Lambda | ECS/Fargate | EC2 |
|---|---|---|---|
| **Scaling** | Automatic (0 to thousands) | Auto-scaling (min=0 with Fargate) | Manual/ASG |
| **Cold start** | 100-2000ms | 30-60s (new task) | N/A (always running) |
| **Max duration** | 15 minutes | Unlimited | Unlimited |
| **Pricing** | Per ms + per request | Per second (always-on) | Per hour (always-on) |
| **Ops overhead** | Zero | Low (Fargate) / Medium (EC2) | High |
| **Best for** | Event-driven, bursty, short tasks | Steady workloads, long-running | Full control, GPU, persistent |
| **Max memory** | 10 GB | 120 GB | Terabytes |
| **Network** | No inbound. Invoke only. | Full networking (ports, LB) | Full networking |
| **Cost at low volume** | Cheapest (scales to zero) | More expensive (minimum tasks) | Most expensive (always-on) |
| **Cost at high volume** | Most expensive | Middle | Cheapest |

---

## Real-World Production Patterns

### Pattern 1: Serverless REST API

```
API Gateway → Lambda → DynamoDB (or Aurora via RDS Proxy)

  Each HTTP route maps to a Lambda function (or handler within one function).
  Auto-scales with traffic. Zero cost when idle.
  Typical: 50-100ms response time (warm), 300-500ms (cold start).
```

### Pattern 2: Event Processing Pipeline

```
S3 upload → Lambda (validate + transform) → SQS → Lambda (process) → DynamoDB

  File uploaded to S3 → triggers Lambda → validates file format
  → puts work item in SQS → second Lambda processes in batch
  → writes results to DynamoDB.
  
  Fully serverless. Scales with upload volume.
```

### Pattern 3: Scheduled Jobs (Cron)

```
EventBridge Schedule (every 5 min) → Lambda → Send digest email

  Replaces traditional cron. No server to maintain.
  If job takes >15 min: Use Step Functions + Lambda.
```

### Pattern 4: Real-Time Stream Processing

```
Kinesis → Lambda (enrich + filter) → Firehose → S3

  Kinesis streams clickstream events.
  Lambda enriches each batch (add user segment from DynamoDB).
  Firehose delivers enriched events to S3.
```

---

## Interview Talking Points & Scripts

### Script 1: When to Use Lambda

> *"I'd use Lambda for event-driven, bursty workloads under 15 minutes — API handlers, queue processors, file triggers, scheduled jobs. The key advantage: zero infrastructure management and scale-to-zero cost. For a startup API handling 1M requests/month, Lambda costs ~$5/month vs ~$30/month for a minimum EC2. But at steady high throughput (>3M requests/month), containers on Fargate become cheaper. Lambda is the default for event processing glue — S3 triggers, SQS consumers, scheduled tasks. I'd avoid it for long-running jobs (>15 min), WebSocket servers (need persistent connections), or sub-10ms latency requirements (cold starts)."*

### Script 2: Cold Starts

> *"Cold starts are Lambda's main weakness. Python/Node: 100-300ms. Java: 500-2000ms. For latency-sensitive APIs, I'd use Provisioned Concurrency — pre-warmed environments that eliminate cold starts entirely. For cost-sensitive scenarios, I'd use Python or Node (fast cold starts), minimize package size, and lazy-load heavy dependencies. Since 2019, VPC Lambda cold starts are manageable (~1s vs the old 10-30s). The key interview point: cold starts matter for synchronous APIs, but NOT for async event processing (SQS consumer doesn't care about 200ms extra latency)."*

### Script 3: Concurrency and Scaling

> *"Lambda auto-scales based on incoming events. Each concurrent request gets its own isolated execution environment. Account default: 1000 concurrent (increase to 10K+). Burst: up to 3000 immediate, then +500/minute. For SQS: concurrency scales with queue depth. For Kinesis: concurrency = shards × parallelization factor. I'd use reserved concurrency to protect critical functions from being starved by noisy neighbors, and set throttling limits on non-critical functions to prevent runaway costs."*

### Script 4: Lambda + Database

> *"Lambda + RDS is a common anti-pattern if done wrong. Each Lambda invocation opens a DB connection — at 1000 concurrent invocations, that's 1000 connections exceeding most database limits. Solution: RDS Proxy. It sits between Lambda and Aurora/RDS, pooling connections. 1000 Lambda connections → 50 actual DB connections. For DynamoDB: no connection issue (HTTP-based, stateless). For read-heavy workloads: DAX (DynamoDB cache) or ElastiCache in VPC with Lambda."*

### Script 5: Step Functions for Workflows

> *"For multi-step processes that exceed Lambda's 15-minute limit or need branching/error handling, I'd use Step Functions. Each step is a Lambda function. Step Functions handles sequencing, parallel execution, retries with backoff, waiting (up to 1 year), and compensation logic. Example: order processing — validate → check inventory → charge payment → if fails → refund → notify customer. Each step is independently retryable. The workflow state is managed by AWS — no database needed for tracking progress."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Lambda for everything"

**Bad**: Using Lambda for WebSocket servers, ML training, or 24/7 workloads.
**Fix**: Lambda for event-driven bursts. ECS/Fargate for steady workloads. EC2 for GPU/special hardware.

### ❌ Mistake 2: Ignoring cold starts

**Bad**: "Lambda adds zero latency to API calls."
**Fix**: Cold starts add 100-2000ms. Mitigate with Provisioned Concurrency or fast runtimes (Python/Node).

### ❌ Mistake 3: Direct DB connections from Lambda

**Bad**: "Lambda connects directly to RDS."
**Fix**: Use RDS Proxy to pool connections. Without it: 1000 concurrent Lambdas → 1000 DB connections → connection exhaustion.

### ❌ Mistake 4: Not mentioning the 15-minute limit

**Bad**: "Lambda will process this 2-hour video transcode."
**Fix**: 15-minute max timeout. For long tasks: use ECS/Fargate, or split into chunks with Step Functions.

### ❌ Mistake 5: Ignoring cost at scale

**Bad**: "Lambda is always cheapest."
**Fix**: Lambda is cheapest at low volume (pay per use). At high steady volume, containers or EC2 are cheaper. Break-even: ~3M requests/month for typical 500ms functions.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│                AWS LAMBDA DEEP DIVE — CHEAT SHEET                    │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  WHAT: Serverless compute. Event → Code → Result. No servers.       │
│  SCALING: Automatic 0 → 1000+ concurrent. Scale to zero on idle.   │
│                                                                      │
│  COLD STARTS:                                                        │
│    Python/Node: 100-300ms. Java: 500-2000ms.                        │
│    Fix: Provisioned Concurrency (pre-warmed, no cold start).        │
│                                                                      │
│  TRIGGERS:                                                           │
│    Sync: API Gateway, ALB (request-response)                        │
│    Async: S3, SNS, EventBridge (fire-and-forget)                    │
│    Polling: SQS, Kinesis, DynamoDB Streams (Lambda polls)           │
│                                                                      │
│  LIMITS:                                                             │
│    Timeout: 15 min. Memory: 128 MB - 10 GB. Package: 250 MB.       │
│    Concurrency: 1000 default (request increase).                    │
│    Payload: 6 MB sync, 256 KB async.                                │
│                                                                      │
│  DATABASE: Use RDS Proxy. Never connect Lambda directly to RDS.     │
│  VPC: Only if needed (adds ~1s cold start). Use VPC Endpoints.      │
│                                                                      │
│  STEP FUNCTIONS: Orchestrate multi-step workflows exceeding 15 min. │
│  LAMBDA@EDGE: Run at CloudFront PoPs (auth, A/B test, redirect).   │
│                                                                      │
│  COST: $0.20/M requests + $0.0000166667/GB-second.                  │
│    Cheapest at low volume. ECS/EC2 cheaper at high steady volume.   │
│    ARM (Graviton2): 20% cheaper + 20% faster. Always use ARM.      │
│                                                                      │
│  vs ECS: Lambda for bursts/events. ECS for steady/long-running.     │
│  vs EC2: Lambda for zero ops. EC2 for GPU/full control.             │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 52: Architecting on AWS** — Lambda in serverless architectures
- **Topic 59: Distributed Job Scheduling** — Lambda as job worker
- **Topic 63: Kinesis Deep Dive** — Lambda as Kinesis consumer
- **Topic 64: Aurora Deep Dive** — RDS Proxy for Lambda connections
- **Topic 65: SNS Deep Dive** — SNS triggering Lambda

---

*This document is part of the System Design Interview Deep Dive series.*
