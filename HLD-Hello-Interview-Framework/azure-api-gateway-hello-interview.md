# Design Azure API Gateway — Hello Interview Framework

> **Question**: Design a high-availability API Gateway (similar to Azure API Management) that handles routing, rate limiting, authentication, load balancing, and transformation for microservices at massive scale.
>
> **Asked at**: Microsoft, Amazon, Google, Netflix
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
1. **Request Routing**: Route incoming API requests to the correct backend microservice based on URL path, headers, query parameters. Support path-based, host-based, and header-based routing.
2. **Authentication & Authorization**: Validate API keys, OAuth 2.0 / JWT tokens, client certificates. Integrate with Azure AD for identity verification. Reject unauthorized requests before they reach backends.
3. **Rate Limiting & Throttling**: Enforce rate limits per API key, per user, per IP, per subscription tier. Return 429 Too Many Requests with retry-after headers.
4. **Load Balancing**: Distribute traffic across backend instances. Support round-robin, weighted, least-connections. Health checks and automatic removal of unhealthy backends.
5. **Request/Response Transformation**: Rewrite URLs, add/remove headers, transform payloads (XML ↔ JSON). API versioning (route v1 vs v2 to different backends).
6. **API Lifecycle Management**: Define APIs with OpenAPI/Swagger specs. Version APIs. Deprecate old versions. Developer portal for API discovery.
7. **Monitoring & Analytics**: Track request counts, latency, error rates per API. Real-time dashboards. Alert on anomalies.

#### Nice to Have (P1)
- Circuit breaker (stop sending to failing backends)
- Response caching (reduce backend load)
- Request validation (schema validation against OpenAPI)
- IP whitelisting / blacklisting
- Mutual TLS (mTLS) for backend communication
- GraphQL support
- WebSocket proxying
- API mocking for development
- Canary deployments (route % of traffic to new version)
- Custom policies (Lua/WASM plugins)

#### Below the Line (Out of Scope)
- Developer portal UI design
- Billing and monetization
- Full service mesh capabilities
- API marketplace

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Requests/sec** | 10 million RPS globally | Enterprise-grade gateway |
| **Latency overhead** | < 5ms p50, < 15ms p99 added latency | Gateway must be near-transparent |
| **Availability** | 99.999% (five nines) | Gateway down = all APIs down |
| **Throughput** | 100 Gbps aggregate | High-bandwidth APIs (file uploads, streaming) |
| **Config propagation** | < 5 seconds | Route changes must take effect quickly |
| **Scale** | 100K+ APIs, 1M+ developers | Multi-tenant platform |
| **Durability** | Zero request loss during failover | No dropped requests |

### Capacity Estimation

```
Traffic:
  Total APIs managed: 100,000
  Requests/sec (avg): 5M RPS
  Requests/sec (peak): 10M RPS
  Requests/day: ~430B
  Average request size: 2 KB
  Average response size: 5 KB

Bandwidth:
  Ingress: 10M × 2 KB = 20 GB/s
  Egress: 10M × 5 KB = 50 GB/s
  Total: 70 GB/s

Gateway Nodes:
  Per node capacity: ~50K RPS
  Nodes needed: 10M / 50K = 200 nodes
  With 2x headroom: 400 nodes globally

Rate Limit State:
  Active API keys: 10M
  Rate limit counters: 10M × 4 windows = 40M counters
  Memory: 40M × 100 bytes = 4 GB (fits in Redis cluster)

Configuration:
  Route rules: 100K APIs × 10 routes avg = 1M rules
  Policy definitions: ~500K policies
  Config size: ~500 MB total
  Propagation frequency: ~100 changes/minute
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  API             │────▶│  Route            │────▶│  Backend Pool      │
│                  │     │                   │     │                    │
│ apiId            │     │ routeId           │     │ poolId             │
│ name             │     │ apiId             │     │ backends[]         │
│ basePath         │     │ method (GET/POST) │     │ healthCheckPath    │
│ version          │     │ pathPattern       │     │ lbAlgorithm        │
│ protocols[]      │     │ backendPoolId     │     │ (roundRobin/       │
│ subscriptionReq  │     │ policies[]        │     │  weighted/least)   │
│ state (active/   │     │ cachePolicy       │     │ circuitBreaker     │
│  deprecated)     │     │ timeout           │     │                    │
└─────────────────┘     └──────────────────┘     └───────────────────┘

┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  Subscription    │     │  Policy           │     │  Backend           │
│                  │     │                   │     │                    │
│ subscriptionId   │     │ policyId          │     │ backendId          │
│ apiId            │     │ type (rateLimit/  │     │ url                │
│ developerId      │     │  auth/transform/  │     │ weight             │
│ apiKey           │     │  cache/validate)  │     │ healthy            │
│ tier (free/std/  │     │ scope (inbound/   │     │ lastHealthCheck    │
│  premium)        │     │  outbound/error)  │     │ region             │
│ quotaLimit       │     │ config {}         │     │ maxConnections     │
│ rateLimit        │     │ order             │     └───────────────────┘
│ state            │     └──────────────────┘
└─────────────────┘
                         ┌──────────────────┐
                         │  RateLimitRule    │
                         │                   │
                         │ ruleId            │
                         │ key (apiKey/IP/   │
                         │  userId)          │
                         │ windowSec         │
                         │ maxRequests       │
                         │ tier              │
                         └──────────────────┘
```

---

## 3️⃣ API Design

### Management Plane — Define an API
```
POST /management/apis
Authorization: Bearer <admin_token>

Request:
{
  "name": "User Service API",
  "basePath": "/api/users",
  "version": "v2",
  "protocols": ["https"],
  "routes": [
    {
      "method": "GET",
      "pathPattern": "/api/users/{userId}",
      "backendPool": "user-service-pool",
      "policies": {
        "inbound": ["validate-jwt", "rate-limit-by-key"],
        "outbound": ["set-header-cors"],
        "on-error": ["return-error-details"]
      },
      "timeout": 5000
    }
  ],
  "backendPools": [
    {
      "name": "user-service-pool",
      "backends": [
        { "url": "https://user-svc-1.internal:8080", "weight": 70 },
        { "url": "https://user-svc-2.internal:8080", "weight": 30 }
      ],
      "healthCheck": { "path": "/health", "intervalSec": 10 },
      "loadBalancing": "weighted"
    }
  ]
}
```

### Data Plane — Proxied Request
```
GET https://api.contoso.com/api/users/12345
Ocp-Apim-Subscription-Key: abc123def456

→ Gateway processes:
  1. TLS termination
  2. Parse route: /api/users/{userId} → user-service-pool
  3. Inbound policies:
     a. validate-jwt → check Authorization header
     b. rate-limit-by-key → check abc123def456 quota
  4. Load balance → select user-svc-1.internal:8080
  5. Forward: GET https://user-svc-1.internal:8080/api/users/12345
  6. Outbound policies:
     a. set-header-cors → add CORS headers
  7. Return response to client

Response: 200 OK
X-Request-Id: req_abc123
X-RateLimit-Remaining: 97
X-RateLimit-Reset: 1705300060
```

---

## 4️⃣ Data Flow

### Request Processing Pipeline

```
Client Request
       │
       ▼
┌──────────────────────────────────────────────────────────┐
│                    GATEWAY NODE                           │
│                                                          │
│  1. TLS Termination (OpenSSL / BoringSSL)               │
│       │                                                  │
│  2. Route Matching (Trie / Radix tree in memory)        │
│       │                                                  │
│  3. INBOUND POLICY PIPELINE                             │
│       ├── Authentication (JWT validate / API key lookup) │
│       ├── Rate Limiting (check Redis counter)           │
│       ├── Request Validation (schema check)             │
│       ├── IP Filtering (allowlist/blocklist)            │
│       └── Request Transform (rewrite URL, add headers)  │
│       │                                                  │
│  4. Load Balancing (select backend from pool)           │
│       │                                                  │
│  5. Backend Call (HTTP/gRPC with timeout + retry)       │
│       │                                                  │
│  6. OUTBOUND POLICY PIPELINE                            │
│       ├── Response Transform (mask fields, add headers) │
│       ├── Caching (store in local cache / Redis)        │
│       └── CORS headers                                  │
│       │                                                  │
│  7. Logging & Metrics (async — fire and forget)         │
│       │                                                  │
│  8. Return Response to Client                           │
└──────────────────────────────────────────────────────────┘
       │
       ▼ (async)
  Analytics Pipeline → Kafka → Metrics Aggregator → Dashboard
```

---

## 5️⃣ High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                     GLOBAL EDGE LAYER                                │
│                                                                      │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐   │
│  │ Edge PoP   │  │ Edge PoP   │  │ Edge PoP   │  │ Edge PoP   │   │
│  │ (US-East)  │  │ (EU-West)  │  │ (Asia-SE)  │  │ (US-West)  │   │
│  │            │  │            │  │            │  │            │   │
│  │  Anycast   │  │  Anycast   │  │  Anycast   │  │  Anycast   │   │
│  │  DNS/BGP   │  │  DNS/BGP   │  │  DNS/BGP   │  │  DNS/BGP   │   │
│  └──────┬─────┘  └──────┬─────┘  └──────┬─────┘  └──────┬─────┘   │
│         └───────────────┬┴───────────────┘               │         │
│                         │                                 │         │
└─────────────────────────┼─────────────────────────────────┘         │
                          ▼                                           │
┌─────────────────────────────────────────────────────────────────────┐
│                   GATEWAY CLUSTER (per region)                       │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  L4 Load Balancer (Azure LB / NLB)                           │   │
│  └──────────────┬───────────────────┬──────────────┬────────────┘   │
│                 ▼                   ▼              ▼                 │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐    │
│  │  Gateway Node 1  │ │  Gateway Node 2  │ │  Gateway Node N  │    │
│  │                  │ │                  │ │                  │    │
│  │ ┌──────────────┐│ │ ┌──────────────┐│ │ ┌──────────────┐│    │
│  │ │Route Table   ││ │ │Route Table   ││ │ │Route Table   ││    │
│  │ │(in-memory    ││ │ │(in-memory    ││ │ │(in-memory    ││    │
│  │ │ radix tree)  ││ │ │ radix tree)  ││ │ │ radix tree)  ││    │
│  │ ├──────────────┤│ │ ├──────────────┤│ │ ├──────────────┤│    │
│  │ │Policy Engine ││ │ │Policy Engine ││ │ │Policy Engine ││    │
│  │ │(auth, rate   ││ │ │(auth, rate   ││ │ │(auth, rate   ││    │
│  │ │ limit, xform)││ │ │ limit, xform)││ │ │ limit, xform)││    │
│  │ ├──────────────┤│ │ ├──────────────┤│ │ ├──────────────┤│    │
│  │ │Local Cache   ││ │ │Local Cache   ││ │ │Local Cache   ││    │
│  │ │(response     ││ │ │(response     ││ │ │(response     ││    │
│  │ │ cache, L1)   ││ │ │ cache, L1)   ││ │ │ cache, L1)   ││    │
│  │ └──────────────┘│ │ └──────────────┘│ │ └──────────────┘│    │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘    │
│                                                                      │
└──────┬──────────────┬──────────────────┬──────────────┬──────────────┘
       │              │                  │              │
       ▼              ▼                  ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Redis Cluster│ │ Config Store │ │ Backend Svcs │ │ Analytics    │
│              │ │              │ │              │ │ Pipeline     │
│ Rate limit   │ │ Azure Cosmos │ │ user-svc     │ │              │
│ counters     │ │ DB / etcd    │ │ order-svc    │ │ Kafka →      │
│ API key      │ │              │ │ payment-svc  │ │ Flink →      │
│ lookup       │ │ API defs     │ │ ...          │ │ Dashboard    │
│ Session      │ │ Route rules  │ │              │ │              │
│ cache        │ │ Policies     │ │              │ │              │
│ Circuit      │ │ Subscriptions│ │              │ │              │
│ breaker state│ │              │ │              │ │              │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    CONTROL PLANE                                     │
│                                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐     │
│  │ Config      │  │ Health      │  │ Developer Portal        │     │
│  │ Propagation │  │ Monitor     │  │ (API docs, try-it,      │     │
│  │ Service     │  │ Service     │  │  key management)        │     │
│  │             │  │             │  │                         │     │
│  │ Push config │  │ Check all   │  │ Self-service for        │     │
│  │ to all      │  │ backends    │  │ API consumers           │     │
│  │ gateway     │  │ Update pool │  │                         │     │
│  │ nodes       │  │ membership  │  │                         │     │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Distributed Rate Limiting

**Problem**: Rate limiting must be consistent across 400+ gateway nodes globally. A user with a 100 RPS limit shouldn't get 100 RPS × 400 nodes = 40,000 RPS by hitting different nodes.

**Solution: Sliding Window with Redis + Local Approximation**

```
Two-Tier Rate Limiting:

TIER 1 — LOCAL (per gateway node, no network hop):
  - Token bucket per API key, refilled based on (global_limit / num_nodes)
  - Node with 100 RPS global limit across 10 nodes → 10 RPS local limit
  - Fast: no Redis call for requests within local budget
  - Approximate but catches burst traffic instantly

TIER 2 — GLOBAL (Redis, for accuracy):
  - Sliding window log in Redis
  - Check every Nth request (or when local bucket depleted)
  
Redis Sliding Window Implementation:
  Key: ratelimit:{apiKey}:{window}
  
  MULTI
    ZADD ratelimit:abc123:60 {timestamp} {requestId}    -- add request
    ZREMRANGEBYSCORE ratelimit:abc123:60 0 {now - 60}   -- remove old entries
    ZCARD ratelimit:abc123:60                            -- count in window
  EXEC
  
  If count > limit → return 429

Optimization — Fixed Window Counter (simpler, less accurate):
  Key: ratelimit:{apiKey}:{minute_bucket}
  INCR ratelimit:abc123:202501151030
  EXPIRE ratelimit:abc123:202501151030 120
  
  If count > limit → 429
  
  Drawback: boundary spike (99 at :59, 100 at :00 = 199 in 2 seconds)
  Solution: Sliding window = weighted average of current + previous window

Response Headers:
  X-RateLimit-Limit: 100
  X-RateLimit-Remaining: 42
  X-RateLimit-Reset: 1705300060 (Unix timestamp)
  Retry-After: 23 (seconds, only on 429)

Hierarchical Limits:
  Global account limit: 10,000 RPS
    └── Per-API limit: 1,000 RPS  
         └── Per-operation limit: 100 RPS
              └── Per-IP limit: 20 RPS
  
  Request must pass ALL levels to proceed.
```

---

### Deep Dive 2: Configuration Propagation

**Problem**: When an admin updates a route or policy, the change must reach all 400 gateway nodes within 5 seconds. How do we propagate configuration safely?

**Solution: Event-Driven Config Push with Version Validation**

```
Config Update Flow:

Admin updates API route via Management API
        │
        ▼
Management Service:
  1. Validate new config (syntax, references, no conflicts)
  2. Write to Config Store (Cosmos DB) with new version number
  3. Publish "config.updated" event to Azure Event Grid
        │
        ▼
Config Propagation Service:
  1. Receive event
  2. Build new config snapshot (full route table + policies)
  3. Push to all gateway nodes via:
     Option A: gRPC streaming (persistent connections to all nodes)
     Option B: Redis Pub/Sub channel "config-updates"
     Option C: Shared config in Redis, nodes poll every 2s
        │
        ▼
Gateway Node receives new config:
  1. Validate config locally (parse routes, compile policies)
  2. Build new route table (radix tree) in memory
  3. Atomic swap: old_table ↔ new_table (pointer swap, zero downtime)
  4. Report config version to health endpoint
  5. Old config garbage collected after drain period (30s)

Safety Mechanisms:
  - Config versioning: monotonically increasing version
  - Rollback: if >10% of nodes report config errors → auto-rollback
  - Canary: push to 5% of nodes first, monitor errors for 60s, then full rollout
  - Config diff: only propagate delta (not full config)
  
Consistency Check:
  Health endpoint includes config version:
  GET /health → { "status": "healthy", "configVersion": 42 }
  
  Config Propagation Service monitors:
  - All nodes should converge to latest version within 5s
  - Alert if any node is >2 versions behind
```

---

### Deep Dive 3: High Availability & Zero-Downtime Failover

**Problem**: The API Gateway is a single point of failure. If it goes down, ALL APIs are unreachable. How do we achieve 99.999% availability?

**Solution: Multi-Layer Redundancy**

```
Layer 1 — DNS-Level Failover:
  api.contoso.com → Azure Traffic Manager (or Anycast DNS)
    Priority routing:
      1. US-East gateway cluster (primary)
      2. US-West gateway cluster (secondary)
      3. EU-West gateway cluster (tertiary)
    
    Health probe: /health every 10s
    Failover time: 30-60s (DNS TTL)

Layer 2 — Regional Load Balancing:
  Within each region: Azure Load Balancer (L4)
    - Distributes across gateway node pool
    - Health checks every 5s
    - Unhealthy node removed in 15s
    - Auto-scaling: 200-600 nodes based on CPU/RPS

Layer 3 — Gateway Node Resilience:
  Each gateway node:
    - Stateless (all state in Redis / config store)
    - Graceful shutdown: drain connections over 30s
    - Hot standby: pre-warmed instances ready to take traffic
    - Local cache: survive Redis outage for rate limiting (degraded mode)

Layer 4 — Backend Resilience:
  Circuit Breaker per backend pool:
    States: CLOSED → OPEN → HALF-OPEN
    
    CLOSED (normal): all requests forwarded
      If error_rate > 50% in 10s window → transition to OPEN
    
    OPEN: all requests fail-fast (503)
      After 30s timeout → transition to HALF-OPEN
    
    HALF-OPEN: allow 10% of traffic through
      If success → CLOSED
      If failure → OPEN again
    
    State stored in Redis (shared across nodes):
      HSET circuit:user-svc-pool state OPEN openedAt 1705300000

  Retry Policy:
    Max retries: 2
    Retry on: 502, 503, 504, connection timeout
    Backoff: exponential (100ms, 200ms)
    Retry budget: max 20% of requests can be retries (prevent retry storms)

Zero-Request-Loss During Deploys:
  Rolling update strategy:
    1. Remove node from LB pool
    2. Wait for in-flight requests to complete (30s drain)
    3. Update node
    4. Health check passes
    5. Add back to LB pool
    6. Repeat for next node
  
  Maximum 10% of nodes updating simultaneously
```

---

### Deep Dive 4: Authentication Pipeline

**Problem**: Every request must be authenticated in <2ms. Supporting multiple auth methods (API key, JWT, mTLS, OAuth) adds complexity. How to do this efficiently?

**Solution: Pluggable Auth Pipeline with Caching**

```
Authentication Flow:

Request arrives → Auth Policy Engine
        │
        ├── Determine auth method from request:
        │   API key in header? → API Key validation
        │   Bearer token? → JWT validation
        │   Client certificate? → mTLS validation
        │   OAuth callback? → OAuth flow
        │
        ▼
API Key Validation (most common):
  1. Extract key from header: Ocp-Apim-Subscription-Key: abc123
  2. Check local cache (LRU, 10K entries, TTL 60s):
     HIT → return cached subscription info (0.01ms)
  3. Cache miss → check Redis:
     HGET apikeys:abc123 → { subscriptionId, developerId, tier, rateLimit, apis[] }
     HIT → cache locally, return (0.5ms)
  4. Redis miss → check Config Store (Cosmos DB):
     → return, cache in Redis + local (5ms)
  5. Key not found → 401 Unauthorized

JWT Validation (OAuth 2.0 / Azure AD):
  1. Extract token from Authorization: Bearer <jwt>
  2. Decode JWT header → extract kid (key ID)
  3. Fetch signing key from JWKS cache:
     Local cache of Azure AD JWKS endpoint
     Refreshed every 24 hours (or on kid miss)
  4. Verify signature (RSA/ECDSA) → 0.1ms
  5. Check claims:
     - exp (not expired)
     - aud (audience matches API)
     - iss (issuer is trusted)
     - scope/roles (has required permissions)
  6. Extract userId from claims for downstream use
  
  Optimization: JWT validation is pure CPU, no network call needed
  after JWKS is cached. → Handles millions of RPS.

Result:
  - Authenticated requests get enriched headers:
    X-User-Id: user123
    X-Tenant-Id: contoso
    X-Subscription-Tier: premium
  - Backend services trust these headers (zero-trust internal network)
```

---

### Deep Dive 5: Observability & Analytics

**Problem**: With 10M RPS, how do we capture detailed analytics without impacting gateway latency?

**Solution: Async Sampling + Streaming Aggregation**

```
Per-Request Logging (fire-and-forget):

Gateway Node:
  For each request:
    1. Capture: requestId, timestamp, apiId, route, statusCode,
       latencyMs, apiKey, clientIP, backendLatencyMs, cacheHit
    2. Write to in-memory ring buffer (not disk!)
    3. Background thread: batch flush to Kafka every 1s (or 1000 records)
    
  Request log:
  {
    "requestId": "req_abc",
    "timestamp": "2025-01-15T10:30:00.123Z",
    "api": "user-service-v2",
    "route": "GET /users/{id}",
    "statusCode": 200,
    "latencyMs": 45,
    "gatewayLatencyMs": 3,
    "backendLatencyMs": 42,
    "apiKey": "abc...(masked)",
    "clientIP": "203.0.113.42",
    "region": "eastus",
    "nodeId": "gw-node-42",
    "cacheHit": false,
    "rateLimited": false
  }

Analytics Pipeline:
  Kafka (partitioned by apiId)
    │
    ├── Real-time: Flink / Stream Analytics
    │     ├── 1-minute aggregations (count, p50, p95, p99, error rate)
    │     ├── Anomaly detection (sudden spike in 5xx errors)
    │     └── Write to time-series DB (Azure Monitor / InfluxDB)
    │
    └── Batch: Spark (hourly)
          ├── Detailed usage reports per subscription
          ├── Top APIs by traffic
          └── Write to Data Warehouse (Azure Synapse)

Sampling for High-Volume APIs:
  If API > 100K RPS → sample 10% of request logs
  If API > 1M RPS → sample 1%
  Always log: errors (4xx, 5xx), slow requests (>1s), rate-limited requests
  
  Sampled logs marked: { "sampled": true, "sampleRate": 0.01 }
  Aggregation adjusts: count * (1/sampleRate) for accurate totals
```
