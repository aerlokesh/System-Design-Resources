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

### 🏗️ Architecture Context

Twitch must handle **200K+ concurrent viewers** chatting in a single channel during popular streams, with message rates exceeding 100K/sec for major events. Not every viewer sees every message — intelligent sampling and sharding are critical.

### 📐 Architecture

```
┌──────────┐     WebSocket     ┌──────────────────┐
│ Viewer    │←────────────────→│  Chat Edge Server │ (10K connections each)
│ (Browser) │                   └────────┬─────────┘
└──────────┘                            │
                                   ┌────▼──────────────┐
                                   │  Chat Room Service │ (per-channel coordinator)
                                   └────┬──────────────┘
                                        │
                           ┌────────────▼────────────┐
                           │  Redis Pub/Sub + Kafka   │
                           │  (inter-server messaging)│
                           └────────────┬────────────┘
                                        │
                           ┌────────────▼────────────┐
                           │  Moderation Service      │ (AutoMod, banned words)
                           └─────────────────────────┘
```

### 💡 Key Design: Channel-Based Sharding

```
# Popular streamer: 200K viewers in chat → 200K WebSocket connections
# Solution: Shard viewers across multiple "chat edge servers"

# Channel: #ninja (200K viewers)
# → 20 chat edge servers, each handling ~10K connections
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

### 💡 Message Sampling for High-Volume Channels

```
# 10K messages/sec arriving → screen can only display ~20/sec usefully
# Server-side sampling:
#   1. All mod/system messages: ALWAYS delivered (never dropped)
#   2. Subscriber messages: sampled at higher rate (50%)
#   3. Non-subscriber messages: sampled at lower rate (5%)
#   4. Priority boost: messages from followed users, recent chatters

# Client receives ~100 messages/sec → renders top 20/sec on screen
# Remaining buffer for scroll-back and slow-mode display
```

### 🎯 Interview Script

> *"For Twitch-scale live chat with 200K viewers in one channel, I'd shard the audience across 20 chat edge servers — each handling 10K WebSocket connections. When a user sends a message, it goes to the Chat Room Service which validates, applies rate limits (1 msg per 1.5s tracked in Redis), runs AutoMod, then publishes to Redis Pub/Sub. All 20 edge servers receive it and broadcast locally. Critical optimization: at 10K messages/sec, not everyone sees everything. Server-side sampling gives priority to subscribers and mods, showing ~100 msgs/sec to each viewer. The client renders ~20/sec on screen. Mod and system messages are NEVER sampled out — they always deliver. Under extreme load, we show 'chat moving too fast' and increase sampling aggressiveness."*

### Scale Numbers

- **200K concurrent viewers** per top channel
- **10K-100K messages/sec** during events
- **20 edge servers** per large channel
- **~100ms end-to-end** for chat message delivery

---

## 8. Google Docs — Real-Time Collaborative Editing

### 🏗️ Architecture Context

Google Docs supports 50+ simultaneous editors on a single document with <200ms edit propagation. The server maintains canonical operation order using Operational Transformation, while clients apply edits optimistically for zero-perceived-latency typing.

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

### 🎯 Interview Script

> *"For Google Docs-style collaborative editing, I'd pin each document to a single session server via consistent hashing — this server is the authority for operation ordering. Clients type locally (instant feedback), then send operations via WebSocket with their base revision number. The server transforms incoming operations against any concurrent ops since that revision using OT, applies them in canonical order, and broadcasts the transformed result. All clients converge because the server enforces a total order. Cursor positions are also transformed — if Alice inserts 5 characters before Bob's cursor, Bob's cursor shifts right by 5. For durability, every operation is written to an append-only log before ACKing, with periodic document snapshots for fast recovery."*

---

## 9. DoorDash — Live Order Tracking Map

### 🏗️ Architecture Context

DoorDash pushes real-time order status and driver location to customers. The key insight: WebSocket is only needed during the **active delivery window** (30-60 minutes per order), not permanently.

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

### 🎯 Interview Script

> *"For order tracking, the customer opens the tracking page → WebSocket connects → subscribes to order:{order_id} events. The order follows a state machine: PLACED → CONFIRMED → PREPARING → EN_ROUTE → DELIVERED. Each state transition publishes to Kafka, the gateway receives and pushes to the customer's WebSocket. Location streaming is resource-efficient: I'd only stream GPS coordinates during the EN_ROUTE phase — before pickup, the customer doesn't need driver location. The Dasher app POSTs GPS every 5 seconds. The client interpolates between GPS points for smooth animation. On delivery completion, the WebSocket closes automatically. Since each order is a short-lived session (30-60 min), connection count stays manageable."*

---

## 10. Zoom — Meeting Signaling & Chat

### 🏗️ Architecture Context

Zoom uses WebSocket for **signaling and control messages** (reliable, ordered) and WebRTC for **media streams** (low-latency, UDP-based). This separation is fundamental to all video conferencing architecture.

### 💡 Key Design: WebSocket for Signaling, WebRTC for Media

```
# WebSocket handles (reliable TCP — every message must arrive):
# - Join/leave events ("Alice joined")
# - Chat messages during meeting
# - Reactions (👍, 👏, ❤️)
# - Hand raise / lower
# - Screen share start/stop signals
# - Mute/unmute status changes
# - Participant list updates

# WebRTC handles (UDP — fast, packet loss acceptable):
# - Audio streams
# - Video streams
# - Screen share stream

# Why separate?
# WebSocket: reliable, ordered (TCP) — perfect for control messages
# WebRTC: unreliable but fast (UDP) — perfect for audio/video
# Losing one video frame is fine; losing a "participant joined" message is not
```

### 🎯 Interview Script

> *"For video conferencing, I'd use two parallel protocols: WebSocket for signaling and control (join/leave, mute status, reactions, chat), and WebRTC for media streams (audio, video, screen share). WebSocket is TCP-based — every message arrives in order, which is critical for 'Alice joined' or chat messages. WebRTC is UDP-based — 50ms of dropped audio is imperceptible, but 50ms of delayed 'mute' notification is fine too, which is why signaling is separate. The WebSocket connection also handles SDP offer/answer exchange for WebRTC session setup. For a 100-person meeting: one WebSocket per participant (100 connections on the signaling server), media routed through an SFU (Selective Forwarding Unit) that relays streams without decoding."*

---

## 11. Twitter/X — Live Timeline & Notifications

### 🏗️ Architecture Context

Twitter uses **different protocols for different features** — demonstrating that one size doesn't fit all. Timeline uses SSE (unidirectional), DMs use WebSocket (bidirectional).

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

### 🎯 Interview Script

> *"Twitter demonstrates protocol-per-feature design. For the live timeline, they use Server-Sent Events (SSE) — it's one-directional (server pushes new tweets to client), simpler than WebSocket, has built-in auto-reconnect in the browser API, and works through all proxies. For Direct Messages, they use WebSocket — bidirectional is needed for sending messages, typing indicators, and read receipts. For notifications (likes, retweets, mentions), SSE is sufficient since it's server-push only. This shows you don't need WebSocket for everything — pick the simplest protocol that meets the requirement. SSE is HTTP-based, works with existing infrastructure, and multiplexes over HTTP/2."*

---

## 12. Notion — Real-Time Page Collaboration

### 🏗️ Architecture Context

Notion uses a **block-based CRDT** for collaborative editing. Unlike Google Docs (character-level OT), Notion treats each paragraph, heading, and list item as an independent block — concurrent edits to different blocks never conflict.

### 💡 Key Design: Block-Based CRDT

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

# Block granularity advantage:
# User A edits paragraph 1, User B edits paragraph 3 → NO CONFLICT
# Only same-block concurrent edits need CRDT merge
# 95%+ of concurrent edits are to different blocks → trivially parallel
```

### 🎯 Interview Script

> *"Notion's key insight is block-level granularity. Each paragraph, heading, list item, and embedded element is an independent block with its own ID. Concurrent edits to different blocks NEVER conflict — they're trivially parallel. Only edits to the SAME block need CRDT merge, and that's rare (<5% of concurrent edits). This dramatically simplifies the collaboration engine compared to character-level OT. The WebSocket carries block-level deltas: 'Block 7 content changed to X' or 'Block inserted after Block 5'. Presence shows who's viewing the page (heartbeat-based). For offline, the CRDT guarantees conflict-free merge on reconnection."*

---

## 13. Coinbase — Live Crypto Price Feeds

### 🏗️ Architecture Context

Coinbase's public WebSocket API serves **millions of subscribers** watching BTC-USD and other pairs. This is a classic **1-to-millions broadcast** pattern where edge delivery via CDN-level WebSocket is essential.

### 💡 Key Design: CDN-Level WebSocket Fan-Out

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
# Edge servers cache and fan-out — origin only sends once per edge PoP
# 200 edge PoPs × 1 message = 200 origin messages for millions of clients
```

### 🎯 Interview Script

> *"For live price feeds to millions of subscribers, I'd use CDN-level WebSocket (Cloudflare Workers or Fastly). The origin server publishes one price update per symbol. Each CDN edge PoP receives one copy and locally fans out to all subscribers at that edge. With 200 edge locations, the origin sends 200 messages to reach millions of clients — not millions of individual pushes. Clients subscribe by product_id. The edge maintains subscription sets and only forwards relevant symbols. This is the same pattern as live sports scores: hierarchical fan-out with topic-based filtering at the edge. Batch aggregation also helps — don't send every trade, aggregate over 100ms windows to reduce message volume by 10x."*

---

## 14. Instagram — Live Video Comments

### 🏗️ Architecture Context

During Instagram Live with 500K+ viewers, comments arrive at 10K+/sec but the screen can only display ~20/sec. **Intelligent sampling** with priority-based selection is the key design challenge.

### 💡 Key Design: Sampled Broadcast with Priority

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
# Priority: verified users > followed users > recent commenters > random
# Hearts: aggregate count, push count every 500ms (not individual reactions)

# Each viewer may see a DIFFERENT set of comments (personalized sampling)
# Verified/celeb comments are shown to EVERYONE (never sampled out)
```

### 🎯 Interview Script

> *"For Instagram Live with 500K viewers and 10K comments/sec, the screen can only show ~20 comments/sec — so server-side sampling is required. I'd implement priority-based sampling: verified users always shown, followed users at 50% rate, everyone else at 5%. Each viewer gets a personalized sample (their followed users boosted). Heart reactions aren't sent individually — aggregate the count and push once per 500ms: 'hearts: +342 in last 500ms'. The client renders the animation locally. Viewers connect to edge gateways (CDN-level WebSocket). The origin broadcaster sends one copy per edge, each edge fans out locally. This keeps origin load at O(num_edges) not O(num_viewers)."*

---

## 15. Multiplayer Games — State Synchronization

### 🏗️ Architecture Context

Multiplayer games face the hardest real-time challenge: **sub-50ms latency** with **30-128 state updates per second**. They use UDP (not WebSocket) for game state, with sophisticated client-side prediction and server reconciliation.

### 💡 Key Design: UDP + Client Prediction + Reconciliation

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

# Lag compensation:
# Server rewinds time to check "was that shot valid 100ms ago?"
# Client shows hit immediately; server confirms/denies 100ms later
```

### 🎯 Interview Script

> *"Multiplayer games are the extreme case of real-time. For fast-action games (Fortnite, FPS), WebSocket is too slow — TCP's head-of-line blocking adds latency. They use custom binary protocols over UDP. The server runs at 30-128 ticks/sec, sending world-state snapshots. Client-side prediction means the player moves IMMEDIATELY on input — the client predicts what the server will confirm. If the server disagrees (e.g., collision detected), the client 'snaps back'. Lag compensation means the server considers 'what did the world look like 100ms ago from this player's perspective?' for hit detection. WebSocket is still used for chat, lobby, matchmaking — things where reliability matters more than speed. For casual games (Among Us), WebSocket at 10 ticks/sec is sufficient."*

---

## 16–25: Additional Real-World Applications

### 16. Datadog — Real-Time Dashboard Updates

```
# Architecture: WebSocket per browser tab showing a dashboard
# Server pushes metric updates every 10s (configurable per panel)
# Subscription-based: only push metrics currently displayed on user's dashboard

# Key insight: Lazy subscription
# User opens dashboard with 20 panels → subscribe to 20 metric queries
# User scrolls down (panels go off-screen) → unsubscribe from hidden panels
# Reduces server-side computation by 60%+ for large dashboards

# Scale: 100K+ active dashboards with personalized queries
# Each dashboard = unique set of subscriptions
# NOT broadcast — each user sees different metrics
```

> *Interview: "For Datadog-style dashboards, each panel subscribes to its specific metric query via WebSocket. I'd implement viewport-aware subscriptions — panels off-screen unsubscribe to save server computation. Updates push every 10 seconds (configurable). This is NOT broadcast — each user's dashboard has unique queries, so it's N individual subscriptions, not fan-out."*

### 17. Stripe — Payment Status + Live Dashboard

```
# Two completely different real-time patterns:

# Pattern 1: Dashboard (internal) — WebSocket
# Ops team watches live transaction feed: { amount, merchant, status, time }
# WebSocket pushes every transaction as it processes
# Filters: by merchant, amount range, country, status

# Pattern 2: Webhooks (external) — HTTP POST
# Merchant's server receives: payment_intent.succeeded, charge.failed
# HTTP POST to merchant's configured URL
# Retry with exponential backoff on failure
# NOT WebSocket — merchants don't maintain persistent connections

# Key interview point:
# WebSocket for HUMAN interfaces (real-time dashboard)
# Webhooks for MACHINE interfaces (server-to-server integration)
```

> *Interview: "Stripe uses WebSocket for their internal ops dashboard — live transaction feed with filtering. But for merchant integrations, they use webhooks (HTTP POST) — merchants don't maintain persistent connections, and webhooks are retry-friendly, auditable, and work with any HTTP server. This demonstrates the principle: WebSocket for human UIs, webhooks for machine integrations."*

### 18. Spotify — "Now Playing" Social Feature

```
# "Friend Activity" sidebar shows what friends are listening to
# Update frequency: Every 3-5 minutes (when song changes)
# Protocol: SSE (Server-Sent Events), NOT WebSocket

# Why SSE over WebSocket?
# 1. One-directional only (server → client)
# 2. Extremely low frequency (1 update per 3-5 min per friend)
# 3. Built-in auto-reconnect (browser EventSource API)
# 4. Works through all proxies/firewalls
# 5. Less infrastructure complexity

# The "now playing" data:
# { userId, trackName, artistName, albumArt, timestamp }
# Pushed when user starts a new track (event-driven, not polling)
```

> *Interview: "Spotify's friend activity is a perfect SSE use case: unidirectional (server→client), low frequency (song changes every 3-5 min), and doesn't need bidirectionality. SSE gives auto-reconnect for free via the browser API, works through all proxies, and requires zero special infrastructure. This shows that NOT everything needs WebSocket — SSE is simpler and sufficient for server-push-only, low-frequency features."*

### 19. LinkedIn — Live Messaging & Notifications

```
# LinkedIn uses different protocols for different features:

# Messaging (DMs): WebSocket
# - Bidirectional: send messages, typing indicators, read receipts
# - Low latency needed for conversation flow
# - Persistent connection while in messaging view

# Notifications: SSE or long-polling
# - One-directional (server → client)
# - Lower frequency (new connections, endorsements, job alerts)
# - Doesn't justify full WebSocket infrastructure

# Feed updates: Pull-based (NOT real-time)
# - User refreshes feed → fetch new posts
# - NOT pushed in real-time (unlike Twitter)
# - Why? LinkedIn posts are less time-sensitive than tweets

# Connection state detection:
# Messaging WebSocket also serves as online/offline indicator
# "Active now" badge = has open WebSocket to messaging service
```

> *Interview: "LinkedIn demonstrates feature-appropriate protocol selection. Messaging uses WebSocket (bidirectional for send + typing + read receipts). Notifications use SSE (one-directional, lower frequency). Feed updates are pull-based — LinkedIn posts aren't time-critical like tweets, so real-time push isn't worth the infrastructure cost. The messaging WebSocket doubles as a presence signal: 'Active now' = open WebSocket."*

### 20. Clubhouse/Twitter Spaces — Live Audio Rooms

```
# Two-layer real-time architecture:

# Layer 1: Room Management (WebSocket)
# - Join/leave room events
# - Raise hand / lower hand
# - Speaker queue management
# - Room metadata (title, speakers, listeners count)
# - Mute/unmute status
# - Reactions (applause, etc.)

# Layer 2: Audio Streaming (WebRTC via SFU)
# - Speakers send audio to SFU (Selective Forwarding Unit)
# - SFU selectively forwards audio to listeners
# - NOT peer-to-peer (can't scale P2P to 1000+ listeners)
# - SFU can mix audio (combine speakers into one stream) or forward individually

# Why SFU over MCU?
# SFU: Forward streams without decoding → lower latency, less CPU
# MCU: Mix all audio into one stream → higher latency, more CPU
# Clubhouse chose SFU for lower latency

# Scale:
# 1000 listeners × 5 speakers → SFU forwards 5 audio streams to 1000 clients
# Each listener receives 5 separate audio streams, mixed on client side
```

> *Interview: "For live audio rooms, I'd use WebSocket for room signaling (join, leave, raise hand, speaker queue) and WebRTC via an SFU for audio. The SFU (Selective Forwarding Unit) receives audio from speakers and selectively forwards to listeners WITHOUT decoding — this gives lower latency than an MCU which mixes streams server-side. For 1000 listeners with 5 speakers: the SFU forwards 5 audio streams to each listener (not P2P which can't scale beyond ~10 peers). The listener's client mixes them locally. WebSocket keeps the room management reliable while WebRTC keeps audio latency under 200ms."*

### 21. PagerDuty — Incident Alerts (Multi-Channel Delivery)

```
# PagerDuty delivers alerts through MULTIPLE channels simultaneously:

# Channel 1: Dashboard (WebSocket)
# - Real-time incident status: triggered → acknowledged → resolved
# - Live updating incident timeline
# - Assignment changes visible instantly

# Channel 2: Mobile Push (APNs/FCM)
# - Wakes up on-call engineer's phone
# - Even when app is closed/backgrounded
# - Critical alerts override Do Not Disturb

# Channel 3: Phone Call (Twilio API)
# - Escalation: if not acknowledged in 5 min → phone call
# - Requires external telephony service

# Channel 4: Webhooks (HTTP POST)
# - Integration with Slack, Teams, Jira
# - Create Slack message, Jira ticket automatically

# Key design: PARALLEL delivery, not sequential
# All channels fire simultaneously
# First acknowledgment (from any channel) stops escalation
```

> *Interview: "PagerDuty demonstrates multi-channel real-time delivery. The dashboard uses WebSocket for live incident updates. Mobile alerts use APNs/FCM to wake the phone even when the app is closed. If unacknowledged for 5 minutes, a phone call (Twilio) fires as escalation. Slack/Jira integrations use webhooks. Critical design: all channels fire in PARALLEL — first acknowledgment from any channel stops the escalation timer. This is the 'defense in depth' approach to alerting."*

### 22. Grafana — Live Metric Streaming

```
# Grafana Live uses Server-Sent Events (SSE) for dashboard panels:

# Architecture:
# Panel renders → subscribes to query "avg(cpu) group by host"
# Grafana server evaluates query every 10s → pushes new data points via SSE
# Client appends to graph in real-time (smooth scrolling chart)

# Why SSE over WebSocket?
# 1. Dashboard viewing is read-only (server → client)
# 2. SSE simpler to implement and debug
# 3. Auto-reconnect on network blip
# 4. HTTP/2 multiplexing: 20 panels = 20 SSE streams over 1 HTTP/2 connection

# Streaming vs Polling mode:
# Streaming: Push new points as they arrive (SSE)
# Polling: Client fetches every N seconds (traditional)
# Streaming reduces server load: no repeated query execution for unchanged data
```

> *Interview: "Grafana uses SSE for live dashboard streaming — each panel subscribes to its metric query, and the server pushes new data points as they're computed. SSE is ideal here: unidirectional (server → client), auto-reconnects on network blips, and multiplexes over HTTP/2 (20 panels = 20 SSE streams over one connection). The key advantage over polling: the server only pushes when new data exists, eliminating repeated query execution for unchanged metrics. For editing dashboards (bidirectional), a separate WebSocket connection handles save/config operations."*

### 23. Jira — Real-Time Board Updates

```
# When Alice moves JIRA-123 from "In Progress" to "Done":

# Architecture:
# Alice's action → REST API → Jira Backend → Board Event → Pub/Sub
#                                                            │
# Bob (viewing same board) ← WebSocket ← Jira Gateway ← ───┘

# Events pushed in real-time:
# - Card moved between columns
# - Card created/deleted
# - Assignee changed
# - Sprint started/completed
# - Comment added

# Optimization: Board-scoped subscriptions
# Only push events for the board you're CURRENTLY viewing
# User switches boards → unsubscribe old, subscribe new
# Reduces event volume dramatically (most events are irrelevant to most users)

# Toast notifications:
# "Alice moved JIRA-123 to Done" → 3 second toast on Bob's screen
# Animation: card smoothly slides to new column
```

> *Interview: "For Jira-style board updates, I'd use WebSocket with board-scoped subscriptions. When a user views a sprint board, they subscribe to events for THAT board only. Card moves, new cards, assignee changes — all pushed via WebSocket. Other users viewing the same board see the card animate to its new column in real-time. When a user switches boards, they unsubscribe from the old and subscribe to the new — reducing irrelevant event traffic. This is O(viewers_of_this_board) not O(all_jira_users)."*

### 24. WhatsApp Web — QR Code Login (WebSocket-Based Auth Flow)

```
# The QR code login flow is entirely WebSocket-driven:

# Step 1: Browser opens web.whatsapp.com
#   → Generates a unique session token
#   → Opens WebSocket connection to WhatsApp server
#   → Displays QR code containing the session token

# Step 2: User scans QR code with phone
#   → Phone sends: { session_token, auth_credentials } to WhatsApp server

# Step 3: Server validates, links session to user account
#   → Server pushes via WebSocket to browser: { type: "authenticated", user: {...} }

# Step 4: Browser receives auth event
#   → Transitions from QR page to chat UI
#   → Same WebSocket connection now used for real-time messaging

# Why WebSocket for auth?
# The browser is WAITING for an event (phone scan) — no polling needed
# WebSocket push is instant: phone scans → browser transitions within 200ms
# The connection then seamlessly transitions to message delivery

# Timeout: If QR not scanned within 60s → regenerate QR (new session token)
```

> *Interview: "WhatsApp Web's QR login is a beautiful WebSocket use case. The browser opens a WebSocket, displays a QR code with a session token. When the phone scans and authenticates, the server pushes 'authenticated' via the WebSocket — the browser transitions instantly to the chat UI. The same WebSocket connection then continues for real-time messaging. No polling, no page refresh, instant transition. It's essentially 'wait for a server push event' — the ideal WebSocket pattern. Timeout after 60s regenerates the QR with a new token for security."*

### 25. Sports Scores — ESPN/LiveScore (Massive Broadcast)

```
# Architecture for pushing live scores to 10M+ concurrent viewers:

# Source: Official score feed (API from leagues/stadiums)
#   → Ingestion Service (normalize, validate)
#   → Kafka topic: "scores.{sport}.{league}"
#   → Fan-out Service (per-region)
#   → Edge WebSocket Gateways (CDN-level)
#   → Millions of clients

# Hierarchical fan-out:
# Origin → 12 regional hubs → 200 edge PoPs → 10M clients
# Origin sends 12 messages (one per region)
# Each region sends to its ~17 edge PoPs
# Each edge PoP fans out to its ~50K local subscribers
# Total origin messages: 12. Total client deliveries: 10M.

# Subscription filtering:
# User subscribes to: Premier League + NBA
# Edge only forwards relevant scores (not ALL sports)
# Reduces per-client push volume by 80-90%

# Latency target: <2 seconds from goal to notification
# Actual: ~500ms from official feed → user's device

# Scale during major events (World Cup final):
# 50M+ concurrent viewers watching one match
# All 50M get the same score update simultaneously
# CDN-level WebSocket handles this with hierarchical fan-out
```

> *Interview: "For live sports scores to 50M concurrent users, I'd use hierarchical fan-out through CDN-level WebSocket. The origin publishes to 12 regional hubs, each region forwards to its ~17 edge PoPs, and each PoP locally pushes to its 50K-200K subscribers. The origin sends 12 messages total; 50M clients receive the update. Subscription filtering at the edge means users only get scores for sports they follow — reducing per-client volume by 80-90%. Latency target is <2 seconds from goal to notification. During a World Cup final, ALL 50M viewers get the same update (broadcast pattern), which is exactly what hierarchical fan-out is designed for."*

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

## 🎯 Interview Deep Dive Scripts: "How Would You Build Real-Time at Scale?"

### Script 1: WebSocket Gateway Architecture (The Foundation)

> *"For real-time at scale, I'd deploy a fleet of WebSocket gateway servers behind a Layer 4 NLB with least-connections routing — NOT round-robin, because WebSocket connections are long-lived and uneven. Each gateway handles 100K-500K persistent connections using epoll-based non-blocking I/O (no thread-per-connection — that would require millions of threads). For 10M concurrent users, that's ~20-100 gateways depending on language (Go/Erlang can push 500K-1M per server, Java/Node closer to 100K-200K). Each connection is registered in Redis: `SET session:{user_id}:{device_id} {gateway_id} EX 86400`. This registry is how we know WHERE a user is connected."*

### Script 2: Cross-Gateway Message Delivery (The Hard Part)

> *"The tricky part is cross-gateway messaging. User A is on Gateway 7. User B is on Gateway 12. When A sends B a message, it must traverse two servers. The flow is: A's WebSocket → Gateway 7 → Message Service (persist to Cassandra, assign message_id and timestamp for ordering) → Redis Pub/Sub PUBLISH to Gateway 12's channel → Gateway 12 pushes via B's WebSocket → B sends client-side ACK → server marks as delivered. End-to-end: ~30-50ms. If B is offline (no session in Redis), we skip the push, store in Cassandra, and trigger FCM/APNs push notification. On reconnect, B does a catch-up query: 'Give me all messages since my last_seen_message_id'."*

### Script 3: Presence at Scale (Online/Offline/Last Seen)

> *"Presence uses heartbeat + TTL in Redis. Client sends a WebSocket ping every 30 seconds. The gateway refreshes a Redis key: `EXPIRE presence:{user_id} 70`. If two heartbeats are missed (70 seconds), the key expires and the user is 'offline'. The critical optimization: we broadcast presence changes ONLY on state transitions — online→offline or offline→online — not on every heartbeat. Without this, 10M users × 30s heartbeat = 333K Redis writes/sec just for presence. With transition-only broadcasting: maybe 50K transitions/sec during peak (much lower). For 'last seen', on disconnect we write: `SET last_seen:{user_id} {timestamp}`. Checking 500 contacts' presence is one `MGET` — under 2ms."*

### Script 4: Handling Millions of Subscribers (Fan-Out for Broadcasts)

> *"For broadcast scenarios like live sports scores or stock prices where one event goes to millions of subscribers, point-to-point through Redis Pub/Sub would overwhelm it. Instead I'd use hierarchical fan-out: the score service publishes to a Kafka topic. Each gateway server subscribes to that topic. Kafka delivers one copy per gateway (consumer group with N members). Each gateway then locally pushes to its 100K-500K connected clients who subscribed to that score. So 10M subscribers need only 20-100 Kafka messages (one per gateway), not 10M individual pushes. Each gateway handles its own local fan-out. The gateway groups clients by their subscriptions (team, stock ticker), so it only pushes relevant updates — reducing total push volume by 90%."*

### Script 5: Connection Lifecycle and Reconnection (Production Robustness)

> *"In production, connections drop constantly — mobile users switch between WiFi and cellular, traverse tunnels, or get backgrounded by the OS. My reconnection protocol: client uses exponential backoff with jitter (1s, 2s, 4s, 8s max + random 0-1s jitter) to avoid thundering herd when a gateway crashes. On reconnect, the client sends its last_received_sequence_number. The server replays any messages the client missed since that sequence. For ordering, each conversation has a monotonically increasing sequence counter — the client reorders messages locally if they arrive out of order. If the client detects 3+ missed heartbeat ACKs, it proactively closes and reconnects. The WebSocket handshake includes a session token (not re-authentication) for fast reconnect."*

### Script 6: Graceful Degradation Under Load (What to Shed)

> *"Under extreme load, we shed features in priority order — never message delivery. First: typing indicators (saves 30-40% of WebSocket traffic, purely cosmetic). Second: real-time presence (batch to every 30 seconds instead of instant, users can tolerate 'Alice is online' being 30s delayed). Third: read receipts (batch and send every 10 seconds). Fourth: reduce heartbeat frequency from 30s to 60s. NEVER shed: message delivery, authentication, heartbeat minimum. If WebSocket infrastructure is completely overwhelmed, clients automatically fall back to SSE (unidirectional, still push-based), and as a last resort to long-polling with 30-second hold. The client SDK handles this automatically via the connection state machine."*

### Script 7: Group Chat and Channel Scalability

> *"For group chat (Slack channels with 1000+ members), sending a message means pushing to potentially 1000 connected clients. I'd NOT fan-out through Redis Pub/Sub per user — instead, each channel gets its own Pub/Sub channel. Gateways subscribe to the channels their connected users belong to. When a message arrives in #general with 5000 members across 50 gateways, it's one PUBLISH that 50 gateways receive, then each gateway locally pushes to its connected members of that channel — maybe 100 users per gateway. The gateway maintains a local in-memory mapping: channel_id → list of local connection IDs. Joining/leaving a channel only updates the local gateway's in-memory set + Redis metadata. This makes group messages O(num_gateways) instead of O(num_members)."*

### Script 8: Real-Time Data Consistency (Message Ordering and Delivery Guarantees)

> *"For message ordering, I assign a server-side sequence number per conversation (not per user or globally). Clients render messages sorted by this sequence. For delivery guarantees, I use a three-state model: SENT (server received), DELIVERED (recipient's device received, client ACK'd), READ (user opened the conversation). The server keeps an outbox per user — messages stay in the outbox until the client ACKs. If the client disconnects before ACKing, messages are re-pushed on reconnect. At-least-once delivery is guaranteed; clients deduplicate by message_id. For critical systems like payments, I'd also persist a delivery_status per message in the database, updated via the ACK flow."*

---

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

### Common Interview Follow-Up Questions & Answers

```
Q: "What happens when a gateway server crashes with 500K connections?"
A: All 500K clients detect the disconnect (missed heartbeat ACK within 10s).
   They reconnect with exponential backoff + jitter → spread over 30 seconds.
   NLB routes them to OTHER gateways (dead gateway removed from target group).
   On reconnect: session re-registered, catch-up messages delivered.
   Impact: 10-30 second disruption for affected users. No message loss (persisted first).

Q: "How do you handle 50K users joining a live stream chat simultaneously?"
A: Pre-create the channel's Pub/Sub subscription on gateways before the stream starts.
   Rate-limit messages (1 per user per 5s). Sample for display (show 100 msgs/sec of 10K/sec).
   Viewers connect to gateways → auto-subscribed to the stream's channel.
   Each gateway handles its local viewers. 50K across 10 gateways = 5K per gateway (trivial).

Q: "How do you test real-time at 10M connections?"
A: Load testing with tools like Gatling or custom Go clients simulating WebSocket connections.
   Each load test machine can simulate ~50K connections.
   200 load test machines → 10M simulated connections.
   Test scenarios: Normal messaging, mass disconnect/reconnect, gateway failure.

Q: "What's the memory footprint per WebSocket connection?"
A: Kernel: ~4 KB for TCP socket buffer (tunable: net.ipv4.tcp_rmem/tcp_wmem).
   Application: ~2-10 KB for connection state (user_id, subscriptions, buffers).
   Total: ~8-16 KB per connection.
   500K connections × 16 KB = 8 GB RAM per gateway server.
   Use 32 GB RAM servers → plenty of headroom.

Q: "How do you prevent a thundering herd on reconnect?"
A: Client-side exponential backoff: 1s → 2s → 4s → 8s → 8s (capped).
   Plus random jitter: add random(0, current_backoff × 0.5).
   Server-side: Connection rate limiting per IP (100 new connections/sec per IP).
   Gateway: Gradual warm-up (accept connections in batches of 1000, pause 100ms between).
```

---

> **Related Topics**: [WebSocket & Real-Time Deep Dive →](./37-websocket-and-real-time-at-scale.md) | [Kafka Deep Dive →](./39-kafka-deep-dive.md) | [Redis Real-World →](./40b-redis-real-world-applications.md) | [Communication Protocols →](./09-communication-protocols.md)
