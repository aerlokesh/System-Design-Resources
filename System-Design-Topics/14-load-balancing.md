# 🎯 Topic 14: Load Balancing

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Layer 4 vs Layer 7 load balancing, algorithms (round-robin, least connections, consistent hashing), health checks, sticky sessions, TLS termination, and global load balancing.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Load Balance](#why-load-balance)
3. [Layer 4 vs Layer 7 Load Balancing](#layer-4-vs-layer-7-load-balancing)
4. [Load Balancing Algorithms](#load-balancing-algorithms)
5. [Health Checks](#health-checks)
6. [Sticky Sessions](#sticky-sessions)
7. [TLS/SSL Termination](#tlsssl-termination)
8. [Global Server Load Balancing (GSLB)](#global-server-load-balancing-gslb)
9. [Load Balancing for WebSocket Connections](#load-balancing-for-websocket-connections)
10. [Auto-Scaling Integration](#auto-scaling-integration)
11. [Load Balancer High Availability](#load-balancer-high-availability)
12. [Service Mesh & Client-Side Load Balancing](#service-mesh--client-side-load-balancing)
13. [Popular Load Balancers](#popular-load-balancers)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Load Balancing by System Design Problem](#load-balancing-by-system-design-problem)
17. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **load balancer** distributes incoming traffic across multiple servers to ensure no single server is overwhelmed, improving availability, reliability, and performance.

```
Without LB:
  All traffic → Server 1 (overloaded) → Server 2, 3 sit idle

With LB:
  Traffic → Load Balancer → Server 1 (33%)
                           → Server 2 (33%)
                           → Server 3 (33%)
```

---

## Why Load Balance

| Benefit | How |
|---|---|
| **Availability** | If one server dies, LB routes to healthy servers |
| **Scalability** | Add more servers behind LB to handle more traffic |
| **Performance** | Distribute load evenly → no single hot server |
| **Maintenance** | Take servers out for upgrades without downtime |
| **Security** | LB is the public-facing endpoint; backend servers are private |

---

## Layer 4 vs Layer 7 Load Balancing

### Layer 4 (Transport Layer — TCP/UDP)
```
LB sees: Source IP, destination IP, port number
LB doesn't see: HTTP headers, URL path, cookies, request body

Decision based on: IP:port tuple only
Speed: Very fast (no packet inspection)
Use: TCP-level distribution, non-HTTP protocols
```

### Layer 7 (Application Layer — HTTP)
```
LB sees: Everything — URL path, headers, cookies, query params, body
LB can: Route by URL, inject headers, terminate TLS, cache responses

Decision based on: URL path, headers, cookies, content
Speed: Slightly slower (must parse HTTP)
Use: Content-based routing, API gateway, microservices
```

### Comparison

| Feature | Layer 4 | Layer 7 |
|---|---|---|
| **Inspects** | TCP/IP headers only | Full HTTP request |
| **Routing** | IP + port | URL path, headers, cookies |
| **TLS termination** | Pass-through (or terminate) | Always terminates |
| **Content routing** | ❌ | ✅ (route /api/users → User Service) |
| **Caching** | ❌ | ✅ (cache responses) |
| **WebSocket** | ✅ (transparent) | ✅ (upgrade-aware) |
| **Speed** | Faster | Slightly slower |
| **Complexity** | Simple | More complex |
| **Use case** | Database, non-HTTP | Web apps, microservices |

### Interview Script
> *"I'd use Layer 7 load balancing at the API gateway — it inspects HTTP headers and routes based on URL path. `/api/v1/tweets` goes to the Tweet Service cluster, `/api/v1/users` goes to the User Service cluster. Layer 7 also terminates TLS, can inject headers like `X-Request-ID` for tracing, and supports content-based routing for A/B testing — send 5% of traffic to the canary deployment."*

---

## Load Balancing Algorithms

### Round-Robin
```
Request 1 → Server A
Request 2 → Server B
Request 3 → Server C
Request 4 → Server A (repeat)

Pros: Simple, even distribution
Cons: Ignores server load; slow server gets same traffic as fast one
Best for: Stateless services with uniform request cost
```

### Weighted Round-Robin
```
Server A (weight 3): Requests 1, 2, 3
Server B (weight 2): Requests 4, 5
Server C (weight 1): Request 6
(repeat)

Use: When servers have different capacities (e.g., mixing instance sizes)
```

### Least Connections
```
New request → server with fewest active connections
Server A: 10 connections → skip
Server B: 3 connections  → CHOOSE THIS
Server C: 7 connections  → skip

Pros: Naturally balances slow vs fast servers
Cons: Slightly more overhead (must track connection counts)
Best for: Long-lived connections (WebSocket, database proxies)
```

### Least Response Time
```
New request → server with lowest average response time
Server A: avg 50ms  → CHOOSE THIS
Server B: avg 120ms → skip (probably overloaded)
Server C: avg 80ms  → skip

Pros: Routes to the actually fastest server
Cons: Requires response time tracking per server
```

### IP Hash
```
hash(client_ip) % num_servers → always same server for same client
Client 1.2.3.4 → Server A (always)
Client 5.6.7.8 → Server B (always)

Pros: Session persistence without cookies
Cons: Uneven distribution if IP distribution is skewed
Best for: Stateful services without cookie-based stickiness
```

### Consistent Hashing
```
Servers and requests mapped to a hash ring.
Each request → nearest server clockwise on ring.
Adding/removing a server only affects ~1/N of requests.

Best for: Cache layer (maintain cache locality when scaling)
```

### Algorithm Selection

| Use Case | Algorithm | Why |
|---|---|---|
| **Stateless API servers** | Round-robin | Simplest, even distribution |
| **Mixed server sizes** | Weighted round-robin | Respect capacity differences |
| **WebSocket gateways** | Least connections | Long-lived connections need balancing |
| **Cache proxies** | Consistent hashing | Preserve cache locality |
| **Latency-sensitive** | Least response time | Route to fastest server |
| **Session stickiness** | IP hash or cookie-based | Maintain session state |

### Interview Script
> *"For WebSocket connections, I'd use least-connections load balancing, not round-robin. WebSocket connections are long-lived (hours to days), so round-robin creates imbalance — servers started earlier accumulate more connections. Least-connections routes each new connection to the server with the fewest active connections, naturally balancing load."*

---

## Health Checks

### Types
```
Passive: LB detects failure from connection errors/timeouts
Active: LB periodically sends health check requests

HTTP health check:
  GET /health every 10 seconds
  200 OK → healthy
  5xx or timeout → unhealthy → remove from rotation

TCP health check:
  Attempt TCP connection every 10 seconds
  Connection succeeds → healthy
  Connection fails → unhealthy

Custom health check:
  GET /health/deep → checks database, cache, dependencies
  Returns: { "status": "healthy", "db": "ok", "redis": "ok" }
```

### Configuration
```
Interval:         10 seconds (check frequency)
Timeout:          5 seconds (max wait for response)
Unhealthy threshold: 3 failures (mark unhealthy after 3 consecutive fails)
Healthy threshold:   2 successes (mark healthy after 2 consecutive passes)
```

### Graceful Shutdown
```
1. Server signals LB: "stop sending traffic" (drain)
2. LB stops routing NEW requests to this server
3. Server finishes in-flight requests (drain period: 30 seconds)
4. Server shuts down cleanly

No dropped requests, no errors during deployment.
```

---

## Sticky Sessions

### When Needed
- Server holds session state in memory (not in Redis).
- WebSocket connections tied to specific server.
- Shopping cart stored in server-local memory.

### Implementation
```
Cookie-based:
  LB sets cookie: Set-Cookie: SERVERID=server-a
  Subsequent requests include cookie → routed to server-a

IP-based:
  hash(client_ip) → always same server
  Problem: Users behind NAT share an IP → uneven distribution

Application-generated:
  Server includes session token → LB maps token to server
```

### Why to Avoid Sticky Sessions
```
Problems:
  - Uneven load (some servers get more "sticky" users)
  - Scaling difficulty (can't remove server with active sessions)
  - Failover complexity (session lost if server dies)

Better approach:
  Store session in Redis (shared state).
  Any server can handle any request (stateless).
  No need for sticky sessions.
```

---

## TLS/SSL Termination

### What It Is
The LB decrypts HTTPS traffic, so backend servers receive plain HTTP.

```
Client ──HTTPS──→ Load Balancer ──HTTP──→ Backend Servers
                  (terminates TLS)         (plain HTTP)
```

### Benefits
- **Offloads CPU**: TLS decryption is CPU-intensive; offload from app servers.
- **Certificate management**: One place to manage SSL certificates.
- **HTTP inspection**: LB can read headers, cookies, URLs for routing.
- **Simplified backends**: Servers don't need TLS configuration.

### When NOT to Terminate at LB
- **End-to-end encryption required**: Compliance (PCI-DSS) may require TLS to backend.
- **Solution**: TLS re-encryption (LB terminates → re-encrypts → backend).

---

## Global Server Load Balancing (GSLB)

### DNS-Based Routing
```
User in Tokyo → DNS query → geo-DNS returns ap-northeast-1 IP
User in Paris → DNS query → geo-DNS returns eu-west-1 IP
User in NYC   → DNS query → geo-DNS returns us-east-1 IP
```

### AWS Route 53 Routing Policies
| Policy | Description | Use Case |
|---|---|---|
| **Geolocation** | Route based on user's location | Regional content |
| **Latency** | Route to lowest-latency region | Performance |
| **Weighted** | Distribute by percentage (90/10) | Canary deployments |
| **Failover** | Primary/secondary with health check | Disaster recovery |

### Multi-Layer Load Balancing
```
Layer 1: DNS (Route 53) → Route to nearest region
Layer 2: Regional LB (ALB/NLB) → Route to healthy server in region
Layer 3: Service mesh (Envoy) → Route to specific microservice instance
```

---

## Load Balancing for WebSocket Connections

### The Challenge
WebSocket connections are long-lived (hours/days). Standard round-robin creates imbalance.

### Solution: Least-Connections + Sticky
```
New WebSocket → least-connections algorithm → server with fewest connections
Once connected → sticky (connection persists on that server)

Scaling:
  Each server: ~65K connections (file descriptor limit)
  10M users: 10M / 65K = ~154 servers

Cross-server messaging:
  User A on Server 1 → message → User B on Server 42
  Server 1 → Redis Pub/Sub → Server 42 → WebSocket → User B
```

---

## Auto-Scaling Integration

```
LB + Auto-Scaling:
  1. Traffic increases → average CPU > 70% threshold
  2. Auto-scaler launches new instances
  3. New instances pass health check
  4. LB adds them to rotation
  5. Traffic distributed across more instances
  
  Traffic decreases → instances drained and terminated
```

### Scaling Policies
```
Target tracking: Maintain average CPU at 60%
Step scaling:    CPU 60-70% → +2 instances
                 CPU 70-80% → +5 instances
                 CPU > 80%  → +10 instances
Scheduled:       Scale up at 9 AM, scale down at 9 PM
Predictive:      ML-based forecasting of traffic patterns
```

---

## Load Balancer High Availability

### The LB as Single Point of Failure
```
Solution: Active-passive LB pair with virtual IP (VIP)

Active LB ──┐
             ├── Virtual IP (VIP) ── Clients connect to VIP
Passive LB ──┘

If Active fails → Passive takes over VIP (< 10 seconds)
Clients don't notice (same IP address)
```

### DNS-Level Redundancy
```
Multiple IPs per DNS record:
  api.example.com → 1.2.3.4 (LB 1)
                  → 5.6.7.8 (LB 2)

Client tries first IP; if failed, tries second.
```

---

## Service Mesh & Client-Side Load Balancing

### Service Mesh (Envoy, Istio, Linkerd)
```
Each service instance has a sidecar proxy:
  Service A → Envoy proxy → Envoy proxy → Service B

The proxy handles:
  - Load balancing (least connections, round robin)
  - Retry logic
  - Circuit breaking
  - Observability (metrics, tracing)
  - Mutual TLS

No separate load balancer needed between services.
```

### Client-Side Load Balancing (gRPC)
```
gRPC client maintains a list of server addresses.
Client chooses server using round-robin or pick-first.
No separate LB instance for internal gRPC calls.
```

---

## Popular Load Balancers

| LB | Type | Use Case | Notes |
|---|---|---|---|
| **AWS ALB** | Layer 7 | HTTP/HTTPS, WebSocket | Path-based routing, managed |
| **AWS NLB** | Layer 4 | TCP/UDP, ultra-low latency | Millions of requests/sec |
| **Nginx** | Layer 7 | Reverse proxy, web server | Open source, highly configurable |
| **HAProxy** | Layer 4/7 | High-performance proxy | Battle-tested, very fast |
| **Envoy** | Layer 7 | Service mesh sidecar | gRPC-native, observability |
| **Cloudflare** | Layer 7 | Global CDN + LB | DDoS protection, edge computing |
| **Traefik** | Layer 7 | Kubernetes ingress | Auto-discovery, Let's Encrypt |

---

## Interview Talking Points & Scripts

### Layer 7 for API Gateway
> *"I'd use Layer 7 load balancing at the API gateway — it inspects HTTP headers and routes based on URL path. It also terminates TLS, injects `X-Request-ID` for tracing, and supports content-based routing for A/B testing."*

### Least-Connections for WebSocket
> *"For WebSocket connections, I'd use least-connections — connections are long-lived, so round-robin creates imbalance. Least-connections naturally balances load by routing to the server with fewest active connections."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Forgetting the LB is a single point of failure
**Fix**: Mention active-passive pair or multiple LB instances.

### ❌ Mistake 2: Using round-robin for everything
**Fix**: Least-connections for long-lived connections, consistent hashing for caches.

### ❌ Mistake 3: Not mentioning health checks
**Fix**: Always describe how the LB detects failed servers (active health checks, intervals).

### ❌ Mistake 4: Sticky sessions instead of external session store
**Fix**: "I'd store sessions in Redis so any server can handle any request — no sticky sessions needed."

### ❌ Mistake 5: Not mentioning TLS termination
**Fix**: The LB terminates TLS, offloading CPU from backend servers and enabling HTTP-level routing.

---

## Load Balancing by System Design Problem

| Problem | LB Type | Algorithm | Notes |
|---|---|---|---|
| **Web API** | L7 (ALB) | Round-robin | Path-based routing to microservices |
| **Chat (WebSocket)** | L4 (NLB) | Least connections | Long-lived connections |
| **Video streaming** | CDN + L7 | Geo-based | Serve from nearest edge |
| **Database proxy** | L4 | Round-robin | PgBouncer for PostgreSQL |
| **gRPC microservices** | Client-side | Round-robin | No external LB needed |
| **Global app** | DNS (Route 53) | Latency-based | Route to nearest region |
| **Cache layer** | Consistent hashing | Hash-based | Preserve cache locality |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               LOAD BALANCING CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  LAYER 4: TCP/IP level, fast, no HTTP inspection             │
│  LAYER 7: HTTP level, content routing, TLS termination       │
│                                                              │
│  ALGORITHMS:                                                 │
│  Round-robin:      Stateless APIs (DEFAULT)                  │
│  Least connections: WebSocket, long-lived connections         │
│  Consistent hashing: Cache proxies (preserve locality)       │
│  Weighted RR:      Mixed server sizes                        │
│  IP hash:          Simple session stickiness                 │
│                                                              │
│  HEALTH CHECKS: Active HTTP every 10s, 3 failures = remove  │
│  TLS TERMINATION: At LB, backends use plain HTTP             │
│  HA: Active-passive pair with virtual IP                     │
│  GLOBAL: DNS geo/latency routing → regional LB → servers    │
│                                                              │
│  AVOID: Sticky sessions (use Redis for shared state)         │
│  PREFER: Stateless backends + external session store         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
