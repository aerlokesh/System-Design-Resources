# 🎯 Topic 32: Leader Election & Coordination

> **System Design Interview — Deep Dive**
> ZooKeeper-based leader election, fencing tokens to prevent split-brain, and distributed coordination for job schedulers and singleton processes.

---

## Table of Contents

- [🎯 Topic 32: Leader Election \& Coordination](#-topic-32-leader-election--coordination)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Why Leader Election](#why-leader-election)
  - [ZooKeeper for Leader Election](#zookeeper-for-leader-election)
    - [How It Works](#how-it-works)
    - [ZooKeeper Session Timeout](#zookeeper-session-timeout)
    - [Interview Script](#interview-script)
  - [Fencing Tokens — Preventing Split-Brain](#fencing-tokens--preventing-split-brain)
    - [The Problem](#the-problem)
    - [The Solution: Fencing Tokens](#the-solution-fencing-tokens)
    - [Interview Script](#interview-script-1)
  - [Redis-Based Leader Election](#redis-based-leader-election)
    - [Simple Redis Lock](#simple-redis-lock)
    - [Limitations](#limitations)
  - [etcd / Raft Consensus](#etcd--raft-consensus)
    - [How It Works](#how-it-works-1)
  - [Leader Election Failure Modes](#leader-election-failure-modes)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [Job Scheduler Leader](#job-scheduler-leader)
    - [Fencing Tokens](#fencing-tokens)
  - [Common Interview Mistakes](#common-interview-mistakes)
    - [❌ Not mentioning fencing tokens (split-brain is the #1 risk)](#-not-mentioning-fencing-tokens-split-brain-is-the-1-risk)
    - [❌ Using Redis for critical leader election (not strongly consistent)](#-using-redis-for-critical-leader-election-not-strongly-consistent)
    - [❌ Not explaining what happens during the failover gap](#-not-explaining-what-happens-during-the-failover-gap)
    - [❌ Forgetting that workers should be stateless](#-forgetting-that-workers-should-be-stateless)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Leader election** ensures exactly ONE node is the designated leader at any given time. The leader performs coordination tasks (job scheduling, partition assignment) while other nodes are standby. When the leader fails, a new leader is automatically elected.

```
Without leader election:
  Worker A scans job queue → assigns job 123 to Worker X
  Worker B scans job queue → assigns job 123 to Worker Y
  → Job 123 executed TWICE! (duplicate email, double charge)

With leader election:
  Leader (Worker A) scans job queue → assigns jobs to workers
  Workers B, C, D: Only process assigned jobs, don't scan
  If Leader A crashes → new leader elected (Worker B) → resumes scanning
```

---

## Why Leader Election

| Use Case | Why One Leader? |
|---|---|
| **Job scheduler** | Only one node should scan for due jobs |
| **Partition assignment** | Only one node decides which consumer gets which partition |
| **Database master** | Only one node accepts writes |
| **Cron jobs** | Scheduled task should run once, not N times |
| **Cache warming** | Only one node should rebuild the cache on startup |
| **Migration runner** | Only one node should run schema migrations |

---

## ZooKeeper for Leader Election

### How It Works
```
1. All candidates create ephemeral sequential znodes:
   /election/leader-0001  (Node A)
   /election/leader-0002  (Node B)
   /election/leader-0003  (Node C)

2. The node with the LOWEST sequence number is the leader:
   Node A (leader-0001) → LEADER
   Node B, C → STANDBY

3. If leader crashes:
   Ephemeral znode /election/leader-0001 is automatically deleted
   (ZooKeeper detects session timeout after 30 seconds)

4. Node B (leader-0002) has the next lowest → becomes LEADER
   Re-election time: 30-45 seconds
```

### ZooKeeper Session Timeout
```
Default: 30 seconds
If leader's heartbeat stops → ZK waits 30 seconds → deletes ephemeral znode
New leader: Next candidate in sequence → 30-45 seconds total failover

During the gap:
  No leader → no new job assignments
  In-progress jobs: Unaffected (workers continue processing)
  New jobs: Wait in queue until new leader starts scanning
```

### Interview Script
> *"For the job scheduler, I'd use ZooKeeper for leader election. Only the elected leader scans the job queue and assigns work to workers. If the leader crashes, ZooKeeper's session timeout (30 seconds) triggers automatic re-election. Workers are completely stateless — they pull jobs from a Redis queue. Losing the leader doesn't stop in-progress work; it only pauses new job assignment for ~30 seconds."*

---

## Fencing Tokens — Preventing Split-Brain

### The Problem
```
Time T1: Leader A acquires lock (token = 5), starts processing
Time T2: Leader A becomes slow (GC pause, network hiccup)
Time T3: Lock expires (TTL reached) — ZK thinks A is dead
Time T4: Leader B elected (token = 6), starts processing
Time T5: Leader A "wakes up" — still thinks it's the leader!

Now BOTH A and B think they're the leader → SPLIT-BRAIN
A writes result with stale state → data corruption
```

### The Solution: Fencing Tokens
```
Each lock acquisition generates a monotonically increasing token:
  Leader A: token = 5
  Leader B: token = 6

Storage layer rule:
  Accept writes ONLY if token >= last_seen_token

Sequence:
  Leader B writes (token=6) → accepted, last_seen_token = 6
  Leader A writes (token=5) → REJECTED (5 < 6)

Stale leader's writes are automatically rejected.
No split-brain. No data corruption.
```

### Interview Script
> *"I'd use fencing tokens to prevent the stale-leader problem. Each lock acquisition generates a monotonically increasing token. The storage layer rejects writes with token ≤ last-seen-token. So when stale Leader A (token=5) tries to write after new Leader B (token=6), the write is rejected. No split-brain, no duplicate processing."*

---

## Redis-Based Leader Election

### Simple Redis Lock
```python
def try_become_leader(node_id, ttl=30):
    acquired = redis.set("leader", node_id, nx=True, ex=ttl)
    if acquired:
        # I'm the leader — start leader loop
        while True:
            do_leader_work()
            redis.expire("leader", ttl)  # Renew lease
            time.sleep(ttl / 3)          # Renew at 1/3 of TTL
    else:
        # Someone else is leader — wait
        time.sleep(ttl)
        try_become_leader(node_id, ttl)
```

### Limitations
```
Redis is NOT strongly consistent (async replication).
If Redis master fails and replica takes over:
  Old leader's key may be lost → two leaders briefly.

For critical systems: Use ZooKeeper or etcd (consensus-based).
For non-critical systems: Redis is simpler and usually sufficient.
```

---

## etcd / Raft Consensus

### How It Works
```
etcd uses Raft consensus:
  - One leader elected among etcd nodes (not your application nodes)
  - Writes go through Raft → majority agreement → committed
  - Leader lease: Your application gets a lease from etcd
  - If lease expires → another application node can acquire it

Stronger guarantees than Redis:
  ✅ Strongly consistent (Raft consensus)
  ✅ No split-brain (by design)
  ✅ Automatic failover within etcd cluster
  
Used by: Kubernetes (etcd is the backing store)
```

---

## Leader Election Failure Modes

| Failure | Impact | Mitigation |
|---|---|---|
| **Leader crash** | No leader for 30-45s | Standby auto-elected |
| **Network partition** | Old leader isolated, new one elected | Fencing tokens |
| **GC pause** | Leader appears dead temporarily | Long TTL + fencing |
| **ZK cluster failure** | No election possible | Multi-AZ ZK deployment |
| **Split-brain** | Two leaders, duplicate work | Fencing tokens + epoch |

---

## Interview Talking Points & Scripts

### Job Scheduler Leader
> *"ZooKeeper for leader election. Only the leader scans the job queue. 30-second failover on crash. Workers are stateless — they continue in-progress work. Only new job assignment pauses briefly."*

### Fencing Tokens
> *"Each leader gets a monotonically increasing token. Storage rejects writes from stale leaders (lower tokens). Prevents split-brain even if old leader 'wakes up' after being replaced."*

---

## Common Interview Mistakes

### ❌ Not mentioning fencing tokens (split-brain is the #1 risk)
### ❌ Using Redis for critical leader election (not strongly consistent)
### ❌ Not explaining what happens during the failover gap
### ❌ Forgetting that workers should be stateless

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
│    etcd: Raft consensus, leader lease, strongly consistent   │
│    Redis: SETNX (simple, not strongly consistent)            │
│                                                              │
│  FENCING TOKENS: Monotonically increasing token per election │
│    Storage rejects writes from stale leaders (lower tokens)  │
│    Prevents split-brain even with network partitions         │
│                                                              │
│  FAILOVER: 30-45 seconds (detection + election)              │
│    In-progress work: Unaffected                              │
│    New work: Paused until new leader elected                 │
│                                                              │
│  WORKERS: Stateless — pull from queue, don't scan            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
