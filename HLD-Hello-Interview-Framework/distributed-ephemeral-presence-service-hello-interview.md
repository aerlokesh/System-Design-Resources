# Design a Distributed "Ephemeral" Presence Service (Google Workspace "Who is Online?") — Hello Interview Framework

> **Question**: Design the "Who is online?" system for a collaborative suite like Google Workspace. When a user opens a document, everyone else currently viewing it can see who's online in real-time. The system must handle millions of concurrent users across millions of documents, with presence state that automatically expires when users disconnect.
>
> **Asked at**: Google, Meta, Slack, Atlassian, Notion
>
> **Difficulty**: Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Join Presence**: When a user opens a document, all current viewers of that document are notified (see the new user's avatar/name appear).
2. **Leave Presence**: When a user closes a document or disconnects, all remaining viewers are notified (user disappears from the viewer list).
3. **View Viewer List**: A user opening a document can see who is currently viewing it in real-time.
4. **Auto-Expiry**: If a user disconnects abruptly (crash, network loss, tab close), their presence automatically expires within a bounded time (e.g., 30 seconds).
5. **Idle Detection**: Distinguish between "active" (typing/clicking) and "idle" (tab open but no interaction for 5 minutes). Show different visual indicator.

#### Nice to Have (P1)
- Live cursor position broadcast (which line a collaborator is editing).
- User status aggregation across the entire workspace (global "who is online?" sidebar).
- Presence for other resource types beyond documents (spreadsheets, slides, chats).
- "Last seen" timestamp for users who recently left.
- Do Not Disturb / invisible mode (user can hide presence).
- Cross-device presence (user opens same doc on phone + laptop → shown once, not twice).

#### Below the Line (Out of Scope)
- Full collaborative editing (OT/CRDT for conflict resolution).
- Document content storage.
- Authentication system internals.
- Mobile push notifications for offline users.
- Analytics on document viewing patterns.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Presence Propagation Latency** | < 1 second P99 (join/leave visible to all viewers) | UX: avatars should appear/disappear promptly |
| **Heartbeat Expiry** | Max 30 seconds after disconnect | Dead presence is misleading; longer = stale data |
| **Availability** | 99.99% for read path (seeing who's online) | Presence is a UX feature; brief write outage tolerable |
| **Consistency** | Eventually consistent (seconds) | Seeing slightly stale viewer list is acceptable |
| **Scale** | 100M DAU, 10M concurrent users, 5M active documents | Google Workspace-scale |
| **Fan-out per document** | Support 1,000 concurrent viewers on a single document | Popular shared docs (all-hands agenda, company wiki) |
| **Memory efficiency** | Ephemeral data only; no long-term storage of presence | Data must auto-expire; no "cold storage" accumulation |
| **Reconnection** | Presence restored within 5 seconds after reconnect | Tab refresh should re-join seamlessly |

### Capacity Estimation

```
Users & Sessions:
  Daily active users: 100M
  Peak concurrent users: 10M (10% of DAU)
  Average documents open per user: 2
  Concurrent "presence sessions": 20M

Documents with active viewers:
  Active documents: ~5M (many docs have 0-1 viewers)
  Average viewers per active doc: 4
  Peak viewers per popular doc: 1,000 (all-hands, company wikis)
  
Presence Events:
  Session opens/closes: ~5M/hour during business hours
  Heartbeat frequency: every 15 seconds per session
  Heartbeats/sec: 20M sessions / 15 sec = ~1.3M heartbeats/sec
  Join/leave events/sec: ~1,400/sec (5M events/hour)
  
Storage (Redis - ephemeral only):
  Per presence entry: user_id (16B) + metadata (64B) = ~80 bytes
  Total entries: 20M sessions × 80 bytes = ~1.6 GB
  Plus doc→user sets: 5M docs × 4 users × 20 bytes = ~400 MB
  Total Redis memory: ~2 GB (very manageable)

Fan-out:
  Join/leave event to N viewers: 1,400 events/sec × avg 4 viewers = ~5,600 pushes/sec
  Peak single popular doc: 1 event × 1,000 viewers = 1,000 simultaneous WebSocket pushes
  
Bandwidth:
  Presence update message: ~200 bytes JSON
  Total push bandwidth: 5,600 × 200 bytes = ~1.1 MB/sec (negligible)
```

---

## 2️⃣ Core Entities

### Entity 1: PresenceSession
```java
public class PresenceSession {
    private final String sessionId;         // UUID per browser tab/client instance
    private final String userId;            // Authenticated user
    private final String resourceId;        // Document/resource being viewed (e.g., "doc_abc123")
    private final ResourceType resourceType; // DOCUMENT, SPREADSHEET, SLIDE, etc.
    private final PresenceStatus status;    // ACTIVE, IDLE
    private final Instant lastHeartbeatAt;  // Last received heartbeat
    private final Instant joinedAt;         // When user opened the document
    private final String serverNodeId;      // Which Presence Server owns this WebSocket
    private final int expiryTtlSeconds;     // Auto-expire TTL (e.g., 30 seconds)
}

public enum PresenceStatus {
    ACTIVE,   // User is actively interacting (typing, clicking, scrolling)
    IDLE      // Tab is open but no interaction for > 5 minutes
}
```

### Entity 2: PresenceView (per document)
```java
public class PresenceView {
    private final String resourceId;            // Document/resource ID
    private final List<ViewerInfo> viewers;     // All current viewers
    private final int viewerCount;              // Total concurrent viewers
    private final Instant fetchedAt;            // Timestamp of this snapshot
}

public class ViewerInfo {
    private final String userId;
    private final String displayName;
    private final String avatarUrl;
    private final PresenceStatus status;        // ACTIVE or IDLE
    private final Instant joinedAt;
    private final String sessionId;             // For deduplication across tabs
}
```

### Entity 3: PresenceEvent
```java
public class PresenceEvent {
    private final String eventId;               // UUID
    private final PresenceEventType type;       // JOIN, LEAVE, STATUS_CHANGE
    private final String resourceId;            // Affected document
    private final String userId;
    private final String sessionId;
    private final ViewerInfo viewer;            // Full viewer info for JOIN events
    private final PresenceStatus newStatus;     // For STATUS_CHANGE events
    private final Instant timestamp;
}

public enum PresenceEventType {
    JOIN,           // User opened the document
    LEAVE,          // User closed or disconnected (explicit or TTL-expired)
    STATUS_CHANGE   // ACTIVE ↔ IDLE transition
}
```

### Entity 4: UserPresenceAggregate (cross-workspace)
```java
public class UserPresenceAggregate {
    private final String userId;
    private final OverallStatus overallStatus;  // ONLINE, IDLE, OFFLINE
    private final List<ResourcePresence> activeIn; // Which docs user is in
    private final Instant lastSeenAt;
}

public enum OverallStatus {
    ONLINE,   // At least one ACTIVE session
    IDLE,     // All sessions are IDLE
    OFFLINE   // No active sessions
}
```

---

## 3️⃣ API Design

### 1. Join Presence (WebSocket Handshake + Message)
```
WS: Connect → wss://presence.workspace.example.com/ws?resource_id=doc_abc123

Authentication: JWT in Authorization header or query param (token=...)
Connection established → server sends current viewer list

Client → Server (after connect):
{
  "type": "JOIN",
  "resource_id": "doc_abc123",
  "resource_type": "DOCUMENT"
}

Server → Client (immediate response — current viewers snapshot):
{
  "type": "PRESENCE_SNAPSHOT",
  "resource_id": "doc_abc123",
  "viewers": [
    {
      "user_id": "user_456",
      "display_name": "Alice Chen",
      "avatar_url": "https://...",
      "status": "ACTIVE",
      "joined_at": "2026-02-28T14:00:00Z"
    },
    {
      "user_id": "user_789",
      "display_name": "Bob Smith",
      "avatar_url": "https://...",
      "status": "IDLE",
      "joined_at": "2026-02-28T13:55:00Z"
    }
  ],
  "viewer_count": 2
}

Server → ALL OTHER VIEWERS (push notification):
{
  "type": "USER_JOINED",
  "resource_id": "doc_abc123",
  "viewer": {
    "user_id": "user_123",
    "display_name": "Carol Davis",
    "avatar_url": "https://...",
    "status": "ACTIVE",
    "joined_at": "2026-02-28T14:05:00Z"
  }
}
```

### 2. Heartbeat (Client → Server, every 15 seconds)
```
Client → Server (WebSocket):
{
  "type": "HEARTBEAT",
  "resource_id": "doc_abc123",
  "status": "ACTIVE"  // or "IDLE"
}

Server → Client (acknowledgment):
{
  "type": "HEARTBEAT_ACK",
  "next_heartbeat_ms": 15000
}

--- If heartbeat stops coming: TTL expires after 30 seconds → LEAVE event generated ---
```

### 3. Leave Presence (Explicit or Auto-Expired)
```
Client → Server (WebSocket close or message):
{
  "type": "LEAVE",
  "resource_id": "doc_abc123"
}

Server → ALL REMAINING VIEWERS:
{
  "type": "USER_LEFT",
  "resource_id": "doc_abc123",
  "user_id": "user_123",
  "session_id": "sess_xyz"
}
```

### 4. Get Viewer List (REST — for initial page load before WebSocket connects)
```
GET /api/v1/presence/resources/{resource_id}/viewers

Response (200 OK):
{
  "resource_id": "doc_abc123",
  "viewer_count": 3,
  "viewers": [
    {
      "user_id": "user_456",
      "display_name": "Alice Chen",
      "avatar_url": "https://...",
      "status": "ACTIVE",
      "joined_at": "2026-02-28T14:00:00Z"
    }
  ],
  "fetched_at": "2026-02-28T14:05:30Z"
}
```

### 5. Get User's Overall Status (Workspace Sidebar)
```
GET /api/v1/presence/users/{user_id}

Response (200 OK):
{
  "user_id": "user_456",
  "overall_status": "ONLINE",
  "active_resources": [
    { "resource_id": "doc_abc123", "resource_type": "DOCUMENT", "status": "ACTIVE" }
  ],
  "last_seen_at": "2026-02-28T14:05:30Z"
}
```

### 6. Status Change (Idle Detection via Client)
```
Client → Server (WebSocket):
{
  "type": "STATUS_CHANGE",
  "resource_id": "doc_abc123",
  "new_status": "IDLE"
}

Server → ALL VIEWERS of doc_abc123:
{
  "type": "USER_STATUS_CHANGED",
  "resource_id": "doc_abc123",
  "user_id": "user_123",
  "new_status": "IDLE"
}
```

---

## 4️⃣ Data Flow

### Flow 1: User Opens a Document (Join)
```
1. User opens doc_abc123 in browser
   ↓
2. Client fetches initial state:
   GET /api/v1/presence/resources/doc_abc123/viewers
   → Returns current viewer list from Redis (fast, < 5ms)
   → UI renders existing viewers immediately
   ↓
3. Client establishes WebSocket:
   WS: wss://presence.workspace.example.com/ws?resource_id=doc_abc123
   → Authenticated via JWT
   → Server assigns this session to a Presence Server (sticky routing by resource_id)
   ↓
4. Presence Server processes JOIN:
   a. Creates PresenceSession in Redis:
      HSET session:{sessionId} userId field1 resourceId field2 ...
      EXPIRE session:{sessionId} 30  ← TTL = heartbeat interval × 2
      SADD resource_viewers:{resourceId} {sessionId}
      EXPIRE resource_viewers:{resourceId} 3600 (1 hour, refreshed on heartbeat)
   b. Publishes JOIN event to pub/sub channel: presence:{resourceId}
   ↓
5. All Presence Servers subscribed to presence:{resourceId} receive the JOIN event
   → Each pushes USER_JOINED to their connected viewers of doc_abc123
   ↓
6. Server sends PRESENCE_SNAPSHOT to the newly joined client
   (full list of current viewers, read from Redis)
```

### Flow 2: Heartbeat Processing (Critical for Ephemeral Correctness)
```
Every 15 seconds:
1. Client sends HEARTBEAT message with current status (ACTIVE or IDLE)
   ↓
2. Presence Server receives heartbeat:
   a. Refresh TTL in Redis:
      EXPIRE session:{sessionId} 30
      EXPIRE resource_viewers:{resourceId} 3600
   b. Update lastHeartbeatAt timestamp:
      HSET session:{sessionId} lastHeartbeatAt <now>
   c. If status changed (ACTIVE → IDLE or vice versa):
      Update Redis + publish STATUS_CHANGE event
   ↓
3. Server sends HEARTBEAT_ACK
   ↓
4. If heartbeat NEVER arrives (crash / network drop):
   → Redis TTL expires after 30 seconds
   → TTL expiry triggers LEAVE event via Redis Keyspace Notifications
     (or proactive polling by Presence Server)
```

### Flow 3: User Closes Document (Explicit Leave)
```
1. User closes tab / navigates away
   ↓
2. Browser fires "beforeunload" event → client sends LEAVE message (best-effort)
   ↓
3. Presence Server:
   a. Remove session from Redis:
      SREM resource_viewers:{resourceId} {sessionId}
      DEL session:{sessionId}
   b. Publish LEAVE event to presence:{resourceId}
   ↓
4. All subscribed Presence Servers broadcast USER_LEFT to their viewers
   ↓
5. If browser crashes / page killed (no LEAVE message):
   → TTL expires naturally after 30 seconds → same LEAVE flow triggers
```

### Flow 4: TTL Expiry → Auto-Generated LEAVE Event
```
Key problem: Redis TTL expiry must trigger a LEAVE broadcast.

Approach A (Redis Keyspace Notifications — used for small scale):
  1. Redis publishes "__keyevent@0__:expired" for session:{sessionId}
  2. Presence Server consumer listens on this channel
  3. Reads sessionId → derives userId + resourceId → publishes LEAVE event
  ↓
  Problem: At 20M sessions with 30s TTL → ~667K expirations/min
  Redis keyspace notifications can overwhelm single consumer
  
Approach B (Proactive polling — preferred at scale):
  1. Each Presence Server tracks sessions it owns
  2. Background thread checks: any session with lastHeartbeat > 30 seconds ago?
  3. If yes → server generates LEAVE event for that session
  4. This is efficient: each server only scans its own sessions (~40K per server with 500 servers)

Approach C (Lazy expiry detection — simpler):
  1. On any READ of resource_viewers:{resourceId}, filter out expired sessions
  2. Don't emit LEAVE events proactively
  3. Problem: other viewers won't see user disappear until next read
  4. Acceptable for non-critical presence (viewer count approximations)
  
→ Chosen: Approach B (proactive polling per server) for sub-30s latency
```

### Flow 5: Cross-Server Fan-out (Critical Design)
```
Problem: User A's session is on Presence Server 1.
         User B is viewing the same doc but connected to Presence Server 2.
         When User A joins, how does Presence Server 2 know to notify User B?

Solution: Redis Pub/Sub channels per resource

  1. User A joins doc_abc123 on Presence Server 1
     ↓
  2. Server 1 publishes to Redis channel "presence:doc_abc123":
     { "type": "JOIN", "userId": "A", "sessionId": "...", "viewer": {...} }
     ↓
  3. ALL Presence Servers (1, 2, 3, ... N) are subscribed to "presence:doc_abc123"
     ↓
  4. Server 2 receives the JOIN event from Redis
     ↓
  5. Server 2 finds all WebSocket connections it manages that are viewing doc_abc123
     ↓
  6. Server 2 pushes USER_JOINED to those connections (e.g., User B's connection)

Pub/Sub subscription management:
  → Server subscribes to "presence:{resourceId}" when its FIRST viewer joins that doc
  → Server unsubscribes when its LAST viewer leaves that doc
  → This keeps subscription count proportional to active docs per server, not total docs
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                               CLIENTS                                         │
│                                                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │  Browser (Google Workspace Tab)                                       │    │
│  │                                                                        │    │
│  │  ┌─────────────────────┐    ┌────────────────────────────────────┐   │    │
│  │  │ REST: Initial viewer │    │ WebSocket: Real-time updates        │   │    │
│  │  │ list on page load   │    │ • Heartbeat every 15s               │   │    │
│  │  │                     │    │ • Receive: USER_JOINED/LEFT         │   │    │
│  │  │ GET /presence/      │    │ • Send: HEARTBEAT, STATUS_CHANGE    │   │    │
│  │  │   resources/{id}/   │    │ • Auto-reconnect on disconnect       │   │    │
│  │  │   viewers           │    │                                      │   │    │
│  │  └─────────────────────┘    └────────────────────────────────────┘   │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────┘
                │ REST                        │ WebSocket
                ▼                             ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY / LOAD BALANCER                            │
│                                                                                │
│  • Auth (JWT validation)            • WebSocket sticky routing by user_id    │
│  • REST routing to Presence API     • Rate limiting                           │
│  • TLS termination                  • Health checks                           │
└───────────────┬──────────────────────────────┬────────────────────────────────┘
                │ REST                         │ WebSocket (consistent hash by resource_id)
                ▼                              ▼
┌─────────────────────────┐    ┌──────────────────────────────────────────────┐
│  PRESENCE REST API       │    │  PRESENCE SERVER CLUSTER                      │
│  (Stateless, 10-20 pods) │    │  (Stateful — WebSocket connections)           │
│                          │    │                                                │
│  • GET viewer list       │    │ ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  • GET user status       │    │ │ Server 1  │  │ Server 2  │  │ Server N  │   │
│  • Reads from Redis only │    │ │          │  │          │  │          │    │
│                          │    │ │ ~40K WS  │  │ ~40K WS  │  │ ~40K WS  │   │
│  No writes               │    │ │ sessions │  │ sessions │  │ sessions │   │
│  No WebSockets           │    │ │          │  │          │  │          │   │
└─────────────────────────┘    │ │ • Routes  │  │ • Routes  │  │ • Routes  │   │
                                │ │   by      │  │   by      │  │   by      │   │
                                │ │ resource  │  │ resource  │  │ resource  │   │
                                │ │           │  │           │  │           │   │
                                │ │ Subscribe │  │ Subscribe │  │ Subscribe │   │
                                │ │ to Redis  │  │ to Redis  │  │ to Redis  │   │
                                │ │ pub/sub   │  │ pub/sub   │  │ pub/sub   │   │
                                │ └──────────┘  └──────────┘  └──────────┘   │
                                │                                               │
                                │  ~500 servers (10M sessions / 20K per server) │
                                └─────────────────────┬─────────────────────────┘
                                                      │
                            ┌─────────────────────────┼──────────────────────┐
                            │                         │                      │
                            ▼                         ▼                      ▼
              ┌─────────────────────┐   ┌──────────────────────┐  ┌──────────────────┐
              │  REDIS CLUSTER      │   │ REDIS PUB/SUB        │  │  KAFKA           │
              │  (Presence State)   │   │ (Cross-server fan-out)│  │  (Audit / Async) │
              │                     │   │                      │  │                  │
              │  ┌───────────────┐  │   │ Channels:            │  │  Topic:          │
              │  │ session:{id}  │  │   │ presence:{resourceId}│  │  presence-events │
              │  │ HASH + TTL=30s│  │   │                      │  │  (JOIN/LEAVE)    │
              │  └───────────────┘  │   │ Published by:        │  │                  │
              │  ┌───────────────┐  │   │  Presence Servers    │  │  Consumers:      │
              │  │ resource_     │  │   │                      │  │  • Analytics     │
              │  │ viewers:{id}  │  │   │ Subscribed by:       │  │  • Notifications │
              │  │ SET of        │  │   │  ALL Presence Servers│  │  • Audit logs    │
              │  │ sessionIds    │  │   │  interested in doc   │  │                  │
              │  └───────────────┘  │   │                      │  │                  │
              │  ┌───────────────┐  │   │ At-most-once         │  │  At-least-once   │
              │  │ user_sessions:│  │   │ (fire-and-forget)    │  │  (durable log)   │
              │  │ {userId}      │  │   │                      │  │                  │
              │  │ SET of        │  │   │ ~5M active channels  │  │                  │
              │  │ sessionIds    │  │   │ ~2M subscriptions    │  │                  │
              │  └───────────────┘  │   │                      │  │                  │
              │                     │   │                      │  │                  │
              │  ~2 GB total        │   │                      │  │                  │
              │  Sharded cluster    │   │                      │  │                  │
              └─────────────────────┘   └──────────────────────┘  └──────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling Strategy |
|-----------|---------|------------|-----------------|
| **Presence REST API** | Serve initial viewer list, user status queries | Node.js/Go (stateless pods) | 10-20 pods; reads Redis only |
| **Presence Server** | Manage WebSocket connections, heartbeats, fan-out | Go/Node.js (stateful) | 500 servers × 20K connections each |
| **Redis Cluster (State)** | Ephemeral presence state with TTL auto-expiry | Redis 7+ cluster | ~2 GB total; sharded by key prefix |
| **Redis Pub/Sub** | Cross-server fan-out for presence events | Redis Pub/Sub (or Valkey) | ~5M active channels; stateless |
| **Kafka** | Durable event log for analytics, audit, notifications | Apache Kafka | 5+ brokers; async consumers only |
| **API Gateway** | Auth, routing, WebSocket sticky sessions | Nginx/Envoy | Standard LB setup |

---

## 6️⃣ Deep Dives

### Deep Dive 1: The Ephemeral State Problem — TTL-Based Presence

**The Core Challenge**: Presence is inherently ephemeral. Users open and close tabs constantly. A user's browser can crash without sending an explicit LEAVE. Network connections drop silently. The system must automatically "forget" users who are no longer active without manual cleanup.

**The Heartbeat + TTL Contract**:

```
Client                          Presence Server              Redis
  │                                    │                        │
  │──── JOIN (resource=doc_abc123) ───>│                        │
  │                                    │─ HSET session:sess1 ──>│
  │                                    │  {userId, resourceId}  │
  │                                    │─ EXPIRE session:sess1 30>│ ← 30 second TTL
  │                                    │─ SADD resource_viewers:doc_abc123 sess1 ──>│
  │                                    │                        │
  │ ←──── PRESENCE_SNAPSHOT ─────────-│                        │
  │                                    │                        │
  │ [15 seconds pass]                  │                        │
  │                                    │                        │
  │──── HEARTBEAT (status=ACTIVE) ────>│                        │
  │                                    │─ EXPIRE session:sess1 30>│ ← Refresh TTL
  │ ←──── HEARTBEAT_ACK ───────────── │                        │
  │                                    │                        │
  │ [user's laptop battery dies]       │                        │
  │                                    │                        │
  │ [15 seconds pass — no heartbeat]   │                        │
  │ [15 more seconds — still nothing]  │                        │
  │                                    │                        │
  │                          [TTL expires: Redis DEL session:sess1]
  │                                    │                        │
  │                          ← proactive scanner detects
  │                            lastHeartbeat > 30s ago
  │                            → generate LEAVE event
  │                            → notify other viewers
```

```java
public class HeartbeatManager {
    
    /**
     * Redis data model for presence:
     * 
     * 1. session:{sessionId} → HASH
     *    Fields: userId, resourceId, resourceType, status, joinedAt, lastHeartbeatAt, serverNodeId
     *    TTL: 30 seconds (refreshed on every heartbeat)
     * 
     * 2. resource_viewers:{resourceId} → SET of sessionIds
     *    TTL: 3600 seconds (refreshed on any activity in that resource)
     *    Purpose: "which sessions are viewing this document?"
     * 
     * 3. user_sessions:{userId} → SET of sessionIds  
     *    TTL: 3600 seconds
     *    Purpose: "which documents is this user in?" (for global presence view)
     * 
     * Key insight: TTL on session:{sessionId} is the heartbeat contract.
     *   If the key exists → user is (probably) online.
     *   If the key is gone → user timed out.
     * 
     * The SET resource_viewers:{resourceId} may contain stale sessionIds
     * (sessions that expired). Always JOIN with a session existence check:
     *   SMEMBERS resource_viewers:{resourceId}
     *   → For each sessionId: EXISTS session:{sessionId}
     *   → Filter out sessions that no longer exist (TTL expired)
     */
    
    private final RedisTemplate<String, String> redis;
    private final PresenceEventPublisher eventPublisher;
    
    // Called every 15 seconds by the client
    public HeartbeatResult processHeartbeat(String sessionId, PresenceStatus newStatus) {
        String sessionKey = "session:" + sessionId;
        
        // Refresh TTL — atomic: if key doesn't exist, heartbeat is for a dead session
        Boolean existed = redis.expire(sessionKey, Duration.ofSeconds(30));
        if (Boolean.FALSE.equals(existed)) {
            // Session was already expired → treat as re-join
            return HeartbeatResult.SESSION_EXPIRED;
        }
        
        // Check for status transition
        String currentStatus = redis.opsForHash().get(sessionKey, "status");
        if (!newStatus.name().equals(currentStatus)) {
            redis.opsForHash().put(sessionKey, "status", newStatus.name());
            redis.opsForHash().put(sessionKey, "lastHeartbeatAt", Instant.now().toString());
            
            // Publish STATUS_CHANGE to all viewers of this resource
            String resourceId = (String) redis.opsForHash().get(sessionKey, "resourceId");
            String userId = (String) redis.opsForHash().get(sessionKey, "userId");
            eventPublisher.publish("presence:" + resourceId, new PresenceEvent(
                PresenceEventType.STATUS_CHANGE, resourceId, userId, sessionId, newStatus));
        } else {
            // Just refresh lastHeartbeatAt without broadcasting
            redis.opsForHash().put(sessionKey, "lastHeartbeatAt", Instant.now().toString());
        }
        
        return HeartbeatResult.OK;
    }
    
    // Background proactive expiry scanner (runs every 10 seconds per server)
    // Each Presence Server only scans sessions it owns (sessionId → serverNodeId mapping)
    @Scheduled(fixedRate = 10000)
    public void scanForExpiredSessions() {
        Set<String> ownedSessions = localSessionRegistry.getAllSessionIds();
        Instant expiryThreshold = Instant.now().minusSeconds(30);
        
        for (String sessionId : ownedSessions) {
            String lastHeartbeatStr = (String) redis.opsForHash()
                .get("session:" + sessionId, "lastHeartbeatAt");
            
            if (lastHeartbeatStr == null) continue;
            Instant lastHeartbeat = Instant.parse(lastHeartbeatStr);
            
            if (lastHeartbeat.isBefore(expiryThreshold)) {
                // Session timed out — generate LEAVE event
                String resourceId = (String) redis.opsForHash()
                    .get("session:" + sessionId, "resourceId");
                String userId = (String) redis.opsForHash()
                    .get("session:" + sessionId, "userId");
                
                // Clean up Redis
                redis.opsForSet().remove("resource_viewers:" + resourceId, sessionId);
                redis.opsForSet().remove("user_sessions:" + userId, sessionId);
                redis.delete("session:" + sessionId);
                
                // Remove from local registry
                localSessionRegistry.remove(sessionId);
                
                // Broadcast LEAVE to all viewers
                eventPublisher.publish("presence:" + resourceId, new PresenceEvent(
                    PresenceEventType.LEAVE, resourceId, userId, sessionId, null));
            }
        }
    }
    
    // Get viewer list for a resource (filters stale entries)
    public List<ViewerInfo> getViewers(String resourceId) {
        Set<String> sessionIds = redis.opsForSet().members("resource_viewers:" + resourceId);
        List<ViewerInfo> viewers = new ArrayList<>();
        
        for (String sessionId : sessionIds) {
            Map<Object, Object> sessionData = redis.opsForHash().entries("session:" + sessionId);
            if (sessionData.isEmpty()) {
                // Stale entry — session TTL expired; clean up lazily
                redis.opsForSet().remove("resource_viewers:" + resourceId, sessionId);
                continue;
            }
            viewers.add(ViewerInfo.fromSessionData(sessionId, sessionData));
        }
        
        return viewers;
    }
}
```

**TTL Tuning Trade-offs**:

| TTL Setting | Heartbeat Interval | Staleness Window | Cost |
|------------|-------------------|-----------------|------|
| 10 seconds | 5s | Up to 10s stale | 2× more heartbeats |
| 30 seconds | 15s | Up to 30s stale | Moderate load |
| 60 seconds | 30s | Up to 60s stale | Half the heartbeats |

→ **Chosen: 30s TTL / 15s heartbeat** — good balance for UX (stale avatars < 30s) vs. scale (1.3M heartbeats/sec manageable).

---

### Deep Dive 2: Cross-Server Fan-out — Redis Pub/Sub vs. Alternatives

**The Problem**: 500 Presence Servers each hold ~20K WebSocket connections. When a user joins a document, all viewers of that document—potentially spread across many servers—must be notified instantly. This is the classic "fan-out across stateful nodes" problem.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  CROSS-SERVER FAN-OUT OPTIONS                                 │
│                                                                               │
│  Option A: Full Mesh (Server-to-Server direct messaging)                     │
│  ┌──────┐     ┌──────┐     ┌──────┐     ┌──────┐                           │
│  │ S1   │────>│ S2   │────>│ S3   │────>│ SN   │                           │
│  └──────┘     └──────┘     └──────┘     └──────┘                           │
│  S1 knows which servers have viewers for doc_abc123 → sends directly        │
│  PRO: Low latency, no intermediary                                           │
│  CON: O(N²) connections for N servers, complex discovery, brittle           │
│                                                                               │
│  Option B: Redis Pub/Sub (Chosen)                                             │
│  ┌──────┐                                                                     │
│  │ S1   │──PUBLISH presence:doc_abc123 ──────────────────────────────┐      │
│  └──────┘                                                              ▼      │
│                                                              ┌──────────────┐ │
│                                                              │ Redis Pub/Sub│ │
│                                                              └──────┬───────┘ │
│              ┌─────────────────────────────────────────────────────┘         │
│              │          │          │          │                               │
│              ▼          ▼          ▼          ▼                               │
│          ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐                         │
│          │  S1  │   │  S2  │   │  S3  │   │  SN  │                         │
│          │ sub  │   │ sub  │   │ sub  │   │ sub  │                         │
│          └──────┘   └──────┘   └──────┘   └──────┘                         │
│  PRO: Simple, O(1) publish, automatic delivery to all subscribers           │
│  CON: At-most-once (fire-and-forget), no persistence, no replay             │
│  Acceptable: presence updates are ephemeral by nature                        │
│                                                                               │
│  Option C: Kafka (for high-durability fan-out)                                │
│  Better for: analytics, audit, notifications (async consumers)               │
│  Worse for: real-time presence (higher latency ~100ms vs ~5ms)               │
└─────────────────────────────────────────────────────────────────────────────┘
```

```java
public class PresenceEventPublisher {
    
    /**
     * Redis Pub/Sub channel naming:
     *   presence:{resourceId}
     *   Examples:
     *     presence:doc_abc123
     *     presence:sheet_xyz789
     *     presence:slide_pqr456
     * 
     * Subscription lifecycle (critical for memory management):
     *   SUBSCRIBE when first viewer of a resource joins THIS server
     *   UNSUBSCRIBE when last viewer of a resource leaves THIS server
     *   → Avoids subscribing to ALL ~5M channels (would overwhelm Redis)
     *   → Each server subscribes only to channels relevant to its current viewers
     * 
     * At 500 servers × avg 4 channels per server = ~2K subscriptions
     * (much less than 5M total channels)
     * 
     * Message format (JSON):
     * {
     *   "type": "JOIN|LEAVE|STATUS_CHANGE",
     *   "resourceId": "doc_abc123",
     *   "userId": "user_123",
     *   "sessionId": "sess_abc",
     *   "viewer": { ...ViewerInfo... },
     *   "timestamp": "2026-02-28T14:05:00Z"
     * }
     */
    
    private final RedisTemplate<String, String> redis;
    private final Map<String, Integer> channelViewerCounts = new ConcurrentHashMap<>();
    
    public void publish(String channel, PresenceEvent event) {
        redis.convertAndSend(channel, JsonSerializer.serialize(event));
        // Also publish to Kafka for durable audit log (async, non-blocking)
        kafkaTemplate.send("presence-events", event.getResourceId(), event);
    }
    
    public void onViewerJoinedThisServer(String resourceId) {
        channelViewerCounts.merge(resourceId, 1, Integer::sum);
        if (channelViewerCounts.get(resourceId) == 1) {
            // First viewer on this server → subscribe to this channel
            redisSubscriber.subscribe("presence:" + resourceId);
        }
    }
    
    public void onViewerLeftThisServer(String resourceId) {
        int remaining = channelViewerCounts.merge(resourceId, -1, Integer::sum);
        if (remaining == 0) {
            // Last viewer on this server → unsubscribe to save resources
            redisSubscriber.unsubscribe("presence:" + resourceId);
            channelViewerCounts.remove(resourceId);
        }
    }
    
    // Called when Redis delivers a message to a subscribed channel
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());    // "presence:doc_abc123"
        String resourceId = channel.substring("presence:".length());
        PresenceEvent event = JsonSerializer.deserialize(message.getBody());
        
        // Find all WebSocket sessions on THIS server viewing this resourceId
        Set<WebSocketSession> sessions = localViewerRegistry.getSessionsForResource(resourceId);
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                String payload = buildClientMessage(event);
                session.sendMessage(new TextMessage(payload));
            }
        }
    }
}
```

**Why Redis Pub/Sub over Kafka for real-time fan-out**:
- Redis Pub/Sub: ~1-5ms delivery, fire-and-forget, perfect for presence
- Kafka: ~50-100ms delivery, durable, better for analytics/audit
- **Hybrid**: Use Redis Pub/Sub for real-time delivery to WebSocket clients, Kafka for durable audit log (both in parallel)

---

### Deep Dive 3: Handling the "Popular Document" Problem (Hot Fan-out)

**The Problem**: A company-wide all-hands document or a shared engineering wiki can have 1,000+ simultaneous viewers. When any one person joins or leaves, 999 viewers must be notified. This is a hot fan-out challenge.

```
Regular doc (4 viewers):    1 event → 3 pushes (trivial)
Popular doc (1,000 viewers): 1 event → 999 pushes

At 1 join/leave per second on the popular doc:
  999 WebSocket pushes/sec from potentially all servers
  
How many servers serve the 1,000 viewers?
  1,000 viewers / 20K sessions per server = ~0.05 → fits on 1 server
  But with hashing distribution, spread across ~5-10 servers
  → Each server handles ~100-200 viewer connections
  → On join: each server pushes to its ~100-200 local viewers
  → Total: 999 pushes distributed across 5-10 servers (manageable)
```

```java
public class PopularDocumentHandler {
    
    /**
     * Strategies for popular documents:
     * 
     * 1. Threshold-based batching:
     *    For docs with > 100 viewers, batch presence events:
     *    Instead of "User X joined" per event:
     *    → Buffer 100ms of events
     *    → Send: "User X joined, User Y left" (merged update)
     *    → Reduces pushes by 10-100× during bursts (meeting start)
     * 
     * 2. Presence approximation for very large audiences:
     *    For docs with > 500 viewers:
     *    → Don't show individual avatars (too many)
     *    → Show "500+ people viewing this document"
     *    → Only broadcast viewer count changes, not individual join/leave
     *    → Redis INCR/DECR on a counter instead of full SET operations
     * 
     * 3. Fan-out offloading (for extreme scale):
     *    → Use a dedicated "Popular Doc Fan-out Service"
     *    → Presence Server publishes ONE event to Kafka
     *    → Fan-out Service has dedicated connections to high-viewer docs
     *    → Handles the 999 WebSocket pushes independently
     */
    
    private static final int INDIVIDUAL_AVATAR_THRESHOLD = 100;
    private static final int BATCHING_THRESHOLD = 50;
    
    public void handlePresenceEvent(PresenceEvent event, String resourceId) {
        int viewerCount = getViewerCount(resourceId);
        
        if (viewerCount > INDIVIDUAL_AVATAR_THRESHOLD) {
            // Switch to aggregate mode: just show viewer count
            broadcastViewerCountUpdate(resourceId, viewerCount);
        } else if (viewerCount > BATCHING_THRESHOLD) {
            // Batch mode: buffer events for 100ms before broadcasting
            eventBatcher.enqueue(resourceId, event);
        } else {
            // Normal mode: immediate individual broadcast
            broadcastImmediately(resourceId, event);
        }
    }
    
    // Batched broadcaster (flushes every 100ms)
    @Scheduled(fixedRate = 100)
    public void flushBatchedEvents() {
        for (Map.Entry<String, List<PresenceEvent>> entry : eventBatcher.drain()) {
            String resourceId = entry.getKey();
            List<PresenceEvent> batch = entry.getValue();
            
            // Merge: joins and leaves in the same 100ms → net delta
            List<ViewerInfo> joined = batch.stream()
                .filter(e -> e.getType() == PresenceEventType.JOIN)
                .map(PresenceEvent::getViewer).collect(toList());
            List<String> left = batch.stream()
                .filter(e -> e.getType() == PresenceEventType.LEAVE)
                .map(PresenceEvent::getUserId).collect(toList());
            
            // Single merged push per 100ms window
            broadcastBatchUpdate(resourceId, joined, left);
        }
    }
}
```

---

### Deep Dive 4: Presence Server Failure and Reconnection

**The Problem**: A Presence Server holds thousands of active WebSocket connections. If it crashes or is restarted (for deployment), all those clients lose their presence sessions simultaneously.

```
Failure scenario:
  Server 2 crashes.
  1,000 users connected to Server 2 lose their WebSocket connection.
  Each of these users is viewing some document.
  Other viewers of those documents see 1,000 users "disappear" simultaneously.
  After reconnect, those 1,000 users "re-appear."

Design goals:
  1. Client detects disconnect quickly (< 5 seconds via WebSocket ping/pong)
  2. Client auto-reconnects with exponential backoff
  3. Re-joined sessions restore presence within 5 seconds
  4. During disconnect window (< 30 seconds): TTL keeps session alive
     → Other viewers don't see a leave/rejoin blip
```

```java
public class PresenceReconnectionHandler {
    
    /**
     * Client-side reconnection strategy:
     * 
     * 1. WebSocket ping/pong (native): detects dead connections
     *    → Server pings every 10s, client pongs
     *    → 2 missed pongs = connection dead
     * 
     * 2. Exponential backoff reconnect:
     *    Attempt 1: 1s delay
     *    Attempt 2: 2s delay
     *    Attempt 3: 4s delay
     *    Attempt 4: 8s delay ... up to max 30s
     * 
     * 3. Reconnection grace period:
     *    Session TTL = 30s. If client reconnects within 30s:
     *    → Session still exists in Redis (TTL not yet expired)
     *    → Client sends REJOIN with sessionId
     *    → Server refreshes TTL, NO LEAVE event broadcast
     *    → Seamless reconnect — other viewers see nothing
     * 
     *    If client reconnects AFTER 30s:
     *    → Session expired → LEAVE already broadcast
     *    → Client sends fresh JOIN
     *    → User re-appears as new viewer
     * 
     * Server-side handling:
     */
    
    public ReconnectResult handleReconnect(String sessionId, String userId) {
        String sessionKey = "session:" + sessionId;
        Boolean sessionExists = redis.hasKey(sessionKey);
        
        if (Boolean.TRUE.equals(sessionExists)) {
            // Session still alive (within grace period) → seamless resume
            redis.expire(sessionKey, Duration.ofSeconds(30));  // Refresh TTL
            
            // Verify userId matches (security check)
            String storedUserId = (String) redis.opsForHash().get(sessionKey, "userId");
            if (!userId.equals(storedUserId)) {
                return ReconnectResult.UNAUTHORIZED;
            }
            
            // Re-register with local server registry
            localSessionRegistry.register(sessionId, userId);
            
            // Send current snapshot to client (state may have changed during disconnect)
            String resourceId = (String) redis.opsForHash().get(sessionKey, "resourceId");
            List<ViewerInfo> viewers = heartbeatManager.getViewers(resourceId);
            
            return ReconnectResult.resumed(viewers);
        } else {
            // Session expired → treat as new JOIN
            return ReconnectResult.EXPIRED;
        }
    }
    
    /**
     * Server restart strategy (rolling deployments):
     * 
     * Problem: Kubernetes rolling restart moves pod → all connections lost
     * 
     * Solution: Graceful shutdown with connection draining
     * 1. K8s sends SIGTERM to pod
     * 2. Pod stops accepting new connections
     * 3. Pod sends "RECONNECTING" message to all connected clients:
     *    { "type": "SERVER_RESTART", "reconnect_after_ms": 2000 }
     * 4. Clients pause heartbeats, wait, then reconnect to new server
     * 5. Sessions stay alive in Redis (TTL not expired during 2s window)
     * 6. Pod waits 3s for connections to drain, then shuts down
     * 
     * Result: Clients experience 2-3s reconnect, then seamless resume.
     *         No spurious LEAVE/JOIN events for other viewers.
     */
}
```

---

### Deep Dive 5: Cross-Device Presence Deduplication

**The Problem**: A user has Google Workspace open on both their laptop browser and phone. Both devices open the same document. Should other viewers see the user listed twice? No — they should appear once with a consolidated status.

```
User "alice" has two sessions:
  session_A: laptop browser, status=ACTIVE
  session_B: phone browser, status=IDLE

Other viewers should see: Alice (ACTIVE) — shown once
If Alice closes laptop: Alice (IDLE) — shown once  
If Alice closes phone too: Alice disappears

Redis model for deduplication:
  user_sessions:{userId} → SET of sessionIds
  
  Example:
  user_sessions:alice → { "session_A", "session_B" }
  session:session_A   → { userId: alice, resourceId: doc_abc123, status: ACTIVE }
  session:session_B   → { userId: alice, resourceId: doc_abc123, status: IDLE }
```

```java
public class PresenceDeduplicationService {
    
    /**
     * Consolidated presence rules:
     * 
     * 1. User appears in viewer list ONCE per document (not once per session)
     * 2. Effective status = highest-priority status across all sessions:
     *    ACTIVE > IDLE > (absent)
     * 3. JOIN event broadcast only when user goes from 0 sessions → 1 session
     * 4. LEAVE event broadcast only when user goes from 1 session → 0 sessions
     * 5. STATUS_CHANGE broadcast when effective status changes
     * 
     * Example flow:
     *   Alice opens doc on laptop → session_A created → JOIN broadcast (1st session)
     *   Alice opens doc on phone → session_B created → NO JOIN broadcast (already present)
     *   Alice closes phone → session_B deleted → NO LEAVE broadcast (session_A still alive)
     *   Alice closes laptop → session_A deleted → LEAVE broadcast (no more sessions)
     */
    
    public void handleSessionJoin(String userId, String sessionId, String resourceId,
                                  PresenceStatus status) {
        // Add session to user's session set
        redis.opsForSet().add("user_sessions:" + userId + ":" + resourceId, sessionId);
        Long sessionCount = redis.opsForSet().size("user_sessions:" + userId + ":" + resourceId);
        
        if (sessionCount == 1) {
            // First session for this user on this resource → emit JOIN
            eventPublisher.publish("presence:" + resourceId, PresenceEvent.join(
                resourceId, userId, sessionId, buildViewerInfo(userId, status)));
        } else {
            // Already present (multi-device) → check if effective status changed
            PresenceStatus previousEffective = getEffectiveStatus(userId, resourceId);
            if (status == PresenceStatus.ACTIVE && previousEffective == PresenceStatus.IDLE) {
                eventPublisher.publish("presence:" + resourceId, PresenceEvent.statusChange(
                    resourceId, userId, sessionId, PresenceStatus.ACTIVE));
            }
        }
    }
    
    public void handleSessionLeave(String userId, String sessionId, String resourceId) {
        redis.opsForSet().remove("user_sessions:" + userId + ":" + resourceId, sessionId);
        Long sessionCount = redis.opsForSet().size("user_sessions:" + userId + ":" + resourceId);
        
        if (sessionCount == 0) {
            // Last session gone → emit LEAVE
            redis.delete("user_sessions:" + userId + ":" + resourceId);
            eventPublisher.publish("presence:" + resourceId, PresenceEvent.leave(
                resourceId, userId, sessionId));
        } else {
            // Still has other sessions → check if effective status degraded
            PresenceStatus newEffective = getEffectiveStatus(userId, resourceId);
            PresenceStatus removedStatus = getSessionStatus(sessionId);
            
            if (removedStatus == PresenceStatus.ACTIVE && newEffective == PresenceStatus.IDLE) {
                // User downgraded: was ACTIVE via this session, all remaining are IDLE
                eventPublisher.publish("presence:" + resourceId, PresenceEvent.statusChange(
                    resourceId, userId, sessionId, PresenceStatus.IDLE));
            }
        }
    }
    
    private PresenceStatus getEffectiveStatus(String userId, String resourceId) {
        Set<String> sessionIds = redis.opsForSet()
            .members("user_sessions:" + userId + ":" + resourceId);
        
        return sessionIds.stream()
            .map(sid -> PresenceStatus.valueOf(
                (String) redis.opsForHash().get("session:" + sid, "status")))
            .filter(Objects::nonNull)
            .max(Comparator.comparingInt(PresenceStatus::getPriority))
            .orElse(null);
    }
}
```

---

### Deep Dive 6: Presence at Global Scale — Redis Sharding Strategy

**The Problem**: With 20M active sessions and 5M active documents, we need to understand Redis cluster topology to avoid hotspots.

```java
public class RedisShardingStrategy {
    
    /**
     * Redis Cluster sharding for presence data:
     * 
     * Default Redis Cluster: consistent hashing over 16,384 hash slots
     * Key distribution by default hash of key prefix
     * 
     * Our keys:
     *   session:{sessionId}          → 20M keys, ~80 bytes each = ~1.6 GB
     *   resource_viewers:{resourceId} → 5M keys, ~80 bytes each = ~400 MB
     *   user_sessions:{userId}:{res} → ~20M keys, ~40 bytes each = ~800 MB
     * 
     * Total: ~3 GB → fits on 1 node but use cluster for HA/throughput
     * 
     * HOTSPOT RISK: resource_viewers:{popular_doc}
     * 
     * Problem: resource_viewers:doc_all_hands_2026 is a SET with 1,000 members.
     *   - Every JOIN/LEAVE = SADD/SREM to this key
     *   - Popular doc = 10 events/second to same key
     *   - All operations route to the same Redis slot/node
     *   - Not inherently a problem at 10 ops/sec; Redis handles >> 100K ops/sec per node
     * 
     * Actual throughput concern:
     *   Heartbeats: 1.3M/sec across all sessions
     *   But each heartbeat updates ONE key (session:{sessionId})
     *   Evenly distributed by sessionId hash → ~100K per Redis node (with 13 nodes)
     *   Well within Redis capacity (~1M simple ops/sec per node)
     * 
     * Redis Pub/Sub note:
     *   Pub/Sub is NOT shardable across Redis Cluster!
     *   A PUBLISH command goes to ONE node; only subscribers on that node receive it.
     *   
     *   Solution options:
     *   A. Single Redis node dedicated to Pub/Sub (separate from cluster)
     *      → 5M channels × metadata = manageable
     *      → Risk: single point of failure
     *   
     *   B. Redis Cluster sharded Pub/Sub (Redis 7+):
     *      → SSUBSCRIBE / SPUBLISH commands
     *      → Channel is hashed to a slot → goes to correct node
     *      → Subscribers must be on the right slot
     *      → Works with our resource-based channel naming
     *   
     *   C. Kafka for fan-out (alternative to Redis Pub/Sub):
     *      → More durable, truly distributed
     *      → Higher latency (~50ms vs ~5ms)
     *      → Better if presence updates must not be lost
     *   
     *   → Chosen: Redis 7+ Sharded Pub/Sub (SSUBSCRIBE/SPUBLISH)
     *     Each presence:{resourceId} channel is consistently hashed to a cluster node
     *     Presence Servers subscribe via SSUBSCRIBE → correct node receives it
     */
    
    // Redis 7+ Sharded Pub/Sub example
    public void publishToResource(String resourceId, PresenceEvent event) {
        String channel = "presence:" + resourceId;
        // SPUBLISH routes to the Redis node responsible for this channel's hash slot
        redis.convertAndSend("SPUBLISH", channel, JsonSerializer.serialize(event));
    }
    
    public void subscribeToResource(String resourceId) {
        // SSUBSCRIBE routes subscription to the correct Redis node
        shardedRedisSubscriber.ssubscribe("presence:" + resourceId, this::onMessage);
    }
}
```

---

### Deep Dive 7: Idle Detection Implementation

**The Problem**: The client must detect when a user becomes idle (no mouse/keyboard interaction for 5 minutes) and report this to the server, which must propagate the status change to all viewers.

```java
/**
 * CLIENT-SIDE idle detection (pseudocode/JavaScript concept):
 * 
 * const IDLE_TIMEOUT_MS = 5 * 60 * 1000;  // 5 minutes
 * let lastActivityAt = Date.now();
 * let currentStatus = 'ACTIVE';
 * 
 * // Track user activity
 * document.addEventListener('mousemove', resetTimer);
 * document.addEventListener('keydown', resetTimer);
 * document.addEventListener('click', resetTimer);
 * document.addEventListener('scroll', resetTimer);
 * document.addEventListener('visibilitychange', () => {
 *   if (document.hidden) setStatus('IDLE');
 *   else resetTimer();
 * });
 * 
 * function resetTimer() {
 *   lastActivityAt = Date.now();
 *   if (currentStatus === 'IDLE') {
 *     setStatus('ACTIVE');
 *   }
 * }
 * 
 * // Check idle every 30 seconds
 * setInterval(() => {
 *   const idleMs = Date.now() - lastActivityAt;
 *   if (idleMs > IDLE_TIMEOUT_MS && currentStatus === 'ACTIVE') {
 *     setStatus('IDLE');
 *   }
 * }, 30000);
 * 
 * function setStatus(status) {
 *   if (status !== currentStatus) {
 *     currentStatus = status;
 *     // Include status in next heartbeat (no extra round-trip)
 *     // or send STATUS_CHANGE immediately for responsiveness
 *     ws.send(JSON.stringify({ type: 'STATUS_CHANGE', status, resource_id: docId }));
 *   }
 * }
 * 
 * // Heartbeat includes current status every 15s
 * setInterval(() => {
 *   ws.send(JSON.stringify({ type: 'HEARTBEAT', status: currentStatus, resource_id: docId }));
 * }, 15000);
 */

public class IdleStatusHandler {

    /**
     * Server-side: trust the client for idle/active transitions.
     * Client sends STATUS_CHANGE explicitly on transition.
     * Server also detects via heartbeat status field (redundant safety net).
     *
     * Key rule: IDLE does NOT stop heartbeats.
     *   An IDLE user still sends heartbeats every 15s (tab is open).
     *   Heartbeats still refresh TTL.
     *   Only ABSENCE of heartbeats triggers LEAVE (TTL expiry).
     *
     * IDLE != OFFLINE:
     *   IDLE   = "tab is open, user stepped away"   (still shown in viewer list)
     *   LEAVE  = "user closed tab or browser crashed" (removed from viewer list)
     */
    public void handleStatusChange(String sessionId, PresenceStatus newStatus) {
        String key = "session:" + sessionId;
        String current = (String) redis.opsForHash().get(key, "status");
        if (newStatus.name().equals(current)) return;

        redis.opsForHash().put(key, "status", newStatus.name());
        String resourceId = (String) redis.opsForHash().get(key, "resourceId");
        String userId = (String) redis.opsForHash().get(key, "userId");
        eventPublisher.publish("presence:" + resourceId,
            new PresenceEvent(STATUS_CHANGE, resourceId, userId, sessionId, newStatus));
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Ephemeral state** | Redis HASH with TTL per session | Auto-expiry handles crashes; no manual cleanup; ~2 GB total |
| **TTL / Heartbeat** | 30s TTL / 15s interval | Stale presence < 30s; 1.3M heartbeats/sec manageable |
| **Expiry detection** | Proactive polling per server | Distributed; each server scans its own ~40K sessions; no keyspace event bottleneck |
| **Cross-server fan-out** | Redis Sharded Pub/Sub (Redis 7+) | ~1-5ms delivery; O(1) publish; fire-and-forget acceptable (missed update self-corrects on next heartbeat) |
| **Pub/Sub + Kafka** | Both in parallel | Redis: real-time (~5ms); Kafka: durable audit log (~50ms); complementary |
| **Subscription lifecycle** | Subscribe on first local viewer, unsubscribe on last | Prevents subscribing to all 5M channels; ~2K total subscriptions across 500 servers |
| **Popular docs** | Threshold-based batching + aggregate mode | < 50 viewers: individual avatars; 50-200: 100ms batching; > 200: viewer count only |
| **Server crash reconnection** | TTL grace period (30s) | Clients reconnect within 5-10s; session HASH still alive; zero LEAVE/JOIN storm |
| **Multi-device deduplication** | user_sessions:{userId}:{resId} SET | User appears once per doc; effective status = max(ACTIVE, IDLE) across all sessions |
| **Initial viewer list** | REST GET before WebSocket connect | Page renders immediately; WebSocket connects in parallel; no blank-screen wait |

## Interview Talking Points

1. **"Heartbeat + TTL contract: the fundamental presence guarantee"** — Session TTL = 30s. Clients send heartbeats every 15s (2x per TTL). If heartbeat stops: TTL expires → LEAVE. This handles all failure modes: crash, network drop, tab kill. No server-side session management needed.

2. **"Proactive expiry scanning vs. Redis Keyspace Notifications"** — At 20M sessions, keyspace notifications flood a single consumer (667K/min). Instead, each Presence Server owns its sessions and runs a background scanner every 10s. Distributed, no SPOF, deterministic latency.

3. **"Redis 7+ Sharded Pub/Sub for cross-server fan-out"** — 500 servers, each holding 20K WS connections. Classic Redis Pub/Sub breaks in cluster mode (PUBLISH goes to one node). Redis 7+ SPUBLISH/SSUBSCRIBE routes correctly. Subscribe only to channels for docs with local viewers → ~2K subscriptions vs. 5M total channels.

4. **"Subscription lifecycle is memory critical"** — Each server subscribes to a channel when its FIRST viewer joins a doc, unsubscribes when its LAST viewer leaves. Without this, 500 servers subscribed to all 5M channels = 2.5B subscription registrations.

5. **"TTL grace period eliminates server restart storms"** — On server crash, clients reconnect within 5-10s. Session HASH still alive (30s TTL not expired). Client sends REJOIN → server refreshes TTL, no events. Other viewers see nothing. Without this, every deploy triggers LEAVE/rejoin flicker.

6. **"Popular doc problem is self-limiting via consistent hash distribution"** — 1,000 viewers distributed across ~10 servers = ~100 local viewers per server. Each server pushes 100 messages per event, not 999. Burst joins batched over 100ms windows → single merged message per server.

7. **"REST + WebSocket dual approach"** — REST serves initial viewer list (page renders immediately). WebSocket connects in parallel for real-time updates. Decouples initial load from WS handshake latency.

8. **"Multi-device deduplication via session SET per user-resource pair"** — user_sessions:{userId}:{resourceId} tracks all active sessions. JOIN/LEAVE events only broadcast on 0→1 and 1→0 transitions. Effective status = ACTIVE if any session is ACTIVE.

---

## Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Presence state** | Redis HASH + SET + TTL | DynamoDB with TTL | Serverless, no Redis ops; slightly higher latency |
| **Cross-server fan-out** | Redis Sharded Pub/Sub | Kafka | If presence updates must be durable/replayable; accept ~50ms latency |
| **WebSocket routing** | Consistent hash by resource_id | Any server + full-mesh | Full-mesh: simpler routing but O(N²) server connections |
| **Presence Server** | Go (high WS concurrency) | Node.js | Node.js: excellent WS performance; Go: better CPU efficiency |
| **Idle detection** | Client-side JS timers | Server-side heartbeat analysis | Server-side: less trusting of client; harder to detect tab visibility |
| **Popular doc fan-out** | Threshold-based batching | Dedicated fan-out service | Dedicated service: cleaner separation; needed at extreme scale (10K+ viewers) |
| **Heartbeat transport** | WebSocket messages | HTTP long-poll / SSE | HTTP: simpler ops (no sticky WS); higher latency (~30s poll interval) |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 7 topics (choose 2-3 based on interviewer interest)

---

**Related Problems**:
- **Slack "who is online?"**: Same pattern; Slack uses "active in the last 30 minutes" threshold
- **Google Docs live cursors**: Presence layer + cursor position broadcast (presence is the foundation)
- **Discord user status**: Global presence (across all servers/channels) vs. doc-scoped presence here
- **Chat system**: WebSocket infrastructure identical; messages replace presence events
- **Notification system**: Fan-out pattern same (Pub/Sub → WebSocket push)
- **Feature flag real-time updates**: Same architecture (SUBSCRIBE to flag-change channels)
