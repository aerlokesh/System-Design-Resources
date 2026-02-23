# Design a Group Chat System - Hello Interview Framework

> **Question**: Design a group chat system where users can create group conversations, send messages to groups, and load an inbox showing the top 10 group chats sorted by most recent message. Support real-time message delivery, read tracking, and efficient inbox loading at scale.
>
> **Asked at**: Meta, WhatsApp, Discord, Slack, Microsoft Teams
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

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
1. **Create Group Chat**: Users can create a group with a name and invite members (2-500 members). Manage membership (add/remove members, leave group).
2. **Send Messages to Group**: A member sends a text message and it's delivered to ALL other group members in real-time (< 500ms).
3. **Inbox / Conversation List**: Load the user's inbox — top 10 (or N) group chats sorted by most recent message. Each entry shows: group name, last message preview, timestamp, unread count.
4. **Message History**: Load paginated message history for a group (scroll up to load older messages).
5. **Unread Count Tracking**: Track how many unread messages each user has per group. Badge count on inbox.

#### Nice to Have (P1)
- Typing indicators ("Alice is typing in Group XYZ...").
- Read receipts (who has seen which message).
- Media messages (images, files, voice notes).
- Message reactions (emoji reactions).
- Push notifications for new messages (when app is in background).
- Group admin roles and permissions.

#### Below the Line (Out of Scope)
- 1:1 direct messages (only group chats).
- Voice/video calling.
- End-to-end encryption.
- Message search across all groups.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Message Delivery Latency** | < 500ms (p99) | Real-time feel for chat |
| **Inbox Load Latency** | < 200ms | Users open app and expect instant inbox |
| **Concurrent Users** | 50M online simultaneously | WhatsApp/Discord scale |
| **Max Group Size** | 500 members | Large groups (communities) |
| **Messages per Second** | 500K msg/sec | 50M users × avg 0.6 msg/min |
| **Message Durability** | Zero loss | Chat history is permanent |
| **Availability** | 99.99% | Chat is a primary communication channel |
| **Ordering** | Messages ordered per group (causal ordering) | Users must see messages in consistent order |

### Capacity Estimation

```
Users:
  Total: 500M registered
  DAU: 100M
  Concurrent online: 50M

Groups:
  Total groups: 2 billion
  Groups per user: ~20 average
  Average group size: 8 members
  Active groups (message in last 24h): 200M

Messages:
  Messages per day: 50 billion
  Per second: ~580K msg/sec
  Average message size: 200 bytes (text + metadata)
  Daily message data: 50B × 200B = ~10 TB/day

Inbox:
  Inbox loads per day: 500M (users open app ~5x/day)
  Inbox QPS: ~6K/sec
  Each inbox: top 20 groups × 200 bytes = 4 KB response

Storage:
  Messages: 10 TB/day × 365 = ~3.6 PB/year
  Group metadata: 2B × 1 KB = 2 TB
  User-group membership: 500M × 20 groups × 16 bytes = 160 GB
  Per-user inbox state: 500M × 20 groups × 50 bytes = 500 GB
```

---

## 2️⃣ Core Entities

### Entity 1: Group
```java
public class Group {
    private final String groupId;             // UUID
    private final String name;                // "Family Chat", "Backend Team"
    private final String avatarUrl;
    private final String createdBy;           // Creator user ID
    private final Instant createdAt;
    private final List<GroupMember> members;
    private final int memberCount;
    private final Message lastMessage;        // Denormalized for inbox display
}

public class GroupMember {
    private final String userId;
    private final GroupRole role;             // ADMIN, MEMBER
    private final Instant joinedAt;
}
```

### Entity 2: Message
```java
public class Message {
    private final String messageId;           // Snowflake ID (time-sortable)
    private final String groupId;
    private final String senderId;
    private final String content;             // Text content
    private final MessageType type;           // TEXT, IMAGE, FILE, SYSTEM
    private final Instant sentAt;
    private final Map<String, String> metadata; // Attachments, reply-to, etc.
}
```

### Entity 3: Inbox Entry (per-user, per-group state)
```java
public class InboxEntry {
    private final String userId;
    private final String groupId;
    private final String groupName;
    private final String groupAvatarUrl;
    private final String lastMessagePreview;  // "Alice: Hey everyone..."
    private final Instant lastMessageAt;      // For sorting inbox
    private final String lastMessageSenderId;
    private final int unreadCount;            // Messages since last read
    private final Instant lastReadAt;         // When user last read this group
    private final boolean muted;              // User muted this group
}
```

---

## 3️⃣ API Design

### 1. Create Group
```
POST /api/v1/groups

Request:
{ "name": "Backend Team", "member_ids": ["usr_1", "usr_2", "usr_3"] }

Response (201 Created):
{
  "group_id": "grp_abc123",
  "name": "Backend Team",
  "members": [
    { "user_id": "usr_1", "role": "ADMIN" },
    { "user_id": "usr_2", "role": "MEMBER" },
    { "user_id": "usr_3", "role": "MEMBER" }
  ],
  "created_at": "2025-01-10T10:00:00Z"
}
```

### 2. Send Message (via WebSocket)
```
→ Client sends:
{
  "type": "SEND_MESSAGE",
  "group_id": "grp_abc123",
  "content": "Hey team, standup in 5!",
  "client_message_id": "client_msg_001"
}

← Server acknowledges:
{
  "type": "MESSAGE_ACK",
  "client_message_id": "client_msg_001",
  "server_message_id": "msg_xyz789",
  "sent_at": "2025-01-10T10:30:00Z"
}

← Server broadcasts to other group members:
{
  "type": "NEW_MESSAGE",
  "group_id": "grp_abc123",
  "message": {
    "message_id": "msg_xyz789",
    "sender_id": "usr_1",
    "sender_name": "Alice",
    "content": "Hey team, standup in 5!",
    "sent_at": "2025-01-10T10:30:00Z"
  }
}
```

### 3. Load Inbox (Top N Recent Groups)
```
GET /api/v1/inbox?limit=20

Response (200 OK):
{
  "inbox": [
    {
      "group_id": "grp_abc123",
      "group_name": "Backend Team",
      "group_avatar": "...",
      "last_message": "Alice: Hey team, standup in 5!",
      "last_message_at": "2025-01-10T10:30:00Z",
      "unread_count": 3,
      "muted": false
    },
    {
      "group_id": "grp_def456",
      "group_name": "Family Chat",
      "last_message": "Mom: Dinner at 7pm?",
      "last_message_at": "2025-01-10T09:15:00Z",
      "unread_count": 0,
      "muted": false
    }
  ]
}
```

### 4. Load Message History (Pagination)
```
GET /api/v1/groups/{group_id}/messages?before=msg_xyz789&limit=50

Response (200 OK):
{
  "group_id": "grp_abc123",
  "messages": [
    { "message_id": "msg_xyz780", "sender": "Bob", "content": "Got it", "sent_at": "..." },
    { "message_id": "msg_xyz779", "sender": "Alice", "content": "Check the PR", "sent_at": "..." }
  ],
  "has_more": true
}
```

### 5. Mark Group as Read
```
POST /api/v1/groups/{group_id}/read
Request: { "last_read_message_id": "msg_xyz789" }
Response: { "unread_count": 0 }
```

---

## 4️⃣ Data Flow

### Flow 1: Send Message to Group
```
1. User Alice sends "Hey team!" to Backend Team (8 members)
   ↓
2. Client sends via WebSocket to Chat Server
   ↓
3. Chat Server:
   a. Validate: is Alice a member of this group?
   b. Assign server message_id (Snowflake — time-sortable)
   c. Persist message to Message Store (Cassandra)
   d. Send ACK to Alice's client
   ↓
4. Fan-out to group members:
   a. Look up group membership: [usr_1(Alice), usr_2(Bob), usr_3(Charlie), ...]
   b. For each member (except sender):
      - Is user ONLINE? → push via WebSocket
      - Is user OFFLINE? → push notification (FCM/APNs)
   c. Update inbox for ALL members:
      - Set last_message = "Alice: Hey team!"
      - Set last_message_at = now
      - Increment unread_count for non-sender members
   ↓
5. Each online member receives message via WebSocket in < 500ms
6. Offline members receive push notification → tap → load inbox
```

### Flow 2: Load Inbox
```
1. User opens app → GET /inbox?limit=20
   ↓
2. Inbox Service:
   a. Read user's inbox from Inbox Store:
      SELECT * FROM user_inbox WHERE user_id = ? ORDER BY last_message_at DESC LIMIT 20
   b. Each row already has: group_name, last_message, unread_count
      (denormalized — no joins needed!)
   ↓
3. Return sorted inbox in < 200ms
```

### Flow 3: Message Fan-Out (Group with 500 members)
```
1. Message sent to group with 500 members
   ↓
2. Fan-out approach decision:
   - Small group (< 50 members): PUSH to each member's WebSocket directly
   - Large group (≥ 50 members): PUBLISH to group channel via Pub/Sub
     Each connected member's server subscribes to the group channel
   ↓
3. Inbox update approach:
   - Small group: UPDATE inbox for each member in batch
   - Large group: Lazy update — inbox refreshed on next open
     (don't write 500 inbox rows per message)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        CLIENTS (Mobile / Web)                               │
│                        WebSocket for real-time                              │
│                        REST for inbox, history, group mgmt                  │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   WEBSOCKET GATEWAY                                         │
│                   (Sticky by user_id, 50M connections)                      │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │
                ┌───────────────┼───────────────┐
                ▼               ▼               ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│  CHAT SERVICE     │ │  INBOX SERVICE    │ │  GROUP SERVICE    │
│                   │ │                   │ │                   │
│  • Receive message│ │  • Load inbox     │ │  • Create group   │
│  • Persist        │ │  • Sorted by      │ │  • Add/remove     │
│  • Fan-out to     │ │    recent message │ │    members        │
│    members        │ │  • Unread counts  │ │  • Group metadata │
│  • WebSocket push │ │                   │ │                   │
└────────┬─────────┘ └────────┬─────────┘ └────────┬──────────┘
         │                    │                     │
    ┌────▼──────┐       ┌────▼──────┐         ┌───▼───────┐
    │ MESSAGE    │       │ INBOX     │         │ GROUP     │
    │ STORE      │       │ STORE     │         │ STORE     │
    │ (Cassandra)│       │ (Redis +  │         │(PostgreSQL│
    │            │       │  Cassandra)│        │ + Redis)  │
    │ Partitioned│       │           │         │           │
    │ by group_id│       │ Per-user  │         │ Members   │
    │            │       │ sorted set│         │ Metadata  │
    └───────────┘       └───────────┘         └───────────┘

    ┌──────────────────────────────────────────────────────────┐
    │              REDIS PUB/SUB                                │
    │  • Channel per group: "group:{group_id}"                 │
    │  • Cross-server message delivery                         │
    │  • Presence (online/offline tracking)                    │
    └──────────────────────────────────────────────────────────┘

    ┌──────────────────────────────────────────────────────────┐
    │              PUSH NOTIFICATION SERVICE                    │
    │  • FCM (Android) / APNs (iOS)                           │
    │  • For offline members                                   │
    │  • Batched per group                                     │
    └──────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **WebSocket Gateway** | 50M connections, routing | Envoy / custom | Sharded by user_id |
| **Chat Service** | Message processing, fan-out | Go/Java on K8s | 100+ pods |
| **Inbox Service** | Load/update user's conversation list | Go/Java on K8s | 50+ pods |
| **Group Service** | Group CRUD, membership | Java on K8s | 20+ pods |
| **Message Store** | Persistent message history | Cassandra | Partitioned by group_id |
| **Inbox Store** | Per-user inbox with sort | Redis Sorted Set + Cassandra | Sharded by user_id |
| **Group Store** | Group metadata, membership | PostgreSQL + Redis cache | Sharded by group_id |
| **Redis Pub/Sub** | Cross-server group message delivery | Redis Cluster | Channel per group |
| **Push Service** | Offline notifications | FCM / APNs | Batched |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Inbox Data Model — Fast "Top N Recent Groups" Query

**The Problem**: When a user opens the app, we must return their top 20 groups sorted by most recent message in < 200ms. With 20 groups per user and 500M users, this is 10B inbox entries. How do we make this fast?

```java
public class InboxStore {

    /**
     * Redis Sorted Set per user:
     *   Key: "inbox:{user_id}"
     *   Score: last_message_timestamp (epoch millis)
     *   Member: group_id
     * 
     * Operations:
     *   Load inbox: ZREVRANGE inbox:{user_id} 0 19 → top 20 by recency → O(K)
     *   Update on new message: ZADD inbox:{user_id} {timestamp} {group_id} → O(log N)
     *   
     * Additional data per entry (Redis Hash):
     *   Key: "inbox_meta:{user_id}:{group_id}"
     *   Fields: group_name, last_message_preview, last_sender, unread_count
     */
    
    // Load inbox: top N groups sorted by recent message
    public List<InboxEntry> loadInbox(String userId, int limit) {
        // Step 1: Get top N group_ids sorted by last_message_at (descending)
        Set<ZSetOperations.TypedTuple<String>> topGroups = 
            redis.zrevrangeWithScores("inbox:" + userId, 0, limit - 1);
        
        // Step 2: Batch-fetch metadata for each group
        List<InboxEntry> inbox = new ArrayList<>();
        Pipeline pipe = redis.pipelined();
        Map<String, Response<Map<String, String>>> metaResponses = new HashMap<>();
        
        for (var entry : topGroups) {
            String groupId = entry.getValue();
            metaResponses.put(groupId, 
                pipe.hgetAll("inbox_meta:" + userId + ":" + groupId));
        }
        pipe.sync();
        
        for (var entry : topGroups) {
            String groupId = entry.getValue();
            Map<String, String> meta = metaResponses.get(groupId).get();
            
            inbox.add(InboxEntry.builder()
                .groupId(groupId)
                .groupName(meta.get("group_name"))
                .lastMessagePreview(meta.get("last_message"))
                .lastMessageAt(Instant.ofEpochMilli(entry.getScore().longValue()))
                .unreadCount(Integer.parseInt(meta.getOrDefault("unread_count", "0")))
                .build());
        }
        
        return inbox;
    }
    
    // Update inbox when new message arrives in a group
    public void onNewMessage(String groupId, Message message, List<String> memberIds) {
        String preview = message.getSenderName() + ": " + truncate(message.getContent(), 50);
        long timestamp = message.getSentAt().toEpochMilli();
        
        Pipeline pipe = redis.pipelined();
        for (String memberId : memberIds) {
            // Update sort order
            pipe.zadd("inbox:" + memberId, timestamp, groupId);
            
            // Update metadata
            pipe.hset("inbox_meta:" + memberId + ":" + groupId, Map.of(
                "last_message", preview,
                "last_sender", message.getSenderId()
            ));
            
            // Increment unread count (except for sender)
            if (!memberId.equals(message.getSenderId())) {
                pipe.hincrBy("inbox_meta:" + memberId + ":" + groupId, "unread_count", 1);
            }
        }
        pipe.sync();
    }
    
    // Mark group as read
    public void markRead(String userId, String groupId) {
        redis.hset("inbox_meta:" + userId + ":" + groupId, "unread_count", "0");
    }
}
```

**Performance**:
```
Inbox load: ZREVRANGE (O(K) = O(20)) + Pipeline HGETALL (20 calls, 1 round-trip)
  Total: < 5ms Redis time + network → < 50ms end-to-end

Inbox update on new message (8-member group):
  8 × ZADD + 8 × HSET + 7 × HINCRBY = 23 Redis commands
  Pipelined: 1 round-trip → < 2ms

Memory per user: 20 groups × ~200 bytes (sorted set + hashes) = ~4 KB
Total for 500M users: 500M × 4 KB = ~2 TB → Redis cluster with 64 nodes × 32 GB
```

---

### Deep Dive 2: Message Fan-Out — Small Group vs Large Group Strategy

**The Problem**: When a message is sent to a group, we must deliver it to all members. A 5-person group is easy; a 500-person group is expensive. We need different strategies for different group sizes.

```java
public class MessageFanOutService {

    private static final int LARGE_GROUP_THRESHOLD = 50;
    
    /**
     * Two strategies based on group size:
     * 
     * SMALL GROUP (< 50 members): DIRECT PUSH
     *   - Look up each member's WebSocket connection
     *   - Send message directly to each connection
     *   - Update each member's inbox in Redis
     *   - Simple, low latency, works for most groups
     * 
     * LARGE GROUP (≥ 50 members): PUB/SUB
     *   - Publish message to Redis Pub/Sub channel "group:{group_id}"
     *   - Each chat server subscribes to channels for its connected users' groups
     *   - Server receives published message → delivers to local connections
     *   - Inbox update: LAZY (on next inbox load) — avoid 500 Redis writes per message
     */
    
    public void fanOutMessage(String groupId, Message message) {
        List<String> memberIds = groupStore.getMembers(groupId);
        int groupSize = memberIds.size();
        
        if (groupSize < LARGE_GROUP_THRESHOLD) {
            directPush(groupId, message, memberIds);
        } else {
            pubSubPush(groupId, message, memberIds);
        }
    }
    
    // Small group: push directly to each member
    private void directPush(String groupId, Message message, List<String> memberIds) {
        for (String memberId : memberIds) {
            if (memberId.equals(message.getSenderId())) continue;
            
            // Try WebSocket delivery
            WebSocketConnection ws = connectionRegistry.getConnection(memberId);
            if (ws != null) {
                ws.send(new NewMessageEvent(groupId, message));
            } else {
                // User offline → push notification
                pushService.sendNotification(memberId, groupId, message);
            }
        }
        
        // Update all members' inboxes
        inboxStore.onNewMessage(groupId, message, memberIds);
    }
    
    // Large group: publish to channel
    private void pubSubPush(String groupId, Message message, List<String> memberIds) {
        // Publish to Redis Pub/Sub channel
        redis.publish("group:" + groupId, serialize(message));
        
        // Lazy inbox update: DON'T update 500 inbox entries per message
        // Instead: when user loads inbox, compute from message timestamps
        // Only update sender's inbox (1 write)
        inboxStore.updateSenderInbox(message.getSenderId(), groupId, message);
        
        // Push notifications for offline members (batched)
        List<String> offlineMembers = memberIds.stream()
            .filter(id -> !id.equals(message.getSenderId()))
            .filter(id -> !connectionRegistry.isOnline(id))
            .toList();
        pushService.sendBatchNotification(offlineMembers, groupId, message);
    }
}
```

**Trade-off: eager vs lazy inbox update**:
```
Small group (8 members): Eager update
  8 ZADD + 8 HSET = 16 Redis ops per message → fine
  Total: ~2ms per message

Large group (500 members): Eager update would be:
  500 ZADD + 500 HSET + 499 HINCRBY = 1499 Redis ops per message → TOO EXPENSIVE
  If group has 100 messages/min → 150K Redis ops/min for ONE group

Large group: Lazy update
  Only update sender's inbox (1 ZADD + 1 HSET)
  When other members load inbox: 
    Check message store for latest message per group → compute on read
  Trade-off: inbox load slightly slower for large group members
             but massively reduces write amplification
```

---

### Deep Dive 3: Message Ordering — Snowflake IDs + Per-Group Sequence

**The Problem**: Messages in a group must appear in the same order for all members. If two members send messages simultaneously, we need a consistent total order.

```java
public class MessageOrderingService {

    /**
     * Ordering strategy: Server-assigned Snowflake IDs
     * 
     * 1. Client sends message with client_message_id (for dedup)
     * 2. Chat Server assigns server message_id using Snowflake ID generator:
     *    - 41 bits: timestamp (ms since epoch) → natural time ordering
     *    - 10 bits: server_id → uniqueness across servers
     *    - 12 bits: sequence → uniqueness within same ms on same server
     * 3. Message stored with server message_id → guarantees total order per group
     * 
     * Why not client timestamps:
     *   - Client clocks are unreliable (skewed, manipulated)
     *   - Two clients at different times send simultaneously
     *   - Server timestamp = single source of truth for ordering
     * 
     * For a single group: messages always go through one Chat Server
     * (sticky routing by group_id) → serial ordering guaranteed
     */
    
    private final SnowflakeIdGenerator idGenerator;
    
    public Message processMessage(String groupId, String senderId, String content) {
        // Assign globally unique, time-sortable ID
        long messageId = idGenerator.nextId();
        
        Message message = Message.builder()
            .messageId(String.valueOf(messageId))
            .groupId(groupId)
            .senderId(senderId)
            .content(content)
            .sentAt(Instant.now())
            .build();
        
        // Persist (Cassandra: partition by group_id, cluster by message_id)
        messageStore.save(message);
        
        return message;
    }
}
```

**Cassandra schema for message ordering**:
```sql
CREATE TABLE messages (
    group_id    TEXT,
    message_id  BIGINT,      -- Snowflake ID (time-sortable)
    sender_id   TEXT,
    content     TEXT,
    sent_at     TIMESTAMP,
    PRIMARY KEY (group_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

-- Query: latest 50 messages in a group
-- SELECT * FROM messages WHERE group_id = 'grp_abc' LIMIT 50;
-- → Returns most recent 50, ordered by message_id DESC

-- Query: messages before a specific message (pagination)
-- SELECT * FROM messages WHERE group_id = 'grp_abc' AND message_id < 123456 LIMIT 50;
```

---

### Deep Dive 4: Unread Count — Efficient Per-User Per-Group Tracking

**The Problem**: Each user needs to know how many unread messages they have in each group. With 500M users × 20 groups = 10B counters to maintain.

```java
public class UnreadCountService {

    /**
     * Unread count strategy:
     * 
     * Store per user per group:
     *   - last_read_message_id: the Snowflake ID of the last message the user read
     * 
     * Computing unread count:
     *   - Option A (eager): increment counter on each new message → write-heavy
     *   - Option B (lazy): count messages WHERE message_id > last_read_message_id → read-heavy
     *   - Option C (hybrid): eager for small groups, lazy for large groups
     * 
     * Our choice: EAGER for small groups (< 50 members)
     *             LAZY for large groups (≥ 50 members)
     * 
     * Eager: Redis HINCRBY on inbox_meta → already done in inbox update
     * Lazy: On inbox load, query Cassandra:
     *   SELECT COUNT(*) FROM messages WHERE group_id = ? AND message_id > ?
     *   (This is efficient because message_id is the clustering key)
     */
    
    public int getUnreadCount(String userId, String groupId, int groupSize) {
        if (groupSize < 50) {
            // Eager: read from pre-computed Redis counter
            String count = redis.hget("inbox_meta:" + userId + ":" + groupId, "unread_count");
            return count != null ? Integer.parseInt(count) : 0;
        } else {
            // Lazy: compute from message store
            long lastReadId = getLastReadMessageId(userId, groupId);
            return messageStore.countMessagesAfter(groupId, lastReadId);
        }
    }
    
    public void markRead(String userId, String groupId, String lastMessageId) {
        // Update last read position
        redis.hset("last_read:" + userId, groupId, lastMessageId);
        
        // Reset unread counter
        redis.hset("inbox_meta:" + userId + ":" + groupId, "unread_count", "0");
    }
    
    // Total unread badge (sum across all groups)
    public int getTotalUnreadBadge(String userId) {
        // Option 1: Sum all per-group counters
        Map<String, String> allMeta = redis.hgetAll("inbox_meta:" + userId);
        return allMeta.values().stream()
            .filter(v -> v.contains("unread_count"))
            .mapToInt(Integer::parseInt)
            .sum();
        
        // Option 2: Maintain a separate total counter (faster but harder to keep consistent)
        // return Integer.parseInt(redis.get("badge:" + userId));
    }
}
```

---

### Deep Dive 5: WebSocket Connection Management & Presence

**The Problem**: 50M concurrent WebSocket connections. When a message is sent, we need to know WHICH server each recipient is connected to in order to deliver the message.

```java
public class ConnectionRegistry {

    /**
     * Connection tracking:
     *   Redis Hash: "connections:{user_id}" → { "server": "chat-server-42", "ws_id": "ws_abc" }
     * 
     * When user connects: HSET connections:{user_id} server chat-server-42
     * When user disconnects: HDEL connections:{user_id}
     * When delivering message: HGET connections:{recipient_id} → which server?
     * 
     * For small groups (< 50):
     *   Look up each member's server → send directly to that server
     * 
     * For large groups (≥ 50):
     *   Use Redis Pub/Sub channel per group
     *   Each server subscribes to channels for groups its users belong to
     *   On message: PUBLISH to group channel → all servers with members receive it
     */
    
    public void onUserConnected(String userId, String serverId) {
        redis.hset("conn:" + userId, "server", serverId);
        redis.hset("conn:" + userId, "connected_at", String.valueOf(Instant.now().toEpochMilli()));
    }
    
    public void onUserDisconnected(String userId) {
        redis.del("conn:" + userId);
    }
    
    public boolean isOnline(String userId) {
        return redis.exists("conn:" + userId);
    }
    
    public String getServer(String userId) {
        return redis.hget("conn:" + userId, "server");
    }
    
    // Deliver message to a specific user
    public void deliverToUser(String userId, Object message) {
        String server = getServer(userId);
        if (server == null) return; // User offline
        
        if (server.equals(currentServerId)) {
            // Same server: deliver directly via local WebSocket
            localConnections.get(userId).send(message);
        } else {
            // Different server: forward via inter-server RPC or Redis Pub/Sub
            redis.publish("server:" + server, serialize(userId, message));
        }
    }
}
```

**Connection distribution**:
```
50M connections ÷ 50K per server = 1000 Chat Server pods

Each pod:
  ~50K WebSocket connections
  ~500 MB RAM for connection state
  Subscribes to Redis channels for all groups its users belong to

Connection lookup: Redis HGET → < 0.5ms
Delivery routing: same server (direct) or cross-server (Redis Pub/Sub) → < 5ms
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Inbox model** | Redis Sorted Set per user (ZREVRANGE) | O(K) for top-K groups; O(log N) update per message; < 50ms inbox load |
| **Fan-out** | Direct push (small groups) + Pub/Sub (large groups) | Threshold at 50 members; avoids 500 Redis writes per message for large groups |
| **Inbox update** | Eager (small groups) + Lazy (large groups) | Small: 16 Redis ops fine. Large: 1500 ops/msg too expensive → compute on read |
| **Message ordering** | Server-assigned Snowflake IDs | Time-sortable, globally unique, no coordination needed; server is ordering authority |
| **Message store** | Cassandra (partition by group_id, cluster by message_id DESC) | Fast ordered reads per group; high write throughput; natural pagination |
| **Unread count** | Eager counter (small) + Lazy count query (large) | Same threshold; hybrid minimizes both read and write costs |
| **Connection tracking** | Redis hash per user → server mapping | O(1) lookup; presence tracking; < 0.5ms |

## Interview Talking Points

1. **"Redis Sorted Set for inbox: ZREVRANGE gives top-K in O(K)"** — Each user's inbox is a sorted set scored by last_message_at. ZREVRANGE 0 19 returns top 20 groups in microseconds. Pipeline HGETALL for metadata. Total < 50ms.
2. **"Small group = direct push, large group = Pub/Sub"** — Under 50 members: look up each member's server, push directly. Over 50: publish to group channel, servers deliver locally. Avoids O(N) connection lookups for large N.
3. **"Eager vs lazy inbox update at 50-member threshold"** — 8-member group: 16 Redis writes per message (fine). 500-member group: 1500 writes per message (150K/min for active groups!). Lazy: compute on read from Cassandra.
4. **"Snowflake IDs for message ordering"** — Time-sortable (41-bit timestamp) + server-unique. Messages routed to same server per group (sticky) → serial ordering. Cassandra clusters by message_id DESC → efficient pagination.
5. **"Cassandra: partition by group_id for message locality"** — All messages for one group on same Cassandra node. DESC clustering → latest messages read first. Pagination via `message_id < X LIMIT 50`.
6. **"Unread = eager counter (small) + lazy count (large)"** — Redis HINCRBY for small groups (already updated during inbox update). Cassandra COUNT for large groups (WHERE message_id > last_read, efficient on clustering key).

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **WhatsApp** | Same messaging pattern | WhatsApp includes 1:1 + group + E2E encryption |
| **Slack** | Same group messaging | Slack adds channels, threads, workspace hierarchy |
| **Discord** | Same large-group messaging | Discord adds voice channels, roles, server structure |
| **Facebook Messenger** | Same inbox pattern | Messenger includes stories, video, cross-platform |
| **Notification System** | Same push delivery | Notifications are one-directional; chat is bidirectional |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Inbox** | Redis Sorted Set | Cassandra (timeseries) | If Redis memory too expensive; trade latency for cost |
| **Messages** | Cassandra | ScyllaDB | Higher throughput; lower tail latency; Cassandra-compatible |
| **Fan-out** | Redis Pub/Sub | Kafka | If need message replay; larger buffer; more durable |
| **Connections** | Redis hash | Consistent hashing (no registry) | If user→server mapping is deterministic |
| **Push** | FCM / APNs | OneSignal / custom | Managed service; less operational burden |
| **IDs** | Snowflake | ULID / UUIDv7 | If don't need custom ID generation; simpler |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 5 topics (choose 2-3 based on interviewer interest)

---

**References**:
- WhatsApp Architecture (Facebook Engineering)
- Discord Engineering Blog (How Discord Stores Billions of Messages)
- Cassandra Data Modeling for Chat Applications
- Redis Sorted Sets for Real-Time Rankings
- Snowflake ID Generation (Twitter Engineering)
- Fan-Out on Write vs Read (Instagram Engineering)
