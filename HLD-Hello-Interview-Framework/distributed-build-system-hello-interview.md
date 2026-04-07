# Design a Distributed Build System — Hello Interview Framework

> **Question**: Design a distributed build system like Bazel Remote Execution, Gradle Build Cache, or an internal CI/CD build farm. Developers submit build requests (compile code, run tests, package artifacts). The system distributes work across a fleet of workers, caches build artifacts to avoid redundant work, and returns results. Think Google's Blaze/Bazel, Buck, or a cloud-hosted CI system at scale.
>
> **Asked at**: Google, Meta, Amazon, Microsoft, Uber
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Build Submission**: Developers submit a build request (source repo, commit SHA, build target, build config). The system queues and schedules it across available workers.
2. **Distributed Execution**: Break large builds into fine-grained tasks (compile file A, link binary B, run test C). Execute tasks in parallel across a fleet of workers. Respect dependency ordering (can't link before compile finishes).
3. **Build Artifact Caching**: Cache outputs of each task keyed by a content hash of inputs (source files + compiler version + flags). If the same task with identical inputs was built before, return the cached result — no re-execution. This is the key to fast incremental builds.
4. **Build Status & Logs**: Developers see real-time status: queued → running → pass/fail. Stream build logs in real-time. Persist logs for post-mortem debugging.
5. **Artifact Storage**: Store final build outputs (binaries, Docker images, test reports) in durable storage. Developers can download artifacts after the build completes.
6. **Priority & Fairness**: Support priority levels (P0 production hotfix > P1 CI pipeline > P2 developer experimentation). Fair scheduling across teams so one team can't monopolize all workers.

#### Nice to Have (P1)
- Remote execution API (Bazel Remote Execution protocol compatible).
- Hermetic builds (sandboxed execution — reproducible regardless of worker).
- Auto-scaling worker fleet based on queue depth.
- Build insights / analytics (build time trends, flaky test detection, cache hit rate).
- Dependency graph visualization.
- Build configuration as code (Buildfile / BUILD.bazel).
- Multi-platform builds (Linux, macOS, Windows workers).

#### Below the Line (Out of Scope)
- Source code management (assume external Git service).
- CI/CD pipeline orchestration (focus on the build execution engine, not pipeline YAML).
- Deployment to production.
- IDE integration details.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Build Latency** | < 5 min for incremental builds (with cache); < 30 min for full builds | Developer productivity — fast feedback loop |
| **Cache Hit Rate** | > 80% for incremental builds | Avoids redundant work; biggest lever for build speed |
| **Throughput** | 10K concurrent builds; 100K tasks/min | Large engineering org (5K+ developers, all building simultaneously) |
| **Availability** | 99.9% for build submission; 99.5% for workers | Builds can retry on worker failure; submission must always work |
| **Artifact Durability** | 99.99% for build outputs (30-day retention) | Developers need to download artifacts days later |
| **Consistency** | Strong for cache (same inputs → same output); eventual for status | Cache correctness is critical; status can lag by seconds |
| **Scale** | 5K developers, 50K builds/day, 1M tasks/day, 10TB artifacts/day | Large tech company scale |
| **Fairness** | No team waits > 5 min for a worker during peak | Prevent starvation during high-load periods |

### Capacity Estimation

```
Builds & Tasks:
  Developers: 5,000
  Builds/day: 50K (10 builds/developer/day)
  Build QPS: 50K / 28800 (10-hour workday) ≈ 1.7/sec avg → 20/sec peak
  Tasks/build: 20 avg (compile + link + test subtasks)
  Tasks/day: 50K × 20 = 1M tasks/day
  Task QPS: 1M / 28800 ≈ 35/sec avg → 200/sec peak
  Avg task duration: 30 seconds
  
Workers:
  Concurrent tasks at peak: 200 tasks/sec × 30s avg = 6,000 concurrent tasks
  Tasks per worker: 2 (multi-core parallelism)
  Workers needed at peak: 6,000 / 2 = 3,000 workers
  Workers at baseline: 500 (off-peak)
  Auto-scale range: 500 → 3,000

Cache:
  Unique task outputs/day: 1M tasks × 20% unique (80% cache hit) = 200K new entries/day
  Avg artifact size: 50 MB (compiled binary, test output)
  New cache data/day: 200K × 50 MB = 10 TB/day
  Cache retention: 30 days
  Total cache: 10 TB × 30 = 300 TB (S3)
  Cache index (metadata): 200K entries/day × 30 days × 1 KB = 180 GB (Redis/DB)

Log Storage:
  Avg log per task: 500 KB
  Logs/day: 1M × 500 KB = 500 GB/day
  Retention: 30 days → 15 TB (S3)

Build Metadata:
  50K builds/day × 5 KB metadata = 250 MB/day → negligible

Infrastructure:
  Scheduler: 10 pods
  Workers: 500-3,000 (auto-scaled)
  Redis: cache index + queue metadata (200 GB)
  PostgreSQL: build metadata + task records
  S3: artifacts + cache + logs (~300 TB)
  Kafka: task events, log streaming
  Cost: $200-500K/month (workers dominate)
```

---

## 2️⃣ Core Entities

### Entity 1: Build
```java
public class Build {
    String buildId;             // UUID
    String userId;              // who submitted
    String teamId;              // for fairness scheduling
    String repoUrl;             // "https://github.com/company/monorepo"
    String commitSha;           // "abc123def456"
    String buildTarget;         // "//services/payment:all" (Bazel-style target)
    BuildConfig config;         // compiler flags, platform, env vars
    BuildPriority priority;     // P0_HOTFIX, P1_CI, P2_DEV
    BuildStatus status;         // QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED
    Instant submittedAt;
    Instant startedAt;
    Instant completedAt;
    int totalTasks;
    int completedTasks;
    int cachedTasks;            // tasks served from cache (didn't execute)
    List<String> artifactUrls;  // final output URLs in S3
    String logUrl;              // aggregated build log URL
}

public enum BuildStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }
public enum BuildPriority { P0_HOTFIX, P1_CI, P2_DEV }
```

### Entity 2: Task (Unit of Work)
```java
public class Task {
    String taskId;              // UUID
    String buildId;             // parent build
    String actionDigest;        // SHA-256 hash of (inputs + command + env) — THE CACHE KEY
    String command;             // "gcc -O2 -o main.o main.c"
    List<String> inputDigests;  // SHA-256 of each input file
    List<String> dependsOn;     // task IDs this task depends on (must complete first)
    TaskStatus status;          // PENDING, QUEUED, RUNNING, SUCCEEDED, FAILED, CACHED
    String workerId;            // which worker is executing (null if not assigned)
    Instant queuedAt;
    Instant startedAt;
    Instant completedAt;
    String outputDigest;        // SHA-256 of output (for caching)
    String outputUrl;           // S3 URL of output artifact
    String logUrl;              // task-level log URL
    int retryCount;             // 0, 1, 2 (max 3 retries on worker failure)
}

public enum TaskStatus { PENDING, QUEUED, RUNNING, SUCCEEDED, FAILED, CACHED }
```

### Entity 3: CacheEntry
```java
public class CacheEntry {
    String actionDigest;        // SHA-256 hash of (inputs + command + env) — primary key
    String outputDigest;        // SHA-256 of the output artifact
    String outputUrl;           // S3 URL where the artifact is stored
    long outputSizeBytes;
    Instant createdAt;
    Instant lastAccessedAt;     // for LRU eviction
    int hitCount;               // analytics: how many times this cache entry was used
}
```

### Entity 4: Worker
```java
public class Worker {
    String workerId;            // UUID
    String hostname;
    WorkerStatus status;        // IDLE, BUSY, DRAINING, OFFLINE
    String platform;            // "linux-x86_64", "macos-arm64", "windows-x86_64"
    int cpuCores;
    long memoryMb;
    long diskMb;
    int currentTasks;           // tasks currently executing (0, 1, or 2)
    int maxConcurrentTasks;     // typically 2
    Instant lastHeartbeatAt;
    String currentBuildId;      // for affinity (prefer sending related tasks to same worker)
}

public enum WorkerStatus { IDLE, BUSY, DRAINING, OFFLINE }
```

### Entity 5: BuildConfig
```java
public class BuildConfig {
    String platform;            // "linux-x86_64"
    String compilerVersion;     // "gcc-13.2"
    Map<String, String> envVars;// {"CC": "gcc", "OPTIMIZE": "2"}
    List<String> buildFlags;    // ["-O2", "--enable-tests"]
    int timeoutSeconds;         // max task execution time (default: 600)
    boolean hermetic;           // run in sandbox? (reproducible builds)
}
```

---

## 3️⃣ API Design

### 1. Submit Build
```
POST /api/v1/builds

Request:
{
  "repo_url": "https://github.com/company/monorepo",
  "commit_sha": "abc123def456",
  "build_target": "//services/payment:all",
  "priority": "P1_CI",
  "config": {
    "platform": "linux-x86_64",
    "compiler_version": "gcc-13.2",
    "build_flags": ["-O2", "--enable-tests"],
    "timeout_seconds": 600,
    "hermetic": true
  }
}

Response (202 Accepted):
{
  "build_id": "build_xyz789",
  "status": "QUEUED",
  "submitted_at": "2025-03-15T10:00:00Z",
  "estimated_duration_sec": 180,
  "status_url": "/api/v1/builds/build_xyz789",
  "log_stream_url": "wss://builds.company.com/ws/builds/build_xyz789/logs"
}
```

### 2. Get Build Status
```
GET /api/v1/builds/{build_id}

Response (200 OK):
{
  "build_id": "build_xyz789",
  "status": "RUNNING",
  "submitted_at": "2025-03-15T10:00:00Z",
  "started_at": "2025-03-15T10:00:05Z",
  "progress": {
    "total_tasks": 47,
    "completed_tasks": 32,
    "cached_tasks": 28,
    "running_tasks": 5,
    "pending_tasks": 10,
    "failed_tasks": 0
  },
  "cache_hit_rate": 0.875,
  "elapsed_seconds": 45
}
```

### 3. Cancel Build
```
POST /api/v1/builds/{build_id}/cancel

Response (200 OK):
{
  "build_id": "build_xyz789",
  "status": "CANCELLED",
  "tasks_cancelled": 15,
  "tasks_already_completed": 32
}
```

### 4. Get Build Artifacts
```
GET /api/v1/builds/{build_id}/artifacts

Response (200 OK):
{
  "artifacts": [
    {
      "name": "payment-service",
      "type": "binary",
      "url": "https://artifacts.company.com/build_xyz789/payment-service",
      "size_bytes": 52428800,
      "digest": "sha256:abc123..."
    },
    {
      "name": "test-report.xml",
      "type": "test_report",
      "url": "https://artifacts.company.com/build_xyz789/test-report.xml",
      "size_bytes": 102400
    }
  ]
}
```

### 5. Stream Build Logs (WebSocket)
```
WSS /ws/builds/{build_id}/logs

Server sends:
{"task_id": "task_001", "level": "INFO", "message": "Compiling main.c...", "timestamp": "..."}
{"task_id": "task_001", "level": "INFO", "message": "Compilation successful (0.8s)", "timestamp": "..."}
{"task_id": "task_002", "level": "INFO", "message": "[CACHED] Linking libutil.so — cache hit", "timestamp": "..."}
{"task_id": "task_015", "level": "ERROR", "message": "Test payment_test.go FAILED: assertion error line 42", "timestamp": "..."}
{"type": "build_complete", "status": "FAILED", "duration_sec": 127, "cache_hit_rate": 0.82}
```

### 6. Check Cache (Internal API — used by workers)
```
GET /api/v1/cache/{action_digest}

Response (200 OK — cache hit):
{
  "hit": true,
  "output_digest": "sha256:def456...",
  "output_url": "https://cache.company.com/outputs/sha256:def456...",
  "output_size_bytes": 5242880
}

Response (404 — cache miss):
{
  "hit": false
}
```

---

## 4️⃣ Data Flow

### Flow 1: Build Submission → Task Graph → Scheduling → Execution
```
1. Developer submits: POST /builds { repo, commit, target, config }
   ↓
2. Build Service:
   a. Create build record in PostgreSQL (status: QUEUED)
   b. Fetch source code: git clone + checkout commit SHA
   c. Parse build graph: analyze BUILD files to determine dependency DAG
      Example DAG for "//services/payment:all":
        compile(main.c) → link(payment-service) → test(payment_test)
        compile(util.c) ↗                        ↗
        compile(db.c)  ↗                         
      → 5 tasks, 3 layers of parallelism
   d. For each task, compute actionDigest = SHA-256(input_files + command + env + compiler)
   e. Produce tasks to Kafka topic "tasks-pending"
   ↓
3. Cache Check (before scheduling):
   For each task, check: does cache have this actionDigest?
   → GET cache:{actionDigest} in Redis
   
   Cache HIT (80% of tasks in incremental builds):
     - Task status → CACHED
     - Copy cached output to build's artifact directory
     - Skip execution entirely
     - Update build progress (completedTasks++, cachedTasks++)
   
   Cache MISS (20% of tasks):
     - Task remains QUEUED
     - Proceed to worker assignment
   ↓
4. Scheduler assigns task to worker:
   - Pick worker: least-loaded with matching platform
   - Prefer worker already assigned to same build (data locality)
   - Task status → RUNNING, workerId set
   - Worker pulls task from its assigned queue
   ↓
5. Worker executes task:
   a. Download input files from CAS (Content-Addressable Storage)
   b. Execute command in sandbox (if hermetic)
   c. Capture stdout/stderr → stream to log service
   d. Upload output to CAS → get outputDigest
   e. Report result: { taskId, status, outputDigest, outputUrl }
   ↓
6. Post-execution:
   a. Store output in cache: SET cache:{actionDigest} → {outputDigest, outputUrl}
   b. Update task record in PostgreSQL
   c. Check: are dependent tasks now unblocked?
      → If all dependencies of task X are complete → task X becomes QUEUED
   d. Update build progress
   e. If all tasks complete → build status → SUCCEEDED or FAILED
   ↓
7. Notify developer:
   - WebSocket: real-time status update
   - If build complete: webhook / Slack notification
```

### Flow 2: Content-Addressable Cache Lookup
```
Every task has an actionDigest = SHA-256(sorted(input_digests) + command + env_vars + compiler_version)

This means:
  - Same source file + same compiler + same flags = same actionDigest
  - If ANYTHING changes (even one byte of one file) → different digest → cache miss
  - This guarantees cache correctness: same inputs ALWAYS produce same output

Cache lookup flow:
  1. Compute actionDigest for the task
  2. Check Redis: GET cache:{actionDigest}
  3. If HIT:
     a. Verify output exists in S3: HEAD {outputUrl}
     b. If exists → return cached output (0 seconds, no execution)
     c. If missing (evicted from S3) → treat as cache miss
  4. If MISS:
     a. Execute task on worker
     b. Upload output to S3
     c. Store in Redis: SET cache:{actionDigest} {outputDigest, outputUrl, size, timestamp}
     
Cache key = actionDigest (deterministic, content-based)
Cache value = outputDigest + outputUrl
Cache storage = Redis (index, 200 GB) + S3 (actual artifacts, 300 TB)
```

### Flow 3: Worker Heartbeat & Failure Recovery
```
Workers send heartbeat every 10 seconds:
  POST /internal/workers/{worker_id}/heartbeat
  { "status": "BUSY", "current_tasks": ["task_001", "task_002"], "cpu": 75, "memory": 60 }

If no heartbeat for 30 seconds:
  1. Scheduler marks worker as OFFLINE
  2. All tasks assigned to this worker → status back to QUEUED
  3. Tasks re-assigned to other workers (retry_count++)
  4. If retry_count > 3 → task FAILED → build FAILED

Worker graceful shutdown (DRAINING):
  1. Worker signals: "draining, don't send new tasks"
  2. Finish currently running tasks (up to timeout)
  3. Report results
  4. Worker goes OFFLINE
  
This enables zero-downtime worker fleet updates.
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                            CLIENTS                                              │
│  [Developer CLI]  [CI/CD Pipeline]  [IDE Plugin]  [Web Dashboard]              │
└──────────────────────────┬──────────────────────────────────────────────────────┘
                           │ HTTPS / WSS
                           ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY + LOAD BALANCER                                   │
│  • Auth (API key / OAuth) • Rate limiting • WebSocket upgrade                  │
└──────┬────────────────────────────┬──────────────────────┬─────────────────────┘
       │                            │                      │
       ▼                            ▼                      ▼
┌──────────────────┐    ┌──────────────────────┐   ┌──────────────────────┐
│  BUILD SERVICE    │    │  SCHEDULER SERVICE    │   │  LOG SERVICE          │
│  (10 pods)        │    │  (5 pods, 1 leader)   │   │  (10 pods)            │
│                   │    │                       │   │                       │
│ • Accept builds   │    │ • Task queue mgmt     │   │ • Stream logs via WS  │
│ • Parse DAG       │    │ • Worker assignment   │   │ • Persist to S3       │
│ • Cache check     │    │ • Priority scheduling │   │ • Search (ES)         │
│ • Track progress  │    │ • Fairness (per-team) │   │                       │
│ • Notify user     │    │ • Failure recovery    │   │                       │
└──────┬───────────┘    └──────────┬────────────┘   └───────────────────────┘
       │                           │
       ▼                           ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                        KAFKA EVENT BUS                                          │
│                                                                                │
│  Topics:                                                                       │
│    tasks-pending     — new tasks ready for scheduling                          │
│    tasks-assigned    — task → worker assignments                               │
│    tasks-completed   — execution results from workers                          │
│    build-events      — status changes for notification                         │
│    log-events        — task log lines for streaming + persistence              │
└──────────────────────────────┬─────────────────────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                        WORKER FLEET (500 — 3,000 workers)                      │
│                                                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       ┌──────────┐  │
│  │ Worker 1  │  │ Worker 2  │  │ Worker 3  │  │ Worker 4  │ ...  │Worker 3000│ │
│  │ Linux x86 │  │ Linux x86 │  │ macOS ARM │  │ Linux x86 │       │ Win x86  │  │
│  │ 8 core    │  │ 16 core   │  │ 12 core   │  │ 8 core    │       │ 8 core   │  │
│  │           │  │           │  │           │  │           │       │          │  │
│  │ • Pull    │  │ • Pull    │  │ • Pull    │  │ • Pull    │       │ • Pull   │  │
│  │   task    │  │   task    │  │   task    │  │   task    │       │   task   │  │
│  │ • Download│  │ • Download│  │ • Download│  │ • Download│       │• Download│  │
│  │   inputs  │  │   inputs  │  │   inputs  │  │   inputs  │       │  inputs  │  │
│  │ • Execute │  │ • Execute │  │ • Execute │  │ • Execute │       │• Execute │  │
│  │   in      │  │   in      │  │   in      │  │   in      │       │  in      │  │
│  │   sandbox │  │   sandbox │  │   sandbox │  │   sandbox │       │  sandbox │  │
│  │ • Upload  │  │ • Upload  │  │ • Upload  │  │ • Upload  │       │• Upload  │  │
│  │   output  │  │   output  │  │   output  │  │   output  │       │  output  │  │
│  │ • Report  │  │ • Report  │  │ • Report  │  │ • Report  │       │• Report  │  │
│  │   result  │  │   result  │  │   result  │  │   result  │       │  result  │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       └──────────┘  │
│                                                                                │
│  Auto-scaling: Kubernetes HPA on Kafka consumer lag + queue depth              │
│  Scale up: queue depth > 1000 for 2 min → add 100 workers                    │
│  Scale down: idle workers > 50% for 10 min → remove 20% workers              │
└────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────┐
│                          STORAGE LAYER                                         │
│                                                                                │
│  ┌────────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐  │
│  │ CAS (S3)            │  │ REDIS CLUSTER     │  │ POSTGRESQL               │  │
│  │ Content-Addressable │  │ (Cache Index)     │  │ (Build Metadata)         │  │
│  │ Storage             │  │                   │  │                          │  │
│  │                     │  │ cache:{digest}    │  │ • builds table           │  │
│  │ • Source inputs     │  │  → output URL     │  │ • tasks table            │  │
│  │ • Compiled outputs  │  │  TTL: 30 days     │  │ • workers table          │  │
│  │ • Test results      │  │                   │  │ • team_quotas            │  │
│  │ • Build logs        │  │ queue:{priority}  │  │                          │  │
│  │                     │  │  → pending tasks  │  │ Sharded: build_id        │  │
│  │ Key: SHA-256 digest │  │                   │  │                          │  │
│  │ ~300 TB total       │  │ worker:{id}       │  │                          │  │
│  │ 30-day retention    │  │  → heartbeat      │  │                          │  │
│  │ S3 lifecycle policy │  │                   │  │                          │  │
│  └────────────────────┘  │ ~200 GB            │  │                          │  │
│                          └──────────────────┘  └──────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────┐
│                       MONITORING & OBSERVABILITY                               │
│  Prometheus + Grafana: build duration, cache hit rate, queue depth, worker util│
│  Key alert: queue depth > 5K for 5 min → scale workers + page on-call        │
│  Key dashboard: cache hit rate (target > 80%), P99 build time, worker count   │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Tech | Scale |
|-----------|---------|------|-------|
| **Build Service** | Accept builds, parse DAG, cache check, track progress, notify | Go/Java (stateless) | 10 pods |
| **Scheduler Service** | Task queue, worker assignment, priority, fairness, failure recovery | Go (leader-elected) | 5 pods (1 active leader) |
| **Worker Fleet** | Execute build tasks in sandboxes, upload outputs, report results | Go/Rust (stateless) | 500-3,000 (auto-scaled) |
| **Log Service** | Stream logs via WebSocket, persist to S3, index in ES for search | Go (stateless) | 10 pods |
| **CAS (S3)** | Content-addressable storage for inputs, outputs, logs | S3 | 300 TB, 30-day retention |
| **Redis** | Cache index, task queues, worker heartbeats | Redis Cluster | 200 GB |
| **PostgreSQL** | Build records, task records, worker registry, team quotas | PostgreSQL (primary + 2 RR) | Sharded by build_id |
| **Kafka** | Task events, log streaming, build notifications | Kafka (5 brokers) | 5 topics, 1M events/day |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Content-Addressable Caching — The Key to Fast Builds

**The Problem**: A full build of a large monorepo takes 30 minutes. But developers typically change 1-2 files between builds. If we can identify that 95% of tasks haven't changed, we can skip them and serve cached outputs — reducing a 30-minute build to 3 minutes.

**How the Action Digest Works**:
```
For each task (e.g., "compile main.c"):

actionDigest = SHA-256(
  sorted(input_file_digests) +    // SHA-256 of main.c, main.h, util.h
  command +                        // "gcc -O2 -c main.c -o main.o"
  env_vars +                       // {"CC": "gcc-13.2", "CFLAGS": "-O2"}
  compiler_digest +                // SHA-256 of the gcc binary itself
  platform                         // "linux-x86_64"
)

This is a PURE FUNCTION: same inputs → same digest → same output.

If the developer changes util.c but NOT main.c:
  - compile(main.c) → same actionDigest → CACHE HIT (skip execution)
  - compile(util.c) → different actionDigest → CACHE MISS (must execute)
  - link(payment-service) → different (because util.o changed) → CACHE MISS
  
Result: 1 out of 3 compile tasks cached, 0 out of 1 link tasks cached.
Net saving: 33% (in practice, with larger builds, savings are 70-90%).
```

**Cache Architecture**:
```
Two-layer cache:

Layer 1: Redis (Cache Index) — answers "does this digest exist?"
  Key: cache:{actionDigest}
  Value: {outputDigest, outputUrl, sizeBytes, createdAt, hitCount}
  TTL: 30 days (refresh on access)
  Lookup: < 1ms
  Size: 200 GB (6M entries × ~30 bytes key + 100 bytes value)

Layer 2: S3 (Content-Addressable Storage) — stores actual artifacts
  Key: outputs/{outputDigest}
  Value: compiled binary, test output, etc.
  Lifecycle: 30-day retention, transition to Glacier after 14 days for cold artifacts
  Size: ~300 TB

Cache write path (after task execution):
  1. Worker uploads output to S3: PUT s3://cas/outputs/{outputDigest}
  2. Worker reports: { actionDigest, outputDigest, outputUrl }
  3. Build Service writes to Redis: SET cache:{actionDigest} {...}

Cache read path (before scheduling):
  1. Build Service computes actionDigest for each task
  2. Redis lookup: GET cache:{actionDigest}
  3. If HIT: verify S3 object exists (HEAD request), return cached output
  4. If MISS: schedule task for execution
```

**Cache Correctness Guarantee**:
```
The cache is CORRECT by construction because:
  1. actionDigest includes ALL inputs (source files, compiler, flags, platform)
  2. If ANY input changes, the digest changes → different cache key → miss
  3. Outputs are stored by content hash (outputDigest) → immutable, content-addressable
  4. No cache invalidation needed — stale entries simply aren't looked up
  
The only way to get a wrong cache hit:
  - SHA-256 collision (probability: 1 in 2^128 ≈ impossible)
  - Non-deterministic build (e.g., timestamp in output) — solved by hermetic builds
```

**Cache Eviction**:
```
Strategy: LRU with 30-day maximum TTL
  - On access: update lastAccessedAt, reset TTL to 30 days
  - If cache exceeds 300 TB: evict least-recently-accessed entries first
  - Entries not accessed for 30 days: auto-expire (S3 lifecycle policy)

Monitoring:
  - Cache hit rate: target > 80% for incremental builds
  - If hit rate drops below 70%: investigate (are developers changing too many files? compiler upgrade?)
  - Cache size growth rate: ~10 TB/day → 300 TB/month → need S3 lifecycle management
```

---

### Deep Dive 2: Task Scheduling — Priority, Fairness & Dependency Ordering

**The Problem**: 1M tasks/day across 5K developers. Some tasks are P0 production hotfixes (must execute immediately), some are P1 CI pipeline (important but can wait seconds), some are P2 developer experiments (lowest priority). No single team should monopolize workers.

**Priority Queue Design (Redis Sorted Sets)**:
```
Three priority queues in Redis:
  ZADD queue:P0 {timestamp} {task_json}   // P0: production hotfix (execute immediately)
  ZADD queue:P1 {timestamp} {task_json}   // P1: CI pipeline (< 30s wait)
  ZADD queue:P2 {timestamp} {task_json}   // P2: developer build (< 5 min wait)

Scheduler dequeue logic:
  1. Try ZPOPMIN queue:P0 → if task found, assign to worker (P0 always first)
  2. Else try ZPOPMIN queue:P1 → assign to worker
  3. Else try ZPOPMIN queue:P2 → assign to worker
  
Within same priority: FIFO by timestamp (oldest first).
```

**Fairness — Per-Team Quotas**:
```
Problem: Team A submits 1000 builds at once, consuming all workers.
         Team B can't build anything for 30 minutes.

Solution: Weighted Fair Queuing
  - Each team has a weight (proportional to team size or allocated budget)
  - Team A (100 devs): weight 100 → max 50% of workers
  - Team B (50 devs): weight 50 → max 25% of workers
  - Team C (30 devs): weight 30 → max 15% of workers
  - Burst pool: 10% of workers available to any team (first-come-first-serve)

Implementation:
  - Track per-team active tasks in Redis: INCR team_tasks:{team_id}
  - Before assigning task: check team hasn't exceeded its quota
  - If over quota: task stays in queue, scheduler picks from another team
  - If all teams are under quota: assign to highest-priority task regardless of team
  
This ensures: during peak, every team gets at least their fair share.
During off-peak, any team can use idle workers (burst pool).
```

**Dependency Ordering**:
```
Build DAG example:
  compile(A) ──→ link(AB) ──→ test(payment)
  compile(B) ──↗

Task states:
  compile(A): QUEUED (no dependencies)
  compile(B): QUEUED (no dependencies)  
  link(AB):   PENDING (depends on compile(A) + compile(B))
  test:       PENDING (depends on link(AB))

When compile(A) completes:
  - Check: are ALL dependencies of link(AB) complete?
    - compile(A): ✅, compile(B): ❌ → link(AB) stays PENDING

When compile(B) completes:
  - Check: are ALL dependencies of link(AB) complete?
    - compile(A): ✅, compile(B): ✅ → link(AB) moves to QUEUED → scheduled

This is a simple topological execution of the DAG.
Each task completion triggers a check on all dependent tasks.
```

---

### Deep Dive 3: Worker Management — Auto-Scaling, Failure Recovery & Sandboxing

**The Problem**: Worker fleet needs to handle 200-6,000 concurrent tasks. Workers can crash mid-execution. Builds must be reproducible (same source → same output regardless of which worker).

**Auto-Scaling**:
```
Scaling signals:
  1. Queue depth: ZCARD queue:P0 + ZCARD queue:P1 + ZCARD queue:P2
  2. Avg task wait time: time from QUEUED to RUNNING
  3. Worker CPU utilization: avg across fleet

Scale-up triggers:
  - Queue depth > 1,000 for 2 minutes → add 100 workers
  - P0 queue depth > 0 for 30 seconds → add 50 workers (urgent)
  - Avg wait time > 60 seconds → add 50 workers

Scale-down triggers:
  - > 50% of workers idle for 10 minutes → drain 20% of fleet
  - Weekend/night: reduce to 200 workers minimum

Implementation: Kubernetes HPA with custom metrics from Prometheus
  - Workers are Kubernetes pods with resource requests (4 CPU, 8 GB RAM, 50 GB disk)
  - Scale range: min=200, max=3000
  - Spot instances for 70% of fleet (builds are retryable → tolerate preemption)
```

**Failure Recovery**:
```
Worker crash detection:
  - Worker sends heartbeat every 10 seconds (Redis: SETEX worker:{id} 30 {status})
  - Scheduler checks: if worker hasn't heartbeated in 30 seconds → mark OFFLINE
  
Recovery:
  1. All tasks assigned to dead worker → status back to QUEUED
  2. Tasks re-enter the scheduling queue with retry_count++
  3. If retry_count > 3 → task FAILED → build FAILED
  4. Partial outputs from dead worker are ignored (tasks are idempotent)

Why retries work:
  - Tasks are pure functions (deterministic: same inputs → same output)
  - No shared state between tasks (each runs in isolated sandbox)
  - A task that failed on Worker A should succeed on Worker B
  - Exception: if failure is due to the task itself (code bug), retries won't help
    → After 3 retries, fail the task and let the developer investigate
```

**Hermetic Sandboxing**:
```
Problem: Worker A has gcc-13.2 installed, Worker B has gcc-13.1. Same source code
produces different output → cache is wrong, builds are non-reproducible.

Solution: Hermetic execution in containers
  - Each task runs inside a Docker container with pinned tool versions
  - Container image digest is part of the actionDigest (cache key)
  - No access to host filesystem, network, or other tasks
  - All inputs are explicitly declared and downloaded from CAS

Execution flow:
  1. Worker creates a fresh container from pinned image (e.g., ubuntu:22.04 + gcc-13.2)
  2. Mount input files (read-only) from CAS download
  3. Execute command inside container
  4. Capture output files from container's output directory
  5. Upload outputs to CAS
  6. Destroy container

This guarantees: same actionDigest → same output, on any worker, at any time.
```

---

### Deep Dive 4: Build Graph Analysis & Parallelism Optimization

**The Problem**: A naive build executes tasks sequentially — 50 tasks × 30 seconds = 25 minutes. But many tasks are independent and can run in parallel. Analyzing the dependency graph and maximizing parallelism can reduce this to 3 minutes.

**DAG Construction**:
```
Build files (like Bazel BUILD) define targets and dependencies:

  cc_library(name="util", srcs=["util.c"], hdrs=["util.h"])
  cc_library(name="db", srcs=["db.c"], deps=[":util"])
  cc_binary(name="payment", srcs=["main.c"], deps=[":util", ":db"])
  cc_test(name="payment_test", srcs=["payment_test.c"], deps=[":payment"])

This produces a DAG:
  compile(util.c) → compile(db.c) → compile(main.c) → link(payment) → test(payment_test)
                  ↗                ↗

Critical path analysis:
  Longest path: compile(util) → compile(db) → compile(main) → link → test = 5 tasks
  With 30s/task: critical path = 150 seconds (2.5 min)
  
  But compile(util), compile(db), compile(main) can partially overlap:
    t=0:   compile(util.c) starts
    t=30:  compile(util.c) done → compile(db.c) + compile(main.c) start simultaneously
    t=60:  compile(db.c) done, compile(main.c) done → link(payment) starts
    t=90:  link done → test(payment_test) starts
    t=120: test done
  
  Total: 120 seconds (vs 150 sequential) — parallelism saves 20%
  In real builds with 100+ tasks and wide DAGs: parallelism saves 70-90%
```

**Parallelism Optimization**:
```
At any point, tasks in QUEUED state are "ready" (all dependencies satisfied).
The scheduler assigns ALL ready tasks in parallel.

Wide DAG example (100 source files, all independent):
  compile(file_001.c) ──→
  compile(file_002.c) ──→  link(service) → test(service_test)
  ...                  ──→
  compile(file_100.c) ──→

  At t=0: all 100 compile tasks are QUEUED simultaneously
  With 100 workers: all 100 run in parallel → 30 seconds total
  Then link (30s) + test (30s) = 90 seconds total
  Sequential would be: 100 × 30 + 30 + 30 = 3,060 seconds (51 min)
  
  Parallelism speedup: 34× faster
```

---

### Deep Dive 5: Log Streaming & Build Observability

**The Problem**: Developers need to see build output in real-time — "what's compiling right now? why is my build stuck? which test failed?" Logs must stream live during the build and be persisted for later debugging.

**Real-Time Log Streaming Architecture**:
```
1. Worker executes task, captures stdout/stderr line by line
2. Worker publishes each log line to Kafka topic "log-events":
   { build_id, task_id, timestamp, level, message }
3. Log Service consumes from Kafka:
   a. WebSocket fanout: for each connected client watching this build,
      push the log line via WebSocket
   b. S3 persistence: batch log lines per task, upload to S3 every 10 seconds
   c. Elasticsearch index: for log search (optional)

Client connection:
  Developer opens: WSS /ws/builds/{build_id}/logs
  Log Service subscribes to Kafka partition for this build_id
  Pushes matching log lines to the WebSocket

After build completes:
  Full logs available at: GET /builds/{build_id}/logs → S3 presigned URL
  Per-task logs: GET /builds/{build_id}/tasks/{task_id}/logs
```

**Build Dashboard Metrics**:
```
Real-time (updated every 5 seconds):
  - Tasks: 32/47 complete (28 cached, 4 executed, 5 running, 10 pending)
  - Cache hit rate: 87.5%
  - Elapsed: 45 seconds
  - Estimated remaining: 30 seconds
  - Worker utilization: 85%

Historical (for build insights):
  - Build time trend (last 30 days): is build getting slower?
  - Cache hit rate trend: dropped from 85% to 60% → investigate (compiler upgrade?)
  - Flaky test detection: test X fails 10% of the time → flag for investigation
  - Slowest tasks: compile(giant_proto.cc) takes 120s → optimize or split
```

---

### Deep Dive 6: Handling Monorepo Scale — 100K+ Files, 10K+ Targets

**The Problem**: Large tech companies use monorepos with millions of files. Building "the entire repo" is impractical. We need to identify the minimum set of tasks affected by a change.

**Affected Target Analysis**:
```
When developer changes file X:
  1. Find all build targets that depend on X (transitively)
  2. Only build those targets — ignore everything else
  
Example:
  Developer changes util.h
  Dependency analysis:
    util.h → util.c (direct dep)
    util.h → db.c (includes util.h)
    util.h → main.c (includes util.h)
  Affected targets: compile(util), compile(db), compile(main), link(payment), test(payment_test)
  Unaffected: compile(auth.c), link(auth-service), ... (1000 other targets)
  
Result: build 5 targets instead of 1005 → 99.5% reduction in work
Combined with caching: the 5 affected targets have 0% cache hit (changed inputs),
but the build only takes 2 minutes instead of 30.
```

**Build Graph Caching**:
```
The dependency graph itself takes time to compute (parsing 10K BUILD files).
Cache the graph keyed by the Git tree hash:
  - If no BUILD files changed since last commit → reuse cached graph
  - If BUILD files changed → recompute only the affected subgraph
  
Graph cache key: SHA-256(all BUILD file contents)
Graph cache hit rate: ~95% (BUILD files change rarely compared to source files)
Savings: 5-10 seconds per build for graph computation
```

---

## Summary: Key Design Decisions

| Decision | Chosen | Why | Tradeoff |
|----------|--------|-----|----------|
| **Cache key** | Content-hash of all inputs (actionDigest) | Correct by construction — no invalidation needed, no stale cache | Computing SHA-256 of all inputs adds ~1-2s per task; worth it for 80%+ cache hits |
| **Cache storage** | Redis index + S3 artifacts | Redis for fast lookup (< 1ms), S3 for cheap durable storage (300 TB) | Two systems to manage; Redis failure doesn't lose data (just cache misses) |
| **Task scheduling** | Priority queues + weighted fair queuing | P0 hotfixes get immediate execution; no team monopolizes workers | Complex scheduler logic; need to tune weights |
| **Workers** | Stateless, auto-scaled, hermetic containers | Reproducible builds, failure recovery via retry, cost-efficient scaling | Container overhead (~2s per task); cold start when scaling up |
| **Log streaming** | Kafka → WebSocket + S3 | Real-time developer feedback + durable storage for debugging | Kafka infrastructure; WebSocket connection management |
| **DAG execution** | Topological order with maximum parallelism | Wide DAGs get 10-30× speedup from parallel execution | Need enough workers for parallelism; diminishing returns past critical path length |
| **Failure handling** | 3 retries with exponential backoff | Tasks are idempotent pure functions; transient failures self-heal | Deterministic failures waste 3× resources before failing; mitigated by fast failure detection |
| **Auto-scaling** | Kubernetes HPA + spot instances (70%) | Scale 500→3,000 workers based on queue depth; 70% cost savings with spot | Spot preemption causes task retries (acceptable — tasks are retryable) |

## Interview Talking Points

1. **"Content-addressable caching keyed by SHA-256 of all inputs — correct by construction, no invalidation"** — The actionDigest includes every input file, the compiler binary, flags, and platform. If anything changes, the digest changes → cache miss. Same inputs always produce the same output. No TTL-based invalidation, no stale cache — the hash IS the correctness guarantee. This gives us 80%+ cache hit rate on incremental builds.

2. **"Two-layer cache: Redis for the index (< 1ms lookup), S3 for artifacts (300 TB durable storage)"** — Redis answers "does this digest exist?" in < 1ms. S3 stores the actual 50 MB compiled binary. If Redis goes down, we get cache misses (builds are slower but correct). If S3 loses an object, the Redis entry points to nothing → treated as cache miss → task re-executes and re-caches.

3. **"Priority queues with per-team fairness quotas prevent starvation during peak"** — P0 production hotfixes always execute first (Redis sorted set ZPOPMIN). Within the same priority, tasks are FIFO. Per-team quotas (weighted by team size) ensure Team A's 1,000-build batch doesn't block Team B's single urgent build. 10% burst pool allows any team to use idle capacity.

4. **"Hermetic builds in containers: same actionDigest → same output on any worker at any time"** — Each task runs in a Docker container with pinned tool versions. The container image digest is part of the cache key. No access to host filesystem or network. This guarantees reproducibility — the cache is always correct because the build environment is deterministic.

5. **"Auto-scaling 500→3,000 workers on queue depth, 70% spot instances for cost efficiency"** — Kubernetes HPA watches queue depth. If > 1,000 tasks waiting for 2 minutes → scale up 100 workers. Spot instances are 70% cheaper; if preempted, tasks retry on other workers (tasks are idempotent). Nights and weekends: scale down to 200 workers.

6. **"DAG-based scheduling: compile 100 independent files in parallel → 34× speedup over sequential"** — The build graph is a DAG. All tasks with satisfied dependencies are QUEUED simultaneously. With enough workers, the build time equals the critical path (longest chain), not the sum of all tasks. Wide DAGs (100 independent compiles) get massive parallelism benefits.

7. **"Worker failure recovery: heartbeat timeout → re-queue tasks → retry on another worker"** — Workers heartbeat every 10 seconds. 30-second timeout → all tasks from dead worker go back to QUEUED with retry_count++. Tasks are pure functions — retry on a different worker produces identical results. After 3 retries, fail (probably a code bug, not infrastructure).

8. **"Affected target analysis: developer changes 1 file → build 5 targets instead of 1,000"** — Reverse dependency analysis from the changed file identifies the minimal set of affected build targets. Combined with caching (80% of those 5 targets may still be cached), a change to 1 file in a million-file monorepo builds in < 3 minutes.

---

## Related System Design Problems

| Problem | Overlap | Key Difference |
|---------|---------|----------------|
| **Job Scheduler** | Task queuing, worker assignment, failure recovery | Build system has DAG dependencies between tasks; job scheduler typically has independent jobs |
| **CI/CD Pipeline (GitHub Actions)** | Build execution, log streaming | Pipeline orchestrates multi-step workflows; build system focuses on fine-grained task parallelism |
| **MapReduce / Spark** | Distributed execution, DAG of stages, failure recovery | MapReduce processes data; build system processes code compilation |
| **Content Delivery (CDN)** | Content-addressable storage, caching | CDN caches content for reads; build cache caches computation results |
| **Distributed Task Queue (Celery)** | Task queue, worker pool, retries | Build system adds DAG ordering, content-addressable caching, and hermetic execution |

---

**Created**: April 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (pick 2-3 based on interviewer interest)
