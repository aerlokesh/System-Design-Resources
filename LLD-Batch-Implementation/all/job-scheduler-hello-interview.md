# Job Scheduler - HELLO Interview Framework

> **Companies**: Microsoft, Salesforce  
> **Difficulty**: Medium  
> **Pattern**: Strategy (scheduling algorithms) + PriorityBlockingQueue  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Job Scheduler?

A system that manages task execution across a pool of worker threads. Jobs have priorities, estimated durations, and lifecycle states. The scheduler picks which job runs next based on a **configurable scheduling policy**.

**Real-world**: cron jobs, Kubernetes CronJobs, CI/CD pipelines (Azure DevOps), AWS Step Functions, Apache Airflow DAGs, Windows Task Scheduler, Azure Functions timer triggers.

### For L63 Microsoft Interview

1. **Strategy Pattern**: Multiple scheduling algorithms (Priority, FIFO, SJF) swappable at runtime
2. **PriorityBlockingQueue**: Thread-safe, auto-sorted — explain why over PriorityQueue
3. **Comparable**: Job ordering by priority (desc) then creation time (FIFO tiebreak)
4. **ExecutorService**: Worker thread pool for parallel execution
5. **Job lifecycle**: PENDING → RUNNING → COMPLETED/FAILED/CANCELLED
6. **Error isolation**: Failed job doesn't crash worker thread

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What types of jobs?" → Generic Runnable tasks with name and priority
- "Priority levels?" → LOW, MEDIUM, HIGH, CRITICAL
- "Concurrent execution?" → Yes, configurable worker thread pool
- "What if a job fails?" → Mark FAILED, don't crash worker, continue processing
- "Cancel pending jobs?" → Yes, only PENDING (not running)

### Functional Requirements (P0)
1. **Submit jobs** with name, priority, task (Runnable), estimated duration
2. **Schedule** based on configurable strategy (Priority, FIFO, SJF)
3. **Execute** via worker thread pool (configurable size)
4. **Job lifecycle**: PENDING → RUNNING → COMPLETED/FAILED
5. **Cancel** pending jobs (not running ones)
6. **Error handling**: Failed task marks job FAILED, worker survives

### Non-Functional
- Thread-safe job submission from multiple threads
- O(log N) insert/dequeue (PriorityBlockingQueue)
- Graceful shutdown (finish running, don't start new)

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌───────────────────────────────────────────────────────┐
│                   JobScheduler                        │
│  - jobQueue: PriorityBlockingQueue<Job>               │
│  - workerPool: ExecutorService (N threads)            │
│  - strategy: SchedulingStrategy                       │
│  - allJobs: ConcurrentHashMap<String, Job>            │
│  - submitJob(name, task, priority, duration) → Job    │
│  - processAll() / processNext()                       │
│  - cancelJob(jobId) → boolean                         │
│  - shutdown()                                         │
└───────────────────┬───────────────────────────────────┘
                    │ manages
          ┌─────────┼─────────┐
          ▼         ▼         ▼
┌──────────────┐ ┌──────────────────┐ ┌──────────────────────┐
│     Job      │ │SchedulingStrategy│ │  ExecutorService     │
│ implements   │ │  (interface)     │ │  (worker thread pool)│
│ Comparable   │ ├──────────────────┤ │                      │
│              │ │ PriorityScheduling│ │ Fixed thread pool    │
│ - id, name  │ │ FIFOScheduling    │ │ of N workers         │
│ - task      │ │ SJFScheduling     │ │                      │
│ - priority  │ └──────────────────┘ └──────────────────────┘
│ - status    │
│ - timestamps│
│ - compareTo │
└──────────────┘
```

### Enum: JobPriority
```
LOW(0) < MEDIUM(1) < HIGH(2) < CRITICAL(3)
```

### Enum: JobStatus
```
PENDING → Waiting in queue
RUNNING → Currently executing
COMPLETED → Finished successfully
FAILED → Task threw exception
CANCELLED → Cancelled before execution
```

### Class: Job (implements Comparable)
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | "JOB-1" |
| name | String | "Backup DB" |
| task | Runnable | The actual work to execute |
| priority | JobPriority | LOW/MEDIUM/HIGH/CRITICAL |
| estimatedDurationMs | int | Hint for SJF scheduling |
| status | JobStatus | Current lifecycle state |
| createdAt | long | For FIFO tiebreaking |
| startedAt, completedAt | long | Timing metrics |

---

## 3️⃣ API Design

```java
class JobScheduler {
    /** Submit a job to the queue. Returns the Job object with ID. */
    Job submitJob(String name, Runnable task, JobPriority priority, int durationMs);
    
    /** Execute all queued jobs using worker pool (blocking until done). */
    void processAll();
    
    /** Execute one job (sequential, for testing). */
    void processNext();
    
    /** Cancel a pending job. Returns false if not PENDING. */
    boolean cancelJob(String jobId);
    
    /** Graceful shutdown: finish running, reject new. */
    void shutdown();
    
    /** Get system status (counts by status). */
    String getStatus();
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Submit 4 Jobs, Process in Priority Order

```
submitJob("Backup DB",        task, LOW,      50ms) → JOB-1
submitJob("Send Email",       task, MEDIUM,   30ms) → JOB-2
submitJob("Process Payment",  task, CRITICAL, 40ms) → JOB-3
submitJob("Generate Report",  task, HIGH,     60ms) → JOB-4

Queue (auto-sorted by PriorityBlockingQueue):
  [CRITICAL: Process Payment, HIGH: Generate Report, MEDIUM: Send Email, LOW: Backup DB]

processNext() → Process Payment (CRITICAL first!)
processNext() → Generate Report (HIGH)
processNext() → Send Email (MEDIUM)
processNext() → Backup DB (LOW)
```

### Scenario 2: Parallel Processing (3 Workers)

```
Submit 6 MEDIUM jobs (Task-1 through Task-6)
Worker pool size = 3

processAll():
  Worker-1: Task-1 starts ──── completes → picks Task-4 ──── completes
  Worker-2: Task-2 starts ──── completes → picks Task-5 ──── completes  
  Worker-3: Task-3 starts ──── completes → picks Task-6 ──── completes

  3 at a time → 6 jobs in ~2× time of 1 job (not 6×)
```

### Scenario 3: Job Failure (Error Isolation)

```
submitJob("Failing Job", () -> { throw new RuntimeException("Simulated error"); }, HIGH, 10ms)
submitJob("Good Job",    () -> doWork(), MEDIUM, 20ms)

processAll():
  Worker-1: "Failing Job" → catch(Exception) → status = FAILED, worker SURVIVES
  Worker-1: "Good Job" → executes normally → status = COMPLETED

  ✅ Failed job didn't crash the worker!
  ✅ Good job still ran after the failure!
```

### Scenario 4: Cancellation

```
submitJob("Important", task, HIGH, 50ms) → JOB-1
submitJob("Optional",  task, LOW,  50ms) → JOB-2

cancelJob("JOB-2"):
  ├─ JOB-2.status == PENDING? → YES
  ├─ JOB-2.status = CANCELLED
  ├─ jobQueue.remove(JOB-2)  ← O(N) but acceptable for cancel
  └─ Return: true

processAll():
  Only JOB-1 executes. JOB-2 was removed from queue.
```

---

## 5️⃣ Design + Implementation

### Job Ordering: The Key to Priority Scheduling

```java
class Job implements Comparable<Job> {
    @Override
    public int compareTo(Job other) {
        // 1. Higher priority FIRST (CRITICAL before LOW)
        int cmp = Integer.compare(other.priority.level, this.priority.level);
        if (cmp != 0) return cmp;
        
        // 2. Same priority → FIFO (earlier submitted first)
        return Long.compare(this.createdAt, other.createdAt);
    }
}
```

**Why this ordering matters**: PriorityBlockingQueue uses `compareTo` to determine dequeue order. CRITICAL jobs always come out before LOW. Same-priority jobs come out in submission order (FIFO). **No starvation within same priority level.**

### PriorityBlockingQueue: Why Not PriorityQueue?

| Feature | PriorityQueue | PriorityBlockingQueue |
|---------|--------------|----------------------|
| Thread-safe | ❌ No | ✅ Yes |
| Blocking take() | ❌ No | ✅ Waits for element |
| Concurrent offer() | ❌ No | ✅ Multiple producers |
| Use case | Single-thread | **Multi-thread producer/consumer** |

### Worker Pool: ExecutorService

```java
class JobScheduler {
    private final PriorityBlockingQueue<Job> jobQueue = new PriorityBlockingQueue<>();
    private final ExecutorService workerPool;

    JobScheduler(int workerCount, SchedulingStrategy strategy) {
        this.workerPool = Executors.newFixedThreadPool(workerCount);
    }

    void processAll() {
        List<Future<?>> futures = new ArrayList<>();
        while (!jobQueue.isEmpty()) {
            Job job = jobQueue.poll();
            if (job != null && job.getStatus() == JobStatus.PENDING) {
                futures.add(workerPool.submit(() -> executeJob(job)));
            }
        }
        // Wait for all to finish
        for (Future<?> f : futures) {
            try { f.get(30, TimeUnit.SECONDS); } catch (Exception e) { }
        }
    }
}
```

### Error Isolation: Critical Design Decision

```java
private void executeJob(Job job) {
    job.setStatus(JobStatus.RUNNING);
    job.setStartedAt(System.currentTimeMillis());
    
    try {
        job.getTask().run();          // execute the actual work
        job.setStatus(JobStatus.COMPLETED);
    } catch (Exception e) {
        job.setStatus(JobStatus.FAILED);
        // DON'T rethrow! Worker thread must survive to process next job.
        // Without this catch, one bad job would PERMANENTLY reduce pool size.
    }
    
    job.setCompletedAt(System.currentTimeMillis());
}
```

**Why `catch(Exception)`?** If a Runnable throws and we don't catch it:
1. The thread dies
2. ExecutorService creates a new one (overhead)
3. Or worse: if using `submit()` with `Callable`, exception is swallowed silently

By catching explicitly, we log the failure, mark the job, and the worker is ready for the next job immediately.

### Graceful Shutdown

```java
void shutdown() {
    workerPool.shutdown();  // stop accepting new tasks
    try {
        if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
            workerPool.shutdownNow();  // force-kill after 5s
        }
    } catch (InterruptedException e) {
        workerPool.shutdownNow();
    }
}
```

**`shutdown()` vs `shutdownNow()`**:
- `shutdown()`: Finish running tasks, reject new submissions
- `shutdownNow()`: Interrupt running tasks, return unfinished
- `awaitTermination()`: Wait up to N seconds, then force

### Strategy Pattern: Scheduling Policies

```java
interface SchedulingStrategy {
    void addJob(Job job, PriorityBlockingQueue<Job> queue);
    String getName();
}

class PriorityScheduling implements SchedulingStrategy {
    @Override
    public void addJob(Job job, PriorityBlockingQueue<Job> queue) {
        queue.put(job);  // natural ordering via Job.compareTo()
    }
}
```

**Why Strategy?** Different scheduling for different scenarios:
- **Priority**: CRITICAL jobs first (default for most systems)
- **FIFO**: Fair, no starvation (customer service queues)
- **SJF**: Shortest job first (minimize average wait time)

---

## 6️⃣ Deep Dives

### Deep Dive 1: Scheduling Strategy Comparison

| Strategy | Order | Best For | Starvation Risk |
|----------|-------|----------|-----------------|
| **Priority** | Highest priority first | Most systems | Low-priority jobs may wait |
| **FIFO** | Submission order | Fair queues | None |
| **SJF** | Shortest duration first | Minimize avg wait | Long jobs may starve |
| **Round Robin** | Time-sliced | Interactive | None |

### Deep Dive 2: Retry on Failure

```java
private void executeWithRetry(Job job, int maxRetries) {
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            job.getTask().run();
            job.setStatus(COMPLETED);
            return;
        } catch (Exception e) {
            if (attempt < maxRetries) {
                Thread.sleep(1000 * (attempt + 1));  // exponential backoff
            } else {
                job.setStatus(FAILED);
                moveToDeadLetterQueue(job);  // manual inspection
            }
        }
    }
}
```

### Deep Dive 3: Dead Letter Queue (DLQ)

Jobs that fail after N retries → DLQ for manual inspection:
```java
Queue<Job> deadLetterQueue = new ConcurrentLinkedQueue<>();
// Admin reviews failed jobs, fixes issues, resubmits
```

### Deep Dive 4: Cron Scheduling

For recurring jobs:
```java
ScheduledExecutorService cron = Executors.newScheduledThreadPool(2);
cron.scheduleAtFixedRate(
    () -> submitJob("Hourly Backup", backupTask, HIGH, 5000),
    0, 1, TimeUnit.HOURS  // initial delay = 0, period = 1 hour
);
```

### Deep Dive 5: Distributed Job Scheduling

| Approach | Implementation | Use Case |
|----------|---------------|----------|
| **Redis Queue** | RPUSH + BLPOP | Simple distributed queue |
| **Kafka Consumer** | Consumer group + partitions | High-throughput, ordered |
| **Celery** (Python) | Redis/RabbitMQ broker | Python ecosystem |
| **Azure Queue Storage** | Azure SDK | Microsoft ecosystem |
| **Kubernetes CronJob** | K8s manifest | Container-based scheduling |

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Empty queue | processNext() prints "no jobs" |
| Cancel running job | Rejected (only PENDING cancellable) |
| Worker exception | Caught, FAILED status, worker survives |
| Shutdown during execution | awaitTermination waits, then shutdownNow |
| Submit after shutdown | ExecutorService rejects → RejectedExecutionException |
| Same priority, same time | createdAt long comparison for FIFO |
| Job takes too long | P1: timeout via Future.get(timeout) |

### Deep Dive 7: Complexity

| Operation | Time | Note |
|-----------|------|------|
| submitJob (enqueue) | O(log N) | PBQ insertion |
| processNext (dequeue) | O(log N) | PBQ poll |
| cancelJob | O(N) | PBQ.remove() scans queue |
| processAll | O(N log N) | N dequeues |
| Parallel speedup | W× | W = worker threads |

---

## 📋 Interview Checklist (L63)

- [ ] **Strategy Pattern**: Swappable scheduling policies (Priority, FIFO, SJF)
- [ ] **PriorityBlockingQueue**: Thread-safe, auto-sorted — vs PriorityQueue
- [ ] **Comparable ordering**: Priority desc + createdAt FIFO tiebreak
- [ ] **ExecutorService**: Fixed thread pool, submit() returns Future
- [ ] **Error isolation**: catch(Exception) in worker — failed job doesn't crash pool
- [ ] **Job lifecycle**: PENDING → RUNNING → COMPLETED/FAILED/CANCELLED
- [ ] **Graceful shutdown**: shutdown() + awaitTermination() + shutdownNow()
- [ ] **Cancellation**: Only PENDING jobs, O(N) remove from queue
- [ ] **Production**: Retry + DLQ, cron, distributed (Redis/Kafka/K8s)

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (Job, Strategy, Scheduler) | 5 min |
| Implementation (PBQ, ExecutorService, error handling) | 15 min |
| Deep Dives (retry, DLQ, distributed, shutdown) | 10 min |
| **Total** | **~35 min** |

See `JobSchedulerSystem.java` for full implementation with sequential + parallel + failure + cancel demos.
