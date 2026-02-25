# Design a Product Catalog System - Hello Interview Framework

> **Question**: Design a product catalog system that allows users to upload items with descriptions and images, while automatically validating uploads to reject illegal items like weapons and providing success/failure feedback. Think Etsy or eBay's listing flow: create a product, attach photos, and the system decides if it complies with policy before it appears to others.
>
> **Asked at**: Meta
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

## 1️⃣ Requirements

### Functional (P0)
1. **Product Listing Creation**: Sellers create listings with: title, description, price, category, images (up to 10). Multi-step upload flow.
2. **Image Upload & Processing**: Upload images (up to 20 MB each). Resize, compress, generate thumbnails. Store in CDN for fast delivery.
3. **Content Validation/Moderation**: Automatically scan listings for policy violations: prohibited items (weapons, drugs, counterfeit), explicit content, misleading descriptions. Provide clear accept/reject feedback.
4. **Listing Lifecycle**: DRAFT → PENDING_REVIEW → APPROVED (visible to buyers) / REJECTED (with reason). Sellers can edit and resubmit rejected listings.
5. **Catalog Browse & Search**: Buyers browse by category, search by keyword, filter by price/location. Only APPROVED listings visible.
6. **Seller Feedback**: Real-time status updates: "Processing images...", "Checking compliance...", "Approved!" or "Rejected: weapon detected in image 3."

### Functional (P1)
- Human moderation queue for edge cases (low-confidence ML decisions)
- Seller reputation score (affects moderation strictness)
- Listing versioning (edit history)
- Bulk upload (CSV import for large sellers)
- Rich media: video uploads, 360° images
- Inventory management (quantity, variants like size/color)

### Non-Functional Requirements
| Attribute | Target |
|-----------|--------|
| **Upload-to-Live** | < 5 minutes (image processing + moderation) |
| **Image Processing** | < 30 seconds per image (resize + thumbnails) |
| **Moderation Latency** | < 2 minutes for auto-moderation |
| **Search Latency** | < 200ms for catalog queries |
| **Availability** | 99.9% for reads, 99.5% for uploads |
| **Scale** | 100M listings, 1M new/day, 50M images/day |
| **Storage** | Images: 5 PB total (100M listings × 5 images × 10 MB avg) |

### Capacity Estimation
```
Listings: 100M total, 1M new/day = ~12/sec sustained, 50/sec peak
Images: 5 images/listing avg × 1M = 5M images/day = ~58 uploads/sec
  Average image: 10 MB → 50 TB/day raw uploads
  After resize (3 sizes): 50 TB × 3 = 150 TB/day processed
  CDN: ~5 PB total image storage

Moderation: 1M listings/day = ~12/sec
  ML inference: ~500ms per listing (text + images)
  ML capacity: 12/sec × 0.5s = 6 concurrent inference → modest GPU need

Search: 10M searches/day = ~116/sec, peak 500/sec
Catalog reads: 50M product views/day = ~580/sec

Storage:
  Listing metadata: 100M × 2 KB = 200 GB
  Images: ~5 PB (CDN/S3)
  Search index: ~50 GB (Elasticsearch)
```

---

## 2️⃣ Core Entities

```java
public class Listing {
    String listingId;           // UUID
    String sellerId;
    String title;
    String description;
    int priceCents;
    String currency;            // "USD"
    String category;            // "electronics", "clothing", etc.
    List<String> tags;
    List<ListingImage> images;
    ListingStatus status;       // DRAFT, PENDING_REVIEW, APPROVED, REJECTED
    ModerationResult moderation;// Why approved/rejected
    int quantity;
    String location;            // Seller's city/country
    Instant createdAt;
    Instant updatedAt;
    Instant approvedAt;
    int version;
}
public enum ListingStatus { DRAFT, PENDING_REVIEW, PROCESSING, APPROVED, REJECTED }

public class ListingImage {
    String imageId;
    String listingId;
    String originalUrl;         // S3 URL (original upload)
    String thumbnailUrl;        // 200×200
    String mediumUrl;           // 800×800
    String largeUrl;            // 1600×1600
    ImageProcessingStatus status; // UPLOADING, PROCESSING, READY, FAILED
    ModerationResult moderation;  // Per-image moderation result
    int sortOrder;
    Instant uploadedAt;
}
public enum ImageProcessingStatus { UPLOADING, PROCESSING, READY, FAILED }

public class ModerationResult {
    ModerationDecision decision;// APPROVED, REJECTED, NEEDS_HUMAN_REVIEW
    double confidenceScore;     // 0.0 - 1.0
    List<Violation> violations;
    String moderatorId;         // Human moderator (if applicable)
    Instant reviewedAt;
}
public enum ModerationDecision { APPROVED, REJECTED, NEEDS_HUMAN_REVIEW }

public class Violation {
    String violationType;       // "PROHIBITED_ITEM", "EXPLICIT_CONTENT", "COUNTERFEIT"
    String description;         // "Weapon detected in image 3"
    String source;              // "IMAGE_ML", "TEXT_ML", "HUMAN"
    double confidence;          // 0.0 - 1.0
    String evidenceReference;   // Which image or text triggered
}

public class Seller {
    String sellerId;
    String name;
    String email;
    double reputationScore;     // 0-5 stars
    int totalListings;
    int rejectedListings;
    SellerTier tier;            // NEW, ESTABLISHED, TRUSTED
    Instant joinedAt;
}

public class Category {
    String categoryId;
    String name;
    String parentCategoryId;    // Hierarchical categories
    List<String> requiredFields;// "size" for clothing, "brand" for electronics
    List<String> prohibitedKeywords; // Category-specific banned terms
}
```

---

## 3️⃣ API Design

### 1. Create Listing (Step 1: Metadata)
```
POST /api/v1/listings
Request: {
  "title": "Vintage Leather Jacket",
  "description": "Genuine leather, size M, brown, great condition",
  "price_cents": 12500, "currency": "USD",
  "category": "clothing", "tags": ["vintage", "leather", "jacket"],
  "quantity": 1, "location": "Brooklyn, NY"
}
Response (201): { "listing_id": "lst_001", "status": "DRAFT", "upload_urls": [] }
```

### 2. Upload Images (Step 2: Direct to S3)
```
POST /api/v1/listings/{listing_id}/images/upload-url
Request: { "filename": "jacket-front.jpg", "content_type": "image/jpeg", "size_bytes": 5242880 }
Response (200): {
  "image_id": "img_001",
  "upload_url": "https://s3.amazonaws.com/uploads/img_001?X-Amz-Signature=...",
  "expires_in_sec": 300
}
Client uploads directly to S3 presigned URL (no server proxy needed)
```

### 3. Submit for Review (Step 3: Trigger Moderation)
```
POST /api/v1/listings/{listing_id}/submit
Response (202): {
  "listing_id": "lst_001", "status": "PENDING_REVIEW",
  "estimated_review_time_sec": 120,
  "message": "Your listing is being reviewed. We'll notify you when it's ready."
}
```

### 4. Get Listing Status (Polling or WebSocket)
```
GET /api/v1/listings/{listing_id}/status
Response (200): {
  "listing_id": "lst_001", "status": "APPROVED",
  "moderation": { "decision": "APPROVED", "confidence": 0.95 },
  "images": [
    { "image_id": "img_001", "status": "READY", "thumbnail_url": "https://cdn/..." }
  ]
}

--- If REJECTED: ---
Response (200): {
  "status": "REJECTED",
  "moderation": {
    "decision": "REJECTED",
    "violations": [
      { "type": "PROHIBITED_ITEM", "description": "Weapon-like object detected in image 2", "confidence": 0.92 }
    ]
  },
  "message": "Please remove the flagged image and resubmit."
}
```

### 5. Search Catalog (Buyer)
```
GET /api/v1/catalog/search?q=leather+jacket&category=clothing&price_max=200&sort=relevance&limit=20
Response (200): {
  "results": [
    { "listing_id": "lst_001", "title": "Vintage Leather Jacket", "price": 125.00,
      "thumbnail": "https://cdn/.../thumb.jpg", "seller": "Alice", "location": "Brooklyn, NY" }
  ],
  "total": 1234, "next_cursor": "..."
}
```

### 6. Browse by Category
```
GET /api/v1/catalog/categories/clothing?sort=newest&limit=20
Response (200): { "listings": [...], "subcategories": ["mens", "womens", "accessories"] }
```

---

## 4️⃣ Data Flow

### Listing Creation → Moderation → Approval Pipeline
```
1. Seller creates listing (title, description, price) → status: DRAFT
   ↓
2. Seller uploads images:
   a. Get presigned S3 URL from API
   b. Upload directly to S3 (no server proxy — saves bandwidth)
   c. S3 triggers Lambda/event → produce to Kafka "image-uploaded"
   ↓
3. Image Processing Pipeline (Kafka consumer):
   a. Download original from S3
   b. Resize: generate thumbnail (200×200), medium (800×800), large (1600×1600)
   c. Compress: optimize quality vs size (WebP + JPEG fallback)
   d. Upload resized images to CDN-backed S3 bucket
   e. Update image status: PROCESSING → READY
   ↓
4. Seller clicks "Submit for Review" → status: PENDING_REVIEW
   → Produce to Kafka "listings-pending-review"
   ↓
5. Moderation Pipeline (Kafka consumer):
   a. TEXT MODERATION:
      - Run NLP classifier on title + description
      - Check for prohibited keywords, misleading claims, policy violations
      - Score: 0.0 (clean) to 1.0 (violation)
   
   b. IMAGE MODERATION:
      - Run image classification model on each image
      - Detect: weapons, drugs, explicit content, counterfeit logos
      - Score per image: 0.0 (clean) to 1.0 (violation)
   
   c. CROSS-CHECK:
      - Does title match images? (e.g., title says "book" but image shows weapon)
      - Price reasonableness for category
   
   d. DECISION:
      - If max_violation_score < 0.3 → AUTO_APPROVE
      - If max_violation_score > 0.8 → AUTO_REJECT (with violation details)
      - If 0.3 ≤ score ≤ 0.8 → NEEDS_HUMAN_REVIEW (queue for moderator)
   ↓
6. If APPROVED:
   a. Status → APPROVED
   b. Index listing in Elasticsearch (searchable by buyers)
   c. Notify seller: "Your listing is live!"
   
   If REJECTED:
   a. Status → REJECTED (with violation details)
   b. Notify seller: "Rejected: weapon detected in image 2. Please edit and resubmit."
   
   If NEEDS_HUMAN_REVIEW:
   a. Add to human moderation queue
   b. Human moderator reviews → APPROVED or REJECTED
   c. Feedback to ML model (training data for improvement)
   ↓
7. Total: upload → live: < 5 minutes (auto-moderation path)
```

### Image Upload Flow (Presigned URL)
```
1. Client requests upload URL: POST /listings/{id}/images/upload-url
2. Server generates S3 presigned PUT URL (expires in 5 min)
3. Client uploads image directly to S3 (no server bandwidth used!)
4. S3 event notification → Kafka "image-uploaded"
5. Image Processor resizes + compresses
6. Processed images stored in CDN-backed bucket
7. Client can poll for image processing status
```

---

## 5️⃣ High-Level Design

```
┌───────────────────────────────────────────────────────────────────┐
│                    SELLERS + BUYERS                                 │
│  Seller: create listing → upload images → submit → view status    │
│  Buyer: search → browse → view listing details                   │
└──────────────────────────┬─────────────────────────────────────────┘
                           ▼
┌───────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Auth, Rate Limit)                   │
└──────┬──────────────────────────┬──────────────────┬──────────────┘
       │ Listing CRUD             │ Image Upload     │ Catalog Search
       ▼                          ▼                  ▼
┌──────────────┐    ┌──────────────────┐   ┌──────────────────┐
│ LISTING      │    │ IMAGE SERVICE    │   │ SEARCH SERVICE   │
│ SERVICE      │    │                  │   │                  │
│              │    │ • Presigned URLs │   │ • Elasticsearch  │
│ • CRUD       │    │ • Track upload   │   │ • Full-text search│
│ • Status mgmt│    │   status         │   │ • Filters/facets │
│ • Submit flow│    │ • Return CDN URLs│   │ • Category browse│
│              │    │                  │   │                  │
│ Pods: 20     │    │ Pods: 10        │   │ Pods: 20        │
└──────┬───────┘    └────────┬────────┘   └────────┬────────┘
       │                     │                      │
  ┌────┼─────────────────────┼──────────────────────┼────┐
  │    ▼                     ▼                      ▼    │
  │ ┌──────────┐  ┌──────────────┐  ┌──────────────────┐ │
  │ │Listing DB│  │ S3 + CDN     │  │ Elasticsearch    │ │
  │ │(Postgres)│  │              │  │                  │ │
  │ │          │  │ • Original   │  │ • 100M listings  │ │
  │ │• Listings│  │   images (S3)│  │ • Full-text index│ │
  │ │• Images  │  │ • Resized    │  │ • Faceted filters│ │
  │ │• Sellers │  │   (CDN-S3)   │  │                  │ │
  │ │          │  │ • ~5 PB total│  │ ~50 GB           │ │
  │ │ ~200 GB  │  │              │  │                  │ │
  │ └──────────┘  └──────────────┘  └──────────────────┘ │
  └──────────────────────────────────────────────────────┘

                    ▼ Kafka
┌───────────────────────────────────────────────────────────────────┐
│  Topic: "image-uploaded"     (S3 upload events → image processing)│
│  Topic: "listings-pending"   (submit → moderation pipeline)       │
│  Topic: "moderation-results" (approved/rejected → update listing) │
│  Topic: "listing-approved"   (→ index in Elasticsearch)          │
└────┬──────────────────────────┬──────────────────┬────────────────┘
     │                          │                  │
┌────▼──────────┐    ┌─────────▼──────┐   ┌──────▼───────────┐
│ IMAGE          │    │ MODERATION     │   │ SEARCH INDEX     │
│ PROCESSOR      │    │ SERVICE        │   │ UPDATER          │
│                │    │                │   │                  │
│ • Resize (3    │    │ • Text ML      │   │ • Consume        │
│   sizes)       │    │ • Image ML     │   │   approved       │
│ • Compress     │    │ • Decision     │   │   listings       │
│ • Thumbnail    │    │   engine       │   │ • Index in ES    │
│ • Upload to CDN│    │ • Human queue  │   │                  │
│                │    │                │   │ Pods: 5          │
│ Pods: 20-50    │    │ GPU: 5-10     │   └──────────────────┘
│ (CPU-bound)    │    │ + Human queue  │
└────────────────┘    └────────────────┘

┌───────────────────────────────────────────────────────────────────┐
│  HUMAN MODERATION QUEUE (for edge cases, 0.3-0.8 confidence)     │
│  • Web UI for moderators: view listing + images + ML scores      │
│  • Approve/reject with reason                                     │
│  • Feedback to ML model (improve accuracy over time)             │
│  • SLA: < 1 hour for human review                                │
└───────────────────────────────────────────────────────────────────┘
```

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Listing Service** | CRUD, status management, submit flow | Go on K8s | 20 pods |
| **Image Service** | Presigned URLs, upload tracking | Go on K8s | 10 pods |
| **Image Processor** | Resize, compress, thumbnail | Go/Python on K8s | 20-50 pods (CPU-bound) |
| **Moderation Service** | Text + image ML classification + decision | Python + GPU | 5-10 GPU nodes |
| **Search Service** | Full-text search, category browse | Go on K8s | 20 pods |
| **Listing DB** | Listings, images, sellers, categories | PostgreSQL | ~200 GB |
| **Image Storage** | Original + processed images | S3 + CloudFront CDN | ~5 PB |
| **Search Index** | Searchable listing data | Elasticsearch | ~50 GB |
| **Kafka** | Event pipeline between components | Kafka | 5+ brokers |

---

## 6️⃣ Deep Dives

### DD1: Image Processing Pipeline — Resize, Compress, Thumbnail in < 30 Seconds

**Problem**: 5M images/day uploaded. Each needs 3 resized versions + compression. Must be fast (seller wants to see preview quickly) and cost-efficient.

```
Pipeline per image:

1. S3 event → Kafka "image-uploaded" → Image Processor consumes
2. Download original from S3 (10 MB avg, ~500ms)
3. Validate: is it a real image? (check magic bytes, not just extension)
4. Resize:
   - Thumbnail: 200×200 (crop to square, center-weighted)
   - Medium: 800×800 (fit within bounds, preserve aspect ratio)
   - Large: 1600×1600 (fit within bounds)
   Library: libvips (fastest image processing, 10× faster than ImageMagick)
5. Compress:
   - WebP format (30% smaller than JPEG at same quality)
   - JPEG fallback for old browsers
   - Quality: 85% (good balance)
6. Upload 3 versions to CDN-backed S3 bucket (~1s)
7. Update DB: image status → READY, set CDN URLs

Total per image: ~5-10 seconds
Throughput: 50 pods × 6 images/min = 300 images/min = 18K/hour
Daily need: 5M / 24h = 208K/hour → need ~60 pods at peak

Presigned URL flow (saves server bandwidth):
  Client → API: "I want to upload" → API returns presigned S3 PUT URL
  Client → S3: direct upload (bypasses our servers completely)
  S3 → Event → Kafka → Image Processor
  → Our servers never touch the raw upload bytes (cost savings!)
```

### DD2: Content Moderation — ML Classification for Policy Compliance

**Problem**: Must detect weapons, drugs, explicit content, counterfeit goods from images AND text. False negatives (allow illegal items) are dangerous. False positives (reject legitimate items) frustrate sellers.

```
Moderation pipeline per listing:

1. TEXT MODERATION (fast, ~100ms):
   - NLP classifier: title + description → prohibited category?
   - Keyword matching: blocklist of prohibited terms (with context awareness)
   - Embeddings: compare against known-violation text patterns
   - Output: text_score (0.0 = clean, 1.0 = violation)

2. IMAGE MODERATION (slower, ~500ms per image):
   - Image classification model (ResNet/EfficientNet fine-tuned):
     * Weapon detection (guns, knives, explosives)
     * Drug paraphernalia
     * Explicit/adult content (NSFW)
     * Counterfeit logos (brand detection + legitimacy check)
   - Output per image: [{ category: "weapon", confidence: 0.92 }, ...]

3. CROSS-CHECK:
   - Title says "children's toy" but image shows weapon → HIGH violation
   - Price anomaly: $10 for a "Rolex" → likely counterfeit

4. DECISION ENGINE:
   max_score = max(text_score, max(image_scores))
   
   if max_score < 0.3:     → AUTO_APPROVE (85% of listings)
   if max_score > 0.8:     → AUTO_REJECT with violation details (5%)
   if 0.3 ≤ score ≤ 0.8:  → NEEDS_HUMAN_REVIEW (10%)

5. FEEDBACK LOOP:
   Human moderation decisions → retrain ML model weekly
   Track: false positive rate, false negative rate, review queue size
   Target: < 1% false negatives (missed violations), < 5% false positives
```

### DD3: Listing Lifecycle — State Machine with Edit & Resubmit

**Problem**: Listing goes through multiple states. Rejected listings can be edited and resubmitted. Must track history and provide clear feedback.

```
State machine:

  DRAFT → PENDING_REVIEW → PROCESSING → APPROVED → (edit) → PENDING_REVIEW
                                       → REJECTED → (edit) → PENDING_REVIEW
                                       → NEEDS_HUMAN_REVIEW → APPROVED / REJECTED

Transitions:
  DRAFT → PENDING_REVIEW: seller clicks "Submit"
  PENDING_REVIEW → PROCESSING: images done processing, moderation starts
  PROCESSING → APPROVED: ML confidence > 0.7 clean
  PROCESSING → REJECTED: ML confidence > 0.8 violation
  PROCESSING → NEEDS_HUMAN_REVIEW: ML uncertain (0.3-0.8)
  REJECTED → DRAFT: seller edits listing (can change images/description)
  DRAFT → PENDING_REVIEW: seller resubmits

Seller notification at each transition:
  PENDING_REVIEW: "Your listing is being reviewed..."
  APPROVED: "Your listing is now live! 🎉"
  REJECTED: "Your listing was rejected: weapon detected in image 2. Please edit and resubmit."
  NEEDS_HUMAN_REVIEW: "Your listing is under additional review. Expected: < 1 hour."

Notification channels: in-app + email + push (configurable)
```

### DD4: Presigned URL Upload — Direct Client-to-S3

**Problem**: 5M images/day at 10 MB average = 50 TB/day of upload bandwidth. Proxying through our servers is expensive and slow.

```
Presigned URL flow:

1. Client: POST /listings/{id}/images/upload-url
   Request: { filename: "jacket.jpg", size: 5242880, content_type: "image/jpeg" }

2. Server validates:
   - Listing exists and belongs to user
   - Image count < 10 (max per listing)
   - File size < 20 MB
   - Content type is image/*

3. Server generates presigned S3 PUT URL:
   url = s3.generate_presigned_url(
     method='PUT',
     bucket='listing-uploads',
     key=f'raw/{image_id}.jpg',
     content_type='image/jpeg',
     expires_in=300  # 5 minutes
   )

4. Return presigned URL to client

5. Client uploads directly to S3:
   PUT https://s3.amazonaws.com/listing-uploads/raw/img_001.jpg
   Headers: Content-Type: image/jpeg
   Body: <binary image data>

6. S3 event notification → triggers image processing pipeline

Benefits:
  - Server never handles image bytes (saves bandwidth + CPU)
  - Client gets direct S3 upload speed (fast, parallel)
  - Presigned URL expires in 5 min (secure)
  - S3 handles concurrent uploads at any scale
```

### DD5: Search & Discovery — Elasticsearch for Catalog Queries

**Problem**: 100M listings searchable by keyword, filterable by category/price/location, sorted by relevance or recency. Only APPROVED listings visible.

```
Elasticsearch index: "listings"

Document structure:
{
  "listing_id": "lst_001",
  "title": "Vintage Leather Jacket",
  "description": "Genuine leather, size M, brown...",
  "category": ["clothing", "mens", "jackets"],
  "price_cents": 12500,
  "currency": "USD",
  "location": { "lat": 40.68, "lng": -73.94 },
  "seller_id": "seller_001",
  "seller_reputation": 4.8,
  "tags": ["vintage", "leather"],
  "image_thumbnail": "https://cdn/.../thumb.jpg",
  "created_at": "2025-01-10T10:00:00Z",
  "status": "APPROVED"  // Only index APPROVED listings
}

Indexing: Kafka consumer for "listing-approved" → index to ES
  On listing update/rejection → remove from ES index

Query examples:
  "leather jacket" + category=clothing + price < 200 + sort=relevance
  → Multi-match query + term filter + range filter + score sorting

Relevance scoring:
  - Title match weight: 3x
  - Description match: 1x
  - Seller reputation boost: +0.1 per star
  - Recency boost: newer listings ranked higher (decay function)
```

### DD6: Human Moderation Queue — Edge Cases

**Problem**: 10% of listings (100K/day) need human review. Must be efficient, fair, and feed back into ML.

```
Human moderation workflow:

1. Listing enters NEEDS_HUMAN_REVIEW → added to moderation queue (PostgreSQL + Redis)

2. Moderator dashboard:
   - See next listing in queue (FIFO, or prioritized by category/risk)
   - View: title, description, all images, ML scores, similar past decisions
   - Quick actions: APPROVE / REJECT (with reason dropdown)
   - Edge case: ESCALATE (to senior moderator)

3. SLA: 95% of queued listings reviewed within 1 hour

4. Moderator tools:
   - Side-by-side: listing images vs ML flagged regions
   - Similar listings: show previously approved/rejected similar items
   - Seller history: past violations, reputation score

5. Feedback to ML:
   - Every human decision → training data for ML model
   - Weekly model retrain with new human-labeled examples
   - Track: agreement rate between ML and humans
   - Goal: reduce NEEDS_HUMAN_REVIEW from 10% → 5% over time

6. Staffing: 100K reviews/day / 8h × 2 min/review = ~210 moderators needed
   (With productivity tools: ~150 moderators)
```

### DD7: Seller Experience — Real-Time Status Feedback

**Problem**: Seller submits listing and wants to know what's happening. "Is it stuck? Why is it taking so long?"

```
Real-time feedback options:

Option A: POLLING (simple, chosen for MVP)
  Client polls GET /listings/{id}/status every 5 seconds
  Server returns current status + progress indicators
  { "status": "PROCESSING", "progress": { "images": "3/5 ready", "moderation": "in progress" } }

Option B: WEBSOCKET (better UX for real-time)
  Client opens WebSocket: /ws/listings/{id}/status
  Server pushes status updates as they happen:
    { "type": "image_ready", "image_id": "img_001", "thumbnail_url": "..." }
    { "type": "moderation_started" }
    { "type": "listing_approved" }

Option C: SERVER-SENT EVENTS (middle ground)
  GET /listings/{id}/status/stream (SSE)
  One-directional push, simpler than WebSocket

Progress timeline shown to seller:
  ✅ Listing created
  ✅ Image 1 uploaded (3/5)
  ⏳ Processing images...
  ⏳ Checking compliance...
  ✅ Approved! Your listing is live.
  
  or:
  ❌ Rejected: Weapon-like object detected in image 2.
     [Edit Listing] [Contact Support]
```

### DD8: Handling Scale — 5M Images/Day Through the Pipeline

**Problem**: 5M images/day = 58/sec sustained. Image processing (resize + compress) takes 5-10 seconds each. Must not create bottleneck.

```
Scaling strategy:

1. IMAGE UPLOAD: S3 presigned URLs → infinite upload throughput
   No server bottleneck (clients upload directly to S3)

2. IMAGE PROCESSING: horizontal scaling via Kafka
   - 5M images/day = 58/sec sustained, 200/sec peak
   - Each pod processes ~6 images/min (10 sec each)
   - 58 images/sec × 10 sec = 580 concurrent → need ~100 pods at peak
   - Auto-scale based on Kafka consumer lag
   - Use spot instances (image processing is fault-tolerant; retry on failure)

3. MODERATION: ML inference scaling
   - 1M listings/day = 12/sec
   - ~500ms per inference → need ~6 concurrent GPU workers
   - Batch: process 10 listings at once (GPU batch inference)
   - With batching: 2 GPU nodes sufficient

4. SEARCH INDEXING: low throughput
   - 1M approved listings/day → ~12 writes/sec to ES
   - ES handles 10K writes/sec easily

5. BACKPRESSURE:
   - If image processing falls behind → Kafka buffers
   - Seller sees "Processing images..." for longer (acceptable)
   - If moderation falls behind → queue grows → auto-scale ML pods
   - SLA violation alert if queue > 10K listings
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Image upload** | S3 presigned URLs (direct client-to-S3) | Server never handles image bytes; infinite upload throughput; cost-efficient |
| **Image processing** | libvips (resize/compress), Kafka-driven pipeline | 10× faster than ImageMagick; horizontally scalable via Kafka consumers |
| **Moderation** | ML auto-classify + human queue for edge cases | 85% auto-approved (fast); 5% auto-rejected; 10% human review (safety net) |
| **Decision thresholds** | < 0.3 approve, > 0.8 reject, middle → human | Balances UX (low false positives) with safety (low false negatives) |
| **Listing lifecycle** | State machine: DRAFT → PENDING → APPROVED/REJECTED | Clear seller feedback; edit + resubmit flow; only approved visible to buyers |
| **Search** | Elasticsearch with Kafka-driven indexing | Full-text + facets + relevance scoring; only APPROVED listings indexed |
| **Seller feedback** | Polling (MVP) / SSE (v2) | Simple to implement; real-time progress timeline for seller UX |
| **Scaling** | Kafka buffers + auto-scaling consumers | Decoupled stages; each scales independently; backpressure handled gracefully |

## Interview Talking Points

1. **"S3 presigned URLs: server never touches image bytes"** — Client uploads 10 MB images directly to S3. Saves 50 TB/day of server bandwidth. S3 event triggers image processing pipeline via Kafka.

2. **"3-tier moderation: ML text (100ms) + ML image (500ms) + human queue (10% edge cases)"** — Text classifier + image classifier run in parallel. < 0.3 → auto-approve (85%). > 0.8 → auto-reject (5%). Middle → human moderator (10%). Human decisions feed back into ML training.

3. **"Listing state machine: DRAFT → PENDING → APPROVED/REJECTED with edit + resubmit"** — Clear status at every step. Rejected listings include violation details ("weapon detected in image 2"). Seller edits and resubmits. Only APPROVED visible to buyers.

4. **"Image processing: libvips for 3 sizes (thumb/medium/large) + WebP compression"** — 5-10 seconds per image. 100 pods handle 200/sec peak. Kafka consumer auto-scales based on lag. Spot instances for cost savings.

5. **"Elasticsearch for catalog search: only index APPROVED listings"** — Kafka consumer for "listing-approved" → index to ES. Full-text + category facets + price range + geo + relevance scoring. Remove on rejection.

6. **"Human moderation: 100K reviews/day with SLA < 1 hour"** — Moderator dashboard shows listing + images + ML scores + similar past decisions. Feedback loop: human decisions retrain ML weekly. Goal: reduce human queue from 10% → 5%.

---

## 🔗 Related Problems
| Problem | Key Difference |
|---------|---------------|
| **Etsy / eBay Listings** | Our exact design |
| **Instagram Post Upload** | Similar image pipeline; no moderation decision gate |
| **Content Moderation (Facebook)** | Same ML pipeline; we add listing lifecycle + marketplace search |
| **Google Drive Upload** | File upload + processing; no moderation or marketplace |

## 🔧 Technology Alternatives
| Component | Chosen | Alternative |
|-----------|--------|------------|
| **Image processing** | libvips | ImageMagick / Sharp (Node.js) / Pillow (Python) |
| **Image moderation ML** | Custom fine-tuned model | AWS Rekognition / Google Vision API / Azure Content Moderator |
| **Search** | Elasticsearch | Algolia / Typesense / PostgreSQL full-text |
| **Image storage** | S3 + CloudFront | GCS + Cloud CDN / Azure Blob + CDN |
| **Event pipeline** | Kafka | SQS / RabbitMQ / Pub/Sub |
| **Listing DB** | PostgreSQL | DynamoDB / MongoDB |

---

**Created**: February 2026 | **Framework**: Hello Interview (6-step) | **Time**: 45-60 min
**References**: Etsy Listing Architecture, ML Content Moderation at Scale, S3 Presigned URL Best Practices, Elasticsearch for E-Commerce Search
