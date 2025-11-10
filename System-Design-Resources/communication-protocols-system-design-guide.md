# Communication Protocols - System Design Interview Guide

## Table of Contents
1. [Overview](#overview)
2. [HTTP/HTTPS & REST](#httphttps--rest)
3. [gRPC](#grpc)
4. [WebSocket](#websocket)
5. [GraphQL](#graphql)
6. [Server-Sent Events (SSE)](#server-sent-events-sse)
7. [Long Polling](#long-polling)
8. [Message Queues (Kafka, RabbitMQ)](#message-queues)
9. [MQTT](#mqtt)
10. [TCP vs UDP](#tcp-vs-udp)
11. [Protocol Comparison Matrix](#protocol-comparison-matrix)
12. [Top 10 HLD Questions with Protocol Choices](#top-10-hld-questions-with-protocol-choices)
13. [Decision Framework](#decision-framework)

---

## Overview

### Why Protocol Choice Matters

```
Wrong Protocol Choice:
├── Poor performance (latency, throughput)
├── Inefficient bandwidth usage
├── Scalability issues
├── Complex implementation
└── Higher operational costs

Right Protocol Choice:
├── Optimal performance
├── Efficient resource usage
├── Natural fit for use case
├── Simpler implementation
└── Better user experience
```

### Key Decision Factors

```
1. Communication Pattern:
   - Request-Response (HTTP, gRPC)
   - Bidirectional Streaming (WebSocket)
   - Server Push (SSE, WebSocket)
   - Asynchronous Messaging (Kafka, RabbitMQ)

2. Performance Requirements:
   - Latency (<100ms, <10ms, <1ms)
   - Throughput (1K QPS, 100K QPS, 1M QPS)
   - Bandwidth constraints

3. Data Format:
   - JSON (human-readable)
   - Protocol Buffers (binary, compact)
   - Plain text
   - Binary data

4. Client Types:
   - Web browsers
   - Mobile apps
   - Server-to-server
   - IoT devices

5. Scale & Architecture:
   - Microservices communication
   - Client-server
   - Peer-to-peer
   - Event-driven
```

---

## HTTP/HTTPS & REST

### What is HTTP/REST?

```
HTTP: Hypertext Transfer Protocol
├── Stateless request-response protocol
├── Built on TCP
├── Port 80 (HTTP), 443 (HTTPS)
└── Foundation of the web

REST: Representational State Transfer
├── Architectural style using HTTP
├── Resource-based URLs
├── Standard HTTP methods (GET, POST, PUT, DELETE)
└── Stateless communication
```

### Characteristics

```
✓ Universal support (browsers, apps, servers)
✓ Human-readable (JSON/XML)
✓ Stateless and cacheable
✓ Simple to implement and debug
✓ Extensive tooling and libraries

✗ Overhead (headers, JSON parsing)
✗ Text-based (larger payload)
✗ Half-duplex (one direction at a time)
✗ Polling required for real-time updates
```

### When to Use HTTP/REST

```
✅ Perfect For:
├── Public APIs (easy integration)
├── CRUD operations (Create, Read, Update, Delete)
├── Web applications (browser compatibility)
├── Mobile apps (standard HTTP clients)
├── Microservices (when low latency not critical)
└── Rate-limited APIs (easy to implement)

❌ Not Ideal For:
├── Real-time bidirectional communication
├── High-frequency updates (>10/second)
├── Low-latency requirements (<10ms)
├── Streaming large datasets
└── Binary data transfer
```

### REST API Design Best Practices

```
Resource-Based URLs:
GET    /api/v1/users           # List users
GET    /api/v1/users/123       # Get specific user
POST   /api/v1/users           # Create user
PUT    /api/v1/users/123       # Update user
DELETE /api/v1/users/123       # Delete user

HTTP Status Codes:
200 OK                  # Successful GET, PUT, PATCH
201 Created             # Successful POST
204 No Content          # Successful DELETE
400 Bad Request         # Invalid input
401 Unauthorized        # Authentication required
403 Forbidden           # Authenticated but no permission
404 Not Found           # Resource doesn't exist
429 Too Many Requests   # Rate limit exceeded
500 Internal Server Error # Server error
503 Service Unavailable  # Server overloaded

Response Format:
{
  "data": { ... },
  "meta": {
    "page": 1,
    "total": 100
  },
  "errors": []
}
```

### HTTP/2 & HTTP/3 Improvements

```
HTTP/1.1:
├── One request per TCP connection
├── Head-of-line blocking
└── No server push

HTTP/2 (2015):
├── Multiplexing (multiple requests over single connection)
├── Header compression (HPACK)
├── Server push capability
├── Binary protocol (more efficient)
└── Backward compatible

HTTP/3 (2022):
├── Built on QUIC (UDP-based)
├── No head-of-line blocking
├── Faster connection establishment (0-RTT)
├── Better for mobile (handles network switching)
└── ~30% faster than HTTP/2

Interview Tip: Mention HTTP/2 for modern systems
```

---

## gRPC

### What is gRPC?

```
gRPC: Google Remote Procedure Call
├── High-performance RPC framework
├── Uses Protocol Buffers (binary format)
├── Built on HTTP/2
├── Supports 11 languages
└── Used heavily in microservices
```

### Protocol Buffers Example

```protobuf
// user.proto
syntax = "proto3";

message User {
  int64 user_id = 1;
  string name = 2;
  string email = 3;
  int32 age = 4;
}

message GetUserRequest {
  int64 user_id = 1;
}

message GetUserResponse {
  User user = 1;
}

service UserService {
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
  rpc ListUsers(ListUsersRequest) returns (stream User);
  rpc CreateUser(stream CreateUserRequest) returns (CreateUserResponse);
  rpc Chat(stream ChatMessage) returns (stream ChatMessage);
}
```

### Communication Patterns

```
1. Unary RPC (Request-Response):
   Client → Server: Single request
   Server → Client: Single response
   Like traditional REST API

2. Server Streaming:
   Client → Server: Single request
   Server → Client: Stream of responses
   Example: Download large file, live data feed

3. Client Streaming:
   Client → Server: Stream of requests
   Server → Client: Single response
   Example: File upload, batch data ingestion

4. Bidirectional Streaming:
   Client ↔ Server: Both send streams
   Example: Chat, real-time collaboration
```

### Performance Comparison

```
Payload Size (User Object):

JSON (REST):
{
  "user_id": 12345,
  "name": "John Doe",
  "email": "john@example.com",
  "age": 30
}
Size: 95 bytes

Protocol Buffers (gRPC):
Binary encoded
Size: 28 bytes

Result: 70% reduction in payload size

Latency Comparison (Microservices):
REST/JSON:  ~50ms per request
gRPC:       ~5-10ms per request
Result: 5-10x faster
```

### When to Use gRPC

```
✅ Perfect For:
├── Microservices communication (low latency)
├── Internal APIs (server-to-server)
├── High-performance systems (trading, gaming)
├── Real-time bidirectional streaming
├── Polyglot environments (multi-language)
└── Mobile to backend (battery efficient)

❌ Not Ideal For:
├── Public APIs (not browser-friendly)
├── Simple CRUD apps (overkill)
├── Browser-only clients (limited support)
├── Human-readable debugging needed
└── External 3rd party integrations
```

### gRPC vs REST Decision

```
Choose gRPC if:
- Internal microservices
- Performance critical (<10ms)
- Bidirectional streaming needed
- Strong typing required
- Multiple languages

Choose REST if:
- Public API
- Browser clients
- Simple CRUD
- Human-readable preferred
- External integrations
```

---

## WebSocket

### What is WebSocket?

```
WebSocket: Full-duplex communication protocol
├── Persistent TCP connection
├── Bidirectional (client ↔ server)
├── Low overhead after handshake
├── Port 80 (ws://), 443 (wss://)
└── Defined in RFC 6455
```

### How WebSocket Works

```
1. HTTP Upgrade Handshake:
   Client → Server:
   GET /chat HTTP/1.1
   Host: example.com
   Upgrade: websocket
   Connection: Upgrade
   Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
   Sec-WebSocket-Version: 13

   Server → Client:
   HTTP/1.1 101 Switching Protocols
   Upgrade: websocket
   Connection: Upgrade
   Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

2. Connection Established:
   Now both can send messages anytime
   No request-response pattern needed
   
3. Message Exchange:
   Client → Server: "Hello"
   Server → Client: "Hi there!"
   Server → Client: "New notification"
   (No client request needed)

4. Connection Close:
   Either side can close connection
   Graceful shutdown with close frame
```

### When to Use WebSocket

```
✅ Perfect For:
├── Real-time chat applications
├── Live notifications
├── Collaborative editing (Google Docs)
├── Multiplayer gaming
├── Live sports scores
├── Stock price updates
├── IoT device communication
└── Live dashboards

❌ Not Ideal For:
├── Simple request-response APIs
├── Infrequent updates (<1/minute)
├── One-way communication (use SSE instead)
├── When HTTP caching needed
└── Load balancer challenges (sticky sessions)
```

### WebSocket Best Practices

```
1. Connection Management:
   ├── Auto-reconnect on disconnect
   ├── Exponential backoff
   ├── Heartbeat/ping-pong (keep alive)
   └── Connection timeout handling

2. Message Format:
   Use JSON for flexibility:
   {
     "type": "message",
     "data": { ... },
     "timestamp": 1234567890
   }

3. Authentication:
   ├── Token in initial handshake
   ├── Validate before upgrade
   └── Re-authenticate periodically

4. Scalability:
   ├── Use Redis Pub/Sub for multi-server
   ├── Sticky sessions at load balancer
   ├── Horizontal scaling with state sharing
   └── Consider WebSocket-specific LB (HAProxy)

5. Fallback:
   ├── Detect WebSocket support
   ├── Fall back to long polling
   └── Use libraries like Socket.IO
```

### WebSocket vs Polling

```
Polling (HTTP):
Every 5 seconds:
  Client → Server: GET /notifications
  Server → Client: [] (empty)
  
10 updates per minute = 120 requests/hour
99% are empty responses
High latency: 0-5 seconds

WebSocket:
Connection established once
Server pushes when available:
  Server → Client: New notification!
  
1 connection, infinite updates
Real-time: <100ms latency
90% less bandwidth
```

---

## GraphQL

### What is GraphQL?

```
GraphQL: Query language for APIs
├── Single endpoint (/graphql)
├── Client specifies exact data needed
├── No over-fetching or under-fetching
├── Strongly typed schema
└── Developed by Facebook (2012)
```

### GraphQL vs REST

```
REST - Multiple Endpoints:
GET /users/123          → User data
GET /users/123/posts    → User's posts
GET /posts/456/comments → Post comments

3 requests, potential over-fetching

GraphQL - Single Query:
POST /graphql
{
  user(id: 123) {
    name
    email
    posts {
      title
      comments {
        text
        author
      }
    }
  }
}

1 request, exact data needed
```

### GraphQL Schema Example

```graphql
type User {
  id: ID!
  name: String!
  email: String!
  posts: [Post!]!
  followers: [User!]!
  createdAt: DateTime!
}

type Post {
  id: ID!
  title: String!
  content: String!
  author: User!
  comments: [Comment!]!
  likes: Int!
}

type Query {
  user(id: ID!): User
  users(limit: Int, offset: Int): [User!]!
  post(id: ID!): Post
  searchPosts(query: String!): [Post!]!
}

type Mutation {
  createPost(title: String!, content: String!): Post!
  updatePost(id: ID!, title: String, content: String): Post!
  deletePost(id: ID!): Boolean!
}

type Subscription {
  postCreated: Post!
  commentAdded(postId: ID!): Comment!
}
```

### When to Use GraphQL

```
✅ Perfect For:
├── Complex data relationships
├── Mobile apps (reduce bandwidth)
├── Multiple client types (web, mobile, desktop)
├── Rapidly evolving requirements
├── When over-fetching is a problem
├── Aggregating multiple microservices
└── Developer-friendly APIs

❌ Not Ideal For:
├── Simple CRUD applications
├── File uploads/downloads
├── Caching critical (harder than REST)
├── Small team (learning curve)
├── Simple internal services
└── When REST is sufficient
```

### GraphQL Performance Considerations

```
Problems:
├── N+1 query problem
├── Complex queries can be expensive
├── No built-in caching (unlike HTTP)
└── Can expose too much data

Solutions:
├── DataLoader (batching & caching)
├── Query complexity analysis
├── Depth limiting
├── Rate limiting by query cost
├── Persisted queries
└── APQ (Automatic Persisted Queries)
```

---

## Server-Sent Events (SSE)

### What is SSE?

```
SSE: Server-Sent Events
├── One-way communication (server → client)
├── Built on HTTP
├── Text-based (event stream)
├── Auto-reconnect on disconnect
└── Simpler than WebSocket
```

### How SSE Works

```
Client:
const eventSource = new EventSource('/events');

eventSource.onmessage = (event) => {
  console.log('New message:', event.data);
};

Server (Node.js):
res.writeHead(200, {
  'Content-Type': 'text/event-stream',
  'Cache-Control': 'no-cache',
  'Connection': 'keep-alive'
});

// Send events
res.write('data: Hello\n\n');
res.write('data: World\n\n');

Event Format:
event: notification
data: {"message": "New update"}
id: 123
retry: 10000

```

### When to Use SSE

```
✅ Perfect For:
├── Live notifications (one-way)
├── News feeds
├── Stock price updates
├── Server status updates
├── Progress bars for long operations
├── Live sports scores (server to client only)
└── Social media feed updates

❌ Not Ideal For:
├── Bidirectional communication (use WebSocket)
├── Binary data
├── When client needs to send frequent messages
└── IE/Edge (limited browser support - though improving)
```

### SSE vs WebSocket

```
SSE:
✓ Simpler (built on HTTP)
✓ Auto-reconnect built-in
✓ Works with HTTP/2 (multiplexing)
✓ Better for one-way (server → client)
✓ Event IDs for message replay
✗ Only text data
✗ Browser limit: 6 connections per domain

WebSocket:
✓ Bidirectional
✓ Binary data support
✓ No connection limit
✗ More complex
✗ Manual reconnect logic
✗ No built-in message replay
```

---

## Long Polling

### What is Long Polling?

```
Long Polling: Enhanced polling technique
├── Client sends request
├── Server holds request open until data available
├── Server responds when data ready
├── Client immediately sends new request
└── Simulates real-time over HTTP
```

### Long Polling Flow

```
Traditional Polling:
Client: Any updates? (request)
Server: No (immediate response)
[Wait 5 seconds]
Client: Any updates? (request)
Server: No (immediate response)
[Wait 5 seconds]
Client: Any updates? (request)
Server: Yes! Here's data (response)

Many wasted requests

Long Polling:
Client: Any updates? (request)
Server: [holds connection open]
Server: [holds connection open]
Server: [holds connection open]
Server: Yes! Here's data (response after 15 seconds)
Client: Any updates? (immediate new request)

Fewer requests, lower latency
```

### When to Use Long Polling

```
✅ Use When:
├── WebSocket not supported (firewall, proxy)
├── Simple implementation needed
├── Infrequent updates (1-10 per minute)
├── HTTP infrastructure already in place
└── Fallback for WebSocket

❌ Avoid When:
├── High-frequency updates (>10/second)
├── WebSocket is available
├── Bidirectional communication needed
└── Scaling is critical (connection overhead)
```

### Long Polling Challenges

```
Problems:
├── Each client holds server connection
├── 10K clients = 10K open connections
├── Server resource intensive
├── Harder to scale than WebSocket
└── Timeout handling complexity

Solutions:
├── Use for fallback only
├── Set reasonable timeouts (30-60s)
├── Implement exponential backoff
├── Consider SSE instead
└── Use WebSocket when possible
```

---

## Message Queues

### Kafka

**What is Kafka?**
```
Apache Kafka: Distributed streaming platform
├── Publish-subscribe messaging
├── High-throughput (millions of messages/sec)
├── Durable (persistent storage)
├── Scalable (horizontal partitioning)
└── Used for event streaming
```

**Architecture:**
```
Producer → Kafka Broker → Consumer

Topic: "user.events"
├── Partition 0: [msg1, msg2, msg3, ...]
├── Partition 1: [msg4, msg5, msg6, ...]
└── Partition 2: [msg7, msg8, msg9, ...]

Each partition is ordered, append-only log
Messages retained for configurable time (days)
Consumers track their offset (position)
```

**When to Use Kafka:**
```
✅ Perfect For:
├── Event streaming (user actions, logs)
├── Real-time data pipelines
├── Log aggregation
├── Metrics collection
├── Stream processing (with Kafka Streams)
├── Event sourcing architecture
├── Microservices communication (async)
└── High-throughput scenarios (>100K msg/sec)

❌ Not Ideal For:
├── Request-response pattern
├── Low-latency (<10ms) requirements
├── Simple queues (overkill)
├── Transactional messaging
└── Small scale (<1K msg/sec)
```

### RabbitMQ

**What is RabbitMQ?**
```
RabbitMQ: Message broker
├── AMQP protocol
├── Flexible routing
├── Multiple exchange types
├── Reliable delivery guarantees
└── Easier than Kafka for simple cases
```

**Exchange Types:**
```
1. Direct Exchange:
   Message goes to queue with matching routing key
   
2. Fanout Exchange:
   Broadcast to all bound queues
   
3. Topic Exchange:
   Pattern matching routing (user.*.created)
   
4. Headers Exchange:
   Route based on message headers
```

**When to Use RabbitMQ:**
```
✅ Perfect For:
├── Task queues (background jobs)
├── Work distribution
├── Complex routing logic
├── Request-response over queue
├── Priority queues
├── Delayed messages
├── RPC patterns
└── Transactional guarantees needed

❌ Not Ideal For:
├── Very high throughput (>100K msg/sec)
├── Stream processing
├── Log aggregation
├── Event replay needed
└── Distributed tracing of events
```

### Kafka vs RabbitMQ

```
┌──────────────┬─────────────┬──────────────┐
│ Feature      │ Kafka       │ RabbitMQ     │
├──────────────┼─────────────┼──────────────┤
│ Throughput   │ Very High   │ Moderate     │
│              │ (1M+ msg/s) │ (10K msg/s)  │
├──────────────┼─────────────┼──────────────┤
│ Persistence  │ Always      │ Optional     │
├──────────────┼─────────────┼──────────────┤
│ Message TTL  │ Time-based  │ Per message  │
├──────────────┼─────────────┼──────────────┤
│ Replay       │ Yes         │ No           │
├──────────────┼─────────────┼──────────────┤
│ Routing      │ Simple      │ Complex      │
├──────────────┼─────────────┼──────────────┤
│ Use Case     │ Streaming   │ Queuing      │
└──────────────┴─────────────┴──────────────┘

Interview Tip:
"For event streaming and high throughput, use Kafka.
For task queues and complex routing, use RabbitMQ."
```

---

## MQTT

### What is MQTT?

```
MQTT: Message Queuing Telemetry Transport
├── Lightweight pub-sub protocol
├── Designed for IoT devices
├── Low bandwidth, low power
├── Built on TCP
└── 3 QoS levels
```

### MQTT Features

```
Publish-Subscribe Model:
Device A → Broker → Device B, C, D

Topics (hierarchical):
home/livingroom/temperature
home/bedroom/light/status
car/tesla/model3/location

QoS Levels:
0 - At most once (fire and forget)
1 - At least once (acknowledged)
2 - Exactly once (handshake)

Small Overhead:
Fixed header: 2 bytes only
Perfect for constrained devices
```

### When to Use MQTT

```
✅ Perfect For:
├── IoT devices (sensors, actuators)
├── Smart home systems
├── Mobile push notifications
├── Low bandwidth scenarios
├── Unreliable networks
├── Battery-powered devices
└── Real-time telemetry

❌ Not Ideal For:
├── Web applications (use WebSocket)
├── Large file transfers
├── Request-response APIs
├── When HTTP is sufficient
└── Browser-based apps (limited support)
```

---

## TCP vs UDP

### TCP (Transmission Control Protocol)

```
TCP: Reliable, connection-oriented
├── Guaranteed delivery
├── Ordered packets
├── Error checking
├── Flow control
├── Congestion control
└── 3-way handshake
```

**When to Use TCP:**
```
✅ Use When:
├── Reliability is critical
├── Data integrity matters
├── Order matters
├── File transfers
├── HTTP, HTTPS, WebSocket
├── Database connections
├── Email (SMTP)
└── SSH, FTP

Examples:
- Web browsing
- Email
- File downloads
- Database queries
- API calls
```

### UDP (User Datagram Protocol)

```
UDP: Fast, connectionless
├── No delivery guarantee
├── No ordering
├── Minimal overhead
├── Lower latency
├── No connection state
└── Fire and forget
```

**When to Use UDP:**
```
✅ Use When:
├── Speed > reliability
├── Real-time critical
├── Some packet loss acceptable
├── Broadcast/multicast needed
├── Low latency required
└── High throughput needed

Examples:
- Video streaming (acceptable quality loss)
- VoIP calls (Zoom, Skype)
- Online gaming (position updates)
- DNS queries (can retry)
- Live sports broadcasts
- IoT sensor data
```

### TCP vs UDP Comparison

```
┌─────────────────┬────────────┬──────────────┐
│ Characteristic  │ TCP        │ UDP          │
├─────────────────┼────────────┼──────────────┤
│ Reliability     │ Yes        │ No           │
│ Ordering        │ Yes        │ No           │
│ Speed           │ Slower     │ Faster       │
│ Overhead        │ Higher     │ Lower        │
│ Use Case        │ Data       │ Real-time    │
│ Example         │ HTTP       │ Video call   │
└─────────────────┴────────────┴──────────────┘

Latency:
TCP: 100-200ms (handshake + transmission)
UDP: 20-50ms (just transmission)

Interview Scenario:
Q: "Why does Zoom use UDP?"
A: "For video calls, low latency is more important 
    than perfect quality. Users prefer a slight quality 
    drop over lag. UDP provides 2-4x lower latency 
    than TCP, making conversations feel natural."
```

---

## Protocol Comparison Matrix

```
┌──────────┬─────────┬──────────┬──────────┬──────────┬──────────┐
│ Protocol │ Latency │ Overhead │ Real-time│ Bidirect │ Browser  │
├──────────┼─────────┼──────────┼──────────┼──────────┼──────────┤
│ REST     │ 50-100ms│ High     │ No       │ No       │ Yes ✓    │
│ gRPC     │ 5-10ms  │ Low      │ Yes      │ Yes      │ Limited  │
│ WebSocket│ 1-5ms   │ Very Low │ Yes      │ Yes      │ Yes ✓    │
│ GraphQL  │ 50-100ms│ Medium   │ No       │ No       │ Yes ✓    │
│ SSE      │ 10-20ms │ Low      │ Yes      │ No       │ Yes ✓    │
│ Kafka    │ 10-50ms │ Low      │ No       │ No       │ No       │
│ MQTT     │ 5-10ms  │ Very Low │ Yes      │ Yes      │ Limited  │
└──────────┴─────────┴──────────┴──────────┴──────────┴──────────┘

┌──────────┬──────────┬──────────┬──────────┬──────────┐
│ Protocol │ Use Case │ Data Size│ Scale    │ Complexity│
├──────────┼──────────┼──────────┼──────────┼──────────┤
│ REST     │ CRUD     │ Any      │ High     │ Low       │
│ gRPC     │ µServices│ Small    │ Very High│ Medium    │
│ WebSocket│ Real-time│ Small    │ High     │ Medium    │
│ GraphQL  │ Flexible │ Any      │ High     │ High      │
│ SSE      │ Push     │ Small    │ High     │ Low       │
│ Kafka    │ Streaming│ Any      │ Very High│ High      │
│ MQTT     │ IoT      │ Small    │ High     │ Low       │
└──────────┴──────────┴──────────┴──────────┴──────────┘
```

---

## Top 10 HLD Questions with Protocol Choices

### 1. Design WhatsApp/Facebook Messenger

**Requirements:**
- Real-time messaging
- Online status
- Message delivery confirmation
- Media sharing
- Group chats

**Protocol Choices:**

```
Client-Server Communication:
✓ WebSocket (Primary)
  - Real-time bidirectional
  - Message send/receive
  - Typing indicators
  - Online status updates
  - Latency: 50-100ms

✓ HTTP/REST (Secondary)
  - Media uploads
  - User profile updates
  - Message history pagination
  - Settings changes

✓ gRPC (Internal Microservices)
  - User service ↔ Message service
  - Message service ↔ Notification service
  - Low latency: 5-10ms

✓ Kafka (Async Processing)
  - Message persistence
  - Delivery confirmation
  - Analytics events
  - Audit logs

Architecture:
┌─────────┐              ┌──────────────┐
│ Client  │─WebSocket──→ │ WebSocket    │
│ (Mobile)│              │ Gateway      │
└─────────┘              └──────┬───────┘
                                ↓
                         ┌──────────────┐
                         │ Message      │
                         │ Service      │
                         └──────┬───────┘
                                ↓
┌──────────────┬─────────────────┼─────────────────┐
↓              ↓                 ↓                 ↓
[Kafka]    [Redis]         [PostgreSQL]      [Notification]
(Events)   (Cache)         (Storage)         (Service)

Why This Combination:
"WebSocket for real-time bidirectional chat, HTTP for 
media uploads (better for large files), gRPC for fast 
internal communication, and Kafka for reliable async 
processing of messages and events."
```

### 2. Design Uber/Lyft

**Requirements:**
- Real-time location tracking
- Ride matching
- ETA updates
- Navigation
- Payment processing

**Protocol Choices:**

```
Driver Location Updates:
✓ MQTT or WebSocket
  - Driver sends GPS every 4 seconds
  - Low battery consumption (MQTT advantage)
  - Persistent connection
  - QoS 1 (at least once delivery)

Rider Location & ETA:
✓ WebSocket
  - Real-time driver location
  - ETA updates
  - Ride status changes
  - Bidirectional (cancel ride)

Backend Services:
✓ gRPC (Microservices)
  - Location Service ↔ Matching Service
  - Matching Service ↔ Pricing Service
  - Low latency critical (<10ms)
  - Efficiency for high-volume calls

✓ Kafka (Event Streaming)
  - Ride events (requested, accepted, completed)
  - Location history for analytics
  - Surge pricing calculations
  - Driver behavior analysis

✓ HTTP/REST (APIs)
  - User signup/login
  - Payment processing
  - Ride history
  - Profile management

Architecture:
┌─────
