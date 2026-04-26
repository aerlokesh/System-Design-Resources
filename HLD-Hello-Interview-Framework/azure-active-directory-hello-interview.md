# Design Azure Active Directory (Identity & Access Management) — Hello Interview Framework

> **Question**: Design an identity and access management system for enterprise users — supporting single sign-on (SSO), authentication flows (OAuth 2.0/OIDC), directory replication, multi-factor authentication, and conditional access — at the scale of Azure AD serving billions of auth requests daily.
>
> **Asked at**: Microsoft, Google, Amazon, Okta
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
1. **User Authentication**: Authenticate users via username/password, SSO tokens. Support OAuth 2.0, OpenID Connect (OIDC), SAML 2.0 protocols. Issue access tokens and refresh tokens.
2. **Single Sign-On (SSO)**: User authenticates once and gains access to all registered applications without re-entering credentials.
3. **Multi-Factor Authentication (MFA)**: Support SMS, authenticator app (TOTP), push notification, FIDO2 security keys, phone call. Configurable per user/group/application.
4. **Directory Service**: Store and manage users, groups, roles, organizational units. CRUD operations on directory objects. Group membership and nesting.
5. **Application Registration**: Register applications (service principals). Configure permissions, redirect URIs, secrets/certificates. Consent framework.
6. **Conditional Access Policies**: Enforce policies based on user, device, location, risk level. Example: require MFA from unknown locations, block sign-in from risky IPs.
7. **Token Management**: Issue JWT access tokens, ID tokens, refresh tokens. Token validation, revocation, and refresh.

#### Nice to Have (P1)
- Self-service password reset
- Passwordless authentication (FIDO2, Windows Hello)
- B2B guest access (invite external users)
- B2C identity (consumer-facing login)
- Privileged Identity Management (PIM — just-in-time admin access)
- Identity Protection (risk-based sign-in detection)
- Directory sync with on-premises AD (Azure AD Connect)
- Device management and compliance
- Audit logs and sign-in logs

#### Below the Line (Out of Scope)
- Full device management (Intune)
- Azure RBAC for resource management
- License management
- On-premises Active Directory domain services

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Tenants** | 10+ million organizations | Global enterprise directory |
| **Users** | 1+ billion identity objects | Enterprise + consumer |
| **Auth requests/day** | 50+ billion | Every API call validates a token |
| **Token issuance/sec** | 500K tokens/sec peak | Sign-in + token refresh |
| **Auth latency** | < 200ms p95 for token issuance | SSO must feel instant |
| **Token validation** | < 5ms (client-side) | JWT verified locally with public key |
| **Availability** | 99.999% | Auth down = everything down |
| **Consistency** | Strong for auth state, eventual for directory replication | Security-critical |
| **Data residency** | Tenant data in declared region | GDPR, sovereignty |

### Capacity Estimation

```
Scale:
  Tenants: 10M organizations
  Users: 1B identity objects
  Groups: 500M groups
  Applications: 50M registered apps
  
Auth Traffic:
  Token issuance/sec: 200K avg, 500K peak
  Token validation: distributed (client-side, no server call)
  Sign-in events/day: 5B (including SSO)
  MFA challenges/day: 500M
  
Directory Operations:
  Read QPS: 2M (user/group lookups, mostly cached)
  Write QPS: 50K (user updates, group changes)
  
Storage:
  Directory data: 1B users × 5 KB = 5 TB
  Groups + memberships: 10 TB
  Audit logs: 50B sign-ins/day × 500 bytes = 25 TB/day
  
Token Signing Keys:
  RSA-2048 keys, rotated every 6 hours
  JWKS endpoint: one of the most queried endpoints globally
```

---

## 2️⃣ Core Entities

```
┌─────────────┐     ┌──────────────┐     ┌────────────────┐
│  Tenant      │────▶│  User         │────▶│  Group          │
│              │     │               │     │                 │
│ tenantId     │     │ userId        │     │ groupId         │
│ domain       │     │ tenantId      │     │ tenantId        │
│ displayName  │     │ email (UPN)   │     │ displayName     │
│ verifiedDomains[] │ displayName   │     │ type (security/ │
│ defaultDomain│     │ passwordHash  │     │  M365/dynamic)  │
│ mfaPolicy    │     │ mfaSettings   │     │ members[]       │
│ conditionalAccess[]│ devices[]     │     │ owners[]        │
│ tokenConfig  │     │ roles[]       │     │ nestedGroups[]  │
└─────────────┘     │ lastSignIn    │     └────────────────┘
                     │ riskLevel     │
                     │ accountEnabled│
                     └──────────────┘
                            │
┌─────────────┐     ┌──────┴───────┐     ┌────────────────┐
│  Application │     │  Service      │     │  Role           │
│  Registration│────▶│  Principal    │     │  Assignment     │
│              │     │               │     │                 │
│ appId        │     │ spId          │     │ roleId          │
│ displayName  │     │ appId         │     │ principalId     │
│ redirectUris[]│    │ tenantId      │     │ resourceId      │
│ permissions  │     │ credentials[] │     │ scope           │
│ secretExpiry │     │ permissions[] │     └────────────────┘
│ certThumbprint│    │ consentGrants[]│
└─────────────┘     └──────────────┘
                     
┌─────────────────┐  ┌──────────────┐
│  Conditional     │  │  Token        │
│  Access Policy   │  │               │
│                  │  │ tokenId (jti) │
│ policyId         │  │ userId        │
│ conditions:      │  │ tenantId      │
│  users/groups    │  │ appId         │
│  apps            │  │ scopes[]      │
│  locations       │  │ issuedAt      │
│  devicePlatform  │  │ expiresAt     │
│  riskLevel       │  │ refreshTokenId│
│ grantControls:   │  │ mfaCompleted  │
│  requireMFA      │  │ deviceId      │
│  compliantDevice │  └──────────────┘
│  blockAccess     │
└─────────────────┘
```

---

## 3️⃣ API Design

### OAuth 2.0 Authorization Code Flow
```
// Step 1: Authorization Request (browser redirect)
GET https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/authorize
  ?client_id={appId}
  &response_type=code
  &redirect_uri=https://app.contoso.com/callback
  &scope=openid profile email User.Read
  &state={random_csrf_token}
  &code_challenge={PKCE_challenge}
  &code_challenge_method=S256

// Step 2: User authenticates (login page)
// Step 3: Azure AD redirects back with auth code
GET https://app.contoso.com/callback
  ?code={authorization_code}
  &state={random_csrf_token}

// Step 4: Exchange code for tokens
POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code={authorization_code}
&client_id={appId}
&client_secret={secret}
&redirect_uri=https://app.contoso.com/callback
&code_verifier={PKCE_verifier}

Response: 200 OK
{
  "access_token": "eyJ0eX...",    // JWT, 1 hour expiry
  "id_token": "eyJ0eX...",        // OIDC identity token
  "refresh_token": "OAAABa...",   // opaque, 90 day expiry
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile email User.Read"
}
```

### Token Refresh
```
POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token

grant_type=refresh_token
&refresh_token={refresh_token}
&client_id={appId}
&scope=openid profile User.Read

Response: 200 OK
{ "access_token": "new_jwt...", "refresh_token": "new_refresh...", ... }
```

### JWKS Endpoint (Public Keys for Token Validation)
```
GET https://login.microsoftonline.com/common/discovery/v2.0/keys

Response: 200 OK  (heavily cached — billions of requests/day)
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "abc123",
      "use": "sig",
      "n": "0vx7agoebG...",    // RSA modulus
      "e": "AQAB",              // RSA exponent
      "x5c": ["MIIC..."]        // X.509 certificate chain
    }
  ]
}
```

---

## 4️⃣ Data Flow

### Sign-In Flow (with MFA and Conditional Access)

```
User navigates to app.contoso.com
        │
        ▼
App redirects to Azure AD login page
        │
        ▼
Azure AD Login Service:
  1. Show login form → user enters email
  2. Home Realm Discovery:
     ├── contoso.com → Tenant: Contoso Corp (tenantId: abc123)
     ├── Federated? (ADFS, Okta, Ping)
     │     Yes → redirect to federation IdP
     │     No → handle locally
     └── Load tenant authentication config
  3. User enters password
  4. Credential Validation:
     ├── Hash password (bcrypt/scrypt)
     ├── Compare with stored hash in Directory DB
     ├── Check: account enabled? locked? expired?
     └── Check: password correct? → proceed / fail
  5. Conditional Access Evaluation:
     ├── Gather signal: user, device, location, app, risk
     ├── Evaluate all matching policies:
     │   Policy 1: "All users, all cloud apps, from outside corp network → require MFA"
     │   Policy 2: "Admins, any app → require compliant device + MFA"
     ├── Combine results: most restrictive grant wins
     └── Result: require MFA ✓, require compliant device ✗ (not admin)
  6. MFA Challenge:
     ├── User has registered: Authenticator app + SMS
     ├── Primary method: push notification to Authenticator
     ├── User approves on phone → MFA satisfied
     └── Fallback: SMS code if push times out (30s)
  7. Issue Tokens:
     ├── Generate auth code (short-lived, 10 min)
     ├── App exchanges code for tokens
     ├── Sign access_token with RSA private key (kid: current key)
     ├── access_token claims: userId, tenantId, scopes, mfa_auth_time, device
     ├── Store refresh_token in Token DB (encrypted)
     └── Set SSO session cookie (encrypted, domain: login.microsoftonline.com)
  8. Redirect user back to app with tokens
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                       │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐        │
│  │  Browser  │  │  Mobile   │  │  Desktop  │  │  API      │        │
│  │  (SSO)    │  │   App     │  │   App     │  │  Client   │        │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘        │
│        └───────────────┴───────────────┴───────────────┘              │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ HTTPS (OAuth 2.0 / OIDC / SAML)
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     EDGE / GATEWAY LAYER                                │
│  ┌────────────────────┐    ┌──────────────────────────┐                │
│  │  Azure Front Door  │    │  Global Traffic Manager   │                │
│  │  (Global Anycast)  │    │  (Route to nearest region)│                │
│  └────────┬───────────┘    └──────────┬───────────────┘                │
└───────────┴───────────────────────────┴────────────────────────────────┘
                                        │
         ┌──────────────┬───────────────┼───────────────┬────────────────┐
         ▼              ▼               ▼               ▼                ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────┐
│ Login        │ │ Token        │ │ MFA          │ │ Conditional  │ │ Directory│
│ Service      │ │ Service      │ │ Service      │ │ Access       │ │ Service  │
│              │ │              │ │              │ │ Engine       │ │          │
│ Credential   │ │ Issue tokens │ │ SMS, push,  │ │              │ │ User/    │
│ validation   │ │ Refresh      │ │ TOTP, FIDO2 │ │ Evaluate     │ │ Group    │
│ Home realm   │ │ Revoke       │ │ challenge   │ │ policies     │ │ CRUD     │
│ discovery    │ │ JWKS endpoint│ │ verify      │ │ based on     │ │ Search   │
│ SSO session  │ │              │ │              │ │ signals      │ │ Graph API│
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └────┬─────┘
       │                │                │                │              │
       ▼                ▼                ▼                ▼              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          DATA LAYER                                     │
│                                                                         │
│  ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────┐  │
│  │ Directory DB      │  │ Token Store     │  │ Policy Store         │  │
│  │ (Distributed DB)  │  │ (Redis +        │  │ (Cosmos DB)          │  │
│  │                   │  │  Cosmos DB)     │  │                      │  │
│  │ Users, Groups,    │  │ Refresh tokens  │  │ Conditional access   │  │
│  │ Service Principals│  │ SSO sessions    │  │ policies, risk       │  │
│  │ Roles, Devices    │  │ Revocation list │  │ signals              │  │
│  │                   │  │                 │  │                      │  │
│  │ Partition: tenantId│  │                 │  │                      │  │
│  │ Multi-region      │  │                 │  │                      │  │
│  │ replication       │  │                 │  │                      │  │
│  └──────────────────┘  └─────────────────┘  └──────────────────────┘  │
│                                                                         │
│  ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────┐  │
│  │ Key Vault / HSM  │  │ Audit Log Store │  │ Risk Engine          │  │
│  │                   │  │                 │  │                      │  │
│  │ Token signing     │  │ Sign-in logs    │  │ ML-based risk        │  │
│  │ keys (RSA-2048)   │  │ Audit trail     │  │ scoring per sign-in  │  │
│  │ Cert management   │  │ 30-day retention│  │ Anomaly detection    │  │
│  └──────────────────┘  └─────────────────┘  └──────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Token Signing & Validation at Scale

**Problem**: Azure AD issues 500K tokens/sec. Each token is signed with RSA-2048. Resource servers (APIs) must validate tokens without calling Azure AD. How to make this work at global scale?

**Solution: Asymmetric Signing with Cached Public Keys (JWKS)**

```
Token Signing Architecture:

Token Issuance (server-side):
  1. Token Service has access to HSM (Hardware Security Module)
  2. HSM stores RSA-2048 private key (never leaves HSM)
  3. Sign JWT: header.payload → RSA-SHA256 signature
  4. Include kid (key ID) in JWT header for key identification
  
  JWT structure:
  {
    "header": { "alg": "RS256", "kid": "abc123", "typ": "JWT" },
    "payload": {
      "iss": "https://login.microsoftonline.com/{tenantId}/v2.0",
      "sub": "user_object_id",
      "aud": "api://app_id",
      "exp": 1705303600,
      "iat": 1705300000,
      "nonce": "random",
      "name": "Alice Smith",
      "preferred_username": "alice@contoso.com",
      "tid": "tenant_id",
      "oid": "user_object_id",
      "scp": "User.Read profile",
      "amr": ["pwd", "mfa"],     // authentication methods
      "azp": "client_app_id"
    },
    "signature": "base64url(RSA-SHA256(header.payload, private_key))"
  }

Token Validation (client-side — no network call to Azure AD):
  1. API receives request with Authorization: Bearer <jwt>
  2. Decode JWT header → extract kid
  3. Fetch public key from JWKS endpoint (cached locally for 24h):
     GET https://login.microsoftonline.com/common/discovery/v2.0/keys
  4. Verify signature using RSA public key: O(1), ~0.1ms
  5. Validate claims: exp, iss, aud, nbf
  6. Extract user info from claims → authorize request
  
  Zero network call to Azure AD for validation! ←— This is key.

Key Rotation:
  - New signing key generated every 6 hours (proactive)
  - Old key remains valid for 48 hours (overlap period)
  - JWKS endpoint always returns current + previous keys
  - Clients cache JWKS with 24h TTL, refresh on kid miss
  
  Timeline:
  T+0h: Key A active, Key B in JWKS for validation
  T+6h: Key C active, Key A + C in JWKS
  T+12h: Key D active, Key C + D in JWKS (Key A removed)

JWKS Endpoint Scale:
  - Billions of requests/day (every API server in the world fetches this)
  - Served from CDN (Azure Front Door) with aggressive caching
  - Cache-Control: max-age=86400 (24 hours)
  - Actual content changes: every 6 hours (key rotation)
  - CDN invalidation on key rotation → propagates globally in <60s
```

---

### Deep Dive 2: Single Sign-On (SSO) Mechanism

**Problem**: A user signs into Teams and should automatically be signed into Outlook, OneDrive, SharePoint, and 1000+ other apps without entering credentials again. How does SSO work?

**Solution: SSO Session Cookie + Silent Token Acquisition**

```
SSO Flow:

1. First Sign-In (Teams):
   User signs into Teams → redirected to login.microsoftonline.com
   → Enters credentials + MFA → Azure AD issues:
     a. access_token for Teams API
     b. id_token with user info
     c. refresh_token for Teams
     d. SSO cookie: "ESTSAUTH" (encrypted, HttpOnly, Secure)
        Domain: login.microsoftonline.com
        Content: { userId, tenantId, authTime, mfaTime, sessionId }
        Expiry: 24 hours (sliding)

2. Second Sign-In (Outlook — SSO):
   User navigates to Outlook → Outlook redirects to login.microsoftonline.com
   → Browser sends ESTSAUTH cookie automatically
   → Azure AD Login Service:
     a. Decrypt cookie → extract sessionId
     b. Validate session: not expired, not revoked
     c. Check conditional access for Outlook app
     d. If satisfied → issue tokens for Outlook (NO password prompt!)
     e. Redirect back to Outlook with tokens
   → Total time: < 500ms, user sees brief redirect, no login form

3. Silent Token Refresh (in background):
   Single-page app (SPA) with expired access_token:
     a. Create hidden iframe: login.microsoftonline.com/authorize?prompt=none
     b. Browser sends SSO cookie in iframe
     c. Azure AD returns new tokens in iframe redirect
     d. SPA intercepts redirect → extracts new tokens
     e. User never sees anything — completely silent
   
   If SSO session expired:
     → Azure AD returns error: "interaction_required"
     → App shows login prompt

SSO Session Management:
  Redis: session:{sessionId} → {
    userId, tenantId, authTime, mfaCompletedAt,
    deviceId, ipAddress, userAgent, apps[]
  }
  
  Session revocation scenarios:
    - User clicks "Sign out" → delete session
    - Admin disables user → revoke all sessions
    - Password change → revoke all sessions
    - Suspicious activity → revoke all sessions
    
  Propagation: session revocation is IMMEDIATE
    → All token refresh attempts fail
    → Access tokens still valid until expiry (1 hour max)
    → Critical apps: use shorter token lifetime (5 min) + continuous access evaluation
```

---

### Deep Dive 3: Multi-Factor Authentication at Scale

**Problem**: 500M MFA challenges/day across multiple methods (push, SMS, TOTP, FIDO2). How to deliver MFA reliably with low latency?

**Solution: Multi-Channel MFA with Adaptive Selection**

```
MFA Architecture:

User completes password → MFA required
        │
        ▼
MFA Service:
  1. Lookup user's registered MFA methods:
     HGET mfa:user123 → ["authenticator_push", "sms:+1555...", "totp"]
  
  2. Select method (priority order):
     a. Authenticator push (fastest, most secure)
     b. TOTP code (no network dependency)
     c. SMS (fallback, less secure)
     d. Phone call (last resort)
  
  3. Challenge based on method:

AUTHENTICATOR PUSH:
  MFA Service → Push Notification Service → APNS/FCM
  → User's phone shows: "Approve sign-in to Outlook? Number: 42"
  → User taps "Approve" + confirms number matching
  → Authenticator app → Azure AD MFA endpoint: 
    POST /mfa/verify { sessionId, method: "push", approved: true }
  → MFA Service: mark MFA completed for this session
  
  Timeout: 60 seconds → fallback to TOTP/SMS
  
  Number Matching (anti-fatigue):
    Login page shows: "Enter this number in your Authenticator: 42"
    User must type "42" in the app → prevents accidental approval

TOTP (Time-Based One-Time Password):
  User opens Authenticator → sees 6-digit code (refreshes every 30s)
  User enters code on login page
  
  Server verification:
    shared_secret = stored for user (encrypted in DB)
    expected_code = HMAC-SHA1(shared_secret, floor(timestamp / 30))
    Check: user_code matches expected_code (with ±1 window for clock skew)

SMS (fallback):
  MFA Service → SMS Gateway (Twilio / Azure Communication Services)
  → SMS to user's phone: "Your verification code is 847291"
  → User enters code
  → Server checks: code matches, not expired (5 min TTL), not reused
  
  Rate limiting: max 5 SMS per user per hour (prevent abuse)

FIDO2 Security Key:
  Browser → WebAuthn API → Security key (USB/NFC/Bluetooth)
  → Challenge-response with public key cryptography
  → Strongest method, phishing-resistant
  → No shared secret on server, no OTP to intercept

Adaptive MFA:
  Low risk sign-in (known device, trusted location):
    → Skip MFA, use device trust
  Medium risk (new location, known device):
    → Require push notification (low friction)
  High risk (new device, anomalous behavior):
    → Require FIDO2 or number matching push
  Critical (password change, admin action):
    → Always require strongest available MFA
```

---

### Deep Dive 4: Directory Replication & Global Availability

**Problem**: Directory data (users, groups) must be available in every Azure region with <50ms read latency. But writes must be consistent (you shouldn't be able to log in after being disabled). How to replicate globally?

**Solution: Multi-Region Write with Conflict Resolution + Urgent Replication for Security**

```
Replication Architecture:

Write Regions: 5 primary regions (US, EU, Asia, etc.)
Read Replicas: 20+ regions (all Azure regions)

Normal Directory Operations (user update, group change):
  Write → Primary region for tenant (determined by data residency config)
  Replicate to read replicas: within 30 seconds (eventual consistency)
  
  Conflict resolution: Last-Writer-Wins (LWW) on modifiedAt timestamp
  Rare conflicts: only for concurrent writes to same object in different regions

Security-Critical Operations (URGENT replication):
  These MUST propagate immediately:
    - User account disabled
    - Password changed / reset
    - MFA re-registration
    - Session revocation
    - Admin role changed
  
  Urgent replication path:
    Write to primary → Publish to "urgent-security" Kafka topic
    → All regions consume within 5 seconds
    → Update local replica immediately
    → Until replicated: Token refresh checks primary (sync call)

Read Path:
  GET /users/{userId}
    → Read from local region replica (< 10ms)
    → Cache hot users in Redis (TTL 5 min)
    → 99.9% of reads served from local replica

Write Path:
  PATCH /users/{userId}
    → Route to primary write region for this tenant
    → May add 50-100ms latency for cross-region write
    → Return success after primary write (async replication to replicas)

Partition Strategy:
  PartitionKey: tenantId
  
  Why tenantId?
    - All queries within a tenant (user lookup, group membership)
    - Tenant size bounded (largest: ~1M users)
    - Data residency: entire tenant pinned to a region
  
  Within partition:
    Secondary indexes on: email (UPN), objectId, displayName
    Composite index: (tenantId, groupId, memberId) for membership queries
```

---

### Deep Dive 5: Conditional Access Policy Engine

**Problem**: Enterprises configure complex policies like "require MFA + compliant device for admins accessing production apps from outside the corporate network." These policies must be evaluated in real-time during every sign-in (<50ms). How?

**Solution: Signal Collection + Rule Engine with Pre-Compiled Policy Tree**

```
Conditional Access Evaluation:

Signal Collection (gathered at sign-in time):
  {
    "user": { "id": "user_123", "groups": ["admins", "engineering"], "risk": "low" },
    "device": { "id": "dev_456", "platform": "Windows", "compliant": true, "managed": true },
    "location": { "ip": "203.0.113.42", "country": "US", "namedLocation": "corporate_vpn" },
    "application": { "id": "app_789", "name": "Azure Portal" },
    "clientApp": "browser",
    "signInRisk": "low",           // ML risk score
    "userRisk": "medium"           // historical risk
  }

Policy Evaluation Engine:
  
  1. Load tenant policies (cached in memory, ~100 policies per tenant):
     [
       { "id": "p1", "conditions": { "users": "All", "apps": "All", "locations": "!corporate" },
         "grant": "requireMFA" },
       { "id": "p2", "conditions": { "users": "group:admins", "apps": "Azure Portal" },
         "grant": "requireMFA AND requireCompliantDevice" },
       { "id": "p3", "conditions": { "signInRisk": "high" },
         "grant": "blockAccess" }
     ]
  
  2. For each policy, evaluate conditions against signals:
     p1: user=All ✓, apps=All ✓, location=corporate ✗ → DOES NOT APPLY
     p2: user=admin group ✓, app=Azure Portal ✓ → APPLIES
     p3: signInRisk=low, not high → DOES NOT APPLY
  
  3. Aggregate grant controls from all applicable policies:
     p2 requires: MFA ✓ AND compliantDevice ✓
  
  4. Check satisfaction:
     MFA completed? (from session) → ✓
     Device compliant? (from device signal) → ✓
     All satisfied → ALLOW sign-in
  
  If not satisfied:
     MFA needed but not done → show MFA challenge
     Compliant device needed but not compliant → show "Access denied: non-compliant device"
     Block access → show "Access blocked by organization policy"

Performance Optimization:
  - Pre-compile policies into decision tree (trie) at policy update time
  - Evaluation: walk the tree with signals → O(depth) not O(num_policies)
  - Cache compiled policy tree per tenant in Redis (invalidate on policy change)
  - Evaluation time: < 5ms for 100 policies

Continuous Access Evaluation (CAE):
  For long-lived sessions (Outlook, Teams):
    - Don't just check at sign-in — continuously evaluate
    - Events that trigger re-evaluation:
      • User disabled → token immediately rejected
      • Password changed → token invalidated
      • Location changed (VPN disconnect) → re-evaluate policies
      • Device compliance status changed
    - Implementation: resource servers subscribe to "critical events" feed
      from Azure AD and validate tokens against latest signals
```
