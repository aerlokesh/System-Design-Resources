# Design Customer Support Chat - Hello Interview Framework

> **Question**: Design a real-time customer support chat system where customers can initiate chat sessions with support agents, get routed to the right agent based on skills/availability, exchange messages in real-time, and have conversations persisted with full history. Support chatbots for initial triage before human handoff.
>
> **Asked at**: Zendesk, Intercom, Salesforce, Amazon, Twilio
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
1. **Real-Time Messaging**: Customers and agents exchange text messages in real-time (< 500ms delivery latency) via WebSocket connections. Support rich media (images, files, links).
2. **Intelligent Routing**: Route incoming chat requests to the best available agent based on skills (billing, technical, returns), current load (concurrent chats), language, and priority (VIP customers).
3. **Queue Management**: When no agents are available, customers enter a queue with position updates. Estimated wait time displayed. Configurable queue limits.
4. **Conversation Persistence**: All messages stored permanently. Customers can view chat history. Agents see full customer conversation history when a chat is assigned to them.
5. **Bot Triage / Handoff**: A chatbot handles initial greeting, collects information (name, issue category, order number), attempts resolution. If unresolved, seamlessly hands off to a human agent with full bot conversation context.

#### Nice to Have (P1)
- Typing indicators ("Customer is typing...").
- Read receipts (message seen by agent/customer).
- Canned responses / macros for agents.
- Chat transfer between agents (with context).
- Customer satisfaction survey after chat ends.
- Agent performance analytics (avg response time, resolution rate).

#### Below the Line (Out of Scope)
- Voice/video calling.
- Email support integration.
- Knowledge base / FAQ system.
- Agent training and workforce management.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Message Delivery Latency** | < 500ms (p99) | Real-time feel for chat conversation |
| **Concurrent Chats** | 500K simultaneous active chats | Large enterprise support scale |
| **Messages per Second** | 50K messages/sec | 500K chats × ~6 messages/min average |
| **Availability** | 99.9% | Support chat is critical customer channel |
| **Message Durability** | Zero loss | Legal/compliance requires full conversation records |
| **Routing Latency** | < 3 seconds to assign agent | Customer should not wait long for initial response |
| **Connection Resilience** | Auto-reconnect with no message loss | Mobile users on unstable networks |
| **Agent Concurrency** | 3-8 simultaneous chats per agent | Industry standard for chat support |

### Capacity Estimation

```
Scale:
  Concurrent active chats: 500K
  Active customers waiting/chatting: 500K
  Active agents online: 100K (avg 5 chats each)
  Total agents: 500K (across shifts)

Messages:
  Messages per chat: ~20 (10 from each side)
  Average chat duration: 15 minutes
  New chats per hour: 500K × (60/15) = 2M chats/hour
  Messages per second: 500K × 6 msg/min / 60 = ~50K msg/sec
  Message size: ~500 bytes avg (text + metadata)
  Message throughput: 50K × 500 = ~25 MB/sec

Storage:
  Daily messages: 50K/sec × 86400 = ~4.3 billion messages/day
  Daily storage: 4.3B × 500 bytes = ~2.15 TB/day
  Annual: ~785 TB/year
  With indexes and metadata: ~1 PB/year

WebSocket Connections:
  Customers: 500K concurrent
  Agents: 100K concurrent
  Total: 600K WebSocket connections
  Each connection: ~10 KB memory → 600K × 10 KB = ~6 GB RAM for connections

Queue:
  Customers in queue at any time: ~50K (10% of active)
  Average wait time target: < 2 minutes
  Queue events per second: ~10K (position updates, assignments)
```

---

## 2️⃣ Core Entities

### Entity 1: Chat Session
```java
public class ChatSession {
    private final String sessionId;            // UUID
    private final String customerId;           // Customer who initiated
    private final String agentId;              // Assigned agent (null if in queue/bot)
    private final ChatStatus status;           // QUEUED, BOT_HANDLING, ACTIVE, TRANSFERRED, CLOSED
    private final String category;             // "billing", "technical", "returns"
    private final Priority priority;           // LOW, NORMAL, HIGH, VIP
    private final String language;             // "en", "es", "ja"
    private final Instant createdAt;
    private final Instant assignedAt;          // When agent was assigned
    private final Instant closedAt;
    private final String botSessionId;         // If bot handled initially
    private final Map<String, String> metadata; // Order ID, account tier, etc.
}

public enum ChatStatus {
    QUEUED,          // Waiting for agent assignment
    BOT_HANDLING,    // Chatbot is handling
    ACTIVE,          // Agent and customer chatting
    TRANSFERRED,     // Being transferred to another agent
    CLOSED           // Chat ended
}
```

### Entity 2: Message
```java
public class Message {
    private final String messageId;            // UUID
    private final String sessionId;            // Which chat session
    private final String senderId;             // Customer ID, Agent ID, or "BOT"
    private final SenderType senderType;       // CUSTOMER, AGENT, BOT, SYSTEM
    private final String content;              // Message text
    private final MessageType type;            // TEXT, IMAGE, FILE, SYSTEM_EVENT
    private final Instant sentAt;
    private final Instant deliveredAt;         // When delivered to recipient's device
    private final Instant readAt;              // When recipient read it
    private final Map<String, String> attachments; // URLs for images/files
}

public enum SenderType { CUSTOMER, AGENT, BOT, SYSTEM }
public enum MessageType { TEXT, IMAGE, FILE, SYSTEM_EVENT }
```

### Entity 3: Agent
```java
public class Agent {
    private final String agentId;
    private final String name;
    private final Set<String> skills;          // ["billing", "technical", "returns"]
    private final Set<String> languages;       // ["en", "es"]
    private final AgentStatus status;          // ONLINE, BUSY, AWAY, OFFLINE
    private final int maxConcurrentChats;      // 5-8 typically
    private final int currentChatCount;        // How many active chats right now
    private final Instant lastActivityAt;
}

public enum AgentStatus { ONLINE, BUSY, AWAY, OFFLINE }
```

### Entity 4: Queue Entry
```java
public class QueueEntry {
    private final String entryId;
    private final String sessionId;
    private final String customerId;
    private final String category;             // Routing category
    private final Priority priority;
    private final Instant enqueuedAt;
    private final int queuePosition;           // 1-based position
    private final Duration estimatedWaitTime;
}
```

---

## 3️⃣ API Design

### 1. Start Chat (Customer → System)
```
POST /api/v1/chats

Request:
{
  "customer_id": "cust_abc123",
  "category": "billing",
  "initial_message": "I need help with my last invoice",
  "language": "en",
  "metadata": { "order_id": "ORD-789", "account_tier": "premium" }
}

Response (201 Created):
{
  "session_id": "sess_xyz789",
  "status": "BOT_HANDLING",
  "websocket_url": "wss://chat.example.com/ws/sess_xyz789",
  "bot_greeting": "Hi! I'm here to help with your billing question. Can you tell me more about the issue with your invoice?"
}
```

### 2. WebSocket Messages (bidirectional)
```
→ Customer sends message:
{ "type": "MESSAGE", "content": "I was charged twice for order ORD-789", "message_type": "TEXT" }

← Server sends message (from agent/bot):
{ "type": "MESSAGE", "sender": "Agent Sarah", "sender_type": "AGENT", "content": "Let me look into that...", "message_id": "msg_123", "sent_at": "..." }

← Server sends typing indicator:
{ "type": "TYPING", "sender": "Agent Sarah", "is_typing": true }

← Server sends queue update:
{ "type": "QUEUE_UPDATE", "position": 3, "estimated_wait_seconds": 45 }

← Server sends agent assignment:
{ "type": "AGENT_ASSIGNED", "agent_name": "Sarah", "agent_id": "agt_456" }
```

### 3. Get Chat History (Customer/Agent)
```
GET /api/v1/chats/{session_id}/messages?limit=50&before=msg_123

Response (200 OK):
{
  "session_id": "sess_xyz789",
  "messages": [
    { "message_id": "msg_001", "sender_type": "BOT", "content": "Hi! How can I help?", "sent_at": "..." },
    { "message_id": "msg_002", "sender_type": "CUSTOMER", "content": "I was charged twice", "sent_at": "..." },
    { "message_id": "msg_003", "sender_type": "SYSTEM", "content": "Transferred to Agent Sarah", "sent_at": "..." },
    { "message_id": "msg_004", "sender_type": "AGENT", "content": "Let me look into that...", "sent_at": "..." }
  ],
  "has_more": false
}
```

### 4. Agent: Accept / Transfer / Close Chat
```
POST /api/v1/chats/{session_id}/close
Request: { "resolution": "RESOLVED", "notes": "Refund issued for duplicate charge", "tags": ["billing", "duplicate-charge"] }

POST /api/v1/chats/{session_id}/transfer
Request: { "target_agent_id": "agt_789", "reason": "Technical issue requires specialist" }
```

### 5. Agent Dashboard
```
GET /api/v1/agents/me/chats

Response (200 OK):
{
  "agent_id": "agt_456",
  "active_chats": [
    { "session_id": "sess_xyz789", "customer_name": "John", "category": "billing", "waiting_seconds": 30, "unread_count": 2 },
    { "session_id": "sess_abc123", "customer_name": "Jane", "category": "technical", "waiting_seconds": 0, "unread_count": 0 }
  ],
  "stats": { "chats_today": 42, "avg_response_time_seconds": 15, "csat_score": 4.7 }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Customer Starts Chat → Bot Triage → Agent Handoff
```
1. Customer clicks "Chat with us" on website/app
   ↓
2. Client calls POST /chats → creates ChatSession (status: BOT_HANDLING)
   ↓
3. Client establishes WebSocket connection to chat server
   ↓
4. BOT TRIAGE:
   a. Bot sends greeting + asks for issue details
   b. Customer describes problem
   c. Bot extracts: category (billing), intent (duplicate charge), order_id
   d. Bot attempts resolution (check FAQ, run simple actions)
   e. If resolved → close chat (no agent needed, ~40% deflection rate)
   f. If unresolved → hand off to human agent
   ↓
5. ROUTING:
   a. Chat enters routing queue with: category=billing, language=en, priority=NORMAL
   b. Routing Service finds best agent:
      - Has "billing" skill
      - Speaks English
      - Currently ONLINE
      - Has capacity (current_chats < max_concurrent)
      - Least loaded (fewest active chats)
   c. If agent found → assign immediately
   d. If no agent available → enter queue with position + estimated wait
   ↓
6. AGENT ASSIGNMENT:
   a. Agent receives notification: "New chat from John (billing - duplicate charge)"
   b. Agent sees full bot conversation context
   c. Agent's WebSocket receives chat assignment
   d. Customer's WebSocket receives: "Agent Sarah is now assisting you"
   ↓
7. LIVE CHAT:
   a. Customer and agent exchange messages via WebSocket
   b. All messages persisted to Message Store
   c. Typing indicators broadcast in real-time
   ↓
8. CLOSE:
   a. Agent resolves issue → closes chat
   b. System sends satisfaction survey to customer
   c. Chat transcript archived
```

### Flow 2: Message Delivery (Real-Time)
```
1. Customer types message in chat widget
   ↓
2. Client sends via WebSocket: { type: "MESSAGE", content: "..." }
   ↓
3. Chat Server receives message:
   a. Validate: is session active? is sender authorized?
   b. Assign message_id and timestamp
   c. Persist to Message Store (async, durable)
   d. Determine recipient(s): agent (or bot if BOT_HANDLING)
   ↓
4. If recipient is on SAME chat server → deliver directly via WebSocket
   If recipient is on DIFFERENT server → publish to Pub/Sub (Redis / Kafka)
   ↓
5. Recipient's chat server receives message → delivers via WebSocket
   ↓
6. Recipient's client sends delivery ACK → update message.deliveredAt
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     CUSTOMERS (Browser / Mobile App)                        │
│                     WebSocket connection to chat server                     │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │
┌───────────────────────────────┴────────────────────────────────────────────┐
│                     AGENTS (Agent Dashboard App)                            │
│                     WebSocket connection to chat server                     │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   WEBSOCKET GATEWAY / LOAD BALANCER                         │
│                   (Sticky sessions by session_id)                           │
│                   600K concurrent connections                               │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                        CHAT SERVERS (Stateful)                              │
│                        (50-100 pods, each handles ~10K connections)         │
│                                                                              │
│  Per-session state:                                                         │
│  • Customer WebSocket + Agent WebSocket                                    │
│  • Message routing between participants                                    │
│  • Typing indicator relay                                                  │
│  • Session state (ACTIVE, QUEUED, etc.)                                   │
│                                                                              │
│  Cross-server messaging: Redis Pub/Sub                                     │
│  (when customer and agent on different servers)                            │
└──────┬──────────────┬──────────────┬───────────────┬───────────────────────┘
       │              │              │               │
  ┌────▼────┐   ┌────▼────┐   ┌────▼─────┐   ┌────▼──────┐
  │ BOT      │   │ ROUTING  │   │ MESSAGE   │   │ QUEUE     │
  │ SERVICE  │   │ SERVICE  │   │ STORE     │   │ SERVICE   │
  │          │   │          │   │           │   │           │
  │ • NLP    │   │ • Skill  │   │ • Cassandra│  │ • Redis   │
  │ • Intent │   │   match  │   │ • All msgs │  │ • Priority│
  │ • FAQ    │   │ • Load   │   │ • History  │  │   queues  │
  │ • Handoff│   │   balance│   │ • Search   │  │ • Position│
  │          │   │ • Priority│  │           │   │ • Wait ETA│
  └──────────┘   └──────────┘   └───────────┘   └───────────┘

  ┌──────────────────────────────────────────────────────────┐
  │              REDIS PUB/SUB                                │
  │  • Cross-server message delivery                         │
  │  • Typing indicators                                     │
  │  • Agent status updates                                  │
  │  • Queue position broadcasts                             │
  └──────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────┐
  │              AGENT STATE SERVICE (Redis)                   │
  │  • Agent online/offline status                           │
  │  • Current chat count per agent                          │
  │  • Skills and languages per agent                        │
  │  • Last activity timestamp                               │
  └──────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **WebSocket Gateway** | Connection management, routing, TLS | Envoy / Nginx | 600K connections, horizontal |
| **Chat Servers** | Per-session message routing, presence | Go/Java (stateful) | 50-100 pods, ~10K connections each |
| **Bot Service** | NLP, intent extraction, FAQ resolution | Python + ML model | Stateless, auto-scaled |
| **Routing Service** | Match chats to best available agent | Java/Go | Stateless, reads agent state from Redis |
| **Queue Service** | Priority queue, position tracking, ETA | Redis Sorted Sets | Single cluster |
| **Message Store** | Persistent message storage, history | Cassandra / DynamoDB | Sharded by session_id |
| **Redis Pub/Sub** | Cross-server message delivery | Redis Cluster | Used for fan-out |
| **Agent State** | Track agent status, load, skills | Redis | Single cluster |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Intelligent Agent Routing — Matching Chats to Agents

**The Problem**: When a chat needs a human agent, we must quickly find the best match considering skills, language, load, and priority. The routing must be fair (no agent overloaded) and fast (< 3 seconds).

```java
public class AgentRouter {

    /**
     * Routing algorithm (priority-weighted scoring):
     * 
     * 1. Filter: agents who have the required skill AND language AND are ONLINE
     * 2. Filter: agents with available capacity (current_chats < max_concurrent)
     * 3. Score: rank remaining agents by composite score:
     *    score = w1 * skill_match + w2 * load_balance + w3 * language_preference + w4 * recency
     * 4. Assign to highest-scoring agent
     * 
     * If no agent available: enqueue with priority
     *   VIP customers → higher priority (served first)
     *   Long-waiting customers → priority boost over time
     */
    
    public AgentAssignment routeChat(ChatSession session) {
        String category = session.getCategory();
        String language = session.getLanguage();
        Priority priority = session.getPriority();
        
        // Step 1: Get candidate agents
        List<Agent> candidates = agentStateService.getOnlineAgents().stream()
            .filter(a -> a.getSkills().contains(category))
            .filter(a -> a.getLanguages().contains(language))
            .filter(a -> a.getCurrentChatCount() < a.getMaxConcurrentChats())
            .toList();
        
        if (candidates.isEmpty()) {
            // No available agent → enqueue
            return enqueueChat(session, priority);
        }
        
        // Step 2: Score and rank
        Agent bestAgent = candidates.stream()
            .max(Comparator.comparingDouble(a -> scoreAgent(a, session)))
            .orElseThrow();
        
        // Step 3: Assign
        agentStateService.incrementChatCount(bestAgent.getAgentId());
        return new AgentAssignment(bestAgent.getAgentId(), bestAgent.getName());
    }
    
    private double scoreAgent(Agent agent, ChatSession session) {
        // Lower load = higher score (prefer less busy agents)
        double loadScore = 1.0 - ((double) agent.getCurrentChatCount() / agent.getMaxConcurrentChats());
        
        // Recency: prefer agents who haven't received a chat recently (fairness)
        double recencyScore = Duration.between(agent.getLastActivityAt(), Instant.now()).getSeconds() / 300.0;
        
        return 0.5 * loadScore + 0.3 * recencyScore + 0.2; // base score
    }
    
    private AgentAssignment enqueueChat(ChatSession session, Priority priority) {
        // Redis ZADD with priority-based score
        // Score = priority_weight * 1000000 + enqueue_timestamp (lower = higher priority + earlier)
        double score = (4 - priority.ordinal()) * 1_000_000.0 + Instant.now().getEpochSecond();
        redis.zadd("queue:" + session.getCategory(), score, session.getSessionId());
        
        int position = redis.zrank("queue:" + session.getCategory(), session.getSessionId()).intValue() + 1;
        Duration estimatedWait = estimateWaitTime(session.getCategory(), position);
        
        return AgentAssignment.queued(position, estimatedWait);
    }
}
```

---

### Deep Dive 2: Cross-Server Message Delivery via Redis Pub/Sub

**The Problem**: Customer is connected to Chat Server A, agent is connected to Chat Server B. When the customer sends a message, it must reach the agent in < 500ms.

```java
public class CrossServerMessageRouter {

    /**
     * Within same server: direct delivery (customer and agent WebSockets in same process)
     * Cross-server: Redis Pub/Sub channel per session
     * 
     * When a session is created:
     *   - Customer's server subscribes to channel: "chat:{session_id}"
     *   - Agent's server subscribes to channel: "chat:{session_id}"
     * 
     * When a message is sent:
     *   1. Persist to Message Store (async)
     *   2. If both on same server → direct WebSocket delivery
     *   3. If on different servers → PUBLISH to "chat:{session_id}"
     *   4. Subscribing server receives → delivers via WebSocket
     */
    
    public void onMessageReceived(String sessionId, Message message) {
        // Step 1: Persist (async, fire-and-forget with retry)
        messageStore.saveAsync(message);
        
        // Step 2: Determine delivery path
        WebSocketConnection recipientWs = localConnections.get(sessionId, message.getRecipientId());
        
        if (recipientWs != null) {
            // Same server: direct delivery
            recipientWs.send(message);
        } else {
            // Different server: publish to Redis
            redis.publish("chat:" + sessionId, serialize(message));
        }
    }
    
    // Each server subscribes to channels for sessions it hosts
    public void onSessionAssigned(String sessionId) {
        redis.subscribe("chat:" + sessionId, msg -> {
            Message message = deserialize(msg);
            WebSocketConnection ws = localConnections.get(sessionId, message.getRecipientId());
            if (ws != null) {
                ws.send(message);
            }
        });
    }
}
```

**Latency breakdown**:
```
Same-server delivery:
  WebSocket receive → persist (async) → WebSocket send → client
  Total: < 10ms

Cross-server delivery:
  WebSocket receive → Redis PUBLISH (< 1ms) → Redis SUBSCRIBE callback (< 1ms) → WebSocket send
  Total: < 50ms

Both well within 500ms target.
```

---

### Deep Dive 3: Bot-to-Agent Handoff — Seamless Transition

**The Problem**: The chatbot handles the initial conversation but can't resolve the issue. It must seamlessly transfer to a human agent, passing all context so the customer doesn't have to repeat themselves.

```java
public class BotHandoffService {

    /**
     * Bot triage flow:
     * 1. Bot greets customer, asks for issue
     * 2. Bot extracts: intent, category, entities (order_id, product, etc.)
     * 3. Bot tries to resolve (FAQ lookup, simple actions)
     * 4. If confidence < threshold OR customer says "talk to a person" → HANDOFF
     * 
     * Handoff includes:
     * - Full conversation transcript (bot messages + customer messages)
     * - Extracted entities (order_id, category, intent)
     * - Customer sentiment (frustrated? calm?)
     * - Bot's assessment of the issue
     * 
     * Agent sees: "Bot Summary: Customer has a duplicate charge on order ORD-789.
     *              Bot attempted refund lookup but needs agent authorization."
     */
    
    public void handoffToAgent(String sessionId) {
        ChatSession session = chatStore.getSession(sessionId);
        
        // Step 1: Generate handoff context
        List<Message> botConversation = messageStore.getMessages(sessionId);
        BotContext context = botService.extractContext(botConversation);
        
        HandoffSummary summary = HandoffSummary.builder()
            .category(context.getCategory())
            .intent(context.getIntent())
            .extractedEntities(context.getEntities()) // {"order_id": "ORD-789"}
            .sentiment(context.getSentiment())          // "FRUSTRATED"
            .botSummary(context.generateSummary())      // Human-readable summary
            .conversationLength(botConversation.size())
            .build();
        
        // Step 2: Update session status
        session.setStatus(ChatStatus.QUEUED);
        session.setMetadata("handoff_summary", summary);
        
        // Step 3: Route to human agent
        AgentAssignment assignment = agentRouter.routeChat(session);
        
        // Step 4: Send system message
        messageStore.save(new Message(sessionId, "SYSTEM", SenderType.SYSTEM,
            "Connecting you with a support agent. Your conversation context has been shared.",
            MessageType.SYSTEM_EVENT));
        
        // Step 5: Agent receives chat with full context
        if (assignment.isAssigned()) {
            agentNotifier.notify(assignment.getAgentId(), session, summary);
        }
    }
}
```

---

### Deep Dive 4: Message Persistence — Cassandra Data Model

**The Problem**: Messages must be stored permanently, queried by session (pagination), and support high write throughput (50K messages/sec).

```sql
-- Cassandra table: messages partitioned by session_id, ordered by timestamp
CREATE TABLE messages (
    session_id    UUID,
    message_id    TIMEUUID,       -- Time-based UUID for natural ordering
    sender_id     TEXT,
    sender_type   TEXT,           -- CUSTOMER, AGENT, BOT, SYSTEM
    content       TEXT,
    message_type  TEXT,           -- TEXT, IMAGE, FILE, SYSTEM_EVENT
    attachments   MAP<TEXT, TEXT>,
    sent_at       TIMESTAMP,
    delivered_at  TIMESTAMP,
    read_at       TIMESTAMP,
    PRIMARY KEY (session_id, message_id)
) WITH CLUSTERING ORDER BY (message_id ASC);

-- Query: get latest 50 messages for a session (pagination)
-- SELECT * FROM messages WHERE session_id = ? ORDER BY message_id DESC LIMIT 50;

-- Query: get messages after a specific message (for sync after reconnect)
-- SELECT * FROM messages WHERE session_id = ? AND message_id > ? LIMIT 100;
```

```
Why Cassandra:
  - Partition by session_id → all messages for a chat on same node
  - Time-ordered clustering → efficient range queries (pagination)
  - High write throughput (50K writes/sec distributed across cluster)
  - Linear scalability (add nodes for more sessions)

Sizing:
  50K writes/sec × 500 bytes = 25 MB/sec → 5-node cluster handles this easily
  2.15 TB/day → with 3x replication = 6.45 TB/day → ~2.4 PB/year
  TTL: keep forever (compliance) or archive to cold storage after 2 years
```

---

### Deep Dive 5: Queue Management — Fair Scheduling with Priority

**The Problem**: When agents are busy, customers wait in queue. VIP customers should be prioritized, but non-VIP customers shouldn't starve. Queue position and estimated wait time must be accurate.

```java
public class QueueManager {

    /**
     * Queue structure: Redis Sorted Set per category
     * 
     * Key: "queue:billing", "queue:technical"
     * Score: priority_bucket × 10^9 + enqueue_timestamp_millis
     *   VIP:    score = 1 × 10^9 + timestamp  (lower = higher priority)
     *   HIGH:   score = 2 × 10^9 + timestamp
     *   NORMAL: score = 3 × 10^9 + timestamp
     *   LOW:    score = 4 × 10^9 + timestamp
     * 
     * ZRANGEBYSCORE returns lowest scores first → VIP first, then by arrival time
     * 
     * Anti-starvation: every 5 minutes, boost NORMAL priority customers
     * who have waited > 10 minutes to HIGH priority.
     */
    
    public QueueEntry enqueue(ChatSession session) {
        String queueKey = "queue:" + session.getCategory();
        double score = computeScore(session.getPriority(), Instant.now());
        
        redis.zadd(queueKey, score, session.getSessionId());
        
        long position = redis.zrank(queueKey, session.getSessionId()) + 1;
        Duration eta = estimateWait(session.getCategory(), position);
        
        // Notify customer of queue position
        sendQueueUpdate(session.getSessionId(), position, eta);
        
        return new QueueEntry(session.getSessionId(), position, eta);
    }
    
    // Called when an agent becomes available
    public Optional<String> dequeueNext(String category) {
        String queueKey = "queue:" + category;
        // ZPOPMIN: atomically remove and return the lowest-score entry (highest priority)
        Set<ZSetOperations.TypedTuple<String>> result = redis.zpopmin(queueKey, 1);
        if (result.isEmpty()) return Optional.empty();
        
        String sessionId = result.iterator().next().getValue();
        // Broadcast position updates to remaining queue members
        broadcastPositionUpdates(queueKey);
        return Optional.of(sessionId);
    }
    
    // Estimate wait time based on recent throughput
    public Duration estimateWait(String category, long position) {
        // avg_service_time × position / available_agents
        double avgServiceMinutes = metricsStore.getAvgChatDuration(category);
        int availableAgents = agentStateService.getAvailableAgentCount(category);
        if (availableAgents == 0) availableAgents = 1;
        double waitMinutes = avgServiceMinutes * position / availableAgents;
        return Duration.ofSeconds((long) (waitMinutes * 60));
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Messaging** | WebSocket (bidirectional) | Real-time < 500ms; supports typing indicators and presence |
| **Cross-server delivery** | Redis Pub/Sub per session | < 50ms delivery; simple; session-scoped channels avoid fan-out waste |
| **Message storage** | Cassandra (partitioned by session_id) | High write throughput; time-ordered; efficient pagination |
| **Agent routing** | Skill + load + priority scoring | Finds best agent fast; fair distribution; VIP priority with anti-starvation |
| **Queue** | Redis Sorted Set with priority-bucketed scores | O(log N) enqueue/dequeue; VIP-first with FIFO within same priority |
| **Bot triage** | Bot first, handoff with context | ~40% deflection; agent sees full conversation; no repeated info |
| **Connection management** | WebSocket Gateway → Chat Servers (stateful) | Separate connection concerns from business logic; session affinity |

## Interview Talking Points

1. **"WebSocket for real-time, Redis Pub/Sub for cross-server"** — Same-server delivery < 10ms. Cross-server via Redis PUBLISH/SUBSCRIBE on per-session channel < 50ms. Both well under 500ms target.
2. **"Skill-based routing with load balancing"** — Filter by skill + language → score by load (prefer less busy) + recency (fairness). Assign to highest-scoring agent. No agent gets overloaded.
3. **"Bot triage deflects 40% of chats"** — Bot handles greeting, intent extraction, FAQ resolution. Hands off to human with full context summary. Agent sees: extracted entities, sentiment, bot's assessment.
4. **"Redis Sorted Set for priority queue"** — Score = priority_bucket × 10^9 + timestamp. VIP always served before NORMAL. Within same priority: FIFO. Anti-starvation: boost priority after 10 min wait.
5. **"Cassandra for message persistence"** — Partitioned by session_id (all messages for one chat colocated). TIMEUUID for natural ordering. 50K writes/sec. Pagination via clustering key range.
6. **"Seamless handoff: bot → queue → agent"** — Single WebSocket connection throughout. Customer sees smooth transition. System messages mark each phase. Agent gets full context without customer repeating.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **WhatsApp / Slack** | Same real-time messaging | WhatsApp is peer-to-peer; support chat is customer-to-agent with routing |
| **Call Center IVR** | Same routing and queue concept | IVR is voice; chat allows concurrent agent handling |
| **Ticket System (Zendesk)** | Same customer support domain | Tickets are async; chat is real-time |
| **Notification System** | Same push delivery pattern | Notifications are one-way; chat is bidirectional |
| **Google Docs** | Same WebSocket infrastructure | Docs needs OT for conflict resolution; chat is append-only |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Real-time transport** | WebSocket | Server-Sent Events (SSE) | If only server→client needed (no client→server) |
| **Cross-server** | Redis Pub/Sub | Kafka | Higher throughput needs; message replay required |
| **Message store** | Cassandra | DynamoDB | Serverless; simpler ops; AWS-native |
| **Queue** | Redis Sorted Set | RabbitMQ / SQS | More complex routing; dead letter queues |
| **Bot** | Custom NLP + FAQ | Dialogflow / Lex / Rasa | Faster setup; managed NLP service |
| **Agent state** | Redis | PostgreSQL + cache | If complex agent scheduling needed |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 5 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Zendesk Chat Architecture
- Intercom Real-Time Messaging Infrastructure
- WebSocket at Scale (Slack Engineering Blog)
- Redis Pub/Sub for Real-Time Communication
- Cassandra Data Modeling for Chat Applications
- Chatbot Handoff Design Patterns
