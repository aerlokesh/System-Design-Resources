# 🎯 Topic 38: Bloom Filters & Probabilistic Data Structures

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Bloom filters for deduplication and crawling, HyperLogLog for unique counting, Count-Min Sketch for frequency estimation, their math and memory characteristics, practical sizing, and how to articulate probabilistic tradeoffs with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Probabilistic Data Structures](#why-probabilistic-data-structures)
3. [Bloom Filter — "Have I Seen This Before?"](#bloom-filter--have-i-seen-this-before)
4. [Bloom Filter Sizing and Math](#bloom-filter-sizing-and-math)
5. [Bloom Filter Variants](#bloom-filter-variants)
6. [HyperLogLog — "How Many Unique Items?"](#hyperloglog--how-many-unique-items)
7. [Count-Min Sketch — "How Frequent Is This Item?"](#count-min-sketch--how-frequent-is-this-item)
8. [Comparison: When to Use Which](#comparison-when-to-use-which)
9. [Real-World System Applications](#real-world-system-applications)
10. [Deep Dive: Applying Probabilistic Structures to Popular Problems](#deep-dive-applying-probabilistic-structures-to-popular-problems)
11. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
12. [Common Interview Mistakes](#common-interview-mistakes)
13. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Probabilistic data structures** trade a small amount of accuracy for massive reductions in memory and computation. They answer questions like "have I seen this before?" or "how many unique items?" using a tiny fraction of the memory that exact solutions require.

```
The tradeoff:
  Exact answer:   "There are exactly 12,312,847 unique visitors today."
  Requires:       100M visitor IDs × 8 bytes = 800 MB (hash set)
  
  Approximate:    "There are approximately 12,312,847 unique visitors (±0.81%)."
  Requires:       12 KB (HyperLogLog)
  
  Memory savings: 65,000x
  Accuracy loss:  0.81% error margin
  
  For a dashboard showing "~12.3M visitors" — this is identical in value.
  The user cannot distinguish 12,312,847 from 12,212,642.
```

---

## Why Probabilistic Data Structures

### The Scale Problem

```
Web crawler: Have I crawled this URL before?
  10 billion URLs × 50 bytes average = 500 GB hash set
  Can't fit in memory of any single machine

Ad deduplication: Have I counted this click already?
  1 billion clicks/day × 16-byte click_id = 16 GB set per day
  Expensive, slow to check

Unique visitor counting: How many distinct users visited today?
  100M visitors × 8 bytes user_id = 800 MB per metric per day
  Tracking 1,000 pages × 800 MB = 800 GB per day
```

### The Probabilistic Solution

| Problem | Exact Solution | Probabilistic Solution | Memory Savings |
|---|---|---|---|
| URL dedup (10B URLs) | 500 GB hash set | 1.5 GB Bloom filter | 333x |
| Unique visitors (100M) | 800 MB hash set | 12 KB HyperLogLog | 65,000x |
| Frequency counting (1B items) | 16 GB hash map | 32 MB Count-Min Sketch | 500x |

### When Approximation Is Acceptable

```
✅ Dashboard metrics: "~12.3M unique visitors" (±0.81% is invisible)
✅ Content dedup: "Probably seen this URL" (false positive → just skip, no harm)
✅ Cache optimization: "Probably NOT in cache" (false negative → just query DB)
✅ Trend detection: "This IP appears frequently" (exact count unnecessary)
✅ Spam filtering: "Probably known spammer" (false positive → manual review)

❌ Financial counts: "$12,312,847 in revenue" (must be exact)
❌ Inventory: "3 items remaining" (can't be approximate at low counts)
❌ Security: "User authenticated" (false positive → security breach)
❌ Billing: "10,003 clicks billed" (must be exact for invoicing)
```

---

## Bloom Filter — "Have I Seen This Before?"

### How It Works

```
Components:
  - Bit array of m bits (all initialized to 0)
  - k independent hash functions (h₁, h₂, ..., hₖ)

INSERT(element):
  For each hash function hᵢ:
    position = hᵢ(element) % m
    bit_array[position] = 1

QUERY(element):
  For each hash function hᵢ:
    position = hᵢ(element) % m
    if bit_array[position] == 0:
      return "DEFINITELY NOT IN SET"  ← 100% certain
  return "PROBABLY IN SET"  ← might be false positive

Example with m=10, k=3:

Insert "apple":  h₁→2, h₂→5, h₃→8  →  [0,0,1,0,0,1,0,0,1,0]
Insert "banana": h₁→1, h₂→5, h₃→9  →  [0,1,1,0,0,1,0,0,1,1]

Query "apple":   h₁→2✓, h₂→5✓, h₃→8✓  →  "PROBABLY YES" ✅ (correct)
Query "cherry":  h₁→2✓, h₂→5✓, h₃→7✗  →  "DEFINITELY NO" ✅ (correct)
Query "date":    h₁→1✓, h₂→8✓, h₃→9✓  →  "PROBABLY YES" ❌ (FALSE POSITIVE!)
  (All bits happen to be set by other insertions, even though "date" was never inserted)
```

### Key Properties

```
FALSE NEGATIVES: IMPOSSIBLE (zero, never, guaranteed)
  If the Bloom filter says "NOT IN SET," it's 100% correct.
  
  Why? To be "not in set," at least one bit position must be 0.
  That bit position could only be 0 if the element was NEVER inserted
  (because insertion only sets bits to 1, never to 0).

FALSE POSITIVES: POSSIBLE (tunable, typically 0.01% - 1%)
  If the Bloom filter says "PROBABLY IN SET," there's a small chance
  it's wrong — all k bit positions happen to be set by OTHER elements.
  
  The false positive rate depends on:
    m: Number of bits (larger → fewer collisions → lower FP rate)
    k: Number of hash functions (optimal k = (m/n) × ln(2))
    n: Number of inserted elements (more elements → more bits set → higher FP)

DELETION: NOT SUPPORTED (standard Bloom filter)
  Setting a bit to 0 would affect other elements that share that bit.
  Use Counting Bloom Filter if deletion is needed.
```

---

## Bloom Filter Sizing and Math

### The Formulas

```
Given:
  n = expected number of elements
  p = desired false positive probability

Optimal bit array size:
  m = -(n × ln(p)) / (ln(2))²

Optimal number of hash functions:
  k = (m / n) × ln(2)

Memory:
  m bits / 8 = bytes needed
```

### Practical Sizing Table

| Elements (n) | False Positive Rate (p) | Bits (m) | Hash Functions (k) | Memory |
|---|---|---|---|---|
| 1 million | 1% (0.01) | 9.6M bits | 7 | 1.2 MB |
| 1 million | 0.1% (0.001) | 14.4M bits | 10 | 1.8 MB |
| 1 million | 0.01% (0.0001) | 19.2M bits | 13 | 2.4 MB |
| 100 million | 1% | 960M bits | 7 | 120 MB |
| 100 million | 0.1% | 1.44B bits | 10 | 180 MB |
| 1 billion | 1% | 9.6B bits | 7 | 1.2 GB |
| 10 billion | 0.01% | 192B bits | 13 | 24 GB |
| 10 billion | 0.01% (vs hash set) | — | — | vs 500 GB |

### Memory Comparison: Bloom Filter vs Hash Set

```
10 billion URLs:

Hash set:
  10B × 50 bytes (average URL) = 500 GB
  Requires distributed hash table across many machines
  Exact membership test: O(1) but expensive

Bloom filter (0.01% false positive):
  m = ~1.5 GB (bit array)
  k = 10 hash functions
  Fits in RAM of a single server!
  
Memory savings: 333x
False positives: 0.01% (1 in 10,000 — nearly perfect)
False negatives: 0% (guaranteed)
```

### Interview Script

> *"For the web crawler's URL dedup, I'd use a Bloom filter. With 10 billion URLs and a 0.01% false positive rate, it needs only 1.5 GB of memory — compared to a 500 GB hash set. The Bloom filter fits in RAM on a single machine. A 0.01% false positive rate means out of 10 billion URLs, maybe 1 million get incorrectly marked as 'already crawled.' For a web crawler, missing 0.01% of URLs is perfectly acceptable — we'll catch them on the next crawl cycle anyway."*

---

## Bloom Filter Variants

### Counting Bloom Filter (Supports Deletion)

```
Standard Bloom: Each position is a single bit (0 or 1)
Counting Bloom: Each position is a counter (0, 1, 2, 3, ...)

Insert: Increment k counter positions
Delete: Decrement k counter positions

Advantage: Supports deletion!
  When element is removed, decrement counters instead of setting to 0.
  Other elements that share those positions still have their counts.

Disadvantage: More memory (4 bits per counter instead of 1 bit)
  4x memory usage compared to standard Bloom filter.

Use case: Cache invalidation — need to remove URLs from the "crawled" set
  when they need to be re-crawled after content changes.
```

### Scalable Bloom Filter

```
Problem: Standard Bloom filter has fixed capacity.
  If you insert more than n elements, false positive rate degrades.

Scalable Bloom: Creates new Bloom filters as capacity fills up.
  BF₁: 1M capacity, 0.5% FP → fills up
  BF₂: 2M capacity, 0.25% FP → fills up  
  BF₃: 4M capacity, 0.125% FP → active

Query: Check ALL Bloom filters (union)
  Overall FP: p₁ + p₂ + p₃ ≈ 0.5% + 0.25% + 0.125% < 1%
  
  Each subsequent filter has a tighter FP rate.
  Geometric series converges, keeping overall FP bounded.

Used by: Redis BF.RESERVE with EXPANSION parameter.
```

### Cuckoo Filter (Modern Alternative)

```
Advantages over Bloom filter:
  ✅ Supports deletion (without counting)
  ✅ Better space efficiency for low FP rates (< 3%)
  ✅ Faster lookups (CPU cache-friendly)

Disadvantages:
  ❌ More complex implementation
  ❌ Can fail to insert (bucket overflow) in rare cases
  ❌ Less space-efficient for high FP rates (> 3%)

Use when: You need deletion + low false positive rate.
```

---

## HyperLogLog — "How Many Unique Items?"

### The Problem: Counting Unique Items at Scale

```
"How many unique visitors did our website have today?"

Exact approach:
  Hash set of all visitor IDs
  100M visitors × 8 bytes = 800 MB per metric
  1,000 pages × 800 MB = 800 GB per day
  
HyperLogLog approach:
  12 KB per metric (constant, regardless of actual count)
  1,000 pages × 12 KB = 12 MB per day
  
  Memory savings: 65,000x
```

### How It Works (Simplified)

```
Key insight: The probability of seeing a hash value with N leading zeros is 1/2^N.

If you hash many elements and the longest run of leading zeros you observe is N,
then you probably processed approximately 2^N unique elements.

Example:
  hash("user_1")  → 0010110...  (2 leading zeros)
  hash("user_2")  → 0000010...  (5 leading zeros) ← longest so far!
  hash("user_3")  → 1001100...  (0 leading zeros)
  hash("user_4")  → 0001011...  (3 leading zeros)
  
  Longest run: 5 leading zeros → estimate: 2^5 = 32 unique elements
  
  This single estimate is noisy. HyperLogLog improves it:

HyperLogLog:
  Split hash into 2^p registers (typically p=14 → 16,384 registers)
  First p bits of hash → register index
  Remaining bits → count leading zeros
  
  Each register tracks the maximum leading zeros seen
  Final estimate: harmonic mean of all register estimates
  
  With 16,384 registers × 6 bits each = 12 KB
  Error rate: 0.81% (standard error = 1.04/√16384)
```

### Redis HyperLogLog

```
Commands:
  PFADD visitors "user_123"       → Add element (idempotent)
  PFADD visitors "user_456"
  PFADD visitors "user_123"       → Duplicate, no change
  PFCOUNT visitors                → ~2 (estimate, ±0.81%)

Memory:
  PFADD: Constant time O(1)
  PFCOUNT: Constant time O(1)
  Memory per HLL: 12 KB (constant, regardless of cardinality)
  
Merge (union):
  PFMERGE total_visitors page1_visitors page2_visitors page3_visitors
  → Unique visitors across ALL pages (deduplicated!)
  → No need to store individual visitor IDs

Real example:
  "How many unique visitors across all 1,000 pages today?"
  
  Without HLL:
    Store 100M visitor IDs per page → dedup across pages → 100 GB+ per day
    
  With HLL:
    1,000 HLLs × 12 KB = 12 MB per day
    PFMERGE to combine → 12 KB result
    "~12,312,847 unique visitors (±0.81%)"
```

### HyperLogLog Accuracy

```
Standard error: 1.04 / √(number of registers)

With 16,384 registers (default):
  Standard error = 1.04 / √16384 = 0.81%
  
  For 12 million visitors:
    Estimate range (1 std dev): 11,903,000 to 12,097,000
    Estimate range (2 std dev): 11,806,000 to 12,194,000 (95% confidence)
    Estimate range (3 std dev): 11,709,000 to 12,291,000 (99.7% confidence)

Can we improve accuracy?
  Use more registers: 2^16 = 65,536 registers = 48 KB → 0.41% error
  Use more registers: 2^18 = 262,144 registers = 192 KB → 0.20% error
  
  Tradeoff: More memory → better accuracy (but still tiny compared to exact)
```

---

## Count-Min Sketch — "How Frequent Is This Item?"

### The Problem: Frequency Estimation

```
"How many times has this IP address appeared in our log in the last hour?"
"What are the top-K most frequent search queries?"

Exact approach:
  Hash map: {IP → count}
  1 billion unique IPs × (16 bytes IP + 8 bytes count) = 24 GB
  
Count-Min Sketch approach:
  d rows × w columns of counters
  Typically: 5 rows × 10,000 columns × 4 bytes = 200 KB
  
  Memory savings: 120,000x
```

### How It Works

```
Structure: d × w 2D array of counters (all initialized to 0)
  d = number of hash functions (typically 4-7)
  w = width of each row (typically 10K-1M depending on accuracy needs)

INCREMENT(element):
  For each row i (0 to d-1):
    position = hᵢ(element) % w
    table[i][position] += 1

QUERY(element):
  estimates = []
  For each row i:
    position = hᵢ(element) % w
    estimates.append(table[i][position])
  return MIN(estimates)  ← Minimum is the best estimate

Why minimum?
  Hash collisions can only ADD to a counter (overestimate).
  The minimum across all rows has the LEAST collision noise.
  
  Overestimates: POSSIBLE (collisions inflate counts)
  Underestimates: IMPOSSIBLE (counts only go up)
```

### Example

```
w=5, d=3 (tiny example for illustration)

Count("apple"):
  h₁("apple")%5 = 2:  row 0: [0, 0, 1, 0, 0]
  h₂("apple")%5 = 0:  row 1: [1, 0, 0, 0, 0]
  h₃("apple")%5 = 4:  row 2: [0, 0, 0, 0, 1]

Count("banana"):
  h₁("banana")%5 = 2: row 0: [0, 0, 2, 0, 0]  ← collision with "apple"!
  h₂("banana")%5 = 3: row 1: [1, 0, 0, 1, 0]
  h₃("banana")%5 = 1: row 2: [0, 1, 0, 0, 1]

Query("apple"):
  row 0, position 2: 2  ← inflated by "banana" collision
  row 1, position 0: 1  ← accurate
  row 2, position 4: 1  ← accurate
  MIN(2, 1, 1) = 1  ← correct answer!

The minimum filters out the collision noise.
```

### Sizing and Accuracy

```
Parameters:
  w (width): Controls accuracy → error ≈ total_count / w
  d (depth): Controls confidence → confidence = 1 - (1/2)^d

Typical configurations:
  
  Light use (trending detection):
    w = 10,000, d = 5
    Memory: 200 KB
    Error: ±0.01% of total count (with 97% confidence)
    
  Heavy use (network monitoring):
    w = 1,000,000, d = 7
    Memory: 28 MB
    Error: ±0.0001% of total count (with 99.2% confidence)

Use cases:
  ✅ Heavy hitter detection: "Which IPs appear > 10,000 times?"
  ✅ Trending queries: "Which search terms are spiking?"
  ✅ Network flow monitoring: "How much traffic from this source?"
  ✅ Frequency capping: "Has this user seen this ad > 3 times today?"
```

---

## Comparison: When to Use Which

| Data Structure | Question It Answers | Memory | Error Type | Best For |
|---|---|---|---|---|
| **Bloom Filter** | "Is X in the set?" | 1-2 GB for 10B items | False positives (tunable) | Dedup, membership test |
| **HyperLogLog** | "How many unique X?" | 12 KB (constant!) | ±0.81% on cardinality | Unique counting |
| **Count-Min Sketch** | "How many times X?" | 200 KB - 28 MB | Overestimates possible | Frequency estimation |
| **Hash Set** (exact) | "Is X in the set?" | 500 GB for 10B items | None (exact) | When exactness required |
| **Hash Map** (exact) | "How many times X?" | 24 GB for 1B items | None (exact) | When exactness required |

### Decision Flow

```
Need to check membership? ("Have I seen this?")
  → Exact required? → Hash Set
  → Approximate OK? → Bloom Filter (1000x less memory)

Need to count unique items? ("How many distinct?")
  → Exact required? → Hash Set + COUNT
  → Approximate OK? → HyperLogLog (65,000x less memory)

Need frequency of items? ("How often does X appear?")
  → Exact required? → Hash Map
  → Approximate OK? → Count-Min Sketch (500x less memory)

Need top-K frequent items? ("What are the most common?")
  → Count-Min Sketch + min-heap (top-K by estimated frequency)
```

---

## Real-World System Applications

### Web Crawler (Bloom Filter)

```
Google-scale web crawler:

Problem: Don't crawl the same URL twice.
  10 billion known URLs. Is this new URL already crawled?

Solution:
  Bloom filter: 1.5 GB, 0.01% false positive rate, 10 hash functions
  
  For each discovered URL:
    if bloom_filter.contains(url):
      skip (already crawled — or false positive, 0.01% chance)
    else:
      add to crawl queue
      bloom_filter.add(url)

False positive impact:
  0.01% of 10B = 1M URLs incorrectly skipped
  These URLs will be discovered and crawled in the next cycle
  Impact: Negligible delay, no data loss
```

### Ad Click Deduplication (Bloom Filter)

```
Problem: User clicks an ad, click event is sent.
  Network retries → same click might be received multiple times.
  Billing requires: count each click exactly once.

Solution:
  Bloom filter per hour: Tracks click_ids seen in this hour
  1 billion clicks/hour × 0.01% FP → 1.2 GB Bloom filter
  
  On click event:
    if bloom_filter.contains(click_id):
      drop (duplicate)
    else:
      bloom_filter.add(click_id)
      process_click(event)

  New Bloom filter every hour (old one discarded).
  False positives: 0.01% of legitimate clicks incorrectly dropped.
    Impact: ~100K clicks/hour under-counted (out of 1B)
    Reconciliation: Nightly batch corrects from raw event log.
```

### Unique Visitor Counting (HyperLogLog)

```
Problem: "How many unique visitors did each page get today?"
  10,000 pages, 100M total visitors

Solution:
  One HyperLogLog per page: 10,000 × 12 KB = 120 MB total
  
  For each page view:
    PFADD page_visitors:{page_id}:{date} {user_id}
  
  Dashboard query:
    PFCOUNT page_visitors:homepage:2025-03-15
    → ~3,247,000 (±0.81%)
  
  Cross-page unique visitors:
    PFMERGE total_visitors page_visitors:*:2025-03-15
    PFCOUNT total_visitors
    → ~12,312,000 unique visitors across all pages

Alternative (exact):
  Hash set per page: 10,000 × 800 MB = 8 TB per day
  100% accurate, but 65,000x more expensive
```

### Heavy Hitter Detection (Count-Min Sketch)

```
Problem: "Which IP addresses are sending > 10,000 requests/minute?"
  Detecting DDoS attacks in real-time.

Solution:
  Count-Min Sketch: d=5, w=100,000 → 2 MB
  
  For each request:
    cms.increment(ip_address)
    estimated_count = cms.query(ip_address)
    if estimated_count > 10,000:
      flag_for_rate_limiting(ip_address)
  
  Reset sketch every minute (or use sliding window).
  
  False positives: Some legitimate IPs might be flagged.
    Impact: Brief rate limiting until next window. Minimal.
  False negatives: Impossible (counts only overestimate).
    Critical: NEVER misses a heavy hitter.
```

### Cache Miss Optimization (Bloom Filter)

```
Problem: 80% of cache misses are for keys that don't exist in the database.
  Each miss → unnecessary DB query → wasted IOPS.

Solution:
  Bloom filter of all keys that exist in the database.
  
  Cache lookup flow:
    1. Check Redis cache → HIT → return
    2. Check Bloom filter → "DEFINITELY NOT IN DB" → return empty (no DB query!)
    3. Check Bloom filter → "PROBABLY IN DB" → query DB
  
  Impact:
    80% of cache misses → Bloom filter says "not in DB" → saved DB query
    DB load reduced by ~64% (80% of cache misses × 80% cache miss rate)
    
  Used by: Google Bigtable, Apache HBase, LevelDB, RocksDB
    (Bloom filter checked before reading SSTable/HFile from disk)
```

---

## Deep Dive: Applying Probabilistic Structures to Popular Problems

### Twitter — Feed Dedup + Trending

```
Feed deduplication (Bloom Filter):
  When assembling a user's timeline, avoid showing duplicate tweets.
  (Same tweet might appear via multiple paths: following + retweet)
  
  Per-request Bloom filter:
    For each candidate tweet:
      if seen_bloom.contains(tweet_id): skip
      else: seen_bloom.add(tweet_id); add to timeline

Trending topics (Count-Min Sketch):
  Count hashtag frequency in sliding 1-hour window.
  CMS: d=7, w=1,000,000 → 28 MB per window
  
  Every 5 minutes: Extract top-100 by estimated frequency.
  Use min-heap of size 100: For each hashtag in CMS, if count > heap min → replace.

Unique impressions (HyperLogLog):
  "How many unique users saw this tweet?"
  PFADD tweet_impressions:{tweet_id} {viewer_user_id}
  PFCOUNT tweet_impressions:{tweet_id} → ~2,847,000 (±0.81%)
```

### Google Analytics — Page View Metrics

```
Unique visitors per page (HyperLogLog):
  PFADD page:{page_id}:visitors:{date} {visitor_id}
  Memory: 12 KB per page per day
  
Unique visitors across site (HyperLogLog merge):
  PFMERGE site:total:{date} page:*:visitors:{date}
  
Sessions per unique visitor (Count-Min Sketch):
  CMS tracks visitor_id → session_count
  "Average sessions per unique visitor" = total_sessions / HLL_count
  
Bot detection (Count-Min Sketch):
  If CMS(visitor_id) > 1000 requests/hour → probably a bot → exclude from metrics
```

### URL Shortener — Duplicate URL Detection

```
Before creating a short URL, check if the long URL already has one:

Exact approach:
  Query DynamoDB: GetItem(long_url) → short_url or null
  Cost: 1 read capacity unit per check, ~2ms
  At 10K creates/sec: 10K RCUs = $2,100/month

Bloom filter optimization:
  Check Bloom filter first: "Has this long URL been shortened before?"
  If "DEFINITELY NOT" → create new short URL (no DB check needed!)
  If "PROBABLY YES" → query DB to confirm and get existing short URL
  
  At 70% new URLs (never seen before):
    70% avoid DB check entirely → save 7K RCUs/sec
    Cost savings: ~$1,500/month
    Latency: 0.001ms (Bloom) vs 2ms (DB) for 70% of requests
```

---

## Interview Talking Points & Scripts

### Bloom Filter for Dedup

> *"For URL deduplication in the web crawler, I'd use a Bloom filter. With 10 billion URLs and 0.01% false positive rate, it needs only 1.5 GB of memory — compared to 500 GB for a hash set. That's a 333x memory reduction. The 0.01% false positive rate means we might skip about 1 million URLs out of 10 billion — they'll be caught in the next crawl cycle. Zero false negatives means we never miss a duplicate."*

### HyperLogLog for Unique Counting

> *"For counting unique visitors per page, I'd use HyperLogLog in Redis. Each page's HLL takes exactly 12 KB regardless of whether 1 or 100 million visitors hit it. For 10,000 pages, that's 120 MB total — compared to 8 TB for hash sets. The accuracy is ±0.81%, which means '~12.3 million unique visitors' is indistinguishable from exact for a dashboard. And PFMERGE lets us compute cross-page unique visitors without storing individual visitor IDs."*

### Count-Min Sketch for Frequency

> *"For heavy hitter detection, I'd use a Count-Min Sketch. A 2 MB sketch with 5 hash functions and 100K buckets can track the frequency of every IP address in real-time. It can only overestimate, never underestimate — so we never miss a DDoS attacker. The occasional false positive (flagging a legitimate high-traffic IP) is resolved with a brief rate limit and a manual review."*

### The General Probabilistic Tradeoff

> *"The common thread across all these structures is the same tradeoff: massive memory savings in exchange for a small, bounded error rate. For a dashboard showing '~12.3M visitors,' paying 65,000x more memory for exact '12,312,847' adds zero business value. I'd reserve exact counting for billing and financial use cases where every unit matters."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "I'd use a hash set for URL deduplication at 10B scale"
**Why it's wrong**: 10B × 50 bytes = 500 GB. Doesn't fit in memory. Distributed hash table adds massive complexity.
**Better**: Bloom filter at 1.5 GB with 0.01% false positives.

### ❌ Mistake 2: Confusing false positives and false negatives in Bloom filters
**Why it's wrong**: Getting these backwards means misunderstanding the guarantees.
**Better**: "Bloom filters have zero false negatives — if it says 'not in set,' that's 100% guaranteed. False positives are possible but tunable."

### ❌ Mistake 3: Not mentioning the memory savings quantitatively
**Why it's wrong**: The whole point of probabilistic structures is memory efficiency. Without numbers, the value isn't clear.
**Better**: "12 KB for HyperLogLog vs 800 MB for a hash set — 65,000x savings."

### ❌ Mistake 4: Using probabilistic structures for financial/billing data
**Why it's wrong**: Billing requires exact counts. "Approximately $12.3M" is not acceptable for an invoice.
**Better**: Use HLL for dashboard display, exact counting for billing reconciliation.

### ❌ Mistake 5: Not mentioning that Bloom filters don't support deletion
**Why it's wrong**: If you need to remove items, standard Bloom filters can't do it.
**Better**: Mention Counting Bloom Filters or Cuckoo Filters for deletion support.

### ❌ Mistake 6: Proposing probabilistic structures without stating the error rate
**Why it's wrong**: "I'd use a Bloom filter" without specifying the false positive rate doesn't demonstrate understanding.
**Better**: "Bloom filter with 0.01% false positive rate and 10 hash functions."

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│     BLOOM FILTERS & PROBABILISTIC DATA STRUCTURES            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  BLOOM FILTER: "Have I seen this before?"                    │
│    How: Bit array + k hash functions                         │
│    Memory: 1.5 GB for 10B items (vs 500 GB hash set)        │
│    False negatives: ZERO (guaranteed)                        │
│    False positives: Tunable (0.01% - 1%)                     │
│    Deletion: NOT supported (use Counting BF for deletion)    │
│    Use: URL dedup, click dedup, cache miss optimization      │
│                                                              │
│  HYPERLOGLOG: "How many unique items?"                       │
│    How: Register-based cardinality estimation                │
│    Memory: 12 KB (constant, regardless of cardinality!)      │
│    Error: ±0.81% (standard, 16K registers)                   │
│    Redis: PFADD, PFCOUNT, PFMERGE                            │
│    Use: Unique visitors, unique search queries, impressions  │
│                                                              │
│  COUNT-MIN SKETCH: "How frequent is this item?"              │
│    How: d×w counter array + d hash functions                 │
│    Memory: 200 KB - 28 MB (depending on accuracy needs)      │
│    Error: Overestimates possible, underestimates IMPOSSIBLE  │
│    Use: Heavy hitters, trending detection, frequency capping │
│                                                              │
│  WHEN TO USE:                                                │
│    Dashboard metrics → HyperLogLog (approximate OK)          │
│    Dedup check → Bloom Filter (false positive OK)            │
│    Frequency tracking → Count-Min Sketch (overestimate OK)   │
│    Billing/Finance → Exact counting (no approximation)       │
│                                                              │
│  THE TRADEOFF:                                               │
│    Massive memory savings (100-65,000x)                      │
│    Small bounded error rate (0.01% - 1%)                     │
│    For dashboards and detection: approximate = good enough   │
│    For billing and compliance: exact is non-negotiable        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 16: Search & Indexing** — Bloom filters in search index optimization
- **Topic 20: Deduplication** — Bloom filters as dedup mechanism
- **Topic 29: Pre-Computation vs On-Demand** — HyperLogLog for pre-computed metrics
- **Topic 33: Reconciliation** — Approximate (stream) vs exact (batch) counting
- **Topic 39: Trending & Top-K Computation** — Count-Min Sketch for trending

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
