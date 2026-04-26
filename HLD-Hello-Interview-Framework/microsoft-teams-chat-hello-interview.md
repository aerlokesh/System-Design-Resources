# Design Microsoft Teams (Chat Service) — Hello Interview Framework

> **Question**: Design the chat functionality of Microsoft Teams to support millions of users sending messages in real-time across 1:1 chats, group chats, and channel conversations with rich features like threads, reactions, @mentions, and file sharing.
>
> **Asked at**: Microsoft, Amazon, Google, Meta
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
1. **Organization & Team Management**: Users belong to an organization (Azure AD tenant). Organizations contain teams. Teams have channels (General, custom).
2. **Channel Messaging**: Post messages to team channels. All channel members see messages in real-time. Support threaded replies under channel messages.
3. **1:1 and Group Chat**: Direct messages between two users. Group chats (up to 250 people). Real-time delivery.
4. **Real-Time Messaging**: Messages appear instantly (<200ms latency). Typing indicators. Read receipts (seen by X people).
5. **@Mentions**: Mention specific users, channels, or teams. Trigger notifications for mentioned entities.
6. **Message History & Sync**: Persist all messages. Paginated history load. Multi-device sync (desktop, mobile, web).
7. **Presence**: Online/Away/Busy/Do Not Disturb/Offline status synced with Outlook calendar.
8. **Notifications**: Push/toast notifications for new messages, mentions, replies. Per-channel notification settings (all activity, mentions only, off).
9. **Rich Media**: Inline images, GIFs, code snippets, adaptive cards. File attachments (backed by SharePoint/OneDrive).

#### Nice to Have (P1)
- Message editing and deletion
- Emoji reactions
- Pinned messages per channel
- Priority notifications (urgent messages)
- Message translation (inline)
- Scheduled messages
- Loop components (live collaborative components in chat)
- Message forwarding
- Bookmarks / saved messages
- Search across messages (full-text)

#### Below the Line (Out of Scope)
- Video/audio calling (separate design)
- Meeting scheduling
- Tabs/apps/bots platform
- Admin compliance & eDiscovery
- Guest access cross-tenant

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **DAU** | 300 million | Teams' reported active user base |
| **Concurrent connections** | 150 million WebSocket connections | ~50% of DAU online at peak |
| **Messages/day** | 5 billion (~58K msgs/sec avg, ~250K peak) | ~17 messages per active user per day |
| **Message delivery latency** | < 200ms (p95) | Real-time collaboration feel |
| **Message history load** | < 300ms for 50 messages | Fast scroll-back |
| **Search latency** | < 500ms | Responsive search experience |
| **Availability** | 99.99% | Business-critical enterprise tool |
| **Consistency** | Strong per conversation (message ordering) | Messages must appear in order |
| **Durability** | Zero message loss | Enterprise compliance requirements |
| **Multi-tenant isolation** | Full data isolation per Azure AD tenant | Enterprise security requirement |

### Capacity Estimation

```
Users & Organizations:
  Total users: 500M registered
  DAU: 300M
  Organizations: 10M
  Average org size: 50 users (range: 5 to 500K)
  Teams per org: 20 average
  Channels per team: 5 average
  
Messages:
  Messages/day: 5B
  Messages/sec (avg): 5B / 86,400 ≈ 58K QPS
  Messages/sec (peak 4x): ~250K QPS
  Average message size: 1 KB (text + metadata + formatting)
  
Storage:
  Daily message storage: 5B × 1 KB = 5 TB/day
  Yearly: 1.8 PB/year
  5 years: ~9 PB (messages only)
  
  File attachments: 500M/day × 1 MB avg = 500 TB/day (stored in SharePoint/OneDrive)
  
WebSocket Connections:
  Concurrent: 150M at peak
  Servers needed: 150M / 100K per server ≈ 1,500 gateway servers
  With 2x headroom: ~3,000 gateway servers

Bandwidth:
  Incoming (messages): 5 TB/day ÷ 86,400 ≈ 60 MB/s
  Outgoing (fan-out):
    Average channel: 30 members → 30x fan-out
    Average 1:1/group: 5 members → 5x fan-out
    Blended fan-out: ~15x
    Outgoing: ~900 MB/s peak
```

---

## 2️⃣ Core Entities

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│  Tenant      │────▶│  Team         │────▶│  Channel       │
│  (Org)       │     │               │     │                │
│ tenantId     │     │ teamId        │     │ channelId      │
│ domain       │     │ tenantId      │     │ teamId         │
│ displayName  │     │ displayName   │     │ name           │
│ settings     │     │ description   │     │ type (standard/│
└─────────────┘     │ visibility    │     │  private/shared)│
                     └──────────────┘     │ memberCount    │
                                           └───────────────┘
                                                   │
┌─────────────┐     ┌──────────────┐              ▼
│  User        │────▶│  Membership  │     ┌───────────────┐
│              │     │               │     │  Message       │
│ userId       │     │ userId        │     │                │
│ tenantId     │     │ entityId      │     │ messageId      │
│ displayName  │     │ entityType    │     │ conversationId │
│ email        │     │ role (owner/  │     │ senderId       │
│ presence     │     │  member/guest)│     │ content (HTML) │
│ avatar       │     │ muted         │     │ parentMessageId│
│ lastSeen     │     │ pinned        │     │ type (text/    │
└─────────────┘     └──────────────┘     │  card/system)  │
                                           │ mentions[]     │
┌─────────────┐     ┌──────────────┐     │ attachments[]  │
│  Conversation│     │  Attachment   │     │ reactions[]    │
│  (Chat)      │     │               │     │ editedAt       │
│ chatId       │     │ attachmentId  │     │ createdAt      │
│ type (1:1/   │     │ messageId     │     │ deletedAt      │
│  group)      │     │ fileName      │     └───────────────┘
│ members[]    │     │ contentType   │
│ topic        │     │ driveItemId   │     ┌───────────────┐
│ createdAt    │     │ url           │     │  Thread        │
│ lastMessage  │     │ size          │     │                │
└─────────────┘     └──────────────┘     │ threadId       │
                                           │ parentMsgId    │
                                           │ channelId      │
                                           │ replyCount     │
                                           │ lastReplyAt    │
                                           └───────────────┘
```

**Key Relationships:**
- A **Tenant** contains many **Teams**, each with multiple **Channels**
- **Users** belong to a Tenant and can be members of Teams/Channels/Chats
- **Messages** live in a **Conversation** (channel or chat), optionally in a **Thread**
- **Attachments** reference SharePoint/OneDrive items via `driveItemId`

---

## 3️⃣ API Design

### Send Message
```
POST /api/v1/chats/{conversationId}/messages
Authorization: Bearer <token>

Request:
{
  "content": {
    "contentType": "html",
    "body": "<p>Hey <at id='user123'>@Alice</at>, check this out!</p>"
  },
  "parentMessageId": "msg_456",        // optional — thread reply
  "attachments": [
    {
      "contentType": "reference",
      "contentUrl": "https://graph.microsoft.com/v1.0/drives/driveId/items/itemId",
      "name": "design-doc.pdf"
    }
  ],
  "mentions": [
    { "id": "user123", "mentionText": "Alice", "type": "user" }
  ],
  "importance": "normal"                // normal | urgent
}

Response: 201 Created
{
  "id": "msg_789",
  "conversationId": "conv_123",
  "from": { "userId": "user_sender", "displayName": "Bob" },
  "createdDateTime": "2025-01-15T10:30:00Z",
  "body": { "contentType": "html", "content": "..." },
  "parentMessageId": "msg_456",
  "mentions": [...],
  "attachments": [...],
  "reactions": []
}
```

### Get Message History
```
GET /api/v1/chats/{conversationId}/messages?$top=50&$skip=0&$orderBy=createdDateTime desc
Authorization: Bearer <token>

Response: 200 OK
{
  "value": [ ...messages... ],
  "@odata.nextLink": "/api/v1/chats/{id}/messages?$skipToken=xxx"
}
```

### Update Presence
```
PUT /api/v1/users/{userId}/presence
Authorization: Bearer <token>

Request:
{
  "availability": "Busy",          // Available | Busy | DoNotDisturb | Away | Offline
  "activity": "InACall"            // InACall | InAMeeting | Presenting | etc.
}
```

### Create Chat (1:1 or Group)
```
POST /api/v1/chats
Authorization: Bearer <token>

Request:
{
  "chatType": "group",
  "topic": "Project Alpha Discussion",
  "members": [
    { "userId": "user_1", "roles": ["owner"] },
    { "userId": "user_2", "roles": ["member"] },
    { "userId": "user_3", "roles": ["member"] }
  ]
}
```

### Subscribe to Real-Time Events (WebSocket)
```
CONNECT wss://teams-rt.microsoft.com/v1/connect
Authorization: Bearer <token>

// Server pushes:
{
  "type": "message",
  "resource": "/chats/conv_123/messages/msg_789",
  "data": { ...message payload... }
}

{
  "type": "typing",
  "resource": "/chats/conv_123",
  "data": { "userId": "user_456", "isTyping": true }
}

{
  "type": "presence",
  "data": { "userId": "user_789", "availability": "Away" }
}
```

---

## 4️⃣ Data Flow

### Sending a Message (Channel)

```
User A types message → Teams Client
        │
        ▼
   API Gateway (Azure Front Door)
        │
        ▼
   Chat Service (validates, enriches)
        │
        ├──▶ Write to Messages DB (Cosmos DB — partitioned by conversationId)
        │
        ├──▶ Publish to Message Bus (Azure Service Bus / Kafka)
        │         │
        │         ├──▶ Fan-out Service → resolves channel members
        │         │         │
        │         │         ├──▶ Notification Router → for each member:
        │         │         │       │
        │         │         │       ├── Online? → push via WebSocket (Signaling Service)
        │         │         │       │
        │         │         │       └── Offline? → queue notification
        │         │         │                  │
        │         │         │                  ├── Push notification (APNS/FCM/WNS)
        │         │         │                  └── Badge count update
        │         │         │
        │         │         └──▶ Update conversation metadata (lastMessage, unreadCount)
        │         │
        │         ├──▶ Search Indexer → index message in Elasticsearch
        │         │
        │         └──▶ Compliance Pipeline → eDiscovery, retention policies
        │
        └──▶ Return 201 to client (optimistic — message persisted)

```

### Reading Message History

```
User opens channel → Teams Client
        │
        ▼
   API Gateway
        │
        ▼
   Chat Service
        │
        ├── 1. Check membership (user in channel?)
        │
        ├── 2. Query Messages DB (Cosmos DB)
        │       → partitionKey = conversationId
        │       → ORDER BY createdAt DESC LIMIT 50
        │
        ├── 3. Enrich messages (user profiles, attachment metadata)
        │
        └── 4. Return paginated response
                │
                └── Client renders & marks as read
                        │
                        └── POST /api/v1/chats/{id}/messages/readReceipt
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENTS                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ Desktop  │  │  Mobile  │  │   Web    │  │   Bot    │       │
│  │ (Electron)│  │(iOS/And) │  │ (React) │  │Framework │       │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘       │
│       └──────────────┴──────────────┴──────────────┘             │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS + WebSocket
                            ▼
┌───────────────────────────────────────────────────────────────────┐
│                    EDGE / GATEWAY LAYER                            │
│  ┌────────────────────┐  ┌──────────────────────┐                │
│  │  Azure Front Door  │  │  API Gateway          │                │
│  │  (Global LB + CDN) │  │  (Auth, Rate Limit,   │                │
│  └────────┬───────────┘  │   Routing, Throttle)  │                │
│           └──────────────┴──────────┬─────────────┘                │
└─────────────────────────────────────┬─────────────────────────────┘
                                      │
          ┌───────────────────────────┼───────────────────────┐
          ▼                           ▼                       ▼
┌─────────────────┐  ┌──────────────────────┐  ┌──────────────────┐
│  Chat Service   │  │  Signaling Service   │  │  Presence Service│
│  (Stateless)    │  │  (WebSocket Gateway) │  │  (Stateless)     │
│                 │  │                      │  │                  │
│ - Send message  │  │ - Manage WS conns    │  │ - Track online/  │
│ - Get history   │  │ - Route real-time    │  │   away/busy      │
│ - CRUD chats    │  │   events to clients  │  │ - Heartbeat      │
│ - Thread mgmt   │  │ - Typing indicators  │  │ - Calendar sync  │
│ - Read receipts │  │ - 150M concurrent    │  │                  │
└────────┬────────┘  └──────────┬───────────┘  └────────┬─────────┘
         │                      │                        │
         ▼                      ▼                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MESSAGE BUS / EVENT BACKBONE                  │
│  ┌───────────────────────────────────────────────────────┐      │
│  │  Azure Service Bus / Kafka Cluster                     │      │
│  │  Topics: messages, presence, notifications, compliance │      │
│  └───────────────────────────────────────────────────────┘      │
└──────┬──────────────┬──────────────────┬───────────────┬────────┘
       │              │                  │               │
       ▼              ▼                  ▼               ▼
┌────────────┐ ┌─────────────┐ ┌──────────────┐ ┌──────────────┐
│ Fan-out    │ │ Notification│ │ Search       │ │ Compliance   │
│ Service    │ │ Service     │ │ Indexer      │ │ Service      │
│            │ │             │ │              │ │              │
│ Resolve    │ │ Push via    │ │ Index to     │ │ Retention,   │
│ members &  │ │ APNS/FCM/  │ │ Elasticsearch│ │ eDiscovery,  │
│ route to   │ │ WNS/Email  │ │ per tenant   │ │ DLP          │
│ signaling  │ │             │ │              │ │              │
└────────────┘ └─────────────┘ └──────────────┘ └──────────────┘
                                      │
┌─────────────────────────────────────────────────────────────────┐
│                        DATA LAYER                                │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐   │
│  │ Azure Cosmos │  │ Azure Redis  │  │ Elasticsearch       │   │
│  │ DB           │  │ Cache        │  │ Cluster             │   │
│  │              │  │              │  │                     │   │
│  │ Messages     │  │ Presence     │  │ Message search      │   │
│  │ (partition:  │  │ Session      │  │ (per-tenant index)  │   │
│  │  convId)     │  │ Unread counts│  │                     │   │
│  │              │  │ Conn mapping │  │                     │   │
│  │ Conversations│  │ Rate limit   │  │                     │   │
│  │ Members      │  │ counters     │  │                     │   │
│  └──────────────┘  └──────────────┘  └─────────────────────┘   │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐                             │
│  │ SharePoint / │  │ Azure Blob   │                             │
│  │ OneDrive     │  │ Storage      │                             │
│  │              │  │              │                             │
│  │ File attach- │  │ Media (imgs, │                             │
│  │ ments, docs  │  │ thumbnails)  │                             │
│  └──────────────┘  └──────────────┘                             │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Scale |
|-----------|---------------|-------|
| **Azure Front Door** | Global load balancing, TLS termination, DDoS protection | Global edge |
| **API Gateway** | Auth (Azure AD tokens), rate limiting, request routing | Regional |
| **Chat Service** | Message CRUD, conversation management, read receipts | ~500 instances |
| **Signaling Service** | WebSocket connection management, real-time event push | ~3,000 instances |
| **Presence Service** | User availability tracking, heartbeat, calendar integration | ~200 instances |
| **Fan-out Service** | Resolve conversation members, route to correct signaling nodes | ~300 instances |
| **Notification Service** | Push notifications via APNS/FCM/WNS, email digest | ~200 instances |
| **Cosmos DB** | Message storage, conversations, memberships | Multi-region, auto-scale |
| **Redis Cache** | Presence, session, connection mapping, unread counts | Clustered, ~50 nodes |
| **Elasticsearch** | Full-text message search per tenant | ~100 nodes |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Real-Time Message Delivery & Fan-out

**Problem**: When a user sends a message in a channel with 10,000 members, how do we deliver it to all online members in <200ms?

**Solution: Hybrid Push with Connection Registry**

```
Message arrives at Chat Service
        │
        ▼
Persist to Cosmos DB (async ack pattern)
        │
        ▼
Publish to Kafka topic: "messages.{tenantId}"
        │
        ▼
Fan-out Service consumes:
  1. Lookup channel members from Members DB / cache
  2. For each member, lookup Connection Registry (Redis):
     Key: "conn:{userId}" → { serverId: "ws-node-42", connectionId: "abc" }
  3. Group members by WebSocket server
  4. Send batch delivery request to each WS server
        │
        ▼
Signaling Service (ws-node-42):
  - Lookup local connection by connectionId
  - Push message via WebSocket frame
  - If connection stale → mark for cleanup

For offline users:
  - Enqueue in Notification Service
  - Increment unread counter in Redis
  - Send push notification (debounced — 5s aggregation window)
```

**Fan-out Optimization for Large Channels:**
- **Small channels (<100 members)**: Direct fan-out — send to each member individually
- **Medium channels (100–10K)**: Batched fan-out — group by WS server, batch deliver
- **Large channels (10K+)**: Lazy fan-out — publish to a "channel topic"; WS servers subscribe on behalf of connected members. Each WS server maintains a local subscription map.

**Connection Registry Design (Redis):**
```
# User → connection mapping
HSET conn:user123 serverId "ws-node-42" connId "abc" tenantId "contoso" lastSeen 1705300000

# Server → users mapping (for batch delivery)
SADD server:ws-node-42:users user123 user456 user789

# Channel subscription (for large channels)
SADD channel:ch_bigall:servers ws-node-42 ws-node-55 ws-node-78
```

**Consistency guarantee**: Messages are written to Cosmos DB before fan-out. If fan-out fails, clients pull on reconnect (last-message cursor sync).

---

### Deep Dive 2: Multi-Tenant Data Isolation & Cosmos DB Partitioning

**Problem**: Teams serves millions of organizations. How do we ensure data isolation, achieve low latency, and scale storage?

**Solution: Tenant-Aware Partitioning in Cosmos DB**

**Partition Strategy:**
```
Container: Messages
  Partition Key: /conversationId
  
  Why conversationId?
  - All queries are scoped to a conversation (get history, post message)
  - Conversation is bounded (~50K messages typical)
  - Avoids hot partitions (tenantId would create huge partitions)
  
  Document structure:
  {
    "id": "msg_789",
    "conversationId": "conv_123",      // partition key
    "tenantId": "contoso.com",         // for cross-partition tenant queries
    "senderId": "user_456",
    "content": "<p>Hello team!</p>",
    "createdAt": "2025-01-15T10:30:00Z",
    "parentMessageId": null,
    "type": "message",
    "ttl": -1                          // no expiry for paid tenants
  }

Container: Conversations
  Partition Key: /participantId (for 1:1/group chats)
                 /teamId (for channel conversations)
  
Container: Presence
  → Redis (not Cosmos — ephemeral data, high update frequency)
```

**Tenant Isolation:**
1. **Logical isolation**: Every document tagged with `tenantId`. Query filters always include `tenantId`.
2. **Authorization**: Azure AD token contains `tenantId` claim. Service layer enforces tenant boundary at every API call.
3. **Encryption**: Per-tenant encryption keys (Azure Key Vault). Customer-managed keys for enterprise plans.
4. **Compliance**: Data residency — tenant data pinned to specific Azure region based on tenant configuration.

**Multi-Region Replication:**
- Cosmos DB multi-region writes enabled for large tenants
- Read replicas in 5+ regions for global enterprises
- Conflict resolution: Last-Writer-Wins (LWW) on `createdAt` for messages (append-only, no conflicts)

---

### Deep Dive 3: Presence System at Scale

**Problem**: 300M DAU need real-time presence updates. A user going online should be reflected to their colleagues within 5 seconds. How to handle this at scale without overwhelming the system?

**Solution: Tiered Presence with Subscription-Based Updates**

```
Architecture:
  
  User heartbeat (every 30s) → Presence Service → Redis
                                                      │
  Calendar webhook → Presence Service → Redis ────────┤
                                                      │
                                                      ▼
                                              Presence Change Detected?
                                                      │
                                              Yes     │      No
                                              ▼       │      └── (discard)
                                      Publish to Kafka topic: "presence"
                                              │
                                              ▼
                                      Presence Fan-out Service
                                              │
                                      Who subscribed to this user's presence?
                                              │
                                              ▼
                                      Fetch subscriber list (Redis)
                                              │
                                      Push update via Signaling Service
```

**Subscription Model (not broadcast):**
- When User A opens a chat/channel view, client sends: `SUBSCRIBE presence [user_B, user_C, ...]`
- Server tracks: `presenceSub:user_B → {user_A, user_D, user_E}` (who wants to know about B)
- When B's presence changes → only notify subscribers (not entire org)
- Unsubscribe when user navigates away or disconnects

**Presence State Machine:**
```
Available ←→ Away (5 min idle timer, or manual)
    ↕             ↕
   Busy    Do Not Disturb
    ↕             ↕
  Offline (disconnect / explicit)

Calendar integration:
  - Meeting in progress → auto-set "In a meeting" (Busy)
  - Focus time → auto-set "Do Not Disturb"
  - Out of office → auto-set "Away"
```

**Redis Structure:**
```
# Current presence
HSET presence:user123 availability "Available" activity "Active" lastSeen 1705300000 source "desktop"

# Presence subscribers
SADD presenceSub:user123 user_A user_D user_E

# TTL: if no heartbeat in 60s → auto-transition to Offline
```

**Scale optimization:**
- Batch presence updates: aggregate changes over 2-second windows before fan-out
- Presence only tracked for users in your organization (tenant scoped)
- Mobile clients: reduced heartbeat frequency (60s) to save battery

---

### Deep Dive 4: Message Ordering & Consistency

**Problem**: In a high-traffic channel, multiple users send messages simultaneously from different regions. How do we ensure everyone sees messages in the same order?

**Solution: Server-Assigned Ordering with Cosmos DB**

**Ordering Strategy:**
```
1. Client sends message → Chat Service
2. Chat Service generates:
   - messageId (ULID — lexicographically sortable, time-based)
   - serverTimestamp (UTC, high-precision)
   - sequenceNumber (per-conversation, monotonically increasing)
3. Write to Cosmos DB with conversationId as partition key
   → Within a partition, Cosmos DB guarantees order of operations
4. Return messageId + serverTimestamp to client
5. Fan-out includes sequenceNumber for clients to detect gaps
```

**Handling Late Messages:**
```
Client maintains: lastSeenSequence per conversation

On receiving message via WebSocket:
  if msg.sequenceNumber == lastSeenSequence + 1:
    append to UI ✓
  elif msg.sequenceNumber > lastSeenSequence + 1:
    GAP detected — fetch missing messages from server
    then apply all in order
  elif msg.sequenceNumber <= lastSeenSequence:
    DUPLICATE — ignore (idempotent)
```

**Multi-Region Conflict Resolution:**
- Cosmos DB with single-region write for message containers (strong consistency within partition)
- If latency matters more: multi-region write with LWW on `serverTimestamp`, plus client-side reordering
- For Teams: prefer single write region per conversation → guarantees total order

**Sequence Number Generation:**
```
Per-conversation counter in Redis:
  INCR seq:conv_123 → returns next sequence number
  
Backed by Cosmos DB transactional batch:
  - Increment conversation.lastSequence
  - Insert message with that sequence
  → Atomic within same partition key
```

---

### Deep Dive 5: Notification Strategy & Deduplication

**Problem**: A user is in 50 channels, gets mentioned in 3 channels within 10 seconds, and has desktop + mobile + web connected. How do we avoid notification spam while ensuring nothing is missed?

**Solution: Smart Notification Pipeline with Device-Aware Routing**

```
Message with @mention → Notification Service
        │
        ▼
  1. Determine notification type:
     - @user mention → always notify
     - @channel/@team → notify based on user preference
     - Regular message → notify based on channel setting
        │
        ▼
  2. Check user's notification preferences:
     - Channel muted? → skip
     - Do Not Disturb? → queue for later (unless urgent)
     - Quiet hours? → queue for digest
        │
        ▼
  3. Device routing:
     ┌─────────────────────────────────────────┐
     │ If user has active WebSocket connection: │
     │   → Deliver via WebSocket (toast in-app) │
     │   → Suppress push notification           │
     │     (wait 3s — if no read receipt,       │
     │      then send push)                     │
     ├─────────────────────────────────────────┤
     │ If user is offline on all devices:       │
     │   → Send push notification               │
     │   → Badge count increment                │
     │   → Queue for email digest (if enabled)  │
     └─────────────────────────────────────────┘
        │
        ▼
  4. Aggregation & dedup:
     - Multiple messages in same channel within 30s → batch into one notification
     - "Alice and 2 others sent messages in #general"
     - Dedup key: (userId, conversationId, 30s window)
```

**Push Notification Flow:**
```
Notification Service
    │
    ├── iOS → Apple Push Notification Service (APNS)
    ├── Android → Firebase Cloud Messaging (FCM)  
    ├── Windows → Windows Notification Service (WNS)
    └── Web → Web Push (VAPID)
    
Device token registry (Cosmos DB):
  {
    "userId": "user_123",
    "devices": [
      { "platform": "ios", "token": "apns_token_xxx", "lastActive": "..." },
      { "platform": "windows", "token": "wns_channel_xxx", "lastActive": "..." }
    ]
  }
```

**Priority Notifications (Urgent Messages):**
- Sender marks message as "Urgent" → bypasses Do Not Disturb
- Repeated push notifications every 2 minutes for 20 minutes until acknowledged
- Audio alert on all connected devices

---

### Deep Dive 6: Search Architecture

**Problem**: Users need to search across all messages they have access to. Search must respect permissions (a user should only find messages from channels/chats they're a member of).

**Solution: Elasticsearch with Security Trimming**

```
Indexing Pipeline:
  Message created → Kafka → Search Indexer Service
        │
        ├── Extract text content (strip HTML)
        ├── Extract metadata (sender, timestamp, conversationId)
        ├── Resolve access list (channel members at index time)
        └── Index into Elasticsearch
            Index name: teams-messages-{tenantId}-{YYYY-MM}
            
  Document:
  {
    "messageId": "msg_789",
    "conversationId": "conv_123",
    "tenantId": "contoso",
    "senderId": "user_456",
    "senderName": "Bob Smith",
    "body": "Hey Alice check out the new design mockups",
    "timestamp": "2025-01-15T10:30:00Z",
    "accessList": ["user_123", "user_456", "user_789"],  // member IDs
    "channelName": "design-team",
    "chatType": "channel",
    "hasAttachment": true,
    "mentions": ["user_123"]
  }

Query with security trimming:
  POST /teams-messages-contoso-*/_search
  {
    "query": {
      "bool": {
        "must": [
          { "match": { "body": "design mockups" } }
        ],
        "filter": [
          { "term": { "tenantId": "contoso" } },
          { "terms": { "accessList": ["user_123"] } }  // requesting user
        ]
      }
    },
    "sort": [{ "timestamp": "desc" }],
    "size": 20
  }
```

**Access List Updates:**
- When a user joins/leaves a channel → background job updates `accessList` on all messages in that channel
- For large channels: use "channel membership" approach — at query time, resolve user's channels and filter by `conversationId IN [user's channels]` instead of per-message `accessList`

**Index Management:**
- Monthly indices for easy lifecycle management
- Hot-warm-cold architecture: last 3 months on SSD, older on HDD, >1 year archived
- Per-tenant indices for large enterprises, shared indices for small tenants
