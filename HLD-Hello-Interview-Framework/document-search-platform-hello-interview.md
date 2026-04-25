# Design a Document Search Platform (Google Drive/Confluence/Notion) — Hello Interview Framework

> **Question**: Design an enterprise document search platform like Google Drive Search, Confluence Search, or Notion Search. Users can search across documents, spreadsheets, presentations, and wikis by content, title, collaborators, and metadata. Support ACL-aware search (users only see documents they have access to), real-time indexing, and relevance ranking.
>
> **Asked at**: Google, Microsoft (SharePoint), Atlassian, Notion, Dropbox, Box, Amazon (internal search)
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

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
1. **Full-Text Search**: Search across document content (body text), titles, comments, and metadata. Support exact phrase search, boolean operators (AND/OR/NOT), and wildcard search.
2. **ACL-Aware Results**: Users only see documents they have access to (owner, editor, viewer, or shared via link/group). This is the #1 differentiator from web search.
3. **Multi-Format Support**: Index content from Google Docs, Sheets, Slides, PDFs, Word, Excel, PowerPoint, plain text, Markdown, code files, images (OCR).
4. **Filtering**: By file type, owner, last modified date, shared with me, starred, folder location, team/workspace.
5. **Ranking**: Relevance + recency + personal signals (recently opened, frequently accessed, owned by me, shared with my team).
6. **Real-Time Indexing**: Document edits should be searchable within 30 seconds (collaborative editing).

#### Nice to Have (P1)
- Search within images (OCR) and scanned PDFs
- AI-powered semantic search ("find the Q3 budget proposal" without exact title)
- Search suggestions / auto-complete for document names
- Highlighted search snippets showing matching passages
- "People also opened" related document suggestions
- Cross-workspace search (search multiple Confluence spaces at once)
- Search history and saved searches

#### Below the Line (Out of Scope)
- Document creation/editing
- Sharing/permission management (separate service)
- Version control / revision history
- Collaborative editing (separate service)
- Email search (different domain)

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Search Latency** | P50 < 200ms, P95 < 500ms, P99 < 1s | Interactive search in productivity apps |
| **Indexing Freshness** | < 30 seconds for edits; < 5 min for new docs | Real-time collaboration requires fresh search |
| **Throughput** | 10K QPS (peak 50K during working hours) | Enterprise-scale, bursty during 9-5 |
| **Index Size** | 10B documents, 100M active users | Google Drive alone has billions of files |
| **Availability** | 99.99% | Search is used constantly during work hours |
| **ACL Enforcement** | 100% — never show unauthorized documents | Security/compliance is non-negotiable |
| **Consistency** | Strong for ACL changes; eventual for content | Permission revoke must be immediate |
| **Scale** | 1 PB indexed content | Rich documents with images, tables, etc. |

### Capacity Estimation

```
Users:
  Total: 100M active users
  Concurrent during peak: 10M
  Searches/day: 500M (5 searches/user/day)
  QPS: 500M / 86400 ≈ 5,800 avg → 50K peak (9-11am)

Documents:
  Total: 10B documents
  Active (accessed in last year): 2B
  New documents/day: 50M
  Edits/day: 500M (real-time collaborative edits)
  Average document size: 50KB (text extracted)
  
Storage:
  Document content index: 2B active × 50KB = 100 TB (compressed: 25 TB)
  Full index (all 10B): 500 TB (compressed: 125 TB)
  ACL index: 10B docs × 10 ACL entries avg × 50B = 5 TB
  Metadata index: 10B × 200B = 2 TB
  
  Hot (active docs): 25 TB indexed (SSD)
  Warm (1-year old): 100 TB indexed (HDD)
  Cold (older): raw content in S3, not indexed

ACL Complexity:
  Average ACL per document: 10 entries (individual users + groups)
  Group memberships: 100M users × avg 20 groups = 2B group memberships
  ACL check per search result: must validate for each of top-K results

Infrastructure:
  Search API servers: 20
  Elasticsearch data nodes: 100 (hot) + 200 (warm)
  ACL cache: Redis cluster (50 nodes)
  Kafka brokers: 20
  Content extraction workers: 100
```

---

## 2️⃣ Core Entities

### Entity 1: Document (Search Index Record)
```java
public class DocumentIndex {
    String docId;                // UUID
    String title;                // "Q3 2026 Budget Proposal"
    String content;              // Full extracted text content
    String contentSnippets;      // Pre-computed snippet candidates
    DocType type;                // DOCUMENT, SPREADSHEET, PRESENTATION, PDF, IMAGE, CODE
    String mimeType;             // "application/vnd.google-apps.document"
    String ownerId;              // Creator user ID
    List<String> collaborators;  // Users who edited
    String workspaceId;          // "Engineering" workspace/space
    String folderId;             // Parent folder
    List<String> folderPath;     // ["My Drive", "Projects", "Q3 Planning"]
    Map<String, String> metadata;// {"department": "Engineering", "status": "Draft"}
    Instant createdAt;
    Instant lastModifiedAt;
    String lastModifiedBy;       // User who last edited
    long sizeBytes;              // Document size
    boolean isStarred;           // Per-user (handled separately)
    String thumbnailUrl;         // Preview image
    Instant indexedAt;           // When we last indexed this document
}
```

### Entity 2: AccessControlEntry
```java
public class AccessControlEntry {
    String docId;
    String principalId;          // User ID or Group ID
    PrincipalType principalType; // USER, GROUP, DOMAIN, ANYONE_WITH_LINK
    Permission permission;       // OWNER, EDITOR, VIEWER, COMMENTER
    Instant grantedAt;
    Instant expiresAt;           // Nullable (for time-limited sharing)
}

public enum PrincipalType { USER, GROUP, DOMAIN, ANYONE_WITH_LINK }
```

### Entity 3: SearchQuery
```java
public class SearchQuery {
    String queryText;            // "budget proposal Q3"
    String userId;               // Searcher's ID (required for ACL)
    DocType typeFilter;          // DOCUMENT, SPREADSHEET, etc.
    String ownerFilter;          // "user_xyz" (specific owner)
    Instant modifiedAfter;       // Last modified after this date
    Instant modifiedBefore;
    boolean sharedWithMe;        // Only show shared docs
    boolean ownedByMe;           // Only show my docs
    boolean starred;             // Only show starred docs
    String workspaceId;          // Limit to specific workspace
    String sortBy;               // "relevance" | "last_modified" | "title"
    int limit;                   // 20
    String pageToken;
}
```

---

## 3️⃣ API Design

### 1. Document Search
```
GET /api/v1/search?q=budget+proposal+Q3&type=document&modified_after=2026-01-01&sort=relevance&limit=20

Headers:
  Authorization: Bearer {jwt}    (REQUIRED — for ACL)

Response (200 OK):
{
  "results": [
    {
      "doc_id": "doc_abc123",
      "title": "Q3 2026 Budget Proposal - Engineering",
      "type": "document",
      "owner": {"id": "user_xyz", "name": "Sarah Chen", "avatar_url": "..."},
      "last_modified": "2026-04-20T14:30:00Z",
      "last_modified_by": "John Smith",
      "snippet": "...the proposed <b>budget</b> for <b>Q3</b> includes a 15% increase in cloud infrastructure spending...",
      "folder_path": ["Engineering", "Planning", "2026"],
      "thumbnail_url": "https://...",
      "permissions": "editor",
      "size_bytes": 245000,
      "starred": true
    }
    // ... more results
  ],
  "total_results": 47,
  "facets": {
    "type": [{"document": 32}, {"spreadsheet": 10}, {"presentation": 5}],
    "owner": [{"Sarah Chen": 12}, {"John Smith": 8}],
    "workspace": [{"Engineering": 30}, {"Finance": 17}]
  },
  "spell_suggestion": null,
  "took_ms": 145
}
```

### 2. Auto-Complete (Document Names)
```
GET /api/v1/search/suggest?q=budg&limit=5

Response (200 OK):
{
  "suggestions": [
    {"type": "document", "title": "Budget Proposal Q3", "doc_id": "doc_abc123"},
    {"type": "document", "title": "Budget Template 2026", "doc_id": "doc_def456"},
    {"type": "query", "text": "budget analysis"},
    {"type": "person", "name": "Budget Team", "id": "group_budget"}
  ]
}
```

---

## 4️⃣ Data Flow

### Document Indexing Flow
```
User edits document → Document Service (real-time)
  → Change event → Kafka (document_updates topic)
  → Content Extractor (extract text from format: Docs, PDF, Sheets, etc.)
  → ACL Resolver (fetch current permissions from Permission Service)
  → Search Indexer → Elasticsearch
      (index both content AND ACL entries for the document)
  → ACL Cache → Redis (update cached ACLs)
```

### Search Flow
```
User submits search → Search API
  → Query Parser (extract terms, filters, operators)
  → ACL Pre-Filter: get user's groups and accessible workspaces from cache
  → Elasticsearch Query:
      - Text search on content + title
      - ACL filter (user_id IN acl_list OR group_id IN acl_list OR public)
      - Apply metadata filters (type, date, owner)
  → Rank results (relevance + personal signals)
  → Return to user (< 500ms)
```

---

## 5️⃣ High-Level Design

```
┌──────────────────────────────────────────────────────────────┐
│                     SEARCH SERVING PATH                       │
│                                                                │
│  User → LB → Search API                                      │
│                 │                                              │
│         ┌───────┴────────┐                                    │
│         ▼                ▼                                     │
│   ACL Pre-Filter    Query Parser                              │
│   (Redis: user's    (tokenize,                                │
│    groups &          operators)                                │
│    permissions)                                                │
│         │                │                                     │
│         └───────┬────────┘                                    │
│                 ▼                                              │
│          ┌──────────────────┐                                 │
│          │  Elasticsearch    │                                 │
│          │  (text search     │                                 │
│          │   + ACL filter    │                                 │
│          │   + metadata      │                                 │
│          │   filters)        │                                 │
│          └────────┬─────────┘                                 │
│                   ▼                                            │
│            ┌────────────┐                                     │
│            │  Ranker     │                                     │
│            │  (relevance │                                     │
│            │  + personal │                                     │
│            │  + recency) │                                     │
│            └──────┬──────┘                                    │
│                   ▼                                            │
│              Response                                          │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    INDEXING PATH                               │
│                                                                │
│  Document Edit → Change Event → Kafka                         │
│                                    │                           │
│                        ┌───────────┼───────────┐              │
│                        ▼           ▼           ▼              │
│                  Content       ACL         Metadata            │
│                  Extractor     Resolver    Enricher            │
│                  (PDF, Docs,   (Permission (folder path,       │
│                   Sheets...)    Service)   workspace)          │
│                        │           │           │              │
│                        └───────────┼───────────┘              │
│                                    ▼                           │
│                             Search Indexer                     │
│                                    │                           │
│                            ┌───────┼───────┐                  │
│                            ▼       ▼       ▼                  │
│                          ES      Redis    S3                  │
│                        (index)  (ACL     (raw                 │
│                                 cache)   content)             │
└──────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: ACL-Aware Search — The Core Challenge

**The Problem**: Unlike web search where all results are public, document search must enforce access control. A user searching "budget" must NEVER see a confidential budget document they don't have access to. This fundamentally changes the search architecture.

**Approach A: Filter at Query Time (Post-Filtering)**
```
1. Search ES for "budget" → get top-1000 results by relevance
2. For each result, check ACL: does user have access?
3. Filter out unauthorized results
4. Return top-20 authorized results

Problem: If user has access to only 1% of documents, we need to 
retrieve 2000 candidates to get 20 results → slow and wasteful!

Worst case: user with very limited access → search 100K documents 
to find 20 they can see → terrible latency
```

**Approach B: Index ACL as a Search Filter (Recommended)**
```
Store ACL in the search index as a field:

{
  "doc_id": "doc_abc123",
  "title": "Q3 Budget Proposal",
  "content": "The proposed budget for Q3...",
  "acl": [
    "user:alice",           // Alice is owner
    "user:bob",             // Bob has access
    "group:engineering",    // Engineering team has access
    "domain:company.com"    // Anyone at the company
  ]
}

At query time, construct ACL filter:
  User "alice" belongs to groups ["engineering", "managers"]
  ACL filter: acl IN ["user:alice", "group:engineering", "group:managers", "domain:company.com"]

ES Query:
{
  "bool": {
    "must": [{"match": {"content": "budget"}}],
    "filter": [
      {"terms": {"acl": ["user:alice", "group:engineering", "group:managers", "domain:company.com"]}}
    ]
  }
}

Result: ES only scores documents that pass the ACL filter → fast!
  No post-filtering needed. ACL is applied as a Lucene filter (cached as bitset).
```

**ACL Update Propagation (Critical)**:
```
When permission changes:
  1. Alice shares doc_abc123 with Bob
  2. Permission Service writes change
  3. Event → Kafka → Search Indexer adds "user:bob" to doc's ACL field
  4. Next time Bob searches → doc_abc123 appears in results

When permission is REVOKED (must be fast!):
  1. Alice removes Bob's access to doc_abc123
  2. Permission Service writes change
  3. CRITICAL: Must update search index within seconds
     - Can't wait for async pipeline (Bob might still see the doc)
  4. Solution: synchronous update to ES on permission revoke
     OR: double-check ACL against Permission Service for top-20 results
         (belt and suspenders)

Consistency model:
  - ACL grants: eventual consistency (minutes OK — Bob can wait)
  - ACL revokes: must be near-instant (< 30 seconds)
  - Implementation: async for grants, sync for revokes
```

**Group Expansion Challenge**:
```
Problem: User "alice" is in group "engineering", which is a sub-group of "all-employees"
  Document ACL: ["group:all-employees"]
  Alice should see this document!

Solution: Expand groups at query time (not at index time)
  1. Resolve all of alice's groups recursively: ["engineering", "all-employees", "us-eng", ...]
  2. Include all groups in the ACL filter
  3. Cache group memberships in Redis (TTL: 5 minutes)
  
  Why not expand at index time?
  - If "all-employees" has 100K members, updating every document's ACL list 
    whenever the group membership changes is O(docs × members) — prohibitive
  - Query-time expansion: only resolve for the searching user → O(groups_per_user)
```

**Interview Talking Points**:
- "Index ACLs as a filter field in Elasticsearch — apply as a cached bitset filter, not post-filtering."
- "ACL grants are eventually consistent; ACL revokes must be near-instant with synchronous updates."
- "Group expansion at query time, not index time — avoids O(docs × members) update cost."

---

### Deep Dive 2: Content Extraction — Multi-Format Indexing

**The Problem**: Documents come in dozens of formats (Docs, PDF, XLSX, PPTX, images). We need to extract searchable text from all of them.

**Extraction Pipeline**:
```
Document → Format Detector (magic bytes / MIME type)
  → Format-Specific Extractor:
  
  Google Docs/Sheets/Slides → API: export as plain text
  PDF → Apache PDFBox / pdf2text (handles text + OCR for scanned PDFs)
  Word (.docx) → Apache POI → extract text, tables, headers
  Excel (.xlsx) → Apache POI → extract cell values, sheet names, formulas
  PowerPoint (.pptx) → Apache POI → extract slide text, speaker notes
  Plain text / Markdown / Code → direct text extraction
  Images (JPEG, PNG) → OCR (Tesseract / Google Vision API)
  
Output: structured JSON
{
  "text": "full extracted text...",
  "title": "extracted title",
  "headings": ["Section 1", "Section 2"],
  "tables": [{"headers": [...], "rows": [...]}],
  "metadata": {"page_count": 12, "word_count": 3400},
  "language": "en"
}
```

**Handling Large Documents**:
```
Problem: A 200-page PDF might have 100KB of text. Indexing all of it is expensive.

Strategy:
  1. Index full content for documents < 50KB text (95% of documents)
  2. For large documents (> 50KB text):
     - Index title + first 10KB + last 2KB + headings/table of contents
     - Store full text in blob storage for snippet extraction at query time
  3. For code files:
     - Index function/class names, comments, imports (structural elements)
     - Skip variable names and boilerplate
```

**Incremental Indexing for Real-Time Edits**:
```
Challenge: In Google Docs, users are typing in real-time. Re-indexing 
the entire document on every keystroke is wasteful.

Solution: Debounced incremental indexing
  1. Document Service emits change events (OT operations / CRDT deltas)
  2. Debounce: accumulate changes for 5-10 seconds
  3. Re-extract changed sections only (if supported by format)
  4. For simple text docs: partial update (update only changed fields in ES)
  5. For complex formats (XLSX): full re-extraction (cheaper than diffing)
  
  Trade-off: 5-10 second delay for searchability vs. constant re-indexing
```

**Interview Talking Points**:
- "Format-specific extractors: PDFBox for PDFs, Apache POI for Office, Tesseract for OCR on images."
- "Debounced incremental indexing: batch edits for 5-10 seconds before re-indexing."
- "For large documents, index title + first 10KB + headings; store full text in blob storage for snippets."

---

### Deep Dive 3: Personal Ranking — "My Documents" Should Rank Higher

**The Problem**: When a user searches "budget", their own budget document should rank higher than a random public document about budgets. Standard BM25 doesn't account for personal context.

**Personal Signals**:
```
Signal                          | Weight | Rationale
─────────────────────────────────────────────────────────────
User is document owner           | 3×    | Highest personal relevance
User edited recently (< 7 days)  | 2.5×  | Active collaboration
User viewed recently (< 7 days)  | 2×    | Recent interest
Document is starred              | 2×    | Explicitly marked important
Shared directly with user         | 1.5×  | Someone thought it's relevant
In user's frequently accessed folder | 1.3× | Workspace affinity
Same team/workspace as user       | 1.2×  | Organizational proximity
```

**Implementation — Score Boosting**:
```
final_score = text_relevance × personal_boost × recency_boost

personal_boost = max(
  3.0 if user is owner,
  2.5 if user edited in last 7 days,
  2.0 if user viewed in last 7 days,
  1.5 if shared directly with user,
  1.0 otherwise
)

recency_boost = 1 + 0.5 × exp(-days_since_modified / 30)
  // Documents modified recently get a gentle boost

User activity data:
  Redis: user_id → {recently_viewed: [doc1, doc2, ...], starred: [doc3, ...]}
  Updated on every document open/star event
  TTL: 30 days (older data not useful for ranking)
```

**"Quick Access" — Zero-Query Suggestions**:
```
Before user types anything, show "Quick Access" documents:
  1. Recently opened (last 7 days)
  2. Frequently accessed (opened > 5 times this month)
  3. Suggested for you (ML model: predict what user will open next)
  
  ML features:
    - Time of day (user opens "standup notes" every morning at 9am)
    - Day of week (user opens "weekly report" on Fridays)
    - Collaborative signals (if teammate just shared/edited a doc, surface it)
    - Calendar integration (upcoming meeting → surface meeting notes doc)

  Pre-computed: batch job updates Quick Access daily + real-time updates
```

**Interview Talking Points**:
- "Owner/editor/viewer hierarchy for personal boost: 3×, 2.5×, 2× respectively."
- "Zero-query Quick Access uses ML to predict what document the user wants before they type."
- "Time-based suggestions: 'standup notes' surfaces at 9am, 'weekly report' on Fridays."

---

### Deep Dive 4: Handling 500M Edits/Day — Real-Time Index Freshness

**The Problem**: In Google Docs, millions of users are simultaneously editing. Each edit should be searchable within 30 seconds. That's 500M indexing operations per day.

**Streaming Index Architecture**:
```
Document Edit Events → Kafka (partitioned by doc_id for ordering)
  → Indexer Workers consume events
  → Debounce: for same doc_id, batch edits in 5-second windows
  → Partial update to Elasticsearch:
      POST /documents/_update/doc_abc123
      {
        "doc": {
          "content": "updated full text...",
          "last_modified": "2026-04-25T10:30:05Z",
          "last_modified_by": "alice"
        }
      }

Throughput:
  500M edits/day, debounced to 100M index updates/day
  100M / 86400 = 1,157 updates/sec → manageable for ES cluster

Kafka partition strategy:
  Partition by doc_id hash → all edits for same doc go to same partition
  → guarantees ordering → no stale overwrites
```

**Handling Elasticsearch Refresh Interval**:
```
ES default refresh_interval: 1 second (new docs visible after 1s)
For our use case: 1s is fine (30s budget, most of latency is in pipeline)

But during bulk re-indexing (migration), set refresh_interval: 30s
to improve indexing throughput (30× fewer refreshes)

Near-real-time search:
  Edit at T=0 → Kafka (50ms) → Indexer (100ms) → ES write (50ms) → ES refresh (up to 1s)
  Total: ~1.2 seconds from edit to searchable ✓ (well within 30s target)
```

**Interview Talking Points**:
- "Debounce edits per doc_id: batch 5 seconds of edits into one index update."
- "Kafka partitioned by doc_id ensures edit ordering — no stale overwrites."
- "End-to-end: edit to searchable in ~1.2 seconds (Kafka latency + ES refresh interval)."

---

### Deep Dive 5: Cross-Workspace / Federated Search

**The Problem**: Enterprise users need to search across multiple "workspaces" — their personal Drive, team wikis, shared folders, Confluence spaces — all in one query.

**Federated Search Architecture**:
```
User searches "deployment guide"
  → Search Coordinator determines which workspaces user has access to:
      - Personal Drive
      - Team Wiki: "Platform Engineering"
      - Confluence Space: "DevOps"
      - Shared Drive: "Company Docs"
  
  → Scatter query to each workspace's search index in parallel
  → Each index returns top-20 results
  → Coordinator merges results by relevance
  → ACL verified (belt and suspenders)
  → Return top-20 global results

Challenges:
  1. Score normalization: BM25 scores from different indices aren't comparable
     Solution: normalize scores to [0, 1] within each index before merging
  
  2. Latency: total latency = max(individual index latencies)
     Solution: timeout at 500ms, return partial results from fast indices
  
  3. Deduplication: same document might exist in multiple workspaces
     Solution: deduplicate by content hash, keep the most-accessed version
```

**Unified vs. Separate Indices**:
```
Option A: One giant index for all workspaces
  Pros: simple query, consistent ranking
  Cons: noisy neighbors, permission complexity, hard to scale per-workspace

Option B: Per-workspace indices (recommended for enterprise)
  Pros: isolation, independent scaling, per-workspace customization
  Cons: federated search complexity, score normalization
  
  Hybrid: per-workspace indices + lightweight global index (titles + metadata only)
  → Global index for quick title matches across workspaces
  → Per-workspace indices for full-text content search
```

**Interview Talking Points**:
- "Federated search: scatter query to workspace indices in parallel, merge by normalized relevance."
- "Score normalization is critical when merging results from different indices — raw BM25 scores aren't comparable."
- "Hybrid: global index for title matches, per-workspace indices for content search."

---

### Deep Dive 6: OCR and AI-Powered Search

**The Problem**: Users scan receipts, whiteboard photos, and handwritten notes. They expect to search for text within these images. Also, users want semantic search: "find the document about last quarter's revenue" even if the title is "Q1_2026_financial_review.xlsx".

**OCR Pipeline**:
```
Image upload → Image format detection (JPEG, PNG, TIFF, PDF-image)
  → Pre-processing (deskew, contrast enhancement, noise reduction)
  → OCR Engine (Google Vision API / Tesseract)
  → Post-processing (spell-check OCR output, structure extraction)
  → Index extracted text

OCR Quality:
  Printed text: 99% accuracy
  Handwritten: 85-90% accuracy
  Whiteboard photos: 80-85% accuracy
  
  Strategy: lower boost for OCR-extracted text (0.5× vs 1× for typed text)
  → Reduces noise from OCR errors in ranking
```

**Semantic Search with Embeddings**:
```
Problem: User searches "revenue report" but document title is "Q1_2026_financial_review.xlsx"
  → BM25 won't match (different words!)

Solution: Embed documents and queries into a shared vector space

Document embedding:
  embedding = SentenceBERT(title + first_500_chars_of_content)
  Store in vector index (FAISS/Pinecone)

Query: "revenue report" → query_embedding = SentenceBERT("revenue report")
  → ANN search in FAISS → finds "Q1_2026_financial_review.xlsx" (semantically similar)

Hybrid retrieval:
  candidates_lexical = BM25_search("revenue report")      // exact word matches
  candidates_semantic = FAISS_ANN_search(query_embedding)  // concept matches
  candidates = union(candidates_lexical, candidates_semantic)
  re-rank with L2 model → return top-20
```

**Interview Talking Points**:
- "OCR for images with lower boost weight (0.5×) to reduce noise from OCR errors."
- "Semantic search with SentenceBERT embeddings: matches concepts, not just words."
- "Hybrid retrieval: BM25 for lexical + FAISS for semantic, then merge and re-rank."

---

## What is Expected at Each Level?

### Mid-Level
- Design basic full-text search with Elasticsearch
- Handle ACL filtering (post-filter approach)
- Multi-format content extraction
- Basic ranking with BM25 + recency

### Senior
- ACL as index filter (not post-filter) with cached bitsets
- ACL revocation consistency (sync vs async)
- Group expansion at query time
- Real-time indexing with Kafka + debouncing
- Personal ranking signals (owner, recently viewed, starred)
- Content extraction pipeline for multiple formats

### Staff
- Federated cross-workspace search with score normalization
- OCR pipeline with quality-aware boosting
- Semantic search with embeddings (FAISS)
- Zero-query Quick Access with ML predictions
- Handling 500M edits/day with debounced streaming
- Strong consistency for ACL revokes, eventual for grants
- Full capacity planning for 10B documents
