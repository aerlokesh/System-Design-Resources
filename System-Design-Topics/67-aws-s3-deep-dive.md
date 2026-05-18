# 🎯 Topic 67: AWS S3 Deep Dive

> **System Design Interview — Deep Dive**
> A comprehensive guide covering Amazon S3 — object storage architecture, 11-nines durability, storage classes (Standard/IA/Glacier), presigned URLs, event notifications, versioning, lifecycle policies, performance optimization (multipart upload, byte-range fetches, S3 Transfer Acceleration), consistency model, S3 Select, security (bucket policies, encryption), and production-grade interview scripts.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [S3 Architecture — How It Achieves 11-Nines Durability](#s3-architecture--how-it-achieves-11-nines-durability)
3. [Objects, Buckets, and Keys](#objects-buckets-and-keys)
4. [Storage Classes](#storage-classes)
5. [Consistency Model — Strong Read-After-Write](#consistency-model--strong-read-after-write)
6. [Performance Optimization](#performance-optimization)
7. [Presigned URLs — Direct Client Upload/Download](#presigned-urls--direct-client-uploaddownload)
8. [Event Notifications](#event-notifications)
9. [Versioning and Object Lock](#versioning-and-object-lock)
10. [Lifecycle Policies](#lifecycle-policies)
11. [Security — Bucket Policies, ACLs, Encryption](#security--bucket-policies-acls-encryption)
12. [S3 Select and Glacier Select](#s3-select-and-glacier-select)
13. [S3 Transfer Acceleration](#s3-transfer-acceleration)
14. [Cross-Region Replication (CRR)](#cross-region-replication-crr)
15. [Cost Model and Optimization](#cost-model-and-optimization)
16. [S3 in System Design — Common Patterns](#s3-in-system-design--common-patterns)
17. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
18. [Common Interview Mistakes](#common-interview-mistakes)
19. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Amazon S3** (Simple Storage Service) is an object storage service offering industry-leading durability (99.999999999% — 11 nines), availability, scalability, and security. It stores any amount of data (unlimited) and serves it at any scale. S3 is the backbone of almost every AWS architecture — storing media, backups, data lakes, static websites, and application data.

```
The mental model:

  S3 = a flat key-value store where:
    Key = "folder/subfolder/filename.ext" (string, up to 1024 bytes)
    Value = object content (0 bytes to 5 TB)
    Metadata = user-defined key-value pairs + system metadata

  NOT a filesystem. No directories. "Folders" are just key prefixes.
  
  s3://my-bucket/photos/2024/march/photo.jpg
    Bucket: my-bucket
    Key: photos/2024/march/photo.jpg
    This is a FLAT key, not a nested directory structure.

Performance:
  Throughput: 5500 GET/sec and 3500 PUT/sec per PREFIX
  Object size: 0 bytes to 5 TB
  Number of objects per bucket: Unlimited
  Number of buckets per account: 100 (soft limit, increase to 1000+)
```

### When to Use S3

```
USE S3 for:
  ✅ Media storage (images, videos, audio)
  ✅ Static website hosting
  ✅ Data lake (raw data for analytics)
  ✅ Backups and archives
  ✅ Application artifacts (logs, exports, reports)
  ✅ ML training data storage
  ✅ CDN origin (CloudFront serves from S3)

DON'T USE S3 for:
  ❌ Frequently updated data (use a database)
  ❌ Low-latency key-value lookups (use DynamoDB/ElastiCache)
  ❌ File system semantics (use EFS or FSx)
  ❌ Block storage for EC2 (use EBS)
  ❌ Transactional writes (no atomic multi-object operations)
```

---

## S3 Architecture — How It Achieves 11-Nines Durability

### Storage Architecture

```
When you PUT an object to S3:
  1. Object is received by the S3 front-end (HTTP API)
  2. S3 splits the object into chunks
  3. Each chunk is replicated to multiple storage servers across multiple AZs
  4. Standard class: Minimum 3 AZs
  5. PUT returns success only after data is durably stored in ALL required locations
  
  Result: 99.999999999% durability = losing 1 object out of 10 billion every year

  Internally:
  ┌──────────┐     ┌──────────┐     ┌──────────┐
  │   AZ-1    │     │   AZ-2    │     │   AZ-3    │
  │ ┌──────┐  │     │ ┌──────┐  │     │ ┌──────┐  │
  │ │Chunk1│  │     │ │Chunk1│  │     │ │Chunk1│  │
  │ │Chunk2│  │     │ │Chunk2│  │     │ │Chunk2│  │
  │ └──────┘  │     │ └──────┘  │     │ └──────┘  │
  └──────────┘     └──────────┘     └──────────┘
  
  Automatic repair: If any replica is lost, S3 automatically recreates it.
  No user action needed. Self-healing.
```

### Durability vs Availability

```
Durability (will my data be lost?):
  Standard:    99.999999999% (11 nines) → lose 1 object per 10 billion per year
  One Zone-IA: 99.999999999% within that AZ → AZ failure = data loss

Availability (can I access my data right now?):
  Standard:    99.99% (52 minutes of downtime per year)
  Standard-IA: 99.9% (8.7 hours per year)
  One Zone-IA: 99.5%
  Glacier:     99.99% (but retrieval takes minutes-hours)
```

---

## Objects, Buckets, and Keys

### Object Anatomy

```
An S3 object consists of:
  Key:          "media/photos/user42/profile.jpg" (unique within bucket)
  Value:        Binary content (the actual file data, 0 - 5 TB)
  Metadata:     System metadata (Content-Type, Last-Modified, ETag)
                + User metadata (x-amz-meta-*: custom key-value pairs)
  Version ID:   If versioning enabled (unique per version of the object)
  ACL:          Access control (legacy, prefer bucket policies)
  Tags:         Key-value tags for classification and lifecycle

ETag:
  For simple uploads: MD5 hash of the object content
  For multipart uploads: Hash of part ETags + "-" + number of parts
  Used for: Cache validation, conditional requests (If-None-Match)
```

### Key Design for Performance

```
S3 performance: 5500 GET/sec and 3500 PUT/sec per PREFIX.

  Old guidance (pre-2018): Add random prefix to avoid hot partitions.
  Current (2018+): S3 automatically partitions. No prefix randomization needed.
  
  BUT: If you have millions of objects with the same prefix accessed simultaneously,
       spreading across different prefixes still helps at extreme scale.

Good key patterns:
  ✅ media/{user_id}/{upload_id}.jpg     → distributes by user_id
  ✅ logs/{date}/{hour}/{instance}.log   → distributes by time
  ✅ data-lake/year=2024/month=03/...    → Hive-compatible partitioning

Performance at scale:
  1 prefix with 5500 GET/sec is the limit.
  Split into 10 prefixes → 55,000 GET/sec total.
  Applications that need >5500 req/sec per prefix: use CloudFront in front.
```

---

## Storage Classes

### Comparison

| Class | Durability | Availability | Retrieval | Min Duration | Use Case |
|---|---|---|---|---|---|
| **Standard** | 11 nines | 99.99% | Instant | None | Frequently accessed data |
| **Intelligent-Tiering** | 11 nines | 99.9% | Instant | 30 days | Unknown access patterns |
| **Standard-IA** | 11 nines | 99.9% | Instant | 30 days | Infrequent but needs instant access |
| **One Zone-IA** | 11 nines (1 AZ) | 99.5% | Instant | 30 days | Reproducible, infrequent |
| **Glacier Instant** | 11 nines | 99.9% | Instant | 90 days | Archive with instant access |
| **Glacier Flexible** | 11 nines | 99.99% | 1-12 hours | 90 days | Archive, hours retrieval OK |
| **Glacier Deep Archive** | 11 nines | 99.99% | 12-48 hours | 180 days | Long-term archive, rare access |

### Pricing (us-east-1)

```
Storage per GB/month:
  Standard:          $0.023
  Standard-IA:       $0.0125 (46% cheaper)
  One Zone-IA:       $0.01   (57% cheaper)
  Glacier Instant:   $0.004  (83% cheaper)
  Glacier Flexible:  $0.0036 (84% cheaper)
  Glacier Deep:      $0.00099 (96% cheaper!)

Retrieval cost:
  Standard:          Free
  Standard-IA:       $0.01/GB retrieved
  Glacier Instant:   $0.03/GB retrieved
  Glacier Flexible:  $0.01/GB (Expedited: $0.03/GB)
  Glacier Deep:      $0.02/GB

Key insight: Cheaper storage = more expensive retrieval.
  Hot data → Standard (free retrieval)
  Cold data → Glacier (cheap storage, expensive retrieval)
```

---

## Consistency Model — Strong Read-After-Write

### Current Model (Since December 2020)

```
S3 provides STRONG read-after-write consistency for ALL operations:

  PUT new object → immediately visible on subsequent GET ✓
  PUT overwrite  → immediately visible on subsequent GET ✓
  DELETE         → immediately visible on subsequent GET (404) ✓
  LIST           → immediately reflects recent PUT/DELETE ✓

Before 2020: S3 had eventual consistency for overwrites and deletes.
  PUT overwrite → GET might return old version for a few seconds.
  This is NO LONGER the case. S3 is strongly consistent.

Interview note:
  If asked about S3 consistency, the answer is:
  "S3 provides strong read-after-write consistency since December 2020.
   There's no eventual consistency for any operation."
```

---

## Performance Optimization

### Multipart Upload (for Large Files)

```
Files > 100 MB should use multipart upload:
  1. Initiate multipart upload → get upload_id
  2. Upload parts in PARALLEL (5 MB - 5 GB per part)
  3. Complete multipart upload (assemble parts)

Benefits:
  - Parallel upload: 10 parts at once → 10x effective bandwidth
  - Resumable: If one part fails, retry only that part
  - Required for objects > 5 GB (single PUT max is 5 GB)
  - Recommended for >100 MB

Example: 1 GB file, 10 parts × 100 MB
  Sequential: 1 GB / 50 Mbps = 160 seconds
  Parallel (10 parts): 100 MB / 50 Mbps = 16 seconds (10x faster)
```

### Byte-Range Fetches (Partial Downloads)

```
GET object with Range header:
  GET /my-video.mp4
  Range: bytes=0-999999  → first 1 MB only

Use cases:
  - Video seeking: Only fetch the 10-second segment the user is watching
  - Parallel download: Fetch different byte ranges in parallel from multiple threads
  - Resume interrupted downloads: Start from where you left off
  - HEAD request for metadata only (no body)
```

### S3 Transfer Acceleration

```
Uploads from distant locations (e.g., Sydney to us-east-1) are slow.
Transfer Acceleration: Routes uploads through CloudFront edge locations.

  Without acceleration: Sydney → Internet → us-east-1 S3 (high latency)
  With acceleration: Sydney → Sydney Edge PoP → AWS backbone → us-east-1 S3

  Speed improvement: 50-500% for long-distance uploads.
  
  Endpoint: mybucket.s3-accelerate.amazonaws.com
  Cost: $0.04/GB (on top of normal PUT cost)
```

---

## Presigned URLs — Direct Client Upload/Download

### Presigned PUT (Upload)

```python
# Server generates a presigned URL → client uploads directly to S3
s3 = boto3.client('s3')
url = s3.generate_presigned_url(
    'put_object',
    Params={'Bucket': 'media-uploads', 'Key': 'user42/photo.jpg',
            'ContentType': 'image/jpeg'},
    ExpiresIn=900  # 15 minutes
)
# Client: PUT {url} with file body → goes directly to S3
# App server never sees the bytes. Zero bandwidth/CPU usage.
```

### Presigned GET (Download)

```python
# Generate a time-limited download URL for private content
url = s3.generate_presigned_url(
    'get_object',
    Params={'Bucket': 'private-content', 'Key': 'reports/q1-2024.pdf'},
    ExpiresIn=3600  # 1 hour
)
# Share URL with authorized user → they can download for 1 hour
# After expiry → 403 Forbidden
```

### Why Presigned URLs Matter in System Design

```
Without presigned URLs:
  Client → App Server (50 MB upload) → App Server → S3
  App server bandwidth: 50 MB × N users = massive cost
  
With presigned URLs:
  Client → S3 directly (50 MB upload)
  App server: generates URL only (1 KB response, 0 bandwidth)
  
  At 100M uploads/day × 5 MB each:
    Without presigned: 500 TB/day through app servers = $50K/day in bandwidth
    With presigned: $0 app server bandwidth = $3K/day (S3 PUT cost only)
    Savings: $47K/day = $17M/year
```

---

## Event Notifications

### S3 → Lambda / SQS / SNS / EventBridge

```
When an object is created/deleted, S3 can trigger:
  - Lambda function (process the file)
  - SQS queue (buffer for async processing)
  - SNS topic (fan-out to multiple consumers)
  - EventBridge (complex routing rules)

Configuration:
  Event types:
    s3:ObjectCreated:*          → any new object
    s3:ObjectCreated:Put        → single PUT
    s3:ObjectCreated:CompleteMultipartUpload
    s3:ObjectRemoved:*          → any deletion
    s3:ObjectRestore:Completed  → Glacier restore done

  Filter: By prefix and/or suffix
    Prefix: "uploads/"    → only trigger for uploads/ prefix
    Suffix: ".jpg"        → only trigger for JPEG files

Use case: Media processing pipeline
  User uploads photo → S3 event → Lambda (resize/compress) → S3 (processed)
```

---

## Versioning and Object Lock

### Versioning

```
Enable versioning on a bucket:
  Every PUT creates a NEW version (old versions preserved).
  DELETE adds a "delete marker" (previous versions still exist).
  
  Use cases:
    - Accidental deletion protection (restore previous version)
    - Audit trail (who changed what, when)
    - Data retention compliance
    
  Cost: You pay for ALL versions stored.
    100 MB file updated 10 times = 1 GB stored (all 10 versions).
    Use lifecycle policies to expire old versions after N days.
```

### Object Lock (WORM)

```
Object Lock: Write Once, Read Many. Prevents deletion or modification.
  Compliance mode: NO ONE can delete (not even root account) until retention expires.
  Governance mode: Special permissions can override (but logged).
  
  Use cases: Financial records, medical records, regulatory compliance.
  Requires versioning enabled.
```

---

## Lifecycle Policies

### Auto-Tiering Rules

```json
{
  "Rules": [
    {
      "ID": "Move-to-IA-after-30-days",
      "Filter": {"Prefix": "logs/"},
      "Status": "Enabled",
      "Transitions": [
        {"Days": 30, "StorageClass": "STANDARD_IA"},
        {"Days": 90, "StorageClass": "GLACIER"},
        {"Days": 365, "StorageClass": "DEEP_ARCHIVE"}
      ],
      "Expiration": {"Days": 730}
    },
    {
      "ID": "Delete-old-versions",
      "Filter": {},
      "Status": "Enabled",
      "NoncurrentVersionExpiration": {"NoncurrentDays": 30}
    }
  ]
}

This rule:
  - Day 0-30: logs/ in Standard ($0.023/GB)
  - Day 30-90: Auto-moves to IA ($0.0125/GB)
  - Day 90-365: Auto-moves to Glacier ($0.0036/GB)
  - Day 365-730: Auto-moves to Deep Archive ($0.00099/GB)
  - Day 730+: Auto-deleted
  - Old versions: Deleted after 30 days
  
  Savings: 80-95% vs keeping everything in Standard forever.
```

---

## Security — Bucket Policies, ACLs, Encryption

### Bucket Policies (Recommended)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontAccess",
      "Effect": "Allow",
      "Principal": {"Service": "cloudfront.amazonaws.com"},
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::my-media-bucket/*",
      "Condition": {
        "StringEquals": {"AWS:SourceArn": "arn:aws:cloudfront::123456789012:distribution/E12345"}
      }
    }
  ]
}
```

### Encryption

```
Server-side encryption (SSE):
  SSE-S3:   AWS manages keys. Default. Zero effort.
  SSE-KMS:  You manage keys in KMS. Audit trail. Per-object key possible.
  SSE-C:    You provide the key with every request. S3 doesn't store key.

Client-side encryption:
  Encrypt BEFORE uploading. S3 stores encrypted blob.
  Only you can decrypt. S3 never sees plaintext.
  
Default encryption (bucket-level):
  All objects automatically encrypted at rest. No opt-in per object.
  Enabled by default since January 2023.
```

### Block Public Access

```
S3 Block Public Access (account or bucket level):
  BlockPublicAcls: true
  IgnorePublicAcls: true
  BlockPublicPolicy: true
  RestrictPublicBuckets: true

  Prevents accidental public exposure.
  ALWAYS enable unless you're intentionally serving a public static website.
```

---

## S3 Select and Glacier Select

```
S3 Select: Run SQL queries WITHIN S3 objects (CSV, JSON, Parquet).
  Instead of downloading 1 GB CSV → filtering in your app:
  Send SQL: SELECT * FROM s3object WHERE status = 'active'
  S3 returns only matching rows → transfers 10 MB instead of 1 GB.
  
  Cost savings: 90% less data transferred = 90% less cost.
  Performance: 4x faster (less network transfer).
  
  Limitations: Simple SQL only. No joins. Single object per query.
  For complex analytics: Use Athena (SQL across many S3 objects).
```

---

## S3 Transfer Acceleration

```
For long-distance uploads (users far from bucket's region):

Normal: User in Sydney → Public internet → S3 in us-east-1
  Latency: High (many hops across public internet)
  
Accelerated: User in Sydney → CloudFront Sydney PoP → AWS backbone → S3 us-east-1
  Latency: Much lower (AWS backbone is faster than public internet)
  
Speed improvement: 50-500% for intercontinental transfers.
Cost: $0.04/GB (upload) + $0.08/GB (download) on top of normal S3 costs.

When to use:
  ✅ Global user base uploading to a single-region bucket
  ✅ Regular large file uploads from distant locations
  ❌ Same-region access (no benefit)
  ❌ Small files (overhead not worth it)
```

---

## Cross-Region Replication (CRR)

```
Replicate objects from one bucket to another (different region):

  Source: us-east-1/my-bucket → Destination: eu-west-1/my-bucket-replica

Replication:
  - Async (not instant — typically seconds to minutes)
  - Same-region replication (SRR) also available
  - Can replicate entire bucket or filtered by prefix/tag
  
Use cases:
  - Disaster recovery (data in 2+ regions)
  - Low-latency access (serve from nearest region)
  - Compliance (data residency requirements)
  - Aggregation (multiple source regions → one analytics bucket)

Configuration:
  Requires versioning on BOTH source and destination buckets.
  IAM role for S3 to write to destination.
  Replication rules specify what to replicate (prefix, tags).
```

---

## Cost Model and Optimization

### Pricing Summary

```
S3 Standard (us-east-1):
  Storage:  $0.023/GB/month (first 50 TB)
  PUT:      $0.005 per 1000 requests
  GET:      $0.0004 per 1000 requests
  Transfer: $0.09/GB (to internet, first 10 TB). Free to CloudFront.

Cost optimization:
  1. Lifecycle policies: Move cold data to IA/Glacier automatically.
  2. Intelligent-Tiering: For unknown access patterns (auto-moves between tiers).
  3. Delete unnecessary data: Incomplete multipart uploads, old versions.
  4. Use CloudFront: S3 → CloudFront transfer is FREE. Internet egress isn't.
  5. Same-region: Keep compute and S3 in same region (free intra-region transfer).
  6. S3 Select: Query in-place instead of downloading entire objects.
  7. Compress before storing: gzip/zstd reduces storage and transfer costs.
```

---

## S3 in System Design — Common Patterns

### Pattern 1: Media Upload + CDN Serving

```
Upload: Client → Presigned URL → S3 (raw)
Process: S3 Event → Lambda → Resize/Compress → S3 (processed)
Serve: Client → CloudFront CDN → S3 (origin)

This is the standard media pipeline (see Topic 58).
```

### Pattern 2: Data Lake

```
Raw data: Application logs, events, exports → S3 (raw zone)
Transform: AWS Glue/EMR → clean, partition → S3 (processed zone)
Query: Athena (SQL on S3) or Redshift Spectrum

S3 as the data lake: Unlimited storage, schema-on-read, cheap.
```

### Pattern 3: Static Website Hosting

```
S3 bucket configured as website:
  index.html, app.js, styles.css → S3
  CloudFront → S3 (HTTPS, custom domain, edge caching)
  
  React/Vue/Angular SPA: Build → S3 → CloudFront → users globally.
  Zero servers. Infinite scalability. ~$1/month for moderate traffic.
```

### Pattern 4: Backup and Archive

```
Database backups: pg_dump → compress → S3 (Standard, lifecycle to Glacier after 7 days)
Application state: Daily snapshots → S3
Compliance archive: Financial records → S3 Glacier Deep Archive (Object Lock)

With lifecycle:
  Day 0-7: Standard ($0.023/GB)
  Day 7-90: IA ($0.0125/GB)
  Day 90+: Deep Archive ($0.00099/GB)
```

---

## Interview Talking Points & Scripts

### Script 1: S3 Fundamentals

> *"S3 is an object store with 11-nines durability — data is replicated across 3+ AZs within a region. It's a flat key-value store where the key is a string (like a file path) and the value is the object content (up to 5 TB). Since December 2020, S3 is strongly consistent — read-after-write is immediately visible. It provides 5500 GET/sec and 3500 PUT/sec per prefix. For higher throughput: distribute objects across prefixes or put CloudFront in front. S3 is unlimited in capacity — no pre-provisioning needed."*

### Script 2: Presigned URLs

> *"For media uploads, I'd use presigned URLs so clients upload directly to S3, bypassing the application server entirely. The server generates a short-lived presigned PUT URL (15-minute expiry), the client uploads directly to S3 at S3's full bandwidth, and our servers never see the bytes. At 100M uploads/day × 5 MB each: 500 TB that our servers DON'T need to handle. The same pattern works for downloads — presigned GET URLs grant time-limited access to private content without making objects public."*

### Script 3: Storage Tiering

> *"S3 has 7 storage classes optimized for different access patterns. For a media platform: processed images served via CDN stay in Standard (instant access, free retrieval). Raw uploads move to Standard-IA after 1 day (46% cheaper, $0.01/GB retrieval). After 90 days, they go to Glacier Instant Retrieval (83% cheaper). After a year: Deep Archive ($0.001/GB — 96% cheaper than Standard). Lifecycle policies automate these transitions. Result: 70-80% storage cost savings without changing application code."*

### Script 4: S3 Events + Processing Pipeline

> *"S3 event notifications trigger processing when objects are created. User uploads a photo → S3 event → Lambda function (resize to 3 sizes, convert to WebP) → writes processed versions back to S3. I'd use SQS between S3 and Lambda for high-volume scenarios (SQS buffers, Lambda processes in batches). For video: S3 event → SQS → ECS task (long-running transcode). The event notification is the glue — the producer (uploader) is completely decoupled from the processor."*

### Script 5: S3 as Data Lake Foundation

> *"For analytics, S3 is the data lake foundation. Raw events land in S3 partitioned by date (s3://lake/events/year=2024/month=03/day=15/). AWS Glue catalogs the schema. Athena runs SQL directly on S3 (no data loading needed). For large-scale processing: Spark on EMR reads from and writes to S3. The key principle: store data ONCE in S3, query it many ways (Athena for ad-hoc, Redshift Spectrum for BI, SageMaker for ML). S3's durability means I never worry about losing the data."*

---

## Common Interview Mistakes

### ❌ Mistake 1: "S3 is eventually consistent"

**Bad**: "After a PUT, reads might return stale data."
**Fix**: S3 has been strongly consistent since December 2020. Read-after-write is immediate.

### ❌ Mistake 2: Using S3 as a database

**Bad**: "I'll store user profiles as S3 objects and query them."
**Fix**: S3 has no query engine (beyond S3 Select for single objects). Use DynamoDB or RDS for queryable data. S3 is for blobs/files.

### ❌ Mistake 3: Uploading through the app server

**Bad**: "Client sends the file to our API, which writes to S3."
**Fix**: Presigned URLs. Client uploads directly. Zero app server bandwidth.

### ❌ Mistake 4: "S3 has unlimited throughput"

**Bad**: Ignoring the 5500 GET/3500 PUT per prefix limit.
**Fix**: Distribute objects across prefixes for high-throughput workloads. Use CloudFront for read-heavy patterns.

### ❌ Mistake 5: Not mentioning storage classes

**Bad**: "Everything stays in S3 Standard forever."
**Fix**: Lifecycle policies automatically tier data. 80%+ of stored data is rarely accessed — move to IA/Glacier for 70-95% cost savings.

### ❌ Mistake 6: Making buckets public

**Bad**: "I'll make the bucket public for the CDN to access."
**Fix**: Use Origin Access Control (OAC) so only CloudFront can access S3. Never make buckets public. Block Public Access should always be enabled.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────────────┐
│                   AWS S3 DEEP DIVE — CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  WHAT: Object storage. Key-value (key=path, value=blob up to 5 TB). │
│  DURABILITY: 99.999999999% (11 nines). 3+ AZ replication.          │
│  CONSISTENCY: Strong read-after-write (since Dec 2020).             │
│                                                                      │
│  PERFORMANCE:                                                        │
│    5500 GET/sec and 3500 PUT/sec per prefix.                        │
│    Multipart upload: Parallel parts for large files.                │
│    Transfer Acceleration: Edge upload for distant users.            │
│                                                                      │
│  STORAGE CLASSES:                                                    │
│    Standard ($0.023/GB) → IA ($0.0125) → Glacier ($0.004) →        │
│    Deep Archive ($0.001). Lifecycle policies auto-transition.        │
│                                                                      │
│  PRESIGNED URLs: Client uploads/downloads directly to/from S3.      │
│    Zero app server bandwidth. 15-min expiry for security.           │
│                                                                      │
│  EVENTS: s3:ObjectCreated → Lambda/SQS/SNS/EventBridge.            │
│    Trigger processing on upload. Decouple producer from processor.  │
│                                                                      │
│  SECURITY:                                                           │
│    Bucket policies (recommended). Block Public Access (always on).  │
│    Encryption: SSE-S3 (default), SSE-KMS (managed keys), SSE-C.    │
│    CloudFront access: Origin Access Control (OAC).                  │
│                                                                      │
│  VERSIONING: Preserve all versions. Protect against accidental delete│
│  OBJECT LOCK: WORM compliance. Cannot delete until retention expires.│
│                                                                      │
│  PATTERNS:                                                           │
│    Media: Presigned upload → S3 → Event → Lambda → CDN             │
│    Data Lake: Events → S3 → Glue → Athena/Redshift/SageMaker      │
│    Static site: S3 + CloudFront (React/Vue SPA)                     │
│    Backup: DB dump → S3 → Lifecycle → Glacier                       │
│                                                                      │
│  COST: $0.023/GB/month (Standard). $0.005/1K PUTs. $0.0004/1K GETs.│
│    Optimize: Lifecycle policies, CloudFront (free S3→CF transfer),  │
│    compress data, clean up incomplete multipart uploads.            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 18: Blob and Media Storage** — S3 as object store
- **Topic 19: CDN and Edge Caching** — CloudFront with S3 origin
- **Topic 52: Architecting on AWS** — S3 in architectures
- **Topic 58: CDN & Media Pipeline** — S3 in media processing

---

*This document is part of the System Design Interview Deep Dive series.*
