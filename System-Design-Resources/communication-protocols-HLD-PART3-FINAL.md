# Communication Protocols - HLD Interview Guide (Part 3 - FINAL)

**Final Section: Performance Benchmarks, Pitfalls, and Interview Cheat Sheet**

---

## Performance Benchmarks & Metrics (Continued)

### Throughput Comparison (Single Server)

```
┌──────────────┬──────────────┬───────────────────┬─────────────┐
│ Protocol     │ Requests/Sec │ Technology        │ Notes       │
├──────────────┼──────────────┼───────────────────┼─────────────┤
│ HTTP/REST    │ 10-20K       │ Node.js/Python    │ JSON parse  │
│              │ 50-100K      │ Go/Rust           │ overhead    │
├──────────────┼──────────────┼───────────────────┼─────────────┤
│ gRPC         │ 100-200K     │ Go/C++            │ Binary fast │
│              │ 50-100K      │ Node.js           │             │
├──────────────┼──────────────┼───────────────────┼─────────────┤
│ WebSocket    │ 50-100K      │ Node.js           │ Persistent  │
│ (messages)   │ 100-500K     │ Go                │ connections │
├──────────────┼──────────────┼───────────────────┼─────────────┤
│ Kafka        │ 1M+ msg/s    │ Kafka cluster     │ Batching    │
│              │ 100K msg/s   │ Single broker     │ optimized   │
├──────────────┼──────────────┼───────────────────┼─────────────┤
│ RabbitMQ     │ 20-50K msg/s │ RabbitMQ cluster  │ Lower than  │
│              │              │                   │ Kafka       │
├──────────────┼──────────────┼───────────────────┼─────────────┤
│ MQTT         │ 100K+ msg/s  │ HiveMQ/Mosquitto  │ Lightweight │
├──────────────┼──────────────┼───────────────────┼─────────────┤
│ UDP          │ Very High    │ Custom            │ No protocol │
│              │ (Limited by  │                   │ overhead    │
│              │  network)    │                   │             │
└──────────────┴──────────────┴───────────────────┴─────────────┘
```

### Bandwidth Efficiency Comparison

```
┌──────────────┬──────────────┬──────────────┬─────────────────┐
│ Protocol     │ Header Size  │ Total Size   │ Efficiency      │
│              │              │ (User Object)│ Rating          │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ HTTP/REST    │ 800+ bytes   │ 950 bytes    │ ⭐              │
│ (HTTP/1.1)   │              │              │ (Baseline)      │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ HTTP/REST    │ 50-100 bytes │ 200 bytes    │ ⭐⭐⭐          │
│ (HTTP/2)     │ (compressed) │              │ (4x better)     │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ gRPC         │ 28 bytes     │ 50 bytes     │ ⭐⭐⭐⭐⭐      │
│              │ (HTTP/2+PB)  │              │ (19x better)    │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ WebSocket    │ 2-6 bytes    │ 52 bytes     │ ⭐⭐⭐⭐⭐      │
│ (after HS)   │ (frame)      │ (JSON)       │ (18x better)    │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ WebSocket    │ 2-6 bytes    │ 30 bytes     │ ⭐⭐⭐⭐⭐+     │
│ (Protobuf)   │              │              │ (32x better)    │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ SSE          │ Event format │ 102 bytes    │ ⭐⭐⭐          │
│              │ overhead     │ (text)       │ (9x better)     │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ MQTT         │ 2 bytes      │ 52 bytes     │ ⭐⭐⭐⭐⭐      │
│              │ (minimal)    │              │ (18x better)    │
└──────────────┴──────────────┴──────────────┴─────────────────┘
```

### Real-World Performance Numbers

```
┌─────────────────────────────┬──────────────────┬────────────┐
│ Company/System              │ Scale            │ Protocol   │
├─────────────────────────────┼──────────────────┼────────────┤
│ WhatsApp                    │ 100B msgs/day    │ WebSocket  │
│                             │ 2B users         │ + Erlang   │
├─────────────────────────────┼──────────────────┼────────────┤
│ Uber                        │ 200K GPS/sec     │ WebSocket  │
│                             │ 1M rides/day     │ + gRPC     │
├─────────────────────────────┼──────────────────┼────────────┤
│ Netflix                     │ 200 Tbps         │ HTTP/2     │
│                             │ 1B hours/week    │ + HLS      │
├─────────────────────────────┼──────────────────┼────────────┤
│ LinkedIn (Kafka)            │ 7 Trillion       │ Kafka      │
│                             │ msgs/day         │            │
├─────────────────────────────┼──────────────────┼────────────┤
│ Discord                     │ 2.5M msgs/sec    │ WebSocket  │
│                             │ 150M users       │ + Elixir   │
├─────────────────────────────┼──────────────────┼────────────┤
│ Twitch                      │ 10M concurrent   │ HLS +      │
│                             │ 31M DAU          │ WebSocket  │
├─────────────────────────────┼──────────────────┼────────────┤
│ Robinhood                   │ 100K orders/sec  │ WebSocket  │
│                             │ (peak)           │ + Kafka    │
├─────────────────────────────┼──────────────────┼────────────┤
│ Amazon                      │ 306M customers   │ REST       │
│                             │ 13B items        │ + gRPC     │
├─────────────────────────────┼──────────────────┼────────────┤
│ Google Docs                 │ 2B documents     │ WebSocket  │
│                             │ 10M concurrent   │ + OT       │
├─────────────────────────────┼──────────────────┼────────────┤
│ AWS IoT Core                │ Billions msgs    │ MQTT       │
│                             │ 10B devices      │            │
└─────────────────────────────┴──────────────────┴────────────┘
```

---

## Common Pitfalls & Best Practices

### Pitfall 1: Using WebSocket for Everything

```
❌ Bad Design:
"I'll use WebSocket for the entire app"

Problems:
├── WebSocket bypasses HTTP caching
├── Load balancers need sticky sessions
├── Debugging is harder
├── No CDN benefits
└── More complex than needed

✅ Better Approach:
Hybrid architecture:
├── REST for CRUD operations (cacheable)
├── WebSocket for real-time features only
├── gRPC for internal microservices
└── Let each protocol do what it's best at

Example: Social Media App
├── GET /feed → REST (cacheable)
├── Like counter updates → WebSocket (real-time)
├── Profile updates → REST (standard CRUD)
└── Internal services → gRPC (high performance)
```

### Pitfall 2: Ignoring Protocol Overhead

```
❌ Bad Thinking:
"JSON is fine, readability matters"

Reality Check:
At scale, overhead matters significantly:

Example: 100M requests/day
├── REST/JSON: 950 bytes × 100M = 95 GB/day
├── gRPC/Protobuf: 50 bytes × 100M = 5 GB/day
└── Savings: 90 GB/day = 2.7 TB/month

Cost Impact:
├── Data transfer: $0.09/GB = $243/month savings
├── Servers needed: 10x fewer with gRPC
├── Total savings: $50K+/month at scale
└── JSON "readability" costs real money

✅ Best Practice:
├── External APIs: JSON (dev-friendly)
├── Internal APIs: Protobuf (efficient)
├── Mobile apps: Protobuf (save bandwidth)
└── Debugging: Use tools (grpcurl, Bloom RPC)
```

### Pitfall 3: Not Planning for Scale

```
❌ Bad Assumption:
"We'll start with REST and migrate to WebSocket later"

Problems:
├── Architecture assumptions baked in
├── Stateless REST → Stateful WebSocket migration is hard
├── Database design may not support real-time
├── Client apps need complete rewrite
└── Expensive migration (months of work)

✅ Better Approach:
Design for future scale from Day 1:

Example: Chat App
Day 1 (MVP):
├── Use WebSocket from start
├── Even if only 1000 users
├── Architecture scales naturally
└── No painful migration later

Cost of getting it wrong:
├── Slack's migration to WebSocket (months)
├── Had to rewrite client apps
├── Database schema changes
├── Customer disruption
└── Could have been avoided
```

### Pitfall 4: Ignoring Connection Limits

```
❌ Bad Design:
"Just add more servers for WebSocket"

Reality:
Connection limits per server:
├── Node.js: ~10K connections
├── Go: ~100K connections
├── C++: ~1M connections
└── Hardware: File descriptor limits

Problem at Scale:
1M concurrent users:
├── Node.js: Need 100 servers
├── Go: Need 10 servers
└── 10x difference in cost

✅ Best Practice:
Choose technology based on scale:
├── <10K connections: Node.js (easier dev)
├── 10K-100K: Go (good balance)
├── >100K per server: C/C++/Rust (max efficiency)
└── Use connection pooling, load balancing
```

### Pitfall 5: Not Implementing Backpressure

```
❌ Bad Design:
Producer sends unlimited messages to consumer

Problems:
├── Consumer overwhelmed
├── Memory exhaustion
├── Messages dropped
├── System crash
└── Data loss

✅ Best Practice:
Implement backpressure mechanisms:

WebSocket:
├── Client: Pause reading if buffer full
├── Server: Detect slow clients, throttle
├── Drop slow clients if necessary
└── Alert on consistently slow clients

Kafka:
├── Consumer processes at own pace
├── Offset tracking (can catch up)
├── Partitions for parallelism
└── Built-in backpressure handling

gRPC Streaming:
├── Flow control at stream level
├── Window size limits
├── Can signal slow consumer
└── Automatic backpressure
```

### Pitfall 6: Celebrity/Hotspot Problem

```
❌ Bad Design:
Treat all users equally in fan-out

Problem:
Celebrity tweets to 100M followers:
├── Fan-out on write: 100M writes to Redis
├── Takes 10+ seconds
├── Overwhelms system
├── Other users affected
└── Unacceptable performance

✅ Better Approach:
Hybrid fan-out:

Normal users (< 10K followers):
└── Fan-out on write (pre-compute feeds)

Celebrities (> 10K followers):
└── Fan-out on read (merge at request time)

Benefits:
├── Normal users: Fast read
├── Celebrities: Slower read (acceptable)
├── System not overwhelmed
└── Fair for everyone

Twitter's Numbers:
├── 99% of users: < 10K followers (fan-out on write)
├── 1% of users: > 10K followers (fan-out on read)
└── System handles both efficiently
```

### Pitfall 7: Not Considering Mobile Constraints

```
❌ Bad Design:
"Desktop-first API works for mobile too"

Mobile Realities:
├── Slower networks (3G/4G)
├── Limited bandwidth caps
├── Battery constraints
├── Intermittent connectivity
└── Higher latency

Problems with Desktop API:
├── Over-fetching (100 fields when need 10)
├── Multiple requests (waterfall)
├── Large payloads (JSON bloat)
├── Constant polling (battery drain)
└── Poor user experience

✅ Mobile-Optimized Approach:

Option 1: GraphQL
├── Client requests exactly what it needs
├── Single request for complex data
├── Reduced bandwidth (30-50%)
└── Better on slow networks

Option 2: Dedicated Mobile Endpoints
├── /api/mobile/v1/feed (minimal data)
├── Fewer fields, smaller responses
├── Pagination with smaller page sizes
└── Aggressive caching

Option 3: Protocol Buffers
├── Binary format (70% smaller)
├── Less CPU for parsing (battery)
├── Faster on slow networks
└── Use with gRPC or custom protocol

Instagram's Approach:
├── Separate mobile API
├── Aggressive image compression
├── Smart prefetching
├── Offline mode with sync
└── Result: Works on 2G networks
```

### Pitfall 8: Single Protocol Mindset

```
❌ Bad Thinking:
"Pick one protocol and use it everywhere"

Reality:
Different components have different needs

Example: Uber Architecture
┌──────────────────┬─────────────────┐
│ Component        │ Protocol        │
├──────────────────┼─────────────────┤
│ Driver GPS       │ WebSocket       │
│ Rider Requests   │ REST            │
│ Microservices    │ gRPC            │
│ Event Processing │ Kafka           │
│ Push Notifs      │ FCM/APNs        │
└──────────────────┴─────────────────┘

✅ Best Practice:
Choose protocol per component:
├── Public API: REST (universal)
├── Real-time features: WebSocket/SSE
├── Internal services: gRPC
├── Event streaming: Kafka
├── IoT devices: MQTT
└── Gaming: UDP + TCP

Trade-off: Complexity vs Optimization
├── More protocols = more to maintain
├── But each optimized for its use case
├── At scale, optimization worth it
└── Start simple, add protocols as needed
```

### Pitfall 9: Ignoring Failure Scenarios

```
❌ Bad Design:
No fallback when primary protocol fails

Scenarios:
├── WebSocket blocked by firewall
├── UDP blocked by corporate network
├── gRPC not supported by legacy client
└── Kafka broker down

✅ Best Practice:
Always have fallback:

WebSocket Fallback Hierarchy:
1. Try WebSocket (optimal)
2. If blocked → Try SSE
3. If SSE blocked → Long polling
4. If all fail → Show error message

Example - Collaborative Editing:
Primary: WebSocket (real-time)
├── Check WebSocket support on load
├── If fails: Fall back to long polling
├── Show warning: "Limited functionality"
└── Still usable, just slower

Netflix Example:
Primary: HTTP/2 (modern browsers)
├── If HTTP/2 not supported
├── Fallback: HTTP/1.1 (works but slower)
├── If that fails: Error with browser upgrade message
└── Graceful degradation
```

### Pitfall 10: Not Monitoring Protocol-Specific Metrics

```
❌ Bad Practice:
Only monitor generic metrics (CPU, memory)

✅ Protocol-Specific Metrics to Track:

WebSocket:
├── Active connections count
├── Messages sent/received per second
├── Connection duration distribution
├── Reconnection rate
├── Error rate by error type
└── Memory per connection

gRPC:
├── RPC latency (p50, p95, p99)
├── Success/error rate per method
├── Request size distribution
├── Response size distribution
└── Stream duration (for streaming RPCs)

Kafka:
├── Producer throughput
├── Consumer lag (critical!)
├── Partition distribution
├── Broker disk usage
└── Replication lag

MQTT:
├── Message publish rate
├── Subscription count per topic
├── QoS level distribution
├── Retained message count
└── Session duration

REST:
├── Response time by endpoint
├── Cache hit ratio
├── Rate limit hits
├── Status code distribution
└── Request size trends

Alerting Thresholds:
├── WebSocket: conn count > 90% capacity
├── gRPC: p99 latency > 100ms
├── Kafka: consumer lag > 1 minute
├── MQTT: publish failures > 1%
└── REST: cache hit ratio < 70%
```

---

## Interview Cheat Sheet

### Quick Protocol Selection Guide

```
Ask yourself these questions:

1. Is it real-time?
   ├── YES → Continue to Q2
   └── NO → REST

2. Is it bidirectional?
   ├── YES → WebSocket
   └── NO → SSE or REST

3. Is it internal (microservices)?
   ├── YES → gRPC
   └── NO → REST

4. Is it IoT/battery-constrained?
   ├── YES → MQTT
   └── NO → Continue

5. Is it event-driven?
   ├── YES → Kafka/RabbitMQ
   └── NO → REST

6. Is latency <10ms critical?
   ├── YES → gRPC or UDP
   └── NO → REST acceptable

7. Is it video streaming?
   ├── YES → HTTP/2 + HLS
   └── NO → Based on above

8. Is it gaming?
   ├── YES → UDP + TCP
   └── NO → Based on above
```

### Protocol Strengths Summary

```
HTTP/REST:
✓ Universal support
✓ Caching infrastructure
✓ Simple to use
✓ Great for CRUD
✗ Higher latency
✗ More bandwidth

gRPC:
✓ 10x faster than REST
✓ Strong typing
✓ Efficient bandwidth
✓ Streaming support
✗ Not browser-native
✗ Harder to debug

WebSocket:
✓ Real-time bidirectional
✓ Low latency
✓ Persistent connection
✓ Flexible
✗ Complex to scale
✗ Needs sticky sessions

GraphQL:
✓ No over/under-fetching
✓ Single endpoint
✓ Self-documenting
✓ Real-time (subscriptions)
✗ Complex caching
✗ Can be slow (N+1)

SSE:
✓ Simple push
✓ Auto-reconnect
✓ HTTP-based
✓ Firewall-friendly
✗ Server → Client only
✗ Text only

MQTT:
✓ Tiny header (2 bytes)
✓ Battery-efficient
✓ QoS levels
✓ Offline support
✗ IoT-specific
✗ Not for web

Kafka:
✓ High throughput (1M/s)
✓ Durable
✓ Replayable
✓ Ordered
✗ Complexity
✗ Operational overhead

UDP:
✓ Ultra-low latency (1-10ms)
✓ No overhead
✓ High throughput
✓ Simple
✗ Unreliable
✗ No ordering

TCP:
✓ Reliable
✓ Ordered
✓ Flow control
✓ Universal
✗ Higher latency
✗ Head-of-line blocking
```

### When to Use What - Quick Reference

```
Use REST when:
├── Public API for third-party developers
├── Standard CRUD operations
├── Caching is important
├── Simple request-response
└── Example: E-commerce, blogs, SaaS apps

Use gRPC when:
├── Internal microservices communication
├── Need low latency (<10ms)
├── High throughput required (>50K QPS)
├── Strong typing important
└── Example: Payment processing, trading systems

Use WebSocket when:
├── Bidirectional real-time needed
├── Chat, messaging, collaboration
├── Live updates with user interaction
├── Gaming (if can't use UDP)
└── Example: WhatsApp, Slack, Google Docs

Use GraphQL when:
├── Multiple client types (web, mobile, tablet)
├── Complex nested data requirements
├── Frequent UI iterations
├── Want to avoid over-fetching
└── Example: Facebook, GitHub, Shopify APIs

Use SSE when:
├── Server → Client push only
├── Live feeds, notifications
├── Want automatic reconnection
├── Simpler than WebSocket
└── Example: Stock tickers, news feeds, dashboards

Use Long Polling when:
├── Fallback for WebSocket/SSE
├── Need real-time but can't use modern protocols
├── Legacy browser support
└── Example: Old enterprise applications

Use Kafka when:
├── Event-driven architecture
├── Need message persistence
├── Multiple consumers of same data
├── Replay capability important
└── Example: Uber rides, Twitter posts, order processing

Use RabbitMQ when:
├── Task queues
├── Background job processing
├── Simpler than Kafka
├── Don't need replay
└── Example: Email sending, report generation

Use MQTT when:
├── IoT devices
├── Battery-powered sensors
├── Intermittent connectivity
├── Extremely low bandwidth
└── Example: Smart home, industrial sensors

Use UDP when:
├── Ultra-low latency required (<10ms)
├── Packet loss acceptable
├── Gaming, video conferencing
├── Real-time position tracking
└── Example: CS:GO, Zoom video, Valorant

Use TCP when:
├── Reliability critical
├── Ordered delivery needed
├── Can accept higher latency
├── File transfer, web browsing
└── Example: HTTP, FTP, most applications
```

### Interview Dos and Don'ts

```
✅ DO:

1. Explain trade-offs
   "I'd use WebSocket for chat because [reasons]
    but consider REST for [other features] because [reasons]"

2. Consider scale
   "At 100K connections, we need X servers"
   "With caching, we can handle 10x more traffic"

3. Mention alternatives
   "I chose WebSocket over SSE because bidirectional"
   "Considered long polling but rejected due to latency"

4. Use real numbers
   "WebSocket: 2-byte header vs REST: 800-byte header"
   "gRPC: 5ms latency vs REST: 50ms latency"

5. Reference real systems
   "Twitter uses hybrid fan-out like I described"
   "Netflix serves 200 Tbps via CDN with HLS"

6. Draw architecture diagrams
   Show how components connect with protocols
   
7. Calculate capacity
   "100M users × 10 requests/day = 1B requests/day"
   "Need 10K QPS, REST can handle this"

❌ DON'T:

1. Use vague reasoning
   ✗ "WebSocket is faster"
   ✓ "WebSocket provides <100ms latency vs polling's 2.5s"

2. Ignore scale
   ✗ "Use WebSocket"
   ✓ "Use WebSocket, need 100 servers for 1M connections"

3. Pick one protocol for everything
   ✗ "Everything uses REST"
   ✓ "REST for API, gRPC for services, WebSocket for chat"

4. Forget about failures
   ✗ Only describe happy path
   ✓ "If WebSocket fails, fall back to long polling"

5. Ignore costs
   ✗ "Scale infinitely"
   ✓ "gRPC saves 90% bandwidth = $50K/month"

6. Use buzzwords without understanding
   ✗ "Use WebSocket because it's modern"
   ✓ "Use WebSocket because we need bidirectional real-time"

7. Overcomplicate
   ✗ "Let's use 10 different protocols"
   ✓ "Start with REST, add WebSocket for real-time only"
```

### Interview Answer Template

```
When asked: "How would you implement [feature]?"

1. Clarify Requirements (30 seconds)
   "Let me confirm the requirements:
    - Is real-time needed?
    - What's the expected scale?
    - Are there latency requirements?
    - Mobile or web or both?"

2. Identify Protocol (30 seconds)
   "Based on [requirements], I'd use [protocol] because:
    - [Reason 1 with numbers]
    - [Reason 2 with comparison]
    - [Trade-off consideration]"

3. Draw Architecture (2 minutes)
   Show components and protocols between them
   
4. Explain Flow (1 minute)
   Walk through a typical request/response

5. Capacity Estimation (1 minute)
   "For 100M users:
    - Need X QPS
    - Need Y servers
    - Need Z storage"

6. Alternatives Considered (30 seconds)
   "I considered [alternative] but rejected because [reason]"

7. Trade-offs (30 seconds)
   "Main trade-off is [X vs Y], chose [X] because [reason]"

Total: ~6 minutes for comprehensive answer
```

### Key Phrases for Interviews

```
Demonstrate Understanding:

"The key trade-off here is latency vs complexity..."
"At this scale, we need to consider..."
"For this use case, [protocol] provides the best balance..."
"The bottleneck would be [X], so we need [Y]..."
"I'd use [protocol] for [component] because [specific reason with numbers]..."

Show Experience:

"Similar to how Twitter handles [X]..."
"Netflix solves this by [approach]..."
"This is the celebrity problem that Facebook faced..."
"Google Docs uses operational transform for this..."
"Uber's architecture uses [pattern]..."

Demonstrate Depth:

"We'd need to implement backpressure because..."
"Connection pooling would help with..."
"The cache hit ratio would be approximately X%..."
"We can shard by [X] to distribute load..."
"For disaster recovery, we'd need [approach]..."

Show Pragmatism:

"We'd start with [simple] and migrate to [complex] only if needed..."
"For MVP, [protocol] is sufficient, but for scale we'd need [other]..."
"The cost-benefit analysis suggests [choice]..."
"Given the team's expertise in [X], I'd recommend..."
```

---

## Top 10 HLD Questions Summary Table

```
┌──────────────────────┬─────────────────────┬──────────────────────┬─────────────────┐
│ System               │ Primary Protocol    │ Why This Protocol    │ Scale           │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ WhatsApp/Messenger   │ WebSocket           │ Bidirectional chat   │ 2B users        │
│                      │ + Kafka             │ <100ms latency       │ 50B msgs/day    │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ Uber/Lyft            │ WebSocket (GPS)     │ Continuous updates   │ 1M drivers      │
│                      │ + gRPC + Redis Geo  │ Location queries     │ 200K GPS/sec    │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ Twitter/Instagram    │ REST + WebSocket    │ Feed (REST)          │ 500M users      │
│                      │ + Kafka             │ Real-time (WS)       │ 50M posts/day   │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ Robinhood (Trading)  │ WebSocket (prices)  │ Real-time quotes     │ 10M users       │
│                      │ + REST + Kafka      │ Reliable orders      │ 100K orders/sec │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ Netflix/YouTube      │ HTTP/2 + HLS        │ CDN-friendly         │ 1B users        │
│                      │ + REST              │ Adaptive bitrate     │ 500M hours/day  │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ Amazon (E-commerce)  │ REST                │ CRUD operations      │ 300M users      │
│                      │ + GraphQL (mobile)  │ Cacheable            │ 1M orders/day   │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ Google Docs          │ WebSocket + OT      │ Collaborative edit   │ 100M docs       │
│                      │ + Kafka             │ <100ms latency       │ 10M concurrent  │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ Online Gaming (FPS)  │ UDP + TCP           │ Ultra-low latency    │ 1M players      │
│                      │                     │ (<50ms required)     │ 60M updates/sec │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│ IoT/Smart Home       │ MQTT                │ Battery-efficient    │ 100M devices    │
│                      │ + Kafka             │ Minimal bandwidth    │ 833K msgs/sec   │
├──────────────────────┼─────────────────────┼──────────────────────┼─────────────────┤
│
