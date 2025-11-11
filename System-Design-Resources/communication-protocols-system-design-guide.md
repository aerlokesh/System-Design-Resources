# Communication Protocols - Complete System Design Interview Guide

## Table of Contents
- [Communication Protocols - Complete System Design Interview Guide](#communication-protocols---complete-system-design-interview-guide)
  - [Table of Contents](#table-of-contents)
  - [Introduction \& Overview](#introduction--overview)
    - [Why Protocol Selection is Critical in System Design](#why-protocol-selection-is-critical-in-system-design)
    - [The Protocol Selection Interview Framework](#the-protocol-selection-interview-framework)
    - [Quick Protocol Selection Cheat Sheet](#quick-protocol-selection-cheat-sheet)
  - [HTTP/HTTPS \& REST API](#httphttps--rest-api)
    - [What is HTTP/REST?](#what-is-httprest)
    - [HTTP Request/Response Anatomy](#http-requestresponse-anatomy)
    - [HTTP Status Codes - Interview Guide](#http-status-codes---interview-guide)
    - [REST API Design Best Practices](#rest-api-design-best-practices)
    - [HTTP/2 \& HTTP/3 - Modern Improvements](#http2--http3---modern-improvements)
    - [When to Use HTTP/REST](#when-to-use-httprest)
    - [REST API Performance Optimization](#rest-api-performance-optimization)
    - [REST API Design Anti-Patterns](#rest-api-design-anti-patterns)
    - [Interview Talking Points for REST](#interview-talking-points-for-rest)
  - [gRPC - Google Remote Procedure Call](#grpc---google-remote-procedure-call)
    - [What is gRPC?](#what-is-grpc)
    - [Protocol Buffers (protobuf)](#protocol-buffers-protobuf)
    - [gRPC Communication Patterns](#grpc-communication-patterns)
    - [gRPC Performance Deep Dive](#grpc-performance-deep-dive)
    - [gRPC Features](#grpc-features)
    - [When to Use gRPC](#when-to-use-grpc)
    - [gRPC vs REST - Detailed Comparison](#grpc-vs-rest---detailed-comparison)
    - [Interview Talking Points for gRPC](#interview-talking-points-for-grpc)
  - [WebSocket - Full-Duplex Communication](#websocket---full-duplex-communication)
    - [What is WebSocket?](#what-is-websocket)
    - [WebSocket Handshake \& Protocol](#websocket-handshake--protocol)
    - [WebSocket Communication Patterns](#websocket-communication-patterns)
    - [WebSocket Scalability \& Best Practices](#websocket-scalability--best-practices)
    - [WebSocket vs Alternatives](#websocket-vs-alternatives)
    - [Interview Talking Points for WebSocket](#interview-talking-points-for-websocket)
  - [GraphQL - Query Language for APIs](#graphql---query-language-for-apis)
    - [What is GraphQL?](#what-is-graphql)
    - [GraphQL vs REST - The Problem GraphQL Solves](#graphql-vs-rest---the-problem-graphql-solves)
    - [GraphQL Core Concepts](#graphql-core-concepts)
    - [GraphQL Resolver Implementation](#graphql-resolver-implementation)
    - [GraphQL Performance Optimization](#graphql-performance-optimization)
    - [GraphQL Best Practices](#graphql-best-practices)
    - [When to Use GraphQL](#when-to-use-graphql)
    - [GraphQL vs REST - Detailed Comparison](#graphql-vs-rest---detailed-comparison)
    - [Interview Talking Points for GraphQL](#interview-talking-points-for-graphql)
  - [Server-Sent Events (SSE) - Unidirectional Push](#server-sent-events-sse---unidirectional-push)
    - [What is Server-Sent Events?](#what-is-server-sent-events)
    - [SSE Protocol Details](#sse-protocol-details)
    - [SSE Client Implementation](#sse-client-implementation)

---

## Introduction & Overview

### Why Protocol Selection is Critical in System Design

In system design interviews, choosing the right communication protocol can make or break your architecture. The protocol you select affects:

```
Performance Impact:
├── Latency: 1ms (WebSocket) vs 100ms (REST)
├── Throughput: 1M msg/sec (Kafka) vs 10K req/sec (REST)
├── Bandwidth: 70% reduction with gRPC vs REST
└── Battery life: MQTT uses 1/10th power of HTTP polling

Cost Impact:
├── Infrastructure: Wrong choice = 10x more servers
├── Bandwidth: Inefficient protocol = $100K+/month extra
├── Development: Complex protocol = months of work
└── Maintenance: Protocol mismatch = constant firefighting

Scale Impact:
├── Real-time requirements: <100ms latency
├── High throughput: >100K requests/second
├── Concurrent connections: 1M+ WebSocket connections
└── Global distribution: CDN, edge computing
```

### The Protocol Selection Interview Framework

**What interviewers look for:**

```
1. Understanding of trade-offs
   ✓ "I'd use WebSocket for real-time bidirectional chat because..."
   ✗ "I'd use WebSocket because it's faster"

2. Matching protocol to use case
   ✓ "For microservices, gRPC provides 5-10x better performance than REST"
   ✗ "I'd use REST for everything"

3. Consideration of constraints
   ✓ "For mobile clients, we need HTTP/REST for battery efficiency"
   ✗ "WebSocket works everywhere"

4. Scalability awareness
   ✓ "At 1M concurrent connections, we need sticky sessions for WebSocket"
   ✗ "Just use load balancer"

5. Real-world experience
   ✓ "Twitter uses hybrid fanout - push for regular users, pull for celebrities"
   ✗ "Twitter uses WebSocket"
```

### Quick Protocol Selection Cheat Sheet

```
┌─────────────────────┬──────────────────────────────────┐
│ Use Case            │ Recommended Protocol             │
├─────────────────────┼──────────────────────────────────┤
│ Public REST API     │ HTTP/REST (JSON)                 │
│ Microservices       │ gRPC (Protocol Buffers)          │
│ Real-time Chat      │ WebSocket                        │
│ Live Notifications  │ WebSocket or SSE                 │
│ Video Streaming     │ HTTP/2 (adaptive), UDP (RTP)     │
│ IoT Sensors         │ MQTT                             │
│ Event Streaming     │ Apache Kafka                     │
│ Task Queues         │ RabbitMQ or AWS SQS              │
│ Mobile-Backend      │ HTTP/REST + WebSocket            │
│ Gaming (realtime)   │ UDP + TCP (hybrid)               │
└─────────────────────┴──────────────────────────────────┘
```

---

## HTTP/HTTPS & REST API

### What is HTTP/REST?

**HTTP (Hypertext Transfer Protocol)**
```
Foundation: TCP/IP protocol suite
Port: 80 (HTTP), 443 (HTTPS)
Version History:
├── HTTP/1.0 (1996): One request per connection
├── HTTP/1.1 (1999): Keep-alive, pipelining
├── HTTP/2 (2015): Multiplexing, server push
└── HTTP/3 (2022): QUIC (UDP-based), 0-RTT

Key Characteristics:
├── Stateless: No client state stored between requests
├── Request-Response: Client initiates, server responds
├── Text-based: Human-readable headers (HTTP/1.1)
├── Methods: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
└── Status Codes: 2xx Success, 3xx Redirect, 4xx Client Error, 5xx Server Error
```

**REST (Representational State Transfer)**
```
Architectural Principles:
1. Client-Server: Separation of concerns
2. Stateless: Each request self-contained
3. Cacheable: Responses must define cacheability
4. Uniform Interface: Resource-based URIs
5. Layered System: Client can't tell if connected to end server
6. Code on Demand (optional): Server can extend client functionality

Resource-Oriented Design:
├── Resources identified by URIs: /users/123
├── Representations: JSON, XML, HTML
├── Standard methods mapped to CRUD:
│   GET → Read
│   POST → Create
│   PUT/PATCH → Update
│   DELETE → Delete
└── HATEOAS: Hypermedia as the Engine of Application State
```

### HTTP Request/Response Anatomy

**HTTP Request:**
```
GET /api/v1/users/123 HTTP/1.1
Host: api.example.com
Accept: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
User-Agent: Mozilla/5.0
Accept-Encoding: gzip, deflate
Connection: keep-alive
Cache-Control: no-cache

Components:
├── Request Line: Method, URI, HTTP Version
├── Headers: Key-value pairs
├── Empty Line: Separates headers from body
└── Body: Optional (for POST, PUT, PATCH)
```

**HTTP Response:**
```
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 324
Cache-Control: max-age=3600
ETag: "33a64df551425fcc55e4"
Date: Mon, 10 Jan 2025 10:00:00 GMT

{
  "id": 123,
  "name": "John Doe",
  "email": "john@example.com",
  "created_at": "2025-01-01T00:00:00Z"
}

Components:
├── Status Line: HTTP Version, Status Code, Reason Phrase
├── Headers: Metadata about response
├── Empty Line
└── Body: Response payload
```

### HTTP Status Codes - Interview Guide

```
2xx Success:
200 OK              → GET, PUT, PATCH successful
201 Created         → POST successful, resource created
202 Accepted        → Request accepted, processing async
204 No Content      → DELETE successful, no body returned
206 Partial Content → Range request successful (video streaming)

3xx Redirection:
301 Moved Permanently    → Resource permanently moved
302 Found               → Temporary redirect
304 Not Modified        → Cached version still valid
307 Temporary Redirect  → Method must not change
308 Permanent Redirect  → Method must not change

4xx Client Errors:
400 Bad Request          → Invalid syntax, malformed request
401 Unauthorized         → Authentication required or failed
403 Forbidden            → Authenticated but not authorized
404 Not Found            → Resource doesn't exist
405 Method Not Allowed   → GET on POST-only endpoint
408 Request Timeout      → Client took too long
409 Conflict             → Resource state conflict (concurrent update)
410 Gone                 → Resource permanently deleted
413 Payload Too Large    → Request body exceeds limit
429 Too Many Requests    → Rate limit exceeded

5xx Server Errors:
500 Internal Server Error → Generic server error
502 Bad Gateway           → Invalid response from upstream
503 Service Unavailable   → Server overloaded or maintenance
504 Gateway Timeout       → Upstream server didn't respond
```

### REST API Design Best Practices

**1. Resource Naming Conventions**
```
✅ Good:
GET    /api/v1/users                 # Collection
GET    /api/v1/users/123             # Specific resource
GET    /api/v1/users/123/orders      # Sub-resource
GET    /api/v1/orders?user_id=123    # Alternative (query param)

✗ Bad:
GET    /api/v1/getUsers              # Don't use verbs in URIs
GET    /api/v1/user                  # Use plural nouns
GET    /api/v1/Users                 # Use lowercase
GET    /api/v1/users/delete/123      # Use DELETE method instead

Nesting Guidelines:
├── Maximum 2 levels: /resource/id/sub-resource
├── Prefer flat structure with query params for complex relationships
└── Use query params for filtering: /users?role=admin&active=true
```

**2. HTTP Method Usage**
```
GET - Retrieve resource(s):
├── Idempotent: Multiple identical requests = same result
├── Safe: No side effects
├── Cacheable: Can be cached by proxies
└── No request body

POST - Create resource:
├── NOT idempotent: Creates new resource each time
├── NOT safe: Has side effects
├── NOT cacheable (usually)
└── Request body contains data

PUT - Replace entire resource:
├── Idempotent: Multiple identical requests = same result
├── Request body contains complete resource
└── Creates resource if doesn't exist (optional)

PATCH - Partial update:
├── Ideally idempotent (but not required)
├── Request body contains only fields to update
└── RFC 7396 (JSON Merge Patch) or RFC 6902 (JSON Patch)

DELETE - Remove resource:
├── Idempotent: Multiple requests = same result
├── 204 No Content or 200 OK with body
└── Soft delete vs hard delete consideration
```

**3. Versioning Strategies**
```
URI Versioning (Most Common):
https://api.example.com/v1/users
https://api.example.com/v2/users
✓ Clear, explicit
✓ Easy to implement
✗ URI changes, breaks caching

Header Versioning:
GET /users
Accept: application/vnd.example.v1+json
✓ URI stays same
✗ Less visible, harder to test

Query Parameter:
GET /users?version=1
✗ Generally not recommended

Recommendation: URI versioning for simplicity
```

**4. Pagination**
```
Offset-Based (Traditional):
GET /users?offset=20&limit=10
Response:
{
  "data": [...],
  "pagination": {
    "offset": 20,
    "limit": 10,
    "total": 1000,
    "has_more": true
  }
}

✓ Simple to implement
✗ Performance degrades with large offsets
✗ Inconsistent results if data changes

Cursor-Based (Recommended for large datasets):
GET /users?cursor=eyJpZCI6MTIzfQ==&limit=10
Response:
{
  "data": [...],
  "pagination": {
    "next_cursor": "eyJpZCI6MTMzfQ==",
    "has_more": true
  }
}

✓ Consistent results
✓ Better performance
✗ Can't jump to specific page
```

**5. Error Response Format**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input data",
    "details": [
      {
        "field": "email",
        "message": "Invalid email format",
        "code": "INVALID_FORMAT"
      },
      {
        "field": "age",
        "message": "Must be at least 18",
        "code": "MIN_VALUE_ERROR"
      }
    ],
    "request_id": "req_abc123",
    "timestamp": "2025-01-10T10:00:00Z"
  }
}

Guidelines:
├── Consistent error structure across all endpoints
├── Machine-readable error codes
├── Human-readable messages
├── Include request_id for debugging
└── Provide actionable details
```

### HTTP/2 & HTTP/3 - Modern Improvements

**HTTP/2 Features (2015)**
```
1. Binary Protocol:
   HTTP/1.1: Text-based (human-readable)
   HTTP/2: Binary framing (efficient parsing)
   
2. Multiplexing:
   Problem (HTTP/1.1): Head-of-line blocking
   ├── Request 1: 100ms
   ├── Request 2: Must wait for Request 1
   └── Request 3: Must wait for Request 2
   
   Solution (HTTP/2): Parallel requests on single connection
   ├── Request 1, 2, 3: All sent simultaneously
   ├── Responses: Can arrive in any order
   └── Result: 3x-5x faster page loads

3. Header Compression (HPACK):
   HTTP/1.1 Headers: 800+ bytes per request
   HTTP/2 Headers: 50-100 bytes (87% reduction)
   
4. Server Push:
   Client requests: index.html
   Server pushes: style.css, script.js (proactively)
   Result: Faster page load

5. Stream Prioritization:
   Critical resources: Higher priority
   Non-critical: Lower priority
   
Performance Impact:
├── Page Load Time: 30-50% faster
├── Bandwidth: 40% reduction
├── Mobile: Significant improvement
└── Adoption: 97% of browsers support
```

**HTTP/3 Features (2022)**
```
Built on QUIC (Quick UDP Internet Connections):

1. UDP Instead of TCP:
   TCP: 3-way handshake (1 RTT) + TLS (1-2 RTT) = 2-3 RTT
   QUIC: Combined handshake (0-1 RTT)
   
2. No Head-of-Line Blocking:
   HTTP/2 on TCP: Packet loss blocks ALL streams
   HTTP/3 on QUIC: Packet loss blocks ONLY affected stream
   
3. Connection Migration:
   Scenario: Switch from WiFi to 4G on mobile
   TCP: Connection drops, must reconnect
   QUIC: Connection persists seamlessly
   
4. Performance Improvements:
   ├── 0-RTT Connection Resumption
   ├── Better loss recovery
   ├── Improved congestion control
   └── 20-30% faster than HTTP/2 on lossy networks

Adoption (2025):
├── Browsers: 95% support
├── CDNs: CloudFl are, Fastly, Akamai
├── Web Servers: NGINX, Apache (experimental)
└── Use: 25% of web traffic
```

### When to Use HTTP/REST

**✅ Perfect For:**
```
1. Public APIs:
   - Third-party integrations
   - Developer-friendly (curl, Postman)
   - Documentation (OpenAPI/Swagger)
   - Wide language support
   
   Example: Stripe API, Twitter API, GitHub API

2. CRUD Applications:
   - Standard operations
   - Resource-based architecture
   - Straightforward mapping
   
   Example: E-commerce product catalog, Blog CMS

3. Mobile Apps:
   - Native HTTP clients
   - Easy debugging
   - Offline support with caching
   
   Example: Instagram, Reddit mobile apps

4. Web Applications:
   - Browser-native support
   - XMLHttpRequest, Fetch API
   - CORS handling
   
   Example: Any SaaS web application

5. Microservices (External APIs):
   - Service discovery compatibility
   - API Gateway integration
   - Rate limiting, authentication
   
   Example: Backend-for-Frontend (BFF) pattern
```

**❌ Not Ideal For:**
```
1. Real-Time Bidirectional:
   Problem: Polling overhead, latency
   Better: WebSocket
   Example: Chat applications

2. High-Frequency Updates:
   Problem: HTTP overhead per request
   Better: WebSocket, SSE
   Example: Live stock prices (10+ updates/sec)

3. Internal Microservices (High Performance):
   Problem: JSON overhead, HTTP headers
   Better: gRPC
   Example: Payment processing microservices

4. Streaming Large Data:
   Problem: Buffering, memory
   Better: gRPC streaming, WebSocket
   Example: Video upload, large file transfer

5. Low-Latency Requirements (<10ms):
   Problem: HTTP overhead
   Better: gRPC, raw TCP
   Example: High-frequency trading
```

### REST API Performance Optimization

**1. HTTP Caching**
```
Cache-Control Header:
Cache-Control: public, max-age=3600, stale-while-revalidate=60

Directives:
├── public: Can be cached by any cache
├── private: Only browser cache
├── no-cache: Must revalidate with server
├── no-store: Don't cache at all
├── max-age=<seconds>: How long to cache
├── s-maxage=<seconds>: CDN/proxy cache duration
└── stale-while-revalidate=<seconds>: Serve stale while fetching fresh

ETag (Entity Tag):
Response: ETag: "33a64df551425fcc55e4"
Next Request: If-None-Match: "33a64df551425fcc55e4"
Response: 304 Not Modified (no body, saves bandwidth)

Last-Modified:
Response: Last-Modified: Mon, 10 Jan 2025 10:00:00 GMT
Next Request: If-Modified-Since: Mon, 10 Jan 2025 10:00:00 GMT
Response: 304 Not Modified

Caching Strategy by Resource Type:
├── Static assets (JS, CSS, images): 1 year, immutable
├── API responses: 1-60 minutes
├── User profiles: 5-15 minutes
├── Dynamic content: no-cache or short max-age
└── Private data: private, no-store
```

**2. Compression**
```
Content-Encoding:
├── gzip: 70-90% reduction, widely supported
├── br (Brotli): 20% better than gzip, HTTP/2+
└── deflate: Older, use gzip instead

Request:
Accept-Encoding: gzip, deflate, br

Response:
Content-Encoding: gzip
Content-Length: 1234 (compressed size)

Impact:
├── JSON payload: 5KB → 500 bytes (90% reduction)
├── Page load time: 30-50% faster
├── Bandwidth cost: 70-90% reduction
└── CPU cost: Minimal (worth the trade-off)
```

**3. Connection Reuse**
```
HTTP/1.1 Keep-Alive:
Connection: keep-alive
Keep-Alive: timeout=5, max=100

Benefits:
├── Avoid TCP handshake overhead (3-way handshake)
├── Avoid TLS handshake (1-2 RTT)
├── 50-100ms saved per request
└── Lower server load

HTTP/2:
├── Single connection multiplexes requests
├── Automatic connection reuse
└── No need for connection pooling tricks
```

**4. Rate Limiting Headers**
```
Response Headers:
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 995
X-RateLimit-Reset: 1704978000

When limit exceeded:
HTTP/1.1 429 Too Many Requests
Retry-After: 60
{
  "error": "Rate limit exceeded",
  "retry_after": 60
}

Implementation:
├── Token bucket algorithm
├── Sliding window
├── Fixed window
└── Redis for distributed rate limiting
```

### REST API Design Anti-Patterns

**❌ Common Mistakes:**
```
1. Verbs in URLs:
   Bad:  POST /api/createUser
   Good: POST /api/users

2. Ignoring HTTP Methods:
   Bad:  POST /api/users/delete/123
   Good: DELETE /api/users/123

3. Not Using Status Codes Properly:
   Bad:  200 OK with {"error": "User not found"}
   Good: 404 Not Found

4. No Versioning:
   Bad:  Breaking changes in same API
   Good: /api/v1/users, /api/v2/users

5. Exposing Internal Implementation:
   Bad:  /api/users?sql=SELECT * FROM users
   Good: /api/users?filter=active

6. Not Paginating:
   Bad:  Return all 1M users
   Good: Return 20 users with cursor

7. Ignoring Caching:
   Bad:  No cache headers
   Good: Proper Cache-Control, ETag

8. Over-fetching:
   Bad:  Return entire user object when only name needed
   Good: Support field selection: ?fields=name,email

9. No Request ID:
   Bad:  Error occurred, no way to trace
   Good: request_id in response for debugging

10. Inconsistent Naming:
    Bad:  /api/users, /api/getOrders, /api/product_list
    Good: /api/users, /api/orders, /api/products
```

### Interview Talking Points for REST

**When interviewer asks: "Why use REST?"**

```
"I'd recommend REST for this system because:

1. Public API Requirements:
   - We need third-party integrations
   - REST is universally supported
   - Easy for developers to integrate (curl, Postman)
   - Self-documenting with OpenAPI/Swagger

2. Caching Benefits:
   - HTTP caching reduces server load by 80-90%
   - CDN can cache responses at edge
   - Browser caching improves user experience

3. Tooling Ecosystem:
   - API gateways (Kong, AWS API Gateway)
   - Monitoring (Datadog, New Relic)
   - Testing (Postman, REST Assured)

4. Scalability:
   - Stateless nature enables horizontal scaling
   - Load balancer friendly (no sticky sessions)
   - Can add servers without coordination

5. HTTP/2 Benefits:
   - Multiplexing reduces latency
   - Header compression saves bandwidth
   - Server push for optimization

Trade-offs to mention:
- For internal microservices, gRPC would be 5-10x faster
- For real-time features, we'd add WebSocket
- HTTP overhead: ~800 bytes per request vs ~28 bytes for gRPC
"
```

**When interviewer asks about performance:**

```
"REST performance considerations:

Payload Size:
- JSON overhead: User object ~150 bytes
- With HTTP headers: ~950 bytes total
- gRPC equivalent: ~50 bytes (19x smaller)

Latency Breakdown:
- DNS lookup: 20-120ms (cached after first)
- TCP handshake: 50-100ms
- TLS handshake: 50-100ms
- HTTP request: 10-50ms
- Total: 130-370ms for first request
- Subsequent (keep-alive): 10-50ms

Optimization strategies:
1. HTTP/2: Multiplexing saves 100-200ms per page load
2. Caching: 80-90% requests served from cache (1-5ms)
3. CDN: Serve from edge (~20ms vs ~200ms from origin)
4. Compression: 70-90% bandwidth reduction
5. Connection pooling: Reuse connections

For our scale (100K requests/sec):
- With caching: 10K to database
- With CDN: 5K to origin
- Result: System can handle 10x scale"
```

---

## gRPC - Google Remote Procedure Call

### What is gRPC?

```
gRPC: High-performance, open-source RPC framework
├── Developed by Google (2015, open-sourced 2016)
├── Built on HTTP/2
├── Uses Protocol Buffers (protobuf) for serialization
├── Supports 11+ languages
└── Production use: Google, Netflix, Square, Cisco

Key Characteristics:
├── 10x smaller payloads than JSON
├── 5-10x faster than REST
├── Strongly typed
├── Code generation for client/server
└── 4 communication patterns
```

### Protocol Buffers (protobuf)

**What is Protobuf?**
```
Protocol Buffers: Binary serialization format
├── Developed by Google (2001, open-sourced 2008)
├── Language-neutral, platform-neutral
├── Compact binary format
├── Backward and forward compatible
└── Auto-generates code from .proto files

Comparison:
JSON: Text-based, human-readable, large
Protobuf: Binary, efficient, compact
```

**Proto File Example:**
```protobuf
// user.proto
syntax = "proto3";

package user_service;

// Message definition (like a struct/class)
message User {
  int64 user_id = 1;           // Field number (not value)
  string username = 2;
  string email = 3;
  int32 age = 4;
  repeated string roles = 5;    // Array/list
  Address address = 6;          // Nested message
  google.protobuf.Timestamp created_at = 7;
}

message Address {
  string street = 1;
  string city = 2;
  string country = 3;
  string postal_code = 4;
}

// Request/Response messages
message GetUserRequest {
  int64 user_id = 1;
}

message GetUserResponse {
  User user = 1;
  bool found = 2;
}

message ListUsersRequest {
  int32 page_size = 1;
  string page_token = 2;
  string filter = 3;            // e.g., "age > 18 AND country = 'US'"
}

message ListUsersResponse {
  repeated User users = 1;
  string next_page_token = 2;
  int32 total_count = 3;
}

message CreateUserRequest {
  User user = 1;
}

message CreateUserResponse {
  User user = 1;
  string message = 2;
}

message UpdateUserRequest {
  User user = 1;
  google.protobuf.FieldMask update_mask = 2;  // Specify fields to update
}

message UpdateUserResponse {
  User user = 1;
}

message DeleteUserRequest {
  int64 user_id = 1;
}

message DeleteUserResponse {
  bool success = 1;
  string message = 2;
}

// Service definition (like interface/API)
service UserService {
  // Unary RPC: Single request → Single response
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
  
  // Server streaming: Single request → Stream of responses
  rpc ListUsers(ListUsersRequest) returns (stream User);
  
  // Client streaming: Stream of requests → Single response
  rpc CreateUsers(stream CreateUserRequest) returns (CreateUserResponse);
  
  // Bidirectional streaming: Stream ↔ Stream
  rpc Chat(stream ChatMessage) returns (stream ChatMessage);
}

// Additional message types
message ChatMessage {
  int64 user_id = 1;
  string message = 2;
  google.protobuf.Timestamp timestamp = 3;
}
```

**Code Generation:**
```bash
# Generate code for multiple languages
protoc --go_out=. user.proto          # Go
protoc --java_out=. user.proto        # Java
protoc --python_out=. user.proto      # Python
protoc --cpp_out=. user.proto         # C++
protoc --csharp_out=. user.proto      # C#

# Generated files provide:
# 1. Message classes with getters/setters
# 2. Serialization/deserialization
# 3. Client stubs
# 4. Server interfaces
```

### gRPC Communication Patterns

**1. Unary RPC (Request-Response)**
```
Pattern: Client → Request → Server → Response → Client

Use Case: Traditional API call

Example:
Client: GetUser(user_id=123)
Server: Returns User object

Flow:
┌────────┐                           ┌────────┐
│ Client │ ──GetUserRequest(123)──→ │ Server │
│        │ ←──GetUserResponse────── │        │
└────────┘                           └────────┘

Performance:
├── Latency: 5-10ms (vs 50-100ms REST)
├── Payload: 50 bytes (vs 950 bytes REST)
└── Throughput: 100K RPS on single server

Code Example (Go):
// Client
client := pb.NewUserServiceClient(conn)
response, err := client.GetUser(ctx, &pb.GetUserRequest{
  UserId: 123,
})
if err != nil {
  log.Fatal(err)
}
fmt.Println(response.User)
```

**2. Server Streaming RPC**
```
Pattern: Client → Request → Server → Stream[Response] → Client

Use Case: 
├── Large result sets (pagination without multiple requests)
├── Real-time updates
├── File download
└── Live data feeds

Example:
Client: ListUsers()
Server: Streams back users one by one

Flow:
┌────────┐                           ┌────────┐
│ Client │ ──ListUsersRequest────→  │ Server │
│        │ ←──User 1──────────────── │        │
│        │ ←──User 2──────────────── │        │
│        │ ←──User 3──────────────── │        │
│        │ ←──... (stream)────────── │        │
└────────┘                           └────────┘

Benefits:
├── Single connection
├── Backpressure handling
├── Cancel anytime
└── Memory efficient (streaming)

Code Example (Go):
// Client
stream, err := client.ListUsers(ctx, &pb.ListUsersRequest{})
for {
  user, err := stream.Recv()
  if err == io.EOF {
    break
  }
  if err != nil {
    log.Fatal(err)
  }
  fmt.Println(user)
}
```

**3. Client Streaming RPC**
```
Pattern: Client → Stream[Request] → Server → Response → Client

Use Case:
├── File upload
├── Batch data ingestion
├── Log aggregation
└── Telemetry data

Example:
Client: Streams multiple CreateUserRequests
Server: Returns single CreateUserResponse with summary

Flow:
┌────────┐                           ┌────────┐
│ Client │ ──CreateUserReq 1──────→ │ Server │
│        │ ──CreateUserReq 2──────→ │        │
│        │ ──CreateUserReq 3──────→ │        │
│        │ ──... (stream)──────────→ │        │
│        │ ←──CreateUserResponse──── │        │
└────────┘                           └────────┘

Benefits:
├── Efficient batch processing
├── Single acknowledgment
├── Reduced network overhead
└── Server-side batching

Code Example (Go):
// Client
stream, err := client.CreateUsers(ctx)
for _, user := range users {
  if err := stream.Send(&pb.CreateUserRequest{User: user}); err != nil {
    log.Fatal(err)
  }
}
response, err := stream.CloseAndRecv()
```

**4. Bidirectional Streaming RPC**
```
Pattern: Client ↔ Stream ↔ Server

Use Case:
├── Real-time chat
├── Collaborative editing
├── Online gaming
├── Live audio/video
└── Interactive sessions

Example:
Both client and server send messages independently

Flow:
┌────────┐                           ┌────────┐
│ Client │ ──Message 1─────────────→ │ Server │
│        │ ←──Response A──────────── │        │
│        │ ──Message 2─────────────→ │        │
│        │ ←──Response B──────────── │        │
│        │ ←──Notification X──────── │        │
│        │ ──Message 3─────────────→ │        │
└────────┘                           └────────┘

Benefits:
├── Full-duplex communication
├── Independent send/receive
├── Low latency
└── Efficient for conversational patterns

Code Example (Go):
// Client
stream, err := client.Chat(ctx)
go func() {
  for {
    msg, err := stream.Recv()
    if err != nil { return }
    fmt.Println("Received:", msg)
  }
}()

stream.Send(&pb.ChatMessage{Message: "Hello"})
stream.Send(&pb.ChatMessage{Message: "How are you?"})
```

### gRPC Performance Deep Dive

**Payload Size Comparison:**
```
User Object:
{
  "user_id": 12345,
  "username": "johndoe",
  "email": "john@example.com",
  "age": 30,
  "roles": ["user", "admin"]
}

JSON (REST):
├── Size: 145 bytes (minified)
├── With HTTP/1.1 headers: ~950 bytes
└── Content-Type, Authorization, etc.

Protocol Buffers (gRPC):
├── Size: 28 bytes (binary)
├── With HTTP/2 headers: ~50 bytes (compressed)
└── Result: 19x smaller!

At Scale:
1M requests/day × 950 bytes = 950 MB/day (REST)
1M requests/day × 50 bytes = 50 MB/day (gRPC)
Bandwidth savings: 900 MB/day = 27 GB/month
```

**Latency Comparison:**
```
Microservice Call (GetUser):

REST/JSON:
├── TCP connection: Reused from pool
├── HTTP request build: 0.5ms
├── JSON serialization: 1ms
├── Network: 2ms
├── Server processing: 2ms
├── JSON deserialization: 1ms
├── HTTP response parse: 0.5ms
└── Total: ~7ms

gRPC/Protobuf:
├── HTTP/2 connection: Multiplexed
├── Protobuf serialization: 0.1ms
├── Network: 2ms
├── Server processing: 2ms
├── Protobuf deserialization: 0.1ms
└── Total: ~4.2ms

Result: 40% faster, 5ms saved per call

At Scale:
├── 10K calls/sec
├── Savings: 50 seconds/sec = 50 cores freed!
└── Cost savings: Significant at cloud scale
```

**Throughput Comparison:**
```
Single Server Capacity:

REST API (Node.js):
├── Parse JSON: CPU intensive
├── HTTP/1.1 overhead
├── Throughput: 10-20K requests/second
└── With clustering: 50K requests/second

gRPC (Go):
├── Binary parsing: Fast
├── HTTP/2 multiplexing
├── Throughput: 100-200K requests/second
└── With clustering: 500K+ requests/second

Result: 10x higher throughput with gRPC
```

### gRPC Features

**1. Deadlines/Timeouts**
```
Problem: Cascading failures in microservices

Solution: Set deadline for each RPC

Client Code:
ctx, cancel := context.WithTimeout(context.Background(), time.Second)
defer cancel()

response, err := client.GetUser(ctx, request)
if err != nil {
  if status.Code(err) == codes.DeadlineExceeded {
    log.Println("Request timed out")
  }
}

Benefits:
├── Prevent hanging requests
├── Fail fast
├── Resource cleanup
└── Better error handling
```

**2. Interceptors (Middleware)**
```
Server Interceptor (Logging):
func loggingInterceptor(ctx context.Context, req interface{}, 
  info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (interface{}, error) {
  
  start := time.Now()
  resp, err := handler(ctx, req)
  duration := time.Since(start)
  
  log.Printf("Method: %s, Duration: %v, Error: %v", 
    info.FullMethod, duration, err)
  
  return resp, err
}

Use Cases:
├── Authentication/authorization
├── Logging and monitoring
├── Rate limiting
├── Error handling
└── Request tracing
```

**3. Load Balancing**
```
Client-Side Load Balancing:

conn, err := grpc.Dial("dns:///my-service:50051",
  grpc.WithBalancerName("round_robin"))

Strategies:
├── Round Robin: Distribute evenly
├── Least Request: Send to least loaded
├── Ring Hash: Consistent hashing
└── Custom: Implement your own

Benefits:
├── No separate load balancer needed
├── Direct connections to servers
├── Lower latency
└── Better resource utilization
```

**4. Health Checking**
```
Service Health API:
service Health {
  rpc Check(HealthCheckRequest) returns (HealthCheckResponse);
  rpc Watch(HealthCheckRequest) returns (stream HealthCheckResponse);
}

Response Status:
├── SERVING: Healthy
├── NOT_SERVING: Unhealthy
├── UNKNOWN: Status unknown
└── SERVICE_UNKNOWN: Service doesn't exist

Load balancers use health checks to route traffic
```

### When to Use gRPC

**✅ Perfect For:**
```
1. Microservices Communication:
   - Internal service-to-service
   - Low latency required (<10ms)
   - High throughput (>50K RPS)
   
   Example:
   Order Service → Payment Service
   Auth Service → User Service
   
2. Mobile to Backend:
   - Efficient binary protocol
   - Battery-friendly (less CPU for parsing)
   - Smaller payloads (save cellular data)
   
   Example: Google Maps, YouTube mobile app

3. Real-time Systems:
   - Bidirectional streaming
   - Low latency
   - Efficient multiplexing
   
   Example: Trading platforms, gaming servers

4. Polyglot Systems:
   - Multiple languages
   - Code generation ensures compatibility
   - Strongly typed contracts
   
   Example: Java backend, Python ML service, Go API gateway

5. High-Performance APIs:
   - CPU-intensive operations
   - Tight latency budgets
   - Binary efficiency critical
   
   Example: Payment processing, ad serving
```

**❌ Not Ideal For:**
```
1. Browser Clients:
   - Limited browser support (requires grpc-web)
   - Not native like HTTP
   - Extra proxy layer needed
   
   Better: REST API for browsers

2. Public APIs:
   - Harder for third parties to integrate
   - Less tooling (no Postman equivalent)
   - Not human-readable (debugging harder)
   
   Better: REST API for external consumption

3. Simple CRUD:
   - Overkill for basic operations
   - Proto file maintenance overhead
   - REST is simpler
   
   Better: REST for simple apps

4. Legacy Systems:
   - May not support HTTP/2
   - Integration complexity
   - Migration cost
   
   Better: Keep using existing protocols

5. Debugging-Heavy Environments:
   - Binary format hard to inspect
   - Need special tools
   - Learning curve
   
   Better: REST for easier debugging
```

### gRPC vs REST - Detailed Comparison

```
┌─────────────────┬──────────────┬──────────────┐
│ Aspect          │ gRPC         │ REST         │
├─────────────────┼──────────────┼──────────────┤
│ Protocol        │ HTTP/2       │ HTTP/1.1/2   │
│ Payload         │ Protobuf     │ JSON         │
│ Size            │ Small (28B)  │ Large (950B) │
│ Speed           │ Fast (5ms)   │ Slower (50ms)│
│ Browser Support │ Limited      │ Native       │
│ Streaming       │ Built-in     │ Not native   │
│ Code Gen        │ Yes          │ Optional     │
│ Human Readable  │ No           │ Yes          │
│ Caching         │ Complex      │ Native       │
│ Learning Curve  │ Steep        │ Gentle       │
│ Use Case        │ Internal     │ Public       │
└─────────────────┴──────────────┴──────────────┘

Decision Matrix:
├── Internal microservices, high perf → gRPC
├── Public API, 3rd party integration → REST
├── Both needed → API Gateway pattern (REST frontend, gRPC backend)
└── Real-time + HTTP → WebSocket or gRPC streaming
```

### Interview Talking Points for gRPC

**When interviewer asks: "Should we use gRPC?"**

```
"For this system, I'd recommend gRPC for internal microservices:

1. Performance Benefits:
   - 5-10x lower latency than REST (5ms vs 50ms)
   - 10x smaller payloads (50 bytes vs 950 bytes)
   - Can handle 100K RPS per server (vs 10K for REST)
   - At our scale (1M requests/day), this means:
     * 10x less bandwidth cost
     * 10x fewer servers needed
     * Significant cost savings

2. Strong Typing:
   - Proto files define exact contract
   - Compile-time type checking
   - Prevents integration bugs
   - Auto-generated documentation

3. Streaming Support:
   - Bidirectional streaming built-in
   - Perfect for: Real-time features, large data transfer
   - More efficient than WebSocket for server-to-server

4. Multi-Language Support:
   - Our stack: Java (API), Python (ML), Go (Gateway)
   - gRPC generates code for all three
   - Consistent experience across languages

Architecture:
┌─────────┐     REST      ┌─────────────┐
│ Browser │────────────→  │ API Gateway │
└─────────┘               └──────┬──────┘
                                 │ gRPC
         ┌───────────────────────┼────────────────────┐
         ↓                       ↓                    ↓
   [Auth Service]        [Order Service]      [Payment Service]
         ↑                       ↑                    ↑
         └───────────gRPC────────┴────────gRPC───────┘

External: REST (browser-friendly)
Internal: gRPC (high-performance)

Trade-offs to mention:
- Need grpc-web proxy for browser clients
- Binary format harder to debug (use grpcurl, grpc_cli)
- Learning curve for team
- But: Performance gains justify the investment at our scale
"
```

**When discussing microservices:**

```
"gRPC advantages for microservices:

1. Service Mesh Integration:
   - Works great with Istio, Linkerd
   - Built-in load balancing
   - Health checking
   - Automatic retries

2. Performance at Scale:
   Example: Payment Processing System
   ├── 10K transactions/second
   ├── Each transaction: 5-10 service calls
   ├── Total: 50-100K internal RPC calls/second
   ├── gRPC latency: 5ms per call
   ├── REST latency: 50ms per call
   └── Result: Transaction time: 50ms (gRPC) vs 500ms (REST)

3. Streaming Benefits:
   Use Case: Batch Processing
   ├── Client streams 10K records
   ├── Server processes and streams back results
   ├── Memory efficient (don't buffer all)
   ├── Can cancel mid-stream
   └── Progress updates while processing

4. Error Handling:
   Rich error model with status codes:
   ├── OK, CANCELLED, UNKNOWN
   ├── INVALID_ARGUMENT, DEADLINE_EXCEEDED
   ├── NOT_FOUND, PERMISSION_DENIED
   ├── RESOURCE_EXHAUSTED, UNIMPLEMENTED
   └── With error details for debugging

5. Backward Compatibility:
   Proto evolution:
   ├── Add new fields without breaking clients
   ├── Deprecate fields gradually
   ├── Field numbers never reused
   └── Clients ignore unknown fields

This makes gRPC ideal for our microservices architecture where
we have 20+ services making millions of internal calls per day."
```

---

## WebSocket - Full-Duplex Communication

### What is WebSocket?

```
WebSocket: Full-duplex communication protocol (RFC 6455)
├── Persistent TCP connection
├── Bidirectional: Client ↔ Server
├── Low overhead after handshake
├── Port 80 (ws://), 443 (wss://)
└── Standardized in 2011

Key Characteristics:
├── Real-time: < 100ms latency
├── Persistent: Single long-lived connection
├── Full-duplex: Both sides send independently
├── Low overhead: 2-byte frame header
└── Binary or text: Support both data types
```

### WebSocket Handshake & Protocol

**Handshake Process:**
```
1. HTTP Upgrade Request (Client → Server):
GET /chat HTTP/1.1
Host: server.example.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
Sec-WebSocket-Protocol: chat, superchat
Sec-WebSocket-Extensions: permessage-deflate

2. Upgrade Response (Server → Client):
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
Sec-WebSocket-Protocol: chat

3. Connection Established:
   Now both can send messages anytime
   No HTTP overhead anymore
   Just WebSocket frames

4. Data Frames:
   ┌─────────────────────────────┐
   │ FIN | Opcode | Mask | Len  │  2-14 bytes header
   │ Payload Data                │  Actual data
   └─────────────────────────────┘

   Frame Types (Opcode):
   ├── 0x1: Text frame (UTF-8)
   ├── 0x2: Binary frame
   ├── 0x8: Close frame
   ├── 0x9: Ping frame
   └── 0xA: Pong frame

5. Connection Close:
   Either side sends close frame
   Other side responds with close frame
   TCP connection closed
```

**Message Format Example:**
```javascript
// Text frame (JSON)
{
  "type": "message",
  "from": "user123",
  "to": "user456",
  "text": "Hello!",
  "timestamp": 1704978000
}

// Binary frame (custom protocol)
[0x01, 0x02, 0x03, ...]  // Could be images, video, protobuf

// Control frames
Ping: Keep connection alive
Pong: Respond to ping
Close: Graceful shutdown
```

### WebSocket Communication Patterns

**1. Real-Time Chat**
```
Architecture:
┌─────────┐                        ┌──────────────┐
│ Client A│─────WebSocket────────→ │   WebSocket  │
└─────────┘                        │   Gateway    │
┌─────────┐                        │              │
│ Client B│─────WebSocket────────→ │  (Node.js/   │
└─────────┘                        │   Go Server) │
┌─────────┐                        │              │
│ Client C│─────WebSocket────────→ │              │
└─────────┘                        └──────┬───────┘
                                          │
                                          ↓
                                   ┌──────────────┐
                                   │ Redis Pub/Sub│
                                   └──────┬───────┘
                                          │
              ┌───────────────────────────┼───────────────┐
              ↓                           ↓               ↓
        [Chat Service]            [Notification]   [Message DB]

Message Flow:
1. User A sends message via WebSocket
2. Gateway receives, publishes to Redis
3. All gateway instances subscribed to Redis
4. Gateways push to relevant clients
5. Async: Save to database, send notifications

Benefits:
├── <100ms message delivery
├── Scales horizontally (Redis Pub/Sub)
├── Connection state distributed
└── Can handle millions of connections
```

**2. Live Notifications**
```
Pattern: Server-initiated push

Example - Social Media:
User A likes User B's post
  ↓
Server detects like event
  ↓
Push notification to User B's device
  ↓
User B sees notification immediately

Implementation:
// Server
io.to(userId).emit('notification', {
  type: 'like',
  from: 'user123',
  post: 'post456',
  timestamp: Date.now()
});

// Client
socket.on('notification', (data) => {
  showNotification(data);
});

Advantages:
├── Instant delivery (<100ms)
├── No polling overhead
├── Battery efficient
└── Scales to millions of users
```

**3. Live Updates (Stock Prices, Sports)**
```
Pattern: Server broadcasts to multiple clients

Example - Stock Trading:
Exchange → Price Update → All subscribed clients

Architecture:
┌──────────┐              ┌────────────────┐
│ Exchange │──WebSocket─→ │ Price Service  │
└──────────┘              └────────┬───────┘
                                   │
                                   ↓
                            ┌──────────────┐
                            │ Redis Pub/Sub│
                            └──────┬───────┘
                                   │
        ┌──────────────────────────┼──────────────────┐
        ↓                          ↓                  ↓
  [WS Gateway 1]           [WS Gateway 2]      [WS Gateway 3]
  (10K connections)        (10K connections)   (10K connections)
        │                          │                  │
        ↓                          ↓                  ↓
   [Clients]                  [Clients]          [Clients]

Optimizations:
├── Throttle updates: Max 1/second per symbol
├── Batch multiple symbols in single message
├── Use binary format (Protobuf) to save bandwidth
├── Compress with permessage-deflate
└── Unsubscribe inactive clients

Performance:
├── Can handle 100K concurrent connections per server
├── 1M price updates/second across cluster
├── <100ms latency from exchange to client
└── 200 bytes per update (JSON) vs 50 bytes (Protobuf)
```

### WebSocket Scalability & Best Practices

**1. Connection Management**
```
Client-Side:
class WebSocketManager {
  constructor(url) {
    this.url = url;
    this.reconnectDelay = 1000;
    this.maxReconnectDelay = 30000;
    this.connect();
  }
  
  connect() {
    this.ws = new WebSocket(this.url);
    
    this.ws.onopen = () => {
      console.log('Connected');
      this.reconnectDelay = 1000;  // Reset backoff
      this.authenticate();
      this.subscribe();
    };
    
    this.ws.onclose = () => {
      console.log('Disconnected, reconnecting...');
      setTimeout(() => this.connect(), this.reconnectDelay);
      this.reconnectDelay = Math.min(
        this.reconnectDelay * 2, 
        this.maxReconnectDelay
      );
    };
    
    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }
  
  send(data) {
    if (this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    } else {
      this.queue.push(data);  // Queue for later
    }
  }
}

Best Practices:
├── Automatic reconnection with exponential backoff
├── Queue messages during disconnect
├── Heartbeat/ping-pong every 30 seconds
├── Handle connection state properly
└── Graceful degradation (fall back to long polling)
```

**2. Server-Side Scaling**
```
Problem: 100K connections per server limit

Solution: Horizontal scaling with state sharing

┌─────────┐              ┌──────────────┐
│ Client  │────────────→ │   HAProxy    │ (Sticky session)
└─────────┘              └──────┬───────┘
                                │
        ┌───────────────────────┼───────────────────┐
        ↓                       ↓                   ↓
  [WS Server 1]          [WS Server 2]        [WS Server 3]
  (Node.js)              (Node.js)            (Node.js)
        │                       │                   │
        └───────────────────────┼───────────────────┘
                                ↓
                         ┌──────────────┐
                         │ Redis Pub/Sub│ (Share state)
                         └──────────────┘

Implementation:
// Subscribe to Redis channels
redis.subscribe('notifications:user123');

redis.on('message', (channel, message) => {
  const userId = channel.split(':')[1];
  const socket = activeConnections.get(userId);
  if (socket) {
    socket.send(message);
  }
});

// Publish message to Redis
redis.publish('notifications:user456', JSON.stringify(data));

Benefits:
├── Scale to millions of connections
├── No single point of failure
├── Can add/remove servers dynamically
└── Clients can connect to any server
```

**3. Message Protocols**
```
JSON (Common):
{
  "type": "event_name",
  "data": { ... },
  "id": "msg_123",
  "timestamp": 1704978000
}

✓ Human-readable
✓ Easy to debug
✓ Universal support
✗ Larger payload
✗ Parsing overhead

MessagePack (Better):
Binary JSON-like format
✓ 30% smaller than JSON
✓ Faster to parse
✗ Not human-readable

Protocol Buffers (Best):
✓ 70% smaller than JSON
✓ Strongly typed
✓ Fastest parsing
✗ Need .proto files
✗ Less flexible

Recommendation:
├── Start with JSON
├── Optimize to MessagePack or Protobuf if needed
└── Use compression (permessage-deflate)
```

**4. Authentication & Security**
```
Authentication Strategies:

Option 1: Token in URL
wss://api.example.com/ws?token=JWT_TOKEN
✗ Token visible in logs
✗ Security risk

Option 2: Token in Subprotocol
Sec-WebSocket-Protocol: access_token, JWT_TOKEN
✗ Not standard use of subprotocol

Option 3: Token in First Message (Recommended)
ws.onopen = () => {
  ws.send(JSON.stringify({
    type: 'auth',
    token: JWT_TOKEN
  }));
};

Server validates before accepting other messages

Option 4: Cookie-Based
Automatic with same-origin
✓ Secure if SameSite cookie
✓ Auto-sent by browser

Security Best Practices:
├── Always use wss:// (WSS over TLS)
├── Validate origin header
├── Implement rate limiting per connection
├── Timeout idle connections
├── Sanitize all messages
└── Monitor for abuse patterns
```

**5. Monitoring & Health**
```
Metrics to Track:
├── Active connections count
├── Messages sent/received per second
├── Connection duration distribution
├── Error rate (connection failures, message errors)
├── Latency (message round-trip time)
└── Memory per connection

Health Endpoint:
GET /health
{
  "status": "healthy",
  "websocket_connections": 45231,
  "messages_per_second": 15234,
  "error_rate": 0.001,
  "uptime_seconds": 86400
}

Alerting:
├── Connection count > 90K → Scale up
├── Error rate > 1% → Investigate
├── Latency > 500ms → Performance issue
└── Memory per connection > 10MB → Memory leak
```

### WebSocket vs Alternatives

```
WebSocket vs HTTP Polling:
┌──────────────┬──────────┬──────────────┐
│ Metric       │ WebSocket│ HTTP Polling │
├──────────────┼──────────┼──────────────┤
│ Latency      │ 50-100ms │ 2.5s avg     │
│ Bandwidth    │ Low      │ 10x higher   │
│ Server Load  │ Low      │ 10x higher   │
│ Battery      │ Efficient│ Drains fast  │
│ Scalability  │ High     │ Limited      │
└──────────────┴──────────┴──────────────┘

WebSocket vs SSE:
┌──────────────┬──────────┬──────────────┐
│ Feature      │ WebSocket│ SSE          │
├──────────────┼──────────┼──────────────┤
│ Direction    │ Bi       │ Server→Client│
│ Data Types   │ Text+Bin │ Text only    │
│ Complexity   │ Higher   │ Lower        │
│ Auto-reconnect│ Manual  │ Built-in     │
│ Use Case     │ Chat     │ Notifications│
└──────────────┴──────────┴──────────────┘

Choose WebSocket when:
├── Bidirectional communication needed
├── Binary data transfer required
├── Multiple message types
├── Complex client-server interaction
└── Gaming, chat, collaborative editing

Choose SSE when:
├── Server → Client only (one-way)
├── Text data only
├── Simpler implementation preferred
├── Auto-reconnect desired
└── Live feeds, notifications, updates
```

### Interview Talking Points for WebSocket

**When interviewer asks: "How would you implement real-time features?"**

```
"I'd use WebSocket for real-time bidirectional communication:

1. Use Case Analysis:
   For chat application:
   ├── Send message: Client → Server
   ├── Receive message: Server → Client
   ├── Typing indicator: Client → Server → Other Client
   ├── Online status: Server → All Clients
   └── Conclusion: Bidirectional, WebSocket is perfect

2. Architecture Design:
   ┌─────────┐                    ┌────────────────┐
   │ Client  │←─────WebSocket────→│ WS Gateway     │
   └─────────┘                    │ (Node.js)      │
                                  │ 10K connections│
                                  └────────┬───────┘
                                           │
                                           ↓
                                    ┌─────────────┐
                                    │ Redis       │
                                    │ Pub/Sub     │
                                    └─────────────┘

3. Scalability Strategy:
   - Each gateway server: 10K connections
   - For 100K users: 10 gateway servers
   - Redis Pub/Sub: Share state across servers
   - HAProxy: Sticky sessions (ip_hash)
   - Auto-scale based on connection count

4. Performance Characteristics:
   - Message latency: 50-100ms end-to-end
   - Overhead: 2-6 bytes per frame (vs 800+ bytes HTTP)
   - Bandwidth: 90% reduction vs polling
   - Can handle 1M messages/second across cluster

5. Reliability Features:
   - Auto-reconnect on disconnect
   - Message queue during offline
   - Heartbeat every 30 seconds
   - Exponential backoff on failures
   - Circuit breaker for backend services

Trade-offs:
- More complex than REST (need to manage connections)
- Load balancer needs sticky sessions
- Debugging harder than HTTP
- But: Essential for real-time UX at this scale"
```

---

## GraphQL - Query Language for APIs

### What is GraphQL?

```
GraphQL: Query language and runtime for APIs
├── Developed by Facebook (2012, open-sourced 2015)
├── Single endpoint: /graphql
├── Client specifies exact data requirements
├── Strongly typed schema
└── Introspection built-in

Key Characteristics:
├── No over-fetching or under-fetching
├── Single request for complex data
├── Real-time with subscriptions
├── Self-documenting
└── Versioning not needed (schema evolution)
```

### GraphQL vs REST - The Problem GraphQL Solves

**REST API Problem:**
```
Scenario: Display user profile with posts and comments

Approach 1: Multiple Endpoints
GET /users/123
{
  "id": 123,
  "name": "John",
  "email": "john@example.com",
  "bio": "...",
  "profile_pic": "...",
  "followers": 1000,
  "following": 500
}

GET /users/123/posts
[
  {"id": 1, "title": "Post 1", "likes": 10},
  {"id": 2, "title": "Post 2", "likes": 20},
  ...
]

GET /posts/1/comments
[
  {"id": 1, "text": "Great!", "author": "user456"},
  ...
]

GET /posts/2/comments
[...]

Result:
- 4+ HTTP requests (waterfall)
- Total latency: 200-400ms
- Over-fetching: Don't need all user fields
- Under-fetching: Need multiple requests
- Bandwidth waste: Duplicate data in responses

Approach 2: Custom Endpoint
GET /users/123/profile-with-posts-and-comments

Problems:
- Endpoint explosion (one per use case)
- Version management nightmare
- Cannot reuse across different views
- Mobile vs Desktop: Different data needs
- Backend teams: Overhead maintaining many endpoints

```

**GraphQL Solution:**
```graphql
# Single Request
query {
  user(id: 123) {
    id
    name
    profilePic
    posts(limit: 10) {
      id
      title
      likes
      comments(limit: 5) {
        id
        text
        author {
          name
        }
      }
    }
  }
}

# Response: Exactly what was requested
{
  "data": {
    "user": {
      "id": 123,
      "name": "John",
      "profilePic": "https://...",
      "posts": [
        {
          "id": 1,
          "title": "Post 1",
          "likes": 10,
          "comments": [
            {
              "id": 1,
              "text": "Great!",
              "author": { "name": "Jane" }
            }
          ]
        }
      ]
    }
  }
}

Benefits:
✓ Single HTTP request
✓ No over-fetching (client chooses fields)
✓ No under-fetching (nested data in one go)
✓ 1 endpoint for all use cases
✓ Self-documenting via introspection
✓ Strongly typed
```

### GraphQL Core Concepts

**1. Schema Definition Language (SDL)**
```graphql
# Types
type User {
  id: ID!              # ! means required (non-null)
  username: String!
  email: String!
  bio: String          # Optional field
  age: Int
  verified: Boolean!
  createdAt: DateTime!
  
  # Relationships
  posts: [Post!]!      # Array of posts (never null)
  followers: [User!]!
  following: [User!]!
  
  # Computed fields (resolved at runtime)
  followerCount: Int!
  postCount: Int!
}

type Post {
  id: ID!
  title: String!
  content: String!
  published: Boolean!
  createdAt: DateTime!
  updatedAt: DateTime!
  
  # Relationships
  author: User!
  comments: [Comment!]!
  likes: [Like!]!
  
  # Computed
  likeCount: Int!
  commentCount: Int!
}

type Comment {
  id: ID!
  text: String!
  createdAt: DateTime!
  author: User!
  post: Post!
}

type Like {
  user: User!
  post: Post!
  createdAt: DateTime!
}

# Enums
enum Role {
  ADMIN
  USER
  MODERATOR
}

# Input Types (for mutations)
input CreateUserInput {
  username: String!
  email: String!
  password: String!
  bio: String
}

input UpdateUserInput {
  username: String
  email: String
  bio: String
}

# Custom Scalars
scalar DateTime
scalar URL
scalar Email
```

**2. Query (Read Operations)**
```graphql
type Query {
  # Get single user
  user(id: ID!): User
  
  # Get current authenticated user
  me: User!
  
  # Search users
  users(
    search: String
    role: Role
    limit: Int = 10
    offset: Int = 0
  ): [User!]!
  
  # Get post
  post(id: ID!): Post
  
  # Get feed
  feed(
    limit: Int = 20
    cursor: String
  ): PostConnection!
  
  # Search posts
  searchPosts(
    query: String!
    tags: [String!]
    author: ID
  ): [Post!]!
}

# Connection type for cursor-based pagination
type PostConnection {
  edges: [PostEdge!]!
  pageInfo: PageInfo!
}

type PostEdge {
  node: Post!
  cursor: String!
}

type PageInfo {
  hasNextPage: Boolean!
  endCursor: String
}

# Example Query
query GetUserProfile($userId: ID!) {
  user(id: $userId) {
    id
    username
    bio
    followerCount
    posts(limit: 5) {
      id
      title
      likeCount
      comments(limit: 3) {
        id
        text
        author {
          username
        }
      }
    }
  }
}

# With Variables
{
  "userId": "123"
}
```

**3. Mutation (Write Operations)**
```graphql
type Mutation {
  # User mutations
  createUser(input: CreateUserInput!): User!
  updateUser(id: ID!, input: UpdateUserInput!): User!
  deleteUser(id: ID!): Boolean!
  
  # Post mutations
  createPost(title: String!, content: String!): Post!
  updatePost(id: ID!, title: String, content: String): Post!
  deletePost(id: ID!): Boolean!
  publishPost(id: ID!): Post!
  
  # Interaction mutations
  likePost(postId: ID!): Like!
  unlikePost(postId: ID!): Boolean!
  addComment(postId: ID!, text: String!): Comment!
  deleteComment(id: ID!): Boolean!
  
  # Follow mutations
  followUser(userId: ID!): User!
  unfollowUser(userId: ID!): Boolean!
}

# Example Mutation
mutation CreatePost($title: String!, $content: String!) {
  createPost(title: $title, content: $content) {
    id
    title
    content
    createdAt
    author {
      username
    }
  }
}

# Variables
{
  "title": "My First Post",
  "content": "Hello GraphQL!"
}

# Response
{
  "data": {
    "createPost": {
      "id": "456",
      "title": "My First Post",
      "content": "Hello GraphQL!",
      "createdAt": "2025-01-10T10:00:00Z",
      "author": {
        "username": "johndoe"
      }
    }
  }
}
```

**4. Subscription (Real-Time)**
```graphql
type Subscription {
  # Post subscriptions
  postCreated: Post!
  postUpdated(postId: ID!): Post!
  postDeleted: ID!
  
  # Comment subscriptions
  commentAdded(postId: ID!): Comment!
  
  # User subscriptions
  userOnlineStatusChanged(userId: ID!): OnlineStatus!
  
  # Notification subscriptions
  notificationReceived: Notification!
}

type OnlineStatus {
  userId: ID!
  online: Boolean!
  lastSeen: DateTime!
}

type Notification {
  id: ID!
  type: NotificationType!
  message: String!
  link: String
  createdAt: DateTime!
  read: Boolean!
}

enum NotificationType {
  LIKE
  COMMENT
  FOLLOW
  MENTION
}

# Example Subscription (WebSocket-based)
subscription OnCommentAdded($postId: ID!) {
  commentAdded(postId: $postId) {
    id
    text
    createdAt
    author {
      username
      profilePic
    }
  }
}

# Client receives real-time updates
{
  "data": {
    "commentAdded": {
      "id": "789",
      "text": "Great post!",
      "createdAt": "2025-01-10T10:05:00Z",
      "author": {
        "username": "janedoe",
        "profilePic": "https://..."
      }
    }
  }
}
```

### GraphQL Resolver Implementation

**Server Implementation (Node.js with Apollo Server):**
```javascript
const { ApolloServer, gql } = require('apollo-server');

// Type Definitions
const typeDefs = gql`
  type User {
    id: ID!
    username: String!
    email: String!
    posts: [Post!]!
    followerCount: Int!
  }
  
  type Post {
    id: ID!
    title: String!
    content: String!
    author: User!
    likeCount: Int!
  }
  
  type Query {
    user(id: ID!): User
    users: [User!]!
    post(id: ID!): Post
    posts: [Post!]!
  }
  
  type Mutation {
    createPost(title: String!, content: String!): Post!
    likePost(postId: ID!): Boolean!
  }
  
  type Subscription {
    postCreated: Post!
  }
`;

// Resolvers
const resolvers = {
  Query: {
    user: async (parent, { id }, context) => {
      // context contains auth info, db connections, etc.
      return await context.db.user.findById(id);
    },
    
    users: async (parent, args, context) => {
      return await context.db.user.findAll();
    },
    
    post: async (parent, { id }, context) => {
      return await context.db.post.findById(id);
    },
    
    posts: async (parent, args, context) => {
      return await context.db.post.findAll();
    }
  },
  
  Mutation: {
    createPost: async (parent, { title, content }, context) => {
      // Check authentication
      if (!context.user) {
        throw new Error('Not authenticated');
      }
      
      const post = await context.db.post.create({
        title,
        content,
        authorId: context.user.id
      });
      
      // Publish subscription event
      context.pubsub.publish('POST_CREATED', { postCreated: post });
      
      return post;
    },
    
    likePost: async (parent, { postId }, context) => {
      if (!context.user) {
        throw new Error('Not authenticated');
      }
      
      await context.db.like.create({
        userId: context.user.id,
        postId
      });
      
      return true;
    }
  },
  
  Subscription: {
    postCreated: {
      subscribe: (parent, args, context) => {
        return context.pubsub.asyncIterator(['POST_CREATED']);
      }
    }
  },
  
  // Field-level resolvers
  User: {
    posts: async (user, args, context) => {
      // Only called if 'posts' field is requested
      return await context.db.post.findByAuthor(user.id);
    },
    
    followerCount: async (user, args, context) => {
      // Computed field
      return await context.db.follow.countByUserId(user.id);
    }
  },
  
  Post: {
    author: async (post, args, context) => {
      // Use DataLoader to batch/cache
      return await context.loaders.user.load(post.authorId);
    },
    
    likeCount: async (post, args, context) => {
      return await context.db.like.countByPostId(post.id);
    }
  }
};

// Server setup
const server = new ApolloServer({
  typeDefs,
  resolvers,
  context: ({ req }) => ({
    user: getUserFromToken(req.headers.authorization),
    db: database,
    loaders: createLoaders(),
    pubsub: pubsub
  })
});

server.listen().then(({ url }) => {
  console.log(`Server ready at ${url}`);
});
```

### GraphQL Performance Optimization

**1. N+1 Query Problem**
```
Problem:
query {
  posts {          # 1 query
    title
    author {       # N queries (one per post)
      username
    }
  }
}

Without optimization:
- 1 query: Get all posts (100 posts)
- 100 queries: Get author for each post
- Total: 101 database queries!

Solution: DataLoader (Batching & Caching)
const DataLoader = require('dataloader');

const userLoader = new DataLoader(async (userIds) => {
  // Batch load all users in single query
  const users = await db.user.findByIds(userIds);
  
  // Return users in same order as userIds
  return userIds.map(id => users.find(u => u.id === id));
});

// In resolver
Post: {
  author: (post, args, context) => {
    return context.loaders.user.load(post.authorId);
  }
}

Result:
- 1 query: Get all posts
- 1 query: Get all authors (batched)
- Total: 2 queries (50x improvement!)

Performance Impact:
Before: 101 queries × 10ms = 1010ms
After: 2 queries × 10ms = 20ms
Improvement: 50x faster
```

**2. Query Complexity Analysis**
```javascript
// Prevent expensive queries
const { createComplexityLimitRule } = require('graphql-validation-complexity');

const server = new ApolloServer({
  validationRules: [
    createComplexityLimitRule(1000, {
      scalarCost: 1,
      objectCost: 5,
      listFactor: 10
    })
  ]
});

// Example: This query would be rejected
query TooExpensive {
  users {              # 100 users × 10 (list factor) = 1000
    posts {            # 100 users × 50 posts × 10 = 50,000
      comments {       # Cost exceeds limit!
        author {
          posts {
            ...
          }
        }
      }
    }
  }
}

// Error Response
{
  "errors": [{
    "message": "Query complexity (52,000) exceeds limit (1,000)"
  }]
}
```

**3. Depth Limiting**
```javascript
const depthLimit = require('graphql-depth-limit');

const server = new ApolloServer({
  validationRules: [depthLimit(5)]
});

// Rejected: Depth = 6
query TooDeep {
  user {              # 1
    posts {           # 2
      author {        # 3
        followers {   # 4
          posts {     # 5
            comments {# 6 - REJECTED!
              ...
            }
          }
        }
      }
    }
  }
}
```

**4. Caching Strategies**
```javascript
// Response caching
const { ApolloServer } = require('apollo-server');
const { RedisCache } = require('apollo-server-cache-redis');

const server = new ApolloServer({
  cache: new RedisCache({
    host: 'redis-server',
    ttl: 300  // 5 minutes
  }),
  
  cacheControl: {
    defaultMaxAge: 60
  }
});

// Field-level cache hints
type Query {
  # Cache for 60 seconds
  user(id: ID!): User @cacheControl(maxAge: 60)
  
  # Don't cache (private data)
  me: User! @cacheControl(maxAge: 0, scope: PRIVATE)
  
  # Cache for 1 hour (public data)
  posts: [Post!]! @cacheControl(maxAge: 3600)
}

// Automatic Persisted Queries (APQ)
// Client sends hash instead of full query
POST /graphql
{
  "extensions": {
    "persistedQuery": {
      "version": 1,
      "sha256Hash": "abc123..."
    }
  }
}

Benefits:
- 90% bandwidth reduction
- CDN caching possible
- Better security (whitelist queries)
```

### GraphQL Best Practices

**1. Schema Design**
```graphql
✅ Good Design:
# Descriptive names
type User {
  id: ID!
  username: String!
  email: String!
}

# Pagination built-in
type Query {
  users(
    first: Int = 20
    after: String
  ): UserConnection!
}

# Error handling
type Mutation {
  createUser(input: CreateUserInput!): CreateUserPayload!
}

type CreateUserPayload {
  user: User
  errors: [Error!]
}

type Error {
  field: String
  message: String!
}

❌ Bad Design:
# Vague names
type Data {
  stuff: String
}

# No pagination
type Query {
  getAllUsers: [User!]!  # Could return millions!
}

# Poor error handling
type Mutation {
  createUser(input: CreateUserInput!): User!  # Throws on error
}
```

**2. Naming Conventions**
```graphql
✓ Types: PascalCase (User, Post, Comment)
✓ Fields: camelCase (firstName, createdAt, postCount)
✓ Arguments: camelCase (userId, pageSize, sortOrder)
✓ Enums: UPPER_SNAKE_CASE (USER_ROLE, POST_STATUS)
✓ Input types: PascalCase with "Input" suffix (CreateUserInput)
✓ Payload types: PascalCase with "Payload" suffix (CreateUserPayload)

Query naming:
✓ Singular: user(id: ID!)
✓ Plural: users(limit: Int)
✓ Action-based mutations: createUser, updatePost, deleteComment
✓ Boolean fields: isPublished, hasLiked, canEdit
```

**3. Versioning Strategy**
```graphql
# Don't version - evolve schema instead!

# Add new fields (non-breaking)
type User {
  id: ID!
  name: String!
  email: String!
  phoneNumber: String  # New field, optional
}

# Deprecate old fields
type User {
  id: ID!
  fullName: String! @deprecated(reason: "Use firstName and lastName")
  firstName: String!
  lastName: String!
}

# Migration path
type User {
  # Old field (deprecated, still works)
  posts: [Post!]! @deprecated(reason: "Use postsConnection for pagination")
  
  # New field (recommended)
  postsConnection(first: Int, after: String): PostConnection!
}
```

### When to Use GraphQL

**✅ Perfect For:**
```
1. Frontend-Driven Development:
   - Mobile apps with data needs
   - Multiple client types (web, mobile, tablet)
   - Frequent UI iterations
   - Client specifies data requirements
   
   Example: Facebook, GitHub, Shopify

2. Complex Data Relationships:
   - Deep nesting (users → posts → comments → authors)
   - Many-to-many relationships
   - Aggregate data from multiple sources
   
   Example: Social networks, CMS, e-commerce

3. Multiple Clients with Different Needs:
   - Mobile: Minimal data
   - Web: More data
   - Admin panel: All data
   - Single API serves all
   
   Example: Instagram (mobile vs web vs admin)

4. Reducing API Requests:
   - Complex screens need 10+ REST endpoints
   - GraphQL: Single request
   - Better mobile experience
   
   Example: Dashboard with multiple widgets

5. Real-Time Features:
   - Subscriptions for live updates
   - Built-in WebSocket support
   - Same type system as queries
   
   Example: Chat apps, live feeds, collaborative tools
```

**❌ Not Ideal For:**
```
1. Simple CRUD APIs:
   - Overhead not justified
   - REST is simpler
   - Standard operations only
   
   Better: REST API

2. File Upload/Download:
   - Binary data handling complex
   - Not GraphQL's strength
   - Need multipart uploads
   
   Better: REST endpoints for files

3. Public APIs (Third-Party):
   - Higher learning curve
   - Less tooling than REST
   - Documentation more complex
   
   Better: REST for public consumption

4. Caching Requirements:
   - HTTP caching harder with POST
   - CDN caching complex
   - Cache invalidation tricky
   
   Better: REST with proper cache headers

5. Simple Backend Services:
   - Microservices don't need GraphQL overhead
   - gRPC better for internal communication
   
   Better: gRPC for service-to-service
```

### GraphQL vs REST - Detailed Comparison

```
┌──────────────────┬──────────────┬──────────────┐
│ Aspect           │ GraphQL      │ REST         │
├──────────────────┼──────────────┼──────────────┤
│ Endpoints        │ 1 (/graphql) │ Many         │
│ Data Fetching    │ Exact needs  │ Fixed        │
│ Over-fetching    │ No           │ Yes          │
│ Under-fetching   │ No           │ Yes (N+1)    │
│ Versioning       │ Not needed   │ Required     │
│ Caching          │ Complex      │ Native       │
│ Real-time        │ Subscriptions│ Not native   │
│ Learning Curve   │ Steeper      │ Gentle       │
│ Tooling          │ Good         │ Excellent    │
│ File Upload      │ Complex      │ Native       │
│ Error Handling   │ In response  │ Status codes │
│ Type Safety      │ Built-in     │ Optional     │
└──────────────────┴──────────────┴──────────────┘

Performance Comparison:
┌─────────────────┬──────────────┬──────────────┐
│ Scenario        │ GraphQL      │ REST         │
├─────────────────┼──────────────┼──────────────┤
│ Simple Query    │ 50ms         │ 50ms         │
│ Nested Data     │ 60ms (1 req) │ 200ms (4 req)│
│ Partial Data    │ 40ms         │ 50ms         │
│ Cache Hit       │ 30ms         │ 5ms          │
│ Bandwidth       │ Optimal      │ 2-5x larger  │
└─────────────────┴──────────────┴──────────────┘
```

### Interview Talking Points for GraphQL

**When interviewer asks: "Should we use GraphQL?"**

```
"I'd evaluate GraphQL vs REST based on these factors:

✅ Use GraphQL if:

1. Multiple Client Types:
   - Mobile app needs: id, name, thumbnail (minimize data)
   - Web app needs: id, name, thumbnail, bio, stats
   - Admin panel needs: All fields + relationships
   
   With GraphQL:
   query MobileUser($id: ID!) {
     user(id: $id) {
       id
       name
       thumbnail
     }
   }
   
   query WebUser($id: ID!) {
     user(id: $id) {
       id
       name
       thumbnail
       bio
       followerCount
       posts(limit: 10) { ... }
     }
   }
   
   Single API serves all clients efficiently

2. Complex UI Requirements:
   Example: User Profile Page
   REST: 6+ API calls
   ├── GET /users/123
   ├── GET /users/123/posts
   ├── GET /users/123/followers
   ├── GET /users/123/following
   ├── GET /posts/{id}/comments (for each post)
   └── Total latency: 300-500ms
   
   GraphQL: 1 API call
   query UserProfile($id: ID!) {
     user(id: $id) {
       ...allTheData
     }
   }
   └── Total latency: 100-150ms

3. Rapid Frontend Development:
   - No backend changes for new UI features
   - Frontend developers self-serve
   - Backend provides data graph
   - Faster iteration cycles

❌ Stick with REST if:

1. Simple APIs:
   Basic CRUD operations
   REST is simpler, well-understood
   Team has no GraphQL experience

2. Heavy Caching Needs:
   Content-heavy site (blog, news)
   CDN caching critical
   HTTP caching works perfectly

3. Public API:
   Third-party developers
   Lower learning curve
   Better tooling (Postman)

Hybrid Approach (Recommended):
┌─────────────┐      REST      ┌─────────────┐
│ Public API  │◄───────────────│   Clients   │
└─────────────┘                └─────────────┘
       │
       │ GraphQL
       ▼
┌─────────────┐                ┌─────────────┐
│ BFF Layer   │◄───GraphQL─────│ Our Web/App │
└─────────────┘                └─────────────┘
       │
       │ gRPC
       ▼
┌─────────────────────────────────────┐
│      Microservices (Internal)       │
└─────────────────────────────────────┘

Implementation Considerations:
├── Use DataLoader for N+1 prevention
├── Implement query complexity limits (max 1000)
├── Set depth limits (max 5 levels)
├── Use Automatic Persisted Queries (APQ)
├── Monitor query performance
└── Implement proper error handling

For our use case [explain specific system]:
I recommend [GraphQL/REST/Hybrid] because..."
```

---

## Server-Sent Events (SSE) - Unidirectional Push

### What is Server-Sent Events?

```
SSE: HTTP-based protocol for server → client push
├── One-way: Server pushes, client receives
├── Built on HTTP (no special protocol)
├── Automatic reconnection
├── Text-based (event stream)
└── W3C Standard (2015)

Key Characteristics:
├── Simple HTTP GET request
├── Content-Type: text/event-stream
├── Long-lived connection
├── UTF-8 text only (no binary)
└── Browser native support (EventSource API)
```

### SSE Protocol Details

**Connection Establishment:**
```
Client Request:
GET /events HTTP/1.1
Host: example.com
Accept: text/event-stream
Cache-Control: no-cache

Server Response:
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

data: First message

data: Second message

data: {"type": "notification", "text": "New message"}

Event Format:
data: message content\n\n

With ID (for reconnection):
id: 123
data: message content

With Event Type:
event: userLoggedIn
data: {"userId": "abc"}

With Retry:
retry: 10000
data: connection info

Multi-line Data:
data: First line
data: Second line
data: Third line

```

### SSE Client Implementation

**JavaScript (Browser):**
```javascript
// Create EventSource
const eventSource = new EventSource('/events');

// Listen to messages
eventSource.onmessage = (event) => {
  console.log('Message:', event.data);
  const data = JSON.parse(event.data);
  updateUI(data);
};

// Listen to specific event types
eventSource.addEventListener('userLoggedIn', (event) => {
  console.log('User logged in:', event.data);
});

eventSource.addEventListener('notification', (event) => {
  showNotification(JSON.parse(event.data));
});

// Handle connection opened
eventSource.onopen = () => {
  console.log('Connection established');
};

// Handle errors
eventSource.onerror = (error) => {
  console.error('SSE error:', error);
  if (eventSource.readyState === EventSource.CLOSED) {
    console.log('Connection closed');
  }
};

// Close connection
eventSource.close();

// Auto-reconnection:
// Browser automatically reconnects on disconnect
// Sends Last-Event-ID header to resume
// Default retry: 3 seconds (configurable by server)
```

**Advanced Client with Authentication:**
```javascript
class SSEManager {
  constructor(url, token) {
    this.url = url;
    this.token = token;
    this.eventSource = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts
