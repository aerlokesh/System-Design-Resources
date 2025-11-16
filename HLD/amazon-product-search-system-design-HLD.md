# Amazon Product Search System - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Capacity Estimation](#capacity-estimation)
4. [High-Level Architecture](#high-level-architecture)
5. [Core Components](#core-components)
6. [Database Design](#database-design)
7. [Search Index Design](#search-index-design)
8. [Deep Dives](#deep-dives)
9. [Technology Stack Justification](#technology-stack-justification)
10. [Ranking & Relevance](#ranking--relevance)
11. [Scalability & Performance](#scalability--performance)
12. [Trade-offs & Alternatives](#trade-offs--alternatives)
13. [Interview Questions & Answers](#interview-questions--answers)

---

## Problem Statement

Design a highly scalable, fast, and relevant product search system for Amazon that enables:
- Customers to find products using natural language queries
- Sub-second search response times at massive scale
- Personalized, relevant search results
- Advanced filtering, sorting, and faceting
- Auto-complete and spell correction
- Support for multiple languages and locales
- Real-time index updates for new products and price changes

### Key Challenges
- **Scale**: 300M+ products, 100M+ daily searches
- **Speed**: P99 latency < 200ms for search queries
- **Relevance**: Personalized ranking based on user behavior
- **Freshness**: Real-time inventory and price updates
- **Complexity**: Multi-attribute search (text, category, price, ratings, etc.)
- **Language**: Support 15+ languages with NLP understanding
- **Business logic**: Promotions, sponsored products, A/B testing
- **Availability**: 99.99% uptime (52 minutes/year downtime)

### Scale Requirements
- **300M+ products** in catalog
- **100M daily active users**
- **500M searches per day** (~6K QPS average, 60K peak)
- **P50 latency < 50ms**, **P99 < 200ms**
- **99.99% availability**
- **Support 100K+ concurrent search requests**
- **Real-time index updates** (< 1 minute staleness)

---

## Requirements

### Functional Requirements

#### Must Have (P0)

1. **Text Search**
   - Free-text search across product titles, descriptions, brands
   - Support exact match, fuzzy match, phrase match
   - Boolean operators (AND, OR, NOT)
   - Wildcard search (*phone* matches "iPhone", "smartphone")
   - Synonym expansion ("laptop" → "notebook computer")

2. **Filtering & Faceting**
   - Category filtering (Electronics > Mobile Phones)
   - Price range filters ($50-$100)
   - Brand filtering (Apple, Samsung, Google)
   - Ratings filter (4+ stars)
   - Availability filter (In stock, Prime eligible)
   - Dynamic facet counts (update based on current filters)

3. **Sorting**
   - Relevance (default)
   - Price (low to high, high to low)
   - Customer reviews (highest rated)
   - Newest arrivals
   - Best sellers

4. **Auto-complete**
   - Type-ahead suggestions after 2+ characters
   - Popular queries
   - Product suggestions
   - Category suggestions
   - < 50ms latency

5. **Spell Correction**
   - "Did you mean?" suggestions
   - Auto-correct common misspellings
   - Support for typos (1-2 character edits)

6. **Personalization**
   - Search history influence ranking
   - Browsing history
   - Purchase history
   - Demographic targeting
   - Location-based relevance

7. **Multi-language Support**
   - Search in 15+ languages
   - Language detection
   - Cross-language search (English query → Spanish products)
   - Locale-specific results

8. **Sponsored Products**
   - Ad placements in search results
   - Clearly marked as "Sponsored"
   - Relevance-based ad ranking
   - Click tracking and billing

#### Nice to Have (P1)
- Visual search (search by image)
- Voice search integration
- Query understanding (intent detection)
- Related searches
- Search history and saved searches
- Product recommendations in search results
- Real-time trending searches
- Search analytics dashboard
- A/B testing framework

### Non-Functional Requirements

#### Performance
- **Search latency**: P50 < 50ms, P95 < 100ms, P99 < 200ms
- **Auto-complete latency**: P99 < 50ms
- **Index update latency**: < 1 minute for new products
- **Throughput**: 60K search QPS (peak)
- **Index size**: Support 300M+ products

#### Scalability
- Horizontal scaling for search nodes
- Support 10x traffic during peak (Prime Day, Black Friday)
- Handle 1 billion products in next 5 years
- Support 1M QPS with additional infrastructure

#### Availability
- 99.99% uptime (52 minutes downtime/year)
- Multi-region deployment (US, EU, Asia)
- Graceful degradation (basic search if personalization fails)
- No single point of failure
- Rolling updates with zero downtime

#### Consistency
- **Eventual consistency** for product catalog (5-minute lag acceptable)
- **Strong consistency** for inventory (no overselling)
- **Real-time** for price updates (critical for user trust)

#### Relevance
- **Click-through rate (CTR)**: > 50% for top 3 results
- **Add-to-cart rate**: > 20% from search
- **Null result rate**: < 5% (most searches return results)
- **Spell correction accuracy**: > 90%

#### Security & Compliance
- PII protection (user data encryption)
- Search query logging (compliance, analytics)
- Rate limiting (prevent abuse, scraping)
- DDoS protection
- GDPR compliance (right to be forgotten)

---

## Capacity Estimation

### Assumptions
```
Users:
- Total users: 500M registered
- Daily Active Users (DAU): 100M
- Monthly Active Users (MAU): 300M

Search behavior:
- Searches per user per day: 5
- Total searches per day: 500M
- Peak multiplier: 12x (Prime Day, holidays)

Products:
- Total products: 300M
- Active products (in stock): 200M
- New products per day: 100K
- Price updates per day: 10M
- Inventory updates: Real-time (100K/sec during peak)

Search queries:
- Average query length: 3-5 words (20-50 characters)
- Results per page: 48 products
- Average pagination depth: 2 pages
```

### Traffic Estimates

**Search Queries**:
```
Daily searches: 500M
Searches per second (average): 500M / 86400 = 5,787 QPS
Peak (12x during events): 5,787 × 12 = 69,444 QPS ≈ 70K QPS

By type:
- Text search: 80% = 400M/day = 4,630 QPS (56K peak)
- Auto-complete: 15% = 75M/day = 868 QPS (10K peak)
- Filter/sort only: 5% = 25M/day = 289 QPS (3.5K peak)

Geographic distribution:
- US: 50% = 35K QPS peak
- EU: 30% = 21K QPS peak  
- Asia: 15% = 10.5K QPS peak
- Other: 5% = 3.5K QPS peak
```

**Index Update Operations**:
```
Product creates: 100K/day = 1.2 updates/sec
Product updates: 10M/day = 116 updates/sec
Inventory updates: 100K/sec (peak, real-time)
Price updates: 10M/day = 116 updates/sec

Total index updates: ~200 updates/sec average
Peak: 100K updates/sec (inventory during flash sales)
```

**Data Transfer**:
```
Per search request:
- Query: 50 bytes
- Request headers: 500 bytes
- Total inbound: 550 bytes

Per search response:
- 48 products × 2KB (title, image, price, rating) = 96KB
- Facets: 5KB
- Metadata: 1KB
- Total outbound: 102KB per search

Daily bandwidth:
Inbound: 500M × 550 bytes = 275 GB/day = 3.2 MB/sec
Outbound: 500M × 102KB = 51 TB/day = 590 MB/sec

Peak bandwidth (12x):
Inbound: 38 MB/sec
Outbound: 7 GB/sec
```

### Storage Estimates

**Product Catalog (Source of Truth)**:
```
Per product:
{
  product_id: 16 bytes (UUID)
  title: 200 bytes
  description: 2000 bytes
  brand: 50 bytes
  category: 100 bytes (hierarchy)
  price: 8 bytes (decimal)
  currency: 3 bytes
  images: 500 bytes (URLs)
  specifications: 1000 bytes (JSON)
  ratings: 50 bytes (avg, count)
  inventory: 20 bytes
  seller_info: 200 bytes
  metadata: 300 bytes
}
Total per product: ~4.5 KB

Total products: 300M
Storage: 300M × 4.5KB = 1.35 TB
With historical data + images metadata: 5 TB
With replication (3x): 15 TB
```

**Search Index (Elasticsearch)**:
```
Per product in index:
- Inverted index (text fields): 3KB
- Doc values (sorting/aggregations): 1KB
- Stored fields (source): 2KB
Total per document: 6KB

Active products: 200M
Index size: 200M × 6KB = 1.2 TB
With replicas (2x): 2.4 TB
With overhead (30%): 3.1 TB per region

3 regions: 9.3 TB total
```

**Auto-complete Index**:
```
Unique query strings: 100M
Per query:
- Query string: 50 bytes
- Frequency count: 4 bytes
- User count: 4 bytes
- Trending score: 4 bytes
Total: 62 bytes

Storage: 100M × 62 bytes = 6.2 GB
With replication: 18.6 GB (negligible)
```

**User Personalization Data**:
```
Per user:
- User ID: 16 bytes
- Search history (last 100): 5KB
- Browse history: 10KB
- Purchase history: 5KB
- Preferences: 2KB
Total: 22KB

Active users: 100M
Storage: 100M × 22KB = 2.2 TB
With replication: 6.6 TB
```

**Click/Interaction Logs**:
```
Per interaction:
- User ID: 16 bytes
- Query: 50 bytes
- Product ID: 16 bytes
- Timestamp: 8 bytes
- Action type: 10 bytes
- Session ID: 16 bytes
- Metadata: 50 bytes
Total: 166 bytes

Daily interactions: 500M searches × 3 clicks avg = 1.5B interactions
Storage per day: 1.5B × 166 bytes = 249 GB/day
Monthly: 7.5 TB
Annual: 90 TB (archived to S3 after 30 days)
```

**Total Storage**:
```
Product catalog: 15 TB
Search index: 9.3 TB
User data: 6.6 TB
Auto-complete: 0.02 TB
Logs (hot - 30 days): 7.5 TB
Logs (cold - S3): 900 TB (10 years)
────────────────────────────
Total hot storage: ~40 TB
Total cold storage: ~900 TB
```

### Memory Requirements

**Search Nodes (Elasticsearch)**:
```
JVM heap: 50% of system memory (recommended)
Per node: 64GB RAM (32GB heap)

Memory usage:
- Index data structures: 20GB
- Query cache: 5GB
- Field data cache: 5GB
- OS file cache: 32GB (remaining)

Nodes per region: 30 nodes
Total memory per region: 30 × 64GB = 1.92 TB
3 regions: 5.76 TB total
```

**Cache (Redis)**:
```
Popular queries cache: 10GB
User session cache: 50GB
Product metadata cache: 20GB
Auto-complete cache: 2GB
Total per region: 82GB

With replication (2x): 164GB per region
3 regions: 492GB total
```

### Infrastructure Estimates

**Search Cluster (Elasticsearch)**:
```
Per node specs:
- CPU: 16 cores
- Memory: 64GB
- Storage: 2TB NVMe SSD
- Network: 10 Gbps

Nodes per region:
- Data nodes: 30 (handle queries and store data)
- Master nodes: 3 (cluster coordination)
- Coordinating nodes: 10 (route requests)
Total per region: 43 nodes

3 regions: 129 nodes total

Query capacity:
- Per data node: 200 QPS
- 30 nodes × 200 = 6,000 QPS per region
- 3 regions: 18,000 QPS
- Peak capacity (with headroom): 70K QPS ✓
```

**API Servers**:
```
Handle request routing, authentication, business logic

Per server capacity: 5,000 QPS
Peak load: 70K QPS
Servers needed: 70K / 5K = 14 servers per region
With redundancy (2x): 28 servers per region
3 regions: 84 servers total

Per server specs:
- CPU: 8 cores
- Memory: 16GB
- Technology: Go/Java
```

**Ranking Service**:
```
ML-based ranking and personalization

Per server: 10,000 ranking operations/sec
Peak ranking requests: 70K QPS
Servers needed: 7 servers per region
With redundancy (2x): 14 servers per region
3 regions: 42 servers total

Per server specs:
- CPU: 16 cores (ML inference)
- Memory: 32GB
- GPU: Optional (NVIDIA T4) for deep learning models
```

**Kafka Cluster (Events & Logs)**:
```
Message throughput:
- Click events: 5,787 events/sec (avg), 70K peak
- Inventory updates: 100K events/sec peak
- Price updates: 116 events/sec

Per broker: 50K events/sec
Brokers per region: 6 brokers
3 regions: 18 brokers total

Per broker specs:
- CPU: 8 cores
- Memory: 32GB
- Storage: 4TB SSD
```

**Database (PostgreSQL) - Product Catalog**:
```
Primary + 5 read replicas per region
3 regions: 18 instances total

Per instance specs:
- CPU: 16 cores
- Memory: 128GB
- Storage: 10TB SSD
- IOPS: 50K
```

### Cost Estimation (AWS)

```
PER REGION COSTS:

Elasticsearch Cluster:
- 30 × i3.4xlarge (data): $37,440/month
- 3 × m5.large (master): $273/month
- 10 × c5.2xlarge (coordinating): $3,060/month
Subtotal: $40,773/month

API Servers:
- 28 × c5.2xlarge: $8,568/month

Ranking Service:
- 14 × c5.4xlarge: $10,584/month

Kafka Cluster:
- 6 × m5.2xlarge: $3,672/month

PostgreSQL:
- 1 × db.r5.4xlarge (primary): $3,744/month
- 5 × db.r5.2xlarge (replicas): $9,360/month
Subtotal: $13,104/month

Redis Cache:
- 2 × cache.r5.2xlarge: $1,260/month

Load Balancers: $500/month
Data Transfer: $10,000/month
S3 (logs, backups): $2,000/month

────────────────────────────────
PER REGION TOTAL: $90,521/month

3 REGIONS: $271,563/month
+ Monitoring, logging: $10,000/month
+ CloudFront CDN: $5,000/month
────────────────────────────────
MONTHLY TOTAL: ~$286,000/month

ANNUAL TOTAL: ~$3.4M/year
```

**Cost Optimization Strategies**:
```
1. Reserved Instances (1-year): 30% savings = $200K/month
2. Spot Instances (non-critical workers): 10% savings = $180K/month
3. S3 Intelligent Tiering (logs): 20% savings = $178K/month
4. Right-sizing instances: 10% savings = $160K/month

Optimized cost: ~$160-180K/month ($2M/year)
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                                  │
│  [Mobile Apps]  [Web Browser]  [Alexa]  [Third-party Apps]           │
└──────────────────────┬─────────────────────────────────────────────────┘
                       │
                       │ HTTPS / HTTP/2
                       ↓
┌────────────────────────────────────────────────────────────────────────┐
│                   CDN & EDGE LAYER (CloudFront)                        │
│  - Static content caching (images, CSS, JS)                            │
│  - DDoS protection (AWS Shield)                                        │
│  - SSL/TLS termination                                                 │
│  - Geographic distribution                                             │
└──────────────────────┬─────────────────────────────────────────────────┘
                       │
            ┌──────────┴─────────┬──────────────────┐
            │                    │                  │
         US-EAST              EU-WEST           AP-SOUTH
         Region               Region             Region
            │                    │                  │
            └────────────────────┼──────────────────┘
                                 ↓
┌────────────────────────────────────────────────────────────────────────┐
│                    LOAD BALANCER (Application LB)                      │
│  - Path routing: /search → Search API                                 │
│                  /autocomplete → Autocomplete API                      │
│  - Health checks                                                       │
│  - Connection pooling                                                  │
│  - SSL offloading                                                      │
└──────────────────────┬─────────────────────────────────────────────────┘
                       │
            ┌──────────┴────────┬───────────────┐
            │                   │                │
            ↓                   ↓                ↓
┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐
│  API Gateway    │  │  API Gateway    │  │  API Gateway     │
│  (Kong/AWS AG)  │  │  (Kong/AWS AG)  │  │  (Kong/AWS AG)   │
│  ─────────────  │  │  ─────────────  │  │  ──────────────  │
│  - Auth (JWT)   │  │  - Auth (JWT)   │  │  - Auth (JWT)    │
│  - Rate limit   │  │  - Rate limit   │  │  - Rate limit    │
│  - Validation   │  │  - Validation   │  │  - Validation    │
│  - Logging      │  │  - Logging      │  │  - Logging       │
│  - Metrics      │  │  - Metrics      │  │  - Metrics       │
└────────┬────────┘  └────────┬────────┘  └────────┬─────────┘
         │                    │                     │
         └────────────────────┼─────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                      │
    Search API          Autocomplete API      Indexing API
        │                     │                      │
        ↓                     ↓                      ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                          API SERVICE LAYER                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐ │
│  │  Search Service  │  │ Autocomplete Svc │  │  Indexing Service    │ │
│  │  ──────────────  │  │  ──────────────  │  │  ──────────────────  │ │
│  │ - Query parsing  │  │ - Prefix search  │  │ - Product ingestion  │ │
│  │ - Spell check    │  │ - Popular query  │  │ - Index updates      │ │
│  │ - Query rewrite  │  │ - Suggestion     │  │ - Bulk operations    │ │
│  │ - Result fetch   │  │ - Completion     │  │ - Schema mgmt        │ │
│  │ - Ranking call   │  │ - Cache lookup   │  │ - Reindexing         │ │
│  │ - Result format  │  └──────────────────┘  └──────────────────────┘ │
│  └──────────┬───────┘                                                  │
└─────────────┼──────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                       RANKING & PERSONALIZATION LAYER                   │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  Ranking Service (ML-based)                                       │ │
│  │  ────────────────────────────────────────────────────────────    │ │
│  │  Input: Search results + User context                             │ │
│  │  Models:                                                           │ │
│  │  • Learning to Rank (LTR): GBDT, Neural Networks                  │ │
│  │  • Personalization: User embeddings, Item embeddings              │ │
│  │  • CTR prediction: Deep learning models                           │ │
│  │  • Diversity: Result diversification                              │ │
│  │  Output: Re-ranked results                                        │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────┬───────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                       SEARCH CLUSTER (Elasticsearch)                    │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  Elasticsearch/OpenSearch Cluster (30 data nodes per region)     │ │
│  │  ───────────────────────────────────────────────────────────────  │ │
│  │  Indices:                                                          │ │
│  │  • products-en-us: English products (US locale)                   │ │
│  │  • products-es-es: Spanish products (ES locale)                   │ │
│  │  • products-autocomplete: Auto-complete index                     │ │
│  │  • products-spell: Spell correction dictionary                    │ │
│  │                                                                    │ │
│  │  Sharding: 15 primary shards × 2 replicas = 45 shard copies      │ │
│  │  Routing: Route by category for better performance                │ │
│  │  Refresh: 1 second (near real-time)                              │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────┬───────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                          CACHE LAYER (Redis Cluster)                    │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  Redis Cluster: 10 nodes × 2 replicas = 20 nodes total           │ │
│  │  ───────────────────────────────────────────────────────────────  │ │
│  │  Keys:                                                             │ │
│  │  • search:query:{query_hash} → Cached results (TTL: 5 min)       │ │
│  │  • autocomplete:{prefix} → Suggestions (TTL: 1 hour)             │ │
│  │  • user:{user_id}:history → Search history (TTL: 30 days)        │ │
│  │  • product:{product_id}:summary → Quick product info             │ │
│  │  • trending:queries → Popular searches (TTL: 15 min)             │ │
│  │  • spell:{word} → Spell correction cache                          │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────┬───────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                      MESSAGE QUEUE (Apache Kafka)                       │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  Topics (6 brokers, RF=3):                                        │ │
│  │  • product-updates: New products, updates (100 partitions)        │ │
│  │  • inventory-updates: Stock changes (200 partitions)              │ │
│  │  • price-updates: Price changes (100 partitions)                  │ │
│  │  • click-events: User interactions (300 partitions)               │ │
│  │  • search-analytics: Query logs (100 partitions)                  │ │
│  │                                                                    │ │
│  │  Consumers:                                                        │ │
│  │  • Indexer: Updates Elasticsearch in near real-time               │ │
│  │  • Analytics: Processes events for ML training                    │ │
│  │  • Ranking: Updates ranking models                                │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└─────────────┬───────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                           DATABASE LAYER                                │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────┐   │
│  │  Product DB      │  │  User DB         │  │  Analytics DB      │   │
│  │  (PostgreSQL)    │  │  (PostgreSQL)    │  │  (ClickHouse)      │   │
│  │  ──────────────  │  │  ──────────────  │  │  ────────────────  │   │
│  │ Primary + 5 RR   │  │ Primary + 3 RR   │  │ Distributed        │   │
│  │                  │  │                  │  │                    │   │
│  │ Tables:          │  │ Tables:          │  │ Tables:            │   │
│  │ • products       │  │ • users          │  │ • search_queries   │   │
│  │ • categories     │  │ • preferences    │  │ • click_events     │   │
│  │ • brands         │  │ • search_history │  │ • impressions      │   │
│  │ • inventory      │  │ • cart_history   │  │ • conversions      │   │
│  │ • prices         │  │ • purchase_hist  │  │ • metrics          │   │
│  │                  │  │                  │  │                    │   │
│  │ Sharded by:      │  │ Sharded by:      │  │ Partitioned by:    │   │
│  │ product_id       │  │ user_id          │  │ date               │   │
│  └──────────────────┘  └──────────────────┘  └────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                      OBJECT STORAGE (S3)                                │
│  • ML training data (historical search logs)                            │
│  • Model artifacts (trained ranking models)                             │
│  • Product images (thumbnails, full-size)                              │
│  • Backup data (database snapshots, index backups)                      │
│  • Analytics exports (monthly/quarterly reports)                        │
└─────────────────────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                    MONITORING & OBSERVABILITY                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────┐ │
│  │ Prometheus   │  │  ELK Stack   │  │  Jaeger/X-Ray                │ │
│  │ + Grafana    │  │  ──────────  │  │  ──────────────────────────  │ │
│  │ ──────────   │  │ - Query logs │  │ - Distributed tracing        │ │
│  │ - Metrics    │  │ - Click logs │  │ - Latency breakdown          │ │
│  │ - Alerts     │  │ - Error logs │  │ - Service dependencies       │ │
│  │ - Dashboards │  │ - Analytics  │  │ - Performance bottlenecks    │ │
│  └──────────────┘  └──────────────┘  └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. Search Service

**Purpose**: Handle search queries and return relevant results

**Responsibilities**:
1. Parse and validate search query
2. Check cache for recent results
3. Apply spell correction if needed
4. Query Elasticsearch with filters
5. Call Ranking Service for personalization
6. Format and return results

**Technology Choice**: **Go (Golang)**

**Why Go?**
```
Requirements:
- High throughput: 70K QPS peak
- Low latency: < 50ms P50
- Concurrent request handling
- Memory efficiency
- Fast startup (for auto-scaling)

Go:
✓ Concurrency: Goroutines (lightweight threads)
✓ Performance: Compiled, near C++ speed
✓ Memory: Low overhead (~25MB baseline)
✓ Deployment: Single binary, easy containers
✓ Libraries: Excellent Elasticsearch, Redis clients
✓ Latency: Predictable GC pauses < 1ms

Alternatives:

Java/Spring Boot:
✗ Memory: 100MB+ per instance
✗ Startup: 10-30 seconds
✗ GC pauses: 10-100ms (problematic)
✓ Ecosystem: Rich libraries
✓ Maturity: Well-tested

Node.js:
✗ Single-threaded: CPU-bound bottleneck
✗ Performance: 3-5x slower than Go
✓ Async I/O: Good for I/O-bound
✗ Type safety: Requires TypeScript

Python:
✗ Performance: 10x slower than Go
✗ GIL: Multi-threading limited
✗ Memory: High overhead
✓ ML integration: Easy

Winner: Go (performance + concurrency + low latency)
```

**Request Flow**:
```
1. Receive HTTP request
   - Path: /api/v1/search?q=iphone&category=electronics
   - Headers: Authorization, Accept-Language
   - Latency budget: 50ms total

2. Authentication & validation (5ms)
   - Verify JWT token
   - Extract user_id
   - Check rate limit (Redis)
   - Validate query parameters

3. Cache lookup (2ms)
   - Generate cache key: hash(query + filters + user_id)
   - Check Redis: GET search:query:{hash}
   - If hit: Return cached results (exit early)
   - Cache hit rate: 40-50% for popular queries

4. Query preprocessing (3ms)
   - Tokenization: "iphone 13 pro" → [iphone, 13, pro]
   - Stopword removal: Remove "the", "a", "an"
   - Synonym expansion: "phone" → ["phone", "smartphone", "mobile"]
   - Spell check: Check against dictionary
   
5. Elasticsearch query (25ms)
   - Build query DSL
   - Add filters (category, price, brand)
   - Add aggregations (facets)
   - Execute query
   - Retrieve top 100 results

6. Ranking service call (10ms)
   - Send results + user context
   - ML model inference
   - Re-rank based on personalization
   - Return top 48 results

7. Response formatting (3ms)
   - Add product images
   - Calculate facet counts
   - Add sponsored products (ads)
   - Format JSON response

8. Cache store (2ms)
   - Store in Redis with TTL: 5 minutes
   - Key: search:query:{hash}

Total: 50ms (P50), 100ms (P95), 200ms (P99)
```

### 2. Elasticsearch Cluster

**Purpose**: Distributed search and analytics engine

**Why Elasticsearch Over Alternatives?**

```
Requirements:
- Full-text search with relevance scoring
- Real-time indexing (< 1 minute)
- Horizontal scalability
- Advanced filtering and aggregations
- Multi-language support
- 200M+ documents, 70K QPS

Elasticsearch/OpenSearch:
✓ Inverted index: Optimized for text search
✓ Near real-time: 1-second refresh
✓ Distributed: Automatic sharding
✓ Relevance: TF-IDF, BM25 scoring
✓ Aggregations: Faceted search built-in
✓ Scale: Linear with nodes
✓ Language: 30+ analyzers
✓ Performance: 200 QPS per node

Solr:
✓ Similar to Elasticsearch
✓ Mature: Older, battle-tested
✗ Cloud-native: Less container-friendly
✗ Learning curve: Steeper
✗ Real-time: Harder to configure

Algolia (SaaS):
✓ Managed: No ops
✓ Fast: Edge network
✓ Easy: Simple API
✗ Cost: $2 per 1K searches = $1M/month!
✗ Control: Limited customization
✗ Data residency: Compliance issues

PostgreSQL Full-Text:
✓ ACID: Transactions
✓ Simple: One system
✗ Scale: Vertical only
✗ Performance: 1K QPS max
✗ Features: Limited relevance tuning
✗ Ops: Not designed for search

DynamoDB + CloudSearch:
✓ Managed: AWS handles ops
✗ Cost: High at this scale
✗ Flexibility: Limited query options
✗ Real-time: Indexing lag

Winner: Elasticsearch (features + performance + scalability + cost)
```

**Cluster Configuration**:
```
Per Region Setup:

Master Nodes (3):
- Role: Cluster coordination, shard allocation
- No data, no queries
- Specs: 4 cores, 16GB RAM
- Why 3: Quorum (2 of 3 must agree)

Data Nodes (30):
- Role: Store data, execute queries
- Specs: 16 cores, 64GB RAM (32GB JVM heap)
- Storage: 2TB NVMe SSD per node
- Total capacity: 60TB raw, 20TB usable (with replicas)

Coordinating Nodes (10):
- Role: Route requests, merge results
- No data stored
- Specs: 8 cores, 32GB RAM
- Why separate: Offload work from data nodes

Total per region: 43 nodes
3 regions: 129 nodes

Sharding Strategy:
- Primary shards: 15 (allows 15-way parallelism)
- Replicas: 2 (total 3 copies for durability)
- Shard size: 30-50GB (optimal for performance)
- Total shards: 15 primary + 30 replicas = 45 shard copies

Why 15 shards:
- Too few (5): Limited parallelism, large shards
- Too many (100): Overhead, slow cluster state
- 15: Sweet spot for 200M documents
```

**Index Design**:
```
products-en-us (Main index):
{
  "settings": {
    "number_of_shards": 15,
    "number_of_replicas": 2,
    "refresh_interval": "1s",
    "max_result_window": 10000,
    "analysis": {
      "analyzer": {
        "product_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "stop", "synonym", "stemmer"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "product_id": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "product_analyzer",
        "fields": {
          "keyword": {"type": "keyword"},
          "ngram": {"type": "text", "analyzer": "ngram_analyzer"}
        }
      },
      "description": {"type": "text", "analyzer": "product_analyzer"},
      "brand": {"type": "keyword"},
      "category": {
        "type": "keyword",
        "fields": {
          "tree": {"type": "text", "analyzer": "path_hierarchy"}
        }
      },
      "price": {"type": "scaled_float", "scaling_factor": 100},
      "rating": {"type": "half_float"},
      "review_count": {"type": "integer"},
      "in_stock": {"type": "boolean"},
      "prime_eligible": {"type": "boolean"},
      "created_at": {"type": "date"},
      "popularity_score": {"type": "float"}
    }
  }
}

Why these types:
- keyword: Exact match, faceting (brand, category)
- text: Full-text search (title, description)
- scaled_float: Space-efficient for prices
- half_float: Sufficient precision for ratings
- boolean: In-stock, Prime
```

**Query Example**:
```
Search: "iphone 13 pro max"
Filters: category=Electronics, price=$900-$1200, rating>=4

Elasticsearch query:
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "iphone 13 pro max",
            "fields": ["title^3", "description^1", "brand^2"],
            "type": "best_fields",
            "tie_breaker": 0.3
          }
        }
      ],
      "filter": [
        {"term": {"category": "Electronics"}},
        {"range": {"price": {"gte": 900, "lte": 1200}}},
        {"range": {"rating": {"gte": 4}}},
        {"term": {"in_stock": true}}
      ]
    }
  },
  "aggs": {
    "brands": {
      "terms": {"field": "brand", "size": 20}
    },
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          {"to": 500},
          {"from": 500, "to": 1000},
          {"from": 1000}
        ]
      }
    }
  },
  "size": 100,
  "from": 0,
  "sort": [
    {"_score": "desc"},
    {"popularity_score": "desc"}
  ]
}

Result: 45ms (includes network, parsing, scoring, aggregations)
```

### 3. Ranking Service

**Purpose**: Personalize and re-rank search results using ML

**Why Separate Ranking Service?**
```
Option 1: Elasticsearch-only ranking
Pros:
+ Simple: One system
+ Fast: No network hop
Cons:
- Limited ML: Basic TF-IDF, BM25
- No personalization: Same results for all users
- Hard to iterate: Deploy ES for model updates

Option 2: Separate Ranking Service (Chosen)
Pros:
+ Advanced ML: GBDT, neural networks
+ Personalization: User-specific features
+ A/B testing: Easy to experiment
+ Independent scaling: Scale ranking separately
+ Model updates: Deploy without touching ES
Cons:
- Complexity: Additional service
- Latency: 10ms added
- Cost: 42 servers

Winner: Separate service (business value >> cost)
```

**Ranking Features** (300+ features):
```
Query Features:
- Query length
- Query type (brand, category, long-tail)
- Query popularity
- Query intent (navigational, informational, transactional)

Product Features:
- Title relevance score (from ES)
- Description match
- Brand popularity
- Category relevance
- Price competitiveness
- Rating (average)
- Review count
- Images quality score
- Availability (in stock, Prime)
- Seller rating

User Features:
- Search history (last 100 queries)
- Click history (CTR per product category)
- Purchase history (category preferences)
- Cart additions
- Wishlist items
- Demographics (age, location)
- Device type (mobile, desktop)
- Time of day, day of week

Interaction Features:
- Historical CTR for (query, product) pair
- Conversion rate
- Time on product page
- Add-to-cart rate
- Purchase rate

Context Features:
- Session length
- Previous queries in session
- Page number (pagination depth)
- Sort order selected
- Filters applied
```

**ML Models**:
```
Stage 1: Candidate Generation (Elasticsearch)
- Input: Query + filters
- Output: Top 100 products
- Latency: 25ms
- Algorithm: BM25 + filters

Stage 2: Learning to Rank (LTR)
- Input: 100 products + 300 features
- Output: Scores for each product
- Latency: 8ms
- Algorithm: Gradient Boosted Trees (XGBoost/LightGBM)
- Model size: 100MB
- Training: Weekly on 30 days of data
- Objective: Optimize for clicks + purchases

Stage 3: Re-ranking & Diversity
- Input: Scored products
- Output: Top 48 products (diverse)
- Latency: 2ms
- Algorithm: Maximal Marginal Relevance (MMR)
- Goal: Avoid showing 48 similar products

Total latency: 35ms
```

**Model Training Pipeline**:
```
Data Collection (Kafka):
- Search queries: 500M/day
- Clicks: 1.5B/day
- Add-to-cart: 100M/day
- Purchases: 20M/day

Feature Engineering (Spark):
- Join events with user/product data
- Aggregate historical features
- Compute interaction features
- Output: 10TB training data

Model Training (SageMaker):
- Algorithm: LightGBM
- Features: 300
- Training data: Last 30 days
- Validation: Last 7 days
- Objective: Weighted clicks + purchases
- Training time: 6 hours
- Frequency: Weekly

Model Deployment:
- Export model to S3
- Deploy to ranking service (rolling update)
- A/B test: 10% traffic to new model
- Monitor metrics: CTR, conversion, revenue
- Gradual rollout: 10% → 50% → 100%
```

### 4. Auto-complete Service

**Purpose**: Provide real-time query suggestions

**Why Separate Index?**
```
Option 1: Same index as products
- Query products index with prefix match
- Problem: Slow (50-100ms), expensive

Option 2: Separate auto-complete index (Chosen)
- Dedicated index optimized for prefix search
- Data: Popular queries, products, categories
- Size: 100M entries vs 200M products
- Latency: < 10ms
- Cost: Negligible (6GB data)

Winner: Separate index (10x faster, minimal cost)
```

**Auto-complete Index Design**:
```
Index: autocomplete-en-us
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 2,
    "analysis": {
      "analyzer": {
        "autocomplete_analyzer": {
          "tokenizer": "standard",
          "filter": ["lowercase", "edge_ngram"]
        }
      },
      "filter": {
        "edge_ngram": {
          "type": "edge_ngram",
          "min_gram": 2,
          "max_gram": 20
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "text": {
        "type": "text",
        "analyzer": "autocomplete_analyzer",
        "search_analyzer": "standard"
      },
      "type": {
        "type": "keyword"  // "query", "product", "category"
      },
      "popularity": {"type": "integer"},
      "weight": {"type": "float"}
    }
  }
}

How it works:
Input: "iph"
Index contains: "iphone" tokenized as ["ip", "iph", "ipho", "iphon", "iphone"]
Match: "iph" matches "iph" token
Return: "iphone" with popularity score

Latency: 5-10ms
```

**Suggestion Ranking**:
```
Factors:
1. Popularity: How often queried (70% weight)
2. Personalization: User's past queries (20% weight)
3. Trending: Recent spike in searches (10% weight)

Algorithm:
score = 0.7 × log(popularity) + 
        0.2 × personalization_score + 
        0.1 × trending_score

Top 10 suggestions returned
```

### 5. Indexing Service

**Purpose**: Keep Elasticsearch in sync with product catalog

**Why Kafka for Change Data Capture?**
```
Requirements:
- Real-time updates (< 1 minute lag)
- No data loss (durability)
- Handle burst (100K updates/sec)
- Order preservation per product
- Replay capability (reindex if needed)

Kafka:
✓ Throughput: 1M+ msg/sec
✓ Durability: Disk-backed, replicated
✓ Ordering: Per partition key (product_id)
✓ Replay: Consumer offset management
✓ Scalability: Add partitions/brokers

Alternatives:

RabbitMQ:
✗ Throughput: 100K msg/sec (10x less)
✗ Replay: Limited

AWS SQS:
✗ Ordering: FIFO limited (3K msg/sec)
✗ Cost: $0.40/M = $3.5M/month

Database triggers:
✗ Performance: Blocks transactions
✗ Reliability: No retry logic
✗ Scalability: Coupled to DB

Winner: Kafka (all requirements met)
```

**Indexing Flow**:
```
1. Product Update in PostgreSQL
   - New product created
   - Price changed
   - Inventory updated
   - Change captured by Debezium (CDC)

2. Publish to Kafka
   - Topic: product-updates
   - Partition key: product_id (ensures ordering)
   - Message: {product_id, change_type, data}
   - Latency: < 10ms

3. Indexer Consumer
   - Subscribe to product-updates topic
   - Consumer group: indexer-group
   - Parallel consumers: 20 (match partition count)
   - Process rate: 10K updates/sec per consumer

4. Batch Updates to Elasticsearch
   - Accumulate 1000 documents OR 1 second
   - Bulk API: Index 1000 docs in single request
   - Latency: 100ms per batch
   - Throughput: 10K docs/sec per consumer

5. Refresh Index
   - Automatic refresh: Every 1 second
   - Makes documents searchable
   - Near real-time: < 1 second lag

Total latency: Product update → Searchable in < 1 minute
```

**Handling Failures**:
```
Scenario 1: Elasticsearch node failure
- Replica shards serve queries
- No downtime
- Auto-recovery when node returns

Scenario 2: Indexer consumer crash
- Kafka retains messages
- New consumer joins group
- Reprocesses from last committed offset
- No data loss

Scenario 3: Bulk index failure
- Retry with exponential backoff
- Max 3 retries
- If still fails: Dead letter queue (DLQ)
- Alert on-call engineer
- Manual investigation

Scenario 4: Elasticsearch cluster down
- Queue builds up in Kafka
- Retention: 7 days (enough time to recover)
- Once cluster up: Resume indexing
- Catch-up time: 2-4 hours
```

---

## Database Design

### PostgreSQL - Product Catalog

**Why PostgreSQL?**
```
Requirements:
- ACID transactions (inventory, pricing)
- Complex queries (joins, aggregations)
- Strong consistency
- Mature ecosystem

PostgreSQL:
✓ ACID: Full transactions
✓ SQL: Rich query language
✓ JSON: JSONB for flexible data
✓ Performance: 50K TPS
✓ Replication: Streaming, logical
✓ Extensions: Full-text, PostGIS

MySQL:
✓ Similar to PostgreSQL
✗ JSON: Less feature-rich
✗ Extensions: Fewer options

NoSQL (MongoDB, DynamoDB):
✗ Transactions: Limited
✗ Joins: Application-side
✗ Consistency: Eventually consistent
✓ Scale: Horizontal

Winner: PostgreSQL (ACID + SQL + maturity)
```

**Schema Design**:
```
products table:
- product_id (UUID, PRIMARY KEY)
- title (VARCHAR(500), indexed)
- description (TEXT)
- brand_id (INT, FOREIGN KEY)
- category_id (INT, FOREIGN KEY)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)
- attributes (JSONB) - flexible schema

Indexes:
- products_title_idx (GIN index for full-text)
- products_brand_idx (B-tree)
- products_category_idx (B-tree)
- products_updated_idx (for CDC)

Sharding strategy:
- Shard by product_id (hash-based)
- 16 shards (mod 16)
- Each shard: 18.75M products

brands table:
- brand_id (INT, PRIMARY KEY)
- name (VARCHAR(100), UNIQUE)
- logo_url (VARCHAR(500))

categories table:
- category_id (INT, PRIMARY KEY)
- name (VARCHAR(100))
- parent_id (INT, FOREIGN KEY, self-reference)
- path (VARCHAR(1000)) - materialized path

inventory table:
- product_id (UUID, PRIMARY KEY)
- quantity (INT, CHECK >= 0)
- warehouse_id (INT)
- updated_at (TIMESTAMP)

Sharded with products

prices table:
- product_id (UUID)
- effective_date (DATE)
- price (DECIMAL(10,2))
- currency (CHAR(3))
- PRIMARY KEY (product_id, effective_date)

Historical prices for price tracking

product_images table:
- image_id (UUID, PRIMARY KEY)
- product_id (UUID, FOREIGN KEY)
- url (VARCHAR(500))
- is_primary (BOOLEAN)
- order (INT)

Multiple images per product
```

### ClickHouse - Analytics

**Why ClickHouse?**
```
Requirements:
- OLAP workload (analytics, aggregations)
- High insert rate (1.5B events/day)
- Fast queries on billions of rows
- Time-series data (search logs, clicks)

ClickHouse:
✓ Column-store: Fast aggregations
✓ Compression: 10-100x compression ratio
✓ Performance: Billions of rows in seconds
✓ Scalability: Distributed queries
✓ SQL: Familiar query language

Alternatives:

PostgreSQL:
✗ Performance: Row-store, slow aggregations
✗ Scale: Billions of rows problematic

Amazon Redshift:
✓ Similar to ClickHouse
✗ Cost: 2-3x more expensive
✗ Latency: Higher

Apache Druid:
✓ Real-time analytics
✗ Complexity: Harder to operate
✗ SQL: Limited support

Winner: ClickHouse (performance + cost + SQL)
```

**Schema Design**:
```
search_queries table:
- query_id (UUID)
- user_id (UUID)
- query_text (String)
- timestamp (DateTime)
- results_count (UInt32)
- latency_ms (UInt16)
- clicked (Bool)
- purchased (Bool)

Engine: MergeTree
Partition by: toYYYYMM(timestamp)
Order by: (timestamp, user_id)

Retention: 2 years
Daily inserts: 500M
Compression ratio: 20x

click_events table:
- event_id (UUID)
- query_id (UUID)
- user_id (UUID)
- product_id (UUID)
- position (UInt8)
- timestamp (DateTime)

Engine: MergeTree
Partition by: toYYYYMM(timestamp)
Order by: (timestamp, query_id, product_id)

Retention: 1 year
Daily inserts: 1.5B
```

**Query Examples**:
```
Daily search volume by category:
SELECT 
  category,
  count() as searches,
  avg(latency_ms) as avg_latency
FROM search_queries
WHERE timestamp >= today() - 7
GROUP BY category
ORDER BY searches DESC
LIMIT 10

Query time: 100ms on 3.5B rows

Click-through rate by position:
SELECT 
  position,
  count() as impressions,
  countIf(clicked) as clicks,
  clicks * 100.0 / impressions as ctr
FROM click_events
WHERE timestamp >= today() - 30
GROUP BY position
ORDER BY position

Query time: 200ms on 45B rows

Top searched products:
SELECT 
  product_id,
  count() as search_count
FROM click_events
WHERE timestamp >= today() - 1
GROUP BY product_id
ORDER BY search_count DESC
LIMIT 100

Query time: 50ms on 1.5B rows
```

---

## Search Index Design

### Elasticsearch Index Strategy

**Multi-Index vs Single Index**:
```
Option 1: Single index for all products
Pros:
+ Simple: One index to manage
+ Cross-locale search: Easy
Cons:
- Size: 300M docs, 3TB index
- Performance: Slower queries
- Updates: Impact all

Option 2: Index per locale (Chosen)
- products-en-us (100M docs, 1TB)
- products-es-es (50M docs, 500GB)
- products-de-de (40M docs, 400GB)
- ...

Pros:
+ Performance: Smaller, faster
+ Relevance: Locale-specific tuning
+ Updates: Isolated impact
+ Scaling: Independent
Cons:
- Complexity: Multiple indexes
- Cross-locale: Harder

Winner: Per-locale indexes (performance + relevance)
```

**Reindexing Strategy**:
```
Challenge: Update index schema without downtime

Blue-Green Reindexing:
1. Create new index: products-en-us-v2
2. Start dual-write: Write to v1 and v2
3. Backfill v2: Copy from v1 (background)
4. Verify v2: Compare counts, test queries
5. Switch alias: products-en-us → v2
6. Stop dual-write
7. Delete v1 after 7 days

Duration: 4-6 hours for 100M docs
Downtime: 0 seconds
```

### Text Analysis

**Analyzers**:
```
Standard Analyzer (Default):
- Tokenization: Whitespace, punctuation
- Lowercase: Convert to lowercase
- Stop words: Remove common words
- Stemming: "running" → "run"

Good for: General text search

Product-Specific Analyzer:
- Tokenization: Preserve model numbers (iPhone13)
- Lowercase: Yes
- Stop words: Minimal (keep "the", "a" for products)
- Synonyms: "laptop" → "notebook", "phone" → "mobile"
- Edge n-grams: For partial matching

Example:
Input: "iPhone 13 Pro Max 256GB"
Tokens: ["iphone", "13", "pro", "max", "256gb", "iph", "ipho", "iphon"]

This allows matching:
- "iphone 13" ✓
- "iph" (prefix) ✓
- "pro max" ✓
```

**Synonym Handling**:
```
Challenge: Users search different terms for same product

Synonym file (10K+ entries):
laptop, notebook, portable computer
phone, mobile, smartphone, cell phone
tv, television, tele
sneakers, shoes, trainers, kicks

Applied at index time:
"laptop" indexed as: ["laptop", "notebook", "portable", "computer"]

Query "notebook" matches documents with "laptop"

Update frequency: Weekly (new product terms)
```

### Relevance Tuning

**Scoring Algorithm**:
```
Base Score (BM25):
score = IDF × (TF × (k1 + 1)) / (TF + k1 × (1 - b + b × (docLength / avgDocLength)))

Where:
- IDF: Inverse Document Frequency (rarer terms score higher)
- TF: Term Frequency (more occurrences score higher)
- k1: 1.2 (term saturation parameter)
- b: 0.75 (length normalization)

Field Boosting:
- title: 3x boost (most important)
- brand: 2x boost
- description: 1x boost (default)
- attributes: 0.5x boost (least important)

Function Score:
final_score = text_score × 0.7 + 
              popularity_score × 0.2 + 
              recency_score × 0.1

Where:
- popularity_score: log(review_count + 1) × rating
- recency_score: decay function (prefer newer products)
```

**A/B Testing Relevance**:
```
Control group (50%): Current algorithm
Test group (50%): New algorithm

Metrics:
- CTR (Click-Through Rate): Clicks / Impressions
- Add-to-cart rate: Carts / Clicks
- Purchase rate: Purchases / Clicks
- Revenue per search: Total revenue / Searches

Statistical significance test:
- Z-test with p-value < 0.05
- Minimum 1 week, 10M searches
- Winner: Higher revenue per search
```

---

## Deep Dives

### Deep Dive 1: Handling Typos & Spell Correction

**Challenge**: Users make typos (20% of queries)

**Solution: Multi-Layer Approach**

**Layer 1: Fuzzy Matching (Real-time)**
```
Elasticsearch fuzziness parameter:
"fuzziness": "AUTO"

AUTO rules:
- 1-2 chars: No fuzziness
- 3-5 chars: Edit distance = 1
- 6+ chars: Edit distance = 2

Example:
Query: "iphne" (typo)
Matches: "iphone" (1 edit: insert 'o')

Latency: No additional cost (built into query)
Coverage: ~60% of typos
```

**Layer 2: Spell Checker (Real-time)**
```
Dictionary: 1M+ correctly spelled terms
- Popular product names
- Brand names
- Common words

Algorithm: Edit distance + frequency
1. Generate candidates (1-2 edits from query)
2. Filter by dictionary
3. Rank by frequency
4. Return top suggestion

Example:
Query: "ipone"
Candidates: ["phone", "iphone", "ipone"]
Dictionary check: ["phone" ✓, "iphone" ✓]
Frequency: iphone (1M) > phone (500K)
Suggestion: "iphone"

Latency: 5ms
Cache hit rate: 80%
Accuracy: 90%
```

**Layer 3: Query Rewrite (if no results)**
```
If fuzzy + spell check return 0 results:
1. Remove least important word
2. Retry search
3. Repeat until results found OR 2 words remaining

Example:
Query: "wireles bluetoth hedphones"
Try 1: "wireles bluetoth" → 0 results
Try 2: "wireles" → 0 results
Try 3: Use spell check → "wireless" → 10K results

Show: "Did you mean: wireless headphones?"
```

**Performance**:
```
Queries with typos: 100M/day (20%)
Fuzzy handled: 60M (60%)
Spell check: 35M (35%)
Query rewrite: 5M (5%)

Total correction rate: 95%
Latency impact: +10ms average
```

### Deep Dive 2: Faceted Search & Dynamic Filters

**Challenge**: Show relevant filters without overwhelming users

**Solution: Smart Facet Selection**

**Facet Types**:
```
1. Category: Electronics > Mobile Phones (tree)
2. Brand: Apple, Samsung, Google (multi-select)
3. Price: Ranges ($0-$50, $50-$100, etc.)
4. Rating: 4+ stars, 3+ stars
5. Prime: Prime eligible (boolean)
6. Availability: In stock, Pre-order
7. Condition: New, Refurbished, Used
```

**Dynamic Facet Selection Algorithm**:
```
1. Start with search query: "laptop"
2. Elasticsearch aggregations return all possible facets
3. Apply business rules:
   - Always show: Category, Brand, Price
   - Show if > 10 distinct values: Rating, Prime
   - Show if > 50% variance: Condition, Availability
4. Rank facets by usefulness:
   - Discriminative power: How much it narrows results
   - User engagement: Historical click rate
5. Show top 7 facets

Example:
Query: "laptop" (50K results)
Facets shown:
- Brand: Dell (5K), HP (4K), Apple (3K), Lenovo (2K)...
- Price: $0-$500 (10K), $500-$1000 (20K), $1000+ (20K)
- Rating: 4+ stars (35K), 3+ stars (45K)
- Screen Size: 13" (8K), 15" (25K), 17" (12K)
- RAM: 8GB (15K), 16GB (25K), 32GB (5K)

Facets hidden (low discriminative power):
- Prime: 90% eligible (not useful to filter)
- Condition: 95% new (not useful)
```

**Facet Count Computation**:
```
Challenge: Update counts as user applies filters

Naive approach:
- User selects Brand: Apple
- Re-query Elasticsearch for all facets
- Problem: Slow (50ms per facet × 7 = 350ms)

Optimized approach:
- Single query with all aggregations
- Elasticsearch returns counts for all facets simultaneously
- Client-side filtering updates UI
- Total time: 50ms for all facets

Elasticsearch aggregation:
{
  "aggs": {
    "brands": {
      "filter": {"term": {"brand": "Apple"}},
      "aggs": {
        "price_ranges": {
          "range": {"field": "price", "ranges": [...]}
        },
        "ratings": {
          "terms": {"field": "rating"}
        }
      }
    }
  }
}

Result: All facet counts in single query
```

**Performance Optimization**:
```
Cache facet results:
- Key: query_hash + filters
- TTL: 5 minutes
- Hit rate: 30% (common filters)

Pre-compute popular facets:
- Daily job: Top 1000 queries
- Pre-calculate all facet combinations
- Store in Redis
- Serve instantly (< 1ms)

Cost savings:
- 30% cache hits = 1.7M ES queries/day saved
- Cost: $50/day saved
```

### Deep Dive 3: Personalized Ranking

**Challenge**: Same query, different users → different results

**Personalization Strategy**:

**User Segmentation**:
```
Segment 1: New users (no history)
- Use global popularity
- Show best-sellers
- No personalization (cold start)
- Coverage: 20% of users

Segment 2: Active users (1-100 purchases)
- Use collaborative filtering
- "Users like you bought..."
- Moderate personalization
- Coverage: 60% of users

Segment 3: Power users (100+ purchases)
- Deep learning embeddings
- Strong personalization
- Category-specific models
- Coverage: 20% of users
```

**Real-time Feature Computation**:
```
Problem: Can't compute 300 features real-time (too slow)

Solution: Precompute + Real-time hybrid

Precomputed (batch, daily):
- User embeddings (256 dimensions)
- Product embeddings (256 dimensions)
- Historical CTR (query, product) pairs
- Category preferences
- Brand affinity
Storage: Redis, 10GB per region

Real-time (computed on request):
- Current session behavior
- Time of day
- Device type
- Recent searches (last 5)
Storage: Session cache, 1KB per user

Total feature computation: < 5ms
```

**Personalization Impact**:
```
A/B Test Results:
Control (no personalization): 
- CTR: 35%
- Conversion: 3.2%
- Revenue/search: $2.50

Treatment (personalized):
- CTR: 42% (+20%)
- Conversion: 4.1% (+28%)
- Revenue/search: $3.20 (+28%)

Winner: Personalized ranking
Incremental revenue: $350M/year
Cost: $2M/year (infrastructure)
ROI: 175x
```

### Deep Dive 4: Handling Prime Day Traffic (10x Spike)

**Challenge**: Normal 6K QPS → 60K QPS in 1 hour

**Detection**:
```
Monitoring metrics:
- QPS per minute (alert at 2x normal)
- Latency P99 (alert at > 300ms)
- Error rate (alert at > 1%)
- ES cluster CPU (alert at > 70%)

Alert triggers: 10 minutes before expected spike
- Based on historical data
- Prime Day starts 12:00 AM PT
- Pre-scale at 11:50 PM
```

**Auto-Scaling Strategy**:
```
API Servers:
- Current: 28 servers per region
- Scale to: 56 servers (2x)
- Time: 5 minutes (AWS ASG)
- Cost: $17K for 24 hours

Elasticsearch Data Nodes:
- Current: 30 nodes
- Scale to: 45 nodes (1.5x)
- Time: 15 minutes (add nodes, rebalance)
- Cost: $19K for 24 hours

Ranking Service:
- Current: 14 servers
- Scale to: 28 servers (2x)
- Time: 5 minutes
- Cost: $5K for 24 hours

Total additional cost: $41K for Prime Day
Revenue uplift: $100M+ (worth it!)
```

**Degradation Strategy** (if spike > 10x):
```
Level 1: Reduce quality (10x-12x load)
- Disable personalization (save 10ms)
- Reduce results from 48 to 24
- Simplify facets (top 3 only)
- Impact: 5% conversion drop
- Capacity gain: +20%

Level 2: Cache aggressively (12x-15x load)
- Increase cache TTL: 5min → 30min
- Cache partial results
- Serve stale data (< 1 hour old)
- Impact: 2% revenue loss
- Capacity gain: +50%

Level 3: Rate limiting (> 15x load)
- Priority users: Prime members
- Limit: 10 searches/minute (vs unlimited)
- Queue excess requests
- Impact: 10% user frustration
- Capacity gain: Preserve system

Never reached Level 3 in production
```

### Deep Dive 5: Multi-Language Search

**Challenge**: Support 15 languages, cross-language queries

**Approach 1: Separate Index per Language** (Chosen)
```
Indexes:
- products-en-US (English, US)
- products-es-ES (Spanish, Spain)
- products-de-DE (German, Germany)
- products-fr-FR (French, France)
- products-ja-JP (Japanese, Japan)
... 15 total

Pros:
+ Language-specific analyzers
+ Cultural relevance (US vs UK English)
+ Performance (smaller indexes)
+ Independent scaling

Cons:
- Duplication (same product in multiple indexes)
- Cross-language search harder
- 15x management overhead

Size impact:
- English: 100M products, 1TB
- Spanish: 50M products, 500GB
- Others: 10-30M each
- Total: 3TB vs 1.2TB (single index)
```

**Language Detection**:
```
Automatic detection:
- Library: langdetect (95% accuracy)
- Fallback: Accept-Language header
- Override: User-selected language

Example:
Query: "ordinateur portable" 
Detected: French → Search products-fr-FR
Result: Laptops in French

Query: "laptop" from France
Detected: English → Search products-en-US
Filter: Ships to France
Result: International products
```

**Cross-Language Search**:
```
Scenario: English query → Spanish products

Solution: Machine Translation
1. Detect query language: "laptop" → English
2. Translate to target: "laptop" → "portátil"
3. Search Spanish index
4. Translate results back to English

Provider: AWS Translate
Cost: $15 per 1M characters
Latency: +30ms
Usage: 5% of queries (multi-lingual users)

Alternative: Multilingual embeddings
- Universal Sentence Encoder
- Query embedding: [0.23, 0.45, ...]
- Product embedding: [0.25, 0.43, ...]
- Cosine similarity matching
- Language-agnostic
- Future implementation
```

---

## Technology Stack Justification

### Summary of Technology Choices

```
Layer               | Technology        | Alternative          | Why Chosen
--------------------|-------------------|----------------------|------------------
API Service         | Go                | Java, Node.js        | Performance + concurrency
Search Engine       | Elasticsearch     | Solr, Algolia        | Features + scale + cost
Ranking             | Python + Go       | Java                 | ML libs + performance
Message Queue       | Kafka             | RabbitMQ, SQS        | Throughput + durability
Product DB          | PostgreSQL        | MySQL, MongoDB       | ACID + JSON + maturity
Analytics DB        | ClickHouse        | Redshift, Druid      | Performance + cost
Cache               | Redis             | Memcached            | Data structures
Object Storage      | S3                | GCS, Azure Blob      | AWS ecosystem
ML Platform         | SageMaker         | Custom               | Managed + integrations
Orchestration       | Kubernetes        | ECS, Nomad           | Industry standard
Monitoring          | Prometheus        | Datadog, New Relic   | Open source + powerful
Logging             | ELK               | Splunk               | Open source + search
Tracing             | Jaeger            | Zipkin, X-Ray        | OpenTracing standard
```

### Detailed Justifications (Already Covered Above)

All major technology choices have been justified in the Core Components section:
- Go for API Service: Performance + concurrency
- Elasticsearch: Full comparison with Solr, Algolia, PostgreSQL, DynamoDB
- Kafka: Compared with RabbitMQ, SQS, database triggers
- PostgreSQL: Compared with MySQL, NoSQL
- ClickHouse: Compared with Redshift, Druid

---

## Ranking & Relevance

### Learning to Rank (LTR) Pipeline

**Training Data Generation**:
```
Input: Click logs (1.5B events/day)

Labels:
- Positive: Clicked AND purchased (weight: 10)
- Positive: Clicked AND added to cart (weight: 5)
- Positive: Clicked (weight: 1)
- Negative: Shown but not clicked (weight: -1)
- Ignored: Not shown (no label)

Features (300+): Already detailed in Ranking Service section
- Query features: Length, type, popularity
- Product features: Title match, rating, price
- User features: History, demographics
- Interaction features: CTR, conversion rate

Training set size:
- 30 days of data
- 45B events
- After filtering: 10B training examples
- Compressed: 500GB
```

**Model Training**:
```
Algorithm: LightGBM (Gradient Boosted Trees)

Why LightGBM over alternatives:
- XGBoost: Similar performance, slower training
- Neural Networks: Better accuracy (+2%), 10x slower inference
- Linear models: Fast but poor accuracy (-20%)

LightGBM advantages:
✓ Training speed: 6 hours vs 24 hours (XGBoost)
✓ Inference: 1ms vs 10ms (neural network)
✓ Interpretable: Feature importance
✓ Memory: 100MB model vs 1GB (neural network)

Hyperparameters:
- num_leaves: 31
- learning_rate: 0.1
- num_iterations: 100
- max_depth: -1 (no limit)
- lambda_l1: 0.1 (L1 regularization)
- lambda_l2: 0.1 (L2 regularization)

Objective function:
loss = -Σ(log(P(click|features)) × weight)

Training time: 6 hours on 100 machines
Model size: 100MB
```

**Online Inference**:
```
Input: 100 products from Elasticsearch

For each product:
1. Extract features (5ms)
   - Cache user features (Redis)
   - Cache product features (ES stored fields)
   - Compute interaction features

2. Model inference (0.05ms per product)
   - Load model (in-memory, pre-loaded)
   - Predict score
   - 100 products × 0.05ms = 5ms total

3. Sort by score (1ms)

Total: 11ms for 100 products
```

**Model Evaluation**:
```
Offline metrics:
- NDCG@10 (Normalized Discounted Cumulative Gain)
- MAP (Mean Average Precision)
- AUC (Area Under Curve)

Current model:
- NDCG@10: 0.78 (0.80 is state-of-art)
- MAP: 0.62
- AUC: 0.85

Online A/B test metrics:
- CTR: +15% vs baseline
- Conversion: +18% vs baseline
- Revenue: +20% vs baseline
```

### Relevance Improvements Over Time

```
Version 1.0 (BM25 only):
- Algorithm: Elasticsearch BM25
- Features: Query-document match only
- CTR: 28%
- Conversion: 2.1%

Version 2.0 (+ Product features):
- Added: Rating, reviews, price
- CTR: 32% (+14%)
- Conversion: 2.5% (+19%)

Version 3.0 (+ User features):
- Added: Search history, purchases
- CTR: 36% (+13%)
- Conversion: 3.0% (+20%)

Version 4.0 (+ Interaction features):
- Added: Historical CTR, conversion
- CTR: 40% (+11%)
- Conversion: 3.5% (+17%)

Version 5.0 (Current - LightGBM):
- Full LTR with 300 features
- CTR: 45% (+13%)
- Conversion: 4.2% (+20%)

Total improvement: 61% CTR, 100% conversion
```

---

## Scalability & Performance

### Horizontal Scaling

**API Service Layer**:
```
Current: 28 servers per region

Scale triggers:
- CPU > 70% for 5 minutes → +4 servers
- Latency P95 > 100ms → +4 servers
- QPS > 5K per server → +4 servers

Max scale: 100 servers per region
Time to scale: 3 minutes (container start)
Cost: $306/server/month

Auto-scaling saves: $50K/month (vs over-provisioning)
```

**Elasticsearch Cluster**:
```
Current: 30 data nodes per region

Scale triggers:
- CPU > 70% → +3 nodes
- Heap pressure > 75% → +3 nodes
- Query latency P95 > 80ms → +3 nodes

Max scale: 60 nodes per region
Time to scale: 15 minutes (node start + shard rebalance)
Cost: $1,248/node/month

Scaling considerations:
- Add nodes in multiples of 3 (for shards)
- Rebalancing: 5-10 minutes
- No downtime (replicas serve requests)
```

**Database Scaling**:
```
PostgreSQL:
- Vertical scaling: Upgrade instance type
- Horizontal reads: Add read replicas
- Horizontal writes: Shard by product_id hash

Current: db.r5.4xlarge (16 cores, 128GB)
Can scale to: db.r5.24xlarge (96 cores, 768GB)

Sharding:
- 16 shards (mod 16 of product_id)
- Each shard: 18.75M products
- Can scale to 256 shards (future)
```

### Performance Optimization

**Query Optimization**:
```
Slow query example: Brand aggregation
Before:
{
  "aggs": {
    "brands": {
      "terms": {"field": "brand", "size": 1000}
    }
  }
}
Latency: 150ms

After (with cardinality limit):
{
  "aggs": {
    "brands": {
      "terms": {
        "field": "brand",
        "size": 20,
        "shard_size": 50
      }
    }
  }
}
Latency: 25ms (-83%)

Lesson: Only get what you need
```

**Caching Strategy**:
```
L1: Browser cache (client-side)
- Cache: Product images, CSS, JS
- TTL: 7 days
- Hit rate: 90%
- Bandwidth saved: 80%

L2: CDN (edge cache)
- Cache: Product images, static assets
- TTL: 1 day
- Hit rate: 85%
- Origin requests: -85%

L3: Redis (application cache)
- Cache: Search results, facets, user data
- TTL: 5 minutes - 1 hour
- Hit rate: 40-50%
- ES queries saved: 200K/day

L4: Elasticsearch query cache
- Cache: Filter queries
- TTL: Until index refresh (1 second)
- Hit rate: 60% for filters
- CPU saved: 30%
```

**Connection Pooling**:
```
Problem: Creating new connections is slow
- Elasticsearch: 10ms overhead per request
- PostgreSQL: 5ms overhead per request

Solution: Connection pools
- API → ES: 100 connections per API server
- API → PostgreSQL: 50 connections per API server
- Reuse connections

Result:
- Latency reduction: 10-15ms per request
- Throughput increase: +30%
```

---

## Trade-offs & Alternatives

### Trade-off 1: Search Quality vs Speed

```
Option A: Deep search (high quality)
- Search 1000 candidates from ES
- Apply full ML ranking
- Return top 48
- Latency: 100ms
- Relevance: Best

Option B: Fast search (lower quality)
- Search 100 candidates from ES
- Simpler ranking
- Return top 48
- Latency: 30ms
- Relevance: -5%

Option C: Hybrid (Chosen)
- Search 100 candidates (25ms)
- Full ML ranking (10ms)
- Return top 48
- Latency: 50ms
- Relevance: -1% vs Option A

Why hybrid:
- 2x faster than deep search
- 1% quality loss acceptable
- Better user experience (speed matters)
- Can always improve ranking model
```

### Trade-off 2: Real-time vs Batch Indexing

```
Option A: Real-time (chosen for prices/inventory)
- CDC → Kafka → ES (< 1 minute)
- Fresh data critical
- Examples: Price, stock status
- Cost: Higher (Kafka infrastructure)

Option B: Batch (chosen for less critical data)
- Database → S3 → Spark → ES (hourly)
- Freshness not critical
- Examples: Reviews, ratings, descriptions
- Cost: Lower (batch processing)

Mixed approach saves 40% on indexing costs
```

### Trade-off 3: Personalization vs Privacy

```
Option A: Deep personalization
- Track everything: clicks, time-on-page, mouse movements
- Build detailed user profiles
- Best relevance (+30% conversion)
- Privacy concerns

Option B: No personalization
- No tracking
- Same results for everyone
- Privacy friendly
- Poor relevance (-30% conversion)

Option C: Balanced (chosen)
- Track essential: searches, purchases, cart
- Anonymize after 90 days
- Opt-out available
- Good relevance (+20% conversion)
- GDPR compliant

Why balanced:
- Legal requirements (GDPR)
- User trust important
- 20% uplift still significant
- Can improve without more data
```

### Trade-off 4: Single vs Multi-Tenant

```
Option A: Shared everything
- One ES cluster for all customers
- Pros: Cheap, simple
- Cons: Noisy neighbor, no isolation

Option B: Dedicated per customer
- Separate ES cluster per customer
- Pros: Isolation, customization
- Cons: Expensive, complex

Option C: Multi-tenant with isolation (chosen)
- Shared ES cluster
- Separate indices per locale
- Resource quotas per index
- Pros: Cost effective + some isolation
- Cons: Still some noisy neighbor risk

Why multi-tenant:
- Cost: 1/10th of dedicated
- Scale: Easier to manage
- Noisy neighbor: Mitigated with quotas
```

---

## Interview Questions & Answers

### Q1: How would you handle a sudden 10x traffic spike?

**Answer:**
```
Immediate actions (0-5 minutes):
1. Auto-scaling kicks in
   - API servers: 28 → 56 (5 min)
   - ES nodes: 30 → 45 (15 min)
   - Ranking: 14 → 28 (5 min)

2. Enable degradation if needed:
   - Increase cache TTL (5min → 30min)
   - Reduce results (48 → 24)
   - Disable personalization (save 10ms)

3. Alert on-call team
   - Monitor dashboards
   - Ready to intervene

Why this works:
- Auto-scaling handles 10x
- Degradation handles 15x
- Manual intervention for > 15x

Historical data:
- Prime Day: Handled 12x spike
- Black Friday: Handled 15x spike
- Never needed manual intervention
```

### Q2: How do you ensure search results are relevant?

**Answer:**
```
Multi-layered approach:

1. Elasticsearch relevance (base layer):
   - BM25 scoring algorithm
   - Field boosting (title 3x, brand 2x)
   - Accounts for 50% of final score

2. ML ranking (personalization):
   - 300+ features
   - LightGBM model (weekly training)
   - User history, product quality
   - Accounts for 40% of final score

3. Business rules (final adjustment):
   - Boost in-stock items
   - Boost Prime eligible
   - Sponsored products
   - Accounts for 10% of final score

Measurement:
- CTR: 45% (industry average: 30%)
- Conversion: 4.2% (industry: 2.5%)
- NDCG@10: 0.78 (academic sota: 0.80)

Continuous improvement:
- Weekly model retraining
- Monthly A/B tests
- Quarterly relevance reviews
```

### Q3: How do you handle typos in search queries?

**Answer:**
```
Three-layer approach (already detailed in Deep Dive 1):

Layer 1: Fuzzy matching (60% of typos)
- Built into Elasticsearch
- Edit distance 1-2
- No additional latency
- Example: "iphne" → "iphone"

Layer 2: Spell checker (35% of typos)
- Dictionary-based
- 1M+ correct terms
- 5ms latency
- Cache hit rate: 80%
- Example: "ipone" → "iphone"

Layer 3: Query rewrite (5% of typos)
- Remove words iteratively
- Last resort
- Example: "wireles bluetoth" → "wireless"

Total correction rate: 95%
Average latency impact: +10ms
User satisfaction: +15% vs no correction
```

### Q4: How do you scale Elasticsearch to handle 70K QPS?

**Answer:**
```
1. Horizontal scaling:
   - 30 data nodes per region (129 total)
   - Each node: 200 QPS capacity
   - Total: 6K QPS per region
   - 3 regions: 18K QPS base capacity
   - With headroom: 70K QPS peak ✓

2. Sharding strategy:
   - 15 primary shards per index
   - 2 replicas (3 total copies)
   - 45 shard copies distributed across nodes
   - Parallel query execution

3. Caching:
   - Query cache: 60% hit rate
   - Filter cache: 90% hit rate
   - Redis application cache: 40% hit rate
   - Reduces load by 50%

4. Query optimization:
   - Coordinating nodes (route requests)
   - Aggregation optimization
   - Field data reduction
   - Smart result window limits

Cost: $40,773/month per region
Alternative (Algolia): $1M/month (25x more!)
```

### Q5: How do you handle product updates in real-time?

**Answer:**
```
Change Data Capture (CDC) pipeline:

1. PostgreSQL change:
   - Price update: $999 → $899
   - Trigger: Debezium CDC connector
   - Time: < 10ms

2. Kafka publish:
   - Topic: product-updates
   - Partition: hash(product_id)
   - Guarantees ordering per product
   - Time: < 10ms

3. Indexer consumer:
   - 20 parallel consumers
   - Batch 1000 updates
   - Bulk index to Elasticsearch
   - Time: 100ms

4. Index refresh:
   - Automatic every 1 second
   - Makes searchable
   - Time: 1 second

Total latency: Update → Searchable in < 2 seconds

For critical updates (price drops):
- Skip batching
- Direct index
- Total latency: < 500ms

Why Kafka:
- Durability: No lost updates
- Ordering: Per product
- Replay: Can reindex anytime
- Scale: 100K updates/sec capacity
```

### Q6: How would you implement visual search (search by image)?

**Answer:**
```
Architecture:

1. Image ingestion:
   - User uploads image
   - Compress + resize
   - Store in S3
   - Time: 500ms

2. Feature extraction:
   - CNN model (ResNet-50)
   - Extract 2048-dim vector
   - Hosted on SageMaker
   - Time: 200ms

3. Vector search:
   - Approximate NN search
   - FAISS index (Facebook AI)
   - Cosine similarity
   - Return top 100 products
   - Time: 20ms

4. Ranking:
   - Apply text-based filters
   - ML ranking
   - Return top 48
   - Time: 10ms

Total latency: 730ms (acceptable for image search)

Index:
- 200M product image embeddings
- FAISS index size: 1.6TB
- Distributed across 10 nodes
- QPS capacity: 1K searches/sec

Cost:
- SageMaker inference: $3K/month
- FAISS index storage: $500/month
- Total: $3.5K/month

Future: Multimodal search (image + text)
```

### Q7: How do you prevent search index from falling behind during bulk updates?

**Answer:**
```
Problem: Product catalog import of 1M products

Naive approach:
- Index 1M products one by one
- Time: 1M × 100ms = 100K seconds = 28 hours ❌

Optimized approach:

1. Bulk indexing:
   - Batch 10K products per request
   - Elasticsearch bulk API
   - Parallel requests (10 concurrent)
   - Time: 100 batches × 10 seconds = 16 minutes ✓

2. Throttling:
   - Monitor cluster health
   - If CPU > 80%: Slow down
   - If heap > 80%: Pause
   - Prevents cluster overwhelm

3. Off-peak scheduling:
   - Run during low-traffic hours (2-4 AM)
   - Minimal impact on search traffic

4. Separate cluster option:
   - Build index on separate cluster
   - Swap alias when complete
   - Zero impact on production

Best practices:
- Pre-allocate shards
- Disable refresh during bulk (refresh after)
- Use SSD for better write performance
- Monitor index rate (MB/sec)

Handled successfully:
- Annual catalog refresh: 300M products
- Completed in 6 hours
- Zero downtime
- No degradation
```

---

## Conclusion

This Amazon Product Search system design handles **500M searches per day** with:

✅ **Sub-second latency**: P50 < 50ms, P99 < 200ms  
✅ **High relevance**: 45% CTR, 4.2% conversion  
✅ **Massive scale**: 300M products, 70K QPS peak  
✅ **Real-time updates**: < 1 minute for price/inventory  
✅ **Personalization**: +20% conversion vs baseline  
✅ **Multi-language**: 15 languages supported  
✅ **Cost-efficient**: $160K/month optimized

**Key Design Decisions:**

1. **Elasticsearch** for search engine (vs Solr, Algolia)
2. **Go** for API services (performance + concurrency)
3. **Kafka** for real-time indexing (durability + replay)
4. **Separate ranking service** (ML flexibility)
5. **PostgreSQL** for product catalog (ACID + SQL)
6. **ClickHouse** for analytics (performance + cost)
7. **Redis** for caching (data structures + speed)
8. **Multi-region** deployment (low latency globally)

**Success Metrics:**
- Search conversion rate: 4.2% (industry avg: 2.5%)
- Zero-result rate: < 5% (industry avg: 15%)
- P99 latency: 200ms (industry avg: 500ms)
- Availability: 99.99% (52 min/year downtime)
- Cost per search: $0.0003 (10x cheaper than Algolia)

This architecture balances **performance, relevance, cost, and scalability** to deliver a world-class product search experience that drives billions in e-commerce revenue.

---

## Additional Resources

**Further Reading:**
- Elasticsearch: The Definitive Guide
- Learning to Rank in Practice
- Designing Data-Intensive Applications
- Streaming Systems (Kafka)

**Amazon Papers:**
- "Learning to Rank for E-commerce"
- "Real-time Personalization at Scale"
- "Dealing with Noisy Labels in Search"

**Industry Benchmarks:**
- Google PAIR: Search Quality Guidelines
