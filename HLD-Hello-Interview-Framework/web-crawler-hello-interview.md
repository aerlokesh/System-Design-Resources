# Web Crawler — Hello Interview Framework

> **Question**: Design a highly scalable web crawler that can crawl billions of web pages. The system should fetch pages, extract content and links, handle deduplication, and store crawled content efficiently.
>
> **Variants asked**:
> - *Standard (Google/Amazon)*: Design a web crawler with centralized coordination (scheduler, service host, blob store, DNS resolver).
> - *Decentralized (Meta/Bloomberg)*: Design a distributed web crawler using 10,000 machines with no centralized components.
>
> **Difficulty**: Hard | **Level**: Senior/Staff

---

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
  - [Variant A: Centralized Architecture (Grokking/Educative)](#variant-a-centralized-architecture-grokkingEducative)
  - [Variant B: Decentralized P2P Architecture (Meta/Bloomberg)](#variant-b-decentralized-p2p-architecture-metabloomberg)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## Introduction & Context

A **web crawler** is a bot that systematically scours the World Wide Web (WWW) for content, starting from a pool of seed URLs. It saves the content in data stores for later use. Efficient storage and retrieval are critical for a robust system.

Crawlers fetch web pages, parse content, and extract URLs for further crawling. This is the foundation of search engines. The crawler's output feeds into subsequent stages:
- **Indexing** and content processing
- **Relevance scoring** (e.g., PageRank)
- **URL frontier management**
- **Analytics**

### Benefits of a Web Crawler
Beyond data collection, web crawlers provide:
- **Web page testing**: Validating links and HTML structures
- **Web page monitoring**: Tracking content or structure updates
- **Site mirroring**: Creating mirrors of popular websites
- **Copyright infringement checks**: Detecting unauthorized content usage

### Challenges
- **Crawler traps**: Infinite loops caused by dynamic links or calendar pages
- **Uneven load**: Some domains contain significantly more pages, requiring load balancing
- **DNS lookup latency**: Frequent DNS lookups slow down the process
- **Scalability**: The system must handle millions of seed URLs and distribute the load across multiple servers

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Crawl the Web**: Starting from seed URLs, fetch web pages, extract links, and recursively crawl discovered URLs
2. **Store Crawled Content**: Extract and store content in a blob store for indexing and ranking
3. **URL Deduplication**: Never crawl the same URL twice — detect via URL checksums
4. **Content Deduplication**: Never store the same content twice — detect via document checksums
5. **Politeness**: Respect `robots.txt`, enforce per-domain crawl rate limits (e.g., max 1 request/sec per domain)
6. **Scheduling**: Regularly schedule recrawling to update records based on priority and freshness

#### Nice to Have (P1)
- Priority-based crawling (important pages first)
- Incremental re-crawling (revisit pages periodically based on change frequency)
- Content deduplication (detect near-duplicate pages via SimHash)
- Link graph construction for PageRank computation
- Extensibility to support multiple protocols (HTTP, FTP) and MIME types (images, videos)
- Custom on-demand crawling beyond routine schedules (Improved UI)

#### Key Interview Questions to Clarify
- "Where do we get the seed URLs from?" → Pre-curated list, sitemap submissions, or existing index
- "Can we use DFS instead of BFS?" → BFS preferred for breadth coverage; DFS risks getting stuck in deep sites
- "How does the crawler handle URLs with variable priorities?" → Priority queue with configurable scoring

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Scalability** | Distributed, multi-threaded, billions of pages | Must handle massive web scale |
| **Consistency** | Ensure data consistency across all crawling workers | Checksum-based dedup |
| **Performance** | Self-throttling per domain (by time or count) | Avoid overloading hosts |
| **Extensibility** | Support new protocols (FTP) and MIME types (images, video) | Modular design |
| **Scheduling** | Customized recrawl frequencies per URL priority | Keep data fresh |
| **Availability** | Tolerate worker failures (up to 10%) | No single point of failure |
| **Politeness** | Max 1 req/sec per domain, respect robots.txt | Legal & ethical requirement |

### Resource Estimation (Grokking/Educative)

#### Assumptions
```
Total web pages:           5 billion
Text content per page:     2,070 KB (≈ 2 MB)
Metadata per page:         500 bytes
Average HTTP traversal:    60 ms
```

#### Storage Estimation
```
Storage per page = 2,070 KB + 500 bytes ≈ 2,070.5 KB
Total storage    = 5 billion × 2,070.5 KB = 10.35 PB per full crawl
```

#### Traversal Time
```
Total traversal time = 5 billion × 60 ms = 0.3 billion seconds ≈ 9.5 years (single server)
Servers needed       = 9.5 years × 365 days ≈ 3,468 servers (to complete in 1 day)
```

#### Bandwidth Estimation
```
Total bandwidth     = 10.35 PB / 86,400 seconds = 960 Gb/sec
Per-server bandwidth = 960 Gb/sec ÷ 3,468 servers ≈ 277 Mb/sec per server
```

### Capacity Estimation (Decentralized Variant — Meta/Bloomberg)

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
    private final int priority;           // Lower = crawl sooner
    private final int depth;              // Hops from seed URL
    private final Instant discoveredAt;
    private final String sourceUrl;       // Where this link was found
    private final Duration recrawlInterval; // How often to recrawl (scheduling)
    private final String urlChecksum;     // For URL dedup (MD5/SHA-256)

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
    private final String contentType;      // MIME type (text/html, image/png, etc.)
    private final byte[] body;             // Raw content
    private final Map<String, String> headers;
    private final List<String> extractedLinks;
    private final List<String> extractedDocuments; // Non-HTML content (images, PDFs)
    private final Instant crawledAt;
    private final String urlChecksum;      // Hash of URL (for URL dedup)
    private final String contentChecksum;  // Hash of body (for content dedup / SimHash)
}
```

### Entity 3: UrlFrontier (Scheduler's Priority Queue + Database)
```java
public class UrlFrontier {
    private final PriorityQueue<CrawlUrl> priorityQueue;  // URLs ready for crawling
    private final RelationalDB urlDatabase;                 // All URLs with priority + recrawl frequency
    // Populated by:
    //   1. User's added URLs (seed + runtime)
    //   2. Crawler's extracted URLs (discovered during crawling)
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

### Entity 5: WorkerState (For decentralized variant)
```java
public class WorkerState {
    private final int workerId;
    private final PriorityQueue<CrawlUrl> frontier;           // URLs to crawl
    private final BloomFilter<String> seenUrls;                // URL dedup (in-memory)
    private final RocksDB seenUrlsOnDisk;                      // Overflow dedup (on-disk)
    private final Map<String, DomainPoliteness> domainState;   // Per-domain rate limiting
    private final Map<String, RobotsTxtRules> robotsCache;     // Cached robots.txt
    private final List<CrawlUrl> outboundBuffer;               // URLs to forward to other workers
    private final long lastCheckpointAt;
}
```

---

## 3️⃣ API Design

### Variant A: Centralized API (Service Host)

#### 1. Submit Seed URLs
```
POST /api/v1/crawler/seed
Request:  { "urls": ["https://example.com", ...], "priority": 5, "recrawlInterval": "7d" }
Response: { "accepted": 1000, "duplicates": 50, "status": "queued" }
```

#### 2. Get Crawl Status
```
GET /api/v1/crawler/status?domain=example.com
Response: { 
    "totalUrlsCrawled": 15000, 
    "pendingUrls": 3200,
    "errorRate": 0.02,
    "lastCrawledAt": "2026-01-15T10:30:00Z" 
}
```

#### 3. Trigger On-Demand Crawl
```
POST /api/v1/crawler/crawl
Request:  { "url": "https://example.com/new-page", "priority": 1 }
Response: { "jobId": "crawl-abc123", "status": "scheduled" }
```

### Variant B: Peer-to-Peer Protocol (Decentralized)

#### 1. Forward Discovered URLs (Batched, Worker-to-Worker)
```protobuf
message ForwardUrlBatch {
    int32 source_worker_id = 1;
    int64 batch_id = 2;               // For idempotent processing
    repeated CrawlUrlProto urls = 3;   // Batch of 100-10,000 URLs
}

message ForwardUrlResponse {
    int64 batch_id = 1;
    int32 accepted = 2;               // New URLs
    int32 rejected = 3;               // Already seen (duplicates)
}
```

#### 2. Heartbeat / Peer Discovery (Gossip Protocol)
```protobuf
message Heartbeat {
    int32 worker_id = 1;
    string host = 2;
    int32 port = 3;
    int64 urls_crawled = 4;
    int64 frontier_size = 5;
    int64 timestamp = 6;
    repeated PeerInfo known_peers = 7;  // Gossip protocol
}
```

#### 3. Anti-Entropy Sync (Periodic Consistency Check)
```protobuf
message AntiEntropyRequest {
    int32 worker_id = 1;
    bytes bloom_filter_digest = 2;
    int64 since_timestamp = 3;
}

message AntiEntropyResponse {
    repeated string missing_urls = 1;
}
```

---

## 4️⃣ Data Flow

### Flow 1: Centralized Crawl Workflow (Grokking/Educative)

```
1. Assignment to a Worker:
   The service host loads a URL from the URL frontier's priority queue
   and assigns it to an available worker.
   ↓
2. DNS Resolution:
   The worker sends the URL for DNS resolution.
   DNS resolver checks cache → returns cached IP, or resolves and caches it.
   ↓
3. Communication Initiation by HTML Fetcher:
   The worker forwards the URL + IP to the HTML fetcher,
   which initiates HTTP communication with the host server.
   ↓
4. Content Extraction:
   Worker extracts URLs and HTML documents from the web page.
   Places the document in a cache (Document Input Stream / DIS)
   for other components to process.
   ↓
5. Dedup Testing (Two-Phase):
   a. URL Checksum: Compare checksum of extracted URL against URL checksum data store.
      → If match: discard URL (already seen)
      → If new: add checksum to store, continue
   b. Document Checksum: Compare checksum of fetched content against document checksum store.
      → If match: discard content (duplicate page)
      → If new: add checksum to store, continue
   ↓
6. URL Extraction & Storing:
   Extractor places newly-discovered URLs into the scheduler's database
   (with priority and recrawl frequency).
   Stores unique content in the blob store.
   ↓
7. Recrawling:
   Once a cycle completes, the crawler repeats from step 1.
   URLs in the scheduler's DB have priority and periodicity.
   Enqueuing into the URL frontier depends on these two factors.
```

### Flow 2: Decentralized Crawl Workflow (Worker Main Loop)
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
   Timeout: 30 seconds, Max page size: 10 MB
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
     b. If owner == me → check local seenUrls, add to frontier if new
     c. If owner != me → add to outboundBuffer for batched forwarding
   ↓
7. Mark URL as crawled in seenUrls set
   ↓
8. Update domain politeness timer
```

### Flow 3: Batched URL Forwarding (Decentralized)
```
1. Worker accumulates discovered URLs for other workers in outbound buffers
   outboundBuffer[worker_42] = [url1, url2, url3, ...]
   ↓
2. Every 30-60 seconds OR when buffer reaches 10,000 URLs:
   For each target worker:
     a. Create ForwardUrlBatch protobuf
     b. Compress with gzip (80-90% compression ratio)
     c. Send via gRPC to target worker
   ↓
3. Target worker receives batch:
   a. Check batch_id for idempotency
   b. For each URL: check Bloom Filter → if new, add to frontier
   c. Return ForwardUrlResponse with counts
   ↓
4. On failure: persist to disk, retry with exponential backoff
```

### Flow 4: Worker Startup / Recovery (Decentralized)
```
1. Worker starts (or restarts after crash)
   ↓
2. Load checkpoint from local disk:
   - frontier.snapshot, seen_urls.bloom, seen_urls.rocks, outbound_buffers/
   ↓
3. If no checkpoint (first boot):
   - Initialize empty frontier
   - Load seed URLs that hash to this worker
   ↓
4. Start main crawl loop + background threads:
   - Outbound URL forwarder (every 30s)
   - Checkpoint writer (every 5 min)
   - Peer heartbeat / gossip (every 10s)
   - Anti-entropy sync (every 10 min)
   - Robots.txt refresher (every 24h per domain)
```

---

## 5️⃣ High-Level Design

### Variant A: Centralized Architecture (Grokking/Educative)

This is the **standard web crawler design** commonly discussed in system design interviews. It uses centralized components for coordination.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                                                                 │
│  ┌────────┐    ┌───────────────────┐    ┌──────────────┐    ┌───────────────┐  │
│  │  User   │    │    Scheduler      │    │ Service Host │    │  HTML Fetcher │  │
│  │(seed    │───►│  ┌─────────────┐  │───►│  (Brain of   │───►│  (Downloads   │  │
│  │ URLs)   │    │  │Priority     │  │    │   Crawler)   │    │   content     │  │
│  └────────┘    │  │Queue (URL   │  │    │              │    │   via HTTP)   │  │
│                 │  │Frontier)    │  │    │  • Manages   │    └──────┬────────┘  │
│  Extracted ───►│  ├─────────────┤  │    │    workers   │           │            │
│  URLs          │  │Relational   │  │    │  • DNS res.  │           │            │
│  (from         │  │Database     │  │    │  • Triggers  │           ▼            │
│   Extractor)   │  │(all URLs +  │  │    │    fetcher   │    ┌──────────────┐   │
│                 │  │priority +   │  │    └──────┬───────┘    │  Extractor   │   │
│                 │  │recrawl freq)│  │           │            │  (Parses     │   │
│                 │  └─────────────┘  │           │            │   URLs &     │   │
│                 └───────────────────┘    ┌──────▼───────┐    │   documents) │   │
│                                          │ DNS Resolver  │    └──────┬───────┘   │
│                                          │ (with cache)  │           │            │
│                                          └───────────────┘           │            │
│                                                                      ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────────┐  │
│  │                      Duplicate Eliminator                                   │  │
│  │  ┌──────────────────────────┐    ┌──────────────────────────┐              │  │
│  │  │ URL Checksum Store       │    │ Document Checksum Store   │              │  │
│  │  │ (URL dedup: is this URL  │    │ (Content dedup: is this   │              │  │
│  │  │  already seen?)          │    │  content already stored?) │              │  │
│  │  └──────────────────────────┘    └──────────────────────────┘              │  │
│  └─────────────────────────────────────┬───────────────────────────────────────┘  │
│                                         │                                         │
│                                         ▼                                         │
│                                  ┌──────────────┐                                │
│                                  │  Blob Store   │                                │
│                                  │  (Stores      │                                │
│                                  │   crawled     │                                │
│                                  │   content)    │                                │
│                                  └──────────────┘                                │
└─────────────────────────────────────────────────────────────────────────────────┘

Document Input Stream (DIS): Cache layer between Extractor and Duplicate Eliminator
```

#### Centralized Component Responsibilities

| Component | Purpose | Details |
|-----------|---------|--------|
| **Scheduler** | URL frontier + scheduling | Priority queue + relational DB. Stores all URLs with priority and recrawl frequency. Fed by user seed URLs and crawler-extracted URLs |
| **Service Host** | "Brain" of the crawler | Manages multi-worker architecture. Workers request next URL from frontier. Resolves IPs via DNS. Triggers HTML fetcher |
| **DNS Resolver** | IP resolution with caching | Resolves domain names. Caches frequently used IPs to reduce latency |
| **HTML Fetcher** | Downloads content from web | Communicates with host servers via HTTP. Extensible to other protocols (FTP) |
| **Extractor** | Parses content & extracts URLs | Extracts URLs and documents from web pages. Extensible to multiple MIME types |
| **Duplicate Eliminator** | Two-phase dedup | Phase 1: URL checksum (have we seen this URL?). Phase 2: Document checksum (have we seen this content?) |
| **Blob Store** | Stores crawled content | Distributed storage for massive unstructured data. Supports up to 500 req/sec per blob |
| **Cache / DIS** | Document Input Stream | Temporary cache between extraction and dedup/storage |

#### Why Two-Phase Deduplication?
```
Phase 1 — URL Checksum:
  ✓ Catches exact duplicate URLs efficiently
  ✗ Misses URL redirections (different URL → same content)
  
Phase 2 — Document Checksum:
  ✓ Catches content duplicates regardless of URL
  ✗ A single byte change produces different checksum
  
Together:
  ✓ URL redirections caught by document checksum
  ✓ Modified documents produce different checksums (intentional — they ARE different)
  ✓ Two layers of defense against wasted storage
```

---

### Variant B: Decentralized P2P Architecture (Meta/Bloomberg)

> **Constraint**: 10,000 machines, NO centralized components (no master node, no shared database, no message queue, no cloud storage).

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

#### Decentralized Component Responsibilities (Per Worker)

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

### Architecture Comparison

| Aspect | Centralized (Grokking) | Decentralized (Meta) |
|--------|----------------------|---------------------|
| **Coordination** | Service Host manages workers | `hash(url) % N` — zero coordination |
| **URL Dedup** | Central URL checksum store | Local Bloom Filter + RocksDB per worker |
| **Content Dedup** | Central document checksum store | Local SimHash index per worker |
| **Storage** | Shared Blob Store | Local SSD per worker |
| **DNS** | Central DNS resolver with cache | Per-worker DNS resolution |
| **Scheduling** | Central Scheduler with priority queue + RDB | Per-worker priority frontier |
| **Failure Handling** | Service host reassigns work | Gossip protocol + consistent hashing rebalance |
| **Scalability** | Add workers + scale central components | Add workers, consistent hashing redistributes |
| **Best for** | Standard interview, search engine focus | "No central infra" constraint, P2P emphasis |

---

## 6️⃣ Deep Dives

### Deep Dive 1: URL Frontier & Scheduling (Centralized)

**The Problem**: How do we decide which URLs to crawl next, and how often to recrawl?

The URL frontier is the key building block of the scheduler. It's composed of:
- **Priority Queue (URL Frontier)**: Hosts URLs ready for crawling, ordered by priority and update frequency
- **Relational Database**: Stores all URLs with priority and recrawl frequency metadata

**Scheduling Strategies**:

```java
public class UrlFrontierScheduler {
    // Method 1: Single priority queue with recrawl frequency
    // - Assign default or specific recrawl frequency to each URL
    // - Higher priority URLs get higher recrawl frequency
    // - URLs are re-enqueued based on their recrawl interval
    
    // Method 2: Multiple priority queues (Educative recommendation)
    // - Separate queues for different priority levels
    // - Process high-priority queues first
    // - Then move to lower-priority queues
    
    private final List<Queue<CrawlUrl>> priorityQueues; // Multiple priority buckets
    private final Map<String, Instant> lastCrawledAt;    // Track recrawl timing
    
    public CrawlUrl getNextUrl() {
        // Check high-priority queues first
        for (Queue<CrawlUrl> queue : priorityQueues) {
            CrawlUrl url = queue.peek();
            if (url != null && shouldRecrawl(url)) {
                return queue.poll();
            }
        }
        return null;
    }
    
    private boolean shouldRecrawl(CrawlUrl url) {
        Instant last = lastCrawledAt.get(url.getUrl());
        if (last == null) return true; // Never crawled
        return Instant.now().isAfter(last.plus(url.getRecrawlInterval()));
    }
}
```

**Priority Queue Size Estimation**:
```
If we maintain 10M URLs in the frontier at any time:
  URL (100 bytes) + metadata (100 bytes) = 200 bytes per entry
  Total: 10M × 200 bytes = 2 GB → fits in memory

Centralized: Single shared priority queue (requires distributed queue like Kafka/Redis)
Distributed: Per-worker priority queues (trivial, each worker manages its own)
```

---

### Deep Dive 2: Duplicate Eliminator — Two-Phase Checksum System

**The Problem**: How do we avoid crawling the same URL and storing the same content twice?

**Centralized Two-Phase Approach (Educative)**:

```java
public class DuplicateEliminator {
    private final ChecksumStore urlChecksumStore;      // Phase 1: URL dedup
    private final ChecksumStore documentChecksumStore;  // Phase 2: Content dedup
    
    /**
     * Phase 1: URL Deduplication
     * Check if we've already seen this exact URL.
     */
    public boolean isUrlDuplicate(String url) {
        String urlChecksum = computeMD5(normalizeUrl(url));
        return urlChecksumStore.exists(urlChecksum);
    }
    
    /**
     * Phase 2: Document Deduplication  
     * Check if we've already stored this exact content.
     * Catches: URL redirections pointing to same content.
     */
    public boolean isContentDuplicate(byte[] content) {
        String docChecksum = computeMD5(content);
        return documentChecksumStore.exists(docChecksum);
    }
    
    /**
     * Full dedup pipeline:
     * 1. Check URL checksum → skip if duplicate URL
     * 2. Fetch content
     * 3. Check document checksum → skip storage if duplicate content
     */
    public DedupResult process(String url, byte[] content) {
        String urlChecksum = computeMD5(normalizeUrl(url));
        
        // Phase 1: URL already seen?
        if (urlChecksumStore.exists(urlChecksum)) {
            return DedupResult.URL_DUPLICATE;
        }
        urlChecksumStore.store(urlChecksum);
        
        // Phase 2: Content already stored?
        String docChecksum = computeMD5(content);
        if (documentChecksumStore.exists(docChecksum)) {
            return DedupResult.CONTENT_DUPLICATE;
        }
        documentChecksumStore.store(docChecksum);
        
        return DedupResult.UNIQUE;
    }
}
```

**Robustness of Two-Phase Dedup**:
```
Scenario 1 — URL Redirection:
  URL A → redirects to → URL B (same content)
  • URL checksum: Different (A ≠ B) → both pass Phase 1
  • Document checksum: Same content → Phase 2 catches duplicate ✓

Scenario 2 — Modified Document:
  Page at same URL changes by 1 byte
  • URL checksum: Same → Phase 1 would skip it
  • But recrawl schedule forces re-fetch → new doc checksum detected
  • This is intentional: the page DID change ✓
```

**Decentralized Approach — Bloom Filter + RocksDB (Two-Tier)**:

```java
public class UrlDeduplicator {
    // Tier 1: In-memory Bloom Filter (fast, probabilistic)
    private BloomFilter<String> bloomFilter;
    
    // Tier 2: On-disk RocksDB (exact, for Bloom false positives)
    private final RocksDB rocksDb;
    
    private static final int EXPECTED_URLS = 50_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.001;

    public synchronized boolean markIfNew(String url) {
        String normalized = UrlNormalizer.normalize(url);
        
        // Tier 1: Bloom Filter (very fast, < 1µs)
        if (!bloomFilter.mightContain(normalized)) {
            bloomFilter.put(normalized);
            rocksDb.put(normalized.getBytes(), EMPTY_BYTES);
            return true; // Definitely NEW
        }
        
        // Bloom says "maybe seen" — could be false positive
        // Tier 2: Exact check in RocksDB (~50µs)
        byte[] existing = rocksDb.get(normalized.getBytes());
        if (existing != null) {
            return false; // Already seen (confirmed)
        }
        
        // Bloom false positive — URL is actually new
        bloomFilter.put(normalized);
        rocksDb.put(normalized.getBytes(), EMPTY_BYTES);
        return true;
    }
}
```

**Why not just Bloom Filter alone?**
```
Bloom Filter alone:
  ✓ Fast (< 1µs), small memory (72 MB for 50M URLs at 0.01% FPR)
  ✗ False positives → we'd SKIP legitimate URLs (lost coverage)

Bloom Filter + RocksDB:
  ✓ Bloom catches 99.9% as "definitely new" → fast path
  ✓ RocksDB resolves the 0.1% "maybe seen" → no false skips
  ✓ RocksDB persists to disk → survives restarts
```

---

### Deep Dive 3: Crawler Traps — Identification & Prevention

**The Problem**: A **crawler trap** is a URL structure that causes indefinite crawling, exhausting resources.

#### Classification of Crawler Traps

| Trap Type | Example | Description |
|-----------|---------|-------------|
| **Query Parameters** | `site.com?query=abc` | Useless variations of a page |
| **Internal Links** | Infinite redirections within a domain | Cyclic link loops |
| **Calendar Pages** | `site.com/cal?date=2025-01-01, 01-02, ...` | Infinite date combinations |
| **Dynamic Content** | Session IDs, counters in URLs | Auto-generated infinite URL space |
| **Cyclic Redirection** | A → B → C → A | Redirect loops |

Traps can be **accidental** (poor website design) or **intentional** (malicious). They waste throughput and negatively impact SEO.

#### Identification Strategy

```java
public class SpiderTrapDetector {
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_PATH_DEPTH = 15;
    private static final int MAX_QUERY_PARAMS = 10;
    private static final int MAX_URLS_PER_DOMAIN_PER_HOUR = 10_000;

    private final Map<String, AtomicInteger> domainUrlCounts = new ConcurrentHashMap<>();

    public boolean isSuspicious(CrawlUrl url) {
        String urlStr = url.getUrl();
        
        // 1. URL too long (likely auto-generated)
        if (urlStr.length() > MAX_URL_LENGTH) return true;
        
        // 2. Too many path segments (deep nesting = likely trap)
        long pathDepth = urlStr.chars().filter(c -> c == '/').count();
        if (pathDepth > MAX_PATH_DEPTH) return true;
        
        // 3. Too many query parameters
        if (urlStr.contains("?")) {
            String query = urlStr.substring(urlStr.indexOf('?') + 1);
            long paramCount = query.chars().filter(c -> c == '&').count() + 1;
            if (paramCount > MAX_QUERY_PARAMS) return true;
        }
        
        // 4. Repeating path patterns (e.g., /a/b/a/b/a/b)
        if (hasRepeatingPattern(urlStr)) return true;
        
        // 5. Too many URLs from same domain (calendar trap)
        String domain = url.getDomain();
        AtomicInteger count = domainUrlCounts.computeIfAbsent(domain, d -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_URLS_PER_DOMAIN_PER_HOUR) return true;
        
        // 6. Crawl depth too high
        if (url.getDepth() > 20) return true;
        
        return false;
    }
}
```

#### Solution (Educative/Grokking)

1. **Application Logic**: Limit crawling on a domain based on page count or depth. Identify and mark "no-go" areas.
2. **Robots Exclusion Protocol**: Fetch and adhere to `robots.txt`. Specifies allowed pages and revisit frequency.
   > ⚠️ `robots.txt` does NOT prevent malicious traps. Other mechanisms must handle those.
3. **Politeness / Self-Throttling**: Adjust crawl speed based on the domain's **Time to First Byte (TTFB)**. Slower servers receive slower crawls to avoid timeouts and overload.

```
┌─────────────────────────────────────────────────┐
│           Crawler Trap Detection Flow            │
│                                                   │
│  URL → [URL Scheme Filter] → [Domain Page Count] │
│         │                      │                  │
│  If unfiltered:          If above threshold:     │
│  discard URL             stop crawling domain,   │
│                          mark as no-go           │
│                          │                        │
│                    If under threshold:            │
│                    continue crawling             │
└─────────────────────────────────────────────────┘
```

---

### Deep Dive 4: Politeness — Respecting Robots.txt & Rate Limits

**The Problem**: We must not overload any single website. Legal and ethical requirement.

```java
public class PolitenessEnforcer {
    private final ConcurrentHashMap<String, DomainState> domainStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RobotsTxtRules> robotsCache = new ConcurrentHashMap<>();

    public boolean canCrawl(CrawlUrl url) {
        String domain = url.getDomain();
        DomainState state = domainStates.computeIfAbsent(domain, 
            d -> new DomainState(d, getDefaultDelay()));
        
        // Check robots.txt
        RobotsTxtRules rules = getRobotsRules(domain);
        if (rules != null && rules.isDisallowed(url.getUrl())) {
            return false;
        }
        
        // Check rate limit
        return state.canRequestNow();
    }

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
            return RobotsTxtRules.ALLOW_ALL;
        });
    }
}
```

**Domain-Aware Frontier** — Per-domain queues with round-robin selector:

```java
public class PolitenessFrontier {
    private final Map<String, Queue<CrawlUrl>> domainQueues = new LinkedHashMap<>();
    private final PolitenessEnforcer politeness;

    public CrawlUrl getNextCrawlable() {
        for (int attempts = 0; attempts < domainQueues.size(); attempts++) {
            String domain = getNextDomain(); // round-robin
            Queue<CrawlUrl> queue = domainQueues.get(domain);
            if (queue != null && !queue.isEmpty()) {
                CrawlUrl url = queue.peek();
                if (politeness.canCrawl(url)) {
                    return queue.poll();
                }
            }
        }
        return null; // All domains rate-limited, wait
    }
}
```

---

### Deep Dive 5: URL Partitioning — Consistent Hashing (Decentralized)

**The Problem**: With 10,000 workers, how does every worker know which URLs it owns without a coordinator?

```java
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
        if (entry == null) entry = ring.firstEntry(); // Wrap around
        return entry.getValue();
    }
}
```

**Why Consistent Hashing over Simple Modulo?**
```
Simple hash(url) % N:
  ✗ If N changes (worker added/removed), EVERY URL changes owner
  ✗ Massive data migration needed

Consistent Hashing with Virtual Nodes (100 per worker):
  ✓ Adding/removing a worker only affects 1/N of URLs
  ✓ Virtual nodes ensure even distribution
  ✓ Graceful rebalancing when cluster size changes
```

**Work Distribution Strategies** (Educative/Grokking):
- **Domain-level assignment**: Hash the hostname → assign entire domain to a worker. Prevents redundant crawling and supports reverse URL indexing.
- **Range division**: Assign ranges of URLs to workers.
- **Per-URL crawling**: Workers take individual URLs (requires coordination to avoid collisions).

---

### Deep Dive 6: Extensibility & Modularity

**The Problem**: The initial design focuses on HTTP, but we need to support other protocols and content types.

**HTML Fetcher Extensibility** (Educative/Grokking):
```
Add modules for other protocols:
  • HTTP module (default)
  • FTP module
  • Future: HTTPS, WebSocket, etc.
  
The crawler invokes the correct module based on the URL scheme.
Subsequent steps (extraction, dedup, storage) remain the same.
```

**Extractor Extensibility**:
```
Add modules to process non-text media from the Document Input Stream (DIS):
  • HTML/Text module (default)
  • Image module (JPEG, PNG)
  • Video module (MP4)
  • PDF module
  • Future: any MIME type

Each MIME type gets its own processing module.
Results stored in the blob store alongside text content.
```

```
┌───────────────────────────────────────────────────────────────┐
│                    Extensible Architecture                      │
│                                                                 │
│  HTML Fetcher:                  Extractor:                     │
│  ┌──────────────┐              ┌──────────────┐               │
│  │ HTTP Module   │              │ HTML Module   │               │
│  │ FTP Module    │  ──DIS──►   │ Image Module  │  ──► Blob    │
│  │ [New Protocol]│              │ Video Module  │      Store   │
│  └──────────────┘              │ [New MIME]    │               │
│                                 └──────────────┘               │
│                                                                 │
│  New protocol? Add a module to HTML Fetcher.                   │
│  New content type? Add a module to Extractor.                  │
│  No changes to the rest of the pipeline.                       │
└───────────────────────────────────────────────────────────────┘
```

---

### Deep Dive 7: Batched URL Forwarding — Minimizing Inter-Node Communication (Decentralized)

**The Problem**: With 10K workers, each discovering ~1M URLs/day for other workers, naive per-URL forwarding creates 10 billion messages/day.

```java
public class OutboundUrlForwarder {
    private final Map<Integer, List<CrawlUrl>> buffers = new ConcurrentHashMap<>();
    private static final int BATCH_SIZE_THRESHOLD = 5_000;
    private static final int FLUSH_INTERVAL_MS = 30_000;

    public void enqueue(CrawlUrl url) {
        int targetWorker = url.getOwnerWorker();
        buffers.computeIfAbsent(targetWorker, k -> new ArrayList<>()).add(url);
        if (buffers.get(targetWorker).size() >= BATCH_SIZE_THRESHOLD) {
            flushBuffer(targetWorker);
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void flushAll() {
        for (int targetWorker : buffers.keySet()) {
            flushBuffer(targetWorker);
        }
    }

    private void flushBuffer(int targetWorker) {
        List<CrawlUrl> urls = buffers.remove(targetWorker);
        if (urls == null || urls.isEmpty()) return;

        // Deduplicate within batch
        Set<String> seen = new HashSet<>();
        List<CrawlUrl> deduped = urls.stream()
            .filter(u -> seen.add(u.getUrl())).collect(Collectors.toList());

        // Compress (URLs compress ~80-90% with gzip)
        byte[] compressed = GzipUtil.compress(createProtobuf(deduped).toByteArray());
        
        try {
            grpcClients.get(targetWorker).forwardUrls(compressed);
        } catch (Exception e) {
            diskBuffer.persist(targetWorker, deduped); // Retry later
        }
    }
}
```

**Communication Budget**:
```
Per worker per day:
  Discovered links:  1M URLs → 95% for other workers = 950K URLs
  Uncompressed: 950K × 100 bytes = 95 MB/day
  After gzip (~85% compression): ~14 MB/day
  Per 30-second batch: ~5 KB per batch → very manageable
```

---

### Deep Dive 8: URL Normalization

**The Problem**: Many URL variations point to the same page. Without normalization, we crawl duplicates.

```java
public class UrlNormalizer {
    public static String normalize(String rawUrl) {
        try {
            URI uri = new URI(rawUrl).normalize();
            String scheme = uri.getScheme().toLowerCase();
            String host = uri.getHost().toLowerCase();
            int port = uri.getPort();
            String path = uri.getPath() != null ? uri.getPath() : "/";
            String query = uri.getQuery();
            
            // 1. Remove default ports (80 for HTTP, 443 for HTTPS)
            if ((scheme.equals("http") && port == 80) || 
                (scheme.equals("https") && port == 443)) port = -1;
            
            // 2. Fragments removed (already by URI)
            // 3. Remove trailing slash on root
            if (path.equals("/") && query == null) path = "";
            
            // 4. Remove "www." prefix
            if (host.startsWith("www.")) host = host.substring(4);
            
            // 5. Sort query parameters
            if (query != null) {
                String[] params = query.split("&");
                Arrays.sort(params);
                query = String.join("&", params);
            }
            
            // 6. Decode unreserved percent-encoded chars
            path = decodeUnreservedChars(path);
            
            // 7. IDN → Punycode
            host = IDN.toASCII(host);
            
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port > 0) sb.append(":").append(port);
            sb.append(path.isEmpty() ? "/" : path);
            if (query != null) sb.append("?").append(query);
            return sb.toString();
        } catch (URISyntaxException e) {
            return rawUrl.toLowerCase();
        }
    }
}

// Examples:
// "HTTP://WWW.Example.COM:80/path"  → "http://example.com/path"
// "http://example.com/a/../b"       → "http://example.com/b"
// "http://example.com/page?b=2&a=1" → "http://example.com/page?a=1&b=2"
// "http://example.com/%7Euser"      → "http://example.com/~user"
// "http://example.com/page#section" → "http://example.com/page"
```

---

### Deep Dive 9: Checkpoint & Crash Recovery (Decentralized)

**The Problem**: Workers can crash at any time. Must resume without re-crawling everything.

```java
public class CheckpointService {
    private static final int CHECKPOINT_INTERVAL_MS = 300_000; // 5 minutes

    @Scheduled(fixedRate = 300_000)
    public void checkpoint() {
        // 1. Serialize frontier to disk (atomic write with .tmp → rename)
        writeFrontierToDisk();
        
        // 2. Flush RocksDB WAL (seen URLs persist automatically)
        state.getSeenUrlsOnDisk().flush(new FlushOptions().setWaitForFlush(true));
        
        // 3. Save outbound buffers
        saveOutboundBuffers();
        
        // 4. Write checkpoint metadata
        writeMetadata(Instant.now(), state.getFrontier().size(), state.getUrlsCrawled());
    }

    public WorkerState restore() {
        if (!checkpointExists()) {
            return WorkerState.empty(workerId); // Fresh start
        }
        // Restore frontier, RocksDB auto-restores, restore outbound buffers
        return new WorkerState(workerId, restoreFrontier(), openRocksDB(), restoreOutbound());
    }
}
```

**Trade-off**: Checkpoint every 5 min → on crash, lose at most 5 min of work. Re-crawling a few hundred URLs is idempotent and cheap. Checkpointing every URL would kill disk throughput.

---

### Deep Dive 10: Gossip Protocol for Peer Discovery & Failure Detection (Decentralized)

```java
public class GossipService {
    private final ConcurrentHashMap<Integer, PeerState> knownPeers = new ConcurrentHashMap<>();
    private static final int GOSSIP_FANOUT = 3;
    private static final int PEER_TIMEOUT_MS = 30_000; // 3 missed heartbeats

    @Scheduled(fixedRate = 10_000)
    public void gossip() {
        Heartbeat hb = buildHeartbeat();
        List<PeerState> targets = pickRandomPeers(GOSSIP_FANOUT);
        for (PeerState peer : targets) {
            udpClient.send(peer.getHost(), peer.getPort(), hb.toByteArray());
        }
    }

    public void onHeartbeatReceived(Heartbeat hb) {
        knownPeers.put(hb.getWorkerId(), new PeerState(hb));
        // Learn about peers transitively (gossip propagation)
        for (PeerInfo info : hb.getKnownPeersList()) {
            knownPeers.putIfAbsent(info.getWorkerId(), new PeerState(info));
        }
    }

    @Scheduled(fixedRate = 10_000)
    public void detectDeadPeers() {
        long now = System.currentTimeMillis();
        for (var entry : knownPeers.entrySet()) {
            if (now - entry.getValue().getLastSeen() > PEER_TIMEOUT_MS) {
                log.warn("Worker {} appears dead", entry.getKey());
            }
        }
    }
}
```

**Convergence Time**:
```
10,000 workers, fanout=3, 10s interval:
  log₃(10000) ≈ 8.4 rounds → ~85 seconds for full convergence
  Dead worker detected within 30 seconds (3 missed heartbeats)
```

---

### Deep Dive 11: Content Deduplication — SimHash for Near-Duplicates

**The Problem**: URL dedup catches exact URL matches. Document checksum catches exact content matches. But what about near-duplicate content (mirrors, print versions, syndication)?

```java
public class ContentDeduplicator {
    private static final int SIMHASH_BITS = 64;
    private static final int HAMMING_THRESHOLD = 3;
    
    private final Map<Long, String> simHashIndex = new ConcurrentHashMap<>();

    public long computeSimHash(String htmlContent) {
        String text = HtmlParser.extractText(htmlContent);
        List<String> shingles = generateShingles(text, 3); // 3-word shingles
        
        int[] vector = new int[SIMHASH_BITS];
        for (String shingle : shingles) {
            long hash = Hashing.murmur3_128()
                .hashString(shingle, StandardCharsets.UTF_8).asLong();
            for (int i = 0; i < SIMHASH_BITS; i++) {
                vector[i] += ((hash >> i) & 1) == 1 ? 1 : -1;
            }
        }
        
        long simHash = 0;
        for (int i = 0; i < SIMHASH_BITS; i++) {
            if (vector[i] > 0) simHash |= (1L << i);
        }
        return simHash;
    }

    public String findDuplicate(String url, String htmlContent) {
        long simHash = computeSimHash(htmlContent);
        for (Map.Entry<Long, String> entry : simHashIndex.entrySet()) {
            int hammingDistance = Long.bitCount(simHash ^ entry.getKey());
            if (hammingDistance <= HAMMING_THRESHOLD) {
                return entry.getValue(); // Near-duplicate found
            }
        }
        simHashIndex.put(simHash, url);
        return null;
    }
}
```

**SimHash Properties**:
```
Two identical pages → Hamming distance = 0
Two nearly identical pages → Hamming distance ≤ 3
Two completely different pages → Hamming distance ≈ 32
Memory: 8 bytes per page × 100K pages/worker = 800 KB (trivial)
```

---

### Deep Dive 12: Priority-Based Crawling & Crawl Budget

```java
public class UrlPrioritizer {
    public int computePriority(CrawlUrl url) {
        int score = 500; // Default: medium
        
        score += url.getDepth() * 30;       // Deeper = lower priority
        if (isHighAuthorityDomain(url.getDomain())) score -= 200; // Well-known domains
        
        String path = new URI(url.getUrl()).getPath();
        if (path == null || path.equals("/")) score -= 100; // Homepage bonus
        if (path != null && (path.contains("/tag/") || path.contains("/page/")))
            score += 100; // Pagination/tag = lower priority
        
        if (url.getUrl().endsWith(".pdf") || url.getUrl().endsWith(".doc"))
            score += 200; // Documents lower priority than HTML
            
        return Math.max(1, Math.min(1000, score));
    }
}

public class CrawlBudgetManager {
    private final Map<String, DomainBudget> budgets = new ConcurrentHashMap<>();
    
    // High-authority domains: 50K pages/day
    // Default domains: 1K pages/day
    // Prevents any single domain from consuming all resources
    
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyBudgets() {
        budgets.values().forEach(b -> b.crawledToday.set(0));
    }
}
```

---

### Deep Dive 13: The Main Crawl Loop — Putting It All Together (Decentralized)

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

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            CrawlUrl url = frontier.getNextCrawlable();
            if (url == null) { Thread.sleep(100); continue; }
            fetchPool.submit(() -> crawlUrl(url));
        }
    }

    private void crawlUrl(CrawlUrl url) {
        try {
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url.getUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "MyCrawler/1.0").build(),
                HttpResponse.BodyHandlers.ofString());

            politeness.recordRequest(url.getDomain());
            if (response.statusCode() != 200) return;

            List<String> links = HtmlParser.extractLinks(response.body(), url.getUrl());
            pageStore.store(url.getUrl(), response.body(), response.headers());

            for (String link : links) {
                String normalized = UrlNormalizer.normalize(link);
                int owner = partitioner.getOwner(normalized);
                if (owner == workerId) {
                    if (dedup.markIfNew(normalized))
                        frontier.add(new CrawlUrl(normalized, url.getDepth() + 1));
                } else {
                    forwarder.enqueue(new CrawlUrl(normalized, owner, url.getDepth() + 1));
                }
            }
        } catch (Exception e) {
            metrics.counter("crawl.error").increment();
        }
    }
}
```

---

### Deep Dive 14: Worker Failure & Rebalancing (Decentralized)

**When a worker permanently dies**, its URLs are orphaned. Solution: consistent hashing ring rebalance.

```java
public class ClusterRebalancer {
    private static final long PERMANENT_DEATH_THRESHOLD_MS = 600_000; // 10 minutes

    @Scheduled(fixedRate = 60_000)
    public void checkForPermanentFailures() {
        Set<Integer> deadWorkers = findPermanentlyDeadWorkers();
        if (!deadWorkers.isEmpty()) {
            ConsistentHashRing newRing = ring.withoutWorkers(deadWorkers);
            for (int deadWorker : deadWorkers) {
                List<Long> reassignedSlots = ring.getSlotsForWorker(deadWorker);
                for (long slot : reassignedSlots) {
                    int newOwner = newRing.getNodeForSlot(slot);
                    if (newOwner == myWorkerId) {
                        // Take over orphaned URLs
                        List<CrawlUrl> orphaned = outboundBuffer.drainBuffer(deadWorker);
                        orphaned.forEach(frontier::add);
                    }
                }
            }
            this.ring = newRing;
        }
    }
}
```

**Properties**: Losing 1 worker out of 10,000 → only 0.01% of URLs need reassignment. No thundering herd.

---

## 📊 Non-Functional Requirements Compliance (Educative Summary)

| Requirement | Technique |
|-------------|-----------|
| **Scalability** | Microservices architecture; components can be added/removed on demand; consistent hashing distributes hostnames across workers |
| **Extensibility & Modularity** | New communication protocol modules in HTML fetcher; new MIME scheme processing modules in extractor |
| **Consistency** | Calculation and comparison of checksums of URLs and Documents in respective data stores |
| **Performance** | Increase workers; blob stores for content; high priority to robots.txt; self-throttle per domain |
| **Scheduling** | Pre-defined default recrawl frequency; separate queues for various priority URLs |

---

## 📊 Key Trade-offs & Decision Matrix

| Decision | Options Considered | Chosen | Why |
|----------|-------------------|--------|-----|
| **Architecture** | Centralized / Decentralized / Hybrid | Both (interview dependent) | Centralized for standard; Decentralized for "no central infra" constraint |
| **URL ownership** | Central coordinator / Consistent hashing / Random | Consistent hashing | Deterministic, no coordination, graceful rebalancing |
| **Deduplication** | URL checksum only / Document checksum only / Both / Bloom+RocksDB | Two-phase (URL + Document) or Bloom+RocksDB | URL catches duplicate URLs; Document catches duplicate content |
| **Communication** | Per-URL forwarding / Batched / No forwarding | Batched every 30s with gzip | 85% compression; ~5 KB per batch per peer |
| **Frontier** | Single PQ / Per-domain queues / Multiple priority queues | Per-domain queues with round-robin / Multiple priority buckets | Politeness-aware; avoids domain starvation |
| **Failure detection** | Heartbeats / Central monitor / Gossip | Gossip (decentralized) or Health checks (centralized) | Matches architecture choice |
| **Crash recovery** | Replay from scratch / Checkpoint | Checkpoint every 5 min to local SSD | ~5 min max re-crawl on restart |
| **Content storage** | Shared blob store / Local disk / HDFS | Blob store (centralized) or Local SSD (decentralized) | Matches architecture choice |
| **Extensibility** | Monolithic / Modular plugins | Modular (protocol modules + MIME modules) | Add support without changing pipeline |

---

## 🏗️ Technology Stack Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│  CENTRALIZED VARIANT                                                │
│  ─────────────────                                                  │
│  Service Host: Java/Go (worker management)                         │
│  Scheduler: Priority Queue + PostgreSQL/MySQL (URL frontier)        │
│  DNS Resolver: Local cache + recursive DNS                          │
│  HTML Fetcher: Async HTTP client (Netty) + FTP modules             │
│  Extractor: HTML parser + MIME-type processors                     │
│  Duplicate Eliminator: Redis/RocksDB (URL + Doc checksum stores)   │
│  Blob Store: S3 / HDFS / Cassandra (crawled content)               │
│  Cache/DIS: Redis / Memcached (document input stream)              │
│  Load Balancing: Client-side LB across workers                     │
├─────────────────────────────────────────────────────────────────────┤
│  DECENTRALIZED VARIANT                                              │
│  ────────────────────                                               │
│  Language: Java 17+                                                 │
│  HTTP: Netty / Java HttpClient (async)                             │
│  Dedup: Guava BloomFilter (in-memory) + RocksDB (on-disk)          │
│  IPC: gRPC + Protocol Buffers (inter-worker forwarding)            │
│  Storage: Local SSD (page storage + checkpoints)                    │
│  Protocols: Gossip (UDP), Consistent Hashing, Anti-Entropy          │
│  NO external dependencies: No Kafka, Redis, PostgreSQL, S3          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Talking Points

### For Centralized Variant (Standard Interview)
1. **"Two-phase dedup catches both URL duplicates and content duplicates"** — URL checksum alone misses redirections; document checksum catches those. Together, comprehensive dedup.
2. **"The Scheduler is the brain"** — Priority queue + relational DB for URL frontier. Handles both seed URLs and crawler-extracted URLs. Supports recrawl scheduling.
3. **"Extensibility is key"** — Modular HTML fetcher (HTTP, FTP modules) and modular Extractor (text, image, video MIME types). New protocols/types = new module, no pipeline changes.
4. **"Crawler traps require multi-layer defense"** — URL scheme analysis + domain page count limits + robots.txt + politeness throttling. No single solution catches all traps.

### For Decentralized Variant (Meta/Bloomberg)
5. **"`hash(url) % N` is the entire coordination mechanism"** — Every worker independently computes who owns every URL. No leader election, no distributed lock.
6. **"Bloom Filter + RocksDB = two-tier dedup"** — Bloom catches 99.9% in < 1µs. RocksDB resolves ambiguous cases exactly and persists across restarts.
7. **"Batching turns 10 billion messages into 5 KB bursts"** — Batch every 30s + gzip. 85% compression on URL data. ~14 MB/day per worker.
8. **"Gossip protocol replaces a coordinator"** — Fanout=3, 10s intervals → entire 10K cluster converges in ~90 seconds. No SPOF.
9. **"Politeness is local, not global"** — Each worker tracks per-domain rate limits for URLs it owns. Deterministic ownership prevents concurrent crawling of the same domain by multiple workers.
10. **"Checkpoint every 5 minutes, not every URL"** — On crash, lose at most 5 min of work. Re-crawling is idempotent and cheap.

---

**References**:
- Educative — Grokking Modern System Design Interview: Web Crawler
- Google's original web crawler architecture (Brin & Page, 1998)
- Mercator: A Scalable, Extensible Web Crawler (Heydon & Najork, 1999)
- IRLbot: Scaling to 6 Billion Pages and Beyond (Lee et al., 2008)
- Consistent Hashing and Random Trees (Karger et al., 1997)
- Gossip Protocols: SWIM (Scalable Weakly-consistent Infection-style Membership)

---

**Created**: February 2026 | **Updated**: April 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Sources**: Grokking/Educative (Centralized) + Hello Interview (Decentralized)
**Deep Dives**: 14 topics (choose 2-3 based on interviewer interest and variant)
