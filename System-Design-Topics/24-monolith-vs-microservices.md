# 🎯 Topic 24: Monolith vs Microservices

> **System Design Interview — Deep Dive**
> Comprehensive coverage of monolithic vs microservices architecture — when to use each, service boundaries, database-per-service, independent scaling, the modular monolith compromise, and how to articulate these decisions in interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Monolithic Architecture](#monolithic-architecture)
3. [Microservices Architecture](#microservices-architecture)
4. [When to Choose Monolith vs Microservices](#when-to-choose-monolith-vs-microservices)
5. [Service Boundary Design](#service-boundary-design)
6. [Database-Per-Service Pattern](#database-per-service-pattern)
7. [Independent Scaling](#independent-scaling)
8. [The Modular Monolith Compromise](#the-modular-monolith-compromise)
9. [Communication Between Microservices](#communication-between-microservices)
10. [Data Consistency in Microservices](#data-consistency-in-microservices)
11. [Operational Complexity](#operational-complexity)
12. [The Migration Path](#the-migration-path)
13. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
14. [Common Interview Mistakes](#common-interview-mistakes)
15. [Architecture by System Design Problem](#architecture-by-system-design-problem)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

| Aspect | Monolith | Microservices |
|---|---|---|
| **Deployment** | One unit | Many independent units |
| **Scaling** | Scale everything together | Scale each service independently |
| **Database** | Shared database | Database per service |
| **Communication** | In-process function calls | Network calls (gRPC, HTTP, Kafka) |
| **Team structure** | One team owns all code | Each team owns their services |
| **Complexity** | Application complexity | Distributed system complexity |
| **Best for** | Small teams, early stage | Large teams, proven product |

---

## Monolithic Architecture

### Structure
```
┌──────────────────────────────────────────┐
│            Monolith Application            │
├──────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │  User    │ │  Tweet   │ │  Feed    │ │
│  │  Module  │ │  Module  │ │  Module  │ │
│  └──────────┘ └──────────┘ └──────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │  Search  │ │ Notifi-  │ │ Analytics│ │
│  │  Module  │ │ cation   │ │  Module  │ │
│  └──────────┘ └──────────┘ └──────────┘ │
├──────────────────────────────────────────┤
│            Shared Database                │
│            (PostgreSQL)                   │
└──────────────────────────────────────────┘
```

### Advantages
- **Simple**: One codebase, one deployment, one database, one debug session.
- **Fast development**: No network calls between modules — just function calls.
- **Easy transactions**: ACID across any tables in the shared database.
- **Easy debugging**: Stack trace shows the full call path.
- **No network failures**: In-process calls don't have network latency or timeouts.
- **Lower operational cost**: One application to deploy, monitor, and maintain.

### Disadvantages
- **Scaling limitations**: Must scale the entire application even if only one module is hot.
- **Deployment coupling**: A change in the notification module requires redeploying the entire app.
- **Technology lock-in**: Entire app must use the same language, framework, and database.
- **Team coupling**: Multiple teams working in the same codebase → merge conflicts, coordination.
- **Blast radius**: A bug in one module can crash the entire application.
- **Code complexity**: As the codebase grows (>500K lines), it becomes harder to understand and modify.

---

## Microservices Architecture

### Structure
```
┌──────────┐  ┌──────────┐  ┌──────────┐
│  User    │  │  Tweet   │  │  Feed    │
│ Service  │  │ Service  │  │ Service  │
│          │  │          │  │          │
│ Postgres │  │ Cassandra│  │  Redis   │
└──────────┘  └──────────┘  └──────────┘
      │              │              │
      └──────── Kafka Event Bus ───┘
      
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Search  │  │ Notifi-  │  │ Analytics│
│ Service  │  │ cation   │  │ Service  │
│          │  │ Service  │  │          │
│ Elastic  │  │   SQS    │  │ClickHouse│
└──────────┘  └──────────┘  └──────────┘
```

### Advantages
- **Independent scaling**: Scale the ingestion service to 50 instances while keeping query at 20.
- **Independent deployment**: Deploy the notification service without touching the tweet service.
- **Technology freedom**: Each service picks the best database and language for its job.
- **Team autonomy**: Each team owns their services end-to-end (code, deploy, operate).
- **Fault isolation**: Notification service crashes ≠ tweet service crashes.
- **Organizational scaling**: 100+ engineers can work independently without stepping on each other.

### Disadvantages
- **Distributed system complexity**: Network failures, eventual consistency, distributed tracing.
- **Operational overhead**: N services × (deployment + monitoring + alerting + logging).
- **Data consistency**: No cross-service ACID transactions — need Sagas.
- **Testing complexity**: Integration tests require multiple services running.
- **Network latency**: Every service call adds 1-10ms of network overhead.
- **Debugging difficulty**: A request crosses 5 services — which one is slow?

### Interview Script
> *"Microservices let us scale the write path independently from the read path. Our ingestion service handles 2M events/sec and needs 50 instances. Our query service handles 100K queries/sec and needs 20 instances. With a monolith, we'd scale both together — deploying 50 instances of the entire application when only the ingestion code needs 50 copies. With microservices, we deploy 50 ingestion workers and 20 query servers, saving 60% on compute costs."*

---

## When to Choose Monolith vs Microservices

### Choose Monolith When
- Team < 20 engineers.
- Product is still finding product-market fit.
- Codebase < 500K lines.
- Write throughput < 50K/sec (single DB handles it).
- Fast iteration speed is the priority.
- You haven't identified clear service boundaries yet.

### Choose Microservices When
- Team > 20 engineers (organizational scaling).
- Different services have vastly different scaling needs.
- Services need different technology stacks.
- Independent deployment is critical (continuous delivery).
- Clear domain boundaries exist (users, orders, payments, notifications).
- The monolith has become a deployment bottleneck.

### Interview Script
> *"In a system design interview, I'd decompose into microservices to demonstrate service boundaries and independent scaling. But I'd acknowledge: in practice, I'd start with a modular monolith — cleanly separated modules within a single deployable — and extract services as the team grows past ~20 engineers and we identify genuine scaling bottlenecks. Premature microservices create distributed system problems before you've even found product-market fit."*

---

## Service Boundary Design

### Domain-Driven Design (DDD)
```
Bounded Contexts → Services:
  User Context    → User Service (profiles, auth, settings)
  Content Context → Tweet Service (tweets, likes, retweets)
  Feed Context    → Feed Service (timeline generation, ranking)
  Search Context  → Search Service (indexing, querying)
  Notification    → Notification Service (push, email, SMS)
  Media Context   → Media Service (upload, processing, CDN)
  Analytics       → Analytics Service (events, dashboards)
```

### Criteria for Good Service Boundaries
```
✅ High cohesion: Related functionality grouped together
✅ Low coupling: Minimal dependencies between services
✅ Single responsibility: Each service has one clear purpose
✅ Team-sized: One team (5-8 engineers) can own the service
✅ Independent deployment: Can deploy without coordinating with other services
✅ Independent data: Owns its database exclusively

❌ Bad boundary: Service A can't function without calling Service B synchronously
❌ Bad boundary: Changing feature X requires deploying 5 services simultaneously
```

---

## Database-Per-Service Pattern

### The Rule
Each service **owns its database exclusively**. No other service accesses it directly.

```
Tweet Service → tweets table (Cassandra)     ← ONLY Tweet Service reads/writes
User Service  → users table (PostgreSQL)     ← ONLY User Service reads/writes
Feed Service  → timelines (Redis)            ← ONLY Feed Service reads/writes

If Feed Service needs user data:
  ✅ Call User Service API: GET /users/{id}
  ✅ Consume UserUpdated Kafka event → cache locally
  ❌ Direct SQL query to User Service's database (NEVER!)
```

### Why?
- **Schema independence**: Each team changes their schema without coordinating.
- **Technology freedom**: Tweet Service uses Cassandra, User Service uses PostgreSQL.
- **Failure isolation**: User DB outage doesn't crash Tweet Service.
- **Scaling independence**: Scale each database independently.

### The Tradeoff
```
✅ Autonomy: Each team controls their schema, technology, scaling
❌ No JOINs across services: Must use API calls or event-driven denormalization
❌ No cross-service transactions: Must use Saga pattern
❌ Data duplication: User name may be cached in multiple services
```

### Interview Script
> *"The database-per-service pattern means each service owns its data exclusively. The Tweet Service owns the tweets table, the User Service owns the users table. If the Timeline Service needs the author's display name, it calls the User Service API or maintains a local cache. No direct database access between services. Yes, this means denormalization and eventual consistency. But it means any team can change their database schema without coordinating with every other team — that autonomy is essential at 100+ engineers."*

---

## Independent Scaling

### The Key Benefit
```
Monolith: Everything scales together
  50 instances × full application = 50 × (ingestion + query + notification + analytics)
  Even though only ingestion is the bottleneck

Microservices: Each scales independently
  Ingestion Service:    50 instances (high write volume)
  Query Service:        20 instances (moderate read volume)
  Notification Service: 5 instances (low volume)
  Analytics Service:    3 instances (batch-oriented)
  
  Total: 78 instances vs 200 instances (50 × 4 services) with monolith
  Savings: ~60% compute cost reduction
```

---

## The Modular Monolith Compromise

### What It Is
A monolith with **clearly defined module boundaries** — the architecture of microservices without the distributed system complexity.

```
┌──────────────────────────────────────────┐
│            Modular Monolith               │
├──────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │  User    │ │  Tweet   │ │  Feed    │ │
│  │  Module  │ │  Module  │ │  Module  │ │
│  │ ──────── │ │ ──────── │ │ ──────── │ │
│  │ Public   │ │ Public   │ │ Public   │ │
│  │ API only │ │ API only │ │ API only │ │
│  └──────────┘ └──────────┘ └──────────┘ │
│                                          │
│  Rules:                                  │
│  - Modules communicate via public API    │
│  - No direct database access between     │
│  - Each module could become a service    │
│  - But deployed as ONE application       │
└──────────────────────────────────────────┘
```

### When to Use
- Starting a new project (don't need microservices yet).
- Team < 20 engineers.
- Want clean architecture without distributed complexity.
- Plan to extract services later as scaling demands.

### The Extraction Path
```
Phase 1: Modular monolith (clean boundaries, one deployment)
Phase 2: Extract hot module to separate service (when it needs independent scaling)
Phase 3: Continue extracting as needed (driven by actual bottlenecks)

Extract based on:
  - This module needs to scale 10x more than others
  - This module needs a different technology stack
  - This module's team needs independent deployment
```

---

## Communication Between Microservices

### Synchronous (gRPC / REST)
```
Feed Service → gRPC → User Service: "Get user profile for display"
  Latency: 5-10ms
  Use: When the caller needs the response immediately

Risk: If User Service is slow, Feed Service slows down (coupling).
Mitigation: Circuit breaker, timeout, fallback (show "Unknown User").
```

### Asynchronous (Kafka Events)
```
User Service → Kafka "UserUpdated" event → Feed Service caches user data locally
  Latency: 1-2 seconds
  Use: When eventual consistency is acceptable

Benefit: Services are decoupled — User Service doesn't even know Feed Service exists.
```

### When to Use Each
```
Synchronous (gRPC): Need response to serve the current request.
  "I need the user's name RIGHT NOW to render this page."

Asynchronous (Kafka): Can process later or maintain local cache.
  "When a user changes their name, update our local cache eventually."
```

---

## Data Consistency in Microservices

### The Problem
No cross-service ACID transactions. Booking a ticket involves:
1. Reserve seat (Booking Service)
2. Charge payment (Payment Service)
3. Send confirmation (Notification Service)

### The Solution: Saga Pattern
```
Orchestrated Saga:
  Booking Orchestrator drives the flow:
    Step 1: Reserve seat → success
    Step 2: Charge payment → FAIL
    Step 3: Compensate: Release seat (undo step 1)
    Step 4: Return error to user

Each step is a local transaction in its service.
Each step has a compensating action (undo).
The orchestrator manages the flow and handles failures.
```

---

## Operational Complexity

### Monolith Operations
```
1 application to: deploy, monitor, alert, log, trace, debug
Tools needed: Basic CI/CD, logging, monitoring
Team: 1-2 DevOps engineers
```

### Microservices Operations
```
N services × (deploy + monitor + alert + log + trace)
Additional infrastructure:
  - Service mesh (Envoy/Istio) for inter-service communication
  - Distributed tracing (Jaeger/X-Ray) for request tracking
  - Centralized logging (ELK) for log aggregation
  - Container orchestration (Kubernetes) for deployment
  - API gateway for external traffic routing
  - Service discovery for internal routing

Team: 5-10+ DevOps/Platform engineers
```

---

## The Migration Path

### Strangler Fig Pattern
```
Gradually replace monolith functionality with microservices:

Phase 1: Route /api/users to monolith
Phase 2: Build User Service microservice
Phase 3: Route /api/users to User Service (new)
Phase 4: Remove user code from monolith
Phase 5: Repeat for next service

The monolith shrinks over time as services take over functionality.
No big-bang rewrite — gradual, reversible migration.
```

---

## Interview Talking Points & Scripts

### The Interview Approach
> *"In a system design interview, I'd decompose into microservices to demonstrate service boundaries and independent scaling. But I'd acknowledge: in practice, I'd start with a modular monolith and extract services as the team grows past ~20 engineers."*

### Independent Scaling
> *"Microservices let us scale independently. Our ingestion service needs 50 instances while query needs 20. With a monolith, we'd deploy 50 copies of everything — wasting 60% on compute."*

### Database Per Service
> *"Each service owns its database exclusively. If the Timeline Service needs user data, it calls the User Service API or caches locally. No direct cross-service database access. The autonomy is essential at 100+ engineers."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Microservices for a small team/new product
### ❌ Mistake 2: Not defining service boundaries clearly
### ❌ Mistake 3: Shared database between microservices
### ❌ Mistake 4: Not mentioning the operational complexity cost
### ❌ Mistake 5: Synchronous calls everywhere (tight coupling)

---

## Architecture by System Design Problem

| Problem | Architecture | Service Decomposition |
|---|---|---|
| **Twitter** | Microservices | User, Tweet, Feed, Search, Notification, Media, Analytics |
| **URL Shortener** | Monolith (small scope) | Single service with cache |
| **Chat (WhatsApp)** | Microservices | Gateway, Message, Presence, Media, Notification |
| **E-Commerce** | Microservices | User, Product, Order, Payment, Shipping, Notification |
| **Rate Limiter** | Shared library or sidecar | Not a standalone service |
| **Leaderboard** | Monolith or single service | Redis-backed, simple scope |
| **Video Streaming** | Microservices | Upload, Transcode, Catalog, Streaming, Recommendation |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│         MONOLITH vs MICROSERVICES CHEAT SHEET                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  MONOLITH:                                                   │
│    ✅ Simple, fast dev, easy transactions, easy debugging     │
│    ❌ Scales as unit, deployment coupling, tech lock-in       │
│    Use: Team < 20, early stage, simple product               │
│                                                              │
│  MICROSERVICES:                                              │
│    ✅ Independent scaling, deployment, tech choice            │
│    ❌ Distributed complexity, ops overhead, no cross-svc ACID │
│    Use: Team > 20, different scaling needs, clear boundaries │
│                                                              │
│  MODULAR MONOLITH: Best of both                              │
│    Clean module boundaries, one deployment                   │
│    Extract to microservices when scaling demands              │
│                                                              │
│  DATABASE-PER-SERVICE:                                       │
│    Each service owns its DB exclusively                      │
│    Cross-service: API calls or Kafka events, not direct DB   │
│                                                              │
│  COMMUNICATION:                                              │
│    Sync (gRPC): Need response now                            │
│    Async (Kafka): Eventual consistency is fine               │
│                                                              │
│  CONSISTENCY: Saga pattern (no cross-service ACID)           │
│  MIGRATION: Strangler Fig (gradual, reversible)              │
│                                                              │
│  INTERVIEW: Decompose into microservices for the design,     │
│  but acknowledge you'd start with modular monolith IRL.      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
