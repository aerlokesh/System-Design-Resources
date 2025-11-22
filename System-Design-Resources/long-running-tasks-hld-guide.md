# Managing Long-Running Tasks - Complete HLD Guide

## Table of Contents
1. [Introduction](#introduction)
2. [The Problem](#the-problem)
3. [The Solution Architecture](#the-solution-architecture)
4. [Core Components](#core-components)
5. [Real-World Architecture Examples](#real-world-architecture-examples)
6. [Design Patterns](#design-patterns)
7. [Failure Handling](#failure-handling)
8. [Decision Framework](#decision-framework)
9. [Interview Guide](#interview-guide)

---

## Introduction

**Long-Running Tasks** are operations that take seconds, minutes, or even hours to complete - far beyond typical API timeout limits (30-60 seconds). Examples include:

- **Video processing**: Transcoding, compression (5-30 minutes)
- **Image manipulation**: Thumbnail generation, filters (10-60 seconds)
- **Report generation**: PDF creation, data aggregation (30-300 seconds)
- **Batch operations**: Email campaigns, data exports (minutes to hours)
- **ML inference**: Model training, predictions (variable)
- **ETL jobs**: Data transformation, migration (hours)

**The Core Pattern**: Accept request immediately, process asynchronously, provide status updates.

---

## The Problem

### Synchronous Processing Fails

```
┌──────────────────────────────────────────────────────────────────┐
│              SYNCHRONOUS LONG-RUNNING TASK (BAD)                  │
└──────────────────────────────────────────────────────────────────┘

User uploads video
   ↓
API Server starts processing
   ↓
Transcoding (10 minutes)
   ↓
User waits... and waits...
   ↓
Timeout after 60 seconds
   ↓
Request fails!
   ↓
User frustrated, retries
   ↓
Server does duplicate work

Problems:
├─ Poor user experience (long wait)
├─ Timeout errors (most load balancers timeout at 30-60s)
├─ Resource waste (API server blocked)
├─ No progress visibility
├─ No retry mechanism
└─ Duplicate work on retry
```

### Real-World Examples of the Problem

```
Video Upload (YouTube):
• User uploads 1GB video
• Transcoding takes 15 minutes
• Browser timeout: 60 seconds
• Result: Upload fails, user retries, wasted work

PDF Report (Analytics Dashboard):
• User requests annual report
• Data aggregation: 2 minutes
• PDF generation: 3 minutes
• Total: 5 minutes
• Result: Request timeout, no report

Batch Email (Marketing Platform):
• Send to 100K subscribers
• Email sending: 30 minutes
• HTTP timeout: 60 seconds
• Result: Unknown status, may send duplicates
```

---

## The Solution Architecture

### Async Task Processing Pattern

```
┌──────────────────────────────────────────────────────────────────┐
│          ASYNCHRONOUS TASK PROCESSING (GOOD)                      │
└──────────────────────────────────────────────────────────────────┘

Phase 1: SUBMISSION (Fast)
User submits task
   ↓
API Server validates request
   ↓
Create job record in database
   ↓
Enqueue job in message queue
   ↓
Return job_id immediately
Time: <100ms

User gets: {
  "job_id": "job_abc123",
  "status": "queued",
  "check_status_url": "/api/jobs/job_abc123"
}

Phase 2: PROCESSING (Async)
Worker picks up job from queue
   ↓
Update status: "processing"
   ↓
Execute long-running task
   ↓
Update status: "completed" or "failed"
   ↓
Store result
Time: Seconds to hours

Phase 3: STATUS CHECKING (User-initiated)
User polls: GET /api/jobs/job_abc123
   ↓
Returns: {
  "job_id": "job_abc123",
  "status": "completed",
  "progress": 100,
  "result_url": "/api/results/abc123"
}

Benefits:
✓ Fast API response (<100ms)
✓ No timeouts
✓ Clear progress tracking
✓ Retry-safe (idempotent with job_id)
✓ Resource isolation
✓ Horizontal scaling
```

---

## Core Components

### Component 1: Job Queue

```
┌──────────────────────────────────────────────────────────────────┐
│                    MESSAGE QUEUE OPTIONS                          │
└──────────────────────────────────────────────────────────────────┘

Technology Comparison:

REDIS (Simple)
├─ Use Case: Simple job queues
├─ Throughput: 100K jobs/sec
├─ Persistence: Optional (AOF)
├─ Ordering: FIFO with LPUSH/RPOP
└─ Best for: Getting started, small scale

RABBITMQ (Reliable)
├─ Use Case: Complex routing, guaranteed delivery
├─ Throughput: 50K jobs/sec
├─ Persistence: Yes (durable queues)
├─ Ordering: FIFO per queue
└─ Best for: Enterprise, reliability critical

KAFKA (High-throughput)
├─ Use Case: Stream processing, event sourcing
├─ Throughput: 1M+ messages/sec
├─ Persistence: Yes (configurable retention)
├─ Ordering: Per partition
└─ Best for: Large scale, event-driven

AWS SQS (Managed)
├─ Use Case: AWS ecosystem, no ops
├─ Throughput: Unlimited (managed)
├─ Persistence: Yes
├─ Ordering: Standard (best effort) or FIFO
└─ Best for: Quick setup, AWS-based

Architecture Pattern:
┌──────────┐     ┌──────────┐     ┌──────────┐
│   API    │────▶│  Queue   │────▶│  Worker  │
│  Server  │     │          │     │  Process │
└──────────┘     └──────────┘     └──────────┘
   Enqueue         Buffer          Dequeue
   (Fast)                          (Process)
```

### Component 2: Worker Pool

```
┌──────────────────────────────────────────────────────────────────┐
│                    WORKER ARCHITECTURE                            │
└──────────────────────────────────────────────────────────────────┘

Worker Fleet:
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
│  Worker 1  │  │  Worker 2  │  │  Worker 3  │  │  Worker N  │
└──────┬─────┘  └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
       │               │               │               │
       └───────────────┴───────────────┴───────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │    Queue     │
                    └──────────────┘

Worker Lifecycle:

1. POLL QUEUE
   • Blocking or periodic polling
   • Claim job (atomic operation)
   • Set visibility timeout

2. UPDATE STATUS
   • Job status: "queued" → "processing"
   • Record worker ID
   • Update start time

3. EXECUTE TASK
   • Perform actual work
   • Report progress periodically
   • Handle errors gracefully

4. COMPLETE
   • Update status: "completed" or "failed"
   • Store result
   • Delete from queue

5. REPEAT
   • Go back to step 1

Scaling Strategy:
• Start with 10 workers
• Monitor queue depth
• Auto-scale based on backlog
• Max workers: Based on downstream capacity
```

### Component 3: Job State Management

```
┌──────────────────────────────────────────────────────────────────┐
│                    JOB STATE STORAGE                              │
└──────────────────────────────────────────────────────────────────┘

Job Record Schema:
{
  job_id: "job_abc123",
  user_id: "user_456",
  type: "video_transcode",
  status: "processing",  // queued, processing, completed, failed
  progress: 45,  // percentage
  created_at: "2025-01-23T10:00:00Z",
  started_at: "2025-01-23T10:00:15Z",
  completed_at: null,
  input: {
    video_url: "s3://bucket/input.mp4",
    resolution: "1080p"
  },
  output: null,
  error: null,
  retry_count: 0,
  worker_id: "worker_3"
}

Storage Options:

DATABASE (PostgreSQL, MySQL):
• Use for: Job metadata, audit trail
• Benefits: ACID, queryable, joins
• Cons: Higher latency for frequent updates

NOSQL (MongoDB, DynamoDB):
• Use for: High-throughput status updates
• Benefits: Fast writes, horizontal scaling
• Cons: Eventual consistency

KEY-VALUE (Redis):
• Use for: Temporary job state
• Benefits: Very fast, TTL support
• Cons: Not durable (use with persistence)

Hybrid Approach (Best):
• Redis: Fast status updates, TTL cleanup
• Database: Permanent record, audit trail
• Write to both asynchronously
```

---

## Real-World Architecture Examples

### Example 1: YouTube - Video Upload & Processing

```
┌──────────────────────────────────────────────────────────────────┐
│              YOUTUBE VIDEO PROCESSING ARCHITECTURE                │
└──────────────────────────────────────────────────────────────────┘

Scale: 500+ hours of video uploaded per minute

Challenge: Transcode videos to multiple formats/resolutions

Architecture:

User uploads video
   ↓
┌────────────────────────┐
│   Upload Service       │
│   • Validate format    │
│   • Check size limits  │
│   • Generate video_id  │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Google Cloud Storage │
│   • Store original     │
│   • Multi-part upload  │
│   • Resume support     │
│   Time: 1-10 minutes   │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Bigtable (Metadata)  │
│   • Video record       │
│   • Status: processing │
│   • Upload progress    │
│   Time: 20ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Pub/Sub (Queue)      │
│   • video_uploaded     │
│   • Async event        │
│   Time: 5ms            │
└────────┬───────────────┘
         │
         ▼ Return to user (total: 1-10 min for upload)
         │
         │ Background Processing Pipeline
         ▼
┌──────────────────────────────────────────────────────────┐
│                 TRANSCODING PIPELINE                      │
│                                                          │
│  Stage 1: Video Analysis (30 sec)                       │
│  ├─ Duration, resolution, codec                         │
│  ├─ Content analysis (SafeSearch)                       │
│  └─ Audio track detection                               │
│                                                          │
│  Stage 2: Parallel Transcoding (5-30 min)              │
│  ├─ 144p (mobile)                                       │
│  ├─ 360p (low quality)                                  │
│  ├─ 720p (HD)                                           │
│  ├─ 1080p (Full HD)                                     │
│  ├─ 1440p (2K)                                          │
│  ├─ 2160p (4K)                                          │
│  └─ Each runs on separate worker                        │
│                                                          │
│  Stage 3: Thumbnail Generation (10 sec)                 │
│  ├─ Extract keyframes                                   │
│  ├─ Generate 4-5 thumbnails                             │
│  └─ ML-based best thumbnail selection                   │
│                                                          │
│  Stage 4: Audio Processing (2 min)                      │
│  ├─ Audio normalization                                 │
│  ├─ Generate captions (speech-to-text)                  │
│  └─ Multiple language support                           │
│                                                          │
│  Stage 5: Finalization (30 sec)                         │
│  ├─ Update Bigtable: status = "completed"              │
│  ├─ Update search index                                 │
│  ├─ Notify user (email/push)                            │
│  └─ Cache metadata                                      │
└──────────────────────────────────────────────────────────┘

Worker Pool Management:

Video Transcoding Workers:
• 1000s of workers globally
• GPU-accelerated (NVIDIA)
• Kubernetes-managed
• Auto-scaling based on queue depth
• Each worker: 1-4 videos at a time

Priority Queues:
├─ HIGH: Paid users, live streams (process first)
├─ MEDIUM: Regular uploads
└─ LOW: Bulk uploads, older videos

Progress Tracking:

Status Updates (every 10 seconds):
{
  job_id: "video_abc123",
  status: "processing",
  progress: 45,  // percentage
  stages_complete: ["analysis", "720p", "1080p"],
  stages_pending: ["4K", "thumbnails", "captions"],
  eta_seconds: 420
}

Client Polling:
• Poll every 5 seconds while processing
• Exponential backoff if taking long
• Push notification when complete

Optimization Techniques:

1. PARALLEL TRANSCODING
   • All resolutions processed simultaneously
   • 6 workers for 6 resolutions
   • Reduces total time from 30min → 5min

2. ADAPTIVE ENCODING
   • Detect video complexity
   • Simple videos: Fast encoding
   • Complex videos: More resources
   • Variable worker allocation

3. SEGMENT-BASED PROCESSING
   • Split video into 10-second segments
   • Process segments in parallel
   • Stitch at the end
   • Enables faster preview generation

4. CACHE ENCODING PROFILES
   • Common settings pre-configured
   • Reduces setup time
   • Consistent quality

Performance:
• Upload acknowledgment: 1-10 minutes
• Processing time: 5-30 minutes
• Total time to publish: 6-40 minutes
• Throughput: 500+ hours/minute uploaded
• Success rate: 99.9%
```

---

### Example 2: Instagram - Photo/Video Processing

```
┌──────────────────────────────────────────────────────────────────┐
│            INSTAGRAM MEDIA PROCESSING ARCHITECTURE                │
└──────────────────────────────────────────────────────────────────┘

Scale: 100M+ photos/day, 10M+ videos/day

Challenge: Fast processing, multiple variants, instant publishing

Architecture:

User uploads photo/video
   ↓
┌────────────────────────┐
│   Upload API           │
│   • Validate           │
│   • Generate ID        │
│   • Check size         │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   S3 (Original)        │
│   • Store raw file     │
│   Time: 2-10 sec       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  Cassandra (Metadata)  │
│   • Media ID           │
│   • Status: processing │
│   Time: 20ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   SQS Queue            │
│   • photo_process      │
│   • video_process      │
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼ Return "Uploading..." (2-10 sec)
         │
         │ Processing Pipeline
         ▼
┌──────────────────────────────────────────────────┐
│               PHOTO PROCESSING                    │
│  (30-60 seconds total)                           │
│                                                  │
│  Worker 1: THUMBNAILS (10 sec)                  │
│  ├─ 150x150 (grid)                              │
│  ├─ 320x320 (feed preview)                      │
│  ├─ 640x640 (full view)                         │
│  └─ Upload to CDN                                │
│                                                  │
│  Worker 2: FILTERS (15 sec)                     │
│  ├─ Generate preview with filter                 │
│  ├─ User-selected filter                        │
│  └─ Upload filtered version                     │
│                                                  │
│  Worker 3: ML PROCESSING (20 sec)               │
│  ├─ Object/face detection                       │
│  ├─ NSFW check                                  │
│  ├─ Quality assessment                          │
│  └─ Auto-tagging suggestions                    │
│                                                  │
│  Worker 4: METADATA EXTRACTION (5 sec)          │
│  ├─ EXIF data                                   │
│  ├─ Location (if available)                     │
│  └─ Camera info                                 │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│               VIDEO PROCESSING                    │
│  (2-10 minutes total)                            │
│                                                  │
│  Worker 1: TRANSCODING (5 min)                  │
│  ├─ Multiple bitrates                            │
│  ├─ H.264/H.265 codec                           │
│  └─ Adaptive streaming (HLS)                    │
│                                                  │
│  Worker 2: THUMBNAIL (30 sec)                   │
│  ├─ Extract frame at 3 seconds                  │
│  ├─ ML-based best frame selection               │
│  └─ Generate preview GIF                        │
│                                                  │
│  Worker 3: CAPTIONS (1 min)                     │
│  ├─ Speech-to-text if audio                     │
│  └─ Generate subtitles                          │
└──────────────────────────────────────────────────┘

Progress Updates:

Real-time progress via WebSocket:
{
  media_id: "img_abc123",
  status: "processing",
  stages: {
    upload: "completed",
    thumbnails: "completed",
    filters: "in_progress",
    ml_analysis: "pending"
  },
  progress: 60,
  eta_seconds: 20
}

Optimization:

1. PRIORITY LANES
   Stories (24h lifetime):
   • High priority queue
   • Process within 10 seconds
   • Acceptable lower quality

   Feed Posts (permanent):
   • Normal priority
   • Full quality processing
   • 30-60 seconds acceptable

2. PARALLEL WORKERS
   • All stages run concurrently
   • Reduces total time by 70%
   • Requires more workers

3. SMART ROUTING
   • Photos: CPU workers
   • Videos: GPU workers
   • Right resource for job type

Performance:
• Photo processing: 30-60 seconds
• Video processing: 2-10 minutes
• Throughput: 1K+ media/sec
• Worker fleet: 10,000+ instances
```

---

### Example 3: Netflix - Content Encoding Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│              NETFLIX ENCODING ARCHITECTURE                        │
└──────────────────────────────────────────────────────────────────┘

Scale: 1000s of titles, Multiple formats per title

Challenge: Encode for all devices, resolutions, network speeds

Architecture:

Content uploaded (from studio)
   ↓
┌────────────────────────┐
│   Encoding Service     │
│   • Validate source    │
│   • Create job spec    │
│   • Generate tasks     │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Job Database         │
│   • Encoding job       │
│   • Multiple tasks     │
│   • Dependencies       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Task Queue           │
│   • 100+ encoding tasks│
│   • per video          │
└────────┬───────────────┘
         │
         ▼ Encoding job created
         │
         │ Distributed Processing (Hours to Days)
         ▼
┌──────────────────────────────────────────────────────────┐
│              ENCODING TASK BREAKDOWN                      │
│                                                          │
│  For each video:                                         │
│                                                          │
│  1. VIDEO ENCODING (Parallel)                           │
│     ├─ 4K HDR (x265)                                    │
│     ├─ 4K SDR (x265)                                    │
│     ├─ 1080p                                            │
│     ├─ 720p                                             │
│     ├─ 480p                                             │
│     ├─ 360p                                             │
│     └─ 240p                                             │
│     Each with 3-5 bitrate variants                      │
│     Total: 30+ encoded versions                         │
│                                                          │
│  2. AUDIO ENCODING (Parallel)                           │
│     ├─ Multiple languages                                │
│     ├─ 5.1 Dolby Atmos                                  │
│     ├─ Stereo                                           │
│     └─ Audio descriptions                               │
│                                                          │
│  3. SUBTITLE GENERATION                                  │
│     ├─ 20+ languages                                    │
│     ├─ SDH (deaf/hard of hearing)                       │
│     └─ Forced subtitles                                 │
│                                                          │
│  4. THUMBNAIL GENERATION                                 │
│     ├─ Extract 100 frames                               │
│     ├─ ML-based scene detection                         │
│     ├─ Select best 10 thumbnails                        │
│     └─ A/B test variants                                │
│                                                          │
│  5. QUALITY CHECK                                        │
│     ├─ Automated QC                                     │
│     ├─ Audio sync check                                 │
│     ├─ Artifact detection                               │
│     └─ Manual review if issues                          │
└──────────────────────────────────────────────────────────┘

Worker Architecture:

┌────────────────────────────────────┐
│        ENCODING WORKERS            │
│                                    │
│  GPU Workers (Encoding):           │
│  ├─ 1000s of instances             │
│  ├─ NVIDIA GPUs                    │
│  ├─ EC2 P3 instances               │
│  └─ 4-8 concurrent encodes         │
│                                    │
│  CPU Workers (Analysis):           │
│  ├─ Quality check                  │
│  ├─ Metadata extraction            │
│  └─ Thumbnail generation           │
│                                    │
│  Specialized Workers:              │
│  ├─ Audio processing               │
│  ├─ Subtitle generation            │
│  └─ Content analysis               │
└────────────────────────────────────┘

Task Orchestration:

┌─────────────────────────────────────┐
│  Encoding Job (Directed Acyclic     │
│  Graph - DAG)                       │
│                                     │
│           [Source Video]            │
│                 │                   │
│       ┌─────────┼─────────┐        │
│       ▼         ▼         ▼        │
│    [1080p]  [720p]  [480p]         │
│       │         │         │        │
│       └─────────┼─────────┘        │
│                 ▼                   │
│           [Thumbnails]              │
│                 ▼                   │
│            [QC Check]               │
│                 ▼                   │
│        [Publish to CDN]             │
└─────────────────────────────────────┘

Progress Tracking:

Job status accessible via API:
GET /api/encoding/job_123

Response:
{
  job_id: "job_123",
  title: "Movie Title",
  status: "processing",
  progress: 65,
  tasks_completed: 22,
  tasks_total: 34,
  estimated_completion: "2025-01-23T14:30:00Z",
  stages: {
    video_encoding: "80% complete",
    audio_encoding: "100% complete",
    thumbnails: "100% complete",
    qc_check: "pending"
  }
}

Optimization Strategies:

1. PARALLEL EVERYTHING
   • All resolutions encode simultaneously
   • Reduces time from hours to minutes
   • Requires massive worker fleet

2. SEGMENT-BASED ENCODING
   • Split video into 10-second chunks
   • Encode chunks independently
   • Allows partial playback (preview)
   • Enables resume on failure

3. PERCEPTUAL ENCODING
   • Analyze video complexity
   • Adjust bitrate dynamically
   • Save 20-50% bandwidth
   • Maintain quality

4. SMART RETRY
   • Failed tasks auto-retry (3 attempts)
   • Different worker on retry
   • Exponential backoff
   • Alert if repeated failures

Performance:
• Job submission: <100ms
• Short video (<5 min): 10-20 minutes processing
• Feature film (2 hours): 4-8 hours processing
• Worker utilization: 85%
• Encoding cost: Largest expense (30% of infrastructure)
```

---

### Example 4: Dropbox - File Synchronization

```
┌──────────────────────────────────────────────────────────────────┐
│              DROPBOX SYNC ENGINE ARCHITECTURE                     │
└──────────────────────────────────────────────────────────────────┘

Scale: 700M+ users, Exabytes of data, Millions of syncs/second

Challenge: Sync files across devices reliably

Architecture:

User uploads file
   ↓
┌────────────────────────┐
│   Upload Service       │
│   • Chunk file (4MB)   │
│   • Calculate hashes   │
│   • Dedup check        │
│   Time: Variable       │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Block Storage        │
│   • Store unique chunks│
│   • Content-addressed  │
│   Time: 1-60 seconds   │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Metadata Store       │
│   • File metadata      │
│   • Status: syncing    │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Sync Queue           │
│   • Notify devices     │
│   • Async propagation  │
└────────┬───────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│         SYNC WORKER FLEET                         │
│                                                  │
│  Stage 1: DELTA DETECTION (100ms)               │
│  ├─ Compare file versions                       │
│  ├─ Identify changed chunks                     │
│  └─ Skip unchanged data (99% of data)           │
│                                                  │
│  Stage 2: CONFLICT RESOLUTION (50ms)            │
│  ├─ Detect concurrent edits                     │
│  ├─ Create conflict copies if needed            │
│  └─ Notify users                                │
│                                                  │
│  Stage 3: PROPAGATION (Variable)                │
│  ├─ Push to online devices (real-time)          │
│  ├─ Queue for offline devices                   │
│  └─ Batch notifications                         │
│                                                  │
│  Stage 4: VERIFICATION (100ms)                  │
│  ├─ Verify checksums                            │
│  ├─ Confirm all devices updated                 │
│  └─ Mark sync complete                          │
└──────────────────────────────────────────────────┘

Long-Running Sync Scenarios:

SMALL FILE (< 1MB):
• Upload: 1 second
• Processing: <1 second
• Sync to devices: 2-5 seconds
• Total: 5-7 seconds

LARGE FILE (1GB):
• Upload: 2-10 minutes (depends on bandwidth)
• Processing: 30 seconds (chunking, dedup)
• Sync to devices: 5-20 minutes (bandwidth dependent)
• Total: 7-30 minutes

FOLDER SYNC (10K files):
• Upload: 10-60 minutes
• Processing: 5 minutes (parallel)
• Sync: 15-90 minutes
• Total: 30 minutes to 3 hours

Optimization:

1. CHUNKING & DEDUPLICATION
   • Only upload changed chunks
   • Saves 90%+ bandwidth
   • Faster syncs

2. DELTA SYNC
   • Only sync differences
   • Binary diff algorithm
   • Critical for large files

3. PARALLEL UPLOADS
   • Multiple chunks simultaneously
   • 10x faster for large files
   • Adaptive based on bandwidth

4. OFFLINE QUEUEING
   • Queue syncs if device offline
   • Process when device reconnects
   • Prevents sync failures

Status API:

GET /api/sync/status/file_abc123

{
  file_id: "file_abc123",
  status: "syncing",
  uploaded_bytes: 450000000,
  total_bytes: 1000000000,
  progress: 45,
  devices_synced: 2,
  devices_pending: 3,
  eta_seconds: 300
}

Performance:
• Small file sync: 5-10 seconds
• Large file sync: Minutes to hours
• Sync success rate: 99.5%
• Conflict rate: 0.1%
```

---

### Example 5: Canva - Design Export & Rendering

```
┌──────────────────────────────────────────────────────────────────┐
│              CANVA EXPORT ARCHITECTURE                            │
└──────────────────────────────────────────────────────────────────┘

Scale: 100M+ users, Millions of exports/day

Challenge: Render complex designs to PDF/PNG/Video

Architecture:

User clicks "Download"
   ↓
┌────────────────────────┐
│   Export API           │
│   • Validate design    │
│   • Check permissions  │
│   • Create job         │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   SQS Queue            │
│   • export_jobs        │
│   • Priority-based     │
│   Time: 10ms           │
└────────┬───────────────┘
         │
         ▼ Return job_id (60ms)
         │
         │ Rendering Pipeline
         ▼
┌──────────────────────────────────────────────────┐
│           RENDERING WORKERS                       │
│                                                  │
│  PDF/Image Export (10-60 seconds):              │
│  ├─ Load design data from database              │
│  ├─ Render each page/element                    │
│  ├─ Apply fonts, images, effects                │
│  ├─ Generate high-res output                    │
│  └─ Upload to S3                                │
│                                                  │
│  Video Export (2-10 minutes):                   │
│  ├─ Render each frame (30fps = 1800 frames/min)│
│  ├─ Apply animations/transitions                │
│  ├─ Encode to MP4                               │
│  ├─ Add audio track                             │
│  └─ Upload to S3                                │
│                                                  │
│  GIF Export (30-90 seconds):                    │
│  ├─ Render animation frames                     │
│  ├─ Optimize color palette                      │
│  ├─ Compress (gifsicle)                         │
│  └─ Upload to S3                                │
└──────────────────────────────────────────────────┘

Worker Specialization:

├─ PDF Workers: CPU-optimized (C5 instances)
├─ Video Workers: GPU-accelerated (P3 instances)
├─ Image Workers: Memory-optimized (R5 instances)
└─ GIF Workers: CPU-optimized

Priority Handling:

Premium Users:
• Dedicated worker pool
• Higher priority queue
• Faster processing (3x)
• No wait time

Free Users:
• Shared worker pool
• Standard queue
• May wait if backlog
• Rate limited

Progress Updates:

WebSocket real-time updates:
{
  export_id: "export_abc",
  status: "rendering",
  progress: 35,
  current_step: "Rendering page 3 of 10",
  eta_seconds: 45
}

Optimizations:

1. CACHING RENDERED ASSETS
   • Common elements cached
   • Fonts, icons pre-rendered
   • Reduces render time by 40%

2. SMART BATCHING
   • Group similar exports
   • Reuse resources
   • Better worker utilization

3. PROGRESSIVE RENDERING
   • Low-res preview first (5 seconds)
   • User sees preview immediately
   • High-res completes in background

Performance:
• Simple export: 10-30 seconds
• Complex export: 30-120 seconds
• Video export: 2-10 minutes
• Queue wait time: 0-30 seconds (premium), 0-5 min (free)
```

---

### Example 6: Mailchimp - Email Campaign Delivery

```
┌──────────────────────────────────────────────────────────────────┐
│            MAILCHIMP CAMPAIGN DELIVERY ARCHITECTURE               │
└──────────────────────────────────────────────────────────────────┘

Scale: Send billions of emails/month

Challenge: Deliver to millions of recipients reliably

Architecture:

User schedules campaign
   ↓
┌────────────────────────┐
│   Campaign API         │
│   • Validate list      │
│   • Check limits       │
│   • Create campaign    │
│   Time: 100ms          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Campaign DB          │
│   • Campaign record    │
│   • Status: scheduled  │
│   Time: 50ms           │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Scheduler            │
│   • Wait until send time│
│   • Or send immediately│
└────────┬───────────────┘
         │
         ▼ Campaign starts
         │
┌────────────────────────┐
│   Segmentation Service │
│   • Split into batches │
│   • 1000 recipients/batch│
│   • Create send jobs   │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Send Queue (SQS)     │
│   • 100K send jobs     │
│   • Rate-limited       │
└────────┬───────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│           EMAIL SENDER WORKERS                    │
│  (100-1000 workers)                              │
│                                                  │
│  For each batch (1000 recipients):              │
│                                                  │
│  1. PERSONALIZATION (2 sec)                     │
│     ├─ Insert recipient name                    │
│     ├─ Custom content blocks                    │
│     └─ Dynamic images                           │
│                                                  │
│  2. RENDERING (1 sec)                           │
│     ├─ Generate HTML                            │
│     ├─ Inline CSS                               │
│     └─ Optimize for email clients               │
│                                                  │
│  3. SENDING (5-10 sec)                          │
│     ├─ SMTP connection                          │
│     ├─ Send to ESP (SendGrid/AWS SES)           │
│     ├─ Rate limit compliance                    │
│     └─ Handle bounces                           │
│                                                  │
│  4. TRACKING (1 sec)                            │
│     ├─ Record send event                        │
│     ├─ Insert tracking pixel                    │
│     └─ Update campaign stats                    │
└──────────────────────────────────────────────────┘

Campaign Progress:

Real-time dashboard:
{
  campaign_id: "camp_abc",
  status: "sending",
  recipients_total: 500000,
  recipients_sent: 275000,
  recipients_pending: 225000,
  progress: 55,
  send_rate: 5000/minute,
  eta_minutes: 45,
  stats: {
    delivered: 270000,
    bounced: 5000,
    opened: 45000,
    clicked: 8000
  }
}

Rate Limiting Strategy:

ESP Limits:
• AWS SES: 14 emails/sec (default)
• SendGrid: Custom based on plan
• Solution: Throttle workers

Implementation:
• Token bucket algorithm
• Distributed rate limiting (Redis)
• Per-ESP limits enforced
• Prevents account suspension

Retry Handling:

Soft Bounces (temporary):
• Retry 3 times with exponential backoff
• 1 min, 5 min, 30 min intervals
• Then mark as failed

Hard Bounces (permanent):
• Don't retry
• Remove from list
• Update recipient status

Optimizations:

1. BATCH SENDING
   • Send 100 emails per SMTP connection
   • Reduces connection overhead
   • 10x throughput improvement

2. WARM-UP STRATEGY
   • New campaigns: Start slow
   • Gradually increase send rate
   • Improves deliverability

3. SMART SCHEDULING
   • Send at optimal times per timezone
   • Spread load over time
   • Better open rates

Performance:
• Campaign setup: <200ms
• Small campaign (10K): 2-5 minutes
• Large campaign (1M): 3-6 hours
• Deliverability: 98%+
```

---

### Example 7: Salesforce - Data Import/Export

```
┌──────────────────────────────────────────────────────────────────┐
│              SALESFORCE DATA JOBS ARCHITECTURE                    │
└──────────────────────────────────────────────────────────────────┘

Scale: 150K+ customers, Massive data volumes

Challenge: Import/export millions of records reliably

Architecture:

User uploads CSV (1M records)
   ↓
┌────────────────────────┐
│   Bulk API             │
│   • Validate CSV       │
│   • Parse headers      │
│   • Create batch job   │
│   Time: 200ms          │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Job Store            │
│   • Job metadata       │
│   • Status: queued     │
│   • Batch size: 10K    │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   Batch Queue          │
│   • Split into batches │
│   • 100 batches        │
└────────┬───────────────┘
         │
         ▼ Job created
         │
         │ Processing (Minutes to Hours)
         ▼
┌──────────────────────────────────────────────────┐
│           BATCH PROCESSING WORKERS                │
│                                                  │
│  For each batch (10K records):                  │
│                                                  │
│  1. VALIDATION (10 sec)                         │
│     ├─ Data type checks                         │
│     ├─ Required fields                          │
│     ├─ Duplicate detection                      │
│     └─ Custom validation rules                  │
│                                                  │
│  2. TRANSFORMATION (5 sec)                      │
│     ├─ Data mapping                             │
│     ├─ Field calculations                       │
│     └─ Lookup relationships                     │
│                                                  │
│  3. DATABASE WRITE (30 sec)                     │
│     ├─ Bulk insert                              │
│     ├─ Update indexes                           │
│     ├─ Trigger workflows                        │
│     └─ Fire events                              │
│                                                  │
│  4. POST-PROCESSING (10 sec)                    │
│     ├─ Send notifications                       │
│     ├─ Update related records                   │
│     └─ Refresh reports                          │
└──────────────────────────────────────────────────┘

Job Status Tracking:

GET /services/async/job_id

{
  id: "job_abc",
  state: "InProgress",
  batches_total: 100,
  batches_completed: 45,
  batches_failed: 2,
  batches_queued: 53,
  records_processed: 450000,
  records_failed: 15000,
  progress: 45
}

Error Handling:

Per-Batch Errors:
• Continue processing other batches
• Don't fail entire job
• Provide error report at end

Error Report:
{
  batch_id: "batch_5",
  records_failed: 150,
  errors: [
    {row: 523, error: "Invalid email"},
    {row: 834, error: "Duplicate record"},
    ...
  ]
}

Optimizations:

1. PARALLEL BATCH PROCESSING
   • Process 10 batches simultaneously
   • 10x faster than sequential
   • Limited by DB write capacity

2. SMART BATCHING
   • Optimal batch size: 10K records
   • Too small: Overhead
   • Too large: Long retries

3. CHECKPOINT & RESUME
   • Save progress after each batch
   • Resume from last checkpoint on failure
   • No duplicate work

Performance:
• Small import (10K): 1-2 minutes
• Medium import (100K): 10-20 minutes
• Large import (1M): 1-3 hours
• Success rate: 98%+ (with retries)
```

---

## Design Patterns

### Pattern 1: Job Queue with Status Polling

```
┌──────────────────────────────────────────────────────────────────┐
│               JOB QUEUE PATTERN                                   │
└──────────────────────────────────────────────────────────────────┘

Flow:

1. SUBMIT
   POST /api/jobs
   Body: {task_type, input_data}
   Response: {job_id, status: "queued"}

2. POLL STATUS
   GET /api/jobs/{job_id}
   Response: {status: "processing", progress: 45}

3. RETRIEVE RESULT
   GET /api/jobs/{job_id}/result
   Response: {data: {...}}

Use Cases:
• Report generation
• Data exports
• Batch processing
• Any task >5 seconds
```

### Pattern 2: Webhook Callbacks

```
┌──────────────────────────────────────────────────────────────────┐
│               WEBHOOK CALLBACK PATTERN                            │
└──────────────────────────────────────────────────────────────────┘

Flow:

1. SUBMIT WITH CALLBACK
   POST /api/jobs
   Body: {
     task_type: "video_encode",
     input: {...},
     callback_url: "https://client.com/webhook"
   }

2. RETURN IMMEDIATELY
   Response: {job_id: "job_abc"}

3. PROCESS ASYNC
   Worker completes job
   
4. CALLBACK
   POST https://client.com/webhook
   Body: {
     job_id: "job_abc",
     status: "completed",
     result_url: "https://cdn.com/output.mp4"
   }

Benefits:
• No polling needed
• Push-based (efficient)
• Real-time notification

Use Cases:
• Payment processing (Stripe webhooks)
• Video encoding (AWS MediaConvert)
• External integrations
```

### Pattern 3: Server-Sent Events (SSE) for Progress

```
┌──────────────────────────────────────────────────────────────────┐
│               SSE PROGRESS PATTERN                                │
└──────────────────────────────────────────────────────────────────┘

Flow:

1. SUBMIT JOB
   POST /api/jobs → {job_id}

2. OPEN SSE CONNECTION
   GET /api/jobs/{job_id}/stream

3. RECEIVE PROGRESS EVENTS
   event: progress
   data: {"progress": 10, "stage": "uploading"}

   event: progress
   data: {"progress": 50, "stage": "processing"}

   event: complete
   data: {"result_url": "..."}

Benefits:
• Real-time updates
• No polling overhead
• Better UX

Use Cases:
• File uploads with progress bar
• Build/deployment status
• Long computations
```

---

## Failure Handling

```
┌──────────────────────────────────────────────────────────────────┐
│                 FAILURE HANDLING STRATEGIES                       │
└──────────────────────────────────────────────────────────────────┘

1. RETRY WITH EXPONENTIAL BACKOFF
   Attempt 1: Immediate
   Attempt 2: Wait 1 minute
   Attempt 3: Wait 5 minutes
   Attempt 4: Wait 30 minutes
   Max: 3-5 retries, then fail

2. DEAD LETTER QUEUE (DLQ)
   Failed jobs → DLQ
   • Manual review
   • Fix and reprocess
   • Don't lose data

3. IDEMPOTENCY
   Same job_id + input → Same result
   • Prevent duplicate processing
   • Safe to retry
   • Use idempotency keys

4. CHECKPOINT & RESUME
   Save progress periodically
   • Resume from checkpoint on failure
   • Don't restart from beginning
   • Critical for multi-hour jobs

5. TIMEOUT HANDLING
   Set visibility timeout on queue
   • If worker crashes, job reappears
   • Another worker picks up
   • Automatic recovery

6. CIRCUIT BREAKER
   If downstream service fails repeatedly:
   • Stop sending jobs temporarily
   • Prevent cascading failures
   • Resume after cooldown
```

---

## Decision Framework

```
┌──────────────────────────────────────────────────────────────────┐
│            LONG-RUNNING TASK DECISION MATRIX                      │
└──────────────────────────────────────────────────────────────────┘

WHEN TO USE ASYNC PATTERN:

Task Duration:
├─ < 5 seconds → Synchronous OK
├─ 5-30 seconds → Consider async
├─ 30-60 seconds → Async recommended
└─ > 60 seconds → Async required (timeout risk)

Task Frequency:
├─ Rare (< 1/minute) → Synchronous OK
├─ Regular (1-100/minute) → Async beneficial
└─ High (> 100/minute) → Async required

Resource Intensity:
├─ Light (simple query) → Synchronous
├─ Medium (some CPU) → Consider async
└─ Heavy (video encoding, ML) → Async required

QUEUE SELECTION:

Simple Use Case:
└─ Redis (LPUSH/BRPOP)

Need Reliability:
└─ RabbitMQ or AWS SQS

High Throughput:
└─ Apache Kafka

Cloud-Native:
└─ AWS SQS, Google Pub/Sub, Azure Service Bus

WORKER SCALING:

Fixed Workload:
└─ Static worker count

Variable Workload:
└─ Auto-scaling based on queue depth

Scheduled Spikes:
└─ Pre-scale before known events

Mixed Workloads:
└─ Multiple worker pools with priorities
```

---

## Interview Guide

```
┌──────────────────────────────────────────────────────────────────┐
│              INTERVIEW FRAMEWORK                                  │
└──────────────────────────────────────────────────────────────────┘

Question: "Design a system for video upload and processing"

STEP 1: CLARIFY (3 min)
• Video size limits?
• Processing requirements?
• Expected latency?
• Scale (uploads/day)?

STEP 2: HIGH-LEVEL DESIGN (10 min)

Client → Upload API → S3 (storage)
                   → Job Queue (SQS/Kafka)
                   → Worker Fleet (processing)
                   → Update status

STEP 3: DEEP DIVE - Async Processing (15 min)

Job Submission:
• Fast validation
• Create job record
• Enqueue for processing
• Return job_id (<100ms)

Worker Processing:
• Poll queue
• Claim job
• Update status
• Process video
• Store result
• Mark complete

Status Checking:
• Client polls GET /api/jobs/{id}
• Returns current progress
• Or use WebSocket for real-time

STEP 4: SCALING (7 min)

Queue:
• Kafka for high throughput
• Partitioned by user_id
• Ordered processing per user

Workers:
• Auto-scaling based on queue depth
• GPU workers for encoding
• 100-1000 instances

Storage:
• S3 for videos (cheap, durable)
• Database for metadata
• CDN for delivery

STEP 5: TRADE-OFFS (5 min)

Consistency:
• Eventual (async processing)
• Acceptable for video uploads

Cost:
• Worker fleet expensive
• But necessary for scale

Complexity:
• More moving parts
• But better UX and scalability
```

---

## Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                  BEST PRACTICES CHECKLIST                         │
└──────────────────────────────────────────────────────────────────┘

ALWAYS DO:
✓ Return immediately with job_id
✓ Provide status check endpoint
✓ Use message queue for reliability
✓ Implement idempotency
✓ Handle failures gracefully
✓ Report progress to users

WORKER MANAGEMENT:
✓ Auto-scale based on queue depth
✓ Use appropriate instance types (CPU/GPU/Memory)
✓ Implement health checks
✓ Set resource limits
✓ Monitor worker utilization

FAILURE HANDLING:
✓ Retry with exponential backoff
✓ Use dead letter queues
✓ Implement circuit breakers
✓ Save checkpoints for long jobs
✓ Alert on repeated failures

STATUS UPDATES:
✓ Update progress periodically
✓ Provide ETA when possible
✓ Show meaningful stage names
✓ Support both polling and webhooks

OPTIMIZATION:
✓ Parallelize when possible
✓ Use priority queues
✓ Batch similar jobs
✓ Cache intermediate results
✓ Profile and optimize hot paths
```

---

**Document Version**: 1.0  
**Created**: January 2025  
**Type**: High-Level Design Guide  
**Examples**: 7 real-world architectures (YouTube, Instagram, Netflix, Dropbox, Canva, Mailchimp, Salesforce)  
**Focus**: Architecture patterns for long-running tasks
