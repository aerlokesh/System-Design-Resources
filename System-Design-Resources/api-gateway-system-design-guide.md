# API Gateway Guide for System Design Interviews
## Complete Reference with Top HLD Interview Examples

## Table of Contents
1. [Introduction](#introduction)
2. [What is an API Gateway](#what-is-an-api-gateway)
3. [Core Features](#core-features)
4. [Architecture Patterns](#architecture-patterns)
5. [Top 10 Interview Questions with API Gateway](#top-10-interview-questions-with-api-gateway)
6. [Trade-offs & Decisions](#trade-offs--decisions)
7. [Scaling Strategies](#scaling-strategies)
8. [How to Use in Interviews](#how-to-use-in-interviews)
9. [Quick Reference](#quick-reference)

---

## Introduction

An **API Gateway** is a server that acts as a single entry point for all client requests to backend microservices.

### Why Mention in Interviews

```
API Gateway Shows You Understand:
✅ Microservices architecture
✅ Separation of concerns  
✅ Cross-cutting concerns (auth, logging, rate limiting)
✅ Scalability patterns
✅ Security best practices

ALWAYS mention for:
- Microservices systems
- Mobile backends
- Rate limiting needs
- Authentication centralization
- Multi-client applications
```

### Basic Concept

```
WITHOUT API Gateway:
┌────────┐
│ Client │─┬─→ User Service
└────────┘ ├─→ Order Service
           ├─→ Product Service
           └─→ Payment Service

Problems:
❌ Client knows all services
❌ Auth in each service
❌ No centralized rate limiting

WITH API Gateway:
┌────────┐
│ Client │─→ [API Gateway] ─┬─→ User Service
└────────┘    ↑ Auth        ├─→ Order Service
              ↑ Rate Limit  ├─→ Product Service
              ↑ Routing     └─→ Payment Service

Benefits:
✅ Single entry point
✅ Centralized auth
✅ Centralized rate limiting
✅ Simplified clients
```

---

## Core Features

### 1. Request Routing

```
Path-Based:
/api/users/*    → User Service
/api/orders/*   → Order Service
/api/products/* → Product Service

Version-Based:
/v1/api/* → Old services
/v2/api/* → New services

Geographic:
US requests → US backend
EU requests → EU backend

Canary (Weighted):
90% → Stable version
10% → New version (testing)
```

### 2. Authentication

```
Flow:
Client → Gateway (validates JWT) → Service

Gateway:
1. Validates token
2. Extracts userId
3. Checks permissions
4. Adds headers (X-User-ID: 123)
5. Routes to service

Service:
- Receives validated context
- Trusts gateway
- No auth logic needed
```

### 3. Rate Limiting

```
Levels:
- Global: 1M req/hour (system protection)
- Per-User: 1K req/hour
- Per-API: /upload (100/hr), /read (10K/hr)
- Per-IP: 10K req/hour (DDoS protection)

Returns 429 if exceeded:
{
  "error": "Rate limit exceeded",
  "retryAfter": 3600
}
```

### 4. Response Aggregation

```
Problem: Client needs data from 3 services

Without Gateway:
3 requests from client

With Gateway:
1 request from client
Gateway makes 3 parallel backend calls
Returns aggregated response

Benefit: 3 round trips → 1 round trip
```

### 5. Caching

```
Cache at gateway:
✅ Product catalog (5 min)
✅ User profiles (1 min)
✅ Config data (1 hour)

Don't cache:
❌ User-specific data
❌ POST/PUT/DELETE
❌ Real-time data

Benefit: 80% cache hit = 80% less backend load
```

---

## Architecture Patterns

### Pattern 1: Simple

```
Clients → API Gateway → Services (3-10)
Use: Small/medium apps
Capacity: 10K-50K RPS
```

### Pattern 2: Multi-Layer

```
Clients → Edge Gateway (DDoS, CDN)
        → Regional Gateway  
        → Team Gateways
        → Services

Use: Large enterprises
Benefit: Team autonomy, blast radius control
```

### Pattern 3: BFF (Backend for Frontend)

```
Web → Web Gateway (GraphQL)
Mobile → Mobile Gateway (REST)
IoT → IoT Gateway (MQTT)
     ↓
   Shared Backend Services

Use: Different client needs
Benefit: Optimized per client type
```

---

## Top 10 Interview Questions with API Gateway

### 1. URL Shortener

```
Gateway Handles:
- Rate limiting (prevent spam): 100/hour
- Caching popular URLs: 80% hit ratio
- Logging all redirects: Analytics

Routes:
POST /shorten → Create Service
GET  /{url}   → Redirect Service (cached!)

Talk: "Gateway caches redirects, reducing backend
load 80% and latency to <50ms"
```

### 2. Twitter/Social Media

```
Gateway Handles:
- JWT authentication
- Rate limiting: tweets (100/hr), follows (50/hr)
- Timeline aggregation: tweets + users + media
- Version routing: v1 vs v2 APIs

Routes:
POST /tweets → Tweet Service
GET  /timeline → Timeline Service (+ aggregation)

Talk: "Gateway aggregates timeline from 3 services,
reducing client requests from 3 to 1"
```

### 3. Uber/Ride-sharing

```
Gateway Handles:
- Multi-tenant: rider vs driver routing
- WebSocket: real-time location
- Geographic routing: nearest datacenter
- Ride orchestration: matching + pricing + notify

Routes:
Rider → Rider Service
Driver → Driver Service

Talk: "Gateway differentiates rider/driver, manages
WebSocket for tracking, orchestrates ride request
across matching, pricing, notification services"
```

### 4. Netflix/Streaming

```
Gateway Handles:
- Device-aware responses: TV (4K), Mobile (adaptive)
- Home aggregation: 4 services → 1 response
- Subscription validation
- CDN URL generation (signed, closest edge)

Routes:
GET /home → Content + User + Rec + Billing

Talk: "Gateway returns device-specific content and
aggregates home page, reducing requests from 4 to 1"
```

### 5. E-commerce/Amazon

```
Gateway Handles:
- Guest vs authenticated differentiation
- Product page aggregation: 4 services
- Payment security (PCI-DSS)
- Flash sale throttling

Routes:
GET /products/{id} → Product + Inventory + Reviews + Rec

Talk: "Gateway aggregates product page, handles payment
security, throttles during flash sales"
```

### 6. WhatsApp/Chat

```
Gateway Handles:
- WebSocket connection management
- Message routing
- Presence detection (online/offline)
- Media upload (pre-signed S3 URLs)
- Rate limiting (spam prevention)

Routes:
WebSocket for messages
POST /media → Pre-signed URL

Talk: "Gateway manages WebSocket for real-time chat,
handles media with pre-signed URLs, tracks presence"
```

### 7. Instagram/Social

```
Gateway Handles:
- Feed aggregation: posts + users + media + likes
- Image upload orchestration (S3 → processing)
- Device-specific image URLs
- Stories with 24hr TTL
- Rate limiting: posts (100/day), likes (1K/hr)

Routes:
GET /feed → Feed + User + Media + Social services

Talk: "Gateway aggregates feed, handles image uploads
with pre-signed URLs, returns device-optimized images"
```

### 8. YouTube/Video

```
Gateway Handles:
- Video upload management (chunked, pre-signed)
- Streaming URLs (manifest, CDN, multiple qualities)
- Watch page aggregation: video + creator + comments + rec
- Quality selection based on network
- Rate limiting: upload (10/day), watch (unlimited)

Routes:
POST /upload → Pre-signed URL + transcoding trigger
GET /stream → Manifest + CDN URLs

Talk: "Gateway manages uploads, returns adaptive bitrate
manifest with CDN URLs, aggregates watch page"
```

### 9. Notification System

```
Gateway Handles:
- Request validation
- Priority routing (critical vs normal)
- Multi-channel orchestration (push + email + SMS)
- Rate limiting per service
- Delivery tracking

Routes:
POST /notify → Notification Service (orchestrates channels)

Talk: "Gateway validates requests, routes by priority,
enforces rate limits to prevent spam"
```

### 10. Food Delivery

```
Gateway Handles:
- Multi-tenant: customer vs delivery vs restaurant
- Location-based routing
- Order orchestration: 7 services involved
- WebSocket for real-time tracking
- Rate limiting: orders (10/hr), browse (1K/hr)

Routes:
Customer → Customer Service
Delivery → Delivery Service
Restaurant → Restaurant Service

Talk: "Gateway routes by user type, orchestrates complex
order flow across 7 services, manages real-time tracking"
```

---

## Trade-offs & Decisions

### 1. Is Gateway a Bottleneck?

```
Concern: Single point of failure, performance limit

Mitigation:
✅ Horizontal scaling (20+ instances)
✅ Load balancer in front
✅ Caching (80% hits)
✅ Async logging
✅ Auto-scaling

Performance:
- NGINX: 50K+ RPS/instance
- Kong: 20K+ RPS/instance
- AWS API GW: Millions RPS
- Latency add: 1-5ms

Conclusion: Benefits >> Concerns
```

### 2. Smart vs Dumb Gateway

```
Prefer DUMB Gateway:
✅ Simple routing
✅ Auth/rate limit only
✅ No business logic
✅ Fast, maintainable

Avoid SMART Gateway:
❌ Business logic
❌ Complex transformations
❌ Heavy processing
❌ Becomes bottleneck

Best Practice: Thin gateway, thick services
```

### 3. Centralized vs Distributed

```
Centralized (Common):
Single gateway for all services
+ Simpler
- Potential bottleneck (mitigated by scaling)

Distributed (Enterprise):
Gateway per team/domain
+ Team autonomy
- Duplication

Hybrid (Best):
Edge gateway + Team gateways
```

---

## Scaling Strategies

### Horizontal Scaling

```
Target: 100K RPS

Calculation:
- Per gateway: 5K RPS
- Needed: 20 gateways
- Buffer (1.5x): 30 gateways
- Multi-region: 10 per region (3 regions)

Architecture:
DNS → Regional LBs → Gateway clusters → Services

Auto-scaling:
- Scale out: CPU > 70%
- Scale in: CPU < 30%
- Min: 3 instances (HA)
- Max: 100 instances
```

### Caching Strategy

```
Cache Configuration:
/api/products → 5 min TTL, 90% hit
/api/users → 1 min TTL, 80% hit
/api/config → 1 hour TTL, 99% hit

Impact:
- Backend load: -80%
- Latency: 100ms → 10ms
- Cost: -60%
```

---

## How to Use in Interviews

### When to Introduce

```
Mention API Gateway when you identify:
- Microservices (✓ Always)
- Need auth centralization (✓ Common)
- Rate limiting needed (✓ Common)
- Multiple clients (✓ Mobile + Web)
- Response aggregation (✓ BFF pattern)

Introduction:
"I'll use an API Gateway as the single entry point.
It centralizes authentication, rate limiting, and
routes to appropriate backend services."
```

### Interview Framework (40 min)

```
Minutes 5-10: Requirements + High-Level
- Identify need for gateway
- Draw architecture with gateway
- List key responsibilities

Minutes 15-25: Deep Dive
- Explain auth flow
- Discuss rate limiting strategy
- Show caching approach
- Describe aggregation (if applicable)

Minutes 30-35: Scaling
- Horizontal scaling strategy
- Performance numbers
- Handle bottleneck concerns

Minutes 35-40: Trade-offs
- Gateway overhead vs benefits
- Alternative approaches
- Monitoring strategy
```

---

## Quick Reference

### Top Interview Scenarios Summary

```
┌─────────────────┬──────────────────────────────────┐
│ System          │ Gateway Role                     │
├─────────────────┼──────────────────────────────────┤
│ URL Shortener   │ Rate limit, cache, logging       │
├─────────────────┼──────────────────────────────────┤
│ Twitter         │ Auth, rate limit, aggregation    │
├─────────────────┼──────────────────────────────────┤
│ Uber            │ Multi-tenant, WebSocket, geo     │
├─────────────────┼──────────────────────────────────┤
│ Netflix         │ Device-aware, CDN, subscription  │
├─────────────────┼──────────────────────────────────┤
│ E-commerce      │ Guest/auth, payment, flash sales │
├─────────────────┼──────────────────────────────────┤
│ WhatsApp        │ WebSocket, media, presence       │
├─────────────────┼──────────────────────────────────┤
│ Instagram       │ Feed aggregation, image optimize │
├─────────────────┼──────────────────────────────────┤
│ YouTube         │ Upload orchestration, streaming  │
├─────────────────┼──────────────────────────────────┤
│ Notifications   │ Priority routing, multi-channel  │
├─────────────────┼──────────────────────────────────┤
│ Food Delivery   │ Multi-tenant, order orchestration│
└─────────────────┴──────────────────────────────────┘
```

### Key Features Checklist

```
□ Request Routing (path, header, method-based)
□ Authentication (JWT, OAuth, API keys)
□ Authorization (RBAC, permissions)
□ Rate Limiting (global, user, API, IP)
□ Response Aggregation (BFF pattern)
□ Caching (GET requests, TTL-based)
□ Protocol Translation (REST↔gRPC, GraphQL)
□ Circuit Breaker (prevent cascading failures)
□ Service Discovery (dynamic routing)
□ Logging & Monitoring (centralized)
□ SSL Termination (HTTPS)
□ CORS Handling
□ Request/Response Transformation
```

### Popular Technologies

```
Open Source:
- Kong: Plugin-based, Lua, high performance
- NGINX: Fast, lightweight, mature
- Apache APISIX: Cloud-native, dynamic

Cloud Managed:
- AWS API Gateway: Serverless, auto-scale
- Google Cloud Endpoints: GCP integration
- Azure API Management: Enterprise features

Choose based on:
- Existing infrastructure
- Team expertise
- Scale requirements
- Budget
```

### Interview Success Pattern

```
1. Identify Need (Minute 5)
   "We have multiple microservices, so I'll use
   an API Gateway for centralized concerns"

2. Draw Architecture (Minute 10)
   [Clients] → [API Gateway] → [Services]

3. Explain Features (Minute 20)
   "Gateway handles: auth (JWT validation),
   rate limiting (per-user), caching (GET requests),
   and aggregation (timeline from 3 services)"

4. Address Concerns (Minute 35)
   "Gateway could be bottleneck, so I'll deploy
   20+ instances with auto-scaling. Adds ~5ms
   latency but provides major benefits"

5. Show Numbers (Throughout)
   "Each gateway handles 5K RPS, need 20 for
   100K RPS, cache hit ratio 80%"
```

### Common Mistakes

```
❌ "I'll use API Gateway" (no explanation why)
✅ "I'll use API Gateway to centralize auth and rate limiting"

❌ "Gateway handles everything" (too much responsibility)
✅ "Gateway handles routing and cross-cutting, services own logic"

❌ "No bottleneck concerns" (ignoring reality)
✅ "Potential bottleneck, mitigated by horizontal scaling"

❌ Adding business logic to gateway
✅ Keep gateway thin, logic in services

❌ Forgetting to scale gateway
✅ Plan for 20+ instances, auto-scaling
```

---

## Conclusion

API Gateway is essential in modern microservices architecture. In interviews, demonstrate you understand:

**Core Value:**
- Single entry point
- Centralized cross-cutting concerns
- Simplified client logic
- Service decoupling

**Key Features:**
- Authentication & Authorization
- Rate Limiting
- Request Routing
- Response Aggregation (when needed)
- Caching
- Monitoring

**Always Discuss:**
- WHY using gateway (specific benefits)
- HOW it scales (horizontal, auto-scale)
- WHAT concerns (bottleneck, latency)
- HOW mitigated (caching, async, redundancy)

**Remember:**
- Mention in ALL microservices interviews
- Keep gateway "dumb" (thin layer)
- Show specific numbers (RPS, latency, cache hits)
- Explain trade-offs clearly

Master these concepts and you'll confidently use API Gateway in any system design interview!

Good luck!
