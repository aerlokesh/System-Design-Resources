# 🎯 Topic 27: Security & Encryption

> **System Design Interview — Deep Dive**
> A comprehensive guide covering authentication, authorization, encryption (symmetric/asymmetric), TLS, JWT, OAuth 2.0, OWASP Top 10, password hashing, rate limiting, API security, secret management, zero trust, data privacy, and how to articulate security decisions in system design interviews.

---

## Table of Contents

1. [Core Concept — Defense in Depth](#core-concept--defense-in-depth)
2. [Authentication — Proving Identity](#authentication--proving-identity)
3. [JWT Deep Dive](#jwt-deep-dive)
4. [OAuth 2.0 & OpenID Connect](#oauth-20--openid-connect)
5. [Session vs Token Authentication](#session-vs-token-authentication)
6. [Authorization — RBAC, ABAC, Permissions](#authorization--rbac-abac-permissions)
7. [Password Security — Hashing & Storage](#password-security--hashing--storage)
8. [Encryption Fundamentals](#encryption-fundamentals)
9. [Symmetric vs Asymmetric Encryption](#symmetric-vs-asymmetric-encryption)
10. [TLS — Encryption in Transit](#tls--encryption-in-transit)
11. [Encryption at Rest](#encryption-at-rest)
12. [End-to-End Encryption (E2EE)](#end-to-end-encryption-e2ee)
13. [Key Management & Rotation](#key-management--rotation)
14. [Secret Management](#secret-management)
15. [API Security Patterns](#api-security-patterns)
16. [OWASP Top 10 — Common Vulnerabilities](#owasp-top-10--common-vulnerabilities)
17. [Rate Limiting & DDoS Protection](#rate-limiting--ddos-protection)
18. [CORS, CSRF & XSS Prevention](#cors-csrf--xss-prevention)
19. [Zero Trust Architecture](#zero-trust-architecture)
20. [Data Privacy — GDPR, PCI DSS, HIPAA](#data-privacy--gdpr-pci-dss-hipaa)
21. [Security by System Design Problem](#security-by-system-design-problem)
22. [Real-World Security Architectures](#real-world-security-architectures)
23. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
24. [Common Interview Mistakes](#common-interview-mistakes)
25. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept — Defense in Depth

Security in system design isn't one layer — it's **multiple layers** where each layer assumes the previous one might be breached.

```
┌─────────────────────────────────────────────────────────────┐
│                   DEFENSE IN DEPTH                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Layer 1: EDGE SECURITY                                     │
│  WAF, DDoS protection, rate limiting, geo-blocking          │
│  → Stops attacks before they reach your app                 │
│                                                              │
│  Layer 2: NETWORK SECURITY                                  │
│  VPC, firewalls, private subnets, TLS everywhere            │
│  → If edge breached, attacker can't reach internal services │
│                                                              │
│  Layer 3: AUTHENTICATION (Who are you?)                     │
│  JWT, OAuth 2.0, API keys, mTLS, MFA                       │
│  → Only verified identities can make requests               │
│                                                              │
│  Layer 4: AUTHORIZATION (What can you do?)                  │
│  RBAC, ABAC, resource policies, least privilege             │
│  → Authenticated users can only access what they're allowed │
│                                                              │
│  Layer 5: DATA SECURITY                                     │
│  Encryption at rest, encryption in transit, field-level      │
│  encryption, tokenization, data masking                      │
│  → If data is stolen, it's unreadable without keys          │
│                                                              │
│  Layer 6: AUDIT & DETECTION                                 │
│  Logging, monitoring, anomaly detection, intrusion detection│
│  → If all else fails, detect and respond quickly             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Authentication — Proving Identity

### Authentication Methods Comparison

| Method | Stateful? | Best For | Security Level |
|--------|-----------|----------|---------------|
| **JWT** | Stateless | API authentication, microservices | Medium-High |
| **Session cookies** | Stateful (server-side) | Traditional web apps | High (server-controlled) |
| **API Keys** | Stateless | Service-to-service, public APIs | Low-Medium |
| **OAuth 2.0** | Depends on flow | Third-party auth, social login | High |
| **mTLS** | Stateless | Service-to-service (zero trust) | Very High |
| **MFA (TOTP)** | Stateful | Additional factor for sensitive ops | Highest |

### Multi-Factor Authentication (MFA)

```
Something you KNOW: Password, PIN
Something you HAVE: Phone (TOTP), hardware key (YubiKey)
Something you ARE: Fingerprint, face recognition

MFA = Require 2+ of these factors
Why: If password leaked, attacker still can't log in without phone/key

TOTP (Time-based One-Time Password):
  Server + Client share secret_key during MFA setup
  Every 30 seconds: OTP = HMAC-SHA1(secret_key, timestamp / 30)
  Both generate same 6-digit code → match = verified
  
  Apps: Google Authenticator, Authy, 1Password
```

---

## JWT Deep Dive

### JWT Structure

```
eyJhbGciOiJSUzI1NiJ9.eyJ1c2VyX2lkIjoiMTIzIn0.signature
     HEADER              PAYLOAD                SIGNATURE
     (Base64)            (Base64)               (Base64)

Header:  { "alg": "RS256", "typ": "JWT", "kid": "key-2024-01" }
Payload: { 
  "sub": "user_123",         // Subject (user ID)
  "iss": "auth.myapp.com",   // Issuer
  "aud": "api.myapp.com",    // Audience
  "exp": 1710500900,         // Expiration (Unix timestamp)
  "iat": 1710497300,         // Issued at
  "roles": ["user", "admin"],
  "tenant_id": "tenant_abc"
}
Signature: RS256(header + "." + payload, private_key)
```

### JWT Best Practices

```
✅ DO:
  • Use RS256 (asymmetric) not HS256 (symmetric) for microservices
    → Auth service signs with private key
    → API services verify with public key (no shared secret!)
  • Short expiry (15 min) + refresh token (7 days)
  • Include only essential claims (not sensitive data)
  • Validate: signature, expiration, issuer, audience
  • Use "kid" (key ID) for key rotation

❌ DON'T:
  • Store in localStorage (XSS vulnerable)
  • Put sensitive data in payload (Base64 ≠ encryption, anyone can decode!)
  • Use "none" algorithm (attacker bypasses signature!)
  • Make tokens too long-lived (can't revoke!)
  • Share signing keys across services
```

### JWT Token Flow

```
┌──────┐                   ┌──────────┐                   ┌──────────┐
│Client│                   │Auth Server│                   │API Server│
└──┬───┘                   └────┬─────┘                   └────┬─────┘
   │ POST /login {email,pass}   │                              │
   │───────────────────────────→│                              │
   │                            │ Validate credentials         │
   │                            │ Generate JWT (sign w/ privkey)│
   │   { access_token, refresh_token }                         │
   │←───────────────────────────│                              │
   │                            │                              │
   │ GET /orders                │                              │
   │ Authorization: Bearer JWT  │                              │
   │──────────────────────────────────────────────────────────→│
   │                            │  Verify JWT signature (pubkey)│
   │                            │  Check expiry, issuer, audience│
   │   { orders: [...] }        │                              │
   │←──────────────────────────────────────────────────────────│
```

### Token Refresh Flow

```
Access Token:  Short-lived (15 min). Sent with every API request.
Refresh Token: Long-lived (7 days). Stored securely. Used to get new access token.

Why two tokens?
  - If access token stolen → attacker has 15 min (limited damage)
  - Refresh token stored in httpOnly cookie → not accessible via JavaScript
  - Refresh is sent ONLY to /auth/refresh endpoint → smaller attack surface

Refresh flow:
  1. Access token expires → API returns 401
  2. Client sends refresh token to POST /auth/refresh
  3. Auth server validates refresh token → issues new access token
  4. If refresh token expired → user must log in again
  
Token revocation:
  Problem: JWT is stateless → can't "delete" it
  Solutions:
    a. Short expiry (15 min) → naturally expires
    b. Token blacklist in Redis → check on every request (adds state)
    c. Token versioning → increment version in DB, reject old versions
```

---

## OAuth 2.0 & OpenID Connect

### OAuth 2.0 Flows

```
Authorization Code Flow (RECOMMENDED for web apps):
  1. User clicks "Login with Google"
  2. App redirects to Google: /authorize?client_id=X&redirect_uri=Y&scope=email
  3. User logs in at Google, grants permission
  4. Google redirects back: /callback?code=AUTH_CODE
  5. App exchanges code for tokens: POST /token { code, client_secret }
  6. Google returns: { access_token, id_token, refresh_token }
  7. App uses access_token to call Google APIs (get user profile)

Authorization Code + PKCE (for mobile/SPA — no client_secret):
  Same as above but with code_verifier/code_challenge
  Prevents authorization code interception attacks

Client Credentials Flow (service-to-service):
  Service A → POST /token { client_id, client_secret, grant_type=client_credentials }
  → Returns access_token for calling Service B's API
  No user involved — machine-to-machine authentication
```

### OpenID Connect (OIDC) = OAuth 2.0 + Identity

```
OAuth 2.0: "Can this app access my Google Drive?" (Authorization)
OIDC:      "Who is this user?" (Authentication)

OIDC adds:
  - id_token (JWT with user identity: email, name, picture)
  - /userinfo endpoint (get user profile)
  - Standard scopes: openid, profile, email
  - Discovery: /.well-known/openid-configuration
```

---

## Session vs Token Authentication

| Feature | Session (Cookie) | Token (JWT) |
|---------|-----------------|-------------|
| Storage | Server (Redis/DB) | Client (cookie/header) |
| Stateful? | Yes (session store) | No (stateless) |
| Scalability | Need shared session store | Any server can verify |
| Revocation | Delete from store → instant | Hard (wait for expiry or blacklist) |
| CSRF risk | Yes (cookies auto-sent) | No (if in header, not cookie) |
| XSS risk | No (httpOnly cookie) | Yes (if in localStorage) |
| Best for | Traditional web apps | APIs, microservices, mobile |
| Logout | Delete session → done | Blacklist token or wait for expiry |

### Recommendation

```
Traditional web app (server-rendered): Session cookies + httpOnly + Secure + SameSite
API backend (React/mobile frontend): JWT in httpOnly cookie (not localStorage!)
Microservice-to-microservice: JWT or mTLS
Third-party integrations: OAuth 2.0 + API keys
```

---

## Authorization — RBAC, ABAC, Permissions

### RBAC (Role-Based Access Control)

```
# Simple: User → Role → Permissions
User "Alice" → Role "admin" → Permissions: [read, write, delete]
User "Bob"   → Role "viewer" → Permissions: [read]

# Implementation:
roles:
  admin: [read, write, delete, manage_users]
  editor: [read, write]
  viewer: [read]

# Check: if user.role.permissions.includes("write") → allow

# Pros: Simple, easy to audit
# Cons: Can't express complex rules ("Bob can edit only HIS posts")
```

### ABAC (Attribute-Based Access Control)

```
# Complex: Policy evaluates attributes of user, resource, action, context
Policy: "Allow if user.department == resource.department AND action == 'read'"
Policy: "Allow if user.role == 'editor' AND resource.owner_id == user.id"
Policy: "Deny if request.ip NOT IN allowed_networks AND action == 'delete'"

# More flexible than RBAC but more complex to manage
# Used by: AWS IAM policies, Google Cloud IAM
```

### Resource-Level Authorization

```
# Most common in system design: "Can user X do action Y on resource Z?"

# Pattern: Ownership check
def can_edit_post(user, post):
    return post.author_id == user.id or user.role == "admin"

# Pattern: Team membership
def can_view_project(user, project):
    return user.id in project.team_member_ids or project.is_public

# Pattern: Hierarchical
def can_manage_org(user, org):
    return user.id == org.owner_id or user.id in org.admin_ids
```

---

## Password Security — Hashing & Storage

### NEVER Store Plaintext Passwords

```
❌ CATASTROPHICALLY BAD:
  INSERT INTO users (email, password) VALUES ('alice@email.com', 'mypassword123')

❌ STILL BAD (simple hash — rainbow table attack):
  password_hash = SHA256("mypassword123")
  
❌ BETTER BUT NOT ENOUGH (hash + salt):
  salt = random_bytes(16)
  password_hash = SHA256(salt + "mypassword123")
  
✅ CORRECT — Use bcrypt, scrypt, or Argon2:
  password_hash = bcrypt.hash("mypassword123", cost=12)
  # Output: $2b$12$LJ3m4ks6Yg8qV6R8VGhPZ.KxTfN1Xz8LLN9XJm6Dp3C9ByHVFhGy
  
  # bcrypt is:
  # 1. Salted (salt embedded in output)
  # 2. Slow by design (cost factor = intentionally expensive)
  # 3. Resistant to rainbow tables, GPU attacks, brute force
```

### Password Hashing Comparison

| Algorithm | Recommended? | Speed | Memory | Notes |
|-----------|-------------|-------|--------|-------|
| **bcrypt** | ✅ Yes | Slow (tunable) | Low | Industry standard, widely supported |
| **Argon2id** | ✅ Best | Slow (tunable) | High (tunable) | Winner of Password Hashing Competition |
| **scrypt** | ✅ Yes | Slow | High | Memory-hard (resists GPU attacks) |
| SHA-256 | ❌ No | Fast | Low | Too fast — brute force in seconds |
| MD5 | ❌ Never | Very fast | Low | Broken, collisions found |

### Verification Flow

```
# Registration:
hash = bcrypt.hash(user_password, cost=12)
db.save(user_email, hash)

# Login:
stored_hash = db.get_hash(user_email)
if bcrypt.verify(input_password, stored_hash):
    issue_jwt(user)
else:
    return "Invalid credentials"  # Don't say "wrong password" — info leak!
```

---

## Encryption Fundamentals

### Encoding vs Encryption vs Hashing

```
ENCODING (reversible, no key):
  Base64("hello") → "aGVsbG8="
  → Anyone can decode. NOT security. Just data format.
  
ENCRYPTION (reversible, WITH key):
  AES_encrypt("hello", secret_key) → "x7f9a2b..."
  AES_decrypt("x7f9a2b...", secret_key) → "hello"
  → Only someone with the key can read it. THIS is security.
  
HASHING (one-way, irreversible):
  SHA256("hello") → "2cf24dba5fb0a30e..."
  → Cannot get "hello" back from hash. Used for verification.
  → Same input always produces same output (deterministic)
```

---

## Symmetric vs Asymmetric Encryption

### Symmetric Encryption (One Key)

```
Same key encrypts AND decrypts:
  Key: "my-secret-key-256bit"
  
  Encrypt: AES-256-GCM("credit card: 4111...", key) → ciphertext
  Decrypt: AES-256-GCM(ciphertext, key) → "credit card: 4111..."

Algorithms: AES-256-GCM (recommended), AES-256-CBC, ChaCha20

Pros: Very fast (100x faster than asymmetric)
Cons: Key distribution problem (how do you securely share the key?)

Use for: Encrypting data at rest, bulk data encryption, database encryption
```

### Asymmetric Encryption (Two Keys)

```
Public key encrypts, Private key decrypts (or vice versa):

  Key pair: { public_key, private_key }
  
  Encrypt: RSA(message, public_key) → ciphertext
  Decrypt: RSA(ciphertext, private_key) → message
  
  Sign:   RSA(hash(message), private_key) → signature
  Verify: RSA(signature, public_key) → hash → compare

Algorithms: RSA-2048+, ECDSA (P-256), Ed25519

Pros: Solves key distribution (share public key openly!)
Cons: Slow (100x slower than symmetric)

Use for: TLS handshake, JWT signing, digital signatures, key exchange
```

### Hybrid Encryption (How TLS Actually Works)

```
TLS uses BOTH:
1. Asymmetric (RSA/ECDHE) → exchange a symmetric key securely
2. Symmetric (AES-256) → encrypt actual data with that key

Why hybrid?
  Asymmetric: Secure but SLOW (can't encrypt GB of data)
  Symmetric: FAST but key distribution problem
  Hybrid: Use asymmetric to exchange symmetric key → then use symmetric for speed!
```

---

## TLS — Encryption in Transit

### TLS Handshake (Simplified)

```
Client                                Server
  │                                      │
  │ ─── ClientHello ────────────────────→│  (supported ciphers, TLS version)
  │                                      │
  │ ←── ServerHello + Certificate ──────│  (chosen cipher, server's public cert)
  │                                      │
  │     Verify certificate chain          │
  │     (Is cert signed by trusted CA?)   │
  │                                      │
  │ ─── Key Exchange ──────────────────→│  (encrypted pre-master secret)
  │                                      │
  │     Both derive session key           │
  │                                      │
  │ ←→ Encrypted communication ←→───────│  (AES-256-GCM with session key)
  │                                      │

Total handshake: ~50-100ms (TLS 1.2), ~30-50ms (TLS 1.3)
TLS 1.3: Fewer round trips, stronger ciphers, faster
```

### Certificate Chain of Trust

```
Root CA (trusted by browsers/OS)
  └── Intermediate CA (signed by Root CA)
       └── Server Certificate (signed by Intermediate CA)
            domain: api.myapp.com
            expires: 2025-06-15
            public_key: ...

Verification: 
  Server cert → signed by Intermediate? ✅
  Intermediate → signed by Root CA? ✅
  Root CA → in browser's trusted store? ✅
  → Certificate VALID. Proceed with TLS.
```

### mTLS (Mutual TLS) — Service-to-Service

```
Regular TLS: Client verifies server (one-way)
mTLS: BOTH sides verify each other (two-way)

  Service A → presents its certificate → Service B verifies
  Service B → presents its certificate → Service A verifies
  
  → Both services are authenticated. No JWT/API key needed.
  
Use case: Zero trust microservices, service mesh (Istio, AWS App Mesh)
AWS: API Gateway supports mTLS for client authentication
```

---

## Encryption at Rest

### What to Encrypt

```
MUST encrypt:
  • Database storage (RDS, DynamoDB, Aurora)
  • Object storage (S3 buckets)
  • Block storage (EBS volumes)
  • Backups and snapshots
  • Log files (may contain PII)
  • Message queues (SQS, Kafka)

Field-level encryption (additional layer):
  • Credit card numbers → tokenized (never store raw!)
  • SSN, government IDs → AES-256 encrypted
  • Health records (HIPAA) → encrypted with per-patient key
  • API keys, secrets → encrypted in Secrets Manager

Envelope encryption pattern:
  1. KMS generates Data Encryption Key (DEK)
  2. DEK encrypts your data locally (fast, AES-256)
  3. KMS master key encrypts the DEK (stored alongside data)
  4. To decrypt: KMS decrypts DEK → DEK decrypts data
  → Data never leaves your service. Only small DEK goes to KMS.
```

---

## End-to-End Encryption (E2EE)

### How WhatsApp/Signal E2EE Works

```
Alice wants to message Bob:

1. Key Exchange (Signal Protocol):
   Alice has: { alice_public, alice_private }
   Bob has:   { bob_public, bob_private }
   
   Alice gets Bob's public key from server
   Shared secret = ECDH(alice_private, bob_public)
   → Same as: ECDH(bob_private, alice_public)
   → Both derive the SAME session key without revealing private keys!

2. Message Encryption:
   Alice: encrypted_msg = AES-256-GCM("Hello Bob", session_key)
   
3. Server stores encrypted_msg (cannot decrypt — no session key!)

4. Bob receives + decrypts:
   Bob: "Hello Bob" = AES-256-GCM(encrypted_msg, session_key)

Server is a "dumb pipe" — routes encrypted blobs, can't read content.
Even if server is hacked, messages are safe (no keys on server).
```

### E2EE Challenges

```
Key management: What if Bob gets a new phone? (key changes → verify!)
Group chats: Distribute key to N members securely (Sender Key protocol)
Search: Can't search encrypted messages server-side
Backup: iCloud/Google backup of messages → NOT end-to-end encrypted!
Law enforcement: Can't comply with data requests (by design)
```

---

## Key Management & Rotation

### Key Rotation — Why and How

```
WHY rotate keys?
  • Limit blast radius if key is compromised
  • Compliance requirements (PCI DSS: rotate annually)
  • Cryptographic best practice (limit amount encrypted per key)

HOW to rotate:
  1. Generate new key (version N+1)
  2. New data → encrypted with new key (version N+1)
  3. Old data → still readable with old key (version N)
  4. Background process: re-encrypt old data with new key
  5. After re-encryption complete → deactivate old key
  
  Key versioning: Store key_version alongside encrypted data
  { data: "encrypted...", key_version: 3 }
  → Decrypt knows which key version to use

AWS KMS:
  • Automatic key rotation (yearly) for customer-managed keys
  • KMS handles versioning transparently
  • Old ciphertexts still decryptable (KMS keeps old key material)
```

---

## Secret Management

### Never Hardcode Secrets

```
❌ CATASTROPHIC:
  DB_PASSWORD = "super-secret-123"  // in source code
  aws_access_key = "AKIA..."       // committed to git

❌ STILL BAD:
  .env file checked into git
  Environment variable visible in process listing

✅ CORRECT — Use a secret manager:
  AWS Secrets Manager:  Auto-rotates DB passwords, API keys
  AWS Parameter Store:  Config values (free tier)
  HashiCorp Vault:      Self-hosted, advanced policies
  
  Application reads secret at runtime:
  password = secretsmanager.get_secret("prod/db/password")
  
  Benefits:
  • Secrets never in code or git
  • Access controlled via IAM
  • Audit trail (who accessed which secret, when)
  • Auto-rotation (zero-downtime password changes)
```

---

## API Security Patterns

### Authentication & Authorization

```
# 1. Bearer Token (JWT)
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
→ Verify signature, check expiry, extract claims, authorize

# 2. API Key (for service-to-service or public APIs)
X-API-Key: sk_live_abc123
→ Simple, but: can't embed user context, hard to rotate per-client

# 3. OAuth 2.0 Access Token
Authorization: Bearer <oauth_access_token>
→ Scoped permissions, delegated access ("App X can read my emails")

# 4. Request Signing (AWS Signature V4)
Authorization: AWS4-HMAC-SHA256 Credential=AKIA.../20240115/us-east-1/s3/aws4_request, ...
→ Signs request with secret key — server verifies without seeing secret
→ Protects against replay attacks (includes timestamp)
```

### Input Validation (CRITICAL)

```
VALIDATE EVERYTHING. TRUST NOTHING.

✅ Validate:
  • Data types: Is "age" actually a number?
  • Ranges: Is "quantity" between 1 and 1000?
  • Format: Does "email" match email regex?
  • Length: Is "name" under 255 chars? (prevent buffer overflow)
  • Enum values: Is "status" one of ["active", "inactive"]?
  
✅ Sanitize:
  • HTML encode user input before rendering (prevent XSS)
  • Parameterized queries (prevent SQL injection)
  • URL encode path parameters

❌ NEVER:
  • Concatenate user input into SQL queries
  • Render user input as raw HTML
  • Use user input in system commands (OS injection)
  • Trust client-side validation alone (always validate server-side too)
```

### Rate Limiting

```
# Prevent abuse, brute force, DDoS
# Patterns:
  Fixed window: 100 requests per minute per user
  Sliding window: 100 requests in any rolling 60-second window
  Token bucket: Refill 10 tokens/sec, max 100 tokens (burst allowed)
  
# What to limit:
  Login attempts: 5 per minute per IP (prevent brute force)
  API calls: 1000/min per API key (prevent abuse)
  Password resets: 3 per hour per email (prevent enumeration)
  File uploads: 10 MB/min per user (prevent resource exhaustion)

# Response: 429 Too Many Requests
  Retry-After: 60   ← tell client when to retry
```

---

## OWASP Top 10 — Common Vulnerabilities

### 1. Injection (SQL, NoSQL, OS Command)

```
❌ VULNERABLE:
  query = "SELECT * FROM users WHERE email = '" + user_input + "'"
  user_input = "'; DROP TABLE users; --"
  → Executes: SELECT * FROM users WHERE email = ''; DROP TABLE users; --'

✅ FIX — Parameterized queries:
  query = "SELECT * FROM users WHERE email = $1"
  params = [user_input]
  → user_input treated as DATA, never as SQL code
```

### 2. Broken Authentication

```
Fix: MFA, bcrypt passwords, short JWT expiry, secure session management
     Rate limit login attempts, account lockout after 5 failures
```

### 3. Sensitive Data Exposure

```
Fix: Encrypt at rest (AES-256), encrypt in transit (TLS 1.2+)
     Don't log PII, mask credit card numbers (show last 4 only)
     Don't return sensitive data in API responses unless needed
```

### 4. Broken Access Control

```
❌ VULNERABLE: User 123 can access /api/users/456/profile (no ownership check)
✅ FIX: if (request.user_id != resource.owner_id) return 403 Forbidden
```

### 5. Security Misconfiguration

```
Fix: Disable debug mode in production, remove default passwords
     Security headers: HSTS, Content-Security-Policy, X-Frame-Options
     Keep dependencies updated (Dependabot, Snyk)
```

### 6-10: XSS, Insecure Deserialization, Components with Vulnerabilities, Logging Failures, SSRF

```
XSS: Sanitize output, Content-Security-Policy header
Deserialization: Don't deserialize untrusted data
Dependencies: Regular vulnerability scanning (npm audit, pip-audit)
Logging: Log security events, don't log passwords/tokens
SSRF: Validate/whitelist URLs, don't fetch arbitrary user-provided URLs
```

---

## CORS, CSRF & XSS Prevention

### CORS (Cross-Origin Resource Sharing)

```
# Browser blocks requests from different origins by default
# CORS headers tell browser: "This origin is allowed"

Access-Control-Allow-Origin: https://myapp.com    ← ONLY this origin
Access-Control-Allow-Methods: GET, POST, PUT       ← these methods
Access-Control-Allow-Headers: Authorization, Content-Type
Access-Control-Allow-Credentials: true             ← send cookies
Access-Control-Max-Age: 86400                       ← cache preflight 24h

❌ NEVER: Access-Control-Allow-Origin: * (with credentials)
   → Allows ANY website to make authenticated requests to your API!
```

### CSRF (Cross-Site Request Forgery)

```
Attack: Malicious site tricks user's browser into making request to your API
  (because browser auto-sends cookies to your domain)
  
Prevention:
  1. CSRF token: Server generates random token per session
     → Include in form as hidden field, verify on server
  2. SameSite cookies: Set-Cookie: session=abc; SameSite=Strict
     → Browser won't send cookie for cross-origin requests
  3. Check Origin/Referer header: Reject if not from your domain
  
If using JWT in Authorization header (not cookie): CSRF not a risk
  (browser doesn't auto-send Authorization headers)
```

### XSS (Cross-Site Scripting)

```
Attack: Attacker injects JavaScript into your page
  <script>steal(document.cookie)</script>
  
Prevention:
  1. HTML encode output: < → &lt;  > → &gt;  " → &quot;
  2. Content-Security-Policy: script-src 'self' ← block inline scripts
  3. httpOnly cookies: Not accessible via JavaScript
  4. Use framework auto-escaping (React, Angular auto-escape by default)
```

---

## Zero Trust Architecture

```
Traditional: "Trust everything inside the network perimeter"
Zero Trust: "Never trust, always verify" — every request, every time

Principles:
1. Verify explicitly: Authenticate + authorize every request (not just at edge)
2. Least privilege: Minimum permissions needed, no more
3. Assume breach: Design as if attacker is already inside your network

Implementation:
  • mTLS between all services (even internal)
  • No "trusted network" — all traffic encrypted
  • Identity-based access (not network-based)
  • Short-lived credentials (rotate frequently)
  • Micro-segmentation (each service isolated)
  
AWS implementation:
  • VPC + Security Groups (network isolation)
  • IAM roles per service (least privilege)
  • VPC endpoints (keep traffic off internet)
  • mTLS via API Gateway or service mesh
  • All data encrypted at rest + in transit
```

---

## Data Privacy — GDPR, PCI DSS, HIPAA

### GDPR (General Data Protection Regulation)

```
Applies to: Any service handling EU citizen data
Key requirements:
  • Right to be forgotten: Delete ALL user data on request
  • Data minimization: Collect only what you need
  • Consent: Explicit opt-in for data collection
  • Encryption: Protect personal data
  • Breach notification: Report within 72 hours
  • Data portability: User can export their data

Implementation:
  • Soft-delete with purge job (cascade delete across all services)
  • Encryption at rest for PII fields
  • Audit log: Who accessed what data, when
  • Data retention policies (auto-delete after N days)
```

### PCI DSS (Payment Card Industry)

```
Applies to: Any service processing credit card data
Key requirements:
  • NEVER store full card number (tokenize via Stripe/Braintree)
  • Encrypt card data in transit and at rest
  • Regular security testing (penetration tests)
  • Access control: Need-to-know basis
  • Network segmentation: Isolate cardholder data environment
  
Best practice: Use Stripe/Braintree → card number never touches YOUR servers
  → Reduces PCI scope from SAQ D (315 requirements) to SAQ A (22 requirements)
```

### HIPAA (Health Insurance Portability and Accountability)

```
Applies to: Any service handling health records (PHI)
Key requirements:
  • Sign BAA with cloud provider (AWS, GCP)
  • Encryption everywhere (at rest + in transit)
  • Access logging and audit trail
  • Minimum necessary access
  • Data backup and recovery plan
```

---

## Security by System Design Problem

```
┌──────────────────────┬─────────────────────────────────────────────┐
│ System               │ Key Security Concerns                        │
├──────────────────────┼─────────────────────────────────────────────┤
│ Chat (WhatsApp)      │ E2EE, key exchange, message expiry          │
│ Payment (Stripe)     │ PCI DSS, tokenization, idempotency          │
│ Auth System          │ bcrypt, JWT, refresh tokens, MFA, brute force│
│ URL Shortener        │ Rate limiting, malicious URL detection       │
│ Social Media         │ Privacy controls, content moderation, XSS    │
│ File Storage         │ Encryption at rest, signed URLs, virus scan  │
│ E-Commerce           │ Payment security, CSRF, inventory fraud      │
│ Healthcare           │ HIPAA, field-level encryption, audit logs    │
│ Multi-tenant SaaS    │ Tenant isolation (IAM/RLS), data separation │
│ Video Streaming      │ DRM, signed URLs, geo-restriction           │
│ Banking              │ MFA, transaction signing, fraud detection    │
│ API Platform         │ API keys, rate limiting, OAuth scopes       │
└──────────────────────┴─────────────────────────────────────────────┘
```

---

## Real-World Security Architectures

### E-Commerce Payment Flow

```
Browser ──HTTPS──→ CloudFront/WAF ──→ API Gateway ──→ Lambda
                    (DDoS protection)  (Cognito auth)   │
                                                         │
                                              Stripe Tokenize (PCI)
                                              → Card number NEVER stored
                                              → Only token stored in DynamoDB
                                              → Token used for future charges
                                                         │
                                              KMS (encrypt order data)
                                              DynamoDB (encrypted at rest)
                                              Secrets Manager (Stripe API key)
                                              CloudTrail (audit every action)
```

### Microservice Security (Zero Trust)

```
                    mTLS
Service A ←─────────────────→ Service B
    │         Encrypted          │
    │         + Verified         │
    │                            │
    └── IAM Role A               └── IAM Role B
        (can only call B)             (can only access DB X)
        
Each service:
  ✅ Has its own IAM role (least privilege)
  ✅ Communicates via mTLS (mutual authentication)
  ✅ Encrypts all data at rest (own KMS key)
  ✅ Logs every access (CloudTrail)
  ✅ Validates JWT on every request (even internal)
```

---

## Interview Talking Points & Scripts

### "How would you secure this system?"

> *"I'd implement defense in depth across multiple layers. At the edge: CloudFront with WAF for DDoS protection and OWASP rule sets. Authentication: JWT with RS256 — short-lived access tokens (15 min) plus refresh tokens in httpOnly cookies. Authorization: RBAC checked on every API endpoint, with resource-level checks for ownership. All data encrypted: TLS 1.2+ in transit, AES-256 at rest via KMS, and field-level encryption for PII like SSNs. Passwords stored with bcrypt (cost factor 12). API rate limiting at 1000 req/min per API key. Secrets in AWS Secrets Manager with auto-rotation. MFA for admin operations. Full audit trail via CloudTrail."*

### "How does E2EE work?"

> *"In end-to-end encryption, like Signal/WhatsApp, each user has a public-private key pair. When Alice messages Bob, she gets Bob's public key, performs a Diffie-Hellman key exchange to derive a shared session key, then encrypts the message with AES-256 using that key. The server only sees encrypted bytes — it can't decrypt because it doesn't have either private key. The challenge is key management: if Bob gets a new phone, his keys change, and Alice needs to verify the new key to prevent man-in-the-middle attacks."*

### "How do you handle PCI compliance?"

> *"I'd never let card numbers touch our servers. Use Stripe Elements — the card form is Stripe's iframe, the number goes directly to Stripe via their SDK. We receive only a token, which we store in our encrypted DynamoDB. This reduces PCI scope from SAQ D (315 requirements) to SAQ A (22 requirements). All payment data encrypted at rest with KMS, TLS in transit, and the Stripe API key stored in Secrets Manager with auto-rotation."*

---

## Common Interview Mistakes

| Mistake | Why It's Wrong | Better |
|---------|---------------|--------|
| "We'll add security later" | Security must be designed in from the start | Discuss auth, encryption, rate limiting in your design |
| "Store passwords with SHA-256" | SHA-256 is too fast — brute forced in seconds | Use bcrypt, Argon2, or scrypt |
| "JWT in localStorage" | XSS can steal the token | httpOnly cookie or in-memory |
| "HTTPS is enough" | HTTPS protects transit only, not at rest | Encrypt at rest too (KMS/AES-256) |
| "One API key for everything" | No granularity, can't revoke per-client | Per-client keys with scoped permissions |
| "Trust the internal network" | Attackers can breach the perimeter | Zero trust: verify every request |
| No mention of rate limiting | Brute force, DDoS, abuse all possible | Rate limit: login (5/min), API (1000/min) |
| "Base64 encode sensitive data" | Base64 is NOT encryption — anyone can decode | AES-256 encryption with proper key management |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│           SECURITY & ENCRYPTION CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  AUTHENTICATION:                                             │
│    JWT: RS256, 15-min expiry, refresh tokens, httpOnly cookie│
│    OAuth 2.0: Authorization Code + PKCE for web/mobile      │
│    MFA: TOTP (Google Authenticator) for sensitive operations │
│    mTLS: Service-to-service in zero trust architectures      │
│                                                              │
│  AUTHORIZATION:                                              │
│    RBAC: Role → Permissions (simple)                        │
│    ABAC: Attribute-based policies (complex, flexible)       │
│    Resource-level: Check ownership on every operation        │
│    Least privilege: Minimum permissions needed               │
│                                                              │
│  PASSWORDS:                                                  │
│    bcrypt (cost=12) or Argon2id — NEVER SHA-256/MD5         │
│    Rate limit login attempts (5/min per IP)                  │
│    Account lockout after N failures                          │
│                                                              │
│  ENCRYPTION:                                                 │
│    Symmetric (AES-256-GCM): Fast, for data at rest          │
│    Asymmetric (RSA/ECDSA): Key exchange, signatures         │
│    Hybrid (TLS): Asymmetric handshake → symmetric data      │
│    At rest: KMS + envelope encryption for all storage        │
│    In transit: TLS 1.2+ everywhere (enforce via policy)     │
│    E2EE: Signal Protocol (ECDH + AES), server can't decrypt │
│                                                              │
│  API SECURITY:                                               │
│    Input validation: Type, range, length, format             │
│    Rate limiting: Per-user, per-IP, per-API-key              │
│    CORS: Whitelist specific origins (never *)                │
│    CSRF: SameSite cookies or JWT in header                   │
│    XSS: HTML encode output, CSP headers, httpOnly cookies   │
│                                                              │
│  OWASP TOP 3:                                               │
│    Injection: Parameterized queries (never concatenate!)     │
│    Broken Auth: bcrypt, MFA, short tokens, rate limit       │
│    Data Exposure: Encrypt, mask, minimize, don't log PII    │
│                                                              │
│  SECRETS:                                                    │
│    Secrets Manager / Vault (NEVER in code/env vars/git)     │
│    Auto-rotate DB passwords, API keys                        │
│    IAM roles (not access keys) for AWS services              │
│                                                              │
│  COMPLIANCE:                                                 │
│    GDPR: Right to delete, consent, encryption, 72h breach   │
│    PCI: Tokenize cards (Stripe), never store card numbers   │
│    HIPAA: BAA, encryption, audit logs, access controls      │
│                                                              │
│  ZERO TRUST: Never trust, always verify                     │
│    mTLS between services, IAM per service, encrypt all data │
│    No trusted network, short-lived credentials              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **[27b: AWS Security & Encryption Real-World](./27b-aws-security-and-encryption-real-world.md)** — IAM, KMS, VPC, WAF, Cognito patterns
- **Topic 15: Rate Limiting** — Token bucket, sliding window algorithms
- **Topic 25: API Design** — Authentication headers, versioning
- **[AWS Architecture Mapping](./52-architecting-on-aws-service-mapping.md)** — Vault→Secrets Manager, Auth0→Cognito

---

*This document is part of the System Design Interview Deep Dive series.*
