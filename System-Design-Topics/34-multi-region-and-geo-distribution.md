# 🎯 Topic 34: Multi-Region & Geo-Distribution

> **System Design Interview — Deep Dive**
> A comprehensive guide covering active-active multi-region deployment, data sovereignty (GDPR), geographic sharding, cross-region replication strategies, conflict resolution, DNS-based routing, and how to articulate geo-distribution decisions with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Multi-Region](#why-multi-region)
3. [Multi-Region Architecture Patterns](#multi-region-architecture-patterns)
4. [Active-Passive vs Active-Active](#active-passive-vs-active-active)
5. [DNS-Based Global Routing](#dns-based-global-routing)
6. [Cross-Region Replication Strategies](#cross-region-replication-strategies)
7. [Conflict Resolution in Active-Active](#conflict-resolution-in-active-active)
8. [Data Sovereignty & GDPR Compliance](#data-sovereignty--gdpr-compliance)
9. [Geographic Sharding](#geographic-sharding)
10. [Cross-Region Latency & Physics](#cross-region-latency--physics)
11. [Regional Failover & Disaster Recovery](#regional-failover--disaster-recovery)
12. [Real-World System Examples](#real-world-system-examples)
13. [Deep Dive: Applying Multi-Region to Popular Problems](#deep-dive-applying-multi-region-to-popular-problems)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Multi-region deployment** means running the full application stack (compute, storage, caching, queues) in multiple geographic regions simultaneously to achieve three goals:

1. **Low latency worldwide**: Users hit the nearest region (~15ms vs ~200ms cross-continent).
2. **Regional fault tolerance**: If an entire AWS region goes down, traffic fails over to the next closest region.
3. **Data sovereignty compliance**: User data stays within legal jurisdictional boundaries (GDPR, data residency laws).

```
┌──────────────────────────────────────────────────────────────┐
│                   GLOBAL DEPLOYMENT                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   🇺🇸 us-east-1          🇪🇺 eu-west-1         🇯🇵 ap-northeast-1│
│   ┌──────────┐         ┌──────────┐         ┌──────────┐    │
│   │ API GW   │         │ API GW   │         │ API GW   │    │
│   │ Services │         │ Services │         │ Services │    │
│   │ Cache    │         │ Cache    │         │ Cache    │    │
│   │ Database │◄───────►│ Database │◄───────►│ Database │    │
│   │ Queue    │  async   │ Queue    │  async   │ Queue    │    │
│   └──────────┘  repl    └──────────┘  repl    └──────────┘    │
│                                                              │
│   Route 53 latency-based routing → nearest region            │
│   Failover: Region down → DNS routes to next closest (<30s)  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Why Multi-Region

### 1. Latency (Speed of Light Is the Bottleneck)

| Route | Distance | Round-Trip Latency |
|---|---|---|
| Same region (us-east-1 ↔ us-east-1) | ~0 km | 1-5ms |
| US East ↔ US West | ~4,000 km | 60-80ms |
| US East ↔ Europe | ~6,000 km | 80-120ms |
| US East ↔ Asia Pacific | ~15,000 km | 150-250ms |
| US East ↔ Australia | ~16,000 km | 200-300ms |

A user in Tokyo hitting a server in Virginia adds **200ms of latency per request** — just for the network round trip. For interactive applications (chat, search, gaming), this is unacceptable.

**Solution**: Deploy in ap-northeast-1 (Tokyo). Tokyo users hit a local server: 5ms instead of 200ms.

### 2. Availability (Regional Failures Happen)

Major AWS region outages (real examples):
- **us-east-1** (Dec 2021): Entire region impaired for 5+ hours. Netflix, Slack, Imgur affected.
- **ap-southeast-1** (Jun 2023): Service degradation for several hours.
- **eu-west-1** (Nov 2020): Kinesis failure cascaded across services.

Single-region applications go completely offline during these events. Multi-region applications route around the failure.

### 3. Compliance (Legal Requirements)

- **GDPR (EU)**: Personal data of EU citizens must be processable within the EU.
- **PDPA (Singapore)**: Data transfer restrictions.
- **Data Residency Laws (Russia, China, India)**: Data must remain within national borders.
- **HIPAA (US)**: Healthcare data restrictions on where data can be stored.

---

## Multi-Region Architecture Patterns

### Pattern 1: Active-Passive (One Primary, One+ Standby)

```
┌──────────────┐                  ┌──────────────┐
│  us-east-1   │  async repl ──→  │  eu-west-1   │
│  (ACTIVE)    │                  │  (PASSIVE)    │
│              │                  │              │
│  All reads   │                  │  Read-only   │
│  All writes  │                  │  No writes   │
└──────────────┘                  └──────────────┘
         ▲
         │ All traffic
    Route 53 (primary)
```

**How it works**: All traffic goes to the primary region. The passive region receives asynchronous replication and serves as a hot standby. On primary failure, DNS fails over to passive.

**Pros**: Simple consistency model (one writer), no conflict resolution needed.
**Cons**: EU users have high latency (must reach US for reads and writes), passive region is mostly idle (wasted cost), failover takes 30-60 seconds.

**Best for**: Applications where simplicity trumps latency; compliance isn't region-specific.

### Pattern 2: Active-Active (All Regions Accept Traffic)

```
┌──────────────┐                  ┌──────────────┐
│  us-east-1   │◄── async repl ──►│  eu-west-1   │
│  (ACTIVE)    │                  │  (ACTIVE)     │
│              │                  │              │
│  Local reads │                  │  Local reads │
│  Local writes│                  │  Local writes│
└──────────────┘                  └──────────────┘
         ▲                                 ▲
         │ US traffic                      │ EU traffic
    Route 53 latency-based routing
```

**How it works**: Every region accepts both reads and writes. Changes are replicated asynchronously between regions.

**Pros**: Low latency for all users, no single point of failure, full utilization of all regions.
**Cons**: Conflict resolution needed (two users update the same data in different regions), eventual consistency between regions, complex operations.

**Best for**: Global applications where latency matters (chat, social media, gaming).

### Pattern 3: Active-Active with Regional Write Ownership

```
┌──────────────┐                  ┌──────────────┐
│  us-east-1   │◄── async repl ──►│  eu-west-1   │
│  (ACTIVE)    │                  │  (ACTIVE)     │
│              │                  │              │
│  Writes: US  │                  │  Writes: EU  │
│  users only  │                  │  users only  │
│  Reads: All  │                  │  Reads: All  │
└──────────────┘                  └──────────────┘

User data is "homed" to one region based on registration.
Alice (US user) → writes always go to us-east-1.
Bob (EU user)   → writes always go to eu-west-1.
Both can READ from any region (local replica).
```

**How it works**: Each user's data has a "home region" where all writes go. All regions replicate and serve reads. This eliminates write conflicts entirely — each piece of data has exactly one write authority.

**Pros**: No conflict resolution needed, low read latency globally, GDPR-friendly.
**Cons**: Write latency for users traveling outside their home region, more complex routing logic.

**Best for**: Applications with clear user-to-region mapping (social media, messaging, SaaS platforms).

### Pattern 4: Follow-the-Sun

```
Business hours in US   → us-east-1 is active, eu-west-1 is passive
Business hours in EU   → eu-west-1 is active, us-east-1 is passive
Switchover: Automated at timezone boundaries
```

**Best for**: Enterprise SaaS where usage patterns follow business hours.

---

## Active-Passive vs Active-Active

| Dimension | Active-Passive | Active-Active |
|---|---|---|
| **Write latency** | Low (one region) | Low everywhere (local writes) |
| **Read latency** | High for remote users | Low everywhere (local reads) |
| **Consistency** | Strong (single writer) | Eventually consistent |
| **Conflict resolution** | Not needed | Required (LWW, CRDTs, or region ownership) |
| **Failover time** | 30-60 seconds (DNS TTL) | Near-instant (already active) |
| **Resource utilization** | ~50% (passive is idle) | ~90% (all regions active) |
| **Operational complexity** | Low | High |
| **Cost efficiency** | Low (paying for idle capacity) | High (all capacity utilized) |
| **Data sovereignty** | Hard (all data in one region) | Natural (data lives in local region) |
| **Best for** | Simple apps, < 100K users globally | Global apps, millions of users |

**Interview talking point**:
> *"I'd choose active-active with regional write ownership. Each user's data is homed to the nearest region. US users write to us-east-1, EU users write to eu-west-1. All regions replicate and serve reads. This gives us low-latency reads globally, GDPR compliance by design, and eliminates write conflicts entirely — each piece of data has exactly one write authority."*

---

## DNS-Based Global Routing

### Route 53 Routing Policies

| Policy | How It Works | Use Case |
|---|---|---|
| **Latency-based** | Routes to the region with lowest latency for the user | Default for multi-region apps |
| **Geolocation** | Routes based on user's geographic location | GDPR compliance (EU traffic → EU region) |
| **Weighted** | Distributes traffic by percentage (80/20) | Canary deployments, gradual migration |
| **Failover** | Routes to secondary when primary health check fails | Active-passive DR |
| **Multi-value answer** | Returns multiple healthy endpoints | Simple load distribution |

### Health Check + Failover

```
Route 53 Configuration:
  Primary:   us-east-1  (health check every 10 seconds)
  Secondary: eu-west-1  (health check every 10 seconds)
  
  Health check: HTTPS GET /health → expect 200 OK
  Failure threshold: 3 consecutive failures → mark unhealthy
  
  Failover sequence:
    T+0s:  us-east-1 starts failing health checks
    T+30s: 3 consecutive failures → Route 53 marks us-east-1 unhealthy
    T+30s: DNS responses start returning eu-west-1 endpoints
    T+30s to T+330s: DNS propagation (TTL: 60-300 seconds)
    
  Total failover time: 30-330 seconds depending on DNS TTL
  
  Optimization: Set DNS TTL to 60 seconds for faster failover
  Tradeoff: Lower TTL = more DNS queries = slightly higher latency
```

### Client-Side Failover (Faster)

```
Mobile app / SDK approach:
  1. App receives list of endpoints: [us-east-1, eu-west-1, ap-northeast-1]
  2. App connects to nearest (latency-based)
  3. If connection fails → immediately try next endpoint
  4. Failover time: < 5 seconds (no DNS propagation delay)
  
  Used by: WhatsApp, Discord, Slack (SDK-level failover)
```

---

## Cross-Region Replication Strategies

### Asynchronous Replication (Most Common)

```
Write in us-east-1 → committed locally → replicated to eu-west-1 asynchronously

Replication lag: 100-500ms (cross-continent network)
Consistency: Eventually consistent between regions
Data loss on failover: Up to 500ms of writes (RPO = 500ms)

Used by: DynamoDB Global Tables, Cassandra multi-DC, CockroachDB
```

### Synchronous Replication (Strongest Consistency)

```
Write in us-east-1 → replicated to eu-west-1 → both commit → acknowledge

Replication lag: 0 (synchronous)
Consistency: Strong (linearizable across regions)
Data loss on failover: Zero (RPO = 0)
Write latency penalty: +100-200ms per write (must wait for cross-region round trip)

Used by: Google Spanner (TrueTime), CockroachDB (optional)
```

### Semi-Synchronous Replication

```
Write in us-east-1 → replicated to eu-west-1 → eu-west-1 acknowledges receipt
→ us-east-1 commits → eu-west-1 applies asynchronously

Compromise: Data is durable in 2 regions before client gets response
But: eu-west-1 may not have applied the write yet (can't read it there)
RPO: Near-zero (data is on disk in both regions)
```

### Replication Strategy Decision Framework

| If you need... | Choose... | Tradeoff |
|---|---|---|
| Zero data loss, global consistency | Synchronous | +100-200ms write latency |
| Low write latency, acceptable small data loss | Asynchronous | 100-500ms replication lag |
| Near-zero data loss, moderate latency | Semi-synchronous | +50-100ms write latency |
| Per-operation flexibility | Tunable (like Cassandra CL) | Operational complexity |

---

## Conflict Resolution in Active-Active

When two regions accept writes simultaneously, conflicts can occur:

### The Conflict Scenario

```
T1: Alice in US updates profile name to "Alice Smith" (us-east-1)
T1: Alice in EU (traveling) updates profile name to "Alice Jones" (eu-west-1)

Both writes succeed locally. When replication happens:
  us-east-1 has "Alice Smith"
  eu-west-1 has "Alice Jones"
  → CONFLICT: Which one wins?
```

### Resolution Strategy 1: Last-Writer-Wins (LWW)

```
Each write includes a timestamp.
On conflict: Higher timestamp wins.

Alice in US: ("Alice Smith", T=1711234567.001)
Alice in EU: ("Alice Jones", T=1711234567.003)

Resolution: "Alice Jones" wins (later timestamp).

Problem: Clock skew between regions can cause "wrong" winner.
Mitigation: Use NTP-synchronized clocks (AWS Time Sync: < 1ms accuracy).

Best for: Simple fields (name, email, avatar) where "latest wins" is acceptable.
Used by: DynamoDB Global Tables, Cassandra.
```

### Resolution Strategy 2: CRDTs (Conflict-Free Replicated Data Types)

```
Designed to merge automatically without conflicts:

Counter CRDT:
  us-east-1: like_count += 1 (local state: {us: 5, eu: 0})
  eu-west-1: like_count += 1 (local state: {us: 0, eu: 3})
  Merge: like_count = 5 + 3 = 8 (both increments preserved!)

Set CRDT (Add-only):
  us-east-1: tags.add("funny")
  eu-west-1: tags.add("viral")
  Merge: tags = {"funny", "viral"} (union — both additions preserved!)

Best for: Counters (likes, views), sets (tags, followers), flags.
Used by: Redis CRDTs, Riak.
```

### Resolution Strategy 3: Application-Level Resolution

```
On conflict detection:
  1. Store BOTH versions (siblings)
  2. Next read: Application resolves (pick one, merge, or ask the user)

Amazon shopping cart approach:
  If two regions add different items to cart → UNION (keep both)
  If two regions update quantity → use higher value (conservative)
  
Best for: Complex business logic where automatic resolution isn't sufficient.
Used by: Amazon (shopping cart), Notion (collaborative editing).
```

### Resolution Strategy 4: Regional Write Ownership (Avoid Conflicts)

```
User data homed to one region:
  user_id prefix encodes home region:
    "us_alice_123" → all writes for Alice go to us-east-1
    "eu_bob_456"   → all writes for Bob go to eu-west-1
  
  Alice travels to Europe:
    Read: Local (eu-west-1 replica) — fast
    Write: Routed to us-east-1 (home region) — +100ms latency
    
  No conflicts possible — single write authority per user.
  
Best for: User-centric data (profiles, messages, settings).
Limitation: Write latency increases when user is far from home region.
```

---

## Data Sovereignty & GDPR Compliance

### Requirements

```
GDPR mandates:
  1. EU user data must be processable within the EU
  2. User can request deletion of all their data (Right to Erasure)
  3. User can request export of all their data (Right to Portability)
  4. Data transfers outside EU require legal safeguards

Practical implications for system design:
  - EU users' data must be stored in eu-west-1 (or another EU region)
  - Cross-region replication of personal data needs legal basis
  - Must track where every piece of user data lives
  - Must be able to delete all data for a user across all systems
```

### Geographic Sharding for Compliance

```
User registration determines home region:
  IP geolocation + explicit country selection

user_id encoding:
  EU users: "eu_{uuid}" → shard to eu-west-1
  US users: "us_{uuid}" → shard to us-east-1
  AP users: "ap_{uuid}" → shard to ap-northeast-1

Data classification:
  PUBLIC data (tweets, public profile): Replicated globally (legal basis: legitimate interest)
  PRIVATE data (DMs, email, phone, address): NEVER leaves home region
  DERIVED data (recommendations, analytics): Aggregated, anonymized before cross-region
```

### Implementation Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    DATA SOVEREIGNTY ARCHITECTURE              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  eu-west-1 (EU Data Sovereignty Zone)                        │
│  ┌──────────────────────────────────┐                        │
│  │  EU Users' Private Data          │ ← NEVER replicated out │
│  │  (DMs, email, phone, address)    │                        │
│  ├──────────────────────────────────┤                        │
│  │  EU Users' Public Data           │ → Replicated globally  │
│  │  (tweets, public profile, bio)   │   (read-only replicas) │
│  └──────────────────────────────────┘                        │
│                                                              │
│  us-east-1 (US Region)                                       │
│  ┌──────────────────────────────────┐                        │
│  │  US Users' Private Data          │ ← Stays in US          │
│  │  US Users' Public Data           │ → Replicated globally  │
│  │  EU Public Data (read replica)   │ ← Read-only copy       │
│  └──────────────────────────────────┘                        │
│                                                              │
│  US user reads EU user's tweet: Local replica (fast)          │
│  US user sends DM to EU user: Routed to eu-west-1            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Geographic Sharding

### User-Based Geographic Sharding

```
Shard by user's home region (determined at registration):

Routing layer:
  GET /api/users/eu_alice_123/profile
    → Extract prefix "eu" → route to eu-west-1

  POST /api/users/eu_alice_123/messages
    → Extract prefix "eu" → route to eu-west-1 (write to home region)

Benefits:
  - All of a user's data co-located (no cross-region JOINs)
  - GDPR compliance by design
  - Single write authority (no conflicts)
  
Challenges:
  - Cross-region social interactions (US user messages EU user)
  - User migration (user moves from US to EU)
  - Uneven user distribution across regions
```

### Cross-Region Interaction Pattern

```
Alice (US) sends message to Bob (EU):

Option A: Route write to recipient's home region
  Alice → us-east-1 API → route to eu-west-1 → store in Bob's partition
  Latency: +100ms (cross-region write)
  Consistency: Strong for Bob's inbox
  
Option B: Store in sender's region, replicate asynchronously
  Alice → us-east-1 → store in Alice's outbox → async replicate to eu-west-1
  Latency: Low for Alice (local write)
  Bob sees message: 100-500ms later (replication lag)
  
WhatsApp approach: Option A (message stored in recipient's region)
Twitter approach: Option B (tweet stored in author's region, fanout async)
```

---

## Cross-Region Latency & Physics

### The Speed of Light Constraint

Light travels at ~200,000 km/s in fiber optic cable. This is a **physical constant** — no engineering can overcome it.

```
Minimum round-trip times (fiber optic):
  New York ↔ London:      ~56ms  (5,600 km)
  New York ↔ Tokyo:       ~130ms (13,000 km)
  London ↔ Singapore:     ~100ms (10,000 km)
  US East ↔ US West:      ~40ms  (4,000 km)
  
  These are MINIMUM times. Real-world adds:
    Router hops:         +5-10ms
    TCP handshake:       +1 RTT
    TLS handshake:       +1-2 RTTs
    Application logic:   +5-50ms
  
  Practical latency: ~2x minimum RTT
```

### Implications for System Design

```
Synchronous cross-region call:
  API in us-east-1 calls DB in eu-west-1
  Latency: ~120ms per DB query × 3 queries = 360ms
  → Unacceptable for user-facing requests

Solution: Data locality
  API and DB in the same region
  Cross-region only for replication (async, not in request path)
  
Rule: NEVER put a cross-region call in the user's critical path.
  User request → local API → local DB → local cache → response
  Cross-region happens in the background (replication, sync)
```

---

## Regional Failover & Disaster Recovery

### Automated Failover Sequence

```
Timeline of a regional failure:

T+0s:    us-east-1 starts experiencing issues
T+10s:   Route 53 health checks begin failing
T+30s:   3 consecutive failures → Route 53 marks us-east-1 unhealthy
T+30s:   Route 53 starts returning eu-west-1 in DNS responses
T+90s:   Most clients have refreshed DNS (TTL = 60s)
T+120s:  95% of traffic now hitting eu-west-1

Data state in eu-west-1:
  Async replication lag: Last 200-500ms of writes may be lost
  RPO (Recovery Point Objective): ~500ms of data
  RTO (Recovery Time Objective): ~90 seconds
```

### Failover Testing (Game Days)

```
Netflix Chaos Engineering approach:
  1. Quarterly: Simulate regional failure during business hours
  2. Route 53 health check → force-fail primary region
  3. Verify: Traffic routes to secondary within SLA
  4. Verify: No data loss beyond RPO
  5. Verify: Application functions correctly in secondary
  6. Failback: Restore primary, verify data sync

Without regular testing:
  "Your disaster recovery plan is just a theory
   until you've tested it under realistic conditions."
```

### Failback Process

```
After us-east-1 recovers:

1. Verify us-east-1 is healthy (all services, databases, caches)
2. Replay any writes that happened during failover:
   eu-west-1 received writes while us-east-1 was down
   These must be replicated back to us-east-1
3. Verify data consistency across regions
4. Gradually shift traffic back (10% → 50% → 100%)
5. Monitor for issues during transition
6. Restore normal routing policies
```

---

## Real-World System Examples

### Netflix — Active-Active Multi-Region

```
Regions: us-east-1, us-west-2, eu-west-1
Architecture: Active-active (all regions serve traffic)
Database: Cassandra (multi-DC replication)
Cache: EVCache (region-local)
Routing: Zuul gateway with region-aware routing
Failover: < 7 minutes for full regional evacuation
Testing: Regularly disables entire regions during business hours
```

### DynamoDB Global Tables — Active-Active

```
Multi-region, multi-active replication
Each region accepts reads and writes
Replication lag: Typically < 1 second
Conflict resolution: Last-Writer-Wins (LWW) by default
Consistency: Eventually consistent across regions
Strongly consistent reads: Only within the local region
```

### Google Spanner — Synchronous Multi-Region

```
Globally consistent (linearizable) across all regions
Uses TrueTime (atomic clocks + GPS) for global ordering
Write latency: Higher (must wait for cross-region consensus)
Read latency: Can be local (with bounded staleness reads)
Cost: Premium ($0.90/node-hour)
Best for: Financial systems requiring global consistency
```

### Slack — Active-Active with Cell Architecture

```
Each "cell" is a complete deployment in a region
Users assigned to cells based on workspace
Cross-cell communication for shared channels
Failover: Cell-level, not region-level (more granular)
```

---

## Deep Dive: Applying Multi-Region to Popular Problems

### WhatsApp / Messaging System

```
Home region per user (based on phone number country code):
  +1 (US) → us-east-1
  +44 (UK) → eu-west-1
  +81 (Japan) → ap-northeast-1

Message storage: In recipient's home region
  Alice (US) sends to Bob (UK): Message stored in eu-west-1
  
Presence (online/offline): Region-local Redis
  Cross-region presence: Async sync every 5 seconds
  
Group chats: Stored in the region of the group creator
  Members in other regions: Read from local replicas
  
Failover: Client has backup server list → tries next region
```

### Twitter / Social Feed

```
Tweet storage: Author's home region
  @alice (US) tweets → stored in us-east-1
  
Timeline generation:
  Fanout on write to home region's timeline cache
  Cross-region: Async replication of tweet to other regions
  
EU user follows US user:
  EU user's timeline cache (eu-west-1) populated from replicated tweets
  Slight delay (100-500ms) for cross-region tweets vs same-region tweets
  
Search: Region-local Elasticsearch index
  Cross-region tweets indexed asynchronously
  Search results may be slightly behind for cross-region content
```

### E-Commerce / Booking System

```
Product catalog: Replicated to all regions (read-heavy, rarely changes)
Inventory: Managed per-region or centrally (depends on fulfillment model)
  
Flash sale scenario:
  If inventory is global: All checkout goes to one region (bottleneck)
  If inventory is per-region: Pre-allocate stock per region
    us-east-1: 1,000 units
    eu-west-1: 500 units
    ap-northeast-1: 300 units
    
  If eu-west-1 sells out: Can redirect to us-east-1 (cross-region checkout)
  Tradeoff: Higher latency for overflow vs over-provisioning inventory
```

---

## Interview Talking Points & Scripts

### Opening Statement

> *"I'd deploy active-active across 3 regions: us-east-1, eu-west-1, and ap-northeast-1. Each region runs the full stack. Route 53 latency-based routing sends users to the nearest region. Reads are always local — 15ms. Writes go to the user's home region. Cross-region replication is asynchronous with 100-500ms lag, which is acceptable for social content but not for financial transactions."*

### Data Sovereignty

> *"For GDPR compliance, I'd use geographic sharding with a user_id prefix encoding the home region. EU users' private data — DMs, email, phone number — never leaves eu-west-1. Public data like tweets and profile bios are replicated globally since they're publicly visible anyway. This gives us compliance by design rather than as an afterthought."*

### Conflict Resolution

> *"For active-active writes, I'd use regional write ownership — each user's data has a single home region, eliminating write conflicts entirely. For the rare case of shared mutable state like counters (likes, views), I'd use CRDTs that merge automatically. For simple profile fields, last-writer-wins with NTP-synchronized timestamps is sufficient — clock skew is under 1ms with AWS Time Sync."*

### Failover

> *"If us-east-1 goes down, Route 53 health checks detect it within 30 seconds and start routing traffic to eu-west-1. With a DNS TTL of 60 seconds, 95% of traffic fails over within 2 minutes. The data loss window is the async replication lag — about 500ms of writes. For our messaging app, this means potentially losing the last 500ms of messages, which we'd recover from Kafka replay once the region comes back."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd deploy in multiple regions for high availability"
**Why it's wrong**: Vague. Doesn't specify active-active vs active-passive, how data is replicated, or how conflicts are handled.
**Better**: Specify the pattern, replication strategy, and conflict resolution mechanism.

### ❌ Mistake 2: Making synchronous cross-region calls in the request path
**Why it's wrong**: Adding 100-200ms to every user request. Speed of light is a physical constant.
**Better**: Keep all request-path calls within the same region. Cross-region only for background replication.

### ❌ Mistake 3: Ignoring conflict resolution in active-active
**Why it's wrong**: Two regions accepting writes will produce conflicts. Ignoring this means data corruption.
**Better**: Choose LWW, CRDTs, or regional write ownership and explain the tradeoff.

### ❌ Mistake 4: Not considering GDPR / data sovereignty
**Why it's wrong**: For any system with international users, data residency is a legal requirement, not an optional feature.
**Better**: Mention geographic sharding, classify data as public/private, and explain which data can be replicated cross-region.

### ❌ Mistake 5: Assuming instant failover
**Why it's wrong**: DNS propagation takes 30-300 seconds. Some data may be lost.
**Better**: Quantify RTO (30-120 seconds) and RPO (100-500ms of writes), and explain the DNS TTL tradeoff.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│        MULTI-REGION & GEO-DISTRIBUTION CHEAT SHEET           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PATTERNS:                                                   │
│    Active-Passive: Simple, strong consistency, wasted capacity│
│    Active-Active: Low latency everywhere, needs conflict res │
│    Regional Write Ownership: Best of both (no conflicts!)    │
│                                                              │
│  ROUTING: Route 53 latency-based → nearest region            │
│  FAILOVER: 30-120 seconds (DNS TTL dependent)                │
│  REPLICATION LAG: 100-500ms (async), 0ms (sync, costly)     │
│                                                              │
│  CONFLICT RESOLUTION:                                        │
│    LWW: Simple fields (name, email) — last timestamp wins    │
│    CRDTs: Counters, sets — auto-merge, no conflicts          │
│    Regional ownership: Each user's data has one writer        │
│                                                              │
│  GDPR / DATA SOVEREIGNTY:                                    │
│    Geographic sharding by user's home region                  │
│    Private data (DMs, email) NEVER leaves home region        │
│    Public data (tweets, profiles) replicated globally         │
│    user_id prefix encodes region                              │
│                                                              │
│  GOLDEN RULES:                                               │
│    NEVER put cross-region calls in the request path           │
│    Test failover quarterly (Game Days)                        │
│    Design for 100-500ms replication lag                       │
│    Classify data: public (replicate) vs private (stay home)  │
│                                                              │
│  LATENCY (speed of light):                                   │
│    Same region: 1-5ms                                        │
│    Cross-continent: 80-200ms (physical minimum)              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 1: CAP Theorem** — The consistency-availability tradeoff across regions
- **Topic 4: Consistency Models** — Eventual consistency in multi-region systems
- **Topic 13: Replication** — Cross-region replication strategies
- **Topic 28: Availability & Disaster Recovery** — RPO/RTO for regional failures
- **Topic 36: Distributed Transactions & Sagas** — Cross-region transaction patterns

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
