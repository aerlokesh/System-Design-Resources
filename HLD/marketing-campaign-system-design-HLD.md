# Marketing Campaign Management System Design

## Table of Contents
- [Marketing Campaign Management System Design](#marketing-campaign-management-system-design)
  - [Table of Contents](#table-of-contents)
  - [Problem Statement \& Requirements](#problem-statement--requirements)
    - [Problem Definition](#problem-definition)
    - [Functional Requirements](#functional-requirements)
    - [Non-Functional Requirements](#non-functional-requirements)
  - [Capacity Estimation](#capacity-estimation)
    - [Assumptions](#assumptions)
    - [Storage Estimation](#storage-estimation)
    - [Bandwidth Estimation](#bandwidth-estimation)
    - [QPS (Queries Per Second)](#qps-queries-per-second)
    - [Infrastructure Requirements](#infrastructure-requirements)
  - [API Design](#api-design)
    - [Campaign Management APIs](#campaign-management-apis)
    - [Segment Management APIs](#segment-management-apis)
    - [User Preference APIs](#user-preference-apis)
    - [Analytics APIs](#analytics-apis)
    - [Webhook APIs](#webhook-apis)
  - [Database Schema](#database-schema)
    - [PostgreSQL (Relational Data)](#postgresql-relational-data)
    - [Cassandra (Time-Series Data)](#cassandra-time-series-data)
    - [Redis (Caching)](#redis-caching)
  - [High-Level Architecture](#high-level-architecture)
  - [Core Components](#core-components)
    - [1. Campaign Service](#1-campaign-service)
    - [2. Segmentation Engine](#2-segmentation-engine)

---

## Problem Statement & Requirements

### Problem Definition
Design a scalable marketing campaign management system that can:
- Create and manage multi-channel campaigns (email, SMS, push, in-app)
- Target specific user segments with personalized content
- Schedule campaigns with complex triggers
- Track campaign performance in real-time
- Support A/B testing and experimentation
- Handle billions of messages per day
- Respect user preferences and regulatory compliance (GDPR, CAN-SPAM)

**Real-World Examples**: Mailchimp, SendGrid, Braze, Iterable, Customer.io

---

### Functional Requirements

1. **Campaign Management**
   - Create, update, delete campaigns
   - Define campaign goals and KPIs
   - Set budget limits
   - Schedule campaigns (immediate, scheduled, recurring)
   - Pause/resume/stop campaigns

2. **User Segmentation**
   - Create custom user segments
   - Define targeting rules (demographics, behavior, preferences)
   - Dynamic segment updates
   - Segment size estimation
   - Exclude lists (unsubscribed, bounced)

3. **Content Management**
   - Create templates (email, SMS, push, in-app)
   - Personalization with dynamic variables
   - Multi-language support
   - A/B test variants
   - Preview and testing

4. **Multi-Channel Delivery**
   - Email campaigns
   - SMS campaigns
   - Push notifications (mobile/web)
   - In-app messages
   - Webhook integrations

5. **Trigger-Based Campaigns**
   - Event-based triggers (signup, purchase, cart abandonment)
   - Time-based triggers (anniversaries, birthdays)
   - Conditional logic (if-then rules)
   - Multi-step workflows

6. **Analytics & Reporting**
   - Real-time campaign metrics
   - Delivery status tracking
   - Engagement metrics (opens, clicks, conversions)
   - Revenue attribution
   - Cohort analysis
   - Export reports

7. **Compliance & Preferences**
   - Opt-in/opt-out management
   - Unsubscribe handling
   - Frequency capping
   - Channel preferences
   - GDPR compliance (data deletion, consent)

---

### Non-Functional Requirements

1. **Scalability**
   - Support 100M+ users
   - Send 1B+ messages/day
   - Handle 10K+ concurrent campaigns
   - Scale horizontally

2. **Performance**
   - Campaign creation: < 1 second
   - Segment computation: < 5 seconds for 10M users
   - Message delivery latency: < 60 seconds
   - Real-time analytics: < 5 second delay

3. **Availability**
   - 99.9% uptime
   - No message loss
   - Graceful degradation
   - Fault tolerance

4. **Reliability**
   - Guaranteed message delivery (at-least-once)
   - Deduplication for idempotency
   - Retry mechanisms
   - Dead letter queues

5. **Security**
   - Authentication and authorization
   - Data encryption (at rest and in transit)
   - PII protection
   - Audit logging

6. **Compliance**
   - GDPR compliance
   - CAN-SPAM Act compliance
   - TCPA compliance (SMS)
   - Data retention policies

---

## Capacity Estimation

### Assumptions
```
Total Users: 100 Million
Active Users (Daily): 30 Million (30%)
Campaigns per day: 5,000
Messages per campaign (avg): 200,000
Total messages/day: 1 Billion

Channel Distribution:
- Email: 60% (600M/day)
- Push: 30% (300M/day)
- SMS: 8% (80M/day)
- In-app: 2% (20M/day)
```

### Storage Estimation
```
User Profile:
- User ID: 8 bytes
- Email: 50 bytes
- Phone: 15 bytes
- Attributes: 500 bytes (JSON)
- Total: ~600 bytes per user
- 100M users: 60 GB

Campaign Data:
- Campaign metadata: 2 KB per campaign
- 5K campaigns/day × 365 days: 1.8M campaigns/year
- Storage: 3.6 GB/year

Message Logs:
- Message metadata: 200 bytes per message
- 1B messages/day × 30 days: 30B messages
- Storage: 6 TB/month
- With compression: 2 TB/month

User Engagement Data:
- Event: 100 bytes (opens, clicks, conversions)
- Engagement rate: 20%
- Events/day: 200M
- Storage: 20 GB/day = 600 GB/month

Total Storage (1 year):
- User profiles: 60 GB
- Campaigns: 3.6 GB
- Message logs: 24 TB
- Engagement: 7.2 TB
- Total: ~32 TB (with replication: 96 TB)
```

### Bandwidth Estimation
```
Message Delivery:
- Average message size: 50 KB (email with images)
- 1B messages/day
- Data transfer: 50 TB/day
- Bandwidth: 50 TB / 86400 sec = 600 MB/sec

With caching and CDN: ~200 MB/sec actual
```

### QPS (Queries Per Second)
```
Campaign API:
- Create/update operations: 100 QPS
- Read operations: 1,000 QPS

Message Sending:
- 1B messages/day
- Peak: 3x average
- Average: 11,500 messages/sec
- Peak: 35,000 messages/sec

Analytics API:
- Real-time queries: 5,000 QPS
- Batch queries: 500 QPS

Total: ~42,000 QPS peak
```

### Infrastructure Requirements
```
Application Servers:
- Handle 42K QPS
- 1K QPS per server
- Required: 50 servers (with redundancy: 100 servers)

Message Queue:
- 35K messages/sec peak
- Kafka cluster: 10 brokers

Database:
- PostgreSQL cluster: 1 primary + 5 read replicas
- Cassandra cluster: 20 nodes (for time-series data)
- Redis cluster: 10 nodes (for caching)

Workers:
- Email workers: 200 (3K emails/sec each)
- SMS workers: 50 (1.6K SMS/sec each)
- Push workers: 100 (3K push/sec each)

Estimated Monthly Cost (AWS):
- Compute: $15,000
- Database: $8,000
- Message Queue: $3,000
- Storage: $2,000
- Network: $5,000
- Email service (SendGrid/SES): $10,000
- SMS service (Twilio): $40,000
Total: ~$83,000/month
```

---

## API Design

### Campaign Management APIs

```http
# Create Campaign
POST /api/v1/campaigns
Headers: Authorization: Bearer <token>
Body: {
  "name": "Summer Sale 2024",
  "description": "Promote summer collection",
  "type": "scheduled",  // immediate, scheduled, triggered
  "channels": ["email", "push"],
  "start_time": "2024-06-01T09:00:00Z",
  "end_time": "2024-06-30T23:59:59Z",
  "target_segment_id": "segment_123",
  "content": {
    "email": {
      "subject": "50% Off Summer Sale!",
      "template_id": "tmpl_456",
      "variables": {
        "discount_code": "SUMMER50"
      }
    },
    "push": {
      "title": "Summer Sale",
      "body": "Get 50% off now!",
      "action_url": "app://summer-sale"
    }
  },
  "budget": {
    "max_spend": 10000,
    "currency": "USD"
  },
  "ab_test": {
    "enabled": true,
    "variants": [
      {"id": "A", "weight": 50},
      {"id": "B", "weight": 50}
    ]
  }
}
Response: {
  "campaign_id": "camp_789",
  "status": "draft",
  "estimated_recipients": 150000,
  "estimated_cost": 7500,
  "created_at": "2024-05-15T10:00:00Z"
}

# Get Campaign Details
GET /api/v1/campaigns/{campaign_id}
Response: {
  "campaign_id": "camp_789",
  "name": "Summer Sale 2024",
  "status": "active",
  "channels": ["email", "push"],
  "metrics": {
    "sent": 145000,
    "delivered": 142000,
    "opened": 45000,
    "clicked": 12000,
    "converted": 3500,
    "revenue": 175000
  },
  "budget_spent": 7250,
  "start_time": "2024-06-01T09:00:00Z",
  "end_time": "2024-06-30T23:59:59Z"
}

# Update Campaign
PATCH /api/v1/campaigns/{campaign_id}
Body: {
  "status": "paused"  // draft, active, paused, completed, cancelled
}

# Delete Campaign
DELETE /api/v1/campaigns/{campaign_id}

# Launch Campaign
POST /api/v1/campaigns/{campaign_id}/launch
Response: {
  "campaign_id": "camp_789",
  "status": "active",
  "launched_at": "2024-06-01T09:00:00Z"
}
```

### Segment Management APIs

```http
# Create Segment
POST /api/v1/segments
Body: {
  "name": "Active Shoppers",
  "description": "Users who made purchase in last 30 days",
  "criteria": {
    "conditions": [
      {
        "field": "last_purchase_date",
        "operator": "greater_than",
        "value": "30_days_ago"
      },
      {
        "field": "total_purchases",
        "operator": "greater_than",
        "value": 1
      }
    ],
    "logic": "AND"
  },
  "dynamic": true  // auto-update segment
}
Response: {
  "segment_id": "seg_456",
  "name": "Active Shoppers",
  "estimated_size": 85000,
  "created_at": "2024-05-15T10:00:00Z"
}

# Get Segment
GET /api/v1/segments/{segment_id}
Response: {
  "segment_id": "seg_456",
  "name": "Active Shoppers",
  "size": 87500,
  "last_updated": "2024-05-20T15:30:00Z",
  "criteria": {...}
}

# Estimate Segment Size
POST /api/v1/segments/estimate
Body: {
  "criteria": {...}
}
Response: {
  "estimated_size": 85000,
  "computation_time_ms": 450
}
```

### User Preference APIs

```http
# Get User Preferences
GET /api/v1/users/{user_id}/preferences
Response: {
  "user_id": "user_123",
  "email_enabled": true,
  "sms_enabled": false,
  "push_enabled": true,
  "frequency_cap": {
    "email": 3,  // max per week
    "push": 5
  },
  "categories": {
    "promotional": true,
    "transactional": true,
    "newsletters": false
  }
}

# Update Preferences
PATCH /api/v1/users/{user_id}/preferences
Body: {
  "email_enabled": false,
  "unsubscribe_reason": "Too many emails"
}

# Unsubscribe
POST /api/v1/unsubscribe
Body: {
  "email": "user@example.com",
  "campaign_id": "camp_789",
  "token": "unsubscribe_token"
}
```

### Analytics APIs

```http
# Get Campaign Analytics
GET /api/v1/campaigns/{campaign_id}/analytics
Query Params: ?start_date=2024-06-01&end_date=2024-06-30
Response: {
  "campaign_id": "camp_789",
  "metrics": {
    "sent": 145000,
    "delivered": 142000,
    "bounced": 3000,
    "opened": 45000,
    "clicked": 12000,
    "converted": 3500,
    "unsubscribed": 150
  },
  "rates": {
    "delivery_rate": 97.9,
    "open_rate": 31.7,
    "click_rate": 8.5,
    "conversion_rate": 2.4,
    "unsubscribe_rate": 0.1
  },
  "revenue": {
    "total": 175000,
    "average_order_value": 50
  },
  "channel_breakdown": {
    "email": {...},
    "push": {...}
  }
}

# Get Real-Time Dashboard
GET /api/v1/dashboard/realtime
Response: {
  "active_campaigns": 45,
  "messages_sent_today": 8500000,
  "messages_sending_now": 12000,
  "current_qps": 15000,
  "top_campaigns": [...]
}

# Export Report
POST /api/v1/campaigns/{campaign_id}/export
Body: {
  "format": "csv",  // csv, json, xlsx
  "metrics": ["sent", "opened", "clicked", "converted"]
}
Response: {
  "export_id": "exp_123",
  "status": "processing",
  "download_url": null  // Available when complete
}
```

### Webhook APIs

```http
# Register Webhook
POST /api/v1/webhooks
Body: {
  "url": "https://example.com/webhook",
  "events": ["campaign.sent", "message.opened", "message.clicked"],
  "secret": "webhook_secret_key"
}

# Test Webhook
POST /api/v1/webhooks/{webhook_id}/test

# Webhook Payload Example
POST https://example.com/webhook
Headers:
  X-Signature: HMAC-SHA256 signature
Body: {
  "event": "message.opened",
  "timestamp": "2024-06-01T10:15:30Z",
  "data": {
    "campaign_id": "camp_789",
    "user_id": "user_123",
    "message_id": "msg_456",
    "channel": "email"
  }
}
```

---

## Database Schema

### PostgreSQL (Relational Data)

```sql
-- Users Table
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    country VARCHAR(2),
    language VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(50) DEFAULT 'UTC',
    attributes JSONB,  -- Custom attributes
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_phone (phone),
    INDEX idx_attributes GIN (attributes)  -- For JSONB queries
);

-- User Preferences Table
CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT TRUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    in_app_enabled BOOLEAN DEFAULT TRUE,
    frequency_cap JSONB,  -- Per channel limits
    categories JSONB,  -- Category preferences
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Unsubscribes Table
CREATE TABLE unsubscribes (
    unsubscribe_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    email VARCHAR(255),
    channel VARCHAR(20),
    reason VARCHAR(500),
    campaign_id BIGINT,
    unsubscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_user (user_id),
    INDEX idx_email (email),
    INDEX idx_campaign (campaign_id)
);

-- Campaigns Table
CREATE TABLE campaigns (
    campaign_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50),  -- scheduled, triggered, transactional
    status VARCHAR(50) DEFAULT 'draft',
    channels TEXT[],  -- Array of channels
    segment_id BIGINT,
    content JSONB,
    schedule JSONB,
    budget JSONB,
    ab_test_config JSONB,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    launched_at TIMESTAMP,
    completed_at TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at)
);

-- Segments Table
CREATE TABLE segments (
    segment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    criteria JSONB NOT NULL,
    dynamic BOOLEAN DEFAULT FALSE,
    size INT DEFAULT 0,
    last_computed_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_dynamic (dynamic)
);

-- Segment Membership Table (for static segments)
CREATE TABLE segment_members (
    segment_id BIGINT,
    user_id BIGINT,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (segment_id, user_id),
    FOREIGN KEY (segment_id) REFERENCES segments(segment_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_user (user_id)
);

-- Templates Table
CREATE TABLE templates (
    template_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    subject VARCHAR(500),  -- For email
    body TEXT NOT NULL,
    variables JSONB,  -- List of variables
    preview_url VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_channel (channel)
);
```

### Cassandra (Time-Series Data)

```cql
-- Message Delivery Log (write-heavy)
CREATE TABLE message_log (
    campaign_id BIGINT,
    message_id UUID,
    user_id BIGINT,
    channel TEXT,
    status TEXT,  -- queued, sent, delivered, bounced, failed
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT,
    metadata MAP<TEXT, TEXT>,
    PRIMARY KEY ((campaign_id), sent_at, message_id)
) WITH CLUSTERING ORDER BY (sent_at DESC);

-- User Events (opens, clicks, conversions)
CREATE TABLE user_events (
    user_id BIGINT,
    event_type TEXT,  -- open, click, conversion
    timestamp TIMESTAMP,
    campaign_id BIGINT,
    message_id UUID,
    channel TEXT,
    metadata MAP<TEXT, TEXT>,
    PRIMARY KEY ((user_id), timestamp, event_type)
) WITH CLUSTERING ORDER BY (timestamp DESC);

-- Campaign Analytics (aggregated metrics)
CREATE TABLE campaign_metrics (
    campaign_id BIGINT,
    date DATE,
    hour INT,
    channel TEXT,
    metric TEXT,  -- sent, delivered, opened, clicked, converted
    value BIGINT,
    PRIMARY KEY ((campaign_id, date), hour, channel, metric)
);
```

### Redis (Caching)

```
# User preference cache
Key: user:prefs:{user_id}
Value: {email_enabled, sms_enabled, ...}
TTL: 1 hour

# Segment cache
Key: segment:{segment_id}:users
Value: Set of user_ids
TTL: 5 minutes

# Campaign stats cache
Key: campaign:{campaign_id}:stats
Value: {sent, delivered, opened, ...}
TTL: 1 minute

# Rate limiting (frequency cap)
Key: ratelimit:user:{user_id}:email
Value: Message count
TTL: 7 days (sliding window)

# Deduplication
Key: dedup:msg:{message_id}
Value: 1
TTL: 24 hours
```

---

## High-Level Architecture

```
                           ┌─────────────────────┐
                           │   Web Dashboard     │
                           │   (React SPA)       │
                           └──────────┬──────────┘
                                      │
                                      │ HTTPS
                                      ▼
                           ┌─────────────────────┐
                           │   Load Balancer     │
                           │   (Nginx/ALB)       │
                           └──────────┬──────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
        ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
        │   API Gateway    │ │   API Gateway    │ │   API Gateway    │
        │   (Campaign API) │ │  (Analytics API) │ │   (User API)     │
        └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
                 │                    │                    │
                 │                    │                    │
    ┌────────────┼────────────────────┼────────────────────┼────────────┐
    │            │                    │                    │            │
    │            ▼                    ▼                    ▼            │
    │   ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐   │
    │   │Campaign Service │  │Analytics Service│  │  User Service│   │
    │   │- Create/Update  │  │- Real-time stats│  │  - Profiles  │   │
    │   │- Scheduling     │  │- Reports        │  │  - Preferences│  │
    │   └────────┬────────┘  └────────┬────────┘  └──────┬───────┘   │
    │            │                     │                   │           │
    │            │                     │                   │           │
    │   ┌────────▼──────────┐  ┌──────▼───────────┐ ┌────▼──────┐   │
    │   │Segmentation Engine│  │Event Processor   │ │PostgreSQL │   │
    │   │- Compute segments │  │- Track events    │ │(Primary + │   │
    │   │- Audience sizing  │  │- Aggregation     │ │Replicas)  │   │
    │   └────────┬──────────┘  └──────┬───────────┘ └───────────┘   │
    │            │                     │                              │
    │            ▼                     ▼                              │
    │   ┌──────────────────────────────────────────────────┐        │
    │   │           Message Queue (Kafka)                   │        │
    │   │  Topics: campaigns, messages, events, analytics   │        │
    │   └────────┬─────────────────────────────────┬────────┘        │
    │            │                                  │                 │
    │            ▼                                  ▼                 │
    │   ┌──────────────────┐              ┌──────────────────┐      │
    │   │Message Scheduler │              │  Event Consumer  │      │
    │   │- Campaign queue  │              │  - Opens/Clicks  │      │
    │   │- Throttling      │              │  - Conversions   │      │
    │   │- Prioritization  │              │  - Analytics     │      │
    │   └────────┬─────────┘              └──────────────────┘      │
    │            │                                                    │
    │            ▼                                                    │
    │   ┌──────────────────────────────────────────────┐            │
    │   │         Message Dispatcher                    │            │
    │   │    (Route to appropriate channel)             │            │
    │   └────┬─────────┬──────────┬──────────┬─────────┘            │
    │        │         │          │          │                       │
    │        ▼         ▼          ▼          ▼                       │
    │   ┌────────┐ ┌──────┐ ┌──────────┐ ┌──────────┐              │
    │   │Email   │ │SMS   │ │  Push    │ │ In-App   │              │
    │   │Workers │ │Workers│ │  Workers │ │ Workers  │              │
    │   └────┬───┘ └───┬──┘ └─────┬────┘ └────┬─────┘              │
    │        │         │          │           │                      │
    └────────┼─────────┼──────────┼───────────┼──────────────────────┘
             │         │          │           │
             ▼         ▼          ▼           ▼
      ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
      │SendGrid  │ │ Twilio   │ │ Firebase │ │ WebSocket│
      │   SES    │ │  Nexmo   │ │   APNS   │ │  Server  │
      └──────────┘ └──────────┘ └──────────┘ └──────────┘

                    ┌──────────────────────────┐
                    │      Data Layer          │
                    │                          │
                    │  PostgreSQL (Metadata)   │
                    │  Cassandra (Events)      │
                    │  Redis (Cache)           │
                    │  S3 (Templates/Assets)   │
                    └──────────────────────────┘

                    ┌──────────────────────────┐
                    │   Monitoring Layer       │
                    │  Prometheus + Grafana    │
                    │  ELK Stack (Logs)        │
                    └──────────────────────────┘
```

---

## Core Components

### 1. Campaign Service

**Responsibilities:**
- Create and manage campaigns
- Validate campaign configuration
- Schedule campaigns
- Coordinate campaign execution

**Key Operations:**

```
Campaign Creation Flow:
1. Validate inputs (segment exists, template valid)
2. Estimate reach and cost
3. Save to database
4. Return campaign ID

Campaign Launch Flow:
1. Validate campaign ready (approved, funded)
2. Compute final segment
3. Generate message tasks
4. Publish to message queue
5. Update campaign status to "active"
```

**Scheduling Logic:**

```python
class CampaignScheduler:
    def schedule_campaign(self, campaign):
        if campaign.type == "immediate":
            self.launch_now(campaign)
        
        elif campaign.type == "scheduled":
            schedule_time = campaign.start_time
            self.schedule_job(schedule_time, self.launch_campaign, campaign.id)
        
        elif campaign.type == "recurring":
            # Cron-like scheduling
            schedule = campaign.recurrence  # e.g., "0 9 * * MON"
            self.schedule_recurring(schedule, self.launch_campaign, campaign.id)
        
        elif campaign.type == "triggered":
            # Event-based trigger
            self.register_trigger(campaign.trigger_event, campaign.id)
    
    def launch_campaign(self, campaign_id):
        campaign = self.get_campaign(campaign_id)
        
        # Compute segment
        user_ids = self.segment_engine.compute_segment(campaign.segment_id)
        
        # Apply filters (unsubscribed, frequency cap)
        filtered_users = self.apply_filters(user_ids, campaign)
        
        # Generate messages
        messages = self.generate_messages(campaign, filtered_users)
        
        # Publish to queue
        self.message_queue.publish_batch(messages)
        
        # Update status
        campaign.status = "active"
        campaign.launched_at = datetime.now()
        self.save(campaign)
```

---

### 2. Segmentation Engine

**Responsibilities:**
- Compute user segments based on criteria
- Estimate segment sizes
- Handle dynamic segments
- Cache segment results

**Segment Criteria DSL:**

```json
{
  "conditions": [
    {
      "field": "user.country",
      "operator": "equals",
      "value": "US"
    },
    {
      "field": "user.last_purchase_date",
      "operator": "within_last",
      "value": "30_days"
    },
    {
      "field": "user.total_spent",
      "operator": "greater_than",
      "value": 100
    }
  ],
  "logic": "AND",
  "exclude_segments": ["unsubscribed_users"]
}
```

**Computation Strategy:**

```python
class SegmentationEngine:
    def compute_segment(self, segment_id):
        segment = self.get_segment(segment_id)
        
        # Check cache first
        cached = self.cache.get(f"segment:{segment_id}:users")
        if cached and not segment.dynamic:
            return cached
        
        # Build SQL query from criteria
        query = self.build_query(segment.criteria)
        
        # Execute query
        user_ids = self.database.execute(query)
        
        # Apply exclusions
        if segment.criteria.exclude_segments:
            for excl_seg_id in segment.criteria.exclude_segments:
                excl_users = self.compute_segment(excl_seg_id)
                user_ids = user_ids - excl_users
        
        # Cache result
        self.cache.set(f"segment:{segment_id}:users", user_ids, ttl=300)
        
        # Update segment size
        segment.size = len(user_ids)
        segment.last_computed_at = datetime.now()
        self.save(segment)
        
        return user_ids
    
    def build_query(self, criteria):
        """Convert criteria JSON to SQL query"""
        conditions = []
        for cond in criteria.conditions:
            field = cond['field']
            operator = cond['operator']
            value = cond['value']
            
            if operator == 'equals':
                conditions.append(f"{field} = '{value}'")
            elif operator == 'greater_than':
                conditions.append(f"{field} > {value}")
            elif operator == 'within_last':
                days = self.parse_days(value)
                conditions.append(f"{field} > NOW() - INTERVAL {days} DAY")
        
        logic = criteria.get('logic', 'AND')
        where_clause = f" {logic} ".join(conditions)
        
        return f"SELECT user_id FROM users WHERE {where_clause}"
```

**Optimization: Segment Size Estimation**

```python
def estimate_segment_size(self, criteria):
    """Fast estimation using EXPLAIN or sampling"""
    query = self.build_query(criteria)
    
    # Use EXPLAIN to get row estimate
    explain_query = f"EXPLAIN {query}"
    result = self.database.execute(explain_query)
    
    # Parse estimated rows from EXPLAIN output
    estimated_rows = self.parse_explain_result(result)
    
    return estimated_rows
```

---

### 3. Message Dispatcher

**Responsibilities:**
- Route messages to appropriate channel workers
- Handle message batching
- Implement rate limiting and throttling
- Track delivery status

**Dispatching Logic:**

```python
class MessageDispatcher:
    def __init__(self):
        self.email_queue = Queue("email_messages")
        self.sms_queue = Queue("sms_messages")
        self.push_queue = Queue("push_messages")
        self.inapp_queue = Queue("inapp_messages")
    
    def dispatch_message(self, message):
        # Check user preferences
        if not self.check_user_preferences(message.user_id, message.channel):
            self.log_skipped(message, "user_preferences")
            return
        
        # Check frequency cap
        if not self.check_frequency_cap(message.user_id, message.channel):
            self.log_skipped(message, "frequency_cap")
            return
        
        # Check deduplication
        if self.is_duplicate(message.id):
            self.log_skipped(message, "duplicate")
            return
        
        # Route to appropriate queue
        if message.channel == "email":
            self.email_queue.publish(message)
        elif message.channel == "sms":
            self.sms_queue.publish(message)
        elif message.channel == "push":
            self.push_queue.publish(message)
        elif message.channel == "in_app":
            self.inapp_queue.publish(message)
        
        # Mark as dispatched
        self.mark_dispatched(message.id)
    
    def check_frequency_cap(self, user_id, channel):
        """Check if user hasn't exceeded frequency cap"""
        key = f"ratelimit:user:{user_id}:{channel}"
        count = self.redis.get(key) or 0
        cap = self.get_user_frequency_cap(user_id, channel)
        
        if count >= cap:
            return False
        
        # Increment counter (sliding window: 7 days)
        self.redis.incr(key)
        self.redis.expire(key, 7 * 24 * 3600)
        return True
    
    def is_duplicate(self, message_id):
        """Check if message already sent in last 24 hours"""
        key = f"dedup:msg:{message_id}"
        if self.redis.exists(key):
            return True
        
        self.redis.set(key, 1, ex=24*3600)
        return False
```

---

### 4. Channel Workers

#### Email Worker

```python
class EmailWorker:
    def __init__(self):
        self.email_service = SendGridClient()  # or AWS SES
        self.template_engine = TemplateEngine()
    
    def process_message(self, message):
        try:
            # Get template
            template = self.get_template(message.template_id)
            
            # Personalize content
            content = self.personalize(template, message.user_data)
            
            # Send email
            result = self.email_service.send(
                to=message.user_email,
                from_email="noreply@example.com",
                subject=content['subject'],
                html_body=content['body'],
                tracking=True,  # Enable open/click tracking
                unsubscribe_url=self.generate_unsubscribe_url(message)
            )
            
            # Log delivery
            self.log_delivery(message.id, "sent", result.message_id)
            
        except Exception as e:
            # Handle failure
            self.log_failure(message.id, str(e))
            self.retry_or_dlq(message)
    
    def personalize(self, template, user_data):
        """Replace variables with user-specific values"""
        subject = template['subject']
        body = template['body']
        
        for key, value in user_data.items():
            subject = subject.replace(f"{{{key}}}", str(value))
            body = body.replace(f"{{{key}}}", str(value))
        
        return {'subject': subject, 'body': body}
    
    def generate_unsubscribe_url(self, message):
        """Generate secure unsubscribe link"""
        token = self.create_unsubscribe_token(message.user_id, message.campaign_id)
        return f"https://example.com/unsubscribe?token={token}"
```

#### SMS Worker

```python
class SMSWorker:
    def __init__(self):
        self.sms_service = TwilioClient()
    
    def process_message(self, message):
        try:
            # Check SMS length (160 chars limit)
            content = self.truncate_if_needed(message.content, 160)
            
            # Send SMS
            result = self.sms_service.send(
                to=message.user_phone,
                from_number="+1234567890",
                body=content
            )
            
            # Log delivery
            self.log_delivery(message.id, "sent", result.sid)
            
        except Exception as e:
            self.log_failure(message.id, str(e))
            self.retry_or_dlq(message)
```

#### Push Notification Worker

```python
class PushWorker:
    def __init__(self):
        self.fcm_client = FirebaseClient()  # For Android
        self.apns_client = APNSClient()     # For iOS
    
    def process_message(self, message):
        try:
            # Get user's device tokens
            devices = self.get_user_devices(message.user_id)
            
            for device in devices:
                if device.platform == "android":
                    self.send_fcm(device.token, message)
                elif device.platform == "ios":
                    self.send_apns(device.token, message)
            
            # Log delivery
            self.log_delivery(message.id, "sent", len(devices))
            
        except Exception as e:
            self.log_failure(message.id, str(e))
            self.retry_or_dlq(message)
```

---

### 5. Analytics Engine

**Responsibilities:**
- Track message events (sent, delivered, opened, clicked, converted)
- Aggregate metrics in real-time
- Generate reports
- Support cohort analysis

**Event Processing:**

```python
class EventProcessor:
    def process_event(self, event):
        """Process incoming event (open, click, conversion)"""
        
        # Store raw event
        self.store_raw_event(event)
        
        # Update real-time aggregates
        self.update_campaign_metrics(event)
        
        # Update user engagement profile
        self.update_user_engagement(event)
        
        # Trigger webhooks
        self.trigger_webhooks(event)
    
    def update_campaign_metrics(self, event):
        """Increment campaign metrics"""
        campaign_id = event['campaign_id']
        event_type = event['event_type']
        channel = event['channel']
        
        # Increment in Redis (fast)
        key = f"campaign:{campaign_id}:stats"
        self.redis.hincrby(key, event_type, 1)
        self.redis.hincrby(key, f"{channel}_{event_type}", 1)
        
        # Also store in Cassandra (persistent)
        hour = datetime.now().hour
        date = datetime.now().date()
        
        self.cassandra.execute("""
            UPDATE campaign_metrics
            SET value = value + 1
            WHERE campaign_id = ? AND date = ? AND hour = ? 
              AND channel = ? AND metric = ?
        """, (campaign_id, date, hour, channel, event_type))
    
    def calculate_conversion_attribution(self, conversion_event):
        """Attribute conversion to campaigns within attribution window"""
        user_id = conversion_event['user_id']
        conversion_time = conversion_event['timestamp']
        
        # Get user's recent campaign interactions (30-day window)
        recent_campaigns = self.get_recent_interactions(
            user_id, 
            conversion_time - timedelta(days=30),
            conversion_time
        )
        
        # Attribution models
        if self.attribution_model == "last_touch":
            # Credit last campaign
            attributed_campaign = recent_campaigns[-1]
        elif self.attribution_model == "first_touch":
            # Credit first campaign
            attributed_campaign = recent_campaigns[0]
        elif self.attribution_model == "linear":
            # Distribute credit equally
            for campaign in recent_campaigns:
                self.credit_conversion(campaign, 1.0 / len(recent_campaigns))
        
        return attributed_campaign
```

**Real-Time Dashboard:**

```python
class DashboardService:
    def get_realtime_stats(self):
        """Get current system stats"""
        stats = {
            'active_campaigns': self.count_active_campaigns(),
            'messages_sent_today': self.get_daily_message_count(),
            'messages_sending_now': self.get_current_queue_depth(),
            'current_qps': self.get_current_qps(),
            'top_campaigns': self.get_top_campaigns(limit=10)
        }
        return stats
    
    def get_campaign_performance(self, campaign_id):
        """Get campaign metrics"""
        # Try Redis first (real-time)
        key = f"campaign:{campaign_id}:stats"
        redis_stats = self.redis.hgetall(key)
        
        if redis_stats:
            return self.format_stats(redis_stats)
        
        # Fallback to Cassandra (historical)
        return self.get_from_cassandra(campaign_id)
```

---

## Campaign Lifecycle

### State Machine

```
┌──────────┐
│  DRAFT   │ ← Initial state
└────┬─────┘
     │ (Validate & Approve)
     ▼
┌──────────┐
│SCHEDULED │
└────┬─────┘
     │ (Trigger time reached)
     ▼
┌──────────┐      ┌─────────┐
│ ACTIVE   │◄────►│ PAUSED  │ (Manual pause/resume)
└────┬─────┘      └─────────┘
     │ (All messages sent OR end time reached)
     ▼
┌──────────┐
│COMPLETED │
└──────────┘

     OR (Manual cancellation)
     ↓
┌──────────┐
│CANCELLED │
└──────────┘
```

### Lifecycle Events

```python
class CampaignLifecycle:
    def on_campaign_created(self, campaign):
        """Called when campaign is created"""
        # Send notification to creator
        self.notify(campaign.created_by, "Campaign created successfully")
        
        # Audit log
        self.audit_log("campaign_created", campaign.id)
    
    def on_campaign_launched(self, campaign):
        """Called when campaign starts"""
        # Update metrics
        self.increment_metric("campaigns_launched")
        
        # Webhook
        self.trigger_webhook("campaign.launched", campaign)
        
        # Start monitoring
        self.start_monitoring(campaign.id)
    
    def on_campaign_completed(self, campaign):
        """Called when campaign completes"""
        # Generate final report
        report = self.generate_final_report(campaign.id)
        
        # Send to stakeholders
        self.send_report(campaign.created_by, report)
        
        # Archive campaign data
        self.archive_campaign(campaign.id)
```

---

## User Segmentation & Targeting

### Advanced Segmentation

**1. Behavioral Segmentation**

```python
# Users who viewed product but didn't purchase
{
  "conditions": [
    {
      "field": "events.product_viewed",
      "operator": "exists",
      "value": "last_7_days"
    },
    {
      "field": "events.product_purchased",
      "operator": "not_exists",
      "value": "last_7_days"
    }
  ],
  "logic": "AND"
}
```

**2. RFM Segmentation (Recency, Frequency, Monetary)**

```python
# High-value customers (bought recently, often, and high-value)
{
  "conditions": [
    {
      "field": "last_purchase_date",
      "operator": "within_last",
      "value": "30_days"
    },
    {
      "field": "purchase_count",
      "operator": "greater_than",
      "value": 5
    },
    {
      "field": "lifetime_value",
      "operator": "greater_than",
      "value": 1000
    }
  ],
  "logic": "AND"
}
```

**3. Lookalike Audiences**

```python
class LookalikeAudience:
    def create_lookalike(self, seed_segment_id, size=10000):
        """Create lookalike audience based on seed segment"""
        
        # Get seed users
        seed_users = self.get_segment_users(seed_segment_id)
        
        # Extract common attributes
        common_attributes = self.analyze_common_attributes(seed_users)
        
        # Find similar users
        similar_users = self.find_similar_users(common_attributes, limit=size)
        
        # Create new segment
        return self.create_segment(
            name=f"Lookalike of {seed_segment_id}",
            user_ids=similar_users
        )
```

### Personalization

**Dynamic Content:**

```python
class ContentPersonalizer:
    def personalize_message(self, template, user):
        """Personalize message content for user"""
        
        # Basic variables
        content = template.replace("{{first_name}}", user.first_name)
        content = content.replace("{{last_name}}", user.last_name)
        
        # Conditional content
        if user.is_premium:
            content = self.insert_premium_content(content)
        
        # Product recommendations
        if "{{recommended_products}}" in content:
            recommendations = self.get_recommendations(user.id)
            content = content.replace("{{recommended_products}}", 
                                     self.format_products(recommendations))
        
        # Localization
        content = self.localize(content, user.language, user.country)
        
        return content
```

---

## Multi-Channel Delivery

### Channel Selection Strategy

```python
class ChannelSelector:
    def select_optimal_channel(self, user_id, campaign):
        """Select best channel for user"""
        
        # Get user preferences
        prefs = self.get_user_preferences(user_id)
        
        # Get user engagement history
        engagement = self.get_channel_engagement(user_id)
        
        # Score each channel
        scores = {}
        for channel in campaign.channels:
            if not prefs.get(f"{channel}_enabled"):
                scores[channel] = 0
                continue
            
            # Engagement rate
            engagement_rate = engagement.get(f"{channel}_engagement_rate", 0)
            
            # Delivery rate
            delivery_rate = engagement.get(f"{channel}_delivery_rate", 1.0)
            
            # Channel cost
            cost = self.get_channel_cost(channel)
            
            # Combined score (weight factors)
            scores[channel] = (engagement_rate * 0.6 + 
                             delivery_rate * 0.3 - 
                             cost * 0.1)
        
        # Return channel with highest score
        return max(scores, key=scores.get)
```

### Multi-Channel Orchestration

```python
class MultiChannelOrchestrator:
    def execute_multi_step_campaign(self, workflow):
        """Execute multi-step campaign workflow"""
        
        # Example workflow:
        # 1. Send email
        # 2. Wait 1 day
        # 3. If not opened, send push notification
        # 4. Wait 2 days
        # 5. If still no conversion, send SMS
        
        for step in workflow.steps:
            if step.type == "send":
                self.send_message(step.channel, step.content)
            
            elif step.type == "wait":
                self.schedule_next_step(step.duration)
                break  # Exit and resume later
            
            elif step.type == "condition":
                if self.evaluate_condition(step.condition):
                    self.execute_branch(step.true_branch)
                else:
                    self.execute_branch(step.false_branch)
```

---

## Analytics & Reporting

### Key Metrics

**Campaign Metrics:**
- Sent: Total messages dispatched
- Delivered: Messages successfully delivered
- Bounced: Messages failed to deliver
- Opened: Messages opened by recipients
- Clicked: Links clicked in messages
- Converted: Desired actions completed
- Unsubscribed: Users who opted out
- Revenue: Total revenue attributed to campaign

**Calculated Rates:**
```
Delivery Rate = (Delivered / Sent) × 100
Open Rate = (Opened / Delivered) × 100
Click Rate = (Clicked / Delivered) × 100
Click-to-Open Rate = (Clicked / Opened) × 100
Conversion Rate = (Converted / Delivered) × 100
ROI = (Revenue - Cost) / Cost × 100
```

### Cohort Analysis

```python
class CohortAnalyzer:
    def analyze_cohort(self, cohort_definition, metric="conversion_rate"):
        """Analyze cohort over time"""
        
        # Define cohorts (e.g., users who signed up in same month)
        cohorts = self.define_cohorts(cohort_definition)
        
        results = []
        for cohort in cohorts:
            cohort_data = {
                'cohort_id': cohort.id,
                'cohort_name': cohort.name,
                'size': len(cohort.users),
                'metrics_over_time': {}
            }
            
            # Track metric over time periods
            for period in range(12):  # 12 months
                value = self.calculate_metric(
                    cohort.users, 
                    metric, 
                    period
                )
                cohort_data['metrics_over_time'][f"month_{period}"] = value
            
            results.append(cohort_data)
        
        return results
```

### A/B Testing Framework

```python
class ABTestManager:
    def create_ab_test(self, campaign, variants):
        """Set up A/B test for campaign"""
        
        test = {
            'campaign_id': campaign.id,
            'variants': variants,  # [{'id': 'A', 'weight': 50}, {'id': 'B', 'weight': 50}]
            'metric': 'conversion_rate',
            'min_sample_size': 1000,
            'confidence_level': 0.95
        }
        
        return test
    
    def assign_variant(self, user_id, test):
        """Assign user to variant"""
        
        # Consistent hashing for same user always gets same variant
        hash_value = hash(f"{user_id}:{test['campaign_id']}") % 100
        
        cumulative_weight = 0
        for variant in test['variants']:
            cumulative_weight += variant['weight']
            if hash_value < cumulative_weight:
                return variant['id']
        
        return test['variants'][-1]['id']
    
    def analyze_results(self, test_id):
        """Statistical analysis of A/B test results"""
        
        test = self.get_test(test_id)
        results = {}
        
        for variant in test['variants']:
            stats = self.get_variant_stats(test_id, variant['id'])
            results[variant['id']] = {
                'sample_size': stats.count,
                'conversion_rate': stats.conversions / stats.count,
                'confidence_interval': self.calculate_ci(stats)
            }
        
        # Statistical significance test
        is_significant, p_value = self.chi_square_test(results)
        
        # Determine winner
        winner = max(results, key=lambda v: results[v]['conversion_rate'])
        
        return {
            'results': results,
            'is_significant': is_significant,
            'p_value': p_value,
            'winner': winner if is_significant else None
        }
```

---

## Design Trade-offs

### 1. Real-Time vs Batch Processing

```
Real-Time Analytics:
Pros:
✓ Immediate insights
✓ Quick decision making
✓ Better user experience

Cons:
✗ Higher infrastructure cost
✗ Complex implementation
✗ More resource intensive

Batch Analytics:
Pros:
✓ Lower cost
✓ Simpler implementation
✓ Better for historical analysis

Cons:
✗ Delayed insights
✗ Can't react quickly
✗ Stale data

Decision: Hybrid approach
- Real-time for critical metrics (delivery status)
- Batch for historical analysis and reporting
```

### 2. Push vs Pull for Segment Updates

```
Push (Event-Driven):
- User event → Update segment membership immediately
- Pros: Always up-to-date
- Cons: High write load, complex logic

Pull (Scheduled):
- Recompute segments periodically (e.g., every hour)
- Pros: Simpler, lower load
- Cons: Stale data between updates

Decision: Pull for dynamic segments (acceptable lag)
```

### 3. Message Storage Duration

```
Options:
1. 30 days: Lower cost, sufficient for most needs
2. 90 days: Compliance requirements
3. 1 year+: Full audit trail

Decision: Tiered storage
- Hot: 30 days (fast access)
- Warm: 90 days (slower access)
- Cold: 1 year (archive)
```

### 4. Consistency vs Availability

```
For campaign metrics:
- Accept eventual consistency
- Prioritize availability
- User sees slightly stale metrics (acceptable)

For user preferences:
- Strong consistency required
- Must respect opt-outs immediately
- Can sacrifice some availability
```

---

## Scalability & Performance

### Horizontal Scaling

```
Components that scale horizontally:
1. API servers (stateless)
2. Workers (email, SMS, push)
3. Event processors
4. Cassandra nodes
5. Redis cache nodes
6. Kafka brokers

Auto-scaling policies:
- API servers: CPU > 70%
- Workers: Queue depth > 10,000
- Scale down during low traffic (2am-6am)
```

### Performance Optimizations

**1. Database Query Optimization**

```sql
-- Add indexes for common queries
CREATE INDEX idx_user_country_lang ON users(country, language);
CREATE INDEX idx_campaign_status_type ON campaigns(status, type);
CREATE INDEX idx_segment_dynamic ON segments(dynamic) WHERE dynamic = TRUE;

-- Partition large tables
CREATE TABLE message_log_2024_06 PARTITION OF message_log
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
```

**2. Caching Strategy**

```
L1 (Application Cache):
- User preferences: 1 hour TTL
- Templates: 1 hour TTL
- Segment membership: 5 min TTL

L2 (Redis):
- Campaign stats: 1 min TTL
- Hot user data: 1 hour TTL
- Frequency cap counters: 7 days TTL

Cache invalidation:
- Write-through for preferences
- Time-based for stats
- Event-driven for critical data
```

**3. Message Batching**

```python
class BatchProcessor:
    def __init__(self, batch_size=1000, batch_timeout=5):
        self.batch = []
        self.batch_size = batch_size
        self.batch_timeout = batch_timeout
    
    def add_message(self, message):
        self.batch.append(message)
        
        if len(self.batch) >= self.batch_size:
            self.flush()
    
    def flush(self):
        """Send batch to email service"""
        if not self.batch:
            return
        
        # Bulk send API
        self.email_service.send_batch(self.batch)
        
        # Clear batch
        self.batch = []

# Benefit: 10x throughput improvement
# Single API call for 1000 emails vs 1000 calls
```

**4. Rate Limiting**

```python
class RateLimiter:
    def __init__(self):
        self.limits = {
            'email': 10000,  # per second
            'sms': 1000,
            'push': 5000
        }
    
    def check_limit(self, channel):
        """Token bucket algorithm"""
        key = f"ratelimit:{channel}"
        
        # Get current tokens
        tokens = self.redis.get(key) or self.limits[channel]
        
        if tokens > 0:
            # Consume token
            self.redis.decr(key)
            return True
        
        return False
    
    def refill_tokens(self):
        """Background job to refill tokens every second"""
        for channel, limit in self.limits.items():
            key = f"ratelimit:{channel}"
            self.redis.set(key, limit)
```

---

## Summary & Key Takeaways

### Architecture Highlights

1. **Microservices Design**
   - Separate services for campaigns, segmentation, analytics
   - Independent scaling
   - Technology flexibility

2. **Event-Driven Architecture**
   - Kafka for message queue
   - Asynchronous processing
   - Loose coupling

3. **Multi-Channel Support**
   - Pluggable channel workers
   - Unified tracking
   - Channel-agnostic campaign management

4. **Real-Time Analytics**
   - Stream processing
   - In-memory aggregation
   - Sub-second latency

5. **Compliance First**
   - Built-in preference management
   - Automatic unsubscribe handling
   - Audit logging

### Success Metrics

```
Technical Metrics:
- Message delivery rate: > 98%
- System uptime: > 99.9%
- API latency: P95 < 200ms
- Message delivery latency: < 60s

Business Metrics:
- Campaign ROI: Track revenue vs cost
- User engagement: Open rate, click rate
- Conversion rate: % of users who convert
- Unsubscribe rate: < 0.5%
```

### Best Practices

1. **Start Simple**
   - MVP: Email only, manual segments
   - Iterate: Add channels, dynamic segments
   - Scale: Optimize based on actual usage

2. **Respect Users**
   - Honor opt-outs immediately
   - Frequency capping
   - Clear unsubscribe links

3. **Monitor Everything**
   - Delivery rates per channel
   - Error rates
   - Cost per message
   - User engagement

4. **Test Before Sending**
   - A/B test content
   - Preview on devices
   - Test with small segments first

### Common Pitfalls

❌ Ignoring user preferences  
❌ Not implementing rate limiting  
❌ Poor error handling (retry logic)  
❌ Insufficient monitoring  
❌ Not tracking ROI  
❌ Sending too frequently  
❌ Poor email deliverability (spam)

---

**Document Version**: 1.0  
**Last Updated**: 2025  
**Focus**: High-Level Design for Marketing Campaign Systems
