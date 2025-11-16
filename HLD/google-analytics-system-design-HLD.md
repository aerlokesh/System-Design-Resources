# Google Analytics - User Analytics Dashboard and Pipeline - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [API Design](#api-design)
9. [Event Processing Flow](#event-processing-flow)
10. [Deep Dives](#deep-dives)
11. [Scalability & Reliability](#scalability--reliability)
12. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a web analytics platform like Google Analytics that allows website owners to:
- Track user behavior on their websites (page views, clicks, events)
- Collect real-time analytics data from millions of websites
- Process billions of events per day with minimal latency
- Provide real-time dashboards showing current activity
- Generate historical reports and trend analysis
- Support custom dimensions and metrics
- Enable audience segmentation and funnel analysis
- Track conversion goals and e-commerce transactions
- Provide insights on user demographics and behavior

### Scale Requirements
- **10 million websites** using the analytics platform
- **50 billion events per day** (pageviews, clicks, events)
- **1 million concurrent events per second** at peak
- **Real-time dashboard updates** within 1-5 seconds
- **Historical data retention** for 14-26 months
- **Support for 100+ custom dimensions** per website
- **Query response time**: < 2 seconds for dashboard
- **Data accuracy**: 99.9% event capture rate

---

## Functional Requirements

### Must Have (P0)

1. **Data Collection**
   - JavaScript tracking library (analytics.js)
   - Mobile SDK (iOS, Android)
   - Server-side tracking API
   - Cookie-based user identification
   - Session management
   - Support for custom events and parameters

2. **Real-Time Analytics**
   - Live visitor count (active users right now)
   - Real-time event stream (last 30 minutes)
   - Active pages and screen views
   - Top referrers and traffic sources
   - Geographic distribution of users
   - Real-time conversion tracking

3. **Historical Reports**
   - Traffic acquisition reports (source, medium, campaign)
   - Audience reports (demographics, interests, devices)
   - Behavior reports (pageviews, bounce rate, time on site)
   - Conversion reports (goals, e-commerce)
   - Custom date range selection
   - Comparison with previous periods

4. **Dashboard & Visualization**
   - Customizable dashboard widgets
   - Line charts, bar charts, pie charts, tables
   - Drill-down capabilities
   - Export to CSV, PDF
   - Scheduled email reports
   - Segment comparison

5. **User Management**
   - Multi-user access control
   - Role-based permissions (read-only, edit, admin)
   - Multiple properties per account
   - Views with filters
   - Goal configuration

6. **Event Tracking**
   - Pageview tracking
   - Event tracking (clicks, form submissions)
   - E-commerce transaction tracking
   - Custom dimensions and metrics
   - User ID tracking
   - Enhanced e-commerce tracking

### Nice to Have (P1)
- Machine learning insights (anomaly detection, predictions)
- Attribution modeling
- Funnel visualization
- Cohort analysis
- User flow visualization
- Integration with advertising platforms
- A/B testing integration
- Data sampling controls
- BigQuery export
- Data import capabilities

---

## Non-Functional Requirements

### Performance
- **Event ingestion latency**: < 100ms (p95)
- **Real-time dashboard delay**: < 5 seconds
- **Report generation time**: < 2 seconds for standard reports
- **Query latency**: < 500ms for simple queries, < 2s for complex
- **JavaScript library load time**: < 50kb (compressed)

### Scalability
- Handle 10M+ websites simultaneously
- Process 50B+ events per day
- Support 1M+ concurrent active users
- Scale to 2M events per second at peak
- Store petabytes of historical data
- Support 100K+ concurrent dashboard queries

### Availability
- **99.9% uptime** for data collection
- **99.95% uptime** for dashboard queries
- Graceful degradation during failures
- Multi-region deployment
- No data loss during failures

### Consistency
- **Eventual consistency** for real-time reports (acceptable 1-5 sec delay)
- **Strong consistency** for billing and account management
- **At-least-once delivery** for event processing
- Idempotent event processing

### Data Retention
- **Real-time data**: 30 minutes in hot storage
- **Recent data**: 7 days in warm storage
- **Historical data**: 14-26 months (based on tier)
- **Aggregated data**: Indefinite retention
- **Raw event data**: 90 days (optional BigQuery export)

### Security & Privacy
- **GDPR compliance**: IP anonymization, data deletion
- **Cookie consent management**
- **Data encryption** in transit and at rest
- **Access control** and audit logs
- **Bot filtering** and fraud detection
- **Cross-origin resource sharing (CORS)** support

---

## Capacity Estimation

### Traffic Estimates

```
Total websites: 10M
Average events per website per day: 5,000
Total events per day: 50B events

Peak traffic (3x average):
- Average: 50B / 86,400 = 578,000 events/sec
- Peak: 1,734,000 events/sec (~1.7M events/sec)

Dashboard users:
- Active website owners: 1M daily
- Average sessions per user: 5 sessions/day
- Average queries per session: 20 queries
- Total queries per day: 100M queries
- Peak queries per second: 3,000 QPS
```

### Storage Estimates

```
Event Data Structure (per event):
- Timestamp: 8 bytes
- User ID (cookie): 36 bytes
- Session ID: 36 bytes
- Page URL: 200 bytes (average)
- Referrer: 100 bytes
- User Agent: 150 bytes
- Geographic data: 50 bytes
- Custom dimensions (10): 500 bytes
- Event type and category: 50 bytes
- E-commerce data: 200 bytes (optional)

Average event size: ~1 KB

Daily storage (raw events): 50B events × 1 KB = 50 TB/day
Monthly storage (raw): 50 TB × 30 = 1.5 PB/month
Annual storage (raw): 1.5 PB × 12 = 18 PB/year

Aggregated data (pre-computed):
- Daily aggregates: 100 GB/day
- Monthly aggregates: 3 TB/month
- Yearly aggregates: 36 TB/year

Total storage (2 years):
- Raw events: 36 PB
- Aggregated: 72 TB
- Indexes: 5 PB
- Total: ~41 PB
```

### Processing Requirements

```
Real-time Processing Pipeline:
- Events per second: 1.7M (peak)
- Processing per event: 10ms
- Required compute: 17,000 cores at peak

Batch Processing Pipeline:
- Daily data: 50 TB
- Aggregation jobs: 4 hours
- Required compute: 10,000 cores

Query Processing:
- Dashboard queries: 3,000 QPS (peak)
- Average query time: 100ms
- Required compute: 300 concurrent queries
```

### Network Bandwidth

```
Ingestion Bandwidth:
- Events per second: 1.7M (peak)
- Event size: 1 KB
- Ingestion bandwidth: 1.7 GB/s = 13.6 Gbps

Dashboard Queries:
- Queries per second: 3,000
- Response size: 50 KB (average)
- Query bandwidth: 150 MB/s = 1.2 Gbps

Total bandwidth: ~15 Gbps at peak
```

### Cost Estimation

```
Monthly Costs (approximate):

Compute:
- Streaming processing: $200K
- Batch processing: $50K
- Query servers: $60K

Storage:
- Hot storage (SSD): $20K
- Warm storage (HDD): $30K
- Cold storage (S3): $800K

Network:
- Data transfer: $75K

Total: ~$1.2M per month
Revenue: 10M websites × $10/month = $100M/month
Infrastructure cost: 1.2% of revenue
```

---

## High-Level Architecture

```
                         [10 Million Websites]
                                  |
                                  ↓
                    [JavaScript SDK / Mobile SDK]
                    (Client-side event collection)
                                  |
                                  ↓
                    [Global CDN - Edge Collection]
                    (Receive events from users)
                                  |
            ┌─────────────────────┼─────────────────────┐
            ↓                     ↓                     ↓
      [US Region]           [EU Region]          [APAC Region]
            |                     |                     |
            └─────────────────────┼─────────────────────┘
                                  ↓
                        [Event Ingestion Layer]
                        (Load Balancer + API Gateway)
                                  |
                                  ↓
                        [Message Queue - Kafka]
                        (Event streaming buffer)
                                  |
            ┌─────────────────────┼─────────────────────┐
            ↓                     ↓                     ↓
    [Stream Processing]   [Batch Processing]    [Real-time Aggregation]
    (Flink/Spark)         (Spark/MapReduce)     (Druid/ClickHouse)
            |                     |                     |
            └─────────────────────┼─────────────────────┘
                                  ↓
                          [Data Storage Layer]
                                  |
        ┌───────────────────────┬─┴─┬───────────────────────┐
        ↓                       ↓   ↓                       ↓
  [Hot Storage]           [Time-Series DB]          [Data Warehouse]
  (Redis/Druid)           (ClickHouse)              (BigQuery/Redshift)
  (Real-time)             (Recent queries)          (Historical analysis)
        |                       |                           |
        └───────────────────────┼───────────────────────────┘
                                ↓
                        [Query Processing Layer]
                                |
            ┌───────────────────┼───────────────────┐
            ↓                   ↓                   ↓
    [Query Router]      [Cache Layer]      [Query Optimizer]
    (Route to DB)       (Redis/Memcached)  (Query planning)
            |                   |                   |
            └───────────────────┼───────────────────┘
                                ↓
                        [API Gateway]
                                |
            ┌───────────────────┼───────────────────┐
            ↓                   ↓                   ↓
    [Dashboard UI]      [Mobile Apps]       [API Clients]
    (React/Angular)     (iOS/Android)       (Third-party)
```

### Key Architectural Decisions

1. **Kafka for Message Queue**
   - High throughput (1M+ messages/sec)
   - Durability (no event loss)
   - Partition by tracking_id for ordering
   - Consumer groups for parallel processing
   - Replay capability for failures

2. **ClickHouse for Time-Series Data**
   - Columnar storage optimized for analytics
   - Fast aggregation queries (sub-second)
   - Horizontal scalability
   - SQL interface for easy querying
   - Built-in compression (10:1 ratio)

3. **Redis for Caching & Real-Time**
   - In-memory speed for active user counts
   - Real-time event stream (last 30 min)
   - Query result caching
   - Sub-millisecond lookups
   - Pub/Sub for real-time updates

4. **BigQuery for Historical Analysis**
   - Petabyte-scale data warehouse
   - Serverless (auto-scaling)
   - Complex analytical queries
   - Integration with ML tools
   - Cost-effective for cold storage

5. **Multi-Tier Storage Strategy**
   - Hot (Redis/Druid): Last 30 minutes
   - Warm (ClickHouse): Last 7 days
   - Cold (BigQuery): 14-26 months
   - Optimizes cost and query performance

---

## Core Components

### 1. Event Collection Layer

**Purpose**: Collect analytics events from millions of websites with minimal latency

**Components**:

#### A. JavaScript SDK (analytics.js)

**Key Features**:
- Asynchronous loading (non-blocking)
- Automatic data collection (pageviews, sessions, bounce rate)
- Cookie management for user identification
- Cross-domain tracking
- Custom dimensions and metrics (up to 100)
- E-commerce transaction tracking
- Exception tracking
- Offline support (queue events when offline)

**Data Collected Automatically**:
- Timestamp and timezone
- Client ID (persistent cookie)
- Session ID (30-minute timeout)
- Page URL, title, referrer
- User agent, language, screen resolution
- Geolocation (from IP address server-side)
- Traffic source (organic, paid, referral, direct)

**Custom Event Tracking**:
- Event category, action, label, value
- E-commerce: products, transactions, revenue
- User properties and custom dimensions
- Content grouping

**Library Size**: ~30kb compressed (critical for page load performance)

#### B. Mobile SDK (iOS/Android)

**Features**:
- Screen view tracking
- Event tracking
- User property tracking
- E-commerce tracking
- Crash reporting
- Automatic session management
- Offline event queuing
- Battery-efficient (batch sends)

**Platform-Specific**:
- iOS: Integrates with APNs for app lifecycle
- Android: Integrates with Google Play Services
- React Native: Cross-platform SDK

#### C. Collection API

**Endpoint**: `https://analytics.google.com/collect`

**Protocol**: HTTP POST with URL-encoded parameters

**Key Parameters**:
- Version (v=1)
- Tracking ID (tid=UA-XXXXX-Y)
- Client ID (cid)
- Hit type (t=pageview/event/transaction)
- Document path (dp), title (dt)
- Event category (ec), action (ea), label (el)
- Custom dimensions (cd1, cd2, ..., cd100)
- Custom metrics (cm1, cm2, ..., cm100)

**Response**: HTTP 200 with empty body (fast acknowledgment)

**Features**:
- Batch sending (send multiple hits in one request)
- Gzip compression
- Retry logic with exponential backoff
- Idempotency via hit deduplication

### 2. Event Ingestion Pipeline

**Purpose**: Receive, validate, and route millions of events per second

**Architecture**:

```
[CDN Edge Servers]
         ↓
[Global Load Balancer]
         ↓
[API Gateway Cluster]
    ├─ Authentication
    ├─ Rate limiting
    ├─ Validation
    └─ Enrichment
         ↓
[Kafka Topic: raw-events]
(Partitioned by tracking_id)
```

**Request Processing**:

1. **Reception**: CDN edge servers receive events globally
2. **Load Balancing**: Route to nearest API gateway instance
3. **Validation**: Check required fields, format, tracking ID validity
4. **Enrichment**: Add server timestamp, IP address, geographic data
5. **Deduplication**: Check for duplicate events (bloom filter)
6. **Rate Limiting**: Enforce per-tracking-ID limits (10K events/minute)
7. **Queueing**: Publish to Kafka for async processing
8. **Acknowledgment**: Return 200 OK immediately

**Validation Rules**:
- Required fields: tracking_id, client_id, timestamp
- Tracking ID format: UA-XXXXXXX-Y
- Timestamp within 24 hours (reject old events)
- URL format validation
- Bot detection (filter known crawlers)
- Payload size limit (< 8KB)

**Bot Detection**:
- User agent pattern matching
- Behavioral analysis (too fast, no mouse movement)
- IP reputation checks
- Challenge-response for suspicious traffic

**Rate Limiting**:
- Token bucket algorithm per tracking ID
- Limit: 10,000 events/minute per website
- Prevents abuse and DDoS
- Returns 429 Too Many Requests if exceeded

**Deduplication Strategy**:
- Bloom filter for recent event IDs (1 hour window)
- Check if event_id seen before
- If duplicate: Acknowledge but don't process
- False positive rate: 0.1% (acceptable)

### 3. Stream Processing (Real-Time)

**Purpose**: Process events in real-time for live dashboards

**Technology**: Apache Flink or Kafka Streams

**Processing Flow**:

```
[Kafka: raw-events]
         ↓
[Stream Processor]
    ├─ Parse and validate
    ├─ Enrich with geo data
    ├─ Session aggregation
    ├─ Real-time metrics
    └─ Anomaly detection
         ↓
┌────────┴────────┐
↓                 ↓
[Druid]       [Redis]
(Query)       (Cache)
```

**Stream Processing Jobs**:

1. **Geo Enrichment**
   - Lookup IP address in MaxMind GeoIP database
   - Add country, region, city, coordinates
   - Cache lookups for performance

2. **Session Aggregation**
   - Group events by session_id
   - 30-minute session timeout
   - Calculate session metrics (duration, page depth, bounce)
   - Update session state in real-time

3. **Active User Counting**
   - Tumbling window (1 minute)
   - Count distinct client_ids
   - Store in Redis for dashboard queries
   - Track active users for last 1, 5, 30 minutes

4. **Pageview Aggregation**
   - Count pageviews per minute per tracking_id
   - Top pages by traffic
   - Traffic sources distribution
   - Store in Druid for querying

5. **Anomaly Detection**
   - Compare current metrics to historical baseline
   - Detect traffic spikes or drops
   - Alert on unusual patterns
   - ML model for pattern recognition

**Windowing Strategies**:
- Tumbling windows: Non-overlapping (1 min, 5 min, 30 min)
- Sliding windows: Overlapping for smooth graphs
- Session windows: Event-triggered (30 min timeout)

**State Management**:
- Flink state backend (RocksDB)
- Checkpointing every 1 minute
- Fault tolerance via state snapshots
- Recovery from failures without data loss

**Performance Optimizations**:
- Parallel processing (key by tracking_id)
- Batch processing (micro-batches of 1000 events)
- Async I/O for external lookups
- Minimize state size

### 4. Batch Processing (Historical)

**Purpose**: Process historical data for reports and aggregations

**Technology**: Apache Spark or MapReduce

**Processing Schedule**:
- **Hourly jobs**: Aggregate last hour's data
- **Daily jobs**: Daily reports and rollups
- **Weekly/Monthly jobs**: Long-term trends

**Batch Jobs**:

1. **Daily Summary Aggregation**
   - Input: Raw events for the day
   - Output: Daily metrics per tracking_id
   - Metrics: Unique visitors, pageviews, sessions, bounce rate, avg session duration
   - Partitioned by date and tracking_id
   - Stored in data warehouse

2. **Top Pages Report**
   - Identify top 100 pages by traffic
   - Calculate unique pageviews
   - Rank by popularity
   - Store for dashboard queries

3. **Traffic Source Attribution**
   - Attribute sessions to source/medium/campaign
   - First-touch vs last-touch attribution
   - Multi-touch attribution models
   - Calculate conversion rates by source

4. **Funnel Analysis**
   - Define conversion funnels
   - Track user progress through steps
   - Calculate drop-off rates
   - Identify bottlenecks

5. **Cohort Analysis**
   - Group users by first visit date
   - Track retention over time
   - Calculate engagement metrics
   - Identify user segments

6. **E-commerce Metrics**
   - Revenue by product, category
   - Average order value
   - Conversion rate
   - Cart abandonment rate

**Processing Optimizations**:
- Columnar storage format (Parquet)
- Data partitioning by date
- Pre-aggregation for common queries
- Incremental processing (process only new data)
- Compression (reduces I/O)

**Data Quality**:
- Remove bot traffic
- Filter invalid events
- Handle missing data
- Reconcile duplicates

### 5. Data Storage Architecture

**Multi-Tier Storage Strategy**:

```
┌─────────────────────────────────────────────────┐
│ Tier 1: Hot Storage (Redis/Druid)              │
│ - Last 30 minutes of data                      │
│ - Real-time dashboard queries                  │
│ - In-memory for ultra-fast access              │
│ - Size: ~100 GB                                │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Tier 2: Warm Storage (ClickHouse)              │
│ - Last 7 days of data                          │
│ - Recent reports and ad-hoc queries            │
│ - Columnar storage, indexed                    │
│ - Size: ~1 TB                                  │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Tier 3: Cold Storage (BigQuery/Redshift)       │
│ - 14-26 months historical data                 │
│ - Long-term trends and analysis                │
│ - Compressed, partitioned by date              │
│ - Size: ~40 PB                                 │
└─────────────────────────────────────────────────┘
```

#### A. Time-Series Database (ClickHouse)

**Why ClickHouse**:
- Columnar storage optimized for analytics
- Fast aggregation queries (sub-second)
- Horizontal scalability (add nodes)
- SQL interface
- Built-in compression (10:1 ratio)
- Handles 1M+ inserts/second

**Data Model**:
- Partitioned by date (monthly partitions)
- Ordered by (tracking_id, timestamp)
- Materialized views for common aggregations
- TTL policy (auto-delete old data after 7 days)

**Query Optimization**:
- Pre-aggregated views for common queries
- Indexes on frequently queried columns
- Sampling for large date ranges
- Query result caching

#### B. Real-Time Storage (Redis)

**Use Cases**:
- Active user count (last 30 minutes)
- Recent event stream
- Real-time metrics cache
- Session state
- Query result caching

**Data Structures**:
- Sorted sets for active users (score = timestamp)
- Lists for recent events (FIFO queue)
- Hashes for session data
- Strings for counters and metrics
- TTL for automatic expiration

**Performance**:
- Sub-millisecond reads
- 100K+ ops/second per node
- Cluster mode for horizontal scaling
- Persistence (RDB snapshots + AOF)

#### C. Data Warehouse (BigQuery/Redshift)

**Purpose**: Long-term historical analysis, complex queries

**Data Model**:
- Partitioned by date (daily partitions)
- Clustered by tracking_id
- Nested/repeated fields for flexibility
- 2-year retention policy

**Query Types**:
- Historical trend analysis
- Cohort studies
- Attribution modeling
- Custom reports
- Data exports

**Cost Optimization**:
- Partition pruning (only scan relevant dates)
- Columnar storage (only read needed columns)
- Result caching (reuse recent query results)
- Query cost estimation before execution

### 6. Query Processing Layer

**Purpose**: Route queries to appropriate storage tier and optimize execution

**Architecture**:

```
[Dashboard Request]
         ↓
[API Gateway]
         ↓
[Query Router]
         ↓
    ┌────┴────┐
    ↓         ↓
[Cache?]   [Query Optimizer]
(Redis)         ↓
    │      [Select Storage]
    │           ↓
    │   ┌───────┴───────┐
    │   ↓               ↓
    │ [Hot: Redis]  [Cold: BigQuery]
    │               
    │       
    └───────────────────────┘
            ↓
    [Aggregate Results]
            ↓
    [Cache Response]
            ↓
    [Return to User]
```

**Query Routing Logic**:

1. **Real-time queries** (last 30 minutes):
   - Route to Redis
   - Sub-second response
   - Active users, current traffic

2. **Recent queries** (last 7 days):
   - Route to ClickHouse
   - 1-2 second response
   - Daily reports, trending pages

3. **Historical queries** (> 7 days):
   - Route to BigQuery
   - 2-5 second response
   - Long-term trends, comparisons

**Query Optimization**:

1. **Cache Check**: Look for cached result in Redis
2. **Date Range Analysis**: Determine which storage tier(s) to query
3. **Query Rewriting**: Optimize SQL for target database
4. **Parallel Execution**: Query multiple tiers in parallel if needed
5. **Result Aggregation**: Combine results from multiple sources
6. **Caching**: Store result for future requests

**Caching Strategy**:
- Cache key: hash(query + parameters)
- TTL based on query type:
  - Real-time: 60 seconds
  - Recent: 5 minutes
  - Historical: 1 hour
- Cache invalidation on data updates

**Query Limits**:
- Max result size: 10MB
- Query timeout: 30 seconds
- Pagination for large results
- Sampling for queries > 1 year

---

## Database Design

### 1. Event Database (ClickHouse)

**Schema Design**:

**Raw Events Table**:
```
Table: analytics_events
Partition: By month (toYYYYMM(date))
Order: (tracking_id, timestamp)
TTL: 7 days

Columns:
- date: Date (partition key)
- tracking_id: String
- client_id: String
- session_id: String
- timestamp: DateTime64(3)
- hit_type: String (pageview, event, transaction)
- page_url: String
- page_title: String
- referrer: String
- user_agent: String
- language: String
- screen_resolution: String
- country: String
- region: String
- city: String
- event_category: String
- event_action: String
- event_label: String
- event_value: Float64
- custom_dimensions: Map(String, String)
- custom_metrics: Map(String, Float64)
```

**Aggregated Tables** (Materialized Views):

**Daily Summary**:
```
Table: daily_summary
Partition: By month
Order: (tracking_id, date)

Columns:
- tracking_id: String
- date: Date
- unique_visitors: UInt64
- pageviews: UInt64
- sessions: UInt64
- bounces: UInt64
- total_session_duration: UInt64
- avg_session_duration: Float32
- bounce_rate: Float32
```

**Hourly Active Users**:
```
Table: hourly_active_users
Partition: By month
Order: (tracking_id, hour)

Columns:
- tracking_id: String
- hour: DateTime
- active_users: UInt64
```

**Top Pages**:
```
Table: top_pages
Partition: By date
Order: (tracking_id, pageviews DESC)

Columns:
- tracking_id: String
- date: Date
- page_path: String
- pageviews: UInt64
- unique_pageviews: UInt64
- avg_time_on_page: Float32
- bounce_rate: Float32
```

**Partitioning Strategy**:
- Partition by month for efficient pruning
- Order by tracking_id for co-location
- TTL for automatic cleanup
- Compression codec (LZ4)

**Replication**:
- Replication factor: 2
- Distributed tables for sharding
- Shard by tracking_id hash

### 2. Metadata Database (PostgreSQL)

**Schema Design**:

**Users Table**:
```
Table: users
Primary Key: user_id

Columns:
- user_id: UUID
- email: VARCHAR(255) UNIQUE
- name: VARCHAR(100)
- created_at: TIMESTAMP
- updated_at: TIMESTAMP
```

**Properties Table**:
```
Table: properties
Primary Key: property_id
Foreign Key: user_id

Columns:
- property_id: UUID
- user_id: UUID
- tracking_id: VARCHAR(50) UNIQUE
- website_url: VARCHAR(500)
- industry: VARCHAR(50)
- timezone: VARCHAR(50)
- currency: VARCHAR(3)
- created_at: TIMESTAMP
```

**Goals Table**:
```
Table: goals
Primary Key: goal_id
Foreign Key: property_id

Columns:
- goal_id: UUID
- property_id: UUID
- goal_name: VARCHAR(100)
- goal_type: VARCHAR(50) (destination, duration, pages_per_session, event)
- goal_value: DECIMAL(10, 2)
- goal_funnel: JSONB
- created_at: TIMESTAMP
```

**Views Table**:
```
Table: views
Primary Key: view_id
Foreign Key: property_id

Columns:
- view_id: UUID
- property_id: UUID
- view_name: VARCHAR(100)
- filters: JSONB
- default_view: BOOLEAN
- created_at: TIMESTAMP
```

**Segments Table**:
```
Table: segments
Primary Key: segment_id
Foreign Key: property_id

Columns:
- segment_id: UUID
- property_id: UUID
- segment_name: VARCHAR(100)
- conditions: JSONB
- created_at: TIMESTAMP
```

**Indexes**:
- B-tree index on email
- B-tree index on tracking_id
- GIN index on filters (JSONB)
- GIN index on conditions (JSONB)

**Sharding Strategy**:
- Shard by user_id (modulo 16)
- Co-locate user and related properties
- Cross-shard joins avoided

### 3. Cache Layer (Redis)

**Data Structures**:

**Active Users** (Sorted Set):
```
Key: active_users:{tracking_id}:{minute}
Score: timestamp
Member: client_id
TTL: 30 minutes

Purpose: Count active users in last 30 minutes
```

**Real-Time Metrics** (Hash):
```
Key: metrics:{tracking_id}:realtime
Fields:
- pageviews_last_minute: count
- active_users_now: count
- top_page: url
- bounce_rate: percentage
TTL: 1 minute

Purpose: Real-time dashboard metrics
```

**Recent Events** (List):
```
Key: events:{tracking_id}:recent
Values: JSON event objects
Max length: 1000 events
TTL: 30 minutes

Purpose: Real-time event stream for dashboard
```

**Query Cache** (String):
```
Key: query:{hash(query+params)}
Value: JSON result
TTL: 60 seconds (real-time), 5 minutes (recent), 1 hour (historical)

Purpose: Cache query results
```

**Session State** (Hash):
```
Key: session:{session_id}
Fields:
- tracking_id
- client_id
- start_time
- last_seen
- pageview_count
- events_count
TTL: 30 minutes

Purpose: Track active session state
```

---

## API Design

### RESTful Endpoints

#### Event Collection APIs

```
POST /collect
- Receive tracking events from websites
- Validates and queues events
- Returns 200 OK immediately
- Rate limit: 10,000 events/minute per tracking_id

POST /batch
- Receive multiple events in one request
- Max 20 events per batch
- Reduces network overhead
- Returns batch acknowledgment

GET /collect.gif
- 1x1 pixel tracking (for email opens)
- All parameters in query string
- Returns transparent GIF
- Sets no-cache headers
```

#### Dashboard APIs

```
GET /api/v1/realtime/active-users?tracking_id={id}
- Get current active user count
- Returns last 1, 5, 30 minute counts
- Response time: < 100ms

GET /api/v1/realtime/events?tracking_id={id}&limit=100
- Get recent events stream
- Real-time event list
- WebSocket alternative available

GET /api/v1/reports/traffic-sources?tracking_id={id}&start_date={date}&end_date={date}
- Traffic acquisition report
- Group by source, medium, campaign
- Includes conversion metrics

GET /api/v1/reports/behavior/pages?tracking_id={id}&start_date={date}&end_date={date}
- Top pages by traffic
- Pageviews, unique pageviews, bounce rate
- Paginated results

GET /api/v1/reports/audience/demographics?tracking_id={id}&start_date={date}&end_date={date}
- User demographics
- Country, city, language, device
- Aggregated metrics

GET /api/v1/reports/conversions/goals?tracking_id={id}&start_date={date}&end_date={date}
- Goal completion report
- Conversion rate by goal
- Funnel visualization data
```

#### Configuration APIs

```
POST /api/v1/properties
- Create new property (website)
- Returns tracking_id

PUT /api/v1/properties/{property_id}/goals
- Configure conversion goals
- Define goal type and value

GET /api/v1/properties/{property_id}/views
- List all views for property
- View filters and settings

POST /api/v1/properties/{property_id}/segments
- Create user segment
- Define segment conditions
```

### API Response Format

```json
{
  "status": "success",
  "data": {
    "tracking_id": "UA-12345-1",
    "realtime": {
      "active_users": 1234,
      "pageviews_per_minute": 567
    }
  },
  "metadata": {
    "timestamp": "2025-01-16T02:44:00Z",
    "processing_time_ms": 45
  }
}
```

### WebSocket API (Real-Time Updates)

```
WS /api/v1/realtime/stream?tracking_id={id}
- Real-time event stream
- Server pushes events as they arrive
- Client subscribes to tracking_id
- Heartbeat every 30 seconds
- Auto-reconnect on disconnect
```

---

## Event Processing Flow

### End-to-End Flow: User Visits Website

```
T=0ms: User lands on website
    ↓
[Browser loads analytics.js]
    ↓
T=50ms: JavaScript executes
    ↓
[Generate client_id (cookie)]
[Generate session_id]
[Collect page data]
    ↓
T=100ms: Send HTTP POST to /collect
    ↓
[CDN Edge Server - nearest location]
    ↓
T=120ms: [API Gateway]
- Validate tracking_id
- Check rate limit
- Add server timestamp
- Lookup geo from IP
    ↓
[Publish to Kafka: raw-events topic]
    ↓
T=150ms: Return 200 OK to browser
─────────────────────────────────
ASYNC PROCESSING BEGINS
    ↓
[Kafka Consumer: Stream Processor]
    ↓
T=200ms: Process event
- Parse fields
- Enrich with geo data
- Session aggregation
- Calculate metrics
    ↓
[Write to multiple destinations]
    ├─ Redis (real-time cache)
    ├─ Druid (real-time queries)
    └─ Kafka (for batch processing)
    ↓
T=500ms: Real-time dashboard updated
─────────────────────────────────
BATCH PROCESSING (Later)
    ↓
T=1 hour: Hourly batch job runs
- Aggregate hour's events
- Store in ClickHouse
- Pre-compute common queries
    ↓
T=24 hours: Daily batch job runs
- Daily summary aggregation
- Long-term storage in BigQuery
- Remove raw events from ClickHouse (TTL)
```

### Dashboard Query Flow

```
User opens dashboard
    ↓
GET /api/v1/realtime/active-users?tracking_id=UA-12345-1
    ↓
[API Gateway]
    ↓
[Query Router]
- Analyze date range (realtime)
- Route to Redis
    ↓
[Redis]
GET active_users:UA-12345-1:*
- Count last 30 minutes
- Return: 1,234 users
    ↓
[API Gateway]
    ↓
Return response: 50ms total
─────────────────────────────────
Historical Query:
    ↓
GET /api/v1/reports/traffic-sources?start_date=2025-01-01&end_date=2025-01-15
    ↓
[Query Router]
- Analyze: 15 days (recent)
- Route to ClickHouse
    ↓
[Query Optimizer]
- Check cache (miss)
- Optimize SQL query
- Execute on ClickHouse
    ↓
[ClickHouse]
- Scan 15 daily partitions
- Aggregate by source/medium
- Return results
    ↓
[Cache result in Redis]
TTL: 5 minutes
    ↓
Return response: 1.2s total
```

---

## Deep Dives

### 1. Session Management

**Challenge**: Track user sessions across multiple pageviews

**Session Definition**:
- Same user (client_id) visiting multiple pages
- 30-minute timeout between interactions
- Session ends when: 30 min idle, midnight, campaign change

**Implementation**:

**Client-Side**:
- Generate session_id on first page
- Store in session storage (browser-level)
- Persist across page reloads
- Clear on browser close

**Server-Side**:
- Maintain session state in Redis
- Update "last_seen" timestamp on each event
- If gap > 30 minutes: Create new session
- Aggregate session metrics (pageviews, duration)

**Session Metrics**:
- Session duration: last_event_time - first_event_time
- Pages per session: COUNT(distinct pages)
- Bounce: Session with only 1 pageview
- Exit page: Last page in session

### 2. Bot Detection and Filtering

**Problem**: Bots inflate traffic numbers, skew analytics

**Detection Methods**:

1. **User Agent Analysis**
   - Known bot patterns (Googlebot, Bingbot)
   - Missing user agent
   - Unusual user agent strings

2. **Behavioral Analysis**
   - Page views too fast (< 1 second apart)
   - Perfect regular intervals
   - No mouse movement / clicks
   - Straight-line scrolling

3. **IP Reputation**
   - Known bot IP ranges
   - Data center IPs
   - VPN/proxy detection

4. **JavaScript Challenges**
   - Require JavaScript execution
   - Canvas fingerprinting
   - Event timing analysis

**Filtering Strategy**:
- Filter at ingestion (reject obvious bots)
- Flag suspicious traffic (process but mark)
- Post-processing removal (batch jobs)
- Allow override for legitimate crawlers

### 3. Real-Time Active User Counting

**Challenge**: Count active users in last N minutes efficiently

**Naive Approach** (doesn't scale):
- Query all events from last 30 minutes
- Count distinct client_ids
- Too slow for real-time

**Optimized Approach** (HyperLogLog):

**Data Structure**: Redis Sorted Set + HyperLogLog

**Storage**:
```
For each minute:
  Key: active_users:UA-12345-1:2025011602:44
  Value: HyperLogLog of client_ids
  TTL: 30 minutes

Active users (last 30 min):
  - Merge 30 HyperLogLog structures
  - Get cardinality estimate
  - Error rate: <2%
  - Memory: ~12KB per minute
```

**Benefits**:
- Constant memory per minute
- Fast merging (milliseconds)
- Acceptable accuracy trade-off
- Scales to millions of users

**Alternative**: Bloom Filter (less accurate but faster)

### 4. Attribution Modeling

**Challenge**: Which marketing channel gets credit for conversion?

**Attribution Models**:

1. **Last Click**: Last touchpoint gets 100% credit
   - Simple, easy to understand
   - Ignores customer journey
   - Used by default

2. **First Click**: First touchpoint gets 100% credit
   - Credits awareness campaigns
   - Ignores nurturing

3. **Linear**: Equal credit across all touchpoints
   - Fair distribution
   - Doesn't account for importance

4. **Time Decay**: More recent touchpoints get more credit
   - 7-day half-life (most recent = 2x oldest)
   - Reflects recency effect

5. **Position-Based**: 40% first, 40% last, 20% middle
   - Balances awareness and conversion
   - More complex calculation

**Implementation**:
- Store all user touchpoints
- Track: timestamp, source, medium, campaign
- Calculate attribution on conversion
- Batch job for attribution reports

**Data Model**:
```
Touchpoint sequence:
1. Organic Search (Day 1)
2. Social Media (Day 3)
3. Email Campaign (Day 5)
4. Direct (Day 7) → Conversion

Last Click: Email = 100%
First Click: Organic = 100%
Linear: Each = 25%
Time Decay: Direct = 40%, Email = 30%, Social = 20%, Organic = 10%
```

### 5. Data Sampling Strategy

**Challenge**: Queries over large date ranges are slow/expensive

**Sampling Types**:

1. **No Sampling**: Use all data
   - Accurate
   - Slow for large datasets
   - Use for: < 1 month queries

2. **Standard Sampling**: 1-10% of data
   - Fast queries
   - 90-99% reduction in scan
   - Use for: > 1 month queries

3. **Adaptive Sampling**: Adjust based on data size
   - < 1M events: No sampling
   - 1-10M events: 10% sampling
   - 10-100M events: 1% sampling
   - > 100M events: 0.1% sampling

**Implementation**:
- Sample deterministically (hash client_id % 100 < 10)
- Same users always sampled
- Preserves user journey
- Scale results by sample rate

**Sample Size Calculation**:
```
For 95% confidence, 5% margin of error:
Minimum sample = 385 events

Adjust by data size:
- 1M events: Sample 1% = 10K events ✓
- 10M events: Sample 0.1% = 10K events ✓
- 100M events: Sample 0.01% = 10K events ✓
```

### 6. Custom Dimensions and Metrics

**Challenge**: Support 100+ custom dimensions per website

**Storage Strategy**:

**Sparse Storage** (Map/JSON):
```
Instead of 100 columns, use:
custom_dimensions: Map(String, String)

Benefits:
- Flexible schema
- Only store used dimensions
- No null columns
- Easy to add new dimensions
```

**Query Performance**:
- Index common dimensions
- Bloom filter for existence checks
- Vectorized processing in ClickHouse
- Materialize views for frequent queries

**Limits**:
- 100 custom dimensions
- 100 custom metrics
- 500 char max per dimension value
- Dimension keys: cd1-cd100
- Metric keys: cm1-cm100

### 7. Funnel Analysis

**Challenge**: Track multi-step conversion funnels

**Funnel Definition**:
```
E-commerce Funnel:
1. Homepage visit
2. Product page visit
3. Add to cart
4. Checkout
5. Purchase complete
```

**Analysis Approach**:

**User-Level Analysis**:
- Track which steps each user completed
- Calculate drop-off between steps
- Time between steps
- Segment by traffic source

**Calculation**:
```
Step 1 (Homepage): 10,000 users → 100%
Step 2 (Product): 7,000 users → 70%
Step 3 (Cart): 3,500 users → 35%
Step 4 (Checkout): 2,000 users → 20%
Step 5 (Purchase): 1,000 users → 10%

Drop-off rates:
Homepage → Product: 30%
Product → Cart: 50%
Cart → Checkout: 43%
Checkout → Purchase: 50%

Biggest drop-off: Product → Cart (50%)
Focus optimization here
```

**Implementation**:
- Batch job processes user journeys
- Identify funnel steps from events
- Calculate progression rates
- Store funnel reports in data warehouse

---

## Scalability & Reliability

### Horizontal Scaling

**Stateless Services** (Easy to scale):
- API Gateway: Add more instances behind load balancer
- Stream processors: Add Flink workers (Kafka rebalances)
- Batch processors: Add Spark workers
- Query servers: Add instances behind load balancer

**Auto-Scaling Rules**:
```
API Gateway:
- Scale up: CPU > 70% for 5 minutes
- Scale down: CPU < 30% for 15 minutes
- Min: 20 instances per region
- Max: 200 instances per region

Stream Processors:
- Scale based on Kafka consumer lag
- If lag > 1M messages: Add 10 workers
- If lag < 100K messages: Remove workers
- Min: 50 workers, Max: 200 workers

Query Servers:
- Scale based on QPS
- Target: 1000 QPS per instance
- Current: 3000 QPS → Need 3 instances
- Add buffer (2x) = 6 instances
```

### Database Scaling

**ClickHouse Horizontal Scaling**:
```
Current: 30 nodes, 100TB, 1M inserts/sec

Add Capacity:
1. Add new shard (10 nodes)
2. Rebalance data automatically
3. No downtime
4. Linear scaling

New capacity: 40 nodes, 133TB, 1.3M inserts/sec
```

**Redis Cluster Scaling**:
```
Current: 10 nodes, 100GB memory

Add Capacity:
1. Add nodes to cluster
2. Reshard hash slots
3. Move data automatically
4. 5-10 minutes process

New capacity: 15 nodes, 150GB memory
```

**BigQuery** (Serverless):
- No manual scaling needed
- Automatic scaling based on query load
- Pay per query (scan cost)
- Slot reservation for predictable pricing

### Multi-Region Deployment

**Active-Active Strategy**:

```
3 Regions: US-East, EU-West, AP-Southeast

User Routing:
- DNS GeoDNS routes to nearest region
- CDN edge servers globally distributed
- WebSocket sticky sessions to region

Data Replication:
- Kafka: Cross-region replication (MirrorMaker)
- ClickHouse: No cross-region (regional data)
- BigQuery: Multi-region dataset
- PostgreSQL: Logical replication to regions

Processing:
- Each region processes its events
- Batch jobs aggregate global data
- Dashboard queries regional + global
```

**Benefits**:
- Low latency (< 50ms) globally
- Regional compliance (GDPR)
- Disaster recovery
- Load distribution

### High Availability

**Failure Scenarios**:

**API Gateway Failure**:
- Load balancer health checks detect failure
- Remove instance from rotation (< 30 seconds)
- Auto-scaling adds replacement
- No data loss (events in Kafka)

**Kafka Broker Failure**:
- Replica brokers take over
- Leader election (< 10 seconds)
- No message loss (replication factor 3)
- Consumers reconnect automatically

**ClickHouse Node Failure**:
- Replica nodes serve queries
- Distributed query redirected
- Write buffered until recovery
- Auto-recovery when node returns

**Redis Node Failure**:
- Redis Cluster: Replica promoted (< 5 seconds)
- Sentinel: Automatic failover
- Clients reconnect automatically
- Cache rebuilt from database

**Region Failure**:
- DNS failover to next nearest region (2-5 minutes)
- Users routed to backup region
- Slightly higher latency
- All data preserved (multi-region replication)

### Monitoring & Alerting

**Key Metrics**:

**System Health**:
- API latency (p50, p95, p99)
- Kafka consumer lag
- Database CPU/memory/disk
- Cache hit ratio
- Error rates

**Business Metrics**:
- Events per second (ingestion rate)
- Active websites (distinct tracking_ids)
- Dashboard queries per second
- Data processing lag (real-time vs actual time)

**Alerts**:

**P0 (Page immediately)**:
- API error rate > 5%
- Kafka consumer lag > 10M messages
- Region unavailable
- Data loss detected

**P1 (Alert on-call)**:
- API latency p95 > 500ms
- Processing lag > 10 minutes
- Database CPU > 90%
- Cache hit ratio < 70%

**P2 (Notify during business hours)**:
- Slow queries detected
- Disk space > 85%
- Unusual traffic patterns

**Dashboards**:
- Real-time ingestion rate
- Processing pipeline health
- Query performance
- Storage utilization
- Cost tracking

### Disaster Recovery

**Backup Strategy**:
```
ClickHouse:
- Daily snapshots to S3
- Retention: 30 days
- Cross-region replication
- Test restore monthly

PostgreSQL:
- Continuous WAL archiving
- Point-in-time recovery (7 days)
- Cross-region replication
- Test restore weekly

Kafka:
- Replication factor: 3
- Cross-region mirrors
- Topic retention: 7 days
- No backup needed (ephemeral)

Redis:
- RDB snapshots every 6 hours
- AOF enabled
- Replicas in other AZs
- Rebuil

d from database if lost
```

**Recovery Procedures**:

**Data Corruption**:
- Restore from snapshot (2-4 hours)
- Replay Kafka messages (if within retention)
- Data loss: Up to last snapshot

**Region Failure**:
- Failover to backup region (2-5 minutes)
- No data loss (multi-region replication)
- Performance impact (higher latency)

**Complete System Failure**:
- Restore from backups (4-8 hours)
- Rebuild aggregations (24-48 hours)
- Data loss: < 1 hour (last backup + Kafka retention)

**RTO** (Recovery Time Objective): 4 hours
**RPO** (Recovery Point Objective): 15 minutes

---

## Trade-offs & Alternatives

### 1. Kafka vs Alternatives

**Kafka** (Chosen):
```
Pros:
+ High throughput (1M+ msg/sec)
+ Durable (disk-backed)
+ Replay capability
+ Exactly-once semantics
+ Consumer groups

Cons:
- Complex to operate
- Higher latency (disk writes)
- Resource intensive

Use when: High volume, need durability, replay
```

**Amazon Kinesis**:
```
Pros:
+ Fully managed
+ Auto-scaling
+ Integration with AWS

Cons:
- More expensive at scale
- 1MB/sec per shard limit
- Vendor lock-in

Use when: AWS-only, moderate volume
```

**Google Pub/Sub**:
```
Pros:
+ Fully managed
+ Global by default
+ At-least-once delivery

Cons:
- Higher cost at scale
- No message ordering
- GCP lock-in

Use when: GCP-only, need global
```

### 2. ClickHouse vs Alternatives

**ClickHouse** (Chosen):
```
Pros:
+ Fastest for analytics
+ Columnar storage
+ SQL interface
+ Great compression

Cons:
- Complex operations
- Limited updates
- No transactions

Use when: High-volume analytics
```

**Apache Druid**:
```
Pros:
+ Real-time ingestion
+ Fast aggregations
+ Horizontal scaling

Cons:
- More memory intensive
- Complex architecture
- Steep learning curve

Use when: Need real-time aggregation
```

**BigQuery**:
```
Pros:
+ Serverless
+ Petabyte scale
+ ML integration

Cons:
- Expensive for high QPS
- Query latency (2-5s)
- Vendor lock-in

Use when: Need serverless, have budget
```

### 3. Synchronous vs Asynchronous Processing

**Asynchronous** (Chosen):
```
Pros:
+ Fast API response (< 100ms)
+ Handles traffic spikes (queue buffers)
+ Retry on failures
+ Independent scaling

Cons:
- Eventual consistency
- More complex architecture
- Harder to debug

Use when: High volume, need resilience
```

**Synchronous**:
```
Pros:
+ Immediate confirmation
+ Simpler architecture
+ Strong consistency
+ Easier debugging

Cons:
- Slow API response (2-3s)
- Can't handle spikes
- Blocks on failures
- Tight coupling

Use when: Low volume, need immediate feedback
```

### 4. Pre-aggregation vs Query-time Aggregation

**Hybrid Approach** (Chosen):
```
Pre-aggregate:
+ Common queries (daily summary)
+ Fast dashboard loading
+ Predictable performance

Query-time:
+ Custom date ranges
+ Ad-hoc queries
+ Flexible dimensions

Use pre-aggregation for 80% of queries
Query-time for 20% of custom queries
```

### 5. Sampling vs No Sampling

**Adaptive Sampling** (Chosen):
```
Small queries (< 1 month):
- No sampling
- 100% accurate

Large queries (> 1 month):
- 1-10% sampling
- 95% confidence
- 10x faster

Trade-off: Slight accuracy loss for speed
Acceptable for long-term trends
Not acceptable for billing/revenue
```

### 6. Self-Hosted vs Managed Services

**Hybrid** (Chosen):
```
Self-hosted:
- Kafka (more control)
- ClickHouse (cost savings)

Managed:
- BigQuery (serverless convenience)
- Redis (Elasticache)
- PostgreSQL (RDS)

Balance: Control vs Operational overhead
```

---

## Conclusion

This Google Analytics design handles **50 billion events per day** with:

**Key Features**:
- Sub-second event ingestion (< 100ms)
- Real-time dashboards (< 5 second delay)
- Historical analysis (14-26 months retention)
- 10M websites supported simultaneously
- 99.9% uptime guarantee

**Core Design Principles**:
1. **Multi-tier storage**: Hot/Warm/Cold for cost optimization
2. **Asynchronous processing**: Queue-based for resilience
3. **Horizontal scaling**: Add capacity by adding nodes
4. **Event streaming**: Kafka for durability and replay
5. **Pre-aggregation**: Speed up common queries
6. **Adaptive sampling**: Balance accuracy vs performance

**Technology Stack**:
- **Message Queue**: Apache Kafka
- **Stream Processing**: Apache Flink
- **Batch Processing**: Apache Spark
- **Time-Series DB**: ClickHouse
- **Real-Time Storage**: Redis
- **Data Warehouse**: BigQuery
- **Metadata DB**: PostgreSQL
- **Cache**: Redis/Memcached

The system scales to **100B+ events/day** by adding more Kafka partitions, Flink workers, and ClickHouse nodes without architectural changes.

**Cost Efficiency**:
- Infrastructure: $1.2M/month
- Revenue: $100M/month
- Cost ratio: 1.2% of revenue

---

**Document Version**: 1.0  
**Last Updated**: November 16, 2025  
**Status**: Complete & Interview-Ready ✅
