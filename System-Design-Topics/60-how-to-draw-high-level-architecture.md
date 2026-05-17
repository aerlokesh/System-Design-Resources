# 🎯 Topic 60: How to Draw the High-Level Architecture

> **System Design Interview — Deep Dive**
> A comprehensive guide covering how to structure, draw, and present high-level architecture diagrams in a system design interview — box-and-arrow conventions, the standard building blocks (clients, API gateway, services, databases, caches, queues, CDN), left-to-right data flow, layered organization, what to include vs omit, how to evolve the diagram during the interview, ASCII diagram techniques, and production-grade talking points for every component you draw.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [The Standard Template — Where to Start](#the-standard-template--where-to-start)
3. [Building Blocks — The Components You Draw](#building-blocks--the-components-you-draw)
4. [Layout Principles — Left to Right, Top to Bottom](#layout-principles--left-to-right-top-to-bottom)
5. [The 5-Minute First Pass](#the-5-minute-first-pass)
6. [Naming Conventions](#naming-conventions)
7. [Arrow Semantics — What Connections Mean](#arrow-semantics--what-connections-mean)
8. [Data Flow Annotation](#data-flow-annotation)
9. [When to Split Into Multiple Diagrams](#when-to-split-into-multiple-diagrams)
10. [Evolving the Diagram During the Interview](#evolving-the-diagram-during-the-interview)
11. [What to Include vs What to Omit](#what-to-include-vs-what-to-omit)
12. [Common Architecture Patterns to Draw](#common-architecture-patterns-to-draw)
13. [Drawing Databases and Storage](#drawing-databases-and-storage)
14. [Drawing Async Flows — Queues and Events](#drawing-async-flows--queues-and-events)
15. [Drawing Read vs Write Paths](#drawing-read-vs-write-paths)
16. [Drawing for Scale — Showing Horizontal Scaling](#drawing-for-scale--showing-horizontal-scaling)
17. [ASCII Diagram Techniques](#ascii-diagram-techniques)
18. [Whiteboard vs Digital — Tool Tips](#whiteboard-vs-digital--tool-tips)
19. [Annotating Non-Functional Requirements on the Diagram](#annotating-non-functional-requirements-on-the-diagram)
20. [Complete Worked Examples](#complete-worked-examples)
21. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
22. [Common Drawing Mistakes](#common-drawing-mistakes)
23. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

The high-level architecture diagram is the **centerpiece** of a system design interview. It's not just a drawing — it's the visual contract of your design that you'll reference and evolve for the remaining 30+ minutes. A clear, well-structured diagram demonstrates you can think in systems, communicate visually, and organize complexity.

```
What the interviewer evaluates from your diagram:

  ✅ Can you identify the right components for the problem?
  ✅ Do you understand how data flows through the system?
  ✅ Can you separate read and write paths?
  ✅ Do you know where to put caches, queues, and databases?
  ✅ Can you communicate clearly under time pressure?
  ✅ Does the diagram tell a story someone can follow?

What the interviewer does NOT expect:
  ❌ Pixel-perfect diagrams
  ❌ Every edge case covered in the first pass
  ❌ Specific AWS service names (unless asked)
  ❌ Low-level implementation details
```

### The Golden Rule

```
A good architecture diagram should be readable by someone who
wasn't in the room. If a stranger looks at it, they should understand:

  1. What the system does (from the title and client/API boxes)
  2. How data flows (from the arrows)
  3. Where data is stored (from the database/cache boxes)
  4. What happens asynchronously (from the queue/worker boxes)
  5. What serves external users (from the CDN/API gateway)
```

---

## The Standard Template — Where to Start

### The Universal Starting Point

Every system design diagram starts with the same skeleton. Memorize this:

```
┌────────┐     ┌─────────────┐     ┌──────────────┐     ┌──────────┐
│ Clients │────→│ API Gateway  │────→│  Service(s)   │────→│ Database │
│ (Web/   │     │ / Load       │     │               │     │          │
│  Mobile)│     │ Balancer     │     │               │     │          │
└────────┘     └─────────────┘     └──────────────┘     └──────────┘
```

Then you ADD components based on the problem:

```
Need caching?        → Add a Cache box between Service and Database.
Need async work?     → Add a Queue + Worker between Service and downstream.
Need media serving?  → Add CDN in front of clients + S3 for storage.
Need real-time?      → Add WebSocket server alongside the API.
Need search?         → Add Elasticsearch alongside the Database.
Need notifications?  → Add a Notification Service + Queue.
```

### The Template with All Common Components

```
                                              ┌──────────┐
                                              │   CDN     │
                                              │ (static)  │
                                              └─────┬────┘
                                                    │
┌────────┐     ┌──────────────┐     ┌───────────────┤
│ Clients │────→│ API Gateway / │────→│  Services     │
│         │     │ Load Balancer │     │               │
└────────┘     └──────────────┘     │  ┌──────────┐ │
                                    │  │ Service A │ │
                                    │  └────┬─────┘ │
                                    │       │       │
                                    │  ┌────▼─────┐ │     ┌──────────┐
                                    │  │  Cache    │ │────→│ Database │
                                    │  │ (Redis)   │ │     │ (Primary)│
                                    │  └──────────┘ │     └──────────┘
                                    │               │
                                    │  ┌──────────┐ │     ┌──────────┐
                                    │  │  Queue    │ │────→│ Workers  │
                                    │  │ (SQS)    │ │     │          │
                                    │  └──────────┘ │     └──────────┘
                                    └───────────────┘
```

---

## Building Blocks — The Components You Draw

### Tier 1: Always Present

| Component | Draw As | Purpose | Example |
|---|---|---|---|
| **Clients** | Rectangle, left side | Users/apps making requests | Web browser, mobile app, API consumer |
| **API Gateway / LB** | Rectangle | Entry point, routing, rate limiting | Nginx, ALB, Kong |
| **Application Service** | Rectangle(s) | Core business logic | UserService, OrderService |
| **Database** | Cylinder or rectangle with label | Persistent storage | PostgreSQL, DynamoDB |

### Tier 2: Add Based on Requirements

| Component | When to Add | Draw As |
|---|---|---|
| **Cache** | Read-heavy, latency-sensitive | Rectangle labeled "Cache (Redis)" |
| **Message Queue** | Async processing, decoupling | Rectangle labeled "Queue (SQS/Kafka)" |
| **Workers** | Background jobs, async processing | Rectangle labeled "Workers" |
| **CDN** | Media-heavy, global users | Cloud shape or rectangle, top-left |
| **Object Storage** | Images, videos, files | Cylinder labeled "S3" |
| **Search Index** | Full-text search, filtering | Rectangle labeled "Elasticsearch" |
| **WebSocket Server** | Real-time features (chat, live) | Rectangle labeled "WS Server" |
| **Notification Service** | Push/email/SMS | Rectangle labeled "Notification Svc" |

### Tier 3: Add During Deep Dives

| Component | When to Add |
|---|---|
| **Read Replicas** | When discussing read scaling |
| **Write-Ahead Log / CDC** | When discussing data sync |
| **Rate Limiter** | When discussing abuse prevention |
| **Scheduler / Cron** | When discussing periodic jobs |
| **ML/Recommendation Service** | When discussing personalization |
| **Analytics Pipeline** | When discussing metrics/reporting |

---

## Layout Principles — Left to Right, Top to Bottom

### The Standard Layout

```
LEFT → RIGHT = Direction of data flow (request path)
TOP → BOTTOM = Layers of abstraction (user-facing → internal)

  LEFT SIDE                    CENTER                    RIGHT SIDE
  ─────────                    ──────                    ──────────
  External                     Application               Storage
  (Clients, CDN)               (Services, Logic)         (DB, Cache, S3)

  TOP: User-facing (API Gateway, CDN, WebSocket)
  MIDDLE: Application logic (Services, Workers)
  BOTTOM: Infrastructure (Databases, Queues, Storage)
```

### Why Left-to-Right?

```
Natural reading direction (in English): Left → Right.
Request flow matches reading flow:
  Client → Gateway → Service → Database
  
The interviewer's eye naturally follows the arrows.
```

### Layered Organization

```
Layer 1 (Top/Left):    CLIENT LAYER
  Clients, CDN, DNS

Layer 2 (Middle-Left): GATEWAY LAYER
  API Gateway, Load Balancer, Rate Limiter

Layer 3 (Center):      APPLICATION LAYER
  Microservices, Business Logic

Layer 4 (Center-Right): DATA ACCESS LAYER
  Cache, Search, Message Queue

Layer 5 (Right/Bottom): STORAGE LAYER
  Database, Object Storage, Data Warehouse
```

### Visual Example

```
Layer 1:  ┌────────┐
          │ Client  │
          └────┬───┘
               │
Layer 2:  ┌────▼─────────┐
          │ API Gateway   │
          └────┬─────────┘
               │
Layer 3:  ┌────▼─────────┐     ┌──────────┐
          │ Tweet Service  │────→│  Queue   │──→ Workers
          └────┬──────────┘     └──────────┘
               │
Layer 4:  ┌────▼─────┐
          │  Cache    │
          │  (Redis)  │
          └────┬─────┘
               │
Layer 5:  ┌────▼──────────┐     ┌──────────┐
          │  PostgreSQL    │     │    S3    │
          │  (tweets)      │     │ (media)  │
          └───────────────┘     └──────────┘
```

---

## The 5-Minute First Pass

### The Technique

In the first 5 minutes of your design, draw the **simplest possible version** of the architecture. Don't optimize. Don't add caches or queues yet. Just show the happy path.

```
Step 1 (30 seconds): Draw Client → API → Service → Database
  "Let me start with the basic request flow."

Step 2 (1 minute): Label the main services based on core entities
  "For Twitter, I'll have a Tweet Service and a User Service."

Step 3 (1 minute): Show the primary data stores
  "Tweets go to a relational database. Media goes to S3."

Step 4 (1 minute): Add the most obvious secondary components
  "I'll add a cache for read-heavy feeds and a CDN for images."

Step 5 (1 minute): Add async flow if relevant
  "Fan-out for the timeline is async — I'll add a queue + workers."

Result: A complete, simple architecture in 5 minutes.
  Then spend the next 30 minutes evolving it during deep dives.
```

### What the First Pass Looks Like

```
"Here's my initial architecture for Twitter:"

┌────────┐     ┌──────────┐     ┌──────────────┐     ┌────────────┐
│ Client  │────→│ API GW /  │────→│ Tweet Service│────→│ PostgreSQL │
│ (Web/   │     │ LB        │     │              │     │ (tweets)   │
│  Mobile)│     └──────────┘     └──────┬───────┘     └────────────┘
└────────┘                              │
                                   ┌────▼──────┐     ┌────────────┐
                                   │ Fan-out    │────→│ Redis      │
                                   │ Queue      │     │ (timelines)│
                                   └───────────┘     └────────────┘

"This is intentionally simple. Let me walk through the data flow,
 then we can dive deeper into any component."
```

### The Key Phrase

> *"Let me start with a simple architecture and evolve it as we discuss requirements."*

This sets expectations. The interviewer knows you'll add complexity as needed.

---

## Naming Conventions

### Service Naming

```
DO: Name services by their domain responsibility
  ✅ Tweet Service
  ✅ User Service
  ✅ Feed Service
  ✅ Notification Service
  ✅ Media Service
  ✅ Search Service

DON'T: Use generic or implementation-specific names
  ❌ Microservice 1, Microservice 2
  ❌ Lambda Function
  ❌ EC2 Instance
  ❌ Backend

Why? Domain names tell the interviewer WHAT the service does.
  "Tweet Service" → handles tweet CRUD.
  "Microservice 1" → tells them nothing.
```

### Database Naming

```
DO: Label with technology AND what it stores
  ✅ PostgreSQL (tweets, users)
  ✅ Redis (timeline cache)
  ✅ Elasticsearch (tweet search)
  ✅ S3 (media files)
  ✅ DynamoDB (user sessions)

DON'T: Just say "Database"
  ❌ DB
  ❌ Database
  ❌ Storage

Why? The technology choice signals your understanding of tradeoffs.
  "PostgreSQL (tweets)" → relational, ACID, good for structured data.
  "Database" → tells the interviewer nothing about your reasoning.
```

### Queue and Cache Naming

```
DO: Label with purpose
  ✅ Fan-out Queue (SQS)
  ✅ Notification Queue (Kafka)
  ✅ Timeline Cache (Redis)
  ✅ Session Cache (Redis)

DON'T: Just say "Queue" or "Cache"
  The system might have 3 queues for different purposes.
  Labeling by purpose prevents confusion.
```

---

## Arrow Semantics — What Connections Mean

### Arrow Types

```
Solid arrow (→):  Synchronous request-response
  Client → API Gateway: HTTP request
  Service → Database: SQL query
  Service → Cache: GET/SET

Dashed arrow (-->): Asynchronous / eventual
  Service --> Queue: Publish message
  Queue --> Worker: Consume message
  Service --> Notification: Fire-and-forget

Double arrow (↔): Bidirectional / persistent connection
  Client ↔ WebSocket Server: Real-time connection
  Service ↔ Database: Read and write

Labeled arrow: Annotate with the protocol or action
  → HTTP/REST
  → gRPC
  → WebSocket
  → Pub/Sub
  → CDC (Change Data Capture)
```

### Arrow Labeling

```
DO label arrows when:
  ✅ Multiple protocols between two components (HTTP vs gRPC)
  ✅ The connection type is non-obvious (CDC, event stream)
  ✅ The data flowing is important to understand ("tweets", "notifications")

DON'T over-label:
  ❌ Every arrow labeled "HTTP" (assumed by default)
  ❌ Arrows labeled with implementation details ("TCP port 5432")
```

### Example with Semantic Arrows

```
┌────────┐  HTTP   ┌──────────┐  gRPC   ┌──────────────┐  SQL    ┌──────────┐
│ Client  │───────→│ API GW    │────────→│ Tweet Service │───────→│PostgreSQL│
└────────┘         └──────────┘         └──────┬───────┘         └──────────┘
                                               │
                                         Pub/Sub│(async)
                                               │
                                        ┌──────▼───────┐
                                        │ Fan-out Queue │
                                        │ (Kafka)       │
                                        └──────┬───────┘
                                               │ Consume
                                        ┌──────▼───────┐
                                        │ Fan-out       │
                                        │ Workers       │
                                        └──────────────┘
```

---

## Data Flow Annotation

### Numbering the Steps

```
Annotate arrows with step numbers to show the order of operations:

  ┌────────┐  ①   ┌──────────┐  ②   ┌──────────────┐
  │ Client  │────→│ API GW    │────→│ Tweet Service │
  └────────┘      └──────────┘     └──────┬───────┘
                                          │
                                     ③   │ ④ (async)
                                     ▼    ▼
                               ┌──────┐ ┌──────────┐
                               │ DB    │ │  Queue   │
                               └──────┘ └────┬─────┘
                                             │ ⑤
                                       ┌─────▼──────┐
                                       │ Fan-out     │
                                       │ Worker      │──→ ⑥ Write to timeline cache
                                       └────────────┘

Walk through: "Let me trace the flow of posting a tweet:
  ① Client sends POST /tweet to API Gateway.
  ② Gateway routes to Tweet Service.
  ③ Tweet Service writes the tweet to PostgreSQL.
  ④ Asynchronously, it publishes to the fan-out queue.
  ⑤ Fan-out workers consume from the queue.
  ⑥ Each worker writes the tweet to followers' timeline caches."
```

### When to Number Steps

```
DO number steps when:
  ✅ Explaining a complex flow (more than 3 hops)
  ✅ There are both sync and async paths
  ✅ The order of operations matters
  ✅ You're walking the interviewer through the design

DON'T number steps when:
  ❌ The flow is obvious (Client → API → DB)
  ❌ You're in the first-pass sketch (keep it simple)
```

---

## When to Split Into Multiple Diagrams

### One Diagram per Flow

For complex systems, draw separate diagrams for each major flow:

```
Diagram 1: "Write Path — Posting a Tweet"
  Client → API → Tweet Service → DB + Queue → Fan-out Workers → Cache

Diagram 2: "Read Path — Loading the Timeline"
  Client → API → Feed Service → Cache → (Cache miss → DB)

Diagram 3: "Search Path"
  Client → API → Search Service → Elasticsearch

Diagram 4: "Notification Path"
  Queue → Notification Service → Push/Email/SMS

Why separate diagrams?
  - Each diagram is focused and readable
  - The interviewer can see you understand read vs write path
  - Deep dives naturally focus on one flow at a time
```

### When to Keep One Diagram

```
Keep one diagram when:
  - The system is simple (< 8 components)
  - Read and write paths share most components
  - The interviewer hasn't asked for specific flows yet

Split into multiple when:
  - The single diagram gets crowded (> 10 components)
  - Read and write paths are fundamentally different
  - The interviewer asks "Walk me through the write path"
```

---

## Evolving the Diagram During the Interview

### The Evolution Pattern

```
Minute 0-5:   SKELETON
  Client → API → Service → Database
  "Here's the basic architecture."

Minute 5-10:  ADD CORE COMPONENTS
  + Cache, + Queue, + CDN
  "Let me add caching for reads and async processing for writes."

Minute 10-20: DEEP DIVE #1
  Expand one component (e.g., the database → show sharding, replicas)
  "For the database, I'd shard by user_id and add read replicas."

Minute 20-30: DEEP DIVE #2
  Expand another component (e.g., the queue → show fan-out details)
  "For fan-out, here's how the queue + workers distribute tweets."

Minute 30-40: DEEP DIVE #3
  Add edge cases, failure handling, scaling annotations
  "If the cache is down, we fall back to the database."
```

### How to Add Components Mid-Interview

```
Physical whiteboard:
  Leave SPACE between components in the first pass.
  Add new boxes in the gaps.
  Use different colored markers for additions (blue = original, red = additions).

Digital whiteboard (Excalidraw, draw.io):
  Components can be moved and resized.
  Add components freely.

Online interview (text-based):
  Maintain a running ASCII diagram.
  Add new components as needed.
  Call out changes: "I'm now adding a cache between Service and DB."
```

### The Key Phrase When Adding

> *"Based on our discussion about [latency/throughput/consistency], I'm adding [component] here because [reason]."*

This shows the interviewer you're making DELIBERATE choices, not just drawing boxes.

---

## What to Include vs What to Omit

### Always Include

```
✅ Client(s) — who initiates requests
✅ API Gateway or Load Balancer — the entry point
✅ Core service(s) — the business logic
✅ Primary database — where data is persisted
✅ Arrows with direction — showing data flow
✅ Labels — what each component is and does
```

### Include When Relevant

```
✅ Cache — if reads are frequent or latency matters
✅ Message Queue — if there's async processing
✅ CDN — if serving media or static content
✅ Object Storage (S3) — if handling files/media
✅ Search Index — if full-text search is needed
✅ WebSocket — if real-time updates are needed
✅ Worker fleet — if background processing exists
```

### Always Omit (Unless Asked)

```
❌ DNS resolution — assumed
❌ TLS/HTTPS termination — assumed
❌ Container orchestration (Kubernetes) — implementation detail
❌ CI/CD pipeline — not relevant to architecture
❌ Logging/monitoring infrastructure — unless discussing observability
❌ Internal network topology — unless discussing latency/partitions
❌ Specific server counts — say "N instances" instead
❌ Authentication flow — unless it's the core problem
```

---

## Common Architecture Patterns to Draw

### Pattern 1: CRUD API

```
The simplest architecture. Use for URL shortener, user profile service.

┌────────┐     ┌────────┐     ┌──────────┐     ┌──────────┐
│ Client  │────→│ API GW │────→│ Service   │────→│ Database │
└────────┘     └────────┘     └──────────┘     └──────────┘
```

### Pattern 2: Read-Heavy with Cache

```
Use for: Feed, product catalog, any read-heavy system.

┌────────┐     ┌────────┐     ┌──────────┐     ┌──────────┐
│ Client  │────→│ API GW │────→│ Service   │────→│ Cache    │
└────────┘     └────────┘     └──────────┘     │ (Redis)  │
                                                └────┬─────┘
                                                     │ miss
                                                ┌────▼─────┐
                                                │ Database  │
                                                └──────────┘
```

### Pattern 3: Write-Heavy with Async Processing

```
Use for: Notification system, analytics ingestion, media upload.

┌────────┐     ┌────────┐     ┌──────────┐     ┌──────────┐
│ Client  │────→│ API GW │────→│ Service   │────→│ Queue    │
└────────┘     └────────┘     └────┬─────┘     └────┬─────┘
                                    │                │
                               ┌────▼─────┐    ┌────▼─────┐
                               │ Database  │    │ Workers  │
                               └──────────┘    └──────────┘
```

### Pattern 4: Fan-Out (Feed/Timeline)

```
Use for: Twitter, Instagram, any social feed.

Write path:
┌────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ Client  │────→│ Tweet Svc│────→│ DB       │     │ Fan-out  │
└────────┘     └────┬─────┘     └──────────┘     │ Workers  │
                    │                              └────┬─────┘
                    └──→ Queue ──────────────────→      │
                                                   ┌────▼─────┐
                                                   │ Timeline  │
                                                   │ Cache     │
                                                   └──────────┘

Read path:
┌────────┐     ┌──────────┐     ┌──────────┐
│ Client  │────→│ Feed Svc │────→│ Timeline │
└────────┘     └──────────┘     │ Cache    │
                                └──────────┘
```

### Pattern 5: Media Pipeline

```
Use for: Instagram, YouTube, any media-heavy system.

┌────────┐ ①    ┌──────────┐ ②    ┌──────────┐
│ Client  │─────→│ API       │─────→│ Presigned│
└────┬───┘      └──────────┘      │ URL      │
     │                             └──────────┘
     │ ③ Direct upload
     ▼
┌──────────┐ ④    ┌──────────┐ ⑤    ┌──────────┐
│    S3     │─────→│ Queue    │─────→│ Workers  │
│  (raw)    │      └──────────┘      │(transcode)│
└──────────┘                         └────┬─────┘
                                          │ ⑥
┌────────┐ ⑦    ┌──────────┐         ┌────▼─────┐
│ Client  │←────│   CDN     │←────────│ S3       │
│ (view)  │     └──────────┘         │(processed)│
└────────┘                           └──────────┘
```

### Pattern 6: Real-Time + REST Hybrid

```
Use for: Chat (WhatsApp), live updates, collaborative editing.

┌────────┐  HTTP   ┌──────────┐     ┌──────────┐     ┌──────────┐
│ Client  │───────→│ API GW    │────→│ Message  │────→│ Database │
└────┬───┘         └──────────┘     │ Service  │     └──────────┘
     │                               └──────────┘
     │ WebSocket   ┌──────────┐     ┌──────────┐
     └────────────→│ WS Server │←──→│ Pub/Sub  │
                   └──────────┘     │ (Redis)  │
                                    └──────────┘
```

---

## Drawing Databases and Storage

### The Cylinder Convention

```
Databases are traditionally drawn as cylinders.
But in ASCII or quick sketches, rectangles with clear labels work fine.

Cylinder (whiteboard):     Rectangle (ASCII/digital):
  ┌───────┐               ┌───────────────┐
  │       │               │ PostgreSQL     │
  │  DB   │       =       │ (tweets,users) │
  │       │               └───────────────┘
  └───────┘
```

### Showing Multiple Databases

```
When the system uses multiple storage technologies, draw them separately:

  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
  │ PostgreSQL   │     │ Redis         │     │ S3           │
  │ (tweets,     │     │ (cache,       │     │ (media       │
  │  users)      │     │  sessions)    │     │  files)      │
  └──────────────┘     └──────────────┘     └──────────────┘
  
  Each has a clear label: technology + what it stores.
  This shows the interviewer you've thought about storage tradeoffs.
```

### Showing Read Replicas and Sharding

```
Read replicas:
  ┌──────────┐
  │ Primary  │────→ ┌──────────┐
  │ (writes) │      │ Replica 1│ (reads)
  └──────────┘  ──→ ┌──────────┐
                    │ Replica 2│ (reads)
                    └──────────┘

Sharding:
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Shard 1  │  │ Shard 2  │  │ Shard 3  │
  │ (A-H)    │  │ (I-P)    │  │ (Q-Z)    │
  └──────────┘  └──────────┘  └──────────┘
  
  Note the shard key in the label (A-H, I-P, Q-Z).
```

---

## Drawing Async Flows — Queues and Events

### Queue as a Decoupling Point

```
Synchronous (tightly coupled):
  Service A ──→ Service B
  If B is slow or down, A is affected.

Asynchronous (decoupled via queue):
  Service A ──→ Queue ──→ Service B
  If B is slow or down, messages buffer in the queue.
  A is unaffected.

Draw the queue BETWEEN the producer and consumer:

  ┌──────────┐     ┌──────────┐     ┌──────────┐
  │ Tweet Svc│──→  │ SQS Queue│  ──→│ Fan-out  │
  │ (produce)│     │          │     │ Worker   │
  └──────────┘     └──────────┘     └──────────┘
```

### Event-Driven with Multiple Consumers

```
One event, multiple consumers (fan-out):

  ┌──────────┐     ┌──────────────┐     ┌──────────────┐
  │ Order Svc│──→  │ Kafka Topic   │──→  │ Email Worker │
  └──────────┘     │ "order-placed"│──→  │ SMS Worker   │
                   │               │──→  │ Analytics    │
                   └──────────────┘     └──────────────┘

  One event published. Three independent consumers.
  Each processes the event for its own purpose.
```

---

## Drawing Read vs Write Paths

### Why Separate Them

```
Many systems have fundamentally different read and write paths:

  Instagram:
    Write: Upload photo → S3 → Process → CDN
    Read: Load feed → Cache → (miss: Database)

  Drawing BOTH on one diagram gets cluttered.
  Drawing them separately is clearer and more impressive.
```

### Write Path Diagram

```
"Here's the write path — what happens when a user posts a tweet:"

┌────────┐  POST   ┌──────────┐  Insert  ┌──────────┐
│ Client  │───────→│ Tweet Svc │────────→│ Postgres │
└────────┘         └────┬─────┘          └──────────┘
                        │
                   Publish│(async)
                        │
                   ┌────▼──────┐     ┌──────────────┐
                   │ Fan-out   │────→│ Redis         │
                   │ Queue     │     │ (follower     │
                   └──────────┘     │  timelines)   │
                                    └──────────────┘
```

### Read Path Diagram

```
"And here's the read path — loading the home timeline:"

┌────────┐  GET    ┌──────────┐  Read   ┌──────────────┐
│ Client  │───────→│ Feed Svc  │───────→│ Redis         │
└────────┘         └──────────┘        │ (timeline    │
                                        │  cache)      │
                                        └──────┬──────┘
                                               │ Cache miss
                                        ┌──────▼──────┐
                                        │  Postgres    │
                                        │  (tweets)    │
                                        └─────────────┘
```

---

## Drawing for Scale — Showing Horizontal Scaling

### How to Show Multiple Instances

```
DON'T draw 50 boxes for 50 servers. 

DO use notation to indicate horizontal scaling:

  Method 1: Stacked boxes (shadow effect)
    ┌──────────┐
    │┌─────────┤
    ││ Service  │  ← implies multiple instances
    │└─────────┘
    └──────────┘

  Method 2: Label with "×N"
    ┌──────────────┐
    │ Tweet Service │
    │    (×50)      │
    └──────────────┘

  Method 3: Text annotation
    ┌──────────────┐
    │ Tweet Service │  ← "Horizontally scaled, 50 instances behind LB"
    └──────────────┘
```

### Showing Auto-Scaling

```
  ┌──────────┐     ┌──────────────────┐     ┌──────────┐
  │ API GW   │────→│ Tweet Service     │────→│ Database │
  └──────────┘     │ (auto-scale:      │     └──────────┘
                   │  min=5, max=100)  │
                   └──────────────────┘
```

---

## ASCII Diagram Techniques

### Box Drawing Characters

```
Standard ASCII:
  ┌──────────┐     Use + - | for simple boxes
  │  Service  │     +----------+
  └──────────┘     |  Service  |
                   +----------+

Unicode box drawing (prettier):
  ┌──────────┐
  │  Service  │
  └──────────┘

Arrows:
  → ← ↑ ↓   Unicode arrows
  ->  <-     ASCII arrows
  ──→  ──→   Box drawing + arrow
```

### Quick Templates

```
Horizontal flow:
  [A] ──→ [B] ──→ [C] ──→ [D]

Vertical flow:
  [A]
   │
   ▼
  [B]
   │
   ▼
  [C]

Fan-out:
       ┌──→ [B]
  [A] ─┤
       └──→ [C]

Fan-in:
  [A] ──┐
        ├──→ [C]
  [B] ──┘

Bidirectional:
  [A] ←──→ [B]
```

---

## Whiteboard vs Digital — Tool Tips

### Physical Whiteboard

```
Tips:
  1. Start in the TOP-LEFT, leave 60% of space empty for additions.
  2. Draw boxes LARGE enough to label clearly.
  3. Use DIFFERENT COLORS: Blue = components, Red = data flow, Green = annotations.
  4. Number your arrows (①②③) when walking through a flow.
  5. Don't erase — cross out and redraw. Erasing wastes time.
  6. Write legibly — if the interviewer can't read it, it doesn't help.
```

### Digital Whiteboard (Excalidraw, draw.io, Miro)

```
Tips:
  1. Pre-draw the skeleton before the interview (Client → API → DB).
  2. Use color coding: Blue=services, Green=data stores, Orange=queues.
  3. Group related components (select + group).
  4. Use straight lines with arrow tips (not freehand).
  5. Zoom in/out to show different levels of detail.
  6. Duplicate components rather than redraw (copy-paste).
```

### Text-Based (Phone Screen / Coding Pad)

```
Tips:
  1. Use ASCII art (see templates above).
  2. Keep it simple — max 6-8 components.
  3. Supplement with text descriptions:
     "Client → API Gateway (Nginx) → Tweet Service → PostgreSQL"
  4. List components and their connections explicitly:
     Components: Client, API GW, Tweet Svc, DB, Cache, Queue, Workers
     Connections: Client→API GW→Tweet Svc→DB, Tweet Svc→Queue→Workers→Cache
```

---

## Annotating Non-Functional Requirements on the Diagram

### Where to Put NFR Annotations

```
Add annotations NEXT TO the relevant component:

  ┌──────────────────┐
  │ API Gateway       │  ← Rate limit: 1000 req/sec per user
  │ (Nginx + Redis)   │  ← TLS termination
  └──────────────────┘

  ┌──────────────────┐
  │ Tweet Service     │  ← Stateless, auto-scale 5-100 instances
  │                   │  ← p99 latency < 200ms
  └──────────────────┘

  ┌──────────────────┐
  │ PostgreSQL        │  ← Sharded by user_id
  │ (Primary +        │  ← 3 read replicas
  │  3 replicas)      │  ← Daily backups to S3
  └──────────────────┘
  
  ┌──────────────────┐
  │ Redis Cache       │  ← TTL: 5 minutes
  │                   │  ← Cache-aside pattern
  │                   │  ← 99% hit rate target
  └──────────────────┘
```

### Common Annotations

```
Latency:    "p99 < 200ms"
Throughput: "10K writes/sec"
Scaling:    "Auto-scale 5-100"
Consistency:"Eventually consistent (5s)"
Availability:"99.99% SLA"
Caching:    "Cache-aside, TTL=5min"
Sharding:   "Shard by user_id"
Replication:"3 replicas, async"
```

---

## Complete Worked Examples

### Example 1: Twitter (Simple)

```
"Design Twitter — posting and reading tweets."

┌────────┐     ┌──────────┐     ┌──────────────┐     ┌──────────────┐
│ Client  │────→│ API GW /  │────→│ Tweet Service│────→│ PostgreSQL   │
│ (Web/   │     │ LB        │     │ (write tweet)│     │ (tweets)     │
│  Mobile)│     └──────────┘     └──────┬───────┘     └──────────────┘
└────────┘                              │
                                   ┌────▼──────────┐
                                   │ Fan-out Queue  │
                                   │ (Kafka)        │
                                   └────┬──────────┘
                                        │
                                   ┌────▼──────────┐    ┌──────────────┐
                                   │ Fan-out Workers│──→│ Redis         │
                                   └───────────────┘    │ (timelines)  │
                                                        └──────────────┘

Read path:
┌────────┐     ┌──────────┐     ┌──────────────┐     ┌──────────────┐
│ Client  │────→│ API GW    │────→│ Feed Service │────→│ Redis        │
└────────┘     └──────────┘     └──────────────┘     │ (timeline)   │
                                                      └──────┬──────┘
                                                             │ miss
                                                      ┌──────▼──────┐
                                                      │ PostgreSQL   │
                                                      └─────────────┘
```

### Example 2: Instagram (Media-Heavy)

```
"Design Instagram — photo upload and feed."

Upload path:
┌────────┐ ①    ┌──────────┐ ② Presigned URL  ┌──────────┐
│ Client  │─────→│ API       │─────────────────→│ Client   │
└────┬───┘      └──────────┘                   │ gets URL │
     │                                          └──────────┘
     │ ③ PUT to S3
     ▼
┌──────────┐ ④ Event  ┌──────────┐ ⑤    ┌──────────────┐
│ S3 (raw) │────────→│ SQS Queue │────→│ Image Workers │
└──────────┘          └──────────┘      │ (resize,      │
                                        │  compress)    │
                                        └──────┬───────┘
                                               │ ⑥
                                        ┌──────▼───────┐
                                        │ S3 (processed)│
                                        └──────┬───────┘
                                               │
                                        ┌──────▼───────┐
                                        │    CDN        │──→ Users globally
                                        └──────────────┘

Feed path:
┌────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ Client  │────→│ Feed Svc  │────→│ Redis    │←────│ Fan-out  │
└────────┘     └──────────┘     │ (feed    │     │ Workers  │
                                │  cache)  │     └──────────┘
                                └──────────┘
```

### Example 3: Chat System (WhatsApp)

```
"Design a chat system."

┌────────┐  HTTP    ┌──────────┐     ┌──────────────┐     ┌──────────┐
│ Client  │────────→│ API GW    │────→│ Message Svc  │────→│ Cassandra│
│         │         └──────────┘     └──────┬───────┘     │ (msgs)   │
│         │                                 │              └──────────┘
│         │ WebSocket  ┌──────────┐    Publish│
│         │←──────────→│ WS Server │←────────┘
└────────┘             │ (N inst.) │
                       └────┬─────┘
                            │ Pub/Sub
                       ┌────▼──────┐
                       │ Redis     │
                       │ Pub/Sub   │
                       └───────────┘

Key annotations:
  - WS Server maintains persistent connections (1M+ concurrent)
  - Message Svc is stateless (writes to DB + publishes to Pub/Sub)
  - WS Server subscribes to Redis Pub/Sub for the connected user's channels
  - If recipient is offline → message stored in DB → delivered on next connect
```

---

## Interview Talking Points & Scripts

### Script 1: Opening

> *"Let me start by drawing the high-level architecture. I'll keep it simple first, and we can dive deeper into any component. Here's the basic request flow: clients connect through an API gateway to our core services, which interact with the database. I'll add caching, async processing, and other components as we discuss specific requirements."*

### Script 2: Justifying a Component

> *"I'm adding a Redis cache between the Feed Service and the database because the timeline is read 1000x more than it's written. With a 99% cache hit rate, we'd serve 99% of timeline reads from Redis at sub-millisecond latency, reducing database load by 100x. I'd use cache-aside with a 5-minute TTL."*

### Script 3: Explaining Async Flow

> *"When a user posts a tweet, I want to return the response immediately — the user shouldn't wait for fan-out to all followers. So I'll decouple these: the Tweet Service writes to the database synchronously, then publishes to a Kafka topic asynchronously. Fan-out workers consume from Kafka and write to each follower's timeline cache. This way, the write path is fast (< 100ms) and the fan-out happens in the background."*

### Script 4: Evolving the Diagram

> *"Now that we're discussing search, let me add an Elasticsearch cluster to the diagram. The Tweet Service will publish new tweets to a Kafka topic, and a search indexer worker will consume those events and index them in Elasticsearch. This way, search is decoupled from the write path — a slow search index doesn't affect tweet posting."*

### Script 5: Summarizing

> *"Let me step back and summarize the architecture. We have three main flows: The write path goes Client → API → Tweet Service → PostgreSQL, with async fan-out via Kafka to populate timeline caches. The read path goes Client → API → Feed Service → Redis timeline cache, with a database fallback on cache miss. The search path goes Client → API → Search Service → Elasticsearch. Each path is independently scalable."*

---

## Common Drawing Mistakes

### ❌ Mistake 1: Starting too complex

**Bad**: Drawing 15 components in the first minute.
**Fix**: Start with Client → API → Service → Database. Add complexity incrementally.

### ❌ Mistake 2: No labels

**Bad**: Boxes with no text or generic labels ("DB", "Service").
**Fix**: Label every box with technology + purpose: "PostgreSQL (tweets, users)."

### ❌ Mistake 3: No arrows / unclear direction

**Bad**: Boxes floating without connections. Or lines without arrowheads.
**Fix**: Every connection has an arrow showing data flow direction.

### ❌ Mistake 4: Not separating read and write paths

**Bad**: One cluttered diagram with every flow overlapping.
**Fix**: Draw write path first, then read path. Separate if complex.

### ❌ Mistake 5: Drawing implementation details

**Bad**: Drawing Kubernetes pods, Docker containers, EC2 instances.
**Fix**: Draw LOGICAL components (Service, Database, Queue). Implementation is a deep dive topic.

### ❌ Mistake 6: Forgetting async flows

**Bad**: Everything is synchronous Client → Service → DB → Response.
**Fix**: Identify what should be async (notifications, fan-out, processing) and draw queues + workers.

### ❌ Mistake 7: Not leaving space for additions

**Bad**: Diagram fills the entire whiteboard in the first 5 minutes.
**Fix**: Use 40% of the space for the first pass. Leave room to add components later.

### ❌ Mistake 8: Not talking while drawing

**Bad**: Silent drawing for 5 minutes, then presenting the completed diagram.
**Fix**: Narrate as you draw: "I'm adding a cache here because..." The interview is a conversation, not a presentation.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│       HOW TO DRAW HIGH-LEVEL ARCHITECTURE — CHEAT SHEET              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  START: Client → API GW → Service → Database                        │
│  Then ADD: Cache, Queue, CDN, Workers, S3 as needed.                │
│                                                                      │
│  LAYOUT: Left → Right (data flow direction)                          │
│    Left: Clients. Center: Services. Right: Storage.                  │
│                                                                      │
│  FIRST PASS (5 min): Simple skeleton. Don't optimize yet.            │
│  EVOLVE: Add components during deep dives with justification.        │
│                                                                      │
│  LABELS:                                                             │
│    Services: Domain name (Tweet Service, Feed Service)               │
│    Databases: Technology + data (PostgreSQL (tweets))                │
│    Queues: Purpose (Fan-out Queue, Notification Queue)               │
│                                                                      │
│  ARROWS:                                                             │
│    Solid →: Synchronous request-response                             │
│    Dashed -->: Asynchronous / eventual                               │
│    Label when non-obvious (gRPC, Pub/Sub, CDC)                       │
│    Number steps (①②③) when walking through flows                    │
│                                                                      │
│  SEPARATE DIAGRAMS:                                                  │
│    Write path vs Read path (when they differ significantly)          │
│    One diagram per major flow (search, notification, media upload)   │
│                                                                      │
│  ANNOTATIONS:                                                        │
│    Latency targets, scaling strategy, cache TTL, shard key           │
│    Place NEXT TO the relevant component                              │
│                                                                      │
│  SCALING:                                                            │
│    Don't draw 50 boxes. Label "×50" or "(auto-scale 5-100)".        │
│    Show sharding with labeled shard boxes when relevant.             │
│                                                                      │
│  OMIT:                                                               │
│    DNS, TLS, Kubernetes, CI/CD, monitoring (unless asked).           │
│    Implementation details. Specific server counts.                   │
│                                                                      │
│  TALK WHILE YOU DRAW:                                                │
│    "I'm adding X because Y."                                        │
│    "Let me trace the write path: ①→②→③→④."                       │
│    "Based on our latency requirement, I'll add a cache here."        │
│                                                                      │
│  KEY PHRASE: "Let me start simple and evolve as we go."              │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 14: Load Balancing** — The API Gateway / LB component
- **Topic 5: Caching Strategies** — When and how to draw cache components
- **Topic 10: Message Queues** — When to draw async flows
- **Topic 24: Monolith vs Microservices** — How many service boxes to draw
- **Topic 25: API Design** — What the arrows represent
- **Topic 52: Architecting on AWS** — Mapping boxes to AWS services

---

*This document is part of the System Design Interview Deep Dive series.*
