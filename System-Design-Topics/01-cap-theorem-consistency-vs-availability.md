# 🎯 Topic 1: CAP Theorem & Consistency vs Availability

> **System Design Interview — Deep Dive**
> A comprehensive guide covering everything you need to know about the CAP Theorem, consistency-availability tradeoffs, and how to articulate these concepts with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [The Three Guarantees Explained](#the-three-guarantees-explained)
3. [Why "Pick 2 of 3" Is Misleading](#why-pick-2-of-3-is-misleading)
4. [CP vs AP — When to Choose What](#cp-vs-ap--when-to-choose-what)
5. [Per-Operation CAP Decisions](#per-operation-cap-decisions)
6. [Real-World System Examples](#real-world-system-examples)
7. [How Network Partitions Actually Happen](#how-network-partitions-actually-happen)
8. [PACELC — The Extended CAP Model](#pacelc--the-extended-cap-model)
9. [Consistency Spectrum in Practice](#consistency-spectrum-in-practice)
10. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
11. [Common Interview Mistakes](#common-interview-mistakes)
12. [Deep Dive: Applying CAP to Popular System Design Problems](#deep-dive-applying-cap-to-popular-system-design-problems)
13. [Tradeoff Decision Framework](#tradeoff-decision-framework)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

The **CAP Theorem** (Brewer's Theorem, 2000) states that a distributed data store can provide at most **two out of three** guarantees simultaneously:

- **C** — Consistency: Every read receives the most recent write or an error.
- **A** — Availability: Every request receives a (non-error) response, without guarantee it contains the most recent write.
- **P** — Partition Tolerance: The system continues to operate despite arbitrary message loss or failure of part of the network.

### The Critical Insight

In any distributed system, **network partitions are inevitable** (hardware fails, cables get cut, switches misroute packets, cloud regions have outages). Since **P is not optional** in a real distributed system, the actual choice is between **C and A** during a partition event:

- **CP**: During a partition, the system returns errors or timeouts rather than risk returning stale/incorrect data.
- **AP**: During a partition, the system returns the best available data, which may be stale, rather than returning an error.

---

## The Three Guarantees Explained

### Consistency (C) — Linearizability

In the CAP sense, "consistency" means **linearizability** — the strongest form of consistency:

- Every read returns the value of the most recent successful write.
- All nodes see the same data at the same time.
- The system behaves as if there is a single copy of the data, even though it is replicated.

**Example**: If User A writes `balance = $500` at time T1, and User B reads `balance` at time T2 > T1, User B **must** see $500. Not $400 (the old value). Not "data unavailable." Exactly $500.

**Cost**: To achieve this, the system must **coordinate** — all replicas must agree on the value before responding. This takes time (network round-trips between replicas) and fails if some replicas are unreachable.

### Availability (A) — Every Request Gets a Response

- Every request to a non-failing node receives a response.
- The response must be a meaningful result (not an error).
- There is no guarantee that the response contains the latest write.

**Example**: If User B sends a read request to Node 2 (which is partitioned from Node 1 where the latest write happened), Node 2 responds with whatever value it has — even if it's stale. The user gets data, but it might be old.

**Cost**: The data returned may be incorrect/stale. In some domains (banking, booking), stale data is worse than no data.

### Partition Tolerance (P) — The System Survives Network Splits

- The system continues to function even when network messages are lost or delayed between nodes.
- Partitions can be transient (milliseconds) or prolonged (minutes to hours).
- In a cloud environment, partitions between availability zones or regions are a regular occurrence.

**Why P Is Non-Negotiable**: In any system deployed on more than one machine, network partitions **will** happen. Treating P as optional means you're designing a single-node system, which defeats the purpose of distributed architecture. Therefore, the real-world choice is always **CP or AP**.

---

## Why "Pick 2 of 3" Is Misleading

The common "Venn diagram" showing CA, CP, and AP as equal choices is inaccurate for several reasons:

### 1. You Can't Choose CA in a Distributed System

A "CA" system would provide both consistency and availability but not tolerate partitions. This is a **single-node database** — like a standalone PostgreSQL instance. The moment you add a second node for replication, you're in a distributed system and **must** handle partitions.

### 2. CAP Is Not a System-Wide Binary Switch

The most important insight for interviews: **CAP is a per-operation, per-data-type decision, not a system-level one**.

A single system can (and should) make different choices for different operations:

| Operation | CAP Choice | Reasoning |
|---|---|---|
| Payment processing | CP | Double-charging is unacceptable |
| Social media feed | AP | Stale feed is better than no feed |
| Inventory count (10,000 units) | AP | Slight overcount is acceptable |
| Inventory count (3 units left) | CP | Must be exact to prevent overselling |
| Presence (online/offline) | AP | Approximate by nature |
| Leader election | CP | Split-brain is catastrophic |
| Like counter display | AP | 4,998 vs 5,001 is invisible |
| Like counter for billing | CP | Must be exact for revenue |

### 3. Partitions Are Rare but Decisive

In a well-run cloud deployment, network partitions happen maybe a few minutes per year. The question is: **what happens during those few minutes?**

- For a social feed: showing yesterday's tweets during a 2-minute partition is perfectly fine (AP).
- For a bank transfer: showing an incorrect balance for even 1 second is a compliance violation (CP).

---

## CP vs AP — When to Choose What

### Choose CP (Consistency over Availability) When:

1. **Financial transactions**: Bank transfers, payments, billing — incorrect balances are unacceptable.
2. **Booking systems**: Ticket reservations, hotel rooms, airline seats — double-selling has legal and trust consequences.
3. **Leader election**: Job schedulers, distributed locks — split-brain means duplicate work (sending emails twice, charging customers twice).
4. **Inventory management with low stock**: When only 3 units remain, the count must be exact.
5. **Configuration management**: Feature flags, routing tables — stale configs can cause cascading failures.

**What CP looks like in practice**:
- The system returns an error or times out during a partition.
- Users see "temporarily unavailable, please try again in 30 seconds."
- The system refuses to serve potentially incorrect data.

### Choose AP (Availability over Consistency) When:

1. **Social feeds and timelines**: A slightly stale feed is better than no feed.
2. **Search results**: Results from a slightly stale index are better than no results.
3. **Analytics dashboards**: Approximate real-time numbers are fine.
4. **Presence indicators**: "Online" / "Last seen 2 minutes ago" is inherently approximate.
5. **Shopping product listings**: "Approximately 47 in stock" is fine for browsing (exact count matters only at checkout).
6. **Content delivery**: Serving a cached version of a web page is better than an error.

**What AP looks like in practice**:
- The system always responds, even during partitions.
- Users get data, but it might be a few seconds (or minutes) old.
- The system eventually converges to the correct state when the partition heals.

---

## Per-Operation CAP Decisions

This is the most powerful concept to demonstrate in an interview: **decomposing a system into operations with different CAP requirements**.

### Example: E-Commerce Platform

```
┌──────────────────────────────────────────────────┐
│              E-Commerce Platform                  │
├──────────────────────────────────────────────────┤
│                                                  │
│  Browse Product Catalog ──────────────── AP      │
│  (Show cached product info, stale OK)            │
│                                                  │
│  Display "~47 in stock" ──────────────── AP      │
│  (Approximate count, cached 30s)                 │
│                                                  │
│  Add to Cart ─────────────────────────── AP      │
│  (Cart is per-session, no global state)          │
│                                                  │
│  Check Exact Inventory at Checkout ──── CP       │
│  (Must be exact to prevent overselling)          │
│                                                  │
│  Process Payment ─────────────────────── CP      │
│  (Must be atomic, no double-charge)              │
│                                                  │
│  Update Order Status ─────────────────── AP      │
│  (Eventual consistency fine)                     │
│                                                  │
│  Send Order Confirmation Email ──────── AP       │
│  (Delayed by seconds is fine)                    │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Example: Messaging System (WhatsApp-like)

```
┌──────────────────────────────────────────────────┐
│              Messaging System                     │
├──────────────────────────────────────────────────┤
│                                                  │
│  Send Message ────────────────────────── CP      │
│  (Message must be durably stored)                │
│                                                  │
│  Message Ordering within Chat ────────── CP      │
│  (Causal ordering — replies after messages)      │
│                                                  │
│  Presence (Online/Offline) ──────────── AP       │
│  (Approximate by nature)                         │
│                                                  │
│  "Typing..." Indicator ──────────────── AP       │
│  (Best-effort, ephemeral)                        │
│                                                  │
│  Read Receipts ──────────────────────── AP       │
│  (Delayed by seconds is fine)                    │
│                                                  │
│  Contact List Sync ──────────────────── AP       │
│  (Eventual consistency fine)                     │
│                                                  │
└──────────────────────────────────────────────────┘
```

---

## Real-World System Examples

### CP Systems

| System | Why CP? |
|---|---|
| **Google Spanner** | Globally consistent, uses TrueTime (atomic clocks + GPS) for external consistency. Sacrifices availability during partitions for guaranteed linearizability. |
| **ZooKeeper** | Used for leader election, configuration management, distributed locks. Incorrect state would cause cascading failures. Uses ZAB protocol (similar to Paxos). |
| **etcd** | Kubernetes' backing store. If etcd returns stale data, pods could be scheduled on non-existent nodes. Uses Raft consensus. |
| **HBase** | Single-master architecture. If the master is unreachable, writes are unavailable. Strong consistency for read-after-write. |

### AP Systems

| System | Why AP? |
|---|---|
| **Cassandra** | Leaderless, tunable consistency. Default is eventual consistency for maximum availability. Each node accepts writes independently. |
| **DynamoDB** | AP by default (eventual consistency reads). Optionally CP for strongly consistent reads (costs 2x read capacity). |
| **CouchDB** | Multi-master replication with eventual consistency. Designed for offline-first applications where availability is paramount. |
| **DNS** | Returns cached records that may be stale for up to TTL (hours). Availability over freshness — better to resolve to a slightly old IP than fail to resolve. |
| **CDN (CloudFront)** | Serves cached content that may be stale. A stale image is better than no image. |

### Hybrid Systems (Different Operations, Different Choices)

| System | CP Operations | AP Operations |
|---|---|---|
| **Amazon (e-commerce)** | Checkout, payment | Product browsing, recommendations |
| **Twitter** | Tweet persistence | Feed display, like counts |
| **Uber** | Payment, trip assignment | Driver location display, ETA |
| **Netflix** | User account, billing | Video catalog, recommendations |

---

## How Network Partitions Actually Happen

Understanding real-world partitions makes your interview answers more credible:

### 1. Inter-Region Partitions
- Submarine cable cuts (happens several times per year globally).
- Cloud provider outages (AWS us-east-1 has had multiple region-level events).
- DNS propagation issues between regions.

### 2. Intra-Region Partitions
- Switch failures in a data center isolating a rack.
- Availability zone connectivity issues.
- Network congestion causing packet loss above acceptable thresholds.

### 3. Application-Level Partitions
- A microservice becomes unreachable due to misconfigured firewall rules.
- A database node gets isolated due to a security group change.
- An overloaded load balancer drops connections.

### 4. Asymmetric Partitions
- Node A can reach Node C, Node B can reach Node C, but A and B can't reach each other.
- Creates the most complex consistency challenges.

### Partition Duration Statistics (Approximate)
- **Cloud provider internal**: 99.99% resolved in < 30 seconds.
- **Regional**: Minutes to hours (rare, maybe 1-2 per year per region).
- **Global**: Hours (extremely rare, maybe once every 2-3 years).

---

## PACELC — The Extended CAP Model

CAP only describes behavior **during** a partition. **PACELC** extends this to include behavior in the **normal (no partition) case**:

```
If Partition (P):
    Choose Availability (A) or Consistency (C)
Else (E):
    Choose Latency (L) or Consistency (C)
```

This matters because even when there's no partition, there's a **latency vs consistency** tradeoff:

- **PA/EL** (e.g., Cassandra, DynamoDB default): During partition, choose availability. During normal operation, choose low latency (eventually consistent reads).
- **PC/EC** (e.g., Google Spanner): During partition, choose consistency. During normal operation, also choose consistency (pay the latency cost of consensus).
- **PA/EC** (e.g., MongoDB with strong reads): During partition, choose availability. During normal operation, choose consistency.
- **PC/EL** (rare): During partition, choose consistency. During normal operation, choose low latency.

### Why PACELC Matters in Interviews

It shows deeper understanding than basic CAP. Most interview candidates stop at "CAP says pick 2." PACELC lets you say:

> *"Even when there's no partition, which is 99.99% of the time, we still face a latency-consistency tradeoff. A strongly consistent read in a multi-region setup requires a round-trip to the leader, adding 100-200ms of latency. An eventually consistent read can be served from the nearest replica in 5ms. For a feed timeline that refreshes every time the user pulls down, the 200ms extra latency for strong consistency is noticeable and unnecessary."*

---

## Consistency Spectrum in Practice

Consistency isn't binary (strong vs eventual). There's a rich spectrum:

### From Weakest to Strongest

| Level | Definition | Use Case | Example |
|---|---|---|---|
| **Eventual Consistency** | All replicas converge to the same value given enough time. No ordering guarantee. | DNS, CDN caching, social media feeds | Cassandra with CL=ONE |
| **Read-Your-Writes** | A user always sees their own writes. Other users may see stale data. | Profile updates, post creation | Route user to master for 5s after write |
| **Monotonic Reads** | A user never sees an older value after seeing a newer one. No "going back in time." | Chat message history | Sticky sessions to same replica |
| **Consistent Prefix** | Writes are seen in the order they were made. No seeing a reply before the original message. | Conversation threads | Causal consistency within a partition |
| **Causal Consistency** | Causally related operations are seen in order. Concurrent operations may be seen in any order. | Chat conversations, collaborative editing | Vector clocks, Lamport timestamps |
| **Linearizability (Strong)** | All operations appear to execute atomically in some total order consistent with real-time. | Payments, booking, leader election | Consensus protocols (Raft, Paxos) |

### Practical Encoding in System Design

```
Timeline Feed:     Eventual Consistency     ← cheapest, fastest
Profile Edits:     Read-Your-Writes         ← user sees own changes immediately
Chat Messages:     Causal Consistency        ← replies appear after originals
Seat Booking:      Linearizability           ← most expensive, slowest, correct
```

---

## Interview Talking Points & Scripts

### Opening Statement (Setting the Frame)

> *"CAP isn't a system-level binary switch — it's a per-operation choice. I'd apply different consistency models to different parts of this system based on the business requirements of each operation."*

### For User-Facing Reads (Feed, Timeline, Search)

> *"For user-facing reads like timelines, I'd choose AP — a slightly stale feed is better than an error. If a user opens Twitter during a brief network partition, they should see yesterday's tweets rather than a 503 error page. The staleness window is typically under 5 seconds, and users genuinely cannot tell the difference between 4,998 and 5,001 likes."*

### For Critical Write Paths (Payments, Bookings)

> *"For the booking/payment path, I'd choose CP — a brief unavailability is better than double-selling. If we're selling the last 2 seats to a concert and a network partition happens, I'd rather show 'temporarily unavailable, try again in 30 seconds' than risk selling 3 seats. The revenue loss from a few seconds of downtime is nothing compared to the legal and trust cost of overselling."*

### For Mixed Systems

> *"Most systems are a mix: CP for the write path of critical data, AP for the read path of non-critical data. In the same ticket booking system, the actual reservation transaction is CP (we cannot double-sell), but the browse page showing 'approximately 47 seats remaining' is AP — it's a cached value that might be 30 seconds stale, and that's perfectly fine for browsing."*

### For Leader Election / Coordination

> *"For the leader election service, CP is non-negotiable — we'd rather have a brief pause in job scheduling than two leaders assigning the same job to different workers. Split-brain in a job scheduler means duplicate execution, which could mean sending the same email twice or charging a customer twice. A 10-second election pause is vastly preferable."*

### Demonstrating Nuance (The Inventory Example)

> *"In practice, network partitions are rare — maybe a few minutes per year in a well-run cloud deployment. But when they happen, the question is: do you show stale data or show errors for those few minutes? For a social feed, stale is clearly fine. For a bank transfer, errors are safer. The interesting cases are in between — like an inventory count for a product with 10,000 units (AP is fine, slight overcount is okay) vs a product with 3 units left (CP is needed, must be exact)."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd choose CP for the entire system"
**Why it's wrong**: Over-constraining the system. Most read operations don't need strong consistency, and making them CP wastes resources and increases latency.

**Better**: Decompose the system into operations and choose per-operation.

### ❌ Mistake 2: "CAP means you can only pick 2 out of 3"
**Why it's wrong**: Oversimplification. P is not optional in distributed systems, so the real choice is C vs A during partitions. And the choice is per-operation, not per-system.

**Better**: *"Since partition tolerance is non-negotiable in a distributed system, the real choice during a partition is between consistency and availability — and I make that choice differently for each operation."*

### ❌ Mistake 3: Confusing CAP consistency with ACID consistency
**Why it's wrong**: CAP's C means linearizability (all nodes see the same data). ACID's C means the database moves from one valid state to another (constraints are satisfied). They're completely different concepts.

### ❌ Mistake 4: Treating eventual consistency as "no consistency"
**Why it's wrong**: Eventual consistency has a well-defined guarantee: given no new updates, all replicas will **eventually** converge to the same value. It's not "anything goes" — it's a bounded convergence guarantee.

### ❌ Mistake 5: Not mentioning specific numbers
**Why it's wrong**: Saying "the staleness is acceptable" is vague. Saying "the staleness window is typically under 5 seconds, and users cannot tell the difference between 4,998 and 5,001 likes" demonstrates concrete reasoning.

---

## Deep Dive: Applying CAP to Popular System Design Problems

### Twitter / Social Feed System

**Write Path (Tweet Creation)**: CP
- The tweet must be durably stored — losing a user's tweet is unacceptable.
- Write to Cassandra with consistency level QUORUM (W=2 out of RF=3).
- User gets confirmation only after durable write.

**Read Path (Timeline)**: AP
- Timeline can be slightly stale (seconds).
- Read from Redis cache (pre-computed timeline) or Cassandra with CL=ONE.
- If one node is down, another serves the read with potentially stale data.

**Like Count Display**: AP
- Exact count is unnecessary for display.
- Cache the count with 30-second TTL.
- 4,998 vs 5,001 is invisible to users.

**Like Count for Analytics/Billing**: CP
- Must be exact for advertiser billing.
- Batch-reconciled from event log nightly.

### Uber / Ride-Sharing

**Trip Assignment**: CP
- Cannot assign the same driver to two riders.
- Use distributed locking on driver_id.

**Driver Location Updates**: AP
- Location is inherently approximate (GPS accuracy ± 5 meters).
- 2-second-stale location is perfectly fine for the rider's map.
- Write to every available replica, read from nearest.

**Payment Processing**: CP
- Must be exact, atomic, no double-charging.
- Use ACID transactions with idempotency keys.

**ETA Calculation**: AP
- Approximate by nature (traffic changes constantly).
- Computed from cached data, updated every few seconds.

### Ticket Booking (Concerts, Flights)

**Seat Browsing**: AP
- "Approximately 47 seats available" — cached for 30 seconds.
- Stale count during browsing is harmless.

**Seat Selection & Locking**: CP
- Must be exact — cannot let two users select the same seat.
- Pessimistic lock: `SELECT ... FOR UPDATE` on the seat row.

**Payment**: CP
- Atomic charge — no double-selling, no double-charging.

**Confirmation Email**: AP
- 5-second delay in email delivery is invisible.
- Queued via Kafka, delivered eventually.

---

## Tradeoff Decision Framework

Use this framework in interviews to structure your CAP reasoning:

```
Step 1: Identify the operation
    "We're talking about the [specific operation]."

Step 2: Define the failure mode of each choice
    "If we choose CP, users see [error/timeout] during a partition."
    "If we choose AP, users see [stale/incorrect data] during a partition."

Step 3: Evaluate the business impact
    "The cost of [stale data] is [low/medium/high] because..."
    "The cost of [unavailability] is [low/medium/high] because..."

Step 4: Make the decision
    "For this operation, I'd choose [CP/AP] because [specific reasoning]."

Step 5: Quantify the staleness (for AP) or downtime (for CP)
    "The staleness window would be ~[X seconds]."
    "The unavailability window would be ~[X seconds]."
```

### Quick Decision Matrix

| Question | If Yes → CP | If Yes → AP |
|---|---|---|
| Does incorrect data cause financial loss? | ✅ | |
| Does incorrect data have legal consequences? | ✅ | |
| Is this data naturally approximate? | | ✅ |
| Can the user retry in 30 seconds? | ✅ | |
| Would an error page lose revenue/users? | | ✅ |
| Is this a read-heavy, display-only operation? | | ✅ |
| Does this involve money changing hands? | ✅ | |
| Is this a coordination/leadership operation? | ✅ | |

---

## Summary Cheat Sheet

```
┌─────────────────────────────────────────────────────────────┐
│                   CAP THEOREM CHEAT SHEET                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  P is MANDATORY → Real choice is C vs A during partition    │
│                                                             │
│  CP = Errors over stale data                                │
│       Use for: payments, bookings, leader election          │
│       Systems: Spanner, ZooKeeper, etcd                     │
│                                                             │
│  AP = Stale data over errors                                │
│       Use for: feeds, search, analytics, presence           │
│       Systems: Cassandra, DynamoDB, DNS, CDN                │
│                                                             │
│  KEY INSIGHT: It's per-operation, not per-system            │
│  Same system: CP for payments, AP for feeds                 │
│                                                             │
│  PACELC: Even without partitions,                           │
│  there's a latency vs consistency tradeoff                  │
│                                                             │
│  CONSISTENCY SPECTRUM (weakest → strongest):                │
│  Eventual → Read-Your-Writes → Monotonic Reads              │
│  → Consistent Prefix → Causal → Linearizable               │
│                                                             │
│  INTERVIEW FORMULA:                                         │
│  State choice → Explain why → Acknowledge cost              │
│  → Justify why cost is acceptable                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 4: Consistency Models** — Deeper dive into the consistency spectrum
- **Topic 13: Replication** — How replication strategies implement C and A
- **Topic 22: Delivery Guarantees** — Exactly-once, at-least-once, at-most-once
- **Topic 28: Availability & Disaster Recovery** — Designing for 99.99% availability
- **Topic 34: Multi-Region & Geo-Distribution** — CAP across regions

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
