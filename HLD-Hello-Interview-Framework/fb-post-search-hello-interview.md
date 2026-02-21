# Design Facebook Post Search - Hello Interview Framework

> **Question**: Design a search system for Facebook posts that allows users to search posts by keyword, returning results sorted by recency or relevance. The constraint: you are NOT allowed to use Elasticsearch or any pre-built full-text search engine — you must design the indexing and retrieval from scratch.
>
> **Asked at**: Meta, Amazon
>
> **Difficulty**: Medium | **Level**: Senior

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
1. **Search Posts by Keyword**: Users type a query (e.g., "birthday party") and receive matching posts containing those terms.
2. **Sort by Recency or Relevance**: Results can be sorted by most recent first, or by a relevance score (TF-IDF-like ranking).
3. **Near-Real-Time Indexing**: New posts must be searchable within 1 minute of creation.
4. **Search All Posts**: All posts must be discoverable — including old and unpopular ones (though they can be slower to retrieve).

#### Nice to Have (P1)
- Phrase search ("happy birthday" as exact phrase).
- Boolean operators (AND, OR, NOT).
- Autocomplete / query suggestions.
- Search within a user's own posts or a specific group's posts.
- Highlighted matching snippets in results.

#### Below the Line (Out of Scope)
- Fuzzy matching (e.g., "bird" matching "ostrich").
- Personalized ranking (results dependent on who is searching).
- Privacy rules and content filtering.
- Sophisticated relevance algorithms (ML-based ranking).
- Images and media search.

**Key Constraint**: No Elasticsearch, Solr, or any pre-built full-text search engine. We must build the inverted index and retrieval ourselves.

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Query Latency** | < 500ms median, < 2s p99 | Users expect fast search results |
| **Indexing Latency** | < 1 minute from post creation to searchable | Near-real-time experience |
| **Query Throughput** | 50K QPS | Facebook-scale search volume |
| **Availability** | 99.9% | Search is a core feature |
| **Scalability** | 100B+ posts, 10B+ unique terms | Facebook-scale corpus |
| **Hot/Cold Data** | Hot (recent/popular) fast; cold (old) acceptable slower | Most searches are for recent content |
| **Storage** | Handle 100B posts × average 50 words = 5T word occurrences | Inverted index must fit distributed storage |

### Capacity Estimation

```
Posts:
  Total posts: 100B (all time)
  New posts per day: 500M
  Average post length: 50 words (after tokenization)
  Unique terms: ~10B (including misspellings, names, etc.)

Inverted Index Size:
  Each posting: term_id (8B) + doc_id (8B) + position (4B) + metadata (4B) = ~24 bytes
  Total postings: 100B posts × 50 words = 5T postings
  Index size: 5T × 24 bytes = 120 TB (uncompressed)
  With compression (variable-byte encoding): ~30 TB

Query Load:
  Search queries: ~50K QPS
  Average query terms: 3 words
  Posting lists to intersect: 3 lists per query
  Average posting list length: varies wildly (common words: billions; rare: thousands)

Hot/Cold Split:
  Hot data (last 7 days): 500M × 7 = 3.5B posts = ~3% of total
  Cold data (older): 96.5B posts = 97% of total
  Observation: 80% of queries match recent posts → optimize hot path
```

---

## 2️⃣ Core Entities

### Entity 1: Post (Document)
```java
public class Post {
    private final long postId;            // Unique post ID
    private final long authorId;          // Who wrote it
    private final String content;         // Raw text
    private final Instant createdAt;      // When posted
    private final int likeCount;          // For relevance scoring
    private final int commentCount;       // For relevance scoring
    private final PostVisibility visibility; // PUBLIC, FRIENDS, PRIVATE
}
```

### Entity 2: Inverted Index Entry
```java
/**
 * An inverted index maps: term → list of (document_id, positions, metadata)
 * 
 * Example: "birthday" → [(post_123, [5, 12]), (post_456, [1]), (post_789, [3, 8, 22])]
 * This means "birthday" appears in post_123 at word positions 5 and 12, etc.
 */
public class PostingList {
    private final String term;                    // The indexed term
    private final List<Posting> postings;         // Sorted by docId (for intersection)
    
    public static class Posting implements Comparable<Posting> {
        private final long postId;
        private final List<Integer> positions;    // Word positions in the post
        private final Instant postCreatedAt;      // For recency sorting
        private final float termFrequency;        // TF component for relevance
    }
}
```

### Entity 3: Search Result
```java
public class SearchResult {
    private final long postId;
    private final long authorId;
    private final String snippet;          // Highlighted matching text
    private final double relevanceScore;   // TF-IDF or BM25 score
    private final Instant createdAt;
}
```

---

## 3️⃣ API Design

### 1. Search Posts
```
GET /api/v1/search?q=birthday+party&sort=RELEVANCE&limit=20&cursor=eyJ...

Headers:
  Authorization: Bearer <JWT>

Response (200 OK):
{
  "query": "birthday party",
  "total_results": 12456,
  "sort": "RELEVANCE",
  "results": [
    {
      "post_id": "post_123",
      "author": { "user_id": "user_456", "name": "Jane Doe" },
      "snippet": "Had an amazing <b>birthday</b> <b>party</b> last night!",
      "relevance_score": 8.72,
      "created_at": "2025-01-10T19:30:00Z",
      "like_count": 234,
      "comment_count": 45
    },
    {
      "post_id": "post_789",
      "author": { "user_id": "user_012", "name": "John Smith" },
      "snippet": "Planning my daughter's <b>birthday</b> <b>party</b>. Any venue suggestions?",
      "relevance_score": 7.89,
      "created_at": "2025-01-10T18:00:00Z",
      "like_count": 56,
      "comment_count": 23
    }
  ],
  "next_cursor": "eyJzY29yZSI6Ny44OSwicG9zdF9pZCI6Nzg5fQ=="
}
```

### 2. Index a New Post (Internal — from Post Service)
```
POST /api/v1/index

Request:
{
  "post_id": "post_123",
  "author_id": "user_456",
  "content": "Had an amazing birthday party last night! Best friends, great cake.",
  "created_at": "2025-01-10T19:30:00Z",
  "visibility": "PUBLIC"
}

Response (202 Accepted):
{
  "status": "QUEUED",
  "estimated_index_time_seconds": 30
}
```

---

## 4️⃣ Data Flow

### Flow 1: Indexing a New Post
```
1. User creates a post: "Had an amazing birthday party last night!"
   ↓
2. Post Service saves to Post DB, publishes event to Kafka "new-posts"
   ↓
3. Indexer Service consumes event:
   a. Tokenize: ["had", "an", "amazing", "birthday", "party", "last", "night", 
                  "best", "friends", "great", "cake"]
   b. Remove stop words: ["amazing", "birthday", "party", "last", "night",
                           "best", "friends", "great", "cake"]
   c. For each term: append (post_id, position, timestamp) to that term's posting list
   d. Write to Hot Index (in-memory + WAL) for immediate searchability
   e. Periodically flush to Cold Index (on-disk SSTable) via compaction
   ↓
4. Post is searchable within seconds (hot path) to ~1 minute (cold path)
```

### Flow 2: Searching for "birthday party"
```
1. User types "birthday party" → GET /api/v1/search?q=birthday+party&sort=RELEVANCE
   ↓
2. Query Service:
   a. Tokenize query: ["birthday", "party"]
   b. Look up posting list for "birthday" → [post_123, post_456, post_789, ...]
   c. Look up posting list for "party" → [post_123, post_234, post_789, ...]
   d. Intersect posting lists: [post_123, post_789] (posts containing BOTH terms)
   e. Score each result (BM25 / TF-IDF + recency boost)
   f. Sort by relevance score (or recency)
   g. Return top 20 results with snippets
   ↓
3. Query path:
   a. First check Hot Index (in-memory, recent posts) — < 10ms
   b. Then check Warm Index (SSD, last 30 days) — < 100ms
   c. Optionally check Cold Index (HDD/S3, older posts) — < 2s
   d. Merge results from all tiers
   ↓
4. Total latency: < 500ms median (most matches in hot/warm tier)
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                   │
│   [Web]        [Mobile]        [API]                                  │
│   GET /search?q=birthday+party                                        │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │    API Gateway / LB      │
              │  • Auth, Rate Limiting   │
              └────────────┬────────────┘
                           │
         ┌─────────────────┼──────────────────────┐
         ▼                                         ▼
┌─────────────────┐                     ┌─────────────────────┐
│  Query Service   │                     │  Indexer Service     │
│  • Parse query   │                     │  • Consume Kafka     │
│  • Look up index │                     │  • Tokenize posts    │
│  • Intersect     │                     │  • Build postings    │
│  • Score & rank  │                     │  • Write to index    │
└───────┬─────────┘                     └──────────┬──────────┘
        │                                          │
        ▼                                          ▼
┌──────────────────────────────────────────────────────────┐
│                    INVERTED INDEX (Distributed)            │
│                                                           │
│  ┌─────────────────────────────────────────────┐         │
│  │ HOT INDEX (In-Memory + WAL)                  │         │
│  │ • Last 24-72 hours of posts                  │         │
│  │ • ConcurrentHashMap<String, PostingList>      │         │
│  │ • ~500 GB across 50 nodes                    │         │
│  │ • < 10ms lookup                              │         │
│  └─────────────────────────────────────────────┘         │
│                                                           │
│  ┌─────────────────────────────────────────────┐         │
│  │ WARM INDEX (SSD-backed SSTable)              │         │
│  │ • Last 30 days of posts                      │         │
│  │ • Immutable sorted files (like LSM-tree)     │         │
│  │ • ~5 TB across 100 nodes                     │         │
│  │ • < 100ms lookup                             │         │
│  └─────────────────────────────────────────────┘         │
│                                                           │
│  ┌─────────────────────────────────────────────┐         │
│  │ COLD INDEX (HDD / S3)                        │         │
│  │ • All historical posts (100B+)               │         │
│  │ • Compressed, partitioned by term prefix     │         │
│  │ • ~30 TB compressed                          │         │
│  │ • < 2s lookup (acceptable for old posts)     │         │
│  └─────────────────────────────────────────────┘         │
└──────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────┐     ┌──────────────────────┐
│  Post DB (PostgreSQL) │     │  Kafka                │
│  Source of truth for  │     │  "new-posts" topic    │
│  post content         │     │  "post-updates" topic │
└──────────────────────┘     └──────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Query Service** | Parse queries, look up index, score results | Java/Spring Boot | Stateless, 50+ pods |
| **Indexer Service** | Consume post events, tokenize, build index | Java | 100+ consumers |
| **Hot Index** | In-memory inverted index for recent posts | Custom (ConcurrentHashMap + WAL) | 50 nodes, sharded by term |
| **Warm Index** | SSD-based immutable index files (SSTable) | Custom LSM-tree / SSTable | 100 nodes, sharded by term |
| **Cold Index** | Compressed historical index | S3 + local SSD cache | On-demand, cached |
| **Post DB** | Store full post content (for snippets) | PostgreSQL (sharded) | 64+ shards |
| **Kafka** | Event stream for new/updated posts | Apache Kafka | 200+ partitions |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Building an Inverted Index From Scratch

**The Problem**: Without Elasticsearch, we need to build our own inverted index. This is the core data structure that maps terms to the documents containing them.

```java
public class InvertedIndex {

    /**
     * An inverted index for full-text search.
     * 
     * Structure:
     *   term → PostingList(sorted list of postings)
     *   Each posting = (docId, positions[], termFrequency, timestamp)
     * 
     * This class handles the HOT index (in-memory for recent posts).
     * Periodically flushed to disk as immutable SSTables (WARM index).
     */
    
    // Term → posting list (thread-safe for concurrent reads/writes)
    private final ConcurrentHashMap<String, PostingList> index = new ConcurrentHashMap<>();
    
    // Write-ahead log for durability
    private final WriteAheadLog wal;
    
    // Document count (for IDF calculation)
    private final AtomicLong totalDocuments = new AtomicLong(0);
    
    /**
     * Index a new post. Called by the Indexer Service for each new post.
     */
    public void indexPost(Post post) {
        // Step 1: Tokenize the post content
        List<String> tokens = tokenizer.tokenize(post.getContent());
        
        // Step 2: Remove stop words
        tokens = stopWordFilter.filter(tokens);
        
        // Step 3: For each unique term, create/update posting
        Map<String, List<Integer>> termPositions = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            String term = tokens.get(i).toLowerCase();
            termPositions.computeIfAbsent(term, k -> new ArrayList<>()).add(i);
        }
        
        // Step 4: Write to WAL first (durability)
        wal.append(new IndexEntry(post.getPostId(), termPositions, post.getCreatedAt()));
        
        // Step 5: Update in-memory index
        for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
            String term = entry.getKey();
            List<Integer> positions = entry.getValue();
            
            float tf = (float) positions.size() / tokens.size(); // Term frequency
            
            Posting posting = new Posting(
                post.getPostId(),
                positions,
                post.getCreatedAt(),
                tf
            );
            
            index.compute(term, (key, existingList) -> {
                if (existingList == null) {
                    existingList = new PostingList(term);
                }
                existingList.addPosting(posting);
                return existingList;
            });
        }
        
        totalDocuments.incrementAndGet();
    }
    
    /**
     * Look up a term's posting list.
     */
    public PostingList getPostingList(String term) {
        return index.getOrDefault(term.toLowerCase(), PostingList.empty());
    }
    
    /**
     * Get document frequency for IDF calculation.
     */
    public int getDocumentFrequency(String term) {
        PostingList list = index.get(term.toLowerCase());
        return list != null ? list.size() : 0;
    }
    
    public long getTotalDocuments() {
        return totalDocuments.get();
    }
}

/**
 * Posting list: sorted list of postings for a single term.
 * Sorted by postId for efficient intersection (merge-based).
 */
public class PostingList {
    private final String term;
    private final List<Posting> postings; // Sorted by postId
    
    /** Add a posting, maintaining sorted order */
    public synchronized void addPosting(Posting posting) {
        // Binary search for insertion point
        int idx = Collections.binarySearch(postings, posting);
        if (idx < 0) {
            postings.add(-(idx + 1), posting);
        }
        // If already exists (same postId), update positions
    }
    
    /** Get all postings (for iteration during search) */
    public List<Posting> getPostings() {
        return Collections.unmodifiableList(postings);
    }
    
    public int size() {
        return postings.size();
    }
}
```

**Index Structure Visualization**:
```
Term Dictionary (in-memory HashMap):
  "birthday" → PostingList [
    (post_001, positions=[5], tf=0.10, time=2025-01-10T19:30),
    (post_042, positions=[1,8], tf=0.15, time=2025-01-10T18:00),
    (post_789, positions=[3], tf=0.08, time=2025-01-09T12:00),
    ... (millions more)
  ]
  
  "party" → PostingList [
    (post_001, positions=[6], tf=0.10, time=2025-01-10T19:30),
    (post_234, positions=[2,5,11], tf=0.20, time=2025-01-10T17:00),
    (post_789, positions=[4], tf=0.08, time=2025-01-09T12:00),
    ...
  ]
  
Query "birthday party":
  1. Get postings for "birthday": [001, 042, 789, ...]
  2. Get postings for "party":    [001, 234, 789, ...]
  3. Intersect (sorted merge):    [001, 789] ← both terms present
```

---

### Deep Dive 2: Posting List Intersection — The Core Search Algorithm

**The Problem**: Given posting lists for "birthday" (1M postings) and "party" (2M postings), find documents containing BOTH terms. We need an efficient intersection algorithm.

```java
public class PostingListIntersector {

    /**
     * Intersect two sorted posting lists to find documents containing both terms.
     * Uses a merge-based algorithm: O(n + m) where n, m are list sizes.
     * 
     * This is the core operation of search. For a 3-word query, we intersect 3 lists.
     */
    public List<ScoredPosting> intersect(PostingList listA, PostingList listB) {
        List<ScoredPosting> result = new ArrayList<>();
        
        Iterator<Posting> iterA = listA.getPostings().iterator();
        Iterator<Posting> iterB = listB.getPostings().iterator();
        
        if (!iterA.hasNext() || !iterB.hasNext()) return result;
        
        Posting a = iterA.next();
        Posting b = iterB.next();
        
        while (true) {
            int cmp = Long.compare(a.getPostId(), b.getPostId());
            
            if (cmp == 0) {
                // Match! Both terms appear in this document
                result.add(new ScoredPosting(a.getPostId(), a, b));
                
                if (!iterA.hasNext() || !iterB.hasNext()) break;
                a = iterA.next();
                b = iterB.next();
            } else if (cmp < 0) {
                // a.postId < b.postId → advance a
                if (!iterA.hasNext()) break;
                a = iterA.next();
            } else {
                // a.postId > b.postId → advance b
                if (!iterB.hasNext()) break;
                b = iterB.next();
            }
        }
        
        return result;
    }
    
    /**
     * Intersect multiple posting lists (for multi-word queries).
     * Strategy: start with the shortest list (fewest postings) for efficiency.
     */
    public List<ScoredPosting> intersectMultiple(List<PostingList> lists) {
        if (lists.isEmpty()) return Collections.emptyList();
        
        // Sort by size: intersect smallest lists first (optimization)
        lists.sort(Comparator.comparingInt(PostingList::size));
        
        List<ScoredPosting> result = lists.get(0).getPostings().stream()
            .map(p -> new ScoredPosting(p.getPostId(), p))
            .collect(Collectors.toList());
        
        for (int i = 1; i < lists.size() && !result.isEmpty(); i++) {
            result = intersectWithList(result, lists.get(i));
        }
        
        return result;
    }
    
    /**
     * Skip-pointer optimization: for very long posting lists,
     * use skip pointers to jump ahead instead of scanning every entry.
     * 
     * Every sqrt(N) entries, store a "skip pointer" to jump ahead.
     * If the target docId is beyond the skip, jump directly.
     */
    public List<ScoredPosting> intersectWithSkipPointers(PostingList shortList, PostingList longList) {
        List<ScoredPosting> result = new ArrayList<>();
        int skipInterval = (int) Math.sqrt(longList.size());
        
        int longIdx = 0;
        for (Posting shortPosting : shortList.getPostings()) {
            long targetDocId = shortPosting.getPostId();
            
            // Use skip pointers to advance in the long list
            while (longIdx + skipInterval < longList.size() &&
                   longList.getPostings().get(longIdx + skipInterval).getPostId() <= targetDocId) {
                longIdx += skipInterval;
            }
            
            // Linear scan from skip point
            while (longIdx < longList.size()) {
                long longDocId = longList.getPostings().get(longIdx).getPostId();
                if (longDocId == targetDocId) {
                    result.add(new ScoredPosting(targetDocId, shortPosting, 
                        longList.getPostings().get(longIdx)));
                    longIdx++;
                    break;
                } else if (longDocId > targetDocId) {
                    break;
                }
                longIdx++;
            }
        }
        
        return result;
    }
}
```

**Intersection Performance**:
```
Query: "birthday party"
  "birthday" posting list: 1,000,000 postings (sorted by docId)
  "party" posting list:    2,000,000 postings (sorted by docId)
  
  Naive (nested loop): O(1M × 2M) = O(2 trillion) → IMPOSSIBLE
  
  Sorted merge: O(1M + 2M) = O(3M) → ~30ms ✓
  
  With skip pointers (sqrt(N) skip interval):
    Skip interval for "party": sqrt(2M) ≈ 1,414
    Effective comparisons: ~1M × log(1,414) ≈ 10M → ~10ms ✓

  Start with shortest list: always begin intersection with the rarest term
```

---

### Deep Dive 3: Tokenization & Text Processing Pipeline

**The Problem**: Raw post text "I'm going to my friend's BIRTHDAY party!! 🎂🎉" needs to be converted into searchable terms: ["going", "friend", "birthday", "party"].

```java
public class TextTokenizer {

    /**
     * Tokenization pipeline for search indexing:
     * 1. Unicode normalization (NFD → NFC)
     * 2. Lowercase
     * 3. Split on whitespace and punctuation
     * 4. Remove stop words (the, is, at, etc.)
     * 5. Optional: stemming (running → run)
     * 6. Remove very short tokens (< 2 chars)
     * 7. Remove emoji and special characters
     */
    
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "is", "are", "was", "were", "be", "been",
        "am", "do", "did", "does", "have", "has", "had", "will", "would",
        "can", "could", "shall", "should", "may", "might", "must",
        "i", "me", "my", "we", "our", "you", "your", "he", "she",
        "it", "they", "them", "this", "that", "and", "or", "but",
        "in", "on", "at", "to", "for", "of", "with", "by", "from",
        "not", "no", "so", "if", "as", "up", "out", "just"
    );
    
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        
        // Step 1: Unicode normalize
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        
        // Step 2: Lowercase
        normalized = normalized.toLowerCase(Locale.ENGLISH);
        
        // Step 3: Extract word tokens (letters and digits only)
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        int position = 0;
        
        while (matcher.find()) {
            String token = matcher.group();
            
            // Step 4: Skip stop words
            if (STOP_WORDS.contains(token)) continue;
            
            // Step 5: Skip very short tokens
            if (token.length() < 2) continue;
            
            // Step 6: Optional stemming (Porter stemmer)
            // token = porterStemmer.stem(token);
            
            tokens.add(token);
        }
        
        return tokens;
    }
    
    /**
     * Tokenize with positions (needed for phrase search and proximity scoring).
     * Returns a map of term → list of positions in the document.
     */
    public Map<String, List<Integer>> tokenizeWithPositions(String text) {
        Map<String, List<Integer>> termPositions = new LinkedHashMap<>();
        List<String> tokens = tokenize(text);
        
        for (int i = 0; i < tokens.size(); i++) {
            termPositions.computeIfAbsent(tokens.get(i), k -> new ArrayList<>()).add(i);
        }
        
        return termPositions;
    }
}
```

---

### Deep Dive 4: Relevance Scoring — BM25

**The Problem**: After intersecting posting lists, we have a set of matching documents. How do we rank them? We use BM25 (Best Matching 25), the industry-standard ranking function.

```java
public class BM25Scorer {

    /**
     * BM25 scoring for search results.
     * 
     * BM25(D, Q) = Σ IDF(qi) × [f(qi, D) × (k1 + 1)] / [f(qi, D) + k1 × (1 - b + b × |D|/avgdl)]
     * 
     * Where:
     *   f(qi, D) = frequency of term qi in document D
     *   |D| = length of document D (in words)
     *   avgdl = average document length
     *   k1 = 1.2 (term frequency saturation parameter)
     *   b = 0.75 (document length normalization parameter)
     *   IDF(qi) = log((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)
     *   N = total documents
     *   n(qi) = documents containing term qi
     */
    
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    
    private final long totalDocuments;
    private final double averageDocumentLength;
    
    public BM25Scorer(long totalDocuments, double averageDocumentLength) {
        this.totalDocuments = totalDocuments;
        this.averageDocumentLength = averageDocumentLength;
    }
    
    /**
     * Compute IDF (Inverse Document Frequency) for a term.
     * Rare terms get higher IDF scores.
     */
    public double idf(int documentFrequency) {
        return Math.log((totalDocuments - documentFrequency + 0.5) / (documentFrequency + 0.5) + 1);
    }
    
    /**
     * Score a single document for a query.
     */
    public double score(ScoredPosting posting, List<String> queryTerms,
                        int documentLength, Map<String, Integer> termDocFrequencies) {
        double score = 0.0;
        
        for (String term : queryTerms) {
            int tf = posting.getTermFrequency(term); // occurrences of term in this doc
            int df = termDocFrequencies.getOrDefault(term, 1);
            
            double idfScore = idf(df);
            double tfNormalized = (tf * (K1 + 1)) / 
                (tf + K1 * (1 - B + B * documentLength / averageDocumentLength));
            
            score += idfScore * tfNormalized;
        }
        
        return score;
    }
    
    /**
     * Combined score: BM25 relevance + recency boost + engagement boost.
     * Used for the default "RELEVANCE" sort order.
     */
    public double combinedScore(ScoredPosting posting, List<String> queryTerms,
                                 int docLength, Map<String, Integer> termDocFreqs,
                                 Instant postCreatedAt, int likeCount, int commentCount) {
        double bm25 = score(posting, queryTerms, docLength, termDocFreqs);
        
        // Recency boost: recent posts get a multiplier (decays exponentially)
        double hoursAge = Duration.between(postCreatedAt, Instant.now()).toHours();
        double recencyBoost = Math.exp(-hoursAge / 168.0); // Half-life = 1 week
        
        // Engagement boost: popular posts get a small boost
        double engagementBoost = Math.log1p(likeCount + commentCount * 2) / 10.0;
        
        return bm25 * (1.0 + recencyBoost) + engagementBoost;
    }
}
```

**BM25 Scoring Example**:
```
Query: "birthday party"
Document: "Had an amazing birthday party last night! Best friends, great cake."

Term "birthday":
  TF = 1 (appears once in 10-word doc)
  DF = 500,000 (500K docs contain "birthday")
  IDF = log((100B - 500K + 0.5) / (500K + 0.5) + 1) = 12.2
  TF_normalized = (1 × 2.2) / (1 + 1.2 × (0.25 + 0.75 × 10/50)) = 1.47
  Score for "birthday" = 12.2 × 1.47 = 17.9

Term "party":
  TF = 1, DF = 2,000,000
  IDF = 10.8, TF_norm = 1.47
  Score for "party" = 10.8 × 1.47 = 15.9

BM25 total = 17.9 + 15.9 = 33.8
Recency boost (posted 2 hours ago) = ×1.99
Engagement boost (234 likes) = +0.55

Combined score = 33.8 × 1.99 + 0.55 = 67.8
```

---

### Deep Dive 5: LSM-Tree Based Index Storage (Hot → Warm → Cold)

**The Problem**: The hot in-memory index can't hold 100B posts. We need an on-disk index that supports fast reads while handling continuous writes. We use an LSM-tree (Log-Structured Merge-tree) approach.

```java
public class LSMIndexManager {

    /**
     * LSM-tree based index management.
     * 
     * Write path:
     *   1. New postings → in-memory MemTable (hot index)
     *   2. When MemTable is full (~1 GB) → flush to disk as immutable SSTable
     *   3. Periodically compact SSTables (merge + deduplicate)
     * 
     * Read path:
     *   1. Check MemTable (most recent data)
     *   2. Check Level-0 SSTables (recently flushed)
     *   3. Check Level-1+ SSTables (compacted, larger files)
     *   4. Use Bloom filters to skip SSTables that don't contain the term
     */
    
    private final MemTable activeMemTable;        // Currently accepting writes
    private volatile MemTable flushingMemTable;   // Being flushed to disk
    private final List<SSTable> level0Tables;     // Recently flushed, unsorted
    private final List<SSTable> level1Tables;     // Compacted, sorted, larger
    
    private static final long MEMTABLE_SIZE_LIMIT = 1_073_741_824L; // 1 GB
    
    /** Write: add a posting to the current MemTable */
    public void addPosting(String term, Posting posting) {
        activeMemTable.put(term, posting);
        
        if (activeMemTable.estimatedSize() > MEMTABLE_SIZE_LIMIT) {
            flushMemTable();
        }
    }
    
    /** Flush MemTable to disk as an immutable SSTable */
    private synchronized void flushMemTable() {
        flushingMemTable = activeMemTable;
        activeMemTable = new MemTable(); // New empty MemTable
        
        CompletableFuture.runAsync(() -> {
            SSTable table = SSTableWriter.write(flushingMemTable, generateTableId());
            synchronized (level0Tables) {
                level0Tables.add(table);
            }
            flushingMemTable = null;
            
            // Trigger compaction if too many L0 tables
            if (level0Tables.size() > 10) {
                triggerCompaction();
            }
        });
    }
    
    /** Read: look up a term across all levels */
    public PostingList lookup(String term) {
        PostingList merged = new PostingList(term);
        
        // Level 0: MemTable (in-memory, newest data)
        PostingList memResult = activeMemTable.get(term);
        if (memResult != null) merged.mergeFrom(memResult);
        
        if (flushingMemTable != null) {
            PostingList flushResult = flushingMemTable.get(term);
            if (flushResult != null) merged.mergeFrom(flushResult);
        }
        
        // Level 1: Recent SSTables on SSD
        for (SSTable table : level0Tables) {
            // Bloom filter check: skip table if term definitely not present
            if (!table.getBloomFilter().mightContain(term)) continue;
            
            PostingList diskResult = table.lookup(term);
            if (diskResult != null) merged.mergeFrom(diskResult);
        }
        
        // Level 2: Compacted SSTables
        for (SSTable table : level1Tables) {
            if (!table.getBloomFilter().mightContain(term)) continue;
            PostingList diskResult = table.lookup(term);
            if (diskResult != null) merged.mergeFrom(diskResult);
        }
        
        return merged;
    }
    
    /** Compaction: merge multiple small SSTables into one large sorted SSTable */
    private void triggerCompaction() {
        CompletableFuture.runAsync(() -> {
            List<SSTable> toCompact;
            synchronized (level0Tables) {
                toCompact = new ArrayList<>(level0Tables);
                level0Tables.clear();
            }
            
            SSTable compacted = SSTableCompactor.compact(toCompact);
            level1Tables.add(compacted);
            
            // Delete old tables
            for (SSTable old : toCompact) {
                old.delete();
            }
            
            log.info("Compacted {} L0 tables into 1 L1 table", toCompact.size());
        });
    }
}
```

**LSM-Tree Visualization**:
```
Write Path:
  New posting → MemTable (RAM, ~1 GB)
                    ↓ (full)
               SSTable L0 (SSD, immutable)
                    ↓ (10+ tables)
               Compaction: merge L0 → L1
                    ↓
               SSTable L1 (SSD, larger, sorted)
                    ↓ (old)
               SSTable L2 (HDD/S3, compressed)

Read Path:
  Query "birthday"
    → Check MemTable (< 1ms)
    → Check L0 SSTables with Bloom filter (< 10ms)
    → Check L1 SSTables with Bloom filter (< 50ms)
    → Merge results from all levels
    → Total: < 100ms for warm data

Bloom Filter: 
  Each SSTable has a Bloom filter (~10 MB)
  False positive rate: 1%
  99% of SSTables skipped on each lookup → massive I/O savings
```

---

### Deep Dive 6: Index Sharding — Distributing Terms Across Nodes

**The Problem**: 30 TB of index data can't fit on one machine. We need to shard the inverted index across 100+ nodes.

```java
public class IndexShardRouter {

    /**
     * Term-based sharding: each term's posting list lives on a specific shard.
     * 
     * Why term-based (not document-based)?
     *   - A search for "birthday" goes to ONE shard (contains entire posting list)
     *   - Document-based would require scatter-gather to ALL shards
     *   - Term-based = fewer network hops per query
     * 
     * Trade-off: indexing a post touches MULTIPLE shards (one per unique term)
     *   - A 50-word post → ~30 unique terms → 30 shard writes
     *   - But writes are async (via Kafka), so latency is acceptable
     */
    
    private static final int SHARD_COUNT = 128;
    private final ConsistentHashRing<IndexNode> hashRing;
    
    public int getShardForTerm(String term) {
        return Math.abs(term.hashCode()) % SHARD_COUNT;
    }
    
    public IndexNode getNodeForTerm(String term) {
        return hashRing.getNode(term);
    }
    
    /**
     * Search query: route each query term to its shard, intersect results.
     */
    public List<SearchResult> search(List<String> queryTerms, int limit) {
        // Step 1: Fetch posting lists from respective shards (parallel)
        Map<String, CompletableFuture<PostingList>> futures = new HashMap<>();
        for (String term : queryTerms) {
            IndexNode node = getNodeForTerm(term);
            futures.put(term, CompletableFuture.supplyAsync(() -> 
                node.getPostingList(term)));
        }
        
        // Step 2: Wait for all posting lists
        Map<String, PostingList> postingLists = new HashMap<>();
        for (var entry : futures.entrySet()) {
            postingLists.put(entry.getKey(), entry.getValue().join());
        }
        
        // Step 3: Intersect posting lists (on query service node)
        List<ScoredPosting> matched = intersector.intersectMultiple(
            new ArrayList<>(postingLists.values()));
        
        // Step 4: Score and rank
        matched.sort(Comparator.comparingDouble(ScoredPosting::getScore).reversed());
        
        return matched.stream().limit(limit)
            .map(this::toSearchResult)
            .toList();
    }
}
```

**Sharding Diagram**:
```
Query: "birthday party"
  │
  ├─ "birthday" → hash("birthday") % 128 = shard 42 → Node 7
  │                  ↓
  │              PostingList: [post_001, post_042, post_789, ...]
  │
  └─ "party" → hash("party") % 128 = shard 91 → Node 15
                     ↓
                 PostingList: [post_001, post_234, post_789, ...]
  
Query Service (any node):
  1. Fetch "birthday" from shard 42 (parallel)
  2. Fetch "party" from shard 91 (parallel)
  3. Intersect locally: [post_001, post_789]
  4. Score, rank, return top 20
```

---

### Deep Dive 7: Hot/Cold Index Tiering

```java
public class TieredQueryExecutor {
    
    /**
     * Execute search across hot/warm/cold tiers.
     * Hot tier answers 80% of queries (recent posts).
     * Cold tier only queried if hot+warm have insufficient results.
     */
    public SearchResponse search(SearchRequest request) {
        List<SearchResult> results = new ArrayList<>();
        
        // Tier 1: Hot index (in-memory, last 72 hours)
        results.addAll(hotIndex.search(request.getQueryTerms(), request.getLimit()));
        
        if (results.size() >= request.getLimit()) {
            return SearchResponse.of(results, "HOT");
        }
        
        // Tier 2: Warm index (SSD, last 30 days)
        int remaining = request.getLimit() - results.size();
        results.addAll(warmIndex.search(request.getQueryTerms(), remaining));
        
        if (results.size() >= request.getLimit() || !request.isIncludeCold()) {
            return SearchResponse.of(results, "WARM");
        }
        
        // Tier 3: Cold index (HDD/S3, all history) — only if explicitly requested
        remaining = request.getLimit() - results.size();
        results.addAll(coldIndex.search(request.getQueryTerms(), remaining));
        
        return SearchResponse.of(results, "COLD");
    }
}
```

**Tier Characteristics**:
```
┌──────────┬───────────────┬──────────────┬───────────┬────────────┐
│ Tier     │ Storage       │ Data Range   │ Latency   │ Size       │
├──────────┼───────────────┼──────────────┼───────────┼────────────┤
│ Hot      │ RAM + WAL     │ Last 72h     │ < 10ms    │ ~500 GB    │
│ Warm     │ SSD (SSTable) │ Last 30 days │ < 100ms   │ ~5 TB      │
│ Cold     │ HDD / S3      │ All time     │ < 2s      │ ~30 TB     │
└──────────┴───────────────┴──────────────┴───────────┴────────────┘

80% of queries satisfied by Hot tier alone
18% need Hot + Warm
2% need all three tiers (searching for old posts)
```

---

### Deep Dive 8: Posting List Compression — Variable-Byte Encoding

**The Problem**: Uncompressed posting lists = 120 TB. With compression, we can reduce to ~30 TB and also speed up I/O (less data to read from disk).

```java
public class VarByteEncoder {

    /**
     * Variable-byte encoding for posting list compression.
     * 
     * Posting lists store docIds sorted in ascending order.
     * Instead of storing absolute docIds, we store GAPS (deltas).
     * Gaps are typically small numbers → compress well with variable-byte.
     * 
     * Example:
     *   DocIds:  [1001, 1005, 1009, 1042, 1100]
     *   Gaps:    [1001,    4,    4,   33,   58]
     *   VarByte: [2 bytes, 1 byte, 1 byte, 1 byte, 1 byte] = 6 bytes
     *   vs raw:  [8, 8, 8, 8, 8] = 40 bytes
     *   Compression: 6.7x
     */
    
    public byte[] encode(List<Long> sortedDocIds) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long prev = 0;
        
        for (long docId : sortedDocIds) {
            long gap = docId - prev;
            encodeVarByte(gap, out);
            prev = docId;
        }
        
        return out.toByteArray();
    }
    
    public List<Long> decode(byte[] encoded) {
        List<Long> docIds = new ArrayList<>();
        long prev = 0;
        int pos = 0;
        
        while (pos < encoded.length) {
            long gap = 0;
            byte b;
            do {
                b = encoded[pos++];
                gap = (gap << 7) | (b & 0x7F);
            } while ((b & 0x80) == 0); // MSB = 1 means last byte
            
            long docId = prev + gap;
            docIds.add(docId);
            prev = docId;
        }
        
        return docIds;
    }
    
    private void encodeVarByte(long value, ByteArrayOutputStream out) {
        List<Byte> bytes = new ArrayList<>();
        while (value >= 128) {
            bytes.add((byte) (value & 0x7F));
            value >>= 7;
        }
        bytes.add((byte) (value | 0x80)); // Set MSB on last byte
        
        // Write in reverse (big-endian VarByte)
        for (int i = bytes.size() - 1; i >= 0; i--) {
            out.write(bytes.get(i));
        }
    }
}
```

**Compression Ratios**:
```
Encoding scheme comparison (for 1M posting list):

Raw (8 bytes/docId):          8 MB
VarByte (gap encoding):       1.2 MB (6.7x compression)
PForDelta (block encoding):   0.8 MB (10x compression)
Simple-9 (bit packing):       0.9 MB (8.9x compression)

We use VarByte for simplicity + good compression.
PForDelta would be better for very large lists but adds complexity.

Total index: 120 TB raw → ~30 TB with VarByte → ~18 TB with PForDelta
```

---

### Deep Dive 9: Index Replication & Fault Tolerance

```java
public class IndexReplicationManager {
    
    /**
     * Each index shard is replicated 3x for fault tolerance.
     * 1 primary (accepts writes) + 2 replicas (serve reads).
     * 
     * Replication via Kafka: indexer publishes postings to Kafka,
     * all replicas consume and build identical indexes.
     */
    
    private static final int REPLICATION_FACTOR = 3;
    
    public void replicatePosting(String term, Posting posting) {
        int shardId = getShardForTerm(term);
        String kafkaTopic = "index-shard-" + shardId;
        
        // All replicas of this shard consume from the same Kafka partition
        kafka.send(new ProducerRecord<>(kafkaTopic, term,
            IndexUpdate.builder().term(term).posting(posting).build()));
    }
    
    /** Health check: detect and replace failed replicas */
    @Scheduled(fixedRate = 10_000)
    public void checkReplicaHealth() {
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            List<IndexNode> replicas = getReplicasForShard(shard);
            for (IndexNode replica : replicas) {
                if (!replica.isHealthy()) {
                    log.warn("Replica {} for shard {} is unhealthy, replacing", replica.getId(), shard);
                    replaceReplica(shard, replica);
                }
            }
        }
    }
}
```

---

### Deep Dive 10: Phrase Search — Positional Index

**The Problem**: User searches for "happy birthday" as an exact phrase. We need to find posts where "happy" appears immediately before "birthday" (at consecutive positions).

```java
public class PhraseSearcher {

    /**
     * Phrase search using positional information in postings.
     * 
     * For "happy birthday":
     *   1. Find posts containing both "happy" AND "birthday"
     *   2. For each matching post, check if positions are consecutive
     *      e.g., "happy" at position 3, "birthday" at position 4 → MATCH
     */
    public List<Long> phraseSearch(String phrase) {
        String[] terms = phrase.toLowerCase().split("\\s+");
        if (terms.length < 2) return singleTermSearch(terms[0]);
        
        // Get posting lists for all terms
        List<PostingList> lists = Arrays.stream(terms)
            .map(index::getPostingList)
            .toList();
        
        // Intersect to find posts containing ALL terms
        List<ScoredPosting> candidates = intersector.intersectMultiple(lists);
        
        // Filter: check positional adjacency
        List<Long> phraseMatches = new ArrayList<>();
        
        for (ScoredPosting candidate : candidates) {
            if (checkPositionalMatch(candidate, terms)) {
                phraseMatches.add(candidate.getPostId());
            }
        }
        
        return phraseMatches;
    }
    
    /**
     * Check if terms appear at consecutive positions in the document.
     */
    private boolean checkPositionalMatch(ScoredPosting posting, String[] terms) {
        // Get positions for first term
        List<Integer> firstPositions = posting.getPositions(terms[0]);
        
        for (int startPos : firstPositions) {
            boolean allMatch = true;
            for (int i = 1; i < terms.length; i++) {
                List<Integer> termPositions = posting.getPositions(terms[i]);
                if (!termPositions.contains(startPos + i)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        
        return false;
    }
}
```

---

### Deep Dive 11: Observability & Metrics

```java
public class SearchMetrics {
    Counter searchQueries     = Counter.builder("search.queries.total").register(registry);
    Timer   searchLatency     = Timer.builder("search.query.latency_ms").register(registry);
    Timer   indexLatency      = Timer.builder("search.index.latency_ms").register(registry);
    Gauge   hotIndexSize      = Gauge.builder("search.index.hot.size_bytes", ...).register(registry);
    Gauge   warmIndexSize     = Gauge.builder("search.index.warm.size_bytes", ...).register(registry);
    Counter indexedPosts      = Counter.builder("search.posts.indexed").register(registry);
    Counter cacheHits         = Counter.builder("search.cache.hits").register(registry);
    Gauge   bloomFilterFPRate = Gauge.builder("search.bloom.fp_rate", ...).register(registry);
    Counter tierHotHits       = Counter.builder("search.tier.hit").tag("tier", "hot").register(registry);
    Counter tierWarmHits      = Counter.builder("search.tier.hit").tag("tier", "warm").register(registry);
    Counter tierColdHits      = Counter.builder("search.tier.hit").tag("tier", "cold").register(registry);
}
```

---

### Deep Dive 12: Query Result Caching

```java
public class SearchResultCache {

    /**
     * Cache popular search queries and their results.
     * 
     * Key: normalized query string + sort order + page
     * Value: list of post_ids (not full results — post content fetched separately)
     * TTL: 30 seconds (results are relatively fresh)
     * 
     * Cache hit rate: ~30-40% (many users search for the same trending topics)
     */
    
    private final LoadingCache<String, List<Long>> cache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterWrite(Duration.ofSeconds(30))
        .build();
    
    public List<Long> getCachedOrSearch(String query, SortOrder sort, int page) {
        String cacheKey = query.toLowerCase().strip() + ":" + sort + ":" + page;
        
        List<Long> cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            metrics.counter("search.cache.hits").increment();
            return cached;
        }
        
        // Cache miss: execute search
        List<Long> results = searchService.executeSearch(query, sort, page);
        cache.put(cacheKey, results);
        metrics.counter("search.cache.misses").increment();
        
        return results;
    }
}
```

---

---

### Deep Dive 13: Stop Word Handling & High-Frequency Term Optimization

**The Problem**: Terms like "the", "is", "and" appear in nearly every document. Their posting lists are enormous (billions of entries). Including them in intersection would be wasteful and slow. But sometimes stop words ARE meaningful — "The Who" (band), "To Be or Not To Be" (quote).

```java
public class StopWordOptimizer {

    /**
     * Tiered stop word handling:
     * 
     * Tier 1: Always remove (ultra-common, never useful alone):
     *   "a", "an", "the", "is", "are", "was", "were", "be"
     * 
     * Tier 2: Remove from AND queries, keep in phrase queries:
     *   "to", "in", "on", "at", "for", "of", "with", "by"
     *   Example: phrase "to be or not to be" → keep all words
     *   Example: AND query "to london" → remove "to", search "london"
     * 
     * Tier 3: Frequency-based dynamic stop words:
     *   If a term appears in > 30% of all documents, treat as stop word
     *   This catches terms like "http", "www", "com" that aren't in standard lists
     */
    
    private static final Set<String> TIER1_ALWAYS_REMOVE = Set.of(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "am",
        "do", "did", "does", "have", "has", "had", "i", "me", "my"
    );
    
    private static final Set<String> TIER2_CONTEXT_DEPENDENT = Set.of(
        "to", "in", "on", "at", "for", "of", "with", "by", "from",
        "and", "or", "but", "not", "no", "so", "if", "as"
    );
    
    private static final double DYNAMIC_STOP_THRESHOLD = 0.30; // 30% of all docs
    
    /**
     * Filter stop words based on query type.
     */
    public List<String> filterForQuery(List<String> terms, QueryType queryType) {
        List<String> filtered = new ArrayList<>();
        
        for (String term : terms) {
            // Tier 1: always remove
            if (TIER1_ALWAYS_REMOVE.contains(term)) continue;
            
            // Tier 2: remove for AND queries, keep for phrase queries
            if (TIER2_CONTEXT_DEPENDENT.contains(term)) {
                if (queryType == QueryType.PHRASE) {
                    filtered.add(term); // Keep for phrase matching
                }
                continue; // Skip for AND queries
            }
            
            // Tier 3: dynamic frequency-based check
            if (isDynamicStopWord(term)) {
                if (queryType == QueryType.PHRASE) {
                    filtered.add(term);
                }
                continue;
            }
            
            filtered.add(term);
        }
        
        // Safety: if ALL terms were removed, fall back to the most specific term
        if (filtered.isEmpty() && !terms.isEmpty()) {
            String rarestTerm = terms.stream()
                .min(Comparator.comparingLong(t -> index.getDocumentFrequency(t)))
                .orElse(terms.get(0));
            filtered.add(rarestTerm);
        }
        
        return filtered;
    }
    
    /** Check if a term is a dynamic stop word (appears in > 30% of docs) */
    private boolean isDynamicStopWord(String term) {
        long df = index.getDocumentFrequency(term);
        long total = index.getTotalDocuments();
        return total > 0 && (double) df / total > DYNAMIC_STOP_THRESHOLD;
    }
    
    /**
     * For high-frequency terms that we must include (phrase search),
     * use a "frequency-ordered" posting list intersection optimization.
     * 
     * Instead of full intersection, take top-N from the rare term's list
     * and only check those against the common term's list.
     */
    public List<ScoredPosting> optimizedPhraseIntersect(PostingList rareTermList,
                                                          PostingList commonTermList,
                                                          int maxCandidates) {
        List<ScoredPosting> results = new ArrayList<>();
        
        // Only check first maxCandidates from rare term
        int checked = 0;
        for (Posting rarePosting : rareTermList.getPostings()) {
            if (checked >= maxCandidates) break;
            
            // Binary search in common term's list (O(log N) instead of scanning)
            Posting commonPosting = commonTermList.binarySearch(rarePosting.getPostId());
            if (commonPosting != null) {
                results.add(new ScoredPosting(rarePosting.getPostId(), rarePosting, commonPosting));
            }
            checked++;
        }
        
        return results;
    }
}
```

**Stop Word Impact**:
```
Without stop word optimization:
  Query: "the birthday party"
  "the" posting list: 95 BILLION postings (95% of all docs)
  "birthday" posting list: 1M postings
  "party" posting list: 2M postings
  Intersection: must scan 95B entries → IMPOSSIBLE in < 500ms

With stop word optimization:
  Remove "the" (Tier 1 always-remove)
  Intersect "birthday" (1M) ∩ "party" (2M) → O(3M) → 30ms ✓

For phrase "to be or not to be":
  Keep all words (phrase mode)
  Start with rarest: "be" (only 100M postings, not 95B like "the")
  Check top 10K candidates from "be" against other terms
  Total: < 200ms ✓
```

---

### Deep Dive 14: Index Compaction & Garbage Collection

**The Problem**: Posts get deleted or edited. The inverted index still contains their postings, wasting space and returning stale results. We need periodic garbage collection without rebuilding the entire index.

```java
public class IndexCompactionService {

    /**
     * Index maintenance: handle deleted/edited posts and reclaim space.
     * 
     * Three operations:
     *   1. Tombstone: mark a posting as deleted (fast, lazy)
     *   2. Compaction: merge SSTables and physically remove tombstoned postings
     *   3. Re-indexing: for edited posts, delete old postings + add new ones
     */
    
    // Tombstone set: postIds that have been deleted but not yet compacted out
    private final ConcurrentHashMap<Long, Instant> tombstones = new ConcurrentHashMap<>();
    
    /** Mark a post as deleted (fast — just add to tombstone set) */
    public void deletePost(long postId) {
        tombstones.put(postId, Instant.now());
        
        // Also delete from hot index immediately
        hotIndex.removePost(postId);
        
        // For warm/cold indexes: tombstone will be applied during compaction
        metrics.counter("index.tombstone.added").increment();
    }
    
    /** Handle post edit: delete old postings, index new content */
    public void updatePost(long postId, String oldContent, String newContent) {
        // Find which terms changed
        Set<String> oldTerms = new HashSet<>(tokenizer.tokenize(oldContent));
        Set<String> newTerms = new HashSet<>(tokenizer.tokenize(newContent));
        
        Set<String> removedTerms = new HashSet<>(oldTerms);
        removedTerms.removeAll(newTerms);
        
        Set<String> addedTerms = new HashSet<>(newTerms);
        addedTerms.removeAll(oldTerms);
        
        // Remove postings for removed terms
        for (String term : removedTerms) {
            hotIndex.removePosting(term, postId);
        }
        
        // Add postings for new terms
        Map<String, List<Integer>> newPositions = tokenizer.tokenizeWithPositions(newContent);
        for (String term : addedTerms) {
            List<Integer> positions = newPositions.get(term);
            if (positions != null) {
                float tf = (float) positions.size() / newTerms.size();
                hotIndex.addPosting(term, new Posting(postId, positions, Instant.now(), tf));
            }
        }
        
        metrics.counter("index.posts.updated").increment();
    }
    
    /**
     * Compaction: merge SSTables while filtering out tombstoned postings.
     * Runs periodically during low-traffic hours.
     */
    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    public void runCompaction() {
        log.info("Starting index compaction. {} tombstones pending", tombstones.size());
        
        long removedPostings = 0;
        long totalPostings = 0;
        
        for (SSTable table : warmIndex.getAllTables()) {
            SSTableWriter writer = new SSTableWriter(table.getId() + "_compacted");
            
            for (Map.Entry<String, PostingList> entry : table.iterator()) {
                String term = entry.getKey();
                PostingList originalList = entry.getValue();
                
                // Filter out tombstoned postings
                PostingList filteredList = new PostingList(term);
                for (Posting posting : originalList.getPostings()) {
                    totalPostings++;
                    if (tombstones.containsKey(posting.getPostId())) {
                        removedPostings++;
                        continue; // Skip deleted post
                    }
                    filteredList.addPosting(posting);
                }
                
                if (!filteredList.isEmpty()) {
                    writer.write(term, filteredList);
                }
            }
            
            writer.finish();
            
            // Atomic swap: replace old table with compacted table
            warmIndex.replaceTable(table, writer.getSSTable());
        }
        
        // Clear applied tombstones (older than 24h, already compacted)
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        tombstones.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        
        log.info("Compaction complete. Removed {}/{} postings ({:.1f}%)", 
            removedPostings, totalPostings, 
            100.0 * removedPostings / Math.max(totalPostings, 1));
        
        metrics.counter("index.compaction.postings_removed").increment(removedPostings);
    }
    
    /**
     * Query-time tombstone filtering: when reading from warm/cold index,
     * filter out any postings that are in the tombstone set.
     */
    public PostingList filterTombstones(PostingList rawList) {
        if (tombstones.isEmpty()) return rawList;
        
        PostingList filtered = new PostingList(rawList.getTerm());
        for (Posting posting : rawList.getPostings()) {
            if (!tombstones.containsKey(posting.getPostId())) {
                filtered.addPosting(posting);
            }
        }
        return filtered;
    }
}
```

**Compaction Strategy**:
```
Writes:
  New post → add postings to hot index (in-memory)
  Deleted post → add to tombstone set (instant) + remove from hot index
  Edited post → diff old/new terms, update hot index

Compaction (daily at 3 AM):
  For each warm SSTable:
    1. Read all postings
    2. Skip postings with tombstoned docIds
    3. Write surviving postings to new SSTable
    4. Atomic swap old → new table
    5. Delete old SSTable file

Impact:
  ~1% of posts deleted per day
  ~0.5% edited per day
  Compaction removes ~1.5% of postings per day
  Keeps index lean and results fresh

Query-time safety:
  Between compaction runs, tombstone set is checked at query time
  O(1) hash lookup per posting → negligible overhead for small tombstone sets
  Tombstone set typically < 10M entries (5 MB in memory)
```

---

## 📐 Index Sizing Deep Dive

### Memory & Storage Breakdown by Tier

```
HOT INDEX (In-Memory):
  Posts covered: last 72 hours = 1.5B posts
  Terms per post: 30 (after stop word removal)
  Total postings: 1.5B × 30 = 45B postings
  Per posting: 24 bytes (docId + positions + metadata)
  Raw size: 45B × 24 = 1.08 TB
  With overhead (HashMap, object headers): ~1.5 TB
  Distributed: 50 nodes × 30 GB each
  
  Term dictionary: ~5M unique terms in 72h window
  Per term entry: 32 bytes (term hash + pointer)
  Dictionary size: 5M × 32 = 160 MB per node (trivial)

WARM INDEX (SSD SSTable):
  Posts covered: 72h to 30 days = ~13.5B posts
  Total postings: 13.5B × 30 = 405B postings
  Compressed (VarByte): 405B × 3.5 bytes avg = ~1.4 TB
  Bloom filters: one per SSTable, ~10 MB each × 100 SSTables = 1 GB
  Distributed: 100 nodes × 14 GB each
  
  SSTable format:
    [Bloom Filter | Term Dictionary | Posting Lists (compressed)]
    Bloom filter: 10 MB, 1% FPR
    Term dictionary: B-tree index, ~50 MB per SSTable
    Posting lists: variable-length, VarByte encoded

COLD INDEX (S3 + Local Cache):
  Posts covered: 30 days to all time = ~86.5B posts
  Total postings: 86.5B × 30 = 2.6T postings
  Compressed: 2.6T × 3.5 bytes = ~9.1 TB
  Stored as: Parquet-like columnar files on S3
  Local SSD cache: 2 TB per node (LRU, caches frequently accessed terms)
  
  Access pattern:
    Term "birthday" in cold → likely cached (popular term)
    Term "xyzabc123" in cold → cache miss → S3 fetch (~200ms)
    Average cold query: ~500ms (cache hit) to ~2s (cache miss)
```

### Term Frequency Distribution (Zipf's Law)

```
Zipf's law: the frequency of a term is inversely proportional to its rank.

Rank 1:    "the"      → 95B postings (in 95% of all docs)
Rank 10:   "people"   → 20B postings
Rank 100:  "birthday" → 1B postings
Rank 1000: "astronaut"→ 50M postings
Rank 10000:"zucchini" → 500K postings
Rank 100K: "supercalifragilistic" → 5K postings

Implication for search:
  - Top 100 terms make up ~50% of all postings → handle as stop words
  - Middle 10K terms handle ~40% → main search terms, well-cached
  - Tail 10B terms handle ~10% → rare queries, often cold index
  
  - 80% of search queries contain terms ranked 100-10,000 (sweet spot)
  - These terms have posting lists of 1M-1B entries → manageable
```

---

## 🔄 Post Edit & Delete Consistency

### Consistency Model for Index Updates

```
Scenario: User edits a post (changes "birthday party" to "wedding celebration")

Timeline:
  T=0:   User saves edit
  T=10ms: Post DB updated (source of truth)
  T=50ms: Kafka event "post-updated" published
  T=200ms: Indexer receives event
  T=300ms: Hot index updated (remove "birthday"+"party", add "wedding"+"celebration")
  T=500ms: New content searchable ✓
  
  Warm/Cold indexes:
    Still contain "birthday party" postings for this post
    Query-time tombstone filtering removes stale results
    Next compaction (3 AM) physically removes stale postings

Guarantee:
  - Hot index is always up-to-date (< 1 second)
  - Warm/cold may return stale results until tombstone check filters them
  - No window where BOTH old and new content match (atomic update in hot index)
```

### Delete Propagation

```
Scenario: User deletes a post

T=0:     User clicks "Delete"
T=10ms:  Post DB marks post as deleted
T=50ms:  Kafka event "post-deleted" published
T=200ms: Indexer adds postId to tombstone set
T=200ms: Hot index: all postings for this post removed immediately
T=200ms: Search for this post's content → no results ✓

Warm/Cold:
  Postings still exist on disk
  Query-time: tombstone check filters them out
  Daily compaction: physically removes from disk

Memory for tombstones:
  5M deletes/day × 8 bytes per postId = 40 MB/day
  Cleared after 24h (post-compaction)
  Max tombstone set: ~40 MB → trivial
```

---

## 🏗️ Operational Considerations

### Index Rebuild Strategy

```
Scenario: Index corruption, need to rebuild from scratch

Option 1: Full rebuild from Post DB
  - Read all 100B posts from PostgreSQL
  - Tokenize and build index from scratch
  - Duration: ~48 hours at 500K posts/sec
  - Problem: 48 hours of no search → unacceptable

Option 2: Incremental rebuild from Kafka
  - Kafka retains 7 days of events
  - Rebuild last 7 days from Kafka replay → covers hot + warm
  - Cold index: restore from S3 backup (snapshots taken weekly)
  - Duration: ~4 hours for hot/warm, ~2 hours for cold restore
  - Search degraded but functional within 4 hours

Option 3: Replica promotion
  - Each shard has 3 replicas
  - If 1 replica is corrupted, the other 2 are fine
  - Promote healthy replica to primary
  - Rebuild corrupted replica from Kafka
  - Duration: zero downtime (handled automatically)
```

### Capacity Planning

```
Growth projections (next 2 years):
  Posts: 100B → 200B (2x)
  Search QPS: 50K → 100K (2x)
  Index size: 30 TB → 60 TB
  
Scaling plan:
  Hot index: 50 nodes → 100 nodes (add nodes, rebalance shards)
  Warm index: 100 nodes → 200 nodes
  Cold index: S3 (unlimited, just add more data)
  Query service: 50 pods → 100 pods (stateless, horizontal)
  
Cost scaling:
  Current: ~$150K/month
  At 2x: ~$280K/month (not 2x due to efficiency improvements)
  Per-query cost: ~$0.003 → ~$0.003 (stays constant due to caching)
## 📊 Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Index type** | Inverted index (custom-built) | No ES allowed; inverted index is the standard for full-text search |
| **Intersection** | Sorted merge + skip pointers | O(n+m) optimal; skip pointers for long lists |
| **Scoring** | BM25 + recency + engagement | Industry standard; recency boost for social media |
| **Storage** | LSM-tree (MemTable → SSTable) | Optimized for write-heavy workload (500M posts/day) |
| **Sharding** | Term-based (hash of term) | Query touches 1 shard per term (not scatter-gather) |
| **Tiering** | Hot (RAM) / Warm (SSD) / Cold (S3) | 80% queries answered from hot tier |
| **Compression** | Variable-byte gap encoding | 6.7x compression; simple and fast |
| **Replication** | 3x via Kafka consumers | Fault tolerant; replicas serve reads |
| **Phrase search** | Positional index | Store word positions; check adjacency |
| **Caching** | Query result cache (30s TTL) | 30-40% hit rate for trending queries |

## 🎯 Interview Talking Points

1. **"Inverted index is the foundation"** — term → sorted posting list. This is what ES does under the hood.
2. **"Sorted merge intersection is O(n+m)"** — Start with shortest list; skip pointers for long lists.
3. **"BM25 is the scoring standard"** — TF saturation (k1), length normalization (b), IDF for rare terms.
4. **"LSM-tree for write-heavy indexing"** — MemTable → flush to SSTable → compact. Same as Cassandra/RocksDB.
5. **"Term-based sharding"** — Query "birthday" goes to ONE shard. Document-based would scatter to ALL shards.
6. **"Hot/Warm/Cold tiering"** — RAM for 72h, SSD for 30d, S3 for all time. 80% queries served from RAM.
7. **"VarByte compression saves 6.7x"** — Store gaps between docIds, not absolute values. Small gaps = small bytes.
8. **"Bloom filters skip irrelevant SSTables"** — 99% of disk reads avoided. 1% false positive is acceptable.
9. **"Positional index enables phrase search"** — Store word positions; check consecutive positions for phrases.
10. **"Kafka-based replication"** — All replicas consume same topic. Identical indexes built independently.
11. **"30-second query cache"** — Trending searches hit cache. 30% fewer index lookups.
12. **"WAL before MemTable"** — Durability: if node crashes, replay WAL to recover in-memory index.

---

**References**:
- Introduction to Information Retrieval (Manning, Raghavan, Schutze)
- BM25 Scoring Function (Robertson & Zaragoza, 2009)
- LSM-Tree (O'Neil et al., 1996)
- Variable-Byte Encoding for Inverted Indexes
- Google Web Search Architecture
- Facebook Search Infrastructure

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 14 topics (choose 2-3 based on interviewer interest)