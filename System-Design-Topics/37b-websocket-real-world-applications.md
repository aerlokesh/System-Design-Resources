# ⚡ WebSocket & Real-Time at Scale — Real-World Applications

> Each example below is a **full mini system design** showing exactly how WebSockets and real-time push systems are used in production at companies like Slack, Discord, Uber, Figma, WhatsApp, and more. Every architecture decision, message flow, and scaling strategy is explained with the **WHY** behind it.

---

## 📋 Table of Contents

1. [Slack — Workspace Messaging & Presence](#1-slack--workspace-messaging--presence)
2. [Discord — Voice & Text for 150M+ Users](#2-discord--voice--text-for-150m-users)
3. [Uber — Real-Time Driver Location & ETA](#3-uber--real-time-driver-location--eta)
4. [Figma — Collaborative Design Cursors](#4-figma--collaborative-design-cursors)
5. [WhatsApp — Message Delivery & Read Receipts](#5-whatsapp--message-delivery--read-receipts)
6. [Robinhood — Live Stock Price Ticker](#6-robinhood--live-stock-price-ticker)
7. [Twitch — Live Chat at 100K+ Messages/Sec](#7-twitch--live-chat-at-100k-messagessec)
8. [Google Docs — Real-Time Collaborative Editing](#8-google-docs--real-time-collaborative-editing)
9. [DoorDash — Live Order Tracking Map](#9-doordash--live-order-tracking-map)
10. [Zoom — Meeting Signaling & Chat](#10-zoom--meeting-signaling--chat)
11. [Twitter/X — Live Timeline & Notifications](#11-twitterx--live-timeline--notifications)
12. [Notion — Real-Time Page Collaboration](#12-notion--real-time-page-collaboration)
13. [Coinbase — Live Crypto Price Feeds](#13-coinbase--live-crypto-price-feeds)
14. [Instagram — Live Video Comments](#14-instagram--live-video-comments)
15. [Multiplayer Games — State Synchronization](#15-multiplayer-games--state-synchronization)
16. [16–25: Additional Real-World Applications (Summary)](#1625-additional-real-world-applications-summary)
17. [🏆 Cheat Sheet: Real-Time Technology Selection Guide](#-cheat-sheet-real-time-technology-selection-guide)
18. [🎯 Interview Summary: "How Would You Build Real-Time at Scale?"](#-interview-summary-how-would-you-build-real-time-at-scale)

---

## 1. Slack — Workspace Messaging & Presence

### 🏗️ Architecture Context

Slack handles **millions of concurrent WebSocket connections**. Every message, typing indicator, emoji reaction, and presence change (online/away/offline) is delivered in real-time via persistent WebSocket connections.

### 📐 Architecture

```
┌──────────┐     WebSocket      ┌──────────────────┐
│  Client  │ ←─────────────────→│  Gateway Server   │
│ (Browser)│     (persistent)   │  (Envoy + custom) │
└──────────┘                    └────────┬─────────┘
                                         │
                                    ┌────▼────┐
                                    │  Redis   │  ← Connection registry
                                    │  Pub/Sub │  ← Cross-gateway messaging
                                    └────┬────┘
                                         │
                              ┌──────────▼──────────┐
                              │   Message Service    │
                              │  (processes + stores) │
                              └──────────┬──────────┘
                                         │
                              ┌──────────▼──────────┐
                              │    MySQL / Vitess    │
                              │  (persistent store)   │
                              └─────────────────────┘
```

### 💡 Key Design Decisions

**Connection Management**:
- Each gateway server holds ~100K–500K WebSocket connections
- Connection registry in Redis: `user:U123 → gateway:GW-5, conn_id:abc`
- When user sends message → gateway publishes to Redis Pub/Sub → target user's gateway pushes via WebSocket

**Presence System**:
```
# User opens Slack → WebSocket connected
SET presence:U123 "online" EX 30     # Expires in 30s

# Heartbeat every 10s (client sends ping)
EXPIRE presence:U123 30              # Reset TTL

# No heartbeat for 30s → key expires → user is "offline"
# Presence change → publish to channel members via Redis Pub/Sub
PUBLISH channel:C456 '{"type":"presence","user":"U123","status":"away"}'
```

**Typing Indicators**:
```
# User starts typing → send to WebSocket
→ Gateway publishes: PUBLISH channel:C456 '{"type":"typing","user":"U123"}'
→ Other members' gateways push to their WebSockets
→ Client shows "Alice is typing..." for 3 seconds (client-side timeout)
→ No persistence needed — purely ephemeral
```

### 🔧 Message Flow

```
1. Alice types message in #general
2. Client sends via WebSocket → Gateway GW-3
3. GW-3 → Message Service: validate, store in MySQL, return msg_id
4. Message Service → Redis Pub/Sub: PUBLISH channel:general {message}
5. All gateways subscribed to #general receive the message
6. Each gateway pushes to connected users who are in #general
7. Offline users: message stored in MySQL, delivered on reconnect
```

### 🎯 Why WebSocket?

| Alternative | Problem |
|-------------|---------|
| HTTP Polling | 10 sec latency for messages; 99% empty polls waste resources |
| SSE | Server-to-client only; can't send messages without separate HTTP endpoint |
| Long Polling | Works but 100-500ms latency; connection overhead on every message |

### Scale Numbers

- **~1.5M concurrent WebSocket connections** (peak)
- **~25K messages/second** across all workspaces
- **<100ms end-to-end latency** (send → deliver)
- Gateway fleet: ~50-100 servers, each holding ~30K connections

---

## 2. Discord — Voice & Text for 150M+ Users

### 🏗️ Architecture Context

Discord handles **~19 million concurrent connections** at peak. Their real-time system supports text chat, voice/video, typing indicators, emoji reactions, and presence — all through a single WebSocket connection per client.

### 📐 Architecture

```
Client ←→ WebSocket Gateway (Elixir/Erlang) ←→ Guild (Server) Sessions
                                                      │
                                              ┌───────▼───────┐
                                              │   Message Bus  │
                                              │   (Pub/Sub)    │
                                              └───────┬───────┘
                                                      │
                                        ┌─────────────▼──────────────┐
                                        │   Cassandra (messages)      │
                                        │   ScyllaDB (recent msgs)   │
                                        └────────────────────────────┘
```

### 💡 Key Design Decisions

**Why Elixir/Erlang for Gateways?**
- Erlang BEAM VM handles **millions of lightweight processes** (one per connection)
- Each connection = one Erlang process (~2KB memory vs ~1MB for OS thread)
- Built-in fault tolerance: if one process crashes, others unaffected
- Hot code reloading: update gateway code WITHOUT disconnecting users

**Guild (Server) Sharding**:
```
# Each Discord server ("guild") is assigned to a guild session
# Guild session tracks: members online, voice channels, active connections
# Sharded by guild_id across session servers

# Message to #general in Guild G123:
1. User's gateway → Guild Session for G123
2. Guild Session → fan-out to all members' gateways
3. Each gateway → push via WebSocket to their connected clients
```

**Lazy Loading**:
```
# Don't send ALL guild data on connect — too much for users in 100+ servers
# Instead:
1. Connect → receive list of guilds (name, icon, unread count only)
2. User clicks guild → request full member list, channel list, recent messages
3. Subscribe to real-time events for that guild only
4. Switch guilds → unsubscribe from old, subscribe to new
```

### Scale Numbers

- **~19M concurrent WebSocket connections**
- **~40M events/second** (messages, reactions, typing, presence)
- Single gateway server: **~1M connections** (Elixir/Erlang)
- Voice: separate UDP connections via **WebRTC**

---

## 3. Uber — Real-Time Driver Location & ETA

### 🏗️ Architecture Context

When you're watching your Uber driver approach, the car icon moves smoothly on the map. That's a real-time system pushing **GPS updates every 4 seconds** from the driver's phone to the rider's phone.

### 📐 Architecture

```
Driver Phone                                           Rider Phone
    │                                                      ▲
    │ GPS every 4s                                         │ Location update push
    │ (HTTPS POST)                                         │ (WebSocket)
    ▼                                                      │
┌─────────────┐     ┌──────────────┐     ┌────────────────┐
│ Location    │────→│  Kafka Topic  │────→│  WebSocket     │
│ Ingestion   │     │ (driver.locs) │     │  Gateway       │
│ Service     │     └──────┬───────┘     └────────────────┘
└─────────────┘            │
                    ┌──────▼───────┐
                    │  Location    │
                    │  Service     │  → Redis GEO (current positions)
                    │  (consumer)  │  → Trip Matcher
                    └──────────────┘
```

### 💡 Key Design Decisions

**Driver → Server: HTTPS, not WebSocket**:
```
# Why HTTPS POST instead of WebSocket for driver locations?
# 1. Driver app sends location every 4 seconds — not truly "real-time streaming"
# 2. HTTP is simpler, more reliable on mobile networks (reconnect = just retry POST)
# 3. Server-side: batch-friendly (Kafka ingestion)
# 4. Driver doesn't need server push (dispatch messages are rare)
```

**Server → Rider: WebSocket**:
```
# Why WebSocket for rider updates?
# 1. Rider stares at map — needs instant car movement (every 4s update)
# 2. WebSocket avoids polling overhead
# 3. Also pushes: ETA changes, driver messages, trip status updates
```

**Location Processing Pipeline**:
```
1. Driver app → POST /location { lat, lng, heading, speed, trip_id }
2. Location Ingestion → Kafka topic "driver.locations"
3. Kafka Consumer:
   a. Update Redis GEO: GEOADD active_drivers {lng} {lat} driver_456
   b. If on active trip → lookup rider's WebSocket → push update
   c. If matching → GEORADIUS to find nearby drivers
4. WebSocket Gateway → push to rider: { lat, lng, eta_seconds, heading }
5. Rider's app → interpolate between GPS points for smooth animation
```

**Client-Side Interpolation (Smooth Movement)**:
```
// Rider receives: { lat: 37.7749, lng: -122.4194, timestamp: T1 }
// 4 seconds later: { lat: 37.7751, lng: -122.4190, timestamp: T2 }
// Client ANIMATES between these two points over 4 seconds
// Result: smooth car movement, not teleporting every 4s
```

---

## 4. Figma — Collaborative Design Cursors

### 🏗️ Architecture Context

Figma supports **real-time collaboration** where multiple designers see each other's cursors, selections, and edits in <50ms. This requires extremely low latency and efficient delta updates.

### 📐 Architecture

```
Designer A ←→ WebSocket ←→ Figma Server ←→ WebSocket ←→ Designer B
                              │
                        ┌─────▼─────┐
                        │  Document  │
                        │  CRDT     │  ← Conflict-free replicated data type
                        │  Engine   │
                        └───────────┘
```

### 💡 Key Design Decisions

**Cursor Positions — Ephemeral, High Frequency**:
```
# Every mouse move → send cursor position (throttled to ~30fps)
→ WebSocket send: { type: "cursor", x: 340, y: 120, userId: "A" }
→ Server → broadcast to all other users in the document
→ NOT persisted — purely in-memory, ephemeral

# Optimization: Only send to users viewing the SAME page/frame
# If Designer B is on page 2, don't send page 1 cursor positions
```

**Design Edits — CRDTs for Conflict Resolution**:
```
# Two designers move the same rectangle simultaneously:
# Designer A: move rect to (100, 200)
# Designer B: move rect to (300, 400)

# CRDT resolves: Last-Writer-Wins with vector clocks
# Both see the same final state — no conflicts, no locking
```

**Binary Protocol (not JSON)**:
```
# Figma uses a custom binary protocol over WebSocket
# Why? JSON parsing overhead is too high for 30fps cursor updates
# Binary: ~10-20 bytes per cursor update vs ~100+ bytes JSON
# 50 collaborators × 30 updates/sec = 1500 messages/sec per document
# Binary keeps bandwidth manageable
```

### 🎯 Why WebSocket Over Alternatives?

- **Bidirectional**: Both directions needed (edits + cursor + selections)
- **Low latency**: <50ms required for cursor feel "live"
- **Persistent**: No reconnection overhead on every interaction
- **Binary frames**: WebSocket supports binary; SSE is text-only

---

## 5. WhatsApp — Message Delivery & Read Receipts

### 📐 Architecture

```
Sender                                                       Receiver
   │                                                             ▲
   │ Send message                                                │ Push message
   │ (XMPP/custom binary over TCP)                              │ (XMPP/custom)
   ▼                                                             │
┌──────────┐     ┌─────────────┐     ┌─────────────┐     ┌──────────┐
│ Sender's │────→│  Routing    │────→│  Offline    │────→│ Receiver's│
│ Gateway  │     │  Service    │     │  Queue      │     │ Gateway   │
└──────────┘     └──────┬──────┘     └─────────────┘     └──────────┘
                        │
                 ┌──────▼──────┐
                 │   Mnesia /  │  ← Connection registry
                 │   Custom DB │  ← Which gateway has which user
                 └─────────────┘
```

### 💡 Key Design: Delivery Acknowledgments

```
# Message lifecycle:
1. Sender sends message → single check mark (✓) = sent to server
2. Server delivers to receiver → double check mark (✓✓) = delivered
3. Receiver reads message → blue check marks (✓✓) = read

# How it works:
# Sent: Server ACKs back to sender's connection
# Delivered: Receiver's device ACKs → server forwards ACK to sender
# Read: Receiver opens chat → "read" event → server forwards to sender

# All via persistent TCP/WebSocket — no polling
```

**Offline Handling**:
```
# Receiver offline → messages queue in "offline store"
# When receiver reconnects:
1. Authenticate
2. Server checks offline queue
3. Deliver all queued messages in order
4. Send delivery receipts for each back to senders
5. Clear offline queue
```

**End-to-End Encryption Impact on Architecture**:
```
# Server cannot read messages — just routes encrypted blobs
# Server stores: encrypted_payload, sender, receiver, timestamp
# Decryption happens only on recipient's device
# Server is a "dumb pipe" — routes without inspecting content
```

---

## 6. Robinhood — Live Stock Price Ticker

### 📐 Architecture

```
                Market Data Feeds
                 (NYSE, NASDAQ)
                       │
                ┌──────▼──────┐
                │  Market Data │
                │  Aggregator  │  → Normalize, deduplicate
                └──────┬──────┘
                       │
                ┌──────▼──────┐
                │    Kafka    │  ← Topic per symbol: prices.AAPL, prices.TSLA
                └──────┬──────┘
                       │
                ┌──────▼──────┐
                │  Streaming  │  → Subscribe users to their watchlist symbols
                │  Service    │  → Push price updates via WebSocket
                └──────┬──────┘
                       │
              ┌────────▼────────┐
              │  WebSocket      │
              │  Gateway        │ → Millions of mobile/web clients
              └─────────────────┘
```

### 💡 Key Design: Topic-Based Subscriptions

```
# User opens app → WebSocket connect → subscribe to their watchlist
subscribe: ["AAPL", "TSLA", "AMZN"]

# Server maintains: symbol → set of subscribers
# When AAPL price changes:
1. Market feed → Kafka topic "prices.AAPL"
2. Streaming service consumes → broadcasts to all AAPL subscribers
3. Client receives: { symbol: "AAPL", price: 185.42, change: +1.2% }

# User adds GOOG to watchlist → subscribe to "GOOG" channel
# User removes TSLA → unsubscribe from "TSLA" channel

# Optimization: Batch updates
# Don't send every tick — aggregate over 100ms windows
# Client-side: smooth animation between price points
```

**Scale Challenge: Hot Symbols**
```
# AAPL during earnings: 5M+ subscribers watching
# Can't fan-out 5M WebSocket pushes from one server
# Solution: hierarchical fan-out
#   Kafka → 100 streaming servers → each pushes to 50K clients
#   Total: 100 × 50K = 5M clients, each streaming server handles its share
```

---

## 7. Twitch — Live Chat at 100K+ Messages/Sec

### 💡 Key Design: Channel-Based Sharding

```
# Popular streamer: 200K viewers in chat → 200K WebSocket connections
# Solution: Shard viewers across multiple "chat servers"

# Channel: #ninja (200K viewers)
# → 20 chat servers, each handling ~10K connections
# → Message published to internal Pub/Sub → all 20 servers broadcast

# Rate limiting (prevent spam):
# - 1 message per 1.5 seconds per user (non-subscriber)
# - Subscribers: 1 message per 0.5 seconds
# - Moderators: no limit
# Enforced server-side, tracked in Redis: INCR ratelimit:user_123:channel_456

# Message dropping under extreme load:
# If chat server overloaded → drop messages from non-subscribers first
# Show "chat is moving too fast" indicator to client
# Critical: MOD messages and system messages NEVER dropped
```

---

## 8. Google Docs — Real-Time Collaborative Editing

### 💡 Key Design: Operational Transformation (OT)

```
# Two users type simultaneously in the same paragraph:
# User A: Insert "Hello" at position 5
# User B: Delete character at position 3

# Problem: If both apply raw operations, document diverges!
# Solution: Operational Transformation
#   1. Server receives both operations
#   2. Transforms them against each other:
#      - A's insert at 5 → still 5 (B's delete at 3 doesn't affect position 5)
#      - B's delete at 3 → still 3 (A's insert at 5 doesn't affect position 3)
#   3. Server applies in canonical order
#   4. Broadcasts transformed operations to all clients
#   5. All clients converge to same document state

# Cursor positions also transformed:
# If User B inserts 5 characters before User A's cursor → shift A's cursor by +5
```

**WebSocket Message Types**:
```
→ { type: "edit", ops: [{ retain: 5 }, { insert: "Hello" }], revision: 42 }
← { type: "ack", revision: 43 }
← { type: "remote_edit", ops: [...], userId: "B", revision: 44 }
← { type: "cursor", userId: "B", position: 15, selectionEnd: 20 }
```

---

## 9. DoorDash — Live Order Tracking Map

### 💡 Key Design: State-Machine + Push

```
# Order lifecycle (each transition → WebSocket push to customer):
PLACED → CONFIRMED → PREPARING → DASHER_ASSIGNED → PICKED_UP → EN_ROUTE → DELIVERED

# Status change:
1. Restaurant confirms → Message Service → Kafka → Gateway → push to customer
2. Dasher picks up → Location starts streaming to customer

# Location streaming:
# Dasher GPS every 5s → server → WebSocket push to customer
# Customer sees dasher moving on map in real-time
# Auto-stops when order delivered

# Efficient: Only stream location during EN_ROUTE state
# Before PICKED_UP → no location needed
# After DELIVERED → disconnect location stream
```

---

## 10. Zoom — Meeting Signaling & Chat

### 💡 Key Design: WebSocket for Signaling, WebRTC for Media

```
# WebSocket handles:
# - Join/leave events ("Alice joined")
# - Chat messages during meeting
# - Reactions (👍, 👏, ❤️)
# - Hand raise / lower
# - Screen share start/stop signals
# - Mute/unmute status changes

# WebRTC handles (separate):
# - Audio streams (UDP)
# - Video streams (UDP)
# - Screen share stream (UDP)

# Why separate?
# WebSocket: reliable, ordered (TCP) — perfect for control messages
# WebRTC: unreliable but fast (UDP) — perfect for audio/video
# Losing one video frame is fine; losing a "participant joined" message is not
```

---

## 11. Twitter/X — Live Timeline & Notifications

### 💡 Key Design: SSE for Timeline, WebSocket for DMs

```
# Timeline updates (new tweets from followed users):
# Twitter uses Server-Sent Events (SSE), not WebSocket
# Why? Timeline is server→client only (one-directional)
# SSE advantages: auto-reconnect built-in, HTTP/2 multiplexing, simpler

# Direct Messages:
# WebSocket — bidirectional needed (send + receive + typing + read receipts)

# Live notifications:
# SSE or WebSocket push: new follower, like, retweet, mention
# Badge count updates in real-time
```

---

## 12. Notion — Real-Time Page Collaboration

```
# When two users edit same page:
# 1. User A makes edit → WebSocket → server
# 2. Server applies to document model (block-based CRDT)
# 3. Server broadcasts delta to User B via WebSocket
# 4. User B's client applies delta to local state

# Presence: "Alice is viewing this page" indicator
# → Heartbeat every 10s: { type: "presence", pageId: "abc", userId: "A" }
# → Server maintains active viewers set
# → Broadcasts viewer list changes to all page viewers
```

---

## 13. Coinbase — Live Crypto Price Feeds

```
# Public WebSocket API:
# Client subscribes to channels:
{
  "type": "subscribe",
  "channels": [
    { "name": "ticker", "product_ids": ["BTC-USD", "ETH-USD"] }
  ]
}

# Server pushes every trade:
{
  "type": "ticker",
  "product_id": "BTC-USD",
  "price": "67234.50",
  "volume_24h": "12345.67",
  "time": "2024-01-15T14:30:05.123Z"
}

# Scale: Millions of subscribers for BTC-USD alone
# Solution: CDN-level WebSocket (Cloudflare, Fastly)
# Edge servers cache and fan-out — origin only sends once per edge
```

---

## 14. Instagram — Live Video Comments

```
# During Instagram Live:
# - 500K viewers watching simultaneously
# - Comments streaming at 10K+/sec
# - Heart reactions floating up (ephemeral animations)

# Architecture:
# Viewer WebSocket → Edge Gateway (CDN) → Comment Router → Broadcaster

# Comment throttling:
# Not ALL comments shown — would flood the screen
# Server samples: show ~20 comments/sec (1 out of every 500 at 10K/sec)
# Priority: verified users, followed users, recent commenters shown first
# Hearts: aggregate count, push count every 500ms (not individual reactions)
```

---

## 15. Multiplayer Games — State Synchronization

```
# Real-time multiplayer (Fortnite, Among Us):
# Protocol: Custom binary over UDP (not WebSocket for action games)
# WebSocket used for: lobby, chat, matchmaking (reliable)
# UDP used for: player position, actions, world state (fast, lossy OK)

# Tick rate: 30-128 updates/sec (game state snapshots)
# Client-side prediction: client moves immediately, server validates
# Reconciliation: if server disagrees → client snaps to correct position

# Among Us (less demanding):
# WebSocket fine — turn-based/slow movement
# 10-15 players per game — low connection count
# Tick rate: ~10 updates/sec sufficient
```

---

## 16–25: Additional Real-World Applications (Summary)

### 16. Datadog — Real-Time Dashboard Updates
```
WebSocket per dashboard. Server pushes metric updates every 10s.
Subscription-based: only push metrics currently displayed on user's dashboard.
```

### 17. Stripe — Payment Status Webhooks + Dashboard
```
Dashboard: WebSocket shows live transaction feed.
Webhooks (server-to-server): HTTP POST, not WebSocket.
Different use case: WebSocket for human UI, Webhooks for machine integration.
```

### 18. Spotify — "Now Playing" Social Feature
```
SSE push when friend starts/changes song.
Low frequency (song changes every 3-5 min) → SSE sufficient.
No need for full WebSocket bidirectionality.
```

### 19. LinkedIn — Live Messaging & Notifications
```
WebSocket for DMs: typing indicators, read receipts.
SSE/polling for notifications (lower frequency, simpler).
```

### 20. Clubhouse/Twitter Spaces — Live Audio Rooms
```
WebSocket for room management: join, leave, raise hand, speaker queue.
WebRTC for actual audio streaming (P2P or via SFU server).
```

### 21. PagerDuty — Incident Alerts
```
WebSocket for dashboard: real-time incident status changes.
Push notifications for mobile: APNs/FCM (not WebSocket).
Webhook for integrations: Slack, Teams, email.
```

### 22. Grafana — Live Metric Streaming
```
Server-Sent Events for dashboard panels.
Each panel subscribes to its query. Server pushes new data points.
WebSocket alternative available for bidirectional plugin use cases.
```

### 23. Jira — Real-Time Board Updates
```
WebSocket push when another team member moves a card.
Board subscribes to sprint/board events.
Shows "Alice moved JIRA-123 to Done" toast notification.
```

### 24. WhatsApp Web — QR Code Login
```
QR code page opens WebSocket → waits for mobile app to scan.
Mobile scans → sends auth token to server → server pushes "authenticated" via WebSocket.
Page transitions to chat UI. Entire auth flow via WebSocket push.
```

### 25. Sports Scores — ESPN/LiveScore
```
WebSocket/SSE for live score updates during matches.
Score change → push to millions in <1 second.
Architecture: Event source → Kafka → Fan-out service → Edge WebSocket → Clients.
```

---

## 🏆 Cheat Sheet: Real-Time Technology Selection Guide

```
┌────────────────────────────────────────────────────────────────────────┐
│         REAL-TIME TECHNOLOGY SELECTION GUIDE                            │
├────────────────────────────────────────────────────────────────────────┤
│ Need                              → Technology                        │
│─────────────────────────────────────────────────────────────────────── │
│ Chat / messaging (bidirectional)  → WebSocket                         │
│ Live dashboard / price feed       → SSE (simpler) or WebSocket        │
│ Notifications (server→client)     → SSE or WebSocket                  │
│ Collaborative editing (cursors)   → WebSocket + CRDT/OT               │
│ Audio/video streaming             → WebRTC (UDP)                      │
│ Mobile push notifications         → APNs (iOS) / FCM (Android)       │
│ Server-to-server events           → Webhooks (HTTP POST)              │
│ IoT sensor streaming              → MQTT                              │
│ Gaming (fast action)              → Custom UDP protocol               │
│ Gaming (turn-based/casual)        → WebSocket                         │
│ One-time status check             → HTTP Polling or Long Polling      │
│                                                                        │
│ SCALING PATTERNS:                                                      │
│ Cross-server messaging            → Redis Pub/Sub or Kafka            │
│ Connection registry               → Redis (user → gateway mapping)    │
│ Fan-out to millions               → Hierarchical: Kafka → N gateways  │
│ Edge delivery (global)            → CDN WebSocket (Cloudflare)        │
│ Connection per server             → 100K-1M (depends on language)     │
│   C/Rust: ~1M | Erlang/Elixir: ~1M | Go: ~500K | Java: ~100K-300K   │
│   Node.js: ~100K-200K                                                 │
│                                                                        │
│ RELIABILITY:                                                           │
│ Offline messages                  → Queue in DB, deliver on reconnect │
│ Reconnection                      → Client exponential backoff + jitter│
│ Delivery guarantees               → Server ACK + client ACK           │
│ Ordering                          → Sequence numbers per conversation  │
│ Graceful degradation              → Fall back to long polling / SSE    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary: "How Would You Build Real-Time at Scale?"

> *"For real-time at scale, I'd use WebSocket gateways behind a load balancer, with each gateway handling 100K-500K connections. I'd use Redis Pub/Sub or Kafka for cross-gateway messaging — when User A sends a message, their gateway publishes to a topic, and the recipient's gateway (which could be a different server) receives and pushes via WebSocket. For presence, I'd use Redis with TTL-based heartbeats. For offline users, I'd queue messages and deliver on reconnect. For extreme fan-out (like live sports scores to millions), I'd use hierarchical fan-out: source → Kafka → N gateway servers → M clients each. For the client, I'd implement exponential backoff with jitter for reconnection, sequence numbers for ordering, and client-side ACKs for delivery confirmation. I'd fall back to SSE or long polling for browsers that don't support WebSocket."*

### Decision Matrix: When to Use What

| Scenario | Protocol | Why |
|----------|----------|-----|
| Chat app (WhatsApp, Slack) | **WebSocket** | Bidirectional, low latency, persistent |
| Live dashboard (Grafana) | **SSE** | Server→client only, simpler, auto-reconnect |
| Video call (Zoom) | **WebRTC** (+ WebSocket for signaling) | UDP for media, WebSocket for control |
| Server integrations | **Webhooks** (HTTP POST) | Simple, retry-friendly, no persistent connection |
| Mobile alerts | **APNs/FCM** | OS-level delivery, works when app closed |
| IoT devices | **MQTT** | Lightweight, designed for constrained devices |
| Action game | **Custom UDP** | Lowest latency, packet loss acceptable |
| Periodic updates | **HTTP Polling** | Simplest, no infrastructure needed |

---

> **Related Topics**: [WebSocket & Real-Time Deep Dive →](./37-websocket-and-real-time-at-scale.md) | [Kafka Deep Dive →](./39-kafka-deep-dive.md) | [Redis Real-World →](./40b-redis-real-world-applications.md) | [Communication Protocols →](./09-communication-protocols.md)
