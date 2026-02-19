# Job Scheduler (Cron) System Design - Hello Interview Framework

> **Question**: Design a distributed job scheduler that can handle high-throughput job processing (10,000+ jobs per second), support both scheduled (cron-based) and ad-hoc job execution, include retry mechanisms for failed jobs, and maintain execution history for up to one year.
>
> **Asked at**: Robinhood, DoorDash, NVIDIA, Microsoft, and 9+ more companies
>
> **Difficulty**: Hard | **Level**: Senior

## Table of Contents
- [Job Scheduler (Cron) System Design - Hello Interview Framework](#job-scheduler-cron-system-design---hello-interview-framework)
  - [Table of Contents](#table-of-contents)
  - [1️⃣ Requirements](#1️⃣-requirements)
    - [Functional Requirements](#functional-requirements)
      - [Core Requirements (P0)](#core-requirements-p0)
      - [Below the Line (Out of Scope)](#below-the-line-out-of-scope)
    - [Non-Functional Requirements](#non-functional-requirements)
    - [Capacity Estimation](#capacity-estimation)
      - [Traffic Estimates](#traffic-estimates)
      - [Storage Estimates](#storage-estimates)
      - [Memory Estimates](#memory-estimates)
      - [Infrastructure Estimates](#infrastructure-estimates)
  - [2️⃣ Core Entities](#2️⃣-core-entities)
    - [Entity Relationship Diagram](#entity-relationship-diagram)
    - [Entity 1: Job Definition](#entity-1-job-definition)
    - [Entity 2: Job Execution (History)](#entity-2-job-execution-history)
    - [Entity 3: Worker](#entity-3-worker)
    - [Entity 4: Scheduler Partition Assignment](#entity-4-scheduler-partition-assignment)
  - [3️⃣ API Design](#3️⃣-api-design)
    - [Authentication](#authentication)
    - [1. Create Job](#1-create-job)
    - [2. Get Job Details](#2-get-job-details)
    - [3. List Jobs](#3-list-jobs)
    - [4. Pause / Resume / Delete Job](#4-pause--resume--delete-job)
    - [5. Trigger Job Immediately (Ad-Hoc)](#5-trigger-job-immediately-ad-hoc)
    - [6. Get Execution History](#6-get-execution-history)
    - [7. Get Single Execution Details](#7-get-single-execution-details)
  - [4️⃣ Data Flow](#4️⃣-data-flow)
    - [Flow 1: Creating a Cron Job](#flow-1-creating-a-cron-job)
    - [Flow 2: Scheduler Triggers a Cron Job (Happy Path)](#flow-2-scheduler-triggers-a-cron-job-happy-path)
    - [Flow 3: Worker Executes a Job](#flow-3-worker-executes-a-job)
    - [Flow 4: Querying Execution History](#flow-4-querying-execution-history)
    - [Flow 5: Ad-Hoc Job Trigger](#flow-5-ad-hoc-job-trigger)
  - [5️⃣ High-Level Design](#5️⃣-high-level-design)
    - [Architecture Diagram](#architecture-diagram)
    - [Component Responsibilities](#component-responsibilities)
    - [Why This Architecture?](#why-this-architecture)
  - [6️⃣ Deep Dives](#6️⃣-deep-dives)
    - [Deep Dive 1: Separating Scheduling from Execution](#deep-dive-1-separating-scheduling-from-execution)
    - [Deep Dive 2: Handling the "Top-of-Minute" Thundering Herd](#deep-dive-2-handling-the-top-of-minute-thundering-herd)
    - [Deep Dive 3: At-Least-Once Delivery \& Idempotency](#deep-dive-3-at-least-once-delivery--idempotency)
    - [Deep Dive 4: Cron Expression Parsing \& Next-Run Calculation](#deep-dive-4-cron-expression-parsing--next-run-calculation)
    - [Deep Dive 5: Retry Strategy \& Dead Letter Queue](#deep-dive-5-retry-strategy--dead-letter-queue)
    - [Deep Dive 6: Hot/Warm/Cold History Storage (1-Year Retention)](#deep-dive-6-hotwarmcold-history-storage-1-year-retention)
    - [Deep Dive 7: Worker Lifecycle Management \& Heartbeats](#deep-dive-7-worker-lifecycle-management--heartbeats)
    - [Deep Dive 8: Stateless Scheduler \& Partition-Based Ownership](#deep-dive-8-stateless-scheduler--partition-based-ownership)
    - [Deep Dive 9: Priority Queues \& Job Starvation Prevention](#deep-dive-9-priority-queues--job-starvation-prevention)
    - [Deep Dive 10: Distributed Locking \& Exactly-Once Triggering](#deep-dive-10-distributed-locking--exactly-once-triggering)
    - [Deep Dive 11: Observability, Alerting \& SLA Monitoring](#deep-dive-11-observability-alerting--sla-monitoring)
    - [Deep Dive 12: Multi-Tenancy, Rate Limiting \& Fairness](#deep-dive-12-multi-tenancy-rate-limiting--fairness)
  - [📊 Summary: Key Trade-offs \& Decision Matrix](#-summary-key-trade-offs--decision-matrix)
  - [🏗️ Technology Stack Summary](#️-technology-stack-summary)
  - [🎯 Interview Talking Points](#-interview-talking-points)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Schedule Jobs**: Users should be able to schedule jobs to be executed immediately (ad-hoc), at a future date/time (one-time), or on a recurring schedule (cron expression, e.g., `"every day at 10:00 AM"`, `"*/5 * * * *"`).
2. **Monitor Job Status**: Users should be able to monitor the status of their jobs (PENDING, RUNNING, SUCCEEDED, FAILED, RETRYING).
3. **Execution History**: The system should maintain execution history for up to **one year**, searchable and filterable.
4. **Retry Failed Jobs**: The system should automatically retry failed jobs with configurable retry policies (max retries, backoff strategy).
5. **High Throughput**: The system must handle **10,000+ job executions per second**.

#### Below the Line (Out of Scope)
- Users should be able to cancel or reschedule jobs (mentioned as out of scope on the question page, but we will briefly address in the API).
- Workflow/DAG orchestration (chaining jobs with dependencies — that's Airflow territory).
- Job code packaging / deployment (the system triggers jobs, it doesn't host the code).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Throughput** | ≥ 10,000 jobs/sec sustained, 50K peak | Core requirement; bursty at top-of-minute |
| **Scheduling Accuracy** | Trigger within ±1 second of scheduled time | Cron users expect second-level precision |
| **Availability** | 99.99% (52 min downtime/year) | Missed schedules = missed SLAs |
| **Delivery Guarantee** | At-least-once execution | We must never silently drop a job |
| **Idempotency** | Supported via execution IDs | Workers must handle duplicate delivery |
| **Data Retention** | 1 year of execution history | Compliance, debugging, auditing |
| **Latency (ad-hoc)** | < 2 seconds from submission to worker start | Interactive use case |
| **Consistency** | Strong consistency for scheduling, eventual for history reads | A job must not be scheduled twice or lost |
| **Fault Tolerance** | No single point of failure; tolerate N-1 node failure in a cluster of N | Scheduler, queue, workers all must be redundant |

### Capacity Estimation

#### Traffic Estimates
```
Job Executions:
  Sustained:  10,000 jobs/sec
  Peak (top-of-minute burst): 50,000 jobs/sec (5x burst)
  Daily:      10,000 × 86,400 = 864 million jobs/day
  Monthly:    ~26 billion jobs/month

Job Creations (schedules):
  New/updated schedules: ~1,000/sec (much lower than executions)
  Most traffic is recurring triggers, not new registrations

History Queries:
  ~500 QPS (dashboards, debugging)
```

#### Storage Estimates
```
Per Job Execution Record:
  - execution_id:    16 bytes (UUID)
  - job_id:          16 bytes
  - status:           1 byte (enum)
  - scheduled_time:   8 bytes
  - started_at:       8 bytes
  - finished_at:      8 bytes
  - worker_id:       16 bytes
  - attempt_number:   4 bytes
  - result/error:   512 bytes (avg, truncated logs)
  - metadata:       256 bytes
  Total: ~845 bytes → round to 1 KB

Daily:   864M × 1 KB = 864 GB/day
Monthly: ~26 TB/month
Yearly:  ~312 TB/year (1-year retention)

Job Definition Storage (relatively small):
  1M active job definitions × 2 KB = 2 GB (negligible)
```

#### Memory Estimates
```
Active Job Queue (next 60 seconds of jobs):
  Peak: 50,000 jobs × 1 KB = 50 MB (trivial)

Worker Registry:
  10,000 workers × 512 bytes = 5 MB

In-flight Job Tracking:
  50,000 concurrent × 1 KB = 50 MB

Redis Total: ~1 GB (very manageable)
```

#### Infrastructure Estimates
```
Workers:
  10,000 jobs/sec, each worker handles ~50 jobs/sec
  → 200 workers minimum, 500 with headroom

Scheduler Nodes: 5-10 (partitioned by job ID range)
Queue Brokers (Kafka): 10-20 brokers, 100+ partitions
Database: Sharded across 10+ nodes for history writes
```

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────┐       ┌──────────────────┐       ┌──────────────────┐
│  Job          │ 1───* │  JobExecution     │ *───1 │  Worker          │
│  Definition   │       │  (History)        │       │                  │
├──────────────┤       ├──────────────────┤       ├──────────────────┤
│ job_id (PK)  │       │ execution_id (PK)│       │ worker_id (PK)   │
│ tenant_id    │       │ job_id (FK)      │       │ host / IP        │
│ name         │       │ worker_id (FK)   │       │ status           │
│ schedule     │       │ status           │       │ last_heartbeat   │
│ callback_url │       │ scheduled_time   │       │ capacity         │
│ payload      │       │ started_at       │       │ labels/tags      │
│ retry_policy │       │ finished_at      │       └──────────────────┘
│ priority     │       │ attempt_number   │
│ status       │       │ result / error   │
│ next_run_at  │       │ idempotency_key  │
│ created_at   │       └──────────────────┘
│ updated_at   │
└──────────────┘
```

### Entity 1: Job Definition
**Purpose**: Represents a task definition including its schedule, callback, and retry policy.

```sql
-- PostgreSQL (source of truth for job definitions)
CREATE TABLE job_definitions (
    job_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    
    -- Schedule
    schedule_type   VARCHAR(20) NOT NULL,  -- 'IMMEDIATE', 'ONE_TIME', 'CRON'
    cron_expression VARCHAR(100),          -- e.g., '*/5 * * * *'
    timezone        VARCHAR(50) DEFAULT 'UTC',
    scheduled_time  TIMESTAMPTZ,           -- For ONE_TIME jobs
    next_run_at     TIMESTAMPTZ,           -- Pre-computed next trigger time
    
    -- Execution Details
    callback_url    VARCHAR(2048) NOT NULL, -- HTTP endpoint to invoke
    callback_method VARCHAR(10) DEFAULT 'POST',
    headers         JSONB,                 -- Custom HTTP headers
    payload         JSONB,                 -- Body to send
    timeout_seconds INT DEFAULT 300,       -- 5 min default timeout
    
    -- Retry Policy
    max_retries     INT DEFAULT 3,
    retry_backoff   VARCHAR(20) DEFAULT 'EXPONENTIAL', -- LINEAR, EXPONENTIAL, FIXED
    retry_delay_ms  INT DEFAULT 1000,      -- Initial retry delay
    max_retry_delay_ms INT DEFAULT 60000,  -- Cap at 60 seconds
    
    -- Priority & Metadata
    priority        INT DEFAULT 5,          -- 1 (highest) - 10 (lowest)
    tags            TEXT[],
    status          VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, PAUSED, DELETED
    
    -- Audit
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    
    -- Partition assignment (which scheduler owns this)
    partition_id    INT NOT NULL
);

CREATE INDEX idx_jobs_next_run ON job_definitions (next_run_at)
    WHERE status = 'ACTIVE' AND schedule_type IN ('CRON', 'ONE_TIME');
CREATE INDEX idx_jobs_tenant ON job_definitions (tenant_id, status);
CREATE INDEX idx_jobs_partition ON job_definitions (partition_id, next_run_at)
    WHERE status = 'ACTIVE';
```

### Entity 2: Job Execution (History)
**Purpose**: Immutable log of every job execution attempt.

```sql
-- Primary store: Cassandra (write-optimized, time-series)
CREATE TABLE job_executions (
    job_id          UUID,
    execution_id    TIMEUUID,           -- Sortable, unique
    tenant_id       UUID,
    
    -- Timing
    scheduled_time  TIMESTAMP,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    
    -- Status
    status          TEXT,               -- PENDING, RUNNING, SUCCEEDED, FAILED, RETRYING, TIMED_OUT
    attempt_number  INT,
    
    -- Execution Details
    worker_id       UUID,
    idempotency_key TEXT,               -- job_id + scheduled_time hash
    callback_url    TEXT,
    http_status     INT,
    
    -- Results
    result          TEXT,               -- Truncated response (max 4KB)
    error_message   TEXT,
    error_type      TEXT,               -- TIMEOUT, HTTP_ERROR, CONNECTION_REFUSED, etc.
    
    -- Metadata
    duration_ms     INT,
    retry_of        TIMEUUID,           -- Points to original execution if this is a retry
    metadata        MAP<TEXT, TEXT>,
    
    PRIMARY KEY ((job_id), execution_id)
) WITH CLUSTERING ORDER BY (execution_id DESC)
  AND default_time_to_live = 31536000;  -- 1 year TTL (auto-delete)

-- Secondary table for querying by tenant
CREATE TABLE executions_by_tenant (
    tenant_id       UUID,
    day             DATE,               -- Partition by day for even distribution
    execution_id    TIMEUUID,
    job_id          UUID,
    job_name        TEXT,
    status          TEXT,
    scheduled_time  TIMESTAMP,
    duration_ms     INT,
    
    PRIMARY KEY ((tenant_id, day), execution_id)
) WITH CLUSTERING ORDER BY (execution_id DESC)
  AND default_time_to_live = 31536000;
```

### Entity 3: Worker
**Purpose**: Represents a worker node that executes jobs.

```sql
-- Redis Hash (real-time state, not persisted to disk long-term)
-- Key: worker:{worker_id}
{
    "worker_id": "w-abc123",
    "host": "10.0.1.42",
    "port": 8080,
    "status": "ACTIVE",            -- ACTIVE, DRAINING, DEAD
    "capacity": 50,                -- Max concurrent jobs
    "current_load": 23,            -- Currently executing
    "labels": ["gpu", "us-east-1"],
    "registered_at": "2025-01-01T00:00:00Z",
    "last_heartbeat": "2025-01-08T14:32:15Z",
    "version": "2.3.1"
}

-- Redis Sorted Set for quick capacity lookup
-- Key: workers:available
-- Score: available_capacity (capacity - current_load)
-- Member: worker_id
```

### Entity 4: Scheduler Partition Assignment
**Purpose**: Maps job partitions to scheduler nodes (who is responsible for triggering which jobs).

```sql
-- Stored in ZooKeeper / etcd for strong consistency
/scheduler/partitions/
    partition_0 → scheduler_node_1
    partition_1 → scheduler_node_2
    partition_2 → scheduler_node_1
    partition_3 → scheduler_node_3
    ...
    partition_63 → scheduler_node_5

-- Each job is assigned to a partition: partition_id = hash(job_id) % 64
```

---

## 3️⃣ API Design

### Authentication
```
Authorization: Bearer <JWT_TOKEN>
X-Tenant-ID: <tenant_id>
Content-Type: application/json
X-Idempotency-Key: <unique_key>  // For job creation dedup
```

### 1. Create Job
```
POST /api/v1/jobs

Request:
{
  "name": "Daily Sales Report",
  "description": "Generate and email daily sales report",
  "schedule": {
    "type": "CRON",                         // IMMEDIATE | ONE_TIME | CRON
    "cron_expression": "0 10 * * *",        // Every day at 10:00 AM
    "timezone": "America/New_York"
  },
  "execution": {
    "callback_url": "https://reports.internal/api/generate",
    "method": "POST",
    "headers": {
      "X-API-Key": "secret123"
    },
    "payload": {
      "report_type": "daily_sales",
      "format": "pdf"
    },
    "timeout_seconds": 600
  },
  "retry_policy": {
    "max_retries": 3,
    "backoff": "EXPONENTIAL",               // LINEAR | EXPONENTIAL | FIXED
    "initial_delay_ms": 1000,
    "max_delay_ms": 60000
  },
  "priority": 3,                            // 1 (highest) - 10 (lowest)
  "tags": ["reporting", "finance"]
}

Response (201 Created):
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Daily Sales Report",
  "status": "ACTIVE",
  "schedule": {
    "type": "CRON",
    "cron_expression": "0 10 * * *",
    "timezone": "America/New_York",
    "next_run_at": "2025-01-09T15:00:00Z"  // UTC equivalent
  },
  "created_at": "2025-01-08T14:30:00Z"
}
```

### 2. Get Job Details
```
GET /api/v1/jobs/{job_id}

Response (200 OK):
{
  "job_id": "550e8400-...",
  "name": "Daily Sales Report",
  "status": "ACTIVE",
  "schedule": {
    "type": "CRON",
    "cron_expression": "0 10 * * *",
    "timezone": "America/New_York",
    "next_run_at": "2025-01-09T15:00:00Z",
    "last_run_at": "2025-01-08T15:00:00Z"
  },
  "execution": { ... },
  "retry_policy": { ... },
  "stats": {
    "total_runs": 365,
    "success_rate": 0.987,
    "avg_duration_ms": 45000,
    "last_status": "SUCCEEDED"
  },
  "created_at": "2024-01-08T14:30:00Z",
  "updated_at": "2025-01-08T14:30:00Z"
}
```

### 3. List Jobs
```
GET /api/v1/jobs?status=ACTIVE&tag=reporting&page=1&limit=50

Response (200 OK):
{
  "jobs": [ ... ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 234,
    "next_cursor": "eyJqb2JfaWQiOi..."
  }
}
```

### 4. Pause / Resume / Delete Job
```
PATCH /api/v1/jobs/{job_id}/pause
PATCH /api/v1/jobs/{job_id}/resume
DELETE /api/v1/jobs/{job_id}

Response (200 OK):
{
  "job_id": "550e8400-...",
  "status": "PAUSED",
  "updated_at": "2025-01-08T15:00:00Z"
}
```

### 5. Trigger Job Immediately (Ad-Hoc)
```
POST /api/v1/jobs/{job_id}/trigger

Request (optional override):
{
  "payload_override": {
    "report_type": "daily_sales",
    "date": "2025-01-07"
  }
}

Response (202 Accepted):
{
  "execution_id": "a1b2c3d4-...",
  "job_id": "550e8400-...",
  "status": "PENDING",
  "scheduled_time": "2025-01-08T15:05:00Z"
}
```

### 6. Get Execution History
```
GET /api/v1/jobs/{job_id}/executions?status=FAILED&start_date=2025-01-01&end_date=2025-01-08&limit=100

Response (200 OK):
{
  "executions": [
    {
      "execution_id": "a1b2c3d4-...",
      "status": "FAILED",
      "attempt_number": 3,
      "scheduled_time": "2025-01-08T15:00:00Z",
      "started_at": "2025-01-08T15:00:01Z",
      "finished_at": "2025-01-08T15:00:12Z",
      "duration_ms": 11000,
      "error": {
        "type": "HTTP_ERROR",
        "message": "Callback returned 503 Service Unavailable",
        "http_status": 503
      },
      "worker_id": "w-xyz789"
    }
  ],
  "pagination": { ... }
}
```

### 7. Get Single Execution Details
```
GET /api/v1/executions/{execution_id}

Response (200 OK):
{
  "execution_id": "a1b2c3d4-...",
  "job_id": "550e8400-...",
  "status": "SUCCEEDED",
  "attempt_number": 1,
  "scheduled_time": "2025-01-08T15:00:00Z",
  "started_at": "2025-01-08T15:00:01Z",
  "finished_at": "2025-01-08T15:00:45Z",
  "duration_ms": 44000,
  "worker_id": "w-abc123",
  "idempotency_key": "550e8400-2025-01-08T15:00:00Z",
  "callback_response": {
    "http_status": 200,
    "body_preview": "{\"status\": \"report_generated\", \"url\": \"s3://...\"}"
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Creating a Cron Job
```
1. User calls: POST /api/v1/jobs
   ↓
2. API Gateway:
   - Authenticate JWT
   - Rate limit check (tenant-level)
   - Validate request schema
   ↓
3. Job Service:
   - Validate cron expression syntax
   - Compute next_run_at from cron + timezone
   - Assign partition_id = hash(job_id) % NUM_PARTITIONS
   ↓
4. Write to PostgreSQL:
   INSERT INTO job_definitions (job_id, ..., next_run_at, partition_id)
   ↓
5. Notify assigned Scheduler node (via Kafka or direct):
   "New job assigned to your partition, reload"
   ↓
6. Scheduler adds job to its in-memory priority queue
   (sorted by next_run_at)
   ↓
7. Return 201 Created to user
   Total latency: < 200ms
```

### Flow 2: Scheduler Triggers a Cron Job (Happy Path)
```
1. Scheduler Node polls its partition every 1 second:
   "Give me all jobs where next_run_at <= NOW()"
   (From in-memory priority queue, backed by DB)
   ↓
2. For each due job:
   a. Generate execution_id (TimeUUID)
   b. Compute idempotency_key = hash(job_id + scheduled_time)
   c. Create execution record: status = PENDING
   ↓
3. Enqueue to Kafka topic "job-executions":
   Partition key = job_id (ordering per job)
   {
     "execution_id": "...",
     "job_id": "...",
     "callback_url": "https://...",
     "payload": {...},
     "idempotency_key": "...",
     "priority": 3,
     "timeout_seconds": 600,
     "max_retries": 3
   }
   ↓
4. Update job_definitions:
   SET next_run_at = compute_next_cron_run(cron_expression, timezone)
   ↓
5. Scheduler moves to next job in queue
   ↓
   (Scheduler work for this tick is DONE — fast and lightweight)
```

### Flow 3: Worker Executes a Job
```
1. Worker pulls message from Kafka "job-executions" topic
   (Consumer group: "workers", many workers share partitions)
   ↓
2. Worker checks capacity:
   - If at max concurrent jobs → don't commit offset, pause partition
   - If capacity available → proceed
   ↓
3. Worker updates execution status → RUNNING:
   - Write to Cassandra: status = RUNNING, started_at = NOW(), worker_id = self
   - Update Redis: INCR worker:{id}:current_load
   ↓
4. Worker invokes callback URL:
   POST https://reports.internal/api/generate
   Headers: { X-Idempotency-Key: "...", X-Execution-ID: "..." }
   Body: { "report_type": "daily_sales" }
   Timeout: 600 seconds
   ↓
5a. SUCCESS (HTTP 2xx):
    - Update Cassandra: status = SUCCEEDED, finished_at, duration_ms, result
    - Commit Kafka offset
    - Decrement worker load
    - Emit metric: job.execution.success
    ↓
5b. FAILURE (HTTP 4xx/5xx, timeout, connection error):
    - Check attempt_number vs max_retries
    - If retries remaining:
      · Compute retry_delay (exponential backoff)
      · Re-enqueue to Kafka "job-retries" topic with delay
      · Update Cassandra: status = RETRYING
    - If max retries exceeded:
      · Update Cassandra: status = FAILED
      · Send to Dead Letter Queue (DLQ)
      · Emit alert if configured
    - Commit Kafka offset (original message processed)
    - Decrement worker load
```

### Flow 4: Querying Execution History
```
1. User calls: GET /api/v1/jobs/{job_id}/executions?status=FAILED
   ↓
2. API Gateway → History Service
   ↓
3. Recent data (last 7 days):
   - Query Cassandra job_executions table
   - Partition key = job_id, filter by status
   - Very fast: single partition read
   ↓
4. Older data (7 days - 1 year):
   - If in warm storage (Cassandra): direct query
   - If in cold storage (S3 + Athena): async query
   - Return partial results + "loading older data" indicator
   ↓
5. Return paginated results to user
   ↓
   Latency: < 100ms for recent, < 5s for cold
```

### Flow 5: Ad-Hoc Job Trigger
```
1. User calls: POST /api/v1/jobs/{job_id}/trigger
   ↓
2. Job Service:
   - Fetch job definition from PostgreSQL
   - Generate execution_id
   - Apply any payload_override
   ↓
3. Directly enqueue to Kafka "job-executions" (high-priority partition):
   - Priority: boosted (lower number = higher priority)
   - scheduled_time = NOW()
   ↓
4. Return 202 Accepted immediately (async execution)
   ↓
5. Worker picks up within < 2 seconds
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                            │
│   [Web Dashboard]    [CLI / SDK]    [Internal Services]    [Terraform/IaC]      │
└─────────────────────────────────┬───────────────────────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │    API Gateway / LB      │
                    │  • Auth (JWT / API Key)  │
                    │  • Rate Limiting         │
                    │  • Request Validation    │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                   ▼
   ┌──────────────────┐  ┌─────────────┐  ┌─────────────────┐
   │   Job Service     │  │  History    │  │   Admin         │
   │  (CRUD + Trigger) │  │  Service   │  │   Service       │
   │  • Create/Update  │  │  • Query   │  │  • Pause/Resume │
   │  • Validate Cron  │  │  • Search  │  │  • Bulk Ops     │
   │  • Ad-hoc Trigger │  │  • Export  │  │  • Metrics      │
   └───────┬──────────┘  └─────┬──────┘  └────────┬────────┘
           │                    │                   │
           ▼                    │                   │
   ┌──────────────────┐        │                   │
   │   PostgreSQL      │        │                   │
   │   (Job Defs)      │        │                   │
   │   • Master + 2    │        │                   │
   │     Read Replicas │        │                   │
   │   • Sharded by    │        │                   │
   │     tenant_id     │        │                   │
   └───────┬──────────┘        │                   │
           │                    │                   │
           │  (notify new/      │                   │
           │   changed jobs)    │                   │
           ▼                    │                   │
   ┌──────────────────────────────────────────────────────┐
   │              SCHEDULER CLUSTER                        │
   │  ┌────────────┐ ┌────────────┐ ┌────────────┐       │
   │  │ Scheduler  │ │ Scheduler  │ │ Scheduler  │       │
   │  │  Node 1    │ │  Node 2    │ │  Node 3    │ ...   │
   │  │ Partitions │ │ Partitions │ │ Partitions │       │
   │  │  0,3,6,9   │ │  1,4,7,10  │ │  2,5,8,11  │       │
   │  └─────┬──────┘ └─────┬──────┘ └─────┬──────┘       │
   │        │               │               │              │
   │        └───────────────┼───────────────┘              │
   │                        │                              │
   │  Coordination: ZooKeeper / etcd                       │
   │  • Partition assignment (consistent hashing)          │
   │  • Leader election for rebalancing                    │
   │  • Distributed locking for duplicate prevention       │
   └──────────────────────┬───────────────────────────────┘
                          │
                          │  Produce job execution messages
                          ▼
   ┌──────────────────────────────────────────────────────┐
   │              KAFKA CLUSTER                            │
   │                                                       │
   │  Topic: job-executions (100 partitions)               │
   │  Topic: job-retries   (50 partitions, delayed)        │
   │  Topic: job-dlq       (10 partitions, dead letters)   │
   │                                                       │
   │  10-20 brokers, RF=3, min.insync.replicas=2          │
   └──────────────────────┬───────────────────────────────┘
                          │
                          │  Consumer group: "workers"
                          ▼
   ┌──────────────────────────────────────────────────────┐
   │              WORKER POOL                              │
   │  ┌─────────┐ ┌─────────┐ ┌─────────┐               │
   │  │ Worker  │ │ Worker  │ │ Worker  │               │
   │  │  1      │ │  2      │ │  3      │  ... ×500     │
   │  │ 50 conc │ │ 50 conc │ │ 50 conc │               │
   │  └────┬────┘ └────┬────┘ └────┬────┘               │
   │       │            │            │                    │
   │  Heartbeats → Redis (worker registry)                │
   │  Status updates → Cassandra (execution history)      │
   │  HTTP callbacks → Target services                    │
   └──────────────────────┬───────────────────────────────┘
                          │
              ┌───────────┼───────────┐
              ▼           ▼           ▼
   ┌──────────────┐ ┌──────────┐ ┌────────────────┐
   │  Cassandra    │ │  Redis   │ │  Target        │
   │  (History)    │ │  Cluster │ │  Services      │
   │              │ │          │ │  (Callbacks)   │
   │  • Execution │ │ • Worker │ │                │
   │    records   │ │   registry│ │  POST /api/... │
   │  • 312 TB/yr │ │ • Locks  │ │  (user code)   │
   │  • TTL=1yr   │ │ • Counters│ │                │
   │  • 10+ nodes │ │ • 3 nodes│ │                │
   └──────┬───────┘ └──────────┘ └────────────────┘
          │
          │  (data > 30 days)
          ▼
   ┌──────────────┐
   │  S3 / Cold   │
   │  Storage     │
   │  • Parquet   │
   │  • Queryable │
   │    via Athena │
   │  • Cheapest  │
   │    tier      │
   └──────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling Strategy |
|-----------|---------|------------|------------------|
| **API Gateway** | Auth, rate limiting, routing | Kong / AWS ALB | Horizontal auto-scale |
| **Job Service** | CRUD operations for job definitions | Go / Java microservice | Stateless, horizontal |
| **Scheduler Cluster** | Trigger due jobs at correct time | Custom Go service | Partition-based (N nodes own disjoint partitions) |
| **Kafka** | Decouple scheduling from execution | Apache Kafka | Add brokers + partitions |
| **Worker Pool** | Execute HTTP callbacks | Go / Python workers | Auto-scale on queue depth |
| **PostgreSQL** | Job definitions (source of truth) | PostgreSQL 15+ | Master-replica, shard by tenant |
| **Cassandra** | Execution history (write-heavy, time-series) | Apache Cassandra | Add nodes linearly |
| **Redis** | Worker registry, locks, counters | Redis Cluster | 3-6 node cluster |
| **ZooKeeper / etcd** | Scheduler coordination, partition assignment | ZooKeeper 3.8 / etcd | 3 or 5 node ensemble |
| **S3 + Athena** | Cold history storage (> 30 days) | AWS S3 + Athena | Infinite, pay-per-query |

### Why This Architecture?

**Key Design Decision: Separate Scheduling from Execution**

The scheduler's only job is to determine *when* a job should run and enqueue it. The worker's only job is to *execute* the callback. This separation is critical because:

1. **Schedulers stay fast**: They only do time comparison + enqueue. No HTTP calls, no waiting on callbacks.
2. **Workers stay independent**: They don't care about cron parsing, just execute what's in the queue.
3. **Kafka acts as a buffer**: Absorbs the "top-of-minute" burst when thousands of cron jobs fire simultaneously.
4. **Each component scales independently**: Need more throughput? Add workers. Need more schedules? Add scheduler partitions.

---

## 6️⃣ Deep Dives

### Deep Dive 1: Separating Scheduling from Execution

**The Problem**: In a naive design, a single process parses cron expressions, determines due jobs, AND executes them. This creates a bottleneck — if a callback takes 30 seconds, the scheduler is blocked and misses other jobs.

**The Solution: Three-Layer Architecture**

```
Layer 1: SCHEDULING (Scheduler Cluster)
  Input:  Job definitions with next_run_at
  Output: Messages on Kafka topic
  Work:   Compare next_run_at <= NOW(), enqueue, update next_run_at
  Speed:  Can process 100,000 jobs/sec (just time comparison + Kafka produce)

Layer 2: QUEUING (Kafka)
  Input:  Job execution messages from schedulers
  Output: Messages consumed by workers
  Work:   Durable, ordered buffer with backpressure
  Speed:  Millions of messages/sec

Layer 3: EXECUTION (Worker Pool)
  Input:  Messages from Kafka
  Output: HTTP callbacks + status updates
  Work:   Make HTTP calls, handle timeouts, retries
  Speed:  Determined by callback latency × concurrency
```

**Why not use a database polling approach?**

```
Option A: Poll DB every second (naive)
  SELECT * FROM jobs WHERE next_run_at <= NOW() AND status = 'ACTIVE'
  
  Problems:
  - 1M active cron jobs → full table scan every second
  - Lock contention between scheduler replicas
  - DB becomes bottleneck at scale
  - Missed triggers if DB is slow

Option B: In-memory priority queue + DB backup (our approach)
  - Scheduler loads its partition's jobs into a min-heap (sorted by next_run_at)
  - Peek at top: O(1) to check if any job is due
  - Pop + enqueue: O(log N)
  - Periodic DB sync: reload every 60 seconds (catch new/changed jobs)
  
  Performance:
  - 100K jobs in heap: ~17 levels deep → pop in ~17 comparisons
  - Can process entire "top-of-minute" burst in milliseconds
```

**Scheduler Pseudocode**:
```java
public class SchedulerNode {
    private final List<Integer> partitions;
    private final PriorityQueue<JobDefinition> jobQueue; // min-heap sorted by nextRunAt
    private final JobRepository db;
    private final KafkaProducer<String, ExecutionMessage> kafka;
    private final CassandraClient cassandra;

    public SchedulerNode(List<Integer> partitionIds) {
        this.partitions = partitionIds;
        this.jobQueue = new PriorityQueue<>(Comparator.comparing(JobDefinition::getNextRunAt));
        loadJobsFromDb();
    }

    private void loadJobsFromDb() {
        // Load all active jobs for our partitions
        List<JobDefinition> jobs = db.findActiveJobsByPartitions(partitions);
        jobQueue.addAll(jobs);
    }

    /** Called every 500ms by the main scheduler loop */
    public void runTick() {
        Instant now = Instant.now();
        List<ExecutionMessage> batch = new ArrayList<>();

        while (!jobQueue.isEmpty() && !jobQueue.peek().getNextRunAt().isAfter(now)) {
            JobDefinition job = jobQueue.poll();

            // Generate execution message
            ExecutionMessage execution = ExecutionMessage.builder()
                .executionId(TimeUUID.generate())
                .jobId(job.getJobId())
                .callbackUrl(job.getCallbackUrl())
                .payload(job.getPayload())
                .idempotencyKey(generateIdempotencyKey(job.getJobId(), job.getNextRunAt()))
                .priority(job.getPriority())
                .timeoutSeconds(job.getTimeoutSeconds())
                .maxRetries(job.getMaxRetries())
                .attemptNumber(1)
                .build();
            batch.add(execution);

            // Compute next run and re-insert into heap
            if (job.getScheduleType() == ScheduleType.CRON) {
                Instant nextRun = CronUtils.computeNextRun(job.getCronExpression(), job.getTimezone());
                job.setNextRunAt(nextRun);
                jobQueue.add(job);
                // Async DB update (don't block the tick)
                db.asyncUpdateNextRunAt(job.getJobId(), nextRun);
            }
        }

        // Batch produce to Kafka (efficient)
        if (!batch.isEmpty()) {
            kafka.batchProduce("job-executions", batch);
            cassandra.batchInsertPending(batch);
        }
    }
}
```

---

### Deep Dive 2: Handling the "Top-of-Minute" Thundering Herd

**The Problem**: Many cron jobs are scheduled at round times: `"0 * * * *"` (top of every hour), `"*/5 * * * *"` (every 5 minutes), `"0 0 * * *"` (midnight). At the top of each minute, there can be a **5-10x spike** in jobs to trigger.

**Scale Example**:
```
Steady state: 10,000 jobs/sec
Top of minute: 50,000 jobs in the first 2-3 seconds
Top of hour:   100,000+ jobs in the first second
Midnight:      500,000+ jobs fire at 00:00:00
```

**Solution 1: Kafka as a Shock Absorber**

```
Schedulers can produce 500K messages to Kafka in < 2 seconds.
Workers consume at their own pace (backpressure).
Kafka retains messages until consumed — no data loss.

Kafka config for burst absorption:
  - 100 partitions on "job-executions" topic
  - batch.size = 65536 (batch messages for efficiency)
  - linger.ms = 5 (wait 5ms to batch)
  - buffer.memory = 256MB per producer
  - Consumer: max.poll.records = 500 (process in batches)
```

**Solution 2: Jittered Scheduling (Spread the Load)**

```java
public Instant computeNextCronWithJitter(String cronExpr, ZoneId timezone, int jitterWindowMs) {
    // Add random jitter to prevent exact-second thundering herd
    Instant exactTime = CronUtils.computeNextRun(cronExpr, timezone);
    long jitterMs = ThreadLocalRandom.current().nextLong(0, jitterWindowMs);
    return exactTime.plusMillis(jitterMs);
}

// Default jitter window: 5000ms
public Instant computeNextCronWithJitter(String cronExpr, ZoneId timezone) {
    return computeNextCronWithJitter(cronExpr, timezone, 5000);
}

// Example: "0 * * * *" (top of hour)
// Without jitter: ALL jobs fire at XX:00:00.000
// With 5s jitter: Jobs spread across XX:00:00.000 - XX:00:05.000
```

**Solution 3: Pre-Staging (Look-Ahead Window)**

```java
// Instead of processing only "now", look ahead 5 seconds
public void runTickWithLookahead() {
    Instant now = Instant.now();
    Instant lookahead = now.plusSeconds(5);

    while (!jobQueue.isEmpty() && !jobQueue.peek().getNextRunAt().isAfter(lookahead)) {
        JobDefinition job = jobQueue.poll();

        // Enqueue with scheduledTime (worker won't execute early)
        ExecutionMessage execution = ExecutionMessage.builder()
            .executionId(TimeUUID.generate())
            .scheduledTime(job.getNextRunAt())     // Worker respects this
            .earliestExecuteAt(job.getNextRunAt())
            .jobId(job.getJobId())
            .callbackUrl(job.getCallbackUrl())
            .build();
        kafka.produce("job-executions", execution);
    }

    // This spreads Kafka produce load over 5 seconds
    // instead of a single spike at the exact second
}
```

**Solution 4: Auto-Scaling Workers**

```yaml
# Kubernetes HPA based on Kafka consumer lag
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: job-worker-hpa
spec:
  scaleTargetRef:
    kind: Deployment
    name: job-workers
  minReplicas: 200
  maxReplicas: 2000
  metrics:
    - type: External
      external:
        metric:
          name: kafka_consumer_lag
          selector:
            matchLabels:
              topic: job-executions
        target:
          type: Value
          value: "10000"  # Scale up when lag > 10K messages
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 100  # Double capacity quickly
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300  # Slow scale-down to avoid flapping
```

---

### Deep Dive 3: At-Least-Once Delivery & Idempotency

**The Problem**: In a distributed system, failures can cause duplicate job executions:
- Scheduler crashes after enqueuing but before updating `next_run_at` → enqueues again on restart
- Worker crashes after executing callback but before committing Kafka offset → message re-delivered
- Network partition causes timeout → scheduler assumes failure, retries

**At-Least-Once Guarantee** (we guarantee every job runs at least once, but possibly more):

```
Why not exactly-once?
  - True exactly-once across distributed systems is extremely expensive
  - Requires 2PC (two-phase commit) across Kafka + Cassandra + callback target
  - Adds 10-50x latency overhead
  - Most job schedulers (Airflow, Quartz, AWS EventBridge) provide at-least-once

Our approach: at-least-once + idempotency keys → effectively exactly-once
```

**Idempotency Key Design**:

```java
public static String generateIdempotencyKey(UUID jobId, Instant scheduledTime) {
    /**
     * Deterministic key: same job + same scheduled time = same key.
     * If a job is triggered twice for the same schedule, the callback
     * receives the same idempotency key both times.
     */
    String raw = jobId.toString() + ":" + scheduledTime.toString();
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
    return Hex.encodeHexString(hash).substring(0, 32);
}

// Example:
// Job "550e8400" scheduled for "2025-01-08T15:00:00Z"
// → idempotency_key = "a3f2b1c9d8e7f6..."
// 
// If scheduler crashes and re-enqueues, same key is generated.
// Callback service sees duplicate key and skips re-processing.
```

**Worker-Side Deduplication**:

```java
public class JobWorker {
    private final JedisCluster redis;
    private final CassandraClient cassandra;

    public void executeJob(ExecutionMessage message) {
        String executionId = message.getExecutionId();
        String idempotencyKey = message.getIdempotencyKey();
        String dedupKey = "exec:dedup:" + idempotencyKey;

        // Check if we've already processed this exact execution (SETNX = set-if-not-exists)
        Boolean acquired = redis.setnx(dedupKey, executionId) == 1;
        if (acquired) {
            redis.expire(dedupKey, 86400); // 24h TTL

            // First time seeing this key → execute
            invokeCallback(message);
        } else {
            // Duplicate delivery → skip execution
            String existing = redis.get(dedupKey);
            log.warn("Duplicate delivery detected: key={}, original={}, duplicate={}",
                     idempotencyKey, existing, executionId);
            // Still update status to reflect this was handled
            cassandra.updateStatus(executionId, "DEDUPLICATED");
        }
    }
}
```

**Callback Contract** (what we require from job owners):

```
Headers sent with every callback:
  X-Idempotency-Key: a3f2b1c9d8e7f6...
  X-Execution-ID: a1b2c3d4-...
  X-Job-ID: 550e8400-...
  X-Attempt-Number: 1

The callback service SHOULD:
  1. Check X-Idempotency-Key before processing
  2. Store processed keys in its own dedup table
  3. Return 200 OK for already-processed keys (don't re-process)
  4. Be safe to call multiple times with same key
```

---

### Deep Dive 4: Cron Expression Parsing & Next-Run Calculation

**Cron Expression Format**:
```
┌───────── minute (0-59)
│ ┌───────── hour (0-23)
│ │ ┌───────── day of month (1-31)
│ │ │ ┌───────── month (1-12)
│ │ │ │ ┌───────── day of week (0-7, 0=Sun, 7=Sun)
│ │ │ │ │
* * * * *

Extended (6-field, with seconds):
┌───────── second (0-59)
│ ┌───────── minute (0-59)
│ │ ┌───────── hour (0-23)
│ │ │ ┌───────── day of month (1-31)
│ │ │ │ ┌───────── month (1-12)
│ │ │ │ │ ┌───────── day of week (0-7)
│ │ │ │ │ │
* * * * * *

Special characters:
  *     Any value
  ,     List: 1,3,5
  -     Range: 1-5
  /     Step: */5 (every 5)
  L     Last day of month
  W     Nearest weekday
  #     Nth day of week: 2#3 (3rd Monday)
```

**Next-Run Computation Algorithm**:

```java
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

public class CronUtils {
    private static final CronParser PARSER = new CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );

    /**
     * Compute the next run time for a cron expression in a given timezone.
     * Returns UTC Instant.
     */
    public static Instant computeNextRun(String cronExpression, String timezone, Instant after) {
        ZoneId tz = ZoneId.of(timezone);

        if (after == null) {
            after = Instant.now();
        }

        // Convert 'after' to the job's local timezone for correct DST handling
        ZonedDateTime localAfter = after.atZone(tz);

        // Parse cron and compute next execution
        Cron cron = PARSER.parse(cronExpression);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        Optional<ZonedDateTime> nextLocal = executionTime.nextExecution(localAfter);

        // Convert back to UTC for storage
        return nextLocal.orElseThrow(() -> new IllegalStateException("No next execution"))
                        .toInstant();
    }

    public static Instant computeNextRun(String cronExpression, String timezone) {
        return computeNextRun(cronExpression, timezone, null);
    }
}

// Examples:
// cron="0 10 * * *", tz="America/New_York", after=2025-01-08T14:00Z
// → next_run = 2025-01-09T15:00:00Z  (10 AM ET = 3 PM UTC)

// DST edge case:
// cron="0 2 * * *", tz="America/New_York"
// On March 9, 2025 (spring forward): 2 AM doesn't exist → skip to 3 AM
// On November 2, 2025 (fall back): 2 AM happens twice → take first occurrence
```

**Timezone & DST Gotchas**:
```
Problem 1: "Every day at 2:30 AM" in America/New_York
  - Spring forward (March): 2:30 AM doesn't exist → fire at 3:00 AM? 3:30 AM? Skip?
  - Fall back (November): 2:30 AM happens twice → fire once or twice?
  
Our policy:
  - Non-existent time → fire at next valid time after the gap
  - Ambiguous time → fire on first occurrence only
  - Document this clearly in API docs

Problem 2: Cron stored as UTC internally
  - "0 10 * * *" in New York = "0 15 * * *" UTC in winter
  - But "0 14 * * *" UTC in summer (EDT = UTC-4)
  - NEVER store cron in UTC! Always store original tz + expression
  - Compute next_run_at in UTC at trigger time
```

---

### Deep Dive 5: Retry Strategy & Dead Letter Queue

**Retry Policy Configuration**:

```java
public class RetryPolicy {
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(500, 502, 503, 504, 429);
    private static final Set<String> RETRYABLE_ERRORS = Set.of(
        "TIMEOUT", "CONNECTION_REFUSED", "CONNECTION_RESET", "DNS_RESOLUTION_FAILED"
    );

    public enum BackoffStrategy { FIXED, LINEAR, EXPONENTIAL }

    private final int maxRetries;
    private final BackoffStrategy backoff;
    private final int initialDelayMs;
    private final int maxDelayMs;

    public RetryPolicy(int maxRetries, BackoffStrategy backoff, int initialDelayMs, int maxDelayMs) {
        this.maxRetries = maxRetries;
        this.backoff = backoff;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    /** Compute delay in ms before next retry */
    public int computeDelay(int attemptNumber) {
        long delay;
        switch (backoff) {
            case FIXED:       delay = initialDelayMs; break;
            case LINEAR:      delay = (long) initialDelayMs * attemptNumber; break;
            case EXPONENTIAL: delay = initialDelayMs * (1L << (attemptNumber - 1)); break;
            default:          delay = initialDelayMs;
        }
        // Add jitter (±25%) to prevent retry storms
        double jitter = 0.75 + ThreadLocalRandom.current().nextDouble() * 0.5;
        delay = (long) (delay * jitter);
        // Cap at max delay
        return (int) Math.min(delay, maxDelayMs);
    }

    /** Determine if this failure is retryable */
    public boolean shouldRetry(int attempt, Integer httpStatus, String errorType) {
        if (attempt >= maxRetries) return false;
        // HTTP 4xx (client errors) are NOT retryable (except 429)
        if (httpStatus != null && httpStatus >= 400 && httpStatus < 500 && httpStatus != 429) return false;
        // Specific error types that are retryable
        if (errorType != null && !RETRYABLE_ERRORS.contains(errorType)) return false;
        return true;
    }
}

// Retry timeline example (EXPONENTIAL, initial=1s):
// Attempt 1: immediate execution → FAIL
// Attempt 2: wait ~1s → FAIL  
// Attempt 3: wait ~2s → FAIL
// Attempt 4: wait ~4s → FAIL (max retries reached → DLQ)
```

**Delayed Retry via Kafka**:

```java
// Option A: Separate delay topics (simple, commonly used)
// Each topic has a different consumer delay

public class RetryRouter {
    private final KafkaProducer<String, ExecutionMessage> kafka;

    public void enqueueRetry(ExecutionMessage message, int attemptNumber, int delayMs) {
        // Pick the closest delay topic
        int delaySec = delayMs / 1000;
        String topic;
        if (delaySec <= 1)       topic = "job-retries-1s";
        else if (delaySec <= 5)  topic = "job-retries-5s";
        else if (delaySec <= 30) topic = "job-retries-30s";
        else                     topic = "job-retries-300s";

        message.setAttemptNumber(attemptNumber + 1);
        message.setRetryAfter(Instant.now().plusMillis(delayMs));
        kafka.produce(topic, message);
    }
}

// Each retry topic has a consumer that:
// 1. Reads messages
// 2. Checks retryAfter timestamp
// 3. If not yet due, sleeps briefly and re-checks
// 4. If due, re-produces to "job-executions" main topic
```

**Dead Letter Queue (DLQ)**:

```java
public class DlqService {
    private final KafkaProducer<String, DlqMessage> kafka;
    private final CassandraClient cassandra;
    private final AlertService alertService;

    /** Send permanently failed job to DLQ for manual intervention */
    public void sendToDlq(ExecutionMessage message, String finalError) {
        DlqMessage dlqMessage = DlqMessage.builder()
            .executionId(message.getExecutionId())
            .jobId(message.getJobId())
            .dlqReason(finalError)
            .dlqTimestamp(Instant.now())
            .totalAttempts(message.getAttemptNumber())
            .errorHistory(message.getErrorHistory())
            .build();

        // Write to DLQ topic
        kafka.produce("job-dlq", dlqMessage);

        // Update execution status
        cassandra.updateExecution(message.getExecutionId(), "FAILED", finalError);

        // Alert job owner
        alertService.send(Alert.builder()
            .channel("pagerduty")
            .severity(Severity.WARNING)
            .title("Job " + message.getJobId() + " exhausted all retries")
            .detail("lastError", finalError)
            .detail("attempts", String.valueOf(message.getAttemptNumber()))
            .detail("executionId", message.getExecutionId())
            .build());
    }
}

// DLQ consumers provide:
// 1. Dashboard for operators to see failed jobs
// 2. Manual retry button (re-enqueue to main topic)
// 3. Auto-retry after a longer delay (e.g., 1 hour)
// 4. Metrics: DLQ depth, age of oldest message
```

---

### Deep Dive 6: Hot/Warm/Cold History Storage (1-Year Retention)

**The Problem**: 312 TB/year of execution history. Storing all of it in Cassandra is expensive. Most queries are for recent data (last 24-48 hours).

**Three-Tier Storage Architecture**:

```
┌───────────────────────────────────────────────────────────┐
│  HOT TIER: Last 7 days                                    │
│  Storage: Cassandra (SSD-backed)                          │
│  Size: ~6 TB (864 GB/day × 7)                            │
│  Latency: < 10ms reads                                    │
│  Use: Dashboard, real-time monitoring, debugging          │
│  Cost: $$$                                                │
├───────────────────────────────────────────────────────────┤
│  WARM TIER: 7 days - 90 days                              │
│  Storage: Cassandra (HDD-backed) OR S3 + DuckDB           │
│  Size: ~72 TB                                             │
│  Latency: 100ms - 1s reads                                │
│  Use: Trend analysis, SLA reporting, troubleshooting      │
│  Cost: $$                                                 │
├───────────────────────────────────────────────────────────┤
│  COLD TIER: 90 days - 1 year                              │
│  Storage: S3 (Parquet) + Athena/Presto for queries        │
│  Size: ~234 TB                                            │
│  Latency: 5-30s reads                                     │
│  Use: Compliance, auditing, yearly reporting              │
│  Cost: $                                                  │
└───────────────────────────────────────────────────────────┘
```

**Data Migration Pipeline**:

```java
// Nightly job: move data from Hot → Warm → Cold

public class HistoryTieringJob {

    public void runDaily() {
        migrateHotToWarm(7);     // Step 1: Hot → Warm (data older than 7 days)
        migrateWarmToCold(90);   // Step 2: Warm → Cold (data older than 90 days)
        purgeExpired(365);       // Step 3: Delete cold data older than 365 days
    }

    /** Export Cassandra data to S3 as Parquet files */
    private void migrateWarmToCold(int cutoffDays) {
        LocalDate cutoff = LocalDate.now().minusDays(cutoffDays);

        for (LocalDate day : getDaysToMigrate(cutoff)) {
            List<JobExecution> records = cassandra.query(
                "SELECT * FROM job_executions WHERE day = ?", day);

            // Convert to Parquet (columnar, compressed)
            String parquetPath = String.format(
                "s3://job-history/year=%d/month=%02d/day=%02d/executions.parquet",
                day.getYear(), day.getMonthValue(), day.getDayOfMonth());
            ParquetWriter.write(parquetPath, records, Compression.ZSTD);

            // Verify S3 write succeeded, then delete from Cassandra
            if (verifyS3Write(parquetPath, records.size())) {
                cassandra.deleteDay(day);
                log.info("Migrated {} records for {}", records.size(), day);
            }
        }
    }

    /** Query cold tier using Athena */
    public List<JobExecution> queryColdStorage(UUID jobId, LocalDate startDate, LocalDate endDate) {
        return athena.query(String.format(
            "SELECT * FROM job_history_cold WHERE job_id = '%s' " +
            "AND day BETWEEN '%s' AND '%s' ORDER BY execution_id DESC LIMIT 1000",
            jobId, startDate, endDate));
    }
}
```

**Cost Comparison (312 TB/year)**:

```
All in Cassandra (SSD):  312 TB × $0.10/GB/month = $31,200/month
Tiered storage:
  Hot (Cassandra SSD):    6 TB × $0.10/GB/month  =    $600/month
  Warm (Cassandra HDD):  72 TB × $0.03/GB/month  =  $2,160/month
  Cold (S3 Standard):   234 TB × $0.023/GB/month =  $5,382/month
  Athena queries:       ~500 QPS cold queries     =    $500/month
  
  Total tiered: ~$8,642/month (72% savings!)
```

**Unified Query Layer**:

```java
public class HistoryService {
    private final CassandraClient cassandraHot;   // SSD-backed
    private final CassandraClient cassandraWarm;   // HDD-backed
    private final AthenaClient athena;             // S3 cold tier

    /** Transparent query across all tiers */
    public List<JobExecution> queryExecutions(UUID jobId, LocalDate startDate,
                                              LocalDate endDate, String status) {
        List<JobExecution> results = new ArrayList<>();
        LocalDate now = LocalDate.now();
        LocalDate hotCutoff = now.minusDays(7);
        LocalDate warmCutoff = now.minusDays(90);

        // Hot tier (Cassandra SSD)
        if (!endDate.isBefore(hotCutoff)) {
            LocalDate hotStart = startDate.isAfter(hotCutoff) ? startDate : hotCutoff;
            results.addAll(cassandraHot.query(jobId, hotStart, endDate, status));
        }

        // Warm tier (Cassandra HDD)
        if (startDate.isBefore(hotCutoff) && !endDate.isBefore(warmCutoff)) {
            LocalDate warmStart = startDate.isAfter(warmCutoff) ? startDate : warmCutoff;
            LocalDate warmEnd = endDate.isBefore(hotCutoff) ? endDate : hotCutoff;
            results.addAll(cassandraWarm.query(jobId, warmStart, warmEnd, status));
        }

        // Cold tier (S3 + Athena)
        if (startDate.isBefore(warmCutoff)) {
            LocalDate coldEnd = endDate.isBefore(warmCutoff) ? endDate : warmCutoff;
            results.addAll(athena.query(jobId, startDate, coldEnd, status));
        }

        results.sort(Comparator.comparing(JobExecution::getExecutionId).reversed());
        return results;
    }
}
```

---

### Deep Dive 7: Worker Lifecycle Management & Heartbeats

**The Problem**: Workers are ephemeral — they crash, get OOM-killed, or lose network connectivity. We must detect dead workers quickly and reassign their in-flight jobs.

**Heartbeat Protocol**:

```java
public class WorkerHeartbeat {
    private static final int HEARTBEAT_INTERVAL_SEC = 5;
    private static final int HEARTBEAT_TIMEOUT_SEC = 15; // 3 missed = dead

    private final String workerId;
    private final JedisCluster redis;
    private final int capacity;
    private final AtomicInteger currentLoad;

    /** Called every 5 seconds by a ScheduledExecutorService */
    public void sendHeartbeat() {
        Pipeline pipeline = redis.pipelined();
        String key = "worker:" + workerId;
        Map<String, String> fields = Map.of(
            "last_heartbeat", Instant.now().toString(),
            "current_load", String.valueOf(currentLoad.get()),
            "status", "ACTIVE"
        );
        pipeline.hset(key, fields);
        pipeline.zadd("workers:available", capacity - currentLoad.get(), workerId);
        pipeline.expire(key, 30);
        pipeline.sync();
    }

    /** Called once on worker startup */
    public void register() {
        String key = "worker:" + workerId;
        redis.hset(key, Map.of(
            "worker_id", workerId,
            "host", InetAddress.getLocalHost().getHostAddress(),
            "capacity", String.valueOf(capacity),
            "current_load", "0",
            "status", "ACTIVE",
            "registered_at", Instant.now().toString(),
            "last_heartbeat", Instant.now().toString()
        ));
        redis.zadd("workers:available", capacity, workerId);
    }

    /** Called on graceful shutdown */
    public void deregister() {
        redis.hset("worker:" + workerId, "status", "DRAINING");
        redis.zrem("workers:available", workerId);
        drainAndShutdown(); // finish in-flight, then cleanup
        redis.del("worker:" + workerId);
    }
}
```

**Dead Worker Detection (Reaper Service)**:

```java
public class WorkerReaper {
    private final JedisCluster redis;
    private final CassandraClient cassandra;
    private final KafkaProducer<String, ExecutionMessage> kafka;

    /** Runs on a separate service, monitors worker health. Called every 10 seconds. */
    @Scheduled(fixedRate = 10_000)
    public void detectDeadWorkers() {
        Set<String> workerKeys = redis.keys("worker:*");
        for (String key : workerKeys) {
            Map<String, String> data = redis.hgetAll(key);
            Instant lastHb = Instant.parse(data.get("last_heartbeat"));
            long ageSec = Duration.between(lastHb, Instant.now()).getSeconds();

            if (ageSec > 15) { // 3 missed heartbeats
                handleDeadWorker(data.get("worker_id"));
            }
        }
    }

    private void handleDeadWorker(String workerId) {
        log.error("Worker {} detected as dead", workerId);

        // 1. Mark worker as DEAD
        redis.hset("worker:" + workerId, "status", "DEAD");
        redis.zrem("workers:available", workerId);

        // 2. Find all in-flight jobs assigned to this worker
        List<JobExecution> orphanedJobs = cassandra.findRunningByWorker(workerId);

        // 3. Re-enqueue orphaned jobs
        for (JobExecution job : orphanedJobs) {
            cassandra.updateStatus(job.getExecutionId(), "RETRYING",
                "Worker " + workerId + " died");
            ExecutionMessage retryMsg = ExecutionMessage.builder()
                .executionId(TimeUUID.generate())
                .jobId(job.getJobId())
                .retryOf(job.getExecutionId())
                .attemptNumber(job.getAttemptNumber() + 1)
                .build();
            kafka.produce("job-executions", retryMsg);
        }

        // 4. Emit alert
        metrics.counter("worker.deaths").tag("worker_id", workerId).increment();
        alertService.send(Severity.WARNING,
            "Worker " + workerId + " died, " + orphanedJobs.size() + " jobs re-queued");
    }
}
```

**Graceful Shutdown (Drain Mode)**:

```java
public class JobWorkerLifecycle {
    private final KafkaConsumer<String, ExecutionMessage> consumer;
    private final WorkerHeartbeat heartbeat;
    private final AtomicInteger inFlightCount;

    /** Kubernetes sends SIGTERM before killing pod */
    @PreDestroy
    public void handleSigterm() {
        log.info("Received SIGTERM, entering drain mode");

        // 1. Stop consuming new messages from Kafka
        consumer.pause(consumer.assignment());

        // 2. Mark as DRAINING (won't receive new jobs)
        heartbeat.deregister();

        // 3. Wait for in-flight jobs to complete (up to 30s)
        Instant deadline = Instant.now().plusSeconds(30);
        while (inFlightCount.get() > 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(1000);
        }

        // 4. If jobs still running after 30s, log and let Kubernetes kill
        if (inFlightCount.get() > 0) {
            log.warn("{} jobs still running at shutdown", inFlightCount.get());
            // These will be detected by reaper and re-queued
        }

        // 5. Commit final Kafka offsets
        consumer.commitSync();
    }
}
```

---

### Deep Dive 8: Stateless Scheduler & Partition-Based Ownership

**The Problem**: Multiple scheduler nodes must coordinate to ensure every job is triggered exactly once — no duplicate triggers, no missed jobs.

**Partition-Based Ownership Model**:

```
64 partitions distributed across N scheduler nodes:

With 4 scheduler nodes:
  Node 1: partitions 0-15  (owns 16 partitions)
  Node 2: partitions 16-31 (owns 16 partitions)
  Node 3: partitions 32-47 (owns 16 partitions)
  Node 4: partitions 48-63 (owns 16 partitions)

Each job belongs to exactly one partition:
  partition_id = murmur3_hash(job_id) % 64

Each partition is owned by exactly one node at any time.
→ No two nodes will ever trigger the same job.
```

**ZooKeeper-Based Partition Assignment**:

```java
public class SchedulerCoordinator implements Watcher {
    private final CuratorFramework zk;
    private final String schedulerId;
    private final int totalPartitions;
    private volatile List<Integer> myPartitions = new ArrayList<>();

    public SchedulerCoordinator(CuratorFramework zk, String schedulerId, int totalPartitions) {
        this.zk = zk;
        this.schedulerId = schedulerId;
        this.totalPartitions = totalPartitions;
    }

    /** Register this scheduler as an ephemeral node */
    public void register() throws Exception {
        // Ephemeral node: auto-deleted if scheduler disconnects
        String data = String.format("{\"host\":\"%s\",\"started\":\"%s\"}",
            InetAddress.getLocalHost().getHostAddress(), Instant.now());
        zk.create().withMode(CreateMode.EPHEMERAL)
           .forPath("/schedulers/live/" + schedulerId, data.getBytes());

        // Watch for changes in live schedulers (triggers rebalance)
        PathChildrenCache cache = new PathChildrenCache(zk, "/schedulers/live", true);
        cache.getListenable().addListener((client, event) -> onSchedulerChange());
        cache.start();
    }

    /** Called when a scheduler joins or leaves */
    private void onSchedulerChange() throws Exception {
        List<String> liveSchedulers = zk.getChildren().forPath("/schedulers/live")
            .stream().sorted().collect(Collectors.toList());  // Deterministic ordering
        int myIndex = liveSchedulers.indexOf(schedulerId);
        int numSchedulers = liveSchedulers.size();

        // Simple round-robin partition assignment
        List<Integer> newPartitions = new ArrayList<>();
        for (int p = 0; p < totalPartitions; p++) {
            if (p % numSchedulers == myIndex) newPartitions.add(p);
        }

        // Detect changes
        Set<Integer> added = new HashSet<>(newPartitions);
        added.removeAll(myPartitions);
        Set<Integer> removed = new HashSet<>(myPartitions);
        removed.removeAll(newPartitions);

        added.forEach(p -> { log.info("Acquired partition: {}", p); loadPartition(p); });
        removed.forEach(p -> { log.info("Released partition: {}", p); unloadPartition(p); });

        myPartitions = newPartitions;
        zk.setData().forPath("/schedulers/assignments/" + schedulerId,
            new ObjectMapper().writeValueAsBytes(Map.of("partitions", newPartitions)));
    }
}
```

**Failover Scenario**:

```
Time T0: 4 schedulers, 16 partitions each
  Node 1: [0-15], Node 2: [16-31], Node 3: [32-47], Node 4: [48-63]

Time T1: Node 3 crashes (ZK ephemeral node expires after ~10s)
  ZK triggers on_scheduler_change on all remaining nodes

Time T2: Rebalance (3 nodes, ~21 partitions each)
  Node 1: [0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45, 48, 51, 54, 57, 60]
  Node 2: [1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37, 40, 43, 46, 49, 52, 55, 58, 61]
  Node 4: [2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44, 47, 50, 53, 56, 59, 62, 63]

Partitions 32-47 (from dead Node 3) redistributed across surviving nodes.
Jobs on those partitions may have missed 1-2 triggers (~10-15 seconds of downtime).
On reload, scheduler detects overdue jobs (next_run_at < NOW()) and triggers immediately.

Recovery time: ~15 seconds (ZK session timeout + reload)
```

**Preventing Double-Trigger During Rebalance**:

```java
public void triggerJobWithFence(JobDefinition job, int partitionId) {
    // Fencing token: partition ownership version
    byte[] ownerData = zk.getData().forPath("/schedulers/partitions/" + partitionId);
    String currentOwner = new String(ownerData);

    if (!currentOwner.equals(schedulerId)) {
        log.warn("Lost partition {} during trigger, skipping", partitionId);
        return;
    }

    // Acquire partition-level lock (short-lived, 5 second TTL)
    String lockKey = String.format("trigger_lock:%d:%s:%d",
        partitionId, job.getJobId(), job.getNextRunAt().getEpochSecond());
    String result = redis.set(lockKey, schedulerId, SetParams.setParams().nx().ex(5));

    if (result == null) {
        log.warn("Another scheduler already triggered {}", job.getJobId());
        return;
    }

    // Safe to trigger
    enqueueToKafka(job);
}
```

---

### Deep Dive 9: Priority Queues & Job Starvation Prevention

**The Problem**: Jobs have priorities 1-10. High-priority jobs (1-3) should execute before low-priority ones (8-10). But low-priority jobs should never starve completely.

**Implementation: Priority-Based Kafka Topics**:

```
Instead of one topic, use priority bands:

  job-executions-p1-p3   (HIGH)    → 40 partitions
  job-executions-p4-p7   (MEDIUM)  → 40 partitions
  job-executions-p8-p10  (LOW)     → 20 partitions

Scheduler routes based on priority:
  if job.priority <= 3:
      topic = "job-executions-p1-p3"
  elif job.priority <= 7:
      topic = "job-executions-p4-p7"
  else:
      topic = "job-executions-p8-p10"
```

**Worker Weighted Consumption**:

```java
public class PriorityWorker {
    // Subscribe to all priority topics
    private final Map<String, KafkaConsumer<String, ExecutionMessage>> consumers = Map.of(
        "HIGH",   new KafkaConsumer<>(props, "job-executions-p1-p3"),
        "MEDIUM", new KafkaConsumer<>(props, "job-executions-p4-p7"),
        "LOW",    new KafkaConsumer<>(props, "job-executions-p8-p10")
    );
    // Weighted polling: HIGH gets 60%, MEDIUM 30%, LOW 10%
    private static final Map<String, Integer> WEIGHTS = Map.of("HIGH", 6, "MEDIUM", 3, "LOW", 1);

    /** Weighted round-robin across priority levels */
    public ConsumerRecord<String, ExecutionMessage> pollNextBatch() {
        for (int i = 0; i < WEIGHTS.get("HIGH"); i++) {
            ConsumerRecords<String, ExecutionMessage> records =
                consumers.get("HIGH").poll(Duration.ofMillis(10));
            if (!records.isEmpty()) return records.iterator().next();
        }
        for (int i = 0; i < WEIGHTS.get("MEDIUM"); i++) {
            ConsumerRecords<String, ExecutionMessage> records =
                consumers.get("MEDIUM").poll(Duration.ofMillis(10));
            if (!records.isEmpty()) return records.iterator().next();
        }
        for (int i = 0; i < WEIGHTS.get("LOW"); i++) {
            ConsumerRecords<String, ExecutionMessage> records =
                consumers.get("LOW").poll(Duration.ofMillis(10));
            if (!records.isEmpty()) return records.iterator().next();
        }
        return null; // No messages available
    }
}
```

**Starvation Prevention: Aging**:

```java
public static int applyPriorityAging(ExecutionMessage message) {
    /**
     * Boost priority of old messages to prevent starvation.
     * A low-priority job waiting > 5 minutes gets promoted.
     */
    Instant enqueuedAt = message.getEnqueuedAt();
    long ageSeconds = Duration.between(enqueuedAt, Instant.now()).getSeconds();
    int originalPriority = message.getPriority();

    // Every 60 seconds of waiting, boost priority by 1
    int agingBoost = (int) (ageSeconds / 60);
    int effectivePriority = Math.max(1, originalPriority - agingBoost);

    if (effectivePriority != originalPriority) {
        log.info("Job {} aged from P{} to P{} after {}s",
                 message.getJobId(), originalPriority, effectivePriority, ageSeconds);
    }
    return effectivePriority;
}

// Example:
// P10 job enqueued at T=0
// At T=60s:  effective priority = P9
// At T=120s: effective priority = P8
// ...
// At T=540s (9 min): effective priority = P1
// Maximum wait for any job: ~10 minutes before it reaches highest priority
```

---

### Deep Dive 10: Distributed Locking & Exactly-Once Triggering

**The Problem**: Even with partition-based ownership, edge cases can cause double-triggers:
- Network partition makes ZooKeeper think a scheduler is dead (but it's still running)
- Rebalance mid-trigger: old owner starts trigger, new owner also triggers
- Clock skew between scheduler nodes

**Defense-in-Depth: Multiple Layers of Protection**

```
Layer 1: Partition ownership (ZooKeeper)
  → Only one scheduler owns a partition at a time
  → Prevents the common case of double-trigger

Layer 2: Redis distributed lock (per job per scheduled time)
  → Short-lived lock: SET NX with 10s TTL
  → Key: trigger:{job_id}:{scheduled_time_epoch}
  → Prevents the edge case of two nodes during rebalance

Layer 3: Idempotency key (worker-side dedup)
  → Even if a job is enqueued twice, workers detect and skip
  → Prevents the edge case of network-delayed Kafka messages

Layer 4: Cassandra execution record (application-level dedup)
  → Before executing, worker checks if execution already exists
  → Lightweight IF NOT EXISTS on Cassandra
```

**Redis Lock Implementation**:

```java
public boolean acquireTriggerLock(UUID jobId, Instant scheduledTime, String schedulerId) {
    /**
     * Acquire a lock for triggering a specific job at a specific time.
     * Returns true if lock acquired (safe to trigger), false if already triggered.
     */
    String lockKey = "trigger:" + jobId + ":" + scheduledTime.getEpochSecond();

    // SET NX (only set if not exists) with 10 second TTL
    String result = redis.set(lockKey, schedulerId, SetParams.setParams().nx().ex(10));

    if ("OK".equals(result)) {
        return true;
    } else {
        String existingOwner = redis.get(lockKey);
        log.info("Trigger lock for {}@{} already held by {}", jobId, scheduledTime, existingOwner);
        return false;
    }
}

// In scheduler tick:
public void triggerJob(JobDefinition job) {
    if (!acquireTriggerLock(job.getJobId(), job.getNextRunAt(), schedulerId)) {
        return; // Already triggered by another node
    }
    enqueueToKafka(job);
}
```

**Fencing Token Pattern** (advanced):

```java
// ZooKeeper provides a monotonically increasing "zxid" (transaction ID)
// that can be used as a fencing token.

public void triggerWithFencing(JobDefinition job, int partitionId) throws Exception {
    // Get current ownership with version
    Stat stat = new Stat();
    zk.getData().storingStatIn(stat).forPath("/schedulers/partitions/" + partitionId);
    int fenceToken = stat.getVersion(); // Monotonically increasing

    // Include fence token in Kafka message
    ExecutionMessage execution = ExecutionMessage.builder()
        .executionId(TimeUUID.generate())
        .jobId(job.getJobId())
        .fenceToken(fenceToken)
        .partitionId(partitionId)
        .build();
    kafka.produce("job-executions", execution);

    // Worker validates fence token before executing.
    // If a stale scheduler sends a message with an old fence token,
    // the worker can detect and reject it.
}
```

---

### Deep Dive 11: Observability, Alerting & SLA Monitoring

**Key Metrics to Track** (using Micrometer / Prometheus):

```java
// Scheduler Metrics
public class SchedulerMetrics {
    // Throughput
    Counter jobsTriggered      = Counter.builder("scheduler.jobs_triggered").register(registry);
    Timer   triggerLatency     = Timer.builder("scheduler.trigger_latency_ms").register(registry);
    Gauge   queueDepth         = Gauge.builder("scheduler.queue_depth", jobQueue, PriorityQueue::size).register(registry);
    Gauge   overdueJobs        = Gauge.builder("scheduler.overdue_jobs", this, SchedulerMetrics::countOverdue).register(registry);
}

// Worker Metrics
public class WorkerMetrics {
    Counter jobsExecuted       = Counter.builder("worker.jobs_executed").register(registry);
    Timer   executionDuration  = Timer.builder("worker.execution_duration_ms").register(registry);
    Gauge   successRate        = Gauge.builder("worker.success_rate", this, WorkerMetrics::calcSuccessRate).register(registry);
    Gauge   activeCount        = Gauge.builder("worker.active_count", this, WorkerMetrics::countActive).register(registry);
    Gauge   utilization        = Gauge.builder("worker.utilization", this, WorkerMetrics::calcUtilization).register(registry);
}

// Queue Metrics (Kafka)
Gauge   consumerLag       = Gauge.builder("kafka.consumer_lag", ...).register(registry);
Timer   produceLatency    = Timer.builder("kafka.produce_latency_ms").register(registry);

// Error Metrics
Counter retries           = Counter.builder("job.retries").register(registry);
Counter dlqEntries        = Counter.builder("job.dlq_entries").register(registry);
Counter timeouts          = Counter.builder("job.timeouts").register(registry);
Counter workerDeaths      = Counter.builder("worker.deaths").register(registry);

// SLA Metrics
Timer   triggerDelay      = Timer.builder("job.trigger_delay_ms").register(registry);   // actual_trigger - scheduled_time
Timer   e2eLatency        = Timer.builder("job.e2e_latency_ms").register(registry);     // execution_complete - scheduled_time
```

**SLA Dashboard**:

```
┌─────────────────────────────────────────────────────┐
│              JOB SCHEDULER SLA DASHBOARD             │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Trigger Accuracy (last 1h):                        │
│  ██████████████████████████████░░ 98.5% within 1s   │
│  ████████████████████████████████ 99.9% within 5s   │
│                                                      │
│  Throughput:                                         │
│  Current: 12,345 jobs/sec  │  Peak: 48,231/sec      │
│  Kafka Lag: 1,243 msgs     │  Avg Lag: 340ms        │
│                                                      │
│  Success Rate (last 1h):                             │
│  ███████████████████████████████░ 97.8%              │
│  Retries: 2.1%  │  DLQ: 0.1%  │  Timeouts: 0.3%   │
│                                                      │
│  Workers:                                            │
│  Active: 487/500  │  Utilization: 72%               │
│  Dead: 0 (last 1h)  │  Draining: 3                  │
│                                                      │
│  History Storage:                                    │
│  Hot: 5.8 TB  │  Warm: 68 TB  │  Cold: 220 TB      │
│  Write rate: 864 GB/day                              │
│                                                      │
└─────────────────────────────────────────────────────┘
```

**Alerting Rules**:

```yaml
alerts:
  - name: HighTriggerDelay
    condition: p99(job.trigger_delay_ms) > 5000  # 5 seconds
    severity: CRITICAL
    message: "Jobs are being triggered >5s late. Check scheduler health."
    
  - name: HighConsumerLag
    condition: kafka.consumer_lag > 50000
    severity: WARNING
    message: "Kafka consumer lag is high. Workers may need scaling."
    
  - name: DLQGrowing
    condition: rate(job.dlq_entries, 5m) > 10  # >10 DLQ entries in 5 min
    severity: WARNING
    message: "Dead letter queue is growing. Check callback health."
    
  - name: WorkerDeath
    condition: rate(worker.deaths, 10m) > 5
    severity: CRITICAL
    message: "Multiple workers dying. Check for OOM, resource issues."
    
  - name: LowSuccessRate
    condition: job.success_rate < 0.95  # Below 95%
    severity: CRITICAL
    message: "Job success rate below 95%. Check callback services."
    
  - name: SchedulerOverdue
    condition: scheduler.overdue_jobs > 1000
    severity: CRITICAL
    message: "1000+ overdue jobs. Scheduler may be stuck or crashed."
    
  - name: HistoryWriteFailure
    condition: rate(cassandra.write_errors, 5m) > 100
    severity: WARNING
    message: "Cassandra write errors increasing. Check cluster health."
```

---

### Deep Dive 12: Multi-Tenancy, Rate Limiting & Fairness

**The Problem**: In a multi-tenant system, one tenant's burst (e.g., 1M jobs at midnight) should not starve other tenants' jobs.

**Tenant Isolation Architecture**:

```
┌─────────────────────────────────────────────────────┐
│                  API GATEWAY                         │
│  Per-tenant rate limiting:                           │
│  • Free tier:    100 jobs/min, 10K active schedules │
│  • Pro tier:    1,000 jobs/min, 100K active         │
│  • Enterprise: 10,000 jobs/min, 1M active           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              TENANT-AWARE KAFKA                      │
│                                                      │
│  Option A: Separate topics per tenant (simple)       │
│    job-executions-tenant-A                           │
│    job-executions-tenant-B                           │
│    → Problem: Too many topics at scale               │
│                                                      │
│  Option B: Single topic, tenant-aware consumers      │
│    Partition key = tenant_id + job_id                 │
│    → Workers weighted by tenant quota                │
│                                                      │
│  Option C (Recommended): Tiered topics               │
│    job-executions-premium     (Enterprise tenants)   │
│    job-executions-standard    (Pro tenants)           │
│    job-executions-free        (Free tenants)          │
│    Workers: 60% premium, 30% standard, 10% free      │
└─────────────────────────────────────────────────────┘
```

**Per-Tenant Rate Limiting**:

```java
public class TenantRateLimiter {
    private static final Map<String, Map<String, Integer>> LIMITS = Map.of(
        "free",       Map.of("jobsPerMin", 100,   "burst", 200),
        "pro",        Map.of("jobsPerMin", 1000,  "burst", 2000),
        "enterprise", Map.of("jobsPerMin", 10000, "burst", 20000)
    );

    private final JedisCluster redis;

    /** Sliding window rate limiter using Redis sorted sets */
    public boolean checkRateLimit(String tenantId, String tier) {
        Map<String, Integer> limit = LIMITS.get(tier);
        String key = "ratelimit:" + tenantId + ":jobs";
        long now = Instant.now().getEpochSecond();
        long windowStart = now - 60;

        Pipeline pipe = redis.pipelined();
        pipe.zremrangeByScore(key, 0, windowStart);                             // Remove old entries
        Response<Long> countResp = pipe.zcard(key);                             // Count current window
        pipe.zadd(key, now, now + ":" + UUID.randomUUID());                     // Add this request
        pipe.expire(key, 120);                                                  // Cleanup
        pipe.sync();

        long currentCount = countResp.get();
        if (currentCount >= limit.get("jobsPerMin")) {
            throw new RateLimitExceededException(tenantId, limit.get("jobsPerMin"),
                60 - (now - windowStart));
        }
        return true;
    }
}
```

**Fair Scheduling Across Tenants**:

```java
public class FairScheduler {
    /**
     * Ensures no single tenant monopolizes execution capacity.
     * Uses weighted fair queuing (WFQ) across tenants.
     */
    private final ConcurrentHashMap<String, AtomicInteger> tenantBudgets = new ConcurrentHashMap<>();

    /** Called every 10 seconds to refresh tenant budgets */
    @Scheduled(fixedRate = 10_000)
    public void allocateBudget() {
        int totalCapacity = 10_000; // jobs/sec system-wide
        List<Tenant> activeTenants = getActiveTenants();

        int totalWeight = activeTenants.stream().mapToInt(this::getWeight).sum();

        for (Tenant tenant : activeTenants) {
            int weight = getWeight(tenant);
            double tenantShare = ((double) weight / totalWeight) * totalCapacity;
            tenantShare = Math.max(tenantShare, 50); // At least 50 jobs/sec
            tenantBudgets.put(tenant.getId(), new AtomicInteger((int) (tenantShare * 10))); // 10-sec window
        }
    }

    private int getWeight(Tenant tenant) {
        switch (tenant.getTier()) {
            case "enterprise": return 10;
            case "pro":        return 3;
            default:           return 1;
        }
    }

    /** Check if tenant has budget remaining */
    public boolean canExecute(String tenantId) {
        AtomicInteger budget = tenantBudgets.get(tenantId);
        if (budget != null && budget.get() > 0) {
            budget.decrementAndGet();
            return true;
        }
        return false; // Tenant over budget, defer to next window
    }
}
```

**Tenant Resource Quotas**:

```
┌──────────────┬──────────────┬──────────────┬───────────────┐
│ Resource      │ Free         │ Pro          │ Enterprise    │
├──────────────┼──────────────┼──────────────┼───────────────┤
│ Active Jobs  │ 10,000       │ 100,000      │ 1,000,000     │
│ Executions/m │ 100          │ 1,000        │ 10,000        │
│ Max Payload  │ 1 KB         │ 10 KB        │ 100 KB        │
│ History Ret. │ 30 days      │ 180 days     │ 365 days      │
│ Max Retries  │ 3            │ 5            │ 10            │
│ Min Interval │ 1 minute     │ 10 seconds   │ 1 second      │
│ Priority     │ 8-10 only    │ 3-10         │ 1-10          │
│ DLQ Access   │ ✗            │ ✓            │ ✓             │
│ Custom Alert │ ✗            │ ✓            │ ✓             │
│ API Rate     │ 10 req/sec   │ 100 req/sec  │ 1000 req/sec  │
│ Support      │ Community    │ Email        │ Dedicated     │
└──────────────┴──────────────┴──────────────┴───────────────┘
```

---

## 📊 Summary: Key Trade-offs & Decision Matrix

| Decision | Options Considered | Chosen | Why |
|----------|-------------------|--------|-----|
| **Scheduling vs Execution** | Combined / Separated | Separated (3-layer) | Scalability, fault isolation, independent scaling |
| **Job Queue** | Redis Streams / SQS / Kafka | Kafka | High throughput, durability, replay, partitioning |
| **Job Definitions DB** | PostgreSQL / DynamoDB / MongoDB | PostgreSQL | Strong consistency for schedules, ACID transactions, rich indexing |
| **Execution History DB** | PostgreSQL / Cassandra / ClickHouse | Cassandra + S3 tiering | Write throughput (864M/day), time-series access, TTL, linear scaling |
| **Scheduler Coordination** | Database locking / Redis / ZooKeeper | ZooKeeper / etcd | Strong consistency, ephemeral nodes, watches for rebalancing |
| **Delivery Guarantee** | At-most-once / At-least-once / Exactly-once | At-least-once + idempotency | Practical guarantee; true exactly-once too expensive across boundaries |
| **Priority Handling** | Single queue / Multiple topics / In-memory sort | Multiple Kafka topics (priority bands) | Simple, avoids head-of-line blocking, easy weighted consumption |
| **History Retention** | All Cassandra / All S3 / Tiered | Hot/Warm/Cold tiered | 72% cost savings vs all-Cassandra; transparent to users |
| **Worker Discovery** | Service mesh / DNS / Redis registry | Redis registry + heartbeats | Low-latency capacity tracking, simple, ephemeral state |
| **Retry Mechanism** | In-process retry / Separate retry topics | Separate Kafka retry topics with delays | Decoupled, survives worker crashes, configurable delays |

## 🏗️ Technology Stack Summary

```
┌─────────────────────────────────────────────────────────┐
│  COMPUTE          │  DATA                               │
│  ─────────        │  ────                                │
│  Go (Scheduler)   │  PostgreSQL (Job Definitions)       │
│  Go (Workers)     │  Cassandra (Execution History Hot)  │
│  Go (API Service) │  S3 + Athena (History Cold)         │
│                    │  Redis Cluster (Locks, Registry)    │
│  MESSAGING         │  ZooKeeper (Coordination)           │
│  ──────────        │                                     │
│  Apache Kafka      │  OBSERVABILITY                      │
│  (3 topic types)   │  ──────────────                     │
│                    │  Prometheus + Grafana (Metrics)     │
│  INFRA             │  ELK Stack (Logs)                   │
│  ─────             │  PagerDuty (Alerting)               │
│  Kubernetes (K8s)  │  Jaeger (Distributed Tracing)       │
│  AWS / GCP         │                                     │
└─────────────────────────────────────────────────────────┘
```

## 🎯 Interview Talking Points

When presenting this design in an interview, emphasize these key insights:

1. **"The scheduler should be dumb and fast"** — It only compares timestamps and produces Kafka messages. No HTTP calls, no waiting. This is the core architectural insight.

2. **"Kafka is our shock absorber"** — The top-of-minute thundering herd is THE scaling challenge. Kafka's ability to absorb bursts and let workers consume at their pace is what makes 10K+ jobs/sec possible.

3. **"At-least-once + idempotency = effectively exactly-once"** — This shows you understand the practical reality of distributed systems. True exactly-once is a red herring in interviews.

4. **"Partition-based ownership eliminates coordination overhead"** — Each scheduler node owns a disjoint set of jobs. No distributed locking needed in the hot path.

5. **"312 TB/year demands tiered storage"** — Hot/warm/cold architecture with a unified query layer shows you think about operational costs, not just functionality.

6. **"Workers are cattle, not pets"** — Heartbeats, reapers, graceful drain, and auto-scaling mean the system self-heals from worker failures without human intervention.

---

**References**:
- Apache Airflow Architecture
- AWS EventBridge / CloudWatch Events
- Quartz Scheduler (Java)
- Uber Peloton (distributed scheduler)
- Google Borg / Kubernetes CronJob

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 12 topics (choose 2-3 based on interviewer interest)
