# Global IP Address Blocking System - Hello Interview Framework

> **Question**: Design a distributed system that blocks requests from government-mandated IP addresses by integrating with government APIs to fetch blocked IP lists. The system should handle both IPv4 and IPv6 addresses, support region-specific blocking, and efficiently propagate updates across a global infrastructure within acceptable timeframes.
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
1. **Block by IP/CIDR**: Block individual IPs (`1.2.3.4`) and CIDR ranges (`10.0.0.0/8`, `2001:db8::/32`) at edge locations globally.
2. **Region-Specific Rules**: Different countries have different blocklists. IP `X` may be blocked in country A but allowed in country B.
3. **Government API Integration**: Ingest blocklists from government-provided APIs, curate/approve updates, then propagate to edge.
4. **IPv4 and IPv6**: Support both address families with efficient lookup for each.
5. **Near-Zero Added Latency**: IP check must add < 1ms to request processing at the edge.
6. **Propagation**: Updates from the control plane reach all edge locations within 60 seconds.

#### Nice to Have (P1)
- Rollback capability (revert a bad blocklist update instantly).
- Staged rollout (deploy to 1% of edge → 10% → 100%).
- Audit trail (who approved which blocklist change, when).
- Allowlist overrides (VIPs, internal IPs exempt from blocking).
- Analytics (block rate per region, top blocked CIDR ranges).

#### Below the Line (Out of Scope)
- DDoS mitigation (rate limiting is separate).
- URL/domain-level blocking (covered by our URL Blocking System).
- Deep packet inspection.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Lookup Latency** | < 1ms per IP check (p99) | Must not slow down any request |
| **Throughput** | 10M+ checks/sec globally | Every inbound request is checked |
| **Propagation** | < 60 seconds from approval to edge enforcement | Governments expect rapid enforcement |
| **Availability** | 99.999% (5 nines) | Blocking infra failure must not break the internet |
| **Accuracy** | Zero false negatives for exact IPs; zero false positives | Block exactly what's in the list, nothing more |
| **Scale** | 50M+ blocked IPs/CIDRs across all regions | Some blocklists are very large |
| **Failure Mode** | Configurable: fail-open or fail-closed per region | Different regions have different compliance needs |
| **Data Sovereignty** | Blocklist data stays within the applicable region | Legal compliance |

### Capacity Estimation

```
Blocklist Size:
  IPv4 individual IPs: ~10M globally
  IPv4 CIDR ranges: ~500K ranges (each covering many IPs)
  IPv6 prefixes: ~200K ranges
  Total entries: ~11M entries across all regions

Storage per edge node:
  Radix trie for IPv4: ~40 MB (32-bit trie, compressed)
  Radix trie for IPv6: ~80 MB (128-bit trie, compressed)
  Total per edge: ~120 MB (fits easily in memory)

Traffic:
  Edge locations: 200+ PoPs globally
  Requests per edge per second: 50K-500K
  Total global: 10M+ IP checks/sec
  Added latency per check: < 100 µs (in-memory trie lookup)

Updates:
  Government API polls: every 5 minutes per source
  Blocklist changes/day: ~10K-100K entries (mostly CIDR range updates)
  Propagation: Kafka → Regional Sync → Edge pull (< 60s)
```

---

## 2️⃣ Core Entities

### Entity 1: IPBlockRule
```sql
CREATE TABLE ip_block_rules (
    rule_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ip_or_cidr      CIDR NOT NULL,              -- '1.2.3.4/32' or '10.0.0.0/8' or '2001:db8::/32'
    ip_version      INT NOT NULL,               -- 4 or 6
    region_code     VARCHAR(10) NOT NULL,        -- 'US', 'CN', 'DE', 'GLOBAL'
    source          VARCHAR(100) NOT NULL,       -- 'GOV_US_OFAC', 'GOV_CN_MIIT', 'MANUAL'
    reason          TEXT,
    status          VARCHAR(20) DEFAULT 'ACTIVE', -- PENDING_APPROVAL, ACTIVE, REVOKED, EXPIRED
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    version         BIGINT DEFAULT 1
);

CREATE INDEX idx_rules_region ON ip_block_rules (region_code, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_rules_cidr ON ip_block_rules USING gist (ip_or_cidr inet_ops);
```

### Entity 2: GovernmentAPISource
```sql
CREATE TABLE gov_api_sources (
    source_id       UUID PRIMARY KEY,
    source_name     VARCHAR(100) NOT NULL,       -- 'US_OFAC', 'CN_MIIT', 'EU_SANCTIONS'
    api_url         TEXT NOT NULL,
    region_code     VARCHAR(10) NOT NULL,
    poll_interval_s INT DEFAULT 300,             -- Poll every 5 minutes
    auth_type       VARCHAR(20),                 -- API_KEY, OAUTH, MTLS
    last_polled_at  TIMESTAMPTZ,
    last_hash       VARCHAR(64),                 -- SHA-256 of last fetched list (detect changes)
    status          VARCHAR(20) DEFAULT 'ACTIVE'
);
```

### Entity 3: PolicyVersion (for rollback)
```sql
CREATE TABLE policy_versions (
    version_id      BIGSERIAL PRIMARY KEY,
    region_code     VARCHAR(10) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    total_rules     INT NOT NULL,
    checksum        VARCHAR(64) NOT NULL,        -- SHA-256 of entire rule set
    status          VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, ROLLED_BACK
    rollback_of     BIGINT                        -- Points to version this rolled back
);
```

---

## 3️⃣ API Design

### Admin APIs

#### 1. Check IP (Debugging)
```
GET /api/v1/check?ip=1.2.3.4&region=US

Response:
{
  "ip": "1.2.3.4",
  "blocked": true,
  "region": "US",
  "matched_rule": "1.2.3.0/24",
  "source": "GOV_US_OFAC",
  "reason": "OFAC sanctions list",
  "latency_us": 45
}
```

#### 2. Add IP Block Rule (Manual)
```
POST /api/v1/rules
{
  "ip_or_cidr": "10.20.30.0/24",
  "region_code": "US",
  "source": "MANUAL",
  "reason": "Incident response IR-2025-042"
}
```

#### 3. Rollback to Previous Version
```
POST /api/v1/regions/{region}/rollback
{
  "target_version": 1234,
  "reason": "Bad government API update at version 1235"
}
```

### Edge API (Internal, called by load balancers/proxies)

#### 4. IP Lookup (Hot Path)
```
// NOT an HTTP API — this is an in-memory function call at the edge
// Called for every inbound request by the edge proxy (Envoy/Nginx)

boolean isBlocked = ipBlockingModule.check(clientIp, edgeRegion);
// Returns in < 100 µs
// If blocked: return 403 Forbidden
// If not blocked: forward request normally
```

---

## 4️⃣ Data Flow

### Flow 1: Government API Polling → Blocklist Update
```
1. Government API Poller (scheduled every 5 min per source):
   GET https://api.ofac.gov/v1/blocked-ips
   ↓
2. Compare response hash with last_hash:
   If same → no changes, skip
   If different → compute diff (new IPs, removed IPs)
   ↓
3. Write diff to staging table (status = PENDING_APPROVAL)
   ↓
4. If auto-approve enabled: approve immediately
   If manual review required: notify admin, wait for approval
   ↓
5. On approval:
   a. Update ip_block_rules: status = ACTIVE
   b. Increment policy_version for the region
   c. Publish to Kafka "ip-blocklist-updates"
   ↓
6. Regional Sync Service consumes Kafka:
   a. Rebuild IP trie for the region
   b. Serialize trie to binary snapshot
   c. Upload snapshot to S3/GCS
   d. Increment edge_version counter in Redis
   ↓
7. Edge nodes poll for version changes (every 5s):
   If new version → download snapshot → hot-swap trie
   ↓
8. Enforcement active at edge within 60 seconds
```

### Flow 2: IP Check at Edge (Hot Path)
```
1. Inbound request arrives at edge PoP
   Client IP: 1.2.3.4
   Edge region: US
   ↓
2. Edge proxy (Envoy sidecar) calls IP blocking module:
   ipTrie.lookup(parseIpv4("1.2.3.4"))
   ↓
3. Radix trie traversal: 32 bit comparisons (IPv4)
   Match found: 1.2.3.0/24 is in the blocklist → BLOCKED
   No match: → ALLOWED
   ↓
4. If BLOCKED: return 403 Forbidden + block page
   If ALLOWED: forward to upstream
   ↓
   Total added latency: < 100 µs
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│               GOVERNMENT APIs (External)                         │
│  [US OFAC API]    [CN MIIT API]    [EU Sanctions API]           │
└──────────────────────┬───────────────────────────────────────────┘
                       │ Poll every 5 min
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│               CONTROL PLANE                                      │
│  ┌─────────────────┐  ┌────────────────┐  ┌──────────────────┐ │
│  │  Gov API Poller  │  │  Admin Service │  │  Approval        │ │
│  │  • Fetch lists   │  │  • Manual CRUD │  │  Workflow        │ │
│  │  • Compute diff  │  │  • Rollback    │  │  • Auto/Manual   │ │
│  │  • Stage changes │  │  • Audit log   │  │  • Staged rollout│ │
│  └────────┬─────────┘  └───────┬────────┘  └────────┬─────────┘ │
│           │                    │                     │           │
│           ▼                    ▼                     ▼           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  PostgreSQL (Source of Truth)                             │   │
│  │  • ip_block_rules, policy_versions, audit_log            │   │
│  └──────────────────────┬───────────────────────────────────┘   │
│                         │                                       │
│                         ▼                                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Kafka: "ip-blocklist-updates"                            │   │
│  └──────────────────────┬───────────────────────────────────┘   │
└─────────────────────────┼───────────────────────────────────────┘
                          │
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
┌────────────────┐ ┌─────────────┐ ┌─────────────┐
│ Regional Sync  │ │ Regional    │ │ Regional    │
│ (US)           │ │ Sync (EU)   │ │ Sync (APAC) │
│ • Build trie   │ │ • Build     │ │ • Build     │
│ • Snapshot→S3  │ │   trie      │ │   trie      │
│ • Version bump │ │ • Snapshot  │ │ • Snapshot   │
└───────┬────────┘ └──────┬──────┘ └──────┬──────┘
        │                 │               │
   ┌────┴────┐       ┌───┴────┐      ┌───┴────┐
   │Edge PoPs│       │Edge PoPs│     │Edge PoPs│
   │(US)     │       │(EU)    │     │(APAC)   │
   │         │       │        │     │         │
   │In-memory│       │In-mem  │     │In-mem   │
   │IPv4 Trie│       │IPv4/6  │     │IPv4/6   │
   │IPv6 Trie│       │Tries   │     │Tries    │
   │~120 MB  │       │~120 MB │     │~120 MB  │
   └─────────┘       └────────┘     └─────────┘
        ▲                 ▲               ▲
   Inbound traffic   Inbound traffic  Inbound traffic
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Gov API Poller** | Fetch blocklists from government APIs | Java scheduled service | 1 per source, HA pair |
| **Admin Service** | Manual CRUD, rollback, audit | Java/Spring Boot | Stateless |
| **PostgreSQL** | Source of truth for all rules + versions | PostgreSQL 15+ | Master + replicas |
| **Kafka** | Propagate blocklist changes to regions | Apache Kafka | Per-region topics |
| **Regional Sync** | Build IP tries, create snapshots | Java microservice | 1 per region |
| **S3/GCS** | Store trie snapshots for edge download | Cloud storage | Managed |
| **Edge Nodes** | In-memory IP trie, < 1ms lookup | Envoy + custom module / C++ | 200+ PoPs globally |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Radix Trie for IP/CIDR Matching

**The Problem**: We need to check if an IP address falls within any blocked CIDR range. Hash tables don't support prefix matching. We need a data structure that handles `10.0.0.0/8` matching `10.1.2.3`.

**Solution: Binary Radix Trie (Patricia Trie)**

```java
public class IpRadixTrie {
    
    static class TrieNode {
        TrieNode[] children = new TrieNode[2]; // 0 and 1 (binary)
        IPBlockRule rule = null;                // Non-null if this prefix is blocked
    }

    private final TrieNode root = new TrieNode();

    /**
     * Insert a CIDR block rule into the trie.
     * e.g., "10.0.0.0/8" → traverse 8 bits of the IP, mark node as blocked
     */
    public void insert(IPBlockRule rule) {
        byte[] ipBytes = parseIpToBytes(rule.getIpOrCidr()); // 4 bytes for IPv4, 16 for IPv6
        int prefixLen = parsePrefixLength(rule.getIpOrCidr()); // e.g., /8, /24, /32
        
        TrieNode current = root;
        for (int i = 0; i < prefixLen; i++) {
            int bit = getBit(ipBytes, i); // Get the i-th bit of the IP address
            if (current.children[bit] == null) {
                current.children[bit] = new TrieNode();
            }
            current = current.children[bit];
        }
        current.rule = rule; // Mark this prefix as blocked
    }

    /**
     * Check if an IP address is blocked.
     * Traverses the trie bit by bit. If any node along the path has a rule, the IP is blocked.
     * This handles CIDR matching: /8 blocks all IPs starting with those 8 bits.
     */
    public IPBlockRule lookup(byte[] ipBytes) {
        TrieNode current = root;
        IPBlockRule lastMatch = root.rule; // Check if root itself has a "block all" rule
        
        int maxBits = ipBytes.length * 8; // 32 for IPv4, 128 for IPv6
        for (int i = 0; i < maxBits; i++) {
            int bit = getBit(ipBytes, i);
            
            if (current.children[bit] == null) {
                break; // No more specific prefix in trie
            }
            current = current.children[bit];
            
            if (current.rule != null) {
                lastMatch = current.rule; // Most specific matching rule
            }
        }
        
        return lastMatch; // null if not blocked, non-null if blocked
    }

    /**
     * Get the i-th bit from an IP address byte array.
     * Bit 0 is the MSB of byte[0], bit 7 is the LSB of byte[0],
     * bit 8 is the MSB of byte[1], etc.
     */
    private int getBit(byte[] bytes, int bitIndex) {
        int byteIndex = bitIndex / 8;
        int bitOffset = 7 - (bitIndex % 8); // MSB first
        return (bytes[byteIndex] >> bitOffset) & 1;
    }
}

// Examples:
// insert("10.0.0.0/8")    → blocks all 10.x.x.x addresses
// insert("192.168.1.0/24") → blocks all 192.168.1.x addresses
// insert("1.2.3.4/32")     → blocks exactly 1.2.3.4
//
// lookup("10.1.2.3")       → matches 10.0.0.0/8 → BLOCKED
// lookup("192.168.1.100")  → matches 192.168.1.0/24 → BLOCKED
// lookup("8.8.8.8")        → no match → ALLOWED
//
// Performance: O(32) for IPv4, O(128) for IPv6 — constant time!
// Memory: ~40 MB for 10M IPv4 entries (compressed radix trie)
```

**Why Radix Trie Over Other Approaches?**
```
Hash Table (exact IP lookup only):
  ✗ Can't match CIDR ranges (10.0.0.0/8 would need 16M entries)
  ✗ Memory explosion for large CIDR blocks

Sorted Array + Binary Search:
  ✓ Can handle ranges
  ✗ O(log N) per lookup instead of O(1)
  ✗ Doesn't handle overlapping CIDRs naturally

Radix Trie:
  ✓ O(32) for IPv4, O(128) for IPv6 — effectively constant
  ✓ Natural CIDR matching (prefix = tree path)
  ✓ Handles overlapping rules (most specific wins)
  ✓ Compact: path compression reduces memory
  ✓ Immutable snapshot: thread-safe reads with atomic swap
```

---

### Deep Dive 2: IPv4 vs. IPv6 Handling

```java
public class DualStackIpChecker {
    private volatile IpRadixTrie ipv4Trie; // 32-bit addresses
    private volatile IpRadixTrie ipv6Trie; // 128-bit addresses

    /**
     * Check if an IP (v4 or v6) is blocked in the given region.
     */
    public BlockDecision check(String ipAddress, String region) {
        byte[] ipBytes;
        IpRadixTrie trie;

        if (isIpv4(ipAddress)) {
            ipBytes = parseIpv4(ipAddress);   // 4 bytes
            trie = ipv4Trie;
        } else {
            ipBytes = parseIpv6(ipAddress);   // 16 bytes
            trie = ipv6Trie;
            
            // Handle IPv4-mapped IPv6 (::ffff:1.2.3.4)
            if (isIpv4Mapped(ipBytes)) {
                byte[] v4Bytes = extractIpv4FromMapped(ipBytes);
                IPBlockRule v4Match = ipv4Trie.lookup(v4Bytes);
                if (v4Match != null) return BlockDecision.blocked(v4Match);
            }
        }

        IPBlockRule match = trie.lookup(ipBytes);
        return match != null ? BlockDecision.blocked(match) : BlockDecision.ALLOW;
    }

    /** Atomic swap: replace both tries simultaneously */
    public void updateTries(IpRadixTrie newIpv4, IpRadixTrie newIpv6) {
        this.ipv4Trie = newIpv4;
        this.ipv6Trie = newIpv6;
        // Old tries become eligible for GC once all in-flight requests finish
    }

    private boolean isIpv4(String ip) {
        return ip.contains(".") && !ip.contains(":");
    }

    private boolean isIpv4Mapped(byte[] ipv6Bytes) {
        // ::ffff:0:0/96 prefix indicates IPv4-mapped IPv6
        for (int i = 0; i < 10; i++) {
            if (ipv6Bytes[i] != 0) return false;
        }
        return ipv6Bytes[10] == (byte) 0xff && ipv6Bytes[11] == (byte) 0xff;
    }
}
```

---

### Deep Dive 3: Government API Integration & Approval Workflow

```java
public class GovernmentApiPoller {

    /**
     * Poll a government API for blocked IP list updates.
     * Runs every poll_interval_s (typically 5 minutes).
     */
    @Scheduled(fixedDelayString = "${gov.api.poll.interval:300000}")
    public void pollAllSources() {
        List<GovernmentApiSource> sources = sourceRepo.findAllActive();
        
        for (GovernmentApiSource source : sources) {
            try {
                pollSource(source);
            } catch (Exception e) {
                log.error("Failed to poll {}: {}", source.getSourceName(), e.getMessage());
                metrics.counter("gov_api.poll.error").tag("source", source.getSourceName()).increment();
            }
        }
    }

    private void pollSource(GovernmentApiSource source) {
        // 1. Fetch current blocklist
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(URI.create(source.getApiUrl()))
                .header("Authorization", getAuthHeader(source))
                .timeout(Duration.ofSeconds(30))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        // 2. Check if anything changed (hash comparison)
        String currentHash = sha256(response.body());
        if (currentHash.equals(source.getLastHash())) {
            log.debug("No changes from {}", source.getSourceName());
            return;
        }

        // 3. Parse the response into IP/CIDR rules
        List<String> newIpList = parseGovernmentResponse(response.body(), source.getSourceName());
        List<String> existingIps = ruleRepo.findActiveIpsBySource(source.getSourceName());

        // 4. Compute diff
        Set<String> toAdd = new HashSet<>(newIpList);
        toAdd.removeAll(existingIps);
        Set<String> toRemove = new HashSet<>(existingIps);
        toRemove.removeAll(newIpList);

        log.info("Source {}: +{} new, -{} removed", source.getSourceName(), toAdd.size(), toRemove.size());

        // 5. Stage changes
        if (source.isAutoApproveEnabled()) {
            applyChanges(source, toAdd, toRemove);
        } else {
            stageForApproval(source, toAdd, toRemove);
            notifyAdmins(source, toAdd.size(), toRemove.size());
        }

        // 6. Update last poll state
        source.setLastHash(currentHash);
        source.setLastPolledAt(Instant.now());
        sourceRepo.save(source);
    }
}
```

---

### Deep Dive 4: Rollback & Staged Rollout

```java
public class PolicyRollbackService {

    /**
     * Instantly rollback a region's blocklist to a previous version.
     * Used when a bad government API update causes false positives.
     */
    public void rollback(String regionCode, long targetVersionId, String reason) {
        PolicyVersion targetVersion = versionRepo.findById(targetVersionId)
            .orElseThrow(() -> new NotFoundException("Version not found"));
        
        // 1. Verify the target version belongs to this region
        if (!targetVersion.getRegionCode().equals(regionCode)) {
            throw new IllegalArgumentException("Version doesn't belong to this region");
        }
        
        // 2. Mark current version as rolled back
        PolicyVersion currentVersion = versionRepo.findActiveByRegion(regionCode);
        currentVersion.setStatus("ROLLED_BACK");
        versionRepo.save(currentVersion);
        
        // 3. Reactivate the target version's rules
        ruleRepo.deactivateAllForRegion(regionCode);
        ruleRepo.reactivateVersion(targetVersionId);
        
        // 4. Create a new version entry (for audit trail)
        PolicyVersion rollbackVersion = PolicyVersion.builder()
            .regionCode(regionCode)
            .totalRules(targetVersion.getTotalRules())
            .checksum(targetVersion.getChecksum())
            .status("ACTIVE")
            .rollbackOf(currentVersion.getVersionId())
            .build();
        versionRepo.save(rollbackVersion);
        
        // 5. Trigger immediate edge propagation
        kafka.send(new ProducerRecord<>("ip-blocklist-updates", regionCode,
            BlocklistUpdate.builder()
                .type("ROLLBACK")
                .regionCode(regionCode)
                .versionId(rollbackVersion.getVersionId())
                .build()));
        
        // 6. Audit log
        auditLog.record("ROLLBACK", regionCode, reason, 
            "from_version=" + currentVersion.getVersionId() + 
            " to_version=" + targetVersionId);
        
        log.warn("ROLLBACK: Region {} rolled back to version {}. Reason: {}", 
                 regionCode, targetVersionId, reason);
    }
}
```

**Staged Rollout**:
```
For risky updates (large diff, new government source):
  1. Deploy to 1% of edge nodes → monitor block rate for 5 min
  2. If block rate anomaly (>2x normal) → auto-rollback
  3. Deploy to 10% → monitor for 10 min
  4. Deploy to 100%
  
Implementation: edge nodes check their "canary group" flag
  canary_group = hash(edge_node_id) % 100
  If canary_group < rollout_percentage → use new version
  Else → keep old version
```

---

### Deep Dive 5: Edge Trie Snapshot & Hot-Swap

```java
public class EdgeTrieSyncService {
    private volatile DualStackIpChecker currentChecker;

    /**
     * Poll for trie updates every 5 seconds.
     * If a new version is available, download snapshot and hot-swap.
     */
    @Scheduled(fixedRate = 5_000)
    public void pollForUpdates() {
        long currentVersion = currentChecker.getVersion();
        long latestVersion = redis.get("edge_version:" + regionCode);
        
        if (latestVersion > currentVersion) {
            log.info("New trie version {} available (current: {})", latestVersion, currentVersion);
            
            // Download serialized trie snapshot from S3
            byte[] ipv4Snapshot = s3.download("ip-tries/" + regionCode + "/ipv4-v" + latestVersion + ".bin");
            byte[] ipv6Snapshot = s3.download("ip-tries/" + regionCode + "/ipv6-v" + latestVersion + ".bin");
            
            // Deserialize
            IpRadixTrie newIpv4 = IpRadixTrie.deserialize(ipv4Snapshot);
            IpRadixTrie newIpv6 = IpRadixTrie.deserialize(ipv6Snapshot);
            
            // Atomic hot-swap (readers on old trie finish naturally)
            DualStackIpChecker newChecker = new DualStackIpChecker(newIpv4, newIpv6, latestVersion);
            this.currentChecker = newChecker;
            
            log.info("Trie hot-swapped to version {}. IPv4: {} rules, IPv6: {} rules",
                     latestVersion, newIpv4.size(), newIpv6.size());
            metrics.gauge("edge.trie_version").set(latestVersion);
        }
    }
}
```

---

### Deep Dive 6: Fail-Open vs. Fail-Closed (Configurable)

```java
public class ConfigurableFailureMode {
    // Different regions have different compliance requirements
    private final Map<String, FailureMode> regionFailureModes = Map.of(
        "US", FailureMode.FAIL_OPEN,    // Better to allow than block everything
        "CN", FailureMode.FAIL_CLOSED,  // Government requires blocking even on failure
        "EU", FailureMode.FAIL_OPEN     // GDPR favors user access
    );

    public BlockDecision checkWithFailureMode(String ip, String region) {
        try {
            return ipChecker.check(ip, region);
        } catch (Exception e) {
            FailureMode mode = regionFailureModes.getOrDefault(region, FailureMode.FAIL_OPEN);
            if (mode == FailureMode.FAIL_CLOSED) {
                log.error("IP check failed, BLOCKING traffic (fail-closed): {}", e.getMessage());
                return BlockDecision.BLOCKED_BY_DEFAULT;
            } else {
                log.error("IP check failed, ALLOWING traffic (fail-open): {}", e.getMessage());
                return BlockDecision.ALLOW;
            }
        }
    }
}
```

---

### Deep Dive 7: Observability & Metrics

```java
public class IpBlockingMetrics {
    Counter checksTotal      = Counter.builder("ip.checks.total").register(registry);
    Counter blockedTotal     = Counter.builder("ip.checks.blocked").register(registry);
    Counter allowedTotal     = Counter.builder("ip.checks.allowed").register(registry);
    Timer   checkLatency     = Timer.builder("ip.check.latency_us").register(registry);
    Gauge   trieVersion      = Gauge.builder("ip.trie.version", ...).register(registry);
    Gauge   ipv4Rules        = Gauge.builder("ip.trie.ipv4_rules", ...).register(registry);
    Gauge   ipv6Rules        = Gauge.builder("ip.trie.ipv6_rules", ...).register(registry);
    Gauge   syncLagSeconds   = Gauge.builder("ip.sync.lag_seconds", ...).register(registry);
    Counter rollbacks        = Counter.builder("ip.rollbacks").register(registry);
    Counter govApiErrors     = Counter.builder("ip.gov_api.errors").register(registry);
}
```

**Key Alerts**:
```yaml
alerts:
  - name: HighSyncLag
    condition: ip.sync.lag_seconds > 120
    severity: CRITICAL
    message: "Edge IP blocklist >2 min stale"
    
  - name: BlockRateAnomaly
    condition: rate(ip.checks.blocked, 5m) change > 300%
    severity: CRITICAL
    message: "IP block rate spiked 3x. Possible bad government API update. Consider rollback."
    
  - name: GovApiPollFailure
    condition: ip.gov_api.errors > 3 consecutive
    severity: WARNING
    message: "Government API unreachable for 3+ polls"
```

---

## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **IP lookup** | Binary Radix Trie | O(32) IPv4, O(128) IPv6; natural CIDR matching |
| **Edge data** | In-memory trie (120 MB) | < 100µs lookup, no network round-trip |
| **Propagation** | Kafka → S3 snapshot → edge pull | Decoupled, reliable, < 60s |
| **Failure mode** | Configurable per region | US: fail-open, CN: fail-closed |
| **Rollback** | Version-based with instant Kafka propagation | Revert bad updates in < 60s |
| **Gov API** | Poll + hash comparison + approval workflow | Detect changes efficiently, human review |
| **IPv4/v6** | Separate tries + IPv4-mapped-v6 handling | Clean separation, correct edge cases |

## 🎯 Interview Talking Points

1. **"Radix trie is the only correct data structure"** — Hash tables can't do CIDR matching. Sorted arrays are O(log N). Radix trie is O(32) for IPv4 and naturally handles overlapping prefixes.
2. **"120 MB in memory at the edge"** — The entire global blocklist fits in RAM on every edge node. No network round-trip for any IP check. Added latency < 100µs.
3. **"Configurable fail-open/fail-closed"** — US prefers fail-open (allow on failure), China requires fail-closed. This is a policy decision, not a technical one.
4. **"Rollback in 60 seconds"** — Version-based snapshots mean we can revert a bad government API update by re-pointing to a previous trie snapshot.
5. **"Staged rollout prevents catastrophe"** — Deploy to 1% canary → 10% → 100%. Auto-rollback if block rate spikes > 2x.
6. **"Government API integration is the hardest part"** — Different formats, auth methods, reliability. Hash-based change detection avoids unnecessary processing.

---

### Deep Dive 8: Allowlist Overrides — VIPs & Internal IPs

**The Problem**: Some IPs must NEVER be blocked, even if they appear on a government list (e.g., internal monitoring systems, CDN origin servers, VIP customer IPs).

```java
public class AllowlistAwareIpChecker {
    private volatile IpRadixTrie allowlistTrie;  // Overrides blocklist
    private final DualStackIpChecker blockChecker;

    /**
     * Check IP with allowlist override.
     * Allowlist takes priority over blocklist.
     */
    public BlockDecision check(String ipAddress, String region) {
        byte[] ipBytes = parseIp(ipAddress);
        
        // 1. Check allowlist first (highest priority)
        IPAllowRule allowMatch = allowlistTrie.lookup(ipBytes);
        if (allowMatch != null) {
            metrics.counter("ip.checks.allowlisted").increment();
            return BlockDecision.ALLOW_OVERRIDE;
        }
        
        // 2. Check blocklist
        return blockChecker.check(ipAddress, region);
    }
}
```

**Allowlist Categories**:
```
┌───────────────────┬──────────────────────────────┬────────────────────────┐
│ Category           │ Example                      │ Priority               │
├───────────────────┼──────────────────────────────┼────────────────────────┤
│ Internal infra    │ 10.0.0.0/8, 172.16.0.0/12   │ Always allow (highest) │
│ CDN origin        │ Cloudflare IPs, Akamai IPs    │ Always allow           │
│ Monitoring        │ Health check IPs              │ Always allow           │
│ VIP customers     │ Enterprise customer egress IPs│ Allow unless manual    │
│ Partner APIs      │ Payment processor IPs         │ Allow unless manual    │
└───────────────────┴──────────────────────────────┴────────────────────────┘
```

---

### Deep Dive 9: CIDR Aggregation & Deduplication

**The Problem**: Government lists often contain redundant entries. `10.0.0.1/32`, `10.0.0.2/32`, ..., `10.0.0.255/32` should be aggregated to `10.0.0.0/24` for trie efficiency.

```java
public class CidrAggregator {
    
    /**
     * Aggregate a list of IP/CIDR entries into the minimal set of CIDR blocks.
     * Reduces trie size and improves memory efficiency.
     */
    public List<String> aggregate(List<String> cidrs) {
        // 1. Parse all CIDRs into (start, end) ranges
        List<IpRange> ranges = cidrs.stream()
            .map(this::cidrToRange)
            .sorted(Comparator.comparing(IpRange::getStart))
            .collect(Collectors.toList());
        
        // 2. Merge overlapping ranges
        List<IpRange> merged = new ArrayList<>();
        IpRange current = ranges.get(0);
        
        for (int i = 1; i < ranges.size(); i++) {
            IpRange next = ranges.get(i);
            if (next.getStart() <= current.getEnd() + 1) {
                // Overlapping or adjacent — merge
                current = new IpRange(current.getStart(), Math.max(current.getEnd(), next.getEnd()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        
        // 3. Convert merged ranges back to minimal CIDR notation
        List<String> result = new ArrayList<>();
        for (IpRange range : merged) {
            result.addAll(rangeToCidrs(range));
        }
        
        return result;
    }
    
    /**
     * Convert a contiguous range to the minimum set of CIDRs.
     * e.g., 10.0.0.0 - 10.0.0.255 → [10.0.0.0/24]
     * e.g., 10.0.0.0 - 10.0.1.127 → [10.0.0.0/24, 10.0.1.0/25]
     */
    private List<String> rangeToCidrs(IpRange range) {
        List<String> cidrs = new ArrayList<>();
        long start = range.getStart();
        long end = range.getEnd();
        
        while (start <= end) {
            // Find the largest prefix that fits
            int maxBits = 32;
            while (maxBits > 0) {
                long mask = ~((1L << (32 - maxBits + 1)) - 1);
                if ((start & mask) == start && (start | ~mask) <= end) {
                    maxBits--;
                } else {
                    break;
                }
            }
            cidrs.add(longToIp(start) + "/" + maxBits);
            start += (1L << (32 - maxBits));
        }
        
        return cidrs;
    }
}

// Example:
// Input:  ["10.0.0.1/32", "10.0.0.2/32", ..., "10.0.0.255/32", "10.0.0.0/32"]
// Output: ["10.0.0.0/24"]  (256 entries → 1 entry!)
//
// Input:  ["192.168.1.0/24", "192.168.1.128/25"]
// Output: ["192.168.1.0/24"]  (superset already covers the subset)
//
// Reduction ratio: typically 5-10x fewer trie entries after aggregation
```

---

### Deep Dive 10: Audit Trail & Compliance

```java
public class IpBlockAuditService {
    private final CassandraTemplate cassandra;
    
    /**
     * Record every blocklist change for compliance.
     * Immutable, append-only, with cryptographic hash chain.
     */
    public void recordChange(AuditEvent event) {
        String previousHash = getLastHash(event.getRegionCode());
        
        AuditRecord record = AuditRecord.builder()
            .recordId(TimeUUID.generate())
            .regionCode(event.getRegionCode())
            .action(event.getAction())       // ADD, REMOVE, ROLLBACK, APPROVE, REJECT
            .ipOrCidr(event.getIpOrCidr())
            .source(event.getSource())
            .performedBy(event.getPerformedBy())
            .reason(event.getReason())
            .policyVersion(event.getPolicyVersion())
            .timestamp(Instant.now())
            .previousHash(previousHash)
            .currentHash(sha256(previousHash + event.toString()))
            .build();
        
        cassandra.insert(record);
    }
    
    /**
     * Generate compliance report for auditors.
     * Shows all changes to a region's blocklist over a date range.
     */
    public ComplianceReport generateReport(String regionCode, LocalDate start, LocalDate end) {
        List<AuditRecord> records = cassandra.query(
            "SELECT * FROM ip_audit_log WHERE region_code = ? AND day >= ? AND day <= ?",
            regionCode, start, end);
        
        return ComplianceReport.builder()
            .regionCode(regionCode)
            .period(start + " to " + end)
            .totalChanges(records.size())
            .additions(records.stream().filter(r -> r.getAction().equals("ADD")).count())
            .removals(records.stream().filter(r -> r.getAction().equals("REMOVE")).count())
            .rollbacks(records.stream().filter(r -> r.getAction().equals("ROLLBACK")).count())
            .approvedBy(records.stream().map(AuditRecord::getPerformedBy).distinct().collect(Collectors.toSet()))
            .hashChainValid(verifyHashChain(records))
            .records(records)
            .build();
    }
    
    /** Verify the cryptographic hash chain is unbroken (no tampering) */
    private boolean verifyHashChain(List<AuditRecord> records) {
        for (int i = 1; i < records.size(); i++) {
            String expected = sha256(records.get(i - 1).getCurrentHash() + records.get(i).toString());
            if (!expected.equals(records.get(i).getCurrentHash())) {
                log.error("Hash chain broken at record {}", records.get(i).getRecordId());
                return false;
            }
        }
        return true;
    }
}
```

---

### Deep Dive 11: Performance Testing & Benchmarks

```java
public class IpBlockingBenchmark {
    
    /**
     * Benchmark radix trie lookup performance.
     * Must achieve < 100µs p99 with 10M+ rules loaded.
     */
    @Benchmark
    public void benchmarkIpv4Lookup(BenchmarkState state) {
        // Random IPv4 address
        byte[] ip = new byte[]{
            (byte) ThreadLocalRandom.current().nextInt(256),
            (byte) ThreadLocalRandom.current().nextInt(256),
            (byte) ThreadLocalRandom.current().nextInt(256),
            (byte) ThreadLocalRandom.current().nextInt(256)
        };
        state.ipv4Trie.lookup(ip);
    }
    
    @Benchmark
    public void benchmarkIpv6Lookup(BenchmarkState state) {
        byte[] ip = new byte[16];
        ThreadLocalRandom.current().nextBytes(ip);
        state.ipv6Trie.lookup(ip);
    }
}

// Expected results (JMH):
// 
// Benchmark                          Mode  Cnt    Score   Error  Units
// benchmarkIpv4Lookup (10M rules)    avgt   10   45.2 ±  2.1    ns/op
// benchmarkIpv6Lookup (200K rules)   avgt   10  112.5 ±  5.3    ns/op
// benchmarkIpv4Lookup (50M rules)    avgt   10   48.7 ±  3.0    ns/op
// 
// Notes:
// - IPv4 lookup is ~45ns regardless of rule count (O(32) fixed depth)
// - IPv6 is ~112ns (O(128) depth but rarely traverses all 128 bits)
// - Both are well under the 1ms (1,000,000 ns) target
// - 10M rules in IPv4 trie uses ~40 MB RAM (path-compressed)
```

---

### Deep Dive 12: Edge Deployment — Envoy Filter Integration

**The Problem**: The IP blocking logic needs to run at the very first hop — inside the edge proxy (Envoy/Nginx) — not as a separate HTTP call.

```java
// This would be implemented as an Envoy WASM filter or native C++ filter
// Here we show the Java equivalent for the logic

public class EnvoyIpBlockFilter {
    private volatile DualStackIpChecker checker;
    private final String edgeRegion;
    
    /**
     * Called by Envoy for every inbound request.
     * Must complete in < 100µs to avoid adding latency.
     */
    public FilterResult onRequest(HttpRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For"); // Or direct connection IP
        if (clientIp == null) {
            clientIp = request.getRemoteAddress();
        }
        
        // Parse to handle X-Forwarded-For chains: "client, proxy1, proxy2"
        String realClientIp = extractRealClientIp(clientIp);
        
        BlockDecision decision = checker.check(realClientIp, edgeRegion);
        
        if (decision.isBlocked()) {
            metrics.counter("ip.edge.blocked").increment();
            return FilterResult.reject(403, "Forbidden", 
                buildBlockPage(realClientIp, decision));
        }
        
        metrics.counter("ip.edge.allowed").increment();
        return FilterResult.CONTINUE;
    }
    
    /**
     * Extract the real client IP from X-Forwarded-For.
     * Trust only the rightmost IP added by our own infrastructure.
     */
    private String extractRealClientIp(String xForwardedFor) {
        if (xForwardedFor == null || xForwardedFor.isBlank()) return null;
        
        String[] ips = xForwardedFor.split(",");
        // The rightmost non-internal IP is the real client
        for (int i = ips.length - 1; i >= 0; i--) {
            String ip = ips[i].trim();
            if (!isInternalIp(ip)) {
                return ip;
            }
        }
        return ips[0].trim(); // Fallback: leftmost
    }
}
```

**Deployment Architecture**:
```
Envoy Proxy (edge PoP):
  ┌─────────────────────────────────────────┐
  │  Listener (port 443)                    │
  │  ↓                                      │
  │  TLS Termination                        │
  │  ↓                                      │
  │  IP Blocking Filter ← IpRadixTrie      │  ← In-process, < 100µs
  │  ↓                                      │
  │  Rate Limiting Filter                   │
  │  ↓                                      │
  │  Routing                                │
  │  ↓                                      │
  │  Upstream Connection                    │
  └─────────────────────────────────────────┘

The IP check happens INSIDE Envoy as a filter.
No HTTP call to an external service.
Trie is loaded into Envoy's memory space.
Hot-swapped via shared memory or file watch.
```

---

### Deep Dive 13: Regional Data Sovereignty & Isolation

```
Each region operates independently:

┌──────────────────────────────────────────────────────┐
│  US REGION (fully isolated)                           │
│                                                       │
│  Gov API Sources: OFAC, FBI, CISA                    │
│  PostgreSQL: us-east-1 (master) + us-west-2 (replica)│
│  Kafka: us-region topic                               │
│  S3: us-ip-tries bucket                               │
│  Edge PoPs: 50+ US locations                          │
│                                                       │
│  US blocklist data NEVER leaves US infrastructure     │
│  US admins can only see/modify US rules               │
│  US edge nodes only download US trie snapshots        │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│  EU REGION (fully isolated)                           │
│                                                       │
│  Gov API Sources: EU Sanctions, Europol               │
│  PostgreSQL: eu-west-1 (master) + eu-central-1 (rep) │
│  Kafka: eu-region topic                               │
│  S3: eu-ip-tries bucket                               │
│  Edge PoPs: 40+ EU locations                          │
│                                                       │
│  GDPR-compliant: EU data stays in EU                  │
│  EU admins can only see/modify EU rules               │
└──────────────────────────────────────────────────────┘

Central metadata plane (no blocklist data):
  - Region health status
  - Version numbers
  - Cross-region coordination signals
  - No actual IP rules stored centrally
```

---

### Deep Dive 14: Analytics & Block Rate Monitoring

```java
public class BlockAnalyticsService {
    
    /** Real-time block rate dashboard, updated every 10 seconds */
    @Scheduled(fixedRate = 10_000)
    public void computeBlockStats() {
        for (String region : activeRegions) {
            long totalChecks = metrics.counter("ip.checks.total").count();
            long totalBlocked = metrics.counter("ip.checks.blocked").count();
            double blockRate = (double) totalBlocked / Math.max(1, totalChecks);
            
            // Top blocked CIDRs (maintained in Redis sorted set)
            Set<Tuple> topBlocked = redis.zrevrangeWithScores(
                "analytics:top_blocked:" + region, 0, 9);
            
            BlockAnalytics analytics = BlockAnalytics.builder()
                .region(region)
                .totalChecks(totalChecks)
                .totalBlocked(totalBlocked)
                .blockRate(blockRate)
                .topBlockedCidrs(topBlocked.stream()
                    .map(t -> new CidrCount(t.getElement(), (long) t.getScore()))
                    .collect(Collectors.toList()))
                .computedAt(Instant.now())
                .build();
            
            redis.setex("analytics:dashboard:" + region, 30, 
                        objectMapper.writeValueAsString(analytics));
            
            // Anomaly detection: if block rate spikes, trigger alert
            Double previousRate = previousBlockRates.get(region);
            if (previousRate != null && blockRate > previousRate * 3) {
                alertService.send(Severity.CRITICAL,
                    "Block rate anomaly in " + region + ": " + 
                    String.format("%.4f → %.4f (3x increase)", previousRate, blockRate));
            }
            previousBlockRates.put(region, blockRate);
        }
    }

    /** Track which CIDRs are blocking the most traffic */
    public void onIpBlocked(String matchedCidr, String region) {
        redis.zincrby("analytics:top_blocked:" + region, 1, matchedCidr);
    }
}
```

**Analytics Dashboard**:
```
┌─────────────────────────────────────────────────────┐
│          IP BLOCKING ANALYTICS — US Region            │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Checks: 2.1M/sec  │  Blocked: 12,345/sec (0.59%)  │
│  Trie Version: 4521 │  Rules: 3.2M IPv4, 180K IPv6  │
│  Sync Lag: 3 sec    │  Last Gov Update: 12 min ago   │
│                                                      │
│  Top Blocked CIDRs (last 1h):                        │
│  1. 45.33.0.0/16    → 234,567 blocks (sanctions)    │
│  2. 185.220.100.0/24 → 156,789 blocks (tor exit)   │
│  3. 91.109.0.0/16   → 98,432 blocks (OFAC)         │
│                                                      │
│  Block Rate Trend (last 24h):                        │
│  ▁▁▁▂▂▃▃▃▂▂▂▃▃▃▃▂▂▂▁▁▁▁▁▁                        │
│  Stable — no anomalies detected                      │
│                                                      │
└─────────────────────────────────────────────────────┘
```

---

**References**:
- Cloudflare WAF / IP Access Rules
- AWS WAF IP Sets
- Linux kernel `ip_set` and `iptables` CIDR matching
- Patricia Trie (Morrison, 1968)

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Deep Dives**: 14 topics
