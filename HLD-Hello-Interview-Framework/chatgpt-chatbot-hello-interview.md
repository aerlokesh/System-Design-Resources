# Design ChatGPT (Conversational AI Chatbot) - Hello Interview Framework

> **Question**: Design a conversational AI system similar to ChatGPT that allows users to have natural language conversations and receive intelligent responses. Focus on a simple but production-ready version: session management, streaming responses, failure handling, rate limiting, and cost-aware capacity.
>
> **Asked at**: OpenAI, Microsoft
>
> **Difficulty**: Hard | **Level**: Senior/Staff

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
1. **Conversational Chat**: Users send natural language messages and receive AI-generated responses. Multi-turn conversations with context from previous messages.
2. **Streaming Responses**: Tokens streamed to the client as they're generated (word-by-word), not waiting for the full response. Reduces perceived latency from 10s+ to < 500ms for first token.
3. **Conversation/Session Management**: Conversations persist across sessions. Users can resume, view history, rename, and delete conversations.
4. **Context Window Management**: Each request includes prior conversation context (within model's token limit). Summarize or truncate older messages when context exceeds limit.
5. **Rate Limiting & Quotas**: Per-user rate limits (messages/min, tokens/day). Free tier vs paid tier quotas. Graceful throttling.
6. **Model Serving**: Route requests to GPU inference clusters. Handle variable latency (1-30 seconds depending on response length). Queue management for burst traffic.

#### Nice to Have (P1)
- Multi-model support (GPT-4, GPT-3.5, Claude — route by user preference/tier).
- System prompts / custom instructions (persistent user preferences).
- Regenerate response (re-run same prompt).
- Stop generation mid-stream (user cancels).
- File/image upload (multimodal input).
- Conversation sharing (share chat via public link).
- Plugins/tool use (web search, code execution, API calls).

#### Below the Line (Out of Scope)
- Training or fine-tuning the LLM model itself.
- Content moderation / safety filtering pipeline details.
- Payment and billing system.
- Mobile app implementation specifics.
- The LLM model architecture (transformer internals).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Time to First Token (TTFT)** | < 500ms P50, < 2s P99 | Streaming UX; user sees response starting immediately |
| **Tokens per Second (TPS)** | 30-80 tokens/sec per stream | Readable speed; model generation rate |
| **End-to-End Latency** | 2-30 seconds (depends on response length) | Acceptable for conversational AI |
| **Availability** | 99.9% | Users tolerate brief outages; not life-critical |
| **Throughput** | 10K concurrent inference requests | Peak load during business hours |
| **Context Limit** | 128K tokens (GPT-4 class) | Full conversation history within limit |
| **Storage** | Conversations persisted indefinitely | Users expect to revisit old chats |
| **Cost Efficiency** | Optimize GPU utilization > 80% | GPUs are $2-5/hr each; idle GPU = wasted money |

### Capacity Estimation

```
Users:
  Total users: 100M
  Daily active users: 10M
  Peak concurrent users: 1M
  
Conversations:
  Messages per user per day: 20 (10 user + 10 assistant)
  Total messages per day: 10M × 20 = 200M messages/day
  Messages per second: ~2,300 sustained, 10K peak
  
  Average message length: 100 tokens (user), 500 tokens (assistant)
  Average tokens per conversation turn: 600 tokens
  Total tokens per day: 200M × 300 avg = 60B tokens/day

Inference:
  Average generation time: 5 seconds (for 500 token response at 100 tok/sec)
  Concurrent inference requests: 2,300 msgs/sec × 5 sec avg = ~11,500 concurrent
  GPU requirement: ~1,500 A100 GPUs (each handles ~8 concurrent streams)
  
Storage:
  Message storage: 200M messages/day × 2 KB avg = 400 GB/day
  Conversation metadata: 10M active conversations × 1 KB = 10 GB
  Total: ~150 TB/year for messages

Cost:
  GPU cost: 1,500 A100s × $2/hr = $3,000/hr = $72,000/day = $26M/year
  → GPU utilization optimization is critical
```

---

## 2️⃣ Core Entities

### Entity 1: Conversation
```java
public class Conversation {
    private final String conversationId;    // UUID
    private final String userId;
    private final String title;             // Auto-generated from first message or user-renamed
    private final String modelId;           // "gpt-4", "gpt-3.5-turbo"
    private final String systemPrompt;      // Custom instructions (optional)
    private final Instant createdAt;
    private final Instant lastMessageAt;
    private final int messageCount;
    private final boolean archived;
}
```

### Entity 2: Message
```java
public class Message {
    private final String messageId;         // UUID
    private final String conversationId;
    private final MessageRole role;         // USER, ASSISTANT, SYSTEM
    private final String content;           // Text content
    private final int tokenCount;           // Tokens in this message
    private final String modelId;           // Model used for generation (assistant messages)
    private final MessageStatus status;     // COMPLETED, STREAMING, FAILED, CANCELLED
    private final long generationTimeMs;    // How long inference took (assistant only)
    private final Instant createdAt;
    private final String parentMessageId;   // For regeneration: which message this replaces
}

public enum MessageRole { SYSTEM, USER, ASSISTANT }
public enum MessageStatus { COMPLETED, STREAMING, FAILED, CANCELLED }
```

### Entity 3: User
```java
public class User {
    private final String userId;
    private final String email;
    private final UserTier tier;            // FREE, PLUS, TEAM, ENTERPRISE
    private final String customInstructions;// Persistent system prompt
    private final String preferredModel;    // Default model for new chats
    private final Instant createdAt;
    private final Instant lastActiveAt;
}

public enum UserTier { FREE, PLUS, TEAM, ENTERPRISE }
```

### Entity 4: Inference Request
```java
public class InferenceRequest {
    private final String requestId;         // UUID
    private final String conversationId;
    private final String userId;
    private final String modelId;
    private final List<Message> context;    // Conversation history (within token limit)
    private final InferenceParams params;   // Temperature, max_tokens, top_p, etc.
    private final Instant queuedAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final RequestStatus status;     // QUEUED, PROCESSING, STREAMING, COMPLETED, FAILED
}

public record InferenceParams(double temperature, int maxTokens, double topP, boolean stream) {}
```

### Entity 5: Usage / Quota
```java
public class UserUsage {
    private final String userId;
    private final String period;            // "2025-01-10" (daily) or "2025-01" (monthly)
    private final long messagesUsed;
    private final long tokensUsed;
    private final long tokensLimit;         // Based on tier
    private final long messagesLimit;
}
```

### Entity 6: Model Configuration
```java
public class ModelConfig {
    private final String modelId;           // "gpt-4", "gpt-3.5-turbo"
    private final int contextWindowTokens;  // 128K, 16K, 4K
    private final int maxOutputTokens;      // 4096, 16384
    private final double costPerInputToken; // $0.03 per 1K tokens
    private final double costPerOutputToken;// $0.06 per 1K tokens
    private final String servingCluster;    // Which GPU cluster serves this model
    private final boolean available;        // Is model currently available
}
```

---

## 3️⃣ API Design

### 1. Send Message (Streaming Response)
```
POST /api/v1/conversations/{conversation_id}/messages
Headers: Authorization: Bearer {token}

Request:
{
  "content": "Explain how distributed systems handle consensus",
  "model": "gpt-4",
  "stream": true,
  "params": { "temperature": 0.7, "max_tokens": 2048 }
}

Response (200 OK, Content-Type: text/event-stream):
data: {"type":"start","message_id":"msg_789","model":"gpt-4"}

data: {"type":"token","content":"Distributed"}
data: {"type":"token","content":" systems"}
data: {"type":"token","content":" achieve"}
data: {"type":"token","content":" consensus"}
data: {"type":"token","content":" through"}
...
data: {"type":"done","message_id":"msg_789","tokens_used":487,"generation_time_ms":4850}
```

> **Server-Sent Events (SSE)** for streaming. Each token sent as it's generated. Client renders incrementally.

### 2. Create Conversation
```
POST /api/v1/conversations

Request:
{ "model": "gpt-4", "system_prompt": "You are a helpful coding assistant" }

Response (201):
{ "conversation_id": "conv_123", "model": "gpt-4", "created_at": "2025-01-10T10:00:00Z" }
```

### 3. Get Conversation History
```
GET /api/v1/conversations/{conversation_id}/messages?limit=50&before={message_id}

Response (200):
{
  "messages": [
    { "message_id": "msg_001", "role": "user", "content": "Hello!", "created_at": "..." },
    { "message_id": "msg_002", "role": "assistant", "content": "Hi! How can I help?", "token_count": 8, "created_at": "..." }
  ],
  "has_more": true
}
```

### 4. List Conversations
```
GET /api/v1/conversations?limit=20&offset=0

Response (200):
{
  "conversations": [
    { "conversation_id": "conv_123", "title": "Distributed Systems Consensus", "last_message_at": "...", "message_count": 12 },
    { "conversation_id": "conv_456", "title": "Python Debugging Help", "last_message_at": "...", "message_count": 8 }
  ]
}
```

### 5. Stop Generation
```
POST /api/v1/conversations/{conversation_id}/messages/{message_id}/stop

Response (200):
{ "message_id": "msg_789", "status": "CANCELLED", "content": "Distributed systems achieve consensus through...", "tokens_generated": 45 }
```

### 6. Regenerate Response
```
POST /api/v1/conversations/{conversation_id}/messages/{message_id}/regenerate

Response: Same SSE stream as Send Message, but replaces the previous assistant response.
```

---

## 4️⃣ Data Flow

### Flow 1: Send Message → Streaming Response (Hot Path)
```
1. User types message, clicks Send
   ↓
2. Client: POST /conversations/{id}/messages (with SSE streaming)
   ↓
3. API Gateway:
   a. Authenticate user (JWT)
   b. Rate limit check (user tier quota)
   c. Route to Chat Service
   ↓
4. Chat Service:
   a. Store user message in Messages DB
   b. Build context window:
      - Load conversation history (last N messages)
      - Prepend system prompt (custom instructions)
      - Count tokens: if total > model context limit → truncate/summarize oldest messages
   c. Create InferenceRequest
   d. Submit to Inference Queue (or direct to GPU cluster if capacity available)
   ↓
5. Inference Service (GPU cluster):
   a. Dequeue request
   b. Load model context into GPU memory
   c. Begin autoregressive generation (one token at a time)
   d. Stream each token back to Chat Service via gRPC stream
   ↓
6. Chat Service:
   a. Receive tokens from Inference Service
   b. Forward each token to client via SSE
   c. Accumulate full response
   d. On completion: save assistant message to DB
   e. Update conversation metadata (last_message_at, message_count)
   f. Update user usage counters
   ↓
7. Client renders tokens as they arrive (typewriter effect)
   Total: TTFT < 500ms, full response in 2-30 seconds
```

### Flow 2: Context Window Management
```
1. User sends new message in conversation with 50 prior messages
   ↓
2. Chat Service builds context:
   a. Load all messages: system_prompt + 50 messages
   b. Count total tokens: system(200) + messages(25,000) + new_message(100) = 25,300
   c. Model context limit: 128,000 tokens → fits! Send all.
   ↓
3. If context EXCEEDS limit:
   a. Strategy 1: TRUNCATION (simple)
      - Keep system prompt + last N messages that fit within limit
      - Drop oldest messages
   b. Strategy 2: SUMMARIZATION (better UX)
      - Summarize older messages into a concise summary (separate LLM call)
      - Keep: system_prompt + summary + last 10 messages
      - Summary: "Previously discussed: distributed consensus, Raft algorithm, leader election..."
   c. Strategy 3: SLIDING WINDOW with importance scoring
      - Score each message by relevance to current query
      - Keep system + high-relevance messages + recent messages
   ↓
4. Context assembled → sent to Inference Service
```

### Flow 3: Rate Limiting & Quota Enforcement
```
1. User sends message
   ↓
2. Rate Limiter checks (Redis):
   a. Short-term: messages per minute (e.g., free: 5/min, plus: 30/min)
      → Token bucket: ratelimit:{user_id}:msg_min
   b. Daily tokens: tokens consumed today (e.g., free: 50K/day, plus: 500K/day)
      → Counter: usage:{user_id}:{date}:tokens
   c. Concurrent requests: max 1 active stream per user (free) or 3 (plus)
      → Set: active_streams:{user_id}
   ↓
3. If limit exceeded:
   → 429 Too Many Requests
   → Response includes: retry_after_seconds, usage info, upgrade prompt
   ↓
4. After response completes:
   → Increment token counter by actual tokens used
   → Remove from active_streams set
```

### Flow 4: Failure Handling & Retries
```
1. Inference Service fails mid-generation (GPU OOM, timeout, crash)
   ↓
2. Chat Service detects failure:
   a. gRPC stream breaks or timeout (30 seconds max generation time)
   b. Send SSE error event to client: { "type": "error", "code": "INFERENCE_FAILED" }
   ↓
3. Automatic retry (if retries < 2):
   a. Re-queue inference request to different GPU node
   b. If different model available: fallback (GPT-4 → GPT-3.5 with user consent)
   c. Resume streaming to same SSE connection
   ↓
4. If all retries fail:
   a. Mark message as FAILED in DB
   b. Client shows "Something went wrong" with retry button
   c. Log failure for monitoring (model, GPU node, error type)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐               │
│  │ Web App   │  │ iOS App   │  │ Android  │  │ API Clients  │               │
│  │ (React)   │  │           │  │ App      │  │ (SDK/REST)   │               │
│  └─────┬─────┘  └─────┬────┘  └─────┬─────┘  └──────┬───────┘               │
│        │ SSE Stream    │              │               │                       │
└────────┼───────────────┼──────────────┼───────────────┼──────────────────────┘
         └───────────────┼──────────────┼───────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       API GATEWAY / LOAD BALANCER                            │
│  • Auth (JWT/API key)     • Rate limiting (per-user, per-tier)              │
│  • SSE connection routing • Request validation                              │
│  • Geographic routing     • DDoS protection                                 │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CHAT SERVICE                                         │
│                         (Stateless, 50-100 pods)                             │
│                                                                               │
│  • Receive user messages    • Build context window (token counting)          │
│  • Store messages to DB     • Submit inference requests                      │
│  • Stream tokens to client  • Handle stop/regenerate                        │
│  • Manage conversations     • Update usage counters                         │
└──────────┬────────────────────────────┬──────────────────────────────────────┘
           │                            │
     ┌─────▼──────┐              ┌─────▼──────────────────┐
     │ MESSAGE    │              │ INFERENCE GATEWAY       │
     │ STORE      │              │ (Request Router/Queue)  │
     │            │              │                         │
     │ PostgreSQL │              │ • Route to correct GPU  │
     │ (sharded   │              │   cluster by model      │
     │  by user)  │              │ • Queue management      │
     │            │              │ • Load balancing         │
     │ • Messages │              │ • Retry on failure       │
     │ • Convos   │              │                         │
     │ • Users    │              │ Pods: 20-50             │
     │            │              └──────────┬──────────────┘
     │ ~150 TB/yr │                         │ gRPC stream
     └────────────┘                         ▼
                          ┌──────────────────────────────────────┐
                          │        GPU INFERENCE CLUSTER          │
                          │                                       │
                          │  ┌─────────┐ ┌─────────┐ ┌────────┐│
                          │  │ GPT-4   │ │ GPT-3.5 │ │ Other  ││
                          │  │ Cluster  │ │ Cluster  │ │ Models ││
                          │  │          │ │          │ │        ││
                          │  │ 800×A100 │ │ 500×A100 │ │ 200×   ││
                          │  │ 8 streams│ │ 16 strmse│ │ A100   ││
                          │  │ per GPU  │ │ per GPU  │ │        ││
                          │  └─────────┘ └─────────┘ └────────┘│
                          │                                       │
                          │  Total: ~1,500 A100 GPUs              │
                          │  Capacity: ~10K concurrent streams    │
                          └───────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        SUPPORTING SERVICES                                    │
│                                                                               │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐           │
│  │ RATE LIMITER     │  │ USAGE TRACKING   │  │ MODEL REGISTRY   │           │
│  │ (Redis)          │  │                  │  │                  │           │
│  │                  │  │ • Token counters  │  │ • Model configs  │           │
│  │ • Token bucket   │  │ • Daily/monthly   │  │ • Cluster routing│           │
│  │ • Per-user       │  │   usage per user  │  │ • Availability   │           │
│  │ • Per-tier       │  │ • Billing data    │  │ • Cost per token │           │
│  │ • Concurrent     │  │                  │  │                  │           │
│  │   streams        │  │ Redis + Postgres │  │ PostgreSQL       │           │
│  └─────────────────┘  └──────────────────┘  └──────────────────┘           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Chat Service** | Message handling, context building, SSE streaming | Go/Java on K8s | 50-100 pods (stateless) |
| **Inference Gateway** | Route requests to GPU clusters, queue management | Go on K8s | 20-50 pods |
| **GPU Inference Cluster** | LLM inference (token generation) | vLLM/TGI on A100 GPUs | ~1,500 GPUs |
| **Message Store** | Conversations, messages, users | PostgreSQL (sharded by user_id) | ~150 TB/year |
| **Rate Limiter** | Per-user token bucket, quota enforcement | Redis | Cluster, ~1 GB |
| **Usage Tracking** | Token/message counters, billing data | Redis (hot) + PostgreSQL (durable) | ~10 GB |
| **Model Registry** | Model configs, cluster routing, availability | PostgreSQL + cache | ~100 MB |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Streaming Architecture — Server-Sent Events (SSE)

**The Problem**: LLM inference takes 2-30 seconds for a full response. Without streaming, the user stares at a blank screen. With streaming (token-by-token), the first token appears in < 500ms and the response builds in real-time.

```
Streaming pipeline:

GPU (token generation) → gRPC stream → Chat Service → SSE → Client

1. GPU generates tokens one at a time (~30-80 tokens/sec)
2. Each token sent via gRPC bidirectional stream to Chat Service
3. Chat Service forwards each token to client via SSE (text/event-stream)
4. Client appends each token to the response bubble (typewriter effect)

SSE vs WebSocket for this use case:
  SSE (chosen): server→client only, simpler, auto-reconnect, HTTP-based
  WebSocket: bidirectional, but we only need server→client for streaming
  → SSE is simpler and sufficient

Connection lifecycle:
  1. Client sends POST /messages → server holds connection open
  2. Server streams SSE events as tokens arrive
  3. On completion: send "done" event, close connection
  4. On error: send "error" event, client can retry
  5. On stop: client sends /stop, server cancels inference, sends "stopped" event

Backpressure:
  - If client is slow to read SSE: TCP backpressure propagates to Chat Service
  - Chat Service buffers up to 1000 tokens; if full → slow down GPU request
  - In practice: SSE over HTTP/2 handles this well
```

---

### Deep Dive 2: Context Window Management — Fitting Conversation History

**The Problem**: GPT-4 has a 128K token context window. A long conversation (100+ messages) may exceed this. We must select the most relevant messages to include as context.

```
Context assembly algorithm:

1. Always include: system prompt (custom instructions)
2. Always include: last user message (current query)
3. Fill remaining space with conversation history (newest first)
4. If still over limit: apply strategy

Strategy A: TRUNCATION (simple, default)
  tokens_remaining = model_context_limit - system_tokens - new_message_tokens - max_output_tokens
  Include messages from newest to oldest until tokens_remaining exhausted
  Drop older messages

Strategy B: ROLLING SUMMARY (better for long conversations)
  When conversation exceeds 80% of context window:
    1. Take oldest 50% of messages
    2. Send to fast model (GPT-3.5): "Summarize this conversation so far in 500 tokens"
    3. Replace old messages with summary
    4. Context = system + summary + recent messages + new message
  
  Summary cached and updated every 20 messages (not on every request)

Strategy C: RETRIEVAL (for very long conversations / enterprise)
  1. Embed each message using embedding model
  2. On new query: embed query, find top-K most relevant past messages
  3. Context = system + relevant_messages + recent_5 + new message
  4. Uses vector DB (Pinecone, pgvector)

Token counting:
  - Use tiktoken library (OpenAI's tokenizer) for exact counts
  - Pre-count tokens on message save (store in message.token_count)
  - Assembly: sum pre-counted tokens, no re-tokenization needed
```

---

### Deep Dive 3: GPU Inference Cluster — Cost-Efficient Model Serving

**The Problem**: GPUs cost $2-5/hour each. With 1,500 A100s, we spend $72K/day on compute. GPU utilization must be > 80% to justify cost. But request patterns are bursty (peak during work hours, low at night).

```
Inference serving architecture:

Model server: vLLM or TGI (Text Generation Inference by HuggingFace)
  - Optimized for LLM serving (continuous batching, PagedAttention)
  - Handles multiple concurrent streams per GPU
  - GPT-4 (175B params): 8 A100s per model instance (tensor parallelism)
  - GPT-3.5 (7B params): 1 A100 per model instance
  
Continuous batching:
  - Traditional: one request per GPU at a time → GPU idle during memory-bound phases
  - vLLM: batch multiple requests, interleave prefill + decode phases
  - Result: 8 concurrent streams per GPU (GPT-4), 16 for smaller models
  
Autoscaling:
  - Scale metric: queue depth (pending inference requests)
  - If queue_depth > 100 for 2 minutes → scale up GPU nodes
  - If queue_depth < 10 for 10 minutes → scale down
  - Min instances: 50% of peak (for overnight low traffic)
  - Max instances: 120% of calculated peak (burst headroom)
  - Scaling latency: GPU node takes 2-5 minutes to boot → pre-warm pool
  
Cost optimization:
  - Spot/preemptible instances for non-urgent workloads (30-60% cheaper)
  - Model routing: route free-tier users to GPT-3.5 (cheaper, faster)
  - KV cache reuse: for regenerate requests, reuse cached prefill
  - Quantization: INT8/INT4 quantized models for lower tiers (50% less GPU)
```

---

### Deep Dive 4: Message Storage — Sharding for 150 TB/Year

**The Problem**: 200M messages/day, 150 TB/year. Users expect instant message history load. Conversations are always accessed by user_id (my conversations) or conversation_id (single conversation).

```
Storage schema (PostgreSQL, sharded by user_id):

CREATE TABLE conversations (
    conversation_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(200),
    model_id VARCHAR(50),
    system_prompt TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    last_message_at TIMESTAMP,
    message_count INT DEFAULT 0,
    archived BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_conv_user ON conversations (user_id, last_message_at DESC);

CREATE TABLE messages (
    message_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    role VARCHAR(10) NOT NULL,  -- 'user', 'assistant', 'system'
    content TEXT NOT NULL,
    token_count INT,
    model_id VARCHAR(50),
    status VARCHAR(20) DEFAULT 'COMPLETED',
    generation_time_ms INT,
    created_at TIMESTAMP DEFAULT NOW(),
    parent_message_id UUID  -- For regeneration
);
CREATE INDEX idx_msg_conv ON messages (conversation_id, created_at);

Sharding: hash(user_id) → shard
  - All conversations and messages for a user on same shard
  - Single-shard queries for "my conversations" and "messages in conversation"
  - 16 shards × ~10 TB each = 150 TB capacity

Caching:
  - Recent conversations: Redis cache per user (last 10 conversations)
  - Active conversation messages: Redis cache (current session)
  - Cache invalidation: on new message, update cache + DB
```

---

### Deep Dive 5: Rate Limiting — Multi-Tier Quota System

**The Problem**: Free users and paid users have different limits. Must handle: messages/minute (burst), tokens/day (budget), concurrent streams (fairness). Cannot let one user consume all GPU capacity.

```
Rate limiting layers:

Layer 1: Messages per minute (token bucket in Redis)
  Free: 5 messages/min, Plus: 30/min, Enterprise: 100/min
  Key: ratelimit:{user_id}:msg_min
  Algorithm: token bucket (refill 1 token per 12s for free)

Layer 2: Tokens per day (counter in Redis)
  Free: 50K tokens/day, Plus: 500K/day, Enterprise: 5M/day
  Key: usage:{user_id}:{date}:tokens
  Incremented AFTER generation completes (actual tokens used)
  Pre-check: estimate tokens (message length × 5) before sending to GPU

Layer 3: Concurrent streams (Redis SET)
  Free: 1 concurrent, Plus: 3, Enterprise: 10
  Key: active_streams:{user_id}
  SADD on request start, SREM on completion/failure/timeout
  TTL on each member: 60 seconds (auto-cleanup if crash)

Layer 4: Global capacity protection
  If GPU queue depth > threshold: reject new requests from free tier first
  Priority queue: Enterprise > Plus > Free
  Degradation: route to smaller/faster model if main model overloaded

  Response on limit:
  HTTP 429 Too Many Requests
  Headers: X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After
  Body: { "error": "rate_limit_exceeded", "retry_after": 12, "upgrade_url": "..." }
```

---

### Deep Dive 6: Stop Generation — Cancelling Mid-Stream

**The Problem**: User realizes the response is going in the wrong direction and clicks "Stop generating." We must cancel GPU inference immediately (save expensive GPU time), send partial response to client, and save what was generated.

```
Stop generation flow:

1. Client sends POST /messages/{id}/stop
2. Chat Service:
   a. Send cancellation signal to Inference Gateway (gRPC cancel)
   b. Inference Gateway forwards cancel to GPU worker
   c. GPU stops generation after current token (< 50ms)
   d. Chat Service receives final partial tokens
   e. Send SSE event: { "type": "stopped", "tokens_generated": 45 }
   f. Close SSE connection
   g. Save partial response to DB with status = CANCELLED
3. Tokens already generated are preserved (user can see partial response)
4. GPU resources freed immediately for next request

Implementation:
  - gRPC supports cancellation natively (context.cancel())
  - Inference server (vLLM) checks cancellation flag between token generations
  - Typical cancel latency: < 100ms from user click to GPU freed
```

---

### Deep Dive 7: Model Routing & Multi-Model Support

**The Problem**: Different models have different costs, speeds, and capabilities. Free users get GPT-3.5 (cheap, fast), paid users get GPT-4 (expensive, smarter). Must route to correct GPU cluster and handle model-specific configurations.

```
Model routing strategy:

1. User selects model (or default from tier):
   Free tier → gpt-3.5-turbo (forced)
   Plus tier → gpt-4 (default), gpt-3.5-turbo (option)
   Enterprise → all models available

2. Inference Gateway routes to model-specific cluster:
   model_id → ModelConfig → serving_cluster → GPU endpoint
   
   gpt-4 → GPU cluster A (800× A100, tensor parallel across 8 GPUs)
   gpt-3.5 → GPU cluster B (500× A100, 1 GPU per instance)
   claude → GPU cluster C (200× A100)

3. Fallback routing:
   If GPT-4 cluster at capacity (queue > 200):
     → Offer user: "GPT-4 is busy. Use GPT-3.5 for faster response?"
     → Enterprise: queue with priority, no fallback
     → Free: automatically downgrade to 3.5

4. A/B testing:
   Route 5% of traffic to new model version for quality evaluation
   Compare: TTFT, tokens/sec, user satisfaction (thumbs up/down)
```

---

### Deep Dive 8: Observability & Cost Monitoring

**The Problem**: With $26M/year in GPU costs and 200M messages/day, we need detailed observability to optimize costs, detect issues, and ensure quality.

```
Key metrics:

LATENCY:
  - TTFT (time to first token): P50, P99 — by model, by tier
  - TPS (tokens per second): per stream, per GPU
  - Total generation time: P50, P99

THROUGHPUT:
  - Requests per second: by model, by tier
  - Concurrent streams: current vs capacity
  - Queue depth: pending inference requests (scale trigger)

COST:
  - GPU utilization %: by cluster, by node (target > 80%)
  - Cost per token: input vs output, by model
  - Cost per user: by tier (ensure free tier < $0.05/user/day)
  - Idle GPU hours: wasted capacity (scaling optimization)

QUALITY:
  - User feedback: thumbs up/down ratio per model
  - Regeneration rate: how often users regenerate (dissatisfaction signal)
  - Error rate: failed inferences, timeouts
  - Stop rate: how often users cancel (bad response direction)

BUSINESS:
  - DAU, messages per user, retention
  - Conversion: free → plus rate
  - Token consumption by tier
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Streaming** | SSE (Server-Sent Events) | Simpler than WebSocket; server→client only; auto-reconnect; sufficient for token streaming |
| **Context management** | Truncation (default) + rolling summary (long convos) | Truncation is fast and simple; summary preserves context for power users |
| **Inference serving** | vLLM with continuous batching | 8× throughput vs naive serving; PagedAttention for memory efficiency; open-source |
| **Message storage** | PostgreSQL sharded by user_id | All user data co-located; simple queries; 16 shards for 150 TB/year |
| **Rate limiting** | 4-layer (msg/min + tokens/day + concurrent + global capacity) | Multiple granularities; prevents abuse at each level; graceful degradation |
| **GPU scaling** | Queue-depth autoscaling + pre-warm pool | React to demand; pre-warm avoids cold start (2-5 min); spot instances for cost |
| **Model routing** | Tier-based + fallback + A/B testing | Free → cheap model; priority queue for paid; automatic fallback on overload |
| **Failure handling** | Retry to different GPU + model fallback | Transparent retry; degrade gracefully (GPT-4 → 3.5); save partial on failure |
| **Token counting** | Pre-computed on save (tiktoken) | Avoid re-tokenization on every context build; O(1) lookup per message |
| **Cost optimization** | Continuous batching + quantization + spot instances | 80%+ GPU utilization; INT8 for lower tiers; spot saves 30-60% |

## Interview Talking Points

1. **"SSE streaming: GPU → gRPC → Chat Service → SSE → Client"** — Tokens streamed as generated. TTFT < 500ms. SSE chosen over WebSocket (simpler, server→client only). gRPC between internal services for efficiency.

2. **"Context window: truncation (default) + rolling summary (long conversations)"** — Token counts pre-computed on message save. Truncation fills newest-first up to model limit. Summary triggered at 80% capacity, cached and updated every 20 messages.

3. **"vLLM continuous batching: 8 concurrent streams per GPU"** — vs 1 stream with naive serving. PagedAttention for memory-efficient KV cache. 1,500 A100s handle 10K concurrent streams. Queue-depth autoscaling.

4. **"4-layer rate limiting: msg/min (burst) + tokens/day (budget) + concurrent (fairness) + global (capacity)"** — Redis token buckets per layer. Free/Plus/Enterprise tiers. Priority queue: paid users served first during overload.

5. **"GPU cost optimization: $26M/year → continuous batching + quantization + spot instances"** — Target 80%+ utilization. INT8 quantization for free tier (50% less GPU). Spot instances for non-real-time workloads. Pre-warm pool avoids cold start.

6. **"Stop generation: cancel GPU inference in < 100ms, save partial response"** — gRPC cancellation propagates to vLLM. GPU freed immediately. Partial tokens preserved in DB. User sees what was generated so far.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Chat System (WhatsApp)** | Same message storage/delivery | WhatsApp is P2P text; ChatGPT is user↔AI with expensive inference |
| **Search Engine** | Same query-response pattern | Search returns indexed results; ChatGPT generates novel responses |
| **Recommendation System** | Same ML serving pattern | Recs are fast (< 50ms); LLM inference is slow (seconds) |
| **Video Streaming (Netflix)** | Same streaming delivery | Video streams pre-encoded content; we stream real-time generated tokens |
| **API Rate Limiter** | Same rate limiting pattern | Our limiter is multi-dimensional (msgs + tokens + concurrent + capacity) |
| **Job Scheduler** | Same queue management | GPU inference queue similar to job queue with priority and capacity limits |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Inference server** | vLLM | TGI (HuggingFace) / NVIDIA Triton | TGI: simpler setup; Triton: multi-framework support (not just LLMs) |
| **Streaming** | SSE | WebSocket / gRPC-Web | WebSocket: if bidirectional needed; gRPC-Web: binary protocol, more efficient |
| **Message store** | PostgreSQL (sharded) | DynamoDB / Cassandra | DynamoDB: serverless, auto-scaling; Cassandra: write-heavy workloads |
| **Rate limiter** | Redis token bucket | Envoy rate limiting / custom in-memory | Envoy: L7 proxy integrated; in-memory: lowest latency but not distributed |
| **Context strategy** | Truncation + summary | RAG (retrieval-augmented) | RAG: for very long conversations or knowledge-augmented responses |
| **GPU orchestration** | K8s + custom autoscaler | SkyPilot / Ray Serve | SkyPilot: multi-cloud GPU optimization; Ray: built-in batching and scaling |
| **Token counting** | tiktoken (pre-computed) | SentencePiece / custom BPE | SentencePiece: for non-OpenAI models; custom BPE: model-specific tokenizer |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- OpenAI API Architecture & Streaming Design
- vLLM: Efficient LLM Serving with PagedAttention
- Server-Sent Events (SSE) vs WebSocket for Streaming
- GPU Inference Optimization (Continuous Batching, KV Cache)
- Token Counting with tiktoken
- Multi-Tier Rate Limiting Strategies
- LLM Context Window Management Strategies
- Cost Optimization for GPU Inference Clusters
