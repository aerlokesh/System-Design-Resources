# System Design Optimization Hacks & Interview Tips

**A curated collection of battle-tested techniques to make your system designs production-ready**

---

## Table of Contents
1. [Performance Optimization Hacks](#performance-optimization-hacks)
2. [Caching Strategies](#caching-strategies)
3. [Database Optimization](#database-optimization)
4. [Scalability Tricks](#scalability-tricks)
5. [Latency Reduction Techniques](#latency-reduction-techniques)
6. [Cost Optimization Hacks](#cost-optimization-hacks)
7. [Reliability & Availability](#reliability--availability)
8. [Network Optimization](#network-optimization)
9. [Storage Optimization](#storage-optimization)
10. [Real-time Systems](#real-time-systems)
11. [Security Best Practices](#security-best-practices)
12. [Monitoring & Debugging](#monitoring--debugging)

---

## Performance Optimization Hacks

### 1. **The "Write-Behind" Cache Pattern**
**What**: Buffer writes in cache, flush asynchronously to database
**When to mention**: Any write-heavy system (social media posts, analytics, logs)
```
User Write → Cache (immediate response) → Background worker → DB
Benefits: 10-100x faster write operations
Trade-off: Risk of data loss if cache crashes before flush
```

### 2. **Request Coalescing**
**What**: Batch multiple identical requests into one
**When to mention**: High-traffic APIs with duplicate requests
```
1000 requests for same data → Deduplicate → 1 DB query → Share result
Example: "If 1000 users refresh dashboard simultaneously, I'd coalesce to 1 DB query"
Savings: 99.9% reduction in database load
```

### 3. **The Thundering Herd Solution**
**What**: Use distributed locks to prevent cache stampede
**When to mention**: Discussing cache invalidation
```
Problem: Cache expires → 10,000 simultaneous DB queries
Solution: First requester gets lock, others wait for cache rebuild
Use Redis SETNX or Redlock algorithm
```

### 4. **Pre-computed Materialized Views**
**What**: Pre-calculate expensive queries, store results
**When to mention**: Analytics dashboards, reporting systems
```
Don't: SELECT with 10 JOINs on every request
Do: Cron job pre-computes at 2 AM → Store in Redis → Serve instantly
Example: "For trending posts, I'd compute hourly, not real-time"
```

### 5. **Hot-Warm-Cold Data Tiering**
**What**: Separate data by access frequency
**When to mention**: Any large-scale storage system
```
Hot (Redis): Last 24 hours, <10ms access
Warm (SSD): Last 30 days, <100ms access
Cold (S3 Glacier): 90+ days, minutes to hours
Cost savings: 95% compared to keeping all data hot
```

---

## Caching Strategies

### 6. **The "Cache-Aside" Pattern**
**What**: Application manages cache explicitly
**When to mention**: Most caching scenarios
```
1. Check cache
2. If miss, fetch from DB
3. Store in cache for next request
4. Return data
```

### 7. **Multi-Level Caching**
**What**: Layer caches for maximum hit ratio
**When to mention**: Global applications, CDN discussions
```
L1: Browser cache (100ms)
L2: CDN edge (50ms)  
L3: Application cache (10ms)
L4: Database query cache (5ms)
Combined hit ratio: 99.5%+
```

### 8. **Probabilistic Cache Warming**
**What**: Pre-populate cache before TTL expires
**When to mention**: Preventing cache miss storms
```
TTL = 60 minutes
Start warming at 55 minutes for high-traffic keys
Use probabilistic early expiration: if rand() < (elapsed/TTL) → refresh
```

### 9. **Negative Caching**
**What**: Cache "not found" results to prevent repeated DB queries
**When to mention**: Protection against malicious requests
```
Example: Cache "user_id_999999 not found" for 5 minutes
Prevents attackers from hammering DB with non-existent user lookups
```

### 10. **Cache Versioning**
**What**: Include version in cache key to avoid stale data
**When to mention**: Code deployment scenarios
```
Key format: user:v2:12345 instead of user:12345
Deploy new code → Use v3 → Old cache automatically ignored
No need for invalidation
```

---

## Database Optimization

### 11. **Read Replicas with Eventual Consistency**
**What**: Route reads to replicas, writes to primary
**When to mention**: Read-heavy workloads (social media feeds)
```
Primary (writes): 1 instance
Replicas (reads): 5-10 instances
Read scaling: 10x capacity
Lag tolerance: 100-500ms acceptable for feeds
```

### 12. **Database Sharding by User ID**
**What**: Partition data across multiple databases
**When to mention**: Multi-tenant applications
```
Shard key: user_id % num_shards
Benefits: Linear scaling, isolated failures
Example: "For Instagram, I'd shard by user_id for photos table"
```

### 13. **Connection Pooling**
**What**: Reuse database connections
**When to mention**: High-throughput applications
```
Problem: Creating connection = 50-100ms overhead
Solution: Pool of 100 connections, reuse across requests
Throughput increase: 10x+
```

### 14. **Denormalization for Read Performance**
**What**: Duplicate data to avoid JOINs
**When to mention**: Read-optimized systems
```
Instead of: Posts + Users + Comments (3 JOINs)
Store: Posts with embedded user info
Trade-off: 2x storage, 10x faster queries
When: Read:Write ratio > 100:1
```

### 15. **The "Partition by Time" Trick**
**What**: Split tables by date ranges
**When to mention**: Time-series data, logs, events
```
logs_2025_01, logs_2025_02, logs_2025_03
Query only recent partition → 100x faster
Drop old partitions easily → No DELETE overhead
```

### 16. **Database Query Result Pagination**
**What**: Cursor-based pagination instead of OFFSET
**When to mention**: Infinite scroll, large result sets
```
Bad: SELECT * FROM posts OFFSET 1000000 LIMIT 20 (slow!)
Good: SELECT * FROM posts WHERE id > last_seen_id LIMIT 20
Always fast, regardless of page number
```

---

## Scalability Tricks

### 17. **Consistent Hashing for Load Distribution**
**What**: Distribute load evenly, minimize redistribution
**When to mention**: Distributed caches, distributed databases
```
Use case: Redis cluster with 10 nodes
Add node → Only 10% keys redistributed (not 50%)
Remove node → Smooth failover
```

### 18. **Auto-Scaling with Predictive Rules**
**What**: Scale based on predictions, not just current load
**When to mention**: Traffic patterns discussion
```
Standard: Scale when CPU > 80%
Better: Scale at 7 AM (before morning traffic spike)
Use ML to predict: "Friday 5 PM = 3x traffic"
```

### 19. **Service Mesh for Microservices**
**What**: Sidecar proxies for resilience
**When to mention**: Microservices architecture
```
Features: Circuit breaking, retries, timeouts, load balancing
Example: "I'd use Istio to automatically retry failed requests"
Benefit: Resilience without code changes
```

### 20. **The "Cell-Based" Architecture**
**What**: Partition infrastructure into isolated cells
**When to mention**: Blast radius limitation
```
Instead of: 1 shared database for all users
Do: 100 cells, each serving 1% of users
Failure affects: 1% instead of 100%
AWS uses this for Route53, S3
```

---

## Latency Reduction Techniques

### 21. **Request Collapsing (Batching)**
**What**: Combine multiple API calls into one
**When to mention**: Frontend optimization, GraphQL
```
Instead of: 10 API calls for user profile
Do: 1 API call with all fields
Latency: 1s → 100ms (10x improvement)
```

### 22. **Prefetching & Predictive Loading**
**What**: Load data before user needs it
**When to mention**: UI/UX optimization
```
Example: "When user hovers over profile pic, prefetch full profile"
Video streaming: Buffer next 30 seconds
Result: Zero perceived latency
```

### 23. **Edge Computing / Lambda@Edge**
**What**: Run logic at CDN edge locations
**When to mention**: Global applications
```
Use cases: Auth checks, A/B testing, URL rewrites
Latency: 200ms → 20ms (10x faster)
Example: CloudFlare Workers, Lambda@Edge
```

### 24. **HTTP/2 & HTTP/3 Multiplexing**
**What**: Multiple requests over single connection
**When to mention**: Web applications
```
HTTP/1.1: 6 parallel connections → Head-of-line blocking
HTTP/2: Unlimited requests, single connection
HTTP/3: QUIC protocol → 0-RTT connection resumption
```

### 25. **Protocol Buffers over JSON**
**What**: Binary serialization for faster parsing
**When to mention**: Mobile apps, microservices
```
JSON: 1 KB, 50ms parsing
Protobuf: 200 bytes, 5ms parsing
When: Internal services, mobile-backend communication
```

---

## Cost Optimization Hacks

### 26. **Spot Instances for Batch Processing**
**What**: Use cheap preemptible VMs for non-critical workloads
**When to mention**: ML training, video encoding, ETL
```
Savings: 70-90% vs on-demand instances
Example: "For nightly analytics, I'd use spot instances"
Fault tolerance: Design for interruptions
```

### 27. **Object Storage Lifecycle Policies**
**What**: Auto-move old data to cheaper storage
**When to mention**: Large file storage systems
```
Day 0-30: S3 Standard ($0.023/GB)
Day 31-90: S3 Infrequent Access ($0.0125/GB)
Day 91+: S3 Glacier ($0.004/GB)
Savings: 80% for old data
```

### 28. **Reserved Instances / Savings Plans**
**What**: Commit to 1-3 year terms for discounts
**When to mention**: Cost-conscious designs
```
Savings: 30-70% vs on-demand
Example: "Baseline capacity on reserved, peaks on-demand"
```

### 29. **Compression Everywhere**
**What**: Compress data in transit and at rest
**When to mention**: Bandwidth/storage discussions
```
API responses: Gzip (70% reduction)
Database: Column compression (50% reduction)
Logs: Zstd (80% reduction)
Total savings: 50-80% on storage and bandwidth
```

### 30. **The "Lazy Deletion" Pattern**
**What**: Mark as deleted, actually delete later in batch
**When to mention**: High-throughput write systems
```
DELETE operation: 100ms (slow, blocks)
UPDATE deleted=true: 5ms (fast)
Batch cleanup: Nightly cron job
Benefit: 20x faster deletes
```

---

## Reliability & Availability

### 31. **Circuit Breaker Pattern**
**What**: Stop calling failing services automatically
**When to mention**: Microservices, external APIs
```
States: Closed (normal) → Open (failing) → Half-Open (testing)
Example: "If payment API fails 5 times, I'd open circuit for 30s"
Prevents: Cascading failures
```

### 32. **Bulkhead Pattern (Resource Isolation)**
**What**: Separate thread pools for different operations
**When to mention**: Preventing resource exhaustion
```
100 threads total:
- 70 for fast operations
- 20 for slow operations  
- 10 for admin operations
Slow ops can't starve fast ops
```

### 33. **Chaos Engineering / Fault Injection**
**What**: Intentionally break things to test resilience
**When to mention**: Production readiness
```
Example: "I'd use Netflix's Chaos Monkey to randomly kill instances"
Tests: How system handles failures
Build confidence: If it survives chaos, it's resilient
```

### 34. **Blue-Green Deployments**
**What**: Run two identical production environments
**When to mention**: Zero-downtime deployments
```
Blue: Current version (live)
Green: New version (standby)
Switch: DNS/Load balancer flip (instant)
Rollback: Switch back if issues
```

### 35. **Database Write-Ahead Logging (WAL)**
**What**: Log changes before applying them
**When to mention**: Data durability discussions
```
Crash recovery: Replay WAL to restore state
Benefits: ACID compliance, point-in-time recovery
Example: PostgreSQL, MySQL InnoDB
```

---

## Network Optimization

### 36. **CDN for Static Assets**
**What**: Serve files from edge locations
**When to mention**: Any web application
```
Origin: 500ms (from US to India)
CDN: 20ms (from India edge)
Cost: 90% cheaper bandwidth
Example: "I'd put images, JS, CSS on CloudFront"
```

### 37. **TCP Fast Open (TFO)**
**What**: Send data in SYN packet
**When to mention**: Latency-sensitive applications
```
Standard TCP: 3-way handshake (1 RTT) + data
TFO: Data in first SYN packet (0 RTT)
Latency reduction: 100-200ms
```

### 38. **Connection Keepalive & Pooling**
**What**: Reuse TCP connections
**When to mention**: High-frequency API calls
```
New connection: 100ms overhead (TCP + TLS)
Reused connection: 1ms overhead
Example: HTTP/1.1 Keep-Alive, connection pools
```

### 39. **DNS Prefetching**
**What**: Resolve DNS before user clicks
**When to mention**: Web performance
```
HTML: <link rel="dns-prefetch" href="//api.example.com">
Saves: 100-300ms on first request
```

### 40. **Anycast Routing**
**What**: Route to nearest server automatically
**When to mention**: Global services
```
Single IP, multiple locations → Network routes to closest
Use case: DDoS protection, CDN
Example: CloudFlare uses anycast for all IPs
```

---

## Storage Optimization

### 41. **Content-Addressable Storage**
**What**: Use hash as filename to deduplicate
**When to mention**: File storage systems
```
File hash = SHA256(content)
Same file uploaded twice → Stored once
Savings: 30-50% for duplicate content
Example: Git, Docker images
```

### 42. **Block-Level Deduplication**
**What**: Store unique blocks only once
**When to mention**: Backup systems, VM storage
```
Split files into 4KB blocks
Hash each block → Store unique blocks only
Savings: 10x for backups (incremental changes)
```

### 43. **Delta Encoding**
**What**: Store only differences from previous version
**When to mention**: Version control, sync systems
```
Version 1: 1 MB
Version 2: Base + 10 KB delta
Storage: 1.01 MB instead of 2 MB
Example: Google Docs, Git diffs
```

### 44. **Erasure Coding vs Replication**
**What**: Mathematical redundancy instead of copying
**When to mention**: Large-scale storage cost optimization
```
3x replication: 1 GB → 3 GB (300% overhead)
Erasure coding (6+3): 1 GB → 1.5 GB (150% overhead)
Savings: 50% storage cost
Example: Azure, Facebook
```

### 45. **Write-Optimized Storage (LSM Trees)**
**What**: Structure optimized for sequential writes
**When to mention**: Write-heavy workloads (logs, time-series)
```
Use case: Kafka, Cassandra, RocksDB
Throughput: 10x faster writes than B-trees
Trade-off: Slightly slower reads
```

---

## Real-time Systems

### 46. **WebSocket Connection Pooling**
**What**: Reuse connections across services
**When to mention**: Chat, live updates, gaming
```
Problem: 1M connections = 1M file descriptors
Solution: Connection server pools → Backend services
Scaling: Handle millions of connections with fewer servers
```

### 47. **Server-Sent Events (SSE) for One-Way Updates**
**What**: HTTP-based streaming, simpler than WebSocket
**When to mention**: Live feeds, notifications
```
Use case: Stock prices, sports scores, news feeds
Benefits: Auto-reconnect, HTTP/2 compatible
When not: Two-way communication needed
```

### 48. **Message Queue for Decoupling**
**What**: Async communication via queue
**When to mention**: Event-driven architectures
```
Producer → Kafka → Consumer
Benefits: Decoupling, buffering, replay capability
Example: "Order service publishes, inventory subscribes"
```

### 49. **Long Polling vs Server Push**
**What**: Alternatives to WebSocket
**When to mention**: Real-time without WebSocket infrastructure
```
Long Polling: Client holds connection until data available
Server Push: Server pushes data when ready
Use when: Can't use WebSocket (firewall restrictions)
```

### 50. **Change Data Capture (CDC)**
**What**: Stream database changes as events
**When to mention**: Data synchronization, caching
```
Tool: Debezium watches database transaction log
Use case: Update cache when DB changes
Benefits: Real-time, no polling overhead
```

---

## Security Best Practices

### 51. **Rate Limiting with Token Bucket**
**What**: Limit requests per user/IP
**When to mention**: API design, DDoS protection
```
Algorithm: Refill X tokens/second, bucket size Y
Example: 100 req/min → 1.67 tokens/sec, bucket=100
Burst allowance: Can use all 100 at once, then throttle
```

### 52. **JWT with Short Expiry + Refresh Tokens**
**What**: Balance security and UX
**When to mention**: Authentication design
```
Access token: 15 minutes (JWT)
Refresh token: 30 days (stored securely)
Compromise: Short-lived exposure
```

### 53. **API Gateway for Security Layer**
**What**: Centralized auth, rate limiting, validation
**When to mention**: Microservices security
```
Features: Auth, rate limiting, request validation, DDoS protection
Example: "API Gateway handles auth before reaching services"
Benefit: Security logic in one place
```

### 54. **Encryption at Rest & in Transit**
**What**: Protect data everywhere
**When to mention**: Compliance, sensitive data
```
In transit: TLS 1.3 (all API calls)
At rest: AES-256 (database, S3)
Keys: AWS KMS, HashiCorp Vault
Example: "I'd enable S3 encryption and RDS encryption"
```

### 55. **Content Security Policy (CSP)**
**What**: Prevent XSS attacks
**When to mention**: Web application security
```
Header: Content-Security-Policy: script-src 'self'
Blocks: Inline scripts, external malicious scripts
Example: "To prevent XSS, I'd use strict CSP headers"
```

---

## Monitoring & Debugging

### 56. **Distributed Tracing**
**What**: Track requests across microservices
**When to mention**: Debugging microservices
```
Tool: Jaeger, Zipkin
Trace ID: Passed through all services
Example: "For slow API, I'd trace which service is bottleneck"
```

### 57. **The RED Method (Metrics)**
**What**: Rate, Errors, Duration
**When to mention**: What metrics to track
```
Rate: Requests per second
Errors: Error rate (%)
Duration: Latency (P50, P95, P99)
Example: "I'd alert if P99 latency > 1s or error rate > 1%"
```

### 58. **Structured Logging with Correlation IDs**
**What**: JSON logs with request tracking
**When to mention**: Debugging distributed systems
```
{ "timestamp": "...", "level": "ERROR", "requestId": "123", "service": "api" }
Benefit: Grep by requestId across all services
Tool: ELK stack, Splunk
```

### 59. **Health Check Endpoints**
**What**: /health endpoint for monitoring
**When to mention**: Load balancer configuration
```
GET /health → 200 OK (healthy) or 500 (unhealthy)
Checks: Database connection, cache connection, disk space
Example: "Load balancer removes unhealthy instances"
```

### 60. **Canary Deployments**
**What**: Roll out to small percentage first
**When to mention**: Risk mitigation
```
Deploy to 5% of servers first
Monitor: Error rate, latency, custom metrics
If good: Roll out to 100%
If bad: Rollback
```

---

## Bonus: Advanced Hacks

### 61. **Bloom Filters for Existence Checks**
**What**: Probabilistic data structure, space-efficient
**When to mention**: "Does X exist?" queries
```
Use case: Check if username exists before DB query
Size: 1 MB Bloom filter vs 100 MB user list
Trade-off: False positives possible (1%), no false negatives
```

### 62. **HyperLogLog for Cardinality**
**What**: Count unique items with tiny memory
**When to mention**: Analytics, unique visitor counts
```
Count 1 billion unique users in 12 KB memory
Accuracy: 2% error margin
Use case: Daily active users, unique views
```

### 63. **CQRS (Command Query Responsibility Segregation)**
**What**: Separate read and write models
**When to mention**: Complex domains with different read/write patterns
```
Write model: Normalized for consistency
Read model: Denormalized for speed
Sync: Event sourcing, CDC
```

### 64. **Event Sourcing**
**What**: Store events, not current state
**When to mention**: Audit trails, time-travel debugging
```
Instead of: user.balance = 100
Store: [+50, +30, +20] events
Benefits: Full history, replay capability
Use case: Banking, order management
```

### 65. **GraphQL DataLoader (N+1 Problem)**
**What**: Batch and cache database requests
**When to mention**: GraphQL API optimization
```
Problem: Fetching 100 users → 100 DB queries
Solution: DataLoader batches → 1 query
Example: "I'd use DataLoader to batch user fetches"
```

---

## How to Use These in Interviews

### Interview Framework:

1. **State the Problem**
   - "At scale, this would cause X problem"

2. **Mention the Hack**
   - "In production, I'd use [specific technique]"

3. **Quantify the Benefit**
   - "This reduces latency from X to Y" or "Saves Z% cost"

4. **Show Trade-offs**
   - "The trade-off is [downside], which is acceptable because [reason]"

### Example Interview Response:

**Interviewer**: "How would you design Instagram's feed?"

**You**: 
> "For the feed, I'd use a **fanout-on-write** approach where we pre-compute feeds. When a user posts, we'd push to followers' feeds asynchronously via Kafka.
>
> To optimize, I'd use **Redis sorted sets** for the feed with scores as timestamps - this gives O(log N) insertion and range queries.
>
> For popular users with millions of followers, I'd switch to **fanout-on-read** to avoid the write amplification problem - that's the trade-off.
>
> I'd also implement **multi-level caching**: L1 in-memory for hot users, L2 in Redis for warm users, and fall back to Cassandra for cold storage.
>
> This architecture at Instagram scale would handle 500M daily active users with P99 latency under 100ms, which I know from seeing similar patterns at [previous company]."

### Red Flags to Avoid:

❌ "I'd just use AWS for everything"
✅ "I'd use S3 for object storage because it provides 11 9s durability and automatic scaling"

❌ "Add caching"
✅ "I'd add Redis with a cache-aside pattern, 5-minute TTL for hot data, and LRU eviction"

❌ "Use a database"
✅ "I'd use PostgreSQL with read replicas for this read-heavy workload, sharded by user_id for horizontal scaling"

---

## Quick Reference: When to Mention What

| Scenario | Hacks to Mention |
|----------|------------------|
| **Social Media Feed** | Fanout-on-write, Redis sorted sets, Read replicas, Sharding by user_id |
| **Search Engine** | Inverted index, Elasticsearch, Distributed crawling, TF-IDF ranking |
| **Video Streaming** | CDN, Adaptive bitrate, Chunked encoding, Prefetching |
| **Chat Application** | WebSocket, Message queue, Presence service, Eventual consistency |
| **E-commerce** | CQRS, Event sourcing, Inventory locks, Payment idempotency |
| **Analytics Dashboard** | Pre-computed views, OLAP databases, Data warehouse, Batch processing |
| **Uber/Maps** | Geohashing, QuadTree, H3 indexing, Real-time location updates |
| **URL Shortener** | Base62 encoding, Consistent hashing, Bloom filter for collisions |
| **Rate Limiter** | Token bucket, Sliding window, Distributed rate limiting (Redis) |
| **Notification System** | Fan-out service, Priority queues, Circuit breaker, Exponential backoff |

---

## Final Interview Tips

1. **Always quantify**: "10x faster", "50% cost reduction", "99.9% uptime"
2. **Show trade-offs**: Every decision has pros/cons
3. **Reference real companies**: "Similar to how Netflix does X"
4. **Start simple, then optimize**: "Version 1 would be X, but at scale I'd use Y"
5. **Ask clarifying questions**: "What's the read/write ratio?" "What's the latency requirement?"

Remember: Interviewers want to see **how you think** and that you've **solved real problems** at scale. These hacks prove you understand production systems.

---

## Further Reading

- Martin Kleppmann: "Designing Data-Intensive Applications"
- Google SRE Book: Site Reliability Engineering
- AWS Well-Architected Framework
- High Scalability Blog: http://highscalability.com
- System Design Primer: https://github.com/donnemartin/system-design-primer

---

**Last Updated**: November 2025

**Note**: These are guidelines, not rules. Always adapt to the specific requirements of your system design problem.
