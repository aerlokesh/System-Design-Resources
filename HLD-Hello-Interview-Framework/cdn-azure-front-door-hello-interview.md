# Design a Content Delivery Network (CDN) — Hello Interview Framework

> **Question**: Design a CDN like Azure Front Door or Azure CDN to distribute content globally, cache assets at edge locations, and reduce latency for users worldwide — handling millions of requests per second.
>
> **Asked at**: Microsoft, Amazon (CloudFront), Google, Cloudflare
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
1. **Content Caching**: Cache static content (images, JS, CSS, videos, documents) at edge PoPs close to users. Reduce origin load.
2. **Global Routing**: Route users to the nearest/fastest edge PoP using Anycast DNS or BGP. Latency-based routing.
3. **Cache Invalidation**: Purge cached content on demand. TTL-based expiration. Cache-Control header respect.
4. **Origin Shield**: Reduce requests to origin by funneling through a mid-tier cache (shield layer). Collapse concurrent requests for same object.
5. **HTTPS/TLS**: TLS termination at the edge. Managed certificates (auto-renewal). HTTP/2, HTTP/3 (QUIC) support.
6. **Custom Domains**: Map custom domains (cdn.contoso.com) to CDN endpoint. SNI-based TLS.
7. **Analytics**: Real-time traffic stats (hits, misses, bandwidth, latency). Cache hit ratio per PoP.

#### Nice to Have (P1)
- Dynamic site acceleration (optimize non-cacheable API traffic)
- WAF integration (Web Application Firewall at the edge)
- DDoS protection
- Edge compute (run custom logic at the edge — like Azure Functions or Cloudflare Workers)
- Image optimization (resize, compress, convert format on-the-fly)
- Video streaming optimization (HLS/DASH chunked delivery)
- Geo-blocking (restrict content by country)
- Token authentication (protect content with signed URLs)
- Real-time log streaming

#### Below the Line (Out of Scope)
- Full WAF rule engine
- DNS management (Azure DNS separate)
- Origin server design
- Video transcoding

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Edge PoPs** | 200+ locations in 60+ countries | Global coverage |
| **Requests/sec** | 100M+ RPS globally | Web-scale traffic |
| **Cache hit ratio** | > 95% for static content | Minimize origin load |
| **Latency** | < 20ms to nearest edge (p50) | Near-instant content |
| **Availability** | 99.999% | CDN failure = website down |
| **Bandwidth** | 100+ Tbps aggregate capacity | Handle traffic spikes |
| **Purge propagation** | < 30 seconds global | Fast content updates |
| **TLS handshake** | < 50ms | Fast HTTPS connection |

### Capacity Estimation

```
Traffic:
  Global RPS: 100M
  Per PoP (200 PoPs): ~500K RPS average
  Peak PoP: 5M RPS
  Average response size: 50 KB
  
Bandwidth:
  Total: 100M × 50 KB = 5 TB/s = 40 Tbps
  Per PoP: 200 Gbps average
  
Cache:
  Unique objects cached globally: 50 billion
  Total cache storage: 50B × 50 KB = 2.5 EB (distributed)
  Per PoP cache: ~50-200 TB (SSD + HDD)
  
  Cache hit ratio: 95% → only 5% of requests go to origin
  Origin traffic: 5M RPS × 50 KB = 250 GB/s to origins
```

---

## 2️⃣ Core Entities

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  CDN Profile     │────▶│  Endpoint          │────▶│  Origin Group      │
│                  │     │                   │     │                    │
│ profileId        │     │ endpointId        │     │ groupId            │
│ name             │     │ hostname          │     │ origins[]          │
│ sku (Standard/   │     │ customDomains[]   │     │ healthProbe        │
│  Premium)        │     │ originGroupId     │     │ lbAlgorithm        │
│ resourceGroup    │     │ routes[]          │     │                    │
└─────────────────┘     │ cachingPolicy     │     └───────────────────┘
                         │ wafPolicy         │
                         │ compressionEnabled│     ┌───────────────────┐
                         └──────────────────┘     │  Origin            │
                                                    │                    │
┌─────────────────┐     ┌──────────────────┐     │ originId           │
│  CacheRule       │     │  PurgeRequest    │     │ hostname           │
│                  │     │                   │     │ port               │
│ ruleId           │     │ purgeId           │     │ protocol           │
│ pathPattern      │     │ paths[]           │     │ weight             │
│ ttl              │     │ status            │     │ priority           │
│ queryStringMode  │     │ completedAt       │     │ enabled            │
│ cacheKeyHeaders[]│     │ popsPurged        │     └───────────────────┘
│ compression      │     └──────────────────┘
└─────────────────┘     
                         ┌──────────────────┐
                         │  EdgePoP          │
                         │                   │
                         │ popId             │
                         │ location          │
                         │ region            │
                         │ cacheCapacity     │
                         │ currentHitRatio   │
                         │ bandwidth         │
                         └──────────────────┘
```

---

## 3️⃣ API Design

### Create CDN Endpoint
```
POST /api/v1/cdn/endpoints
Authorization: Bearer <token>

Request:
{
  "hostname": "media.contoso.com",
  "originGroup": {
    "origins": [
      { "hostname": "origin-east.contoso.com", "weight": 70 },
      { "hostname": "origin-west.contoso.com", "weight": 30 }
    ],
    "healthProbe": { "path": "/health", "intervalSec": 30 }
  },
  "cachingRules": [
    {
      "pathPattern": "/static/*",
      "ttl": 86400,
      "queryStringMode": "ignoreAll",
      "compression": true
    },
    {
      "pathPattern": "/api/*",
      "ttl": 0,
      "cacheMode": "bypass"
    }
  ]
}
```

### Purge Cache
```
POST /api/v1/cdn/endpoints/{endpointId}/purge
Authorization: Bearer <token>

Request:
{
  "paths": ["/images/logo.png", "/static/app.*.js"],
  "type": "single"       // single | wildcard | all
}

Response: 202 Accepted
{
  "purgeId": "purge_abc123",
  "status": "inProgress",
  "estimatedCompletionSec": 30
}
```

---

## 4️⃣ Data Flow

### Cache Hit (Happy Path)

```
User in Tokyo requests: GET https://media.contoso.com/images/hero.jpg
        │
        ▼
   DNS Resolution (Anycast):
     media.contoso.com → Anycast IP → routed to nearest PoP (Tokyo)
        │
        ▼
   Edge PoP (Tokyo):
     1. TLS termination (pre-loaded certificate)
     2. Parse request → compute cache key:
        key = "media.contoso.com/images/hero.jpg"
     3. Cache lookup (SSD → HDD):
        FOUND! (cache hit) → still fresh? (TTL not expired?) → YES
     4. Serve directly from cache → 200 OK
        │
        └── Total latency: 5-15ms (user ← edge PoP)
        
   Cache hit ratio: 95%+ for static content
```

### Cache Miss (Origin Fetch)

```
User in Tokyo requests: GET https://media.contoso.com/images/new-banner.jpg
        │
        ▼
   Edge PoP (Tokyo):
     1. Cache lookup → NOT FOUND (cache miss)
     2. Request coalescing: are other clients requesting the same object?
        Yes → add to wait group (only one origin request)
        No → proceed
     3. Forward to Origin Shield (regional mid-tier cache, e.g., Singapore):
        │
        ▼
   Origin Shield (Singapore):
     1. Cache lookup → NOT FOUND
     2. Forward to origin: GET https://origin-east.contoso.com/images/new-banner.jpg
        │
        ▼
   Origin Server:
     1. Generate/serve content
     2. Return with Cache-Control: public, max-age=86400
        │
        ▼
   Origin Shield (Singapore):
     1. Cache response (TTL from Cache-Control)
     2. Return to Edge PoP
        │
        ▼
   Edge PoP (Tokyo):
     1. Cache response locally
     2. Serve to all waiting clients
        │
        └── Total latency: 100-300ms (origin fetch + cache store)
        └── Subsequent requests: 5-15ms (served from edge cache)
```

---

## 5️⃣ High-Level Design

```
┌───────────────────────────────────────────────────────────────────────┐
│                         GLOBAL EDGE NETWORK                           │
│                                                                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │ PoP:    │  │ PoP:    │  │ PoP:    │  │ PoP:    │  │ PoP:    │  │
│  │ Tokyo   │  │ London  │  │ NYC     │  │ São     │  │ Sydney  │  │
│  │         │  │         │  │         │  │ Paulo   │  │         │  │
│  │ ┌─────┐ │  │ ┌─────┐ │  │ ┌─────┐ │  │ ┌─────┐ │  │ ┌─────┐ │  │
│  │ │Cache│ │  │ │Cache│ │  │ │Cache│ │  │ │Cache│ │  │ │Cache│ │  │
│  │ │SSD: │ │  │ │SSD: │ │  │ │SSD: │ │  │ │SSD: │ │  │ │SSD: │ │  │
│  │ │50TB │ │  │ │100TB│ │  │ │200TB│ │  │ │50TB │ │  │ │50TB │ │  │
│  │ │HDD: │ │  │ │HDD: │ │  │ │HDD: │ │  │ │HDD: │ │  │ │HDD: │ │  │
│  │ │200TB│ │  │ │500TB│ │  │ │1PB  │ │  │ │200TB│ │  │ │200TB│ │  │
│  │ └─────┘ │  │ └─────┘ │  │ └─────┘ │  │ └─────┘ │  │ └─────┘ │  │
│  │         │  │         │  │         │  │         │  │         │  │
│  │ TLS     │  │ TLS     │  │ TLS     │  │ TLS     │  │ TLS     │  │
│  │ WAF     │  │ WAF     │  │ WAF     │  │ WAF     │  │ WAF     │  │
│  │ Compress│  │ Compress│  │ Compress│  │ Compress│  │ Compress│  │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  │
│       └────────────┴────────────┴────────────┴────────────┘        │
│                                 │                                    │
└─────────────────────────────────┼────────────────────────────────────┘
                                  │ (cache miss)
┌─────────────────────────────────┼────────────────────────────────────┐
│                    ORIGIN SHIELD LAYER                                 │
│                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Shield:      │  │ Shield:      │  │ Shield:      │              │
│  │ US-East      │  │ EU-West      │  │ Asia-SE      │              │
│  │              │  │              │  │              │              │
│  │ Regional     │  │ Regional     │  │ Regional     │              │
│  │ cache        │  │ cache        │  │ cache        │              │
│  │ (PB-scale)   │  │ (PB-scale)   │  │ (PB-scale)   │              │
│  │              │  │              │  │              │              │
│  │ Request      │  │ Request      │  │ Request      │              │
│  │ coalescing   │  │ coalescing   │  │ coalescing   │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         └──────────────────┴──────────────────┘                      │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │ (shield miss)
┌─────────────────────────────────┼────────────────────────────────────┐
│                         ORIGIN SERVERS                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Azure Blob   │  │ App Service  │  │ Custom       │              │
│  │ Storage      │  │ / VM         │  │ Origin       │              │
│  │              │  │              │  │              │              │
│  │ Static files │  │ Dynamic API  │  │ Customer's   │              │
│  │              │  │              │  │ server       │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                    CONTROL PLANE                                      │
│                                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐     │
│  │ Config       │  │ Purge        │  │ Analytics              │     │
│  │ Manager      │  │ Orchestrator │  │ Pipeline               │     │
│  │              │  │              │  │                        │     │
│  │ Propagate    │  │ Fan-out      │  │ Real-time metrics      │     │
│  │ rules to     │  │ purge to     │  │ per PoP, per endpoint  │     │
│  │ all PoPs     │  │ all PoPs     │  │ Kafka → ClickHouse     │     │
│  └─────────────┘  └──────────────┘  └────────────────────────┘     │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Cache Key Design & Optimization

**Problem**: Two requests for the same content might have different URLs (query strings, headers, cookies). How do we maximize cache hit ratio while serving correct content?

**Solution: Configurable Cache Key with Normalization**

```
Default cache key:
  key = "{scheme}://{host}{path}?{queryString}"
  
  Problem: same image, different analytics params:
    /images/hero.jpg?utm_source=google
    /images/hero.jpg?utm_source=twitter
    → Two different cache entries for same content! (waste)

Cache Key Strategies:

1. Ignore Query String (best for static assets):
   Rule: pathPattern "/static/*" → queryStringMode: "ignoreAll"
   key = "{host}/static/app.js" (ignore ?v=123)
   
2. Include Specific Query Params:
   Rule: pathPattern "/api/products" → queryStringInclude: ["page", "sort"]
   key = "{host}/api/products?page=2&sort=price"
   Ignores: ?tracking=abc&session=xyz (irrelevant to content)

3. Vary by Header:
   Origin returns: Vary: Accept-Encoding, Accept-Language
   Cache key includes: Accept-Encoding=gzip, Accept-Language=en
   → Different cached versions for gzip vs brotli, en vs fr

4. Cache Key Normalization:
   - Lowercase the host and path
   - Sort query parameters alphabetically
   - Remove tracking parameters (utm_*, fbclid, etc.)
   - Normalize Accept-Encoding to canonical values (gzip, br, identity)
   
   Before: /Images/HERO.jpg?b=1&a=2&utm_source=fb
   After:  /images/hero.jpg?a=2&b=1

Cache Hierarchy (per PoP):
  L1: SSD cache (hot — most frequently accessed, ~50 TB)
  L2: HDD cache (warm — less frequent, ~200 TB)
  L3: Origin Shield (regional shared cache)
  L4: Origin Server

Eviction Policy:
  LRU with frequency weighting (LFU-LRU hybrid)
  Objects accessed more frequently are "stickier"
  Large objects (>100 MB) evicted first unless very popular
```

### Deep Dive 2: Cache Purge at Global Scale

**Problem**: Content owner updates an image. The old version is cached in 200+ PoPs worldwide. How to purge it in <30 seconds?

**Solution: Fan-Out Purge via Control Plane**

```
Purge Flow:

Admin: POST /purge { paths: ["/images/logo.png"] }
        │
        ▼
   Purge Orchestrator (control plane):
     1. Validate purge request
     2. Create purge job: { id, paths, status: "inProgress", createdAt }
     3. Fan-out purge command to ALL PoPs:
        
        Option A: Push via persistent gRPC streams
          Control plane maintains gRPC connection to every PoP
          Send PurgeCommand { paths: ["/images/logo.png"] }
          PoP ACKs → orchestrator tracks progress
          
        Option B: Pub/Sub broadcast
          Publish to "purge" topic (e.g., Azure Service Bus)
          Each PoP subscribes → receives purge command
        
     4. Each PoP processes locally:
        a. Hash path → find in cache index
        b. Mark as invalid (soft delete)
        c. Don't actually delete data yet (lazy cleanup)
        d. Next request for this path → treat as cache miss → fetch from origin
        e. ACK to orchestrator
     
     5. Orchestrator aggregates ACKs:
        All 200+ PoPs confirmed → status: "completed"
        
     Total time: 10-30 seconds (network + processing)

Wildcard Purge:
  Path: "/static/*" → must scan cache index for matching keys
  More expensive: O(N) where N is cached objects with prefix
  Optimization: prefix tree (trie) on cache keys → fast prefix lookup

Soft Purge (stale-while-revalidate):
  Instead of deleting: mark as "stale"
  Next request: serve stale content + background revalidate from origin
  If origin returns 304 Not Modified → promote back to fresh
  Benefit: zero-miss-penalty for consumers, origin gets time to prepare
```

### Deep Dive 3: Request Coalescing (Thundering Herd Protection)

**Problem**: A popular object expires. 10,000 concurrent requests hit the edge. Without coalescing, all 10,000 go to origin simultaneously (thundering herd). How to prevent this?

**Solution: Request Coalescing + Stale-While-Revalidate**

```
Request Coalescing:

Concurrent requests for same cache key:
  Request 1 arrives → cache miss → forward to origin (leader request)
  Request 2 arrives (same key, 50ms later) → join wait group
  Request 3 arrives (same key, 100ms later) → join wait group
  ... Request 10,000 → join wait group
  
  Origin responds to Request 1 → 
    1. Cache the response
    2. Serve to ALL 10,000 waiting requests
    3. Only 1 request hit origin instead of 10,000!

Implementation:
  In-memory map per PoP:
    pendingRequests: Map<cacheKey, Promise<Response>>
    
  On cache miss:
    if (pendingRequests.has(key)):
      return pendingRequests.get(key)  // wait for leader
    else:
      promise = fetchFromOrigin(key)
      pendingRequests.set(key, promise)
      response = await promise
      cache.set(key, response)
      pendingRequests.delete(key)
      return response

Stale-While-Revalidate:
  Cache-Control: max-age=3600, stale-while-revalidate=86400
  
  Object is 3601 seconds old (just expired):
    1. Serve stale content immediately (0 latency)
    2. Background: fetch fresh content from origin
    3. Update cache with fresh content
    4. Next request gets fresh content
  
  Object is 90,000 seconds old (beyond stale window):
    1. Cache miss → must wait for origin
    2. Use request coalescing as above

Combined protection against thundering herd:
  Layer 1: Stale-while-revalidate (prevent miss entirely)
  Layer 2: Request coalescing at edge (1 request to shield)
  Layer 3: Request coalescing at shield (1 request to origin)
  Layer 4: Origin load balancing + circuit breaker
```
