# 🎯 Topic 25: API Design

> **System Design Interview — Deep Dive**
> REST vs gRPC vs GraphQL, resource naming, versioning, pagination, error handling, rate limiting headers, and how to design clean, scalable APIs for system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [REST API Design Principles](#rest-api-design-principles)
3. [GraphQL for Mobile Clients](#graphql-for-mobile-clients)
4. [gRPC for Internal Services](#grpc-for-internal-services)
5. [API Versioning](#api-versioning)
6. [Pagination](#pagination)
7. [Error Handling](#error-handling)
8. [Idempotency in API Design](#idempotency-in-api-design)
9. [Authentication & Authorization](#authentication--authorization)
10. [Rate Limiting Headers](#rate-limiting-headers)
11. [API Gateway Pattern](#api-gateway-pattern)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

API design determines how clients interact with your system. The choice depends on the client type and use case:

| Protocol | Best For | Key Advantage |
|---|---|---|
| **REST** | Public APIs, web clients | Universal, cacheable, simple |
| **GraphQL** | Mobile clients, varied data needs | Client specifies exact data shape |
| **gRPC** | Internal service-to-service | Binary, typed, fast, streaming |

---

## REST API Design Principles

### Resource-Oriented URLs
```
GET    /api/v1/tweets                    → List tweets
POST   /api/v1/tweets                    → Create tweet
GET    /api/v1/tweets/{id}               → Get tweet by ID
PUT    /api/v1/tweets/{id}               → Full update
PATCH  /api/v1/tweets/{id}               → Partial update
DELETE /api/v1/tweets/{id}               → Delete tweet

GET    /api/v1/users/{id}/tweets         → User's tweets (sub-resource)
GET    /api/v1/users/{id}/followers      → User's followers
POST   /api/v1/tweets/{id}/likes         → Like a tweet
DELETE /api/v1/tweets/{id}/likes         → Unlike a tweet
```

### HTTP Status Codes
```
200 OK:          Successful GET/PUT/PATCH
201 Created:     Successful POST (resource created)
204 No Content:  Successful DELETE
400 Bad Request: Invalid input (validation error)
401 Unauthorized: Missing/invalid authentication
403 Forbidden:   Authenticated but not authorized
404 Not Found:   Resource doesn't exist
409 Conflict:    Resource already exists (duplicate)
429 Too Many:    Rate limited
500 Server Error: Internal error
503 Unavailable:  Service temporarily down
```

### Response Format
```json
{
  "data": {
    "tweet_id": "123",
    "content": "Hello world",
    "author": { "user_id": "456", "name": "Alice" },
    "created_at": "2025-03-15T14:30:00Z",
    "like_count": 42
  },
  "meta": {
    "request_id": "req-abc-123"
  }
}
```

### Interview Script
> *"I'd use REST for the public API — it's stateless, cacheable via HTTP headers, documented with OpenAPI/Swagger, and literally every developer on Earth knows how to call it. For internal service-to-service calls, gRPC with Protocol Buffers — binary serialization is 3-10x smaller than JSON, strong typing catches errors at compile time, and HTTP/2 multiplexing handles thousands of concurrent RPCs."*

---

## GraphQL for Mobile Clients

### The Problem REST Has
```
REST over-fetching:
  GET /users/123 → returns 30 fields (2KB), client needs 3 fields

REST under-fetching:
  Need user profile + recent posts + follower count = 3 API calls

GraphQL: One request, exact data needed:
  query { user(id: "123") { name, avatar, followerCount, recentPosts(limit: 3) { title } } }
```

### Interview Script
> *"GraphQL makes sense specifically for the mobile client. Our user profile page needs the user's name, avatar, follower count, and last 3 posts — but our REST API either returns the entire user object (over-fetching 30 fields) or requires 3 separate API calls. GraphQL lets the mobile client specify exactly what it needs — one request, minimal bandwidth on cellular."*

---

## gRPC for Internal Services

### Why gRPC Internally
```
Protobuf: 3-10x smaller than JSON
HTTP/2: Multiplexed (thousands of RPCs per connection)
Typed: Compile-time errors instead of runtime
Code generation: Auto-generated clients in 10+ languages
Streaming: Server streaming, client streaming, bidirectional
```

---

## API Versioning

### URL Path Versioning (Recommended)
```
/api/v1/tweets      → Version 1
/api/v2/tweets      → Version 2 (breaking changes)

Pros: Explicit, easy to route, easy to test
Cons: URL changes between versions
```

### Header Versioning
```
GET /api/tweets
Accept: application/vnd.example.v2+json

Pros: Clean URLs
Cons: Harder to test (need custom headers), less visible
```

### Best Practice
```
Non-breaking changes: Add new fields (backward compatible, no version bump)
Breaking changes:     New version (v1 → v2)
Deprecation:          Support v1 for 12 months after v2 launch
```

---

## Pagination

### Cursor-Based (Recommended for Feeds)
```
GET /api/v1/timeline?cursor=eyJpZCI6MTIzNDV9&limit=20

Response:
{
  "data": [...20 tweets...],
  "pagination": {
    "next_cursor": "eyJpZCI6MTIzMjV9",
    "has_more": true
  }
}

Query: SELECT * FROM timeline WHERE tweet_id < 12345 ORDER BY tweet_id DESC LIMIT 20
Performance: O(1) regardless of scroll depth
```

### Offset-Based (For Admin Tables)
```
GET /api/v1/users?page=3&limit=20

Query: SELECT * FROM users LIMIT 20 OFFSET 40
Performance: O(N) — degrades with page depth
Use only for: Small datasets, admin interfaces where page jumping is needed
```

### Interview Script
> *"I'd use cursor-based pagination for the infinite-scroll timeline. The client sends `GET /timeline?cursor=eyJpZCI6MTIzNDV9&limit=20`. Internally, the cursor decodes to `{id: 12345}`, and the query uses `WHERE tweet_id < 12345 ORDER BY tweet_id DESC LIMIT 20`. This is O(1) regardless of position — whether the user has scrolled through 20 or 20,000 tweets. Offset-based pagination is a trap at scale — the database scans and discards 10,000 rows to find the 20 you want."*

---

## Error Handling

### Consistent Error Response
```json
{
  "error": {
    "code": "TWEET_NOT_FOUND",
    "message": "Tweet with ID 123 not found",
    "details": {
      "tweet_id": "123",
      "suggestion": "The tweet may have been deleted"
    },
    "request_id": "req-abc-123"
  }
}
```

### Error Code Categories
```
Validation errors (400):   INVALID_CONTENT, CONTENT_TOO_LONG
Auth errors (401/403):     TOKEN_EXPIRED, INSUFFICIENT_PERMISSIONS
Not found (404):           TWEET_NOT_FOUND, USER_NOT_FOUND
Conflict (409):            DUPLICATE_EMAIL, ALREADY_LIKED
Rate limit (429):          RATE_LIMIT_EXCEEDED
Server errors (500):       INTERNAL_ERROR (never expose stack traces)
```

---

## Idempotency in API Design

### For Mutation Operations
```
POST /api/v1/payments
Headers:
  Idempotency-Key: abc-123-def-456

If same key sent again → return cached result (no double-charge)
```

### Naturally Idempotent Methods
```
GET:    Always idempotent (reading doesn't change state)
PUT:    Idempotent (setting to the same value is a no-op)
DELETE: Idempotent (deleting already-deleted is a no-op)
POST:   NOT naturally idempotent → needs idempotency key
PATCH:  Depends on the operation (increment is not idempotent)
```

---

## Authentication & Authorization

### JWT for Stateless Auth
```
Client → POST /auth/login { email, password }
Server → { "token": "eyJhbGc...", "expires_in": 900 }

Subsequent requests:
  GET /api/v1/tweets
  Authorization: Bearer eyJhbGc...

API Gateway validates JWT → forwards claims as headers → no session store needed.
Short-lived tokens (15 min) + refresh tokens for security.
```

### Interview Script
> *"I'd use JWT for stateless authentication across microservices. The API gateway validates the JWT signature and forwards the decoded claims as headers. No downstream service needs to call an auth server. At 100K requests/sec, eliminating a session store lookup saves us a Redis cluster and reduces p99 latency by 2ms. Short-lived tokens (15 minutes) with a lightweight blacklist for emergency revocation."*

---

## Rate Limiting Headers

```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1710500000

HTTP/1.1 429 Too Many Requests
Retry-After: 30
```

---

## API Gateway Pattern

```
Client → API Gateway → Route by path → Microservices
                     → Auth validation
                     → Rate limiting
                     → Request logging
                     → TLS termination
                     → Response caching
```

---

## Interview Talking Points & Scripts

### REST for Public
> *"REST for public APIs — universal, cacheable, OpenAPI-documented. gRPC for internal — binary, typed, fast. GraphQL for mobile — one request, exact data."*

### Cursor Pagination
> *"Cursor-based for infinite scroll — O(1) at any depth. Offset-based is a trap — O(N) at page 500."*

---

## Common Interview Mistakes

### ❌ Using GraphQL for simple CRUD (overkill)
### ❌ Offset pagination for feeds (degrades at scale)
### ❌ Not including idempotency keys for POST/payments
### ❌ Exposing internal errors in API responses (security risk)
### ❌ Not versioning the API

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                  API DESIGN CHEAT SHEET                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  REST: Public APIs (universal, cacheable)                    │
│  GraphQL: Mobile (exact data, one request)                   │
│  gRPC: Internal (binary, typed, fast)                        │
│                                                              │
│  PAGINATION: Cursor-based for feeds (O(1))                   │
│              Offset-based for admin tables only               │
│                                                              │
│  VERSIONING: URL path (/api/v1/...) — explicit, easy         │
│  AUTH: JWT (stateless, 15-min tokens)                        │
│  IDEMPOTENCY: Client-generated key for POST/mutations        │
│  ERRORS: Consistent JSON format with error codes             │
│  RATE LIMITING: X-RateLimit-Remaining + Retry-After          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
