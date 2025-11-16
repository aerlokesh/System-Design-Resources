# Social Feed Ads Management and Display System - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [API Design](#api-design)
9. [Ad Serving Flow](#ad-serving-flow)
10. [Deep Dives](#deep-dives)
11. [Scalability & Reliability](#scalability--reliability)
12. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design an ads management and display system for a social media platform that:
- Serves personalized ads in user feeds alongside organic content
- Enables advertisers to create, manage, and optimize ad campaigns
- Supports real-time bidding (RTB) and auction-based ad placement
- Provides detailed analytics and reporting for advertisers
- Tracks ad impressions, clicks, conversions, and engagement
- Manages advertiser budgets and billing
- Optimizes ad delivery based on user interests and behavior
- Ensures user privacy and compliance with regulations (GDPR, CCPA)
- Handles fraud detection and brand safety

### Scale Requirements
- **500 million daily active users (DAU)**
- **10 billion feed requests per day** (20 requests per user)
- **50 million advertisers** globally
- **100 million active ad campaigns**
- **Ad load**: 1 ad per 5 organic posts (20% ad density)
- **2 billion ad impressions per day**
- **Ad serving latency**: < 100ms (p95)
- **Click-through rate (CTR)**: 1-3% average
- **Auction processing**: < 50ms

---

## Functional Requirements

### Must Have (P0)

1. **Ad Campaign Management**
   - Create, edit, pause, delete campaigns
   - Multiple ad formats (image, video, carousel, story)
   - Campaign objectives (awareness, consideration, conversion)
   - Budget settings (daily, lifetime, total)
   - Schedule campaigns (start/end dates)
   - Targeting criteria (demographics, interests, behaviors)
   - Bid strategies (manual, automatic, target CPA)

2. **Ad Targeting**
   - Demographic targeting (age, gender, location)
   - Interest-based targeting (user interests, behaviors)
   - Lookalike audiences (similar to existing customers)
   - Custom audiences (upload customer lists)
   - Retargeting (users who visited website)
   - Contextual targeting (based on current content)

3. **Ad Serving**
   - Real-time ad selection for each user
   - Auction-based ranking (bid × quality score)
   - Frequency capping (max impressions per user)
   - Pacing (distribute budget evenly)
   - A/B testing support
   - Fallback ads (if no bids)

4. **Ad Auction System**
   - Real-time bidding (RTB)
   - Second-price auction mechanism
   - Quality score calculation
   - Bid ranking and selection
   - Reserve price enforcement
   - Auction transparency

5. **Performance Tracking**
   - Impressions (ad shown)
   - Clicks (user clicked ad)
   - Conversions (user completed action)
   - Engagement (likes, shares, comments)
   - Video views (25%, 50%, 75%, 100%)
   - Conversion attribution

6. **Budget Management**
   - Daily budget pacing
   - Lifetime budget tracking
   - Auto-pause on budget exhausted
   - Spending alerts
   - Payment processing

7. **Analytics & Reporting**
   - Campaign performance dashboards
   - Real-time metrics
   - Demographic breakdowns
   - Placement performance
   - ROI and ROAS calculations
   - Custom reports

8. **Fraud Detection**
   - Click fraud detection
   - Impression fraud (bots)
   - Invalid traffic filtering
   - Suspicious patterns
   - Advertiser protection

### Nice to Have (P1)
- Dynamic creative optimization (DCO)
- Predictive budget recommendations
- Automated bid optimization (machine learning)
- Cross-device attribution
- Brand safety controls
- Viewability measurement
- Conversion lift studies
- Integration with third-party measurement tools

---

## Non-Functional Requirements

### Performance
- **Ad serving latency**: < 100ms (p95)
- **Auction processing**: < 50ms
- **Feed load time**: < 1 second (with ads)
- **Campaign creation**: < 2 seconds
- **Dashboard load**: < 3 seconds

### Scalability
- Support 500M DAU
- Handle 10B feed requests per day
- Serve 2B ad impressions per day
- Support 50M advertisers
- 100M active campaigns

### Availability
- **99.99% uptime** for ad serving
- **99.95% uptime** for advertiser dashboard
- Graceful degradation (serve organic content if ads fail)
- Multi-region deployment

### Consistency
- **Eventual consistency** for campaign updates (acceptable 1-minute delay)
- **Strong consistency** for billing and budget
- **At-least-once delivery** for events
- Idempotent event processing

### Privacy & Compliance
- **GDPR compliance**: User consent, data deletion
- **CCPA compliance**: Do not sell, opt-out
- **Cookie policies**: First-party data only
- **Ad preferences**: User control over ad categories
- **Transparency**: Why user saw ad

### Monetization
- **Revenue share**: 70% to platform, 30% to creator (if applicable)
- **Minimum CPM**: $1-5 (cost per thousand impressions)
- **Payment terms**: Net 30 days for advertisers
- **Refunds**: Invalid traffic refunded

---

## Capacity Estimation

### Traffic Estimates

```
Daily Active Users: 500M
Feed requests per user per day: 20
Total feed requests per day: 10B

Ad density: 1 ad per 5 posts (20%)
Ad slots per day: 10B / 5 = 2B ad impressions

Average CTR: 2%
Ad clicks per day: 2B × 0.02 = 40M clicks

Conversion rate: 5% of clicks
Conversions per day: 40M × 0.05 = 2M conversions

QPS (feed requests):
Average: 10B / 86,400 = 115,741 QPS
Peak (3x): 347,222 QPS

Ad serving QPS:
Average: 2B / 86,400 = 23,148 QPS
Peak: 69,444 QPS
```

### Storage Estimates

```
Advertisers: 50M
Campaigns: 100M
Ad creatives: 500M

Advertiser data:
- Per advertiser: 5 KB (profile, payment info)
- Total: 50M × 5 KB = 250 GB

Campaign data:
- Per campaign: 10 KB (settings, targeting, budget)
- Total: 100M × 10 KB = 1 TB

Ad creative data:
- Per creative: 2 KB (metadata, URLs)
- Images/videos: Stored in CDN (separate)
- Total: 500M × 2 KB = 1 TB

User targeting data:
- Per user: 10 KB (interests, demographics, behavior)
- Total: 500M × 10 KB = 5 TB

Events (impressions, clicks, conversions):
- Impressions: 2B/day × 500 bytes = 1 TB/day
- Clicks: 40M/day × 500 bytes = 20 GB/day
- 90-day retention: 90 TB

Total storage:
- Metadata: 7.25 TB
- Events: 90 TB
- Indexes: 20 TB
- Total: ~117 TB

With replication (3x): 351 TB
```

### Processing Requirements

```
Ad Auction Processing:
- Requests per second: 69,444 (peak)
- Auction time: 50ms per request
- Concurrent auctions: 69,444 × 0.05 = 3,472 concurrent
- CPU cores needed: 3,500 cores

Targeting evaluation:
- Check user profile against campaigns
- 100M campaigns to evaluate (with indexes, top 1000)
- Evaluation time: 30ms
- CPU cores: 2,000 cores

Event processing:
- Events per second: 2B impressions + 40M clicks / 86,400 = 23,611 events/sec
- Processing per event: 10ms (log, aggregate, bill)
- CPU cores: 236 cores

Total compute: ~6,000 cores
```

### Network Bandwidth

```
Ad serving:
- Requests per second: 69,444 (peak)
- Request size: 1 KB (user context)
- Response size: 5 KB (ad metadata + creative URL)
- Bandwidth: 69,444 × 6 KB = 417 MB/s = 3.3 Gbps

Event tracking:
- Events per second: 23,611
- Event size: 500 bytes
- Bandwidth: 23,611 × 0.5 KB = 11.8 MB/s = 94 Mbps

Total: ~3.4 Gbps
```

### Cost Estimation

```
Monthly Costs (approximate):

Compute:
- Ad serving: 200 instances × $300 = $60K
- Auction engine: 100 instances × $400 = $40K
- Event processing: 50 instances × $200 = $10K

Storage:
- Metadata (SSD): 10 TB × $200/TB = $2K
- Events (HDD): 100 TB × $30/TB = $3K
- CDN (ad creatives): $50K

Database:
- PostgreSQL (RDS): $10K
- Redis: $5K
- Cassandra: $15K

Total: ~$195K per month

Revenue:
- 2B impressions/day × 30 days = 60B impressions/month
- Average CPM: $3
- Revenue: 60B / 1000 × $3 = $180M/month

Infrastructure cost: 0.1% of revenue
```

---

## High-Level Architecture

```
                    [500M Daily Active Users]
                              |
                              ↓
                    [Client Apps]
                    (iOS, Android, Web)
                              |
                              ↓
                    [CDN + Load Balancer]
                    (Edge servers globally)
                              |
                              ↓
                    [API Gateway]
                    (Auth, rate limiting)
                              |
            ┌─────────────────┼─────────────────┐
            ↓                 ↓                  ↓
    [Feed Service]    [Ad Service]      [Advertiser API]
    (Organic posts)   (Ad serving)      (Campaign mgmt)
            |                 |                  |
            └─────────────────┼──────────────────┘
                              ↓
                    [Ad Selection Engine]
                              |
        ┌─────────────────────┼─────────────────┐
        ↓                     ↓                  ↓
  [Targeting        [Auction Engine]    [Budget Service]
   Engine]          (Real-time bidding) (Budget checks)
  (User matching)
        |                     |                  |
        └─────────────────────┼──────────────────┘
                              ↓
                    [Ad Ranking]
                    (Sort by bid × quality)
                              |
                              ↓
                    [Ad Cache Layer]
                    (Redis - hot ads)
                              |
            ┌─────────────────┼─────────────────┐
            ↓                 ↓                  ↓
    [User Profile      [Campaign DB]      [Creative DB]
     DB]               (Active campaigns) (Ad creatives)
    (Demographics,     (PostgreSQL)       (S3/CDN)
     interests)
    (Cassandra)
            |                 |                  |
            └─────────────────┼──────────────────┘
                              ↓
                    [Event Tracking]
                              |
        ┌─────────────────────┼─────────────────┐
        ↓                     ↓                  ↓
  [Kafka Event       [Analytics        [Billing Service]
   Stream]            Pipeline]         (Budget deduction)
  (Impressions,      (Real-time        (PostgreSQL)
   clicks)            metrics)
                     (Flink/Spark)
        |                     |                  |
        └─────────────────────┼──────────────────┘
                              ↓
                    [Data Warehouse]
                    (BigQuery/Redshift)
                              ↓
                    [Analytics Dashboard]
                    (Advertiser reporting)
```

### Key Architectural Decisions

1. **Real-Time Auction**
   - Every ad request triggers auction
   - Bids evaluated in real-time
   - Quality score + bid determines winner
   - Sub-50ms auction processing

2. **Cassandra for User Profiles**
   - High read throughput (millions of profile lookups/sec)
   - User interest data updated frequently
   - Horizontal scalability
   - Eventually consistent (acceptable)

3. **Redis for Ad Caching**
   - Cache winning ads per user segment
   - Sub-millisecond lookups
   - Reduce database load
   - TTL-based expiration

4. **Event Streaming (Kafka)**
   - Track all ad events asynchronously
   - Decouple event tracking from serving
   - Enable real-time and batch analytics
   - Support multiple consumers

5. **Targeting Index**
   - Pre-compute eligible ads per user segment
   - Inverted index (interest → ads)
   - Reduce auction candidates (1000 vs 100M)
   - Update index hourly

---

## Core Components

### 1. Ad Campaign Management System

**Purpose**: Allow advertisers to create and manage campaigns

**Campaign Structure**:
```
Advertiser Account
    └─ Campaign
        ├─ Campaign objective (awareness, traffic, conversions)
        ├─ Budget (daily, lifetime)
        ├─ Schedule (start/end dates)
        ├─ Bid strategy (manual CPC, auto-bid, target CPA)
        └─ Ad Groups
            ├─ Targeting criteria
            ├─ Ad placements (feed, stories, sidebar)
            └─ Ad Creatives
                ├─ Images/videos
                ├─ Copy (headline, description, CTA)
                └─ Landing page URL
```

**Campaign Settings**:
```
Budget:
- Daily budget: $100/day
- Lifetime budget: $10,000
- Bid amount: $2 per click (CPC)
- Or bid amount: $10 per thousand impressions (CPM)

Targeting:
- Age: 25-45
- Gender: All
- Location: United States, Urban areas
- Interests: Technology, Gadgets, Sports
- Behaviors: Online shoppers, Mobile users
- Lookalike: Similar to existing customers (1% match)

Schedule:
- Start: 2025-01-20
- End: 2025-02-20
- Time: All day, or specific hours (9 AM - 9 PM)

Placements:
- Feed: Yes
- Stories: Yes
- Reels: No
- Sidebar: No
```

**Creative Specifications**:
```
Image Ads:
- Dimensions: 1200×628 px (recommended)
- File size: < 5 MB
- Format: JPG, PNG
- Text overlay: < 20% of image

Video Ads:
- Duration: 15-60 seconds
- Resolution: 1080p or higher
- File size: < 100 MB
- Format: MP4, MOV

Carousel Ads:
- 2-10 cards
- Each card: Image + headline + link
- Swipeable format
```

**Approval Process**:
```
Ad submission → Automated review → Manual review → Approved/Rejected

Automated checks:
- Image quality (resolution, clarity)
- Text policy (no misleading claims)
- Landing page accessibility
- Prohibited content detection

Manual review (if flagged):
- Human moderator review
- Usually within 24 hours
- Provide feedback if rejected
```

### 2. User Profile & Targeting Service

**Purpose**: Maintain user profiles for ad targeting

**User Profile Data**:
```
Demographics:
- Age: 32
- Gender: Male
- Location: San Francisco, CA, USA
- Language: English
- Education: College degree
- Relationship status: Married

Interests (computed from behavior):
- Technology: 0.95 (high interest)
- Sports: 0.75
- Travel: 0.60
- Fashion: 0.30
- Finance: 0.85

Behaviors:
- Device: iPhone
- Connection: WiFi
- Usage time: Evenings (6-10 PM)
- Engagement: High (likes, shares, comments)
- Purchase history: Online shopper

Custom Audiences:
- Retargeting pixel fired: Yes
- Email match: customer_list_123
- Lookalike: audience_456 (5% similarity)
```

**Interest Calculation**:
```
Sources:
1. Explicit signals:
   - Pages followed
   - Groups joined
   - Interests selected in profile

2. Implicit signals:
   - Posts liked/shared
   - Time spent on content
   - Clicks on links
   - Video watch time
   - Search queries

3. Machine learning:
   - Topic modeling on consumed content
   - Collaborative filtering (similar users)
   - Engagement prediction

Update frequency: Real-time (as user interacts)
Aggregation: Hourly batch job
```

**Privacy Controls**:
```
User settings:
- Ad personalization: On/Off
- Interest categories: User can remove
- Advertiser exclusions: Block specific advertisers
- Data download: Export all data
- Data deletion: Right to be forgotten

Compliance:
- GDPR: Consent required, data portability
- CCPA: Do not sell, opt-out
- Transparency: Show why user saw ad
```

### 3. Ad Selection Engine

**Purpose**: Select best ad to show for each user request

**Selection Process**:

```
[Feed Request from User]
        ↓
[Extract User Context]
- user_id
- location
- device
- time_of_day
        ↓
[Fetch User Profile]
- Demographics
- Interests
- Behaviors
        ↓
[Candidate Retrieval]
- Query targeting index
- Find campaigns matching user
- Initial candidates: ~1000 campaigns
        ↓
[Budget Filtering]
- Check campaign budget remaining
- Check daily pacing
- Filter out exhausted campaigns
- Reduced to: ~100 campaigns
        ↓
[Auction]
- Evaluate bids
- Calculate quality scores
- Rank by: bid × quality × relevance
- Select top 1-3 ads
        ↓
[Frequency Capping]
- Check how many times user saw this ad
- Skip if frequency cap reached
        ↓
[Return Winning Ad]
- Ad creative metadata
- Impression tracking pixel
- Click tracking URL

Total latency: < 100ms
```

**Targeting Index**:
```
Pre-computed index for fast lookup:

Index structure (inverted):
interest:technology → [campaign_1, campaign_2, ..., campaign_1000]
location:san_francisco → [campaign_5, campaign_20, ...]
age:25-34 → [campaign_10, campaign_15, ...]

Query process:
1. Get user interests: [technology, sports]
2. Lookup: campaigns for technology ∩ campaigns for sports
3. Result: Intersection of matching campaigns
4. Fast (< 10ms) vs scanning all 100M campaigns

Index update: Hourly (acceptable staleness)
```

**Frequency Capping**:
```
Track per user:
ad_freq:{user_id}:{campaign_id} → impression_count

Rules:
- Max 3 impressions per ad per day
- Max 10 impressions per advertiser per day
- Min 1 hour between same ad

Check before serving:
IF impression_count >= 3 THEN skip_ad

Store in Redis:
- Fast lookup (< 1ms)
- TTL: 24 hours (auto-reset daily)
```

### 4. Ad Auction System

**Purpose**: Determine which ad to show through bidding

**Auction Mechanism**: Second-Price Auction (Vickrey)

**How It Works**:
```
Eligible ads for auction:
Ad A: Bid $5, Quality 0.8 → Score: 5 × 0.8 = 4.0
Ad B: Bid $4, Quality 0.9 → Score: 4 × 0.9 = 3.6
Ad C: Bid $6, Quality 0.5 → Score: 6 × 0.5 = 3.0

Ranking: A > B > C

Winner: Ad A

Price paid: Second-price
- Second highest score: 3.6
- Price = 3.6 / quality_A = 3.6 / 0.8 = $4.50
- Ad A pays $4.50 (not $5!)

Benefits:
- Encourages truthful bidding
- Rewards quality
- Fair pricing
```

**Quality Score Calculation**:
```
Factors:
1. Expected CTR (40% weight)
   - Historical CTR of this ad
   - Predicted based on ML model

2. Ad relevance (30% weight)
   - How well ad matches user interests
   - Semantic similarity

3. Landing page experience (20% weight)
   - Page load speed
   - Mobile-friendliness
   - User engagement on page

4. Ad format (10% weight)
   - Video > Image > Text
   - Native > Display

Score: Weighted sum (0.0 - 1.0)
```

**Bid Strategies**:

**Manual Bidding**:
```
Advertiser sets fixed bid:
- CPC: $2 per click
- CPM: $10 per 1000 impressions

Simple, predictable
```

**Automatic Bidding**:
```
System adjusts bid to maximize:
- Conversions (target CPA)
- Clicks (maximize clicks)
- Impressions (maximize reach)

ML model predicts optimal bid
Adjusts in real-time based on performance
```

**Target CPA (Cost Per Acquisition)**:
```
Advertiser sets target: $20 per conversion

System automatically bids:
- Higher when conversion likely
- Lower when conversion unlikely
- Aims to hit target CPA

ML model learns conversion patterns
```

### 5. Event Tracking System

**Purpose**: Track all ad interactions for billing and analytics

**Event Types**:

**1. Impression**:
```
Fired when: Ad visible on screen (50% for 1 second)
Data tracked:
- user_id
- ad_id, campaign_id, advertiser_id
- timestamp
- placement (feed, story, sidebar)
- device, location
- viewability (% of ad visible)

Billing: Charged for CPM campaigns
```

**2. Click**:
```
Fired when: User clicks ad
Data tracked:
- All impression data +
- click_timestamp
- destination_url
- referrer

Billing: Charged for CPC campaigns
```

**3. Conversion**:
```
Fired when: User completes desired action
Data tracked:
- All click data +
- conversion_type (purchase, signup, download)
- conversion_value ($)
- attribution_window (7 days, 28 days)

Billing: Charged for CPA campaigns
```

**4. Engagement**:
```
Fired when: User interacts with ad post
Actions:
- Like, share, comment
- Video play, pause, complete
- Carousel swipe

Not billed, but affects quality score
```

**Event Pipeline**:
```
[Browser/App]
    ↓
[Tracking Pixel / SDK]
Fire event
    ↓
[Event Collector API]
Validate and queue
    ↓
[Kafka Event Stream]
Buffer events
    ↓
┌───────────┴───────────┐
↓                       ↓
[Real-Time           [Batch Processing]
 Processing]          (Daily aggregation)
(Flink)               (Spark)
    ├─ Deduplication      ├─ Campaign performance
    ├─ Fraud detection    ├─ User analytics
    ├─ Real-time metrics  └─ Billing aggregation
    └─ Budget updates
    ↓                       ↓
[Cassandra]          [Data Warehouse]
(Recent events)      (Historical analysis)
    ↓                       ↓
[Advertiser          [BI Tools]
 Dashboard]          (Reports, analytics)
(Real-time metrics)
```

**Deduplication**:
```
Problem: User refreshes page, duplicate impression

Solution: Deduplication window
- Track impression_id in Redis
- TTL: 1 minute
- If seen within window: Discard duplicate
- Only charge once

Window size: 60 seconds (balance accuracy vs memory)
```

### 6. Budget Management System

**Purpose**: Track spending and enforce budget limits

**Budget Tracking**:

**Daily Budget Pacing**:
```
Campaign settings:
- Daily budget: $100
- Target: Spend evenly throughout day

Pacing algorithm:
hour_budget = daily_budget / 24 = $100 / 24 = $4.16/hour

Every hour:
- Check spend so far this hour
- If < hour_budget: Normal serving
- If > hour_budget: Reduce serving probability
- Reset at start of next hour

Prevents: Spending entire budget in first hour
```

**Lifetime Budget**:
```
Campaign settings:
- Lifetime budget: $10,000
- Duration: 30 days
- Expected daily: $10,000 / 30 = $333/day

Tracking:
- Total spent: $5,000 (after 15 days)
- Remaining: $5,000
- Days left: 15
- Suggested daily: $5,000 / 15 = $333/day

When lifetime budget exhausted:
- Auto-pause campaign
- Notify advertiser
- Stop serving ads immediately
```

**Real-Time Budget Checks**:
```
Before showing ad:
1. Query current spend (Redis cache)
2. Check if budget remaining
3. If budget exceeded: Skip ad
4. If budget OK: Serve ad
5. Reserve budget amount (optimistic lock)

After impression/click:
6. Confirm charge (deduct from budget)
7. Update spend counters

Latency: < 5ms (Redis)
```

**Budget Enforcement**:
```
Budget states:
ACTIVE: Budget available, serve ads
PAUSED: Manually paused by advertiser
EXHAUSTED: Budget spent, auto-paused
SCHEDULED: Not yet started

Update budget in real-time:
- Every impression: Deduct CPM cost
- Every click: Deduct CPC cost
- Check budget before auction
- Prevent overspend
```

### 7. Analytics & Reporting System

**Purpose**: Provide performance insights to advertisers

**Real-Time Metrics**:
```
Dashboard shows (last 1 hour):
- Impressions: 50,000
- Clicks: 1,000 (CTR: 2%)
- Conversions: 50 (CR: 5%)
- Spend: $2,000
- CPC: $2.00
- CPA: $40.00
- ROAS: 3.5x (if revenue tracked)

Update frequency: Every 30 seconds
Data source: Kafka stream → Flink → Redis
```

**Historical Reports**:
```
Date range: Last 30 days

Aggregated metrics:
- Daily impressions trend
- CTR by age group
- Conversions by location
- Spend by placement
- Top performing creatives
- Hour-of-day performance

Data source: Data warehouse (BigQuery)
Query time: 2-5 seconds
```

**Attribution Reporting**:
```
Track user journey:
1. User sees Ad (impression)
2. User clicks Ad (click)
3. User visits website
4. User converts (purchase, signup)

Attribution windows:
- 1-day click
- 7-day click
- 28-day click
- 1-day view

Multi-touch attribution:
- First click, last click, linear, time-decay
```

**Custom Reports**:
```
Advertisers can create:
- Custom metrics
- Custom dimensions
- Scheduled exports (daily CSV)
- API access to data
- Integration with third-party tools
```

### 8. Fraud Detection System

**Purpose**: Detect and prevent ad fraud

**Fraud Types**:

**1. Click Fraud**:
```
Patterns:
- High click volume from single IP
- Bot-like behavior (perfect timing)
- Clicks without page engagement
- Clicks from known fraud IPs
- Same user clicking repeatedly

Detection:
- IP reputation checks
- Device fingerprinting
- Behavioral analysis
- ML models for anomaly detection

Action:
- Flag suspicious clicks
- Don't charge advertiser
- Block fraud source
```

**2. Impression Fraud**:
```
Patterns:
- Non-human traffic (bots)
- Hidden ads (0×0 px)
- Auto-refreshing pages
- Misleading placements

Detection:
- Bot detection (user agent, behavior)
- Viewability measurement
- Placement verification
- Traffic quality analysis

Action:
- Filter invalid impressions
- Refund advertiser
```

**3. Conversion Fraud**:
```
Patterns:
- Fake conversions
- Click injection
- Attribution manipulation

Detection:
- Conversion rate anomaly
- Time-to-convert analysis
- Cross-reference with advertiser data

Action:
- Investigate suspicious conversions
- Refund if confirmed fraud
```

**Fraud Prevention**:
```
Rate limiting:
- Max 10 clicks per user per ad per day
- Max 100 clicks per IP per hour

CAPTCHA:
- Show for suspicious patterns
- Verify human user

Bot detection:
- JavaScript challenges
- Canvas fingerprinting
- Mouse movement analysis
- Browser automation detection
```

---

## Database Design

### 1. Campaign Database (PostgreSQL)

**Schema**:

**Advertisers Table**:
```
Table: advertisers

Columns:
- advertiser_id: UUID PRIMARY KEY
- company_name: VARCHAR(255)
- email: VARCHAR(255) UNIQUE
- billing_address: JSONB
- payment_method: VARCHAR(50)
- account_balance: DECIMAL(12, 2)
- status: ENUM('active', 'suspended', 'closed')
- created_at: TIMESTAMP
- updated_at: TIMESTAMP
```

**Campaigns Table**:
```
Table: campaigns

Columns:
- campaign_id: UUID PRIMARY KEY
- advertiser_id: UUID (FK)
- name: VARCHAR(255)
- objective: VARCHAR(50) (awareness, traffic, conversions)
- status: ENUM('active', 'paused', 'exhausted', 'scheduled')
- daily_budget: DECIMAL(10, 2)
- lifetime_budget: DECIMAL(12, 2)
- total_spent: DECIMAL(12, 2)
- bid_strategy: VARCHAR(50)
- bid_amount: DECIMAL(8, 2)
- start_date: TIMESTAMP
- end_date: TIMESTAMP
- targeting_criteria: JSONB
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

Indexes:
- advertiser_id, status
- status, start_date
```

**Ad Creatives Table**:
```
Table: ad_creatives

Columns:
- creative_id: UUID PRIMARY KEY
- campaign_id: UUID (FK)
- format: VARCHAR(20) (image, video, carousel)
- headline: VARCHAR(100)
- description: TEXT
- cta_text: VARCHAR(50) (Call to action)
- image_url: VARCHAR(500)
- video_url: VARCHAR(500)
- landing_page_url: VARCHAR(500)
- status: ENUM('pending', 'approved', 'rejected')
- quality_score: DECIMAL(3, 2)
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

Indexes:
- campaign_id, status
- status, quality_score DESC
```

### 2. User Profile Database (Cassandra)

**Schema**:

**User Interests Table**:
```
Table: user_interests
Partition Key: user_id
Clustering Key: interest_category

Columns:
- user_id: UUID
- interest_category: TEXT
- interest_score: FLOAT (0.0 - 1.0)
- last_updated: TIMESTAMP

Purpose: Fast lookup of user interests for targeting
Query: Get all interests for user_id
```

**User Demographics Table**:
```
Table: user_demographics
Partition Key: user_id

Columns:
- user_id: UUID
- age: INT
- gender: TEXT
- location_country: TEXT
- location_city: TEXT
- language: TEXT
- education: TEXT
- relationship_status: TEXT
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

Purpose: Quick demographic lookup for targeting
```

**Ad Frequency Table**:
```
Table: ad_frequency
Partition Key: user_id
Clustering Key: campaign_id, date

Columns:
- user_id: UUID
- campaign_id: UUID
- date: DATE
- impression_count: INT
- last_impression: TIMESTAMP

TTL: 30 days

Purpose: Track frequency capping
```

### 3. Event Database (Cassandra)

**Impressions Table**:
```
Table: ad_impressions
Partition Key: (campaign_id, date)
Clustering Key: timestamp, impression_id

Columns:
- impression_id: UUID
- campaign_id: UUID
- ad_id: UUID
- user_id: UUID
- timestamp: TIMESTAMP
- placement: TEXT
- device: TEXT
- location: TEXT
- viewability: FLOAT
- cost: DECIMAL(8, 4)

TTL: 90 days

Purpose: Track all impressions for billing and analytics
```

**Clicks Table**:
```
Table: ad_clicks
Partition Key: (campaign_id, date)
Clustering Key: timestamp, click_id

Columns:
- click_id: UUID
- impression_id: UUID (references impression)
- campaign_id: UUID
- ad_id: UUID
- user_id: UUID
- timestamp: TIMESTAMP
- destination_url: TEXT
- cost: DECIMAL(8, 4)

TTL: 90 days
```

**Conversions Table**:
```
Table: ad_conversions
Partition Key: (campaign_id, date)
Clustering Key: timestamp, conversion_id

Columns:
- conversion_id: UUID
- click_id: UUID (references click)
- campaign_id: UUID
- user_id: UUID
- timestamp: TIMESTAMP
- conversion_type: TEXT
- conversion_value: DECIMAL(10, 2)
- attribution_window: TEXT

TTL: 365 days (longer for attribution analysis)
```

---

## API Design

### Advertiser APIs

**Campaign Management**:
```
POST /api/v1/campaigns
- Create new campaign
- Returns campaign_id

PUT /api/v1/campaigns/{campaign_id}
- Update campaign settings
- Budget, targeting, schedule

GET /api/v1/campaigns/{campaign_id}
- Get campaign details

DELETE /api/v1/campaigns/{campaign_id}
- Delete campaign

POST /api/v1/campaigns/{campaign_id}/pause
- Pause campaign

POST /api/v1/campaigns/{campaign_id}/resume
- Resume campaign
```

**Creative Management**:
```
POST /api/v1/creatives
- Upload ad creative
- Image/video files

GET /api/v1/creatives/{creative_id}
- Get creative details

PUT /api/v1/creatives/{creative_id}
- Update creative

DELETE /api/v1/creatives/{creative_id}
- Delete creative
```

**Analytics APIs**:
```
GET /api/v1/campaigns/{campaign_id}/insights?start_date={date}&end_date={date}
- Get campaign performance
- Impressions, clicks, conversions, spend

GET /api/v1/campaigns/{campaign_id}/breakdown?dimension={age|gender|location}
- Performance breakdown by dimension

GET /api/v1/audiences/{audience_id}/insights
- Custom audience performance

POST /api/v1/reports/custom
- Create custom report
- Export to CSV
```

### Ad Serving API

```
POST /api/v1/ads/request

Request:
{
  "user_id": "user_abc123",
  "placement": "feed",
  "context": {
    "device": "mobile",
    "location": "san_francisco",
    "language": "en"
  },
  "num_ads": 1
}

Response:
{
  "ads": [
    {
      "ad_id": "ad_xyz789",
      "campaign_id": "campaign_123",
      "creative": {
        "format": "image",
        "headline": "New iPhone 15 Pro",
        "description": "Pre-order now!",
        "image_url": "https://cdn.example.com/ad_image.jpg",
        "cta_text": "Shop Now",
        "landing_url": "https://example.com/iphone15"
      },
      "tracking": {
        "impression_url": "https://ads.example.com/track/impression/xyz",
        "click_url": "https://ads.example.com/track/click/xyz"
      }
    }
  ],
  "request_id": "req_abc"
}
```

### Event Tracking API

```
POST /api/v1/events/impression

Request:
{
  "impression_id": "imp_xyz",
  "ad_id": "ad_123",
  "user_id": "user_abc",
  "timestamp": 1705363200,
  "viewability": 0.75
}

Response:
{
  "status": "recorded",
  "impression_id": "imp_xyz"
}

POST /api/v1/events/click

Request:
{
  "click_id": "click_xyz",
  "impression_id": "imp_xyz",
  "ad_id": "ad_123",
  "user_id": "user_abc",
  "timestamp": 1705363205
}

Response:
{
  "status": "recorded",
  "click_id": "click_xyz",
  "redirect_url": "https://example.com/landing"
}
```

---

## Ad Serving Flow

### Complete Flow: User Opens Feed

```
T=0ms: User opens app/web
    ↓
[Client sends feed request]
GET /api/v1/feed?user_id=user_123
    ↓
[API Gateway]
Authenticate, route
    ↓
T=10ms: [Feed Service]
- Fetch organic posts (posts from friends/followed pages)
- Determine ad slots (every 5th post)
- Request 2 ads
    ↓
T=20ms: [Ad Service]
POST /ads/request {user_id, num_ads: 2}
    ↓
[Fetch User Profile from Cassandra]
- Demographics: age=32, gender=M, location=SF
- Interests: technology=0.95, sports=0.75
- Cache hit: 80% (Redis)
    ↓
T=30ms: [Targeting Engine]
- Query inverted index
- Lookup campaigns for: technology + sports + SF + age:25-45
- Initial candidates: 1,000 campaigns
    ↓
T=40ms: [Budget Service]
- Check budget for each campaign (Redis)
- Filter exhausted campaigns
- Check pacing
- Remaining candidates: 100 campaigns
    ↓
T=50ms: [Auction Engine]
- For each campaign:
  - Get bid amount
  - Calculate quality score (ML model)
  - Compute: bid × quality
- Rank campaigns
- Top 2 winners:
  - Campaign A: Score 4.5
  - Campaign B: Score 3.2
    ↓
T=60ms: [Frequency Capping Check]
- Check Redis: ad_freq:user_123:campaign_A
- Count: 1 (saw once today)
- Limit: 3 (OK, can show)
    ↓
T=65ms: [Fetch Ad Creatives]
- Get creative metadata from cache (Redis)
- Cache miss: Query PostgreSQL
- Get image URLs from CDN
    ↓
T=80ms: [Return Ads to Feed Service]
{
  "ads": [
    {
      "ad_id": "ad_A",
      "creative": {...},
      "tracking_urls": {...}
    },
    {
      "ad_id": "ad_B",
      "creative": {...},
      "tracking_urls": {...}
    }
  ]
}
    ↓
T=90ms: [Feed Service Assembles Feed]
- Mix organic posts + ads
- Position: Post 1, Post 2, Post 3, Post 4, Ad A, Post 5...
    ↓
T=100ms: Return complete feed to client
─────────────────────────────────
CLIENT-SIDE
    ↓
[User scrolls, Ad appears on screen]
    ↓
[Viewability tracking]
- 50% of ad visible for 1+ second
- Fire impression event
    ↓
POST /api/v1/events/impression
    ↓
[Event Collector]
- Validate event
- Publish to Kafka
- Return 200 OK
    ↓
[Kafka → Flink]
- Real-time processing
- Deduplication check
- Fraud detection
- Update metrics
- Deduct budget
    ↓
[Update Redis Counters]
- Campaign spend +$0.003 (CPM charge)
- Ad frequency counter +1
- Real-time dashboard updated
    ↓
[User clicks ad]
POST /api/v1/events/click
- Track click
- Deduct CPC ($2.00)
- Redirect to landing page
```

---

## Deep Dives

### 1. Ad Ranking Algorithm

**Challenge**: Select best ad from thousands of candidates

**Naive Approach**:
```
Score = bid_amount

Problem:
- High bid but irrelevant ad wins
- Poor user experience
- Low engagement
- Advertisers overpay for bad results
```

**Better Approach**: Bid × Quality × Relevance
```
Components:

1. Bid Amount ($)
   - How much advertiser will pay
   - CPC or CPM bid

2. Quality Score (0.0 - 1.0)
   - Expected CTR
   - Ad relevance
   - Landing page quality
   - Historical performance

3. Relevance Score (0.0 - 1.0)
   - User interest match
   - Contextual relevance
   - Personalization

Final Score = Bid × Quality × Relevance

Example:
Ad A: $5 bid × 0.8 quality × 0.9 relevance = 3.6
Ad B: $10 bid × 0.4 quality × 0.5 relevance = 2.0

Winner: Ad A (better overall even with lower bid)
```

**Machine Learning Model**:
```
Predict CTR (Click-Through Rate):

Features:
- User features:
  - Demographics (age, gender, location)
  - Interests and behaviors
  - Past ad interactions
  - Device type
  - Time of day
- Ad features:
  - Creative type (image, video)
  - Historical CTR
  - Ad copy sentiment
  - Landing page quality
- Context features:
  - Placement (feed, story)
  - Surrounding content
  - Session activity

Model: Gradient Boosted Decision Trees (GBDT)
Update frequency: Daily (retrain on yesterday's data)
Prediction latency: < 10ms
```

### 2. Targeting Index Optimization

**Challenge**: Find relevant campaigns quickly from 100M total

**Without Index** (Slow):
```
For each feed request:
1. Scan all 100M campaigns
2. Check targeting criteria for each
3. Find matching campaigns

Time: 10+ seconds ❌
```

**With Inverted Index** (Fast):
```
Pre-compute index:

interest:technology → [camp_1, camp_5, camp_10, ...camp_1000]
location:sf → [camp_2, camp_5, camp_15, ...]
age:25-34 → [camp_1, camp_3, camp_20, ...]

For feed request (user interested in technology, age 30, SF):
1. Lookup: interest:technology → 1000 campaigns
2. Lookup: age:25-34 → 800 campaigns
3. Lookup: location:sf → 600 campaigns
4. Intersection: ~100 campaigns (only those matching ALL)

Time: 10ms ✓

Speedup: 1000x
```

**Index Update Strategy**:
```
Full rebuild: Every hour (acceptable staleness)
Incremental update: Real-time for new campaigns

Storage: Redis
- Key: targeting:{criterion}
- Value: Set of campaign_ids
- Memory: ~10 GB for 100M campaigns

Trade-off: Slightly stale (up to 1 hour) vs fast queries
```

### 3. Budget Pacing Algorithm

**Challenge**: Spend budget evenly throughout day

**Problem Without Pacing**:
```
Campaign budget: $100/day
Without pacing:
- 8 AM: Spend $80 (rush hour traffic)
- 10 AM: Budget exhausted
- Rest of day: No ads shown

Result: Miss evening traffic (often high-converting)
```

**Solution: Pacing**:
```
Hour-by-hour budget allocation:
$100 / 24 hours = $4.17/hour

Pacing mechanism:
Every hour check:
- Spent this hour: $3.50
- Budget this hour: $4.17
- Remaining: $0.67
- Serving probability: 1.0 (spend more)

If spent $5.00 (over):
- Serving probability: 0.5 (throttle)

If spent $2.00 (under):
- Serving probability: 1.0 (spend more, catch up)
```

**Adaptive Pacing**:
```
Learn from patterns:
- Morning: Lower traffic, lower bids
- Evening: Higher traffic, higher bids
- Adjust budget allocation by hour

Machine learning:
- Predict traffic patterns
- Allocate more budget to high-performing hours
- Optimize for conversions

Example:
6 PM hour typically converts best
- Allocate 10% of daily budget ($10)
- vs 4% uniform allocation ($4)
```

### 4. Lookalike Audience Creation

**Challenge**: Find users similar to advertiser's customers

**Process**:

**Step 1: Seed Audience**:
```
Advertiser provides:
- Customer list (emails, phone numbers, IDs)
- Or: Website visitors (retargeting pixel)
- Size: 10,000 customers

Match to platform users:
- Email match: 7,000 users
- Match rate: 70%
```

**Step 2: Analyze Common Traits**:
```
Find patterns in seed audience:
Demographics:
- Age: 25-45 (80%)
- Gender: 60% Male, 40% Female
- Location: Urban areas (70%)

Interests:
- Technology: 85% high interest
- Sports: 60%
- Travel: 55%

Behaviors:
- Mobile-first users: 90%
- Online shoppers: 95%
- High engagement: 70%
```

**Step 3: Find Similar Users**:
```
Similarity metrics:
- Demographic similarity
- Interest similarity (cosine similarity)
- Behavior similarity

Similarity score:
similarity = (demographic_match × 0.3) + 
             (interest_match × 0.5) + 
             (behavior_match × 0.2)

Select users with similarity > 0.8
```

**Step 4: Size Control**:
```
Lookalike sizes:
- 1%: Top 1% most similar (5M users)
- 5%: Top 5% (25M users)
- 10%: Top 10% (50M users)

Advertiser chooses:
- 1%: Highest quality, smallest reach
- 10%: Lower quality, largest reach

Balance: Quality vs Reach
```

**Update Frequency**:
```
Recompute lookalike:
- Weekly (user interests change)
- When seed audience updated
- Dynamic audience (auto-updates)
```

### 5. A/B Testing for Ads

**Goal**: Test which ad creative performs better

**Setup**:
```
Campaign: iPhone 15 Pro
Creative A: Image of phone
Creative B: Video demo
Creative C: Carousel (multiple angles)

Split: 33% / 33% / 34%
```

**Implementation**:
```
During ad serving:
1. Auction selects campaign
2. Randomly select creative (based on split)
3. Consistent hashing (same user sees same creative)
4. Track performance per creative

Metrics tracked:
- CTR per creative
- Conversion rate per creative
- Engagement per creative
```

**Statistical Significance**:
```
After 10,000 impressions per creative:

Results:
Creative A: 1.5% CTR
Creative B: 2.3% CTR ← Winner
Creative C: 1.8% CTR

Confidence: 95%
Winner: Creative B (significantly better)

Action:
- Allocate more budget to Creative B
- Or pause Creative A & C
- Winner takes all
```

### 6. Ad Relevance and Quality

**Challenge**: Show ads users actually want to see

**User Experience Balance**:
```
Too many irrelevant ads:
- Users frustrated
- Ad blindness
- Users leave platform
- Revenue down

High quality ads:
- Users engage
- Higher CTR
- Better for advertisers
- Higher revenue
```

**Quality Enforcement**:
```
Low quality score (<0.5):
- Ad shown less frequently
- Requires higher bid to compete
- Eventually filtered out

High quality score (>0.8):
- Ad shown more frequently
- Competitive even with lower bid
- Rewarded in auction
```

**Negative Feedback**:
```
User actions:
- Hide ad
- Report ad
- Block advertiser

Impact:
- Quality score decreases
- Ad shown less
- Eventually paused if too many reports

Threshold: > 10% hide rate → Auto-pause
```

---

## Scalability & Reliability

### Horizontal Scaling

**Ad Serving Layer**:
```
Stateless servers behind load balancer

Current: 200 instances, 69K QPS
Each instance: 350 QPS capacity

To double capacity:
- Add 200 more instances
- Load balancer distributes automatically
- No config changes

New capacity: 400 instances, 138K QPS
```

**Auction Engine**:
```
CPU-intensive processing

Current: 100 instances
Each handles: 700 auctions/sec

Scale up:
- Add more instances
- Horizontal scaling (stateless)
- Linear scaling

Target: 1 instance per 10K DAU
```

**Event Processing**:
```
Kafka consumer group scaling:

Current: 50 consumers, 24K events/sec
Lag: 0 (keeping up)

If lag increases:
- Add more consumers (auto-scale)
- Kafka rebalances partitions
- Lag decreases

Scaling: Automatic based on lag
```

### High Availability

**Multi-Region Deployment**:
```
3 Regions: US, EU, APAC

Each region:
- Complete ad serving stack
- Regional user profiles
- Regional ad inventory

Benefits:
- Low latency (serve from nearest)
- Disaster recovery
- Compliance (GDPR data residency)
```

**Graceful Degradation**:
```
If ad system fails:
- Serve organic content only
- No ads in feed
- User experience uninterrupted
- Revenue impact: Temporary

Better than entire feed down
```

**Database Failover**:
```
PostgreSQL:
- Primary + 2 replicas
- Automatic failover (< 30 seconds)
- Write to primary, read from replicas

Cassandra:
- Replication factor: 3
- Quorum reads/writes
- No single point of failure
- Node failure: Automatic handling

Redis:
- Redis Cluster with replicas
- Automatic failover (< 5 seconds)
- Rebuild cache from database if needed
```

### Performance Optimization

**Caching Strategy**:

**L1 Cache** (Application memory):
```
Cache: Campaign metadata
Size: 1 GB per instance
TTL: 5 minutes

Hit ratio: 60%
Latency: < 1ms
```

**L2 Cache** (Redis):
```
Cache: User profiles, targeting index, budgets
Size: 500 GB cluster
TTL: 1-60 minutes (varies by data type)

Hit ratio: 80%
Latency: < 5ms
```

**L3 Cache** (CDN):
```
Cache: Ad creative images/videos
Global distribution
TTL: 24 hours

Hit ratio: 95%
Latency: < 50ms
```

**Database Optimization**:
```
Read replicas:
- PostgreSQL: 5 read replicas
- Route reads to replicas
- Reduce primary load
- Scale read capacity

Connection pooling:
- PgBouncer: 10,000 connections → 100 database connections
- Reduce overhead
- Better resource utilization

Partitioning:
- Events table: Partition by date
- Only query relevant partitions
- 10x query speedup
```

---

## Trade-offs & Alternatives

### 1. Real-Time Auction vs Pre-Computed

**Real-Time Auction** (Chosen):
```
Pros:
+ Fresh bids (advertisers can adjust any time)
+ Dynamic competition
+ Optimal pricing
+ Accurate budget tracking

Cons:
- Higher latency (50ms)
- More compute intensive
- Complex system

Use when: Dynamic pricing, many advertisers
```

**Pre-Computed Winners**:
```
Pros:
+ Lower latency (< 10ms)
+ Simpler system
+ Lower compute

Cons:
- Stale (computed hourly)
- Less optimal pricing
- Slower budget updates

Use when: Few advertisers, simpler needs
```

### 2. Cassandra vs PostgreSQL for User Profiles

**Cassandra** (Chosen):
```
Pros:
+ High read throughput (millions/sec)
+ Horizontal scaling
+ No single point of failure
+ Write-optimized (frequent updates)

Cons:
- Eventual consistency
- Limited query flexibility
- No joins

Use when: High throughput, simple queries
```

**PostgreSQL**:
```
Pros:
+ ACID transactions
+ Complex queries
+ Joins supported
+ Strong consistency

Cons:
- Limited scalability
- Single master bottleneck
- Read replicas needed

Use when: Need transactions, complex queries
```

### 3. Second-Price vs First-Price Auction

**Second-Price** (Chosen):
```
Pros:
+ Encourages truthful bidding
+ Advertisers bid true value
+ More efficient market
+ Fair pricing

Cons:
- Advertisers pay less than bid
- Lower revenue potential

Use when: Want efficient market, long-term
```

**First-Price**:
```
Pros:
+ Higher revenue (pay full bid)
+ Simpler to understand

Cons:
- Bid shading (advertisers bid low)
- Inefficient market
- Gaming the system

Use when: Maximize short-term revenue
```

### 4. Interest-Based vs Contextual Targeting

**Hybrid** (Chosen):
```
Interest-based:
+ More accurate (user's actual interests)
+ Higher engagement
+ Better personalization

Contextual:
+ Privacy-friendly (no user tracking)
+ Works for new users
+ Content-relevant

Use both:
- Interest-based as primary
- Contextual as fallback
- Combine signals for best results
```

### 5. Event Processing: Real-Time vs Batch

**Hybrid** (Chosen):
```
Real-time (Flink):
+ Immediate budget updates
+ Real-time dashboards
+ Fast fraud detection
- Higher cost
- More complex

Batch (Spark):
+ Historical analysis
+ Complex aggregations
+ Cost-effective
- Delayed insights

Use both:
- Real-time for critical path (budget, fraud)
- Batch for analytics and reporting
```

---

## Conclusion

This Social Feed Ads System serves **2 billion ad impressions per day** to **500M users** with:

**Key Features**:
- Real-time auction-based ad selection (< 100ms)
- Personalized targeting based on interests
- Second-price auction for fair pricing
- Budget management with pacing
- Comprehensive fraud detection
- Real-time analytics for advertisers
- 99.99% uptime guarantee

**Core Design Principles**:
1. **User experience first**: Relevant ads, frequency capping
2. **Real-time auction**: Dynamic pricing, fair competition
3. **Quality rewards**: Quality score × bid ranking
4. **Budget control**: Pacing, real-time tracking
5. **Fraud prevention**: Multi-layer detection
6. **Privacy compliance**: GDPR, CCPA, transparency

**Technology Stack**:
- **Ad Serving**: Custom service (Java/Go)
- **Auction**: In-memory auction engine
- **User Profiles**: Cassandra
- **Campaign DB**: PostgreSQL
- **Cache**: Redis
- **Event Stream**: Kafka
- **Real-Time Processing**: Apache Flink
- **Batch Processing**: Apache Spark
- **Data Warehouse**: BigQuery
- **CDN**: CloudFront/Cloud CDN

**Revenue Model**:
- Infrastructure: $195K/month
- Revenue: $180M/month
- Cost ratio: 0.1% of revenue
- Highly profitable business

**Scalability**:
- Scales to 1B DAU by adding more servers
- Scales to 200M campaigns with index optimization
- Linear scaling for most components

---

**Document Version**: 1.0  
**Last Updated**: November 16, 2025  
**Status**: Complete & Interview-Ready ✅
