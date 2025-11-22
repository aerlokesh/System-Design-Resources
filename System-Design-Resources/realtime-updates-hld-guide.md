# Real-time Updates Pattern - High-Level Design Guide

## Table of Contents
1. [Introduction](#introduction)
2. [The Two-Hop Architecture](#the-two-hop-architecture)
3. [Client-Server Protocols](#client-server-protocols)
4. [Server-Side Distribution](#server-side-distribution)
5. [Real-World Architecture Examples](#real-world-architecture-examples)
6. [Scaling Strategies](#scaling-strategies)
7. [Design Patterns](#design-patterns)
8. [Decision Framework](#decision-framework)
9. [Interview Guide](#interview-guide)

---

## Introduction

**Real-time Updates** is a system design pattern for delivering immediate data changes from servers to clients. This pattern is critical for applications requiring instant feedback such as chat, trading platforms, collaborative tools, gaming, and live dashboards.

### The Problem

Traditional HTTP follows a request-response model where:
- Client must initiate every request
- Server cannot push updates
- Polling creates massive overhead
- High latency between updates

### Why It Matters

```
Use Case Examples:
├─ Chat Applications → Messages arrive in milliseconds
├─ Trading Platforms → Stock prices update continuously
├─ Collaborative Editing → Multiple users see changes instantly
├─ Live Dashboards → Metrics refresh without reload
└─ Gaming → Player positions sync in real-time
```

---

## The Two-Hop Architecture

Every real-time system must solve two distinct problems:

```
┌───────────────────────────────────────────────────────────────────┐
│                    TWO-HOP ARCHITECTURE                            │
└───────────────────────────────────────────────────────────────────┘

Event → [HOP 2: Server-to-Server] → [HOP 1: Server-to-Client] → User

                     ┌────────────────┐
                     │  Event Source  │
                     │  (Database,    │
                     │   External API,│
                     │   User Action) │
                     └────────┬───────┘
                              │
                              ▼ HOP 2: Distribution
                     ┌────────────────┐
                     │   Pub/Sub      │
                     │   (Redis,      │
                     │    Kafka,      │
                     │    RabbitMQ)   │
                     └────────┬───────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
       ┌──────────┐    ┌──────────┐   ┌──────────┐
       │WebSocket │    │WebSocket │   │WebSocket │
       │Server 1  │    │Server 2  │   │Server 3  │
       └────┬─────┘    └────┬─────┘   └────┬─────┘
            │               │               │
            ▼ HOP 1: Client Protocol        ▼
       ┌─────────┐      ┌─────────┐   ┌─────────┐
       │  User 1 │      │  User 2 │   │  User 3 │
       └─────────┘      └─────────┘   └─────────┘
```

---

## Client-Server Protocols

### Protocol Overview

```
┌──────────────────────────────────────────────────────────────────┐
│              CLIENT-SERVER COMMUNICATION METHODS                  │
└──────────────────────────────────────────────────────────────────┘

1. SIMPLE POLLING
   Client ──GET─────> Server
          <─200/Data──
   [Wait 5 seconds]
   Client ──GET─────> Server
          <─200/Data──

   Latency: 1-30 seconds
   Efficiency: LOW (99% wasted requests)
   Use Case: Email check, rare updates

2. LONG POLLING  
   Client ──GET─────> Server (holds connection)
          [30 sec or until data available]
          <─200/Data──
   Client ──GET─────> Server (immediately)

   Latency: 0.5-3 seconds
   Efficiency: MEDIUM (no wasted polls)
   Use Case: Near real-time, legacy browser support

3. SERVER-SENT EVENTS (SSE)
   Client ──GET─────> Server (keeps connection open)
          <───Data───  (stream)
          <───Data───
          <───Data───

   Latency: 50-200ms
   Efficiency: HIGH (one-way stream)
   Use Case: Stock tickers, news feeds, one-way data

4. WEBSOCKET
   Client ←─────────→ Server (full-duplex)
          ←───Data───
          ───Data───→
          ←───Data───

   Latency: 10-100ms
   Efficiency: VERY HIGH (bidirectional)
   Use Case: Chat, gaming, collaboration

5. WEBRTC
   Client ←─────────→ Client (peer-to-peer)
          No server in data path

   Latency: 10-50ms
   Efficiency: EXTREME (no server)
   Use Case: Gaming, video calls, P2P applications
```

---

### Protocol Comparison Matrix

```
┌──────────────────────────────────────────────────────────────────────┐
│                    PROTOCOL DECISION MATRIX                           │
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│ Feature        │ Polling │ Long Poll │ SSE    │WebSocket│ WebRTC    │
│ ──────────────────────────────────────────────────────────────────   │
│ Latency        │ 1-30s   │ 0.5-3s    │ 50-200ms│ 10-100ms│ 10-50ms  │
│ Bidirectional  │ ✓       │ ✓         │ ✗       │ ✓       │ ✓        │
│ Complexity     │ Low     │ Medium    │ Low     │ Medium  │ High     │
│ Browser        │ All     │ All       │ Most    │ All     │ Most     │
│ Firewall OK    │ ✓       │ ✓         │ ✓       │ Mostly  │ Complex  │
│ Mobile Battery │ Poor    │ Fair      │ Good    │ Good    │ Fair     │
│ Server Load    │ High    │ Medium    │ Low     │ Low     │ None     │
│ Scale          │ Poor    │ Fair      │ Good    │ Excellent│ P2P      │
│                                                                        │
│ ════════════════════════════════════════════════════════════════════  │
│                                                                        │
│ WHEN TO USE:                                                           │
│                                                                        │
│ Polling:       Updates > 30 seconds apart, simplicity critical        │
│ Long Polling:  Near real-time, can't use WebSocket                    │
│ SSE:           One-way server→client, stock tickers, feeds            │
│ WebSocket:     Bidirectional, chat, trading, collaboration            │
│ WebRTC:        Ultra-low latency P2P, gaming, video calls              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Server-Side Distribution

### Distribution Architecture Patterns

```
┌──────────────────────────────────────────────────────────────────┐
│               SERVER-SIDE DISTRIBUTION METHODS                    │
└──────────────────────────────────────────────────────────────────┘

Pattern 1: DIRECT POLLING (Simple)
┌──────────────┐
│ Data Source  │ (External API, Database)
└──────┬───────┘
       │ [Server polls every second]
       ▼
┌──────────────┐
│ WebSocket    │
│ Server       │
└──────┬───────┘
       │
       ▼
   Clients

Pros: Simple, works with any source
Cons: Polling overhead, added latency
Use: External sources you can't modify


Pattern 2: REDIS PUB/SUB (Moderate Scale)
┌──────────────┐
│ Data Source  │
└──────┬───────┘
       │ publish('channel', data)
       ▼
┌──────────────┐
│    Redis     │
│  Pub/Sub     │
└──────┬───────┘
       │
       ├──────────┬──────────┬──────────┐
       ▼          ▼          ▼          ▼
   [Server 1] [Server 2] [Server 3] [Server 4]
       │          │          │          │
       ▼          ▼          ▼          ▼
   Clients    Clients    Clients    Clients

Pros: Fast (<1ms), simple pub/sub
Cons: No persistence, limited scale
Use: <100K concurrent, real-time needed


Pattern 3: KAFKA (High Scale)
┌──────────────┐
│ Data Source  │
└──────┬───────┘
       │ produce(topic, data)
       ▼
┌──────────────────────────────┐
│         Kafka Cluster         │
│  Topic: stock-prices          │
│  Partitions: 100              │
│  Replication: 3               │
└──────┬───────────────────────┘
       │
       │ Consumer Groups
       │
       ├──────────┬──────────┬──────────┐
       ▼          ▼          ▼          ▼
   [Group 1] [Group 2] [Group 3] [Group 4]
   WS Servers WS Servers WS Servers WS Servers
       │          │          │          │
       ▼          ▼          ▼          ▼
   100K conn  100K conn  100K conn  100K conn

Pros: Million msg/sec, persistent, replay
Cons: Complex, higher latency (10ms)
Use: >100K concurrent, need durability
```

---

### Complete Production Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│           FULL PRODUCTION REAL-TIME ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

                    Multiple Event Sources
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
   ┌─────────┐        ┌─────────┐        ┌─────────┐
   │Database │        │External │        │  User   │
   │CDC      │        │  API    │        │ Actions │
   └────┬────┘        └────┬────┘        └────┬────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
                           ▼
                ┌──────────────────────┐
                │   Message Broker     │
                │   (Apache Kafka)     │
                │                      │
                │   Topics:            │
                │   • messages         │
                │   • prices           │
                │   • notifications    │
                │                      │
                │   Partitions: 100    │
                │   Retention: 7 days  │
                └──────────┬───────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
    ┌────────┐        ┌────────┐        ┌────────┐
    │   WS   │        │   WS   │        │   WS   │
    │Server 1│        │Server 2│        │Server 3│
    │        │        │        │        │        │
    │US-EAST │        │US-WEST │        │EU-WEST │
    │10K conn│        │10K conn│        │10K conn│
    └───┬────┘        └───┬────┘        └───┬────┘
        │                 │                 │
        │                 │                 │
   ┌────┴───┐       ┌────┴───┐       ┌────┴───┐
   │Clients │       │Clients │       │Clients │
   │Americas│       │Americas│       │  Europe│
   └────────┘       └────────┘       └────────┘

Capacity: 30K concurrent connections
Throughput: 100K+ messages/second
Latency: 50-150ms end-to-end
Availability: 99.99% (multi-region)
```

---

## Real-World Architecture Examples

### Example 1: WhatsApp Messaging System

```
┌──────────────────────────────────────────────────────────────────┐
│                    WHATSAPP ARCHITECTURE                          │
└──────────────────────────────────────────────────────────────────┘

Scale: 2B+ users, 100B+ messages/day

Architecture Layers:
┌──────────────┐
│ Mobile Apps  │ (iOS/Android)
└──────┬───────┘
       │ WebSocket over TCP
       ▼
┌──────────────────────────┐
│  Chat Servers (Erlang)   │
│  • 1 process per chat    │
│  • Millions concurrent   │
│  • Message routing       │
└──────┬───────────────────┘
       │
       ├─────────────────────┬─────────────────┐
       │                     │                 │
       ▼                     ▼                 ▼
┌──────────┐         ┌──────────┐      ┌──────────┐
│ Message  │         │ Offline  │      │  Push    │
│  Queue   │         │ Storage  │      │Notification│
│(In-Memory│         │(Database)│      │ Service  │
└──────────┘         └──────────┘      └──────────┘

Message Flow:

Sender Online → Receiver Online:
User A sends "Hello"
   ↓
Erlang chat process
   ↓
User B connected? YES
   ↓
Push via WebSocket → Delivered in 50-200ms

Sender Online → Receiver Offline:
User A sends "Hello"
   ↓
Erlang chat process
   ↓
User B connected? NO
   ↓
├─> Store in PostgreSQL (offline messages)
└─> Send push notification (FCM/APNs)

Key Design Decisions:
├─ WebSocket: Bidirectional, persistent connection
├─ Erlang: Lightweight processes (2GB = millions of chats)
├─ Custom Queue: Message ordering guarantees
├─ E2E Encryption: Security and privacy
└─ Multi-DC: Geographic distribution for low latency

Performance Metrics:
• Message latency: 50-200ms (online users)
• Concurrent connections: 10M+ per data center
• Messages/second: 100K+
• Availability: 99.99%
```

---

### Example 2: Robinhood Stock Trading Platform

```
┌──────────────────────────────────────────────────────────────────┐
│                ROBINHOOD REAL-TIME PRICE UPDATES                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 1M+ concurrent users, 8K stocks, 1M price updates/sec

Architecture:

Stock Exchanges (NYSE, NASDAQ)
   ↓ FIX Protocol / WebSocket
┌──────────────────────────────┐
│  Price Aggregation Service   │
│  • Parse feeds               │
│  • Normalize format          │
│  • Deduplicate               │
│  • Enrich metadata           │
│  Throughput: 1M events/sec   │
└──────┬───────────────────────┘
       │
       ├─────────────────────┬─────────────────┐
       ▼                     ▼                 ▼
┌──────────┐          ┌──────────┐      ┌──────────┐
│  Kafka   │          │  Redis   │      │TimescaleDB│
│  Stream  │          │  Cache   │      │Historical│
│100 parts │          │5sec TTL  │      │ (10 years)│
└────┬─────┘          └────┬─────┘      └──────────┘
     │                     │
     │                     │ (Fast lookups)
     ▼                     ▼
┌──────────────────────────────┐
│   WebSocket Server Farm      │
│   • 20 instances             │
│   • 5K connections each      │
│   • Regional deployment      │
│   • Load balanced            │
└──────┬───────────────────────┘
       │
       ├──────────┬──────────┬──────────┐
       ▼          ▼          ▼          ▼
  100K users  100K users 100K users 100K users
  (Mobile)    (Web)      (Mobile)   (Web)

Optimizations:

1. SELECTIVE SUBSCRIPTION
   Problem: Can't send all 8K stocks to all users
   Solution: Users subscribe to watchlist (10-50 stocks)
   Result: 99% reduction in bandwidth

2. THROTTLING
   Problem: Some stocks update 100x/second
   Solution: Max 1 update/sec per stock to client
   Result: 99% reduction in messages

3. BATCHING
   Problem: Sending each stock separately
   Solution: Bundle 10 stocks per WebSocket message
   Result: 10x fewer messages

4. COMPRESSION
   Format: JSON (200 bytes) → Protocol Buffers (50 bytes)
   Result: 75% bandwidth savings

5. REGIONAL DEPLOYMENT
   Deploy: US-East, US-West, EU-West
   Route: User to nearest region
   Result: 50-70% latency reduction

Performance:
• End-to-end latency: 65ms (exchange → client)
• Concurrent users: 100K+
• Price updates: 1M/sec aggregate
• Per-user bandwidth: 5KB/sec
• Connection stability: 99.9%
```

---

### Example 3: Google Docs Collaborative Editing

```
┌──────────────────────────────────────────────────────────────────┐
│              GOOGLE DOCS COLLABORATION ARCHITECTURE               │
└──────────────────────────────────────────────────────────────────┘

Scale: 50+ concurrent editors per document

Architecture:

┌─────────┐              ┌─────────┐              ┌─────────┐
│ User A  │              │Document │              │ User B  │
│ (Types) │─WebSocket─>  │ Server  │  <─WebSocket─│(Views)  │
└─────────┘              └────┬────┘              └─────────┘
                              │
                              ▼
                     ┌────────────────┐
                     │  Operation     │
                     │  Transformation│
                     │  (OT Engine)   │
                     └────┬───────────┘
                          │
               ┌──────────┼──────────┐
               ▼          ▼          ▼
         ┌─────────┐ ┌─────────┐ ┌─────────┐
         │ Redis   │ │Firebase │ │ Postgres│
         │(Cache)  │ │(Realtime│ │(Durable)│
         └─────────┘ └─────────┘ └─────────┘

Operation Flow:

User A types "H" at position 0
   ↓
WebSocket → Document Server
   ↓
Operation: {type: 'insert', pos: 0, char: 'H', version: 1}
   ↓
OT Engine checks for conflicts
   ↓
Transform if concurrent edits detected
   ↓
Apply to master document
   ↓
Broadcast to all other editors
   ↓
User B receives and applies locally
   ↓
Document remains consistent!

Key Components:

1. OPERATIONAL TRANSFORMATION (OT)
   Handles concurrent edits without conflicts
   
   Scenario: Two users type simultaneously
   • User A inserts "X" at position 5
   • User B inserts "Y" at position 3 (before A's edit)
   • OT adjusts A's position: 5 → 6
   • Both edits applied correctly

2. PRESENCE AWARENESS
   • Who's viewing/editing
   • Cursor positions shown
   • Selection highlights
   • Active user indicators

3. VERSION VECTORS
   Each edit has sequence number
   Tracks document history
   Enables conflict resolution

Performance:
• Keystroke latency: 50-150ms
• Concurrent editors: 50+
• Document size: 1M+ characters
• Edit synchronization: 99.99% accurate
```

---

### Example 4: Uber Live Location Tracking

```
┌──────────────────────────────────────────────────────────────────┐
│                 UBER LOCATION TRACKING SYSTEM                     │
└──────────────────────────────────────────────────────────────────┘

Scale: 3M+ drivers, 100M+ riders

Architecture:

Driver Mobile App
   │ Sends location every 5 seconds
   ▼
┌────────────────────┐
│  API Gateway       │
│  • Rate limiting   │
│  • Authentication  │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│ Location Service   │
│ • Validate coords  │
│ • Enrich data      │
└────────┬───────────┘
         │
         ▼
┌────────────────────────────┐
│   Kafka Topic:             │
│   driver-locations         │
│   Partitions: 50           │
│   Retention: 24 hours      │
└────────┬───────────────────┘
         │
         ├────────────┬─────────────┬────────────┐
         ▼            ▼             ▼            ▼
    ┌────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
    │ Redis  │  │Database │  │Analytics│  │WebSocket│
    │Geospa- │  │HistoryH │  │ Service │  │ Servers │
    │tial    │  │         │  │         │  │         │
    └────────┘  └─────────┘  └─────────┘  └────┬────┘
         │                                       │
         │ (Fast lookup: find nearby drivers)    │
         │                                       ▼
         │                                  ┌─────────┐
         │                                  │ Rider   │
         └─────────────────────────────────>│  Apps   │
                    (Query nearby)          └─────────┘

Data Flow:

1. Driver Updates Location:
   Driver at (37.7749, -122.4194)
      ↓
   API validates and publishes to Kafka
      ↓
   Multiple consumers process:
      ├─> Redis: GEOADD drivers 37.7749 -122.4194 driver_id
      ├─> Database: INSERT location history
      └─> WebSocket: Push to matched riders

2. Rider Sees Driver Location:
   Rider requests nearby drivers
      ↓
   Query Redis: GEORADIUS 37.7749 -122.4194 5km
      ↓
   Returns: [driver_1, driver_2, driver_3]
      ↓
   Establish WebSocket subscriptions
      ↓
   Receive location updates every 5 seconds

3. Trip in Progress:
   Rider subscribed to specific driver
      ↓
   Driver location updates
      ↓
   Kafka → WebSocket server
      ↓
   Push to rider (show car on map)

Optimizations:

1. GEOSPATIAL INDEXING
   • Redis GEO commands for fast radius queries
   • Find drivers within 5km in <10ms
   • Updates in O(log N) time

2. SELECTIVE UPDATES
   • Only push to riders with active trips
   • Don't push to idle riders
   • Reduces updates by 95%

3. UPDATE FREQUENCY
   • Driver sends: every 5 seconds
   • Rider receives: every 5 seconds
   • Not truly real-time (battery optimization)
   • Acceptable trade-off

Performance:
• Location update latency: 5-10 seconds
• Geo query latency: <10ms
• Concurrent drivers: 3M+
• Updates/second: 600K (3M drivers / 5 seconds)
```

---

### Example 5: Slack Real-time Messaging

```
┌──────────────────────────────────────────────────────────────────┐
│                     SLACK ARCHITECTURE                            │
└──────────────────────────────────────────────────────────────────┘

Scale: 10M+ daily active users, Billions of messages/day

High-Level Flow:

User sends message
   ↓
┌────────────────────────┐
│   API Gateway          │
│   • Authentication     │
│   • Rate limiting      │
│   • Request routing    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Message Service       │
│  • Validate message    │
│  • Check permissions   │
│  • Generate ID         │
│  • Store in database   │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Message Queue        │
│   (Internal system)    │
│   • Reliable delivery  │
│   • Ordered by channel │
└────────┬───────────────┘
         │
         ├─────────────────────────────┬────────────────┐
         ▼                             ▼                ▼
┌─────────────────┐          ┌──────────────┐  ┌──────────────┐
│ WebSocket Farm  │          │  Database    │  │  Search      │
│ • 100+ servers  │          │  (Persist)   │  │  Index       │
│ • Regional      │          │  (Cassandra) │  │(Elasticsearch│
│ • 10K conn each │          └──────────────┘  └──────────────┘
└────────┬────────┘
         │
         ├───────────────────┬───────────────┐
         ▼                   ▼               ▼
    ┌────────┐         ┌────────┐      ┌────────┐
    │Online  │         │Online  │      │Offline │
    │User 1  │         │User 2  │      │User 3  │
    └────────┘         └────────┘      └────────┘
         │                   │               │
         │                   │               └─> Push notification
         ▼                   ▼
    Sees message       Sees message

Channel Architecture:

Channel Types:
├─ Direct Message (1:1) → Simple routing
├─ Private Channel (Small group) → Broadcast to members
├─ Public Channel (Large) → Fan-out on read
└─ Shared Channel (Cross-workspace) → Complex routing

Optimization: Channel Size Strategy
├─ Small (<100 users): Fan-out on write
│  └─> Push to all members immediately
│
├─ Medium (100-10K): Hybrid approach
│  └─> Push to active users, others on-demand
│
└─ Large (>10K users): Fan-out on read
   └─> Don't push to all, fetch on channel open

Performance:
• Message delivery: <500ms to online users
• Typing indicators: <100ms
• Presence updates: <1 second
• File upload: Async, notification on complete
```

---

### Example 6: Multiplayer Gaming (Fortnite Architecture)

```
┌──────────────────────────────────────────────────────────────────┐
│              MULTIPLAYER GAME REAL-TIME SYNC                      │
└──────────────────────────────────────────────────────────────────┘

Scale: 100 players per match, Millions of concurrent matches

Architecture:

┌──────────────┐
│ Game Client  │ (PC/Console/Mobile)
│ • Renders    │
│ • Predicts   │
│ • Reconciles │
└──────┬───────┘
       │ UDP (not TCP!)
       │ Sends: 60 packets/sec (every 16ms)
       ▼
┌──────────────────────┐
│  Game Server         │
│  • Authoritative     │
│  • Physics sim       │
│  • Collision detect  │
│  • State broadcast   │
│  Rate: 20-30 Hz      │
└──────┬───────────────┘
       │
       │ Broadcasts state updates
       ├─────────┬─────────┬─────────┬─────────┐
       ▼         ▼         ▼         ▼         ▼
   Player 1  Player 2  Player 3  ... Player 100

Game State Update Flow:

Input → Prediction → Network → Server → Broadcast → Reconciliation

Player presses "W" (move forward)
   ↓
CLIENT-SIDE PREDICTION
   Apply movement locally (don't wait for server)
   Show immediate feedback
   ↓
SEND TO SERVER
   UDP packet: {input: 'W', sequence: 12345, timestamp}
   ↓
SERVER PROCESSING
   Validate input
   Apply to authoritative state
   Run physics simulation
   Detect collisions
   ↓
BROADCAST UPDATE
   Send to all players: {
      player_id: 123,
      position: (x, y, z),
      velocity: (vx, vy, vz),
      sequence: 12345
   }
   ↓
CLIENT RECONCILIATION
   Compare predicted vs server state
   If close: smooth interpolation
   If far: snap to server state

Key Optimizations:

1. INTEREST MANAGEMENT
   Problem: Don't need to know about all 100 players
   Solution: Only send updates for nearby players (<50m)
   Result: 90% bandwidth reduction

2. DELTA COMPRESSION
   Problem: Sending full state is expensive
   Solution: Only send what changed
   Example:
   • Full state: 500 bytes
   • Delta: 50 bytes (10x smaller)

3. QUANTIZATION
   Problem: Floating point precision unnecessary
   Solution: Reduce precision
   • Position: 16-bit int (cm precision)
   • Rotation: 8-bit int (1.4° precision)
   • Velocity: 12-bit int
   Result: 60% smaller packets

4. LAG COMPENSATION
   • Server rewinds time for hit detection
   • Accounts for player latency
   • Client sees immediate action
   • Server validates retroactively

Network Requirements:
• Latency: 30-100ms acceptable
• Packet loss: <1% (UDP tolerates some loss)
• Jitter: <30ms variance
• Update rate: 20-60 Hz

Performance:
• Input latency: 16ms (local prediction)
• Server latency: 50ms (network + processing)
• Total: 66ms (feels instant due to prediction)
• Players per match: 100
• Matches globally: Millions concurrent
```

---

### Example 7: ESPN Live Sports Scoreboard

```
┌──────────────────────────────────────────────────────────────────┐
│                   LIVE SPORTS SCOREBOARD                          │
└──────────────────────────────────────────────────────────────────┘

Scale: 50 concurrent games, 50M+ viewers during playoffs

Architecture:

Official Game Data Feed
   ↓
┌────────────────────────┐
│ Ingestion Service      │
│ • Validates data       │
│ • Enriches stats       │
│ • Deduplicates         │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Kafka:game-events    │
│   Partitions: 50       │
│   (1 per game)         │
└────────┬───────────────┘
         │
         ├────────────┬────────────┬────────────┐
         ▼            ▼            ▼            ▼
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │Analytics│  │ Redis  │  │Database│  │WebSocket│
    │Processor│  │ Cache  │  │Archive │  │  Farm  │
    │         │  │1sec TTL│  │        │  │        │
    └────────┘  └────────┘  └────────┘  └────┬───┘
                                              │
                     ┌────────────────────────┤
                     │                        │
                     ▼                        ▼
            ┌──────────────┐        ┌──────────────┐
            │  1M viewers  │        │  1M viewers  │
            │ (Same game)  │        │(Other games) │
            └──────────────┘        └──────────────┘

Event Types:
├─ SCORE_UPDATE → Highest priority
├─ TIMEOUT → Medium priority
├─ SUBSTITUTION → Low priority
├─ STAT_UPDATE → Low priority
└─ GAME_END → Highest priority

Broadcast Strategy:

Popular Game (1M viewers):
   Single update from source
      ↓
   Kafka partition
      ↓
   100 WebSocket servers (10K connections each)
      ↓
   Redis Pub/Sub coordinates servers
      ↓
   Broadcast to all 1M viewers in <200ms

Multi-Tier Caching:

Tier 1: CDN (CloudFront)
• Static: Team logos, player photos
• TTL: 24 hours
• Hit rate: 98%

Tier 2: Redis Cache
• Dynamic: Live scores, recent plays
• TTL: 1-30 seconds
• Hit rate: 92%

Tier 3: Database
• Historical: Full game logs
• For cache misses and analytics

Performance:
• Update latency: 100-300ms (feed to viewer)
• Concurrent games: 50+
• Peak viewers: 50M+ (playoffs)
• Updates per game: 100-200/minute
• Bandwidth per viewer: 1KB/sec
```

---

### Example 8: Cryptocurrency Exchange Order Book

```
┌──────────────────────────────────────────────────────────────────┐
│                CRYPTO EXCHANGE ORDER BOOK                         │
└──────────────────────────────────────────────────────────────────┘

Scale: 100K+ concurrent traders, 1000 updates/sec per symbol

Challenge: Ultra-low latency updates with high accuracy

Architecture:

┌────────────────────┐
│ Trading Engine     │ (C++ for performance)
│ • Match orders     │
│ • Update book      │
│ • Generate events  │
│ Latency: <1ms      │
└────────┬───────────┘
         │
         │ Shared memory / Unix socket
         ▼
┌────────────────────┐
│ WebSocket Gateway  │ (Rust for low-level control)
│ • Parse events     │
│ • Format messages  │
│ • Broadcast        │
│ Latency: <5ms      │
└────────┬───────────┘
         │
         ├─────────────────┬─────────────────┐
         ▼                 ▼                 ▼
    ┌────────┐        ┌────────┐       ┌────────┐
    │Trader 1│        │Trader 2│       │Trader 3│
    │(Pro)   │        │(Retail)│       │(Bot)   │
    └────────┘        └────────┘       └────────┘

Order Book Structure:
{
  symbol: "BTC/USD",
  bids: [
    [price: 43250.00, qty: 0.5],
    [price: 43249.50, qty: 1.2],
    [price: 43249.00, qty: 0.8]
  ],
  asks: [
    [price: 43250.50, qty: 0.7],
    [price: 43251.00, qty: 1.5]
  ],
  sequence: 12345678,
  timestamp: 1704723845123
}

Update Types:

1. FULL SNAPSHOT (Initial)
   When: Client first connects
   Size: 500-1000 price levels
   Data: ~50KB compressed
   Frequency: Once per connection

2. DELTA UPDATES (Ongoing)
   When: Order book changes
   Size: 1-5 price levels
   Data: ~100 bytes per update
   Frequency: 100-1000/second

Delta Update Format:
{
  type: "delta",
  symbol: "BTC/USD",
  changes: [
    {side: "bid", price: 43250.00, qty: 0.7},  // Update
    {side: "ask", price: 43252.00, qty: 0}     // Remove (qty=0)
  ],
  sequence: 12345679
}

Client Reconstruction:
1. Receive full snapshot on connect
2. Apply deltas sequentially
3. Maintain local order book
4. If sequence gap → request full snapshot
5. Sort by price after each update

Optimizations:

1. CONFLATION (Slow Clients)
   Problem: Client can't keep up with 1000 updates/sec
   Solution: Merge multiple updates into one
   Example: 10 updates in 100ms → 1 combined delta
   Result: 10x fewer messages, slight staleness

2. LEVEL GROUPING
   Problem: Too many price levels (granular)
   Solution: Group by $1 or $10 increments
   Display: "~1.5 BTC between $43,200-$43,300"
   Result: 90% fewer updates

3. TIERED SERVICE
   Pro traders: Full depth, binary protocol, <10ms
   Retail: Top 20 levels, JSON, <50ms
   Mobile: Top 10 levels, compressed, <200ms

Performance:
• Latency: 5-20ms (engine to client)
• Update rate: 100-1000/sec per symbol
• Concurrent connections: 100K+
• Data throughput: 10-50 MB/sec aggregate
• Accuracy: 99.999% (no missed updates)
```

---

### Example 9: IoT Sensor Monitoring Dashboard

```
┌──────────────────────────────────────────────────────────────────┐
│                IOT DASHBOARD REAL-TIME MONITORING                 │
└──────────────────────────────────────────────────────────────────┘

Scale: 10K IoT devices, 1K concurrent dashboard viewers

Architecture:

┌────────────────────┐
│ IoT Sensors        │ (10K devices)
│ • Temperature      │
│ • Humidity         │
│ • Pressure         │
│ Send: Every 5 sec  │
└────────┬───────────┘
         │ MQTT Protocol
         ▼
┌────────────────────┐
│   MQTT Broker      │ (Eclipse Mosquitto)
│   • Topic routing  │
│   • QoS levels     │
│   • Persistent     │
│   Topics: 10K      │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│ Kafka Bridge       │
│ • MQTT → Kafka     │
│ • Protocol adapt   │
└────────┬───────────┘
         │
         ▼
┌────────────────────────┐
│ Kafka:sensor-readings  │
│ Partitions: 20         │
│ Retention: 30 days     │
└────────┬───────────────┘
         │
         ├──────────┬──────────┬──────────┐
         ▼          ▼          ▼          ▼
    ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
    │ Stream │ │TimeSeri│ │Analytics│ │WebSocket│
    │Processor│ │es DB   │ │ Engine │ │ Gateway │
    │        │ │        │ │        │ │        │
    │• Alerts│ │• Store │ │• Trends│ │• Push  │
    │• Agg   │ │• Query │ │• ML    │ │• Filter│
    └────────┘ └────────┘ └────────┘ └────┬───┘
                                          │
                                          ▼
                                    ┌──────────┐
                                    │Dashboard │
                                    │ Users    │
                                    └──────────┘

Data Flow:

Device Reading:
{
  deviceId: "sensor_001",
  temperature: 23.5,
  humidity: 45.2,
  pressure: 1013.25,
  timestamp: 1704723845000,
  location: "Building A, Floor 3"
}

Alert Generation:
IF temperature > 30°C:
   ↓
Generate alert
   ↓
High priority Kafka topic
   ↓
WebSocket push to dashboard
   ↓
Visual alert + sound notification

Dashboard Optimization:

1. DOWNSAMPLING
   Problem: 10K devices × 0.2 updates/sec = 2K updates/sec
   Solution: Dashboard shows 100 devices at once
   Result: Subscribe only to visible devices
   Impact: 100x reduction in updates

2. AGGREGATION
   Problem: Individual sensor too granular
   Solution: Show floor/building averages
   Example: "Floor 3 avg temp: 23.8°C" vs 20 individual sensors
   Result: 20x fewer updates

3. UPDATE TIERS
   Critical alerts: Immediate push
   Normal readings: Batched every 10 seconds
   Historical data: On-demand query

Performance:
• Sensor-to-dashboard: 200-500ms
• Alert delivery: <1 second
• Dashboard updates: 5-10/second
• Concurrent dashboards: 1K
• Device scalability: 100K+ (with partitioning)
```

---

### Example 10: Live Auction Platform

```
┌──────────────────────────────────────────────────────────────────┐
│                  LIVE AUCTION BIDDING SYSTEM                      │
└──────────────────────────────────────────────────────────────────┘

Scale: 10K concurrent auctions, 100K watchers

Requirements:
• Show current bid within 100ms
• Handle concurrent bids correctly
• Update countdown timer
• Notify outbid users

Architecture:

┌──────────┐
│ Bidder   │ POST /bid {amount: $105}
└────┬─────┘
     │
     ▼
┌──────────────────────┐
│  API Gateway         │
│  • Auth check        │
│  • Rate limit        │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│  Bidding Service     │
│  • Validate bid      │
│  • Check funds       │
│  • Acquire lock      │
│  • Update (atomic)   │
│  • Release lock      │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│   Redis (State)      │
│   • Current bid      │
│   • Leading bidder   │
│   • Bid count        │
│   • TTL: auction end │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│  Event Publisher     │
│  (Kafka/Redis)       │
└────────┬─────────────┘
         │
         ├─────────────────┬─────────────┬───────────────┐
         ▼                 ▼             ▼               ▼
   ┌──────────┐    ┌──────────┐  ┌──────────┐  ┌──────────┐
   │WebSocket │    │Notification│  │Database  │  │Analytics │
   │Broadcast │    │  Service   │  │ (Audit)  │  │  Engine  │
   └────┬─────┘    └──────────┘  └──────────┘  └──────────┘
        │
        ├──────────────────┬──────────────────┐
        ▼                  ▼                  ▼
   ┌────────┐        ┌────────┐        ┌────────┐
   │User A  │        │User B  │        │User C  │
   │(Bidder)│        │(Watching)       │(Watching)
   └────────┘        └────────┘        └────────┘

Bid Conflict Resolution:

Scenario: Two users bid $105 simultaneously

Time t=100ms: User A bids $105
Time t=102ms: User B bids $105

Server Processing:
1. User A acquires lock first (t=101ms)
2. User A's bid accepted → current = $105
3. User A releases lock
4. User B acquires lock (t=103ms)
5. User B's bid rejected (not > $105 + min_increment)
6. User B receives error: "Bid too low"

Message to Watchers:
{
  type: "BID_UPDATE",
  auctionId: "auction_123",
  currentBid: 105.00,
  bidder: "user_***",  // Masked for privacy
  bidCount: 15,
  timeRemaining: 120,  // seconds
  timestamp: 1704723845000
}

Countdown Timer Strategy:
• Server sends time_remaining + timestamp
• Client calculates: remaining - (now - timestamp)
• Resync every 10 seconds (prevent drift)
• When timer hits 0, disable bid button

Performance:
• Bid processing: <50ms
• Update to all watchers: 100ms
• Concurrent watchers per auction: 10K
• Lock acquisition success: 99.9%
• Timer accuracy: ±100ms
```

---

## Scaling Strategies

### Horizontal Scaling Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│              SCALING TO MILLIONS OF CONNECTIONS                   │
└──────────────────────────────────────────────────────────────────┘

Single Server Limit: 10-20K connections per server

Scale to 1M Users:

                    ┌──────────────────┐
                    │  Load Balancer   │
                    │  • Sticky session│
                    │  • Health checks │
                    │  • Geographic LB │
                    └────────┬─────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
           ▼                 ▼                 ▼
      ┌────────┐        ┌────────┐        ┌────────┐
      │WS Srv 1│        │WS Srv 2│   ...  │WS Srv N│
      │10K conn│        │10K conn│        │10K conn│
      └───┬────┘        └───┬────┘        └───┬────┘
          │                  │                 │
          └──────────────────┼─────────────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │   Redis Pub/Sub  │
                    │   or Kafka       │
                    │   (Coordination) │
                    └──────────────────┘

Calculation:
1M users = 100 servers × 10K connections

Key Considerations:

1. STICKY SESSIONS
   Problem: WebSocket is stateful
   Solution: Route same user to same server
   Methods:
   • IP hash: Hash(client_IP) % num_servers
   • Cookie-based: Load balancer sets cookie
   • Connection ID: Consistent hashing

2. SERVER COORDINATION
   Problem: User A on Server 1, User B on Server 2
   Solution: Redis Pub/Sub or Kafka
   Flow:
   Server 1 receives message
      ↓
   Publish to Redis channel
      ↓
   All servers receive
      ↓
   Server 2 pushes to User B

3. CONNECTION POOLING
   Problem: Each connection uses memory
   Solution:
   • Limit connections per server (10-20K)
   • Monitor memory usage
   • Reject new connections when full
   • Auto-scale based on load

4. GEOGRAPHIC DISTRIBUTION
   Deploy servers in multiple regions:
   • US-East (serves Americas East)
   • US-West (serves Americas West)
   • EU-West (serves Europe)
   • AP-South (serves Asia)
   
   Benefits:
   • 50-70% latency reduction
   • Regional failover
   • Better user experience
```

---

### Connection Management Patterns

```
┌──────────────────────────────────────────────────────────────────┐
│                  CONNECTION LIFECYCLE                             │
└──────────────────────────────────────────────────────────────────┘

Phase 1: ESTABLISHMENT
Client connects
   ↓
Load balancer routes (sticky)
   ↓
Server accepts connection
   ↓
Client sends auth token
   ↓
Server validates and registers
   ↓
Connection established

Phase 2: ACTIVE COMMUNICATION
Client ←──────────→ Server
   Heartbeat (every 30s)
   Data messages
   Subscription updates

Phase 3: FAILURE DETECTION
Client misses heartbeat
   ↓
Server timeout (60 seconds)
   ↓
Server closes connection
   ↓
Client detects close
   ↓
Client reconnects (with backoff)

Phase 4: GRACEFUL SHUTDOWN
Server needs to shutdown
   ↓
Send reconnect message to clients
   ↓
Clients disconnect and reconnect elsewhere
   ↓
Server waits (max 30 seconds)
   ↓
Force close remaining
   ↓
Server shuts down

Reconnection Strategy:

Exponential Backoff:
Attempt 1: Wait 1 second
Attempt 2: Wait 2 seconds
Attempt 3: Wait 4 seconds
Attempt 4: Wait 8 seconds
Attempt 5+: Wait 30 seconds (max)

With Jitter:
Add random 0-1000ms to prevent thundering herd
Result: Connections spread out over time
```

---

## Design Patterns

### Pattern 1: Subscription Management

```
┌──────────────────────────────────────────────────────────────────┐
│              SUBSCRIPTION TRACKING ARCHITECTURE                   │
└──────────────────────────────────────────────────────────────────┘

Data Structures:

Index 1: User → Resources
{
  "user_123": ["AAPL", "GOOGL", "MSFT"],
  "user_456": ["TSLA", "NVDA"],
  ...
}

Index 2: Resource → Users (Reverse Index)
{
  "AAPL": ["user_123", "user_789", ...],
  "GOOGL": ["user_123", "user_456", ...],
  ...
}

Broadcast Algorithm:

Stock price update for AAPL
   ↓
Lookup reverse index: Get all subscribers of AAPL
   ↓
subscribers = ["user_123", "user_789", "user_234", ...]
   ↓
For each subscriber:
   Get WebSocket connection
   Send price update
   
Performance:
• Lookup: O(1) with hash map
• Broadcast: O(N) where N = subscribers
• Memory: 10M users × 10 stocks × 8 bytes = 800MB

Optimization: Don't Broadcast to All
Only send to users who:
1. Have active WebSocket connection
2. Are subscribed to the resource
3. Haven't received update recently (throttling)

Result: Send to 1K users instead of 100K
```

---

### Pattern 2: Message Batching

```
┌──────────────────────────────────────────────────────────────────┐
│                    MESSAGE BATCHING PATTERN                       │
└──────────────────────────────────────────────────────────────────┘

Problem: Sending individual updates is inefficient

Without Batching:
Server has 10 stock updates
   ↓
Send 10 separate WebSocket messages
   ↓
Overhead: 10 × (headers + framing) = High

With Batching:
Server collects updates for 100ms
   ↓
Bundle into single message
   ↓
Send 1 WebSocket message with 10 updates
   ↓
Overhead: 1 × (headers + framing) = Low

Message Format:
{
  type: "batch",
  updates: [
    {symbol: "AAPL", price: 178.50},
    {symbol: "GOOGL", price: 142.30},
    {symbol: "MSFT", price: 380.20},
    ...
  ],
  timestamp: 1704723845000
}

Trade-off Analysis:
• Latency: +100ms (batching window)
• Bandwidth: -90% (fewer messages)
• CPU: -80% (less serialization overhead)
• User experience: Minimal impact

Decision: Batch for high-frequency updates
```

---

### Pattern 3: Selective Broadcasting

```
┌──────────────────────────────────────────────────────────────────┐
│              SELECTIVE VS BROADCAST DISTRIBUTION                  │
└──────────────────────────────────────────────────────────────────┘

Broadcast to All (Inefficient):
Update happens
   ↓
Send to ALL 100K connected users
   ↓
99% don't care about this update
   ↓
Wasted bandwidth and processing

Selective Distribution (Efficient):
Update happens for resource "AAPL"
   ↓
Query: Who's subscribed to "AAPL"?
   ↓
Result: 1,000 users
   ↓
Send only to those 1,000 users
   ↓
100x more efficient!

Implementation:

Subscription Registry:
┌─────────────────────────────────────────┐
│ Resource: AAPL                          │
│ Subscribers: [user_1, user_2, ..., user_1000] │
└─────────────────────────────────────────┘

When update arrives:
   subscribers = registry.get("AAPL")
   for user in subscribers:
      connection = getConnection(user)
      connection.send(update)

Memory Calculation:
100K users × average 10 subscriptions = 1M subscriptions
1M × 16 bytes (pointer) = 16MB (easily manageable)
```

---

### Pattern 4: Fan-out Strategies

```
┌──────────────────────────────────────────────────────────────────┐
│                    FAN-OUT PATTERNS                               │
└──────────────────────────────────────────────────────────────────┘

Scenario: User posts update, has 10M followers

Option 1: FAN-OUT ON WRITE
Celebrity posts
   ↓
Write to 10M user feeds immediately
   ↓
Takes: Minutes to complete
   ↓
High write load
   ↓
Expensive but fast reads

Option 2: FAN-OUT ON READ
Celebrity posts
   ↓
Store in celebrity's timeline only
   ↓
When follower opens app:
   Fetch celebrity posts + merge
   ↓
Fast write, slower reads

Option 3: HYBRID (Best)
Celebrity posts
   ↓
Check follower count
   ↓
├─> <1K followers: Fan-out on write
├─> 1K-1M followers: Push to active only
└─> >1M followers: Fan-out on read

Active Detection:
• Bloom filter tracks active users
• Push to users active in last 24 hours
• Others fetch on next app open

Result:
• Write load: 95% reduction
• Read latency: Minimal impact
• Best of both worlds
```

---

## Decision Framework

### Protocol Selection Flowchart

```
┌──────────────────────────────────────────────────────────────────┐
│              REAL-TIME PROTOCOL DECISION TREE                     │
└──────────────────────────────────────────────────────────────────┘

START: Need real-time updates?
   │
   ├─> NO → Use regular REST API
   │
   └─> YES
        │
        ▼
   Need bidirectional communication?
        │
        ├─> NO (Server → Client only)
        │    │
        │    ▼
        │   Latency requirement?
        │    │
        │    ├─> >1 second → Long Polling
        │    └─> <1 second → SSE
        │
        └─> YES (Bidirectional)
             │
             ▼
        Need ultra-low latency (<50ms)?
             │
             ├─> YES
             │    │
             │    ▼
             │   Peer-to-peer possible?
             │    │
             │    ├─> YES → WebRTC
             │    └─> NO → WebSocket (optimized)
             │
             └─> NO
                  │
                  ▼
             Is it a standard use case?
                  │
                  ├─> Chat/Messaging → WebSocket
                  ├─> Gaming → WebSocket or WebRTC
                  ├─> Collaboration → WebSocket
                  ├─> Trading → WebSocket
                  └─> Dashboard → SSE or WebSocket
```

---

### Scale-Based Architecture Selection

```
┌──────────────────────────────────────────────────────────────────┐
│            ARCHITECTURE BY SCALE REQUIREMENTS                     │
└──────────────────────────────────────────────────────────────────┘

< 1,000 Users (Small Scale)
├─ Protocol: Any (WebSocket recommended)
├─ Distribution: Single server
├─ Message Broker: Optional (direct broadcast)
└─ Cost: Low, simple deployment

Architecture:
   Client ←WebSocket→ Single Server ←Poll→ Data Source


1K - 100K Users (Medium Scale)
├─ Protocol: WebSocket or SSE
├─ Distribution: 5-10 servers with load balancer
├─ Message Broker: Redis Pub/Sub
└─ Cost: Moderate, needs coordination

Architecture:
                     ┌──────────┐
                     │   Redis  │
                     │  Pub/Sub │
                     └─────┬────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
   Client ←WebSocket→ Server 1    Server 2    Server 3


100K - 1M Users (Large Scale)
├─ Protocol: WebSocket
├─ Distribution: 50-100 servers, multi-region
├─ Message Broker: Kafka
└─ Cost: High, complex infrastructure

Architecture:
              ┌────────────┐
              │   Kafka    │
              │  Cluster   │
              └──────┬─────┘
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
   [Server Farm] [Server Farm] [Server Farm]
    US-East       US-West       EU-West
     30K conn      30K conn      40K conn


> 1M Users (Internet Scale)
├─ Protocol: WebSocket + CDN
├─ Distribution: 100+ servers, global deployment
├─ Message Broker: Kafka + regional clusters
└─ Cost: Very high, enterprise infrastructure

Architecture:
         ┌──────────┐         ┌──────────┐
         │  Kafka   │         │  Kafka   │
         │US Cluster│         │EU Cluster│
         └────┬─────┘         └────┬─────┘
              │                    │
    ┌─────────┼──────────┐  ┌──────┼───────────┐
    ▼         ▼          ▼  ▼      ▼           ▼
[US-E-1] [US-E-2] [US-W] [EU-W] [EU-C] [AP-SE]
 20K conn  20K conn 15K    25K    20K     20K

Total: 120K+ connections across 100+ servers
```

---

### Load Balancing Strategies

```
┌──────────────────────────────────────────────────────────────────┐
│                WEBSOCKET LOAD BALANCING                           │
└──────────────────────────────────────────────────────────────────┘

Challenge: WebSocket connections are stateful
Solution: Sticky sessions required

Method 1: IP HASH
   Client IP: 192.168.1.100
   Hash(IP) % 3 = 1
   Route to Server 1
   
   Pros: Simple, works well
   Cons: Imbalance if many users behind NAT

Method 2: COOKIE-BASED
   First request → Set cookie with server ID
   Subsequent requests → Route by cookie
   
   Pros: Perfect distribution
   Cons: Requires cookie support

Method 3: CONSISTENT HASHING
   Hash(userId) → point on ring
   Find nearest server on ring
   
   Pros: Minimal rehashing on server add/remove
   Cons: More complex

Load Balancer Configuration:

┌──────────────────────────┐
│    Application LB        │
│    (ALB/HAProxy)         │
│                          │
│  Features needed:        │
│  • WebSocket support     │
│  • Sticky sessions       │
│  • Health checks         │
│  • SSL termination       │
└──────────────────────────┘
```

---

## Performance Optimization Patterns

### Optimization 1: Throttling

```
┌──────────────────────────────────────────────────────────────────┐
│                     UPDATE THROTTLING                             │
└──────────────────────────────────────────────────────────────────┘

Problem: Stock updates 100x per second, users don't need all

Without Throttling:
Stock AAPL updates 100 times in 1 second
   ↓
Send 100 WebSocket messages
   ↓
User can't process that fast anyway
   ↓
Wasted bandwidth

With Throttling:
Stock AAPL updates 100 times in 1 second
   ↓
Keep only latest update per second
   ↓
Send 1 WebSocket message with latest price
   ↓
User sees smooth updates

Implementation Strategy:
• Window-based: Collect updates for 1 second, send latest
• Token bucket: Allow max N updates per time window
• Adaptive: Slow down if client can't keep up

Result:
• Bandwidth: 99% reduction
• Latency: +1 second (acceptable)
• User experience: Still feels real-time
```

---

### Optimization 2: Compression

```
┌──────────────────────────────────────────────────────────────────┐
│                    MESSAGE COMPRESSION                            │
└──────────────────────────────────────────────────────────────────┘

Comparison of Formats:

JSON (Baseline):
{
  "symbol": "AAPL",
  "price": 178.50,
  "change": 2.30,
  "volume": 45623190
}
Size: 200 bytes

JSON Minified:
{"s":"AAPL","p":178.50,"c":2.30,"v":45623190}
Size: 120 bytes (40% savings)

Protocol Buffers (Binary):
Binary encoding of same data
Size: 50 bytes (75% savings)

MessagePack:
Binary encoding, more compact than JSON
Size: 80 bytes (60% savings)

When to Compress:
• Message > 1KB: Always compress
• Message 100B-1KB: Compress if high frequency
• Message < 100B: Not worth overhead

Impact at Scale:
100K users × 10 messages/sec × 200 bytes = 200MB/sec
100K users × 10 messages/sec × 50 bytes = 50MB/sec
Savings: 150MB/sec = $15K/month bandwidth cost
```

---

### Optimization 3: Delta Updates

```
┌──────────────────────────────────────────────────────────────────┐
│                      DELTA UPDATES                                │
└──────────────────────────────────────────────────────────────────┘

Concept: Send only what changed, not full state

Full State Update:
{
  players: [
    {id: 1, pos: (100, 200), health: 95, ammo: 30},
    {id: 2, pos: (150, 250), health: 80, ammo: 25},
    {id: 3, pos: (200, 300), health: 100, ammo: 30},
    ... (100 players)
  ]
}
Size: 50KB per update

Delta Update:
{
  changed: [
    {id: 1, pos: (101, 201)},  // Only position changed
    {id: 2, health: 75}        // Only health changed
  ]
}
Size: 500 bytes per update (100x smaller!)

Use Cases:
• Gaming: Player positions
• Order book: Price level changes
• Documents: Character edits
• Maps: Object movements

Strategy:
1. Client maintains local state
2. Server sends initial full snapshot
3. Server sends deltas for changes
4. Client applies deltas to local state
5. If state diverges, request full snapshot
```

---

### Optimization 4: Priority Queuing

```
┌──────────────────────────────────────────────────────────────────┐
│                    PRIORITY-BASED DELIVERY                        │
└──────────────────────────────────────────────────────────────────┘

Problem: Not all updates are equally important

Message Priority Levels:

CRITICAL (P0): Immediate delivery
• Trade execution confirmations
• Payment status updates
• Security alerts
• System errors

HIGH (P1): <100ms delivery
• Chat messages
• Stock price updates
• Game state changes

MEDIUM (P2): <1s delivery
• Typing indicators
• Presence updates
• Non-critical notifications

LOW (P3): <5s delivery, can batch
• Analytics events
• Read receipts
• Status updates

Implementation:

┌──────────────────────┐
│  Message Router      │
└──────┬───────────────┘
       │
       ├─────────┬─────────┬─────────┐
       │         │         │         │
       ▼         ▼         ▼         ▼
   [P0 Queue][P1 Queue][P2 Queue][P3 Queue]
      │         │         │         │
      │         │         │         └─> Batch every 5s
      │         │         └─> Batch every 1s
      │         └─> Send immediately
      └─> Send immediately

Result:
• Critical messages never delayed
• Low-priority batched for efficiency
• Bandwidth reduction: 60%
```

---

## Trade-offs & Decision Matrix

```
┌──────────────────────────────────────────────────────────────────┐
│                    KEY TRADE-OFFS                                 │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│ Trade-off 1: LATENCY vs BATTERY LIFE                              │
│ ├─ More frequent updates = Lower latency = More battery drain    │
│ └─ Decision: Balance based on use case                            │
│    • Gaming: 60 Hz (critical)                                     │
│    • Chat: On-demand (efficient)                                  │
│    • Location: 5 sec interval (balanced)                          │
│                                                                    │
│ Trade-off 2: PUSH vs PULL                                         │
│ ├─ Push: Server-initiated, efficient, complex                     │
│ ├─ Pull: Client-initiated, simple, wasteful                       │
│ └─ Decision: Push for real-time, pull for rare                    │
│                                                                    │
│ Trade-off 3: CONSISTENCY vs AVAILABILITY                          │
│ ├─ Strong consistency = Higher latency                            │
│ ├─ Eventual consistency = Lower latency, may show stale data      │
│ └─ Decision: Real-time systems prefer availability                │
│    • Trading: Eventual OK (100ms stale acceptable)                │
│    • Banking: Strong needed (must be accurate)                    │
│                                                                    │
│ Trade-off 4: SIMPLICITY vs PERFORMANCE                            │
│ ├─ Simple polling = Easy to build, poor performance               │
│ ├─ WebSocket = Complex, excellent performance                     │
│ └─ Decision: Match complexity to requirements                     │
│    • Start simple, optimize later                                 │
│    • Don't over-engineer for small scale                          │
│                                                                    │
│ Trade-off 5: BROADCAST vs SELECTIVE                               │
│ ├─ Broadcast: Simple, wasteful                                    │
│ ├─ Selective: Efficient, requires subscription management         │
│ └─ Decision: Selective at scale                                   │
│    • <1K users: Broadcast OK                                      │
│    • >10K users: Must be selective                                │
└──────────────────────────────────────────────────────────────────┘
```

---

## Interview Guide

### Common Interview Questions & Frameworks

#### Q1: "Design a real-time chat system for 1M users"

**Framework to Answer:**

```
1. CLARIFY REQUIREMENTS
   • 1:1 chat or group chat?
   • File sharing needed?
   • Message history for how long?
   • Read receipts, typing indicators?
   • Expected latency?

2. CAPACITY ESTIMATION
   • 1M DAU (Daily Active Users)
   • 20% concurrent: 200K connections
   • 50 messages/user/day = 50M messages/day
   • 50M / 86400 sec ≈ 580 messages/sec average
   • Peak (10x): 5,800 messages/sec

3. HIGH-LEVEL DESIGN
   Client ←WebSocket→ Chat Server ←Kafka→ Database
   
4. DEEP DIVES
   • WebSocket for bidirectional
   • Kafka for message distribution across servers
   • Redis for online presence tracking
   • PostgreSQL for message history
   • Push notifications for offline users

5. SCALING
   • 200K connections = 20 servers × 10K each
   • Sticky sessions for load balancing
   • Multi-region deployment
   • Redis Pub/Sub for server coordination
```

---

#### Q2: "How do you handle a celebrity user with 10M followers?"

**Framework to Answer:**

```
PROBLEM STATEMENT:
Celebrity with 10M followers posts update
→ Need to notify all followers in real-time
→ Can't write to 10M feeds (takes minutes)

SOLUTION OPTIONS:

Option 1: Fan-out on Write (❌ Doesn't Scale)
┌──────────┐
│Celebrity │ Posts update
│  Posts   │
└────┬─────┘
     │
     ▼
Write to ALL 10M follower feeds
├─> Takes: 10 minutes to complete
├─> Write load: Massive
└─> Read: Fast

Option 2: Fan-out on Read (❌ Slow Reads)
┌──────────┐
│Celebrity │ Posts update
│  Posts   │
└────┬─────┘
     │
     ▼
Write to celebrity timeline only
When follower loads feed:
├─> Fetch from celebrity timeline
├─> Merge with regular feed
├─> Takes: 500ms per request
└─> Write: Fast, Read: Slow

Option 3: Hybrid Approach (✅ BEST)
┌──────────┐
│Celebrity │ Posts update
│  Posts   │
└────┬─────┘
     │
     ▼
Check follower count
     │
     ├─> <1K: Fan-out on write
     │   └─> Push to all immediately
     │
     ├─> 1K-1M: Push to active users only
     │   ├─> Track who's online (Redis)
     │   ├─> Push to online users (~10K)
     │   └─> Others fetch on next open
     │
     └─> >1M: Fan-out on read + cache
         ├─> Don't push to anyone
         ├─> Cache post in CDN
         └─> Fetch on demand (fast from cache)

Implementation:
Use Bloom Filter to track active users
• 10M users → 12MB Bloom filter
• False positive rate: 1%
• Query time: O(1), microseconds

Result:
• Write load: 99.5% reduction
• Read latency: +50ms (acceptable)
• Push to 100K active instead of 10M total
```

---

#### Q3: "How do you maintain message ordering across distributed servers?"

**Framework to Answer:**

```
PROBLEM:
User A on Server 1, User B on Server 2
Messages can arrive out of order

SOLUTION APPROACHES:

Approach 1: SEQUENCE NUMBERS
Every message gets global sequence number
Client tracks last received sequence
If gap detected, request missing messages

Message Format:
{
  id: "msg_12345",
  sequence: 98765,
  content: "Hello",
  timestamp: 1704723845000
}

Client Logic:
   Receive message with sequence 100
   Expected sequence: 99
   Gap detected (missing 99)
   ↓
   Request: GET /messages?from=99&to=99
   ↓
   Receive missing message
   ↓
   Apply in order: 99, then 100

Approach 2: TIMESTAMP-BASED
Use timestamps with vector clocks
Account for clock skew
Buffer messages briefly
Deliver in timestamp order

Approach 3: PARTITION-BASED
Route messages by partition key
Same key always goes to same partition
Order guaranteed within partition

Example:
   chat_room_id as partition key
   All messages for room_1 → Partition 3
   Kafka guarantees order within partition

RECOMMENDATION:
Combination approach:
• Sequence numbers for detection
• Timestamps for ordering
• Partition keys for guarantee
```

---

#### Q4: "How would you design real-time stock price updates?"

**Framework to Answer:**

```
REQUIREMENTS:
• 8,000 stocks tracked
• 1M concurrent users
• <100ms latency end-to-end
• 1M price updates/sec from exchanges

ARCHITECTURE:

1. DATA INGESTION
   Exchanges (NYSE, NASDAQ)
      ↓
   Price Aggregator (normalize, deduplicate)
      ↓
   Kafka (topic: stock-prices, 100 partitions)

2. STORAGE LAYER
   ├─> Redis (current prices, 5 sec TTL)
   └─> TimescaleDB (historical, 10 years)

3. DISTRIBUTION
   Kafka Consumer Groups
      ↓
   20 WebSocket servers (5K connections each)
      ↓
   100K clients

4. KEY OPTIMIZATIONS
   
   a) Selective Subscription
      • Users subscribe to watchlist (10-50 stocks)
      • Don't send all 8K stocks to everyone
      • Result: 99% bandwidth reduction
   
   b) Throttling
      • Max 1 update per second per stock
      • Aggregate high-frequency updates
      • Result: 99% message reduction
   
   c) Batching
      • Bundle multiple stocks in one message
      • Send every 100ms with accumulated updates
      • Result: 10x fewer messages
   
   d) Binary Protocol
      • Use Protocol Buffers instead of JSON
      • Result: 75% size reduction

5. CAPACITY CALCULATION
   
   Bandwidth per user:
   • 50 stocks in watchlist
   • 1 update/sec per stock
   • 50 bytes per update (binary)
   • 50 × 50 bytes = 2.5 KB/sec per user
   
   Total:
   • 100K users × 2.5 KB/sec = 250 MB/sec
   • Manageable with proper infrastructure

6. LATENCY BREAKDOWN
   Exchange → Aggregator: 20ms
   Aggregator → Kafka: 10ms
   Kafka → WebSocket Server: 10ms
   WebSocket → Client: 25ms
   ────────────────────────
   Total: 65ms ✓ Under 100ms target
```

---

#### Q5: "How do you handle connection failures and reconnection?"

**Framework to Answer:**

```
FAILURE SCENARIOS:

1. Client Loses Network
2. Server Crashes
3. Load Balancer Fails
4. Message Broker Down

SOLUTION STRATEGY:

CLIENT-SIDE:

1. Detection
   • Missed heartbeat (30 sec timeout)
   • WebSocket onclose event
   • Network state change

2. Reconnection
   Exponential Backoff:
   Attempt 1: 1 second
   Attempt 2: 2 seconds
   Attempt 3: 4 seconds
   Attempt 4: 8 seconds
   Max: 30 seconds
   
   With Jitter:
   • Add random 0-1000ms
   • Prevents thundering herd

3. State Recovery
   • Store last message ID
   • On reconnect: GET /messages?since=<last_id>
   • Catch up on missed messages
   • Resume normal operation

SERVER-SIDE:

1. Dead Connection Detection
   • No heartbeat for 60 seconds
   • Clean up resources
   • Remove from subscription registry

2. Graceful Shutdown
   • Send "reconnect" message to clients
   • Wait for clients to disconnect (30 sec)
   • Close remaining connections
   • Shutdown

3. Failover
   If primary region fails:
   • DNS switches to secondary region
   • Clients reconnect automatically
   • State synced via database
   • Downtime: <1 minute

DELIVERY GUARANTEES:

At-Most-Once:
• Send and forget
• Fast but may lose messages
• Use: Non-critical updates

At-Least-Once:
• Acknowledge receipt
• Retry if no ACK
• May receive duplicates
• Use: Most real-time systems

Exactly-Once:
• Deduplication required
• Complex to implement
• Slower but accurate
• Use: Financial transactions
```

---

## Complete Decision Framework

```
┌──────────────────────────────────────────────────────────────────┐
│            COMPREHENSIVE DECISION MATRIX                          │
└──────────────────────────────────────────────────────────────────┘

STEP 1: Determine Latency Requirement
├─ <50ms: WebRTC or optimized WebSocket
├─ <500ms: WebSocket or SSE
├─ <5s: Long Polling
└─> >5s: Simple Polling

STEP 2: Check Bidirectional Need
├─ YES: WebSocket or WebRTC
└─ NO: SSE or Long Polling

STEP 3: Estimate Scale
├─ <1K users: Single server, any protocol
├─ 1K-100K: Multi-server with Redis Pub/Sub
├─ 100K-1M: WebSocket farm with Kafka
└─ >1M: Multi-region, Kafka, CDN

STEP 4: Consider Infrastructure
├─ Have WebSocket support? → Use WebSocket
├─ Firewall restrictions? → Use SSE or Long Polling
├─ Mobile-first? → Consider battery (throttle updates)
└─ Legacy browser support? → Provide fallbacks

STEP 5: Plan for Failures
├─ Implement reconnection logic
├─ Store offline messages
├─ Use circuit breakers
└─ Monitor connection health

═══════════════════════════════════════════════════════════════

QUICK REFERENCE BY USE CASE:

Chat Application:
Protocol: WebSocket
Distribution: Kafka
Scale: 20 servers × 10K = 200K users
Latency: <500ms

Stock Trading:
Protocol: WebSocket
Distribution: Kafka + Redis
Scale: 100 servers × 5K = 500K users
Latency: <100ms

Collaborative Editing:
Protocol: WebSocket
Distribution: Direct or Redis
Scale: 10 servers × 1K = 10K concurrent editors
Latency: <200ms

Multiplayer Gaming:
Protocol: UDP + WebSocket or WebRTC
Distribution: Per-game server
Scale: Millions of matches
Latency: <50ms

Live Dashboard:
Protocol: SSE or WebSocket
Distribution: Redis Pub/Sub
Scale: 50 servers × 500 = 25K viewers
Latency: <1 second

IoT Monitoring:
Protocol: MQTT → WebSocket
Distribution: Kafka
Scale: 100K devices → 1K dashboards
Latency: <1 second
```

---

## Summary: Real-time Updates Patterns

```
┌──────────────────────────────────────────────────────────────────┐
│                    PATTERN SUMMARY                                │
└──────────────────────────────────────────────────────────────────┘

CLIENT-SERVER PROTOCOLS (Choose One):
├─ Simple Polling:    Updates > 30s, simplicity critical
├─ Long Polling:      Near real-time, can't use WebSocket
├─ SSE:               One-way updates, news feeds
├─ WebSocket:         Bidirectional, most common
└─ WebRTC:            P2P, ultra-low latency

SERVER DISTRIBUTION (Choose Based on Scale):
├─ Direct:           <1K users, single server
├─ Redis Pub/Sub:    1K-100K users, multiple servers
└─ Kafka:            >100K users, high throughput

OPTIMIZATION PATTERNS (Apply as Needed):
├─ Subscription Management: Track who wants what
├─ Throttling: Limit update frequency
├─ Batching: Combine multiple updates
├─ Compression: Reduce message size
├─ Delta Updates: Send only changes
├─ Priority Queuing: Critical messages first
└─ Selective Broadcasting: Send to interested only

RELIABILITY PATTERNS (Always Apply):
├─ Reconnection with backoff
├─ Message deduplication
├─ Sequence tracking
├─ Offline message storage
└─ Graceful degradation

═══════════════════════════════════════════════════════════════

INTERVIEW TIPS:

1. Always start with requirements clarification
2. Draw the two-hop architecture
3. Choose protocol based on latency/scale
4. Discuss trade-offs explicitly
5. Mention optimization techniques
6. Address failure scenarios
7. Provide capacity estimations
8. Reference real-world examples
```

---

## Final Recommendations

```
┌──────────────────────────────────────────────────────────────────┐
│                    BEST PRACTICES                                 │
└──────────────────────────────────────────────────────────────────┘

ARCHITECTURE:
✓ Start simple, scale incrementally
✓ Use WebSocket for most real-time needs
✓ Add Kafka only when Redis insufficient
✓ Deploy in multiple regions for global users
✓ Implement health checks and monitoring

PERFORMANCE:
✓ Measure latency early and often
✓ Optimize only after measurement
✓ Use selective broadcasting at scale
✓ Batch messages when latency allows
✓ Compress for bandwidth-sensitive scenarios

RELIABILITY:
✓ Implement reconnection with backoff
✓ Store messages for offline users
✓ Use sequence numbers to detect gaps
✓ Plan for graceful degradation
✓ Test failure scenarios

OPERATIONS:
✓ Monitor connection counts per server
✓ Track message delivery latency
✓ Alert on connection storms
✓ Log all failures for debugging
✓ Capacity plan for 3x peak load

COST OPTIMIZATION:
✓ Use compression to reduce bandwidth costs
✓ Throttle unnecessary updates
✓ Implement tiered service levels
✓ Cache aggressively
✓ Right-size server instances
```

---

**Document Version**: 1.0  
**Created**: January 2025  
**Type**: High-Level Design Guide  
**Focus**: Architecture patterns without implementation details  
**Coverage**: 10 real-world examples, decision frameworks, interview prep
