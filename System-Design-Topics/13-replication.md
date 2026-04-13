# 🎯 Topic 13: Replication

> **System Design Interview — Deep Dive**
> A comprehensive guide covering single-leader, multi-leader, and leaderless replication — replication lag, read-your-writes, quorum configurations, conflict resolution, and how to articulate replication decisions in interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Replicate](#why-replicate)
3. [Single-Leader (Master-Slave) Replication](#single-leader-master-slave-replication)
4. [Multi-Leader Replication](#multi-leader-replication)
5. [Leaderless Replication](#leaderless-replication)
6. [Synchronous vs Asynchronous Replication](#synchronous-vs-asynchronous-replication)
7. [Replication Lag and Its Consequences](#replication-lag-and-its-consequences)
8. [Handling Replication Lag — Read-Your-Writes](#handling-replication-lag--read-your-writes)
9. [Quorum Reads and Writes (W + R > N)](#quorum-reads-and-writes-w--r--n)
10. [Conflict Resolution in Multi-Leader/Leaderless](#conflict-resolution-in-multi-leaderleaderless)
11. [Replication Factor Selection](#replication-factor-selection)
12. [Failover — What Happens When the Leader Dies](#failover--what-happens-when-the-leader-dies)
13. [Replication in Popular Databases](#replication-in-popular-databases)
14. [Multi-Region Replication](#multi-region-replication)
15. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
16. [Common Interview Mistakes](#common-interview-mistakes)
17. [Replication Strategy by System Design Problem](#replication-strategy-by-system-design-problem)
18. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Replication** maintains copies of data on multiple machines for:
1. **High availability**: If one node dies, others serve traffic.
2. **Read scaling**: Distribute reads across replicas.
3. **Low latency**: Place replicas near users geographically.

The core tradeoff: **stronger consistency guarantees require more coordination, which increases latency and reduces throughput**.

---

## Why Replicate

| Goal | How Replication Helps |
|---|---|
| **High availability** | Node failure → other replicas serve traffic (no downtime) |
| **Read throughput** | N replicas = ~N× read capacity |
| **Disaster recovery** | Region failure → other region has full copy |
| **Low latency** | Read from nearest replica (same city vs cross-continent) |
| **Durability** | Data survives hardware failure (disk, machine, rack, DC) |

---

## Single-Leader (Master-Slave) Replication

### How It Works
```
All writes → Leader (Master)
Leader replicates → Follower 1, Follower 2 (async or sync)
Reads can go to Leader or any Follower

┌──────────┐    writes     ┌──────────┐
│  Client   │ ───────────→ │  Leader   │
│           │              │ (Master)  │
│           │ ←── reads ── │           │
└──────────┘              └─────┬─────┘
                                │ replication
                    ┌───────────┼───────────┐
                    ▼           ▼           ▼
              ┌──────────┐ ┌──────────┐ ┌──────────┐
              │Follower 1│ │Follower 2│ │Follower 3│
              │ (reads)  │ │ (reads)  │ │ (reads)  │
              └──────────┘ └──────────┘ └──────────┘
```

### Advantages
- **Simple**: Clear write path (one leader), clear read path (any node).
- **No write conflicts**: Only one node accepts writes.
- **Strong consistency possible**: Read from leader = always latest data.
- **Mature**: Built into PostgreSQL, MySQL, MongoDB, Redis.

### Disadvantages
- **Write bottleneck**: All writes go through one node.
- **Single point of failure for writes**: Leader down = no writes until failover.
- **Replication lag**: Followers may be behind the leader.
- **Cross-region latency**: If leader is in US, EU writes have 150ms latency.

### Performance
```
Leader handles: ~50K writes/sec (PostgreSQL) to ~100K writes/sec (Redis)
Each follower adds: ~50K reads/sec
3 followers: ~200K total read capacity

Replication lag:
  Same region: 1-50ms (typical: ~10ms)
  Cross-region: 100-500ms
```

### Interview Script
> *"I'd use single-leader replication with two read replicas. All writes go to the master (which handles our 5K writes/sec comfortably), and reads are distributed across the master and two replicas for 3x read throughput. The replication lag averages 50ms, occasionally spiking to 500ms. For read-your-writes consistency, I'd route the writing user's subsequent reads to the master for 5 seconds after any write."*

---

## Multi-Leader Replication

### How It Works
```
Each region has its own leader.
Leaders replicate to each other asynchronously.
Writes go to the nearest leader (low latency).

US Region:                    EU Region:
┌──────────┐                ┌──────────┐
│ Leader A │ ←── async ───→ │ Leader B │
│ (US)     │  replication   │ (EU)     │
│          │                │          │
│Followers │                │Followers │
└──────────┘                └──────────┘

User in Paris writes to Leader B (10ms)
  instead of Leader A (150ms cross-Atlantic)
```

### When to Use
- **Multi-region active-active**: Both regions accept writes.
- **Offline-capable applications**: Devices that sync when back online.
- **Collaborative editing**: Multiple users editing same document simultaneously.

### The Conflict Problem
```
Time T1: User A in US updates name = "Alice" → Leader A
Time T2: User B in EU updates name = "Alicia" → Leader B
Time T3: Leaders sync → CONFLICT! Which name wins?
```

### Conflict Resolution Strategies
| Strategy | How | Tradeoff |
|---|---|---|
| **Last-Writer-Wins (LWW)** | Highest timestamp wins | Simple but data loss risk (clock skew) |
| **Custom merge** | Application logic merges conflicting values | Complex but correct |
| **CRDTs** | Data structures that auto-converge | Limited to specific data types |
| **User resolution** | Present both versions to user | Best UX, most complex |

### Interview Script
> *"Multi-leader replication is necessary for our active-active multi-region deployment. Users in Europe should write to the EU master with 10ms latency, not route to the US master with 150ms latency. The conflict resolution strategy depends on the data type: last-writer-wins for simple fields like display name, and CRDTs for counters like follower counts (both increments should be preserved, not overwritten)."*

---

## Leaderless Replication

### How It Works
```
No leader. Any node accepts writes.
Client writes to multiple nodes simultaneously.
Client reads from multiple nodes simultaneously.

Write: Client sends write to N nodes, waits for W acknowledgments.
Read: Client reads from N nodes, takes the newest value from R responses.

┌──────────┐     write to all      ┌──────────┐
│  Client   │ ───────────────────→ │  Node A  │ ✓ (ACK)
│           │ ───────────────────→ │  Node B  │ ✓ (ACK)
│           │ ───────────────────→ │  Node C  │ ✗ (temp. unavailable)
│           │                      └──────────┘
│  W=2 ACKs │ → Write successful (2 of 3 ACKed)
└──────────┘
```

### Quorum Formula
```
N = total replicas
W = write quorum (minimum ACKs for write success)
R = read quorum (minimum responses for read)

If W + R > N → guaranteed to read latest write
  Because at least one node in the read quorum has the latest write.

Common: N=3, W=2, R=2 → W+R=4 > N=3 → Strong consistency
```

### Examples
- **Cassandra**: Leaderless, tunable consistency (ONE, QUORUM, ALL).
- **DynamoDB**: Leaderless with quorum-like behavior.
- **Riak**: Leaderless with CRDTs.

### Advantages
- **No single point of failure**: Any node can serve reads and writes.
- **High availability**: Tolerates node failures without failover delay.
- **No write bottleneck**: Writes distributed across all nodes.
- **Tunable consistency**: Choose W and R per operation.

### Disadvantages
- **Conflict resolution needed**: Concurrent writes to same key → conflicts.
- **Complexity**: Application must handle read repair, anti-entropy.
- **No global ordering**: Operations on different nodes may be seen in different orders.

### Interview Script
> *"I'd set replication factor to 3 with quorum reads and writes (W=2, R=2, N=3). This means every write must succeed on at least 2 of 3 replicas before ACK, and every read contacts at least 2 of 3 replicas and returns the newest value. Since W + R (4) > N (3), we're guaranteed to read our own writes. The system tolerates 1 node failure for both reads and writes. It's the standard balance."*

---

## Synchronous vs Asynchronous Replication

### Synchronous
```
Client → Leader → write to disk → replicate to follower → follower ACKs → Leader ACKs client

Guarantee: If leader ACKs, data is on at least 2 nodes.
Cost: Higher write latency (wait for follower ACK).
Risk: If follower is slow/down, writes are blocked.
```

### Asynchronous (Most Common)
```
Client → Leader → write to disk → ACK to client → replicate to follower later

Guarantee: Data is on leader only when ACKed. Followers catch up later.
Cost: Lower write latency (don't wait for follower).
Risk: If leader dies before replication, data is LOST.
```

### Semi-Synchronous (Practical Compromise)
```
Client → Leader → write to disk → replicate to ONE follower (sync) → ACK
  → replicate to other followers (async)

Guarantee: Data is on at least 2 nodes when ACKed.
Cost: Moderate latency (wait for fastest follower).
Risk: If the sync follower is down, another is promoted to sync role.
```

### Which to Use
| Scenario | Replication Type | Why |
|---|---|---|
| **Financial transactions** | Synchronous | Cannot lose data |
| **Social media posts** | Asynchronous | Slight data loss risk is acceptable |
| **Session data** | Asynchronous | Can be regenerated |
| **Configuration/flags** | Semi-synchronous | Must be durable but fast |

---

## Replication Lag and Its Consequences

### What Is Replication Lag?
```
Leader writes value X at time T.
Follower receives X at time T + lag.

Typical lag:
  Same rack:    < 1ms
  Same region:  1-50ms (typical: 10ms)
  Cross-region: 100-500ms
  Under load:   Can spike to seconds or minutes
```

### Consequences of Lag

**1. Stale reads**: User reads from follower, sees old data.
```
User updates profile → Leader has new data
User refreshes page → Read hits follower → sees OLD profile
```

**2. Non-monotonic reads**: User goes "back in time."
```
Read 1 → Follower A (up-to-date) → "Order: Shipped"
Read 2 → Follower B (behind) → "Order: Processing" ← went backwards!
```

**3. Causality violations**: Reply appears before the original message.
```
Alice posts "Let's meet at 3pm" → replicated to Follower A
Bob replies "Sounds good!" → replicated to Follower B
Reader sees Bob's reply BEFORE Alice's message (if Follower B is ahead)
```

---

## Handling Replication Lag — Read-Your-Writes

### Strategy 1: Read from Leader After Write
```python
def read_profile(user_id, requesting_user_id):
    if requesting_user_id == user_id:
        # User reading their OWN profile → route to leader
        if time_since_last_write(user_id) < 5:  # seconds
            return leader.query(user_id)
    
    # Reading someone else's profile → any replica is fine
    return any_replica.query(user_id)
```

### Strategy 2: Monotonic Reads via Session Stickiness
```
User's session → always routed to same follower.
Within that follower, reads are always monotonic.
If follower fails → reassign to another (accept brief staleness).
```

### Strategy 3: Causal Consistency via Timestamps
```
Write response includes: { version: 42, timestamp: T }
Client includes in next read: { min_version: 42 }
Server: If local replica version < 42, wait or redirect to leader.
```

---

## Quorum Reads and Writes (W + R > N)

### Configurations and Their Tradeoffs

| Config | W | R | N | Fault Tolerance | Read Speed | Write Speed | Use Case |
|---|---|---|---|---|---|---|---|
| Strong | 2 | 2 | 3 | 1 node failure | Medium | Medium | Standard (DEFAULT) |
| Write-heavy | 1 | 3 | 3 | Writes: 2 failures, Reads: 0 | Slow | Fast | Logging, events |
| Read-heavy | 3 | 1 | 3 | Writes: 0, Reads: 2 failures | Fast | Slow | Config, flags |
| Eventual (AP) | 1 | 1 | 3 | Read+Write: 2 failures each | Fastest | Fastest | Feeds, analytics |
| Maximum safety | 3 | 3 | 3 | 0 failures | Slowest | Slowest | Rarely used |

### Sloppy Quorum (Cassandra)
When the required quorum nodes are unreachable, accept writes on ANY available node (even if it's not one of the designated replicas). Later, "hinted handoff" forwards the data to the correct nodes.

```
Normal: Write to nodes A, B, C (designated replicas)
Node C down: Write to nodes A, B, D (D is a temporary stand-in)
When C recovers: D sends the data to C (hinted handoff)
```

---

## Conflict Resolution in Multi-Leader/Leaderless

### Last-Writer-Wins (LWW)
```
Timestamp comparison: highest timestamp wins.
  Update A at T=100: name = "Alice"
  Update B at T=101: name = "Alicia"
  Winner: "Alicia" (T=101 > T=100)

Problem: Clock skew. If Server A's clock is 2 seconds ahead,
  its writes always win even if Server B wrote later in real time.
```

### Merge Functions
```
For counters: sum both values
  Node A: counter = 5 (was 3, +2)
  Node B: counter = 7 (was 3, +4)
  Merged: counter = 3 + 2 + 4 = 9 (not max(5,7)=7, which loses +2)

For sets: union
  Node A: tags = {fun, music}
  Node B: tags = {fun, travel}
  Merged: tags = {fun, music, travel}
```

### CRDTs (See Topic 4)
Mathematically guaranteed convergence. Best for counters, sets, registers.

---

## Replication Factor Selection

| RF | Durability | Availability | Cost | Use Case |
|---|---|---|---|---|
| 1 | Low (1 copy) | None (no redundancy) | Cheapest | Development only |
| 2 | Medium | Survives 1 failure | 2x storage | Non-critical data |
| **3** | **High** | **Survives 1 failure + maintenance** | **3x storage** | **Production standard** |
| 5 | Very high | Survives 2 failures | 5x storage | Critical financial data |

### Why RF=3 Is Standard
```
With RF=3 and W=2, R=2:
  - Can lose 1 node and still read AND write (quorum met)
  - Can take 1 node offline for maintenance while surviving 1 failure
  - Storage cost: 3x, which is manageable
  - More durable than RF=2 (can survive coincidental double failure)
```

---

## Failover — What Happens When the Leader Dies

### Automatic Failover Process
```
1. Health check detects leader failure (timeout: 10-30 seconds)
2. Election: Followers elect a new leader
   - Most up-to-date follower is preferred
   - Consensus protocol (Raft, ZAB) ensures single leader
3. Reconfiguration: Clients redirect writes to new leader
4. Old leader recovers → becomes a follower

Total failover time: 10-45 seconds (depending on detection + election)
```

### Failover Dangers
**Split-brain**: Old leader comes back thinking it's still leader → two leaders accepting writes → data divergence.

**Solution**: Fencing tokens. Each leader has a monotonically increasing epoch number. Storage rejects writes from older epochs.

**Data loss risk**: If async replication, new leader may be missing the last few writes from old leader. Those writes are lost.

---

## Replication in Popular Databases

| Database | Replication Type | Default RF | Notes |
|---|---|---|---|
| **PostgreSQL** | Single-leader | 1 (manual setup) | Streaming replication, sync/async |
| **MySQL** | Single-leader | 1 (manual setup) | Row-based or statement-based |
| **Cassandra** | Leaderless | 3 | Tunable consistency per query |
| **DynamoDB** | Leaderless (managed) | 3 | Auto-managed, cross-AZ |
| **MongoDB** | Single-leader (replica set) | 3 | Automatic failover |
| **Redis** | Single-leader + Sentinel | 1-3 | Async replication |
| **CockroachDB** | Raft consensus (multi-leader-like) | 3 | Strongly consistent |

---

## Multi-Region Replication

### Active-Passive
```
US-East: Leader (all writes) + Followers (reads)
EU-West: Followers only (reads)

US users: Low-latency writes (same region as leader)
EU users: Low-latency reads, but writes go cross-Atlantic (150ms)
Failover: Promote EU follower to leader if US-East fails
```

### Active-Active (Multi-Leader)
```
US-East: Leader A (accepts writes) + Followers
EU-West: Leader B (accepts writes) + Followers

US users: Low-latency writes to Leader A
EU users: Low-latency writes to Leader B
Leaders sync asynchronously (100-300ms lag)
Conflict resolution needed for concurrent writes to same data
```

### Design Considerations
```
Data sovereignty: EU user data MUST stay in EU region (GDPR).
  → Geographic sharding: EU users → eu-west, US users → us-east.
  → Only public data (posts, public profiles) replicated cross-region.
  → Private data (DMs, email, phone) never leaves home region.
```

---

## Interview Talking Points & Scripts

### Single-Leader with Read Replicas
> *"I'd use single-leader replication with two read replicas. All writes go to the master, reads distributed across replicas for 3x read throughput. For read-your-writes, I'd route the writing user's reads to the master for 5 seconds after any write."*

### Quorum Configuration
> *"I'd set RF=3 with W=2, R=2. Since W+R (4) > N (3), we get strong consistency. The system tolerates 1 node failure for both reads and writes."*

### Multi-Region Active-Active
> *"Multi-leader replication for active-active across US and EU. Writes go to the local leader (10ms vs 150ms cross-region). Async replication between leaders with LWW for simple fields and CRDTs for counters."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not specifying sync vs async replication
### ❌ Mistake 2: Forgetting replication lag consequences
### ❌ Mistake 3: Not mentioning failover mechanism
### ❌ Mistake 4: Using RF=1 in production designs
### ❌ Mistake 5: Multi-leader without conflict resolution strategy

---

## Replication Strategy by System Design Problem

| Problem | Replication Type | RF | Notes |
|---|---|---|---|
| **Twitter** | Single-leader per shard | 3 | Read replicas for timeline reads |
| **WhatsApp** | Cassandra (leaderless) | 3 | W=2, R=2 for message durability |
| **Payment System** | Single-leader, sync replication | 3 | Cannot lose transactions |
| **Analytics** | Cassandra (leaderless) | 3 | W=1, R=1 for speed |
| **Multi-Region App** | Multi-leader | 3/region | Active-active with conflict resolution |
| **Cache (Redis)** | Single-leader + Sentinel | 2 | Async; cache is rebuildable |
| **Config Store (etcd)** | Raft consensus | 3-5 | Strong consistency required |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                  REPLICATION CHEAT SHEET                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  TYPES:                                                      │
│  Single-Leader: All writes → one node → replicate to followers│
│  Multi-Leader:  Multiple nodes accept writes → sync between  │
│  Leaderless:    Any node accepts writes → quorum consensus   │
│                                                              │
│  STANDARD CONFIG: RF=3, W=2, R=2                             │
│    W+R > N → strong consistency                              │
│    Tolerates 1 node failure for reads AND writes             │
│                                                              │
│  SYNC vs ASYNC:                                              │
│    Sync: No data loss, higher latency                        │
│    Async: Possible data loss, lower latency (MOST COMMON)   │
│    Semi-sync: One sync follower, rest async (BEST BALANCE)   │
│                                                              │
│  REPLICATION LAG HANDLING:                                   │
│    Read-your-writes: Route to leader for 5s after write      │
│    Monotonic reads: Session stickiness to same replica       │
│    Causal ordering: Version-based read routing               │
│                                                              │
│  FAILOVER:                                                   │
│    Detection: 10-30s health check timeout                    │
│    Election: Most up-to-date follower promoted               │
│    Total: 10-45 seconds                                      │
│    Risk: Split-brain (use fencing tokens)                    │
│                                                              │
│  MULTI-REGION:                                               │
│    Active-passive: One region writes, others read            │
│    Active-active: Both regions write, conflict resolution    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
