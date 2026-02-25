# Design a Product Catalog System - Hello Interview Framework

> **Question**: Design a product catalog system that allows users to upload items with descriptions and images, while automatically validating uploads to reject illegal items like weapons and providing success/failure feedback. Think Etsy or eBay's listing flow: create a product, attach photos, and the system decides if it complies with policy before it appears to others.
>
> **Asked at**: Meta
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
1. **Product Listing Creation**: Sellers create listings with title, description, price, category, and images (up to 10). Multi-step upload flow: metadata → images → submit.
2. **Image Upload & Processing**: Upload images (up to 20 MB each). Resize, compress, generate thumbnails. Store in CDN for fast delivery. Direct client-to-S3 via presigned URLs.
3. **Content Validation/Moderation**: Automatically scan listings for policy violations — prohibited items (weapons, drugs, counterfeit), explicit content, misleading descriptions. Provide clear accept/reject feedback with violation details.
4. **Listing Lifecycle**: State machine: DRAFT → PENDING_REVIEW → APPROVED (visible to buyers) / REJECTED (with reason). Sellers can edit and resubmit rejected listings.
5. **Catalog Browse & Search**: Buyers browse by category, search by keyword, filter by price/location. Only APPROVED listings visible to buyers.
6. **Seller Feedback**: Real-time status updates throughout the pipeline: "Processing images...", "Checking compliance...", "Approved!" or "Rejected: weapon detected in image 3."

#### Nice to Have (P1)
- Human moderation queue for edge cases (low-confidence ML decisions).
- Seller reputation score (affects moderation strictness — trusted sellers get fast-tracked).
- Listing versioning (edit history, diff between versions).
- Bulk upload (CSV import for large sellers).
- Rich media: video uploads, 360° images.
- Inventory management (quantity, variants like size/color).
- Similar listing detection (prevent duplicate/scam listings).

#### Below the Line (Out of Scope)
- Payment processing (assume separate payments service).
- Shipping/logistics management.
- Buyer-seller messaging.
- Reviews/ratings of listings or sellers.
- Recommendation engine (related items).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Upload-to-Live** | < 5 minutes (image processing + moderation) | Seller expects fast turnaround; competitive with Etsy/eBay |
| **Image Processing** | < 30 seconds per image (resize + thumbnails) | Seller wants to see preview quickly after upload |
| **Moderation Latency** | < 2 minutes for auto-moderation | Most listings should be auto-approved within minutes |
| **Search Latency** | < 200ms for catalog queries | Buyers expect instant search results |
| **Availability** | 99.9% for reads, 99.5% for uploads | Read-heavy marketplace; uploads can tolerate brief outages |
| **Consistency** | Strong for listing writes, eventual for search index | Listing status must be accurate; search can lag a few seconds |
| **Scale** | 100M listings, 1M new/day, 50M images/day | Large marketplace scale (Etsy/eBay tier) |
| **Storage** | Images: ~5 PB total (100M listings × 5 images × 10 MB avg) | Majority of storage cost is images |
| **Throughput** | 12 listings/sec sustained, 50/sec peak; 58 image uploads/sec | Peak during evenings and promotional events |

### Capacity Estimation

```
Listings:
  Total: 100M
  New per day: 1M = ~12/sec sustained, 50/sec peak
  Listing metadata: 100M × 2 KB = ~200 GB

Images:
  Per listing: 5 images average (max 10)
  New per day: 5M = ~58 uploads/sec
  Average image: 10 MB → 50 TB/day raw uploads
  After resize (3 sizes per image): 50 TB × 3 = 150 TB/day processed
  Total image storage: ~5 PB (CDN/S3)

Moderation:
  Listings to moderate: 1M/day = ~12/sec
  ML inference: ~500ms per listing (text + images in parallel)
  ML capacity: 12/sec × 0.5s = 6 concurrent inference → modest GPU need
  Auto-approved: 85% (~850K/day)
  Auto-rejected: 5% (~50K/day)
  Human review: 10% (~100K/day)

Search:
  Queries per day: 10M = ~116/sec, peak 500/sec
  Catalog views per day: 50M = ~580/sec
  Search index size: ~50 GB (Elasticsearch)

Storage summary:
  Listing metadata (Postgres): ~200 GB
  Images (S3 + CDN): ~5 PB
  Search index (Elasticsearch): ~50 GB
  Total non-image: ~250 GB (fits on a single large DB instance)
```

---

## 2️⃣ Core Entities

### Entity 1: Listing
```java
public class Listing {
    private final String listingId;         // UUID
    private final String sellerId;
    private final String title;
    private final String description;
    private final int priceCents;
    private final String currency;          // "USD"
    private final String category;          // "electronics", "clothing", etc.
    private final List<String> tags;
    private final List<ListingImage> images;
    private final ListingStatus status;     // DRAFT, PENDING_REVIEW, PROCESSING, APPROVED, REJECTED
    private final ModerationResult moderation;
    private final int quantity;
    private final String location;          // Seller's city/country
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant approvedAt;
    private final int version;              // For optimistic concurrency + edit tracking
}

public enum ListingStatus {
    DRAFT,              // Seller is composing the listing
    PENDING_REVIEW,     // Submitted, waiting for image processing + moderation
    PROCESSING,         // Images processed, moderation in progress
    APPROVED,           // Live and visible to buyers
    REJECTED,           // Failed moderation (with violation details)
    NEEDS_HUMAN_REVIEW  // ML uncertain, queued for human moderator
}
```

### Entity 2: ListingImage
```java
public class ListingImage {
    private final String imageId;           // UUID
    private final String listingId;
    private final String originalUrl;       // S3 URL (original upload)
    private final String thumbnailUrl;      // 200×200 (for search results, grids)
    private final String mediumUrl;         // 800×800 (for listing detail page)
    private final String largeUrl;          // 1600×1600 (for zoom/fullscreen)
    private final ImageStatus status;       // UPLOADING, PROCESSING, READY, FAILED
    private final ModerationResult moderation; // Per-image moderation result
    private final int sortOrder;            // Display order chosen by seller
    private final long sizeBytes;
    private final String contentType;       // "image/jpeg", "image/png", "image/webp"
    private final Instant uploadedAt;
}

public enum ImageStatus {
    UPLOADING,   // Presigned URL issued, client uploading to S3
    PROCESSING,  // Resize + compress pipeline running
    READY,       // All sizes generated, CDN URLs available
    FAILED       // Processing failed (corrupt image, unsupported format)
}
```

### Entity 3: ModerationResult
```java
public class ModerationResult {
    private final ModerationDecision decision; // APPROVED, REJECTED, NEEDS_HUMAN_REVIEW
    private final double confidenceScore;      // 0.0 - 1.0 (highest violation score)
    private final List<Violation> violations;  // All detected violations
    private final String moderatorId;          // Human moderator (if applicable)
    private final Instant reviewedAt;
    private final ModerationSource source;     // AUTO_ML, HUMAN, HYBRID
}

public enum ModerationDecision { APPROVED, REJECTED, NEEDS_HUMAN_REVIEW }
public enum ModerationSource { AUTO_ML, HUMAN, HYBRID }
```

### Entity 4: Violation
```java
public class Violation {
    private final String violationType;     // "PROHIBITED_ITEM", "EXPLICIT_CONTENT", "COUNTERFEIT"
    private final String description;       // "Weapon detected in image 3"
    private final String source;            // "IMAGE_ML", "TEXT_ML", "CROSS_CHECK", "HUMAN"
    private final double confidence;        // 0.0 - 1.0
    private final String evidenceRef;       // Which image or text snippet triggered this
}
```

### Entity 5: Seller
```java
public class Seller {
    private final String sellerId;
    private final String name;
    private final String email;
    private final double reputationScore;   // 0-5 stars
    private final int totalListings;
    private final int approvedListings;
    private final int rejectedListings;
    private final SellerTier tier;          // NEW, ESTABLISHED, TRUSTED
    private final Instant joinedAt;
}

public enum SellerTier {
    NEW,          // < 10 listings, no reputation → strictest moderation
    ESTABLISHED,  // 10-100 listings, > 3.5 stars → standard moderation
    TRUSTED       // 100+ listings, > 4.5 stars, < 1% rejection rate → fast-track
}
```

### Entity 6: Category
```java
public class Category {
    private final String categoryId;
    private final String name;
    private final String parentCategoryId;   // Hierarchical: "clothing" > "mens" > "jackets"
    private final List<String> requiredFields; // "size" for clothing, "brand" for electronics
    private final List<String> prohibitedKeywords; // Category-specific banned terms
    private final double moderationStrictness;  // 0.0 (lenient) - 1.0 (strict); weapons category = 1.0
}
```

---

## 3️⃣ API Design

### 1. Create Listing (Step 1: Metadata)
```
POST /api/v1/listings

Request:
{
  "title": "Vintage Leather Jacket",
  "description": "Genuine leather, size M, brown, great condition. Barely worn.",
  "price_cents": 12500,
  "currency": "USD",
  "category": "clothing",
  "tags": ["vintage", "leather", "jacket"],
  "quantity": 1,
  "location": "Brooklyn, NY"
}

Response (201 Created):
{
  "listing_id": "lst_a1b2c3d4",
  "status": "DRAFT",
  "created_at": "2025-01-10T10:00:00Z",
  "upload_urls": []
}
```

> **Note**: Listing starts in DRAFT status. No images yet — seller adds them in Step 2.

### 2. Request Image Upload URL (Step 2: Presigned URL)
```
POST /api/v1/listings/{listing_id}/images/upload-url

Request:
{
  "filename": "jacket-front.jpg",
  "content_type": "image/jpeg",
  "size_bytes": 5242880
}

Response (200 OK):
{
  "image_id": "img_x1y2z3",
  "upload_url": "https://s3.amazonaws.com/listing-uploads/raw/img_x1y2z3.jpg?X-Amz-Signature=...",
  "expires_in_sec": 300,
  "max_size_bytes": 20971520
}

Client then uploads directly to S3 presigned URL (no server proxy needed).
S3 upload event triggers image processing pipeline automatically.
```

### 3. Submit for Review (Step 3: Trigger Moderation)
```
POST /api/v1/listings/{listing_id}/submit

Response (202 Accepted):
{
  "listing_id": "lst_a1b2c3d4",
  "status": "PENDING_REVIEW",
  "estimated_review_time_sec": 120,
  "images_processing": 3,
  "images_ready": 2,
  "message": "Your listing is being reviewed. We'll notify you when it's ready."
}
```

> **Note**: Returns 202 (Accepted) not 200 — the moderation is async. Client polls or uses SSE for updates.

### 4. Get Listing Status (Polling / SSE)
```
GET /api/v1/listings/{listing_id}/status

Response (200 OK) — APPROVED case:
{
  "listing_id": "lst_a1b2c3d4",
  "status": "APPROVED",
  "moderation": {
    "decision": "APPROVED",
    "confidence": 0.95,
    "source": "AUTO_ML",
    "reviewed_at": "2025-01-10T10:01:45Z"
  },
  "images": [
    {
      "image_id": "img_x1y2z3",
      "status": "READY",
      "thumbnail_url": "https://cdn.marketplace.com/img_x1y2z3/thumb.webp",
      "medium_url": "https://cdn.marketplace.com/img_x1y2z3/medium.webp",
      "large_url": "https://cdn.marketplace.com/img_x1y2z3/large.webp"
    }
  ],
  "live_url": "https://marketplace.com/listing/lst_a1b2c3d4"
}

Response (200 OK) — REJECTED case:
{
  "listing_id": "lst_a1b2c3d4",
  "status": "REJECTED",
  "moderation": {
    "decision": "REJECTED",
    "confidence": 0.92,
    "violations": [
      {
        "type": "PROHIBITED_ITEM",
        "description": "Weapon-like object detected in image 2",
        "source": "IMAGE_ML",
        "confidence": 0.92,
        "evidence_ref": "img_a4b5c6"
      }
    ]
  },
  "message": "Please remove the flagged image and resubmit.",
  "actions": ["EDIT_LISTING", "CONTACT_SUPPORT", "APPEAL"]
}
```

### 5. Edit and Resubmit (After Rejection)
```
PATCH /api/v1/listings/{listing_id}

Request:
{
  "description": "Genuine leather jacket, updated description",
  "remove_image_ids": ["img_a4b5c6"]
}

Response (200 OK):
{
  "listing_id": "lst_a1b2c3d4",
  "status": "DRAFT",
  "version": 2,
  "message": "Listing updated. Submit again when ready."
}
```

### 6. Search Catalog (Buyer)
```
GET /api/v1/catalog/search?q=leather+jacket&category=clothing&price_max=20000&sort=relevance&limit=20

Response (200 OK):
{
  "results": [
    {
      "listing_id": "lst_a1b2c3d4",
      "title": "Vintage Leather Jacket",
      "price_cents": 12500,
      "currency": "USD",
      "thumbnail_url": "https://cdn.marketplace.com/img_x1y2z3/thumb.webp",
      "seller_name": "Alice",
      "seller_reputation": 4.8,
      "location": "Brooklyn, NY",
      "created_at": "2025-01-10T10:00:00Z"
    }
  ],
  "total": 1234,
  "next_cursor": "eyJzb3J0IjoicmVsZXZhbmNlIiwib2Zmc2V0IjoyMH0="
}
```

### 7. Browse by Category
```
GET /api/v1/catalog/categories/clothing?sort=newest&limit=20

Response (200 OK):
{
  "category": { "id": "clothing", "name": "Clothing", "parent": null },
  "subcategories": [
    { "id": "mens", "name": "Men's", "listing_count": 45230 },
    { "id": "womens", "name": "Women's", "listing_count": 62140 },
    { "id": "accessories", "name": "Accessories", "listing_count": 18900 }
  ],
  "listings": [...],
  "total": 126270,
  "next_cursor": "..."
}
```

### 8. Seller Status Stream (SSE)
```
GET /api/v1/listings/{listing_id}/status/stream
Accept: text/event-stream

event: image_ready
data: { "image_id": "img_x1y2z3", "status": "READY", "thumbnail_url": "https://cdn/..." }

event: moderation_started
data: { "stage": "text_analysis", "message": "Analyzing listing text..." }

event: moderation_progress
data: { "stage": "image_analysis", "message": "Scanning images for policy compliance..." }

event: listing_approved
data: { "status": "APPROVED", "live_url": "https://marketplace.com/listing/lst_a1b2c3d4" }
```

---

## 4️⃣ Data Flow

### Flow 1: Listing Creation → Image Upload → Submit → Moderation → Approval
```
1. Seller creates listing (title, description, price) → status: DRAFT
   POST /listings → Listing Service → PostgreSQL (listing row created)
   ↓
2. Seller uploads images (1-10):
   a. Request presigned URL: POST /listings/{id}/images/upload-url
      → Image Service validates (listing exists, count < 10, size < 20 MB)
      → Generates S3 presigned PUT URL (expires in 5 min)
   b. Client uploads directly to S3 (server never touches image bytes)
   c. S3 event notification → Kafka topic "image-uploaded"
   ↓
3. Image Processing Pipeline (Kafka consumer):
   a. Download original from S3 (10 MB avg, ~500ms)
   b. Validate: real image? (check magic bytes, not just extension)
   c. Resize: thumbnail (200×200), medium (800×800), large (1600×1600)
   d. Compress: WebP format (30% smaller than JPEG at same quality)
   e. Upload 3 versions to CDN-backed S3 bucket (~1s)
   f. Update DB: image status → READY, set CDN URLs
   Total per image: ~5-10 seconds
   ↓
4. Seller clicks "Submit for Review" → status: PENDING_REVIEW
   → Listing Service checks: all images READY? (wait if some still processing)
   → Produce to Kafka topic "listings-pending-review"
   ↓
5. Moderation Pipeline (Kafka consumer):
   a. TEXT MODERATION (~100ms):
      - NLP classifier on title + description
      - Prohibited keyword matching (context-aware)
      - Embeddings comparison against known-violation patterns
   
   b. IMAGE MODERATION (~500ms per image, parallelized):
      - Image classification model (weapons, drugs, explicit, counterfeit)
      - Per-image violation score: 0.0 (clean) to 1.0 (violation)
   
   c. CROSS-CHECK (~50ms):
      - Title-image consistency: title says "book" but image shows weapon?
      - Price reasonableness for category
      - Duplicate/scam detection (similar to existing rejected listings)
   
   d. DECISION ENGINE:
      max_score = max(text_score, max(image_scores), cross_check_score)
      
      if max_score < 0.3:     → AUTO_APPROVE (85% of listings)
      if max_score > 0.8:     → AUTO_REJECT with violation details (5%)
      if 0.3 ≤ score ≤ 0.8:  → NEEDS_HUMAN_REVIEW (10%)
   ↓
6. Post-Decision:
   APPROVED:
     a. Status → APPROVED, set approvedAt
     b. Index listing in Elasticsearch (searchable by buyers)
     c. Notify seller: "Your listing is live! 🎉"
   
   REJECTED:
     a. Status → REJECTED with violation details
     b. Notify seller: "Rejected: weapon detected in image 2. Please edit and resubmit."
   
   NEEDS_HUMAN_REVIEW:
     a. Add to human moderation queue (Redis sorted set by priority)
     b. Notify seller: "Under additional review. Expected: < 1 hour."
     c. Human moderator reviews → APPROVED or REJECTED
     d. Human decision → ML training data (feedback loop)
   ↓
7. Total end-to-end: upload → live = < 5 minutes (auto-moderation path)
```

### Flow 2: Presigned URL Upload (Direct Client-to-S3)
```
1. Client: POST /listings/{id}/images/upload-url
   { filename: "jacket.jpg", size: 5242880, content_type: "image/jpeg" }
   ↓
2. Server validates: listing exists, belongs to user, count < 10, size < 20 MB
   ↓
3. Server generates presigned S3 PUT URL:
   s3.generatePresignedUrl(PUT, "listing-uploads", "raw/{image_id}.jpg", 300s expiry)
   ↓
4. Server creates image row in DB: status = UPLOADING
   ↓
5. Client uploads directly to S3:
   PUT https://s3.amazonaws.com/listing-uploads/raw/img_001.jpg
   Body: <binary image data>
   ↓
6. S3 event notification → Kafka "image-uploaded" → Image Processor
   ↓
Benefits:
  - Server NEVER handles image bytes (saves 50 TB/day bandwidth)
  - Client gets direct S3 upload speed (parallel, fast)
  - Presigned URL expires in 5 min (secure)
  - S3 handles unlimited concurrent uploads
```

### Flow 3: Search Index Update (Eventual Consistency)
```
1. Listing approved → Kafka "listing-approved"
   ↓
2. Search Index Updater (Kafka consumer):
   a. Consume approved listing event
   b. Build ES document: title, description, category, price, location, thumbnail, seller
   c. Index into Elasticsearch
   d. Available for buyer search within 1-5 seconds
   ↓
3. Listing rejected or edited → Kafka "listing-removed"
   a. Remove from Elasticsearch index
   b. Listing no longer appears in buyer search
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                            │
│                                                                              │
│  ┌──────────────────┐              ┌──────────────────┐                    │
│  │  SELLER APP       │              │  BUYER APP        │                    │
│  │                   │              │                   │                    │
│  │ • Create listing  │              │ • Search catalog  │                    │
│  │ • Upload images   │              │ • Browse categories│                   │
│  │ • Submit for review│             │ • View listing    │                    │
│  │ • View status     │              │ • Filter/sort     │                    │
│  │ • Edit & resubmit │              │                   │                    │
│  └────────┬──────────┘              └────────┬──────────┘                    │
│           │                                  │                               │
└───────────┼──────────────────────────────────┼───────────────────────────────┘
            │                                  │
            └──────────────┬───────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Auth, Rate Limit, Routing)                   │
└──────┬──────────────────────────────┬──────────────────┬───────────────────┘
       │ Listing CRUD + Submit        │ Image Upload      │ Catalog Search
       ▼                              ▼                   ▼
┌──────────────────┐    ┌──────────────────┐   ┌──────────────────────┐
│ LISTING SERVICE   │    │ IMAGE SERVICE     │   │ SEARCH SERVICE       │
│ (Stateless)       │    │ (Stateless)       │   │ (Stateless)          │
│                   │    │                   │   │                      │
│ • CRUD operations │    │ • Presigned URLs  │   │ • Full-text search   │
│ • Status mgmt     │    │ • Upload tracking │   │ • Category browse    │
│ • Submit flow     │    │ • CDN URL return  │   │ • Filters & facets   │
│ • Edit & resubmit │    │                   │   │ • Relevance scoring  │
│                   │    │ Pods: 10          │   │                      │
│ Pods: 20          │    │                   │   │ Pods: 20             │
└──────┬────────────┘    └────────┬──────────┘   └──────────┬───────────┘
       │                          │                         │
  ┌────┼──────────────────────────┼─────────────────────────┼────────┐
  │    ▼                          ▼                         ▼        │
  │ ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐  │
  │ │ LISTING DB    │   │ S3 + CDN         │   │ ELASTICSEARCH    │  │
  │ │ (PostgreSQL)  │   │                  │   │                  │  │
  │ │               │   │ • Original images│   │ • 100M listings  │  │
  │ │ • Listings    │   │   (S3 raw)       │   │ • Full-text index│  │
  │ │ • Images meta │   │ • Resized images │   │ • Faceted filters│  │
  │ │ • Sellers     │   │   (CDN-backed S3)│   │ • Geo search     │  │
  │ │ • Categories  │   │                  │   │                  │  │
  │ │               │   │ ~5 PB total      │   │ ~50 GB           │  │
  │ │ ~200 GB       │   │                  │   │                  │  │
  │ └──────────────┘   └──────────────────┘   └──────────────────┘  │
  └─────────────────────────────────────────────────────────────────┘

                         ▼ KAFKA EVENT BUS ▼
┌────────────────────────────────────────────────────────────────────────────┐
│  Topic: "image-uploaded"        S3 upload events → image processing        │
│  Topic: "listings-pending"      Submit → moderation pipeline               │
│  Topic: "moderation-results"    Approved/rejected → update listing status  │
│  Topic: "listing-approved"      → index in Elasticsearch                  │
│  Topic: "listing-removed"       → remove from Elasticsearch               │
│  Topic: "seller-notifications"  → push notifications to seller            │
└─────┬──────────────────────────┬──────────────────────┬───────────────────┘
      │                          │                      │
┌─────▼──────────────┐  ┌───────▼──────────────┐  ┌───▼──────────────────┐
│ IMAGE PROCESSOR     │  │ MODERATION SERVICE    │  │ SEARCH INDEX UPDATER │
│                     │  │                       │  │                      │
│ • Download original │  │ • Text ML classifier  │  │ • Consume approved   │
│ • Validate format   │  │ • Image ML classifier │  │   listing events     │
│ • Resize (3 sizes)  │  │ • Cross-check engine  │  │ • Index to ES        │
│ • Compress (WebP)   │  │ • Decision engine     │  │ • Remove on reject   │
│ • Upload to CDN     │  │ • Confidence scoring  │  │                      │
│ • Update DB status  │  │                       │  │ Pods: 5              │
│                     │  │ GPU Pods: 5-10        │  └──────────────────────┘
│ CPU Pods: 20-100    │  │ + Human Queue UI      │
│ (auto-scaled)       │  │                       │
└─────────────────────┘  └───────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│  NOTIFICATION SERVICE                                                       │
│  • Consume seller-notifications topic                                      │
│  • Push via: in-app, email, mobile push (configurable per seller)         │
│  • Status updates: "Processing...", "Approved!", "Rejected: reason"       │
└────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│  HUMAN MODERATION QUEUE (for edge cases, 0.3-0.8 ML confidence)           │
│  • Web UI: view listing + images + ML scores + similar past decisions     │
│  • Quick actions: APPROVE / REJECT (with reason dropdown)                 │
│  • Escalation: senior moderator for ambiguous cases                       │
│  • SLA: 95% reviewed within 1 hour                                        │
│  • Feedback loop: human decisions retrain ML model weekly                 │
└────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Listing Service** | CRUD, status management, submit flow | Go/Java on K8s | 20 stateless pods |
| **Image Service** | Presigned URLs, upload tracking, CDN URL return | Go/Java on K8s | 10 stateless pods |
| **Image Processor** | Resize, compress, thumbnail generation | Go/Python on K8s | 20-100 CPU pods (auto-scaled) |
| **Moderation Service** | Text ML + Image ML + cross-check + decision engine | Python + GPU on K8s | 5-10 GPU nodes |
| **Search Service** | Full-text search, category browse, faceted filters | Go/Java on K8s | 20 stateless pods |
| **Search Index Updater** | Consume approved/rejected events → update ES | Go/Java on K8s | 5 pods |
| **Notification Service** | Push status updates to sellers | Go/Java on K8s | 5 pods |
| **Listing DB** | Source of truth: listings, images, sellers, categories | PostgreSQL (primary-replica) | ~200 GB |
| **Image Storage** | Original + processed images | S3 + CloudFront CDN | ~5 PB |
| **Search Index** | Searchable listing data (only APPROVED) | Elasticsearch 3-node cluster | ~50 GB |
| **Event Bus** | Decouple stages, buffer bursts, enable async pipelines | Kafka (5+ brokers) | 6 topics |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Data Model — Relational Schema for Listings

**The Problem**: We need a schema that supports listing CRUD, image tracking, moderation results, and efficient queries for both sellers (my listings) and buyers (search/browse). The listing lifecycle (state machine) adds complexity with status transitions and version tracking.

```sql
-- Listings table: source of truth for all listing metadata
CREATE TABLE listings (
    listing_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID NOT NULL REFERENCES sellers(seller_id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    price_cents     INTEGER NOT NULL CHECK (price_cents > 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    category_id     VARCHAR(100) NOT NULL,
    tags            TEXT[],                         -- PostgreSQL array
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    quantity        INTEGER DEFAULT 1,
    location        VARCHAR(200),
    version         INTEGER DEFAULT 1,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    approved_at     TIMESTAMP,
    
    CONSTRAINT valid_status CHECK (status IN 
        ('DRAFT', 'PENDING_REVIEW', 'PROCESSING', 'APPROVED', 'REJECTED', 'NEEDS_HUMAN_REVIEW'))
);

CREATE INDEX idx_listings_seller ON listings (seller_id, created_at DESC);
CREATE INDEX idx_listings_status ON listings (status, created_at DESC);
CREATE INDEX idx_listings_category ON listings (category_id, status, created_at DESC);

-- Listing images: tracks upload status + CDN URLs for each size
CREATE TABLE listing_images (
    image_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id      UUID NOT NULL REFERENCES listings(listing_id) ON DELETE CASCADE,
    original_url    VARCHAR(1000),
    thumbnail_url   VARCHAR(1000),          -- 200×200
    medium_url      VARCHAR(1000),          -- 800×800
    large_url       VARCHAR(1000),          -- 1600×1600
    status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
    sort_order      INTEGER DEFAULT 0,
    size_bytes      BIGINT,
    content_type    VARCHAR(50),
    uploaded_at     TIMESTAMP DEFAULT NOW(),
    processed_at    TIMESTAMP,

    CONSTRAINT valid_image_status CHECK (status IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX idx_images_listing ON listing_images (listing_id, sort_order);

-- Moderation results: one per listing, updated on each review
CREATE TABLE moderation_results (
    moderation_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id      UUID NOT NULL REFERENCES listings(listing_id),
    decision        VARCHAR(30) NOT NULL,
    confidence      DOUBLE PRECISION,
    source          VARCHAR(20) NOT NULL,   -- 'AUTO_ML', 'HUMAN', 'HYBRID'
    moderator_id    UUID,                   -- human moderator (if applicable)
    reviewed_at     TIMESTAMP DEFAULT NOW(),
    version         INTEGER NOT NULL,       -- matches listing version at time of review
    
    CONSTRAINT valid_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'NEEDS_HUMAN_REVIEW'))
);

CREATE INDEX idx_moderation_listing ON moderation_results (listing_id, version DESC);

-- Violations: one row per detected violation per moderation
CREATE TABLE violations (
    violation_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moderation_id   UUID NOT NULL REFERENCES moderation_results(moderation_id),
    violation_type  VARCHAR(50) NOT NULL,   -- 'PROHIBITED_ITEM', 'EXPLICIT_CONTENT', etc.
    description     TEXT NOT NULL,
    source          VARCHAR(20) NOT NULL,   -- 'IMAGE_ML', 'TEXT_ML', 'CROSS_CHECK', 'HUMAN'
    confidence      DOUBLE PRECISION,
    evidence_ref    VARCHAR(255)            -- image_id or text snippet reference
);

CREATE INDEX idx_violations_moderation ON violations (moderation_id);

-- Sellers table
CREATE TABLE sellers (
    seller_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    reputation      DOUBLE PRECISION DEFAULT 0.0,
    total_listings  INTEGER DEFAULT 0,
    approved_count  INTEGER DEFAULT 0,
    rejected_count  INTEGER DEFAULT 0,
    tier            VARCHAR(20) DEFAULT 'NEW',
    joined_at       TIMESTAMP DEFAULT NOW(),

    CONSTRAINT valid_tier CHECK (tier IN ('NEW', 'ESTABLISHED', 'TRUSTED'))
);

-- Categories: hierarchical with moderation config
CREATE TABLE categories (
    category_id         VARCHAR(100) PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    parent_category_id  VARCHAR(100) REFERENCES categories(category_id),
    required_fields     TEXT[],
    prohibited_keywords TEXT[],
    moderation_strictness DOUBLE PRECISION DEFAULT 0.5
);
```

**Why this schema works**:
```
Query: "Get seller's listings sorted by recency"
  → SELECT * FROM listings WHERE seller_id = ? ORDER BY created_at DESC LIMIT 20;
  → Uses idx_listings_seller → efficient

Query: "Get all PENDING_REVIEW listings for moderation pipeline"
  → SELECT * FROM listings WHERE status = 'PENDING_REVIEW' ORDER BY created_at LIMIT 100;
  → Uses idx_listings_status → efficient

Query: "Get listing with all images and latest moderation result"
  → SELECT l.*, li.*, mr.* FROM listings l
    LEFT JOIN listing_images li ON l.listing_id = li.listing_id
    LEFT JOIN moderation_results mr ON l.listing_id = mr.listing_id AND mr.version = l.version
    WHERE l.listing_id = ?;
  → Single query joins for listing detail page
```

---

### Deep Dive 2: Image Processing Pipeline — Resize, Compress, Thumbnail in < 30 Seconds

**The Problem**: 5M images/day uploaded. Each needs 3 resized versions + compression. Must be fast (seller wants to see preview quickly) and cost-efficient. Images arrive as raw uploads to S3 — we need an async pipeline to process them.

```java
public class ImageProcessingService {

    /**
     * Image processing pipeline per image:
     * 
     * 1. S3 event → Kafka "image-uploaded" → this consumer
     * 2. Download original from S3 (10 MB avg, ~500ms)
     * 3. Validate: is it a real image? (magic bytes, not just extension)
     * 4. Resize to 3 sizes (libvips — 10× faster than ImageMagick)
     * 5. Compress: WebP format (30% smaller than JPEG at same quality)
     * 6. Upload 3 versions to CDN-backed S3 bucket
     * 7. Update DB: image status → READY, set CDN URLs
     * 
     * Total per image: ~5-10 seconds
     * Throughput: 50 pods × 6 images/min = 300 images/min = 18K/hour
     * Daily need: 5M/24h = ~208K/hour → need ~60 pods at peak
     */
    
    private static final int THUMB_SIZE = 200;
    private static final int MEDIUM_SIZE = 800;
    private static final int LARGE_SIZE = 1600;
    private static final int JPEG_QUALITY = 85;
    
    public void processImage(ImageUploadedEvent event) {
        String imageId = event.getImageId();
        String listingId = event.getListingId();
        String s3Key = event.getS3Key();
        
        try {
            // Step 1: Update status to PROCESSING
            imageRepository.updateStatus(imageId, ImageStatus.PROCESSING);
            
            // Step 2: Download original from S3
            byte[] originalBytes = s3Client.getObject(UPLOAD_BUCKET, s3Key);
            
            // Step 3: Validate — is this a real image?
            ImageFormat format = validateImage(originalBytes);
            if (format == null) {
                imageRepository.updateStatus(imageId, ImageStatus.FAILED);
                return;
            }
            
            // Step 4: Resize to 3 sizes (using libvips via JNI)
            // libvips is streaming — memory-efficient for large images
            byte[] thumbnail = resizeImage(originalBytes, THUMB_SIZE, THUMB_SIZE, CropMode.CENTER);
            byte[] medium = resizeImage(originalBytes, MEDIUM_SIZE, MEDIUM_SIZE, CropMode.FIT);
            byte[] large = resizeImage(originalBytes, LARGE_SIZE, LARGE_SIZE, CropMode.FIT);
            
            // Step 5: Compress to WebP (30% smaller than JPEG)
            byte[] thumbWebP = compressToWebP(thumbnail, JPEG_QUALITY);
            byte[] mediumWebP = compressToWebP(medium, JPEG_QUALITY);
            byte[] largeWebP = compressToWebP(large, JPEG_QUALITY);
            
            // Step 6: Upload all 3 sizes to CDN-backed bucket (parallel)
            String basePath = String.format("processed/%s/%s", listingId, imageId);
            
            CompletableFuture<String> thumbFuture = uploadAsync(CDN_BUCKET, basePath + "/thumb.webp", thumbWebP);
            CompletableFuture<String> mediumFuture = uploadAsync(CDN_BUCKET, basePath + "/medium.webp", mediumWebP);
            CompletableFuture<String> largeFuture = uploadAsync(CDN_BUCKET, basePath + "/large.webp", largeWebP);
            
            CompletableFuture.allOf(thumbFuture, mediumFuture, largeFuture).join();
            
            // Step 7: Update DB with CDN URLs and status → READY
            String cdnBase = "https://cdn.marketplace.com/" + basePath;
            imageRepository.updateProcessed(imageId,
                cdnBase + "/thumb.webp",
                cdnBase + "/medium.webp",
                cdnBase + "/large.webp",
                ImageStatus.READY);
            
            // Step 8: Publish event for SSE notification to seller
            kafkaProducer.send("image-processing-complete", new ImageReadyEvent(
                imageId, listingId, cdnBase + "/thumb.webp"));
            
        } catch (Exception e) {
            log.error("Image processing failed for {}: {}", imageId, e.getMessage());
            imageRepository.updateStatus(imageId, ImageStatus.FAILED);
            // Retry via Kafka consumer retry policy (3 attempts with backoff)
        }
    }
    
    /**
     * Validate image by checking magic bytes (file signature).
     * Don't trust Content-Type header or file extension — they can be spoofed.
     */
    private ImageFormat validateImage(byte[] bytes) {
        if (bytes.length < 4) return null;
        
        // JPEG: starts with FF D8 FF
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return ImageFormat.JPEG;
        }
        // PNG: starts with 89 50 4E 47
        if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 
            && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
            return ImageFormat.PNG;
        }
        // WebP: starts with RIFF....WEBP
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' 
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return ImageFormat.WEBP;
        }
        
        return null; // Unsupported or not a real image
    }
    
    private byte[] resizeImage(byte[] original, int maxWidth, int maxHeight, CropMode mode) {
        // libvips: streaming resize — never loads full image into memory
        // For THUMB: center-crop to square (most relevant area)
        // For MEDIUM/LARGE: fit within bounds, preserve aspect ratio
        return VipsImageProcessor.resize(original, maxWidth, maxHeight, mode);
    }
}
```

**Scaling math**:
```
5M images/day = 58 images/sec sustained, 200/sec peak
Each image takes ~10 seconds (download + resize + compress + upload)
Concurrent images at peak: 200/sec × 10s = 2000 images in flight
Pods needed: 2000 / 6 images-per-pod-per-min = ~100 pods at peak

Auto-scaling:
  - Scale on Kafka consumer lag metric
  - If lag > 1000 messages → scale up
  - If lag < 100 → scale down
  - Use spot instances (image processing is idempotent; retry on preemption)

Cost optimization:
  - Spot instances for 80% of pods (3× cheaper)
  - On-demand for baseline 20 pods
  - Peak scaling: 20 on-demand + 80 spot = 100 total
```

---

### Deep Dive 3: Content Moderation — ML Classification for Policy Compliance

**The Problem**: Must detect weapons, drugs, explicit content, counterfeit goods from images AND text. False negatives (allowing illegal items) are dangerous and carry legal risk. False positives (rejecting legitimate items) frustrate sellers and lose revenue. Must balance safety with UX.

```java
public class ModerationService {

    /**
     * Moderation pipeline per listing:
     * 
     * Three stages run in parallel, then a decision engine combines results.
     * 
     * Thresholds:
     *   < 0.3 → AUTO_APPROVE (85% of listings) — fast, good UX
     *   > 0.8 → AUTO_REJECT with violation details (5%) — high confidence, safe
     *   0.3 - 0.8 → NEEDS_HUMAN_REVIEW (10%) — uncertain, safety net
     * 
     * Seller tier affects thresholds:
     *   TRUSTED sellers: approve threshold raised to 0.4 (more lenient)
     *   NEW sellers: approve threshold lowered to 0.2 (stricter)
     */
    
    private static final double AUTO_APPROVE_THRESHOLD = 0.3;
    private static final double AUTO_REJECT_THRESHOLD = 0.8;
    
    public ModerationResult moderateListing(Listing listing, List<ListingImage> images) {
        Seller seller = sellerRepository.findById(listing.getSellerId());
        
        // Run all three stages in parallel
        CompletableFuture<TextModerationResult> textFuture = 
            CompletableFuture.supplyAsync(() -> moderateText(listing));
        
        CompletableFuture<List<ImageModerationResult>> imageFuture = 
            CompletableFuture.supplyAsync(() -> moderateImages(images));
        
        CompletableFuture<CrossCheckResult> crossCheckFuture = 
            CompletableFuture.supplyAsync(() -> crossCheck(listing, images));
        
        // Wait for all stages
        CompletableFuture.allOf(textFuture, imageFuture, crossCheckFuture).join();
        
        TextModerationResult textResult = textFuture.join();
        List<ImageModerationResult> imageResults = imageFuture.join();
        CrossCheckResult crossCheckResult = crossCheckFuture.join();
        
        // Combine all violations
        List<Violation> allViolations = new ArrayList<>();
        allViolations.addAll(textResult.getViolations());
        imageResults.forEach(r -> allViolations.addAll(r.getViolations()));
        allViolations.addAll(crossCheckResult.getViolations());
        
        // Decision engine: max violation score determines outcome
        double maxScore = Math.max(
            textResult.getMaxScore(),
            Math.max(
                imageResults.stream().mapToDouble(ImageModerationResult::getMaxScore).max().orElse(0.0),
                crossCheckResult.getMaxScore()
            )
        );
        
        // Adjust thresholds by seller tier
        double approveThreshold = adjustThreshold(AUTO_APPROVE_THRESHOLD, seller.getTier());
        double rejectThreshold = adjustThreshold(AUTO_REJECT_THRESHOLD, seller.getTier());
        
        ModerationDecision decision;
        if (maxScore < approveThreshold) {
            decision = ModerationDecision.APPROVED;
        } else if (maxScore > rejectThreshold) {
            decision = ModerationDecision.REJECTED;
        } else {
            decision = ModerationDecision.NEEDS_HUMAN_REVIEW;
        }
        
        return new ModerationResult(decision, maxScore, allViolations, 
            null, Instant.now(), ModerationSource.AUTO_ML);
    }
    
    /**
     * TEXT MODERATION (~100ms):
     * - NLP classifier on title + description
     * - Prohibited keyword matching (context-aware, not just substring)
     * - Embeddings comparison against known-violation text patterns
     */
    private TextModerationResult moderateText(Listing listing) {
        String text = listing.getTitle() + " " + listing.getDescription();
        List<Violation> violations = new ArrayList<>();
        
        // 1. NLP classifier: trained on policy-violating listings
        double classifierScore = textClassifier.predict(text);
        
        // 2. Keyword matching (context-aware)
        // "hunting knife for camping" → maybe OK in sporting goods
        // "knife for sale, sharp blade" → suspicious
        Category category = categoryRepository.findById(listing.getCategory());
        List<String> matches = keywordMatcher.findProhibited(text, category);
        double keywordScore = matches.isEmpty() ? 0.0 : Math.min(matches.size() * 0.3, 1.0);
        
        // 3. Embedding similarity to known-violation patterns
        double embeddingScore = embeddingMatcher.similarityToViolations(text);
        
        double maxScore = Math.max(classifierScore, Math.max(keywordScore, embeddingScore));
        
        if (maxScore > 0.2) {
            violations.add(new Violation("POLICY_VIOLATION",
                "Text analysis flagged potential policy violation",
                "TEXT_ML", maxScore, "title+description"));
        }
        
        return new TextModerationResult(maxScore, violations);
    }
    
    /**
     * IMAGE MODERATION (~500ms per image, parallelized):
     * - Image classification model (ResNet/EfficientNet fine-tuned)
     * - Detects: weapons, drugs, explicit content, counterfeit logos
     * - Per-image violation score
     */
    private List<ImageModerationResult> moderateImages(List<ListingImage> images) {
        return images.parallelStream()
            .map(image -> {
                byte[] imageBytes = s3Client.getObject(CDN_BUCKET, image.getOriginalUrl());
                
                // Multi-label classification: one image can trigger multiple categories
                Map<String, Double> predictions = imageClassifier.classify(imageBytes);
                // predictions = { "weapon": 0.92, "drug": 0.01, "explicit": 0.03, "counterfeit": 0.05 }
                
                List<Violation> violations = new ArrayList<>();
                for (var entry : predictions.entrySet()) {
                    if (entry.getValue() > 0.2) {
                        violations.add(new Violation(
                            mapToViolationType(entry.getKey()),
                            String.format("%s detected in %s (confidence: %.0f%%)", 
                                entry.getKey(), image.getImageId(), entry.getValue() * 100),
                            "IMAGE_ML",
                            entry.getValue(),
                            image.getImageId()
                        ));
                    }
                }
                
                double maxScore = predictions.values().stream()
                    .mapToDouble(Double::doubleValue).max().orElse(0.0);
                
                return new ImageModerationResult(image.getImageId(), maxScore, violations);
            })
            .toList();
    }
    
    /**
     * CROSS-CHECK (~50ms):
     * - Title-image consistency
     * - Price reasonableness for category
     * - Similar to known-rejected listings (scam detection)
     */
    private CrossCheckResult crossCheck(Listing listing, List<ListingImage> images) {
        List<Violation> violations = new ArrayList<>();
        double maxScore = 0.0;
        
        // 1. Price anomaly: $10 for a "Rolex" → likely counterfeit
        double priceScore = priceAnomalyDetector.score(
            listing.getCategory(), listing.getPriceCents(), listing.getTitle());
        if (priceScore > 0.5) {
            violations.add(new Violation("COUNTERFEIT",
                "Price is unusually low for this category and title",
                "CROSS_CHECK", priceScore, "price_anomaly"));
            maxScore = Math.max(maxScore, priceScore);
        }
        
        // 2. Duplicate/scam: similar to previously rejected listings
        double dupScore = duplicateDetector.similarityToRejected(
            listing.getTitle(), listing.getDescription());
        if (dupScore > 0.5) {
            violations.add(new Violation("SUSPECTED_SCAM",
                "Listing is similar to previously rejected listings",
                "CROSS_CHECK", dupScore, "duplicate_check"));
            maxScore = Math.max(maxScore, dupScore);
        }
        
        return new CrossCheckResult(maxScore, violations);
    }
    
    /**
     * Seller tier adjusts thresholds:
     * TRUSTED: more lenient (higher approve threshold, fewer false rejects)
     * NEW: stricter (lower approve threshold, catch more edge cases)
     */
    private double adjustThreshold(double baseThreshold, SellerTier tier) {
        return switch (tier) {
            case TRUSTED -> baseThreshold + 0.1;     // More lenient
            case ESTABLISHED -> baseThreshold;         // Standard
            case NEW -> baseThreshold - 0.1;           // Stricter
        };
    }
}
```

**Moderation accuracy targets**:
```
False negatives (missed violations): < 1%
  → A weapon listing gets through = legal/PR risk
  → Conservative thresholds + human review for uncertain cases

False positives (wrongly rejected): < 5%
  → Legitimate listings rejected = seller frustration + lost revenue
  → Sellers can appeal; human reviews can override ML

Distribution:
  85% AUTO_APPROVE: ML confident it's clean → live in < 5 min
  5%  AUTO_REJECT:  ML confident it's a violation → instant rejection with details
  10% HUMAN_REVIEW: ML uncertain → queued for human moderator (SLA < 1 hr)

Feedback loop:
  Every human decision → training data for weekly ML model retrain
  Goal: reduce NEEDS_HUMAN_REVIEW from 10% → 5% over 6 months
  Track: agreement rate between ML and human decisions
```

---

### Deep Dive 4: Listing Lifecycle — State Machine with Edit & Resubmit

**The Problem**: Listing goes through multiple states. Rejected listings can be edited and resubmitted. Must track history, provide clear feedback at each transition, and prevent invalid state transitions.

```java
public class ListingLifecycleService {

    /**
     * State machine:
     * 
     *   DRAFT ──submit──→ PENDING_REVIEW ──images ready──→ PROCESSING
     *                                                         │
     *                             ┌───────────────────────────┤
     *                             │               │           │
     *                             ▼               ▼           ▼
     *                         APPROVED    NEEDS_HUMAN    REJECTED
     *                             │        REVIEW            │
     *                             │           │              │
     *                             │       ┌───┴───┐          │
     *                             │       ▼       ▼          │
     *                             │   APPROVED  REJECTED     │
     *                             │                          │
     *                             ▼                          ▼
     *                         (visible)               edit → DRAFT → resubmit
     * 
     * Valid transitions (anything else → throw IllegalStateTransitionException):
     *   DRAFT → PENDING_REVIEW          (seller submits)
     *   PENDING_REVIEW → PROCESSING     (all images ready)
     *   PROCESSING → APPROVED           (ML: max_score < 0.3)
     *   PROCESSING → REJECTED           (ML: max_score > 0.8)
     *   PROCESSING → NEEDS_HUMAN_REVIEW (ML: 0.3 ≤ score ≤ 0.8)
     *   NEEDS_HUMAN_REVIEW → APPROVED   (human approves)
     *   NEEDS_HUMAN_REVIEW → REJECTED   (human rejects)
     *   REJECTED → DRAFT                (seller edits and wants to resubmit)
     *   APPROVED → DRAFT                (seller edits an approved listing)
     */
    
    private static final Map<ListingStatus, Set<ListingStatus>> VALID_TRANSITIONS = Map.of(
        ListingStatus.DRAFT, Set.of(ListingStatus.PENDING_REVIEW),
        ListingStatus.PENDING_REVIEW, Set.of(ListingStatus.PROCESSING),
        ListingStatus.PROCESSING, Set.of(
            ListingStatus.APPROVED, ListingStatus.REJECTED, ListingStatus.NEEDS_HUMAN_REVIEW),
        ListingStatus.NEEDS_HUMAN_REVIEW, Set.of(ListingStatus.APPROVED, ListingStatus.REJECTED),
        ListingStatus.REJECTED, Set.of(ListingStatus.DRAFT),
        ListingStatus.APPROVED, Set.of(ListingStatus.DRAFT)  // Edit an approved listing
    );
    
    @Transactional
    public Listing transitionStatus(String listingId, ListingStatus newStatus, 
                                     ModerationResult moderation) {
        Listing listing = listingRepository.findByIdForUpdate(listingId); // SELECT FOR UPDATE
        
        // Validate transition
        Set<ListingStatus> allowed = VALID_TRANSITIONS.get(listing.getStatus());
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new IllegalStateTransitionException(
                String.format("Cannot transition from %s to %s", listing.getStatus(), newStatus));
        }
        
        // Apply transition
        listing.setStatus(newStatus);
        listing.setUpdatedAt(Instant.now());
        
        if (newStatus == ListingStatus.APPROVED) {
            listing.setApprovedAt(Instant.now());
        }
        
        if (newStatus == ListingStatus.DRAFT && listing.getStatus() == ListingStatus.REJECTED) {
            // Resubmit: increment version for new moderation pass
            listing.setVersion(listing.getVersion() + 1);
        }
        
        // Save moderation result if provided
        if (moderation != null) {
            moderationRepository.save(new ModerationResultEntity(
                listingId, moderation, listing.getVersion()));
        }
        
        listingRepository.save(listing);
        
        // Publish events based on new status
        publishStatusEvent(listing, newStatus, moderation);
        
        return listing;
    }
    
    private void publishStatusEvent(Listing listing, ListingStatus newStatus, 
                                     ModerationResult moderation) {
        switch (newStatus) {
            case APPROVED -> {
                // Index in search + notify seller
                kafkaProducer.send("listing-approved", new ListingApprovedEvent(listing));
                kafkaProducer.send("seller-notifications", new SellerNotification(
                    listing.getSellerId(), "Your listing '" + listing.getTitle() + "' is now live! 🎉",
                    NotificationType.LISTING_APPROVED));
            }
            case REJECTED -> {
                // Remove from search + notify seller with violation details
                kafkaProducer.send("listing-removed", new ListingRemovedEvent(listing.getListingId()));
                String violationSummary = moderation.getViolations().stream()
                    .map(v -> v.getDescription())
                    .collect(Collectors.joining("; "));
                kafkaProducer.send("seller-notifications", new SellerNotification(
                    listing.getSellerId(), 
                    "Rejected: " + violationSummary + ". Please edit and resubmit.",
                    NotificationType.LISTING_REJECTED));
            }
            case NEEDS_HUMAN_REVIEW -> {
                kafkaProducer.send("human-review-queue", new HumanReviewRequest(listing));
                kafkaProducer.send("seller-notifications", new SellerNotification(
                    listing.getSellerId(),
                    "Your listing is under additional review. Expected: < 1 hour.",
                    NotificationType.LISTING_UNDER_REVIEW));
            }
            case PENDING_REVIEW -> {
                kafkaProducer.send("listings-pending-review", new ListingPendingEvent(listing));
            }
            default -> {} // DRAFT, PROCESSING: no external events needed
        }
    }
}
```

---

### Deep Dive 5: Presigned URL Upload — Direct Client-to-S3

**The Problem**: 5M images/day at 10 MB average = 50 TB/day of upload bandwidth. Proxying through our servers is expensive, slow, and a scaling bottleneck. We need a way to let clients upload directly to object storage.

```java
public class ImageUploadService {

    /**
     * Presigned URL flow:
     * 
     * 1. Client requests upload URL → Server validates + generates presigned S3 PUT URL
     * 2. Client uploads directly to S3 (bypasses our servers completely)
     * 3. S3 event notification → triggers image processing pipeline
     * 
     * Benefits:
     *   - Server NEVER handles image bytes (saves 50 TB/day bandwidth)
     *   - Infinite upload throughput (S3 handles it)
     *   - Presigned URL expires in 5 min (secure)
     *   - Parallel uploads from client (upload 10 images simultaneously)
     * 
     * Security:
     *   - Presigned URL is scoped: specific bucket, key, content-type, max size
     *   - Expires in 300 seconds (5 min)
     *   - Only PUT allowed (no GET, DELETE, LIST)
     *   - Content-Type must match (can't upload .exe as image/jpeg)
     */
    
    private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024; // 20 MB
    private static final int MAX_IMAGES_PER_LISTING = 10;
    private static final int PRESIGNED_URL_EXPIRY_SECONDS = 300;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    
    public PresignedUploadResponse requestUploadUrl(String listingId, String sellerId,
                                                     String filename, String contentType,
                                                     long sizeBytes) {
        // Validate listing ownership
        Listing listing = listingRepository.findById(listingId);
        if (!listing.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("Listing does not belong to this seller");
        }
        
        // Validate listing status (can only upload to DRAFT listings)
        if (listing.getStatus() != ListingStatus.DRAFT) {
            throw new InvalidStateException("Can only upload images to DRAFT listings");
        }
        
        // Validate image count
        int currentImageCount = imageRepository.countByListingId(listingId);
        if (currentImageCount >= MAX_IMAGES_PER_LISTING) {
            throw new LimitExceededException(
                "Maximum " + MAX_IMAGES_PER_LISTING + " images per listing");
        }
        
        // Validate content type
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidInputException("Unsupported content type: " + contentType);
        }
        
        // Validate size
        if (sizeBytes > MAX_IMAGE_SIZE) {
            throw new InvalidInputException("Image too large. Maximum: 20 MB");
        }
        
        // Generate image ID and S3 key
        String imageId = UUID.randomUUID().toString();
        String s3Key = String.format("raw/%s/%s/%s", listingId, imageId, sanitizeFilename(filename));
        
        // Generate presigned PUT URL
        GeneratePresignedUrlRequest presignRequest = new GeneratePresignedUrlRequest(
            UPLOAD_BUCKET, s3Key)
            .withMethod(HttpMethod.PUT)
            .withContentType(contentType)
            .withExpiration(Date.from(Instant.now().plusSeconds(PRESIGNED_URL_EXPIRY_SECONDS)));
        
        // Set content-length range (prevent uploading larger file than declared)
        presignRequest.putCustomRequestHeader("x-amz-content-length-range", 
            "1," + sizeBytes);
        
        URL presignedUrl = s3Client.generatePresignedUrl(presignRequest);
        
        // Create image record in DB (status: UPLOADING)
        ListingImage image = new ListingImage(
            imageId, listingId, s3Key, null, null, null,
            ImageStatus.UPLOADING, currentImageCount, sizeBytes, contentType, Instant.now());
        imageRepository.save(image);
        
        return new PresignedUploadResponse(
            imageId, presignedUrl.toString(), PRESIGNED_URL_EXPIRY_SECONDS, MAX_IMAGE_SIZE);
    }
    
    /**
     * Called when S3 event notification confirms upload completed.
     * Triggers the image processing pipeline via Kafka.
     */
    public void onS3UploadComplete(S3EventNotification event) {
        String s3Key = event.getRecords().get(0).getS3().getObject().getKey();
        
        // Extract imageId from key: "raw/{listingId}/{imageId}/{filename}"
        String[] parts = s3Key.split("/");
        String listingId = parts[1];
        String imageId = parts[2];
        
        // Verify the image record exists
        ListingImage image = imageRepository.findById(imageId);
        if (image == null || image.getStatus() != ImageStatus.UPLOADING) {
            log.warn("Unexpected S3 upload event for image {}", imageId);
            return;
        }
        
        // Update original URL
        String originalUrl = String.format("s3://%s/%s", UPLOAD_BUCKET, s3Key);
        imageRepository.updateOriginalUrl(imageId, originalUrl);
        
        // Trigger image processing pipeline
        kafkaProducer.send("image-uploaded", new ImageUploadedEvent(
            imageId, listingId, s3Key, image.getContentType()));
    }
    
    private String sanitizeFilename(String filename) {
        // Remove special characters, keep extension
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
```

**Upload performance comparison**:
```
Without presigned URLs (server proxy):
  Client → Server → S3
  Server bandwidth: 50 TB/day = 4.6 Gbps sustained
  Server CPU: TLS termination + stream forwarding
  Cost: ~$500/day in bandwidth + compute
  Latency: +200-500ms per upload (extra hop)

With presigned URLs (direct to S3):
  Client → S3 (direct)
  Server bandwidth: ~0 (only metadata requests)
  Server CPU: ~0 for uploads (only generates presigned URLs)
  Cost: S3 PUT requests only (~$25/day for 5M uploads)
  Latency: direct S3 speed (fastest path)

Savings: ~95% reduction in server bandwidth and upload compute cost
```

---

### Deep Dive 6: Search & Discovery — Elasticsearch for Catalog Queries

**The Problem**: 100M listings searchable by keyword, filterable by category/price/location, sorted by relevance or recency. Only APPROVED listings visible to buyers. Must support faceted search (counts per category) and < 200ms latency.

```java
public class CatalogSearchService {

    /**
     * Elasticsearch index: "listings"
     * 
     * Only APPROVED listings are indexed. On approval → index, on rejection → remove.
     * 
     * Document structure:
     * {
     *   "listing_id": "lst_a1b2c3d4",
     *   "title": "Vintage Leather Jacket",
     *   "description": "Genuine leather, size M, brown...",
     *   "category": ["clothing", "mens", "jackets"],  // Hierarchical array
     *   "price_cents": 12500,
     *   "currency": "USD",
     *   "location": { "lat": 40.68, "lng": -73.94 },
     *   "seller_id": "seller_001",
     *   "seller_name": "Alice",
     *   "seller_reputation": 4.8,
     *   "tags": ["vintage", "leather"],
     *   "thumbnail_url": "https://cdn/.../thumb.webp",
     *   "created_at": "2025-01-10T10:00:00Z",
     *   "approved_at": "2025-01-10T10:02:00Z"
     * }
     * 
     * Relevance scoring:
     *   - Title match: 3× weight (most important signal)
     *   - Description match: 1× weight
     *   - Seller reputation: +0.1 boost per star
     *   - Recency: exponential decay (newer listings rank higher)
     */
    
    public SearchResult search(String query, String category, Integer priceMaxCents,
                                String sortBy, int limit, String cursor) {
        
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        
        // Full-text search on title (3× boost) and description
        if (query != null && !query.isBlank()) {
            boolQuery.must(QueryBuilders.multiMatchQuery(query, "title^3", "description")
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO));
        }
        
        // Category filter (hierarchical: "clothing" matches "clothing/mens/jackets")
        if (category != null) {
            boolQuery.filter(QueryBuilders.termQuery("category", category));
        }
        
        // Price filter
        if (priceMaxCents != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price_cents").lte(priceMaxCents));
        }
        
        // Build search request
        SearchSourceBuilder source = new SearchSourceBuilder()
            .query(boolQuery)
            .size(limit);
        
        // Sorting
        if ("relevance".equals(sortBy) || sortBy == null) {
            // Default: relevance score + recency decay + seller reputation boost
            source.query(QueryBuilders.functionScoreQuery(boolQuery,
                new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                    // Recency boost: newer listings score higher (half-life = 30 days)
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.exponentialDecayFunction("approved_at", "now", "30d")),
                    // Seller reputation boost: +0.1 per star
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.fieldValueFactorFunction("seller_reputation")
                            .modifier(FieldValueFactorFunction.Modifier.LN1P)
                            .factor(0.1f))
                }).scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                  .boostMode(CombineFunction.MULTIPLY));
        } else if ("newest".equals(sortBy)) {
            source.sort("approved_at", SortOrder.DESC);
        } else if ("price_low".equals(sortBy)) {
            source.sort("price_cents", SortOrder.ASC);
        } else if ("price_high".equals(sortBy)) {
            source.sort("price_cents", SortOrder.DESC);
        }
        
        // Pagination via search_after (cursor-based, better than offset for deep pagination)
        if (cursor != null) {
            Object[] searchAfter = decodeCursor(cursor);
            source.searchAfter(searchAfter);
        }
        
        // Facets: count per category (for filter sidebar)
        source.aggregation(AggregationBuilders.terms("categories").field("category").size(20));
        source.aggregation(AggregationBuilders.range("price_ranges").field("price_cents")
            .addRange("Under $25", 0, 2500)
            .addRange("$25-$50", 2500, 5000)
            .addRange("$50-$100", 5000, 10000)
            .addRange("$100-$250", 10000, 25000)
            .addRange("Over $250", 25000, Double.MAX_VALUE));
        
        SearchRequest request = new SearchRequest("listings").source(source);
        SearchResponse response = esClient.search(request);
        
        // Map results
        List<ListingSearchResult> results = Arrays.stream(response.getHits().getHits())
            .map(hit -> mapToSearchResult(hit.getSourceAsMap()))
            .toList();
        
        // Build cursor for next page
        SearchHit[] hits = response.getHits().getHits();
        String nextCursor = hits.length > 0 
            ? encodeCursor(hits[hits.length - 1].getSortValues()) 
            : null;
        
        return new SearchResult(results, response.getHits().getTotalHits().value,
            nextCursor, extractFacets(response.getAggregations()));
    }
    
    /**
     * Index a newly approved listing into Elasticsearch.
     * Called by Search Index Updater (Kafka consumer for "listing-approved").
     */
    public void indexListing(ListingApprovedEvent event) {
        Listing listing = event.getListing();
        Seller seller = sellerRepository.findById(listing.getSellerId());
        ListingImage primaryImage = imageRepository.findPrimary(listing.getListingId());
        
        Map<String, Object> doc = Map.of(
            "listing_id", listing.getListingId(),
            "title", listing.getTitle(),
            "description", listing.getDescription(),
            "category", getCategoryHierarchy(listing.getCategory()),
            "price_cents", listing.getPriceCents(),
            "currency", listing.getCurrency(),
            "location", getGeoPoint(listing.getLocation()),
            "seller_id", seller.getSellerId(),
            "seller_name", seller.getName(),
            "seller_reputation", seller.getReputationScore(),
            "tags", listing.getTags(),
            "thumbnail_url", primaryImage != null ? primaryImage.getThumbnailUrl() : "",
            "created_at", listing.getCreatedAt(),
            "approved_at", listing.getApprovedAt()
        );
        
        IndexRequest indexReq = new IndexRequest("listings")
            .id(listing.getListingId())
            .source(doc);
        esClient.index(indexReq);
    }
    
    /**
     * Remove a listing from search index (on rejection or edit).
     */
    public void removeListing(String listingId) {
        DeleteRequest deleteReq = new DeleteRequest("listings").id(listingId);
        esClient.delete(deleteReq);
    }
}
```

**Search performance**:
```
Index size: 100M listings × ~500 bytes/doc = ~50 GB
  With inverted index overhead: ~100 GB across 3 ES nodes

Query: "leather jacket" + category=clothing + price < $200 + sort=relevance
  → Multi-match query + term filter + range filter + function_score
  → Elasticsearch: < 50ms for typical query

Facets: category counts + price range counts
  → Terms aggregation + range aggregation
  → Computed alongside search (same request)

Cursor-based pagination (search_after):
  → O(1) for any page depth (no deep offset performance issue)
  → Client stores opaque cursor token, sends with next request
```

---

### Deep Dive 7: Human Moderation Queue — Edge Cases at Scale

**The Problem**: 10% of listings (100K/day) need human review. Must be efficient (minimize moderator time per listing), fair (FIFO with priority), and feed back into ML improvement.

```java
public class HumanModerationService {

    /**
     * Queue implementation: Redis Sorted Set
     * 
     * Score = priority (lower = higher priority):
     *   NEW seller listings → priority 1 (review first — higher risk)
     *   High-violation-score → priority 2
     *   Standard → priority 3
     *   TRUSTED seller → priority 4 (review last — lower risk)
     * 
     * Within same priority: FIFO by timestamp (earliest first).
     * Combined score: priority * 10^10 + unix_timestamp
     * 
     * Queue capacity: 100K listings/day = ~4K/hour
     * Moderators needed: 4K/hour / (30 reviews/hour/moderator) = ~135 moderators
     * SLA: 95% reviewed within 1 hour
     */
    
    private static final String QUEUE_KEY = "moderation:queue";
    
    public void enqueue(Listing listing, ModerationResult mlResult) {
        int priority = calculatePriority(listing, mlResult);
        
        // Score: priority * 10^10 + timestamp (ensures FIFO within priority)
        double score = priority * 10_000_000_000L + Instant.now().getEpochSecond();
        
        String queueEntry = objectMapper.writeValueAsString(new ModerationQueueEntry(
            listing.getListingId(),
            listing.getSellerId(),
            listing.getTitle(),
            mlResult.getConfidenceScore(),
            mlResult.getViolations(),
            Instant.now()
        ));
        
        redis.zadd(QUEUE_KEY, score, queueEntry);
        
        // Track queue depth metric
        metrics.gauge("moderation.queue.depth", redis.zcard(QUEUE_KEY));
    }
    
    /**
     * Moderator fetches next listing to review.
     * Uses ZPOPMIN to atomically get and remove lowest-score entry (highest priority).
     */
    public ModerationQueueEntry getNextForReview(String moderatorId) {
        // Atomic pop: get + remove in one operation (no race condition between moderators)
        Set<ZSetOperations.TypedTuple<String>> entries = redis.zpopmin(QUEUE_KEY, 1);
        
        if (entries.isEmpty()) {
            return null; // Queue is empty
        }
        
        String json = entries.iterator().next().getValue();
        ModerationQueueEntry entry = objectMapper.readValue(json, ModerationQueueEntry.class);
        
        // Lock: assign to moderator (prevent double-review)
        redis.setex("moderation:lock:" + entry.getListingId(), 3600, moderatorId);
        
        // Load full listing + images for display
        entry.setFullListing(listingRepository.findById(entry.getListingId()));
        entry.setImages(imageRepository.findByListingId(entry.getListingId()));
        entry.setMlScores(moderationRepository.findLatest(entry.getListingId()));
        entry.setSimilarPastDecisions(findSimilarDecisions(entry.getListingId(), 5));
        
        return entry;
    }
    
    /**
     * Moderator submits decision.
     * Updates listing status + feeds back to ML model.
     */
    public void submitDecision(String listingId, String moderatorId,
                                ModerationDecision decision, String reason) {
        // Verify lock
        String lockedBy = redis.get("moderation:lock:" + listingId);
        if (!moderatorId.equals(lockedBy)) {
            throw new ConflictException("Listing is assigned to a different moderator");
        }
        
        // Create moderation result
        ModerationResult result = new ModerationResult(
            decision, 1.0, // Human decision = confidence 1.0
            decision == ModerationDecision.REJECTED 
                ? List.of(new Violation("HUMAN_FLAGGED", reason, "HUMAN", 1.0, "moderator"))
                : List.of(),
            moderatorId, Instant.now(), ModerationSource.HUMAN
        );
        
        // Transition listing status
        ListingStatus newStatus = decision == ModerationDecision.APPROVED 
            ? ListingStatus.APPROVED : ListingStatus.REJECTED;
        listingLifecycleService.transitionStatus(listingId, newStatus, result);
        
        // Release lock
        redis.del("moderation:lock:" + listingId);
        
        // FEEDBACK LOOP: send to ML training pipeline
        // Human decision + original ML scores = labeled training data
        kafkaProducer.send("ml-feedback", new MLFeedbackEvent(
            listingId, decision, result, 
            moderationRepository.findLatestML(listingId))); // Original ML scores
        
        // Update moderator stats
        moderatorStatsService.recordDecision(moderatorId, decision);
    }
    
    private int calculatePriority(Listing listing, ModerationResult mlResult) {
        Seller seller = sellerRepository.findById(listing.getSellerId());
        
        // NEW sellers with high violation scores → highest priority (most risky)
        if (seller.getTier() == SellerTier.NEW && mlResult.getConfidenceScore() > 0.6) {
            return 1;
        }
        // High violation score from any seller
        if (mlResult.getConfidenceScore() > 0.6) {
            return 2;
        }
        // Standard cases
        if (seller.getTier() != SellerTier.TRUSTED) {
            return 3;
        }
        // Trusted sellers → lowest priority
        return 4;
    }
    
    /**
     * Find similar past moderation decisions to assist moderator.
     * Uses title/description embeddings to find nearest neighbors.
     */
    private List<SimilarDecision> findSimilarDecisions(String listingId, int limit) {
        Listing listing = listingRepository.findById(listingId);
        float[] embedding = embeddingService.embed(listing.getTitle() + " " + listing.getDescription());
        
        // Search vector DB (or ES KNN) for similar previously-moderated listings
        return vectorSearch.findNearest(embedding, limit).stream()
            .map(match -> new SimilarDecision(
                match.getListingId(),
                match.getTitle(),
                match.getDecision(),
                match.getSimilarityScore()))
            .toList();
    }
}
```

**Queue metrics**:
```
Queue depth: ~4K listings (1 hour of backlog at steady state)
Moderator capacity: 135 moderators × 30 reviews/hour = 4,050/hour
SLA compliance: 95% < 1 hour (tracked, alerted if slipping)

Moderator dashboard shows:
  1. Listing title + description
  2. All images with ML-flagged regions highlighted
  3. ML confidence scores per violation category
  4. 5 similar past decisions (helps calibrate judgment)
  5. Seller history: past listings, rejection rate, tier
  6. Quick actions: APPROVE / REJECT + reason dropdown

Feedback loop impact:
  Week 1: 10% human review rate
  Month 3: 7% (ML learns from human decisions)
  Month 6: 5% (goal)
  Metric: ML-human agreement rate > 90%
```

---

### Deep Dive 8: Seller Experience — Real-Time Status Feedback

**The Problem**: Seller submits listing and wants to know what's happening. "Is it stuck? Why is it taking so long?" Without feedback, sellers feel anxious and submit support tickets. We need a real-time progress indicator.

```java
public class SellerStatusService {

    /**
     * Three approaches for real-time status:
     * 
     * Option A: POLLING (simple, works everywhere)
     *   Client polls GET /listings/{id}/status every 5 seconds
     *   Pros: Simple, works on all clients, no connection state
     *   Cons: Wasteful (many requests with no change), 5s latency
     * 
     * Option B: SERVER-SENT EVENTS / SSE (chosen for v1)
     *   Client opens GET /listings/{id}/status/stream
     *   Server pushes updates as they happen
     *   Pros: Real-time, one connection per listing, simple protocol
     *   Cons: One-directional, connection limits per browser
     * 
     * Option C: WEBSOCKET (most capable)
     *   Client opens WS connection for bidirectional communication
     *   Pros: Full duplex, lowest latency
     *   Cons: More complex, stateful connections, harder to scale
     * 
     * Choice: SSE for v1 (real-time, simple), with polling fallback.
     * 
     * Implementation: Redis Pub/Sub for internal event routing.
     * Each listing gets a channel: "status:{listing_id}"
     * SSE handler subscribes to channel and pushes events to client.
     */
    
    public void handleSSEConnection(String listingId, String sellerId, 
                                     SseEmitter emitter) {
        // Verify ownership
        Listing listing = listingRepository.findById(listingId);
        if (!listing.getSellerId().equals(sellerId)) {
            emitter.completeWithError(new ForbiddenException("Not your listing"));
            return;
        }
        
        // Send current status immediately
        emitter.send(SseEmitter.event()
            .name("current_status")
            .data(buildStatusSnapshot(listing)));
        
        // Subscribe to Redis channel for updates
        String channel = "status:" + listingId;
        
        MessageListener listener = (message, pattern) -> {
            try {
                StatusEvent event = objectMapper.readValue(message.getBody(), StatusEvent.class);
                emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(event.getData()));
                
                // If terminal status, complete the SSE stream
                if (event.isTerminal()) {
                    emitter.complete();
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        };
        
        redis.subscribe(listener, channel);
        
        // Cleanup on disconnect
        emitter.onCompletion(() -> redis.unsubscribe(listener, channel));
        emitter.onTimeout(() -> redis.unsubscribe(listener, channel));
        
        // 10-minute timeout (listing should be moderated by then)
        emitter.setTimeout(600_000L);
    }
    
    /**
     * Internal: publish status events from pipeline stages.
     * Called by Image Processor, Moderation Service, etc.
     */
    public void publishStatusUpdate(String listingId, String eventType, 
                                     Map<String, Object> data) {
        StatusEvent event = new StatusEvent(eventType, data, isTerminal(eventType));
        String channel = "status:" + listingId;
        
        redis.convertAndSend(channel, objectMapper.writeValueAsString(event));
    }
    
    private StatusSnapshot buildStatusSnapshot(Listing listing) {
        List<ListingImage> images = imageRepository.findByListingId(listing.getListingId());
        
        long imagesReady = images.stream().filter(i -> i.getStatus() == ImageStatus.READY).count();
        long imagesProcessing = images.stream().filter(i -> i.getStatus() == ImageStatus.PROCESSING).count();
        
        List<StatusStep> steps = new ArrayList<>();
        steps.add(new StatusStep("listing_created", "Listing created", true));
        steps.add(new StatusStep("images_uploaded", 
            String.format("Images uploaded (%d/%d)", images.size(), images.size()), 
            !images.isEmpty()));
        steps.add(new StatusStep("images_processed",
            String.format("Images processed (%d/%d ready)", imagesReady, images.size()),
            imagesProcessing == 0 && imagesReady == images.size()));
        
        boolean moderationStarted = listing.getStatus() == ListingStatus.PROCESSING
            || listing.getStatus() == ListingStatus.APPROVED
            || listing.getStatus() == ListingStatus.REJECTED;
        steps.add(new StatusStep("moderation", "Checking compliance...", moderationStarted));
        
        boolean done = listing.getStatus() == ListingStatus.APPROVED 
            || listing.getStatus() == ListingStatus.REJECTED;
        String finalMessage = listing.getStatus() == ListingStatus.APPROVED
            ? "Approved! Your listing is live. 🎉"
            : listing.getStatus() == ListingStatus.REJECTED
                ? "Rejected. Please review the feedback and resubmit."
                : "In progress...";
        steps.add(new StatusStep("result", finalMessage, done));
        
        return new StatusSnapshot(listing.getListingId(), listing.getStatus(), steps);
    }
    
    private boolean isTerminal(String eventType) {
        return "listing_approved".equals(eventType) || "listing_rejected".equals(eventType);
    }
}
```

**Progress timeline shown to seller**:
```
✅ Listing created
✅ Images uploaded (5/5)
✅ Images processed (5/5 ready)
⏳ Checking compliance...        ← currently here
○ Result

Progress bar: ████████████░░░░ 75%
Estimated time remaining: ~1 minute

--- On approval: ---
✅ Listing created
✅ Images uploaded (5/5)
✅ Images processed (5/5 ready)
✅ Compliance check passed
✅ Approved! Your listing is live. 🎉
   [View Listing]  [Share]

--- On rejection: ---
✅ Listing created
✅ Images uploaded (5/5)
✅ Images processed (5/5 ready)
❌ Rejected: Weapon-like object detected in image 2.
   [Edit Listing]  [Contact Support]  [Appeal]
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Image upload** | S3 presigned URLs (direct client-to-S3) | Server never handles image bytes; infinite upload throughput; saves 50 TB/day bandwidth |
| **Image processing** | libvips (resize/compress), Kafka-driven pipeline | 10× faster than ImageMagick; horizontally scalable via Kafka consumers; auto-scale on lag |
| **Moderation** | ML auto-classify (text+image+cross-check) + human queue | 85% auto-approved (fast UX); 5% auto-rejected (safety); 10% human review (safety net) |
| **Decision thresholds** | < 0.3 approve, > 0.8 reject, middle → human | Balances UX (low false positives) with safety (low false negatives); seller-tier adjusted |
| **Listing lifecycle** | State machine: DRAFT → PENDING → APPROVED/REJECTED | Clear seller feedback at each step; edit + resubmit flow; only APPROVED visible to buyers |
| **Search** | Elasticsearch with Kafka-driven indexing | Full-text + facets + relevance scoring + geo; cursor-based pagination; only APPROVED indexed |
| **Seller feedback** | SSE (v1) with polling fallback | Real-time progress timeline; Redis Pub/Sub for internal routing; 10-min timeout |
| **Scaling** | Kafka buffers + auto-scaling consumers per stage | Decoupled stages scale independently; backpressure handled gracefully via Kafka consumer lag |
| **DB choice** | PostgreSQL for listings + ES for search | Strong consistency for listing writes; eventual consistency for search is acceptable |
| **Human moderation queue** | Redis Sorted Set with priority scoring | O(log N) enqueue/dequeue; priority by seller risk; FIFO within priority; ~135 moderators needed |

## Interview Talking Points

1. **"S3 presigned URLs: server never touches image bytes"** — Client uploads 10 MB images directly to S3. Saves 50 TB/day of server bandwidth. S3 event triggers image processing pipeline via Kafka. Presigned URL expires in 5 min, scoped to specific bucket/key/content-type.

2. **"3-tier moderation: text ML + image ML + cross-check — all parallel"** — Text classifier (100ms) + image classifier (500ms) + price/duplicate cross-check (50ms) run concurrently via CompletableFuture. Max violation score determines outcome: < 0.3 auto-approve (85%), > 0.8 auto-reject (5%), middle human review (10%). Seller tier adjusts thresholds.

3. **"Listing state machine with 6 states and validated transitions"** — Map of valid transitions enforced in code. SELECT FOR UPDATE for concurrency. Each transition publishes Kafka events (index in ES, notify seller, queue for review). Version tracking for edit + resubmit flow.

4. **"Image processing: libvips for 3 sizes + WebP compression in < 10s"** — 5M images/day processed by Kafka consumers. Auto-scale 20-100 pods based on consumer lag. Spot instances for 80% of capacity. Magic byte validation (not just file extension). Parallel upload of 3 resized versions to CDN.

5. **"Elasticsearch for catalog search: only index APPROVED listings"** — Kafka consumer for "listing-approved" indexes to ES. Full-text + category facets + price range + geo + relevance scoring (title 3x boost + recency decay + seller reputation). Cursor-based pagination via search_after. Remove on rejection.

6. **"Human moderation: Redis Sorted Set priority queue with ML feedback loop"** — 100K reviews/day. ZPOPMIN for atomic dequeue. Priority: new sellers + high violation score first. Moderator dashboard shows ML scores + 5 similar past decisions. Every human decision becomes ML training data. Goal: reduce human queue from 10% to 5%.

7. **"SSE for real-time seller status with Redis Pub/Sub routing"** — Each listing gets a Redis channel. Pipeline stages publish events. SSE handler subscribes and pushes to client. Progress timeline: created → uploaded → processed → compliance check → result. 10-min timeout with polling fallback.

8. **"Kafka decouples 5 pipeline stages that scale independently"** — Image upload (S3), image processing (CPU pods), moderation (GPU pods), search indexing (ES), notifications — each auto-scales on its own metric. Backpressure handled by Kafka buffering. No stage blocks another.

---

## Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Etsy / eBay Listings** | Our exact design | Same listing flow + moderation + marketplace search |
| **Instagram Post Upload** | Similar image pipeline | No moderation decision gate; no marketplace search |
| **Content Moderation (Facebook)** | Same ML pipeline | We add listing lifecycle + marketplace search + seller UX |
| **Google Drive Upload** | File upload + processing | No moderation or marketplace; different access patterns |
| **Image CDN (Cloudinary)** | Same image processing | No listing metadata, moderation, or search |
| **E-Commerce Search (Amazon)** | Same search pattern | More complex ranking; inventory management; reviews |

## Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Image processing** | libvips | ImageMagick / Sharp (Node.js) / Pillow (Python) | Simpler setup; lower throughput acceptable |
| **Image moderation ML** | Custom fine-tuned model | AWS Rekognition / Google Vision API / Azure Content Moderator | Faster to market; no ML team; higher per-image cost |
| **Search** | Elasticsearch | Algolia / Typesense / PostgreSQL full-text | Algolia: hosted, simpler ops; PG: small scale, fewer dependencies |
| **Image storage** | S3 + CloudFront | GCS + Cloud CDN / Azure Blob + CDN | Multi-cloud or GCP/Azure-first shops |
| **Event pipeline** | Kafka | SQS / RabbitMQ / Google Pub/Sub | SQS: simpler, no Kafka ops; RabbitMQ: lower throughput |
| **Listing DB** | PostgreSQL | DynamoDB / CockroachDB | DynamoDB: serverless, infinite scale; CockroachDB: global distribution |
| **Real-time feedback** | SSE + Redis Pub/Sub | WebSocket / Long polling / Firebase | WebSocket: bidirectional needed; Firebase: mobile-first, managed |
| **Human moderation queue** | Redis Sorted Set | PostgreSQL + polling / SQS FIFO | PG: simpler, fewer dependencies; SQS: managed, auto-scale |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Etsy Listing Architecture and Content Moderation
- S3 Presigned URL Best Practices (AWS Documentation)
- ML Content Moderation at Scale (Meta Engineering Blog)
- Elasticsearch for E-Commerce Search Patterns
- libvips Image Processing Performance Benchmarks
- Redis Sorted Sets for Priority Queues
- Server-Sent Events (SSE) for Real-Time Updates
- Kafka Consumer Auto-Scaling Patterns
