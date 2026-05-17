# 🎯 Topic 58: CDN & Media Processing Pipeline

> **System Design Interview — Deep Dive**
> A comprehensive guide covering media upload and processing pipelines — presigned URL uploads to S3, asynchronous media processing (thumbnails, transcoding, compression), content-addressable storage for cache-friendly URLs, CDN serving with edge caching, adaptive bitrate streaming, image optimization pipelines, video transcoding at scale, and production-grade interview scripts for Instagram, Netflix, and any media-heavy system.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Media Pipelines Are Hard](#why-media-pipelines-are-hard)
3. [Upload Path — Presigned URLs](#upload-path--presigned-urls)
4. [Async Processing Pipeline](#async-processing-pipeline)
5. [Image Processing — Thumbnails & Variants](#image-processing--thumbnails--variants)
6. [Video Transcoding Pipeline](#video-transcoding-pipeline)
7. [Content-Addressable Storage](#content-addressable-storage)
8. [CDN Architecture & Edge Caching](#cdn-architecture--edge-caching)
9. [Cache Invalidation Strategies](#cache-invalidation-strategies)
10. [Adaptive Bitrate Streaming (ABR)](#adaptive-bitrate-streaming-abr)
11. [Image Optimization — On-the-Fly vs Pre-Generated](#image-optimization--on-the-fly-vs-pre-generated)
12. [Storage Tiers & Lifecycle Policies](#storage-tiers--lifecycle-policies)
13. [Upload Validation & Security](#upload-validation--security)
14. [Processing Status & Notifications](#processing-status--notifications)
15. [Failure Handling & Retries](#failure-handling--retries)
16. [Cost Optimization](#cost-optimization)
17. [Performance Benchmarks](#performance-benchmarks)
18. [Real-World Production Patterns](#real-world-production-patterns)
19. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
20. [Common Interview Mistakes](#common-interview-mistakes)
21. [Media Pipeline by System Design Problem](#media-pipeline-by-system-design-problem)
22. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **media processing pipeline** handles the upload, transformation, storage, and delivery of images and videos at scale. Instead of sending media through application servers (which wastes bandwidth and CPU), clients upload directly to object storage using presigned URLs. Asynchronous workers then process the media (thumbnails, transcoding, compression), and a CDN serves the results globally with edge caching.

```
The full pipeline at a glance:

  ┌────────┐  1. Get presigned URL   ┌──────────┐
  │ Client  │────────────────────────→│ API Server│
  └────┬───┘                          └──────────┘
       │
       │ 2. Upload directly to S3 (presigned URL)
       ▼
  ┌─────────┐  3. S3 Event           ┌────────────────┐
  │   S3     │───────────────────────→│ Processing Queue│
  │ (raw)    │                        │   (SQS/Kafka)  │
  └─────────┘                         └───────┬────────┘
                                              │
                                    4. Async processing
                                              │
                                    ┌─────────▼─────────┐
                                    │  Media Workers      │
                                    │  - Thumbnails       │
                                    │  - Transcode        │
                                    │  - Compress         │
                                    │  - Watermark        │
                                    └─────────┬──────────┘
                                              │
                                    5. Write processed variants
                                              │
                                    ┌─────────▼─────────┐
                                    │   S3 (processed)    │
                                    │   /thumbnails/      │
                                    │   /720p/ /1080p/    │
                                    └─────────┬──────────┘
                                              │
  ┌────────┐  6. CDN serves          ┌────────▼────────┐
  │ Client  │←───────────────────────│      CDN         │
  │ (read)  │  (edge-cached)         │  (CloudFront)    │
  └────────┘                         └─────────────────┘
```

### Why This Architecture

```
Without this pipeline (naive approach):
  Client → uploads 50 MB video to app server → app server writes to S3
  → app server transcodes → app server returns transcoded URL
  
  Problems:
    1. App server receives 50 MB over its network → bandwidth bottleneck
    2. App server CPU transcodes video → blocked for minutes
    3. User waits for entire transcode to complete → terrible UX
    4. App server is stateful during processing → can't auto-scale
    5. If app server crashes during transcode → upload lost

With presigned URL + async pipeline:
    1. Client uploads 50 MB directly to S3 → 0 bandwidth on app server
    2. S3 event triggers async worker → app server is free
    3. User gets immediate "upload complete" → great UX
    4. Workers are stateless, horizontally scalable
    5. S3 durability: 99.999999999% (11 nines) → upload never lost
```

---

## Why Media Pipelines Are Hard

### Challenge 1: Scale of Data

```
Instagram:
  100M+ photos uploaded per day
  Average photo: 5 MB raw → 3 variants (thumbnail, standard, full)
  Daily upload volume: 500 TB of raw uploads
  Storage after processing: 300 TB/day (compressed)

Netflix:
  Thousands of new titles per month
  Each title: 1-3 hour video → transcoded to 100+ variants
  One movie → 120 GB of source → 6 TB of encoded outputs
  
YouTube:
  500 hours of video uploaded per MINUTE
  = 720,000 hours/day
  Each hour → 10+ quality levels
  Daily processing: 7.2M+ hours of transcode output
```

### Challenge 2: Processing Cost

```
Image processing:
  Resize + compress one image: ~100ms on a single CPU
  100M images/day: 100M × 0.1s = 10M CPU-seconds = 116 CPU-days
  At 3 variants each: 348 CPU-days
  → Need hundreds of processing workers running in parallel.

Video transcoding:
  1 hour of 4K video → 1080p transcode: ~2 hours of CPU time
  1 hour of 4K video → all quality levels: ~10 hours of CPU time
  500 hours/minute (YouTube) → 5,000 hours of CPU per minute
  → Need thousands of GPU/CPU workers (or hardware encoders).
```

### Challenge 3: Global Delivery

```
Users worldwide need fast access to media:
  User in Tokyo accessing a photo uploaded by a user in New York:
    Without CDN: Request → New York S3 → 200ms network latency
    With CDN: Request → Tokyo edge PoP (cached) → 5ms latency
    
  40x faster with CDN edge caching.
  
  CDN cache hit rates for media:
    Popular content: 95-99% cache hit rate
    Long tail: 50-70% cache hit rate
    Average: 85-95% cache hit rate
    
  85% of requests never reach the origin server.
```

### Challenge 4: Cache Invalidation

```
User uploads profile photo → visible immediately.
User changes profile photo → OLD photo cached at 200+ CDN edge locations.

Options:
  A. Wait for TTL expiry (minutes-hours) → user sees old photo → bad UX
  B. Purge cache at all PoPs → expensive, slow, complex
  C. Content-addressable URLs → new URL for new content → no invalidation needed!

Content-addressable URL:
  https://cdn.example.com/img/a1b2c3d4e5f6.jpg  ← hash of content
  
  New photo → new hash → new URL → CDN fetches from origin → no stale cache.
  Old URL still serves the old photo (which is correct for old references).
```

---

## Upload Path — Presigned URLs

### How Presigned URLs Work

```
A presigned URL is a temporary, pre-authorized URL that allows
a client to upload directly to S3 without going through your servers.

Step 1: Client requests upload permission from API server.
  POST /api/upload/request
  { "filename": "photo.jpg", "content_type": "image/jpeg", "size": 5242880 }

Step 2: API server generates a presigned S3 PUT URL.
  The URL includes:
    - S3 bucket and key (where to upload)
    - Expiration time (e.g., 15 minutes)
    - Allowed content type and size
    - AWS signature (cryptographic proof of authorization)
    
  Response:
  {
    "upload_url": "https://s3.amazonaws.com/my-bucket/raw/user42/abc123.jpg?X-Amz-...",
    "media_id": "abc123",
    "expires_in": 900
  }

Step 3: Client uploads directly to S3 using the presigned URL.
  PUT https://s3.amazonaws.com/my-bucket/raw/user42/abc123.jpg?X-Amz-...
  Content-Type: image/jpeg
  Body: <raw bytes of photo>
  
  S3 validates the signature and accepts the upload.
  Your API server never sees the bytes.

Step 4: S3 triggers processing via event notification.
  S3 Event → SQS/SNS/Lambda → Processing pipeline starts.
```

### Presigned URL Generation (Python)

```python
import boto3
import uuid

class UploadService:
    """Generates presigned URLs for direct-to-S3 uploads."""
    
    def __init__(self):
        self.s3 = boto3.client('s3')
        self.bucket = 'media-uploads-raw'
        self.max_size = 100 * 1024 * 1024  # 100 MB max
    
    def request_upload(self, user_id: str, content_type: str, file_size: int) -> dict:
        # Validate
        if file_size > self.max_size:
            raise ValueError("File too large")
        if content_type not in ['image/jpeg', 'image/png', 'image/webp',
                                 'video/mp4', 'video/quicktime']:
            raise ValueError("Unsupported content type")
        
        # Generate unique media ID and S3 key
        media_id = str(uuid.uuid4())
        s3_key = f"raw/{user_id}/{media_id}"
        
        # Create presigned URL
        presigned_url = self.s3.generate_presigned_url(
            'put_object',
            Params={
                'Bucket': self.bucket,
                'Key': s3_key,
                'ContentType': content_type,
                'ContentLength': file_size,
            },
            ExpiresIn=900,  # 15 minutes
        )
        
        # Record pending upload in database
        db.insert('media', {
            'media_id': media_id,
            'user_id': user_id,
            'status': 'PENDING_UPLOAD',
            'content_type': content_type,
            's3_key': s3_key,
            'created_at': datetime.utcnow(),
        })
        
        return {
            'upload_url': presigned_url,
            'media_id': media_id,
            'expires_in': 900,
        }
```

### Why Not Upload Through App Servers?

```
Presigned URL:
  Client ──50MB──→ S3 (100 Gbps network)
  App server: 0 bytes processed. Free to handle API requests.
  Latency: Direct S3 upload is faster (no proxy hop).
  
Through app server:
  Client ──50MB──→ App Server ──50MB──→ S3
  App server: 50 MB received + 50 MB sent = 100 MB bandwidth used.
  Latency: Extra hop through app server.
  At 100M uploads/day × 5 MB: 500 TB/day through your app servers!
  
  You'd need hundreds of app servers just for upload bandwidth.
  With presigned URLs: 0 extra servers needed. S3 handles it.

Cost at scale (100M uploads/day, 5 MB each):
  Through app server: ~$50K/day in EC2 bandwidth + compute
  Presigned to S3: ~$3K/day (S3 PUT cost only)
  Savings: ~$47K/day = $17M/year
```

### Multipart Upload for Large Files

```
For files > 100 MB (especially videos), use multipart upload:

  1. API server initiates multipart upload:
     create_multipart_upload(Bucket, Key) → upload_id
  
  2. API server generates presigned URLs for each part (5-100 MB each):
     For a 1 GB video: 10 parts × 100 MB each
     10 presigned URLs, one per part.
  
  3. Client uploads parts in parallel:
     Part 1 → S3 (presigned URL 1)  ← parallel
     Part 2 → S3 (presigned URL 2)  ← parallel
     Part 3 → S3 (presigned URL 3)  ← parallel
     ...
  
  4. Client (or server) completes the multipart upload:
     complete_multipart_upload(Bucket, Key, upload_id, parts)
  
  Benefits:
    - Parallel upload: 10 parts at 50 Mbps each = 500 Mbps effective
    - Resumable: If one part fails, retry only that part
    - No timeout: Each part has its own timeout (vs one giant upload)
```

---

## Async Processing Pipeline

### Pipeline Architecture

```
┌─────────┐     S3 Event     ┌─────────────┐     ┌─────────────────┐
│ S3 (raw) │────────────────→ │ SQS Queue    │────→│ Processing       │
│          │                  │              │     │ Workers (ECS/λ)  │
└─────────┘                  └─────────────┘     └────────┬─────────┘
                                                          │
                                               ┌──────────▼──────────┐
                                               │  Processing Steps    │
                                               │                     │
                                               │  1. Validate media  │
                                               │  2. Extract metadata│
                                               │  3. Generate variants│
                                               │  4. Write to S3     │
                                               │  5. Update database │
                                               │  6. Notify client   │
                                               └─────────────────────┘
```

### S3 Event Configuration

```json
{
  "LambdaFunctionConfigurations": [{
    "Events": ["s3:ObjectCreated:*"],
    "Filter": {
      "Key": { "FilterRules": [{ "Name": "prefix", "Value": "raw/" }] }
    },
    "LambdaFunctionArn": "arn:aws:lambda:...:media-trigger"
  }]
}

// Lambda trigger → sends message to SQS queue
// Why not process in Lambda directly?
//   Lambda: 15 min timeout, 10 GB memory max
//   Video transcoding can take hours and needs 32+ GB RAM
//   Lambda is great as a TRIGGER, not as a PROCESSOR.
```

### Processing Worker Implementation

```python
class MediaProcessor:
    """Asynchronous media processing worker."""
    
    def process(self, message):
        media_id = message['media_id']
        s3_key = message['s3_key']
        content_type = message['content_type']
        
        try:
            # Step 1: Download raw media from S3
            raw_data = s3.get_object(Bucket='media-raw', Key=s3_key)['Body'].read()
            
            # Step 2: Validate (file type, dimensions, no malware)
            self._validate(raw_data, content_type)
            
            # Step 3: Extract metadata (EXIF, dimensions, duration)
            metadata = self._extract_metadata(raw_data, content_type)
            
            # Step 4: Generate variants based on content type
            if content_type.startswith('image/'):
                variants = self._process_image(raw_data, metadata)
            elif content_type.startswith('video/'):
                variants = self._process_video(raw_data, metadata)
            
            # Step 5: Upload variants to processed S3 bucket
            for variant in variants:
                # Content-addressable key: hash of the content
                content_hash = hashlib.sha256(variant['data']).hexdigest()[:16]
                processed_key = f"{variant['type']}/{content_hash}.{variant['ext']}"
                
                s3.put_object(
                    Bucket='media-processed',
                    Key=processed_key,
                    Body=variant['data'],
                    ContentType=variant['content_type'],
                    CacheControl='public, max-age=31536000, immutable',
                )
                variant['s3_key'] = processed_key
                variant['cdn_url'] = f"https://cdn.example.com/{processed_key}"
            
            # Step 6: Update database
            db.update('media', media_id, {
                'status': 'PROCESSED',
                'variants': json.dumps([{
                    'type': v['type'],
                    'url': v['cdn_url'],
                    'width': v['width'],
                    'height': v['height'],
                    'size': v['size'],
                } for v in variants]),
                'metadata': json.dumps(metadata),
                'processed_at': datetime.utcnow(),
            })
            
            # Step 7: Notify client (WebSocket, push, or polling)
            notify_user(message['user_id'], {
                'event': 'media_processed',
                'media_id': media_id,
                'variants': [v['cdn_url'] for v in variants],
            })
            
        except Exception as e:
            db.update('media', media_id, {
                'status': 'FAILED',
                'error': str(e),
            })
            raise  # SQS will retry based on redrive policy
```

### Processing Times

```
Image processing (per image):
  Resize to 3 variants (thumb, medium, full): ~200ms
  Compress (WebP conversion): ~100ms
  Total per image: ~300ms
  
  At 100M images/day: 100M × 0.3s = 30M CPU-seconds = 347 CPU-days
  With 500 workers: 347 / 500 = 0.7 days → completes well within 24 hours.

Video transcoding (per video):
  1-minute 1080p video → 720p transcode: ~30 seconds (GPU)
  1-minute 1080p video → all quality levels (5): ~3 minutes (GPU)
  1-hour video → all quality levels: ~3 hours (GPU)
  
  With hardware encoding (NVENC): 3-5x faster than CPU encoding.
```

---

## Image Processing — Thumbnails & Variants

### Standard Image Variants

```
For a photo-sharing platform (Instagram-style):

  Original upload: 4032 × 3024 px, 5.2 MB (JPEG)

  Variant 1: Thumbnail (150 × 150)
    Use: Grid view, notifications, comments
    Size: ~8 KB (WebP)
    Crop: Center square crop

  Variant 2: Standard (1080 × 810)
    Use: Feed view, detail page
    Size: ~120 KB (WebP)
    Crop: Fit within 1080px width, maintain aspect ratio

  Variant 3: Full resolution (max 2048 × 2048)
    Use: Zoom view, download
    Size: ~400 KB (WebP)
    Crop: Fit within 2048px, maintain aspect ratio

  Total per photo: Original (5.2 MB) + 3 variants (~528 KB) = 5.7 MB stored
  Served: 99% of requests hit Standard (120 KB). Efficient!
```

### Image Processing Code

```python
from PIL import Image
import io

class ImageVariantGenerator:
    """Generates image variants for a photo platform."""
    
    VARIANTS = [
        {'name': 'thumbnail', 'max_size': 150,  'quality': 80, 'crop': 'square'},
        {'name': 'standard',  'max_size': 1080, 'quality': 85, 'crop': 'fit'},
        {'name': 'full',      'max_size': 2048, 'quality': 90, 'crop': 'fit'},
    ]
    
    def generate_variants(self, raw_data: bytes) -> list:
        img = Image.open(io.BytesIO(raw_data))
        
        # Strip EXIF data (privacy: remove GPS, camera info)
        # Keep orientation info
        img = self._strip_exif_preserve_orientation(img)
        
        variants = []
        for spec in self.VARIANTS:
            variant_img = self._resize(img, spec)
            
            # Convert to WebP for smaller file size
            buffer = io.BytesIO()
            variant_img.save(buffer, format='WEBP', quality=spec['quality'])
            data = buffer.getvalue()
            
            variants.append({
                'type': spec['name'],
                'data': data,
                'ext': 'webp',
                'content_type': 'image/webp',
                'width': variant_img.width,
                'height': variant_img.height,
                'size': len(data),
            })
        
        return variants
    
    def _resize(self, img, spec):
        if spec['crop'] == 'square':
            # Center crop to square, then resize
            size = min(img.width, img.height)
            left = (img.width - size) // 2
            top = (img.height - size) // 2
            img = img.crop((left, top, left + size, top + size))
            return img.resize((spec['max_size'], spec['max_size']), Image.LANCZOS)
        else:
            # Fit within max_size, maintain aspect ratio
            img.thumbnail((spec['max_size'], spec['max_size']), Image.LANCZOS)
            return img
```

### Format Comparison

```
Original JPEG (4032×3024): 5,200 KB

  JPEG (quality 85): 420 KB  (1x baseline)
  WebP (quality 85): 280 KB  (33% smaller than JPEG)
  AVIF (quality 85): 200 KB  (52% smaller than JPEG)
  
Browser support (2024):
  JPEG: 100%
  WebP: 97% (all modern browsers)
  AVIF: 85% (Chrome, Firefox, Safari 16+)

Recommendation:
  Serve WebP as default (97% support, 33% smaller).
  Serve AVIF where supported (Content Negotiation via Accept header).
  Fallback to JPEG for old browsers.

  Implementation: CDN-level content negotiation
    Request: Accept: image/avif, image/webp, image/jpeg
    CDN: Serve AVIF if available, else WebP, else JPEG.
    
    Or: Generate all 3 formats, select at CDN edge.
```

---

## Video Transcoding Pipeline

### Why Transcode?

```
Raw upload: 4K video, H.264, 50 Mbps, 1.2 GB for 3 minutes.

Problems:
  1. Too large: 1.2 GB → mobile users on 4G can't stream this.
  2. Wrong codec: Some devices don't support H.264 High Profile.
  3. Single quality: User with slow internet sees buffering.
  4. No seeking: Raw file may not have proper keyframe intervals.

Solution: Transcode into multiple quality levels and formats.
  
  Output variants:
    240p  → 400 Kbps  (2G/3G, low-end devices)
    360p  → 800 Kbps  (3G, emerging markets)
    480p  → 1.5 Mbps  (4G, standard mobile)
    720p  → 3 Mbps    (WiFi, tablets)
    1080p → 6 Mbps    (WiFi, desktop)
    4K    → 16 Mbps   (Fiber, smart TV)

  Each level: Segmented into 2-6 second chunks for adaptive streaming.
```

### Transcoding Architecture

```
┌─────────────┐    S3 Event    ┌──────────────┐    ┌─────────────────┐
│ S3 (raw      │──────────────→│ Transcode     │───→│ Worker Fleet    │
│  video)      │               │ Queue (SQS)   │    │ (GPU instances) │
└─────────────┘               └──────────────┘    └────────┬────────┘
                                                           │
                                              ┌────────────▼────────────┐
                                              │ Transcode Job Manager   │
                                              │                         │
                                              │ Split into segments:    │
                                              │   Segment 1 (0:00-0:10)│
                                              │   Segment 2 (0:10-0:20)│
                                              │   ...                   │
                                              │                         │
                                              │ Each segment × 6 levels│
                                              │   = parallel jobs       │
                                              └────────────┬────────────┘
                                                           │
                                              ┌────────────▼────────────┐
                                              │ Parallel Transcoding     │
                                              │                         │
                                              │ Seg1-240p → Worker A   │
                                              │ Seg1-480p → Worker B   │
                                              │ Seg1-1080p → Worker C  │
                                              │ Seg2-240p → Worker D   │
                                              │ ...                     │
                                              └────────────┬────────────┘
                                                           │
                                              ┌────────────▼────────────┐
                                              │ Assembly + Manifest      │
                                              │                         │
                                              │ Combine segments        │
                                              │ Generate HLS/DASH       │
                                              │ manifest files          │
                                              │ Upload to S3            │
                                              └─────────────────────────┘
```

### HLS Manifest Example

```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=426x240
/video/abc123/240p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
/video/abc123/360p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=854x480
/video/abc123/480p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720
/video/abc123/720p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080
/video/abc123/1080p/playlist.m3u8

Player reads this manifest, picks the quality level based on
current bandwidth, and can switch mid-stream (adaptive bitrate).
```

---

## Content-Addressable Storage

### The Core Idea

```
Traditional URL:
  https://cdn.example.com/users/42/profile.jpg
  
  User updates profile photo → same URL → CDN has stale cache!
  Must invalidate cache at 200+ edge locations → slow, expensive.

Content-addressable URL:
  https://cdn.example.com/img/a1b2c3d4e5f6.jpg
  
  The filename IS the hash of the content.
  hash(photo_bytes) = "a1b2c3d4e5f6"
  
  New photo → new hash → new URL → CDN fetches from origin (cache miss).
  Old URL still valid → old references still work.
  
  NO CACHE INVALIDATION NEEDED. Ever.
```

### Implementation

```python
import hashlib

def content_addressable_key(data: bytes, variant_type: str, extension: str) -> str:
    """Generate content-addressable S3 key from file content."""
    content_hash = hashlib.sha256(data).hexdigest()[:16]
    return f"{variant_type}/{content_hash}.{extension}"
    # Example: "thumbnail/a1b2c3d4e5f6g7h8.webp"

# When processing an image:
thumbnail_data = generate_thumbnail(raw_data)
key = content_addressable_key(thumbnail_data, 'thumbnail', 'webp')
# key = "thumbnail/a1b2c3d4e5f6g7h8.webp"

s3.put_object(
    Bucket='media-processed',
    Key=key,
    Body=thumbnail_data,
    CacheControl='public, max-age=31536000, immutable',  # Cache forever!
)
# Cache-Control: immutable → CDN and browser cache this indefinitely.
# Content never changes (because the URL IS the hash of the content).

cdn_url = f"https://cdn.example.com/{key}"
# https://cdn.example.com/thumbnail/a1b2c3d4e5f6g7h8.webp
```

### Benefits of Content-Addressable URLs

```
1. NO CACHE INVALIDATION
   Different content → different hash → different URL → no stale cache.
   Set Cache-Control: immutable, max-age=31536000 (1 year).
   CDN, browser, and intermediate proxies cache aggressively.

2. DEDUPLICATION
   If two users upload the same photo → same hash → same S3 key.
   Store once, serve everywhere. Natural deduplication.
   Savings: Instagram reports ~15% dedup rate on photos.

3. INTEGRITY VERIFICATION
   Download file, hash it, compare to URL hash.
   If they don't match → file corrupted in transit → retry.

4. IMMUTABLE INFRASTRUCTURE
   Processed files are never overwritten. Append-only.
   No race conditions, no partial writes, no corruption.
   Rollback: Just point metadata back to old URL (old content still exists).

5. CDN CACHE HIT RATES
   Content-addressable + immutable + long TTL → 95-99% cache hit rate.
   vs mutable URLs with short TTL → 60-80% cache hit rate.
```

### Metadata ↔ URL Mapping

```
The database maps media IDs to content-addressable URLs:

  media table:
    media_id: "m-abc123"
    user_id: "user:42"
    variants: {
      "thumbnail": "https://cdn.example.com/thumbnail/a1b2c3d4.webp",
      "standard":  "https://cdn.example.com/standard/e5f6g7h8.webp",
      "full":      "https://cdn.example.com/full/i9j0k1l2.webp"
    }
    
  When user updates their photo:
    1. New photo processed → new hashes → new URLs
    2. Database updated: variants → new URLs
    3. Old URLs still valid in CDN cache → old cached pages still work
    4. New pages load new URLs → CDN fetches from origin (cache miss)
    5. After TTL, old content expires from CDN (or never accessed again)
```

---

## CDN Architecture & Edge Caching

### CDN Request Flow

```
User in Tokyo requests: https://cdn.example.com/standard/e5f6g7h8.webp

  Step 1: DNS resolution → nearest CloudFront PoP (Tokyo)
  
  Step 2: Edge check
    Tokyo PoP cache: HIT? → Return cached response (5ms). Done!
    Tokyo PoP cache: MISS → continue.
  
  Step 3: Regional cache check (optional, CloudFront uses "Regional Edge Caches")
    Asia Pacific regional cache: HIT? → Return (20ms). Done!
    MISS → continue.
  
  Step 4: Origin fetch
    Fetch from S3 origin (us-east-1): ~150ms
    Cache at Tokyo PoP: TTL = 1 year (immutable content).
    Return to user.

  Next request for the same URL from Tokyo: Step 2 → HIT → 5ms.

  Cache hierarchy:
    L1: Browser cache → 0ms (if previously loaded)
    L2: CDN Edge PoP → 5ms (200+ locations worldwide)
    L3: CDN Regional Cache → 20ms (~12 regional caches)
    L4: S3 Origin → 50-200ms (origin region)
```

### CDN Cache Configuration

```
CloudFront distribution config:

  Origin: S3 bucket (media-processed)
  
  Cache behavior for /thumbnail/*, /standard/*, /full/*:
    TTL: 31536000 (1 year) — content-addressable, never changes
    Cache-Control: public, max-age=31536000, immutable
    Compress: Yes (gzip/brotli for applicable types)
    Viewer Protocol: HTTPS only
    
  Cache behavior for /video/*/playlist.m3u8:
    TTL: 60 seconds — manifest can change (new segments added for live)
    
  Cache behavior for /video/*/segments/*:
    TTL: 31536000 (1 year) — segments are immutable once created
    
  Custom headers:
    X-Cache-Status: HIT or MISS (for debugging)
```

### CDN Numbers at Scale

```
Instagram-scale CDN:
  Photo requests: ~50B per day
  CDN cache hit rate: 95%
  Requests reaching origin: 2.5B/day (5%)
  
  Bandwidth served by CDN: ~15 PB/day
  Bandwidth from origin: ~750 TB/day
  
  CDN cost: ~$0.02/GB = $300K/day for 15 PB
  Without CDN (all from origin): $0.09/GB = $1.35M/day
  CDN saves: $1M/day = $365M/year

Netflix CDN (Open Connect):
  Bandwidth: ~300 Gbps average, peaks to 500+ Gbps
  Uses its own CDN (Open Connect Appliances placed in ISP data centers)
  Cache hit rate: 95%+ (most content is popular catalog)
```

---

## Cache Invalidation Strategies

### Strategy 1: Content-Addressable URLs (Best — No Invalidation)

```
New content → new hash → new URL → no invalidation needed.
Set TTL = forever. Cache-Control: immutable.

Limitations:
  - Requires changing all references to the old URL.
  - Database update must propagate before users see the new URL.
  - Not applicable for truly mutable URLs (e.g., /api/user/42/profile).
```

### Strategy 2: Versioned URLs

```
https://cdn.example.com/users/42/profile.jpg?v=3

  v=1 → original photo
  v=2 → updated photo
  v=3 → latest photo

  Same as content-addressable but uses a version counter.
  Database stores the current version.
  
  Cache-Control: max-age=31536000 (versioned URL is effectively immutable)
  Update: Change v= parameter → new URL → CDN fetches new version.
```

### Strategy 3: Explicit Purge (When Needed)

```
Use when content-addressable URLs aren't possible:
  CloudFront: create_invalidation(Paths=['/users/42/profile.jpg'])
  Akamai: purge_cache(url='...')
  
  Cost: CloudFront charges $0.005 per invalidation path (first 1000/month free).
  Latency: 1-5 minutes for global purge across all PoPs.
  
  Use sparingly: Emergency content removal, legal takedowns, critical fixes.
  Not suitable for routine updates (too slow, too expensive at scale).
```

### Strategy 4: Short TTL + Stale-While-Revalidate

```
Cache-Control: max-age=60, stale-while-revalidate=300

  CDN caches for 60 seconds (fresh).
  After 60 seconds: Serve stale version while fetching new version in background.
  User always gets a response instantly (stale for up to 5 minutes).
  
  Use for: Metadata that changes occasionally (user bios, post text).
  Not ideal for media (content-addressable is better).
```

---

## Adaptive Bitrate Streaming (ABR)

### How ABR Works

```
Player starts streaming a video:

  1. Download master manifest (HLS: .m3u8, DASH: .mpd)
     Contains list of quality levels and their bitrates.

  2. Measure network bandwidth: ~5 Mbps

  3. Select quality level: 720p (3 Mbps) — best fit under 5 Mbps

  4. Download segments at 720p:
     Segment 1 (0:00-0:04) → 720p → plays
     Segment 2 (0:04-0:08) → 720p → plays

  5. Bandwidth drops to 1 Mbps (user entered elevator):
     Player switches DOWN: Segment 3 → 360p (800 Kbps)
     No buffering. Quality degrades gracefully.

  6. Bandwidth recovers to 10 Mbps:
     Player switches UP: Segment 10 → 1080p (6 Mbps)
     Quality improves transparently.

  User perceives: Seamless playback with varying quality.
  No buffering events (the #1 cause of user abandonment for video).
```

### Segment + Manifest Structure in S3

```
S3 bucket: media-processed

  /video/{video_id}/manifest.m3u8          ← master manifest
  /video/{video_id}/240p/playlist.m3u8     ← quality-level playlist
  /video/{video_id}/240p/segment_001.ts    ← 4-second video chunk
  /video/{video_id}/240p/segment_002.ts
  ...
  /video/{video_id}/720p/playlist.m3u8
  /video/{video_id}/720p/segment_001.ts
  /video/{video_id}/720p/segment_002.ts
  ...
  /video/{video_id}/1080p/playlist.m3u8
  /video/{video_id}/1080p/segment_001.ts
  /video/{video_id}/1080p/segment_002.ts
  ...

CDN caching:
  Manifest: Short TTL (60s) — can change for live streams.
  Segments: Long TTL (1 year) — immutable once created.
  
  95%+ of requests are for segments → 95%+ cache hit rate.
```

---

## Image Optimization — On-the-Fly vs Pre-Generated

### Pre-Generated (Instagram Approach)

```
On upload: Generate ALL variants ahead of time.
  Thumbnail (150px), Standard (1080px), Full (2048px) in JPEG, WebP, AVIF.
  = 9 files per image.

Pros:
  ✅ Fastest serving: Variant already exists → direct CDN serve.
  ✅ No compute on read path.
  ✅ Simple: No transformation logic at edge.

Cons:
  ❌ Storage cost: 9 files per image × 100M images/day.
  ❌ Processing delay: Must generate all 9 before image is ready.
  ❌ Inflexible: Adding a new variant requires re-processing ALL images.

Best for: High-traffic platforms where every image is viewed millions of times.
  The upfront cost is amortized over millions of views.
```

### On-the-Fly (Imgix/Thumbor/CloudFront Functions Approach)

```
On upload: Store only the original.
On first request for a variant: Generate it, cache in CDN.

  Request: https://cdn.example.com/img/abc123.jpg?w=150&h=150&fmt=webp
  CDN: MISS → Origin (resize service) → generate thumbnail → cache → serve.
  Next request: CDN HIT → serve from cache (5ms).

Pros:
  ✅ Store only originals → less storage.
  ✅ Infinite variants: Any size, format, quality via URL params.
  ✅ No pre-processing delay.
  ✅ Adding new variant = just use a new URL (no reprocessing).

Cons:
  ❌ First request is slow (~200ms for resize + origin fetch).
  ❌ Resize service is on the read path → must be highly available.
  ❌ Long-tail images may never be cached → always go to origin.

Best for: E-commerce (products shown at many sizes), CMS platforms.
```

### Hybrid (Recommended for Most Systems)

```
Pre-generate the TOP 3 most-used variants on upload (thumbnail, standard, full).
Use on-the-fly for everything else (rare sizes, new formats).

  Upload → generate thumbnail + standard + full → S3 → CDN (pre-cached).
  Rare variant request (e.g., 400×300 crop for email) → on-the-fly resize.
  
  95% of traffic: Pre-generated → instant CDN serve.
  5% of traffic: On-the-fly → 200ms first request, then cached.
```

---

## Storage Tiers & Lifecycle Policies

### S3 Storage Classes

```
Raw uploads (first 24 hours):
  S3 Standard: $0.023/GB/month
  High availability, fast access (processing needs to read the raw file).

Processed variants (active serving):
  S3 Standard: $0.023/GB/month
  CDN serves most traffic. S3 is the origin for cache misses.

Raw uploads (after processing):
  S3 Intelligent-Tiering or S3 Infrequent Access: $0.0125/GB/month
  Raw files rarely accessed after processing (only for re-processing).

Old media (>90 days, rarely accessed):
  S3 Glacier Instant Retrieval: $0.004/GB/month
  Accessible in milliseconds, but storage is 6x cheaper.

Archive (>1 year, compliance/legal):
  S3 Glacier Deep Archive: $0.00099/GB/month
  Retrieval in 12 hours. Cheapest possible storage.
```

### Lifecycle Policy

```json
{
  "Rules": [
    {
      "ID": "Raw-to-IA",
      "Filter": { "Prefix": "raw/" },
      "Transitions": [
        { "Days": 1, "StorageClass": "STANDARD_IA" },
        { "Days": 90, "StorageClass": "GLACIER" }
      ]
    },
    {
      "ID": "Processed-tiering",
      "Filter": { "Prefix": "thumbnail/" },
      "Transitions": [
        { "Days": 90, "StorageClass": "INTELLIGENT_TIERING" }
      ]
    }
  ]
}
```

### Cost Savings

```
Without lifecycle policies (everything in S3 Standard):
  500 TB/day × 365 days × $0.023/GB = $4.2M/month
  
With lifecycle policies:
  Active (30 days, Standard):    15 PB × $0.023 = $345K
  Warm (60 days, IA):           30 PB × $0.0125 = $375K
  Cold (rest of year, Glacier): 137 PB × $0.004 = $548K
  Total: ~$1.27M/month
  
  Savings: $2.93M/month = $35M/year
```

---

## Upload Validation & Security

### Validation Pipeline

```
Step 1: Content-Type validation
  Check actual file magic bytes, not just the extension.
  A .jpg file could contain a PHP webshell!
  
  import magic
  file_type = magic.from_buffer(data[:2048], mime=True)
  if file_type not in ALLOWED_TYPES:
      reject("Invalid file type")

Step 2: Dimension and duration limits
  Images: Max 50 MP (megapixels). Reject decompression bombs.
  Videos: Max 60 minutes. Max 4K resolution.
  
Step 3: File size limits
  Images: Max 20 MB
  Videos: Max 2 GB (multipart upload for larger)
  
Step 4: Malware scanning (optional)
  Route through ClamAV or AWS GuardDuty.
  Scan before making content publicly accessible.
  
Step 5: NSFW / Content moderation
  AWS Rekognition or Google Cloud Vision API.
  Flag content for human review if confidence > 70%.
  Block if confidence > 95%.
  
Step 6: EXIF stripping
  Remove GPS coordinates, camera serial numbers, and other PII.
  Preserve orientation data.
```

### Presigned URL Security

```
Protections:
  1. Short expiration: 15 minutes (presigned URL expires).
  2. Content-Type restriction: URL only accepts specified MIME type.
  3. Content-Length restriction: URL rejects files above size limit.
  4. One-time use: Application marks media_id as uploaded after first use.
  5. Authentication: User must be authenticated to get a presigned URL.
  6. Rate limiting: Max 10 upload requests per minute per user.
```

---

## Processing Status & Notifications

### Status State Machine

```
  PENDING_UPLOAD → UPLOADED → PROCESSING → PROCESSED
                                  ↓
                               FAILED → RETRY → PROCESSING (up to 3 retries)
                                  ↓
                             PERMANENTLY_FAILED
```

### Polling vs Push for Processing Status

```
Polling:
  Client: GET /api/media/{id}/status every 2 seconds
  Response: { "status": "PROCESSING", "progress": 45 }
  
  Simple. Works everywhere. Wastes bandwidth if processing is slow.

Push (WebSocket):
  Server pushes: { "media_id": "abc", "status": "PROCESSED", "urls": [...] }
  
  Real-time. No wasted requests. More complex (WebSocket connection management).

Push (Server-Sent Events):
  GET /api/media/{id}/events (SSE stream)
  data: {"status": "PROCESSING", "progress": 45}
  data: {"status": "PROCESSED", "urls": [...]}
  
  Lighter than WebSocket. Unidirectional. Auto-reconnects.

Recommendation: SSE for processing status. 
  Simple, real-time, no wasted requests, auto-reconnect.
```

---

## Failure Handling & Retries

### SQS Dead Letter Queue (DLQ)

```
Processing message flow:

  SQS Queue → Worker → Success → Message deleted
                    → Failure → Message returns to queue (invisible for 30 sec)
                    → Retry 1 → Failure → Retry 2 → Failure → Retry 3
                    → Moved to DLQ (Dead Letter Queue)

  DLQ: Holds permanently failed messages for manual inspection.
  
  Redrive policy:
    maxReceiveCount: 3  (retry 3 times before DLQ)
    
  Common failures:
    - Corrupted file (can't decode) → won't succeed on retry → DLQ
    - Transient S3 error → succeeds on retry
    - Out of memory (file too large) → needs larger instance → DLQ + alert
```

### Idempotent Processing

```
If a message is processed twice (SQS at-least-once delivery):
  Same raw file → same processing → same content hash → same S3 key.
  S3 PutObject overwrites with identical content → no harm.
  Database update is idempotent (same URLs).
  
  Content-addressable storage makes processing naturally idempotent.
```

---

## Cost Optimization

### Key Cost Levers

```
1. CDN cache hit rate → higher = less origin traffic
   Target: >90%. Achieved via content-addressable URLs + long TTL.
   Every 1% improvement in cache hit rate saves ~$30K/month at scale.

2. Storage tiering → move cold data to cheaper tiers
   Raw files → S3-IA after 1 day. Glacier after 90 days.
   Savings: 70-80% on storage costs.

3. Spot instances for processing workers
   Media processing is fault-tolerant (can retry).
   Spot instances: 60-70% cheaper than on-demand.
   Run processing workers on Spot. DLQ catches interrupted jobs.

4. Right-size image variants
   Don't generate 10 variants if 3 cover 95% of use cases.
   Each unnecessary variant = storage cost + processing time.

5. Format optimization
   WebP: 33% smaller than JPEG → 33% less CDN bandwidth → 33% less cost.
   AVIF: 52% smaller → 52% less cost.
   
6. Lazy processing for unpopular content
   Only generate full variant set for content that gets >100 views.
   For <100 views: Generate only thumbnail + standard.
   Add variants on-demand as popularity grows.
```

---

## Performance Benchmarks

```
Upload (presigned URL to S3):
  5 MB photo:  1-3 seconds (depends on client bandwidth)
  100 MB video: 5-15 seconds (multipart, parallel)
  1 GB video:   30-120 seconds (multipart, 10 parallel parts)

Processing:
  Image (3 variants + WebP): 300ms per image
  1-min video (6 quality levels): 3 minutes (GPU)
  1-hour video (6 quality levels): 3 hours (GPU), 1 hour (hardware encoder)

CDN serving:
  Cache HIT latency:  5-15ms (edge PoP)
  Cache MISS latency: 50-200ms (origin fetch + edge cache)
  Cache hit rate: 90-99% (content-addressable URLs)

End-to-end (upload to viewable):
  Image: 2-5 seconds (upload + process + CDN propagation)
  Short video (1 min): 1-5 minutes
  Long video (1 hour): 3-6 hours (all quality levels)
  
  Instagram: Photos available within 5 seconds of upload.
  YouTube: Videos in "processing" for 5 minutes to several hours.
```

---

## Real-World Production Patterns

### Pattern 1: Instagram Photo Pipeline

```
Upload: Presigned URL → S3
Processing: 
  - Strip EXIF (privacy)
  - Generate 3 sizes (thumbnail, standard, full) in WebP + JPEG
  - Run through content moderation (Rekognition)
  - Store content-addressable URLs in Cassandra
Serving: Facebook's CDN (200+ PoPs)
Scale: 100M+ photos/day, 50B+ photo views/day
```

### Pattern 2: Netflix Video Pipeline

```
Upload: Studio uploads master file (ProRes/DNxHD, 100+ GB)
Processing:
  - Encode to 2000+ variants (quality levels × codecs × devices)
  - Per-title encoding: ML model determines optimal bitrate ladder per title
  - Chunks of 4 seconds each for ABR streaming
  - Generate subtitles, thumbnails (one per scene change)
Serving: Open Connect CDN (ISP-embedded servers)
Scale: Thousands of new titles/month, 300+ Gbps sustained streaming
```

### Pattern 3: TikTok/Reels Short Video

```
Upload: Mobile app → presigned URL → S3 equivalent
Processing:
  - Transcode to 3-4 quality levels (240p, 480p, 720p, 1080p)
  - First frame thumbnail
  - Audio extraction for music recognition
  - Content moderation (real-time, <30 seconds)
Serving: Global CDN with edge caching
Special: Processing must complete in <30 seconds for user experience.
  Use GPU instances with hardware encoders for fast transcode.
Scale: 1B+ daily video views
```

### Pattern 4: E-Commerce Product Images (Amazon)

```
Upload: Seller uploads product photos via portal
Processing:
  - Resize to 20+ variants (search results, detail page, zoom, mobile)
  - Background removal (ML-based)
  - Color correction and normalization
  - 360° view assembly (for 3D product views)
  - Content-addressable storage
Serving: CloudFront CDN
Special: On-the-fly resize for uncommon dimensions.
Scale: Billions of product images, trillions of image views/year
```

---

## Interview Talking Points & Scripts

### Script 1: Upload Path

> *"For media uploads, I'd use presigned URLs so clients upload directly to S3, bypassing the application server entirely. The API server generates a short-lived presigned PUT URL (15-minute expiry), the client uploads directly to S3 at S3's full bandwidth, and our servers never see the bytes. At 100M uploads per day of 5 MB each, that's 500 TB of bandwidth our servers DON'T need to handle — saving ~$17M per year compared to proxying through EC2."*

### Script 2: Async Processing

> *"Once the raw file lands in S3, an event triggers an SQS message. Processing workers pick up the message and generate all needed variants: for images, 3 sizes in WebP format; for videos, 6 quality levels segmented for adaptive bitrate streaming. Processing is fully async — the user gets an immediate 'upload complete' while workers handle the transformation. SQS retry with DLQ handles failures. Workers run on Spot instances for 60% cost savings."*

### Script 3: Content-Addressable URLs

> *"I'd use content-addressable URLs where the filename is the hash of the content: `cdn.example.com/img/a1b2c3d4.webp`. Since the URL changes whenever the content changes, cache invalidation is eliminated entirely. I can set `Cache-Control: immutable, max-age=31536000` — CDN, browsers, and proxies all cache aggressively. This pushes cache hit rates to 95-99%, reducing origin load by 20x compared to mutable URLs with short TTLs."*

### Script 4: CDN Serving

> *"Media is served through a CDN with 200+ edge PoPs worldwide. A user in Tokyo requesting a photo gets it from the Tokyo edge cache in 5ms, not from the S3 origin in Virginia at 200ms. With content-addressable URLs and immutable cache headers, 95%+ of requests are cache hits. For video, HLS manifests point to segment files at each quality level — the player adapts quality based on bandwidth, downloading 4-second chunks from the nearest edge."*

### Script 5: Video Transcoding

> *"Video transcoding is the most compute-intensive part. I'd split the video into segments and transcode each segment in parallel across a fleet of GPU workers — 6 quality levels times N segments, all running concurrently. A 1-hour video that would take 3 hours sequentially finishes in 15-20 minutes with parallel processing. The output is HLS segments and a master manifest listing all quality levels. The player picks the right quality adaptively."*

### Script 6: Cost Optimization

> *"Three key cost levers: First, content-addressable URLs with immutable cache headers push CDN hit rates above 95% — every 1% improvement saves ~$30K/month. Second, S3 lifecycle policies move raw uploads to Infrequent Access after 1 day and Glacier after 90 days — 70% storage savings. Third, processing workers on Spot instances — media processing is retry-safe, so 60% compute savings with Spot. Together, these cut media infrastructure costs by 50-70%."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Uploading media through the app server

**Bad**: "Client sends the photo to our API server, which writes it to S3."
**Fix**: Presigned URLs. Client uploads directly to S3. Zero bandwidth/CPU on app servers.

### ❌ Mistake 2: Synchronous processing

**Bad**: "The API server resizes the image and returns the URL."
**Fix**: Async pipeline. S3 event → SQS → workers. User gets immediate confirmation. Processing happens in background.

### ❌ Mistake 3: Mutable URLs with cache invalidation

**Bad**: "When the user updates their photo, we invalidate the CDN cache."
**Fix**: Content-addressable URLs. New content = new hash = new URL. No invalidation needed. Cache forever.

### ❌ Mistake 4: No CDN

**Bad**: "We serve images directly from S3."
**Fix**: CDN with edge caching. 5ms edge response vs 200ms origin response. 95% cache hit rate = 20x less S3 load.

### ❌ Mistake 5: Single video quality

**Bad**: "We transcode the video to 1080p and serve it."
**Fix**: Adaptive bitrate streaming. 6 quality levels + HLS/DASH. Player adapts to bandwidth. No buffering.

### ❌ Mistake 6: No storage tiering

**Bad**: "All media stays in S3 Standard forever."
**Fix**: Lifecycle policies. Raw → S3-IA after 1 day → Glacier after 90 days. 70% storage savings.

### ❌ Mistake 7: Not mentioning processing failures

**Bad**: "The processing always succeeds."
**Fix**: SQS retry (3 attempts) → DLQ for permanent failures. Idempotent processing via content-addressable keys.

### ❌ Mistake 8: Not mentioning content validation

**Bad**: "We accept any uploaded file."
**Fix**: Validate magic bytes (not just extension), check dimensions, file size limits, malware scan, NSFW detection.

---

## Media Pipeline by System Design Problem

| Problem | Media Type | Variants | CDN Strategy | Special Considerations |
|---|---|---|---|---|
| **Instagram** | Photos | 3 sizes × 2 formats | Content-addressable, immutable | EXIF stripping, NSFW detection |
| **Netflix** | Long video | 2000+ variants | Open Connect (ISP CDN) | Per-title encoding, DRM |
| **TikTok** | Short video | 4 quality levels | Edge CDN, <30s processing | Speed critical, hardware encoders |
| **YouTube** | Any video | 6 quality levels + HLS | Google CDN | Processing time varies 5min-hours |
| **Twitter** | Images + GIF | 3 sizes, GIF→MP4 conversion | Content-addressable | Convert GIF to MP4 for 90% size reduction |
| **E-commerce** | Product images | 20+ sizes, background removal | On-the-fly resize + CDN | ML-based background removal |
| **WhatsApp** | Photos + video | Compressed for mobile | CDN + E2E encryption | Client-side encryption before upload |
| **Slack** | Files + preview | Thumbnail + preview | CDN per workspace | Workspace-scoped access control |
| **Google Drive** | Any file | Preview + thumbnail | CDN with access tokens | Conversion (DOCX→PDF preview) |
| **Spotify** | Audio | Multiple bitrates (OGG) | CDN with audio segments | Gapless playback, crossfade |

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│         CDN & MEDIA PROCESSING PIPELINE — CHEAT SHEET                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  UPLOAD: Presigned URL → direct to S3 (0 bytes through app server)   │
│    15-min expiry. Multipart for >100 MB. Validate on arrival.        │
│                                                                      │
│  PROCESSING: Async pipeline                                          │
│    S3 Event → SQS → Worker Fleet (Spot instances)                    │
│    Images: 3 variants × WebP (300ms/image)                           │
│    Videos: 6 quality levels × HLS segments (3 hrs/hr on GPU)         │
│    Parallel: Split video into segments, transcode all concurrently.  │
│                                                                      │
│  CONTENT-ADDRESSABLE STORAGE:                                        │
│    URL = hash of content: cdn.example.com/img/{sha256}.webp          │
│    New content → new hash → new URL → NO CACHE INVALIDATION.         │
│    Cache-Control: immutable, max-age=31536000                        │
│    Natural deduplication (same content = same key).                   │
│                                                                      │
│  CDN:                                                                │
│    200+ edge PoPs. 5ms edge latency vs 200ms origin.                 │
│    Cache hit rate: 95-99% with content-addressable URLs.             │
│    Hierarchy: Browser → Edge PoP → Regional → Origin (S3).          │
│                                                                      │
│  ADAPTIVE BITRATE (video):                                           │
│    HLS/DASH manifests. 4-second segments per quality level.          │
│    Player switches quality based on bandwidth. No buffering.         │
│                                                                      │
│  STORAGE TIERS:                                                      │
│    Hot (processed, active): S3 Standard                              │
│    Warm (raw, after 1 day): S3-IA (46% cheaper)                      │
│    Cold (after 90 days):    Glacier (83% cheaper)                    │
│                                                                      │
│  FAILURES:                                                           │
│    SQS retry (3 attempts) → DLQ for permanent failures.             │
│    Idempotent: Content-addressable keys → safe to reprocess.         │
│                                                                      │
│  COST:                                                               │
│    CDN hit rate ↑ = origin cost ↓ (95% hit = 20x less S3 load).     │
│    Spot instances for processing: 60% compute savings.               │
│    Storage lifecycle: 70% storage savings.                           │
│    WebP over JPEG: 33% less CDN bandwidth.                           │
│                                                                      │
│  SECURITY:                                                           │
│    Magic byte validation (not just extension).                       │
│    EXIF stripping (privacy: remove GPS, camera info).                │
│    NSFW detection (ML-based content moderation).                     │
│    Presigned URL: Short TTL, content-type restriction.               │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 18: Blob and Media Storage** — S3, object storage fundamentals
- **Topic 19: CDN and Edge Caching** — CDN architecture and caching strategies
- **Topic 27: Security** — Upload validation, content security
- **Topic 35: Cost vs Performance** — Storage tiering, CDN cost optimization
- **Topic 40: Redis Deep Dive** — Caching media metadata
- **Topic 42: Job Scheduling** — Scheduling transcode jobs
- **Topic 55: Virtual Queue for Flash Sales** — Queue pattern reused for processing

---

*This document is part of the System Design Interview Deep Dive series.*
