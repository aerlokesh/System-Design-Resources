# 🎯 Topic 14: Load Balancing

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Layer 4 vs Layer 7 load balancing, algorithms (round-robin, least connections, consistent hashing, weighted), health checks, sticky sessions, TLS termination, AWS ALB/NLB/Global Accelerator real-world patterns, global load balancing, WebSocket handling, auto-scaling, service mesh, and how to articulate load balancing decisions in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Load Balance](#why-load-balance)
3. [Layer 4 vs Layer 7 Load Balancing](#layer-4-vs-layer-7-load-balancing)
4. [Load Balancing Algorithms Deep Dive](#load-balancing-algorithms-deep-dive)
5. [Consistent Hashing — The Interview Favorite](#consistent-hashing--the-interview-favorite)
6. [Health Checks — Deep Dive](#health-checks--deep-dive)
7. [Sticky Sessions (Session Affinity)](#sticky-sessions-session-affinity)
8. [TLS/SSL Termination](#tlsssl-termination)
9. [AWS Load Balancers — ALB, NLB, GLB](#aws-load-balancers--alb-nlb-glb)
10. [AWS ALB Deep Dive — Real-World Patterns](#aws-alb-deep-dive--real-world-patterns)
11. [AWS NLB Deep Dive — When & Why](#aws-nlb-deep-dive--when--why)
12. [Global Server Load Balancing (GSLB)](#global-server-load-balancing-gslb)
13. [AWS Global Accelerator vs CloudFront](#aws-global-accelerator-vs-cloudfront)
14. [Load Balancing for WebSocket Connections](#load-balancing-for-websocket-connections)
15. [Auto-Scaling Integration](#auto-scaling-integration)
16. [Load Balancer High Availability](#load-balancer-high-availability)
17. [Service Mesh & Client-Side Load Balancing](#service-mesh--client-side-load-balancing)
18. [DNS-Based Load Balancing (Route 53)](#dns-based-load-balancing-route-53)
19. [Real-World Architecture Patterns](#real-world-architecture-patterns)
20. [Load Balancing by System Design Problem](#load-balancing-by-system-design-problem)
21. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
22. [Common Interview Mistakes](#common-interview-mistakes)
23. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **load balancer** distributes incoming requests across multiple servers to ensure no single server is overwhelmed. It's the front door of every scalable system.

```
Without load balancer:
  All users → Single server → Server overloaded → CRASH
  Capacity: Limited to one machine

With load balancer:
  Users → Load Balancer → Server 1 (33%)
                        → Server 2 (33%)
                        → Server 3 (33%)
  Capacity: N × single server capacity
  If Server 2 dies → LB routes to 1 and 3 (zero downtime)
```

---

## Why Load Balance

```
1. SCALABILITY: Distribute traffic across N servers → handle N× more load
2. AVAILABILITY: If server dies → LB stops sending traffic → zero downtime
3. PERFORMANCE: Route to least-loaded or geographically closest server
4. FLEXIBILITY: Roll out new versions gradually (canary, blue-green)
5. SECURITY: Single entry point → apply WAF, TLS, rate limiting once
6. MAINTENANCE: Drain server → update → re-add (zero-downtime deploys)
```

---

## Layer 4 vs Layer 7 Load Balancing

### Layer 4 (Transport Layer — TCP/UDP)

```
What it sees: Source IP, destination IP, port, protocol (TCP/UDP)
What it can't see: HTTP method, URL path, headers, cookies, body
Decision based on: IP + port → route to backend

How it works:
  Client ─TCP─→ LB ─TCP─→ Backend
  LB forwards the raw TCP connection
  Very fast — just rewrites IP addresses and forwards packets

AWS: Network Load Balancer (NLB)
Use when: Need raw TCP/UDP, ultra-low latency, static IPs, gaming, IoT
```

### Layer 7 (Application Layer — HTTP/HTTPS)

```
What it sees: EVERYTHING in the HTTP request
  URL path, method, headers, cookies, query params, body
Decision based on: Content of the request

How it works:
  Client ─HTTP─→ LB (terminates connection, inspects request)
                 → Chooses backend based on URL/header/cookie
                 → Opens NEW connection to backend
                 → Forwards request

AWS: Application Load Balancer (ALB)
Use when: Need path-based routing, host-based routing, WebSocket, 
          HTTP/2, header inspection, authentication
```

### Comparison Table

| Feature | Layer 4 (NLB) | Layer 7 (ALB) |
|---------|--------------|---------------|
| OSI Layer | Transport (TCP/UDP) | Application (HTTP/HTTPS) |
| Sees | IP, port, protocol | Full HTTP request |
| Routing | IP hash, round-robin | Path, host, header, cookie |
| TLS termination | Pass-through or terminate | Always terminates |
| WebSocket | ✅ (TCP passthrough) | ✅ (HTTP upgrade) |
| Latency | **Lowest** (~100μs overhead) | Higher (~1-5ms overhead) |
| Throughput | **Millions of RPS** | Hundreds of thousands RPS |
| Static IP | ✅ Yes | ❌ No (use Global Accelerator) |
| Cost (AWS) | Per LCU ($0.006/hr) | Per LCU ($0.008/hr) |
| Use case | TCP services, gaming, IoT | Web apps, APIs, microservices |

### When to Use Which (Decision)

```
Need to route by URL path (/api/* vs /static/*)?      → ALB
Need to route by hostname (api.com vs web.com)?        → ALB  
Need authentication at LB level (Cognito/OIDC)?        → ALB
Need raw TCP/UDP (databases, gaming, MQTT)?             → NLB
Need ultra-low latency (microseconds matter)?           → NLB
Need static IP (whitelisting, DNS)?                     → NLB
Need WebSocket with HTTP-level features?                → ALB
Default for web apps / APIs?                            → ALB
```

---

## Load Balancing Algorithms Deep Dive

### 1. Round Robin

```
Requests distributed in order: Server 1 → 2 → 3 → 1 → 2 → 3 ...

Pros: Simplest, even distribution when servers are identical
Cons: Ignores server load, ignores request complexity

Best for: Homogeneous servers, stateless services
Example: 3 identical EC2 instances behind ALB (default)
```

### 2. Weighted Round Robin

```
Servers with different capacities:
  Server A (8 CPU): weight=3 → gets 3 out of 5 requests
  Server B (4 CPU): weight=2 → gets 2 out of 5 requests

Use case: Mixed instance sizes, gradual rollout (new version gets weight=1)
AWS ALB: Target group with weighted target groups (traffic shifting)
```

### 3. Least Connections

```
Route to server with fewest active connections:
  Server A: 42 connections → NOT chosen
  Server B: 15 connections → ✅ CHOSEN
  Server C: 38 connections → NOT chosen

Pros: Adapts to actual server load, handles slow requests well
Cons: Needs connection tracking (slightly more overhead)

Best for: Long-lived connections (WebSocket), variable request duration
AWS ALB: "Least Outstanding Requests" algorithm
```

### 4. Least Response Time

```
Route to server with lowest average response time:
  Server A: avg 45ms → ✅ CHOSEN (fastest)
  Server B: avg 120ms → NOT chosen (slower — maybe higher load)
  Server C: avg 80ms → NOT chosen

Pros: Optimizes for user experience directly
Cons: Requires continuous latency measurement, can oscillate
```

### 5. IP Hash

```
hash(client_IP) % N = server_index
  Client 1.2.3.4 → hash → 7 % 3 = 1 → Server 1
  Client 5.6.7.8 → hash → 4 % 3 = 1 → Server 1
  Client 9.0.1.2 → hash → 2 % 3 = 2 → Server 2

Pros: Same client always goes to same server (poor man's sticky sessions)
Cons: Uneven distribution, ALL requests move on server add/remove

Best for: Simple session affinity without cookies
Problem: When N changes (scale up/down), almost ALL mappings change!
→ Solution: Consistent Hashing
```

### 6. Random

```
Randomly pick a server for each request.
Surprisingly good with large server pools (law of large numbers).
Used in: Simple client-side load balancing, chaos testing.
```

---

## Consistent Hashing — The Interview Favorite

### The Problem with Simple Hashing

```
# Simple hash: hash(key) % N
# With 3 servers: hash("user_123") % 3 = server 1

# ADD one server (N=3 → N=4):
# hash("user_123") % 4 = server 3 ← DIFFERENT!
# Almost ALL keys get remapped → massive cache invalidation!
# At scale: millions of cache misses → thundering herd → origin overloaded
```

### How Consistent Hashing Works

```
# Arrange servers on a virtual ring (0 to 2^32):

          Server A (hash=100)
         /                    \
   Server D (hash=300)    Server B (hash=150)
         \                    /
          Server C (hash=250)

# To find server for a key:
# 1. Hash the key: hash("user_123") = 180
# 2. Walk clockwise until you find a server
# 3. 180 → next server clockwise → Server C (250)

# ADD Server E at hash=200:
# Only keys between 150-200 move from Server C to Server E
# ALL OTHER keys stay where they are!
# Only ~K/N keys move (where K = total keys, N = servers)
```

### Virtual Nodes (Vnodes) — Solving Imbalance

```
# Problem: 4 servers on ring → uneven distribution (some get 40%, some get 10%)
# Solution: Each physical server gets 100-200 virtual positions on the ring

Server A → V_A1(100), V_A2(500), V_A3(900), V_A4(1300)...
Server B → V_B1(200), V_B2(600), V_B3(1000), V_B4(1400)...

# More evenly distributed around the ring
# When Server A removed → its load spread evenly across B, C, D
# Not concentrated on one neighbor
```

### Where Consistent Hashing Is Used

```
• DynamoDB: Partition data across storage nodes
• Cassandra: Distribute data across ring of nodes
• Redis Cluster: Assign hash slots to nodes
• CDN: Route requests to nearest cache server
• Chat systems: Assign users to WebSocket gateways
• Load balancing: Sticky sessions based on user ID
```

---

## Health Checks — Deep Dive

### Types of Health Checks

```
# 1. TCP Health Check (Layer 4)
# Can we open a TCP connection to port 8080?
# Simple but: Server might be running but returning 500s
# AWS NLB default

# 2. HTTP Health Check (Layer 7) — RECOMMENDED
# GET /health → 200 OK?
# Better: Actually checks if application is responding correctly
# AWS ALB default: GET /health every 30 seconds

# 3. Deep Health Check (Application-Level)
# GET /health/deep → checks DB connection, Redis connection, disk space
# Returns: { "status": "healthy", "db": "ok", "cache": "ok", "disk": "ok" }
# ⚠️ Careful: If DB is slow, health check times out → LB marks HEALTHY server as unhealthy!
# → Use for: Startup checks, readiness probes
# → Don't use for: Liveness checks (could cascade failures)

# 4. Shallow Health Check (for Liveness)
# GET /health → 200 OK (just checks "am I running?")
# Fast, doesn't check dependencies
# → Use for: ALB health checks (determine if this specific server is alive)
```

### Health Check Configuration (AWS ALB)

```
Health check path:       /health
Healthy threshold:       3    (3 consecutive successes → mark healthy)
Unhealthy threshold:     2    (2 consecutive failures → mark unhealthy)
Interval:                30s  (check every 30 seconds)
Timeout:                 5s   (fail if no response in 5s)
Success codes:           200  (or 200-299)

# Why thresholds > 1?
# Prevent flapping: 1 timeout doesn't mean server is down (network blip)
# 2 consecutive failures → likely real issue → remove from pool
# 3 consecutive successes → confirmed recovered → add back to pool
```

### Graceful Shutdown with Health Checks

```
# Problem: Server receives SIGTERM → immediately stops → in-flight requests fail

# Solution: Graceful drain
1. Receive SIGTERM (shutdown signal)
2. Health check endpoint returns 503 (Service Unavailable)
3. LB marks as unhealthy → stops sending NEW requests (within 30s)
4. Server finishes processing in-flight requests (drain period: 30-300s)
5. After drain: Server shuts down cleanly

AWS: ALB "deregistration delay" (default 300 seconds)
  → ALB waits 300s for in-flight requests before removing target
  → Set to: max_request_duration + buffer
```

---

## Sticky Sessions (Session Affinity)

### What & Why

```
Problem: User logs in → request goes to Server A (session created)
         Next request → goes to Server B → no session → user logged out!

Solution: Sticky sessions → LB always sends same user to same server

Methods:
1. Cookie-based (ALB): LB sets AWSALB cookie → routes by cookie value
   Duration-based: Sticky for X seconds (e.g., 1 hour)
   Application-based: Custom cookie name from your app

2. IP-based (NLB): hash(client_IP) → always same server
   Problem: Users behind same NAT → same IP → all go to same server!

3. Header-based: Custom header (X-User-Id) → consistent routing
```

### When to Use (and When NOT To)

```
✅ USE sticky sessions:
  • Legacy apps with server-side sessions
  • WebSocket connections (MUST stay on same server)
  • In-memory caching per user

❌ AVOID sticky sessions (better alternatives):
  • Store sessions in Redis/DynamoDB → ANY server can serve any user
  • Use JWT → stateless authentication, no session to stick to
  • Use consistent hashing → natural affinity without LB cookies

Problem with sticky sessions:
  • Uneven load (one server gets all "heavy" users)
  • Scaling issues (add server → existing sessions don't move)
  • Failover (server dies → user loses session → re-login)
```

---

## TLS/SSL Termination

### Where to Terminate TLS

```
Option 1: At Load Balancer (RECOMMENDED)
  Client ──HTTPS──→ ALB (terminates TLS) ──HTTP──→ Backend
  
  Pros:
  • Backend servers don't need certificates (simpler)
  • ALB handles TLS offloading (CPU-intensive work)
  • Centralized certificate management (ACM)
  • HTTP/2 → HTTP/1.1 translation done at LB
  
  Cons:
  • Traffic between ALB and backend is HTTP (unencrypted in VPC)
  • Regulatory: Some standards require end-to-end encryption

Option 2: End-to-End (TLS Passthrough)
  Client ──HTTPS──→ NLB (passes through) ──HTTPS──→ Backend
  
  Pros: True end-to-end encryption, compliance
  Cons: Backend manages certificates, more complex

Option 3: Re-encryption
  Client ──HTTPS──→ ALB (terminates) ──HTTPS──→ Backend (re-encrypts)
  
  Pros: LB can inspect traffic + backend traffic encrypted
  Cons: Double TLS overhead, certificate management on both sides

AWS Best Practice:
  ALB terminates TLS (ACM certificate) → HTTP to backend in private VPC
  If compliance requires: ALB re-encryption to backend (HTTPS target group)
```

---

## AWS Load Balancers — ALB, NLB, GLB

### Complete AWS Load Balancer Comparison

| Feature | ALB | NLB | GWLB | CLB (Legacy) |
|---------|-----|-----|------|-------------|
| Layer | 7 (HTTP/HTTPS) | 4 (TCP/UDP/TLS) | 3 (IP) | 4+7 |
| Protocol | HTTP, HTTPS, gRPC | TCP, UDP, TLS | IP (GENEVE) | HTTP, TCP |
| Routing | Path, host, header, query, method | Port, protocol | N/A | Basic |
| Static IP | ❌ | ✅ | ✅ | ❌ |
| WebSocket | ✅ | ✅ (TCP) | N/A | ❌ |
| HTTP/2 | ✅ | N/A | N/A | ❌ |
| gRPC | ✅ | ✅ (TCP) | N/A | ❌ |
| Auth (Cognito/OIDC) | ✅ | ❌ | ❌ | ❌ |
| WAF integration | ✅ | ❌ | ❌ | ❌ |
| Lambda target | ✅ | ❌ | ❌ | ❌ |
| Cross-zone LB | ✅ (free) | ✅ ($) | ✅ | ✅ |
| Pricing | ~$0.008/LCU-hr | ~$0.006/LCU-hr | ~$0.013/hr | Legacy |
| Use case | Web apps, APIs | TCP services, static IP | Security appliances | Don't use |

---

## AWS ALB Deep Dive — Real-World Patterns

### Path-Based Routing (Microservices)

```
# Single ALB routes to different services based on URL path

ALB Rules:
  /api/users/*     → Target Group: user-service (ECS Fargate)
  /api/orders/*    → Target Group: order-service (ECS Fargate)
  /api/payments/*  → Target Group: payment-service (ECS Fargate)
  /static/*        → Target Group: S3 bucket (via Lambda or fixed response)
  Default (*)      → Target Group: frontend (React SPA in ECS)

# One domain, one ALB, many services
# No need for API Gateway for simple routing
# Save cost: One ALB (~$22/month) vs one per service
```

### Host-Based Routing (Multi-Tenant)

```
# Route by domain name

ALB Rules:
  api.myapp.com    → Target Group: API servers
  admin.myapp.com  → Target Group: Admin panel
  docs.myapp.com   → Target Group: Documentation site
  *.myapp.com      → Target Group: Default (404 page)

# All domains point to same ALB IP
# ALB reads Host header → routes accordingly
```

### Weighted Target Groups (Canary Deploys)

```
# Deploy new version to 5% of traffic:

ALB Forward Action:
  Target Group: v1-service  → Weight: 95  (current version)
  Target Group: v2-service  → Weight: 5   (new version)

# Gradually increase:
# v2=5% → monitor → v2=25% → monitor → v2=50% → monitor → v2=100%
# If errors spike at 5% → rollback instantly (set v1=100%)

# This IS canary deployment using ALB natively!
# No additional deployment tools needed for basic canary
```

### ALB + Cognito Authentication

```
# ALB can authenticate users BEFORE reaching your backend!

ALB Rule:
  Path: /api/*
  Action 1: Authenticate with Cognito User Pool
    → If not logged in → redirect to Cognito login page
    → If logged in → continue to next action
  Action 2: Forward to Target Group: api-service
    → Request includes user claims in headers:
    → x-amzn-oidc-identity: user_123
    → x-amzn-oidc-data: <JWT with user info>

# Your backend doesn't need authentication code!
# ALB handles: Login page, token validation, token refresh
# Backend receives: Verified user ID in headers
```

### ALB Fixed Response (Maintenance Mode)

```
# Return static response without any backend:

ALB Rule:
  Path: /*
  Action: Return fixed response
    Status: 503
    Content-Type: text/html
    Body: "<h1>We're undergoing maintenance. Back in 30 minutes!</h1>"

# Zero backend needed for maintenance page
# Also useful for: /health endpoint at LB level, CORS preflight responses
```

---

## AWS NLB Deep Dive — When & Why

### Use Cases for NLB

```
1. STATIC IPs (required for IP whitelisting)
   NLB provides 1 static IP per AZ
   Useful: Partners whitelist your IP, IoT devices configured with IP
   
2. EXTREME PERFORMANCE
   NLB handles millions of RPS with microsecond latency
   ALB: ~1-5ms added latency
   NLB: ~100μs added latency
   
3. TCP/UDP PROTOCOLS
   gRPC (can use ALB too), MQTT (IoT), game servers, DNS, SIP
   
4. TLS PASSTHROUGH
   Client → NLB → Backend (TLS not terminated at LB)
   Backend sees original client certificate (mTLS)
   
5. PRIVATELINK
   Expose service to other AWS accounts via PrivateLink
   Requires NLB as frontend
```

### NLB + ALB Combo Pattern

```
# Need static IP + Layer 7 features?
# → Put NLB in front of ALB!

Client → NLB (static IP) → ALB (path routing, auth, WAF) → Backend

# Alternatively: ALB + Global Accelerator (provides static IPs for ALB)
```

---

## Global Server Load Balancing (GSLB)

### Multi-Region Architecture

```
# Users worldwide → route to NEAREST region

                    ┌──── us-east-1 ────┐
                    │  ALB → ECS (API)  │
                    │  Aurora (primary) │
  User (NYC) ──→   └───────────────────┘
                    
Route 53            
(latency-based)     
                    
  User (London) ──→ ┌──── eu-west-1 ────┐
                    │  ALB → ECS (API)  │
                    │  Aurora (replica) │
                    └───────────────────┘

  User (Tokyo) ──→  ┌──── ap-northeast-1 ─┐
                    │  ALB → ECS (API)     │
                    │  Aurora (replica)    │
                    └──────────────────────┘
```

### Route 53 Routing Policies for GSLB

```
# Simple: Single endpoint
# → Not load balancing, just DNS

# Weighted: A/B testing between regions
# us-east-1: weight=70 (70% of traffic)
# eu-west-1: weight=30 (30% of traffic)

# Latency-based: Route to lowest-latency region
# Route 53 measures latency from user → each region
# Automatically routes to fastest region for each user
# → NYC user → us-east-1 (30ms), London user → eu-west-1 (20ms)

# Failover: Active-passive
# Primary: us-east-1 (active)
# Secondary: eu-west-1 (standby)
# Health check fails on primary → Route 53 switches to secondary
# RTO: ~60 seconds (DNS TTL)

# Geolocation: Route by user's country
# EU users → eu-west-1 (GDPR compliance: data stays in EU)
# US users → us-east-1
# Default → us-east-1

# Multi-value: Return multiple healthy IPs
# Return up to 8 healthy endpoints → client picks one
# Basic client-side load balancing via DNS
```

---

## AWS Global Accelerator vs CloudFront

```
# Both provide "global edge" access, but for different purposes:

┌──────────────────┬────────────────────┬────────────────────┐
│ Feature          │ Global Accelerator  │ CloudFront          │
├──────────────────┼────────────────────┼────────────────────┤
│ Static IPs       │ ✅ 2 anycast IPs   │ ❌ No              │
│ Protocol         │ TCP + UDP           │ HTTP/HTTPS only    │
│ Caching          │ ❌ No               │ ✅ Yes (CDN)       │
│ Edge compute     │ ❌ No               │ ✅ Lambda@Edge     │
│ Routing          │ Nearest healthy EP  │ Nearest edge + cache│
│ Use case         │ Non-HTTP (gaming,   │ HTTP content, APIs, │
│                  │ IoT), static IPs,   │ static assets,     │
│                  │ instant failover    │ edge caching        │
│ Cost             │ $0.025/hr + data    │ Per-request + data │
└──────────────────┴────────────────────┴────────────────────┘

# Rule of thumb:
# HTTP APIs + static content → CloudFront
# TCP/UDP + static IPs + instant failover → Global Accelerator
# HTTP APIs + need static IPs → Global Accelerator in front of ALB
```

---

## Load Balancing for WebSocket Connections

```
# Challenge: WebSocket = long-lived TCP connection
# Can't redistribute mid-connection → must be sticky

ALB WebSocket handling:
1. Client sends HTTP Upgrade request
2. ALB routes to backend (standard routing rules)
3. Backend responds with 101 Switching Protocols
4. ALB maintains WebSocket connection between client ↔ backend
5. Connection stays on SAME backend until closed

# Scaling challenge:
# 100K WebSocket connections → each pinned to a server
# Can't rebalance without disconnecting users!

# Solutions:
# 1. Consistent hashing: hash(user_id) → always same server
# 2. Connection registry: Redis maps user → server
#    Other services query Redis to find user's server → route message there
# 3. Pub/Sub fan-out: All servers subscribe to Redis/Kafka
#    Message published → ALL servers check if their user is connected → push

# NLB for WebSocket:
# NLB is TCP passthrough → WebSocket works naturally
# But: No path-based routing, no HTTP features
```

---

## Auto-Scaling Integration

### ALB + Auto Scaling Group

```
# Auto Scaling Group registers instances with ALB Target Group automatically

Auto Scaling Policy:
  Target tracking: Keep CPU at 60%
    CPU < 60% → Scale in (remove instances)
    CPU > 60% → Scale out (add instances)
  
  Step scaling:
    CPU 60-70% → +1 instance
    CPU 70-80% → +2 instances
    CPU > 80%  → +4 instances

Scale-out flow:
  1. CloudWatch alarm: CPU > 60% for 3 minutes
  2. ASG launches new EC2 instance
  3. Instance registers with ALB Target Group
  4. ALB health check passes (healthy threshold: 3)
  5. ALB starts sending traffic (~90 seconds total)

Scale-in flow:
  1. CPU < 40% for 15 minutes (cooldown prevents flapping)
  2. ASG selects instance to terminate
  3. ALB starts draining (deregistration delay: 300s)
  4. In-flight requests complete
  5. Instance terminated

# ECS + ALB:
# Same concept but ECS manages container tasks instead of EC2 instances
# ECS Service auto-scaling: Target tracking on CPU/memory/custom metric
# Faster scale-out: Container startup (~10-30s) vs EC2 (~60-120s)
```

### Scaling Metrics for LB

```
# What to monitor for scaling decisions:

ALB Metrics (CloudWatch):
  RequestCountPerTarget    → Scale when requests/target > threshold
  TargetResponseTime       → Scale when latency increases
  ActiveConnectionCount    → Scale for connection-based workloads
  HTTPCode_Target_5XX      → Something wrong, maybe need more capacity

Custom Metrics (better):
  QueueDepth              → Scale workers based on pending work
  ConcurrentUsers          → Scale WebSocket servers based on connections
  OrdersPerMinute          → Scale based on business metric
```

---

## Load Balancer High Availability

```
# Single LB = single point of failure!

AWS ALB/NLB are inherently HA:
  • Deployed across multiple AZs automatically
  • AWS manages redundancy (you never see individual LB nodes)
  • If one AZ fails → LB nodes in other AZs handle traffic
  • Cross-zone load balancing: Distribute evenly across all AZs

# Self-managed (Nginx/HAProxy):
# Need TWO load balancers + failover mechanism

  ┌─────────────────────────────────────┐
  │  Virtual IP (VIP): 10.0.0.100      │
  │  ┌──────────┐    ┌──────────┐      │
  │  │ LB Active│    │ LB Standby│     │
  │  │ (primary)│    │ (secondary)│    │
  │  └────┬─────┘    └────┬──────┘    │
  │       │  Heartbeat     │           │
  │       │←──────────────→│           │
  │       │                │           │
  │  If Active dies → Standby takes VIP│
  │  Keepalived / VRRP protocol        │
  └─────────────────────────────────────┘

# AWS: Just use ALB/NLB → HA is built-in
```

---

## Service Mesh & Client-Side Load Balancing

### Service Mesh (Istio/Envoy/App Mesh)

```
# Instead of centralized LB → each service has a sidecar proxy

  Service A ──→ [Envoy Proxy A] ──→ [Envoy Proxy B] ──→ Service B
                  (sidecar)           (sidecar)

# Sidecar handles: Load balancing, retries, circuit breaking, mTLS, observability
# No separate LB infrastructure needed
# AWS: App Mesh (Envoy-based) or ECS Service Connect

Benefits:
  • Decentralized — no LB bottleneck
  • Rich L7 features (retry, timeout, circuit break)
  • mTLS between all services (zero trust)
  • Distributed tracing built-in
  
Drawbacks:
  • Complexity (Envoy config, control plane)
  • Resource overhead (sidecar per service = extra CPU/memory)
  • Debugging (another layer to troubleshoot)
```

### Client-Side Load Balancing

```
# Client itself chooses which server to call (no LB needed)

# Pattern: Service discovery → client gets list of servers → client picks one
# Service A → Service Registry (Consul/Eureka) → ["server1:8080", "server2:8080"]
# Service A → picks server1 (round-robin or least-connections)

# AWS: Cloud Map (service discovery) + SDK-based routing
# gRPC: Built-in client-side LB with name resolution

Benefits: No LB infrastructure, lower latency
Drawbacks: LB logic in every client, harder to update algorithm
```

---

## DNS-Based Load Balancing (Route 53)

```
# Route 53 resolves domain → returns IP(s) of backend(s)

my-api.example.com → Route 53
  → Health check: Server A (healthy), Server B (healthy), Server C (unhealthy)
  → Returns: [IP_A, IP_B]  (C removed because unhealthy)
  → Client connects to IP_A or IP_B

Pros:
  • No LB infrastructure (DNS does the routing)
  • Global routing (latency-based, geolocation)
  • Cheap (Route 53 queries: $0.40/M)

Cons:
  • DNS TTL caching → slow failover (clients cache old IPs)
  • No session affinity (each DNS lookup may return different IP)
  • Limited algorithms (weighted, latency, failover, geo — not least-connections)
  • Can't do path-based routing (DNS only knows domain, not URL path)

Use for: GSLB (multi-region routing), combined with ALB per region
  Route 53 → region selection → ALB → service selection
```

---

## Real-World Architecture Patterns

### Pattern 1: Simple Web App

```
Route 53 → ALB → Auto Scaling Group (EC2/ECS)
                   ├── Instance 1
                   ├── Instance 2
                   └── Instance 3

ALB config: Default action → forward to target group
Health check: GET /health → 200
Auto-scale: Target CPU 60%
```

### Pattern 2: Microservices with Path Routing

```
Route 53 → CloudFront → ALB
                          ├── /api/users/* → ECS: user-service (3 tasks)
                          ├── /api/orders/* → ECS: order-service (5 tasks)
                          ├── /api/payments/* → ECS: payment-service (2 tasks)
                          └── /* → S3: React frontend (via CloudFront)
```

### Pattern 3: Multi-Region Active-Active

```
Route 53 (latency-based)
  ├── us-east-1: ALB → ECS → Aurora (primary)
  └── eu-west-1: ALB → ECS → Aurora (read replica)

# Writes: Always go to us-east-1 (primary)
# Reads: Served from closest region
# Failover: Route 53 health check → if us-east-1 ALB unhealthy → all traffic to eu-west-1
#           Aurora: Promote read replica to primary
```

### Pattern 4: WebSocket + API on Same ALB

```
ALB
  ├── /ws/* → Target Group: WebSocket servers (sticky sessions, long timeout)
  │          Connection idle timeout: 3600s (1 hour)
  └── /api/* → Target Group: API servers (standard, no sticky)
              Connection idle timeout: 60s

# Same ALB handles both! Different routing rules and timeouts.
```

### Pattern 5: Internal Service Communication

```
# Internal ALB (not internet-facing) for service-to-service

Internet → Public ALB → Frontend Service
                             │
                     Internal ALB (private VPC)
                        ├── user-service.internal
                        ├── order-service.internal
                        └── payment-service.internal

# Internal ALB: No public IP, only accessible within VPC
# DNS: order-service.internal → Internal ALB → order-service targets
# Service discovery via ALB + Route 53 private hosted zone
```

---

## Load Balancing by System Design Problem

```
┌────────────────────────┬──────────────────────────────────────────┐
│ System                 │ Load Balancing Strategy                   │
├────────────────────────┼──────────────────────────────────────────┤
│ Web App (React + API)  │ ALB: /api/* → backend, /* → frontend    │
│ Microservices          │ ALB: Path-based routing to each service  │
│ WebSocket (Chat)       │ ALB: Sticky sessions or consistent hash │
│ Gaming (UDP)           │ NLB: UDP passthrough, ultra-low latency │
│ Global API             │ Route 53 latency → ALB per region       │
│ gRPC Services          │ ALB (HTTP/2) or NLB (TCP)              │
│ Video Streaming        │ CloudFront (CDN) + ALB origin           │
│ IoT (MQTT)             │ NLB: TCP on port 8883 (MQTT over TLS)  │
│ Multi-tenant SaaS      │ ALB: Host-based routing (tenant.app.com)│
│ Canary Deploy          │ ALB: Weighted target groups (95%/5%)    │
│ Static IP Required     │ NLB or ALB + Global Accelerator         │
│ Internal Microservices │ Internal ALB (private VPC, no internet) │
└────────────────────────┴──────────────────────────────────────────┘
```

---

## Interview Talking Points & Scripts

### "How would you handle load balancing?"

> *"I'd use an ALB for HTTP/HTTPS traffic — it gives me path-based routing for microservices (/api/users → user-service, /api/orders → order-service), built-in health checks, TLS termination with ACM, and weighted target groups for canary deployments. Behind it, an ECS Fargate auto-scaling group that scales based on CPU utilization targeting 60%. For health checks, a shallow /health endpoint that returns 200 if the process is running, with a 30-second interval and 2-failure unhealthy threshold. For WebSocket connections, I'd enable sticky sessions on the ALB. For multi-region, Route 53 with latency-based routing to ALBs in each region."*

### "Explain consistent hashing and when you'd use it"

> *"Consistent hashing arranges servers on a virtual ring. To find a server for a key, you hash the key and walk clockwise to the nearest server. The key benefit: when you add or remove a server, only K/N keys need to move, instead of almost all keys with simple modulo hashing. We use virtual nodes — each physical server gets 100+ positions on the ring — to ensure even distribution. I'd use it for: cache sharding (Redis cluster), database partitioning (DynamoDB, Cassandra), and WebSocket server assignment (route same user to same server consistently)."*

### "Layer 4 vs Layer 7?"

> *"Layer 4 operates at TCP/UDP — it's blazing fast (microsecond overhead) but can only route based on IP and port. Use it for raw TCP services, gaming, or when you need static IPs. Layer 7 operates at HTTP — it can route based on URL path, hostname, headers, and cookies, plus handle TLS termination, authentication, and WebSocket upgrades. The trade-off is slightly higher latency (1-5ms). On AWS, that's NLB vs ALB. For 95% of web applications, ALB is the right choice."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong | Better |
|---------|---------------|--------|
| "Use round robin for everything" | Ignores server capacity differences, request complexity | Least connections for variable workloads, weighted for mixed instance types |
| "Add more servers = solved" | LB misconfiguration, health checks matter | Design proper health checks, drain connections, auto-scale policies |
| "Sticky sessions = good" | Creates hotspots, breaks on failover | Use external session store (Redis/DynamoDB) + stateless servers |
| Not mentioning health checks | Dead servers receive traffic → user errors | Always discuss: health check path, interval, thresholds |
| "DNS load balancing is enough" | DNS caching delays failover (minutes) | DNS for region selection, ALB for server selection |
| Only discussing one layer | Miss the full picture | Route 53 (global) → ALB (region) → consistent hashing (application) |
| Forgetting about failover | No plan for server/region failure | Health checks + auto-scaling + multi-AZ + Route 53 failover |
| "ALB for everything" | NLB exists for a reason | TCP/UDP, static IPs, ultra-low latency → NLB |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│           LOAD BALANCING CHEAT SHEET                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  LAYER 4 (TCP/UDP) — NLB:                                   │
│    Ultra-low latency, static IPs, TCP passthrough            │
│    Use: Gaming, IoT, gRPC, when you need static IPs          │
│                                                              │
│  LAYER 7 (HTTP/HTTPS) — ALB:                                │
│    Path/host routing, auth, WAF, WebSocket, gRPC             │
│    Use: Web apps, APIs, microservices (95% of use cases)     │
│                                                              │
│  ALGORITHMS:                                                 │
│    Round robin: Simple, equal servers                        │
│    Weighted: Mixed instance sizes, canary deploys            │
│    Least connections: Variable duration requests, WebSocket   │
│    Consistent hashing: Cache sharding, session affinity       │
│    IP hash: Simple sticky sessions (avoid if possible)       │
│                                                              │
│  AWS:                                                        │
│    ALB: HTTP, path/host routing, auth, WAF, Lambda target   │
│    NLB: TCP/UDP, static IP, ultra-low latency, PrivateLink  │
│    Route 53: Global routing (latency, failover, geo, weighted)│
│    Global Accelerator: Static IPs + anycast for ALB          │
│    CloudFront: CDN + edge caching + Lambda@Edge              │
│                                                              │
│  HEALTH CHECKS:                                              │
│    Shallow /health → "am I alive?" (for LB liveness)        │
│    Deep /health/deep → check DB, cache, deps (for readiness)│
│    Unhealthy: 2 failures, Healthy: 3 successes, Interval: 30s│
│    Graceful drain: 300s deregistration delay                  │
│                                                              │
│  SCALING:                                                    │
│    Auto Scaling + ALB: Target CPU 60%, step scaling for spikes│
│    ECS Fargate: Fastest (10-30s container startup)           │
│    EC2 ASG: Slower (60-120s instance launch)                 │
│                                                              │
│  PATTERNS:                                                   │
│    Microservices: ALB path-based routing                     │
│    Multi-region: Route 53 latency → ALB per region           │
│    Canary: ALB weighted target groups (95/5)                 │
│    WebSocket: ALB sticky sessions or consistent hashing       │
│    Internal: Private ALB in VPC (no internet access)         │
│    Static IP needed: NLB or ALB + Global Accelerator         │
│                                                              │
│  AVOID:                                                      │
│    Sticky sessions (use Redis/DynamoDB for sessions)         │
│    DNS-only LB (too slow failover)                           │
│    Single AZ (always multi-AZ)                               │
│    CLB (use ALB or NLB instead — CLB is legacy)              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 12: Sharding & Partitioning** — Consistent hashing for data distribution
- **Topic 15: Rate Limiting** — Rate limit at LB level (ALB + WAF)
- **Topic 19: CDN & Edge Caching** — CloudFront in front of ALB
- **Topic 37: WebSocket & Real-Time** — WebSocket load balancing patterns
- **[AWS Architecture Mapping](./52-architecting-on-aws-service-mapping.md)** — Nginx→ALB, HAProxy→NLB

---

*This document is part of the System Design Interview Deep Dive series.*
