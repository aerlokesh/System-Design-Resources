# Design SSO (Single Sign-On) Login — Hello Interview Framework

> **Question**: Design a Single Sign-On system where a user logs in once through a central Identity Provider (IDP) and gains access to multiple independent Service Providers (e.g., Gmail, YouTube, Google Drive) without re-authenticating. Support login, token refresh, logout (including single-logout across all SPs), and session management. Think Google's SSO or Okta.
>
> **Asked at**: Amazon, Google, Microsoft, Okta
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

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
1. **Single Sign-On**: User authenticates once with the IDP. When they visit any registered Service Provider, the SP redirects to the IDP, which recognizes the existing session and issues a new SP-specific token — no re-login needed.
2. **Authentication (Login)**: User provides credentials (email + password) to the IDP. IDP validates credentials, creates a session, issues a short-lived access token (JWT, 15 min) and a long-lived refresh token (7 days) per SP.
3. **Token Refresh**: When an access token expires, the SP requests a new one using the refresh token. No user interaction required. If the refresh token is also expired or revoked → redirect to IDP login.
4. **Single Logout**: When a user logs out from one SP, they are logged out from all SPs and the IDP session is destroyed. All refresh tokens are revoked.
5. **Session Management**: The IDP maintains a central login session. Each SP gets its own access token (different audience claim). Tokens are per-SP to prevent cross-SP token misuse.
6. **Service Provider Registration**: SPs register with the IDP (client_id, client_secret, redirect_uri, allowed scopes). Standard OAuth 2.0 / OpenID Connect.

#### Nice to Have (P1)
- Multi-Factor Authentication (MFA) — TOTP, SMS, push notification.
- Social login (Login with Google/GitHub) via upstream IDP federation.
- Role-Based Access Control (RBAC) — different permissions per SP.
- Account lockout after N failed attempts.
- Passwordless login (magic link, WebAuthn/passkeys).
- Admin console for managing users, SPs, and policies.
- Audit log of all login/logout/token events.

#### Below the Line (Out of Scope)
- User registration / profile management (assume separate User Service).
- Authorization policies (fine-grained RBAC within each SP).
- SAML 2.0 (focus on OAuth 2.0 + OIDC).
- Federated identity across different organizations.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Login Latency** | < 500ms (credential check + token issuance) | Must feel instant; users abandon slow login |
| **Token Validation** | < 5ms at SP side (local JWT verification) | Every API call validates the token; can't afford round-trip to IDP |
| **Redirect Latency** | < 300ms (SP → IDP → SP with token) | SSO redirect should feel transparent |
| **Availability** | 99.99% for token validation; 99.9% for login | If IDP is down, no one can log in to any SP — critical path |
| **Throughput** | 10K logins/sec peak; 100K token validations/sec | Enterprise SSO during morning login rush (9 AM across time zones) |
| **Token Security** | Access token: 15 min TTL; Refresh token: 7 days | Short-lived tokens limit blast radius of compromise |
| **Scale** | 100M users, 50 SPs, 1B active sessions | Enterprise-to-consumer scale (Google, Microsoft) |
| **Consistency** | Strong for credential checks; eventual for session propagation | Wrong password must always be rejected; logout propagation can take 1-2 seconds |

### Capacity Estimation

```
Users & Traffic:
  Total users: 100M
  DAU: 30M
  Logins/day: 30M (most users login once/day via SSO)
  Login QPS: 30M / 86400 ≈ 350 avg → 10K peak (9 AM rush)
  
Token Operations:
  Access token validations: 100K/sec (every SP API call)
  Token refresh: ~2M/day (tokens expire every 15 min, ~4 refreshes/session)
  Refresh QPS: 2M / 86400 ≈ 23 avg → 500 peak

Session Storage:
  Active sessions: 30M DAU × 1 IDP session = 30M sessions
  Per session: user_id (16B) + session_id (16B) + created_at (8B) + 
               SP tokens list (50 SPs × 32B = 1.6KB) + metadata (200B) ≈ 2KB
  Total: 30M × 2KB = 60 GB (fits in Redis cluster)

Token Storage:
  Refresh tokens in DB: 30M users × avg 3 SPs = 90M refresh tokens
  Per token: 200 bytes → 18 GB (PostgreSQL)
  
Blacklist (logout):
  Revoked JTI (JWT ID) entries: ~1M/day (logout events)
  Per entry: JTI (16B) + expiry (8B) = 24B
  Redis blacklist: ~24 MB/day (with 15-min TTL, only ~100K entries at any time = 2.4 MB)

Public Key Cache:
  IDP public keys (for JWT verification): 3 keys × 2KB = 6 KB (negligible, cached at every SP)
  Key rotation: every 90 days

Infrastructure:
  IDP Service: 20 pods (stateless)
  Redis cluster: 6 nodes (session store + blacklist)
  PostgreSQL: primary + 3 replicas (user credentials + refresh tokens)
  Load balancer: per region
  3 regions: ~$30-50K/month
```

---

## 2️⃣ Core Entities

### Entity 1: User
```java
public class User {
    String userId;              // UUID
    String email;               // "alice@company.com" (unique)
    String passwordHash;        // bcrypt hash (cost factor 12)
    String name;
    boolean mfaEnabled;
    MfaMethod mfaMethod;        // TOTP, SMS, PUSH
    String mfaSecret;           // encrypted TOTP secret
    AccountStatus status;       // ACTIVE, LOCKED, DISABLED
    int failedLoginAttempts;
    Instant lastLoginAt;
    Instant createdAt;
}

public enum AccountStatus { ACTIVE, LOCKED, DISABLED }
public enum MfaMethod { TOTP, SMS, PUSH, NONE }
```

### Entity 2: ServiceProvider (Client)
```java
public class ServiceProvider {
    String clientId;            // UUID, issued on registration
    String clientSecret;        // hashed, used for confidential clients
    String name;                // "Gmail", "YouTube", "Drive"
    String redirectUri;         // "https://mail.google.com/callback"
    List<String> allowedScopes; // ["openid", "profile", "email"]
    boolean isActive;
    Instant registeredAt;
}
```

### Entity 3: IDPSession (Central Login Session)
```java
public class IDPSession {
    String sessionId;           // UUID — stored as browser cookie at IDP domain
    String userId;
    Instant createdAt;
    Instant lastActivityAt;
    Instant expiresAt;          // 8 hours (sliding window)
    String ipAddress;
    String userAgent;
    Set<String> activeSpIds;    // SPs this session has issued tokens for
}
```

### Entity 4: RefreshToken
```java
public class RefreshToken {
    String tokenId;             // UUID
    String tokenHash;           // SHA-256 hash of the actual token value
    String userId;
    String clientId;            // which SP this refresh token is for
    Instant issuedAt;
    Instant expiresAt;          // 7 days
    TokenStatus status;         // ACTIVE, REVOKED, EXPIRED
    String sessionId;           // links back to IDP session (for single-logout)
}

public enum TokenStatus { ACTIVE, REVOKED, EXPIRED }
```

### Entity 5: AccessToken (JWT — not stored, verified locally)
```java
// JWT Payload (claims)
public class AccessTokenClaims {
    String sub;                 // user_id
    String iss;                 // "https://idp.company.com" (issuer)
    String aud;                 // "gmail" (audience — the specific SP)
    String jti;                 // unique token ID (for blacklist check on logout)
    long iat;                   // issued at (epoch seconds)
    long exp;                   // expires at (iat + 900 = 15 minutes)
    List<String> scopes;        // ["openid", "profile", "email"]
    String sessionId;           // IDP session reference
}
```

### Entity 6: AuditEvent
```java
public class AuditEvent {
    String eventId;
    String userId;
    String clientId;            // which SP
    EventType eventType;        // LOGIN, LOGOUT, TOKEN_REFRESH, TOKEN_REVOKE, LOGIN_FAILED
    String ipAddress;
    String userAgent;
    Instant timestamp;
    Map<String, String> metadata; // {"reason": "expired_refresh_token"}
}

public enum EventType { LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, TOKEN_REFRESH, TOKEN_REVOKE, SESSION_EXPIRED }
```

---

## 3️⃣ API Design

### 1. Authorize (SP redirects user to IDP)
```
GET /oauth/authorize?client_id=gmail&redirect_uri=https://mail.google.com/callback&response_type=code&scope=openid+profile+email&state=xyz123

Flow:
  1. SP detects user has no valid access token → redirects browser here
  2. IDP checks for existing IDP session (cookie: idp_session_id)
     a. If session exists and valid → skip login, issue auth code immediately
     b. If no session → show login page
  3. After login (or session found) → redirect back to SP:
     302 Redirect → https://mail.google.com/callback?code=AUTH_CODE_123&state=xyz123
```

### 2. Login (User submits credentials to IDP)
```
POST /auth/login

Request:
{
  "email": "alice@company.com",
  "password": "s3cureP@ss!",
  "client_id": "gmail",
  "redirect_uri": "https://mail.google.com/callback",
  "state": "xyz123"
}

Response (302 Redirect on success):
  Set-Cookie: idp_session_id=SESS_ABC123; Domain=idp.company.com; HttpOnly; Secure; SameSite=Lax; Max-Age=28800
  Location: https://mail.google.com/callback?code=AUTH_CODE_456&state=xyz123

Response (401 on failure):
{
  "error": "invalid_credentials",
  "message": "Email or password is incorrect.",
  "remaining_attempts": 2
}
```

### 3. Token Exchange (SP exchanges auth code for tokens)
```
POST /oauth/token

Request (server-to-server, confidential):
{
  "grant_type": "authorization_code",
  "code": "AUTH_CODE_456",
  "client_id": "gmail",
  "client_secret": "SP_SECRET_789",
  "redirect_uri": "https://mail.google.com/callback"
}

Response (200 OK):
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "RT_XYZ_OPAQUE_STRING_789",
  "id_token": "eyJhbGciOiJSUzI1NiIs...",
  "scope": "openid profile email"
}
```

### 4. Token Refresh
```
POST /oauth/token

Request:
{
  "grant_type": "refresh_token",
  "refresh_token": "RT_XYZ_OPAQUE_STRING_789",
  "client_id": "gmail",
  "client_secret": "SP_SECRET_789"
}

Response (200 OK):
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...(NEW)...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "RT_NEW_OPAQUE_STRING_012"
}

Response (401 — refresh token revoked/expired):
{
  "error": "invalid_grant",
  "message": "Refresh token has been revoked. Please re-authenticate."
}
→ SP redirects user to /oauth/authorize (which redirects to login if IDP session also expired)
```

### 5. Logout (Single Logout)
```
POST /auth/logout

Headers:
  Cookie: idp_session_id=SESS_ABC123

Response (200 OK):
{
  "message": "Logged out from all services.",
  "services_logged_out": ["gmail", "youtube", "drive"]
}
  Set-Cookie: idp_session_id=; Max-Age=0  (clear session cookie)

Backend actions:
  1. Delete IDP session from Redis
  2. Revoke ALL refresh tokens linked to this session (across all SPs)
  3. Add all active JTIs to Redis blacklist (TTL = remaining access token lifetime)
  4. Notify SPs via back-channel logout (webhook) — optional for immediate effect
```

### 6. JWKS Endpoint (Public keys for token verification)
```
GET /.well-known/jwks.json

Response (200 OK, Cache-Control: max-age=86400):
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-2025-Q1",
      "use": "sig",
      "alg": "RS256",
      "n": "0vx7agoebGcQSuu...",
      "e": "AQAB"
    },
    {
      "kty": "RSA",
      "kid": "key-2024-Q4",
      "use": "sig",
      "alg": "RS256",
      "n": "ofgWCuLjybRlzo0...",
      "e": "AQAB"
    }
  ]
}

SPs cache these keys locally. When verifying a JWT:
  1. Read 'kid' from JWT header → find matching key in cached JWKS
  2. Verify signature using the public key
  3. Check exp, iss, aud claims
  4. If kid not found in cache → re-fetch JWKS (key rotation happened)
```

### 7. UserInfo Endpoint (OIDC standard)
```
GET /oauth/userinfo
Authorization: Bearer {access_token}

Response (200 OK):
{
  "sub": "user_abc123",
  "name": "Alice Johnson",
  "email": "alice@company.com",
  "email_verified": true,
  "picture": "https://cdn.company.com/avatars/alice.jpg"
}
```

---

## 4️⃣ Data Flow

### Flow 1: First Login (No Existing Session) — Full OAuth 2.0 + OIDC
```
1. User visits https://mail.google.com (Gmail = Service Provider A)
   → SP checks: does user have a valid access_token? NO
   ↓
2. SP redirects browser to IDP:
   302 → https://idp.company.com/oauth/authorize?client_id=gmail&redirect_uri=...&state=xyz
   ↓
3. IDP checks: does browser have idp_session_id cookie? NO
   → IDP shows login page
   ↓
4. User submits email + password
   POST /auth/login { email, password, client_id, redirect_uri, state }
   ↓
5. IDP validates credentials:
   a. Lookup user by email in PostgreSQL
   b. bcrypt.verify(password, stored_hash) — ~200ms (intentionally slow)
   c. If MFA enabled → challenge MFA (separate flow)
   d. If credentials valid:
      i.   Create IDP session in Redis: SET session:{session_id} {user_id, created_at, ...} EX 28800
      ii.  Generate authorization code (random, 1-time use, 60s expiry) → store in Redis
      iii. Set session cookie on browser: idp_session_id=SESS_ABC123
      iv.  Redirect: 302 → https://mail.google.com/callback?code=AUTH_CODE_456&state=xyz
   ↓
6. Gmail backend exchanges code for tokens (server-to-server):
   POST https://idp.company.com/oauth/token { grant_type=authorization_code, code=AUTH_CODE_456, client_id, client_secret }
   ↓
7. IDP validates auth code, then:
   a. Generate access_token (JWT, signed with RSA private key, 15 min TTL):
      { sub: user_id, aud: "gmail", iss: "idp.company.com", exp: now+900, jti: unique_id }
   b. Generate refresh_token (opaque string, 7 day TTL):
      Store hash in PostgreSQL: { token_hash, user_id, client_id, session_id, expires_at }
   c. Generate id_token (JWT with user profile claims, per OIDC)
   d. Return all tokens to Gmail backend
   ↓
8. Gmail stores refresh_token securely, sets access_token in user's session/cookie
   → User is now logged into Gmail ✅

Total latency: ~500ms (bcrypt dominates)
```

### Flow 2: SSO — User Visits Second SP (YouTube, Session Already Exists)
```
1. User visits https://youtube.com (Service Provider B)
   → SP checks: does user have a valid access_token? NO (first visit to YouTube)
   ↓
2. YouTube redirects browser to IDP:
   302 → https://idp.company.com/oauth/authorize?client_id=youtube&redirect_uri=...&state=abc
   ↓
3. IDP checks: does browser have idp_session_id cookie? YES (from Gmail login)
   → IDP looks up session in Redis: GET session:SESS_ABC123 → valid, not expired
   ↓
4. IDP skips login page entirely:
   a. Generate new authorization code for YouTube
   b. Redirect immediately: 302 → https://youtube.com/callback?code=AUTH_CODE_789&state=abc
   ↓
5. YouTube exchanges code for tokens (same as Flow 1, Step 6-7)
   → IDP issues access_token with aud:"youtube" (different audience than Gmail's token)
   → IDP issues separate refresh_token for YouTube
   → IDP adds "youtube" to session's activeSpIds set
   ↓
6. User is now logged into YouTube ✅ — WITHOUT entering credentials again

Total latency: ~200ms (no bcrypt, just session lookup + token generation)
The user saw: a brief redirect flash (SP → IDP → SP), < 300ms total
```

### Flow 3: Token Refresh (Access Token Expired)
```
1. Gmail's access_token expires after 15 minutes
   → Gmail API returns 401 to frontend
   ↓
2. Gmail backend calls IDP:
   POST /oauth/token { grant_type=refresh_token, refresh_token=RT_XYZ, client_id, client_secret }
   ↓
3. IDP validates refresh token:
   a. Hash the provided token: SHA-256(RT_XYZ) → lookup in PostgreSQL
   b. Check: status=ACTIVE? Not expired? client_id matches?
   c. Check: linked session still exists in Redis? (If session deleted by logout → reject)
   d. If valid:
      i.   Issue new access_token (JWT, 15 min)
      ii.  Issue new refresh_token (rotate: old one revoked, new one stored)
      iii. Return to Gmail
   ↓
4. Gmail updates its stored tokens, resumes API call seamlessly
   → User doesn't notice anything

Total latency: ~50ms (DB lookup + token signing)
```

### Flow 4: Single Logout (User Logs Out from Any SP)
```
1. User clicks "Logout" in Gmail
   → Gmail calls IDP: POST /auth/logout (with session cookie or user's session reference)
   ↓
2. IDP performs single-logout:
   a. Lookup IDP session in Redis: GET session:SESS_ABC123
      → Returns: { userId, activeSpIds: ["gmail", "youtube", "drive"] }
   
   b. Revoke ALL refresh tokens for this session:
      UPDATE refresh_tokens SET status='REVOKED' WHERE session_id='SESS_ABC123'
      → All 3 SPs' refresh tokens are now invalid
   
   c. Blacklist active JTIs (JWT IDs):
      For each recently issued access token's JTI:
      SET blacklist:{jti} 1 EX {remaining_ttl}
      → Any SP checking the blacklist will reject these tokens immediately
   
   d. Delete IDP session from Redis:
      DEL session:SESS_ABC123
   
   e. Clear session cookie:
      Set-Cookie: idp_session_id=; Max-Age=0
   
   f. (Optional) Back-channel logout: notify each SP via webhook
      POST https://youtube.com/.well-known/logout { logout_token: JWT with sub + sid }
      POST https://drive.google.com/.well-known/logout { ... }
   ↓
3. Result:
   - Gmail: logged out immediately (initiated the logout)
   - YouTube: access_token still works for up to 15 min (unless SP checks blacklist),
              but refresh will fail → user redirected to login on next refresh
   - Drive: same as YouTube
   - If back-channel logout is implemented → SPs clear local sessions immediately

Total latency: ~100ms for the logout initiator
Propagation to other SPs: < 15 min (token expiry) or < 2s (with back-channel logout)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                CLIENTS                                           │
│                                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                       │
│  │  Gmail (SP A) │    │ YouTube (SP B)│    │ Drive (SP C) │                       │
│  │              │    │              │    │              │                       │
│  │ Has its own  │    │ Has its own  │    │ Has its own  │                       │
│  │ access_token │    │ access_token │    │ access_token │                       │
│  │ (aud:"gmail")│    │ (aud:"youtube")│  │ (aud:"drive")│                       │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                       │
│         │                   │                   │                               │
└─────────┼───────────────────┼───────────────────┼───────────────────────────────┘
          │                   │                   │
          │  Token validation │ Token validation  │ Token validation
          │  (LOCAL: verify   │ (LOCAL: verify    │ (LOCAL: verify
          │   JWT signature   │  JWT signature    │  JWT signature
          │   using cached    │  using cached     │  using cached
          │   IDP public key) │  IDP public key)  │  IDP public key)
          │                   │                   │
          │  OAuth redirect   │  OAuth redirect   │  OAuth redirect
          │  on no token      │  on no token      │  on no token
          │                   │                   │
          └───────────────────┼───────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     LOAD BALANCER (Layer 7, TLS termination)                     │
│                     Route: /oauth/*, /auth/*, /.well-known/*                     │
└──────────────────────────────┬──────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        IDP SERVICE (Stateless, 20 pods)                          │
│                                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐  │
│  │ Auth Controller   │  │ Token Controller  │  │ Session Controller            │  │
│  │                   │  │                   │  │                              │  │
│  │ POST /auth/login  │  │ POST /oauth/token │  │ GET /oauth/authorize         │  │
│  │ POST /auth/logout │  │  - auth_code      │  │  - check session cookie      │  │
│  │ POST /auth/mfa    │  │  - refresh_token  │  │  - redirect or show login    │  │
│  │                   │  │                   │  │                              │  │
│  │ • Validate creds  │  │ • Issue JWT       │  │ • Session lookup in Redis    │  │
│  │ • bcrypt verify   │  │ • Rotate refresh  │  │ • Auth code generation       │  │
│  │ • Create session  │  │ • Validate code   │  │ • SP registration check      │  │
│  │ • Rate limiting   │  │ • Blacklist check │  │                              │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────────┘  │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │ Crypto Module                                                             │    │
│  │ • RSA key pair for JWT signing (private key) and verification (public)   │    │
│  │ • Key rotation: new key pair every 90 days, old key valid for 90 more    │    │
│  │ • JWKS endpoint serves current + previous public keys                    │    │
│  │ • bcrypt (cost 12) for password hashing                                  │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
          ▼                    ▼                    ▼
┌──────────────────┐  ┌────────────────┐  ┌─────────────────────────┐
│ REDIS CLUSTER     │  │ POSTGRESQL      │  │ KAFKA (Audit Log)       │
│ (6 nodes, 60 GB)  │  │ (Primary + 3RR) │  │                         │
│                   │  │                │  │ Topic: auth-events       │
│ Keys:             │  │ Tables:        │  │  - login_success         │
│                   │  │                │  │  - login_failed          │
│ session:{id}      │  │ • users        │  │  - token_refresh         │
│  → user session   │  │   (100M rows)  │  │  - logout                │
│  TTL: 8 hours     │  │ • refresh_     │  │  - token_revoke          │
│                   │  │   tokens       │  │                         │
│ authcode:{code}   │  │   (90M rows)   │  │ Consumers:              │
│  → pending auth   │  │ • service_     │  │  - Audit DB (ClickHouse)│
│  TTL: 60 seconds  │  │   providers    │  │  - Security alerts      │
│                   │  │   (50 rows)    │  │  - Analytics dashboard  │
│ blacklist:{jti}   │  │ • audit_events │  │                         │
│  → revoked token  │  │   (archival)   │  │                         │
│  TTL: ≤ 15 min    │  │                │  │                         │
│                   │  │ Sharded by:    │  │                         │
│ ratelimit:{ip}    │  │ user_id        │  │                         │
│  → login attempts │  │                │  │                         │
│  TTL: 15 min      │  │                │  │                         │
│                   │  │                │  │                         │
│ publickey:cache   │  │                │  │                         │
│  → cached JWKS    │  │                │  │                         │
│  TTL: 24 hours    │  │                │  │                         │
└──────────────────┘  └────────────────┘  └─────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Tech | Scale |
|-----------|---------|------|-------|
| **IDP Service** | Authentication, token issuance, session management, logout | Go/Java (stateless pods) | 20 pods/region |
| **Redis Cluster** | Sessions, auth codes, JTI blacklist, rate limiting, public key cache | Redis (6 nodes) | 60 GB, 100K ops/sec |
| **PostgreSQL** | Users, refresh tokens, SP registrations | PostgreSQL (primary + 3 RR) | 18 GB, sharded by user_id |
| **Kafka** | Audit event stream for security, analytics, compliance | Kafka (3 brokers) | 1 topic, 30M events/day |
| **JWKS Endpoint** | Serve public keys for SP-side JWT verification | Static HTTP (CDN-cached) | Negligible load |
| **Load Balancer** | TLS termination, routing, DDoS protection | AWS ALB / Nginx | Per region |

---

## 6️⃣ Deep Dives

### Deep Dive 1: JWT Design — Why Per-SP Tokens and How SPs Validate Without Calling IDP

**The Problem**: If all SPs share the same token, a compromised SP could impersonate a user on another SP. Token validation must be fast (< 5ms) because every single API call at every SP must validate the token — calling the IDP for each validation would be a massive bottleneck.

**Solution: Per-SP Audience + Asymmetric Signing**

```
JWT Structure (Access Token):

Header:
{
  "alg": "RS256",        // RSA signature
  "kid": "key-2025-Q1",  // key ID (for key rotation)
  "typ": "JWT"
}

Payload:
{
  "sub": "user_abc123",         // user ID
  "iss": "https://idp.company.com",  // issuer
  "aud": "gmail",                // audience — THIS token is ONLY for Gmail
  "jti": "tok_unique_xyz789",   // unique ID (for blacklist)
  "iat": 1710000000,            // issued at
  "exp": 1710000900,            // expires at (iat + 900 = 15 min)
  "scopes": ["openid", "profile", "email"],
  "session_id": "SESS_ABC123"
}

Signature:
  RSA-SHA256(base64(header) + "." + base64(payload), IDP_PRIVATE_KEY)
```

**Why Per-SP Audience?**
- Gmail receives a token with `aud: "gmail"`. If this token is stolen and used against YouTube's API, YouTube checks `aud` and rejects it (aud ≠ "youtube").
- This prevents cross-SP token replay attacks.
- Each SP gets completely separate access and refresh tokens — isolated blast radius.

**How SPs Validate Without Calling IDP (< 5ms)**:
```
SP Token Validation Flow (happens on EVERY API request):

1. Extract JWT from Authorization header
2. Decode header → get "kid" (key ID)
3. Look up public key from LOCAL CACHE (cached JWKS from IDP)
   → If kid not found: re-fetch https://idp.company.com/.well-known/jwks.json (rare: key rotation)
4. Verify RSA signature using cached public key (< 1ms, CPU-only, no network)
5. Check claims:
   a. exp > now? (not expired)
   b. iss == "https://idp.company.com"? (trusted issuer)
   c. aud == "gmail"? (meant for this SP)
6. (Optional) Check JTI blacklist in Redis: EXISTS blacklist:{jti}?
   → Only needed if immediate logout propagation is required
   → Adds ~1ms if checking Redis
7. Token valid → proceed with request

Total: 1-5ms (mostly CPU for RSA verification)
No network call to IDP → SPs are fully independent for token validation
If IDP goes down, SPs continue working until tokens expire
```

**Key Rotation**:
```
Every 90 days, IDP generates a new RSA key pair:
  1. Generate new key pair (kid: "key-2025-Q2")
  2. Add new public key to JWKS endpoint
  3. Start signing new tokens with new private key
  4. Keep old public key in JWKS for 90 more days (tokens signed with old key are still valid)
  5. After 90 days: remove old key from JWKS

This means JWKS always has 2 keys: current + previous.
SPs cache JWKS for 24 hours. On kid miss (new key), they re-fetch once.
```

---

### Deep Dive 2: Session Management — Redis for Central Sessions + Single Logout

**The Problem**: The IDP must track "is this user currently logged in?" to enable SSO (skip login for second SP) and single-logout (revoke everything when user logs out). Sessions must survive IDP pod restarts (stateless pods), support 30M concurrent sessions, and be lookupable in < 5ms.

**Solution: Redis as Session Store**

```
Session data structure in Redis:

Key: session:{session_id}
Value (JSON or Redis Hash):
{
  "user_id": "user_abc123",
  "created_at": 1710000000,
  "last_activity_at": 1710003600,
  "ip_address": "203.0.113.42",
  "user_agent": "Chrome/120 macOS",
  "active_sps": ["gmail", "youtube", "drive"],
  "active_jtis": ["tok_xyz1", "tok_xyz2", "tok_xyz3"]  // for blacklisting on logout
}
TTL: 28800 seconds (8 hours, sliding — refreshed on activity)
```

**Sliding Session Expiry**:
```
On every SSO redirect or token refresh:
  1. GET session:{session_id}
  2. If exists and not expired:
     a. Update last_activity_at
     b. EXPIRE session:{session_id} 28800  (reset 8-hour TTL)
  3. If expired or not found:
     a. User must re-authenticate

This means: a user who is active all day keeps their session alive.
A user who is inactive for 8 hours must re-login.
```

**Single Logout Implementation**:
```java
public class LogoutService {
    
    /**
     * Single logout: one SP triggers → all SPs affected.
     * 
     * Steps:
     * 1. Find the IDP session
     * 2. Revoke all refresh tokens linked to this session
     * 3. Blacklist all active JTIs (so existing access tokens are rejected)
     * 4. Delete the session
     * 5. (Optional) Notify SPs via back-channel logout
     */
    public LogoutResult logout(String sessionId) {
        // 1. Get session from Redis
        IDPSession session = redis.get("session:" + sessionId);
        if (session == null) {
            return LogoutResult.ALREADY_LOGGED_OUT;
        }
        
        // 2. Revoke all refresh tokens for this session (across all SPs)
        // This prevents any SP from refreshing after logout
        int revoked = refreshTokenRepository.revokeBySessionId(sessionId);
        // SQL: UPDATE refresh_tokens SET status='REVOKED' WHERE session_id=? AND status='ACTIVE'
        
        // 3. Blacklist active JTIs
        // Any SP that checks the blacklist will reject these tokens immediately
        for (String jti : session.getActiveJtis()) {
            // TTL = remaining lifetime of the access token (max 15 min)
            redis.setex("blacklist:" + jti, 900, "1");
        }
        
        // 4. Delete IDP session
        redis.del("session:" + sessionId);
        
        // 5. (Optional) Back-channel logout to all active SPs
        for (String spId : session.getActiveSpIds()) {
            ServiceProvider sp = spRegistry.get(spId);
            if (sp.getLogoutUri() != null) {
                // Send logout_token (JWT) to SP's logout endpoint
                String logoutToken = generateLogoutToken(session.getUserId(), sessionId);
                httpClient.postAsync(sp.getLogoutUri(), Map.of("logout_token", logoutToken));
            }
        }
        
        // 6. Emit audit event
        kafkaProducer.send("auth-events", new AuditEvent(
            session.getUserId(), "LOGOUT", sessionId, session.getActiveSpIds()));
        
        return new LogoutResult(true, session.getActiveSpIds(), revoked);
    }
}
```

**Logout Propagation Timing**:
```
Immediate (< 100ms):
  - IDP session deleted → no new SSO tokens can be issued
  - Refresh tokens revoked → no token refresh will succeed

Near-immediate (< 2s, if implemented):
  - Back-channel logout webhooks notify SPs → SPs clear local sessions

Eventually (< 15 min, worst case):
  - Existing access tokens expire naturally
  - If SP checks JTI blacklist: immediate rejection
  - If SP only checks expiry: up to 15 min of stale access

Tradeoff: 
  - Checking blacklist on every request adds ~1ms latency but gives immediate logout
  - Most systems accept the 15-min window for simplicity
  - For high-security (banking), blacklist check is mandatory
```

---

### Deep Dive 3: Refresh Token Security — Rotation, Revocation & Storage

**The Problem**: Refresh tokens are long-lived (7 days) and powerful (can generate new access tokens). If stolen, an attacker can maintain access indefinitely. We need defense-in-depth: secure storage, automatic rotation, and revocation capabilities.

**Refresh Token Rotation (Prevents Replay)**:
```
On every token refresh:
  1. SP sends: { refresh_token: "RT_OLD_123" }
  2. IDP:
     a. Validate RT_OLD_123 (hash lookup in DB)
     b. Issue new access_token
     c. Issue NEW refresh_token "RT_NEW_456" 
     d. REVOKE old refresh_token "RT_OLD_123" (status → REVOKED)
     e. Return new tokens to SP
  
  Why? If an attacker steals RT_OLD_123 and tries to use it AFTER the legitimate
  user has already refreshed → RT_OLD_123 is revoked → attacker gets rejected →
  IDP detects the replay attempt → can revoke ALL tokens for this user (nuclear option)

Replay detection:
  If a revoked refresh token is presented:
  → This means either the user or an attacker used the old token
  → Conservative response: revoke ALL refresh tokens for this session
  → Emit security alert
```

**Refresh Token Storage (Server-Side)**:
```sql
CREATE TABLE refresh_tokens (
    token_id        UUID PRIMARY KEY,
    token_hash      VARCHAR(64) NOT NULL,  -- SHA-256(refresh_token_value)
    user_id         UUID NOT NULL REFERENCES users(user_id),
    client_id       VARCHAR(100) NOT NULL REFERENCES service_providers(client_id),
    session_id      UUID NOT NULL,
    issued_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP NOT NULL,    -- 7 days from issued_at
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    replaced_by     UUID,                  -- points to the new token (for rotation chain tracking)
    
    CONSTRAINT valid_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_refresh_hash ON refresh_tokens (token_hash) WHERE status = 'ACTIVE';
CREATE INDEX idx_refresh_session ON refresh_tokens (session_id);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id, client_id);
```

**Why hash the token?**
- We store `SHA-256(refresh_token)`, not the raw token
- If the database is breached, attackers get hashes — not usable tokens
- Same principle as password hashing (but SHA-256 is sufficient here since tokens are random, not human-chosen)

---

### Deep Dive 4: Rate Limiting & Brute Force Protection

**The Problem**: Login endpoints are prime targets for credential stuffing (testing stolen password databases), brute force attacks, and DDoS. We need to rate-limit without blocking legitimate users.

**Multi-Layer Rate Limiting**:
```
Layer 1: Per-IP Rate Limit (Redis, sliding window)
  Key: ratelimit:ip:{ip_address}
  Limit: 20 login attempts per 15 minutes per IP
  Violation: 429 Too Many Requests + Retry-After header
  Purpose: Block distributed attacks from single IP

Layer 2: Per-Account Rate Limit (Redis)
  Key: ratelimit:account:{email_hash}
  Limit: 5 failed attempts → account locked for 30 minutes
  Violation: 423 Locked + "Account temporarily locked" message
  Purpose: Prevent brute force on specific accounts
  
  After 5 failures:
    User.failedLoginAttempts = 5
    User.status = LOCKED
    User.lockedUntil = now + 30 min
  
  On successful login: reset failedLoginAttempts to 0

Layer 3: Global Rate Limit (API Gateway)
  Total login QPS limit: 20K/sec (2× peak capacity)
  Purpose: Prevent total service overwhelm during DDoS
  Circuit breaker: if error rate > 10%, shed load

Layer 4: CAPTCHA Challenge
  After 3 failed attempts from same IP or account:
  → Require CAPTCHA (Google reCAPTCHA v3 or hCaptcha)
  → Blocks automated attacks while allowing legitimate retries
```

**Redis Implementation**:
```java
public class LoginRateLimiter {
    
    private static final int MAX_IP_ATTEMPTS = 20;
    private static final int MAX_ACCOUNT_ATTEMPTS = 5;
    private static final int WINDOW_SECONDS = 900; // 15 minutes
    
    public RateLimitResult checkRateLimit(String ipAddress, String email) {
        String ipKey = "ratelimit:ip:" + ipAddress;
        String accountKey = "ratelimit:account:" + sha256(email);
        
        // Check IP limit (sliding window counter)
        long ipCount = redis.incr(ipKey);
        if (ipCount == 1) {
            redis.expire(ipKey, WINDOW_SECONDS);
        }
        if (ipCount > MAX_IP_ATTEMPTS) {
            return RateLimitResult.IP_BLOCKED;
        }
        
        // Check account limit
        long accountCount = redis.incr(accountKey);
        if (accountCount == 1) {
            redis.expire(accountKey, WINDOW_SECONDS);
        }
        if (accountCount > MAX_ACCOUNT_ATTEMPTS) {
            // Lock account in PostgreSQL
            userRepository.lockAccount(email, Duration.ofMinutes(30));
            return RateLimitResult.ACCOUNT_LOCKED;
        }
        
        // Check if CAPTCHA should be required
        if (ipCount > 3 || accountCount > 3) {
            return RateLimitResult.CAPTCHA_REQUIRED;
        }
        
        return RateLimitResult.ALLOWED;
    }
    
    // On successful login: clear the account counter
    public void onSuccessfulLogin(String email) {
        redis.del("ratelimit:account:" + sha256(email));
    }
}
```

---

### Deep Dive 5: Token Blacklist — Immediate Logout Enforcement

**The Problem**: JWT access tokens are stateless — once issued, the IDP can't "un-issue" them. If a user logs out, their access token is technically still valid until it expires (up to 15 minutes). For security-sensitive applications, this is unacceptable.

**Solution: JTI Blacklist in Redis**

```
On logout:
  For each active access token's JTI:
    SET blacklist:{jti} 1 EX {remaining_ttl}
    
  Example:
    Token issued at 10:00, expires at 10:15
    User logs out at 10:10 (5 min remaining)
    SET blacklist:tok_xyz 1 EX 300  (5 min TTL)

On token validation at SP:
  After verifying JWT signature and claims:
    EXISTS blacklist:{jti}?
    If YES → reject token (401 Unauthorized)
    If NO → allow request

Memory: At any time, max 15 min worth of JTIs are in the blacklist
  At 10K logouts/hour, 15-min window → max 2,500 entries
  At 24 bytes each → ~60 KB (negligible)
  
  Even at 1M logouts/hour → 250K entries → ~6 MB (still negligible)
```

**Tradeoff: Blacklist Check vs. Pure Stateless**
```
Option A: No blacklist (pure stateless JWT)
  Pro: Zero additional latency, zero Redis dependency for validation
  Con: Tokens valid for up to 15 min after logout
  Acceptable for: Social media feeds, content browsing, low-security apps

Option B: Blacklist check on every request (chosen for enterprise SSO)
  Pro: Immediate logout enforcement (< 1 second)
  Con: +1ms per request (Redis EXISTS), Redis dependency
  Acceptable for: Banking, healthcare, enterprise SSO, any security-sensitive app

Option C: Short token TTL (2 min) + no blacklist
  Pro: Max 2-min stale access, no blacklist complexity
  Con: Very frequent refresh calls (every 2 min), higher IDP load
  Acceptable for: Apps that can tolerate frequent refresh
```

---

### Deep Dive 6: Password Security — bcrypt, Hashing & Credential Storage

**The Problem**: If the user database is breached, passwords must not be recoverable. The hashing algorithm must be deliberately slow to resist brute force.

**bcrypt with Cost Factor 12**:
```
Why bcrypt?
  - Intentionally slow: cost 12 ≈ 200ms per hash (prevents brute force)
  - Built-in salt: each hash includes a random salt (prevents rainbow tables)
  - Adaptive: increase cost factor as hardware gets faster

Storage:
  User.passwordHash = "$2b$12$WowY.pfbMHe7pQ0qM8PK1OxC7SY6r.i0HdGVzJfLhRdA7bW9L8G6m"
                       ↑     ↑  ↑──────────── 22-char salt ──────────↑
                       │     └── cost factor (12 = 2^12 iterations)
                       └── bcrypt version

Login verification:
  bcrypt.verify(submitted_password, stored_hash) → true/false
  Time: ~200ms (this is intentional — an attacker trying 1M passwords needs 55 hours)

Never store:
  ❌ Plaintext passwords
  ❌ MD5/SHA-256 hashes (too fast — billions/sec on GPU)
  ❌ Unsalted hashes (vulnerable to rainbow tables)
```

---

## Summary: Key Design Decisions

| Decision | Chosen | Why | Tradeoff |
|----------|--------|-----|----------|
| **Token format** | JWT (RS256) with per-SP audience | SPs validate locally (no IDP call per request), per-SP isolation prevents cross-SP replay | Token size (~800 bytes vs 32-byte opaque); can't revoke without blacklist |
| **Session store** | Redis (centralized) | < 5ms lookup, 30M sessions fit in 60GB, TTL for auto-expiry, atomic operations | If Redis is down, no SSO (mitigate with Redis Cluster HA) |
| **Refresh token storage** | PostgreSQL (hashed) | Durable, supports revocation queries, audit trail | Slower than Redis for lookup (~5ms vs 1ms), but refresh is infrequent |
| **Logout propagation** | JTI blacklist + back-channel logout | Immediate enforcement (blacklist) + SP notification (webhook) | Blacklist adds 1ms per request; back-channel adds complexity |
| **Password hashing** | bcrypt cost 12 | 200ms per hash blocks brute force; built-in salt | Login is ~200ms slower (acceptable for login, not for every API call) |
| **Key rotation** | 90-day rotation, 2 keys in JWKS | Limits key compromise blast radius; old tokens still validate | SPs must re-fetch JWKS on kid miss |
| **Rate limiting** | Per-IP + Per-account + CAPTCHA | Multi-layer defense against credential stuffing, brute force, DDoS | Legitimate users may hit rate limit during attack on same IP |
| **Auth code flow** | OAuth 2.0 Authorization Code + PKCE | Industry standard, confidential client exchange, prevents code interception | More complex than implicit flow (worth it for security) |

## Interview Talking Points

1. **"Per-SP tokens with different audience claims — prevents cross-SP replay attacks"** — Gmail gets a token with `aud:"gmail"`. Even if stolen, it can't be used against YouTube (aud check fails). Each SP has completely isolated access and refresh tokens. Different blast radius than a shared token model.

2. **"SPs validate JWTs locally using cached public keys — zero IDP dependency at runtime"** — The IDP signs with an RSA private key. SPs download the public key once (JWKS endpoint, cached for 24 hours) and verify every JWT locally in < 1ms. If the IDP goes down, SPs continue working until tokens expire. This is why JWTs are preferred over opaque tokens for SSO.

3. **"Redis for sessions (30M concurrent, < 5ms), PostgreSQL for refresh tokens (durable, revocable)"** — Sessions are ephemeral (8-hour sliding TTL, auto-expire) → Redis. Refresh tokens need durability, revocation queries, and audit trail → PostgreSQL. Different storage for different access patterns.

4. **"Single logout: revoke refresh tokens + blacklist JTIs + delete session + back-channel notify"** — Four-layer logout ensures no stale access. Refresh revocation prevents token refresh. JTI blacklist catches active access tokens immediately. Session deletion prevents new SSO. Back-channel webhooks let SPs clear local sessions. Total propagation: < 2 seconds.

5. **"Refresh token rotation: on every refresh, old token is revoked and new one issued"** — If an attacker steals a refresh token, the legitimate user's next refresh revokes it. If the attacker uses the old token after revocation, the IDP detects the replay and can revoke everything for that session. This is the defense-in-depth strategy from OAuth 2.0 BCP.

6. **"bcrypt cost 12 for passwords: intentionally 200ms per hash"** — An attacker who steals the database needs 200ms × number of guesses to brute-force each password. At 1M guesses, that's 55 hours per user. GPU parallelism doesn't help much (bcrypt is memory-hard). MD5 would be 1M guesses in < 1 second.

7. **"Three-layer rate limiting: per-IP (20/15min), per-account (5 failures → lock), CAPTCHA after 3 failures"** — Credential stuffing attacks send valid-looking requests from distributed IPs. Per-IP catches concentrated attacks. Per-account catches distributed attacks targeting one user. CAPTCHA blocks automation while allowing legitimate retries.

8. **"Key rotation every 90 days with dual-key JWKS"** — New tokens signed with new key, old tokens still validate with old key. JWKS always serves both keys. SPs cache JWKS for 24 hours and re-fetch on kid miss. If a key is compromised, we rotate immediately and the old key is removed from JWKS — all tokens signed with it become unverifiable within 24 hours (SP cache TTL).

---

## Related System Design Problems

| Problem | Overlap | Key Difference |
|---------|---------|----------------|
| **OAuth 2.0 Provider** | Same token issuance, client registration | SSO adds central session + single-logout across SPs |
| **User Authentication Service** | Login, password hashing, rate limiting | SSO adds cross-SP session sharing + token isolation per SP |
| **API Gateway** | JWT validation, rate limiting, routing | SSO focuses on identity issuance, not request routing |
| **Session Management** | Redis sessions, cookie-based auth | SSO sessions are cross-domain (IDP domain), not per-SP |
| **Chat System (Auth)** | JWT for WebSocket auth | SSO handles multi-SP token issuance, not persistent connections |

---

**Created**: April 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (pick 2-3 based on interviewer interest)
