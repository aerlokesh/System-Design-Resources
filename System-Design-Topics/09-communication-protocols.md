# 🎯 Topic 9: Communication Protocols

> **System Design Interview — Deep Dive**
> A comprehensive guide covering HTTP/REST, gRPC, WebSocket, SSE, GraphQL, and long polling — when to use each protocol, the tradeoffs, and how to articulate protocol choices with precision in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Protocol Landscape Overview](#protocol-landscape-overview)
3. [HTTP/REST — The Universal Standard](#httprest--the-universal-standard)
4. [gRPC — High-Performance Service-to-Service](#grpc--high-performance-service-to-service)
5. [WebSocket — Full-Duplex Real-Time](#websocket--full-duplex-real-time)
6. [Server-Sent Events (SSE) — Server Push Over HTTP](#server-sent-events-sse--server-push-over-http)
7. [GraphQL — Client-Driven Data Fetching](#graphql--client-driven-data-fetching)
8. [Long Polling — The HTTP Workaround](#long-polling--the-http-workaround)
9. [Protocol Comparison Matrix](#protocol-comparison-matrix)
10. [The Graceful Degradation Chain](#the-graceful-degradation-chain)
11. [Choosing Protocols by Use Case](#choosing-protocols-by-use-case)
12. [Client-to-Server vs Server-to-Server](#client-to-server-vs-server-to-server)
13. [Protocol Buffers vs JSON](#protocol-buffers-vs-json)
14. [HTTP/1.1 vs HTTP/2 vs HTTP/3](#http11-vs-http2-vs-http3)
15. [Connection Management at Scale](#connection-management-at-scale)
16. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
17. [Common Interview Mistakes](#common-interview-mistakes)
18. [Protocol Decisions for Popular System Design Problems](#protocol-decisions-for-popular-system-design-problems)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Communication protocol selection depends on three key factors:

1. **Direction**: Unidirectional (client→server OR server→client) vs bidirectional (both).
2. **Latency requirements**: Real-time (< 100ms) vs request-response (seconds acceptable).
3. **Context**: Client-to-server (external, untrusted) vs service-to-service (internal, trusted).

```
Client ↔ Server Communication:
  REST:      Client asks, server responds (request/response)
  WebSocket: Both can send anytime (full-duplex)
  SSE:       Server pushes to client (unidirectional)
  GraphQL:   Client specifies exactly what data it wants

Service ↔ Service Communication:
  gRPC:      Binary, fast, strongly typed
  REST:      Simple, widely supported
  Message Queue: Async, decoupled (Kafka, SQS)
```

---

## Protocol Landscape Overview

```
┌──────────────────────────────────────────────────────────────┐
│                  PROTOCOL LANDSCAPE                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  REQUEST/RESPONSE (Client initiates):                        │
│  ├── HTTP/REST    (text/JSON, stateless, universal)          │
│  ├── gRPC         (binary/protobuf, multiplexed, typed)      │
│  └── GraphQL      (flexible queries, client-driven)          │
│                                                              │
│  SERVER PUSH (Server initiates):                             │
│  ├── WebSocket    (full-duplex, persistent connection)       │
│  ├── SSE          (server → client only, auto-reconnect)    │
│  └── Long Polling (HTTP workaround, universal support)       │
│                                                              │
│  ASYNC MESSAGING (Decoupled):                                │
│  ├── Kafka        (event streaming, replay, multi-consumer)  │
│  ├── SQS/RabbitMQ (task queues, exactly-once delivery)       │
│  └── Redis Pub/Sub (lightweight, ephemeral)                  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## HTTP/REST — The Universal Standard

### When to Use
- **Public APIs**: REST is the lingua franca of the internet.
- **CRUD operations**: Natural mapping to HTTP methods (GET, POST, PUT, DELETE).
- **Stateless operations**: Each request carries all context needed.
- **Cacheable responses**: HTTP caching (ETags, Cache-Control) works naturally.
- **When in doubt**: REST is always a safe choice for client-facing APIs.

### REST Design Principles
```
GET    /api/v1/tweets/{id}           → Read a tweet
POST   /api/v1/tweets                → Create a tweet
PUT    /api/v1/tweets/{id}           → Update a tweet (full replace)
PATCH  /api/v1/tweets/{id}           → Partial update
DELETE /api/v1/tweets/{id}           → Delete a tweet

GET    /api/v1/users/{id}/followers  → List followers
GET    /api/v1/users/{id}/tweets?page=2&limit=20  → Paginated list
```

### Characteristics
| Aspect | Details |
|---|---|
| **Protocol** | HTTP/1.1 or HTTP/2 |
| **Serialization** | JSON (human-readable, ~500 bytes per typical response) |
| **Connection** | New connection per request (HTTP/1.1) or multiplexed (HTTP/2) |
| **State** | Stateless (each request is self-contained) |
| **Caching** | Built-in via HTTP headers (Cache-Control, ETag, Last-Modified) |
| **Tooling** | Universal: curl, Postman, browsers, every programming language |
| **Latency** | 10-100ms per request (varies by payload size and network) |

### Limitations
- **Over-fetching**: GET /user returns all fields even if client needs just the name.
- **Under-fetching**: Need user profile + recent posts? That's 2 API calls (N+1 problem).
- **No server push**: Client must poll for updates.
- **Text-based**: JSON is verbose compared to binary formats.

### Interview Script
> *"I'd use REST for the public API — it's stateless, cacheable via HTTP headers, documented with OpenAPI/Swagger, and literally every developer on Earth knows how to call it."*

---

## gRPC — High-Performance Service-to-Service

### When to Use
- **Internal service-to-service communication**: Where both sides are under your control.
- **High-throughput, low-latency**: Binary serialization is 3-10x smaller than JSON.
- **Strong typing**: Compile-time errors instead of runtime errors.
- **Streaming**: Client streaming, server streaming, or bidirectional streaming.
- **Polyglot services**: Auto-generated clients for 10+ languages from one .proto file.

### How gRPC Works
```
1. Define service in .proto file:
   
   service TweetService {
     rpc GetTweet(GetTweetRequest) returns (Tweet);
     rpc CreateTweet(CreateTweetRequest) returns (Tweet);
     rpc StreamTimeline(TimelineRequest) returns (stream Tweet);
   }
   
   message Tweet {
     string id = 1;
     string content = 2;
     string author_id = 3;
     int64 timestamp = 4;
   }

2. Generate client and server code (auto-generated)
3. Client calls server like a local function:
   
   tweet = tweet_service.GetTweet(GetTweetRequest(id="123"))
```

### Characteristics
| Aspect | Details |
|---|---|
| **Protocol** | HTTP/2 (mandatory) |
| **Serialization** | Protocol Buffers (binary, 3-10x smaller than JSON) |
| **Connection** | Multiplexed (thousands of RPCs over one TCP connection) |
| **Typing** | Strongly typed (compile-time schema validation) |
| **Streaming** | Client streaming, server streaming, bidirectional |
| **Code generation** | Auto-generated clients in 10+ languages |
| **Latency** | 1-10ms (binary serialization + multiplexing) |

### gRPC vs REST Performance
```
Payload size comparison (same data):
  JSON (REST):     486 bytes
  Protobuf (gRPC): 128 bytes  (3.8x smaller)

Serialization speed:
  JSON encode:     ~1ms
  Protobuf encode: ~0.1ms  (10x faster)

Network impact at 50K requests/sec:
  JSON: 486 × 50K = 24.3 MB/sec
  Protobuf: 128 × 50K = 6.4 MB/sec  (3.8x less bandwidth)
```

### Limitations
- **Browser support**: No native browser gRPC (need gRPC-Web proxy).
- **Not human-readable**: Binary format can't be inspected in browser DevTools.
- **Complexity**: Requires .proto file management, code generation pipeline.
- **Debugging**: Can't test with curl like REST.

### Interview Script
> *"For internal service-to-service communication, I'd use gRPC — it uses Protocol Buffers for binary serialization (3-10x smaller payloads than JSON), HTTP/2 for multiplexing (multiple requests over a single connection), and code-generated clients with strong typing (compile-time errors instead of runtime errors). In our microservice mesh where Service A calls Service B 50,000 times per second, gRPC reduces bandwidth by 5x and latency by 2x compared to REST/JSON."*

---

## WebSocket — Full-Duplex Real-Time

### When to Use
- **Real-time bidirectional communication**: Chat, gaming, collaborative editing.
- **Low-latency server push**: Stock tickers, live scores, notifications.
- **Persistent connections**: When you need to maintain state between messages.
- **High-frequency updates**: Multiple messages per second in both directions.

### How WebSocket Works
```
1. HTTP Upgrade handshake:
   Client: GET /chat HTTP/1.1
           Upgrade: websocket
           Connection: Upgrade
   
   Server: HTTP/1.1 101 Switching Protocols
           Upgrade: websocket

2. Connection is now full-duplex:
   Client ←→ Server (both can send at any time)

3. Messages are framed (not HTTP requests):
   Small overhead per message (~2-14 bytes header vs ~200+ bytes for HTTP)
```

### Architecture at Scale
```
                    ┌─────────────────┐
                    │  Load Balancer   │
                    │ (Layer 4/sticky) │
                    └───────┬─────────┘
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
         ┌─────────┐  ┌─────────┐  ┌─────────┐
         │Gateway 1│  │Gateway 2│  │Gateway 3│
         │ 65K conn│  │ 65K conn│  │ 65K conn│
         └────┬────┘  └────┬────┘  └────┬────┘
              │            │            │
              └─────── Redis Pub/Sub ───┘
                    (cross-gateway messaging)
```

### Characteristics
| Aspect | Details |
|---|---|
| **Protocol** | WebSocket (ws:// or wss://) |
| **Connection** | Persistent, full-duplex |
| **Overhead** | 2-14 bytes per frame (vs ~200 bytes for HTTP) |
| **Direction** | Bidirectional (both sides can send anytime) |
| **State** | Stateful (connection-based) |
| **Scaling** | Challenging (sticky sessions, connection management) |
| **Use cases** | Chat, gaming, real-time collaboration, live feeds |

### Limitations
- **Stateful**: Connections are tied to specific servers → need session affinity.
- **Load balancing**: Can't round-robin (connections are persistent).
- **Firewall issues**: Some corporate firewalls block WebSocket upgrade.
- **Reconnection**: No auto-reconnect (must implement in client).
- **Scaling complexity**: Each server holds thousands of connections.

### Interview Script
> *"For the chat feature, WebSocket is the clear choice — we need full-duplex, persistent, low-latency communication. When Alice sends a message, it travels: Alice's client → WebSocket → Gateway Server → Message Service → Kafka → Gateway Server hosting Bob's connection → WebSocket → Bob's client. Total latency: ~30ms. With HTTP polling, Bob would only see the message on his next poll interval — if we poll every 3 seconds, average delivery latency is 1.5 seconds. That's 50x worse."*

---

## Server-Sent Events (SSE) — Server Push Over HTTP

### When to Use
- **Unidirectional server-to-client streaming**: Live updates, notifications, dashboards.
- **When you don't need client-to-server messaging**: Stock ticker, news feed, log tail.
- **When you want simplicity**: SSE works over standard HTTP (no protocol upgrade).
- **When auto-reconnect matters**: SSE has built-in reconnection logic.

### How SSE Works
```
Client opens EventSource:
  const source = new EventSource('/api/stock/AAPL');
  source.onmessage = (event) => updatePrice(event.data);

Server sends events:
  HTTP/1.1 200 OK
  Content-Type: text/event-stream
  
  data: {"symbol": "AAPL", "price": 178.52}
  
  data: {"symbol": "AAPL", "price": 178.55}
  
  data: {"symbol": "AAPL", "price": 178.49}
```

### SSE vs WebSocket

| Aspect | SSE | WebSocket |
|---|---|---|
| **Direction** | Server → Client only | Bidirectional |
| **Protocol** | HTTP | Custom (ws://) |
| **Auto-reconnect** | Built-in | Must implement |
| **Load balancing** | Standard HTTP LB works | Need sticky sessions |
| **Browser support** | `EventSource` API (native) | `WebSocket` API (native) |
| **Firewalls** | Works everywhere (it's HTTP) | May be blocked |
| **Binary data** | Text only (Base64 for binary) | Native binary support |
| **Best for** | Stock ticker, notifications | Chat, gaming, collaboration |

### Interview Script
> *"For the real-time stock ticker, I'd use Server-Sent Events (SSE) over WebSocket. The data flow is unidirectional — the server pushes price updates to the client, but the client never sends data back through this channel. SSE works over standard HTTP, auto-reconnects on connection drops (WebSocket doesn't), and is simpler to load-balance because it's stateless HTTP. The browser natively supports SSE with the `EventSource` API — no library needed."*

---

## GraphQL — Client-Driven Data Fetching

### When to Use
- **Mobile clients**: Minimize payload size over cellular connections.
- **Multiple client types**: Web, mobile, TV each need different data shapes.
- **Complex object graphs**: Fetch related entities in one request.
- **Rapid frontend iteration**: Frontend team can change queries without backend changes.

### How It Solves REST's Problems
```
REST (over-fetching):
  GET /api/users/123
  Returns: { id, name, email, avatar, bio, created_at, last_login, 
             settings, preferences, ... }  (30 fields, 2KB)
  Client only needs: name, avatar

REST (under-fetching / N+1):
  GET /api/users/123          → user data
  GET /api/users/123/posts    → user's posts (second request!)
  GET /api/posts/456/comments → post's comments (third request!)

GraphQL (one request, exact data):
  query {
    user(id: "123") {
      name
      avatar
      recentPosts(limit: 3) {
        title
        commentCount
      }
    }
  }
  
  Returns exactly: { name, avatar, recentPosts: [{title, commentCount}, ...] }
  One request, minimal payload, no over- or under-fetching.
```

### Characteristics
| Aspect | Details |
|---|---|
| **Query language** | Client specifies exact data shape |
| **Endpoint** | Single endpoint: POST /graphql |
| **Over-fetching** | Eliminated (client requests only needed fields) |
| **Under-fetching** | Eliminated (nested queries in one request) |
| **Typing** | Strongly typed schema |
| **Introspection** | Self-documenting (clients can discover schema) |
| **Caching** | More complex than REST (no URL-based caching) |

### Limitations
- **Complexity**: Query parsing, validation, resolver functions.
- **Caching**: Can't use HTTP caching (all requests are POST to same URL).
- **N+1 at server level**: Naive resolvers cause DB N+1 (solve with DataLoader).
- **Security**: Complex queries can be used for DoS (query depth/cost limiting needed).
- **Overkill**: For simple CRUD APIs, REST is simpler.

### Interview Script
> *"GraphQL makes sense specifically for the mobile client. Our user profile page needs the user's name, avatar, follower count, and last 3 posts — but our REST API either returns the entire user object (over-fetching 30 fields when we need 3) or requires 3 separate API calls. GraphQL lets the mobile client specify exactly `{ user { name, avatar, followerCount, recentPosts(limit: 3) { title } } }` — one request, exactly the data needed, minimal bandwidth on a cellular connection."*

---

## Long Polling — The HTTP Workaround

### How It Works
```
1. Client sends HTTP request
2. Server holds the connection open (doesn't respond immediately)
3. When new data is available, server responds
4. Client immediately sends another request (loop)

Timeline:
Client: GET /messages/poll?since=timestamp_123 ─────→ Server
Server: (holds connection for up to 30 seconds)
Server: New message arrives → HTTP 200 {new_messages: [...]}
Client: Immediately: GET /messages/poll?since=timestamp_456 ─→ Server
Server: (holds again...)
```

### When to Use
- **Universal compatibility**: Works through any firewall, proxy, or network.
- **Fallback**: When WebSocket and SSE are not available.
- **Simple implementation**: Just HTTP requests with delayed responses.
- **Legacy systems**: When you can't upgrade infrastructure.

### Limitations
- **Connection overhead**: Each poll is a new HTTP request with full headers.
- **Server resources**: Holding connections open consumes server threads.
- **Latency**: Average delivery = poll_interval / 2.
- **Bandwidth waste**: Empty responses when no new data.

---

## Protocol Comparison Matrix

| Feature | REST | gRPC | WebSocket | SSE | GraphQL | Long Polling |
|---|---|---|---|---|---|---|
| **Direction** | Req/Res | Req/Res + Stream | Bidirectional | Server→Client | Req/Res | Simulated Push |
| **Protocol** | HTTP/1.1+ | HTTP/2 | WS | HTTP | HTTP | HTTP |
| **Serialization** | JSON | Protobuf | Any | Text | JSON | JSON |
| **Latency** | 10-100ms | 1-10ms | < 5ms | < 5ms | 10-100ms | 0-30s |
| **Browser** | ✅ | ❌ (need proxy) | ✅ | ✅ | ✅ | ✅ |
| **Caching** | ✅ (HTTP) | ❌ | ❌ | ❌ | ⚠️ (complex) | ❌ |
| **Firewall** | ✅ | ✅ | ⚠️ (may block) | ✅ | ✅ | ✅ |
| **Complexity** | Low | Medium | High | Low | Medium | Low |
| **Best For** | Public API | Internal RPC | Chat, games | Dashboards | Mobile apps | Fallback |

---

## The Graceful Degradation Chain

When building real-time features, implement a transport negotiation chain:

```
Attempt 1: WebSocket
  ↓ (fails: firewall blocks upgrade)
Attempt 2: SSE
  ↓ (fails: proxy doesn't support HTTP/2)
Attempt 3: Long Polling
  ↓ (always works: it's just HTTP)

The client library auto-negotiates the best transport.
Business logic is identical regardless of transport.
Result: 100% client compatibility.
```

### Interview Script
> *"I'd use WebSocket for the primary real-time channel but implement a graceful degradation chain: WebSocket → SSE → long polling. Some corporate firewalls strip WebSocket upgrade headers. Some older proxies don't support HTTP/2 for SSE. Long polling works everywhere because it's just regular HTTP. The client library negotiates the best available transport at connection time — the business logic is identical regardless of which transport is active."*

---

## Choosing Protocols by Use Case

### Client-to-Server (External)

| Use Case | Protocol | Why |
|---|---|---|
| **Public REST API** | HTTP/REST | Universal, cacheable, well-understood |
| **Mobile app (varied data needs)** | GraphQL | Minimize bandwidth, one request |
| **Chat / messaging** | WebSocket | Bidirectional, real-time |
| **Live stock prices** | SSE | Server push, auto-reconnect |
| **File upload** | HTTP multipart or presigned S3 URL | Standard, resumable |
| **Video streaming** | HLS/DASH over HTTP | Adaptive bitrate, CDN-compatible |

### Server-to-Server (Internal)

| Use Case | Protocol | Why |
|---|---|---|
| **Synchronous RPC** | gRPC | Binary, typed, fast, multiplexed |
| **Event streaming** | Kafka | Async, replay, multi-consumer |
| **Task distribution** | SQS/RabbitMQ | Exactly-once, simple |
| **Real-time notifications between services** | Redis Pub/Sub | Lightweight, ephemeral |
| **Legacy service integration** | REST | Simplicity, compatibility |

---

## Client-to-Server vs Server-to-Server

### The Two-Protocol Architecture
```
External (client-facing):           Internal (service-to-service):
  REST/GraphQL for APIs              gRPC for sync calls
  WebSocket for real-time            Kafka for async events
  SSE for server push                Redis Pub/Sub for pub/sub

┌────────┐  REST/WS  ┌────────────┐  gRPC  ┌──────────┐
│ Client │ ────────→ │ API Gateway │ ─────→ │ Service A│
│        │ ←──────── │            │ ←───── │          │
└────────┘           └─────┬──────┘        └──────────┘
                           │ Kafka
                           ▼
                     ┌──────────┐
                     │ Service B│
                     └──────────┘
```

### Why Different Protocols?
- **External**: Must be browser-compatible, human-readable, firewall-friendly.
- **Internal**: Can optimize for performance (binary), type safety (protobuf), and throughput (multiplexing).

### Interview Script
> *"I'd use REST for the public API — it's stateless, cacheable via HTTP headers, documented with OpenAPI/Swagger, and every developer knows how to call it. For internal service-to-service calls, gRPC with Protocol Buffers — the binary serialization is 3-10x smaller than JSON, the strong typing catches errors at compile time, and HTTP/2 multiplexing means a single TCP connection handles thousands of concurrent RPCs."*

---

## Protocol Buffers vs JSON

### Size Comparison
```
JSON (154 bytes):
{
  "user_id": 123456789,
  "name": "Alice Johnson",
  "email": "alice@example.com",
  "follower_count": 42500,
  "is_verified": true
}

Protocol Buffers (48 bytes):
Binary encoded (not human-readable)
3.2x smaller
```

### When to Use Each
| JSON | Protocol Buffers |
|---|---|
| Client-facing APIs | Internal service-to-service |
| Debugging/logging | High-throughput data pipelines |
| Schema flexibility | Schema evolution with backward compatibility |
| Human readability needed | Performance is critical |
| Small payloads | Large payloads |

---

## HTTP/1.1 vs HTTP/2 vs HTTP/3

| Feature | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|---|
| **Multiplexing** | No (1 request per connection) | Yes (many requests per connection) | Yes |
| **Header compression** | No | HPACK | QPACK |
| **Transport** | TCP | TCP | QUIC (UDP) |
| **Head-of-line blocking** | Yes (TCP level) | Partially solved | Fully solved |
| **Server push** | No | Yes | Yes |
| **Connection setup** | TCP handshake | TCP + TLS | 0-RTT (QUIC) |
| **Adoption** | Universal | Widespread | Growing |

### Why HTTP/2 Matters for gRPC
gRPC requires HTTP/2 because it depends on:
- **Multiplexing**: Multiple RPCs over one connection.
- **Binary framing**: Efficient for protobuf payloads.
- **Server push**: For streaming RPCs.
- **Header compression**: Reduces overhead on high-frequency calls.

---

## Connection Management at Scale

### WebSocket Connections
```
Per gateway server: ~65K connections (limited by file descriptors)
Using epoll (Linux): Non-blocking I/O, no thread per connection

For 10 million concurrent users:
  10M / 65K = ~154 gateway servers

Each gateway: 
  - Redis lookup: session:{user_id} → gateway_server_id
  - Cross-gateway messaging via Redis Pub/Sub or Kafka
```

### gRPC Connection Pooling
```
Each client maintains connection pool to each service:
  pool_size = max(cpu_cores, expected_concurrent_requests / 100)
  
  Example: 8 cores, 1000 concurrent requests
  pool_size = max(8, 10) = 10 connections
  
  Each HTTP/2 connection multiplexes ~100 concurrent streams
  10 connections × 100 streams = 1000 concurrent RPCs
```

---

## Interview Talking Points & Scripts

### Protocol Selection Framework
> *"My protocol selection framework: REST for public APIs (universal, cacheable), gRPC for internal service-to-service (binary, typed, fast), WebSocket for bidirectional real-time (chat, gaming), SSE for server push (stock tickers, notifications). I'd use REST for external + gRPC for internal in most systems."*

### Real-Time Transport Negotiation
> *"I'd use WebSocket for the primary real-time channel but implement graceful degradation: WebSocket → SSE → long polling. This ensures 100% client compatibility while optimizing for the best available transport."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using WebSocket for everything
**Problem**: WebSocket is overkill for request-response patterns and adds connection management complexity.

### ❌ Mistake 2: Using REST for internal high-throughput calls
**Problem**: JSON serialization overhead at 50K+ calls/sec wastes significant bandwidth. gRPC is 3-10x more efficient.

### ❌ Mistake 3: Not mentioning protocol for each communication path
**Problem**: Saying "services communicate" without specifying the protocol. Always name the protocol and explain why.

### ❌ Mistake 4: WebSocket for unidirectional server push
**Problem**: If data only flows server→client, SSE is simpler, auto-reconnects, and works through more firewalls.

### ❌ Mistake 5: GraphQL for simple CRUD
**Problem**: GraphQL adds complexity (parsing, resolvers, caching). For simple CRUD with well-defined entities, REST is simpler and sufficient.

---

## Protocol Decisions for Popular System Design Problems

| System | Client → Server | Server → Client | Service ↔ Service |
|---|---|---|---|
| **Twitter** | REST (API) | SSE or Long Polling (feed updates) | gRPC + Kafka |
| **WhatsApp** | WebSocket (messages) | WebSocket (delivery) | gRPC + Kafka |
| **E-Commerce** | REST (catalog, orders) | SSE (order status) | gRPC + Kafka |
| **Stock Ticker** | REST (historical data) | SSE (live prices) | gRPC |
| **Google Docs** | WebSocket (edits) | WebSocket (collaborator edits) | gRPC |
| **Uber** | REST (request ride) + WebSocket (tracking) | WebSocket (driver location) | gRPC + Kafka |
| **Netflix** | REST (catalog) + HLS (video) | — | gRPC |
| **Instagram** | REST/GraphQL (feed, profile) | Push notification (APNs/FCM) | gRPC + Kafka |
| **Slack** | WebSocket (messages) | WebSocket (messages) | gRPC + Kafka |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│          COMMUNICATION PROTOCOLS CHEAT SHEET                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CLIENT-FACING:                                              │
│  REST:      Public APIs, CRUD, cacheable (DEFAULT)           │
│  GraphQL:   Mobile apps, complex data needs                  │
│  WebSocket: Chat, gaming, collaborative editing              │
│  SSE:       Stock tickers, dashboards, notifications         │
│  Long Poll: Universal fallback                               │
│                                                              │
│  SERVICE-TO-SERVICE:                                         │
│  gRPC:      Sync RPC (3-10x faster than REST)                │
│  Kafka:     Async events (decoupled, replay)                 │
│  SQS:       Task queues (simple, managed)                    │
│                                                              │
│  GRACEFUL DEGRADATION:                                       │
│  WebSocket → SSE → Long Polling (100% compatibility)         │
│                                                              │
│  SERIALIZATION:                                              │
│  JSON:     Client-facing (human-readable)                    │
│  Protobuf: Internal (3-10x smaller, 10x faster)             │
│                                                              │
│  KEY NUMBERS:                                                │
│  REST latency: 10-100ms                                      │
│  gRPC latency: 1-10ms                                        │
│  WebSocket: < 5ms per message                                │
│  Protobuf: 3-10x smaller than JSON                           │
│  WebSocket connections per server: ~65K                       │
│                                                              │
│  FORMULA:                                                    │
│  External = REST (+ WebSocket/SSE for real-time)             │
│  Internal = gRPC (sync) + Kafka (async)                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 7: Push vs Pull** — Push uses WebSocket/SSE, pull uses REST
- **Topic 8: Sync vs Async** — Protocol choice depends on sync/async pattern
- **Topic 10: Message Queues vs Event Streams** — Async communication protocols
- **Topic 25: API Design** — REST/GraphQL design principles
- **Topic 37: WebSocket at Scale** — WebSocket architecture deep dive

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
