# 🎯 Topic 42: Job Scheduling

> **System Design Interview — Deep Dive**
> A comprehensive guide covering distributed job scheduling, cron-based vs event-driven jobs, leader-based scanners, delayed job queues, retry strategies, idempotency, job deduplication, priority queues, and how to articulate job scheduling decisions in system design interviews.

---

## Table of Contents

- [🎯 Topic 42: Job Scheduling](#-topic-42-job-scheduling)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Types of Scheduled Jobs](#types-of-scheduled-jobs)
  - [Architecture: Leader-Based Job Scanner](#architecture-leader-based-job-scanner)
    - [The Standard Pattern](#the-standard-pattern)
    - [Database Schema](#database-schema)
  - [Delayed Job Queue (Redis Sorted Set)](#delayed-job-queue-redis-sorted-set)
  - [Job State Machine](#job-state-machine)
  - [Retry Strategies](#retry-strategies)
    - [Exponential Backoff with Jitter](#exponential-backoff-with-jitter)
    - [Retry Decision Matrix](#retry-decision-matrix)
  - [Idempotency — The Non-Negotiable Requirement](#idempotency--the-non-negotiable-requirement)
  - [Job Deduplication](#job-deduplication)
  - [Priority Queues](#priority-queues)
  - [Scaling Job Processing](#scaling-job-processing)
  - [Failure Handling and Dead Letter Queues](#failure-handling-and-dead-letter-queues)
  - [Real-World Examples](#real-world-examples)
    - [Amazon SQS + Lambda (Serverless Job Processing)](#amazon-sqs--lambda-serverless-job-processing)
    - [Airflow (Data Pipeline Scheduling)](#airflow-data-pipeline-scheduling)
    - [Celery (Python Task Queue)](#celery-python-task-queue)
  - [Deep Dive: Common Job Scheduling Problems](#deep-dive-common-job-scheduling-problems)
    - [Email Notification Scheduler](#email-notification-scheduler)
    - [Billing Cycle Processing](#billing-cycle-processing)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [Architecture](#architecture)
    - [Exactly-Once Execution](#exactly-once-execution)
  - [Common Interview Mistakes](#common-interview-mistakes)
    - [❌ Mistake 1: Using cron on a single machine](#-mistake-1-using-cron-on-a-single-machine)
    - [❌ Mistake 2: Not mentioning idempotency](#-mistake-2-not-mentioning-idempotency)
    - [❌ Mistake 3: No retry strategy](#-mistake-3-no-retry-strategy)
    - [❌ Mistake 4: Not separating scanning from execution](#-mistake-4-not-separating-scanning-from-execution)
  - [Summary Cheat Sheet](#summary-cheat-sheet)
  - [Related Topics](#related-topics)

---

## Core Concept

A **job scheduler** is a system that executes tasks at specified times or in response to events. In distributed systems, the challenge is ensuring jobs run **exactly once**, **on time**, and **survive failures** across a fleet of workers.

```
Single-machine cron:
  */5 * * * * python send_reports.py  → Runs every 5 minutes on ONE machine
  Problem: If machine dies → no reports. No retry. No monitoring. No scaling.

Distributed job scheduler:
  Job definition: {id: "daily_report", schedule: "0 3 * * *", handler: "ReportService.generate"}
  Stored in: Database (PostgreSQL/DynamoDB)
  Scanned by: Leader node (elected via ZooKeeper)
  Executed by: Stateless worker pool (pulled from Redis/SQS queue)
  Monitored: Dashboard + alerts for failures, timeouts, SLA breaches
  
  If worker dies → leader detects timeout → reassigns job to another worker
  If leader dies → new leader elected → resumes scanning within 30 seconds
```

---

## Types of Scheduled Jobs

| Type | Trigger | Example | Latency Tolerance |
|---|---|---|---|
| **Cron/Periodic** | Fixed schedule | Daily reconciliation at 3 AM | Minutes |
| **Delayed** | Run after X time | Send reminder email 24 hours after signup | Seconds |
| **Event-driven** | Triggered by event | Process payment after order placed | Milliseconds |
| **Recurring** | After completion, schedule next | Poll API every 30 seconds | Seconds |
| **One-time** | Run once at specific time | Schedule meeting reminder | Seconds |

```
Cron jobs: Leader scans job_schedule table every second
  SELECT * FROM jobs WHERE next_run_at <= NOW() AND status = 'PENDING'
  
Delayed jobs: Redis sorted set with execute_at as score
  ZADD delayed_jobs {execute_at_timestamp} {job_payload}
  Poll: ZRANGEBYSCORE delayed_jobs 0 {now} LIMIT 0 100
  
Event-driven: Kafka consumer or SQS trigger
  Order placed → Kafka "order-events" → Payment worker consumes
```

---

## Architecture: Leader-Based Job Scanner

### The Standard Pattern

```
┌─────────────────────────────────────────┐
│  Leader (elected via ZooKeeper/etcd)    │
│  ────────────────────────────────────── │
│  Every 1 second:                        │
│    Scan job_schedule table              │
│    Find due jobs (next_run_at <= NOW()) │
│    Create job_execution records         │
│    Push to work queue (Redis/SQS)       │
│    Update next_run_at for recurring     │
│    Monitor stuck jobs (timeout check)   │
└─────────────┬───────────────────────────┘
              │ Job assignments via Redis queue
  ┌───────────┼──────────┬───────────┐
  ▼           ▼          ▼           ▼
┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐
│ W1  │   │ W2  │   │ W3  │   │ W4  │  (Stateless workers)
└─────┘   └─────┘   └─────┘   └─────┘
  BRPOP from queue → process → report completion

Leader responsibilities:
  1. Scan for due jobs (every 1 second)
  2. Claim jobs atomically (UPDATE SET status='ASSIGNED' WHERE status='PENDING')
  3. Push to work queue
  4. Monitor execution timeouts
  5. Handle retries for failed jobs
  6. Compute next_run_at for cron jobs

Why only ONE leader scans:
  If 3 nodes all scan simultaneously → each finds the same due jobs
  → Job pushed to queue 3 times → executed 3 times → DUPLICATE!
  Leader election ensures exactly one scanner.
```

### Database Schema

```sql
CREATE TABLE job_definitions (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    cron_expression TEXT,           -- "0 3 * * *" = daily at 3 AM
    handler TEXT NOT NULL,          -- "ReportService.generate"
    payload JSONB,                  -- {"report_type": "daily", "email": "team@"}
    timeout_seconds INT DEFAULT 300,
    max_retries INT DEFAULT 3,
    priority INT DEFAULT 5,         -- 1 = highest
    is_enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE TABLE job_executions (
    id UUID PRIMARY KEY,
    job_id UUID REFERENCES job_definitions(id),
    status TEXT DEFAULT 'PENDING',  -- PENDING → ASSIGNED → RUNNING → SUCCESS/FAILED
    scheduled_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    worker_id TEXT,
    attempt_number INT DEFAULT 1,
    error_message TEXT,
    fencing_token BIGINT,           -- Prevents stale leader writes
    created_at TIMESTAMPTZ
);

-- Index for leader scanning
CREATE INDEX idx_executions_due ON job_executions(scheduled_at) 
  WHERE status = 'PENDING';
```

---

## Delayed Job Queue (Redis Sorted Set)

```
Use Redis sorted set with timestamp as score:

Schedule a job (run in 24 hours):
  ZADD delayed_jobs 1742169600 '{"type":"reminder","user_id":"123"}'
  (score = Unix timestamp when job should execute)

Poll for due jobs (every 1 second):
  jobs = ZRANGEBYSCORE delayed_jobs 0 {current_time} LIMIT 0 100
  for job in jobs:
      ZREM delayed_jobs job      # Remove atomically
      LPUSH work_queue job       # Push to processing queue

Why Redis sorted set?
  O(log N) insert, O(log N + M) range query
  Millions of delayed jobs with sub-ms poll latency
  Atomic ZRANGEBYSCORE + ZREM prevents double-processing

Alternative: DynamoDB with TTL
  PK: job_id, SK: execute_at
  GSI: PK = "PENDING", SK = execute_at
  Query: GSI PK = "PENDING", SK <= NOW() → due jobs
```

---

## Job State Machine

```
PENDING → ASSIGNED → RUNNING → SUCCESS
                  ↘         ↘
                   FAILED → RETRY → PENDING (back to queue)
                              ↘
                          DEAD_LETTER (max retries exceeded)

State transitions:
  PENDING:    Job created, waiting for scanner to pick it up
  ASSIGNED:   Leader claimed it, pushed to work queue
  RUNNING:    Worker pulled from queue, currently processing
  SUCCESS:    Worker completed successfully, result stored
  FAILED:     Worker reported error (transient: retry, permanent: DLQ)
  RETRY:      Waiting for retry delay before re-entering PENDING
  DEAD_LETTER: Max retries exceeded, requires human investigation

Timeout handling:
  If job stays in RUNNING for > timeout_seconds:
    Leader marks as FAILED (assumed worker crashed)
    If retries remaining → schedule retry with backoff
    If max retries exceeded → move to DEAD_LETTER
```

---

## Retry Strategies

### Exponential Backoff with Jitter

```
retry_delay = min(base_delay × 2^attempt + random_jitter, max_delay)

Attempt 1: delay = 1s × 2^0 + jitter = ~1-2 seconds
Attempt 2: delay = 1s × 2^1 + jitter = ~2-4 seconds  
Attempt 3: delay = 1s × 2^2 + jitter = ~4-8 seconds
Attempt 4: delay = 1s × 2^3 + jitter = ~8-16 seconds
Attempt 5: delay = min(1s × 2^4, 300s) + jitter = ~16-32 seconds

Why jitter?
  Without: 1,000 failed jobs all retry at exactly 16 seconds → thundering herd
  With jitter: Spread across 16-32 seconds → smooth load
  
Max retries: 3-5 for most jobs (then DLQ)
Max delay cap: 5 minutes (don't wait too long)
```

### Retry Decision Matrix

```
Transient errors (RETRY):
  ✅ Network timeout → retry (temporary network issue)
  ✅ HTTP 429 (rate limited) → retry with longer backoff
  ✅ HTTP 503 (service unavailable) → retry
  ✅ Database connection timeout → retry
  
Permanent errors (DO NOT RETRY → DLQ):
  ❌ HTTP 400 (bad request) → won't succeed on retry
  ❌ HTTP 404 (not found) → resource doesn't exist
  ❌ Validation error → data is wrong, not infrastructure
  ❌ HTTP 401/403 (auth error) → credentials wrong

Application must classify errors:
  try:
      process(job)
  except TransientError:
      return RETRY
  except PermanentError:
      return DEAD_LETTER
```

---

## Idempotency — The Non-Negotiable Requirement

```
Jobs WILL be executed more than once due to:
  - Worker crashes after processing, before acknowledging
  - Network timeout causes leader to reassign
  - At-least-once queue delivery

Every job handler MUST be idempotent:

❌ Non-idempotent:
  def send_email(job):
      email_service.send(job.to, job.subject, job.body)
  → Called twice = user gets 2 emails!

✅ Idempotent:
  def send_email(job):
      if db.exists(sent_emails, job.idempotency_key):
          return SUCCESS  # Already sent, skip
      email_service.send(job.to, job.subject, job.body)
      db.insert(sent_emails, job.idempotency_key)
      return SUCCESS
  → Called twice = user gets 1 email ✓

Idempotency key: Unique identifier for this specific job execution
  Format: job_id + scheduled_at + attempt_number
  Or: Hash of job parameters (deterministic dedup)
```

---

## Job Deduplication

```
Prevent the same job from being scheduled twice:

Scenario: Leader scans at T=1, finds job 123 due, pushes to queue.
  At T=2: Leader scans again, job 123 still shows as "PENDING" (worker hasn't started yet).
  → Pushes to queue AGAIN → job 123 executed twice!

Solution: Atomic claim with status update

  UPDATE job_executions 
  SET status = 'ASSIGNED', worker_id = NULL, fencing_token = {token}
  WHERE id = {job_id} AND status = 'PENDING'
  RETURNING id;
  
  If 0 rows affected → already claimed by previous scan → skip.
  Atomic: Only one scan cycle can claim the job.

Alternative: Redis lock per job
  SET lock:job:{id} "scanning" NX EX 60
  If SET returns OK → this scan owns it → push to queue
  If nil → already being processed → skip
```

---

## Priority Queues

```
Multiple priority levels for different job types:

  Priority 1 (Critical): Payment processing, fraud alerts
  Priority 5 (Normal): Email notifications, report generation
  Priority 10 (Low): Analytics aggregation, cleanup tasks

Implementation with Redis:
  LPUSH queue:priority:1 {job}   (critical)
  LPUSH queue:priority:5 {job}   (normal)
  LPUSH queue:priority:10 {job}  (low)

  Worker poll order:
    job = BRPOP queue:priority:1 queue:priority:5 queue:priority:10 30
    → Redis tries queues left to right → critical jobs always processed first

  Starvation prevention:
    If low-priority queue backs up → alert
    Dedicate some workers exclusively to low-priority queue
    Or: Weighted fair queuing (process 5 critical, 3 normal, 1 low per cycle)
```

---

## Scaling Job Processing

```
Horizontal scaling — add more workers:

  10 workers → 100 jobs/sec throughput
  50 workers → 500 jobs/sec throughput
  
  Workers are stateless → just add more instances
  Auto-scale based on queue depth:
    Queue depth > 1,000 for 5 minutes → add 10 workers
    Queue depth < 100 for 10 minutes → remove 5 workers

Vertical scaling of scanner:
  Leader scans 10,000 jobs/sec from database
  If > 10,000 due jobs/sec → scan becomes bottleneck
  
  Solution: Partition scanning
    Leader 1: Scans jobs WHERE id % 4 = 0
    Leader 2: Scans jobs WHERE id % 4 = 1
    Leader 3: Scans jobs WHERE id % 4 = 2
    Leader 4: Scans jobs WHERE id % 4 = 3
    → 4 leaders, each scanning 1/4 of jobs, no overlap
```

---

## Failure Handling and Dead Letter Queues

```
DLQ: Where jobs go to die (gracefully):

After max retries (3-5 attempts):
  Job moved to dead_letter_queue table/topic
  Alert sent to on-call: "Job {id} failed permanently after 5 attempts"
  
  DLQ record:
    {job_id, original_payload, error_messages: [...], attempts: 5,
     first_failure: T1, last_failure: T5, handler: "PaymentService.charge"}

DLQ processing:
  Engineer investigates: Bug in handler? External service down?
  Fix the issue → replay from DLQ:
    Read from dead_letter_queue → push back to main queue → reprocess
  
  Some systems auto-retry DLQ jobs daily (in case external service recovered)
```

---

## Real-World Examples

### Amazon SQS + Lambda (Serverless Job Processing)

```
SQS queue → Lambda function (auto-invoked per message)
  Scale: 0 to 1,000 concurrent Lambda invocations automatically
  No workers to manage. Pay per invocation.
  DLQ: SQS dead-letter queue after maxReceiveCount
```

### Airflow (Data Pipeline Scheduling)

```
DAG-based job scheduling:
  Task A → Task B → Task C (dependencies)
  Retry policies per task
  Backfill: Re-run past failed tasks
  UI: Visual DAG execution monitoring
  Use: ETL pipelines, ML training, report generation
```

### Celery (Python Task Queue)

```
Python → Celery → Redis/RabbitMQ → Workers
  @app.task(bind=True, max_retries=3, default_retry_delay=60)
  def send_email(self, to, subject, body):
      try:
          email_service.send(to, subject, body)
      except TransientError as e:
          self.retry(exc=e)
```

---

## Deep Dive: Common Job Scheduling Problems

### Email Notification Scheduler

```
"Send reminder email 24 hours after user signs up"

  User signs up at 14:30 March 15
  → Schedule job: ZADD delayed_jobs {March 16 14:30 UTC} '{"user":"123","type":"reminder"}'
  
  Scanner polls every 1 second
  At March 16 14:30: Job becomes due → pushed to email worker queue
  Worker: Check if user is still active → send reminder email
  
  Idempotency: If sent, store in sent_reminders table → skip on re-delivery
```

### Billing Cycle Processing

```
"Process monthly billing for 10M customers on the 1st of each month"

  Job: Cron "0 0 1 * *" → runs at midnight on 1st of each month
  
  But: 10M customers can't be processed in one job!
  
  Fan-out pattern:
    Master job → creates 10,000 sub-jobs (1,000 customers each)
    Each sub-job: Process 1,000 customers (generate invoice, charge card)
    
    LPUSH billing_queue '{"customer_range": "0-999"}'
    LPUSH billing_queue '{"customer_range": "1000-1999"}'
    ...
    
    100 workers × 100 customers/sec = 10,000 customers/sec
    10M customers / 10K/sec = ~17 minutes total
```

---

## Interview Talking Points & Scripts

### Architecture

> *"I'd use a leader-based job scheduler. A leader node (elected via ZooKeeper) scans the job_schedule table every second for due jobs. When it finds one, it atomically claims it (UPDATE SET status='ASSIGNED' WHERE status='PENDING') and pushes to a Redis work queue. Stateless workers pull jobs via BRPOP, process them, and report completion. If a worker crashes, the leader detects the timeout and reassigns. If the leader crashes, ZooKeeper elects a new one in ~30 seconds — in-progress jobs continue unaffected."*

### Exactly-Once Execution

> *"Jobs will inevitably be delivered more than once — worker crashes, network timeouts, leader reassignment. Every job handler must be idempotent. I'd use an idempotency key (job_id + scheduled_at) and check a dedup table before processing. If the key exists, skip. If not, process and record the key. This guarantees exactly-once semantics at the application level, even with at-least-once queue delivery."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using cron on a single machine
**Better**: Leader-based distributed scheduler with worker pool. Single-machine cron has no HA.

### ❌ Mistake 2: Not mentioning idempotency
**Better**: "Every handler is idempotent. Duplicate delivery produces the same result."

### ❌ Mistake 3: No retry strategy
**Better**: "Exponential backoff with jitter: 1s, 2s, 4s, 8s, then DLQ after 5 attempts."

### ❌ Mistake 4: Not separating scanning from execution
**Better**: "Leader scans and assigns. Workers execute. Separation of concerns — if workers are slow, scanning isn't affected."

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                JOB SCHEDULING CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ARCHITECTURE: Leader (scanner) + Stateless Workers (pool)   │
│  LEADER: ZooKeeper-elected, scans job table every 1 second   │
│  QUEUE: Redis (BRPOP) or SQS for job distribution            │
│  WORKERS: Stateless, pull from queue, report completion      │
│                                                              │
│  DELAYED JOBS: Redis sorted set (score = execute_at timestamp)│
│  CRON JOBS: Leader scans WHERE next_run_at <= NOW()          │
│  EVENT JOBS: Kafka consumer or SQS trigger                   │
│                                                              │
│  STATE MACHINE: PENDING → ASSIGNED → RUNNING → SUCCESS/FAILED│
│  RETRIES: Exponential backoff + jitter (1s, 2s, 4s, 8s)     │
│  MAX RETRIES: 3-5 → then Dead Letter Queue                  │
│  IDEMPOTENCY: MANDATORY. Check dedup table before processing.│
│                                                              │
│  PRIORITY: Multiple Redis queues, BRPOP in priority order    │
│  SCALING: Add workers for throughput. Partition scanning      │
│    for scanner throughput.                                   │
│                                                              │
│  FAILOVER: Leader dies → new leader in 30s. Workers die →    │
│    timeout → reassign. In-progress jobs: unaffected.         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 32: Leader Election** — ZooKeeper for job scheduler leader
- **Topic 40: Redis Deep Dive** — Sorted sets for delayed jobs, BRPOP for queues
- **Topic 22: Delivery Guarantees** — At-least-once + idempotent handlers
- **Topic 36: Distributed Transactions** — Saga pattern for multi-step jobs

---

*This document is part of the System Design Interview Deep Dive series.*
