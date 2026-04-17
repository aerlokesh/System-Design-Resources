# 🎯 Topic 32: Leader Election & Coordination

> **System Design Interview — Deep Dive**
> A comprehensive guide covering ZooKeeper-based leader election, fencing tokens to prevent split-brain, distributed coordination for job schedulers and singleton processes, Raft consensus, Redis-based locks, lease management, and how to articulate leader election decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Leader Election](#why-leader-election)
3. [ZooKeeper for Leader Election](#zookeeper-for-leader-election)
4. [etcd and Raft Consensus](#etcd-and-raft-consensus)
5. [Redis-Based Leader Election](#redis-based-leader-election)
6. [Fencing Tokens — Preventing Split-Brain](#fencing-tokens--preventing-split-brain)
7. [Lease-Based Leadership](#lease-based-leadership)
8. [Leader Election Failure Modes](#leader-election-failure-modes)
9. [Distributed Locks vs Leader Election](#distributed-locks-vs-leader-election)
10. [Worker Architecture: Stateless Workers + Leader Coordinator](#worker-architecture-stateless-workers--leader-coordinator)
11. [Real-World System Examples](#real-world-system-examples)
12. [Deep Dive: Applying Leader Election to Popular Problems](#deep-dive-applying-leader-election-to-popular-problems)
13. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
14. [Common Interview Mistakes](#common-interview-mistakes)
15. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Leader election** ensures exactly **ONE** node is the designated leader at any given time in a distributed system. The leader performs coordination tasks (job scheduling, partition assignment, configuration distribution) while other nodes are standby. When the leader fails, a new leader is automatically elected.

```
Without leader election:
  Worker A scans job queue → assigns job 123 to Worker X
  Worker B scans job queue → assigns job 123 to Worker Y
  → Job 123 executed TWICE! (duplicate email, double charge, data corruption)

With leader election:
  Leader (Worker A) scans job queue → assigns jobs to workers
  Workers B, C, D: Only process assigned jobs, don't scan the queue
  If Leader A crashes → new leader elected (Worker B) → resumes scanning
  → Job 123 executed exactly ONCE ✓
```

**The fundamental guarantee**: At any point in time, there is **at most one** active leader. Never zero for long (availability), never two simultaneously (correctness).

---

## Why Leader Election

### Use Cases

| Use Case | Why One Leader? | Consequence of Two Leaders |
|---|---|---|
| **Job scheduler** | Only one node should scan for due jobs | Jobs executed multiple times |
| **Kafka consumer group coordinator** | One coordinator assigns partitions to consumers | Duplicate message processing |
| **Database master** | Only one node accepts writes | Split-brain, data divergence |
| **Cron job runner** | Scheduled task should run once, not N times | N duplicate executions |
| **Cache warming** | Only one node should rebuild the cache | Wasteful redundant work |
| **Migration runner** | Only one node should run schema migrations | Conflicting migrations crash DB |
| **Rate limit coordinator** | One node aggregates global rate counts | Over-counting or under-counting |
| **Config distributor** | One node pushes config changes to all | Conflicting configs applied |

### What Makes Leader Election Hard

```
The challenge: Distributed consensus is fundamentally difficult.

1. Network partitions: Node A can't reach Node B — is B dead, or just unreachable?
2. Process pauses: GC pause makes Node A unresponsive for 10 seconds — is it dead?
3. Clock skew: Node A's clock is 500ms ahead of Node B's — who wrote "last"?
4. Partial failures: Network works one direction but not the other (asymmetric partition)

These ambiguities mean you can never be 100% sure the current leader is still alive.
The best you can do: Use timeouts + fencing to BOUND the damage from stale leaders.
```

---

## ZooKeeper for Leader Election

### How It Works

```
ZooKeeper is a distributed coordination service with strong consistency (ZAB protocol).

Leader election via ephemeral sequential znodes:

1. All candidates create ephemeral sequential znodes under /election:
   Node A creates: /election/leader-0000000001
   Node B creates: /election/leader-0000000002
   Node C creates: /election/leader-0000000003

2. The node with the LOWEST sequence number is the leader:
   Node A (leader-0000000001) → LEADER ✓
   Node B (leader-0000000002) → STANDBY (watches leader-0000000001)
   Node C (leader-0000000003) → STANDBY (watches leader-0000000002)

3. If leader (Node A) crashes:
   ZooKeeper detects session timeout (heartbeat missed for 30 seconds)
   Ephemeral znode /election/leader-0000000001 is automatically deleted
   
4. Node B's watch triggers → Node B checks: am I now the lowest?
   leader-0000000002 is now the lowest → Node B becomes LEADER
   
   Re-election time: ~30-45 seconds (session timeout + watch notification)

Key properties:
  ✅ Ephemeral znodes: Automatically deleted when session ends (crash detection)
  ✅ Sequential znodes: Deterministic ordering (no ambiguity about who is leader)
  ✅ Watches: Efficient notification (no polling required)
  ✅ Strong consistency: ZAB consensus ensures all ZK nodes agree
```

### ZooKeeper Session Management

```
Session lifecycle:
  1. Client connects to ZK → session created (session_id, timeout=30s)
  2. Client sends heartbeats to ZK every 10 seconds
  3. ZK tracks heartbeats per session
  4. If no heartbeat for 30 seconds → session expired → all ephemeral znodes deleted

Session timeout considerations:
  Too short (5s): False positives — GC pauses, network blips trigger election
    → Leader changes every few minutes → instability
  Too long (60s): Slow failover — 60 seconds with no leader
    → Long gap in job scheduling, configuration updates
  
  Sweet spot: 15-30 seconds
    30 seconds for most applications (tolerates brief network issues)
    15 seconds for latency-sensitive coordination (faster failover, more false positives)

Herd effect prevention:
  Without optimization: When leader dies, ALL standbys check if they're the new leader
  → "Herd effect" — N nodes simultaneously query ZK → ZK overloaded
  
  Solution: Each node watches only the znode BEFORE it in sequence
  Node C watches leader-0000000002 (not leader-0000000001)
  Only Node B is notified when leader dies → one check, not N
```

### ZooKeeper Implementation (Java/Curator)

```java
// Using Apache Curator (high-level ZooKeeper client)
LeaderSelector selector = new LeaderSelector(client, "/election", new LeaderSelectorListener() {
    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        // This method is called when THIS node becomes the leader
        System.out.println("I am now the leader!");
        
        while (isRunning) {
            scanJobQueue();
            assignJobsToWorkers();
            Thread.sleep(1000);
        }
        
        // When this method returns, leadership is released
        // Another node will be elected leader
    }
});
selector.autoRequeue();  // Re-enter election if leadership is lost
selector.start();
```

---

## etcd and Raft Consensus

### How etcd Works

```
etcd uses Raft consensus (simpler than Paxos, widely used):

Raft guarantees:
  ✅ At most ONE leader among etcd nodes at any time
  ✅ Writes committed only when majority of etcd nodes agree
  ✅ Reads from leader are always consistent
  ✅ Automatic leader election among etcd nodes (not your app nodes)

Application leader election via etcd leases:

1. Application node requests a lease from etcd (TTL = 15 seconds):
   PUT /leader value="node-A" lease=123
   
2. If key doesn't exist → node-A becomes leader
   If key exists → someone else is leader → node-A waits
   
3. node-A refreshes lease every 5 seconds (keepalive)
   
4. If node-A crashes → lease expires after 15 seconds
   → /leader key is deleted → next candidate acquires it

Comparison with ZooKeeper:
  Both provide: Strong consistency, ephemeral keys, watches
  etcd: Simpler API, Raft (easier to understand), used by Kubernetes
  ZooKeeper: More mature, ZAB protocol, used by Kafka/Hadoop
  
  For new systems: etcd is increasingly preferred (simpler, K8s integration)
  For Kafka ecosystem: ZooKeeper (native integration, although KRaft is replacing it)
```

### Raft Consensus Deep Dive

```
Raft roles:
  Leader: Handles all client requests, replicates to followers
  Follower: Passively receives replicated data from leader
  Candidate: Temporarily during election (trying to become leader)

Election process:
  1. Follower hasn't heard from leader for election_timeout (150-300ms)
  2. Follower becomes Candidate, increments term, votes for itself
  3. Candidate requests votes from all other nodes
  4. If majority vote yes → Candidate becomes Leader
  5. New Leader starts sending heartbeats to prevent new elections

Split vote handling:
  If two Candidates start election simultaneously → split vote (neither gets majority)
  Both back off for random time (150-300ms) → one restarts election first
  Randomized timeout breaks the symmetry → converges quickly

Log replication:
  Leader receives write → appends to local log
  Sends log entry to all followers
  When majority acknowledge → entry is committed
  Leader applies to state machine → responds to client
  
  Committed entries are never lost (even if leader crashes)
  Followers apply committed entries to their state machines
```

---

## Redis-Based Leader Election

### Simple Redis Lock

```python
import redis
import time

def try_become_leader(redis_client, node_id, ttl=30):
    """Attempt to acquire leadership via Redis SETNX"""
    acquired = redis_client.set(
        "leader_lock", 
        node_id, 
        nx=True,      # Only set if key doesn't exist
        ex=ttl         # Auto-expire after TTL seconds
    )
    
    if acquired:
        # I'm the leader — start leader loop
        while True:
            do_leader_work()
            # Renew lease at 1/3 of TTL (before expiry)
            redis_client.expire("leader_lock", ttl)
            time.sleep(ttl / 3)
    else:
        # Someone else is leader — wait and retry
        time.sleep(ttl / 2)
        try_become_leader(redis_client, node_id, ttl)
```

### Why Redis Is NOT Strongly Consistent

```
Redis uses async replication:
  1. Client writes to Redis master → master acknowledges
  2. Master replicates to replicas asynchronously
  
Failure scenario:
  1. Node A acquires lock on Redis master: SET leader_lock "node-A" NX EX 30
  2. Master crashes BEFORE replicating to replica
  3. Replica is promoted to master → lock doesn't exist!
  4. Node B acquires lock on new master: SET leader_lock "node-B" NX EX 30
  5. Now BOTH Node A and Node B think they're the leader → SPLIT-BRAIN!

This window is typically < 1 second, but it exists.
```

### When Redis Is Acceptable for Leader Election

```
✅ Acceptable (non-critical coordination):
  Cache warming (duplicate warming is wasteful but not dangerous)
  Analytics aggregation (duplicate counting can be reconciled)
  Notification batching (duplicate notification is annoying but not harmful)
  Background cleanup tasks (idempotent operations tolerate duplicates)

❌ Not acceptable (critical coordination):
  Payment processing (duplicate charge is unacceptable)
  Job scheduling where duplicate execution is harmful
  Master database designation (split-brain causes data corruption)
  Distributed locks protecting financial transactions

Rule of thumb:
  If duplicate execution of the leader's work is TOLERABLE → Redis is fine
  If duplicate execution causes DATA LOSS or FINANCIAL HARM → use ZooKeeper/etcd
```

### Redlock (Distributed Redis Lock)

```
Redlock attempts to fix Redis's consistency issue:

1. Client tries to acquire lock on N independent Redis instances (N=5)
2. If client acquires lock on majority (≥ 3 of 5) → lock acquired
3. If not → release any locks acquired and retry

Controversy:
  Martin Kleppmann's analysis shows Redlock is still unsafe:
  - Clock skew between Redis instances can cause two clients to hold the lock
  - Process pauses (GC) can cause lock to expire while client thinks it holds it
  - Fencing tokens are still needed for true safety

Recommendation:
  For truly critical leader election: Use ZooKeeper or etcd (consensus-based)
  For non-critical: Simple Redis SETNX is simpler and "good enough"
  Redlock: More complex than SETNX but still not as safe as ZK/etcd
```

---

## Fencing Tokens — Preventing Split-Brain

### The Stale Leader Problem

```
The most dangerous failure mode in leader election:

Timeline:
  T1: Leader A acquires lock (token = 33), starts processing
  T2: Leader A hits a GC pause (frozen for 15 seconds)
  T3: Lock expires (TTL reached) — ZK/Redis thinks A is dead
  T4: Leader B elected (token = 34), starts processing
  T5: Leader A "wakes up" from GC pause — STILL THINKS IT'S THE LEADER!
  
  T6: Leader B writes "balance = $500" to database (token = 34)
  T7: Leader A writes "balance = $300" to database (token = 33)
  → Leader A's stale write OVERWRITES Leader B's correct write!
  → Data corruption. $200 lost.

This can happen with ANY leader election mechanism — ZooKeeper, etcd, Redis.
The lock can expire while the leader is paused/slow.
```

### The Solution: Fencing Tokens

```
Each lock acquisition generates a monotonically increasing token (epoch number):

  Leader A: acquires lock → token = 33
  Leader B: acquires lock (after A's expires) → token = 34

Storage layer enforces fencing:
  Every write includes the fencing token.
  Storage layer rule: Accept write ONLY if token ≥ last_seen_token.

Sequence (with fencing):
  T6: Leader B writes (token=34) → accepted, storage records last_seen_token = 34
  T7: Leader A writes (token=33) → REJECTED (33 < 34)

Stale leader's writes are automatically rejected.
No split-brain. No data corruption.

Implementation:
  // Storage layer (e.g., database)
  UPDATE accounts 
  SET balance = $300, last_token = 33 
  WHERE account_id = 'X' AND last_token < 33;
  // Returns 0 rows affected → stale write rejected
```

### Implementing Fencing

```
Option 1: Database-level fencing
  Each table has a "fencing_token" column
  Writes include token in WHERE clause
  Rejected if token <= current token

  UPDATE jobs 
  SET status = 'ASSIGNED', assigned_to = 'worker-5', fencing_token = 34
  WHERE job_id = 123 AND fencing_token < 34;

Option 2: Application-level fencing
  Before processing, check current token:
  
  def process_as_leader(job, my_token):
      current_token = redis.get("leader_token")
      if my_token < current_token:
          raise StaleLeaderException("I'm no longer the leader")
      # Safe to process
      assign_job(job)

Option 3: ZooKeeper ephemeral znode version
  ZooKeeper znodes have a version number that auto-increments
  Use the znode version as the fencing token
  Guaranteed monotonically increasing
```

---

## Lease-Based Leadership

### How Leases Work

```
A lease is a time-bounded lock:

1. Leader acquires lease (TTL = 15 seconds)
2. Leader periodically renews lease (every 5 seconds)
3. If leader fails to renew → lease expires → leadership available

Lease renewal:
  while (isLeader) {
      doLeaderWork();
      
      boolean renewed = renewLease();
      if (!renewed) {
          // Lost leadership — stop immediately
          stepDown();
          return;
      }
      
      sleep(LEASE_TTL / 3);  // Renew at 1/3 of TTL
  }

Why renew at TTL/3?
  TTL = 15 seconds, renew every 5 seconds
  If ONE renewal fails → still have 10 seconds before expiry
  If TWO renewals fail → still have 5 seconds
  Only after 3 consecutive failures → lease expires
  Provides tolerance for brief network issues
```

### Lease Safety Rules

```
Rule 1: Stop work BEFORE lease expires
  Don't start work that takes longer than remaining lease time
  
  remaining = lease_expiry - now()
  if remaining < MIN_LEASE_BUFFER (e.g., 5 seconds):
      // Don't start new work — renew first or step down
      renewOrStepDown()

Rule 2: Make work idempotent
  Even with fencing, it's possible for a stale leader to start work
  before the fence is checked. If all work is idempotent:
    - Duplicate assignment → same result
    - Duplicate counter update → check-and-set prevents double count

Rule 3: Use fencing tokens for storage writes
  Even if the leader "thinks" it has the lease,
  the storage layer is the final arbiter via fencing tokens.
```

---

## Leader Election Failure Modes

### Comprehensive Failure Matrix

| Failure | Impact | Duration | Mitigation |
|---|---|---|---|
| **Leader crashes** | No leader until re-election | 15-45 seconds | Standby auto-elected via ZK/etcd |
| **Network partition** | Leader isolated from ZK | Lease duration | Fencing tokens prevent stale writes |
| **GC pause** | Leader unresponsive temporarily | 1-30 seconds | Fencing tokens + lease timeout |
| **ZK cluster failure** | No election possible | Until ZK recovers | Multi-AZ ZK deployment (3-5 nodes) |
| **Split-brain** | Two nodes think they're leader | Until fencing triggers | Fencing tokens on all writes |
| **Flapping** | Leader keeps changing rapidly | Continuous | Increase session timeout, investigate root cause |
| **Slow leader** | Leader alive but not performing | Until detected | Health check + performance-based demotion |

### What Happens During the Failover Gap

```
Timeline of leader failure:

T+0s:    Leader A crashes
T+0-15s: ZK still waiting for heartbeat timeout
          No leader active. New jobs accumulate in queue.
          In-progress jobs: Unaffected (workers continue processing)
          
T+15-30s: ZK detects session timeout, deletes ephemeral znode
          Standby Node B detects via watch
          Node B creates new ephemeral znode → becomes leader
          
T+30-35s: Node B initializes leader state
          Scans job queue for unassigned jobs
          Resumes normal operation

Total gap: 30-45 seconds

During the gap:
  ✅ In-progress jobs: Continue normally (workers are independent)
  ⚠️ New jobs: Wait in queue (no one scanning/assigning)
  ⚠️ Monitoring: May show stale dashboards (if leader was the aggregator)
  ❌ Nothing lost: Jobs queue, metrics queue, everything catches up

For most systems: 30-45 second gap is acceptable.
For payment processing: Use multi-leader with fencing (no gap).
```

### Preventing Flapping

```
Flapping = leader changes back and forth rapidly

Cause: Unstable network, intermittent GC pauses, overloaded ZK

Symptoms:
  Leader elected → loses connection → new leader elected → old leader reconnects
  → old leader elected → loses connection again → cycle repeats

Prevention:
  1. Increase session timeout (30s → 60s): Tolerate brief instability
  2. Jitter: Random delay before re-entering election (avoid thundering herd)
  3. Backoff: If a node has been leader and lost it, wait longer before trying again
  4. Health check: Don't just check ZK connectivity — check leader's ability to do work
     If CPU > 95% → step down voluntarily (don't wait for timeout)
```

---

## Distributed Locks vs Leader Election

### Key Differences

| Aspect | Distributed Lock | Leader Election |
|---|---|---|
| **Duration** | Short (seconds to minutes) | Long (hours to indefinitely) |
| **Purpose** | Protect a critical section | Coordinate ongoing work |
| **Granularity** | Per-resource (one lock per seat) | Per-role (one leader per system) |
| **Who holds it** | Any worker, briefly | One designated coordinator |
| **On failure** | Lock auto-releases, another worker retries | New leader elected, takes over coordination |
| **Example** | Lock seat A1 during booking | Leader scans job queue continuously |

### When to Use Which

```
Distributed Lock:
  ✅ "Only one worker should process this specific order at a time"
  ✅ "Lock this database row while updating it"
  ✅ "Prevent two users from booking the same seat simultaneously"
  
  Granularity: Per-item (thousands of concurrent locks)
  Duration: Seconds (processing time for one operation)
  Implementation: Redis SETNX, ZK ephemeral znode

Leader Election:
  ✅ "Only one node should scan the job queue"
  ✅ "Only one node should be the Kafka consumer group coordinator"
  ✅ "Only one node should run the nightly batch reconciliation"
  
  Granularity: Per-role (one leader for the entire system or subsystem)
  Duration: Hours to indefinitely (until crash or graceful shutdown)
  Implementation: ZK ephemeral sequential znodes, etcd leases
```

---

## Worker Architecture: Stateless Workers + Leader Coordinator

### The Recommended Pattern

```
┌────────────────────┐
│  Leader (Node A)    │
│  ────────────────── │
│  Scans job queue    │
│  Assigns jobs       │
│  Monitors progress  │
│  Handles timeouts   │
└─────────┬──────────┘
          │ Job assignments via Redis queue
  ┌───────┼───────┬───────┐
  │       │       │       │
  ▼       ▼       ▼       ▼
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ W1  │ │ W2  │ │ W3  │ │ W4  │  (Stateless workers)
└─────┘ └─────┘ └─────┘ └─────┘
  Pull jobs from Redis queue, process, report completion

Key principles:
  1. Workers are STATELESS — they pull from a queue, process, and report
  2. Workers don't know (or care) who the leader is
  3. If a worker crashes, its job is reassigned by the leader
  4. If the leader crashes, in-progress jobs continue
     New leader resumes scanning/assigning

Worker failure handling:
  Leader tracks: {job_id: 123, worker: "W2", started_at: T1, timeout: 300s}
  If now() - started_at > timeout → job is stuck → reassign to another worker
  
  Worker heartbeats: W2 → Redis: SET worker:W2:heartbeat {timestamp} EX 60
  If heartbeat expires → leader marks W2's jobs as available for reassignment
```

### Job Queue Design

```
Leader writes to Redis queue:
  LPUSH job_queue '{"job_id": 123, "type": "send_email", ...}'
  LPUSH job_queue '{"job_id": 124, "type": "process_payment", ...}'

Workers consume from queue:
  job = BRPOPLPUSH job_queue processing_queue 30
  // Atomically: Move job from job_queue to processing_queue
  // If worker crashes, job is still in processing_queue (not lost)
  
  process(job)
  LREM processing_queue 1 job  // Remove from processing after completion
  
  // Leader periodically scans processing_queue for stuck jobs
  // If job has been in processing_queue > timeout → move back to job_queue
```

---

## Real-World System Examples

### Kafka — Consumer Group Coordinator

```
Each consumer group has ONE coordinator (leader election within Kafka brokers):

Coordinator responsibilities:
  - Track which consumers are in the group
  - Assign partitions to consumers (rebalancing)
  - Detect consumer failures (heartbeat timeout)
  - Trigger rebalance when consumers join/leave

If coordinator broker fails:
  - Kafka elects new coordinator from remaining brokers
  - New coordinator reads group metadata from __consumer_offsets topic
  - Triggers rebalance to reassign partitions
  - Failover: ~30 seconds
```

### Kubernetes — etcd Leader for Control Plane

```
Kubernetes control plane uses etcd for:
  - Leader election among kube-controller-manager instances
  - Leader election among kube-scheduler instances
  - Storing cluster state (pods, services, configs)

Only ONE controller-manager and ONE scheduler are active at a time.
Others are standby. etcd lease-based leadership with 15-second TTL.

If active controller-manager crashes:
  etcd lease expires → standby acquires lease → becomes active
  Failover: ~15 seconds
```

### Apache Flink — JobManager Leader Election

```
Flink JobManager (coordinator for stream processing):
  One active JobManager, one+ standby
  Leader election via ZooKeeper
  
  Active JobManager: Manages checkpoints, triggers savepoints, assigns tasks
  Standby: Watches ZK, ready to take over
  
  On failover:
    New JobManager reads latest checkpoint from HDFS/S3
    Resumes processing from last successful checkpoint
    No data loss (exactly-once semantics preserved)
```

---

## Deep Dive: Applying Leader Election to Popular Problems

### Job Scheduler

```
Leader election for a distributed job scheduler:

Leader responsibilities:
  1. Scan job_schedule table every 1 second
  2. Find due jobs: WHERE next_run_at <= NOW() AND status = 'PENDING'
  3. For each due job: Create job execution record + push to work queue
  4. Monitor execution: Timeout handling, retry logic, failure alerting

Implementation:
  ZooKeeper: Ephemeral sequential znode under /job-scheduler/election
  Session timeout: 30 seconds
  Fencing token: ZK znode version number

  // Leader loop
  while (isLeader()) {
      List<Job> dueJobs = db.query("SELECT * FROM jobs WHERE next_run_at <= NOW()");
      for (Job job : dueJobs) {
          db.execute("UPDATE jobs SET status = 'ASSIGNED', fencing_token = ? WHERE id = ?", 
                     myToken, job.id);
          redis.lpush("job_queue", serialize(job));
      }
      Thread.sleep(1000);
  }

Why only one leader should scan:
  If 3 nodes all scan simultaneously:
    All 3 find job 123 as "due"
    All 3 assign job 123
    Job 123 runs 3 times → triplicate email, triple charge
  
  With leader: Only one node scans → job runs exactly once
```

### Configuration Distributor

```
Leader election for configuration management:

Problem: Feature flags, routing tables, rate limit configs must be
  consistent across all application instances.

Leader responsibilities:
  1. Watch config source (database, config service)
  2. Detect config changes
  3. Broadcast changes to all application instances
  4. Verify all instances applied the change

Implementation:
  Leader watches config source → detects change
  Leader publishes to Redis Pub/Sub channel "config-updates"
  All app instances subscribe to "config-updates"
  On receiving update: Apply config, ACK to leader
  Leader tracks: {config_version: 42, acked_instances: 47/50}
  If 3 instances don't ACK within 30 seconds → alert on-call

Why leader:
  Without leader: Each instance polls config source → thundering herd
  With leader: One poll, one broadcast → efficient
```

### Database Failover Coordinator

```
Leader election for database HA:

Scenario: PostgreSQL primary + 2 replicas

Sentinel (leader) responsibilities:
  1. Monitor primary health (heartbeat every 5 seconds)
  2. If primary fails: Promote one replica to primary
  3. Reconfigure other replicas to follow new primary
  4. Update connection string in application config

Redis Sentinel is a real-world implementation:
  3-5 Sentinel instances, each monitoring the Redis primary
  Majority vote required to declare primary as failed (avoid false positives)
  Leader Sentinel performs the actual failover (promote + reconfigure)
```

---

## Interview Talking Points & Scripts

### Job Scheduler Leader

> *"For the job scheduler, I'd use ZooKeeper for leader election. Each scheduler node creates an ephemeral sequential znode under /election. The node with the lowest sequence number becomes the leader and is responsible for scanning the job queue and assigning work. If the leader crashes, ZooKeeper's session timeout (30 seconds) detects the failure, deletes the ephemeral znode, and the next candidate automatically becomes leader. Workers are completely stateless — they pull from a Redis queue. Losing the leader doesn't stop in-progress work; it only pauses new job assignment for ~30 seconds."*

### Fencing Tokens

> *"The critical risk in leader election is split-brain — a stale leader continuing to act after being replaced. I'd use fencing tokens to prevent this. Each lock acquisition generates a monotonically increasing token. The storage layer rejects any write with a token lower than the last seen token. So when stale Leader A (token=33) tries to write after new Leader B (token=34), the write is rejected. This guarantees that even if a leader 'wakes up' after a GC pause, it can't corrupt data."*

### Redis vs ZooKeeper

> *"I'd use ZooKeeper for critical leader election — like the job scheduler where duplicate execution causes real harm. ZooKeeper provides strong consistency via ZAB consensus. For non-critical coordination like cache warming, I'd use Redis SETNX — it's simpler to operate, and if two nodes briefly warm the cache simultaneously, no data is lost. The choice depends on the consequence of split-brain: if it's a nuisance, use Redis; if it's a data integrity issue, use ZooKeeper."*

### Worker Architecture

> *"The leader pattern works with stateless workers. The leader scans the job queue, assigns work to a Redis queue. Workers are interchangeable — they pull from the queue, process the job, and report completion. If a worker crashes, the leader detects the timeout and reassigns the job. If the leader crashes, in-progress workers continue unaffected. Only new job assignment pauses for 30 seconds until a new leader is elected. This separation of concerns makes the system highly resilient."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not mentioning fencing tokens
**Why it's wrong**: Split-brain is the #1 risk in leader election. Without fencing, a stale leader can corrupt data after being replaced.
**Better**: Always mention fencing tokens and how the storage layer enforces them.

### ❌ Mistake 2: Using Redis for critical leader election
**Why it's wrong**: Redis's async replication means a brief window where two nodes can both think they're the leader. For critical operations, this causes data corruption.
**Better**: Use ZooKeeper or etcd for critical coordination. Redis only for non-critical/idempotent tasks.

### ❌ Mistake 3: Not explaining what happens during the failover gap
**Why it's wrong**: "30 seconds with no leader" sounds scary. Explaining the actual impact shows maturity.
**Better**: "In-progress work continues unaffected. Only new job assignment pauses. Jobs queue up and are processed immediately once the new leader takes over. Total impact: ~30 seconds of delayed new assignments."

### ❌ Mistake 4: Forgetting that workers should be stateless
**Why it's wrong**: If workers have state, leader failover requires complex state transfer.
**Better**: Workers are stateless — they pull from a queue. State lives in the database/queue. Workers are interchangeable.

### ❌ Mistake 5: Not distinguishing distributed locks from leader election
**Why it's wrong**: They solve different problems. Locks protect resources; leaders coordinate ongoing work.
**Better**: "I'd use a distributed lock for booking the specific seat (per-resource, seconds). I'd use leader election for the job scheduler (per-system, continuous)."

### ❌ Mistake 6: Not mentioning the session timeout tradeoff
**Why it's wrong**: Session timeout is a key design parameter that balances failover speed vs stability.
**Better**: "I'd set the ZK session timeout to 30 seconds — short enough for quick failover, long enough to tolerate brief network blips without unnecessary leader changes."

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│        LEADER ELECTION & COORDINATION CHEAT SHEET            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PURPOSE: Exactly ONE leader for coordination tasks          │
│  Use: Job scheduler, partition assignment, singleton process │
│                                                              │
│  TOOLS:                                                      │
│    ZooKeeper: Ephemeral znodes, session timeout (30s)        │
│      Best for: Critical coordination (strong consistency)    │
│    etcd: Raft consensus, leader lease                        │
│      Best for: Kubernetes ecosystem, modern systems          │
│    Redis: SETNX (simple, NOT strongly consistent)            │
│      Best for: Non-critical, idempotent tasks                │
│                                                              │
│  FENCING TOKENS:                                             │
│    Monotonically increasing token per election               │
│    Storage rejects writes from stale leaders (lower tokens)  │
│    Prevents split-brain even with GC pauses/network issues   │
│    MANDATORY for any critical leader election                │
│                                                              │
│  LEASE MANAGEMENT:                                           │
│    Acquire lease (TTL=15s), renew every TTL/3 (5s)           │
│    3 missed renewals → lease expires → new leader elected    │
│    Always check remaining lease before starting new work     │
│                                                              │
│  FAILOVER: 15-45 seconds (detection + election)              │
│    In-progress work: Unaffected (stateless workers continue) │
│    New work: Queued, processed when new leader starts        │
│                                                              │
│  WORKER ARCHITECTURE:                                        │
│    Leader: Scans, assigns, monitors, handles timeouts        │
│    Workers: Stateless, pull from queue, process, report      │
│    Separation of concerns: Leader coordinates, workers execute│
│                                                              │
│  SESSION TIMEOUT TRADEOFF:                                   │
│    Too short (5s): False positives, leader flapping          │
│    Too long (60s): Slow failover, long gap                   │
│    Sweet spot: 15-30 seconds for most applications           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 1: CAP Theorem** — CP choice for leader election (consistency over availability)
- **Topic 21: Concurrency Control** — Distributed locks as fine-grained coordination
- **Topic 36: Distributed Transactions & Sagas** — Coordinator pattern in sagas
- **Topic 40: Job Scheduling** — Leader-based job scheduling architecture
- **Topic 28: Availability & Disaster Recovery** — Failover strategies

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
