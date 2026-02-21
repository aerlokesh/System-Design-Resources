# Design Slack — Hello Interview Framework

> **Question**: Design a real-time team messaging platform like Slack that supports channels, direct messages, threads, presence, file sharing, and search across a workspace — serving millions of concurrent users.
>
> **Asked at**: Amazon, Google, Meta, Microsoft, Salesforce
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
1. **Workspace Management**: Users belong to a workspace (organization). Workspaces are isolated — users in one workspace can't see another's data.
2. **Channels**: Create public/private channels within a workspace. Users join/leave channels. Post messages to channels. All channel members see messages in real-time.
3. **Direct Messages (DMs)**: 1:1 and group DMs (up to 9 people). Real-time delivery.
4. **Threaded Replies**: Reply to a specific message, creating a thread. Thread replies don't clutter the main channel feed (unless explicitly posted to channel too).
5. **Real-Time Messaging**: Messages appear instantly for all online members (< 200ms latency). Typing indicators.
6. **Presence**: Online/away/offline status per user. Updated in real-time to other workspace members.
7. **Message History**: Scroll back through unlimited history. Load older messages on demand (pagination).
8. **Notifications**: Push notifications for mentions (@user, @channel), DMs, and threads the user is following. Notification preferences per channel.
9. **Search**: Full-text search across all messages the user has access to. Filter by channel, user, date range.

#### Nice to Have (P1)
- File/image sharing (upload, preview, download)
- Emoji reactions on messages
- Message editing and deletion (with "edited" indicator)
- Pinned messages per channel
- Bookmarked messages
- Slack Connect (cross-workspace channels)
- Huddles (real-time audio/video calls)
- Workflows / Slack bots / integrations
- Scheduled messages
- Channel topics and descriptions
- User profiles with status messages

#### Below the Line (Out of Scope)
- Slack app marketplace / bot development platform
- Video conferencing (Huddles)
- Slack Connect (cross-organization)
- Admin console / compliance / audit logs

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **DAU** | 20 million | Enterprise messaging at Slack's scale |
| **Concurrent connections** | 10 million WebSocket connections | ~50% of DAU online at peak |
| **Messages/day** | 2 billion (~23K msgs/sec avg, ~100K peak) | 100 messages per active user per day |
| **Message delivery latency** | < 200ms (p95) | Real-time feel — must be instant |
| **Message history load** | < 300ms for 50 messages | Scrolling back through history |
| **Search latency** | < 500ms | Must feel responsive |
| **Availability** | 99.99% for messaging | Business-critical communication tool |
| **Consistency** | Strong per channel (message ordering) | Messages must appear in order within a channel |
| **Durability** | Zero message loss | Enterprise customers depend on message history |
| **Retention** | Unlimited (free: 90 days, paid: unlimited) | Compliance and knowledge base |

### Capacity Estimation

```
Users & Workspaces:
  Total users: 50M registered
  DAU: 20M
  Workspaces: 2M
  Average workspace size: 25 users (range: 5 to 100K)
  Channels per workspace: 50 average (range: 10 to 10K)
  
Messages:
  Messages/day: 2B
  Messages/sec (avg): 2B / 86,400 ≈ 23K QPS
  Messages/sec (peak 3x): ~70K QPS
  Average message size: 500 bytes (text + metadata)
  
Storage:
  Daily message storage: 2B × 500 bytes = 1 TB/day
  Yearly: 365 TB/year
  5 years: ~1.8 PB (messages only)
  
  File uploads: 200M/day × 500 KB avg = 100 TB/day
  Yearly files: 36.5 PB/year (stored in S3, lifecycle to Glacier)
  
WebSocket Connections:
  Concurrent: 10M at peak
  Servers needed: 10M / 65K per server ≈ 155 gateway servers
  With 2x headroom: ~300 gateway servers

Bandwidth:
  Incoming (messages): 1 TB/day ÷ 86,400 ≈ 12 MB/s
  Outgoing (fan-out to channel members):
    Average channel: 25 members
    2B msgs × 25 deliveries × 500 bytes = 25 TB/day outgoing
    25 TB / 86,400 ≈ 290 MB/s outgoing
```

---

## 2️⃣ Core Entities

### Entity Relationships
```
Workspace ──1:N──→ User (via membership)
Workspace ──1:N──→ Channel
Channel   ──M:N──→ User (via channel_members)
Channel   ──1:N──→ Message
Message   ──1:N──→ Thread Reply (message with parent_id)
User      ──M:N──→ DM Conversation (via dm_members)
DM Conv.  ──1:N──→ Message
Message   ──1:N──→ Reaction
Message   ──1:N──→ File Attachment
```

### Entity 1: Workspace
```
Workspaces Table (MySQL):
- workspace_id (PK, UUID)
- name (varchar 100, e.g., "Acme Corp")
- domain (unique, e.g., "acme-corp")
- plan (enum: FREE, PRO, BUSINESS, ENTERPRISE)
- icon_url
- created_at (timestamp)
- max_members (int)
- message_retention_days (int, null = unlimited)
```

### Entity 2: User
```
Users Table (MySQL):
- user_id (PK, UUID)
- email (unique, indexed)
- display_name (varchar 100)
- avatar_url
- status_text (varchar 100, e.g., "In a meeting")
- status_emoji (varchar 10, e.g., "📅")
- timezone (varchar 50)
- created_at (timestamp)

Workspace_Members Table (MySQL):
- workspace_id (PK, FK)
- user_id (PK, FK)
- role (enum: OWNER, ADMIN, MEMBER, GUEST)
- joined_at (timestamp)
- deactivated_at (timestamp, nullable)
```

### Entity 3: Channel
```
Channels Table (MySQL):
- channel_id (PK, UUID)
- workspace_id (FK, indexed)
- name (varchar 80, unique per workspace)
- type (enum: PUBLIC, PRIVATE, DM)
- topic (varchar 250)
- description (text)
- created_by (FK user_id)
- created_at (timestamp)
- is_archived (boolean)
- member_count (int)

Channel_Members Table (MySQL):
- channel_id (PK, FK)
- user_id (PK, FK)
- role (enum: ADMIN, MEMBER)
- joined_at (timestamp)
- muted_until (timestamp, nullable)
- notification_pref (enum: ALL, MENTIONS, NONE)
- last_read_message_id (UUID — tracks unread position)
```

### Entity 4: Message
```
Messages Table (Cassandra):
- channel_id (partition key)
- message_id (clustering key, timeuuid DESC)
- user_id (sender)
- content (text, max 40K chars)
- message_type (enum: TEXT, FILE, SYSTEM, BOT)
- parent_message_id (nullable — for thread replies)
- thread_reply_count (int)
- thread_latest_reply_at (timestamp)
- is_edited (boolean)
- edited_at (timestamp, nullable)
- is_deleted (boolean)
- reactions (map<text, set<uuid>>)  -- emoji → set of user_ids
- file_ids (list<uuid>)
- mentioned_user_ids (set<uuid>)
- mentioned_channel_ids (set<uuid>)
- has_at_channel (boolean)
- created_at (timestamp)

PRIMARY KEY ((channel_id), message_id)
WITH CLUSTERING ORDER BY (message_id DESC)
```

### Entity 5: File
```
Files Table (MySQL):
- file_id (PK, UUID)
- workspace_id (FK)
- uploaded_by (FK user_id)
- channel_id (FK)
- message_id (FK)
- filename (varchar 255)
- mime_type (varchar 100)
- size_bytes (bigint)
- s3_key (varchar 500)
- thumbnail_url (varchar 500)
- created_at (timestamp)
```

### Entity 6: Thread (Virtual — implemented via Message.parent_message_id)
```
A thread is not a separate entity — it's a set of messages where parent_message_id = the root message's ID.

Thread queries:
  "Get thread replies":
    SELECT * FROM messages WHERE channel_id = ? AND parent_message_id = ? ORDER BY message_id ASC

  The root message tracks:
    thread_reply_count (incremented on each reply)
    thread_latest_reply_at (updated on each reply)
```

### Entity 7: Notification Preference (per channel per user)
```
Already embedded in Channel_Members:
- notification_pref: ALL | MENTIONS | NONE
- muted_until: null (not muted) or timestamp (muted until)

Unread tracking:
- last_read_message_id: the last message the user "saw" in this channel
- Unread count = messages in channel after last_read_message_id
```

### Entity 8: Session / Presence (Redis)
```
Online Status:
  Key: online:{workspace_id}:{user_id}
  Value: "active" | "away"
  TTL: 70 seconds (refreshed by heartbeats)

WebSocket Session:
  Key: session:{user_id}:{device_id}
  Type: Hash
  Fields: gateway_server, connection_id, workspace_id, last_heartbeat
  TTL: 70 seconds

User's Subscribed Channels (cached for fast routing):
  Key: user_channels:{user_id}
  Type: Set
  Members: channel_ids the user belongs to
  TTL: 1 hour (invalidated on join/leave)
```

---

## 3️⃣ API Design

### Authentication
```
Authorization: Bearer <JWT_TOKEN>
X-Workspace-ID: {workspace_id}
```

### Channel APIs

#### 1. Create Channel
```
POST /api/v1/channels

Request:
{
  "name": "engineering",
  "type": "PUBLIC",
  "description": "Engineering team discussions",
  "member_ids": ["usr_456", "usr_789"]
}

Response (201 Created):
{
  "channel_id": "ch_abc123",
  "name": "engineering",
  "type": "PUBLIC",
  "member_count": 3,
  "created_at": "2025-01-15T10:00:00Z"
}
```

#### 2. List User's Channels
```
GET /api/v1/channels?workspace_id=ws_123

Response (200 OK):
{
  "channels": [
    {
      "channel_id": "ch_abc123",
      "name": "engineering",
      "type": "PUBLIC",
      "member_count": 45,
      "unread_count": 12,
      "last_message_preview": "Alice: Deploying v2.5 now...",
      "last_message_at": "2025-01-15T14:30:00Z"
    }
  ]
}
```

#### 3. Join/Leave Channel
```
POST /api/v1/channels/{channel_id}/join
DELETE /api/v1/channels/{channel_id}/leave
```

### Messaging APIs (WebSocket)

#### 4. Send Message
```
WebSocket → Server:
{
  "action": "send_message",
  "data": {
    "channel_id": "ch_abc123",
    "content": "Hey team, the deploy is done! @bob check the logs",
    "client_msg_id": "client_uuid_123"
  }
}

Server → Sender (ACK):
{
  "action": "message_ack",
  "data": {
    "client_msg_id": "client_uuid_123",
    "message_id": "msg_server_xyz",
    "created_at": "2025-01-15T14:30:00Z"
  }
}

Server → All Channel Members (broadcast):
{
  "action": "new_message",
  "data": {
    "channel_id": "ch_abc123",
    "message_id": "msg_server_xyz",
    "user_id": "usr_123",
    "display_name": "Alice",
    "avatar_url": "...",
    "content": "Hey team, the deploy is done! @bob check the logs",
    "mentions": ["usr_456"],
    "created_at": "2025-01-15T14:30:00Z"
  }
}
```

#### 5. Send Thread Reply
```
WebSocket → Server:
{
  "action": "send_message",
  "data": {
    "channel_id": "ch_abc123",
    "parent_message_id": "msg_root_456",
    "content": "Logs look good, all green ✅",
    "also_post_to_channel": false
  }
}
```

#### 6. Typing Indicator
```
WebSocket → Server:
{
  "action": "typing",
  "data": {
    "channel_id": "ch_abc123"
  }
}

Server → Channel Members:
{
  "action": "user_typing",
  "data": {
    "channel_id": "ch_abc123",
    "user_id": "usr_123",
    "display_name": "Alice"
  }
}
```

### Message History (REST — for scroll-back)

#### 7. Get Channel Messages
```
GET /api/v1/channels/{channel_id}/messages?before={message_id}&limit=50

Response (200 OK):
{
  "messages": [
    {
      "message_id": "msg_xyz",
      "user_id": "usr_123",
      "display_name": "Alice",
      "content": "...",
      "thread_reply_count": 5,
      "reactions": {"👍": ["usr_456", "usr_789"], "🎉": ["usr_012"]},
      "created_at": "2025-01-15T14:30:00Z"
    }
  ],
  "has_more": true
}
```

#### 8. Get Thread Messages
```
GET /api/v1/channels/{channel_id}/messages/{message_id}/thread?limit=50

Response (200 OK):
{
  "root_message": { ... },
  "replies": [ ... ],
  "has_more": false
}
```

### Search API

#### 9. Search Messages
```
GET /api/v1/search?q=deploy+logs&channel_id=ch_abc123&from=usr_123&after=2025-01-01

Response (200 OK):
{
  "messages": [
    {
      "message_id": "msg_xyz",
      "channel_id": "ch_abc123",
      "channel_name": "engineering",
      "user_id": "usr_123",
      "content": "...deploy is done! check the **logs**...",
      "highlight": "check the <mark>logs</mark>",
      "created_at": "2025-01-15T14:30:00Z"
    }
  ],
  "total_results": 142,
  "has_more": true
}
```

### Presence API

#### 10. Presence Update (WebSocket)
```
Client → Server (on app focus):
{ "action": "presence", "data": { "status": "active" } }

Client → Server (idle 5 min):
{ "action": "presence", "data": { "status": "away" } }

Server → Workspace Members:
{ "action": "presence_change", "data": { "user_id": "usr_123", "status": "active" } }
```

### Notification Preferences

#### 11. Update Channel Notification Preference
```
PUT /api/v1/channels/{channel_id}/notifications

Request:
{
  "preference": "MENTIONS",
  "muted_until": null
}
```

---

## 4️⃣ Data Flow

### Flow 1: Send Channel Message (Real-Time)
```
1. Alice types "Deploy done! @bob check logs" in #engineering → hits Enter
   ↓
2. Client sends via WebSocket to Alice's Gateway Server:
   { action: "send_message", channel_id: "ch_eng", content: "...", client_msg_id: "abc" }
   ↓
3. Gateway forwards to Message Service
   ↓
4. Message Service:
   a. Validate: Alice is a member of #engineering
   b. Parse content: extract mentions (@bob → usr_456), links, emoji
   c. Generate message_id (timeuuid — sorted by time)
   d. Write to Cassandra: INSERT INTO messages (channel_id, message_id, ...)
   e. Publish to Kafka topic "messages.created":
      { channel_id, message_id, sender_id, content, mentions: [usr_456] }
   f. Return ACK to Alice via WebSocket (< 50ms)
   ↓
5. Alice sees message in her UI immediately (optimistic + server ACK)

ASYNC (via Kafka consumers):

6. Channel Fanout Worker:
   a. Fetch channel members from Redis cache (SMEMBERS channel:ch_eng:members)
   b. Result: 45 members including Alice
   c. For each online member (check Redis: EXISTS online:{ws}:{user}):
      - Lookup their WebSocket gateway (Redis: session:{user}:{device})
      - Publish to Redis Pub/Sub: PUBLISH messages:{gateway_server} {message}
   d. For each offline member:
      - Check notification preference (channel_members table or cache)
      - If preference allows → queue push notification
   e. All 45 members receive the message in < 200ms
   ↓
7. Notification Worker:
   a. Bob is mentioned → always notify regardless of channel preference
   b. Other members → notify based on preference (ALL, MENTIONS, NONE)
   c. Mobile push via FCM/APNS: "Alice in #engineering: Deploy done! @bob check logs"
   d. Desktop notification via WebSocket
   e. Update unread badge count
   ↓
8. Search Indexer Worker:
   a. Index message in Elasticsearch
   b. Fields: content, channel_id, user_id, workspace_id, created_at
   c. Searchable within ~1 second
   ↓
9. Unread Counter Worker:
   a. For each member: if last_read_message_id < new message_id → increment unread count
   b. Update sidebar unread badges via WebSocket
```

### Flow 2: Open Channel (Load History + Real-Time)
```
1. User clicks #engineering in sidebar
   ↓
2. Client sends REST request:
   GET /api/v1/channels/ch_eng/messages?limit=50
   ↓
3. Message Service:
   a. Query Cassandra: SELECT * FROM messages WHERE channel_id = 'ch_eng' LIMIT 50
      (returns latest 50 messages, sorted by timeuuid DESC)
   b. Hydrate user info (batch lookup from Redis cache / MySQL)
   c. Return 50 messages with user details (< 300ms)
   ↓
4. Client renders messages
   ↓
5. Client marks channel as read:
   WebSocket → { action: "mark_read", channel_id: "ch_eng", message_id: "msg_latest" }
   ↓
6. Server updates last_read_message_id in channel_members table
   Unread count resets to 0 for this channel
   Sidebar badge updates via WebSocket
   ↓
7. From now on, real-time: new messages arrive via WebSocket (Flow 1)
   User scrolling up → REST calls for older messages (pagination using before cursor)
```

### Flow 3: Thread Reply
```
1. Bob clicks "Reply in thread" on Alice's message (msg_root_456)
   ↓
2. Client loads thread:
   GET /api/v1/channels/ch_eng/messages/msg_root_456/thread?limit=50
   ↓
3. Bob types reply → sends via WebSocket:
   { action: "send_message", channel_id: "ch_eng", parent_message_id: "msg_root_456", content: "Logs look good ✅" }
   ↓
4. Message Service:
   a. Write reply to Cassandra (same channel partition, with parent_message_id set)
   b. Update root message: thread_reply_count += 1, thread_latest_reply_at = now
   c. Publish to Kafka: "messages.created" with parent_message_id
   ↓
5. Fanout Worker:
   a. Notify users in the thread (anyone who has replied or is following the thread)
   b. Do NOT post to main channel feed (unless also_post_to_channel = true)
   c. Update thread badge on root message for channel viewers
   ↓
6. Alice gets notified: "Bob replied in thread: Logs look good ✅"
```

### Flow 4: Presence Updates
```
1. User opens Slack app → WebSocket connected
   ↓
2. Client sends: { action: "presence", data: { status: "active" } }
   ↓
3. Server:
   a. SET online:{workspace_id}:{user_id} "active" EX 70
   b. Get user's workspace members (who need to know about this user)
   c. Broadcast presence change to online workspace members:
      { action: "presence_change", user_id: "usr_123", status: "active" }
   ↓
4. Every 30 seconds: client sends heartbeat → server refreshes TTL
   ↓
5. User idle for 5 minutes:
   a. Client sends: { action: "presence", status: "away" }
   b. Server updates Redis: SET online:{ws}:{user} "away" EX 70
   c. Broadcast to workspace members
   ↓
6. User closes app / loses connection:
   a. No heartbeat → Redis key expires after 70s
   b. Server detects disconnect → broadcast "offline" to workspace members
```

### Flow 5: Search
```
1. User types "deploy logs" in search box
   ↓
2. Client sends: GET /api/v1/search?q=deploy+logs
   ↓
3. Search Service:
   a. Determine user's accessible channels:
      - All public channels in workspace
      - Private channels where user is a member
      - DM conversations where user is a participant
   b. Build Elasticsearch query:
      {
        "query": {
          "bool": {
            "must": [{"match": {"content": "deploy logs"}}],
            "filter": [{"terms": {"channel_id": [accessible_channel_ids]}}]
          }
        },
        "sort": [{"created_at": "desc"}]
      }
   c. Execute search (< 500ms)
   d. Hydrate results with user info, channel names
   ↓
4. Return results with highlighted matches
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CLIENTS (Desktop, Mobile, Web)                    │
│                                                                     │
│   [Electron App]    [iOS/Android]    [Browser]    [API Clients]    │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ↓
                  ┌──────────────────────┐
                  │    CDN (CloudFront)   │
                  │  Files, avatars, JS   │
                  └──────────┬───────────┘
                             │
                             ↓
                  ┌──────────────────────┐
                  │  Load Balancer (ALB)  │
                  │  SSL termination      │
                  └──────────┬───────────┘
                             │
              ┌──────────────┴──────────────┐
              ↓                             ↓
   ┌────────────────────┐      ┌────────────────────┐
   │  WebSocket Gateway  │      │   REST API Layer   │
   │  (300 servers)      │      │   (stateless)      │
   │  • Real-time msgs   │      │   • Message history│
   │  • Typing indicators│      │   • Channel CRUD   │
   │  • Presence         │      │   • Search         │
   │  • 10M connections  │      │   • File upload     │
   └─────────┬──────────┘      └─────────┬──────────┘
             │                            │
             └──────────┬─────────────────┘
                        │
                        ↓
             ┌──────────────────────┐
             │    Message Service    │
             │  • Validate & store   │
             │  • Parse mentions     │
             │  • Publish to Kafka   │
             └──────────┬───────────┘
                        │
          ┌─────────────┼─────────────────────────┐
          ↓             ↓                         ↓
   ┌────────────┐ ┌──────────┐           ┌──────────────┐
   │   Redis    │ │  Kafka   │           │  Cassandra   │
   │  Cluster   │ │  Cluster │           │  Cluster     │
   │            │ │          │           │              │
   │ • Sessions │ │ Topics:  │           │ • Messages   │
   │ • Presence │ │ • msgs   │           │ • Partitioned│
   │ • Channels │ │ • notifs │           │   by channel │
   │ • Unread   │ │ • search │           │ • RF=3       │
   │ • Pub/Sub  │ │          │           │              │
   └────────────┘ └────┬─────┘           └──────────────┘
                       │
          ┌────────────┼────────────────────┐
          ↓            ↓                    ↓
   ┌────────────┐ ┌──────────┐    ┌──────────────┐
   │  Fanout    │ │ Notif    │    │  Search      │
   │  Worker    │ │ Worker   │    │  Indexer     │
   │            │ │          │    │              │
   │ Deliver to │ │ FCM/APNS │    │ Index in     │
   │ online     │ │ Mobile   │    │ Elasticsearch│
   │ members    │ │ push     │    │              │
   └────────────┘ └──────────┘    └──────┬───────┘
                                         ↓
                                  ┌──────────────┐
                                  │Elasticsearch  │
                                  │  Cluster      │
                                  │              │
                                  │ Full-text    │
                                  │ search across│
                                  │ all messages │
                                  └──────────────┘
```

### Component Details

#### WebSocket Gateway (Stateful)
- **Purpose**: Maintain persistent connections for real-time communication
- **Scale**: 300 servers, 65K connections each = ~10M concurrent
- **Connection registry**: Redis hash `session:{user_id}:{device_id}` → gateway_server
- **Message routing**: Redis Pub/Sub — publish to the channel for the target gateway server
- **Heartbeat**: 30s client ping, 70s TTL for session keys

#### Message Service (Stateless)
- **Purpose**: Validate, store, and publish messages
- **Scale**: 50+ stateless pods behind ALB
- **Operations**: Validate membership → parse content → write to Cassandra → publish to Kafka → ACK to sender

#### Redis Cluster (10 masters × 32 GB = 320 GB)
- **Sessions**: `session:{user_id}:{device_id}` → gateway routing (70s TTL)
- **Presence**: `online:{workspace_id}:{user_id}` → "active"/"away" (70s TTL)
- **Channel members**: `channel:{channel_id}:members` → Set of user_ids (1h TTL, invalidated on join/leave)
- **Unread counts**: `unread:{user_id}:{channel_id}` → count (updated on new message + mark-read)
- **Pub/Sub**: Route messages between gateway servers
- **Typing indicators**: `typing:{channel_id}:{user_id}` → 5s TTL

#### Cassandra Cluster (Messages — Source of Truth)
- **Partition key**: channel_id — all messages for one channel on the same partition
- **Clustering key**: message_id (timeuuid DESC) — sorted by time
- **Replication factor**: 3
- **Consistency**: QUORUM reads/writes
- **Why Cassandra**: Write-heavy (23K msgs/sec), time-series-like access pattern (recent messages read most), linear scalability

#### Kafka Cluster (10 brokers)
- **Topic `messages.created`**: All new messages — consumed by fanout, notification, and search indexer workers
- **Topic `presence.updated`**: Presence changes — consumed by presence broadcaster
- **Partition key**: channel_id (ordering per channel)
- **Retention**: 7 days
- **Consumer groups**: fanout-workers, notification-workers, search-indexers

#### Elasticsearch Cluster (Search)
- **Purpose**: Full-text search across all messages
- **Indexed fields**: content, channel_id, workspace_id, user_id, created_at
- **Access control**: At query time, filter by user's accessible channels
- **Indexing**: Async via Kafka consumer (1-2 second lag)
- **Scale**: 6 nodes (3 master, 3 data), 15 shards

#### MySQL (Metadata — Source of Truth)
- **Tables**: Workspaces, Users, Channels, Channel_Members, Files
- **Sharding**: By workspace_id (all data for one workspace on the same shard)
- **Replication**: 1 master + 3 read replicas per shard
- **Why MySQL**: Low volume metadata with complex relationships and transactional needs

#### Object Storage (S3)
- **Purpose**: File uploads (images, documents, videos)
- **Upload flow**: Client gets presigned URL → uploads directly to S3 → async processing (thumbnails, virus scan)
- **Served via CDN** for fast downloads

---

## 6️⃣ Deep Dives

### Deep Dive 1: Channel Message Fanout — Delivering to N Members in Real-Time

**The Problem**: A message in #general (1000 members) must reach all online members in < 200ms. Sequential delivery (1000 × 1ms = 1 second) is too slow.

**Solution**: Parallel fanout via Redis Pub/Sub with gateway-level batching.

**How It Works**:
1. Fanout Worker receives `message.created` event from Kafka
2. Fetch channel members from Redis: `SMEMBERS channel:{id}:members` → 1000 user_ids
3. For each member, check if online: `EXISTS online:{ws}:{user}` → batch via pipeline (1000 checks in ~5ms)
4. Group online members by their WebSocket gateway server (from `session:{user}:{device}`)
5. Send ONE Redis Pub/Sub message per gateway server (not per user):
   - `PUBLISH messages:gateway-42 {msg, recipients: [usr_1, usr_5, usr_9, ...]}`
   - Gateway 42 receives one message, pushes to all its connected recipients
6. Offline members: batch push notification (100 per FCM API call)

**Why Gateway-Level Batching**: Instead of 1000 Pub/Sub publishes (one per user), we might publish to 50 gateways (one per gateway). Each gateway handles its local users. This reduces Redis Pub/Sub traffic by 20×.

**Numbers**: 1000-member channel → ~50 gateway servers → 50 Pub/Sub messages → each gateway pushes to ~20 local members → total < 100ms.

**Talking Point**: "The key optimization is batching by gateway server — instead of 1000 individual Redis Pub/Sub messages, I group recipients by their gateway and send one message per gateway containing all recipient user_ids for that server. The gateway then does local delivery to its WebSocket connections."

---

### Deep Dive 2: Unread Counts & Read Receipts at Scale

**The Problem**: Every user needs to see accurate unread counts for every channel in their sidebar. With 20M DAU and 50 channels each, that's 1 billion unread counters to maintain in real-time.

**Solution**: Last-read pointer per user per channel + lazy unread calculation.

**How It Works**:
1. **Mark as read**: When user opens a channel, store `last_read_message_id` for (user, channel) in MySQL + Redis cache
2. **Unread count on new message**: When a message arrives in a channel, DON'T increment 1000 individual unread counters. Instead:
   - Push the new message event to the user's WebSocket
   - Client-side compares `last_read_message_id` with latest message_id → calculates unread locally
3. **Channel list load**: When user opens the app, query Redis for `last_read` per channel + latest `message_id` per channel → compute unread counts
4. **Badge (mentions)**: Separate counter `mentions:{user}:{channel}` — incremented only when @user or @channel is used

**Why Not Individual Counters?**: Incrementing N counters on every message (N = channel members) doesn't scale. A 1000-member channel generating 100 msgs/minute would cause 100K counter writes/minute just for unread tracking.

**Optimization — Unread Summary Cache**:
- Redis hash `unread_summary:{user_id}` with fields = channel_ids, values = last_read_message_id
- TTL: 1 day, invalidated on mark-read
- Sidebar rendering: compare each channel's latest message_id with the user's last_read → unread badge

**Talking Point**: "I'd track unread state with a per-user-per-channel `last_read_message_id` pointer rather than maintaining individual counters. The unread count is computed on-the-fly by comparing the pointer with the channel's latest message. This means a new message doesn't trigger N counter increments — it's O(1) on write and O(channels) on read, which is much better because reads happen once (on app open) while writes happen constantly."

---

### Deep Dive 3: Threaded Messages Without Cluttering the Channel

**The Problem**: Thread replies should appear in the thread view but NOT in the main channel feed (unless explicitly posted to channel). How do we model and query this efficiently?

**Solution**: All messages (including thread replies) are stored in the same Cassandra partition (by channel_id). Thread replies have `parent_message_id` set. Queries filter accordingly.

**Query Patterns**:
- **Main channel feed**: `SELECT * FROM messages WHERE channel_id = ? AND parent_message_id IS NULL ORDER BY message_id DESC LIMIT 50`
  - Only returns top-level messages (not thread replies)
- **Thread view**: `SELECT * FROM messages WHERE channel_id = ? AND parent_message_id = ? ORDER BY message_id ASC`
  - Returns all replies to a specific root message
- **"Also post to channel"**: When `also_post_to_channel = true`, the reply is stored TWICE — once as a thread reply (with parent_message_id) and once as a channel message (parent_message_id = null, with a reference to the thread)

**Thread Metadata on Root Message**:
- `thread_reply_count`: Incremented on each reply (displayed as "5 replies" under the root message)
- `thread_latest_reply_at`: Used to show "Last reply 2 min ago"
- `thread_participant_ids`: Set of user_ids who replied — used to determine who to notify on new replies

**Notification Logic for Threads**:
- User posts a reply → notify all `thread_participant_ids` (people who previously replied)
- User explicitly follows a thread → add to notification list
- User mutes a thread → remove from notification list

**Talking Point**: "Threads and channel messages share the same Cassandra partition (by channel_id). The `parent_message_id` field distinguishes them. The main feed query filters `parent_message_id IS NULL`. This means thread replies don't cause extra partitions or tables — just an extra column. The root message carries thread metadata (reply count, participants) to render the thread preview in the channel."

---

### Deep Dive 4: Search with Access Control

**The Problem**: A user searches for "deploy" but should only see results from channels they're a member of (public channels + their private channels + their DMs). With 50 channels per user and millions of messages, this must be fast.

**Solution**: Elasticsearch with per-query access control filtering.

**How It Works**:
1. **Indexing**: Every message is indexed in Elasticsearch asynchronously (via Kafka consumer). Indexed fields: content, channel_id, workspace_id, user_id, created_at, channel_type.
2. **At query time**: 
   a. Fetch user's accessible channels: public channels in workspace + private channels where user is member + DM conversations
   b. Build ES query with `terms` filter on channel_id
   c. Execute search

**Access Control Approaches**:

| Approach | Pros | Cons |
|----------|------|------|
| **Filter at query time** (our choice) | Simple, always consistent | Slower for users in many channels (large terms filter) |
| **Per-user search index** | Fast queries, no filter needed | Massive index duplication, expensive to maintain |
| **Document-level security (ES)** | Built-in, no app logic | Complex setup, performance concerns |

**Optimization**: Cache user's accessible channel list in Redis (`user_channels:{user_id}` set). Most users have < 100 channels — the `terms` filter handles this efficiently. For users in 1000+ channels, use a boolean query with workspace-level public filter + explicit private/DM list.

**Talking Point**: "Search access control is handled at query time — not at index time. Every message is indexed once, and the ES query includes a `terms` filter on the user's accessible channel_ids. This is simpler than maintaining per-user search indexes and stays consistent when users join/leave channels."

---

### Deep Dive 5: Presence at Scale — Broadcasting to a Large Workspace

**The Problem**: A workspace has 10,000 users. When one user comes online, we need to notify all 10K users. If 100 users change presence per minute, that's 1M notifications/minute just for one workspace.

**Solution**: Tiered presence broadcasting — only notify visible contacts.

**Optimization Approaches**:

1. **Workspace-level presence subscription** (naive): Broadcast to all workspace members. Works for small workspaces (< 500 users). Too expensive for large ones.

2. **Channel-level presence** (better): Only broadcast presence to users who share a channel with you. If Alice is in 5 channels with 50 unique users, broadcast to those 50 — not the entire 10K workspace.

3. **Viewport-based presence** (best — what Slack actually does): Client tells server which users are currently visible on screen (sidebar, channel member list). Server only sends presence updates for those users. When the viewport changes, client subscribes to new users and unsubscribes from old ones.

**Implementation**:
```
Client opens Slack → sends list of visible user_ids in sidebar:
  { action: "presence_subscribe", user_ids: ["usr_1", "usr_5", "usr_12", ...] }

Server stores subscription: user_presence_subscribers:{target_user} → Set of subscriber user_ids

When usr_5 goes online:
  - Get subscribers: SMEMBERS user_presence_subscribers:usr_5
  - Notify only those subscribers (typically 50-200 users, not 10K)

Client scrolls sidebar → updates subscription list
```

**Talking Point**: "For large workspaces, broadcasting presence to all 10K members is too expensive. I'd use viewport-based presence — the client tells the server which users are currently visible, and the server only sends presence updates for those users. This reduces presence broadcast from O(workspace_size) to O(viewport_size), typically 50-200 users."

---

### Deep Dive 6: Multi-Device Sync

**The Problem**: A user has Slack open on desktop, mobile, and web simultaneously. All three must stay in sync — messages appear on all devices, unread state is consistent, typing indicators work correctly.

**Solution**: Multiple WebSocket connections per user, each tracked independently.

**How It Works**:
1. Each device establishes its own WebSocket connection with a unique `device_id`
2. Session registry in Redis: `session:{user_id}:{device_id}` → gateway_server
3. When delivering a message, the Fanout Worker looks up ALL sessions for the user:
   - `SCAN session:{user_id}:*` (or maintain a set `user_sessions:{user_id}`)
   - Deliver to each device's gateway server
4. **Mark as read on one device → sync to others**: When desktop marks #engineering as read, server broadcasts `mark_read` event to mobile and web sessions → sidebars update

**Presence with Multiple Devices**:
- If ANY device is "active" → user shows as "active"
- If all devices are "away" → user shows as "away"
- If all devices disconnect → user shows as "offline"
- Server tracks per-device status, derives aggregate status

**Notification Dedup Across Devices**:
- If user has read the message on desktop (mark_read event) → cancel pending mobile push notification
- Use a small delay (2-3 seconds) before sending mobile push — if user reads on another device in that window, cancel the push

**Talking Point**: "Each device has its own WebSocket session tracked in Redis. Messages are delivered to all active sessions. When the user reads a message on desktop, we broadcast the `mark_read` event to their other devices so the sidebar updates everywhere. For push notifications, we add a 2-second delay — if they read the message on another device in that window, we cancel the push."

---

### Deep Dive 7: Handling Large Channels (#general with 100K members)

**The Problem**: Some channels (like #general or #announcements) have the entire company — 100K members. Fanout to 100K members per message is expensive.

**Solution**: Lazy delivery for large channels.

**Approaches by Channel Size**:
- **Small channels (< 500 members)**: Full fanout — push to every online member's WebSocket immediately
- **Medium channels (500-10K members)**: Batch fanout — push in parallel batches, prioritize mentioned users
- **Large channels (> 10K members)**: Pull-based — DON'T push. Instead:
  - Store message in Cassandra (write once)
  - Update channel's `latest_message_id` in Redis
  - When user opens the channel → client fetches latest messages (pull)
  - Only push notifications for @mentions and @channel

**Why Not Always Push for Large Channels?**: 100K members × 100 messages/day = 10M push deliveries/day for ONE channel. Most of those users aren't looking at #general right now. Pulling on-demand is cheaper.

**@channel Mention Handling**: When someone posts `@channel` in #general (100K members), we DON'T push-notify all 100K. Instead:
- Push-notify only members with notification_pref = ALL
- For members with notification_pref = MENTIONS, queue a background batch notification (rate-limited to avoid overwhelming FCM/APNS)
- Admin can restrict @channel mentions to channel admins only

**Talking Point**: "For #general with 100K members, I'd switch from push to pull. Messages are written to Cassandra and indexed, but we don't fan out to 100K WebSocket connections. Instead, when a user opens #general, they pull the latest messages. We only push for @mentions. This is similar to the celebrity problem in Twitter — for very large audiences, pull at read time is more efficient than push at write time."

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Real-time protocol** | WebSocket | Bidirectional, low latency, typing indicators, presence |
| **Message store** | Cassandra (partitioned by channel_id) | Write-heavy, time-series access, scales linearly |
| **Metadata store** | MySQL (sharded by workspace_id) | Relational data (users, channels, members), transactions |
| **Cache + real-time state** | Redis Cluster | Sessions, presence, channel members, pub/sub routing |
| **Event bus** | Kafka | Decouple message write from fanout, search, notifications |
| **Search** | Elasticsearch (async index) | Full-text search with per-query access control |
| **Fanout strategy** | Push (small channels) / Pull (large channels) | Balances latency vs cost for different channel sizes |
| **Thread model** | Same table, parent_message_id field | Simple, co-located with channel, efficient queries |
| **Unread tracking** | last_read_message_id pointer | O(1) write vs O(N) counter increments per message |
| **Presence broadcasting** | Viewport-based subscriptions | Scales to large workspaces without N² broadcast |
| **Multi-device** | Per-device WebSocket sessions | All devices stay in sync, notification dedup |
| **File storage** | S3 + presigned URLs + CDN | Scalable, no backend bottleneck, global delivery |

## Interview Talking Points

1. **"WebSocket for real-time, REST for history"** — Real-time messages, typing, presence via WebSocket. Message history, search, channel CRUD via REST. Two protocols, best of both.
2. **"Cassandra partitioned by channel_id"** — All messages for one channel on one partition. Timeuuid clustering for chronological order. 23K writes/sec scales linearly.
3. **"Kafka decouples write from fanout"** — Message write → ACK in 50ms. Fanout, notification, search indexing happen async via consumer groups.
4. **"Redis Pub/Sub for gateway routing"** — Look up user's gateway in Redis, publish to that gateway's channel. Gateway delivers to local WebSocket connections.
5. **"Unread = last_read pointer, not counters"** — Don't increment N counters per message. Store one pointer per (user, channel). Compute unread on read.
6. **"Threads share the channel partition"** — Same Cassandra table, `parent_message_id` distinguishes. No extra partitions or tables.
7. **"Access-controlled search at query time"** — Index everything once. Filter by user's accessible channels in the ES query.
8. **"Viewport-based presence for large workspaces"** — Client subscribes to visible users. Server only broadcasts for subscribed users. O(viewport) not O(workspace).
9. **"Pull for large channels, push for small"** — #general (100K members) → pull on open. #team (20 members) → push in real-time.
10. **"Multi-device via per-device sessions"** — Each device has its own WebSocket. Mark-read syncs across devices. Push notification delay for dedup.

---

**Created**: February 2026  
**Framework**: Hello Interview (6-step)  
**Estimated Interview Time**: 45-60 minutes  
**Deep Dives**: 7 topics (choose 2-3 based on interviewer interest)  
**Status**: Complete & Interview-Ready ✅
