# Design a Feature Flag Service - Hello Interview Framework

> **Question**: Design a feature flag service that allows software teams to enable/disable features dynamically without code deployment, supporting targeted rollouts to user segments (by region, percentage, etc.) and handling 1M flag evaluations per second with minimal latency. Think LaunchDarkly, ConfigCat, or Unleash.
>
> **Asked at**: Microsoft, Stripe, Amazon
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

## 1️⃣ Requirements

### Functional (P0)
1. **Flag CRUD**: Create, update, delete feature flags. Each flag: key, variations (boolean/string/number/JSON), default value, targeting rules, enabled/disabled.
2. **Flag Evaluation**: Given a flag key + user context (user_id, region, plan, custom attributes) → return the correct variation. Ultra-low latency (< 5ms). Deterministic: same user always gets same variation.
3. **Targeting Rules**: Percentage rollout (10% of users get new feature), user segment targeting (region=US, plan=enterprise), individual user targeting (user_id whitelist), kill switch (instant off).
4. **Gradual Rollout**: Increase from 1% → 10% → 50% → 100% over time. Deterministic bucketing: hash(flag_key + user_id) % 100 < percentage → same user stays in same bucket.
5. **Real-Time Propagation**: Flag changes propagate to all SDKs within 10 seconds. No deploy needed.
6. **Multi-Environment**: Separate flag configs per environment (dev, staging, production). Same flag key, different rules per env.

### Functional (P1)
- Audit log (who changed what flag, when, old/new value)
- Flag prerequisites (flag B only on if flag A is on)
- Scheduled flag changes (auto-enable at midnight)
- Flag analytics (impression tracking: which users evaluated which flag)
- A/B test integration (flag variations → experiment metrics)
- Approval workflows (production flag changes require peer review)

### Non-Functional Requirements
| Attribute | Target |
|-----------|--------|
| **Evaluation Latency** | < 5ms P99 (client-side SDK: 0ms via local cache) |
| **Throughput** | 1M evaluations/sec across all services |
| **Propagation** | < 10 seconds from flag change to all SDKs |
| **Availability** | 99.99% (if flag service down, SDKs use cached flags) |
| **Consistency** | Eventually consistent (< 10s), deterministic bucketing |
| **Scale** | 10K flags, 1000 services, 100M users |

### Capacity Estimation
```
Flags: 10K total, 5K active
Services: 1000 (each with SDK instance)
Evaluations: 1M/sec (1000 services × 1000 evals/sec each)

Flag config size: 10K flags × 2 KB each = 20 MB (entire flag set)
  → Fits in SDK local memory cache (every service caches all flags)

Propagation: 1000 SDK instances to update on flag change
  Flag changes: ~100/day (infrequent writes)
  SSE connections: 1000 persistent connections to Flag Service

Audit logs: ~100 changes/day × 1 KB = 100 KB/day
Impressions (if tracked): 1M/sec × 50 bytes = 50 MB/sec = 4.3 TB/day
```

---

## 2️⃣ Core Entities

```java
public class FeatureFlag {
    String flagKey;             // "new-checkout-flow", "dark-mode"
    String projectId;           // Organizational grouping
    String environment;         // "production", "staging", "dev"
    FlagType type;              // BOOLEAN, STRING, NUMBER, JSON
    List<Variation> variations; // [{ value: true }, { value: false }]
    int defaultVariationIndex;  // Default when no rule matches
    int offVariationIndex;      // Value when flag is OFF
    boolean enabled;            // Master kill switch
    List<TargetingRule> rules;  // Evaluated in order
    FallbackRule fallthrough;   // If no rule matches: percentage rollout
    List<UserTarget> userTargets; // Individual user overrides
    int version;                // Incremented on every change
    Instant updatedAt;
    String updatedBy;
}

public class Variation {
    int index;                  // 0, 1, 2, ...
    Object value;               // true, "variant-a", 42, { "config": "..." }
    String name;                // "Control", "Treatment A"
}

public class TargetingRule {
    String ruleId;
    List<Clause> clauses;       // AND logic: all clauses must match
    RolloutConfig rollout;      // Which variation(s) to serve if rule matches
}

public class Clause {
    String attribute;           // "region", "plan", "user.age", custom attribute
    Operator operator;          // IN, NOT_IN, EQUALS, GREATER_THAN, CONTAINS, MATCHES_REGEX
    List<String> values;        // ["US", "CA"] for IN operator
}
public enum Operator { IN, NOT_IN, EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, CONTAINS, MATCHES_REGEX }

public class RolloutConfig {
    RolloutType type;           // SINGLE_VARIATION, PERCENTAGE_ROLLOUT
    int variationIndex;         // For SINGLE_VARIATION
    List<WeightedVariation> weightedVariations; // For percentage: [{ variation: 0, weight: 90 }, { variation: 1, weight: 10 }]
    String bucketBy;            // Hash attribute: "user_id" (default)
}

public class UserTarget {
    List<String> userIds;       // Specific user IDs
    int variationIndex;         // Which variation they always get
}

public class UserContext {
    String userId;              // Required: hash key for bucketing
    Map<String, Object> attributes; // { "region": "US", "plan": "enterprise", "beta_tester": true }
}

public class FlagChangeEvent {
    String flagKey;
    String environment;
    int oldVersion;
    int newVersion;
    String changedBy;
    Instant changedAt;
    String changeType;          // CREATED, UPDATED, DELETED, TOGGLED
}
```

---

## 3️⃣ API Design

### 1. Evaluate Flag (SDK Hot Path — cached locally)
```
POST /api/v1/flags/evaluate
Request: {
  "flag_key": "new-checkout-flow",
  "user": { "user_id": "user_123", "attributes": { "region": "US", "plan": "enterprise" } },
  "environment": "production"
}
Response: { "value": true, "variation_index": 0, "version": 42, "reason": "RULE_MATCH:rule_001" }
```
> **In practice**: SDK caches all flags locally. Evaluation is in-memory (< 0.1ms). This API is only for server-side SDKs that don't cache.

### 2. Get All Flags (SDK Bootstrap)
```
GET /api/v1/flags?environment=production
Response: {
  "flags": {
    "new-checkout-flow": { "key": "new-checkout-flow", "enabled": true, "version": 42, "variations": [...], "rules": [...] },
    "dark-mode": { ... }
  },
  "version": 1234  // Global config version for change detection
}
```

### 3. Create/Update Flag (Admin Console)
```
PUT /api/v1/flags/{flag_key}/environments/{env}
Request: {
  "enabled": true,
  "rules": [{
    "clauses": [{ "attribute": "region", "operator": "IN", "values": ["US", "CA"] }],
    "rollout": { "type": "PERCENTAGE_ROLLOUT", "weighted_variations": [{ "variation": 0, "weight": 90 }, { "variation": 1, "weight": 10 }] }
  }],
  "fallthrough": { "type": "PERCENTAGE_ROLLOUT", "weighted_variations": [{ "variation": 0, "weight": 95 }, { "variation": 1, "weight": 5 }] }
}
Response (200): { "flag_key": "new-checkout-flow", "version": 43, "updated_at": "..." }
```

### 4. Toggle Flag On/Off (Kill Switch)
```
PATCH /api/v1/flags/{flag_key}/environments/{env}/toggle
Request: { "enabled": false }
Response (200): { "flag_key": "new-checkout-flow", "enabled": false, "version": 44 }
```

### 5. Stream Flag Changes (SSE — SDK Real-Time Updates)
```
GET /api/v1/flags/stream?environment=production
Response (text/event-stream):
data: {"type":"flag_updated","flag_key":"new-checkout-flow","version":43}
data: {"type":"flag_updated","flag_key":"dark-mode","version":12}
```

### 6. Get Audit Log
```
GET /api/v1/flags/{flag_key}/audit?limit=50
Response: { "events": [{ "changed_by": "alice@company.com", "changed_at": "...", "old_enabled": true, "new_enabled": false, ... }] }
```

---

## 4️⃣ Data Flow

### Flag Evaluation (Hot Path — In SDK Memory)
```
1. Application code: if (featureFlags.isEnabled("new-checkout-flow", userContext)) { ... }
   ↓
2. SDK evaluates LOCALLY from cached flag config (0ms network, < 0.1ms CPU):
   a. Find flag "new-checkout-flow" in local cache
   b. If flag.enabled == false → return offVariation
   c. Check userTargets: is user_123 in any target list? → return targeted variation
   d. Evaluate rules in order:
      Rule 1: region IN ["US", "CA"] → user.region = "US" → MATCH!
        → Apply rollout: hash("new-checkout-flow:user_123") % 100 = 73
        → 73 < 90 (90% weight for variation 0) → return variation 0 (true)
   e. If no rule matches → evaluate fallthrough rollout
   ↓
3. Return result to application (zero network latency)
```

### Flag Change Propagation (Write Path)
```
1. Admin updates flag in console → PUT /api/v1/flags/{key}/environments/production
   ↓
2. Flag Service:
   a. Validate flag config (rules, variations)
   b. Increment version
   c. Write to Flag Store (PostgreSQL)
   d. Publish to Kafka "flag-changes" topic
   e. Write to audit log
   ↓
3. SSE Relay Service:
   a. Consume from Kafka
   b. Push SSE event to all connected SDKs: { "type": "flag_updated", "flag_key": "..." }
   ↓
4. SDK receives SSE event:
   a. Fetch updated flag config: GET /api/v1/flags/{key}?env=production
   b. Update local cache
   c. New evaluations immediately use updated config
   ↓
5. Total propagation: < 10 seconds from admin save to SDK cache updated
```

### SDK Bootstrap (On Service Start)
```
1. Service starts → SDK initializes
   ↓
2. SDK calls GET /api/v1/flags?environment=production
   → Receives full flag config (~20 MB for 10K flags)
   → Stores in local memory cache
   ↓
3. SDK opens SSE connection for real-time updates
   ↓
4. If Flag Service is unreachable:
   → SDK uses last known cached config (persisted to disk)
   → Service starts normally with stale (but functional) flags
   → When connection restores: sync latest config
```

---

## 5️⃣ High-Level Design

```
┌───────────────────────────────────────────────────────────┐
│                    APPLICATION SERVICES                     │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
│  │ Service A    │ │ Service B    │ │ Service N    │        │
│  │ ┌─────────┐ │ │ ┌─────────┐ │ │ ┌─────────┐ │        │
│  │ │ SDK     │ │ │ │ SDK     │ │ │ │ SDK     │ │        │
│  │ │ (local  │ │ │ │ (local  │ │ │ │ (local  │ │        │
│  │ │  cache) │ │ │ │  cache) │ │ │ │  cache) │ │        │
│  │ │ 0ms eval│ │ │ │ 0ms eval│ │ │ │ 0ms eval│ │        │
│  │ └────┬────┘ │ │ └────┬────┘ │ │ └────┬────┘ │        │
│  └──────┼──────┘ └──────┼──────┘ └──────┼──────┘        │
└─────────┼───────────────┼──────────────┼─────────────────┘
          │ SSE stream     │              │
          └───────────────┼──────────────┘
                          ▼
┌───────────────────────────────────────────────────────────┐
│                 FLAG SERVICE (20-50 pods)                   │
│                                                             │
│  • Serve flag configs (GET /flags)                         │
│  • SSE relay (push updates to SDKs)                        │
│  • Flag CRUD API (admin console)                           │
│  • Evaluation API (for SDKs that don't cache)              │
└──────────┬─────────────────────────┬───────────────────────┘
           │                         │
     ┌─────▼──────┐          ┌──────▼──────────┐
     │ FLAG STORE │          │ FLAG CACHE       │
     │ (Postgres) │          │ (Redis)          │
     │            │          │                  │
     │ • Flags    │          │ • Full flag set  │
     │ • Rules    │          │   per env cached │
     │ • Versions │          │ • Version counter│
     │ • Audit log│          │ • ~20 MB per env │
     │            │          │                  │
     │ ~100 MB    │          │ ~60 MB (3 envs)  │
     └────────────┘          └─────────────────┘

┌───────────────────────────────────────────────────────────┐
│  KAFKA: "flag-changes"                                     │
│  • Published on every flag update                         │
│  • Consumed by SSE Relay → pushed to all SDK connections  │
│  • Consumed by Audit Writer → persisted for compliance    │
└───────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────┐
│  ADMIN CONSOLE (Web UI)                                    │
│  • Create/edit flags + targeting rules                    │
│  • Toggle kill switches                                   │
│  • View audit log                                         │
│  • Per-environment management                             │
│  • Approval workflows (for production changes)            │
└───────────────────────────────────────────────────────────┘
```

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Flag Service** | Serve configs, SSE relay, CRUD API | Go on K8s | 20-50 pods |
| **SDK (in each service)** | Local cache, evaluate flags in-memory | Library (Go/Java/JS/Python) | Embedded in each service |
| **Flag Store** | Source of truth for all flags | PostgreSQL | ~100 MB, replicated |
| **Flag Cache** | Pre-computed flag sets per environment | Redis | ~60 MB |
| **Kafka** | Propagate flag changes to SSE relay | Kafka | 3 brokers |
| **Admin Console** | Web UI for flag management | React app | 3-5 pods |

---

## 6️⃣ Deep Dives

### DD1: Flag Evaluation Algorithm — Deterministic Bucketing

**Problem**: "Roll out to 10% of users" must be deterministic: same user always in same bucket. Adding users to 10% → 20% must not reshuffle existing users.

```
Deterministic bucketing:
  bucket = hash(flag_key + ":" + user_id) % 100  (0-99)
  
  10% rollout: bucket < 10 → treatment, bucket >= 10 → control
  Increase to 20%: bucket < 20 → treatment
  → Users 0-9 STILL in treatment (no change!)
  → Users 10-19 NEWLY in treatment
  → Monotonic expansion: increasing % only adds users, never removes

Hash function: MurmurHash3 or SHA-1 (first 4 bytes → int → mod 100)
  Must be uniform distribution across 0-99

Weighted variations (A/B/C test):
  Variation A: weight 50 → buckets 0-49
  Variation B: weight 30 → buckets 50-79
  Variation C: weight 20 → buckets 80-99
  
  User with bucket 65 → Variation B
```

### DD2: SDK Architecture — Local Cache + SSE Updates

**Problem**: 1M evaluations/sec. Can't call Flag Service on every evaluation. SDK must cache flags locally and evaluate in-memory.

```
SDK lifecycle:

1. BOOTSTRAP (on service start):
   - GET /flags?env=production → full flag set (~20 MB)
   - Store in in-memory HashMap: flagKey → FlagConfig
   - Also persist to local disk (fallback if service restarts while Flag Service down)

2. EVALUATION (on every feature check):
   - Lookup flagKey in local HashMap → O(1)
   - Evaluate targeting rules + bucketing → O(number of rules, typically < 10)
   - Total: < 0.1ms (zero network)

3. REAL-TIME UPDATES:
   - SSE connection to Flag Service: /flags/stream?env=production
   - On event: fetch updated flag, replace in local cache
   - Connection drops → auto-reconnect with exponential backoff
   - While disconnected: continue evaluating from cached flags

4. GRACEFUL DEGRADATION:
   - Flag Service totally down → SDK uses last known good config
   - Never crashes or blocks: evaluation always returns a value
   - If flag not found in cache → return hardcoded default from code
```

### DD3: Targeting Rule Evaluation — Ordered Rules with Clauses

**Problem**: Complex targeting: "if region=US AND plan=enterprise → 100% enabled; if region=EU → 50% enabled; everyone else → 5%."

```
Rule evaluation (in order, first match wins):

for each rule in flag.rules:
  match = true
  for each clause in rule.clauses:  // AND logic
    if not evaluateClause(clause, userContext):
      match = false
      break
  if match:
    return evaluateRollout(rule.rollout, userContext)

// No rule matched → evaluate fallthrough
return evaluateRollout(flag.fallthrough, userContext)

Clause evaluation:
  clause: { attribute: "region", operator: "IN", values: ["US", "CA"] }
  userContext.attributes["region"] = "US"
  → "US" IN ["US", "CA"] → true

Operators: IN, NOT_IN, EQUALS, GREATER_THAN, LESS_THAN, CONTAINS, MATCHES_REGEX
  All evaluated in < 0.01ms (simple string/number comparisons)
```

### DD4: Change Propagation — SSE for < 10 Second Updates

**Problem**: Admin toggles kill switch. All 1000 services must stop showing the feature within 10 seconds.

```
Propagation pipeline:

1. Admin toggles flag OFF → Flag Service writes to DB + Kafka
2. SSE Relay consumes from Kafka → pushes SSE event to all 1000 SDKs
3. Each SDK receives event → fetches updated flag → updates local cache
4. Total: ~2-5 seconds (Kafka ~100ms + SSE push ~1s + SDK fetch ~1-3s)

SSE connection management:
  - 1000 SDK instances × 1 SSE connection each = 1000 persistent connections
  - Distributed across Flag Service pods (200 connections per pod × 5 pods)
  - HTTP/2 multiplexing: efficient for many concurrent SSE streams

Fallback if SSE fails:
  - SDK polls GET /flags every 30 seconds (backup)
  - Detects version change → full sync
  - Ensures eventual consistency even if SSE has issues

Kill switch SLA: < 10 seconds from toggle to all services using new value
  Worst case (SSE + poll): 30 seconds
```

### DD5: Consistency & Caching — Read-Heavy, Write-Rare

**Problem**: 1M evaluations/sec (reads) vs ~100 flag changes/day (writes). Read path must be zero-latency. Write path must propagate quickly.

```
Caching layers:

L1: SDK in-memory cache (EVERY service, < 0.1ms)
  - Full flag set cached in HashMap
  - Updated via SSE events (real-time)
  - Also persisted to disk (survives restart)
  - Consistency: eventual (< 10s after change)

L2: Redis (Flag Service, < 1ms)
  - Pre-computed flag configs per environment
  - Serves bootstrap requests (GET /flags)
  - Invalidated on flag change (Kafka consumer updates Redis)

L3: PostgreSQL (source of truth)
  - All flag configs, versions, rules
  - Read: only on Redis cache miss (rare)
  - Write: all flag changes

Write flow: Admin → PostgreSQL → Kafka → Redis + SSE → SDKs
Read flow: SDK local cache (0ms) → Redis (1ms) → PostgreSQL (10ms)

99.99% of reads: L1 hit (SDK local cache, zero network)
```

### DD6: Multi-Environment — Same Flag, Different Rules

**Problem**: Flag "new-checkout-flow" exists in dev, staging, production with different configs. Dev: 100% on. Staging: 50%. Production: 5%.

```
Data model: FlagConfig is per (flag_key, environment)

Storage:
  Key: flags:{flag_key}:{environment}
  Example: flags:new-checkout-flow:production → { enabled: true, rules: [...], version: 42 }

SDK initialization:
  SDK is configured with one environment: SDK.init(env="production")
  Only fetches/caches flags for that environment
  Evaluations only consider the configured environment's rules

Admin console:
  Toggle environment tabs: [dev] [staging] [production]
  Each tab shows independent flag config
  Can "promote" config: copy staging rules → production

Version tracking: per-flag-per-environment
  flags:new-checkout-flow:production version 42
  flags:new-checkout-flow:staging version 15
  Independent version counters, independent SSE streams per environment
```

### DD7: Resilience — Flag Service Down, SDKs Continue

**Problem**: If Flag Service goes down, all 1000 services must continue operating. Features should keep working with last known flag values.

```
Graceful degradation layers:

1. SDK has local in-memory cache → continues evaluating (0ms, no impact)
2. SDK has disk-persisted cache → survives service restart during outage
3. SDK reconnects to Flag Service with exponential backoff
4. Application code provides hardcoded defaults as ultimate fallback:
   if (featureFlags.isEnabled("new-checkout-flow", user, DEFAULT_FALSE)) { ... }

What happens during Flag Service outage:
  - Evaluations: ZERO impact (all from local cache)
  - Flag changes by admins: queued (applied when service recovers)
  - New service starts: uses disk cache or hardcoded defaults
  - Recovery: SDKs auto-reconnect, sync latest flags, resume SSE stream

Testing: "Chaos Flag Service" → deliberately kill Flag Service monthly
  Verify: all services continue operating with cached flags
```

### DD8: Observability — Flag Impressions & Debugging

**Problem**: After rolling out a flag, need to know: how many users are in each variation? Is the rollout working as expected? Why did user X get variation Y?

```
Flag impressions:
  SDK tracks: { flag_key, variation_index, user_id, timestamp }
  Batched and sent to analytics pipeline every 30 seconds
  Analytics DB: "new-checkout-flow: 95% control, 5% treatment, 50K evaluations/hour"

Evaluation debugger:
  API: POST /flags/{key}/debug-evaluate
  Request: { user: { user_id: "user_123", attributes: { region: "US" } } }
  Response: {
    "value": true,
    "variation_index": 0,
    "reason": "RULE_MATCH",
    "rule_id": "rule_001",
    "rule_description": "region IN [US, CA]",
    "bucket_value": 73,
    "rollout_applied": "73 < 90 (90% weight for variation 0)"
  }
  → Explains exactly WHY this user got this variation

Monitoring:
  - Metric: flag_evaluation_total{flag_key, variation, environment}
  - Alert: if flag "new-checkout-flow" treatment variation drops to 0% → something wrong
  - Dashboard: per-flag evaluation rate, variation distribution, error rate
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Evaluation** | SDK local cache (0ms, in-memory) | 1M evals/sec; zero network latency; no Flag Service dependency |
| **Bucketing** | Deterministic hash (MurmurHash3) | Same user always in same bucket; monotonic rollout expansion |
| **Propagation** | SSE push + Kafka | < 10s from change to all SDKs; real-time without polling |
| **Consistency** | Eventually consistent (< 10s) | Read-heavy (1M/sec) vs write-rare (100/day); stale flags are safe |
| **Resilience** | Disk-persisted cache + hardcoded defaults | Flag Service outage = zero impact on evaluation; graceful degradation |
| **Multi-env** | Per-flag-per-environment config | Same flag key, independent rules per env; safe promotion workflow |
| **Admin safety** | Audit log + approval workflows | Every change tracked; production changes require peer review |

## Interview Talking Points

1. **"SDK local cache: 0ms evaluation, 1M evals/sec, zero Flag Service dependency"** — SDK caches all flags in-memory HashMap. Evaluation = lookup + rule matching (< 0.1ms). No network call. If Flag Service is down → continues from cache.

2. **"Deterministic bucketing: hash(flag_key:user_id) % 100 for monotonic rollout"** — 10% → 20% only ADDS users, never removes. Same user always in same bucket. MurmurHash3 for uniform distribution.

3. **"SSE push for < 10s propagation from admin toggle to all SDKs"** — Flag change → Kafka → SSE Relay → 1000 SDK connections. Fallback: SDK polls every 30s. Kill switch SLA: < 10 seconds.

4. **"Ordered rule evaluation with AND clauses, first match wins"** — Rules evaluated top-to-bottom. Each rule has AND-combined clauses (attribute/operator/values). Rollout via weighted variations. Fallthrough if no rule matches.

5. **"Resilience: memory cache → disk cache → hardcoded defaults"** — Flag Service outage has ZERO impact on evaluation. SDK persists cache to disk. Application provides defaults as ultimate fallback.

6. **"Multi-environment: same flag key, independent configs per env"** — Dev (100% on), staging (50%), production (5%). Independent versions. Promote config between environments.

---

## 🔗 Related Problems
| Problem | Key Difference |
|---------|---------------|
| **LaunchDarkly / ConfigCat** | Our exact design |
| **A/B Testing Platform** | A/B testing adds experiment metrics; flags are the targeting mechanism |
| **Config Management** | Config is broader; feature flags are boolean/variation-specific |
| **i18n System** | Same CDN-first caching; i18n serves strings, flags serve boolean/variations |

## 🔧 Technology Alternatives
| Component | Chosen | Alternative |
|-----------|--------|------------|
| **SDK cache** | In-memory HashMap | Persistent key-value (RocksDB) |
| **Propagation** | SSE | WebSocket / gRPC streaming / Polling |
| **Flag store** | PostgreSQL | DynamoDB / etcd / Consul |
| **Bucketing** | MurmurHash3 | SHA-1 / CRC32 / FNV-1a |
| **Change streaming** | Kafka + SSE | Redis Pub/Sub / Firebase Realtime DB |

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Time**: 45-60 min
**References**: LaunchDarkly Architecture, Feature Flag Best Practices, Deterministic Bucketing Algorithms, SSE for Real-Time Config Propagation