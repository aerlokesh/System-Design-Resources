# Global URL Blocking System Design - Hello Interview Framework

> **Question**: Design a distributed system that provides blocked URLs globally. The system should adhere to a list of blocked URLs provided by various governments and ensure that users in those regions cannot access blocked content. The system must handle billions of URL checks per day with minimal latency.
>
> **Submitted by**: Alex Ilyenko | **Level**: Senior
>
> **Difficulty**: Hard | **Companies**: Cloudflare, Akamai, ISPs, Government-mandated filtering

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
1. **URL Blocking**: The system should check whether a given URL is blocked in a specific country/region and return a block decision in real-time.
2. **Blocklist Management**: Government authorities / admins should be able to add, remove, and update blocked URLs per region.
3. **Regional Policies**: Different countries have different blocklists. A URL blocked in Country A may be accessible in Country B.
4. **Wildcard / Pattern Matching**: Support blocking entire domains (`*.gambling-site.com`), specific paths (`example.com/illegal-content`), and regex-based patterns.
5. **Block Page Response**: When a URL is blocked, users should see a customizable block page explaining why access is denied.

#### Nice to Have (P1)
- Audit log of all blocklist changes (who added/removed, when, reason).
- Temporary blocks with auto-expiration (TTL-based).
- Category-based blocking (gambling, adult content, piracy).
- User appeal/override workflow.
- Analytics dashboard showing block rates per region, top blocked domains.

#### Below the Line (Out of Scope)
- Deep packet inspection (DPI) of encrypted traffic.
- VPN/proxy detection and bypass prevention.
- Content filtering within pages (only URL-level blocking).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Throughput** | 1M+ URL checks/sec globally (100B+ checks/day) | Every HTTP request from users in covered regions |
| **Latency** | < 5ms for URL lookup (p99) | Must not noticeably slow down browsing |
| **Availability** | 99.999% (5 nines) | Blocking infra failure = either everything is blocked or nothing is |
| **Consistency** | Eventual consistency, < 60 second propagation | New blocks should take effect within 1 minute globally |
| **Freshness** | Blocklist updates propagated in < 60 seconds | Governments expect rapid enforcement |
| **Scale** | 10M+ blocked URLs across all regions | Some countries have very large blocklists |
| **Fault Tolerance** | Fail-open (allow access) on system failure | Better to miss a block than to block all traffic |
| **Data Sovereignty** | Blocklist data stays within the region it applies to | Legal compliance |

### Capacity Estimation

#### Traffic Estimates
```
URL Check Requests:
  Global internet users in regulated regions: ~2 billion
  Average requests per user per day: 50-100
  Total: 100-200 billion URL checks/day
  → ~1.2M - 2.3M checks/sec sustained
  Peak (3x): ~7M checks/sec

Blocklist Updates:
  ~1,000-10,000 updates/day globally (batch + individual)
  Very low write volume compared to reads
  Read:Write ratio ≈ 10,000,000:1 (extremely read-heavy)
```

#### Storage Estimates
```
Blocklist Entries:
  10M blocked URLs globally
  Average URL: 100 bytes
  Metadata per entry: 200 bytes (region, reason, category, timestamps)
  Total per entry: ~300 bytes
  
  Raw blocklist: 10M × 300 bytes = 3 GB
  With indices + bloom filters: ~10 GB
  Per-region subsets: ~500 MB - 2 GB each (highly variable)

Bloom Filter (per region):
  1M URLs, 0.1% false positive rate
  → ~1.7 MB per bloom filter
  200 regions × 1.7 MB = 340 MB (trivially small)
```

#### Memory Estimates (per edge node)
```
Bloom Filter:          2 MB (for the region it serves)
Hot URL Cache (LRU):   500 MB (top 10M most-checked URLs)
Trie/Radix Tree:       1-2 GB (for exact + prefix matching)
Total per edge node:   ~2-3 GB RAM (very manageable)
```

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│  BlocklistEntry   │ *───1 │  Region           │ 1───* │  BlocklistRule   │
├──────────────────┤       ├──────────────────┤       ├──────────────────┤
│ entry_id (PK)    │       │ region_code (PK)  │       │ rule_id (PK)     │
│ url_pattern      │       │ name              │       │ pattern_type     │
│ pattern_type     │       │ default_action    │       │ compiled_regex   │
│ region_code (FK) │       │ last_sync_at      │       │ priority         │
│ category         │       │ bloom_filter_ver  │       └──────────────────┘
│ reason           │       └──────────────────┘
│ status           │       ┌──────────────────┐
│ created_by       │       │  AuditLog         │
│ created_at       │       ├──────────────────┤
│ expires_at       │       │ log_id (PK)       │
│ updated_at       │       │ entry_id (FK)     │
└──────────────────┘       │ action            │
                           │ performed_by      │
                           │ timestamp         │
                           │ reason            │
                           └──────────────────┘
```

### Entity 1: BlocklistEntry
```sql
CREATE TABLE blocklist_entries (
    entry_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_pattern     TEXT NOT NULL,              -- e.g., '*.gambling.com', 'piracy.org/downloads/*'
    pattern_type    VARCHAR(20) NOT NULL,       -- EXACT, PREFIX, SUFFIX, WILDCARD, REGEX
    region_code     VARCHAR(10) NOT NULL,       -- ISO 3166-1 alpha-2: 'US', 'CN', 'DE', 'GLOBAL'
    category        VARCHAR(50),               -- GAMBLING, ADULT, PIRACY, TERRORISM, GOVERNMENT
    reason          TEXT,                       -- 'Court order #12345', 'National security'
    status          VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, REVOKED
    block_page_url  TEXT,                       -- Custom block page for this entry
    priority        INT DEFAULT 100,           -- Lower = higher priority (for conflict resolution)
    created_by      VARCHAR(100) NOT NULL,     -- Admin ID / Government authority
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,               -- NULL = permanent
    version         BIGINT DEFAULT 1,          -- Optimistic locking

    CONSTRAINT uk_url_region UNIQUE (url_pattern, region_code)
);

CREATE INDEX idx_blocklist_region ON blocklist_entries (region_code, status)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_blocklist_pattern ON blocklist_entries USING gin (url_pattern gin_trgm_ops);
CREATE INDEX idx_blocklist_expires ON blocklist_entries (expires_at)
    WHERE expires_at IS NOT NULL AND status = 'ACTIVE';
```

### Entity 2: Region Configuration
```sql
CREATE TABLE regions (
    region_code     VARCHAR(10) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    default_action  VARCHAR(10) DEFAULT 'ALLOW',  -- ALLOW or BLOCK (for fail-safe)
    total_entries   BIGINT DEFAULT 0,
    bloom_version   BIGINT DEFAULT 0,              -- Incremented on every blocklist change
    last_sync_at    TIMESTAMPTZ,
    config          JSONB                          -- Region-specific settings
);
```

### Entity 3: Audit Log
```sql
-- Append-only audit trail (Cassandra or PostgreSQL)
CREATE TABLE audit_log (
    log_id          TIMEUUID PRIMARY KEY,
    entry_id        UUID NOT NULL,
    action          VARCHAR(20) NOT NULL,          -- CREATED, UPDATED, REVOKED, EXPIRED
    performed_by    VARCHAR(100) NOT NULL,
    region_code     VARCHAR(10) NOT NULL,
    old_value       JSONB,
    new_value       JSONB,
    reason          TEXT,
    timestamp       TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 3️⃣ API Design

### Admin APIs (Blocklist Management)

#### 1. Add URL to Blocklist
```
POST /api/v1/blocklist

Headers:
  Authorization: Bearer <ADMIN_JWT>
  X-Region: CN

Request:
{
  "url_pattern": "*.gambling-site.com",
  "pattern_type": "WILDCARD",
  "region_code": "CN",
  "category": "GAMBLING",
  "reason": "Ministry of Culture Directive #2025-042",
  "expires_at": null,
  "block_page_url": "https://block.gov.cn/gambling"
}

Response (201 Created):
{
  "entry_id": "a1b2c3d4-...",
  "url_pattern": "*.gambling-site.com",
  "region_code": "CN",
  "status": "ACTIVE",
  "estimated_propagation_seconds": 30,
  "created_at": "2025-01-08T10:00:00Z"
}
```

#### 2. Remove / Revoke URL from Blocklist
```
DELETE /api/v1/blocklist/{entry_id}

Request:
{
  "reason": "Court order revoked per appeal #789"
}

Response (200 OK):
{
  "entry_id": "a1b2c3d4-...",
  "status": "REVOKED",
  "revoked_at": "2025-01-08T15:00:00Z"
}
```

#### 3. Bulk Import Blocklist
```
POST /api/v1/blocklist/bulk

Request:
{
  "region_code": "DE",
  "entries": [
    { "url_pattern": "piracy-movies.com", "pattern_type": "EXACT", "category": "PIRACY" },
    { "url_pattern": "*.illegal-downloads.net", "pattern_type": "WILDCARD", "category": "PIRACY" }
  ],
  "reason": "BPjM Index update 2025-Q1"
}

Response (202 Accepted):
{
  "import_id": "import-xyz",
  "total_entries": 2,
  "status": "PROCESSING",
  "estimated_completion_seconds": 5
}
```

#### 4. Search Blocklist
```
GET /api/v1/blocklist?region=CN&category=GAMBLING&status=ACTIVE&limit=50

Response (200 OK):
{
  "entries": [ ... ],
  "total": 1234,
  "pagination": { "next_cursor": "..." }
}
```

### Edge/Lookup API (URL Check)

#### 5. Check URL (High-Throughput Path)
```
GET /api/v1/check?url=https://gambling-site.com/poker&region=CN

Response (200 OK — NOT BLOCKED):
{
  "blocked": false,
  "url": "https://gambling-site.com/poker",
  "region": "CN",
  "latency_ms": 2
}

Response (200 OK — BLOCKED):
{
  "blocked": true,
  "url": "https://gambling-site.com/poker",
  "region": "CN",
  "matched_rule": "*.gambling-site.com",
  "category": "GAMBLING",
  "block_page_url": "https://block.gov.cn/gambling",
  "reason": "Ministry of Culture Directive #2025-042",
  "latency_ms": 1
}
```

> **Note**: In production, this is typically a DNS-level or proxy-level check, not an HTTP API call. The API is for testing/debugging. Real checks happen via DNS sinkhole or inline proxy.

---

## 4️⃣ Data Flow

### Flow 1: URL Check (Happy Path — Not Blocked)
```
1. User requests: https://example.com/page
   ↓
2. DNS Resolver / ISP Proxy intercepts the request
   ↓
3. Edge Node (local to user's region) checks URL:
   a. In-memory Bloom Filter check: "Is this URL POSSIBLY blocked?"
      → Bloom Filter says NO → DEFINITELY not blocked
      → Return: ALLOW (< 1ms)
   ↓
4. User accesses the page normally
   Total latency added: < 1ms
```

### Flow 2: URL Check (Blocked URL)
```
1. User requests: https://gambling-site.com/poker
   ↓
2. Edge Node Bloom Filter check:
   → Bloom Filter says MAYBE → need to verify
   ↓
3. Edge Node checks local LRU Cache:
   → Cache HIT: URL is confirmed blocked
   → Return: BLOCK + block page redirect
   ↓
   OR
   → Cache MISS: Query local Trie/Radix Tree
   ↓
4. Trie lookup for pattern matching:
   a. Exact match: "gambling-site.com/poker" → NO
   b. Wildcard match: "*.gambling-site.com" → YES ✓
   ↓
5. Return BLOCK decision:
   - HTTP 302 redirect to block page
   - Or DNS NXDOMAIN / sinkhole IP
   ↓
6. User sees block page: "This site is blocked in your region"
   Total latency added: < 5ms
```

### Flow 3: Admin Adds New Blocked URL
```
1. Admin calls: POST /api/v1/blocklist
   { "url_pattern": "new-illegal-site.com", "region_code": "CN" }
   ↓
2. Central API Service:
   - Validates URL pattern
   - Checks for duplicates
   - Writes to PostgreSQL (source of truth)
   - Writes to Audit Log
   ↓
3. Publish to Kafka topic "blocklist-updates":
   {
     "action": "ADD",
     "entry_id": "...",
     "url_pattern": "new-illegal-site.com",
     "region_code": "CN",
     "version": 12345
   }
   ↓
4. Regional Sync Service (in CN region) consumes message:
   - Updates local region database
   - Rebuilds Bloom Filter (incremental)
   - Updates Trie data structure
   - Increments bloom_version
   ↓
5. Edge Nodes in CN poll for updates (every 5-10 seconds):
   "Has bloom_version changed since my last sync?"
   → YES: Pull delta updates
   → Update local Bloom Filter + Trie + Cache
   ↓
6. New block is effective globally in < 60 seconds
```

### Flow 4: Bloom Filter False Positive Handling
```
1. User requests: https://legitimate-site.com
   ↓
2. Bloom Filter says: MAYBE blocked (false positive!)
   ↓
3. Edge Node checks local Trie/DB:
   → No match found → NOT blocked (false positive confirmed)
   ↓
4. Cache the negative result:
   LRU Cache: "legitimate-site.com" → NOT_BLOCKED (TTL: 1 hour)
   ↓
5. Future checks for this URL skip Trie lookup
   Total latency: < 5ms (slightly slower than non-bloom-hit)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         ADMIN / GOVERNMENT                              │
│   [Admin Dashboard]    [Government API]    [Bulk Import Tool]           │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
                 ┌──────────────────────────┐
                 │   Central Admin Service   │
                 │  • CRUD blocklist entries │
                 │  • Validate patterns      │
                 │  • Audit logging          │
                 └─────────────┬────────────┘
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
          ┌──────────────────┐  ┌─────────────────┐
          │   PostgreSQL      │  │   Kafka          │
          │   (Source of      │  │   "blocklist-    │
          │    Truth)         │  │    updates"      │
          │   • 10M entries   │  │   • CDC stream   │
          │   • Audit log     │  │   • Per-region   │
          └──────────────────┘  └────────┬────────┘
                                         │
                    ┌────────────────────┬┴───────────────────┐
                    ▼                    ▼                     ▼
          ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
          │  Regional Sync    │ │  Regional Sync    │ │  Regional Sync    │
          │  Service (CN)     │ │  Service (EU)     │ │  Service (US)     │
          │  • Consume Kafka  │ │  • Consume Kafka  │ │  • Consume Kafka  │
          │  • Build Bloom    │ │  • Build Bloom    │ │  • Build Bloom    │
          │  • Build Trie     │ │  • Build Trie     │ │  • Build Trie     │
          │  • Serve deltas   │ │  • Serve deltas   │ │  • Serve deltas   │
          └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
                   │                    │                     │
          ┌────────┴─────────┐ ┌────────┴─────────┐ ┌────────┴─────────┐
          │  Edge Nodes (CN)  │ │  Edge Nodes (EU)  │ │  Edge Nodes (US)  │
          │  ┌─────┐┌─────┐  │ │  ┌─────┐┌─────┐  │ │  ┌─────┐┌─────┐  │
          │  │Edge ││Edge │  │ │  │Edge ││Edge │  │ │  │Edge ││Edge │  │
          │  │ 1   ││ 2   │  │ │  │ 1   ││ 2   │  │ │  │ 1   ││ 2   │  │
          │  └──┬──┘└──┬──┘  │ │  └──┬──┘└──┬──┘  │ │  └──┬──┘└──┬──┘  │
          │     │      │     │ │     │      │     │ │     │      │     │
          │  In-memory:       │ │  In-memory:       │ │  In-memory:       │
          │  • Bloom Filter   │ │  • Bloom Filter   │ │  • Bloom Filter   │
          │  • Trie/Radix     │ │  • Trie/Radix     │ │  • Trie/Radix     │
          │  • LRU Cache      │ │  • LRU Cache      │ │  • LRU Cache      │
          └──────────────────┘ └──────────────────┘ └──────────────────┘
                   ▲                    ▲                     ▲
                   │                    │                     │
          ┌────────┴─────────┐ ┌────────┴─────────┐ ┌────────┴─────────┐
          │  Users (China)    │ │  Users (Europe)   │ │  Users (USA)      │
          │  DNS / ISP Proxy  │ │  DNS / ISP Proxy  │ │  DNS / ISP Proxy  │
          └──────────────────┘ └──────────────────┘ └──────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Admin Service** | CRUD blocklist, validation, audit | Java/Spring Boot | Horizontal, low traffic |
| **PostgreSQL** | Source of truth for blocklist entries | PostgreSQL 15+ | Master + read replicas |
| **Kafka** | CDC stream of blocklist changes | Apache Kafka | Per-region topics |
| **Regional Sync Service** | Build & serve Bloom Filters + Tries per region | Java microservice | 1 per region, HA pair |
| **Edge Nodes** | Ultra-fast URL lookup, < 5ms | Custom C++/Rust/Java | 100s-1000s per region, at ISP/CDN PoPs |
| **DNS Sinkhole** | Block at DNS level (primary mechanism) | CoreDNS / BIND | Integrated with ISP resolvers |
| **Block Page Server** | Serve customizable block pages | Nginx + CDN | Static content, CDN-cached |

### Why This Architecture?

1. **Bloom Filter first**: Eliminates 99.9% of lookups in < 1ms (most URLs aren't blocked).
2. **Edge-local data**: No network round-trip for URL checks. Everything is in-memory on the edge.
3. **Regional isolation**: Each region only stores its own blocklist. Data sovereignty preserved.
4. **Eventual consistency is fine**: A 30-60 second delay for new blocks is acceptable; we're not doing real-time trading.
5. **Fail-open**: If edge nodes crash, traffic flows unblocked (safer than blocking all traffic).

---

## 6️⃣ Deep Dives

### Deep Dive 1: Bloom Filter for Ultra-Fast URL Lookups

**The Problem**: Checking 10M+ URLs for every single HTTP request is too slow with a hash table lookup, especially at 1M+ requests/sec per edge node.

**Bloom Filter Primer**:
```
A Bloom Filter is a space-efficient probabilistic data structure that answers:
  "Is this element IN the set?" 
  
Possible answers:
  - "DEFINITELY NOT in the set" → 100% accurate, zero false negatives
  - "POSSIBLY in the set"       → May be a false positive (configurable rate)

Properties:
  - O(1) lookup time (k hash functions, k bit checks)
  - Very compact: 1M elements at 0.1% FPR = 1.7 MB
  - No deletions (use Counting Bloom Filter or rebuild)
  - Cannot return the matched entry (just yes/no)
```

**Implementation**:

```java
public class UrlBloomFilter {
    private final BitSet bitSet;
    private final int size;
    private final int numHashFunctions;

    /**
     * Create a Bloom Filter for the expected number of URLs.
     * @param expectedElements Number of blocked URLs (e.g., 1,000,000)
     * @param falsePositiveRate Desired FPR (e.g., 0.001 = 0.1%)
     */
    public UrlBloomFilter(int expectedElements, double falsePositiveRate) {
        // Optimal size: m = -n*ln(p) / (ln(2))^2
        this.size = (int) Math.ceil(
            -expectedElements * Math.log(falsePositiveRate) / Math.pow(Math.log(2), 2)
        );
        // Optimal hash functions: k = (m/n) * ln(2)
        this.numHashFunctions = (int) Math.round(
            ((double) size / expectedElements) * Math.log(2)
        );
        this.bitSet = new BitSet(size);
    }

    public void add(String url) {
        String normalized = normalizeUrl(url);
        for (int i = 0; i < numHashFunctions; i++) {
            int hash = murmur3Hash(normalized, i) % size;
            bitSet.set(Math.abs(hash));
        }
    }

    /**
     * Returns true if the URL is POSSIBLY blocked (may be false positive).
     * Returns false if the URL is DEFINITELY NOT blocked.
     */
    public boolean mightContain(String url) {
        String normalized = normalizeUrl(url);
        for (int i = 0; i < numHashFunctions; i++) {
            int hash = murmur3Hash(normalized, i) % size;
            if (!bitSet.get(Math.abs(hash))) {
                return false; // Definitely not in the set
            }
        }
        return true; // Possibly in the set
    }

    private String normalizeUrl(String url) {
        // Remove protocol, trailing slash, lowercase, decode percent-encoding
        return url.toLowerCase()
                  .replaceFirst("^https?://", "")
                  .replaceFirst("/$", "");
    }

    private int murmur3Hash(String data, int seed) {
        return Hashing.murmur3_32_fixed(seed)
                      .hashString(data, StandardCharsets.UTF_8)
                      .asInt();
    }
}

// Usage:
// UrlBloomFilter filter = new UrlBloomFilter(1_000_000, 0.001);
// filter.add("gambling-site.com");
// filter.mightContain("gambling-site.com")  → true
// filter.mightContain("google.com")         → false (almost certainly)
```

**Sizing Table**:
```
┌──────────────┬───────────┬────────────┬──────────────┐
│ Blocked URLs │ FPR       │ Memory     │ Hash Funcs   │
├──────────────┼───────────┼────────────┼──────────────┤
│ 100,000      │ 0.1%      │ 170 KB     │ 10           │
│ 1,000,000    │ 0.1%      │ 1.7 MB     │ 10           │
│ 1,000,000    │ 0.01%     │ 2.3 MB     │ 13           │
│ 10,000,000   │ 0.1%      │ 17 MB      │ 10           │
│ 10,000,000   │ 0.01%     │ 23 MB      │ 13           │
└──────────────┴───────────┴────────────┴──────────────┘
```

---

### Deep Dive 2: Trie / Radix Tree for Pattern Matching

**The Problem**: Bloom Filters only support exact membership checks. We need wildcard matching (`*.gambling.com`), prefix matching (`piracy.org/downloads/*`), and suffix matching.

**Solution: Reversed-Domain Trie**

```java
public class UrlBlockingTrie {
    
    static class TrieNode {
        Map<String, TrieNode> children = new HashMap<>();
        BlocklistEntry blockEntry = null;  // non-null if this node is a blocked endpoint
        boolean isWildcard = false;        // true if "*.domain" pattern
    }

    private final TrieNode root = new TrieNode();

    /**
     * Insert a blocklist entry into the trie.
     * Domain is reversed for efficient suffix matching:
     *   "*.gambling.com" → stored as "com.gambling.*"
     *   "evil.org/bad-path" → stored as "org.evil/bad-path"
     */
    public void insert(BlocklistEntry entry) {
        String[] parts = reverseAndSplit(entry.getUrlPattern());
        TrieNode current = root;
        
        for (String part : parts) {
            if (part.equals("*")) {
                current.isWildcard = true;
                current.blockEntry = entry;
                return;
            }
            current.children.putIfAbsent(part, new TrieNode());
            current = current.children.get(part);
        }
        current.blockEntry = entry;
    }

    /**
     * Check if a URL matches any blocked pattern.
     * Returns the matching entry or null if not blocked.
     */
    public BlocklistEntry lookup(String url) {
        String[] parts = reverseAndSplit(normalizeUrl(url));
        TrieNode current = root;

        for (String part : parts) {
            // Check wildcard at current level
            if (current.isWildcard) {
                return current.blockEntry;  // Wildcard matches everything below
            }
            
            TrieNode next = current.children.get(part);
            if (next == null) {
                return null;  // No match
            }
            current = next;
        }

        // Check if the final node has a block entry (exact match)
        if (current.blockEntry != null) {
            return current.blockEntry;
        }
        // Check if there's a wildcard child (matches subpaths)
        if (current.isWildcard) {
            return current.blockEntry;
        }
        return null;
    }

    private String[] reverseAndSplit(String url) {
        // "www.gambling.com/poker" → ["com", "gambling", "www", "/poker"]
        String normalized = url.replaceFirst("^https?://", "").replaceFirst("/$", "");
        String[] hostAndPath = normalized.split("/", 2);
        String[] domainParts = hostAndPath[0].split("\\.");
        
        List<String> result = new ArrayList<>();
        for (int i = domainParts.length - 1; i >= 0; i--) {
            result.add(domainParts[i]);
        }
        if (hostAndPath.length > 1) {
            result.add("/" + hostAndPath[1]);
        }
        return result.toArray(new String[0]);
    }
}

// Examples:
// trie.insert(entry("*.gambling.com"))  → blocks gambling.com and ALL subdomains
// trie.lookup("www.gambling.com")       → returns the entry (wildcard match)
// trie.lookup("gambling.com/poker")     → returns the entry (wildcard match)
// trie.lookup("not-gambling.com")       → returns null (no match)
```

**Why Reversed Domain?**
```
Domains are hierarchical right-to-left: com → google → www
By reversing, prefix matching in the trie naturally handles:
  "*.gambling.com" = all subdomains of gambling.com
  
Forward trie: hard to match "anything.gambling.com"
Reverse trie: easy — just traverse com → gambling → wildcard
```

---

### Deep Dive 3: Edge Node Architecture — The 3-Layer Lookup

**The Problem**: Each edge node must answer "Is this URL blocked?" in < 5ms for millions of requests/sec, while supporting exact, wildcard, and regex patterns.

**Three-Layer Lookup Pipeline** (executed in order, short-circuit on first definitive answer):

```java
public class EdgeUrlChecker {
    private final UrlBloomFilter bloomFilter;       // Layer 1: Fast rejection
    private final ConcurrentHashMap<String, CacheEntry> lruCache;  // Layer 2: Recent results
    private final UrlBlockingTrie trie;             // Layer 3: Full pattern matching

    /**
     * Check if a URL is blocked. Called for every user request.
     * Must complete in < 5ms (p99).
     */
    public BlockDecision check(String url, String regionCode) {
        String normalized = UrlNormalizer.normalize(url);
        
        // LAYER 1: Bloom Filter (< 1µs)
        if (!bloomFilter.mightContain(normalized)) {
            metrics.counter("url.check.bloom_reject").increment();
            return BlockDecision.ALLOW;  // Definitely not blocked
        }
        
        // Bloom says MAYBE — need to verify
        metrics.counter("url.check.bloom_pass").increment();
        
        // LAYER 2: LRU Cache (< 10µs)
        CacheEntry cached = lruCache.get(normalized);
        if (cached != null && !cached.isExpired()) {
            metrics.counter("url.check.cache_hit").increment();
            return cached.getDecision();
        }
        
        // LAYER 3: Trie Lookup (< 1ms)
        BlocklistEntry entry = trie.lookup(normalized);
        
        BlockDecision decision;
        if (entry != null) {
            decision = BlockDecision.blocked(entry);
            metrics.counter("url.check.blocked").increment();
        } else {
            decision = BlockDecision.ALLOW;  // Bloom false positive
            metrics.counter("url.check.bloom_false_positive").increment();
        }
        
        // Cache the result (both positive and negative)
        lruCache.put(normalized, new CacheEntry(decision, Instant.now().plusSeconds(3600)));
        
        return decision;
    }
}
```

**Performance Breakdown**:
```
┌───────────────┬──────────┬──────────────┬─────────────────────────┐
│ Layer          │ Latency  │ % of traffic │ What it handles          │
├───────────────┼──────────┼──────────────┼─────────────────────────┤
│ Bloom Filter  │ < 1µs    │ 99.9%        │ "Definitely not blocked" │
│ LRU Cache     │ < 10µs   │ 0.08%        │ Repeated checks          │
│ Trie Lookup   │ < 1ms    │ 0.02%        │ Bloom positives (true+FP)│
└───────────────┴──────────┴──────────────┴─────────────────────────┘

Overall p99 latency: < 2ms (dominated by bloom-only path)
Bloom FP rate 0.1% → only 1 in 1000 checks reach the Trie
```

---

### Deep Dive 4: Blocklist Propagation — Keeping Edge Nodes in Sync

**The Problem**: When an admin adds a new blocked URL, how do 1000+ edge nodes across the globe learn about it within 60 seconds?

**Push + Pull Hybrid Approach**:

```java
public class EdgeNodeSyncService {
    private final RegionalSyncClient syncClient;
    private volatile long currentBloomVersion = 0;
    private final UrlBloomFilter bloomFilter;
    private final UrlBlockingTrie trie;

    /**
     * Pull-based sync: polls Regional Sync Service every 5 seconds.
     * Lightweight — only checks version number, pulls delta if changed.
     */
    @Scheduled(fixedRate = 5_000)
    public void pollForUpdates() {
        long latestVersion = syncClient.getLatestBloomVersion(regionCode);
        
        if (latestVersion > currentBloomVersion) {
            log.info("Blocklist updated: {} → {}", currentBloomVersion, latestVersion);
            
            // Pull delta (only changes since our version)
            BlocklistDelta delta = syncClient.getDelta(regionCode, currentBloomVersion);
            
            // Apply additions
            for (BlocklistEntry added : delta.getAdded()) {
                trie.insert(added);
            }
            
            // Apply removals (trie doesn't support removal; mark as inactive)
            for (BlocklistEntry removed : delta.getRemoved()) {
                trie.remove(removed.getUrlPattern());
                lruCache.remove(UrlNormalizer.normalize(removed.getUrlPattern()));
            }
            
            // Rebuild Bloom Filter if > 100 changes (incremental add isn't possible)
            if (delta.getTotalChanges() > 100 || delta.getRemoved().size() > 0) {
                rebuildBloomFilter();
            } else {
                // Add new entries to existing bloom (only additions work)
                for (BlocklistEntry added : delta.getAdded()) {
                    bloomFilter.add(added.getUrlPattern());
                }
            }
            
            currentBloomVersion = latestVersion;
            metrics.gauge("edge.bloom_version").set(currentBloomVersion);
        }
    }

    /** Full rebuild from regional sync service (used on startup or large changes) */
    public void fullSync() {
        List<BlocklistEntry> allEntries = syncClient.getAllEntries(regionCode);
        
        // Rebuild Bloom Filter
        UrlBloomFilter newFilter = new UrlBloomFilter(allEntries.size() * 2, 0.001);
        allEntries.forEach(e -> newFilter.add(e.getUrlPattern()));
        
        // Rebuild Trie
        UrlBlockingTrie newTrie = new UrlBlockingTrie();
        allEntries.forEach(newTrie::insert);
        
        // Atomic swap (readers see old or new, never partial)
        this.bloomFilter = newFilter;
        this.trie = newTrie;
        this.lruCache.clear();
        
        log.info("Full sync complete: {} entries loaded", allEntries.size());
    }
}
```

**Propagation Timeline**:
```
T+0s:    Admin adds blocked URL via API
T+0.1s:  Written to PostgreSQL
T+0.2s:  Published to Kafka "blocklist-updates"
T+1s:    Regional Sync Service consumes message, updates regional store
T+2s:    Regional Sync increments bloom_version
T+5-10s: Edge nodes poll, detect version change, pull delta
T+10-15s: Edge nodes update local Bloom + Trie
─────────────────────────────────────────────────
Total: 10-15 seconds typical, < 60 seconds guaranteed
```

---

### Deep Dive 5: DNS-Level Blocking vs. HTTP Proxy Blocking

**The Problem**: Where in the network stack do we intercept and block URLs?

**Option A: DNS Sinkhole (Preferred for Domain-Level Blocking)**

```
How it works:
  User → DNS Query "gambling-site.com" → ISP DNS Resolver → Edge Node
  Edge Node: Bloom Filter → "MAYBE blocked" → Trie → "YES, blocked"
  Response: DNS returns sinkhole IP (e.g., 10.0.0.1) instead of real IP
  User browser connects to sinkhole IP → Block page served

Pros:
  ✓ Works for ALL protocols (HTTP, HTTPS, FTP, etc.)
  ✓ Very fast (DNS lookup adds < 5ms)
  ✓ Can't be bypassed by HTTPS (DNS happens before TLS)
  ✓ No need to inspect traffic content

Cons:
  ✗ Can only block entire domains, not specific paths
  ✗ Users can bypass with DoH (DNS over HTTPS) to external resolvers
  ✗ Blocks entire domain even if only one page is illegal
```

**Option B: HTTP/HTTPS Proxy (For Path-Level Blocking)**

```
How it works:
  User → HTTP Request → ISP Transparent Proxy → Edge Node
  Edge Node inspects: Host header + URL path
  If blocked: Return 302 redirect to block page
  If not blocked: Forward to destination

For HTTPS (SNI-based):
  User → TLS ClientHello (contains SNI: gambling-site.com) → Proxy
  Proxy inspects SNI field (unencrypted) → Block or forward
  Note: Cannot inspect path for HTTPS without TLS termination (MITM)

Pros:
  ✓ Can block specific paths (example.com/illegal-page)
  ✓ Can inject block page inline
  ✓ More granular control

Cons:
  ✗ Only works for HTTP (HTTPS needs SNI inspection or MITM)
  ✗ Adds more latency (proxy in the path)
  ✗ Higher infrastructure cost
  ✗ Privacy concerns with MITM
```

**Recommended Hybrid Approach**:
```
1. Domain-level blocks → DNS Sinkhole (90% of cases)
2. Path-level blocks   → HTTP Proxy (10% of cases, HTTP only)
3. HTTPS + path-level  → SNI-based domain block + warn admin about limitations
```

---

### Deep Dive 6: Handling Blocklist Removals — The Bloom Filter Problem

**The Problem**: Standard Bloom Filters don't support deletions. When a URL is unblocked, we can't remove it from the filter.

**Solution 1: Periodic Full Rebuild (Simple, Recommended)**

```java
public class BloomFilterRebuildService {
    
    /**
     * Rebuild the entire Bloom Filter from the current blocklist.
     * Scheduled every 5 minutes or triggered by removals.
     */
    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void rebuildBloomFilter() {
        List<String> activeUrls = regionStore.getAllActiveUrlPatterns(regionCode);
        
        // Create new filter (slightly oversized for future additions)
        UrlBloomFilter newFilter = new UrlBloomFilter(
            (int) (activeUrls.size() * 1.5), 0.001);
        
        activeUrls.forEach(newFilter::add);
        
        // Atomic swap — old filter is garbage collected
        edgeNodeState.setBloomFilter(newFilter);
        
        log.info("Bloom filter rebuilt: {} entries, {} bytes",
                 activeUrls.size(), newFilter.getSizeInBytes());
    }
}

// Cost analysis:
// 1M URLs → rebuild takes ~200ms (single-threaded)
// 10M URLs → rebuild takes ~2 seconds
// Acceptable for a 5-minute rebuild cycle
```

**Solution 2: Counting Bloom Filter (Supports Deletions)**

```java
public class CountingBloomFilter {
    private final int[] counters;  // Instead of bits, use 4-bit counters
    private final int size;
    private final int numHashFunctions;

    public void add(String url) {
        for (int i = 0; i < numHashFunctions; i++) {
            int idx = Math.abs(murmur3Hash(url, i) % size);
            if (counters[idx] < 15) counters[idx]++;  // 4-bit max = 15
        }
    }

    public void remove(String url) {
        for (int i = 0; i < numHashFunctions; i++) {
            int idx = Math.abs(murmur3Hash(url, i) % size);
            if (counters[idx] > 0) counters[idx]--;
        }
    }

    public boolean mightContain(String url) {
        for (int i = 0; i < numHashFunctions; i++) {
            int idx = Math.abs(murmur3Hash(url, i) % size);
            if (counters[idx] == 0) return false;
        }
        return true;
    }
}

// Trade-off: 4x more memory than standard Bloom (4 bits per counter vs 1 bit)
// 1M URLs: 1.7 MB (standard) → 6.8 MB (counting) — still very manageable
```

---

### Deep Dive 7: Fail-Open vs. Fail-Closed — Failure Mode Design

**The Problem**: When the blocking system fails, what happens to user traffic?

**Fail-Open (Recommended: Allow on Failure)**

```java
public class ResilientUrlChecker {
    private final EdgeUrlChecker checker;
    private final CircuitBreaker circuitBreaker;

    public BlockDecision checkWithFallback(String url, String region) {
        try {
            if (circuitBreaker.isOpen()) {
                metrics.counter("url.check.circuit_open").increment();
                return BlockDecision.ALLOW;  // Fail-open: allow traffic
            }
            
            BlockDecision decision = checker.check(url, region);
            circuitBreaker.recordSuccess();
            return decision;
            
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.error("URL check failed, allowing traffic (fail-open): {}", e.getMessage());
            metrics.counter("url.check.error_allow").increment();
            return BlockDecision.ALLOW;
        }
    }
}
```

**Why Fail-Open?**
```
Fail-Open (Allow):
  ✓ Users can still browse the internet
  ✓ ISP doesn't face angry customers
  ✗ Some blocked content may be temporarily accessible
  
Fail-Closed (Block):
  ✗ ALL internet traffic stops for affected users
  ✗ ISP faces massive outage complaints
  ✗ Equivalent to a DDoS on your own users
  ✓ No blocked content accessible during failure

Decision: Fail-open is almost always the right choice.
The risk of "temporarily unblocking a few URLs" is much smaller
than "blocking the entire internet for millions of users."
```

---

### Deep Dive 8: Data Sovereignty & Regional Isolation

**The Problem**: Governments require that their blocklists stay within their jurisdiction. China's blocklist must not be accessible from European servers, and vice versa.

**Architecture for Data Sovereignty**:

```
┌─────────────────────────────────────────┐
│          CENTRAL CONTROL PLANE           │
│  (Metadata only, no blocklist content)   │
│  • Region registry                       │
│  • Version numbers                       │
│  • Cross-region coordination             │
│  • Located in neutral jurisdiction       │
└─────────────────────────────────────────┘
         │                │              │
    ┌────┘                │              └────┐
    ▼                     ▼                   ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  CHINA ZONE   │  │  EU ZONE     │  │  US ZONE      │
│               │  │              │  │               │
│  Admin API    │  │  Admin API   │  │  Admin API    │
│  PostgreSQL   │  │  PostgreSQL  │  │  PostgreSQL   │
│  Kafka        │  │  Kafka       │  │  Kafka        │
│  Sync Service │  │  Sync Svc    │  │  Sync Svc     │
│  Edge Nodes   │  │  Edge Nodes  │  │  Edge Nodes   │
│               │  │              │  │               │
│  CN blocklist │  │  EU blocklist│  │  US blocklist  │
│  NEVER leaves │  │  NEVER leaves│  │  NEVER leaves  │
│  this zone    │  │  this zone   │  │  this zone     │
└──────────────┘  └──────────────┘  └──────────────┘

Key rules:
  1. Blocklist data never crosses zone boundaries
  2. Each zone is independently operable (can survive if central goes down)
  3. Central plane only has metadata: region names, version numbers, health
  4. Encryption at rest within each zone (government-approved algorithms)
```

---

### Deep Dive 9: URL Normalization & Evasion Prevention

**The Problem**: Attackers try to bypass blocklists using URL encoding tricks, unicode, redirects, etc.

**Normalization Pipeline**:

```java
public class UrlNormalizer {

    /**
     * Normalize a URL to its canonical form to prevent evasion.
     * All equivalent URLs should normalize to the same string.
     */
    public static String normalize(String rawUrl) {
        String url = rawUrl;
        
        // 1. Lowercase the scheme and host
        url = lowercaseSchemeAndHost(url);
        
        // 2. Decode percent-encoding (except reserved chars)
        url = decodePercentEncoding(url);
        
        // 3. Remove default ports (:80 for HTTP, :443 for HTTPS)
        url = removeDefaultPort(url);
        
        // 4. Remove fragment (#section)
        url = removeFragment(url);
        
        // 5. Sort query parameters (optional, for consistent matching)
        url = sortQueryParams(url);
        
        // 6. Remove trailing slash
        url = removeTrailingSlash(url);
        
        // 7. Handle IDN / Punycode (unicode domains)
        url = convertToAsciiDomain(url);
        
        // 8. Remove "www." prefix (optional, configurable)
        url = removeWwwPrefix(url);
        
        // 9. Resolve path traversal (/../, /./)
        url = resolvePathTraversal(url);
        
        return url;
    }
}

// Evasion attempts and how normalization handles them:
// 
// Attempt: "GAMBLING-SITE.COM"         → normalized: "gambling-site.com"
// Attempt: "gambling-site.com:80"       → normalized: "gambling-site.com"
// Attempt: "gambling-site.com/"         → normalized: "gambling-site.com"
// Attempt: "%67ambling-site.com"        → normalized: "gambling-site.com"
// Attempt: "gambling-site.com/./poker"  → normalized: "gambling-site.com/poker"
// Attempt: "www.gambling-site.com"      → normalized: "gambling-site.com"
// Attempt: "гэмблинг.com" (Cyrillic)   → normalized: "xn--e1afkbacgd6k.com" (Punycode)
```

---

### Deep Dive 10: Observability & Operational Metrics

**Key Metrics**:

```java
public class UrlBlockingMetrics {
    // Throughput
    Counter checksTotal      = Counter.builder("url.checks.total").register(registry);
    Counter blockedTotal     = Counter.builder("url.checks.blocked").register(registry);
    Counter allowedTotal     = Counter.builder("url.checks.allowed").register(registry);
    
    // Bloom Filter effectiveness  
    Counter bloomRejects     = Counter.builder("bloom.rejects").register(registry);       // Definite no
    Counter bloomPasses      = Counter.builder("bloom.passes").register(registry);         // Maybe yes
    Counter bloomFalsePos    = Counter.builder("bloom.false_positives").register(registry);// FP confirmed
    Gauge   bloomFpRate      = Gauge.builder("bloom.fp_rate", this, m -> 
        (double) bloomFalsePos.count() / bloomPasses.count()).register(registry);
    
    // Latency
    Timer   checkLatency     = Timer.builder("url.check.latency_ms").register(registry);
    Timer   syncLatency      = Timer.builder("edge.sync.latency_ms").register(registry);
    
    // Sync Health
    Gauge   bloomVersion     = Gauge.builder("edge.bloom_version", ...).register(registry);
    Gauge   entriesLoaded    = Gauge.builder("edge.entries_loaded", ...).register(registry);
    Gauge   syncLagSeconds   = Gauge.builder("edge.sync_lag_seconds", ...).register(registry);
    
    // Errors
    Counter checkErrors      = Counter.builder("url.check.errors").register(registry);
    Counter syncErrors       = Counter.builder("edge.sync.errors").register(registry);
}
```

**Alerting Rules**:

```yaml
alerts:
  - name: HighSyncLag
    condition: edge.sync_lag_seconds > 120
    severity: CRITICAL
    message: "Edge node blocklist is >2 minutes stale. New blocks not enforced."
    
  - name: HighBloomFalsePositiveRate
    condition: bloom.fp_rate > 0.01  # > 1%
    severity: WARNING
    message: "Bloom filter FP rate too high. Consider increasing filter size."
    
  - name: CheckLatencyHigh
    condition: p99(url.check.latency_ms) > 10
    severity: WARNING
    message: "URL check latency exceeding 10ms. Investigate edge node health."
    
  - name: EdgeNodeDown
    condition: edge.heartbeat_age > 30
    severity: CRITICAL
    message: "Edge node not reporting heartbeats. Fail-open in effect."
    
  - name: BlockRateAnomaly
    condition: rate(url.checks.blocked, 5m) change > 200%
    severity: WARNING
    message: "Block rate spiked. Possible bulk blocklist update or misconfiguration."
```

---

### Deep Dive 11: Bulk Blocklist Import & Change Management

**The Problem**: Governments often submit thousands of URLs at once (e.g., quarterly regulatory updates). A bulk import of 50,000 entries must not disrupt real-time URL checks.

```java
public class BulkImportService {
    private final BlocklistRepository repository;
    private final KafkaProducer<String, BlocklistEvent> kafka;
    private final AuditLogService auditLog;
    
    /**
     * Process a bulk blocklist import asynchronously.
     * Validates entries, writes in batches, publishes changes.
     */
    public CompletableFuture<BulkImportResult> processBulkImport(BulkImportRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            String importId = UUID.randomUUID().toString();
            log.info("Starting bulk import {}: {} entries for region {}", 
                     importId, request.getEntries().size(), request.getRegionCode());

            int accepted = 0, rejected = 0, duplicate = 0;
            List<BlocklistEntry> batch = new ArrayList<>();
            
            for (BulkEntryRequest entry : request.getEntries()) {
                // 1. Validate each entry
                ValidationResult validation = validateEntry(entry);
                if (!validation.isValid()) {
                    rejected++;
                    log.debug("Rejected entry '{}': {}", entry.getUrlPattern(), validation.getError());
                    continue;
                }
                
                // 2. Check for duplicates
                if (repository.existsByUrlAndRegion(entry.getUrlPattern(), request.getRegionCode())) {
                    duplicate++;
                    continue;
                }
                
                // 3. Build entry
                BlocklistEntry blockEntry = BlocklistEntry.builder()
                    .entryId(UUID.randomUUID())
                    .urlPattern(entry.getUrlPattern())
                    .patternType(entry.getPatternType())
                    .regionCode(request.getRegionCode())
                    .category(entry.getCategory())
                    .reason(request.getReason())
                    .createdBy(request.getSubmittedBy())
                    .status("ACTIVE")
                    .build();
                batch.add(blockEntry);
                accepted++;
                
                // 4. Flush in batches of 500 (avoid huge transactions)
                if (batch.size() >= 500) {
                    flushBatch(batch, request.getRegionCode());
                    batch.clear();
                }
            }
            
            // Flush remaining
            if (!batch.isEmpty()) {
                flushBatch(batch, request.getRegionCode());
            }
            
            // 5. Write audit log for entire import
            auditLog.recordBulkImport(importId, request.getRegionCode(), 
                                      accepted, rejected, duplicate, request.getReason());
            
            log.info("Bulk import {} complete: accepted={}, rejected={}, duplicate={}", 
                     importId, accepted, rejected, duplicate);
            
            return new BulkImportResult(importId, accepted, rejected, duplicate);
        });
    }
    
    private void flushBatch(List<BlocklistEntry> batch, String regionCode) {
        // Write to PostgreSQL in one transaction
        repository.batchInsert(batch);
        
        // Publish each entry to Kafka for edge propagation
        for (BlocklistEntry entry : batch) {
            kafka.send(new ProducerRecord<>("blocklist-updates", regionCode,
                BlocklistEvent.builder()
                    .action("ADD")
                    .entryId(entry.getEntryId().toString())
                    .urlPattern(entry.getUrlPattern())
                    .regionCode(regionCode)
                    .build()));
        }
    }
    
    private ValidationResult validateEntry(BulkEntryRequest entry) {
        if (entry.getUrlPattern() == null || entry.getUrlPattern().isBlank()) {
            return ValidationResult.invalid("URL pattern is empty");
        }
        if (entry.getUrlPattern().length() > 2048) {
            return ValidationResult.invalid("URL pattern exceeds 2048 characters");
        }
        if (entry.getPatternType() == null) {
            return ValidationResult.invalid("Pattern type is required");
        }
        // Validate regex patterns don't have ReDoS vulnerabilities
        if (entry.getPatternType().equals("REGEX")) {
            try {
                Pattern.compile(entry.getUrlPattern());
                // Check for catastrophic backtracking
                if (isVulnerableToRedos(entry.getUrlPattern())) {
                    return ValidationResult.invalid("Regex pattern vulnerable to ReDoS");
                }
            } catch (PatternSyntaxException e) {
                return ValidationResult.invalid("Invalid regex: " + e.getMessage());
            }
        }
        return ValidationResult.valid();
    }
}
```

**Rate Limiting Bulk Imports**:
```
To prevent bulk imports from overwhelming the system:
  - Max 1 bulk import per region at a time
  - Max 100,000 entries per bulk import
  - Batched DB writes (500 entries per transaction)
  - Kafka publishes are async with backpressure
  - Edge nodes apply changes incrementally (don't rebuild everything at once)
  
Estimated timing for 50,000 entry import:
  Validation: ~5 seconds
  DB writes (100 batches × 50ms): ~5 seconds
  Kafka publish: ~2 seconds
  Edge propagation: ~30 seconds
  Total: ~42 seconds end-to-end
```

---

### Deep Dive 12: Auto-Expiring Blocks & TTL Management

**The Problem**: Some blocks are temporary (court injunctions pending appeal, event-based blocks). The system must auto-expire entries without manual intervention.

```java
public class BlockExpirationService {
    private final BlocklistRepository repository;
    private final KafkaProducer<String, BlocklistEvent> kafka;
    
    /**
     * Runs every minute. Finds and expires stale entries.
     */
    @Scheduled(fixedRate = 60_000)
    public void expireStaleEntries() {
        Instant now = Instant.now();
        
        // Find entries that have passed their expiration time
        List<BlocklistEntry> expiredEntries = repository.findExpiredEntries(now);
        
        if (expiredEntries.isEmpty()) return;
        
        log.info("Expiring {} blocklist entries", expiredEntries.size());
        
        for (BlocklistEntry entry : expiredEntries) {
            // Update status in DB
            entry.setStatus("EXPIRED");
            entry.setUpdatedAt(now);
            repository.save(entry);
            
            // Publish removal event to Kafka
            kafka.send(new ProducerRecord<>("blocklist-updates", entry.getRegionCode(),
                BlocklistEvent.builder()
                    .action("REMOVE")
                    .entryId(entry.getEntryId().toString())
                    .urlPattern(entry.getUrlPattern())
                    .regionCode(entry.getRegionCode())
                    .reason("Auto-expired at " + entry.getExpiresAt())
                    .build()));
            
            // Audit log
            auditLog.record(entry.getEntryId(), "EXPIRED", "SYSTEM",
                "Auto-expired per TTL policy. Original expires_at: " + entry.getExpiresAt());
            
            metrics.counter("blocklist.entries.expired").tag("region", entry.getRegionCode()).increment();
        }
        
        // Increment bloom version so edge nodes re-sync
        for (String region : expiredEntries.stream()
                .map(BlocklistEntry::getRegionCode)
                .collect(Collectors.toSet())) {
            repository.incrementBloomVersion(region);
        }
    }
}
```

**TTL Patterns in Practice**:
```
┌─────────────────────────┬────────────────────┬──────────────────────────────────┐
│ Use Case                 │ Typical TTL        │ Example                          │
├─────────────────────────┼────────────────────┼──────────────────────────────────┤
│ Court temporary order    │ 30-90 days         │ Block pending appeal hearing     │
│ Emergency takedown       │ 24-72 hours        │ Terrorist content during attack  │
│ Event-based block        │ Hours to days      │ Block gambling during exam season │
│ Regulatory review        │ 6-12 months        │ Annual compliance review cycle   │
│ Permanent block          │ NULL (no expiry)   │ Child exploitation material      │
└─────────────────────────┴────────────────────┴──────────────────────────────────┘
```

---

### Deep Dive 13: Category-Based Blocking & Policy Engine

**The Problem**: Instead of blocking individual URLs, admins want to say "block all gambling in China" or "block all piracy in Germany." This requires mapping URLs to categories and applying regional policies.

```java
public class PolicyEngine {
    private final Map<String, Set<String>> regionPolicies = new ConcurrentHashMap<>();
    // regionCode → set of blocked categories

    /**
     * Check if a URL is blocked based on regional policy + category.
     * Called AFTER the Bloom Filter + Trie check for known URLs.
     * This covers dynamically categorized URLs.
     */
    public BlockDecision checkPolicy(String url, String regionCode) {
        Set<String> blockedCategories = regionPolicies.get(regionCode);
        if (blockedCategories == null || blockedCategories.isEmpty()) {
            return BlockDecision.ALLOW;
        }
        
        // Categorize the URL (using domain-level classification)
        String domain = extractDomain(url);
        Set<String> urlCategories = categoryService.getCategoriesForDomain(domain);
        
        // Check if any category is blocked in this region
        for (String category : urlCategories) {
            if (blockedCategories.contains(category)) {
                return BlockDecision.blocked(
                    "Policy: " + category + " blocked in " + regionCode,
                    category,
                    getBlockPageForCategory(regionCode, category));
            }
        }
        
        return BlockDecision.ALLOW;
    }
    
    /**
     * Load regional policies from database.
     * Called on startup and when policies change.
     */
    public void loadPolicies() {
        List<RegionPolicy> policies = policyRepository.findAllActive();
        for (RegionPolicy policy : policies) {
            regionPolicies.computeIfAbsent(policy.getRegionCode(), k -> ConcurrentHashMap.newKeySet())
                          .add(policy.getCategory());
        }
        log.info("Loaded {} regional policies across {} regions", 
                 policies.size(), regionPolicies.size());
    }
}

public class DomainCategoryService {
    // Pre-computed domain → categories mapping
    // Sources: commercial URL classification databases (e.g., Webshrinker, Zvelo)
    // Updated daily via batch job
    
    private final Map<String, Set<String>> domainCategories = new ConcurrentHashMap<>();
    
    /**
     * Get categories for a domain.
     * Uses multi-tier lookup: cache → local DB → external API.
     */
    public Set<String> getCategoriesForDomain(String domain) {
        // Tier 1: In-memory cache
        Set<String> cached = domainCategories.get(domain);
        if (cached != null) return cached;
        
        // Tier 2: Local DB (pre-computed for top 10M domains)
        Set<String> fromDb = categoryDb.getCategories(domain);
        if (fromDb != null && !fromDb.isEmpty()) {
            domainCategories.put(domain, fromDb);
            return fromDb;
        }
        
        // Tier 3: External classification API (for unknown domains)
        // Async — don't block the URL check. Default to ALLOW for unclassified.
        asyncClassifier.classifyAsync(domain);
        return Set.of("UNKNOWN");
    }
}
```

**Category Hierarchy**:
```
ADULT
  ├── PORNOGRAPHY
  ├── ADULT_CONTENT
  └── NUDITY
GAMBLING
  ├── ONLINE_CASINOS
  ├── SPORTS_BETTING
  └── LOTTERY
PIRACY
  ├── TORRENT_SITES
  ├── STREAMING_PIRACY
  └── SOFTWARE_PIRACY
ILLEGAL
  ├── DRUGS
  ├── WEAPONS
  └── FRAUD
TERRORISM
  ├── RECRUITMENT
  └── PROPAGANDA
GOVERNMENT
  ├── CENSORED_MEDIA
  └── POLITICAL_DISSENT

Each region specifies which top-level categories (or sub-categories) are blocked.
Example: China blocks GAMBLING + GOVERNMENT.POLITICAL_DISSENT + ADULT
Example: Germany blocks PIRACY + TERRORISM
```

---

### Deep Dive 14: Audit Trail & Compliance Reporting

**The Problem**: Governments require proof that blocklists are being enforced. We need immutable audit logs showing when entries were added, removed, who did it, and why.

```java
public class AuditLogService {
    private final CassandraTemplate cassandra; // Append-only, immutable, time-series optimized

    /**
     * Record an audit event. Called for every blocklist change.
     */
    public void record(UUID entryId, String action, String performedBy, String reason) {
        AuditLogEntry log = AuditLogEntry.builder()
            .logId(TimeUUID.generate())
            .entryId(entryId)
            .action(action)           // CREATED, UPDATED, REVOKED, EXPIRED, BULK_IMPORT
            .performedBy(performedBy) // Admin ID or "SYSTEM" for auto-expiration
            .regionCode(getRegionForEntry(entryId))
            .reason(reason)
            .timestamp(Instant.now())
            .clientIp(getCurrentRequestIp())
            .userAgent(getCurrentUserAgent())
            .build();
        
        cassandra.insert(log);
        
        // Also write to compliance-specific table (queryable by date range + region)
        ComplianceRecord compliance = ComplianceRecord.builder()
            .regionCode(log.getRegionCode())
            .day(LocalDate.now())
            .logId(log.getLogId())
            .action(log.getAction())
            .urlPattern(getUrlPatternForEntry(entryId))
            .timestamp(log.getTimestamp())
            .build();
        cassandra.insert(compliance);
    }

    /**
     * Generate compliance report for a region over a date range.
     * Used by government auditors to verify enforcement.
     */
    public ComplianceReport generateReport(String regionCode, LocalDate startDate, LocalDate endDate) {
        // Query compliance records for the region
        List<ComplianceRecord> records = cassandra.query(
            "SELECT * FROM compliance_records WHERE region_code = ? AND day >= ? AND day <= ?",
            regionCode, startDate, endDate);
        
        // Aggregate statistics
        long totalAdded = records.stream().filter(r -> r.getAction().equals("CREATED")).count();
        long totalRemoved = records.stream().filter(r -> 
            r.getAction().equals("REVOKED") || r.getAction().equals("EXPIRED")).count();
        long totalActive = repository.countActiveEntries(regionCode);
        
        // Compute enforcement rate (how quickly blocks were propagated)
        // Compare entry created_at with first edge node enforcement timestamp
        double avgPropagationSeconds = computeAvgPropagation(records);
        
        return ComplianceReport.builder()
            .regionCode(regionCode)
            .reportPeriod(startDate + " to " + endDate)
            .totalEntriesAdded(totalAdded)
            .totalEntriesRemoved(totalRemoved)
            .currentActiveEntries(totalActive)
            .avgPropagationTimeSeconds(avgPropagationSeconds)
            .slaCompliance(avgPropagationSeconds < 60.0 ? "COMPLIANT" : "NON-COMPLIANT")
            .auditTrail(records)
            .generatedAt(Instant.now())
            .build();
    }

    /**
     * Immutability guarantee: audit logs cannot be deleted or modified.
     * Implemented via:
     * 1. Cassandra table with no DELETE permissions for any user
     * 2. Write-once schema (no UPDATE operations)
     * 3. Backup to separate cold storage (S3) daily
     * 4. Cryptographic hash chain (each log entry includes hash of previous)
     */
}
```

**Audit Log Schema** (Cassandra):
```sql
CREATE TABLE audit_log (
    region_code   TEXT,
    day           DATE,
    log_id        TIMEUUID,
    entry_id      UUID,
    action        TEXT,
    url_pattern   TEXT,
    performed_by  TEXT,
    reason        TEXT,
    client_ip     TEXT,
    timestamp     TIMESTAMP,
    prev_hash     TEXT,    -- SHA-256 of previous log entry (hash chain)
    
    PRIMARY KEY ((region_code, day), log_id)
) WITH CLUSTERING ORDER BY (log_id DESC)
  AND default_time_to_live = 0;  -- Never expire audit logs

-- Retention: 7 years (legal requirement in most jurisdictions)
-- Backed up to S3 Glacier daily
```

**Compliance Dashboard**:
```
┌─────────────────────────────────────────────────────┐
│        BLOCKLIST COMPLIANCE REPORT — CN Region       │
│        Period: 2025-Q1 (Jan 1 - Mar 31)             │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Blocklist Size:                                     │
│  Active entries:    2,345,678                        │
│  Added this quarter:   12,456                        │
│  Removed this quarter:  3,789                        │
│  Auto-expired:          1,234                        │
│                                                      │
│  Enforcement SLA:                                    │
│  Avg propagation time:  18 seconds                   │
│  P99 propagation time:  45 seconds                   │
│  SLA target:            < 60 seconds                 │
│  Status: ✅ COMPLIANT                                │
│                                                      │
│  Edge Node Health:                                   │
│  Active edge nodes:     842 / 850                    │
│  Sync lag (max):        12 seconds                   │
│  Bloom filter FPR:      0.08%                        │
│                                                      │
│  Block Rate:                                         │
│  Total checks:          28.5 billion                 │
│  Total blocks:          412 million (1.4%)           │
│  Top blocked category:  GAMBLING (68%)               │
│                                                      │
│  Audit Trail: 15,245 change events (fully logged)    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

---

## 📊 Summary: Key Trade-offs & Decision Matrix

| Decision | Options Considered | Chosen | Why |
|----------|-------------------|--------|-----|
| **Primary lookup** | Hash Table / Bloom Filter / Trie | Bloom Filter + Trie | Bloom eliminates 99.9% in < 1µs; Trie handles wildcards |
| **Blocking mechanism** | DNS Sinkhole / HTTP Proxy / Both | DNS (primary) + Proxy (path-level) | DNS works for HTTPS, lowest latency, widest coverage |
| **Data distribution** | Central DB query / Edge-local data | Edge-local (in-memory) | Zero network latency for lookups; critical for < 5ms target |
| **Consistency** | Strong / Eventual | Eventual (< 60s) | Acceptable delay; strong consistency would kill performance |
| **Failure mode** | Fail-open / Fail-closed | Fail-open (allow) | Blocking all internet on failure is unacceptable |
| **Bloom deletions** | Counting BF / Periodic rebuild | Periodic rebuild (5 min) | Simpler, 4x less memory, rebuild is fast (< 2s for 10M) |
| **Propagation** | Push / Pull / Hybrid | Hybrid (Kafka push → poll pull) | Fast propagation + edge nodes control their own sync pace |
| **Pattern matching** | Regex / Trie / Hash per pattern | Reversed-domain Trie | O(depth) lookup, natural wildcard support, memory efficient |
| **Data sovereignty** | Shared DB / Regional isolation | Fully isolated zones | Legal compliance; each zone independently operable |
| **Sync protocol** | Full sync / Delta sync | Delta with periodic full | Delta for speed; full sync as recovery / consistency check |

## 🏗️ Technology Stack Summary

```
┌────────────────────────────────────────────────────────┐
│  ADMIN PLANE        │  EDGE DATA PLANE                 │
│  ────────────       │  ───────────────                  │
│  Java (Spring Boot) │  Java / C++ (Edge Nodes)         │
│  PostgreSQL (SoT)   │  In-Memory: Bloom + Trie + LRU   │
│  Kafka (CDC)        │  CoreDNS (DNS Sinkhole)          │
│                     │  Nginx (Block Pages)              │
│  SYNC LAYER         │                                   │
│  ──────────         │  OBSERVABILITY                    │
│  Regional Sync Svc  │  Prometheus + Grafana             │
│  gRPC + Protobuf    │  PagerDuty (Alerting)             │
│  S3 (BF snapshots)  │  ELK (Logs)                       │
└────────────────────────────────────────────────────────┘
```

## 🎯 Interview Talking Points

1. **"Bloom Filter is the hero"** — It eliminates 99.9% of checks in < 1 microsecond. Without it, every request would need a Trie lookup, increasing latency 1000x.

2. **"Data lives at the edge, not in the cloud"** — The entire blocklist fits in < 3GB of RAM per edge node. No network round-trip needed for any URL check. This is what makes < 5ms possible.

3. **"DNS is the primary blocking mechanism"** — It works for all protocols (HTTP, HTTPS, FTP), happens before TLS, and adds minimal latency. HTTP proxy is only needed for path-level granularity.

4. **"Fail-open is the only sane default"** — A blocking system that blocks the entire internet on failure is worse than one that temporarily allows a few blocked URLs through.

5. **"Reversed-domain Trie makes wildcards trivial"** — By storing `com.gambling.*` instead of `*.gambling.com`, wildcard matching becomes a simple prefix traversal.

6. **"Data sovereignty is non-negotiable"** — China's blocklist never touches EU servers. Fully isolated regional zones with independent databases, syncing only through a metadata control plane.

---

**References**:
- Cloudflare Gateway (DNS filtering architecture)
- OpenDNS / Cisco Umbrella
- UK CleanFeed (BT's URL blocking system)
- China's Great Firewall (GFW) architecture analysis
- Bloom Filter: Burton H. Bloom, 1970

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 14 topics (choose 2-3 based on interviewer interest)
