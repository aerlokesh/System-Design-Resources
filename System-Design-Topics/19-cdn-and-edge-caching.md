# 🎯 Topic 19: CDN & Edge Caching

> **System Design Interview — Deep Dive**
> Comprehensive coverage of CDN architecture, pull-through vs push caching, cache hit ratios, content-addressable URLs, edge computing, multi-CDN strategies, and how to articulate CDN decisions in interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [How CDNs Work — Pull-Through Caching](#how-cdns-work--pull-through-caching)
3. [Push vs Pull CDN](#push-vs-pull-cdn)
4. [Cache Hit Ratio — The Key Metric](#cache-hit-ratio--the-key-metric)
5. [Content-Addressable URLs](#content-addressable-urls)
6. [Cache-Control Headers](#cache-control-headers)
7. [CDN Invalidation](#cdn-invalidation)
8. [Edge Computing](#edge-computing)
9. [CDN for Dynamic Content](#cdn-for-dynamic-content)
10. [Multi-CDN Strategy](#multi-cdn-strategy)
11. [CDN Security — DDoS & WAF](#cdn-security--ddos--waf)
12. [Popular CDN Providers](#popular-cdn-providers)
13. [Cost Model](#cost-model)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **Content Delivery Network (CDN)** caches content at **edge locations** worldwide, serving users from the geographically nearest point. This reduces latency from 200ms (cross-continent origin fetch) to 15ms (local edge cache).

```
Without CDN:
  User in Tokyo → Request → Origin in US-East (200ms round-trip)
  Every request crosses the Pacific Ocean.

With CDN:
  User in Tokyo → Request → CDN Edge in Tokyo (15ms)
    Cache HIT (95%): Serve from edge → 15ms
    Cache MISS (5%): Fetch from origin (200ms) → cache at edge → serve
  
  Subsequent Tokyo users: 15ms (cached at edge)
```

---

## How CDNs Work — Pull-Through Caching

```
First request (cache MISS):
  1. User in Tokyo → CDN Edge (Tokyo) → cache check → MISS
  2. CDN Edge → Origin Server (US-East) → fetch content (200ms)
  3. CDN Edge caches content locally
  4. CDN Edge returns content to user
  Total: ~250ms

Subsequent requests (cache HIT):
  1. User in Tokyo → CDN Edge (Tokyo) → cache check → HIT
  2. CDN Edge returns cached content
  Total: ~15ms

With 95% hit ratio:
  95% of requests: 15ms
  5% of requests: 250ms
  Average: 0.95 × 15 + 0.05 × 250 = 26.75ms (vs 200ms without CDN)
```

---

## Push vs Pull CDN

| Aspect | Pull (Origin Pull) | Push (Upload) |
|---|---|---|
| **How** | CDN fetches from origin on cache miss | You upload content to CDN proactively |
| **First request** | Cache miss → fetch from origin | Already cached → instant |
| **Best for** | Dynamic/popular content | Large files, known content |
| **Origin load** | Origin gets miss traffic | No origin during serving |
| **Complexity** | Simple (CDN manages caching) | Complex (you manage uploads) |
| **Use case** | Website assets, API responses | Video segments, software downloads |

### Interview Script
> *"I'd serve all media through CloudFront CDN with pull-through caching. When the first user in Tokyo requests an image, CloudFront checks its Tokyo edge cache (miss), fetches from S3 origin in us-east-1 (200ms), caches at the Tokyo edge, and returns to the user. Every subsequent request from Tokyo hits the edge cache — 15ms instead of 200ms. With a 95% cache hit ratio across our 100 billion monthly image requests, the CDN handles 95 billion requests locally. Only 5 billion hit S3 origin."*

---

## Cache Hit Ratio — The Key Metric

### What Is It
```
Cache Hit Ratio = (Requests served from cache) / (Total requests)

95% hit ratio with 100B monthly requests:
  Cache hits: 95B requests served from edge (15ms each)
  Cache misses: 5B requests served from origin (200ms each)
  Origin load reduction: 20x (from 100B to 5B)
```

### Factors That Affect Hit Ratio

| Factor | Higher Ratio | Lower Ratio |
|---|---|---|
| **TTL** | Long TTL (hours/days) | Short TTL (seconds) |
| **Content type** | Static (images, CSS, JS) | Dynamic (API responses) |
| **URL structure** | Consistent (same URL = same content) | Query params vary per user |
| **Content popularity** | Popular (many users, same content) | Niche (few users, unique content) |
| **Edge location count** | More edges = more local caches | Fewer edges = more misses |

### Typical Hit Ratios
```
Static assets (images, CSS, JS):  95-99%
Video segments:                    90-95%
API responses (cacheable):         60-80%
Personalized content:              0-20% (not worth caching at CDN)
```

---

## Content-Addressable URLs

### The Immutable Content Pattern
```
URL includes content hash → content at URL never changes:
  cdn.example.com/images/abc123def456.jpg  (hash of file content)

When user uploads new photo:
  New hash → new URL: cdn.example.com/images/789ghi012jkl.jpg
  Old URL stays cached forever (nobody requests it)
  New URL fetched fresh on first access

Benefits:
  ✅ Cache-Control: max-age=31536000 (1 year)
  ✅ Zero invalidation needed
  ✅ Zero stale content
  ✅ Maximum hit ratio
```

### Build-Time Asset Hashing
```
Webpack output:
  app.3f8a2b1c.js   (hash changes when code changes)
  styles.7d2e9f4a.css

HTML references:
  <script src="cdn.example.com/js/app.3f8a2b1c.js"></script>

Deployment:
  1. Build new JS → new hash → app.5e6c7d8f.js
  2. Deploy HTML referencing new URL
  3. Old URL stays cached (still works if old HTML is cached)
  4. New URL fetched fresh → cached for 1 year
```

### Interview Script
> *"For cache invalidation at the CDN, I use content-addressable URLs: `cdn.example.com/images/{content_hash}.jpg`. The hash changes when the content changes, making each URL immutable. I set Cache-Control to `max-age=31536000` (1 year) because the content at that URL never changes. When a user updates their profile photo, the URL changes. Zero invalidation complexity, zero stale content, and the CDN cache hit ratio stays above 95%."*

---

## Cache-Control Headers

### Key Headers
```http
Cache-Control: public, max-age=31536000, immutable
  public:    CDN and browsers can cache
  max-age:   Cache for 1 year (31536000 seconds)
  immutable: Don't even revalidate (content never changes at this URL)

Cache-Control: private, max-age=0, no-cache
  private:   Only browser can cache (not CDN)
  no-cache:  Must revalidate with origin before serving
  Use for:   Personalized content, auth tokens

Cache-Control: public, max-age=60, stale-while-revalidate=300
  max-age=60:                Fresh for 60 seconds
  stale-while-revalidate=300: After 60s, serve stale while refreshing in background
  Use for:   Dynamic content that can be slightly stale (API responses)
```

### Strategy by Content Type
| Content | Cache-Control | Reasoning |
|---|---|---|
| **Images (hashed URL)** | `public, max-age=31536000, immutable` | Content never changes at this URL |
| **CSS/JS (hashed)** | `public, max-age=31536000, immutable` | Build hash ensures uniqueness |
| **HTML pages** | `public, max-age=60, stale-while-revalidate=300` | Need to pick up new asset URLs |
| **API GET responses** | `public, max-age=10, stale-while-revalidate=30` | Short freshness, soft staleness |
| **User-specific data** | `private, no-store` | Never cache at CDN |
| **Auth responses** | `no-store` | Security-sensitive |

---

## CDN Invalidation

### Strategy 1: Versioned URLs (Preferred)
No invalidation needed. Content-addressed URLs are immutable.

### Strategy 2: Purge API
```python
# CloudFront invalidation
cloudfront.create_invalidation(
    DistributionId='EDFDVBD6EXAMPLE',
    InvalidationBatch={
        'Paths': {'Quantity': 1, 'Items': ['/api/products/123']},
        'CallerReference': str(time.time())
    }
)
# Propagation time: 5-15 minutes to ALL edge locations
# Cost: First 1000/month free, then $0.005/path
```

### Strategy 3: Surrogate Keys (Fastly)
```
Response: Surrogate-Key: product-123 category-electronics
Purge:    fastly.purge_key("product-123")
Result:   All pages tagged with product-123 purged instantly
```

### Strategy 4: Short TTL + Stale-While-Revalidate
```
Cache-Control: max-age=60, stale-while-revalidate=300
  - Serves cached content for 60 seconds (fresh)
  - After 60s, serves stale while refreshing in background
  - User always gets fast response
  - Content refreshed within 60 seconds of change
```

---

## Edge Computing

### What It Is
Running application logic at CDN edge locations, not just caching static content.

### Use Cases
| Use Case | Implementation | Benefit |
|---|---|---|
| **A/B testing** | Edge selects variant | No origin round-trip for routing |
| **Geo-routing** | Edge redirects by location | Localized content without origin |
| **Auth token validation** | Edge validates JWT | Reject unauthorized at edge |
| **URL rewriting** | Edge transforms URLs | Clean URLs without origin |
| **Image optimization** | Edge resizes/converts | On-demand format/size |
| **Bot detection** | Edge analyzes patterns | Block bots before they hit origin |

### Technologies
- **CloudFront Functions**: Lightweight, < 1ms, JavaScript.
- **Lambda@Edge**: Full Lambda, < 5s, Node.js/Python.
- **Cloudflare Workers**: V8 isolates, global, < 50ms.
- **Fastly Compute@Edge**: Wasm-based, < 100μs startup.

---

## CDN for Dynamic Content

### Can CDNs Cache API Responses?
```
Yes, with short TTL:
  GET /api/products/123 → Cache-Control: public, max-age=60
  Product data changes rarely → 60-second staleness is fine
  Hit ratio: 60-80% for popular products

  GET /api/feed → Cache-Control: private, no-cache
  Feed is personalized → cannot cache at CDN
  
Strategy: Cache what's cacheable, pass through what's not.
```

### Connection Reuse
Even for non-cacheable requests, CDN provides value:
```
Without CDN:
  User → TLS handshake (100ms) → Origin (200ms) = 300ms

With CDN:
  User → TLS handshake to nearby edge (20ms) → 
  Edge → persistent connection to origin (50ms) = 70ms

CDN maintains persistent connections to origin.
Eliminates TLS handshake latency for every request.
Benefit even with 0% cache hit ratio: 4x latency reduction.
```

---

## Multi-CDN Strategy

### Why Multiple CDNs
```
Single CDN risks:
  - CDN outage → global service disruption
  - CDN regional issue → users in that region affected
  - Vendor lock-in → limited negotiation leverage

Multi-CDN benefits:
  - Failover: CDN A down → route to CDN B
  - Performance: Route each user to the fastest CDN
  - Cost: Negotiate better rates with competition
  - Coverage: CDN A better in Asia, CDN B better in Europe
```

### Implementation
```
DNS-based routing:
  cdn.example.com → CNAME → multi-cdn-router.example.com
  Router checks: latency, availability, cost
  Returns: Best CDN endpoint for this user's location

Health checking:
  Monitor all CDN endpoints from multiple locations
  If CDN A edge in Tokyo fails → route Tokyo users to CDN B
  Failover time: < 30 seconds (DNS TTL)
```

---

## CDN Security — DDoS & WAF

### DDoS Mitigation
```
CDN absorbs attack traffic at the edge:
  Attacker → 100 Gbps of traffic → CDN (200+ PoPs, 100+ Tbps capacity)
  CDN absorbs, filters, and blocks malicious traffic
  Only legitimate traffic reaches origin

Without CDN:
  Attacker → 100 Gbps → Your origin (10 Gbps capacity) → OVERWHELMED
```

### Web Application Firewall (WAF)
```
CDN edge inspects requests:
  Block: SQL injection, XSS, known bot signatures
  Rate limit: Per-IP request rate limiting at edge
  Geo-block: Block traffic from specific countries
  
Applied at edge → malicious requests never reach origin.
```

---

## Popular CDN Providers

| Provider | Strengths | Edge Locations | Pricing Model |
|---|---|---|---|
| **CloudFront** | AWS integration, Lambda@Edge | 400+ | Per-request + bandwidth |
| **Cloudflare** | DDoS protection, Workers, free tier | 300+ | Flat-rate plans |
| **Fastly** | Real-time purge, Compute@Edge | 80+ | Per-request + bandwidth |
| **Akamai** | Enterprise, largest network | 4000+ | Enterprise contracts |
| **Google Cloud CDN** | GCP integration | 130+ | Per-bandwidth |

---

## Cost Model

```
CloudFront pricing example:
  First 10 TB/month:     $0.085/GB
  Next 40 TB/month:      $0.080/GB
  Next 100 TB/month:     $0.060/GB
  
  HTTPS requests:        $0.0100 per 10,000 requests
  Invalidation:          $0.005 per path (first 1000/month free)

Example: 50TB/month, 10B requests:
  Bandwidth: 10TB × $0.085 + 40TB × $0.080 = $4,050
  Requests:  10B / 10,000 × $0.01 = $10,000
  Total: ~$14,050/month

vs serving from origin:
  50TB bandwidth from EC2: $4,500 (data transfer)
  + server capacity to handle 10B requests: $50,000+/month
  CDN saves $36,000+/month in server costs
```

---

## Interview Talking Points & Scripts

### CDN for Media
> *"I'd serve all media through CloudFront CDN with pull-through caching. 95% cache hit ratio means the CDN handles 95B of 100B monthly requests locally. Only 5B hit S3 origin. That's a 20x reduction in origin load and 10x latency improvement."*

### Content-Addressable URLs
> *"For cache invalidation at the CDN, I use content-addressable URLs. The hash changes when content changes. Cache-Control set to 1 year. Zero invalidation complexity, zero stale content."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Not mentioning CDN for media-heavy systems
### ❌ Mistake 2: Active CDN invalidation instead of versioned URLs
### ❌ Mistake 3: Trying to cache personalized content at CDN
### ❌ Mistake 4: Not mentioning cache hit ratio and its impact
### ❌ Mistake 5: CDN only for static content (also helps dynamic via connection reuse)

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              CDN & EDGE CACHING CHEAT SHEET                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CORE: Cache at 200+ edge locations worldwide                │
│  LATENCY: 200ms (origin) → 15ms (edge)                      │
│  HIT RATIO: 95%+ for static, 60-80% for cacheable API       │
│                                                              │
│  INVALIDATION: Versioned URLs (PREFERRED)                    │
│    Content-addressed: hash in URL → immutable                │
│    max-age=31536000 (1 year) → zero invalidation             │
│                                                              │
│  DYNAMIC CONTENT: short TTL + stale-while-revalidate         │
│  PERSONALIZED: private, no-cache (don't CDN-cache)           │
│                                                              │
│  EDGE COMPUTING: A/B testing, auth, geo-routing at edge      │
│  SECURITY: DDoS absorption, WAF at edge                      │
│                                                              │
│  COST: CDN typically saves 50-80% vs scaling origin          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
