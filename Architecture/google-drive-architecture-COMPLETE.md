# Google Drive System Design - Complete Architecture Diagram

## Full System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              USER LAYER                                      │
│                                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │  Web Client  │    │  Desktop App │    │  Mobile App  │                 │
│  │  (Browser)   │    │  (Mac/Win)   │    │  (iOS/And)   │                 │
│  │              │    │              │    │              │                 │
│  │  - React SPA │    │  - Electron  │    │  - Native    │                 │
│  │  - WebSocket │    │  - FS Watch  │    │  - React N.  │                 │
│  │  - IndexedDB │    │  - Local DB  │    │  - SQLite    │                 │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘                 │
└─────────┼──────────────────┼──────────────────┼────────────────────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       CDN LAYER (CloudFront)                                 │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Edge Locations: 400+ globally                                       │  │
│  │  Content: Thumbnails, previews, small files (<10MB)                  │  │
│  │  Performance: <15ms latency, 95% hit rate                            │  │
│  │  Handles: 60% of read traffic (thumbnails/previews)                  │  │
│  │  Smart routing: Nearest edge for downloads                           │  │
│  │  Cache behaviors:                                                    │  │
│  │  - Thumbnails: 7 days TTL                                            │  │
│  │  - Previews: 24 hours TTL                                            │  │
│  │  - Small files: 1 hour TTL                                           │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LOAD BALANCER (ALB)                           │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  Multi-AZ Deployment | Health Checks (10s) | SSL/TLS Termination     │  │
│  │  Geo-routing | Auto-failover | Sticky sessions (sync)                │  │
│  │  WebSocket support | Connection draining | 99.99% availability       │  │
│  │  Request routing: Path-based + Host-based                            │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────┬────────────────────────────────┬───────────────────────────┘
                 │                                │
         ┌───────┴────────┐              ┌────────┴───────┐
         │                │              │                │
         ▼                ▼              ▼                ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│  API Gateway   │  │  API Gateway   │  │  API Gateway   │  │  API Gateway   │
│  Cluster 1     │  │  Cluster 2     │  │  Cluster 3     │  │  Cluster 4     │
│  (US-EAST-1a)  │  │  (US-EAST-1b)  │  │  (US-WEST)     │  │  (EU-WEST)     │
│                │  │                │  │                │  │                │
│  10 instances  │  │  10 instances  │  │  8 instances   │  │  6 instances   │
└────────┬───────┘  └────────┬───────┘  └────────┬───────┘  └────────┬───────┘
         │                   │                   │                   │
         └───────────────────┴───────────────────┴───────────────────┘
                                      │
┌─────────────────────────────────────┼─────────────────────────────────────┐
│                        SERVICE LAYER (Microservices)                        │
│                                                                            │
│     ┌────────────────────────────────┴────────────────────────────┐       │
│     │                                                              │       │
│     ▼                ▼                ▼                ▼           │       │
│ ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │       │
│ │  UPLOAD  │  │ DOWNLOAD │  │   SYNC   │  │  SHARE   │           │       │
│ │  SERVICE │  │  SERVICE │  │  SERVICE │  │  SERVICE │           │       │
│ │          │  │          │  │          │  │          │           │       │
│ │ 50 inst. │  │ 40 inst. │  │ 60 inst. │  │ 20 inst. │           │       │
│ │ Handles: │  │ Handles: │  │ Handles: │  │ Handles: │           │       │
│ │ -Chunking│  │ -Assembly│  │ -Delta   │  │ -Perms   │           │       │
│ │ -Dedup   │  │ -Resume  │  │ -Conflict│  │ -Links   │           │       │
│ │ -Encrypt │  │ -Stream  │  │ -Notify  │  │ -Collab  │           │       │
│ │ -Validate│  │ -Decrypt │  │ -Queue   │  │ -Audit   │           │       │
│ │ -S3 API  │  │ -CDN     │  │ -Vector  │  │ -Token   │           │       │
│ └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │       │
│      │             │             │             │                  │       │
│      ▼             ▼             ▼             ▼                  │       │
│ ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐          │       │
│ │ METADATA │  │ PREVIEW  │  │  SEARCH  │  │ VERSION  │          │       │
│ │  SERVICE │  │  SERVICE │  │  SERVICE │  │  SERVICE │          │       │
│ │          │  │          │  │          │  │          │          │       │
│ │ 30 inst. │  │ 25 inst. │  │ 15 inst. │  │ 20 inst. │          │       │
│ │ Handles: │  │ Handles: │  │ Handles: │  │ Handles: │          │       │
│ │ -Tree    │  │ -Thumb   │  │ -Index   │  │ -History │          │       │
│ │ -ACL     │  │ -Convert │  │ -Query   │  │ -Restore │          │       │
│ │ -Quota   │  │ -Cache   │  │ -Filter  │  │ -Diff    │          │       │
│ │ -Activity│  │ -Render  │  │ -Rank    │  │ -Purge   │          │       │
│ │ -DB OPS  │  │ -FFmpeg  │  │ -ES OPS  │  │ -Compress│          │       │
│ └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘          │       │
└──────┼──────────────┼──────────────┼──────────────┼───────────────┘       │
       │              │              │              │
       ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  MESSAGE QUEUE LAYER (SQS + SNS + Kafka)                     │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  SQS QUEUES (Standard & FIFO):                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐ │  │
│  │  │  • file_upload_chunks (Standard)                               │ │  │
│  │  │    - Visibility timeout: 5 min                                 │ │  │
│  │  │    - Max receives: 3                                           │ │  │
│  │  │    - DLQ: file_upload_chunks_dlq                               │ │  │
│  │  │                                                                 │ │  │
│  │  │  • file_uploaded_complete (FIFO)                               │ │  │
│  │  │    - Deduplication: Content-based                              │ │  │
│  │  │    - Message group: file_id                                    │ │  │
│  │  │                                                                 │ │  │
│  │  │  • file_modified (Standard)                                    │ │  │
│  │  │  • file_shared (Standard)                                      │ │  │
│  │  │  • file_deleted (Standard)                                     │ │  │
│  │  │                                                                 │ │  │
│  │  │  • sync_request (FIFO)                                         │ │  │
│  │  │    - Per-user message groups                                   │ │  │
│  │  │                                                                 │ │  │
│  │  │  • preview_generation (Standard)                               │ │  │
│  │  │    - Priority queue: High/Low                                  │ │  │
│  │  │                                                                 │ │  │
│  │  │  • search_indexing (Standard)                                  │ │  │
│  │  │  • virus_scanning (Standard)                                   │ │  │
│  │  │  • quota_update (FIFO)                                         │ │  │
│  │  │  • activity_logging (Standard)                                 │ │  │
│  │  └────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                       │  │
│  │  SNS TOPICS (Fan-out):                                               │  │
│  │  ┌────────────────────────────────────────────────────────────────┐ │  │
│  │  │  • file_events                                                 │ │  │
│  │  │    Subscribers: 6 SQS queues                                   │ │  │
│  │  │    - Preview generation                                        │ │  │
│  │  │    - Search indexing                                           │ │  │
│  │  │    - Virus scanning                                            │ │  │
│  │  │    - Activity logging                                          │ │  │
│  │  │    - Quota updates                                             │ │  │
│  │  │    - Analytics pipeline                                        │ │  │
│  │  │                                                                 │ │  │
│  │  │  • sync_notifications                                          │ │  │
│  │  │    Subscribers: WebSocket servers, Mobile push, Email          │ │  │
│  │  └────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                       │  │
│  │  KAFKA STREAMS (Real-time processing):                               │  │
│  │  ┌────────────────────────────────────────────────────────────────┐ │  │
│  │  │  • realtime_sync_events (10 partitions)                        │ │  │
│  │  │  • collaboration_operations (5 partitions)                     │ │  │
│  │  │  • metrics_collection (20 partitions)                          │ │  │
│  │  └────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                       │  │
│  │  Configuration:                                                      │  │
│  │  • Message retention: 14 days (SQS), 7 days (Kafka)                 │  │
│  │  • Dead letter queues: Enabled for all                              │  │
│  │  • Throughput: 100K messages/sec                                     │  │
│  │  • Monitoring: CloudWatch + Custom metrics                           │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────────────────────┘
                             │
                             │ Consumed by Worker Fleet
                             │
┌────────────────────────────┼────────────────────────────────────────────────┐
│                       WORKER FLEET (Auto-scaling)                            │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  UPLOAD PROCESSING WORKERS (200 instances)                            │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Process uploaded chunks                              │ │ │
│  │  │  Queue: file_upload_chunks                                      │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Validate chunk integrity (MD5, size)                        │ │ │
│  │  │  2. Encrypt chunk (AES-256)                                     │ │ │
│  │  │  3. Compress if beneficial (gzip)                               │ │ │
│  │  │  4. Upload to S3 (multipart)                                    │ │ │
│  │  │  5. Update chunk tracking in Redis                              │ │ │
│  │  │  6. Check if all chunks complete                                │ │ │
│  │  │  7. If complete → Publish to file_uploaded_complete queue       │ │ │
│  │  │  Capacity: 10K chunks/sec                                       │ │ │
│  │  │  Latency: 150ms per chunk                                       │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  SYNC WORKERS (500 instances - Critical Path)                        │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Real-time file synchronization                       │ │ │
│  │  │  Queue: sync_request (FIFO)                                     │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Receive sync request from device                            │ │ │
│  │  │  2. Calculate delta (binary diff algorithm)                     │ │ │
│  │  │  3. Check version vectors for conflicts                         │ │ │
│  │  │  4. If conflict:                                                 │ │ │
│  │  │     a. Create conflicted copy                                   │ │ │
│  │  │     b. Notify all devices                                       │ │ │
│  │  │     c. Log conflict for resolution                              │ │ │
│  │  │  5. If no conflict:                                              │ │ │
│  │  │     a. Apply delta to base version                              │ │ │
│  │  │     b. Store new version in S3                                  │ │ │
│  │  │     c. Update metadata in PostgreSQL                            │ │ │
│  │  │     d. Invalidate caches                                        │ │ │
│  │  │  6. Publish to sync_notifications (SNS)                         │ │ │
│  │  │  7. Push real-time updates via WebSocket                        │ │ │
│  │  │  8. Send mobile push notifications (FCM/APNS)                   │ │ │
│  │  │  Capacity: 50K syncs/sec                                        │ │ │
│  │  │  Latency: 300-500ms per sync                                    │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  PREVIEW GENERATION WORKERS (200 instances)                          │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Generate thumbnails and previews                     │ │ │
│  │  │  Queue: preview_generation (Priority)                           │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Download file from S3                                       │ │ │
│  │  │  2. Detect file type (MIME + magic bytes)                       │ │ │
│  │  │  3. Generate thumbnails (multiple sizes):                       │ │ │
│  │  │     - Small: 150x150                                            │ │ │
│  │  │     - Medium: 400x400                                           │ │ │
│  │  │     - Large: 800x800                                            │ │ │
│  │  │  4. For specific file types:                                    │ │ │
│  │  │     - Images: ImageMagick conversion                            │ │ │
│  │  │     - Videos: FFmpeg frame extraction                           │ │ │
│  │  │     - Documents: LibreOffice/Poppler PDF rendering              │ │ │
│  │  │     - Code: Syntax highlighting                                 │ │ │
│  │  │  5. Optimize images (WebP, compression)                         │ │ │
│  │  │  6. Upload thumbnails to S3                                     │ │ │
│  │  │  7. Cache in Redis                                              │ │ │
│  │  │  8. Push to CDN (CloudFront)                                    │ │ │
│  │  │  9. Update metadata with preview URLs                           │ │ │
│  │  │  Capacity: 15K previews/sec                                     │ │ │
│  │  │  Latency: 2-10 seconds per file                                 │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  SEARCH INDEXING WORKERS (100 instances)                             │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Index files for search                               │ │ │
│  │  │  Queue: search_indexing                                          │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Download file metadata and content sample                   │ │ │
│  │  │  2. Extract searchable text:                                    │ │ │
│  │  │     - PDF: Text extraction via Tika                             │ │ │
│  │  │     - Office: Text extraction via Tika                          │ │ │
│  │  │     - Images: OCR via Tesseract                                 │ │ │
│  │  │     - Code: Language detection + keywords                       │ │ │
│  │  │     - Text: Direct indexing                                     │ │ │
│  │  │  3. Extract metadata:                                            │ │ │
│  │  │     - File properties                                           │ │ │
│  │  │     - EXIF data (images)                                        │ │ │
│  │  │     - Audio metadata (MP3, etc.)                                │ │ │
│  │  │  4. Build searchable document:                                  │ │ │
│  │  │     - Title, content, tags                                      │ │ │
│  │  │     - User, folder, dates                                       │ │ │
│  │  │     - Permissions for search filtering                          │ │ │
│  │  │  5. Index in Elasticsearch                                      │ │ │
│  │  │  6. Update search suggestions                                   │ │ │
│  │  │  7. Build related files graph                                   │ │ │
│  │  │  Capacity: 5K files/sec                                         │ │ │
│  │  │  Latency: 1-5 seconds per file                                  │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  VIRUS SCANNING WORKERS (50 instances)                               │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Scan files for malware                               │ │ │
│  │  │  Queue: virus_scanning                                           │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Download file from S3 (stream for large files)              │ │ │
│  │  │  2. Scan with ClamAV                                            │ │ │
│  │  │  3. Check file hash against threat databases                    │ │ │
│  │  │  4. Perform heuristic analysis                                  │ │ │
│  │  │  5. If malware detected:                                        │ │ │
│  │  │     a. Quarantine file (move to isolated S3 bucket)             │ │ │
│  │  │     b. Mark file as malware in DB                               │ │ │
│  │  │     c. Notify user via email                                    │ │ │
│  │  │     d. Block downloads                                          │ │ │
│  │  │     e. Log security incident                                    │ │ │
│  │  │  6. If clean:                                                    │ │ │
│  │  │     a. Mark as scanned in metadata                              │ │ │
│  │  │     b. Allow file access                                        │ │ │
│  │  │  7. Update virus database daily                                 │ │ │
│  │  │  Capacity: 2K scans/sec                                         │ │ │
│  │  │  Latency: 2-30 seconds per file                                 │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  METADATA WORKERS (80 instances)                                     │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Post-upload metadata processing                      │ │ │
│  │  │  Queue: file_uploaded_complete                                   │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Verify all chunks uploaded successfully                     │ │ │
│  │  │  2. Finalize multipart upload in S3                             │ │ │
│  │  │  3. Create file metadata record in PostgreSQL                   │ │ │
│  │  │  4. Update folder tree structure                                │ │ │
│  │  │  5. Set default permissions (owner)                             │ │ │
│  │  │  6. Create initial version record                               │ │ │
│  │  │  7. Update user quota                                           │ │ │
│  │  │  8. Add to user's recent files                                  │ │ │
│  │  │  9. Publish to file_events (SNS) for fan-out                    │ │ │
│  │  │  10. Cache metadata in Redis                                    │ │ │
│  │  │  11. Log activity                                               │ │ │
│  │  │  Capacity: 8K files/sec                                         │ │ │
│  │  │  Latency: 50-100ms per file                                     │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  QUOTA & ANALYTICS WORKERS (30 instances)                            │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Track usage and enforce quotas                       │ │ │
│  │  │  Queue: quota_update                                             │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Update user storage quota                                   │ │ │
│  │  │  2. Check if over quota                                         │ │ │
│  │  │  3. Send warnings at 80%, 90%, 100%                             │ │ │
│  │  │  4. Track file type statistics                                  │ │ │
│  │  │  5. Update usage analytics                                      │ │ │
│  │  │  6. Generate daily usage reports                                │ │ │
│  │  │  Capacity: 20K updates/sec                                      │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │  ACTIVITY LOGGING WORKERS (40 instances)                             │ │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │ │
│  │  │  Function: Log all file activities                              │ │ │
│  │  │  Queue: activity_logging                                         │ │ │
│  │  │  Tasks:                                                          │ │ │
│  │  │  1. Parse event data                                            │ │ │
│  │  │  2. Enrich with user/file info                                  │ │ │
│  │  │  3. Store in PostgreSQL activity_log table                      │ │ │
│  │  │  4. Store in time-series DB for analytics                       │ │ │
│  │  │  5. Update file access timestamps                               │ │ │
│  │  │  6. Trigger audit alerts if needed                              │ │ │
│  │  │  Capacity: 30K events/sec                                       │ │ │
│  │  └─────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
                             │
    ┌────────────────────────┼────────────────────────┐
    │                        │                        │
    ▼                        ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STORAGE LAYER                                        │
│                                                                              │
│  ┌────────────────────┐  ┌──────────────────┐  ┌──────────────────────┐   │
