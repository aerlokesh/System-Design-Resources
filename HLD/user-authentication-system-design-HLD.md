# User Login and Authentication System - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [Authentication Flow](#authentication-flow)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a scalable, secure user authentication and authorization system that supports:
- User registration with email/phone verification
- Secure login with password and passwordless options
- Multi-factor authentication (MFA)
- Social login (OAuth 2.0) - Google, Facebook, Apple
- Session management with JWT tokens
- Password reset and account recovery
- Role-based access control (RBAC)
- Rate limiting and brute-force protection
- Single Sign-On (SSO)
- Audit logging for security compliance
- Account lockout policies
- Device fingerprinting and trusted devices

### Scale Requirements
- **500 million registered users**
- **100 million daily active users (DAU)**
- **50 million login attempts per day**
- **Peak load: 10,000 logins per second**
- **Session validation: 100,000 requests per second**
- **99.99% availability** (52 minutes downtime/year)
- **Login latency: < 200ms (p95)**
- **Token validation: < 10ms**
- **Password reset: < 5 seconds**
- **Support 100+ countries with data residency compliance**

---

## Functional Requirements

### Must Have (P0)

#### 1. **User Registration**
- Register with email + password
- Register with phone + OTP
- Email/phone verification required
- Username uniqueness validation
- Password strength requirements
- CAPTCHA for bot prevention
- Account activation via link/code
- Terms of service acceptance tracking

#### 2. **User Login**
- Login with email/username + password
- Login with phone + OTP
- Social login (OAuth 2.0):
  - Google
  - Facebook
  - Apple Sign-In
  - GitHub (for developer platforms)
- Passwordless login (magic link)
- Biometric login (mobile apps)
- Remember me functionality
- Login from multiple devices

#### 3. **Multi-Factor Authentication (MFA)**
- SMS OTP (6-digit code)
- Email OTP
- Authenticator app (TOTP - Google Authenticator, Authy)
- Backup codes (10 single-use codes)
- Biometric as second factor
- Hardware security keys (FIDO2/WebAuthn)
- MFA enforcement for admin users
- Trusted device management

#### 4. **Session Management**
- JWT access tokens (short-lived: 15 minutes)
- Refresh tokens (long-lived: 30 days)
- Token revocation capability
- Logout from single device
- Logout from all devices
- Concurrent session limits (max 5 devices)
- Session timeout on inactivity (30 minutes)
- Device tracking (last login, location, IP)

#### 5. **Password Management**
- Secure password hashing (bcrypt, Argon2)
- Password reset via email
- Password reset via SMS
- Security questions (deprecated - not recommended)
- Password change with current password verification
- Password history (prevent reuse of last 5)
- Force password change on next login
- Compromised password detection (HaveIBeenPwned API)

#### 6. **Account Security**
- Rate limiting (5 failed attempts → 15 min lockout)
- Progressive delays on failed attempts
- Account lockout after 10 failed attempts
- CAPTCHA after 3 failed attempts
- Suspicious activity detection
- Login notifications (email/SMS)
- New device notifications
- IP address whitelisting/blacklisting
- Geolocation-based blocking

#### 7. **Account Recovery**
- Forgot password flow
- Account locked - contact support
- Email not received - resend
- Phone number recovery
- Identity verification for sensitive actions
- Account deletion/deactivation

### Nice to Have (P1)
- Single Sign-On (SSO) with SAML 2.0
- Federated identity (OpenID Connect)
- Risk-based authentication (low/medium/high)
- Adaptive authentication (challenge only when suspicious)
- Passwordless authentication (WebAuthn/FIDO2)
- Login with QR code (desktop ↔ mobile)
- Login analytics dashboard
- User behavior analytics (UBA)
- Anomaly detection with ML
- Privacy-preserving authentication (zero-knowledge proofs)

---

## Non-Functional Requirements

### Performance
- **Login latency**: < 200ms (p95), < 500ms (p99)
- **Token validation**: < 10ms
- **Password hashing**: < 500ms (balance security & UX)
- **OTP delivery**: < 5 seconds
- **Session creation**: < 100ms
- **API throughput**: 100K requests/second

### Scalability
- Support 500M users
- Handle 10K logins/second peak
- 100K token validations/second
- Scale to 1B users in 3 years
- Global deployment (multi-region)

### Availability
- **99.99% uptime** (4 nines)
- Multi-region active-active deployment
- Auto-failover (< 30 seconds)
- Zero-downtime deployments
- Graceful degradation (passwordless fallback if SMS down)

### Security
- **Encryption**: TLS 1.3 for all connections
- **Data at rest**: AES-256 encryption
- **Password hashing**: Argon2id (memory-hard function)
- **Token signing**: RS256 (RSA with SHA-256)
- **PII protection**: Encrypt emails, phone numbers
- **Audit logging**: All authentication events
- **Compliance**: GDPR, CCPA, SOC 2, ISO 27001
- **Vulnerability scanning**: Regular penetration testing
- **Secret management**: HashiCorp Vault, AWS Secrets Manager

### Consistency
- **Strong consistency** for authentication state
- **Eventual consistency** for audit logs
- **Read-after-write consistency** for user profile

### Latency
- **North America**: < 50ms
- **Europe**: < 100ms
- **Asia**: < 150ms
- **Cross-region**: < 200ms

---

## Capacity Estimation

### Traffic Estimates
```
Registered Users: 500M
Daily Active Users (DAU): 100M (20% of total)
Monthly Active Users (MAU): 250M (50% of total)

Login Events:
- Users login 1-2 times per day
- Total logins/day: 100M × 1.5 = 150M
- Logins/second (average): 150M / 86,400 ≈ 1,736 QPS
- Logins/second (peak - 5x): 8,680 QPS

Token Validations:
- Every API request validates token
- Average user makes 50 API calls/session
- Total validations/day: 100M × 50 = 5B
- Validations/second: 5B / 86,400 ≈ 57,870 QPS
- Peak (3x): 173,610 QPS

Registration:
- New users/day: 500K (0.1% growth)
- Registrations/second: 500K / 86,400 ≈ 6 QPS
- Peak (10x): 60 QPS

Password Resets:
- 1% of users reset password monthly
- Resets/month: 500M × 1% = 5M
- Resets/day: 5M / 30 = 166,666
- Resets/second: 166,666 / 86,400 ≈ 2 QPS

MFA Events:
- 30% of users have MFA enabled
- MFA challenges: 150M × 30% = 45M/day
- MFA/second: 45M / 86,400 ≈ 521 QPS

OAuth Logins:
- 40% use social login
- OAuth logins/day: 150M × 40% = 60M
- OAuth/second: 60M / 86,400 ≈ 694 QPS
```

### Storage Estimates

**User Accounts**:
```
Per user:
{
  user_id: 16 bytes (UUID)
  email: 50 bytes (encrypted)
  phone: 20 bytes (encrypted)
  password_hash: 60 bytes (bcrypt)
  username: 30 bytes
  first_name: 30 bytes
  last_name: 30 bytes
  created_at: 8 bytes
  updated_at: 8 bytes
  status: 10 bytes
  mfa_enabled: 1 byte
  mfa_secret: 32 bytes
  metadata: 200 bytes (JSON)
}
Total per user: ~500 bytes

Total users: 500M × 500 bytes = 250 GB
With indexes (3x): 750 GB
With replication (3x): 2.25 TB
```

**Sessions**:
```
Active sessions: 100M users × 2 devices = 200M sessions
Per session:
{
  session_id: 16 bytes
  user_id: 16 bytes
  access_token: 500 bytes (JWT)
  refresh_token: 500 bytes
  device_info: 200 bytes
  ip_address: 16 bytes
  created_at: 8 bytes
  expires_at: 8 bytes
  last_activity: 8 bytes
}
Total per session: ~1,300 bytes

Total: 200M × 1.3 KB = 260 GB
Stored in Redis (in-memory): 260 GB
With replication (3x): 780 GB
```

**Audit Logs**:
```
Events per day:
- Logins: 150M
- Token validations: 5B (not all logged)
- Failed attempts: 10M
- MFA events: 45M
- Password resets: 166K
- Account changes: 5M
Total logged events: ~220M/day

Per log entry:
{
  event_id: 16 bytes
  user_id: 16 bytes
  event_type: 20 bytes
  timestamp: 8 bytes
  ip_address: 16 bytes
  user_agent: 200 bytes
  result: 10 bytes
  metadata: 300 bytes
}
Total per log: ~600 bytes

Daily logs: 220M × 600 bytes = 132 GB/day
Monthly: 132 GB × 30 = 3.96 TB/month
With 1-year retention: 47.5 TB
With replication: 142.5 TB
```

**OAuth Tokens**:
```
OAuth users: 500M × 40% = 200M
Per OAuth profile:
{
  user_id: 16 bytes
  provider: 10 bytes (google, facebook)
  provider_user_id: 50 bytes
  access_token: 500 bytes
  refresh_token: 500 bytes
  token_expiry: 8 bytes
  scope: 100 bytes
}
Total per profile: ~1,200 bytes

Total: 200M × 1.2 KB = 240 GB
With replication: 720 GB
```

**Total Storage**:
```
User accounts: 2.25 TB
Sessions (Redis): 0.78 TB
Audit logs (1 year): 142.5 TB
OAuth tokens: 0.72 TB
Backup codes, MFA: 0.5 TB
─────────────────────────
Total: ~147 TB
```

### Bandwidth Estimates
```
Login request size:
- Payload: 500 bytes (email + password)
- Response: 2 KB (JWT + user info)
- Total per login: 2.5 KB

Daily bandwidth (logins):
- Incoming: 150M × 500 bytes = 75 GB
- Outgoing: 150M × 2 KB = 300 GB
- Total: 375 GB/day ≈ 4.3 MB/second

Token validation:
- Request: 600 bytes (JWT)
- Response: 200 bytes (valid/invalid)
- Per validation: 800 bytes

Daily bandwidth (validation):
- Total: 5B × 800 bytes = 4 TB/day ≈ 46 MB/second

Peak bandwidth:
- Logins: 4.3 MB/s × 5 = 21.5 MB/s
- Validations: 46 MB/s × 3 = 138 MB/s
- Total peak: ~160 MB/s
```

### Server Estimates
```
API Servers (Login/Registration):
  - Handle 8,680 logins/second (peak)
  - Each server: 500 logins/second
  - Servers needed: 8,680 / 500 = 18 servers
  - With redundancy (3x): 54 servers

Token Validation Servers:
  - Handle 173,610 validations/second (peak)
  - Each server: 10,000 validations/second
  - Servers needed: 173,610 / 10,000 = 18 servers
  - With redundancy (2x): 36 servers

OAuth Integration Servers:
  - Handle 694 OAuth logins/second
  - Each server: 200 OAuth/second
  - Servers needed: 694 / 200 = 4 servers
  - With redundancy (2x): 8 servers

MFA Service:
  - Handle 521 MFA challenges/second
  - Each server: 200 MFA/second
  - Servers needed: 521 / 200 = 3 servers
  - With redundancy (2x): 6 servers

Total servers: 54 + 36 + 8 + 6 = 104 servers

Cost estimation (AWS):
- API servers: 54 × $150/month = $8,100/month
- Validation servers: 36 × $100/month = $3,600/month
- OAuth servers: 8 × $100/month = $800/month
- MFA servers: 6 × $80/month = $480/month
- Database (RDS + Aurora): $10,000/month
- Redis (ElastiCache): $5,000/month
- S3 (logs, backups): $2,000/month
- SMS/Email services: $15,000/month
- Total: ~$45,000/month
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                      CLIENTS                               │
│  Web Apps | Mobile Apps | Desktop Apps | Third-party APIs │
└────────────────────────┬───────────────────────────────────┘
                         │ (HTTPS - TLS 1.3)
                         ↓
┌────────────────────────────────────────────────────────────┐
│                   CDN / LOAD BALANCER                      │
│  CloudFront / AWS ALB - SSL termination, DDoS protection  │
│  WAF (Web Application Firewall) - SQL injection, XSS      │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ↓
┌────────────────────────────────────────────────────────────┐
│                    API GATEWAY                             │
│  - Rate limiting (per IP: 10 req/sec)                      │
│  - Request validation                                      │
│  - Routing                                                 │
│  - API key management                                      │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓               ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Auth Service │ │   User       │ │    MFA       │
│  (Login/     │ │  Service     │ │   Service    │
│  Register)   │ │  (Profile)   │ │  (OTP/TOTP)  │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       └────────────────┼────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────┐
│              CACHE LAYER (Redis Cluster)                   │
│  - Session tokens (TTL: 30 days)                           │
│  - Rate limit counters (sliding window)                    │
│  - Failed login attempts (TTL: 15 min)                     │
│  - OTP codes (TTL: 5 min)                                  │
│  - User profile cache (TTL: 1 hour)                        │
│  - Blacklisted tokens (revoked)                            │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓               ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│    User      │ │   Session    │ │    Audit     │
│      DB      │ │      DB      │ │     Log      │
│ (PostgreSQL) │ │  (Redis +    │ │  (Cassandra) │
│              │ │  PostgreSQL) │ │              │
│ - Users      │ │ - Active     │ │ - Login logs │
│ - Passwords  │ │   sessions   │ │ - Failed     │
│ - MFA config │ │ - Devices    │ │   attempts   │
│ - OAuth      │ │ - Refresh    │ │ - Security   │
│   tokens     │ │   tokens     │ │   events     │
└──────────────┘ └──────────────┘ └──────────────┘

┌────────────────────────────────────────────────────────────┐
│              TOKEN SERVICE (JWT)                           │
│  - Generate access tokens (15 min TTL)                     │
│  - Generate refresh tokens (30 day TTL)                    │
│  - Token validation (signature verification)               │
│  - Token revocation                                        │
│  - Public key distribution                                 │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              PASSWORD SERVICE                              │
│  - Password hashing (Argon2id)                             │
│  - Password strength validation                            │
│  - Compromised password check (HaveIBeenPwned)            │
│  - Password reset token generation                         │
│  - Password history tracking                               │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              OAUTH SERVICE                                 │
│  - OAuth 2.0 flow (Authorization Code)                     │
│  - Provider integration (Google, Facebook, Apple)          │
│  - Token exchange                                          │
│  - User info fetching                                      │
│  - Account linking                                         │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│           NOTIFICATION SERVICE                             │
│  - Send verification emails                                │
│  - Send OTP via SMS                                        │
│  - Send login alerts                                       │
│  - Password reset emails                                   │
│  - Security notifications                                  │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│           FRAUD DETECTION ENGINE                           │
│  - IP reputation checking                                  │
│  - Device fingerprinting                                   │
│  - Behavioral analysis (login patterns)                    │
│  - Anomaly detection (ML model)                            │
│  - Risk scoring (low/medium/high)                          │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              SECRET MANAGEMENT                             │
│  HashiCorp Vault / AWS Secrets Manager                     │
│  - JWT signing keys (rotation every 90 days)               │
│  - Database credentials                                    │
│  - API keys (OAuth, SMS)                                   │
│  - Encryption keys                                         │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              MONITORING & OBSERVABILITY                    │
│  Prometheus | Grafana | ELK | Jaeger | PagerDuty         │
│  - Failed login rate                                       │
│  - Token validation latency                                │
│  - Suspicious activity alerts                              │
│  - System health metrics                                   │
└────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **JWT for Stateless Authentication**
   - Access tokens: Short-lived (15 min), self-contained
   - Refresh tokens: Long-lived (30 days), stored in DB
   - No server-side session storage for access tokens
   - Horizontal scaling without shared state
   - Token revocation via blacklist (Redis)

2. **Redis for Session Management**
   - In-memory speed (< 1ms lookups)
   - Session data cached
   - Rate limiting with atomic operations
   - Failed login attempt tracking
   - OTP storage with automatic expiration

3. **PostgreSQL for User Data**
   - ACID transactions required
   - User profiles need strong consistency
   - Complex queries (user search, admin queries)
   - Relatively small hot dataset
   - Read replicas for scaling reads

4. **Cassandra for Audit Logs**
   - Write-heavy workload (all auth events)
   - Time-series data
   - Long retention (1 year)
   - Fast writes, eventual consistency acceptable
   - Compliance requirements

5. **Multi-Factor Authentication**
   - TOTP (Time-based OTP) - most secure
   - SMS OTP - fallback option
   - Email OTP - last resort
   - Backup codes - account recovery
   - Hardware keys (FIDO2) - enterprise

6. **Rate Limiting Strategy**
   - Per IP: 10 login attempts/minute
   - Per user: 5 failed attempts → 15 min lockout
   - Per endpoint: Global rate limits
   - Sliding window algorithm (Redis)
   - Progressive delays on failures

---

## Core Components

### 1. Authentication Service

**Purpose**: Handle user login, registration, and token management

**Responsibilities**:
- Validate credentials
- Generate JWT tokens
- Refresh token rotation
- Login rate limiting
- Failed attempt tracking
- Account lockout management

**API Endpoints**:

**Register User**:
```http
POST /api/v1/auth/register

Request:
{
  "email": "user@example.com",
  "password": "SecureP@ssw0rd!",
  "username": "john_doe",
  "first_name": "John",
  "last_name": "Doe",
  "phone": "+1234567890",
  "terms_accepted": true
}

Response (201 Created):
{
  "user_id": "usr_abc123",
  "email": "user@example.com",
  "status": "pending_verification",
  "verification_sent": true,
  "message": "Check your email to verify your account"
}
```

**Login**:
```http
POST /api/v1/auth/login

Request:
{
  "email": "user@example.com",
  "password": "SecureP@ssw0rd!",
  "remember_me": true,
  "device_info": {
    "device_id": "device_xyz",
    "platform": "ios",
    "app_version": "1.2.0"
  }
}

Response (200 OK):
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "rt_def456...",
  "token_type": "Bearer",
  "expires_in": 900,  // 15 minutes
  "user": {
    "user_id": "usr_abc123",
    "email": "user@example.com",
    "username": "john_doe",
    "first_name": "John",
    "last_name": "Doe",
    "mfa_enabled": true,
    "mfa_required": true  // Must complete MFA challenge
  }
}

// If MFA required:
Response (202 Accepted):
{
  "challenge_id": "chal_xyz123",
  "mfa_methods": ["totp", "sms"],
  "message": "MFA verification required"
}
```

**Refresh Token**:
```http
POST /api/v1/auth/refresh

Request:
{
  "refresh_token": "rt_def456..."
}

Response (200 OK):
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "rt_ghi789...",  // New refresh token (rotation)
  "token_type": "Bearer",
  "expires_in": 900
}
```

**Logout**:
```http
POST /api/v1/auth/logout

Request:
{
  "refresh_token": "rt_def456...",
  "logout_all_devices": false
}

Response (200 OK):
{
  "message": "Logged out successfully"
}

// Adds refresh token to blacklist
// If logout_all_devices=true: Invalidate all user's refresh tokens
```

**Login Flow**:
```
1. Client sends credentials
2. Validate request format
3. Check rate limit (Redis):
   - IP: 10 attempts/minute
   - User: 5 failed attempts → lockout
4. Fetch user from database (by email)
5. Verify password hash (Argon2):
   - Compare: verify(stored_hash, input_password)
   - Timing: ~300ms (memory-hard function)
6. If invalid:
   - Increment failed attempts (Redis)
   - If >= 3 attempts: Require CAPTCHA
   - If >= 5 attempts: Lock account for 15 min
   - Return 401 Unauthorized
7. If valid:
   - Reset failed attempt counter
   - Check MFA status:
     - If MFA enabled: Create challenge, return 202
     - If MFA disabled: Continue
   - Generate JWT access token (15 min)
   - Generate refresh token (30 days)
   - Store session in Redis
   - Store refresh token in PostgreSQL
   - Log successful login (Cassandra)
   - Send login notification (async)
   - Return tokens

Total latency: ~350ms
```

**JWT Token Structure**:
```json
Header:
{
  "alg": "RS256",  // RSA with SHA-256
  "typ": "JWT",
  "kid": "key_id_20240101"  // Key rotation
}

Payload:
{
  "sub": "usr_abc123",  // Subject (user ID)
  "email": "user@example.com",
  "username": "john_doe",
  "roles": ["user", "premium"],
  "permissions": ["read:profile", "write:profile"],
  "iat": 1704672000,  // Issued at
  "exp": 1704672900,  // Expires at (15 min)
  "iss": "auth.example.com",  // Issuer
  "aud": "api.example.com",  // Audience
  "jti": "jwt_xyz123",  // JWT ID (unique)
  "device_id": "device_xyz",
  "session_id": "sess_abc"
}

Signature:
RSASHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  private_key
)
```

**Token Validation**:
```
Fast path (99% of requests):
1. Parse JWT (no external calls)
2. Verify signature with public key (cached)
3. Check expiration (exp claim)
4. Check issuer and audience
5. Extract user_id and permissions
6. Allow request

Total time: < 5ms

Slow path (token revoked):
1. Check Redis blacklist: EXISTS blacklist:jwt_xyz123
2. If exists: Reject (token revoked)
3. Else: Allow

Total time: < 10ms (Redis lookup)
```

**Refresh Token Rotation**:
```
Security: Prevent token replay attacks

Flow:
1. Client uses refresh token to get new access token
2. Server validates refresh token (database lookup)
3. Generate new access token
4. Generate new refresh token (rotation)
5. Store new refresh token in database
6. Mark old refresh token as used (can't reuse)
7. Return new tokens

Benefits:
- Limits window of stolen token usefulness
- Detects token theft (if old token reused)
- Automatic revocation on suspicious activity
```

### 2. User Service

**Purpose**: Manage user profiles and preferences

**API Endpoints**:

**Get Profile**:
```http
GET /api/v1/users/me
Authorization: Bearer <access_token>

Response (200 OK):
{
  "user_id": "usr_abc123",
  "email": "user@example.com",
  "username": "john_doe",
  "first_name": "John",
  "last_name": "Doe",
  "phone": "+1234567890",
  "phone_verified": true,
  "email_verified": true,
  "mfa_enabled": true,
  "created_at": "2024-01-01T00:00:00Z",
  "last_login": "2024-01-08T10:00:00Z",
  "account_status": "active"
}
```

**Update Profile**:
```http
PATCH /api/v1/users/me

Request:
{
  "first_name": "Jonathan",
  "phone": "+1234567891"
}

Response (200 OK):
{
  "message": "Profile updated successfully",
  "changes": ["first_name", "phone"],
  "phone_verification_required": true
}
```

**Delete Account**:
```http
DELETE /api/v1/users/me

Request:
{
  "password": "SecureP@ssw0rd!",
  "confirmation": "DELETE"
}

Response (200 OK):
{
  "message": "Account scheduled for deletion in 30 days",
  "deletion_date": "2024-02-08T00:00:00Z"
}
```

### 3. Multi-Factor Authentication (MFA) Service

**Purpose**: Provide second-factor authentication

**TOTP (Authenticator App)**:
```
Setup Flow:
1. User enables MFA in settings
2. Generate secret key (32 bytes, base32 encoded)
3. Create QR code with otpauth:// URI:
   otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example
4. User scans QR with authenticator app
5. User enters 6-digit code to confirm
6. Server validates code
7. Generate 10 backup codes
8. Store secret encrypted in database

Validation:
1. User enters 6-digit code
2. Server fetches user's TOTP secret
3. Calculate valid codes for current time window:
   - Current 30-second window
   - Previous window (clock skew tolerance)
   - Next window
4. If code matches any window: Success
5. Prevent replay: Store used codes (Redis, TTL 90s)

Algorithm:
TOTP = HOTP(K, T)
where:
  K = secret key
  T = floor(current_time / 30)  // 30-second window
  HOTP = HMAC-SHA1(K, T) mod 10^6  // 6-digit code
```

**SMS OTP**:
```
Flow:
1. User requests SMS OTP
2. Generate 6-digit random code
3. Store in Redis with 5-minute TTL:
   SET otp:user_123:sms "123456" EX 300
4. Send SMS via Twilio/SNS
5. User enters code
6. Server validates:
   - Fetch from Redis: GET otp:user_123:sms
   - Compare codes
   - If match: Delete from Redis, allow login
   - If mismatch: Increment attempt counter
   - After 3 failed attempts: Block for 15 minutes

Rate limiting:
- Max 3 OTP requests per 15 minutes
- Prevents SMS flooding abuse
```

**Backup Codes**:
```
Generation:
- Create 10 random 8-character codes
- Hash each code (bcrypt)
- Store hashed codes in database
- Display codes once to user (download/print)

Usage:
1. User enters backup code
2. Server fetches all backup codes for user
3. Compare with each hashed code
4. If match:
   - Mark code as used (soft delete)
   - Allow login
   - Warn user: X codes remaining
5. If all codes used:
   - Prompt to generate new set
```

### 4. Password Service

**Password Hashing (Argon2id)**:
```
Algorithm: Argon2id (hybrid of Argon2i and Argon2d)
- Memory-hard: Resistant to GPU/ASIC attacks
- Time-hard: Configurable iterations
- Parallelism: Multi-threading support

Parameters:
- Memory: 64 MB
- Iterations: 3
- Parallelism: 4 threads
- Salt: 16 bytes (random)
- Output: 32 bytes hash

Hash format:
$argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>

Time: ~300-500ms (intentionally slow for security)

Why Argon2 over bcrypt:
- More resistant to GPU cracking
- Configurable memory usage
- Winner of Password Hashing Competition (2015)
- Modern algorithm (bcrypt from 1999)
```

**Password Strength Validation**:
```
Requirements:
- Minimum 8 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 number
- At least 1 special character
- Not in common password list (top 10K)
- Not compromised (HaveIBeenPwned check)

Strength score (0-4):
- 0: Very weak (< 8 chars)
- 1: Weak (meets basic requirements)
- 2: Fair (10+ chars, mixed case)
- 3: Strong (12+ chars, all types)
- 4: Very strong (16+ chars, high entropy)

Rejected passwords:
- "password123"
- "qwerty123"
- Common keyboard patterns
- User's personal info (name, email)
```

**Compromised Password Detection**:
```
API: HaveIBeenPwned (k-anonymity)

Flow:
1. Hash password: SHA1(password)
2. Take first 5 characters of hash
3. Send to HIBP API: /range/21BD1
4. Receive list of hash suffixes:
   0018A45C4D1DEF81644B54AB7F969B88D65:3
   00D4F6E8FA6EECAD2A3AA415EEC418D38EC:1
   ...
5. Check if our hash suffix is in list
6. If found: Password compromised, reject

Privacy:
- Never send full password or hash
- Only first 5 chars of hash sent
- k-anonymity preserved
- Zero knowledge to HIBP
```

**Password Reset Flow**:
```
1. User clicks "Forgot Password"
2. Enter email address
3. Server checks if email exists (don't reveal)
4. Generate reset token:
   - 32 bytes random
   - Hash with SHA256
   - Store hash in database with expiry (1 hour)
5. Send email with reset link:
   https://example.com/reset?token=<token>
6. User clicks link
7. Server validates token:
   - Check not expired
   - Check not already used
8. User enters new password
9. Validate password strength
10. Hash new password (Argon2id)
11. Update in database
12. Invalidate reset token
13. Invalidate all sessions (force re-login)
14. Send confirmation email

Security:
- Tokens expire in 1 hour
- One-time use only
- Don't reveal if email exists
- Always show "Check your email" message
```

### 5. OAuth Service

**OAuth 2.0 Authorization Code Flow**:
```
Google Login Example:

1. User clicks "Login with Google"
2. Redirect to Google authorization:
   https://accounts.google.com/o/oauth2/v2/auth?
     client_id=<our_client_id>
     &redirect_uri=https://example.com/auth/google/callback
     &response_type=code
     &scope=openid email profile
     &state=<csrf_token>

3. User logs in to Google, grants permissions
4. Google redirects back:
   https://example.com/auth/google/callback?
     code=<authorization_code>
     &state=<csrf_token>

5. Server validates state (CSRF protection)
6. Exchange code for tokens:
   POST https://oauth2.googleapis.com/token
   {
     "code": "<authorization_code>",
     "client_id": "<our_client_id>",
     "client_secret": "<our_secret>",
     "redirect_uri": "https://example.com/auth/google/callback",
     "grant_type": "authorization_code"
   }

7. Google responds with tokens:
   {
     "access_token": "<google_access_token>",
     "id_token": "<jwt_id_token>",
     "expires_in": 3600,
     "refresh_token": "<google_refresh_token>"
   }

8. Decode id_token (JWT):
   {
     "sub": "google_user_id_123",
     "email": "user@gmail.com",
     "email_verified": true,
     "name": "John Doe",
     "picture": "https://..."
   }

9. Check if user exists (by email or provider_user_id)
10. If new user:
    - Create account
    - Link to Google account
    - Skip password (OAuth-only)
11. If existing user:
    - Link Google account if not linked
    - Or just login
12. Generate our JWT tokens
13. Return to client

Total time: 2-5 seconds (multiple redirects)
```

**Account Linking**:
```
Scenario: User has password account, wants to add Google

Flow:
1. User already logged in (has session)
2. Clicks "Connect Google Account"
3. OAuth flow as above
4. After receiving Google tokens:
   - Check if Google account already linked to another user
   - If yes: Reject (Google account already in use)
   - If no: Link to current user
5. Store in oauth_accounts table:
   {
     user_id: "usr_abc123",
     provider: "google",
     provider_user_id: "google_user_id_123",
     access_token: "<encrypted>",
     refresh_token: "<encrypted>",
     email: "user@gmail.com"
   }
6. User can now login with either password or Google
```

**Supported Providers**:
```
1. Google
   - OpenID Connect
   - Scopes: openid, email, profile
   - Reliable, fast
   - 99.9% uptime

2. Facebook
   - OAuth 2.0
   - Scopes: email, public_profile
   - Graph API for user info
   - Frequent API changes

3. Apple Sign In
   - Required for iOS apps
   - Privacy-focused (email relay)
   - OpenID Connect
   - Provides JWT directly

4. GitHub
   - OAuth 2.0
   - Developer platforms
   - Scopes: user:email
   - Reliable
```

### 6. Rate Limiter

**Sliding Window Algorithm**:
```
Implementation: Redis Sorted Set

Key: rate_limit:login:user_123
Type: Sorted Set
Score: Timestamp
Value: Attempt ID

Add attempt:
ZADD rate_limit:login:user_123 <timestamp> <attempt_id>

Count in window (last 15 minutes):
ZCOUNT rate_limit:login:user_123 <now-900> <now>

Remove old entries:
ZREMRANGEBYSCORE rate_limit:login:user_123 0 <now-900>

Check limit:
IF count >= 5 THEN
  lockout_until = now + 900  // 15 minutes
  SET lockout:user_123 lockout_until EX 900
  RETURN "Account locked for 15 minutes"
```

**Progressive Delays**:
```
Failed Attempt → Delay before next attempt

1st fail: 0 seconds
2nd fail: 2 seconds
3rd fail: 5 seconds (+ CAPTCHA)
4th fail: 10 seconds
5th fail: 15 minute lockout

Implementation:
After failed attempt:
  delay = min(2^(attempts-1), 10)  // Exponential backoff
  SETEX delay:user_123 delay 1
  
Before processing login:
  IF EXISTS delay:user_123 THEN
    RETURN 429 "Too many attempts, wait X seconds"
```

**Multi-Level Rate Limiting**:
```
1. Per IP (prevent brute force from single machine):
   - 10 login attempts per minute
   - 100 API calls per minute
   - Block for 1 hour if exceeded

2. Per User (prevent distributed attack on single account):
   - 5 failed logins → 15 min lockout
   - 10 failed logins → 1 hour lockout
   - 20 failed logins → Account freeze (manual review)

3. Per Endpoint:
   - /login: 10/min per IP
   - /register: 5/min per IP
   - /password-reset: 3/15min per IP
   - /verify-email: 5/hour per user

4. Global (DDoS protection):
   - 10,000 requests/second system-wide
   - If exceeded: Enable CAPTCHA for all
   - Alert security team
```

---

## Database Design

### PostgreSQL Schema

```sql
-- Users table
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    email_verified BOOLEAN DEFAULT false,
    phone VARCHAR(20),
    phone_verified BOOLEAN DEFAULT false,
    username VARCHAR(50) UNIQUE,
    password_hash VARCHAR(255),  -- Argon2 hash
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    status VARCHAR(20) DEFAULT 'active',  -- active, locked, suspended, deleted
    mfa_enabled BOOLEAN DEFAULT false,
    mfa_secret VARCHAR(255),  -- Encrypted TOTP secret
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_login_at TIMESTAMP,
    deleted_at TIMESTAMP  -- Soft delete
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_status ON users(status);

-- OAuth accounts
CREATE TABLE oauth_accounts (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,  -- google, facebook, apple, github
    provider_user_id VARCHAR(255) NOT NULL,
    access_token TEXT,  -- Encrypted
    refresh_token TEXT,  -- Encrypted
    token_expires_at TIMESTAMP,
    email VARCHAR(255),
    profile_data JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(provider, provider_user_id)
);

CREATE INDEX idx_oauth_user_id ON oauth_accounts(user_id);
CREATE INDEX idx_oauth_provider ON oauth_accounts(provider, provider_user_id);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,  -- SHA256 of token
    device_id VARCHAR(255),
    device_info JSONB,
    ip_address INET,
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP DEFAULT NOW(),
    revoked BOOLEAN DEFAULT false,
    revoked_at TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked = false;

-- Sessions (also stored in Redis for speed)
CREATE TABLE sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    device_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    location JSONB,  -- {city, country, lat, lon}
    created_at TIMESTAMP DEFAULT NOW(),
    last_activity TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT true
);

CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_active ON sessions(user_id, active) WHERE active = true;

-- Password history (prevent reuse)
CREATE TABLE password_history (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_password_history_user ON password_history(user_id, created_at DESC);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT false,
    used_at TIMESTAMP,
    ip_address INET,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reset_tokens_hash ON password_reset_tokens(token_hash) WHERE used = false;
CREATE INDEX idx_reset_tokens_user ON password_reset_tokens(user_id, created_at DESC);

-- Email verification tokens
CREATE TABLE verification_tokens (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    token_type VARCHAR(20) NOT NULL,  -- email, phone
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT false,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_verification_tokens_hash ON verification_tokens(token_hash) WHERE used = false;

-- MFA backup codes
CREATE TABLE backup_codes (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used BOOLEAN DEFAULT false,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_backup_codes_user ON backup_codes(user_id) WHERE used = false;

-- Trusted devices
CREATE TABLE trusted_devices (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    device_id VARCHAR(255) NOT NULL,
    device_name VARCHAR(255),
    device_fingerprint VARCHAR(255),
    trusted_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    active BOOLEAN DEFAULT true,
    UNIQUE(user_id, device_id)
);

CREATE INDEX idx_trusted_devices_user ON trusted_devices(user_id, active);

-- Roles and permissions (RBAC)
CREATE TABLE roles (
    role_id SERIAL PRIMARY KEY,
    role_name VARCHAR(50) UNIQUE NOT NULL,  -- admin, user, moderator
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE permissions (
    permission_id SERIAL PRIMARY KEY,
    permission_name VARCHAR(100) UNIQUE NOT NULL,  -- read:users, write:users
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE role_permissions (
    role_id INT REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id INT REFERENCES permissions(permission_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    role_id INT REFERENCES roles(role_id) ON DELETE CASCADE,
    granted_at TIMESTAMP DEFAULT NOW(),
    granted_by UUID REFERENCES users(user_id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
```

### Cassandra Schema (Audit Logs)

```cql
-- Authentication events
CREATE TABLE auth_events (
    user_id uuid,
    event_time timestamp,
    event_id timeuuid,
    event_type text,  -- login, logout, login_failed, mfa_challenge, password_reset
    ip_address text,
    user_agent text,
    device_id text,
    location text,  -- JSON
    result text,  -- success, failure
    failure_reason text,
    metadata text,  -- JSON
    PRIMARY KEY ((user_id), event_time, event_id)
) WITH CLUSTERING ORDER BY (event_time DESC, event_id DESC)
AND default_time_to_live = 31536000;  -- 1 year

-- Security events (suspicious activity)
CREATE TABLE security_events (
    event_date text,  -- YYYY-MM-DD for partitioning
    event_time timestamp,
    event_id timeuuid,
    user_id uuid,
    event_type text,  -- account_locked, suspicious_login, password_changed
    severity text,  -- low, medium, high, critical
    ip_address text,
    details text,  -- JSON
    PRIMARY KEY ((event_date), event_time, event_id)
) WITH CLUSTERING ORDER BY (event_time DESC, event_id DESC)
AND default_time_to_live = 31536000;

-- Failed login attempts (for analysis)
CREATE TABLE failed_logins (
    ip_address text,
    attempt_time timestamp,
    event_id timeuuid,
    email text,
    reason text,
    user_agent text,
    PRIMARY KEY ((ip_address), attempt_time, event_id)
) WITH CLUSTERING ORDER BY (attempt_time DESC, event_id DESC)
AND default_time_to_live = 2592000;  -- 30 days
```

---

## Authentication Flow

### Complete Login Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ USER LOGIN FLOW - End to End                                    │
└─────────────────────────────────────────────────────────────────┘

T=0ms: User submits credentials
{
  "email": "user@example.com",
  "password": "SecureP@ssw0rd!",
  "device_id": "device_xyz"
}

T=5ms: API Gateway
├─ Rate limit check (Redis)
│  └─ IP: 10 attempts/min ✓
│  └─ User: 3 failed attempts (< 5 limit) ✓
├─ Request validation
└─ Route to Auth Service

T=10ms: Auth Service
├─ Fetch user by email (PostgreSQL + Redis cache)
│  Cache miss → Query DB → Cache result (TTL: 1 hour)
│  User found ✓

T=310ms: Password Verification
├─ Argon2id verify(stored_hash, input_password)
│  Memory: 64 MB
│  Iterations: 3
│  Time: ~300ms
│  Result: MATCH ✓

T=315ms: Post-Authentication Checks
├─ Account status check
│  Status: active ✓
├─ MFA status check
│  MFA enabled: true
│  └─ Create MFA challenge
│  └─ Return 202 Accepted
│      {
│        "challenge_id": "chal_abc",
│        "mfa_methods": ["totp", "sms"],
│        "message": "Enter MFA code"
│      }

[User enters TOTP code from authenticator app]

T=30s: MFA Verification Request
{
  "challenge_id": "chal_abc",
  "code": "123456",
  "method": "totp"
}

T=30.005s: MFA Service
├─ Fetch TOTP secret (decrypted)
├─ Calculate valid codes:
│  └─ Current window: 123456 ✓
│  └─ Previous window: 654321
│  └─ Next window: 789012
├─ Code matches current window ✓
├─ Check replay (Redis):
│  └─ Code not used in last 90 seconds ✓
└─ Mark code as used (Redis, TTL: 90s)

T=30.100s: Token Generation
├─ Generate access token (JWT)
│  ├─ Claims: user_id, email, roles, permissions
│  ├─ Expiry: 15 minutes
│  ├─ Sign with RS256 (private key)
│  └─ Token size: ~500 bytes
├─ Generate refresh token
│  ├─ Random 32 bytes
│  ├─ Hash with SHA256
│  ├─ Store in PostgreSQL
│  └─ Expiry: 30 days

T=30.150s: Session Creation
├─ Create session record
│  ├─ Store in Redis (fast lookup)
│  │  SET session:sess_abc {...} EX 1800
│  └─ Store in PostgreSQL (durability)
├─ Track device
│  └─ Add to trusted_devices if new

T=30.200s: Audit Logging
├─ Log successful login (Cassandra)
│  Event: login_success
│  User: usr_abc123
│  IP: 192.168.1.1
│  Device: device_xyz
│  Timestamp: 2024-01-08T10:00:00Z

T=30.250s: Notifications (Async)
├─ Send login notification email
│  "New login from Chrome on Windows"
│  Location: San Francisco, CA
│  Device: device_xyz
│  Time: 10:00 AM PST
│  [Was this you?] [Secure your account]

T=30.300s: Response
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "rt_def456...",
  "token_type": "Bearer",
  "expires_in": 900,
  "user": {
    "user_id": "usr_abc123",
    "email": "user@example.com",
    "username": "john_doe",
    "first_name": "John",
    "last_name": "Doe",
    "roles": ["user", "premium"]
  }
}

Total latency: ~350ms (including MFA)
```

### OAuth Login Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ SOCIAL LOGIN FLOW - Google OAuth 2.0                            │
└─────────────────────────────────────────────────────────────────┘

T=0s: User clicks "Login with Google"

T=0.1s: Redirect to Google
https://accounts.google.com/o/oauth2/v2/auth?
  client_id=<our_client_id>
  &redirect_uri=https://example.com/auth/google/callback
  &response_type=code
  &scope=openid email profile
  &state=<csrf_token>

[User logs in to Google, grants permissions]

T=5s: Google redirects back
https://example.com/auth/google/callback?
  code=<authorization_code>
  &state=<csrf_token>

T=5.1s: OAuth Service
├─ Validate state (CSRF protection) ✓
├─ Exchange code for tokens
│  POST https://oauth2.googleapis.com/token
│  {
│    "code": "<auth_code>",
│    "client_id": "<our_client_id>",
│    "client_secret": "<our_secret>",
│    "redirect_uri": "...",
│    "grant_type": "authorization_code"
│  }

T=5.5s: Google responds
{
  "access_token": "<google_access_token>",
  "id_token": "<jwt_id_token>",
  "expires_in": 3600,
  "refresh_token": "<google_refresh_token>"
}

T=5.6s: Decode ID Token
{
  "sub": "google_user_id_123",
  "email": "user@gmail.com",
  "email_verified": true,
  "name": "John Doe",
  "picture": "https://lh3.googleusercontent.com/..."
}

T=5.7s: User Lookup
├─ Check if user exists:
│  SELECT * FROM users WHERE email = 'user@gmail.com'
│  OR EXISTS (
│    SELECT 1 FROM oauth_accounts
│    WHERE provider = 'google'
│    AND provider_user_id = 'google_user_id_123'
│  )

Case 1: New User (First time Google login)
├─ Create new user account
│  INSERT INTO users (email, username, first_name, ...)
│  VALUES ('user@gmail.com', 'john_doe', 'John', ...)
│  email_verified = true (Google verified)
│  password_hash = NULL (OAuth-only)
├─ Create OAuth link
│  INSERT INTO oauth_accounts (user_id, provider, ...)
├─ Generate JWT tokens (our system)
└─ Return tokens

Case 2: Existing User
├─ Check if Google account already linked
│  If not linked: Create oauth_accounts entry
│  If linked: Just login
├─ Generate JWT tokens
└─ Return tokens

T=6s: Complete
Total time: ~6 seconds (includes Google interaction)
```

---

## Deep Dives

### 1. Preventing Credential Stuffing Attacks

**Attack**: Attackers use leaked credentials from other sites to try logging in

**Defense Layers**:

```
Layer 1: Rate Limiting
- 5 failed attempts per user → 15 min lockout
- 10 login attempts per IP per minute
- Block IPs from known botnets

Layer 2: CAPTCHA
- After 3 failed attempts
- Invisible reCAPTCHA for suspicious behavior
- Fallback to image CAPTCHA

Layer 3: Device Fingerprinting
- Track: User-agent, screen resolution, timezone, plugins
- Create fingerprint hash
- Flag login if fingerprint changed
- Challenge with MFA if suspicious

Layer 4: Behavioral Analysis
- Track normal login times (user logs in 9-5 PM usually)
- Flag logins outside normal hours
- Track normal locations (IP geolocation)
- Flag logins from new countries

Layer 5: Compromised Password Detection
- Check against HaveIBeenPwned
- Force password change if compromised
- Notify user of breach

Layer 6: ML-Based Anomaly Detection
- Features:
  * Login time deviation from user's pattern
  * Geographic distance from last login
  * Device/browser changed
  * Failed attempt patterns
  * Typing speed/pattern (keystroke dynamics)
- Risk score: 0-100
- Actions:
  * < 30: Allow
  * 30-60: Require email OTP
  * 60-80: Require SMS OTP + Email
  * > 80: Block + Alert security team

Implementation:
IF risk_score > 30 THEN
  Require additional verification
IF risk_score > 80 THEN
  Block login
  Send security alert
  Require password reset
```

### 2. JWT Token Security

**Token Theft Scenarios**:

```
Scenario 1: XSS Attack (Cross-Site Scripting)
- Attacker injects malicious JavaScript
- Script reads tokens from localStorage
- Sends tokens to attacker's server

Defense:
✓ Store tokens in httpOnly cookies (not accessible to JavaScript)
✓ Use Content Security Policy (CSP)
✓ Sanitize all user inputs
✓ Use secure cookie flags: httpOnly, secure, sameSite

Scenario 2: Token Replay
- Attacker intercepts valid token
- Uses it before expiration

Defense:
✓ Short-lived access tokens (15 minutes)
✓ Refresh token rotation
✓ Bind tokens to device fingerprint
✓ Monitor for suspicious patterns:
  - Same token used from multiple IPs simultaneously
  - Impossible travel (Tokyo → New York in 1 hour)

Scenario 3: Man-in-the-Middle (MITM)
- Attacker intercepts traffic
- Reads tokens

Defense:
✓ TLS 1.3 only
✓ HSTS (HTTP Strict Transport Security)
✓ Certificate pinning (mobile apps)
✓ No mixed content (all resources HTTPS)

Scenario 4: Compromised Refresh Token
- Long-lived token stolen
- Attacker can generate new access tokens

Defense:
✓ Refresh token rotation (new token on each use)
✓ Detect reuse of old refresh token (token theft detected)
✓ Automatic revocation of all tokens for that user
✓ Force re-authentication
✓ Alert user via email
```

### 3. Handling Distributed Sessions

**Challenge**: User logs in, load balancer routes to Server A. Next request routes to Server B. How does Server B know user is authenticated?

**Solution (JWT + Redis)**:
```
Hybrid approach combines benefits of both:

1. JWT for fast validation (99% of requests):
   - Stateless, no shared state needed
   - Servers validate independently
   - < 5ms validation time

2. Redis for critical operations (1% of requests):
   - Check token revocation
   - Session metadata
   - Real-time updates

Flow:
1. User logs in at Server A
2. Generate JWT + store session in Redis
3. Return JWT to client
4. Client sends JWT to Server B
5. Server B validates JWT (no Redis needed)
6. For sensitive operations: Check Redis
7. Request processed

Benefits:
- Fast (stateless validation)
- Flexible (can revoke instantly)
- Scalable (servers don't communicate)
```

---

## Scalability & Reliability

### Horizontal Scaling

**Database Sharding**:
```
PostgreSQL sharding by user_id:
- 16 shards (hash(user_id) % 16)
- Each shard: ~31M users
- Read replicas: 3 per shard
- Cross-shard queries: Avoid

Benefits:
- Linear scalability
- Isolated failures
- Better performance
```

**Auto-Scaling**:
```
Metrics triggering scale-up:
- CPU > 70% for 5 minutes
- Queue depth > 1000
- Response time > 500ms

Scale policy:
- Add 20% more instances
- Min: 10 instances per region
- Max: 100 instances per region
- Cool-down: 5 minutes
```

### High Availability

**Multi-Region Deployment**:
```
3 Regions: US-East, EU-West, AP-South

Each region:
- Complete auth stack
- Local databases
- Redis cluster
- Cassandra with multi-DC replication

Failover:
- GeoDNS routes to nearest region
- Cross-region failover < 30 seconds
- Data replicated asynchronously
```

**Disaster Recovery**:
```
Backup Strategy:
- PostgreSQL: Continuous WAL + daily snapshots
- Cassandra: Daily snapshots + incremental backups
- Redis: RDB every 6 hours + AOF

Recovery Times:
- RTO: 4 hours
- RPO: 15 minutes

Scenarios:
1. Server failure: < 30 seconds (auto-failover)
2. Database failure: 2-4 hours (restore from backup)
3. Region failure: 2-5 minutes (GeoDNS failover)
```

### Monitoring

**Key Metrics**:
```
Authentication:
- Login success rate: > 95%
- Login latency p95: < 200ms
- Failed login rate: < 5%
- MFA success rate: > 90%

Security:
- Brute force attempts
- Account takeover attempts
- Suspicious activity score
- Password compromise rate

System:
- API availability: > 99.99%
- CPU usage: < 70%
- Memory usage: < 80%
- Database connections: < 80%
```

**Alerting**:
```
P0 (Page immediately):
- API error rate > 5%
- Database unavailable
- Login success < 90%

P1 (Alert on-call):
- Login latency > 500ms
- Failed login rate > 10%
- Brute force attack detected

P2 (Business hours):
- Slow queries
- High memory usage
- Certificate expiring < 30 days
```

---

## Trade-offs & Alternatives

### 1. JWT vs Session Tokens

**JWT** (Chosen):
```
Pros:
+ Stateless (no DB lookup)
+ Horizontal scaling easy
+ Self-contained (all info in token)
+ Fast validation (< 5ms)

Cons:
- Cannot instantly revoke
- Larger payload size
- Token contains stale data

When to use:
- High traffic systems
- Microservices
- Need horizontal scaling
```

**Session Tokens**:
```
Pros:
+ Instant revocation
+ Smaller payload
+ Fresh data always
+ Easier to implement

Cons:
- Requires shared state (Redis)
- DB lookup on each request
- Single point of failure
- Harder to scale

When to use:
- Admin systems
- High-security applications
- Small scale
```

### 2. Argon2 vs bcrypt vs scrypt

**Argon2id** (Chosen):
```
Pros:
+ Winner of Password Hashing Competition
+ Memory-hard (GPU resistant)
+ Configurable parameters
+ Modern algorithm (2015)

Cons:
- Less widely adopted
- Newer (less battle-tested)

Parameters:
- Memory: 64 MB
- Time: ~300-500ms
```

**bcrypt**:
```
Pros:
+ Battle-tested (1999)
+ Widely supported
+ Adaptive (adjustable cost)

Cons:
- CPU-only (vulnerable to GPU)
- Fixed memory usage
- Older algorithm

When to use:
- Legacy systems
- Need wide compatibility
```

### 3. Multi-Region: Active-Active vs Active-Passive

**Active-Active** (Chosen):
```
Pros:
+ Lower latency (route to nearest)
+ Better resource utilization
+ No wasted capacity
+ Faster failover

Cons:
- More complex
- Data consistency challenges
- Higher cost

Implementation:
- All regions serve traffic
- Async replication
- Eventual consistency acceptable
```

**Active-Passive**:
```
Pros:
+ Simpler to implement
+ Easier data consistency
+ Lower cost (standby cheaper)

Cons:
- Higher latency
- Wasted capacity in standby
- Slower failover (minutes)

When to use:
- Smaller scale
- Strong consistency required
- Budget constraints
```

### 4. Passwordless vs Password-based

**Password-based** (Chosen as default):
```
Pros:
+ Familiar to users
+ Works offline
+ No dependency on email/SMS
+ Universal support

Cons:
- Users choose weak passwords
- Phishing vulnerability
- Password reuse
- Forgot password friction

Best practices:
- Strong password requirements
- MFA enforcement
- Compromised password detection
```

**Passwordless (Magic Link)**:
```
Pros:
+ No password to remember
+ No password reuse
+ Phishing resistant
+ Better UX for some users

Cons:
- Requires email/SMS access
- Dependency on third party
- Email delivery delays
- Link expiration friction

When to use:
- Consumer apps
- Low-security contexts
- Infrequent logins
```

### 5. SMS OTP vs Authenticator App

**Authenticator App (TOTP)** (Preferred):
```
Pros:
+ No SMS cost
+ Works offline
+ More secure (no SIM swap)
+ Faster (no network delay)

Cons:
- Requires smartphone
- Setup friction
- Lost device = locked out

Use when:
- Security is priority
- Cost-sensitive
- Target tech-savvy users
```

**SMS OTP**:
```
Pros:
+ Universal (any phone)
+ Easy to understand
+ No app install needed

Cons:
- SIM swap vulnerability
- SMS delivery delays
- Cost ($0.01 per SMS)
- Network dependency

Use when:
- Need wide accessibility
- Low-tech users
- Backup method
```

---

## Conclusion

This authentication system design provides:

**Security**:
- Argon2id password hashing
- Multi-factor authentication
- Rate limiting and brute-force protection
- Comprehensive audit logging
- Token rotation and revocation

**Scalability**:
- Supports 500M users, 100M DAU
- Handles 10K logins/second peak
- Horizontal scaling with JWT
- Multi-region deployment
- Auto-scaling capabilities

**Reliability**:
- 99.99% availability
- Multi-region active-active
- Automatic failover < 30 seconds
- Disaster recovery (RTO: 4 hours)

**Performance**:
- Login latency < 200ms (p95)
- Token validation < 10ms
- Password hashing ~300-500ms

The system balances security, scalability, and user experience while remaining flexible enough to adapt to future requirements and scale to 1B users.

---

**Document Status**: Complete ✓
