# 🎯 Topic 25: API Design

> **System Design Interview — Deep Dive**
> A comprehensive guide covering REST API design, GraphQL, gRPC, API versioning, error handling, idempotency, pagination, filtering, rate limiting integration, authentication patterns, webhook APIs, and how to articulate API design decisions with depth in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [REST API Design Principles](#rest-api-design-principles)
3. [HTTP Methods — When to Use Which](#http-methods--when-to-use-which)
4. [URL Structure and Naming Conventions](#url-structure-and-naming-conventions)
5. [Request and Response Design](#request-and-response-design)
6. [HTTP Status Codes — The Right Code for Every Situation](#http-status-codes)
7. [Error Handling](#error-handling)
8. [API Versioning](#api-versioning)
9. [Pagination](#pagination)
10. [Filtering, Sorting, and Field Selection](#filtering-sorting-and-field-selection)
11. [Idempotency in APIs](#idempotency-in-apis)
12. [Authentication and Authorization in APIs](#authentication-and-authorization-in-apis)
13. [Rate Limiting in API Design](#rate-limiting-in-api-design)
14. [REST vs GraphQL vs gRPC](#rest-vs-graphql-vs-grpc)
15. [Webhook API Design](#webhook-api-design)
16. [API Gateway Patterns](#api-gateway-patterns)
17. [Real-World API Examples](#real-world-api-examples)
18. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
19. [Common Interview Mistakes](#common-interview-mistakes)
20. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**API design** is defining the contract between your system and its consumers — how clients request data, create resources, handle errors, and authenticate. A well-designed API is intuitive, consistent, backward-compatible, and scales from 1 client to 1 million.

```
A great API is like a great UI for developers:
  Discoverable: Developers can guess endpoints without reading docs
  Consistent: Same patterns everywhere (no surprises)
  Forgiving: Clear error messages when things go wrong
  Evolvable: Can add features without breaking existing clients
  Performant: Supports pagination, filtering, field selection
```

---

## REST API Design Principles

### The Resource-Oriented Model

```
REST = Resources (nouns) + HTTP Methods (verbs)

Resources are THINGS (nouns):
  /users          → Collection of users
  /users/123      → Specific user
  /users/123/orders  → Orders belonging to user 123
  /orders/456     → Specific order (direct access)

HTTP Methods are ACTIONS (verbs):
  GET    → Read (retrieve a resource)
  POST   → Create (create a new resource)
  PUT    → Replace (full update of a resource)
  PATCH  → Partial Update (modify specific fields)
  DELETE → Remove (delete a resource)

The combination tells the story:
  GET /users/123        → "Get user 123"
  POST /users           → "Create a new user"
  PATCH /users/123      → "Update some fields of user 123"
  DELETE /users/123     → "Delete user 123"
  GET /users/123/orders → "Get all orders for user 123"
```

### Key REST Principles

```
1. Stateless: Each request contains ALL info needed to process it.
   Server doesn't remember previous requests.
   Auth token in every request, not "you logged in earlier."

2. Resource-based: URLs represent resources, not actions.
   ✅ GET /users/123     (resource-based)
   ❌ GET /getUser?id=123 (action-based, RPC-style)
   ❌ POST /deleteUser    (action in URL, wrong method)

3. Uniform interface: Same patterns across all resources.
   GET /users, GET /orders, GET /products (consistent)
   Not: GET /users, FETCH /orders, RETRIEVE /products (inconsistent)

4. HATEOAS (Hypermedia): Responses include links to related resources.
   { "id": 123, "name": "Alice", 
     "_links": { "orders": "/users/123/orders", "profile": "/users/123/profile" } }
   (Nice to have, not required in interviews)
```

---

## HTTP Methods — When to Use Which

| Method | Purpose | Idempotent? | Safe? | Request Body? | Example |
|---|---|---|---|---|---|
| **GET** | Read/retrieve | Yes | Yes | No | GET /users/123 |
| **POST** | Create | No | No | Yes | POST /users |
| **PUT** | Full replace | Yes | No | Yes | PUT /users/123 |
| **PATCH** | Partial update | Yes* | No | Yes | PATCH /users/123 |
| **DELETE** | Remove | Yes | No | No | DELETE /users/123 |

```
Idempotent: Multiple identical requests produce the same result.
  PUT /users/123 {name: "Bob"} → always sets name to "Bob" (same result)
  DELETE /users/123 → user deleted. Delete again → still deleted (same result)
  POST /users {name: "Bob"} → creates a NEW user each time (NOT idempotent!)

Safe: Doesn't modify server state. GET is safe (read-only).
  Safe methods can be cached, prefetched, retried freely.

POST vs PUT:
  POST /orders → Create a NEW order (server assigns ID)
  PUT /orders/456 → Replace order 456 (client knows the ID)
  
  POST = "create something new, you decide the ID"
  PUT = "here's exactly what this resource should look like"

PUT vs PATCH:
  PUT /users/123 {name: "Bob", email: "bob@x.com", age: 30}
    → Replace ENTIRE resource (must send all fields)
  
  PATCH /users/123 {name: "Bob"}
    → Update ONLY the name field (other fields unchanged)
    
  Use PATCH for partial updates (most common in practice).
  Use PUT only when replacing the entire resource.
```

---

## URL Structure and Naming Conventions

### Best Practices

```
✅ Use plural nouns for collections:
  /users (not /user)
  /orders (not /order)
  /products (not /product)

✅ Use lowercase with hyphens:
  /user-profiles (not /userProfiles, not /user_profiles)
  /order-items (not /orderItems)

✅ Nest for relationships (but don't go deeper than 2 levels):
  /users/123/orders          ✅ (orders for user 123)
  /users/123/orders/456      ✅ (specific order for user 123)
  /users/123/orders/456/items/789  ❌ (too deep! Use /order-items/789)

✅ Use query parameters for filtering:
  /orders?status=pending&created_after=2025-03-01
  /products?category=electronics&sort=price&order=asc

❌ Don't put verbs in URLs:
  ❌ /users/123/activate    (use PATCH /users/123 {status: "active"})
  ❌ /orders/456/cancel     (use PATCH /orders/456 {status: "cancelled"})
  
  Exception: Operations that don't map to CRUD:
  ✅ POST /orders/456/refund  (trigger a process, not simple field update)
  ✅ POST /users/123/reset-password  (action that sends email)
```

### Sub-Resource vs Top-Level

```
Accessing a resource through its parent:
  GET /users/123/orders → Orders for this specific user
  
  vs accessing directly:
  GET /orders/456 → Any order by ID (regardless of user)

Both are valid. Use the one that matches your access pattern:
  "Show me MY orders" → GET /users/me/orders (or GET /orders?user_id=me)
  "Admin looks up any order" → GET /orders/456
  
  If resource is ALWAYS accessed through parent → sub-resource URL
  If resource is independently identifiable → top-level URL + optional filter
```

---

## Request and Response Design

### Request Body (POST/PUT/PATCH)

```json
// POST /orders
{
  "user_id": "user_123",
  "items": [
    { "product_id": "prod_456", "quantity": 2 },
    { "product_id": "prod_789", "quantity": 1 }
  ],
  "shipping_address": {
    "street": "123 Main St",
    "city": "NYC",
    "zip": "10001"
  },
  "payment_method_id": "pm_card_4242",
  "idempotency_key": "order_req_abc123"
}
```

### Response Body

```json
// 201 Created
{
  "id": "ord_xyz789",
  "status": "confirmed",
  "user_id": "user_123",
  "total": 109.97,
  "currency": "USD",
  "items": [
    { "product_id": "prod_456", "name": "Widget", "quantity": 2, "unit_price": 29.99 },
    { "product_id": "prod_789", "name": "Gadget", "quantity": 1, "unit_price": 49.99 }
  ],
  "shipping_address": { ... },
  "created_at": "2025-03-15T14:30:00Z",
  "estimated_delivery": "2025-03-18T12:00:00Z"
}
```

### Response Envelope Pattern

```json
// Consistent envelope for all responses:
{
  "data": { ... },          // The actual resource(s)
  "meta": {                 // Pagination, request metadata
    "total": 1247,
    "page": 3,
    "per_page": 50,
    "request_id": "req_abc123"
  },
  "links": {                // Pagination links
    "next": "/orders?page=4&per_page=50",
    "prev": "/orders?page=2&per_page=50"
  }
}

// Error envelope:
{
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Your account balance is insufficient for this purchase.",
    "details": {
      "required": 109.97,
      "available": 85.00,
      "currency": "USD"
    },
    "request_id": "req_abc123",
    "documentation_url": "https://api.example.com/docs/errors#insufficient-funds"
  }
}
```

---

## HTTP Status Codes

### Success Codes (2xx)

```
200 OK: Request succeeded. Response includes data.
  GET /users/123 → 200 with user data
  PATCH /users/123 → 200 with updated user
  DELETE /users/123 → 200 (or 204)

201 Created: New resource created successfully.
  POST /orders → 201 with new order data + Location header
  Location: /orders/xyz789

204 No Content: Success, but no response body.
  DELETE /users/123 → 204 (deleted, nothing to return)
  PUT /settings → 204 (updated, client already knows the data)

202 Accepted: Request accepted for async processing.
  POST /reports/generate → 202 (report will be ready later)
  Response: { "status": "processing", "check_at": "/reports/123" }
```

### Client Error Codes (4xx)

```
400 Bad Request: Invalid request (malformed JSON, missing required field).
  { "error": { "code": "VALIDATION_ERROR", "message": "email is required" } }

401 Unauthorized: Not authenticated (no token, or token expired).
  Response: WWW-Authenticate: Bearer realm="api"

403 Forbidden: Authenticated but not authorized (wrong role/permissions).
  "You're logged in as a regular user, but this requires admin."

404 Not Found: Resource doesn't exist.
  GET /users/999999 → 404 (user doesn't exist)
  ALSO: Sometimes used to hide existence (security: 404 instead of 403)

405 Method Not Allowed: Wrong HTTP method.
  DELETE /users → 405 (can't delete entire collection)

409 Conflict: Request conflicts with current state.
  POST /users {email: "alice@x.com"} → 409 (email already exists)
  PUT /orders/456 {version: 3} → 409 (current version is 4, conflict)

422 Unprocessable Entity: Valid JSON, but semantically wrong.
  POST /orders {quantity: -5} → 422 (can't have negative quantity)

429 Too Many Requests: Rate limit exceeded.
  Headers: Retry-After: 30, X-RateLimit-Remaining: 0
```

### Server Error Codes (5xx)

```
500 Internal Server Error: Server bug. Client can retry.
502 Bad Gateway: Upstream service is down (load balancer can't reach backend).
503 Service Unavailable: Server overloaded or in maintenance.
  Headers: Retry-After: 60
504 Gateway Timeout: Upstream service is too slow.
```

---

## Error Handling

### Structured Error Response

```json
{
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Order ord_xyz789 not found.",
    "status": 404,
    "request_id": "req_abc123",
    "timestamp": "2025-03-15T14:30:00Z",
    "details": {
      "resource_type": "order",
      "resource_id": "ord_xyz789"
    },
    "documentation_url": "https://api.example.com/docs/errors#resource-not-found"
  }
}
```

### Validation Errors (Multiple Fields)

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed.",
    "status": 422,
    "details": {
      "fields": [
        { "field": "email", "message": "Invalid email format", "value": "not-an-email" },
        { "field": "age", "message": "Must be between 13 and 120", "value": -5 }
      ]
    }
  }
}
```

### Error Design Principles

```
1. Machine-readable error code: "INSUFFICIENT_FUNDS" (client can switch on this)
2. Human-readable message: "Your balance is too low" (display to user)
3. Request ID: Always include for debugging (correlate with server logs)
4. Documentation link: Where to learn more about this error
5. Don't expose internals: No stack traces, no SQL errors, no internal IPs

Security: Never reveal:
  ❌ "User with this email already exists" (confirms email is registered → attack vector)
  ✅ "Unable to create account. Please try a different email." (vague, safe)
```

---

## API Versioning

### Strategies

```
1. URL versioning (most common, most explicit):
   /api/v1/users/123
   /api/v2/users/123
   
   Pros: Clear, visible, easy to route at load balancer
   Cons: URL pollution, clients must update URLs for new versions

2. Header versioning:
   Accept: application/vnd.example.v2+json
   
   Pros: Clean URLs, content negotiation
   Cons: Hidden, harder to test (can't just paste URL in browser)

3. Query parameter:
   /api/users/123?version=2
   
   Pros: Simple, visible
   Cons: Optional parameter → what's the default? Confusing.

Recommendation for interviews:
  URL versioning (/api/v1/) — most explicit, easiest to reason about.
  "I'd version the API in the URL: /api/v2/orders. When we need breaking changes,
   we deploy v2 alongside v1, give clients 6 months to migrate, then deprecate v1."
```

### Backward Compatibility Rules

```
SAFE changes (no new version needed):
  ✅ Adding a new field to response (existing clients ignore it)
  ✅ Adding a new optional query parameter
  ✅ Adding a new endpoint
  ✅ Adding a new value to an enum (if client handles "unknown" gracefully)

BREAKING changes (need new version):
  ❌ Removing a response field
  ❌ Renaming a response field
  ❌ Changing a field's type (string → number)
  ❌ Adding a required request field
  ❌ Changing URL structure
  ❌ Changing error response format

Strategy: Additive changes only in existing version.
  Breaking changes → new major version → deprecation timeline for old version.
```

---

## Pagination

### Cursor-Based (Recommended)

```
Request: GET /orders?limit=20&cursor=eyJpZCI6MTAwfQ==
Response:
{
  "data": [...20 orders...],
  "pagination": {
    "next_cursor": "eyJpZCI6MTIwfQ==",
    "has_more": true
  }
}

Next page: GET /orders?limit=20&cursor=eyJpZCI6MTIwfQ==

Cursor = opaque token (base64 encoded last item's ID/sort key).
Client doesn't know what's inside — just passes it back.

Pros:
  ✅ Consistent results even with concurrent inserts/deletes
  ✅ O(1) performance regardless of page number
  ✅ Works with DynamoDB LastEvaluatedKey, Elasticsearch search_after
  
Cons:
  ❌ Can't jump to "page 50" (must traverse sequentially)
  ❌ Not great for UI with page numbers (but perfect for infinite scroll)
```

### Offset-Based (Simple but Limited)

```
Request: GET /orders?page=3&per_page=20
Response:
{
  "data": [...20 orders...],
  "meta": { "total": 1247, "page": 3, "per_page": 20, "total_pages": 63 }
}

Pros:
  ✅ Can jump to any page (page=50)
  ✅ Can show "Page 3 of 63" in UI
  
Cons:
  ❌ OFFSET 1000 in SQL → slow (DB scans and discards 1000 rows)
  ❌ Inconsistent with concurrent changes (items shift between pages)
  ❌ Doesn't work with DynamoDB (no OFFSET concept)

Use offset for: Small datasets, admin UIs with page numbers.
Use cursor for: Large datasets, mobile infinite scroll, DynamoDB.
```

---

## Filtering, Sorting, and Field Selection

### Filtering

```
GET /orders?status=pending&created_after=2025-03-01&total_min=100

  status=pending: Exact match
  created_after=2025-03-01: Range filter
  total_min=100: Numeric range

Complex filters:
  GET /products?category=electronics&price[gte]=100&price[lte]=500&in_stock=true
  
  Square bracket notation for range operators:
    price[gte]=100 → price >= 100
    price[lte]=500 → price <= 500
    price[gt], price[lt] → strict greater/less than
```

### Sorting

```
GET /products?sort=price&order=asc
GET /products?sort=-price (hyphen prefix = descending)
GET /products?sort=created_at:desc,price:asc (multiple fields)

Default sort: Most recent first (created_at DESC) for most resources.
Always document the default sort order.
```

### Field Selection (Sparse Fieldsets)

```
GET /users/123?fields=id,name,avatar_url

  Returns ONLY requested fields (reduces response size):
  { "id": 123, "name": "Alice", "avatar_url": "..." }
  
  Instead of full 50-field response.
  Saves bandwidth, especially for mobile clients.

GraphQL equivalent: This is exactly what GraphQL does natively.
REST requires explicit field selection support.
```

---

## Idempotency in APIs

### The Problem

```
Client sends: POST /payments {amount: 100}
Network timeout — client doesn't know if server received it.
Client retries: POST /payments {amount: 100}

Without idempotency: Customer charged $200 (two payments created!)
With idempotency: Customer charged $100 (second request returns cached result)
```

### Idempotency Key Pattern

```
Request:
  POST /payments
  X-Idempotency-Key: idem_abc123
  { "amount": 100, "customer": "cust_456" }

Server logic:
  1. Check Redis: GET idempotency:idem_abc123
  2. If exists → return cached response (don't re-execute)
  3. If not exists:
     a. Process payment
     b. Store: SET idempotency:idem_abc123 {response} EX 86400
     c. Return response

  Key lifetime: 24 hours (long enough for retries, short enough to not waste memory)
  
  Stripe, Amazon, and most payment APIs use this exact pattern.
  
  Client generates the key (UUID) → server deduplicates.
  Same key + same endpoint + same body = same response (no duplicate execution).
```

### Which Methods Need Idempotency Keys?

```
GET:    Naturally idempotent (read-only). No key needed.
PUT:    Naturally idempotent (same body → same result). No key needed.
DELETE: Naturally idempotent (delete twice → still deleted). No key needed.
PATCH:  Usually idempotent (SET name = "Bob" twice → same result). Usually no key.
POST:   NOT idempotent! Each POST creates a new resource.
  → POST endpoints that have side effects MUST support idempotency keys.
  → Especially: payments, orders, transfers, notifications.
```

---

## Authentication and Authorization in APIs

### Auth Patterns Summary

```
Public APIs (third-party developers):
  API Key: X-API-Key: sk_live_abc123
  Simple, easy to revoke, rate limit per key.

Mobile/SPA (first-party apps):
  JWT (short-lived): Authorization: Bearer eyJ...
  + Refresh token (long-lived, stored securely)
  Access token expires in 15 minutes → refresh for new one.

Web Apps (browser, same domain):
  HttpOnly Cookie: Set-Cookie: session_id=abc; HttpOnly; Secure; SameSite=Strict
  Best security for browser-based clients.

Service-to-Service (internal):
  mTLS (mutual TLS): Both client and server present certificates.
  Or: Signed JWT with service identity.
  No user credentials — just service identity.

Webhook verification:
  HMAC signature: X-Signature: sha256=abc123...
  Receiver verifies signature with shared secret.
```

---

## Rate Limiting in API Design

```
Design rate limits per:
  Per API key: 1000 requests/minute
  Per user: 100 requests/minute (across all their API keys)
  Per endpoint: /search limited to 10 req/sec (expensive)
  Per IP: 50 requests/minute (unauthenticated)

Response headers:
  X-RateLimit-Limit: 1000
  X-RateLimit-Remaining: 847
  X-RateLimit-Reset: 1742234460

When exceeded:
  HTTP 429 Too Many Requests
  Retry-After: 30
  Body: { "error": { "code": "RATE_LIMIT_EXCEEDED", "retry_after_seconds": 30 } }

Tiered rate limits:
  Free tier: 100 req/min
  Pro tier: 1000 req/min
  Enterprise: 10000 req/min (or custom)
```

---

## REST vs GraphQL vs gRPC

| Aspect | REST | GraphQL | gRPC |
|---|---|---|---|
| **Protocol** | HTTP/1.1 or HTTP/2 | HTTP (typically POST) | HTTP/2 |
| **Data format** | JSON | JSON | Protobuf (binary) |
| **Schema** | OpenAPI/Swagger | Strong schema (SDL) | .proto files |
| **Flexibility** | Fixed endpoints | Client specifies fields | Fixed methods |
| **Over-fetching** | Common problem | Solved (request only needed fields) | Minimal (binary) |
| **Caching** | HTTP caching (GET) | Harder (all POST) | Not HTTP-cacheable |
| **Real-time** | Polling/WebSocket | Subscriptions | Bidirectional streaming |
| **Best for** | Public APIs, CRUD | Mobile apps, complex UIs | Internal microservices |

```
Choose REST when:
  ✅ Public API for third-party developers (most familiar)
  ✅ Simple CRUD operations
  ✅ HTTP caching is important
  ✅ Resource-oriented data model

Choose GraphQL when:
  ✅ Mobile app needing different data per screen
  ✅ Multiple clients needing different response shapes
  ✅ Reducing over-fetching is critical (bandwidth-sensitive)
  ✅ Rapid frontend iteration without backend changes

Choose gRPC when:
  ✅ Internal service-to-service communication
  ✅ High-performance, low-latency requirements
  ✅ Streaming data (bidirectional)
  ✅ Strong typing with code generation
  ✅ Not browser-facing (limited browser support)
```

---

## Webhook API Design

### Designing Webhook Delivery

```
Registration:
  POST /webhooks
  { "url": "https://customer.com/webhook", "events": ["order.created", "order.shipped"] }

Delivery:
  POST https://customer.com/webhook
  Content-Type: application/json
  X-Webhook-Signature: sha256=abc123...
  X-Webhook-Id: wh_event_789
  X-Webhook-Timestamp: 2025-03-15T14:30:00Z
  
  { "event": "order.created", "data": { "order_id": "ord_456", ... } }

Verification:
  Receiver computes: HMAC-SHA256(request_body, webhook_secret)
  Compares with X-Webhook-Signature → if match, authentic.

Retry policy:
  If receiver responds non-2xx → retry with exponential backoff:
  1 min → 5 min → 30 min → 2 hours → 8 hours → 24 hours → give up (disable webhook)

Idempotency:
  Receiver uses X-Webhook-Id to deduplicate (same event delivered twice = process once).
```

---

## API Gateway Patterns

```
API Gateway sits between clients and backend services:

  Client → API Gateway → Service A
                       → Service B
                       → Service C

Gateway responsibilities:
  Authentication: Validate JWT/API key before reaching services
  Rate limiting: Enforce limits per client/API key
  Routing: /users → User Service, /orders → Order Service
  Transformation: Rename fields, merge responses, version translation
  Caching: Cache GET responses (short TTL)
  Logging: Access logs, request/response capture
  CORS: Handle OPTIONS preflight responses
  
Products: AWS API Gateway, Kong, Envoy, Nginx, Apigee

In system design:
  "I'd put an API gateway in front of our microservices. It handles 
   authentication, rate limiting, and routing — so individual services 
   don't need to implement these cross-cutting concerns."
```

---

## Real-World API Examples

### Stripe API (Gold Standard)

```
POST /v1/charges
Authorization: Bearer sk_live_abc123
Idempotency-Key: charge_req_456

{
  "amount": 2000,        // In cents ($20.00)
  "currency": "usd",
  "source": "tok_visa",
  "description": "Order #789"
}

Response (201 Created):
{
  "id": "ch_1234",
  "amount": 2000,
  "currency": "usd",
  "status": "succeeded",
  "created": 1742234400
}

Why Stripe's API is great:
  - Consistent URL structure (/v1/resource)
  - Idempotency keys for payment safety
  - Expanding related objects (?expand[]=customer)
  - Pagination with cursor (starting_after parameter)
  - Clear error codes with machine-readable types
```

### Twitter/X API

```
GET /2/tweets?ids=123,456&tweet.fields=created_at,public_metrics
Authorization: Bearer <token>

Response:
{
  "data": [
    { "id": "123", "text": "Hello!", "created_at": "...", "public_metrics": {...} }
  ]
}

Patterns used:
  - Field selection (tweet.fields=...)
  - Batch fetching (ids=123,456)
  - Expansion of nested objects
  - Cursor pagination for timelines
```

---

## Interview Talking Points & Scripts

### API Design for an Order System

> *"I'd design the order API as RESTful resources. POST /api/v1/orders to create an order — the request includes an X-Idempotency-Key header so retries don't create duplicate orders. Response is 201 with the order object and a Location header pointing to /api/v1/orders/{id}. GET /api/v1/users/{id}/orders for a user's order history — cursor-paginated with 20 items per page by default. PATCH /api/v1/orders/{id} for status updates. I'd version in the URL (/v1/) and plan for /v2/ when we need breaking changes."*

### Error Handling

> *"Every error response follows a consistent envelope: a machine-readable code (INSUFFICIENT_INVENTORY), a human-readable message, the request_id for debugging, and a documentation URL. For validation errors, I'd return 422 with field-level error details. For auth issues, 401 if not authenticated, 403 if wrong permissions. I'd never expose internal errors to clients — 500 with a generic message and the request_id is enough for the client; engineers correlate using the request_id in our logging system."*

### Versioning

> *"I'd version the API in the URL: /api/v1/. Adding new optional fields is backward-compatible — no version bump needed. When we need a breaking change (like restructuring the order response), we release /api/v2/ alongside v1, give clients a 6-month migration window with deprecation warnings in response headers, then sunset v1."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Verbs in URLs
**Wrong**: POST /createOrder, GET /getUser
**Better**: POST /orders, GET /users/123. Resources are nouns, methods are verbs.

### ❌ Mistake 2: Not mentioning idempotency for POST
**Wrong**: "Clients send POST /orders to create an order."
**Better**: "POST /orders with an X-Idempotency-Key header. If the network times out and the client retries, the server returns the cached response instead of creating a duplicate."

### ❌ Mistake 3: Using 200 for everything
**Wrong**: Always returning 200 with error in body.
**Better**: 201 for created, 204 for deleted, 400 for bad input, 404 for not found, 429 for rate limited.

### ❌ Mistake 4: No pagination
**Wrong**: GET /orders returns ALL orders (could be millions).
**Better**: Cursor-based pagination with configurable limit (default 20, max 100).

### ❌ Mistake 5: Exposing internal errors
**Wrong**: { "error": "NullPointerException at OrderService.java:42" }
**Better**: { "error": { "code": "INTERNAL_ERROR", "message": "Something went wrong", "request_id": "req_abc" } }

### ❌ Mistake 6: No versioning strategy
**Wrong**: "We'll just update the existing API."
**Better**: "URL-versioned at /api/v1/. Breaking changes go in v2. Deprecation timeline of 6 months."

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                   API DESIGN CHEAT SHEET                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  RESOURCES: Plural nouns, lowercase, hyphens                 │
│    /users, /orders, /user-profiles                           │
│                                                              │
│  METHODS:                                                    │
│    GET (read), POST (create), PUT (replace),                 │
│    PATCH (partial update), DELETE (remove)                    │
│                                                              │
│  STATUS CODES:                                               │
│    200 OK, 201 Created, 204 No Content                       │
│    400 Bad Request, 401 Unauthorized, 403 Forbidden          │
│    404 Not Found, 409 Conflict, 422 Unprocessable, 429 Rate  │
│    500 Internal Error, 503 Unavailable                       │
│                                                              │
│  PAGINATION: Cursor-based (recommended), offset (simple)     │
│  FILTERING: Query params (?status=pending&price[gte]=100)    │
│  VERSIONING: URL-based (/api/v1/) — most explicit            │
│                                                              │
│  IDEMPOTENCY:                                                │
│    POST endpoints: X-Idempotency-Key header (client-generated)│
│    Server: Dedup via Redis (24h TTL)                         │
│    GET/PUT/DELETE: Naturally idempotent (no key needed)       │
│                                                              │
│  ERROR HANDLING:                                             │
│    Consistent envelope: code, message, request_id, details   │
│    Never expose internals (no stack traces!)                 │
│                                                              │
│  AUTH: Bearer JWT (APIs), Cookies (web), API keys (3rd party)│
│  RATE LIMITING: X-RateLimit-* headers, 429 + Retry-After     │
│                                                              │
│  REST vs GraphQL vs gRPC:                                    │
│    REST: Public APIs, CRUD, HTTP caching                     │
│    GraphQL: Mobile, flexible queries, reduce over-fetching   │
│    gRPC: Internal services, streaming, high performance      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 9: Communication Protocols** — HTTP/1.1, HTTP/2, gRPC, WebSocket
- **Topic 15: Rate Limiting** — Implementation of rate limiting in APIs
- **Topic 26: Pagination** — Deep dive into pagination strategies
- **Topic 27: Security & Encryption** — Auth, TLS, API security
- **Topic 51: HTTP Headers** — Complete header reference for APIs

---

*This document is part of the System Design Interview Deep Dive series.*
