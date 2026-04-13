# 🎯 Topic 27: Security & Encryption

> **System Design Interview — Deep Dive**
> JWT authentication, end-to-end encryption, TLS, data encryption at rest, OAuth 2.0, and security patterns for system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Authentication — JWT Stateless Tokens](#authentication--jwt-stateless-tokens)
3. [End-to-End Encryption (E2EE)](#end-to-end-encryption-e2ee)
4. [Encryption in Transit (TLS)](#encryption-in-transit-tls)
5. [Encryption at Rest](#encryption-at-rest)
6. [OAuth 2.0 & SSO](#oauth-20--sso)
7. [Token Management](#token-management)
8. [API Security Best Practices](#api-security-best-practices)
9. [Data Privacy (GDPR)](#data-privacy-gdpr)
10. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
11. [Common Interview Mistakes](#common-interview-mistakes)
12. [Security by System Design Problem](#security-by-system-design-problem)
13. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Security in system design spans three layers:
1. **Authentication**: Who are you? (JWT, OAuth, API keys)
2. **Authorization**: What can you do? (RBAC, ABAC, permissions)
3. **Encryption**: Protect data in transit (TLS) and at rest (AES-256)

---

## Authentication — JWT Stateless Tokens

### How JWT Works
```
1. User logs in: POST /auth/login { email, password }
2. Server validates credentials → generates JWT
3. JWT = Base64(header).Base64(payload).signature

Header:  { "alg": "RS256", "typ": "JWT" }
Payload: { "user_id": "123", "roles": ["user"], "exp": 1710500900 }
Signature: HMAC-SHA256(header + payload, secret_key)

4. Client stores JWT and sends with every request:
   Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

### Stateless Advantage
```
Without JWT (session-based):
  Every request → Redis lookup (session store) → 2ms latency
  At 100K requests/sec → need dedicated Redis cluster

With JWT:
  Every request → verify signature locally (CPU only) → < 0.1ms
  No session store needed. Any server can validate.
  At 100K requests/sec → no external dependency.
```

### Token Revocation Challenge
```
Problem: JWT is valid until it expires. Can't revoke mid-flight.
If user's account is compromised → JWT still works until expiry.

Solutions:
  1. Short-lived tokens (15 minutes) → limits exposure window
  2. Refresh tokens (7 days) → stored in DB, revocable
  3. Token blacklist in Redis → checked on sensitive operations
     (lightweight: only stores revoked tokens, not all tokens)
```

### Interview Script
> *"I'd use JWT for stateless authentication across microservices. The API gateway validates the JWT signature and forwards the decoded claims (user_id, roles, permissions) as headers to downstream services. No downstream service needs to call an auth server — the token carries everything. At 100K requests/sec, eliminating a session store lookup saves us a Redis cluster and reduces p99 latency by 2ms. The tradeoff: if a user's account is compromised, we can't revoke their token immediately. I'd mitigate with short-lived tokens (15 minutes) and a lightweight token blacklist in Redis for emergency revocation."*

---

## End-to-End Encryption (E2EE)

### How It Works (Signal Protocol)
```
1. Alice generates: Public key (shared) + Private key (secret)
2. Bob generates:   Public key (shared) + Private key (secret)
3. Key exchange:    Diffie-Hellman → shared secret (both derive same key)
4. Alice encrypts:  AES-256(message, shared_secret) → encrypted blob
5. Server stores:   0x4A8F2B... (can't read it — doesn't have the key)
6. Bob decrypts:    AES-256(encrypted_blob, shared_secret) → original message
```

### What the Server Sees
```
Without E2EE: Server stores plaintext "Let's meet at 3pm"
With E2EE:    Server stores "0x4A8F2B9E3D..." (encrypted blob)

Even if:
  - Database is breached → attacker sees encrypted blobs
  - Government subpoena → server can't produce plaintext
  - Rogue employee → can't read messages
```

### Tradeoffs
```
✅ Maximum privacy: Nobody but sender and receiver can read messages
❌ No server-side search: Can't index what you can't read
❌ No content moderation: Can't detect policy violations
❌ Complex multi-device sync: Keys must be distributed to each device
❌ Key management: Lost key = lost messages (no server recovery)
```

### Interview Script
> *"For the messaging system, I'd implement end-to-end encryption using the Signal Protocol. Alice's client encrypts each message with a per-session AES-256 key derived from a Diffie-Hellman key exchange. The server only sees encrypted blobs. Even if our database is breached, we cannot produce message content. The tradeoff is real: no server-side search, no content moderation, and complex multi-device sync."*

---

## Encryption in Transit (TLS)

### What It Protects
```
Client ──HTTPS (TLS 1.3)──→ Load Balancer ──HTTP──→ Backend
  ↑ encrypted in transit         ↑ TLS terminated at LB

Protects against:
  - Man-in-the-middle (MITM) attacks
  - Packet sniffing on public WiFi
  - ISP/government surveillance of traffic
```

### TLS Termination
```
Option 1: Terminate at load balancer (most common)
  Client → HTTPS → LB → HTTP → Backend (internal network is trusted)

Option 2: End-to-end TLS (compliance)
  Client → HTTPS → LB → HTTPS → Backend (PCI-DSS requirement)
  Cost: Higher CPU on backends, more certificate management
```

---

## Encryption at Rest

### Database Encryption
```
PostgreSQL: Transparent Data Encryption (TDE)
  Data encrypted on disk with AES-256
  Decrypted in memory when read
  Key managed by AWS KMS (Hardware Security Module)

S3: Server-Side Encryption
  SSE-S3: Amazon manages the keys
  SSE-KMS: You manage keys via KMS (audit trail)
  SSE-C: You provide the key with each request (full control)
```

### Application-Level Encryption
```
For sensitive fields (SSN, credit card):
  Encrypt at application layer before storing
  encrypted_ssn = AES-256(ssn, field_encryption_key)
  Store encrypted_ssn in database
  Even DBAs can't read plaintext SSN
```

---

## OAuth 2.0 & SSO

### OAuth 2.0 Authorization Code Flow
```
1. User clicks "Login with Google"
2. Redirect to Google: GET google.com/auth?client_id=xxx&redirect_uri=xxx
3. User authenticates with Google
4. Google redirects to callback: yourapp.com/callback?code=AUTH_CODE
5. Your server exchanges code for tokens:
   POST google.com/token { code, client_secret }
6. Google returns: { access_token, refresh_token, id_token }
7. Your server creates session/JWT for the user
```

### When to Use
- **Login with Google/Facebook/GitHub**: Social login
- **Third-party API access**: Your app accesses user's Google Calendar
- **SSO**: Enterprise single sign-on across multiple internal apps

---

## Token Management

### Access Token vs Refresh Token
```
Access Token:
  Short-lived: 15 minutes
  Sent with every API request
  Contains claims (user_id, roles)
  If leaked: Limited damage (expires quickly)

Refresh Token:
  Long-lived: 7-30 days
  Stored securely (HttpOnly cookie)
  Used only to get new access tokens
  If leaked: Revokable (stored in DB)
  
Flow:
  Access token expires → client sends refresh token → server issues new access token
  Refresh token compromised → server revokes it from DB → attacker locked out
```

---

## API Security Best Practices

```
1. HTTPS everywhere (no HTTP)
2. Rate limiting per user/IP/API key
3. Input validation (prevent SQL injection, XSS)
4. CORS (restrict which origins can call your API)
5. Request signing (HMAC for webhooks)
6. API key rotation (support multiple active keys)
7. Least privilege (minimal permissions per token)
8. Audit logging (who accessed what, when)
9. Secrets management (AWS Secrets Manager, not env vars)
10. Dependency scanning (known vulnerabilities)
```

---

## Data Privacy (GDPR)

```
GDPR requirements affecting system design:
  - Right to access: User can request all their data
  - Right to deletion: User can request data deletion
  - Data portability: Export in machine-readable format
  - Consent tracking: Record what user consented to
  - Data minimization: Only collect what you need
  - Geographic restrictions: EU data stays in EU region

Implementation:
  - Geographic sharding: EU users → eu-west-1 region
  - Soft delete with purge job: Mark deleted, purge after 30 days
  - Data export API: Generate JSON/CSV of user's data
  - Consent service: Track and enforce consent per data type
```

---

## Interview Talking Points & Scripts

### JWT
> *"JWT for stateless auth. API gateway validates signature, forwards claims as headers. No session store. 15-minute expiry + blacklist for emergency revocation."*

### E2EE
> *"Signal Protocol for messaging E2EE. Server stores encrypted blobs — can't read messages even if breached. Tradeoff: no server-side search or moderation."*

---

## Common Interview Mistakes

### ❌ Not mentioning TLS/HTTPS
### ❌ JWT without addressing revocation
### ❌ Storing passwords in plaintext (always bcrypt/argon2)
### ❌ Not separating authentication from authorization
### ❌ Encryption at rest without key management (KMS)

---

## Security by System Design Problem

| Problem | Auth | Encryption | Special |
|---|---|---|---|
| **WhatsApp** | Phone verification | E2EE (Signal Protocol) | No server-side content access |
| **Twitter** | JWT + OAuth | TLS + at-rest | Content moderation (no E2EE) |
| **Payment** | JWT + API keys | TLS + field-level encryption | PCI-DSS compliance |
| **E-Commerce** | JWT + OAuth social login | TLS + at-rest | Credit card tokenization |
| **Healthcare** | JWT + MFA | E2EE + at-rest + field-level | HIPAA compliance |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│            SECURITY & ENCRYPTION CHEAT SHEET                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  AUTHENTICATION: JWT (stateless, 15-min expiry)              │
│    + Refresh tokens (7d, revocable, stored in DB)            │
│    + Blacklist in Redis for emergency revocation             │
│                                                              │
│  ENCRYPTION IN TRANSIT: TLS 1.3 (HTTPS everywhere)          │
│    Terminate at LB (most common)                             │
│    End-to-end TLS for compliance (PCI-DSS)                   │
│                                                              │
│  ENCRYPTION AT REST: AES-256 via AWS KMS                     │
│    DB-level: Transparent Data Encryption                     │
│    Field-level: SSN, credit card (app-layer encryption)      │
│    S3: SSE-KMS with audit trail                              │
│                                                              │
│  E2EE: Signal Protocol (messaging)                           │
│    Server sees encrypted blobs only                          │
│    Tradeoff: No search, no moderation                        │
│                                                              │
│  OAUTH 2.0: Social login, third-party API access             │
│  GDPR: Geographic sharding, right to deletion, data export   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
