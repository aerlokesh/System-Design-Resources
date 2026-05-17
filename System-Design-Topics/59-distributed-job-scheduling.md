# 🎯 Topic 59: Distributed Job Scheduling

> **System Design Interview — Deep Dive**
> A comprehensive guide covering distributed job scheduling at scale — leader election for the scheduler (ZooKeeper/Redis), Redis sorted set job queues, visibility timeout (SQS pattern), heartbeat-based liveness detection, worker fleet management, at-least-once execution, idempotency requirements, cron expression evaluation, job partitioning, workflow DAGs, and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Distributed Job Scheduling Is Hard](#why-distributed-job-scheduling-is-hard)
3. [Architecture — The Three Components](#architecture--the-three-components)
4. [Leader Election for the Scheduler](#leader-election-for-the-scheduler)
5. [Redis Sorted Set as a Time-Based Job Queue](#redis-sorted-set-as-a-time-based-job-queue)
6. [Visibility Timeout — The SQS Pattern](#visibility-timeout--the-sqs-pattern)
7. [Worker Fleet and Heartbeat Protocol](#worker-fleet-and-heartbeat-protocol)
8. [Job State Machine](#job-state-machine)
9. [Idempotency — The Non-Negotiable Requirement](#idempotency--the-non-negotiable-requirement)
10. [Cron Expression Evaluation at Scale](#cron-expression-evaluation-at-scale)
11. [Job Partitioning and Sharding](#job-partitioning-and-sharding)
12. [Priority Queues and Fair Scheduling](#priority-queues-and-fair-scheduling)
13. [Workflow DAGs — Multi-Step Jobs](#workflow-dags--multi-step-jobs)
14. [Retry Strategies and Backoff](#retry-strategies-and-backoff)
15. [Dead Letter Queue and Poison Pills](#dead-letter-queue-and-poison-pills)
16. [Exactly-Once vs At-Least-Once Execution](#exactly-once-vs-at-least-once-execution)
17. [Scaling to Millions of Jobs](#scaling-to-millions-of-jobs)
18. [Observability and Monitoring](#observability-and-monitoring)
19. [Real-World Production Patterns](#real-world-production-patterns)
20. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
21. [Common Interview Mistakes](#common-interview-mistakes)
22. [Distributed Job Scheduling by System Design Problem](#distributed-job-scheduling-by-system-design-problem)
23. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **distributed job scheduler** manages the execution of millions of scheduled tasks across a fleet of workers with no single point of failure. Unlike a single-server cron, it must handle leader election, job deduplication, worker liveness detection, and graceful reassignment of jobs when workers die.

```
Single-server cron:
  One machine runs `crontab`. If it dies, all jobs stop.
  Jobs: Hundreds. Scale: One machine.

Distributed job scheduler:
  Leader schedules jobs → Redis queue → Worker fleet executes.
  If the leader dies → new leader elected in seconds.
  If a worker dies → its jobs are reassigned to other workers.
  Jobs: Millions. Scale: Hundreds of workers.

  ┌─────────────┐     ┌─────────────────┐     ┌──────────────┐
  │  Scheduler   │────→│  Job Queue      │────→│  Worker Fleet │
  │  (Leader)    │     │  (Redis ZSET)   │     │  (Stateless)  │
  │              │     │                 │     │               │
  │  Evaluates   │     │  Sorted by      │     │  Pull jobs    │
  │  cron exprs  │     │  execution time │     │  Execute      │
  │  Enqueues    │     │                 │     │  Heartbeat    │
  └─────────────┘     └─────────────────┘     └──────────────┘
        ↑                                           │
        │ Leader election                           │ Heartbeat
        │ (ZooKeeper/Redis)                         │ (every 10s)
  ┌─────┴─────────┐                          ┌──────▼──────────┐
  │ Standby        │                          │ Reassignment     │
  │ Schedulers     │                          │ Service          │
  │ (passive)      │                          │ (monitors health)│
  └───────────────┘                          └─────────────────┘
```

---

## Why Distributed Job Scheduling Is Hard

### Challenge 1: No Duplicate Execution

```
Job: "Send daily digest email to user:42 at 8:00 AM"

  Without protection:
    Worker A picks up the job at 8:00:00.000
    Worker B picks up the same job at 8:00:00.005  ← race condition!
    User receives two identical emails. Bad!

  Solution: Atomic claim with visibility timeout.
    Worker A: ZPOPMIN or SETNX → claims the job atomically.
    Worker B: Job already claimed → skips.
    Only ONE worker processes each job.
```

### Challenge 2: No Missed Execution

```
Job: "Process payroll at midnight"

  Scheduler dies at 11:59 PM. No backup.
  Payroll doesn't run. Everyone panics.

  Solution: Leader election with fast failover.
    Active scheduler dies → standby detects within 10 seconds
    → new leader elected → resumes scheduling → job runs at 12:00:10 AM.
    10-second delay, but no missed jobs.
```

### Challenge 3: Worker Failure Mid-Execution

```
Worker starts job: "Generate monthly report" (takes 30 minutes).
Worker crashes at minute 15. Job is half-done.

  Without detection: Job is never completed. No one knows.
  
  Solution: Heartbeat + visibility timeout.
    Worker sends heartbeat every 10 seconds.
    If no heartbeat for 30 seconds → worker assumed dead.
    Job reassigned to another worker → re-executes from scratch.
    Job MUST be idempotent (re-execution produces same result).
```

### Challenge 4: Clock Skew

```
Job scheduled for 12:00:00.000 UTC.
  Scheduler clock: 12:00:00.000 → enqueues job.
  Worker A clock: 11:59:59.800 → "it's not time yet" (wrong!).
  Worker B clock: 12:00:00.200 → "it's past time" (correct).

  Solution: Use the queue's timestamps, not the worker's clock.
    Redis ZSET score = job execution time (Unix timestamp).
    Workers: ZPOPMIN where score <= now() (Redis server time).
    All workers use the SAME clock (Redis server's).
```

### Challenge 5: Scale

```
Requirements:
  10M scheduled jobs (cron definitions).
  1M jobs execute per hour (peak).
  500 workers processing concurrently.
  
  A single Redis instance handles ~300K ops/sec.
  1M jobs/hour = 278 jobs/sec → easily within Redis capacity.
  Even at 10M jobs/hour: 2,778 jobs/sec → one Redis instance is fine.
  
  The bottleneck is job EXECUTION, not scheduling.
  Worker fleet is the scaling dimension.
```

---

## Architecture — The Three Components

### Component 1: Scheduler (Leader)

```
Responsibility:
  - Evaluate cron expressions → determine next execution time
  - Enqueue jobs into the Redis sorted set at the right time
  - Handle recurring job re-scheduling after completion
  - Manage job definitions (CRUD)

Properties:
  - Runs as a SINGLE leader (prevents duplicate scheduling)
  - Stateless (all state in Redis/DB) → any instance can be leader
  - If leader dies, standby takes over via leader election
  
  NOT responsible for executing jobs (that's the worker's job).
```

### Component 2: Job Queue (Redis ZSET)

```
Responsibility:
  - Time-ordered queue of jobs ready to execute
  - Atomic dequeue (ZPOPMIN) → prevents duplicate pickup
  - Score = scheduled execution time (Unix timestamp)
  - Supports peek (what's coming next) and size queries

Properties:
  - Redis ZSET: O(log N) insert, O(log N) pop, O(1) count
  - Can hold millions of pending jobs
  - TTL on individual job metadata for cleanup
  
Data model:
  Key: jobs:pending
  Type: Sorted Set
  Score: Execution timestamp (epoch seconds)
  Member: job_id (unique identifier)
  
  Separate hash for job details:
  Key: job:{job_id}
  Type: Hash
  Fields: type, payload, retry_count, created_at, timeout
```

### Component 3: Worker Fleet

```
Responsibility:
  - Pull jobs from the queue
  - Execute job logic
  - Report completion/failure
  - Send heartbeats during execution

Properties:
  - Stateless → horizontally scalable
  - Pull-based → workers grab work when ready (self-balancing)
  - Each worker handles one or more jobs concurrently
  - Heartbeat: "I'm alive and working on job X"
```

### Full Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Control Plane                                 │
│                                                                     │
│  ┌────────────────┐    ┌──────────────────┐                         │
│  │  ZooKeeper /    │    │  Job Definition  │                         │
│  │  Redis Lock     │    │  Database        │                         │
│  │  (leader elect) │    │  (PostgreSQL)    │                         │
│  └────────┬───────┘    └────────┬─────────┘                         │
│           │                     │                                    │
│  ┌────────▼─────────────────────▼───────────────────┐               │
│  │              Scheduler (Leader)                    │               │
│  │                                                   │               │
│  │  Every 1 second:                                  │               │
│  │    1. Query DB for jobs due in next 60 seconds    │               │
│  │    2. ZADD jobs:pending {exec_time} {job_id}      │               │
│  │    3. For recurring jobs: calculate next run,      │               │
│  │       update DB with next_execution_time           │               │
│  └──────────────────┬────────────────────────────────┘               │
│                     │                                                │
└─────────────────────┼────────────────────────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────────────────────────┐
│                       Data Plane                                      │
│                                                                      │
│  ┌───────────────────────────────────────────┐                       │
│  │            Redis Cluster                    │                       │
│  │                                            │                       │
│  │  ZSET: jobs:pending                        │                       │
│  │    {1710500000, "job:abc"}                 │                       │
│  │    {1710500001, "job:def"}                 │                       │
│  │    {1710500005, "job:ghi"}                 │                       │
│  │                                            │                       │
│  │  HASH: job:abc  → {type, payload, ...}     │                       │
│  │  SET: jobs:processing → {job:abc, ...}     │                       │
│  │  HASH: worker:w1 → {heartbeat, job_id}     │                       │
│  └───────────────────┬───────────────────────┘                       │
│                      │                                                │
│  ┌───────────────────▼───────────────────────┐                       │
│  │           Worker Fleet (N workers)          │                       │
│  │                                            │                       │
│  │  Worker 1: Pull → Execute → Complete       │                       │
│  │  Worker 2: Pull → Execute → Heartbeat...   │                       │
│  │  Worker 3: Pull → Execute → Complete       │                       │
│  │  ...                                       │                       │
│  │  Worker N: Pull → Execute → Fail → Retry   │                       │
│  └────────────────────────────────────────────┘                       │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Leader Election for the Scheduler

### Why Leader Election?

```
Multiple scheduler instances running simultaneously → duplicate job enqueuing.

  Scheduler A: "Job X due at 12:00" → ZADD jobs:pending 1710500000 job:X
  Scheduler B: "Job X due at 12:00" → ZADD jobs:pending 1710500000 job:X
  
  ZADD is idempotent (same score + member = no duplicate), so this specific
  case is safe. BUT:
  
  Scheduler A: Calculates next run for recurring job → updates DB
  Scheduler B: Also calculates next run → ALSO updates DB → race condition!
  
  Only ONE scheduler should be active at a time.
```

### Option 1: ZooKeeper Leader Election

```
ZooKeeper: Distributed coordination service (used by Kafka, HBase).

  How it works:
    1. Each scheduler instance creates an ephemeral sequential node:
       /scheduler/leader-000000001 (Scheduler A)
       /scheduler/leader-000000002 (Scheduler B)
       /scheduler/leader-000000003 (Scheduler C)
    
    2. The instance with the LOWEST sequence number is the leader.
       Scheduler A (000000001) → LEADER
       Scheduler B (000000002) → STANDBY (watches A's node)
       Scheduler C (000000003) → STANDBY (watches B's node)
    
    3. If Scheduler A crashes:
       Ephemeral node /scheduler/leader-000000001 is deleted (session expired).
       Scheduler B's watch fires → B becomes leader.
       Failover time: ZooKeeper session timeout (10-30 seconds).

  Pros:
    ✅ Proven, battle-tested (LinkedIn, Kafka, HBase use this)
    ✅ Strong consistency guarantees
    ✅ Handles network partitions correctly
    
  Cons:
    ❌ Requires running ZooKeeper cluster (3-5 nodes)
    ❌ Additional operational complexity
```

### Option 2: Redis-Based Leader Election (Simpler)

```
Using Redis SETNX + TTL for leader lock:

  def try_become_leader(redis, instance_id, ttl=30):
      # Try to acquire leader lock
      acquired = redis.set(
          "scheduler:leader",
          instance_id,
          nx=True,    # Only set if key doesn't exist
          ex=ttl      # Expire after 30 seconds
      )
      return acquired  # True if we became leader
  
  def maintain_leadership(redis, instance_id, ttl=30):
      # Renew lock every ttl/3 seconds (10 seconds for 30s TTL)
      current_leader = redis.get("scheduler:leader")
      if current_leader == instance_id:
          redis.expire("scheduler:leader", ttl)  # Extend lease
          return True
      return False  # Lost leadership

  # Main loop
  while True:
      if am_leader:
          am_leader = maintain_leadership(redis, my_id)
          if am_leader:
              run_scheduler_cycle()  # Enqueue due jobs
      else:
          am_leader = try_become_leader(redis, my_id)
      time.sleep(10)  # Check every 10 seconds

Failover:
  Leader dies → Redis key expires after 30 seconds
  → Standby acquires lock → becomes leader in <40 seconds total.

Pros:
  ✅ Simple — uses existing Redis infrastructure
  ✅ No ZooKeeper dependency
  ✅ Fast to implement
  
Cons:
  ❌ Redis SETNX has known edge cases with Redis Cluster failover
  ❌ Less robust than ZooKeeper for strict leader election
  ❌ Clock-dependent (TTL-based)
  
Good enough for: Most job schedulers. Not for financial transactions.
```

### Option 3: Database-Based Leader Election

```
Use PostgreSQL advisory locks or a leader table:

  CREATE TABLE scheduler_leader (
      id INT PRIMARY KEY DEFAULT 1,  -- Singleton row
      instance_id VARCHAR(255),
      acquired_at TIMESTAMP,
      expires_at TIMESTAMP
  );

  -- Try to become leader
  UPDATE scheduler_leader 
  SET instance_id = 'scheduler-A', acquired_at = NOW(), expires_at = NOW() + INTERVAL '30 seconds'
  WHERE id = 1 AND (expires_at < NOW() OR instance_id = 'scheduler-A');
  
  -- If 1 row updated → we're the leader
  -- If 0 rows updated → someone else is leader

Pros: No additional infrastructure (uses existing DB).
Cons: DB polling adds load. Failover is slow (TTL-based).
```

---

## Redis Sorted Set as a Time-Based Job Queue

### The Core Data Structure

```
Key: jobs:pending
Type: Sorted Set
Score: Unix timestamp of when the job should execute
Member: Unique job ID

  ZADD jobs:pending 1710500000 "job:send-email:user42"
  ZADD jobs:pending 1710500060 "job:generate-report:daily"
  ZADD jobs:pending 1710500120 "job:cleanup-expired-sessions"
  ZADD jobs:pending 1710503600 "job:send-email:user99"

  Sorted by score (execution time):
    1710500000 → job:send-email:user42       (12:00:00)
    1710500060 → job:generate-report:daily    (12:01:00)
    1710500120 → job:cleanup-expired-sessions (12:02:00)
    1710503600 → job:send-email:user99        (13:00:00)

Why ZSET?
  ✅ Naturally sorted by execution time
  ✅ ZRANGEBYSCORE: "Give me all jobs due before NOW" → O(log N + M)
  ✅ ZPOPMIN: Atomic dequeue of the oldest job → O(log N)
  ✅ ZCARD: Count pending jobs → O(1)
  ✅ ZSCORE: Check when a specific job is scheduled → O(1)
  ✅ ZREM: Cancel a specific job → O(log N)
```

### Scheduler Enqueue Logic

```python
class Scheduler:
    """Leader scheduler — evaluates cron expressions and enqueues jobs."""
    
    def __init__(self, redis, db):
        self.redis = redis
        self.db = db
    
    def run_cycle(self):
        """Called every 1 second by the leader."""
        now = time.time()
        lookahead = now + 60  # Enqueue jobs due in the next 60 seconds
        
        # Query DB for jobs due soon
        due_jobs = self.db.query("""
            SELECT job_id, cron_expression, job_type, payload, next_execution_time
            FROM scheduled_jobs
            WHERE next_execution_time <= %s
              AND status = 'ACTIVE'
            ORDER BY next_execution_time
            LIMIT 10000
        """, (datetime.fromtimestamp(lookahead),))
        
        pipe = self.redis.pipeline()
        for job in due_jobs:
            exec_time = job['next_execution_time'].timestamp()
            
            # Enqueue to Redis ZSET (idempotent — same job_id won't duplicate)
            pipe.zadd('jobs:pending', {job['job_id']: exec_time})
            
            # Store job details in a hash
            pipe.hmset(f"job:{job['job_id']}", {
                'type': job['job_type'],
                'payload': json.dumps(job['payload']),
                'scheduled_at': exec_time,
                'timeout': 300,  # 5-minute execution timeout
            })
            pipe.expire(f"job:{job['job_id']}", 3600)  # 1-hour TTL for cleanup
        
        pipe.execute()
        
        # Update next execution time for recurring jobs
        for job in due_jobs:
            if job['cron_expression']:
                next_time = self._next_cron_time(job['cron_expression'])
                self.db.execute("""
                    UPDATE scheduled_jobs 
                    SET next_execution_time = %s
                    WHERE job_id = %s
                """, (next_time, job['job_id']))
```

### Worker Dequeue Logic (Atomic)

```lua
-- Atomic dequeue: Pop jobs that are due NOW
-- KEYS[1] = "jobs:pending"
-- KEYS[2] = "jobs:processing"
-- ARGV[1] = current timestamp
-- ARGV[2] = visibility timeout (seconds)
-- ARGV[3] = worker_id
-- ARGV[4] = max jobs to dequeue

local pending_key = KEYS[1]
local processing_key = KEYS[2]
local now = tonumber(ARGV[1])
local visibility_timeout = tonumber(ARGV[2])
local worker_id = ARGV[3]
local max_jobs = tonumber(ARGV[4]) or 1

-- Get jobs that are due (score <= now)
local due_jobs = redis.call('ZRANGEBYSCORE', pending_key, '-inf', now, 'LIMIT', 0, max_jobs)

if #due_jobs == 0 then
    return {}  -- No jobs due
end

local claimed = {}
for i, job_id in ipairs(due_jobs) do
    -- Remove from pending
    local removed = redis.call('ZREM', pending_key, job_id)
    if removed == 1 then
        -- Add to processing set with visibility timeout
        local timeout_at = now + visibility_timeout
        redis.call('ZADD', processing_key, timeout_at, job_id)
        
        -- Record which worker claimed this job
        redis.call('HSET', 'job:' .. job_id, 'worker', worker_id, 'claimed_at', now)
        
        table.insert(claimed, job_id)
    end
end

return claimed
```

---

## Visibility Timeout — The SQS Pattern

### How It Works

```
The visibility timeout prevents job loss when workers fail:

  1. Worker claims job → job moves from "pending" to "processing"
     Processing set score = claim_time + visibility_timeout (e.g., +300 seconds)
  
  2. Worker executes the job (takes 0-300 seconds)
  
  3a. SUCCESS: Worker deletes job from "processing" set.
      Job is done. Everyone is happy.
  
  3b. WORKER DIES: No one deletes the job from "processing."
      After 300 seconds, the visibility timeout expires.
      A reaper process detects: score < now() → job timed out!
      Reaper moves job back to "pending" → another worker picks it up.

  Timeline:
    T=0:    Worker A claims job. Processing score = T+300.
    T=60:   Worker A crashes.
    T=300:  Reaper runs. Finds job in processing with score < now().
    T=300:  Reaper: ZREM processing + ZADD pending → job re-queued.
    T=301:  Worker B claims the re-queued job. Executes it.
    
  Net effect: Job executes AT LEAST ONCE.
  Worst case delay: visibility_timeout duration (5 minutes in this example).
```

### Reaper Implementation

```python
class VisibilityTimeoutReaper:
    """Reclaims jobs from dead workers."""
    
    def __init__(self, redis):
        self.redis = redis
    
    def run(self):
        """Called every 30 seconds."""
        now = time.time()
        
        # Find jobs in processing that have exceeded their visibility timeout
        timed_out = self.redis.zrangebyscore(
            'jobs:processing',
            '-inf',
            now,  # Score (timeout_at) has passed
            start=0,
            num=100
        )
        
        if not timed_out:
            return
        
        pipe = self.redis.pipeline()
        for job_id in timed_out:
            job_data = self.redis.hgetall(f"job:{job_id}")
            retry_count = int(job_data.get(b'retry_count', 0))
            max_retries = int(job_data.get(b'max_retries', 3))
            
            if retry_count < max_retries:
                # Re-queue with incremented retry count
                pipe.zrem('jobs:processing', job_id)
                pipe.zadd('jobs:pending', {job_id: now})  # Re-queue immediately
                pipe.hincrby(f"job:{job_id}", 'retry_count', 1)
                pipe.hset(f"job:{job_id}", 'last_retry_reason', 'visibility_timeout')
            else:
                # Max retries exceeded → move to DLQ
                pipe.zrem('jobs:processing', job_id)
                pipe.zadd('jobs:dlq', {job_id: now})
                pipe.hset(f"job:{job_id}", 'status', 'DEAD_LETTERED')
        
        pipe.execute()
```

### Heartbeat Extension

```
For long-running jobs, the worker extends the visibility timeout:

  Job estimated time: 30 minutes.
  Default visibility timeout: 5 minutes.
  
  Worker: Every 60 seconds, extend the timeout by another 5 minutes.
    ZADD jobs:processing {now + 300} {job_id}  → updates the score
  
  If the worker dies, the heartbeat stops.
  Within 5 minutes, the reaper reclaims the job.

  def extend_visibility(redis, job_id, extension_seconds=300):
      new_timeout = time.time() + extension_seconds
      redis.zadd('jobs:processing', {job_id: new_timeout})
      return True
```

---

## Worker Fleet and Heartbeat Protocol

### Worker Lifecycle

```
1. REGISTER: Worker starts → registers in Redis
   HSET worker:{worker_id} status "ACTIVE" last_heartbeat {now}
   SADD workers:active {worker_id}

2. POLL: Worker polls for jobs
   Execute Lua dequeue script → get up to N jobs

3. EXECUTE: Worker runs job logic
   During execution: extend visibility timeout every 60 seconds

4. COMPLETE: Worker reports job completion
   ZREM jobs:processing {job_id}
   HSET job:{job_id} status "COMPLETED" completed_at {now}

5. HEARTBEAT: Every 10 seconds
   HSET worker:{worker_id} last_heartbeat {now}
   If the worker misses 3 consecutive heartbeats → assumed dead.

6. DEREGISTER: Worker shuts down gracefully
   Release any claimed jobs (re-queue them)
   SREM workers:active {worker_id}
   DEL worker:{worker_id}
```

### Worker Implementation

```python
class Worker:
    """Distributed job execution worker."""
    
    def __init__(self, redis, worker_id, concurrency=5):
        self.redis = redis
        self.worker_id = worker_id
        self.concurrency = concurrency
        self.running_jobs = {}
        self.dequeue_script = redis.register_script(DEQUEUE_LUA)
    
    def start(self):
        self._register()
        self._start_heartbeat_thread()
        self._start_poll_loop()
    
    def _register(self):
        self.redis.hset(f"worker:{self.worker_id}", mapping={
            'status': 'ACTIVE',
            'last_heartbeat': time.time(),
            'concurrency': self.concurrency,
            'hostname': socket.gethostname(),
        })
        self.redis.sadd('workers:active', self.worker_id)
    
    def _poll_loop(self):
        while self.running:
            available_slots = self.concurrency - len(self.running_jobs)
            if available_slots <= 0:
                time.sleep(1)
                continue
            
            # Atomically claim up to N jobs
            claimed_jobs = self.dequeue_script(
                keys=['jobs:pending', 'jobs:processing'],
                args=[time.time(), 300, self.worker_id, available_slots]
            )
            
            for job_id in claimed_jobs:
                job_data = self.redis.hgetall(f"job:{job_id}")
                thread = threading.Thread(target=self._execute_job, args=(job_id, job_data))
                thread.start()
                self.running_jobs[job_id] = thread
            
            if not claimed_jobs:
                time.sleep(1)  # No jobs available → back off
    
    def _execute_job(self, job_id, job_data):
        try:
            # Run the job
            handler = get_handler(job_data[b'type'].decode())
            payload = json.loads(job_data[b'payload'])
            handler.execute(payload)
            
            # Mark complete
            self.redis.zrem('jobs:processing', job_id)
            self.redis.hset(f"job:{job_id}", 'status', 'COMPLETED')
            
        except Exception as e:
            # Mark failed (reaper will handle retry/DLQ)
            self.redis.hset(f"job:{job_id}", mapping={
                'status': 'FAILED',
                'error': str(e),
                'failed_at': time.time(),
            })
            # Don't remove from processing — let reaper handle based on retry policy
        
        finally:
            del self.running_jobs[job_id]
    
    def _heartbeat_loop(self):
        while self.running:
            self.redis.hset(f"worker:{self.worker_id}", 'last_heartbeat', time.time())
            # Extend visibility for all running jobs
            for job_id in list(self.running_jobs.keys()):
                self.redis.zadd('jobs:processing', {job_id: time.time() + 300})
            time.sleep(10)
```

### Dead Worker Detection

```python
class WorkerHealthChecker:
    """Detects dead workers and reclaims their jobs."""
    
    HEARTBEAT_TIMEOUT = 30  # seconds
    
    def check(self):
        now = time.time()
        active_workers = self.redis.smembers('workers:active')
        
        for worker_id in active_workers:
            last_heartbeat = float(
                self.redis.hget(f"worker:{worker_id}", 'last_heartbeat') or 0
            )
            
            if now - last_heartbeat > self.HEARTBEAT_TIMEOUT:
                # Worker is dead — reclaim its jobs
                self._reclaim_worker_jobs(worker_id)
                self.redis.srem('workers:active', worker_id)
                self.redis.delete(f"worker:{worker_id}")
    
    def _reclaim_worker_jobs(self, dead_worker_id):
        """Find all jobs assigned to the dead worker and re-queue them."""
        # Scan processing jobs for ones owned by this worker
        processing_jobs = self.redis.zrange('jobs:processing', 0, -1)
        
        pipe = self.redis.pipeline()
        for job_id in processing_jobs:
            worker = self.redis.hget(f"job:{job_id}", 'worker')
            if worker and worker.decode() == dead_worker_id:
                # Re-queue the job
                pipe.zrem('jobs:processing', job_id)
                pipe.zadd('jobs:pending', {job_id: time.time()})
                pipe.hset(f"job:{job_id}", 'last_retry_reason', 'worker_death')
                pipe.hincrby(f"job:{job_id}", 'retry_count', 1)
        
        pipe.execute()
```

---

## Job State Machine

```
┌──────────┐    Scheduler     ┌──────────┐    Worker claims   ┌────────────┐
│ SCHEDULED │───────────────→ │ PENDING   │──────────────────→│ PROCESSING  │
│ (in DB)   │  enqueues       │ (in ZSET) │  dequeues          │ (executing) │
└──────────┘                  └──────────┘                    └─────┬──────┘
                                    ↑                               │
                                    │ Reaper re-queues      ┌──────▼──────┐
                                    │ (visibility timeout)  │             │
                                    │                       │  SUCCESS?   │
                                    │            ┌──────────┤             │
                                    │     NO     │          └──────┬──────┘
                                    │   (retry)  │           YES   │
                              ┌─────┴─────┐      │          ┌──────▼──────┐
                              │  PENDING   │←─────┘          │ COMPLETED   │
                              │ (retry)    │                 │             │
                              └─────┬─────┘                 └─────────────┘
                                    │
                              Max retries exceeded
                                    │
                              ┌─────▼──────────┐
                              │ DEAD_LETTERED   │
                              │ (DLQ)           │
                              └────────────────┘
```

### State Transitions with Redis Commands

```
SCHEDULED → PENDING:
  Scheduler: ZADD jobs:pending {exec_time} {job_id}

PENDING → PROCESSING:
  Worker: Lua script (ZREM pending + ZADD processing)

PROCESSING → COMPLETED:
  Worker: ZREM jobs:processing {job_id}
          HSET job:{job_id} status COMPLETED

PROCESSING → PENDING (retry):
  Reaper: ZREM jobs:processing {job_id}
          ZADD jobs:pending {now} {job_id}
          HINCRBY job:{job_id} retry_count 1

PROCESSING → DEAD_LETTERED:
  Reaper: ZREM jobs:processing {job_id}
          ZADD jobs:dlq {now} {job_id}
```

---

## Idempotency — The Non-Negotiable Requirement

### Why Idempotency Is Mandatory

```
At-least-once execution means a job MAY run more than once:
  - Worker crashes after executing but before acknowledging
  - Network partition: worker completes, ACK lost, job re-assigned
  - Reaper reclaims a job that was actually still running (clock skew)

If the job is NOT idempotent:
  Job: "Transfer $100 from Account A to Account B"
  Runs twice → $200 transferred → catastrophic!

If the job IS idempotent:
  Job: "Set Account A balance to (current - $100), set Account B balance to (current + $100)"
         with idempotency key: "transfer:txn:abc123"
  Runs twice → second execution is a no-op → correct!
```

### Idempotency Patterns

```
Pattern 1: Idempotency key in database
  Before executing: Check if idempotency key exists.
    SELECT 1 FROM job_executions WHERE idempotency_key = 'txn:abc123'
  If exists → skip (already executed).
  If not → execute → INSERT idempotency_key.
  
  The INSERT + business logic should be in the SAME transaction.

Pattern 2: Version-based updates
  UPDATE accounts SET balance = balance - 100 
  WHERE account_id = 'A' AND version = 5;
  
  If version changed (concurrent execution) → 0 rows updated → retry or skip.

Pattern 3: Natural idempotency
  Some operations are naturally idempotent:
  - SET operations: "Set user.email = 'new@example.com'" (safe to repeat)
  - DELETE operations: "Delete record X" (second delete = no-op)
  - PUT operations: "Upload file to S3 key" (overwrite = same result)

Pattern 4: Deduplication table
  CREATE TABLE job_dedup (
      job_id VARCHAR(255) PRIMARY KEY,
      executed_at TIMESTAMP,
      result JSONB
  );
  
  INSERT INTO job_dedup (job_id, executed_at, result)
  VALUES ('job:abc', NOW(), '{"status": "sent"}')
  ON CONFLICT (job_id) DO NOTHING;
  
  If INSERT succeeds → first execution → do work.
  If INSERT fails (conflict) → duplicate → skip.
```

---

## Cron Expression Evaluation at Scale

### The Scheduling Problem

```
10M cron jobs. Each has a cron expression:
  "0 8 * * *"     → Every day at 8:00 AM
  "*/5 * * * *"   → Every 5 minutes
  "0 0 1 * *"     → First day of every month at midnight

The scheduler must:
  1. For each job, compute the next execution time from the cron expression.
  2. Enqueue jobs that are due within the next scheduling window.
  3. Do this QUICKLY (can't spend 10 minutes scanning 10M jobs).
```

### Efficient Scanning

```
Approach 1: Database index on next_execution_time
  CREATE INDEX idx_next_exec ON scheduled_jobs(next_execution_time) 
  WHERE status = 'ACTIVE';
  
  Query: SELECT * FROM scheduled_jobs 
         WHERE next_execution_time <= NOW() + INTERVAL '60 seconds'
         AND status = 'ACTIVE'
         ORDER BY next_execution_time
         LIMIT 10000;
  
  With index: O(log N + K) where K = number of due jobs.
  10M total jobs, but only 10K due in the next minute → fast scan.

Approach 2: Partition by next_execution_time
  Partition the table by hour or day.
  Scheduler only scans the current hour's partition.
  Much smaller scan set.

Approach 3: Pre-computed schedule in Redis
  On job creation/update: Compute next 24 hours of execution times.
  ZADD jobs:schedule {exec_time} {job_id} for each upcoming execution.
  
  Scheduler: Just ZRANGEBYSCORE jobs:schedule -inf {now+60}
  No database query at all → purely Redis → sub-millisecond.
  
  Tradeoff: Must re-compute when cron expression changes.
```

### Next Execution Time Calculation

```python
from croniter import croniter

def next_execution_time(cron_expr: str, from_time: datetime = None) -> datetime:
    """Calculate the next execution time for a cron expression."""
    base = from_time or datetime.utcnow()
    cron = croniter(cron_expr, base)
    return cron.get_next(datetime)

# Examples:
next_execution_time("0 8 * * *")      # → 2024-03-16 08:00:00 (tomorrow 8 AM)
next_execution_time("*/5 * * * *")    # → 2024-03-15 12:05:00 (next 5-min mark)
next_execution_time("0 0 1 * *")      # → 2024-04-01 00:00:00 (first of next month)
```

---

## Job Partitioning and Sharding

### Why Partition?

```
With 10M jobs and 500 workers, a single Redis instance handles the queue.
But with 100M+ jobs or high throughput needs, partition the workload.

Partition strategies:
  1. By job type: email-jobs → Queue A, report-jobs → Queue B
  2. By tenant: tenant:acme → Queue A, tenant:beta → Queue B
  3. By hash: hash(job_id) % N → Queue N
```

### Multi-Queue Architecture

```
┌────────────────────────────────────┐
│         Scheduler (Leader)          │
│                                    │
│  Evaluates cron → routes to queue  │
└──────────────┬─────────────────────┘
               │
      ┌────────┴────────┐
      │                 │
┌─────▼─────┐    ┌──────▼─────┐
│ Queue A    │    │ Queue B     │
│ (email)    │    │ (reports)   │
│ Redis ZSET │    │ Redis ZSET  │
└─────┬─────┘    └──────┬─────┘
      │                 │
┌─────▼─────┐    ┌──────▼─────┐
│ Workers    │    │ Workers     │
│ (email     │    │ (report     │
│  fleet)    │    │  fleet)     │
└───────────┘    └────────────┘

Benefits:
  - Each queue has its own worker fleet → isolation
  - Email workers don't starve report workers
  - Scale each queue independently
  - Different visibility timeouts per queue type
```

---

## Priority Queues and Fair Scheduling

### Priority Levels

```
Priority queues: Some jobs are more important than others.

  High priority:   Payment processing, critical alerts
  Medium priority: Email notifications, report generation
  Low priority:    Analytics, cleanup tasks

Implementation: Multiple ZSET queues with priority ordering.

  jobs:pending:high    → Workers check this FIRST
  jobs:pending:medium  → Workers check this SECOND
  jobs:pending:low     → Workers check this LAST

  Worker poll order:
    1. ZPOPMIN jobs:pending:high → if empty...
    2. ZPOPMIN jobs:pending:medium → if empty...
    3. ZPOPMIN jobs:pending:low
    
  Or: Weighted round-robin
    High: 60% of polls
    Medium: 30% of polls
    Low: 10% of polls
```

### Fair Scheduling Across Tenants

```
Multi-tenant system: Tenant A submits 10K jobs, Tenant B submits 10 jobs.
Without fairness: Tenant A monopolizes all workers. Tenant B waits hours.

Solution: Per-tenant queues with round-robin scheduling.

  jobs:pending:tenant:acme    → 10K jobs
  jobs:pending:tenant:beta    → 10 jobs
  jobs:pending:tenant:gamma   → 500 jobs

  Worker: Round-robin across tenant queues.
    Poll acme → grab 1 job
    Poll beta → grab 1 job
    Poll gamma → grab 1 job
    Poll acme → grab 1 job
    ...
  
  Each tenant gets roughly equal share of worker time.
  No single tenant can monopolize the system.
```

---

## Workflow DAGs — Multi-Step Jobs

### Job Dependencies

```
Some jobs have dependencies: "Run B only after A completes."

  Example: Data pipeline
    Step 1: Extract data from source (extract)
    Step 2: Transform data (transform) — depends on step 1
    Step 3: Load into warehouse (load) — depends on step 2
    Step 4: Send notification — depends on step 3

  DAG (Directed Acyclic Graph):
    extract → transform → load → notify
```

### Implementation: Job Chaining

```python
class WorkflowEngine:
    """Simple DAG-based workflow execution."""
    
    def __init__(self, redis, scheduler):
        self.redis = redis
        self.scheduler = scheduler
    
    def create_workflow(self, workflow_id, steps):
        """
        steps = [
            {"id": "extract", "type": "data_extract", "depends_on": []},
            {"id": "transform", "type": "data_transform", "depends_on": ["extract"]},
            {"id": "load", "type": "data_load", "depends_on": ["transform"]},
            {"id": "notify", "type": "send_notification", "depends_on": ["load"]},
        ]
        """
        # Store workflow definition
        self.redis.hmset(f"workflow:{workflow_id}", {
            'steps': json.dumps(steps),
            'status': 'RUNNING',
        })
        
        # Schedule initial steps (no dependencies)
        for step in steps:
            if not step['depends_on']:
                self.scheduler.enqueue_job(
                    job_id=f"workflow:{workflow_id}:step:{step['id']}",
                    job_type=step['type'],
                    payload={'workflow_id': workflow_id, 'step_id': step['id']},
                )
    
    def on_step_complete(self, workflow_id, step_id):
        """Called when a workflow step completes. Triggers dependent steps."""
        steps = json.loads(self.redis.hget(f"workflow:{workflow_id}", 'steps'))
        
        # Mark this step as complete
        self.redis.hset(f"workflow:{workflow_id}:step:{step_id}", 'status', 'COMPLETED')
        
        # Find steps that depend on the completed step
        for step in steps:
            if step_id in step.get('depends_on', []):
                # Check if ALL dependencies are complete
                all_deps_complete = all(
                    self.redis.hget(f"workflow:{workflow_id}:step:{dep}", 'status') == b'COMPLETED'
                    for dep in step['depends_on']
                )
                
                if all_deps_complete:
                    self.scheduler.enqueue_job(
                        job_id=f"workflow:{workflow_id}:step:{step['id']}",
                        job_type=step['type'],
                        payload={'workflow_id': workflow_id, 'step_id': step['id']},
                    )
```

---

## Retry Strategies and Backoff

### Exponential Backoff with Jitter

```
Retry 1: Wait 1 second + random(0, 0.5s)
Retry 2: Wait 2 seconds + random(0, 1s)
Retry 3: Wait 4 seconds + random(0, 2s)
Retry 4: Wait 8 seconds + random(0, 4s)
Retry 5: Wait 16 seconds + random(0, 8s) → MAX

Formula:
  delay = min(max_delay, base_delay × 2^retry_count) + random(0, jitter)

Why jitter? Without it, all retrying jobs wake up at the same time → thundering herd.

Implementation in Redis:
  On failure: Calculate retry delay.
  ZADD jobs:pending {now + delay} {job_id}  → Re-queue with future execution time.
  The job sits in the ZSET until its score (time) arrives.
```

### Retry Decision Matrix

```
Error Type              | Retry? | Max Retries | Backoff
─────────────────────────┼────────┼─────────────┼──────────
Transient (timeout)      | Yes    | 5           | Exponential
Rate limited (429)       | Yes    | 3           | Fixed (Retry-After)
Server error (500)       | Yes    | 3           | Exponential
Bad request (400)        | No     | 0           | —
Auth error (401/403)     | No     | 0           | —
Not found (404)          | No     | 0           | —
Resource exhausted       | Yes    | 5           | Exponential + longer base
```

---

## Dead Letter Queue and Poison Pills

### What Goes to the DLQ

```
A job ends up in the DLQ when:
  1. Max retries exceeded (e.g., failed 5 times)
  2. Non-retryable error (400 Bad Request)
  3. Poison pill: Job causes worker crash every time

DLQ: Redis ZSET "jobs:dlq" with score = time entered DLQ.

  ZADD jobs:dlq {now} {job_id}
  
DLQ jobs are NOT automatically retried.
Requires human investigation or automated remediation.
```

### Poison Pill Detection

```
A poison pill is a job that crashes the worker every time.

  Job: {"type": "process_image", "payload": {"url": "https://evil.com/bomb.jpg"}}
  Worker downloads the image → decompression bomb → OOM → worker crashes.
  Reaper re-queues the job → another worker crashes → infinite loop!

Detection:
  Track consecutive failures per job.
  If retry_count > 3 AND all failures are "worker_death" → likely poison pill.
  Move to DLQ immediately. Alert ops team.

Prevention:
  Validate job payloads before execution.
  Resource limits on workers (cgroups, container limits).
  Timeout on execution (kill after max_execution_time).
```

---

## Exactly-Once vs At-Least-Once Execution

### At-Least-Once (Default)

```
Guarantee: Every job executes AT LEAST one time.
Possible: Job executes MORE than once (on failure/retry).
Requirement: Jobs MUST be idempotent.

This is what our architecture provides:
  - ZPOPMIN dequeue is atomic (no two workers get the same job)
  - If worker dies → visibility timeout → re-queue → another worker runs it
  - If ACK is lost → reaper re-queues → another worker runs it
  - Duplicate execution possible → idempotency required

99.99% of the time: Jobs execute exactly once.
0.01% of the time: Jobs execute twice (on failures).
```

### Exactly-Once (Hard, Expensive)

```
Guarantee: Every job executes EXACTLY one time. No duplicates.

Requires:
  1. Dequeue + execute + acknowledge in a SINGLE transaction
  2. No possibility of duplicate execution

Approaches:
  A. Two-phase commit (2PC): Dequeue from queue + execute in DB → atomic.
     Slow, complex, fragile. Not recommended.
  
  B. Outbox pattern: Write job result to DB in same transaction as dedup key.
     If transaction commits → job completed exactly once.
     If transaction rolls back → job never happened → safe to retry.
  
  C. Idempotent at-least-once (pragmatic):
     At-least-once execution + idempotent jobs = effectively exactly-once.
     This is what 99% of production systems use.

RECOMMENDATION: At-least-once with idempotent jobs.
  Simpler, faster, scales better, works in practice.
```

---

## Scaling to Millions of Jobs

### Scaling Dimensions

```
1. Number of job definitions: 10M cron schedules
   Solution: Database with indexed next_execution_time.
   Scheduler scans only due jobs (index makes this O(K) not O(N)).

2. Number of pending jobs in queue: 1M at peak
   Solution: Redis ZSET handles 1M members easily (~100 MB).
   ZADD/ZPOPMIN are O(log N) → fast even at millions.

3. Number of workers: 500 concurrent workers
   Solution: Workers are stateless → add more.
   Redis connection pool: 500 connections → well within limits.

4. Job execution throughput: 10K jobs/sec
   Solution: Workers pull in batches (10 jobs per poll).
   500 workers × 10 jobs/batch ÷ 1 sec = 5K jobs/sec per poll cycle.
   
   For 10K/sec: 1000 workers or faster poll cycle (500ms).
```

### Bottleneck Analysis

```
Component      | Capacity                    | Bottleneck At
───────────────┼─────────────────────────────┼─────────────
Redis ZSET     | 300K ops/sec                | 300K jobs/sec enqueue+dequeue
PostgreSQL     | 5K scans/sec (indexed)      | 5K scheduler cycles/sec
Worker fleet   | N × concurrency             | Depends on job duration
Network        | 10 Gbps between components  | Rarely the bottleneck

At most scales, the bottleneck is JOB EXECUTION TIME, not scheduling.
  1-second job × 500 workers = 500 jobs/sec throughput.
  100ms job × 500 workers = 5000 jobs/sec throughput.
  
Scale workers to match required throughput.
```

---

## Observability and Monitoring

### Key Metrics

```
Queue Health:
  jobs.pending.count          = gauge    # Jobs waiting to execute
  jobs.processing.count       = gauge    # Jobs currently being executed
  jobs.dlq.count              = gauge    # Jobs in dead letter queue
  jobs.completed_per_sec      = counter  # Throughput
  jobs.failed_per_sec         = counter  # Failure rate

Latency:
  jobs.queue_wait_time_ms     = histogram  # Time from enqueue to pickup
  jobs.execution_time_ms      = histogram  # Time to execute
  jobs.end_to_end_latency_ms  = histogram  # Scheduled time → completion time

Worker Health:
  workers.active_count        = gauge
  workers.dead_count          = counter  # Workers that missed heartbeats
  workers.utilization         = gauge    # % of slots in use

Scheduler Health:
  scheduler.leader_id         = info
  scheduler.cycle_time_ms     = histogram
  scheduler.jobs_enqueued     = counter
```

### Alerting Rules

```
CRITICAL:
  jobs.pending.count > 100K for 5 min       → Queue backlog growing
  jobs.dlq.count > 0                        → Jobs failing permanently
  workers.active_count < 10                  → Worker fleet dying
  scheduler.cycle_time_ms > 30000            → Scheduler stuck

WARNING:
  jobs.queue_wait_time_ms.p99 > 60000        → Jobs waiting >1 min
  jobs.failed_per_sec > 10                    → High failure rate
  workers.utilization > 90%                   → Need more workers
```

---

## Real-World Production Patterns

### Pattern 1: Airflow (Data Pipeline Scheduling)

```
Apache Airflow: The most popular workflow orchestrator.
  - DAG-based: Define pipelines as Python DAGs.
  - Scheduler: Parses DAGs, creates DagRuns, schedules Tasks.
  - Executor: CeleryExecutor (distributed) or KubernetesExecutor.
  - Metastore: PostgreSQL tracks task states.
  
  Scale: LinkedIn runs 10K+ Airflow DAGs with millions of tasks/day.
  
  Limitation: Single scheduler process (HA added in Airflow 2.0).
```

### Pattern 2: Sidekiq (Ruby Background Jobs)

```
Sidekiq: Redis-backed job processor for Ruby.
  - Jobs pushed to Redis lists (LPUSH/BRPOP).
  - Scheduled jobs in a ZSET (score = execution time).
  - Workers are threads in a Sidekiq process.
  
  Scale: ~7K jobs/sec per Sidekiq process.
  10 processes = 70K jobs/sec.
  
  Simplicity: Redis-only, no ZooKeeper, no DB for queue.
```

### Pattern 3: AWS Step Functions (Serverless Workflows)

```
Step Functions: Managed workflow orchestration.
  - Define workflows as state machines (JSON/YAML).
  - Steps: Lambda, ECS tasks, SQS, SNS, DynamoDB, etc.
  - Built-in retry, error handling, parallel execution.
  - No servers to manage.
  
  Scale: Millions of executions per account.
  Cost: $0.025 per 1K state transitions.
```

### Pattern 4: Temporal (Durable Execution)

```
Temporal: Open-source durable workflow execution.
  - Workflows written in Go, Java, Python, TypeScript.
  - Durable execution: If a worker dies, workflow resumes exactly where it left off.
  - Event sourcing: Complete history of every workflow execution.
  - Timer service: Handles scheduled and delayed executions.
  
  Scale: Uber runs Temporal at 10M+ workflows/day.
  Key feature: Workflows can run for days/weeks (long-running with checkpoints).
```

### Pattern 5: Custom Redis-Based Scheduler (Stripe)

```
Stripe's job scheduling:
  - Redis ZSET for delayed/scheduled jobs.
  - SQS for immediate job dispatch.
  - Workers process idempotent payment jobs.
  - Exactly-once via idempotency keys in PostgreSQL.
  
  Scale: Millions of payment jobs per day.
  Critical: No duplicate charges (idempotency is non-negotiable for payments).
```

---

## Interview Talking Points & Scripts

### Script 1: Core Architecture

> *"For distributed job scheduling, I'd use three components: a leader scheduler elected via Redis SETNX, a Redis sorted set as the time-based job queue, and a stateless worker fleet. The scheduler evaluates cron expressions, computes next execution times, and enqueues jobs into the ZSET with the execution timestamp as the score. Workers atomically dequeue jobs using a Lua script (ZRANGEBYSCORE + ZREM in one operation), execute them, and acknowledge completion. If a worker dies, the visibility timeout expires and a reaper re-queues the job."*

### Script 2: No Single Point of Failure

> *"There's no single point of failure. The scheduler runs in active-passive mode with leader election — if the active scheduler dies, a standby acquires the lock within 30 seconds and resumes scheduling. Workers are stateless and horizontally scalable — if one dies, its jobs are reclaimed via the visibility timeout pattern. Redis itself runs with Sentinel or Cluster for HA. The database stores job definitions but isn't on the hot path for execution."*

### Script 3: Visibility Timeout

> *"I'd use the SQS visibility timeout pattern. When a worker claims a job, it's moved from the pending ZSET to a processing ZSET with a score set to now + visibility_timeout (e.g., 5 minutes). If the worker completes, it deletes the job from processing. If the worker dies, the visibility timeout expires, and a reaper process detects it (score < now), re-queuing the job for another worker. For long-running jobs, the worker extends the timeout via heartbeat every 60 seconds."*

### Script 4: Idempotency

> *"Since we guarantee at-least-once execution, jobs MUST be idempotent. I'd use an idempotency key stored alongside the job result in the database. Before executing, the worker checks: has this job_id been completed? If yes, skip. If no, execute and record the idempotency key in the same transaction as the business logic. This way, even if a job runs twice due to a reaper race condition, the second execution is a no-op."*

### Script 5: Scaling

> *"Scaling has two dimensions: scheduling capacity and execution capacity. The scheduler scans the database for due jobs using an index on next_execution_time — O(K) where K is the number of due jobs, not N total jobs. The Redis ZSET handles millions of pending jobs at O(log N) per operation. Execution scales by adding workers — they're stateless, just pull from the queue. For 10K jobs/sec, I'd need 500 workers with 100ms average execution time."*

### Script 6: Workflow DAGs

> *"For multi-step workflows, I'd implement a simple DAG engine. Each step is a job with a list of dependencies. When a step completes, the workflow engine checks if all dependencies of the next steps are satisfied. If yes, it enqueues those steps. This is like a simplified Airflow — the DAG definition is stored in the database, and execution follows the dependency graph. A workflow-level timeout catches stuck workflows."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "Just use cron on each server"

**Bad**: Multiple servers running the same cron → duplicate execution.
**Fix**: Single leader scheduler + distributed queue + worker fleet.

### ❌ Mistake 2: No visibility timeout

**Bad**: Worker claims job, dies, job is lost forever.
**Fix**: Visibility timeout. If worker doesn't ACK within timeout, reaper re-queues the job.

### ❌ Mistake 3: Non-idempotent jobs

**Bad**: "We guarantee exactly-once execution" (nearly impossible at scale).
**Fix**: At-least-once execution + idempotent jobs = effectively exactly-once. All jobs must be safe to re-execute.

### ❌ Mistake 4: No leader election

**Bad**: "Both scheduler instances run simultaneously — ZADD is idempotent so it's fine."
**Fix**: Idempotent enqueue is fine, but parallel cron evaluation and DB updates cause race conditions. Use leader election.

### ❌ Mistake 5: Polling the database for every job

**Bad**: Workers query the database every second to check for due jobs.
**Fix**: Scheduler pushes to Redis queue. Workers pull from Redis. Database is only scanned by the scheduler (one query per cycle).

### ❌ Mistake 6: No dead letter queue

**Bad**: Failed jobs retry forever, clogging the queue.
**Fix**: Max retry limit + DLQ. After N failures, move to DLQ for manual inspection.

### ❌ Mistake 7: No worker heartbeat

**Bad**: Worker dies silently. No one reassigns its jobs for hours.
**Fix**: Workers send heartbeats every 10 seconds. Missing 3 heartbeats → worker declared dead → jobs reclaimed.

### ❌ Mistake 8: Single Redis ZSET for all job types

**Bad**: Payment jobs stuck behind 100K analytics jobs.
**Fix**: Separate queues per job type or priority level. Workers poll high-priority queue first.

---

## Distributed Job Scheduling by System Design Problem

| Problem | Job Type | Volume | Key Considerations |
|---|---|---|---|
| **Email Digest** | Recurring (daily) | 10M/day | Batch into hourly chunks, idempotent send |
| **Payment Processing** | On-demand + scheduled | 1M/day | Exactly-once via idempotency key, DLQ critical |
| **Report Generation** | Recurring (hourly/daily) | 100K/day | Long-running (30 min), heartbeat extension |
| **Data Pipeline (ETL)** | DAG workflow | 10K/day | Dependencies, Airflow/Temporal pattern |
| **Notification System** | Scheduled + triggered | 50M/day | Priority queues, tenant fairness |
| **Cache Warming** | Recurring (every 5 min) | 100K/cycle | Fast execution, low priority |
| **Cleanup / TTL Expiry** | Recurring (hourly) | 1M/day | Batch processing, can be delayed |
| **Subscription Billing** | Recurring (monthly) | 10M/month | Idempotent charges, retry with backoff |
| **Health Checks** | Recurring (every 30s) | 100K/cycle | Fast timeout, alert on failure |
| **Media Processing** | On-demand (upload-triggered) | 10M/day | GPU workers, long visibility timeout |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│           DISTRIBUTED JOB SCHEDULING — CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ARCHITECTURE:                                                       │
│    Scheduler (leader) → Redis ZSET Queue → Worker Fleet              │
│    Leader election: Redis SETNX or ZooKeeper                         │
│    Workers: Stateless, pull-based, horizontally scalable             │
│                                                                      │
│  REDIS ZSET QUEUE:                                                   │
│    Score = execution timestamp. Member = job_id.                     │
│    ZADD to enqueue. ZPOPMIN (Lua) to dequeue atomically.            │
│    ZRANGEBYSCORE for "what's due now?"                               │
│                                                                      │
│  VISIBILITY TIMEOUT (SQS pattern):                                   │
│    Claimed job → processing ZSET (score = now + timeout).            │
│    Worker ACKs → remove from processing. Done.                       │
│    Worker dies → timeout expires → reaper re-queues.                 │
│    Long jobs: Extend timeout via heartbeat every 60s.                │
│                                                                      │
│  HEARTBEAT:                                                          │
│    Worker → HSET worker:{id} last_heartbeat {now} every 10s.        │
│    Missing 3 heartbeats → worker dead → reclaim its jobs.            │
│                                                                      │
│  IDEMPOTENCY: MANDATORY.                                             │
│    At-least-once execution → jobs may run more than once.            │
│    Idempotency key in DB (same transaction as business logic).       │
│    Natural idempotency: SET, DELETE, PUT are safe to repeat.         │
│                                                                      │
│  JOB STATES:                                                         │
│    SCHEDULED → PENDING → PROCESSING → COMPLETED                     │
│                              ↓                                       │
│                         FAILED → RETRY (up to max) → DLQ            │
│                                                                      │
│  RETRY: Exponential backoff with jitter.                             │
│    delay = min(max, base × 2^retry) + random(0, jitter)             │
│    Re-queue: ZADD jobs:pending {now + delay} {job_id}                │
│                                                                      │
│  SCALING:                                                            │
│    Scheduling: DB index on next_execution_time.                      │
│    Queue: Redis ZSET handles millions. O(log N) ops.                 │
│    Execution: Add more workers. Workers are the scaling dimension.   │
│                                                                      │
│  PRIORITY: Multiple queues (high/medium/low).                        │
│    Workers poll high first. Weighted round-robin for fairness.       │
│                                                                      │
│  WORKFLOWS (DAGs):                                                   │
│    Steps with dependencies. On step complete → check deps → enqueue. │
│    Simplified Airflow pattern.                                       │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 10: Message Queues** — SQS/Kafka as alternatives to Redis ZSET
- **Topic 21: Concurrency Control** — Preventing duplicate job execution
- **Topic 32: Leader Election** — ZooKeeper, Redis, etcd patterns
- **Topic 40: Redis Deep Dive** — ZSET, Lua scripting, Cluster
- **Topic 42: Job Scheduling** — Basic job scheduling patterns
- **Topic 55: Virtual Queue** — Queue pattern with admission control

---

*This document is part of the System Design Interview Deep Dive series.*
