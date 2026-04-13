# 🎯 Topic 18: Blob & Media Storage

> **System Design Interview — Deep Dive**
> A comprehensive guide covering blob storage architecture (S3), presigned URLs for direct upload, media processing pipelines (thumbnails, transcoding), CDN delivery, and why you should never store blobs in databases.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Blob Storage, Not Databases](#why-blob-storage-not-databases)
3. [S3 — The Universal Blob Store](#s3--the-universal-blob-store)
4. [Presigned URL Upload Pattern](#presigned-url-upload-pattern)
5. [Media Processing Pipeline](#media-processing-pipeline)
6. [Image Processing](#image-processing)
7. [Video Transcoding](#video-transcoding)
8. [Content Moderation](#content-moderation)
9. [CDN Integration for Media Delivery](#cdn-integration-for-media-delivery)
10. [Storage Organization & Key Design](#storage-organization--key-design)
11. [Media Metadata Management](#media-metadata-management)
12. [Cost Optimization](#cost-optimization)
13. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
14. [Common Interview Mistakes](#common-interview-mistakes)
15. [Media Architecture by System Design Problem](#media-architecture-by-system-design-problem)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

Media files (images, videos, documents) are **binary large objects (BLOBs)** that require fundamentally different storage than structured data. They should be stored in **object storage (S3)** and served through **CDN**, never in your database.

```
Structured data (user profiles, orders):
  → PostgreSQL, DynamoDB (transactional, indexed, queryable)

Unstructured data (images, videos, PDFs):
  → S3 (blob storage, CDN-served, 11 nines durability)

Database stores: URL reference ("s3://bucket/img/abc123.jpg")
S3 stores: Actual binary bytes
CDN serves: Binary bytes to end users at edge locations
```

---

## Why Blob Storage, Not Databases

### The Problem with Database Blob Storage
```
Storing a 5MB image in PostgreSQL means:
  ❌ Backups: 5MB per image × 1B images = 5PB backup size (was 100GB)
  ❌ Replication: 5MB per image flows through WAL → 50x replication traffic
  ❌ I/O: Database pages bloated with binary data → slower query performance
  ❌ No CDN: Can't serve from edge without an app layer in between
  ❌ No streaming: Database isn't designed for streaming large binary responses
  ❌ Memory: Buffer pool polluted with blob data instead of useful indexes

Storing a URL reference in PostgreSQL:
  ✅ URL: "s3://bucket/images/abc123.jpg" → 50 bytes instead of 5MB
  ✅ Backups: 100GB (unchanged, just URL strings)
  ✅ Replication: Minimal overhead (50 bytes per image row)
  ✅ CDN: Image served directly from CloudFront edge (15ms globally)
  ✅ Streaming: S3 supports byte-range requests natively
  ✅ Durability: S3 offers 99.999999999% (11 nines) durability
```

### Interview Script
> *"I'd never store media blobs in the database. Storing a 5MB image in PostgreSQL means the 5MB blob is included in every full backup, replication traffic increases 50x, and we can't serve the image through a CDN without an application layer in between. Instead, store a URL reference in the DB (`s3://bucket/images/abc123.jpg`) — 50 bytes instead of 5MB. The actual bytes live in S3 with 11 nines of durability, served through CloudFront CDN at edge locations worldwide."*

---

## S3 — The Universal Blob Store

### Key Properties
| Property | Value |
|---|---|
| **Durability** | 99.999999999% (11 nines) |
| **Availability** | 99.99% |
| **Max object size** | 5 TB |
| **Multipart upload** | Required for > 100MB (up to 10,000 parts) |
| **Versioning** | Optional — keep all versions of an object |
| **Encryption** | Server-side (AES-256) or client-side |
| **Access control** | Bucket policies, IAM, presigned URLs |
| **Events** | S3 Event Notifications → Lambda, SQS, SNS |

### Storage Classes (Cost Optimization)
| Class | Cost/GB/month | Use Case |
|---|---|---|
| **Standard** | $0.023 | Frequently accessed media |
| **Intelligent-Tiering** | $0.023-$0.0125 | Unpredictable access patterns |
| **Standard-IA** | $0.0125 | Older media (> 30 days) |
| **Glacier IR** | $0.004 | Archived media (quarterly access) |
| **Glacier** | $0.004 | Long-term archive |
| **Glacier Deep** | $0.00099 | Compliance (7+ year retention) |

---

## Presigned URL Upload Pattern

### The Problem with Proxy Upload
```
Traditional (BAD):
  Client → Upload 10MB image → API Server → Forward to S3
  
Problems:
  - API server is a bottleneck (handles actual bytes)
  - API server needs 10MB of memory per concurrent upload
  - 10,000 concurrent uploads × 10MB = 100GB memory on API servers
  - Network bandwidth doubled (client→server + server→S3)
```

### The Solution: Direct-to-S3 Upload
```
Step 1: Client requests upload URL (lightweight)
  Client → POST /api/upload/request → API Server
  API Server → generates presigned S3 PUT URL (50ms)
  API Server → returns presigned URL to client

Step 2: Client uploads directly to S3 (heavy lifting)
  Client → PUT https://bucket.s3.amazonaws.com/img/xyz?signature=... → S3
  S3 absorbs all the bandwidth (massively scalable)
  API server is NOT involved in the actual upload

Step 3: S3 event triggers processing
  S3 → S3 Event Notification → Lambda/SQS → Processing Pipeline
```

### Flow Diagram
```
┌──────────┐  1. Request URL  ┌──────────┐  Generate presigned URL
│  Client   │ ──────────────→ │ API      │ ──→ AWS SDK
│           │ ←── URL ─────── │ Server   │ ←── presigned PUT URL
│           │                 └──────────┘
│           │
│           │  2. Upload directly to S3 (10MB image)
│           │ ──────────────────────────────→ ┌──────────┐
│           │ ←── 200 OK ──────────────────── │    S3    │
└──────────┘                                  └────┬─────┘
                                                   │
                                    3. S3 Event Notification
                                                   │
                                              ┌────▼─────┐
                                              │ Processing│
                                              │ Pipeline  │
                                              └──────────┘
```

### Presigned URL Generation (Python)
```python
import boto3

s3_client = boto3.client('s3')

def generate_upload_url(user_id, file_extension):
    key = f"uploads/{user_id}/{uuid4()}.{file_extension}"
    url = s3_client.generate_presigned_url(
        'put_object',
        Params={
            'Bucket': 'media-uploads',
            'Key': key,
            'ContentType': f'image/{file_extension}'
        },
        ExpiresIn=300  # 5-minute expiration
    )
    return {"upload_url": url, "key": key}
```

### Interview Script
> *"Users upload directly to S3 via a presigned URL — our backend never touches the actual bytes. The flow: client requests upload URL from our API (returns presigned S3 PUT URL in 50ms) → client uploads directly to S3 using that URL (leverages S3's massive bandwidth) → S3 triggers an event to our processing queue. This architecture means our API servers never become a bottleneck for file uploads. Even if 10,000 users upload simultaneously, our servers handle only 10,000 lightweight URL-generation requests while S3 absorbs all the bandwidth."*

---

## Media Processing Pipeline

### Architecture
```
┌──────────┐   S3 Event   ┌──────────┐   Enqueue   ┌──────────┐
│    S3    │ ───────────→ │  Lambda   │ ──────────→ │   SQS    │
│ (upload) │              │ (trigger) │             │ (queue)  │
└──────────┘              └──────────┘             └────┬─────┘
                                                        │
                                              ┌─────────┼─────────┐
                                              ▼         ▼         ▼
                                         ┌────────┐ ┌────────┐ ┌────────┐
                                         │Worker 1│ │Worker 2│ │Worker 3│
                                         └────┬───┘ └────┬───┘ └────┬───┘
                                              │         │         │
                                              ▼         ▼         ▼
                                    ┌─────────────────────────────────┐
                                    │        Processing Steps:        │
                                    │ 1. Generate thumbnails          │
                                    │ 2. Transcode video (if video)   │
                                    │ 3. Strip EXIF metadata          │
                                    │ 4. Content moderation (ML)      │
                                    │ 5. Update DB: status='published'│
                                    │ 6. Notify user (push/email)     │
                                    └─────────────────────────────────┘
```

---

## Image Processing

### Thumbnail Generation
```
Original upload: 4000×3000 pixels, 5MB JPEG

Generated thumbnails:
  150×150  → profile avatars, thumbnails (15KB)
  300×300  → small cards, previews (40KB)
  600×600  → medium display (80KB)
  1200×900 → large display (200KB)

Storage:
  S3 key pattern: images/{hash}/original.jpg
                  images/{hash}/150x150.jpg
                  images/{hash}/300x300.jpg
                  images/{hash}/600x600.jpg
                  images/{hash}/1200x900.jpg

Total: 5 versions × average 70KB = 340KB per image (vs 5MB original)
```

### Image Optimization
```
1. Format conversion: JPEG/PNG → WebP (30-50% smaller)
2. Quality optimization: 85% quality (visually identical, much smaller)
3. Progressive JPEG: Image loads blurry-to-sharp (better perceived speed)
4. Responsive images: Serve appropriate size based on device screen
5. Lazy loading: Only load images visible in viewport
```

### EXIF Metadata Stripping
```
Camera photos contain EXIF data:
  - GPS coordinates (privacy risk!)
  - Camera model
  - Date/time
  - Lens settings

MUST strip GPS data before serving.
Optionally preserve: date, camera info (for photo apps).
```

---

## Video Transcoding

### Why Transcode?
Users upload in various formats and resolutions. We need to serve optimized versions for every device and network condition.

### Output Formats
```
Input: user_upload.mov (1080p, 500MB)

Transcoded outputs:
  video/360p.mp4    (640×360, 500kbps)    → Mobile on 3G
  video/480p.mp4    (854×480, 1Mbps)      → Mobile on 4G
  video/720p.mp4    (1280×720, 2.5Mbps)   → Tablet/Desktop
  video/1080p.mp4   (1920×1080, 5Mbps)    → Desktop fullscreen
  video/4k.mp4      (3840×2160, 15Mbps)   → 4K displays

HLS adaptive streaming:
  video/master.m3u8  (playlist pointing to all qualities)
  Segments: 6-second chunks per quality level
  Client auto-switches quality based on bandwidth
```

### Processing Time
```
1-minute video:
  360p: ~30 seconds
  1080p: ~2 minutes
  4K: ~5 minutes
  All qualities (parallel): ~5 minutes total

10-minute video: ~30-50 minutes for all qualities
```

### Architecture
```
Upload → S3 → SQS → Transcoding Workers (GPU instances)
  → Multiple quality outputs → S3
  → HLS packaging → S3
  → CDN delivery → Users

Workers: Spot instances (60-80% cheaper than on-demand)
  GPU instances for hardware-accelerated encoding
  Auto-scale based on queue depth
```

---

## Content Moderation

### Automated (ML-Based)
```
Pipeline step after upload:
  1. Run image through ML model (nudity, violence, hate symbols)
  2. Score: 0.0 (safe) to 1.0 (violation)
  3. If score > 0.9: Auto-reject, notify user
  4. If 0.5 < score < 0.9: Queue for human review
  5. If score < 0.5: Auto-approve

Services: AWS Rekognition, Google Vision AI, custom models
Latency: 2-10 seconds per image, 30-60 seconds per video
```

### Integration in Pipeline
```
Upload → S3 → Processing queue → 
  Step 1: Generate thumbnails
  Step 2: Content moderation (parallel with Step 1)
  Step 3: If approved → status = 'published'
           If rejected → status = 'removed', notify user
           If uncertain → status = 'pending_review', queue for human review
```

---

## CDN Integration for Media Delivery

### Architecture
```
User in Tokyo requests image:
  1. Browser: GET cdn.example.com/images/abc123/600x600.jpg
  2. CDN Edge (Tokyo): Cache check → MISS (first request)
  3. CDN → S3 Origin (us-east-1): Fetch image (200ms)
  4. CDN Edge (Tokyo): Cache image, return to user
  5. Next request from Tokyo: Cache HIT → 15ms (local edge)

Cache hit ratio: 95%+ for popular content
```

### Cache-Control Headers
```
For media files (immutable once created):
  Cache-Control: public, max-age=31536000  (1 year)
  
Why safe to cache forever?
  Images are content-addressed: abc123.jpg always has the same content.
  When user uploads new photo → new URL (def456.jpg).
  Old URL cached forever (nobody requests it anymore).
```

### Signed URLs for Private Content
```
Private photos (DMs, private albums):
  URL: cdn.example.com/private/img/abc123.jpg?
       Expires=1710500000&
       Signature=base64_signature&
       Key-Pair-Id=APKAID...

  URL expires after specified time.
  Only authorized users can generate valid signed URLs.
  CDN verifies signature before serving content.
```

---

## Storage Organization & Key Design

### S3 Key Patterns
```
Public images:
  s3://media-bucket/images/{content_hash}/{size}.{format}
  s3://media-bucket/images/abc123/original.jpg
  s3://media-bucket/images/abc123/600x600.webp

Videos:
  s3://media-bucket/videos/{video_id}/original.mp4
  s3://media-bucket/videos/{video_id}/720p.mp4
  s3://media-bucket/videos/{video_id}/hls/master.m3u8

User uploads (pre-processing):
  s3://upload-bucket/uploads/{user_id}/{uuid}.{ext}

Avatars:
  s3://media-bucket/avatars/{user_id}/current.jpg
  s3://media-bucket/avatars/{user_id}/current_150x150.jpg
```

### Key Design Best Practices
```
✅ Content-addressed: Hash-based keys ensure immutability
✅ Hierarchical: Easy to manage lifecycle per prefix
✅ No sequential prefixes: Avoid "2025/03/15/..." (S3 hot partition)
✅ Include version/size: abc123/600x600.webp
❌ No PII in keys: Don't use email addresses as S3 keys
```

---

## Media Metadata Management

### Database Schema
```sql
CREATE TABLE media (
    media_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    media_type VARCHAR(10),  -- 'image', 'video'
    original_key TEXT,        -- S3 key for original upload
    status VARCHAR(20),       -- 'processing', 'published', 'removed'
    width INT,
    height INT,
    file_size_bytes BIGINT,
    duration_seconds INT,     -- for videos
    content_hash VARCHAR(64), -- SHA-256 for dedup
    thumbnails JSONB,         -- {"150x150": "s3://...", "600x600": "s3://..."}
    created_at TIMESTAMP DEFAULT NOW(),
    moderation_status VARCHAR(20)  -- 'approved', 'pending', 'rejected'
);
```

### Deduplication
```
Before processing:
  Compute SHA-256 hash of uploaded file.
  Check: Does this hash already exist in our media table?
  If yes: Reuse existing processed files (save processing cost).
  If no: Process normally.

Savings: ~15-20% of uploads are duplicates (reposts, forwards).
```

---

## Cost Optimization

### Storage Costs at Scale
```
Instagram-scale: 100M photos/day × 5MB average = 500TB/day
  Monthly raw storage: 15 PB

Cost without optimization:
  S3 Standard: 15 PB × $0.023/GB = $345,000/month

With optimization:
  1. Thumbnails only for display (not original): 15PB → 3PB
  2. WebP format (50% smaller): 3PB → 1.5PB
  3. Lifecycle (older → IA → Glacier): 
     Hot (30d): 1.5PB × $0.023 = $34,500
     IA (1-12 months): 15PB × $0.0125 = $187,500
     Glacier (>1yr): 150PB × $0.004 = $600,000
  4. Dedup (15% savings on new uploads)

Total optimized: ~$80K/month for hot data vs $345K unoptimized
```

### Spot Instances for Processing
```
Transcoding workers on Spot: $0.03/hour vs $0.10/hour on-demand
Savings: 70% on transcoding compute
If interrupted: SQS visibility timeout re-queues the job
```

---

## Interview Talking Points & Scripts

### Never Store Blobs in DB
> *"I'd never store media blobs in the database. Store a URL reference in the DB — 50 bytes instead of 5MB. The actual bytes live in S3 with 11 nines of durability, served through CloudFront CDN at edge locations worldwide."*

### Presigned URL Upload
> *"Users upload directly to S3 via a presigned URL — our backend never touches the actual bytes. Even if 10,000 users upload simultaneously, our servers handle only lightweight URL-generation requests while S3 absorbs all the bandwidth."*

### Async Processing Pipeline
> *"Everything after the upload is async. S3 event triggers a Lambda that enqueues a processing job. Workers generate thumbnails, transcode video, strip EXIF metadata, run content moderation ML, and update the post status. This takes 5-30 seconds for images and 1-5 minutes for video, all invisible to the user."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Storing blobs in the database
**Fix**: S3 for blobs, DB for URL references.

### ❌ Mistake 2: Proxy uploads through API servers
**Fix**: Presigned URLs for direct client-to-S3 upload.

### ❌ Mistake 3: Synchronous media processing
**Fix**: Async pipeline via SQS/Kafka. User gets immediate confirmation.

### ❌ Mistake 4: Serving media from origin without CDN
**Fix**: CloudFront CDN at 200+ edge locations. 95%+ cache hit ratio.

### ❌ Mistake 5: Not mentioning thumbnail generation
**Fix**: Multiple sizes for different contexts (avatar, preview, full display).

---

## Media Architecture by System Design Problem

| Problem | Upload | Processing | Storage | Delivery |
|---|---|---|---|---|
| **Instagram** | Presigned S3 URL | Thumbnails, filters, moderation | S3 + lifecycle | CloudFront CDN |
| **YouTube** | Resumable upload | Transcode (360p-4K), HLS | S3 tiered | CDN + adaptive streaming |
| **WhatsApp** | Presigned URL | E2E encrypted, thumbnails | S3 (encrypted) | Direct download |
| **Twitter** | Presigned URL | Thumbnails, GIF→video, moderation | S3 | CDN |
| **Slack** | Direct upload | Thumbnails, virus scan | S3 | Signed CDN URLs |
| **Google Drive** | Resumable upload | Preview generation, indexing | GCS tiered | Signed URLs |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│            BLOB & MEDIA STORAGE CHEAT SHEET                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  RULE #1: Never store blobs in databases                     │
│    DB stores: URL reference (50 bytes)                       │
│    S3 stores: Actual binary (5MB image)                      │
│                                                              │
│  UPLOAD: Presigned URL → direct client-to-S3                 │
│    API server generates URL (50ms)                           │
│    Client uploads directly to S3                             │
│    API server never touches bytes                            │
│                                                              │
│  PROCESSING (async):                                         │
│    S3 Event → SQS → Workers                                 │
│    Thumbnails: 150×150, 300×300, 600×600                     │
│    Video: Transcode to 360p, 720p, 1080p, 4K + HLS          │
│    Moderation: ML scoring → auto/human review                │
│    EXIF: Strip GPS coordinates (privacy)                     │
│                                                              │
│  DELIVERY: CDN (CloudFront)                                  │
│    95%+ cache hit ratio                                      │
│    Content-addressed URLs → max-age 1 year                   │
│    Private content: Signed URLs with expiration              │
│                                                              │
│  S3 DURABILITY: 99.999999999% (11 nines)                     │
│  COST: $0.023/GB (Standard) → $0.004/GB (Glacier)            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
