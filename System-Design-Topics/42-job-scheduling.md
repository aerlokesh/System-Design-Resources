# 🎯 Topic 40: Job Scheduling

> **System Design Interview — Deep Dive**
> Scheduler architecture with leader election, Redis sorted sets for due jobs, visibility timeout for fault tolerance, and idempotent job execution.

---

## Architecture

```
┌──────────────┐     ┌────────────────────┐     ┌──────────────┐
│  Scheduler   │────→│ Redis Sorted Set   │────→│  Workers     │
│  Leader      │     │ (pending jobs)     │     │ (stateless)  │
│ (via ZK)     │     │ Score = due_time   │     │              │
└──────────────┘     └────────────────────┘     └──────────────┘

Scheduler Leader:
  Every 1 second: ZRANGEBYSCORE jobs:pending 0 {now}
  → Returns all jobs whose scheduled time has passed
  → Moves each to processing queue

Workers:
  Pull from processing queue → execute → ACK
  If worker dies: Visibility timeout → job reappears → another worker picks up
```

## Key Design Points

### Leader Election
```
ZooKeeper elects ONE scheduler leader.
Only the leader scans for due jobs.
If leader crashes: New leader elected in 30-45 seconds.
Workers are stateless — they continue in-progress work.
```

### Visibility Timeout (Fault Tolerance)
```
Worker picks job → job hidden for 5 minutes (expected max execution time)
Worker completes → deletes job from queue
Worker crashes → after 5 minutes, job becomes visible again → picked by another worker

This is exactly how SQS visibility timeout works.
```

### Idempotent Execution
```
Every job must be idempotent. If a worker crashes after executing but before ACK:
  Job reappears → another worker executes it AGAIN
  
  "Send monthly billing email":
    Before sending: SELECT sent FROM billing_emails WHERE user_id=? AND month='2025-03'
    If already sent → ACK without re-sending
    If not sent → send → mark as sent → ACK
```

## Interview Script
> *"The scheduler has three components. The leader (via ZooKeeper) scans a Redis sorted set every second for due jobs. Stateless workers pull from a processing queue. If a worker dies mid-execution, the visibility timeout ensures the job reappears. Every job is idempotent — duplicate execution is harmless."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               JOB SCHEDULING CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│  Leader: Scans Redis sorted set (ZRANGEBYSCORE 0 {now})      │
│  Workers: Stateless, pull from queue, ACK on completion      │
│  Fault tolerance: Visibility timeout (5 min) → auto-retry    │
│  Idempotency: Every job safe to re-execute                   │
│  Leader election: ZooKeeper (30-45s failover)                │
│  Scheduling: Redis sorted set, score = due_timestamp         │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
