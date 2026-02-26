# Design a Secure Key Vault Service — Hello Interview Framework

> **Question**: Design a service like Azure Key Vault that securely stores and retrieves secrets, certificates, and keys for applications and users.
>
> **Source**: [Exponent](https://www.tryexponent.com/questions/5437/system-design-secure-key-vault-service)
>
> **Asked at**: Microsoft
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

## Context: What is a Key Vault?

Applications need secrets: database passwords, API keys, TLS certificates, encryption keys. Hardcoding them in code or config files is insecure — anyone with repo access sees them. A **Key Vault** is a centralized, secure service that stores secrets and provides controlled access with full audit logging.

**Real-world examples**: Azure Key Vault, AWS Secrets Manager, HashiCorp Vault, Google Secret Manager.

**Why interviewers ask this**: It tests security thinking (encryption, access control, HSMs), distributed systems (replication, availability), and API design (versioning, pagination, access policies).

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Store Secrets**: Create, read, update, delete key-value secrets (e.g., `DB_PASSWORD=hunter2`). Secrets are encrypted at rest. Support versioning — every update creates a new version, old versions are retrievable.
2. **Store Cryptographic Keys**: Generate and manage RSA/AES keys for encryption/signing. Support key operations (encrypt, decrypt, sign, verify) WITHOUT ever exposing the raw key material.
3. **Store Certificates**: Upload and manage X.509 TLS certificates with metadata (expiration, issuer). Alert on upcoming expirations.
4. **Access Control (RBAC + Policies)**: Fine-grained permissions per vault, per secret. "App A can read secret X, but not secret Y." "User B can manage certificates but not keys."
5. **Audit Logging**: Every access (read, write, delete) is logged with who, what, when, from where. Immutable audit trail for compliance (SOC 2, HIPAA).
6. **Secret Rotation**: Support automatic rotation of secrets (e.g., rotate DB password every 30 days) with zero-downtime for consuming applications.

#### Nice to Have (P1)
- Hardware Security Module (HSM) backing for key storage (FIPS 140-2 Level 3)
- Cross-region replication for disaster recovery
- Dynamic secrets (generate short-lived credentials on demand, like Vault's database secrets)
- Secret references / injection (sidecar or init-container pattern for Kubernetes)
- Soft delete with recovery window (30 days)
- Multi-tenant vault isolation

#### Below the Line (Out of Scope)
- Full PKI (Certificate Authority) implementation
- Identity provider (assume Azure AD / OAuth exists)
- Network security (VPN, private endpoints — assume they exist)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Availability** | 99.99% (52 min downtime/year) | Applications can't start without secrets |
| **Latency** | < 50ms p99 for secret reads | Secrets fetched on every request/startup |
| **Durability** | 99.999999999% (11 nines) | Losing a secret = losing access to systems |
| **Security** | Zero plaintext exposure; encrypted at rest + in transit | THE core requirement — this is a security product |
| **Throughput** | 10,000 reads/sec per vault | Microservices fleet fetching secrets concurrently |
| **Consistency** | Strong consistency for writes; reads can be eventual (< 1s) | Newly rotated secrets must be available immediately |
| **Compliance** | SOC 2 Type II, HIPAA, FedRAMP | Enterprise customers require compliance certification |

### Capacity Estimation

```
Scale (Azure Key Vault-like):
  Tenants: 500,000 organizations
  Vaults: 2M vaults total
  Secrets per vault: avg 50 (range: 1-10,000)
  Total secrets: 100M across all vaults

Storage:
  Average secret size: 1 KB (value + metadata + versions)
  Total secret data: 100M × 1 KB = 100 GB (easily fits on one node)
  With 5 versions per secret: 500 GB
  With encryption overhead (2x): 1 TB
  With replication (3x): 3 TB

Traffic:
  Read QPS: 1M reads/sec globally (across all vaults)
  Write QPS: 10K writes/sec (secrets rarely change)
  Read:Write ratio: 100:1

  Per-vault peak: 10,000 reads/sec (large microservices fleet)
  Per-secret peak: 1,000 reads/sec (hot secret)

Audit logs:
  1M reads/sec × 500 bytes/log = 500 MB/sec
  Daily: 43 TB of audit logs
  Retention: 1 year → 15 PB (compressed: ~3 PB)
```

---

## 2️⃣ Core Entities

### Entity 1: Vault
```java
public class Vault {
    private final String vaultId;              // UUID
    private final String vaultName;            // "my-app-prod-vault"
    private final String tenantId;             // Azure AD tenant
    private final String region;               // "eastus"
    private final VaultSku sku;                // STANDARD (software), PREMIUM (HSM-backed)
    private final boolean softDeleteEnabled;   // 30-day recovery
    private final boolean purgeProtectionEnabled; // Cannot permanently delete
    private final Instant createdAt;
    private final VaultStatus status;          // ACTIVE, SOFT_DELETED, PURGED
}
```

### Entity 2: Secret
```java
public class Secret {
    private final String secretId;             // vault_id + name
    private final String vaultId;
    private final String name;                 // "DB_PASSWORD"
    private final String currentVersion;       // "v3" (latest)
    private final boolean enabled;
    private final Instant notBefore;           // Secret not valid before this time
    private final Instant expiresAt;           // Secret expires (auto-disable)
    private final Map<String, String> tags;    // User-defined metadata
    private final ContentType contentType;     // "text/plain", "application/json"
}

public class SecretVersion {
    private final String secretId;
    private final String version;              // "v1", "v2", "v3"
    private final byte[] encryptedValue;       // AES-256 encrypted secret value
    private final String keyEncryptionKeyId;   // Which KEK encrypted this version
    private final Instant createdAt;
    private final boolean enabled;
    private final String createdBy;            // User/app that created this version
}
```

### Entity 3: Cryptographic Key
```java
public class CryptoKey {
    private final String keyId;
    private final String vaultId;
    private final String name;                 // "my-encryption-key"
    private final KeyType keyType;             // RSA_2048, RSA_4096, AES_256, EC_P256
    private final List<KeyOperation> allowedOps; // ENCRYPT, DECRYPT, SIGN, VERIFY, WRAP, UNWRAP
    private final String currentVersion;
    private final boolean enabled;
    private final boolean hsmBacked;           // true = key material in HSM, never exportable
    private final Instant expiresAt;
}

// KEY MATERIAL IS NEVER RETURNED TO THE CALLER
// Instead, the vault performs operations ON BEHALF of the caller:
//   POST /keys/{name}/encrypt  → sends plaintext, gets ciphertext back
//   POST /keys/{name}/decrypt  → sends ciphertext, gets plaintext back
//   POST /keys/{name}/sign     → sends hash, gets signature back
```

### Entity 4: Certificate
```java
public class Certificate {
    private final String certId;
    private final String vaultId;
    private final String name;                 // "api-tls-cert"
    private final String subject;              // "CN=api.example.com"
    private final String issuer;               // "DigiCert", "Let's Encrypt", "Self"
    private final Instant notBefore;
    private final Instant expiresAt;
    private final String thumbprint;           // SHA-1 hash of cert
    private final byte[] encryptedPfx;         // Encrypted PKCS#12 bundle
    private final String associatedKeyId;      // Private key stored as CryptoKey
    private final CertificatePolicy policy;    // Auto-renewal settings
}
```

### Entity 5: Access Policy
```java
public class AccessPolicy {
    private final String vaultId;
    private final String principalId;          // User, app, or group ID
    private final PrincipalType principalType; // USER, SERVICE_PRINCIPAL, GROUP
    private final Set<SecretPermission> secretPermissions;   // GET, LIST, SET, DELETE, BACKUP, RESTORE
    private final Set<KeyPermission> keyPermissions;         // GET, LIST, CREATE, DELETE, ENCRYPT, DECRYPT, SIGN
    private final Set<CertPermission> certPermissions;       // GET, LIST, CREATE, DELETE, IMPORT
}

public enum SecretPermission {
    GET, LIST, SET, DELETE, BACKUP, RESTORE, RECOVER, PURGE
}
```

### Entity 6: Audit Log Entry
```java
public class AuditLogEntry {
    private final String logId;
    private final Instant timestamp;
    private final String vaultId;
    private final String resourceType;         // SECRET, KEY, CERTIFICATE
    private final String resourceName;         // "DB_PASSWORD"
    private final String operation;            // "SecretGet", "KeyDecrypt", "CertCreate"
    private final String callerIdentity;       // "app-id-123" or "user@company.com"
    private final String callerIpAddress;
    private final String result;               // "Success", "Forbidden", "NotFound"
    private final int httpStatusCode;
    private final String correlationId;        // For request tracing
}
```

---

## 3️⃣ API Design

### 1. Create/Update Secret
```
PUT /vaults/{vault_name}/secrets/{secret_name}

Headers:
  Authorization: Bearer <JWT>

Request:
{
  "value": "my-super-secret-password",
  "content_type": "text/plain",
  "tags": { "environment": "production", "owner": "team-backend" },
  "attributes": {
    "enabled": true,
    "not_before": "2025-01-01T00:00:00Z",
    "expires": "2026-01-01T00:00:00Z"
  }
}

Response (201 Created):
{
  "id": "https://my-vault.vault.azure.net/secrets/DB_PASSWORD/v3",
  "name": "DB_PASSWORD",
  "version": "v3",
  "attributes": {
    "enabled": true,
    "created": "2025-06-14T10:00:00Z",
    "updated": "2025-06-14T10:00:00Z",
    "expires": "2026-01-01T00:00:00Z"
  },
  "tags": { "environment": "production" }
}
// Note: value is NOT returned in the create response (write-only)
```

### 2. Get Secret
```
GET /vaults/{vault_name}/secrets/{secret_name}
GET /vaults/{vault_name}/secrets/{secret_name}/{version}  ← specific version

Headers:
  Authorization: Bearer <JWT>

Response (200 OK):
{
  "id": "https://my-vault.vault.azure.net/secrets/DB_PASSWORD/v3",
  "value": "my-super-secret-password",
  "attributes": {
    "enabled": true,
    "created": "2025-06-14T10:00:00Z",
    "expires": "2026-01-01T00:00:00Z"
  },
  "tags": { "environment": "production" }
}
// AUDIT: logged as "SecretGet" with caller identity and IP
```

### 3. Key Operations (encrypt/decrypt WITHOUT exposing key)
```
POST /vaults/{vault_name}/keys/{key_name}/encrypt

Request:
{
  "algorithm": "RSA-OAEP-256",
  "value": "base64-encoded-plaintext"
}

Response:
{
  "kid": "https://my-vault.vault.azure.net/keys/my-key/v2",
  "value": "base64-encoded-ciphertext",
  "algorithm": "RSA-OAEP-256"
}
// The raw key material NEVER leaves the vault / HSM
```

### 4. List Secrets (names only, not values)
```
GET /vaults/{vault_name}/secrets?maxresults=25&skiptoken=abc

Response:
{
  "value": [
    { "id": ".../secrets/DB_PASSWORD", "attributes": { "enabled": true } },
    { "id": ".../secrets/API_KEY", "attributes": { "enabled": true } }
  ],
  "nextLink": "...?skiptoken=def"
}
// Values are NOT included in list — must GET individually (with audit log)
```

### 5. Set Access Policy
```
PUT /vaults/{vault_name}/accessPolicies/add

Request:
{
  "properties": {
    "accessPolicies": [{
      "tenant_id": "tenant-123",
      "object_id": "app-service-principal-456",
      "permissions": {
        "secrets": ["get", "list"],
        "keys": ["encrypt", "decrypt"],
        "certificates": []
      }
    }]
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Application Reads a Secret
```
1. App starts → needs DB_PASSWORD from vault
   App has a managed identity (service principal) with JWT token
   ↓
2. GET /vaults/my-vault/secrets/DB_PASSWORD
   Authorization: Bearer <JWT from Azure AD>
   ↓
3. API Gateway:
   a. Validate JWT (signature, expiry, audience)
   b. Extract caller identity: "app-id-456"
   c. Rate limit check (10,000 req/sec per vault)
   ↓
4. Vault Service:
   a. Check access policy: does app-id-456 have GET permission on secrets?
      → YES: proceed
      → NO: return 403 Forbidden (logged)
   b. Check if secret is enabled and not expired
   c. Fetch encrypted secret value from storage
   d. Decrypt value using vault's master key (KEK → DEK → plaintext)
   e. Return plaintext to caller over TLS
   ↓
5. Audit log written (async):
   { "operation": "SecretGet", "caller": "app-id-456",
     "resource": "DB_PASSWORD", "result": "Success", "ip": "10.0.1.50" }
   ↓
6. App receives secret value, uses it to connect to database
```

### Flow 2: Secret Rotation (Zero-Downtime)
```
1. Rotation trigger: scheduled (every 30 days) or manual
   ↓
2. Rotation Service:
   a. Generate new DB password: "new-password-xyz"
   b. Update the database user's password (via DB admin API)
   c. Store new password as a new VERSION of the secret:
      PUT /vaults/my-vault/secrets/DB_PASSWORD → creates version v4
   d. Old version v3 still exists (apps using it still work)
   ↓
3. Applications:
   a. On next secret fetch, they get v4 (latest)
   b. Apps using cached v3 continue working (DB accepts both during rotation)
   c. After TTL expires, apps refresh and get v4
   ↓
4. After 24 hours: disable old version v3
   (any app still using v3 will fail → forces upgrade)
   ↓
5. Audit: rotation event logged with old_version, new_version
```

### Flow 3: Envelope Encryption (How Secrets Are Stored)
```
THE ENCRYPTION HIERARCHY:

Level 1: HSM Master Key (never leaves HSM)
  ↓ wraps
Level 2: Key Encryption Key (KEK) — per vault, stored encrypted by master key
  ↓ wraps  
Level 3: Data Encryption Key (DEK) — per secret, stored encrypted by KEK
  ↓ encrypts
Level 4: Secret Value — stored encrypted by DEK

STORING A SECRET:
  1. Generate random DEK (AES-256 key)
  2. Encrypt secret value with DEK → ciphertext
  3. Encrypt DEK with vault's KEK → encrypted DEK
  4. Store: {encrypted_value, encrypted_dek, kek_id}
  5. DEK and plaintext are NEVER stored — only encrypted forms

READING A SECRET:
  1. Fetch: {encrypted_value, encrypted_dek, kek_id}
  2. Decrypt encrypted_dek using KEK → get DEK
  3. Decrypt encrypted_value using DEK → get plaintext
  4. Return plaintext to caller
  5. DEK is discarded from memory after use

WHY ENVELOPE ENCRYPTION?
  - Re-encrypting 100M secrets when rotating the master key would take days
  - Instead: rotate KEK → only re-encrypt the DEKs (tiny, fast)
  - HSM master key rotation: only re-wraps the KEKs (even faster)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                      │
│  Applications (via SDK), Users (via CLI/Portal), CI/CD Pipelines     │
│  All authenticate via Azure AD → get JWT → call Vault API            │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTPS (TLS 1.3)
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY                                       │
│  • JWT validation (Azure AD)                                         │
│  • Rate limiting (per vault: 10K req/sec)                            │
│  • TLS termination                                                   │
│  • Request routing                                                   │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    VAULT SERVICE (stateless, 50+ pods)                │
│                                                                      │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────────────┐  │
│  │ AuthZ Engine   │ │ Secret Ops     │ │ Key Ops                │  │
│  │                │ │                │ │                        │  │
│  │ Check access   │ │ CRUD secrets   │ │ Encrypt, decrypt,     │  │
│  │ policies for   │ │ Version mgmt   │ │ sign, verify           │  │
│  │ caller + action│ │ Envelope enc   │ │ Key never leaves       │  │
│  │                │ │ /decryption    │ │ service/HSM            │  │
│  └────────────────┘ └────────────────┘ └────────────────────────┘  │
│                                                                      │
│  ┌────────────────┐ ┌────────────────────────────────────────────┐  │
│  │ Cert Ops       │ │ Audit Logger                                │  │
│  │                │ │                                              │  │
│  │ Store, renew,  │ │ Every operation → immutable audit log        │  │
│  │ expiry alerts  │ │ Async write to append-only log store         │  │
│  └────────────────┘ └────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ ENCRYPTED      │ │ ACCESS POLICY  │ │ HSM            │
│ STORAGE        │ │ STORE          │ │ (Hardware      │
│                │ │                │ │  Security      │
│ PostgreSQL     │ │ PostgreSQL     │ │  Module)       │
│ (encrypted at  │ │ + Redis cache  │ │                │
│  rest with TDE)│ │                │ │ Master keys    │
│                │ │ Policies cached│ │ KEK wrapping   │
│ Secrets,       │ │ in Redis       │ │ Key operations │
│ encrypted DEKs,│ │ (5 min TTL)    │ │ FIPS 140-2 L3 │
│ key metadata   │ │                │ │                │
└────────────────┘ └────────────────┘ └────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    AUDIT LOG STORE                                    │
│                                                                      │
│  Append-only, immutable log (Azure Monitor / Splunk / S3 + Athena)  │
│  Every read, write, delete, policy change logged                     │
│  Retention: 1+ years (compliance)                                    │
│  Tamper-proof (hash chain or write-once storage)                     │
└─────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CROSS-REGION REPLICATION                           │
│                                                                      │
│  Primary: East US → Async replication → Secondary: West US           │
│  Encrypted secrets replicated (still encrypted, no plaintext in      │
│  transit). Failover: DNS switch to secondary region.                 │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology |
|-----------|---------|-----------|
| **API Gateway** | JWT validation, rate limiting, TLS | Azure Front Door / NGINX |
| **Vault Service** | Business logic: CRUD, encryption, access checks | Java/Go, 50+ stateless pods |
| **AuthZ Engine** | Evaluate access policies per request | In-process, policy cache in Redis |
| **HSM** | Master key storage, KEK wrapping, key operations | Azure Managed HSM (FIPS 140-2 L3) |
| **Encrypted Storage** | Persist encrypted secrets + metadata | PostgreSQL with TDE |
| **Policy Cache** | Cache access policies for fast AuthZ | Redis (5-min TTL) |
| **Audit Log Store** | Immutable, append-only audit trail | Azure Monitor / S3 + Athena |
| **Replication** | Cross-region DR for encrypted data | Async PostgreSQL replication |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Envelope Encryption — How Secrets Are Actually Stored

**The Problem**: If we encrypt all secrets with one master key, rotating that key means re-encrypting 100M secrets. If the master key is compromised, ALL secrets are exposed at once.

**Solution: Three-Layer Envelope Encryption**

```java
public class EnvelopeEncryptionService {

    private final HSMClient hsm;  // Hardware Security Module

    /**
     * ENCRYPTION HIERARCHY:
     * 
     * HSM Master Key (MK) — lives ONLY inside HSM hardware
     *   └─ wraps → Key Encryption Key (KEK) — one per vault
     *       └─ wraps → Data Encryption Key (DEK) — one per secret version
     *           └─ encrypts → Secret Value (plaintext)
     * 
     * WHY THREE LAYERS?
     * 1. Master Key rotation: only re-wraps KEKs (fast, 2M vaults × 1 KEK each)
     * 2. KEK rotation per vault: only re-wraps DEKs (fast, avg 50 secrets per vault)
     * 3. DEK per secret: compromise of one DEK exposes only ONE secret
     * 4. HSM ensures master key NEVER exists in software memory
     */

    public EncryptedSecret encryptSecret(String vaultId, String plaintext) {
        // Step 1: Generate a fresh DEK (AES-256, random)
        byte[] dek = generateRandom256BitKey();

        // Step 2: Encrypt the secret value with DEK
        byte[] iv = generateRandom128BitIV();
        byte[] ciphertext = aes256GCMEncrypt(plaintext.getBytes(), dek, iv);
        // GCM mode provides both confidentiality AND integrity (authenticated encryption)

        // Step 3: Get the vault's KEK (already wrapped by master key, stored in DB)
        WrappedKEK wrappedKek = keyStore.getKEK(vaultId);
        // Unwrap KEK using HSM (master key operation — happens inside HSM)
        byte[] kek = hsm.unwrapKey(wrappedKek.getEncryptedKek(), wrappedKek.getMasterKeyId());

        // Step 4: Wrap (encrypt) the DEK with the KEK
        byte[] wrappedDek = aes256KeyWrap(dek, kek);

        // Step 5: WIPE plaintext DEK and KEK from memory immediately
        Arrays.fill(dek, (byte) 0);
        Arrays.fill(kek, (byte) 0);
        // dek and kek are now zeroed — can't be recovered from memory dump

        // Step 6: Store only encrypted forms
        return new EncryptedSecret(ciphertext, iv, wrappedDek, wrappedKek.getKekId());
        // plaintext and DEK are GONE — only encrypted data persists
    }

    public String decryptSecret(String vaultId, EncryptedSecret encrypted) {
        // Step 1: Get vault's KEK
        WrappedKEK wrappedKek = keyStore.getKEK(vaultId);
        byte[] kek = hsm.unwrapKey(wrappedKek.getEncryptedKek(), wrappedKek.getMasterKeyId());

        // Step 2: Unwrap DEK
        byte[] dek = aes256KeyUnwrap(encrypted.getWrappedDek(), kek);

        // Step 3: Decrypt secret value
        byte[] plaintext = aes256GCMDecrypt(encrypted.getCiphertext(), dek, encrypted.getIv());

        // Step 4: Wipe keys from memory
        Arrays.fill(dek, (byte) 0);
        Arrays.fill(kek, (byte) 0);

        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
```

**Key rotation at each level**:
```
MASTER KEY ROTATION (annual, triggered by HSM policy):
  1. HSM generates new master key MK2
  2. For each vault's KEK: unwrap with MK1, re-wrap with MK2
  3. 2M vaults × 1 KEK each = 2M HSM operations (~1 hour)
  4. No secrets are touched — only KEKs re-wrapped

KEK ROTATION (per vault, on demand):
  1. Generate new KEK2 for the vault
  2. For each secret in vault: unwrap DEK with KEK1, re-wrap with KEK2
  3. 50 secrets × 1 re-wrap each = 50 operations (~1 second)
  4. Secret values are NOT re-encrypted — only DEKs re-wrapped

DEK is never rotated — it's unique per secret version.
New version = new DEK. Old version keeps old DEK.
```

---

### Deep Dive 2: Access Control — RBAC + Vault Policies

**The Problem**: Thousands of applications and users need access to different secrets. "App A can read DB_PASSWORD but not API_KEY. User B can manage certificates but not decrypt keys."

```java
public class AuthorizationEngine {

    private final LoadingCache<String, List<AccessPolicy>> policyCache;

    /**
     * Authorization check on EVERY request.
     * Must be fast (< 1ms) — cached in Redis + local memory.
     * 
     * Access model:
     *   Vault → Access Policies → each grants {principal, permissions}
     * 
     * Example policies for vault "my-vault":
     *   { principal: "app-backend-456", secrets: [GET, LIST], keys: [], certs: [] }
     *   { principal: "app-ci-789", secrets: [GET, LIST, SET], keys: [], certs: [GET, LIST] }
     *   { principal: "admin-user@co.com", secrets: [ALL], keys: [ALL], certs: [ALL] }
     */

    public AuthZResult authorize(AuthZRequest request) {
        // 1. Get caller identity from JWT
        String callerId = request.getCallerId();  // "app-backend-456"
        String vaultId = request.getVaultId();
        String resourceType = request.getResourceType(); // SECRET, KEY, CERTIFICATE
        String permission = request.getPermission();      // GET, SET, DELETE, ENCRYPT, etc.

        // 2. Look up policies (cached — 5 min TTL in Redis, 30s in local memory)
        List<AccessPolicy> policies = policyCache.get(vaultId);

        // 3. Find matching policy for this caller
        for (AccessPolicy policy : policies) {
            if (policy.getPrincipalId().equals(callerId) ||
                isGroupMember(callerId, policy.getPrincipalId())) {
                
                Set<String> allowedPermissions = policy.getPermissions(resourceType);
                if (allowedPermissions.contains(permission) || allowedPermissions.contains("ALL")) {
                    return AuthZResult.ALLOWED;
                }
            }
        }

        // 4. No matching policy → DENIED (default deny)
        auditLogger.logDenied(request);
        return AuthZResult.DENIED;
    }
}
```

**Principle of least privilege**:
```
GOOD access policy (fine-grained):
  App backend: secrets.GET on "DB_PASSWORD", "REDIS_URL" only
  CI pipeline: secrets.GET, secrets.SET on all secrets in vault
  Security team: all permissions (admin)

BAD access policy (too broad):
  App backend: ALL permissions on ALL secrets
  → If app is compromised, attacker has full access to everything

BEST PRACTICE:
  1. One vault per environment (prod vault, staging vault, dev vault)
  2. Separate vaults for different teams/services
  3. Service principals get only the permissions they need
  4. No human should have secret GET in production (automation only)
  5. Regular access reviews (quarterly audit of who has access to what)
```

---

### Deep Dive 3: Audit Logging — Immutable Compliance Trail

**The Problem**: For SOC 2 / HIPAA compliance, every secret access must be logged and the logs must be tamper-proof. An attacker who compromises the vault shouldn't be able to erase evidence.

```java
public class AuditLogger {

    private final KafkaProducer<String, AuditLogEntry> kafka;
    private final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    private volatile String previousHash = "GENESIS"; // Hash chain anchor

    /**
     * Log every vault operation. Called AFTER the operation completes.
     * Async (via Kafka) so it doesn't block the read path.
     * Hash chain: each entry includes hash of previous entry → tamper detection.
     */
    public void log(String vaultId, String resourceType, String resourceName,
                     String operation, String callerId, String callerIp,
                     String result, int httpStatus) {

        AuditLogEntry entry = AuditLogEntry.builder()
            .logId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .vaultId(vaultId)
            .resourceType(resourceType)      // SECRET, KEY, CERTIFICATE
            .resourceName(resourceName)      // "DB_PASSWORD"
            .operation(operation)            // "SecretGet", "KeyDecrypt"
            .callerIdentity(callerId)        // "app-id-123"
            .callerIpAddress(callerIp)
            .result(result)                  // "Success", "Forbidden"
            .httpStatusCode(httpStatus)
            .previousHash(previousHash)      // Hash chain link
            .build();

        // Compute hash of this entry (includes previousHash → chain)
        String entryHash = sha256Hex(entry.toString());
        entry.setCurrentHash(entryHash);
        this.previousHash = entryHash; // Next entry will reference this hash

        // Async send to Kafka → consumed by log store writer
        kafka.send(new ProducerRecord<>("vault-audit-logs", vaultId, entry));
    }

    /**
     * Verify integrity of audit log chain.
     * If any entry was modified or deleted, the hash chain breaks.
     */
    public boolean verifyChainIntegrity(List<AuditLogEntry> entries) {
        String expectedPrevHash = "GENESIS";
        for (AuditLogEntry entry : entries) {
            // Check previous hash links correctly
            if (!entry.getPreviousHash().equals(expectedPrevHash)) {
                log.error("AUDIT CHAIN BROKEN at entry {}: expected prev={}, got={}",
                    entry.getLogId(), expectedPrevHash, entry.getPreviousHash());
                return false; // TAMPERED!
            }
            // Verify current hash is correct
            String recomputed = sha256Hex(entry.toStringWithoutHash());
            if (!entry.getCurrentHash().equals(recomputed)) {
                log.error("AUDIT ENTRY MODIFIED: {} hash mismatch", entry.getLogId());
                return false; // TAMPERED!
            }
            expectedPrevHash = entry.getCurrentHash();
        }
        return true; // Chain intact ✓
    }
}
```

```
WHAT GETS LOGGED (every single operation):
  ✅ SecretGet — who read which secret, when, from which IP
  ✅ SecretSet — who created/updated which secret
  ✅ SecretDelete — who deleted (even soft-delete)
  ✅ KeyEncrypt/Decrypt — who used which key for which operation
  ✅ AccessPolicyChange — who modified permissions
  ✅ AuthZDenied — who tried to access and was rejected (security signal!)

IMMUTABILITY GUARANTEES:
  1. Write-once storage (S3 with Object Lock / Azure Immutable Blob)
  2. Hash chain: each log entry includes hash of previous entry
     → Deleting or modifying any entry breaks the chain (detectable)
  3. Separate storage from vault data (compromise of vault DB ≠ compromise of logs)
  4. Audit logs written asynchronously (don't block the secret read path)
     but guaranteed delivery (Kafka → log store, at-least-once)

RETENTION:
  - 1 year minimum (SOC 2 requirement)
  - 7 years for financial services (SEC/FINRA)
  - Compressed: ~3 PB/year at Meta-scale (cold storage: ~$36K/year on S3 Glacier)
```

---

### Deep Dive 4: Secret Rotation — Zero-Downtime Password Change

**The Problem**: DB passwords should be rotated every 30-90 days. But changing the password means every app using it needs the new one. How to rotate without downtime?

```java
public class SecretRotationService {

    private final VaultService vaultService;
    private final DatabaseAdminClient dbAdmin;

    /**
     * Rotate a database password with zero downtime.
     * Uses dual-password pattern: DB accepts BOTH old and new during transition.
     */
    @Scheduled(cron = "0 0 2 1 * *") // 2 AM on 1st of every month
    public void rotateScheduledSecrets() {
        List<Secret> rotatable = secretRepo.findByRotationEnabled(true);
        for (Secret secret : rotatable) {
            if (isRotationDue(secret)) {
                rotateSecret(secret.getVaultId(), secret.getName());
            }
        }
    }

    public RotationResult rotateSecret(String vaultId, String secretName) {
        // Step 1: Get current (old) password
        String oldPassword = vaultService.getSecret(vaultId, secretName);
        String oldVersion = vaultService.getCurrentVersion(vaultId, secretName);

        // Step 2: Generate new password (cryptographically random)
        String newPassword = generateSecurePassword(32); // 32 chars, mixed case + digits + symbols

        // Step 3: Set new password in the database FIRST
        // DB now accepts BOTH old and new passwords
        dbAdmin.setPassword(secretName, newPassword);
        // For DBs that don't support dual-password:
        //   Use two DB users (app_user_a, app_user_b), alternate between them

        // Step 4: Store new password as new version in vault
        String newVersion = vaultService.setSecret(vaultId, secretName, newPassword);
        // Now: latest version = new password. Apps will pick it up on next refresh.

        // Step 5: Schedule old version disable (after apps have transitioned)
        scheduler.schedule(() -> {
            vaultService.disableVersion(vaultId, secretName, oldVersion);
            log.info("Disabled old version {} of secret {}", oldVersion, secretName);
        }, Duration.ofHours(24)); // Give apps 24 hours to refresh

        // Step 6: Schedule old password removal from DB
        scheduler.schedule(() -> {
            dbAdmin.removeOldPassword(secretName, oldPassword);
            log.info("Removed old password for {}", secretName);
        }, Duration.ofHours(25));

        auditLogger.log(vaultId, "SECRET", secretName, "SecretRotate",
            "rotation-service", "internal", "Success", 200);

        return new RotationResult(secretName, oldVersion, newVersion, Instant.now());
    }

    private String generateSecurePassword(int length) {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
```

```
THE DUAL-PASSWORD ROTATION PATTERN:

Step 1: Generate new password "new_pass_v4"
Step 2: Set BOTH passwords as valid in the database:
   ALTER USER app_user SET PASSWORD 'new_pass_v4';
   -- DB now accepts BOTH old (v3) and new (v4)
   -- (Some DBs support this natively; others need two users)
Step 3: Store new password as secret version v4 in vault
Step 4: Apps gradually pick up v4 (on next secret refresh)
Step 5: After TTL (e.g., 24 hours), disable v3 in vault
Step 6: Remove old password from database

TIMELINE:
  T=0:    New password generated, set in DB, stored as v4
  T=0-24h: Both v3 and v4 work. Apps transition gradually.
  T=24h:  v3 disabled in vault. Apps that haven't refreshed fail.
  T=25h:  Old password removed from DB.

ZERO DOWNTIME because:
  - DB accepts both passwords during transition window
  - Apps fetch latest version on their normal refresh cycle
  - No coordinated restart needed
```

---

### Deep Dive 5: High Availability — Multi-Region Replication

**The Problem**: If the vault is down, applications can't start (they can't get secrets). 99.99% availability = 52 minutes downtime/year max.

```java
public class VaultClientWithCaching {

    private final VaultHttpClient vaultClient;
    private final LoadingCache<String, CachedSecret> secretCache;

    /**
     * Client-side secret caching for availability.
     * If vault is briefly down, app uses cached secret.
     * Cache is in-memory, encrypted, with TTL-based refresh.
     */
    public VaultClientWithCaching(String vaultUrl) {
        this.vaultClient = new VaultHttpClient(vaultUrl);
        this.secretCache = CacheBuilder.newBuilder()
            .maximumSize(1000)                     // Max 1000 secrets cached
            .expireAfterWrite(Duration.ofMinutes(15)) // Refresh every 15 min
            .build(new CacheLoader<>() {
                @Override
                public CachedSecret load(String secretName) {
                    return fetchAndCache(secretName);
                }
            });
    }

    public String getSecret(String secretName) {
        try {
            CachedSecret cached = secretCache.get(secretName);

            // If cache entry is stale (> TTL), try to refresh in background
            if (cached.isStale()) {
                refreshAsync(secretName);
            }

            return cached.getValue();

        } catch (Exception e) {
            // Vault unavailable — try to use stale cached value
            CachedSecret stale = secretCache.getIfPresent(secretName);
            if (stale != null) {
                log.warn("Vault unavailable, using stale cached secret for {}", secretName);
                metrics.counter("vault.cache.stale_hit").increment();
                return stale.getValue(); // Better stale than down!
            }
            throw new VaultUnavailableException("Vault unreachable and no cached value", e);
        }
    }

    private CachedSecret fetchAndCache(String secretName) {
        String value = vaultClient.getSecret(secretName);
        return new CachedSecret(value, Instant.now(), Duration.ofMinutes(15));
    }

    private void refreshAsync(String secretName) {
        CompletableFuture.runAsync(() -> {
            try {
                CachedSecret fresh = fetchAndCache(secretName);
                secretCache.put(secretName, fresh);
            } catch (Exception e) {
                log.debug("Background refresh failed for {}, keeping cached value", secretName);
            }
        });
    }
}
```

```
AVAILABILITY STRATEGY:

Layer 1: Multi-AZ within a region
  Primary DB in AZ-1, synchronous replica in AZ-2
  HSM in AZ-1, HSM in AZ-2 (independent hardware)
  Vault service pods spread across AZ-1, AZ-2, AZ-3
  → Survives single AZ failure

Layer 2: Cross-region async replication
  Primary: East US → async replication → Secondary: West US
  Encrypted data replicated (no plaintext in transit)
  HSM keys replicated via HSM-to-HSM secure channel
  RPO: < 5 seconds (async replication lag)
  RTO: < 5 minutes (DNS failover)

Layer 3: Client-side caching
  Applications SHOULD cache secrets locally (in-memory, encrypted)
  Cache TTL: 5-30 minutes
  If vault is briefly unavailable, app uses cached secret
  → Applications survive short vault outages

FAILOVER PROCESS:
  1. Primary region health check fails (3 consecutive failures)
  2. Automated DNS switch: vault.azure.net → secondary region
  3. Secondary region promoted to primary
  4. RPO: up to 5 seconds of writes may be lost (async replication)
  5. Operations team investigates and repairs primary
  6. When primary is back: re-sync from (now-primary) secondary
```

---

### Deep Dive 6: Soft Delete & Purge Protection

**The Problem**: An accidental `DELETE /secrets/DB_PASSWORD` could lock out an entire application. A malicious insider could permanently destroy secrets.

```
SOFT DELETE (enabled by default):
  DELETE /secrets/DB_PASSWORD
  → Secret is NOT permanently deleted
  → Moved to "deleted" state with 90-day recovery window
  → Can be recovered: POST /secrets/DB_PASSWORD/recover
  → After 90 days: automatically purged (permanently deleted)

PURGE PROTECTION (opt-in, recommended for production):
  When enabled: even vault ADMINS cannot permanently delete secrets
  The secret MUST go through the 90-day soft-delete window
  → Protects against:
    - Accidental deletion by admin
    - Malicious insider with admin access
    - Compromised admin credentials
  → To permanently delete: must wait 90 days (no override)

STATE MACHINE:
  ACTIVE → (DELETE) → SOFT_DELETED → (90 days) → PURGED
                         ↓
                      (RECOVER) → ACTIVE
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Alternative | Why |
|----------|--------|-------------|-----|
| **Encryption** | Three-layer envelope (HSM → KEK → DEK → data) | Single master key for all | Fast key rotation; blast radius limited per-secret |
| **HSM** | Hardware Security Module for master keys | Software-only encryption | FIPS 140-2 compliance; master key never in software memory |
| **Access model** | Per-vault access policies (RBAC) | Per-secret ACLs | Simpler to manage at scale; per-secret is too granular for most |
| **Audit logging** | Async, append-only, immutable | Synchronous logging | Don't block reads for audit; write-once storage for tamper-proofing |
| **Secret rotation** | Dual-password pattern with versioning | Stop-the-world rotation | Zero downtime; apps transition gradually on their refresh cycle |
| **Availability** | Multi-AZ + cross-region + client caching | Single region only | 99.99% SLA; apps cache secrets for short vault outages |
| **Soft delete** | 90-day recovery with purge protection | Immediate permanent delete | Protects against accidental and malicious deletion |
| **Key ops** | Server-side only (key never leaves vault) | Export raw key material | Prevents key exposure; even if app is compromised, key stays in vault |

## Interview Talking Points

1. **"Three-layer envelope encryption: HSM → KEK → DEK → secret"** — Master key lives only in HSM hardware. KEK per vault. DEK per secret version. Rotation at any layer doesn't require re-encrypting all secrets. Blast radius: one compromised DEK = one secret.

2. **"Key material NEVER leaves the vault"** — For crypto keys, the vault performs operations (encrypt, sign) on behalf of the caller. The raw key is never returned. Even if the app is compromised, the key stays safe in the HSM.

3. **"Audit every access — immutable, tamper-proof logs"** — Write-once storage + hash chain. Separate storage from vault data. Attacker can't erase evidence. SOC 2 / HIPAA compliance built-in.

4. **"Dual-password rotation for zero downtime"** — New password set in both DB and vault. Both old and new work during transition. Apps refresh on their cycle. No coordinated restart needed.

5. **"Soft delete with purge protection"** — Accidental DELETE = recoverable within 90 days. Even admins can't permanently destroy secrets immediately. Protects against malicious insiders.

6. **"Client-side caching for availability"** — Apps cache secrets in-memory (encrypted). If vault is briefly down, apps use cached values. TTL-based refresh. Applications survive short outages without losing access.

7. **"Default deny, least privilege"** — No access unless explicitly granted. One vault per environment. Apps get only GET permission on specific secrets they need. No human GET access in production.

8. **"Wipe keys from memory immediately after use"** — `Arrays.fill(dek, (byte) 0)` after decryption. Prevents memory dump attacks. DEK exists in memory only for the microseconds of the decrypt operation.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Authentication System** | Same JWT/OAuth patterns | Auth system issues tokens; vault consumes them for access control |
| **Distributed Key-Value Store** | Same CRUD storage pattern | KV store is general-purpose; vault adds encryption, HSM, audit, RBAC |
| **Certificate Authority (PKI)** | Vault stores certs, but doesn't issue them | Full CA is much more complex (chain of trust, revocation, OCSP) |
| **Password Manager (1Password)** | Same secret storage concept | Password manager is user-facing; vault is API/app-facing with RBAC |
| **Encryption Service** | Same envelope encryption pattern | Encryption service just encrypts; vault adds storage, versioning, rotation |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Source**: [Exponent](https://www.tryexponent.com/questions/5437/system-design-secure-key-vault-service)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 6 topics (choose 2-3 based on interviewer interest)
