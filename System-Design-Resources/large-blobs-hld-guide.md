# Handling Large Blobs - Complete HLD Guide

## Table of Contents
1. [Introduction](#introduction)
2. [The Problem](#the-problem)
3. [Solution Patterns](#solution-patterns)
4. [Upload Strategies](#upload-strategies)
5. [Download Strategies](#download-strategies)
6. [Real-World Architecture Examples](#real-world-architecture-examples)
7. [Optimization Techniques](#optimization-techniques)
8. [Decision Framework](#decision-framework)
9. [Interview Guide](#interview-guide)

---

## Introduction

**Large Blobs** (Binary Large Objects) are files too big to handle through traditional API servers - typically >10MB. Common examples:

- **Videos**: 100MB to 10GB+ (YouTube, TikTok, Netflix)
- **Images**: 5-50MB high-resolution (Instagram, Flickr, Pinterest)
- **Documents**: 10-500MB (Google Drive, Dropbox, Box)
- **Audio**: 5-50MB (Spotify, SoundCloud)
- **Datasets**: GB to TB (Research data, backups)
- **Software**: 100MB-10GB (App stores, game downloads)

**Key Challenges:**
- Upload/download through API servers is slow and wasteful
- Server resources tied up during transfer
- Timeout issues for large files
- Network interruptions require restart
- Expensive bandwidth costs

---

## The Problem

### Naive Approach: Routing Through Servers

```
┌──────────────────────────────────────────────────────────────────┐
│              NAIVE FILE UPLOAD (BAD ARCHITECTURE)                 │
└──────────────────────────────────────────────────────────────────┘

User uploads 2GB video
   ↓
┌────────────────────────┐
│   Client (Browser)     │
│   Upload speed: 10Mbps │
│   Time: 27 minutes     │
└────────┬───────────────┘
         │ HTTP POST with file in body
         │ 2GB traveling through network
         ▼
┌────────────────────────┐
│   API Server           │
│   • Receives 2GB       │
│   • Buffers in memory  │
│   • Server blocked     │
│   • 27 minutes tied up │
└────────┬───────────────┘
         │ Forward to storage
         │ Another 2GB transfer
         ▼
┌────────────────────────┐
│   S3 / Blob Storage    │
│   • Finally stored     │
│   • Total: 54 minutes  │
└────────────────────────┘

Problems:
├─ API server blocked for 27+ minutes
├─ Memory usage: 2GB per upload
├─ Only handles 1-2 concurrent uploads per server
├─ Bandwidth: 2x (client→server + server→S3)
├─ Single point of failure (if server crashes, upload lost)
├─ No resume capability
├─ Expensive (server costs + bandwidth)
└─ Scales poorly (need servers just to proxy files)

Cost Impact:
• 10 concurrent uploads = 10 servers @ $100/month = $1000/month
• Just to proxy files!
• S3 bandwidth: Also charged
• Total waste of resources
```

### Why Databases Are Wrong for Large Files

```
┌──────────────────────────────────────────────────────────────────┐
│              STORING BLOBS IN DATABASE (VERY BAD)                 │
└──────────────────────────────────────────────────────────────────┘

Storing 100MB video in PostgreSQL:

Problems:
├─ BLOB column consumes 100MB per row
├─ Query performance: Slow (reading entire table)
├─ Backup time: Hours longer
├─ Replication: 100MB propagated to replicas
├─ Memory: Large rows don't fit in cache
├─ Index bloat: B-trees get huge
└─ Cost: Database storage expensive ($0.10/GB vs S3 $0.023/GB)

Example Impact:
• 10K videos = 1TB in database
• Backup: 6 hours instead of 30 minutes
• Query: 10x slower
• Storage cost: $100/month vs $23/month

Rule of Thumb:
• < 1MB: OK in database (profile pictures)
• 1-10MB: Consider blob storage
• > 10MB: MUST use blob storage (S3, GCS, Azure Blob)
```

---

## Solution Patterns

### Pattern Overview

```
┌──────────────────────────────────────────────────────────────────┐
│              BLOB HANDLING SOLUTION PATTERNS                      │
└──────────────────────────────────────────────────────────────────┘

1. PRESIGNED URLs (Direct Upload/Download)
   Client ←presigned URL─ API Server
   Client ─upload────────→ S3 directly
   
   Benefits: No proxy, fast, scalable
   Use: Almost always (default choice)

2. MULTIPART UPLOAD (Large Files)
   Split file into chunks
   Upload chunks in parallel
   S3 assembles on completion
   
   Benefits: Faster, resumable
   Use: Files > 100MB

3. RESUMABLE UPLOAD (Unreliable Networks)
   Client uploads with checkpoints
   Can resume from last checkpoint
   
   Benefits: Reliable for mobile/slow networks
   Use: Mobile apps, large files

4. CDN DISTRIBUTION (Fast Downloads)
   Files served from edge locations
   Cached close to users
   
   Benefits: Low latency, high throughput
   Use: Public content, media files

5. CHUNKED DOWNLOAD (Large Files)
   Support HTTP range requests
   Download in chunks
   Enable seeking (videos)
   
   Benefits: Fast start, seeking support
   Use: Videos, large downloads
```

---

## Upload Strategies

### Strategy 1: Presigned URL Upload

```
┌──────────────────────────────────────────────────────────────────┐
│              PRESIGNED URL UPLOAD FLOW                            │
└──────────────────────────────────────────────────────────────────┘

Step 1: REQUEST UPLOAD URL
Client → API Server
   POST /api/upload/request
   Body: {
     filename: "video.mp4",
     size: 500000000,  // 500MB
     content_type: "video/mp4"
   }

API Server:
   • Validates user permissions
   • Generates unique file ID
   • Creates presigned S3 URL (expires in 1 hour)
   • Stores metadata in database
   Time: 50ms

Response:
{
  file_id: "file_abc123",
  upload_url: "https://s3.amazonaws.com/bucket/file_abc123?
               X-Amz-Signature=xxx&X-Amz-Expires=3600",
  expires_at: "2025-01-23T11:00:00Z"
}

Step 2: DIRECT UPLOAD TO S3
Client → S3 (bypassing API server)
   PUT to upload_url
   Body: File binary data
   
   Benefits:
   • API server not involved
   • S3 handles all transfer
   • Parallel uploads possible
   Time: Based on network speed

Step 3: NOTIFY COMPLETION
Client → API Server
   POST /api/upload/complete
   Body: {file_id: "file_abc123"}

API Server:
   • Update metadata (status: "uploaded")
   • Trigger post-processing (thumbnail, etc.)
   • Return success
   Time: 50ms

Total API server time: 100ms (vs 27 minutes in naive approach!)

Architecture:

┌─────────┐                    ┌─────────┐
│ Client  │─(1) Request────────▶│   API   │
│         │◀─── Upload URL─────│  Server │
│         │                    └─────────┘
│         │
│         │─(2) Direct Upload──┐
│         │                    │
│         │                    ▼
│         │              ┌─────────┐
│         │              │   S3    │
│         │              │         │
│         │              └─────────┘
│         │
│         │─(3) Complete───────▶API Server
└─────────┘

Benefits:
• API server: <1 second involvement
• S3 handles transfer (built for this)
• Scales infinitely
• Cost: S3 bandwidth only
• Secure: Presigned URL expires
```

### Strategy 2: Multipart Upload

```
┌──────────────────────────────────────────────────────────────────┐
│              MULTIPART UPLOAD FOR LARGE FILES                     │
└──────────────────────────────────────────────────────────────────┘

Use Case: Files > 100MB (recommended by AWS)

Flow:

Step 1: INITIATE MULTIPART UPLOAD
   Client → API → S3
   POST /initiate
   Response: {upload_id: "upload_xyz"}

Step 2: UPLOAD PARTS (In Parallel)
   Split file into 5MB-5GB chunks
   
   Part 1 (5MB) ────┐
   Part 2 (5MB) ────┤
   Part 3 (5MB) ────┼──→ S3 (parallel)
   ...              │
   Part 100 (5MB) ──┘
   
   Each part: Presigned URL
   Upload: Parallel (10-20 parts at once)
   
   Speed improvement:
   • Sequential: 27 minutes
   • Parallel (10 parts): 3 minutes
   • 9x faster!

Step 3: COMPLETE MULTIPART UPLOAD
   Client → API → S3
   POST /complete
   Body: {upload_id, parts: [
     {part_number: 1, etag: "abc"},
     {part_number: 2, etag: "def"},
     ...
   ]}
   
   S3 assembles parts into single file

Benefits:
✓ Parallel uploads (much faster)
✓ Resumable (retry failed parts only)
✓ Network fault tolerant
✓ Progress tracking (per part)

Example: 1GB File
• Parts: 200 × 5MB
• Sequential: 13 minutes
• Parallel (20 at once): 40 seconds
• 20x faster!
```

### Strategy 3: Resumable Upload

```
┌──────────────────────────────────────────────────────────────────┐
│              RESUMABLE UPLOAD PATTERN                             │
└──────────────────────────────────────────────────────────────────┘

Problem: Network interruption loses progress

Solution: Chunked upload with resume capability

Protocol (TUS Standard):

1. INITIATE
   POST /files
   Response: {
     upload_url: "/files/abc123",
     upload_offset: 0
   }

2. UPLOAD IN CHUNKS
   PATCH /files/abc123
   Headers:
     Upload-Offset: 0
     Content-Length: 5000000
   Body: [5MB chunk]
   
   Response:
     Upload-Offset: 5000000  // Next byte to upload

3. IF INTERRUPTED
   Client reconnects
   HEAD /files/abc123
   Response:
     Upload-Offset: 5000000  // Resume from here
   
   Client resumes from 5MB mark
   No need to re-upload first 5MB

4. COMPLETE
   When Upload-Offset = File Size
   Server marks upload complete

Benefits:
• Network fault tolerant
• Mobile-friendly
• No wasted bandwidth
• Clear progress tracking

Use Cases:
• Mobile apps (unreliable networks)
• Large files (>1GB)
• Slow connections
• Critical uploads (can't afford to restart)
```

---

## Download Strategies

### Strategy 1: Presigned URL Download

```
┌──────────────────────────────────────────────────────────────────┐
│              PRESIGNED URL DOWNLOAD FLOW                          │
└──────────────────────────────────────────────────────────────────┘

Step 1: REQUEST DOWNLOAD URL
Client → API Server
   GET /api/files/file_abc123/download

API Server:
   • Check user permissions
   • Generate presigned S3 URL (expires in 1 hour)
   • Log download event
   Time: 20ms

Response:
{
  download_url: "https://s3.amazonaws.com/bucket/file_abc123?
                 X-Amz-Signature=xxx&X-Amz-Expires=3600",
  filename: "video.mp4",
  size: 500000000,
  expires_at: "2025-01-23T11:00:00Z"
}

Step 2: DIRECT DOWNLOAD FROM S3
Client → S3 directly
   GET download_url
   
   S3 returns file
   Bandwidth: Only client ↔ S3
   Time: Based on network

Benefits:
• API server: 20ms involvement
• S3 handles all bandwidth
• Scales infinitely
• No server bottleneck
```

### Strategy 2: CDN for Faster Downloads

```
┌──────────────────────────────────────────────────────────────────┐
│              CDN DISTRIBUTION FOR DOWNLOADS                       │
└──────────────────────────────────────────────────────────────────┘

Architecture:

User in Tokyo requests video
   ↓
┌────────────────────────┐
│   CloudFront Edge      │
│   (Tokyo location)     │
│   Check cache          │
└────────┬───────────────┘
         │
         ├─> Cache HIT (95% of requests)
         │   └─> Serve from Tokyo edge (20ms)
         │
         └─> Cache MISS (5% of requests)
             └─> Fetch from S3 (US-East)
                 └─> Cache in Tokyo
                 └─> Serve to user (200ms first time)

Latency Comparison:

Direct from S3 (US):
• Tokyo user → US S3: 200ms latency
• 1GB file: 2-5 minutes

Via CDN (edge cache):
• Tokyo user → Tokyo edge: 20ms latency
• 1GB file: 30-60 seconds
• 3-5x faster!

CDN Configuration:

Origin: S3 bucket
Edge locations: 300+ globally
Cache TTL: 24-72 hours (for immutable files)
Cache key: File ID or URL

Use Cases:
• Public content (videos, images)
• Software downloads
• Static assets
• Popular files
```

### Strategy 3: Range Requests (Partial Downloads)

```
┌──────────────────────────────────────────────────────────────────┐
│              HTTP RANGE REQUEST PATTERN                           │
└──────────────────────────────────────────────────────────────────┘

What: Download specific byte ranges of file

Use Case: Video seeking, large file downloads

Request:
GET /video.mp4
Range: bytes=1000000-2000000

Response:
Status: 206 Partial Content
Content-Range: bytes 1000000-2000000/500000000
Content-Length: 1000000
[1MB of video data]

Video Player Benefits:

Scenario: User wants to skip to 5 minutes in video
   
Without range requests:
• Must download entire 500MB video first
• Then seek to 5 minutes
• Wastes bandwidth, time

With range requests:
• Calculate byte offset for 5 minutes
• Request: Range: bytes=50000000-51000000
• Download 1MB chunk
• Play immediately
• Download more as needed

Benefits:
• Instant playback (adaptive streaming)
• Bandwidth efficient (only download viewed)
• Seeking support
• Resume capability

Implementation:
• S3 supports range requests natively
• CloudFront passes through ranges
• Client library handles automatically
```

---

## Real-World Architecture Examples

### Example 1: YouTube - Video Upload Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│              YOUTUBE VIDEO UPLOAD ARCHITECTURE                    │
└──────────────────────────────────────────────────────────────────┘

Scale: 500+ hours of video/minute, Files up to 256GB

Challenge: Fast, reliable upload for billions of users

Architecture:

User uploads video
   ↓
┌────────────────────────┐
│   Upload Service       │
│   • Request upload URL │
│   • Validate user      │
│   • Check quota        │
│   Time: 100ms          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Generate Presigned   │
│   • GCS upload URL     │
│   • Multipart enabled  │
│   • 24h expiration     │
└────────┬───────────────┘
         │
         ▼ Return upload URL
         │
Client uploads DIRECTLY to GCS
   ↓
┌──────────────────────────────────────────────┐
│       MULTIPART PARALLEL UPLOAD               │
│                                              │
│  File: 1GB video                             │
│  Chunks: 200 × 5MB                           │
│  Parallel: 20 chunks at once                 │
│  Time: 2-5 minutes (vs 20 min sequential)    │
│                                              │
│  Progress tracking:                          │
│  • Chunk 1: Uploaded ✓                       │
│  • Chunk 2: Uploading... 45%                 │
│  • Chunk 3: Queued                           │
│  • ...                                       │
│  Overall: 67% complete                       │
└──────────────────────────────────────────────┘
         │
         ▼ Upload complete
┌────────────────────────┐
│   Webhook Notification │
│   • GCS → API Server   │
│   • Upload successful  │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Update Metadata      │
│   • Bigtable record    │
│   • Status: uploaded   │
│   • Trigger processing │
└────────────────────────┘

Upload Optimizations:

1. SMART CHUNKING
   • Adaptive chunk size based on bandwidth
   • Fast connection: 50MB chunks
   • Slow connection: 5MB chunks
   • Maximizes throughput

2. PARALLEL UPLOADS
   • 10-20 chunks simultaneously
   • Saturates available bandwidth
   • 10-20x faster than sequential

3. RESUME ON FAILURE
   • Track uploaded chunks
   • Only retry failed chunks
   • No duplicate work

4. UPLOAD DEDUPLICATION
   • Hash file before upload
   • Check if already exists
   • Return existing if match
   • Saves bandwidth

Performance:
• Small video (100MB): 30-60 seconds
• Large video (1GB): 2-5 minutes
• Huge video (10GB): 20-40 minutes
• Resume success rate: 99.5%
```

---

### Example 2: Dropbox - File Upload/Download

```
┌──────────────────────────────────────────────────────────────────┐
│              DROPBOX FILE TRANSFER ARCHITECTURE                   │
└──────────────────────────────────────────────────────────────────┘

Scale: 700M users, Exabytes of data

Challenge: Sync files reliably across devices

Upload Architecture:

User uploads file
   ↓
┌────────────────────────┐
│   Dropbox Client       │
│   • Chunk file (4MB)   │
│   • Calculate SHA256   │
│   • Check for dedup    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   API: Request Upload  │
│   • Check existing     │
│   • Return upload URLs │
│   Time: 200ms          │
└────────┬───────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│         CHUNKED UPLOAD (Parallel)             │
│                                              │
│  Chunk 1 (4MB) ────┐                         │
│  Chunk 2 (4MB) ────┤                         │
│  Chunk 3 (4MB) ────┼──→ S3 (parallel)        │
│  ...               │    Via presigned URLs    │
│  Chunk N (4MB) ────┘                         │
│                                              │
│  10 chunks at once                           │
│  Adaptive based on bandwidth                 │
└──────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────┐
│   Dropbox Block Server │
│   • Verify all chunks  │
│   • Assemble file      │
│   • Dedup storage      │
│   • Notify devices     │
└────────────────────────┘

Deduplication Magic:

Before upload, client calculates hash:
• File already exists? 
  └─> No upload needed! (instant sync)
• File partially exists?
  └─> Only upload new chunks
• File completely new?
  └─> Upload all chunks

Result: 90%+ of "uploads" are instant (deduplicated)

Download Architecture:

User downloads file
   ↓
┌────────────────────────┐
│   Request Download     │
│   • Get chunk list     │
│   • Generate URLs      │
└────────┬───────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│         CHUNKED DOWNLOAD (Parallel)           │
│                                              │
│  Download 10 chunks simultaneously            │
│  Assemble locally                            │
│  Verify checksums                            │
│  Time: 2-10x faster than sequential          │
└──────────────────────────────────────────────┘

Optimization Techniques:

1. DELTA SYNC
   Only transfer changed bytes
   Binary diff algorithm
   Critical for large files with small edits

2. COMPRESSION
   Compress before upload
   Decompress after download
   50-70% bandwidth savings

3. EDGE CACHING
   Popular files cached at edge
   Faster downloads for shared folders

Performance:
• Small file (1MB): <1 second
• Medium file (100MB): 10-30 seconds
• Large file (1GB): 1-3 minutes
• Dedup rate: 90%+ (most uploads instant)
```

---

### Example 3: Instagram - Photo/Video Upload

```
┌──────────────────────────────────────────────────────────────────┐
│              INSTAGRAM MEDIA UPLOAD ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 100M+ photos/day, 10M+ videos/day

Challenge: Fast upload for mobile users

Mobile Upload Flow:

Step 1: PREPARE
   • Compress image (client-side)
   • Reduce resolution if needed
   • Original: 12MB → Compressed: 2MB
   Time: 1-2 seconds

Step 2: REQUEST UPLOAD URL
   Client → API
   POST /api/media/upload-request
   Response: {upload_url, media_id}
   Time: 100ms

Step 3: DIRECT UPLOAD
   Client → S3 presigned URL
   • Upload compressed version
   • Show upload progress bar
   Time: 2-10 seconds (2MB on mobile)

Step 4: CONFIRMATION
   Client → API
   POST /api/media/uploaded
   Body: {media_id}
   
   API triggers:
   • Thumbnail generation
   • Filter application
   • ML processing
   • Feed distribution
   Time: 50ms response, processing async

Video Upload Strategy:

Videos > 100MB:
• Multipart upload
• Split into 10MB chunks
• Upload 5 chunks in parallel
• Resume on network failure

Mobile Optimization:
• Compress on device
• Adaptive quality based on network
• WiFi: High quality
• Cellular: Lower quality
• Background upload (continue if app closed)

Progress Tracking:

Upload progress via local storage:
{
  media_id: "img_abc",
  total_bytes: 2000000,
  uploaded_bytes: 1500000,
  progress: 75,
  chunks_complete: [1, 2, 3],
  chunks_pending: [4]
}

Can resume even after app restart

Performance:
• Photo upload: 3-10 seconds
• Video upload: 10-60 seconds
• Success rate: 98% (with retries)
• Background upload: Continues in background
```

---

### Example 4: Netflix - Content Delivery

```
┌──────────────────────────────────────────────────────────────────┐
│              NETFLIX VIDEO DELIVERY ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 230M subscribers, 15% of internet traffic

Challenge: Stream high-quality video globally

Architecture:

User plays video
   ↓
┌────────────────────────┐
│   Netflix App          │
│   • Request manifest   │
│   • Get CDN URLs       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   API Server           │
│   • Check subscription │
│   • Select bitrates    │
│   • Return CDN URLs    │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼ Returns: Manifest with URLs for each chunk
         │
┌────────────────────────────────────────────────┐
│       ADAPTIVE STREAMING DOWNLOAD              │
│                                               │
│  Video chunked into 10-second segments        │
│  Multiple quality levels:                     │
│  • 4K: 25 Mbps                               │
│  • 1080p: 8 Mbps                             │
│  • 720p: 5 Mbps                              │
│  • 480p: 2.5 Mbps                            │
│  • 240p: 0.8 Mbps                            │
│                                               │
│  Client adapts based on bandwidth:            │
│  • Fast connection: Download 4K               │
│  • Slow connection: Download 480p             │
│  • Seamless switching mid-stream              │
└────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────┐
│   Open Connect CDN     │
│   • 300+ locations     │
│   • ISP-embedded       │
│   • 95%+ cache hit     │
│   Time: 10-50ms        │
└────────────────────────┘

Optimization Strategies:

1. PREDICTIVE CACHING
   • Pre-cache popular content
   • New releases: Global distribution before launch
   • Regional preferences: Cache locally popular

2. ADAPTIVE BITRATE STREAMING (ABR)
   • Client measures bandwidth every few seconds
   • Switches quality seamlessly
   • Prevents buffering

3. PARALLEL CHUNK DOWNLOADS
   • Download next 3 chunks in advance
   • Always buffered ahead
   • Smooth playback

4. RANGE REQUESTS
   • Seek to any point instantly
   • Only download needed segments
   • Saves bandwidth

Download Pattern:

Initial load:
• Download first chunk (10 sec of video)
• Start playback immediately
• Time to first frame: <1 second

Ongoing:
• Download ahead (30-60 seconds buffered)
• Adapt quality based on speed
• Seamless experience

Performance:
• Time to first frame: <1 second
• Buffering events: <0.1% (rare)
• Quality switches: Seamless
• Bandwidth usage: Optimized (only what's watched)
```

---

### Example 5: Google Drive - File Storage

```
┌──────────────────────────────────────────────────────────────────┐
│              GOOGLE DRIVE FILE ARCHITECTURE                       │
└──────────────────────────────────────────────────────────────────┘

Scale: 1B+ users, Exabytes of storage

Challenge: Store any file type, any size, accessible everywhere

Upload Architecture:

User uploads file
   ↓
┌────────────────────────┐
│   Drive Client         │
│   • Chunk file (256KB) │
│   • Calculate hash     │
│   • Compress chunks    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Upload API           │
│   • Dedup check        │
│   • Request upload URLs│
│   • Create metadata    │
└────────┬───────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│         CHUNKED PARALLEL UPLOAD               │
│                                              │
│  256KB chunks uploaded in parallel            │
│  • 20-50 chunks at once                      │
│  • Resumable (track uploaded chunks)         │
│  • Content-addressed (hash-based)            │
│  • Deduplicated across users                 │
└────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────┐
│   Google Cloud Storage │
│   • Globally replicated│
│   • 11 nines durability│
│   • Encrypted          │
└────────────────────────┘

Deduplication Strategy:

Content-Addressed Storage:
• Chunk hash = storage key
• Same content = same key
• Stored once, referenced multiple times

Example:
• User A uploads cat.jpg
• Chunks stored: [hash1, hash2, hash3]

• User B uploads same cat.jpg
• Chunks already exist!
• No upload needed
• Just create metadata reference

Result: 90%+ dedup rate for some content types

Download Architecture:

User downloads file
   ↓
Request: GET /files/file_id
   ↓
API returns: Chunk list + download URLs
   ↓
┌──────────────────────────────────────────────┐
│         PARALLEL CHUNK DOWNLOAD               │
│                                              │
│  Download 20 chunks simultaneously            │
│  Assemble in order                           │
│  Verify checksums                            │
│  Time: Much faster than sequential           │
└──────────────────────────────────────────────┘

Mobile Optimization:

Selective Sync:
• Don't download all files
• Download on-demand
• Cache recently used
• Saves mobile storage

Thumbnail Preview:
• Download tiny version first (10KB)
• Show preview immediately
• Full file on request

Performance:
• Upload (1GB): 2-5 minutes
• Download (1GB): 1-3 minutes
• Dedup: 90% uploads instant
• Sync: Cross-device in <10 seconds
```

---

### Example 6: WhatsApp - Media Messaging

```
┌──────────────────────────────────────────────────────────────────┐
│              WHATSAPP MEDIA MESSAGE ARCHITECTURE                  │
└──────────────────────────────────────────────────────────────────┘

Scale: 2B users, 100B messages/day, 10B+ media files/day

Challenge: Fast media sharing over mobile networks

Architecture:

User sends photo
   ↓
┌────────────────────────┐
│   WhatsApp Client      │
│   • Compress image     │
│   • Resize (max 1600px)│
│   • JPEG quality: 75%  │
│   • Result: ~300KB     │
│   Time: 1 second       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Upload to WhatsApp   │
│   Servers (S3-backed)  │
│   • Direct upload      │
│   • Encrypted E2E      │
│   Time: 2-5 seconds    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Generate Download URL│
│   • Short-lived URL    │
│   • Expires in 30 days │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Send Message         │
│   • Message + media URL│
│   • Recipient downloads│
│   Time: 100ms          │
└────────────────────────┘

Media Handling Strategy:

PHOTOS:
• Compress aggressively (mobile bandwidth)
• Original: 12MB → WhatsApp: 300KB
• Quality loss acceptable for messaging
• Very fast upload

VIDEOS:
• Compress (H.264)
• Limit: 16MB on mobile
• Original quality for "documents"
• Longer upload (10-30 seconds)

DOCUMENTS:
• No compression
• Original file preserved
• Direct upload to S3
• Slower but accurate

Download on Demand:
• Media not auto-downloaded
• Shows thumbnail (5KB)
• Download full on tap
• Saves mobile data

Performance:
• Photo send: 3-6 seconds total
• Video send: 15-45 seconds
• Download: 2-10 seconds
• Storage: 30-day retention
```

---

## Optimization Techniques

```
┌──────────────────────────────────────────────────────────────────┐
│              BLOB OPTIMIZATION STRATEGIES                         │
└──────────────────────────────────────────────────────────────────┘

1. DEDUPLICATION
   Content-addressable storage
   • Hash content → storage key
   • Same file → same hash → single copy
   • Massive storage savings
   
   Use: Dropbox, Google Drive, backup systems

2. COMPRESSION
   Compress before upload
   • Images: 50-80% smaller
   • Videos: Already compressed (skip)
   • Documents: 60-90% smaller
   
   Use: Any non-compressed format

3. CLIENT-SIDE PROCESSING
   Offload work to client
   • Resize images
   • Compress videos
   • Calculate checksums
   
   Benefits: Reduces server load, faster uploads

4. PARALLEL TRANSFERS
   Multiple chunks simultaneously
   • 10-20x faster
   • Saturates bandwidth
   
   Use: Files > 100MB

5. PROGRESSIVE LOADING
   Show preview while downloading
   • Thumbnail first (fast)
   • Low-res version
   • High-res on demand
   
   Use: Images, videos

6. LAZY LOADING
   Don't download until needed
   • Scroll-based loading
   • Viewport-aware
   
   Use: Image galleries, feeds

7. ADAPTIVE QUALITY
   Serve different quality based on:
   • Device (mobile vs desktop)
   • Network (WiFi vs cellular)
   • User preference
   
   Use: Streaming, image services

8. EDGE CACHING
   Cache at CDN edge locations
   • 95%+ hit rate
   • 10-50ms latency
   
   Use: Public/popular content

9. SMART RETRY
   Exponential backoff
   • Transient failures: Retry
   • Permanent failures: Fail fast
   
   Use: Always

10. BANDWIDTH THROTTLING
    Limit upload/download speed
    • Prevent network saturation
    • Fair usage
    
    Use: Background sync, large files
```

---

## Decision Framework

```
┌──────────────────────────────────────────────────────────────────┐
│              BLOB HANDLING DECISION TREE                          │
└──────────────────────────────────────────────────────────────────┘

START: Need to handle files?
   │
   ▼
What's the file size?
   │
   ├─> < 1MB
   │   └─> Store in database (acceptable)
   │       • Profile pictures
   │       • Small documents
   │
   ├─> 1-10MB
   │   └─> Use S3 with presigned URLs
   │       • Simple, fast
   │       • No special handling needed
   │
   ├─> 10-100MB
   │   └─> Use S3 with presigned URLs
   │       • Consider multipart for >50MB
   │       • Progress tracking
   │       • Resume capability
   │
   └─> > 100MB
       └─> MUST use:
           • Multipart upload (parallel)
           • Resumable protocol (mobile)
           • Chunked download
           • Progress tracking
           • CDN for delivery

Is content public or private?
   │
   ├─> Public
   │   └─> Use CDN heavily
   │       • CloudFront, Akamai
   │       • 95%+ cache hit rate
   │       • Serve from edge
   │
   └─> Private
       └─> Use presigned URLs
           • Time-limited access
           • User-specific permissions
           • Direct from S3

Is it for streaming (video/audio)?
   │
   └─> YES
       • Adaptive bitrate streaming
       • Multiple quality levels
       • Chunked encoding (10-sec segments)
       • Range request support
       • CDN distribution

Is network reliability a concern?
   │
   └─> YES (Mobile)
       • Resumable uploads
       • Small chunks (1-5MB)
       • Background transfers
       • Retry logic
```

---

## Interview Guide

```
┌──────────────────────────────────────────────────────────────────┐
│              INTERVIEW FRAMEWORK                                  │
└──────────────────────────────────────────────────────────────────┘

Question: "Design a file upload system for large files"

STEP 1: CLARIFY (3 min)
• File size range?
• Public or private?
• Upload frequency?
• Network reliability (mobile/desktop)?
• Need to stream (video)?

STEP 2: HIGH-LEVEL DESIGN (10 min)

Components:
Client → API Server (metadata only)
      → S3/Blob Storage (file data)
      → CDN (for downloads)

Explain presigned URLs:
"Client gets temporary URL from API,
 uploads directly to S3, bypassing our servers"

STEP 3: DEEP DIVE - Large Files (15 min)

For files > 100MB:
• Multipart upload
• Split into 5-10MB chunks
• Upload 10-20 chunks in parallel
• S3 assembles on completion

Benefits:
• 10-20x faster than sequential
• Resumable (retry failed parts)
• Progress tracking

STEP 4: DOWNLOAD OPTIMIZATION (7 min)

For public content:
• Use CDN (CloudFront)
• Cache at edge (300+ locations)
• 95% cache hit rate
• 10-50ms latency

For videos:
• Adaptive streaming
• Multiple bitrates
• Range requests for seeking

STEP 5: FAILURE HANDLING (5 min)

Upload failures:
• Retry with exponential backoff
• Resume from last uploaded chunk
• Deduplication prevents waste

Network interruptions:
• Resumable upload protocol
• Client tracks progress locally
• Resume when reconnected
```

### Common Interview Questions

```
Q: "Why not store files in the database?"

A: Multiple reasons:
• Performance: BLOB columns slow down queries
• Scalability: Databases don't scale for files
• Cost: DB storage 5x more expensive than S3
• Backups: Much slower with large files
• Replication: Unnecessary load

Rule: Files >10MB always go to blob storage

Q: "How do you handle a 10GB file upload?"

A: Multipart upload strategy:
• Split into 5MB chunks (2000 chunks)
• Upload 20 chunks in parallel
• Time: ~15 minutes vs 3+ hours sequential
• Resumable if interrupted
• Progress tracking per chunk

Q: "How do you ensure fast downloads globally?"

A: CDN strategy:
• Distribute via CloudFront/Akamai
• 300+ edge locations
• User downloads from nearest edge
• 95%+ cache hit rate
• Tokyo user: 20ms vs 200ms latency

Q: "What if someone uploads inappropriate content?"

A: Multi-layer approach:
• Client-side: Hash-based blocking (known bad files)
• Server-side: ML scan (async after upload)
• Takedown: Remove from storage + CDN
• Prevention: Hash blacklist

Q: "How do you prevent abuse (unlimited uploads)?"

A: Rate limiting + quotas:
• Per-user quota (5GB free, 100GB paid)
• Upload rate limit (10 files/hour)
• File size limits (5GB per file)
• Bandwidth limits (prevent DoS)
• Cost monitoring (alert on anomalies)
```

---

## Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                  BEST PRACTICES CHECKLIST                         │
└──────────────────────────────────────────────────────────────────┘

ALWAYS DO:
✓ Use blob storage (S3, GCS, Azure) for files >10MB
✓ Use presigned URLs for direct client↔storage
✓ Implement multipart upload for files >100MB
✓ Add resume capability for large files
✓ Track upload progress
✓ Use CDN for downloads
✓ Support range requests for videos

UPLOAD OPTIMIZATION:
✓ Compress before upload (if not already compressed)
✓ Deduplication check (hash-based)
✓ Parallel chunk uploads
✓ Client-side processing
✓ Background uploads (mobile)

DOWNLOAD OPTIMIZATION:
✓ CDN with high cache hit rate (>90%)
✓ Adaptive streaming for videos
✓ Lazy loading for images
✓ Parallel chunk downloads
✓ Progressive loading

SECURITY:
✓ Presigned URLs with expiration
✓ Permission checks before generating URLs
✓ Content scanning (virus, inappropriate)
✓ Rate limiting and quotas
✓ Encryption at rest and in transit

MOBILE CONSIDERATIONS:
✓ Adaptive quality based on network
✓ Background uploads
✓ Resume capability
✓ Bandwidth awareness (WiFi vs cellular)
✓ Battery optimization
```

---

**Document Version**: 1.0  
**Created**: January 2025  
**Type**: High-Level Design Guide  
**Examples**: 6 real-world architectures (YouTube, Dropbox, Instagram, Netflix, Google Drive, WhatsApp)  
**Focus**: Blob storage, uploads, downloads, CDN distribution
