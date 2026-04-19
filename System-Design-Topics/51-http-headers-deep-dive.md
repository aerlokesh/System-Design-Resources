# 🎯 Topic 51: HTTP Headers Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering request/response headers, caching headers (Cache-Control, ETag, Last-Modified), security headers (CORS, CSP, HSTS), authentication headers (Authorization, cookies, JWT), rate limiting headers, content negotiation, custom headers for distributed systems, and how to articulate HTTP header decisions in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Request Headers — What the Client Sends](#request-headers--what-the-client-sends)
3. [Response Headers — What the Server Returns](#response-headers--what-the-server-returns)
4. [Caching Headers — The Complete Picture](#caching-headers--the-complete-picture)
5. [Security Headers](#security-headers)
6. [Authentication and Authorization Headers](#authentication-and-authorization-headers)
7. [Rate Limiting Headers](#rate-limiting-headers)
8. [Content Negotiation](#content-negotiation)
9. [Custom Headers for Distributed Systems](#custom-headers-for-distributed-systems)
10. [CORS — Cross-Origin Resource Sharing](#cors--cross-origin-resource-sharing)
11. [Compression Headers](#compression-headers)
12. [Connection and Keep-Alive Headers](#connection-and-keep-alive-headers)
13. [Headers in System Design Interviews](#headers-in-system-design-interviews)
14. [Common Interview Mistakes](#common-interview-mistakes)
15. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**HTTP headers** are metadata sent with every HTTP request and response. They control caching, authentication, content type, security policies, routing, and observability. In system design, headers are how you implement cross-cutting concerns (auth, tracing, rate limiting) without polluting the request body.

```
HTTP Request:
  GET /api/v1/users/123 HTTP/1.1
  Host: api.example.com
  Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
  Accept: application/json
  Accept-Encoding: gzip, br
  X-Request-Id: req_abc123
  X-Trace-Id: trace_xyz789
  If-None-Match: "etag_v5"

HTTP Response:
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=utf-8
  Cache-Control: private, max-age=300
  ETag: "etag_v6"
  X-RateLimit-Remaining: 97
  X-Request-Id: req_abc123
  Strict-Transport-Security: max-age=31536000
  Content-Encoding: gzip
  
  {"id": 123, "name": "Alice", ...}
```

---

## Request Headers — What the Client Sends

### Essential Request Headers

```
Host: api.example.com
  Required in HTTP/1.1. Identifies the target server (virtual hosting).
  One IP can serve multiple domains — Host header tells which one.

Authorization: Bearer <token>
  Authentication credential. Most common: Bearer token (JWT or opaque).
  Alternatives: Basic (base64 encoded user:pass), API key, AWS Signature.

Accept: application/json
  What content types the client can handle.
  Server responds with matching type (or 406 Not Acceptable).

Accept-Encoding: gzip, deflate, br
  What compression algorithms the client supports.
  Server compresses response → smaller payload → faster transfer.
  br (Brotli): Best compression ratio (20-30% better than gzip).

Content-Type: application/json
  For POST/PUT requests: What format is the request body?
  application/json: JSON body
  application/x-www-form-urlencoded: Form data
  multipart/form-data: File uploads

User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 17_0...)
  Client software identity. Used for:
  - Analytics (% mobile vs desktop)
  - Bot detection (known bot User-Agents)
  - Feature flags (serve different experience by client)

Cookie: session_id=abc123; theme=dark
  Client sends stored cookies back to server.
  Used for: Session management, preferences, tracking.
```

### Conditional Request Headers (Caching)

```
If-None-Match: "etag_v5"
  "I have version etag_v5 cached. Has it changed?"
  Server responds: 304 Not Modified (save bandwidth) or 200 with new data.

If-Modified-Since: Wed, 15 Mar 2025 10:00:00 GMT
  "I have data from March 15. Anything newer?"
  Server responds: 304 Not Modified or 200 with updated data.

If-Match: "etag_v5"
  For PUT/PATCH: "Only update if server version matches my version"
  Prevents lost updates (optimistic concurrency control via HTTP).
  Server responds: 200 (updated) or 412 Precondition Failed (stale).
```

---

## Response Headers — What the Server Returns

### Essential Response Headers

```
Content-Type: application/json; charset=utf-8
  What format is the response body.
  ALWAYS include charset for text-based responses.

Content-Length: 1234
  Size of response body in bytes.
  Allows client to show progress bars, detect truncated responses.

Content-Encoding: gzip
  Response body is compressed with this algorithm.
  Client must decompress before parsing.

Location: /api/v1/users/456
  For 201 Created: URL of the newly created resource.
  For 301/302 redirects: Where to go next.

Retry-After: 60
  For 429 (Too Many Requests) or 503 (Service Unavailable):
  "Try again after 60 seconds."

X-Request-Id: req_abc123
  Echo back the client's request ID (for correlation in logs).
  If client didn't send one, server generates and returns one.
```

---

## Caching Headers — The Complete Picture

### Cache-Control (The Most Important Caching Header)

```
Cache-Control directives (response):

  public, max-age=3600
    Any cache (browser, CDN, proxy) can store this for 1 hour.
    Use for: Public content (product images, CSS, JS, public API responses).

  private, max-age=300
    Only the browser can cache (not CDN/proxy). Cached for 5 minutes.
    Use for: User-specific data (profile, cart, dashboard).

  no-cache
    Cache CAN store it, but MUST revalidate with server before serving.
    Every request → server check (If-None-Match) → 304 or fresh data.
    Use for: Data that changes unpredictably but benefits from conditional caching.

  no-store
    NEVER cache. Not in browser, not in CDN, not anywhere.
    Use for: Sensitive data (bank balance, PII, auth tokens).

  max-age=0, must-revalidate
    Treat cached version as immediately stale → always revalidate.
    Similar to no-cache but stricter (must-revalidate means DON'T serve stale).

  s-maxage=86400
    CDN/shared cache max age (overrides max-age for CDN).
    "Browser caches for max-age=300, CDN caches for s-maxage=86400."
    Use for: Content that CDN should cache longer than browser.

  stale-while-revalidate=60
    Serve stale content while fetching fresh in background (60 second window).
    User gets instant response (stale), next request gets fresh.
    Use for: Non-critical data where slight staleness is OK for speed.

  immutable
    Content will NEVER change (versioned URLs like /app.v3.js).
    Browser won't even check for updates (no conditional requests).
    Use for: Static assets with content hash in URL.
```

### ETag (Entity Tag)

```
Server generates a unique identifier for the content version:

  Response:
    ETag: "a1b2c3d4"
    Cache-Control: private, max-age=0, must-revalidate

  Next request (after cache expires):
    If-None-Match: "a1b2c3d4"

  Server checks:
    If current ETag == "a1b2c3d4" → 304 Not Modified (no body, save bandwidth)
    If current ETag != "a1b2c3d4" → 200 OK with full new response + new ETag

ETag generation strategies:
  Hash of content: ETag = md5(response_body) — always accurate
  Version number: ETag = "v5" — from database version column
  Last modified timestamp: ETag = "1742234400" — timestamp-based
  
Strong vs Weak ETags:
  "a1b2c3d4" — Strong: Byte-for-byte identical
  W/"a1b2c3d4" — Weak: Semantically equivalent (minor differences OK)
```

### Last-Modified / If-Modified-Since

```
Response:
  Last-Modified: Wed, 15 Mar 2025 10:00:00 GMT

Next request:
  If-Modified-Since: Wed, 15 Mar 2025 10:00:00 GMT

Server checks:
  If resource modified after that date → 200 with new data
  If NOT modified → 304 Not Modified

ETag vs Last-Modified:
  ETag: More precise (content-based), works for sub-second changes
  Last-Modified: Simpler, 1-second resolution
  Best practice: Use BOTH (ETag takes priority if both present)
```

### CDN Caching Strategy Example

```
Static assets (CSS, JS, images with content hash):
  Cache-Control: public, max-age=31536000, immutable
  URL: /static/app.3f8a2b.js (hash in filename = unique per version)
  → Cached forever. New deploy = new hash = new URL = CDN fetches new version.

API responses (user-specific):
  Cache-Control: private, no-cache
  ETag: "user_123_v42"
  → Browser caches, but always revalidates. If unchanged → 304 (fast).

Public API (product listing):
  Cache-Control: public, s-maxage=60, stale-while-revalidate=30
  → CDN caches for 60 seconds. After 60s, serves stale while refreshing.
  → Users always get fast response. Data at most 90 seconds old.
```

---

## Security Headers

### Strict-Transport-Security (HSTS)

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload

  "For the next year, ONLY access this site over HTTPS. Never HTTP."
  Prevents: SSL stripping attacks, accidental HTTP access.
  
  includeSubDomains: Apply to all subdomains too.
  preload: Submit to browser HSTS preload list (built into Chrome/Firefox).
  
  ALWAYS set on production APIs. No reason not to.
```

### Content-Security-Policy (CSP)

```
Content-Security-Policy: default-src 'self'; script-src 'self' cdn.example.com; img-src *

  Controls what resources the browser is allowed to load.
  Prevents: XSS (Cross-Site Scripting) — injected scripts can't execute.
  
  default-src 'self': Only load resources from same origin by default.
  script-src 'self' cdn.example.com: Scripts only from self or CDN.
  img-src *: Images from anywhere (needed for user-uploaded content).
  
  For APIs (not web pages): Less relevant, but still good practice.
```

### X-Content-Type-Options

```
X-Content-Type-Options: nosniff

  Prevents MIME type sniffing.
  Browser respects Content-Type header strictly.
  Prevents: Attacks where attacker uploads .txt that browser executes as JS.
  
  ALWAYS set. One header, zero downside.
```

### X-Frame-Options

```
X-Frame-Options: DENY (or SAMEORIGIN)

  Prevents page from being embedded in an iframe.
  Prevents: Clickjacking attacks.
  
  DENY: Never allow framing.
  SAMEORIGIN: Only allow framing by same origin.
```

### Other Security Headers

```
X-XSS-Protection: 0
  Disable browser's built-in XSS filter (use CSP instead — more reliable).

Referrer-Policy: strict-origin-when-cross-origin
  Control what referrer info is sent with outgoing requests.
  Prevents: Leaking sensitive URL paths to third parties.

Permissions-Policy: camera=(), microphone=(), geolocation=()
  Disable browser APIs you don't need.
  Prevents: Malicious scripts accessing camera/mic.
```

---

## Authentication and Authorization Headers

### Bearer Token (JWT)

```
Request:
  Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

  JWT decoded:
    Header: {"alg": "RS256", "typ": "JWT"}
    Payload: {"sub": "user_123", "exp": 1742234400, "role": "admin"}
    Signature: [cryptographic signature]

  Server validates:
    1. Verify signature (using public key)
    2. Check exp (not expired)
    3. Extract user_id and role from payload
    → No database lookup needed! Stateless authentication.

  Tradeoff:
    ✅ Stateless (no session store needed)
    ✅ Scalable (any server can validate)
    ❌ Can't revoke until expiry (unless using a blocklist)
    ❌ Large header size (~1-2 KB per request)
```

### API Key Authentication

```
Request (header):
  X-API-Key: sk_live_abc123def456

Request (query param — less secure, appears in logs):
  GET /api/data?api_key=sk_live_abc123def456

Server validates:
  Lookup API key in database/Redis → check rate limit, permissions
  
  Tradeoff vs JWT:
    ✅ Simple (just a string lookup)
    ✅ Easy to revoke (delete from database)
    ❌ Stateful (needs database/cache lookup per request)
    ❌ No embedded claims (must look up permissions separately)
```

### Cookie-Based Session

```
Response (on login):
  Set-Cookie: session_id=abc123; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=1800

Request (subsequent):
  Cookie: session_id=abc123

  Server: Look up session_id in Redis → get user_id, role, expiry
  
  Cookie attributes:
    HttpOnly: JavaScript can't access (prevents XSS stealing session)
    Secure: Only sent over HTTPS
    SameSite=Strict: Not sent in cross-origin requests (prevents CSRF)
    Max-Age=1800: Expires in 30 minutes
    
  Tradeoff vs JWT:
    ✅ Easy to revoke (delete from Redis)
    ✅ Smaller (just session ID, not full payload)
    ❌ Stateful (requires Redis/database lookup)
    ❌ Doesn't work well for API clients (cookies are browser-centric)
```

### Choosing Auth Mechanism (Interview Script)

```
"For the public API consumed by third-party developers, I'd use API keys — 
simple, easy to manage, easy to revoke. For the mobile app, I'd use JWT with 
short-lived access tokens (15 minutes) and refresh tokens (7 days). For the 
web app, I'd use HttpOnly cookies with session IDs stored in Redis — best 
security against XSS and CSRF for browser-based clients."
```

---

## Rate Limiting Headers

### Standard Rate Limit Headers

```
Response headers (inform client of their rate limit status):

  X-RateLimit-Limit: 100
    Max requests allowed per window.

  X-RateLimit-Remaining: 97
    Requests remaining in current window.

  X-RateLimit-Reset: 1742234460
    When the window resets (Unix timestamp).

When rate limited (429 response):
  HTTP/1.1 429 Too Many Requests
  Retry-After: 30
  X-RateLimit-Limit: 100
  X-RateLimit-Remaining: 0
  X-RateLimit-Reset: 1742234460

  {"error": "rate_limit_exceeded", "message": "Try again in 30 seconds"}

Why expose rate limit headers?
  Good API citizenship: Clients can implement backoff before hitting 429.
  Debugging: Clients can see how much budget they have left.
  Transparency: Builds trust with API consumers.
```

### Draft IETF Standard (RateLimit Header Fields)

```
Newer standard (replacing X- prefix):

  RateLimit-Limit: 100
  RateLimit-Remaining: 97
  RateLimit-Reset: 30   (seconds until reset, not Unix timestamp)

Some APIs use both for backward compatibility.
```

---

## Content Negotiation

### Accept Header

```
Client specifies preferred response format:

  Accept: application/json
  Accept: application/xml
  Accept: text/html
  Accept: application/json, application/xml;q=0.9, */*;q=0.5
    → Prefers JSON, accepts XML, anything else as last resort.

Server responds with:
  Content-Type: application/json (if supported)
  OR: 406 Not Acceptable (if can't serve any requested format)

API versioning via Accept header:
  Accept: application/vnd.example.v2+json
  → Requests version 2 of the API in JSON format.
  Cleaner than URL versioning (/api/v2/...) but less discoverable.
```

### Accept-Language

```
Accept-Language: en-US, en;q=0.9, es;q=0.8

  Server returns content in preferred language if available.
  Used for: Internationalized APIs, localized error messages.
  
  Response:
    Content-Language: en-US
```

---

## Custom Headers for Distributed Systems

### Request Tracing

```
X-Request-Id: req_abc123
  Unique ID for this specific request.
  Generated by: API gateway or first service in the chain.
  Passed to: ALL downstream services (preserved in logs).
  Purpose: Correlate logs across 10+ services for one user request.

X-Trace-Id: trace_xyz789 (or traceparent in W3C standard)
  Distributed tracing context.
  Links this request to its parent trace in Jaeger/X-Ray/Datadog.
  
  W3C Standard:
    traceparent: 00-trace_id-span_id-01
    tracestate: vendor-specific data

X-Correlation-Id: corr_456
  Business-level correlation (e.g., all requests for one order).
  Different from request ID (which is per-HTTP-request).
```

### Routing and Load Balancing

```
X-Forwarded-For: 203.0.113.50, 70.41.3.18
  Original client IP (when behind load balancer/proxy).
  First IP = real client. Subsequent = intermediate proxies.
  Use for: Rate limiting by real IP, geo-routing, analytics.

X-Forwarded-Proto: https
  Original protocol (load balancer terminates TLS → backend sees HTTP).
  Use for: Ensuring redirect URLs use HTTPS.

X-Forwarded-Host: www.example.com
  Original Host header (before proxy/CDN rewrites).

X-Real-IP: 203.0.113.50
  Simplified: Just the real client IP (Nginx convention).
```

### Feature Flags and A/B Testing

```
X-Feature-Flags: dark_mode=true,new_checkout=false
  Active feature flags for this request.
  Set by: Feature flag service at the gateway.
  Used by: Frontend to toggle UI features.

X-Experiment-Group: checkout_v2_treatment
  A/B test group assignment.
  Set by: Experimentation framework.
  Used by: Analytics to attribute conversions.

X-Device-Type: mobile
  Detected device type (gateway parses User-Agent).
  Used by: Services to adjust behavior (mobile-optimized responses).
```

### Internal Service Headers

```
X-Authenticated-User: user_123
  Set by API gateway after validating the auth token.
  Backend services trust this header (don't re-validate).
  IMPORTANT: Strip this header from external requests!
  Only trusted internal traffic should have this.

X-Service-Name: order-service
  Identity of the calling service (service-to-service).
  Used for: Per-service rate limiting, access control, logging.

X-Idempotency-Key: idem_abc123
  Client-provided key for idempotent operations.
  Server: If seen before → return cached response (don't re-execute).
  Use for: Payment APIs, order creation, any non-idempotent operation.
```

---

## CORS — Cross-Origin Resource Sharing

### How CORS Works

```
Browser enforces: JavaScript on domain-A.com can't call API on domain-B.com
  UNLESS domain-B.com explicitly allows it via CORS headers.

Preflight request (OPTIONS):
  Browser sends OPTIONS before the actual request:
  
  OPTIONS /api/users HTTP/1.1
  Origin: https://frontend.example.com
  Access-Control-Request-Method: POST
  Access-Control-Request-Headers: Authorization, Content-Type

Server CORS response:
  Access-Control-Allow-Origin: https://frontend.example.com
  Access-Control-Allow-Methods: GET, POST, PUT, DELETE
  Access-Control-Allow-Headers: Authorization, Content-Type
  Access-Control-Max-Age: 86400   (cache preflight for 24 hours)
  Access-Control-Allow-Credentials: true

  Now browser allows the actual POST request.

Common configurations:
  Allow all origins (public API): Access-Control-Allow-Origin: *
  Allow specific origin: Access-Control-Allow-Origin: https://app.example.com
  Allow credentials: Access-Control-Allow-Credentials: true
    (Required for cookies to be sent cross-origin)
    NOTE: Can't use * with credentials — must specify exact origin.
```

### CORS in System Design

```
When CORS matters:
  - Frontend (React/Angular) on different domain than API
  - Microservices exposed directly to browser
  - Third-party integrations calling your API from their frontend

When CORS doesn't matter:
  - Server-to-server communication (no browser involved)
  - Mobile apps (not subject to CORS)
  - Same-origin deployment (frontend and API on same domain)

Interview tip:
  "I'd configure CORS on the API gateway to allow our frontend domain.
   For the OPTIONS preflight, I'd set Access-Control-Max-Age: 86400 to 
   cache the preflight response for 24 hours — avoiding the extra round 
   trip for most requests."
```

---

## Compression Headers

### Request

```
Accept-Encoding: gzip, deflate, br
  "I support these compression algorithms."
  br (Brotli): 15-25% better compression than gzip for text.
  gzip: Universal support, good compression.
```

### Response

```
Content-Encoding: br
  "Response body is Brotli-compressed."
  Client decompresses before parsing.

Vary: Accept-Encoding
  "CDN: cache different versions based on Accept-Encoding."
  Client sends Accept-Encoding: gzip → CDN serves gzip version.
  Client sends Accept-Encoding: br → CDN serves Brotli version.
  Without Vary: CDN might serve gzip to a Brotli-capable client.

Compression savings:
  JSON API response (uncompressed): 50 KB
  With gzip: ~10 KB (80% reduction)
  With Brotli: ~8 KB (84% reduction)
  
  At 100K requests/sec × 40 KB savings = 4 GB/sec bandwidth saved!
```

---

## Connection and Keep-Alive Headers

### HTTP/1.1 Keep-Alive

```
Connection: keep-alive
Keep-Alive: timeout=30, max=100

  "Keep this TCP connection open for 30 seconds or 100 requests."
  
  Without keep-alive: Every request = new TCP connection (expensive!)
    TCP handshake: ~30ms
    TLS handshake: ~50ms
    Total overhead: ~80ms per request
    
  With keep-alive: One connection, many requests.
    First request: 80ms (setup) + response time
    Subsequent: 0ms overhead + response time
    
  HTTP/2: Keep-alive is implicit (multiplexing — many requests on one connection).
```

### HTTP/2 Specific

```
HTTP/2 features (no special headers needed):
  Multiplexing: Multiple requests on one connection (no head-of-line blocking)
  Header compression (HPACK): Headers sent efficiently (delta-encoded)
  Server push: Server proactively sends resources before client requests them
  Binary framing: More efficient than text-based HTTP/1.1
  
  Upgrade header (HTTP/1.1 → HTTP/2):
    Connection: Upgrade
    Upgrade: h2c
    
  In practice: Most connections upgrade via TLS ALPN (automatic, no header needed).
```

---

## Headers in System Design Interviews

### When to Mention Headers

```
1. Caching strategy:
   "I'd set Cache-Control: public, s-maxage=60 on the CDN for product listing API,
    with ETag for conditional revalidation. Browser caches for max-age=10."

2. Authentication:
   "Bearer JWT tokens in the Authorization header. API gateway validates and 
    sets X-Authenticated-User for downstream services."

3. Rate limiting:
   "The API returns X-RateLimit-Remaining so clients can back off gracefully.
    On 429, Retry-After tells them exactly when to retry."

4. Distributed tracing:
   "X-Request-Id generated at the API gateway, propagated to all services,
    included in every log line for end-to-end correlation."

5. Idempotency:
   "Client sends X-Idempotency-Key for payment API. Server checks Redis —
    if key exists, returns cached response without re-executing."

6. Content negotiation:
   "Accept: application/json header. API versioning via Accept header:
    application/vnd.example.v2+json rather than URL versioning."
```

### Headers That Show Senior-Level Thinking

```
Mentioning these in your design shows depth:

  ETag + If-None-Match: "Conditional GET reduces bandwidth by 90% for unchanged data"
  stale-while-revalidate: "Users get instant response while cache refreshes in background"
  X-Idempotency-Key: "Prevents double-charges when client retries a timeout"
  Vary: Accept-Encoding: "CDN serves correct compressed version per client capability"
  X-Forwarded-For: "Rate limit by real client IP, not load balancer IP"
  Retry-After: "Graceful degradation — clients know exactly when to retry"
```

---

## Common Interview Mistakes

### ❌ Mistake 1: Not mentioning caching headers for read-heavy APIs
**Better**: "Cache-Control: public, s-maxage=60 for CDN. ETag for conditional revalidation. Saves 90%+ bandwidth for unchanged responses."

### ❌ Mistake 2: No authentication header strategy
**Better**: "JWT in Authorization: Bearer for APIs. HttpOnly cookies for web apps. API keys for third-party developers."

### ❌ Mistake 3: Ignoring distributed tracing headers
**Better**: "X-Request-Id propagated through all services for end-to-end log correlation."

### ❌ Mistake 4: Not mentioning idempotency key header
**Better**: "Client sends X-Idempotency-Key for non-idempotent operations. Server deduplicates using Redis."

### ❌ Mistake 5: Cache-Control: no-cache everywhere
**Better**: Differentiate: `public, max-age=3600` for static assets, `private, no-cache` for user data, `no-store` for sensitive data.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                HTTP HEADERS CHEAT SHEET                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CACHING:                                                    │
│    Cache-Control: public, max-age=3600 (CDN + browser cache) │
│    Cache-Control: private, no-cache (user-specific, validate)│
│    Cache-Control: no-store (sensitive, never cache)           │
│    ETag + If-None-Match (conditional GET, 304 Not Modified)  │
│    stale-while-revalidate=60 (instant response, bg refresh)  │
│    immutable (versioned static assets, never check)          │
│                                                              │
│  AUTHENTICATION:                                             │
│    Authorization: Bearer <JWT> (APIs, mobile)                │
│    Cookie: session_id=<id> (web apps, HttpOnly+Secure)       │
│    X-API-Key: <key> (third-party developers)                 │
│                                                              │
│  SECURITY:                                                   │
│    Strict-Transport-Security (HSTS — HTTPS only)             │
│    Content-Security-Policy (XSS prevention)                  │
│    X-Content-Type-Options: nosniff                           │
│    X-Frame-Options: DENY (clickjacking prevention)           │
│                                                              │
│  RATE LIMITING:                                              │
│    X-RateLimit-Limit / Remaining / Reset                     │
│    Retry-After: 30 (on 429 response)                         │
│                                                              │
│  DISTRIBUTED SYSTEMS:                                        │
│    X-Request-Id: Unique per request (log correlation)        │
│    X-Trace-Id: Distributed tracing context                   │
│    X-Idempotency-Key: Prevent duplicate execution            │
│    X-Forwarded-For: Real client IP (behind LB/proxy)         │
│    X-Authenticated-User: Set by gateway (internal only)      │
│                                                              │
│  CONTENT:                                                    │
│    Content-Type: application/json; charset=utf-8             │
│    Accept-Encoding: gzip, br (client → server)               │
│    Content-Encoding: br (server → client)                    │
│    Vary: Accept-Encoding (CDN cache key)                     │
│                                                              │
│  CORS:                                                       │
│    Access-Control-Allow-Origin: https://app.example.com      │
│    Access-Control-Max-Age: 86400 (cache preflight 24h)       │
│    Access-Control-Allow-Credentials: true (for cookies)      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 5: Caching Strategies** — Cache-Control and CDN caching
- **Topic 15: Rate Limiting** — Rate limit headers and throttling
- **Topic 19: CDN & Edge Caching** — s-maxage, Vary, stale-while-revalidate
- **Topic 25: API Design** — Content negotiation, versioning headers
- **Topic 27: Security & Encryption** — HSTS, CSP, auth headers
- **Topic 45: Observability** — X-Request-Id, tracing headers

---

*This document is part of the System Design Interview Deep Dive series.*
