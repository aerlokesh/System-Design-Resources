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

A system that manages the execution of tasks across a pool of worker threads. Jobs have priorities, estimated durations, and lifecycle states. The scheduler picks which job runs next based on a **configurable scheduling policy**.

**Real-world**: cron jobs, Kubernetes CronJobs, CI/CD pipelines (Azure DevOps), AWS Step Functions, Airflow DAGs, Windows Task Scheduler.

### For L63 Microsoft Interview

1. **Strategy Pattern**: Multiple scheduling algorithms (Priority, FIFO, SJF) swappable at runtime
2. **PriorityBlockingQueue**: Thread-safe, auto-sorted queue — explain why over PriorityQueue
3. **Comparable**: Job ordering by priority (desc) then creation time (FIFO)
4. **ExecutorService**: Worker thread pool for parallel execution
5. **Job lifecycle**: PENDING → RUNNING → COMPLETED/FAILED/CANCELLED

---

## 1️⃣ Requirements

### Functional (P0)
1. Submit jobs with name, priority, estimated duration, runnable task
2. Schedule based on configurable strategy (Priority, FIFO, SJF)
3. Execute via worker thread pool (configurable size)
4. Job lifecycle: PENDING → RUNNING → COMPLETED/FAILED
5. Cancel pending jobs
6. Handle task exceptions (FAILED status, don't crash worker)

### Non-Functional
- Thread-safe job submission from multiple threads
- Worker pool size configurable
- Graceful shutdown (finish running jobs)

---

## 2️⃣ Core Entities

```
Job implements Comparable<Job>
  - id, name, task (Runnable), priority, status, timestamps
  - compareTo: higher priority first, then FIFO by createdAt

SchedulingStrategy (interface) — Strategy pattern
  ├── PriorityScheduling   — natural ordering (priority + FIFO)
  ├── FIFOScheduling       — first come first served
  └── SJFScheduling        — shortest estimated duration first

JobScheduler
  - PriorityBlockingQueue<Job> — thread-safe, auto-sorted
  - ExecutorService workerPool — N worker threads
  - submitJob() / processAll() / processNext() / cancelJob()
```

---

## 3️⃣ API Design

```java
class JobScheduler {
    Job submitJob(String name, Runnable task, JobPriority priority, int durationMs);
    void processAll();          // execute all queued jobs (blocking)
    void processNext();         // execute one job
    boolean cancelJob(String jobId);  // cancel if PENDING
    void shutdown();            // graceful shutdown
}
```

---

## 5️⃣ Design + Implementation

### Job Ordering (The Key Insight)

```java
class Job implements Comparable<Job> {
    @Override
    public int compareTo(Job other) {
        // Higher priority first (CRITICAL > HIGH > MEDIUM > LOW)
        int cmp = Integer.compare(other.priority.level, this.priority.level);
        if (cmp != 0) return cmp;
        
        // Same priority → FIFO (earlier submitted = higher priority)
        return Long.compare(this.createdAt, other.createdAt);
    }
}
```

**Why this ordering matters**: PriorityBlockingQueue uses `compareTo` to order elements. CRITICAL jobs always dequeue before LOW. Among same-priority jobs, the earliest submitted goes first. No starvation for same-priority.

### PriorityBlockingQueue (Why Not PriorityQueue?)

| Feature | PriorityQueue | PriorityBlockingQueue |
|---------|--------------|----------------------|
| Thread-safe | ❌ | ✅ |
| Blocking poll | ❌ | ✅ (waits for element) |
| Concurrent add | ❌ | ✅ |
| Use case | Single-thread | Multi-thread producer/consumer |

### Job Execution with Error Handling

```java
private void executeJob(Job job) {
    job.setStatus(JobStatus.RUNNING);
    job.setStartedAt(System.currentTimeMillis());
    
    try {
        job.getTask().run();
        job.setStatus(JobStatus.COMPLETED);
    } catch (Exception e) {
        job.setStatus(JobStatus.FAILED);
        // DON'T rethrow! Worker thread must survive to process next job.
    }
    
    job.setCompletedAt(System.currentTimeMillis());
}
```

**Critical**: `catch (Exception e)` prevents a failing task from killing the worker thread. Without this, one bad job would reduce the worker pool permanently.

### Worker Pool with ExecutorService

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
            if (job != null) {
                futures.add(workerPool.submit(() -> executeJob(job)));
            }
        }
        // Wait for all to complete
        for (Future<?> f : futures) {
            try { f.get(30, TimeUnit.SECONDS); } catch (Exception e) { }
        }
    }
    
    void shutdown() {
        workerPool.shutdown();
        try { workerPool.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { workerPool.shutdownNow(); }
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Scheduling Strategy Trade-offs

| Strategy | Order | Best For | Starvation Risk |
|----------|-------|----------|-----------------|
| **Priority** | Highest priority first | Most systems | Low-priority jobs may wait |
| **FIFO** | First submitted first | Fair, simple | None |
| **SJF** | Shortest duration first | Minimize avg wait time | Long jobs may starve |
| **Round Robin** | Time-sliced rotation | Interactive systems | None |

### Deep Dive 2: Job Cancellation

```java
boolean cancelJob(String jobId) {
    Job job = allJobs.get(jobId);
    if (job.getStatus() != JobStatus.PENDING) return false;  // can't cancel running
    job.setStatus(JobStatus.CANCELLED);
    jobQueue.remove(job);  // O(N) — acceptable for cancellation
    return true;
}
```

**Why only PENDING?** Once RUNNING, cancellation requires thread interruption (complex, unreliable). Better to let it finish or timeout.

### Deep Dive 3: Production Extensions

| Feature | Implementation |
|---------|---------------|
| **Retry on failure** | `if (FAILED && retries < maxRetries) requeue()` |
| **Dead letter queue** | Jobs failing N times → DLQ for manual inspection |
| **Cron scheduling** | `ScheduledExecutorService.scheduleAtFixedRate()` |
| **Distributed** | Redis-backed queue (Bull, Celery) or Kafka consumers |
| **DAG dependencies** | Job B depends on Job A → topological sort |
| **Rate limiting** | Combine with Token Bucket for throttled execution |

### Deep Dive 4: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Empty queue | processNext() prints "no jobs" |
| Cancel running job | Rejected (only PENDING cancellable) |
| Worker exception | Caught, job marked FAILED, worker survives |
| Shutdown during execution | awaitTermination waits, then shutdownNow |
| Submit after shutdown | IllegalStateException from ExecutorService |

### Deep Dive 5: Complexity

| Operation | Time |
|-----------|------|
| submitJob | O(log N) — PBQ insertion |
| processNext (dequeue) | O(log N) — PBQ poll |
| cancelJob | O(N) — PBQ.remove() |
| processAll | O(N log N) — N dequeues |

---

## 📋 Interview Checklist (L63)

- [ ] **Strategy Pattern**: Swappable scheduling policies
- [ ] **PriorityBlockingQueue**: Thread-safe, auto-sorted, vs PriorityQueue
- [ ] **Comparable ordering**: Priority desc + creation time FIFO
- [ ] **ExecutorService**: Fixed thread pool for workers
- [ ] **Error handling**: catch Exception in worker, don't crash pool
- [ ] **Job lifecycle**: PENDING → RUNNING → COMPLETED/FAILED/CANCELLED
- [ ] **Graceful shutdown**: awaitTermination + shutdownNow fallback
- [ ] **Production**: Retry, DLQ, cron, distributed (Redis/Kafka)

See `JobSchedulerSystem.java` for full implementation with sequential + parallel + failure demos.
