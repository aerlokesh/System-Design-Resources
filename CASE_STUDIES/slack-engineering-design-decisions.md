# Slack Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Slack's engineering blog, conference talks, and infrastructure posts. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Slack Engineering Blog (slack.engineering), Strange Loop / QCon talks, "Scaling Slack" talks

---

## Table of Contents

1. [Channel-Based Message Storage (MySQL + Vitess)](#1-mysql-vitess)
2. [Real-Time Messaging — WebSocket at Scale](#2-websocket-at-scale)
3. [Flannel — Edge Cache for Channel Data](#3-flannel-edge-cache)
4. [Channel Membership & Presence at Scale](#4-presence-at-scale)
5. [Search — From Solr to Custom Lucene](#5-search)
6. [Message Sending Pipeline — Async Fan-Out](#6-message-fanout)
7. [Shared Channels (Slack Connect)](#7-slack-connect)
8. [Job Queue — Async Task Processing](#8-job-queue)
9. [Migration from PHP to Hack to HHVM to… Back](#9-php-migration)
10. [Scaling to Enterprise — Large Workspace Challenges](#10-enterprise-scaling)
11. [File Storage & Media Processing](#11-file-storage)
12. [Notification Delivery Pipeline](#12-notification-pipeline)
13. [Data Model — How Slack Stores Messages](#13-data-model)
14. [Incident Management & Reliability](#14-incident-management)
15. [Caching Strategy — Multi-Layer](#15-caching-strategy)

---

## 1. MySQL + Vitess — Message Storage

**Blog Post**: "Scaling Datastores at Slack with Vitess" + "How Slack Moved to Vitess"

### Problem
Slack stores billions of messages. Each workspace's messages are isolated (tenant isolation). Originally sharded MySQL manually — but managing 10,000+ shards was operationally painful. Adding shards, rebalancing, and schema migrations were nightmares.

### What They Did
Migrated from manually-sharded MySQL to **Vitess** — a MySQL sharding middleware originally built at YouTube.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Database** | MySQL (via Vitess) | PostgreSQL, Cassandra, DynamoDB | Team's deep MySQL expertise; MySQL is battle-tested; relational model fits Slack's data |
| **Sharding** | Vitess (transparent sharding proxy) | Application-level sharding (manual) | Vitess handles shard routing, schema migrations, and resharding automatically |
| **Shard key** | workspace_id (tenant isolation) | channel_id, user_id | All data for one workspace on the same shard — no cross-shard queries for most operations |
| **Schema migrations** | Online schema change (via Vitess) | Manual ALTER TABLE per shard | Vitess applies schema changes one shard at a time, online, zero downtime |
| **Consistency** | Strong (MySQL ACID per shard) | Eventual consistency | Messages must be ordered and durable — ACID transactions within a workspace shard |

### Why MySQL over NoSQL?
Slack's data is inherently relational: messages belong to channels, channels belong to workspaces, users have memberships. MySQL's JOINs, transactions, and rich query support are valuable. Cassandra's denormalized model would require maintaining many materialized views.

### Tradeoff
- ✅ Strong consistency within a workspace (ACID)
- ✅ Vitess automates sharding, resharding, and schema migrations
- ✅ MySQL is well-understood, proven, great tooling
- ❌ Cross-workspace queries are expensive (cross-shard)
- ❌ Vitess adds a proxy layer (small latency overhead)
- ❌ MySQL has lower write throughput per shard than Cassandra

### Interview Use
> "For a multi-tenant system like Slack, I'd shard by workspace_id using Vitess on top of MySQL. This gives us tenant isolation (all data for one workspace on one shard), strong consistency (ACID), and Vitess handles the operational complexity of sharding — transparent routing, online schema changes, and automatic resharding. Slack migrated thousands of MySQL shards to Vitess."

---

## 2. WebSocket at Scale — Real-Time Messaging

**Blog Post**: "Scaling Slack's Real-Time Messaging" + various conference talks

### Problem
When a user sends a message in #general with 5,000 members, all 5,000 need to see it in real-time. Slack maintains millions of persistent WebSocket connections. Routing a message from the sender to all online channel members must happen in < 200ms.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Protocol** | WebSocket (persistent connection) | Long polling, SSE | Bidirectional: messages, typing indicators, presence updates, reactions all need push |
| **Gateway servers** | Dedicated WebSocket gateway tier | Integrated with app servers | Stateful connections need separate scaling from stateless app logic |
| **Connection registry** | Centralized (which user is on which gateway) | Gossip-based | Need to route messages to specific users → must know their gateway server |
| **Message routing** | Message service → lookup channel members → lookup their gateways → push | Broadcast to all gateways | Targeted delivery is more efficient than broadcast for large deployments |
| **Reconnection** | Client-side reconnect with exponential backoff + jitter | Server-side push retry | Clients are best positioned to detect and recover from disconnections |

### Architecture
```
User sends message → API Server → Message Service:
  1. Store in MySQL (Vitess)
  2. Lookup channel members
  3. For each online member: lookup their WebSocket gateway
  4. Push message to each gateway → gateway pushes to user's WebSocket
```

### Tradeoff
- ✅ Real-time: messages appear in < 200ms for all online members
- ✅ WebSocket enables typing indicators, presence, reactions — all instant
- ❌ WebSocket gateways are stateful (harder to scale, harder to deploy)
- ❌ Connection registry is a critical path — if it's slow, message delivery is slow
- ❌ Each gateway server handles limited connections (~65K); need many servers

### Interview Use
> "For real-time messaging, I'd use dedicated WebSocket gateway servers — separate from the stateless app servers. When a message is sent, the Message Service looks up all online channel members, finds their gateway servers, and pushes to each. Slack maintains millions of concurrent WebSocket connections across hundreds of gateway servers. The connection registry (user → gateway mapping) is the critical path."

---

## 3. Flannel — Edge Cache for Channel Data

**Blog Post**: "Flannel: Application-Level Edge Caching to Make Slack Scale"

### Problem
When a user opens a channel, the client needs: channel metadata, member list, latest messages, user presence, and notification preferences. Fetching all of this from multiple backend services adds latency. For large workspaces, a single channel open could trigger dozens of backend calls.

### What They Built
**Flannel** — an application-level edge cache that pre-computes and caches all the data a client needs when it connects. Instead of making N backend calls, the client makes one call to Flannel.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Aggregation** | Pre-compute "boot data" (everything a client needs on connect) | Client fetches each resource individually | One call to Flannel vs 10+ calls to different services — reduces client connect time by 50%+ |
| **Cache strategy** | Lazy-populate per workspace on first connection, keep warm | Pre-compute for all workspaces | Can't afford to pre-compute for millions of workspaces; lazy is cost-effective |
| **Invalidation** | Event-driven: backend services publish change events → Flannel updates cache | TTL-only | Sub-second freshness; TTL would mean stale data for common operations |
| **Scope** | Per-workspace cache (all data for one workspace in one cache entry) | Per-user cache | Most data is shared within a workspace (channels, members); per-user wastes memory |
| **Consistency** | Eventually consistent (brief staleness OK for sidebar data) | Strongly consistent | Sidebar channel list being 1-2 seconds stale is acceptable; messages are delivered separately via WebSocket |

### Tradeoff
- ✅ 50%+ reduction in client connection time (one call vs many)
- ✅ Reduces load on backend services (Flannel absorbs reads)
- ✅ Event-driven invalidation keeps data fresh
- ❌ Additional service to maintain (Flannel itself)
- ❌ Cache memory grows with workspace count
- ❌ Brief staleness possible (acceptable for sidebar data)

### Interview Use
> "To reduce client connection time, I'd add an application-level edge cache — like Slack's Flannel. Instead of the client making 10+ calls to different services on connect, it makes one call to Flannel which pre-aggregates all boot data (channels, members, preferences, unread counts). Flannel is invalidated via events from backend services. This reduced Slack's connection time by 50%+."

---

## 4. Presence at Scale

**Blog Post**: "Scaling Slack's Real-Time Presence System"

### Problem
Slack shows online/away/offline status for every user in a workspace. For a workspace with 100K users, this means tracking and broadcasting presence for 100K users to the relevant subset of members.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Status tracking** | Heartbeat-based: client sends heartbeat every 30s → server tracks TTL | Connection-based (online if WebSocket connected) | Heartbeat handles cases where connection is alive but user is idle (AFK) |
| **Active vs Away** | 5 min of no activity → "away" | Only online/offline | "Away" distinguishes "app is open but user is AFK" from "actively using Slack" |
| **Broadcasting** | Only to users who can see you (shared channel or DM) | Broadcast to entire workspace | Broadcasting to 100K users on every status change is too expensive |
| **Subscription model** | Client subscribes to presence of visible users (sidebar) | Subscribe to all workspace members | Client tells server which users it cares about; server only sends updates for those |
| **Batching** | Batch presence updates (send one update with N user statuses) | Individual updates | Reduces WebSocket message count; especially during "morning login storm" |

### The "Morning Login Storm" Problem
When a company starts work (9 AM), thousands of users come online within minutes. Each triggers a presence update → broadcasts to their contacts → millions of WebSocket messages in minutes.

**Solution**: 
- Batch presence updates: wait 2-3 seconds, collect all changes, send one batched update
- Prioritize: immediate for DM partners, delayed for large channels
- Rate-limit: don't send more than N presence updates per second per client

### Tradeoff
- ✅ Subscription-based: only send presence for users the client cares about
- ✅ Batching handles the morning login storm efficiently
- ❌ Subscription management adds complexity (track who's subscribed to whom)
- ❌ Brief staleness: batching delays presence updates by 2-3 seconds
- ❌ Large workspaces still have high presence traffic during login/logout storms

### Interview Use
> "For presence at scale, I'd use subscription-based broadcasting — the client tells the server which users are visible (sidebar), and the server only sends presence updates for those. This is critical for large workspaces where broadcasting to all 100K members on every status change is too expensive. Slack batches presence updates during the 'morning login storm' to reduce WebSocket traffic by 10×."

---

## 5. Search

**Blog Post**: "Scaling Slack's Search Infrastructure"

### Problem
Users search for messages, files, channels, and people across their entire workspace history. Must be fast (< 500ms), handle typos, respect permissions (users can only see messages from channels they're in), and return relevant results.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Engine** | Elasticsearch → custom Lucene-based | Pure Elasticsearch | ES didn't scale to Slack's multi-tenant requirements; custom solution optimized per-workspace indexing |
| **Index strategy** | Per-workspace index (tenant isolation) | Global index with filter | Per-workspace index prevents data leakage between workspaces; matches sharding model |
| **Access control** | At query time: filter results by user's channel membership | At index time: per-user index | Query-time filtering is simpler and stays consistent with membership changes |
| **Ranking** | Recency + relevance + personal signals (your channels, your DMs) | Pure text relevance | In workplace search, recent messages in your channels are more relevant than old messages in channels you don't use |
| **Indexing** | Near real-time: messages indexed within seconds via async worker | Batch (hourly re-index) | Users expect to find a message they just sent |

### Tradeoff
- ✅ Per-workspace indexing provides strong tenant isolation
- ✅ Near real-time: messages searchable within seconds
- ✅ Personalized ranking (your channels weighted higher)
- ❌ Per-workspace indexes mean many small indexes (operational overhead)
- ❌ Custom Lucene solution is expensive to maintain vs managed Elasticsearch
- ❌ Access control at query time adds latency (must check channel membership)

### Interview Use
> "For multi-tenant search, I'd use per-tenant indexes for strong isolation — like Slack's approach. Each workspace has its own search index. At query time, results are filtered by the user's channel membership. Ranking combines text relevance with recency and personal signals (your channels weighted higher). Messages are indexed within seconds via async workers."

---

## 6. Message Fan-Out Pipeline

**Blog Post**: "Scaling Slack's Message Sending Pipeline"

### Problem
When a user sends a message to a channel with N members, the system must: store the message, deliver it to all online members in real-time, update unread counts for all members, send push notifications to offline members, and index for search.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Synchronous (write to MySQL before ACK to sender) | Async write | Message must be durable before telling the sender "sent" |
| **Fan-out** | Asynchronous (via job queue after storage) | Synchronous fan-out | Don't make the sender wait for all N members to be notified |
| **Delivery** | Targeted: lookup online members → their gateways → push | Broadcast to all gateways | Targeted is more efficient for large deployments (hundreds of gateways) |
| **Notifications** | Async, batched: aggregate notifications + respect preferences | Individual, immediate | Prevents notification spam; respects per-channel mute settings |
| **Search indexing** | Async worker (seconds delay) | Synchronous | Search can tolerate 1-2 second lag; don't slow down message sending |
| **Unread tracking** | Pointer-based (last_read per user per channel) | Counter increment per member per message | Pointer is O(1) per message sent; counter would be O(N) for N members |

### Pipeline Flow
```
User sends message:
  1. [SYNC] Write to MySQL → ACK to sender (< 50ms)
  2. [ASYNC] Enqueue fan-out job
  3. [ASYNC] Fan-out worker:
     a. Fetch channel members (cached in Redis)
     b. For each online member: route to their WebSocket gateway
     c. For offline members with notifications enabled: enqueue push notification
  4. [ASYNC] Index in search engine
```

### Tradeoff
- ✅ Fast send (< 50ms for sender) — async fan-out doesn't block
- ✅ Pointer-based unread is O(1) per message (not O(N) for N members)
- ✅ Notifications respect per-channel preferences
- ❌ Brief delay before all members receive the message (~100-200ms)
- ❌ Job queue is a critical dependency — if queue backs up, delivery is delayed
- ❌ Push notification delivery depends on FCM/APNS reliability

### Interview Use
> "For message sending, I'd write synchronously to the database (durability first), then async fan-out via a job queue. The sender gets ACK in < 50ms; fan-out to channel members happens asynchronously. Unread tracking uses a pointer (last_read_message_id) per user per channel — O(1) per message instead of incrementing N counters. This is Slack's approach for scaling message delivery."

---

## 7. Slack Connect (Shared Channels)

**Blog Post**: "Building Slack Connect: The Technical Architecture of Shared Channels"

### Problem
Slack Connect lets channels span multiple workspaces (e.g., a vendor channel shared between Acme Corp and their client). Data must respect both workspaces' policies (retention, compliance, DLP). Messages must be delivered to users in different workspaces.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Data model** | Shared channel exists in both workspaces; messages replicated to both | Messages in one workspace, fetched cross-workspace | Each workspace must own its data for compliance/retention; if one workspace deletes, the other keeps theirs |
| **Replication** | Async replication of messages to each participating workspace's shard | Single source of truth | Each workspace's data policies (retention, DLP) apply independently to their copy |
| **Identity** | Users identified by (workspace_id, user_id) — not globally unique user | Global user identity | Respects workspace isolation; a user in two workspaces has separate identities per workspace |
| **Policies** | Intersection of policies: both workspaces' rules apply | One workspace's rules override | The more restrictive policy wins (e.g., if either workspace has DLP, messages are scanned) |

### Tradeoff
- ✅ Each workspace controls its own data (delete, retain, export)
- ✅ Compliance policies are independently enforced per workspace
- ❌ Message duplication (stored in each workspace's shard)
- ❌ Complex replication logic (N workspaces in a shared channel = N copies)
- ❌ Cross-workspace operations are more complex (member list, permissions)

### Interview Use
> "For cross-tenant shared resources (like Slack Connect), I'd replicate data to each tenant's shard rather than sharing a single source of truth. Each tenant applies their own data policies (retention, compliance) independently. The tradeoff is data duplication, but the benefit is full tenant data sovereignty. Slack replicates shared channel messages to every participating workspace."

---

## 8. Job Queue — Async Processing

**Blog Post**: Various Slack engineering talks

### Problem
Slack has many async tasks: message fan-out, push notifications, search indexing, file processing, link unfurling, bot/integration processing. Need a reliable, prioritized job queue.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Queue** | Redis-based job queue (custom) → later Kafka for high-volume | RabbitMQ, SQS | Redis was already in the stack; custom queue tuned for Slack's needs |
| **Priority** | Multiple priority lanes (real-time fan-out > link unfurl > analytics) | Single FIFO queue | Message delivery can't be blocked by lower-priority work like link preview generation |
| **Retry** | Exponential backoff + DLQ | No retry / infinite retry | Transient failures are common; DLQ prevents poison messages |
| **Idempotency** | At-least-once delivery; workers must be idempotent | Exactly-once | Redis/queue provides at-least-once; workers handle duplicate execution safely |
| **Monitoring** | Queue depth per priority + worker lag metrics + alerting | No monitoring | Queue backup = user-visible delay; must alert immediately |

### Tradeoff
- ✅ Priority lanes ensure message delivery isn't blocked by background work
- ✅ At-least-once + idempotent workers is simple and reliable
- ❌ Custom queue system requires ongoing maintenance
- ❌ Redis-based queue lacks the durability guarantees of Kafka

### Interview Use
> "For async processing, I'd use a priority job queue — high-priority lanes for message delivery and notifications, lower priority for search indexing and link unfurling. Slack uses Redis-based queues with priority lanes. Workers are idempotent (at-least-once delivery). The key metric is queue depth per priority lane — if it's growing, delivery is delayed."

---

## 9. PHP → Hack → HHVM (Language Evolution)

**Blog Post**: "Taking PHP Seriously" + "Migrating Slack's Desktop App to Multi-Workspace"

### Problem
Slack was built on PHP (like Facebook's early days). As they scaled, PHP's performance and type safety became limiting. But a full rewrite would halt feature development for years.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Kept PHP → adopted Hack (PHP superset with types) | Rewrite in Go, Java, Rust | Hack is compatible with existing PHP code; gradual migration, no big-bang rewrite |
| **Runtime** | HHVM (Facebook's PHP/Hack VM) → later standard PHP with JIT | Stock PHP | HHVM was faster; later PHP 8's JIT closed the gap |
| **Type system** | Gradually added Hack type annotations | Stay untyped | Type safety catches bugs at development time; critical as team and codebase grew |
| **Services** | Extract hot paths into Go/Java services | Everything in PHP | Performance-critical services (WebSocket gateway, message routing) use compiled languages |

### Tradeoff
- ✅ No risky rewrite — continuous feature delivery while improving the codebase
- ✅ Hack's type system caught bugs before production
- ✅ Performance-critical paths extracted to Go/Java
- ❌ PHP/Hack ecosystem is smaller than Java/Go
- ❌ HHVM dependency added complexity (later migrated back to PHP)
- ❌ Mixed-language codebase requires polyglot expertise

### Interview Use
> "Like Instagram (Python) and Slack (PHP), the lesson is: don't rewrite — optimize and evolve. Slack gradually adopted Hack (typed PHP) and extracted performance-critical services to Go/Java. This let them ship features continuously while improving the foundation. A full rewrite would have been a multi-year project with high risk."

---

## 10. Enterprise Scaling — Large Workspaces

**Blog Post**: "Scaling Slack for Our Largest Customers"

### Problem
Enterprise customers have workspaces with 100K+ users and 50K+ channels. Operations that work fine for a 50-person startup break at enterprise scale: channel list loading, presence broadcasting, search, and message fan-out.

### Challenges & Solutions

| Challenge | At 50 Users | At 100K Users | Solution |
|-----------|-------------|---------------|---------|
| **Channel list** | Load all channels (fast) | 50K channels = slow | Lazy load: show recent/starred first, load rest on scroll |
| **Presence** | Broadcast to all | 100K broadcasts per status change | Subscription-based: only send for visible users (sidebar) |
| **#general fan-out** | Fan out to 50 users (~5ms) | Fan out to 100K users (~seconds) | Switch to pull model for large channels (like Twitter's celebrity problem) |
| **Search** | Small index, fast | Millions of messages to search | Per-workspace index with caching for frequent queries |
| **Sidebar rendering** | Render all channels | 50K channels → slow render | Virtualized list: only render visible channels; load more on scroll |
| **Unread counts** | Track for all channels | 50K channels × 100K users = 5B states | Lazy computation: compute unread on demand, not on every message |

### Tradeoff
- ✅ Slack works for enterprises (100K+ users, critical revenue)
- ❌ Every feature must be designed for both small and large workspaces
- ❌ Large workspace optimizations (lazy load, subscription model) add complexity
- ❌ Some features work differently at enterprise scale (e.g., pull vs push for large channels)

### Interview Use
> "When designing for enterprise scale, I'd use tiered strategies: push fan-out for small channels (< 500 members), pull for large channels (> 10K). Lazy-load the channel list (show recent first, load rest on scroll). Subscription-based presence (only track visible users). These are the patterns Slack uses for 100K+ user workspaces."

---

## 11. File Storage & Media

### Problem
Users share files (images, documents, videos) in channels. Files must be stored durably, accessible by all channel members, and served fast globally.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | S3 (originally via file uploads, later direct to S3) | Own storage | S3 is virtually unlimited, 11 nines durability, no management needed |
| **Upload flow** | Client → presigned URL → direct upload to S3 | Client → Slack server → S3 | Direct upload avoids bottlenecking Slack servers with file bytes |
| **Thumbnails** | Async generation (Lambda/worker) after upload | Sync generation before response | User gets instant confirmation; thumbnail generated in background |
| **Access control** | Signed URLs with expiry (presigned GET URLs) | Auth check per file download | Signed URLs are faster (no auth call); expire after hours |
| **CDN** | CloudFront in front of S3 | Serve directly from S3 | CDN for global performance, reduced S3 egress costs |

### Interview Use
> "For file sharing, I'd use presigned upload URLs (client uploads directly to S3, bypassing the app server) and presigned download URLs with expiry (no per-request auth check). Async processing generates thumbnails and previews. CDN in front of S3 for global delivery. This is the standard pattern — Slack, Discord, and most messaging platforms use it."

---

## 12. Notification Delivery

### Problem
Slack sends billions of notifications: desktop, mobile push, email digests. Users have granular preferences: per-channel notification settings, do-not-disturb schedules, and keyword alerts.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Preferences** | Per-channel (ALL, MENTIONS, NONE) + per-keyword + DND schedule | Global on/off | Users want precise control (mute #random but not #incidents) |
| **Aggregation** | "Alice and 3 others" for multiple likes/reactions | Individual notification per action | Reduces notification fatigue; especially important for busy channels |
| **Cross-device dedup** | If read on desktop within 2-3 seconds → cancel mobile push | Always send to all devices | Prevents annoying duplicate buzz when user is already reading on desktop |
| **Priority** | DMs > Mentions > Channel messages > Marketing | Single priority | DMs are urgent; channel mentions are important; general channel messages can wait |
| **DND** | Suppress everything except "urgent" (override keyword) | Suppress all | Users need a way to reach someone in emergencies even during DND |

### Interview Use
> "For notifications, I'd implement per-channel preferences (ALL/MENTIONS/NONE) + DND schedules + cross-device dedup (cancel mobile push if read on desktop within 2-3 seconds). Priority: DMs > mentions > general messages. Aggregate similar notifications to prevent fatigue. This granular approach is essential for enterprise messaging — Slack users are in hundreds of channels."

---

## 13. Data Model — How Slack Stores Messages

### Schema Design

```
Messages (MySQL / Vitess):
  Shard key: workspace_id
  
  Table: messages
    - workspace_id
    - channel_id
    - message_ts (unique identifier — timestamp + counter)
    - user_id
    - text
    - type (message, bot_message, file_share, etc.)
    - parent_message_ts (for threads — null for top-level)
    - thread_ts (thread root timestamp)
    - reply_count
    - latest_reply_ts
    - edited_ts (null if not edited)
    - is_deleted
    
  Primary Key: (workspace_id, channel_id, message_ts)
  
  Unique ID: message_ts (timestamp-based — gives natural ordering)
```

### Key Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Message ID** | Timestamp-based (message_ts) | Natural ordering; no separate sequence generator; can extract time from ID |
| **Thread model** | parent_message_ts links reply to root | Simple — replies are just messages with a parent pointer |
| **Soft delete** | is_deleted flag (not actual DELETE) | Compliance/audit trail; can show "This message was deleted" |
| **Edit history** | Store latest + edited_ts flag | Full edit history too expensive; "edited" indicator is sufficient |
| **Shard key** | workspace_id | All data for one workspace on one shard — no cross-shard queries within a workspace |

### Interview Use
> "Slack uses timestamp-based message IDs (message_ts) — this gives natural chronological ordering without a separate ID generator. Threads are simply messages with a `parent_message_ts` pointer. All data is sharded by workspace_id via Vitess, so all queries for a workspace stay on one shard. Deletes are soft (is_deleted flag) for compliance audit trails."

---

## 14. Incident Management & Reliability

**Blog Post**: "All Hands on Deck: The Slack Incident Process"

### Problem
Slack is business-critical infrastructure — outages directly impact millions of workers' ability to communicate. Need rapid incident detection, response, and resolution.

### Design Decisions

| Decision | Chose | Why |
|----------|-------|-----|
| **Detection** | Automated monitoring + user reports + internal dogfooding | Manual detection only | Slack employees use Slack — they detect issues as fast as external users |
| **Incident channels** | Dedicated Slack channel per incident (created automatically) | War room / phone call | Written communication is searchable and async; multiple responders can contribute |
| **Severity levels** | SEV1 (total outage) → SEV5 (minor) | Single severity | Different response urgency and escalation paths |
| **Communication** | Public status page + Twitter + in-app banner | Private communication only | Transparency builds trust; users need to know Slack is aware of the issue |
| **Post-mortem** | Blameless post-mortem for every SEV1/SEV2 | Blame the person who caused it | Focus on systemic causes, not individuals; encourages reporting and transparency |

### Key Reliability Metrics
- **Uptime target**: 99.99% (< 53 minutes downtime/year)
- **Detection time**: < 2 minutes (automated alerting)
- **Acknowledgment**: < 5 minutes (on-call response)
- **Communication**: < 15 minutes (public status update)

### Interview Use
> "For reliability, I'd implement blameless post-mortems (focus on systemic fixes, not individual blame), automated incident detection (< 2 minutes), and a public status page for transparency. Slack creates a dedicated incident channel for each SEV1/2 — all communication happens there, making it searchable and async. This is critical for a communication platform where users have no alternative way to communicate during an outage."

---

## 15. Caching Strategy — Multi-Layer

### Problem
Slack's data is highly cacheable (channel metadata, user profiles, workspace settings change infrequently) but must be fresh (messages, unread counts change constantly).

### Design Decisions

| Layer | What's Cached | TTL | Invalidation |
|-------|-------------|-----|-------------|
| **Flannel (edge)** | Boot data (channels, members, preferences per workspace) | Warm (no TTL) | Event-driven (backend publishes change events) |
| **Application cache** | User profiles, channel metadata, workspace settings | 5-60 minutes | Event-driven + TTL safety net |
| **MySQL query cache** | Frequent queries (channel member lookups) | Short (seconds) | Automatic (MySQL invalidates on write) |
| **CDN** | Static assets, file previews, avatars | 1 year (content-addressable) | Versioned URLs (change URL when content changes) |

### Key Insight: Cache What Changes Slowly, Compute What Changes Fast
- **Cache**: Channel names, user profiles, workspace settings → change rarely → cache with event invalidation
- **Don't cache**: Unread counts → change on every message → compute on demand from last_read pointer
- **Cache briefly**: Channel member list → changes occasionally → cache with short TTL + event invalidation

### Interview Use
> "I'd use multi-layer caching: an edge cache (Flannel) for boot data with event-driven invalidation, application cache for metadata with short TTL + events, and CDN for static assets with content-addressable URLs. The key principle: cache what changes slowly (channel names, profiles), compute what changes fast (unread counts). Slack's Flannel reduced client connection time by 50%+ by pre-aggregating all boot data into a single cache entry."

---

## 🎯 Quick Reference: Slack's Key Decisions

### Data
| System | Choice | Why |
|--------|--------|-----|
| Messages | MySQL + Vitess (sharded by workspace_id) | ACID, tenant isolation, Vitess automates sharding |
| Message ID | Timestamp-based (message_ts) | Natural ordering, no separate ID service |
| Threads | parent_message_ts pointer | Simple — replies are just messages with a parent link |
| Search | Custom Lucene (per-workspace index) | Tenant isolation, personalized ranking |
| Files | S3 + presigned URLs + CDN | Direct upload, no server bottleneck |

### Real-Time
| System | Choice | Why |
|--------|--------|-----|
| Messaging | WebSocket (dedicated gateway tier) | Bidirectional, instant delivery |
| Presence | Subscription-based + batching | Only send for visible users; handle morning login storm |
| Connection registry | Centralized (user → gateway mapping) | Must route messages to correct gateway |
| Fan-out | Async via job queue (sync write + async delivery) | < 50ms ACK; delivery in background |

### Infrastructure
| System | Choice | Why |
|--------|--------|-----|
| Edge cache | Flannel (pre-aggregated boot data) | 50% faster client connection |
| Job queue | Redis-based with priority lanes | Message delivery > link unfurling > analytics |
| Language | PHP → Hack + Go/Java services | Gradual evolution, no risky rewrite |
| Notifications | Per-channel prefs + cross-device dedup + DND | Granular control for enterprise users |

### Enterprise
| Challenge | Solution |
|-----------|---------|
| 100K users presence | Subscription-based (visible users only) |
| 50K channels sidebar | Lazy load (recent first, scroll to load more) |
| Large channel fan-out | Pull model for > 10K members |
| Unread counts at scale | Pointer-based (last_read), compute on demand |
| Cross-workspace channels | Replicate to each workspace's shard |

---

## 🗣️ How to Use Slack Examples in Interviews

### Example Sentences
- "Slack uses Vitess on MySQL for transparent sharding — I'd take the same approach for multi-tenant data with workspace_id as shard key."
- "Like Slack's Flannel, I'd add an edge cache that pre-aggregates boot data — one call instead of 10+ service calls on client connect."
- "Slack uses subscription-based presence — the client subscribes only to visible users, not the entire workspace. Essential for 100K+ member workspaces."
- "For message sending, I'd follow Slack's pattern: sync write to DB → ACK to sender → async fan-out. The sender sees < 50ms latency."
- "Slack's unread tracking uses a last_read pointer per (user, channel) — O(1) per message instead of O(N) counter increments."
- "For large channels (>10K members), I'd switch from push to pull — like Slack's approach for enterprise #general channels."
- "Slack uses timestamp-based message IDs — natural ordering without a separate ID generator."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 15 design decisions across data, real-time, infrastructure, and enterprise scaling  
**Status**: Complete & Interview-Ready ✅
