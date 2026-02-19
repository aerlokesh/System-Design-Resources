# Web Crawler with Limited Communication - Hello Interview Framework

> **Question**: Design a distributed web crawler using 10,000 machines to crawl 1 billion URLs without any centralized components (no master nodes, databases, queues, or cloud services). Focus on decentralized coordination, load distribution, and handling communication constraints between nodes.
>
> **Asked at**: Meta, Bloomberg
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
1. **Crawl the Web**: Starting from a set of seed URLs, the system should fetch web pages, extract links, and recursively crawl discovered URLs.
2. **URL Deduplication**: Never crawl the same URL twice. Every URL must be owned by exactly one worker.
3. **URL Partitioning**: Each worker is responsible for a deterministic subset of URLs via `hash(url) % N` — no centralized coordinator.
4. **Politeness**: Respect `robots.txt`, enforce per-domain crawl rate limits (e.g., max 1 request/sec per domain).
5. **Store Crawled Content**: Each worker stores fetched pages locally on its own disk.

#### Nice to Have (P1)
- Priority-based crawling (important pages first).
- Incremental re-crawling (revisit pages periodically).
- Content deduplication (detect near-duplicate pages).
- Link graph construction for PageRank computation.

#### Key Constraints
- **10,000 machines** — high CPU, local disk, local RAM.
- **No centralized components** — no master node, no shared database, no message queue service, no cloud storage (S3/DynamoDB).
- **Limited, unreliable inter-node communication** — bandwidth is scarce, connections drop.
- **Each machine acts autonomously** — can crash and restart independently.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Throughput** | 1 billion URLs/day (~11,600 URLs/sec globally) | Core requirement |
| **Per-worker throughput** | ~100,000 URLs/day (~1.2 URLs/sec) | 1B / 10K workers |
| **Latency** | Not critical (batch system) | Throughput >> latency |
| **Availability** | Tolerate up to 10% worker failures | No single point of failure |
| **Deduplication** | Zero duplicate crawls (within partition) | Waste of bandwidth + politeness violations |
| **Politeness** | Max 1 req/sec per domain, respect robots.txt | Legal & ethical requirement |
| **Communication** | Minimize inter-node traffic; batch transfers | Bandwidth is the bottleneck |
| **Persistence** | Survive worker restarts; checkpoint state to local disk | Crash recovery without data loss |
| **Consistency** | Eventual; no global coordination required | Decentralized by design |

### Capacity Estimation

```
Cluster:
  Workers: 10,000 machines
  Per worker: 16 cores, 64 GB RAM, 2 TB local SSD

URLs:
  Target: 1 billion URLs/day
  Per worker: 1B / 10K = 100,000 URLs/day = ~1.16 URLs/sec
  
  Average page size: 100 KB (HTML)
  Per worker storage/day: 100K × 100 KB = 10 GB/day
  Per worker storage/month: 300 GB/month (fits in 2 TB SSD easily)

Frontier (URL queue per worker):
  At any time: ~1M URLs in frontier per worker
  URL avg length: 100 bytes + metadata: 200 bytes
  Frontier size: 1M × 200 bytes = 200 MB (trivial)

URL Seen Set (deduplication):
  Total URLs seen by one worker: ~10M-50M over time
  Using Bloom Filter: 50M URLs at 0.01% FPR = ~72 MB
  Using RocksDB on-disk hash: unlimited, O(1) lookup

Inter-node communication:
  Discovered URLs that hash to other workers → must be forwarded
  Assume 10 outbound links per page → 10 × 100K = 1M URLs/day to forward
  95% hash to other workers → 950K URLs/day outbound = ~190 MB/day
  Batched every 60 seconds → ~130 KB/batch/worker → very manageable
```

---

## 2️⃣ Core Entities

### Entity 1: CrawlUrl (URL to be crawled)
```java
public class CrawlUrl implements Comparable<CrawlUrl> {
    private final String url;
    private final String domain;          // Extracted for politeness
    private final int ownerWorker;        // hash(url) % N
    private final int priority;           // Lower = crawl sooner
    private final int depth;              // Hops from seed URL
    private final Instant discoveredAt;
    private final String sourceUrl;       // Where this link was found

    public int getOwnerWorker() {
        return Math.abs(url.hashCode()) % ClusterConfig.TOTAL_WORKERS;
    }

    @Override
    public int compareTo(CrawlUrl other) {
        return Integer.compare(this.priority, other.priority);
    }
}
```

### Entity 2: CrawledPage (Fetched content)
```java
public class CrawledPage {
    private final String url;
    private final int httpStatus;
    private final String contentType;
    private final byte[] body;            // Raw HTML
    private final Map<String, String> headers;
    private final List<String> extractedLinks;
    private final Instant crawledAt;
    private final long contentHash;       // For content dedup (SimHash / MD5)
    private final int workerIdCrawledBy;
}
```

### Entity 3: WorkerState (Local node state)
```java
public class WorkerState {
    private final int workerId;
    private final PriorityQueue<CrawlUrl> frontier;           // URLs to crawl (priority queue)
    private final BloomFilter<String> seenUrls;                // URL dedup (in-memory)
    private final RocksDB seenUrlsOnDisk;                      // Overflow dedup (on-disk)
    private final Map<String, DomainPoliteness> domainState;   // Per-domain rate limiting
    private final Map<String, RobotsTxtRules> robotsCache;     // Cached robots.txt
    private final List<CrawlUrl> outboundBuffer;               // URLs to forward to other workers
    private final long lastCheckpointAt;
}
```

### Entity 4: DomainPoliteness (Per-domain rate limiter)
```java
public class DomainPoliteness {
    private final String domain;
    private Instant lastRequestAt;
    private final int minDelayMs;         // From robots.txt Crawl-delay or default 1000ms
    private final RobotsTxtRules rules;   // Parsed robots.txt
    
    public boolean canCrawlNow() {
        return Instant.now().isAfter(lastRequestAt.plusMillis(minDelayMs));
    }
    
    public void recordRequest() {
        this.lastRequestAt = Instant.now();
    }
}
```

---

## 3️⃣ API Design

Since this is a **peer-to-peer system with no central API**, the "API" is the inter-worker communication protocol.

### Worker-to-Worker Protocol (gRPC / Custom TCP)

#### 1. Forward Discovered URLs (Batched)
```
// Sent every 30-60 seconds from worker A → worker B
// Contains URLs whose hash maps to worker B

message ForwardUrlBatch {
    int32 source_worker_id = 1;
    int64 batch_id = 2;               // For idempotent processing
    repeated CrawlUrlProto urls = 3;   // Batch of 100-10,000 URLs
}

message CrawlUrlProto {
    string url = 1;
    int32 priority = 2;
    int32 depth = 3;
    string source_url = 4;
}

message ForwardUrlResponse {
    int64 batch_id = 1;
    int32 accepted = 2;               // How many were new (not duplicates)
    int32 rejected = 3;               // Already seen
}
```

#### 2. Heartbeat / Peer Discovery
```
// Workers periodically announce themselves to known peers
// Gossip protocol: each worker tells others about workers it knows

message Heartbeat {
    int32 worker_id = 1;
    string host = 2;
    int32 port = 3;
    int64 urls_crawled = 4;
    int64 frontier_size = 5;
    int64 timestamp = 6;
    repeated PeerInfo known_peers = 7;  // Gossip protocol
}

message PeerInfo {
    int32 worker_id = 1;
    string host = 2;
    int32 port = 3;
    int64 last_seen = 4;
}
```

#### 3. Anti-Entropy Sync (Periodic Consistency Check)
```
// Workers periodically exchange Bloom Filter digests to detect missed URLs

message AntiEntropyRequest {
    int32 worker_id = 1;
    bytes bloom_filter_digest = 2;     // Compressed Bloom filter of recently crawled URLs
    int64 since_timestamp = 3;
}

message AntiEntropyResponse {
    repeated string missing_urls = 1;  // URLs the peer has but we don't
}
```

---

## 4️⃣ Data Flow

### Flow 1: Worker Crawls a URL (Main Loop)
```
1. Worker picks highest-priority URL from local frontier (PriorityQueue)
   ↓
2. Politeness check:
   a. Check robots.txt (cached, re-fetch if stale > 24h)
   b. Check domain rate limiter: can I crawl this domain now?
   → If NO: put URL back, pick next URL from different domain
   → If YES: proceed
   ↓
3. Fetch page via HTTP(S):
   GET https://example.com/page
   Timeout: 30 seconds
   Max page size: 10 MB
   ↓
4. Parse & extract:
   a. Parse HTML content
   b. Extract all <a href="..."> links
   c. Normalize URLs (resolve relative, lowercase, remove fragments)
   d. Compute content hash (for dedup)
   ↓
5. Store crawled page:
   → Write to local disk: /data/crawled/{domain}/{url_hash}.html.gz
   ↓
6. Process extracted links:
   For each link:
     a. Compute owner = hash(link) % N
     b. If owner == me:
        → Check local seenUrls Bloom Filter
        → If not seen: add to my frontier
     c. If owner != me:
        → Add to outboundBuffer[owner] for batched forwarding
   ↓
7. Mark URL as crawled in seenUrls set
   ↓
8. Update domain politeness timer
```

### Flow 2: Forwarding Discovered URLs to Other Workers
```
1. Worker accumulates discovered URLs for other workers in outbound buffers
   outboundBuffer[worker_42] = [url1, url2, url3, ...]
   ↓
2. Every 30-60 seconds OR when buffer reaches 10,000 URLs:
   For each target worker with pending URLs:
     a. Create ForwardUrlBatch protobuf
     b. Compress with gzip (URLs compress very well: 80-90% ratio)
     c. Send via gRPC to target worker
   ↓
3. Target worker receives batch:
   a. Check batch_id for idempotency (skip if already processed)
   b. For each URL:
      - Check Bloom Filter: already seen?
      - If new: add to frontier + mark as seen
      - If seen: discard (dedup)
   c. Return ForwardUrlResponse with counts
   ↓
4. Source worker:
   - On success: clear buffer for that target
   - On failure: retain buffer, retry next cycle (exponential backoff)
   - If target unreachable for > 5 minutes: buffer to disk, retry later
```

### Flow 3: Worker Startup / Recovery
```
1. Worker starts (or restarts after crash):
   ↓
2. Load checkpoint from local disk:
   - frontier.snapshot (serialized priority queue)
   - seen_urls.bloom (Bloom filter state)
   - seen_urls.rocks (RocksDB on-disk URL set)
   - outbound_buffers/ (unsent URL batches)
   ↓
3. If no checkpoint (first boot):
   - Initialize empty frontier
   - If worker_id is in seed-worker set:
     → Load seed URLs that hash to this worker
   ↓
4. Start main crawl loop
   ↓
5. Start background threads:
   - Outbound URL forwarder (every 30s)
   - Checkpoint writer (every 5 min)
   - Peer heartbeat / gossip (every 10s)
   - Anti-entropy sync (every 10 min)
   - Robots.txt refresher (every 24h per domain)
```

### Flow 4: Handling a Dead Worker
```
1. Worker 42 crashes (power failure, OOM, etc.)
   ↓
2. Other workers notice: heartbeats from worker 42 stop
   (detected after 3 missed heartbeats = 30 seconds)
   ↓
3. URLs that hash to worker 42 accumulate in outbound buffers
   of other workers (can't be delivered)
   ↓
4. Worker 42 restarts:
   - Loads checkpoint from disk
   - Resumes crawling from last checkpoint
   - May re-crawl some URLs crawled since last checkpoint (idempotent)
   ↓
5. Other workers detect worker 42 is back via gossip
   - Flush buffered URLs for worker 42
   ↓
6. System self-heals. No data lost (just delayed).

Alternative: If worker 42 is permanently dead:
   - Remaining workers detect via prolonged absence
   - Consistent hashing ring rebalances: worker 42's URLs
     get redistributed to neighboring workers
   - Uses virtual nodes for even distribution
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SEED URL DISTRIBUTOR (bootstrap only)             │
│  Distribute initial seed URLs to workers based on hash(url) % N     │
│  (Runs once at startup, then the system is fully decentralized)     │
└──────────────────────────────────┬──────────────────────────────────┘
                                   │ (seed URLs)
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│   WORKER 0        │    │   WORKER 1        │    │   WORKER 9999    │
│                    │    │                    │    │                    │
│  ┌──────────────┐ │    │  ┌──────────────┐ │    │  ┌──────────────┐ │
│  │ Crawl Engine │ │    │  │ Crawl Engine │ │    │  │ Crawl Engine │ │
│  │ • Fetch page │ │    │  │ • Fetch page │ │    │  │ • Fetch page │ │
│  │ • Parse HTML │ │    │  │ • Parse HTML │ │    │  │ • Parse HTML │ │
│  │ • Extract    │ │    │  │ • Extract    │ │    │  │ • Extract    │ │
│  │   links      │ │    │  │   links      │ │    │  │   links      │ │
│  └──────┬───────┘ │    │  └──────┬───────┘ │    │  └──────┬───────┘ │
│         │         │    │         │         │    │         │         │
│  ┌──────▼───────┐ │    │  ┌──────▼───────┐ │    │  ┌──────▼───────┐ │
│  │ URL Router   │ │    │  │ URL Router   │ │    │  │ URL Router   │ │
│  │ hash(url)%N  │◄├────┤──┤►             │◄├────┤──┤►             │ │
│  │ → local or   │ │    │  │              │ │    │  │              │ │
│  │   forward    │ │    │  │              │ │    │  │              │ │
│  └──────┬───────┘ │    │  └──────┬───────┘ │    │  └──────┬───────┘ │
│         │         │    │         │         │    │         │         │
│  ┌──────▼───────┐ │    │  ┌──────▼───────┐ │    │  ┌──────▼───────┐ │
│  │ Frontier     │ │    │  │ Frontier     │ │    │  │ Frontier     │ │
│  │ (PriorityQ)  │ │    │  │ (PriorityQ)  │ │    │  │ (PriorityQ)  │ │
│  ├──────────────┤ │    │  ├──────────────┤ │    │  ├──────────────┤ │
│  │ Seen URLs    │ │    │  │ Seen URLs    │ │    │  │ Seen URLs    │ │
│  │ (Bloom+Rock) │ │    │  │ (Bloom+Rock) │ │    │  │ (Bloom+Rock) │ │
│  ├──────────────┤ │    │  ├──────────────┤ │    │  ├──────────────┤ │
│  │ Politeness   │ │    │  │ Politeness   │ │    │  │ Politeness   │ │
│  │ (per-domain) │ │    │  │ (per-domain) │ │    │  │ (per-domain) │ │
│  ├──────────────┤ │    │  ├──────────────┤ │    │  ├──────────────┤ │
│  │ Page Store   │ │    │  │ Page Store   │ │    │  │ Page Store   │ │
│  │ (local SSD)  │ │    │  │ (local SSD)  │ │    │  │ (local SSD)  │ │
│  └──────────────┘ │    │  └──────────────┘ │    │  └──────────────┘ │
└──────────────────┘    └──────────────────┘    └──────────────────┘
         ▲    │                  ▲    │                  ▲    │
         │    ▼                  │    ▼                  │    ▼
    ┌────┴────────┐         ┌───┴─────────┐        ┌───┴─────────┐
    │ Gossip /    │         │ Gossip /    │        │ Gossip /    │
    │ Heartbeat   │◄───────►│ Heartbeat   │◄──────►│ Heartbeat   │
    │ Protocol    │         │ Protocol    │        │ Protocol    │
    └─────────────┘         └─────────────┘        └─────────────┘

No master. No central DB. No shared queue.
Each worker is autonomous and identical.
```

### Component Responsibilities (Per Worker)

| Component | Purpose | Implementation |
|-----------|---------|---------------|
| **Crawl Engine** | Fetch pages, parse HTML, extract links | Multi-threaded HTTP client (50 concurrent fetches) |
| **URL Router** | Determine URL owner via `hash(url) % N`, forward or keep | Consistent hashing with virtual nodes |
| **Frontier** | Priority queue of URLs to crawl next | In-memory `PriorityQueue` + disk overflow |
| **Seen URLs** | Deduplication: never crawl same URL twice | In-memory Bloom Filter + RocksDB on-disk |
| **Politeness Engine** | Per-domain rate limiting + robots.txt | In-memory map of domain → last_request_time |
| **Page Store** | Store crawled content on local disk | gzip'd files: `/data/crawled/{domain}/{hash}.gz` |
| **Outbound Buffer** | Batch URLs for forwarding to other workers | In-memory buffer, flush every 30-60 seconds |
| **Checkpoint Service** | Persist state to disk for crash recovery | Serialize frontier + Bloom filter every 5 min |
| **Gossip Service** | Peer discovery + failure detection | UDP heartbeats every 10 seconds |

### Why This Architecture?

1. **No single point of failure**: Every worker is identical and autonomous. Any can crash and recover.
2. **Deterministic ownership**: `hash(url) % N` means every worker knows exactly who owns every URL, with zero coordination.
3. **Minimal communication**: Workers only communicate to forward discovered URLs and heartbeats. All crawling + dedup is local.
4. **Horizontally scalable**: Add more workers → each handles fewer URLs. Rebalance via consistent hashing.

---

## 6️⃣ Deep Dives

### Deep Dive 1: Deterministic URL Partitioning via Consistent Hashing

**The Problem**: With 10,000 workers, how does every worker know which URLs it owns without a coordinator?

**Solution: `hash(url) % N` with Consistent Hashing**

```java
public class UrlPartitioner {
    private final int totalWorkers;
    private final ConsistentHashRing ring;

    public UrlPartitioner(int totalWorkers) {
        this.totalWorkers = totalWorkers;
        // 100 virtual nodes per worker for even distribution
        this.ring = new ConsistentHashRing(totalWorkers, 100);
    }

    /** Determine which worker owns this URL */
    public int getOwner(String url) {
        String normalized = UrlNormalizer.normalize(url);
        return ring.getNode(normalized);
    }

    /** Check if this URL belongs to me */
    public boolean isMyUrl(String url, int myWorkerId) {
        return getOwner(url) == myWorkerId;
    }
}

public class ConsistentHashRing {
    private final TreeMap<Long, Integer> ring = new TreeMap<>();

    public ConsistentHashRing(int totalWorkers, int virtualNodesPerWorker) {
        for (int worker = 0; worker < totalWorkers; worker++) {
            for (int vn = 0; vn < virtualNodesPerWorker; vn++) {
                long hash = murmurHash("worker-" + worker + "-vn-" + vn);
                ring.put(hash, worker);
            }
        }
    }

    public int getNode(String key) {
        long hash = murmurHash(key);
        Map.Entry<Long, Integer> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry(); // Wrap around
        }
        return entry.getValue();
    }

    private long murmurHash(String key) {
        return Hashing.murmur3_128()
            .hashString(key, StandardCharsets.UTF_8)
            .asLong();
    }
}
```

**Why Consistent Hashing over Simple Modulo?**
```
Simple hash(url) % N:
  ✗ If N changes (worker added/removed), EVERY URL changes owner
  ✗ Massive data migration needed

Consistent Hashing with Virtual Nodes:
  ✓ Adding/removing a worker only affects 1/N of URLs
  ✓ Virtual nodes ensure even distribution
  ✓ Graceful rebalancing when cluster size changes
```

---

### Deep Dive 2: URL Deduplication at Scale

**The Problem**: With 1 billion URLs per day across 10K workers, how do we ensure no URL is crawled twice? Without a shared database?

**Solution: Local Bloom Filter + RocksDB (Two-Tier Dedup)**

```java
public class UrlDeduplicator {
    // Tier 1: In-memory Bloom Filter (fast, probabilistic)
    private BloomFilter<String> bloomFilter;
    
    // Tier 2: On-disk RocksDB (exact, for Bloom false positives)
    private final RocksDB rocksDb;
    
    private static final int EXPECTED_URLS = 50_000_000;  // 50M per worker
    private static final double FALSE_POSITIVE_RATE = 0.001; // 0.1%

    public UrlDeduplicator(String dbPath) throws RocksDBException {
        this.bloomFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            EXPECTED_URLS, FALSE_POSITIVE_RATE);
        
        Options options = new Options().setCreateIfMissing(true);
        options.setBloomLocality(10);
        this.rocksDb = RocksDB.open(options, dbPath);
    }

    /**
     * Returns true if URL is new (never seen before).
     * Returns false if URL was already seen (skip it).
     * Thread-safe.
     */
    public synchronized boolean markIfNew(String url) {
        String normalized = UrlNormalizer.normalize(url);
        
        // Tier 1: Bloom Filter (very fast, < 1µs)
        if (!bloomFilter.mightContain(normalized)) {
            // Definitely new — add to both tiers
            bloomFilter.put(normalized);
            rocksDb.put(normalized.getBytes(), EMPTY_BYTES);
            return true; // NEW
        }
        
        // Bloom says "maybe seen" — could be false positive
        // Tier 2: Exact check in RocksDB (slower, ~50µs, but definitive)
        byte[] existing = rocksDb.get(normalized.getBytes());
        if (existing != null) {
            return false; // Already seen (confirmed)
        }
        
        // Bloom false positive — URL is actually new
        bloomFilter.put(normalized);
        rocksDb.put(normalized.getBytes(), EMPTY_BYTES);
        return true; // NEW (Bloom was wrong)
    }
}
```

**Why not just use a Bloom Filter alone?**
```
Bloom Filter alone:
  ✓ Fast (< 1µs)
  ✓ Small memory (72 MB for 50M URLs)
  ✗ False positives → we'd SKIP legitimate URLs (lost coverage)
  ✗ False positive rate grows as more URLs are added
  ✗ Can't recover from restart without persisting the entire bit array

Bloom Filter + RocksDB:
  ✓ Bloom catches 99.9% as "definitely new" → fast path
  ✓ RocksDB resolves the 0.1% "maybe seen" → no false skips
  ✓ RocksDB persists to disk → survives restarts
  ✓ RocksDB is append-mostly → very fast for this workload
```

---

### Deep Dive 3: Politeness — Respecting Robots.txt & Rate Limits

**The Problem**: We must not overload any single website. Legal and ethical requirement. Many workers may have URLs from the same domain.

**Key Insight**: Because `hash(url) % N` distributes by URL not by domain, one worker may have URLs from thousands of domains. But all URLs from the same domain-path combo go to the same worker (since the URL determines the owner).

```java
public class PolitenessEnforcer {
    private final ConcurrentHashMap<String, DomainState> domainStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RobotsTxtRules> robotsCache = new ConcurrentHashMap<>();

    /** Check if we can crawl this URL now, respecting politeness */
    public boolean canCrawl(CrawlUrl url) {
        String domain = url.getDomain();
        DomainState state = domainStates.computeIfAbsent(domain, 
            d -> new DomainState(d, getDefaultDelay()));
        
        // Check robots.txt
        RobotsTxtRules rules = getRobotsRules(domain);
        if (rules != null && rules.isDisallowed(url.getUrl())) {
            return false; // Disallowed by robots.txt
        }
        
        // Check rate limit
        return state.canRequestNow();
    }

    /** Record that we made a request to this domain */
    public void recordRequest(String domain) {
        DomainState state = domainStates.get(domain);
        if (state != null) {
            state.recordRequest();
        }
    }

    /** Fetch and cache robots.txt for a domain */
    private RobotsTxtRules getRobotsRules(String domain) {
        return robotsCache.computeIfAbsent(domain, d -> {
            try {
                String robotsUrl = "https://" + d + "/robots.txt";
                HttpResponse response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(robotsUrl))
                        .timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return RobotsTxtParser.parse(response.body());
                }
            } catch (Exception e) {
                log.debug("No robots.txt for {}", d);
            }
            return RobotsTxtRules.ALLOW_ALL; // No robots.txt = allow everything
        });
    }

    static class DomainState {
        private final String domain;
        private volatile Instant lastRequestAt = Instant.EPOCH;
        private final int delayMs;

        public boolean canRequestNow() {
            return Instant.now().isAfter(lastRequestAt.plusMillis(delayMs));
        }

        public void recordRequest() {
            this.lastRequestAt = Instant.now();
        }
    }
}
```

**Domain-Aware Frontier** — Instead of a single priority queue, use per-domain queues with a round-robin selector:

```java
public class PolitenessFrontier {
    // Per-domain FIFO queues (within a domain, crawl in order)
    private final Map<String, Queue<CrawlUrl>> domainQueues = new LinkedHashMap<>();
    private final Iterator<String> domainRoundRobin;
    private final PolitenessEnforcer politeness;

    /** Get the next URL that is safe to crawl right now */
    public CrawlUrl getNextCrawlable() {
        // Round-robin across domains to avoid starving any domain
        for (int attempts = 0; attempts < domainQueues.size(); attempts++) {
            String domain = getNextDomain(); // round-robin
            Queue<CrawlUrl> queue = domainQueues.get(domain);
            
            if (queue != null && !queue.isEmpty()) {
                CrawlUrl url = queue.peek();
                if (politeness.canCrawl(url)) {
                    return queue.poll(); // Safe to crawl now
                }
            }
        }
        return null; // All domains are rate-limited, wait
    }
}
```

---

### Deep Dive 4: Batched URL Forwarding — Minimizing Inter-Node Communication

**The Problem**: With 10K workers, each discovering ~1M URLs/day destined for other workers, naive per-URL forwarding would create 10 billion messages/day. Bandwidth is limited.

**Solution: Batching + Compression + Coalescing**

```java
public class OutboundUrlForwarder {
    // Buffer: target_worker_id → list of URLs to send
    private final Map<Integer, List<CrawlUrl>> buffers = new ConcurrentHashMap<>();
    private static final int BATCH_SIZE_THRESHOLD = 5_000;
    private static final int FLUSH_INTERVAL_MS = 30_000; // 30 seconds

    /** Called when a discovered URL belongs to another worker */
    public void enqueue(CrawlUrl url) {
        int targetWorker = url.getOwnerWorker();
        buffers.computeIfAbsent(targetWorker, k -> new ArrayList<>()).add(url);
        
        // Flush immediately if buffer is large
        if (buffers.get(targetWorker).size() >= BATCH_SIZE_THRESHOLD) {
            flushBuffer(targetWorker);
        }
    }

    /** Periodic flush — runs every 30 seconds */
    @Scheduled(fixedRate = 30_000)
    public void flushAll() {
        for (int targetWorker : buffers.keySet()) {
            flushBuffer(targetWorker);
        }
    }

    private void flushBuffer(int targetWorker) {
        List<CrawlUrl> urls = buffers.remove(targetWorker);
        if (urls == null || urls.isEmpty()) return;

        // Deduplicate within the batch (same URL discovered multiple times)
        Set<String> seen = new HashSet<>();
        List<CrawlUrl> deduped = urls.stream()
            .filter(u -> seen.add(u.getUrl()))
            .collect(Collectors.toList());

        // Create protobuf batch
        ForwardUrlBatch batch = ForwardUrlBatch.newBuilder()
            .setSourceWorkerId(myWorkerId)
            .setBatchId(batchIdGenerator.incrementAndGet())
            .addAllUrls(deduped.stream().map(this::toProto).collect(Collectors.toList()))
            .build();

        // Compress (URLs compress ~80-90% with gzip)
        byte[] compressed = GzipUtil.compress(batch.toByteArray());
        
        // Send with retry
        try {
            grpcClients.get(targetWorker).forwardUrls(compressed);
            metrics.counter("outbound.batches.success").increment();
        } catch (Exception e) {
            // On failure: persist to disk, retry later
            diskBuffer.persist(targetWorker, batch);
            metrics.counter("outbound.batches.failed").increment();
            log.warn("Failed to forward {} URLs to worker {}: {}", 
                     deduped.size(), targetWorker, e.getMessage());
        }
    }
}
```

**Communication Budget**:
```
Per worker per day:
  Discovered links:  1M URLs
  95% for other workers: 950K URLs
  Average URL: 100 bytes

  Uncompressed: 950K × 100 bytes = 95 MB/day
  After gzip (~85% compression): 95 MB × 0.15 = ~14 MB/day
  
  Per 30-second batch: 14 MB / 2880 batches = ~5 KB per batch
  → Extremely manageable even on constrained networks
```

---

### Deep Dive 5: Checkpoint & Crash Recovery

**The Problem**: Workers can crash at any time. On restart, they must resume where they left off without re-crawling everything and without losing their frontier.

```java
public class CheckpointService {
    private static final String CHECKPOINT_DIR = "/data/checkpoint/";
    private static final int CHECKPOINT_INTERVAL_MS = 300_000; // 5 minutes

    private final WorkerState state;

    /** Periodic checkpoint — runs every 5 minutes */
    @Scheduled(fixedRate = 300_000)
    public void checkpoint() {
        long startMs = System.currentTimeMillis();
        
        try {
            // 1. Write frontier to disk (serialized priority queue)
            String frontierPath = CHECKPOINT_DIR + "frontier.bin";
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new GZIPOutputStream(new FileOutputStream(frontierPath + ".tmp")))) {
                oos.writeObject(new ArrayList<>(state.getFrontier()));
            }
            Files.move(Path.of(frontierPath + ".tmp"), Path.of(frontierPath), 
                       StandardCopyOption.ATOMIC_MOVE);
            
            // 2. Bloom filter is backed by RocksDB (auto-persisted)
            // Just flush the WAL
            state.getSeenUrlsOnDisk().flush(new FlushOptions().setWaitForFlush(true));
            
            // 3. Save outbound buffers
            for (var entry : state.getOutboundBuffers().entrySet()) {
                String path = CHECKPOINT_DIR + "outbound/" + entry.getKey() + ".bin";
                SerializationUtil.writeToFile(path, entry.getValue());
            }
            
            // 4. Save metadata
            CheckpointMeta meta = new CheckpointMeta(
                Instant.now(),
                state.getFrontier().size(),
                state.getUrlsCrawled(),
                state.getBloomFilterSize()
            );
            JsonUtil.writeToFile(CHECKPOINT_DIR + "meta.json", meta);
            
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("Checkpoint complete in {}ms: frontier={}, crawled={}", 
                     durationMs, meta.frontierSize, meta.urlsCrawled);
            metrics.timer("checkpoint.duration_ms").record(durationMs, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            log.error("Checkpoint failed!", e);
            metrics.counter("checkpoint.failures").increment();
        }
    }

    /** Restore state from last checkpoint on startup */
    public WorkerState restore() {
        Path metaPath = Path.of(CHECKPOINT_DIR + "meta.json");
        if (!Files.exists(metaPath)) {
            log.info("No checkpoint found, starting fresh");
            return WorkerState.empty(workerId);
        }
        
        CheckpointMeta meta = JsonUtil.readFromFile(metaPath, CheckpointMeta.class);
        log.info("Restoring checkpoint from {}: frontier={}, crawled={}", 
                 meta.timestamp, meta.frontierSize, meta.urlsCrawled);
        
        // Restore frontier
        List<CrawlUrl> frontier = SerializationUtil.readFromFile(
            CHECKPOINT_DIR + "frontier.bin");
        
        // RocksDB restores automatically on open
        RocksDB seenUrls = RocksDB.open(new Options(), CHECKPOINT_DIR + "seen_urls");
        
        // Restore outbound buffers
        Map<Integer, List<CrawlUrl>> outbound = restoreOutboundBuffers();
        
        return new WorkerState(workerId, new PriorityQueue<>(frontier), 
                               seenUrls, outbound);
    }
}
```

---

### Deep Dive 6: Gossip Protocol for Peer Discovery & Failure Detection

**The Problem**: With no central coordinator, how do workers find each other and detect failures?

```java
public class GossipService {
    private final ConcurrentHashMap<Integer, PeerState> knownPeers = new ConcurrentHashMap<>();
    private static final int GOSSIP_INTERVAL_MS = 10_000;   // 10 seconds
    private static final int PEER_TIMEOUT_MS = 30_000;      // 3 missed heartbeats = dead
    private static final int GOSSIP_FANOUT = 3;             // Tell 3 random peers each cycle

    /** Send heartbeat to a few random peers every 10 seconds */
    @Scheduled(fixedRate = 10_000)
    public void gossip() {
        Heartbeat heartbeat = Heartbeat.newBuilder()
            .setWorkerId(myWorkerId)
            .setHost(myHost)
            .setPort(myPort)
            .setUrlsCrawled(state.getUrlsCrawled())
            .setFrontierSize(state.getFrontier().size())
            .setTimestamp(System.currentTimeMillis())
            .addAllKnownPeers(getRecentPeers())  // Share what we know
            .build();

        // Pick GOSSIP_FANOUT random peers
        List<PeerState> targets = pickRandomPeers(GOSSIP_FANOUT);
        for (PeerState peer : targets) {
            try {
                udpClient.send(peer.getHost(), peer.getPort(), heartbeat.toByteArray());
            } catch (Exception e) {
                // UDP is fire-and-forget, failures are expected
            }
        }
    }

    /** Called when we receive a heartbeat from another worker */
    public void onHeartbeatReceived(Heartbeat heartbeat) {
        // Update direct sender
        knownPeers.put(heartbeat.getWorkerId(), new PeerState(
            heartbeat.getWorkerId(), heartbeat.getHost(), 
            heartbeat.getPort(), System.currentTimeMillis()));
        
        // Learn about peers from gossip (transitive discovery)
        for (PeerInfo info : heartbeat.getKnownPeersList()) {
            knownPeers.putIfAbsent(info.getWorkerId(), new PeerState(
                info.getWorkerId(), info.getHost(), 
                info.getPort(), info.getLastSeen()));
        }
    }

    /** Detect and handle dead peers */
    @Scheduled(fixedRate = 10_000)
    public void detectDeadPeers() {
        long now = System.currentTimeMillis();
        for (var entry : knownPeers.entrySet()) {
            PeerState peer = entry.getValue();
            if (now - peer.getLastSeen() > PEER_TIMEOUT_MS) {
                log.warn("Worker {} appears dead (last seen {}ms ago)", 
                         peer.getWorkerId(), now - peer.getLastSeen());
                // Buffer URLs for this peer on disk (will send when it recovers)
                // Optionally: trigger consistent hashing rebalance
            }
        }
    }
}
```

**Gossip Convergence Time**:
```
With 10,000 workers and fanout=3:
  Each gossip round, each worker tells 3 peers
  Information spreads exponentially: 1 → 3 → 9 → 27 → ...
  log₃(10000) ≈ 8.4 rounds to reach all workers
  At 10 seconds per round → ~85 seconds for full convergence
  
  A new worker joining is known by all peers within ~90 seconds.
  A dead worker is detected within 30 seconds (3 missed heartbeats).
```

---

### Deep Dive 7: Anti-Entropy Sync — Detecting Missed URLs

**The Problem**: Network failures can cause URL forwarding batches to be lost. Without anti-entropy, some URLs would never be crawled.

```java
public class AntiEntropyService {
    private final UrlDeduplicator dedup;
    private static final int SYNC_INTERVAL_MS = 600_000; // Every 10 minutes

    /**
     * Periodically compare recent crawl state with neighboring workers.
     * Uses Bloom filter digests to efficiently detect missing URLs.
     */
    @Scheduled(fixedRate = 600_000)
    public void runAntiEntropy() {
        // Build a Bloom filter of URLs we've crawled in the last hour
        BloomFilter<String> recentlyCrawled = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8), 100_000, 0.01);
        
        dedup.getRecentlyCrawled(Duration.ofHours(1))
             .forEach(recentlyCrawled::put);
        
        byte[] digest = serializeBloomFilter(recentlyCrawled);
        
        // Send to a few random peers that are our "neighbors" in the hash ring
        List<Integer> neighbors = partitioner.getNeighborWorkers(myWorkerId, 3);
        for (int neighbor : neighbors) {
            try {
                AntiEntropyResponse response = grpcClients.get(neighbor)
                    .antiEntropy(AntiEntropyRequest.newBuilder()
                        .setWorkerId(myWorkerId)
                        .setBloomFilterDigest(ByteString.copyFrom(digest))
                        .setSinceTimestamp(Instant.now().minus(Duration.ofHours(1)).toEpochMilli())
                        .build());
                
                // Process any URLs the neighbor has that we're missing
                for (String missingUrl : response.getMissingUrlsList()) {
                    if (partitioner.isMyUrl(missingUrl, myWorkerId)) {
                        dedup.markIfNew(missingUrl);
                        // Add to frontier if new
                    }
                }
                
                log.info("Anti-entropy with worker {}: {} missing URLs recovered",
                         neighbor, response.getMissingUrlsCount());
                
            } catch (Exception e) {
                log.debug("Anti-entropy with worker {} failed: {}", neighbor, e.getMessage());
            }
        }
    }
}
```

---

### Deep Dive 8: Handling Worker Failures & Rebalancing

**The Problem**: When a worker permanently dies, the URLs it owns are orphaned. No other worker will crawl them.

```java
public class ClusterRebalancer {
    private final GossipService gossip;
    private final ConsistentHashRing ring;
    private static final long PERMANENT_DEATH_THRESHOLD_MS = 600_000; // 10 minutes

    /**
     * If a worker has been unreachable for > 10 minutes,
     * assume it's permanently dead and rebalance its URLs.
     */
    @Scheduled(fixedRate = 60_000)
    public void checkForPermanentFailures() {
        long now = System.currentTimeMillis();
        Set<Integer> deadWorkers = new HashSet<>();

        for (var entry : gossip.getKnownPeers().entrySet()) {
            if (now - entry.getValue().getLastSeen() > PERMANENT_DEATH_THRESHOLD_MS) {
                deadWorkers.add(entry.getKey());
            }
        }

        if (!deadWorkers.isEmpty() && !deadWorkers.equals(previousDeadSet)) {
            log.warn("Detected permanently dead workers: {}", deadWorkers);
            
            // Remove dead workers from hash ring
            ConsistentHashRing newRing = ring.withoutWorkers(deadWorkers);
            
            // Check if any of the dead workers' URLs now map to ME
            // (Only the "successor" worker on the hash ring takes over)
            for (int deadWorker : deadWorkers) {
                List<Long> reassignedSlots = ring.getSlotsForWorker(deadWorker);
                for (long slot : reassignedSlots) {
                    int newOwner = newRing.getNodeForSlot(slot);
                    if (newOwner == myWorkerId) {
                        log.info("Taking over slot {} from dead worker {}", slot, deadWorker);
                        // Drain any buffered URLs for the dead worker
                        // These URLs now belong to me
                        List<CrawlUrl> orphanedUrls = outboundBuffer.drainBuffer(deadWorker);
                        orphanedUrls.forEach(url -> frontier.add(url));
                    }
                }
            }
            
            // Update the active ring
            this.ring = newRing;
            previousDeadSet = deadWorkers;
        }
    }
}
```

**Rebalancing Properties**:
```
With consistent hashing (100 virtual nodes per worker):
  - Losing 1 worker out of 10,000 → only 0.01% of URLs need reassignment
  - Each neighboring worker takes over a tiny slice
  - No thundering herd, no massive data movement
  - Worker recovery: when dead worker comes back, it reclaims its slots
```

---

### Deep Dive 9: The Main Crawl Loop — Putting It All Together

```java
public class CrawlWorker {
    private final int workerId;
    private final PolitenessFrontier frontier;
    private final UrlDeduplicator dedup;
    private final PolitenessEnforcer politeness;
    private final OutboundUrlForwarder forwarder;
    private final UrlPartitioner partitioner;
    private final PageStore pageStore;
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(50);

    /** Main crawl loop — runs continuously */
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 1. Get next URL that's safe to crawl (respects politeness)
                CrawlUrl url = frontier.getNextCrawlable();
                if (url == null) {
                    Thread.sleep(100); // All domains are rate-limited, wait briefly
                    continue;
                }

                // 2. Submit to thread pool for async fetching
                fetchPool.submit(() -> crawlUrl(url));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void crawlUrl(CrawlUrl url) {
        try {
            // 3. Fetch the page
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url.getUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "MyCrawler/1.0")
                    .build(),
                HttpResponse.BodyHandlers.ofString());

            politeness.recordRequest(url.getDomain());

            if (response.statusCode() != 200) {
                metrics.counter("crawl.non_200").increment();
                return;
            }

            // 4. Parse HTML and extract links
            List<String> links = HtmlParser.extractLinks(response.body(), url.getUrl());
            
            // 5. Store the page locally
            pageStore.store(url.getUrl(), response.body(), response.headers());
            metrics.counter("crawl.success").increment();

            // 6. Process discovered links
            for (String link : links) {
                String normalized = UrlNormalizer.normalize(link);
                int owner = partitioner.getOwner(normalized);

                if (owner == workerId) {
                    // Mine — check dedup and add to frontier
                    if (dedup.markIfNew(normalized)) {
                        frontier.add(new CrawlUrl(normalized, url.getDepth() + 1));
                    }
                } else {
                    // Not mine — buffer for forwarding
                    forwarder.enqueue(new CrawlUrl(normalized, owner, url.getDepth() + 1));
                }
            }

        } catch (Exception e) {
            metrics.counter("crawl.error").increment();
            log.debug("Failed to crawl {}: {}", url.getUrl(), e.getMessage());
        }
    }
}
```

---

### Deep Dive 10: Observability Without a Central Monitoring System

**The Problem**: With no central infrastructure, how do we monitor 10,000 workers?

```java
public class DecentralizedMetrics {
    // Each worker maintains its own metrics locally
    private final AtomicLong urlsCrawled = new AtomicLong();
    private final AtomicLong urlsInFrontier = new AtomicLong();
    private final AtomicLong bytesDownloaded = new AtomicLong();
    private final AtomicLong errorsCount = new AtomicLong();
    private final AtomicLong forwardedUrls = new AtomicLong();

    /** Expose metrics via HTTP endpoint on each worker (pull-based) */
    // GET http://worker-42:9090/metrics
    // A lightweight Prometheus-compatible endpoint
    
    /** Aggregate metrics via gossip protocol */
    // Each heartbeat includes key metrics
    // Any worker can compute a cluster-wide estimate:
    
    public ClusterStats estimateClusterStats() {
        long totalCrawled = 0;
        long totalFrontier = 0;
        int aliveWorkers = 0;
        
        for (PeerState peer : gossip.getKnownPeers().values()) {
            if (peer.isAlive()) {
                totalCrawled += peer.getUrlsCrawled();
                totalFrontier += peer.getFrontierSize();
                aliveWorkers++;
            }
        }
        
        return new ClusterStats(totalCrawled, totalFrontier, aliveWorkers,
            (double) totalCrawled / Math.max(1, aliveWorkers)); // avg per worker
    }
    
    /** Periodic local log for debugging */
    @Scheduled(fixedRate = 60_000)
    public void logStats() {
        ClusterStats stats = estimateClusterStats();
        log.info("Worker {} | Crawled: {} | Frontier: {} | Errors: {} | " +
                 "Cluster estimate: {} total crawled, {}/{} workers alive",
                 workerId, urlsCrawled, urlsInFrontier, errorsCount,
                 stats.totalCrawled, stats.aliveWorkers, TOTAL_WORKERS);
    }
}
```

**Monitoring Approach**:
```
Option A: Each worker exposes /metrics endpoint (Prometheus pull)
  - Lightweight HTTP server on each worker
  - Prometheus scrapes all 10K workers
  - Works if you have a monitoring system outside the crawler cluster

Option B: Gossip-based aggregate metrics (fully decentralized)
  - Each heartbeat includes key counters
  - Any worker can compute cluster-wide estimates
  - No external system needed

Option C: Log shipping to a shared log aggregator
  - Workers write structured logs to local disk
  - Periodically ship compressed logs to a shared NFS or log server
  - Best for post-hoc analysis
```

---

## 📊 Summary: Key Trade-offs & Decision Matrix

| Decision | Options Considered | Chosen | Why |
|----------|-------------------|--------|-----|
| **URL ownership** | Central coordinator / Consistent hashing / Random | Consistent hashing (`hash(url) % N`) | Deterministic, no coordination, graceful rebalancing |
| **Deduplication** | Shared DB / Bloom filter / Local RocksDB | Bloom Filter + RocksDB (two-tier) | Fast + exact; no shared state needed |
| **Communication** | Per-URL forwarding / Batched / No forwarding | Batched every 30s with gzip compression | 85% compression; ~5 KB per batch per peer |
| **Frontier** | Single PQ / Per-domain queues / Redis | Per-domain queues with round-robin | Politeness-aware; avoids domain starvation |
| **Failure detection** | Heartbeats / Central monitor / Gossip | Gossip protocol (10s interval, fanout=3) | Fully decentralized; ~90s convergence |
| **Crash recovery** | Replay from scratch / Checkpoint to disk | Checkpoint every 5 min to local SSD | ~5 min max re-crawl on restart |
| **Consistency** | Strong / Eventual / Best-effort | Eventual with anti-entropy repair | Anti-entropy catches missed URLs every 10 min |
| **Rebalancing** | Manual / Automatic / None | Automatic via consistent hashing ring update | Only successor workers take over; minimal data movement |
| **Content storage** | Shared storage / Local disk / HDFS | Local disk (gzip'd files per domain) | No shared infrastructure; 2 TB SSD per worker |
| **Politeness** | Global rate limiter / Per-worker local | Local per-domain rate limiter | Each worker tracks its own domains; no cross-worker coordination needed |

## 🏗️ Technology Stack Summary

```
┌─────────────────────────────────────────────────────────┐
│  PER WORKER (× 10,000)                                  │
│  ─────────────────────                                   │
│  Java 17+ (Crawl Engine)                                │
│  Netty / Java HttpClient (async HTTP)                   │
│  RocksDB (on-disk URL dedup)                            │
│  Guava BloomFilter (in-memory URL dedup)                │
│  gRPC (inter-worker URL forwarding)                     │
│  Protocol Buffers (serialization)                       │
│  Local SSD (page storage + checkpoints)                 │
│                                                          │
│  PROTOCOLS                                               │
│  ─────────                                               │
│  Gossip (UDP heartbeats, peer discovery)                │
│  Consistent Hashing (URL partitioning)                  │
│  Anti-Entropy (Bloom filter digest exchange)            │
│                                                          │
│  NO EXTERNAL DEPENDENCIES                                │
│  ─────────────────────────                               │
│  No Kafka. No Redis. No PostgreSQL. No S3.              │
│  Fully self-contained, peer-to-peer.                    │
└─────────────────────────────────────────────────────────┘
```

## 🎯 Interview Talking Points

1. **"`hash(url) % N` is the entire coordination mechanism"** — Every worker independently computes who owns every URL. No leader election, no distributed lock, no shared state. This is the core insight.

2. **"Bloom Filter + RocksDB = two-tier dedup"** — Bloom catches 99.9% in < 1µs (fast path). RocksDB resolves the 0.1% ambiguous cases exactly and persists across restarts. Neither alone is sufficient.

3. **"Batching turns 10 billion messages into 5 KB bursts"** — Instead of forwarding URLs one-by-one, batch every 30 seconds + gzip. 85% compression on URL data. Communication budget drops to ~14 MB/day per worker.

4. **"Gossip protocol replaces a coordinator"** — With fanout=3 and 10s intervals, the entire 10K cluster converges in ~90 seconds. No single point of failure for discovery or failure detection.

5. **"Politeness is local, not global"** — Each worker tracks per-domain rate limits for URLs it owns. Since URL ownership is deterministic, no two workers ever crawl the same domain's URL concurrently.

6. **"Checkpoint every 5 minutes, not every URL"** — On crash, we lose at most 5 minutes of work. Re-crawling a few hundred URLs is idempotent and cheap. Checkpointing every URL would kill disk throughput.

---

### Deep Dive 11: URL Normalization & Trap Detection

**The Problem**: The web is full of URL variations that point to the same page, and "spider traps" — infinite URL spaces generated by dynamic sites (e.g., calendars: `site.com/calendar?date=2025-01-01`, `site.com/calendar?date=2025-01-02`, ...).

**URL Normalization**:

```java
public class UrlNormalizer {

    /**
     * Normalize a URL to its canonical form.
     * Ensures equivalent URLs produce the same string.
     */
    public static String normalize(String rawUrl) {
        try {
            URI uri = new URI(rawUrl).normalize(); // Resolve /../ and /./
            
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "http";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            int port = uri.getPort();
            String path = uri.getPath() != null ? uri.getPath() : "/";
            String query = uri.getQuery();
            
            // 1. Remove default ports
            if ((scheme.equals("http") && port == 80) || 
                (scheme.equals("https") && port == 443)) {
                port = -1;
            }
            
            // 2. Remove fragment (#section)
            // Already handled by URI — fragments are not included
            
            // 3. Remove trailing slash on root paths
            if (path.equals("/") && query == null) {
                path = "";
            }
            
            // 4. Remove "www." prefix
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            
            // 5. Sort query parameters for consistency
            if (query != null) {
                String[] params = query.split("&");
                Arrays.sort(params);
                query = String.join("&", params);
            }
            
            // 6. Decode percent-encoding for unreserved characters
            path = decodeUnreservedChars(path);
            
            // 7. Handle IDN (Internationalized Domain Names) → Punycode
            host = IDN.toASCII(host);
            
            // Reconstruct
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port > 0) sb.append(":").append(port);
            sb.append(path.isEmpty() ? "/" : path);
            if (query != null) sb.append("?").append(query);
            
            return sb.toString();
            
        } catch (URISyntaxException e) {
            return rawUrl.toLowerCase(); // Best effort
        }
    }

    /**
     * Decode percent-encoded characters that don't need encoding.
     * e.g., %41 → A, %7E → ~
     */
    private static String decodeUnreservedChars(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '%' && i + 2 < input.length()) {
                int code = Integer.parseInt(input.substring(i + 1, i + 3), 16);
                if (isUnreserved(code)) {
                    result.append((char) code);
                    i += 2;
                    continue;
                }
            }
            result.append(input.charAt(i));
        }
        return result.toString();
    }
    
    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || 
               (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~';
    }
}

// Normalization examples:
// "HTTP://WWW.Example.COM:80/path"  → "http://example.com/path"
// "http://example.com/a/../b"       → "http://example.com/b"
// "http://example.com/page?b=2&a=1" → "http://example.com/page?a=1&b=2"
// "http://example.com/%7Euser"      → "http://example.com/~user"
// "http://example.com/page#section" → "http://example.com/page"
```

**Spider Trap Detection**:

```java
public class SpiderTrapDetector {
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_PATH_DEPTH = 15;
    private static final int MAX_QUERY_PARAMS = 10;
    private static final int MAX_URLS_PER_DOMAIN_PER_HOUR = 10_000;

    private final Map<String, AtomicInteger> domainUrlCounts = new ConcurrentHashMap<>();

    /**
     * Detect if a URL is likely part of a spider trap.
     * Returns true if the URL should be SKIPPED.
     */
    public boolean isSuspicious(CrawlUrl url) {
        String urlStr = url.getUrl();
        
        // 1. URL too long (likely auto-generated)
        if (urlStr.length() > MAX_URL_LENGTH) {
            return true;
        }
        
        // 2. Too many path segments (deep nesting = likely trap)
        long pathDepth = urlStr.chars().filter(c -> c == '/').count();
        if (pathDepth > MAX_PATH_DEPTH) {
            return true;
        }
        
        // 3. Too many query parameters
        if (urlStr.contains("?")) {
            String query = urlStr.substring(urlStr.indexOf('?') + 1);
            long paramCount = query.chars().filter(c -> c == '&').count() + 1;
            if (paramCount > MAX_QUERY_PARAMS) {
                return true;
            }
        }
        
        // 4. Repeating path patterns (e.g., /a/b/a/b/a/b)
        if (hasRepeatingPattern(urlStr)) {
            return true;
        }
        
        // 5. Too many URLs from same domain in short time (calendar trap)
        String domain = url.getDomain();
        AtomicInteger count = domainUrlCounts.computeIfAbsent(domain, d -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_URLS_PER_DOMAIN_PER_HOUR) {
            log.warn("Domain {} exceeds URL limit, possible spider trap", domain);
            return true;
        }
        
        // 6. Crawl depth too high (too many hops from seed)
        if (url.getDepth() > 20) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasRepeatingPattern(String url) {
        String path = url.replaceFirst("https?://[^/]+", ""); // Extract path
        String[] segments = path.split("/");
        if (segments.length < 4) return false;
        
        // Check for repeating pairs
        for (int len = 1; len <= segments.length / 2; len++) {
            boolean repeating = true;
            for (int i = len; i < segments.length; i++) {
                if (!segments[i].equals(segments[i % len])) {
                    repeating = false;
                    break;
                }
            }
            if (repeating && segments.length / len >= 2) return true;
        }
        return false;
    }
    
    /** Reset hourly domain counts */
    @Scheduled(fixedRate = 3_600_000)
    public void resetHourlyCounts() {
        domainUrlCounts.clear();
    }
}
```

**Common Spider Traps**:
```
1. Calendar pages:   site.com/cal?date=2025-01-01, 2025-01-02, ... (infinite dates)
2. Session IDs:      site.com/page?sid=abc123 (new session = new URL)
3. Sort parameters:  site.com/products?sort=price&page=1, page=2, ... (thousands of pages)
4. Relative loops:   site.com/a/b/../a/b/../a/b/ (path traversal loops)
5. Soft 404s:        site.com/anything → returns 200 OK with "page not found" content
6. Counter pages:    site.com/view/1, /view/2, /view/3 ... (auto-incrementing IDs)

Detection strategy:
  - URL length limits
  - Path depth limits  
  - Repeating pattern detection
  - Per-domain URL count caps
  - Content fingerprinting (detect soft 404s)
```

---

### Deep Dive 12: Priority-Based Crawling & Crawl Budget Allocation

**The Problem**: Not all URLs are equally important. Google.com homepage is more valuable than a random blog's 50th page. With a crawl budget of 1B URLs/day, we need to prioritize.

**Priority Scoring**:

```java
public class UrlPrioritizer {
    
    /**
     * Compute priority score for a URL. Lower score = higher priority.
     * Score range: 1 (most important) to 1000 (least important).
     */
    public int computePriority(CrawlUrl url) {
        int score = 500; // Default: medium priority
        
        // 1. Depth penalty: deeper pages are less important
        // Seed URLs (depth 0) = high priority, depth 10+ = low
        score += url.getDepth() * 30;
        
        // 2. Domain authority bonus
        // Well-known domains get priority (could be from a pre-computed list)
        if (isHighAuthorityDomain(url.getDomain())) {
            score -= 200;
        }
        
        // 3. Path heuristics
        // Homepage ("/") is most important, deep paths less so
        String path = new URI(url.getUrl()).getPath();
        if (path == null || path.equals("/") || path.equals("")) {
            score -= 100; // Homepage bonus
        }
        if (path != null && path.contains("/tag/") || path.contains("/page/")) {
            score += 100; // Pagination/tag pages are less valuable
        }
        
        // 4. Freshness: recently discovered URLs get a slight boost
        long ageMinutes = Duration.between(url.getDiscoveredAt(), Instant.now()).toMinutes();
        if (ageMinutes < 60) {
            score -= 50; // Discovered in last hour → boost
        }
        
        // 5. Content type hints from URL
        if (url.getUrl().endsWith(".pdf") || url.getUrl().endsWith(".doc")) {
            score += 200; // Documents are lower priority than HTML
        }
        if (url.getUrl().contains("/api/") || url.getUrl().contains("/rss")) {
            score += 300; // API/RSS endpoints usually not useful for crawling
        }
        
        // Clamp to valid range
        return Math.max(1, Math.min(1000, score));
    }

    private boolean isHighAuthorityDomain(String domain) {
        // Could be loaded from a file: top 10K domains by PageRank / traffic
        Set<String> topDomains = Set.of(
            "wikipedia.org", "github.com", "stackoverflow.com",
            "nytimes.com", "bbc.com", "reuters.com"
            // ... thousands more
        );
        return topDomains.contains(domain);
    }
}
```

**Crawl Budget per Domain**:

```java
public class CrawlBudgetManager {
    // Limit how many URLs we crawl per domain per day
    // Prevents any single domain from consuming all resources
    
    private final Map<String, DomainBudget> budgets = new ConcurrentHashMap<>();
    
    public static class DomainBudget {
        final String domain;
        final int dailyLimit;
        final AtomicInteger crawledToday = new AtomicInteger(0);
        
        public DomainBudget(String domain) {
            this.domain = domain;
            // High-authority domains get larger budgets
            if (UrlPrioritizer.isHighAuthorityDomain(domain)) {
                this.dailyLimit = 50_000;  // Crawl up to 50K pages/day
            } else {
                this.dailyLimit = 1_000;   // Default: 1K pages/day
            }
        }
        
        public boolean hasRemainingBudget() {
            return crawledToday.get() < dailyLimit;
        }
        
        public void recordCrawl() {
            crawledToday.incrementAndGet();
        }
    }

    public boolean canCrawlDomain(String domain) {
        DomainBudget budget = budgets.computeIfAbsent(domain, DomainBudget::new);
        return budget.hasRemainingBudget();
    }
    
    /** Reset daily budgets at midnight */
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyBudgets() {
        budgets.values().forEach(b -> b.crawledToday.set(0));
        log.info("Reset daily crawl budgets for {} domains", budgets.size());
    }
}
```

**Priority Queue Integration**:

```java
public class PrioritizedFrontier {
    // Multiple priority levels (buckets) for efficient scheduling
    private static final int NUM_BUCKETS = 10;
    
    // Bucket 0 = highest priority (scores 1-100)
    // Bucket 9 = lowest priority (scores 901-1000)
    private final List<Queue<CrawlUrl>> buckets;
    
    // Weighted selection: higher buckets get more attention
    // Bucket 0: 50% of picks, Bucket 1: 20%, Bucket 2: 10%, ...
    private static final int[] WEIGHTS = {50, 20, 10, 5, 5, 3, 3, 2, 1, 1};
    
    public void add(CrawlUrl url) {
        int bucket = Math.min(NUM_BUCKETS - 1, url.getPriority() / 100);
        buckets.get(bucket).add(url);
    }
    
    /** Select next URL using weighted random across priority buckets */
    public CrawlUrl pollNext() {
        int totalWeight = Arrays.stream(WEIGHTS).sum();
        int rand = ThreadLocalRandom.current().nextInt(totalWeight);
        
        int cumulative = 0;
        for (int i = 0; i < NUM_BUCKETS; i++) {
            cumulative += WEIGHTS[i];
            if (rand < cumulative) {
                CrawlUrl url = buckets.get(i).poll();
                if (url != null) return url;
                // Bucket empty, try next
            }
        }
        
        // Fallback: return from any non-empty bucket
        for (Queue<CrawlUrl> bucket : buckets) {
            CrawlUrl url = bucket.poll();
            if (url != null) return url;
        }
        return null; // All buckets empty
    }
    
    public int totalSize() {
        return buckets.stream().mapToInt(Queue::size).sum();
    }
}

// Priority distribution effect:
// If frontier has 1M URLs across all buckets:
//   ~50% of crawl effort goes to bucket 0 (highest priority)
//   ~20% goes to bucket 1
//   Only ~1% goes to bucket 9 (lowest priority)
// This ensures important pages are crawled first, but no page is starved
```

---

### Deep Dive 13: Content Deduplication — Detecting Near-Duplicate Pages

**The Problem**: Many websites have duplicate or near-duplicate content (mirrors, syndication, printer-friendly versions). Crawling duplicates wastes bandwidth and storage.

**Solution: SimHash (Locality-Sensitive Hashing)**

```java
public class ContentDeduplicator {
    private static final int SIMHASH_BITS = 64;
    private static final int HAMMING_THRESHOLD = 3; // Pages within 3-bit difference = near-duplicate
    
    private final Map<Long, String> simHashIndex = new ConcurrentHashMap<>(); // simhash → first URL seen

    /**
     * Compute SimHash fingerprint of page content.
     * Similar pages produce similar hashes (small Hamming distance).
     */
    public long computeSimHash(String htmlContent) {
        // Extract visible text (strip HTML tags)
        String text = HtmlParser.extractText(htmlContent);
        
        // Tokenize into shingles (n-grams)
        List<String> shingles = generateShingles(text, 3); // 3-word shingles
        
        int[] vector = new int[SIMHASH_BITS];
        
        for (String shingle : shingles) {
            long hash = Hashing.murmur3_128()
                .hashString(shingle, StandardCharsets.UTF_8)
                .asLong();
            
            for (int i = 0; i < SIMHASH_BITS; i++) {
                if (((hash >> i) & 1) == 1) {
                    vector[i]++;
                } else {
                    vector[i]--;
                }
            }
        }
        
        // Convert to binary fingerprint
        long simHash = 0;
        for (int i = 0; i < SIMHASH_BITS; i++) {
            if (vector[i] > 0) {
                simHash |= (1L << i);
            }
        }
        return simHash;
    }

    /**
     * Check if this content is a near-duplicate of something we've already crawled.
     * Returns the URL of the duplicate, or null if content is unique.
     */
    public String findDuplicate(String url, String htmlContent) {
        long simHash = computeSimHash(htmlContent);
        
        // Check against all stored hashes (in practice, use a more efficient index)
        for (Map.Entry<Long, String> entry : simHashIndex.entrySet()) {
            int hammingDistance = Long.bitCount(simHash ^ entry.getKey());
            if (hammingDistance <= HAMMING_THRESHOLD) {
                return entry.getValue(); // Near-duplicate found
            }
        }
        
        // No duplicate — store this hash
        simHashIndex.put(simHash, url);
        return null;
    }

    private List<String> generateShingles(String text, int n) {
        String[] words = text.toLowerCase().split("\\s+");
        List<String> shingles = new ArrayList<>();
        for (int i = 0; i <= words.length - n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) sb.append(" ");
                sb.append(words[i + j]);
            }
            shingles.add(sb.toString());
        }
        return shingles;
    }
}

// SimHash properties:
// - Two identical pages → identical SimHash (Hamming distance = 0)
// - Two nearly identical pages → Hamming distance ≤ 3
// - Two completely different pages → Hamming distance ~32 (half of 64 bits)
// 
// Use case: skip crawling "print version" of a page we already crawled
// Memory: 8 bytes per page × 100K pages/worker = 800 KB (trivial)
```

---

### Deep Dive 14: Link Graph Construction for PageRank

**The Problem**: After crawling, we want to compute PageRank or other link-analysis metrics. Each worker has a partial view of the link graph.

```java
public class LinkGraphBuilder {
    // Local adjacency list: URL → list of outbound links
    // Only stores links where the SOURCE URL belongs to this worker
    private final Map<String, List<String>> adjacencyList = new HashMap<>();
    
    // Reverse index: URL → list of pages that link TO this URL
    private final Map<String, List<String>> inboundLinks = new HashMap<>();
    
    /**
     * Record a link discovered during crawling.
     * Called for every <a href> extracted from a crawled page.
     */
    public void recordLink(String sourceUrl, String targetUrl) {
        // Forward edge (outbound)
        adjacencyList.computeIfAbsent(sourceUrl, k -> new ArrayList<>()).add(targetUrl);
        
        // Reverse edge (inbound) — only if target is owned by this worker
        if (partitioner.isMyUrl(targetUrl, myWorkerId)) {
            inboundLinks.computeIfAbsent(targetUrl, k -> new ArrayList<>()).add(sourceUrl);
        }
    }
    
    /**
     * Export link graph partition for offline PageRank computation.
     * Written to local disk in edge-list format.
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void exportLinkGraph() {
        String path = "/data/link-graph/" + LocalDate.now() + "/edges.gz";
        
        try (var writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(path))))) {
            
            for (var entry : adjacencyList.entrySet()) {
                String source = entry.getKey();
                for (String target : entry.getValue()) {
                    writer.write(source + "\t" + target);
                    writer.newLine();
                }
            }
            
            log.info("Exported link graph: {} source URLs, {} total edges",
                     adjacencyList.size(), 
                     adjacencyList.values().stream().mapToInt(List::size).sum());
                     
        } catch (IOException e) {
            log.error("Failed to export link graph", e);
        }
    }
    
    /**
     * Simple local PageRank approximation.
     * Full PageRank requires a MapReduce-style distributed computation,
     * but this gives a rough estimate using only local data.
     */
    public Map<String, Double> computeLocalPageRank(int iterations) {
        Map<String, Double> ranks = new HashMap<>();
        double dampingFactor = 0.85;
        int totalPages = adjacencyList.size();
        
        // Initialize all ranks to 1/N
        for (String url : adjacencyList.keySet()) {
            ranks.put(url, 1.0 / totalPages);
        }
        
        for (int iter = 0; iter < iterations; iter++) {
            Map<String, Double> newRanks = new HashMap<>();
            
            for (String url : adjacencyList.keySet()) {
                double inboundScore = 0.0;
                List<String> inbound = inboundLinks.getOrDefault(url, Collections.emptyList());
                
                for (String source : inbound) {
                    double sourceRank = ranks.getOrDefault(source, 1.0 / totalPages);
                    int outDegree = adjacencyList.getOrDefault(source, Collections.emptyList()).size();
                    if (outDegree > 0) {
                        inboundScore += sourceRank / outDegree;
                    }
                }
                
                double newRank = (1 - dampingFactor) / totalPages + dampingFactor * inboundScore;
                newRanks.put(url, newRank);
            }
            
            ranks = newRanks;
        }
        
        return ranks;
    }
}
```

**Distributed PageRank** (for full accuracy, requires coordination):
```
Phase 1: Each worker exports its link graph partition to disk
Phase 2: Use a separate MapReduce job (Hadoop/Spark) to:
  a. Collect all link graph partitions
  b. Run iterative PageRank across the full graph
  c. Output final rankings

This is a POST-PROCESSING step, not part of the real-time crawl loop.
The crawler focuses on fetching; PageRank is computed offline.
```

---

**References**:
- Google's original web crawler architecture (Brin & Page, 1998)
- Mercator: A Scalable, Extensible Web Crawler (Heydon & Najork, 1999)
- IRLbot: Scaling to 6 Billion Pages and Beyond (Lee et al., 2008)
- Consistent Hashing and Random Trees (Karger et al., 1997)
- Gossip Protocols: SWIM (Scalable Weakly-consistent Infection-style Membership)

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 14 topics (choose 2-3 based on interviewer interest)
