# Microsoft Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Microsoft's engineering blog, Azure architecture docs, Build/Ignite talks, and open-source projects. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Microsoft Engineering Blog, Azure Architecture Center, Build/Ignite conferences, GitHub Engineering Blog, Teams Engineering Blog

---

## Table of Contents

1. [Azure Cosmos DB — Multi-Model Global Database](#1-cosmos-db)
2. [Microsoft Teams — Real-Time Communication at Enterprise Scale](#2-microsoft-teams)
3. [Azure Service Fabric — Microservice Platform](#3-service-fabric)
4. [Bing Search — Web-Scale Search Engine](#4-bing-search)
5. [GitHub — Scaling Git to 100M+ Repositories](#5-github)
6. [Azure Event Hubs — Event Streaming (Kafka-Compatible)](#6-event-hubs)
7. [VS Code — Electron Architecture Decisions](#7-vs-code)
8. [Xbox Live — Gaming at Scale](#8-xbox-live)
9. [Azure Functions — Serverless Platform](#9-azure-functions)
10. [OneDrive/SharePoint — File Storage at Scale](#10-onedrive-sharepoint)
11. [TypeScript — Adding Types to JavaScript](#11-typescript)
12. [Azure Front Door — Global Load Balancing & CDN](#12-azure-front-door)
13. [FASTER — Concurrent Key-Value Store](#13-faster)
14. [Microsoft Identity Platform (Entra ID)](#14-identity-platform)
15. [Orleans — Virtual Actor Framework](#15-orleans)

---

## 1. Azure Cosmos DB — Multi-Model Global Database

**Source**: "Azure Cosmos DB: Design Philosophy" + Build conference talks

### Problem
Microsoft needed a globally distributed database for Azure services (Office 365, Xbox, Skype) that could handle multiple data models (document, key-value, graph, column-family) with tunable consistency and single-digit millisecond latency anywhere in the world.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Distribution** | Active-active multi-region (write to any region) | Single-region with replication | Global apps need local writes; cross-region writes add unacceptable latency |
| **Consistency** | 5 tunable levels (Strong, Bounded Staleness, Session, Consistent Prefix, Eventual) | Fixed strong or eventual | Different use cases need different consistency; one size doesn't fit all |
| **Data model** | Multi-model (document, key-value, graph, table) on one engine | Separate engines per model | One operational platform; customers choose API without changing database |
| **Partitioning** | Automatic horizontal partitioning by partition key | Manual sharding | Eliminates sharding complexity for developers |
| **SLA** | 99.999% availability SLA (with multi-region) | 99.99% | Five-nines for mission-critical enterprise workloads |
| **Throughput** | Request Units (RU) — abstract throughput currency | Raw QPS | RU normalizes across operation types (read, write, query complexity) |

### The 5 Consistency Levels (Unique to Cosmos DB)

| Level | Guarantee | Latency | Use Case |
|-------|-----------|---------|----------|
| **Strong** | Linearizable reads (always latest write) | Highest | Financial transactions |
| **Bounded Staleness** | Reads lag behind writes by at most K versions or T seconds | High | Leaderboards, trending |
| **Session** | Within a session: read-your-writes, monotonic reads | Medium | Most apps (default) |
| **Consistent Prefix** | Reads never see out-of-order writes | Low | Social feeds, timelines |
| **Eventual** | Reads may see any previous write | Lowest | Counters, like counts |

### Tradeoff
- ✅ Active-active global writes with tunable consistency — unique in the market
- ✅ 5 consistency levels let developers choose per use case
- ✅ Multi-model reduces operational overhead (one DB for documents, graphs, key-value)
- ✅ 99.999% SLA — strongest in the industry
- ❌ Expensive (Request Unit pricing can be costly at scale)
- ❌ Vendor lock-in (Cosmos DB API, even with MongoDB/Cassandra compatibility modes)
- ❌ Partition key choice is critical and hard to change later
- ❌ Complex pricing model (RUs are confusing)

### Interview Use
> "For a globally distributed database with tunable consistency, I'd consider Cosmos DB — it offers 5 consistency levels from strong to eventual, active-active multi-region writes, and a 99.999% SLA. The session consistency level (read-your-writes within a session) is perfect for most user-facing applications. The tradeoff is cost — RU-based pricing is expensive at very high scale compared to self-managed databases."

---

## 2. Microsoft Teams — Real-Time Communication

**Source**: "How Microsoft Teams Handles 250+ Million Users" + Ignite talks

### Problem
Teams must support 250M+ monthly active users with chat, video calls, file sharing, app integrations, and enterprise compliance — all in real-time. Built on Azure, integrating with Office 365 ecosystem.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Microservices on Azure Service Fabric | Monolith or Kubernetes | Service Fabric was Azure's native microservice platform; deep integration |
| **Real-time** | SignalR (WebSocket abstraction) | Raw WebSocket, SSE | SignalR handles fallback (WebSocket → SSE → long polling) automatically |
| **Message storage** | Azure Cosmos DB (chat messages) + Azure SQL (metadata) | Single database | Cosmos for global, low-latency chat; SQL for relational metadata with transactions |
| **File sharing** | OneDrive/SharePoint (files stored in user's OneDrive) | Separate file storage | Reuse existing Office 365 storage; files accessible outside Teams too |
| **Video** | Custom media stack (Azure Communication Services) | WebRTC only | Enterprise features: recording, transcription, large meetings (1000+ participants) |
| **Compliance** | Built-in: eDiscovery, retention policies, DLP | Bolt-on compliance | Enterprise customers require compliance from day one; can't retrofit |

### Scaling Challenges Solved

| Challenge | Solution |
|-----------|---------|
| **Chat at scale** | Cosmos DB with session consistency; partition by chat_id |
| **Presence for 250M users** | Distributed presence service; updates only to visible contacts |
| **Meetings with 1000+ people** | Media server mixing (not peer-to-peer); selective forwarding |
| **Tenant isolation** | Per-tenant data isolation in multi-tenant infrastructure |
| **Search** | Azure Cognitive Search with per-tenant indexing |
| **Notifications** | Azure Notification Hubs (push) + SignalR (real-time) |

### Tradeoff
- ✅ Deep Office 365 integration (files in OneDrive, calendar in Outlook, identity in Entra ID)
- ✅ Enterprise compliance built-in (critical for regulated industries)
- ✅ SignalR handles protocol fallback automatically
- ❌ Azure lock-in (deeply coupled to Azure services)
- ❌ Complexity of integrating with entire Office 365 ecosystem
- ❌ Performance challenges at launch (Teams was initially slower than Slack)

### Interview Use
> "For an enterprise messaging platform, I'd use a microservice architecture with SignalR for real-time communication (it handles WebSocket → SSE → long polling fallback automatically). Chat messages in a globally distributed database (Cosmos DB) with session consistency. Files stored in the user's existing cloud storage (like OneDrive) rather than a separate file system. Teams' key lesson: enterprise compliance (eDiscovery, DLP, retention) must be built in from day one, not bolted on."

---

## 3. Azure Service Fabric — Microservice Platform

**Source**: "Service Fabric: A Distributed Systems Platform" + Build talks

### Problem
Microsoft needed a platform to run their own massive services (Azure SQL, Cosmos DB, Cortana, Skype, Power BI) as reliable microservices. Kubernetes didn't exist yet. Needed stateful services, not just stateless containers.

### What They Built
**FASTER** — a key-value store from Microsoft Research that achieves 160M+ ops/sec on a single machine by using a combination of lock-free hash index, hybrid log (in-memory + SSD), and epoch-based memory management.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Hash index** | Lock-free (compare-and-swap) | Lock-based hash table | Lock-free enables 160M+ ops/sec; no contention between threads |
| **Storage** | Hybrid log: hot in memory, warm on SSD, cold compacted | All in-memory (Redis) | Handles datasets larger than memory; SSD for warm data at low cost |
| **Updates** | In-place updates (no copy-on-write) | Copy-on-write (RocksDB) | In-place is faster for point updates; avoids write amplification |
| **Concurrency** | Epoch-based memory management | Garbage collection | Epochs allow safe concurrent access without GC pauses |

### Tradeoff
- ✅ 160M+ ops/sec on a single machine (10× Redis throughput)
- ✅ Handles datasets larger than memory (hybrid log)
- ❌ More complex API than Redis (epoch-based sessions)
- ❌ Research project — smaller community and ecosystem than Redis
- ❌ Optimized for point lookups; limited range query support

### Interview Use
> "For extreme single-machine throughput (hundreds of millions of ops/sec), lock-free data structures like Microsoft's FASTER achieve 10× Redis performance by eliminating contention. The hybrid log (memory + SSD) handles datasets larger than RAM. For most applications, Redis is sufficient — FASTER is for specialized high-throughput scenarios."

---

## 14. Microsoft Identity Platform (Entra ID)

**Source**: "Scaling Azure Active Directory" + Ignite talks

### Problem
Microsoft's identity platform (formerly Azure AD, now Entra ID) authenticates 1.2 billion+ identities, processes 76 billion auth requests/day, and must be available 99.999%+ — because if identity goes down, nothing works.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Globally distributed, active-active across 30+ datacenters | Active-passive | Identity must be fast and available everywhere; latency-sensitive (every API call starts with auth) |
| **Token format** | JWT (stateless, self-contained) | Session-based (stateful) | JWT validates without a round trip to the auth server; critical for microservices |
| **Token caching** | Client-side token caching (MSAL library) | Server-side session store | Reduce auth server load by 90%+; tokens are cached and reused until expiry |
| **Protocol** | OAuth 2.0 + OpenID Connect (industry standards) | Proprietary auth | Standards enable integration with any platform; avoid lock-in |
| **MFA** | Risk-adaptive (require MFA only for risky sign-ins) | Always require MFA | Balances security with user experience; low-risk sign-ins skip MFA |
| **Availability** | 99.999% SLA (5 nines) | 99.99% | Identity is the foundation — everything depends on it |

### Tradeoff
- ✅ 76 billion auth requests/day with 99.999% availability
- ✅ JWT tokens are stateless — no server-side session to look up
- ✅ Risk-adaptive MFA balances security and UX
- ❌ JWT can't be revoked until expiry (mitigated by short-lived tokens + refresh tokens)
- ❌ Global distribution adds replication complexity
- ❌ Token size (JWTs can be large with many claims)

### Interview Use
> "For authentication at scale, I'd use JWT tokens (stateless, self-contained) with short expiry (1 hour) + refresh tokens (7 days). This eliminates the need for a centralized session store — each service validates the JWT locally. Microsoft's Entra ID processes 76B auth requests/day using this model. The tradeoff: JWTs can't be instantly revoked (must wait for expiry), mitigated by short TTLs and a token revocation list for emergencies."

---

## 15. Orleans — Virtual Actor Framework

**Source**: "Orleans: Distributed Virtual Actors for Programmability and Scalability" (Microsoft Research)

### Problem
Building distributed, stateful applications (gaming, IoT, digital twins) is hard. Developers must handle threading, concurrency, state distribution, and failure recovery. Need a simpler programming model.

### What They Built
**Orleans** — a virtual actor framework. Actors (called "grains") are the unit of computation. Each grain has state, runs single-threaded (no locks needed), and is automatically distributed across the cluster.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Programming model** | Virtual actors (grains) — always exist, activated on demand | Explicit actor lifecycle management | Developers don't manage actor creation/destruction; the runtime handles it |
| **Concurrency** | Single-threaded per grain (turn-based) | Multi-threaded with locks | No locks, no deadlocks, no race conditions — dramatically simpler |
| **State** | Built-in persistent state (pluggable storage: Azure Table, SQL, etc.) | External state management | State co-located with compute; automatic persistence and recovery |
| **Distribution** | Automatic — runtime places grains on servers, migrates on failure | Manual placement | Developers think in terms of grains, not servers |
| **Communication** | RPC-style method calls (not message passing) | Message queues (Akka-style) | Method calls are natural for C# developers; compiler checks types and signatures |

### Orleans vs Akka

| Dimension | Orleans | Akka |
|-----------|---------|------|
| **Language** | C# (.NET) | Scala/Java (JVM) |
| **Actor lifecycle** | Virtual (always exist, activated on demand) | Explicit (create/destroy) |
| **Communication** | Typed method calls | Untyped messages |
| **State** | Built-in persistence | External (Akka Persistence) |
| **Best for** | .NET ecosystem, Azure | JVM ecosystem |

### Real-World Usage
- **Halo** (Xbox): Each game session is a grain; game state persisted automatically
- **Azure IoT Hub**: Each device is a virtual actor (digital twin)
- **Skype**: Call management using Orleans grains

### Tradeoff
- ✅ Dramatically simpler distributed programming (no locks, no explicit lifecycle)
- ✅ Automatic distribution and recovery
- ✅ Single-threaded grains eliminate concurrency bugs
- ❌ .NET-specific (not language-agnostic like Kubernetes)
- ❌ Actor model doesn't fit all problems (batch processing, heavy computation)
- ❌ Inter-grain communication has network overhead (even if on same machine)

### Interview Use
> "For stateful distributed systems (gaming sessions, IoT digital twins, chat rooms), the actor model simplifies concurrency — each actor is single-threaded, no locks needed. Microsoft's Orleans uses 'virtual actors' that always exist and are activated on demand. Halo uses Orleans for game sessions. The lesson: when state is per-entity (per-game, per-device, per-user), the actor model maps naturally and eliminates concurrency complexity."

---

## 🎯 Quick Reference: Microsoft's Key Decisions

### Azure Services
| Service | Key Decision | Why |
|---------|-------------|-----|
| Cosmos DB | 5 tunable consistency levels | Different use cases need different consistency tradeoffs |
| Service Fabric | Built-in stateful services | Co-locate state with compute for lower latency |
| Event Hubs | Kafka wire-protocol compatibility | Existing Kafka clients work; reduces migration friction |
| Azure Functions | Durable Functions (code-based orchestration) | Developers write workflows in code, not JSON |
| Front Door | Split-TCP at edge PoPs | Saves 500ms+ on TLS handshake for distant users |

### Products
| Product | Key Decision | Why |
|---------|-------------|-----|
| Teams | SignalR (WebSocket abstraction) | Automatic fallback: WebSocket → SSE → long polling |
| GitHub | VFS for Git (lazy loading) | 300GB monorepo: clone in minutes, not hours |
| VS Code | Extension Host (separate process) | Buggy extension can't crash the editor |
| Xbox Live | Pre-provision on game launch (not reactive) | 100× spike too fast for reactive scaling |
| OneDrive | Chunked sync + dedup + OT | Bandwidth savings + real-time co-authoring |

### Open Source Impact
| Project | Impact | Lesson |
|---------|--------|--------|
| TypeScript | Most popular language on GitHub | Superset approach = zero migration cost = mass adoption |
| LSP (Language Server Protocol) | Industry standard for editor tooling | N + M instead of N × M implementations |
| Orleans | Virtual actor model for gaming/IoT | Simplify concurrency with single-threaded actors |
| FASTER | 160M+ ops/sec key-value | Lock-free + hybrid log saturates modern hardware |

---

## 🗣️ How to Use Microsoft Examples in Interviews

### Example Sentences
- "Cosmos DB offers 5 consistency levels — I'd use session consistency for most user-facing applications (read-your-writes within a session)."
- "Like VS Code, I'd isolate third-party extensions in a separate process to prevent a buggy plugin from crashing the main application."
- "For Bing-scale search, I'd use multi-stage ranking: fast retrieval → lightweight ML filter → heavy neural model on top candidates."
- "Microsoft Teams uses SignalR for real-time communication — it automatically falls back from WebSocket to SSE to long polling."
- "For file sync like OneDrive, I'd use chunked storage with differential sync — only transfer changed chunks, not the entire file."
- "TypeScript succeeded by being a superset of JavaScript — zero migration cost. The lesson: backward compatibility enables adoption."
- "Azure Front Door's split-TCP optimization saves 500ms+ by terminating TLS at the nearest PoP."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 15 design decisions across Azure, products, open-source, and gaming  
**Status**: Complete & Interview-Ready ✅
**Service Fabric** — a distributed systems platform for deploying and managing microservices. Supports both stateless and stateful services with built-in state management.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Stateful services** | Built-in reliable collections (dictionaries, queues) replicated across nodes | External state (Redis, DB) | Eliminates external dependency; state co-located with compute for low latency |
| **Service model** | Both stateless and stateful services | Stateless only (Kubernetes) | Many services benefit from local state (caching, counters, sessions) |
| **Orchestration** | Built-in service placement, failover, and scaling | Manual placement | Automatic: if a node dies, Service Fabric moves the service + its state to a healthy node |
| **Partitioning** | Built-in partitioning for stateful services | Application-level sharding | Developers define partition scheme; Service Fabric handles routing and rebalancing |
| **Upgrade** | Rolling upgrades with automatic rollback | Blue/green deployment | Rolling upgrades are safer for stateful services; automatic rollback on health check failure |

### Service Fabric vs Kubernetes

| Dimension | Service Fabric | Kubernetes |
|-----------|---------------|------------|
| **Stateful services** | Native (reliable collections) | Requires external state (Redis, DB) |
| **Programming model** | Actors, Reliable Services | Containers (any language) |
| **State management** | Built-in replication | StatefulSets + PVs (bolted on) |
| **Ecosystem** | Microsoft-centric | Massive open-source ecosystem |
| **Adoption** | Azure services (SQL DB, Cosmos) | Industry standard |
| **Best for** | Stateful microservices on Azure | General container orchestration |

### Interview Use
> "For stateful microservices (services that maintain state like counters, caches, or session data), Azure Service Fabric provides built-in replicated state. Unlike Kubernetes where state is external (Redis, DB), Service Fabric co-locates state with compute for lower latency. Microsoft uses it internally for Azure SQL DB, Cosmos DB, and Skype. However, Kubernetes has become the industry standard for most new deployments."

---

## 4. Bing Search — Web-Scale Search

**Source**: "Inside Bing: A Web-Scale Search Engine" + various Microsoft Research papers

### Problem
Index the entire web (hundreds of billions of pages), serve billions of queries/day with < 500ms latency, and rank results using ML models while handling adversarial SEO, spam, and content diversity.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Index** | Inverted index across thousands of servers, hash-partitioned by document | Per-term partitioning | Document partitioning enables parallel query processing across all terms |
| **Ranking** | Multi-stage: fast linear model → expensive neural model (BERT/GPT) | Single-stage ML | Can't run GPT on every result; fast model filters 1000 → 100, neural model re-ranks top 100 |
| **Crawl** | Continuous crawling (prioritized by page importance and freshness) | Periodic batch crawl | News and trending content needs hours-level freshness, not weekly |
| **Freshness** | Hybrid index: main index (weekly rebuild) + supplemental index (hours) | Single index with incremental updates | Supplemental index gives fresh results without expensive full index rebuild |
| **ML integration** | Deep learning for understanding query intent + ranking | Traditional BM25 / TF-IDF | BERT/GPT-class models dramatically improve search relevance; Microsoft integrated GPT into Bing |
| **Spell correction** | ML-based with user click data | Dictionary-based | Click data provides implicit feedback: "users who searched X actually wanted Y" |

### Multi-Stage Ranking
```
Query arrives:
  1. Query understanding (NLP): intent, entities, location, language
  2. Candidate retrieval: inverted index → 10,000 candidate URLs (< 50ms)
  3. Fast ranking (lightweight ML model): 10,000 → 200 (< 100ms)
  4. Deep ranking (BERT/GPT model): 200 → top 10 (< 200ms)
  5. Blending: mix web results + news + images + ads + knowledge panel
  6. Return to user (total < 500ms)
```

### Tradeoff
- ✅ Multi-stage ranking makes deep learning feasible at web scale
- ✅ Hybrid index (main + supplemental) balances freshness with cost
- ✅ GPT integration dramatically improves conversational queries
- ❌ Massive infrastructure cost (thousands of servers for index + ranking)
- ❌ Adversarial: SEO spam constantly evolves to game ranking
- ❌ Deep learning models are expensive to train and serve

### Interview Use
> "For web-scale search, I'd use multi-stage ranking — like Bing's approach. Fast retrieval from inverted index (10K candidates), lightweight ML model (filter to 200), then a heavy neural model (re-rank top 200 to find the best 10). This makes deep learning feasible at scale — you can't run BERT on every page in the index, but you can run it on the top 200. Total query latency < 500ms."

---

## 5. GitHub — Scaling Git at 100M+ Repos

**Source**: GitHub Engineering Blog, "How GitHub Scaled Git" talks

### Problem
GitHub hosts 100M+ repositories. Git operations (clone, push, pull) are I/O intensive. The biggest repos (monorepos like Windows — 300GB!) push Git to its limits.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Distributed filesystem (custom) for Git objects | Single NFS server | Git repos vary from KB to hundreds of GB; need to distribute storage |
| **Large repos** | VFS for Git (virtual filesystem — lazy loading) | Download everything | Windows monorepo (300GB) can't be fully cloned; VFS loads files on demand |
| **Compute** | Stateless API servers + backend Git workers | Monolith | Separate compute from storage; scale API and Git operations independently |
| **Database** | MySQL (sharded) for metadata (users, issues, PRs) | Single DB | Metadata is relational and transactional; shard by repository |
| **Search** | Elasticsearch (code search across all repos) | Simple grep | Full-text code search with language-aware tokenization |
| **Availability** | Multi-datacenter with failover | Single datacenter | GitHub is critical developer infrastructure; outages impact millions |
| **Language** | Ruby on Rails → C for hot paths → Go for new services | Full rewrite | Gradual extraction: Ruby for product development speed, C for Git operations, Go for new microservices |

### VFS for Git (Virtual File System)
- Problem: Windows repo is 300GB, 3.5M files — `git clone` takes hours
- Solution: Clone only metadata; download file contents on first access (lazy loading)
- Result: Clone goes from hours to minutes; developers only download files they touch

### Tradeoff
- ✅ VFS for Git enables massive monorepos (Windows, Office use it)
- ✅ Gradual language migration (Ruby → C → Go) avoids risky rewrites
- ✅ Sharded MySQL for metadata is proven and well-understood
- ❌ Custom storage layer is expensive to maintain
- ❌ VFS for Git requires client-side installation (not standard Git)
- ❌ Large monorepos still push Git's limits (not designed for 300GB repos)

### Interview Use
> "For a platform hosting millions of repositories, I'd shard metadata by repository_id (MySQL) and distribute Git object storage across a custom filesystem. For very large repos, a virtual filesystem approach (lazy loading — only download files on access) reduces clone time from hours to minutes. GitHub uses this for the Windows monorepo (300GB, 3.5M files). The lesson: sometimes the underlying tool (Git) needs to be extended for scale."

---

## 6. Azure Event Hubs — Event Streaming

**Source**: Azure Event Hubs documentation + architecture guides

### Problem
Azure needed a managed event streaming service for IoT, analytics, and event-driven architectures — compatible with Apache Kafka but fully managed.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Kafka compatibility** | Wire-level Kafka protocol support | Proprietary API only | Customers can use existing Kafka clients; reduces migration friction |
| **Partitioning** | Partition-based (like Kafka) | Shard-based (like Kinesis) | Kafka-compatible; partitions map naturally to parallel consumers |
| **Throughput** | Throughput units (auto-scale) | Per-shard fixed capacity | Auto-inflate: scale up automatically during traffic spikes |
| **Retention** | Up to 90 days (vs Kafka's unlimited) | 24 hours (Kinesis default) | Balance between cost and replay needs; 90 days covers most use cases |
| **Integration** | Native Azure integration (Functions, Stream Analytics, Data Explorer) | Generic connectors | Azure-native: trigger Functions on events, pipe to Data Explorer for analytics |
| **Capture** | Auto-capture to Azure Blob/Data Lake | Manual consumer to storage | Built-in archival: every event automatically saved to Blob Storage for compliance |

### Event Hubs vs Kafka vs Kinesis

| Dimension | Event Hubs | Kafka (MSK) | Kinesis |
|-----------|-----------|-------------|---------|
| **Management** | Fully managed | Managed (still configure topics) | Fully managed |
| **Kafka compatible** | ✅ (wire protocol) | ✅ (is Kafka) | ❌ |
| **Throughput** | Auto-scale (throughput units) | Per-partition | Per-shard (1 MB/s) |
| **Retention** | Up to 90 days | Unlimited | Up to 365 days |
| **Best cloud** | Azure | AWS (MSK) | AWS |
| **Auto-capture** | ✅ (Blob/Data Lake) | ❌ (need consumer) | ✅ (Firehose) |

### Interview Use
> "On Azure, I'd use Event Hubs for event streaming — it's Kafka-compatible (existing Kafka clients work), fully managed, and auto-scales throughput. The auto-capture feature automatically archives every event to Blob Storage for compliance and replay. On AWS, the equivalent is MSK (for Kafka compatibility) or Kinesis (for simplicity)."

---

## 7. VS Code — Electron Architecture

**Source**: VS Code engineering blog + "Building VS Code" talks

### Problem
Build a code editor that runs on Windows, Mac, and Linux with a rich extension ecosystem and near-native performance. Must be fast enough for developers who are sensitive to editor latency.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Framework** | Electron (Chromium + Node.js) | Native (Qt, GTK), Web-only | Cross-platform with one codebase; rich web technologies for UI; Node.js for filesystem access |
| **Language** | TypeScript | JavaScript, C++ | Type safety catches bugs; better IDE experience (autocomplete, refactoring) |
| **Architecture** | Multi-process: main process + renderer + extension host | Single process | Extensions run in a separate process — a buggy extension can't crash the editor |
| **Extension isolation** | Extensions run in a dedicated Extension Host process | In-process extensions | Isolation: a slow extension doesn't freeze the UI; crash containment |
| **Language servers** | LSP (Language Server Protocol) — standard protocol for language features | Built-in per-language | LSP: one protocol, community provides servers for every language; VS Code doesn't need to implement each |
| **Remote development** | Separate UI (local) from workspace (remote: SSH, container, WSL) | Everything local | Remote Development: run VS Code UI locally, edit files on a remote server, container, or WSL |

### LSP (Language Server Protocol) — Industry Impact
- **Before LSP**: Each editor needed to implement language features (autocomplete, go-to-definition) for each language. N editors × M languages = N×M implementations.
- **With LSP**: One language server per language, one LSP client per editor. N + M implementations.
- **Result**: LSP became an industry standard. Vim, Emacs, Sublime, and others adopted it. Created by Microsoft for VS Code.

### Tradeoff
- ✅ Cross-platform from day one (Windows, Mac, Linux)
- ✅ Extension ecosystem (50K+ extensions) — richest of any editor
- ✅ LSP created an industry standard for language tooling
- ✅ Remote Development enables editing on any machine
- ❌ Electron uses more memory than native editors (Chromium overhead)
- ❌ Startup time slower than native editors (loading Chromium)
- ❌ Extension host process adds inter-process communication overhead

### Interview Use
> "VS Code's key architectural decisions: (1) extensions run in a separate process for isolation — a buggy extension can't crash the editor, (2) LSP (Language Server Protocol) standardized language tooling across all editors, and (3) Remote Development separates UI from workspace. The lesson for system design: isolate untrusted/third-party code in separate processes, and create protocols/standards for extensibility rather than implementing everything in-house."

---

## 8. Xbox Live — Gaming at Scale

**Source**: "Scaling Xbox Live" GDC talks + Azure gaming blog

### Problem
Xbox Live serves 100M+ gamers with real-time multiplayer matching, leaderboards, achievements, friends lists, and voice chat. Spiky traffic patterns (game launches, weekends, holidays).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Infrastructure** | Azure (migrated from custom data centers) | Own data centers | Azure provides global reach, auto-scaling, and reduces operational burden |
| **Matchmaking** | Server-side matchmaking (not peer-to-peer) | P2P with host migration | Server-side: fairer matching, no host advantage, handles host disconnects |
| **Leaderboards** | Redis sorted sets (real-time) + Azure SQL (historical) | Single database | Redis for < 1ms leaderboard queries; SQL for complex analytics |
| **Session management** | Stateful: player session mapped to specific server | Stateless | Multiplayer games are inherently stateful (game state, player positions) |
| **Scaling** | Auto-scale on game launch events (pre-provision based on pre-orders) | Reactive scaling | Game launches spike 100×; reactive is too slow — pre-provision based on demand prediction |
| **Voice chat** | Azure Communication Services (relay-based) | P2P voice | Relay: works across NATs, consistent quality, can add moderation/recording |

### Tradeoff
- ✅ Azure provides global reach and auto-scaling
- ✅ Server-side matchmaking is fairer than P2P
- ✅ Pre-provisioning handles game launch spikes
- ❌ Server-side multiplayer is more expensive than P2P (server costs for every game session)
- ❌ Latency: server in the middle adds ~20-50ms vs direct P2P
- ❌ Pre-provisioning can waste resources if demand predictions are wrong

### Interview Use
> "For real-time multiplayer gaming, I'd use server-side matchmaking and game hosting rather than P2P — fairer, no host advantage, and works across NATs. Leaderboards use Redis sorted sets for real-time ranking. The biggest challenge is launch-day traffic spikes (100× normal) — pre-provision based on pre-order data rather than reactive scaling. Xbox Live migrated from custom data centers to Azure for global reach and elastic scaling."

---

## 9. Azure Functions — Serverless

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Hosting** | Consumption (scale-to-zero), Premium (pre-warmed), Dedicated | Single plan | Different workloads need different tradeoffs (cost vs cold start) |
| **Triggers** | Event-driven: HTTP, Timer, Blob, Queue, Cosmos DB, Event Hub | HTTP only | Serverless should react to any event, not just HTTP requests |
| **Durable Functions** | Workflow orchestration built into serverless (code-based) | Step Functions (JSON state machine) | Developers write orchestration in code (C#, JS, Python), not JSON |
| **Cold start** | Premium plan: pre-warmed instances (no cold start) | Accept cold starts | Enterprise customers need < 1 second response; cold starts are 3-10 seconds |
| **Language support** | C#, JavaScript, Python, Java, PowerShell, Go | Single language | Azure's enterprise customers use many languages |

### Durable Functions vs AWS Step Functions

| Dimension | Durable Functions | Step Functions |
|-----------|------------------|---------------|
| **Definition** | Code (C#, JS, Python) | JSON (ASL) |
| **Debugging** | Standard debugger | CloudWatch logs |
| **Flexibility** | Full language features | State machine constructs |
| **Local dev** | Full local debugging | Limited local testing |
| **Best for** | Developers who want code-based orchestration | Visual workflow designers |

### Interview Use
> "For serverless workflow orchestration, Azure Durable Functions offers code-based orchestration (write workflows in C#/JS/Python) vs Step Functions' JSON state machines. Code-based is more flexible and debuggable. For simple event processing, any serverless platform (Lambda, Azure Functions) works — the key is the trigger model: react to any event (HTTP, queue, storage, database change)."

---

## 10. OneDrive/SharePoint — File Storage at Scale

**Source**: "Building OneDrive at Scale" + Ignite talks

### Problem
OneDrive stores billions of files for hundreds of millions of users. Must handle sync across devices, real-time co-authoring (Word, Excel), version history, and enterprise compliance (DLP, retention).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Azure Blob Storage (chunked, deduplicated) | Custom storage | Azure Blob for durability (11 nines) and cost optimization |
| **Chunking** | Files split into chunks (4MB); only changed chunks synced | Full file upload/download on any change | For a 1GB file with a small edit, only sync the changed 4MB chunk, not the full 1GB |
| **Deduplication** | Content-addressable: hash(chunk) → store once, reference many | Store every copy | If 1000 users have the same PowerPoint template, store it once |
| **Sync protocol** | Differential sync (track changes, sync deltas) | Full sync each time | Reduces bandwidth dramatically; essential for mobile (limited data) |
| **Co-authoring** | Operational Transformation (OT) for real-time collaboration | Lock file while editing | Multiple users edit simultaneously; changes merge in real-time |
| **Versioning** | Automatic version history (last 500 versions or 30 days) | Manual save-as | Users can recover from accidental deletions or overwrites |

### Tradeoff
- ✅ Chunked sync: only transfer changed portions (saves bandwidth)
- ✅ Deduplication: massive storage savings (enterprise templates, shared files)
- ✅ Real-time co-authoring: multiple users in the same document
- ❌ Chunking adds complexity to upload/download logic
- ❌ Co-authoring conflict resolution is complex (OT algorithm)
- ❌ Version history increases storage (mitigated by dedup)

### Interview Use
> "For a file sync service, I'd use chunked storage (4MB chunks) with differential sync — only transfer the changed chunks, not the entire file. Content-addressable storage (hash-based deduplication) saves storage when multiple users have the same file. For real-time co-authoring, use Operational Transformation (OT) or CRDTs to merge concurrent edits. OneDrive uses all three: chunking, dedup, and OT."

---

## 11. TypeScript — Adding Types to JavaScript

**Source**: "TypeScript: JavaScript that Scales" + Anders Hejlsberg talks

### Problem
JavaScript at scale (large codebases, many developers) leads to bugs that are only caught at runtime. IDE support (autocomplete, refactoring) is limited without type information.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Approach** | Superset of JavaScript (all JS is valid TS) | New language (Dart, CoffeeScript) | Zero migration cost — existing JS code works in TypeScript immediately |
| **Type system** | Structural typing (shape-based, not nominal) | Nominal typing (Java-style class-based) | Structural typing fits JavaScript's dynamic nature; duck typing with safety |
| **Compilation** | Transpile to JavaScript (erasure — types removed at runtime) | New runtime | Works with every existing JS runtime (browsers, Node.js); no new runtime needed |
| **Strictness** | Gradual typing (can mix typed and untyped code) | All-or-nothing | Teams can adopt incrementally; type annotate as you go |
| **Ecosystem** | DefinitelyTyped (community type definitions for JS libraries) | Only typed libraries usable | Community provides types for existing JS libraries; full ecosystem available |

### Tradeoff
- ✅ Zero migration cost: existing JavaScript code is valid TypeScript
- ✅ Gradual adoption: add types incrementally, not all-or-nothing
- ✅ Structural typing fits JavaScript's programming model
- ✅ IDE experience dramatically improves (autocomplete, error detection, refactoring)
- ❌ Compilation step adds to build time
- ❌ Type definitions for third-party libraries may be incomplete or outdated
- ❌ Complex type system can be confusing (conditional types, mapped types)

### Interview Use
> "TypeScript's key decision was being a **superset** of JavaScript — all existing JS code is valid TypeScript. This means zero migration cost and gradual adoption. The lesson for system design: when evolving a system, make the new version backward-compatible with the old. Don't require a big-bang migration. TypeScript went from 0 to the most popular language on GitHub by making adoption frictionless."

---

## 12. Azure Front Door — Global Load Balancing

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Scope** | Global (anycast across all Azure PoPs) | Regional (per-region LB) | Users routed to nearest PoP globally; no DNS-based routing delays |
| **Protocol** | Layer 7 (HTTP/HTTPS) with split-TCP | Layer 4 (TCP pass-through) | Split-TCP: terminate TLS at PoP, maintain persistent connection to backend. Faster TLS handshake for users |
| **Routing** | Latency-based + weighted + priority + session affinity | Simple round-robin | Different apps need different routing strategies |
| **WAF** | Built-in Web Application Firewall (OWASP rules) | Separate WAF service | Security at the edge; block attacks before they reach the origin |
| **Caching** | Built-in CDN caching at PoPs | Separate CDN | Unified: routing + caching + WAF in one service |

### Split-TCP Optimization
- **Without split-TCP**: User in Tokyo → TLS handshake → backend in US (200ms RTT × 3 round trips = 600ms)
- **With split-TCP**: User in Tokyo → TLS handshake → PoP in Tokyo (10ms RTT × 3 = 30ms). PoP keeps persistent connection to US backend
- **Result**: 570ms saved on connection setup

### Interview Use
> "For global traffic management, I'd use a service like Azure Front Door or AWS CloudFront — anycast routing to the nearest PoP, split-TCP to terminate TLS at the edge (saves 3 round trips of cross-region latency), built-in WAF for security, and CDN caching. The split-TCP optimization alone can save 500ms+ for users far from the origin."

---

## 13. FASTER — Concurrent Key-Value Store

**Source**: Microsoft Research — "FASTER: A Concurrent Key-Value Store with In-Place Updates"

### Problem
Need a key-value store that handles billions of operations per second on a single machine — for session state, ML feature caches, and real-time analytics. Existing stores (Redis, RocksDB) don't saturate modern hardware.

### What They Built