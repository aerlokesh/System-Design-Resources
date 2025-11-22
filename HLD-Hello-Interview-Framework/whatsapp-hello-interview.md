# WhatsApp Messenger System Design - Hello Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [High-Level Design](#5️⃣-high-level-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **User Management**
   - User registration with phone number verification
   - Profile management (name, status message, profile picture)
   - Contact list synchronization
   - Block/unblock users
   - Privacy settings (last seen, profile picture, status)

2. **One-on-One Messaging**
   - Send/receive text messages in real-time
   - Message delivery status (sent ✓, delivered ✓✓, read ✓✓ blue)
   - Message history retrieval
   - Delete messages (for me, for everyone)
   - Forward messages
   - Reply to specific messages (quoted replies)

3. **Group Chat**
   - Create groups (up to 256 participants)
   - Add/remove participants
   - Admin roles and permissions
   - Group name, description, icon
   - View participant list
   - Group delivery receipts
   - Leave group

4. **Media Sharing**
   - Share images (JPEG, PNG, max 16MB)
   - Share videos (MP4, max 16MB)
   - Share documents (PDF, DOC, max 100MB)
   - Share voice messages
   - Share location
   - Share contacts

5. **Presence & Status**
   - Online/offline indicators
   - Last seen timestamp
   - Typing indicators
   - "Recording audio" indicator

#### Nice to Have (P1)
- Voice calls (1:1 and group)
- Video calls (1:1 and group)
- Status/Stories (24-hour expiry)
- Message reactions (emoji)
- Disappearing messages
- Archived chats
- Starred messages
- Message search
- Multi-device support
- Two-factor authentication

### Non-Functional Requirements

#### Scalability
- **Daily Active Users (DAU)**: 2 billion
- **Messages per day**: 100 billion (~1.15M messages/second)
- **Concurrent connections**: 500 million simultaneous WebSocket connections
- **Group messages**: 4.5 billion per day
- **Peak load**: Handle 10x traffic during events

#### Performance
- **Message delivery latency**: < 100ms (p95)
- **Message send latency**: < 50ms
- **Connection establishment**: < 1 second
- **Image upload**: < 3 seconds (5MB image)
- **Conversation load**: < 200ms

#### Availability
- **Uptime**: 99.99% (< 53 minutes downtime/year)
- **Multi-region deployment** for low latency globally
- **No message loss** (durability guarantee)
- **Offline message queueing** (deliver when user comes online)

#### Reliability
- **Message delivery**: At-least-once guarantee
- **Message ordering**: Maintain order per conversation
- **No duplicates**: Idempotent delivery
- **Persistent storage**: Messages stored until deleted
- **Cross-device sync**: All devices receive messages

#### Security
- **End-to-end encryption (E2EE)**: Server cannot read messages
- **Perfect forward secrecy**: Compromised keys don't expose past messages
- **Server-side encryption** at rest
- **TLS 1.3** for all connections
- **Two-factor authentication**
- **Signal Protocol** for E2EE implementation

#### Consistency
- **Eventual consistency** for presence, last seen (acceptable)
- **Strong consistency** for messages (no loss)
- **Causal ordering** for conversation messages

### Capacity Estimation

#### Traffic Estimates
```
Daily Active Users (DAU): 2B
Messages per user per day: 50
Total messages/day: 2B × 50 = 100 billion

Messages/second (average): 100B / 86,400 ≈ 1.15M QPS
Messages/second (peak, 3x): ~3.5M QPS

1:1 messages: 95.5B/day (95.5%)
Group messages: 4.5B/day (4.5%)

Concurrent WebSocket connections: 500M
```

#### Storage Estimates

**Text Messages**:
```
Average message size: 100 bytes (text + metadata)
Daily text messages: 100B
Daily storage: 100B × 100 bytes = 10 TB/day

Yearly storage: 10 TB × 365 = 3.65 PB/year
5-year storage: 18.25 PB (text only)
```

**Media Messages**:
```
Media messages/day: 10B (10% of total)
Average sizes:
- Images: 500 KB
- Videos: 5 MB
- Voice messages: 50 KB
- Documents: 2 MB

Distribution: 60% images, 20% videos, 15% voice, 5% docs
Weighted average: 1.4 MB per media message

Daily media storage: 10B × 1.4 MB = 14 PB/day
Yearly: 14 PB × 365 = 5.1 EB/year

With compression (50% reduction): 2.55 EB/year
```

**Total Storage**:
```
Year 1: 2.6 EB (2.55 media + 0.05 text)
Year 5: 13 EB

Note: Server retention for undelivered messages: 30 days
Primary storage: On client devices (encrypted)
```

#### Bandwidth Estimates
```
Incoming (message sends):
  10 TB text + 14 PB media = 14.01 PB/day
  14.01 PB / 86,400 seconds ≈ 162 GB/second
  Peak (3x): ~486 GB/second

Outgoing (message deliveries):
  Similar to incoming: ~162 GB/second
```

#### Memory Requirements
```
Online user presence:
  500M concurrent × 100 bytes = 50 GB

Recent conversations (50 messages per user):
  500M users × 50 messages × 100 bytes = 2.5 TB

Undelivered message queues:
  200M offline users × 100 messages × 100 bytes = 2 TB

Connection metadata:
  500M connections × 500 bytes = 250 GB

Total memory: ~5 TB
Distributed: 200 Redis nodes × 25GB each
```

#### Server Estimates
```
WebSocket connections per server: 65K (optimized)
Servers for 500M connections: 500M / 65K ≈ 8,000 servers
With redundancy (2x): 16,000 servers
Organized in regional clusters
```

---

## 2️⃣ Core Entities

### Entity 1: User
**Purpose**: Represents a WhatsApp user account

**Schema**:
```sql
Users Table (MySQL):
- user_id (PK, UUID)
- phone_number_hash (unique, indexed, SHA-256)
- display_name (varchar 100)
- status_message (varchar 139)
- profile_picture_url
- about (text)
- created_at (timestamp)
- last_seen_at (timestamp)
- is_online (boolean)
- privacy_last_seen (enum: everyone, contacts, nobody)
- privacy_profile_pic (enum: everyone, contacts, nobody)
- privacy_status (enum: everyone, contacts, nobody)
- privacy_read_receipts (boolean)

Indexes:
- PRIMARY KEY (user_id)
- UNIQUE INDEX (phone_number_hash)
- INDEX (last_seen_at)
```

**Relationships**:
- Has many: Messages, Contacts, Devices
- Many-to-many: Groups (via group_members)

**Sharding**: By user_id (100 shards)

### Entity 2: Message (1:1)
**Purpose**: Represents a message between two users

**Schema**:
```cql
Messages Table (Cassandra):
- conversation_id (partition key, UUID)
- message_id (clustering key, timeuuid)
- sender_id (UUID)
- receiver_id (UUID)
- encrypted_content (blob)
- message_type (enum: TEXT, IMAGE, VIDEO, DOCUMENT, VOICE, LOCATION, CONTACT)
- media_url (text, nullable)
- created_at (timestamp)
- status (enum: SENT, DELIVERED, READ, FAILED)
- is_deleted (boolean)
- deleted_at (timestamp, nullable)
- reply_to_message_id (timeuuid, nullable)

PRIMARY KEY ((conversation_id), message_id)
WITH CLUSTERING ORDER BY (message_id DESC)

Indexes:
- None needed (queries by conversation_id efficient)
```

**Relationships**:
- Belongs to: Conversation
- Belongs to: User (sender)
- Optional: Reply to another Message

**Conversation ID Generation**:
```
conversation_id = UUID.v5(namespace, sorted([user_a_id, user_b_id]))

Benefits:
- Deterministic (same ID for both users)
- No separate conversation table needed
- Always get same conv_id for user pair
```

### Entity 3: Group Message
**Purpose**: Represents a message in a group chat

**Schema**:
```cql
Group_Messages Table (Cassandra):
- group_id (partition key, UUID)
- message_id (clustering key, timeuuid)
- sender_id (UUID)
- encrypted_content (blob)
- message_type (enum: TEXT, IMAGE, VIDEO, etc.)
- media_url (text, nullable)
- created_at (timestamp)
- is_deleted (boolean)
- reply_to_message_id (timeuuid, nullable)

PRIMARY KEY ((group_id), message_id)
WITH CLUSTERING ORDER BY (message_id DESC)

Group_Message_Receipts Table:
- group_id (partition key)
- message_id (clustering key, timeuuid)
- user_id (clustering key, UUID)
- status (enum: SENT, DELIVERED, READ)
- timestamp (timestamp)

PRIMARY KEY ((group_id, message_id), user_id)
```

**Relationships**:
- Belongs to: Group
- Belongs to: User (sender)
- Has many: Delivery Receipts (one per member)

### Entity 4: Group
**Purpose**: Represents a group chat

**Schema**:
```cql
Groups Table (Cassandra):
- group_id (PK, UUID)
- name (text, max 25 chars)
- description (text, max 512 chars)
- icon_url (text)
- created_by (UUID)
- created_at (timestamp)
- max_members (int, default 256)
- member_count (counter)

Group_Members Table:
- group_id (partition key)
- user_id (clustering key)
- role (enum: ADMIN, MEMBER)
- joined_at (timestamp)
- added_by (UUID)
- is_active (boolean)

PRIMARY KEY ((group_id), user_id)

User_Groups Table (for user's group list):
- user_id (partition key)
- group_id (clustering key)
- joined_at (timestamp)
- last_message_at (timestamp)

PRIMARY KEY ((user_id), last_message_at, group_id)
WITH CLUSTERING ORDER BY (last_message_at DESC)
```

**Relationships**:
- Has many: Members (Users)
- Has many: Messages
- Created by: User

### Entity 5: Contact
**Purpose**: User's contact list

**Schema**:
```sql
Contacts Table (MySQL):
- user_id (FK to Users)
- contact_user_id (FK to Users)
- contact_name (varchar 100, user's local name)
- added_at (timestamp)
- is_blocked (boolean)

PRIMARY KEY (user_id, contact_user_id)
INDEX (contact_user_id)
```

**Relationships**:
- Links two Users
- Unidirectional (Alice adds Bob != Bob adds Alice)

### Entity 6: Device
**Purpose**: Track user's devices for multi-device support

**Schema**:
```sql
Devices Table (MySQL):
- device_id (PK, UUID)
- user_id (FK to Users)
- device_name (varchar 100, e.g., "iPhone 13")
- device_type (enum: PHONE, TABLET, WEB, DESKTOP)
- platform (enum: IOS, ANDROID, WEB, WINDOWS, MAC)
- push_token (varchar 500, for FCM/APNS)
- last_active_at (timestamp)
- is_primary (boolean)
- created_at (timestamp)

INDEX (user_id, last_active_at)
```

**Relationships**:
- Belongs to: User
- User has many Devices (max 5)

### Entity 7: Session (Cached)
**Purpose**: Track active WebSocket connections

**Redis Structure**:
```
Key: session:{user_id}:{device_id}
Type: Hash
Fields:
  - gateway_server: "ws-gateway-42.us-east"
  - connection_id: "conn_abc123"
  - connected_at: 1704672000
  - last_heartbeat: 1704673000
TTL: 70 seconds (auto-cleanup)

Key: online:{user_id}
Type: String
Value: 1 (is online)
TTL: 70 seconds (refreshed by heartbeats)

Key: last_seen:{user_id}
Type: String
Value: Unix timestamp
TTL: 7 days
```

### Entity 8: Undelivered Messages Queue
**Purpose**: Queue messages for offline users

**Redis Structure**:
```
Key: undelivered:{user_id}
Type: List (FIFO queue)
Values: message_id (list of message IDs)

Commands:
LPUSH undelivered:bob msg_abc  -- Add to queue
LRANGE undelivered:bob 0 -1    -- Get all queued
LREM undelivered:bob 1 msg_abc -- Remove after delivery

TTL: 30 days (undelivered messages expire)
Max size: 10,000 messages per user
```

---

## 3️⃣ API Design

### Authentication
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
X-Device-ID: {device_id}
X-App-Version: 2.25.1
```

### User APIs

#### 1. Register / Verify Phone
```
POST /api/v1/auth/register

Request:
{
  "phone_number": "+1234567890",
  "country_code": "US"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "verification_id": "ver_123abc",
    "expires_in": 300,
    "message": "OTP sent to +1234567890"
  }
}
```

#### 2. Verify OTP
```
POST /api/v1/auth/verify

Request:
{
  "verification_id": "ver_123abc",
  "otp": "123456"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "user_id": "usr_789xyz",
    "access_token": "jwt_token_here",
    "refresh_token": "refresh_token_here",
    "is_new_user": true
  }
}
```

#### 3. Get User Profile
```
GET /api/v1/users/{user_id}

Response (200 OK):
{
  "success": true,
  "data": {
    "user_id": "usr_789xyz",
    "phone_number": "+1234567890",
    "display_name": "Alice",
    "status_message": "Hey there! I'm using WhatsApp",
    "profile_picture": "https://cdn.whatsapp.com/...",
    "last_seen": 1704672000,
    "is_online": true
  }
}
```

#### 4. Update Profile
```
PUT /api/v1/users/me

Request:
{
  "display_name": "Alice Smith",
  "status_message": "Busy coding",
  "profile_picture": "data:image/jpeg;base64,..."
}

Response (200 OK):
{
  "success": true,
  "message": "Profile updated successfully"
}
```

#### 5. Sync Contacts
```
POST /api/v1/users/contacts/sync

Request:
{
  "contacts": [
    {"phone_hash": "hash1"},
    {"phone_hash": "hash2"}
  ]
}

Response (200 OK):
{
  "success": true,
  "data": {
    "registered_users": [
      {
        "user_id": "usr_456",
        "phone_number": "+1234567891",
        "display_name": "Bob"
      }
    ]
  }
}
```

### Messaging APIs (WebSocket)

#### 6. Send Message (1:1)
```
WebSocket Message:
{
  "action": "send_message",
  "data": {
    "temp_id": "temp_client_123",
    "receiver_id": "usr_456",
    "encrypted_content": "base64_encrypted_data",
    "message_type": "TEXT",
    "timestamp": 1704672000000
  }
}

Server Response (ACK):
{
  "action": "message_ack",
  "data": {
    "temp_id": "temp_client_123",
    "message_id": "msg_server_abc",
    "status": "SENT",
    "timestamp": 1704672001000
  }
}
```

#### 7. Receive Message
```
WebSocket Push from Server:
{
  "action": "new_message",
  "data": {
    "message_id": "msg_xyz",
    "conversation_id": "conv_abc",
    "sender_id": "usr_789",
    "encrypted_content": "base64_data",
    "message_type": "TEXT",
    "timestamp": 1704672000000
  }
}

Client Response (Delivery ACK):
{
  "action": "delivery_ack",
  "data": {
    "message_id": "msg_xyz",
    "status": "DELIVERED"
  }
}
```

#### 8. Send Read Receipt
```
WebSocket Message:
{
  "action": "read_receipt",
  "data": {
    "conversation_id": "conv_abc",
    "message_id": "msg_xyz",
    "read_at": 1704672100000
  }
}
```

#### 9. Typing Indicator
```
WebSocket Message:
{
  "action": "typing",
  "data": {
    "conversation_id": "conv_abc",
    "status": "TYPING"  // or "STOPPED"
  }
}

Server Broadcast to Participants:
{
  "action": "user_typing",
  "data": {
    "conversation_id": "conv_abc",
    "user_id": "usr_789",
    "username": "Alice",
    "status": "TYPING"
  }
}
```

### Group APIs

#### 10. Create Group
```
POST /api/v1/groups

Request:
{
  "name": "Family Group",
  "description": "Our family chat",
  "member_ids": ["usr_456", "usr_789", "usr_012"]
}

Response (201 Created):
{
  "success": true,
  "data": {
    "group_id": "grp_abc123",
    "name": "Family Group",
    "admin_ids": ["usr_123"],
    "member_count": 4,
    "created_at": "2025-01-08T14:00:00Z"
  }
}
```

#### 11. Send Group Message
```
WebSocket Message:
{
  "action": "send_group_message",
  "data": {
    "temp_id": "temp_123",
    "group_id": "grp_abc123",
    "encrypted_content": "base64_data",
    "message_type": "TEXT"
  }
}

Server Response:
{
  "action": "message_ack",
  "data": {
    "temp_id": "temp_123",
    "message_id": "msg_grp_xyz",
    "delivered_to": 200,
    "total_members": 256,
    "status": "SENT"
  }
}
```

#### 12. Add Group Member
```
POST /api/v1/groups/{group_id}/members

Request:
{
  "user_ids": ["usr_999"]
}

Response (200 OK):
{
  "success": true,
  "data": {
    "group_id": "grp_abc123",
    "member_count": 257,
    "added_members": [
      {
        "user_id": "usr_999",
        "display_name": "New Member"
      }
    ]
  }
}
```

### Media APIs

#### 13. Request Media Upload URL
```
POST /api/v1/media/upload/init

Request:
{
  "media_type": "IMAGE",
  "file_size": 5242880,
  "mime_type": "image/jpeg"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "media_id": "media_abc123",
    "upload_url": "https://s3.presigned.url",
    "expires_in": 900
  }
}
```

#### 14. Confirm Upload
```
POST /api/v1/media/upload/complete

Request:
{
  "media_id": "media_abc123",
  "s3_key": "media/usr_123/2025-01/uuid.jpg"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "media_id": "media_abc123",
    "media_url": "https://cdn.whatsapp.com/...",
    "thumbnail_url": "https://cdn.whatsapp.com/.../thumb.jpg"
  }
}
```

### Presence APIs

#### 15. Update Presence (WebSocket)
```
Client → Server (on app open):
{
  "action": "presence_update",
  "data": {
    "status": "ONLINE"
  }
}

Client → Server (on app close):
{
  "action": "presence_update",
  "data": {
    "status": "OFFLINE"
  }
}

Server → Contacts (broadcast):
{
  "action": "contact_presence",
  "data": {
    "user_id": "usr_789",
    "status": "ONLINE",
    "last_seen": null
  }
}
```

#### 16. Heartbeat (WebSocket)
```
Client → Server (every 30 seconds):
{
  "action": "PING",
  "timestamp": 1704672000000
}

Server → Client:
{
  "action": "PONG",
  "timestamp": 1704672001000,
  "server_time": 1704672001000
}
```

---

## 4️⃣ Data Flow

### Flow 1: User Registration with OTP
```
1. User enters phone number in app
   ↓
2. App sends: POST /api/v1/auth/register
   ↓
3. User Service:
   - Validate phone number format
   - Check if already registered
   - Generate 6-digit OTP (random)
   - Store OTP in Redis (5 min TTL)
   ↓
4. Send OTP via SMS (Twilio/AWS SNS)
   ↓
5. Return verification_id to client
   ↓
6. User enters OTP in app
   ↓
7. App sends: POST /api/v1/auth/verify with OTP
   ↓
8. User Service validates OTP (Redis lookup)
   ↓
9. Create user account:
   - Generate user_id (UUID)
   - Hash phone number (SHA-256)
   - Create profile with defaults
   - Insert into MySQL
   ↓
10. Generate JWT tokens (access 1hr, refresh 7 days)
    ↓
11. Return tokens to client
    ↓
12. Client establishes WebSocket connection
    ↓
13. Sync contacts (get WhatsApp users from contact list)
```

### Flow 2: Send 1:1 Message (Both Online)
```
ALICE'S DEVICE:
1. Alice types "Hello" and hits send
   ↓
2. App encrypts message (Signal Protocol)
   - Uses session key with Bob
   - AES-256-GCM encryption
   ↓
3. Send via WebSocket to gateway
   {
     "action": "send_message",
     "temp_id": "temp_123",
     "receiver_id": "bob_id",
     "encrypted_content": "...",
     "timestamp": 1704672000
   }
   ↓
4. Show single tick ✓ (SENT) optimistically

WHATSAPP SERVER:
5. WebSocket gateway receives message (T=10ms)
   ↓
6. Forward to Message Service
   ↓
7. Message Service:
   - Validate sender permissions
   - Generate conversation_id (deterministic)
   - Generate message_id (Snowflake/timeuuid)
   - Check if Bob online (Redis lookup)
   - Result: Bob is ONLINE
   ↓
8. Lookup Bob's WebSocket gateway (Redis)
   - Bob connected to ws-gateway-27
   ↓
9. Publish to Redis Pub/Sub:
   - Channel: "messages:ws-gateway-27"
   - Payload: {message_id, receiver_id: bob, content}
   ↓
10. Send ACK to Alice (T=20ms)
    - Replace temp_id with server message_id
    - Status: SENT
    ↓
11. Async: Publish to Kafka for persistence
    - Topic: "messages.created"
    - Non-blocking

BOB'S DEVICE:
12. ws-gateway-27 receives from Redis Pub/Sub (T=25ms)
    ↓
13. Push to Bob's WebSocket connection
    ↓
14. Bob's app receives message (T=30ms)
    ↓
15. Bob's app decrypts message
    - Uses session key with Alice
    - Decrypts: "Hello"
    ↓
16. Display message in chat
    ↓
17. Send DELIVERED ACK to server
    {
      "action": "delivery_ack",
      "message_id": "msg_abc"
    }
    ↓
18. Server updates status: DELIVERED
    ↓
19. Server notifies Alice: Double tick ✓✓ (T=40ms)

BOB OPENS CHAT:
20. Bob taps on chat with Alice (T=5000ms)
    ↓
21. Bob's app sends READ receipt
    {
      "action": "read_receipt",
      "message_id": "msg_abc"
    }
    ↓
22. Server updates status: READ
    ↓
23. Server notifies Alice: Blue ticks ✓✓ (blue)

PERSISTENCE (Async):
24. Kafka consumer writes to Cassandra (T=50ms)
    ↓
25. Message permanently stored
    ↓
26. Replicated to 3 nodes

TOTAL END-TO-END LATENCY:
- Alice sees SENT (✓): 20ms
- Alice sees DELIVERED (✓✓): 40ms
- Alice sees READ (blue ✓✓): 5000ms (when Bob opens)
```

### Flow 3: Send Message (Receiver Offline)
```
1. Alice sends "Hello" to Charlie
   ↓
2. Server processing (same as Flow 2)
   ↓
3. Check Charlie's status (Redis): OFFLINE
   ↓
4. Server actions:
   a. Store message in Cassandra
   b. Add to undelivered queue (Redis):
      LPUSH undelivered:charlie msg_xyz
   c. Retrieve Charlie's push tokens (from cache/DB)
   d. Send push notification (FCM/APNS)
   e. Return SENT status to Alice (single tick ✓)
   ↓
5. Charlie receives push notification
   "New message from Alice"
   ↓
6. Charlie opens WhatsApp (T=3600s, 1 hour later)
   ↓
7. App establishes WebSocket connection
   ↓
8. Server detects Charlie online:
   - Retrieve undelivered queue
   - LRANGE undelivered:charlie 0 -1
   - Result: [msg_xyz, msg_abc, ...]
   ↓
9. Server delivers queued messages in order:
   - Push each message via WebSocket
   - Wait for DELIVERED ACK
   - Remove from queue: LREM undelivered:charlie
   - Update status: DELIVERED
   ↓
10. Notify Alice: Double ticks ✓✓
    ↓
11. Charlie opens chat with Alice
    ↓
12. Charlie's app sends READ receipts
    ↓
13. Notify Alice: Blue ticks ✓✓ (blue)
```

### Flow 4: Send Group Message
```
1. Alice sends "Hi everyone" to Family Group (256 members)
   ↓
2. App encrypts message (group session key)
   ↓
3. Send via WebSocket
   {
     "action": "send_group_message",
     "group_id": "grp_family",
     "encrypted_content": "...",
     "message_type": "TEXT"
   }
   ↓
4. Message Service:
   - Validate Alice is group member
   - Generate message_id
   - Store message ONCE in Cassandra
   - Publish to Kafka: "group.message.fanout"
   ↓
5. Return ACK to Alice (single tick ✓)

ASYNC FANOUT (via Kafka consumer):
6. Fanout Worker picks up event
   ↓
7. Fetch group members (cached in Redis SET):
   - SMEMBERS group:grp_family:members
   - Result: 256 user IDs
   ↓
8. For each member (parallel processing):
   a. Skip sender (Alice)
   b. Check online status (Redis)
   c. If ONLINE (150 members):
      - Lookup WebSocket gateway
      - Push message via gateway
      - Mark as DELIVERED
   d. If OFFLINE (106 members):
      - Add to undelivered queue
      - Send push notification
      - Mark as SENT
   ↓
9. Update delivery status:
   - Store receipt per member in Cassandra
   - Delivered: 150/255
   ↓
10. Notify Alice: "Delivered to 150/255"
    ↓
11. As members read message:
    - Track read receipts
    - "Read by 80/255"
    ↓
12. Alice can tap to see who read (member list)

TOTAL TIME: 100-150ms for complete fanout
```

### Flow 5: Establish WebSocket Connection
```
1. User opens WhatsApp app
   ↓
2. App initiates WebSocket connection
   - wss://gateway.whatsapp.com/ws
   ↓
3. TLS handshake (certificate validation)
   ↓
4. WebSocket upgrade (HTTP → WS)
   ↓
5. Client sends authentication:
   {
     "action": "authenticate",
     "access_token": "jwt_token",
     "device_id": "device_xyz"
   }
   ↓
6. Gateway validates JWT:
   - Decode token
   - Check expiry
   - Verify signature
   ↓
7. Create session in Redis:
   - session:{user_id}:{device_id}
   - Store gateway server, connection_id
   - TTL: 70 seconds
   ↓
8. Update presence: ONLINE
   - SETEX online:{user_id} 70 1
   ↓
9. Retrieve undelivered messages:
   - LRANGE undelivered:{user_id} 0 -1
   ↓
10. Deliver queued messages to client
    ↓
11. Start heartbeat (ping every 30s)
    ↓
12. Connection established (T=1000ms total)
```

### Flow 6: Media Upload & Sharing
```
1. Alice selects photo to send
   ↓
2. App encrypts photo locally (E2EE)
   - Generate encryption key
   - Encrypt with AES-256
   ↓
3. Request upload URL:
   POST /api/v1/media/upload/init
   ↓
4. Server generates pre-signed S3 URL
   - Unique key with UUID
   - 15 minute expiry
   ↓
5. App uploads encrypted file directly to S3
   - Parallel chunked upload
   - Progress indicator shown
   ↓
6. Upload completes (T=2000ms for 5MB)
   ↓
7. App confirms: POST /api/v1/media/upload/complete
   ↓
8. Trigger async processing (Lambda):
   - Generate thumbnail (encrypted)
   - Extract metadata
   - Compress if needed
   - Store processed versions
   ↓
9. Send message with media_url:
   {
     "action": "send_message",
     "receiver_id": "bob_id",
     "message_type": "IMAGE",
     "media_url": "s3_key",
     "encryption_key": "key_for_bob"
   }
   ↓
10. Bob receives message notification
    ↓
11. Bob's app downloads encrypted media from CDN
    ↓
12. Decrypts locally and displays
```

### Flow 7: Typing Indicator
```
1. Alice starts typing in chat with Bob
   ↓
2. After 1 second of typing, app sends:
   {
     "action": "typing",
     "conversation_id": "conv_alice_bob",
     "status": "TYPING"
   }
   ↓
3. Server stores in Redis:
   - SETEX typing:conv_alice_bob:alice 5 1
   ↓
4. Check if Bob is online
   ↓
5. If Bob online: Broadcast to Bob
   {
     "action": "user_typing",
     "user_id": "alice_id",
     "conversation_id": "conv_alice_bob"
   }
   ↓
6. Bob's app shows: "Alice is typing..."
   ↓
7. Auto-expires after 5 seconds (TTL)
   ↓
8. Alice stops typing or sends message:
   - Send "STOPPED" status
   - Delete Redis key
   - Bob's indicator disappears
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
                      [Mobile/Web/Desktop Clients]
                      iOS | Android | Web | Desktop
                                    |
                                    ↓ (WSS/HTTPS)
                    ┌───────────────────────────────┐
                    │   CDN - CloudFront/Akamai     │
                    │   - Profile pictures          │
                    │   - Media files (encrypted)   │
                    │   - 90% cache hit             │
                    └───────────────┬───────────────┘
                                    |
                                    ↓
                         [DNS - Route53]
                         Geographic routing
                                    |
                                    ↓
                    [Load Balancer - ALB]
                    Multi-region, SSL termination
                                    |
                    ┌───────────────┴───────────────┐
                    ↓                               ↓
        [HTTP API Layer]              [WebSocket Gateway Layer]
        RESTful APIs:                 Persistent Connections:
        • Registration                • Real-time messaging
        • Profile updates             • Presence updates
        • Media URLs                  • Typing indicators
        • Group management            • 500M connections
                    |                               |
                    └───────────────┬───────────────┘
                                    |
                            [API Gateway]
                            - JWT validation
                            - Rate limiting
                            - Protocol routing
                                    |
                ┌───────────────────┼───────────────────┐
                ↓                   ↓                   ↓
        [Message Service]   [User Service]    [Group Service]
        - Send/receive      - Auth/profile    - Group ops
        - Status tracking   - Contact sync    - Member mgmt
        - Validation        - Presence        - Fanout logic
                |                   |                   |
                └───────────────────┼───────────────────┘
                                    |
                        ┌───────────┴───────────┐
                        ↓                       ↓
                [Cache Layer]           [Message Queue]
                Redis Cluster           Apache Kafka
                • Sessions (70s)        • messages.created
                • Presence (70s)        • messages.delivered
                • Undelivered queue     • messages.read
                • Group members         • group.fanout
                5TB distributed         1.15M msg/sec
                                    |
            ┌───────────────────────┼───────────────────┐
            ↓                       ↓                   ↓
    [User DB]               [Message DB]          [Search]
    MySQL                   Cassandra Cluster     Elasticsearch
    Master                  • 1:1 messages        (Optional)
    + 3 replicas            • Group messages      User/message
    Sharded                 • Receipts            search
    by user_id              Write-optimized
    4TB                     Multi-DC replication
                                    |
                ┌───────────────────┴───────────────────┐
                ↓                                       ↓
        [Object Storage]                    [Background Workers]
        S3 - Encrypted Media                Kafka Consumers:
        • Images (encrypted)                • Delivery Worker
        • Videos (encrypted)                • Notification Worker
        • Documents                         • Media Processor
        • Voice messages                    • Analytics Worker
        2.6 EB/year                         
                                    |
                        ┌───────────┴───────────┐
                        ↓                       ↓
                [Push Services]         [Monitoring]
                FCM/APNS                Prometheus/Grafana
                Offline delivery        ELK Stack
```

### Component Details

#### 1. WebSocket Gateway (Stateful)
**Purpose**: Maintain persistent real-time connections

**Architecture**:
- 16,000 gateway servers globally
- Each handles 65K concurrent connections
- Stateful (connection state per user)
- Geographic distribution for low latency

**Features**:
- Persistent bidirectional communication
- Automatic reconnection handling
- Heartbeat mechanism (30s ping/pong)
- Message queueing during brief disconnects
- Connection pooling and management

**Load Distribution**:
```
Strategy: Geographic + Load-based routing

User in India:
  → Routed to ap-south-1 gateway cluster
  → Nearest available gateway server
  → Latency: <50ms

Connection tracking:
  Redis key: session:{user_id}:{device_id}
  Value: {gateway_server, connection_id}
```

#### 2. Message Service
**Purpose**: Core message handling logic

**Responsibilities**:
- Validate message format and permissions
- Generate unique message IDs (timeuuid)
- Determine message routing (online/offline)
- Update message status (sent/delivered/read)
- Handle message deletion
- Manage conversation metadata

**Message ID**: Timeuuid (time-sortable UUID)
- Ensures message ordering
- Globally unique
- Contains timestamp for sorting

#### 3. User Service
**Purpose**: User account and profile management

**Features**:
- Phone number verification (OTP via SMS)
- JWT token generation and validation
- Profile CRUD operations
- Contact list management
- Privacy settings enforcement
- Block/unblock functionality

#### 4. Group Service
**Purpose**: Group chat management

**Features**:
- Create/delete groups
- Add/remove members (with permissions)
- Admin role management
- Group metadata updates
- Member list caching
- Group message fanout coordination

#### 5. Presence Service
**Purpose**: Track user online/offline status

**Implementation**:
```
Online Status (Redis):
  Key: online:{user_id}
  Value: 1
  TTL: 70 seconds (refreshed by heartbeats)

Last Seen (Redis + MySQL):
  Redis (hot data): 7 day TTL
  MySQL (permanent): Batched updates every 5 min

Privacy Enforcement:
  Before returning status:
    - Check requester relationship
    - Check user's privacy settings
    - Return accordingly
```

#### 6. Cache Layer (Redis Cluster)

**What We Cache**:
```
Sessions (70s TTL):
  session:{user_id}:{device_id}
  - Gateway server
  - Connection ID
  - Last heartbeat

Presence (70s TTL):
  online:{user_id} → 1
  last_seen:{user_id} → timestamp

Undelivered Messages (30 day TTL):
  undelivered:{user_id} → [msg_ids]

Group Members (1 hour TTL):
  group:{group_id}:members → Set of user_ids

User Profiles (1 hour TTL):
  user:{user_id}:profile → JSON data
```

**Cluster Configuration**:
- 200 nodes × 25GB = 5TB total
- 5 master shards
- 2 replicas per shard
- Redis Sentinel for automatic failover

#### 7. Databases

**A. User Database (MySQL)**
- Purpose: User accounts, contacts, devices
- Sharding: By user_id (100 shards)
- Replication: Master + 3 read replicas
- Size: ~4TB
- Queries: Auth, profile, contacts

**B. Message Database (Cassandra)**
- Purpose: All messages (1:1 and group)
- Partitioning: By conversation_id/group_id
- Replication Factor: 3
- Write throughput: 1.15M writes/sec
- Consistency: Quorum

**C. Object Storage (S3)**
- Purpose: Encrypted media files
- Size: 2.6 EB per year
- Lifecycle: 30 days standard, then glacier
- CDN: CloudFront integration

#### 8. Message Queue (Kafka)

**Topics**:
```
messages.created (high volume):
- All message creation events
- 1.15M msgs/sec average

messages.delivered:
- Delivery acknowledgments
- 1M msgs/sec

messages.read:
- Read receipts
- 500K msgs/sec

group.message.fanout:
- Group message fanout events
- 50K msgs/sec

presence.updated:
- User presence changes
- 100K msgs/sec
```

**Configuration**:
- 20 Kafka brokers
- 3 replicas per partition
- 7 day retention
- Compression: LZ4

#### 9. Background Workers

**Delivery Worker**:
- Consumes from Kafka
- Handles message fanout
- Updates delivery status
- 100 worker instances

**Notification Worker**:
- Sends push notifications (FCM/APNS)
- Handles batching for groups
- Retry logic (3 attempts)
- 50 worker instances

**Media Processor**:
- Generates thumbnails
- Compresses media
- Encrypts processed versions
- 20 worker instances

---

## 6️⃣ Deep Dives

### Deep Dive 1: End-to-End Encryption (Signal Protocol)

**Why E2EE?**
- Server cannot read message content
- Privacy guarantee for users
- Even if server compromised, messages safe

**Signal Protocol Components**:

```
1. Identity Keys (Long-term):
   - Generated once per device
   - Public key uploaded to server
   - Private key never leaves device
   - Used to authenticate users

2. Signed Pre-Keys:
   - Medium-term keys (1 week lifespan)
   - Signed with identity key
   - Uploaded to server (100 at a time)
   - Used for initial handshake

3. One-Time Pre-Keys:
   - Used once and destroyed
   - Uploaded to server in batches
   - Provides perfect forward secrecy

4. Session Keys:
   - Derived from handshake
   - Used for actual message encryption
   - Rotated after 1000 messages
```

**Initial Handshake (Alice → Bob, first message)**:

```python
class SignalProtocolHandler:
    def initiate_session(self, alice_device, bob_user_id):
        """Alice initiates session with Bob"""
        
        # 1. Fetch Bob's keys from server
        bob_keys = server.get_user_keys(bob_user_id)
        # Returns: identity_key, signed_pre_key, one_time_pre_key
        
        # 2. Perform X3DH (Extended Triple Diffie-Hellman)
        shared_secret = self.x3dh_handshake(
            alice_device.identity_key,
            alice_device.ephemeral_key,
            bob_keys.identity_key,
            bob_keys.signed_pre_key,
            bob_keys.one_time_pre_key
        )
        
        # 3. Derive session keys using KDF
        session_key = hkdf(shared_secret, info="WhatsAppSession")
        
        # 4. Store session
        alice_device.store_session(bob_user_id, session_key)
        
        # 5. Encrypt message
        encrypted = aes_256_gcm_encrypt(
            plaintext="Hello",
            key=session_key,
            nonce=generate_nonce()
        )
        
        # 6. Send message with handshake data
        message = {
            "encrypted_content": encrypted,
            "handshake_data": {
                "alice_identity_key": alice_device.identity_key.public,
                "alice_ephemeral_key": alice_device.ephemeral_key.public,
                "used_pre_key_id": bob_keys.one_time_pre_key.id
            }
        }
        
        return message
    
    def receive_initial_message(self, bob_device, message):
        """Bob receives Alice's first message"""
        
        # 1. Extract handshake data
        handshake = message.handshake_data
        
        # 2. Retrieve own pre-keys
        signed_pre_key = bob_device.get_signed_pre_key()
        one_time_pre_key = bob_device.get_one_time_pre_key(
            handshake.used_pre_key_id
        )
        
        # 3. Perform X3DH to derive shared secret
        shared_secret = self.x3dh_handshake(
            bob_device.identity_key,
            signed_pre_key,
            handshake.alice_identity_key,
            handshake.alice_ephemeral_key
        )
        
        # 4. Derive session key (same as Alice's)
        session_key = hkdf(shared_secret, info="WhatsAppSession")
        
        # 5. Store session
        bob_device.store_session(alice_user_id, session_key)
        
        # 6. Decrypt message
        plaintext = aes_256_gcm_decrypt(
            message.encrypted_content,
            session_key
        )
        
        # 7. Delete used one-time pre-key (forward secrecy)
        bob_device.delete_one_time_pre_key(handshake.used_pre_key_id)
        
        return plaintext  # "Hello"
```

**Subsequent Messages**:
```python
def send_message(self, plaintext, receiver_id):
    """Send message after session established"""
    
    # 1. Get existing session
    session = self.get_session(receiver_id)
    
    # 2. Encrypt with session key
    encrypted = aes_256_gcm_encrypt(
        plaintext=plaintext,
        key=session.key,
        nonce=generate_nonce()
    )
    
    # 3. Increment message counter
    session.message_counter += 1
    
    # 4. Check if need to rotate keys
    if session.message_counter >= 1000:
        self.rotate_session_keys(receiver_id)
    
    return encrypted
```

**Perfect Forward Secrecy**:
```
Mechanism: Key rotation

After 1000 messages or 1 week:
  1. Generate new ephemeral key
  2. Perform DH key exchange
  3. Derive new session key
  4. Delete old session key securely
  
Result: Past messages can't be decrypted even if current key compromised
```

### Deep Dive 2: WebSocket at Scale (500M Connections)

**The C10K Problem** (solved):
```
Traditional: One thread per connection
  - 10K connections = 10K threads
  - High memory usage (1MB per thread)
  - Context switching overhead

Modern Solution: Event-driven I/O
  - Single thread handles 65K+ connections
  - Epoll (Linux), kqueue (BSD), IOCP (Windows)
  - Non-blocking I/O
  - Event loop architecture
```

**Gateway Server Architecture**:

```python
class WebSocketGateway:
    def __init__(self):
        self.connections = {}  # connection_id → Connection object
        self.user_connections = {}  # user_id → [connection_ids]
        self.event_loop = EventLoop()
        
    async def handle_connection(self, websocket):
        """Handle new WebSocket connection"""
        
        try:
            # 1. Authenticate
            auth_msg = await websocket.receive()
            user_id = self.authenticate(auth_msg)
            
            # 2. Register connection
            conn_id = self.register_connection(user_id, websocket)
            
            # 3. Update presence
            await self.set_online(user_id)
            
            # 4. Deliver queued messages
            await self.deliver_queued_messages(user_id, websocket)
            
            # 5. Start heartbeat
            asyncio.create_task(self.heartbeat_loop(conn_id))
            
            # 6. Message loop
            async for message in websocket:
                await self.handle_message(user_id, message)
                
        except Exception as e:
            logger.error(f"Connection error: {e}")
        finally:
            # Cleanup on disconnect
            await self.cleanup_connection(conn_id, user_id)
    
    async def heartbeat_loop(self, conn_id):
        """Send heartbeats to keep connection alive"""
        
        while conn_id in self.connections:
            try:
                # Send PING
                await self.connections[conn_id].send({"action": "PING"})
                
                # Wait for PONG (timeout 10s)
                pong = await asyncio.wait_for(
                    self.connections[conn_id].receive(),
                    timeout=10
                )
                
                if pong.get("action") != "PONG":
                    raise TimeoutError("No PONG received")
                
                # Update last heartbeat in Redis
                redis.hset(
                    f"session:{conn_id}",
                    "last_heartbeat",
                    time.time()
                )
                
                # Wait 30 seconds
                await asyncio.sleep(30)
                
            except TimeoutError:
                logger.warning(f"Heartbeat timeout for {conn_id}")
                await self.close_connection(conn_id)
                break
```

**Connection State Management**:

```python
def register_connection(self, user_id, device_id, gateway_server):
    """Register new WebSocket connection"""
    
    # Generate unique connection ID
    conn_id = f"conn_{uuid.uuid4()}"
    
    # Store in Redis
    session_data = {
        "user_id": user_id,
        "device_id": device_id,
        "gateway_server": gateway_server,
        "connected_at": time.time(),
        "last_heartbeat": time.time()
    }
    
    redis.hmset(f"session:{conn_id}", session_data)
    redis.expire(f"session:{conn_id}", 70)  # Auto-cleanup
    
    # Add to user's connections
    redis.sadd(f"user_connections:{user_id}", conn_id)
    
    return conn_id

def route_message_to_user(self, user_id, message):
    """Route message to user's active connection(s)"""
    
    # Get all active connections for user
    conn_ids = redis.smembers(f"user_connections:{user_id}")
    
    for conn_id in conn_ids:
        # Get gateway server for this connection
        session = redis.hgetall(f"session:{conn_id}")
        gateway = session['gateway_server']
        
        # Publish to that gateway's Redis channel
        redis.publish(
            f"messages:{gateway}",
            json.dumps({
                "connection_id": conn_id,
                "message": message
            })
        )
```

**Scaling Pattern**:
```
Horizontal scaling:
  - Add more gateway servers
  - Load balancer distributes new connections
  - Redis tracks which user on which gateway
  - Messages routed via Redis Pub/Sub

Vertical scaling:
  - Optimize event loop (epoll tuning)
  - Increase file descriptor limits
  - Use more CPU cores per server
```

### Deep Dive 3: Message Delivery Guarantees

**At-Least-Once Delivery**:

```
Guarantee: Message will be delivered, may be delivered multiple times

Implementation:
  1. Client sends message with temp_id
  2. Server generates message_id
  3. Store in Cassandra (durable)
  4. Attempt delivery to recipient
  5. If delivery fails:
     - Keep in undelivered queue
     - Retry on reconnect
     - Client may resend if no ACK (creates duplicate)
  6. Server/Client use message_id for deduplication

Idempotency:
  - Cassandra PRIMARY KEY (conversation_id, message_id)
  - Duplicate inserts ignored
  - Client tracks received message_ids
  - Shows message only once even if received twice
```

**Message Ordering**:

```python
class MessageOrderer:
    """Ensure messages displayed in correct order"""
    
    def __init__(self):
        self.buffer = []  # Temporary buffer
        self.last_displayed = None
    
    def receive_message(self, message):
        """Buffer and reorder messages"""
        
        # Add to buffer
        self.buffer.append(message)
        
        # Sort by message_id (timeuuid is sortable)
        self.buffer.sort(key=lambda m: m.message_id)
        
        # Display all consecutive messages
        messages_to_display = []
        
        for msg in self.buffer:
            if self.last_displayed is None or \
               msg.message_id > self.last_displayed:
                messages_to_display.append(msg)
                self.last_displayed = msg.message_id
            else:
                break  # Gap detected, wait for missing message
        
        # Remove displayed from buffer
        self.buffer = [m for m in self.buffer 
                       if m.message_id > self.last_displayed]
        
        return messages_to_display
```

**Handling Duplicates**:

```python
class DuplicateFilter:
    """Prevent showing duplicate messages"""
    
    def __init__(self):
        # Keep last 1000 message IDs per conversation
        self.seen_messages = {}  # conv_id → deque of message_ids
    
    def is_duplicate(self, conversation_id, message_id):
        """Check if message already received"""
        
        if conversation_id not in self.seen_messages:
            self.seen_messages[conversation_id] = deque(maxlen=1000)
        
        seen = self.seen_messages[conversation_id]
        
        if message_id in seen:
            return True  # Duplicate
        
        # Not duplicate, add to seen
        seen.append(message_id)
        return False
```

### Deep Dive 4: Group Message Fanout

**Challenge**: Efficiently deliver message to 256 members

**Naive Approach (Sequential)**:
```
For each of 256 members:
  - Check if online
  - Deliver message
  - Wait for ACK
  
Time: 256 × 50ms = 12,800ms ❌ Too slow!
```

**Optimized Approach (Parallel)**:

```python
class GroupFanoutService:
    def __init__(self):
        self.max_workers = 50  # Parallel workers
    
    async def fanout_group_message(self, group_id, message_id):
        """Deliver message to all group members"""
        
        # 1. Fetch group members (cached)
        members = await redis.smembers(f"group:{group_id}:members")
        # Returns: Set of 256 user_ids
        
        # 2. Split into online and offline
        online_members, offline_members = await self.classify_members(members)
        
        # 3. Parallel fanout
        async with asyncio.TaskGroup() as tg:
            # Online members (parallel push)
            for member_id in online_members:
                tg.create_task(
                    self.deliver_to_online_user(member_id, message_id)
                )
            
            # Offline members (queue + notification)
            for member_id in offline_members:
                tg.create_task(
                    self.queue_for_offline_user(member_id, message_id)
                )
        
        # 4. Collect results
        delivered_count = len(online_members)
        sent_count = len(offline_members)
        
        # 5. Update sender: "Delivered to X/Y"
        await self.notify_sender(
            message_id,
            delivered=delivered_count,
            sent=sent_count,
            total=len(members)
        )
        
        # 6. Store delivery receipts
        await cassandra.batch_insert(
            "group_message_receipts",
            [(group_id, message_id, member_id, status) 
             for member_id, status in results]
        )

Total Time: ~100-150ms for 256 members
```

**Optimization: Batched Push Notifications**:

```python
def send_group_notifications(group_id, message_id, offline_members):
    """Send push notifications in batches"""
    
    # Don't send individual notification for each offline member
    # Send one notification per member, but batch the API calls
    
    batch_size = 100
    batches = [offline_members[i:i+batch_size] 
               for i in range(0, len(offline_members), batch_size)]
    
    for batch in batches:
        # Prepare batch notification payload
        notifications = []
        
        for member_id in batch:
            device_tokens = get_device_tokens(member_id)
            
            for token in device_tokens:
                notifications.append({
                    "token": token,
                    "title": f"Family Group",
                    "body": "Alice: New message",
                    "data": {
                        "group_id": group_id,
                        "message_id": message_id
                    }
                })
        
        # Send batch to FCM/APNS (single API call)
        fcm.send_batch(notifications)
    
    # Batch 100 notifications per API call
    # Much faster than 106 individual calls
```

### Deep Dive 5: Presence & Last Seen

**Online Status Architecture**:

```
Components:
1. WebSocket connection = User is ONLINE
2. Heartbeat mechanism (30s ping)
3. Redis TTL for automatic cleanup (70s)

Flow:
┌─────────────────────────────────────┐
│ User opens app                      │
│ → Establish WebSocket               │
│ → Server: SETEX online:{user_id} 70 1│
│ → Broadcast to user's contacts      │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Every 30 seconds                    │
│ → Client sends PING                 │
│ → Server refreshes TTL (70s)        │
│ → Server sends PONG                 │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ User closes app / network dies      │
│ → Heartbeat stops                   │
│ → Redis key expires (after 70s)     │
│ → Presence changes to OFFLINE       │
│ → Set last_seen timestamp           │
│ → Broadcast to contacts             │
└─────────────────────────────────────┘
```

**Last Seen Privacy**:

```python
def get_last_seen(requester_id, target_user_id):
    """Get user's last seen with privacy check"""
    
    # 1. Get target user's privacy setting
    privacy = db.query(
        "SELECT privacy_last_seen FROM users WHERE user_id = ?",
        target_user_id
    )[0]
    
    # 2. Check if requester can see
    can_see = False
    
    if privacy == 'everyone':
        can_see = True
    elif privacy == 'contacts':
        # Check if requester is in target's contacts
        can_see = is_contact(target_user_id, requester_id)
    elif privacy == 'nobody':
        can_see = False
    
    # 3. Return accordingly
    if can_see:
        # Check if currently online
        is_online = redis.get(f"online:{target_user_id}")
        
        if is_online:
            return {"status": "online", "last_seen": None}
        else:
            last_seen = redis.get(f"last_seen:{target_user_id}")
            return {"status": "offline", "last_seen": last_seen}
    else:
        return {"status": "hidden", "last_seen": None}
```

### Deep Dive 6: Multi-Device Support

**Challenge**: Sync messages across phone, web, tablet

**Architecture**:

```
Primary Device (Phone):
  - Stores encryption keys
  - Master device
  - Can add/remove linked devices

Linked Devices (Web, Tablet):
  - Paired with primary
  - Receive encryption keys from primary
  - Independent message delivery
```

**Pairing Flow (WhatsApp Web)**:

```
1. User opens web.whatsapp.com
   ↓
2. Web generates QR code:
   Q[ERROR] Failed to process response: The model returned the following errors: Input is too long for requested model.
