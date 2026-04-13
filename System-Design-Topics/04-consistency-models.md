# 🎯 Topic 4: Consistency Models

> **System Design Interview — Deep Dive**
> A comprehensive guide covering the full spectrum of consistency models — from eventual consistency to linearizability — with practical implementation details, real-world examples, and interview-ready articulation strategies.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [The Consistency Spectrum](#the-consistency-spectrum)
3. [Eventual Consistency](#eventual-consistency)
4. [Read-Your-Writes Consistency](#read-your-writes-consistency)
5. [Monotonic Reads](#monotonic-reads)
6. [Consistent Prefix (Causal Ordering)](#consistent-prefix-causal-ordering)
7. [Causal Consistency](#causal-consistency)
8. [Linearizability (Strong Consistency)](#linearizability-strong-consistency)
9. [CRDTs — Conflict-Free Replicated Data Types](#crdts--conflict-free-replicated-data-types)
10. [Quorum Reads & Writes](#quorum-reads--writes)
11. [Tunable Consistency](#tunable-consistency)
12. [Consistency in Multi-Region Deployments](#consistency-in-multi-region-deployments)
13. [Implementing Consistency Guarantees](#implementing-consistency-guarantees)
14. [Consistency vs Latency vs Throughput](#consistency-vs-latency-vs-throughput)
15. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
16. [Common Interview Mistakes](#common-interview-mistakes)
17. [Applying Consistency Models to System Design Problems](#applying-consistency-models-to-system-design-problems)
18. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Consistency in distributed systems is **not binary** — it's a rich spectrum. The stronger the consistency guarantee, the more coordination (network round-trips, locks, consensus protocols) is required, which means **higher latency and lower throughput**.

The key insight for interviews: **different operations within the same system need different consistency levels**. Over-specifying consistency wastes resources; under-specifying it causes bugs.

```
Weakest ◄──────────────────────────────────────────────► Strongest
                                                          
Eventual    Read-Your    Monotonic    Consistent    Causal    Linearizable
            -Writes      Reads        Prefix                  (Strong)
                                                          
Cheapest ◄─────────────────────────────────────────────► Most Expensive
Fastest  ◄─────────────────────────────────────────────► Slowest
```

---

## The Consistency Spectrum

| Level | Guarantee | Cost | Use Case |
|---|---|---|---|
| **Eventual** | All replicas converge given enough time | Lowest latency, highest throughput | DNS, CDN, like counters, analytics |
| **Read-Your-Writes** | A user sees their own writes immediately | Minimal extra cost (routing logic) | Profile edits, post creation |
| **Monotonic Reads** | Never see older data after seeing newer data | Session stickiness | Chat history, order tracking |
| **Consistent Prefix** | Writes seen in order they occurred | Causal ordering within partition | Conversation threads |
| **Causal** | Causally related ops ordered; concurrent ops unordered | Vector clocks / Lamport timestamps | Chat apps, collaborative tools |
| **Linearizable** | All ops appear atomic, in real-time order | Consensus protocol (Raft/Paxos) | Payments, bookings, leader election |

---

## Eventual Consistency

### Definition
Given no new updates, all replicas will **eventually** converge to the same value. There is no bound on how long "eventually" takes (in practice, usually milliseconds to seconds).

### How It Works
```
Time T1: Client writes value=100 to Node A
Time T2: Node A replicates to Node B (async) — takes 50ms
Time T3: Node A replicates to Node C (async) — takes 80ms

Between T1 and T3:
  - Read from Node A → 100 ✓
  - Read from Node B → may return old value (stale)
  - Read from Node C → may return old value (stale)

After T3: All nodes return 100 ✓
```

### Conflict Resolution
When two clients write different values to two different replicas simultaneously, you need a conflict resolution strategy:

| Strategy | How It Works | Pros | Cons |
|---|---|---|---|
| **Last-Writer-Wins (LWW)** | Highest timestamp wins | Simple | Clock skew can cause data loss |
| **Version Vectors** | Track causal history; detect conflicts | Accurate conflict detection | Complex; storage overhead |
| **Application-Level** | Return all conflicting versions; app decides | Maximum flexibility | Complexity in application code |
| **CRDTs** | Mathematically guaranteed convergence | Zero conflicts | Limited data types |

### When to Use
- **Like counters**: 4,998 vs 5,001 is invisible to users.
- **CDN caching**: Serving a slightly stale page is better than no page.
- **DNS**: Resolving to a slightly old IP is better than failing to resolve.
- **Analytics**: Approximate real-time numbers are fine for dashboards.
- **Social feeds**: A tweet appearing 3 seconds late is invisible.

### Interview Script
> *"For the like counter display, eventual consistency is perfect. Whether the user sees 4,998 or 5,001 likes is invisible — the difference is undetectable by humans. We'd store the count in Cassandra with CL=ONE for both reads and writes, giving us sub-5ms latency and maximum throughput. The count will be accurate within seconds as replication completes."*

---

## Read-Your-Writes Consistency

### Definition
A user always sees their own writes. Other users may see stale data temporarily.

### Why It Matters
Without read-your-writes, this happens:
1. User updates profile picture → sees "Updated successfully!"
2. User refreshes the page → **sees the OLD picture** (read went to a stale replica)
3. User thinks the update failed → tries again → frustration

### Implementation Strategies

**Strategy 1: Route to master after write**
```
After write:
  Set cookie: last_write_timestamp = now()

On read:
  If (now - last_write_timestamp) < 5 seconds:
    Route to MASTER (guaranteed to have latest data)
  Else:
    Route to any REPLICA (eventual consistency is fine)
```

**Strategy 2: Read from the replica that accepted the write**
```
Write to Replica B → success
Set session affinity: user_123 → Replica B

Subsequent reads for user_123:
  Route to Replica B (which has the latest write)
  Until replication catches up to other replicas
```

**Strategy 3: Include write version in response**
```
Write response: { "version": 42 }
Client stores version locally

Read request: GET /profile?min_version=42
Server: If local replica version < 42, wait or redirect to master
```

### Cost
- Minimal: Only the writing user's reads are affected.
- ~5 seconds of routing overhead per write event.
- No impact on other users' read performance.

### Interview Script
> *"Read-your-writes consistency is essential for user-facing edits. When I update my profile picture and immediately refresh the page, I must see the new picture — even if other users see the old one for a few seconds while replication catches up. I'd implement this by routing the user's reads to the master for 5 seconds after any write, then falling back to replicas. The 5-second window covers the typical replication lag with a safety margin."*

---

## Monotonic Reads

### Definition
If a user reads value V at time T, any subsequent read by the same user will return V or a newer value — never an older value. No "going back in time."

### Why It Matters
Without monotonic reads:
1. Request 1 → Replica A (up-to-date) → "Order status: Shipped"
2. Request 2 → Replica B (stale) → "Order status: Processing"
3. User thinks the order was un-shipped → confusion, support ticket

### Implementation
**Session stickiness**: Route all reads from the same user to the same replica within a session.

```
User session starts → assign to Replica C
All reads during session → Replica C
Replica C fails → reassign to Replica D (which may be behind Replica C)
  → Accept potential staleness only on failover
```

### Combined with Read-Your-Writes
These two guarantees are often implemented together:
- Read-your-writes: You see your own latest write.
- Monotonic reads: You never see data older than what you've already seen.

Together, they prevent the most confusing user-facing anomalies.

---

## Consistent Prefix (Causal Ordering)

### Definition
If a sequence of writes happens in order A → B → C, any observer sees them in the same order. They might not see all of them (could see just A, or A and B), but never out of order (never C before B).

### Why It Matters
In a chat conversation:
```
Alice: "What time should we meet?"     (T1)
Bob:   "How about 3pm?"                (T2)
Alice: "Sounds good!"                  (T3)
```

Without consistent prefix, a reader might see:
```
Alice: "Sounds good!"                  (T3)  ← Makes no sense without context
Bob:   "How about 3pm?"                (T2)
Alice: "What time should we meet?"     (T1)
```

### Implementation
- **Single-partition ordering**: All messages in a conversation go to the same partition (Kafka partition, Cassandra partition). Within a partition, order is guaranteed.
- **Sequence numbers**: Assign monotonically increasing sequence numbers. Clients render messages in sequence order, buffering out-of-order arrivals.

---

## Causal Consistency

### Definition
Operations that are **causally related** are seen in the same order by all observers. Operations that are **concurrent** (no causal relationship) may be seen in any order.

### Causal vs Concurrent
```
Causal:
  Alice posts "Hello!" (event A)
  Bob replies to Alice's post (event B — caused by A)
  → B must appear after A everywhere

Concurrent:
  Alice posts "Hello!" (event A)
  Charlie posts "Hi everyone!" (event C — independent of A)
  → A and C can appear in any order relative to each other
```

### Implementation: Vector Clocks / Lamport Timestamps

**Lamport timestamps** (simpler):
```
Each node maintains a counter.
On local event: counter++
On send: attach counter to message
On receive: counter = max(local_counter, received_counter) + 1

Events with lower timestamps happened-before events with higher timestamps.
```

**Vector clocks** (more precise):
```
Each node maintains a vector [node1_counter, node2_counter, ...]
Allows detecting true concurrency vs causal ordering.
```

### Why It's Perfect for Chat
In a chat with Alice, Bob, and Charlie:
- Alice's reply to Bob must appear after Bob's message (causal).
- Charlie's unrelated message can appear anywhere (concurrent).
- We don't need global ordering across ALL conversations — just causal ordering within each.

### Cost vs Linearizability
Causal consistency is **dramatically cheaper** than linearizability because it doesn't require global coordination. Only causally related events need ordering — concurrent events are free to proceed independently.

### Interview Script
> *"For messages in a chat conversation, I'd use causal consistency — we need Alice's reply to appear after the message she's replying to, but we don't need global ordering across all 500 million conversations happening simultaneously. Causal consistency gives us the 'happens-before' relationship within a conversation while allowing independent conversations to proceed without coordination. This is dramatically cheaper than strong consistency at global scale."*

---

## Linearizability (Strong Consistency)

### Definition
All operations appear to execute atomically in some total order that is consistent with the real-time ordering of those operations. Every read returns the value of the most recent completed write.

### What This Means Practically
```
Time 1: Client A writes X = 10 → acknowledged
Time 2: Client B reads X → MUST return 10 (not any older value)
Time 3: Client C writes X = 20 → acknowledged
Time 4: Client A reads X → MUST return 20
```

The system behaves as if there is a **single copy** of the data, even though it's replicated across multiple nodes.

### Implementation: Consensus Protocols

**Raft** (most common):
- One leader, multiple followers.
- Writes: Client → Leader → replicates to majority → ACK to client.
- Reads: Either from leader (guaranteed latest) or via read quorum.
- Leader election if current leader fails.

**Paxos** (classic, more complex):
- Proposer, Acceptor, Learner roles.
- Two-phase protocol: Prepare → Accept.
- Handles concurrent proposals correctly.

**ZAB** (ZooKeeper):
- Similar to Raft, optimized for ZooKeeper's use case.
- Atomic broadcast — all followers see writes in the same order.

### Cost
- **Latency**: Every write requires a majority of replicas to ACK → at least one network round-trip to the farthest majority node.
- **Throughput**: Serialized writes through the leader limit write throughput.
- **Availability**: If the majority is unreachable, writes are unavailable (CP tradeoff).

### When to Use
- **Payments**: Cannot double-charge or show incorrect balance.
- **Bookings**: Cannot double-sell the last seat.
- **Leader election**: Cannot have two leaders (split-brain).
- **Configuration**: Feature flags must be consistent across all servers.
- **Distributed locks**: Lock must be exclusive.

### Interview Script
> *"Strong consistency means every read returns the latest write, globally. I'd use it for the seat availability check during the final booking step — `SELECT available FROM seats WHERE seat_id = 'A7' FOR UPDATE`. But for the browse page showing 'approximately 47 seats remaining', eventual consistency is fine. That number can be cached for 30 seconds because it's informational, not transactional. Using strong consistency for both would increase our read latency by 3x for zero user-visible benefit."*

---

## CRDTs — Conflict-Free Replicated Data Types

### Definition
Data structures that can be replicated across multiple nodes, updated independently and concurrently, and **mathematically guaranteed to converge** to the same state without coordination.

### Types of CRDTs

| CRDT Type | Description | Use Case |
|---|---|---|
| **G-Counter** | Grow-only counter (each node has its own count; total = sum) | Like counts, view counts |
| **PN-Counter** | Positive-Negative counter (two G-Counters: increments and decrements) | Follower counts (follow/unfollow) |
| **G-Set** | Grow-only set (add elements, never remove) | Tag sets, "users who viewed" |
| **OR-Set** | Observed-Remove set (add and remove with unique tags) | Shopping cart items |
| **LWW-Register** | Last-Writer-Wins register (timestamp-based) | User profile fields |
| **RGA** | Replicated Growable Array (for text editing) | Collaborative document editing |

### How G-Counter Works
```
Node A counter: [A:5, B:0, C:0]  → total = 5
Node B counter: [A:0, B:3, C:0]  → total = 3
Node C counter: [A:0, B:0, C:7]  → total = 7

After merge (take max per node):
All nodes: [A:5, B:3, C:7]       → total = 15

Key insight: Merge is commutative, associative, and idempotent.
Order of merges doesn't matter. Result is always the same.
```

### Real-World Applications
- **Google Docs / Figma**: RGA-based CRDTs for collaborative text editing.
- **Redis CRDT**: Enterprise Redis supports CRDT-based active-active replication.
- **Riak**: Uses CRDTs for conflict-free multi-master replication.
- **Shopping carts**: OR-Set CRDT for add/remove items across devices.

### Interview Script
> *"For the collaborative editing feature, I'd use CRDTs — Conflict-free Replicated Data Types. Two users can edit the same document simultaneously in different regions, and when their changes replicate, the CRDT guarantees convergence without any coordination. The specific CRDT for text editing is an RGA (Replicated Growable Array) — insertions and deletions are commutative, so the order of replication doesn't matter. Google Docs and Figma both use CRDT-like approaches for this exact reason."*

---

## Quorum Reads & Writes

### The Formula
For N replicas, W write replicas, and R read replicas:

```
If W + R > N → Guaranteed to read the latest write (strong consistency)
If W + R ≤ N → Possible to read stale data (eventual consistency)
```

### Common Configurations

| Configuration | W | R | N | Guarantee | Use Case |
|---|---|---|---|---|---|
| **Strong** | 2 | 2 | 3 | W+R=4 > N=3 → Strong | Payments, bookings |
| **Write-heavy** | 1 | 3 | 3 | W+R=4 > N=3 → Strong reads, fast writes | Logging, events |
| **Read-heavy** | 3 | 1 | 3 | W+R=4 > N=3 → Strong writes, fast reads | Config, feature flags |
| **AP (eventual)** | 1 | 1 | 3 | W+R=2 ≤ N=3 → Eventual | Social feeds, analytics |

### Tradeoffs
```
Higher W → Slower writes, more durable
Higher R → Slower reads, more consistent
Lower W → Faster writes, risk of data loss on node failure
Lower R → Faster reads, risk of stale data
```

### Interview Script
> *"I'd set replication factor to 3 with quorum reads and writes (W=2, R=2, N=3). This means every write must succeed on at least 2 of 3 replicas before ACK, and every read contacts at least 2 of 3 replicas and returns the newest value. Since W + R (4) > N (3), we're guaranteed to read our own writes. The system tolerates 1 node failure for both reads and writes."*

---

## Tunable Consistency

### Concept
Databases like Cassandra and DynamoDB let you choose consistency level **per query**, not per table or per database.

### Cassandra Consistency Levels

| Level | Replicas Required | Latency | Use Case |
|---|---|---|---|
| **ONE** | 1 of N | Lowest | Analytics, non-critical reads |
| **QUORUM** | ⌈(N+1)/2⌉ | Medium | Standard operations |
| **LOCAL_QUORUM** | Quorum within local DC | Medium (no cross-DC) | Multi-DC with regional consistency |
| **ALL** | N of N | Highest | Critical operations (rarely used) |
| **EACH_QUORUM** | Quorum in each DC | Highest | Global consistency |

### Per-Operation Tuning Example
```java
// Writing a tweet — need durability but not global consensus
session.execute(insertTweet.setConsistencyLevel(ConsistencyLevel.QUORUM));

// Reading a timeline — speed over freshness
session.execute(selectTimeline.setConsistencyLevel(ConsistencyLevel.ONE));

// Checking seat availability for booking — need exactness
session.execute(checkSeat.setConsistencyLevel(ConsistencyLevel.ALL));
```

---

## Consistency in Multi-Region Deployments

### The Latency Problem
```
Same region:        1-5ms round-trip
Cross-region:       50-200ms round-trip
Cross-continent:    100-300ms round-trip
```

Strong consistency across regions means every write waits for cross-region round-trip → 100-300ms write latency. For a social feed, this is unacceptable.

### Strategies

**Strategy 1: Local Strong, Global Eventual**
```
US-East writes → strong within US-East (5ms)
                → async replication to EU-West (200ms lag)
EU-West reads  → local replica (may be 200ms stale)
```

**Strategy 2: Global Strong (Spanner)**
```
Any write → consensus across all regions → 200-500ms
Any read  → guaranteed latest value globally
Cost: Very high write latency; justified only for financial data
```

**Strategy 3: Causal Consistency Across Regions**
```
Causally related operations → ordered correctly cross-region
Concurrent operations → independently processed per region
Cost: Much lower than linearizability; captures most user-visible ordering
```

---

## Implementing Consistency Guarantees

### Implementation Summary

| Consistency Level | Implementation Mechanism |
|---|---|
| Eventual | Async replication, no coordination |
| Read-Your-Writes | Route to master for N seconds post-write |
| Monotonic Reads | Session stickiness to same replica |
| Consistent Prefix | Partition-level ordering (Kafka, Cassandra) |
| Causal | Vector clocks / Lamport timestamps |
| Linearizable | Consensus protocol (Raft, Paxos, 2PC) |

---

## Consistency vs Latency vs Throughput

```
┌──────────────────────────────────────────────────────────────┐
│          CONSISTENCY TRADEOFF TRIANGLE                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Stronger consistency → Higher latency                       │
│                       → Lower throughput                     │
│                       → Better correctness                   │
│                                                              │
│  Weaker consistency  → Lower latency                        │
│                       → Higher throughput                     │
│                       → Risk of stale/incorrect data         │
│                                                              │
│  LATENCY COMPARISON (3-replica setup):                       │
│  CL=ONE:     ~2ms  (1 replica responds)                     │
│  CL=QUORUM:  ~5ms  (2 of 3 replicas respond)               │
│  CL=ALL:     ~10ms (all 3 replicas respond)                 │
│                                                              │
│  THROUGHPUT COMPARISON:                                      │
│  CL=ONE:     100K reads/sec per node                        │
│  CL=QUORUM:  40K reads/sec per node                         │
│  CL=ALL:     15K reads/sec per node                         │
│                                                              │
│  CROSS-REGION:                                               │
│  Local read:   5ms                                           │
│  Quorum (cross-region): 100-200ms                           │
│  Linearizable (global): 200-500ms                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Interview Talking Points & Scripts

### Choosing Consistency Per Operation
> *"I'd apply different consistency models to different operations. The timeline read is eventually consistent — a 3-second-stale feed is invisible. The profile update uses read-your-writes — the editing user must see their change immediately. The payment uses linearizability — we cannot show an incorrect balance. Each operation gets the cheapest consistency level that meets its correctness requirements."*

### Explaining CRDTs
> *"For the collaborative editing feature, I'd use CRDTs. Two users editing simultaneously in different regions produce changes that converge without coordination — mathematically guaranteed. The tradeoff is limited data types and some complexity in the CRDT implementation, but for this specific problem, it's the elegant solution."*

### Cost of Strong Consistency
> *"Linearizability costs us in three ways: latency (consensus round-trip adds 5-10ms within a region, 100-200ms cross-region), throughput (serialized through a single leader), and availability (majority must be reachable). For a payment, these costs are justified. For a timeline, they're wasteful."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "We need strong consistency everywhere"
**Problem**: Over-constraining the system. 90% of operations don't need strong consistency.

### ❌ Mistake 2: Confusing consistency models
**Problem**: Mixing up "eventual consistency" (CAP) with "consistency" (ACID). They're different concepts.

### ❌ Mistake 3: Not knowing quorum math
**Problem**: Saying "we'd use replication" without specifying W, R, N and whether W+R > N.

### ❌ Mistake 4: Ignoring read-your-writes
**Problem**: Designing a system where a user edits their profile and then sees the old version. This is the #1 user-facing consistency bug.

### ❌ Mistake 5: Not mentioning CRDTs for collaborative editing
**Problem**: When asked about Google Docs or collaborative features, reaching for distributed locks instead of CRDTs/OT.

---

## Applying Consistency Models to System Design Problems

| System | Operation | Consistency Level | Why |
|---|---|---|---|
| **Twitter** | Read timeline | Eventual | Stale feed is fine |
| **Twitter** | Post tweet | Read-your-writes | User must see their own tweet |
| **Twitter** | Like count display | Eventual | 4,998 vs 5,001 is invisible |
| **WhatsApp** | Message delivery | Causal | Reply must appear after original |
| **WhatsApp** | Presence (online) | Eventual | Approximate by nature |
| **Booking** | Browse availability | Eventual | "~47 seats" is fine |
| **Booking** | Reserve seat | Linearizable | Cannot double-sell |
| **Banking** | Transfer funds | Linearizable | Must be exact |
| **Banking** | View balance | Read-your-writes | Must see own transactions |
| **Google Docs** | Collaborative editing | Causal (CRDTs) | Convergence without coordination |
| **Config/Flags** | Read feature flag | Linearizable | Stale flags cause bugs |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              CONSISTENCY MODELS CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  SPECTRUM (weak → strong):                                   │
│  Eventual → Read-Your-Writes → Monotonic Reads               │
│  → Consistent Prefix → Causal → Linearizable                │
│                                                              │
│  EVENTUAL: Replicas converge over time                       │
│    Use: feeds, counters, CDN, analytics                      │
│                                                              │
│  READ-YOUR-WRITES: See your own writes immediately           │
│    Impl: Route to master for 5s after write                  │
│    Use: profile edits, post creation                         │
│                                                              │
│  CAUSAL: Causally related ops are ordered                    │
│    Impl: Vector clocks, Lamport timestamps                   │
│    Use: chat, collaborative editing                          │
│                                                              │
│  LINEARIZABLE: Single-copy illusion                          │
│    Impl: Raft, Paxos, 2PC                                   │
│    Use: payments, bookings, leader election                  │
│                                                              │
│  CRDTs: Conflict-free convergence, no coordination           │
│    Use: collaborative editing, counters, shopping carts      │
│                                                              │
│  QUORUM: W + R > N → strong; W + R ≤ N → eventual           │
│    Standard: W=2, R=2, N=3 (tolerates 1 failure)            │
│                                                              │
│  RULE: Use the CHEAPEST consistency that meets               │
│  the correctness requirement of each operation               │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 1: CAP Theorem** — The C in CAP is linearizability
- **Topic 2: Database Selection** — Different DBs offer different consistency models
- **Topic 13: Replication** — How replication implements consistency
- **Topic 22: Delivery Guarantees** — Exactly-once, at-least-once
- **Topic 34: Multi-Region** — Consistency across geographic regions

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
