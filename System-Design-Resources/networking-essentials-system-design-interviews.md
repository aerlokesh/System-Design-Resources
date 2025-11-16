# Networking Essentials for System Design Interviews

## Table of Contents
1. [OSI Model & Network Layers](#osi-model--network-layers)
2. [IP Addressing & DNS](#ip-addressing--dns)
3. [TCP vs UDP](#tcp-vs-udp)
4. [HTTP Protocol Family](#http-protocol-family)
5. [Real-Time Communication](#real-time-communication)
6. [API Protocols](#api-protocols)
7. [Load Balancing](#load-balancing)
8. [Network Resilience Patterns](#network-resilience-patterns)
9. [Content Delivery & Caching](#content-delivery--caching)
10. [Security & Encryption](#security--encryption)
11. [When to Use What](#when-to-use-what)
12. [System Design Examples](#system-design-examples)

---

## OSI Model & Network Layers

### The 7 Layers (Bottom to Top)

```
7. APPLICATION    | HTTP, FTP, SMTP, DNS, SSH
6. PRESENTATION   | SSL/TLS, Encryption, Compression
5. SESSION        | Session management, Authentication
4. TRANSPORT      | TCP, UDP, Port numbers
3. NETWORK        | IP, Routing, ICMP
2. DATA LINK      | MAC, Ethernet, WiFi
1. PHYSICAL       | Cables, Radio waves, Bits
```

### Practical Layers for System Design

**Most interviews focus on layers 3-7:**

**Layer 4 (Transport)**:
```
TCP:
- Reliable, ordered delivery
- Connection-oriented (3-way handshake)
- Flow control, congestion control
- Use for: HTTP, databases, file transfers

UDP:
- Unreliable, unordered
- Connectionless (no handshake)
- Low overhead
- Use for: Streaming, gaming, DNS, VoIP
```

**Layer 7 (Application)**:
```
HTTP/HTTPS:
- Request-response model
- Stateless
- RESTful APIs

WebSocket:
- Full-duplex communication
- Persistent connection
- Real-time bidirectional

gRPC:
- RPC framework
- Protocol Buffers
- HTTP/2 based
```

### Why OSI Model Matters in Interviews

**Understanding layer interactions helps with:**
- Load balancer decisions (L4 vs L7)
- CDN strategies (which layers to cache)
- Security design (encryption at which layer)
- Performance optimization (reduce layer overhead)
- Debugging distributed systems

---

## IP Addressing & DNS

### IP Addressing

**IPv4**:
```
Format: 32-bit address (4 octets)
Example: 192.168.1.1
Total addresses: 4.3 billion
Status: Exhausted

Private ranges (RFC 1918):
- 10.0.0.0/8 (10.0.0.0 - 10.255.255.255)
- 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
- 192.168.0.0/16 (192.168.0.0 - 192.168.255.255)

Use for: Internal networks, NAT
```

**IPv6**:
```
Format: 128-bit address (8 groups of 4 hex digits)
Example: 2001:0db8:85a3:0000:0000:8a2e:0370:7334
Total addresses: 340 undecillion
Status: Gradually adopting

Use for: Public internet, future-proofing
```

### DNS (Domain Name System)

**Purpose**: Translate domain names to IP addresses

**DNS Hierarchy**:
```
. (root)
  └── .com (TLD)
      └── amazon.com (domain)
          └── www.amazon.com (subdomain)
```

**DNS Query Flow**:
```
1. User types: www.amazon.com
2. Browser checks cache: Miss
3. OS checks cache: Miss
4. Query DNS resolver (ISP): 8.8.8.8
5. Resolver queries root DNS: . → .com servers
6. Query .com TLD: amazon.com → Amazon's nameservers
7. Query Amazon nameservers: www.amazon.com → 52.94.236.248
8. Return IP to browser
9. Browser connects to 52.94.236.248

Total time: 20-50ms (first request)
Cached time: <1ms (subsequent requests)
```

**DNS Record Types**:
```
A Record: Domain → IPv4
  amazon.com → 52.94.236.248

AAAA Record: Domain → IPv6
  amazon.com → 2600:9000:21f8:1234::1

CNAME: Alias to another domain
  www.amazon.com → amazon.com

MX: Mail exchange
  amazon.com → mail.amazon.com

TXT: Arbitrary text (SPF, DKIM, verification)
  amazon.com → "v=spf1 include:_spf.google.com ~all"

NS: Name server
  amazon.com → ns1.amazon.com
```

**DNS for System Design**:

**Load Balancing with DNS**:
```
Round-robin DNS:
- Multiple A records for same domain
- amazon.com → [IP1, IP2, IP3]
- DNS rotates responses
- Simple but limited

Problems:
- No health checks
- Client caching (TTL)
- Uneven distribution

Better: Use dedicated load balancer
```

**Geographic Routing (GeoDNS)**:
```
Route53 Geolocation routing:
- US users → us-east-1 (52.1.2.3)
- EU users → eu-west-1 (54.1.2.3)
- Asia users → ap-south-1 (13.1.2.3)

Benefits:
- Lower latency (closer server)
- Compliance (data residency)
- Cost (cheaper regions)

Use case: Global applications (Netflix, Amazon, Facebook)
```

**DNS TTL Strategy**:
```
Short TTL (60 seconds):
Pros:
+ Fast failover (1 minute to switch)
+ Easy A/B testing
Cons:
- More DNS queries
- Higher DNS costs

Long TTL (24 hours):
Pros:
+ Fewer DNS queries
+ Lower costs
Cons:
- Slow failover (24 hours)
- Hard to change

Best practice: 300 seconds (5 minutes)
- Balance of speed and cost
```

---

## TCP vs UDP

### TCP (Transmission Control Protocol)

**Characteristics**:
```
✓ Reliable: Guarantees delivery
✓ Ordered: Packets arrive in order
✓ Connection-oriented: 3-way handshake
✓ Flow control: Prevents overwhelming receiver
✓ Congestion control: Adjusts to network conditions
✓ Error detection: Checksums

✗ Overhead: Headers, handshakes, ACKs
✗ Latency: Higher due to reliability mechanisms
```

**3-Way Handshake**:
```
Client                    Server
  |                          |
  |-----SYN (seq=100)------->|  (1)
  |                          |
  |<----SYN-ACK (seq=200)----|  (2)
  |     (ack=101)            |
  |                          |
  |-----ACK (ack=201)------->|  (3)
  |                          |
  |===Connection Established=|

Total: 1.5 RTT (Round-Trip Time)
- US-EU: 150ms
- Optimizations: TCP Fast Open, TLS 1.3
```

**When to Use TCP**:
- HTTP/HTTPS requests
- Database connections
- File transfers (FTP, SFTP)
- Email (SMTP, IMAP)
- SSH, Remote desktop
- Any data that must be reliable

### UDP (User Datagram Protocol)

**Characteristics**:
```
✓ Fast: No handshake, minimal overhead
✓ Low latency: No waiting for ACKs
✓ Lightweight: Small headers (8 bytes vs 20+ for TCP)
✓ Broadcast/Multicast: One-to-many communication

✗ Unreliable: Packets may be lost
✗ Unordered: Packets may arrive out of order
✗ No flow control: Can overwhelm receiver
✗ No congestion control: Doesn't adjust to network
```

**When to Use UDP**:
- Video/audio streaming (slight loss acceptable)
- Online gaming (speed > reliability)
- DNS queries (retry if timeout)
- VoIP (Voice over IP)
- IoT sensor data (latest value matters)
- Live broadcasts

**UDP in System Design**:
```
Video Streaming (YouTube Live):
- Use UDP for real-time delivery
- Application layer handles:
  * Forward Error Correction (FEC)
  * Buffering
  * Dropping old packets
- Result: Low latency, good quality

Gaming (Fortnite):
- Player position updates via UDP
- 60 updates/second
- If packet lost: Next update corrects
- TCP would lag (waiting for retransmission)
```

### QUIC (Quick UDP Internet Connections)

**Evolution of UDP**:
```
QUIC = UDP + Reliability + Security

Features:
✓ Multiplexing: Multiple streams, no head-of-line blocking
✓ 0-RTT: Resume connections instantly
✓ Built-in TLS: Always encrypted
✓ Connection migration: Survives IP changes (mobile networks)

Used by: HTTP/3
Adoption: Google (20%+ of internet traffic)
```

---

## HTTP Protocol Family

### HTTP/1.1

**Characteristics**:
```
Request-response model:
Client: GET /api/users
Server: 200 OK + data

Features:
- Text-based protocol
- Keep-alive connections (reuse TCP)
- Pipelining (limited support)
- Chunked transfer encoding

Limitations:
- Head-of-line blocking (one request at a time per connection)
- Header redundancy (repeated headers)
- No server push
```

**Keep-Alive**:
```
Without keep-alive:
Each request = new TCP connection
- 3-way handshake: 150ms
- Request: 50ms
- Close: 50ms
- Total: 250ms per request

With keep-alive:
First request: 250ms (handshake + request)
Subsequent: 50ms (reuse connection)

Headers:
Connection: keep-alive
Keep-Alive: timeout=5, max=100

Savings: 80% latency reduction
```

### HTTP/2

**Major Improvements**:
```
1. Multiplexing: Multiple requests over single connection
   - No head-of-line blocking
   - Concurrent requests

2. Binary protocol: More efficient than text
   - Smaller headers
   - Faster parsing

3. Header compression (HPACK):
   - Reduces redundancy
   - 80% size reduction

4. Server push:
   - Server can send resources proactively
   - HTML + CSS + JS in one roundtrip

5. Stream prioritization:
   - Critical resources first
   - Better page load
```

**Performance Comparison**:
```
Loading page with 100 resources:

HTTP/1.1 (6 connections):
- 17 roundtrips
- 2.5 seconds

HTTP/2 (1 connection):
- 1 roundtrip
- 0.8 seconds

Improvement: 3x faster
```

**When to Use HTTP/2**:
- Modern web applications
- API gateways
- Microservices communication
- Any high-frequency HTTP traffic

### HTTP/3 (QUIC)

**Built on QUIC (UDP)**:
```
Advantages over HTTP/2:
1. No head-of-line blocking (even at transport layer)
2. Faster connection establishment (0-RTT)
3. Better mobile performance (connection migration)
4. Improved congestion control

Use cases:
- Mobile apps (unstable connections)
- Video streaming
- Real-time applications

Adoption: 25% of web traffic (2024)
```

### HTTPS (HTTP + TLS)

**TLS Handshake**:
```
Full handshake (2 RTT):
Client                    Server
  |                          |
  |-----ClientHello--------->|
  |<----ServerHello----------|
  |     (Certificate)        |
  |                          |
  |-----KeyExchange--------->|
  |<----Finished-------------|
  |-----Finished------------>|
  |===Encrypted Connection===|

Time: 300ms (US-EU)

TLS 1.3 (1-RTT):
- Combined hello + key exchange
- Time: 150ms (50% faster)

0-RTT resumption:
- Resume previous session
- Time: 0ms (instant)
```

**When to Use HTTPS**:
- Always (security best practice)
- Required for: Passwords, payments, PII
- SEO benefit: Google ranking boost
- HTTP/2: Requires HTTPS in browsers

---

## Real-Time Communication

### Server-Sent Events (SSE)

**Characteristics**:
```
Server → Client only (one-way)
Built on HTTP
Text-based (event-stream)
Automatic reconnection
Browser native support

Connection: Single long-lived HTTP connection
```

**Use Cases**:
```
1. Live scores/updates
   - Sports scores
   - Stock prices
   - Social media feeds

2. Notifications
   - New message alerts
   - Status updates

3. Progress tracking
   - File upload progress
   - Job completion status
```

**SSE Example**:
```
Client establishes connection:
GET /api/events
Accept: text/event-stream

Server sends events:
data: {"type": "score_update", "score": "3-2"}\n\n
data: {"type": "goal", "team": "home"}\n\n
data: {"type": "end_game"}\n\n

Browser EventSource API:
const eventSource = new EventSource('/api/events');
eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  updateUI(data);
};

Latency: < 100ms for updates
```

**SSE vs Alternatives**:
```
vs Long Polling:
+ Simpler: No reconnection logic
+ Efficient: Single connection
+ Native: Browser EventSource API

vs WebSocket:
- One-way only (server → client)
+ Simpler: Uses HTTP
+ Firewall-friendly: Standard HTTP port
- Not for bidirectional (chat, gaming)

When to choose SSE:
- Server → Client updates only
- Firewall restrictions (WebSocket blocked)
- Simpler requirements
```

### WebSockets

**Characteristics**:
```
Full-duplex: Client ↔ Server bidirectional
Persistent connection
Low overhead after handshake
Binary and text support
Custom protocol support
```

**WebSocket Handshake**:
```
Client request (HTTP Upgrade):
GET /chat HTTP/1.1
Host: server.example.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13

Server response:
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

Now: Bidirectional WebSocket connection

Handshake time: 1 RTT (~50-150ms)
Subsequent messages: < 10ms
```

**Frame Structure**:
```
Small overhead: 2-6 bytes per message
vs HTTP: 200+ bytes of headers per request

Example:
Send "Hello": 
- WebSocket: 7 bytes (2 header + 5 data)
- HTTP: 200+ bytes (headers + 5 data)

Savings: 95% overhead reduction
```

**Use Cases**:
```
Perfect for:
1. Chat applications (WhatsApp, Slack)
   - Real-time messaging
   - Typing indicators
   - Read receipts

2. Collaborative editing (Google Docs)
   - Real-time updates
   - Cursor positions
   - Conflict resolution

3. Live notifications
   - Social media feeds
   - Activity streams

4. Gaming
   - Multiplayer real-time games
   - Player positions
   - Game state sync

5. Financial tickers
   - Stock prices
   - Trading platforms

6. IoT dashboards
   - Sensor data streaming
```

**Scaling WebSockets**:
```
Challenge: Each connection = server resources

Connection capacity:
- Per server: 50K-100K connections
- Memory: 10KB per connection
- CPU: Minimal (unless high message rate)

For 1M concurrent users:
- Servers needed: 10-20 (with 50K each)
- Load balancer: Sticky sessions (same user → same server)
- Horizontal scaling: Add servers

Alternative for massive scale:
- Use managed service (AWS API Gateway WebSocket)
- Or hybrid: WebSocket for active users, polling for inactive
```

### Long Polling

**Mechanism**:
```
Client makes request, server holds it open until event

Flow:
1. Client: GET /api/messages?since=timestamp
2. Server: Wait for new message (hold for 30 seconds)
3. Message arrives OR timeout
4. Server: Respond with message
5. Client: Immediately make new request

Advantages:
+ Works everywhere (standard HTTP)
+ No special infrastructure
+ Firewall-friendly

Disadvantages:
- Inefficient: Connection per client
- Latency: Half RTT delay average
- Server load: Many open connections
```

**When to Use Long Polling**:
```
Legacy environments:
- Can't use WebSockets (firewall restrictions)
- Old browsers (IE 9)
- Fallback mechanism

Modern: Prefer WebSockets or SSE
```

### WebRTC (Web Real-Time Communication)

**Purpose**: Peer-to-peer audio/video/data communication

**Architecture**:
```
Peer-to-peer (P2P):
Client A ←────────→ Client B
      (direct connection)

No server in media path (just signaling)
```

**Use Cases**:
```
1. Video calling (Zoom, Google Meet)
   - Low latency (< 150ms)
   - No server bandwidth cost
   - Direct peer-to-peer

2. File sharing (P2P)
   - BitTorrent in browser
   - No server storage

3. Live streaming (Twitch alternative)
   - Broadcaster → Viewers (P2P)
   - Reduced CDN costs

4. Gaming
   - P2P game state sync
   - Low latency
```

**WebRTC Flow**:
```
1. Signaling (via server - WebSocket/HTTP):
   - Exchange SDP (Session Description Protocol)
   - ICE candidates (IP/port pairs)

2. STUN server:
   - Discover public IP/port
   - NAT traversal

3. TURN server (fallback):
   - Relay if P2P fails (firewall)
   - Acts as proxy

4. Direct connection established:
   - Media flows P2P
   - Encrypted (DTLS)

Latency: 50-150ms (P2P)
vs Server relay: 100-300ms
```

**When NOT to Use WebRTC**:
```
Avoid if:
- Need server-side processing (recording, transcoding)
- Strict compliance (must log all communication)
- Large audience (1M viewers) - CDN better
- Corporate firewalls (often blocked)

Use server-based streaming instead
```

---

## API Protocols

### REST (Representational State Transfer)

**Characteristics**:
```
- Resource-based: /users/123
- HTTP methods: GET, POST, PUT, DELETE
- Stateless: No server-side session
- JSON/XML responses
- HATEOAS: Hypermedia links

Standard structure:
GET /api/users/123 → Fetch user
POST /api/users → Create user
PUT /api/users/123 → Update user
DELETE /api/users/123 → Delete user
```

**Pros & Cons**:
```
Pros:
+ Simple: Easy to understand
+ Cacheable: HTTP caching
+ Stateless: Scalable
+ Wide support: Every language

Cons:
- Over-fetching: Get more data than needed
- Under-fetching: Multiple requests for related data
- Versioning: Breaking changes hard
```

### GraphQL

**Purpose**: Query language for APIs

**Key Features**:
```
1. Query exactly what you need:
   query {
     user(id: 123) {
       name
       email
     }
   }

2. Single endpoint: /graphql

3. Strong typing: Schema definition

4. Real-time: Subscriptions (WebSocket)
```

**GraphQL vs REST**:
```
REST (3 requests):
GET /api/users/123 → {id, name, email, address, ...}
GET /api/users/123/posts → [{id, title, ...}, ...]
GET /api/users/123/friends → [{id, name, ...}, ...]

GraphQL (1 request):
query {
  user(id: 123) {
    name
    posts { title }
    friends { name }
  }
}

Savings: 2 fewer network roundtrips = 200ms
```

**When to Use GraphQL**:
```
Perfect for:
+ Mobile apps (reduce data transfer)
+ Complex UIs (many interconnected data)
+ Rapid iteration (frontend changes often)
+ Multiple clients (web, mobile, desktop)

Avoid if:
- Simple CRUD (REST is simpler)
- File uploads (less mature)
- Caching important (REST better)
- Team unfamiliar with GraphQL
```

### gRPC (Google Remote Procedure Call)

**Characteristics**:
```
- Binary protocol (Protocol Buffers)
- HTTP/2 based
- Bi-directional streaming
- Multiple language support
- Code generation
```

**Protocol Buffers**:
```
Define schema:
message User {
  int32 id = 1;
  string name = 2;
  string email = 3;
}

Compile to code:
- Java, Go, Python, etc.
- Type-safe
- Efficient serialization

Size comparison:
JSON: 100 bytes
Protobuf: 30 bytes (70% smaller)
```

**gRPC vs REST**:
```
gRPC:
✓ Performance: 7-10x faster (binary)
✓ Streaming: Bidirectional
✓ Type-safe: Code generation
✓ Efficiency: Smaller payloads
✗ Browser: Limited support
✗ Human-readable: Binary format
✗ Debugging: Harder

REST:
✓ Browser: Native support
✓ Human-readable: JSON
✓ Debugging: Easy (curl, Postman)
✗ Performance: Slower (JSON, HTTP/1.1)
✗ Streaming: Not built-in

When to use gRPC:
- Microservices communication (internal)
- High-performance requirements
- Streaming data
- Polyglot environments (multiple languages)

When to use REST:
- Public APIs (browser access)
- Simple CRUD
- Third-party integrations
```

**gRPC Streaming Types**:
```
1. Unary (like REST):
   Client sends one, server responds one

2. Server streaming:
   Client sends one, server streams many
   Use: Download large file, live updates

3. Client streaming:
   Client streams many, server responds one
   Use: Upload file chunks, sensor data

4. Bidirectional streaming:
   Both stream concurrently
   Use: Chat, collaborative editing
```

---

## Load Balancing

### Layer 4 vs Layer 7 Load Balancing

**Layer 4 (Transport Layer - TCP/UDP)**:
```
Routes based on:
- IP address
- Port number
- Simple, fast

Cannot see:
- HTTP headers
- URLs
- Cookies

Advantages:
+ Fast: No payload inspection
+ Protocol agnostic: Works with any TCP/UDP
+ Lower latency: < 1ms overhead

Disadvantages:
- No content-based routing
- No sticky sessions (without tricks)
- Limited health checks

Use for:
- Non-HTTP protocols (databases, message queues)
- Maximum performance needed
- Simple routing sufficient

Example: AWS Network Load Balancer (NLB)
```

**Layer 7 (Application Layer - HTTP)**:
```
Routes based on:
- URL path: /api/users → Service A
- HTTP method: POST → Write service
- Headers: X-User-Type: premium → Premium servers
- Cookies: Session affinity

Advantages:
+ Content-aware: Smart routing
+ Sticky sessions: Same user → same server
+ Advanced health checks: HTTP 200 check
+ SSL termination: Offload from backends

Disadvantages:
- Slower: Payload inspection (5-10ms overhead)
- HTTP-only: Doesn't work for other protocols

Use for:
- HTTP/HTTPS traffic
- Microservices (path-based routing)
- A/B testing
- Canary deployments

Example: AWS Application Load Balancer (ALB), Nginx
```

### Load Balancing Algorithms

**Round Robin**:
```
Simplest: Rotate through servers

Server pool: [S1, S2, S3]
Request 1 → S1
Request 2 → S2
Request 3 → S3
Request 4 → S1 (cycle)

Pros:
+ Simple
+ Fair distribution

Cons:
- Ignores server capacity
- Ignores current load
```

**Least Connections**:
```
Route to server with fewest active connections

Current state:
S1: 100 connections
S2: 50 connections
S3: 75 connections

Next request → S2 (least connections)

Pros:
+ Considers load
+ Better for long-lived connections

Cons:
- More complex
- Doesn't consider request weight
```

**Least Response Time**:
```
Route to fastest responding server

Health check results:
S1: 50ms average
S2: 20ms average
S3: 35ms average

Next request → S2 (fastest)

Pros:
+ Considers performance
+ Adapts to server health

Cons:
- Complex
- Need continuous monitoring
```

**Consistent Hashing**:
```
Use for: Cache distribution, sharding

hash(user_id) → Server
- Same user always hits same server
- Sticky sessions
- Cache locality

Example:
user_123 → hash(123) % 3 = 0 → S1
user_456 → hash(456) % 3 = 0 → S1
user_789 → hash(789) % 3 = 1 → S2

Pros:
+ Session affinity
+ Cache hits

Cons:
- Uneven distribution if hash bad
- Server changes affect many users
```

**Weighted Round Robin**:
```
Assign weights based on capacity

Server pool:
S1: weight 3 (powerful server)
S2: weight 2 (medium server)
S3: weight 1 (weak server)

Distribution:
S1 gets 50% of traffic
S2 gets 33% of traffic
S3 gets 17% of traffic

Use for: Heterogeneous servers
```

### Health Checks

**Types**:
```
1. TCP health check:
   - Connect to port
   - If success: Healthy
   - Fast but shallow

2. HTTP health check:
   - GET /health
   - Expect 200 OK
   - More reliable

3. Deep health check:
   - Check dependencies (DB, cache)
   - Comprehensive but slower

Configuration:
Interval: Every 10 seconds
Timeout: 5 seconds
Unhealthy threshold: 2 failures
Healthy threshold: 2 successes
```

**Graceful Degradation**:
```
Server becomes unhealthy:
1. Load balancer detects (via health check)
2. Stop sending new requests
3. Wait for existing requests to complete (30s drain time)
4. Remove from pool
5. Alert operations team

Server recovers:
1. Health check passes
2. Add back to pool gradually (10% → 50% → 100%)
3. Monitor for issues
```

---

## Network Resilience Patterns

### Retries with Exponential Backoff

**Problem**: Transient failures (network blip, server restart)

**Naive Retry**:
```
Request fails → Retry immediately
Problem: Server overwhelmed during recovery
Result: Thundering herd problem
```

**Exponential Backoff**:
```
Retry intervals grow exponentially:
Attempt 1: Wait 1 second
Attempt 2: Wait 2 seconds
Attempt 3: Wait 4 seconds
Attempt 4: Wait 8 seconds
Attempt 5: Wait 16 seconds
Max: 32 seconds

Formula: delay = base_delay × 2^attempt
```

**Adding Jitter (Randomization)**:
```
Problem: All clients retry at same time (synchronized)

Without jitter:
100 clients fail at t=0
All retry at t=1s → Server overload

With jitter:
delay = base_delay × 2^attempt × (0.5 + random(0, 0.5))

Attempt 1: 0.5-1.5 seconds (randomized)
Result: Requests spread out over time

Implementation:
import random
delay = min(max_delay, base_delay * (2 ** attempt) * (0.5 + random.random() / 2))
```

**When to Retry**:
```
Retry for (transient errors):
✓ 5xx errors (server errors)
✓ Network timeouts
✓ Connection refused (temporarily)
✓ Rate limit (429 - after backoff)

Don't retry (permanent errors):
✗ 4xx errors (client errors)
✗ 401 Unauthorized
✗ 404 Not Found
✗ 400 Bad Request

Idempotency:
- Safe: GET, PUT, DELETE
- Unsafe: POST (might create duplicates)
- Solution: Idempotency keys
```

### Circuit Breaker Pattern

**Purpose**: Prevent cascading failures

**States**:
```
CLOSED (Normal operation):
- Requests pass through
- Track failures
- If failure threshold reached → OPEN

OPEN (Failing):
- Reject requests immediately (fail fast)
- Don't call failing service
- Wait for timeout (30-60 seconds)
- After timeout → HALF-OPEN

HALF-OPEN (Testing):
- Allow one request through (test)
- If success → CLOSED (recovered)
- If failure → OPEN (still failing)
```

**Configuration**:
```
Failure threshold: 5 failures in 10 seconds
Timeout: 30 seconds
Success threshold: 2 consecutive successes

Example:
Time 0:00 - Request fails (1)
Time 0:02 - Request fails (2)
Time 0:04 - Request fails (3)
Time 0:06 - Request fails (4)
Time 0:08 - Request fails (5) → OPEN circuit
Time 0:10 - Request rejected (circuit open)
Time 0:38 - Circuit → HALF-OPEN (after 30s)
Time 0:38 - Test request succeeds → CLOSED
Time 0:40 - Normal operation resumed
```

**Benefits**:
```
Prevent cascading failures:
- Service A fails
- Service B calls Service A (circuit breaker)
- Circuit opens after 5 failures
- Service B continues (fails fast)
- Service B doesn't get overwhelmed

Without circuit breaker:
- Service A fails
- Service B keeps calling (timeouts)
- Service B threads exhausted waiting
- Service B fails
- Service C fails (domino effect)

With circuit breaker:
- Service A fails
- Service B circuit opens (fails immediately)
- Service B remains healthy
- Service C remains healthy
- Cascade prevented
```

### Rate Limiting

**Purpose**: Prevent abuse, ensure fair resource allocation

**Algorithms**:

**1. Token Bucket**:
```
Bucket holds N tokens
Requests consume tokens
Tokens refill at rate R per second

Example:
- Bucket size: 100 tokens
- Refill rate: 10 tokens/second
- Request cost: 1 token

Time 0: 100 tokens available
Request → 99 tokens (allowed)
...
Time 1: 109 tokens (99 + 10 refill, capped at 100)

Burst handling: Can consume all 100 tokens instantly
Good for: Bursty traffic
```

**2. Leaky Bucket**:
```
Requests enter bucket, processed at fixed rate

Rate: 10 requests/second
Queue size: 100 requests

If queue full: Drop/reject requests

Smooths traffic: Enforces steady rate
Good for: Traffic shaping
```

**3. Fixed Window**:
```
Time window: 1 minute
Limit: 100 requests per minute

Time 0:00-0:59: Count requests
If count > 100: Reject
Time 1:00: Reset counter

Problem: Burst at window boundary
- 100 requests at 0:59
- 100 requests at 1:00
- Total: 200 requests in 1 second

Bad for: Preventing bursts
```

**4. Sliding Window** (Best):
```
Rolling window: Last 60 seconds
Limit: 100 requests

At any time: Count last 60 seconds
If count > 100: Reject

Smooths traffic: No boundary issue

Implementation (Redis):
Key: rate_limit:user_123
Type: Sorted Set
Score: Timestamp
Value: Request ID

Add: ZADD rate_limit:user_123 <now> <request_id>
Count: ZCOUNT rate_limit:user_123 <now-60> <now>
Clean: ZREMRANGEBYSCORE rate_limit:user_123 0 <now-60>

Good for: Most use cases
```

**Rate Limiting Strategies**:
```
Per user: 100 requests/minute
Per IP: 1000 requests/minute
Per API key: 10K requests/minute
Global: 1M requests/minute

Response when limited:
HTTP 429 Too Many Requests
Retry-After: 60 (seconds)

Headers:
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 23
X-RateLimit-Reset: 1704672000
```

### Timeouts

**Purpose**: Don't wait forever for responses

**Types**:
```
Connection timeout:
- Time to establish connection
- Default: 10 seconds
- Use: 2-5 seconds (detect failure fast)

Read timeout:
- Time waiting for response
- Default: 60 seconds
- Use: 10-30 seconds

Write timeout:
- Time sending request
- Use: 10 seconds

Idle timeout:
- Keep-alive without activity
- Use: 60-120 seconds
```

**Cascading Timeouts**:
```
Problem: Timeouts in distributed systems

Service A → Service B → Service C

If each has 30s timeout:
- C takes 30s to timeout
- B waits 30s, then times out (total 60s)
- A waits 60s, then times out (total 90s)
- User waits 90 seconds!

Solution: Deadline propagation
- A sets deadline: 10 seconds
- B inherits deadline: 9 seconds remaining
- C inherits deadline: 8 seconds remaining
- Each service respects overall deadline

Result: User waits 10 seconds max
```

### Bulkhead Pattern

**Purpose**: Isolate resources to prevent total failure

**Analogy**: Ship bulkheads (compartments)
- One compartment floods
- Others remain dry
- Ship stays afloat

**Implementation**:
```
Separate thread pools per dependency:

Service A has 100 threads:
- 30 threads for Service B calls
- 30 threads for Service C calls
- 40 threads for other work

If Service B fails:
- 30 threads may be blocked
- But 70 threads still working
- Service A remains partially functional

Without bulkheads:
- All 100 threads could be blocked
- Service A completely down
```

---

## Content Delivery & Caching

### CDN (Content Delivery Network)

**Purpose**: Distribute content globally for low latency

**Architecture**:
```
Origin Server (US)
       ↓
  CDN Provider
   /    |    \
EU Edge  Asia Edge  US Edge
   |       |          |
 Users   Users      Users

User request flow:
1. User in Germany requests image
2. DNS routes to nearest edge (Frankfurt)
3. Edge checks cache: HIT → Return (50ms)
4. If MISS → Fetch from origin (200ms), cache, return

Latency:
- Direct to origin: 200ms
- Via CDN (cache hit): 50ms
- Improvement: 4x faster
```

**CDN Caching Strategy**:
```
Static content (long TTL):
- Images: 7 days
- CSS/JS: 7 days (with versioning)
- Videos: 30 days

Dynamic content (short TTL):
- API responses: 5 minutes
- Product prices: 1 minute
- User data: No cache

Cache-Control headers:
Cache-Control: public, max-age=604800 (7 days)
Cache-Control: private, no-cache (don't cache)
```

**CDN Providers**:
```
CloudFlare:
- 200+ locations
- Free tier available
- DDoS protection
- Good for: Startups, small apps

AWS CloudFront:
- 200+ locations
- AWS integration
- Lambda@Edge
- Good for: AWS-based apps

Akamai:
- 300K+ servers
- Enterprise-grade
- Expensive
- Good for: Large enterprises

Fastly:
- Real-time purging
- VCL customization
- Good for: Dynamic content
```

**Cache Invalidation**:
```
Methods:
1. TTL expiration (automatic)
2. Manual purge (API call)
3. Cache tagging (purge by tag)
4. URL versioning (/v2/style.css)

Best practice: URL versioning
- Never invalidate
- Change URL for new version
- Old version cached until TTL
- No cache inconsistency
```

### Proxy Patterns

**Forward Proxy**:
```
Client → Proxy → Internet

Use cases:
- Corporate firewall (filter content)
- Anonymity (hide client IP)
- Caching (save bandwidth)
- Access control

Example: Squid proxy
```

**Reverse Proxy**:
```
Internet → Proxy → Backend Servers

Use cases:
- Load balancing
- SSL termination
- Caching
- Security (hide backend)
- Compression

Example: Nginx, HAProxy
```

**API Gateway** (Special Reverse Proxy):
```
Responsibilities:
- Authentication/Authorization
- Rate limiting
- Request routing
- Response transformation
- Caching
- Metrics/logging

Example: Kong, AWS API Gateway, Apigee
```

---

## Security & Encryption

### TLS/SSL

**Purpose**: Encrypt data in transit

**TLS Versions**:
```
TLS 1.0/1.1: Deprecated (vulnerable)
TLS 1.2: Current standard
TLS 1.3: Latest, fastest (1-RTT, 0-RTT resumption)
```

**Certificate Chain**:
```
Root CA (Certificate Authority)
  └── Intermediate CA
      └── Server Certificate (amazon.com)

Browser verifies:
1. Server sends certificate
2. Check signature (signed by Intermediate CA)
3. Check Intermediate (signed by Root CA)
4. Check Root CA (trusted in browser)
5. Verify not expired
6. Verify domain matches

Result: Trust established
```

**Perfect Forward Secrecy (PFS)**:
```
Ensures: Compromising long-term key doesn't compromise past sessions

How: Ephemeral keys per session
- Each session uses unique key
- Key destroyed after session
- Past sessions remain secure

Ciphers with PFS:
- ECDHE (Elliptic Curve Diffie-Hellman Ephemeral)
- DHE (Diffie-Hellman Ephemeral)

Configuration:
ssl_prefer_server_ciphers on;
ssl_ciphers ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-GCM-SHA256;
```

### Encryption Types

**Symmetric Encryption**:
```
Same key for encryption & decryption

Algorithm: AES-256
Speed: Fast (hardware accelerated)
Key size: 256 bits

Use for:
- Data at rest (disk)
- Bulk data encryption
- TLS session encryption (after handshake)

Challenge: Key distribution
```

**Asymmetric Encryption**:
```
Public key encrypts, private key decrypts

Algorithm: RSA, ECC
Speed: Slow (100x slower than symmetric)
Key size: 2048-4096 bits

Use for:
- TLS handshake (exchange session key)
- Digital signatures
- Key exchange

Process:
1. Encrypt with public key
2. Only private key can decrypt
3. Safe to share public key
```

**Hashing**:
```
One-way function: Cannot reverse

Algorithms:
- SHA-256: 256-bit output
- bcrypt: Password hashing (with salt)

Use for:
- Passwords (bcrypt)
- Data integrity (SHA-256)
- Digital signatures

Properties:
- Deterministic: Same input → same output
- One-way: Can't reverse
- Collision-resistant: Hard to find two inputs with same hash
```

---

## Regionalization & Multi-Region

### Multi-Region Architecture

**Purpose**: Low latency globally, disaster recovery

**Strategies**:

**Active-Active**:
```
All regions serve traffic simultaneously

Architecture:
Region US-East ←─┐
Region EU-West ←─┼─ Global Traffic (GeoDNS)
Region AP-South ←┘

Pros:
+ Low latency: Users route to nearest
+ High availability: Region fails, others continue
+ Load distribution: Spread across regions

Cons:
- Complexity: Data synchronization
- Cost: 3x infrastructure
- Consistency: Cross-region coordination

Use for: Global consumer apps (Netflix, Amazon)
```

**Active-Passive (DR)**:
```
Primary region serves traffic
Secondary region on standby

Normal:
Region US-East (Active) ← All traffic
Region EU-West (Passive, syncing data)

Failover:
Region US-East (Down)
Region EU-West (Promoted to Active) ← All traffic

Pros:
+ Simpler: No cross-region sync during normal operation
+ Cost: Secondary can be smaller
+ Consistency: Single active region

Cons:
- Higher latency: Not nearest region
- Failover time: 5-15 minutes

Use for: Business apps, internal tools
```

**Read-Write Split**:
```
Writes: One region (master)
Reads: All regions (replicas)

Architecture:
US-East (Master) ← All writes
   ↓ (replicate)
EU-West (Replica) ← EU reads
AP-South (Replica) ← Asia reads

Pros:
+ Read latency: Low globally
+ Simple: Clear write path
+ Scalable: Add read replicas easily

Cons:
- Write latency: Must go to master region
- Replication lag: Eventual consistency

Use for: Read-heavy apps (95% reads)
```

### Cross-Region Data Sync

**Database Replication**:
```
Async replication:
- Master writes → Replicate to other regions
- Lag: 1-5 seconds
- Risk: Data loss if master fails

Sync replication:
- Wait for replica ACK before confirming
- Lag: 0 seconds
- Penalty: Higher write latency (+100-200ms)

Conflict resolution:
- Last-write-wins (timestamp)
- Vector clocks (concurrent updates)
- CRDTs (Conflict-free Replicated Data Types)
```

**CDN Cache Consistency**:
```
Problem: Cache in EU shows old data after US update

Solutions:
1. Cache purge API (invalidate all regions)
2. Short TTL (1 minute)
3. Versioned URLs (/v2/data.json)

Best: Versioned URLs + long TTL
```

---

## When to Use What

### Communication Protocol Decision Tree

```
Need real-time bidirectional?
├─ YES
│  ├─ Browser client?
│  │  ├─ YES → WebSocket
│  │  └─ NO → gRPC bidirectional stream
│  └─ High performance internal?
│     └─ YES → gRPC
│
└─ NO (Request-response)
   ├─ Public API?
   │  ├─ YES
   │  │  ├─ Complex queries? → GraphQL
   │  │  └─ Simple CRUD? → REST
   │  └─ NO (Internal)
   │     ├─ High performance? → gRPC
   │     └─ Simple? → REST
   │
   └─ Server → Client only?
      ├─ YES
      │  ├─ Firewall concerns? → SSE
      │  └─ Need bidirectional later? → WebSocket
      └─ NO → HTTP/REST
```

### Load Balancer Decision

```
What protocol?
├─ HTTP/HTTPS
│  ├─ Need content-aware routing? → Layer 7 (ALB)
│  ├─ Need SSL termination? → Layer 7
│  └─ Just distribution? → Layer 4 (NLB) - faster
│
├─ TCP (Database, Redis, etc.)
│  └─ → Layer 4 (NLB)
│
└─ UDP
   └─ → Layer 4 (NLB with UDP support)
```

### Database Protocol

```
Database type?
├─ SQL (PostgreSQL, MySQL)
│  └─ Use native protocol over TCP
│     - PostgreSQL: Port 5432
│     - MySQL: Port 3306
│     - Binary protocol (efficient)
│
├─ NoSQL
│  ├─ MongoDB → Binary protocol (BSON) over TCP
│  ├─ Redis → RESP protocol over TCP
│  ├─ Cassandra → CQL over TCP
│  └─ DynamoDB → HTTPS (REST)
│
└─ Message Queue
   ├─ Kafka → Binary protocol over TCP
   ├─ RabbitMQ → AMQP over TCP
   └─ SQS → HTTPS (REST)
```

---

## System Design Examples

### Example 1: Chat Application (WhatsApp, Slack)

**Network Requirements**:
- Real-time message delivery
- Bidirectional communication
- Low latency (< 100ms)
- High concurrency (millions of connections)

**Protocol Choice**:
```
✓ WebSocket:
- Full-duplex communication
- Persistent connection
- Low overhead (2-6 bytes per message)
- Sub-100ms latency

Why not alternatives:
✗ HTTP polling: Too slow, inefficient
✗ Long polling: High server load
✗ SSE: One-way only (need bidirectional)
✗ gRPC: Limited browser support
```

**Architecture**:
```
Clients (1M concurrent)
    ↓ WebSocket
Load Balancer (Layer 7, sticky sessions)
    ↓
WebSocket Servers (20 servers, 50K connections each)
    ↓
Message Queue (Kafka - for offline delivery)
    ↓
Database (Cassandra - message history)
```

**Scaling Considerations**:
- Sticky sessions: Same user → same WebSocket server
- Horizontal scaling: Add more WebSocket servers
- Message queue: Buffer for offline users
- Database: Store message history

### Example 2: Video Streaming (Netflix, YouTube)

**Network Requirements**:
- High bandwidth (4K = 25 Mbps)
- Low latency for live (< 3 seconds)
- Adaptive bitrate
- Global distribution

**Protocol Choice**:
```
For VOD (Video on Demand):
✓ HTTP/2 or HTTP/3:
- Adaptive streaming (HLS, DASH)
- CDN caching
- Resume capability

For Live Streaming:
✓ WebRTC (low latency):
- P2P or server-based
- < 1 second latency
- Interactive use cases

✓ HLS over HTTP (scalability):
- Chunk-based delivery
- 3-10 second latency
- CDN distribution
```

**Architecture**:
```
Origin Server (Transcode)
    ↓ Upload chunks
CDN (CloudFront) - Global distribution
    ↓ HTTPS
Clients (Adaptive bitrate player)

Protocols used:
- Ingest: RTMP or WebRTC (broadcaster → origin)
- Distribution: HLS over HTTPS (CDN → viewers)
- Control: REST API (start/stop stream)
```

**CDN Strategy**:
- Cache video chunks (30 days TTL)
- Multiple bitrates (360p, 720p, 1080p, 4K)
- Regional PoPs (200+ globally)
- 95% cache hit rate

### Example 3: Real-Time Collaboration (Google Docs)

**Network Requirements**:
- Sub-second sync (< 500ms)
- Operational transformation (conflict resolution)
- Bidirectional updates
- Presence (who's online)

**Protocol Choice**:
```
✓ WebSocket:
- Real-time bidirectional
- Low latency
- Persistent connection

Data sync protocol:
- Operational Transform (OT) or
- CRDTs (Conflict-free Replicated Data Types)
```

**Architecture**:
```
Clients
    ↓ WebSocket
Collaboration Server (handles OT)
    ↓
Message Queue (Kafka - event sourcing)
    ↓
Database (Store final document)
```

**Update Flow**:
```
1. User A types "H" at position 0
2. Send via WebSocket: {op: "insert", pos: 0, char: "H"}
3. Server receives, applies OT
4. Broadcast to other users via WebSocket
5. User B receives, applies locally
6. Total latency: 50-200ms
```

### Example 4: Microservices Communication

**Network Requirements**:
- High throughput (10K+ RPS)
- Low latency (< 10ms)
- Service discovery
- Load balancing

**Protocol Choice**:
```
For internal services:
✓ gRPC:
- Binary protocol (fast)
- HTTP/2 (multiplexing)
- Code generation (type-safe)
- Bi-directional streaming

For external APIs:
✓ REST:
- Browser compatibility
- Easy to debug
- Wide adoption
```

**Service Mesh Architecture**:
```
Service A
    ↓ gRPC
Envoy Proxy (sidecar)
    ↓
Service Discovery (Consul, etcd)
    ↓
Envoy Proxy (sidecar)
    ↓ gRPC
Service B

Features:
- Automatic retry
- Circuit breaking
- Load balancing
- Metrics/tracing
- mTLS (mutual TLS)
```

### Example 5: API Gateway

**Network Requirements**:
- Handle multiple protocols
- Rate limiting
- Authentication
- Request routing

**Protocol Support**:
```
Incoming:
- REST (HTTP/HTTPS)
- WebSocket
- GraphQL

Outgoing:
- REST to microservices
- gRPC to internal services
- Message queue for async
```

**Architecture**:
```
Clients (Web, Mobile)
    ↓ HTTPS/WebSocket
API Gateway (Kong, AWS API Gateway)
    ├─ REST → User Service
    ├─ gRPC → Payment Service
    ├─ WebSocket → Notification Service
    └─ Kafka → Analytics Service

Features:
- Authentication (JWT)
- Rate limiting (Token bucket)
- Load balancing (Round-robin)
- Circuit breaker
- Request transformation
```

### Example 6: Distributed Cache (Redis Cluster)

**Network Requirements**:
- Sub-millisecond latency
- High availability
- Data partitioning
- Replication

**Protocol**:
```
✓ RESP (Redis Serialization Protocol):
- Binary-safe
- Simple text protocol
- Pipelining support
- Pub/Sub support

Connection:
- TCP port 6379
- Persistent connections
- Connection pooling
```

**Architecture**:
```
Redis Cluster (6 nodes):
Master 1 (shard 1) ← Slave 1
Master 2 (shard 2) ← Slave 2
Master 3 (shard 3) ← Slave 3

Client
    ↓ Smart client (knows topology)
Routes to correct shard (consistent hashing)

Features:
- Automatic sharding (16384 slots)
- Replication (master-slave)
- Automatic failover
- No single point of failure
```

### Example 7: Notification System

**Network Requirements**:
- Multiple channels (Push, Email, SMS, WebSocket)
- Reliable delivery
- High throughput

**Protocols by Channel**:
```
Push Notifications:
- iOS: APNs over HTTP/2
- Android: FCM over HTTPS
- Web: Web Push over HTTPS

Email:
- SMTP over TCP (sending)
- API over HTTPS (SendGrid, SES)

SMS:
- API over HTTPS (Twilio)

In-app:
- WebSocket (real-time)
- SSE (Server-sent events)
```

**Architecture**:
```
Notification Service
    ↓ HTTP/2
APNs/FCM (Push providers)

    ↓ HTTPS
SendGrid (Email provider)

    ↓ WebSocket
Connected clients (In-app)

Internal:
    ↓ gRPC
Other microservices
```

---

## Additional Network Concepts

### NAT (Network Address Translation)

**Purpose**: Share one public IP among multiple private IPs

**How it works**:
```
Private network:
- Device A: 192.168.1.10
- Device B: 192.168.1.11
- Router: 192.168.1.1 (private), 203.0.113.5 (public)

Outgoing:
Device A (192.168.1.10:5000) → Internet
Router translates: 203.0.113.5:5000 → Internet

Incoming response:
Internet → 203.0.113.5:5000
Router translates: → 192.168.1.10:5000

NAT table tracks mappings
```

**NAT Types**:
```
1. Static NAT (1-to-1):
   - One public IP per private IP
   - Expensive

2. Dynamic NAT (many-to-many from pool):
   - Pool of public IPs
   - Assign temporarily

3. PAT/NAPT (Port Address Translation):
   - Many private → One public (different ports)
   - Most common
```

**NAT Traversal** (for P2P):
```
Problem: Can't directly connect to device behind NAT

Solutions:
1. STUN (Session Traversal Utilities for NAT):
   - Discover public IP/port
   - Works if NAT is "friendly"

2. TURN (Traversal Using Relays around NAT):
   - Relay server
   - Fallback if STUN fails

3. ICE (Interactive Connectivity Establishment):
   - Try STUN first
   - Fall back to TURN
   - Used by WebRTC
```

### Anycast

**Purpose**: Multiple servers share same IP, route to nearest

**How it works**:
```
IP: 1.1.1.1 announced in multiple locations
- San Francisco
- London
- Singapore

User in Europe → Routed to London (nearest)
User in Asia → Routed to Singapore (nearest)

BGP routing handles automatically
```

**Use cases**:
```
1. DNS (8.8.8.8, 1.1.1.1):
   - Low latency globally
   - DDoS mitigation (distributed)

2. CDN edge servers:
   - CloudFlare uses anycast
   - Automatic failover

3. DDoS protection:
   - Attack distributed across locations
   - Hard to overwhelm
```

### Connection Pooling

**Purpose**: Reuse connections, reduce overhead

**Without pooling**:
```
Each request:
1. Open connection (3-way handshake): 150ms
2. TLS handshake: 150ms
3. Send request: 50ms
4. Close connection: 50ms
Total: 400ms

For 1000 requests: 400 seconds = 6.7 minutes
```

**With pooling**:
```
Maintain pool of open connections:
- Min: 10 connections
- Max: 100 connections
- Idle timeout: 60 seconds

First request: 400ms (establish)
Subsequent: 50ms (reuse)

For 1000 requests: 0.4s + (999 × 0.05s) = 50 seconds

Savings: 8x faster
```

**Configuration**:
```
Database connection pool:
- Pool size: 50 connections
- Max wait: 10 seconds
- Test on borrow: True
- Eviction: Close idle > 5 minutes

HTTP client pool:
- Connections per route: 10
- Total connections: 100
- Keep-alive: 60 seconds
- Connection timeout: 5 seconds
```

### Request Coalescing

**Purpose**: Combine multiple identical requests

**Problem**:
```
Same resource requested simultaneously:
- User 1: GET /api/trending
- User 2: GET /api/trending (same time)
- User 3: GET /api/trending (same time)

Naive: 3 separate queries to database
```

**Solution: Request coalescing**:
```
1. User 1 requests /api/trending
   - Start fetching
   - Register in "in-flight" map

2. User 2 requests /api/trending
   - Check in-flight map: Found
   - Attach to User 1's request (wait)

3. User 3 requests /api/trending
   - Check in-flight map: Found
   - Attach to User 1's request (wait)

4. Result arrives
   - Send to all 3 users
   - Remove from in-flight map

Result: 1 database query instead of 3
```

**Implementation (Redis)**:
```
Key: coalesce:/api/trending
Value: request_id_123

Process:
1. Check Redis for key
2. If exists: Wait for pub/sub message
3. If not: Set key, execute query
4. Publish result to channel
5. Delete key
```

---

## Common Interview Questions

### Q: How would you design a global CDN?

**Answer:**
```
Architecture:

1. Edge servers (200+ locations):
   - Cache content
   - Serve from nearest location
   - Handle 95% of requests

2. Origin servers (3 regions):
   - Master content
   - Handle cache misses (5%)

3. DNS routing:
   - GeoDNS routes to nearest edge
   - Anycast for automatic routing

4. Cache hierarchy:
   - L1: Edge cache (hot content)
   - L2: Regional cache (warm content)
   - L3: Origin (cold content)

5. Content distribution:
   - Push: Proactively distribute to edges
   - Pull: Fetch on first request, cache

Protocol: HTTPS over HTTP/2
Latency: < 50ms globally (cache hit)
Bandwidth: Reduce origin traffic by 95%
```

### Q: Explain difference between horizontal and vertical scaling in networking context

**Answer:**
```
Vertical Scaling (Scale Up):
- Bigger server: More CPU, RAM, NIC bandwidth
- Network: 1 Gbps → 10 Gbps NIC
- Limits: Single server bottleneck
- Cost: Exponential (2x performance = 4x cost)

Example:
1 server: 10K QPS
Upgrade: 25K QPS
Max: 100K QPS (then stuck)

Horizontal Scaling (Scale Out):
- More servers: Add identical machines
- Network: Load balancer distributes
- Limits: Nearly unlimited
- Cost: Linear (2x servers = 2x cost)

Example:
1 server: 10K QPS
Add server: 20K QPS
Add 10 servers: 100K QPS
Add 100 servers: 1M QPS

Winner for scale: Horizontal

Network considerations:
- Load balancer must scale (use DNS or multiple LBs)
- East-west traffic (server-to-server) increases
- Network bandwidth becomes critical
```

### Q: How do you handle network partitions in distributed systems?

**Answer:**
```
Network partition: Nodes can't communicate

Example:
Data Center A ←─X─→ Data Center B
(Can't talk)

CAP Theorem applies:
- Consistency
- Availability
- Partition tolerance

Pick 2:

CP (Consistency + Partition tolerance):
- Reject writes during partition
- Preserve data integrity
- Sacrifice availability
- Example: Banking (consistency critical)

AP (Availability + Partition tolerance):
- Accept writes during partition
- Risk: Conflicts when partition heals
- Sacrifice consistency
- Example: Social media (availability critical)

Detection:
- Heartbeat monitoring
- Timeout threshold: 10 seconds
- Action: Trigger partition handling

Resolution:
- Wait for partition to heal
- Apply conflict resolution (last-write-wins, vector clocks)
- Manual intervention if needed
```

### Q: Design a rate limiter at scale

**Answer:**
```
Requirements:
- 100M users
- Per-user limit: 100 requests/minute
- Global limit: 100M requests/minute
- Distributed system

Challenges:
- Can't store all users in single Redis
- Need distributed rate limiting
- Race conditions (concurrent requests)

Solution: Distributed rate limiter

Architecture:
Clients
    ↓
Load Balancer
    ↓
API Servers (100 servers)
    ↓
Redis Cluster (10 shards)
    ↓
Rate limiting logic (sliding window)

Implementation:
1. Shard by user_id: hash(user_id) % 10
2. Each Redis shard handles 10M users
3. Sliding window algorithm (Redis sorted set)
4. Atomic operations (Lua scripts)

Edge case: Race conditions
- Two API servers check same user simultaneously
- Both see 99 requests
- Both allow request 100 and 101
- Solution: Lua script (atomic operation in Redis)

Result:
- 116K QPS rate limit checks
- < 1ms latency per check
- 99.9% accuracy (rare race conditions acceptable)
- Cost: $3K/month for Redis cluster
```

### Q: When would you use UDP over TCP?

**Answer:**
```
Use UDP when:
1. Speed > Reliability
2. Real-time requirements
3. Loss acceptable
4. Broadcast/multicast needed

Examples:

Video conferencing (Zoom):
- 30 fps = 33ms per frame
- If packet lost, skip frame
- TCP retry would cause lag
- UDP = smooth video

Online gaming (Fortnite):
- Player position updates (60/sec)
- Old position irrelevant
- Next update overrides
- UDP = responsive gameplay

DNS queries:
- Small queries (< 512 bytes fit in 1 packet)
- If lost, retry entire query
- UDP = fast resolution (20ms vs 150ms TCP)

Live sports streaming:
- Slight pixelation acceptable
- Buffering would delay broadcast
- UDP = live experience

When NOT to use UDP:
- Financial transactions (must be reliable)
- File transfers (must be complete)
- Database replication (must be consistent)
- API calls (need confirmation)
```

---

## Protocol Comparison Matrix

```
Protocol    | Speed     | Reliability | Use Case              | Browser | Overhead
------------|-----------|-------------|-----------------------|---------|----------
HTTP/1.1    | Medium    | High        | REST APIs             | ✓       | High
HTTP/2      | High      | High        | Modern web/APIs       | ✓       | Medium
HTTP/3      | High      | High        | Mobile, streaming     | ✓       | Low
WebSocket   | High      | High        | Chat, real-time       | ✓       | Very Low
SSE         | High      | High        | Server push           | ✓       | Low
gRPC        | Very High | High        | Microservices         | Limited | Very Low
GraphQL     | Medium    | High        | Complex queries       | ✓       | Medium
WebRTC      | Very High | Medium      | Video calls, P2P      | ✓       | Low
REST        | Medium    | High        | Public APIs           | ✓       | Medium
MQTT        | Medium    | Medium      | IoT                   | Partial | Very Low
```

### Latency Comparison

```
Protocol          | Connection Setup | Per Request | Total (First)
------------------|------------------|-------------|---------------
HTTP/1.1          | 300ms (TCP+TLS)  | 50ms        | 350ms
HTTP/1.1 (reuse)  | 0ms              | 50ms        | 50ms
HTTP/2            | 300ms (TCP+TLS)  | 10ms        | 310ms
HTTP/2 (reuse)    | 0ms              | 10ms        | 10ms
HTTP/3 (QUIC)     | 50ms (0-RTT)     | 10ms        | 60ms
WebSocket         | 150ms (upgrade)  | 5ms         | 155ms
WebSocket (reuse) | 0ms              | 5ms         | 5ms
gRPC              | 300ms (TCP+TLS)  | 5ms         | 305ms
gRPC (reuse)      | 0ms              | 5ms         | 5ms
```

### Bandwidth Efficiency

```
Scenario: Send 1KB data, 1000 times

HTTP/1.1 (new connection each):
- Headers: 200 bytes × 1000 = 200KB
- Data: 1KB × 1000 = 1MB
- Total: 1.2MB

HTTP/2 (one connection, multiplexed):
- Headers: 50 bytes × 1000 = 50KB (compressed)
- Data: 1KB × 1000 = 1MB
- Total: 1.05MB (12% savings)

WebSocket (persistent):
- Initial handshake: 500 bytes
- Frame headers: 4 bytes × 1000 = 4KB
- Data: 1KB × 1000 = 1MB
- Total: 1.004MB (16% savings vs HTTP/2, 83% vs HTTP/1.1)

gRPC (HTTP/2 + Protobuf):
- Headers: 50KB (compressed)
- Data (Protobuf): 300 bytes × 1000 = 300KB (70% smaller than JSON)
- Total: 350KB (71% savings vs HTTP/1.1)
```

---

## Additional Topics (Beyond Original Request)

### CORS (Cross-Origin Resource Sharing)

**Problem**: Browser security prevents cross-origin requests

**Same-Origin Policy**:
```
https://example.com can only call https://example.com APIs
Cannot call https://api.other.com (different origin)

Origin = Protocol + Domain + Port
```

**CORS Headers**:
```
Preflight request (OPTIONS):
Origin: https://example.com
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type

Server response:
Access-Control-Allow-Origin: https://example.com
Access-Control-Allow-Methods: POST, GET, OPTIONS
Access-Control-Allow-Headers: Content-Type
Access-Control-Max-Age: 86400

Now: Actual POST request allowed
```

### Service Discovery

**Purpose**: Find service instances dynamically

**Methods**:

**1. Client-Side Discovery**:
```
Client queries registry → Gets list of instances → Calls directly

Registry (Consul, etcd, Zookeeper):
- Service A: [IP1:8080, IP2:8080, IP3:8080]
- Client picks one (round-robin, random)

Pros:
+ Client controls load balancing
+ No proxy overhead
Cons:
- Client complexity
- Language-specific SDKs
```

**2. Server-Side Discovery** (More common):
```
Client → Load balancer → Registry → Backend

Load balancer queries registry
Routes to healthy instance

Pros:
+ Simple client
+ Centralized logic
Cons:
- Extra hop (small latency)
```

### Connection Draining

**Purpose**: Gracefully shut down servers

**Process**:
```
1. Server to shut down
2. Load balancer marks "draining"
3. No new connections to server
4. Existing connections continue
5. Wait for connections to finish (30-60s)
6. Force close remaining connections
7. Shut down server

Result: Zero dropped requests
```

---

## Quick Decision Guide

### Choose Communication Protocol

```
Question: What's the use case?

Chat/Messaging → WebSocket
Live updates (stock prices) → SSE or WebSocket
Video calling → WebRTC
API (public) → REST
API (internal microservices) → gRPC
Complex data fetching → GraphQL
File transfer → HTTP (chunked) or FTP
Gaming → UDP + custom protocol
IoT sensors → MQTT or UDP
```

### Choose Load Balancer Type

```
Question: What are you load balancing?

HTTP/HTTPS + need URL routing → Layer 7 (ALB)
HTTP/HTTPS + simple distribution → Layer 4 (faster)
Database connections → Layer 4 (NLB)
WebSocket → Layer 7 (sticky sessions)
gRPC → Layer 4 or Layer 7
Any TCP/UDP → Layer 4
```

### Choose Caching Strategy

```
Question: What's the access pattern?

Read-heavy static (images) → CDN + long TTL (7 days)
Read-heavy dynamic (API) → Redis + short TTL (5 min)
Write-heavy → Write-through cache
Rarely updated → Cache-aside (lazy loading)
Real-time prices → No cache or 1-second TTL
```

---

## Summary & Best Practices

### Networking Principles for System Design

1. **Always use HTTPS** (security, SEO, HTTP/2)
2. **Cache aggressively** (CDN, Redis, client-side)
3. **Use connection pooling** (8x latency reduction)
4. **Implement retries with backoff + jitter** (resilience)
5. **Add circuit breakers** (prevent cascading failures)
6. **Choose right protocol** (WebSocket for real-time, gRPC for microservices)
7. **Multi-region deployment** (low latency globally)
8. **Monitor network metrics** (latency, throughput, errors)

### Common Mistakes to Avoid

```
❌ Using HTTP/1.1 for modern apps → Use HTTP/2
❌ No connection pooling → Reuse connections
❌ Polling for real-time → Use WebSocket/SSE
❌ No timeout settings → Always set timeouts
❌ Single region deployment → Use multi-region
❌ Ignoring CDN → Use CDN for static content
❌ No rate limiting → Implement rate limits
❌ Missing circuit breakers → Add circuit breakers
❌ Retry without backoff → Add exponential backoff + jitter
❌ Using REST internally → Consider gRPC
```

### Performance Optimization Checklist

```
✓ Enable HTTP/2 or HTTP/3
✓ Use CDN for static content
✓ Implement connection pooling
✓ Add caching layers (CDN, Redis, client)
✓ Enable compression (gzip, brotli)
✓ Use persistent connections (keep-alive)
✓ Implement request coalescing
✓ Add rate limiting
✓ Set appropriate timeouts
✓ Use WebSocket for real-time (not polling)
✓ Enable TLS 1.3 (faster handshake)
✓ Use binary protocols when appropriate (gRPC, Protobuf)
```

### Interview Preparation Tips

**When discussing networking in interviews:**

1. **Start with requirements**
   - What are the performance needs?
   - Is it real-time?
   - Read-heavy or write-heavy?
   - Global or regional?

2. **Justify protocol choices**
   - Why WebSocket over SSE?
   - Why gRPC over REST?
   - Always explain trade-offs

3. **Discuss scaling**
   - How to handle 10x traffic?
   - Load balancing strategy
   - Caching approach

4. **Consider failure scenarios**
   - What if network fails?
   - Circuit breaker strategy
   - Retry logic

5. **Calculate numbers**
   - Bandwidth requirements
   - Connection limits
   - Latency budget

6. **Mention real examples**
   - "Like how WhatsApp uses WebSocket for chat"
   - "Similar to how Netflix uses CDN"
   - Shows practical knowledge

Good luck with your interviews! 🚀
