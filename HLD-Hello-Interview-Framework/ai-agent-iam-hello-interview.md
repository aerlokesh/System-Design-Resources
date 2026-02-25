# Design an IAM System for AI Agents - Hello Interview Framework

> **Question**: Design an IAM system for a cloud provider that allows customers and enterprises to create and manage multiple AI agents with proper access controls and permissions. Customers create autonomous/automated agents and control their access across services, data, and environments.
>
> **Asked at**: Google
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
1. **Agent Identity CRUD**: Create, list, update, disable, delete AI agent identities. Each agent has a unique identity within a tenant/organization.
2. **Policy Model (RBAC + ABAC)**: Define policies with Allow/Deny effects on (principal, action, resource) tuples. Support role-based (RBAC) and attribute-based (ABAC) conditions (e.g., "allow if agent.environment == 'production' AND time.hour BETWEEN 9 AND 17").
3. **Short-Lived Credentials**: Agents authenticate with API keys or certificates, then receive short-lived tokens (JWT, 15-min expiry). No long-lived secrets in agent code.
4. **Authorization Decisions**: Given an agent, action, and resource — evaluate all applicable policies and return Allow/Deny in < 10ms. This is the hot path (called on every API request).
5. **Multi-Tenancy**: Strict tenant isolation. Tenant A's agents cannot access Tenant B's resources. Shared infrastructure, isolated data.
6. **Audit Logging**: Every authorization decision, credential issuance, and policy change is logged immutably for compliance and forensic analysis.

#### Nice to Have (P1)
- Permission boundaries (max permissions an agent can ever have, regardless of attached policies).
- Session policies (temporary scope reduction for a single session).
- Federated identity (OIDC/SAML trust with external identity providers).
- Cross-account/cross-tenant agent delegation (Agent in Tenant A accesses Tenant B's resources via trust policy).
- Policy simulation ("what if" — test a policy change before applying).
- Anomaly detection (alert if agent accesses unusual resources).

#### Below the Line (Out of Scope)
- The cloud services being protected (compute, storage, ML pipelines).
- Network-level security (VPCs, firewalls, mTLS between services).
- User (human) IAM — we focus on agent (machine) identities.
- Billing and quota management.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Auth Decision Latency** | < 10ms P99 | Called on every API request; must not bottleneck services |
| **Auth Decision Throughput** | 1M decisions/sec | Cloud-scale: millions of agents making API calls |
| **Credential Issuance Latency** | < 100ms | Agent startup/rotation should be fast |
| **Availability** | 99.999% for auth decisions | If IAM is down, all services are down |
| **Consistency** | Strong for policy writes, eventual for reads (< 5s propagation) | Policy changes must be durable; auth decisions can use slightly stale cache |
| **Durability** | Zero audit log loss | Compliance requirement; tamper-proof |
| **Scale** | 100M agents, 10M policies, 10K tenants | Enterprise cloud scale |
| **Security** | Zero cross-tenant data leaks | IAM is the trust foundation |

### Capacity Estimation

```
Agents: 100M total, 10M active daily
Tenants: 10K organizations
Policies: 10M total (avg 1000 per tenant)
Roles: 500K total (avg 50 per tenant)

Authorization decisions:
  1M decisions/sec (peak)
  86.4B decisions/day
  Each decision: evaluate ~10 policies × ~3 conditions = 30 condition evaluations
  Decision payload: ~500 bytes (request) + ~100 bytes (response)

Credential issuance:
  10M tokens issued/day (15-min lifetime, agents refresh ~96 times/day × 100K active agents)
  ~115 issuances/sec sustained, 1K peak

Audit logs:
  1M auth decisions/sec + 115 credential events/sec + ~10 policy changes/sec
  ≈ 1M audit events/sec
  Event size: ~500 bytes
  Storage: 1M × 500 bytes × 86400 sec = ~43 TB/day

Storage:
  Policy store: 10M policies × 2 KB = ~20 GB
  Agent metadata: 100M agents × 500 bytes = ~50 GB
  Audit logs: ~43 TB/day (tiered: 30 days hot → cold archive)
  Policy cache (Redis): ~20 GB (full policy set replicated)
```

---

## 2️⃣ Core Entities

### Entity 1: Agent Identity
```java
public class Agent {
    private final String agentId;           // UUID
    private final String tenantId;          // Organization scope
    private final String name;              // "data-pipeline-agent", "ml-inference-bot"
    private final AgentType type;           // SERVICE, AUTONOMOUS, SCHEDULED
    private final AgentStatus status;       // ACTIVE, DISABLED, SUSPENDED
    private final String description;
    private final Map<String, String> tags; // Custom metadata (environment, team, etc.)
    private final List<String> roleArns;    // Attached roles
    private final List<String> policyArns;  // Directly attached policies
    private final String permissionBoundary;// Max permissions ARN (guardrail)
    private final CredentialConfig credentialConfig; // Auth method
    private final Instant createdAt;
    private final Instant lastAuthenticatedAt;
}

public enum AgentType { SERVICE, AUTONOMOUS, SCHEDULED, INTERACTIVE }
public enum AgentStatus { ACTIVE, DISABLED, SUSPENDED, PENDING_DELETION }
```

### Entity 2: Policy
```java
public class Policy {
    private final String policyId;          // ARN: arn:iam::tenant_123:policy/ReadOnlyS3
    private final String tenantId;
    private final String name;
    private final String description;
    private final int version;              // Immutable versions; new version on update
    private final List<PolicyStatement> statements;
    private final PolicyType type;          // MANAGED, INLINE, BOUNDARY
    private final Instant createdAt;
}

public class PolicyStatement {
    private final String sid;               // Statement ID (human-readable)
    private final Effect effect;            // ALLOW or DENY
    private final List<String> principals;  // Agent ARNs or "*"
    private final List<String> actions;     // "s3:GetObject", "ml:InvokeModel"
    private final List<String> resources;   // "arn:s3::tenant_123:bucket/data/*"
    private final List<Condition> conditions; // ABAC conditions
}

public enum Effect { ALLOW, DENY }

public class Condition {
    private final String operator;          // "StringEquals", "NumericGreaterThan", "DateLessThan"
    private final String key;               // "agent.environment", "request.time", "resource.tag/sensitivity"
    private final List<String> values;      // ["production"], ["2025-01-01T00:00:00Z"]
}
```

### Entity 3: Role (Assumable Identity)
```java
public class Role {
    private final String roleId;            // ARN
    private final String tenantId;
    private final String name;
    private final String description;
    private final List<String> policyArns;  // Policies attached to this role
    private final TrustPolicy trustPolicy;  // WHO can assume this role
    private final String permissionBoundary;
    private final int maxSessionDurationSec; // Default: 3600 (1 hour)
    private final Instant createdAt;
}

public class TrustPolicy {
    private final List<String> trustedPrincipals; // Agent ARNs or tenant IDs allowed to assume
    private final List<Condition> conditions;      // Additional conditions for assumption
}
```

### Entity 4: Credential / Token
```java
public class AgentCredential {
    private final String credentialId;
    private final String agentId;
    private final CredentialType type;      // API_KEY, CERTIFICATE, OIDC_TOKEN
    private final String secretHash;        // Hashed secret (never stored plain)
    private final Instant createdAt;
    private final Instant expiresAt;
    private final boolean active;
}

public class SessionToken {
    private final String tokenId;           // JTI (JWT ID)
    private final String agentId;
    private final String tenantId;
    private final String assumedRoleArn;    // Role assumed (if any)
    private final List<String> effectivePolicies; // Resolved policy ARNs
    private final Instant issuedAt;
    private final Instant expiresAt;        // 15 minutes from issuance
    private final Map<String, String> sessionContext; // Extra attributes
}
```

### Entity 5: Tenant / Organization
```java
public class Tenant {
    private final String tenantId;
    private final String organizationName;
    private final TenantTier tier;          // FREE, PRO, ENTERPRISE
    private final TenantQuota quota;        // Max agents, policies, roles
    private final List<String> trustedIdentityProviders; // OIDC/SAML endpoints
    private final Instant createdAt;
}

public record TenantQuota(int maxAgents, int maxPolicies, int maxRoles, int maxRequestsPerSec) {}
```

### Entity 6: Audit Log Entry
```java
public class AuditLogEntry {
    private final String eventId;           // UUID
    private final String tenantId;
    private final Instant timestamp;
    private final AuditEventType eventType; // AUTH_DECISION, CREDENTIAL_ISSUED, POLICY_CHANGED, AGENT_CREATED
    private final String principalId;       // Agent or admin who triggered
    private final String action;            // "s3:GetObject", "iam:CreatePolicy"
    private final String resource;          // Target resource ARN
    private final String decision;          // ALLOW, DENY, or N/A
    private final String sourceIp;
    private final String userAgent;
    private final Map<String, String> context; // Additional context
    private final String requestId;         // For tracing
}
```

---

## 3️⃣ API Design

### 1. Create Agent
```
POST /api/v1/agents
Headers: X-Tenant-Id: tenant_123, Authorization: Bearer {admin_token}

Request:
{
  "name": "data-pipeline-agent",
  "type": "SERVICE",
  "description": "ETL pipeline agent for daily data processing",
  "tags": { "environment": "production", "team": "data-eng" },
  "credential_type": "API_KEY",
  "permission_boundary": "arn:iam::tenant_123:policy/MaxDataAccess"
}

Response (201):
{
  "agent_id": "agent_abc123",
  "arn": "arn:iam::tenant_123:agent/data-pipeline-agent",
  "api_key": "sk_live_abc123...",
  "api_secret": "ss_live_xyz789...",
  "status": "ACTIVE",
  "created_at": "2025-01-10T10:00:00Z"
}
```

### 2. Create/Attach Policy
```
POST /api/v1/policies
Request:
{
  "name": "S3ReadOnlyDataBucket",
  "statements": [
    {
      "sid": "AllowS3Read",
      "effect": "ALLOW",
      "actions": ["s3:GetObject", "s3:ListBucket"],
      "resources": ["arn:s3::tenant_123:bucket/data-lake/*"],
      "conditions": [
        { "operator": "StringEquals", "key": "agent.tags.environment", "values": ["production"] }
      ]
    }
  ]
}

--- Attach to agent: ---
PUT /api/v1/agents/{agent_id}/policies/{policy_arn}
Response (200): { "attached": true }
```

### 3. Get Temporary Credentials (Assume Role / Get Session Token)
```
POST /api/v1/credentials/session

Request:
{
  "agent_id": "agent_abc123",
  "api_key": "sk_live_abc123...",
  "api_secret": "ss_live_xyz789...",
  "role_arn": "arn:iam::tenant_123:role/DataPipelineRole",
  "session_duration_sec": 900
}

Response (200):
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_at": "2025-01-10T10:15:00Z",
  "expires_in_sec": 900,
  "assumed_role": "arn:iam::tenant_123:role/DataPipelineRole"
}
```

### 4. Authorize (Policy Evaluation — Hot Path)
```
POST /api/v1/authorize

Request:
{
  "principal": "arn:iam::tenant_123:agent/data-pipeline-agent",
  "action": "s3:GetObject",
  "resource": "arn:s3::tenant_123:bucket/data-lake/2025/01/file.parquet",
  "context": {
    "agent.tags.environment": "production",
    "request.time": "2025-01-10T10:05:00Z",
    "source_ip": "10.0.1.50"
  }
}

Response (200): { "decision": "ALLOW", "matched_statements": ["AllowS3Read"], "evaluation_time_ms": 2 }
Response (200): { "decision": "DENY", "reason": "EXPLICIT_DENY", "matched_statements": ["DenyProdWrites"] }
Response (200): { "decision": "DENY", "reason": "NO_MATCHING_ALLOW" }
```

> **Evaluation order**: Explicit DENY → Permission Boundary → Session Policy → ALLOW. Default: DENY.

### 5. Query Audit Logs
```
GET /api/v1/audit-logs?agent_id=agent_abc123&event_type=AUTH_DECISION&decision=DENY&start=2025-01-10T00:00:00Z&end=2025-01-10T23:59:59Z&limit=100

Response (200):
{
  "events": [
    {
      "event_id": "evt_001",
      "timestamp": "2025-01-10T10:05:00Z",
      "event_type": "AUTH_DECISION",
      "principal": "agent_abc123",
      "action": "s3:PutObject",
      "resource": "arn:s3::tenant_123:bucket/data-lake/...",
      "decision": "DENY",
      "reason": "EXPLICIT_DENY: DenyProdWrites"
    }
  ],
  "pagination": { "next_token": "..." }
}
```

### 6. Create/Manage Roles
```
POST /api/v1/roles
Request:
{
  "name": "DataPipelineRole",
  "trust_policy": {
    "trusted_principals": ["arn:iam::tenant_123:agent/data-pipeline-agent"],
    "conditions": [{ "operator": "StringEquals", "key": "agent.tags.environment", "values": ["production"] }]
  },
  "policy_arns": ["arn:iam::tenant_123:policy/S3ReadOnlyDataBucket"],
  "max_session_duration_sec": 3600
}
```

---

## 4️⃣ Data Flow

### Flow 1: Authorization Decision (Hot Path — < 10ms)
```
1. Service receives API request from agent (with Bearer token)
   ↓
2. Service calls POST /api/v1/authorize (or SDK inline evaluation)
   ↓
3. Policy Engine:
   a. Validate JWT token (signature, expiry, tenant_id) — < 1ms
   b. Extract principal, assumed role, session policies from token
   c. Load applicable policies from cache (Redis or local):
      - Agent's directly attached policies
      - Role's policies (if role assumed)
      - Permission boundary policies
      - Organization-level SCPs (Service Control Policies)
   d. Evaluate policies (deny-overrides algorithm):
      Step 1: Check explicit DENYs → if any DENY matches → DENY
      Step 2: Check permission boundary → if action not in boundary → DENY
      Step 3: Check session policies → if action not in session scope → DENY
      Step 4: Check for matching ALLOW → if found → ALLOW
      Step 5: Default → DENY (implicit deny)
   e. Log decision to audit pipeline (async, fire-and-forget)
   ↓
4. Return ALLOW or DENY with matched statement IDs
   Total: < 10ms P99
```

### Flow 2: Agent Credential Issuance
```
1. Agent calls POST /api/v1/credentials/session with API key + secret
   ↓
2. Credential Service:
   a. Validate API key + secret (bcrypt hash comparison)
   b. Check agent status (must be ACTIVE)
   c. If assuming role: validate trust policy (is agent trusted?)
   d. Resolve effective policies:
      intersection(agent_policies ∪ role_policies, permission_boundary)
   e. Generate JWT:
      {
        "sub": "agent_abc123",
        "iss": "iam.cloud.example.com",
        "tenant": "tenant_123",
        "role": "arn:iam::tenant_123:role/DataPipelineRole",
        "policies": ["policy_1", "policy_2"],
        "exp": now + 900,  // 15 minutes
        "jti": "token_uuid_001"
      }
   f. Sign with RSA-256 private key
   g. Log credential issuance to audit pipeline
   ↓
3. Return signed JWT to agent
   Agent includes JWT in all subsequent API calls
```

### Flow 3: Policy Change Propagation
```
1. Admin creates/updates policy via POST /api/v1/policies
   ↓
2. Policy Service:
   a. Validate policy syntax and semantics
   b. Store new policy version in Policy Store (PostgreSQL)
   c. Publish "policy-changed" event to Kafka
   ↓
3. Cache Invalidation:
   a. Policy Cache Updater consumes event
   b. Update Redis cache with new policy version
   c. Broadcast invalidation to local caches (all Policy Engine pods)
   ↓
4. Propagation time: < 5 seconds from write to all caches updated
   During propagation: some auth decisions may use stale (previous) policy
   → Eventual consistency tradeoff (acceptable for IAM)
```

### Flow 4: Audit Log Pipeline
```
1. Every auth decision / credential event / policy change:
   → Produce to Kafka topic "audit-events" (async, non-blocking)
   ↓
2. Kafka (durable, replicated):
   Topic: "audit-events", 100 partitions, replication=3
   Throughput: 1M events/sec
   ↓
3. Audit consumers:
   a. Real-time: write to Elasticsearch (searchable, 30-day hot)
   b. Archival: write to S3/GCS (immutable, compressed, 7-year retention)
   c. Alerting: anomaly detection stream processor (unusual deny patterns)
   ↓
4. Audit logs are APPEND-ONLY, cryptographically hashed (tamper-proof chain)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AI AGENTS / CLOUD SERVICES                           │
│                                                                               │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐               │
│  │ ML Agent   │  │ Data ETL  │  │ CI/CD Bot  │  │ Monitoring│               │
│  │            │  │ Agent     │  │            │  │ Agent     │               │
│  └─────┬──────┘  └─────┬─────┘  └─────┬──────┘  └─────┬─────┘               │
└────────┼───────────────┼──────────────┼───────────────┼──────────────────────┘
         │ Bearer JWT    │              │               │
         └───────────────┼──────────────┼───────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       API GATEWAY                                            │
│  • JWT validation (signature + expiry)  • Rate limiting per tenant          │
│  • Extract tenant_id from token         • Route to service                  │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
               ┌────────────────┼────────────────┐
               │                │                │
         ┌─────▼──────┐  ┌─────▼──────┐  ┌─────▼───────────┐
         │ AGENT MGMT  │  │ POLICY     │  │ CREDENTIAL      │
         │ SERVICE     │  │ ENGINE     │  │ SERVICE         │
         │             │  │            │  │                 │
         │ • CRUD      │  │ • Authorize│  │ • Issue tokens  │
         │   agents    │  │   (hot path│  │ • Rotate keys   │
         │ • Attach    │  │   < 10ms)  │  │ • Assume roles  │
         │   policies  │  │ • Evaluate │  │ • Validate creds│
         │ • List/query│  │   RBAC+ABAC│  │                 │
         │             │  │ • Deny-    │  │ Pods: 20-50     │
         │ Pods: 10-20 │  │   overrides│  └─────────────────┘
         └──────┬──────┘  │            │
                │         │ Pods:      │
                │         │ 100-500    │
                │         │ (hot path) │
                │         └──────┬─────┘
                │                │
   ┌────────────┼────────────────┼──────────────────┐
   │            │                │                  │
   ▼            ▼                ▼                  ▼
┌──────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ AGENT    │ │ POLICY STORE │ │ POLICY CACHE │ │ CREDENTIAL   │
│ STORE    │ │              │ │              │ │ STORE        │
│          │ │ PostgreSQL   │ │ Redis        │ │              │
│PostgreSQL│ │              │ │ Cluster      │ │ PostgreSQL   │
│          │ │ • Policies   │ │              │ │ + HSM        │
│ • Agents │ │ • Roles      │ │ • Full policy│ │              │
│ • Profiles│ │ • Statements│ │   set cached │ │ • API keys   │
│ • Tags   │ │ • Versions   │ │ • Per-tenant │ │   (hashed)   │
│          │ │              │ │   partitions │ │ • Certificates│
│ ~50 GB   │ │ ~20 GB       │ │ ~20 GB       │ │ • Revocation │
└──────────┘ └──────────────┘ └──────────────┘ └──────────────┘

                    │ Kafka
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA                                                │
│  Topic: "audit-events"       (1M events/sec, auth decisions + admin actions) │
│  Topic: "policy-changes"     (cache invalidation signals)                    │
│  Topic: "credential-events"  (issuance, rotation, revocation)                │
└──────────┬──────────────────────────┬────────────────────────────────────────┘
           │                          │
     ┌─────▼──────────────┐    ┌─────▼──────────────┐
     │ AUDIT SERVICE       │    │ CACHE INVALIDATION  │
     │                     │    │ SERVICE             │
     │ • ES (30-day hot)   │    │                     │
     │ • S3 (7yr archive)  │    │ • Consume policy    │
     │ • Anomaly detection │    │   change events     │
     │                     │    │ • Update Redis      │
     │ Pods: 20-50         │    │ • Broadcast to pods │
     └─────────────────────┘    │                     │
                                │ Pods: 3-5           │
                                └─────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Policy Engine** | Evaluate auth decisions (hot path, < 10ms) | Go/Rust on K8s | 100-500 pods (CPU-bound, latency-critical) |
| **Agent Management** | CRUD for agent identities | Java on K8s | 10-20 pods |
| **Credential Service** | Issue/rotate/revoke tokens and API keys | Java on K8s + HSM | 20-50 pods |
| **Policy Store** | Source of truth for policies, roles, statements | PostgreSQL | ~20 GB, replicated |
| **Policy Cache** | Full policy set cached for fast evaluation | Redis Cluster | ~20 GB, read replicas |
| **Agent Store** | Agent metadata, profiles, tags | PostgreSQL | ~50 GB |
| **Credential Store** | Hashed API keys, certificates, revocation lists | PostgreSQL + HSM | Encrypted at rest |
| **Audit Service** | Immutable audit log storage and querying | Kafka → Elasticsearch + S3 | 1M events/sec |
| **Cache Invalidation** | Propagate policy changes to all caches | Kafka consumer | 3-5 pods |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Policy Evaluation Engine — < 10ms Authorization Decisions

**The Problem**: Every API call across the entire cloud platform calls the Policy Engine. At 1M decisions/sec, each decision must evaluate ~10 policies with conditions in < 10ms. This is the most latency-sensitive component.

```java
public class PolicyEvaluationEngine {

    /**
     * Deny-overrides evaluation algorithm (same as AWS IAM):
     * 
     * 1. Gather all applicable policies:
     *    - Agent's directly attached policies
     *    - Role's policies (if role assumed)
     *    - Permission boundary (if set)
     *    - Organization SCPs (Service Control Policies)
     *    - Session policies (if present in token)
     * 
     * 2. Evaluate in order:
     *    a. EXPLICIT DENY: if any statement matches with Effect=DENY → DENY
     *    b. PERMISSION BOUNDARY: if action not allowed by boundary → DENY
     *    c. SCP: if action not allowed by org SCP → DENY
     *    d. SESSION POLICY: if action not in session scope → DENY
     *    e. ALLOW: if any statement matches with Effect=ALLOW → ALLOW
     *    f. DEFAULT: → IMPLICIT DENY
     * 
     * Performance:
     *   - Policies pre-loaded in local memory (L1 cache, refreshed every 30s)
     *   - Redis L2 cache for cache misses
     *   - Condition evaluation: ~0.01ms per condition
     *   - 10 policies × 3 conditions = 30 evaluations = ~0.3ms
     *   - Total with overhead: < 5ms P99
     */
    
    // Local in-memory policy cache (per pod, refreshed via Kafka)
    private final LoadingCache<String, List<Policy>> policyCache;
    
    public AuthDecision evaluate(AuthRequest request) {
        String tenantId = request.getTenantId();
        String principalArn = request.getPrincipal();
        String action = request.getAction();
        String resource = request.getResource();
        Map<String, String> context = request.getContext();
        
        // Step 1: Load all applicable policies
        List<Policy> policies = loadApplicablePolicies(tenantId, principalArn);
        
        // Step 2: Check explicit DENYs first
        for (Policy policy : policies) {
            for (PolicyStatement stmt : policy.getStatements()) {
                if (stmt.getEffect() == Effect.DENY && matches(stmt, action, resource, context)) {
                    return AuthDecision.deny("EXPLICIT_DENY", stmt.getSid());
                }
            }
        }
        
        // Step 3: Check permission boundary
        Policy boundary = getPermissionBoundary(principalArn);
        if (boundary != null && !isAllowedByBoundary(boundary, action, resource)) {
            return AuthDecision.deny("PERMISSION_BOUNDARY_VIOLATION", boundary.getName());
        }
        
        // Step 4: Check for matching ALLOW
        for (Policy policy : policies) {
            for (PolicyStatement stmt : policy.getStatements()) {
                if (stmt.getEffect() == Effect.ALLOW && matches(stmt, action, resource, context)) {
                    return AuthDecision.allow(stmt.getSid());
                }
            }
        }
        
        // Step 5: Default deny
        return AuthDecision.deny("IMPLICIT_DENY", null);
    }
    
    private boolean matches(PolicyStatement stmt, String action, String resource, Map<String, String> context) {
        // Action matching: support wildcards ("s3:*", "s3:Get*")
        if (!matchesPattern(stmt.getActions(), action)) return false;
        
        // Resource matching: support ARN wildcards
        if (!matchesPattern(stmt.getResources(), resource)) return false;
        
        // Condition evaluation (ABAC)
        for (Condition condition : stmt.getConditions()) {
            if (!evaluateCondition(condition, context)) return false;
        }
        
        return true;
    }
    
    private boolean evaluateCondition(Condition condition, Map<String, String> context) {
        String actualValue = context.get(condition.getKey());
        if (actualValue == null) return false;
        return switch (condition.getOperator()) {
            case "StringEquals" -> condition.getValues().contains(actualValue);
            case "StringNotEquals" -> !condition.getValues().contains(actualValue);
            case "StringLike" -> condition.getValues().stream().anyMatch(v -> wildcardMatch(v, actualValue));
            case "NumericGreaterThan" -> Double.parseDouble(actualValue) > Double.parseDouble(condition.getValues().get(0));
            case "NumericLessThan" -> Double.parseDouble(actualValue) < Double.parseDouble(condition.getValues().get(0));
            case "DateGreaterThan" -> Instant.parse(actualValue).isAfter(Instant.parse(condition.getValues().get(0)));
            case "Bool" -> Boolean.parseBoolean(actualValue) == Boolean.parseBoolean(condition.getValues().get(0));
            default -> false;
        };
    }
}
```

---

### Deep Dive 2: Short-Lived Credentials & JWT Token Design

**The Problem**: AI agents run autonomously — they can't enter passwords. Long-lived API keys are a security risk (leaked keys = full access until manually revoked). We use short-lived JWT tokens (15-min expiry) that agents refresh automatically.

```
JWT Token Structure:
{
  "header": { "alg": "RS256", "kid": "key_2025_01" },
  "payload": {
    "sub": "agent_abc123",                          // Agent ID
    "iss": "iam.cloud.example.com",                  // Issuer
    "aud": "cloud.example.com",                      // Audience
    "tenant": "tenant_123",                          // Tenant isolation
    "role": "arn:iam::tenant_123:role/DataRole",     // Assumed role (optional)
    "policies": ["policy_1", "policy_2"],            // Effective policy ARNs
    "boundary": "arn:iam::tenant_123:policy/Max",    // Permission boundary
    "tags": { "env": "production", "team": "data" }, // Agent attributes (for ABAC)
    "iat": 1736503200,                               // Issued at
    "exp": 1736504100,                               // Expires (15 min)
    "jti": "tok_uuid_001"                            // Token ID (for revocation)
  }
}

Key rotation: RSA key pairs rotated monthly. JWKS endpoint publishes public keys.
Revocation: jti added to Redis blocklist on explicit revoke (TTL = token remaining lifetime).
Verification: any service verifies locally using public key (no network call to IAM).
```

---

### Deep Dive 3: Policy Storage & Caching — Low-Latency Reads with Consistency

**The Problem**: 10M policies must be instantly accessible for auth decisions. Writing to PostgreSQL is strong-consistent, but reading from DB on every auth decision (1M/sec) is too slow. We cache aggressively but must propagate changes within 5 seconds.

```
Caching architecture (3 layers):

L1: Local in-memory cache (per Policy Engine pod)
  - Caffeine cache, 10K entries, 30-second TTL
  - Absorbs 95% of reads (same agent makes many requests)
  - Invalidated via Kafka "policy-changes" topic subscription

L2: Redis cluster (shared)
  - Full policy set cached by tenant: policies:{tenant_id} → JSON
  - Also per-agent: agent_policies:{agent_id} → [policy_ids]
  - TTL: 5 minutes, refreshed on access
  - Invalidated by Cache Invalidation Service on policy change

L3: PostgreSQL (source of truth)
  - Read on L1+L2 miss (~0.1% of requests)
  - All writes go here first

Policy versioning:
  - Each policy has an immutable version number
  - Updates create new version (old version preserved for audit)
  - Cache keys include version: policy:{id}:v{version}
  - Active version tracked in: policy_active:{id} → latest_version

Propagation timeline:
  t=0ms:   Admin writes policy to PostgreSQL
  t=50ms:  Kafka event "policy-changed" published
  t=500ms: Cache Invalidation Service updates Redis
  t=1000ms:Kafka event reaches Policy Engine pods → L1 invalidated
  t=5000ms:All pods guaranteed to have new policy (worst case)
```

---

### Deep Dive 4: Multi-Tenancy & Tenant Isolation

**The Problem**: 10K tenants share the same infrastructure. Tenant A's agents must never see Tenant B's policies, resources, or audit logs. A bug in our code should not leak cross-tenant data.

```
Isolation layers:

1. DATA ISOLATION
   - Every DB row has tenant_id column (non-nullable)
   - Every query includes WHERE tenant_id = ? (enforced by ORM interceptor)
   - Every API request extracts tenant_id from JWT (not from user input)
   - Redis keys prefixed: policies:{tenant_id}:...
   - Kafka audit events tagged with tenant_id for partitioning

2. COMPUTE ISOLATION
   - Shared Policy Engine pods serve all tenants
   - Per-tenant rate limiting: max requests/sec from tenant quota
   - Noisy neighbor protection: if tenant exceeds quota → throttle (429)
   - Enterprise tenants can opt for dedicated Policy Engine pods

3. NETWORK ISOLATION
   - Agent tokens contain tenant_id → services validate token tenant matches resource tenant
   - Cross-tenant access only via explicit trust policies (federated)
   - No agent can forge a token for a different tenant (RSA signature verification)

4. AUDIT ISOLATION
   - Audit logs partitioned by tenant_id in Kafka
   - Elasticsearch indices per tenant: audit-{tenant_id}-{date}
   - Admin can only query their own tenant's audit logs
```

---

### Deep Dive 5: Audit Logging at Scale — 1M Events/Sec, Tamper-Proof

**The Problem**: Every auth decision (1M/sec) must be logged for compliance (SOC2, GDPR, HIPAA). Logs must be immutable, tamper-proof, and queryable for forensic analysis.

```
Pipeline: Policy Engine → Kafka → Elasticsearch + S3

Kafka: 100 partitions, replication=3, acks=all
  - 1M events/sec × 500 bytes = 500 MB/sec
  - Retention: 7 days (backup before archival)

Elasticsearch (hot tier, 30 days):
  - Index per tenant per day: audit-{tenant}-{date}
  - Searchable by: agent_id, action, resource, decision, time range
  - ~43 TB/day across all tenants

S3/GCS (cold archive, 7 years):
  - Compressed Parquet files, partitioned by tenant/date
  - Immutable: write-once, no deletes (compliance)
  - ~5 TB/day compressed

Tamper-proofing:
  - Each audit event includes hash of previous event (hash chain)
  - Daily integrity check: recompute chain, alert if broken
  - S3 Object Lock (WORM) for regulatory compliance
```

---

### Deep Dive 6: Least Privilege & Permission Boundaries for AI Agents

**The Problem**: AI agents are autonomous — they may request broader access than needed, or be tricked (prompt injection) into accessing unauthorized resources. Permission boundaries act as guardrails.

```
Permission boundaries = maximum permissions an agent can EVER have

Example:
  Agent "ml-training-bot" has:
    - Attached policy: AllowS3ReadWrite (broad access)
    - Permission boundary: MaxMLAccess (restricts to ML + S3, no IAM, no billing)
  
  Effective permissions = INTERSECTION(attached_policies, permission_boundary)
  → Agent can read/write S3 (both allow) but cannot touch IAM (boundary blocks)

Session policies = further temporary restrictions:
  Agent assumes role with session policy: "only access bucket/training-data/*"
  → Effective = INTERSECTION(role_policies, session_policy, permission_boundary)
  → Even if role allows all S3, session restricts to one bucket

Why critical for AI agents:
  1. Agents may be compromised (prompt injection, supply chain attack)
  2. Boundary limits blast radius regardless of what policies are attached
  3. An admin cannot accidentally give an agent more than its boundary allows
  4. Defense-in-depth: multiple independent permission gates
```

---

### Deep Dive 7: Federated Identity — OIDC/SAML Trust for External Agents

**The Problem**: Enterprises have existing identity providers (Okta, Azure AD, Google Workspace). Their AI agents should authenticate via OIDC tokens from these providers rather than managing separate IAM credentials.

```
Federated authentication flow:

1. Enterprise configures trust: "Trust OIDC tokens from https://login.okta.com/enterprise_123"
   → Stored as TrustedIdentityProvider in tenant config

2. External agent authenticates with Okta → receives OIDC ID token

3. Agent calls our IAM: POST /api/v1/credentials/federated
   { "oidc_token": "eyJ...", "role_arn": "arn:iam::tenant_123:role/ExternalRole" }

4. IAM Credential Service:
   a. Validate OIDC token (signature, issuer, audience, expiry)
   b. Check issuer is in tenant's trusted providers list
   c. Map OIDC claims to agent identity (sub claim → agent_id)
   d. Validate trust policy on requested role: does it trust this OIDC issuer?
   e. Issue short-lived session token (same JWT as internal agents)

5. From this point: agent uses session token like any other agent
   → Same auth decisions, same audit logging, same policy evaluation

Cross-tenant delegation:
  Tenant A trusts Tenant B: "Allow agents from tenant_B to assume ReadOnlyRole"
  → Trust policy: { "trusted_principals": ["arn:iam::tenant_B:agent/*"] }
  → Tenant B's agent assumes role in Tenant A → gets scoped access
```

---

### Deep Dive 8: High Availability & Disaster Recovery

**The Problem**: If the IAM system is unavailable, ALL cloud services are unavailable (every request needs authorization). 99.999% availability = < 5.26 minutes downtime/year.

```
HA strategy:

1. POLICY ENGINE (most critical — auth decisions):
   - 100-500 pods across 3+ availability zones
   - Each pod has full policy cache in local memory (L1)
   - Even if Redis AND PostgreSQL are both down → auth decisions continue
     using stale local cache (graceful degradation)
   - Health check: if pod can't refresh cache in 5 min → alert but don't kill
   - Zero single points of failure for the decision path

2. CACHE-FIRST ARCHITECTURE:
   - Auth decision path: L1 local → L2 Redis → L3 PostgreSQL
   - L1 hit rate: 95% → even Redis failure doesn't impact most requests
   - L2 (Redis) is replicated across AZs with automatic failover
   - L3 (PostgreSQL) is replicated with read replicas

3. CREDENTIAL SERVICE:
   - Tokens are self-contained JWTs → verification is LOCAL (public key)
   - No network call to IAM needed to verify a token
   - If Credential Service is down: existing tokens still work until expiry
   - RSA public keys cached on every service (JWKS endpoint, 1-hour cache)

4. AUDIT PIPELINE:
   - If Kafka is down: buffer audit events locally (disk queue)
   - When Kafka recovers: drain buffer (no event loss)
   - Audit unavailability does NOT block auth decisions (fire-and-forget)

5. DISASTER RECOVERY:
   - Active-active across 2 regions (hot-hot)
   - Policy writes: primary region, replicated to secondary (< 1s lag)
   - Auth decisions: served from nearest region (local cache)
   - Failover: DNS switch to secondary region (< 30 seconds)

Failure modes handled:
  - Single pod crash → load balancer routes to healthy pods (< 1s)
  - Redis cluster failure → L1 cache + PostgreSQL fallback (< 10ms degradation)
  - PostgreSQL primary failure → promote replica (< 30s, no auth impact due to caching)
  - Full region failure → DNS failover to secondary region (< 30s)
  - Kafka failure → local disk buffer for audit (no auth impact)
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Policy evaluation** | Deny-overrides algorithm (AWS IAM-compatible) | Industry standard; explicit deny always wins; defense-in-depth with boundaries |
| **Credentials** | Short-lived JWT (15 min) + API key for issuance | JWTs verifiable locally (no IAM call); short lifetime limits blast radius |
| **Policy caching** | 3-layer (L1 local + L2 Redis + L3 PostgreSQL) | 95% L1 hit rate; auth decisions survive Redis/DB failures; < 5s propagation |
| **Audit logging** | Async Kafka → ES + S3 (fire-and-forget from decision path) | Non-blocking; 1M events/sec; auth decisions not impacted by audit failures |
| **Multi-tenancy** | Shared infra + tenant_id in every row/key/token | Cost-efficient; tenant_id enforced at ORM + API + token level |
| **Permission boundaries** | Max-permissions guardrail (intersection with attached policies) | Critical for AI agents; limits blast radius of compromised agents |
| **Consistency** | Strong writes (PostgreSQL) + eventual reads (< 5s cache propagation) | Policy changes durable immediately; auth decisions tolerate seconds of staleness |
| **HA** | Cache-first + active-active multi-region | Auth decisions never fail; JWT verification is local; graceful degradation |
| **Token revocation** | Redis blocklist (jti → TTL matching token remaining life) | Fast revocation; automatic cleanup via TTL; check only on explicit revoke |
| **Policy model** | RBAC + ABAC hybrid | RBAC for role assignment; ABAC for fine-grained conditions (environment, time, IP) |

## Interview Talking Points

1. **"Deny-overrides policy evaluation: explicit deny → boundary → SCP → allow → implicit deny"** — Same algorithm as AWS IAM. 10 policies × 3 conditions evaluated in < 5ms. Local L1 cache (Caffeine, 30s TTL) absorbs 95% of reads.

2. **"Short-lived JWTs (15 min) verified locally with RSA public keys"** — No network call to IAM on every request. JWKS endpoint serves public keys. Token contains tenant_id, policies, role, tags (for ABAC). Revocation via Redis jti blocklist.

3. **"3-layer policy cache: L1 (in-memory) → L2 (Redis) → L3 (PostgreSQL)"** — L1 hit rate 95% → auth decisions work even if Redis is down. Policy changes propagate via Kafka in < 5s. Cache-first design gives 99.999% availability.

4. **"Permission boundaries = max permissions guardrail for AI agents"** — Effective permissions = intersection(attached, boundary). Even if agent is compromised, boundary limits blast radius. Critical for autonomous AI agents.

5. **"Audit pipeline: async Kafka (1M events/sec) → ES (30-day hot) + S3 (7-year archive)"** — Fire-and-forget from decision path. Hash chain for tamper-proofing. S3 Object Lock for WORM compliance. Non-blocking: audit failure never blocks auth.

6. **"Multi-tenancy: tenant_id in every DB row, Redis key, JWT claim, and Kafka partition"** — ORM interceptor enforces tenant_id in all queries. Token's tenant_id extracted server-side (not from user input). Cross-tenant access only via explicit trust policies.

7. **"Federated identity: OIDC/SAML trust for external agents"** — Enterprise configures trusted IdP. External agent presents OIDC token → our IAM validates and issues session JWT. Cross-tenant delegation via trust policies on roles.

8. **"Active-active multi-region for 99.999% availability"** — Auth decisions served from nearest region using local cache. JWT verification is fully local. PostgreSQL replication < 1s. DNS failover < 30s. Audit buffered locally during Kafka outages.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **AWS IAM** | Identical pattern | Our system is agent-focused (machine identity vs human); same policy model |
| **OAuth2 / OIDC Provider** | Same token issuance | OAuth2 is for user delegation; our system is for machine-to-machine auth |
| **API Gateway (Kong/Envoy)** | Same auth enforcement point | Gateway validates tokens; our system issues tokens and evaluates policies |
| **RBAC System** | Same role model | We add ABAC conditions on top of RBAC for fine-grained control |
| **Audit/Compliance System** | Same logging pipeline | Our audit is integrated with auth decisions; standalone audit is broader |
| **Secret Manager (Vault)** | Same credential management | Vault manages secrets; we manage identities and authorization policies |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Policy engine** | Custom (Go/Rust) | Open Policy Agent (OPA) / Cedar | OPA: standard policy language (Rego); Cedar: AWS-backed, formally verified |
| **Policy store** | PostgreSQL | CockroachDB / Spanner | CockroachDB: global strong consistency without sharding; Spanner: Google-native |
| **Policy cache** | Redis + Caffeine | Hazelcast / Memcached | Hazelcast: embedded cache with cluster invalidation; Memcached: simpler |
| **Token format** | JWT (RS256) | PASETO / Macaroons | PASETO: no algorithm confusion attacks; Macaroons: caveats for delegation |
| **Audit storage** | ES + S3 | ClickHouse / BigQuery | ClickHouse: faster analytics on audit data; BigQuery: serverless |
| **HSM** | AWS CloudHSM / GCP Cloud HSM | Software HSM (SoftHSM) | SoftHSM: development/testing; Cloud HSM: production compliance |
| **Identity federation** | OIDC/SAML | SPIFFE/SPIRE | SPIFFE: workload identity standard; better for service mesh environments |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- AWS IAM Architecture & Policy Evaluation Logic
- Google Cloud IAM (Conditions & Policy Binding)
- NIST SP 800-162: ABAC Guide
- RFC 7519: JSON Web Tokens (JWT)
- RFC 7517: JSON Web Key Sets (JWKS)
- Open Policy Agent (OPA) & Rego Policy Language
- Cedar Policy Language (AWS Verified Permissions)
- SPIFFE/SPIRE Workload Identity Framework
