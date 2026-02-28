import os

path = "/Users/aerloki/Desktop/SYSTEM DESIGN/HLD-Hello-Interview-Framework/distributed-ephemeral-presence-service-hello-interview.md"

sections = []

sections.append("""# Design a Distributed "Ephemeral" Presence Service (Google Workspace "Who is Online?") — Hello Interview Framework

> **Question**: Design the "Who is online?" system for a collaborative suite like Google Workspace. When a user opens a document, everyone else currently viewing it can see who is online in real-time. The system must handle millions of concurrent users across millions of documents, with presence state that automatically expires when users disconnect.
>
> **Asked at**: Google, Meta, Slack, Atlassian, Notion
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## Table of Contents
- [1. Requirements](#1-requirements)
- [2. Core Entities](#2-core-entities)
- [3. API Design](#3-api-design)
- [4. Data Flow](#4-data-flow)
- [5. High-Level Design](#5-high-level-design)
- [6. Deep Dives](#6-deep-dives)

---

## 1. Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Join Presence**: When a user opens a document, all current viewers are notified (new user avatar/name appears).
2. **Leave Presence**: When a user closes a document or disconnects, all remaining viewers are notified.
3. **View Viewer List**: A user opening a document can see who is currently viewing it in real-time.
4. **Auto-Expiry**: If a user disconnects abruptly (crash, network loss), their presence automatically expires within 30 seconds.
5. **Idle Detection**: Distinguish between "active" (typing/clicking) and "idle" (no interaction for 5 minutes).

#### Nice to Have (P1)
- Live cursor position broadcast (which line a collaborator is editing).
- User status aggregation across the entire workspace (global "who is online?" sidebar).
- Presence for other resource types (spreadsheets, slides, chats).
- "Last seen" timestamp for users who recently left.
- Do Not Disturb / invisible mode.
- Cross-device presence (same doc on phone + laptop shown once).

#### Below the Line (Out of Scope)
- Full collaborative editing (OT/CRDT for conflict resolution).
- Document content storage, authentication internals, mobile push notifications.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Presence Propagation Latency** | < 1 second P99 | UX: avatars should appear/disappear promptly |
| **Heartbeat Expiry** | Max 30 seconds after disconnect | Dead presence is misleading; longer = stale data |
| **Availability** | 99.99% for read path | Presence is a UX feature; brief write outage tolerable |
| **Consistency** | Eventually consistent (seconds) | Seeing slightly stale viewer list is acceptable |
| **Scale** | 100M DAU, 10M concurrent users, 5M active documents | Google Workspace-scale |
| **Fan-out per document** | Support 1,000 concurrent viewers | Popular shared docs (all-hands agenda, company wiki) |
| **Memory efficiency** | Ephemeral data only; auto-expire | No cold-storage accumulation |
| **Reconnection** | Presence restored within 5 seconds | Tab refresh should re-join seamlessly |

### Capacity Estimation

```
Users & Sessions:
  Daily active users: 100M
  Peak concurrent users: 10M (10% of DAU)
  Average documents open per user: 2
  Concurrent "presence sessions": 20M

Documents with active viewers:
  Active documents: ~5M
  Average viewers per active doc: 4
  Peak viewers per popular doc: 1,000

Presence Events:
  Heartbeat frequency: every 15 seconds per session
  Heartbeats/sec: 20M / 15 = ~1.3M heartbeats/sec
  Join/leave events/sec: ~1,400/sec (5M events/hour)

Storage (Redis - ephemeral only):
  20M sessions x 80 bytes = ~1.6 GB
  5M docs x 4 users x 20 bytes = ~400 MB
  Total Redis: ~2 GB

Fan-out:
  1,400 events/sec x avg 4 viewers = ~5,600 WebSocket pushes/sec
  Peak popular doc: 1 event x 1,000 viewers = 1,000 simultaneous pushes
```

---

## 2. Core Entities

### Entity 1: PresenceSession
```java
public class PresenceSession {
    private final String sessionId;          // UUID per browser tab
    private final String userId;             // Authenticated user
    private final String resourceId;         // Document being viewed
    private final ResourceType resourceType; // DOCUMENT, SPREADSHEET, SLIDE
    private final PresenceStatus status;     // ACTIVE or IDLE
    private final Instant lastHeartbeatAt;
    private final Instant joinedAt;
    private final String serverNodeId;       // Which Presence Server owns this WS
    private final int expiryTtlSeconds;      // 30 seconds
}

public enum PresenceStatus { ACTIVE, IDLE }
```

### Entity 2: PresenceView
```java
public class PresenceView {
    private final String resourceId;
    private final List<ViewerInfo> viewers;
    private final int viewerCount;
    private final Instant fetchedAt;
}

public class ViewerInfo {
    private final String userId;
    private final String displayName;
    private final String avatarUrl;
    private final PresenceStatus status;
    private final Instant joinedAt;
    private final String sessionId; // For multi-device deduplication
}
```

### Entity 3: PresenceEvent
```java
public class PresenceEvent {
    private final String eventId;
    private final PresenceEventType type;   // JOIN, LEAVE, STATUS_CHANGE
    private final String resourceId;
    private final String userId;
    private final String sessionId;
    private final ViewerInfo viewer;        // For JOIN events
    private final PresenceStatus newStatus; // For STATUS_CHANGE events
    private final Instant timestamp;
}
```

### Entity 4: UserPresenceAggregate
```java
public class UserPresenceAggregate {
    private final String userId;
    private final OverallStatus overallStatus; // ONLINE, IDLE, OFFLINE
    private final List<ResourcePresence> activeIn;
    private final Instant lastSeenAt;
}
```

---

## 3. API Design

### 1. Join Presence (WebSocket)
```
WS Connect: wss://presence.workspace.example.com/ws?resource_id=doc_abc123

Client -> Server:
{ "type": "JOIN", "resource_id": "doc_abc123", "resource_type": "DOCUMENT" }

Server -> Client (current viewers snapshot):
{
  "type": "PRESENCE_SNAPSHOT",
  "resource_id": "doc_abc123",
  "viewers": [
    { "user_id": "user_456", "display_name": "Alice", "status": "ACTIVE", "joined_at": "2026-02-28T14:00:00Z" },
    { "user_id": "user_789", "display_name": "Bob",   "status": "IDLE",   "joined_at": "2026-02-28T13:55:00Z" }
  ],
  "viewer_count": 2
}

Server -> ALL OTHER VIEWERS (push):
{
  "type": "USER_JOINED",
  "resource_id": "doc_abc123",
  "viewer": { "user_id": "user_123", "display_name": "Carol", "status": "ACTIVE", "joined_at": "2026-02-28T14:05:00Z" }
}
```

### 2. Heartbeat (every 15 seconds)
```
Client -> Server:
{ "type": "HEARTBEAT", "resource_id": "doc_abc123", "status": "ACTIVE" }

Server -> Client:
{ "type": "HEARTBEAT_ACK", "next_heartbeat_ms": 15000 }

If heartbeat stops: TTL expires after 30s -> auto-generated LEAVE event
```

### 3. Leave Presence
```
Client -> Server (explicit leave):
{ "type": "LEAVE", "resource_id": "doc_abc123" }

Server -> ALL REMAINING VIEWERS:
{ "type": "USER_LEFT", "resource_id": "doc_abc123", "user_id": "user_123", "session_id": "sess_xyz" }

If browser crashes (no explicit LEAVE): TTL auto-expiry handles it within 30s
```

### 4. Get Viewer List (REST - initial page load)
```
GET /api/v1/presence/resources/{resource_id}/viewers

Response 200:
{
  "resource_id": "doc_abc123",
  "viewer_count": 3,
  "viewers": [{ "user_id": "user_456", "display_name": "Alice", "status": "ACTIVE", "joined_at": "..." }],
  "fetched_at": "2026-02-28T14:05:30Z"
}
```

### 5. Get User Overall Status
```
GET /api/v1/presence/users/{user_id}

Response 200:
{
  "user_id": "user_456",
  "overall_status": "ONLINE",
  "active_resources": [{ "resource_id": "doc_abc123", "status": "ACTIVE" }],
  "last_seen_at": "2026-02-28T14:05:30Z"
}
```

### 6. Status Change (Idle Detection)
```
Client -> Server:
{ "type": "STATUS_CHANGE", "resource_id": "doc_abc123", "new_status": "IDLE" }

Server -> ALL VIEWERS:
{ "type": "USER_STATUS_CHANGED", "resource_id": "doc_abc123", "user_id": "user_123", "new_status": "IDLE" }
```

---

## 4. Data Flow

### Flow 1: User Opens a Document (Join)
```
1. User opens doc_abc123
   |
2. REST: GET /presence/resources/doc_abc123/viewers
   -> Viewer list from Redis (< 5ms) -> UI renders immediately
   |
3. WebSocket connect (consistent hash by resource_id -> Presence Server S)
   -> JWT auth
   |
4. Presence Server processes JOIN:
   HSET session:{sessionId} userId X resourceId Y status ACTIVE joinedAt Z
   EXPIRE session:{sessionId} 30                    <- 30s TTL
   SADD resource_viewers:{resourceId} {sessionId}
   SADD user_sessions:{userId}:{resourceId} {sessionId}
   SPUBLISH "presence:{resourceId}" JOIN_EVENT      <- Redis 7+ sharded pub/sub
   |
5. All Presence Servers subscribed to "presence:{resourceId}" receive JOIN_EVENT
   -> Each pushes USER_JOINED to their local viewers of doc_abc123
   |
6. New client receives PRESENCE_SNAPSHOT (full current viewer list from Redis)
```

### Flow 2: Heartbeat (Keeps Presence Alive)
```
Every 15 seconds:
   Client -> Server: { type: "HEARTBEAT", status: "ACTIVE" }
   Server:
     EXPIRE session:{sessionId} 30     <- refresh TTL
     Update lastHeartbeatAt
     If status changed: SPUBLISH STATUS_CHANGE
   Server -> Client: HEARTBEAT_ACK

If heartbeat STOPS (crash/network drop):
   -> TTL expires after 30s
   -> Proactive scanner generates LEAVE event within 10s of TTL expiry
   -> USER_LEFT broadcast to all viewers
```

### Flow 3: Explicit Leave
```
1. User closes tab -> beforeunload fires -> Client sends LEAVE (best-effort)
2. Presence Server:
   SREM resource_viewers:{resourceId} {sessionId}
   SREM user_sessions:{userId}:{resourceId} {sessionId}
   DEL session:{sessionId}
   SPUBLISH "presence:{resourceId}" LEAVE_EVENT
3. All subscribers push USER_LEFT to local viewers
```

### Flow 4: TTL Expiry -> Auto-Generated LEAVE
```
Option A -- Redis Keyspace Notifications:
  Redis publishes "__keyevent@0__:expired" on TTL expiry
  PRO: automatic
  CON: 20M sessions / 30s = 667K expirations/min; single consumer overwhelmed
  NOT recommended at scale

Option B -- Proactive Polling per Server (CHOSEN):
  Each Presence Server tracks its own sessions in local memory
  Background thread every 10s: lastHeartbeat > 30s ago? -> generate LEAVE
  PRO: distributed (500 servers x 40K sessions each)
  PRO: no Redis keyspace feature dependency
  Max latency: 30s (TTL) + 10s (scan interval) = 40s worst case

Option C -- Lazy Expiry on Read:
  When reading resource_viewers:{resourceId}, filter sessions where
  EXISTS session:{sessionId} returns false
  CON: other viewers don't see user disappear proactively
  Use only for approximate viewer counts
```

### Flow 5: Cross-Server Fan-out
```
Problem: User A on Server 1, User B on Server 2, both viewing doc_abc123.
         A joins -> how does Server 2 notify B?

Solution: Redis Sharded Pub/Sub (Redis 7+)

  1. A joins on Server 1
  2. Server 1: SPUBLISH "presence:doc_abc123" JOIN_EVENT
     (SPUBLISH routes to the cluster node owning this channel's hash slot)
  3. ALL servers with SSUBSCRIBE on "presence:doc_abc123" receive it
  4. Server 2 receives -> finds local WebSocket connections for doc_abc123 -> pushes to B

Critical memory optimization:
  - Server SSUBSCRIBES to "presence:{resourceId}" only when its FIRST viewer joins that doc
  - Server SUNSUBSCRIBES when its LAST viewer leaves
  - 500 servers x avg 4 active doc subs = ~2,000 total subscriptions
  - vs subscribing to all 5M channels = would exhaust memory
```

---

## 5. High-Level Design

### Architecture Diagram

```
+------------------------------------------------------------------------------+
|                            CLIENTS (Browser)                                  |
|  REST: GET /presence/resources/{id}/viewers  (initial viewer list)           |
|  WebSocket: send HEARTBEAT/JOIN/LEAVE, receive USER_JOINED/USER_LEFT events  |
+------------------------------------------------------------------------------+
                   |  REST            |  WebSocket
                   v                  v
+------------------------------------------------------------------------------+
|                   API GATEWAY / LOAD BALANCER                                 |
|  JWT Auth | REST routing | WebSocket consistent-hash routing by resource_id  |
+------------------------------------------------------------------------------+
          |  REST                    |  WebSocket (sticky by resource_id)
          v                          v
+-----------------------+  +------------------------------------------------+
|  PRESENCE REST API     |  |   PRESENCE SERVER CLUSTER (500 servers)         |
|  (Stateless, 10-20     |  |   (Stateful -- holds WebSocket connections)      |
|   pods)                |  |                                                  |
|  - GET viewer list     |  |  Server 1      Server 2   ...    Server 500     |
|  - GET user status     |  |  ~20K WS       ~20K WS           ~20K WS       |
|  - Reads Redis only    |  |  sessions      sessions           sessions      |
|  - No writes           |  |                                                  |
+-----------------------+  |  Each server:                                    |
                            |  - Manages local WS connections                 |
                            |  - Runs proactive expiry scanner                |
                            |  - SSUBSCRIBES to channels for active docs      |
                            +------------------+-----------------------------+
                                               |
             +-------------------+-------------+------------------+
             |                   |                                |
             v                   v                                v
+---------------------+  +-------------------+  +---------------------+
|  REDIS CLUSTER       |  |  REDIS SHARDED    |  |  KAFKA              |
|  (Presence State)    |  |  PUB/SUB          |  |  (Audit/Analytics)  |
|                      |  |  (Cross-server    |  |                     |
|  HASH session:{id}   |  |   Fan-out)        |  |  Topic:             |
|  TTL: 30s            |  |                   |  |  "presence-events"  |
|                      |  |  Redis 7+:        |  |                     |
|  SET resource_       |  |  SPUBLISH /       |  |  Consumers:         |
|  viewers:{resId}     |  |  SSUBSCRIBE       |  |  - Analytics        |
|  TTL: 3600s          |  |                   |  |  - Audit log        |
|                      |  |  Channel:         |  |  - Notifications    |
|  SET user_sessions:  |  |  presence:{resId} |  |                     |
|  {userId}:{resId}    |  |                   |  |  At-least-once      |
|  TTL: 3600s          |  |  ~5ms latency     |  |  ~50ms latency      |
|                      |  |  At-most-once     |  |  (async only)       |
|  ~3 GB total         |  |  (fire-forget)    |  |                     |
+---------------------+  +-------------------+  +---------------------+
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Presence REST API** | Initial viewer list, user status | Node.js/Go stateless | 10-20 pods |
| **Presence Server** | WebSocket mgmt, heartbeats, fan-out | Go stateful | 500 x 20K connections |
| **Redis Cluster (State)** | Ephemeral sessions, TTL auto-expiry | Redis 7+ | ~3 GB, sharded |
| **Redis Sharded Pub/Sub** | Cross-server real-time fan-out | SSUBSCRIBE/SPUBLISH | ~5M channels |
| **Kafka** | Durable event log for audit/analytics | Apache Kafka | Async consumers only |

---

## 6. Deep Dives

### Deep Dive 1: TTL-Based Ephemeral Presence

**Core Challenge**: Presence is inherently ephemeral. Browsers crash without sending LEAVE. The system must automatically forget stale users.

**Redis Data Model**:
```
Key: session:{sessionId}              Type: HASH    TTL: 30s
  Fields: userId, resourceId, status, lastHeartbeatAt, serverNodeId, joinedAt

Key: resource_viewers:{resourceId}    Type: SET     TTL: 3600s
  Members: Set of sessionIds currently viewing this document
  Note: May contain stale entries (expired sessions) -- always filter on read

Key: user_sessions:{userId}:{resId}   Type: SET     TTL: 3600s
  Members: Set of sessionIds for this user on this document
  Purpose: Multi-device deduplication (user appears once per doc, not once per tab)
```

```java
public class HeartbeatManager {

    // Called every 15 seconds from the client
    public HeartbeatResult processHeartbeat(String sessionId, PresenceStatus newStatus) {
        String key = "session:" + sessionId;

        // Refresh TTL. Returns false if key doesn't exist (already expired)
        Boolean existed = redis.expire(key, Duration.ofSeconds(30));
        if (Boolean.FALSE.equals(existed)) {
            return HeartbeatResult.SESSION_EXPIRED; // Client must send fresh JOIN
        }

        // Detect and publish status transitions (ACTIVE <-> IDLE)
        String current = (String) redis.opsForHash().get(key, "status");
        if (!newStatus.name().equals(current)) {
            redis.opsForHash().put(key, "status", newStatus.name());
            String resourceId = (String) redis.opsForHash().get(key, "resourceId");
            String userId = (String) redis.opsForHash().get(key, "userId");
            eventPublisher.publish(resourceId,
                PresenceEvent.statusChange(resourceId, userId, sessionId, newStatus));
        }
        redis.opsForHash().put(key, "lastHeartbeatAt", Instant.now().toString());
        return HeartbeatResult.OK;
    }

    // Proactive expiry scanner -- runs every 10s per Presence Server
    // Each server scans ONLY the sessions it owns (local registry ~40K entries)
    @Scheduled(fixedRate = 10000)
    public void scanExpiredSessions() {
        Instant threshold = Instant.now().minusSeconds(30);
        for (String sessionId : localSessionRegistry.getAll()) {
            String lhb = (String) redis.opsForHash()
                .get("session:" + sessionId, "lastHeartbeatAt");
            if (lhb == null || Instant.parse(lhb).isBefore(threshold)) {
                String resourceId = (String) redis.opsForHash()
                    .get("session:" + sessionId, "resourceId");
                String userId = (String) redis.opsForHash()
                    .get("session:" + sessionId, "userId");
                // Clean up all Redis keys
                redis.opsForSet().remove("resource_viewers:" + resourceId, sessionId);
                redis.opsForSet().remove("user_sessions:" + userId + ":" + resourceId, sessionId);
                redis.delete("session:" + sessionId);
                localSessionRegistry.remove(sessionId);
                // Broadcast LEAVE to all viewers of this document
                eventPublisher.publish(resourceId,
                    PresenceEvent.leave(resourceId, userId, sessionId));
            }
        }
    }

    // Read viewer list with lazy cleanup of stale SET entries
    public List<ViewerInfo> getViewers(String resourceId) {
        List<ViewerInfo> result = new ArrayList<>();
        for (String sid : redis.opsForSet().members("resource_viewers:" + resourceId)) {
            Map<Object, Object> data = redis.opsForHash().entries("session:" + sid);
            if (data.isEmpty()) {
                // Session TTL expired -- lazy remove from SET
                redis.opsForSet().remove("resource_viewers:" + resourceId, sid);
            } else {
                result.add(ViewerInfo.from(sid, data));
            }
        }
        return result;
    }
}
```

**TTL Tuning Trade-offs**:

| TTL / Heartbeat | Max Staleness | Heartbeats/sec (20M sessions) |
|-----------------|---------------|-------------------------------|
| 10s / 5s | 10s stale | 4M/sec (high infrastructure cost) |
| **30s / 15s** | **30s stale** | **1.3M/sec (chosen)** |
| 60s / 30s | 60s stale | 667K/sec (users see ghost avatars) |

---

### Deep Dive 2: Cross-Server Fan-out with Redis Sharded Pub/Sub

**The Problem**: 500 Presence Servers, each with 20K WebSocket connections. When User A joins a doc on Server 1, User B on Server 2 watching the same doc must be notified within ~1 second.

```
Fan-out Options:

A) Full Mesh (direct server-to-server)
   PRO: No intermediary, lowest latency
   CON: O(N^2) connections = 500^2 = 250,000 TCP connections
        Complex service discovery, operationally unmaintainable

B) Redis Pub/Sub (CHOSEN)
   Server 1 SPUBLISH "presence:doc_abc123" EVENT
   All servers with SSUBSCRIBE on that channel receive it
   PRO: O(1) publish regardless of subscriber count, ~1-5ms
   CON: At-most-once (fire-and-forget, no replay)
   OK because: missed presence update self-corrects on next heartbeat

C) Kafka
   PRO: Durable, at-least-once delivery
   CON: ~50-100ms latency, too slow for real-time avatar updates
   USE: For durable audit log (run in parallel, async)
```

```java
public class PresenceEventPublisher implements MessageListener {

    // Tracks how many of THIS server's clients are viewing each resource
    private final ConcurrentHashMap<String, AtomicInteger> localViewerCounts
        = new ConcurrentHashMap<>();

    // SUBSCRIBE when first local viewer joins a resource on this server
    public void onLocalViewerJoined(String resourceId) {
        int count = localViewerCounts
            .computeIfAbsent(resourceId, k -> new AtomicInteger(0)).incrementAndGet();
        if (count == 1) {
            // First local viewer -> subscribe to this resource's channel
            // SSUBSCRIBE routes to the correct Redis cluster node for this channel
            shardedRedis.ssubscribe(this, "presence:" + resourceId);
        }
    }

    // UNSUBSCRIBE when last local viewer leaves (memory optimization)
    public void onLocalViewerLeft(String resourceId) {
        AtomicInteger c = localViewerCounts.get(resourceId);
        if (c != null && c.decrementAndGet() == 0) {
            shardedRedis.sunsubscribe("presence:" + resourceId);
            localViewerCounts.remove(resourceId);
        }
    }

    // Publish join/leave/status events
    public void publish(String resourceId, PresenceEvent event) {
        // Real-time delivery (~5ms)
        shardedRedis.spublish("presence:" + resourceId, toJson(event));
        // Durable log for audit/analytics (async, ~50ms, non-blocking)
        kafka.send("presence-events", resourceId, event);
    }

    // Called by Redis when a message arrives on a subscribed channel
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());  // "presence:doc_abc123"
        String resourceId = channel.substring("presence:".length());
        PresenceEvent event = fromJson(message.getBody());

        // Push to all local WebSocket connections watching this resource
        for (WebSocketSession ws : localViewerRegistry.forResource(resourceId)) {
            if (ws.isOpen()) {
                ws.sendMessage(new TextMessage(buildClientMessage(event)));
            }
        }
    }
}
```

**Why Redis 7+ Sharded Pub/Sub matters**:

| Feature | Classic Redis Pub/Sub | Redis 7+ Sharded Pub/Sub |
|---------|----------------------|--------------------------|
| Cluster support | PUBLISH goes to ONE node; others miss it | SPUBLISH hashes channel to a slot; cluster cooperates |
| Memory | All subscribers on same node | Subscribers on any node, routed correctly |
| Scale | Single-node bottleneck for pub/sub | Horizontally scalable across cluster |

---

### Deep Dive 3: Handling the Popular Document Problem

**Problem**: A company-wide all-hands doc can have 1,000+ simultaneous viewers. Every join/leave event triggers 999 WebSocket pushes.

```
Regular doc (4 viewers):     1 event -> 3 pushes (trivial)
Popular doc (1,000 viewers): 1 event -> 999 pushes

Load distribution analysis:
  1,000 viewers spread across ~5-10 servers (consistent hash)
  Each server: ~100-200 local WS connections for this doc
  On join: each server pushes to its 100-200 connections
  Total: 999 pushes distributed across 5-10 servers = manageable

Real concern: Burst joins (200 people join an all-hands in 5 seconds)
  200 events x 999 pushes = 199,800 pushes in 5 seconds
  Across 10 servers = 20K pushes/sec per server
  Go/Node.js handles 100K+ WS messages/sec -> still manageable
```

```java
public class PopularDocumentHandler {

    private static final int BATCHING_THRESHOLD  = 50;   // viewers -> start batching
    private static final int AGGREGATE_THRESHOLD = 200;  // viewers -> show count only

    public void handlePresenceEvent(PresenceEvent event, String resourceId) {
        int viewerCount = redis.opsForSet().size("resource_viewers:" + resourceId).intValue();

        if (viewerCount > AGGREGATE_THRESHOLD) {
            // Show "200+ people viewing this document" instead of individual avatars
            // Only send viewer count deltas (+1/-1), not full user info
            broadcastViewerCountDelta(resourceId, event.getType());

        } else if (viewerCount > BATCHING_THRESHOLD) {
            // Buffer 100ms of events, then send one merged "BATCH_UPDATE" message
            // "3 joined, 1 left" instead of 4 separate WebSocket messages
            eventBatcher.enqueue(resourceId, event);

        } else {
            // Normal: immediate individual avatar update
            broadcastImmediately(resourceId, event);
        }
    }

    @Scheduled(fixedRate = 100)
    public void flushBatched() {
        for (var entry : eventBatcher.drain()) {
            String resourceId = entry.getKey();
            List<PresenceEvent> batch = entry.getValue();
            List<ViewerInfo> joined = batch.stream()
                .filter(e -> e.getType() == JOIN).map(PresenceEvent::getViewer).toList();
            List<String> left = batch.stream()
                .filter(e -> e.getType() == LEAVE).map(PresenceEvent::getUserId).toList();
            broadcastBatchUpdate(resourceId, joined, left);
        }
    }
}
```

---

### Deep Dive 4: Presence Server Failure and Reconnection

**Problem**: A Presence Server crash causes thousands of WebSocket clients to disconnect simultaneously, potentially causing a massive LEAVE/rejoin storm.

**The TTL Grace Period Solution**:
```
Server 2 crashes (holds 1,000 connections, all viewing various docs)

WITHOUT grace period:
  1,000 LEAVE events broadcast -> 1,000 JOIN events (after reconnect)
  -> massive avatar flicker for all viewers everywhere

WITH grace period (TTL = 30s, reconnect < 30s):
  Client detects disconnect via WS ping/pong timeout (~5-10s)
  Client reconnects (exponential backoff: 1s, 2s, 4s) -> usually within 5-10s
  Client sends REJOIN with existing sessionId
  Server: session HASH still exists in Redis (TTL not expired)
  Server: EXPIRE session:{sessionId} 30   <- refresh TTL, NO events
  Other viewers: see NOTHING (seamless reconnect)

If reconnect > 30s (very poor network or extended server outage):
  TTL expired -> LEAVE already broadcast
  Client sends fresh JOIN -> user re-appears normally
```

```java
public class PresenceReconnectionHandler {

    public ReconnectResult handleReconnect(String sessionId, String userId) {
        String key = "session:" + sessionId;
        if (!redis.hasKey(key)) {
            return ReconnectResult.EXPIRED; // Must send fresh JOIN
        }
        // Security: verify the userId matches what's stored
        String stored = (String) redis.opsForHash().get(key, "userId");
        if (!userId.equals(stored)) {
            return ReconnectResult.UNAUTHORIZED;
        }
        // Seamless resume: refresh TTL, re-register locally
        redis.expire(key, Duration