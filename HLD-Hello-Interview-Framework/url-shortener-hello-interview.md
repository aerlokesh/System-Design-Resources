# URL Shortener System Design - Hello Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [High-Level Design](#5️⃣-high-level-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **URL Shortening**
   - Convert long URL to short URL
   - Generate unique short codes (no collisions)
   - Short code should be as short as possible (6-7 characters)
   - Default expiration: 10 years

2. **URL Redirection**
   - Redirect short URL to original long URL
   - Support HTTP 301 (permanent) and 302 (temporary) redirects
   - Handle invalid/expired URLs gracefully

3. **Custom Aliases**
   - User can specify custom short URLs (vanity URLs)
   - Check alias availability
   - Example: bit.ly/my-company

4. **Analytics Tracking**
   - Track click count per URL
   - Track timestamp of clicks
   - Geographic location
   - Referrer information
   - Device type (mobile/desktop)

#### Nice to Have (P1)
- Custom expiration dates
- User accounts and dashboard
- QR code generation
- Link preview (Open Graph metadata)
- Branded domains (custom.domain.com/abc)
- Link editing (change destination)
- Password-protected URLs
- A/B testing different destinations

### Non-Functional Requirements

#### Scalability
- **URL creations**: 100 million per month (~40 QPS)
- **Redirects**: 10 billion per month (~4K QPS)
- **Read-to-write ratio**: 100:1 (read-heavy)
- **Storage**: Support billions of URLs

#### Performance
- **Redirect latency**: < 100ms (p99)
- **URL creation**: < 200ms
- **Cache hit ratio**: > 80%

#### Availability
- **Uptime**: 99.99% (4 nines)
- Multi-region deployment
- No single point of failure

#### Reliability
- **URL durability**: Never break (unless expired)
- **Data durability**: 99.999999999% (11 nines)
- Handle traffic spikes (viral links)

#### Security
- Validate URLs (prevent malicious sites)
- Rate limiting (prevent abuse)
- CAPTCHA for suspicious activity
- Blacklist spam domains

### Capacity Estimation

#### Traffic Estimates
```
URL Shortening (Writes):
  100M/month = 100M / 2.6M seconds ≈ 40 QPS
  Peak (3x): ~120 QPS

URL Redirection (Reads):
  10B/month = 10B / 2.6M seconds ≈ 3,850 QPS
  Peak (3x): ~11,500 QPS

Read:Write Ratio: 3,850 / 40 = 96:1 ≈ 100:1
```

#### Storage Estimates
```
URLs created: 100M/month
Lifetime: 10 years
Total URLs: 100M × 12 × 10 = 12 billion URLs

Storage per URL:
  - Long URL: 200 bytes
  - Short code: 7 bytes
  - Metadata: 18 bytes (timestamp, user_id, etc.)
  - Total: ~225 bytes per URL

Total Storage: 12B × 225 bytes = 2.7 TB

With Analytics (10 clicks per URL average):
  12B URLs × 10 clicks × 50 bytes = 6 TB

Total: ~9 TB for 10 years (very manageable)
```

#### Bandwidth Estimates
```
Writes: 40 QPS × 225 bytes = 9 KB/s
Reads: 3,850 QPS × 225 bytes = 866 KB/s

Total: < 1 MB/s (negligible bandwidth)
```

#### Memory Estimates (Caching)
```
80-20 rule: 20% of URLs generate 80% of traffic

Cache 20% of URLs:
  12B × 0.2 = 2.4B URLs
  2.4B × 225 bytes = 540 GB

Distributed across 10 Redis servers:
  540 GB / 10 = 54 GB per server

With buffer: ~100 GB per server
```

---

## 2️⃣ Core Entities

### Entity 1: URL Mapping
**Purpose**: Maps short code to original long URL

**Schema**:
```sql
URLs Table (PostgreSQL/MySQL):
- id (PK, BIGSERIAL)
- short_code (unique, indexed, varchar 10)
- long_url (varchar 2048)
- user_id (bigint, nullable)
- created_at (timestamp)
- expires_at (timestamp, nullable)
- is_custom (boolean, default false)
- click_count (bigint, default 0)

Indexes:
- PRIMARY KEY (id)
- UNIQUE INDEX (short_code)
- INDEX (user_id, created_at DESC)
- INDEX (expires_at) WHERE expires_at IS NOT NULL
```

**Relationships**:
- Belongs to: User (optional)
- Has many: Click Analytics

### Entity 2: Click Analytics
**Purpose**: Track click events for analytics

**Schema**:
```cql
Clicks Table (Cassandra):
- short_code (partition key)
- clicked_at (clustering key, timestamp)
- click_id (timeuuid)
- ip_address (text)
- country (text)
- city (text)
- referrer (text)
- user_agent (text)
- device_type (text: mobile/desktop/tablet)

PRIMARY KEY ((short_code), clicked_at, click_id)
WITH CLUSTERING ORDER BY (clicked_at DESC)
```

**Relationships**:
- Belongs to: URL Mapping

**Alternative**: ClickHouse (OLAP database for analytics)

### Entity 3: User (Optional)
**Purpose**: Registered users who can manage URLs

**Schema**:
```sql
Users Table:
- user_id (PK, bigserial)
- email (unique, varchar 255)
- password_hash (varchar 255)
- api_key (unique, varchar 64)
- created_at (timestamp)
- subscription_tier (enum: free, premium)

Indexes:
- PRIMARY KEY (user_id)
- UNIQUE INDEX (email)
- UNIQUE INDEX (api_key)
```

**Relationships**:
- Has many: URL Mappings

### Entity 4: Short Code Counter (Redis)
**Purpose**: Generate unique sequential IDs for Base62 encoding

**Redis Structure**:
```
Key: url_counter
Type: Integer
Value: Current counter value

Commands:
INCR url_counter  → Returns next unique number
GET url_counter   → Get current value

Persistence: AOF enabled for durability
```

**Alternative**: Zookeeper for distributed counter

---

## 3️⃣ API Design

### Authentication Header
```
Authorization: Bearer <JWT_TOKEN>
or
X-API-Key: <api_key>
Content-Type: application/json
```

### URL Shortening APIs

#### 1. Shorten URL
```
POST /api/v1/shorten

Request:
{
  "long_url": "https://www.example.com/very/long/url/here",
  "custom_alias": "my-link",        // Optional
  "expires_at": "2026-01-08T00:00:00Z"  // Optional
}

Response (201 Created):
{
  "success": true,
  "data": {
    "short_url": "https://short.link/a3X9z",
    "short_code": "a3X9z",
    "long_url": "https://www.example.com/very/long/url/here",
    "created_at": "2025-01-08T14:00:00Z",
    "expires_at": "2035-01-08T14:00:00Z"
  }
}

Error Response (custom alias taken):
{
  "success": false,
  "error": {
    "code": "ALIAS_TAKEN",
    "message": "Custom alias 'my-link' is already taken"
  }
}
```

#### 2. Check Alias Availability
```
GET /api/v1/available/{alias}

Response (200 OK):
{
  "success": true,
  "data": {
    "alias": "my-link",
    "available": true
  }
}
```

#### 3. Get URL Info
```
GET /api/v1/urls/{short_code}

Response (200 OK):
{
  "success": true,
  "data": {
    "short_code": "a3X9z",
    "long_url": "https://www.example.com/...",
    "created_at": "2025-01-08T14:00:00Z",
    "expires_at": "2035-01-08T14:00:00Z",
    "total_clicks": 1523,
    "is_active": true
  }
}
```

### URL Redirection

#### 4. Redirect to Original URL
```
GET /{short_code}

Response (302 Found):
Location: https://www.example.com/very/long/url/here

Response Headers:
HTTP/1.1 302 Found
Location: https://www.example.com/very/long/url/here
Cache-Control: no-cache, no-store, must-revalidate

Error Response (404 Not Found):
HTTP/1.1 404 Not Found
Content-Type: text/html

<html>
  <body>
    <h1>URL Not Found</h1>
    <p>This short link doesn't exist or has expired.</p>
  </body>
</html>
```

### Analytics APIs

#### 5. Get Analytics
```
GET /api/v1/analytics/{short_code}

Query Parameters:
- start_date: ISO date (optional)
- end_date: ISO date (optional)
- group_by: day|week|month (optional)

Response (200 OK):
{
  "success": true,
  "data": {
    "short_code": "a3X9z",
    "total_clicks": 1523,
    "unique_clicks": 892,
    "clicks_by_date": [
      {"date": "2025-01-08", "count": 342},
      {"date": "2025-01-07", "count": 581}
    ],
    "clicks_by_country": [
      {"country": "US", "count": 812, "percentage": 53.3},
      {"country": "UK", "count": 231, "percentage": 15.2}
    ],
    "clicks_by_device": [
      {"device": "mobile", "count": 923, "percentage": 60.6},
      {"device": "desktop", "count": 600, "percentage": 39.4}
    ],
    "top_referrers": [
      {"referrer": "twitter.com", "count": 450},
      {"referrer": "facebook.com", "count": 380}
    ]
  }
}
```

### User Management APIs (if accounts enabled)

#### 6. Register User
```
POST /api/v1/users/register

Request:
{
  "email": "alice@example.com",
  "password": "securepass123"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "user_id": 123,
    "email": "alice@example.com",
    "api_key": "sk_abc123xyz",
    "created_at": "2025-01-08T14:00:00Z"
  }
}
```

#### 7. Get User URLs
```
GET /api/v1/users/me/urls?page=1&limit=20

Response (200 OK):
{
  "success": true,
  "data": {
    "urls": [
      {
        "short_code": "a3X9z",
        "long_url": "https://...",
        "clicks": 1523,
        "created_at": "2025-01-01T10:00:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 145
    }
  }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Shorten URL
```
1. User enters long URL in web interface
   ↓
2. Frontend calls: POST /api/v1/shorten
   ↓
3. API Gateway:
   - Checks rate limit (10/hour for anonymous)
   - Validates authentication (if required)
   ↓
4. URL Service:
   - Validates URL format (regex)
   - Checks URL safety (Google Safe Browsing API)
   - Checks if custom alias provided
   ↓
5a. Custom Alias Flow:
    - Check if alias available (DB query)
    - If taken: Return error
    - If available: Use custom alias
    ↓
5b. Auto-Generate Flow:
    - Increment counter in Redis: INCR url_counter
    - Convert counter to Base62: encode_base62(123456)
    - Result: short_code = "w7e"
    ↓
6. Store in PostgreSQL:
   INSERT INTO urls (short_code, long_url, created_at, expires_at)
   VALUES ('w7e', 'https://...', NOW(), NOW() + INTERVAL '10 years')
   ↓
7. Return short URL to user
   - Short URL: https://short.link/w7e
   - Total time: < 200ms
   ↓
8. Background tasks (async):
   - Generate QR code (if premium user)
   - Fetch link preview metadata
   - Update search index
```

### Flow 2: Redirect Short URL (Cache Hit)
```
1. User clicks: https://short.link/a3X9z
   ↓
2. DNS resolves to nearest datacenter
   ↓
3. Load Balancer → Application Server
   ↓
4. Application Server checks Redis cache:
   - Key: "url:a3X9z"
   - Cache HIT (80% of requests)
   - Retrieve long_url from cache
   - Time: < 10ms
   ↓
5. Async (non-blocking): Log click to Kafka
   {
     "short_code": "a3X9z",
     "timestamp": 1704672000,
     "ip": "192.168.1.1",
     "user_agent": "Mozilla/5.0...",
     "referrer": "twitter.com"
   }
   ↓
6. Return HTTP 302 redirect:
   Location: https://www.example.com/original/url
   ↓
7. Browser redirects to original URL
   ↓
8. Total time: 10-20ms

Background Processing (Kafka consumer):
9. Analytics worker batches 1000 events
   ↓
10. Write batch to Cassandra
    ↓
11. Update Redis counter: INCR clicks:a3X9z
    ↓
12. Pre-compute daily aggregates
```

### Flow 3: Redirect Short URL (Cache Miss)
```
1. User clicks: https://short.link/x9K2m
   ↓
2. Load Balancer → Application Server
   ↓
3. Check Redis cache: Cache MISS (20% of requests)
   ↓
4. Query PostgreSQL:
   SELECT long_url, expires_at 
   FROM urls 
   WHERE short_code = 'x9K2m'
   ↓
5. Database returns result (10-30ms)
   ↓
6. Check if expired:
   - If expires_at < NOW(): Return 410 Gone
   - If valid: Proceed
   ↓
7. Store in Redis cache (24 hour TTL):
   SETEX url:x9K2m 86400 "https://example.com/..."
   ↓
8. Log click event to Kafka (async)
   ↓
9. Return HTTP 302 redirect
   ↓
10. Total time: 50-100ms
```

### Flow 4: Get Analytics
```
1. User requests analytics dashboard
   ↓
2. API call: GET /api/v1/analytics/a3X9z
   ↓
3. Analytics Service:
   - Check if user owns URL (if auth required)
   - Query pre-computed aggregates from Redis
   ↓
4a. Recent data (last 7 days) from Redis:
    - Daily click counts: hash structure
    - Country distribution: sorted set
    - Referrer stats: hash structure
    - Time: < 50ms
    ↓
4b. Historical data from Cassandra:
    - Query click_events table
    - Time range filter
    - Aggregate on-the-fly
    - Time: 100-500ms
    ↓
5. Merge real-time and historical data
   ↓
6. Return analytics JSON
   ↓
7. Frontend renders charts/graphs
```

### Flow 5: Custom Alias Creation
```
1. User requests custom alias: "my-company-2025"
   ↓
2. POST /api/v1/shorten with custom_alias
   ↓
3. URL Service:
   - Validate alias format:
     * Length: 4-20 characters
     * Characters: a-z, A-Z, 0-9, hyphen
     * Not reserved: (api, admin, dashboard)
   ↓
4. Check availability (DB query):
   SELECT 1 FROM urls WHERE short_code = 'my-company-2025'
   ↓
5a. If exists: Return 409 Conflict
    "Alias already taken"
    ↓
5b. If available:
    - Insert with custom short_code
    - Mark is_custom = true
    - Return success
    ↓
6. User gets: https://short.link/my-company-2025
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
                        [Users - Web/Mobile/API]
                                    |
                                    ↓
                        [DNS - Route53/CloudFlare]
                        Geographic routing
                                    |
                                    ↓
                        [CDN - CloudFront (Optional)]
                        Static assets, redirect pages
                                    |
                                    ↓
                [Application Load Balancer (ALB)]
                SSL termination, Health checks
                                    |
                        ┌───────────┴───────────┐
                        ↓                       ↓
                [API Gateway]           [Web Servers]
                Rate limiting           Static pages
                        |                       |
                        └───────────┬───────────┘
                                    |
                    ┌───────────────┴───────────────┐
                    ↓                               ↓
            [Write API]                      [Read API]
            URL Shortening                   URL Redirection
            40 QPS                           4K QPS
                    |                               |
                    |                               ↓
                    |                    [Cache - Redis Cluster]
                    |                    • Hot URLs (80% hits)
                    |                    • 540 GB distributed
                    |                    • 10 nodes × 54GB
                    |                               |
                    └───────────────┬───────────────┘
                                    |
                        [Application Servers]
                        Stateless, Auto-scaling
                        Node.js/Python/Go
                                    |
                        ┌───────────┴───────────┐
                        ↓                       ↓
                [URL Service]          [Analytics Service]
                - Generate codes       - Track clicks
                - Validate URLs        - Aggregate data
                - Store mappings       - Generate reports
                        |                       |
                    ┌───┴───────────────────────┴───┐
                    ↓                               ↓
            [Primary Database]              [Analytics Database]
            PostgreSQL/MySQL                Cassandra/ClickHouse
            • URLs table                    • Click events
            • Users table                   • Time-series data
            • 9TB total                     • Billions of rows
            Master + 5 replicas             Write-optimized
            Sharded by short_code
                    |
                    ↓
            [Message Queue - Kafka]
            • Click events
            • Async processing
            • Buffer traffic spikes
                    |
                    ↓
            [Background Workers]
            • Analytics processor
            • Link preview fetcher
            • QR code generator
                    |
                    ↓
            [Object Storage - S3]
            • QR codes
            • Database backups
            • Exported analytics
```

### Component Details

#### 1. Load Balancer (ALB)
**Purpose**: Distribute traffic across servers

**Configuration**:
- Round-robin distribution
- Health checks every 10 seconds
- SSL/TLS termination
- Sticky sessions: Not needed (stateless)

**Capacity**: 50K requests/second per LB

#### 2. API Gateway
**Purpose**: Rate limiting, authentication, routing

**Rate Limiting**:
```
Anonymous Users:
- 10 URL creations per hour
- 100 redirects per hour

Authenticated Users (Free):
- 100 URL creations per hour
- 1000 redirects per hour

Premium Users:
- 10,000 URL creations per hour
- Unlimited redirects
```

**Implementation**: Token bucket algorithm in Redis

#### 3. Application Servers
**Stateless Design**: Any server can handle any request

**Scaling**:
- Auto-scaling based on CPU (target: 70%)
- Min instances: 5
- Max instances: 50
- Scale up time: 2 minutes

**Technology Options**:
- **Node.js**: High concurrency, event-driven
- **Go**: High performance, compiled
- **Python (Flask/FastAPI)**: Quick development

#### 4. Cache Layer (Redis)
**Purpose**: Reduce database load, improve latency

**Cache Strategy**: Cache-aside (lazy loading)

**What We Cache**:
```
URL Mappings (24 hour TTL):
  Key: url:{short_code}
  Value: {long_url, expires_at}

Click Counters (permanent):
  Key: clicks:{short_code}
  Value: Click count

Rate Limit Counters (1 hour TTL):
  Key: rate:{ip}:create
  Value: Request count

User Sessions (1 hour TTL):
  Key: session:{user_id}
  Value: JWT metadata
```

**Cache Warming**:
- Pre-load top 1000 URLs on startup
- Identify from analytics (most clicked)
- Schedule refresh for trending URLs

**Cache Eviction**: LRU (Least Recently Used)

#### 5. Primary Database (PostgreSQL)
**Purpose**: Source of truth for URL mappings

**Scaling Strategy**:
```
Phase 1 (< 100M URLs):
  - Single PostgreSQL instance
  - 5 read replicas for redirects
  - Master handles writes

Phase 2 (100M - 1B URLs):
  - Shard by short_code ranges
  - Shard 1: 0-9, a-f
  - Shard 2: g-m
  - Shard 3: n-t
  - Shard 4: u-z, A-Z

Phase 3 (> 1B URLs):
  - Consider migrating to Cassandra
  - Or use DynamoDB for infinite scale
```

**Replication**:
- 1 Master (writes)
- 5 Read Replicas (reads)
- Async replication (< 1 second lag)

#### 6. Analytics Database (Cassandra)
**Purpose**: Store billions of click events

**Why Cassandra**:
- Write-optimized (4K clicks/sec)
- Time-series data (sorted by timestamp)
- Linear scalability
- Geographic distribution

**Cluster Configuration**:
- 10 nodes
- Replication factor: 3
- Write throughput: 10K/sec per node
- Total: 100K writes/sec (plenty of headroom)

#### 7. Message Queue (Kafka)
**Purpose**: Decouple redirect from analytics

**Topics**:
```
click_events:
- All click events
- 4K msgs/sec average
- 12K peak
- Retention: 7 days
```

**Benefits**:
- Redirects not blocked by analytics
- Can replay events if analytics DB fails
- Buffer traffic spikes

---

## 6️⃣ Deep Dives

### Deep Dive 1: Short Code Generation (Base62)

**The Algorithm**:

```python
class Base62Encoder:
    BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    
    @staticmethod
    def encode(number):
        """Convert number to Base62 string"""
        if number == 0:
            return Base62Encoder.BASE62[0]
        
        result = []
        while number > 0:
            remainder = number % 62
            result.append(Base62Encoder.BASE62[remainder])
            number = number // 62
        
        return ''.join(reversed(result))
    
    @staticmethod
    def decode(short_code):
        """Convert Base62 string back to number"""
        number = 0
        for char in short_code:
            number = number * 62 + Base62Encoder.BASE62.index(char)
        return number

# Examples:
# encode(0) → "0"
# encode(61) → "Z"
# encode(62) → "10"
# encode(125) → "21"
# encode(3844) → "100"
# encode(56800235584) → "zzzzzz" (6 chars)
```

**Distributed Counter Problem**:

```
Problem: Single Redis counter becomes bottleneck at high scale

Solution 1: Pre-allocate Ranges
  Server 1: Gets range 1-1,000,000
  Server 2: Gets range 1,000,001-2,000,000
  Server 3: Gets range 2,000,001-3,000,000
  
  Each server maintains local counter
  When range exhausted, request new range

Solution 2: Use Zookeeper
  - Distributed coordination
  - Atomic counter operations
  - No single point of failure

Solution 3: Timestamp + Server ID + Sequence
  Similar to Twitter Snowflake:
  - Timestamp (41 bits)
  - Server ID (10 bits)
  - Sequence (13 bits)
  Convert to Base62
```

**Implementation with Pre-allocated Ranges**:

```python
class DistributedCounter:
    def __init__(self, server_id, range_size=1_000_000):
        self.server_id = server_id
        self.range_size = range_size
        self.current_range_start = None
        self.current_counter = 0
    
    def get_next_id(self):
        """Get next unique ID"""
        
        # Check if need new range
        if self.current_range_start is None or \
           self.current_counter >= self.range_size:
            self.allocate_new_range()
        
        # Return ID from current range
        id = self.current_range_start + self.current_counter
        self.current_counter += 1
        
        return id
    
    def allocate_new_range(self):
        """Get new range from Zookeeper/Redis"""
        
        # Atomic increment in Zookeeper
        range_id = zk.increment_and_get('/url_ranges/counter')
        
        self.current_range_start = range_id * self.range_size
        self.current_counter = 0
        
        logger.info(f"Allocated range: {self.current_range_start} to "
                    f"{self.current_range_start + self.range_size}")

# Usage:
counter = DistributedCounter(server_id=1)
id = counter.get_next_id()  # e.g., 1000000
short_code = Base62Encoder.encode(id)  # "4gfFC"
```

**Why Base62 is Optimal**:
```
Characters: 0-9, a-z, A-Z = 62 total

Length    Combinations      Time to Exhaust (100M/month)
4 chars   14.7 million     < 1 month (too small)
5 chars   916 million      9 months (too small)
6 chars   56.8 billion     47 years ✓
7 chars   3.5 trillion     2,916 years ✓

Recommendation: Start with 6, expand to 7 if needed
```

### Deep Dive 2: Caching Strategy

**Cache-Aside Pattern**:

```python
class URLCache:
    def __init__(self, redis_client, db_client):
        self.redis = redis_client
        self.db = db_client
    
    def get_long_url(self, short_code):
        """Get long URL with cache-aside pattern"""
        
        # 1. Try cache first
        cached = self.redis.get(f"url:{short_code}")
        
        if cached:
            # Cache hit
            cache_hit_metric.inc()
            return json.loads(cached)
        
        # 2. Cache miss - query database
        cache_miss_metric.inc()
        
        url_data = self.db.query(
            "SELECT long_url, expires_at FROM urls WHERE short_code = ?",
            short_code
        )
        
        if not url_data:
            return None
        
        # 3. Check expiration
        if url_data['expires_at'] and url_data['expires_at'] < datetime.now():
            return None  # Expired
        
        # 4. Store in cache for next time (24 hour TTL)
        self.redis.setex(
            f"url:{short_code}",
            86400,  # 24 hours
            json.dumps(url_data)
        )
        
        return url_data
```

**Cache Warming for Popular URLs**:

```python
def warm_cache():
    """Pre-load hot URLs into cache on startup"""
    
    # Get top 1000 most clicked URLs from last 7 days
    hot_urls = analytics_db.query("""
        SELECT short_code, COUNT(*) as clicks
        FROM click_events
        WHERE clicked_at > NOW() - INTERVAL '7 days'
        GROUP BY short_code
        ORDER BY clicks DESC
        LIMIT 1000
    """)
    
    # Load into cache
    for url in hot_urls:
        url_data = db.query(
            "SELECT long_url FROM urls WHERE short_code = ?",
            url.short_code
        )
        
        if url_data:
            redis.setex(
                f"url:{url.short_code}",
                86400,
                json.dumps(url_data)
            )
    
    logger.info(f"Warmed cache with {len(hot_urls)} URLs")
```

**Cache Eviction**:
- **Policy**: LRU (Least Recently Used)
- **Max Memory**: 100GB per Redis node
- **Eviction**: Automatic when memory full
- **Monitoring**: Track eviction rate

### Deep Dive 3: Analytics at Scale

**Challenge**: Store and query billions of click events

**Architecture**:

```
[Redirect Request] → [Log to
