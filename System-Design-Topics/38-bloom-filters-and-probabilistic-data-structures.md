# 🎯 Topic 38: Bloom Filters & Probabilistic Data Structures

> **System Design Interview — Deep Dive**
> Bloom filters for dedup/crawling, HyperLogLog for unique counting, Count-Min Sketch for frequency estimation — memory-efficient approximations.

---

## Bloom Filter

### How It Works
```
Bit array (m bits) + k hash functions
Insert: Set k bit positions to 1
Query:  Check k bit positions — all 1? "Probably exists." Any 0? "Definitely not."

False negatives: ZERO (if it says "not seen," it's 100% correct)
False positives: Tunable (0.01-1%)
```

### Configuration
```
10 billion URLs, 0.01% false positive:
  Memory: ~1.5 GB (bit array)
  Hash functions: 10
  vs Hash set: 200 GB (1000x more memory)
```

### Use Cases
- Web crawler: "Have I crawled this URL before?"
- Ad dedup: "Have I seen this click event before?"
- Cache miss optimization: Check Bloom before querying DB
- Email spam: "Is this sender known?"

## HyperLogLog (Unique Counting)

```
Estimates COUNT(DISTINCT ...) using only 12 KB of memory.
Error rate: ±0.81%

Example:
  How many unique visitors today?
  Hash set: 100M visitors × 8 bytes = 800 MB
  HyperLogLog: 12 KB (regardless of count) — 65,000x less memory

Redis: PFADD visitors "user_123"
       PFCOUNT visitors → ~12,312,847 (±0.81% error)
```

## Count-Min Sketch

```
Estimates frequency of elements in a stream.
"How many times has this IP appeared in the last hour?"

Memory: Fixed size (regardless of unique elements)
Error: Overestimates possible, underestimates impossible
Use: Identifying heavy hitters, frequency estimation
```

## Interview Script
> *"The Bloom filter lets us check 'have I seen this URL before?' in < 1 microsecond with zero false negatives. For 10 billion URLs: 1.5 GB of memory vs 200 GB for a hash set — 1000x savings."*

> *"HyperLogLog estimates cardinality using only 12 KB regardless of actual count. For 'unique visitors per day': approximately 12.3 million at 0.81% error is just as useful as exactly 12,312,847. 65,000x memory savings."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│     BLOOM FILTERS & PROBABILISTIC DATA STRUCTURES            │
├──────────────────────────────────────────────────────────────┤
│  BLOOM FILTER: "Have I seen this before?"                    │
│    Memory: 1000x less than hash set                          │
│    False negatives: ZERO. False positives: tunable (0.01%)   │
│    Use: Crawler dedup, click dedup, cache miss optimization  │
│                                                              │
│  HYPERLOGLOG: "How many unique items?"                       │
│    Memory: 12 KB (constant). Error: ±0.81%                   │
│    Use: Unique visitors, unique search queries               │
│                                                              │
│  COUNT-MIN SKETCH: "How frequent is this item?"              │
│    Memory: Fixed. Error: Overestimates possible              │
│    Use: Heavy hitters, IP frequency counting                 │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
